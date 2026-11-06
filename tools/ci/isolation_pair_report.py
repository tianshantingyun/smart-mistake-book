#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""isolation OFF/ON 配对报告与红集判定（批次 41 / WS4 item 26/28/31）。

## 它消灭的具体失败

批次 40 的 isolation 结论停在"单臂 15/15 绿"：没有配对臂、没有逐方法红集、
A3 的 kill 判据写的是"总数必须 = 5 且集合严格相等"，而实测出现设计外的
watchdog kill（`step04-isolation.md` §2）——在负载敏感量上要求精确等于，等于
把噪声写成红。批次 39 的三窗红集（6/7/5 例、集合各异）也因此无法比较。

本工具把红集变成一个**向量**而不是集合，并且只在低负载窗内比较：
1. 顺序扫描 `am instrument -r` 原始流（失败块 `stack=` 在 `test=` 之前，用
   "最近一个 test=" 回填会把失败算到下一个用例头上——那本身就是一次假红集）；
2. 每臂每轮得到 `方法 -> {PASS, FAIL, ERROR, SKIP, UNKNOWN}` 向量；
3. 臂内 k 轮向量逐项相同才叫 `STABLE`，否则 `UNCONVERGED`；一臂缺失的方法先补
   `UNKNOWN` 并记 `method_missing`——两侧没跑同一组方法时配对不成立（F7）；
   **期望方法清单（`--expected-methods`）是配对的前提**：没有清单时观察到的并集
   不能自证完整——两臂同时漏跑同一批方法（包括两边都只产生空日志、`OK (0 tests)`）
   会看起来"两侧都跑过且都没红"。缺清单直接 fail-closed
   （`expected_methods_missing`），观察集为空记 `no_observed_methods`。
4. 两臂所有轮次的窗口都必须 PASS（或 logcat 明确 `GATED`），否则 `UNCONVERGED`；
5. 配对成立后：向量相同 → `PAIRED_RED_SET_IDENTICAL`（**不等于**"T4 排除"）；
   向量不同且 OFF 臂**生产侧** T4 已移除、k≥4、该方法的精确 Fisher p<0.05 →
   `T4_CAUSAL`；其余一律 `PAIRED_RED_SET_DIVERGES_T4_UNVERIFIED`。
6. kill 归因按 `TestRunner: started:` 边界归属，判据改成结构性不变量（R5 §5.4）：
   必须杀的方法各 ≥1、必须不杀的方法 0、总数 ≥5；总数偏离只记 WARN。

统计上限（R5 §5.3，写死在这里）：单方法每臂 k 轮完美分离的精确 Fisher
p = 1.0 / 0.333 / 0.100 / **0.0286**（k=1/2/3/4）。k<4 时**不存在** p<0.05 的
因果证据；本工具据此拒绝输出 `T4_CAUSAL`，而不是"看起来一致就归因"。

## 退出码

- 0：判定为 `PAIRED_RED_SET_IDENTICAL` 或 `T4_CAUSAL`；
- 1：`UNCONVERGED` 或 `PAIRED_RED_SET_DIVERGES_T4_UNVERIFIED`（都不是可引用的因果结论）；
- 2：输入缺失/无法解析。

## 用法

    python -I tools/ci/isolation_pair_report.py \
        --run arm=OFF,round=1,window=PASS,log=build/x/off-1.log,logcat=build/x/off-1.logcat \
        --run arm=ON,round=1,window=PASS,log=build/x/on-1.log,logcat=build/x/on-1.logcat \
        ... \
        --off-arm-production-t4 test-side-only \
        --out build/x/pair.json
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass, field
from pathlib import Path

PASS = "PASS"
FAIL = "FAIL"
ERROR = "ERROR"
SKIP = "SKIP"
UNKNOWN = "UNKNOWN"
RED = {FAIL, ERROR}
NON_PASS = {FAIL, ERROR, SKIP, UNKNOWN}

CODE_TO_STATUS = {0: PASS, -1: ERROR, -2: FAIL, -3: UNKNOWN, -4: SKIP}

# kill 归因的设计基数（批次 40 step04 §2 的实测分布）：
# - watchdog 必须杀：3 个方法各 ≥1；
# - terminate 必须杀：theClientRebuildsAtMostOnce 的 2 次清理性 terminate；
# - watchdog 总数 5（其中 theClientRebuildsOnce… 的 2 次是批次 40 判定的"集合外"，
#   R5 §5.4 裁定：偏离只记 WARN，只有"必须不杀"出现 kill 才判红）。
WATCHDOG_MUST_KILL = (
    "aHungWorkerReleasesTheCallerWithinHardPlus500ms",
    "aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight",
    "theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState",
)
TERMINATE_EXPECTED = {
    "theClientRebuildsAtMostOnceAfterConfirmedAbandons": 2,
}
# 必须不杀：warm-up / ready / 连接超时 / 消费者校验 / 隔离往返 / 冷启动。
MUST_NOT_KILL = (
    "anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled",
    "aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching",
    "aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds",
    "anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead",
    "aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot",
    "aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp",
    "workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater",
    "anUnattributedEngineFailureKeepsItsPrefixAcrossTheAidlWire",
    "aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram",
)
DESIGN_WATCHDOG_KILLS = 5
REASON_OFF_ARM_REMOVED = "removed"


@dataclass
class RunInput:
    arm: str
    round_no: int
    window: str
    instrument: str
    logcat: str = ""
    label: str = ""
    source: str = ""


# ---------------------------------------------------------------- parsing


def parse_instrument_log(text: str) -> dict[str, str]:
    """顺序扫描 `-r` 流；同一测试的首个终态码获胜（重试不覆盖首轮）。"""
    results: dict[str, str] = {}
    current_test: str | None = None
    for raw in text.splitlines():
        line = raw.rstrip("\r")
        if line.startswith("INSTRUMENTATION_STATUS: test="):
            current_test = line.split("=", 1)[1].strip()
            continue
        if line.startswith("INSTRUMENTATION_STATUS_CODE:"):
            try:
                code = int(line.split(":", 1)[1].strip())
            except ValueError:
                continue
            if code == 1:
                continue
            if current_test is not None and code in CODE_TO_STATUS:
                results.setdefault(current_test, CODE_TO_STATUS[code])
    return results


def parse_gate_lines(text: str) -> list[dict]:
    """从 logcat 提取 instrumentation 自证门的机器行。"""
    found = []
    for raw in text.splitlines():
        if "SMB_LOWLOAD_GATE:" not in raw:
            continue
        tail = raw.split("SMB_LOWLOAD_GATE:", 1)[1].strip()
        fields: dict[str, str] = {}
        for token in tail.split():
            if "=" in token:
                key, value = token.split("=", 1)
                fields[key] = value
        found.append(fields)
    return found


def parse_kill_attribution(logcat: str) -> dict[str, dict[str, int]]:
    """按 `TestRunner: started:` 边界把服务端 kill 行归到当前用例。"""
    watchdog: dict[str, int] = {}
    terminate: dict[str, int] = {}
    other: dict[str, int] = {}
    current = "_unattributed"
    for raw in logcat.splitlines():
        line = raw.rstrip("\r")
        # logcat 形状：`I/TestRunner(19648): started: method(class)`（PID 在括号里）。
        if "TestRunner" in line and ": started: " in line:
            body = line.split(": started: ", 1)[1]
            method = body.split("(", 1)[0].strip()
            if method:
                current = method
                watchdog.setdefault(current, 0)
                terminate.setdefault(current, 0)
                other.setdefault(current, 0)
            continue
        if "killing :visual_js worker:" not in line:
            continue
        if "watchdog hard deadline" in line:
            watchdog[current] = watchdog.get(current, 0) + 1
        elif "terminate(" in line:
            terminate[current] = terminate.get(current, 0) + 1
        else:
            other[current] = other.get(current, 0) + 1
    return {"watchdog": watchdog, "terminate": terminate, "other": other}


def evaluate_kill_invariants(watchdog: dict[str, int],
                             terminate: dict[str, int] | None = None) -> dict:
    """结构性不变量（R5 §5.4），不要求精确等于设计基数。"""
    terminate = terminate or {}
    missing = [method for method in WATCHDOG_MUST_KILL if watchdog.get(method, 0) <= 0]
    terminate_missing = [
        method for method, expected in TERMINATE_EXPECTED.items()
        if terminate.get(method, 0) < expected
    ]
    must_not_kill_hits = [method for method in MUST_NOT_KILL if watchdog.get(method, 0) > 0]
    total = sum(watchdog.values())
    outside_design = sorted(
        method for method, count in watchdog.items()
        if count > 0 and method not in WATCHDOG_MUST_KILL and method not in MUST_NOT_KILL
    )
    total_below_design = total < DESIGN_WATCHDOG_KILLS
    return {
        "watchdog_total": total,
        "design_total": DESIGN_WATCHDOG_KILLS,
        "design_total_deviation": total - DESIGN_WATCHDOG_KILLS,
        "must_kill_missing": missing,
        "terminate_expected_missing": terminate_missing,
        "must_not_kill_hits": must_not_kill_hits,
        "outside_design": outside_design,
        "unexpected_watchdog_kills": bool(must_not_kill_hits),
        # "必须不杀"出现 kill 才是红；总数偏离设计基数只记 WARN（R5 §5.4）。
        "structurally_ok": not missing and not terminate_missing and not must_not_kill_hits,
        "total_below_design_warn": total_below_design,
        "per_method": dict(sorted(watchdog.items())),
    }


# ---------------------------------------------------------------- statistics


def fisher_exact_two_sided(a: int, b: int, c: int, d: int) -> float:
    """2×2 精确 Fisher（双尾）：[[a, b], [c, d]]。"""
    n = a + b + c + d
    if n == 0:
        return 1.0
    row1 = a + b
    col1 = a + c

    def probability(x: int) -> float:
        return math.comb(row1, x) * math.comb(n - row1, col1 - x) / math.comb(n, col1)

    observed = probability(a)
    low = max(0, col1 - (n - row1))
    high = min(row1, col1)
    total = sum(
        probability(x) for x in range(low, high + 1)
        if probability(x) <= observed + 1e-12
    )
    return min(1.0, total)


def mcnemar_exact_p(b: int, c: int) -> float:
    """精确 McNemar：p = 2·P(X ≤ min(b,c))，X~Bin(b+c, 0.5)。"""
    n = b + c
    if n == 0:
        return 1.0
    tail = sum(math.comb(n, i) for i in range(min(b, c) + 1)) / (2 ** n)
    return min(1.0, 2.0 * tail)


# ---------------------------------------------------------------- report


def _arm_rounds(runs: list[RunInput]) -> dict[str, dict[int, RunInput]]:
    arms: dict[str, dict[int, RunInput]] = {}
    for run in runs:
        arms.setdefault(run.arm.upper(), {})[run.round_no] = run
    return arms


def _effective_window(run: RunInput) -> tuple[str, list[str]]:
    reasons = []
    declared = run.window.upper()
    gates = parse_gate_lines(run.logcat)
    ungated = [gate for gate in gates if gate.get("verdict", "").upper() != "GATED"]
    if ungated:
        reasons.append("instrumentation_ungated")
    if declared != PASS:
        reasons.append(f"window_{declared.lower() or 'unknown'}")
    return (PASS if not reasons else "INVALID"), reasons


def build_report(runs: list[RunInput], off_arm_production_t4: str,
                 expected_methods: list[str] | None = None) -> dict:
    reasons: list[str] = []
    arms = _arm_rounds(runs)
    off = arms.get("OFF", {})
    on = arms.get("ON", {})
    if not off or not on:
        reasons.append("arm_missing")

    windows: dict[str, dict[str, str]] = {}
    for arm_name, rounds in arms.items():
        windows[arm_name] = {}
        for round_no, run in rounds.items():
            effective, window_reasons = _effective_window(run)
            windows[arm_name][str(round_no)] = effective
            if effective != PASS:
                reasons.append("window_not_pass")
                reasons.extend(window_reasons)

    rounds_per_arm = min(len(off), len(on)) if off and on else 0
    if rounds_per_arm < 3:
        reasons.append("rounds_short")

    vectors: dict[str, dict[int, dict[str, str]]] = {}
    for arm_name, rounds in arms.items():
        vectors[arm_name] = {}
        for round_no, run in rounds.items():
            vectors[arm_name][round_no] = parse_instrument_log(run.instrument)

    # F7：一臂少跑/漏报的方法必须是 UNKNOWN，不能"在向量里不存在 → 两侧都没红"。
    # 缺失是**配对不成立**的证据：两侧没有跑同一组方法，红集不可比。
    #
    # 只取观察并集不足以发现"两臂同时漏跑同一批"：需要的是一份**期望清单**。
    # 清单缺失时配对门 fail-closed——没有清单，"两侧都没红"可能只是"两侧都没跑"。
    observed_methods = sorted({
        method
        for arm_vectors in vectors.values()
        for vector in arm_vectors.values()
        for method in vector
    })
    expected = sorted({method.strip() for method in (expected_methods or []) if method.strip()})
    if not expected:
        reasons.append("expected_methods_missing")
    if not observed_methods:
        reasons.append("no_observed_methods")
    universe = sorted(set(expected) | set(observed_methods))
    missing_methods: dict[str, list[str]] = {}
    for arm_name, rounds in vectors.items():
        arm_missing: list[str] = []
        for vector in rounds.values():
            for method in universe:
                if method not in vector:
                    vector[method] = UNKNOWN
                    if method not in arm_missing:
                        arm_missing.append(method)
        missing_methods[arm_name] = arm_missing
    if any(missing_methods.values()):
        reasons.append("method_missing")

    unstable: list[str] = []
    for arm_name, rounds in arms.items():
        ordered = [vectors[arm_name][round_no] for round_no in sorted(vectors[arm_name])]
        if len(ordered) >= 2:
            methods = sorted({method for vector in ordered for method in vector})
            baseline = tuple(ordered[0].get(method, UNKNOWN) for method in methods)
            for vector in ordered[1:]:
                current = tuple(vector.get(method, UNKNOWN) for method in methods)
                if current != baseline:
                    unstable.append(arm_name)
                    break
    if unstable:
        reasons.append("arm_unstable")
    if any(
        status in {SKIP, UNKNOWN}
        for arm_vectors in vectors.values()
        for vector in arm_vectors.values()
        for status in vector.values()
    ):
        # SKIP（含整套件门跳过）与 UNKNOWN 都不算通过；只要有它们，配对不成立，
        # 不得拿"两臂都没有红"冒充"两臂都跑过"。
        reasons.append("skipped_or_unknown")

    paired = not reasons and bool(off) and bool(on) and rounds_per_arm >= 3

    report: dict = {
        "verdict": "UNCONVERGED" if not paired else None,
        "paired": paired,
        "reasons": [],
        "rounds_per_arm": rounds_per_arm,
        "windows": windows,
        "arms": {},
        "missing_methods": missing_methods,
        "expected_methods": expected,
        "observed_methods": observed_methods,
        "kill_attribution": {},
        "kill_invariants": {},
        "mcnemar_p": None,
        "fisher": None,
        "off_arm_production_t4": off_arm_production_t4,
    }

    kill_aggregate: dict[str, dict[str, int]] = {}
    for arm_name, rounds in arms.items():
        arm_vectors = {}
        watchdog: dict[str, int] = {}
        terminate: dict[str, int] = {}
        for round_no in sorted(rounds):
            run = rounds[round_no]
            arm_vectors[str(round_no)] = vectors[arm_name].get(round_no, {})
            attribution = parse_kill_attribution(run.logcat)
            for method, count in attribution["watchdog"].items():
                watchdog[method] = watchdog.get(method, 0) + count
            for method, count in attribution["terminate"].items():
                terminate[method] = terminate.get(method, 0) + count
        report["arms"][arm_name] = arm_vectors
        kill_aggregate[arm_name] = watchdog
        report["kill_attribution"][arm_name] = {
            "watchdog": dict(sorted(watchdog.items())),
            "terminate": dict(sorted(terminate.items())),
        }
        report["kill_invariants"][arm_name] = evaluate_kill_invariants(watchdog, terminate)

    if not paired:
        report["reasons"] = _dedupe(reasons)
        return report

    methods = sorted({method for vector in vectors["OFF"].values() for method in vector}
                     | {method for vector in vectors["ON"].values() for method in vector})
    k = rounds_per_arm
    discordant: list[dict] = []
    fisher_rows: list[dict] = []
    for method in methods:
        off_red = sum(1 for round_no in sorted(off)[:k]
                      if vectors["OFF"][round_no].get(method, UNKNOWN) in RED)
        on_red = sum(1 for round_no in sorted(on)[:k]
                     if vectors["ON"][round_no].get(method, UNKNOWN) in RED)
        if off_red != on_red:
            discordant.append({
                "method": method,
                "off_red": off_red,
                "on_red": on_red,
                "fisher_p": fisher_exact_two_sided(off_red, k - off_red, on_red, k - on_red),
            })
            # 完美分离：一侧 k 轮全红、另一侧 0 轮红。
            if {off_red, on_red} == {0, k}:
                fisher_rows.append(discordant[-1])

    # McNemar 只在"逐轮配对"有意义：同一方法在每个 round 上的 off/on 红与否。
    b = c = 0
    for method in methods:
        for round_no in sorted(off)[:k]:
            off_red = vectors["OFF"][round_no].get(method, UNKNOWN) in RED
            on_red = vectors["ON"][round_no].get(method, UNKNOWN) in RED
            if off_red and not on_red:
                b += 1
            elif on_red and not off_red:
                c += 1
    report["mcnemar_p"] = mcnemar_exact_p(b, c)
    report["discordant"] = discordant
    report["fisher"] = fisher_rows

    identical = not discordant
    if identical:
        report["verdict"] = "PAIRED_RED_SET_IDENTICAL"
        report["reasons"] = []
        return report

    causal = (
        off_arm_production_t4 == REASON_OFF_ARM_REMOVED
        and k >= 4
        and any(row["fisher_p"] < 0.05 for row in fisher_rows)
    )
    if causal:
        report["verdict"] = "T4_CAUSAL"
        report["reasons"] = []
        return report

    report["verdict"] = "PAIRED_RED_SET_DIVERGES_T4_UNVERIFIED"
    if off_arm_production_t4 != REASON_OFF_ARM_REMOVED:
        reasons.append("off_arm_not_t4_removed")
    if k < 4:
        reasons.append("rounds_short_for_causality")
    if not any(row["fisher_p"] < 0.05 for row in fisher_rows):
        reasons.append("no_perfect_separation_p_below_0_05")
    report["reasons"] = _dedupe(reasons)
    return report


def _dedupe(values: list[str]) -> list[str]:
    seen = set()
    return [value for value in values if not (value in seen or seen.add(value))]


# ---------------------------------------------------------------- CLI


def parse_run_spec(spec: str) -> RunInput:
    fields: dict[str, str] = {}
    for token in spec.split(","):
        token = token.strip()
        if not token:
            continue
        if "=" not in token:
            raise ValueError(f"unexpected --run token: {token!r}")
        key, value = token.split("=", 1)
        fields[key.strip()] = value.strip()
    for required in ("arm", "round", "log"):
        if not fields.get(required):
            raise ValueError(f"--run missing {required}: {spec!r}")
    instrument = Path(fields["log"]).read_text(encoding="utf-8", errors="replace")
    logcat = ""
    if fields.get("logcat"):
        logcat = Path(fields["logcat"]).read_text(encoding="utf-8", errors="replace")
    return RunInput(
        arm=fields["arm"],
        round_no=int(fields["round"]),
        window=fields.get("window", "PASS"),
        instrument=instrument,
        logcat=logcat,
        label=fields.get("label", ""),
        source=fields["log"],
    )


def render_markdown(report: dict) -> str:
    lines = [
        "# isolation OFF/ON 配对报告（机器生成）",
        "",
        f"- verdict: **{report['verdict']}**",
        f"- paired: {report['paired']}",
        f"- rounds_per_arm: {report['rounds_per_arm']}",
        f"- off_arm_production_t4: `{report['off_arm_production_t4']}`",
        f"- reasons: {', '.join(report['reasons']) or '-'}",
        f"- missing_methods: {report.get('missing_methods', {})}",
        f"- expected_methods: {len(report.get('expected_methods', []))}",
        f"- observed_methods: {len(report.get('observed_methods', []))}",
        f"- mcnemar_p: {report['mcnemar_p']}",
        "",
        "## 逐臂红集（FAIL/ERROR 为红；SKIP/UNKNOWN 单列，不算通过）",
        "",
    ]
    methods = sorted({method for arm in report["arms"].values()
                      for vector in arm.values() for method in vector})
    arms = sorted(report["arms"])
    lines.append("| method | " + " | ".join(arms) + " |")
    lines.append("|---|" + "|".join("---" for _ in arms) + "|")
    for method in methods:
        cells = []
        for arm in arms:
            statuses = [vector.get(method, UNKNOWN) for vector in report["arms"][arm].values()]
            cells.append("/".join(statuses) if statuses else "-")
        lines.append(f"| `{method}` | " + " | ".join(cells) + " |")
    lines += ["", "## kill 归因（watchdog / terminate）", ""]
    for arm in arms:
        attribution = report["kill_attribution"].get(arm, {})
        lines.append(f"- {arm}: watchdog={attribution.get('watchdog', {})} "
                     f"terminate={attribution.get('terminate', {})} "
                     f"invariants={report['kill_invariants'].get(arm, {})}")
    return "\n".join(lines) + "\n"


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="isolation OFF/ON paired report")
    parser.add_argument("--run", action="append", default=[],
                        help="arm=OFF,round=1,window=PASS,log=...,logcat=...")
    parser.add_argument("--off-arm-production-t4", default="test-side-only",
                        choices=["test-side-only", "removed", "absent"],
                        help="OFF 臂是否在生产侧移除了 T4 的 6 处写点")
    parser.add_argument("--expected-methods", required=True,
                        help="期望方法清单（每行一个）；缺失即 fail-closed，"
                             "防止两臂同时漏跑仍判 IDENTICAL")
    parser.add_argument("--out", help="JSON report path")
    parser.add_argument("--md", help="Markdown report path")
    args = parser.parse_args(argv)

    try:
        runs = [parse_run_spec(spec) for spec in args.run]
        expected_methods = [
            line.strip()
            for line in Path(args.expected_methods).read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.strip().startswith("#")
        ]
    except (OSError, ValueError) as failure:
        print(f"isolation_pair_report: {failure}", file=sys.stderr)
        return 2
    if not runs:
        print("isolation_pair_report: no --run inputs", file=sys.stderr)
        return 2

    report = build_report(runs, args.off_arm_production_t4, expected_methods)
    payload = json.dumps(report, ensure_ascii=False, indent=2)
    if args.out:
        Path(args.out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.out).write_text(payload, encoding="utf-8")
    if args.md:
        Path(args.md).parent.mkdir(parents=True, exist_ok=True)
        Path(args.md).write_text(render_markdown(report), encoding="utf-8")
    print(payload)
    return 0 if report["verdict"] in {"PAIRED_RED_SET_IDENTICAL", "T4_CAUSAL"} else 1


if __name__ == "__main__":
    sys.exit(main())
