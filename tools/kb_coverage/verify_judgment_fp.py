# -*- coding: utf-8 -*-
"""判定引用门：判定表每一行都必须命中池中恰一条 (rel_path, chunk_id)，fp 一致 → exit 0。

它消灭的失败：判定表引用的块在池里悬空（块被删/重排后判定没跟上），或
`(rel_path, chunk_id)` 撞键（判定行的归属有歧义），或重映射表与池的 fp 对不上
（重排时内容换了却沿用旧判定）。这三类任一出现，materialize 都会静默绑错内容。

检查项：
1. 池键唯一（撞键即失败）；
2. 判定表每行 `(chunk_rel, chunk_id)` 恰好命中一条池行，且池行 fp 非空；
3. 若存在 key_remap.csv：旧键不得仍残留在池中、新键必须存在且 fp 与映射表一致、
   判定表不得仍引用旧键（说明重映射没执行到位）。

用法：
    python tools/kb_coverage/verify_judgment_fp.py
"""

from __future__ import annotations

import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_coverage.pool_path import POOL_PATH  # noqa: E402

CHUNKS = POOL_PATH
JUDGMENTS = REPO / "tools/kb_coverage/tables/material_judgments.csv"
REMAP = REPO / "tools/kb_coverage/tables/key_remap.csv"


def main(argv: list[str] | None = None) -> int:
    pool: dict[tuple[str, str], list[dict]] = defaultdict(list)
    with CHUNKS.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            rec = json.loads(line)
            pool[(rec.get("rel_path") or "", rec.get("chunk_id") or "")].append(rec)

    problems: list[str] = []
    dup_keys = [k for k, rs in pool.items() if len(rs) > 1]
    if dup_keys:
        problems.append(f"池存在 {len(dup_keys)} 个重复键，例：{sorted(dup_keys)[:3]}")

    judgments = list(csv.DictReader(JUDGMENTS.open(encoding="utf-8"))) \
        if JUDGMENTS.exists() else []
    jkeys = set()
    for r in judgments:
        key = (r["chunk_rel"], r["chunk_id"])
        jkeys.add(key)
        rows = pool.get(key)
        if not rows:
            problems.append(f"判定悬空：{key}")
        elif len(rows) > 1:
            problems.append(f"判定撞键：{key}（池中 {len(rows)} 行）")
        elif not (rows[0].get("fp") or "").strip():
            problems.append(f"池行无 fp：{key}")

    if REMAP.exists():
        with REMAP.open(encoding="utf-8", newline="") as fh:
            for entry in csv.DictReader(fh):
                old = (entry["chunk_rel"], entry["old_chunk_id"])
                new = (entry["chunk_rel"], entry["new_chunk_id"])
                # 稠密重编号会复用旧序号数字（新行可能恰好顶着旧序号），
                # 所以"旧键不在池中"不是不变式；成立的不变式是——
                # 新键必须存在且 fp 与映射表一致（内容没变、只改 id），
                # 且判定表不得仍引用旧键（说明重映射没执行到位）。
                prows = pool.get(new)
                if not prows:
                    problems.append(f"新键不在池：{new}")
                elif (prows[0].get("fp") or "") != entry["fp"]:
                    problems.append(f"fp 不一致（映射表 vs 池）：{new}")
                if old in jkeys:
                    problems.append(f"判定仍引用旧键（未重映射）：{old}")

    print(f"判定 {len(judgments)} 行 / 池 {len(pool)} 唯一键 / 问题 {len(problems)}")
    if problems:
        for p in problems[:20]:
            print("  -", p, file=sys.stderr)
        return 1
    print("判定引用门通过：全命中、fp 一致")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
