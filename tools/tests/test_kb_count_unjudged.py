# -*- coding: utf-8 -*-
"""kb_coverage.count_unjudged 的契约测试。

覆盖：三档键数口径（已判定 = 判定表 − 重判队列；机械SKIP / 待判定 由
prescreen 判据分出）、重判队列键按未判定处理、exit 0。全部临时文件打桩。

跑法（与仓库其余 tools/tests 一致）：
    python -m unittest discover -s tools/tests -t tools -p "test_kb_count_unjudged.py"
"""

from __future__ import annotations

import csv
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from kb_coverage import count_unjudged as cu
from kb_coverage import prescreen_chunks as pc

GOOD_TEXT = (
    "加速度是速度对时间的变化率，其方向与速度变化量的方向一致。"
    "加速度是矢量，在国际单位制中单位是米每二次方秒。"
)
JUDGMENT_COLS = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title",
                 "summary", "applicability", "content", "boundary", "note", "midx"]


def _write_pool(path: Path, records: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as fh:
        for r in records:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")


def _write_csv(path: Path, cols: list[str], rows: list[list[str]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(cols)
        w.writerows(rows)


class CountsTests(unittest.TestCase):
    def _run(self, td: str, pool: list[dict], judged: list[list[str]],
             rejudge: list[list[str]]) -> int:
        p = Path(td) / "chunks.jsonl"
        j = Path(td) / "judged.csv"
        q = Path(td) / "rejudge.csv"
        _write_pool(p, pool)
        _write_csv(j, JUDGMENT_COLS, judged)
        _write_csv(q, ["chunk_rel", "chunk_id", "midx", "node_slug", "type", "note"], rejudge)
        with mock.patch.object(pc, "CHUNKS", p), \
                mock.patch.object(pc, "JUDGMENTS", j), \
                mock.patch.object(pc, "REJUDGE", q):
            return cu.counts()

    def test_three_buckets_with_rejudge(self):
        pool = [
            # 已判定（判定表、不在队列）→ 已判定
            {"rel_path": "srcA/1.docx", "chunk_id": "j1", "fp": "fp1", "heading": "加速度", "text": GOOD_TEXT},
            # 判定表里有 + 队列里有（坏 type 待重判）→ 按未判定：JUDGE 类 → 待判定
            {"rel_path": "srcA/1.docx", "chunk_id": "q1", "fp": "fp2", "heading": "加速度", "text": GOOD_TEXT},
            # 未判定 + 题目派生 → 机械SKIP
            {"rel_path": "srcA/1.docx", "chunk_id": "s1", "fp": "fp3", "heading": "例 1", "text": GOOD_TEXT},
            # 未判定 + 过短 → 机械SKIP
            {"rel_path": "srcB/2.docx", "chunk_id": "s2", "fp": "fp4", "heading": "", "text": "很短"},
            # 未判定 + JUDGE → 待判定
            {"rel_path": "srcB/2.docx", "chunk_id": "u1", "fp": "fp5", "heading": "速度", "text": GOOD_TEXT},
        ]
        judged = [
            ["srcA/1.docx", "j1", "MATERIAL", "某节点", "CONCEPT_EXPLANATION", "", "", "", "", "", "", ""],
            ["srcA/1.docx", "q1", "SKIP", "某节点", "DERIVATION", "", "", "", "", "", "", ""],
        ]
        rejudge = [["srcA/1.docx", "q1", "", "某节点", "DERIVATION", "待重判"]]
        with tempfile.TemporaryDirectory() as td:
            counted = self._run(td, pool, judged, rejudge)
        self.assertEqual((1, 2, 2), counted)  # 已判定 1 / 机械SKIP 2 / 待判定 2

    def test_missing_tables_default_empty(self):
        pool = [
            {"rel_path": "srcA/1.docx", "chunk_id": "u1", "fp": "fp5", "heading": "速度", "text": GOOD_TEXT},
        ]
        with tempfile.TemporaryDirectory() as td:
            counted = self._run(td, pool, [], [])
        self.assertEqual((0, 0, 1), counted)

    def test_main_exit_zero_and_print(self):
        pool = [
            {"rel_path": "srcA/1.docx", "chunk_id": "u1", "fp": "fp5", "heading": "速度", "text": GOOD_TEXT},
            {"rel_path": "srcA/1.docx", "chunk_id": "s1", "fp": "fp3", "heading": "例 1", "text": GOOD_TEXT},
        ]
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "chunks.jsonl"
            j = Path(td) / "judged.csv"
            q = Path(td) / "rejudge.csv"
            _write_pool(p, pool)
            _write_csv(j, JUDGMENT_COLS, [])
            _write_csv(q, ["chunk_rel", "chunk_id", "midx", "node_slug", "type", "note"], [])
            buf = io.StringIO()
            with mock.patch.object(pc, "CHUNKS", p), \
                    mock.patch.object(pc, "JUDGMENTS", j), \
                    mock.patch.object(pc, "REJUDGE", q), \
                    contextlib.redirect_stdout(buf):
                rc = cu.main([])
            self.assertEqual(0, rc)
            self.assertEqual("已判定 0 / 机械SKIP 1 / 待判定 1", buf.getvalue().strip())


if __name__ == "__main__":
    unittest.main()
