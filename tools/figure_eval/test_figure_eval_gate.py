# -*- coding: utf-8 -*-
"""PRD-09 生图评测 harness 的机制测试（T5，批次 20）。

这些测试只验证**评测机制本身**（夹具冻结完整性、防面向结果、配对/切换记录、
指标字段、门常量引用、预算口径、报告工件），不验证任何真实生图质量——
真实 Provider 无凭据，一切真实质量结论 UNVERIFIED（PRD-09 退出门原文：
`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:137`）。

运行：
    python -m pytest tools/figure_eval/test_figure_eval_gate.py -q
"""

from __future__ import annotations

import copy
import hashlib
import json
import math
import tempfile
from pathlib import Path

import pytest

import figure_eval_gate as fem

HERE = Path(__file__).resolve().parent


# ---------------------------------------------------------------------------
# 1) 冻结夹具：形状、字段齐、评审来源必填（PRD-09 :132 原文）
# ---------------------------------------------------------------------------

def test_blind_set_is_120_in_six_groups_of_20():
    tasks = fem.load_frozen_tasks(HERE / "tasks_blind_120.json")
    assert len(tasks) == 120
    groups: dict[str, int] = {}
    for t in tasks:
        groups[t["group"]] = groups.get(t["group"], 0) + 1
    assert len(groups) == 6
    assert set(groups.values()) == {20}
    # 设计§6 原文的六组（design 2026-10-19 §6 :91：比例/坐标、力学/阶段、流程/公式、
    # 源图重建、去手写、自然插画）
    expected_groups = {
        "proportion_coordinates",
        "mechanics_phases",
        "flow_formula",
        "source_rebuild",
        "handwriting_removal",
        "natural_illustration",
    }
    assert set(groups) == expected_groups


def test_every_task_has_required_fields_including_review_source():
    for name, count in (("tasks_blind_120.json", 120), ("tasks_dev_24.json", None)):
        tasks = fem.load_frozen_tasks(HERE / name)
        if count is not None:
            assert len(tasks) == count
        for t in tasks:
            for field in (
                "task_id",
                "set",
                "group",
                "figure_type",
                "problem_statement",
                "fact_expectations",
                "review_source",
            ):
                assert t.get(field), f"{t.get('task_id')} missing {field}"
            assert t["set"] in ("blind", "dev")
            assert len(t["fact_expectations"]) >= 1
            rs = t["review_source"]
            # 评审来源字段必填；当前只能"模型标注，无人工专家"，如实标注
            assert rs.get("annotator")
            assert rs.get("human_expert") is False
            assert "无人工专家" in rs.get("evidence_limitation", "")
            for exp in t["fact_expectations"]:
                assert exp["kind"] in fem.EXPECTATION_KINDS
                assert exp.get("key")


def test_blind_and_dev_ids_disjoint_and_dev_excluded_from_denominator():
    blind = fem.load_frozen_tasks(HERE / "tasks_blind_120.json")
    dev = fem.load_frozen_tasks(HERE / "tasks_dev_24.json")
    blind_ids = {t["task_id"] for t in blind}
    dev_ids = {t["task_id"] for t in dev}
    assert len(blind_ids) == 120 and len(dev_ids) == 24
    assert blind_ids.isdisjoint(dev_ids)
    report = fem.run_fake_evaluation(output_dir=None)
    assert report["denominator"]["blind"] == 120
    assert report["denominator"]["dev_excluded"] == 24


# ---------------------------------------------------------------------------
# 2) 冻结 manifest：防面向结果篡改
# ---------------------------------------------------------------------------

def test_freeze_manifest_verifies_real_files():
    fem.verify_freeze_manifest(HERE)  # 不抛异常即通过


def test_freeze_manifest_rejects_tampered_task_file():
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        for name in (
            "tasks_blind_120.json",
            "tasks_dev_24.json",
            "budget.json",
            "freeze_manifest.json",
        ):
            (tmp / name).write_bytes((HERE / name).read_bytes())
        # 篡改一个盲测任务后（无论改题意还是预期），manifest 必须拒绝运行
        data = json.loads((tmp / "tasks_blind_120.json").read_text(encoding="utf-8"))
        data["tasks"][0]["problem_statement"] += "（面向结果的篡改）"
        (tmp / "tasks_blind_120.json").write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        with pytest.raises(fem.FreezeIntegrityError):
            fem.verify_freeze_manifest(tmp)


# ---------------------------------------------------------------------------
# 3) fake 驱动：确定性 + 与预期解耦（不准面向结果）
# ---------------------------------------------------------------------------

def test_fake_driver_is_deterministic():
    r1 = fem.run_fake_evaluation(run_seed="t5-red", output_dir=None)
    r2 = fem.run_fake_evaluation(run_seed="t5-red", output_dir=None)
    assert r1["task_records"] == r2["task_records"]


def test_driver_outcome_independent_of_expectation_values():
    """改动夹具预期值不得改变驱动裁决（防"夹具面向结果"耦合）。"""
    tasks = fem.load_frozen_tasks(HERE / "tasks_blind_120.json")
    variant = copy.deepcopy(tasks)
    for t in variant:
        for exp in t["fact_expectations"]:
            if exp["kind"] == "numeric" and isinstance(exp["expected"], (int, float)):
                exp["expected"] = exp["expected"] * 7 + 3
    drv = fem.FakeImageModelDriver(run_seed="independence")
    base = [drv.decide(t) for t in tasks]
    other = [drv.decide(t) for t in variant]
    assert base == other  # 裁决类别与损坏位置只由 task_id/backend/seed 决定


def test_fact_checker_catches_critical_mismatch_and_never_accepts():
    exp = {
        "kind": "direction",
        "key": "friction_direction",
        "expected": "down",
        "critical": True,
    }
    verdict = fem.check_fact_expectations(
        [{"key": "friction_direction", "value": "up"}], [exp]
    )
    assert verdict["accepted"] is False
    assert any(m["critical"] for m in verdict["mismatches"])


# ---------------------------------------------------------------------------
# 4) 配对/切换与分类完备
# ---------------------------------------------------------------------------

def test_fake_run_exercises_all_classification_paths():
    report = fem.run_fake_evaluation(output_dir=None)
    classes = {r["image_model"]["classification"] for r in report["task_records"]}
    for required in ("PASS", "DEGRADED_TO_TEXT", "NO_FIGURE", "FAILED"):
        # FAILED_* 前缀均计入失败分类
        if required == "FAILED":
            assert any(c.startswith("FAILED") for c in classes), classes
        else:
            assert required in classes, classes
    js_classes = {r["js"]["classification"] for r in report["task_records"]}
    assert js_classes == {"JS_STUB_NOT_WIRED"}
    assert report["pairing"]["switch_events"] >= 1


def test_switch_events_keep_operation_identity_and_budget_continuity():
    report = fem.run_fake_evaluation(output_dir=None)
    switch_records = [r for r in report["task_records"] if r.get("switch_event")]
    assert switch_records, "配对子集必须产生至少一条切换记录"
    for r in switch_records:
        ev = r["switch_event"]
        # 切换仍在同一 operation（不含 method），预算不重置（契约 §7.1 :138）
        assert ev["operation_id"] == r["operation_id"]
        assert ev["from_method"] == "IMAGE_MODEL" and ev["to_method"] == "JS"
        # JS 本地执行不占远程派发：切换点前后远程派发数保持连续
        assert ev["dispatches_before_switch"] == r["image_model"]["dispatches_used"]
        assert ev["reason"]
        assert r["image_model"]["dispatches_used"] <= 6


# ---------------------------------------------------------------------------
# 5) 预算口径：六派发总门引用 + 费用未知记未知
# ---------------------------------------------------------------------------

def test_budget_cites_six_dispatch_gate_and_unknown_cost():
    budget = json.loads((HERE / "budget.json").read_text(encoding="utf-8"))
    assert budget["per_operation"]["remote_dispatch_cap"] == 6
    src = json.dumps(budget, ensure_ascii=False)
    assert "ModelTasks.kt" in src and "MAX_DISPATCHES" in src
    assert "visual-generation-contract.md" in src
    cost = budget["cost"]
    assert cost["status"] == "unknown"
    assert cost["amount"] is None  # 未知费用标未知，不写 0（设计§6 :95）


def test_run_respects_dispatch_cap_and_never_zero_cost():
    report = fem.run_fake_evaluation(output_dir=None)
    for r in report["task_records"]:
        assert r["image_model"]["dispatches_used"] <= 6
        assert r["cost"]["amount"] is None and r["cost"]["status"] == "unknown"
        assert r["tokens"]["input"] is None and r["tokens"]["output"] is None
    assert report["budget"]["total_dispatches_used"] <= 120 * 6


# ---------------------------------------------------------------------------
# 6) 门常量按 PRD 原文引用，不发明
# ---------------------------------------------------------------------------

def test_gate_constants_come_from_prd_original_text():
    g = fem.GATES
    assert g["first_pass_rate_min"]["value"] == 0.90
    assert g["within_budget_final_rate_min"]["value"] == 0.95
    assert g["per_group_final_rate_min"]["value"] == 0.90
    assert g["critical_errors_accepted_max"]["value"] == 0
    assert g["first_image_p95_ms_js_max"]["value"] == 30000
    assert g["first_image_p95_ms_image_model_max"]["value"] == 120000
    for key, gate in g.items():
        assert "source" in gate and "2026-10-19" in json.dumps(gate["source"], ensure_ascii=False), key
    # 引用的是这两份原文文件
    srcs = json.dumps(g, ensure_ascii=False)
    assert "product-readiness-design.md" in srcs
    assert "product-readiness-followup-plan.md" in srcs


def test_compute_gates_math_on_synthetic_records():
    records = []
    for i in range(10):
        records.append(
            {
                "task_id": f"S-{i}",
                "group": "g1",
                "first_attempt_pass": i < 9,
                "final_accepted": i < 10,
                "critical_error_accepted": False,
                "image_model": {
                    "first_image_latency_ms": 1000.0 * (i + 1),
                    "classification": "PASS",
                },
                "js": {"first_image_latency_ms": None},
            }
        )
    computed = fem.compute_quality_gates(records, denominator=10)
    assert math.isclose(computed["first_pass_rate"]["value"], 0.9)
    assert math.isclose(computed["within_budget_final_rate"]["value"], 1.0)
    assert computed["first_pass_rate"]["met"] is True
    assert computed["critical_errors_accepted"]["value"] == 0
    p95 = computed["first_image_p95_ms"]["image_model"]
    assert p95 > 0


def test_wilson_interval_reported_with_denominator():
    report = fem.run_fake_evaluation(output_dir=None)
    for name in ("first_pass_rate", "within_budget_final_rate"):
        blk = report["quality_gates"][name]
        assert blk["denominator"] == 120
        lo, hi = blk["wilson95"]
        assert 0.0 <= lo <= blk["value"] <= hi <= 1.0


# ---------------------------------------------------------------------------
# 7) 指标字段与报告工件
# ---------------------------------------------------------------------------

def test_metric_fields_present_per_task_and_aggregate():
    report = fem.run_fake_evaluation(output_dir=None)
    for r in report["task_records"]:
        im = r["image_model"]
        for field in (
            "first_image_latency_ms",
            "total_duration_ms",
            "tokens",
            "cost",
            "dispatches_used",
        ):
            assert field in im
        assert im["total_duration_ms"] >= 0
    agg = report["metrics"]["first_image_latency_ms"]
    assert {"p50", "p95"} <= set(agg["image_model"])
    # 采集点声明必须落盘（真实值待 key，先落字段与采集点）
    assert report["metrics"]["collection_points"]["first_image_latency_ms"]


def test_fake_run_writes_json_and_md_report_artifacts(tmp_path):
    report = fem.run_fake_evaluation(output_dir=tmp_path)
    jpath = tmp_path / "figure_eval_fake_run_report.json"
    mpath = tmp_path / "figure_eval_fake_run_report.md"
    assert jpath.exists() and mpath.exists()
    loaded = json.loads(jpath.read_text(encoding="utf-8"))
    assert loaded["run_type"] == "FAKE_DRIVER_MACHINERY_CHECK"
    md = mpath.read_text(encoding="utf-8")
    assert "UNVERIFIED" in md
    assert "模型标注，无人工专家" in md
    assert "非真实质量" in md


def test_machinery_verdict_independent_of_quality_gates():
    """fake 运行的机器裁决不得被质量门（假数据）结论劫持。"""
    report = fem.run_fake_evaluation(output_dir=None)
    assert report["machinery_check"]["verdict"] in ("PASS", "FAIL")
    assert report["machinery_check"]["quality_gates_are_informational"] is True
