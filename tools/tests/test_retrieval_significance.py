# -*- coding: utf-8 -*-
"""`kb_coverage.retrieval_significance` 的契约测试。

覆盖三件事（对应它消灭的三类失败）：
1. 点估计与账本口径：MISS 账本 → 逐题命中 → Recall@5 的数字必须与账本条数自洽。
2. 两种区间的**方向性**：点估计必须落在区间内；按章 cluster 的区间因为少算了章内相关性，
   在"整章全中/整章全错"的极端设计上**必须比逐题区间宽**（这正是它存在的理由）。
3. 两条守卫必须拒：账本里有金标集之外的题面（账本与金标不是同一版）、账本行格式不符——
   一律 `SystemExit`，**不许静默跳过**（静默跳过会把"少了几条"读成"这几条命中了"）。
"""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from kb_coverage import retrieval_significance as sig


def write_golden(path: Path, cases: list[dict]) -> None:
    path.write_text(json.dumps(cases, ensure_ascii=False), encoding="utf-8")


def miss_line(subject: str, query: str, slug: str, chapter: str) -> str:
    return f"MISS [{subject}] {query} | expectedSlug={slug} | chapter={chapter} | rank=absent"


class PointEstimateTest(unittest.TestCase):
    def test_recall_matches_ledger_size(self):
        golden = [
            {"chapter": "C1", "subject": "MATH", "expectedSlug": "s1", "query": "q1"},
            {"chapter": "C1", "subject": "MATH", "expectedSlug": "s2", "query": "q2"},
            {"chapter": "C2", "subject": "PHYSICS", "expectedSlug": "s3", "query": "q3"},
            {"chapter": "C2", "subject": "PHYSICS", "expectedSlug": "s4", "query": "q4"},
        ]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "g.json").write_text(json.dumps(golden, ensure_ascii=False), encoding="utf-8")
            (root / "m.txt").write_text(miss_line("MATH", "q1", "s1", "C1") + "\n", encoding="utf-8")
            rows = sig.outcomes(sig.load_golden(root / "g.json"), sig.load_misses(root / "m.txt"))
            self.assertEqual(3 / 4, sig.recall(rows))

    def test_blank_lines_are_ignored_and_unknown_query_is_fatal(self):
        golden = [{"chapter": "C1", "subject": "MATH", "expectedSlug": "s1", "query": "q1"}]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "g.json").write_text(json.dumps(golden, ensure_ascii=False), encoding="utf-8")
            ok = root / "ok.txt"
            ok.write_text("\n" + miss_line("MATH", "q1", "s1", "C1") + "\n\n", encoding="utf-8")
            self.assertEqual({"q1"}, sig.load_misses(ok))
            foreign = root / "foreign.txt"
            foreign.write_text(miss_line("MATH", "不在金标集里的题", "s9", "C1"), encoding="utf-8")
            with self.assertRaises(SystemExit):
                sig.outcomes(sig.load_golden(root / "g.json"), sig.load_misses(foreign))

    def test_malformed_line_is_fatal(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.txt"
            path.write_text("MISS [MATH] 缺了管道分隔的题面\n", encoding="utf-8")
            with self.assertRaises(SystemExit):
                sig.load_misses(path)


class IntervalShapeTest(unittest.TestCase):
    """整章同向的极端设计：cluster 区间必须比逐题区间**宽**（章内相关性不能被忽略掉）。"""

    def _rows(self):
        rows = []
        for chapter in ("C1", "C2", "C3", "C4"):
            for index in range(4):
                # C1 全中、C2 全错、C3 全中、C4 全错 ⇒ 章间方差被推到最大
                rows.append((chapter, "MATH", 1 if chapter in ("C1", "C3") else 0))
        return rows

    def test_cluster_interval_is_wider_than_query_interval(self):
        import random

        rows = self._rows()
        rng = random.Random(0)
        point = sig.recall(rows)
        query_ci = [sig.percentile(sig.bootstrap_query(rows, 2000, rng), 2.5),
                    sig.percentile(sig.bootstrap_query(rows, 2000, rng), 97.5)]
        chapter_ci = [sig.percentile(sig.bootstrap_chapter(rows, 2000, rng), 2.5),
                      sig.percentile(sig.bootstrap_chapter(rows, 2000, rng), 97.5)]
        self.assertLessEqual(query_ci[0], point)
        self.assertGreaterEqual(query_ci[1], point)
        self.assertLessEqual(chapter_ci[0], point)
        self.assertGreaterEqual(chapter_ci[1], point)
        self.assertGreater(
            chapter_ci[1] - chapter_ci[0], query_ci[1] - query_ci[0],
            f"cluster 区间应更宽：chapter={chapter_ci} query={query_ci}",
        )

    def test_paired_delta_interval_centers_on_zero_for_identical_ledgers(self):
        import random

        rows = self._rows()
        rng = random.Random(0)
        deltas = sig.paired_chapter_delta(rows, rows, 2000, rng)
        self.assertTrue(all(abs(d) < 1e-12 for d in deltas))


if __name__ == "__main__":
    unittest.main()
