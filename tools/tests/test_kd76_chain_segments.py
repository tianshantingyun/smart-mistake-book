# -*- coding: utf-8 -*-
"""29.5 s 链分段脚本的机制测试（批次 41 / WS3 item 22）。

只验证**分段报告层**：

- 四段预算从生产常量读出且和 == 29 500 ms；
- S1/S2 的 p95 用 nearest-rank（n=20 → 第 19 个，不是 max）；
- C3 的 KD-66 witness 不混进 "Σ 实测 max"；缺 C4 时该和为 UNVERIFIED；
- 没有"单例端到端"输入项（`--end-to-end-ms` 被 argparse 拒绝）。

不验证任何设备行为。

运行（仓库根）：
    python -m unittest discover -s tools/tests -t tools -k kd76_chain_segments
"""

from __future__ import annotations

import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "ci"))

import kd76_chain_segments as C  # noqa: E402


def write_round(path: Path, round_no: int, bind: int, warmup: int) -> None:
    path.write_text(
        '<testsuite name="t" tests="1" failures="0">'
        '<testcase classname="t" name="m"><system-out><![CDATA[\n'
        f"KD76_ROUND round={round_no} totalRounds=20 bootCount={round_no} elapsedRealtimeMillis=1000\n"
        f"KD76_META round={round_no} apkSha256={'a' * 64} fingerprint=fp sdk=34 "
        "pageSize=4096 versionName=0.2.0\n"
        f"KD76_SAMPLE round={round_no} status=SAMPLE bind={bind} warmup={warmup} "
        f"readyGap=1 firstByte=2 total={bind + warmup + 3} generation=7 "
        "loadBefore=0.01 loadAfter=0.02\n"
        "]]></system-out></testcase></testsuite>",
        encoding="utf-8",
    )


class Kd76ChainSegmentsTest(unittest.TestCase):

    def _run(self, *args: str) -> tuple[int, str]:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = C.main(list(args))
        return code, out.getvalue()

    def _run_dir(self, temp: str) -> Path:
        run = Path(temp) / "run"
        run.mkdir()
        for round_no in range(1, 21):
            write_round(run / f"round-{round_no:02d}.xml", round_no, 100 + round_no, 200 + round_no)
        return run

    def test_budgets_are_read_from_production_constants(self) -> None:
        budgets = C.load_budgets()
        self.assertEqual(
            {"C1_bind": 15_000, "C2_warmup": 10_000, "C3_host": 2_500, "C4_death": 2_000},
            budgets,
        )
        self.assertEqual(29_500, sum(budgets.values()))

    def test_segment_p95_is_nearest_rank_not_max(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = self._run_dir(temp)
            code, text = self._run(str(run), "--json")
        self.assertEqual(0, code, text)
        report = json.loads(text)
        self.assertEqual(119, report["segments"]["C1_bind"]["p95"])
        self.assertEqual(120, report["segments"]["C1_bind"]["max"])
        self.assertEqual(219, report["segments"]["C2_warmup"]["p95"])
        self.assertEqual(220, report["segments"]["C2_warmup"]["max"])

    def test_c3_witness_is_not_part_of_sum_of_measured_max(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = self._run_dir(temp)
            code, text = self._run(
                str(run), "--json", "--c3-witness-ms", "2000", "--c4-max-ms", "1500"
            )
        self.assertEqual(0, code, text)
        report = json.loads(text)
        # C1 max 120 + C2 max 220 + C4 max 1500；C3 witness 2000 不参与。
        self.assertEqual(1_840, report["sum_of_measured_max_millis"])

    def test_missing_c4_keeps_sum_verifiable_as_unverified(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = self._run_dir(temp)
            code, text = self._run(str(run), "--json")
        self.assertEqual(0, code, text)
        report = json.loads(text)
        self.assertIsNone(report["sum_of_measured_max_millis"])
        self.assertIn("UNVERIFIED", report["sum_of_measured_max_note"])

    def test_single_end_to_end_input_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = self._run_dir(temp)
            with contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as raised:
                    C.main([str(run), "--end-to-end-ms", "29500"])
        self.assertEqual(2, raised.exception.code)

    def test_text_reports_the_envelope_as_supremum(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = self._run_dir(temp)
            code, text = self._run(str(run))
        self.assertEqual(0, code, text)
        self.assertIn("sum of budgets = 29500 ms", text)
        self.assertIn("supremum", text)
        self.assertIn("no single end-to-end", text)


if __name__ == "__main__":
    unittest.main()
