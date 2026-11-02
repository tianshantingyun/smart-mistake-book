# -*- coding: utf-8 -*-
"""isolation OFF/ON 配对报告工具的机制测试（批次 41 / WS4 item 26/28/31）。

只验证解析与判定规则，不跑设备：
- `-r` 原始流必须**顺序扫描**（失败块里 `stack=` 在 `test=` 之前），不能回填；
- 红集是 15 方法 × {PASS,FAIL,ERROR,SKIP,UNKNOWN} 的**向量**，不是集合；
- 只有两臂窗口都 PASS、两臂各自 3 轮稳定，配对才成立；
- 配对成立但 OFF 臂未做**生产侧** T4 移除时，不得输出 T4 因果；
- 判定词里不得把 SKIP / UNGATED 算成 PASS。

运行（仓库根）：
    python -m unittest discover -s tools/tests -t tools -k isolation_pair_report
"""
from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "ci"))

import isolation_pair_report as R  # noqa: E402

CLASS = "com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest"
METHODS = ["mAlpha", "mBeta", "mGamma"]


def _status(method: str, code: int, stream: str = ".") -> str:
    return (
        f"INSTRUMENTATION_STATUS: class={CLASS}\n"
        "INSTRUMENTATION_STATUS: current=1\n"
        "INSTRUMENTATION_STATUS: id=AndroidJUnitRunner\n"
        f"INSTRUMENTATION_STATUS: numtests={len(METHODS)}\n"
        f"INSTRUMENTATION_STATUS: stream={stream}\n"
        f"INSTRUMENTATION_STATUS: test={method}\n"
        f"INSTRUMENTATION_STATUS_CODE: {code}\n"
    )


def _log(codes: dict[str, int], failed_stream: str = "java.lang.AssertionError: boom") -> str:
    """按 runner 的真实块形状生成日志：失败块 stack 在 test= 之前。"""
    chunks = ["INSTRUMENTATION_STATUS: numtests=%d\n" % len(METHODS)]
    for method in METHODS:
        code = codes[method]
        chunks.append(_status(method, 1, stream=""))
        if code == 0:
            chunks.append(_status(method, 0, stream="."))
        elif code == -4:
            chunks.append(_status(method, -4, stream="assumption failed"))
        else:
            chunks.append(
                "INSTRUMENTATION_STATUS: stack=" + failed_stream + "\n"
                + _status(method, code, stream="")
            )
    chunks.append("INSTRUMENTATION_RESULT: stream=\n\nTime: 61.0\nOK (%d tests)\n" % len(METHODS))
    return "".join(chunks)


def _run(arm: str, round_no: int, codes: dict[str, int], window: str = "PASS",
         logcat: str | None = None) -> R.RunInput:
    return R.RunInput(arm=arm, round_no=round_no, window=window,
                      instrument=_log(codes), logcat=logcat or "")


ALL_GREEN = {method: 0 for method in METHODS}
BETA_RED = {**ALL_GREEN, "mBeta": -2}


class ParserTest(unittest.TestCase):

    def test_sequential_scan_attributes_failure_to_the_test_after_the_stack(self) -> None:
        vectors = R.parse_instrument_log(_log(BETA_RED))
        self.assertEqual("PASS", vectors["mAlpha"])
        self.assertEqual("FAIL", vectors["mBeta"])
        self.assertEqual("PASS", vectors["mGamma"])

    def test_skip_is_not_pass(self) -> None:
        vectors = R.parse_instrument_log(_log({**ALL_GREEN, "mGamma": -4}))
        self.assertEqual("SKIP", vectors["mGamma"])

    def test_error_code_maps_to_error(self) -> None:
        vectors = R.parse_instrument_log(_log({**ALL_GREEN, "mAlpha": -1}))
        self.assertEqual("ERROR", vectors["mAlpha"])


class PairingTest(unittest.TestCase):

    def test_three_stable_identical_rounds_per_arm_are_paired(self) -> None:
        runs = []
        for round_no in (1, 2, 3):
            runs.append(_run("OFF", round_no, BETA_RED))
            runs.append(_run("ON", round_no, BETA_RED))
        report = R.build_report(runs, off_arm_production_t4="test-side-only")
        self.assertEqual("PAIRED_RED_SET_IDENTICAL", report["verdict"])
        self.assertEqual(3, report["rounds_per_arm"])
        self.assertTrue(report["paired"])

    def test_invalid_window_means_unconverged(self) -> None:
        runs = []
        for round_no in (1, 2, 3):
            runs.append(_run("OFF", round_no, BETA_RED, window="INVALID"))
            runs.append(_run("ON", round_no, BETA_RED))
        report = R.build_report(runs, off_arm_production_t4="test-side-only")
        self.assertEqual("UNCONVERGED", report["verdict"])
        self.assertIn("window_not_pass", report["reasons"])

    def test_unstable_arm_means_unconverged(self) -> None:
        runs = []
        for round_no in (1, 2, 3):
            codes = BETA_RED if round_no != 2 else ALL_GREEN
            runs.append(_run("OFF", round_no, codes))
            runs.append(_run("ON", round_no, BETA_RED))
        report = R.build_report(runs, off_arm_production_t4="test-side-only")
        self.assertEqual("UNCONVERGED", report["verdict"])
        self.assertIn("arm_unstable", report["reasons"])

    def test_divergence_without_production_t4_removal_is_not_causal(self) -> None:
        runs = []
        for round_no in (1, 2, 3):
            runs.append(_run("OFF", round_no, ALL_GREEN))
            runs.append(_run("ON", round_no, BETA_RED))
        report = R.build_report(runs, off_arm_production_t4="test-side-only")
        self.assertEqual("PAIRED_RED_SET_DIVERGES_T4_UNVERIFIED", report["verdict"])
        self.assertIn("off_arm_not_t4_removed", report["reasons"])

    def test_divergence_with_verified_removal_and_four_rounds_is_causal(self) -> None:
        runs = []
        for round_no in (1, 2, 3, 4):
            runs.append(_run("OFF", round_no, ALL_GREEN))
            runs.append(_run("ON", round_no, BETA_RED))
        report = R.build_report(runs, off_arm_production_t4="removed")
        self.assertEqual("T4_CAUSAL", report["verdict"])
        # R5 §5.3：单方法每臂 k=4 完美分离的精确 Fisher p = 2/C(8,4) = 0.0286；
        # McNemar 在 k=4 时只有 0.125，不足以支撑因果——两条统计量分别记录。
        self.assertAlmostEqual(0.0286, report["fisher"][0]["fisher_p"], places=4)
        self.assertEqual(0.125, report["mcnemar_p"])

    def test_skipped_arm_is_not_a_paired_arm(self) -> None:
        skipped = {method: -4 for method in METHODS}
        runs = []
        for round_no in (1, 2, 3):
            runs.append(_run("OFF", round_no, skipped))
            runs.append(_run("ON", round_no, ALL_GREEN))
        report = R.build_report(runs, off_arm_production_t4="test-side-only")
        self.assertEqual("UNCONVERGED", report["verdict"])
        self.assertIn("skipped_or_unknown", report["reasons"])

    def test_gate_ungated_line_overrides_declared_window(self) -> None:
        logcat = "I/SMB_LOWLOAD_GATE: SMB_LOWLOAD_GATE: verdict=UNGATED reason=busy_p95"
        report = R.build_report(
            [_run("OFF", 1, BETA_RED, logcat=logcat)] * 1
            + [_run("ON", 1, BETA_RED)],
            off_arm_production_t4="test-side-only",
        )
        self.assertEqual("UNCONVERGED", report["verdict"])
        self.assertIn("window_not_pass", report["reasons"])


class KillAttributionTest(unittest.TestCase):

    def _logcat(self) -> str:
        return "\n".join([
            "I TestRunner: started: mAlpha(%s)" % CLASS,
            "W VisualJsService: hard deadline (1500 ms) for request x; killing the worker",
            "W VisualJsService: killing :visual_js worker: watchdog hard deadline",
            "I TestRunner: started: mBeta(%s)" % CLASS,
            "W VisualJsService: killing :visual_js worker: "
            "terminate(generation=123) accepted",
            "I TestRunner: started: mGamma(%s)" % CLASS,
        ])

    def test_kills_are_attributed_to_the_last_started_test(self) -> None:
        attribution = R.parse_kill_attribution(self._logcat())
        self.assertEqual(1, attribution["watchdog"]["mAlpha"])
        self.assertEqual(0, attribution["watchdog"]["mBeta"])
        self.assertEqual(1, attribution["terminate"]["mBeta"])

    def test_real_logcat_shape_with_pid_is_attributed(self) -> None:
        # 设备实测形状：`I/TestRunner(19648): started: method(class)`。
        logcat = "\n".join([
            "11-02 11:58:44.554 I/TestRunner(19648): started: mAlpha(%s)" % CLASS,
            "11-02 11:58:49.206 W/VisualJsService(19704): killing :visual_js worker: watchdog hard deadline",
            "11-02 11:58:50.059 I/TestRunner(19648): finished: mAlpha(%s)" % CLASS,
        ])
        attribution = R.parse_kill_attribution(logcat)
        self.assertEqual(1, attribution["watchdog"]["mAlpha"])
        self.assertNotIn("_unattributed", attribution["watchdog"])

    def test_watchdog_on_must_not_kill_method_is_reported(self) -> None:
        logcat = self._logcat().replace(
            "started: mAlpha", "started: aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds"
        )
        attribution = R.parse_kill_attribution(logcat)
        flags = R.evaluate_kill_invariants(attribution["watchdog"])
        self.assertTrue(flags["unexpected_watchdog_kills"])
        self.assertIn("aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds",
                      flags["must_not_kill_hits"])

    def test_design_kills_are_reported_as_satisfied(self) -> None:
        watchdog = {
            "aHungWorkerReleasesTheCallerWithinHardPlus500ms": 1,
            "aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight": 1,
            "theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState": 1,
            # 批次 40 的"集合外"2 次：总数 5，只记 WARN 不判红（R5 §5.4）。
            "theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy": 2,
        }
        terminate = {"theClientRebuildsAtMostOnceAfterConfirmedAbandons": 2}
        flags = R.evaluate_kill_invariants(watchdog, terminate)
        self.assertTrue(flags["structurally_ok"], flags)
        self.assertEqual(5, flags["watchdog_total"])
        self.assertFalse(flags["total_below_design_warn"])
        self.assertIn("theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy",
                      flags["outside_design"])

    def test_total_below_design_is_only_a_warn(self) -> None:
        watchdog = {
            "aHungWorkerReleasesTheCallerWithinHardPlus500ms": 1,
            "aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight": 1,
            "theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState": 1,
        }
        terminate = {"theClientRebuildsAtMostOnceAfterConfirmedAbandons": 2}
        flags = R.evaluate_kill_invariants(watchdog, terminate)
        self.assertTrue(flags["structurally_ok"], flags)
        self.assertTrue(flags["total_below_design_warn"])
        self.assertEqual(-2, flags["design_total_deviation"])


if __name__ == "__main__":
    unittest.main()
