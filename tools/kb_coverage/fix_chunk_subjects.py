# -*- coding: utf-8 -*-
"""块池科目标注修复：把 `UNASSIGNED` 块按 `extraction_state._subject` 重定科目。

## 它消灭的失败

池里 1,469 块的 `subject` 是 `UNASSIGNED`（考点词里撞见别的科目词被判成"全科卷"）。
`materialize.py` 第 144–146 行对非四科块直接 `非四科块` 硬错误 → 只要判定表里有这些块的
MATERIAL 行，`materialize --write` 整批不写。修复前必须证明**不动任何已定科目的块**
（本工具 `--check` 就是这条证明）。

两处都要改，因为判定的两侧各自读一份：
- **池**（`pool_path.POOL_PATH`）：`materialize` 与判定员派单的块来源；
- **切片**（`tables/judgment_slices/*.jsonl`）：`check_slice_verdicts.py` 按切片的
  `subject` 查节点集，切片说 UNASSIGNED 就会把合法的 CHEMISTRY 绑定判成"节点不存在"。

## 用法

    PYTHONPATH=tools python tools/kb_coverage/fix_chunk_subjects.py --check   # 只报数，不动盘
    PYTHONPATH=tools python tools/kb_coverage/fix_chunk_subjects.py --write   # 池 + 切片就地重写
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from kb_coverage.extraction_state import _subject  # noqa: E402
from kb_coverage.pool_path import POOL_PATH  # noqa: E402

SLICES = TOOLS / "kb_coverage" / "tables" / "judgment_slices"


def _plan_pool() -> tuple[list[str], int, int]:
    """返回 (输出行, 改动数, 总行数)。行按原样保留字段顺序与其余内容。"""
    out, changed, total = [], 0, 0
    with POOL_PATH.open(encoding="utf-8") as fh:
        for line in fh:
            if not line.strip():
                continue
            total += 1
            rec = json.loads(line)
            new = _subject(rec["rel_path"])
            if new != rec.get("subject"):
                rec["subject"] = new
                changed += 1
            out.append(json.dumps(rec, ensure_ascii=False))
    return out, changed, total


def _plan_slices(live: dict[tuple[str, str], str]) -> list[tuple[Path, list[str], int]]:
    """按**池**（权威）回填切片的 subject；键对齐 (chunk_rel, chunk_id)。"""
    plans = []
    for path in sorted(SLICES.glob("*.jsonl")):
        lines, changed = [], 0
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            rec = json.loads(line)
            subj = live.get((rec["chunk_rel"], rec["chunk_id"]))
            if subj and subj != rec.get("subject"):
                rec["subject"] = subj
                changed += 1
            lines.append(json.dumps(rec, ensure_ascii=False))
        plans.append((path, lines, changed))
    return plans


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true", help="就地重写池与切片（默认只报数）")
    args = ap.parse_args(argv)

    out, changed, total = _plan_pool()
    live = {}
    for line in out:
        rec = json.loads(line)
        live[(rec["rel_path"], rec["chunk_id"])] = rec["subject"]
    plans = _plan_slices(live)
    slice_changed = sum(c for _p, _l, c in plans)
    print(f"池 {POOL_PATH.name}：{total} 行、待改 {changed}")
    print(f"切片 {len(plans)} 个文件、待改行 {slice_changed}")

    if not args.write:
        return 0

    # 池：新文件 + 原子替换（避免半截文件）。写入前先烘到内存，任何异常都不会动盘。
    tmp = POOL_PATH.with_suffix(".fixing.jsonl")
    with tmp.open("w", encoding="utf-8", newline="\n") as fh:
        for line in out:
            fh.write(line + "\n")
    tmp.replace(POOL_PATH)
    for path, lines, _c in plans:
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("已写入。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
