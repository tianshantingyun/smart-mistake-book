# -*- coding: utf-8 -*-
"""把「侧车胜出」的归属裁定写进**判定源**（否则每次 `merge --resync` 都会回退）。

## 它消灭的失败

102 条归属冲突的裁定里，**20 条判侧车节点胜出**。当初只把结论写进了判定表——
而 `merge_text_judgments --resync` 的语义是**源优先**（源改了 → 覆盖表），
于是下一次合并就把这 20 行拉回源里的旧值，与侧车里已绑定的材料**节点不一致** →
`materialize --write` 报 `slug 撞车` 整批拒绝（实测：`ext-bio-d93a11b3d8-009`）。

判据（全过才写）：裁定表里 `decided_node == sidecar_node` 的行；在判定源里按
`(chunk_id, midx)` 唯一定位；只改 `node_slug` 一个字段（其余字段是判定员写的，不动）。

用法：`PYTHONPATH=tools python tools/kb_coverage/sync_rebind_decisions.py [--write]`
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
VERDICTS = REPO / "knowledge-production" / "judgment-verdicts"
VERDICT_CSV = REPO / "build" / "agent-input" / "rebind_conflicts_verdict.csv"
HDR = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title", "summary",
       "applicability", "content", "boundary", "note", "midx"]


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)

    decided = {}
    for r in csv.DictReader(VERDICT_CSV.open(encoding="utf-8-sig", newline="")):
        if r["decided_node"] == r["sidecar_node"] and r["sidecar_node"] != r["judgment_node"]:
            decided[r["chunk_id"]] = r["decided_node"]
    print(f"裁定表里「侧车胜出」的块 {len(decided)} 个")

    changed = 0
    touched = []
    for path in sorted(VERDICTS.glob("*.jsonl.csv")):
        rows = list(csv.DictReader(path.open(encoding="utf-8-sig", newline="")))
        hit = False
        for row in rows:
            want = decided.get(row["chunk_id"])
            if want and row["action"] == "MATERIAL" and row["node_slug"] != want:
                print(f"   {row['chunk_id']}[{row['midx']}]: {row['node_slug'][:30]} → {want[:30]}")
                row["node_slug"] = want
                changed += 1
                hit = True
        if hit:
            touched.append(path.name)
            if args.write:
                with path.open("w", encoding="utf-8", newline="") as fh:
                    w = csv.DictWriter(fh, fieldnames=HDR, lineterminator="\r\n")
                    w.writeheader()
                    w.writerows({k: (r.get(k) or "") for k in HDR} for r in rows)
    print(f"改 {changed} 行、涉及 {len(touched)} 个文件（{'已写盘' if args.write else '未写盘'}）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
