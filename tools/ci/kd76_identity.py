#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KD-76 A6 的身份代际：已安装 APK sha、设备代际、安装代际。

批次 43 / WS2 item 11（协议 `docs/research/2026-11-05-kd76-a6-protocol.md` §3.1–§3.3）。

## 它消灭的具体失败

1. **同 HEAD 不同构建被当成"同一构建"**（批次 42：两批 app sha `35e1…` vs `ec13…`，
   各自 PASS 却被当成 A6）：这里只取**宿主拉包**算出的已安装 base APK sha256
   （app 与 test 各一个），app 自报的 `KD76_META apkSha256` 只作交叉。
2. **换设备/换模拟器实例但同 fingerprint**：`deviceGenerationId` 把 serial、fingerprint、
   sdk、build.id、incremental、model、device、abilist、pageSize、nproc、AVD 名与宿主
   模拟器进程身份一起入哈希。
3. **两批之间重装同字节包**（`install -r` 改 `lastUpdateTime`）：`installGenerationId`
   把 app/test sha、firstInstallTime、lastUpdateTime、versionCode、签名行一起入哈希。

## 边界（必须如实）

- `emulatorInstanceId` 在 Windows 上来自宿主 `qemu-system-*` 进程的 PID/创建时间，
  通过命令行里的 `-port <serialPort>` 映射；拿不到时写 `NA`，并在 JSON 的
  `emulatorInstanceIdSource` 里标明 `NA`（不得假装已绑定实例）。
- 真机没有 emulator 实例，写 `NA`/`real-device`。
- 本工具不安装、不重启、不改设备；只读。

用法：
    python -I tools/ci/kd76_identity.py --serial emulator-5584 \
        --app-package com.tingyun.smartmistakebook.localfirst \
        --test-package com.tingyun.smartmistakebook.localfirst.test \
        --work-dir build/kd76-a6/identity --json
    python -I tools/ci/kd76_identity.py ... --format kv
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ADB_TIMEOUT_SECONDS = 60
PULL_TIMEOUT_SECONDS = 600

PROP_KEYS = {
    "fingerprint": "ro.build.fingerprint",
    "sdk": "ro.build.version.sdk",
    "buildId": "ro.build.id",
    "incremental": "ro.build.version.incremental",
    "model": "ro.product.model",
    "device": "ro.product.device",
    "abilist": "ro.product.cpu.abilist",
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical_generation(fields: dict[str, str]) -> str:
    """把一组 key=value 拍成稳定串再哈希；空值用 `<missing>`，不允许静默丢字段。"""
    payload = "|".join(f"{key}={fields.get(key) or '<missing>'}" for key in sorted(fields))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def parse_dumpsys_package(text: str) -> dict[str, str]:
    """从 `dumpsys package <pkg>` 里取安装代际字段；缺失保留空串。

    **签名只取 `signatures:[...]` 列表**，不取整行：`PackageSignatures{<objhash> ...}`
    里的对象哈希是 system_server 进程内的 identity hash，**每次重启都会变**。
    批次 43 实跑教训：整行哈希让 `installGenerationId` 在 20 次 reboot 后变化，
    把同一安装误判成"两批之间重装"，A6 被自己的身份门拒绝。
    """
    def first(pattern: str) -> str:
        match = re.search(pattern, text, re.MULTILINE)
        return match.group(1).strip() if match else ""

    version = first(r"^\s*versionCode=(\d+)")
    signature_list = first(r"^\s*signatures=.*?signatures:\[([^\]]*)\]")
    return {
        "versionCode": version,
        "firstInstallTime": first(r"^\s*firstInstallTime=(.+)$"),
        "lastUpdateTime": first(r"^\s*lastUpdateTime=(.+)$"),
        "signaturesDigest": (
            hashlib.sha256(signature_list.encode("utf-8")).hexdigest() if signature_list else ""
        ),
    }


def _run(command: list[str], timeout: int = ADB_TIMEOUT_SECONDS) -> str:
    completed = subprocess.run(
        command,
        capture_output=True,
        text=True,
        timeout=timeout,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(
            f"command failed ({completed.returncode}): {' '.join(command)}: "
            f"{(completed.stderr or '').strip()[:400]}"
        )
    return completed.stdout.replace("\r", "")


def find_adb(explicit: str | None) -> str:
    if explicit:
        return explicit
    found = shutil.which("adb")
    if found:
        return found
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = os.environ.get(key)
        if not root:
            continue
        for name in ("adb.exe", "adb"):
            candidate = Path(root) / "platform-tools" / name
            if candidate.is_file():
                return str(candidate)
    raise SystemExit("kd76_identity: adb not found (pass --adb or set ANDROID_HOME)")


def _serial_port(serial: str) -> str:
    match = re.search(r"emulator-(\d+)$", serial)
    return match.group(1) if match else ""


def host_emulator_instance(serial: str) -> tuple[str, str]:
    """返回 (instanceId, source)。拿不到就写 NA —— 不假装绑定。"""
    port = _serial_port(serial)
    if not port:
        return "real-device", "real-device"
    if os.name != "nt":
        return "NA", "NA"
    try:
        completed = subprocess.run(
            [
                "powershell",
                "-NoProfile",
                "-Command",
                "Get-CimInstance Win32_Process -Filter \"Name LIKE '%qemu%'\" | "
                "Select-Object ProcessId,CreationDate,CommandLine | ConvertTo-Json -Depth 3 -Compress",
            ],
            capture_output=True,
            text=True,
            timeout=90,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return "NA", "NA"
    if completed.returncode != 0 or not completed.stdout.strip():
        return "NA", "NA"
    try:
        payload = json.loads(completed.stdout)
    except json.JSONDecodeError:
        return "NA", "NA"
    entries = payload if isinstance(payload, list) else [payload]
    pattern = re.compile(rf"-port\s+{re.escape(port)}\b")
    for entry in entries:
        command = entry.get("CommandLine") or ""
        if not pattern.search(command):
            continue
        created = entry.get("CreationDate") or ""
        match = re.search(r"Date\((\d+)\)", str(created))
        epoch = match.group(1) if match else "unknown"
        return f"qemu:{entry.get('ProcessId')}:{epoch}", "host-qemu-process"
    return "NA", "NA"


def collect(
    serial: str,
    app_package: str,
    test_package: str,
    work_dir: Path,
    adb: str,
) -> dict[str, str]:
    work_dir.mkdir(parents=True, exist_ok=True)

    def adb_shell(*args: str) -> str:
        return _run([adb, "-s", serial, "shell", *args]).strip()

    fields: dict[str, str] = {"serial": serial}
    for name, prop in PROP_KEYS.items():
        fields[name] = adb_shell("getprop", prop)
    fields["pageSize"] = adb_shell("getconf", "PAGE_SIZE")
    fields["nproc"] = adb_shell("nproc")

    avd_name = "NA"
    try:
        console = _run([adb, "-s", serial, "emu", "avd", "name"])
        first = console.splitlines()[0].strip() if console.splitlines() else ""
        if first and first != "OK":
            avd_name = first
    except RuntimeError:
        avd_name = "NA"
    fields["avdName"] = avd_name
    instance_id, instance_source = host_emulator_instance(serial)
    fields["emulatorInstanceId"] = instance_id
    fields["emulatorInstanceIdSource"] = instance_source

    # 已安装包的身份：宿主拉包算 sha256，不信 app 自报值。
    for label, package in (("App", app_package), ("Test", test_package)):
        paths = adb_shell("pm", "path", package)
        lines = [line[len("package:"):] for line in paths.splitlines() if line.startswith("package:")]
        if len(lines) != 1:
            raise SystemExit(
                f"kd76_identity: expected one base.apk for {package}, got {len(lines)}: {lines}"
            )
        target = work_dir / f"installed-{label.lower()}.apk"
        _run([adb, "-s", serial, "pull", lines[0], str(target)], timeout=PULL_TIMEOUT_SECONDS)
        fields[f"installed{label}Sha256"] = sha256_file(target)
        # 同一字段两次读取必须逐字相同：不稳定字段（例如把 system_server 的 identity
        # hash 哈希进去）会让 20 次 reboot 后的复检把自己判成"重装"，详见
        # parse_dumpsys_package 的 KDoc。宁可在这里失败，也不要把不稳定值写进 plan。
        first_read = parse_dumpsys_package(_run([adb, "-s", serial, "shell", "dumpsys", "package", package]))
        second_read = parse_dumpsys_package(_run([adb, "-s", serial, "shell", "dumpsys", "package", package]))
        unstable = [key for key in first_read if first_read[key] != second_read[key]]
        if unstable:
            raise SystemExit(
                f"kd76_identity: {package} install-generation fields are not stable across two "
                f"reads: {unstable}; refusing to bind a plan to an unstable identity"
            )
        for key, value in first_read.items():
            fields[f"{label.lower()}{key[0].upper()}{key[1:]}"] = value
        if not fields[f"{label.lower()}SignaturesDigest"]:
            raise SystemExit(f"kd76_identity: no signatures line for {package}")

    fields["deviceGenerationId"] = canonical_generation(
        {
            "serial": fields["serial"],
            "fingerprint": fields["fingerprint"],
            "sdk": fields["sdk"],
            "buildId": fields["buildId"],
            "incremental": fields["incremental"],
            "model": fields["model"],
            "device": fields["device"],
            "abilist": fields["abilist"],
            "pageSize": fields["pageSize"],
            "nproc": fields["nproc"],
            "avdName": fields["avdName"],
            "emulatorInstanceId": fields["emulatorInstanceId"],
        }
    )
    fields["installGenerationId"] = canonical_generation(
        {
            "appSha256": fields["installedAppSha256"],
            "testSha256": fields["installedTestSha256"],
            "firstInstallTime": fields["appFirstInstallTime"],
            "lastUpdateTime": fields["appLastUpdateTime"],
            "versionCode": fields["appVersionCode"],
            "signaturesDigest": fields["appSignaturesDigest"],
        }
    )
    fields["appPackage"] = app_package
    fields["testPackage"] = test_package
    return fields


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="KD-76 A6 installed/device/install identity")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--app-package", required=True)
    parser.add_argument("--test-package", required=True)
    parser.add_argument("--work-dir", required=True)
    parser.add_argument("--adb", default=None)
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--format", choices=("json", "kv"), default="kv")
    args = parser.parse_args(argv)

    try:
        fields = collect(
            serial=args.serial,
            app_package=args.app_package,
            test_package=args.test_package,
            work_dir=Path(args.work_dir),
            adb=find_adb(args.adb),
        )
    except (RuntimeError, SystemExit) as error:
        print(f"kd76_identity: {error}", file=sys.stderr)
        return 2

    if args.json or args.format == "json":
        print(json.dumps(fields, ensure_ascii=False, indent=2, sort_keys=True))
    else:
        for key in sorted(fields):
            print(f"{key}={fields[key]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
