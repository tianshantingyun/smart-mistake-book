#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G 域设备证据的逐项绑定校验（批次 44）。

## 它消灭的具体失败

批次 43 出现过“设备全绿”但 APK 无最终树绑定、以及把 `OK (15 tests)` 与 15 个
`-4` 并排读成通过。设备 JSON 一旦失去 `serial`/`apk_sha256` 绑定，就不再是证据，
只是一个数字。本文件对每份 G 设备证据做**结构 + 取值**双校验：

1. 必须同时带 `serial` 与 `apk_sha256`（64 hex）；
2. 每条断言的期望值写死在测试里（不是从 JSON 里读出来再自己比自己）；
3. 证据里的 `package` 必须是本次安装的 flavor 包名。

这些测试不重新跑 adb，只校验落盘证据自洽；采集命令写在 `probe` 字段里供复跑。
"""

from __future__ import annotations

import json
import re
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "docs/acceptance/evidence/G/device"
APK_SHA = "84cae864c42f7e26203d21f3901c06a4d0f02f2dd3b012bb63727ef32e5dab5b"
BATCH47_APK_SHA = "f534b0dd8aeabeeeec633bcbb9d24a62efe900eb75fdbcce199b979d58f7f7de"
BATCH47_ARTIFACTS = {
    "device-deeplink-G050-recheck-b47.json",
    "device-deeplink-G051-recheck-b47.json",
}
PACKAGE = "com.tingyun.smartmistakebook.localfirst"


def load(name: str) -> dict:
    return json.loads((EVIDENCE / name).read_text(encoding="utf-8"))


@pytest.fixture(params=sorted(path.name for path in EVIDENCE.glob("*.json")))
def artifact(request):
    return request.param, load(request.param)


def test_every_artifact_is_bound_to_serial_and_apk(artifact) -> None:
    name, data = artifact
    assert data["serial"] == "emulator-5584", name
    expected_sha = BATCH47_APK_SHA if name in BATCH47_ARTIFACTS else APK_SHA
    assert data["apk_sha256"] == expected_sha, name
    if name in BATCH47_ARTIFACTS:
        assert data["archived_apk_sha256"] == APK_SHA, name
        assert data["apk_sha256"] != data["archived_apk_sha256"], name
        assert data["captured_at"].startswith("2026-11-06"), name
    assert re.fullmatch(r"[0-9a-f]{64}", data["apk_sha256"]), name
    assert data["package"] == PACKAGE, name


class TestGDeviceManifestBinding:
    def test_permission_surface_matches_reviewed_set(self) -> None:
        data = load("device-permissions.json")
        assert data["permission_count"] == 7
        expected = {
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "android.permission.WAKE_LOCK",
        }
        assert set(data["requested_permissions"]) == expected

    def test_dangerous_permission_cannot_be_granted(self) -> None:
        data = load("device-permissions.json")
        assert "has not requested permission" in data["dangerous_permission_grant_attempt"]

    def test_merged_manifest_turns_off_backup_and_cleartext(self) -> None:
        data = load("device-manifest.json")
        assert data["allow_backup"] == "false"
        assert data["uses_cleartext_traffic"] == "false"
        assert data["network_security_config_present"] is True
        assert data["data_extraction_rules_present"] is True
        assert data["isolated_process_service"] is True

    def test_debug_artifact_exports_five_app_components(self) -> None:
        data = load("device-manifest.json")
        names = sorted(component["name"].split(".")[-1] for component in data["app_exported_components"])
        assert names == ["BindingAuditActivity", "KnowledgeEvidenceAuditActivity",
                         "MainActivity", "TestCaptureModelFailuresReceiver",
                         "TestSeedModelConfigReceiver"]

    def test_backup_is_refused_by_the_platform(self) -> None:
        data = load("device-backup.json")
        assert "Backup is not allowed" in data["bmgr_backupnow"]
        assert data["allow_backup_flag_present"] is False


class TestGDeviceProbes:
    def test_deep_link_does_not_resolve_to_this_app(self) -> None:
        data = load("device-deeplink.json")
        assert data["handles_view_action"] is False
        assert "chrome" in data["resolved_package"]
        assert "unable to resolve" in data["custom_scheme_start"]

    def test_file_providers_deny_unauthorized_reads(self) -> None:
        data = load("device-fileexposure.json")
        assert data["pdf_provider_denied"] is True
        assert data["batch_provider_denied"] is True
        assert "not exported" in data["pdf_provider_response"]

    def test_screencap_is_available(self) -> None:
        data = load("device-fileexposure.json")
        assert data["screencap_available"] is True
        assert data["screencap_dimensions"] == "1080x2400"
        assert data["screencap_png_bytes"] > 10_000

    def test_logcat_carries_no_secret_pattern(self) -> None:
        data = load("device-logcat.json")
        assert data["logcat_lines"] > 1_000
        assert data["secret_pattern_hits"] == 0
        assert data["first_hit"] is None

    def test_no_plaintext_secret_directory_at_runtime(self) -> None:
        data = load("device-secrets.json")
        assert data["model_secrets_dir_present"] is False
        assert not any("model-secrets" in name for name in data["files"])

    def test_debug_only_activity_is_reachable_in_debug_artifact(self) -> None:
        data = load("device-intent.json")
        assert data["debug_activity_reachable_from_shell"] is True
