# -*- coding: utf-8 -*-
"""校验单批判定原始文件（verdicts_raw/batch-NN.tsv）并输出问题行。

用法：python -I -X utf8 check_raw.py verdicts_raw/batch-01.tsv
检查：行集与 judge_input/batch-NN.json 1:1、verdict 合法、target 存在且同科且≠现绑、
      evidence_quote 逐字子串（对材料五字段任一）、confidence 合法、REBIND/NONE 有 note。
"""

from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
HEADER = ["material_slug", "verdict", "target_node_slug", "confidence", "evidence_quote", "note"]


def load_nodes() -> dict[str, dict]:
    nodes = {}
    for line in (HERE / "node_index.tsv").read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        subject, topic, slug, name, kind, boundary = line.split("\t")
        nodes[slug] = {"subject": subject, "name": name}
    return nodes


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("用法：check_raw.py verdicts_raw/batch-NN.tsv")
    path = Path(sys.argv[1])
    batch = path.stem.replace("batch-", "")
    judge = json.loads((HERE / "judge_input" / f"batch-{batch}.json").read_text(encoding="utf-8"))
    samples = {}
    for line in (HERE / "sample.jsonl").read_text(encoding="utf-8").splitlines():
        rec = json.loads(line)
        samples[rec["material_slug"]] = rec
    nodes = load_nodes()

    rows = list(csv.DictReader(path.open(encoding="utf-8", newline=""), delimiter="\t"))
    problems: list[str] = []
    if [r for r in rows] and list(rows[0].keys()) != HEADER:
        problems.append(f"表头不符：{list(rows[0].keys())}")
    expected = [it["material_slug"] for it in judge]
    got = [r["material_slug"] for r in rows]
    if expected != got:
        problems.append(f"行集/顺序不符：expected {len(expected)} got {len(got)}；"
                        f"缺 {sorted(set(expected) - set(got))[:5]} 多 {sorted(set(got) - set(expected))[:5]}")
    for r in rows:
        slug = r["material_slug"]
        rec = samples.get(slug)
        if rec is None:
            problems.append(f"{slug}: 不在样本")
            continue
        if r["verdict"] not in ("CORRECT", "REBIND", "NONE"):
            problems.append(f"{slug}: verdict={r['verdict']}")
        cur = rec["node"]["node_slug"]
        if r["verdict"] == "REBIND":
            t = r["target_node_slug"].strip()
            if not t:
                problems.append(f"{slug}: REBIND 缺 target")
            elif t not in nodes:
                problems.append(f"{slug}: target 不存在 {t}")
            elif nodes[t]["subject"] != rec["subject"]:
                problems.append(f"{slug}: target 跨科 {t}")
            elif t == cur:
                problems.append(f"{slug}: target == 现绑")
        elif r["target_node_slug"].strip():
            problems.append(f"{slug}: 非 REBIND 却给 target")
        if r["confidence"] not in ("high", "medium", "low"):
            problems.append(f"{slug}: confidence={r['confidence']}")
        quote = r["evidence_quote"]
        m = rec["material"]
        hay = "\n".join(str(m.get(k) or "") for k in
                        ("title", "summaryMarkdown", "applicabilityMarkdown", "contentMarkdown", "boundaryMarkdown"))
        if not quote or quote not in hay:
            problems.append(f"{slug}: quote 非逐字子串：{quote[:50]}")
        if r["verdict"] in ("REBIND", "NONE") and not r["note"].strip():
            problems.append(f"{slug}: {r['verdict']} 缺 note")
    print(json.dumps({"file": str(path), "rows": len(rows), "problems": len(problems)},
                     ensure_ascii=False))
    for p in problems:
        print("PROBLEM\t" + p)
    return 0 if not problems else 2


if __name__ == "__main__":
    raise SystemExit(main())
