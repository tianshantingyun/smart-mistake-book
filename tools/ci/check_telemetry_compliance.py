#!/usr/bin/env python3
"""R5 §4.2 static compliance checks (V1/V2/V3/V9/V10/V11/V12 source-level parts).

Run from the repository root:

    python -I tools/ci/check_telemetry_compliance.py

The script only reads fixed, policy-defined paths (no user input, no ".."). It is a
source-level audit, not a substitute for `aapt2`/merged-manifest or device packet
capture; those remain separate evidence levels in the R5 document.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT_MARKER = "settings.gradle.kts"
TELEMETRY_DIR = Path(
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry"
)
CONSENT_STORE = TELEMETRY_DIR / "TelemetryConsentStore.kt"
APPLICATION = Path(
    "app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt"
)
STRICT_OFFLINE_MANIFEST = Path("app/src/strictOffline/AndroidManifest.xml")
LOCAL_FIRST_MANIFEST = Path("app/src/localFirst/AndroidManifest.xml")
BUILD_FILES = (
    Path("gradle/libs.versions.toml"),
    Path("app/build.gradle.kts"),
    Path("core/data/build.gradle.kts"),
    Path("settings.gradle.kts"),
)

NETWORK_TOKENS = (
    "java.net.",
    "okhttp",
    "HttpURLConnection",
    "Socket(",
    "URL(",
    "android.permission.INTERNET",
)
UPLOAD_TOKENS = ("telemetry-upload", "enqueue", "WorkManager", "OkHttpClient", "Retrofit")
ANALYTICS_SDKS = (
    "firebase",
    "opentelemetry",
    "appsflyer",
    "umeng",
    "sensorsdata",
    "play-services-analytics",
)
AUDIT_KEYS = (
    "policy_version",
    "policy_hash",
    "granted_at_epoch_millis",
    "withdrawn_at_epoch_millis",
    "age_band",
    "guardian_confirmed",
)
IDENTITY_KEYS = ("student_name", "phone", "email", "android_id", "imei", "open_id")


def read(path: Path) -> str:
    with path.open("r", encoding="utf-8") as handle:
        return handle.read()


def telemetry_source() -> str:
    files = sorted(TELEMETRY_DIR.glob("*.kt"))
    if not files:
        raise AssertionError(f"no Kotlin sources under {TELEMETRY_DIR}")
    return "\n".join(read(path) for path in files)


def check_v1() -> None:
    store = read(CONSENT_STORE)
    matches = re.findall(r"TelemetryConsentState\.GRANTED", store)
    assert len(matches) == 1, (
        "GRANTED may only be written by TelemetryConsentStore.grant(); "
        f"found {len(matches)} occurrences"
    )


def check_v2() -> None:
    application = read(APPLICATION)
    assert "Telemetry" not in application, (
        "SmartMistakeBookApplication must not initialize telemetry in onCreate"
    )


def check_v3() -> None:
    source = telemetry_source()
    for token in NETWORK_TOKENS:
        assert token not in source, f"telemetry source contains network token {token!r}"


def check_v9() -> None:
    strict = read(STRICT_OFFLINE_MANIFEST)
    assert "android.permission.INTERNET" in strict
    assert "android.permission.ACCESS_NETWORK_STATE" in strict
    assert 'tools:node="remove"' in strict
    local_first = read(LOCAL_FIRST_MANIFEST)
    assert "android.permission.INTERNET" in local_first


def check_v10() -> None:
    combined = "\n".join(read(path) for path in BUILD_FILES).lower()
    for sdk in ANALYTICS_SDKS:
        assert sdk not in combined, f"build files declare analytics SDK {sdk!r}"


def check_v11() -> None:
    store = read(CONSENT_STORE)
    for key in AUDIT_KEYS:
        assert key in store, f"consent store is missing audit key {key!r}"
    lowered = store.lower()
    for identity in IDENTITY_KEYS:
        assert identity not in lowered, f"consent store must not persist {identity!r}"


def check_v12() -> None:
    source = telemetry_source()
    for token in UPLOAD_TOKENS:
        assert token not in source, f"telemetry source contains upload token {token!r}"


def main() -> int:
    if not Path(ROOT_MARKER).is_file():
        print(
            f"run this script from the repository root (missing {ROOT_MARKER} in {Path.cwd()})",
            file=sys.stderr,
        )
        return 2

    checks = {
        "V1": check_v1,
        "V2": check_v2,
        "V3": check_v3,
        "V9": check_v9,
        "V10": check_v10,
        "V11": check_v11,
        "V12": check_v12,
    }
    failed = 0
    for name, check in checks.items():
        try:
            check()
            print(f"{name} PASS")
        except AssertionError as error:
            failed += 1
            print(f"{name} FAIL: {error}")

    print(
        "note: V9 is source-level only; merged-manifest/aapt2 and device packet capture "
        "are separate evidence levels (R5 §4.2)."
    )
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
