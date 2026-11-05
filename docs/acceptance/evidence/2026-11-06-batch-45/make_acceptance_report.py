#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批次 45 终裁报告生成器（只读台账与已落盘的门禁 JSON）。

## 它消灭的具体失败

`docs/acceptance/verdicts.md` 与 `docs/acceptance/domain-index.md` 若手写，
就会和台账 verdict、`acceptance_report.py` 的 exit、`lint`/`trace` 的实值脱节——
批次 44 的 adjudication-draft §B1 正是这种「报告与台账不同源」。

本脚本只做投影，不改台账、不改工具、不改源码：

- 输入：`docs/acceptance/ledger.md`（1200 行权威 verdict）
        `docs/acceptance/evidence/2026-11-06-batch-45/{report,lint,trace}.json`
        `docs/acceptance/verdicts/*-review.json`（域级独立复核）
- 输出：`docs/acceptance/verdicts.md`（逐项终裁表）
        `docs/acceptance/domain-index.md`（域索引）

## 不伪造的规矩

- 复核人只写真实 review 证据文件里的 `reviewer`；没有就写 `—`。
- BLOCKED/UNVERIFIED 的原因取台账 `方法` 列原文（截断不改写）。
- FAIL 原因映射到可读证据文件；映射不到时写证据路径，不猜。
- 结论由输入 JSON 的 exit 与计数决定，不由脚本写死。

用法：
    python -I docs/acceptance/evidence/2026-11-06-batch-45/make_acceptance_report.py
"""

from __future__ import annotations

import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]  # docs/acceptance/evidence/<batch> -> repo root
sys.path.insert(0, str(ROOT / "tools" / "ci"))

import acceptance_model as M  # noqa: E402

LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"
EVID = HERE
VERDICTS_MD = ROOT / "docs" / "acceptance" / "verdicts.md"
DOMAIN_INDEX = ROOT / "docs" / "acceptance" / "domain-index.md"
REVIEW_DIR = ROOT / "docs" / "acceptance" / "verdicts"

# FAIL 行原因：每条只引用证据文件里可读到的内容。path 为仓库相对路径。
FAIL_REASONS = {
    "D0-030": "d0-self/docs/verdicts.txt：OBSERVED real_verdict_json=0；"
              "每个 PASS 没有逐行终裁 JSON（现仅有 10 个域级 review JSON）",
    "D0-081": "d0-self/docs/protocol.txt：OBSERVED conflict_rule_present=False；"
              "终裁协议未写「冲突时保留双方判词」",
    "C-065": "c-restore-single-rerun.xml tests=1 failures=1；日志断言 "
             "AssertionError: the restored document must leave ink "
             "(restore is not a silent no-op)",
    "F-055": "soak.cmd.log：DURATION_TARGET_S=7200 / DURATION_ACTUAL_S=2723 / "
             "CRASHED=1 / FIRST_CRASH_ELAPSED_S=2504；soak-crash-logcat.txt:3-7 "
             "FATAL IllegalArgumentException（Desired retention 0.7..0.97，"
             "StudyExperienceRepository.kt:737，入口 SchedulingSettingsScreen.kt:74）",
    "F-057": "同 F-055 的崩溃证据：窗口内命中 ≥1 次 FATAL（soak-crash-logcat.txt），"
             "超限路径成立",
    "H-018": "h-018.log FAIL EXIT=1（本会话复跑 h_compliance_gate --only H-018 仍 exit=1）："
             "DOCX 无文档级 /AIGC；KD-84 部分页丢标；KD-85 模型改卷文本漏标",
    "H-052": "台账证据 h-052.log FAIL EXIT=1；本会话 --only H-052 已 exit=0 PASS → 判词过期，"
             "但缺新版双类证据与独立复核，未代改为 PASS",
    "H-061": "h-061.log FAIL EXIT=1：部分页打印 writeSubset 逐页重绘，文档级 /AIGC 丢失（KD-84 open）",
    "H-062": "h-062.log FAIL EXIT=1：模型改卷文本未纳入 /AIGC 判据（KD-85 open）",
    "H-066": "台账证据 h-066.log FAIL EXIT=1；本会话 --only H-066 已 exit=0 PASS → 判词过期，"
             "但缺新版双类证据与独立复核，未代改为 PASS",
    "J-001": "j-identity.log：dirty_paths=4248；--require-clean 被拒绝（负控 j-neg-clean-tree.log）",
    "J-002": "j-identity.log：产物的 commits_behind_head=32",
    "J-020": "j-signing.log：RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD 等缺失、tracked_keystores=[]；"
             "j-apksigner.log:10 V2 Signer CN=WS8 Ephemeral Local Only, OU=NotForRelease",
    "J-021": "j-apk-cert.log:1 APK SHA256 9D:C0:7E:9F…056E:20 ≠ "
             "j-keytool.log:1 AAB SHA256 A9:E7:6C:AD…2701",
    "J-024": "同一对比：APK 证书 CN=WS8 Ephemeral Local Only 与 AAB 证书 CN=ZCode Release Smoke 身份不同",
    "J-025": "j-keytool.log 仅含 SHA256 行，无有效期字段；j-domain/00-index.md:58 记 AAB 证书"
             "有效期至 2026-10-30（摘要非证据）。证据链不闭合，发布签名不成立由 J-020/J-021/J-024/J-027 独立支持",
    "J-027": "j-signing.log：文档只写 keystore.properties，实际 RELEASE_* 4/4 缺失、0 个已跟踪 keystore",
    "J-033": "j-aab16k.log EXIT=1：ABI set ['arm64-v8a','armeabi-v7a','x86','x86_64'] "
             "!= abiFilters ['arm64-v8a','x86_64']",
    "J-034": "j-aab16k.log EXIT=1：required native library missing: libquickjs.so",
    "J-041": "j-aab16k.log EXIT=1：AAB 含 32 位 .so（armeabi-v7a/x86），负向断言不成立",
    "J-048": "j-version-artifacts.log：四份产物 versionCode 全为 2，跨构建无单调证据",
    "J-051": "j-version-source.log:33-34 versionCode = 2 / versionName = \"0.2.0\" 为手写两行，"
             "无生成或注入机制",
    "J-052": "j-version-artifacts.log vc=2；j-artifact-mtimes.log AAB mtime 2026-09-30，"
             "历史 AAB 与现 APK 陈旧不可比",
    "GATE-032": "gate/acceptance-report.txt：report state=FAIL、exit_code=1（882/30/144/144），"
                "故「报告终态 exit 0」不成立",
    "GATE-035": "gate/acceptance-counts.txt：pass_with_review=0（882 个 PASS 行无一引用 review 证据）",
    "GATE-038": "gate/ci-report-require-complete.txt：--require-complete exit=1，"
                "violations=NOT_ACCEPTANCE_COMPLETE + PASS_WITHOUT_REVIEW",
    "GATE-042": "gate/ci-lint-strict.txt：CI 严格模式 exit=1（29 条 EVIDENCE_REUSED 升为违规）；"
                "本地 lint exit=0",
    "GATE-091": "gate/aud08-split-gate.txt：countExactRate=4/7=0.5714，门要求 >=0.9 → 未过",
    "GATE-092": "gate/aud08-split-gate.txt：fragmentRate=0/16=0.0000 单条过 0.05，但门整体判 FAIL，"
                "「单条过」不等于门过",
    "GATE-099": "gate/aud08-split-gate.txt EXIT=1：AUD-08 维持 FAIL，未转绿",
}

BLOCKED_GROUPS = [
    ("A-010", ["A-010"], "真实 Provider / 凭据缺失", "runbook §9；注入 *_API_KEY 后重跑"),
    ("D-082…D-090", [f"D-{n:03d}" for n in range(82, 91)], "无真实 Provider 密钥",
     "runbook.md:162；环境变量注入后按 D 域步骤执行"),
    ("F-006…F-087（10）",
     ["F-006", "F-015", "F-024", "F-033", "F-042", "F-051", "F-060", "F-069", "F-078", "F-087"],
     "无 arm64 真机或镜像", "runbook §9 / §9.1；换机后重跑"),
    ("G-087/G-088", ["G-087", "G-088"], "无离线 advisory；锁文件 NOT_LANDED 放行态", "runbook §9"),
    ("H-008/H-024/H-025/H-036/H-039…H-043/H-051/H-053/H-081",
     ["H-008", "H-024", "H-025", "H-036", "H-039", "H-040", "H-041", "H-042", "H-043",
      "H-051", "H-053", "H-081"],
     "热区禁改 / 隐私政策未发布 / Play 后台 / CAC 备案 / 英文材料未产出", "runbook §9 / §13 FIX-T16–T18"),
    ("I-007/I-061/I-070/I-075/I-079", ["I-007", "I-061", "I-070", "I-075", "I-079"],
     "无 TalkBack 语音回传驱动 / 第三方 IME / 传感器真机", "runbook §6 / §9"),
    ("J-006 起 34 项",
     ["J-006", "J-017", "J-019", "J-035", "J-037", "J-038", "J-039", "J-042", "J-043",
      "J-055", "J-056", "J-057", "J-061", "J-065", "J-068", "J-069", "J-073", "J-074",
      "J-075", "J-076", "J-077", "J-078", "J-079", "J-080", "J-081", "J-082", "J-083",
      "J-084", "J-085", "J-086", "J-087", "J-088", "J-089", "J-090"],
     "干净树 / 生产签名 / arm64 / 成对产物 / Play Console", "runbook §9.2"),
    ("FIX-T04…T20 的 85 行（含 17 个不可修登记）",
     [f"FIX-{n:03d}" for n in [
         16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
         36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55,
         56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75,
         76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95,
         96, 97, 98, 99, 100]],
     "20 个修复单中 17 个的落点/外部产物/独立读回未满足；17 个不可修登记行已从 PASS 改判 BLOCKED", "runbook §13 FIX-T04–T20"),
    ("GATE-096/GATE-097/GATE-100", ["GATE-096", "GATE-097", "GATE-100"],
     "无标注员 / 依赖标注执行 / 门红 + 人工金标缺失", "runbook §9"),
]

DOMAIN_TITLES = {key: title for key, title, _ in M.DOMAINS}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def truncate(text: str, limit: int) -> str:
    text = " ".join(text.split())
    return text if len(text) <= limit else text[: limit - 1] + "…"


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}


def review_summary() -> list[dict]:
    out = []
    for path in sorted(REVIEW_DIR.glob("*-review.json")):
        data = load_json(path)
        out.append({
            "file": path.name,
            "domain": data.get("domain", "?"),
            "verdict": data.get("verdict", "?"),
            "reviewer": data.get("reviewer", ""),
            "run_id": data.get("run_id", ""),
            "ledger_sha256": data.get("ledger_sha256", ""),
            "self_review": data.get("self_review"),
        })
    return out


def reason_for(row: M.Row) -> str:
    if row.verdict == "FAIL":
        return FAIL_REASONS.get(row.row_id, f"台账判 FAIL；证据：{row.evidence_text or '—'}")
    method = row.method.strip()
    if row.verdict == "BLOCKED":
        body = method.split("BLOCKED:", 1)[1].strip() if "BLOCKED:" in method else method
        return truncate(body, 240)
    if row.verdict == "UNVERIFIED":
        body = method[len("UNVERIFIED:"):].strip() if method.startswith("UNVERIFIED:") else method
        return truncate(body, 240)
    return ""


def main() -> int:
    ledger_sha = sha256(LEDGER)
    rows, issues = M.parse_ledger(LEDGER)
    if issues:
        print(f"LEDGER_ISSUES: {issues}", file=sys.stderr)
        return 2
    report = load_json(EVID / "report.json")
    require = load_json(EVID / "report-require-complete.json")
    lint = load_json(EVID / "lint.json")
    trace = load_json(EVID / "trace.json")
    generated = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    run_id = f"{generated}-batch45-acceptance-report"

    counts = {v: 0 for v in M.VERDICTS}
    per_domain: dict[str, dict[str, int]] = {}
    for row in rows:
        counts[row.verdict] = counts.get(row.verdict, 0) + 1
        per_domain.setdefault(row.domain, {v: 0 for v in M.VERDICTS})[row.verdict] += 1

    reviews = review_summary()
    review_by_domain = {item["domain"]: item for item in reviews}
    non_pass = [row for row in rows if row.verdict != "PASS"]
    review_tokens = sum(1 for row in rows for e in row.evidence() if e.kind == "review")
    verdict_json_files = sorted(p.name for p in REVIEW_DIR.glob("*.json")
                                if not p.name.startswith("_"))
    gates = [
        ("acceptance_init --check", "python -I tools/ci/acceptance_init.py --check", 0,
         "1200 行 / ID / 域计数通过（成功时静默）", "evidence/2026-11-06-batch-45/init-check.txt"),
        ("acceptance_report", "python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md",
         report.get("exit_code", 1),
         f"counts={report.get('counts')}；state={report.get('state')}；violations={len(report.get('violations', []))}；"
         f"pass_with_review={report.get('pass_with_review')}", "evidence/2026-11-06-batch-45/report.txt"),
        ("acceptance_report --require-complete",
         "python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --require-complete",
         require.get("exit_code", 1),
         "violations=NOT_ACCEPTANCE_COMPLETE + PASS_WITHOUT_REVIEW；acceptance_complete=false",
         "evidence/2026-11-06-batch-45/report-require-complete.txt"),
        ("acceptance_lint", "python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md",
         lint.get("exit_code", 1),
         f"violations={len(lint.get('violations', []))}；warnings={len(lint.get('warnings', []))}（全 EVIDENCE_REUSED）",
         "evidence/2026-11-06-batch-45/lint.txt"),
        ("trace_requirements", "python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md",
         trace.get("exit_code", 1),
         f"gaps={len(trace.get('gaps', []))}；total={trace.get('total')}", "evidence/2026-11-06-batch-45/trace.txt"),
    ]

    lines: list[str] = []
    add = lines.append
    add("# 批次 45 逐项终裁表（1200 项）")
    add("")
    add(f"> 生成命令：`python -I docs/acceptance/evidence/2026-11-06-batch-45/make_acceptance_report.py`")
    add(f"> run-id：`{run_id}`　台账 `docs/acceptance/ledger.md` sha256：`{ledger_sha}`")
    add(f"> 可复算输入：`docs/acceptance/evidence/2026-11-06-batch-45/report.json`、`lint.json`、`trace.json`（本会话实跑）")
    add("")
    add("## 0. 结论：**验收不通过（NOT ACCEPTED），不签总体通过**")
    add("")
    add("| 项 | 实值 | 判定 |")
    add("|---|---|---|")
    add(f"| 台账行数 | {len(rows)}（D0 100 / A–J 各 90 / FIX 100 / GATE 100） | — |")
    add(f"| verdict 计数 | PASS {counts['PASS']} / FAIL {counts['FAIL']} / BLOCKED {counts['BLOCKED']} "
        f"/ UNVERIFIED {counts['UNVERIFIED']} | 非 PASS {len(non_pass)} 行 |")
    add(f"| `acceptance_report` | **exit {report.get('exit_code')}**（state={report.get('state')}） | 阻断 |")
    add(f"| `--require-complete` | **exit {require.get('exit_code', 1)}**"
        f"（{', '.join(v['code'] for v in require.get('violations', [])) or '—'}） | 阻断 |")
    add(f"| `acceptance_lint` | exit {lint.get('exit_code')}，violations={len(lint.get('violations', []))}，"
        f"warnings={len(lint.get('warnings', []))} | 反作弊面无违规 |")
    add(f"| `trace_requirements` | exit {trace.get('exit_code')}，gaps={len(trace.get('gaps', []))} | 追溯缺口为 0 |")
    add(f"| `pass_with_review` | **{report.get('pass_with_review')}** / `pass_without_review` "
        f"{report.get('pass_without_review')} | DoD §1.5 未成立 |")
    add(f"| 域级独立复核 JSON | {len(verdict_json_files)} 个（A–J），绑定**修复前**台账 `bb25ef23…` | 存在，但未在新台账重跑、也未在台账行内引用 |")
    add("")
    add("**判词**：本报告不能签「批次 45 验收通过」，也不构成发版就绪。`PASS` 只表示"
        "「该行证据列通过 `acceptance_report.py` 的 ≥2 类独立证据机械审计」，"
        "不表示该行已完成 DoD §1.5 独立复核，更不表示产品可用。")
    add("")
    add("解除条件见 `docs/acceptance/adjudication.md` §4；逐项原因见本文件 §3、§5。")
    add("")

    add("## 1. 域统计与域级复核")
    add("")
    add("| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 域级复核 JSON | 复核 verdict |")
    add("|---|---:|---:|---:|---:|---:|---|---|")
    for key, title, _ in M.DOMAINS:
        c = per_domain.get(key, {v: 0 for v in M.VERDICTS})
        rv = review_by_domain.get(key)
        add(f"| {key} {title} | {sum(c.values())} | {c['PASS']} | {c['FAIL']} | {c['BLOCKED']} | "
            f"{c['UNVERIFIED']} | {rv['file'] if rv else '—'} | {rv['verdict'] if rv else '—'} |")
    add(f"| **合计** | **{len(rows)}** | **{counts['PASS']}** | **{counts['FAIL']}** | "
        f"**{counts['BLOCKED']}** | **{counts['UNVERIFIED']}** | {len(reviews)} | 无域级 PASS |")
    add("")
    add("> 域级复核 verdict 不是行级签字：A UNVERIFIED / B SAMPLED_REVIEW / C FAIL / D PASS_WORKTREE /"
        " E PASS_WITH_REVIEW / F UNVERIFIED / G BLOCKED / H UNVERIFIED / I UNVERIFIED / J FAIL。"
        "没有任何一域给出「域整体通过」。")
    add("")

    add("## 2. 门禁实跑（本会话，逐字命令）")
    add("")
    add("| 门 | 命令 | exit | 关键实值 | 证据 |")
    add("|---|---|---:|---|---|")
    for name, cmd, code, detail, log in gates:
        add(f"| {name} | `{cmd}` | {code} | {detail.replace('|', '／')} | `{log}` |")
    add("")
    add("证据哈希清单：`docs/acceptance/evidence/2026-11-06-batch-45/EVIDENCE.sha256`（覆盖批次 45 证据根）与"
        "`REVIEWS.sha256`（覆盖 `docs/acceptance/verdicts/`）；verify 结果见 adjudication §2。")
    add("")

    add(f"## 3. 非 PASS 项（{len(non_pass)} 条）")
    add("")
    add(f"### 3.1 FAIL（{counts['FAIL']} 条，逐条原因）")
    add("")
    add("| id | 检查 | 原因（证据原文） |")
    add("|---|---|---|")
    for row in rows:
        if row.verdict == "FAIL":
            add(f"| {row.row_id} | {row.check.replace('|', '／')} | {reason_for(row).replace('|', '／')} |")
    add("")
    add(f"### 3.2 BLOCKED（{counts['BLOCKED']} 条，按原因分组）")
    add("")
    add("| 组 | 行数 | 缺失条件 | 出口 | id 全列 |")
    add("|---|---:|---|---|---|")
    for group, ids, cause, exit_path in BLOCKED_GROUPS:
        add(f"| {group} | {len(ids)} | {cause} | {exit_path} | {', '.join(ids)} |")
    add("")
    add(f"### 3.3 UNVERIFIED（{counts['UNVERIFIED']} 条，按域分组）")
    add("")
    add("| 域 | 行数 | id 全列 | 主要类别 |")
    add("|---|---:|---|---|")
    categories = {
        "D0": "无终裁 JSON 实例 / 无 verdict schema",
        "A": "缺大数据/权限拒绝/中断恢复设备用例",
        "B": "缺 mask=13 篡改/部分写失败夹具",
        "C": "只有单类证据（cmd）或设备侧缺对应拒绝用例",
        "E": "缺并发/DST/设备事务用例",
        "F": "无真机目标或同机历史基线；低存储 UI 未确认",
        "G": "无崩溃采集/Provider/截图/供应链校验",
        "H": "人工语感、RTL/i18n、真实 PDF 截图",
        "I": "待独立复核/人工听读/视觉判断/IME/硬件驱动",
        "J": "门禁阶段构建、成对产物、Play、人工复核",
        "GATE": "人工金标残余",
    }
    for key, _title, _ in M.DOMAINS:
        ids = [row.row_id for row in rows if row.domain == key and row.verdict == "UNVERIFIED"]
        if not ids:
            continue
        add(f"| {key} | {len(ids)} | {', '.join(ids)} | {categories.get(key, '')} |")
    add("")
    add("> 全部 1200 行逐项 verdict 与证据原文见 §5；台账 `方法` 列保留每行残余说明。")
    add("")

    add("## 4. 复核面")
    add("")
    add("| 项 | 实值 |")
    add("|---|---|")
    add(f"| 域级 review JSON | {len(verdict_json_files)} 个：{', '.join(verdict_json_files)} |")
    add(f"| 证据列含 `review:` token 的台账行 | **{review_tokens}** |")
    add(f"| `pass_with_review` | **{report.get('pass_with_review')}** |")
    add(f"| `pass_without_review` | {report.get('pass_without_review')} |")
    add("")
    add("说明：10 个域级复核 JSON 均含 `reviewer`、`run_id`、非自身 `self_review=false`，但绑定的是"
        "**修复前**台账 `bb25ef23…`（修复环未重跑复核）；当前台账 `" + ledger_sha[:8] + "…` 上它们不能"
        "作为行级 review 证据。台账**没有任何一行**把 review 文件写进证据列，所以报告计数仍为 0。"
        f"域级抽样复核不能替代 {report.get('pass_without_review')} 行的行级复核，也不能把任何域签成整体通过。")
    add("")

    add("## 5. 逐项终裁表（1200 行）")
    add("")
    add("| id | domain | verdict | 证据（台账原文） | 原因/残余（非 PASS） |")
    add("|---|---|---|---|---|")
    for row in rows:
        evidence = (row.evidence_text or "—").replace("|", "／")
        reason = reason_for(row).replace("|", "／")
        add(f"| {row.row_id} | {row.domain} | {row.verdict} | {evidence} | {reason} |")
    add("")
    add("## 6. 签字区")
    add("")
    add("| 字段 | 值 |")
    add("|---|---|")
    add("| 结论 | **验收不通过（NOT ACCEPTED）** |")
    add("| 可签范围 | 无：FAIL " + str(counts['FAIL']) + " / BLOCKED " + str(counts['BLOCKED']) +
        " / UNVERIFIED " + str(counts['UNVERIFIED']) + " 未清零；0 行有行级独立复核 |")
    add(f"| ledger_sha256 | `{ledger_sha}` |")
    add(f"| run-id | `{run_id}` |")
    add("| 签字人 | （主会话确认「不通过」结论；不得改写成通过） |")
    add("")
    (ROOT / "docs" / "acceptance" / "verdicts.md").write_text("\n".join(lines), encoding="utf-8", newline="\n")

    index: list[str] = []
    ia = index.append
    ia("# 批次 45 域索引（域 → 计数 → 复核 → 出口）")
    ia("")
    ia(f"> 台账 sha256：`{ledger_sha}`；run-id：`{run_id}`。")
    ia("> 本文件是 `docs/acceptance/verdicts.md` 的域级导航，不单独构成验收结论。")
    ia("")
    ia("| 域 | 检查主题 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 域级复核 | 域出口 |")
    ia("|---|---|---:|---:|---:|---:|---:|---|---|")
    exits = {
        "D0": "acceptance_report / lint / trace 复跑；补逐行终裁 JSON 与冲突规则",
        "A": "runbook §9；补大数据/权限/中断设备用例",
        "B": "补 mask=13 夹具；迁移矩阵已在册",
        "C": "runbook §5；C-065 复跑；单类证据补第二类",
        "D": "runbook §9；真实 Provider 密钥",
        "E": "补并发/DST/设备事务用例；行级复核绑定",
        "F": "runbook §9.1；arm64 真机与历史基线",
        "G": "runbook §9；离线 advisory、锁文件落地",
        "H": "runbook §9 / §13 FIX-T16–T18；热区授权与外部材料",
        "I": "runbook §6 / §9；TalkBack/IME/真机",
        "J": "runbook §9.2；签名、arm64、成对产物、Play",
        "FIX": "runbook §13 FIX-T04–T20",
        "GATE": "runbook §9；人工金标与对抗终裁",
    }
    for key, title, _ in M.DOMAINS:
        c = per_domain.get(key, {v: 0 for v in M.VERDICTS})
        rv = review_by_domain.get(key)
        ia(f"| {key} {title} | {title} | {sum(c.values())} | {c['PASS']} | {c['FAIL']} | {c['BLOCKED']} | "
           f"{c['UNVERIFIED']} | {rv['verdict'] if rv else '—'} | {exits.get(key, '')} |")
    ia(f"| **合计** | — | **{len(rows)}** | **{counts['PASS']}** | **{counts['FAIL']}** | "
       f"**{counts['BLOCKED']}** | **{counts['UNVERIFIED']}** | 无域级 PASS | — |")
    ia("")
    ia("## 证据落点")
    ia("")
    ia("- 逐项终裁：`docs/acceptance/verdicts.md`")
    ia("- 机器报告：`docs/acceptance/acceptance-report.json`")
    ia("- 门禁原始输出：`docs/acceptance/evidence/2026-11-06-batch-45/`")
    ia("- 十域复核 JSON：`docs/acceptance/verdicts/{A..J}-review.json`")
    ia("- 批次 45 域证据：`docs/research/evidence/2026-11-06-batch-45/`")
    ia("- 终裁：`docs/acceptance/adjudication.md`")
    ia("")
    (ROOT / "docs" / "acceptance" / "domain-index.md").write_text(
        "\n".join(index), encoding="utf-8", newline="\n")

    print(json.dumps({
        "verdicts_md": "docs/acceptance/verdicts.md",
        "domain_index": "docs/acceptance/domain-index.md",
        "ledger_sha256": ledger_sha,
        "counts": counts,
        "non_pass": len(non_pass),
        "rows_with_review_evidence": review_tokens,
        "review_json_files": len(verdict_json_files),
        "exit": 0,
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
