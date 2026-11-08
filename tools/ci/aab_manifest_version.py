#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 AAB 自己的 proto manifest 读 versionCode / versionName / package。

存在理由（消灭的具体失败）：AAB 的 ``base/manifest/AndroidManifest.xml`` 是 protobuf，
``aapt2`` 与 ``apkanalyzer`` 都读不了（本轮实测：aapt2 报 "could not identify format of APK"，
apkanalyzer 报 ``NoSuchFileException: /AndroidManifest.xml``），本机也没有可执行的
``bundletool-all``（rc 缓存里只有库 jar）。若不读 AAB 字节，发版只能拿 APK 的
``output-metadata.json`` 推断 AAB 的版本号 —— AAB 与 APK 若不同源，Play 收到的包会带一个
没人验过的 versionCode。

判据（fail-closed）：
- 必须存在 ``base/manifest/AndroidManifest.xml`` 且根元素名为 ``manifest``；
- ``android:versionCode`` 的字符串形式与编译后整数形式必须都存在且相等；
- 给了 ``--expected-code`` 时必须相等；给了 ``--expected-name`` 时必须相等；
- 任何一项读不出或不一致 → exit 1（不猜、不回退到 APK 元数据）。

schema 按 AGP 9.3.0 产出的 proto 实测布局（见 tools/tests/test_aab_manifest_version.py 的夹具）：
XmlNode{1:element}, XmlElement{1:ns,3:name,4:attribute,5:child},
XmlAttribute{1:ns,2:name,3:value,4:source,5:resource_id,6:compiled_item},
Item{7:primitive}, Primitive{6:int_decimal_value}。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

MANIFEST_ENTRY = "base/manifest/AndroidManifest.xml"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
RID_VERSION_CODE = 0x0101021B
RID_VERSION_NAME = 0x0101021C


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
    """Yield ``(field_number, wire_type, value)`` for one protobuf message."""
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


def _string_field(data: bytes, number: int) -> str | None:
    for field, wire, value in pb_fields(data):
        if field == number and wire == 2:
            return value.decode("utf-8", "replace")
    return None


def _varint_field(data: bytes, number: int) -> int | None:
    for field, wire, value in pb_fields(data):
        if field == number and wire == 0:
            return value
    return None


def _prim_int(attribute: bytes) -> int | None:
    """XmlAttribute.compiled_item → Item.primitive → Primitive.int_decimal_value."""
    item = None
    for field, wire, value in pb_fields(attribute):
        if field == 6 and wire == 2:
            item = value
    if item is None:
        return None
    primitive = None
    for field, wire, value in pb_fields(item):
        if field == 7 and wire == 2:
            primitive = value
    if primitive is None:
        return None
    return _varint_field(primitive, 6)


def manifest_attributes(manifest: bytes) -> tuple[str, list[dict]]:
    """Return ``(root_name, attributes)`` for the AAB prototype manifest."""
    root = None
    for field, wire, value in pb_fields(manifest):
        if field == 1 and wire == 2:
            root = value
    if root is None:
        raise ValueError("no root XmlNode.element in manifest")
    name = _string_field(root, 3)
    if not name:
        raise ValueError("root element has no name")
    attributes = []
    for field, wire, value in pb_fields(root):
        if field != 4 or wire != 2:
            continue
        attributes.append({
            "namespace": _string_field(value, 1),
            "name": _string_field(value, 2),
            "value": _string_field(value, 3),
            "resource_id": _varint_field(value, 5),
            "prim_int": _prim_int(value),
        })
    return name, attributes


def _pick(attributes: list[dict], resource_id: int | None, name: str) -> dict | None:
    for attribute in attributes:
        if resource_id is not None and attribute["resource_id"] == resource_id:
            return attribute
        if resource_id is None and attribute["name"] == name:
            return attribute
    return None


def inspect_aab(path: Path, expected_code: int | None = None,
                expected_name: str | None = None) -> dict:
    payload: dict = {
        "aab": path.as_posix(),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "manifest_entry": MANIFEST_ENTRY,
        "package": None,
        "version_code": None,
        "version_name": None,
        "version_code_sources": {"string": None, "compiled_int": None},
        "expected_code": expected_code,
        "expected_name": expected_name,
        "errors": [],
    }
    errors: list[str] = payload["errors"]
    try:
        with zipfile.ZipFile(path) as archive:
            manifest = archive.read(MANIFEST_ENTRY)
    except KeyError:
        errors.append(f"missing {MANIFEST_ENTRY}")
        return payload
    except (OSError, zipfile.BadZipFile) as error:
        errors.append(f"unreadable AAB: {type(error).__name__}: {error}")
        return payload
    try:
        root_name, attributes = manifest_attributes(manifest)
    except ValueError as error:
        errors.append(f"unparsable proto manifest: {error}")
        return payload
    if root_name != "manifest":
        errors.append(f"root element is {root_name!r}, expected 'manifest'")
    package_attr = _pick(attributes, None, "package")
    payload["package"] = package_attr["value"] if package_attr else None
    if not payload["package"]:
        errors.append("manifest package attribute missing")

    code_attr = _pick(attributes, RID_VERSION_CODE, "versionCode")
    if code_attr is None:
        errors.append("android:versionCode attribute missing")
    else:
        string_value = code_attr["value"]
        compiled = code_attr["prim_int"]
        payload["version_code_sources"] = {"string": string_value, "compiled_int": compiled}
        if string_value is None or string_value.strip() == "":
            errors.append("android:versionCode has no string value")
        elif not string_value.isdigit():
            errors.append(f"android:versionCode string is not an integer: {string_value!r}")
        if compiled is None:
            errors.append("android:versionCode has no compiled int value")
        if string_value and string_value.isdigit() and compiled is not None:
            if int(string_value) != compiled:
                errors.append(
                    f"android:versionCode string {string_value} != compiled {compiled}")
            else:
                payload["version_code"] = compiled

    name_attr = _pick(attributes, RID_VERSION_NAME, "versionName")
    if name_attr is None or not name_attr["value"]:
        errors.append("android:versionName attribute missing")
    else:
        payload["version_name"] = name_attr["value"]

    if expected_code is not None:
        if not (1 <= expected_code <= 2_100_000_000):
            errors.append("expected versionCode out of Play range 1..2100000000")
        if payload["version_code"] is not None and payload["version_code"] != expected_code:
            errors.append(
                f"AAB versionCode {payload['version_code']} != --expected-code {expected_code}")
    if expected_name is not None and payload["version_name"] not in (None, expected_name):
        errors.append(
            f"AAB versionName {payload['version_name']!r} != --expected-name {expected_name!r}")
    return payload


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="读 AAB proto manifest 的版本字段")
    parser.add_argument("--aab", required=True)
    parser.add_argument("--expected-code", type=int)
    parser.add_argument("--expected-name")
    parser.add_argument("--out")
    args = parser.parse_args(argv)
    payload = inspect_aab(Path(args.aab), args.expected_code, args.expected_name)
    if args.out:
        Path(args.out).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if not payload["errors"] else 1


if __name__ == "__main__":
    sys.exit(main())
