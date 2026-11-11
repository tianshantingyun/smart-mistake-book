# -*- coding: utf-8 -*-
"""am_instrument_recorder 的解析/判定单测（批次 44 A 域证据工具）。

消灭的具体失败：`am instrument` 在用例失败时仍返回 0，录制器若只看进程
退出码就会把红写成绿。这里用三段真实形态的 `-r` 输出锁定：
通过 / 失败 / 假设跳过 都必须被解析成不同 verdict。
"""

from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "ci"))

import am_instrument_recorder as R  # noqa: E402


PASS_TEXT = """\
INSTRUMENTATION_STATUS: class=com.example.SampleTest
INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=1
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=onePasses
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.example.SampleTest
INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=onePasses
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_RESULT: stream=

Time: 0.01

OK (1 test)
"""

FAIL_TEXT = """\
INSTRUMENTATION_STATUS: class=com.example.SampleTest
INSTRUMENTATION_STATUS: test=oneFails
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.example.SampleTest
INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: boom
INSTRUMENTATION_STATUS: test=oneFails
INSTRUMENTATION_STATUS_CODE: -2
INSTRUMENTATION_RESULT: stream=

Time: 0.01

FAILURES!!!
Tests run: 1,  Failures: 1
"""

ASSUMPTION_TEXT = """\
INSTRUMENTATION_STATUS: class=com.example.SampleTest
INSTRUMENTATION_STATUS: test=oneAssumes
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.example.SampleTest
INSTRUMENTATION_STATUS: stack=org.junit.AssumptionViolatedException: need device
INSTRUMENTATION_STATUS: test=oneAssumes
INSTRUMENTATION_STATUS_CODE: -4
INSTRUMENTATION_RESULT: stream=

Time: 0.01

OK (1 test)
"""

MIXED_TEXT = """\
INSTRUMENTATION_STATUS: class=com.example.SampleTest
INSTRUMENTATION_STATUS: test=a
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.example.SampleTest
INSTRUMENTATION_STATUS: test=a
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.example.SampleTest
INSTRUMENTATION_STATUS: test=b
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.example.SampleTest
INSTRUMENTATION_STATUS: test=b
INSTRUMENTATION_STATUS_CODE: -1
INSTRUMENTATION_RESULT: stream=

Time: 0.01

FAILURES!!!
Tests run: 2,  Failures: 0, Errors: 1
"""


def test_pass_is_green():
    parsed = R.parse_instrumentation(PASS_TEXT)
    assert parsed.final_ok == 1
    assert parsed.counts() == {
        "tests": 1, "evaluated": 1, "failures": 0, "errors": 0, "skipped": 0,
    }
    passed, reason = R.verdict(parsed, 0)
    assert passed, reason


def test_failure_is_red_even_when_process_exit_is_zero():
    parsed = R.parse_instrumentation(FAIL_TEXT)
    assert parsed.counts()["failures"] == 1
    passed, _ = R.verdict(parsed, 0)
    assert not passed


def test_assumption_skip_is_not_a_pass():
    parsed = R.parse_instrumentation(ASSUMPTION_TEXT)
    assert parsed.counts()["skipped"] == 1
    passed, _ = R.verdict(parsed, 0)
    assert not passed


def test_error_is_red():
    parsed = R.parse_instrumentation(MIXED_TEXT)
    assert parsed.counts()["tests"] == 2
    assert parsed.counts()["errors"] == 1
    passed, _ = R.verdict(parsed, 0)
    assert not passed


def test_xml_declares_the_parsed_counts():
    xml = R.to_junit_xml(R.parse_instrumentation(MIXED_TEXT), "sample", "emulator-1")
    assert xml.startswith('<?xml version="1.0" encoding="UTF-8"?>')
    assert 'tests="2"' in xml
    assert 'errors="1"' in xml
    assert 'hostname="emulator-1"' in xml
    assert '<error message="instrumentation error">' in xml
