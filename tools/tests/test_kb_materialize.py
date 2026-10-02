# -*- coding: utf-8 -*-
"""materialize：判定表 → 材料入库的计划校验门。

plan() 校验每一行前先读三个**阶段产物**：块池（`tables/extracted_chunks.rekeyed.jsonl`）、
包 JSON（staging 或成品目录）与提取状态表。块池未纳入版本控制，CI checkout 里必然缺席；
缺席时 `load_chunks()` 返回空表，plan() 会把每一行都判成"块不存在"——断言随即变成误报
（实测：`_row()` 的合法行在空池下报 `块不存在`）。所以本文件：产物缺席 → 显式 skip 并写明
理由（跳过的是"无判别力"的用例，不是放宽断言）；产物在场 → 照常断言，一条不减。

type 白名单回归（2026-10-02 收紧）：协议 v1.1 只许 3 值，且必须与
`merge_text_judgments.TYPES` 逐字一致；4 种旧值（WORKED_EXAMPLE / COMPLETE_SOLUTION /
DERIVATION / REPRESENTATION_GUIDE）在 MATERIAL 行一律硬拒，在 SKIP 行不生效——存量
判定表 86,414 行 dry-run 0 错误靠的正是"旧 type 只剩 SKIP 行"。
"""

from __future__ import annotations

import unittest

from kb_build import pack_io
from kb_coverage import extraction_state as es
from kb_coverage import materialize as mat
from kb_coverage import merge_text_judgments as mtj
from kb_coverage.pool_path import POOL_PATH


def _missing_inputs() -> list[str]:
    """plan() 依赖的阶段产物里不在场的（只查路径，不读内容、无副作用）。"""
    missing = []
    if not POOL_PATH.exists():
        missing.append(str(POOL_PATH))
    # 包：staging 缺席时 work_dir() 会从成品目录复制基线，两边都没有才算缺席。
    if not ((pack_io.STAGING_DIR / pack_io.PACK_NAME).exists()
            or (pack_io.release_dir() / pack_io.PACK_NAME).exists()):
        missing.append(pack_io.PACK_NAME)
    if not es.TABLE.exists():
        missing.append(str(es.TABLE))
    return missing


_ARTIFACTS_MISSING = _missing_inputs()
_ARTIFACT_SKIP = (
    "阶段产物不在场，plan() 断言无判别力（会把它误报成失败），显式跳过："
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
    def test_valid_row_plans_clean(self):
        pl = mat.plan([_row()])
        self.assertEqual([], pl["errors"])
        self.assertEqual(1, pl["material_count"])

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
