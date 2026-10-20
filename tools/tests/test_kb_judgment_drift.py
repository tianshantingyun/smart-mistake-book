# -*- coding: utf-8 -*-
"""判定源 ↔ 包文本漂移检查的契约测试。

它盯的失败：判定表（入库的唯一判定源）与 sidecar 材料（实际分发文本）是同一条材料的
两份拷贝，修复/改写只落一边时两者会静默分叉——只查表或只查包的门都看不出来。
本检查按同一 slug 把 content/boundary 逐字对拍，容差 0。

测试打桩在纯函数 `judgment_drift.compare` 与门函数
`gate.judgment_pack_drift_metric(materials=…, rows=…)` 上，不依赖仓库当前读数。
"""
from __future__ import annotations

import unittest
from unittest import mock

from kb_build import gate
from kb_coverage import judgment_drift, judgments

MATH_REL = "资料/数学/一轮/集合.docx"


def _row(**overrides) -> dict:
    row = {
        "chunk_rel": MATH_REL,
        "chunk_id": "abc123-001",
        "action": "MATERIAL",
        "node_slug": "集合的含义与表示",
        "type": "CONCEPT_EXPLANATION",
        "title": "集合的含义",
        "summary": "概要",
        "applicability": "适用性",
        "content": "正文内容",
        "boundary": "边界内容",
        "note": "",
        "midx": "",
    }
    row.update(overrides)
    return row


def _material(**overrides) -> dict:
    material = {
        "slug": "ext-mat-abc123-001",
        "subject": "MATH",
        "title": "集合的含义",
        "summaryMarkdown": "概要",
        "applicabilityMarkdown": "适用性",
        "contentMarkdown": "正文内容",
        "boundaryMarkdown": "边界内容",
    }
    material.update(overrides)
    return material


class JudgmentSlugTest(unittest.TestCase):

    def test_slug_formula_matches_materialize(self):
        self.assertEqual("ext-mat-abc123-001", judgments.material_slug(_row(), "MATH"))
        self.assertEqual("ext-che-abc123-001b",
                         judgments.material_slug(_row(midx=" b "), "CHEMISTRY"))
        # 全科卷/坏 chunk_id 无法定位到包内材料。
        self.assertIsNone(judgments.material_slug(_row(), None))
        self.assertIsNone(judgments.material_slug(_row(chunk_id="nohyphen"), "MATH"))


class CompareTest(unittest.TestCase):

    def test_identical_text_is_green(self):
        result = judgment_drift.compare([_row()], [_material()])
        self.assertEqual(0, result["value"])
        self.assertEqual(1, result["matched"])
        self.assertEqual([], result["content_diffs"])
        self.assertEqual([], result["boundary_diffs"])

    def test_strip_is_the_materialize_normalization(self):
        # materialize 落盘时对 content/summary/boundary 都 strip；判定表里的行尾空白
        # 不应冒充漂移。
        result = judgment_drift.compare(
            [_row(content="  正文内容\n", boundary=" 边界内容 ",
                  summary=" 概要 ")],
            [_material(contentMarkdown="正文内容", boundaryMarkdown="边界内容")])
        self.assertEqual(0, result["value"], result)

    def test_content_and_boundary_differences_are_counted_separately(self):
        result = judgment_drift.compare(
            [_row(content="判定源正文", boundary="判定源边界")],
            [_material(contentMarkdown="包内正文", boundaryMarkdown="包内边界")])
        self.assertEqual(2, result["value"])
        self.assertEqual(1, len(result["content_diffs"]))
        self.assertEqual(1, len(result["boundary_diffs"]))
        self.assertEqual("判定源正文", result["content_diffs"][0]["expected"])
        self.assertEqual("包内正文", result["content_diffs"][0]["actual"])

    def test_empty_boundary_falls_back_to_summary_like_materialize(self):
        green = judgment_drift.compare(
            [_row(boundary="", summary="概要")],
            [_material(summaryMarkdown="概要", boundaryMarkdown="概要")])
        self.assertEqual(0, green["value"], green)
        red = judgment_drift.compare(
            [_row(boundary="", summary="概要")],
            [_material(summaryMarkdown="概要", boundaryMarkdown="另一版边界")])
        self.assertEqual(1, red["value"])
        self.assertEqual(1, len(red["boundary_diffs"]))

    def test_missing_material_is_not_silently_green(self):
        result = judgment_drift.compare([_row()], [])
        self.assertEqual(1, result["value"])
        self.assertEqual(1, len(result["missing"]))
        self.assertEqual("ext-mat-abc123-001", result["missing"][0]["slug"])

    def test_skip_rows_are_not_compared(self):
        result = judgment_drift.compare([_row(action="SKIP")], [_material()])
        self.assertEqual(0, result["material_rows"])
        self.assertEqual(0, result["value"])
        # 包里还留着这条材料，而判定源已判 SKIP：只提示，不计门值。
        self.assertEqual(1, result["orphan_ext"])
        self.assertEqual(1, result["orphan_ext_skip"])

    def test_duplicate_material_slugs_are_reported(self):
        result = judgment_drift.compare([_row(), _row(midx="")], [_material()])
        self.assertEqual({"ext-mat-abc123-001": 2}, result["duplicate_slugs"])
        self.assertEqual(0, result["value"])

    def test_unlocatable_material_row_is_red(self):
        result = judgment_drift.compare(
            [_row(chunk_rel="全科卷/综合.docx")], [])
        self.assertEqual(1, result["value"])
        self.assertEqual(1, len(result["unlocatable"]))


class RunKbChecksWiringTest(unittest.TestCase):
    """接线钉：`run_kb_checks` 必须把漂移读数放进独立 `judgment_drift` 节。"""

    def test_run_exposes_judgment_drift_section(self):
        from ci import run_kb_checks
        from pathlib import Path

        red = gate.Metric("judgment_pack_drift", "判定源与包文本漂移",
                          value=1, detail=["1 处"])
        with mock.patch.object(gate, "evaluate", return_value=[]), \
                mock.patch.object(gate, "judgment_pack_drift_metric", return_value=red), \
                mock.patch.object(run_kb_checks.check_pack_contract, "evaluate",
                                  return_value=({}, {"topics": 0, "points": 0})), \
                mock.patch.object(run_kb_checks.check_pack_contract,
                                  "report_only_counts", return_value={}), \
                mock.patch.object(run_kb_checks.check_pack_contract,
                                  "legacy_type_counts",
                                  return_value={"legacy_total": 0}), \
                mock.patch.object(run_kb_checks.roundtrip, "run",
                                  return_value=(0, 0)), \
                mock.patch.object(run_kb_checks.pack_io, "load_json",
                                  return_value={}), \
                mock.patch.object(run_kb_checks.check_asset, "evaluate",
                                  return_value={"ok": True, "checks": [],
                                                "failed": []}):
            result = run_kb_checks.run(Path(".").resolve())
        self.assertIn("judgment_drift", result)
        self.assertFalse(result["judgment_drift"]["ok"])
        self.assertEqual(1, result["judgment_drift"]["value"])
        self.assertFalse(result["ok"])


class GateMetricTest(unittest.TestCase):

    def test_metric_key_and_red_value(self):
        # 已登记基线 377 内的存量不判红；这里把基线压到 0 验证「超出即红」。
        with mock.patch.object(gate, "JUDGMENT_PACK_DRIFT_BASELINE", 0):
            metric = gate.judgment_pack_drift_metric(
                materials=[_material(contentMarkdown="包内正文")],
                rows=[_row(content="判定源正文")])
        self.assertEqual("judgment_pack_drift", metric.key)
        self.assertEqual(1, metric.value)
        self.assertFalse(metric.ok)
        self.assertTrue(any("ext-mat-abc123-001" in line for line in metric.detail))

    def test_equal_to_baseline_is_info_not_red(self):
        with mock.patch.object(gate, "JUDGMENT_PACK_DRIFT_BASELINE", 1):
            metric = gate.judgment_pack_drift_metric(
                materials=[_material(contentMarkdown="包内正文")],
                rows=[_row(content="判定源正文")])
        self.assertEqual(1, metric.value)
        self.assertTrue(metric.ok)
        self.assertTrue(any("等于已登记基线" in line for line in metric.detail))

    def test_unreadable_judgment_table_is_red_not_green(self):
        with mock.patch.object(judgment_drift, "load_rows",
                               side_effect=OSError("判定表缺席")):
            metric = gate.judgment_pack_drift_metric(materials=[_material()])
        self.assertEqual(1, metric.value)
        self.assertFalse(metric.ok)
        self.assertTrue(any("检查不可执行" in line for line in metric.detail))


if __name__ == "__main__":
    unittest.main()
