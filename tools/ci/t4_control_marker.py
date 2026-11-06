#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T4 控制轮可核标记（批次 41 / WS4 item 27）。

## 它消灭的具体失败

批次 40 的隔离结论卡在"T4 关联机制上不可能，但无法做去 T4 控制轮"：
`step04-isolation.md` 的 I50-6/I50-7 是"6 处写点回退、文件级备份/还原、sha 校验"，
但没有任何**机器可核**的产物——构建日志里没有标记、控制臂没有引擎 sha，谁也
无法从证据判断"这次构建到底含不含 T4 打点"。批次 39 只能写"机制反证"。

本工具只做**可核标记**，不执行回退（回退是 `app/src/main/.../VisualJsClientEngine.kt`
的临时改动，属落码轨/主会话授权范围）：

1. `emit --build-log <log> (--apk <apk> | --serial <device>)`：记录
   - `engine_sha256`（整文件）；
   - 6 处打点行的行号 + 行级 sha256（5 个语义写点，`readyAt` 两处成功出口）；
   - 构建日志是否含 `T4_REVERT_APPLIED`；
   - **APK 身份**（F8）：`--serial` 时从 `pm path` 读**已安装** APK 的 base sha
     与 dex 类清单（含分包并集）；`--apk` 时读构建产物的同一身份。两者都没有 →
     标记无效——源码树一致不能证明设备上装的是同一份构建；两者都有时还必须
     sha/类清单一致（`installed_apk_differs_from_artifact`），否则源码树→产物→
     已安装包的链条断开；
   - `arm`: `ON_PRODUCTION_T4_PRESENT`（6 处打点在场）或
     `OFF_PRODUCTION_T4_REMOVED`（0 处且日志声明已回退）。
2. `verify --marker <json>`：重算当前树 + 重新读取 marker 记录的 APK 身份（已安装
   优先），引擎 sha / 行级 sha / APK sha / 类清单任一漂移即失败。

## fail-closed

- 日志声明 `T4_REVERT_APPLIED` 但源码仍有打点行 → **无效**（exit 1）；
- 没有声明回退但打点数 ≠ 6 → **无效**（既不是完整 ON 也不是 OFF）；
- 没有任何 APK 身份（既无 `--apk` 也无 `--serial`）→ **无效**（exit 1）；
- 缺构建日志/缺引擎文件/设备或 APK 不可读 → exit 2（没有"通过"可言）。

## 用法

    python -I tools/ci/t4_control_marker.py emit \
        --build-log build/control/build.log --serial emulator-5584 \
        --package com.tingyun.smartmistakebook.offline \
        --out build/control/t4.marker.json
    python -I tools/ci/t4_control_marker.py verify \
        --marker build/control/t4.marker.json
    # 离线/无设备时用构建产物身份：
    python -I tools/ci/t4_control_marker.py emit \
        --build-log build/control/build.log --apk build/off/app.apk \
        --out build/control/t4.marker.json

配合 `tools/ci/run_isolation_pair.sh --off-arm-production-t4`：只有 `verify` 通过、
且标记 `arm=OFF_PRODUCTION_T4_REMOVED` 时，配对报告才允许讨论 T4 因果。
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import subprocess
import sys
import time
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ENGINE_PATH = "app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt"
REVERT_MARKER = "T4_REVERT_APPLIED"
EXPECTED_ANCHORS = 6
DEFAULT_PACKAGE = "com.tingyun.smartmistakebook.offline"
DEX_ENTRY = re.compile(r"classes\d*\.dex")
ANCHOR_NEEDLES = (
    "timestamps.bindRequestedAtMillis =",
    "timestamps.bindConnectedAtMillis =",
    "timestamps.readyAtMillis =",
    "timestamps.executeAcceptedAtMillis =",
    "timestamps.firstByteAtMillis =",
)
EXIT_OK = 0
EXIT_INVALID = 1
EXIT_INPUT = 2


def _sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _sha256_text(text: str) -> str:
    return _sha256_bytes(text.encode("utf-8"))


def find_anchors(source: str) -> list[dict]:
    """定位 T4 打点行；返回行号（1-based）与行级 sha（去行尾空白后）。"""
    anchors = []
    for number, line in enumerate(source.splitlines(), start=1):
        stripped = line.strip()
        if any(needle in stripped for needle in ANCHOR_NEEDLES):
            anchors.append({
                "line": number,
                "anchor": next(needle for needle in ANCHOR_NEEDLES if needle in stripped),
                "line_sha256": _sha256_text(stripped),
            })
    return anchors


def _u4(data: bytes, offset: int) -> int:
    if offset < 0 or offset + 4 > len(data):
        raise ValueError(f"truncated dex at 0x{offset:x}")
    return int.from_bytes(data[offset:offset + 4], "little")


def _uleb128(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while True:
        if offset >= len(data):
            raise ValueError("truncated uleb128")
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, offset
        shift += 7
        if shift > 35:
            raise ValueError("uleb128 is too long")


def dex_class_descriptors(data: bytes) -> list[str]:
    """从 dex 的 class_defs 读类描述符（如 `Lx/Engine;`）。

    只读 DEX 头/string_ids/type_ids/class_defs 三张表，不执行任何代码；
    形状不对时抛 ValueError（调用方 fail-closed，不把"解析失败"当成空类清单）。
    """
    if len(data) < 0x70 or not data.startswith(b"dex\n"):
        raise ValueError("not a dex file")
    string_ids_size = _u4(data, 0x38)
    string_ids_off = _u4(data, 0x3C)
    type_ids_size = _u4(data, 0x40)
    type_ids_off = _u4(data, 0x44)
    class_defs_size = _u4(data, 0x60)
    class_defs_off = _u4(data, 0x64)
    descriptors: list[str] = []
    for index in range(class_defs_size):
        class_idx = _u4(data, class_defs_off + index * 32)
        if class_idx >= type_ids_size:
            raise ValueError(f"class_defs[{index}] class_idx out of range")
        descriptor_idx = _u4(data, type_ids_off + class_idx * 4)
        if descriptor_idx >= string_ids_size:
            raise ValueError(f"class_defs[{index}] descriptor_idx out of range")
        string_data_off = _u4(data, string_ids_off + descriptor_idx * 4)
        length, cursor = _uleb128(data, string_data_off)
        raw = data[cursor:cursor + length]
        if len(raw) != length:
            raise ValueError(f"class_defs[{index}] descriptor is truncated")
        descriptors.append(raw.decode("utf-8", errors="replace"))
    return sorted(set(descriptors))


def _apk_identity_from_bytes(data: bytes) -> dict:
    dex_entries: list[dict] = []
    classes: set[str] = set()
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in sorted(archive.namelist()):
            if not DEX_ENTRY.fullmatch(name):
                continue
            dex = archive.read(name)
            entry_classes = dex_class_descriptors(dex)
            dex_entries.append({
                "name": name,
                "sha256": _sha256_bytes(dex),
                "classes": entry_classes,
            })
            classes.update(entry_classes)
    if not dex_entries:
        raise ValueError("apk has no classes*.dex entry")
    return {
        "sha256": _sha256_bytes(data),
        "size": len(data),
        "dex_entries": dex_entries,
        "classes": sorted(classes),
    }


def file_apk_identity(apk_path: Path) -> dict:
    """已构建 APK 的 sha + dex 类清单（离线可核，不依赖设备）。"""
    data = apk_path.read_bytes()
    identity = _apk_identity_from_bytes(data)
    identity["path"] = str(apk_path)
    return identity


def installed_apk_identity(adb: str, serial: str, package: str) -> dict:
    """从设备读取**已安装** APK 的 sha + 类清单（base APK 身份 + 全部分包类并集）。"""
    listing = subprocess.run(
        [adb, "-s", serial, "shell", "pm", "path", package],
        capture_output=True, text=True, timeout=120,
    )
    if listing.returncode != 0:
        raise RuntimeError(f"pm path {package} failed: {listing.stderr.strip()}")
    paths = sorted(
        line.split("package:", 1)[1].strip()
        for line in listing.stdout.splitlines()
        if line.startswith("package:")
    )
    if not paths:
        raise RuntimeError(f"package not installed: {package}")

    base_path = next((path for path in paths if path.endswith("/base.apk")), paths[0])
    classes: set[str] = set()
    sha_by_path: dict[str, str] = {}
    base_identity: dict | None = None
    for path in paths:
        pulled = subprocess.run(
            [adb, "-s", serial, "exec-out", "cat", path],
            capture_output=True, timeout=300,
        )
        if pulled.returncode != 0:
            raise RuntimeError(f"cannot read installed apk {path}: {pulled.stderr!r}")
        identity = _apk_identity_from_bytes(pulled.stdout)
        sha_by_path[path] = identity["sha256"]
        classes.update(identity["classes"])
        if path == base_path:
            base_identity = identity
    if base_identity is None:
        raise RuntimeError(f"base apk not found among {paths}")
    return {
        "package": package,
        "serial": serial,
        "adb": adb,
        "apk_paths": paths,
        "base_path": base_path,
        "sha256": base_identity["sha256"],
        "apk_sha256_by_path": sha_by_path,
        "dex_entries": base_identity["dex_entries"],
        "classes": sorted(classes),
    }


def _identity_drift(stored: dict, current: dict) -> list[str]:
    """比较 marker 里记录的 APK 身份与当前身份；sha/类清单任一漂移都算失败。"""
    reasons: list[str] = []
    if stored.get("sha256") != current.get("sha256"):
        reasons.append("apk_sha_drift")
    if sorted(stored.get("classes", [])) != sorted(current.get("classes", [])):
        reasons.append("apk_classes_drift")
    if stored.get("apk_sha256_by_path") and \
            stored.get("apk_sha256_by_path") != current.get("apk_sha256_by_path"):
        reasons.append("apk_split_sha_drift")
    return reasons


def build_marker(repo: Path, build_log: str, *,
                 apk_identity: dict | None = None,
                 installed_identity: dict | None = None) -> dict:
    engine = repo / ENGINE_PATH
    if not engine.is_file():
        return {"ok": False, "reasons": ["engine_missing"]}
    raw = engine.read_bytes()
    source = raw.decode("utf-8", errors="replace")
    anchors = find_anchors(source)
    claims_reverted = REVERT_MARKER in build_log

    reasons: list[str] = []
    if claims_reverted and anchors:
        reasons.append("marker_claims_removed_but_anchors_present")
    if not claims_reverted and len(anchors) != EXPECTED_ANCHORS:
        reasons.append("anchor_count")
    # F8：源码树一致不等于**已安装**的包一致。OFF/ON 两臂可能装着不同构建，
    # 只比源码树会让"树上是 OFF、设备上是 ON"通过控制轮校验。这里必须至少有
    # 一份 APK 身份（已安装优先）；两者都没有 → 不可核 → INVALID。
    if installed_identity is None and apk_identity is None:
        reasons.append("no_apk_identity")
    if apk_identity is not None and installed_identity is not None:
        # 源码树 → 构建产物 → 已安装包必须同源；只记录两份身份而不对拍，仍可能
        # "树上 OFF、设备上 ON"。
        if _identity_drift(apk_identity, installed_identity):
            reasons.append("installed_apk_differs_from_artifact")
    identity_source = (
        "installed_apk" if installed_identity is not None
        else ("artifact_apk" if apk_identity is not None else "none")
    )
    if claims_reverted and anchors:
        arm = "INVALID"
    elif claims_reverted:
        arm = "OFF_PRODUCTION_T4_REMOVED"
    else:
        arm = "ON_PRODUCTION_T4_PRESENT"

    return {
        "ok": not reasons,
        "reasons": reasons,
        "arm": arm,
        "engine_path": ENGINE_PATH,
        "engine_sha256": _sha256_bytes(raw),
        "anchor_count": len(anchors),
        "expected_anchor_count": EXPECTED_ANCHORS,
        "anchors": anchors,
        "revert_marker_seen": claims_reverted,
        "build_log_sha256": _sha256_text(build_log),
        "identity_source": identity_source,
        "apk_identity": apk_identity,
        "installed_apk_identity": installed_identity,
        "generated_at_epoch": time.time(),
    }


def verify_marker(repo: Path, marker: dict, *,
                  apk_identity: dict | None = None,
                  installed_identity: dict | None = None) -> dict:
    reasons: list[str] = []
    current = build_marker(
        repo,
        REVERT_MARKER if marker.get("revert_marker_seen") else "",
        apk_identity=apk_identity,
        installed_identity=installed_identity,
    )
    if not current.get("ok") and marker.get("arm") != "OFF_PRODUCTION_T4_REMOVED":
        reasons.extend(current.get("reasons", []))
    if current.get("engine_sha256") != marker.get("engine_sha256"):
        reasons.append("engine_sha_drift")
    if marker.get("arm") == "OFF_PRODUCTION_T4_REMOVED":
        if current.get("anchor_count") != 0:
            reasons.append("off_arm_anchors_present")
    elif marker.get("arm") == "ON_PRODUCTION_T4_PRESENT":
        if current.get("anchor_count") != EXPECTED_ANCHORS:
            reasons.append("on_arm_anchor_count_drift")
        current_lines = {(item["line"], item["line_sha256"]) for item in current.get("anchors", [])}
        marker_lines = {(item["line"], item["line_sha256"]) for item in marker.get("anchors", [])}
        if current_lines != marker_lines:
            reasons.append("anchor_line_drift")
    else:
        reasons.append("marker_arm_unrecognized")

    stored_installed = marker.get("installed_apk_identity")
    stored_artifact = marker.get("apk_identity")
    if stored_installed is not None:
        if installed_identity is None:
            reasons.append("installed_apk_unavailable")
        else:
            reasons.extend(_identity_drift(stored_installed, installed_identity))
    if stored_artifact is not None:
        if apk_identity is None:
            reasons.append("artifact_apk_unavailable")
        else:
            reasons.extend(_identity_drift(stored_artifact, apk_identity))
    if stored_installed is None and stored_artifact is None:
        reasons.append("no_apk_identity")
    if apk_identity is not None and installed_identity is not None:
        if _identity_drift(apk_identity, installed_identity):
            reasons.append("installed_apk_differs_from_artifact")

    return {
        "ok": not reasons,
        "reasons": reasons,
        "arm": marker.get("arm"),
        "engine_sha256": current.get("engine_sha256"),
        "anchor_count": current.get("anchor_count"),
        "identity_source": marker.get("identity_source", "none"),
        "apk_sha256": (stored_installed or stored_artifact or {}).get("sha256", ""),
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="auditable T4 control-round marker")
    sub = parser.add_subparsers(dest="command", required=True)

    emit = sub.add_parser("emit", help="record the current engine state against a build log")
    emit.add_argument("--repo", default=str(REPO))
    emit.add_argument("--build-log", required=True)
    emit.add_argument("--out", required=True)
    emit.add_argument("--apk", help="built APK artifact (sha + dex class inventory)")
    emit.add_argument("--adb", default="adb")
    emit.add_argument("--serial", help="device serial; reads the installed APK identity")
    emit.add_argument("--package", default=DEFAULT_PACKAGE)

    verify = sub.add_parser("verify", help="re-check a marker against tree + installed APK")
    verify.add_argument("--repo", default=str(REPO))
    verify.add_argument("--marker", required=True)
    verify.add_argument("--apk", help="built APK artifact (defaults to the path stored in the marker)")
    verify.add_argument("--adb", default=None,
                        help="adb binary (defaults to the marker's recorded adb, else 'adb')")
    verify.add_argument("--serial", help="device serial (defaults to the serial stored in the marker)")
    verify.add_argument("--package", help="package name (defaults to the marker/package default)")

    args = parser.parse_args(argv)
    repo = Path(args.repo)

    def capture_identity(apk_path: str | None, serial: str | None,
                         package: str | None,
                         adb: str | None) -> tuple[dict | None, dict | None, str | None]:
        artifact = installed = None
        error = None
        adb_binary = adb or "adb"
        if apk_path:
            try:
                artifact = file_apk_identity(Path(apk_path))
            except (OSError, ValueError, zipfile.BadZipFile) as failure:
                error = f"cannot read apk {apk_path}: {failure}"
                return None, None, error
        if serial:
            try:
                installed = installed_apk_identity(
                    adb_binary, serial, package or DEFAULT_PACKAGE
                )
            except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired,
                    zipfile.BadZipFile) as failure:
                error = f"cannot read installed apk on {serial}: {failure}"
                return artifact, None, error
        return artifact, installed, error

    if args.command == "emit":
        try:
            build_log = Path(args.build_log).read_text(encoding="utf-8", errors="replace")
        except OSError as failure:
            print(f"t4_control_marker: cannot read build log: {failure}", file=sys.stderr)
            return EXIT_INPUT
        apk_identity, installed_identity, error = capture_identity(
            args.apk, args.serial, args.package, args.adb
        )
        if error:
            print(f"t4_control_marker: {error}", file=sys.stderr)
            return EXIT_INPUT
        marker = build_marker(
            repo, build_log,
            apk_identity=apk_identity,
            installed_identity=installed_identity,
        )
        if marker.get("reasons") == ["engine_missing"]:
            print("t4_control_marker: engine source missing", file=sys.stderr)
            return EXIT_INPUT
        target = Path(args.out)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(marker, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(marker, ensure_ascii=False))
        return EXIT_OK if marker["ok"] else EXIT_INVALID

    try:
        marker = json.loads(Path(args.marker).read_text(encoding="utf-8"))
    except (OSError, ValueError) as failure:
        print(f"t4_control_marker: cannot read marker: {failure}", file=sys.stderr)
        return EXIT_INPUT
    stored_installed = marker.get("installed_apk_identity") or {}
    stored_artifact = marker.get("apk_identity") or {}
    apk_identity, installed_identity, error = capture_identity(
        args.apk or stored_artifact.get("path"),
        args.serial or stored_installed.get("serial"),
        args.package or stored_installed.get("package"),
        args.adb or stored_installed.get("adb"),
    )
    if error:
        # fail-closed：设备/工件不可读时不得回退成"只比源码树"。
        print(f"t4_control_marker: {error}", file=sys.stderr)
        return EXIT_INPUT
    result = verify_marker(
        repo, marker,
        apk_identity=apk_identity,
        installed_identity=installed_identity,
    )
    print(json.dumps(result, ensure_ascii=False))
    return EXIT_OK if result["ok"] else EXIT_INVALID


if __name__ == "__main__":
    sys.exit(main())
