#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KD-76 主口径聚合：从 20 份单轮 XML 里取四阶段时间戳，算 `M = S-bind + S-warmup`。

批次 41 / WS3 item 21（计划 §2:53）。配套 `tools/ci/run_kd76_rounds.sh`
（每轮一次独立 `am instrument`，逐轮落 `round-NN.xml`）。

## 它消灭的具体失败

批次 40 的 KD-76 复核（记录 §8 #6、§9.1-2）指出：校准套件只有"单进程 20 轮"入口，
设备轨没有主口径 M 值；而"单进程 20 轮"不能冒充主口径。单轮入口落地后，如果汇总仍靠
人工读日志，就会出现三种已知失败：

1. **把单进程 20 轮当 20 个独立样本**：本脚本只读逐轮 XML，`round` 必须是 1..20
   各恰好一份；缺轮/重复一律 NOT EVALUABLE。每轮还必须有 `bootCount` 且 20 个值
   互不相同（证明每轮来自一次新 boot），缺失/重复同样 NOT EVALUABLE。
2. **G1 红被 p95 吸收**：任一轮 `status=FAIL`/`INVALID`、XML 带 `<failure>`/`<error>`、
   或某轮没有 SAMPLE 行，都直接 NOT EVALUABLE——**绝不输出 PASS 判词**。
3. **跨快照/跨设备合并**：20 轮的 `apkSha256` 或设备指纹不一致时 NOT EVALUABLE
   （设备绿必须绑定同一源快照 + 同一 APK sha）；某一轮没记 sha 时该轮标 INVALID
   并计入 n，绝不从集合里剔除。
4. **同一 boot 重复 / 假 reboot**：`KD76_ENV` 的宿主 `bootIdBefore/bootIdAfter`
   必须每轮非空、轮前轮后相同、20 轮互不相同；缺失/变化/重复一律该轮 INVALID 或
   整批 NOT EVALUABLE。app `bootCount` 只作交叉核对；`mode=force-stop` 不是主口径。
   测量前 `:visual_js` 已存在（`pidWorker` 非空）同样不是冷启动。

## 统计口径（R2 `docs/research/2026-11-01-kd76-stats.md` §2.1/§2.3/§3）

- p95 = `sorted[ceil(0.95*n)-1]`（nearest-rank，与 Kotlin 门同式；**不得**换插值）。
- n=20 时 p95 = 第 19/20 个（容忍一个最大值）；n<20 一律 NOT EVALUABLE。
- 门 = `READY_TIMEOUT_MILLIS / 2` = 5 000 ms（`VisualJsClientEngine.READY_TIMEOUT_MILLIS`
  在 androidTest 里被反射钉住 10 000；本脚本镜像同一派生值，不提供放宽开关）。
- 失败轮是删失样本（值 ≥ 预算），不是缺失值；不删点、不缩尾、不重跑。

## 退出码 / 输出

- 0：n=20、G1 绿、M.p95 ≤ gate  → `G2: PASS`
- 1：n=20、G1 绿、M.p95 > gate  → `G2: FAIL`
- 2：n<20 / G1 红 / 快照不一致 / 输入无法解析 → `G2: NOT EVALUABLE`（不出现 PASS 字样）

文本输出直接可贴证据；`--json` 给机器消费。只读，不写文件。

用法：
    python -I tools/ci/summarize_kd76.py build/kd76-runs/<run-dir>
    python -I tools/ci/summarize_kd76.py round-01.xml round-02.xml ...
    python -I tools/ci/summarize_kd76.py <dir> --json
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass, field, replace
from pathlib import Path

MAIN_ROUNDS = 20
READY_TIMEOUT_MILLIS = 10_000
GATE_MILLIS = READY_TIMEOUT_MILLIS // 2  # 5_000，派生而非另写一个数

SAMPLE_LINE = re.compile(
    r"KD76_SAMPLE\s+round=(\d+)\s+status=(SAMPLE|FAIL|INVALID)\b(.*)$", re.MULTILINE
)
META_LINE = re.compile(r"KD76_META\s+round=(\d+)\b(.*)$", re.MULTILINE)
ROUND_LINE = re.compile(r"KD76_ROUND\s+round=(\d+)\b(.*)$", re.MULTILINE)
# 宿主侧轮次身份：run_kd76_rounds.sh 在 am instrument 前后各读一次
# /proc/sys/kernel/random/boot_id，并记录 host boot_count / pidof / mode。
ENV_LINE = re.compile(r"KD76_ENV\s+round=(\d+)\b(.*)$", re.MULTILINE)
APK_SHA = re.compile(r"\bapkSha256=([0-9a-fA-F]{64})\b")
FINGERPRINT = re.compile(r"\bfingerprint=(\S+)")
BOOT_COUNT = re.compile(r"\bbootCount=(-?\d+)")
BOOT_ID_BEFORE = re.compile(r"\bbootIdBefore=(\S+)")
BOOT_ID_AFTER = re.compile(r"\bbootIdAfter=(\S+)")
HOST_BOOT_COUNT = re.compile(r"\bhostBootCount=(-?\d+)")
MODE_FIELD = re.compile(r"\bmode=([A-Za-z][A-Za-z-]*)")
PID_WORKER = re.compile(r"\bpidWorker=(\S*)")
INT_FIELD = r"\b{name}=(-?\d+)\b"


@dataclass(frozen=True)
class RoundRow:
    """一轮的机器证据。`status != SAMPLE` 时四段为 None。"""

    round: int
    status: str
    bind_ms: int | None = None
    warmup_ms: int | None = None
    ready_gap_ms: int | None = None
    first_byte_ms: int | None = None
    total_ms: int | None = None
    censored_lower_bound_ms: int | None = None
    reason: str = ""
    apk_sha256: str = ""
    fingerprint: str = ""
    boot_count: int | None = None
    # 宿主侧 boot 证明（KD76_ENV）：app 读不到 /proc boot_id，宿主通道是主证明。
    boot_id_before: str = ""
    boot_id_after: str = ""
    host_boot_count: int | None = None
    mode: str = ""
    pid_worker: str = ""
    source: str = ""
    xml_failed: bool = False

    @property
    def main_millis(self) -> int | None:
        if self.bind_ms is None or self.warmup_ms is None:
            return None
        return self.bind_ms + self.warmup_ms


@dataclass
class Report:
    verdict: str  # PASS / FAIL / NOT_EVALUABLE
    evaluable: bool
    n: int
    required_n: int = MAIN_ROUNDS
    gate_millis: int = GATE_MILLIS
    stats: dict[str, object] = field(default_factory=dict)
    g1_reasons: list[str] = field(default_factory=list)
    reasons: list[str] = field(default_factory=list)
    apk_sha256: str = ""
    fingerprints: list[str] = field(default_factory=list)
    boot_counts: list[int] = field(default_factory=list)
    boot_ids: list[str] = field(default_factory=list)
    modes: list[str] = field(default_factory=list)
    rounds: list[dict[str, object]] = field(default_factory=list)


# ---------------------------------------------------------------- XML 读取


def _reject_unsafe_xml(text: str, source: Path) -> None:
    """解析前拒绝 DOCTYPE/ENTITY（不可信输入不得启用实体/外部实体）。"""
    if re.search(r"<!DOCTYPE|<!ENTITY", text, re.IGNORECASE):
        raise ValueError(f"{source}: DOCTYPE/ENTITY is not allowed in instrumentation XML")


def _read_xml(source: Path) -> ET.Element:
    text = source.read_text(encoding="utf-8", errors="replace")
    _reject_unsafe_xml(text, source)
    return ET.fromstring(text)


def _xml_failed(root: ET.Element) -> bool:
    for element in root.iter():
        tag = element.tag.rsplit("}", 1)[-1]
        if tag in ("failure", "error"):
            return True
        if tag == "testsuite":
            for key in ("failures", "errors"):
                try:
                    if int(element.get(key, "0") or "0") > 0:
                        return True
                except ValueError:
                    return True
    return False


def _system_out(root: ET.Element) -> str:
    chunks: list[str] = []
    for element in root.iter():
        if element.tag.rsplit("}", 1)[-1] == "system-out" and element.text:
            chunks.append(element.text)
    return "\n".join(chunks)


def _int_field(text: str, name: str) -> int | None:
    match = re.search(INT_FIELD.format(name=re.escape(name)), text)
    return int(match.group(1)) if match else None


def parse_round_file(source: Path) -> tuple[RoundRow | None, list[str]]:
    """解析一份 round XML；返回 (row, problems)。一行 SAMPLE 都没有时 row 为 None。"""
    problems: list[str] = []
    try:
        root = _read_xml(source)
    except (OSError, ET.ParseError, ValueError) as error:
        return None, [f"{source.name}: {error}"]

    xml_failed = _xml_failed(root)
    text = _system_out(root)

    meta: dict[int, tuple[str, str, int | None]] = {}
    for match in META_LINE.finditer(text):
        round_no = int(match.group(1))
        payload = match.group(2)
        sha_match = APK_SHA.search(payload)
        fp_match = FINGERPRINT.search(payload)
        boot_match = BOOT_COUNT.search(payload)
        meta[round_no] = (
            sha_match.group(1).lower() if sha_match else "",
            fp_match.group(1) if fp_match else "",
            int(boot_match.group(1)) if boot_match else None,
        )

    boot_by_round: dict[int, int] = {}
    for match in ROUND_LINE.finditer(text):
        boot_match = BOOT_COUNT.search(match.group(2))
        if boot_match:
            boot_by_round[int(match.group(1))] = int(boot_match.group(1))

    # 宿主 KD76_ENV：缺行时全部字段为空，由 aggregate 判该轮 INVALID。
    env_by_round: dict[int, tuple[str, str, int | None, str, str]] = {}
    for match in ENV_LINE.finditer(text):
        round_no = int(match.group(1))
        payload = match.group(2)
        before = BOOT_ID_BEFORE.search(payload)
        after = BOOT_ID_AFTER.search(payload)
        host_boot = HOST_BOOT_COUNT.search(payload)
        mode = MODE_FIELD.search(payload)
        worker = PID_WORKER.search(payload)
        env_by_round[round_no] = (
            before.group(1) if before else "",
            after.group(1) if after else "",
            int(host_boot.group(1)) if host_boot else None,
            mode.group(1) if mode else "",
            worker.group(1) if worker else "",
        )

    rows: dict[int, RoundRow] = {}
    sample_counts: dict[int, int] = {}
    # 同一轮里逐字相同的 (status, payload) 是同一观测：androidTest 的 `emit()` 同时写
    # Log.i 与 System.out，两条都会进 `<system-out>`。只折叠**完全相同**的行；内容不同
    # 的重复（先 FAIL 后 SAMPLE）仍逐条计入，下面的防假绿检查照常拒绝。
    seen_samples: dict[int, set[tuple[str, str]]] = {}
    for match in SAMPLE_LINE.finditer(text):
        round_no = int(match.group(1))
        status = match.group(2)
        payload = match.group(3)
        sample_key = (status, payload.strip())
        seen = seen_samples.setdefault(round_no, set())
        if sample_key in seen:
            continue
        seen.add(sample_key)
        sample_counts[round_no] = sample_counts.get(round_no, 0) + 1
        sha, fingerprint, boot = meta.get(round_no, ("", "", boot_by_round.get(round_no)))
        if boot is None:
            boot = boot_by_round.get(round_no)
        boot_before, boot_after, host_boot, mode, pid_worker = env_by_round.get(
            round_no, ("", "", None, "", "")
        )

        if status == "SAMPLE":
            bind = _int_field(payload, "bind")
            warmup = _int_field(payload, "warmup")
            ready_gap = _int_field(payload, "readyGap")
            first_byte = _int_field(payload, "firstByte")
            total = _int_field(payload, "total")
            if None in (bind, warmup, ready_gap, first_byte, total):
                rows[round_no] = RoundRow(
                    round=round_no,
                    status="INVALID",
                    reason="SAMPLE line is missing one of bind/warmup/readyGap/firstByte/total",
                    apk_sha256=sha,
                    fingerprint=fingerprint,
                    boot_count=boot,
                    boot_id_before=boot_before,
                    boot_id_after=boot_after,
                    host_boot_count=host_boot,
                    mode=mode,
                    pid_worker=pid_worker,
                    source=source.name,
                    xml_failed=xml_failed,
                )
                continue
            if total != bind + warmup + ready_gap + first_byte:
                rows[round_no] = RoundRow(
                    round=round_no,
                    status="INVALID",
                    reason=(
                        f"total({total}) != bind({bind}) + warmup({warmup}) + "
                        f"readyGap({ready_gap}) + firstByte({first_byte})"
                    ),
                    apk_sha256=sha,
                    fingerprint=fingerprint,
                    boot_count=boot,
                    boot_id_before=boot_before,
                    boot_id_after=boot_after,
                    host_boot_count=host_boot,
                    mode=mode,
                    pid_worker=pid_worker,
                    source=source.name,
                    xml_failed=xml_failed,
                )
                continue
            rows[round_no] = RoundRow(
                round=round_no,
                status="SAMPLE",
                bind_ms=bind,
                warmup_ms=warmup,
                ready_gap_ms=ready_gap,
                first_byte_ms=first_byte,
                total_ms=total,
                apk_sha256=sha,
                fingerprint=fingerprint,
                boot_count=boot,
                boot_id_before=boot_before,
                boot_id_after=boot_after,
                host_boot_count=host_boot,
                mode=mode,
                pid_worker=pid_worker,
                source=source.name,
                xml_failed=xml_failed,
            )
        else:
            reason_match = re.search(r"\breason=(.*)$", payload)
            censor = _int_field(payload, "censoredLowerBoundMillis")
            rows[round_no] = RoundRow(
                round=round_no,
                status=status,
                censored_lower_bound_ms=censor,
                reason=(reason_match.group(1).strip() if reason_match else ""),
                apk_sha256=sha,
                fingerprint=fingerprint,
                boot_count=boot,
                boot_id_before=boot_before,
                boot_id_after=boot_after,
                host_boot_count=host_boot,
                mode=mode,
                pid_worker=pid_worker,
                source=source.name,
                xml_failed=xml_failed,
            )

    if not rows:
        problems.append(f"{source.name}: no KD76_SAMPLE line in <system-out>")
        return None, problems
    # 同一轮出现多条**内容不同**的终态行时，dict 的"后者覆盖前者"会让后来追加的 SAMPLE
    # 洗掉先前的 FAIL（重跑/拼接日志即可制造假绿）。每轮只允许一条终态行；内容不同的重复
    # 一律记问题。逐字相同的重复（Log.i + System.out 双写）已在上面的 `seen_samples` 折叠。
    for round_no, count in sorted(sample_counts.items()):
        if count > 1:
            problems.append(
                f"{source.name}: round {round_no} has {count} KD76_SAMPLE lines "
                "(one terminal sample per round; a later line must not overwrite an earlier failure)"
            )
    if len(rows) > 1:
        problems.append(
            f"{source.name}: {len(rows)} distinct rounds in one file "
            "(one round per am instrument execution; refusing to merge)"
        )
    return next(iter(rows.values())), problems


# ---------------------------------------------------------------- 统计


def percentile_nearest_rank(sorted_values: list[int], percent: int) -> int:
    """`sorted[ceil(n*p/100)-1]`；与 Kotlin 门同式，值一定是观测样本。"""
    if not sorted_values:
        raise ValueError("a percentile needs at least one sample")
    index = math.ceil(len(sorted_values) * percent / 100) - 1
    return sorted_values[min(max(index, 0), len(sorted_values) - 1)]


def describe(values: list[int]) -> dict[str, object]:
    sorted_values = sorted(values)
    return {
        "n": len(sorted_values),
        "min": sorted_values[0],
        "p50": percentile_nearest_rank(sorted_values, 50),
        "p90": percentile_nearest_rank(sorted_values, 90),
        "p95": percentile_nearest_rank(sorted_values, 95),
        "max": sorted_values[-1],
        "sorted": sorted_values,
    }


# ---------------------------------------------------------------- 聚合


def collect_xml_paths(targets: list[str]) -> list[Path]:
    paths: list[Path] = []
    for target in targets:
        path = Path(target)
        if path.is_dir():
            paths.extend(sorted(path.glob("round-*.xml")))
        elif path.is_file():
            paths.append(path)
        else:
            raise FileNotFoundError(f"input not found: {target}")
    # 去重但保留顺序
    seen: set[Path] = set()
    unique: list[Path] = []
    for path in paths:
        resolved = path.resolve()
        if resolved not in seen:
            seen.add(resolved)
            unique.append(path)
    return unique


def aggregate(paths: list[Path]) -> Report:
    reasons: list[str] = []
    rows: list[RoundRow] = []
    for path in paths:
        row, problems = parse_round_file(path)
        reasons.extend(problems)
        if row is not None:
            rows.append(row)

    by_round: dict[int, list[RoundRow]] = {}
    for row in rows:
        by_round.setdefault(row.round, []).append(row)

    duplicates = sorted(round_no for round_no, items in by_round.items() if len(items) > 1)
    if duplicates:
        reasons.append(f"duplicate round evidence: {duplicates}; each round must appear once")

    selected: list[RoundRow] = []
    for round_no in sorted(by_round):
        # 同一轮的重复证据若完全一致，仍按重复处理（上面的 reasons 已判 NOT EVALUABLE）；
        # 这里只保留一条，便于报告展示。
        row = by_round[round_no][0]
        # F10：per-round APK sha 是这一轮的设备身份。缺失时**不得**从 sha 集合里剔除后
        # 让其余轮投票出一个"一致 sha"——那会把 19 轮 PASS 当成 20 轮 PASS。把该轮标
        # INVALID 并保留在样本统计里（n 因此 < 20）。
        if row.status == "SAMPLE" and not row.apk_sha256:
            row = replace(
                row,
                status="INVALID",
                reason="missing per-round apkSha256",
            )
        selected.append(row)

    # F10 后继：宿主 boot_id 是"每轮一次新 boot"的主证明（app 读不到 /proc，宿主
    # 通道是唯一主口径）。缺失、轮中变化、测量前 worker 已存在都不是可剔除的字段，
    # 而是该轮 INVALID——保留槽位、n 减 1，绝不由其余轮代表。
    missing_host_boot: list[int] = []
    changed_host_boot: list[int] = []
    hot_worker: list[int] = []
    extra_reasons: dict[int, list[str]] = {}
    for row in selected:
        if not row.boot_id_before or not row.boot_id_after:
            missing_host_boot.append(row.round)
            extra_reasons.setdefault(row.round, []).append(
                "missing host boot_id (boot proof)"
            )
        elif row.boot_id_before != row.boot_id_after:
            changed_host_boot.append(row.round)
            extra_reasons.setdefault(row.round, []).append(
                f"host boot_id changed during round {row.round} "
                f"({row.boot_id_before} != {row.boot_id_after})"
            )
        if row.pid_worker:
            hot_worker.append(row.round)
            extra_reasons.setdefault(row.round, []).append(
                f"hot worker process already running before measurement "
                f"(pidWorker={row.pid_worker}; not a cold start)"
            )
    if extra_reasons:
        selected = [
            replace(row, status="INVALID", reason="; ".join(extra_reasons[row.round]))
            if row.round in extra_reasons
            else row
            for row in selected
        ]

    if missing_host_boot:
        reasons.append(
            f"rounds without host boot_id: {sorted(missing_host_boot)} "
            "(each round must prove its boot identity from the host)"
        )

    host_boot_ids = sorted(
        row.boot_id_before for row in selected if row.boot_id_before
    )
    repeated_host_boot = sorted(
        {value for value in host_boot_ids if host_boot_ids.count(value) > 1}
    )
    if repeated_host_boot:
        reasons.append(
            f"repeated host boot_id across rounds: {repeated_host_boot} "
            "(rounds must come from distinct reboots, not one boot repeated)"
        )

    modes = sorted({row.mode for row in selected if row.mode})
    non_reboot_modes = [mode for mode in modes if mode != "reboot"]
    if non_reboot_modes:
        reasons.append(
            f"mode={','.join(non_reboot_modes)} is not reboot; "
            "the 20-round cold-start criterion requires per-round reboot"
        )

    host_count_mismatches = [
        f"round {row.round}: app bootCount={row.boot_count} != host bootCount={row.host_boot_count}"
        for row in selected
        if row.boot_count is not None
        and row.host_boot_count is not None
        and row.boot_count != row.host_boot_count
    ]
    if host_count_mismatches:
        reasons.append(
            "host bootCount does not match app bootCount: "
            + "; ".join(host_count_mismatches)
        )

    present = {row.round for row in selected}
    expected = set(range(1, MAIN_ROUNDS + 1))
    missing = sorted(expected - present)
    unexpected = sorted(present - expected)
    if missing:
        reasons.append(f"missing rounds: {missing} (expected exactly 1..{MAIN_ROUNDS})")
    if unexpected:
        reasons.append(f"unexpected round ids: {unexpected} (expected 1..{MAIN_ROUNDS})")

    g1_reasons: list[str] = []
    for row in selected:
        if row.xml_failed:
            g1_reasons.append(f"round {row.round}: XML carries a <failure>/<error> element")
        if row.status == "FAIL":
            bound = row.censored_lower_bound_ms if row.censored_lower_bound_ms is not None else "?"
            g1_reasons.append(
                f"round {row.round}: {row.status} (censored >= {bound} ms): {row.reason}"
            )
        elif row.status == "INVALID":
            g1_reasons.append(f"round {row.round}: INVALID: {row.reason}")
        elif row.status != "SAMPLE":
            g1_reasons.append(f"round {row.round}: unknown status {row.status!r}")

    samples = [row for row in selected if row.status == "SAMPLE" and row.main_millis is not None]
    n = len(samples)

    shas = sorted({row.apk_sha256 for row in selected if row.apk_sha256})
    fingerprints = sorted({row.fingerprint for row in selected if row.fingerprint})
    boot_counts = sorted(row.boot_count for row in selected if row.boot_count is not None)
    if len(shas) > 1:
        reasons.append(f"mixed APK sha256 across rounds: {shas}")
    elif not shas:
        reasons.append("no apkSha256 recorded by any round (device green must bind an APK sha)")

    # F10：bootCount 门。20 轮主口径要求每轮来自一次新 boot（reboot 模式）；缺失或重复
    # 都无法证明"20 次独立冷启动"，不能只看 sampleCount==20。
    boot_missing = [row.round for row in selected if row.boot_count is None]
    if boot_missing:
        reasons.append(
            f"rounds without bootCount: {boot_missing} "
            "(each round must prove its boot identity)"
        )
    repeated_boots = sorted({
        value for value in boot_counts if boot_counts.count(value) > 1
    })
    if repeated_boots:
        reasons.append(
            f"repeated bootCount across rounds: {repeated_boots} "
            "(rounds must come from distinct boots, not one process/boot repeated)"
        )
    if len(fingerprints) > 1:
        reasons.append(f"mixed device fingerprints across rounds: {fingerprints}")
    elif not fingerprints:
        reasons.append("no device fingerprint recorded by any round")

    evaluable = not reasons and not g1_reasons and n == MAIN_ROUNDS

    report = Report(
        verdict="NOT_EVALUABLE",
        evaluable=evaluable,
        n=n,
        reasons=reasons,
        g1_reasons=g1_reasons,
        apk_sha256=shas[0] if len(shas) == 1 else "",
        fingerprints=fingerprints,
        boot_counts=boot_counts,
        boot_ids=host_boot_ids,
        modes=modes,
        rounds=[asdict(row) for row in selected],
    )

    if not evaluable:
        return report

    stats = describe([int(row.main_millis) for row in samples])
    report.stats = stats
    report.verdict = "PASS" if int(stats["p95"]) <= GATE_MILLIS else "FAIL"
    return report


# ---------------------------------------------------------------- 渲染


def render_text(report: Report) -> str:
    lines: list[str] = []
    lines.append(
        "KD-76 main criterion M = per-round (S-bind + S-warmup), nearest-rank p95 "
        f"(n must be {MAIN_ROUNDS})"
    )
    lines.append(
        f"samples: n={report.n}/{report.required_n} | "
        f"G1: {'green' if not report.g1_reasons else 'RED'}"
    )
    if report.apk_sha256:
        lines.append(f"apkSha256: {report.apk_sha256}")
    if report.fingerprints:
        lines.append(f"device fingerprint(s): {', '.join(report.fingerprints)}")
    if report.boot_counts:
        lines.append(f"bootCount(s): {report.boot_counts}")
    if report.boot_ids:
        unique = len(set(report.boot_ids))
        lines.append(f"host boot_id(s): n={len(report.boot_ids)} unique={unique}")
    if report.modes:
        lines.append(f"mode(s): {report.modes}")

    if report.evaluable:
        stats = report.stats
        lines.append(
            "M: "
            f"min={stats['min']} p50={stats['p50']} p90={stats['p90']} "
            f"p95={stats['p95']} max={stats['max']} "
            f"sorted={stats['sorted']}"
        )
        lines.append(
            f"gate: M.p95 <= {GATE_MILLIS} ms "
            f"(READY_TIMEOUT {READY_TIMEOUT_MILLIS} / 2; never relaxed)"
        )
        if report.verdict == "PASS":
            lines.append(f"G2: PASS (M.p95={stats['p95']} ms <= {GATE_MILLIS} ms)")
        else:
            lines.append(f"G2: FAIL (M.p95={stats['p95']} ms > {GATE_MILLIS} ms)")
        lines.append(
            f"G3: max={stats['max']} ms (attribution required if max > {GATE_MILLIS} ms)"
        )
        return "\n".join(lines)

    snapshot_ok = not any(
        "mixed APK" in reason or "mixed device" in reason or "no apkSha256" in reason
        or "no device fingerprint" in reason or "bootCount" in reason
        for reason in report.reasons
    )
    lines.append(
        f"G2: NOT EVALUABLE (n={report.n}/{report.required_n}; "
        f"G1 {'RED' if report.g1_reasons else 'green'}; "
        f"snapshot {'consistent' if snapshot_ok else 'INCONSISTENT'}; "
        f"input {'complete' if not report.reasons else 'incomplete'})"
    )
    for reason in report.reasons:
        lines.append(f"  - {reason}")
    for reason in report.g1_reasons:
        lines.append(f"  - G1: {reason}")
    lines.append(
        "  no verdict may be derived from this run; do not quote a p95 or a gate result"
    )
    return "\n".join(lines)


# ---------------------------------------------------------------- CLI


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Aggregate KD-76 single-round XML evidence into the main criterion M.",
    )
    parser.add_argument("inputs", nargs="+", help="run directory or round-*.xml files")
    parser.add_argument("--json", action="store_true", help="machine-readable report")
    args = parser.parse_args(argv)

    try:
        paths = collect_xml_paths(args.inputs)
    except FileNotFoundError as error:
        print(f"summarize_kd76: {error}", file=sys.stderr)
        return 2
    if not paths:
        print("summarize_kd76: no round-*.xml files found", file=sys.stderr)
        return 2

    report = aggregate(paths)
    if args.json:
        print(json.dumps(asdict(report), ensure_ascii=False, indent=2))
    else:
        print(render_text(report))

    if not report.evaluable:
        return 2
    return 0 if report.verdict == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
