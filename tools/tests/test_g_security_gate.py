#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G 域（安全与隐私）逐项断言（批次 44）。

## 它消灭的具体失败

`tools/ci/g_security_gate.py` 给出的是一个总退出码；一条断言写错或整段被删，
退出码仍可能是 0，台账却按“全绿”记账。本文件把同一批不变量拆成**逐例**断言，
每例只读源码/清单，失败时 pytest 的 XML 会点名是哪一条，无法被总退出码掩盖。

独立的第二实现：这里不复用 gate 的辅助函数，直接读文件并逐条比对期望字符串，
两条路径同时错才可能同时绿。

约定：这里的断言只覆盖**源码/清单可判定**的事实；设备侧在
`docs/acceptance/evidence/G/device/*.json`，运行时外发/真实 Provider 不在本文件。
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8", errors="replace")


MANIFEST = "app/src/main/AndroidManifest.xml"


class TestGSecrets:
    def test_vault_uses_aes_gcm(self) -> None:
        text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/AndroidKeystoreModelSecretVault.kt")
        assert "KEY_ALGORITHM_AES" in text
        assert "BLOCK_MODE_GCM" in text
        assert "GCMParameterSpec" in text

    def test_vault_alias_is_single_constant(self) -> None:
        text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/AndroidKeystoreModelSecretVault.kt")
        assert 'internal const val MODEL_SECRET_KEY_ALIAS = "smart_mistake_book_model_api_key_v2"' in text

    def test_vault_lives_in_no_backup_directory(self) -> None:
        text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/AndroidKeystoreModelSecretVault.kt")
        assert "noBackupFilesDir" in text

    def test_api_key_is_not_persisted_in_settings_datastore(self) -> None:
        store = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/DataStoreModelConfigurationStore.kt")
        assert "vault.write" in store
        assert not re.search(r"stringPreferencesKey\([^)]*key", store, re.I)

    def test_api_key_validation_rejects_bad_material(self) -> None:
        text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/ModelConfigurationValidator.kt")
        for issue in ("API_KEY_REQUIRED", "API_KEY_TOO_LONG",
                      "API_KEY_CONTAINS_WHITESPACE_OR_CONTROL_CHARACTER"):
            assert issue in text

    def test_no_tracked_signing_material(self) -> None:
        import subprocess
        out = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True).stdout
        offenders = [name for name in out.splitlines()
                     if re.search(r"\.(jks|keystore|p12|pfx|pem)$", name, re.I)]
        assert offenders == []


class TestGLogging:
    def test_telemetry_consent_defaults_off(self) -> None:
        text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryConsentStore.kt")
        assert "UNKNOWN" in text
        assert "isEnabled" in text

    def test_telemetry_package_has_no_network_transport(self) -> None:
        base = ROOT / "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry"
        for path in base.glob("*.kt"):
            text = path.read_text(encoding="utf-8", errors="replace")
            for token in ("java.net.", "okhttp", "HttpURLConnection"):
                assert token not in text, f"{path.name} imports {token}"

    def test_log_calls_never_interpolate_credentials(self) -> None:
        pattern = re.compile(r"Log\.[dewiv]\((.*?)\)", re.S)
        secret = re.compile(r"(\$\{?(?:apiKey|api_key|secretKey|credential|password)|\bBearer\s*\$\{)")
        offenders = []
        for path in (ROOT / "app/src/main").rglob("*.kt"):
            for match in pattern.finditer(path.read_text(encoding="utf-8", errors="replace")):
                if secret.search(match.group(1)):
                    offenders.append(path.name)
        assert offenders == []

    def test_diagnostics_bundle_has_no_network_or_db_copy(self) -> None:
        base = ROOT / "app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics"
        for path in base.glob("*.kt"):
            text = path.read_text(encoding="utf-8", errors="replace")
            for token in ("java.net.", "okhttp", "HttpURLConnection"):
                assert token not in text


class TestGBackup:
    def test_auto_backup_disabled(self) -> None:
        assert 'android:allowBackup="false"' in read(MANIFEST)

    def test_full_backup_excludes_every_domain(self) -> None:
        text = read("app/src/main/res/xml/backup_rules.xml")
        for domain in ("root", "file", "database", "sharedpref", "external"):
            assert f'<exclude domain="{domain}" path="." />' in text

    def test_data_extraction_rules_cover_cloud_and_device_transfer(self) -> None:
        text = read("app/src/main/res/xml/data_extraction_rules.xml")
        assert '<cloud-backup disableIfNoEncryptionCapabilities="true">' in text
        assert "<device-transfer>" in text
        assert text.count('<exclude domain="database" path="." />') == 2

    def test_archive_rejects_traversal_entries(self) -> None:
        text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt")
        assert 'name.contains("..")' in text
        assert 'name.startsWith("/")' in text

    def test_backup_settings_document_has_no_credential_field(self) -> None:
        text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSettingsDocument.kt")
        assert not re.search(r"(?i)apikey|api_key|keystore", text)


class TestGEgress:
    def test_cleartext_disabled_in_manifest_and_config(self) -> None:
        assert 'android:usesCleartextTraffic="false"' in read(MANIFEST)
        assert 'cleartextTrafficPermitted="false"' in read(
            "app/src/localFirst/res/xml/network_security_config.xml")

    def test_network_security_config_trusts_system_only(self) -> None:
        text = read("app/src/localFirst/res/xml/network_security_config.xml")
        assert '<certificates src="system" />' in text
        assert "user" not in text

    def test_model_endpoint_requires_https_and_no_embedded_credentials(self) -> None:
        text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTransport.kt")
        assert 'base.scheme != "https"' in text
        assert "base.username.isNotEmpty()" in text

    def test_egress_requires_authorization_manifest(self) -> None:
        text = read("core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelEgress.kt")
        assert "EGRESS_AUTHORIZATION_REQUIRED" in text
        assert "request.egressManifest ?: throw ModelEgressAuthorizationException" in text

    def test_strict_offline_flavor_removes_internet_permission(self) -> None:
        text = read("app/src/strictOffline/AndroidManifest.xml")
        assert "android.permission.INTERNET" in text
        assert 'tools:node="remove"' in text

    def test_telemetry_source_has_no_egress_path(self) -> None:
        base = ROOT / "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry"
        files = sorted(base.glob("*.kt"))
        assert files
        for path in files:
            text = path.read_text(encoding="utf-8", errors="replace")
            for token in ("java.net.", "okhttp", "HttpURLConnection", "URLConnection", "Socket("):
                assert token not in text, f"{path.name} carries {token}"


class TestGPermissions:
    def test_no_dangerous_permission_is_declared(self) -> None:
        main = read(MANIFEST)
        flavor = read("app/src/localFirst/AndroidManifest.xml")
        banned = ("CAMERA", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
                  "READ_MEDIA_IMAGES", "ACCESS_FINE_LOCATION", "READ_CONTACTS",
                  "RECORD_AUDIO", "MANAGE_EXTERNAL_STORAGE", "QUERY_ALL_PACKAGES")
        for name in re.findall(r'android:name="android\.permission\.([A-Z_]+)"', main + flavor):
            assert name not in banned, name

    def test_camera_declared_optional_without_permission(self) -> None:
        text = read(MANIFEST)
        assert 'android.hardware.camera' in text
        assert 'android:required="false"' in text
        assert "android.permission.CAMERA" not in text

    def test_post_notifications_is_requested_at_runtime(self) -> None:
        text = read("app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderScreen.kt")
        assert "permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)" in text

    def test_no_foreground_service_type_declared(self) -> None:
        manifests = [path for base in ("app", "core", "feature")
                     for path in (ROOT / base).rglob("AndroidManifest.xml")
                     if "/build/" not in path.as_posix()]
        assert len(manifests) >= 8
        for path in manifests:
            text = path.read_text(encoding="utf-8", errors="replace")
            assert "android:foregroundServiceType" not in text, path.as_posix()

    def test_notifications_respect_denial(self) -> None:
        export = read("app/src/main/kotlin/com/tingyun/smartmistakebook/MistakeExportNotifications.kt")
        platform = read("app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderPlatform.kt")
        assert "if (!canPost()) return" in export
        assert "ContextCompat.checkSelfPermission" in platform


class TestGDeepLink:
    def test_no_view_or_browsable_filter(self) -> None:
        for name in (MANIFEST, "app/src/debug/AndroidManifest.xml",
                     "app/src/localFirst/AndroidManifest.xml",
                     "app/src/strictOffline/AndroidManifest.xml"):
            text = read(name)
            assert "android.intent.action.VIEW" not in text
            assert "android.intent.category.BROWSABLE" not in text

    def test_no_custom_scheme(self) -> None:
        for name in (MANIFEST, "app/src/debug/AndroidManifest.xml"):
            assert "android:scheme" not in read(name)

    def test_no_auto_verify(self) -> None:
        for name in (MANIFEST, "app/src/debug/AndroidManifest.xml"):
            assert "autoVerify" not in read(name)

    def test_no_action_view_consumer_in_product_sources(self) -> None:
        offenders = [path.name for path in (ROOT / "app/src/main").rglob("*.kt")
                     if "Intent.ACTION_VIEW" in path.read_text(encoding="utf-8", errors="replace")]
        assert offenders == []

    def test_share_intake_enforces_scheme_type_count_and_size(self) -> None:
        text = read("feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureShareIntake.kt")
        for token in ("content://", "mimeType", "MAX_", "size"):
            assert token in text, token

    def test_no_asset_link_declaration(self) -> None:
        import subprocess
        out = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True).stdout
        assert not [n for n in out.splitlines() if n.endswith("assetlinks.json") or ".well-known" in n]


class TestGIntent:
    def test_only_main_activity_is_exported_in_main_manifest(self) -> None:
        text = read(MANIFEST)
        assert text.count('android:exported="true"') == 1
        assert "MainActivity" in text

    def test_file_providers_are_private(self) -> None:
        combined = "".join(read(name) for name in (
            "core/export/src/main/AndroidManifest.xml",
            "core/data/src/main/AndroidManifest.xml",
            "feature/capture/src/main/AndroidManifest.xml",
        ))
        assert combined.count('android:exported="false"') == 4
        assert combined.count('android:grantUriPermissions="true"') == 4

    def test_visual_js_service_is_isolated(self) -> None:
        text = read(MANIFEST)
        assert 'android:isolatedProcess="true"' in text
        assert 'android:process=":visual_js"' in text
        assert 'android:exported="false"' in text

    def test_pending_intents_are_immutable(self) -> None:
        hits = []
        for path in (ROOT / "app/src/main").rglob("*.kt"):
            hits.append(path.read_text(encoding="utf-8", errors="replace"))
        text = "\n".join(hits)
        assert text.count("PendingIntent.get") == text.count("PendingIntent.FLAG_IMMUTABLE")
        assert "PendingIntent.FLAG_MUTABLE" not in text

    def test_debug_only_components_declared_only_in_debug_source_set(self) -> None:
        debug = read("app/src/debug/AndroidManifest.xml")
        main = read(MANIFEST)
        for component in ("BindingAuditActivity", "KnowledgeEvidenceAuditActivity",
                          "TestSeedModelConfigReceiver", "TestCaptureModelFailuresReceiver"):
            assert component in debug
            assert component not in main

    def test_start_activity_uses_explicit_or_chooser(self) -> None:
        for path in (ROOT / "app/src/main").rglob("*.kt"):
            text = path.read_text(encoding="utf-8", errors="replace")
            for match in re.finditer(r"\.startActivity\(", text):
                window = text[max(0, match.start() - 600): match.start() + 200]
                assert "createChooser" in window or "Intent(" in window, path.name

    def test_notifications_are_private_on_lockscreen(self) -> None:
        for name in ("app/src/main/kotlin/com/tingyun/smartmistakebook/MistakeExportNotifications.kt",
                     "app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderPlatform.kt"):
            assert "VISIBILITY_PRIVATE" in read(name)


class TestGFileExposure:
    def test_file_provider_paths_are_subdirectories(self) -> None:
        for name in ("core/export/src/main/res/xml/mistake_pdf_export_paths.xml",
                     "core/export/src/main/res/xml/mistake_docx_export_paths.xml",
                     "core/data/src/main/res/xml/batch_import_file_paths.xml",
                     "feature/capture/src/main/res/xml/capture_file_paths.xml"):
            text = read(name)
            assert 'path="."' not in text
            assert 'path="/"' not in text

    def test_share_intents_grant_one_shot_read(self) -> None:
        pdf = read("core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt")
        assert "FLAG_GRANT_READ_URI_PERMISSION" in pdf
        assert "prepared.verifyIntegrity()" in pdf

    def test_share_uri_is_confined_to_export_directory(self) -> None:
        pdf = read("core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt")
        assert "canonicalFile" in pdf
        assert "contentDirectory?.parentFile == allowedDirectory" in pdf

    def test_no_file_uri_handed_to_other_apps(self) -> None:
        for path in (ROOT / "core").rglob("*.kt"):
            if "/src/test/" in path.as_posix() or "/src/androidTest/" in path.as_posix():
                continue
            for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
                if "Uri.fromFile(" in line:
                    assert "EXTRA_STREAM" not in line and "ClipData" not in line

    def test_batch_import_staging_is_private(self) -> None:
        text = read("core/data/src/main/res/xml/batch_import_file_paths.xml")
        assert "<files-path" in text
        assert "<external-path" not in text


class TestGClipboardScreenshot:
    def test_single_clipboard_write_site(self) -> None:
        sites = [path.as_posix() for path in (ROOT / "core").rglob("*.kt")
                 if "setClipEntry" in path.read_text(encoding="utf-8", errors="replace")
                 or "setPrimaryClip" in path.read_text(encoding="utf-8", errors="replace")]
        assert [Path(site).name for site in sites] == ["ClaudeCopyButton.kt"]

    def test_clipboard_carries_no_credential(self) -> None:
        text = read("core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/ClaudeCopyButton.kt")
        assert not re.search(r"(?i)(apikey|secret|bearer|token)", text)
        assert "ClipData.newPlainText(label, text)" in text

    def test_flag_secure_only_on_configuration_screen(self) -> None:
        sites = [path.as_posix() for path in (ROOT / "app/src/main").rglob("*.kt")
                 if "FLAG_SECURE" in path.read_text(encoding="utf-8", errors="replace")]
        assert [Path(site).name for site in sites] == ["SecondaryScreens.kt"]

    def test_flag_secure_is_cleared_on_dispose(self) -> None:
        text = read("app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt")
        assert "window?.addFlags" in text
        assert "onDispose { window?.clearFlags" in text


class TestGPlaintextDeps:
    def test_signing_reads_environment_only(self) -> None:
        text = read("app/build.gradle.kts")
        assert "System.getenv" in text
        assert "RELEASE_" in text
        assert 'storePassword = "' not in text

    def test_dependency_versions_are_recorded(self) -> None:
        text = read("gradle/libs.versions.toml")
        for coordinate, version in (("quickjs-kt-android", "1.0.15"), ("okhttp", "5.4.0"),
                                    ("room3", "3.0.0"), ("mlkit-text-recognition", "16.0.1")):
            assert re.search(rf'(?m)^{re.escape(coordinate)}\s*=\s*"{re.escape(version)}"', text), coordinate

    def test_repositories_are_limited_and_centralized(self) -> None:
        text = read("settings.gradle.kts")
        assert "FAIL_ON_PROJECT_REPOS" in text
        assert "maven(" not in text

    def test_quickjs_pin_and_kotlin_constraint_declared(self) -> None:
        catalog = read("gradle/libs.versions.toml")
        assert re.search(r'(?m)^quickjs-kt-android\s*=\s*"1\.0\.15"', catalog)
        root_build = read("build.gradle.kts")
        assert "kotlin-stdlib" in root_build
        assert "2.3.21" in catalog

    def test_no_untracked_secret_bearing_scripts(self) -> None:
        import subprocess
        out = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT,
                             capture_output=True, text=True, check=True).stdout
        untracked = [line[3:] for line in out.splitlines() if line.startswith("?? ")]
        pattern = re.compile(r"(sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY-----)")
        for name in untracked:
            path = ROOT / name
            if not path.is_file() or path.stat().st_size > 1_000_000:
                continue
            for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
                if pattern.search(line) and "model-task-" not in line:
                    raise AssertionError(f"{name}: {line[:80]}")


class TestGDependencyVerification:
    """G-089：Gradle 依赖校验元数据（批次 47 修复）。

    它消灭的具体失败：批次 46 的 G-089 只有 file: 证据——
    `gradle/verification-metadata.xml` 缺失即 FAIL，而 gate 里唯一的存在性
    断言（chk_g088）是死代码、未注册进 CHECKS，`--topic plaintext_deps` 在
    文件缺失时仍 exit 0：缺失被总退出码掩盖，台账行没有任何可复跑证据。
    """

    NS = "{https://schema.gradle.org/dependency-verification}"

    def _gate(self):
        import sys
        sys.path.insert(0, str(ROOT / "tools" / "ci"))
        import g_security_gate as gate
        return gate

    def test_gate_registers_g089_in_plaintext_deps_topic(self) -> None:
        gate = self._gate()
        assert 89 in gate.CHECKS, "G-089 断言未注册进 CHECKS（死代码即此失败）"
        assert 89 in gate.TOPICS["plaintext_deps"], "G-089 不在 plaintext_deps topic 内"

    def test_verification_metadata_present_and_wellformed(self) -> None:
        # 独立第二实现：不经过 gate，直接解析 XML 判定同一事实。
        import xml.etree.ElementTree as ET
        path = ROOT / "gradle/verification-metadata.xml"
        assert path.is_file(), "gradle/verification-metadata.xml missing"
        root = ET.parse(path).getroot()
        assert root.tag == f"{self.NS}verification-metadata", root.tag
        assert root.findtext(f"{self.NS}configuration/{self.NS}verify-metadata") == "true"
        components = root.findall(f"{self.NS}components/{self.NS}component")
        assert len(components) > 0, "verification metadata has no components"

    def test_gate_check_g089_passes_on_current_tree(self) -> None:
        gate = self._gate()
        ok, detail = gate.CHECKS[89]()
        assert ok, detail
        assert "components=" in detail

    def test_gate_check_g089_fails_when_metadata_missing(self, tmp_path, monkeypatch) -> None:
        gate = self._gate()
        monkeypatch.setattr(gate, "ROOT", tmp_path)
        ok, detail = gate.CHECKS[89]()
        assert not ok
        assert "present=False" in detail
