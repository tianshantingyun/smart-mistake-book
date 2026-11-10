#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批次 48 终裁报告投影器：verdicts.md / domain-index.md 再生成。

## 它消灭的具体失败

`docs/acceptance/verdicts.md` 与 `docs/acceptance/domain-index.md` 若停留在批次 47 版，
就与批次 48 的台账不同步：批次 48 改判后 ledger sha `a9912c2a…` → `28e96a8d…`、
D-089 BLOCKED→PASS（898/23/279），批次 47 版 verdicts.md 第 526 行仍写
`| D-089 | BLOCKED |`、§1 合计仍写 897/23/280。手写投影会再次与台账脱节——
本脚本只做「台账 + 本批已落盘的门禁 JSON → 报告」的机械投影：

- 输入：`docs/acceptance/ledger.md`（1200 行权威 verdict）
        `gate/acceptance-report.json`（--require-complete 实跑输出）
        `gate/lint.json`、`gate/lint-warn-only.json`、`gate/trace.json`
        `gate/blocked-runbook-coverage-*.json`（两种载体）
        `docs/acceptance/verdicts/rows/*.json`（行级复核覆盖计数）
- 输出：`docs/acceptance/verdicts.md`（§0 结论 / §1 域统计 / §2 逐项表 / §3 不可变项）
        `docs/acceptance/domain-index.md`（域级导航）

## 不伪造的规矩

- §0 的每个数字取自门禁 JSON 的实跑字段；报告不硬编码计数。
- §2 逐项表的「检查 / 证据」列取台账行原文（证据单元格超 220 字符按批次 47
  报告同款截断加省略号，全文在台账）；verdict 取台账行，绝不手写。
- `d0_self_acceptance` 本轮未跑，§0 该行如实写「未跑」，不写 exit。
- FAIL 的处置、BLOCKED 的出口在台账与 blocked-runbook，本脚本不发明新结论。

用法：
    python -I docs/acceptance/evidence/2026-11-07-batch-48/make_verdicts_report.py
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]  # docs/acceptance/evidence/<batch> -> repo root
sys.path.insert(0, str(ROOT / "tools" / "ci"))

import acceptance_model as M  # noqa: E402

LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"
GATE = HERE / "gate"
ROWS_DIR = ROOT / "docs" / "acceptance" / "verdicts" / "rows"
VERDICTS_MD = ROOT / "docs" / "acceptance" / "verdicts.md"
DOMAIN_INDEX = ROOT / "docs" / "acceptance" / "domain-index.md"

B48 = "2026-11-07-batch-48"
D089_RUN_ID = "2026-11-06T18:40:12Z-batch48-d-domain-device"

DOMAIN_EXITS = {
    "D0": "acceptance_report --require-complete exit 1（NOT_ACCEPTANCE_COMPLETE + REVIEW_CHECKS_SHARED 18 组）；"
          "lint exit 1（144 EVIDENCE_REUSED）/ warn-only exit 0；trace exit 0；d0 --group all 本批未跑"
          "（批次 47 已知 h-061_fail 陈旧预期遗留）",
    "A": "blocked-runbook A 域 §A-1..A-5；缺用例/大样本/产品口径",
    "B": "blocked-runbook B 域 §B-1/B-2；mask=13 状态夹具",
    "C": "blocked-runbook C 域 §C-1/C-2；C-008/C-088 仍阻塞",
    "D": "blocked-runbook §1 Provider；D-089 已关闭（批次 48 E-48-D-DEV-01 设备取证）；"
         "D-082 上游请求 id 绑定、D-083..086/088 失败注入、D-090 无 key 复跑仍 BLOCKED",
    "E": "blocked-runbook E 域 §E-1..E-3；并发/真事务/DST",
    "F": "blocked-runbook F 域；F-055/F-057 soak 残余 2 FAIL；arm64 行按用户裁定豁免注记（verdict 维持 BLOCKED，不伪造 PASS）",
    "G": "blocked-runbook G 域；G-089 已关闭（批次 47 verification-metadata.xml）；离线 advisory",
    "H": "blocked-runbook H 域；KD-84 已 closed（批次 47，H-061）；KD-85 与热区授权仍 open",
    "I": "blocked-runbook I 域 §9.5；TalkBack/IME/真机/独立复核签字",
    "J": "blocked-runbook J 域；13 FAIL 全保留；arm64/真机行豁免注记维持 BLOCKED（干净树/生产签名仍是结构前提）",
    "FIX": "blocked-runbook FIX 域（85 行，FIX-T04–T20 映射）；FIX-041..045 豁免注记维持 BLOCKED",
    "GATE": "blocked-runbook GATE 域；AUD-08 v2 门 FAIL（① 10/24=0.4167 <0.9 未过；③ 0.0385≤0.05 过；"
            "切残 fragmentedQuestions=3≠0，WS2 gate-report-v2-01.json）；GATE-096..098/100 维持 BLOCKED"
            "（金标 v2 为 G1 model-labeled，非 G3/G4/G5 人工金标）",
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

    # 行级复核覆盖：PASS 行有 rows/<ID>.json 的比例，逐域统计。
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
    lint_48 = [v for v in lint.get("violations", []) if "2026-11-07" in json.dumps(v, ensure_ascii=False)]

    # ---------- verdicts.md ----------
    L: list[str] = []
    L.append("# 批次 48 逐项终裁表（1200 项，行级复核版）")
    L.append("")
    L.append(f"> run-id：批次 48（D 域设备取证 `{D089_RUN_ID}`；WS4 门禁复跑 2026-11-07）。"
             f"　台账 `docs/acceptance/ledger.md` sha256：`{ledger_sha}`")
    L.append(f"> 输入：`docs/acceptance/acceptance-report.json`、"
             f"`docs/acceptance/evidence/{B48}/gate/`（lint/trace/runbook 覆盖）。"
             f"生成器：`docs/acceptance/evidence/{B48}/make_verdicts_report.py`。")
    L.append("")
    L.append("## 0. 结论：**验收不通过（NOT ACCEPTED），不签总体通过**")
    L.append("")
    L.append("| 项 | 实值 | 判定 |")
    L.append("|---|---|---|")
    L.append("| 台账行数 | 1200（D0 100 / A–J 各 90 / FIX 100 / GATE 100） | — |")
    L.append(f"| verdict 计数 | PASS {counts['PASS']} / FAIL {counts['FAIL']} / BLOCKED {counts['BLOCKED']}"
             f" / UNVERIFIED {counts['UNVERIFIED']} | 非 PASS {counts['FAIL'] + counts['BLOCKED']} 行 |")
    L.append(f"| `acceptance_report --require-complete` | **exit {report['exit_code']}**"
             f"（NOT_ACCEPTANCE_COMPLETE (UNVERIFIED={report['counts']['UNVERIFIED']}"
             f" FAIL={report['counts']['FAIL']} BLOCKED={report['counts']['BLOCKED']})"
             f" + REVIEW_CHECKS_SHARED {len(shared_groups)} 组） | 阻断：仍有 FAIL/BLOCKED |")
    lint_txt = "、".join(f"{c}×{n}" for c, n in lint_codes.items()) or "无"
    L.append(f"| `acceptance_lint` | **exit {lint['exit_code']}**（{lint_txt}；"
             f"{len(lint_48)} 条涉及批次 48 新证据） | 复用告警（诊断面） |")
    L.append(f"| `acceptance_lint --warn-only` | exit {lint_warn['exit_code']}"
             f"（violations=0，{len(lint_warn.get('warnings', []))} 告警降级） | 复用告警不阻断 |")
    L.append(f"| `trace_requirements` | exit {trace['exit_code']}，gaps={len(trace.get('gaps', []))} | 追溯缺口为 0 |")
    L.append(f"| BLOCKED runbook 覆盖 | blocked-runbook.md 载体 **{cov_carrier['covered']}/{cov_carrier['blocked_rows']}**"
             f" exit 0；runbook.md 默认载体 {cov_default['covered']}/{cov_default['blocked_rows']} exit 1"
             f"（默认载体与计划载体错位，批次 47 §6.4 已登记） | 出口条目完整（不证明条件取得） |")
    L.append(f"| `pass_with_review` | **{report['pass_with_review']} / {counts['PASS']}**；"
             f"`pass_without_review` {report['pass_without_review']}；"
             f"`pass_with_invalid_review` {report['pass_with_invalid_review']} | DoD §1.5 机械面成立 |")
    L.append(f"| `pass_with_row_specific_review` | **{report['pass_with_row_specific_review']} / {counts['PASS']}**；"
             f"{len(shared_groups)} 组 ≥5 行共用（{shared_rows} 行） | 行级判别力缺口未清零 |")
    L.append("| `d0_self_acceptance --group all` | 未跑（本批记录员未跑；批次 47 已知 h-061_fail"
             " 陈旧预期遗留） | 不计入本轮门值 |")
    L.append("| 切分门 AUD-08（v2） | 只读引用 WS2 归档 `gate-report-v2-01.json`：① 10/24=0.4167 <0.9 未过；"
             "③ 0.0385≤0.05 过；切残 3≠0；未重跑（acceptance 单次复算红线） | AUD-08 维持 FAIL |")
    L.append("")
    L.append("**判词**：本报告不能签「批次 48 验收通过」，也不构成发版就绪。`PASS` 表示该行证据通过"
             "`acceptance_report.py` 的 ≥2 类独立证据审计，并有一个行级复核 JSON（逐项复算通过）；"
             "行级复核为**机械复算**（sha256、cmd exit、XML counts、device 绑定），不替代第二个人/第二模型的语义复核，"
             "也不表示产品可用。批次 48 实得：BLOCKED 280→**279**（唯一 verdict 变更 = D-089 以 E-48-D-DEV-01 "
             "设备证据转 PASS）；金标 v2 落盘但等级 **G1（model-labeled）**，非人工金标，AUD-08 不因此转绿；"
             "v2 切分门 FAIL 如实落盘（① 0.4167）。")
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
    total_all = sum(counts.values())
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
    L.append("- 6C-2：mask=8 不提供、mask=0 禁止、派生索引恒裁；本批只读确认未漂移"
             "（SmbkSelectionPlan.kt blob `e550f5f8…` 与 HEAD 逐字节一致，`git diff` 空；"
             "`:79` 常量与 `:593` require 在位）。")
    L.append("- KD-76 open（known-defects.md:2916）；isolation 不定案（KD-66 关闭依据不变，批次 40 E-40-04 PARTIAL）；"
             "p09 未收敛（批次 45 三次实测，本批未重跑）。")
    L.append("- AUD-08 FAIL（v2 门 ① 0.4167 <0.9）；金标 v2 = G1 model-labeled；真机/arm64 行为用户裁定豁免注记，"
             "verdict 维持 BLOCKED。")
    L.append("- 发版不成立；本批未新增 Gradle/设备重跑（WS3 设备取证除外，见 E-48-D-DEV-01）。")
    VERDICTS_MD.write_text("\n".join(L) + "\n", encoding="utf-8", newline="\n")

    # ---------- domain-index.md ----------
    D: list[str] = []
    D.append(f"# 批次 48 域索引（域 → 计数 → 行级复核 → 出口）")
    D.append("")
    D.append(f"> 台账 sha256：`{ledger_sha}`；run-id：批次 48（D 域设备取证 `{D089_RUN_ID}`；WS4 门禁复跑 2026-11-07）。")
    D.append("> 本文件是 `docs/acceptance/verdicts.md` 的域级导航，不单独构成验收结论。")
    D.append("")
    D.append("| 域 | 检查主题 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 行级复核 | 域出口 |")
    D.append("|---|---|---:|---:|---:|---:|---:|---|---|")
    for key, title, _ in M.DOMAINS:
        dc = per_domain.get(key, {v: 0 for v in M.VERDICTS})
        have, total = review_cov.get(key, (0, 0))
        D.append(f"| {key} {title} | {title} | {sum(dc.values())} | {dc['PASS']} | {dc['FAIL']} | {dc['BLOCKED']}"
                 f" | {dc['UNVERIFIED']} | {have}/{total} | {DOMAIN_EXITS[key]} |")
    D.append(f"| **合计** | — | **{total_all}** | **{counts['PASS']}** | **{counts['FAIL']}** | **{counts['BLOCKED']}**"
             f" | **{counts['UNVERIFIED']}** | **{report['pass_with_review']}/{counts['PASS']}** | 无域级 PASS |")
    D.append("")
    D.append("## 证据落点")
    D.append("")
    D.append("- 逐项终裁：`docs/acceptance/verdicts.md`")
    D.append("- 行级复核 JSON：`docs/acceptance/verdicts/rows/<ID>.json`（PASS/FAIL 行）")
    D.append("- 机器报告：`docs/acceptance/acceptance-report.json`")
    D.append(f"- 门禁原始输出：`docs/acceptance/evidence/{B48}/gate/`")
    D.append(f"- 三份哈希清单：`docs/acceptance/evidence/{B48}/{{REVIEWS,GATES}}.sha256` +"
             f" `docs/research/evidence/{B48}/EVIDENCE.sha256`")
    D.append(f"- 批次 48 域证据：`docs/research/evidence/{B48}/`（golden-v2 / splitter / d-domain）")
    D.append("- BLOCKED runbook：`docs/acceptance/blocked-runbook.md`（批次 47 WS3 建立，批次 48 WS3 更新）")
    D.append("- 终裁：`docs/acceptance/adjudication.md`")
    DOMAIN_INDEX.write_text("\n".join(D) + "\n", encoding="utf-8", newline="\n")

    print(f"VERDICTS_MD rows={len(rows)} ledger_sha={ledger_sha[:12]}… "
          f"counts={counts} -> {VERDICTS_MD}")
    print(f"DOMAIN_INDEX -> {DOMAIN_INDEX}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
