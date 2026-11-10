#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批次 51 台账更新（只改目标行，按 ID 定位；改完逐行对比）。

范围：A-023/A-028、B-001、E-017/E-065/E-075/E-080、F-055/F-057/F-058/F-063、
H-024/H-036/H-053、J-001/J-002/J-019/J-020/J-021/J-043/J-048/J-052、
FIX-061/FIX-064、GATE-091/GATE-092/GATE-099/GATE-096/GATE-097/GATE-098/GATE-100。

不新增/删除行；不改其他行；不写 review JSON。
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"

P51 = "docs/research/evidence/2026-11-10-batch-51"
ACC = "docs/acceptance/evidence/2026-11-10-batch-51"

UPDATES: dict[str, dict] = {
    "A-023": {
        "method": "BLOCKED: 大队列扫描的设备端耗时/内存与 OOM/ANR 未测（批次51 3E 设备用例 OK(4) 已跑："
                  "队外捞回 10、时间预算先绑住、同日累计 ≤10、无考试日指纹一致；非大数据量耗时/内存门）；"
                  "出口见 docs/acceptance/runbook.md §9",
        "append": [f"cmd:{P51}/device/26-instrument-3e-rerun.log"],
    },
    "A-028": {"append": [f"cmd:{P51}/device/22-instrument-library-6b3-5c.log"]},
    "B-001": {"append": [f"cmd:{P51}/device/05-instrument-migration-matrix.log"]},
    "E-017": {"append": [f"cmd:{P51}/device/24-instrument-app-5b-aigc-capability.log"]},
    "E-065": {"append": [f"cmd:{P51}/device/23-instrument-5c-realrepo-js-painter.log"]},
    "E-075": {"append": [f"cmd:{P51}/device/26-instrument-3e-rerun.log"]},
    "E-080": {"append": [f"cmd:{P51}/device/21-instrument-kd67.log"]},
    "F-055": {"append": [f"device:{ACC}/device/soak-b51.device.json",
                          f"file:{P51}/device/37-soak-console.log"]},
    "F-057": {
        "method_append": "；批次51 7200s/115 轮仍未命中（crash_delta=0、dropbox_delta=0），"
                         "正向判据未满足，维持 FAIL",
        "append": [f"device:{ACC}/device/soak-b51.device.json",
                   f"file:{P51}/device/37-soak-console.log"],
    },
    "F-058": {
        "check": "长时 2h：重复运行（批次50 与批次51 两个独立 7200s 窗口）",
        "method": "同一 soak 判据两次独立窗口：批次50 emulator-5590 / release APK 1bf98b00 与 "
                  "批次51 emulator-5592 / release APK b6fbd877，各 7200s / 115 轮；两窗 "
                  "crash_delta=0、dropbox_delta=0、monkey 非零 0、pid 未重启",
        "evidence": f"cmd:{P51}/acceptance/f058-soak-repeat-audit.log; "
                    f"device:docs/acceptance/evidence/2026-11-09-batch-50/device/soak-b50.device.json; "
                    f"device:{ACC}/device/soak-b51.device.json; "
                    f"file:docs/research/evidence/2026-11-09-batch-50/device/37-soak-console.log; "
                    f"file:{P51}/device/37-soak-console.log; "
                    f"review:docs/acceptance/verdicts/rows/F-058.json",
        "verdict": "PASS",
    },
    "F-063": {"append": [f"device:{ACC}/device/soak-b51.device.json",
                          f"file:{P51}/device/37-soak-console.log"]},
    "H-024": {"append": [f"file:{P51}/release/external-blockers.md"]},
    "H-036": {"append": [f"file:{P51}/release/external-blockers.md"]},
    "H-053": {"append": [f"file:{P51}/release/external-blockers.md"]},
    "J-001": {
        "method_append": "；批次51 identity --require-clean 仍 exit 1（dirty_paths 5489）",
        "append": [f"file:{P51}/release/identity-require-clean-strictOffline.json",
                   f"file:{P51}/release/clean-clone-build.log"],
    },
    "J-002": {
        "method": "cmd: python -I tools/ci/j_release_verify.py identity --root . "
                  "--apk app/build/outputs/apk/localFirst/release/app-localFirst-release.apk "
                  "--aab app/build/outputs/bundle/localFirstRelease/app-localFirst-release.aab "
                  "--mapping app/build/outputs/mapping/localFirstRelease/mapping.txt "
                  "--gradle app/build.gradle.kts "
                  "--metadata app/build/outputs/apk/localFirst/release/output-metadata.json "
                  "--require-head（批次51 实跑）",
        "evidence": f"cmd:{P51}/acceptance/j002-identity-require-head.log; "
                    f"file:{ACC}/j002-identity-require-head.json; "
                    f"test:{P51}/acceptance/j-release-verifier-b51.xml; "
                    f"review:docs/acceptance/verdicts/rows/J-002.json",
        "verdict": "PASS",
    },
    "J-019": {"append": [f"file:{P51}/release/validateReleaseSigning-with-env.log",
                          f"file:{P51}/release/signing-localFirst.json",
                          f"file:{P51}/release/external-blockers.md"]},
    "J-020": {"append": [f"file:{P51}/release/signing-localFirst.json",
                          f"file:{P51}/release/keytool-keystore.txt",
                          f"file:{P51}/release/external-blockers.md"]},
    "J-021": {"append": [f"file:{P51}/release/apksigner-localFirst.txt",
                          f"file:{P51}/release/keytool-keystore.txt",
                          f"file:{P51}/release/external-blockers.md"]},
    "J-043": {"append": [f"file:{P51}/release/device-probe.txt",
                          f"file:{P51}/release/external-blockers.md"]},
    "J-048": {
        "method": "cmd: python -I tools/ci/j_release_verify.py version + 跨批次产物读数"
                  "（批次50 APK/AAB 101160 → 批次51 APK/AAB 101177，commit_count 1160→1177）",
        "evidence": f"cmd:{P51}/acceptance/j048-version-monotonic-audit.log; "
                    f"file:docs/research/evidence/2026-11-09-batch-50/release/aapt2-badging-localFirst.txt; "
                    f"file:{P51}/release/version-localFirst.json; "
                    f"file:{P51}/release/aab-manifest-localFirst.json; "
                    f"test:{P51}/acceptance/j-release-verifier-b51.xml; "
                    f"review:docs/acceptance/verdicts/rows/J-048.json",
        "verdict": "PASS",
    },
    "J-052": {
        "method": "cmd: 历史 AAB（批次50，101160）与现 APK（批次51，101177）逐字段读数",
        "evidence": f"cmd:{P51}/acceptance/j052-version-comparable-audit.log; "
                    f"file:docs/research/evidence/2026-11-09-batch-50/release/aab-manifest-localFirst.json; "
                    f"file:{P51}/release/aab-manifest-localFirst.json; "
                    f"file:{P51}/release/version-localFirst.json; "
                    f"test:{P51}/acceptance/j-release-verifier-b51.xml; "
                    f"review:docs/acceptance/verdicts/rows/J-052.json",
        "verdict": "PASS",
    },
    "FIX-061": {
        "method_append": "；批次51 干净 clone 构建在 :core:export:compileReleaseKotlin 失败："
                         "AI_GENERATED_DOCUMENT_NOTICE 定义文件在 HEAD 未跟踪，干净树不可编译",
        "append": [f"file:{P51}/release/clean-clone-build.log",
                   f"file:{P51}/release/external-blockers.md"],
    },
    "FIX-064": {"append": [f"file:{P51}/release/clean-clone-baseline.txt",
                           f"file:{P51}/release/clean-clone-build.log",
                           f"file:{P51}/release/external-blockers.md"]},
    "GATE-091": {
        "check": "切分门 exit 1：countExactRate 19/24=0.7917 低于 0.9（批次51 合并门；"
                 "acceptance 单切片 7/8=0.875）",
        "method": "cmd: python -I tools/split_golden/check_split_gate.py（批次51 acceptance 只跑一次；"
                  "合并 dev 16 + acceptance 8 = 24 页）",
        "evidence": f"file:{P51}/split/acceptance/gate/gate-report-b51-primary.json; "
                    f"file:{P51}/split/acceptance/gate/gate-evidence-b51-primary.md; "
                    f"file:{P51}/split/acceptance/gate/merged-24-primary.json; "
                    f"file:{P51}/split/acceptance/gate/gate-primary.stdout.txt; "
                    f"review:docs/acceptance/verdicts/rows/GATE-091.json",
    },
    "GATE-092": {
        "check": "fragmentRate 4/78=0.0513 >0.05 且硬红线 fragmentedQuestions=4≠0，"
                 "门整体判红（批次51）",
        "method": "cmd: python -I tools/split_golden/check_split_gate.py（批次51 合并门）",
        "evidence": f"file:{P51}/split/acceptance/gate/gate-report-b51-primary.json; "
                    f"file:{P51}/split/acceptance/gate/gate-evidence-b51-primary.md; "
                    f"file:{P51}/split/acceptance/gate/merged-24-primary.json; "
                    f"file:{P51}/split/acceptance/gate/gate-primary.stdout.txt; "
                    f"review:docs/acceptance/verdicts/rows/GATE-092.json",
    },
    "GATE-099": {
        "check": "AUD-08 批次51 维持 FAIL，未转绿（① 19/24=0.7917；③ 4/78=0.0513；fragmented=4）",
        "method": "cmd: python -I tools/split_golden/check_split_gate.py（批次51 合并门）",
        "evidence": f"file:{P51}/split/acceptance/gate/gate-report-b51-primary.json; "
                    f"file:{P51}/split/acceptance/gate/gate-evidence-b51-primary.md; "
                    f"file:{P51}/split/acceptance/gate/merged-24-primary.json; "
                    f"file:{P51}/split/acceptance/gate/gate-primary.stdout.txt; "
                    f"review:docs/acceptance/verdicts/rows/GATE-099.json",
    },
    "GATE-096": {"append": [f"file:{P51}/split/acceptance/README.md"]},
    "GATE-097": {"append": [f"file:{P51}/3d-audit/REPORT.md",
                            f"file:{P51}/mva-gate/README.md"]},
    "GATE-098": {"append": [f"file:{P51}/split/acceptance/README.md"]},
    "GATE-100": {
        "method": "BLOCKED: 3D FAIL / MVA FAIL / AUD-08 FAIL + 人工金标缺失 + 外部五项"
                  "（Play 账号 / 生产钥 / 备案号 / 公网隐私 URL / 图像 provider）；"
                  "出口 docs/acceptance/runbook.md",
        "append": [f"file:{P51}/3d-audit/REPORT.md",
                   f"file:{P51}/mva-gate/README.md",
                   f"file:{P51}/split/acceptance/gate/gate-report-b51-primary.json",
                   f"file:{P51}/release/external-blockers.md"],
    },
}


def main() -> int:
    text = LEDGER.read_text(encoding="utf-8")
    lines = text.splitlines()
    out: list[str] = []
    changed: dict[str, tuple[str, str]] = {}
    seen: set[str] = set()
    for line in lines:
        if not line.startswith("| "):
            out.append(line)
            continue
        parts = line.split("|")
        if len(parts) != 8:
            out.append(line)
            continue
        row_id = parts[1].strip()
        if row_id not in UPDATES:
            out.append(line)
            continue
        seen.add(row_id)
        cells = [parts[1].strip(), parts[2].strip(), parts[3].strip(),
                 parts[4].strip(), parts[5].strip(), parts[6].strip()]
        spec = UPDATES[row_id]
        if "check" in spec:
            cells[2] = spec["check"]
        if "method" in spec:
            cells[3] = spec["method"]
        if "method_append" in spec:
            cells[3] = cells[3] + spec["method_append"]
        if "evidence" in spec:
            cells[4] = spec["evidence"]
        if "append" in spec:
            cells[4] = "; ".join([cells[4]] + spec["append"]) if cells[4] not in ("", "-") \
                else "; ".join(spec["append"])
        old_verdict = cells[5]
        if "verdict" in spec:
            cells[5] = spec["verdict"]
        changed[row_id] = (old_verdict, cells[5])
        out.append("| " + " | ".join(cells) + " |")

    missing = sorted(set(UPDATES) - seen)
    if missing:
        print(f"ERROR: rows not found: {missing}", file=sys.stderr)
        return 1
    if len(out) != len(lines):
        print(f"ERROR: line count changed {len(lines)} -> {len(out)}", file=sys.stderr)
        return 1
    LEDGER.write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
    for row_id in sorted(changed):
        old, new = changed[row_id]
        mark = " *" if old != new else ""
        print(f"{row_id}: {old} -> {new}{mark}")
    print(f"rows updated: {len(changed)}; EXIT=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
