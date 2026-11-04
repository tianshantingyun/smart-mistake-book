#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KD-76 A6 合成判词：两批**独立完整** n=20 的合取，不是 n=40。

批次 43 / WS2 item 12（计划 `.zcode/plans/plan-2026-11-05-batch-43.md:39-40`；
协议 `docs/research/2026-11-05-kd76-a6-protocol.md` §3/§5）。

## 它消灭的具体失败

1. **把两次 n=20 当 n=40**（批次 42 的两次单批就是这样被读的）：本工具只做**合取**，
   不重算合并分位数、不平均、不相加；输出里没有 n=40、没有 combined p95。
2. **同 HEAD 不同构建冒充"同一构建"**（批次 42：app sha `35e1…` vs `ec13…`）：
   I1 只信宿主 `pm path` 拉包算出的 `installedAppSha256`/`installedTestSha256`，
   并要求两批与预注册 `a6-plan.txt` 三方相等。
3. **换设备/换模拟器实例、两批之间重装同字节包**：I2/I3 用 `deviceGenerationId` /
   `installGenerationId`（含 firstInstallTime/lastUpdateTime）比对。
4. **两批并行/背靠背/切段冒充两次独立**：I5 用逐轮墙钟窗口 + `A.finished < B.started` +
   预注册 `minSeparationMinutes`（硬下限 10）。
5. **同一 boot 内 force-stop 冒充 reboot**：I6 要求 40 个宿主 `boot_id` 两两不同、
   A ∩ B = ∅、每轮 before==after、`bootCount` 严格递增且 B.min > A.max。
6. **复制 A 批当 B 批 / 手写最小 XML**：I9 要求两批 `runId` 不同、20 对同号文件不得
   全同、`.am.txt` 非空、XML 含 `--- am instrument output ---` 与 `OK (n test)`，
   并重算 manifest 里的逐文件哈希链。
7. **条件漂移（一批在构建风暴、一批空载）**：I8 用批次级 M 中位数比与
   `la1/nproc` 中位数比各 ≤ 2×。
8. **改门/改样本量/改估计量**：门与 nearest-rank 直接复用 `summarize_kd76` 的常量与
   函数，本文件不另写一个数；固定向量由 `tools/tests/test_kd76_a6_verify.py` 钉住。

## 判词（协议 §5.3 的穷举表）

| batchA | batchB | I0–I10 | 判词 | exit |
|---|---|---|---|---|
| PASS | PASS | 全绿 | A6: CONFIRMED | 0 |
| FAIL/PASS 任一 | 可出 p95 | 全绿 | A6: REFUTED | 1 |
| 任一批 NOT_EVALUABLE 或 I0–I10 任一红 | | | A6: NOT EVALUABLE | 2 |

单轮 FAIL/INVALID/缺轮 → 批级 NOT_EVALUABLE → A6 NOT EVALUABLE（不是 REFUTED）；
若失败轮归因产品，台账必须另记真红，不因 A6 NOT_EVALUABLE 消失。

## 边界（必须如实）

- 只读 manifest / round XML / `.am.txt`；不重新跑设备、不改门、不修复证据。
- 哈希链只防"局部改坏"，防不了"全量重造"（协议 §7.2 的 L3/L4）；本工具**不声称**
  能证明没有造假。
- A6 CONFIRMED 不等于 KD-76 关闭：C4（死亡确认段）、29.5 s 链、产品进程首请求、arm64/真机
  仍是独立条件。

用法：
    python -I tools/ci/kd76_a6_verify.py --plan <a6-plan.txt> \
        --batch-a build/kd76-runs/a6/batchA --batch-b build/kd76-runs/a6/batchB
    python -I tools/ci/kd76_a6_verify.py ... --json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import statistics
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from summarize_kd76 import (  # noqa: E402
    GATE_MILLIS,
    MAIN_ROUNDS,
    _read_xml,  # 复用已带 DOCTYPE/ENTITY 拒绝的解析入口（同一不可信输入边界）
    aggregate,
    collect_xml_paths,
    percentile_nearest_rank,
)

PLAN_VERSION = 1
MIN_SEPARATION_FLOOR_MINUTES = 10
RECOMMENDED_SEPARATION_MINUTES = 30
MAX_REPLACEMENT_BATCHES = 1
WARMUP_RATIO_LIMIT = 2.0
LOAD_RATIO_LIMIT = 2.0
MAX_ROUND_XML_BYTES = 8 * 1024 * 1024

PLAN_REQUIRED = {
    "a6PlanVersion": str(PLAN_VERSION),
    "requiredBatches": "2",
    "roundsPerBatch": str(MAIN_ROUNDS),
    "estimator": "nearest-rank",
    "gateMillis": str(GATE_MILLIS),
    "mode": "reboot",
    "settleSeconds": "0",
    "allowReinstall": "false",
}
PLAN_SHA_FIELDS = (
    "expectedAppApkSha256",
    "expectedTestApkSha256",
    "expectedDeviceGenerationId",
    "expectedInstallGenerationId",
)
PROTOCOL_FIELDS = (
    "mode",
    "settleSeconds",
    "rounds",
    "firstRound",
    "lastRound",
    "testClass",
    "testMethod",
    "component",
    "gateMillis",
    "estimator",
)

UTC_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")
GENERATION_RE = re.compile(r"\bgeneration=(-?\d+)")


# ---------------------------------------------------------------- 读取


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_kv(path: Path) -> dict[str, str]:
    fields: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not line or "=" not in line:
            continue
        key, value = line.split("=", 1)
        fields[key.strip()] = value.strip()
    return fields


def parse_utc(text: str) -> datetime | None:
    if not text or not UTC_RE.match(text):
        return None
    try:
        return datetime.strptime(text, "%Y-%m-%dT%H:%M:%SZ")
    except ValueError:
        return None


def _system_out(path: Path) -> str:
    """读 `<system-out>` 文本；沿用 summarize_kd76 的不可信 XML 边界。

    `_read_xml` 在解析前拒绝 `<!DOCTYPE`/`<!ENTITY`（实体扩展类输入），本函数另加
    文件大小上限，避免把超大文件整体读进内存。
    """
    try:
        if path.stat().st_size > MAX_ROUND_XML_BYTES:
            return ""
        root = _read_xml(path)
    except (OSError, ValueError, Exception):  # 解析失败等于没有可读证据
        return ""
    chunks: list[str] = []
    for element in root.iter():
        if element.tag.rsplit("}", 1)[-1] == "system-out" and element.text:
            chunks.append(element.text)
    return "\n".join(chunks)


def _env_field(text: str, name: str) -> str:
    match = re.search(rf"\b{re.escape(name)}=(\S*)", text)
    return match.group(1) if match else ""


@dataclass
class RoundFacts:
    round: int
    boot_id_before: str
    boot_id_after: str
    boot_count: int | None
    pid_worker: str
    pid_worker_after: str
    pid_target: str
    warmup_finished: int | None
    warmup_failed: int | None
    generation: str
    started: datetime | None
    finished: datetime | None
    screen_awake: str
    load_before: str
    la1: float | None
    nproc: int | None
    sample_main_ms: int | None
    warmup_ms: int | None
    status: str


@dataclass
class BatchFacts:
    directory: Path
    label: str
    manifest: dict[str, str]
    report: object
    rounds: list[RoundFacts] = field(default_factory=list)
    problems: list[str] = field(default_factory=list)

    @property
    def run_id(self) -> str:
        return self.manifest.get("runId", "")

    @property
    def main_values(self) -> list[int]:
        values: list[int] = []
        for row in self.rounds:
            if row.sample_main_ms is not None and row.status == "SAMPLE":
                values.append(row.sample_main_ms)
        return values

    @property
    def warmup_values(self) -> list[int]:
        return [
            row.warmup_ms
            for row in self.rounds
            if row.warmup_ms is not None and row.status == "SAMPLE"
        ]

    def p95(self) -> int | None:
        values = self.main_values
        if len(values) < MAIN_ROUNDS:
            return None
        return percentile_nearest_rank(sorted(values), 95)

    def max_value(self) -> int | None:
        values = self.main_values
        return max(values) if values else None


def load_batch(directory: Path, label: str) -> BatchFacts:
    manifest_path = directory / "manifest.txt"
    if not manifest_path.is_file():
        raise FileNotFoundError(f"{label}: manifest.txt not found in {directory}")
    manifest = read_kv(manifest_path)
    paths = collect_xml_paths([str(directory)])
    report = aggregate(paths) if paths else None

    rounds: list[RoundFacts] = []
    problems: list[str] = []
    for path in paths:
        text = _system_out(path)
        match = re.search(r"round-(\d+)\.xml$", path.name)
        if not match:
            continue
        name_round = int(match.group(1))
        sample = re.search(
            rf"KD76_SAMPLE\s+round={name_round}\s+status=SAMPLE\b(.*)$", text, re.MULTILINE
        )
        main_ms = None
        warmup_ms = None
        status = "SAMPLE"
        if sample:
            payload = sample.group(1)
            bind = _int(payload, "bind")
            warmup = _int(payload, "warmup")
            warmup_ms = warmup
            if bind is not None and warmup is not None:
                main_ms = bind + warmup
        else:
            other = re.search(rf"KD76_SAMPLE\s+round={name_round}\s+status=(\w+)", text)
            status = other.group(1) if other else "MISSING"

        load_before = _env_field(text, "loadBefore")
        la1_text = _env_field(text, "la1")
        la1 = None
        if la1_text:
            try:
                la1 = float(la1_text)
            except ValueError:
                la1 = None
        elif load_before:
            try:
                la1 = float(load_before.split("_")[0])
            except (ValueError, IndexError):
                la1 = None
        nproc_text = _env_field(text, "nproc")
        nproc = int(nproc_text) if nproc_text.isdigit() else None

        generation_match = GENERATION_RE.search(text)
        rounds.append(
            RoundFacts(
                round=name_round,
                boot_id_before=_env_field(text, "bootIdBefore"),
                boot_id_after=_env_field(text, "bootIdAfter"),
                boot_count=_int(_env_field(text, "hostBootCount")),
                pid_worker=_env_field(text, "pidWorker"),
                pid_worker_after=_env_field(text, "pidWorkerAfter"),
                pid_target=_env_field(text, "pidTarget"),
                warmup_finished=_int(_env_field(text, "warmupFinishedCount")),
                warmup_failed=_int(_env_field(text, "warmupFailedCount")),
                generation=generation_match.group(1) if generation_match else "",
                started=parse_utc(_env_field(text, "roundStartedUtc")),
                finished=parse_utc(_env_field(text, "roundFinishedUtc")),
                screen_awake=_env_field(text, "screenAwake"),
                load_before=load_before,
                la1=la1,
                nproc=nproc,
                sample_main_ms=main_ms,
                warmup_ms=warmup_ms,
                status=status,
            )
        )
        if status != "SAMPLE":
            problems.append(f"{label} round {name_round}: status={status}")

    return BatchFacts(
        directory=directory, label=label, manifest=manifest, report=report, rounds=rounds, problems=problems
    )


def _int(text: str, name: str | None = None) -> int | None:
    if name is None:
        try:
            return int(text)
        except (TypeError, ValueError):
            return None
    match = re.search(rf"\b{re.escape(name)}=(-?\d+)", text)
    return int(match.group(1)) if match else None


# ---------------------------------------------------------------- I0–I10


def _manifest_int(manifest: dict[str, str], key: str) -> int | None:
    text = manifest.get(key, "")
    return int(text) if text.lstrip("-").isdigit() else None


def check_plan(plan: dict[str, str], plan_sha: str, batch_a: BatchFacts, batch_b: BatchFacts) -> list[str]:
    reasons: list[str] = []
    missing = [key for key in PLAN_REQUIRED if key not in plan]
    if missing:
        reasons.append(f"plan_mismatch: missing fields {sorted(missing)}")
    for key, expected in PLAN_REQUIRED.items():
        if key in plan and plan[key] != expected:
            reasons.append(f"plan_mismatch: {key}={plan[key]!r} expected {expected!r}")
    for key in PLAN_SHA_FIELDS:
        value = plan.get(key, "")
        if not re.fullmatch(r"[0-9a-f]{64}", value):
            reasons.append(f"plan_mismatch: {key} is not a 64-hex digest")
    separation_text = plan.get("minSeparationMinutes", "")
    if not separation_text.isdigit():
        reasons.append("plan_mismatch: minSeparationMinutes is required")
    elif int(separation_text) < MIN_SEPARATION_FLOOR_MINUTES:
        reasons.append(
            f"plan_mismatch: minSeparationMinutes={separation_text} < hard floor "
            f"{MIN_SEPARATION_FLOOR_MINUTES}"
        )
    replacement_text = plan.get("maxReplacementBatches", "")
    if not replacement_text.isdigit():
        reasons.append("plan_mismatch: maxReplacementBatches is required")
    elif int(replacement_text) > MAX_REPLACEMENT_BATCHES:
        reasons.append(
            f"plan_mismatch: maxReplacementBatches={replacement_text} > {MAX_REPLACEMENT_BATCHES}"
        )
    for batch in (batch_a, batch_b):
        recorded = batch.manifest.get("a6PlanSha256", "")
        if recorded != plan_sha:
            reasons.append(
                f"plan_mismatch: {batch.label} a6PlanSha256={recorded or '<missing>'} != plan {plan_sha}"
            )
    return reasons


def check_identity(plan: dict[str, str], batch_a: BatchFacts, batch_b: BatchFacts) -> list[str]:
    reasons: list[str] = []

    def compare(field_name: str, expected_key: str, code: str) -> None:
        a = batch_a.manifest.get(field_name, "")
        b = batch_b.manifest.get(field_name, "")
        expected = plan.get(expected_key, "")
        if not a or not b:
            reasons.append(f"{code}: {field_name} missing (A={a or '<missing>'} B={b or '<missing>'})")
        elif a != b:
            reasons.append(f"{code}: {field_name} A={a} != B={b}")
        elif expected and a != expected:
            reasons.append(f"{code}: {field_name} {a} != plan {expected}")

    compare("installedAppSha256", "expectedAppApkSha256", "build_mismatch")
    compare("installedTestSha256", "expectedTestApkSha256", "build_mismatch")
    compare("deviceGenerationId", "expectedDeviceGenerationId", "device_generation_changed")
    compare("installGenerationId", "expectedInstallGenerationId", "install_generation_changed")

    if plan.get("allowReinstall") == "false":
        for key in ("firstInstallTime", "lastUpdateTime"):
            a = batch_a.manifest.get(key, "")
            b = batch_b.manifest.get(key, "")
            if not a or not b:
                reasons.append(f"install_generation_changed: {key} missing")
            elif a != b:
                reasons.append(f"install_generation_changed: {key} A={a} != B={b}")
    return reasons


def check_protocol(batch_a: BatchFacts, batch_b: BatchFacts) -> list[str]:
    reasons: list[str] = []
    for key in PROTOCOL_FIELDS:
        a = batch_a.manifest.get(key, "")
        b = batch_b.manifest.get(key, "")
        if not a or not b:
            reasons.append(f"protocol_mismatch: {key} missing")
        elif a != b:
            reasons.append(f"protocol_mismatch: {key} A={a!r} != B={b!r}")
    if batch_a.manifest.get("mode") != "reboot":
        reasons.append("protocol_mismatch: mode must be reboot")
    if batch_a.manifest.get("settleSeconds", "0") != "0":
        reasons.append("protocol_mismatch: settleSeconds must be 0")
    if _manifest_int(batch_a.manifest, "rounds") != MAIN_ROUNDS:
        reasons.append(f"protocol_mismatch: rounds must be {MAIN_ROUNDS}")
    if _manifest_int(batch_a.manifest, "firstRound") != 1:
        reasons.append("protocol_mismatch: firstRound must be 1")
    return reasons


def _windows(batch: BatchFacts) -> list[tuple[datetime, datetime]]:
    windows: list[tuple[datetime, datetime]] = []
    for row in batch.rounds:
        if row.started and row.finished:
            windows.append((row.started, row.finished))
    return windows


def check_time(plan: dict[str, str], batch_a: BatchFacts, batch_b: BatchFacts) -> tuple[list[str], float | None]:
    reasons: list[str] = []
    a_started = parse_utc(batch_a.manifest.get("startedUtc", ""))
    a_finished = parse_utc(batch_a.manifest.get("finishedUtc", ""))
    b_started = parse_utc(batch_b.manifest.get("startedUtc", ""))
    b_finished = parse_utc(batch_b.manifest.get("finishedUtc", ""))
    if not all((a_started, a_finished, b_started, b_finished)):
        reasons.append("time_overlap: manifest startedUtc/finishedUtc missing or malformed")
        return reasons, None

    if not a_finished < b_started:
        reasons.append(
            f"time_overlap: batchA finished {a_finished:%Y-%m-%dT%H:%M:%SZ} is not before "
            f"batchB started {b_started:%Y-%m-%dT%H:%M:%SZ}"
        )

    windows = _windows(batch_a) + _windows(batch_b)
    if len(windows) != MAIN_ROUNDS * 2:
        reasons.append(
            f"time_overlap: only {len(windows)}/40 round wall-clock windows recorded "
            "(roundStartedUtc/roundFinishedUtc)"
        )
    for started, finished in windows:
        if finished < started:
            reasons.append(f"clock_anomaly: round window finished before it started ({started} > {finished})")
    ordered = sorted(windows)
    for (start1, end1), (start2, _end2) in zip(ordered, ordered[1:]):
        if start2 < end1:
            reasons.append(
                f"time_overlap: round windows overlap ({start1:%H:%M:%S}-{end1:%H:%M:%S} vs "
                f"{start2:%H:%M:%S})"
            )
            break

    observed_gap = (b_started - a_finished).total_seconds() / 60.0
    declared_text = plan.get("minSeparationMinutes", "0")
    declared = int(declared_text) if declared_text.isdigit() else 0
    if declared and observed_gap < declared:
        reasons.append(
            f"separation_too_short: observed gap {observed_gap:.1f} min < declared {declared} min"
        )
    return reasons, observed_gap


def check_boot(batch_a: BatchFacts, batch_b: BatchFacts) -> list[str]:
    reasons: list[str] = []
    rows = batch_a.rounds + batch_b.rounds
    boot_ids = [row.boot_id_before for row in rows]
    if any(not value for value in boot_ids):
        reasons.append("boot_id_repeated: at least one round has no host boot_id")
    unique = len(set(value for value in boot_ids if value))
    if unique != len(rows):
        reasons.append(f"boot_id_repeated: {unique}/{len(rows)} distinct host boot_ids")
    overlap = {row.boot_id_before for row in batch_a.rounds} & {
        row.boot_id_before for row in batch_b.rounds
    }
    if overlap:
        reasons.append(f"boot_id_overlap: {sorted(overlap)} appear in both batches")
    changed = [row.round for row in rows if row.boot_id_before != row.boot_id_after]
    if changed:
        reasons.append(f"boot_id_repeated: boot_id changed during rounds {changed}")

    counts = [row.boot_count for row in rows]
    if any(value is None for value in counts):
        reasons.append("boot_count_mismatch: at least one round has no app bootCount")
    else:
        values = [int(value) for value in counts]
        if len(set(values)) != len(values):
            reasons.append("boot_count_mismatch: repeated bootCount across rounds")
        if any(later <= earlier for earlier, later in zip(values, values[1:])):
            reasons.append(
                f"boot_count_not_increasing: sequence {values} is not strictly increasing"
            )
        a_max = max(
            (row.boot_count for row in batch_a.rounds if row.boot_count is not None),
            default=None,
        )
        b_min = min(
            (row.boot_count for row in batch_b.rounds if row.boot_count is not None),
            default=None,
        )
        if a_max is not None and b_min is not None and b_min <= a_max:
            reasons.append(
                f"boot_count_reset: batchB minimum bootCount {b_min} <= batchA maximum {a_max}"
            )
    return reasons


def check_process(batch_a: BatchFacts, batch_b: BatchFacts) -> list[str]:
    reasons: list[str] = []
    rows = batch_a.rounds + batch_b.rounds
    hot = [row.round for row in rows if row.pid_worker]
    if hot:
        reasons.append(f"worker_hot: :visual_js already running before rounds {hot}")
    hot_after = [row.round for row in rows if row.pid_worker_after]
    if hot_after:
        reasons.append(f"worker_hot: :visual_js running after logcat reset in rounds {hot_after}")
    bits = {bool(row.pid_target) for row in rows}
    if len(bits) > 1:
        reasons.append("process_bit_drift: target process presence differs across rounds")
    # 只有 SAMPLE 轮需要 generation；失败轮（FAIL/INVALID）按 A5 是删失样本，
    # 本来就没有段值，不能因此被说成"冷启动不成立"。
    missing_generation = [row.round for row in rows if row.status == "SAMPLE" and not row.generation]
    if missing_generation:
        reasons.append(f"generation_missing: rounds {missing_generation} carry no generation")
    bad_warmup = [
        row.round for row in rows if row.warmup_finished != 1 or row.warmup_failed not in (0, None)
    ]
    if bad_warmup:
        reasons.append(
            f"warmup_witness_missing: rounds {bad_warmup} do not show exactly one "
            "'visual_js warm-up finished' and zero failures"
        )
    # `dumpsys power` 的 `mWakefulness` 原值是 `Awake`；脚本侧另有布尔归一化写法 `true`。
    awake_values = {"true", "Awake"}
    if any(row.screen_awake not in awake_values for row in rows):
        reasons.append("condition_drift: at least one round was measured with the screen not awake")
    return reasons


def _median(values: list[float]) -> float | None:
    return statistics.median(values) if values else None


def check_condition(batch_a: BatchFacts, batch_b: BatchFacts) -> list[str]:
    reasons: list[str] = []
    warm_a = batch_a.warmup_values
    warm_b = batch_b.warmup_values
    if not warm_a or not warm_b:
        reasons.append("condition_drift: a batch has no samples to compare")
        return reasons
    median_a = _median([float(value) for value in warm_a])
    median_b = _median([float(value) for value in warm_b])
    if median_a and median_b:
        ratio = max(median_a, median_b) / min(median_a, median_b)
        if ratio > WARMUP_RATIO_LIMIT:
            reasons.append(
                f"condition_drift: S-warmup median ratio {ratio:.2f}x > {WARMUP_RATIO_LIMIT}x "
                f"({median_a} vs {median_b})"
            )
    load_a = [row.la1 / row.nproc for row in batch_a.rounds if row.la1 is not None and row.nproc]
    load_b = [row.la1 / row.nproc for row in batch_b.rounds if row.la1 is not None and row.nproc]
    if not load_a or not load_b:
        reasons.append("condition_drift: la1/nproc missing in at least one batch")
    else:
        median_load_a = _median(load_a)
        median_load_b = _median(load_b)
        if median_load_a and median_load_b:
            ratio = max(median_load_a, median_load_b) / min(median_load_a, median_load_b)
            if ratio > LOAD_RATIO_LIMIT:
                reasons.append(
                    f"condition_drift: la1/nproc median ratio {ratio:.2f}x > {LOAD_RATIO_LIMIT}x"
                )
    return reasons


def check_evidence(plan: dict[str, str], batch_a: BatchFacts, batch_b: BatchFacts) -> list[str]:
    reasons: list[str] = []
    for batch in (batch_a, batch_b):
        xml_paths = sorted(batch.directory.glob("round-*.xml"))
        am_paths = sorted(batch.directory.glob("round-*.am.txt"))
        if len(xml_paths) != MAIN_ROUNDS:
            reasons.append(f"raw_output_missing: {batch.label} has {len(xml_paths)}/20 round XML files")
        if len(am_paths) != MAIN_ROUNDS:
            reasons.append(f"raw_output_missing: {batch.label} has {len(am_paths)}/20 .am.txt files")
        empty = [path.name for path in am_paths if path.stat().st_size == 0]
        if empty:
            reasons.append(f"raw_output_missing: {batch.label} empty raw outputs {empty}")
        missing_am_output: list[str] = []
        for path in xml_paths:
            text = _system_out(path)
            if "--- am instrument output ---" not in text or not re.search(
                r"OK \(\d+ test", text
            ):
                missing_am_output.append(path.name)
        if missing_am_output:
            reasons.append(
                f"raw_output_missing: {batch.label} XML without am output/OK line "
                f"{missing_am_output[:5]}"
            )
        chain = {
            key: value
            for key, value in batch.manifest.items()
            if key.endswith(".xml") or key.endswith(".am.txt")
        }
        if not chain:
            reasons.append(f"hash_chain_broken: {batch.label} manifest carries no per-file hashes")
        else:
            for key, expected in chain.items():
                path = batch.directory / key.split(".", 1)[1]
                if not path.is_file():
                    reasons.append(f"hash_chain_broken: {batch.label} missing {path.name}")
                elif sha256_file(path) != expected:
                    reasons.append(f"hash_chain_broken: {batch.label} {path.name} hash mismatch")
        manifest_sha_path = batch.directory / "manifest.sha256"
        if not manifest_sha_path.is_file():
            reasons.append(f"hash_chain_broken: {batch.label} has no manifest.sha256")
        elif manifest_sha_path.read_text(encoding="utf-8").strip() != sha256_file(
            batch.directory / "manifest.txt"
        ):
            reasons.append(
                f"hash_chain_broken: {batch.label} manifest.sha256 does not match manifest.txt"
            )

    if batch_a.run_id and batch_b.run_id and batch_a.run_id == batch_b.run_id:
        reasons.append(f"run_id_reused: both batches recorded runId={batch_a.run_id}")
    if not batch_a.run_id or not batch_b.run_id:
        reasons.append("run_id_reused: at least one batch has no runId")

    identical = 0
    for index in range(1, MAIN_ROUNDS + 1):
        for suffix in (".xml", ".am.txt"):
            left = batch_a.directory / f"round-{index:02d}{suffix}"
            right = batch_b.directory / f"round-{index:02d}{suffix}"
            if left.is_file() and right.is_file() and sha256_file(left) == sha256_file(right):
                identical += 1
    if identical == MAIN_ROUNDS * 2:
        reasons.append(
            "evidence_reused: all 40 same-numbered files are byte-identical across batches"
        )
    return reasons


def check_retry(plan: dict[str, str], batch_a: BatchFacts, batch_b: BatchFacts) -> list[str]:
    reasons: list[str] = []
    declared = plan.get("maxReplacementBatches", "0")
    declared_int = int(declared) if declared.isdigit() else 0
    for batch in (batch_a, batch_b):
        requested = _manifest_int(batch.manifest, "roundsRequested")
        failed = _manifest_int(batch.manifest, "failedRounds")
        replacement = _manifest_int(batch.manifest, "replacementCount")
        if requested != MAIN_ROUNDS:
            reasons.append(
                f"optional_stopping: {batch.label} roundsRequested={requested} != {MAIN_ROUNDS}"
            )
        if failed is None:
            reasons.append(f"failed_rounds: {batch.label} has no failedRounds")
        elif failed != 0:
            # 产品失败轮不是 optional stopping：它让该批 NOT EVALUABLE（A5/A7），
            # 且不得靠重跑洗绿。这里只如实报数，不暗示"重跑一次就好"。
            reasons.append(
                f"failed_rounds: {batch.label} failedRounds={failed} "
                "(per-round product failure; censored sample, not a retry)"
            )
        if replacement is None:
            reasons.append(f"optional_stopping: {batch.label} has no replacementCount")
        elif replacement > declared_int:
            reasons.append(
                f"retry_budget_exceeded: {batch.label} replacementCount={replacement} > "
                f"declared {declared_int}"
            )
    return reasons


# ---------------------------------------------------------------- 判词


@dataclass
class A6Result:
    verdict: str
    exit_code: int
    reasons: list[str]
    plan_sha: str
    gap_minutes: float | None
    batch_a: BatchFacts
    batch_b: BatchFacts

    def payload(self) -> dict[str, object]:
        def batch_payload(batch: BatchFacts) -> dict[str, object]:
            p95 = batch.p95()
            report = batch.report
            return {
                "run_id": batch.run_id,
                "batch_id": batch.manifest.get("batchId", ""),
                "app_sha256": batch.manifest.get("installedAppSha256", ""),
                "test_sha256": batch.manifest.get("installedTestSha256", ""),
                "device_generation_id": batch.manifest.get("deviceGenerationId", ""),
                "install_generation_id": batch.manifest.get("installGenerationId", ""),
                "started_utc": batch.manifest.get("startedUtc", ""),
                "finished_utc": batch.manifest.get("finishedUtc", ""),
                "n": getattr(report, "n", 0),
                "verdict": getattr(report, "verdict", "NOT_EVALUABLE"),
                "g1_red": bool(getattr(report, "g1_reasons", [])),
                "p95_millis": p95,
                "max_millis": batch.max_value(),
                "gate_millis": GATE_MILLIS,
                "gate_margin_millis": (GATE_MILLIS - p95) if p95 is not None else None,
                "boot_ids": [row.boot_id_before for row in batch.rounds],
                "boot_counts": [row.boot_count for row in batch.rounds],
            }

        return {
            "verdict": self.verdict,
            "exit_code": self.exit_code,
            "reasons": self.reasons,
            "plan_sha256": self.plan_sha,
            "observed_gap_minutes": self.gap_minutes,
            "batchA": batch_payload(self.batch_a),
            "batchB": batch_payload(self.batch_b),
            "rules": [
                "A6 is a conjunction of two independent complete n=20 verdicts, not a merged 40-sample estimate",
                "percentiles are not aggregated: no combined/averaged/added p95 is produced",
                "a per-round FAIL/INVALID makes that batch NOT EVALUABLE, not A6 REFUTED",
                "A6 CONFIRMED does not cover C4, the 29.5 s chain, the product-process first request, arm64 or real devices",
            ],
        }


def evaluate(plan_path: Path, batch_a_dir: Path, batch_b_dir: Path) -> A6Result:
    plan = read_kv(plan_path)
    plan_sha = sha256_file(plan_path)
    batch_a = load_batch(batch_a_dir, "batchA")
    batch_b = load_batch(batch_b_dir, "batchB")

    reasons: list[str] = []
    reasons += check_plan(plan, plan_sha, batch_a, batch_b)
    reasons += check_identity(plan, batch_a, batch_b)
    reasons += check_protocol(batch_a, batch_b)
    time_reasons, gap = check_time(plan, batch_a, batch_b)
    reasons += time_reasons
    reasons += check_boot(batch_a, batch_b)
    reasons += check_process(batch_a, batch_b)
    reasons += check_condition(batch_a, batch_b)
    reasons += check_evidence(plan, batch_a, batch_b)
    reasons += check_retry(plan, batch_a, batch_b)

    # 每批自身的理由（由 summarize_kd76 判）：如实列出，便于审计"哪批出不了 p95"。
    for batch in (batch_a, batch_b):
        report = batch.report
        if report is None:
            reasons.append(f"batch_{batch.label}: no round evidence")
            continue
        for reason in getattr(report, "reasons", []):
            reasons.append(f"batch_{batch.label}: {reason}")
        for reason in getattr(report, "g1_reasons", []):
            reasons.append(f"batch_{batch.label}: G1: {reason}")

    verdict_a = getattr(batch_a.report, "verdict", "NOT_EVALUABLE")
    verdict_b = getattr(batch_b.report, "verdict", "NOT_EVALUABLE")

    if reasons:
        verdict, code = "NOT_EVALUABLE", 2
    elif verdict_a == "NOT_EVALUABLE" or verdict_b == "NOT_EVALUABLE":
        verdict, code = "NOT_EVALUABLE", 2
    elif verdict_a == "FAIL" or verdict_b == "FAIL":
        verdict, code = "REFUTED", 1
    else:
        verdict, code = "CONFIRMED", 0
    return A6Result(verdict, code, reasons, plan_sha, gap, batch_a, batch_b)


def _fmt(value: object) -> str:
    return "UNVERIFIED" if value is None else str(value)


def render_text(result: A6Result) -> str:
    payload = result.payload()
    batch_a = payload["batchA"]
    batch_b = payload["batchB"]

    def batch_line(name: str, batch: dict[str, object]) -> str:
        p95 = batch["p95_millis"]
        gate = batch["gate_millis"]
        margin = batch["gate_margin_millis"]
        app_sha = str(batch["app_sha256"])
        return (
            f"  {name}: n={batch['n']}/20, G1 {'RED' if batch['g1_red'] else 'green'}, "
            f"M.p95={_fmt(p95)} ms (gate {gate} ms, margin {_fmt(margin)} ms), "
            f"max={_fmt(batch['max_millis'])} ms, batch={batch['batch_id']}, "
            f"runId={batch['run_id']}, app={app_sha[:16] + '…' if app_sha else '<missing>'}"
        )

    display = result.verdict.replace("_", " ")
    lines: list[str] = [f"A6: {display}"]
    lines.append(batch_line("batchA", batch_a))  # type: ignore[arg-type]
    lines.append(batch_line("batchB", batch_b))  # type: ignore[arg-type]
    if result.verdict == "CONFIRMED":
        lines.append(
            "  independence: build same (installed app+test sha), deviceGeneration same, "
            "installGeneration same, 40/40 host boot_id unique, A∩B=∅, "
            f"gap={_fmt(result.gap_minutes)} min, protocol same, condition drift none"
        )
        lines.append(
            "  note: A6 is a two-batch conjunction, not a merged 40-sample estimate. "
            "It does not cover C4, the 29.5 s chain, the product-process first request, "
            "arm64 or real devices."
        )
    elif result.verdict == "REFUTED":
        lines.append(
            "  note: at least one complete batch reproduced p95 > gate; do not average, "
            "add or merge the two batches."
        )
    else:
        for reason in result.reasons[:40]:
            lines.append(f"  - {reason}")
        if len(result.reasons) > 40:
            lines.append(f"  - … {len(result.reasons) - 40} more")
        lines.append(
            "  no A6 claim may be derived; per-batch numbers are not a substitute, "
            "and no batch PASS may be read as A6."
        )
        lines.append(
            f"  observed gap: {_fmt(result.gap_minutes)} min; plan sha256: {result.plan_sha}"
        )
    return "\n".join(lines)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Conjoin two independent complete KD-76 n=20 batches (A6). "
            "Never merges samples or percentiles."
        )
    )
    parser.add_argument("--plan", required=True, help="pre-registered a6-plan.txt")
    parser.add_argument("--batch-a", required=True, help="batch A run directory")
    parser.add_argument("--batch-b", required=True, help="batch B run directory")
    parser.add_argument("--json", action="store_true", help="machine-readable result")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    plan_path = Path(args.plan)
    batch_a_dir = Path(args.batch_a)
    batch_b_dir = Path(args.batch_b)
    for label, path in (("plan", plan_path), ("batchA", batch_a_dir), ("batchB", batch_b_dir)):
        if not path.exists():
            print(f"kd76_a6_verify: {label} not found: {path}", file=sys.stderr)
            return 2
    try:
        result = evaluate(plan_path, batch_a_dir, batch_b_dir)
    except FileNotFoundError as error:
        print(f"kd76_a6_verify: {error}", file=sys.stderr)
        return 2

    if args.json:
        print(json.dumps(result.payload(), ensure_ascii=False, indent=2, default=str))
    else:
        print(render_text(result))
    return result.exit_code


if __name__ == "__main__":
    sys.exit(main())
