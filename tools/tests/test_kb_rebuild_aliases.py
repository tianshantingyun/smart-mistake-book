# -*- coding: utf-8 -*-
"""别名重建的用例：别名必须"有据可依"且"全局互斥"。

它消灭的失败：别名曾被字符串机械匹配污染（`函数的概念` 挂着 `导数的概念与导函数`），
而别名是检索打分（权重 7／精确短语加成 70）与模型选点的输入 —— 指向别的概念
就会把匹配引到错误节点上。这些用例钉住三条判据：形状合格、必须来自本节点绑定材料标题、
同学科内一个名字只归一个节点。
"""

from __future__ import annotations

import csv
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from kb_build import rebuild_aliases as ra

EXCEPTIONS_FILE = "alias_exceptions.csv"


def write_exceptions(tables_dir: Path, rows: list[tuple[str, str, str, str]]) -> Path:
    """按真实表头写一份例外表（测试夹具）。"""
    tables_dir.mkdir(parents=True, exist_ok=True)
    path = tables_dir / EXCEPTIONS_FILE
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["subject", "slug", "alias", "reason"])
        writer.writerows(rows)
    return path


def point(slug: str, name: str, aliases: list[str] | None = None) -> dict:
    return {"slug": slug, "name": name, "aliases": aliases or [name], "kind": "CONCEPT",
            "boundary": f"定位：某册 某章。{name}的边界。", "sourceLocator": "《测试》",
            "prerequisiteSlugs": []}


def pack_with(points: list[tuple[str, dict]]) -> dict:
    """points: (topic_slug, point) —— 都挂在同一个章下的不同主题里。"""
    topics = [{"slug": "某册·某章", "name": "某章", "parentSlug": "某册",
               "sourceLocator": "定位：某册·某章", "knowledgePoints": []}]
    for topic_slug, pt in points:
        topics.append({"slug": topic_slug, "name": topic_slug.split("·")[-1],
                       "parentSlug": "某册·某章", "sourceLocator": f"定位：{topic_slug}",
                       "knowledgePoints": [pt]})
    return {"schemaVersion": 2, "packId": "t", "taxonomyVersion": "t", "sourceNamespace": "n",
            "reviewedAtEpochMillis": 1, "sourceUri": "https://e.t",
            "coverage": {"baselineId": "b", "catalogLevel": "PARTIAL",
                         "teachingSupportLevel": "PARTIAL"},
            "subjects": [{"subject": "MATH", "sourceFingerprint": "A" * 64, "topics": topics}]}


class TitleUsableTest(unittest.TestCase):
    def test_short_noun_phrase_is_usable(self):
        self.assertIsNone(ra.title_usable("同素异形体"))

    def test_sentence_like_titles_rejected(self):
        self.assertIsNotNone(ra.title_usable("下列判断正确的是（　）"))
        self.assertIsNotNone(ra.title_usable("已知函数 f(x) 在区间上单调递增"))

    def test_long_and_truncated_titles_rejected(self):
        self.assertIsNotNone(ra.title_usable("物体看作质点的条件：物体的大小和形状对研究问题的影响可以忽略"))
        # 逗号出现即判句子片段（名字里不会有逗号）
        self.assertIsNotNone(ra.title_usable("奇偶性性质法求参：拆出含参部分，由不含参"))
        self.assertIsNotNone(ra.title_usable("$\frac{1}{2}$ 的系数"))

    def test_dangling_suffix_and_unbalanced_bracket_rejected(self):
        self.assertIsNotNone(ra.title_usable("动量定理在电磁感应中的"))
        self.assertIsNotNone(ra.title_usable("（实验）探究加速度与力的关系（"))
        self.assertIsNone(ra.title_usable("（实验）探究加速度与力的关系"),
                          "括号配对的书名式标题是合法别名")

    def test_textbook_style_colon_title_is_kept(self):
        """教材节名带冒号（实验：探究平抛运动的特点）是好别名，不该被逗号规则误伤。"""
        self.assertIsNone(ra.title_usable("实验：探究平抛运动的特点"))


class RebuildTest(unittest.TestCase):
    def _plan(self):
        pack = pack_with([
            ("某册·某章·函数", point("函数的概念", "函数的概念",
                                     ["函数的概念", "导数的概念与导函数"])),
            ("某册·某章·导数", point("导数的概念", "导数的概念")),
        ])
        titles = {
            ("MATH", "函数的概念"): {"函数的概念与三要素", "题面：下列判断正确的是（　）"},
            ("MATH", "导数的概念"): {"导数的概念与导函数"},
        }
        return pack, ra.rebuild(pack, titles)

    def test_aliases_come_only_from_bound_titles(self):
        _, plan = self._plan()
        self.assertEqual({"函数的概念与三要素"}, plan["candidates"][("MATH", "函数的概念")])
        self.assertEqual({"导数的概念与导函数"}, plan["candidates"][("MATH", "导数的概念")])

    def test_rejected_candidates_are_recorded_with_reason(self):
        _, plan = self._plan()
        reasons = {(d["slug"], d["candidate"]): d["reason"] for d in plan["dropped"]}
        self.assertIn(("函数的概念", "题面：下列判断正确的是（　）"), reasons)

    def test_applied_aliases_keep_name_first_without_duplicates(self):
        pack, plan = self._plan()
        ra.apply_to_pack(pack, plan)
        node = pack["subjects"][0]["topics"][1]["knowledgePoints"][0]
        self.assertEqual("函数的概念", node["aliases"][0])
        self.assertEqual(len(set(node["aliases"])), len(node["aliases"]))
        self.assertNotIn("导数的概念与导函数", node["aliases"], "污染别名必须被清掉")

    def test_name_duplicate_candidate_is_dropped(self):
        pack = pack_with([("某册·某章·甲", point("甲概念", "甲概念"))])
        plan = ra.rebuild(pack, {("MATH", "甲概念"): {"甲概念"}})
        self.assertEqual(set(), plan["candidates"][("MATH", "甲概念")])
        ra.apply_to_pack(pack, plan)
        node = pack["subjects"][0]["topics"][1]["knowledgePoints"][0]
        self.assertEqual(["甲概念"], node["aliases"])

    def test_candidate_equal_to_other_node_name_is_dropped(self):
        pack = pack_with([
            ("某册·某章·甲", point("甲概念", "甲概念")),
            ("某册·某章·乙", point("乙概念", "乙概念")),
        ])
        plan = ra.rebuild(pack, {("MATH", "甲概念"): {"乙概念"}})
        self.assertEqual(set(), plan["candidates"][("MATH", "甲概念")])
        reasons = [d["reason"] for d in plan["dropped"] if d["slug"] == "甲概念"]
        self.assertTrue(any("主名相同" in r for r in reasons), reasons)

    def test_candidate_claimed_by_two_nodes_is_dropped_globally(self):
        pack = pack_with([
            ("某册·某章·甲", point("甲概念", "甲概念")),
            ("某册·某章·乙", point("乙概念", "乙概念")),
        ])
        plan = ra.rebuild(pack, {("MATH", "甲概念"): {"共用别名"},
                                 ("MATH", "乙概念"): {"共用别名"}})
        self.assertEqual(set(), plan["candidates"][("MATH", "甲概念")])
        self.assertEqual(set(), plan["candidates"][("MATH", "乙概念")])
        reasons = {d["reason"] for d in plan["dropped"]}
        self.assertTrue(any("同时 claim" in r for r in reasons), reasons)

    def test_rebuild_is_idempotent(self):
        pack, plan = self._plan()
        ra.apply_to_pack(pack, plan)
        again = ra.rebuild(pack, {("MATH", "函数的概念"): {"函数的概念与三要素"},
                                  ("MATH", "导数的概念"): {"导数的概念与导函数"}})
        stats = ra.apply_to_pack(pack, again)
        self.assertEqual(0, stats["nodes_changed"])


class AliasExceptionsTest(unittest.TestCase):
    """人工例外表：登记的点名别名必须活过每一次重建。

    它消灭的失败：重建只认"主名 + 该点绑定材料的标题"，批11 撤回点名的检索需求
    （原名）在每次窗口重建时都被整表重写冲掉——手插 alias_map 行活不过下一次重建。
    """

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.tables_dir = Path(self._tmp.name)
        patcher = mock.patch.object(ra, "TABLES", self.tables_dir)
        patcher.start()
        self.addCleanup(patcher.stop)

    def _pack(self):
        return pack_with([
            ("某册·某章·甲", point("甲概念", "甲概念")),
            ("某册·某章·乙", point("乙概念", "乙概念")),
        ])

    def test_exception_alias_is_merged_when_no_bound_title_matches(self):
        write_exceptions(self.tables_dir, [
            ("MATH", "甲概念", "手插点名", "批11 撤回点名检索需求 2026-10-17"),
        ])
        plan = ra.rebuild(self._pack(), {("MATH", "甲概念"): {"绑定标题别名"}})
        self.assertEqual({"绑定标题别名", "手插点名"},
                         plan["candidates"][("MATH", "甲概念")])
        self.assertEqual(1, plan["exceptions_applied"])

    def test_exception_alias_survives_apply(self):
        write_exceptions(self.tables_dir, [
            ("MATH", "甲概念", "手插点名", "批11 撤回点名检索需求 2026-10-17"),
        ])
        pack = self._pack()
        plan = ra.rebuild(pack, {("MATH", "甲概念"): set()})
        ra.apply_to_pack(pack, plan)
        node = pack["subjects"][0]["topics"][1]["knowledgePoints"][0]
        self.assertEqual(["甲概念", "手插点名"], node["aliases"])

    def test_exception_wins_over_derived_claim_and_counts_conflict(self):
        write_exceptions(self.tables_dir, [
            ("MATH", "甲概念", "共用别名", "登记者优先"),
        ])
        plan = ra.rebuild(self._pack(), {
            ("MATH", "甲概念"): {"共用别名"},
            ("MATH", "乙概念"): {"共用别名"},
        })
        self.assertIn("共用别名", plan["candidates"][("MATH", "甲概念")])
        self.assertNotIn("共用别名", plan["candidates"][("MATH", "乙概念")])
        self.assertEqual(1, plan["exception_conflicts"])
        self.assertTrue(any("例外" in d["reason"] for d in plan["dropped"]),
                        plan["dropped"])

    def test_exception_with_bad_shape_is_rejected(self):
        write_exceptions(self.tables_dir, [
            ("MATH", "甲概念", "已知函数 f(x) 在区间上单调递增", "坏形状"),
        ])
        plan = ra.rebuild(self._pack(), {})
        self.assertEqual(set(), plan["candidates"][("MATH", "甲概念")])
        self.assertEqual(1, len(plan["exceptions_rejected"]))
        self.assertTrue(any("形状" in r["reason"] for r in plan["exceptions_rejected"]),
                        plan["exceptions_rejected"])

    def test_exception_colliding_with_other_node_name_is_rejected(self):
        write_exceptions(self.tables_dir, [
            ("MATH", "甲概念", "乙概念", "撞同学科主名"),
        ])
        plan = ra.rebuild(self._pack(), {})
        self.assertEqual(set(), plan["candidates"][("MATH", "甲概念")])
        self.assertTrue(any("主名" in r["reason"] for r in plan["exceptions_rejected"]),
                        plan["exceptions_rejected"])

    def test_same_alias_registered_by_two_nodes_is_rejected(self):
        write_exceptions(self.tables_dir, [
            ("MATH", "甲概念", "共用别名", "甲登记"),
            ("MATH", "乙概念", "共用别名", "乙登记"),
        ])
        plan = ra.rebuild(self._pack(), {})
        self.assertEqual(set(), plan["candidates"][("MATH", "甲概念")])
        self.assertEqual(set(), plan["candidates"][("MATH", "乙概念")])
        self.assertEqual(2, len(plan["exceptions_rejected"]))
        self.assertTrue(all("同时登记" in r["reason"] for r in plan["exceptions_rejected"]),
                        plan["exceptions_rejected"])

    def test_entry_falls_back_when_exception_row_is_removed(self):
        path = write_exceptions(self.tables_dir, [
            ("MATH", "甲概念", "手插点名", "批11 撤回点名检索需求 2026-10-17"),
        ])
        titles = {("MATH", "甲概念"): {"绑定标题别名"}}
        first = ra.rebuild(self._pack(), titles)
        self.assertIn("手插点名", first["candidates"][("MATH", "甲概念")])
        path.unlink()
        second = ra.rebuild(self._pack(), titles)
        self.assertNotIn("手插点名", second["candidates"][("MATH", "甲概念")])
        self.assertEqual({"绑定标题别名"}, second["candidates"][("MATH", "甲概念")])

    def test_rebuild_with_exceptions_is_idempotent(self):
        write_exceptions(self.tables_dir, [
            ("MATH", "甲概念", "手插点名", "批11 撤回点名检索需求 2026-10-17"),
        ])
        titles = {("MATH", "甲概念"): {"绑定标题别名"}}
        pack = self._pack()
        first = ra.rebuild(pack, titles)
        self.assertIn("手插点名", first["candidates"][("MATH", "甲概念")])
        ra.apply_to_pack(pack, first)
        stats = ra.apply_to_pack(pack, ra.rebuild(pack, titles))
        self.assertEqual(0, stats["nodes_changed"])


if __name__ == "__main__":
    unittest.main()
