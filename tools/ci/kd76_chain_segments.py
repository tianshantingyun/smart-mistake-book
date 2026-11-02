#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KD-76 的 29.5 s worst-case 链：只按 S1–S4 分段报数，禁止拼单例端到端。

批次 41 / WS3 item 22（计划 §2:54；口径 R2 `docs/research/2026-11-01-kd76-stats.md` §4）。

## 它消灭的具体失败

契约里的"最坏约 29.5 s"是四段**预算之和**（15 000 + 10 000 + 2 500 + 2 000），是
supremum，不是任何单例的实测值。批次 40 之前出现过两种错误写法：

1. 把"实测 29.5 s"当证据引用——但四段顶满在同一执行里结构性不可达（C1 顶满即终止、
   C2 顶满只接 C4、C3 要求前两段成功；R2 §4.4）。要"拼"出 29.5 s 只能靠夹具接力，
   测到的是测试作者选的注入常量之和，循环论证。
2. 把各段 p95 相加当"链 p95"——分位数不可加（Prometheus 官方文档；R2 §4.3）。

本脚本只做两件事：

- 从生产常量读出四段预算并断言其和 == 29 500 ms（常量漂移 → 非零退出，文档必须同步）；
- 从逐轮 XML（`run_kd76_rounds.sh` 产出）读出**同一轮集合**的 S1/S2 实测分布，
  逐段报 n/min/p50/p95/max；C3/C4 没有分布输入时明确写 UNVERIFIED。

**不提供**任何"端到端单例"输入项；`Σ 实测 max` 只在该轮集合四段都有实测时给出
（且只是上界，不是等式）。

## 退出码

- 0：预算常量读到且 Σ == 29 500 ms，S1/S2 至少各 1 个样本；
- 2：缺输入/常量读不到/Σ 预算 != 29 500（没有"分段报告"可言）。

用法：
    python -I tools/ci/kd76_chain_segments.py build/kd76-runs/<run-dir>
    python -I tools/ci/kd76_chain_segments.py <run-dir> --c3-witness-ms 2000
    python -I tools/ci/kd76_chain_segments.py <run-dir> --json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from summarize_kd76 import (  # noqa: E402
    collect_xml_paths,
    describe,
    parse_round_file,
)

REPO = Path(__file__).resolve().parents[2]
ENGINE_PATH = REPO / "app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt"
BUDGET_PATH = REPO / "core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt"

DOCUMENTED_ENVELOPE_MILLIS = 29_500

CONST_RE = r"const\s+val\s+{name}\s*:\s*Long\s*=\s*([0-9_]+)L"


def read_long_constant(path: Path, name: str) -> int:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as error:
        raise SystemExit(f"kd76_chain_segments: cannot read {path}: {error}")
    match = re.search(CONST_RE.format(name=re.escape(name)), text)
    if not match:
        raise SystemExit(f"kd76_chain_segments: constant {name} not found in {path}")
    return int(match.group(1).replace("_", ""))


def load_budgets() -> dict[str, int]:
    return {
        "C1_bind": read_long_constant(ENGINE_PATH, "FIRST_CONNECT_TIMEOUT_MILLIS"),
        "C2_warmup": read_long_constant(ENGINE_PATH, "READY_TIMEOUT_MILLIS"),
        "C3_host": read_long_constant(BUDGET_PATH, "HOST_PHASE_DEADLINE_MILLIS"),
        "C4_death": read_long_constant(ENGINE_PATH, "DEATH_CONFIRM_TIMEOUT_MILLIS"),
    }


def load_segment_samples(paths: list[Path]) -> tuple[list[int], list[int], list[str]]:
    binds: list[int] = []
    warmups: list[int] = []
    problems: list[str] = []
    for path in paths:
        row, rounds_problems = parse_round_file(path)
        problems.extend(rounds_problems)
        if row is None:
            continue
        if row.status != "SAMPLE":
            problems.append(f"{path.name}: round {row.round} is {row.status}, not a sample")
            continue
        if row.bind_ms is None or row.warmup_ms is None:
            problems.append(f"{path.name}: round {row.round} has no S-bind/S-warmup values")
            continue
        binds.append(row.bind_ms)
        warmups.append(row.warmup_ms)
    return binds, warmups, problems


def sum_of_measured_max(
    c1_max: int, c2_max: int, c4_max: int | None
) -> int | None:
    """C3 只有 witness（上界），没有实测 max；缺 C4 时不给和。"""
    if c4_max is None:
        return None
    return c1_max + c2_max + c4_max


def build_report(args: argparse.Namespace) -> dict[str, object]:
    budgets = load_budgets()
    envelope = sum(budgets.values())
    if envelope != DOCUMENTED_ENVELOPE_MILLIS:
        raise SystemExit(
            "kd76_chain_segments: production budgets sum to "
            f"{envelope} ms, but the documented envelope is {DOCUMENTED_ENVELOPE_MILLIS} ms; "
            "update docs/current/visual-generation-contract.md and this tool in the same change"
        )

    try:
        paths = collect_xml_paths(args.inputs)
    except FileNotFoundError as error:
        raise SystemExit(f"kd76_chain_segments: {error}")
    if not paths:
        raise SystemExit("kd76_chain_segments: no round-*.xml files found")

    binds, warmups, problems = load_segment_samples(paths)
    if not binds:
        raise SystemExit(
            "kd76_chain_segments: no SAMPLE rows with S-bind/S-warmup; "
            "run tools/ci/run_kd76_rounds.sh first"
        )

    c1 = describe(binds)
    c2 = describe(warmups)
    c3_max = args.c3_witness_ms
    c4_max = args.c4_max_ms
    # C3 只有 KD-66 witness（hard+500 上界），不是 C3 分布；不得混进 "Σ 实测 max"。
    measured_max = sum_of_measured_max(int(c1["max"]), int(c2["max"]), c4_max)

    return {
        "budgets": budgets,
        "envelope_millis": envelope,
        "documented_envelope_millis": DOCUMENTED_ENVELOPE_MILLIS,
        "segments": {
            "C1_bind": {**c1, "budget_millis": budgets["C1_bind"], "source": "round-*.xml S-bind"},
            "C2_warmup": {**c2, "budget_millis": budgets["C2_warmup"], "source": "round-*.xml S-warmup"},
            "C3_host": {
                "budget_millis": budgets["C3_host"],
                "max_witness_millis": c3_max,
                "source": "KD-66 witness (hard+500 ms), not a C3 distribution",
            },
            "C4_death": {
                "budget_millis": budgets["C4_death"],
                "max_millis": c4_max,
                "source": "abandonForTest wall clock" if c4_max is not None else "UNVERIFIED",
            },
        },
        "sum_of_measured_max_millis": measured_max,
        "sum_of_measured_max_note": (
            "upper bound only; C3 has no measured max (the KD-66 witness is a bound, "
            "not a C3 distribution)"
            if measured_max is not None
            else "UNVERIFIED: C4 has no measured max"
        ),
        "problems": problems,
        "rules": [
            "segment p95s are never added into a chain p95 (quantiles are not additive)",
            "no single end-to-end 29.5 s sample exists or may be synthesized (R2 §4.4)",
            "the envelope 29 500 ms is the sum of budgets (supremum), not a measured value",
        ],
    }


def _fmt(value: object) -> str:
    return "UNVERIFIED" if value is None else str(value)


def render_text(report: dict[str, object]) -> str:
    budgets = report["budgets"]
    segments = report["segments"]
    assert isinstance(budgets, dict) and isinstance(segments, dict)
    c1 = segments["C1_bind"]
    c2 = segments["C2_warmup"]
    c3 = segments["C3_host"]
    c4 = segments["C4_death"]

    def stat(segment: dict[str, object], key: str) -> str:
        return _fmt(segment.get(key))

    lines = [
        "KD-76 29.5 s chain — segmented budgets + per-segment measured distributions",
        "(budgets read from production constants; a sum != 29 500 ms is a hard error)",
        "",
        "| segment | budget ms | n | min | p50 | p95 | max | evidence |",
        "|---|---:|---:|---:|---:|---:|---:|---|",
        f"| C1 S-bind | {budgets['C1_bind']} | {stat(c1, 'n')} | {stat(c1, 'min')} | "
        f"{stat(c1, 'p50')} | {stat(c1, 'p95')} | {stat(c1, 'max')} | round-*.xml S-bind |",
        f"| C2 S-warmup | {budgets['C2_warmup']} | {stat(c2, 'n')} | {stat(c2, 'min')} | "
        f"{stat(c2, 'p50')} | {stat(c2, 'p95')} | {stat(c2, 'max')} | round-*.xml S-warmup |",
        f"| C3 host | {budgets['C3_host']} | - | - | - | - | "
        f"witness={_fmt(c3.get('max_witness_millis'))} | KD-66 witness, not a distribution |",
        f"| C4 death | {budgets['C4_death']} | - | - | - | - | "
        f"{_fmt(c4.get('max_millis'))} | abandonForTest wall clock |",
        "",
        f"sum of budgets = {report['envelope_millis']} ms "
        f"(documented {report['documented_envelope_millis']}; supremum, NOT a measured value)",
        f"sum of measured max = {_fmt(report['sum_of_measured_max_millis'])} — "
        f"{report['sum_of_measured_max_note']}",
        "",
        "rules:",
    ]
    for rule in report["rules"]:  # type: ignore[union-attr]
        lines.append(f"  - {rule}")
    problems = report["problems"]
    if problems:  # type: ignore[union-attr]
        lines.append("input warnings:")
        for problem in problems:  # type: ignore[union-attr]
            lines.append(f"  - {problem}")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Report the KD-76 29.5 s chain as per-segment budgets/distributions; "
            "single end-to-end samples are intentionally not accepted."
        )
    )
    parser.add_argument("inputs", nargs="+", help="run directory or round-*.xml files")
    parser.add_argument(
        "--c3-witness-ms",
        type=int,
        default=None,
        help="KD-66 witness bound (elapsed <= hard+500 ms); not a C3 distribution",
    )
    parser.add_argument(
        "--c4-max-ms",
        type=int,
        default=None,
        help="measured C4 max from abandonForTest wall clock (omit when unmeasured)",
    )
    parser.add_argument("--json", action="store_true", help="machine-readable report")
    args = parser.parse_args(argv)

    report = build_report(args)
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        print(render_text(report))
    return 0


if __name__ == "__main__":
    sys.exit(main())
