# -*- coding: utf-8 -*-
"""按 slug 从 staging 侧车里删除材料（入库前的"腾位"操作）。

## 它消灭的失败

`materialize --write` 对同一 slug **只认一个节点**：侧车里那条材料挂的节点，必须与判定表里
同一块的目标节点一致，否则整批拒绝（`slug 撞车`）。而块池判定是**多轮**的——同一个块可能被
后一轮重判、挂到更准的节点上，侧车里却留着上一轮那条。实测（2026-10-01 全量入库前）：102 条
这样的陈旧材料把 38,492 条材料的整批写入卡死。

**为什么不改绑了事**：改绑只能改节点、改不了材料正文——陈旧那条的正文是上一轮写的，
本轮重判的正文才是最新（"修正向前"）。所以流程是：**删掉陈旧那条 → 改判定行的节点（如需）
→ 重跑 `materialize --write`**，让它按本轮判定重新落一条（同 slug、新正文、裁定后的节点）。

## 安全边界（全过才写）

- 只认 `build/kb-staging/` 下的侧车（经 `pack_io.sidecar_paths()`）——**永不碰成品目录**；
- 只删清单里点名的 slug，删不到就报出来（不静默）；
- 空删/找不到文件一律不改盘；`--write` 才落盘，默认只报数。

## 用法

    PYTHONPATH=tools python -m kb_build.drop_materials --slugs a,b,c          # 报数
    PYTHONPATH=tools python -m kb_build.drop_materials --slugs-file x.csv --write
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from kb_build import pack_io  # noqa: E402


def load_slugs(args) -> list[str]:
    slugs: list[str] = []
    if args.slugs:
        slugs += [s.strip() for s in args.slugs.split(",") if s.strip()]
    if args.slugs_file:
        with open(args.slugs_file, encoding="utf-8-sig", newline="") as fh:
            for row in csv.DictReader(fh):
                for key in ("material_slug", "slug"):
                    if row.get(key):
                        slugs.append(row[key].strip())
                        break
    return sorted(set(slugs))


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--slugs", default="", help="逗号分隔的 material slug")
    ap.add_argument("--slugs-file", type=Path, default=None,
                    help="CSV，取 material_slug/slug 列")
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)

    want = set(load_slugs(args))
    if not want:
        print("清单为空，不动盘。")
        return 0
    dropped: list[str] = []
    per_file: dict[str, int] = {}
    for sp in pack_io.sidecar_paths():
        if not sp.exists():
            continue
        doc = pack_io.load_json(sp)
        mats = doc.get("materials") or []
        keep = [m for m in mats if m.get("slug") not in want]
        hit = len(mats) - len(keep)
        if not hit:
            continue
        for m in mats:
            if m.get("slug") in want:
                dropped.append(m["slug"])
        per_file[sp.name] = hit
        if args.write:
            doc["materials"] = keep
            # 用 pack_io 的规范排版（indent=1 / 不转义中文 / LF）——照抄不齐会把整卷重排，
            # staging_freeze 的指纹比对与后续 diff 会被格式噪音淹没。
            pack_io.dump_json(doc, sp)
    miss = sorted(want - set(dropped))
    print(f"清单 {len(want)} 条：命中 {len(dropped)}、未命中 {len(miss)}"
          f"（{'已写盘' if args.write else '未写盘，加 --write 落盘'}）")
    for name, n in sorted(per_file.items()):
        print(f"   {name}: -{n}")
    if miss:
        print("未命中（侧车里没有这些 slug）：")
        for s in miss[:20]:
            print(f"   ! {s}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
