#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""金标扩集**候选批次**的机械校验（`docs/kb-golden-v2-protocol.md` §3 第 1 步）。

## 它消灭的具体失败

扩集是唯一一次能被"看结果调题面"污染的窗口。这一批候选由代理起草，**起草者的自述是二手信息**：
"slug 存在""章归属正确""与现有 90 条不重复"都必须由机械断言重核一遍，否则把不良条目冻进判官，
后面所有质量结论都建在沙子上。

比既有 `validate_golden.py` 多的三条（后者按**切片**工作、只校验 v1 那套流程）：
- **章归属 = 精确断言**：`chapter` 必须**逐字等于**该知识点所属 topic 的 slug
  （topic slug 本身就是"册·章·主题"全路径，实测：`化学必修第二册·第七章·有机化合物`）。
- **近似重复**：只查逐字重合不够——换数字/换语序的改写会以"新题"混进来（`difflib` 比值）。
- **泄漏检查**（可选）：候选题面与**真机 MISS 账本**的相似度。起草者被禁止接触检索结果；
  若某条候选与"已知会漏掉的题"高度相似，说明它可能是照着结果反推的（起草者无从辩解，只能看数据）。

用法：

    python tools/kb_coverage/check_golden_candidate.py --candidate build/stage7/golden-v2-draft.json \
        [--golden tools/kb_coverage/tables/golden_queries_v1.json] \
        [--misses build/stage7/golden-fused-misses-device-2026-09-28.txt]

退出码：全过 0，任一断言失败 1（逐条列出，不截断）。
"""

from __future__ import annotations

import argparse
import collections
import difflib
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
KEYS = ("chapter", "expectedSlug", "query", "subject")
CJK = re.compile(r"[\u4e00-\u9fff]")
NUMERIC = re.compile(r"[0-9]|[=+\-*/^]|[a-zA-Z]\s*\(")
DISCERN = re.compile(r"为什么|能不能|对不对|是否|吗？|吗\?|如何判断")
MISS_LINE = re.compile(r"^\s*MISS \[[A-Z]+\]\s*(?P<query>.*?)\s*\|\s*expectedSlug=.*$")


def load_pack(pack_path: Path) -> dict[tuple[str, str], tuple[str, str]]:
    """(subject, slug) -> (所属 topic 的 slug, kind)。

    slug **不全局唯一**（实测：`阿伏加德罗常数` 在 PHYSICS 与 CHEMISTRY 各有一个）——
    节点 id 本来就按科隔离（`kb:<pack>:<subject>:atomic:<slug>`），所以键必须是
    (subject, slug) 二元组；本函数只在**同一科内** slug 重复时报错。
    """
    pack = json.loads(pack_path.read_text(encoding="utf-8"))
    index: dict[tuple[str, str], tuple[str, str]] = {}
    for subject in pack["subjects"]:
        for topic in subject["topics"]:
            for point in topic.get("knowledgePoints") or []:
                key = (subject["subject"], point["slug"])
                if key in index:
                    raise SystemExit(f"知识库自身矛盾：同科内 slug 重复 {key!r}")
                index[key] = (topic["slug"], point.get("kind") or "")
    return index


def load_miss_queries(path: Path) -> list[str]:
    queries = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = MISS_LINE.match(line)
        if match:
            queries.append(match.group("query"))
    return queries


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="金标候选批次的机械校验")
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--golden", type=Path,
                        default=REPO / "tools/kb_coverage/tables/golden_queries_v2.json")
    parser.add_argument("--pack", type=Path,
                        default=REPO / "core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json")
    parser.add_argument("--misses", type=Path, default=None)
    parser.add_argument("--near-dup-ratio", type=float, default=0.80)
    parser.add_argument("--leak-ratio", type=float, default=0.60)
    parser.add_argument("--skip-batch-rules", action="store_true",
                        help="校验**已合并的判官**（v1+候选）时用：批次级规则只对新起草的批次成立"
                             "（题面长度下限 15、风格代理量、slug 使用 ≤2、与 v1 的重合/近似/泄漏检查"
                             "对合并集自比自会全红）——这些规则不该拿去卡一份冻结的判官。")
    args = parser.parse_args(argv)
    batch_rules = not args.skip_batch_rules

    candidate = json.loads(args.candidate.read_text(encoding="utf-8"))
    existing = json.loads(args.golden.read_text(encoding="utf-8"))
    index = load_pack(args.pack)
    failures: list[str] = []
    notes: list[str] = []

    # C1 形状
    if not isinstance(candidate, list) or not candidate:
        print("候选不是非空数组", file=sys.stderr)
        return 1
    for position, row in enumerate(candidate, 1):
        if not isinstance(row, dict) or set(row) != set(KEYS):
            failures.append(f"C1 #{position} 字段集合不是 {KEYS}：{sorted(row) if isinstance(row, dict) else type(row).__name__}")
            continue
        for key in KEYS:
            if not isinstance(row[key], str) or not row[key].strip():
                failures.append(f"C1 #{position} {key} 不是非空字符串：{row[key]!r}")

    # C2 题面（起草批次：15–60 字；已合并的判官：4–60 字，照 v1 自己的口径）
    min_len = 15 if batch_rules else 4
    for position, row in enumerate(candidate, 1):
        if set(row) != set(KEYS):
            continue
        query = row["query"]
        if not (min_len <= len(query) <= 60):
            failures.append(f"C2 #{position} 题面长度 {len(query)} 不在 {min_len}–60：{query[:40]}")
        if not CJK.search(query):
            failures.append(f"C2 #{position} 题面没有汉字：{query[:40]}")
        if "\n" in query or "\r" in query:
            failures.append(f"C2 #{position} 题面含换行：{query[:40]}")
        if query != query.strip():
            failures.append(f"C2 #{position} 题面首尾有空白：{query[:40]}")

    # C3/C4 slug 存在 + 章归属精确 + subject 一致
    for position, row in enumerate(candidate, 1):
        if set(row) != set(KEYS):
            continue
        entry = index.get((row["subject"], row["expectedSlug"]))
        if entry is None:
            failures.append(f"C3 #{position} (subject, slug) 不在知识库里：({row['subject']!r}, {row['expectedSlug']!r})")
            continue
        topic_slug, kind = entry
        if row["chapter"] != topic_slug:
            failures.append(
                f"C4 #{position} chapter 与所属 topic slug 不是逐字相等：\n"
                f"        候选 {row['chapter']!r}\n        包内 {topic_slug!r}（slug={row['expectedSlug']}）")

    # C5 唯一性
    queries = [row["query"] for row in candidate if set(row) == set(KEYS)]
    for text, count in collections.Counter(queries).items():
        if count > 1:
            failures.append(f"C5 候选内部题面重复 ×{count}：{text[:40]}")
    existing_queries = {row["query"] for row in existing}
    if batch_rules:
        for text in queries:
            if text in existing_queries:
                failures.append(f"C5 与现有金标题面逐字重合：{text[:40]}")
    pairs = collections.Counter((row["query"], row["expectedSlug"]) for row in candidate if set(row) == set(KEYS))
    for pair, count in pairs.items():
        if count > 1:
            failures.append(f"C5 (query, slug) 重复 ×{count}：{pair[0][:30]}")
    if batch_rules:
        slug_counts = collections.Counter(row["expectedSlug"] for row in candidate if set(row) == set(KEYS))
        for slug, count in slug_counts.items():
            if count > 2:
                failures.append(f"C5 slug 使用 {count} 次（上限 2）：{slug}")
    by_chapter: dict[str, list[str]] = collections.defaultdict(list)
    for row in candidate:
        if set(row) == set(KEYS):
            by_chapter[row["chapter"]].append(row["expectedSlug"])
    for chapter, slugs in by_chapter.items():
        duplicated = [s for s, c in collections.Counter(slugs).items() if c > 1]
        if duplicated:
            failures.append(f"C5 同章内 slug 重复：{chapter} → {duplicated}")

    # C6 近似重复（对现有金标）——只对**新起草批次**成立
    if batch_rules:
        for text in queries:
            close = difflib.get_close_matches(text, existing_queries, n=1, cutoff=args.near_dup_ratio)
            if close:
                ratio = difflib.SequenceMatcher(None, text, close[0]).ratio()
                failures.append(f"C6 与现有金标近似（{ratio:.2f}）：\n        候选 {text[:40]}\n        现有 {close[0][:40]}")

    # C7 泄漏检查（候选 vs 真机 MISS 账本）——只对**新起草批次**成立
    if args.misses is not None and batch_rules:
        missed = load_miss_queries(args.misses)
        for text in queries:
            close = difflib.get_close_matches(text, missed, n=1, cutoff=args.leak_ratio)
            if close:
                ratio = difflib.SequenceMatcher(None, text, close[0]).ratio()
                failures.append(f"C7 与 MISS 账本过高相似（{ratio:.2f}，需人工判断是否照结果反推）：\n"
                                f"        候选 {text[:40]}\n        MISS {close[0][:40]}")
        notes.append(f"C7 泄漏检查：对照 {len(missed)} 条 MISS 题面，阈值 {args.leak_ratio}")

    # C8 风格代理量（起草批次的写作配额；合并后的判官不再按它卡）
    numeric = sum(1 for t in queries if NUMERIC.search(t))
    discern = sum(1 for t in queries if DISCERN.search(t))
    notes.append(f"C8 含数值/公式符号 {numeric} 条（起草规则 ≥12）；辨析式 {discern} 条（规则 ≥8）")
    if batch_rules and numeric < 12:
        failures.append(f"C8 含数值/公式符号只有 {numeric} 条（规则 ≥12）")
    if batch_rules and discern < 8:
        failures.append(f"C8 辨析式只有 {discern} 条（规则 ≥8）")

    # 配额概览
    per_subject = collections.Counter(row["subject"] for row in candidate if set(row) == set(KEYS))
    notes.append("科分布：" + " / ".join(f"{k} {v}" for k, v in sorted(per_subject.items())))
    notes.append(f"章数：{len(by_chapter)}（候选 {len(queries)} 条）")

    for note in notes:
        print("  · " + note)
    if failures:
        print(f"\n失败 {len(failures)} 条：")
        for item in failures:
            print("  ✗ " + item)
        return 1
    print("\n候选批次机械校验通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
