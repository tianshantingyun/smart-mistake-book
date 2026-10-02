# -*- coding: utf-8 -*-
"""extraction_state：提取状态机的迁移守卫与账本完整性。"""

from __future__ import annotations

import csv
import unittest

from kb_coverage import extraction_state as es


class InitialStateTest(unittest.TestCase):
    def test_office_doc_is_pending(self):
        state, _ = es._initial_state({"content_bearing": "yes", "ext": ".docx",
                                      "text_layer": ""})
        self.assertEqual("PENDING", state)

    def test_scanned_pdf_is_pending_scanned(self):
        state, _ = es._initial_state({"content_bearing": "yes", "ext": ".pdf",
                                      "text_layer": "SCANNED_IMAGE"})
        self.assertEqual("PENDING_SCANNED", state)

    def test_binary_is_skipped(self):
        state, _ = es._initial_state({"content_bearing": "yes", "ext": ".ttf",
                                      "text_layer": ""})
        self.assertEqual("SKIPPED_NO_CONTENT", state)

    def test_multi_subject_path_is_unassigned(self):
        self.assertEqual("UNASSIGNED", es._subject("全科卷/数学+物理合订.docx"))

    def test_deep_subject_keyword_found(self):
        self.assertEqual("MATH", es._subject("2026年新高考资料/一轮复习/2026体育单招数学讲义.docx"))

    def test_topic_word_does_not_hijack_subject(self):
        """考点词含别的科目词时按**目录**定科目，不能整串判 UNASSIGNED。

        消灭的失败：`烃的衍生物`（含"生物"）、`降低化学反应活化能的酶`（含"化学"）这类
        路径被旧规则判成 UNASSIGNED（实测池内 1,469 块），到 `materialize --write`
        变成 `非四科块` 硬错误。
        """
        self.assertEqual("CHEMISTRY", es._subject(
            "2026年新高考资料(3)/一轮复习/2026年高考化学一轮复习讲义+练习+课件/第九章 有机化学基础"
            "/专题03 烃的衍生物（知识清单）（全国通用）（学生版）.docx"))
        self.assertEqual("BIOLOGY", es._subject(
            "2026年新高考资料(4)/一轮复习/【2】2026届高中生物学 一轮复习课件"
            "/第8讲 降低化学反应活化能的酶 .pptx"))

    def test_shallowest_subject_component_wins(self):
        """物理文件里的 `数学归纳法` 不得把科目判成数学（deepest-match 会错）。"""
        self.assertEqual("PHYSICS", es._subject(
            "2026年新高考资料(1)/二轮复习/2026版物理二轮复习/2026版 大二轮 物理 （培优版）"
            "/教师用书Word版文档/专题二 计算题培优练3 图像法或数学归纳法解决多次碰撞问题.docx"))

    def test_subject_of_path_contract(self):
        """`subject_of_path` 是**唯一口径**：三个下游工具（合并/去重命名/建点规划）都调它。

        真不可判（全科卷）返回 None —— 下游据此报"学科未识别"而不是猜一个科目。
        """
        self.assertEqual("BIOLOGY", es.subject_of_path(
            "2026年新高考资料(4)/一轮复习/【2】2026届高中生物学 一轮复习课件"
            "/第8讲 降低化学反应活化能的酶 .pptx"))
        self.assertIsNone(es.subject_of_path("全科卷/数学+物理合订.docx"))


class TransitionTest(unittest.TestCase):
    def _table(self, tmp):
        (tmp / "extraction_state.csv").write_text(
            "rel_path,subject,ext,state,output_ref,tool,note,updated_at\n"
            "a.docx,MATH,.docx,PENDING,,,init,2026-09-18 00:00:00\n"
            "b.pdf,MATH,.pdf,PENDING_SCANNED,,,init,2026-09-18 00:00:00\n"
            "c.pdf,MATH,.pdf,ERROR,,phase2,崩了,2026-09-18 00:00:00\n",
            encoding="utf-8")
        return tmp / "extraction_state.csv"

    def test_forward_mark(self):
        import tempfile, pathlib
        with tempfile.TemporaryDirectory() as d:
            path = self._table(pathlib.Path(d))
            es.mark({"a.docx": ("EXTRACTED", "m1,m2")}, "phase2", path)
            row = es.load_states(path)["a.docx"]
            self.assertEqual("EXTRACTED", row["state"])
            self.assertEqual("m1,m2", row["output_ref"])

    def test_error_can_retry(self):
        import tempfile, pathlib
        with tempfile.TemporaryDirectory() as d:
            path = self._table(pathlib.Path(d))
            es.mark({"c.pdf": ("PENDING_SCANNED", "")}, "retry", path)
            self.assertEqual("PENDING_SCANNED", es.load_states(path)["c.pdf"]["state"])

    def test_illegal_transition_raises(self):
        import tempfile, pathlib
        with tempfile.TemporaryDirectory() as d:
            path = self._table(pathlib.Path(d))
            es.mark({"a.docx": ("ERROR", "")}, "phase2", path)  # 合法迁移
            with self.assertRaises(ValueError):
                es.mark({"a.docx": ("EXTRACTED", "x"), }, "phase2", path)  # ERROR 只能回 PENDING*
            with self.assertRaises(ValueError):
                es.mark({"nope.docx": ("REJECTED", "")}, "phase2", path)   # 不存在


if __name__ == "__main__":
    unittest.main()
