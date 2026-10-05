# -*- coding: utf-8 -*-
"""materialize：判定表 → 材料入库的计划校验门。

plan() 校验每一行前读两个**纳管输入**：包 JSON（staging 缺席时由成品目录复制成基线）与
提取状态表——两者都在版本控制里，干净检出必然在场。第三类输入**块池**
（`tables/extracted_chunks.rekeyed.jsonl`）不纳管，CI checkout 里必然缺席；缺席时
`load_chunks()` 返回空表，plan() 会把每一行都判成"块不存在"，断言随即变成误报。
所以本文件的 PlanTest 用 `mock.patch.object(mat, "load_chunks", …)` 注入合成块池——
判据在 CI 与本地跑的是同一份代码，靠整类 skip 会让 CI 只剩常量比较（复核 F4）。

type 白名单回归（2026-10-02 收紧）：协议 v1.1 只许 3 值，且必须与
`merge_text_judgments.TYPES` 逐字一致；4 种旧值（WORKED_EXAMPLE / COMPLETE_SOLUTION /
DERIVATION / REPRESENTATION_GUIDE）在 MATERIAL 行一律硬拒，在 SKIP 行不生效——存量
判定表 86,414 行 dry-run 0 错误靠的正是"旧 type 只剩 SKIP 行"。
"""

from __future__ import annotations

import unittest
from pathlib import Path
from unittest import mock

from kb_build import pack_io
from kb_coverage import extraction_state as es
from kb_coverage import materialize as mat
from kb_coverage import merge_text_judgments as mtj

# 合成块池：与 `_row()` 同键，subject 取四科之一（plan 按块科目查节点表）。
_SYNTH_POOL = [{
    "rel_path": ("2026年新高考资料/一轮复习/2026年体育单招数学零基础一轮总复习/"
                 "资料/1.1集合的概念（讲义）（学生版）.docx"),
    "chunk_id": "9a51501f22-001",
    "subject": "MATH",
}]


def _missing_inputs() -> list[str]:
    """plan() 里**不可 mock**的两个输入（只查路径，不读内容、无副作用）。"""
    missing = []
    # 包：staging 缺席时 work_dir() 会从成品目录复制基线，两边都没有才算缺席。
    if not ((pack_io.STAGING_DIR / pack_io.PACK_NAME).exists()
            or (pack_io.release_dir() / pack_io.PACK_NAME).exists()):
        missing.append(pack_io.PACK_NAME)
    if not es.TABLE.exists():
        missing.append(str(es.TABLE))
    return missing


_ARTIFACTS_MISSING = _missing_inputs()
_ARTIFACT_SKIP = (
    "包或提取状态表不在场（本仓库内均应纳管，缺席只可能是异常检出）："
    + "、".join(_ARTIFACTS_MISSING)
)


def _row(**over):
    base = {
        "chunk_rel": "2026年新高考资料/一轮复习/2026年体育单招数学零基础一轮总复习/资料/1.1集合的概念（讲义）（学生版）.docx",
        "chunk_id": "9a51501f22-001",
        "action": "MATERIAL",
        "node_slug": "交集与并集",
        "type": "CONCEPT_EXPLANATION",
        "title": "t", "summary": "s", "applicability": "a",
        "content": "c", "boundary": "b", "note": "",
    }
    base.update(over)
    return base


@unittest.skipIf(bool(_ARTIFACTS_MISSING), _ARTIFACT_SKIP)
class PlanTest(unittest.TestCase):
    """plan() 六条判据的行为断言；块池用合成夹具注入，CI 与本地同份代码。"""

    def setUp(self):
        patcher = mock.patch.object(mat, "load_chunks",
                                    return_value=[dict(c) for c in _SYNTH_POOL])
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_valid_row_plans_clean(self):
        pl = mat.plan([_row()])
        self.assertEqual([], pl["errors"])
        self.assertEqual(1, pl["material_count"])

    def test_pool_is_injected_not_read_from_disk(self):
        # 注入生效的证明：把池切成空表，合法行必须立刻报"块不存在"——说明本类的判据
        # 读的是注入的合成池，而不是磁盘上的块池（CI 上那块池缺席）。
        with mock.patch.object(mat, "load_chunks", return_value=[]):
            pl = mat.plan([_row()])
        self.assertTrue(any("块不存在" in e for e in pl["errors"]), pl["errors"])

    def test_unknown_node_is_error(self):
        pl = mat.plan([_row(node_slug="不存在的节点xyz")])
        self.assertTrue(any("节点不存在" in e for e in pl["errors"]), pl["errors"])

    def test_bad_type_is_error(self):
        pl = mat.plan([_row(type="NOT_A_TYPE")])
        self.assertTrue(any("非法 type" in e for e in pl["errors"]), pl["errors"])

    def test_missing_chunk_is_error(self):
        pl = mat.plan([_row(chunk_id="0000000000-999")])
        self.assertTrue(any("块不存在" in e for e in pl["errors"]), pl["errors"])

    def test_empty_title_is_error(self):
        pl = mat.plan([_row(title="   ")])
        self.assertTrue(any("缺 title" in e for e in pl["errors"]), pl["errors"])

    def test_legacy_types_are_errors(self):
        # 收紧（协议 v1.1）后 4 种旧值在 MATERIAL 行必须全部被拒——单侧放行 = 旁路复开。
        legacy = ("WORKED_EXAMPLE", "COMPLETE_SOLUTION", "DERIVATION", "REPRESENTATION_GUIDE")
        pl = mat.plan([_row(type=t) for t in legacy])
        for t in legacy:
            self.assertTrue(any("非法 type" in e and t in e for e in pl["errors"]),
                            (t, pl["errors"]))

    def test_skip_row_with_legacy_type_plans_clean(self):
        # 存量旧 type 全部落在 SKIP 行：收紧不得把"整表重放"从 0 错误变成整批拒绝。
        pl = mat.plan([_row(action="SKIP", type="DERIVATION", note="存量旧值，待重判")])
        self.assertEqual([], pl["errors"])
        self.assertEqual(0, pl["material_count"])
        self.assertEqual(1, pl["skip_count"])


class OutputRefAppendTest(unittest.TestCase):
    """write() 的 output_ref 追加判重：必须按逗号分段后**精确相等**，不得子串判重。

    消灭的失败（实测）：`ext-mat-…-100` 是行内已存在的 `ext-mat-…-1000` 的子串，
    旧实现 `slug not in ref` 把它当"已记录"→ 不再追加 → 材料落了包、账本 output_ref
    缺号（全表 7 个 rel 共 264 条）。本类用例在旧实现下必须失败。

    夹具只切断外部边界（计划/卷状态/源条目/落盘/状态机写入）；被测的 slug 拼装与
    output_ref 追加逻辑逐行真跑，不打桩。
    """

    REL = "夹具/2026数学/材料.docx"

    def _write(self, chunk_ids: list[str], ref: str = ""):
        chunks = {(self.REL, cid): {"subject": "MATH"} for cid in chunk_ids}
        plan = {"errors": [], "material_count": len(chunk_ids), "skip_count": 0,
                "states": {self.REL: {"output_ref": ref}}, "pack": {}, "chunks": chunks}
        path = Path("夹具/sidecar-01.json")
        state = {"paths": [path], "counts": {path: {}}, "sizes": {path: 0},
                 "docs": {path: {"schemaVersion": 2, "packId": "p",
                                 "sources": [], "materials": []}}}
        marked: dict = {}
        with mock.patch.object(mat, "plan", return_value=plan), \
             mock.patch.object(mat, "_sidecar_state", return_value=state), \
             mock.patch.object(mat, "_existing_source_entries", return_value={}), \
             mock.patch.object(mat.pack_io, "dump_json"), \
             mock.patch.object(mat.es, "mark",
                               side_effect=lambda paths, tool: marked.update(paths) or []):
            stats = mat.write([_row(chunk_rel=self.REL, chunk_id=cid) for cid in chunk_ids])
        return marked, state["docs"][path]["materials"], stats

    def test_shorter_slug_is_not_swallowed_by_longer_one(self):
        # 先钉住旧实现漏记的机制（前提）：短 slug 确实是长 slug 的子串。
        long_slug, short_slug = "ext-mat-abc123-1000", "ext-mat-abc123-100"
        self.assertIn(short_slug, long_slug)
        marked, materials, stats = self._write(["abc123-1000", "abc123-100"])
        self.assertEqual(2, stats["materialized"])
        self.assertEqual([long_slug, short_slug], [m["slug"] for m in materials])
        self.assertEqual(f"{long_slug},{short_slug}", marked[self.REL][1])

    def test_trailing_empty_segment_neither_blocks_nor_duplicates(self):
        # output_ref 以逗号结尾（空段）：空串不得被当成已记录的 slug，
        # 短 slug 仍须追加，且复算后只应有两个非空段。
        long_slug, short_slug = "ext-mat-abc123-1000", "ext-mat-abc123-100"
        marked, _materials, _stats = self._write(["abc123-100"], ref=f"{long_slug},")
        segments = [s for s in marked[self.REL][1].split(",") if s]
        self.assertEqual([long_slug, short_slug], segments)

    def test_already_recorded_slug_is_not_appended_twice(self):
        # 精确判重的负向面：slug 已在账本（精确段）→ ref 不动、状态机不写。
        long_slug = "ext-mat-abc123-1000"
        marked, _materials, _stats = self._write(["abc123-1000"], ref=long_slug)
        self.assertEqual({}, marked)


class TypesWhitelistTest(unittest.TestCase):
    """不依赖阶段产物：白名单本身，与"两处写入侧必须同一份"的约束。"""

    def test_whitelist_is_three_values(self):
        self.assertEqual(
            {"CONCEPT_EXPLANATION", "METHOD_MODEL", "MISCONCEPTION_GUIDE"}, mat.TYPES)

    def test_whitelist_identical_to_merge(self):
        # 合并（merge）与落库（materialize）任一单侧放宽，收紧就白做。
        self.assertEqual(mtj.TYPES, mat.TYPES)


if __name__ == "__main__":
    unittest.main()
