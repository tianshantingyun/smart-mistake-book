# -*- coding: utf-8 -*-
"""KD-76 A6 合成判词的机制测试（批次 43 / WS2 item 12）。

只验证 **A6 composer 本身**（`tools/ci/kd76_a6_verify.py`）：

- 两批同构建、boot 不交、时间有序、协议一致 → `A6: CONFIRMED`（exit 0）；
- 两批 app/test sha 不同（批次 42 的真实形状）→ `NOT EVALUABLE`（exit 2），
  文本**不得**出现 `A6: CONFIRMED`；
- B 批早于 A 批、boot_id 跨批重复、n=19、两批证据逐字节相同、条件漂移
  → `NOT EVALUABLE`；
- 两批都可出 p95 且至少一批 > 5000 ms → `REFUTED`（exit 1），不得相加/平均；
- 固定向量钉住 nearest-rank p95 与 5 000 ms 门（防止 composer 另发明一个门）。

不验证任何设备行为：真实两批样本由 `tools/ci/run_kd76_rounds.sh` 产出。

运行（仓库根）：
    python -m pytest tools/tests/test_kd76_a6_verify.py -q
"""

from __future__ import annotations

import contextlib
import hashlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "ci"))

import kd76_a6_verify as V  # noqa: E402
import summarize_kd76 as S  # noqa: E402

SHA_APP = "a" * 64
SHA_TEST = "b" * 64
GEN_DEVICE = "c" * 64
GEN_INSTALL = "d" * 64
PLAN_TEXT = """a6PlanVersion=1
planId=test-plan
requiredBatches=2
roundsPerBatch=20
estimator=nearest-rank
gateMillis=5000
mode=reboot
settleSeconds=0
minSeparationMinutes=30
maxReplacementBatches=1
expectedAppApkSha256={app}
expectedTestApkSha256={test}
expectedDeviceGenerationId={device}
expectedInstallGenerationId={install}
allowReinstall=false
""".format(app=SHA_APP, test=SHA_TEST, device=GEN_DEVICE, install=GEN_INSTALL)


def _boot_id(batch: str, round_no: int) -> str:
    return f"{batch.lower()}00000{batch.lower()}-0000-4000-8000-{round_no:012d}"


def _utc(minute: int, second: int = 0) -> str:
    hour, rest = divmod(minute, 60)
    return f"2026-11-05T{hour:02d}:{rest:02d}:{second:02d}Z"


def round_xml(
    round_no: int,
    *,
    batch: str,
    bind: int,
    warmup: int,
    boot_id: str,
    boot_count: int,
    started: str,
    finished: str,
    sha: str = SHA_APP,
    run_id: str = "run",
    warmup_finished: int = 1,
    pid_worker_after: str = "",
    la1: str = "0.10",
    nproc: str = "4",
    failure: bool = False,
    include_am_marker: bool = True,
) -> str:
    total = bind + warmup + 3
    env = (
        f"KD76_ENV round={round_no} mode=reboot bootIdBefore={boot_id} bootIdAfter={boot_id} "
        f"hostBootCount={boot_count} pidTarget=4242 pidWorker= pidWorkerAfter={pid_worker_after} "
        f"appsFrozen=0 loadBefore={la1}_0.10_0.10_1/1000_4242 loadAfter={la1}_0.10_0.10_1/1000_4242 "
        f"batchId={batch} runId={run_id} roundStartedUtc={started} roundFinishedUtc={finished} "
        f"screenAwake=true warmupFinishedCount={warmup_finished} warmupFailedCount=0 "
        f"la1={la1} nproc={nproc}"
    )
    am_marker = "--- am instrument output ---\nOK (1 test)\n" if include_am_marker else ""
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<testsuite name="t" tests="1" failures="{1 if failure else 0}" errors="0">'
        '<testcase classname="t" name="m"><system-out><![CDATA[\n'
        f"{env}\n"
        f"KD76_ROUND round={round_no} totalRounds=20 bootCount={boot_count} "
        f"elapsedRealtimeMillis=1000\n"
        f"KD76_META round={round_no} apkSha256={sha} "
        f"fingerprint=google/sdk_gphone64_x86_64:14/UE1A/123:userdebug/test-keys "
        f"sdk=34 pageSize=4096 versionName=0.2.0\n"
        f"KD76_SAMPLE round={round_no} status=SAMPLE bind={bind} warmup={warmup} "
        f"readyGap=1 firstByte=2 total={total} generation=7 "
        f"loadBefore={la1} loadAfter={la1}\n"
        f"{am_marker}"
        "]]></system-out>"
        + ('<failure message="x">x</failure>' if failure else "")
        + "</testcase></testsuite>"
    )


def write_batch(
    directory: Path,
    *,
    batch: str,
    plan_sha: str,
    base_minute: int,
    binds: list[int] | None = None,
    warmups: list[int] | None = None,
    sha: str = SHA_APP,
    test_sha: str = SHA_TEST,
    boot_ids: list[str] | None = None,
    boot_counts: list[int] | None = None,
    run_id: str | None = None,
    failure_round: int | None = None,
    warmup_finished: int = 1,
    la1_values: list[str] | None = None,
    include_am_marker: bool = True,
    extra_manifest: dict[str, str] | None = None,
) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    run_id = run_id or f"run-{batch}"
    binds = binds or [100 + i for i in range(20)]
    warmups = warmups or [200 + i for i in range(20)]
    boot_ids = boot_ids or [_boot_id(batch, i) for i in range(1, 21)]
    if boot_counts is None:
        boot_counts = list(range(1, 21)) if batch == "A" else list(range(21, 41))
    la1_values = la1_values or ["0.10"] * 20

    hash_lines: list[str] = []
    for index in range(20):
        round_no = index + 1
        started = _utc(base_minute + index, 0)
        finished = _utc(base_minute + index, 30)
        xml = round_xml(
            round_no,
            batch=batch,
            bind=binds[index],
            warmup=warmups[index],
            boot_id=boot_ids[index],
            boot_count=boot_counts[index],
            started=started,
            finished=finished,
            sha=sha,
            run_id=run_id,
            warmup_finished=warmup_finished,
            la1=la1_values[index],
            failure=(failure_round == round_no),
            include_am_marker=include_am_marker,
        )
        xml_path = directory / f"round-{round_no:02d}.xml"
        xml_path.write_text(xml, encoding="utf-8")
        am_path = directory / f"round-{round_no:02d}.am.txt"
        am_text = "INSTRUMENTATION_STATUS: numtests=1\nOK (1 test)\n"
        am_path.write_text(am_text, encoding="utf-8")
        hash_lines.append(f"roundFileSha256.round-{round_no:02d}.xml={_sha_bytes(xml_path.read_bytes())}")
        hash_lines.append(f"amFileSha256.round-{round_no:02d}.am.txt={_sha_bytes(am_path.read_bytes())}")

    finished_utc = _utc(base_minute + 20, 0)
    manifest = {
        "ksum": "kd76-run",
        "mode": "reboot",
        "serial": "emulator-5584",
        "targetPackage": "com.tingyun.smartmistakebook.localfirst",
        "component": "com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner",
        "testClass": "com.tingyun.smartmistakebook.visualjs.VisualJsConnectStageCalibrationInstrumentedTest",
        "testMethod": "readRoundFromInstrumentationArg",
        "rounds": "20",
        "firstRound": "1",
        "lastRound": "20",
        "preregisteredN": "20",
        "estimator": "nearest-rank",
        "gateMillis": "5000",
        "settleSeconds": "0",
        "startedUtc": _utc(base_minute, 0),
        "batchId": batch,
        "runId": run_id,
        "a6PlanSha256": plan_sha,
        "installedAppSha256": sha,
        "installedTestSha256": test_sha,
        "deviceGenerationId": GEN_DEVICE,
        "installGenerationId": GEN_INSTALL,
        "firstInstallTime": "2026-11-03 18:00:00",
        "lastUpdateTime": "2026-11-03 19:00:00",
        "deviceNproc": "4",
        "avdName": "kb_test_device",
        "emulatorInstanceId": "qemu:5584:starttime",
        "finishedUtc": finished_utc,
        "failedRounds": "0" if failure_round is None else "1",
        "roundsRequested": "20",
        "replacementCount": "0",
        "bootIds": ",".join(boot_ids),
        "note": "synthetic fixture",
    }
    if extra_manifest:
        manifest.update(extra_manifest)
    lines = [manifest.pop("ksum")]
    lines.extend(f"{key}={value}" for key, value in manifest.items())
    lines.extend(hash_lines)
    manifest_path = directory / "manifest.txt"
    manifest_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    (directory / "manifest.sha256").write_text(
        _sha_bytes(manifest_path.read_bytes()) + "\n", encoding="utf-8"
    )
    return directory


def _sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class A6VerifyTest(unittest.TestCase):

    def _setup(self, temp: str, **batch_overrides) -> tuple[Path, Path, Path]:
        root = Path(temp) / "a6"
        root.mkdir(parents=True, exist_ok=True)
        plan = root / "a6-plan.txt"
        plan.write_text(PLAN_TEXT, encoding="utf-8")
        plan_sha = _sha_bytes(plan.read_bytes())
        batch_a = write_batch(root / "batchA", batch="A", plan_sha=plan_sha, base_minute=0)
        b_kwargs = dict(batch="B", plan_sha=plan_sha, base_minute=300)
        b_kwargs.update(batch_overrides.pop("b", {}))
        batch_b = write_batch(root / "batchB", **b_kwargs)
        return plan, batch_a, batch_b

    def _run(self, plan: Path, batch_a: Path, batch_b: Path) -> tuple[int, dict]:
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()):
            code = V.main(
                [
                    "--plan", str(plan),
                    "--batch-a", str(batch_a),
                    "--batch-b", str(batch_b),
                    "--json",
                ]
            )
        return code, json.loads(out.getvalue())

    def _run_text(self, plan: Path, batch_a: Path, batch_b: Path) -> tuple[int, str]:
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()):
            code = V.main(["--plan", str(plan), "--batch-a", str(batch_a), "--batch-b", str(batch_b)])
        return code, out.getvalue()

    # ---------------------------------------------------------------- 正向

    def test_same_build_two_batches_confirm(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            plan, batch_a, batch_b = self._setup(temp)
            code, text = self._run_text(plan, batch_a, batch_b)
        self.assertEqual(0, code, text)
        self.assertIn("A6: CONFIRMED", text)
        self.assertIn("batchA:", text)
        self.assertIn("batchB:", text)
        self.assertNotIn("n=40", text)
        self.assertNotIn("combined", text.lower())

    def test_confirmed_reports_both_p95_separately(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            plan, batch_a, batch_b = self._setup(
                temp,
                b={"binds": [3000 + i for i in range(20)]},
            )
            code, payload = self._run(plan, batch_a, batch_b)
        self.assertEqual(0, code, payload)
        self.assertEqual("CONFIRMED", payload["verdict"])
        self.assertEqual(20, payload["batchA"]["n"])
        self.assertEqual(20, payload["batchB"]["n"])
        self.assertEqual(300 + 18 * 2, payload["batchA"]["p95_millis"])
        self.assertEqual(3200 + 18 * 2, payload["batchB"]["p95_millis"])
        self.assertNotIn("merged_p95_millis", payload)

    # ---------------------------------------------------------------- 结构性拒绝

    def test_different_app_sha_is_not_evaluable_and_never_confirmed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            plan, batch_a, batch_b = self._setup(temp, b={"sha": "e" * 64})
            code, text = self._run_text(plan, batch_a, batch_b)
        self.assertEqual(2, code)
        self.assertIn("A6: NOT EVALUABLE", text)
        self.assertNotIn("A6: CONFIRMED", text)
        self.assertIn("build_mismatch", text)

    def test_batch_b_before_a_is_not_evaluable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            plan, batch_a, batch_b = self._setup(temp, b={"base_minute": -600})
            code, text = self._run_text(plan, batch_a, batch_b)
        self.assertEqual(2, code)
        self.assertIn("time_overlap", text)

    def test_repeated_boot_id_across_batches_is_not_evaluable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            shared = [_boot_id("A", i) for i in range(1, 21)]
            plan, batch_a, batch_b = self._setup(temp, b={"boot_ids": shared})
            code, text = self._run_text(plan, batch_a, batch_b)
        self.assertEqual(2, code)
        self.assertIn("boot_id_overlap", text)

    def test_single_batch_short_sample_is_not_evaluable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            plan, batch_a, batch_b = self._setup(temp)
            # 只写 19 份 XML：缺轮必须让该批 NOT EVALUABLE
            (batch_b / "round-20.xml").unlink()
            (batch_b / "round-20.am.txt").unlink()
            code, text = self._run_text(plan, batch_a, batch_b)
        self.assertEqual(2, code)
        self.assertIn("A6: NOT EVALUABLE", text)
        self.assertNotIn("A6: CONFIRMED", text)

    def test_byte_identical_evidence_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            plan, batch_a, batch_b = self._setup(temp)
            for index in range(1, 21):
                for suffix in ("xml", "am.txt"):
                    source = batch_a / f"round-{index:02d}.{suffix}"
                    (batch_b / f"round-{index:02d}.{suffix}").write_bytes(source.read_bytes())
            code, text = self._run_text(plan, batch_a, batch_b)
        self.assertEqual(2, code)
        self.assertIn("evidence_reused", text)

    def test_warmup_condition_drift_is_not_evaluable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            plan, batch_a, batch_b = self._setup(
                temp, b={"warmups": [2000 + i for i in range(20)]}
            )
            code, text = self._run_text(plan, batch_a, batch_b)
        self.assertEqual(2, code)
        self.assertIn("condition_drift", text)

    def test_missing_raw_am_output_is_not_evaluable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            plan, batch_a, batch_b = self._setup(temp, b={"include_am_marker": False})
            code, text = self._run_text(plan, batch_a, batch_b)
        self.assertEqual(2, code)
        self.assertIn("raw_output_missing", text)

    # ---------------------------------------------------------------- 判词语义

    def test_failed_round_is_not_optional_stopping(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            plan, batch_a, batch_b = self._setup(temp, b={"failure_round": 17})
            code, text = self._run_text(plan, batch_a, batch_b)
        self.assertEqual(2, code)
        self.assertIn("failed_rounds", text)
        self.assertNotIn("optional_stopping", text)
        self.assertNotIn("A6: CONFIRMED", text)

    def test_both_batches_over_gate_is_refuted(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            plan, batch_a, batch_b = self._setup(
                temp,
                b={"binds": [5000 + i for i in range(20)]},
            )
            code, text = self._run_text(plan, batch_a, batch_b)
        self.assertEqual(1, code, text)
        self.assertIn("A6: REFUTED", text)
        self.assertNotIn("A6: CONFIRMED", text)

    def test_gate_and_percentile_fixed_vectors(self) -> None:
        self.assertEqual(5000, S.GATE_MILLIS)
        self.assertEqual(5000, V.GATE_MILLIS)
        values = list(range(1, 21))
        self.assertEqual(19, S.percentile_nearest_rank(values, 95))
        self.assertEqual(19, V.percentile_nearest_rank(values, 95))
        self.assertEqual(
            S.percentile_nearest_rank(values, 95), V.percentile_nearest_rank(values, 95)
        )

    def test_plan_with_short_separation_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "a6"
            root.mkdir(parents=True, exist_ok=True)
            plan = root / "a6-plan.txt"
            plan.write_text(PLAN_TEXT.replace("minSeparationMinutes=30", "minSeparationMinutes=5"), encoding="utf-8")
            plan_sha = _sha_bytes(plan.read_bytes())
            batch_a = write_batch(root / "batchA", batch="A", plan_sha=plan_sha, base_minute=0)
            batch_b = write_batch(root / "batchB", batch="B", plan_sha=plan_sha, base_minute=300)
            code, text = self._run_text(plan, batch_a, batch_b)
        self.assertEqual(2, code)
        self.assertIn("plan_mismatch", text)


if __name__ == "__main__":
    unittest.main()
