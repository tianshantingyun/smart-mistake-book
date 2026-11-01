# -*- coding: utf-8 -*-
"""material_coverage_report：logcat 行 → per-subject 覆盖率/空材料率的解析与聚合。

用例钉住四件事：logcat 前缀不影响识别；分母/分子口径不被材料条数污染；
写坏的行不会被静默当成 0；目录扫描与 0 事件报告都明确。
"""

from __future__ import annotations

import io
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from kb_coverage import material_coverage_report as R


def _line(subject: str, queried: int, returned: int, with_material: int,
          without_material: int, empty: str = "") -> str:
    return (
        "10-31 01:02:03.456  1234  1234 I kbobs   : "
        f"{R.LINE_MARKER} subject={subject} queriedNodes={queried} "
        f"returnedMaterials={returned} nodesWithMaterial={with_material} "
        f"nodesWithoutMaterial={without_material} emptyNodeIds=[{empty}]"
    )


class ParseLineTest(unittest.TestCase):
    def test_logcat_prefix_is_tolerated(self):
        event, malformed = R.parse_line(_line("math", 3, 2, 2, 1, "kc-b"))
        self.assertFalse(malformed)
        assert event is not None
        self.assertEqual("math", event.subject)
        self.assertEqual(3, event.queried_nodes)
        self.assertEqual(2, event.returned_materials)
        self.assertEqual(2, event.nodes_with_material)
        self.assertEqual(1, event.nodes_without_material)
        self.assertEqual(("kc-b",), event.empty_node_ids)

    def test_non_metric_line_is_skipped_without_being_malformed(self):
        event, malformed = R.parse_line("10-31 01:02:03.456  1234  I other: hello")
        self.assertIsNone(event)
        self.assertFalse(malformed)

    def test_truncated_metric_line_is_malformed_not_zero(self):
        event, malformed = R.parse_line(
            "I kbobs: v=1 metric=material_coverage subject=math queriedNodes=3"
        )
        self.assertIsNone(event)
        self.assertTrue(malformed)

    def test_inconsistent_counts_are_malformed(self):
        # with + without != queried：行被截断或字段错位，不能算一次 0 覆盖。
        event, malformed = R.parse_line(_line("math", 5, 2, 2, 1))
        self.assertIsNone(event)
        self.assertTrue(malformed)

    def test_more_empty_ids_than_empty_nodes_is_malformed(self):
        event, malformed = R.parse_line(_line("math", 2, 2, 2, 0, "kc-a, kc-b"))
        self.assertIsNone(event)
        self.assertTrue(malformed)


class BuildReportTest(unittest.TestCase):
    def test_aggregates_per_subject_and_computes_both_rates(self):
        lines = "\n".join([
            _line("math", 3, 2, 2, 1, "kc-b"),
            _line("math", 1, 0, 0, 1, "kc-c"),
            _line("physics", 2, 1, 1, 1, "kc-p"),
            "not a metric line",
        ])
        report = R.build_report(["-"], stdin=io.StringIO(lines))

        self.assertEqual(3, report.event_count)
        self.assertEqual(0, report.malformed_lines)
        math = report.subjects["math"]
        self.assertEqual((2, 4, 2, 2, 2),
                         (math.events, math.queried_nodes, math.returned_materials,
                          math.nodes_with_material, math.nodes_without_material))
        self.assertAlmostEqual(0.5, math.coverage)
        self.assertAlmostEqual(0.5, math.empty_material_node_rate)

        physics = report.subjects["physics"]
        self.assertAlmostEqual(0.5, physics.coverage)
        self.assertAlmostEqual(0.5, physics.empty_material_node_rate)

        totals = report.totals()
        self.assertEqual(6, totals.queried_nodes)
        self.assertEqual(3, totals.nodes_with_material)
        self.assertAlmostEqual(0.5, totals.coverage)

    def test_material_count_never_substitutes_for_node_count(self):
        # returnedMaterials 可以大于 queriedNodes（一会儿多材料），覆盖率仍按节点算。
        report = R.build_report(["-"], stdin=io.StringIO(_line("math", 2, 9, 2, 0)))
        math = report.subjects["math"]
        self.assertEqual(9, math.returned_materials)
        self.assertAlmostEqual(1.0, math.coverage)

    def test_directory_scan_only_reads_log_like_files(self):
        root = Path(tempfile.mkdtemp(prefix="coverage-report-"))
        self.addCleanup(shutil.rmtree, root, True)
        (root / "a.log").write_text(_line("math", 1, 1, 1, 0), encoding="utf-8")
        (root / "nested").mkdir()
        (root / "nested" / "b.txt").write_text(_line("math", 1, 0, 0, 1), encoding="utf-8")
        (root / "c.logcat").write_text(_line("physics", 1, 1, 1, 0), encoding="utf-8")
        (root / "ignored.bin").write_text(_line("chemistry", 1, 1, 1, 0), encoding="utf-8")

        report = R.build_report([str(root)])
        self.assertEqual(3, report.files_scanned)
        self.assertEqual({"math", "physics"}, set(report.subjects))

    def test_zero_events_report_says_so_and_can_fail_strictly(self):
        root = Path(tempfile.mkdtemp(prefix="coverage-empty-"))
        self.addCleanup(shutil.rmtree, root, True)
        log = root / "empty.log"
        log.write_text("nothing here\n", encoding="utf-8")

        report = R.build_report([str(log)])
        self.assertEqual(0, report.event_count)
        text = R.render_text(report, [str(log)])
        self.assertIn("WARNING: 0 行匹配", text)

        self.assertEqual(0, R.main([str(log)]))
        self.assertEqual(1, R.main(["--require-events", str(log)]))

    def test_json_report_carries_counts_and_rates(self):
        report = R.build_report(["-"], stdin=io.StringIO(_line("math", 4, 3, 3, 1)))
        payload = json.loads(R.render_json(report, ["-"]))
        self.assertEqual(1, payload["version"])
        self.assertEqual(1, payload["matchedEvents"])
        math = payload["subjects"]["math"]
        self.assertEqual(4, math["queriedNodes"])
        self.assertEqual(3, math["nodesWithMaterial"])
        self.assertAlmostEqual(0.75, math["coverage"])
        self.assertAlmostEqual(0.25, math["emptyMaterialNodeRate"])

    def test_missing_input_returns_read_error(self):
        self.assertEqual(2, R.main(["--json", "does-not-exist.log"]))


if __name__ == "__main__":
    unittest.main()
