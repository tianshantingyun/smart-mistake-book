# -*- coding: utf-8 -*-
"""判定表 MATERIAL 行的"例题派生"泄漏检查（4 条正则：题号/成套选项/答案语/题干尾）。

## 它消灭的失败

判定代理写回的材料里仍可能混入题目形态（题号标题、A/B/C/D 成套选项、
"答案/故选"语、短题干无结论）——题目入库违反"只要知识点"的底线。
判定阶段机器已挡过一轮，但判定表是**代理改写后的**最终内容，
入包前再用 `kb_build.audit_material_examples` 的**同一组正则**机械复核，
命中即出清单（按 node_slug 汇总）。

字段映射与 audit_material_examples.classify 一致：
title → title；contentMarkdown/summaryMarkdown/applicabilityMarkdown
← content/summary/applicability。只查 action=MATERIAL 行；不改判定表、
不改 materialize 逻辑。

退出码：无命中 exit 0；有命中 exit 1（守卫语义，接 CI 门时直接失败）。

用法：PYTHONPATH=tools python -m kb_coverage.check_exam_leak
"""

from __future__ import annotations

import csv
import sys
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import audit_material_examples as ame  # noqa: E402

JUDGMENTS = REPO / "tools/kb_coverage/tables/material_judgments.csv"


def check_rows(rows: list[dict]) -> list[dict]:
    """对 MATERIAL 行跑 4 条判据，返回命中清单（node_slug/chunk_rel/chunk_id/midx/reason）。"""
    hits: list[dict] = []
    for r in rows:
        if (r.get("action") or "").strip() != "MATERIAL":
            continue
        fake = {
            "title": r.get("title") or "",
            "contentMarkdown": r.get("content") or "",
            "summaryMarkdown": r.get("summary") or "",
            "applicabilityMarkdown": r.get("applicability") or "",
        }
        is_example, why = ame.classify(fake)
        if is_example:
            hits.append({
                "node_slug": (r.get("node_slug") or "").strip() or "(空)",
                "chunk_rel": r.get("chunk_rel") or "",
                "chunk_id": r.get("chunk_id") or "",
                "midx": (r.get("midx") or "").strip(),
                "reason": why,
            })
    return hits


def main(argv: list[str] | None = None) -> int:
    with JUDGMENTS.open(encoding="utf-8-sig", newline="") as fh:
        rows = list(csv.DictReader(fh))
    material = [r for r in rows if (r.get("action") or "").strip() == "MATERIAL"]
    hits = check_rows(rows)
    by_slug: dict[str, list[dict]] = defaultdict(list)
    for h in hits:
        by_slug[h["node_slug"]].append(h)
    reasons = Counter(h["reason"].split("：")[0] for h in hits)
    print(f"MATERIAL 行 {len(material)}；例题派生命中 {len(hits)}（涉及 node_slug {len(by_slug)} 个）")
    if not hits:
        print("无命中")
        return 0
    print("按判据：")
    for why, n in reasons.most_common():
        print(f"   {n:>5}  {why}")
    print()
    print("按 node_slug（命中行数 | slug）：")
    for slug in sorted(by_slug, key=lambda s: (-len(by_slug[s]), s)):
        print(f"   {len(by_slug[slug]):>5}  {slug}")
    print()
    print("明细（前 30 条）：")
    for h in hits[:30]:
        print(f"   [{h['reason'][:26]}] {h['chunk_rel'][:44]} {h['chunk_id']}{h['midx']}"
              f" → {h['node_slug'][:30]}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
