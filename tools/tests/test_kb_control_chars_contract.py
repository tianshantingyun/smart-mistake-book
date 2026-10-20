# -*- coding: utf-8 -*-
"""`kb_build.gate.control_chars` 与数据库契约同源的测试。

契约判据（KnowledgeTeachingMaterialContract.kt:216-229）：
    Char.isISOControl() && character !in "\n\r\t"
即 C0(0x00-0x1F) 去掉 \n \r \t，加上 DEL(0x7F) 与 C1(0x80-0x9F)。

旧门集合只抽查 FF/TAB/CR/BS/BEL/VT：既漏掉 C0 其余码位与 C1/DEL，又把契约允许的
TAB/CR 当缺陷。这里把"必须命中"和"必须不命中"两侧都钉住，避免下次扩集时静默放宽。
"""
from __future__ import annotations

import unittest
from pathlib import Path

from kb_build import gate

ALLOWED = "\n\r\t"
EXPECTED = (
    {chr(code) for code in range(0x20) if chr(code) not in ALLOWED}
    | {chr(0x7F)}
    | {chr(code) for code in range(0x80, 0xA0)}
)


def _material(**fields) -> list[tuple[Path, dict]]:
    base = {"slug": "ext-mat-test-001", "subject": "MATH",
            "title": "标题", "summaryMarkdown": "概要",
            "applicabilityMarkdown": "适用性", "contentMarkdown": "正文",
            "boundaryMarkdown": "边界"}
    base.update(fields)
    return [(Path("fixture.json"), base)]


class ControlCharsContractTest(unittest.TestCase):

    def test_char_set_is_exactly_contract_iso_controls_minus_newline_cr_tab(self):
        self.assertEqual(EXPECTED, set(gate._CONTROL_CHARS))
        # 旧集合抽查过的 6 个字符里，TAB/CR 现在必须放行，其余仍拒。
        for allowed in ("\t", "\r", "\n"):
            self.assertNotIn(allowed, gate._CONTROL_CHARS)
        for rejected in ("\x07", "\x08", "\x0b", "\x0c"):
            self.assertIn(rejected, gate._CONTROL_CHARS)
        # 旧集合完全没覆盖的两段。
        self.assertIn("\x00", gate._CONTROL_CHARS)
        self.assertIn("\x1f", gate._CONTROL_CHARS)
        self.assertIn("\x7f", gate._CONTROL_CHARS)
        self.assertIn("\x80", gate._CONTROL_CHARS)
        self.assertIn("\x9f", gate._CONTROL_CHARS)
        # 0xA0 已不是 ISO 控制（NBSP），不得误收。
        self.assertNotIn("\xa0", gate._CONTROL_CHARS)

    def test_newline_cr_tab_are_legal_and_not_flagged(self):
        metrics = gate.control_chars_metric(_material(
            title="标题\t分区", contentMarkdown="第一行\n第二行\r\n第三行"))
        self.assertEqual(0, metrics.value, metrics.detail)
        self.assertTrue(metrics.ok)

    def test_each_newly_covered_segment_is_flagged_with_field_name(self):
        for ch in ("\x00", "\x01", "\x0e", "\x1f", "\x7f", "\x80", "\x9f"):
            with self.subTest(char=f"U+{ord(ch):04X}"):
                metrics = gate.control_chars_metric(_material(title=f"标题{ch}残迹"))
                self.assertEqual(1, metrics.value)
                self.assertFalse(metrics.ok)
                self.assertIn("title", metrics.detail[0])

    def test_content_and_boundary_fields_are_scanned(self):
        for field in ("contentMarkdown", "boundaryMarkdown"):
            with self.subTest(field=field):
                metrics = gate.control_chars_metric(_material(**{field: "x\x01y"}))
                self.assertEqual(1, metrics.value)
                self.assertIn(field, metrics.detail[0])

    def test_non_iso_unicode_is_not_swept_in(self):
        # NBSP / 零宽空格 / 方向标记都不是 ISO 控制字符：本门只对齐契约的 ISO 控制段。
        for ch in ("\u00a0", "\u200b", "\u200e"):
            with self.subTest(char=f"U+{ord(ch):04X}"):
                metrics = gate.control_chars_metric(_material(contentMarkdown=f"a{ch}b"))
                self.assertEqual(0, metrics.value, metrics.detail)


if __name__ == "__main__":
    unittest.main()
