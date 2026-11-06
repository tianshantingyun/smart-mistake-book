# -*- coding: utf-8 -*-
"""isolation witness 检查器的机制测试（批次 43 / WS3 item 19/22/25）。

只验证**分类与判据**，不跑设备：
- `-r` 流里 15 个 `-4` + `IsolationGate#require` 首帧必须判 `ENV_SKIP`，
  不能判 `NOT_SCHEDULED`（那是"零执行"与"环境不满足"的混淆，研究件 §3）；
- 只有流没有 witness 时，S5–S8 一律 `UNDECIDABLE_NO_WITNESS`，不得猜；
- 有 gate 无 body → `FIXTURE_SKIP`；有 body_begin 无 body_end → `BODY_CRASH`；
- 15 例全 `EXECUTED` 且 wall ≥ floor、cpu_delta ≥ 1 时 `ok=True`；
- 820 ms 级"全跳过轮"在 floor 上必然失败；
- canary 失败 / roster 漂移 / 产物截断都是违规，不降级成绿。

运行（仓库根）：
    python -I -m pytest tools/tests/test_isolation_witness_check.py -q
"""
from __future__ import annotations

import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "ci"))

import isolation_witness_check as W  # noqa: E402

CLASS = "com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest"
EXPECTATIONS = Path(REPO / "tools" / "ci" / "isolation_witness_expectations.json")
METHODS_FILE = Path(REPO / "tools" / "ci" / "visual_js_isolation_methods.txt")


def _methods() -> list[str]:
    return [
        line.strip()
        for line in METHODS_FILE.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]


def _status(method: str, code: int, stream: str = ".") -> str:
    return (
        f"INSTRUMENTATION_STATUS: class={CLASS}\n"
        f"INSTRUMENTATION_STATUS: numtests={len(_methods())}\n"
        f"INSTRUMENTATION_STATUS: stream={stream}\n"
        f"INSTRUMENTATION_STATUS: test={method}\n"
        f"INSTRUMENTATION_STATUS_CODE: {code}\n"
    )


def _assumption_block(method: str) -> str:
    """批次 42 r1 的真实块形状：test= → CODE:1 → stack → CODE:-4。"""
    return (
        _status(method, 1, stream="")
        + "INSTRUMENTATION_STATUS: stack=org.junit.AssumptionViolatedException: "
        "SMB_LOWLOAD_GATE UNGATED\n"
        + "\tat org.junit.Assume.assumeTrue(Assume.java:68)\n"
        + "\tat com.tingyun.smartmistakebook.visualjs.IsolationGate"
        ".require(LowLoadProbe.kt:209)\n"
        + _status(method, -4, stream="assumption failed")
    )


def _pass_block(method: str) -> str:
    return _status(method, 1, stream="") + _status(method, 0, stream=".")


def _fail_block(method: str) -> str:
    return (
        _status(method, 1, stream="")
        + "INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: boom\n"
        + "\tat com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest"
        f".{method}(VisualJsIsolationInstrumentedTest.kt:1)\n"
        + _status(method, -2, stream="")
    )


def _suite_rows(methods: list[str], floor_ok: bool = True, canary_ok: bool | None = True,
                dump: bool = False) -> list[dict]:
    expectations = json.loads(EXPECTATIONS.read_text(encoding="utf-8"))
    specs = {item["method"]: item for item in expectations["methods"]}
    rows: list[dict] = [
        {"v": 1, "seq": 1, "phase": "suite_start", "t_ms": 1000, "cpu_ms": 10,
         "run": "unit", "arm": "OFF"},
        {"v": 1, "seq": 2, "phase": "canary", "t_ms": 1001, "cpu_ms": 10},
    ]
    seq = 3
    gate_index = 0
    for method in methods:
        spec = specs.get(method, {"floor_ms": 0})
        wall = (spec.get("floor_ms") or 0) + 100 if floor_ok else 50
        rows.append({"v": 1, "seq": seq, "phase": "rule_begin", "method": method,
                     "t_ms": 2000, "cpu_ms": 100})
        seq += 1
        rows.append({"v": 1, "seq": seq, "phase": "gate", "method": method,
                     "verdict": "GATED", "reason": "", "shell_gate": "PASS",
                     "shell_reason": "", "reused": gate_index > 0, "t_ms": 2001,
                     "cpu_ms": 100})
        gate_index += 1
        seq += 1
        rows.append({"v": 1, "seq": seq, "phase": "body_begin", "method": method,
                     "t_ms": 2010, "cpu_ms": 101, "t_ns": 1})
        seq += 1
        rows.append({"v": 1, "seq": seq, "phase": "body_end", "method": method,
                     "outcome": "PASS", "wall_ms": wall, "cpu_delta_ms": 50,
                     "cpu_unreadable": False, "threads": 20, "t_ms": 2010 + wall,
                     "cpu_ms": 151})
        seq += 1
        rows.append({"v": 1, "seq": seq, "phase": "rule_end", "method": method,
                     "t_ms": 2011 + wall, "cpu_ms": 152})
        seq += 1
    suite_end = {"v": 1, "seq": seq, "phase": "suite_end", "rows": len(rows) + 1,
                 "canary_ok": canary_ok, "t_ms": 99999, "cpu_ms": 999}
    if dump:
        suite_end["witness_unavailable"] = "canary_readback_failed"
    rows.append(suite_end)
    return rows


def _stream(methods: list[str], blocks: dict[str, str] | None = None) -> str:
    blocks = blocks or {}
    chunks = [f"INSTRUMENTATION_STATUS: numtests={len(methods)}\n"]
    for method in methods:
        chunks.append(blocks.get(method, _pass_block(method)))
    return "".join(chunks)


def _report(rows: list[dict], stream_text: str, *, roster: list[str] | None = None) -> dict:
    expectations = W.load_expectations(EXPECTATIONS)
    roster = roster if roster is not None else _methods()
    return W.build_report(
        witness_rows=rows,
        stream=W.parse_stream(stream_text),
        expectations=expectations,
        roster=roster,
        witness_source="unit",
        stream_supplied=True,
    )


class StreamOnlyClassificationTest(unittest.TestCase):
    def test_fifteen_assumption_skips_are_env_skip_not_not_scheduled(self):
        methods = _methods()
        stream = W.parse_stream(_stream(methods, {m: _assumption_block(m) for m in methods}))
        for method in methods:
            state, reasons = W.classify([], stream.get(method), "stream_only")
            self.assertEqual("ENV_SKIP", state, method)
            self.assertNotEqual("NOT_SCHEDULED", state, method)
            self.assertTrue(any("IsolationGate" in reason for reason in reasons))

    def test_stream_only_cannot_judge_body_states(self):
        # 即使流里有 CODE:1/CODE:0，没有 witness 也不能说"体内执行过"。
        stream = W.parse_stream(_status("mOne", 1) + _status("mOne", 0))
        state, _ = W.classify([], stream.get("mOne"), "stream_only")
        self.assertEqual("UNDECIDABLE_NO_WITNESS", state)

    def test_missing_everywhere_is_not_scheduled(self):
        state, _ = W.classify([], None, "stream_only")
        self.assertEqual("NOT_SCHEDULED", state)


class WitnessClassificationTest(unittest.TestCase):
    def test_gate_ungated_without_body_is_env_skip(self):
        method = "mOne"
        rows = [
            {"phase": "gate", "method": method, "verdict": "UNGATED",
             "reason": "instrumentation_self_probe:own_cpu_busy", "reused": False},
        ]
        state, reasons = W.classify(rows, {"status": "SKIP", "stack_first": None},
                                    "witness+stream")
        self.assertEqual("ENV_SKIP", state)
        self.assertTrue(any("own_cpu_busy" in reason for reason in reasons))

    def test_gate_gated_without_body_is_fixture_skip(self):
        rows = [{"phase": "gate", "method": "mOne", "verdict": "GATED", "reason": ""}]
        state, _ = W.classify(rows, {"status": "SKIP", "stack_first": None},
                              "witness+stream")
        self.assertEqual("FIXTURE_SKIP", state)

    def test_body_begin_without_end_is_body_crash(self):
        rows = [{"phase": "body_begin", "method": "mOne"}]
        state, _ = W.classify(rows, {"status": "FAIL", "stack_first": None},
                              "witness+stream")
        self.assertEqual("BODY_CRASH", state)

    def test_body_assumption_is_body_skip(self):
        rows = [
            {"phase": "body_begin", "method": "mOne"},
            {"phase": "body_end", "method": "mOne", "outcome": "ASSUMPTION"},
        ]
        state, _ = W.classify(rows, {"status": "SKIP", "stack_first": None},
                              "witness+stream")
        self.assertEqual("BODY_SKIP", state)

    def test_witness_rows_without_stream_block_is_incomplete(self):
        rows = [{"phase": "body_begin", "method": "mOne"}]
        state, _ = W.classify(rows, None, "witness+stream")
        self.assertEqual("STREAM_INCOMPLETE", state)

    def test_stream_pass_without_body_is_contradiction(self):
        state, _ = W.classify(
            [{"phase": "gate", "method": "mOne", "verdict": "GATED"}],
            {"status": "PASS", "stack_first": None},
            "witness+stream",
        )
        self.assertEqual("CONTRADICTION_PASS_WITHOUT_BODY", state)


class ReportTest(unittest.TestCase):
    def test_all_fifteen_executed_satisfies_floors(self):
        methods = _methods()
        report = _report(_suite_rows(methods), _stream(methods))
        self.assertEqual(15, report["counts"]["EXECUTED"])
        self.assertEqual([], report["violations"])
        self.assertTrue(report["canary_ok"])
        self.assertGreaterEqual(
            report["load_evidence"]["total_body_wall_ms"],
            report["load_evidence"]["floor_sum_ms"],
        )

    def test_skipped_round_cannot_meet_floors(self):
        # 批次 42 r1 的形状：15 个 -4、零 body。总时长 820 ms 远低于 floor_sum 44450。
        methods = _methods()
        stream = _stream(methods, {m: _assumption_block(m) for m in methods})
        report = _report([], stream)
        self.assertEqual(15, report["counts"]["ENV_SKIP"])
        self.assertEqual(0, report["counts"]["EXECUTED"])
        self.assertLess(report["load_evidence"]["total_body_wall_ms"],
                        report["load_evidence"]["floor_sum_ms"])

    def test_floor_violation_is_reported(self):
        methods = _methods()
        report = _report(_suite_rows(methods, floor_ok=False), _stream(methods))
        self.assertEqual(15, report["counts"]["EXECUTED"])
        # 只有 floor_ms > 0 的 9 例会红；floor=0 的用例只靠 cpu_delta>=1 区分。
        specs = json.loads(EXPECTATIONS.read_text(encoding="utf-8"))["methods"]
        nonzero = sum(1 for spec in specs if (spec.get("floor_ms") or 0) > 0)
        hits = [v for v in report["violations"] if "floor_ms" in v]
        self.assertEqual(nonzero, len(hits), report["violations"])

    def test_zero_cpu_delta_is_reported(self):
        methods = _methods()
        rows = _suite_rows(methods)
        for row in rows:
            if row.get("phase") == "body_end":
                row["cpu_delta_ms"] = 0
        report = _report(rows, _stream(methods))
        hits = [v for v in report["violations"] if "cpu_delta_ms" in v]
        self.assertEqual(15, len(hits), report["violations"])

    def test_canary_failure_is_not_green(self):
        methods = _methods()
        report = _report(_suite_rows(methods, canary_ok=False), _stream(methods))
        self.assertFalse(report["canary_ok"])
        # build_report 本身不带 requirements；main() 把它变成违规。

    def test_roster_mismatch_is_a_violation(self):
        methods = _methods()
        report = _report(_suite_rows(methods), _stream(methods), roster=methods[:-1])
        self.assertTrue(any("roster_mismatch" in v for v in report["violations"]))

    def test_gate_cache_is_countable(self):
        methods = _methods()
        report = _report(_suite_rows(methods), _stream(methods))
        self.assertEqual(15, report["gate_cache"]["gate_rows"])
        self.assertEqual(14, report["gate_cache"]["reused_true"])
        self.assertEqual(1, report["gate_cache"]["reused_false"])


class MainTest(unittest.TestCase):
    def _write(self, directory: Path, name: str, text: str) -> Path:
        path = directory / name
        path.write_text(text, encoding="utf-8")
        return path

    def _run(self, *args: str) -> tuple[int, dict]:
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            code = W.main(list(args))
        return code, json.loads(buffer.getvalue())

    def test_cli_green_requires_executed_and_floors(self):
        methods = _methods()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            witness = root / "witness.jsonl"
            witness.write_text(
                "\n".join(json.dumps(row) for row in _suite_rows(methods)) + "\n",
                encoding="utf-8",
            )
            stream = self._write(root, "run.instrument.log", _stream(methods))
            code, report = self._run(
                "--witness", str(witness), "--stream", str(stream),
                "--require-executed", "--require-floors",
            )
            self.assertEqual(0, code, report["violations"])
            self.assertTrue(report["ok"])

    def test_cli_canary_failure_exits_one(self):
        methods = _methods()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            witness = root / "witness.jsonl"
            witness.write_text(
                "\n".join(json.dumps(row) for row in _suite_rows(methods, canary_ok=False)) + "\n",
                encoding="utf-8",
            )
            stream = self._write(root, "run.instrument.log", _stream(methods))
            code, report = self._run("--witness", str(witness), "--stream", str(stream))
            self.assertEqual(1, code)
            self.assertTrue(any("canary" in v for v in report["violations"]))

    def test_cli_uses_logcat_mirror_when_file_absent(self):
        methods = _methods()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mirror_lines = [
                "01-01 00:00:00.000 I/SMB_ISOLATION_WITNESS(1): "
                + W.WITNESS_MIRROR + " " + json.dumps(row)
                for row in _suite_rows(methods)
            ]
            logcat = self._write(root, "run.logcat", "\n".join(mirror_lines) + "\n")
            stream = self._write(root, "run.instrument.log", _stream(methods))
            code, report = self._run(
                "--logcat", str(logcat), "--stream", str(stream),
                "--require-executed",
            )
            self.assertEqual(0, code, report["violations"])
            self.assertEqual("witness+stream", report["evidence"])
            self.assertTrue(report["witness_source"].endswith("#logcat"))

    def test_cli_stream_only_requires_fails_fast(self):
        methods = _methods()
        stream_text = _stream(methods, {m: _assumption_block(m) for m in methods})
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            stream = self._write(root, "run.instrument.log", stream_text)
            code, report = self._run(
                "--stream", str(stream), "--allow-missing-witness",
                "--require-executed",
            )
            self.assertEqual(1, code)
            self.assertEqual("stream_only", report["evidence"])
            self.assertEqual(15, report["counts"]["ENV_SKIP"])


if __name__ == "__main__":
    unittest.main()
