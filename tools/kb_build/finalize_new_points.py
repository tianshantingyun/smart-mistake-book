# -*- coding: utf-8 -*-
"""新点收口三件：填真边界 / 零材料点记删 / 来源单元声明（建点闭环的最后一步）。

## 它消灭的三个失败（都是建点后才暴露的）

1. **`locator_boundary` 红 296**：规划表给新点的 `boundary` 是 `定位：<册 章>。` 空壳（excerpt 为空），
   门判"只有定位串、没有真边界"。修法：把该点**材料的 boundary** 接在定位串之后——
   与既有节点同格式（`定位：…。<真边界句>`），信息来自本轮真的写出来的材料。
2. **`unbound_points` 红 8**：块过薄/图未提取/填空骨架的 8 个点写不出材料，会永远零材料。
   修法：按权威删点通道（`point_delete.csv`）记为待删，并把 `new_points_manual.csv` 里的行挪进停车场。
3. **`chapter_uncovered_units` 红 7**：新点的 `source_locator`（`块池判定·<来源>`）不在章表里。
   修法：往 `chapter_by_source.csv` 追加这 7 个单元，`decision=keep_per_node`
   ——归属按节点自己的定位串（与 `人教版高中教材（2019）` 同口径）。

用法：`PYTHONPATH=tools python -m kb_build.finalize_new_points [--write]`
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from kb_build import pack_io  # noqa: E402

REPO = TOOLS.parent
PLAN = REPO / "build" / "agent-input" / "block_proposals_plan.json"
MANUAL = REPO / "tools" / "kb_build" / "tables" / "new_points_manual.csv"
PARKED = REPO / "build" / "agent-input" / "new_points_parked.csv"
DELETE = REPO / "tools" / "kb_build" / "tables" / "point_delete.csv"
CHAPTER_SRC = REPO / "tools" / "kb_build" / "tables" / "chapter_by_source.csv"
MANUAL_COLS = ("subject", "slug", "name", "kind", "parent_topic_slug", "boundary", "source_locator")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)

    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    pack = pack_io.load_json(pack_io.pack_path())

    # 材料反查：node_id → 材料 boundary（首条）
    mat_by_node: dict[str, str] = {}
    for sp in pack_io.sidecar_paths():
        if not sp.exists():
            continue
        for m in pack_io.load_json(sp).get("materials") or []:
            for b in m.get("bindings") or []:
                mat_by_node.setdefault(b["knowledgeNodeId"], (m.get("boundaryMarkdown") or "").strip())

    filled, empty = [], []
    for s in pack["subjects"]:
        subj = s["subject"]
        for t in s["topics"]:
            for kp in t.get("knowledgePoints") or []:
                p = next((x for x in plan["plan"] if x["subject"] == subj and x["slug"] == kp["slug"]), None)
                if p is None:
                    continue
                node_id = f"kb:{pack['packId']}:{subj.lower()}:atomic:{kp['slug']}"
                mb = mat_by_node.get(node_id, "")
                if not mb:
                    empty.append((subj, kp["slug"], p["place"]))
                    continue
                mb = mb.rstrip().rstrip("\\").strip()
                new_boundary = f"定位：{p['place']}。{mb}"
                if kp.get("boundary") != new_boundary:
                    filled.append((subj, kp["slug"], kp.get("boundary") or "", new_boundary))
                    if args.write:
                        kp["boundary"] = new_boundary

    # 来源单元：从规划表实际用到的 source_locator 里取（章表缺的那些）
    units = sorted({p["source_locator"] for p in plan["plan"]})
    have_units = {r["source_unit"] for r in csv.DictReader(CHAPTER_SRC.open(encoding="utf-8-sig"))}
    new_units = [u for u in units if u not in have_units]

    print(f"① 待填真边界 {len(filled)} 个")
    for f in filled[:3]:
        print(f"   {f[1][:26]}: {f[2][:26]} → {f[3][:60]}")
    print(f"② 零材料点（记删）{len(empty)} 个：")
    for e in empty:
        print(f"   [{e[0]}] {e[1][:34]}")
    print(f"③ 章表缺的来源单元 {len(new_units)} 个：{new_units}")

    if not args.write:
        print("（未写盘，加 --write）")
        return 0

    # ① 写回包
    if filled:
        pack_io.dump_json(pack, pack_io.pack_path())
        print(f"→ 包已写回（{len(filled)} 个节点边界）")

    # ② 删点表 + 停车场 + 手册表清理
    with DELETE.open("a", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        for subj, slug, _place in empty:
            w.writerow([subj, slug, "建点后块过薄/图未提取，写不出材料——按建点闭环规则撤回", ""])
    rows = list(csv.DictReader(MANUAL.open(encoding="utf-8-sig", newline="")))
    empty_keys = {(s, x) for s, x, _ in empty}
    park = [{**r, "reason": "建点后写不出材料，已撤回（撤回记录见 point_delete.csv）"}
            for r in rows if (r["subject"], r["slug"]) in empty_keys]
    keep = [r for r in rows if (r["subject"], r["slug"]) not in empty_keys]
    with PARKED.open("a", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(MANUAL_COLS) + ["reason"])
        w.writerows(park)
    with MANUAL.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(MANUAL_COLS))
        w.writeheader()
        w.writerows(keep)
    print(f"→ point_delete.csv +{len(empty)} 行；new_points_manual 挪走 {len(park)} 行（剩 {len(keep)}）")

    # ③ 章表补行
    if new_units:
        with CHAPTER_SRC.open("a", encoding="utf-8", newline="") as fh:
            w = csv.writer(fh)
            for u in new_units:
                w.writerow([u, "", "", "", "", "keep_per_node", "块池判定来源；归属按节点自身定位串"])
        print(f"→ chapter_by_source.csv +{len(new_units)} 行（decision=keep_per_node）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
