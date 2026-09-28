#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""金标检索的**不确定性**：单集的置信区间 + 两集/两路的配对检验。

## 它消灭的具体失败

主集 Recall@5 是一个**单个数字**（0.7444 = 67/90）。拿它跟一条写死的线（0.75）比大小、
然后说"没过线"，是把**抽样噪声当成了系统差异**：90 条查询里每条 0/1，章节内还彼此相关
（同一章 9 条共享知识点分布），点估计的标准误远超 `sqrt(p(1-p)/n)` 那个朴素公式。
2026 年外部对标（`docs/kb-golden-v2-protocol.md` §0）把"配对显著性检验"列为检索评测的常规项，
本仓库此前没有任何一处给区间——于是"0.7444 差 0.0056"这种话无法判断是信号还是噪声。

## 两种区间，为什么都要给

1. **逐题 bootstrap**：按查询重采样。假设"查询是独立的"，通常偏窄。
2. **按章 cluster bootstrap**：以**章**为重采样单位。同一章内的题强相关（共享知识点与
   材料分布），这一条才是**该设计下的诚实区间**（金标集是"10 章 × 9 题"的整群抽样）。
   两者并列给出，读的人自己看到"忽略聚类会窄多少"。

配对检验同理：同一批查询上比 A/B 两路（或两个版本），以**章**为重采样单位做配对 bootstrap，
报差值区间与双侧 p 值（差值符号翻转的复制比例）。

## 用法

    # 单集：从 MISS 账本反推逐题命中
    python tools/kb_coverage/retrieval_significance.py \
        --golden tools/kb_coverage/tables/golden_queries_v1.json \
        --misses build/golden-fused-misses.txt

    # 两路配对（A 是基线、B 是改动；两边都必须是同一金标集、同一个 MISS 账本格式）
    python tools/kb_coverage/retrieval_significance.py \
        --golden tools/kb_coverage/tables/golden_queries_v1.json \
        --misses build/golden-fused-misses.txt --misses-b build/other-misses.txt

MISS 账本格式（`GoldenRetrievalInstrumentedTest` 落盘的那份，逐行）：
    MISS [CHEMISTRY] <题面> | expectedSlug=<slug> | chapter=<章> | rank=<名次或 absent>
配对时按**题面文本**对齐（两边题面必须逐字一致，否则报错退出——不许悄悄按序号对齐）。
"""

from __future__ import annotations

import argparse
import json
import random
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

MISS_LINE = re.compile(r"^\s*MISS \[(?P<subject>[A-Z]+)\]\s*(?P<query>.*?)\s*\|\s*expectedSlug=(?P<slug>.*?)\s*\|\s*chapter=(?P<chapter>.*?)\s*\|\s*rank=(?P<rank>.*?)\s*$")


def load_golden(path: Path) -> list[dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list) or not data:
        raise SystemExit("金标集不是非空数组：%s" % path)
    for row in data:
        for key in ("chapter", "expectedSlug", "query", "subject"):
            if key not in row:
                raise SystemExit("金标条目缺字段 %s：%s" % (key, json.dumps(row, ensure_ascii=False)[:120]))
    return data


def load_misses(path: Path) -> set[str]:
    """返回 MISS 掉的**题面**集合。认不出格式的行直接报错（不静默跳过）。"""
    queries: set[str] = set()
    for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        match = MISS_LINE.match(line)
        if match is None:
            raise SystemExit("MISS 账本第 %d 行格式不符：%s" % (lineno, line[:160]))
        queries.add(match.group("query"))
    return queries


def outcomes(golden: list[dict], misses: set[str]) -> list[tuple[str, str, int]]:
    """逐题 (chapter, subject, hit)；题面在 MISS 账本里 = 未命中。"""
    rows = []
    for case in golden:
        rows.append((case["chapter"], case["subject"], 0 if case["query"] in misses else 1))
    unknown = misses - {case["query"] for case in golden}
    if unknown:
        raise SystemExit("MISS 账本里有 %d 条题面不在金标集内（账本与金标集不是同一版）：%s"
                         % (len(unknown), sorted(unknown)[:2]))
    return rows


def recall(rows: list[tuple[str, str, int]]) -> float:
    return sum(r[2] for r in rows) / len(rows) if rows else float("nan")


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return float("nan")
    ordered = sorted(values)
    index = (len(ordered) - 1) * pct / 100.0
    low = int(index)
    high = min(low + 1, len(ordered) - 1)
    frac = index - low
    return ordered[low] * (1 - frac) + ordered[high] * frac


def bootstrap_query(rows, reps, rng) -> list[float]:
    n = len(rows)
    return [recall([rows[rng.randrange(n)] for _ in range(n)]) for _ in range(reps)]


def bootstrap_chapter(rows, reps, rng) -> list[float]:
    by_chapter: dict[str, list[tuple[str, str, int]]] = {}
    for row in rows:
        by_chapter.setdefault(row[0], []).append(row)
    chapters = list(by_chapter.values())
    k = len(chapters)
    out = []
    for _ in range(reps):
        sample: list[tuple[str, str, int]] = []
        for _ in range(k):
            sample.extend(chapters[rng.randrange(k)])
        out.append(recall(sample))
    return out


def paired_chapter_delta(rows_a, rows_b, reps, rng) -> list[float]:
    """按章 cluster 的配对 bootstrap：每次复制取同一批章，再算两路的差值。

    rows_a/rows_b 已按同一顺序对齐（同一批查询）。
    """
    by_chapter: dict[str, list[int]] = {}
    for index, row in enumerate(rows_a):
        by_chapter.setdefault(row[0], []).append(index)
    chapters = list(by_chapter.values())
    k = len(chapters)
    out = []
    for _ in range(reps):
        picked: list[int] = []
        for _ in range(k):
            picked.extend(chapters[rng.randrange(k)])
        delta = sum(rows_b[i][2] - rows_a[i][2] for i in picked) / len(picked)
        out.append(delta)
    return out


def report_single(name: str, rows, reps: int, rng) -> dict:
    point = recall(rows)
    q = bootstrap_query(rows, reps, rng)
    c = bootstrap_chapter(rows, reps, rng)
    return {
        "name": name,
        "n": len(rows),
        "chapters": len({r[0] for r in rows}),
        "recall": point,
        "ci95_query": [percentile(q, 2.5), percentile(q, 97.5)],
        "ci95_chapter": [percentile(c, 2.5), percentile(c, 97.5)],
        "per_subject": {
            subject: recall([r for r in rows if r[1] == subject])
            for subject in sorted({r[1] for r in rows})
        },
        "per_chapter": {
            chapter: recall([r for r in rows if r[0] == chapter])
            for chapter in sorted({r[0] for r in rows})
        },
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="金标检索的不确定性（CI + 配对检验）")
    parser.add_argument("--golden", type=Path, default=REPO / "tools/kb_coverage/tables/golden_queries_v2.json")
    parser.add_argument("--misses", type=Path, required=True, help="A 路（或单集）的 MISS 账本")
    parser.add_argument("--misses-b", type=Path, default=None, help="给了就做 A/B 配对检验")
    parser.add_argument("--reps", type=int, default=20000)
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)

    rng = random.Random(args.seed)
    # 注（安全扫描处置）：这里的 `random` 是**必要的可复现性设计**，不是安全用途——
    # bootstrap 重采样必须能由 `--seed` 逐位复现（同一账本 + 同一 seed ⇒ 同一区间），
    # 换成 `secrets` 会让区间每次不同，评测就无法对账。故不改。
    golden = load_golden(args.golden)
    rows_a = outcomes(golden, load_misses(args.misses))
    result: dict = {"golden": str(args.golden), "reps": args.reps, "seed": args.seed,
                    "misses": str(args.misses), "a": report_single("A", rows_a, args.reps, rng)}

    if args.misses_b is not None:
        misses_b = load_misses(args.misses_b)
        rows_b = outcomes(golden, misses_b)
        deltas = paired_chapter_delta(rows_a, rows_b, args.reps, rng)
        # 双侧 p：差值符号翻转的比例（加 1 的平滑，避免 0 概率）
        non_positive = sum(1 for d in deltas if d <= 0) + 1
        non_negative = sum(1 for d in deltas if d >= 0) + 1
        total = len(deltas) + 1
        p_two_sided = min(1.0, 2 * min(non_positive, non_negative) / total)
        result["b"] = report_single("B", rows_b, args.reps, rng)
        result["paired"] = {
            "delta_recall_b_minus_a": recall(rows_b) - recall(rows_a),
            "ci95_chapter": [percentile(deltas, 2.5), percentile(deltas, 97.5)],
            "p_two_sided": p_two_sided,
            "note": "按章 cluster 配对 bootstrap；区间跨 0 或 p 大 ⇒ 这点差异在本设计下不可分辨",
        }

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=1))
        return 0

    def fmt_ci(ci):
        return "[%.4f, %.4f]" % (ci[0], ci[1])

    a = result["a"]
    print("金标集：%s（n=%d，%d 章）" % (args.golden, a["n"], a["chapters"]))
    print("A 路 recall=%.4f（%d/%d）" % (a["recall"], round(a["recall"] * a["n"]), a["n"]))
    print("  95%% CI（逐题 bootstrap）  ：%s" % fmt_ci(a["ci95_query"]))
    print("  95%% CI（按章 cluster，诚实口径）：%s" % fmt_ci(a["ci95_chapter"]))
    for chapter, value in a["per_chapter"].items():
        print("    %-40s %.4f" % (chapter, value))
    if "b" in result:
        b = result["b"]
        paired = result["paired"]
        print("B 路 recall=%.4f（%d/%d）  95%% CI（按章）：%s"
              % (b["recall"], round(b["recall"] * b["n"]), b["n"], fmt_ci(b["ci95_chapter"])))
        print("配对差 B−A=%.4f  95%% CI（按章）：%s  双侧 p=%.4f"
              % (paired["delta_recall_b_minus_a"], fmt_ci(paired["ci95_chapter"]), paired["p_two_sided"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
