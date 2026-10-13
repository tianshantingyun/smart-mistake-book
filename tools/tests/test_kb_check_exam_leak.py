# -*- coding: utf-8 -*-
"""kb_coverage.check_exam_leak 的契约测试。

覆盖：对判定表全部 MATERIAL 行跑 audit_material_examples 的 4 条判据
（题号/成套选项/答案语/题干尾）、只查 MATERIAL 行（SKIP 行忽略）、
命中清单按 node_slug 汇总、无命中 exit 0、有命中 exit 1（守卫语义）。
判定表与豁免表都用临时文件打桩。

豁免语义（2026-10-11 起）另有一组用例：逐条豁免后 exit 0、部分豁免只报残余、
**内容指纹不符的豁免不生效**（文本改写后重新变红）、陈旧豁免只列出不判红。

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
from kb_coverage import judgments

COLS = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title",
        "summary", "applicability", "content", "boundary", "note", "midx"]
EX_COLS = ["chunk_rel", "chunk_id", "midx", "material_slug", "fields_fp",
           "class", "reason", "reviewed_at"]

CLEAN = "加速度是速度对时间的变化率，其方向与速度变化量的方向一致。" * 3
HIT = "本题答案为 C，故选 C。" + CLEAN  # 命中「答案/故选语」判据


def _row(chunk_id: str, action: str, slug: str, title: str, content: str,
         chunk_rel: str = "srcA/1.docx", midx: str = "") -> list[str]:
    return [chunk_rel, chunk_id, action, slug, "CONCEPT_EXPLANATION",
            title, "概要", "适用性", content, "边界", "注", midx]


def _write(base: Path, rows: list[list[str]]) -> None:
    """判定表写进 base 目录的 5 片（列按 COLS 对位；与生产同一份 write_all）。"""
    judgments.write_all([dict(zip(COLS, r)) for r in rows], base)


def _fp(title: str, content: str, summary: str = "概要",
        applicability: str = "适用性") -> str:
    return cel.fields_fp({"title": title, "content": content,
                          "summary": summary, "applicability": applicability})


def _write_exemptions(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=EX_COLS)
        w.writeheader()
        w.writerows(rows)


def _ex(chunk_id: str, fp: str, slug: str = "slug", chunk_rel: str = "srcA/1.docx",
        midx: str = "", reason: str = "方法材料里的「答案」为作答指引，非题目答案") -> dict:
    return {"chunk_rel": chunk_rel, "chunk_id": chunk_id, "midx": midx,
            "material_slug": slug, "fields_fp": fp, "class": "answer_meta",
            "reason": reason, "reviewed_at": "2026-10-11"}


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

    def test_hit_carries_identity_and_fingerprint(self):
        hits = cel.check_rows([dict(zip(COLS, _row("c1", "MATERIAL", "答案语", "加速度", HIT)))])
        self.assertEqual(1, len(hits))
        self.assertEqual("c1", hits[0]["chunk_id"])
        self.assertEqual("", hits[0]["midx"])
        self.assertEqual(_fp("加速度", HIT), hits[0]["fields_fp"])


class MainTests(unittest.TestCase):
    def _run(self, path: Path, rows: list[list[str]],
             exemptions: list[dict] | None = None) -> tuple[int, str]:
        _write(path, rows)      # path 是片目录（原先是单文件 j.csv）
        ex_path = path.with_name("exemptions.csv")
        if exemptions:
            _write_exemptions(ex_path, exemptions)
        buf = io.StringIO()
        with mock.patch.object(cel, "JUDGMENTS", path), \
                mock.patch.object(cel, "EXEMPTIONS", ex_path), \
                contextlib.redirect_stdout(buf):
            rc = cel.main([])
        return rc, buf.getvalue()

    def test_no_hits_exit_zero(self):
        with tempfile.TemporaryDirectory() as td:
            rc, out = self._run(Path(td) / "j", [_row("c1", "MATERIAL", "加速度", "加速度", CLEAN)])
            self.assertEqual(0, rc)
            self.assertIn("无命中", out)

    def test_hits_exit_one_and_grouped_by_slug(self):
        with tempfile.TemporaryDirectory() as td:
            rows = [
                _row("c1", "MATERIAL", "力的合成", "加速度", "本题答案为 C，故选 C。" + CLEAN),
                _row("c2", "MATERIAL", "力的合成", "加速度", "正确选项为 D。" + CLEAN),
                _row("c3", "MATERIAL", "守恒法", "加速度", CLEAN),
            ]
            rc, out = self._run(Path(td) / "j", rows)
            self.assertEqual(1, rc)
            self.assertIn("例题派生命中 2", out)
            self.assertIn("力的合成", out)
            # 按 node_slug 汇总：力的合成 2 行（干净行不出现在汇总里）
            self.assertIn("2  力的合成", out)

    def test_all_hits_exempted_exit_zero(self):
        with tempfile.TemporaryDirectory() as td:
            rows = [_row("c1", "MATERIAL", "力的合成", "加速度", HIT),
                    _row("c2", "MATERIAL", "守恒法", "加速度", HIT)]
            ex = [_ex("c1", _fp("加速度", HIT)), _ex("c2", _fp("加速度", HIT))]
            rc, out = self._run(Path(td) / "j", rows, ex)
            self.assertEqual(0, rc)
            self.assertIn("已豁免 2、待处理 0", out)
            self.assertIn("全部命中已逐条豁免", out)

    def test_partial_exemption_lists_residual(self):
        with tempfile.TemporaryDirectory() as td:
            rows = [_row("c1", "MATERIAL", "力的合成", "加速度", HIT),
                    _row("c2", "MATERIAL", "守恒法", "加速度", HIT)]
            ex = [_ex("c1", _fp("加速度", HIT))]
            rc, out = self._run(Path(td) / "j", rows, ex)
            self.assertEqual(1, rc)
            self.assertIn("已豁免 1、待处理 1", out)
            self.assertIn("残余（未豁免，共 1 条", out)
            self.assertIn("c2", out)

    def test_fingerprint_mismatch_reapplies_check(self):
        """豁免只覆盖复核过的那版文本：指纹不符（文本改写）必须重新变红。"""
        with tempfile.TemporaryDirectory() as td:
            rows = [_row("c1", "MATERIAL", "力的合成", "加速度", HIT)]
            ex = [_ex("c1", "0" * 16)]
            rc, out = self._run(Path(td) / "j", rows, ex)
            self.assertEqual(1, rc)
            self.assertIn("已豁免 0、待处理 1", out)

    def test_stale_exemption_reported_but_not_red(self):
        with tempfile.TemporaryDirectory() as td:
            rows = [_row("c1", "MATERIAL", "守恒法", "加速度", CLEAN)]
            ex = [_ex("c1", _fp("加速度", CLEAN))]
            rc, out = self._run(Path(td) / "j", rows, ex)
            self.assertEqual(0, rc)
            self.assertIn("豁免表陈旧 1 条", out)
            self.assertIn("无命中", out)

    def test_missing_exemptions_file_means_no_exemption(self):
        with tempfile.TemporaryDirectory() as td:
            rc, out = self._run(Path(td) / "j",
                                [_row("c1", "MATERIAL", "力的合成", "加速度", HIT)])
            self.assertEqual(1, rc)
            self.assertIn("已豁免 0、待处理 1", out)


class LoadExemptionsTests(unittest.TestCase):
    def test_missing_file_is_empty(self):
        with tempfile.TemporaryDirectory() as td:
            self.assertEqual({}, cel.load_exemptions(Path(td) / "nope.csv"))

    def test_duplicate_key_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "e.csv"
            _write_exemptions(path, [_ex("c1", "a" * 16), _ex("c1", "a" * 16)])
            with self.assertRaises(ValueError):
                cel.load_exemptions(path)

    def test_missing_reason_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "e.csv"
            _write_exemptions(path, [_ex("c1", "a" * 16, reason="")])
            with self.assertRaises(ValueError):
                cel.load_exemptions(path)


if __name__ == "__main__":
    unittest.main()
