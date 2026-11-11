#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 `am instrument` 的逐例输出录成可审计的 XML / 日志 / 设备绑定 JSON。

## 它消灭的具体失败

`am instrument -w` 在**用例失败时退出码仍是 0**（Android instrumentation 的
约定是“工具跑完了”，不是“用例通过”）。批次 42 §2.3 与批次 43 §4 都出现过
“JUnit 打印 `OK (n tests)` / 失败混在文本里”被人工读成绿的情况。本工具把
逐例状态码（1 开始 / 0 通过 / -1 错误 / -2 失败 / -3 忽略 / -4 假设失败）
解析成 JUnit XML，并在日志尾部只写**由解析结果决定**的 `EXIT=` 行：

- 有 error/failure/ignored/assumption → `EXIT=1`
- 解析不到任何用例或没有 `OK (n tests)` 收尾 → `EXIT=1`
- 全部通过且收尾是 `OK (n tests)` → `EXIT=0`

因此 `tools/ci/acceptance_report.py` 读到的是“用例级真相”，而不是
`am instrument` 的进程退出码。

## 用法

    python -I tools/ci/am_instrument_recorder.py \
      --serial emulator-5584 \
      --package com.tingyun.smartmistakebook.localfirst.test \
      --class com.tingyun.smartmistakebook.RootExperienceInstrumentedTest \
      --arg smb_allow_data_reset=true \
      --apk-sha "$APP_SHA" --test-apk-sha "$TEST_SHA" \
      --label r1-root --out-dir build/acceptance/a-domain

退出码：0 全部通过；1 有失败/错误/跳过/无法解析；2 参数或 adb 不可用。
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

STATUS_RE = re.compile(r"^INSTRUMENTATION_STATUS:\s*([A-Za-z_]+)=(.*)$")
STATUS_CODE_RE = re.compile(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$")
OK_RE = re.compile(r"^OK \((\d+) tests?\)\s*$", re.MULTILINE)
FAILURES_RE = re.compile(r"^FAILURES!!!", re.MULTILINE)

START = 1
PASS = 0
ERROR = -1
FAILURE = -2
IGNORED = -3
ASSUMPTION = -4


@dataclass
class Case:
    classname: str = ""
    name: str = ""
    stack: str = ""
    status: int | None = None

    def key(self) -> tuple[str, str]:
        return (self.classname, self.name)


@dataclass
class Parsed:
    cases: list[Case] = field(default_factory=list)
    final_ok: int | None = None
    saw_failures_banner: bool = False

    def counts(self) -> dict[str, int]:
        failures = sum(1 for c in self.cases if c.status == FAILURE)
        errors = sum(1 for c in self.cases if c.status == ERROR)
        skipped = sum(1 for c in self.cases if c.status in (IGNORED, ASSUMPTION))
        evaluated = sum(1 for c in self.cases if c.status in (PASS, FAILURE, ERROR))
        return {
            "tests": len(self.cases),
            "evaluated": evaluated,
            "failures": failures,
            "errors": errors,
            "skipped": skipped,
        }


def parse_instrumentation(text: str) -> Parsed:
    parsed = Parsed()
    current = Case()
    started: dict[tuple[str, str], Case] = {}
    # 一个用例可能先发 start(1)、再发结果。用 key 去重，结果覆盖 start。
    for raw in text.splitlines():
        line = raw.rstrip("\r")
        match = STATUS_RE.match(line)
        if match:
            key, value = match.group(1), match.group(2)
            if key == "class":
                current.classname = value.strip()
            elif key == "test":
                current.name = value.strip()
            elif key == "stack":
                current.stack += value + "\n"
            continue
        code_match = STATUS_CODE_RE.match(line)
        if not code_match:
            continue
        code = int(code_match.group(1))
        if code == START:
            current = Case(classname=current.classname, name=current.name)
            if current.key() not in started:
                parsed.cases.append(current)
                started[current.key()] = current
            continue
        if code in (PASS, ERROR, FAILURE, IGNORED, ASSUMPTION):
            key = (current.classname, current.name)
            if not key[1]:
                current = Case()
                continue
            case = started.get(key)
            if case is None:
                case = Case(classname=key[0], name=key[1])
                parsed.cases.append(case)
                started[key] = case
            case.status = code
            if current.stack:
                case.stack = current.stack
            current = Case()
    ok = OK_RE.search(text)
    if ok:
        parsed.final_ok = int(ok.group(1))
    parsed.saw_failures_banner = bool(FAILURES_RE.search(text))
    return parsed


def _xml_escape(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def to_junit_xml(parsed: Parsed, suite_name: str, serial: str) -> str:
    counts = parsed.counts()
    stamp = _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    out = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        (
            f'<testsuite name="{_xml_escape(suite_name)}" tests="{counts["tests"]}" '
            f'failures="{counts["failures"]}" errors="{counts["errors"]}" '
            f'skipped="{counts["skipped"]}" hostname="{_xml_escape(serial)}" '
            f'timestamp="{stamp}">'
        ),
    ]
    for case in parsed.cases:
        out.append(
            f'  <testcase classname="{_xml_escape(case.classname)}" '
            f'name="{_xml_escape(case.name)}" time="0">'
        )
        if case.status == FAILURE:
            out.append(
                f'    <failure message="instrumentation failure">'
                f"{_xml_escape(case.stack)}</failure>"
            )
        elif case.status == ERROR:
            out.append(
                f'    <error message="instrumentation error">'
                f"{_xml_escape(case.stack)}</error>"
            )
        elif case.status in (IGNORED, ASSUMPTION):
            out.append(f'    <skipped message="ignored or assumption failed"/>')
        out.append("  </testcase>")
    out.append("</testsuite>")
    return "\n".join(out) + "\n"


def verdict(parsed: Parsed, returncode: int) -> tuple[bool, str]:
    counts = parsed.counts()
    if returncode != 0:
        return False, f"adb/am 返回 {returncode}"
    if counts["tests"] == 0:
        return False, "未解析到任何用例"
    if counts["failures"] or counts["errors"]:
        return False, (
            f"failures={counts['failures']} errors={counts['errors']}"
        )
    if counts["skipped"]:
        return False, f"skipped={counts['skipped']}（跳过不计通过）"
    if parsed.saw_failures_banner:
        return False, "输出含 FAILURES!!! 收尾"
    if parsed.final_ok is None:
        return False, "缺少 OK (n tests) 收尾"
    if parsed.final_ok != counts["tests"]:
        return False, (
            f"OK({parsed.final_ok}) 与解析用例数 {counts['tests']} 不一致"
        )
    return True, "all passed"


def _find_adb(explicit: str | None) -> str:
    if explicit and explicit != "adb":
        return explicit
    found = shutil.which("adb")
    if found:
        return found
    for candidate in (
        r"C:\Android\Sdk\platform-tools\adb.exe",
        "/c/Android/Sdk/platform-tools/adb.exe",
    ):
        if Path(candidate).exists():
            return candidate
    return explicit or "adb"


def _boot_id(adb: str, serial: str) -> str:
    try:
        out = subprocess.run(
            [adb, "-s", serial, "shell", "cat", "/proc/sys/kernel/random/boot_id"],
            capture_output=True, text=True, timeout=20,
        )
        return out.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return ""


def run(args: argparse.Namespace) -> int:
    adb = _find_adb(args.adb)
    cmd = [
        adb, "-s", args.serial, "shell", "am", "instrument", "-w", "-r",
    ]
    for raw in args.arg:
        key, _, value = raw.partition("=")
        cmd.extend(["-e", key, value])
    if args.class_filter:
        cmd.extend(["-e", "class", args.class_filter])
    cmd.append(f"{args.package}/{args.runner}")

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    label = args.label or "instrument"
    try:
        completed = subprocess.run(
            cmd, capture_output=True, text=True, encoding="utf-8",
            errors="replace", timeout=args.timeout,
        )
        output = (completed.stdout or "") + (completed.stderr or "")
        returncode = completed.returncode
    except subprocess.TimeoutExpired as exc:
        output = (exc.stdout or "") if isinstance(exc.stdout, str) else ""
        output += f"\nTIMEOUT after {args.timeout}s\n"
        returncode = 124

    parsed = parse_instrumentation(output)
    passed, reason = verdict(parsed, returncode)
    counts = parsed.counts()

    # cmd 证据日志只放原始输出与真实判定：不放 "skipped=" 这类派生统计，
    # 否则 acceptance_report 的跳过标记会把“0 skipped”误读成“有跳过”。
    (out_dir / f"{label}.log").write_text(
        output.rstrip()
        + "\n\n"
        + f"VERDICT {'PASS' if passed else 'FAIL'}: {reason}\n"
        + f"EXIT={0 if passed else 1}\n",
        encoding="utf-8",
        newline="\n",
    )
    (out_dir / f"{label}.summary.txt").write_text(
        f"tests={counts['tests']} evaluated={counts['evaluated']} "
        f"failures={counts['failures']} errors={counts['errors']} "
        f"ignored_or_assumed={counts['skipped']}\n"
        f"VERDICT {'PASS' if passed else 'FAIL'}: {reason}\n",
        encoding="utf-8",
        newline="\n",
    )
    (out_dir / f"{label}.xml").write_text(
        to_junit_xml(parsed, label, args.serial), encoding="utf-8", newline="\n"
    )
    device = {
        "serial": args.serial,
        "boot_id": _boot_id(adb, args.serial),
        "apk_sha256": args.apk_sha,
        "test_apk_sha256": args.test_apk_sha,
        "package": args.package,
        "runner": args.runner,
        "class_filter": args.class_filter,
        "counts": counts,
        "verdict": "PASS" if passed else "FAIL",
        "reason": reason,
        "recorded_at": _dt.datetime.now(_dt.timezone.utc)
        .strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    (out_dir / f"{label}.device.json").write_text(
        json.dumps(device, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(json.dumps(device, ensure_ascii=False, indent=2))
    return 0 if passed else 1


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="录制 am instrument 逐例结果")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--runner", default="androidx.test.runner.AndroidJUnitRunner")
    parser.add_argument("--class", dest="class_filter", default="")
    parser.add_argument("--arg", action="append", default=[])
    parser.add_argument("--apk-sha", default="")
    parser.add_argument("--test-apk-sha", default="")
    parser.add_argument("--label", required=True)
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--timeout", type=int, default=900)
    args = parser.parse_args(argv)
    try:
        return run(args)
    except OSError as exc:
        print(f"ADB_ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
