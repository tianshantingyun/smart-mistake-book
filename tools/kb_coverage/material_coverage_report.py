# -*- coding: utf-8 -*-
"""把 `v=1 metric=material_coverage` 日志行聚合成 per-subject 覆盖率报告。

输入（只读）：logcat 文本、`adb logcat -d` 的转储、或 `build/` 下任意日志文件；
也接受 `-` 从 stdin 读取。输出：每个 subject 的
`queriedNodes / returnedMaterials / nodesWithMaterial / nodesWithoutMaterial` 合计，
以及两个率：

- 覆盖率 = nodesWithMaterial / queriedNodes（本次查询真的拿到了材料的节点占比）
- 空材料节点率 = nodesWithoutMaterial / queriedNodes（请求了但没有材料的节点占比）

计数口径是**按事件累加**（一行 = 一次取数），不是去重节点数；同一节点被多次查询会
被多次计入。`emptyNodeIds` 每行最多 10 个（`KnowledgeMaterialCoverageTelemetry.MAX_EMPTY_NODE_IDS`），
所以本报告不用它推算唯一空节点数，只把节点计数作为权威口径。

它消灭的失败：批次26 落地的遥测行此前只能靠人工 grep 看单行；要回答“数学/物理各自
覆盖率多少、空材料节点率多少”必须手工加总，容易把 `returnedMaterials`（材料条数）和
`nodesWithMaterial`（节点数）混为一谈。

格式契约见
`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeMaterialCoverageTelemetry.kt:39,88-98`。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterator, Sequence, TextIO

LINE_MARKER = "v=1 metric=material_coverage"

# 行首可能带 logcat 的时间/pid/tag 前缀，因此不锚定 `^`；字段顺序与
# KnowledgeMaterialCoverageTelemetry.formatLine 完全一致，改格式即失配（显式报 malformed）。
_LINE_RE = re.compile(
    re.escape(LINE_MARKER)
    + r"\s+subject=(?P<subject>\S+)"
    + r"\s+queriedNodes=(?P<queried>\d+)"
    + r"\s+returnedMaterials=(?P<returned>\d+)"
    + r"\s+nodesWithMaterial=(?P<with>\d+)"
    + r"\s+nodesWithoutMaterial=(?P<without>\d+)"
    + r"\s+emptyNodeIds=\[(?P<empty>[^\]]*)\]"
)

LOG_SUFFIXES = (".log", ".txt", ".logcat")


@dataclass(frozen=True)
class CoverageEvent:
    """一行遥测日志解出的一次材料取数事实。"""

    subject: str
    queried_nodes: int
    returned_materials: int
    nodes_with_material: int
    nodes_without_material: int
    empty_node_ids: tuple[str, ...]


@dataclass
class SubjectTotals:
    events: int = 0
    queried_nodes: int = 0
    returned_materials: int = 0
    nodes_with_material: int = 0
    nodes_without_material: int = 0

    def add(self, event: CoverageEvent) -> None:
        self.events += 1
        self.queried_nodes += event.queried_nodes
        self.returned_materials += event.returned_materials
        self.nodes_with_material += event.nodes_with_material
        self.nodes_without_material += event.nodes_without_material

    @property
    def coverage(self) -> float | None:
        return _ratio(self.nodes_with_material, self.queried_nodes)

    @property
    def empty_material_node_rate(self) -> float | None:
        return _ratio(self.nodes_without_material, self.queried_nodes)


@dataclass
class Report:
    subjects: dict[str, SubjectTotals] = field(default_factory=dict)
    files_scanned: int = 0
    lines_scanned: int = 0
    malformed_lines: int = 0

    @property
    def event_count(self) -> int:
        return sum(t.events for t in self.subjects.values())

    def totals(self) -> SubjectTotals:
        total = SubjectTotals()
        for totals in self.subjects.values():
            total.events += totals.events
            total.queried_nodes += totals.queried_nodes
            total.returned_materials += totals.returned_materials
            total.nodes_with_material += totals.nodes_with_material
            total.nodes_without_material += totals.nodes_without_material
        return total


def _ratio(numerator: int, denominator: int) -> float | None:
    return None if denominator == 0 else numerator / denominator


def parse_line(line: str) -> tuple[CoverageEvent | None, bool]:
    """返回 `(event, malformed)`。

    - 不是该指标行：`(None, False)`，跳过。
    - 有指标前缀但字段缺失/不一致：`(None, True)`，计入 malformed，绝不静默当 0。
    """
    marker = line.find(LINE_MARKER)
    if marker < 0:
        return None, False
    match = _LINE_RE.search(line, marker)
    if match is None:
        return None, True

    empty_raw = match.group("empty").strip()
    empty_ids = tuple(part.strip() for part in empty_raw.split(",") if part.strip())
    queried = int(match.group("queried"))
    with_material = int(match.group("with"))
    without_material = int(match.group("without"))

    # 分子 + 分母必须回到总数；对不上说明行被截断/写坏，不能拿来算覆盖率。
    if with_material + without_material != queried:
        return None, True
    # 生产实现里 emptyNodeIds 是 `queried - withMaterial` 的前 10 个；条的明细多于
    # 空节点数同样说明字段错位。
    if len(empty_ids) > without_material:
        return None, True

    subject = match.group("subject")
    if not subject:
        return None, True

    return (
        CoverageEvent(
            subject=subject,
            queried_nodes=queried,
            returned_materials=int(match.group("returned")),
            nodes_with_material=with_material,
            nodes_without_material=without_material,
            empty_node_ids=empty_ids,
        ),
        False,
    )


def _is_log_file(path: Path) -> bool:
    name = path.name.lower()
    return name.endswith(LOG_SUFFIXES) or "logcat" in name


def iter_input_files(paths: Sequence[str]) -> Iterator[Path]:
    """展开输入；目录递归收集 `.log/.txt/.logcat/*logcat*`，显式文件不筛后缀。"""
    for raw in paths:
        if raw == "-":
            continue
        path = Path(raw)
        if path.is_dir():
            for candidate in sorted(path.rglob("*")):
                if candidate.is_file() and _is_log_file(candidate):
                    yield candidate
        elif path.is_file():
            yield path
        else:
            raise FileNotFoundError(raw)


def iter_lines(paths: Sequence[str], stdin: TextIO | None = None) -> Iterator[str]:
    """逐行产出输入文本；`-` 表示 stdin。"""
    stdin = stdin if stdin is not None else sys.stdin
    for path in iter_input_files(paths):
        with path.open("r", encoding="utf-8", errors="replace") as handle:
            for line in handle:
                yield line.rstrip("\r\n")
    if "-" in paths:
        for line in stdin:
            yield line.rstrip("\r\n")


def build_report(paths: Sequence[str], stdin: TextIO | None = None) -> Report:
    report = Report()
    report.files_scanned = len(list(iter_input_files(paths)))
    for line in iter_lines(paths, stdin):
        report.lines_scanned += 1
        event, malformed = parse_line(line)
        if malformed:
            report.malformed_lines += 1
        elif event is not None:
            report.subjects.setdefault(event.subject, SubjectTotals()).add(event)
    return report


def _pct(value: float | None) -> str:
    return "n/a" if value is None else f"{value * 100:.2f}%"


def render_text(report: Report, inputs: Sequence[str]) -> str:
    lines = [
        "材料覆盖率报告（v=1 metric=material_coverage）",
        f"输入: {', '.join(inputs)}",
        f"扫描文件: {report.files_scanned}",
        f"读取行: {report.lines_scanned}",
        f"匹配事件: {report.event_count}",
        f"格式错误行: {report.malformed_lines}",
        "",
    ]
    if not report.subjects:
        lines.append("WARNING: 0 行匹配；没有可聚合的材料覆盖事实。")
        lines.append("  检查日志是否含 `v=1 metric=material_coverage`（tag=kbobs），")
        lines.append("  或批次是否在设备上真正触发过材料取数。")
        return "\n".join(lines) + "\n"

    header = (
        f"{'subject':<12} {'events':>6} {'queried':>8} {'returned':>8} "
        f"{'withMat':>8} {'withoutMat':>10} {'coverage':>9} {'emptyRate':>9}"
    )
    lines.append(header)
    lines.append("-" * len(header))
    for subject in sorted(report.subjects):
        totals = report.subjects[subject]
        lines.append(
            f"{subject:<12} {totals.events:>6} {totals.queried_nodes:>8} "
            f"{totals.returned_materials:>8} {totals.nodes_with_material:>8} "
            f"{totals.nodes_without_material:>10} {_pct(totals.coverage):>9} "
            f"{_pct(totals.empty_material_node_rate):>9}"
        )
    totals = report.totals()
    lines.append("-" * len(header))
    lines.append(
        f"{'TOTAL':<12} {totals.events:>6} {totals.queried_nodes:>8} "
        f"{totals.returned_materials:>8} {totals.nodes_with_material:>8} "
        f"{totals.nodes_without_material:>10} {_pct(totals.coverage):>9} "
        f"{_pct(totals.empty_material_node_rate):>9}"
    )
    lines.append("")
    lines.append("口径: 按事件累加；coverage=nodesWithMaterial/queriedNodes，")
    lines.append("      emptyRate=nodesWithoutMaterial/queriedNodes。")
    return "\n".join(lines) + "\n"


def render_json(report: Report, inputs: Sequence[str]) -> str:
    payload = {
        "metric": "material_coverage",
        "version": 1,
        "inputs": list(inputs),
        "filesScanned": report.files_scanned,
        "linesScanned": report.lines_scanned,
        "matchedEvents": report.event_count,
        "malformedLines": report.malformed_lines,
        "subjects": {
            subject: {
                "events": totals.events,
                "queriedNodes": totals.queried_nodes,
                "returnedMaterials": totals.returned_materials,
                "nodesWithMaterial": totals.nodes_with_material,
                "nodesWithoutMaterial": totals.nodes_without_material,
                "coverage": totals.coverage,
                "emptyMaterialNodeRate": totals.empty_material_node_rate,
            }
            for subject, totals in sorted(report.subjects.items())
        },
    }
    totals = report.totals()
    payload["totals"] = {
        "events": totals.events,
        "queriedNodes": totals.queried_nodes,
        "returnedMaterials": totals.returned_materials,
        "nodesWithMaterial": totals.nodes_with_material,
        "nodesWithoutMaterial": totals.nodes_without_material,
        "coverage": totals.coverage,
        "emptyMaterialNodeRate": totals.empty_material_node_rate,
    }
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def main(argv: Sequence[str] | None = None, stdin: TextIO | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="把 material_coverage 日志行聚合成 per-subject 覆盖率报告（只读）。",
    )
    parser.add_argument(
        "paths",
        nargs="+",
        help="日志文件/目录/logcat 转储，或 `-` 从 stdin 读取。",
    )
    parser.add_argument("--json", action="store_true", help="输出 JSON 而不是表格。")
    parser.add_argument(
        "--require-events",
        action="store_true",
        help="0 个匹配事件时以退出码 1 失败（默认仍输出报告并返回 0）。",
    )
    args = parser.parse_args(argv)

    try:
        report = build_report(args.paths, stdin=stdin)
    except FileNotFoundError as error:
        print(f"输入不存在: {error}", file=sys.stderr)
        return 2

    output = render_json(report, args.paths) if args.json else render_text(report, args.paths)
    sys.stdout.write(output)
    if args.require_events and report.event_count == 0:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
