#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""D0 自验收驱动（批次 45 / 计划 WS2 13–22）。

## 它消灭的具体失败

批次 44 的 D0 100 行 verdict 全是 `UNVERIFIED`、证据列全是 `-`：台账里写着
“报告会抓占位命令 / lint 会抓 summary / trace 会抓缺路径”，但没有任何一条
**当前可复跑**的命令证明工具真的会这么做。批次 44 只证明了工具能抓别人，没有
证明它抓自己。

本驱动为每个验收工具给出同一形态的自测：真实 CLI 调用 → 断言退出码；再用
**自突变**（把输入改坏）确认工具翻红；异常输入确认 exit 2 而不是静默通过。
它不重复单元测试的内部断言（那由 `tools/tests/` 承担），只做端到端复算。

## 为什么只打印检查行

子进程原文里可能含 `UNVERIFIED`、`-4`、`SKIP` 等状态词；把这些词抄进验收日志
会让 `acceptance_lint.py` 把“日志记录了一次失败实验”误读成“这一行不是 PASS”。
所以原文落在 `--work` 目录，本驱动的 stdout 只输出
`CHECK <name> EXPECTED=<n> ACTUAL=<n> OK|BAD` 与末行 `EXIT=<n>`。

## 用法

    python -I tools/ci/d0_self_acceptance.py --group report
    python -I tools/ci/d0_self_acceptance.py --group docs --section dod
    python -I tools/ci/d0_self_acceptance.py --group all

`--group hash` 对 `--evidence` 目录生成/校验哈希清单；该组必须在其余组把证据
落盘之后运行（`--group all` 已按此顺序）。

退出码：0 全部检查符合期望；1 有检查不符合；2 参数/环境错误。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import struct
import subprocess
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CI = ROOT / "tools" / "ci"
LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"
EVIDENCE = ROOT / "docs" / "research" / "evidence" / "2026-11-06-batch-45" / "d0-self"
MANIFEST = EVIDENCE.parent / "hash" / "MANIFEST.sha256"
FAKE_GREEN_41 = ROOT / "tools" / "tests" / "fixtures" / "acceptance" / "fake_green_batch41.ledger.md"
FAKE_GREEN_42 = ROOT / "tools" / "tests" / "fixtures" / "acceptance" / "fake_green_batch42.ledger.md"

sys.path.insert(0, str(CI))

import acceptance_model as M  # noqa: E402
import screenshot_baseline as SB  # noqa: E402

HEADER = "| ID | 域 | 检查 | 方法 | 证据 | verdict |\n|---|---|---|---|---|---|\n"
DOC_SECTIONS = ("dod", "verdicts", "patch", "runbook", "protocol", "forgery", "replay")


# --------------------------------------------------------------------- harness


class Recorder:
    def __init__(self, label: str, work: Path) -> None:
        self.label = label
        self.work = work
        self.checks = 0
        self.failures = 0

    def _emit(self, name: str, expected: int, actual: int, extra: str = "") -> bool:
        ok = expected == actual
        self.checks += 1
        if not ok:
            self.failures += 1
        suffix = f" {extra}" if extra else ""
        print(f"CHECK {name} EXPECTED={expected} ACTUAL={actual} "
              f"{'OK' if ok else 'BAD'}{suffix}")
        return ok

    def run(self, name: str, argv: list[str], expected: int,
            cwd: Path | None = None) -> subprocess.CompletedProcess:
        proc = subprocess.run(
            [sys.executable, "-I", *argv],
            cwd=str(cwd or ROOT), capture_output=True, text=True,
            encoding="utf-8", errors="replace",
        )
        raw = self.work / "raw" / f"{name}.txt"
        raw.parent.mkdir(parents=True, exist_ok=True)
        raw.write_text(
            "ARGV=" + repr(argv) + f"\nRC={proc.returncode}\n"
            "--- stdout\n" + proc.stdout + "\n--- stderr\n" + proc.stderr + "\n",
            encoding="utf-8", newline="\n",
        )
        self._emit(name, expected, proc.returncode)
        return proc

    def ok(self, name: str, condition: bool, extra: str = "") -> bool:
        return self._emit(name, 1, 1 if condition else 0, extra)

    def rc(self, name: str, expected: int, actual: int) -> bool:
        return self._emit(name, expected, actual)

    @staticmethod
    def note(key: str, value: object) -> None:
        print(f"OBSERVED {key}={value}")


def _write(path: Path, text: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")
    return path


def _ledger_row(row_id: str, domain: str, verdict: str, evidence: str = "-",
                method: str = "cmd: python -I tools/ci/acceptance_init.py --check") -> str:
    return f"| {row_id} | {domain} | 自测行 | {method} | {evidence} | {verdict} |\n"


def _ledger(rows: str) -> str:
    return HEADER + rows


def _codes(data: dict) -> list[str]:
    return [item.get("code", "") for item in data.get("violations", [])]


# ----------------------------------------------------------------------- model


def group_model(rec: Recorder) -> None:
    rows, issues = M.parse_ledger(LEDGER)
    rec.ok("ledger_rows_1200", len(rows) == 1200, f"rows={len(rows)}")
    identity = M.identity_issues(rows)
    rec.ok("ledger_ids_and_counts", not identity, f"identity_issues={len(identity)}")
    rec.ok("columns_six", not [i for i in issues if i["code"] == "ROW_COLUMN_COUNT"])
    rec.ok("verdicts_legal", not [i for i in issues if i["code"] == "BAD_VERDICT"])
    rec.ok("verdicts_nonempty", all(row.verdict for row in rows))
    total_tokens = sum(len(row.evidence()) for row in rows)
    bad_tokens = [i for i in issues if i["code"] == "BAD_EVIDENCE"]
    rec.ok("evidence_tokens_parse", not bad_tokens and total_tokens > 0,
           f"tokens={total_tokens}")
    rec.run("init_check", ["tools/ci/acceptance_init.py", "--check"], 0)

    mutations = {
        "verdict": (_ledger(_ledger_row("D0-001", "D0", "OK")), "BAD_VERDICT"),
        "duplicate": (_ledger(_ledger_row("D0-001", "D0", "PASS") * 2), "DUPLICATE_ID"),
        "columns": ("| D0-001 | D0 | 自测行 | cmd: x | PASS |\n", "ROW_COLUMN_COUNT"),
        "domain": (_ledger(_ledger_row("A-001", "D0", "PASS")), "DOMAIN_MISMATCH"),
        "kind": (_ledger(_ledger_row("D0-001", "D0", "PASS", "log:notes/x.txt")),
                 "BAD_EVIDENCE"),
        "empty_path": (_ledger(_ledger_row("D0-001", "D0", "PASS", "cmd:")),
                       "BAD_EVIDENCE"),
    }
    for name, (text, code) in mutations.items():
        _, mut_issues = M.parse_ledger_text(text, name)
        rec.ok(f"mutation_{name}", any(i["code"] == code for i in mut_issues))


# ------------------------------------------------------------------------ init


def group_init(rec: Recorder, tmp: Path) -> None:
    target = tmp / "generated.md"
    rec.run("write_fresh", ["tools/ci/acceptance_init.py", "--ledger", str(target), "--write"], 0)
    rows, issues = M.parse_ledger(target)
    rec.ok("generated_1200", len(rows) == 1200 and not M.identity_issues(rows))

    text = target.read_text(encoding="utf-8")
    marker = "| D0-001 | D0 |"
    lines = text.splitlines(keepends=True)
    for index, line in enumerate(lines):
        if line.startswith(marker):
            parts = line.rstrip("\n").split("|")
            parts[5] = " cmd:build/keep.txt#exit=0; test:build/keep.xml "
            parts[6] = " FAIL "
            lines[index] = "|".join(parts) + "\n"
            break
    kept = _write(tmp / "kept.md", "".join(lines))
    rec.run("write_preserve", ["tools/ci/acceptance_init.py", "--ledger", str(kept), "--write"], 0)
    after_rows, _ = M.parse_ledger(kept)
    row = next(item for item in after_rows if item.row_id == "D0-001")
    rec.ok("preserved_verdict", row.verdict == "FAIL")
    rec.ok("preserved_evidence", "build/keep.txt" in row.evidence_text)
    first_bytes = kept.read_bytes()
    rec.run("write_idempotent", ["tools/ci/acceptance_init.py", "--ledger", str(kept), "--write"], 0)
    rec.ok("idempotent_bytes", kept.read_bytes() == first_bytes)

    drifted = _write(tmp / "drifted.md", "".join(
        line for line in text.splitlines(keepends=True) if not line.startswith(marker)))
    proc = rec.run("check_drift", ["tools/ci/acceptance_init.py", "--ledger", str(drifted), "--check"], 1)
    rec.ok("drift_reported", "MISSING_ROW" in proc.stdout + proc.stderr)

    bad = _write(tmp / "bad.md", text.replace("| D0-002 | D0 | 台账 ledger.md：ID 唯一且属于 13 个域前缀 | cmd: python -I tools/ci/acceptance_init.py --check | - | UNVERIFIED |",
                                              "| D0-002 | D0 | 台账 ledger.md：ID 唯一且属于 13 个域前缀 | cmd: python -I tools/ci/acceptance_init.py --check | - | OK |"))
    proc = rec.run("check_bad_verdict", ["tools/ci/acceptance_init.py", "--ledger", str(bad), "--check"], 1)
    rec.ok("bad_verdict_reported", "BAD_VERDICT" in proc.stdout + proc.stderr)


# ---------------------------------------------------------------------- report


GOOD_XML = '<testsuite name="self" tests="2" failures="0" errors="0" skipped="0"/>\n'


def _report_fixture(tmp: Path, name: str, evidence: str, log_text: str | None = None,
                    xml_text: str = GOOD_XML) -> tuple[Path, Path]:
    root = tmp / name
    (root / "logs").mkdir(parents=True, exist_ok=True)
    (root / "xml").mkdir(parents=True, exist_ok=True)
    (root / "json").mkdir(parents=True, exist_ok=True)
    (root / "notes").mkdir(parents=True, exist_ok=True)
    _write(root / "logs" / "ok.log",
           log_text or "COMMAND: python -I tools/ci/check_lockfiles.py\nEXIT=0\n")
    _write(root / "xml" / "ok.xml", xml_text)
    ledger = _write(root / "ledger.md", _ledger(_ledger_row("D0-001", "D0", "PASS", evidence)))
    return root, ledger


def group_report(rec: Recorder, tmp: Path) -> None:
    good_root, good_ledger = _report_fixture(tmp, "good", "cmd:logs/ok.log; test:xml/ok.xml")
    proc = rec.run("good_exit", ["tools/ci/acceptance_report.py", "--ledger", str(good_ledger),
                                 "--repo-root", str(good_root), "--partial"], 0)
    good = json.loads(proc.stdout)
    rec.ok("good_two_kinds", good.get("pass_with_two_kinds") == 1)
    rec.ok("good_state", good.get("state") == "PASS")
    digest = hashlib.sha256(good_ledger.read_bytes()).hexdigest()
    rec.ok("json_ledger_sha", good.get("ledger_sha256") == digest)
    proc2 = rec.run("stable_exit", ["tools/ci/acceptance_report.py", "--ledger", str(good_ledger),
                                    "--repo-root", str(good_root), "--partial"], 0)
    second = json.loads(proc2.stdout)
    rec.ok("stable_counts", second.get("counts") == good.get("counts"))

    cases = {
        "one_kind": ("cmd:logs/ok.log", None, GOOD_XML, "PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE"),
        "missing_path": ("cmd:logs/absent.log; test:xml/ok.xml", None, GOOD_XML,
                         "EVIDENCE_PATH_MISSING"),
        "placeholder": ("cmd:logs/ok.log; test:xml/ok.xml",
                        "CMD: ./gradlew --tests <E-domain classes>\nEXIT=0\n",
                        GOOD_XML, "PLACEHOLDER_CMD"),
        "nonzero_exit": ("cmd:logs/ok.log; test:xml/ok.xml", "EXIT=1\n", GOOD_XML,
                         "CMD_EXIT_NONZERO"),
        "xml_failures": ("cmd:logs/ok.log; test:xml/ok.xml", None,
                         '<testsuite name="self" tests="2" failures="1" errors="0"/>\n',
                         "TEST_XML_FAILURES"),
        "summary": ("cmd:logs/ok.log; test:xml/ok.xml; file:notes/run-summary.md", None,
                    GOOD_XML, "SUMMARY_AS_EVIDENCE"),
    }
    for name, (evidence, log_text, xml_text, code) in cases.items():
        root, ledger = _report_fixture(tmp, name, evidence, log_text, xml_text)
        _write(root / "notes" / "run-summary.md", "summary\n")
        proc = rec.run(f"mutation_{name}", [
            "tools/ci/acceptance_report.py", "--ledger", str(ledger),
            "--repo-root", str(root), "--partial"], 1)
        data = json.loads(proc.stdout) if proc.stdout.strip().startswith("{") else {}
        rec.ok(f"caught_{name}", code in _codes(data))

    review_root, review_ledger = _report_fixture(tmp, "review_self", "review:json/review.json; cmd:logs/ok.log")
    _write(review_root / "json" / "review.json", json.dumps({
        "reviewer": "author", "authors": ["author"], "run_id": "r1",
        "ledger_sha256": hashlib.sha256(review_ledger.read_bytes()).hexdigest(),
    }, ensure_ascii=False))
    proc = rec.run("mutation_self_review", [
        "tools/ci/acceptance_report.py", "--ledger", str(review_ledger),
        "--repo-root", str(review_root), "--partial"], 1)
    rec.ok("caught_self_review", "REVIEW_SELF" in _codes(json.loads(proc.stdout)))

    miss_root, miss_ledger = _report_fixture(tmp, "review_missing", "review:json/review.json; cmd:logs/ok.log")
    _write(miss_root / "json" / "review.json", json.dumps({"reviewer": "rev", "authors": []}))
    proc = rec.run("mutation_review_missing_field", [
        "tools/ci/acceptance_report.py", "--ledger", str(miss_ledger),
        "--repo-root", str(miss_root), "--partial"], 1)
    rec.ok("caught_review_missing", "REVIEW_MISSING_FIELD" in _codes(json.loads(proc.stdout)))

    dev_root, dev_ledger = _report_fixture(tmp, "device_zero", "device:json/device.json; test:xml/ok.xml")
    _write(dev_root / "json" / "device.json", json.dumps({
        "serial": "emulator-5554", "apk_sha256": "0" * 64,
        "verdict": "PASS", "counts": {"evaluated": 0},
    }))
    proc = rec.run("mutation_device_zero", [
        "tools/ci/acceptance_report.py", "--ledger", str(dev_ledger),
        "--repo-root", str(dev_root), "--partial"], 1)
    rec.ok("caught_device_zero", "DEVICE_EVIDENCE_NOT_EVALUATED" in _codes(json.loads(proc.stdout)))

    complete_root, complete_ledger = _report_fixture(tmp, "incomplete", "cmd:logs/ok.log; test:xml/ok.xml")
    _write(complete_ledger, _ledger(
        _ledger_row("D0-001", "D0", "PASS", "cmd:logs/ok.log; test:xml/ok.xml")
        + _ledger_row("D0-002", "D0", "UNVERIFIED")))
    proc = rec.run("mutation_incomplete", [
        "tools/ci/acceptance_report.py", "--ledger", str(complete_ledger),
        "--repo-root", str(complete_root), "--partial", "--require-complete"], 1)
    rec.ok("caught_incomplete", "NOT_ACCEPTANCE_COMPLETE" in _codes(json.loads(proc.stdout)))

    # D0-020：非法 verdict / 重复 ID / 缺行在**完整 1200 行**口径下也必须 exit 1
    # （不能用 --partial 把缺行过滤掉）。
    real_text = LEDGER.read_text(encoding="utf-8")
    real_lines = [line for line in real_text.splitlines(keepends=True)
                  if not line.startswith("| D0-001 |")]
    missing = _write(tmp / "full-missing.md", "".join(real_lines))
    proc = rec.run("full_missing_row", ["tools/ci/acceptance_report.py",
                                        "--ledger", str(missing)], 1)
    rec.ok("caught_missing_row", "MISSING_ROW" in _codes(json.loads(proc.stdout)))
    duplicate_line = next(line for line in real_text.splitlines(keepends=True)
                          if line.startswith("| D0-001 |"))
    duplicated = _write(tmp / "full-duplicate.md", real_text + duplicate_line)
    proc = rec.run("full_duplicate", ["tools/ci/acceptance_report.py",
                                      "--ledger", str(duplicated)], 1)
    rec.ok("caught_duplicate", "DUPLICATE_ID" in _codes(json.loads(proc.stdout)))
    bad_lines = real_text.splitlines(keepends=True)
    for index, line in enumerate(bad_lines):
        if line.startswith("| D0-002 |"):
            cells = line.rstrip("\n").split("|")
            cells[6] = " OK "
            bad_lines[index] = "|".join(cells) + "\n"
            break
    bad_verdict = _write(tmp / "full-bad-verdict.md", "".join(bad_lines))
    proc = rec.run("full_bad_verdict", ["tools/ci/acceptance_report.py",
                                        "--ledger", str(bad_verdict)], 1)
    rec.ok("caught_bad_verdict", "BAD_VERDICT" in _codes(json.loads(proc.stdout)))


# ------------------------------------------------------------------------ lint


def group_lint(rec: Recorder, tmp: Path) -> None:
    proc = rec.run("real_ledger", ["tools/ci/acceptance_lint.py", "--ledger", str(LEDGER)], 0)
    data = json.loads(proc.stdout)
    rec.ok("real_zero_violations", data.get("violations") == [] and data.get("ok") is True)

    root = tmp / "cases"
    (root / "logs").mkdir(parents=True, exist_ok=True)
    (root / "notes").mkdir(parents=True, exist_ok=True)
    _write(root / "logs" / "ok.log", "COMMAND: python -I tools/ci/check_lockfiles.py\nEXIT=0\n")
    _write(root / "logs" / "skip.log", "INSTRUMENTATION_STATUS_CODE: -4\nEXIT=0\n")
    _write(root / "logs" / "bad_exit.log", "EXIT=1\n")
    _write(root / "notes" / "run-summary.md", "summary\n")
    _write(root / "json" / "review.json", json.dumps({
        "reviewer": "author", "authors": ["author"], "run_id": "r1", "ledger_sha256": "0" * 64,
    }))

    cases = {
        "summary": (_ledger_row("D0-001", "D0", "PASS", "file:notes/run-summary.md; cmd:logs/ok.log"),
                    "SUMMARY_AS_EVIDENCE"),
        "skip": (_ledger_row("D0-001", "D0", "PASS", "cmd:logs/skip.log"),
                 "SKIPPED_GATE_AS_PASS"),
        "bad_exit": (_ledger_row("D0-001", "D0", "PASS", "cmd:logs/bad_exit.log"),
                     "SKIPPED_GATE_AS_PASS"),
        "self_review": (_ledger_row("D0-001", "D0", "PASS", "review:json/review.json"),
                        "SELF_REVIEW"),
        "empty": (_ledger_row("D0-001", "D0", "PASS", "-"), "PASS_EMPTY_EVIDENCE"),
        "marked_pass": (_ledger_row("D0-001", "D0", "PASS", "cmd:logs/ok.log",
                                    "UNVERIFIED: 尚未执行"), "UNVERIFIED_AS_PASS"),
        "duplicate": (_ledger_row("D0-001", "D0", "PASS", "cmd:logs/ok.log") * 2,
                      "DUPLICATE_ID"),
    }
    for name, (rows, code) in cases.items():
        ledger = _write(tmp / f"lint-{name}.md", _ledger(rows))
        proc = rec.run(f"mutation_{name}", [
            "tools/ci/acceptance_lint.py", "--ledger", str(ledger),
            "--repo-root", str(root), "--partial"], 1)
        data = json.loads(proc.stdout)
        rec.ok(f"caught_{name}", code in _codes(data))

    for name, fixture in (("batch41", FAKE_GREEN_41), ("batch42", FAKE_GREEN_42)):
        rec.run(f"replay_{name}", [
            "tools/ci/acceptance_lint.py", "--ledger", str(fixture),
            "--repo-root", str(ROOT), "--partial"], 1)

    reuse_rows = "".join(
        _ledger_row(f"D0-{index:03d}", "D0", "PASS", "cmd:logs/ok.log")
        for index in range(1, 12))
    reuse_ledger = _write(tmp / "lint-reuse.md", _ledger(reuse_rows))
    proc = rec.run("reuse_warning", [
        "tools/ci/acceptance_lint.py", "--ledger", str(reuse_ledger),
        "--repo-root", str(root), "--partial"], 0)
    data = json.loads(proc.stdout)
    rec.ok("reuse_warned", bool(data.get("warnings")) and data.get("violations") == [])
    proc = rec.run("strict_warnings", [
        "tools/ci/acceptance_lint.py", "--ledger", str(reuse_ledger),
        "--repo-root", str(root), "--partial", "--strict-warnings"], 1)
    rec.ok("reuse_escalated", "EVIDENCE_REUSED" in _codes(json.loads(proc.stdout)))


# ----------------------------------------------------------------------- trace


def group_trace(rec: Recorder, tmp: Path) -> None:
    out = tmp / "trace-real.json"
    proc = rec.run("real_ledger", ["tools/ci/trace_requirements.py", "--ledger", str(LEDGER),
                                   "--out", str(out)], 1)
    data = json.loads(out.read_text(encoding="utf-8"))
    rec.ok("real_rows_1200", data.get("total") == 1200)
    rec.ok("real_gaps_visible", bool(data.get("gaps")))
    rec.ok("d0_rows_traced", all(any(row["id"] == f"D0-{i:03d}" for row in data["rows"])
                                 for i in range(1, 101)))

    def trace_case(name: str, rows: str, expected: int) -> dict:
        ledger = _write(tmp / f"trace-{name}.md", _ledger(rows))
        out = tmp / f"trace-{name}.json"
        rec.run(f"case_{name}", ["tools/ci/trace_requirements.py", "--ledger", str(ledger),
                                 "--repo-root", str(ROOT), "--partial", "--out", str(out)],
                expected)
        return json.loads(out.read_text(encoding="utf-8")).get("gaps", [])

    clean = trace_case("clean", _ledger_row(
        "D0-001", "D0", "PASS", "test:tools/tests/test_acceptance_report.py",
        "test: tools/tests/test_acceptance_report.py"), 0)
    rec.ok("clean_no_gaps", clean == [])
    gaps = trace_case("missing_test", _ledger_row(
        "D0-001", "D0", "PASS", "test:tools/tests/no_such_test.py",
        "test: tools/tests/no_such_test.py"), 1)
    rec.ok("caught_missing_test", "MISSING_TEST_PATH" in [g["code"] for g in gaps])
    gaps = trace_case("placeholder", _ledger_row(
        "D0-001", "D0", "PASS", "test:tools/tests/test_acceptance_report.py",
        "test: tools/tests/<JourneyTest>.py"), 1)
    rec.ok("caught_placeholder", "PLACEHOLDER" in [g["code"] for g in gaps])
    gaps = trace_case("no_landing", _ledger_row(
        "D0-001", "D0", "PASS", "file:docs/acceptance/dod.md",
        "file: docs/acceptance/dod.md"), 1)
    rec.ok("caught_no_landing", "PASS_WITHOUT_TEST_LANDING" in [g["code"] for g in gaps])
    gaps = trace_case("device_missing", _ledger_row(
        "D0-001", "D0", "PASS", "cmd:tools/tests/fixtures/acceptance/fake_green_run.log",
        "device: adb shell am instrument -w Example"), 1)
    rec.ok("caught_device_missing", "DEVICE_EVIDENCE_MISSING" in [g["code"] for g in gaps])
    gaps = trace_case("evidence_missing", _ledger_row(
        "D0-001", "D0", "PASS", "test:tools/tests/absent-test.xml",
        "test: tools/tests/test_acceptance_report.py"), 1)
    rec.ok("caught_evidence_missing", "MISSING_EVIDENCE_PATH" in [g["code"] for g in gaps])

    second = tmp / "trace-real-2.json"
    rec.run("real_ledger_again", ["tools/ci/trace_requirements.py", "--ledger", str(LEDGER),
                                  "--out", str(second)], 1)
    again = json.loads(second.read_text(encoding="utf-8"))
    rec.ok("json_stable", (again.get("total"), len(again.get("gaps", [])))
           == (data.get("total"), len(data.get("gaps", []))))


# ------------------------------------------------------------------------ hash


def group_hash(rec: Recorder, tmp: Path) -> None:
    root = tmp / "hash-root"
    shutil.rmtree(root, ignore_errors=True)
    _write(root / "a.txt", "alpha\n")
    _write(root / "sub" / "b.txt", "beta\n")
    manifest = tmp / "manifest.sha256"
    proc = rec.run("generate", ["tools/ci/acceptance_hash_manifest.py", "--root", str(root),
                                "--out", str(manifest), "--self-hash"], 0)
    self_hash = proc.stdout.strip().splitlines()[-1] if proc.stdout.strip() else ""
    rec.ok("self_hash_64hex", len(self_hash) == 64 and all(c in "0123456789abcdef" for c in self_hash))
    rec.note("fixture_self_sha256", self_hash)
    rec.run("verify_clean", ["tools/ci/acceptance_hash_manifest.py", "--root", str(root),
                             "--verify", str(manifest)], 0)
    _write(root / "a.txt", "alpha-changed\n")
    proc = rec.run("verify_changed", ["tools/ci/acceptance_hash_manifest.py", "--root", str(root),
                                      "--verify", str(manifest)], 1)
    rec.ok("caught_mismatch", "HASH_MISMATCH" in proc.stdout)
    (root / "sub" / "b.txt").unlink()
    proc = rec.run("verify_missing", ["tools/ci/acceptance_hash_manifest.py", "--root", str(root),
                                      "--verify", str(manifest)], 1)
    rec.ok("caught_missing_file", "MISSING_FILE" in proc.stdout)
    _write(root / "sub" / "b.txt", "beta\n")
    _write(root / "c.txt", "gamma\n")
    proc = rec.run("verify_unlisted", ["tools/ci/acceptance_hash_manifest.py", "--root", str(root),
                                       "--verify", str(manifest)], 1)
    rec.ok("caught_unlisted", "UNLISTED_FILE" in proc.stdout)

    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    proc = rec.run("evidence_generate", ["tools/ci/acceptance_hash_manifest.py", "--root", str(EVIDENCE),
                                         "--out", str(MANIFEST), "--self-hash"], 0)
    real_self = proc.stdout.strip().splitlines()[-1] if proc.stdout.strip() else ""
    rec.ok("evidence_self_hash_matches",
           MANIFEST.exists() and real_self == hashlib.sha256(MANIFEST.read_bytes()).hexdigest())
    rec.note("manifest_self_sha256", real_self)
    rec.ok("manifest_nonempty", MANIFEST.exists() and len(
        MANIFEST.read_text(encoding="utf-8").splitlines()) >= 16,
        f"lines={len(MANIFEST.read_text(encoding='utf-8').splitlines()) if MANIFEST.exists() else 0}")
    rec.run("evidence_verify", ["tools/ci/acceptance_hash_manifest.py", "--root", str(EVIDENCE),
                                "--verify", str(MANIFEST)], 0)
    manifest_lines = MANIFEST.read_text(encoding="utf-8").splitlines() if MANIFEST.exists() else []
    rec.ok("manifest_paths_posix",
           bool(manifest_lines) and all("\\" not in line and ":" not in line.split("  ", 1)[1]
                                        for line in manifest_lines))
    rec.ok("manifest_no_secrets",
           all(re.fullmatch(r"[0-9a-f]{64}  [^\s]+", line) for line in manifest_lines))
    rec.note("manifest_files", len(manifest_lines))


# ------------------------------------------------------------------ metamorphic


def group_metamorphic(rec: Recorder, tmp: Path) -> None:
    out = tmp / "demo.json"
    rec.run("demo", ["tools/ci/metamorphic_checks.py", "--demo", "--out", str(out)], 0)
    data = json.loads(out.read_text(encoding="utf-8"))
    rec.ok("demo_relations_12", len(data.get("relations", [])) == 12)
    rec.ok("demo_all_ok", data.get("ok") is True)

    pairs = tmp / "pairs"
    _write(pairs / "export" / "append_item.before.json", json.dumps(
        {"items": [{"id": "a", "text": "A"}], "page_count": 1, "extracted_text": ["A"]}))
    _write(pairs / "export" / "append_item.after.json", json.dumps(
        {"items": [{"id": "a", "text": "A"}], "page_count": 1, "extracted_text": ["B"]}))
    out = tmp / "pair.json"
    rec.run("pair_broken", ["tools/ci/metamorphic_checks.py", "--pairs-dir", str(pairs),
                            "--only", "export/append_item", "--out", str(out)], 1)
    broken = json.loads(out.read_text(encoding="utf-8"))
    relation = broken["relations"][0]
    rec.ok("counterexample_present", bool(relation.get("counterexample"))
           and relation.get("ok") is False)
    rec.ok("reason_readable", bool(relation.get("reason")))

    empty = tmp / "pairs-empty"
    empty.mkdir(parents=True, exist_ok=True)
    rec.run("missing_fixture", ["tools/ci/metamorphic_checks.py", "--pairs-dir", str(empty),
                                "--only", "export/append_item"], 2)
    rec.run("unknown_relation", ["tools/ci/metamorphic_checks.py", "--pairs-dir", str(pairs),
                                 "--only", "no/such"], 2)


# ------------------------------------------------------------------- screenshot


def _png_rgb(width: int, height: int, rgb: tuple[int, int, int]) -> bytes:
    def chunk(tag: bytes, payload: bytes) -> bytes:
        return (struct.pack(">I", len(payload)) + tag + payload
                + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))

    raw = b"".join(b"\x00" + bytes(rgb) * width for _ in range(height))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def group_screenshot(rec: Recorder, tmp: Path) -> None:
    root = tmp / "shots"
    base = root / "base.png"
    same = root / "same.png"
    diff = root / "diff.png"
    _write(base, "")
    base.write_bytes(SB.encode_png_rgba(4, 4, bytes((5, 5, 5, 255)) * 16))
    same.write_bytes(base.read_bytes())
    changed = bytearray(bytes((5, 5, 5, 255)) * 16)
    changed[0] = 250
    diff.write_bytes(SB.encode_png_rgba(4, 4, bytes(changed)))

    def compare(name: str, current: Path, expected: int, *extra: str) -> dict:
        out = root / f"{name}.json"
        rec.run(name, ["tools/ci/screenshot_baseline.py", "compare", "--baseline", str(base),
                       "--current", str(current), "--out", str(out),
                       "--max-mae", "0", "--max-changed-ratio", "0"], expected)
        return json.loads(out.read_text(encoding="utf-8")) if out.exists() else {}

    rec.ok("identical_pass", compare("identical", same, 0).get("ok") is True)
    rec.ok("changed_fails", compare("changed", diff, 1).get("ok") is False)
    rec.run("threshold_required", ["tools/ci/screenshot_baseline.py", "compare",
                                   "--baseline", str(base), "--current", str(same)], 2)
    report = compare("identical", same, 0)
    rec.ok("hashes_in_report",
           report.get("baseline_sha256") == hashlib.sha256(base.read_bytes()).hexdigest()
           and report.get("current_sha256") == hashlib.sha256(same.read_bytes()).hexdigest())
    rec.ok("mae_computed", report.get("mae") == 0.0 and report.get("changed_ratio") == 0.0)

    rgb = root / "rgb.png"
    rgb.write_bytes(_png_rgb(2, 2, (10, 20, 30)))
    decoded = SB.decode_png(rgb.read_bytes())
    rec.ok("rgb8_decoded", (decoded.width, decoded.height) == (2, 2)
           and decoded.pixels[:4] == bytes((10, 20, 30, 255)))

    tall = root / "tall.png"
    tall.write_bytes(SB.encode_png_rgba(4, 5, bytes((5, 5, 5, 255)) * 20))
    mismatch = compare("size_mismatch", tall, 1)
    rec.ok("size_mismatch_reason", mismatch.get("reason") == "SIZE_MISMATCH")

    # D0-050：采集侧车字段。真机采集不在本驱动内，改读批次 44 设备轨已归档的
    # 侧车（同一工具写出）并复算它与 PNG 的绑定。
    sidecar = (ROOT / "docs/research/evidence/2026-11-06-batch-44/device-track/probes"
               / "home-light.png.json")
    if sidecar.exists():
        meta = json.loads(sidecar.read_text(encoding="utf-8"))
        png = sidecar.with_name(sidecar.name[:-5])
        rec.ok("capture_sidecar_fields",
               {"serial", "captured_at", "apk_sha256", "sha256", "command"} <= set(meta))
        rec.ok("capture_sidecar_hash",
               png.exists() and meta.get("sha256") == hashlib.sha256(png.read_bytes()).hexdigest())
        rec.ok("capture_serial_present", bool(meta.get("serial")) and bool(meta.get("captured_at")))
    else:
        rec.ok("capture_sidecar_fields", False, "sidecar_missing")


# ------------------------------------------------------------------------- a11y


def _dump(clickable_bounds: str = "[0,0][100,100]", text: str = "OK",
          content_desc: str = "") -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<hierarchy rotation="0">\n'
        '  <node index="0" class="android.widget.FrameLayout" text="" '
        'content-desc="" clickable="false" enabled="true" bounds="[0,0][1080,1920]">\n'
        f'    <node index="1" class="android.widget.Button" text="{text}" '
        f'content-desc="{content_desc}" clickable="true" enabled="true" '
        f'bounds="{clickable_bounds}" />\n'
        '  </node>\n'
        '</hierarchy>\n'
    )


def group_a11y(rec: Recorder, tmp: Path) -> None:
    root = tmp / "a11y"
    clean = _write(root / "clean.xml", _dump())
    colors = _write(root / "colors.json", json.dumps(
        {"pairs": [{"label": "body", "fg": [0, 0, 0], "bg": [255, 255, 255]}]}))
    out = root / "clean.json"
    rec.run("clean_pass", ["tools/ci/a11y_audit.py", "audit", "--dump", str(clean),
                           "--colors", str(colors), "--out", str(out)], 0)
    data = json.loads(out.read_text(encoding="utf-8"))
    rec.ok("nodes_counted", data.get("nodes") == 2 and data.get("clickable") == 1)
    rec.ok("structural_pass", data.get("structural_ok") is True)
    rec.ok("contrast_pass", data.get("contrast", {}).get("status") == "PASS")
    rec.ok("dump_hash", data.get("dump_sha256") == hashlib.sha256(clean.read_bytes()).hexdigest())
    rec.ok("no_violations", data.get("violations") == [] and data.get("violation_codes") == [])

    small = _write(root / "small.xml", _dump(clickable_bounds="[0,0][20,20]"))
    out = root / "small.json"
    proc = rec.run("small_target_fails", ["tools/ci/a11y_audit.py", "audit", "--dump", str(small),
                                          "--colors", str(colors), "--out", str(out)], 1)
    codes = json.loads(out.read_text(encoding="utf-8")).get("violation_codes", [])
    rec.ok("touch_target_caught", "TOUCH_TARGET_TOO_SMALL" in codes)

    nolabel = _write(root / "nolabel.xml", _dump(text="", content_desc=""))
    out = root / "nolabel.json"
    rec.run("missing_label_fails", ["tools/ci/a11y_audit.py", "audit", "--dump", str(nolabel),
                                    "--colors", str(colors), "--out", str(out)], 1)
    codes = json.loads(out.read_text(encoding="utf-8")).get("violation_codes", [])
    rec.ok("label_caught", "MISSING_LABEL" in codes)

    out = root / "nocolors.json"
    rec.run("no_colors_ok", ["tools/ci/a11y_audit.py", "audit", "--dump", str(clean),
                             "--out", str(out)], 0)
    data = json.loads(out.read_text(encoding="utf-8"))
    rec.ok("no_colors_not_pass", data.get("contrast", {}).get("status") != "PASS"
           and data.get("ok") is False)

    low = _write(root / "low.json", json.dumps(
        {"pairs": [{"label": "faint", "fg": [180, 180, 180], "bg": [255, 255, 255]}]}))
    out = root / "low-out.json"
    rec.run("low_contrast_fails", ["tools/ci/a11y_audit.py", "audit", "--dump", str(clean),
                                   "--colors", str(low), "--out", str(out)], 1)
    codes = json.loads(out.read_text(encoding="utf-8")).get("violation_codes", [])
    rec.ok("contrast_caught", "CONTRAST_TOO_LOW" in codes)

    rec.run("bad_dump_exit2", ["tools/ci/a11y_audit.py", "audit",
                               "--dump", str(root / "missing.xml")], 2)

    # D0-057：真实设备导出物能被离线解析。批次 44 的 i-domain 语义树是
    # `uiautomator dump` 的产物，这里用同一工具复算，不接触设备。
    archived = (ROOT / "docs/research/evidence/2026-11-05-batch-44/i-domain"
                / "dump-smoke.xml")
    if archived.exists():
        out = root / "archived.json"
        proc = rec.run("archived_dump", ["tools/ci/a11y_audit.py", "audit",
                                         "--dump", str(archived), "--out", str(out)], 0)
        parsed = json.loads(out.read_text(encoding="utf-8"))
        rec.ok("archived_dump_nodes", parsed.get("nodes", 0) > 0,
               f"nodes={parsed.get('nodes')}")
    else:
        rec.ok("archived_dump_nodes", False, "dump_missing")


# ----------------------------------------------------------------- h_compliance


def group_h_compliance(rec: Recorder) -> None:
    proc = rec.run("all", ["tools/ci/h_compliance_gate.py", "--all"], 1)
    lines = [line for line in proc.stdout.splitlines()
             if line.startswith("H-") and len(line.split()) >= 2]
    rec.ok("all_checks_90", len(lines) == 90, f"lines={len(lines)}")
    states = {line.split()[0]: line.split()[1] for line in lines}
    rec.ok("all_has_failures", any(state == "FAIL" for state in states.values()))
    for check in ("H-018", "H-061", "H-062"):
        rec.ok(f"{check.lower()}_fail", states.get(check) == "FAIL")
    for check in ("H-052", "H-066"):
        rec.ok(f"{check.lower()}_pass", states.get(check) == "PASS")
    rec.run("only_052", ["tools/ci/h_compliance_gate.py", "--only", "H-052"], 0)
    rec.run("only_066", ["tools/ci/h_compliance_gate.py", "--only", "H-066"], 0)
    rec.run("only_018", ["tools/ci/h_compliance_gate.py", "--only", "H-018"], 1)


# ------------------------------------------------------------------------ docs


def _read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8", errors="replace")


def group_docs(rec: Recorder, section: str, tmp: Path) -> None:
    dod = _read("docs/acceptance/dod.md")
    runbook = _read("docs/acceptance/runbook.md")
    verdict_readme = _read("docs/acceptance/verdicts/README.md")
    if section == "dod":
        anchors = [
            ("main_path", "### 1.1 功能", "可复跑"),
            ("failure_path", "### 1.2 失败路径", "数据安全"),
            ("boundary", "### 1.3 边界", "杀进程"),
            ("evidence", "### 1.4 证据", "2 类独立证据"),
            ("independent_review", "### 1.5 独立复核", "reviewer"),
            ("adversarial", "### 1.6 对抗复核", "反例"),
            ("mapping", "## 6. 与台账的映射", "D0"),
        ]
        for name, heading, keyword in anchors:
            rec.ok(f"dod_{name}", heading in dod and keyword in dod)
    elif section == "verdicts":
        verdict_dir = ROOT / "docs" / "acceptance" / "verdicts"
        template = json.loads((verdict_dir / "_template.json").read_text(encoding="utf-8"))
        real = [path for path in verdict_dir.glob("*.json") if not path.name.startswith("_")]
        rec.ok("dir_and_readme", verdict_dir.is_dir() and "reviewer" in verdict_readme)
        rec.ok("template_fields", {"run_id", "ledger_sha256", "evidence_sha256",
                                   "reviewer", "authors"} <= set(template))
        rec.ok("no_real_verdict_json", len(real) == 0, f"json={len(real)}")
        rec.ok("no_verdict_schema", not list(verdict_dir.glob("*.schema.json")))
        rec.note("real_verdict_json", len(real))
        rec.note("verdict_schema_files", len(list(verdict_dir.glob("*.schema.json"))))
    elif section == "patch":
        apply_proc = subprocess.run(["git", "apply", "--check", "tools/ci/acceptance_ci.patch"],
                                    cwd=str(ROOT), capture_output=True, text=True)
        _write(tmp / "raw" / "patch-apply.txt",
               f"RC={apply_proc.returncode}\n{apply_proc.stdout}{apply_proc.stderr}")
        rec.rc("apply_check", 0, apply_proc.returncode)
        patch = _read("tools/ci/acceptance_ci.patch")
        rec.ok("report_step", "acceptance_report.py" in patch)
        rec.ok("lint_step", "acceptance_lint.py" in patch)
        rec.ok("trace_step", "trace_requirements.py" in patch)
        rec.ok("failure_blocks", "continue-on-error" not in patch)
        rec.ok("new_file_only", patch.count("new file mode") == 1)
        rec.ok("not_applied", not (ROOT / ".github" / "workflows" / "acceptance.yml").exists())
    elif section == "runbook":
        checks = {
            "per_domain": all(token in runbook for token in (
                "## 2.", "## 4.", "## 5.", "## 6.", "## 10.")),
            "verdict_criteria": "## 8. verdict 决策" in runbook,
            "archive_paths": "## 10. 证据哈希清单" in runbook,
            "device_binding": "APK sha" in runbook and "serial" in runbook,
            "residual_declaration": "## 5." in runbook and "## 9.1" in runbook,
            "blocked_exit": "## 9. BLOCKED 清单与出口" in runbook,
        }
        for name, condition in checks.items():
            rec.ok(f"runbook_{name}", condition)
    elif section == "protocol":
        checks = {
            "sign_fields": all(token in runbook for token in (
                '"id"', '"verdict"', '"run_id"', '"reviewer"', '"authors"',
                '"ledger_sha256"', '"evidence"', '"evidence_sha256"', '"adversarial"')),
            "sha_binding": "`ledger_sha256` 由" in runbook,
            "non_forgeable": "run_id" in verdict_readme and "ledger_sha256" in verdict_readme,
            "double_sign": "## 1.5 独立复核" in dod and "## 1.6 对抗复核" in dod,
            "archive_path": "verdicts/<ID>.json" in runbook,
        }
        for name, condition in checks.items():
            rec.ok(f"protocol_{name}", condition)
        # D0-081 要求“冲突时保留双方判词”。两份协议文件都没有这条规则；这里
        # 断言的是**观察到的缺失**，台账按失败登记，不把缺失写成通过。
        present = ("冲突" in dod or "冲突" in runbook or "冲突" in verdict_readme)
        rec.note("conflict_rule_present", present)
        rec.ok("protocol_conflict_rule_absent", present is False)
    elif section == "forgery":
        checks = {
            "self_review": "reviewer 不得给自己签字" in dod,
            "summary_evidence": "SUMMARY_AS_EVIDENCE" in dod,
            "screenshot_binding": "serial" in dod and "apk_sha256" in dod,
            "cmd_exit": "命令日志必须含可读 exit" in dod,
            "device_serial": "`device` JSON 必须有 `serial`" in runbook,
            "no_rewrite": "UNVERIFIED_AS_PASS" in dod,
        }
        for name, condition in checks.items():
            rec.ok(f"forgery_{name}", condition)
    elif section == "replay":
        for name, fixture in (("41", FAKE_GREEN_41), ("42", FAKE_GREEN_42)):
            rec.ok(f"fixture_{name}", fixture.exists())
            rec.run(f"report_{name}", ["tools/ci/acceptance_report.py", "--ledger", str(fixture),
                                       "--repo-root", str(ROOT), "--partial"], 1)
            rec.run(f"lint_{name}", ["tools/ci/acceptance_lint.py", "--ledger", str(fixture),
                                     "--repo-root", str(ROOT), "--partial"], 1)
        rec.ok("runbook_documents", "## 3. 历史假绿回放" in runbook
               and "fake_green_batch42.ledger.md" in runbook)
        rec.ok("thresholds_not_relaxed", "期望 1" in runbook
               and "CMD_LOG_SKIP_MARKER" in runbook)


# ------------------------------------------------------------------------ main


GROUPS = ("model", "init", "report", "lint", "trace", "metamorphic", "screenshot",
          "a11y", "h_compliance", "docs", "hash")


def main(argv: list[str] | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="D0 验收工具自验收驱动")
    parser.add_argument("--group", required=True, choices=GROUPS + ("all",))
    parser.add_argument("--section", choices=DOC_SECTIONS)
    parser.add_argument("--work", default=str(ROOT / "build" / "d0-self"))
    args = parser.parse_args(argv)

    if args.group == "docs" and not args.section:
        parser.error("--group docs 需要 --section")
    if args.group != "docs" and args.section:
        parser.error("--section 只用于 --group docs")

    work = Path(args.work)
    if not work.is_absolute():
        work = ROOT / work
    label = "docs-" + args.section if args.section else args.group
    rec = Recorder(label, work / label)
    print(f"GROUP {label}")
    print("COMMAND: python -I tools/ci/d0_self_acceptance.py --group "
          + args.group + (f" --section {args.section}" if args.section else ""))

    order = list(GROUPS) if args.group == "all" else [args.group]
    if "hash" in order:
        order.remove("hash")
        order.append("hash")
    for group in order:
        tmp = work / group
        tmp.mkdir(parents=True, exist_ok=True)
        if group == "model":
            group_model(rec)
        elif group == "init":
            group_init(rec, tmp)
        elif group == "report":
            group_report(rec, tmp)
        elif group == "lint":
            group_lint(rec, tmp)
        elif group == "trace":
            group_trace(rec, tmp)
        elif group == "metamorphic":
            group_metamorphic(rec, tmp)
        elif group == "screenshot":
            group_screenshot(rec, tmp)
        elif group == "a11y":
            group_a11y(rec, tmp)
        elif group == "h_compliance":
            group_h_compliance(rec)
        elif group == "hash":
            group_hash(rec, tmp)
        elif group == "docs":
            sections = DOC_SECTIONS if args.group == "all" else (args.section,)
            for section in sections:
                group_docs(rec, section, tmp / section)

    print(f"CHECKS={rec.checks} FAILURES={rec.failures}")
    print(f"EXIT={0 if rec.failures == 0 else 1}")
    return 0 if rec.failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
