#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""修复 J-002/J-048/J-052 行级 review JSON 的判词自相矛盾。

现象（独立复核 finding）：这三行批次51 由 FAIL 机械翻为 PASS，但 notes 仍逐字保留
「行级复核判 FAIL…不洗绿」，且重复了 4 次批次51 换绑后缀。读者会看到 PASS verdict
与「判 FAIL」判词并存。

本脚本只改这三份的 notes（verdict/evidence/checks 不动），并加机器可读的
prior_verdict / verdict_flip 字段：明确翻转是机械复算、非独立语义复核。
不洗绿、不改判、不改台账。
"""

from __future__ import annotations

import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
ROWS = ROOT / "docs/acceptance/verdicts/rows"
TARGETS = ("J-002", "J-048", "J-052")

NEW_NOTES = (
    "历史：批次50 行级复核判 FAIL（保留原判词，不洗绿）。"
    "批次51：按本批新证据重新机械复算通过，verdict 由 FAIL 翻为 PASS；"
    "翻转为机械复算，不构成第二人/第二模型的独立语义复核。 "
    "批次51：ledger_sha256 与 ledger_row 换绑；证据字节未改（本批新增证据行除外）。"
)


def main() -> int:
    for row_id in TARGETS:
        path = ROWS / f"{row_id}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("verdict") != "PASS":
            raise SystemExit(f"{row_id}: expected PASS, got {data.get('verdict')!r}")
        old_notes = data.get("notes") or ""
        if "行级复核判 FAIL" not in old_notes:
            raise SystemExit(f"{row_id}: expected prior FAIL note, refusing to rewrite")
        if "批次51：ledger_sha256 与 ledger_row 换绑" not in old_notes:
            raise SystemExit(f"{row_id}: expected batch51 rebound suffix")
        data["notes"] = NEW_NOTES
        data["prior_verdict"] = "FAIL"
        data["verdict_flip"] = {
            "from": "FAIL",
            "to": "PASS",
            "kind": "mechanical-recompute",
            "semantic_review": False,
            "reason": ("批次50 的证据矛盾已由批次51 新证据替换并实跑通过；"
                       "未做第二人/第二模型语义复核"),
        }
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n",
                        encoding="utf-8", newline="\n")
        print(f"{row_id}: notes rewritten; prior_verdict=FAIL; verdict_flip=mechanical-recompute")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
