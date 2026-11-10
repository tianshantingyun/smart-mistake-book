# -*- coding: utf-8 -*-
"""MVA 门读数器的机制测试（批次 50 / G4）。

这些测试只验证**读数器本身**（冻结完整性、盲测/开发隔离、A/B/C 通道路由、
"没有样本就报 null 不报 0"、关键错误登记不读自报结论）。它们**不**验证任何
真实生图质量：真实 Provider 与 VLM 判官在本机都不可用（无图像后端、无判官
实现），真实质量一律 UNVERIFIED。

运行：
    python -m pytest tools/figure_eval/test_mva_gate.py -q
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

import mva_gate as mg

HERE = Path(__file__).resolve().parent


def _blind():
    return mg.load_tasks(HERE / mg.BLIND_FILE)


def _dev():
    return mg.load_tasks(HERE / mg.DEV_FILE)


def _good_facts(task):
    """按夹具预期构造一组"正确"的图上事实（label_absent 的正确输出是不出现）。"""
    return {
        e["key"]: ([] if e["kind"] == "label_absent" else e["expected"])
        for e in task["fact_expectations"]
    }


# ---------------------------------------------------------------------------
# 1) 冻结完整性 + 盲测/开发隔离
# ---------------------------------------------------------------------------

def test_fixture_freeze_and_generator_sha_match_manifest():
    report = mg.verify_fixture(HERE)
    assert report["manifest_sha_verified"] is True
    assert report["generator_sha_matches"] is True
    assert report["files"]["tasks_blind_120.json"]["sha256"] == report["manifest"]["files"]["tasks_blind_120.json"]["sha256"]


def test_tampered_frozen_task_file_is_rejected(tmp_path):
    for name in (mg.BLIND_FILE, mg.DEV_FILE, mg.MANIFEST_FILE):
        (tmp_path / name).write_bytes((HERE / name).read_bytes())
    tampered = (tmp_path / mg.BLIND_FILE).read_bytes() + b"\n"
    (tmp_path / mg.BLIND_FILE).write_bytes(tampered)
    with pytest.raises(Exception) as err:
        mg.verify_fixture(tmp_path)
    assert "tampered" in str(err.value)


def test_blind_and_dev_are_disjoint_and_dev_is_excluded():
    blind, dev = _blind(), _dev()
    report = mg.blind_dev_isolation(blind, dev)
    assert report["overlap"] == []
    assert report["blind_count"] == 120
    assert report["dev_count"] == 24
    assert report["dev_in_denominator"] is False


def test_injected_overlap_is_rejected():
    blind, dev = _blind(), _dev()
    dev_with_leak = dev + [blind[0]]
    with pytest.raises(Exception) as err:
        mg.blind_dev_isolation(blind, dev_with_leak)
    assert "overlap" in str(err.value).lower()


# ---------------------------------------------------------------------------
# 2) A/B/C 通道路由（R5 §4.2 规则）
# ---------------------------------------------------------------------------

def test_channel_rules():
    assert mg.fact_channel({"kind": "numeric", "expected": 1.0, "tolerance": 1e-09}) == "A"
    assert mg.fact_channel({"kind": "numeric", "expected": 1.5, "tolerance": 1e-06}) == "A"
    assert mg.fact_channel({"kind": "numeric", "expected": 2.0, "tolerance": 0.01}) == "A|B"
    assert mg.fact_channel({"kind": "label_set", "expected": ["1:1"]}) == "A|B"
    assert mg.fact_channel({"kind": "label_absent", "expected": ["答案"]}) == "A|B"
    assert mg.fact_channel({"kind": "direction", "expected": "down"}) == "A|C"
    assert mg.fact_channel({"kind": "ordering", "expected": ["a", "b"]}) == "A|C"
    assert mg.fact_channel({"kind": "count", "expected": 6}) == "A|B|C"
    assert mg.fact_channel({"kind": "count", "expected": 7}) == "A"
    with pytest.raises(ValueError):
        mg.fact_channel({"kind": "unknown_kind", "expected": 1})


def test_channel_map_matches_r5_counts_and_is_byte_identical():
    first = mg.build_channel_map(_blind())
    second = mg.build_channel_map(_blind())
    assert mg.channel_map_json_bytes(first) == mg.channel_map_json_bytes(second)
    assert first["totals"]["facts"] == 288
    assert first["totals"]["channel"] == {"A": 94, "A|B": 116, "A|C": 40, "A|B|C": 38}
    assert first["totals"]["critical_channel"]["A"] == 87
    assert first["totals"]["tasks_with_critical_a_only"] == 57
    assert first["totals"]["tasks_with_all_critical_a_only"] == 27


def test_channel_map_rejects_unknown_kind():
    task = json.loads(json.dumps(_blind()[0]))
    task["fact_expectations"][0]["kind"] = "not_a_kind"
    with pytest.raises(ValueError):
        mg.build_channel_map([task])


# ---------------------------------------------------------------------------
# 3) 匿名 ID（盲法：提示词里不出现 task_id / 组前缀）
# ---------------------------------------------------------------------------

def test_anon_id_hides_task_id_and_is_salt_dependent():
    task_id = "BLD-MECHPHASE-003"
    anon = mg.anon_id(task_id, "run-seed-a")
    assert task_id not in anon
    assert "MECHPHASE" not in anon
    assert anon.startswith("T")
    assert len(anon) == 11
    assert anon == mg.anon_id(task_id, "run-seed-a")
    assert anon != mg.anon_id(task_id, "run-seed-b")


# ---------------------------------------------------------------------------
# 4) 门读数：没有样本报 null，不报 0
# ---------------------------------------------------------------------------

def test_no_samples_reports_null_and_never_passes():
    reading = mg.gate_reading(_blind(), {}, arm="IMAGE_MODEL", latency_threshold_ms=120_000)
    assert reading["evaluable"] is False
    assert reading["first_pass_rate"]["value"] is None
    assert reading["within_budget_final_rate"]["value"] is None
    assert reading["first_pass_rate"]["met"] is False
    assert reading["within_budget_final_rate"]["met"] is False
    assert reading["first_image_p95_ms"]["value"] is None
    assert reading["critical_errors_accepted"]["value"] == 0
    assert reading["critical_errors_accepted"]["citable"] is False
    assert len(reading["per_group_final"]["groups"]) == 6
    assert all(g["value"] is None for g in reading["per_group_final"]["groups"].values())


def test_provider_self_report_without_facts_is_not_a_first_pass():
    # 只有 classification 自报成功、没有事实核验 → 不计任何命中。
    records = {
        "BLD-PROPCOORD-001": {"task_id": "BLD-PROPCOORD-001", "classification": "GENERATED_OK"},
    }
    reading = mg.gate_reading(_blind(), records, arm="IMAGE_MODEL", latency_threshold_ms=120_000)
    assert reading["evaluable"] is False
    assert reading["first_pass_rate"]["value"] is None
    assert reading["deterministic_checks"]["render"] == 0


def test_first_pass_is_recomputed_from_facts_not_from_record_flags():
    task = _blind()[0]
    good = _good_facts(task)
    bad = dict(good)
    # 关键事实改错：numeric 期望 1.0 → 2.0（超 1e-9 容差）
    bad[task["fact_expectations"][0]["key"]] = task["fact_expectations"][0]["expected"] + 1.0
    records = {
        task["task_id"]: {
            "task_id": task["task_id"],
            "classification": "GENERATED_OK",
            "accepted_self_report": True,
            "first_attempt_facts": bad,
            "final_facts": bad,
            "latency_ms": 1234.0,
        }
    }
    reading = mg.gate_reading(_blind(), records, arm="IMAGE_MODEL", latency_threshold_ms=120_000)
    assert reading["evaluable"] is True
    assert reading["first_pass_rate"]["successes"] == 0
    assert reading["within_budget_final_rate"]["successes"] == 0
    assert reading["critical_errors_accepted"]["value"] == 1
    assert reading["critical_errors_accepted"]["register"][0]["source"] == "deterministic_mismatch"
    assert reading["critical_errors_accepted"]["met"] is False
    assert reading["critical_errors_accepted"]["citable"] is True


def test_clean_facts_pass_and_latency_feeds_p95():
    task = _blind()[0]
    good = _good_facts(task)
    records = {
        task["task_id"]: {
            "task_id": task["task_id"],
            "first_attempt_facts": good,
            "final_facts": good,
            "latency_ms": 9_000.0,
        }
    }
    reading = mg.gate_reading(_blind(), records, arm="IMAGE_MODEL", latency_threshold_ms=120_000)
    assert reading["first_pass_rate"]["successes"] == 1
    assert reading["first_pass_rate"]["denominator"] == 120
    assert reading["first_image_p95_ms"]["value"] == 9_000.0
    assert reading["per_group_final"]["groups"]["proportion_coordinates"]["successes"] == 1
    assert reading["per_group_final"]["groups"]["mechanics_phases"]["value"] is None


def test_p95_uses_end_to_end_latency_and_reports_render_only_separately():
    """门 p95 口径 = 请求→首图（作者耗时 + 渲染），渲染耗时只作并列读数。

    修复环背景（批次51）：`mva_gate_run.py` 曾只把适配器渲染延迟（~35 ms）塞进
    `latency_ms`，使机器可读门记 p95 met=true；设计 §6 的「首张合格图 p95」是
    请求→首图，应含作者模型耗时。本测试钉住：端到端超阈值必须 met=false，
    渲染侧读数仍单独保留，不得再用它冒充端到端门。
    """
    task = _blind()[0]
    good = _good_facts(task)
    records = {
        task["task_id"]: {
            "task_id": task["task_id"],
            "first_attempt_facts": good,
            "final_facts": good,
            "latency_ms": 40_000.0,
            "render_latency_ms": 35.0,
        }
    }
    reading = mg.gate_reading(_blind(), records, arm="JS", latency_threshold_ms=30_000)
    p95 = reading["first_image_p95_ms"]
    assert p95["value"] == 40_000.0
    assert p95["met"] is False
    assert p95["scope"] == "request_to_first_image"
    assert p95["render_only"]["value"] == 35.0
    assert p95["render_only"]["met"] is True


def test_p95_without_render_latency_still_fails_closed_when_value_present():
    task = _blind()[0]
    good = _good_facts(task)
    records = {
        task["task_id"]: {
            "task_id": task["task_id"],
            "first_attempt_facts": good,
            "final_facts": good,
            "latency_ms": 20_000.0,
        }
    }
    reading = mg.gate_reading(_blind(), records, arm="JS", latency_threshold_ms=30_000)
    p95 = reading["first_image_p95_ms"]
    assert p95["value"] == 20_000.0
    assert p95["met"] is True
    assert p95["render_only"]["value"] is None


def test_judge_register_sources_are_counted_and_blocks_the_gate():
    task = _blind()[1]
    good = _good_facts(task)
    records = {
        task["task_id"]: {
            "task_id": task["task_id"],
            "first_attempt_facts": good,
            "final_facts": good,
            "latency_ms": 11_000.0,
            "critical_error_register": [
                {"fact_key": task["fact_expectations"][0]["key"], "source": "judge_false_accept"},
                {"fact_key": task["fact_expectations"][1]["key"], "source": "unresolved_disagreement"},
            ],
        }
    }
    reading = mg.gate_reading(_blind(), records, arm="JS", latency_threshold_ms=30_000)
    assert reading["critical_errors_accepted"]["value"] == 2
    sources = [e["source"] for e in reading["critical_errors_accepted"]["register"]]
    assert sources.count("judge_false_accept") == 1
    assert sources.count("unresolved_disagreement") == 1


def test_zero_event_upper_bound():
    assert mg.zero_event_upper_bound(120) == pytest.approx(0.0247, abs=5e-4)
    assert mg.zero_event_upper_bound(20) == pytest.approx(0.1391, abs=5e-4)
    assert mg.zero_event_upper_bound(0) is None


def test_harness_report_without_facts_reads_as_not_evaluable():
    """harness 报告的 final_accepted 是自报；没有事实字段就不得计命中。"""
    report = {"task_records": [
        {"task_id": "BLD-PROPCOORD-001", "group": "proportion_coordinates",
         "primary_classification": "PASS", "final_accepted": True},
        {"task_id": "BLD-PROPCOORD-002", "group": "proportion_coordinates",
         "primary_classification": "PASS_WITH_REPAIR", "final_accepted": True},
    ]}
    records = mg.records_from_harness_report(report)
    reading = mg.gate_reading(_blind(), records, arm="IMAGE_MODEL", latency_threshold_ms=120_000)
    assert reading["evaluable"] is False
    assert reading["samples"] == 0
    assert reading["first_pass_rate"]["value"] is None
    assert reading["critical_errors_accepted"]["value"] == 0
    assert reading["critical_errors_accepted"]["citable"] is False


def test_fixture_sanity_passes_on_frozen_set_and_rejects_bad_oracle():
    assert mg.fixture_sanity(_blind())["facts_checked"] == 288
    broken = json.loads(json.dumps(_blind()[0]))
    broken["fact_expectations"][0].pop("tolerance")
    with pytest.raises(ValueError):
        mg.fixture_sanity([broken])
    bad_direction = json.loads(json.dumps(_blind()[0]))
    bad_direction["fact_expectations"] = [
        {"kind": "direction", "key": "d", "expected": "sideways", "critical": True}]
    with pytest.raises(ValueError):
        mg.fixture_sanity([bad_direction])
