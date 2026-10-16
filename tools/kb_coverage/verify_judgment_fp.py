# -*- coding: utf-8 -*-
"""判定引用门：判定表每一行都必须命中池中恰一条 (rel_path, chunk_id)，fp 一致 → exit 0。

它消灭的失败：判定表引用的块在池里悬空（块被删/重排后判定没跟上），或
`(rel_path, chunk_id)` 撞键（判定行的归属有歧义），或重映射表与池的 fp 对不上
（重排时内容换了却沿用旧判定）。这三类任一出现，materialize 都会静默绑错内容。

检查项：
1. 池键唯一（撞键即失败）；
2. 判定表每行 `(chunk_rel, chunk_id)` 恰好命中一条池行，且池行 fp 非空；
3. 若存在 key_remap.csv：新键必须存在且 fp 与映射表一致；另设 **fp 锚定迁移窗守卫**——
   仅当 `old != new`、判定表仍引用 old、池中仍有 old、条目 fp 非空且
   `池[old].fp == 条目.fp`（旧 id 仍持**同一内容**）时报「疑似未重映射」，交人工裁决，
   不自动处置。

    v1 的判据是「`old != new` 且判定引用 old ⇒ 未重映射」。它把两个 id 空间当一个：
    key_remap 的 old 是 P0.2 重排**之前**的稠密编号（`sha256(rel)[:10]-NNN`），判定行的
    键是重排**之后**的现空间 id，两者同前缀同位数、稠密重叠——新行会顶掉别的块的旧序号，
    于是"判定引用了某个 old 键"证明不了任何未重映射。实树实测（2026-10-16）：v1 命中
    6,011 条，逐条核 fp 后全部是复用类（`池[old].fp != 条目.fp`）；单例
    `5c62da9e03-1133`（化学 53，池中 fp 4644e3fc5d68）对应映射条目 1133→1446
    （fp 0151fd444c17，另一块）——两条 fp 不同即证判定行指现空间那块，不是被搬走的那块。
    真断链（重映射已执行、old 已不在池）本就由检查项 2 的「判定悬空」拦住。
    fp 相等才是唯一可靠的迁移窗特征（旧 id 上仍持同一内容），且仍可能是"内容相同的
    另一行占了旧序号"，故只报「疑似」，不自动处置。

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
from kb_coverage import judgments  # noqa: E402
from kb_coverage.pool_path import POOL_PATH  # noqa: E402

CHUNKS = POOL_PATH
# 判定表是**分片目录**（按科目的 5 片，见 kb_coverage/judgments.py）。
JUDGMENTS = judgments.parts_dir()
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

    judgments_rows = judgments.load_all(JUDGMENTS) if JUDGMENTS.exists() else []
    jkeys = set()
    for r in judgments_rows:
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
                # 稠密重编号会复用旧序号数字（新行可能恰好顶着别的块的旧序号），
                # 所以"旧键出现在映射表里"不是判定未重映射的证据；不变式是——
                # 新键必须存在且 fp 与映射表一致（内容没变、只改 id）。
                prows = pool.get(new)
                if not prows:
                    problems.append(f"新键不在池：{new}")
                elif (prows[0].get("fp") or "") != entry["fp"]:
                    problems.append(f"fp 不一致（映射表 vs 池）：{new}")
                # fp 锚定迁移窗守卫（v2）：只有"旧 id 仍持**同一内容**"才谈得上
                # "判定没跟着改号"。fp 不等 = 旧序号已被他块复用（真断链会由
                # 检查项 2 的「判定悬空」拦住），不报。
                # 恒等条目（old == new）对 fp 相等子句恒真，必须由 old != new 排除：
                # 池里那一条就是它自己，判定引用它本就是对的（v1 实测假阳性 2,109 条）。
                if entry["old_chunk_id"] == entry["new_chunk_id"] or old not in jkeys:
                    continue
                orows = pool.get(old)
                old_fp = (orows[0].get("fp") or "") if orows else ""
                if (entry["fp"] or "").strip() and old_fp == entry["fp"]:
                    problems.append(f"疑似未重映射（旧键仍持旧内容）：{old}")

    print(f"判定 {len(judgments_rows)} 行 / 池 {len(pool)} 唯一键 / 问题 {len(problems)}")
    if problems:
        for p in problems[:20]:
            print("  -", p, file=sys.stderr)
        return 1
    print("判定引用门通过：全命中、fp 一致")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
