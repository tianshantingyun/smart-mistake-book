#!/usr/bin/env python3
"""Render docs/status.md from real build outputs.

Values that have no measurement source are rendered as NOT_MEASURED,
never as fabricated numbers (acceptance-audit 4.2 requirement).

AUD-15（2026-10-15）再加两条口径：

- **证据必须属于这一版源码**。`build/` 里留着上一轮编译出来的 `.class`/`.jar` 和 lint XML
  是常态：只看"文件存在"就报 OK，等于把上一版源码的产物记成这一版源码的证据。编译证据
  要求产物晚于本模块源码，lint 报告同样；证据不成立一律 NOT_MEASURED。
- **run 上下文必须绑到一次真实运行**。`COMMIT_SHA`/`BRANCH` 由 CI 传入并与 git HEAD 核对，
  拿不到就显式写 `unknown`，且 Overall 不判 PASS。
"""

from __future__ import annotations

import glob
import hashlib
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TEMPLATE = REPO / ".github" / "workflows" / "status-template.md"
OUTPUT = REPO / "docs" / "status.md"

NOT_MEASURED = "NOT_MEASURED"

# KD-40（2026-10-16 登记）：页面顶部的 sha 是**生成这一刻**读到的 HEAD，不是"本页所描述那一版
# 代码的提交"——本批代码还没提交时本地生成，它指向的就是本批之前的提交（登记时实测指向
# `a15aebb7`）。页面不把这一点说破，读者（包括下一批的自己）会把 sha 读成"这段描述对应的提交"。
# 所以这句话与 sha/branch 印在同一处，且**不按 BOUND/UNBOUND 分支**：头部两种情况都会印出
# sha/branch（UNBOUND 时是 declared sha 或 `unknown`），定义必须与绑定结果无关。
RUN_CONTEXT_NOTE = "绑定时刻 = 生成时刻，非所描述代码的提交"

MODULES = [
    ":core:database",
    ":core:data",
    ":core:domain",
    ":feature:library",
    ":feature:capture",
    ":feature:tutor",
]

# 与 `.github/workflows/android-check.yml` 的 Lint 步骤同一份清单（那里的原话：一个模块
# 没被点名就永远不会被检查）。`lint<Flavor>Debug` 只匹配 :app——它是唯一有 product flavor
# 的模块——所以库模块的 NewApi/RestrictedApi 在过去无人看见（KD-11/KD-12 登记过），状态页
# 也从来没显示过它们。两处要一起改。
LINT_LIBRARY_MODULES = [
    ":core:data",
    ":core:database",
    ":core:export",
    ":core:ui",
    ":feature:capture",
    ":feature:library",
    ":feature:profile",
    ":feature:review",
    ":feature:tutor",
]


def gradle_dir(module: str) -> Path:
    return REPO / module.lstrip(":").replace(":", "/")


def test_results(module: str) -> list[Path]:
    base = gradle_dir(module) / "build" / "test-results"
    if not base.exists():
        return []
    return sorted(base.rglob("TEST-*.xml"))


def summarize_tests(module: str) -> tuple[int, int, int]:
    total = passed = failures = 0
    for xml in test_results(module):
        try:
            root = ET.parse(xml).getroot()
        except ET.ParseError:
            continue
        total += int(root.get("tests", 0))
        failures += int(root.get("failures", 0)) + int(root.get("errors", 0))
    passed = total - failures
    return total, max(passed, 0), failures


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def apk_outputs() -> dict[str, Path]:
    results: dict[str, Path] = {}
    pattern = gradle_dir(":app") / "build" / "outputs" / "apk"
    for path in sorted(pattern.rglob("*.apk")):
        name = path.name
        flavor = "localFirst" if "localfirst" in str(path).lower() or "local-first" in name else (
            "strictOffline" if "offline" in str(path).lower() or "strict-offline" in name else "unknown"
        )
        kind = "debug" if "debug" in name else ("release" if "release" in name else "other")
        results[f"{flavor}-{kind}"] = path
    return results


def newest_stamp(root: Path, pattern: str) -> float | None:
    """root 下匹配 pattern 的文件里最新的 mtime；没有则 None。"""
    if not root.exists():
        return None
    newest: float | None = None
    for path in root.rglob(pattern):
        if not path.is_file():
            continue
        stamp = path.stat().st_mtime
        if newest is None or stamp > newest:
            newest = stamp
    return newest


def source_stamp(module: str) -> float | None:
    """本模块源码（含测试源）的最新 mtime，作为"这一版源码"的时间戳。"""
    return newest_stamp(gradle_dir(module) / "src", "*")


def format_stamp(stamp: float | None) -> str:
    return datetime.fromtimestamp(stamp).strftime("%m-%d %H:%M") if stamp else "?"


def staleness_reason(artifact: float | None, module: str, kind: str) -> str:
    """空串表示证据可用；否则给出为什么不能用。

    产物存在但**早于源码**时，它是上一版源码的产物——不能报成本次的证据（AUD-15：
    旧 classes/APK/XML 不能报本次 OK）。取不到源码时同样不认。
    """
    if artifact is None:
        return f"无{kind}"
    sources = source_stamp(module)
    if sources is None:
        return f"无源码可对照（{kind}无法归属到这一版）"
    if artifact < sources:
        return f"{kind}早于源码（{format_stamp(artifact)} < 源码 {format_stamp(sources)}）"
    return ""


def lint_counts(module: str, report: Path) -> tuple[tuple[int, int] | None, str]:
    """(errors, warnings)，或 (None, 原因)。报告缺/过期/解析失败都算没有证据。"""
    if not report.exists():
        return None, "无 lint 报告"
    reason = staleness_reason(report.stat().st_mtime, module, "lint 报告")
    if reason:
        return None, reason
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        return None, "lint 报告解析失败"
    errors = warnings = 0
    for issue in root.iter("issue"):
        severity = issue.get("severity", "")
        if severity == "Error":
            errors += 1
        elif severity == "Warning":
            warnings += 1
    return (errors, warnings), ""


def lint_summary() -> tuple[dict[str, str], bool | None, list[str]]:
    """app 两个变体 + 全部 Android 库模块的 lint 证据 →（模板值, 三值判定, 无证据原因）。

    三值判定：True = 每份报告都在且 0 error；False = 有报告但红了；None = 有模块没有可用
    报告（"没测"绝不等于"通过"）。库模块逐模块列出，让"某个库的 lint 结果不可见"这件事
    在页面上看得出来，而不是被两条 app 行盖住。
    """
    values: dict[str, str] = {}
    notes: list[str] = []
    errors = warnings = 0

    for variant, key in (("localFirstDebug", "LINT_LOCAL_FIRST"), ("strictOfflineDebug", "LINT_STRICT_OFFLINE")):
        report = gradle_dir(":app") / "build" / "reports" / f"lint-results-{variant}.xml"
        counts, reason = lint_counts(":app", report)
        if counts is None:
            values[f"{key}_STATUS"] = NOT_MEASURED
            values[f"{key}_ERRORS"] = NOT_MEASURED
            values[f"{key}_WARNINGS"] = NOT_MEASURED
            notes.append(f"`{variant}`：{reason}")
            continue
        variant_errors, variant_warnings = counts
        errors += variant_errors
        warnings += variant_warnings
        values[f"{key}_STATUS"] = "OK" if variant_errors == 0 else "FAIL"
        values[f"{key}_ERRORS"] = str(variant_errors)
        values[f"{key}_WARNINGS"] = str(variant_warnings)

    rows: list[str] = []
    for module in LINT_LIBRARY_MODULES:
        report = gradle_dir(module) / "build" / "reports" / "lint-results-debug.xml"
        counts, reason = lint_counts(module, report)
        if counts is None:
            rows.append(f"| `{module}` | {NOT_MEASURED} | {NOT_MEASURED} | {NOT_MEASURED} |")
            notes.append(f"`{module}`：{reason}")
            continue
        module_errors, module_warnings = counts
        errors += module_errors
        warnings += module_warnings
        rows.append(f"| `{module}` | {'OK' if module_errors == 0 else 'FAIL'} | {module_errors} | {module_warnings} |")
    values["LINT_LIBRARY_ROWS"] = "\n".join(rows)

    if notes:
        ok: bool | None = None
        values["LINT_LIBRARY_STATUS"] = NOT_MEASURED
        values["LINT_LIBRARY_ERRORS"] = NOT_MEASURED
        values["LINT_LIBRARY_WARNINGS"] = NOT_MEASURED
        values["LINT_LIBRARY_NOTE"] = (
            "以下位置没有可用 lint 证据（记 NOT_MEASURED，不能当作通过）：" + "；".join(notes)
        )
    else:
        ok = errors == 0
        values["LINT_LIBRARY_STATUS"] = "OK" if ok else "FAIL"
        values["LINT_LIBRARY_ERRORS"] = str(errors)
        values["LINT_LIBRARY_WARNINGS"] = str(warnings)
        values["LINT_LIBRARY_NOTE"] = (
            f"{len(LINT_LIBRARY_MODULES) + 2} 份报告（app 2 变体 + {len(LINT_LIBRARY_MODULES)} 个 Android 库模块）"
            f"都在且不早于各自源码；错误合计 {errors}。"
        )
    return values, ok, notes


def compile_status(module: str) -> tuple[str, str]:
    """(Status, 原因)。只有"产物晚于本模块源码"才算编译通过。

    见模块头 AUD-15 一条：留着上一轮的 `.class`/`.jar` 不是证据。
    """
    base = gradle_dir(module) / "build"
    stamps = [
        stamp
        for stamp in (
            newest_stamp(base / "classes", "*.class"),
            # AGP 模块把编译结果打成 intermediates 里的 JAR，而不是 build/classes 下的散 class。
            newest_stamp(base / "intermediates" / "compile_library_classes_jar", "*.jar"),
            newest_stamp(base / "intermediates" / "compile_app_classes_jar", "*.jar"),
        )
        if stamp is not None
    ]
    if not stamps:
        return NOT_MEASURED, "无编译产物"
    reason = staleness_reason(max(stamps), module, "类产物")
    return (NOT_MEASURED, reason) if reason else ("OK", "")


def app_variant_evidence(variant: str) -> tuple[str, str]:
    jar_dir = gradle_dir(":app") / "build" / "intermediates" / "compile_app_classes_jar" / variant
    reason = staleness_reason(newest_stamp(jar_dir, "*.jar"), ":app", "类产物")
    return (NOT_MEASURED, reason) if reason else ("OK", "")


def coverage_reports(module: str) -> list[Path]:
    """Candidate coverage XMLs: Kover for JVM modules, the AGP+Jacoco report for
    Android libraries (Kover 0.9.1 cannot see AGP 9's built-in-Kotlin variants
    — see KD-5). The first one with real counters wins."""
    gradle = gradle_dir(module)
    return [
        gradle / "build" / "reports" / "kover" / "report.xml",
        gradle / "build" / "reports" / "coverage" / "unit-test.xml",
    ]


def kover_coverage(module: str) -> tuple[str, str]:
    """Line/branch coverage percentages from the module's coverage XML."""
    counters: dict[str, tuple[int, int]] = {}
    for report in coverage_reports(module):
        if not report.exists():
            continue
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        counters = {}
        for counter in root.iter("counter"):
            kind = counter.get("type", "")
            if kind in ("LINE", "BRANCH"):
                missed = int(counter.get("missed", 0))
                covered = int(counter.get("covered", 0))
                counters[kind] = (missed, covered)
        # A report with zero counters means nothing was instrumented; try the
        # next candidate instead of rendering a misleading "0.0%".
        line = counters.get("LINE", (0, 0))
        if counters and line[0] + line[1] > 0:
            break
    if not counters:
        return NOT_MEASURED, NOT_MEASURED
    line = counters.get("LINE", (0, 0))
    if line[0] + line[1] == 0:
        return NOT_MEASURED, NOT_MEASURED

    def percentage(kind: str) -> str:
        missed, covered = counters[kind]
        total = missed + covered
        return f"{covered / total * 100:.1f}%" if total else NOT_MEASURED

    return percentage("LINE"), percentage("BRANCH")


def kb_gate_rows() -> tuple[str, bool, int, int]:
    """Run the knowledge-base content gates (kb_build.gate) on the bundled pack.

    Returns (template table rows, all-green, ok count, total count). 门数**从 gate.evaluate()
    现算**、不在调用处写死：它从 22 长到 23 的那一刻，四处写死的 "22" 就同时变成了假话
    （2026-09-28 实测：真跑会打印 `23/22`）。A crash while evaluating
    is reported as red, never as unmeasured: F-05's lesson is that a status
    report which cannot say what happened must not say "PASS".
    """
    try:
        sys.path.insert(0, str(REPO / "tools"))
        from kb_build import gate
        metrics = gate.evaluate()
    except Exception as exc:  # noqa: BLE001 - crash = red, with the reason visible
        return f"| gate evaluation crashed | FAIL | {exc} |", False, 0, 0
    rows = []
    ok_count = 0
    for metric in metrics:
        if metric.ok:
            ok_count += 1
        rows.append(
            f"| `{metric.key}` | {metric.title} | {'OK' if metric.ok else 'FAIL'} | {metric.value} |"
        )
    return "\n".join(rows), ok_count == len(metrics), ok_count, len(metrics)


def kb_authority_shas() -> str:
    """权威表（kb_build.tables 加载的那几张）逐表 sha256 前 12 位，一行；并标注保护级别。

    存在的理由（2026-09-28 实测）：同一份 `chapter_map.csv`，本地读到的是**工作树里那一版**
    （绿），CI 读到的是**已提交那一版**（红）——"门全绿"这个数字本身不携带"对应哪版权威表"
    的信息，而权威表恰恰是最常被并发改动的一层。表不在位时写 `missing`，不当作通过。

    保护级别（2026-10-02 标注，回应"6 张表并列展示、其中 2 张没有门"的审计结论；
    2026-10-12 批9 F9 落门后更新）：
    - `门保护`：被 gate.py / check_pack_contract 读取，与包分叉即红；
      `material_bindings` 自批9 d2 起入此列——欠账三桶（节点缺/空/欠账）经
      `check_pack_contract.material_binding_gate` 判红/绿（run_kb_checks 与 promote 同读）；
    - `报告型`：目前**没有强制力**，只在 run_kb_checks 的 INFO 节与 check_pack_contract
      的报告型计数里报数——列在这里是记账，不是背书（现仅 boundary_map）。
    """
    try:
        sys.path.insert(0, str(REPO / "tools"))
        from kb_build import tables as tables_mod
    except Exception as exc:  # noqa: BLE001
        return f"权威表 sha256：NOT_MEASURED（{type(exc).__name__}）"
    guarded = "门保护"
    reported = "报告型"
    names = (
        (tables_mod.CHAPTER_MAP, guarded),
        (tables_mod.CHAPTER_BY_SOURCE, guarded),
        (tables_mod.ALIAS_MAP, guarded),
        (tables_mod.BOUNDARY_MAP, reported),
        (tables_mod.PREREQ_MAP, guarded),
        (tables_mod.MATERIAL_BINDINGS, guarded),
    )
    parts = []
    for name, level in names:
        path = tables_mod.TABLES_DIR / name
        if not path.is_file():
            parts.append(f"`{name}`=missing（{level}）")
            continue
        parts.append(f"`{name}`=`{hashlib.sha256(path.read_bytes()).hexdigest()[:12]}`（{level}）")
    return "权威表 sha256（前 12 位）：" + " · ".join(parts)


def tools_test_summary() -> tuple[str, str, str, bool]:
    """Run the Python tools test suite (tools/tests) and summarize total/passed/failed.

    Runs in a subprocess so test code cannot mutate this process' globals
    (several tests rebind tables.TABLES_DIR). A crash or a hung run is red,
    never "not measured".
    """
    env = dict(os.environ)
    env["PYTHONPATH"] = "tools" + os.pathsep + env.get("PYTHONPATH", "")
    try:
        proc = subprocess.run(
            [sys.executable, "-m", "unittest", "discover", "-s", "tools/tests"],
            cwd=REPO, env=env, capture_output=True, text=True, timeout=900,
        )
    except Exception as exc:  # noqa: BLE001
        return f"RUN_FAILED ({type(exc).__name__})", NOT_MEASURED, NOT_MEASURED, False
    stderr = proc.stderr
    total_match = re.search(r"Ran (\d+) tests?", stderr)
    if total_match is None:
        tail = stderr.strip().splitlines()
        detail = tail[-1][:160] if tail else f"exit={proc.returncode}"
        return f"RUN_FAILED ({detail})", NOT_MEASURED, NOT_MEASURED, False
    total = int(total_match.group(1))
    failed = 0
    for marker in ("failures=", "errors="):
        match = re.search(rf"{marker}(\d+)", stderr)
        if match:
            failed += int(match.group(1))
    if proc.returncode != 0:
        failed = max(failed, 1)
    return str(total), str(total - failed), str(failed), failed == 0


def benchmark_metrics_text() -> str:
    """Verbatim content of the retrieval benchmark written by
    :core:data's FourSubjectRetrievalBenchmarkTest (module-relative
    build/benchmark-metrics.txt). Staleness detection is R3's job; here the
    report only refuses to pretend the measurement does not exist."""
    path = gradle_dir(":core:data") / "build" / "benchmark-metrics.txt"
    if not path.exists():
        return NOT_MEASURED
    try:
        return path.read_text(encoding="utf-8").strip() or NOT_MEASURED
    except OSError:
        return NOT_MEASURED


def git_output(*args: str) -> str | None:
    """git 命令的 stdout；无 git、非零退出、超时都只是"拿不到"，不抛。"""
    try:
        proc = subprocess.run(
            ["git", *args], cwd=REPO, capture_output=True, text=True, timeout=60,
        )
    except Exception:  # noqa: BLE001
        return None
    if proc.returncode != 0:
        return None
    return proc.stdout.strip() or None


def run_context() -> tuple[dict[str, str], bool]:
    """(模板值, 上下文是否绑定)。

    过去这里是写死的占位符（sha=`local`、branch=`unknown`）：一张不知道对应哪个提交的报表，
    与"门全绿这个数字本身不携带对应哪版权威表"（kb_authority_shas 登记的那族）是同一个毛病。
    现在：CI 传入的 COMMIT_SHA/BRANCH 优先，并与 git HEAD 核对；读不到就显式写 `unknown`，
    绑定失败会经 [run_context_bound] 让 Overall 不判 PASS。

    KD-40（2026-10-16 登记，本函数即修复落点）：绑定成立只说明"sha = 生成这一刻的 HEAD"，
    不等于"sha = 本页所描述那一版代码的提交"——代码还没提交时本地生成（或 CI commit-back 之前
    生成），页面顶部的 sha 就是**上一版**的提交。所以 [RUN_CONTEXT_NOTE] 与 sha/branch 一起
    进页面头部；它不随绑定结果变化（见常量处的说明），页面上因此不存在"只有 sha、不解释"的读法。
    """
    declared_sha = os.environ.get("COMMIT_SHA", "").strip()
    declared_branch = os.environ.get("BRANCH", "").strip()
    head_sha = git_output("rev-parse", "HEAD")
    head_branch = git_output("rev-parse", "--abbrev-ref", "HEAD")

    notes: list[str] = []
    sha = declared_sha or head_sha or ""
    if not sha:
        notes.append("sha 拿不到（无 COMMIT_SHA，也读不到 git HEAD）")
    elif declared_sha and head_sha and declared_sha != head_sha:
        notes.append(f"COMMIT_SHA={declared_sha[:12]} 与 git HEAD={head_sha[:12]} 不一致")
    branch = declared_branch or head_branch or ""
    if not branch:
        notes.append("branch 拿不到（无 BRANCH，也读不到 git HEAD 的分支）")

    if declared_sha and head_sha:
        sha_source = "CI 变量，与 git HEAD 一致"
    elif declared_sha:
        sha_source = "CI 变量（无 git 可核对）"
    else:
        sha_source = "git HEAD"
    triggered_by = os.environ.get("TRIGGERED_BY", "").strip() or "unknown"

    bound = not notes
    values = {
        "COMMIT_SHA": sha or "unknown",
        "BRANCH": branch or "unknown",
        "TRIGGERED_BY": triggered_by,
        "RUN_CONTEXT": (
            f"BOUND · sha `{sha[:12]}`（{sha_source}） · branch `{branch}` · 触发 {triggered_by}"
            if bound
            else "UNBOUND（" + "；".join(notes) + "）—— Overall 不判 PASS"
        ),
        "RUN_CONTEXT_NOTE": RUN_CONTEXT_NOTE,
    }
    return values, bound


def job_flag() -> bool | None:
    """CI 用 `JOB_STATUS: ${{ job.status }}` 显式传入；本地直接跑就没有这个变量。

    原先是 `os.environ.get("JOB_STATUS", "success")`——缺省 "success" 让"没测到"变成了
    "测过且绿"，本地运行或环境变量意外丢失时 Overall 会被抬成 PASS。没有就是 None（没测）。
    """
    status = os.environ.get("JOB_STATUS", "").strip()
    return None if not status else status.lower() == "success"


def overall_status(flags: list[bool | None]) -> str:
    """三值汇总：有红即 FAIL；没有红但有"没测"即 NOT_MEASURED；全绿且全部测到才 PASS。

    "拿不到"绝不当成"通过"：run 上下文未绑定（False）会直接判 FAIL，缺证据（None）会把
    PASS 挡成 NOT_MEASURED。
    """
    if any(flag is False for flag in flags):
        return "FAIL"
    if any(flag is None for flag in flags):
        return NOT_MEASURED
    return "PASS"


def main() -> int:
    if not TEMPLATE.exists():
        print(f"template missing: {TEMPLATE}", file=sys.stderr)
        return 1
    text = TEMPLATE.read_text(encoding="utf-8")

    values: dict[str, str] = {}

    context_values, run_context_bound = run_context()
    values.update(context_values)
    values["BUILD_DATE"] = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    values["GENERATION_TIMESTAMP"] = datetime.now(timezone.utc).isoformat()

    compile_notes: list[str] = []
    for module in MODULES:
        key = module.lstrip(":").replace(":", "_").upper()
        total, passed, failed = summarize_tests(module)
        if total > 0:
            values[f"{key}_TEST_TOTAL"] = str(total)
            values[f"{key}_TEST_PASSED"] = str(passed)
            values[f"{key}_TEST_FAILED"] = str(failed)
            values[f"{key}_TEST_DURATION"] = "-"
        else:
            values[f"{key}_TEST_TOTAL"] = NOT_MEASURED
            values[f"{key}_TEST_PASSED"] = NOT_MEASURED
            values[f"{key}_TEST_FAILED"] = NOT_MEASURED
            values[f"{key}_TEST_DURATION"] = NOT_MEASURED
        status, reason = compile_status(module)
        values[f"{key}_STATUS"] = status
        if reason:
            compile_notes.append(f"`{module}`：{reason}")
        values[f"{key}_DURATION"] = "-"

    for variant, key in (("localFirstDebug", "APP_LOCAL_FIRST"), ("strictOfflineDebug", "APP_STRICT_OFFLINE")):
        status, reason = app_variant_evidence(variant)
        values[f"{key}_STATUS"] = status
        if reason:
            compile_notes.append(f"`:app:{variant}`：{reason}")
        values[f"{key}_DURATION"] = "-"

    # 编译证据的判定：全新鲜 → True；有位置无证据 → None（没测不等于通过）。这个脚本看不到
    # "编译失败"本身——失败留下的就是无产物/旧产物，所以它只会降级到 NOT_MEASURED，不会变成红。
    compile_ok: bool | None = True if not compile_notes else None
    values["COMPILE_EVIDENCE_NOTE"] = (
        f"编译证据（要求产物晚于本模块源码）：{len(MODULES) + 2} 个模块/变体全部新鲜。"
        if not compile_notes
        else "编译证据（要求产物晚于本模块源码）：以下位置记 NOT_MEASURED —— " + "；".join(compile_notes)
    )

    # Lint：app 两个变体 + 全部 Android 库模块（原先只读 app 变体，库模块的 NewApi 在页面上
    # 完全不可见，见 LINT_LIBRARY_MODULES 的说明）。
    lint_values, lint_ok, _lint_notes = lint_summary()
    values.update(lint_values)

    # Coverage: only modules with a coverage XML render values; the rest stay
    # NOT_MEASURED. JVM modules use Kover, Android libraries the AGP+Jacoco
    # report (see coverage_report).
    for module, prefix in (
        (":core:domain", "CORE_DOMAIN"),
        (":core:data", "CORE_DATA"),
        (":core:database", "CORE_DATABASE"),
    ):
        line_pct, branch_pct = kover_coverage(module)
        values[f"{prefix}_LINE_COVERAGE"] = line_pct
        values[f"{prefix}_BRANCH_COVERAGE"] = branch_pct

    apks = apk_outputs()
    release_rows = {
        "RELEASE_LOCAL_FIRST_STATUS": NOT_MEASURED,
        "RELEASE_LOCAL_FIRST_APK_SIZE": NOT_MEASURED,
        "RELEASE_LOCAL_FIRST_AAB_SIZE": NOT_MEASURED,
        "RELEASE_STRICT_OFFLINE_STATUS": NOT_MEASURED,
        "RELEASE_STRICT_OFFLINE_APK_SIZE": NOT_MEASURED,
        "RELEASE_STRICT_OFFLINE_AAB_SIZE": NOT_MEASURED,
        "LOCAL_FIRST_DEBUG_APK_SHA": NOT_MEASURED,
        "LOCAL_FIRST_RELEASE_APK_SHA": NOT_MEASURED,
        "STRICT_OFFLINE_DEBUG_APK_SHA": NOT_MEASURED,
        "STRICT_OFFLINE_RELEASE_APK_SHA": NOT_MEASURED,
    }
    aab_pattern = gradle_dir(":app") / "build" / "outputs" / "bundle"
    aabs = {p.name: p.stat().st_size for p in aab_pattern.rglob("*.aab")}
    # tag is "<flavor>-<kind>"; upper() does not split camelCase, so the
    # display key must be mapped explicitly to match the template
    # (LOCAL_FIRST_DEBUG_..., not LOCALFIRST_DEBUG_...).
    display_flavor = {
        "localFirst": "LOCAL_FIRST",
        "strictOffline": "STRICT_OFFLINE",
        "unknown": "UNKNOWN",
    }
    for tag, path in apks.items():
        flavor, _, kind = tag.partition("-")
        flavor_key = display_flavor.get(flavor, flavor.upper())
        release_rows[f"{flavor_key}_{kind.upper()}_APK_SHA"] = sha256(path)
        size_mb = path.stat().st_size / (1024 * 1024)
        if kind == "release":
            release_rows[f"RELEASE_{flavor_key}_STATUS"] = "OK"
            release_rows[f"RELEASE_{flavor_key}_APK_SIZE"] = f"{size_mb:.1f} MB"
    if aabs:
        first_aab = next(iter(aabs.values()))
        release_rows.setdefault("AAB_SIZE_PLACEHOLDER", f"{first_aab / (1024 * 1024):.1f} MB")
    values.update(release_rows)

    # Knowledge base: the content gates run here (kb_build.gate, count computed not hardcoded),
    # the Python tools suite is summarized here, and the retrieval benchmark file written
    # by :core:data's JVM test is embedded verbatim. Overall must not be PASS
    # unless all of these are measured AND green — that is the F-05 root fix:
    # a PASS that only tracked the build job's status could never reflect the
    # state of the knowledge base at all.
    gates_rows, gates_ok, gates_ok_count, gates_total = kb_gate_rows()
    tools_total, tools_passed, tools_failed, tools_ok = tools_test_summary()
    values["KB_GATE_ROWS"] = gates_rows
    values["KB_AUTHORITY_SHAS"] = kb_authority_shas()
    values["GATES_OK_COUNT"] = f"{gates_ok_count}/{gates_total}"
    values["TOOLS_TESTS_TOTAL"] = tools_total
    values["TOOLS_TESTS_PASSED"] = tools_passed
    values["TOOLS_TESTS_FAILED"] = tools_failed
    values["BENCHMARK_METRICS"] = benchmark_metrics_text()

    # JOB_STATUS 没有缺省值：CI 用 `JOB_STATUS: ${{ job.status }}` 显式传入，本地跑就没有。
    job_ok = job_flag()

    def verdict(flag: bool | None) -> str:
        return "OK" if flag is True else (NOT_MEASURED if flag is None else "FAIL")

    # 三值：有红即 FAIL；没有红但有"没测"即 NOT_MEASURED；全绿且全部测到才 PASS。
    # "拿不到"绝不当成"通过"——run 上下文拿不到时这一条就会把 PASS 挡掉。
    overall = overall_status([job_ok, gates_ok, tools_ok, lint_ok, compile_ok, run_context_bound])
    values["OVERALL_STATUS"] = overall
    values["OVERALL_BREAKDOWN"] = " · ".join((
        f"build job: {verdict(job_ok)}",
        f"KB gates: {'all OK' if gates_ok else f'{gates_ok_count}/{gates_total} OK or crashed'}",
        f"tools tests: {tools_passed}/{tools_total} passed"
        if tools_ok else f"tools tests: {tools_failed}/{tools_total} failed or run failed",
        f"lint（app 2 变体 + {len(LINT_LIBRARY_MODULES)} 个库模块）: {verdict(lint_ok)}",
        f"compile evidence: {verdict(compile_ok)}",
        f"run context: {'BOUND' if run_context_bound else 'UNBOUND'}",
    ))

    # Known measurements first, then anything still unresolved in the
    # template is genuinely unmeasured. The sweep must run AFTER value
    # substitution: sweeping first erased every real value (acceptance
    # audit: status.md rendered all-NOT_MEASURED even on green runs).
    rendered = text
    for placeholder, value in values.items():
        rendered = rendered.replace("{{" + placeholder + "}}", value)
    rendered = re.sub(r"\{\{[A-Z0-9_]+\}\}", NOT_MEASURED, rendered)

    OUTPUT.write_text(rendered, encoding="utf-8")
    print(f"wrote {OUTPUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
