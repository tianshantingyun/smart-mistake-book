# -*- coding: utf-8 -*-
"""REBIND 目标检索：按材料标题/摘要对同科节点做 2-gram 相似度 + 关键词过滤。

用法：
    python -I -X utf8 find_targets.py --subject CHEMISTRY --query "淀粉 纤维素 同分异构体"
    python -I -X utf8 find_targets.py --slugs slug1,slug2        # 从 sample.jsonl 取标题+摘要批量查
只读 node_index.tsv / sample.jsonl，不写文件。
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def bigrams(s: str) -> set[str]:
    s = "".join(ch for ch in s if not ch.isspace())
    return {s[i:i + 2] for i in range(len(s) - 1)} if len(s) > 1 else {s}


def load_nodes() -> list[dict]:
    rows = []
    for line in (HERE / "node_index.tsv").read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        subject, topic, slug, name, kind, boundary = line.split("\t")
        rows.append({"subject": subject, "topic": topic, "slug": slug, "name": name,
                     "kind": kind, "boundary": boundary})
    return rows


def query(subject: str, text: str, top: int = 10) -> list[dict]:
    Q = bigrams(text)
    out = []
    for node in load_nodes():
        if node["subject"] != subject:
            continue
        B = bigrams(node["name"] + " " + node["boundary"])
        score = len(Q & B) / len(Q | B) if Q and B else 0.0
        # 关键词命中加分：查询里的连续 2-gram 是否出现在节点名
        name_hits = sum(1 for g in Q if g in node["name"])
        out.append({**node, "score": round(score, 4), "name_hits": name_hits})
    out.sort(key=lambda r: (-r["name_hits"], -r["score"], r["slug"]))
    return out[:top]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--subject")
    ap.add_argument("--query")
    ap.add_argument("--slugs")
    ap.add_argument("--top", type=int, default=10)
    args = ap.parse_args()
    if args.slugs:
        samples = {json.loads(line)["material_slug"]: json.loads(line)
                   for line in (HERE / "sample.jsonl").read_text(encoding="utf-8").splitlines()}
        for slug in args.slugs.split(","):
            rec = samples[slug]
            m = rec["material"]
            text = (m.get("title") or "") + " " + (m.get("summaryMarkdown") or "") + " " + (m.get("contentMarkdown") or "")[:200]
            print(f"\n### {slug} | {rec['subject']} | current={rec['node']['name']}")
            for r in query(rec["subject"], text, args.top):
                print(f"{r['slug']}\t{r['name']}\t{r['name_hits']}/{r['score']}\t{r['topic']}")
    else:
        if not args.subject or not args.query:
            raise SystemExit("需要 --subject 与 --query（或 --slugs）")
        for r in query(args.subject, args.query, args.top):
            print(f"{r['slug']}\t{r['name']}\t{r['name_hits']}/{r['score']}\t{r['topic']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
