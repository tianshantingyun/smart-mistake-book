#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""设备截图基线/像素差（批次 44 / D0 item 8、C 域视觉证据）。

## 它消灭的具体失败

视觉验收没有基线、没有阈值、只比文件名就宣称“视觉未回归”。本工具：

1. 用 `adb exec-out screencap -p` 采集 8-bit PNG，并把 **serial / 时间 /
   APK sha256** 写进同名 `.json` 侧车；截图与其绑定对象可追溯；
2. 纯 stdlib 解码 PNG（color type 0/2/4/6，bit depth 8，非隔行），计算
   **MAE** 与 **变化像素比例**；
3. 阈值必须由调用方显式给出；超阈值 exit 1，尺寸不一致 fail-closed。

不引入新依赖（计划 §6）。不改 Gradle、不联网。

## 安全边界

采集只执行固定参数 `adb exec-out screencap -p`（shell=False）；serial 通过
`ANDROID_SERIAL` 环境变量传递并先做字符白名单校验，不拼接进命令。

## 用法

    python -I tools/ci/screenshot_baseline.py capture --serial <serial> --out build/shots/home.png --apk-sha <sha256>
    python -I tools/ci/screenshot_baseline.py compare --baseline build/shots/home.base.png \
        --current build/shots/home.new.png --max-mae 1.0 --max-changed-ratio 0.001 \
        --out build/shots/home.diff.json

退出码：0 在阈值内；1 超阈值/尺寸不一致；2 参数/解码错误。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import struct
import subprocess
import sys
import zlib
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
SERIAL_RE = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


class PngError(ValueError):
    pass


@dataclass(frozen=True)
class PngImage:
    width: int
    height: int
    pixels: bytes  # RGBA8


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def decode_png(data: bytes) -> PngImage:
    if not data.startswith(PNG_SIGNATURE):
        raise PngError("bad PNG signature")
    offset = len(PNG_SIGNATURE)
    width = height = bit_depth = color_type = interlace = None
    idat = bytearray()
    while offset + 8 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        chunk_type = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        offset += 12 + length
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, compression, filter_method, interlace = \
                struct.unpack(">IIBBBBB", payload)
            if compression != 0 or filter_method != 0 or interlace != 0:
                raise PngError("only non-interlaced PNG with standard filters supported")
            if bit_depth != 8:
                raise PngError(f"bit depth {bit_depth} unsupported (need 8)")
        elif chunk_type == b"IDAT":
            idat.extend(payload)
        elif chunk_type == b"IEND":
            break
    if width is None or height is None:
        raise PngError("missing IHDR")
    channels = {0: 1, 2: 3, 4: 2, 6: 4}.get(color_type)
    if channels is None:
        raise PngError(f"color type {color_type} unsupported")
    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    expected = height * (stride + 1)
    if len(raw) < expected:
        raise PngError(f"truncated image data: {len(raw)} < {expected}")
    out = bytearray(width * height * 4)
    previous = bytearray(stride)
    pos = 0
    for y in range(height):
        filter_type = raw[pos]
        pos += 1
        row = bytearray(raw[pos:pos + stride])
        pos += stride
        if filter_type == 1:
            for i in range(channels, stride):
                row[i] = (row[i] + row[i - channels]) & 0xFF
        elif filter_type == 2:
            for i in range(stride):
                row[i] = (row[i] + previous[i]) & 0xFF
        elif filter_type == 3:
            for i in range(stride):
                left = row[i - channels] if i >= channels else 0
                row[i] = (row[i] + ((left + previous[i]) >> 1)) & 0xFF
        elif filter_type == 4:
            for i in range(stride):
                left = row[i - channels] if i >= channels else 0
                up_left = previous[i - channels] if i >= channels else 0
                row[i] = (row[i] + _paeth(left, previous[i], up_left)) & 0xFF
        elif filter_type != 0:
            raise PngError(f"unknown filter {filter_type}")
        if channels == 4:
            out[y * width * 4:(y + 1) * width * 4] = row
        elif channels == 3:
            for x in range(width):
                base = y * width * 4 + x * 4
                out[base:base + 3] = row[x * 3:x * 3 + 3]
                out[base + 3] = 255
        elif channels == 2:
            for x in range(width):
                base = y * width * 4 + x * 4
                value = row[x * 2]
                out[base:base + 3] = bytes((value, value, value))
                out[base + 3] = row[x * 2 + 1]
        else:
            for x in range(width):
                base = y * width * 4 + x * 4
                value = row[x]
                out[base:base + 3] = bytes((value, value, value))
                out[base + 3] = 255
        previous = row
    return PngImage(width=width, height=height, pixels=bytes(out))


def _chunk(kind: bytes, payload: bytes) -> bytes:
    return (struct.pack(">I", len(payload)) + kind + payload
            + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF))


def encode_png_rgba(width: int, height: int, pixels: bytes) -> bytes:
    if len(pixels) != width * height * 4:
        raise PngError("pixel buffer size mismatch")
    stride = width * 4
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        raw.extend(pixels[y * stride:(y + 1) * stride])
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (PNG_SIGNATURE + _chunk(b"IHDR", ihdr)
            + _chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + _chunk(b"IEND", b""))


def compare_images(before: bytes, before_w: int, before_h: int,
                   after: bytes, after_w: int, after_h: int,
                   max_mae: float, max_changed_ratio: float) -> dict:
    if (before_w, before_h) != (after_w, after_h):
        return {
            "ok": False, "reason": "SIZE_MISMATCH", "mae": None,
            "changed_ratio": None, "changed_pixels": None,
            "before_size": [before_w, before_h], "after_size": [after_w, after_h],
        }
    total_diff = 0
    changed_pixels = 0
    for index in range(0, len(before), 4):
        changed = False
        for offset in range(4):
            delta = abs(before[index + offset] - after[index + offset])
            total_diff += delta
            if delta:
                changed = True
        if changed:
            changed_pixels += 1
    pixels = before_w * before_h
    mae = total_diff / max(1, pixels * 4)
    changed_ratio = changed_pixels / max(1, pixels)
    ok = mae <= max_mae and changed_ratio <= max_changed_ratio
    return {
        "ok": ok,
        "reason": "" if ok else "THRESHOLD_EXCEEDED",
        "mae": mae,
        "changed_ratio": changed_ratio,
        "changed_pixels": changed_pixels,
        "pixels": pixels,
        "thresholds": {"max_mae": max_mae, "max_changed_ratio": max_changed_ratio},
    }


def _read_png(path: Path) -> PngImage:
    return decode_png(path.read_bytes())


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def cmd_capture(args: argparse.Namespace) -> int:
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    if not SERIAL_RE.match(args.serial):
        print("CAPTURE_FAILED: illegal serial", file=sys.stderr)
        return 2
    env = dict(os.environ)
    env["ANDROID_SERIAL"] = args.serial
    proc = subprocess.run(
        ["adb", "exec-out", "screencap", "-p"],
        capture_output=True, shell=False, env=env,
    )
    if proc.returncode != 0 or not proc.stdout.startswith(PNG_SIGNATURE):
        print("CAPTURE_FAILED: adb exec-out screencap -p", file=sys.stderr)
        print(proc.stderr.decode("utf-8", "replace")[:2000], file=sys.stderr)
        return 2
    out.write_bytes(proc.stdout)
    sidecar = {
        "file": out.as_posix(),
        "sha256": hashlib.sha256(proc.stdout).hexdigest(),
        "serial": args.serial,
        "apk_sha256": args.apk_sha,
        "captured_at": datetime.now(timezone.utc).isoformat(),
        "command": ["adb", "exec-out", "screencap", "-p"],
    }
    out.with_name(out.name + ".json").write_text(
        json.dumps(sidecar, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(sidecar, ensure_ascii=False, indent=2))
    return 0


def cmd_compare(args: argparse.Namespace) -> int:
    baseline, current = Path(args.baseline), Path(args.current)
    try:
        base = _read_png(baseline)
        cur = _read_png(current)
    except (OSError, PngError) as exc:
        print(f"PNG_ERROR: {exc}", file=sys.stderr)
        return 2
    result = compare_images(base.pixels, base.width, base.height,
                            cur.pixels, cur.width, cur.height,
                            args.max_mae, args.max_changed_ratio)
    result.update({
        "baseline": baseline.as_posix(),
        "baseline_sha256": _sha256(baseline),
        "current": current.as_posix(),
        "current_sha256": _sha256(current),
    })
    if args.out:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["ok"] else 1


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="设备截图基线/像素差")
    sub = parser.add_subparsers(dest="command", required=True)
    capture = sub.add_parser("capture")
    capture.add_argument("--serial", required=True)
    capture.add_argument("--out", required=True)
    capture.add_argument("--apk-sha")
    capture.set_defaults(func=cmd_capture)

    compare = sub.add_parser("compare")
    compare.add_argument("--baseline", required=True)
    compare.add_argument("--current", required=True)
    compare.add_argument("--max-mae", type=float, required=True)
    compare.add_argument("--max-changed-ratio", type=float, required=True)
    compare.add_argument("--out")
    compare.set_defaults(func=cmd_compare)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
