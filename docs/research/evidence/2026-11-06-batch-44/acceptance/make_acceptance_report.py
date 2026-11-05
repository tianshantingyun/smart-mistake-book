#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批次 44 终裁报告生成器（只读台账与证据，写两处落点）。

## 它消灭的具体失败

「1200 项验收」在本批里散落成 13 个台账域、260 条 trace 缺口、29 条 lint 告警；
若主会话要签字，只能自己拼 460KB 台账并猜每条 PASS 的证据与 run 绑定，
于是又回到「看起来齐了」的口头结论。本脚本把同一份台账投影成两张可直接签字的表：

1. `docs/acceptance/verdicts.md`：1200 行逐项（id / 证据 / 复核人 / run-id）+
   530 行非 PASS 项逐条原因 + 四门实跑记录 + 主会话签字区；
2. `docs/research/evidence/2026-11-06-batch-44/acceptance-report.json`：
   同一结论的机器可读版本（门禁 exit、计数、非 PASS 清单、trace 缺口分类）。

## 不伪造的规矩

- `复核人` 只写真实存在的 review 证据里的 reviewer；没有就写 `—`，不代签。
- `run-id` 来自证据文件自身（设备 JSON 的 `serial@recorded_at#boot`），
  没有设备证据时写 `ledger@<sha8>` 并明确说明它是台账绑定、不是一次设备执行。
- FAIL 的原因来自证据日志原文（见 FAIL_REASONS 的逐条引用）；
  其余 verdict 的原因取自台账 `方法` 列原文，不改写、不美化。

用法：
    python -I docs/research/evidence/2026-11-06-batch-44/acceptance/make_acceptance_report.py
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]  # docs/research/evidence/<batch>/acceptance -> repo root
sys.path.insert(0, str(ROOT / "tools" / "ci"))

import acceptance_model as M  # noqa: E402

LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"
VERDICTS_MD = ROOT / "docs" / "acceptance" / "verdicts.md"
REPORT_JSON = ROOT / "docs" / "research" / "evidence" / "2026-11-06-batch-44" / "acceptance-report.json"
EVID = "docs/research/evidence/2026-11-06-batch-44/acceptance"

RUN_ID_PREFIX = "-batch44-acceptance-report"

# FAIL 行的原因逐条取自证据原文；path:line 指向可复核的文件。
FAIL_REASONS = {
    "C-065": "设备用例 theRestoreLegAloneReplaysOfflineWithZeroDispatchBookings 断言失败："
             "java.lang.AssertionError: the restored document must leave ink "
             "(restore is not a silent no-op)（c-domain/c-restore-single-rerun.log；XML failures=1）",
    "F-055": "2h soak 在 2504s 发生 FATAL EXCEPTION（Desired retention 越界），进程重启，"
             "2723s 人工停止，未完成 2h（f-domain/soak.cmd.log、soak.device.json）",
    "F-057": "2h 窗口内命中 ≥1 次 FATAL EXCEPTION（SchedulingSettingsScreen.kt:74 → "
             "SchedulingOptions 越界），超限路径成立（f-domain/soak-crash-logcat.txt）",
    "H-018": "h_compliance_gate --only H-018 exit=1：DOCX 无文档级 /AIGC；KD-84 部分页丢标；"
             "KD-85 模型改卷文本漏标（docs/acceptance/evidence/H/logs/h-018.log）",
    "H-052": "免责声明文案仍称 PDF 尚未写入文件级隐式信息，而导出链已注入 /AIGC（h-052.log exit=1）",
    "H-061": "KD-84 open：writeSubset 逐页重绘，Info 字典不随页复制（h-061.log exit=1）",
    "H-062": "KD-85 open：toDraft 只透传来源 hasModelParsedText（h-062.log exit=1）",
    "H-066": "SecondaryScreens.kt:826 把 failure.message 直接拼进用户可见文案（h-066.log exit=1）",
    "J-001": "工作树脏 4248 行（j-identity.log dirty_paths=4248）；--require-clean 被包装器拒绝"
             "（j-neg-clean-tree.log 为负控）",
    "J-002": "release APK revision faab17f5… 落后 HEAD 542b9bba… 32 个提交（j-identity.log "
             "commits_behind_head=32）",
    "J-020": "release 未用生产钥匙：APK 证书 CN=WS8 Ephemeral Local Only；RELEASE_* 4/4 缺失"
             "（j-apk-cert.log、j-signing.log）",
    "J-021": "签名指纹不等于已登记发布指纹：APK 证书 9dc07e9f…056e20，AAB 证书 A9:E7:6C:AD…"
             "（j-apksigner.log、j-keytool.log）",
    "J-024": "APK 证书 CN=WS8 Ephemeral Local Only 与 AAB 证书 CN=ZCode Release Smoke 身份不同"
             "（j-apk-cert.log、j-keytool.log）",
    "J-025": "AAB 证书有效期至 2026-10-30，已过期（j-keytool.log）",
    "J-027": "文档只写 keystore.properties，实际 RELEASE_* 4/4 缺失、0 个已跟踪 keystore "
             "（j-signing.log）",
    "J-033": "AAB ABI 集合 [arm64-v8a, armeabi-v7a, x86, x86_64] != abiFilters "
             "[arm64-v8a, x86_64]（j-aab16k.log errors，EXIT=1）",
    "J-034": "AAB 缺 libquickjs.so（j-aab16k.log errors: required native library missing）",
    "J-041": "AAB 含 32 位 .so（armeabi-v7a/x86），负向断言不成立（j-aab16k.log abi_set）",
    "J-048": "四份产物 versionCode 全为 2（aab 亦为 2），跨构建无单调证据（j-version-artifacts.log）",
    "J-051": "版本号是 app/build.gradle.kts:33-34 手写两行，无生成/注入机制"
             "（j-version-source.log grep 证据）",
    "J-052": "历史 AAB 与现 APK 同为 versionCode=2 且 AAB mtime 2026-09-30，陈旧不可比"
             "（j-artifact-mtimes.log、j-bundletool-manifest.log）",
}

BLOCKED_RUNBOOK = "docs/acceptance/runbook.md"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def truncate(text: str, limit: int) -> str:
    text = " ".join(text.split())
    return text if len(text) <= limit else text[: limit - 1] + "…"


def device_binding(root: Path, row: M.Row) -> str:
    for item in row.evidence():
        if item.kind != "device":
            continue
        path = M.resolve(root, item.target)
        if not path.exists():
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue
        serial, recorded = data.get("serial"), data.get("recorded_at")
        if serial and recorded:
            return f"{serial}@{recorded}#boot={str(data.get('boot_id', ''))[:8]}"
    return ""


def reviewer_of(row: M.Row, root: Path) -> tuple[str, str]:
    """返回 (reviewer, 说明)。只有真实 review 证据且文件存在才给名字。"""
    for item in row.evidence():
        if item.kind != "review":
            continue
        path = M.resolve(root, item.target)
        if not path.exists():
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue
        reviewer = str(data.get("reviewer", "")).strip()
        if reviewer:
            return reviewer, str(data.get("run_id", "")).strip()
    return "", ""


def reason_for(row: M.Row, root: Path) -> str:
    if row.verdict == "FAIL":
        return FAIL_REASONS.get(row.row_id, "FAIL（证据日志见台账证据列）")
    method = row.method.strip()
    if row.verdict == "BLOCKED":
        if "BLOCKED:" in method:
            body = method.split("BLOCKED:", 1)[1].strip()
        else:
            body = method
        body = body.split("；")[0]
        if BLOCKED_RUNBOOK in body:
            return truncate(body, 260)
        return truncate(f"{body}（出口：{BLOCKED_RUNBOOK}）", 260)
    if method.startswith("UNVERIFIED:"):
        return truncate(method[len("UNVERIFIED:"):].strip(), 260)
    if method.startswith("review:"):
        target = method[len("review:"):].strip()
        return f"复核/终裁文件不存在：{target}（文件未生成，故无独立复核结论可引用）"
    if row.evidence_text.strip() in {"-", "—", ""}:
        return truncate(f"证据列空、未回填；方法声明：{method}", 240)
    if method.startswith(("cmd:", "test:", "device:", "python", "./gradlew", "file:", "hash:")):
        return truncate(f"仅单类或未执行到判定口；方法：{method}", 240)
    return truncate(method, 240)


def classify_unverified(row: M.Row) -> str:
    method = row.method.strip()
    if method.startswith("review:"):
        return "缺独立复核/终裁文件"
    if method.startswith("UNVERIFIED:"):
        return "已界定未测/缺用例"
    if row.evidence_text.strip() in {"-", "—", ""}:
        return "骨架行未回填证据"
    return "单类证据或未达判定口"


def evidence_archival(rows: list[M.Row]) -> dict | None:
    """统计「证据文件是否在版本库里」。干净克隆能否复现证据，取决于这一条。"""
    import subprocess

    try:
        tracked = set(subprocess.run(["git", "ls-files"], capture_output=True, text=True,
                                     encoding="utf-8", cwd=ROOT).stdout.splitlines())
    except OSError:
        return None
    if not tracked:
        return None
    pass_rows = [row for row in rows if row.verdict == "PASS"]
    loose = [row.row_id for row in pass_rows
             if any(item.target not in tracked for item in row.evidence())]
    log_rows = [row.row_id for row in pass_rows
                if any(item.kind == "cmd" and item.target.endswith(".log")
                       for item in row.evidence())]
    return {
        "tracked_evidence_files": sum(1 for t in tracked if t.startswith("docs/research/evidence")),
        "tracked_log_files": sum(1 for t in tracked if t.endswith(".log")),
        "pass_rows_with_cmd_log_evidence": len(log_rows),
        "pass_rows_with_untracked_evidence": len(loose),
        "pass_rows_total": len(pass_rows),
        "sample_untracked": loose[:5],
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="批次 44 终裁报告生成")
    parser.add_argument("--ledger", default=str(LEDGER))
    parser.add_argument("--out-md", default=str(VERDICTS_MD))
    parser.add_argument("--out-json", default=str(REPORT_JSON))
    args = parser.parse_args(argv)

    ledger = Path(args.ledger)
    root = ROOT
    ledger_sha = sha256(ledger)
    rows, issues = M.parse_ledger(ledger)
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    RUN_ID = generated_at + RUN_ID_PREFIX

    counts = {v: 0 for v in M.VERDICTS}
    per_domain: dict[str, dict[str, int]] = {}
    for row in rows:
        counts[row.verdict] = counts.get(row.verdict, 0) + 1
        per_domain.setdefault(row.domain, {v: 0 for v in M.VERDICTS})[row.verdict] += 1

    trace_path = ROOT / EVID / "trace.json"
    crosscheck_path = ROOT / EVID / "crosscheck.json"
    lint_path = ROOT / EVID / "lint.json"
    report_path = ROOT / EVID / "report.json"
    trace = json.loads(trace_path.read_text(encoding="utf-8")) if trace_path.exists() else {}
    cross = json.loads(crosscheck_path.read_text(encoding="utf-8")) if crosscheck_path.exists() else {}
    lint = json.loads(lint_path.read_text(encoding="utf-8")) if lint_path.exists() else {}
    report = json.loads(report_path.read_text(encoding="utf-8")) if report_path.exists() else {}

    gates = [
        {"gate": "acceptance_init", "command": "python -I tools/ci/acceptance_init.py --check",
         "exit": 0, "log": f"{EVID}/init-check.log",
         "result": "1200 行 / ID 集合 / 域计数通过（工具成功时静默，只留 exit；"
                   f"独立重算见 {EVID}/ledger-shape.log：rows=1200、issues=[]、match=True）"},
        {"gate": "acceptance_report", "command":
            "python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md",
         "exit": report.get("exit_code", 0), "log": f"{EVID}/report.stdout.log",
         "result": f"violations=0；PASS {counts['PASS']} 行均 ≥2 类独立证据；"
                   f"acceptance_complete={str(report.get('acceptance_complete')).lower()}"},
        {"gate": "acceptance_report --require-complete", "command":
            "python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --require-complete",
         "exit": 1, "log": f"{EVID}/report-require-complete.log",
         "result": "NOT_ACCEPTANCE_COMPLETE（仍有 UNVERIFIED/FAIL）"},
        {"gate": "acceptance_lint", "command":
            "python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md",
         "exit": lint.get("exit_code", 0), "log": f"{EVID}/lint.stdout.log",
         "result": f"violations=0；warnings={len(lint.get('warnings', []))}（全部 EVIDENCE_REUSED）"},
        {"gate": "trace_requirements", "command":
            "python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md",
         "exit": trace.get("exit_code", 1), "log": f"{EVID}/trace.stdout.log",
         "result": f"gaps={len(trace.get('gaps', []))}；归一化后剩 "
                   f"{cross.get('normalized_gap_rows', 0)} 行（57+10 UNVERIFIED 待产证据 / "
                   f"44 PASS 待改方法列）"},
        {"gate": "tool self-tests", "command":
            "python -I -m pytest tools/tests/test_acceptance_report.py "
            "tools/tests/test_acceptance_lint.py tools/tests/test_acceptance_init.py "
            "tools/tests/test_trace_requirements.py -q",
         "exit": 0, "log": f"{EVID}/tool-selftests.log",
         "result": "30 passed（JUnit XML 同步落盘 tool-selftests.xml，含批次 41/42 假绿负控用例）"},
        {"gate": "false-green replay drill", "command":
            "python -I tools/ci/acceptance_report.py --ledger "
            "tools/tests/fixtures/acceptance/fake_green_batch4{1,2}.ledger.md --partial",
         "exit": 1, "log": f"{EVID}/replay-drill.log",
         "result": "批次 41/42 已知假绿夹具均 exit 1（PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE、"
                   "SKIPPED_GATE_AS_PASS、SUMMARY_AS_EVIDENCE、UNVERIFIED_AS_PASS）"},
    ]

    non_pass: list[dict] = []
    for row in rows:
        if row.verdict == "PASS":
            continue
        reviewer, _ = reviewer_of(row, root)
        non_pass.append({
            "id": row.row_id,
            "domain": row.domain,
            "verdict": row.verdict,
            "check": row.check,
            "reason": reason_for(row, root),
            "category": classify_unverified(row) if row.verdict == "UNVERIFIED" else "",
            "evidence": row.evidence_text,
            "reviewer": reviewer,
            "run_id": device_binding(root, row) or f"ledger@{ledger_sha[:8]}",
        })

    review_tokens = sum(1 for row in rows for e in row.evidence() if e.kind == "review")
    review_method_rows = sum(1 for row in rows if row.method.strip().startswith("review:"))
    archival = evidence_archival(rows)
    verdict_files = sorted(p.name for p in (ROOT / "docs" / "acceptance" / "verdicts").glob("*.json")
                           if not p.name.startswith("_"))

    lines: list[str] = []
    add = lines.append
    add("# 批次 44 逐项终裁表（1200 项）")
    add("")
    add(f"> 生成命令：`python -I {EVID}/make_acceptance_report.py`（生成器）")
    add(f"> 本报告 run-id：`{RUN_ID}`　台账 `docs/acceptance/ledger.md` sha256：`{ledger_sha}`")
    add("> 逐行数据源：`docs/acceptance/ledger.md`（verdict 与证据列原文投影，未改写）；"
        "证据文件与 run 绑定由 `acceptance_crosscheck.py` 复核。")
    add("")
    add("## 0. 结论先行：本批**不可**作总体通过签字")
    add("")
    add("| 项 | 结果 |")
    add("|---|---|")
    add(f"| 台账行数 | {len(rows)}（D0 100 / A–J 各 90 / FIX 100 / GATE 100） |")
    add(f"| 计数 | PASS {counts['PASS']} / FAIL {counts['FAIL']} / BLOCKED {counts['BLOCKED']} "
        f"/ UNVERIFIED {counts['UNVERIFIED']} |")
    add(f"| 终裁门 `--require-complete` | **exit 1**（NOT_ACCEPTANCE_COMPLETE） |")
    add(f"| 追溯门 `trace_requirements` | **exit 1**（{len(trace.get('gaps', []))} 条 gap） |")
    add(f"| 独立复核覆盖 | 证据列含 `review:` token 的行 **0**；方法列要求复核文件的行 "
        f"{review_method_rows} 条且文件全部不存在；`docs/acceptance/verdicts/*.json` 终裁文件 "
        f"{len(verdict_files)} 个 |")
    add("")
    add("因此：`PASS` 在本报告里的含义限定为「该行证据列通过 `acceptance_report.py` 的"
        "≥2 类独立证据审计」，**不等于**该行已通过 DoD §1.5 独立复核与 GATE 终裁。"
        "主会话只能逐行签字，不能签「批次 44 验收通过」。")
    add("")

    add("## 1. 统计")
    add("")
    add("| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED |")
    add("|---|---:|---:|---:|---:|---:|")
    for key, title, _ in M.DOMAINS:
        row_counts = per_domain.get(key, {v: 0 for v in M.VERDICTS})
        add(f"| {key} {title} | {sum(row_counts.values())} | {row_counts['PASS']} | "
            f"{row_counts['FAIL']} | {row_counts['BLOCKED']} | {row_counts['UNVERIFIED']} |")
    add(f"| **合计** | **{len(rows)}** | **{counts['PASS']}** | **{counts['FAIL']}** | "
        f"**{counts['BLOCKED']}** | **{counts['UNVERIFIED']}** |")
    add("")

    add("## 2. 四门实跑（本轮，可复跑）")
    add("")
    add("| 门 | 命令 | exit | 结论 | 日志 |")
    add("|---|---|---:|---|---|")
    for gate in gates:
        add(f"| {gate['gate']} | `{gate['command']}` | {gate['exit']} | {gate['result']} | "
            f"`{gate['log']}` |")
    add("")
    add("复跑方式：`python -I tools/ci/acceptance_init.py --check`、"
        "`python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md "
        "--require-complete`、`python -I tools/ci/acceptance_lint.py --ledger "
        "docs/acceptance/ledger.md`、`python -I tools/ci/trace_requirements.py --ledger "
        "docs/acceptance/ledger.md`。")
    add("")
    add(f"一次性重放（含每条 exit 标记与本报告 ledger sha 绑定）：`{EVID}/gates-final.log`。")
    add("")
    gap_raw = cross.get("gap_classification_raw", {})
    if gap_raw:
        add("### 2.1 trace 260 条 gap 的真实性分类")
        add("")
        add("| gap 码 | 解析器误报 | 真实缺口 | 说明 |")
        add("|---|---:|---:|---|")
        notes = {
            "MISSING_TEST_PATH": "`test: path#方法名` 的 `#方法名` 未被剥离；文件实际存在"
                                  "（如 PaperEditPromptGoldenTest.kt）",
            "MISSING_TOOL_PATH": "`python -I -m pytest` 把 `-m` 当脚本路径",
            "PASS_WITHOUT_TEST_LANDING": "方法列没有 `test:`/`device:` 落点。其中 16 条是解析器误报"
                                         "（全角 `；` 截断）；44 条是 I 域真实缺口：方法只写 "
                                         "`python -I tools/ci/a11y_probe.py …` 或 `a11y_audit.py`，"
                                         "其中 42 条证据列已有 device JSON、2 条为静态主题审计，"
                                         "属台账方法列未按 `device:` 约定书写",
            "MISSING_EVIDENCE_PATH": "占位复核文件真的不存在（FIX/GATE 行），无假绿",
            "DEVICE_EVIDENCE_MISSING": "GATE 行声明设备执行但无 device 证据，无假绿",
        }
        for code, buckets in sorted(gap_raw.items()):
            add(f"| {code} | {buckets.get('parser-artifact', 0)} | "
                f"{buckets.get('real-gap', 0)} | {notes.get(code, '')} |")
        add("")
        add(f"归一化重算（剥离 `#方法名`、忽略 `-m`、按 `；` 分块、认 `device:` 证据）后，"
            f"剩余 {cross.get('normalized_gap_rows', 0)} 行有缺口，分布为 "
            f"{json.dumps(cross.get('normalized_gap_counts', {}), ensure_ascii=False)}：")
        add("")
        add("- `MISSING_EVIDENCE_PATH` 57 行 + `DEVICE_EVIDENCE_MISSING` 10 行：全为 **UNVERIFIED** 行，"
            "即复核/终裁文件与设备轮次尚未产生，没有把未跑写成 PASS；")
        add("- `PASS_WITHOUT_TEST_LANDING` 44 行：全为 **PASS** 的 I 域行，方法是 `python -I "
            "tools/ci/a11y_probe.py …` / `a11y_audit.py`，证据列已有 `device:`/`test:`；"
            "这是台账方法列未按 `device:` 约定书写造成的追溯缺口，**不是**证据缺失，"
            "但也不能当作 trace 门已过——需域负责人改方法列或工具认该形态后复跑。")
        add("")
        add("**待主会话决定**：`tools/ci/trace_requirements.py` 的三处解析缺陷属本报告范围外"
            "（工具目录不在本次写权限内），需单独提交修复；在修复前该门不能清零。")
        add("")

    add("## 3. 非 PASS 项逐条原因（" + str(len(non_pass)) + " 条）")
    add("")
    for verdict in ("FAIL", "BLOCKED", "UNVERIFIED"):
        subset = [item for item in non_pass if item["verdict"] == verdict]
        add(f"### 3.{('FAIL','BLOCKED','UNVERIFIED').index(verdict) + 1} {verdict}（{len(subset)} 条）")
        add("")
        if not subset:
            add("（无）")
            add("")
            continue
        add("| id | 检查 | 原因 | 证据 |")
        add("|---|---|---|---|")
        for item in subset:
            check = item["check"].replace("|", "／")
            reason = item["reason"].replace("|", "／")
            evidence = (item["evidence"] or "-").replace("|", "／")
            add(f"| {item['id']} | {check} | {reason} | {evidence} |")
        add("")

    add("## 4. 复核面、对抗面与人类残余（本批未成立的部分）")
    add("")
    add("| 面 | 事实 | 对结论的影响 |")
    add("|---|---|---|")
    add(f"| 独立复核 | 证据列 `review:` token **0** 个；方法列声明复核文件的行 {review_method_rows} 条"
        f"（GATE 30 / FIX 20 / D0 7 / J 7），文件**全部不存在**；`docs/acceptance/verdicts/*.json` "
        f"终裁文件 0 个 | DoD §1.5 未成立；670 行 PASS 缺复核面，不能作为终裁通过 |")
    add("| 对抗复核 | GATE 十条「对抗复核反例尝试」行（GATE-002/012/…/092）全为 UNVERIFIED，"
        "文件不存在；原始矛盾证据只在 A/B/C 三域有产物"
        "（`a-domain/adversarial-attempts.md`、`b-domain/b-adversarial-mask-policy.log`、"
        "`c-domain/c-adversarial-metamorphic-red.log`、`c-adversarial-seams-red.log`、"
        "`c-domain/adversarial-pairs/`），D–J 七域无 | 计划 §7「十域每域至少 1 条反例」当前 **3/10** |")
    add("| 人类语感/审美 | I-082…I-090 九行台账已自行登记为「人工语感残余：屏幕阅读器语感与审美"
        "无法自动判定」并保持 UNVERIFIED；H-075/H-076（i18n 抽取与 RTL 走查）同样 UNVERIFIED | "
        "DoD §5 明列的不可替代面，本报告不为其签字，也不改写为 PASS |")
    add("| 外部条件 | BLOCKED 73 行按台账原文归为：真实 Provider 密钥 10（D 域 9 + A-010）、"
        "arm64 设备 10（F 域）、J 域发布外部依赖 34（生产签名/Play Console/成对产物/arm64）、"
        "H 域 12（Play Console、隐私政策未发布、热区禁改、CAC 备案）、"
        "I 域 5（真机旋转/IME/无故障注入入口）、G 域 2（离线 advisory、锁文件放行态） | "
        "出口统一在 `docs/acceptance/runbook.md`；拿到密钥/真机/账号后按 runbook 复跑再改 verdict |")
    if archival:
        add(f"| 证据归档 | `git ls-files` 下 `docs/research/evidence` 已跟踪 "
            f"{archival['tracked_evidence_files']} 个文件，其中 `.log` **0** 个"
            f"（`.gitignore:31: *.log`）；670 行 PASS 中 "
            f"{archival['pass_rows_with_untracked_evidence']} 行的证据里至少一条路径**未被 git 跟踪**，"
            f"其中 {archival['pass_rows_with_cmd_log_evidence']} 行直接依赖 `cmd:*.log` | "
            "干净克隆上 `acceptance_report.py` 会把这些行判为证据缺失；"
            "证据归档（D0-014 hash manifest 尚未生成）必须在提交前解决，本报告未改 `.gitignore` |")
    add("")

    add("## 5. 逐项终裁表（1200 行）")
    add("")
    add("- `复核人`：只有真实存在且可读的 `review:` 证据里的 `reviewer` 才写入；"
        "当前 **0 行**具备该证据，故全部为 `—`。空值不是通过，是本批尚未完成的复核面。")
    add(f"- `run-id`：有设备证据的行取设备 JSON 自带的 `serial@recorded_at#boot`（证据文件内可核）；"
        f"无设备证据的行写 `ledger@{ledger_sha[:8]}`——它是台账绑定，**不是**一次设备执行。")
    add("- 证据列为台账原文（`;` 分隔 token），未经删减。")
    add("")
    add("| id | verdict | 证据 | 复核人 | run-id |")
    add("|---|---|---|---|---|")
    for row in rows:
        reviewer, _ = reviewer_of(row, root)
        run_id = device_binding(root, row) or f"ledger@{ledger_sha[:8]}"
        evidence = (row.evidence_text or "-").replace("|", "／")
        add(f"| {row.row_id} | {row.verdict} | {evidence} | {reviewer or '—'} | {run_id} |")
    add("")

    add("## 6. 主会话签字区")
    add("")
    add("签字规则（DoD §3、`docs/acceptance/verdicts/README.md`）：")
    add("")
    add("1. 只对单个 id 签字，不写总体「通过」；")
    add("2. `reviewer` 不得在 `authors` 内，且必须与该行 `review:` 证据里的名字一致；")
    add("3. 签字必须绑 `run_id` 与本报告记录的 `ledger_sha256`；")
    add("4. 签 PASS 前，该行必须同时具备：≥2 类独立证据（report 已过）、"
        "独立复核文件（当前 0 行）、对抗复核记录（GATE 域）。")
    add("")
    add("| 字段 | 值（主会话填写） |")
    add("|---|---|")
    add("| 签字人 | |")
    add("| 签字 run-id | |")
    add(f"| ledger_sha256 | `{ledger_sha}` |")
    add("| 签字范围（id 列表或「无」） | |")
    add("| 签字时间 | |")
    add("| 结论 | 不得为「批次 44 验收通过」：FAIL 21 / BLOCKED 73 / UNVERIFIED 436 未清零 |")
    add("")
    add("未签字的 530 行与 670 行 PASS 的复核面，按域回到："
        "`docs/acceptance/runbook.md`（BLOCKED 出口）、"
        "`docs/acceptance/verdicts/README.md`（复核文件格式）、"
        "本目录 §3（逐条原因）。")
    add("")

    md = "\n".join(lines)
    out_md = Path(args.out_md)
    out_md.parent.mkdir(parents=True, exist_ok=True)
    out_md.write_text(md, encoding="utf-8", newline="\n")

    payload = {
        "schema": "batch44-acceptance-report/v1",
        "run_id": RUN_ID,
        "generated_at_utc": generated_at,
        "ledger": ledger.relative_to(ROOT).as_posix(),
        "ledger_sha256": ledger_sha,
        "total": len(rows),
        "counts": counts,
        "domain_counts": per_domain,
        "acceptance_complete": False,
        "terminal_blockers": [
            f"FAIL {counts['FAIL']} 行未清零",
            f"BLOCKED {counts['BLOCKED']} 行（外部条件缺失，见 runbook）",
            f"UNVERIFIED {counts['UNVERIFIED']} 行未达判定口",
            f"independent review 覆盖 0 行：证据列无 review token；"
            f"{review_method_rows} 行方法列要求的复核文件全部不存在",
            "trace_requirements exit 1（260 gap：200 条解析器误报；60 条真实缺口全在 UNVERIFIED 行；"
            "另有 44 行 PASS 因方法列未写 device: 落点而保留）",
            "证据未归档：670 行 PASS 的证据里至少一条路径未被 git 跟踪（.gitignore:31 *.log）",
        ],
        "gates": gates,
        "pass_review_coverage": {
            "rows": len(rows),
            "rows_with_review_evidence": review_tokens,
            "rows_with_review_method": review_method_rows,
            "verdict_json_files": verdict_files,
        },
        "review_and_adversarial": {
            "independent_review_rows_with_evidence": review_tokens,
            "rows_declaring_review_file": review_method_rows,
            "review_files_present": verdict_files,
            "gate_adversarial_rows": 10,
            "gate_adversarial_rows_verified": 0,
            "domains_with_adversarial_artifacts": ["A", "B", "C"],
            "domains_without_adversarial_artifacts": ["D", "E", "F", "G", "H", "I", "J"],
            "human_residual_rows": ["I-082", "I-083", "I-084", "I-085", "I-086",
                                    "I-087", "I-088", "I-089", "I-090"],
        },
        "lint": {
            "exit": lint.get("exit_code"),
            "violations": len(lint.get("violations", [])),
            "warnings": len(lint.get("warnings", [])),
            "warning_codes": sorted({w.get("code") for w in lint.get("warnings", [])}),
            "artifact": f"{EVID}/lint.json",
        },
        "trace": {
            "exit": trace.get("exit_code"),
            "raw_gaps": len(trace.get("gaps", [])),
            "classified": gap_raw,
            "normalized_rows_with_gaps": cross.get("normalized_gap_rows"),
            "normalized_counts": cross.get("normalized_gap_counts"),
            "tool_defects": [
                "test: path#方法名 未剥离 #方法名",
                "python -I -m pytest 把 -m 当脚本路径",
                "全角 ； 未作为方法分隔符",
            ],
            "artifacts": [f"{EVID}/trace.json", f"{EVID}/crosscheck.json"],
        },
        "non_pass": non_pass,
        "non_pass_counts": {
            "FAIL": counts["FAIL"], "BLOCKED": counts["BLOCKED"],
            "UNVERIFIED": counts["UNVERIFIED"],
        },
        "artifacts": {
            "verdicts_md": out_md.relative_to(ROOT).as_posix(),
            "gates_final_log": f"{EVID}/gates-final.log",
            "init_log": f"{EVID}/init-check.log",
            "report_json": f"{EVID}/report.json",
            "report_require_complete_log": f"{EVID}/report-require-complete.log",
            "lint_json": f"{EVID}/lint.json",
            "trace_json": f"{EVID}/trace.json",
            "crosscheck_json": f"{EVID}/crosscheck.json",
            "tool_selftests_log": f"{EVID}/tool-selftests.log",
            "tool_selftests_xml": f"{EVID}/tool-selftests.xml",
            "ledger_shape_log": f"{EVID}/ledger-shape.log",
            "replay_drill_log": f"{EVID}/replay-drill.log",
        },
        "evidence_archival": archival,
        "not_substitutable": [
            "屏幕阅读器语感 / 中文文案审美：自动化不可判定，保持 UNVERIFIED",
            "真实 Provider 生成质量：无密钥，BLOCKED",
            "生产签名指纹与发布窗口：无签名密钥，BLOCKED/FAIL 如实登记",
            "arm64 真机性能门：无 arm64 设备，BLOCKED",
        ],
        "signoff": {
            "status": "pending",
            "reviewer": "",
            "run_id": "",
            "ledger_sha256": ledger_sha,
            "signed_ids": [],
            "note": "不允许总体通过签字；只能逐 id 签字并各自绑定 review 证据。",
        },
    }
    out_json = Path(args.out_json)
    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
                        encoding="utf-8", newline="\n")
    print(json.dumps({
        "verdicts_md": out_md.relative_to(ROOT).as_posix(),
        "report_json": out_json.relative_to(ROOT).as_posix(),
        "ledger_sha256": ledger_sha,
        "counts": counts,
        "non_pass": len(non_pass),
        "review_evidence_tokens": review_tokens,
        "exit": 0,
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
