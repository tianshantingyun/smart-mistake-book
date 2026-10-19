# -*- coding: utf-8 -*-
"""终验逐节台账 join 的单元测试（自造小树 / 小表夹具）。

先红后绿纪律：本文件先于 `tools/kb_coverage/final_coverage_join.py` 落盘，
首跑应为 ImportError（模块尚不存在）；实现后同一命令转绿。
"""
from __future__ import annotations

import csv
import json
import tempfile
import unittest
from pathlib import Path

from kb_coverage.final_coverage_join import (
    ALL_VOLUMES,
    OFFICIAL_SECTION_COUNTS,
    chapter_numbers,
    is_non_section_row,
    load_curriculum,
    load_pack_index,
    normalize_name,
    parse_final_dispositions,
    parse_final_table,
    parse_reports,
    self_check,
    join_rows,
    render_ledger,
)


REPORT_HEADER = "| 官方章 | 官方节 | W-7 | 现包现状 |\n|---|---|---|---|\n"


class Fixture:
    """最小夹具：1 册 / 1 章 / 5 官方节 + 1 非节行、2 个主题、5 个点、6 条材料。"""

    def __init__(self, root: Path) -> None:
        self.root = root
        self.docs = root / "docs"
        self.docs.mkdir(parents=True)
        (self.docs / "reference").mkdir()
        (self.docs / "CHEMISTRY.md").write_text(
            "## 逐册对照\n### 化学必修第一册（fixture）\n" + REPORT_HEADER
            + "| 第一章 物质及其变化 | 第一节 物质的分类及转化 | ✅ | 同名 第一章·物质分类与转化·物质的分类及转化（1 点） |\n"
            + "| 第一章 物质及其变化 | 第二节 离子反应 | ✅ | 同名 第一章·离子反应与氧化还原·离子反应（1 点） |\n"
            + "| 第一章 物质及其变化 | 第三节 物质的量 | ✅ | 同名 第二章·物质的量（1 点） |\n"
            + "| 第一章 物质及其变化 | 第四节 氧化还原反应 | ✅ | 同名 第一章·离子反应与氧化还原·氧化还原反应（1 点） |\n"
            + "| 第一章 物质及其变化 | 第五节 氮及其化合物 | ⚠️合并 | 硫氮及其化合物→第一章·硫氮及其化合物（1 点） |\n"
            + "| 第一章 物质及其变化 | （官方无此节） | ⚠️错位 | 电离→第一章·电离平衡（1 点） |\n",
            encoding="utf-8",
        )
        for name in ("BIOLOGY.md", "PHYSICS.md", "MATH.md"):
            (self.docs / name).write_text("## 逐册对照\n", encoding="utf-8")

        (self.docs / "FINAL-COVERAGE-TABLE.md").write_text(
            "## 3. 逐册对照\n"
            "### 3.1 化学\n"
            "#### 化学必修第一册（官方 1 章 / 5 节，另 1 行非节）\n"
            + REPORT_HEADER.replace("| W-7 | 现包现状 |", "| W-7 | 现包现状 | 注记 |").replace("|---|---|---|---|", "|---|---|---|---|---|")
            + "| 第一章 物质及其变化 | 第一节 物质的分类及转化 | ✅ | 同名 第一章·物质分类与转化·物质的分类及转化（1 点） | — |\n"
            + "| 第一章 物质及其变化 | 第二节 离子反应 | ✅ | 同名 第一章·离子反应与氧化还原·离子反应（1 点） | — |\n"
            + "| 第一章 物质及其变化 | 第三节 物质的量 | ✅ | 同名 第二章·物质的量（1 点） | — |\n"
            + "| 第一章 物质及其变化 | 第四节 氧化还原反应 | ✅ | 同名 第一章·离子反应与氧化还原·氧化还原反应（1 点） | — |\n"
            + "| 第一章 物质及其变化 | 第五节 氮及其化合物 | ⚠️合并 | 硫氮及其化合物→第一章·硫氮及其化合物（1 点） | — |\n"
            + "| 第一章 物质及其变化 | （官方无此节） | ⚠️错位 | 电离→第一章·电离平衡（1 点） | — |\n"
            + "\n## 4. 现包侧结构偏差台账\n"
            + "### 4.1 伪章节点\n"
            + "- `化学必修第一册·第二章第三节`（0 点，1 个子主题） ｜ 批11 已处置：kit-01#1 DELETE（伪章）；子主题 物质的量（1 点）落 化学必修第一册·第二章·物质的量\n"
            + "### 4.2 章层缺失 / 未带官方章名\n"
            + "| 册 | 章节点数 | 带官方章名 | 现章节点名 |\n|---|---|---|---|\n"
            + "| 化学选择性必修2 | 1 | 0/3 | 物质结构与性质 ｜ 批11 已处置：kit-05#4 NEW_TOPIC 建三章 |\n"
            + "### 4.3 跨册错位（官方节 ↔ 现包位置，刷新后仍存在）\n"
            + "| 官方节（官方册） | 现包位置 | 点数 |\n|---|---|---|\n"
            + "| 第二节 离子反应（化学必修第一册） | `化学必修第一册·第一章·离子反应与氧化还原·离子反应` | 1 ｜ 批11 已处置：kit-02#9 RELOCATE→必修一 第一章·离子反应 |\n"
            + "### 4.4 册级游离主题（无册前缀，挂学科根）\n"
            + "- 铝及其化合物(1点) ｜ 批11 已处置：kit-06#14 RELOCATE→必修一·第三章·金属材料节位\n"
            + "### 4.5 缺失节（现包仍无同名节点）\n"
            + "| 学科 | 官方册 | 官方节 | W-7 |\n|---|---|---|---|\n"
            + "| 化学 | 化学必修第一册 | 第四节 氧化还原反应 | ❌缺失 ｜ 批11 已处置：kit-04#1 NEW_TOPIC→化学必修第一册·第一章·氧化还原反应 |\n"
            + "\n## 批11 F2 残差处置记录（fixture）\n"
            + "### 11.2.1 kit-02（化学，1 行）\n"
            + "| item_id | disposition | 旧 → 新／处置 |\n|---|---|---|\n"
            + "| kit-02#9 | RELOCATE | 必修一·电离平衡 → 选必1·电离平衡 |\n"
            + "### 11.6 台账逐行标注对账与表外项\n"
            + "| item_id | 处置 | 说明（为何表外） |\n|---|---|---|\n"
            + "| kit-05#1 | WAIVE | 化学必修第一册：章层 4/4 齐备、无缺章 |\n",
            encoding="utf-8",
        )

        (self.docs / "reference" / "standard_chapter_names.json").write_text(
            json.dumps({"化学必修第一册": ["物质及其变化"]}, ensure_ascii=False), encoding="utf-8")
        (self.docs / "reference" / "chapter_structure_full.json").write_text(
            json.dumps({"化学必修第一册": [{"name": "第一章 物质及其变化", "secs": ["第一节 物质的分类及转化"]}]},
                       ensure_ascii=False), encoding="utf-8")

        self.pack = root / "pack.json"
        self.pack.write_text(json.dumps({
            "schemaVersion": 2,
            "packId": "fixture",
            "taxonomyVersion": "fixture",
            "subjects": [
                {"subject": "CHEMISTRY", "topics": [
                    {"slug": "化学必修第一册", "name": "化学必修第一册", "knowledgePoints": []},
                    {"slug": "化学必修第一册·第一章", "name": "物质及其变化", "parentSlug": "化学必修第一册", "knowledgePoints": []},
                    {"slug": "化学必修第一册·第一章·物质分类与转化", "name": "物质分类与转化", "parentSlug": "化学必修第一册·第一章", "knowledgePoints": []},
                    {"slug": "化学必修第一册·第一章·物质分类与转化·物质的分类及转化", "name": "物质的分类及转化", "parentSlug": "化学必修第一册·第一章·物质分类与转化", "knowledgePoints": [{"slug": "物质的分类", "name": "物质的分类"}]},
                    {"slug": "化学必修第一册·第一章·离子反应与氧化还原", "name": "离子反应与氧化还原", "parentSlug": "化学必修第一册·第一章", "knowledgePoints": []},
                    {"slug": "化学必修第一册·第一章·离子反应与氧化还原·离子反应", "name": "离子反应", "parentSlug": "化学必修第一册·第一章·离子反应与氧化还原", "knowledgePoints": [{"slug": "离子", "name": "离子"}]},
                    {"slug": "化学必修第一册·第一章·离子反应与氧化还原·氧化还原反应", "name": "氧化还原反应", "parentSlug": "化学必修第一册·第一章·离子反应与氧化还原", "knowledgePoints": [{"slug": "氧化还原反应", "name": "氧化还原反应"}]},
                    {"slug": "化学必修第一册·第二章", "name": "海水中的重要元素——钠和氯", "parentSlug": "化学必修第一册", "knowledgePoints": []},
                    {"slug": "化学必修第一册·第二章·物质的量", "name": "物质的量", "parentSlug": "化学必修第一册·第二章", "knowledgePoints": [{"slug": "物质的量", "name": "物质的量"}]},
                    {"slug": "化学必修第一册·第三章", "name": "铁 金属材料", "parentSlug": "化学必修第一册", "knowledgePoints": []},
                    {"slug": "化学必修第一册·第三章·金属材料", "name": "金属材料", "parentSlug": "化学必修第一册·第三章", "knowledgePoints": [{"slug": "铝及其化合物", "name": "铝及其化合物"}]},
                    {"slug": "化学必修第一册·第四章", "name": "物质结构 元素周期律", "parentSlug": "化学必修第一册", "knowledgePoints": []},
                    {"slug": "化学必修第一册·第四章·硫氮及其化合物", "name": "硫氮及其化合物", "parentSlug": "化学必修第一册·第四章", "knowledgePoints": [{"slug": "氮的化合物", "name": "氮的化合物"}]},
                ]},
                {"subject": "CHEMISTRY", "topics": [
                    {"slug": "化学选择性必修1", "name": "化学选择性必修1", "knowledgePoints": []},
                    {"slug": "化学选择性必修1·第一章", "name": "化学反应的热效应", "parentSlug": "化学选择性必修1", "knowledgePoints": []},
                    {"slug": "化学选择性必修1·第一章·物质分类与转化", "name": "物质的分类及转化", "parentSlug": "化学选择性必修1·第一章", "knowledgePoints": [{"slug": "跨册同位名点", "name": "跨册同位名点"}]},
                ]},
            ],
        }, ensure_ascii=False), encoding="utf-8")

        self.sidecar = root / "sidecar.json"
        self.sidecar.write_text(json.dumps({"materials": [
            {"slug": "m1", "subject": "CHEMISTRY", "bindings": [{"knowledgeNodeId": "kb:fixture:chemistry:atomic:物质的分类"}]},
            {"slug": "m2", "subject": "CHEMISTRY", "bindings": [{"knowledgeNodeId": "kb:fixture:chemistry:atomic:物质的分类"}]},
            {"slug": "m3", "subject": "CHEMISTRY", "bindings": [{"knowledgeNodeId": "kb:fixture:chemistry:atomic:离子"}]},
            {"slug": "m4", "subject": "CHEMISTRY", "bindings": [{"knowledgeNodeId": "kb:fixture:chemistry:atomic:氧化还原反应"}]},
            {"slug": "m5", "subject": "CHEMISTRY", "bindings": [{"knowledgeNodeId": "kb:fixture:chemistry:atomic:物质的量"}]},
            {"slug": "m6", "subject": "CHEMISTRY", "bindings": [{"knowledgeNodeId": "kb:fixture:chemistry:atomic:氮的化合物"}]},
        ]}, ensure_ascii=False), encoding="utf-8")

        self.curriculum = root / "curriculum.csv"
        with self.curriculum.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=["subject", "candidate_slug", "status", "target_node_slug",
                                               "evidence_phrase", "note"])
            w.writeheader()
            for row in [
                ("CHEMISTRY", "c1", "COVERED", "物质的分类"),
                ("CHEMISTRY", "c2", "PARTIAL", "离子"),
                ("CHEMISTRY", "c3", "COVERED", "氮的化合物"),
                ("CHEMISTRY", "c4", "COVERED", "物质的量"),
                ("CHEMISTRY", "c5", "NOT_COVERED", ""),
            ]:
                w.writerow(dict(zip(["subject", "candidate_slug", "status", "target_node_slug",
                                     "evidence_phrase", "note"], row + ("ev", "note"))))


class ParsingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.fx = Fixture(Path(self.tmp.name))

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def test_normalize_name_strips_section_numbering_and_spaces(self) -> None:
        self.assertEqual("物质的分类及转化", normalize_name("第一节 物质的分类及转化"))
        self.assertEqual("集合的概念", normalize_name("1.1 集合的概念"))
        self.assertEqual("质点参考系", normalize_name("1　质点　参考系"))

    def test_chapter_numbers_handles_merged_chapters(self) -> None:
        self.assertEqual({"五", "六"}, chapter_numbers("第五六章"))
        self.assertEqual({"一", "二", "三"}, chapter_numbers("第一至三章"))
        self.assertEqual({"十一"}, chapter_numbers("第十一章"))
        self.assertEqual({"九", "十"}, chapter_numbers("第九十章"))

    def test_parse_reports_counts_sections_and_non_sections(self) -> None:
        rows = parse_reports(self.fx.docs)
        self.assertEqual(6, len(rows))
        sections = [r for r in rows if not is_non_section_row(r)]
        nonsec = [r for r in rows if is_non_section_row(r)]
        self.assertEqual(5, len(sections))
        self.assertEqual(1, len(nonsec))
        self.assertEqual("CHEMISTRY", sections[0].subject)
        self.assertEqual("化学必修第一册", sections[0].volume)

    def test_parse_final_table_maps_every_report_row(self) -> None:
        table = parse_final_table(self.fx.docs / "FINAL-COVERAGE-TABLE.md")
        rows = parse_reports(self.fx.docs)
        for row in rows:
            cell = table.get(row.volume, row.chapter, row.section)
            self.assertIsNotNone(cell, (row.volume, row.chapter, row.section))
            self.assertTrue(cell.status)


class JoinTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.fx = Fixture(Path(self.tmp.name))
        self.index = load_pack_index(self.fx.pack, [self.fx.sidecar])
        self.final = parse_final_table(self.fx.docs / "FINAL-COVERAGE-TABLE.md")
        self.dispositions = parse_final_dispositions(self.fx.docs / "FINAL-COVERAGE-TABLE.md")
        self.curriculum = load_curriculum(self.fx.curriculum)
        self.rows = parse_reports(self.fx.docs)
        self.ledger = join_rows(self.rows, self.index, self.final, self.dispositions, self.curriculum)

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def test_pack_index_counts_subtree_materials(self) -> None:
        self.assertEqual(2, len(self.index.subtree_materials[
            "化学必修第一册·第一章·物质分类与转化·物质的分类及转化"]))
        self.assertEqual(1, len(self.index.subtree_materials[
            "化学必修第一册·第一章·离子反应与氧化还原·离子反应"]))
        self.assertEqual(6, self.index.total_materials)

    def test_match_prefers_same_volume_and_chapter(self) -> None:
        row = next(r for r in self.rows if r.section.endswith("物质的分类及转化"))
        entry = next(e for e in self.ledger if e.official is row)
        self.assertEqual(["化学必修第一册·第一章·物质分类与转化·物质的分类及转化"],
                         [t.slug for t in entry.landing])

    def test_join_row_reports_landing_material_and_curriculum(self) -> None:
        row = next(r for r in self.rows if r.section.endswith("物质的分类及转化"))
        entry = next(e for e in self.ledger if e.official is row)
        self.assertEqual(2, entry.material_count)
        self.assertEqual({"COVERED": 1}, dict(entry.curriculum_status))
        self.assertEqual([("物质的分类", "COVERED")], entry.curriculum_targets)

    def test_join_row_falls_back_to_final_covering_topic(self) -> None:
        row = next(r for r in self.rows if r.section.endswith("氮及其化合物"))
        entry = next(e for e in self.ledger if e.official is row)
        self.assertEqual(["化学必修第一册·第四章·硫氮及其化合物"], [t.slug for t in entry.landing])
        self.assertEqual("final_arrow", entry.landing_source)
        self.assertEqual(1, entry.material_count)

    def test_dispositions_attach_from_final_sections(self) -> None:
        by_section = {e.official.section: e for e in self.ledger if e.is_section}
        self.assertIn("kit-01#1", " ".join(d.text for d in by_section["第三节 物质的量"].dispositions))
        self.assertIn("kit-02#9", " ".join(d.text for d in by_section["第二节 离子反应"].dispositions))
        self.assertIn("kit-04#1", " ".join(d.text for d in by_section["第四节 氧化还原反应"].dispositions))
        all_text = " ".join(d.text for e in self.ledger for d in e.dispositions)
        self.assertIn("kit-05#1", all_text)
        # §4.2 的册级章层条目已解析（该册不在小夹具树中，故只验证解析，不验证挂行）。
        self.assertTrue(any(d.kind == "chapter" and "kit-05#4" in d.text
                            for d in parse_final_dispositions(self.fx.docs / "FINAL-COVERAGE-TABLE.md")))

    def test_self_check_reports_fixture_count_mismatch(self) -> None:
        report = self_check(self.ledger, self.index, self.curriculum, self.fx.docs)
        self.assertFalse(report.ok)
        joined = "\n".join(report.errors)
        self.assertIn("官方节行数", joined)
        self.assertIn("非节行数", joined)

    def test_render_ledger_has_expected_row_counts(self) -> None:
        markdown = render_ledger(self.ledger, self.index, self.curriculum, self.fx.docs,
                                 pack_path=self.fx.pack)
        self.assertIn("5 官方节 + 1 非节 = 6", markdown)
        self.assertIn("官方节", markdown)
        self.assertIn("非节", markdown)


if __name__ == "__main__":
    unittest.main()
