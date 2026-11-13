#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""``tools/ci/j_release_verify.py`` 的单测（批次 44 / J 域）。

覆盖四类判据的"能红"面：
- ELF64 ``PT_LOAD.p_align`` 小于 ``0x4000`` 必须报错；
- AAB 未声明 ``PAGE_ALIGNMENT_16K``、ABI 集合不符或缺 ``libquickjs.so`` 必须报错；
- mapping 的 ``R8$$REMOVED$$CLASS$$`` 墓碑不计入类数，dex 绑定负数控制必须为 0；
- 版本号缺失/与 output-metadata 不一致必须报错。
"""

from __future__ import annotations

import importlib.util
import os
import shutil
import struct
import subprocess
import sys
import zipfile
from datetime import datetime
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
RETRACE_NAME = "retrace.bat" if os.name == "nt" else "retrace"
SDK_ROOT = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "C:/Android/Sdk")
RETRACE = Path(shutil.which(RETRACE_NAME) or SDK_ROOT / "cmdline-tools/latest/bin" / RETRACE_NAME)
SPEC = importlib.util.spec_from_file_location(
    "j_release_verify", ROOT / "tools" / "ci" / "j_release_verify.py")
J = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(J)


def make_elf(align: int) -> bytes:
    header = bytearray(64)
    header[0:4] = b"\x7fELF"
    header[4] = 2  # ELF64
    header[5] = 1  # little endian
    struct.pack_into("<Q", header, 0x20, 64)
    struct.pack_into("<H", header, 0x36, 56)
    struct.pack_into("<H", header, 0x38, 2)
    load = bytearray(56)
    struct.pack_into("<I", load, 0, 1)          # PT_LOAD
    struct.pack_into("<I", load, 4, 5)          # R|X
    struct.pack_into("<Q", load, 0x10, 0)
    struct.pack_into("<Q", load, 0x28, 0x100)
    struct.pack_into("<Q", load, 0x30, align)
    relro = bytearray(56)
    struct.pack_into("<I", relro, 0, 0x6474E552)  # PT_GNU_RELRO
    struct.pack_into("<I", relro, 4, 4)
    struct.pack_into("<Q", relro, 0x10, 0)
    struct.pack_into("<Q", relro, 0x28, 0x80)
    struct.pack_into("<Q", relro, 0x30, 0x4000)
    return bytes(header + load + relro)


def make_zip(path: Path, entries: dict[str, bytes]) -> Path:
    with zipfile.ZipFile(path, "w", zipfile.ZIP_STORED) as archive:
        for name, payload in entries.items():
            archive.writestr(name, payload)
    return path


def make_elf_with_relro(align: int, relro_memsz: int, rw_vaddr: int) -> bytes:
    header = bytearray(64)
    header[0:4] = b"\x7fELF"
    header[4] = 2
    header[5] = 1
    struct.pack_into("<Q", header, 0x20, 64)
    struct.pack_into("<H", header, 0x36, 56)
    struct.pack_into("<H", header, 0x38, 2)
    load = bytearray(56)
    struct.pack_into("<I", load, 0, 1)
    struct.pack_into("<I", load, 4, 6)  # R|W
    struct.pack_into("<Q", load, 0x10, rw_vaddr)
    struct.pack_into("<Q", load, 0x28, 0x100)
    struct.pack_into("<Q", load, 0x30, align)
    relro = bytearray(56)
    struct.pack_into("<I", relro, 0, 0x6474E552)
    struct.pack_into("<I", relro, 4, 4)
    struct.pack_into("<Q", relro, 0x10, 0)
    struct.pack_into("<Q", relro, 0x28, relro_memsz)
    struct.pack_into("<Q", relro, 0x30, align)
    return bytes(header + load + relro)


def test_elf_relro_sharing_page_fails() -> None:
    errors = J.elf_align_errors(make_elf_with_relro(0x4000, 0x1000, 0x2000))
    assert any("RELRO" in error for error in errors)


def test_parse_gradle_versions() -> None:
    text = 'versionCode = 7\nversionName = "1.2.3"\nndk { abiFilters += setOf("arm64-v8a", "x86_64") }\n'
    info = J.parse_gradle_versions(text)
    assert info == {"version_code": 7, "version_name": "1.2.3",
                    "abi_filters": ["arm64-v8a", "x86_64"]}


def test_elf_alignment_below_16k_fails() -> None:
    errors = J.elf_align_errors(make_elf(0x1000))
    assert any("p_align=0x1000" in error for error in errors)


def test_elf_alignment_16k_passes() -> None:
    assert J.elf_align_errors(make_elf(0x4000)) == []


def test_selftest_patches_and_goes_red(tmp_path: Path) -> None:
    apk = make_zip(tmp_path / "app.apk", {"lib/arm64-v8a/libx.so": make_elf(0x4000)})
    result = J.selftest_apk_16k(apk)
    assert result["ok"] is True
    assert result["patched_load_segments"] == 1
    assert result["errors"]


def test_find_alignment_reads_nested_protobuf() -> None:
    inner = bytes([0x08, 0x01, 0x10, 0x02])  # enabled=1, alignment=2
    outer = bytes([0x12, len(inner)]) + inner
    assert J.find_alignment(outer) == 2
    assert J.find_alignment(b"\x08\x00") is None


def test_aab_rejects_32bit_and_missing_quickjs(tmp_path: Path) -> None:
    inner = bytes([0x08, 0x01, 0x10, 0x02])
    outer = bytes([0x12, len(inner)]) + inner
    stale = make_zip(tmp_path / "stale.aab", {
        "BundleConfig.pb": outer,
        "base/lib/arm64-v8a/libfoo.so": b"x",
        "base/lib/armeabi-v7a/libfoo.so": b"x",
    })
    result = J.check_aab(stale, ["arm64-v8a", "x86_64"], ["libquickjs.so"])
    assert any("ABI set" in error for error in result["errors"])
    assert any("libquickjs.so" in error for error in result["errors"])
    fresh = make_zip(tmp_path / "fresh.aab", {
        "BundleConfig.pb": outer,
        "base/lib/arm64-v8a/libquickjs.so": b"x",
        "base/lib/x86_64/libquickjs.so": b"x",
    })
    assert J.check_aab(fresh, ["arm64-v8a", "x86_64"], ["libquickjs.so"])["errors"] == []


def test_mapping_ignores_removed_tombstones() -> None:
    text = (
        "com.tingyun.smartmistakebook.Foo -> a60:\n"
        "com.tingyun.smartmistakebook.Bar -> R8$$REMOVED$$CLASS$$1:\n"
    )
    stats = J.parse_mapping(text)
    assert stats["classes"] == 1
    assert stats["removed"] == 1
    assert stats["renamed"] == 1
    assert stats["renamed_classes"] == [("com.tingyun.smartmistakebook.Foo", "a60")]


def test_r8_binding_requires_dex_hit_and_zero_negative(tmp_path: Path) -> None:
    mapping = tmp_path / "mapping.txt"
    mapping.write_text("com.tingyun.smartmistakebook.Foo -> a60:\n", encoding="utf-8")
    apk = make_zip(tmp_path / "app.apk", {"classes.dex": b"xxLa60;xx"})
    result = J.check_r8_binding(mapping, apk, sample=1)
    assert result["samples"][0]["hits"] == 1
    assert result["negative_control_hits"] == 0
    assert result["errors"] == []


def test_apk_revision_reads_textproto(tmp_path: Path) -> None:
    apk = make_zip(tmp_path / "app.apk", {
        "META-INF/version-control-info.textproto": b'revision: "abc1234"\n'})
    assert J.apk_revision(apk) == "abc1234"
    assert J.apk_revision(make_zip(tmp_path / "none.apk", {"x": b"y"})) is None


def test_version_command_flags_mismatch(tmp_path: Path) -> None:
    gradle = tmp_path / "build.gradle.kts"
    gradle.write_text('versionCode = 2\nversionName = "0.2.0"\n', encoding="utf-8")
    metadata = tmp_path / "output-metadata.json"
    metadata.write_text('{"elements":[{"versionCode":3,"versionName":"0.2.0"}]}',
                        encoding="utf-8")
    code = J.main(["version", "--gradle", str(gradle), "--metadata", str(metadata)])
    assert code == 1


def _injected_version_fixture(tmp_path: Path) -> tuple[Path, Path]:
    """versionCode 由 -Psmbk.versionCode 注入：gradle 里没有字面数字。"""
    gradle = tmp_path / "build.gradle.kts"
    gradle.write_text(
        'val smbkVersionCode: Int = smbkVersionCodeRaw?.toIntOrNull() ?: 2\n'
        'versionCode = smbkVersionCode\nversionName = "0.2.0"\n',
        encoding="utf-8")
    metadata = tmp_path / "output-metadata.json"
    metadata.write_text('{"elements":[{"versionCode":101160,"versionName":"0.2.0"}]}',
                        encoding="utf-8")
    return gradle, metadata


def test_version_injected_requires_expected_code(tmp_path: Path) -> None:
    """注入形态下不给 --expected-code 必须 fail-closed（不得靠猜）。"""
    gradle, metadata = _injected_version_fixture(tmp_path)
    assert J.main(["version", "--gradle", str(gradle), "--metadata", str(metadata)]) == 1


def test_version_injected_accepts_expected_code(tmp_path: Path) -> None:
    gradle, metadata = _injected_version_fixture(tmp_path)
    assert J.main(["version", "--gradle", str(gradle), "--metadata", str(metadata),
                   "--expected-code", "101160"]) == 0


def test_version_expected_code_mismatch_fails(tmp_path: Path) -> None:
    gradle, metadata = _injected_version_fixture(tmp_path)
    assert J.main(["version", "--gradle", str(gradle), "--metadata", str(metadata),
                   "--expected-code", "101161"]) == 1


def _retrace(mapping: Path, stack: Path) -> str:
    completed = subprocess.run([str(RETRACE), "--verbose", str(mapping), str(stack)],
                               capture_output=True, text=True)
    assert completed.returncode == 0, completed.stderr
    return completed.stdout + completed.stderr


def test_retrace_requires_at_prefix_and_restores_class(tmp_path: Path) -> None:
    if not RETRACE.exists():
        pytest.fail(f"retrace missing: {RETRACE}")
    mapping = tmp_path / "mapping.txt"
    mapping.write_text("com.example.Foo -> a:\n    1:2:void bar():1:1 -> b\n",
                       encoding="utf-8")
    good = tmp_path / "good.txt"
    good.write_text("at a.b(SourceFile:1)\n", encoding="utf-8")
    assert "com.example.Foo" in _retrace(mapping, good)
    bad = tmp_path / "bad.txt"
    bad.write_text("at com.example.Foo.b(SourceFile:1)\n", encoding="utf-8")
    assert "bar" not in _retrace(mapping, bad)


def _git_repo(tmp_path: Path) -> Path:
    repo = tmp_path / "repo"
    repo.mkdir()
    subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
    subprocess.run(["git", "config", "user.email", "j@example.invalid"], cwd=repo, check=True)
    subprocess.run(["git", "config", "user.name", "j"], cwd=repo, check=True)
    (repo / "tracked.txt").write_text("one\n", encoding="utf-8")
    subprocess.run(["git", "add", "tracked.txt"], cwd=repo, check=True)
    subprocess.run(["git", "commit", "-q", "-m", "init"], cwd=repo, check=True)
    return repo


def test_identity_requires_clean_tree(tmp_path: Path) -> None:
    repo = _git_repo(tmp_path)
    make_zip(repo / "app.apk", {
        "META-INF/version-control-info.textproto": b'revision: "deadbee"\n'})
    make_zip(repo / "app.aab", {"BundleConfig.pb": b""})
    (repo / "mapping.txt").write_text("", encoding="utf-8")
    (repo / "build.gradle.kts").write_text('versionCode = 2\nversionName = "0.2.0"\n',
                                           encoding="utf-8")
    (repo / "output-metadata.json").write_text(
        '{"elements":[{"versionCode":2,"versionName":"0.2.0"}]}', encoding="utf-8")
    subprocess.run(["git", "add", "-A"], cwd=repo, check=True)
    subprocess.run(["git", "commit", "-q", "-m", "artifacts"], cwd=repo, check=True)
    common = ["identity", "--root", str(repo), "--apk", str(repo / "app.apk"),
              "--aab", str(repo / "app.aab"), "--mapping", str(repo / "mapping.txt"),
              "--gradle", str(repo / "build.gradle.kts"),
              "--metadata", str(repo / "output-metadata.json")]
    assert J.main([*common, "--require-clean"]) == 0
    (repo / "tracked.txt").write_text("dirty\n", encoding="utf-8")
    assert J.main([*common, "--require-clean"]) == 1


def _identity_args(repo: Path, apk: Path) -> list[str]:
    make_zip(repo / "app.aab", {"BundleConfig.pb": b""})
    (repo / "mapping.txt").write_text("", encoding="utf-8")
    (repo / "build.gradle.kts").write_text('versionCode = 2\nversionName = "0.2.0"\n',
                                           encoding="utf-8")
    (repo / "output-metadata.json").write_text(
        '{"elements":[{"versionCode":2,"versionName":"0.2.0"}]}', encoding="utf-8")
    return ["identity", "--root", str(repo), "--apk", str(apk),
            "--aab", str(repo / "app.aab"), "--mapping", str(repo / "mapping.txt"),
            "--gradle", str(repo / "build.gradle.kts"),
            "--metadata", str(repo / "output-metadata.json")]


def test_identity_fails_when_zip_integrity_reports_bad_member(
        tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    """identity 必须把 zip_integrity 的坏成员当作失败，而不能只写进 payload。"""
    repo = _git_repo(tmp_path)
    apk = make_zip(repo / "app.apk", {"classes.dex": b"dex"})
    args = _identity_args(repo, apk)
    monkeypatch.setattr(J, "zip_integrity", lambda path: "classes.dex")
    assert J.main(args) == 1


def test_identity_survives_corrupt_apk_and_fails(tmp_path: Path) -> None:
    """APK 不是有效 zip 时 identity 必须返回 1，而不是抛异常或假绿。"""
    repo = _git_repo(tmp_path)
    apk = repo / "app.apk"
    apk.write_bytes(b"not a zip file")
    args = _identity_args(repo, apk)
    assert J.main(args) == 1


def test_version_command_flags_out_of_range(tmp_path: Path) -> None:
    gradle = tmp_path / "build.gradle.kts"
    gradle.write_text('versionCode = 0\nversionName = "0.2.0"\n', encoding="utf-8")
    metadata = tmp_path / "output-metadata.json"
    metadata.write_text('{"elements":[{"versionCode":0,"versionName":"0.2.0"}]}',
                        encoding="utf-8")
    assert J.main(["version", "--gradle", str(gradle), "--metadata", str(metadata)]) == 1


def test_r8keep_flags_no_arg_constructor_only() -> None:
    usage = (
        "com.example.Kept:\n"
        "    public void <init>()\n"
        "com.example.WithArgs:\n"
        "    public void <init>(java.lang.String)\n"
    )
    violations = J.check_kept_no_arg_constructors(
        usage, ["com.example.Kept", "com.example.WithArgs"])
    assert violations == ["com.example.Kept: public void <init>()"]


def test_apk16k_accepts_aligned_stored_native_library(tmp_path: Path) -> None:
    apk = tmp_path / "aligned.apk"
    with zipfile.ZipFile(apk, "w", zipfile.ZIP_STORED) as archive:
        archive.writestr("pad", b"x" * 16300)
        archive.writestr("lib/arm64-v8a/libx.so", make_elf(0x4000))
    payload = J.check_apk_16k(apk)
    assert payload["errors"] == []
    assert payload["files"][0]["offset_mod_16384"] == 0


def test_zip_integrity_accepts_valid_and_rejects_truncated(tmp_path: Path) -> None:
    apk = make_zip(tmp_path / "ok.apk", {"classes.dex": b"dex"})
    assert J.zip_integrity(apk) is None
    broken = tmp_path / "broken.apk"
    broken.write_bytes(apk.read_bytes()[:-10])
    with pytest.raises(zipfile.BadZipFile):
        J.zip_integrity(broken)


# ---------------------------------------------------------------------------
# 批次 47 / J 域 FAIL 根因：signing 判据必须 fail-closed。
# 现状（批次 46 证据 j-signing.log）是 4/4 环境变量缺失、APK/AAB 签名身份不同、
# AAB 证书已过期，命令仍 exit 0，导致 J-020/021/024/025/027 只能靠人工矛盾检查。
# ---------------------------------------------------------------------------

ZH_KEYTOOL_CERT = """签名者 #1:

证书 #1：
所有者: CN=ZCode Release Smoke, OU=local, O=local, L=local, ST=local, C=CN
发布者: CN=ZCode Release Smoke, OU=local, O=local, L=local, ST=local, C=CN
序列号: cb12fbb3fff94b68
生效时间: Wed Sep 30 00:12:34 CST 2026, 失效时间: Fri Oct 30 00:12:34 CST 2026
证书指纹:
\t SHA1: 9B:C3:17:5F:0C:35:BD:69:D3:84:32:5C:07:86:AC:7E:FC:F9:2A:27
\t SHA256: A9:E7:6C:AD:95:AF:11:D0:EA:CE:DA:67:3B:00:2E:A6:18:70:2D:43:94:BF:23:49:D4:57:9C:86:F3:3B:27:01
签名算法名称: SHA384withRSA
"""


def test_parse_keytool_cert_zh_locale() -> None:
    """中文 keytool 输出（所有者/生效时间/失效时间）必须能读出 owner 与有效期。"""
    facts = J.parse_keytool_cert(ZH_KEYTOOL_CERT)
    assert facts["sha256"] == (
        "a9e76cad95af11d0eaceda673b002ea618702d4394bf2349d4579c86f33b2701")
    assert facts["owner"].startswith("CN=ZCode Release Smoke")
    assert facts["not_before"] == "2026-09-30T00:12:34"
    assert facts["not_after"] == "2026-10-30T00:12:34"


def test_signing_errors_fail_closed_on_missing_env() -> None:
    state = {
        "env_present": [],
        "env_missing": ["RELEASE_STORE_FILE"],
        "apk_signer": {"sha256": "aa"},
        "aab_signer": {"sha256": "aa", "not_after": "2027-01-01T00:00:00"},
    }
    errors = J.signing_errors(state, now=datetime(2026, 11, 5))
    assert any("env" in error for error in errors)


def test_signing_errors_flags_identity_mismatch_and_expiry() -> None:
    state = {
        "env_present": ["RELEASE_KEY_ALIAS"],
        "env_missing": [],
        "apk_signer": {"sha256": "aa"},
        "aab_signer": {"sha256": "bb", "not_after": "2026-10-30T00:12:34"},
    }
    errors = J.signing_errors(state, now=datetime(2026, 11, 5))
    assert any("identity" in error for error in errors)
    assert any("expired" in error for error in errors)


def test_signing_errors_requires_artifacts() -> None:
    state = {"env_present": ["x"], "env_missing": [], "apk_signer": {}, "aab_signer": {}}
    errors = J.signing_errors(state, now=datetime(2026, 11, 5))
    assert any("artifact" in error for error in errors)


def test_signing_errors_accepts_complete_attestation() -> None:
    state = {
        "env_present": [
            "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD",
            "RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD",
        ],
        "env_missing": [],
        "apk_signer": {"sha256": "ab" * 32},
        "aab_signer": {"sha256": "ab" * 32, "not_after": "2027-12-31T00:00:00"},
    }
    assert J.signing_errors(state, now=datetime(2026, 11, 5)) == []


def test_signing_command_fails_closed_without_release_env(
        monkeypatch: pytest.MonkeyPatch) -> None:
    for name in ("RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD",
                 "RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD"):
        monkeypatch.delenv(name, raising=False)
    assert J.main(["signing", "--root", str(ROOT)]) == 1


def test_decode_prefers_utf8_and_falls_back_to_gbk() -> None:
    """keytool 在 zh_CN Windows 上用 GBK 输出；写死 utf-8+replace 会把 owner 读成 null。"""
    text = "所有者: CN=Release"
    assert J._decode(text.encode("utf-8")) == text
    assert J._decode(text.encode("gbk")) == text
