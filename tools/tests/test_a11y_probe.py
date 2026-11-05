# -*- coding: utf-8 -*-
"""a11y_probe.py 纯函数测试（批次 44 / I 域）。

消灭的具体失败：探针把“找不到节点坐标”静默当成通过。本测试钉住坐标解析与
审计封装：无 bounds 的节点必须返回 None，审计结果必须来自 a11y_audit 的判定
而不是探针自己的复述。
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "ci"))

import a11y_probe as P  # noqa: E402

XML = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node class="android.widget.Button" clickable="true" text="继续" bounds="[10,10][200,200]" />
  <node class="android.widget.TextView" clickable="false" text="标题" bounds="[0,300][400,340]" />
</hierarchy>
"""


class ProbeHelpersTest(unittest.TestCase):
    def test_center_parses_bounds(self) -> None:
        self.assertEqual(P.center("[10,10][200,200]"), (105, 105))
        self.assertIsNone(P.center(""))

    def test_audit_payload_flags_unlabeled_clickable(self) -> None:
        xml = XML.replace('text="继续"', 'text=""')
        result = P._audit_payload(xml, 160, None)
        self.assertIn("MISSING_LABEL", result["violation_codes"])
        self.assertFalse(result["ok"])

    def test_audit_payload_marks_contrast_unverified_without_colors(self) -> None:
        result = P._audit_payload(XML, 160, None)
        self.assertTrue(result["structural_ok"])
        self.assertEqual(result["contrast"]["status"], "UNVERIFIED")
        self.assertFalse(result["ok"])

    def test_merged_label_reads_descendant_text(self) -> None:
        """消灭的具体失败：焦点容器自身无 text，但其子节点有文本，被判“无朗读文本”。"""
        xml = """<hierarchy><node class="android.view.View" clickable="true" bounds="[0,0][100,100]">
        <node class="android.widget.TextView" text="筛选" bounds="[0,0][100,100]"/></node></hierarchy>"""
        nodes = P.A.parse_dump(xml)
        clickable = next(node for node in nodes if node["clickable"])
        self.assertEqual(P.A._merged_label(nodes, clickable["index"]), "筛选")


if __name__ == "__main__":
    unittest.main()
