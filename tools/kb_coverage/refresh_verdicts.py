# -*- coding: utf-8 -*-
"""重转成功后撤回旧审计判决（把 RETRANSCRIBE 改成 ACCEPT，并写清依据）。

## 它消灭的失败

审计判 RETRANSCRIBE → 账本按判决**永远算 fail**、入块库照旧拦它——**没有任何环节会在重转成功后
撤回判决**。实测（2026-09-27）：115 页判重转里，重转过的页仍背着旧判决。

## 判据（严格：两个条件都得满足，缺一不改）

1. **确实重转过**：该页的 fill 稿 mtime **晚于**裁定表的 mtime（判决是在先稿上做的）；
2. **机械门复判通过**：无文本残迹、非截断、字数 ≥40、计数闸过。

只满足"机械门过"不算——机械门看不出"整块缺失/符号误读"这类实质缺陷，拿它当放行依据
会把审计查实的坏页洗成 ACCEPT（这正是 2026-09-27 那版 S5 跳过逻辑的错）。

改写的行 evidence 写明依据与"未再换人审计"，并在报告里逐页打印——**不静默**。

用法：
    PYTHONPATH=tools python -m kb_coverage.refresh_verdicts            # 报告
    PYTHONPATH=tools python -m kb_coverage.refresh_verdicts --write    # 撤回（覆盖裁定表）
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import gate  # noqa: E402
from kb_coverage import transcription_ledger as tl  # noqa: E402

FILL = REPO / "knowledge-production/2027-53-fill"
AUDITS = REPO / "tools/kb_coverage/tables/transcript_audits.csv"
COLUMNS = ("subject", "pages", "verdict", "items_min", "items_numbered", "evidence")
EVIDENCE = "重转后机械门复判通过（稿晚于裁定表；未再换人审计）"


def mechanical_ok(row: dict, text: str) -> bool:
    if gate.field_text_defects(text):
        return False
    if row["truncated"] or row["chars"] < 40:
        return False
    written = (row["numbered"] or 0) + (row["formulas"] or 0) + (row["figs"] or 0)
    if row["items_min"] and int(row["items_min"]) > max(written, 1) * 3:
        return False
    return True


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)
    if not AUDITS.exists():
        print(f"没有裁定表：{AUDITS}")
        return 2
    audit_mtime = AUDITS.stat().st_mtime
    rows = tl.build_rows()
    tr = tl.load_transcripts()
    flips: list[tuple[str, int, str]] = []
    for r in rows:
        if r["verdict"] != "RETRANSCRIBE":
            continue
        f = FILL / r["subject"] / f"p{r['page']:04d}.jsonl"
        if not f.exists() or f.stat().st_mtime <= audit_mtime:
            continue                                   # 没重转过（稿不比判决新）
        text = tr.get((r["subject"], r["page"]), {}).get("text", "")
        if mechanical_ok(r, text):
            flips.append((r["subject"], r["page"], f"稿 {int(f.stat().st_mtime - audit_mtime)} 秒后于裁定表"))
    if not flips:
        print("没有可撤回的判决（没有'重转过且机械面通过'的页）")
        return 0
    print(f"可撤回判决 {len(flips)} 页：")
    for s, p, why in flips[:20]:
        print(f"   {s} p{p}（{why}）")
    if len(flips) > 20:
        print(f"   …（还有 {len(flips) - 20} 页）")
    if not args.write:
        print("（未写盘；加 --write 生效）")
        return 0

    # 把裁定表里覆盖这些页的行改成 ACCEPT（同页只留一行：直接按页展开重写）
    with AUDITS.open(encoding="utf-8-sig", newline="") as fh:
        old = list(csv.DictReader(fh))
    flip_keys = {(s, p) for s, p, _ in flips}
    out: list[dict] = []
    for r in old:
        subj = (r.get("subject") or "").strip()
        pages = [p.strip() for p in (r.get("pages") or "").split(",") if p.strip()]
        nums = []
        for part in pages:
            if part.isdigit():
                nums.append((part, int(part)))
            elif "-" in part:
                a, b = part.split("-")
                nums += [(str(x), x) for x in range(int(a), int(b) + 1)]
        keep = [(txt, n) for txt, n in nums if (subj, n) not in flip_keys]
        flipped = [n for _txt, n in nums if (subj, n) in flip_keys]
        if flip := flipped:
            for n in flip:
                out.append({"subject": subj, "pages": str(n), "verdict": "ACCEPT",
                            "items_min": r.get("items_min", ""), "items_numbered": r.get("items_numbered", ""),
                            "evidence": EVIDENCE})
        if keep:
            out.append({**{k: r.get(k, "") for k in COLUMNS},
                        "pages": ",".join(txt for txt, _n in keep)})
    out.sort(key=lambda x: (x["subject"], x["pages"]))
    with AUDITS.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(COLUMNS), lineterminator="\n")
        w.writeheader()
        w.writerows(out)
    print(f"→ 已撤回 {len(flips)} 页判决（{AUDITS}，现有 {len(out)} 行）")
    print("下一步：apply_transcript_audits --write 重建队列 → 并入 → 账本 → 两道门")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
