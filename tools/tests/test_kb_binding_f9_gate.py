# -*- coding: utf-8 -*-
"""F9 对账门：material_bindings 的报告型计数（节点缺/空/欠账）→ 门。

它消灭的失败：`material_bindings.csv` 在 `docs/status.md` 被展示为 6 张权威表之一，
但它的欠账计数此前**只报数、不进任何红/绿**——节点删/并、材料改绑后不更新台账，
没有任何检查会说话（详见 docs/kb-outstanding-research-2026-10-02.md ⑥ 与 F9）。
2026-10-12 批9 d2 清账（41 节点缺 + 37 空 + 3 欠账 → 0/0/0，逐条处置见
`build/agent-batch9/d2/bindings-fix.csv`）后，三桶计数经
`check_pack_contract.material_binding_gate` 进门：≤ 具名基线（归零后只许递减）。

验收映射：
- 合成夹具：任一桶超基线 → 报问题（含桶名与基线）；三桶 ≤ 基线 → 无问题；
- 基线具名登记且为清账后实测值（0/0/0）；
- 工作目录（晋升候选，与 run_kb_checks / promote 同源）上三桶 ≤ 基线。

口径说明：对账对象是**工作目录**——两段拓扑（staging → promote）下成品包在窗口前
落后于 staging 属预期；干净检出（无 staging）上本钉跳过，由 run_kb_checks 的
成品根运行如实报数。
"""

from __future__ import annotations

import csv
import shutil
import tempfile
import unittest
from pathlib import Path

from kb_build import check_pack_contract as cpc
from kb_build import pack_io

STAGING_PACK = pack_io.STAGING_DIR / pack_io.PACK_NAME


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


class BindingBaselineTest(unittest.TestCase):
    """基线具名登记：清账后归零，只许递减。"""

    def test_baseline_registered_zeroed(self):
        self.assertEqual(
            {"point_missing": 0, "empty": 0, "arrear": 0},
            cpc.MATERIAL_BINDING_BASELINE,
            "F9 基线是批9 d2 清账后的实测值（0/0/0）；要改基线必须同步改本钉与清账台账")

    def test_gate_passes_at_baseline(self):
        counts = {"mb_point_missing": 0, "mb_empty": 0, "mb_arrear": 0}
        self.assertEqual([], cpc.material_binding_gate(counts))

    def test_gate_flags_each_bucket(self):
        for key, label in (("mb_point_missing", "节点缺"),
                           ("mb_empty", "空"),
                           ("mb_arrear", "欠账")):
            counts = {"mb_point_missing": 0, "mb_empty": 0, "mb_arrear": 0}
            counts[key] = 2
            problems = cpc.material_binding_gate(counts)
            self.assertEqual(1, len(problems), problems)
            self.assertIn(label, problems[0])
            self.assertIn("基线 0", problems[0])

    def test_gate_tolerates_missing_keys(self):
        """只有 boundary_map 计数（无 mb_* 键）时不误报。"""
        self.assertEqual([], cpc.material_binding_gate({}))


class BindingGateOnCountsTest(unittest.TestCase):
    """合成夹具：report_only_counts 的三桶直连 material_binding_gate（端到端口径）。"""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-f9-gate-", dir=pack_io.REPO / "build"))
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        _write_csv(self.tmp / "material_bindings.csv",
                   ["material_slug", "point_slug", "subject", "reason"], [
            {"material_slug": "m-ok", "point_slug": "节点甲", "subject": "MATH", "reason": "r"},
            {"material_slug": "m-ok2", "point_slug": "节点乙", "subject": "MATH", "reason": "r"},
        ])

    def test_clean_table_passes_gate(self):
        counts = cpc.report_only_counts(_mini_pack(), self.tmp, bindings={"m-ok": {"节点甲"},
                                                                          "m-ok2": {"节点乙"}})
        self.assertEqual([], cpc.material_binding_gate(counts))

    def test_arrear_and_missing_flagged(self):
        # m-ok 现绑其它节点（欠账 1），m-ok2 正常生效
        counts = cpc.report_only_counts(_mini_pack(), self.tmp, bindings={"m-ok": {"节点乙"},
                                                                          "m-ok2": {"节点乙"}})
        self.assertEqual(1, counts["mb_arrear"])
        self.assertEqual(0, counts["mb_empty"])
        problems = cpc.material_binding_gate(counts)
        self.assertEqual(1, len(problems), problems)
        self.assertIn("欠账 1", problems[0])

        _write_csv(self.tmp / "material_bindings.csv",
                   ["material_slug", "point_slug", "subject", "reason"], [
            {"material_slug": "m-ok", "point_slug": "不在包内", "subject": "MATH", "reason": "r"},
        ])
        counts = cpc.report_only_counts(_mini_pack(), self.tmp, bindings={"m-ok": {"节点甲"}})
        self.assertEqual(1, counts["mb_point_missing"])
        self.assertIn("节点缺 1", cpc.material_binding_gate(counts)[0])


class WorkDirLedgerReconcilesTest(unittest.TestCase):
    """工作目录（晋升候选）上的现状钉：三桶 ≤ 具名基线。"""

    @unittest.skipUnless(STAGING_PACK.exists(),
                         "无 staging（干净检出）：本钉按工作目录（晋升候选）口径；"
                         "成品包在批10 窗口 promote 前落后于 staging 属预期")
    def test_workdir_gate_green(self):
        counts = cpc.report_only_counts(pack_io.load_json(pack_io.pack_path()))
        self.assertEqual([], cpc.material_binding_gate(counts),
                         f"F9 未过（工作目录 {pack_io.work_dir()}）：{counts}")


class EvaluateWiresGateTest(unittest.TestCase):
    """evaluate() 必须把 F9 并进表↔包一致性（run_kb_checks / promote 读同一结果）。"""

    def test_evaluate_has_material_bindings_key(self):
        consistency, _stats = cpc.evaluate()
        self.assertIn("material_bindings", consistency,
                      "evaluate() 未接线 F9 门——run_kb_checks / promote 将看不到 material_bindings 欠账")


if __name__ == "__main__":
    unittest.main()
