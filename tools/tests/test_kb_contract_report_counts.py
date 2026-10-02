# -*- coding: utf-8 -*-
"""报告型计数（boundary_map / material_bindings / 三值外 type）的语义与基线钉。

它消灭的失败：两张表在 `docs/status.md` 被展示为"6 张权威表"，其中 boundary_map（20 行
vs 包内 3,866 条 boundary）与 material_bindings（598 行 vs 50,383 条材料）**此前不在任何
对账里**——手改 boundary 文本、改绑材料都不会被任何检查发现（详见
docs/kb-outstanding-research-2026-10-02.md ⑥ 与 F9）。现在：现状被量出来（不红不绿），
且"三值外 type 只许递减"有一条硬回归钉（F7 决策前的基线豁免形态）。

验收映射：
- 合成夹具上逐桶语义正确（生效 / stale / missing / 欠账 / 节点缺 / 空）；
- 当前成品包：boundary_map 全生效（stale=missing=0）、各桶求和 == 表行数；
- 三值外 type 总数 ≤ 基线 667（超过即红）。
"""

from __future__ import annotations

import csv
import shutil
import tempfile
import unittest
from pathlib import Path

from kb_build import check_pack_contract as cpc
from kb_build import pack_io


def _write_csv(path: Path, cols: list[str], rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=cols, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def _mini_pack() -> dict:
    return {"packId": "test", "subjects": [
        {"subject": "MATH", "topics": [{
            "slug": "t1", "name": "主题一",
            "knowledgePoints": [
                {"slug": "节点甲", "boundary": "甲的真边界。"},
                {"slug": "节点乙", "boundary": "乙的真边界。"},
            ]}]},
    ]}


class ReportOnlyCountsSemanticsTest(unittest.TestCase):
    """合成夹具上的逐桶语义（不读真包、不读真表）。"""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-report-counts-", dir=pack_io.REPO / "build"))
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        _write_csv(self.tmp / "boundary_map.csv", ["subject", "slug", "boundary"], [
            {"subject": "MATH", "slug": "节点甲", "boundary": "甲的真边界。"},      # effective
            {"subject": "MATH", "slug": "节点乙", "boundary": "已被改过的旧值。"},  # stale
            {"subject": "MATH", "slug": "不存在", "boundary": "x"},                # missing
        ])
        _write_csv(self.tmp / "material_bindings.csv",
                   ["material_slug", "point_slug", "subject", "reason"], [
            {"material_slug": "m-ok", "point_slug": "节点甲", "subject": "MATH", "reason": "r"},
            {"material_slug": "m-ok", "point_slug": "节点乙", "subject": "MATH", "reason": "r"},  # arrear
            {"material_slug": "m-gone", "point_slug": "节点甲", "subject": "MATH", "reason": "r"},  # 材料缺
            {"material_slug": "m-ok", "point_slug": "无节点", "subject": "MATH", "reason": "r"},   # 节点缺
            {"material_slug": "", "point_slug": "节点甲", "subject": "MATH", "reason": "r"},       # 空
        ])

    def test_buckets(self):
        counts = cpc.report_only_counts(_mini_pack(), self.tmp,
                                        bindings={"m-ok": {"节点甲"}})
        self.assertEqual(3, counts["boundary_rows"])
        self.assertEqual(1, counts["boundary_effective"])
        self.assertEqual(1, counts["boundary_stale"])
        self.assertEqual(1, counts["boundary_missing"])
        self.assertEqual(5, counts["mb_rows"])
        self.assertEqual(1, counts["mb_effective"])
        self.assertEqual(1, counts["mb_arrear"])
        self.assertEqual(1, counts["mb_material_missing"])
        self.assertEqual(1, counts["mb_point_missing"])
        self.assertEqual(1, counts["mb_empty"])
        self.assertEqual(counts["mb_rows"],
                         counts["mb_effective"] + counts["mb_arrear"]
                         + counts["mb_material_missing"] + counts["mb_point_missing"]
                         + counts["mb_empty"])


class CurrentPackReportOnlyTest(unittest.TestCase):
    """当前成品包上的现状钉（边界全生效 + 桶求和 + type 基线只许递减）。"""

    @classmethod
    def setUpClass(cls):
        cls.pack = pack_io.load_json(pack_io.release_dir() / pack_io.PACK_NAME)

    def test_boundary_map_fully_effective(self):
        counts = cpc.report_only_counts(self.pack)
        self.assertGreater(counts["boundary_rows"], 0)
        self.assertEqual(0, counts["boundary_stale"], "boundary_map 有行与包内节点不一致")
        self.assertEqual(0, counts["boundary_missing"], "boundary_map 有行指向包内不存在的节点")
        self.assertEqual(counts["boundary_rows"], counts["boundary_effective"])

    def test_material_bindings_buckets_sum(self):
        counts = cpc.report_only_counts(self.pack)
        self.assertEqual(counts["mb_rows"],
                         counts["mb_effective"] + counts["mb_arrear"]
                         + counts["mb_material_missing"] + counts["mb_point_missing"]
                         + counts["mb_empty"])

    def test_legacy_type_count_only_decreases(self):
        legacy = cpc.legacy_type_counts()
        self.assertLessEqual(
            legacy["legacy_total"], cpc.LEGACY_TYPE_BASELINE,
            "三值外材料 type 超过基线——F7 决策前只许递减（新写入必须走三值白名单）")


if __name__ == "__main__":
    unittest.main()
