#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""J 域发布工程检查器（批次 44）。

它消灭的具体失败（每条都可指认）：

1. 树上的 AAB 是旧构建（含 32 位 ``.so``、缺 ``libquickjs.so``），
   ``bundletool validate`` 仍 exit 0，于是被当成"当前配置合格"——本工具的
   ``aab16k`` 同时核对 16KB 声明、ABI 集合与必需 native 库，任一不符即 exit 1。
2. 只看 ``adb install`` 的 exit code：实测失败时仍为 0。本工具不把 exit code
   当判词，``identity`` 输出 revision/version/ABI 等可判定字段，判词由调用方
   按输出行取。
3. ``retrace`` 对全限定混淆名原样返回且 exit 0——``r8`` 的 dex 绑定用原字节
   搜索 ``L<混淆名>;`` 并带 ``Lzzzzz9;`` 负控；只认命中数。
4. ``zipalign`` 是单点证据；``apk16k`` 用纯标准库独立解析 ZIP 本地头与 ELF
   程序头，且 ``--selftest`` 把 ``p_align`` 改成 ``0x1000`` 必须变红。

不运行 Gradle、不联网；只读产物与源码。退出码：0 = 本子命令判据成立；
1 = 判据不成立；2 = 参数/文件错误。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import locale
import re
import struct
import subprocess
import sys
import zipfile
from datetime import datetime
from pathlib import Path

PAGE = 0x4000
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552
ELF_MAGIC = b"\x7fELF"
R8_REMOVED = "R8$$REMOVED$$CLASS$$"
NEGATIVE_SYMBOL = b"Lzzzzz9;"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _decode(raw: bytes) -> str:
    """优先 UTF-8，失败回退本机 locale（zh_CN Windows 的 keytool 输出是 GBK）。

    旧实现固定 ``encoding="utf-8", errors="replace"``：keytool 的中文行被替换成
    乱码，``Owner:``/有效期永远读不到，J-025 的「证书已过期」因此看不见。
    """
    for encoding in ("utf-8", locale.getpreferredencoding(False) or "", "gbk"):
        if not encoding:
            continue
        try:
            return raw.decode(encoding)
        except (UnicodeDecodeError, LookupError):
            continue
    return raw.decode("utf-8", errors="replace")


def run_tool(args: list[str], cwd: Path | None = None) -> tuple[int, str]:
    """Run a tool; ``.bat`` needs ``cmd /c`` on Windows."""
    command = list(args)
    if sys.platform == "win32" and command and command[0].lower().endswith(".bat"):
        command = ["cmd", "/c", *command]
    try:
        completed = subprocess.run(
            command, cwd=str(cwd) if cwd else None,
            capture_output=True,
        )
    except OSError as error:
        return 127, f"{type(error).__name__}: {error}"
    return completed.returncode, _decode(completed.stdout or b"") + _decode(completed.stderr or b"")


def git(root: Path, *args: str) -> tuple[int, str]:
    return run_tool(["git", *args], cwd=root)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def parse_gradle_versions(text: str) -> dict:
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    abi = re.search(r"abiFilters\s*\+=\s*setOf\(([^)]*)\)", text)
    abis = re.findall(r'"([^"]+)"', abi.group(1)) if abi else []
    return {
        "version_code": int(code.group(1)) if code else None,
        "version_name": name.group(1) if name else None,
        "abi_filters": abis,
    }


def zip_names(path: Path) -> list[str]:
    with zipfile.ZipFile(path) as archive:
        return archive.namelist()


def so_abis(names: list[str]) -> list[str]:
    found = set()
    for name in names:
        parts = name.split("/")
        if not name.endswith(".so") or "lib" not in parts:
            continue
        index = parts.index("lib")
        if index + 1 < len(parts):
            found.add(parts[index + 1])
    return sorted(found)


def zip_integrity(path: Path) -> str | None:
    with zipfile.ZipFile(path) as archive:
        return archive.testzip()


def apk_revision(path: Path) -> str | None:
    with zipfile.ZipFile(path) as archive:
        try:
            raw = archive.read("META-INF/version-control-info.textproto")
        except KeyError:
            return None
    match = re.search(r'revision:\s*"([0-9a-fA-F]{7,40})"', raw.decode("utf-8", "replace"))
    return match.group(1) if match else None


def elf_program_headers(data: bytes):
    """Return ``(type, flags, vaddr, memsz, align)`` for ELF64; ``None`` otherwise."""
    if len(data) < 64 or data[:4] != ELF_MAGIC or data[4] != 2:
        return None
    machine_endian = data[5]
    order = "<" if machine_endian == 1 else ">"
    try:
        phoff = struct.unpack_from(order + "Q", data, 0x20)[0]
        phentsize = struct.unpack_from(order + "H", data, 0x36)[0]
        phnum = struct.unpack_from(order + "H", data, 0x38)[0]
    except struct.error:
        return None
    headers = []
    for index in range(phnum):
        base = phoff + index * phentsize
        try:
            p_type = struct.unpack_from(order + "I", data, base)[0]
            p_flags = struct.unpack_from(order + "I", data, base + 4)[0]
            p_vaddr = struct.unpack_from(order + "Q", data, base + 0x10)[0]
            p_memsz = struct.unpack_from(order + "Q", data, base + 0x28)[0]
            p_align = struct.unpack_from(order + "Q", data, base + 0x30)[0]
        except struct.error:
            return None
        headers.append((p_type, p_flags, p_vaddr, p_memsz, p_align))
    return headers


def elf_align_errors(data: bytes) -> list[str]:
    headers = elf_program_headers(data)
    if headers is None:
        return ["not an ELF64 image"]
    loads = [h for h in headers if h[0] == PT_LOAD]
    if not loads:
        return ["no PT_LOAD segment"]
    errors = [f"PT_LOAD p_align=0x{align:x} < 0x{PAGE:x}" for (_t, _f, _v, _m, align) in loads if align < PAGE]
    relro = [h for h in headers if h[0] == PT_GNU_RELRO]
    writable = [h for h in loads if h[1] & 0x2]
    if relro and writable:
        end = relro[0][2] + relro[0][3]
        if end % PAGE:
            limit = (end // PAGE + 1) * PAGE
            for (_t, _f, vaddr, memsz, _a) in writable:
                if vaddr < limit and vaddr + memsz > end:
                    errors.append(f"RELRO end 0x{end:x} shares page with RW LOAD @0x{vaddr:x}")
    return errors


def zip_data_offset(handle, info: zipfile.ZipInfo) -> int:
    handle.seek(info.header_offset)
    header = handle.read(30)
    name_len = struct.unpack_from("<H", header, 26)[0]
    extra_len = struct.unpack_from("<H", header, 28)[0]
    return info.header_offset + 30 + name_len + extra_len


def check_apk_16k(path: Path) -> dict:
    results = []
    errors = []
    with zipfile.ZipFile(path) as archive, path.open("rb") as handle:
        for info in archive.infolist():
            if not info.filename.endswith(".so"):
                continue
            data = archive.read(info.filename)
            offset = zip_data_offset(handle, info)
            item = {
                "name": info.filename,
                "stored": info.compress_type == 0,
                "offset_mod_16384": offset % 16384,
                "size": info.file_size,
                "elf_errors": elf_align_errors(data),
            }
            if not item["stored"]:
                item["elf_errors"].append("not STORED (deflated native library)")
            if item["offset_mod_16384"] != 0:
                item["elf_errors"].append("zip data offset not 16384-aligned")
            errors.extend(f"{info.filename}: {error}" for error in item["elf_errors"])
            results.append(item)
    return {"apk": path.as_posix(), "files": results, "errors": errors,
            "total": len(results)}


def selftest_apk_16k(path: Path) -> dict:
    """Patch the first ``.so`` to 0x1000 page alignment; the check must go red."""
    with zipfile.ZipFile(path) as archive:
        name = next((n for n in archive.namelist() if n.endswith(".so")), None)
        if name is None:
            return {"ok": False, "detail": "no .so in APK"}
        data = bytearray(archive.read(name))
    headers = elf_program_headers(bytes(data))
    if headers is None:
        return {"ok": False, "detail": "not ELF64"}
    order = "<"
    phoff = struct.unpack_from(order + "Q", data, 0x20)[0]
    phentsize = struct.unpack_from(order + "H", data, 0x36)[0]
    phnum = struct.unpack_from(order + "H", data, 0x38)[0]
    patched = 0
    for index in range(phnum):
        base = phoff + index * phentsize
        if struct.unpack_from(order + "I", data, base)[0] == PT_LOAD:
            struct.pack_into(order + "Q", data, base + 0x30, 0x1000)
            patched += 1
    errors = elf_align_errors(bytes(data))
    return {"ok": bool(errors), "patched_load_segments": patched,
            "errors": errors, "target": name}


def pb_varint(data: bytes, index: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while index < len(data):
        byte = data[index]
        index += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, index
        shift += 7
    raise ValueError("truncated varint")


def pb_fields(data: bytes):
    """Yield ``(field_number, wire_type, value)`` for a protobuf message."""
    index = 0
    while index < len(data):
        key, index = pb_varint(data, index)
        field, wire = key >> 3, key & 0x7
        if wire == 0:
            value, index = pb_varint(data, index)
        elif wire == 2:
            length, index = pb_varint(data, index)
            value = data[index:index + length]
            index += length
        elif wire == 1:
            value = data[index:index + 8]
            index += 8
        elif wire == 5:
            value = data[index:index + 4]
            index += 4
        else:
            raise ValueError(f"unsupported wire type {wire}")
        yield field, wire, value


def find_alignment(data: bytes, depth: int = 0) -> int | None:
    """Recursively find ``{1: enabled=1, 2: alignment}`` and return alignment."""
    if depth > 4:
        return None
    try:
        fields = list(pb_fields(data))
    except ValueError:
        return None
    simple = {field: value for field, wire, value in fields if wire == 0}
    if simple.get(1) == 1 and isinstance(simple.get(2), int):
        return simple[2]
    for field, wire, value in fields:
        if wire == 2 and isinstance(value, bytes):
            found = find_alignment(value, depth + 1)
            if found is not None:
                return found
    return None


def check_aab(path: Path, expected_abis: list[str], required_libs: list[str]) -> dict:
    names = zip_names(path)
    abis = so_abis(names)
    libs = sorted({name.rsplit("/", 1)[-1] for name in names if name.endswith(".so")})
    with zipfile.ZipFile(path) as archive:
        try:
            config = archive.read("BundleConfig.pb")
        except KeyError:
            config = b""
    alignment = find_alignment(config) if config else None
    errors = []
    if alignment != 2:
        errors.append(f"PAGE_ALIGNMENT_16K not declared (found {alignment})")
    if expected_abis and abis != sorted(expected_abis):
        errors.append(f"ABI set {abis} != abiFilters {sorted(expected_abis)}")
    for lib in required_libs:
        if lib not in libs:
            errors.append(f"required native library missing: {lib}")
    return {"aab": path.as_posix(), "sha256": sha256_file(path), "abi_set": abis,
            "native_libs": libs, "alignment": alignment, "errors": errors}


def parse_mapping(text: str) -> dict:
    original = []
    removed = 0
    renamed = []
    project_prefix = "com.tingyun.smartmistakebook."
    project_classes = 0
    project_renamed = 0
    project_removed = 0
    for line in text.splitlines():
        match = re.match(r"^(\S+) -> (\S+):$", line.strip())
        if not match:
            continue
        source, target = match.group(1), match.group(2)
        is_project = source.startswith(project_prefix)
        if R8_REMOVED in target:
            removed += 1
            if is_project:
                project_removed += 1
            continue
        original.append(source)
        if is_project:
            project_classes += 1
        source_last = source.rsplit(".", 1)[-1]
        target_last = target.rsplit(".", 1)[-1]
        if source_last != target_last:
            renamed.append((source, target))
            if is_project:
                project_renamed += 1
    return {"classes": len(original), "renamed": len(renamed), "removed": removed,
            "project_classes": project_classes, "project_renamed": project_renamed,
            "project_removed": project_removed,
            "renamed_classes": renamed}


def check_r8_binding(mapping: Path, apk: Path, sample: int = 5) -> dict:
    stats = parse_mapping(read_text(mapping))
    with zipfile.ZipFile(apk) as archive:
        dex = b"".join(archive.read(name) for name in archive.namelist()
                       if re.match(r"classes\d*\.dex$", name))
    project = [pair for pair in stats["renamed_classes"]
               if pair[0].startswith("com.tingyun.smartmistakebook.")]
    sample_pairs = project[:sample]
    hits = []
    for source, target in sample_pairs:
        token = f"L{target};".encode("utf-8")
        hits.append({"source": source, "target": target, "hits": dex.count(token)})
    negative = dex.count(NEGATIVE_SYMBOL)
    errors = []
    if len(sample_pairs) < min(sample, 5):
        errors.append(f"only {len(sample_pairs)} project renamed classes sampled")
    for item in hits:
        if item["hits"] <= 0:
            errors.append(f"no dex hit for {item['source']} -> {item['target']}")
    if negative != 0:
        errors.append(f"negative control {NEGATIVE_SYMBOL!r} hit {negative} times")
    return {"mapping": mapping.as_posix(), "classes": stats["classes"],
            "renamed": stats["renamed"], "removed": stats["removed"],
            "samples": hits, "negative_control_hits": negative, "errors": errors}


def check_kept_no_arg_constructors(text: str, class_names: list[str]) -> list[str]:
    """R8 ``usage.txt`` blocks that still contain a no-arg constructor are removed."""
    blocks: dict[str, list[str]] = {}
    current = None
    for raw in text.splitlines():
        if raw and not raw.startswith((" ", "\t")) and raw.rstrip().endswith(":"):
            current = raw.rstrip()[:-1]
            blocks.setdefault(current, [])
            continue
        if current is not None and raw.strip():
            blocks[current].append(raw.strip())
    violations = []
    for name in class_names:
        for member in blocks.get(name, []):
            if re.search(r"\bvoid <init>\(\)\s*$", member):
                violations.append(f"{name}: {member}")
    return violations


def apk_signer_digest(apksigner: Path, apk: Path) -> dict:
    code, output = run_tool([str(apksigner), "verify", "--print-certs", "--verbose", str(apk)])
    digest = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)", output)
    schemes = {
        "v1": bool(re.search(r"Verified using v1 scheme \(JAR signing\): true", output)),
        "v2": bool(re.search(r"Verified using v2 scheme \(APK Signature Scheme v2\): true", output)),
        "v3": bool(re.search(r"Verified using v3 scheme \(APK Signature Scheme v3\): true", output)),
    }
    return {"exit": code, "sha256": digest.group(1) if digest else None, "schemes": schemes}


_KEYTOOL_DATE_RE = re.compile(
    r"[A-Z][a-z]{2}\s+[A-Z][a-z]{2}\s+\d{1,2}\s+\d{2}:\d{2}:\d{2}\s+[A-Za-z]{2,5}\s+\d{4}")
_MONTHS = {name: index for index, name in enumerate(
    ("Jan", "Feb", "Mar", "Apr", "May", "Jun",
     "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"), 1)}


def _keytool_time(text: str) -> str | None:
    """把 keytool 的 ``Wed Sep 30 00:12:34 CST 2026`` 归一为 ISO 本地时间。

    ``strptime('%Z')`` 在 Windows/Python 3.13 上拒绝 ``CST``，所以手工拆字段。
    """
    match = _KEYTOOL_DATE_RE.search(text)
    if not match:
        return None
    _, mon, day, clock, _tz, year = match.group(0).split()
    hh, mm, ss = clock.split(":")
    month = _MONTHS.get(mon)
    if month is None:
        return None
    return f"{int(year):04d}-{month:02d}-{int(day):02d}T{hh}:{mm}:{ss}"


def parse_keytool_cert(output: str) -> dict:
    """解析 ``keytool -printcert`` 输出（英文与中文标签都支持）。

    J-025 的旧判据只认 ``Owner:``/``SHA256:``；中文 locale 下 owner 读成 null、
    有效期完全没读到，导致「证书已过期」无法被看见。这里同时取 owner 与
    not_before/not_after，缺失保持 None（由 signing_errors 判红）。
    """
    digest = re.search(r"SHA256:\s*([0-9A-Fa-f:]+)", output)
    owner = re.search(r"(?:Owner|所有者)\s*[:：]\s*(.+)", output)
    stamps = [match.group(0) for match in _KEYTOOL_DATE_RE.finditer(output)]
    return {
        "sha256": digest.group(1).replace(":", "").lower() if digest else None,
        "owner": owner.group(1).strip() if owner else None,
        "not_before": _keytool_time(stamps[0]) if stamps else None,
        "not_after": _keytool_time(stamps[-1]) if len(stamps) > 1 else None,
    }


def aab_signer_digest(aab: Path) -> dict:
    code, output = run_tool(["keytool", "-printcert", "-jarfile", str(aab)])
    facts = parse_keytool_cert(output)
    facts["exit"] = code
    return facts


def _norm_digest(value: str | None) -> str | None:
    if not value:
        return None
    return value.replace(":", "").strip().lower()


def signing_errors(state: dict, now: datetime | None = None) -> list[str]:
    """signing 判据必须 fail-closed：不能证明「生产签名环境 + 同一身份 + 有效期」。

    旧实现无论如何都 exit 0，批次 46 的日志因此出现「4/4 变量缺失、APK 是
    NotForRelease 临时钥匙、AAB 证书已过期、两者身份不同」却 exit 0 的形态，
    使 J-020/021/024/025/027 只能靠人工矛盾检查判红。
    """
    errors: list[str] = []
    missing = list(state.get("env_missing") or [])
    if missing:
        errors.append("release signing env missing: " + ", ".join(missing))
    apk = state.get("apk_signer") or {}
    aab = state.get("aab_signer") or {}
    apk_sha = _norm_digest(apk.get("sha256"))
    aab_sha = _norm_digest(aab.get("sha256"))
    if not apk_sha or not aab_sha:
        errors.append("signer artifact missing: APK and AAB signer digests are both required")
    elif apk_sha != aab_sha:
        errors.append(
            f"APK/AAB signer identity differs: apk={apk_sha[:16]} aab={aab_sha[:16]}")
    not_after = aab.get("not_after")
    if not not_after:
        errors.append("AAB certificate validity not readable")
    else:
        try:
            expiry = datetime.fromisoformat(not_after)
        except ValueError:
            errors.append(f"AAB certificate validity unparsable: {not_after!r}")
        else:
            if (now or datetime.now()) > expiry:
                errors.append(f"AAB certificate expired at {not_after}")
    return errors


def signing_state(root: Path, apk: Path | None, aab: Path | None,
                  apksigner: Path | None) -> dict:
    env_names = ["RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD",
                 "RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD"]
    import os
    present = [name for name in env_names if os.environ.get(name)]
    code, tracked = git(root, "ls-files", "--", "*.jks", "*.keystore")
    release_md = root / "RELEASE.md"
    doc = read_text(release_md) if release_md.exists() else ""
    state = {
        "env_present": present,
        "env_missing": [name for name in env_names if name not in present],
        "tracked_keystores": [line for line in tracked.splitlines() if line.strip()],
        "doc_mentions_keystore_properties": "keystore.properties" in doc,
        "doc_mentions_env_contract": "RELEASE_KEY_ALIAS" in doc,
    }
    if apk and apksigner and apksigner.exists():
        state["apk_signer"] = apk_signer_digest(apksigner, apk)
    if aab:
        state["aab_signer"] = aab_signer_digest(aab)
    return state


def _zip_snapshot(path: Path) -> dict:
    """读取 ZIP 产物；损坏时返回可判词，而不是抛异常或写 null 假绿。"""
    try:
        names = zip_names(path)
        return {
            "readable": True,
            "revision": apk_revision(path),
            "zip_integrity": zip_integrity(path),
            "so_abis": so_abis(names),
            "native_libs": sorted({n.rsplit("/", 1)[-1] for n in names
                                   if n.endswith(".so")}),
            "error": None,
        }
    except (zipfile.BadZipFile, OSError, KeyError) as exc:
        return {
            "readable": False,
            "revision": None,
            "zip_integrity": f"unreadable: {type(exc).__name__}: {exc}",
            "so_abis": [],
            "native_libs": [],
            "error": f"{type(exc).__name__}: {exc}",
        }


def identity_report(root: Path, apk: Path, aab: Path, mapping: Path,
                    gradle: Path, metadata: Path) -> dict:
    code, head = git(root, "rev-parse", "HEAD")
    head = head.strip() if code == 0 else None
    _, status = git(root, "status", "--porcelain", "-uall")
    dirty = [line for line in status.splitlines() if line.strip()]
    apk_snapshot = _zip_snapshot(apk)
    aab_snapshot = _zip_snapshot(aab)
    revision = apk_snapshot["revision"]
    distance = None
    revision_is_commit = False
    if revision and head:
        code_type, _ = git(root, "cat-file", "-t", revision)
        revision_is_commit = code_type == 0
        if revision_is_commit:
            code_count, count = git(root, "rev-list", "--count", f"{revision}..HEAD")
            distance = int(count.strip()) if code_count == 0 else None
    gradle_info = parse_gradle_versions(read_text(gradle))
    metadata_info = json.loads(read_text(metadata)) if metadata.exists() else {}
    element = (metadata_info.get("elements") or [{}])[0]
    mapping_text = read_text(mapping) if mapping.exists() else ""
    mapping_stats = parse_mapping(mapping_text)
    return {
        "head": head,
        "dirty_paths": len(dirty),
        "dirty_sample": dirty[:10],
        "apk": {"path": apk.as_posix(), "sha256": sha256_file(apk),
                "revision": revision, "revision_is_commit": revision_is_commit,
                "commits_behind_head": distance,
                "readable": apk_snapshot["readable"],
                "zip_integrity": apk_snapshot["zip_integrity"],
                "so_abis": apk_snapshot["so_abis"]},
        "aab": {"path": aab.as_posix(), "sha256": sha256_file(aab),
                "readable": aab_snapshot["readable"],
                "zip_integrity": aab_snapshot["zip_integrity"],
                "so_abis": aab_snapshot["so_abis"],
                "native_libs": aab_snapshot["native_libs"]},
        "mapping": {"path": mapping.as_posix(), "sha256": sha256_file(mapping),
                    "classes": mapping_stats["classes"],
                    "renamed": mapping_stats["renamed"],
                    "removed": mapping_stats["removed"],
                    "project_classes": mapping_stats["project_classes"],
                    "project_renamed": mapping_stats["project_renamed"],
                    "project_removed": mapping_stats["project_removed"]},
        "versions": {"gradle": gradle_info,
                     "output_metadata": {"versionCode": element.get("versionCode"),
                                         "versionName": element.get("versionName")}},
    }


def write_json(path: Path | None, payload: dict) -> None:
    if path is None:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8", newline="\n")


def emit(payload: dict, out: Path | None, ok: bool) -> int:
    write_json(out, payload)
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if ok else 1


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="J 域发布工程检查器")
    sub = parser.add_subparsers(dest="command", required=True)

    identity = sub.add_parser("identity")
    identity.add_argument("--root", required=True)
    identity.add_argument("--apk", required=True)
    identity.add_argument("--aab", required=True)
    identity.add_argument("--mapping", required=True)
    identity.add_argument("--gradle", required=True)
    identity.add_argument("--metadata", required=True)
    identity.add_argument("--out")
    identity.add_argument("--require-clean", action="store_true")
    identity.add_argument("--require-head", action="store_true")

    apk16k = sub.add_parser("apk16k")
    apk16k.add_argument("--apk", required=True)
    apk16k.add_argument("--selftest", action="store_true")
    apk16k.add_argument("--out")

    aab16k = sub.add_parser("aab16k")
    aab16k.add_argument("--aab", required=True)
    aab16k.add_argument("--abis", default="arm64-v8a,x86_64")
    aab16k.add_argument("--require-libs", default="libquickjs.so")
    aab16k.add_argument("--out")

    r8 = sub.add_parser("r8")
    r8.add_argument("--mapping", required=True)
    r8.add_argument("--apk", required=True)
    r8.add_argument("--out")

    signing = sub.add_parser("signing")
    signing.add_argument("--root", required=True)
    signing.add_argument("--apk")
    signing.add_argument("--aab")
    signing.add_argument("--apksigner")
    signing.add_argument("--out")

    version = sub.add_parser("version")
    version.add_argument("--gradle", required=True)
    version.add_argument("--metadata", required=True)
    version.add_argument("--name-suffix", default="-offline")
    # 批次 50：versionCode 改为构建期注入（-Psmbk.versionCode），gradle 文本里没有字面数字。
    # 注入形态必须显式给期望值，门才能继续 fail-closed；字面量形态仍按老路比对。
    version.add_argument("--expected-code", type=int)
    version.add_argument("--out")

    r8keep = sub.add_parser("r8keep")
    r8keep.add_argument("--usage", required=True)
    r8keep.add_argument("--classes", required=True)
    r8keep.add_argument("--out")

    args = parser.parse_args(argv)

    if args.command == "identity":
        payload = identity_report(Path(args.root), Path(args.apk), Path(args.aab),
                                  Path(args.mapping), Path(args.gradle), Path(args.metadata))
        # ZIP 完整性是判词，不是 payload 说明：坏成员或不可读必须 fail-closed。
        ok = (payload["apk"]["zip_integrity"] is None
              and payload["aab"]["zip_integrity"] is None)
        if args.require_clean and payload["dirty_paths"] != 0:
            ok = False
        if args.require_head and (not payload["apk"]["revision_is_commit"]
                                  or payload["apk"]["commits_behind_head"] != 0):
            ok = False
        return emit(payload, Path(args.out) if args.out else None, ok)

    if args.command == "apk16k":
        if args.selftest:
            payload = selftest_apk_16k(Path(args.apk))
            return emit(payload, Path(args.out) if args.out else None, bool(payload["ok"]))
        payload = check_apk_16k(Path(args.apk))
        return emit(payload, Path(args.out) if args.out else None, not payload["errors"])

    if args.command == "aab16k":
        expected = [item for item in args.abis.split(",") if item]
        required = [item for item in args.require_libs.split(",") if item]
        payload = check_aab(Path(args.aab), expected, required)
        return emit(payload, Path(args.out) if args.out else None, not payload["errors"])

    if args.command == "r8":
        payload = check_r8_binding(Path(args.mapping), Path(args.apk))
        return emit(payload, Path(args.out) if args.out else None, not payload["errors"])

    if args.command == "signing":
        payload = signing_state(
            Path(args.root),
            Path(args.apk) if args.apk else None,
            Path(args.aab) if args.aab else None,
            Path(args.apksigner) if args.apksigner else None,
        )
        payload["errors"] = signing_errors(payload)
        return emit(payload, Path(args.out) if args.out else None, not payload["errors"])

    if args.command == "r8keep":
        text = read_text(Path(args.usage))
        classes = [item for item in args.classes.split(",") if item]
        violations = check_kept_no_arg_constructors(text, classes)
        payload = {"usage": args.usage, "classes": classes, "violations": violations}
        return emit(payload, Path(args.out) if args.out else None, not violations)

    if args.command == "version":
        gradle_info = parse_gradle_versions(read_text(Path(args.gradle)))
        metadata = json.loads(read_text(Path(args.metadata))) if Path(args.metadata).exists() else {}
        element = (metadata.get("elements") or [{}])[0]
        expected = args.expected_code
        payload = {
            "gradle": gradle_info,
            "output_metadata": {"versionCode": element.get("versionCode"),
                                "versionName": element.get("versionName")},
            "expected_code": expected,
            "errors": [],
        }
        # versionCode 有两个合法来源：gradle 字面量（历史包）或构建参数注入（批次 50 起）。
        # 注入形态下 gradle 文本里没有数字，必须由发布脚本给 --expected-code；不给就红，
        # 不允许拿 output-metadata 自证（那样门只剩自洽，丢了"期望值"这个独立输入）。
        if gradle_info["version_name"] is None:
            payload["errors"].append("versionName not found in gradle script")
        if expected is None:
            if gradle_info["version_code"] is None:
                payload["errors"].append(
                    "versionCode is injected at build time; pass --expected-code "
                    "(docs/research/2026-11-09-release-signing.md §3.4.1)")
            elif element.get("versionCode") != gradle_info["version_code"]:
                payload["errors"].append("versionCode differs from output-metadata")
        else:
            if not (1 <= expected <= 2100000000):
                payload["errors"].append("expected versionCode out of Play range 1..2100000000")
            if gradle_info["version_code"] is not None and gradle_info["version_code"] != expected:
                payload["errors"].append("gradle versionCode differs from --expected-code")
            if element.get("versionCode") != expected:
                payload["errors"].append("output-metadata versionCode != --expected-code")
        if gradle_info["version_name"] is not None and element.get("versionName") not in (
                gradle_info["version_name"], f"{gradle_info['version_name']}{args.name_suffix}"):
            payload["errors"].append("versionName differs from output-metadata")
        if gradle_info["version_code"] is not None and not (1 <= gradle_info["version_code"] <= 2100000000):
            payload["errors"].append("versionCode out of Play range 1..2100000000")
        return emit(payload, Path(args.out) if args.out else None, not payload["errors"])

    return 2


if __name__ == "__main__":
    sys.exit(main())
