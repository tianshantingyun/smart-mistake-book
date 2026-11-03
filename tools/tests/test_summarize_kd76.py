# -*- coding: utf-8 -*-
"""KD-76 汇总脚本的机制测试（批次 41 / WS3 item 21）。

只验证**汇总层本身**：

- 20 轮一致样本 → `G2: PASS`（exit 0）；
- 任一 FAIL/INVALID、XML `<failure>`、缺轮、重复轮 → `NOT EVALUABLE`（exit 2），
  且文本里**不得出现 PASS 判词**；
- 跨轮 APK sha / 设备指纹不一致 → `NOT EVALUABLE`（设备绿必须绑定同一快照/APK）；
- 解析前拒绝 DOCTYPE/ENTITY；
- nearest-rank 口径固定（0..9：p50=4、p95=9；n=20 时 p95=第 19 个）。

不验证任何设备行为；设备样本由 `tools/ci/run_kd76_rounds.sh` 产出。

运行（仓库根）：
    python -m unittest discover -s tools/tests -t tools -k summarize_kd76
"""

from __future__ import annotations

import contextlib
import io
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "ci"))

import summarize_kd76 as S  # noqa: E402

SHA_A = "a" * 64
SHA_B = "b" * 64


def boot_id(round_no: int) -> str:
    """每轮宿主 boot_id 的固定夹具值（20 轮互不相同）。"""
    return f"0000000{round_no // 10}-0000-4000-8000-{round_no:012d}"


def round_xml(
    round_no: int,
    *,
    status: str = "SAMPLE",
    bind: int = 100,
    warmup: int = 200,
    sha: str = SHA_A,
    fingerprint: str = "google/sdk_gphone64_x86_64:14/UE1A/123:userdebug/test-keys",
    boot: int | None = None,
    omit_boot: bool = False,
    failure: bool = False,
    host_boot_id: str | None = None,
    host_boot_id_after: str | None = None,
    omit_host_boot_id: bool = False,
    mode: str = "reboot",
    host_boot: int | None = None,
    pid_worker: str = "",
) -> str:
    if status == "SAMPLE":
        sample = (
            f"KD76_SAMPLE round={round_no} status=SAMPLE bind={bind} warmup={warmup} "
            f"readyGap=1 firstByte=2 total={bind + warmup + 3} generation=7 "
            "loadBefore=0.01 loadAfter=0.02"
        )
    else:
        sample = (
            f"KD76_SAMPLE round={round_no} status={status} "
            "censoredLowerBoundMillis=15000 reason=connect failed"
        )
    boot_value = None if omit_boot else (round_no if boot is None else boot)
    boot_field = "" if boot_value is None else f" bootCount={boot_value}"

    boot_id_before = boot_id(round_no) if host_boot_id is None else host_boot_id
    boot_id_after = (
        boot_id_before if host_boot_id_after is None else host_boot_id_after
    )
    host_boot_value = (
        (boot_value if boot_value is not None else round_no)
        if host_boot is None
        else host_boot
    )
    env_line = ""
    if not omit_host_boot_id:
        env_line = (
            f"KD76_ENV round={round_no} mode={mode} "
            f"bootIdBefore={boot_id_before} bootIdAfter={boot_id_after} "
            f"hostBootCount={host_boot_value} pidTarget=4242 pidWorker={pid_worker}\n"
        )
    return (
        f'<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<testsuite name="t" tests="1" failures="{1 if failure else 0}" errors="0">'
        f'<testcase classname="t" name="m"><system-out><![CDATA[\n'
        f"{env_line}"
        f"KD76_ROUND round={round_no} totalRounds=20{boot_field} "
        f"elapsedRealtimeMillis=1000\n"
        f"KD76_META round={round_no} apkSha256={sha} fingerprint={fingerprint} "
        f"sdk=34 pageSize=4096 versionName=0.2.0\n"
        f"{sample}\n]]></system-out>"
        + ('<failure message="x">x</failure>' if failure else "")
        + "</testcase></testsuite>"
    )


class SummarizeKd76Test(unittest.TestCase):

    def _write_run(self, directory: Path, overrides: dict[int, dict] | None = None) -> None:
        overrides = overrides or {}
        directory.mkdir(parents=True, exist_ok=True)
        for round_no in range(1, 21):
            values = overrides.get(round_no, {})
            (directory / f"round-{round_no:02d}.xml").write_text(
                round_xml(round_no, **values), encoding="utf-8"
            )

    def _run(self, target: Path) -> tuple[int, str]:
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()):
            code = S.main([str(target)])
        return code, out.getvalue()

    def test_twenty_consistent_samples_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {i: {"bind": 100 + i, "warmup": 200 + i} for i in range(1, 21)})
            code, text = self._run(run)
        self.assertEqual(0, code, text)
        self.assertIn("G2: PASS", text)
        self.assertIn("n=20/20", text)

    def test_failed_round_is_not_evaluable_and_never_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {20: {"status": "FAIL", "failure": True}})
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("NOT EVALUABLE", text)
        self.assertIn("n=19/20", text)
        self.assertNotIn("PASS", text)

    def test_missing_round_is_not_evaluable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run)
            (run / "round-07.xml").unlink()
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("missing rounds: [7]", text)
        self.assertNotIn("PASS", text)

    def test_duplicate_round_is_not_evaluable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run)
            (run / "round-07-copy.xml").write_text(round_xml(7), encoding="utf-8")
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("duplicate round evidence", text)
        self.assertNotIn("PASS", text)

    def test_mixed_apk_sha_is_not_evaluable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {5: {"sha": SHA_B}})
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("mixed APK sha256", text)
        self.assertNotIn("PASS", text)

    def test_p95_above_gate_is_fail_not_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {i: {"bind": 3000, "warmup": 3000} for i in range(1, 21)})
            code, text = self._run(run)
        self.assertEqual(1, code, text)
        self.assertIn("G2: FAIL", text)
        self.assertNotIn("G2: PASS", text)

    def test_one_round_without_apk_sha_is_invalid_and_counted(self) -> None:
        """F10 假绿：20 轮里 1 轮没记 APK sha，旧逻辑把它从 sha 集合剔除后仍 PASS。"""
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {20: {"sha": ""}})
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("NOT EVALUABLE", text)
        self.assertIn("n=19/20", text)
        self.assertIn("missing per-round apkSha256", text)
        self.assertNotIn("PASS", text)

    def test_repeated_boot_count_is_not_evaluable(self) -> None:
        """20 轮必须可证各自来自一次新 boot；同 boot 多数轮不能算 20 个独立样本。"""
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {20: {"boot": 19}})
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("repeated bootCount", text)
        self.assertNotIn("PASS", text)

    def test_missing_boot_count_is_not_evaluable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {3: {"omit_boot": True}})
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("rounds without bootCount: [3]", text)
        self.assertNotIn("PASS", text)

    def test_missing_host_boot_id_is_invalid_and_counted(self) -> None:
        """F10 后继：只有 app bootCount 不够，宿主 boot_id 缺失的轮不得被其余轮代表。"""
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {6: {"omit_host_boot_id": True}})
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("NOT EVALUABLE", text)
        self.assertIn("n=19/20", text)
        self.assertIn("without host boot_id", text)
        self.assertNotIn("PASS", text)

    def test_run_without_any_host_boot_id_is_not_evaluable(self) -> None:
        """批次 41 之前产出的 XML 没有 KD76_ENV；旧证据不得被当作 20 轮主口径。"""
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {i: {"omit_host_boot_id": True} for i in range(1, 21)})
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("n=0/20", text)
        self.assertIn("without host boot_id", text)
        self.assertNotIn("PASS", text)

    def test_repeated_host_boot_id_is_not_evaluable(self) -> None:
        """同一 boot 内跑两次（force-stop 形状）必须出局，即使 app bootCount 被伪造。"""
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(
                run, {20: {"host_boot_id": boot_id(1), "host_boot_id_after": boot_id(1)}}
            )
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("repeated host boot_id", text)
        self.assertNotIn("PASS", text)

    def test_boot_id_changed_mid_round_is_invalid(self) -> None:
        """轮中重启会重置 uptimeMillis；该轮不是合法冷启动样本。"""
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {9: {"host_boot_id_after": boot_id(10)}})
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("n=19/20", text)
        self.assertIn("host boot_id changed during round 9", text)
        self.assertNotIn("PASS", text)

    def test_force_stop_mode_is_not_main_criterion(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {i: {"mode": "force-stop"} for i in range(1, 21)})
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("mode=force-stop", text)
        self.assertIn("not reboot", text)
        self.assertNotIn("PASS", text)

    def test_host_boot_count_mismatch_is_not_evaluable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {4: {"host_boot": 99}})
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("host bootCount", text)
        self.assertIn("round 4", text)
        self.assertNotIn("PASS", text)

    def test_hot_worker_process_is_invalid(self) -> None:
        """测量前 :visual_js 已存在 → 不是冷启动。"""
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            self._write_run(run, {2: {"pid_worker": "4321"}})
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("n=19/20", text)
        self.assertIn("hot worker", text)
        self.assertNotIn("PASS", text)

    def test_doctype_is_rejected_before_parsing(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            run = Path(temp) / "run"
            run.mkdir()
            (run / "round-01.xml").write_text(
                '<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>'
                "<testsuite/>",
                encoding="utf-8",
            )
            code, text = self._run(run)
        self.assertEqual(2, code)
        self.assertIn("DOCTYPE/ENTITY is not allowed", text)
        self.assertNotIn("PASS", text)

    def test_nearest_rank_estimator_is_pinned(self) -> None:
        # 与 androidTest 的 assertPreRegistration 同一固定向量（R2 §2.3 #5）。
        fixed = list(range(10))
        self.assertEqual(4, S.percentile_nearest_rank(fixed, 50))
        self.assertEqual(9, S.percentile_nearest_rank(fixed, 95))
        # n=20：p95 = 第 ceil(19)-1 = 18 个（容忍一个最大值）。
        twenty = list(range(20))
        self.assertEqual(18, S.percentile_nearest_rank(twenty, 95))
        self.assertEqual(19, S.percentile_nearest_rank(twenty, 100))


if __name__ == "__main__":
    unittest.main()
