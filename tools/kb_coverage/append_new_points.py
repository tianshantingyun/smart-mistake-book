# -*- coding: utf-8 -*-
"""把建点规划表追加进 `new_points_manual.csv`（`create_points` 的唯一入口）。

## 它消灭的失败

`create_points` 只认 `new_points_manual.csv` 的 7 列（`subject,slug,name,kind,parent_topic_slug,boundary,source_locator`），
而 `block_proposals_plan.csv` 是 10 列（多 `place,evidence_chunk,alias_written_as`）——
多出来的列会让 `tables._require_columns` 报错、少一行 parent 不存在会让**整批**拒写（`create_points` 的错误处理是整批 return 1）。
所以追加前先把两头对齐：只投影 7 列 + 逐行校验 `parent_topic_slug` 在 staging 的该科主题里真实存在。

幂等：已在表里的 slug 跳过（`create_points` 本身也按 slug 幂等，两道都留着）。

用法：`PYTHONPATH=tools python tools/kb_coverage/append_new_points.py [--write]`
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from kb_build import pack_io  # noqa: E402

REPO = TOOLS.parent
PLAN = REPO / "build" / "agent-input" / "block_proposals_plan.csv"
MANUAL = REPO / "tools" / "kb_build" / "tables" / "new_points_manual.csv"
COLS = ("subject", "slug", "name", "kind", "parent_topic_slug", "boundary", "source_locator")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)

    topics = {s["subject"]: {t["slug"] for t in s["topics"]}
              for s in pack_io.load_json(pack_io.pack_path())["subjects"]}
    manual_rows = list(csv.DictReader(MANUAL.open(encoding="utf-8-sig", newline="")))
    have = {(r["subject"], r["slug"]) for r in manual_rows}

    plan = list(csv.DictReader(PLAN.open(encoding="utf-8-sig", newline="")))
    add, dup, bad = [], 0, []
    for p in plan:
        if (p["subject"], p["slug"]) in have:
            dup += 1
            continue
        if p["parent_topic_slug"] not in topics.get(p["subject"], set()):
            bad.append((p["slug"], p["parent_topic_slug"]))
            continue
        add.append({k: p[k] for k in COLS})
        have.add((p["subject"], p["slug"]))

    print(f"规划 {len(plan)} 行：待追加 {len(add)}、已在表 {dup}、父主题不存在 {len(bad)}")
    for slug, parent in bad[:10]:
        print(f"   ! {slug[:30]} → 父主题 {parent[:44]}")

    if bad:
        print("有父主题不存在 → 不写（避免 create_points 整批拒错）")
        return 1
    if args.write and add:
        with MANUAL.open("a", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=list(COLS))
            for r in add:
                w.writerow(r)
        print(f"→ 已追加 {len(add)} 行到 {MANUAL.name}")
    elif not args.write:
        print("（未写盘，加 --write 生效）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
