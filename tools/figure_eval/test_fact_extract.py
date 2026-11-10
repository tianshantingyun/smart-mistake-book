# -*- coding: utf-8 -*-
"""事实提取器的机制测试（批次 51 / G4 T27；先红后绿）。

纪律（每条对应一个具体失败）：

1. **提取器不读 `fact_expectations`。** `extract()` 的签名里没有预期值；测试用
   `inspect.signature` 钉住，防止有人"顺便"把夹具塞进提取器。
2. **回放栈漏了必须读错。** 关掉 concat 回放（[fact_extract.replay] 的
   `use_matrix_stack=False`）后，卡片位置全部塌到原点 —— `step_order` 必须判错。
3. **label 不套栈。** 编译器已把 label 投影到根空间；提取器再乘一次组矩阵就是双重
   变换（与 painter 的头号回放错误同型）。测试用平移组把两种结果拉开。
4. **像素不可判的事实必须 fail-closed。** `label_absent` 的夹具值是**类名**
   （"红笔勾"），显示列表里没有这三个字；文本级 disjoint 会假接受。该键一律
   `UNDECODABLE`，不输出值、不冒充通过。
5. **未知 key 不兜底。** 没登记解码规则的 key 直接 `ValueError`，不默认"可判"。
6. **判定仍走既有 `check_fact_expectations`。** 测试把提取值喂给门函数验证语义
   （subset/disjoint/容差）没有被提取器改写。

运行：
    python -m pytest tools/figure_eval/test_fact_extract.py -q
"""

from __future__ import annotations

import inspect
import json
import math
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import fact_extract as fx  # noqa: E402
import figure_eval_gate as fem  # noqa: E402


# ---------------------------------------------------------------------------
# 夹具构造（手写 DrawOps V1；键面与 VisualDrawOpsParser.serialize 同形）
# ---------------------------------------------------------------------------

def label(content_id: str, x: float, y: float, *, alpha: float = 1.0,
          h_align: str = "left", v_align: str = "top") -> dict:
    return {
        "op": "label", "contentId": content_id, "x": x, "y": y,
        "rotationDeg": 0, "hAlign": h_align, "vAlign": v_align,
        "color": "#333333", "alpha": alpha,
    }


def path(commands: list, *, fill: str | None = "#333333", alpha: float = 1.0) -> dict:
    return {
        "op": "path", "path": commands, "fill": fill,
        "stroke": None if fill is not None else {
            "width": 2, "cap": "butt", "join": "miter", "miterLimit": 4, "dash": None,
        },
        "alpha": alpha,
    }


def doc(ops: list, *, hits: list | None = None) -> dict:
    labels = sum(1 for op in ops if op["op"] == "label")
    return {
        "drawSchemaVersion": 1,
        "viewBox": {"x": 0, "y": 0, "w": 360, "h": 240},
        "background": None,
        "ops": ops,
        "hitRegions": hits or [],
        "stats": {"nodes": 0, "pathCommands": 0, "labels": labels, "ops": len(ops)},
    }


def metric(content_id: str, text: str, *, width: float = 10.0, height: float = 12.0,
           baseline: float = 9.0) -> dict:
    return {
        "contentId": content_id, "payload": text, "widthDu": width,
        "heightDu": height, "baselineDu": baseline, "fontSizeSp": 16.0,
        "digest": "test:" + content_id,
    }


def extract(ops_doc: dict, metrics: list[dict], keys: list[str], **kwargs) -> fx.Extraction:
    return fx.extract(ops_doc, metrics, keys=keys, **kwargs)


def values(extraction: fx.Extraction) -> dict:
    return {key: entry["value"] for key, entry in extraction.facts.items()
            if entry["status"] == fx.DECODED}


def status(extraction: fx.Extraction, key: str) -> str:
    return extraction.facts[key]["status"]


# ---------------------------------------------------------------------------
# 1) 签名：不读预期值
# ---------------------------------------------------------------------------

def test_extract_signature_has_no_expectation_input():
    params = list(inspect.signature(fx.extract).parameters)
    assert params == ["draw_ops", "metrics", "keys", "scene", "role_map",
                      "replay_matrix_stack"], params


def test_extraction_is_independent_of_any_expectation_table():
    ops = doc([path([["M", 40, 100], ["L", 100, 100], ["M", 100, 100], ["L", 160, 100]]),
               label("c-ratio", 100, 118)])
    metrics = [metric("c-ratio", "1:1")]
    keys = ["segment_length_ratio", "ratio_label", "segment_count"]
    first = values(extract(ops, metrics, keys))
    second = values(extract(ops, metrics, keys))
    assert first == second
    # 没有任何调用参数携带预期值；"改预期不改事实"由签名结构性保证。
    assert "expect" not in "".join(inspect.signature(fx.extract).parameters)  # 反向断言


# ---------------------------------------------------------------------------
# 2) 回放：栈必须回放，label 不回放
# ---------------------------------------------------------------------------

def test_path_in_group_is_replayed_to_root_space():
    ops = doc([
        {"op": "save"},
        {"op": "concat", "m": [1, 0, 0, 1, 100, 50]},
        path([["M", 0, 0], ["L", 40, 0]]),
        {"op": "restore"},
    ])
    obs = fx.observe(ops, [])
    run = obs.runs[0]
    assert run["lengths"] == [40.0]
    assert min(p[0] for p in obs.subpaths[0]["points"] if p) == 100.0
    assert max(p[0] for p in obs.subpaths[0]["points"] if p) == 140.0


def test_label_inside_translated_group_is_not_double_transformed():
    ops = doc([
        {"op": "save"},
        {"op": "concat", "m": [1, 0, 0, 1, 100, 50]},
        label("c-1", 10, 20),
        {"op": "restore"},
    ])
    obs = fx.observe(ops, [metric("c-1", "第1步")])
    assert obs.labels[0]["anchor_x"] == pytest.approx(10.0)
    # label 的 y 是 baseline（KDoc）；vAlign=top 时文本顶边 = y - baseline = 20 - 9。
    assert obs.labels[0]["anchor_y"] == pytest.approx(11.0)


def test_missing_matrix_stack_replay_reads_wrong_step_order():
    def card(y: float, text: str, content_id: str) -> list[dict]:
        return [
            {"op": "save"},
            {"op": "concat", "m": [1, 0, 0, 1, 0, y]},
            path([["M", 60, 0], ["L", 300, 0], ["L", 300, 50], ["L", 60, 50], ["Z"]]),
            {"op": "restore"},
            label(content_id, 180, y + 25),
        ]

    ops = doc(card(10, "移项", "c-0") + card(80, "合并同类项", "c-1") + card(150, "系数化为1", "c-2"))
    metrics = [metric("c-0", "移项"), metric("c-1", "合并同类项"), metric("c-2", "系数化为1")]
    keys = ["step_order", "step_count"]

    good = values(extract(ops, metrics, keys))
    assert good["step_order"] == ["移项", "合并同类项", "系数化为1"]
    assert good["step_count"] == 3

    # 手工禁用栈回放：三张卡片全部塌到 y=0，顺序读数不再等于真值。
    broken = values(extract(ops, metrics, keys, replay_matrix_stack=False))
    assert broken["step_order"] != ["移项", "合并同类项", "系数化为1"]
    assert broken["step_count"] == 3


# ---------------------------------------------------------------------------
# 3) 判定语义仍走既有 check_fact_expectations
# ---------------------------------------------------------------------------

def ratio_task_expectations() -> list[dict]:
    return [
        {"kind": "numeric", "key": "segment_length_ratio", "expected": 1.0,
         "tolerance": 1e-09, "critical": True},
        {"kind": "label_set", "key": "ratio_label", "expected": ["1:1"],
         "critical": True},
        {"kind": "count", "key": "segment_count", "expected": 2, "critical": False},
    ]


def test_ratio_bar_extraction_passes_existing_checker():
    ops = doc([path([["M", 40, 100], ["L", 100, 100], ["M", 100, 100], ["L", 160, 100]]),
               label("c-ratio", 100, 118)])
    extraction = extract(ops, [metric("c-ratio", "1:1")],
                         ["segment_length_ratio", "ratio_label", "segment_count"])
    verdict = fem.check_fact_expectations(
        [{"key": k, "value": v} for k, v in values(extraction).items()],
        ratio_task_expectations(),
    )
    assert verdict["accepted"] is True
    assert verdict["verified"] == 3


def test_geometry_correct_label_wrong_fails_only_the_label_fact():
    ops = doc([path([["M", 40, 100], ["L", 100, 100], ["M", 100, 100], ["L", 160, 100]]),
               label("c-ratio", 100, 118)])
    extraction = extract(ops, [metric("c-ratio", "3:1")],
                         ["segment_length_ratio", "ratio_label", "segment_count"])
    verdict = fem.check_fact_expectations(
        [{"key": k, "value": v} for k, v in values(extraction).items()],
        ratio_task_expectations(),
    )
    assert verdict["accepted"] is False
    assert [m["key"] for m in verdict["mismatches"]] == ["ratio_label"]


def test_wrong_ratio_is_reported_with_fixture_tolerance_semantics():
    ops = doc([path([["M", 40, 100], ["L", 160, 100], ["M", 160, 100], ["L", 200, 100]])])
    extraction = extract(ops, [], ["segment_length_ratio"])
    assert values(extraction)["segment_length_ratio"] == pytest.approx(3.0)
    verdict = fem.check_fact_expectations(
        [{"key": "segment_length_ratio", "value": values(extraction)["segment_length_ratio"]}],
        ratio_task_expectations()[:1],
    )
    assert verdict["accepted"] is False


# ---------------------------------------------------------------------------
# 4) 可见性：clip 掩码与 alpha=0
# ---------------------------------------------------------------------------

def test_clip_masks_out_labels_and_shapes():
    ops = doc([
        {"op": "save"},
        {"op": "clip", "path": [["M", 0, 0], ["L", 100, 0], ["L", 100, 240],
                                ["L", 0, 240], ["Z"]], "rule": "nonzero"},
        path([["M", 120, 40], ["L", 160, 40], ["L", 160, 80], ["L", 120, 80], ["Z"]]),
        label("c-visible", 50, 60),
        label("c-clipped", 150, 60),
        {"op": "restore"},
    ])
    obs = fx.observe(ops, [metric("c-visible", "可见"), metric("c-clipped", "被裁")])
    texts = [item["text"] for item in obs.visible_labels]
    assert texts == ["可见"]
    assert obs.shapes == []  # 完全落在裁剪区外的形状不计数
    assert obs.partially_clipped is False  # 完全在外 ≠ 部分被裁


def test_partially_clipped_shape_is_not_counted_and_is_flagged():
    ops = doc([
        {"op": "save"},
        {"op": "clip", "path": [["M", 0, 0], ["L", 100, 0], ["L", 100, 240],
                                ["L", 0, 240], ["Z"]], "rule": "nonzero"},
        path([["M", 80, 40], ["L", 140, 40], ["L", 140, 80], ["L", 80, 80], ["Z"]]),
        {"op": "restore"},
    ])
    obs = fx.observe(ops, [])
    assert obs.shapes == []
    assert obs.partially_clipped is True


def test_zero_alpha_label_is_invisible():
    ops = doc([label("c-1", 20, 20, alpha=0.0), label("c-2", 40, 20, alpha=1.0)])
    obs = fx.observe(ops, [metric("c-1", "消失"), metric("c-2", "保留")])
    assert [item["text"] for item in obs.visible_labels] == ["保留"]


# ---------------------------------------------------------------------------
# 5) label_absent / 未知 key：fail-closed
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("key", ["removed_handwriting", "erased_answer"])
def test_label_absent_is_undecodable_not_a_pass(key):
    ops = doc([label("c-1", 20, 20)])
    extraction = extract(ops, [metric("c-1", "题干第(1)问")], [key])
    entry = extraction.facts[key]
    assert entry["status"] == fx.UNDECODABLE
    assert "value" not in entry
    assert "C" in entry["reason"]


def test_unknown_key_fails_closed_instead_of_defaulting_to_decodable():
    with pytest.raises(ValueError):
        fx.extract(doc([path([["M", 0, 0], ["L", 1, 0]])]), [], keys=["not_a_fact_key"])


# ---------------------------------------------------------------------------
# 6) 结构化解码器：网格 / 轴 / 坐标点 / 数轴
# ---------------------------------------------------------------------------

def grid_doc(rows: int, columns: int) -> dict:
    ops = []
    for r in range(rows + 1):
        y = 40 + 20 * r
        ops.append(path([["M", 40, y], ["L", 40 + 20 * columns, y]]))
    for c in range(columns + 1):
        x = 40 + 20 * c
        ops.append(path([["M", x, 40], ["L", x, 40 + 20 * rows]]))
    return doc(ops)


def test_grid_counts_match_the_drawn_lines():
    extraction = extract(grid_doc(4, 4), [], ["row_count", "column_count", "cell_count"])
    got = values(extraction)
    assert got == {"row_count": 4.0, "column_count": 4.0, "cell_count": 16}


def axis_doc(values_: list[float], y: float = 200.0) -> tuple[dict, list[dict]]:
    ops = [path([["M", 40, y], ["L", 320, y]])]
    metrics = []
    for index, value in enumerate(values_):
        x = 40 + 70 * index
        content = f"c-tick-{index}"
        ops.append(label(content, x, y + 8, h_align="center"))
        metrics.append(metric(content, str(value), width=20.0))
    return doc(ops), metrics


def test_axis_min_max_and_tick_count_come_from_the_labeled_ticks():
    ops, metrics = axis_doc([0.0, 0.25, 0.5, 0.75, 1.0])
    got = values(extract(ops, metrics, ["axis_min", "axis_max", "tick_count"]))
    assert got == {"axis_min": 0.0, "axis_max": 1.0, "tick_count": 5}


def coordinate_doc() -> tuple[dict, list[dict]]:
    ops = [
        path([["M", 40, 200], ["L", 320, 200]]),   # x 轴
        path([["M", 40, 200], ["L", 40, 20]]),     # y 轴
    ]
    metrics = []
    # x 轴刻度：0,1,2 在 (100,200) (160,200) (220,200)
    for index, (x, value) in enumerate([(100, "0"), (160, "1"), (220, "2")]):
        content = f"c-x{index}"
        ops.append(label(content, x, 208, h_align="center", v_align="top"))
        metrics.append(metric(content, value, width=10.0))
    # y 轴刻度：0,1,2 在 (40,200) (40,140) (40,80)
    for index, (y, value) in enumerate([(200, "0"), (140, "1"), (80, "2")]):
        content = f"c-y{index}"
        ops.append(label(content, 32, y, h_align="right", v_align="middle"))
        metrics.append(metric(content, value, width=10.0, height=12.0))
    # 点标记（小菱形）：中心 (165,137) 与 x 轴刻度 1、y 轴刻度 1 同位置。
    ops.append(path([["M", 165, 131], ["L", 171, 137], ["L", 165, 143],
                     ["L", 159, 137], ["Z"]]))
    ops.append(label("c-point", 175, 129))
    metrics.append(metric("c-point", "P(1,1)"))
    return doc(ops), metrics


def test_coordinate_point_is_calibrated_from_labeled_axes():
    ops, metrics = coordinate_doc()
    got = values(extract(ops, metrics, ["point_x", "point_y", "point_label"]))
    assert got["point_x"] == pytest.approx(1.0, abs=1e-9)
    assert got["point_y"] == pytest.approx(1.0, abs=1e-9)
    # label_set 的判定是既有 subset 语义（多报不失败；报告须写 verified_with_extras）。
    verdict = fem.check_fact_expectations(
        [{"key": "point_label", "value": got["point_label"]}],
        [{"kind": "label_set", "key": "point_label", "expected": ["P(1,1)"],
          "critical": False}],
    )
    assert verdict["accepted"] is True
    assert set(got["point_label"]) > {"P(1,1)"}  # 刻度标签也在可见文本里（已登记宽松点）


def number_line_doc() -> tuple[dict, list[dict]]:
    # 标定：刻度标签中心 0 在 x=105、1 在 x=265；1/3→158.333，1/2→185。
    third_x = 105 + 160 / 3
    half_x = 185
    ops = [
        path([["M", 40, 200], ["L", 320, 200]]),
        label("c-0", 100, 208, h_align="center"),
        label("c-1", 260, 208, h_align="center"),
        path([["M", third_x, 194], ["L", third_x + 6, 200], ["L", third_x, 206],
              ["L", third_x - 6, 200], ["Z"]]),
        path([["M", half_x, 194], ["L", half_x + 6, 200], ["L", half_x, 206],
              ["L", half_x - 6, 200], ["Z"]]),
        label("c-third", third_x, 180, h_align="center"),
        label("c-half", half_x, 160, h_align="center"),
    ]
    metrics = [
        metric("c-0", "0", width=10.0), metric("c-1", "1", width=10.0),
        # 分数标签的文本宽度置 0：锚点即标记点位置（避免测试把标签盒偏移当几何）。
        metric("c-third", "1/3", width=0.0), metric("c-half", "1/2", width=0.0),
    ]
    return doc(ops), metrics


def test_number_line_value_and_order_are_read_from_geometry():
    ops, metrics = number_line_doc()
    got = values(extract(ops, metrics, ["value_of_1_3", "value_of_1_2",
                                        "ascending_order", "marked_points"]))
    assert got["value_of_1_3"] == pytest.approx(1 / 3, abs=1e-9)
    assert got["value_of_1_2"] == pytest.approx(1 / 2, abs=1e-9)
    assert got["ascending_order"] == ["1/3", "1/2"]
    assert got["marked_points"] == 2


def test_printed_values_are_read_from_text_not_from_geometry():
    ops = doc([
        label("c-disc", 40, 40),
        label("c-ms", 40, 80),
        label("c-cm", 40, 120),
    ])
    metrics = [
        metric("c-disc", "Δ=b²−4ac=49", width=120.0),
        metric("c-ms", "72 km/h = 20 m/s", width=120.0),
        metric("c-cm", "实际距离 150000 厘米", width=120.0),
    ]
    got = values(extract(ops, metrics, ["discriminant", "converted_ms", "real_distance_cm"]))
    assert got == {"discriminant": 49.0, "converted_ms": 20.0, "real_distance_cm": 150000.0}


# ---------------------------------------------------------------------------
# 7) 角色绑定：SceneV1 id → op 索引（A2 的载体，未声明即不可判）
# ---------------------------------------------------------------------------

def scene_with_ids() -> dict:
    return {
        "schemaVersion": 1, "sceneId": "s", "revision": 1,
        "viewBox": {"x": 0, "y": 0, "width": 360, "height": 240},
        "paths": [{"id": "bar", "commands": [["M", 40, 100], ["L", 160, 100]]}],
        "root": {"type": "group", "children": [
            {"type": "group", "id": "g1", "matrix": [1, 0, 0, 1, 0, 20], "children": [
                {"type": "path", "id": "bar-node", "pathId": "bar", "fill": "#333333"},
            ]},
            {"type": "label", "id": "ratio-label", "contentId": "c-ratio",
             "anchor": {"x": 100, "y": 130}, "hAlign": "center", "vAlign": "bottom",
             "color": "#333333"},
        ]},
    }


def scene_draw_ops() -> dict:
    # 编译器发射顺序：save? → concat → path → restore；label 在组外。
    return doc([
        {"op": "save"},
        {"op": "concat", "m": [1, 0, 0, 1, 0, 20]},
        path([["M", 40, 100], ["L", 160, 100]]),
        {"op": "restore"},
        label("c-ratio", 100, 130, h_align="center", v_align="bottom"),
    ])


def test_scene_node_ids_bind_to_op_indices_by_traversal_order():
    binding = fx.bind_scene_nodes(scene_with_ids(), scene_draw_ops())
    assert binding["bar-node"]["op_index"] == 2
    assert binding["ratio-label"]["op_index"] == 4
    assert binding["bar-node"]["node_type"] == "path"


def test_scene_binding_mismatch_fails_closed():
    broken = doc([path([["M", 0, 0], ["L", 10, 0]])])
    with pytest.raises(ValueError):
        fx.bind_scene_nodes(scene_with_ids(), broken)


def test_role_map_absent_keeps_a2_keys_undecodable():
    ops, metrics = coordinate_doc()
    extraction = extract(ops, metrics, ["point_x"])
    assert status(extraction, "point_x") == fx.DECODED  # 结构化可判：唯一小标记
    extraction = extract(ops, metrics, ["arm_length_ratio"])
    assert status(extraction, "arm_length_ratio") == fx.UNDECODABLE


def test_ambiguous_marked_points_fail_closed():
    ops, metrics = coordinate_doc()
    ops["ops"].append(path([["M", 200, 194], ["L", 206, 200], ["L", 200, 206],
                            ["L", 194, 200], ["Z"]]))
    extraction = extract(ops, metrics, ["point_x"])
    assert status(extraction, "point_x") == fx.UNDECODABLE
    assert "不唯一" in extraction.facts["point_x"]["reason"]


# ---------------------------------------------------------------------------
# 7b) 任务级可评性：critical 不可判 ⇒ NOT_EVALUABLE，summary 标 partial
# ---------------------------------------------------------------------------

def test_critical_undecodable_makes_the_task_not_evaluable_and_summary_partial():
    task = {
        "task_id": "BLD-DEHANDWR-001",
        "fact_expectations": [
            {"kind": "label_absent", "key": "removed_handwriting",
             "expected": ["红笔勾", "对号", "波浪线"], "critical": True},
        ],
    }
    extraction = extract(doc([label("c-1", 20, 20)]),
                         [metric("c-1", "题干第(1)问")], ["removed_handwriting"])
    record = fx.task_record(task, extraction)
    assert record["evaluable"] is False
    assert record["blocking_undecodable"] == ["removed_handwriting"]
    summary = fx.summarize([record])
    assert summary["partial"] is True
    assert summary["coverage"] == 0.0
    assert summary["blocking_by_key"] == {"removed_handwriting": 1}


def test_non_critical_undecodable_does_not_block_the_task():
    task = {
        "task_id": "X",
        "fact_expectations": [
            {"kind": "label_absent", "key": "removed_handwriting",
             "expected": ["对号"], "critical": False},
        ],
    }
    extraction = extract(doc([label("c-1", 20, 20)]),
                         [metric("c-1", "题干")], ["removed_handwriting"])
    assert fx.task_record(task, extraction)["evaluable"] is True


# ---------------------------------------------------------------------------
# 8) 生产编译器产物回归（probe_outputs.json；不是手写夹具）
# ---------------------------------------------------------------------------

PROBE_OUTPUTS = (
    Path(__file__).resolve().parents[2]
    / "docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/probe_outputs.json"
)


@pytest.mark.skipif(not PROBE_OUTPUTS.is_file(), reason="probe_outputs.json 不在树上")
def test_production_compiler_outputs_match_the_probe_expectations():
    probe = json.loads(PROBE_OUTPUTS.read_text(encoding="utf-8"))
    blind = json.loads((HERE / "tasks_blind_120.json").read_text(encoding="utf-8"))["tasks"]
    tasks = {task["task_id"]: task for task in blind}
    cases = {case["name"]: case for case in probe["cases"]}
    checks = [
        ("ratio-1to1-correct", "BLD-PROPCOORD-001", True),
        ("ratio-2to3-correct", "BLD-PROPCOORD-005", True),
        ("ratio-1to1-rotated", "BLD-PROPCOORD-001", True),
        ("ratio-1to1-correct", "BLD-PROPCOORD-002", False),
        ("ratio-2to3-correct", "BLD-PROPCOORD-001", False),
        ("ratio-label-mismatch", "BLD-PROPCOORD-001", False),
        ("flow-order-correct", "BLD-FLOWFORM-001", True),
        ("flow-order-reversed", "BLD-FLOWFORM-001", False),
        ("handwriting-residue-present", "BLD-DEHANDWR-001", False),
        ("handwriting-residue-absent", "BLD-DEHANDWR-001", False),
    ]
    for case_name, task_id, want in checks:
        case = cases[case_name]
        task = tasks[task_id]
        metrics = [
            dict(metric, payload=case.get("contents", {}).get(metric["contentId"]))
            for metric in case["metrics"]
        ]
        keys = [fact["key"] for fact in task["fact_expectations"]]
        extraction = fx.extract(case["drawOps"], metrics, keys=keys)
        verdict = fem.check_fact_expectations(
            extraction.as_output_facts(), task["fact_expectations"])
        assert verdict["accepted"] is want, (
            f"{case_name} vs {task_id}: got {verdict['accepted']}, want {want}, "
            f"facts={extraction.decoded}, undecodable={extraction.undecodable}")
    # 去手写题的两条 label_absent 必须如实 UNDECODABLE（不是假通过）。
    residue = fx.extract(
        cases["handwriting-residue-present"]["drawOps"],
        [dict(metric, payload=cases["handwriting-residue-present"].get("contents", {}).get(
            metric["contentId"])) for metric in cases["handwriting-residue-present"]["metrics"]],
        keys=["removed_handwriting"],
    )
    assert residue.facts["removed_handwriting"]["status"] == fx.UNDECODABLE
