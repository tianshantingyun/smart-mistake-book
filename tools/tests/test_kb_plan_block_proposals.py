# -*- coding: utf-8 -*-
"""plan_block_proposals：提案名清洗（判定员把解释写进名字时的还原）。"""

from __future__ import annotations

import unittest

from kb_coverage.plan_block_proposals import clean_name


class CleanNameTest(unittest.TestCase):
    """这些字符串全部取自真实判定产物（2026-09-30 块池轮 225 条提案）。

    消灭的失败：判定员按协议写 `NEW:BIOLOGY/DNA片段的电泳鉴定（正式区无对口节点，本条为…）`，
    名字里带了解释尾巴。直接建点会得到 119 个"整句话当名称"的垃圾节点（坏名门会拦，但提案就
    卡死没人处理）。清洗后 136 条可建点、只有 8 条仍需人工。
    """

    def test_parenthetical_explanation_stripped(self):
        self.assertEqual("DNA片段的电泳鉴定", clean_name(
            "DNA片段的电泳鉴定（正式区无对口节点，本条为电泳原理与迁移速率）"))

    def test_unclosed_parenthetical_truncation_stripped(self):
        """判定员写到一半被截断、括号没闭合的那类。"""
        self.assertEqual("内能", clean_name(
            "内能（正式区缺内能节点；本块给出内能的定义、动能与势能的组成、与温度"))

    def test_dash_explanation_stripped(self):
        self.assertEqual("准晶体", clean_name(
            "准晶体——准晶体介于晶体与非晶体之间、具有原子排列有序性但无周期性，"
            "但无平移周期性的一种固态物质"))

    def test_clean_name_untouched(self):
        """本来就是干净知识点名的一律不动。"""
        for name in ("硝酸盐的性质", "氢氧化铝沉淀图像分析", "电镀与电解精炼"):
            self.assertEqual(name, clean_name(name))

    def test_single_dash_pair_not_split(self):
        """只有一个连字符的名字不能被当成破折号切开（如 `U-I图像`）。"""
        self.assertEqual("U-I图像与I-U图像的理解", clean_name("U-I图像与I-U图像的理解"))


if __name__ == "__main__":
    unittest.main()
