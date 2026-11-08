#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""``tools/ci/aab_manifest_version.py`` 的单测（批次 50 / 发布线）。

覆盖"能红"面：versionCode 字符串与编译整数不一致、缺属性、缺 manifest 条目、
期望值不符都必须 exit 1；正常夹具必须读出 101160 并 exit 0。
夹具按 AGP 9.3.0 产出的 proto 布局手写字节（不依赖真实 AAB）。
"""

from __future__ import annotations

import importlib.util
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "aab_manifest_version", ROOT / "tools" / "ci" / "aab_manifest_version.py")
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)

ANDROID_NS = "http://schemas.android.com/apk/res/android"


def varint(value: int) -> bytes:
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def field_varint(number: int, value: int) -> bytes:
    return varint(number << 3) + varint(value)


def field_bytes(number: int, payload: bytes) -> bytes:
    return varint((number << 3) | 2) + varint(len(payload)) + payload


def attribute(name: str, value: str | None = None, resource_id: int | None = None,
              compiled_int: int | None = None) -> bytes:
    body = field_bytes(1, ANDROID_NS.encode()) + field_bytes(2, name.encode())
    if value is not None:
        body += field_bytes(3, value.encode())
    if resource_id is not None:
        body += field_varint(5, resource_id)
    if compiled_int is not None:
        body += field_bytes(6, field_bytes(7, field_varint(6, compiled_int)))
    return body


def element(name: str, attributes: list[bytes]) -> bytes:
    body = field_bytes(3, name.encode())
    for item in attributes:
        body += field_bytes(4, item)
    return body


def manifest_bytes(package: str = "com.tingyun.smartmistakebook.offline",
                   version_code: str | None = "101160",
                   compiled: int | None = 101160,
                   version_name: str | None = "0.2.0-offline") -> bytes:
    attributes = [attribute("package", package)]
    if version_code is not None or compiled is not None:
        attributes.append(attribute("versionCode", version_code, M.RID_VERSION_CODE, compiled))
    if version_name is not None:
        attributes.append(attribute("versionName", version_name, M.RID_VERSION_NAME))
    return field_bytes(1, element("manifest", attributes))


def make_aab(path: Path, manifest: bytes | None) -> Path:
    with zipfile.ZipFile(path, "w") as archive:
        if manifest is not None:
            archive.writestr("base/manifest/AndroidManifest.xml", manifest)
        archive.writestr("BundleConfig.pb", bytes([0x08, 0x01, 0x10, 0x02]))
    return path


def test_reads_version_and_package(tmp_path: Path) -> None:
    aab = make_aab(tmp_path / "ok.aab", manifest_bytes())
    payload = M.inspect_aab(aab, 101160, "0.2.0-offline")
    assert payload["errors"] == []
    assert payload["version_code"] == 101160
    assert payload["version_name"] == "0.2.0-offline"
    assert payload["package"] == "com.tingyun.smartmistakebook.offline"
    assert payload["version_code_sources"] == {"string": "101160", "compiled_int": 101160}


def test_expected_code_mismatch_fails(tmp_path: Path) -> None:
    aab = make_aab(tmp_path / "mismatch.aab", manifest_bytes())
    assert M.main(["--aab", str(aab), "--expected-code", "101161"]) == 1


def test_expected_name_mismatch_fails(tmp_path: Path) -> None:
    aab = make_aab(tmp_path / "name.aab", manifest_bytes())
    assert M.main(["--aab", str(aab), "--expected-name", "0.2.0"]) == 1


def test_string_and_compiled_disagree_fails(tmp_path: Path) -> None:
    aab = make_aab(tmp_path / "disagree.aab",
                   manifest_bytes(version_code="101159", compiled=101160))
    payload = M.inspect_aab(aab)
    assert payload["version_code"] is None
    assert any("!=" in error for error in payload["errors"])


def test_missing_version_code_fails(tmp_path: Path) -> None:
    aab = make_aab(tmp_path / "absent.aab",
                   manifest_bytes(version_code=None, compiled=None))
    payload = M.inspect_aab(aab)
    assert any("versionCode attribute missing" in error for error in payload["errors"])


def test_missing_manifest_entry_fails(tmp_path: Path) -> None:
    aab = make_aab(tmp_path / "nomanifest.aab", None)
    assert M.main(["--aab", str(aab)]) == 1
    payload = M.inspect_aab(aab)
    assert any("missing base/manifest" in error for error in payload["errors"])


def test_out_file_written(tmp_path: Path) -> None:
    aab = make_aab(tmp_path / "out.aab", manifest_bytes())
    out = tmp_path / "result.json"
    assert M.main(["--aab", str(aab), "--expected-code", "101160", "--out", str(out)]) == 0
    assert json.loads(out.read_text(encoding="utf-8"))["version_code"] == 101160
