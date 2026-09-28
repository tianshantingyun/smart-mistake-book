# -*- coding: utf-8 -*-
"""kb_coverage.check_exam_leak 的契约测试。

覆盖：对判定表全部 MATERIAL 行跑 audit_material_examples 的 4 条判据
（题号/成套选项/答案语/题干尾）、只查 MATERIAL 行（SKIP 行忽略）、
命中清单按 node_slug 汇总、无命中 exit 0、有命中 exit 1（守卫语义）。
判定表用临时文件打桩。

跑法（与仓库其余 tools/tests 一致）：
    python -m unittest discover -s tools/tests -t tools -p "test_kb_check_exam_leak.py"
"""

from __future__ import annotations

import csv
import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from kb_coverage import check_exam_leak as cel

COLS = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title",
        "summary", "applicability", "content", "boundary", "note", "midx"]

CLEAN = "加速度是速度对时间的变化率，其方向与速度变化量的方向一致。" * 3


def _row(chunk_id: str, action: str, slug: str, title: str, content: str) -> list[str]:
    return ["srcA/1.docx", chunk_id, action, slug, "CONCEPT_EXPLANATION",
            title, "概要", "适用性", content, "边界", "注", ""]


def _write(path: Path, rows: list[list[str]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(COLS)
        w.writerows(rows)


class CheckRowsTests(unittest.TestCase):
    def test_clean_material_rows_not_flagged(self):
        hits = cel.check_rows([dict(zip(COLS, _row("c1", "MATERIAL", "加速度", "加速度", CLEAN)))])
        self.assertEqual([], hits)

    def test_skip_rows_ignored(self):
        # SKIP 行即使满身题目形态也不查（只查 MATERIAL 行）
        hits = cel.check_rows([dict(zip(COLS, _row("c1", "SKIP", "例1", "（2025·全国卷）", "答案：C")))])
        self.assertEqual([], hits)

    def test_four_criteria_each_flag(self):
        rows = [
            _row("c1", "MATERIAL", "标题题号", "（2025·全国甲卷）第 3 题", CLEAN),
            _row("c2", "MATERIAL", "成套选项", "加速度", "A. 甲 B. 乙 C. 丙 D. 丁"),
            _row("c3", "MATERIAL", "答案语", "加速度", "本题答案为 C，故选 C。" + CLEAN),
            _row("c4", "MATERIAL", "短题干", "加速度", "求加速度"),
        ]
        hits = cel.check_rows([dict(zip(COLS, r)) for r in rows])
        self.assertEqual(4, len(hits))
        self.assertEqual({"标题题号", "成套选项", "答案语", "短题干"}, {h["node_slug"] for h in hits})
        self.assertIn("标题是题号/来源", hits[0]["reason"])
        reasons = [h["reason"] for h in hits]
        self.assertTrue(any("成套选项" in r for r in reasons))
        self.assertTrue(any("答案/故选" in r for r in reasons))
        self.assertTrue(any("短题干且无结论" in r for r in reasons))

    def test_non_material_actions_never_checked(self):
        rows = [
            _row("c1", "SKIP", "被忽略", "（2025·全国卷）", "答案：C"),
            _row("c2", "MATERIAL", "干净", "加速度", CLEAN),
        ]
        hits = cel.check_rows([dict(zip(COLS, r)) for r in rows])
        self.assertEqual([], hits)


class MainTests(unittest.TestCase):
    def _run(self, path: Path, rows: list[list[str]]) -> tuple[int, str]:
        _write(path, rows)
        buf = io.StringIO()
        with mock.patch.object(cel, "JUDGMENTS", path), contextlib.redirect_stdout(buf):
            rc = cel.main([])
        return rc, buf.getvalue()

    def test_no_hits_exit_zero(self):
        with tempfile.TemporaryDirectory() as td:
            rc, out = self._run(Path(td) / "j.csv", [_row("c1", "MATERIAL", "加速度", "加速度", CLEAN)])
            self.assertEqual(0, rc)
            self.assertIn("无命中", out)

    def test_hits_exit_one_and_grouped_by_slug(self):
        with tempfile.TemporaryDirectory() as td:
            rows = [
                _row("c1", "MATERIAL", "力的合成", "加速度", "本题答案为 C，故选 C。" + CLEAN),
                _row("c2", "MATERIAL", "力的合成", "加速度", "正确选项为 D。" + CLEAN),
                _row("c3", "MATERIAL", "守恒法", "加速度", CLEAN),
            ]
            rc, out = self._run(Path(td) / "j.csv", rows)
            self.assertEqual(1, rc)
            self.assertIn("例题派生命中 2", out)
            self.assertIn("力的合成", out)
            # 按 node_slug 汇总：力的合成 2 行（干净行不出现在汇总里）
            self.assertIn("2  力的合成", out)


if __name__ == "__main__":
    unittest.main()
