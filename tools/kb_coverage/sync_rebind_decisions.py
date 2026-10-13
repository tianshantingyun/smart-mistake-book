# -*- coding: utf-8 -*-
"""把「侧车胜出」的归属裁定写进**判定源**（否则每次 `merge --resync` 都会回退）。

## 它消灭的失败

102 条归属冲突的裁定里，**20 条判侧车节点胜出**。当初只把结论写进了判定表——
而 `merge_text_judgments --resync` 的语义是**源优先**（源改了 → 覆盖表），
于是下一次合并就把这 20 行拉回源里的旧值，与侧车里已绑定的材料**节点不一致** →
`materialize --write` 报 `slug 撞车` 整批拒绝（实测：`ext-bio-d93a11b3d8-009`）。

第二类失败（2026-10-10 批次 8 修）：判定表里**同一个 chunk_id 可以有多行**
（同块多条材料，midx `''`/`b`/`c`…；实测 7 个 chunk_id 出现 2–3 行）。
旧实现把匹配键定为 `chunk_id` 单键：`decided[chunk_id]` 后写覆盖先写，
再把同一个节点应用到源里**同块的所有行** → 不同 midx 的裁定**串行错配**。
现在匹配键是 `(chunk_id, midx)`；老裁定表若无 `midx` 列则按 `midx=''` 处理（向后兼容），
且同键两条冲突裁定直接拒绝写盘。

第三类失败（同批实测 4 行）：**源文件里存的 midx 不可信**。`merge_text_judgments`
合并时会重排 midx——同 `(chunk_rel, chunk_id)` 组内按收集顺序取 `''`/`b`/`c`…，
文件里的旧值不被使用（实测 `450188d41e-013` 单行存 `b` → merge 认 `''`；
`e14aad82a5-320` 三行存 `b/c/d` → merge 认 `''/b/c`）。若按存值配对，
裁定的写入落不到 merge 会拿去比对的那一行，resync 照旧回退。故本工具**按 merge 的同一
口径派生 midx** 再配对（老裁定表的 `midx=''` 即组内首行）。

判据（全过才写）：裁定表里 `decided_node == sidecar_node` 的行；在判定源里按
`(chunk_id, merge 派生 midx)` 唯一定位；只改 `node_slug` 一个字段（其余字段是判定员写的，不动）。

用法：`PYTHONPATH=tools python tools/kb_coverage/sync_rebind_decisions.py [--verdicts PATH] [--write]`
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
# 与 merge_text_judgments 的 suffix 逐字一致（组内第 n 行的派生 midx）。
SUFFIX = ["", "b", "c", "d", "e", "f", "g", "h"]


def detect_eol(path: Path) -> str:
    """文件的行尾风格：含 CRLF → `\\r\\n`，否则 `\\n`（判定源实测 221 CRLF / 13 LF）。

    旧实现写盘硬编码 CRLF：写一个 LF 源会把整个文件行尾翻成 CRLF，diff 噪音淹没真正
    改的那几行（判定源是要逐行复核的产物）。按文件探测、写回原风格。
    """
    return "\r\n" if b"\r\n" in path.read_bytes() else "\n"


def load_decisions(path: Path) -> dict[tuple[str, str], str]:
    """裁定表 → {(chunk_id, midx): decided_node}。

    midx 列缺失（老表）按 `''` 处理。同键两条裁定给不同节点 → 拒绝（不得静默取一个）。
    """
    decided: dict[tuple[str, str], str] = {}
    with path.open(encoding="utf-8-sig", newline="") as fh:
        for r in csv.DictReader(fh):
            if r["decided_node"] == r["sidecar_node"] and r["sidecar_node"] != r["judgment_node"]:
                key = (r["chunk_id"], (r.get("midx") or "").strip())
                prev = decided.get(key)
                if prev is not None and prev != r["decided_node"]:
                    raise SystemExit(f"裁定表同键冲突：{key} → {prev!r} / {r['decided_node']!r}")
                decided[key] = r["decided_node"]
    return decided


def read_sources() -> list[tuple[Path, list[dict], list[str]]]:
    """读全部判定源，并按 merge 的口径给每行派生 midx（组内序号，不认文件里的旧值）。"""
    loaded: list[tuple[Path, list[dict], list[str]]] = []
    seen: dict[tuple[str, str], int] = {}
    for path in sorted(VERDICTS.glob("*.jsonl.csv")):
        with path.open(encoding="utf-8-sig", newline="") as fh:
            rows = list(csv.DictReader(fh))
        mids = []
        for row in rows:
            key = (row["chunk_rel"], row["chunk_id"])
            n = seen.get(key, 0)
            seen[key] = n + 1
            mids.append(SUFFIX[n] if n < len(SUFFIX) else f"x{n}")
        loaded.append((path, rows, mids))
    return loaded


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--verdicts", type=Path, default=None,
                    help="裁定表 CSV（默认：build/agent-input/rebind_conflicts_verdict.csv）")
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)

    verdicts = args.verdicts if args.verdicts is not None else VERDICT_CSV
    decided = load_decisions(verdicts)
    print(f"裁定表 {verdicts.name}：「侧车胜出」键 {len(decided)} 个")

    changed = 0
    touched = []
    unmatched = set(decided)
    for path, rows, mids in read_sources():
        hit = False
        for row, midx in zip(rows, mids):
            key = (row["chunk_id"], midx)
            want = decided.get(key)
            if want is None:
                continue
            unmatched.discard(key)
            if row["action"] == "MATERIAL" and row["node_slug"] != want:
                print(f"   {path.name} {key[0]}[{key[1]}]: {row['node_slug']} → {want}")
                row["node_slug"] = want
                changed += 1
                hit = True
        if hit:
            touched.append(path.name)
            if args.write:
                eol = detect_eol(path)          # 必须在 open("w") 截断之前探测
                with path.open("w", encoding="utf-8", newline="") as fh:
                    w = csv.DictWriter(fh, fieldnames=HDR, lineterminator=eol)
                    w.writeheader()
                    w.writerows({k: (r.get(k) or "") for k in HDR} for r in rows)
    print(f"改 {changed} 行、涉及 {len(touched)} 个文件（{'已写盘' if args.write else '未写盘'}）")
    if unmatched:
        print(f"裁定表键在判定源里无对应行 {len(unmatched)} 个（未写）：")
        for cid, midx in sorted(unmatched)[:20]:
            print(f"   {cid}[{midx}]")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
