# -*- coding: utf-8 -*-
"""近重复去重（kb_coverage.dedupe_near_dup_materials）的契约测试。

它盯的失败（v27 实测）：
1. 去重只按逐字相同判重，漏掉学生版/教师版、两份教辅之间的近重复；
2. 去重把 `content_audit_anchors.csv` 里的 I-04 锚点
   `bio-kb-chromatin-chromosome-same-substance` 退休，`test_i04` 变红；
3. 纯 Jaccard 0.70 会把"同一模板换实体"的表格式材料判成重复，退休后丢知识点
   （核糖/脱氧核糖、果糖/半乳糖、蔗糖/乳糖、温度/pH）。

用例只打桩纯函数与台账，不跑全包（全包 dry 由命令本身跑）。
"""

from __future__ import annotations

import csv
import unittest
from pathlib import Path

import sys

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from kb_coverage import dedupe_near_dup_materials as D  # noqa: E402


def _material(slug: str, title: str, summary: str, content: str) -> dict:
    return {
        "slug": slug,
        "subject": "BIOLOGY",
        "title": title,
        "summaryMarkdown": summary,
        "contentMarkdown": content,
        "bindings": [{"knowledgeNodeId": "kb:x:biology:atomic:n", "role": "PRIMARY"}],
    }


class TextTest(unittest.TestCase):
    def test_normalize_removes_case_and_whitespace_but_keeps_punctuation(self):
        self.assertEqual("ab。c", D.normalize_text(" A B 。\tC "))
        self.assertEqual("ab", D.normalize_text("ＡＢ"))

    def test_minhash_signature_is_deterministic_and_value_sensitive(self):
        left = D.shingle_hashes(_material("a", "标题", "摘要", "正文内容一二三"))
        again = D.shingle_hashes(_material("a", "标题", "摘要", "正文内容一二三"))
        other = D.shingle_hashes(_material("b", "标题", "摘要", "完全不同的正文"))
        self.assertEqual(D.minhash_signature(left), D.minhash_signature(again))
        self.assertNotEqual(D.minhash_signature(left), D.minhash_signature(other))
        self.assertEqual(D.NUM_PERM, len(D.minhash_signature(left)))

    def test_exact_jaccard_identity_and_threshold(self):
        left = D.shingle_hashes(_material("a", "t", "s", "同一段正文"))
        self.assertEqual(1.0, D.exact_jaccard(left, left))
        self.assertEqual(0.0, D.exact_jaccard(left, frozenset()))


class SubstantiveTokenTest(unittest.TestCase):
    def test_table_row_entity_substitution_is_flagged(self):
        kept = "单糖；种类：五碳糖；脱氧核糖；定义：不能水解的糖类；主要功能：DNA的组成成分"
        retired = "单糖；种类：五碳糖；核糖；定义：不能水解的糖类；主要功能：RNA的组成成分"
        hits = D.substantive_substitutions(kept, retired)
        self.assertTrue(hits, "核糖/脱氧核糖 必须被判成实体替换")

    def test_formatting_and_enumeration_differences_are_not_flagged(self):
        kept = "1. 定义：使物体在平衡位置附近做往复运动的力。\n2. 方向：总是指向平衡位置。"
        retired = "①定义：使物体在平衡位置附近做往复运动的力。\n②方向：总是指向平衡位置。"
        self.assertEqual([], D.substantive_substitutions(kept, retired))


class KeepRuleTest(unittest.TestCase):
    def test_longer_content_wins_then_suffix_then_slug(self):
        short = _material("x-001b", "t", "s", "短")
        long = _material("x-002", "t", "s", "更长的正文")
        same_a = _material("x-001c", "t", "s", "等长正文")
        same_b = _material("x-001", "t", "s", "等长正文")
        ordered = sorted([short, long, same_a, same_b], key=D.keep_rank)
        self.assertEqual("x-002", ordered[0]["slug"], "正文更长者必须优先")
        self.assertEqual("x-001", ordered[1]["slug"], "等长时 slug 后缀更短/字典序更小者优先")


class AnchorProtectionTest(unittest.TestCase):
    def test_retire_ledger_never_contains_anchors_or_referenced_slugs(self):
        ledger = D.RETIRE_LEDGER
        if not ledger.exists():
            self.skipTest("尚无退休台账（本批尚未执行）")
        with ledger.open(encoding="utf-8", newline="") as handle:
            slugs = {(row.get("slug") or "").strip() for row in csv.DictReader(handle)}
        slugs.discard("")
        self.assertTrue(slugs, "退休台账不该只有表头")
        protected = D.load_anchor_slugs(known_slugs=slugs)
        self.assertEqual({}, protected,
                         f"退休台账里出现被 tests/gates/锚点表引用的材料：{sorted(protected)}")


if __name__ == "__main__":
    unittest.main()
