# -*- coding: utf-8 -*-
"""清理 `new_points_manual.csv` 里的**陈旧行**（建点前必跑）。

## 它消灭的失败

`create_points` 会把表里**所有不在包内**的行都建成节点。但表是留档式的（建过的行不删），于是两类陈旧行
会在大批量建点时被"复活"：

1. **已被合并的节点**（slug 出现在 `point_merge.csv` 的 `merged_slug` 里）——建回来就是死节点的复活，
   与幸存者同名同义，检索与掌握度被拆成两个 id（登记册 U-03 记录过这个坑，当时手工删了 4 行）；
2. **带前后空格的残行**（实测 8 条，如 `' 复数范围内解方程'`）——包内已有同名正确节点，建出来是
   只差一个空格的孪生节点。

清理是**把行挪进停车场**（`build/agent-input/new_points_parked.csv`，带原因），不是静默删除——
记录留在盘上、可复核可回滚。

## 用法

    PYTHONPATH=tools python tools/kb_build/prune_new_points.py            # 只报数
    PYTHONPATH=tools python tools/kb_build/prune_new_points.py --write    # 挪走
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
MANUAL = REPO / "tools" / "kb_build" / "tables" / "new_points_manual.csv"
MERGES = REPO / "tools" / "kb_build" / "tables" / "point_merge.csv"
PARKED = REPO / "build" / "agent-input" / "new_points_parked.csv"
COLS = ("subject", "slug", "name", "kind", "parent_topic_slug", "boundary", "source_locator")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)

    pack = pack_io.load_json(pack_io.pack_path())
    have: dict[str, set[str]] = {}
    for s in pack["subjects"]:
        for t in s["topics"]:
            for k in t.get("knowledgePoints") or []:
                have.setdefault(s["subject"], set()).add(k["slug"])
    merged = {r["merged_slug"] for r in csv.DictReader(MERGES.open(encoding="utf-8-sig"))}

    rows = list(csv.DictReader(MANUAL.open(encoding="utf-8-sig", newline="")))
    keep, park = [], []
    for r in rows:
        slug = r["slug"]
        if slug in have.get(r["subject"], set()):
            keep.append(r)
            continue
        if slug in merged:
            park.append({**r, "reason": "已被 point_merge 合并（建回即复活死节点）"})
        elif slug != slug.strip():
            park.append({**r, "reason": "slug 含前后空格（包内已有同名正确节点）"})
        else:
            keep.append(r)

    print(f"表 {len(rows)} 行：保留 {len(keep)}、挪走 {len(park)}")
    for p in park:
        print(f"   - {p['slug'][:34]!r}  {p['reason']}")

    if args.write and park:
        PARKED.parent.mkdir(parents=True, exist_ok=True)
        new = not PARKED.exists()
        with PARKED.open("a", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=list(COLS) + ["reason"])
            if new:
                w.writeheader()
            w.writerows(park)
        with MANUAL.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=list(COLS))
            w.writeheader()
            w.writerows(keep)
        print(f"→ 挪走 {len(park)} 行到 {PARKED.name}，表剩 {len(keep)} 行")
    elif not args.write:
        print("（未写盘，加 --write 生效）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
