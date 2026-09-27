# -*- coding: utf-8 -*-
"""把"机械门判坏"的页送进重转队列（判据 → 队列的通路）。

## 它消灭的失败

审计判 RETRANSCRIBE 的页有通路（裁定表 → 队列），但**机械门自己判坏的页没有**：
`field_text_defects` / 截断 / 计数闸 判 fail 之后，没有任何环节把它们排进重转——
实测（2026-09-27）收口后剩 35 页 fail（控制字符 26 / 非法转义 4 / `$` 不成对 3 / 计数闸 2），
它们既不在队列里、也没人重转，就那样挂着。

本工具按账本状态写 RETRANSCRIBE 行（evidence 写清**是哪一类**判据判的），交给 S5 重转。
只写"还没有判决"的页；已有判决的页不覆盖（避免和审计判决打架）。

用法：
    PYTHONPATH=tools python -m kb_coverage.queue_defective_pages            # 报告
    PYTHONPATH=tools python -m kb_coverage.queue_defective_pages --write
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

AUDITS = REPO / "tools/kb_coverage/tables/transcript_audits.csv"
COLUMNS = ("subject", "pages", "verdict", "items_min", "items_numbered", "evidence")


def defect_reason(row: dict, text: str) -> str:
    d = gate.field_text_defects(text)
    if d:
        return "机械门判坏：" + "、".join(d)
    if row["truncated"]:
        return "机械门判坏：结构性截断"
    if row["chars"] < 40:
        return "机械门判坏：整页过短"
    written = (row["numbered"] or 0) + (row["formulas"] or 0) + (row["figs"] or 0)
    if row["items_min"] and int(row["items_min"]) > max(written, 1) * 3:
        return "机械门判坏：清点数远超写出量（疑似整块漏）"
    return "机械门判坏：其它"


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)

    rows = tl.build_rows()
    tr = tl.load_transcripts()
    # 注意：**每页都有判决**（审计表覆盖全部页，多数是 ACCEPT），所以"判坏且无判决"永远为空。
    # 真正的条件是"机械门判坏 **且** 当前判决不是 RETRANSCRIBE"——这些页要改判重转。
    todo = [r for r in rows if r["gate"] == "fail" and r["verdict"] != "RETRANSCRIBE"]
    if not todo:
        print("没有'机械门判坏但未判重转'的页")
        return 0
    print(f"要送进队列 {len(todo)} 页：")
    new_rows = []
    for r in todo:
        text = tr.get((r["subject"], r["page"]), {}).get("text", "")
        why = defect_reason(r, text)
        print(f"   {r['subject']} p{r['page']}：{why}")
        new_rows.append({"subject": r["subject"], "pages": str(r["page"]), "verdict": "RETRANSCRIBE",
                         "items_min": r["items_min"], "items_numbered": "", "evidence": why})
    if not args.write:
        print("（未写盘；加 --write 生效）")
        return 0
    with AUDITS.open(encoding="utf-8-sig", newline="") as fh:
        old = list(csv.DictReader(fh))
    target = {(r["subject"], int(r["pages"])) for r in new_rows}

    def expand(text: str) -> list[tuple[str, int]]:
        out: list[tuple[str, int]] = []
        for part in [p.strip() for p in text.split(",") if p.strip()]:
            if part.isdigit():
                out.append((part, int(part)))
            elif "-" in part:
                a, b = part.split("-")
                out += [(str(x), x) for x in range(int(a), int(b) + 1)]
        return out

    out: list[dict] = []
    for r in old:
        subj = (r.get("subject") or "").strip()
        keep = [(t, n) for t, n in expand(r.get("pages") or "") if (subj, n) not in target]
        if keep:
            out.append({**{k: r.get(k, "") for k in COLUMNS},
                        "pages": ",".join(t for t, _n in keep)})
    out += new_rows
    out.sort(key=lambda x: (x["subject"], x["pages"]))
    with AUDITS.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(COLUMNS), lineterminator="\n")
        w.writeheader()
        w.writerows(out)
    print(f"→ 已写入裁定表（{len(old)} → {len(out)} 行；目标页已从原行摘出并改判重转）。"
          f"下一步：apply_transcript_audits --write 重建队列 → S5 重转")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
