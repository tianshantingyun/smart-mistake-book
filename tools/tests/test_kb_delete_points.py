# -*- coding: utf-8 -*-
"""delete_points 的用例。只删"无材料 + 不被引用 + 判为残渣"的点；幂等；清悬挂前置。"""

from __future__ import annotations

import unittest

from kb_build import delete_points as dp


def pt(slug: str, prereq: list[str] | None = None) -> dict:
    return {"slug": slug, "name": slug, "aliases": [], "kind": "CONCEPT",
            "boundary": "b", "sourceLocator": "l", "prerequisiteSlugs": prereq or []}


def pack_with(points_by_topic: list[list[dict]]) -> dict:
    return {
        "schemaVersion": 2, "packId": "t", "taxonomyVersion": "t", "sourceNamespace": "n",
        "reviewedAtEpochMillis": 1, "sourceUri": "https://e.t",
        "coverage": {"baselineId": "b", "catalogLevel": "PARTIAL", "teachingSupportLevel": "PARTIAL"},
        "subjects": [{"subject": "MATH", "sourceFingerprint": "A" * 64,
                      "topics": [{"slug": f"t{i}", "name": f"章{i}", "sourceLocator": "l",
                                  "knowledgePoints": pts} for i, pts in enumerate(points_by_topic)]}],
    }


class DeleteTest(unittest.TestCase):
    def test_removes_only_listed_points(self):
        pack = pack_with([[pt("a"), pt("b"), pt("c")]])
        removed, dangling = dp.delete(pack, {("MATH", "b")})
        self.assertEqual(1, removed)
        self.assertEqual(0, dangling)
        self.assertEqual(["a", "c"], [p["slug"] for p in pack["subjects"][0]["topics"][0]["knowledgePoints"]])

    def test_cleans_dangling_prerequisite(self):
        """表本不该含被引用点，但删除逻辑必须清悬挂引用——这是防损坏的双保险。"""
        pack = pack_with([[pt("a", prereq=["b"]), pt("b")]])
        removed, dangling = dp.delete(pack, {("MATH", "b")})
        self.assertEqual(1, removed)
        self.assertEqual(1, dangling)
        self.assertEqual([], pack["subjects"][0]["topics"][0]["knowledgePoints"][0]["prerequisiteSlugs"])

    def test_is_idempotent(self):
        pack = pack_with([[pt("a"), pt("b")]])
        self.assertEqual(1, dp.delete(pack, {("MATH", "a")})[0])
        # 第二次：a 已不在，删 0
        self.assertEqual(0, dp.delete(pack, {("MATH", "a")})[0])
        self.assertEqual(1, dp._point_count(pack))

    def test_prereq_stays_a_list(self):
        """prerequisiteSlugs 必须是 list（JSON 可序列化）——set 会让 dump 崩溃。"""
        pack = pack_with([[pt("a", prereq=["b"]), pt("b"), pt("c")]])
        dp.delete(pack, {("MATH", "b")})
        self.assertIsInstance(pack["subjects"][0]["topics"][0]["knowledgePoints"][0]["prerequisiteSlugs"], list)


class RealPackTest(unittest.TestCase):
    def test_delete_is_idempotent_on_shipped(self):
        """当前成品已应用过 point_delete.csv；再跑必须删 0（幂等，重放确定）。"""
        from kb_build import pack_io
        pack = pack_io.load_json(pack_io.pack_path())
        deletes = dp.load_deletes()
        self.assertEqual(0, dp.delete(pack, deletes)[0])
        self.assertEqual(0, dp.delete(pack, deletes)[1], "不得清理悬挂前置")

    def test_shipped_point_count(self):
        from kb_build import pack_io
        pack = pack_io.load_json(pack_io.pack_path())
        # 2026-09-19：两批视觉转写新增 328 个方法节点、别名取证反查改绑 92 条并合并 9 条同物重复
        # 2026-09-19 坏名分流收口：把 fix_bad_names 的定稿移植进权威表后又合并 9 条
        # （属性条目/碎片并入主节点）→ 2434−9=2425
        # 同日再删 1 条题干残片（`(1)写出分子式为C5H12的烷烃的结构简式：`，连同它唯一的
        # 题干材料，用户批准）→ 2424
        # 2026-09-19 文本判定轮：化学/生物「一轮复习讲义·学生版 + 知识清单·学生版」9,405 块判定入库，
        # 新建 428 个知识点并合并 1 条同物重复 → 2424 + 428 = 2852
        # 2026-09-19 文本判定第二轮（并行会话）再入库 729 个知识点（sidecar 滚到 v2-10）
        # → 2852 + 729 = 3572。
        # 2026-10-01 残渣合并 2 条：point_merge.csv 新增两行把残渣节点
        # `溶质为碱的溶液-h-全部来自水的电离`、`六种表示物质变化的方程式` 并入专指节点
        # → 3572 − 2 = 3570。点数随入库轮次增长，由入库侧在提交里维护本 pin。
        # 2026-10-02 建点闭环：块池判定提案建 304 点、撤回 8 点（块过薄写不出材料）
        # → 3570 + 304 − 8 = 3866。
        # 2026-10-03 批次 3 结构清理（staging 实读）：本轮 point_merge.csv 新增 103 行同章同名/
        # 近重复节点合并（另一阶段落表），改绑执行轮再并入「模型特点」→「子弹打木块模型」1 对
        # （唯一材料本属三电荷节点、按 round2 裁定改绑后归零），并删除 1 个空壳节点
        # （`菠菜常用作补铁食品之一-…`，3 条材料全为误绑、改绑后归零）→ 3866 − 103 − 1 − 1 = 3761。
        # 2026-10-11 薄料收口轮（staging 实读）：point_merge.csv 新增 20 对近重复薄点合并
        # （双遍复核 AGREE；其中「细胞中能源物质的供能顺序」并入后幸存者变 2 条材料）→ 3761 − 20 = 3741。
        # 2026-10-14 批次9 超配 C2 + 批次10 零材料点收口（staging 实读）：point_merge.csv 批次9 超配
        # 382 行合并（2026-10-12）+ 批次10 零材料点 1 行（2026-10-13，`焰色反应`→`焰色试验`）
        # + 同批 point_delete.csv 3 条残片删点（2 条化学、1 条数学）→ 3741 − 382 − 1 − 3 = 3355。
        # 分解口径：HEAD 成品 3741 点 − staging 现点 3355 = 386 = merged 383 + deleted 3 + missing 0。
        # 2026-10-14 批次10 promote v14 落包后重钉（成品实测）：上列 staging 口径 3355 之外，
        # 批次10 窗口内容另新建 4 个知识点（[CHEMISTRY] 研究物质结构的价值、[MATH] 获取数据的基本途径、
        # [PHYSICS] 家庭电路与安全用电、[PHYSICS] 材料科学及其应用）→ 成品实点 3741 − 386 + 4 = 3359
        # （量源 = 成品包与 HEAD 包逐点集合差：退场 386、新增 4；round-1 失败信息 `3355 != 3359`）。
        # 2026-10-16 批次11 promote v15 再重钉（成品实测）：批次11 超配并点（point_merge.csv 批次11
        # 新增 170 行）+ F2 窗口 1 行（`电磁振动-电磁波和传感器`→`电磁振荡`）共 171 行落地（退场 171，
        # 全部可由 point_merge.csv 的已执行行解释、无无据退场）；同批新建拆点 22 个（new_points.csv
        # 20 + new_points_manual.csv 2）→ 成品实点 3359 − 171 + 22 = 3210（量源 = 成品包与 HEAD 包
        # 逐点集合差：退场 171、新增 22；本用例 round-1 失败信息 `3359 != 3210`；
        # 复算脚本 build/agent-batch11/pin/measure_py_pins.py）。
        # 2026-10-20 批次13 promote v17 再重钉（成品实测）：批次13 新建 116 点（其中 85 个零材料
        # 新点按「空点收口」撤回：84 行落 point_delete.csv「空点撤回…待后续配材后按
        # new_points_manual 重建」、1 点并入近义 survivor 落 point_merge.csv，见各表行内理由）
        # + 删 3 残点 + 窗口② 建 7 点 → 成品实点 3210 + 116 − 85 − 3 + 7 = 3245
        # （量源 = 成品包实跑 _point_count 与本用例 round-1 失败信息 `3210 != 3245`）。
        # 2026-10-31 批次27 v23 重钉（成品实测）：41 行 REOPEN 中 35 条 BUILD 新建 35 个节点
        # （6 条 MERGE 指向既有节点，不新增）→ 3245 + 35 = 3280；同步证据 = new_points_manual
        # 1940 → 1941（抽屉原理）、point_delete 283 → 249（撤销 34 行空点撤回）。
        # 量源 = 成品/staging 实跑 _point_count 与本用例 round-1 失败信息 `3245 != 3280`。
        # 点数随入库/结构清理轮次变化，由执行轮在提交里维护本 pin。
        self.assertEqual(3280, dp._point_count(pack))

    def test_purge_table_refs_is_clean_on_shipped(self):
        """成品已删过点、外部表已清过——再 purge 必须 0（无悬空引用残留）。"""
        from kb_build import pack_io
        pack = pack_io.load_json(pack_io.pack_path())
        purged = dp._purge_table_refs(pack)
        self.assertEqual(0, sum(purged.values()), purged)

    def test_cli_runs_without_crashing(self):
        """**CLI 必须真的能跑通**——这是库函数级用例照不到的一块。

        它消灭的失败（2026-09-19 实测）：`main()` 里"用前后集合求差算出被删 slug"那一段写的是
        `s["subject"]`，而 `iter_points` 产出的第一项是 subject **字符串**，于是 CLI 一跑就
        TypeError；而库函数 `delete()` 的用例全绿、没有任何门会红——因为"加一段无损求差"
        的改动**没有对应的执行验证**。同类形态在别的表驱动工具上也查一遍。
        """
        self.assertEqual(0, dp.main([]), "delete_points 的 CLI 在成品上应当跑通且不改任何东西")


if __name__ == "__main__":
    unittest.main()
