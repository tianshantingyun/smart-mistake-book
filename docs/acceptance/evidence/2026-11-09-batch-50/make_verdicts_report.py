#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批次 50 终裁报告投影器（verdicts.md / domain-index.md 再生成）。

输入：docs/acceptance/ledger.md（1200 行权威 verdict）、本目录 gate/ 下本批实跑门禁 JSON、
docs/acceptance/verdicts/rows/*.json（行级复核覆盖计数）。
输出：docs/acceptance/verdicts.md、docs/acceptance/domain-index.md。

只做机械投影：verdict/检查/证据取台账原文，计数取门禁 JSON 实跑字段；
不写回台账、不改行级 JSON、不发明结论。
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / "tools" / "ci"))

import acceptance_model as M  # noqa: E402

LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"
GATE = HERE / "gate"
ROWS_DIR = ROOT / "docs" / "acceptance" / "verdicts" / "rows"
VERDICTS_MD = ROOT / "docs" / "acceptance" / "verdicts.md"
DOMAIN_INDEX = ROOT / "docs" / "acceptance" / "domain-index.md"

B50 = "2026-11-09-batch-50"
RUN_ID = "2026-11-09T00:00:00Z-batch50-ledger"

DOMAIN_EXITS = {
    "D0": "acceptance_report --require-complete exit 1（NOT_ACCEPTANCE_COMPLETE + REVIEW_CHECKS_SHARED）；"
          "lint exit 1（EVIDENCE_REUSED，诊断面）/ warn-only exit 0；trace exit 0；acceptance_init --check exit 0；"
          "d0 --group all 本批未跑",
    "A": "blocked-runbook A 域；批次50 将 5c 错因面板/6B-3 改卷 UI/5b 容量设备证据追加到 A-028/A-037，"
         "A-031 等 25 行仍缺独立空态/大数据/权限用例",
    "B": "blocked-runbook B 域 §B-1/B-2；**schema 75 设备矩阵已重跑并换绑**（迁移 1..75 20 例、备份 34、"
         "trimmer 10、rowCounts 2、drill 4 绿）；B-077/B-078（mask=13 partial/corrupt）仍缺夹具",
    "C": "blocked-runbook C 域 §C-1/C-2；C-008/C-088 仍阻塞（本批未动）",
    "D": "blocked-runbook §1 Provider；D-082..086/088/090 仍 BLOCKED（本批未动）",
    "E": "blocked-runbook E 域 §E-1..E-3；并发 7 行 + DST 1 行保持 BLOCKED；批次50 追加 3E/5b/5c/KD-67 "
         "证据到 E-017/E-065/E-070/E-075/E-080/E-081（E-070 仍 BLOCKED）",
    "F": "blocked-runbook F 域；批次50 release APK 2h soak（7200s/115 轮）刷新 F-055/F-059/F-063；"
         "F-057 因超限路径未命中（FATAL=0）维持 FAIL；F-056/F-058 仍 BLOCKED；arm64/16KB 运行时未验",
    "G": "blocked-runbook G 域；G-089 已关闭（批次 47）；本批未动",
    "H": "blocked-runbook H 域；批次50 AIGC（显式角标/DOCX/PDF/KD-85/KD-84）与隐私未成年人专章"
         "将 H-008/H-018/H-025/H-043/H-062 转 PASS；H-024/H-036/H-053 外部 BLOCKED/FAIL；"
         "H-063/H-070/H-075/H-076/H-077/H-080/H-081/H-089/H-090 仍为人工/外部项",
    "I": "blocked-runbook I 域 §9.5；TalkBack/IME/真机/独立复核签字（本批未动）",
    "J": "blocked-runbook J 域；批次50 本地 release key/AAB 结构/versionCode 注入将 J-022/J-024/J-025/J-027/"
         "J-033/J-034/J-041/J-051 转 PASS；J-001/J-002/J-020/J-021/J-048/J-052 仍 FAIL（干净树/生产钥匙/"
         "跨构建单调）；arm64/16KB 运行时/Play 账号仍 BLOCKED",
    "FIX": "blocked-runbook FIX 域；批次50 FIX-T04/T05/T07/T17 关闭态门将 FIX-016..020/021..025/031..035/"
            "081..085 转 PASS；其余修复单仍 BLOCKED（真实 Provider/arm64/生产签名/Play 等外部项）",
    "GATE": "blocked-runbook GATE 域；AUD-08 批次50 单次跑 FAIL（① 7/24=0.2917 <0.9；③ 0.0128≤0.05；"
            "硬红线 fragmentedQuestions=1≠0）；GATE-096..098/100 维持 BLOCKED（金标 G1 model-labeled）",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def truncate(text: str, limit: int = 220) -> str:
    text = " ".join(text.split())
    return text if len(text) <= limit else text[: limit - 1] + "…"


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    ledger_sha = sha256(LEDGER)
    rows, issues = M.parse_ledger(LEDGER)
    if issues:
        print(f"LEDGER_ISSUES: {issues}", file=sys.stderr)
        return 2

    report = load_json(GATE / "acceptance-report.json")
    req = load_json(GATE / "acceptance-report-require-complete.json")
    lint = load_json(GATE / "lint.json")
    lint_warn = load_json(GATE / "lint-warn-only.json")
    trace = load_json(GATE / "trace.json")
    cov_default = load_json(GATE / "blocked-runbook-coverage-runbook-md.json")
    cov_carrier = load_json(GATE / "blocked-runbook-coverage-blocked-runbook-md.json")

    counts = {v: 0 for v in M.VERDICTS}
    per_domain: dict[str, dict[str, int]] = {}
    for row in rows:
        counts[row.verdict] = counts.get(row.verdict, 0) + 1
        per_domain.setdefault(row.domain, {v: 0 for v in M.VERDICTS})[row.verdict] += 1

    review_cov: dict[str, tuple[int, int]] = {}
    for dom, dc in per_domain.items():
        pass_rows = [r for r in rows if r.domain == dom and r.verdict == "PASS"]
        have = sum(1 for r in pass_rows if (ROWS_DIR / f"{r.row_id}.json").exists())
        review_cov[dom] = (have, len(pass_rows))

    shared_groups = report.get("review_shared_check_groups", [])
    shared_rows = sum(g["rows"] for g in shared_groups)
    lint_codes: dict[str, int] = {}
    for v in lint.get("violations", []):
        lint_codes[v.get("code", "?")] = lint_codes.get(v.get("code", "?"), 0) + 1
    req_codes = [v.get("code") for v in req.get("violations", [])]
    total_all = sum(counts.values())

    L: list[str] = []
    L.append("# 批次 50 逐项终裁表（1200 项，行级复核版）")
    L.append("")
    L.append(f"> 台账 `docs/acceptance/ledger.md` sha256：`{ledger_sha}`；run-id：`{RUN_ID}`。")
    L.append("> 本批 = 发版水平总攻：四门（3D/3E/MVA/AUD-08）+ 产品面（5b/5c/6B-3/JS/KD-67）+ 发布线"
             "（versionCode/本地 release key/AAB/16KB/AIGC/隐私）。")
    L.append(f"> 输入：`docs/acceptance/acceptance-report.json`、"
             f"`docs/acceptance/evidence/{B50}/gate/`（report/lint/trace/runbook 覆盖）。"
             f"生成器：`docs/acceptance/evidence/{B50}/make_verdicts_report.py`。")
    L.append("")
    L.append("## 0. 结论：**验收不通过（NOT ACCEPTED），不签总体通过**")
    L.append("")
    L.append("| 项 | 实值 | 判定 |")
    L.append("|---|---|---|")
    L.append("| 台账行数 | 1200（D0 100 / A–J 各 90 / FIX 100 / GATE 100） | — |")
    L.append(f"| verdict 计数 | PASS {counts['PASS']} / FAIL {counts['FAIL']} / BLOCKED {counts['BLOCKED']}"
             f" / UNVERIFIED {counts['UNVERIFIED']} | 非 PASS {counts['FAIL'] + counts['BLOCKED']} 行 |")
    L.append(f"| `acceptance_report --require-complete` | **exit {req['exit_code']}**"
             f"（{' + '.join(req_codes)}） | 阻断：仍有 FAIL/BLOCKED |")
    lint_txt = "、".join(f"{c}×{n}" for c, n in lint_codes.items()) or "无"
    L.append(f"| `acceptance_lint` | **exit {lint['exit_code']}**（{lint_txt}） | 复用告警（诊断面） |")
    L.append(f"| `acceptance_lint --warn-only` | exit {lint_warn['exit_code']}"
             f"（violations=0，{len(lint_warn.get('warnings', []))} 告警降级） | 复用告警不阻断 |")
    L.append(f"| `trace_requirements` | exit {trace['exit_code']}，gaps={len(trace.get('gaps', []))} | 追溯缺口为 0 |")
    L.append(f"| BLOCKED runbook 覆盖 | blocked-runbook.md 载体 **{cov_carrier['covered']}/{cov_carrier['blocked_rows']}**"
             f"；runbook.md 默认载体 {cov_default['covered']}/{cov_default['blocked_rows']}"
             f"（默认载体与计划载体错位，批次 47 §6.4 已登记） | 出口条目完整（不证明条件取得） |")
    L.append(f"| `pass_with_review` | **{report['pass_with_review']} / {counts['PASS']}**；"
             f"`pass_without_review` {report['pass_without_review']}；"
             f"`pass_with_invalid_review` {report['pass_with_invalid_review']} | DoD §1.5 机械面成立 |")
    L.append(f"| `pass_with_row_specific_review` | **{report['pass_with_row_specific_review']} / {counts['PASS']}**；"
             f"{len(shared_groups)} 组 ≥5 行共用（{shared_rows} 行） | 行级判别力缺口未清零 |")
    L.append("| `acceptance_init --check` | exit 0（无输出） | 台账结构成立 |")
    L.append("| `d0_self_acceptance --group all` | 未跑（本记录员未跑） | 不计入本轮门值 |")
    L.append("| 切分门 AUD-08（批次50 单次） | ① 7/24=0.2917 <0.9 未过；③ 1/78=0.0128≤0.05 过；"
             "硬红线 fragmentedQuestions=1≠0 → **FAIL** | AUD-08 维持 FAIL |")
    L.append("")
    L.append("**判词**：本报告不能签「批次 50 验收通过」，也不构成发版就绪。`PASS` 表示该行证据通过"
             "`acceptance_report.py` 的 ≥2 类独立证据审计，并有一个行级复核 JSON（逐项复算通过）；"
             "行级复核为**机械复算**（sha256、cmd exit、XML counts、device 绑定），不替代第二个人/第二模型的语义复核。"
             "批次 50 实得：PASS 900→**933**、FAIL 22→**13**、BLOCKED 278→**254**；"
             "新增 PASS 集中在 H（AIGC/隐私 5 行）、FIX（T04/T05/T07/T17 20 行）、J（发布结构 8 行）；"
             "3D D-1 仍 `overall_pass=false`（Wilson 下界 0.8979<0.90；主判据 0.9757 **未过 κ 前置**，"
             "只是 model-labeled 暂定值，不得当门值引用）、3E 条件性通过（第二档无预留份额）、"
             "MVA 不可评（零生图/零 VLM judge）、AUD-08 FAIL（acceptance 8 页在本批只运行一次，"
             "但同一 8 页已在批次 48 消费，held-out 不可恢复）；这些门**不因台账行升级而变成通过**。")
    L.append("")
    L.append("## 1. 域统计")
    L.append("")
    L.append("| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 行级复核 |")
    L.append("|---|---:|---:|---:|---:|---:|---|")
    dom_titles = {key: title for key, title, _ in M.DOMAINS}
    for key, title, _ in M.DOMAINS:
        dc = per_domain.get(key, {v: 0 for v in M.VERDICTS})
        have, total = review_cov.get(key, (0, 0))
        L.append(f"| {key} {title} | {sum(dc.values())} | {dc['PASS']} | {dc['FAIL']} | {dc['BLOCKED']}"
                 f" | {dc['UNVERIFIED']} | {have}/{total} |")
    L.append(f"| **合计** | **{total_all}** | **{counts['PASS']}** | **{counts['FAIL']}** | **{counts['BLOCKED']}**"
             f" | **{counts['UNVERIFIED']}** | **{report['pass_with_review']}/{counts['PASS']}** |")
    L.append("")
    L.append("## 2. 逐项表")
    L.append("")
    for key, title, _ in M.DOMAINS:
        dom_rows = [r for r in rows if r.domain == key]
        L.append(f"### {key} {title}（{len(dom_rows)}）")
        L.append("")
        L.append("| ID | verdict | 检查 | 证据 |")
        L.append("|---|---|---|---|")
        for r in dom_rows:
            L.append(f"| {r.row_id} | {r.verdict} | {truncate(r.check)} | {truncate(r.evidence_text)} |")
        L.append("")
    L.append("## 3. 不可变项")
    L.append("")
    L.append("- 门阈值未改：AUD-08 ① ≥0.9 / ③ ≤0.05 / fragmentedQuestions=0；3D D-1 0.95；MVA 90/95/90/0；"
             "3E 每日上限 10。")
    L.append("- 掌握阈值 21 天/0.9、G6 幅度 0.5、`ReviewPlannerV2.VERSION` 未改（批次49 结论沿用）。")
    L.append("- 本地 release key 明确 `OU=NOT-PLAY-PRODUCTION`；不是 Play 生产身份，未伪造生产签名/指纹。")
    L.append("- 备案号/运营主体/联系方式/Play 账号/隐私政策公开 URL 仍 BLOCKED（外部）。")
    L.append("- 3D 金标为 model-labeled（G1），且 D-1 判定一致性 κ **NOT RUN** → 主判据只可读作"
             "「model-labeled 暂定值」，按 R2 §4.5 不得用于门；"
             "AUD-08 acceptance 8 页**在本批**只运行一次（唯一 run），但同一 8 页已在批次 48 的"
             " `acceptance-once-01` 消费，held-out 属性不可恢复（本批是第二次观测）——"
             "见 `split-gate/SCOPE-CORRECTION.md`；MVA 零生图/零 judge。")
    VERDICTS_MD.write_text("\n".join(L) + "\n", encoding="utf-8", newline="\n")

    D: list[str] = []
    D.append("# 批次 50 域索引（域 → 计数 → 行级复核 → 出口）")
    D.append("")
    D.append(f"> 台账 sha256：`{ledger_sha}`；run-id：`{RUN_ID}`。")
    D.append("> 本文件是 `docs/acceptance/verdicts.md` 的域级导航，不单独构成验收结论。")
    D.append("")
    D.append("| 域 | 检查主题 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 行级复核 | 域出口 |")
    D.append("|---|---|---:|---:|---:|---:|---:|---|---|")
    for key, title, _ in M.DOMAINS:
        dc = per_domain.get(key, {v: 0 for v in M.VERDICTS})
        have, total = review_cov.get(key, (0, 0))
        D.append(f"| {key} {title} | {title} | {sum(dc.values())} | {dc['PASS']} | {dc['FAIL']} | "
                 f"{dc['BLOCKED']} | {dc['UNVERIFIED']} | {have}/{total} | {DOMAIN_EXITS.get(key, '')} |")
    D.append(f"| **合计** | — | **{total_all}** | **{counts['PASS']}** | **{counts['FAIL']}** | "
             f"**{counts['BLOCKED']}** | **{counts['UNVERIFIED']}** | **{report['pass_with_review']}/{counts['PASS']}** | "
             f"无域级 PASS；`--require-complete` exit {req['exit_code']} |")
    D.append("")
    D.append("## 证据落点")
    D.append("")
    D.append("- 逐项终裁：`docs/acceptance/verdicts.md`")
    D.append("- 行级复核 JSON：`docs/acceptance/verdicts/rows/<ID>.json`（PASS/FAIL 行）")
    D.append("- 机器报告：`docs/acceptance/acceptance-report.json`")
    D.append(f"- 门禁原始输出：`docs/acceptance/evidence/{B50}/gate/`")
    D.append(f"- 批次 50 证据：`docs/research/evidence/{B50}/`（3e/d1/d2/d3/mva/split-gate/5b/5c/6b-3/js/kd67/"
             "aigc/privacy/release/device/g0）")
    D.append(f"- 哈希清单：`docs/acceptance/evidence/{B50}/REVIEWS.sha256`、`.../GATES.sha256`、"
             f"`docs/research/evidence/{B50}/EVIDENCE.sha256`")
    D.append("- BLOCKED runbook：`docs/acceptance/blocked-runbook.md`")
    D.append("- 终裁：`docs/acceptance/adjudication.md`（批次50 段由主会话收口时追加）")
    DOMAIN_INDEX.write_text("\n".join(D) + "\n", encoding="utf-8", newline="\n")
    print("ledger", ledger_sha)
    print("counts", counts)
    print("written", VERDICTS_MD, DOMAIN_INDEX)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
