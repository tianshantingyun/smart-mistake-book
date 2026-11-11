# -*- coding: utf-8 -*-
"""MVA 门读数器（批次 50 / G4，2026-11-09）。

**只做读数，不做生图。** 本模块回答一个问题：在给定产物事实的前提下，MVA 六道
门值（首次 ≥90%、预算内 ≥95%、每组 ≥90%、关键错误接受 =0、JS p95 ≤30 s、
IMAGE_MODEL p95 ≤120 s）应当读到什么；并把"没有样本"与"样本为 0 命中"严格分开。

纪律（每条对应一个具体失败）：

1. **没有样本报 null，不报 0%。** 两臂今天都产不出合格样本（无图像后端、JS 事实
   核验未接），若按 0/120 报"命中率 0%"，会把"不可评"伪装成"很差"，也会让下次
   有人拿 0% 去调 prompt。`gate_reading` 无样本时 `value=None / met=False /
   evaluable=False`。
2. **首次命中由事实核验重算，不读自报结论。** `figure_eval_gate.py:892` 的
   `first_pass = outcome == "GENERATED_OK"` 是 provider 自报；本模块要求每条记录
   带 `first_attempt_facts`，用 `check_fact_expectations` 重算（PRD-09 :132
   "不能只读模型自己输出的断言"）。
3. **关键错误登记不读硬编码。** `figure_eval_gate.py:980` 的
   `critical_error_accepted=False` 是恒真式；本模块按 `critical_error_register`
   显式条目 + "自报接受但核验发现 critical 不匹配"（`deterministic_mismatch`）
   计数，并给出 `citable`（无可评样本或登记源不完整时不可引用）。
4. **通道路由确定性可复核。** A/B/C 通道分配由 `fact_channel` 规则从冻结夹具
   确定生成，写 `judge_channel_map.json`，两次构建逐字节相同；未在名单内的 kind
   直接报错（不默认落到"视觉能判"）。
5. **盲法可复核。** `anon_id` 是加盐 sha 前缀，提示词只允许出现它；`task_id`
   前缀本身编码组别（`BLD-MECHPHASE-`），不得进口。
6. **冻结与隔离 fail-closed。** 复用 `figure_eval_gate.verify_freeze_manifest`
   （sha 不符即拒跑），并额外校验生成器 sha 与盲测/开发集 task_id 交集为空。

运行：
    python tools/figure_eval/mva_gate.py --out docs/research/evidence/2026-11-09-batch-50/mva
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

import figure_eval_gate as fem

BLIND_FILE = "tasks_blind_120.json"
DEV_FILE = "tasks_dev_24.json"
MANIFEST_FILE = "freeze_manifest.json"
GENERATOR_FILE = "build_frozen_tasks.py"
CHANNEL_MAP_FILE = "judge_channel_map.json"

SCHEMA_VERSION = 1

# R5 §4.2 的通道分配规则（确定性、可复核；改规则即改本列表与 channel_map sha）
CHANNEL_RULES = [
    {"kind": "numeric", "predicate": "tolerance <= 1e-6", "channel": "A",
     "why": "1e-9/1e-6 精确值在栅格上不存在（信息不在像素里），只能结构化核验"},
    {"kind": "numeric", "predicate": "tolerance >= 0.01", "channel": "A|B",
     "why": "印刷换算值可 OCR 后比对；A 通道仍优先"},
    {"kind": "label_set", "predicate": "always", "channel": "A|B",
     "why": "标签可转写为字符串；subset 语义陷阱（多报标签）另记 verified_with_extras"},
    {"kind": "label_absent", "predicate": "always", "channel": "A|B",
     "why": "禁写内容做存在性判定；漏读即误过，必须双判"},
    {"kind": "direction", "predicate": "always", "channel": "A|C",
     "why": "方向不可转写为字符串；VisionQ 实测 Relation 轴最弱（45.2%）"},
    {"kind": "ordering", "predicate": "always", "channel": "A|C",
     "why": "顺序依赖相对位置，需视觉判断"},
    {"kind": "count", "predicate": "expected <= 6", "channel": "A|B|C",
     "why": "小计数可目视/OCR/结构化三路"},
    {"kind": "count", "predicate": "expected > 6", "channel": "A",
     "why": "大计数不可目视（11–25），只能结构化核验"},
]

EXPECTED_GROUPS = (
    "proportion_coordinates",
    "mechanics_phases",
    "flow_formula",
    "source_rebuild",
    "handwriting_removal",
    "natural_illustration",
)


class IsolationError(RuntimeError):
    """盲测集与开发集重叠（分母污染）；评测门拒绝运行。"""


def load_tasks(path: str | Path) -> list[dict]:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    tasks = payload["tasks"]
    assert isinstance(tasks, list) and tasks, f"empty task set: {path}"
    return tasks


def _sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify_fixture(fixture_dir: str | Path) -> dict:
    """冻结完整性：manifest sha（复用 gate）+ 生成器 sha + 计数一致。"""
    fixture_dir = Path(fixture_dir)
    manifest = fem.verify_freeze_manifest(fixture_dir)  # sha 不符即抛 FreezeIntegrityError
    files: dict[str, dict] = {}
    for name, meta in manifest["files"].items():
        path = fixture_dir / name
        actual = _sha256_bytes(path.read_bytes())
        assert actual == meta["sha256"], f"{name} sha 漂移：{actual[:12]} != {meta['sha256'][:12]}"
        tasks = load_tasks(path)
        counts: dict[str, int] = {}
        for task in tasks:
            counts[task["group"]] = counts.get(task["group"], 0) + 1
        assert len(tasks) == meta["task_count"], f"{name} 计数漂移：{len(tasks)} != {meta['task_count']}"
        assert counts == meta["group_counts"], f"{name} 分组计数漂移：{counts} != {meta['group_counts']}"
        files[name] = {
            "sha256": actual,
            "task_count": len(tasks),
            "group_counts": dict(sorted(counts.items())),
        }
    generator = fixture_dir / GENERATOR_FILE
    generator_actual = _sha256_bytes(generator.read_bytes())
    return {
        "manifest_sha_verified": True,
        "generator_sha_matches": generator_actual == manifest.get("generator_sha256"),
        "generator_sha256": generator_actual,
        "manifest_generator_sha256": manifest.get("generator_sha256"),
        "frozen_at": manifest.get("frozen_at"),
        "files": files,
        "manifest": manifest,
    }


def blind_dev_isolation(blind: list[dict], dev: list[dict]) -> dict:
    """盲测/开发隔离：交集必须为空；开发集不进 120 分母。"""
    blind_ids = {t["task_id"] for t in blind}
    dev_ids = {t["task_id"] for t in dev}
    overlap = sorted(blind_ids & dev_ids)
    if overlap:
        raise IsolationError(f"blind/dev overlap: {overlap[:5]}（开发集污染盲测分母，拒绝运行）")
    if len(blind_ids) != len(blind):
        raise IsolationError("blind task_id 重复")
    group_counts: dict[str, int] = {}
    for task in blind:
        group_counts[task["group"]] = group_counts.get(task["group"], 0) + 1
    return {
        "blind_count": len(blind),
        "dev_count": len(dev),
        "overlap": overlap,
        "dev_in_denominator": False,
        "blind_group_counts": dict(sorted(group_counts.items())),
        "groups_complete": all(group_counts.get(g) == 20 for g in EXPECTED_GROUPS),
    }


def fact_channel(fact: dict) -> str:
    """按 R5 §4.2 把一条夹具事实路由到通道；未知 kind 一律报错（不默认给 C）。"""
    kind = fact["kind"]
    if kind == "numeric":
        tolerance = float(fact["tolerance"])
        return "A" if tolerance <= 1e-6 else "A|B"
    if kind in ("label_set", "label_absent"):
        return "A|B"
    if kind in ("direction", "ordering"):
        return "A|C"
    if kind == "count":
        return "A|B|C" if int(fact["expected"]) <= 6 else "A"
    raise ValueError(f"未知事实 kind：{kind!r}（不默认路由，需先定规则）")


def fixture_sanity(tasks: list[dict]) -> dict:
    """夹具自洽检查：坏 oracle 会让核验器报假不匹配，不能让它冒充"模型画错"。

    - numeric 必须带有限正容差（缺容差 → 比较退化成精确相等，1e-9 意图被静默改写）；
    - count 期望必须是非负整数；direction 必须在既定枚举内；
    - label_set/label_absent 期望必须是非空字符串列表；
    - 每条事实必须有 key 且同题内唯一。
    """
    directions = {"down", "along_incline_up", "up"}
    kinds = {"numeric", "label_set", "label_absent", "direction", "ordering", "count"}
    checked = 0
    for task in tasks:
        keys = [e["key"] for e in task["fact_expectations"]]
        if len(keys) != len(set(keys)):
            raise ValueError(f"{task['task_id']} 事实 key 重复：{keys}")
        for fact in task["fact_expectations"]:
            kind = fact["kind"]
            if kind not in kinds:
                raise ValueError(f"{task['task_id']} 未知 kind：{kind!r}")
            expected = fact["expected"]
            if kind == "numeric":
                tol = fact.get("tolerance")
                if not isinstance(tol, (int, float)) or not math.isfinite(float(tol)) or float(tol) <= 0:
                    raise ValueError(f"{task['task_id']}/{fact['key']} numeric 容差非法：{tol!r}")
                if not isinstance(expected, (int, float)) or not math.isfinite(float(expected)):
                    raise ValueError(f"{task['task_id']}/{fact['key']} numeric 期望非有限数：{expected!r}")
            elif kind == "count":
                if not isinstance(expected, int) or expected < 0:
                    raise ValueError(f"{task['task_id']}/{fact['key']} count 期望非法：{expected!r}")
            elif kind == "direction":
                if expected not in directions:
                    raise ValueError(f"{task['task_id']}/{fact['key']} direction 越界：{expected!r}")
            elif kind in ("label_set", "label_absent"):
                if not isinstance(expected, list) or not expected or not all(isinstance(s, str) and s for s in expected):
                    raise ValueError(f"{task['task_id']}/{fact['key']} 标签期望非法：{expected!r}")
            elif kind == "ordering":
                if not isinstance(expected, list) or len(expected) < 2:
                    raise ValueError(f"{task['task_id']}/{fact['key']} ordering 期望不是 2 项以上序列：{expected!r}")
            checked += 1
    return {"facts_checked": checked, "status": "PASS"}


def build_channel_map(tasks: list[dict]) -> dict:
    facts: list[dict] = []
    channel_counts: dict[str, int] = {}
    critical_counts: dict[str, int] = {}
    tasks_with_critical_a_only = 0
    tasks_with_all_critical_a_only = 0
    for task in tasks:
        expectations = task["fact_expectations"]
        if not expectations:
            continue
        entries = []
        for fact in expectations:
            channel = fact_channel(fact)
            entry = {
                "task_id": task["task_id"],
                "group": task["group"],
                "key": fact["key"],
                "kind": fact["kind"],
                "channel": channel,
                "critical": bool(fact.get("critical", False)),
            }
            entries.append(entry)
            facts.append(entry)
            channel_counts[channel] = channel_counts.get(channel, 0) + 1
            if entry["critical"]:
                critical_counts[channel] = critical_counts.get(channel, 0) + 1
        critical_entries = [e for e in entries if e["critical"]]
        if any(e["channel"] == "A" for e in critical_entries):
            tasks_with_critical_a_only += 1
        if critical_entries and all(e["channel"] == "A" for e in critical_entries):
            tasks_with_all_critical_a_only += 1
    return {
        "schema_version": SCHEMA_VERSION,
        "name": "mva_judge_channel_map",
        "purpose": "A/B/C 通道分配（派生自冻结夹具；不改 120 集本身）",
        "rules": CHANNEL_RULES,
        "totals": {
            "tasks": len(tasks),
            "facts": len(facts),
            "critical_facts": sum(critical_counts.values()),
            "channel": dict(sorted(channel_counts.items())),
            "critical_channel": dict(sorted(critical_counts.items())),
            "tasks_with_critical_a_only": tasks_with_critical_a_only,
            "tasks_with_all_critical_a_only": tasks_with_all_critical_a_only,
        },
        "facts": sorted(facts, key=lambda e: (e["task_id"], e["key"])),
    }


def channel_map_json_bytes(mapping: dict) -> bytes:
    return (json.dumps(mapping, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def write_channel_map(fixture_dir: str | Path, mapping: dict) -> dict:
    path = Path(fixture_dir) / CHANNEL_MAP_FILE
    data = channel_map_json_bytes(mapping)
    path.write_bytes(data)
    return {"path": str(path), "sha256": _sha256_bytes(data), "bytes": len(data)}


def anon_id(task_id: str, salt: str) -> str:
    return "T" + _sha256_bytes(f"{salt}|{task_id}".encode("utf-8"))[:10]


def zero_event_upper_bound(n: int, p: float = 0.05) -> float | None:
    """零事件 95% 单侧上界 1 - p^(1/n)；n=0 无定义记 None。"""
    if n <= 0:
        return None
    return 1.0 - p ** (1.0 / n)


def _rate(successes: int, denominator: int, threshold: float, gate: dict, n_samples: int) -> dict:
    value = successes / denominator if (denominator and n_samples) else None
    lo, hi = fem.wilson_ci(successes, denominator) if (denominator and n_samples) else (None, None)
    return {
        "value": value,
        "successes": successes,
        "denominator": denominator,
        "threshold": threshold,
        "met": bool(value is not None and value >= threshold),
        "wilson95": [lo, hi],
        "samples": n_samples,
        "source": gate["source"],
        "informational_only": True,
    }


def gate_reading(
    tasks: list[dict],
    records_by_task: dict[str, dict],
    arm: str,
    latency_threshold_ms: float,
) -> dict:
    """从产物事实重算六道门读数。

    记录形状（每条 task 一条；缺记录 = 无样本，不是失败样本）：
        {"task_id","first_attempt_facts":{key:value}|None,"final_facts":{key:value}|None,
         "latency_ms": float|None, "render_latency_ms": float|None,
         "critical_error_register":[{"fact_key","source"}],
         "accepted_self_report": bool}

    判定：
    - 首次命中 = `first_attempt_facts` 经 `check_fact_expectations` 全过；
    - 预算内最终 = `final_facts` 全过；
    - 关键错误被接受 = 显式登记条目 + "自报接受但核验发现 critical 不匹配"
      （deterministic_mismatch）；
    - p95 = 最终合格样本的 `latency_ms`（请求→首张合格图，含作者耗时 + 渲染）；
      `render_latency_ms` 只作并列的渲染侧读数，不参与门判。
    """
    first_successes = 0
    final_successes = 0
    samples = 0
    latencies: list[float] = []
    render_latencies: list[float] = []
    register: list[dict] = []
    checks = {"render": 0, "geometry": 0, "text": 0}
    per_group_success: dict[str, int] = {g: 0 for g in EXPECTED_GROUPS}
    per_group_total: dict[str, int] = {g: 0 for g in EXPECTED_GROUPS}
    per_group_samples: dict[str, int] = {g: 0 for g in EXPECTED_GROUPS}

    for task in tasks:
        group = task["group"]
        per_group_total.setdefault(group, 0)
        per_group_total[group] += 1
        per_group_success.setdefault(group, 0)

        record = records_by_task.get(task["task_id"])
        if not record:
            continue
        first_facts = record.get("first_attempt_facts")
        final_facts = record.get("final_facts")
        if first_facts is None and final_facts is None:
            continue
        samples += 1
        per_group_samples[group] = per_group_samples.get(group, 0) + 1

        if record.get("render_ok") is True:
            checks["render"] += 1
        if record.get("geometry_ok") is True:
            checks["geometry"] += 1
        if record.get("text_ok") is True:
            checks["text"] += 1

        expectations = task["fact_expectations"]
        if first_facts is not None:
            verdict = fem.check_fact_expectations(
                [{"key": k, "value": v} for k, v in first_facts.items()], expectations)
            if verdict["accepted"]:
                first_successes += 1
        if final_facts is not None:
            verdict = fem.check_fact_expectations(
                [{"key": k, "value": v} for k, v in final_facts.items()], expectations)
            critical_mismatches = [m for m in verdict["mismatches"] if m["critical"]]
            if verdict["accepted"]:
                final_successes += 1
                per_group_success[group] = per_group_success.get(group, 0) + 1
                latency = record.get("latency_ms")
                if isinstance(latency, (int, float)):
                    latencies.append(float(latency))
                render_latency = record.get("render_latency_ms")
                if isinstance(render_latency, (int, float)):
                    render_latencies.append(float(render_latency))
            elif critical_mismatches and record.get("accepted_self_report"):
                for mismatch in critical_mismatches:
                    register.append({
                        "task_id": task["task_id"],
                        "fact_key": mismatch["key"],
                        "kind": mismatch["kind"],
                        "source": "deterministic_mismatch",
                        "note": "记录自报接受，但事实核验发现 critical 不匹配",
                    })
        for entry in record.get("critical_error_register", []) or []:
            item = dict(entry)
            item.setdefault("task_id", task["task_id"])
            register.append(item)

    evaluable = samples > 0
    denominator = len(tasks)
    per_group = {}
    for group in EXPECTED_GROUPS:
        total = per_group_total.get(group, 0)
        ok = per_group_success.get(group, 0)
        n_samples = per_group_samples.get(group, 0)
        per_group[group] = {
            "value": (ok / total) if (total and n_samples) else None,
            "successes": ok,
            "denominator": total,
            "threshold": fem.GATES["per_group_final_rate_min"]["value"],
            "met": bool(total and n_samples and ok / total >= fem.GATES["per_group_final_rate_min"]["value"]),
            "samples": n_samples,
        }

    critical_value = len(register)
    return {
        "arm": arm,
        "evaluable": evaluable,
        "samples": samples,
        "denominator": denominator,
        "coverage": samples / denominator if denominator else None,
        "reason": None if evaluable else (
            "两臂均未产出带事实的样本：IMAGE_MODEL 无图像后端、JS 臂无引擎适配器/事实提取器"
            "（本机不可生成）；无样本记 null，不记 0 命中"
        ),
        "first_pass_rate": _rate(first_successes, denominator,
                                 fem.GATES["first_pass_rate_min"]["value"],
                                 fem.GATES["first_pass_rate_min"], samples),
        "within_budget_final_rate": _rate(final_successes, denominator,
                                          fem.GATES["within_budget_final_rate_min"]["value"],
                                          fem.GATES["within_budget_final_rate_min"], samples),
        "per_group_final": {
            "threshold": fem.GATES["per_group_final_rate_min"]["value"],
            "source": fem.GATES["per_group_final_rate_min"]["source"],
            "groups": per_group,
            "all_met": all(v["met"] for v in per_group.values()),
        },
        "critical_errors_accepted": {
            "value": critical_value,
            "threshold": fem.GATES["critical_errors_accepted_max"]["value"],
            "met": bool(evaluable and critical_value <= fem.GATES["critical_errors_accepted_max"]["value"]),
            "citable": evaluable,
            "zero_event_upper_bound_95": zero_event_upper_bound(denominator) if evaluable else None,
            "register": register,
            "source": fem.GATES["critical_errors_accepted_max"]["source"],
            "note": (
                "无样本时 0 条不可引用（此时计数为 0 只因没有产物）；"
                "figure_eval_gate.py:980 的 critical_error_accepted=False 是恒真式，不得作为本门证据"
            ),
        },
        "first_image_p95_ms": {
            "value": fem.percentile(latencies, 0.95),
            "threshold_ms": latency_threshold_ms,
            "met": bool(latencies and fem.percentile(latencies, 0.95) <= latency_threshold_ms),
            "samples": len(latencies),
            "scope": "request_to_first_image",
            "source": (fem.GATES["first_image_p95_ms_js_max"]["source"] if arm == "JS"
                       else fem.GATES["first_image_p95_ms_image_model_max"]["source"]),
            # 渲染侧读数只作并列披露：设计 §6 的门是请求→首图，不含作者模型会用
            # 渲染延迟把门读成 met=true。没有 render_latency_ms 时记 null，不猜。
            "render_only": {
                "value": fem.percentile(render_latencies, 0.95),
                "threshold_ms": latency_threshold_ms,
                "met": bool(render_latencies
                            and fem.percentile(render_latencies, 0.95) <= latency_threshold_ms),
                "samples": len(render_latencies),
                "scope": "render_only",
            },
        },
        "deterministic_checks": {
            "render": checks["render"],
            "geometry": checks["geometry"],
            "text": checks["text"],
            "status": "RUN" if samples else "NOT_RUN",
            "note": "渲染/几何/文字三类确定性检查按记录里的 render_ok/geometry_ok/text_ok 计数",
        },
    }


def records_from_harness_report(report: dict) -> dict[str, dict]:
    """把 `figure_eval_gate` 的 task_records 转成读数器记录形状。

    只搬身份与自报字段；**不伪造事实**——报告里没有 `first_attempt_facts`，
    所以转换后的记录一律"无事实"，读数器据此报不可评（不报 0 命中）。
    """
    records: dict[str, dict] = {}
    for record in report.get("task_records", []) or []:
        task_id = record.get("task_id")
        if not task_id:
            continue
        records[task_id] = {
            "task_id": task_id,
            "group": record.get("group"),
            "classification": record.get("primary_classification"),
            "accepted_self_report": record.get("final_accepted") is True,
            "first_attempt_facts": None,
            "final_facts": None,
            "latency_ms": None,
        }
    return records


def preflight(
    fixture_dir: str | Path,
    records_by_task: dict[str, dict] | None = None,
    write_map: bool = False,
) -> dict:
    fixture_dir = Path(fixture_dir)
    records_by_task = records_by_task or {}
    fixture = verify_fixture(fixture_dir)
    blind = load_tasks(fixture_dir / BLIND_FILE)
    dev = load_tasks(fixture_dir / DEV_FILE)
    isolation = blind_dev_isolation(blind, dev)
    mapping = build_channel_map(blind)
    map_info = {"sha256": _sha256_bytes(channel_map_json_bytes(mapping)), "bytes": len(channel_map_json_bytes(mapping))}
    if write_map:
        map_info.update(write_channel_map(fixture_dir, mapping))
    readings = {
        arm: gate_reading(blind, records_by_task.get(arm, {}) if records_by_task else {},
                          arm=arm, latency_threshold_ms=threshold)
        for arm, threshold in (("IMAGE_MODEL", fem.GATES["first_image_p95_ms_image_model_max"]["value"]),
                               ("JS", fem.GATES["first_image_p95_ms_js_max"]["value"]))
    }
    return {
        "schema_version": SCHEMA_VERSION,
        "generated_by": "tools/figure_eval/mva_gate.py",
        "fixture": fixture,
        "fixture_sanity": fixture_sanity(blind),
        "isolation": isolation,
        "channel_map": map_info,
        "channel_totals": mapping["totals"],
        "statistics": {
            "wilson95_at_thresholds": {
                "108/120 (0.90)": fem.wilson_ci(108, 120),
                "114/120 (0.95)": fem.wilson_ci(114, 120),
                "119/120": fem.wilson_ci(119, 120),
                "20/20 (0.90 group)": fem.wilson_ci(20, 20),
            },
            "zero_event_upper_bound_95": {
                "n=120": zero_event_upper_bound(120),
                "n=209": zero_event_upper_bound(209),
                "n=20": zero_event_upper_bound(20),
            },
        },
        "readings": readings,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="MVA 门读数器（只读夹具，不生成图）")
    parser.add_argument("--fixture-dir", default=str(HERE))
    parser.add_argument("--out", default=None, help="输出目录（写 mva_preflight.json）")
    parser.add_argument("--write-channel-map", action="store_true")
    parser.add_argument(
        "--harness-report", action="append", default=[], metavar="ARM=PATH",
        help="消费 figure_eval_gate 报告（可重复）；报告无事实字段 → 该臂读作不可评",
    )
    args = parser.parse_args(argv)

    records_by_task: dict[str, dict] = {}
    for spec in args.harness_report:
        arm, _, path = spec.partition("=")
        if not path:
            parser.error(f"--harness-report 需要 ARM=PATH：{spec!r}")
        records_by_task[arm] = records_from_harness_report(
            json.loads(Path(path).read_text(encoding="utf-8")))
    report = preflight(args.fixture_dir, records_by_task=records_by_task,
                       write_map=args.write_channel_map)
    text = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.out:
        out = Path(args.out)
        out.mkdir(parents=True, exist_ok=True)
        (out / "mva_preflight.json").write_text(text, encoding="utf-8")
    print(json.dumps({
        "fixture_ok": report["fixture"]["manifest_sha_verified"] and report["fixture"]["generator_sha_matches"],
        "isolation": {k: report["isolation"][k] for k in ("blind_count", "dev_count", "overlap", "groups_complete")},
        "channel_totals": report["channel_totals"],
        "channel_map_sha256": report["channel_map"]["sha256"],
        "arms": {arm: {"evaluable": r["evaluable"], "samples": r["samples"]}
                 for arm, r in report["readings"].items()},
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
