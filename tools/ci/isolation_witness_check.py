#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""isolation witness 分类与 15 例自证判据（批次 43 / WS3 item 19–23/25）。

## 它消灭的具体失败

`docs/research/2026-11-04-batch-42-record.md` §2.3 记录了本仓最贵的一次假绿：
**15 例全 `-4`、零执行，却打印 `OK (15 tests)`**。原因不是 runner 有 bug，而是
JUnit 把 assumption 失败单独计数：`wasSuccessful() = failures == 0`，`-4` 不进
`failures`，`getRunCount()` 仍按 `testFinished` 计 15（研究件 §1.4）。已有工具
`isolation_pair_report.py` 只把 `-4` 记成 `SKIP`，它能说"不是通过"，**说不出**
"这一例到底跑没跑、为什么没跑"。

本工具消费 `IsolationWitness` 写出的 JSONL（`phase ∈ rule_begin / gate /
body_begin / body_end`），把每条方法收敛到 8 个观测态之一，再按
`isolation_witness_expectations.json` 的 `floor_ms` 判"体内是否真的工作了"：

- S1 `NOT_SCHEDULED`：流与 witness 都没有该方法 → 未执行；
- S2 `SCHEDULED_NOT_RUN`：有 `CODE:1`/`rule_begin` 但无 gate 行也无终态 → 未执行；
- S3 `IGNORED`：终态 `-3` → 未执行（显式）；
- S4 `ENV_SKIP`：`-4` + 首帧 `IsolationGate#require`（或有 gate 行且 UNGATED、无 body）
  → **环境不满足**；
- S5 `FIXTURE_SKIP`：gate 行 GATED、无 `body_begin`、终态 `-4` 且首帧不是 gate
  → **夹具跳过**（当前合法命中数应为 0）；
- S6 `BODY_SKIP`：`body_begin` + `body_end(ASSUMPTION)` + `-4` → 夹具跳过（体内）；
- S7 `BODY_CRASH`：`body_begin` 无 `body_end`，终态 `-2/-1` 或缺失 → 已执行未完成；
- S8 `EXECUTED`：`body_begin` + `body_end` + 终态 `0/-2`；`-2` 是执行过的红。

**证据等级（研究件 §3）**：只有 `-r` 流时（`stream_only`），S5/S6/S7/S8 一律不可判，
输出 `UNDECIDABLE_NO_WITNESS`，绝不猜。`witness+stream` 才能判全部。

## 低负载前门的接入方式（item 21，事后而非事前）

`lowload_gate.py` 仍是**唯一**的环境负载判定权（instrumentation 读不到
`/proc/stat`）。本工具只把 witness 的 `wall_ms` / `cpu_delta_ms` 作为**事后**证据：
一例真跑过必然有墙钟，15 例的 `floor` 之和是 `44450 ms`；r1 那种 820 ms 的"全跳过轮"
在逐例 floor 上直接暴露。**红线**：`cpu_delta_ms / wall_ms` 只记录、不设阈值——
`own_cpu_busy` 已经用单核分数误判过多线程进程（研究件 §2.4），不能把同一个 bug
搬到第二个位置。

## 退出码

- 0：解析成功且没有违规（`--require-executed` / `--require-floors` 满足时）；
- 1：有违规（未执行、floor 不足、witness 不可用、roster 漂移、矛盾态）；
- 2：输入不可读/参数错误。

## 用法

    python -I tools/ci/isolation_witness_check.py \
        --witness build/iso/run-1/witness.jsonl \
        --logcat build/iso/run-1.logcat \
        --stream build/iso/run-1.instrument.log \
        --require-executed --require-floors \
        --out build/iso/witness-check.json
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

PASS = "PASS"
FAIL = "FAIL"
ERROR = "ERROR"
SKIP = "SKIP"
IGNORED = "IGNORED"
UNKNOWN = "UNKNOWN"

# `-3` 是 ignored（@Ignore / 过滤器），与配对报告的 UNKNOWN 归并**不同**：本工具要
# 把"显式不跑"与"跑了但没证据"分开，所以保留独立状态。
CODE_TO_STATUS = {0: PASS, -1: ERROR, -2: FAIL, -3: IGNORED, -4: SKIP}

STATES = (
    "NOT_SCHEDULED",
    "SCHEDULED_NOT_RUN",
    "IGNORED",
    "ENV_SKIP",
    "FIXTURE_SKIP",
    "BODY_SKIP",
    "BODY_CRASH",
    "EXECUTED",
    "UNDECIDABLE_NO_WITNESS",
    "STREAM_INCOMPLETE",
    "CONTRADICTION_PASS_WITHOUT_BODY",
)

# 这些态表示"这一例没有被证明执行过"，--require-executed 会把它们判违规。
NOT_EXECUTED = {"NOT_SCHEDULED", "SCHEDULED_NOT_RUN", "IGNORED", "ENV_SKIP",
                "FIXTURE_SKIP", "BODY_SKIP", "UNDECIDABLE_NO_WITNESS",
                "STREAM_INCOMPLETE", "CONTRADICTION_PASS_WITHOUT_BODY"}
# 这些态是"证据面矛盾/截断"，即使不加 --require-executed 也算违规。
ALWAYS_VIOLATION = {"STREAM_INCOMPLETE", "CONTRADICTION_PASS_WITHOUT_BODY"}

WITNESS_MIRROR = "SMB_ISOLATION_WITNESS:"


# ---------------------------------------------------------------- parsing


def parse_stream(text: str) -> dict[str, dict]:
    """解析 `am instrument -r` 流，保留每例首个终态码与 `-4` 堆栈帧。

    顺序扫描：`stack=` 出现在终态码之前，按"最近一个 `test=`"归属；首个终态码获胜。
    堆栈保留**全部**帧而不是首帧：真实流的第一帧是 `org.junit.Assume.assumeTrue`，
    真正的归因帧（`IsolationGate.require`）在第二帧（研究件 §2.1 的原文形状）。
    """
    per: dict[str, dict] = {}
    current: str | None = None
    collecting_stack = False
    for raw in text.splitlines():
        line = raw.rstrip("\r")
        if line.startswith("INSTRUMENTATION_STATUS: test="):
            current = line.split("=", 1)[1].strip()
            entry = per.setdefault(
                current, {"status": None, "stack_frames": [], "started": False}
            )
            collecting_stack = False
            continue
        if line.startswith("INSTRUMENTATION_STATUS_CODE:"):
            try:
                code = int(line.split(":", 1)[1].strip())
            except ValueError:
                continue
            collecting_stack = False
            if current is None:
                continue
            if code == 1:
                per[current]["started"] = True
                continue
            if per[current]["status"] is None:
                per[current]["status"] = CODE_TO_STATUS.get(code, UNKNOWN)
            continue
        if line.startswith("INSTRUMENTATION_STATUS: stack="):
            collecting_stack = True
            continue
        if collecting_stack and current is not None:
            stripped = line.strip()
            if stripped.startswith("at "):
                frame = stripped[3:].split("(", 1)[0].strip()
                if frame:
                    per[current]["stack_frames"].append(frame)
    return per


def parse_witness_rows(lines: list[str]) -> list[dict]:
    """解析 JSONL；坏行计入 `malformed` 而不是静默丢弃。"""
    rows: list[dict] = []
    for raw in lines:
        line = raw.strip()
        if not line:
            continue
        try:
            parsed = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(parsed, dict) and parsed.get("phase"):
            rows.append(parsed)
    return rows


def parse_logcat_mirror(text: str) -> list[dict]:
    """文件不可写时的下限回读：从 `SMB_ISOLATION_WITNESS: {json}` 提取同一份行。"""
    rows: list[dict] = []
    seen: set[int] = set()
    for raw in text.splitlines():
        if WITNESS_MIRROR not in raw:
            continue
        tail = raw.split(WITNESS_MIRROR, 1)[1].strip()
        try:
            parsed = json.loads(tail)
        except json.JSONDecodeError:
            continue
        seq = parsed.get("seq")
        if isinstance(seq, int):
            if seq in seen:
                continue
            seen.add(seq)
        rows.append(parsed)
    return rows


def rows_by_method(rows: list[dict]) -> dict[str, list[dict]]:
    grouped: dict[str, list[dict]] = {}
    for row in rows:
        method = row.get("method")
        if not isinstance(method, str) or not method:
            continue
        grouped.setdefault(method, []).append(row)
    return grouped


def suite_rows(rows: list[dict]) -> dict[str, dict]:
    found: dict[str, dict] = {}
    for row in rows:
        phase = row.get("phase")
        if phase in {"suite_start", "canary", "witness_error", "suite_end"}:
            found.setdefault(phase, row)
    return found


# ---------------------------------------------------------------- classification


def classify(rows: list[dict], stream_entry: dict | None, evidence: str) -> tuple[str, list[str]]:
    """把一个方法收敛到 [STATES] 之一；优先级为研究件 §3 表格自上而下。"""
    reasons: list[str] = []
    phases = [row.get("phase") for row in rows]
    gate = next((row for row in rows if row.get("phase") == "gate"), None)
    body_begin = "body_begin" in phases
    body_end = next((row for row in rows if row.get("phase") == "body_end"), None)
    status = (stream_entry or {}).get("status")
    frames = (stream_entry or {}).get("stack_frames") or []
    gate_frame = any("IsolationGate" in frame for frame in frames)
    stack = ";".join(frames[:3])
    seen_stream = stream_entry is not None

    if evidence == "stream_only":
        if not seen_stream:
            return "NOT_SCHEDULED", ["no_stream_block", "evidence=stream_only"]
        if status == IGNORED:
            return "IGNORED", ["stream:-3"]
        if status == SKIP and gate_frame:
            return "ENV_SKIP", ["stream:-4", f"stack={stack}"]
        if status is None:
            return "SCHEDULED_NOT_RUN", ["started_without_terminal"]
        # S5/S6/S7/S8 需要 witness；没有 witness 时不得猜。
        return "UNDECIDABLE_NO_WITNESS", ["evidence=stream_only", f"status={status}"]

    if not rows and not seen_stream:
        return "NOT_SCHEDULED", ["no_witness_rows", "no_stream_block"]
    if rows and not seen_stream:
        return "STREAM_INCOMPLETE", ["witness_rows_without_stream_block"]
    if seen_stream and not rows:
        return "UNDECIDABLE_NO_WITNESS", ["stream_without_witness_rows"]

    if body_begin and body_end is not None:
        outcome = body_end.get("outcome")
        if status == IGNORED:
            return "IGNORED", ["stream:-3"]
        if outcome == "ASSUMPTION" and status == SKIP:
            return "BODY_SKIP", [f"skip_reason={body_end.get('skip_reason', '')}"]
        if status is None:
            return "STREAM_INCOMPLETE", ["body_end_without_stream_terminal"]
        return "EXECUTED", [f"outcome={outcome}", f"status={status}"]

    if body_begin and body_end is None:
        return "BODY_CRASH", ["body_begin_without_body_end", f"status={status}"]

    if status == IGNORED:
        return "IGNORED", ["stream:-3"]
    if gate is not None and gate.get("verdict") == "UNGATED" and status == SKIP:
        return "ENV_SKIP", ["gate_ungated", f"reason={gate.get('reason', '')}"]
    if (
        gate is not None
        and gate.get("verdict") == "GATED"
        and status == SKIP
        and not gate_frame
    ):
        return "FIXTURE_SKIP", ["gate_gated_but_no_body"]
    if status == SKIP and gate_frame:
        return "ENV_SKIP", ["stream:-4", f"stack={stack}"]
    if status is None:
        return "SCHEDULED_NOT_RUN", ["started_without_terminal", "no_gate_row"]
    if status == PASS:
        return "CONTRADICTION_PASS_WITHOUT_BODY", ["stream:0", "no_body_begin"]
    return "UNDECIDABLE_NO_WITNESS", ["gate_and_body_absent", f"status={status}"]


# ---------------------------------------------------------------- expectations


def check_floor(spec: dict, rows: list[dict], cpu_delta_min: int) -> dict:
    """只检查机器可观测的两项：`wall_ms >= floor_ms` 与 `cpu_delta_ms >= 1`。

    `require_effects` / `forbid` 是**文档性**字段：效应由用例断言承担（失败即 `-2`），
    kill 归因由 `isolation_pair_report.py` 承担。这里不重复实现，避免两处漂移。
    """
    end = next((row for row in rows if row.get("phase") == "body_end"), None) or {}
    wall = end.get("wall_ms")
    cpu = end.get("cpu_delta_ms")
    unreadable = bool(end.get("cpu_unreadable"))
    floor = int(spec.get("floor_ms") or 0)
    wall_ok = isinstance(wall, (int, float)) and wall >= floor
    cpu_ok = None if unreadable else (isinstance(cpu, (int, float)) and cpu >= cpu_delta_min)
    return {
        "method": spec.get("method"),
        "floor_ms": floor,
        "wall_ms": wall,
        "wall_ok": wall_ok,
        "cpu_delta_ms": cpu,
        "cpu_delta_min_ms": cpu_delta_min,
        "cpu_ok": cpu_ok,
        "cpu_unreadable": unreadable,
        "outcome": end.get("outcome"),
        "require_effects": spec.get("require_effects", []),
        "forbid": spec.get("forbid", []),
    }


# ---------------------------------------------------------------- report


def load_expectations(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    methods = data.get("methods") or []
    if not isinstance(methods, list) or not methods:
        raise ValueError("expectations must contain a non-empty methods list")
    names = [item.get("method") for item in methods]
    if any(not name for name in names):
        raise ValueError("every expectation needs a method")
    if len(set(names)) != len(names):
        raise ValueError("duplicate method in expectations")
    return data


def load_methods(path: Path) -> list[str]:
    return [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]


def build_report(
    witness_rows: list[dict],
    stream: dict[str, dict],
    expectations: dict,
    roster: list[str],
    witness_source: str,
    stream_supplied: bool,
) -> dict:
    suite = suite_rows(witness_rows)
    grouped = rows_by_method(witness_rows)
    evidence = "witness+stream" if stream_supplied and witness_rows else (
        "stream_only" if stream_supplied else "witness_only"
    )
    specifications = expectations.get("methods") or []
    cpu_delta_min = int(expectations.get("cpu_delta_min_ms") or 1)

    canary_ok: bool | None = None
    if "suite_end" in suite:
        canary_ok = bool(suite["suite_end"].get("canary_ok"))
    elif "canary" in suite:
        canary_ok = True  # 行存在；读回结果以 suite_end 为准

    methods = [item["method"] for item in specifications]
    results: dict[str, dict] = {}
    violations: list[str] = []
    for spec in specifications:
        method = spec["method"]
        rows = grouped.get(method, [])
        state, reasons = classify(rows, stream.get(method), evidence)
        entry = {
            "state": state,
            "reasons": reasons,
            "rows": len(rows),
            "stream_status": (stream.get(method) or {}).get("status"),
            "gate": next((row for row in rows if row.get("phase") == "gate"), None),
            "floor": None,
        }
        if state == "EXECUTED":
            floor = check_floor(spec, rows, cpu_delta_min)
            entry["floor"] = floor
            if not floor["wall_ok"]:
                violations.append(
                    f"{method}: wall_ms={floor['wall_ms']} < floor_ms={floor['floor_ms']}"
                )
            if floor["cpu_ok"] is False:
                violations.append(
                    f"{method}: cpu_delta_ms={floor['cpu_delta_ms']} < {cpu_delta_min}"
                )
        results[method] = entry

    roster_set = set(roster)
    expected_set = set(methods)
    roster_missing = sorted(expected_set - roster_set)
    roster_extra = sorted(roster_set - expected_set)
    if roster_missing or roster_extra:
        violations.append(
            f"roster_mismatch: missing_from_roster={roster_missing} extra_in_roster={roster_extra}"
        )

    counts = {state: 0 for state in STATES}
    for entry in results.values():
        counts[entry["state"]] = counts.get(entry["state"], 0) + 1
    for method, entry in results.items():
        if entry["state"] in ALWAYS_VIOLATION:
            violations.append(f"{method}: {entry['state']} ({', '.join(entry['reasons'])})")

    executed = sorted(m for m, entry in results.items() if entry["state"] == "EXECUTED")
    body_rows = [
        row for row in witness_rows
        if row.get("phase") == "body_end" and isinstance(row.get("wall_ms"), (int, float))
    ]
    total_body_wall = sum(row["wall_ms"] for row in body_rows)
    floor_sum = sum(int(spec.get("floor_ms") or 0) for spec in specifications)
    gate_rows = [row for row in witness_rows if row.get("phase") == "gate"]
    reused_counts = {
        "gate_rows": len(gate_rows),
        "reused_true": sum(1 for row in gate_rows if row.get("reused") is True),
        "reused_false": sum(1 for row in gate_rows if row.get("reused") is False),
    }

    return {
        "evidence": evidence,
        "witness_source": witness_source,
        "witness_available": "suite_start" in suite,
        "canary_ok": canary_ok,
        "witness_unavailable_reason": (suite.get("suite_end") or {}).get("witness_unavailable"),
        "suite": {
            "start": suite.get("suite_start"),
            "end": suite.get("suite_end"),
            "rows": len(witness_rows),
        },
        "expected_methods": len(methods),
        "counts": counts,
        "executed": executed,
        "not_executed": sorted(NOT_EXECUTED.intersection({e["state"] for e in results.values()})),
        "methods": results,
        "gate_cache": reused_counts,
        "load_evidence": {
            "floor_sum_ms": floor_sum,
            "total_body_wall_ms": total_body_wall,
            "body_rows": len(body_rows),
            "cpu_ratio_note": "cpu_delta_ms/wall_ms 只记录，不设阈值（研究件 §4.4）",
            "cpu_ratio_per_method": {
                method: round(
                    (entry["floor"]["cpu_delta_ms"] or 0) / entry["floor"]["wall_ms"], 4
                )
                if entry.get("floor") and entry["floor"].get("wall_ms")
                else None
                for method, entry in results.items()
            },
        },
        "violations": violations,
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="isolation witness classifier (batch 43 / WS3)")
    parser.add_argument("--witness", help="witness.jsonl path")
    parser.add_argument("--logcat", help="logcat mirror fallback (SMB_ISOLATION_WITNESS)")
    parser.add_argument("--stream", help="am instrument -r output")
    parser.add_argument(
        "--expectations",
        default=str(Path(__file__).with_name("isolation_witness_expectations.json")),
    )
    parser.add_argument(
        "--methods",
        default=str(Path(__file__).with_name("visual_js_isolation_methods.txt")),
    )
    parser.add_argument("--require-executed", action="store_true",
                        help="每一例都必须是 EXECUTED（witness 齐全）")
    parser.add_argument("--require-floors", action="store_true",
                        help="EXECUTED 的方法必须满足 floor 与 cpu_delta>=1")
    parser.add_argument("--allow-missing-witness", action="store_true",
                        help="stream_only 输入（旧产物回归夹具）时允许 witness 缺失")
    parser.add_argument("--out", help="JSON report path")
    args = parser.parse_args(argv)

    witness_rows: list[dict] = []
    witness_source = ""
    try:
        if args.witness:
            witness_source = args.witness
            witness_rows = parse_witness_rows(
                Path(args.witness).read_text(encoding="utf-8", errors="replace").splitlines()
            )
        if not witness_rows and args.logcat:
            witness_source = f"{args.logcat}#logcat"
            witness_rows = parse_logcat_mirror(
                Path(args.logcat).read_text(encoding="utf-8", errors="replace")
            )
        stream_text = ""
        if args.stream:
            stream_text = Path(args.stream).read_text(encoding="utf-8", errors="replace")
        elif args.logcat:
            stream_text = Path(args.logcat).read_text(encoding="utf-8", errors="replace")
        expectations = load_expectations(Path(args.expectations))
        roster = load_methods(Path(args.methods))
    except (OSError, ValueError) as failure:
        print(f"isolation_witness_check: {failure}", file=sys.stderr)
        return 2

    stream = parse_stream(stream_text)
    report = build_report(
        witness_rows=witness_rows,
        stream=stream,
        expectations=expectations,
        roster=roster,
        witness_source=witness_source,
        stream_supplied=bool(args.stream or args.logcat),
    )
    violations = list(report["violations"])

    if not report["witness_available"] and not args.allow_missing_witness:
        violations.append("witness_missing: no suite_start row")
    if report["canary_ok"] is False:
        violations.append("canary_failed: witness file unreadable/unwritable")
    if args.require_executed:
        for method in report["methods"]:
            state = report["methods"][method]["state"]
            if state in NOT_EXECUTED:
                violations.append(f"{method}: not executed ({state})")
    if args.require_floors:
        for method, entry in report["methods"].items():
            if entry["state"] != "EXECUTED":
                continue
            floor = entry.get("floor") or {}
            if not floor.get("wall_ok"):
                violations.append(
                    f"{method}: floor unmet (wall_ms={floor.get('wall_ms')} "
                    f"< {floor.get('floor_ms')})"
                )
            if floor.get("cpu_ok") is False:
                violations.append(
                    f"{method}: cpu window empty (cpu_delta_ms={floor.get('cpu_delta_ms')})"
                )

    report["violations"] = sorted(dict.fromkeys(violations))
    report["ok"] = not report["violations"]
    payload = json.dumps(report, ensure_ascii=False, indent=2)
    if args.out:
        Path(args.out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.out).write_text(payload, encoding="utf-8")
    print(payload)
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
