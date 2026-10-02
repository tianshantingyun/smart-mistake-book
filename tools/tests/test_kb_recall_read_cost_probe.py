# -*- coding: utf-8 -*-
"""召回固定读数探针（tools/kb_perf/recall_read_cost.py）的守门用例。

它消灭的失败：探针是一次性脚本时，"固定读数开销"只有一个文档里的数字（≈12.6ms），
没人能复跑、也没人会发现它悄悄测了**旧表形**。本用例保证：
- 探针在 quick 档可跑（同参数确定性），输出契约稳定（三笔读数 / 采样数 / p50）；
- 抄写的表结构与生产 schema 导出（57.json）逐名一致——schema 升级即红，
  提醒同步探针而不是让它继续测旧列。

验收映射：schema 字段/索引被改而探针未同步 ⇒ 本用例失败。
"""

from __future__ import annotations

import importlib.util
import io
import json
import unittest
from contextlib import redirect_stdout
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PROBE = REPO / "tools" / "kb_perf" / "recall_read_cost.py"
SCHEMA = REPO / ("core/database/schemas/com.tingyun.smartmistakebook.core.database."
                 "StudyDatabase/57.json")


def _load_probe():
    spec = importlib.util.spec_from_file_location("kb_perf_recall_read_cost", PROBE)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class RecallReadCostProbeTest(unittest.TestCase):
    def setUp(self):
        self.probe = _load_probe()

    def test_quick_mode_reports_three_readings(self):
        buf = io.StringIO()
        with redirect_stdout(buf):
            code = self.probe.main(["--quick", "--json"])
        self.assertEqual(0, code)
        payload = json.loads(buf.getvalue())
        self.assertEqual(3, payload["readings_per_recall"])
        self.assertEqual(
            ["readVersion", "countReviewedKnowledgeNodesBySubject",
             "countIndexedKnowledgeNodesBySubject"], payload["queries"])
        self.assertEqual(4 * 200, payload["nodes_total"])
        self.assertEqual(4 * 200 * 24, payload["features_total"])
        self.assertGreaterEqual(payload["p50_ms"], 0.0)
        self.assertGreaterEqual(payload["samples"], 1)

    def test_schema_drift_is_red(self):
        # 构造一份列名被动过手的 schema 副本：探针必须当场报错，而不是继续测旧表形。
        import tempfile

        with tempfile.TemporaryDirectory(dir=REPO / "build") as td:
            tampered = Path(td) / "57.json"
            data = json.loads(SCHEMA.read_text(encoding="utf-8"))
            for entity in data["database"]["entities"]:
                if entity["tableName"] == "knowledge_node":
                    entity["fields"][0]["columnName"] = "knowledge_node_id_v2"
            tampered.write_text(json.dumps(data), encoding="utf-8")
            conn = self.probe.build(2, 2)
            try:
                with self.assertRaises(AssertionError):
                    self.probe.assert_matches_schema(conn, tampered)
            finally:
                conn.close()
            # 真 schema 不得报错（自证：上面那次报错来自列名被改，而非函数恒抛）
            conn2 = self.probe.build(2, 2)
            try:
                self.probe.assert_matches_schema(conn2, SCHEMA)
            finally:
                conn2.close()


if __name__ == "__main__":
    unittest.main()
