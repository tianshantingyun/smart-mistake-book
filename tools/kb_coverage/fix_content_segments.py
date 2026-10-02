# -*- coding: utf-8 -*-
"""判定产物修复：把 MATERIAL 行里超过 4 段的 `content` 合回 ≤4 段。

## 它消灭的失败

协议规定 `content` 1–4 行（行间用字面两字符 `\\n`）。判定员在教材/课件类切片里反复写成 5+ 段
（本会话三轮共 20+ 行，横跨生物/化学/数学四片），`check_slice_verdicts.py` 会把整片判不过，
于是这些片要么重派、要么卡在门外进不了 `materialize`。

合并规则是**确定性**的：每次把**最短的相邻两段**用「；」接起来（若前段已以句读结尾则不重复加），
循环到 ≤4 段。信息不丢、行序不变，只是分段变粗。

## 用法

    PYTHONPATH=tools python tools/kb_coverage/fix_content_segments.py            # 只报数
    PYTHONPATH=tools python tools/kb_coverage/fix_content_segments.py --write    # 就地修
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
MAX_SEG = 4
JOIN = "；"


def shrink(content: str) -> tuple[str, int]:
    """返回 (≤4 段的 content, 合并次数)。"""
    segs = [s for s in content.split("\\n")]
    merges = 0
    while len(segs) > MAX_SEG:
        # 找最短的相邻对（按拼接后长度）
        best = min(range(len(segs) - 1), key=lambda i: len(segs[i]) + len(segs[i + 1]))
        head = segs[best].rstrip()
        tail = segs[best + 1].lstrip()
        if head and head[-1] in "。；;，,、":
            head = head[:-1]
        segs[best:best + 2] = [f"{head}{JOIN}{tail}"]
        merges += 1
    return "\\n".join(segs), merges


def fix_file(path: Path, write: bool) -> int:
    raw = path.read_bytes()
    crlf = b"\r\n" in raw
    with path.open(encoding="utf-8-sig", newline="") as fh:
        rows = list(csv.DictReader(fh))
        fields = rows[0].keys() if rows else []
    changed = 0
    for r in rows:
        if (r.get("action") or "") != "MATERIAL":
            continue
        content = r.get("content") or ""
        if len(content.split("\\n")) <= MAX_SEG:
            continue
        new, merges = shrink(content)
        if len(new.split("\\n")) > MAX_SEG:      # 防御：不该发生
            continue
        r["content"] = new
        changed += 1
        print(f"   {path.name}  {r['chunk_id']}: 合并 {merges} 次 → {len(new.split(chr(92)+'n'))} 段")
    if write and changed:
        with path.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=list(fields), lineterminator="\r\n" if crlf else "\n")
            w.writeheader()
            w.writerows(rows)
    return changed


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--dir", type=Path, default=VERDICTS)
    args = ap.parse_args(argv)
    total = 0
    for path in sorted(args.dir.glob("*.jsonl.csv")):
        n = fix_file(path, args.write)
        total += n
    print(f"{'已修' if args.write else '待修'} {total} 行"
          f"（{'已写盘' if args.write else '未写盘，加 --write 落盘'}）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
