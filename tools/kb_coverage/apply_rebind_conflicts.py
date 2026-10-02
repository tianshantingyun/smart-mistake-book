# -*- coding: utf-8 -*-
"""把归属裁定表的结论写回**判定表**的 `node_slug`（入库前腾位流程第②步）。

## 为什么需要它

侧车里已有材料挂的节点与判定表不一致时（多轮判定的必然产物），`materialize --write` 会因
`slug 撞车` 整批拒绝。裁定人（人或代理）按块正文给出 `decided_node` 后，要把它落到判定表里——
随后 `drop_materials.py` 删掉侧车那条陈旧材料，`materialize --write` 就会按**本轮判定**
（最新正文 + 裁定后的节点）重新落一条。

## 判据（全过才写）

- 裁定表列 `material_slug,chunk_id,sidecar_node,judgment_node,decided_node,...`；
- `decided_node` 必须 ∈ {`sidecar_node`, `judgment_node`}（不接受第三个值——裁定是二选一）；
- 每行必须能在判定表里按 `chunk_id` + 同 slug 规则唯一定位（定位不到就报出来，不静默）；
- 幂等：表里已是 `decided_node` 的行不算改动。

## 用法

    PYTHONPATH=tools python -m kb_coverage.apply_rebind_conflicts --verdicts <csv>            # 报数
    PYTHONPATH=tools python -m kb_coverage.apply_rebind_conflicts --verdicts <csv> --write    # 落盘
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from kb_build import pack_io  # noqa: E402
from kb_coverage.pool_path import POOL_PATH  # noqa: E402

REPO = TOOLS.parent
JUDGMENTS = REPO / "tools" / "kb_coverage" / "tables" / "material_judgments.csv"
HDR = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title", "summary",
       "applicability", "content", "boundary", "note", "midx"]
SUBJ3 = {"MATH": "mat", "PHYSICS": "phy", "CHEMISTRY": "che", "BIOLOGY": "bio"}


def slug_of(subject: str, chunk_id: str, midx: str) -> str:
    return "ext-%s-%s-%s%s" % (SUBJ3[subject], chunk_id.split("-", 1)[0],
                               chunk_id.rsplit("-", 1)[-1], (midx or "").strip())


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--verdicts", type=Path, required=True)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)

    subj = {}
    with POOL_PATH.open(encoding="utf-8") as fh:
        for line in fh:
            if not line.strip():
                continue
            r = __import__("json").loads(line)
            subj[(r["rel_path"], r["chunk_id"])] = r["subject"]

    rows = list(csv.DictReader(JUDGMENTS.open(encoding="utf-8-sig", newline="")))
    index: dict[tuple[str, str], int] = {}
    for i, r in enumerate(rows):
        s = subj.get((r["chunk_rel"], r["chunk_id"]))
        if not s:
            continue
        index[(slug_of(s, r["chunk_id"], r["midx"]), r["chunk_id"])] = i

    changed = problem = 0
    for v in csv.DictReader(args.verdicts.open(encoding="utf-8-sig", newline="")):
        want = (v.get("decided_node") or "").strip()
        if want not in ((v.get("sidecar_node") or "").strip(), (v.get("judgment_node") or "").strip()):
            print(f"   ! decided_node 越界：{v.get('material_slug')} → {want!r}")
            problem += 1
            continue
        i = index.get(((v.get("material_slug") or "").strip(), (v.get("chunk_id") or "").strip()))
        if i is None:
            print(f"   ! 判定表里定位不到：{v.get('material_slug')}")
            problem += 1
            continue
        if rows[i]["node_slug"] == want:
            continue
        print(f"   ~ {v['chunk_id']}: {rows[i]['node_slug'][:38]} → {want[:38]}")
        rows[i]["node_slug"] = want
        changed += 1

    print(f"待改 {changed} 行、异常 {problem} 条"
          f"（{'已写盘' if args.write else '未写盘，加 --write 落盘'}）")
    if args.write and changed:
        # 判定表是 CRLF（与 merge_text_judgments 的默认 lineterminator 一致）——不照抄会把
        # 全表 8 万行的行尾都改掉，diff 噪音淹没真正的 102 处改动。
        with JUDGMENTS.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=HDR, lineterminator="\r\n")
            w.writeheader()
            w.writerows({k: (r.get(k) or "") for k in HDR} for r in rows)
    return 0 if not problem else 1


if __name__ == "__main__":
    raise SystemExit(main())
