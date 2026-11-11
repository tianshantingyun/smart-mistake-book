# -*- coding: utf-8 -*-
"""批次 50 修复环的回归不变量（D-1 框漂移 / acceptance 一次性口径 / κ 前置 / MVA 恒真门）。

消灭的具体失败（逐条对应本批独立复核 finding）：
1. D-1 的 `sample_d1.py --check` 已是 match=false/exit 2，而 REPORT 仍写 match=true；
   抽样框被下游工具原地重写后，"框漂移对门值的影响"只有人工口头断言。
2. AUD-08 的 `acceptance 8 页只复算一次` 被写成全局一次性；同一 8 页其实已在批次 48
   `acceptance-once-01` 消费（2026-11-07 `splitter/README.md:34`）。
3. D-1 主判据 0.9757 的判定一致性 κ 为 NOT RUN，按 R2 §4.5 不得用于门；
   record/verdicts 若不带此前置，读者会把 0.9757 当可用门值。
4. MVA 旧门的 `critical_mismatch_accepted_zero` 读的是 `figure_eval_gate.py:980` 的
   逐记录常量 false，结构上恒真；若不标 citable=false，未来引用会把它当证据。

测试只读仓库文件（`BATCH50_REPAIR_ROOT` 可指向夹具根做红样回放）：
    python -I -m pytest tools/tests/test_batch50_repair_invariants.py -q
"""
from __future__ import annotations

import json
import os
import re
import unittest
from pathlib import Path

REPO = Path(os.environ.get("BATCH50_REPAIR_ROOT", Path(__file__).resolve().parents[2]))
EVID = REPO / "docs" / "research" / "evidence" / "2026-11-09-batch-50"
D1 = EVID / "d1"
REPAIR = EVID / "repair"
RECORD = REPO / "docs" / "research" / "2026-11-09-batch-50-record.md"
VERDICTS = REPO / "docs" / "acceptance" / "verdicts.md"


def _text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class D1FrameDriftTest(unittest.TestCase):
    def test_frame_drift_is_quantified_and_disclosed(self):
        data = json.loads(_text(D1 / "frame-drift.json"))
        self.assertEqual(2, data["replay"]["exit"],
                         "样本键对当前框必须复放失败（旧框已被 D-2 覆盖）；把 exit 改成 0 就是掩盖漂移")
        self.assertFalse(data["replay"]["match"])
        self.assertTrue(data["sampling_frame"]["sha256"].startswith("fce8269c"))
        self.assertTrue(data["current_frame"]["sha256"].startswith("ce7d6823"))
        self.assertEqual(-4, data["delta"]["layer_size"])
        self.assertEqual(0, data["sample_layer_mismatches"],
                         "1,600 条已判定样本的层归属必须与当前框一致；不一致则判定样本不可用")
        self.assertGreater(data["resample_under_current_frame"]["different_keys"], 0,
                           "重抽必须有差异——否则样本其实可复放，本记录不成立")
        self.assertTrue(data["estimate_under_current_frame"]["main_pass"])
        self.assertFalse(data["estimate_under_current_frame"]["guardrail_nofpc_pass"])
        self.assertTrue(data["estimate_under_current_frame"]["guardrail_fpc_pass"],
                        "护栏对 FPC 口径敏感这一事实必须被量化，不能只写一句'不敏感'")
        self.assertTrue(data["conclusion_unchanged"])
        self.assertTrue(all(data["self_check_against_stats"].values()),
                        "漂移复算必须在抽样时点 N 上逐位复现 stats.json")

    def test_report_no_longer_claims_match_true(self):
        report = _text(D1 / "REPORT.md")
        self.assertNotIn('"match": true', report,
                         "REPORT 不得再抄抽样时点的 match:true；当前真实结果是 false/exit 2")
        self.assertIn('"match": false', report)
        self.assertIn("FRAME-DRIFT.md", report)
        self.assertIn("0.9005765917383107", report,
                      "FPC 护栏下界必须与 no-FPC 口径并列，避免 overall_pass 对口径敏感被隐藏")
        self.assertIn("κ", report)
        self.assertIn("暂定值", report)


class KappaCaveatTest(unittest.TestCase):
    def test_record_carries_the_kappa_precondition(self):
        record = _text(RECORD)
        self.assertIn("κ", record)
        self.assertIn("model-labeled 暂定值", record)
        self.assertIn("不得用于门", record,
                      "R2 §4.5 的 κ<0.60 前置必须随 3D 结论一起出现，不能只写 PASS main")

    def test_verdicts_do_not_present_0_9757_as_gate_value(self):
        verdicts = _text(VERDICTS)
        self.assertIn("未过 κ 前置", verdicts)
        self.assertIn("model-labeled 暂定值", verdicts)


class AcceptanceOnceScopeTest(unittest.TestCase):
    def test_tool_scope_text_is_scoped_to_this_batch(self):
        source = _text(REPO / "tools" / "split_golden" / "check_split_gate.py")
        block = re.search(r"SCOPE_REASONS_V2 = \((.*?)\n\)\n", source, re.S)
        self.assertIsNotNone(block, "SCOPE_REASONS_V2 常量必须存在")
        scope = "\n".join(line for line in block.group(1).splitlines()
                          if not line.lstrip().startswith("#"))
        self.assertIn("在本批只运行一次", scope)
        self.assertIn("批次 48", scope)
        self.assertIn("held-out 属性不可恢复", scope)
        # 范围声明本身不得回潮为全局一次性（注释里引用旧文案不算）
        self.assertNotIn("acceptance 8 页只复算一次", scope)

    def test_correction_note_exists_and_gate_is_still_fail(self):
        note = _text(EVID / "split-gate" / "SCOPE-CORRECTION.md")
        self.assertIn("acceptance-once-01", note)
        self.assertIn("held-out", note)
        self.assertIn("第二次观测", note)
        report = json.loads(_text(EVID / "split-gate" / "gate-report-b50.json"))
        self.assertEqual("FAIL", report["gate"]["state"],
                         "口径更正不得把门洗绿")
        self.assertEqual(7, report["metrics"]["countExact"]["successes"])
        self.assertEqual(24, report["metrics"]["countExact"]["denominator"])
        self.assertEqual(1, report["metrics"]["fragment"]["successes"])
        self.assertEqual(78, report["metrics"]["fragment"]["denominator"])

    def test_verdicts_scope_bullet_mentions_batch48(self):
        verdicts = _text(VERDICTS)
        self.assertIn("同一 8 页已在批次 48", verdicts)
        self.assertIn("在本批只运行一次", verdicts)


class MvaLegacyFieldTest(unittest.TestCase):
    def test_legacy_critical_error_field_is_marked_non_citable(self):
        source = _text(REPO / "tools" / "figure_eval" / "figure_eval_gate.py")
        self.assertIn('"critical_error_evidence": {', source)
        self.assertIn('"field": "exercised_paths.critical_mismatch_accepted_zero"', source)
        self.assertIn('"citable": False', source)
        self.assertIn("不可引用为门证据", source)
        readme = _text(EVID / "mva" / "README.md")
        self.assertIn("citable", readme)


class ThresholdsUnchangedTest(unittest.TestCase):
    def test_split_and_mva_and_3e_thresholds_are_untouched(self):
        split = _text(REPO / "tools" / "split_golden" / "check_split_gate.py")
        self.assertRegex(split, r"(?m)^COUNT_GATE = 0\.9$")
        self.assertRegex(split, r"(?m)^FRAGMENT_GATE = 0\.05$")
        mva = _text(REPO / "tools" / "figure_eval" / "figure_eval_gate.py")
        for key, value in (("first_pass_rate_min", "0.90"),
                           ("within_budget_final_rate_min", "0.95"),
                           ("per_group_final_rate_min", "0.90"),
                           ("critical_errors_accepted_max", "0")):
            block = re.search(rf'"{key}": \{{[^}}]*"value": ({re.escape(value)}),', mva, re.S)
            self.assertIsNotNone(block, f"{key} 阈值必须保持 {value}")
        scan = _text(REPO / "core" / "domain" / "src" / "main" / "kotlin" / "com" /
                     "tingyun" / "smartmistakebook" / "core" / "domain" /
                     "KnowledgeReviewFullLibraryScan.kt")
        self.assertIn("FULL_LIBRARY_SCAN_DAILY_CAP: Int = 10", scan)


class BlockedRegistryTest(unittest.TestCase):
    def test_blocked_items_are_registered_not_silently_dropped(self):
        blocked = _text(REPAIR / "BLOCKED.md")
        for token in ("5c", "3E", "JS", "干净树", "Play", "备案号"):
            self.assertIn(token, blocked, f"阻塞登记缺 {token}")


if __name__ == "__main__":
    unittest.main()
