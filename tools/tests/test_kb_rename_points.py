# -*- coding: utf-8 -*-
"""rename_points：只改 name，不动 slug（材料/绑定/前置不受影响）。"""

from __future__ import annotations

import unittest

from kb_build import pack_io, rename_points as rp


def pt(slug: str, name: str) -> dict:
    return {"slug": slug, "name": name, "aliases": [], "kind": "CONCEPT",
            "boundary": "b", "sourceLocator": "l", "prerequisiteSlugs": []}


def pack_with(points: list[dict]) -> dict:
    return {
        "schemaVersion": 2, "packId": "t", "taxonomyVersion": "t", "sourceNamespace": "n",
        "reviewedAtEpochMillis": 1, "sourceUri": "https://e.t",
        "coverage": {"baselineId": "b", "catalogLevel": "PARTIAL", "teachingSupportLevel": "PARTIAL"},
        "subjects": [{"subject": "MATH", "sourceFingerprint": "A" * 64,
                      "topics": [{"slug": "t0", "name": "章", "sourceLocator": "l",
                                  "knowledgePoints": points}]}],
    }


class RenameTest(unittest.TestCase):
    def test_changes_name_keeps_slug(self):
        pack = pack_with([pt("s", "下列说法正确的是")])
        changed = rp.rename(pack, {("MATH", "s"): "集合与元素关系"})
        self.assertEqual(1, changed)
        p = pack["subjects"][0]["topics"][0]["knowledgePoints"][0]
        self.assertEqual("集合与元素关系", p["name"])
        self.assertEqual("s", p["slug"], "slug 必须不变——材料/绑定/前置引用它")

    def test_is_idempotent(self):
        pack = pack_with([pt("s", "旧名")])
        self.assertEqual(1, rp.rename(pack, {("MATH", "s"): "新名"}))
        self.assertEqual(0, rp.rename(pack, {("MATH", "s"): "新名"}))

    def test_only_named_subject(self):
        """表键含 subject，不能把 A 科的重名点改了。"""
        pack = pack_with([pt("s", "旧名")])
        self.assertEqual(0, rp.rename(pack, {("PHYSICS", "s"): "新名"}))


class RealPackTest(unittest.TestCase):
    def test_rename_is_idempotent_on_shipped(self):
        """当前成品已应用过 point_rename.csv；再跑必须 0 改动（幂等）。"""
        pack = pack_io.load_json(pack_io.pack_path())
        renames = rp.load_renames()
        self.assertEqual(0, rp.rename(pack, renames))

    def test_shipped_point_count(self):
        from kb_build import delete_points as dp
        pack = pack_io.load_json(pack_io.pack_path())
        # 2026-09-19：两批视觉转写新增 328 个方法节点、别名取证反查改绑 92 条并合并 9 条同物重复
        # 2026-09-19 坏名分流收口：把 fix_bad_names 的定稿移植进权威表后又合并 9 条
        # （属性条目/碎片并入主节点）→ 2434−9=2425
        # 同日再删 1 条题干残片（含它唯一的题干材料）→ 2424
        # 2026-09-19 文本判定轮：化学/生物讲义与知识清单 9,405 块判定入库，新建 428 个知识点
        # 并合并 1 条同物重复 → 2424 + 428 = 2852
        # 2026-09-19 文本判定第二轮（并行会话）再入库 729 个知识点（sidecar 滚到 v2-10）
        # → 2852 + 729 = 3572。
        # 2026-10-01 残渣合并 2 条：point_merge.csv 新增两行把残渣节点
        # `溶质为碱的溶液-h-全部来自水的电离`、`六种表示物质变化的方程式` 并入专指节点
        # → 3572 − 2 = 3570。点数随入库轮次增长，由入库侧在提交里维护本 pin。
        # 2026-10-02 建点闭环：块池判定提案建 304 点、撤回 8 点（块过薄写不出材料）
        # → 3570 + 304 − 8 = 3866。
        # 2026-10-03 批次 3 结构清理（staging 实读）：本轮 point_merge.csv 新增 103 行节点合并
        # ＋改绑执行轮并入「模型特点」1 对、删空壳节点 1 个（`菠菜常用作补铁食品之一-…`，
        # 三条材料按 round2 裁定改绑后归零）→ 3866 − 103 − 1 − 1 = 3761。按新实测值就地更新。
        # 2026-10-11 薄料收口轮（staging 实读）：point_merge.csv 新增 20 对近重复薄点合并
        # （双遍复核 AGREE）→ 3761 − 20 = 3741。按新实测值就地更新。
        # 2026-10-14 批次9 超配 C2 + 批次10 零材料点收口（staging 实读）：point_merge.csv 批次9 超配
        # 382 行合并（2026-10-12）+ 批次10 零材料点 1 行（2026-10-13）+ 同批 point_delete.csv 3 条
        # 残片删点 → 3741 − 382 − 1 − 3 = 3355。按新实测值就地更新。
        # （分解口径：HEAD 成品 3741 − staging 现点 3355 = 386 = merged 383 + deleted 3 + missing 0。）
        # 2026-10-14 批次10 promote v14 落包后重钉（成品实测）：staging 口径 3355 + 批次10 窗口内容
        # 新建 4 个知识点（[CHEMISTRY] 研究物质结构的价值、[MATH] 获取数据的基本途径、
        # [PHYSICS] 家庭电路与安全用电、[PHYSICS] 材料科学及其应用）→ 成品实点 3741 − 386 + 4 = 3359
        # （量源 = 成品包与 HEAD 包逐点集合差：退场 386、新增 4；round-1 失败信息 `3355 != 3359`）。
        self.assertEqual(3359, dp._point_count(pack))


if __name__ == "__main__":
    unittest.main()
