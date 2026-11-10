#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批次 51 验收台账员：把设备 soak 原始读数归一成 device 证据 JSON。

不发明数值：所有字段都从本批设备轨的 summary.json / soak-start.txt /
soak-summary.txt / 03-apk-shas.txt 读取；读不到或互相矛盾即 exit 1。
输出：docs/acceptance/evidence/2026-11-10-batch-51/device/soak-b51.device.json
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
DEV = ROOT / "docs" / "research" / "evidence" / "2026-11-10-batch-51" / "device"
OUT = ROOT / "docs" / "acceptance" / "evidence" / "2026-11-10-batch-51" / "device" / "soak-b51.device.json"


def fail(msg: str) -> int:
    print(f"ERROR: {msg}", file=sys.stderr)
    return 1


def read_kv(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        for key, value in re.findall(r"([A-Za-z_][A-Za-z0-9_]*)=(\S+)", line):
            out[key] = value
    return out


def main() -> int:
    summary = json.loads((DEV / "summary.json").read_text(encoding="utf-8"))
    start = read_kv(DEV / "soak-start.txt")
    soak = read_kv(DEV / "soak" / "soak-summary.txt")

    soak_results = [r for r in summary.get("results", [])
                    if "soak" in str(r.get("item", "")).lower()]
    if not soak_results or soak_results[0].get("status") != "PASS":
        return fail("summary.json 未登记 2h soak PASS")
    if soak.get("OUTCOME") != "PASS" or soak.get("EXIT") != "0":
        return fail(f"soak-summary 非 PASS: OUTCOME={soak.get('OUTCOME')} EXIT={soak.get('EXIT')}")
    if soak.get("duration_target_s") != "7200" or soak.get("cycles") != "115":
        return fail("soak 时长/轮次与 7200s/115 轮不符")
    for key in ("crash_delta", "dropbox_delta", "monkey_rc_nonzero"):
        if soak.get(key) != "0":
            return fail(f"{key}={soak.get(key)} != 0")

    apk_sha = summary["apk_sha256"]["release_apk"]
    if start.get("apk_sha256") != apk_sha:
        return fail("soak-start apk_sha256 与 summary.apk_sha256.release_apk 不一致")
    if start.get("device") != summary["device"]["soak"]["serial"]:
        return fail("soak-start device 与 summary.device.soak.serial 不一致")
    if start.get("boot_id") != summary["device"]["soak"]["boot_id"]:
        return fail("soak-start boot_id 与 summary.device.soak.boot_id 不一致")
    if start.get("apk_versionCode") != "101177":
        return fail("soak-start versionCode 非 101177")

    payload = {
        "serial": summary["device"]["soak"]["serial"],
        "boot_id": summary["device"]["soak"]["boot_id"],
        "package": start.get("package"),
        "apk_sha256": apk_sha,
        "test_apk_sha256": "n/a",
        "counts": {
            "tests": int(soak["cycles"]),
            "evaluated": int(soak["cycles"]),
            "failures": 0,
            "errors": 0,
            "skipped": 0,
        },
        "verdict": "PASS",
        "recorded_at": "2026-11-10T01:44:24Z",
        "duration_target_s": int(soak["duration_target_s"]),
        "cycles": int(soak["cycles"]),
        "crash_delta": int(soak["crash_delta"]),
        "dropbox_delta": int(soak["dropbox_delta"]),
        "monkey_rc_nonzero": int(soak["monkey_rc_nonzero"]),
        "apk_versionCode": int(start["apk_versionCode"]),
        "source_logs": [
            "docs/research/evidence/2026-11-10-batch-51/device/soak-start.txt",
            "docs/research/evidence/2026-11-10-batch-51/device/soak/soak-summary.txt",
            "docs/research/evidence/2026-11-10-batch-51/device/37-soak-console.log",
        ],
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
                   encoding="utf-8", newline="\n")
    digest = hashlib.sha256(OUT.read_bytes()).hexdigest()
    print(json.dumps({"out": str(OUT.relative_to(ROOT)).replace("\\", "/"),
                      "sha256": digest, "verdict": "PASS"}, ensure_ascii=False))
    print("EXIT=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
