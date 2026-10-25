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
    # 默认运行未注入引擎：JS 后端如实降级为 NOT_AVAILABLE，不得冒充成功。
    assert js_classes == {"JS_NOT_AVAILABLE"}
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


# ---------------------------------------------------------------------------
# 8) JS 后端接线（批次 21 T4）：引擎端口 / NOT_AVAILABLE / 后端切换 / 配对形状
#
# 这些用例只验证**端口机制**：真实 QuickJS 执行归设备轨
# （core:data QuickJsVisualExecutionEngineInstrumentedTest），本文件一律注入 fake
# 引擎，不装载 QuickJS、不触碰 Android。真实引擎适配器（设备/ADB 侧）只需实现
# JsEnginePort 的 availability()/run() 两个方法，经 JsPort(engine=...) 注入。
# ---------------------------------------------------------------------------

_SCENE_JSON = '{"schemaVersion":1,"sceneId":"feval-probe","content":[{"contentId":"c0"}]}'


def _js_task(task_id: str = "BL-T-0001", group: str = "proportion_coordinates") -> dict:
    return {
        "task_id": task_id,
        "set": "blind",
        "group": group,
        "figure_type": "proportion",
        "problem_statement": "测试题面（不得出现在引擎请求里）",
        "fact_expectations": [
            {"kind": "numeric", "key": "ratio", "expected": 0.5, "critical": True}
        ],
        "review_source": {
            "annotator": "model",
            "human_expert": False,
            "evidence_limitation": "模型标注，无人工专家",
        },
    }


def _rendered_result() -> dict:
    return {
        "status": "RENDERED",
        "scene_json": _SCENE_JSON,
        "scene_schema_version": 1,
        "content_ids": ["c0"],
    }


class _FakeVisualEngine:
    """core:domain.VisualExecutionEngine 结果形状的测试替身。"""

    def __init__(self, *, available: bool = True, result: dict | None = None, error=None):
        self._available = available
        self._result = result
        self._error = error
        self.requests: list[dict] = []

    def availability(self) -> dict:
        return {
            "available": self._available,
            "engine": {"name": "fake-visual-engine", "driver": "test"},
            "reason": None if self._available else "fake engine not available",
        }

    def run(self, request: dict) -> dict:
        self.requests.append(request)
        if self._error is not None:
            raise self._error
        return self._result


def test_js_port_without_engine_reports_not_available_and_never_success():
    record = fem.JsPort().evaluate(_js_task())
    assert record["backend"] == "JS"
    assert record["status"] == "NOT_AVAILABLE"
    assert record["classification"] == "JS_NOT_AVAILABLE"
    assert record["artifact"] is None
    assert record["dispatches_used"] == 0
    assert record["first_image_latency_ms"] is None
    assert record["fact_verification"] == "UNVERIFIED"
    assert record["quality_accepted"] is False
    assert "NOT_AVAILABLE" in record["note"]


def test_js_port_does_not_execute_when_probe_says_unavailable():
    engine = _FakeVisualEngine(available=False)
    record = fem.JsPort(engine=engine).evaluate(_js_task())
    assert record["status"] == "NOT_AVAILABLE"
    assert record["classification"] == "JS_NOT_AVAILABLE"
    assert engine.requests == []


def test_js_port_runs_render_scene_program_through_injected_engine():
    engine = _FakeVisualEngine(result=_rendered_result())
    record = fem.JsPort(engine=engine).evaluate(_js_task())

    assert record["status"] == "AVAILABLE"
    # 产物引用 ≠ 合格：分类显式带 UNVERIFIED，事实核验未接。
    assert record["classification"] == "JS_RENDERED_UNVERIFIED"
    assert record["artifact"] == {
        "scene_sha256": hashlib.sha256(_SCENE_JSON.encode("utf-8")).hexdigest(),
        "scene_bytes": len(_SCENE_JSON.encode("utf-8")),
        "scene_schema_version": 1,
        "content_ids": ["c0"],
    }
    assert record["fact_verification"] == "UNVERIFIED"
    assert record["quality_accepted"] is False
    assert record["dispatches_used"] == 0  # JS 本地执行不占远程派发（契约 :138）
    assert record["tokens"]["status"] == "not_applicable_local"
    assert record["cost"]["amount"] is None
    assert record["cost"]["status"] == "not_applicable_local"
    assert record["first_image_latency_ms"] is not None
    assert record["first_image_latency_ms"] >= 0

    (request,) = engine.requests
    assert request["sourceJs"] == fem.RENDER_SCENE_PROGRAM_JS
    for key in (
        "programId",
        "sourceJs",
        "sceneRevision",
        "stateJson",
        "viewport",
        "timeMs",
        "seed",
        "theme",
        "explicitUserRetry",
    ):
        assert key in request, key
    assert json.loads(request["stateJson"]) == {
        "taskId": "BL-T-0001",
        "group": "proportion_coordinates",
    }
    # 防面向结果：端口不得把夹具预期值/题面喂给引擎。
    dumped = json.dumps(request, ensure_ascii=False)
    assert "fact_expectations" not in dumped
    assert "0.5" not in dumped
    assert "测试题面" not in dumped


def test_js_port_maps_engine_rejection_to_bounded_failure():
    engine = _FakeVisualEngine(
        result={
            "status": "REJECTED",
            "reason": "PHASE_TIMEOUT",
            "diagnostics": "x" * 10_000,
        }
    )
    record = fem.JsPort(engine=engine).evaluate(_js_task())

    assert record["status"] == "AVAILABLE"
    assert record["classification"] == "JS_FAILED_PHASE_TIMEOUT"
    assert record["artifact"] is None
    assert record["first_image_latency_ms"] is None
    assert record["quality_accepted"] is False
    assert len(record["diagnostics"]) <= fem.MAX_DIAGNOSTICS_CHARS


def test_js_port_protocol_violation_is_failure_not_success():
    engine = _FakeVisualEngine(result={"status": "OK"})
    record = fem.JsPort(engine=engine).evaluate(_js_task())
    assert record["status"] == "AVAILABLE"
    assert record["classification"] == "JS_FAILED_ENGINE_PROTOCOL"
    assert record["artifact"] is None
    assert record["quality_accepted"] is False


def test_js_port_engine_lost_during_run_degrades_to_not_available():
    engine = _FakeVisualEngine(error=fem.JsEngineUnavailable("device offline"))
    record = fem.JsPort(engine=engine).evaluate(_js_task())
    assert record["status"] == "NOT_AVAILABLE"
    assert record["classification"] == "JS_NOT_AVAILABLE"
    assert "device offline" in record["diagnostics"]
    assert record["artifact"] is None


def test_pairing_record_shape_contains_both_backends_and_honest_js_status():
    report = fem.run_fake_evaluation(output_dir=None)
    for r in report["task_records"]:
        assert list(r["backends"]) == ["IMAGE_MODEL", "JS"]
        assert r["backends"]["IMAGE_MODEL"] is r["image_model"]
        assert r["backends"]["JS"] is r["js"]
        js = r["js"]
        assert js["backend"] == "JS"
        assert js["status"] in ("AVAILABLE", "NOT_AVAILABLE")
        assert js["dispatches_used"] == 0
        assert js["fact_verification"] == "UNVERIFIED"
        assert js["quality_accepted"] is False
        assert js["cost"]["amount"] is None
        if js["status"] == "NOT_AVAILABLE":
            assert js["classification"] == "JS_NOT_AVAILABLE"
            assert js["artifact"] is None
    pairing = report["pairing"]
    assert pairing["primary_backend"] == "IMAGE_MODEL"
    assert pairing["backends"]["IMAGE_MODEL"]["status"] == "FAKE_DRIVER"
    assert pairing["backends"]["JS"]["status"] == "NOT_AVAILABLE"
    assert report["backends"]["enumeration"] == ["IMAGE_MODEL", "JS"]


def test_runner_supports_js_primary_backend_and_bidirectional_switch_records():
    blind = fem.load_frozen_tasks(HERE / "tasks_blind_120.json")
    reject_ids = {
        task["task_id"] for index, task in enumerate(blind) if index % 5 == 0 and index < 10
    }

    class _SwitchingEngine(_FakeVisualEngine):
        def run(self, request: dict) -> dict:
            self.requests.append(request)
            state = json.loads(request["stateJson"])
            if state["taskId"] in reject_ids:
                return {
                    "status": "REJECTED",
                    "reason": "INVALID_PROGRAM",
                    "diagnostics": "probe rejection",
                }
            return _rendered_result()

    engine = _SwitchingEngine()
    report = fem.run_fake_evaluation(
        output_dir=None,
        backend="JS",
        js_port=fem.JsPort(engine=engine),
    )

    assert report["backends"]["enumeration"] == ["IMAGE_MODEL", "JS"]
    assert report["backends"]["primary"] == "JS"
    assert report["pairing"]["primary_backend"] == "JS"
    # JS 产物事实核验未接：质量门不可评，不得把 RENDERED 当合格。
    assert report["quality_gates"]["applies_to"] == "JS"
    assert report["quality_gates"]["evaluable"] is False

    records = report["task_records"]
    assert all(r["primary_backend"] == "JS" for r in records)
    assert all(r["final_accepted"] is False for r in records)
    assert all(r["js"]["classification"] != "JS_NOT_AVAILABLE" for r in records)
    assert any(r["first_attempt_pass"] for r in records)

    switched = [r for r in records if r.get("switch_event")]
    assert switched, "JS 主运行的失败题应记录 JS→IMAGE_MODEL 切换"
    for r in switched:
        event = r["switch_event"]
        assert event["from_method"] == "JS"
        assert event["to_method"] == "IMAGE_MODEL"
        assert event["operation_id"] == r["operation_id"]
        assert event["dispatches_before_switch"] == 0  # JS 本地执行不占远程派发
        assert event["image_model_result"] == r["image_model"]["classification"]
        assert r["js"]["classification"].startswith("JS_FAILED_")


def test_default_run_records_js_not_available_in_machinery_check():
    report = fem.run_fake_evaluation(output_dir=None)
    paths = report["machinery_check"]["exercised_paths"]
    assert paths["js_backend_recorded"] is True
    assert paths["js_backend_honest_status"] is True
    assert paths["js_never_claims_quality"] is True
    assert paths["backends_pairing_complete"] is True
    assert "js_stub_recorded" not in paths
    assert report["machinery_check"]["verdict"] == "PASS"


def test_js_port_probe_exception_degrades_to_not_available():
    class _BrokenProbeEngine:
        def availability(self) -> dict:
            raise RuntimeError("probe boom")

        def run(self, frame: dict) -> dict:
            raise AssertionError("must not run when the probe fails")

    record = fem.JsPort(engine=_BrokenProbeEngine()).evaluate(_js_task())
    assert record["status"] == "NOT_AVAILABLE"
    assert record["classification"] == "JS_NOT_AVAILABLE"
    assert "probe boom" in record["diagnostics"]
    assert record["artifact"] is None


def test_runner_rejects_unknown_backend():
    with pytest.raises(ValueError):
        fem.run_fake_evaluation(output_dir=None, backend="NOT_A_BACKEND")


def test_js_primary_run_refuses_quality_gate_enforcement():
    # JS 事实核验未接，不允许把机制产物当质量门结论。
    with pytest.raises(ValueError):
        fem.run_fake_evaluation(
            output_dir=None,
            backend="JS",
            enforce_quality_gates=True,
        )
