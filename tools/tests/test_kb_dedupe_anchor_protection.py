# -*- coding: utf-8 -*-
"""去重锚点保护扩展（金标 expectedSlug / THIN ≤2 材料）的契约测试。

它盯的失败（对应 `docs/research/2026-11-02-kb-b33-b3-dedupe-prereg.md` §2.4）：

1. **金标节点的材料被近重复退休**：金标 `expectedSlug` 是判读基准，削薄它的材料
   不会让任何门变红，却把该题的材料交付掏空。金标文件缺失/全空时必须**报错**，
   不能静默退化成"无保护"。
2. **THIN 节点（≤2 材料）被降到 1 条或 0 条**：节点从可教变不可教（R3 的 D5）。
3. **3 材料节点必须仍可去重**：保护只能收窄，不能把整批去重变成空操作。
4. **受保护材料永不进入 retire**：三类保护合并后与 retire 交集必须为空。

用例只打桩纯函数（`build_plan` 与两个 loader），不跑全包。
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from kb_coverage import dedupe_near_dup_materials as D  # noqa: E402

SAME_CONTENT = "同一个知识点的正文：先定义，再说明适用条件，最后给出一个判断方法。"
NODE = "kb:moe-2025-four-subjects-v1:biology:atomic:测试节点"


def _material(slug: str, node_id: str = NODE, content: str = SAME_CONTENT) -> dict:
    return {
        "slug": slug,
        "subject": "BIOLOGY",
        "title": "测试标题",
        "summaryMarkdown": "摘要",
        "contentMarkdown": content,
        "bindings": [{"knowledgeNodeId": node_id, "role": "PRIMARY"}],
    }


def _groups(*materials: dict, node_id: str = NODE) -> dict[str, list[dict]]:
    return {node_id: list(materials)}


class GoldenProtectionTest(unittest.TestCase):
    def _golden(self, slugs: list[str]) -> Path:
        tmp = Path(tempfile.mkdtemp())
        path = tmp / "golden.json"
        path.write_text(json.dumps([{"expectedSlug": s, "query": "q"} for s in slugs],
                                   ensure_ascii=False), encoding="utf-8")
        return path

    def test_golden_expected_slug_protects_every_material_of_the_node(self):
        path = self._golden(["测试节点"])
        groups = _groups(_material("a"), _material("b"), _material("c"))
        protected = D.load_golden_expected_slugs(golden_path=path, groups=groups)
        self.assertEqual({"a", "b", "c"}, set(protected))
        self.assertEqual(["golden:expectedSlug"], protected["a"])

    def test_golden_node_stops_retirement(self):
        path = self._golden(["测试节点"])
        groups = _groups(_material("a"), _material("b"))
        protected = D.load_golden_expected_slugs(golden_path=path, groups=groups)
        plan = D.build_plan(groups, {}, extra_protected=protected)
        self.assertEqual([], plan["retire"])
        self.assertEqual(0, plan["counts"]["retire"])

    def test_node_not_in_golden_is_not_protected(self):
        path = self._golden(["别的节点"])
        groups = _groups(_material("a"), _material("b"))
        plan = D.build_plan(groups, {},
                            extra_protected=D.load_golden_expected_slugs(golden_path=path, groups=groups))
        self.assertEqual(["b"], [r["slug"] for r in plan["retire"]])

    def test_missing_golden_file_raises(self):
        with self.assertRaises(FileNotFoundError):
            D.load_golden_expected_slugs(golden_path=Path(tempfile.mkdtemp()) / "nope.json",
                                         groups={})

    def test_empty_expected_slugs_raise(self):
        tmp = Path(tempfile.mkdtemp()) / "golden.json"
        tmp.write_text("[]", encoding="utf-8")
        with self.assertRaises(ValueError):
            D.load_golden_expected_slugs(golden_path=tmp, groups={})


class ThinProtectionTest(unittest.TestCase):
    def test_nodes_with_two_or_fewer_materials_are_protected(self):
        single = _groups(_material("only"), node_id="kb:x:biology:atomic:单条")
        pair = _groups(_material("a"), _material("b"), node_id="kb:x:biology:atomic:两条")
        self.assertEqual({"only"}, set(D.load_thin_node_slugs(single)))
        self.assertEqual({"a", "b"}, set(D.load_thin_node_slugs(pair)))

    def test_thin_protection_keeps_pair_from_dropping_to_one(self):
        pair = _groups(_material("a"), _material("b"), node_id="kb:x:biology:atomic:两条")
        plan = D.build_plan(pair, {}, extra_protected=D.load_thin_node_slugs(pair))
        self.assertEqual([], plan["retire"], "≤2 材料的节点不得被去重削到 1 条")
        protected = D.load_thin_node_slugs(pair)
        self.assertEqual(["thin:<=2-materials"], protected["a"])

    def test_three_material_node_can_still_be_deduplicated(self):
        groups = _groups(_material("a"), _material("b"), _material("c"),
                         node_id="kb:x:biology:atomic:三条")
        plan = D.build_plan(groups, {}, extra_protected=D.load_thin_node_slugs(groups))
        self.assertEqual(2, plan["counts"]["retire"],
                         "3 材料节点不在 THIN 保护内，必须仍能去重（否则本批成了空操作）")
        self.assertEqual({"b", "c"}, {r["slug"] for r in plan["retire"]})
        self.assertEqual({"a"}, {r["kept_slug"] for r in plan["retire"]})


class MergeAndDisjointnessTest(unittest.TestCase):
    def test_merge_keeps_every_source(self):
        merged = D.merge_protections({"a": ["alias:x"]}, {"a": ["golden:expectedSlug"]},
                                     {"b": ["thin:<=2-materials"]})
        self.assertEqual(["alias:x", "golden:expectedSlug"], merged["a"])
        self.assertEqual(["thin:<=2-materials"], merged["b"])

    def test_protected_never_enters_retire(self):
        tmp = Path(tempfile.mkdtemp()) / "golden.json"
        tmp.write_text(json.dumps([{"expectedSlug": "受保护"}], ensure_ascii=False),
                       encoding="utf-8")
        protected_node = "kb:x:biology:atomic:受保护"
        free_node = "kb:x:biology:atomic:自由"
        groups = {protected_node: [_material("p1", protected_node), _material("p2", protected_node)],
                  free_node: [_material("f1", free_node), _material("f2", free_node),
                              _material("f3", free_node)]}
        protected = D.merge_protections(
            D.load_golden_expected_slugs(golden_path=tmp, groups=groups),
            D.load_thin_node_slugs(groups))
        plan = D.build_plan(groups, {}, extra_protected=protected)
        retired = {r["slug"] for r in plan["retire"]}
        self.assertEqual(set(), retired & set(protected),
                         "受保护材料进入了 retire 清单")
        self.assertEqual({"f2", "f3"}, retired)


if __name__ == "__main__":
    unittest.main()
