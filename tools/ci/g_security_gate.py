#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G 域（安全与隐私）可执行验收门（批次 44）。

## 它消灭的具体失败

批次 41–43 的“安全”结论都停在散文：`docs/research/...ws10-security-deps.md`
写“密钥 0 / 无明文 / 权限最小”，但没有一条能复跑的命令；Mimosa 只给
`inconclusive`，于是“安全”既不能被证伪，也不能被复核。本脚本把 G 域里
**源码/清单可判定的部分**逐条变成带退出码的断言，一条断言一行输出，
失败即 exit 1。

边界（同样重要）：本脚本只读仓库文本，**不证明运行时行为**。设备侧事实
（dumpsys/aapt2/am/content 探针）由 `docs/acceptance/evidence/G/device/*.json`
单独承载；真实 Provider 外发、CVE 比对、人工金标不在本脚本能力内。

用法：

    python -I tools/ci/g_security_gate.py --topic secrets
    python -I tools/ci/g_security_gate.py --all

输出：每行 `G-NNN PASS|FAIL <detail>`，末行 `EXIT=0|1`。
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

TOPICS = {
    "secrets": list(range(1, 10)),
    "logging": list(range(10, 19)),
    "backup": list(range(19, 28)),
    "egress": list(range(28, 37)),
    "permissions": list(range(37, 46)),
    "deeplink": list(range(46, 55)),
    "intent": list(range(55, 64)),
    "fileexposure": list(range(64, 73)),
    "clipboard_screenshot": list(range(73, 82)),
    "plaintext_deps": list(range(82, 91)),
}

MAIN_MANIFEST = "app/src/main/AndroidManifest.xml"
LOCAL_FIRST_MANIFEST = "app/src/localFirst/AndroidManifest.xml"
OFFLINE_MANIFEST = "app/src/strictOffline/AndroidManifest.xml"
SOURCE_DIRS = ("app/src/main", "core", "feature")

SECRET_RE = re.compile(
    r"(sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}"
    r"|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"
    r"|ghp_[A-Za-z0-9]{36}|xox[baprs]-[A-Za-z0-9-]{10,})"
)
# 已知非密钥：批次 43 登记的两处都是 `model-task-<32hex>` 标识符，
# 命中 `sk-` 只是因为 `ta**sk-**` 相邻；按整词 `\btask-` 归属排除。
KNOWN_NON_SECRET = "model-task-"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8", errors="replace")


def kt_files() -> list[Path]:
    files: list[Path] = []
    for base in SOURCE_DIRS:
        for path in (ROOT / base).rglob("*.kt"):
            if "/build/" in path.as_posix():
                continue
            files.append(path)
    return files


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def main_kt_files() -> list[Path]:
    """产品面源码：排除 src/test 与 src/androidTest 的用例夹具。"""
    return [path for path in kt_files()
            if "/src/test/" not in path.as_posix() and "/src/androidTest/" not in path.as_posix()]


def tracked_files() -> list[str]:
    try:
        out = subprocess.run(
            ["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True
        ).stdout
    except (OSError, subprocess.CalledProcessError):
        return []
    return [item for item in out.decode("utf-8", "replace").split("\0") if item]


def manifests() -> list[Path]:
    return sorted(
        path for path in (ROOT / "app").rglob("AndroidManifest.xml")
        if "/build/" not in path.as_posix()
    ) + sorted(
        path for base in ("core", "feature")
        for path in (ROOT / base).rglob("AndroidManifest.xml")
        if "/build/" not in path.as_posix()
    )


# --------------------------------------------------------------------------
# 断言集合：id -> (callable, topic)。callable 返回 (ok, detail)。
# --------------------------------------------------------------------------

def chk_g001() -> tuple[bool, str]:
    hits: list[str] = []
    for name in tracked_files():
        path = ROOT / name
        if not path.is_file() or path.stat().st_size > 2_000_000:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for line in text.splitlines():
            if SECRET_RE.search(line) and KNOWN_NON_SECRET not in line:
                hits.append(f"{name}: {line.strip()[:80]}")
    return (not hits, f"tracked secret-pattern hits={len(hits)}" + (f" first={hits[0]}" if hits else ""))


def chk_g002() -> tuple[bool, str]:
    # 只认“凭据类标识符 = 长字面量”。协议常量（*_TOKEN = "SMART_MISTAKE_..."）
    # 是 wire 契约里的标记，不是密钥；把它们算进来会让这条断言永远红。
    pattern = re.compile(
        r"(?i)([a-z_]*api[_-]?key|[a-z_]*secretkey|[a-z_]*secret|password)\s*[=:]\s*\"[A-Za-z0-9_\-]{16,}\""
    )
    hits = [rel(path) for path in main_kt_files() if pattern.search(read(rel(path)))]
    return (not hits, f"hardcoded-credential assignments={len(hits)}" + (f" {hits[:3]}" if hits else ""))


def chk_g003() -> tuple[bool, str]:
    text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/AndroidKeystoreModelSecretVault.kt")
    need = ["KeyGenParameterSpec", "KEY_ALGORITHM_AES", "BLOCK_MODE_GCM", "GCMParameterSpec",
            'MODEL_SECRET_KEY_ALIAS = "smart_mistake_book_model_api_key_v2"']
    missing = [item for item in need if item not in text]
    return (not missing, "keystore AES/GCM vault markers=" + ("all present" if not missing else f"missing {missing}"))


def chk_g004() -> tuple[bool, str]:
    store = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/DataStoreModelConfigurationStore.kt")
    settings = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSettingsDocument.kt")
    store_uses_vault = "vault.write" in store and "vault.readSafely" in store
    settings_has_no_key = not re.search(r"(?i)apikey", settings)
    return (store_uses_vault and settings_has_no_key,
            f"secret routed to vault={store_uses_vault}; backup settings doc key field={not settings_has_no_key}")


def chk_g005() -> tuple[bool, str]:
    vault = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/AndroidKeystoreModelSecretVault.kt")
    repo = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt")
    ok = "noBackupFilesDir" in vault and "noBackupFilesDir" in repo and '"model-secrets"' in repo
    return (ok, "secret vault under noBackupFilesDir=" + str(ok))


def chk_g006() -> tuple[bool, str]:
    repo = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt")
    alias_uses_constant = "MODEL_SECRET_KEY_ALIAS" in repo
    duplicate_literal = "smart_mistake_book_model_api_key_v2" in repo
    deletes_dir = "secretVaultDir.deleteRecursively()" in repo
    return (alias_uses_constant and not duplicate_literal and deletes_dir,
            f"alias via constant={alias_uses_constant}; duplicated alias literal={duplicate_literal}; vault dir deleted={deletes_dir}")


def chk_g007() -> tuple[bool, str]:
    text = read("core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ModelConfigurationStore.kt")
    ok = "always redacted" in text and "toString" in text
    return (ok, "secret container string form redacted=" + str(ok))


def chk_g008() -> tuple[bool, str]:
    text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/ModelConfigurationValidator.kt")
    need = ["API_KEY_TOO_LONG", "CONTAINS_WHITESPACE_OR_CONTROL_CHARACTER", "API_KEY_REQUIRED"]
    missing = [item for item in need if item not in text]
    return (not missing, "api-key validation issues=" + ("all present" if not missing else f"missing {missing}"))


def chk_g009() -> tuple[bool, str]:
    codec = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt")
    leaked = "model-secrets" in codec or ("api" in codec.lower() and "secret" in codec.lower())
    return (not leaked, "archive codec references secret material=" + str(leaked))


def chk_g010() -> tuple[bool, str]:
    text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryConsentStore.kt")
    ok = "UNKNOWN" in text and "isEnabled" in text
    off_by_default = re.search(r"UNKNOWN[^\n]*(?:OFF|false)", text) is not None
    return (ok and off_by_default, f"consent unknown-state documented={ok}; off-by-default binding={off_by_default}")


def chk_g011() -> tuple[bool, str]:
    tokens = ("java.net.", "okhttp", "HttpURLConnection", "Socket(", "URLConnection")
    hits: list[str] = []
    base = ROOT / "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry"
    for path in base.glob("*.kt"):
        text = path.read_text(encoding="utf-8", errors="replace")
        for token in tokens:
            if token in text:
                hits.append(f"{path.name}:{token}")
    return (not hits, "telemetry network tokens=" + ("0" if not hits else str(hits[:4])))


def chk_g012() -> tuple[bool, str]:
    log_call = re.compile(r"Log\.[dewiv]\((.*?)\)", re.S)
    # 只认“把凭据变量插进日志”，不认日志文案里出现 case=stale-token 这类标签。
    sensitive = re.compile(r"(\$\{?(?:apiKey|api_key|secretKey|credential|password)|\bBearer\s*\$\{)")
    hits: list[str] = []
    total = 0
    for path in main_kt_files():
        text = read(rel(path))
        for match in log_call.finditer(text):
            total += 1
            if sensitive.search(match.group(1)):
                hits.append(rel(path))
    return (not hits, f"log calls scanned={total}; sensitive-argument hits={len(hits)}" + (f" {hits[:3]}" if hits else ""))


def chk_g013() -> tuple[bool, str]:
    base = ROOT / "app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics"
    tokens = ("java.net.", "okhttp", "HttpURLConnection", "Socket(", "URL(")
    hits = [f"{p.name}:{t}" for p in base.glob("*.kt") for t in tokens if t in read(rel(p))]
    copies_db = any("database" in read(rel(p)).lower() and "copy" in read(rel(p)).lower() for p in base.glob("*.kt"))
    return (not hits and not copies_db, f"diagnostics network tokens={len(hits)}; copies database={copies_db}")


def chk_g015() -> tuple[bool, str]:
    mods = ("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model",
            "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings",
            "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup")
    hits = [rel(p) for base in mods for p in (ROOT / base).rglob("*.kt") if "println(" in read(rel(p))]
    return (not hits, "stdout prints in security-sensitive modules=" + ("0" if not hits else str(hits[:3])))


def chk_g017() -> tuple[bool, str]:
    log_call = re.compile(r"Log\.[dewiv]\((.*?)\)", re.S)
    # 用户内容只会以“变量插值”的形式进日志；日志文案里的 question(s)/Answer-key
    # 是计数与错误分类，不构成内容外泄。
    pii = re.compile(r"\$\{?(?:question|answer|document)")
    hits = [rel(p) for p in main_kt_files()
            if any(pii.search(m.group(1)) for m in log_call.finditer(read(rel(p))))]
    return (not hits, "log calls carrying user content fields=" + ("0" if not hits else str(hits[:3])))


def chk_g018() -> tuple[bool, str]:
    texts = [
        read("core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ModelConfigurationStore.kt"),
        read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/settings/AndroidKeystoreModelSecretVault.kt"),
    ]
    leaks = [i for i, t in enumerate(texts) if re.search(r"apiKey\s*=\s*apiKey\b", t)]
    return (not leaks, f"secret echoed into data-class copy={len(leaks)}")


def chk_g019() -> tuple[bool, str]:
    text = read(MAIN_MANIFEST)
    ok = 'android:allowBackup="false"' in text
    return (ok, "main manifest allowBackup=false=" + str(ok))


def chk_g020() -> tuple[bool, str]:
    text = read("app/src/main/res/xml/backup_rules.xml")
    domains = ("root", "file", "database", "sharedpref", "external")
    missing = [d for d in domains if f'<exclude domain="{d}" path="." />' not in text]
    return (not missing, "full-backup excludes=" + ("all five domains" if not missing else f"missing {missing}"))


def chk_g021() -> tuple[bool, str]:
    text = read("app/src/main/res/xml/data_extraction_rules.xml")
    ok = ('<cloud-backup disableIfNoEncryptionCapabilities="true">' in text
          and "<device-transfer>" in text
          and text.count('<exclude domain="database" path="." />') == 2)
    return (ok, "cloud + device-transfer both exclude db=" + str(ok))


def chk_g023() -> tuple[bool, str]:
    text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSettingsDocument.kt")
    return (not re.search(r"(?i)apikey|api_key|keystore", text),
            "settings document carries credential fields=" + str(bool(re.search(r"(?i)apikey|api_key|keystore", text))))


def chk_g024() -> tuple[bool, str]:
    text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt")
    ok = 'name.contains("..")' in text and 'name.startsWith("/")' in text
    return (ok, "archive entry traversal guard=" + str(ok))


def chk_g025() -> tuple[bool, str]:
    text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt")
    ok = all(token in text for token in ("DATABASE_ENTRY", "SETTINGS_ENTRY", "ASSET_PREFIX", 'return null'))
    return (ok, "restore path whitelist=" + str(ok))


def chk_g026() -> tuple[bool, str]:
    text = read("core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt")
    ok = "linkedSetOf(9, 13, 15)" in text and "requireCreatable" in text
    return (ok, "allowed masks {9,13,15} with require guard=" + str(ok))


def chk_g027() -> tuple[bool, str]:
    text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt")
    ok = "closureDigest == recomputed" in text
    return (ok, "closure digest recomputed on read=" + str(ok))


def run_script(rel_path: str, *args: str) -> tuple[bool, str]:
    try:
        proc = subprocess.run(
            [sys.executable, "-I", str(ROOT / rel_path), *args],
            cwd=ROOT, capture_output=True, text=True, timeout=180,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        return (False, f"{rel_path} could not run: {exc}")
    tail = (proc.stdout or proc.stderr).strip().splitlines()[-1:] or [""]
    return (proc.returncode == 0, f"{rel_path} rc={proc.returncode} last={tail[0][:100]}")


def chk_g028() -> tuple[bool, str]:
    return run_script("tools/ci/check_telemetry_compliance.py")


def chk_g029() -> tuple[bool, str]:
    main_m = read(MAIN_MANIFEST)
    cfg = read("app/src/localFirst/res/xml/network_security_config.xml")
    ok = 'android:usesCleartextTraffic="false"' in main_m and 'cleartextTrafficPermitted="false"' in cfg
    return (ok, "cleartext disabled in manifest and NSC=" + str(ok))


def chk_g030() -> tuple[bool, str]:
    cfg = read("app/src/localFirst/res/xml/network_security_config.xml")
    ok = ('<base-config cleartextTrafficPermitted="false">' in cfg
          and '<certificates src="system" />' in cfg
          and "user" not in cfg)
    return (ok, "NSC trusts system anchors only=" + str(ok))


def chk_g031() -> tuple[bool, str]:
    text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTransport.kt")
    ok = ('base.scheme != "https"' in text and "base.username.isNotEmpty()" in text
          and "UnsafeModelEndpointException" in text)
    return (ok, "https-only + no embedded credentials=" + str(ok))


def chk_g032() -> tuple[bool, str]:
    text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTransport.kt")
    ok = ("isGlobalUnicast" in text and "isPubliclyRoutable" in text
          and "addresses.any { !it.isPubliclyRoutable() }" in text)
    return (ok, "SSRF guard rejects non-public addresses=" + str(ok))


def chk_g033() -> tuple[bool, str]:
    text = read("core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelEgress.kt")
    ok = ("EGRESS_AUTHORIZATION_REQUIRED" in text
          and "request.egressManifest ?: throw ModelEgressAuthorizationException" in text)
    return (ok, "external egress requires manifest=" + str(ok))


def chk_g034() -> tuple[bool, str]:
    text = read("core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiCompatibleModelGateway.kt")
    ok = text.count("requireCurrentAuthorizationBeforeEnqueue") >= 2 and "requireCurrentExternalAuthorization" in text
    return (ok, f"pre-enqueue revalidation call sites={text.count('requireCurrentAuthorizationBeforeEnqueue')}")


def chk_g035() -> tuple[bool, str]:
    text = read(OFFLINE_MANIFEST)
    ok = ('android.permission.INTERNET' in text and 'tools:node="remove"' in text)
    return (ok, "strictOffline removes INTERNET=" + str(ok))


def chk_g037() -> tuple[bool, str]:
    main_m = read(MAIN_MANIFEST)
    declared = re.findall(r'<uses-permission android:name="([^"]+)"', main_m)
    flavor = re.findall(r'<uses-permission android:name="([^"]+)"', read(LOCAL_FIRST_MANIFEST))
    dangerous = [p for p in declared + flavor if p.split(".")[-1] in {
        "CAMERA", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES",
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "READ_CONTACTS", "SEND_SMS",
        "RECORD_AUDIO", "MANAGE_EXTERNAL_STORAGE", "QUERY_ALL_PACKAGES",
    }]
    return (not dangerous, f"main={len(declared)} flavor={len(flavor)} dangerous={dangerous}")


def chk_g038() -> tuple[bool, str]:
    text = read("app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderScreen.kt")
    ok = "permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)" in text
    return (ok, "runtime POST_NOTIFICATIONS request=" + str(ok))


def chk_g039() -> tuple[bool, str]:
    text = read(MAIN_MANIFEST)
    ok = ('android.hardware.camera' in text and 'android:required="false"' in text
          and "android.permission.CAMERA" not in text)
    return (ok, "camera feature optional, no CAMERA permission=" + str(ok))


def chk_g040() -> tuple[bool, str]:
    main_m, flavor, offline = read(MAIN_MANIFEST), read(LOCAL_FIRST_MANIFEST), read(OFFLINE_MANIFEST)
    ok = ("android.permission.INTERNET" not in main_m
          and "android.permission.INTERNET" in flavor
          and "android.permission.INTERNET" in offline
          and 'tools:node="remove"' in offline)
    return (ok, "INTERNET only in networked flavor, removed in strictOffline=" + str(ok))


def chk_g041() -> tuple[bool, str]:
    texts = [read(rel(p)) for p in manifests()]
    banned = ("MANAGE_EXTERNAL_STORAGE", "QUERY_ALL_PACKAGES", "READ_MEDIA_", "READ_EXTERNAL_STORAGE")
    hits = [b for b in banned if any(b in t for t in texts)]
    return (not hits, "banned permissions in manifests=" + ("0" if not hits else str(hits)))


def chk_g042() -> tuple[bool, str]:
    export = read("app/src/main/kotlin/com/tingyun/smartmistakebook/MistakeExportNotifications.kt")
    platform = read("app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderPlatform.kt")
    ok = ("if (!canPost()) return" in export
          and "ContextCompat.checkSelfPermission" in platform
          and "Manifest.permission.POST_NOTIFICATIONS" in platform)
    return (ok, "export + reminder both gate on the shared notification predicate=" + str(ok))


def chk_g043() -> tuple[bool, str]:
    texts = [read(rel(p)) for p in manifests()]
    has_type = any("android:foregroundServiceType" in t for t in texts)
    return (not has_type, "foregroundServiceType declared=" + str(has_type))


def chk_g046() -> tuple[bool, str]:
    texts = [read(rel(p)) for p in manifests()]
    ok = not any("android.intent.action.VIEW" in t for t in texts) and not any(
        "android.intent.category.BROWSABLE" in t for t in texts)
    return (ok, "no VIEW/BROWSABLE filters in any manifest=" + str(ok))


def chk_g047() -> tuple[bool, str]:
    texts = [read(rel(p)) for p in manifests()]
    ok = not any("android:scheme" in t for t in texts)
    return (ok, "no custom URI scheme declared=" + str(ok))


def chk_g048() -> tuple[bool, str]:
    texts = [read(rel(p)) for p in manifests()]
    return (not any("autoVerify" in t for t in texts), "autoVerify declarations=" + str(any("autoVerify" in t for t in texts)))


def chk_g049() -> tuple[bool, str]:
    hits = [name for name in tracked_files() if name.endswith("assetlinks.json") or ".well-known" in name]
    return (not hits, "digital-asset-link declarations=" + ("0" if not hits else str(hits[:3])))


def chk_g052() -> tuple[bool, str]:
    text = read(MAIN_MANIFEST)
    ok = ('android:mimeType="image/*"' in text and 'android:mimeType="application/pdf"' in text
          and 'android:mimeType="*/*"' not in text and text.count("android.intent.action.SEND") == 2)
    return (ok, "share filters limited to image/* and application/pdf=" + str(ok))


def chk_g053() -> tuple[bool, str]:
    hits = [rel(p) for p in main_kt_files() if "class CaptureShareIntake" in read(rel(p))]
    body = read(hits[0]) if hits else ""
    ok = bool(hits) and all(token in body for token in ("MAX_", "scheme", "mimeType"))
    return (ok, f"share intake validation in {hits[0] if hits else 'missing'}={ok}")


RELEASE_MERGED = ("app/build/intermediates/merged_manifest/localFirstRelease/"
                  "processLocalFirstReleaseMainManifest/AndroidManifest.xml")


def _merged_exported(rel_path: str) -> list[tuple[str, str, str | None, str | None]]:
    """返回 (tag, name, permission, enabled)；清单不存在时返回空表。"""
    import xml.etree.ElementTree as ET
    path = ROOT / rel_path
    if not path.exists():
        return []
    a = "{http://schemas.android.com/apk/res/android}"
    root = ET.parse(path).getroot()
    app = root.find("application")
    rows = []
    for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
        for element in (app.findall(tag) if app is not None else []):
            if element.get(a + "exported") == "true":
                rows.append((tag, element.get(a + "name") or "",
                             element.get(a + "permission"), element.get(a + "enabled")))
    return rows


def chk_g055() -> tuple[bool, str]:
    text = read(MAIN_MANIFEST)
    author_side = text.count('android:exported="true"') == 1 and "MainActivity" in text
    merged = _merged_exported(RELEASE_MERGED)
    if not merged:
        return (False, f"release merged manifest missing: {RELEASE_MERGED}")
    app_owned = [name for _, name, _, _ in merged if name.startswith("com.tingyun")]
    return (author_side and app_owned == ["com.tingyun.smartmistakebook.MainActivity"],
            f"main exported=1; release merged app-owned exported={app_owned}; library exported={len(merged) - len(app_owned)}")


def chk_g056() -> tuple[bool, str]:
    return run_script("tools/ci/check_release_manifest.py")


def chk_g057() -> tuple[bool, str]:
    text = read("core/export/src/main/AndroidManifest.xml") + read("core/data/src/main/AndroidManifest.xml") + read("feature/capture/src/main/AndroidManifest.xml")
    ok = text.count('android:exported="false"') == 4 and text.count('android:grantUriPermissions="true"') == 4
    return (ok, f"file providers non-exported={text.count('android:exported=\"false\"')} grantUri={text.count('grantUriPermissions')}")


def chk_g058() -> tuple[bool, str]:
    text = read(MAIN_MANIFEST)
    ok = ('android:isolatedProcess="true"' in text and 'android:process=":visual_js"' in text
          and 'android:exported="false"' in text and "VisualJsService" in text)
    return (ok, "visual JS service isolated and non-exported=" + str(ok))


def chk_g059() -> tuple[bool, str]:
    sites = sum(read(rel(p)).count("PendingIntent.get") for p in main_kt_files())
    immutable = sum(read(rel(p)).count("PendingIntent.FLAG_IMMUTABLE") for p in main_kt_files())
    mutable = sum(read(rel(p)).count("PendingIntent.FLAG_MUTABLE") for p in main_kt_files())
    return (sites == immutable and mutable == 0,
            f"PendingIntent sites={sites} FLAG_IMMUTABLE={immutable} FLAG_MUTABLE={mutable}")


def chk_g060() -> tuple[bool, str]:
    files = ["app/src/main/kotlin/com/tingyun/smartmistakebook/MistakeExportNotifications.kt",
             "app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderPlatform.kt"]
    ok = all("VISIBILITY_PRIVATE" in read(f) for f in files)
    return (ok, "notification lock-screen visibility private=" + str(ok))


def chk_g061() -> tuple[bool, str]:
    hits: list[str] = []
    for path in main_kt_files():
        text = read(rel(path))
        for match in re.finditer(r"\.startActivity\(", text):
            window = text[max(0, match.start() - 600): match.start() + 200]
            if "createChooser" in window or "Intent(" in window:
                continue
            hits.append(rel(path))
    return (not hits, f"startActivity sites without explicit/chooser intent={len(hits)}" + (f" {hits[:3]}" if hits else ""))


def chk_g062() -> tuple[bool, str]:
    hits = [rel(p) for p in main_kt_files() if "Intent.ACTION_VIEW" in read(rel(p))]
    return (not hits, "ACTION_VIEW consumers=" + ("0" if not hits else str(hits[:3])))


def chk_g063() -> tuple[bool, str]:
    merged = _merged_exported(RELEASE_MERGED)
    if not merged:
        return (False, f"release merged manifest missing: {RELEASE_MERGED}")
    unguarded = [
        name for _, name, permission, enabled in merged
        if not name.startswith("com.tingyun") and not permission and enabled != "false"
    ]
    return (not unguarded,
            f"library exported components={len(merged)}; without permission or disabled={unguarded}")


def chk_g064() -> tuple[bool, str]:
    paths = ["core/export/src/main/res/xml/mistake_pdf_export_paths.xml",
             "core/export/src/main/res/xml/mistake_docx_export_paths.xml",
             "core/data/src/main/res/xml/batch_import_file_paths.xml",
             "feature/capture/src/main/res/xml/capture_file_paths.xml"]
    bad = []
    for name in paths:
        text = read(name)
        if 'path="."' in text or 'path="/"' in text:
            bad.append(name)
    return (not bad, f"providers exposing app root={len(bad)}")


def chk_g065() -> tuple[bool, str]:
    return chk_g057()


def chk_g067() -> tuple[bool, str]:
    # 内部 file:// 句柄（ML Kit InputImage、正文记录）本身不是暴露面；危险的是把
    # file:// 交给别的应用（Intent/EXTRA_STREAM/ClipData）。只断言后者为 0。
    offenders = []
    internal = 0
    for path in main_kt_files():
        for line in read(rel(path)).splitlines():
            if "Uri.fromFile(" not in line and '"file://' not in line:
                continue
            if any(token in line for token in ("Intent", "EXTRA_STREAM", "ClipData", "startActivity")):
                offenders.append(f"{rel(path)}: {line.strip()[:80]}")
            else:
                internal += 1
    return (not offenders, f"file:// handed to other apps={len(offenders)}; internal handles={internal}")

def chk_g068() -> tuple[bool, str]:
    pdf = read("core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt")
    docx = read("core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeDocxDelivery.kt")
    ok = pdf.count("FLAG_GRANT_READ_URI_PERMISSION") >= 1 and docx.count("FLAG_GRANT_READ_URI_PERMISSION") >= 1
    return (ok, f"share grants: pdf={pdf.count('FLAG_GRANT_READ_URI_PERMISSION')} docx={docx.count('FLAG_GRANT_READ_URI_PERMISSION')}")


def chk_g069() -> tuple[bool, str]:
    pdf = read("core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt")
    ok = ("canonicalFile" in pdf and "contentDirectory?.parentFile == allowedDirectory" in pdf
          and "prepared.verifyIntegrity()" in pdf)
    return (ok, "share URI canonical containment + integrity=" + str(ok))


def chk_g070() -> tuple[bool, str]:
    text = read("core/data/src/main/res/xml/batch_import_file_paths.xml")
    ok = "<files-path" in text and "<external-path" not in text and "<external-files-path" not in text
    return (ok, "batch import staging on private files-path=" + str(ok))


def chk_g071() -> tuple[bool, str]:
    hits = [rel(p) for p in main_kt_files()
            if "getExternalFilesDir" in read(rel(p)) or "getExternalStorageDirectory" in read(rel(p))]
    return (not hits, "external storage writes=" + ("0" if not hits else str(hits[:3])))


def chk_g072() -> tuple[bool, str]:
    docx = read("core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeDocxDelivery.kt")
    ok = "canonicalFile" in docx and "parentFile == allowedDirectory" in docx
    return (ok, "docx share URI canonical containment=" + str(ok))


def chk_g073() -> tuple[bool, str]:
    hits = [rel(p) for p in main_kt_files() if "setClipEntry" in read(rel(p)) or "setPrimaryClip" in read(rel(p))]
    return (hits == ["core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/ClaudeCopyButton.kt"],
            f"clipboard write sites={hits}")


def chk_g074() -> tuple[bool, str]:
    hits = [rel(p) for p in main_kt_files()
            if "setClipEntry" in read(rel(p)) or "setPrimaryClip" in read(rel(p))
            if re.search(r"(?i)apikey|secret|bearer|token", read(rel(p)))]
    return (not hits, "clipboard write sites referencing credentials=" + ("0" if not hits else str(hits)))


def chk_g075() -> tuple[bool, str]:
    text = read("core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/ClaudeCopyButton.kt")
    ok = "ClipData.newPlainText(label, text)" in text
    return (ok, "clipboard entry labelled=" + str(ok))


def chk_g076() -> tuple[bool, str]:
    hits = [rel(p) for p in main_kt_files() if "getPrimaryClip" in read(rel(p)) or "getClipEntry" in read(rel(p))]
    return (not hits, "clipboard read sites=" + ("0" if not hits else str(hits[:3])))


def chk_g077() -> tuple[bool, str]:
    hits = [rel(p) for p in main_kt_files() if "FLAG_SECURE" in read(rel(p))]
    return (hits == ["app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt"],
            f"FLAG_SECURE sites={hits}")


def chk_g078() -> tuple[bool, str]:
    text = read("app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt")
    ok = ("window?.addFlags" in text and "onDispose { window?.clearFlags" in text)
    return (ok, "FLAG_SECURE added and cleared on dispose=" + str(ok))


def chk_g082() -> tuple[bool, str]:
    key_files = [name for name in tracked_files()
                 if re.search(r"\.(jks|keystore|p12|pfx|pem)$", name, re.I)
                 or name.endswith("keystore.properties")]
    return (not key_files, "signing/key material tracked=" + ("0" if not key_files else str(key_files[:3])))


def chk_g083() -> tuple[bool, str]:
    try:
        out = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT,
                             capture_output=True, text=True, check=True).stdout
    except (OSError, subprocess.CalledProcessError) as exc:
        return (False, f"git status failed: {exc}")
    untracked = [line[3:] for line in out.splitlines() if line.startswith("?? ")]
    hits = []
    for name in untracked:
        path = ROOT / name
        if not path.is_file() or path.stat().st_size > 1_000_000:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for line in text.splitlines():
            if SECRET_RE.search(line) and KNOWN_NON_SECRET not in line:
                hits.append(f"{name}: {line.strip()[:60]}")
    return (not hits, f"untracked files scanned={len(untracked)}; secret hits={len(hits)}")


def chk_g084() -> tuple[bool, str]:
    text = read("app/build.gradle.kts")
    ok = all(token in text for token in ("RELEASE_", "System.getenv"))
    literals = re.findall(r'storePassword\s*=\s*"', text)
    return (ok and not literals, "signing reads env vars=" + str(ok) + f"; inline passwords={len(literals)}")


def chk_g085() -> tuple[bool, str]:
    return run_script("tools/ci/check_dependency_pins.py")


def chk_g086() -> tuple[bool, str]:
    text = read("gradle/libs.versions.toml")
    want = {"quickjs-kt-android": "1.0.15", "okhttp": "5.4.0", "room3": "3.0.0",
            "mlkit-text-recognition": "16.0.1", "kotlin": "2.3.21"}
    missing = [f"{k}={v}" for k, v in want.items()
               if not re.search(rf'(?m)^{re.escape(k)}\s*=\s*"{re.escape(v)}"', text)]
    return (not missing, "pinned direct coordinates=" + ("all recorded" if not missing else f"drift {missing}"))


def chk_g089() -> tuple[bool, str]:
    """G-089：Gradle 依赖校验元数据存在且可判定。

    它消灭的具体失败：批次 46 时本断言以 chk_g088 的名字写成但从未注册进
    CHECKS（死代码），`--topic plaintext_deps` 在 gradle/verification-metadata.xml
    整个缺失时仍 exit 0，台账 G-089 行因此没有任何可复跑证据、只能 FAIL。
    这里不只查存在：还解析 XML、确认根元素与 verify-metadata=true、组件数>0，
    否则一个空壳 XML 也会被读成"已启用"。
    """
    path = ROOT / "gradle/verification-metadata.xml"
    if not path.is_file():
        return (False, "gradle verification metadata present=False "
                       "(missing gradle/verification-metadata.xml)")
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        return (False, f"gradle verification metadata unparsable: {exc}")
    ns = "{https://schema.gradle.org/dependency-verification}"
    if root.tag != f"{ns}verification-metadata":
        return (False, f"gradle verification metadata unexpected root={root.tag}")
    components = root.findall(f"{ns}components/{ns}component")
    verify_metadata = root.findtext(f"{ns}configuration/{ns}verify-metadata")
    ok = len(components) > 0 and verify_metadata == "true"
    return (ok, f"gradle verification metadata components={len(components)} "
                f"verify_metadata={verify_metadata}")


def chk_g090() -> tuple[bool, str]:
    text = read("settings.gradle.kts")
    repos = re.findall(r"(?m)^\s+(google\(\)|mavenCentral\(\)|maven\()", text)
    ok = ("FAIL_ON_PROJECT_REPOS" in text and set(repos) <= {"google()", "mavenCentral()"})
    return (ok, f"repositories={sorted(set(repos))}; FAIL_ON_PROJECT_REPOS={('FAIL_ON_PROJECT_REPOS' in text)}")


CHECKS = {
    1: chk_g001, 2: chk_g002, 3: chk_g003, 4: chk_g004, 5: chk_g005, 6: chk_g006,
    7: chk_g007, 8: chk_g008, 9: chk_g009,
    10: chk_g010, 11: chk_g011, 12: chk_g012, 13: chk_g013, 15: chk_g015,
    17: chk_g017, 18: chk_g018,
    19: chk_g019, 20: chk_g020, 21: chk_g021, 23: chk_g023, 24: chk_g024,
    25: chk_g025, 26: chk_g026, 27: chk_g027,
    28: chk_g028, 29: chk_g029, 30: chk_g030, 31: chk_g031, 32: chk_g032,
    33: chk_g033, 34: chk_g034, 35: chk_g035,
    37: chk_g037, 38: chk_g038, 39: chk_g039, 40: chk_g040, 41: chk_g041,
    42: chk_g042, 43: chk_g043,
    46: chk_g046, 47: chk_g047, 48: chk_g048, 49: chk_g049, 52: chk_g052, 53: chk_g053,
    55: chk_g055, 56: chk_g056, 57: chk_g057, 58: chk_g058, 59: chk_g059, 60: chk_g060,
    61: chk_g061, 62: chk_g062, 63: chk_g063,
    64: chk_g064, 65: chk_g065, 67: chk_g067, 68: chk_g068, 69: chk_g069, 70: chk_g070,
    71: chk_g071, 72: chk_g072,
    73: chk_g073, 74: chk_g074, 75: chk_g075, 76: chk_g076, 77: chk_g077, 78: chk_g078,
    82: chk_g082, 83: chk_g083, 84: chk_g084, 85: chk_g085, 86: chk_g086,
    89: chk_g089, 90: chk_g090,
}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="G 域安全验收门")
    parser.add_argument("--topic", choices=sorted(TOPICS))
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args(argv)
    if not args.topic and not args.all:
        parser.error("need --topic or --all")

    numbers = TOPICS[args.topic] if args.topic else sorted(CHECKS)
    failed = 0
    for number in numbers:
        func = CHECKS.get(number)
        if func is None:
            continue
        try:
            ok, detail = func()
        except Exception as exc:  # 断言自身炸掉也要显式红，不能静默
            ok, detail = False, f"check raised {type(exc).__name__}: {exc}"
        print(f"G-{number:03d} {'PASS' if ok else 'FAIL'} {detail}")
        if not ok:
            failed += 1
    print(f"RESULT: {len(numbers) - failed} passed, {failed} failed, topic={args.topic or 'all'}")
    print(f"EXIT={0 if failed == 0 else 1}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
