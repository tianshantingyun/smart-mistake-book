"""Run the notification denial case after revoking permission outside the app process."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
TEST = "com.tingyun.smartmistakebook.MistakeExportNotificationsInstrumentedTest#withoutPermissionNothingIsPostedAndNothingThrows"
PACKAGES = {
    "localFirst": "com.tingyun.smartmistakebook.localfirst",
    "strictOffline": "com.tingyun.smartmistakebook.offline",
}


def instrumentation_passed(returncode: int, output: str) -> bool:
    passed = re.search(r"^OK \(1 test\)\s*$", output, re.MULTILINE)
    failed = re.search(r"FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_ABORTED", output)
    return returncode == 0 and bool(passed) and not failed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=shutil.which("adb"))
    parser.add_argument("--variant", choices=PACKAGES, action="append")
    args = parser.parse_args()
    if not args.adb:
        parser.error("adb must be on PATH or supplied with --adb")
    adb = [args.adb]
    if os.environ.get("ANDROID_SERIAL"):
        adb += ["-s", os.environ["ANDROID_SERIAL"]]
    output_dir = ROOT / "build/notification-permission-results"
    output_dir.mkdir(parents=True, exist_ok=True)
    for variant in args.variant or PACKAGES:
        package = PACKAGES[variant]
        app = ROOT / f"app/build/outputs/apk/{variant}/debug/app-{variant}-debug.apk"
        test = ROOT / f"app/build/outputs/apk/androidTest/{variant}/debug/app-{variant}-debug-androidTest.apk"
        for apk in (app, test):
            if not apk.is_file():
                raise FileNotFoundError(apk)
            subprocess.run(adb + ["install", "-r", "-t", str(apk)], check=True, timeout=120)
        subprocess.run(adb + ["shell", "pm", "revoke", package, "android.permission.POST_NOTIFICATIONS"], check=True, timeout=30)
        result = subprocess.run(
            adb + ["shell", "am", "instrument", "-w", "-e", "class", TEST,
                   "-e", "smb_notifications_denied", "true",
                   f"{package}.test/androidx.test.runner.AndroidJUnitRunner"],
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=180,
        )
        output = result.stdout + result.stderr
        (output_dir / f"{variant}.txt").write_text(output, encoding="utf-8")
        if not instrumentation_passed(result.returncode, output):
            raise RuntimeError(f"{variant} notification denial failed\n{output}")
        print(f"{variant} notification denial passed after external pm revoke")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
