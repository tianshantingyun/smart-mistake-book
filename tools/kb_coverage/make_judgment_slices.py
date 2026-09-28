# -*- coding: utf-8 -*-
"""把"仍需模型判定"的块按来源切成判定切片 + 机械 SKIP 清单。

## 它消灭的失败

封包轮要对**全部未判定块**逐块送模型判定。整批送没有边界：块池 18 万行，
代理上下文装不下、失败难续传，而且本可机械判定的块（题目/目录/版权/过短/
重复）也跟着浪费判定量。本工具复用 `kb_coverage.prescreen_chunks` 的**同一份**
分流实现（`iter_classified`），只对 JUDGE 类切块、按来源分组、每片 ≤ N 块，
文件名 `来源__序号.jsonl` 稳定幂等（重跑产物一致）；机械可判的 SKIP 同步落
`tables/mechanical_skips.csv`（schema 同判定表，action=SKIP，note=机械判据理由）。

已判定键 = 判定表键 − 重判队列键（重判队列键一律视为未判定，待重判）。

块池路径经 `kb_coverage.pool_path.POOL_PATH`（唯一权威源；当前生效名是
`extracted_chunks.rekeyed.jsonl`，旧名 `extracted_chunks.jsonl` 是 P0.2 重排前
遗留、句柄被锁暂不可动，见 pool_path.py）。

用法：
    PYTHONPATH=tools python -m kb_coverage.make_judgment_slices          # 全量写盘
    PYTHONPATH=tools python -m kb_coverage.make_judgment_slices --pilot  # 每来源只出第 1 片
    PYTHONPATH=tools python -m kb_coverage.make_judgment_slices --list   # 只打印切片路径清单，不写盘
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_coverage import prescreen_chunks as pc  # noqa: E402

# 块池/判定表/重判队列的路径一律用 prescreen_chunks 的常量（单一权威源，见 pool_path.py）。
SLICES_DIR = REPO / "tools/kb_coverage/tables/judgment_slices"
MECH_SKIPS = REPO / "tools/kb_coverage/tables/mechanical_skips.csv"

# 与判定表同序的列（mechanical_skips.csv 的 schema）。
JUDGMENT_COLS = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title",
                 "summary", "applicability", "content", "boundary", "note", "midx"]
_SLICE_COLS = ("chunk_rel", "chunk_id", "heading", "text", "subject", "fp")
_FN_BAD = re.compile(r'[\\/:*?"<>|\x00-\x1f]')


def slice_filename_source(src: str) -> str:
    """来源段 → 文件名安全形式。当前 14 个来源段均无需变换，仅防御非法字符。"""
    return _FN_BAD.sub("_", src).rstrip(" .") or "unknown"


def slice_row(rec: dict) -> dict:
    """池记录 → 切片行（行 schema：chunk_rel,chunk_id,heading,text,subject,fp）。"""
    out: dict = {}
    for col in _SLICE_COLS:
        key = "rel_path" if col == "chunk_rel" else col
        out[col] = rec.get(key) or ""
    return out


def classify_pool() -> tuple[dict[str, list[dict]], list[dict]]:
    """读块池分流，返回 (by_source JUDGE 行, SKIP 清单行)。

    顺序 = 块池文件顺序；判定表键 − 重判队列键视为已判定（跳过），
    其余键走 prescreen 判据（含指纹/重复键去重），只保留 JUDGE 类行。
    """
    judged = pc.load_judged_keys()
    rejudge = pc.load_rejudge_keys()
    effective_judged = judged - rejudge
    by_source: dict[str, list[dict]] = defaultdict(list)
    skips: list[dict] = []
    for rec, verdict, why in pc.iter_classified(pc.CHUNKS, effective_judged):
        if verdict == pc.JUDGED:
            continue
        if verdict == "JUDGE":
            src = (rec.get("rel_path") or "?").split("/")[0]
            by_source[src].append(slice_row(rec))
        else:
            skips.append({
                "chunk_rel": rec.get("rel_path") or "",
                "chunk_id": rec.get("chunk_id") or "",
                "action": "SKIP",
                "node_slug": "", "type": "", "title": "", "summary": "",
                "applicability": "", "content": "", "boundary": "",
                "note": why or verdict, "midx": "",
            })
    return dict(by_source), skips


def plan_slices(by_source: dict[str, list[dict]], size: int, pilot: bool) -> list[tuple[Path, list[dict]]]:
    """按来源分组、每片 ≤ size 块；pilot 时每来源只出第 1 片。文件名稳定幂等。"""
    plan: list[tuple[Path, list[dict]]] = []
    for src, rows in by_source.items():
        fsrc = slice_filename_source(src)
        for i in range(0, len(rows), size):
            plan.append((SLICES_DIR / f"{fsrc}__{i // size + 1}.jsonl", rows[i:i + size]))
            if pilot:
                break
    return plan


def write_slices(plan: list[tuple[Path, list[dict]]]) -> None:
    """整目录重写（先清旧 *.jsonl 再逐片临时文件原子替换）→ 重跑产物一致。"""
    if SLICES_DIR.exists():
        for old in SLICES_DIR.glob("*.jsonl"):
            old.unlink()
    SLICES_DIR.mkdir(parents=True, exist_ok=True)
    for path, rows in plan:
        text = "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_text(text, encoding="utf-8")
        os.replace(tmp, path)


def write_skips(skips: list[dict]) -> None:
    MECH_SKIPS.parent.mkdir(parents=True, exist_ok=True)
    tmp = MECH_SKIPS.with_suffix(MECH_SKIPS.suffix + ".tmp")
    with tmp.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=JUDGMENT_COLS)
        writer.writeheader()
        writer.writerows(skips)
    os.replace(tmp, MECH_SKIPS)


def _display_path(path: Path) -> str:
    """输出用路径：仓库内 → 相对路径（/ 分隔）；仓库外（测试打桩）→ 绝对路径。"""
    try:
        return path.relative_to(REPO).as_posix()
    except ValueError:
        return path.as_posix()


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--size", type=int, default=300, help="每片最大块数（默认 300）")
    ap.add_argument("--pilot", action="store_true", help="每来源只出第 1 片")
    ap.add_argument("--list", action="store_true", help="只打印切片路径清单（一行一个），不写盘")
    args = ap.parse_args(argv)
    if args.size < 1:
        ap.error("--size 必须 ≥ 1")

    by_source, skips = classify_pool()
    plan = plan_slices(by_source, args.size, args.pilot)
    if args.list:
        for path, _rows in plan:
            print(_display_path(path))
        return 0
    write_slices(plan)
    write_skips(skips)
    total = sum(len(rows) for _path, rows in plan)
    print(f"来源 {len(by_source)} 个；JUDGE 块 {total} 个 → 切片 {len(plan)} 片（--size {args.size}"
          f"{'，--pilot 每来源 1 片' if args.pilot else ''}）")
    print(f"机械 SKIP {len(skips)} 行 → {_display_path(MECH_SKIPS)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
