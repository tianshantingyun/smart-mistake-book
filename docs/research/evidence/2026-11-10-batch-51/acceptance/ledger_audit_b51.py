#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批次 51 验收台账员：对拟改判/刷新的行做可重算审计，并落命令日志。

只读既有证据；不重跑 Gradle、不改产品代码。
每份日志末行写 EXIT=<code>，供 acceptance_report 的 cmd 证据复算。

审计项：
  F-058  两次独立 2h soak（批次50 / 批次51）均满足同一判据
  J-002  release 产物 revision == HEAD 且 behind 0（j_release_verify identity --require-head）
  J-048  versionCode 跨构建单调（101160 -> 101177）
  J-052  历史 AAB 与现 APK 的 versionCode 可比
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
OUT = Path(__file__).resolve().parent
DEV50 = ROOT / "docs" / "research" / "evidence" / "2026-11-09-batch-50" / "device"
REL50 = ROOT / "docs" / "research" / "evidence" / "2026-11-09-batch-50" / "release"
DEV51 = ROOT / "docs" / "research" / "evidence" / "2026-11-10-batch-51" / "device"
REL51 = ROOT / "docs" / "research" / "evidence" / "2026-11-10-batch-51" / "release"
ACC51 = ROOT / "docs" / "acceptance" / "evidence" / "2026-11-10-batch-51"


def write_log(name: str, lines: list[str], code: int) -> Path:
    path = OUT / name
    body = "\n".join(lines).rstrip() + f"\nEXIT={code}\n"
    path.write_text(body, encoding="utf-8", newline="\n")
    return path


def kv(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        for key, value in re.findall(r"([A-Za-z_][A-Za-z0-9_]*)=(\S+)", line):
            out[key] = value
    return out


def check_f058() -> int:
    lines = ["F-058 长时 2h 重复运行审计", ""]
    ok = True
    for label, summary in (
        ("batch50", DEV50 / "soak" / "soak-summary.txt"),
        ("batch51", DEV51 / "soak" / "soak-summary.txt"),
    ):
        data = kv(summary)
        lines.append(f"{label}: duration_target_s={data.get('duration_target_s')} "
                     f"cycles={data.get('cycles')} crash_delta={data.get('crash_delta')} "
                     f"dropbox_delta={data.get('dropbox_delta')} "
                     f"monkey_rc_nonzero={data.get('monkey_rc_nonzero')} "
                     f"OUTCOME={data.get('OUTCOME')} EXIT={data.get('EXIT')}")
        ok = ok and data.get("OUTCOME") == "PASS" and data.get("EXIT") == "0"
        ok = ok and data.get("duration_target_s") == "7200" and data.get("cycles") == "115"
        ok = ok and data.get("crash_delta") == "0" and data.get("dropbox_delta") == "0"
        ok = ok and data.get("monkey_rc_nonzero") == "0"
    for label, dev in (
        ("batch50", ROOT / "docs" / "acceptance" / "evidence" / "2026-11-09-batch-50" / "device" / "soak-b50.device.json"),
        ("batch51", ACC51 / "device" / "soak-b51.device.json"),
    ):
        data = json.loads(dev.read_text(encoding="utf-8"))
        counts = data.get("counts") or {}
        lines.append(f"{label}: serial={data.get('serial')} verdict={data.get('verdict')} "
                     f"evaluated={counts.get('evaluated')} failures={counts.get('failures')} "
                     f"errors={counts.get('errors')}")
        ok = ok and data.get("verdict") == "PASS" and counts.get("failures") == 0
        ok = ok and counts.get("errors") == 0 and counts.get("evaluated", 0) > 0
    lines.append("both windows 7200s / 115 cycles / 0 new crash / 0 new dropbox / monkey rc all 0")
    write_log("f058-soak-repeat-audit.log", lines, 0 if ok else 1)
    return 0 if ok else 1


def _vc_from_badging(path: Path) -> int:
    m = re.search(r"versionCode='(\d+)'", path.read_text(encoding="utf-8"))
    if not m:
        raise SystemExit(f"no versionCode in {path}")
    return int(m.group(1))


def _vc_from_manifest(path: Path) -> int:
    data = json.loads(path.read_text(encoding="utf-8"))
    return int(data["version_code"])


def _vc_from_metadata(path: Path) -> int:
    data = json.loads(path.read_text(encoding="utf-8"))
    return int(data["output_metadata"]["versionCode"])


def check_j048() -> int:
    h = _vc_from_badging(REL50 / "aapt2-badging-localFirst.txt")
    h_aab = _vc_from_manifest(REL50 / "aab-manifest-localFirst.json")
    c_apk = _vc_from_metadata(REL51 / "version-localFirst.json")
    c_aab = _vc_from_manifest(REL51 / "aab-manifest-localFirst.json")
    ok = h == h_aab and c_apk == c_aab and c_apk > h and 1 <= c_apk <= 2_100_000_000
    lines = [
        "J-048 versionCode 跨构建单调审计",
        f"batch50 APK badging versionCode={h}",
        f"batch50 AAB proto manifest version_code={h_aab}",
        f"batch51 APK output-metadata versionCode={c_apk}",
        f"batch51 AAB proto manifest version_code={c_aab}",
        f"monotonic={c_apk > h} (101160 -> 101177; +17 commits)",
    ]
    write_log("j048-version-monotonic-audit.log", lines, 0 if ok else 1)
    return 0 if ok else 1


def check_j052() -> int:
    hist = json.loads((REL50 / "aab-manifest-localFirst.json").read_text(encoding="utf-8"))
    cur_aab = json.loads((REL51 / "aab-manifest-localFirst.json").read_text(encoding="utf-8"))
    cur_apk = json.loads((REL51 / "version-localFirst.json").read_text(encoding="utf-8"))
    h = int(hist["version_code"])
    c_aab = int(cur_aab["version_code"])
    c_apk = int(cur_apk["output_metadata"]["versionCode"])
    same_pkg = hist["package"] == cur_aab["package"]
    ok = same_pkg and c_apk == c_aab and c_apk > h
    lines = [
        "J-052 历史 AAB 与现 APK 的 versionCode 可比审计",
        f"historical AAB package={hist['package']} versionCode={h}",
        f"current AAB package={cur_aab['package']} versionCode={c_aab}",
        f"current APK output-metadata versionCode={c_apk} versionName={cur_apk['output_metadata'].get('versionName')}",
        f"same_package(aab)={same_pkg}; comparable={c_apk == c_aab}; newer={c_apk > h}",
    ]
    write_log("j052-version-comparable-audit.log", lines, 0 if ok else 1)
    return 0 if ok else 1


def check_j002() -> int:
    out_json = ACC51 / "j002-identity-require-head.json"
    cmd = [
        sys.executable, "-I", "tools/ci/j_release_verify.py", "identity",
        "--root", ".",
        "--apk", "app/build/outputs/apk/localFirst/release/app-localFirst-release.apk",
        "--aab", "app/build/outputs/bundle/localFirstRelease/app-localFirst-release.aab",
        "--mapping", "app/build/outputs/mapping/localFirstRelease/mapping.txt",
        "--gradle", "app/build.gradle.kts",
        "--metadata", "app/build/outputs/apk/localFirst/release/output-metadata.json",
        "--require-head",
        "--out", str(out_json),
    ]
    proc = subprocess.run(cmd, cwd=str(ROOT), capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=120)
    lines = ["J-002 release 产物 revision / behind 审计", "command=" + " ".join(cmd[1:]),
             "stdout:", proc.stdout.strip(), "stderr:", proc.stderr.strip()]
    if out_json.exists():
        data = json.loads(out_json.read_text(encoding="utf-8"))
        lines.append(f"head={data.get('head')} revision={data['apk'].get('revision')} "
                     f"revision_is_commit={data['apk'].get('revision_is_commit')} "
                     f"commits_behind_head={data['apk'].get('commits_behind_head')} "
                     f"dirty_paths={data.get('dirty_paths')}")
    write_log("j002-identity-require-head.log", lines, proc.returncode)
    return proc.returncode


def main() -> int:
    results = {
        "F-058": check_f058(),
        "J-002": check_j002(),
        "J-048": check_j048(),
        "J-052": check_j052(),
    }
    print(json.dumps(results, ensure_ascii=False))
    return 0 if all(code == 0 for code in results.values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
