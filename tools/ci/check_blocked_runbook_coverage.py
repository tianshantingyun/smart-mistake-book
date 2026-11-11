#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""BLOCKED 行的 runbook 覆盖统计门（批次 47 WS3）。

## 它消灭的具体失败

台账 BLOCKED 行的证据 token 只写了 `file:docs/acceptance/runbook.md`，一行
“条件/命令/判据/责任人”俱全的出口并没有因此存在——I 域 37 条 BLOCKED 曾只有
5 个 ID（FIX-T19 提到的 I-007/I-061/I-070/I-075/I-079）在 runbook 里出现，
`blocked_runbook_coverage` 实际是 5/37，却被证据 token 冒充为“已覆盖”。

本脚把覆盖变成**可复算的判据**：某 BLOCKED 行算覆盖，当且仅当
1) 行 ID 在 `docs/acceptance/runbook.md` 某个小节出现；且
2) 该小节至少给出一条可执行命令（`python -I` / `adb`）或一个明确的人工判据
   （“人工”“签署”“评审人”）。

它只证明“出口条目存在且给了命令/判据”。它**不**证明条件已取得——BLOCKED 行
在条件取得前不得改 PASS。

## 用法

    python -I tools/ci/check_blocked_runbook_coverage.py --domain I
    python -I tools/ci/check_blocked_runbook_coverage.py            # 全部域

exit 0 = 过滤范围内每条 BLOCKED 行都被覆盖；
exit 1 = 存在未覆盖行；
exit 2 = 输入不可读。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUNBOOK = ROOT / "docs/acceptance/runbook.md"
LEDGER = ROOT / "docs/acceptance/ledger.md"
ACTION = re.compile(r"python -I|adb -s|人工|签署|评审人")


def ledger_rows() -> list[dict]:
    rows: list[dict] = []
    line_no = 0
    for line in LEDGER.read_text(encoding="utf-8").splitlines():
        line_no += 1
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) != 6 or cells[0] == "ID" or set(cells[0]) <= set("-: "):
            continue
        rows.append({"id": cells[0], "domain": cells[1], "verdict": cells[5]})
    return rows


def section_spans(text: str) -> list[tuple[int, int, str]]:
    """按 `## `/`### ` 标题切段，返回 (start, end, heading)。"""
    marks = [(m.start(), m.group(0).strip()) for m in re.finditer(r"(?m)^#{2,3} .*$", text)]
    spans = []
    for idx, (start, heading) in enumerate(marks):
        end = marks[idx + 1][0] if idx + 1 in range(len(marks)) else len(text)
        spans.append((start, end, heading))
    return spans


def coverage(text: str, rows: list[dict], domain: str | None) -> dict:
    spans = section_spans(text)
    per_row = []
    for row in rows:
        if row["verdict"] != "BLOCKED":
            continue
        if domain and row["domain"].upper() != domain.upper():
            continue
        hits = []
        for start, end, heading in spans:
            body = text[start:end]
            if re.search(rf"(?<![A-Za-z0-9-]){re.escape(row['id'])}(?![0-9])", body):
                hits.append({"heading": heading, "has_action": bool(ACTION.search(body))})
        covered = any(h["has_action"] for h in hits)
        per_row.append({"id": row["id"], "domain": row["domain"],
                        "covered": covered,
                        "sections": [h["heading"] for h in hits]})
    covered_n = sum(1 for r in per_row if r["covered"])
    return {
        "runbook": str(RUNBOOK.relative_to(ROOT)),
        "domain": domain,
        "blocked_rows": len(per_row),
        "covered": covered_n,
        "missing": [r["id"] for r in per_row if not r["covered"]],
        "rows": per_row,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--domain", default=None)
    parser.add_argument("--out", default=None)
    args = parser.parse_args()
    try:
        text = RUNBOOK.read_text(encoding="utf-8")
    except OSError as exc:
        print(f"RUNBOOK_UNREADABLE {exc}", file=sys.stderr)
        return 2
    result = coverage(text, ledger_rows(), args.domain)
    payload = json.dumps({k: v for k, v in result.items() if k != "rows"},
                         ensure_ascii=False, indent=2)
    if args.out:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                       encoding="utf-8")
    print(payload)
    return 0 if not result["missing"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
