# -*- coding: utf-8 -*-
"""P0.3 判定表对账：死节点重指合并后继 + type 3 值归一化。

## 它消灭的失败

1. `point_merge.csv` 合并掉的旧 slug 仍残留在判定表（12 行）→ `materialize --dry-run`
   报「节点不存在」并拒绝整批写盘。本模块把这 12 行重指到合并后的后继 slug。
2. 判定表 698 行 MATERIAL 用了协议禁用 type（冻结协议 §1 只许 3 值）→ 移入重判队列
   `tables/rejudge_queue.csv`，原行 action 改 SKIP，materialize 不再把它们当材料。

## 规则（全部机械可验）

- **死节点重指**：action=MATERIAL 且 `node_slug` 不在同科成品包、且
  `(subject, node_slug)` 命中 `point_merge.csv` 的 merged_slug → 重指 survivor_slug；
  survivor 必须存在于同科成品包（`build/kb-staging/moe-2025-four-subjects-v1.json`），
  否则列为阻塞项拒绝写盘（按协议升级提问）。
- **type 归一化**：action=MATERIAL 且 type ∉ {CONCEPT_EXPLANATION, METHOD_MODEL,
  MISCONCEPTION_GUIDE} → 该行追加进重判队列（保留 chunk_rel/chunk_id 原键），
  原行 action=SKIP、note=`已入重判队列(type=<原 type>)`。
- SKIP 行一律不动（含空 type 的 SKIP 行）。
- 幂等：重跑 0 改写；重判队列按行身份 (chunk_rel, chunk_id, midx) 去重追加。
  （一块可出多条材料，同 (chunk_rel, chunk_id) 下 midx 区分行，不能按块键去重。）
- 块不存在（chunk 不在生效块池）不在本次对账范围，留给 materialize 报告。

用法：
    PYTHONPATH=tools python tools/kb_coverage/reconcile_judgments.py          # 报告（不写盘）
    PYTHONPATH=tools python tools/kb_coverage/reconcile_judgments.py --write  # 落盘
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import pack_io  # noqa: E402
from kb_coverage.office_extract import load_chunks  # noqa: E402

JUDGMENTS = REPO / "tools" / "kb_coverage" / "tables" / "material_judgments.csv"
MERGE = REPO / "tools" / "kb_build" / "tables" / "point_merge.csv"
QUEUE = REPO / "tools" / "kb_coverage" / "tables" / "rejudge_queue.csv"
COLUMNS = ("chunk_rel", "chunk_id", "action", "node_slug", "type", "title",
           "summary", "applicability", "content", "boundary", "note", "midx")
QUEUE_COLUMNS = ("chunk_rel", "chunk_id", "midx", "node_slug", "type", "note")
ALLOWED_TYPES = {"CONCEPT_EXPLANATION", "METHOD_MODEL", "MISCONCEPTION_GUIDE"}


def _row_key(r: dict) -> tuple[str, str, str]:
    """判定行身份：块键 + midx（一块可出多条材料）。"""
    return (r["chunk_rel"], r["chunk_id"], r.get("midx") or "")


def load_judgments() -> list[dict]:
    if not JUDGMENTS.exists():
        return []
    with JUDGMENTS.open(encoding="utf-8", newline="") as fh:
        rows = list(csv.DictReader(fh))
    if rows:
        missing = [c for c in COLUMNS if c not in rows[0]]
        if missing:
            raise ValueError(f"判定表缺列 {missing}")
    return rows


def load_merge() -> dict[tuple[str, str], str]:
    out: dict[tuple[str, str], str] = {}
    with MERGE.open(encoding="utf-8-sig", newline="") as fh:
        for r in csv.DictReader(fh):
            out[(r["subject"], r["merged_slug"])] = r["survivor_slug"]
    return out


def plan(judgments: list[dict], chunks: dict, nodes: dict[str, set[str]],
         merge: dict[tuple[str, str], str]) -> dict:
    """dry-run：把判定表分成 重指 / 移重判队列 / 阻塞 三类，不写盘。

    chunks: {(chunk_rel, chunk_id): chunk}；nodes: {subject: set(slug)}；
    merge: {(subject, merged_slug): survivor_slug}。
    """
    redirects: list[tuple[dict, str]] = []
    moves: list[dict] = []
    problems: list[str] = []
    for r in judgments:
        key = (r["chunk_rel"], r["chunk_id"])
        ch = chunks.get(key)
        if ch is None:
            continue  # 块不存在留给 materialize 报
        subject = ch["subject"]
        if r["action"] == "SKIP":
            continue
        if r["action"] != "MATERIAL":
            problems.append(f"非法 action {r['action']}（{key}）")
            continue
        if r["type"] not in ALLOWED_TYPES:
            moves.append(r)  # type 先于 slug：坏 type 行直接进重判队列，不再当材料
            continue
        if r["node_slug"] in nodes[subject]:
            continue
        mkey = (subject, r["node_slug"])
        if mkey not in merge:
            problems.append(f"节点不存在且无合并后继: {subject}/{r['node_slug']}（{key}）")
            continue
        survivor = merge[mkey]
        if survivor not in nodes[subject]:
            problems.append(f"合并后继不在成品包: {subject}/{survivor}（{key}）")
            continue
        redirects.append((r, survivor))
    return {"redirects": redirects, "moves": moves, "problems": problems}


def apply(pl: dict, judgments: list[dict],
          judgments_path: Path = JUDGMENTS, queue_path: Path = QUEUE) -> dict:
    """落盘：判定表整体重写（行序不变，只改命中的行）+ 重判队列去重追加。"""
    if pl["problems"]:
        raise ValueError("对账计划有阻塞项，拒绝写盘：\n  " + "\n  ".join(pl["problems"][:10]))
    redirect_by_key = {_row_key(r): survivor for r, survivor in pl["redirects"]}
    move_keys = {_row_key(r) for r in pl["moves"]}
    moved_rows = [r for r in judgments if _row_key(r) in move_keys]
    for r in judgments:
        key = _row_key(r)
        if key in redirect_by_key:
            r["node_slug"] = redirect_by_key[key]
        if key in move_keys:
            r["action"] = "SKIP"
            r["note"] = f"已入重判队列(type={r['type']})"
    with judgments_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=COLUMNS)
        w.writeheader()
        w.writerows(judgments)
    # 重判队列：按行身份 (chunk_rel, chunk_id, midx) 去重追加（幂等）
    existing: set[tuple[str, str, str]] = set()
    if queue_path.exists() and queue_path.stat().st_size > 0:
        with queue_path.open(encoding="utf-8", newline="") as fh:
            existing = {_row_key(r) for r in csv.DictReader(fh)}
    appended = 0
    with queue_path.open("a", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=QUEUE_COLUMNS)
        if queue_path.stat().st_size == 0:
            w.writeheader()
        for r in sorted(moved_rows, key=lambda x: (x["chunk_rel"], x["chunk_id"], x["midx"])):
            if _row_key(r) in existing:
                continue
            w.writerow({"chunk_rel": r["chunk_rel"], "chunk_id": r["chunk_id"],
                        "midx": r.get("midx") or "", "node_slug": r["node_slug"],
                        "type": r["type"],
                        "note": f"type 不合 3 值规则（协议 §1 禁用 {r['type']}），待重判"})
            appended += 1
    return {"redirected": len(pl["redirects"]), "moved": len(moved_rows),
            "queue_appended": appended}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)
    judgments = load_judgments()
    chunks = {(c["rel_path"], c["chunk_id"]): c for c in load_chunks()}
    pack = pack_io.load_json(pack_io.pack_path())
    nodes = {s["subject"]: {k["slug"] for t in s["topics"] for k in t["knowledgePoints"]}
             for s in pack["subjects"]}
    merge = load_merge()
    pl = plan(judgments, chunks, nodes, merge)
    print(f"判定 {len(judgments)} 行：死节点重指 {len(pl['redirects'])}，"
          f"移重判队列 {len(pl['moves'])}，阻塞 {len(pl['problems'])}")
    for p in pl["problems"][:10]:
        print("  !", p)
    if pl["problems"]:
        print("有阻塞项（后继缺失/跨科/非法 action），拒绝写盘——按协议升级提问")
        return 1
    if not args.write:
        print("（未写盘；加 --write 生效）")
        return 0
    stats = apply(pl, judgments)
    print(f"→ 死节点重指 {stats['redirected']} 行；移入重判队列 {stats['moved']} 行"
          f"（本次追加 {stats['queue_appended']}）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
