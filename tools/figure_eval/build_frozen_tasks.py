# -*- coding: utf-8 -*-
"""冻结 PRD-09 生图评测盲测任务集（批次 20 / T5，2026-10-25）。

产出（本目录）：
- tasks_blind_120.json：120 个盲测任务（6 组 × 20，组名取自
  `docs/superpowers/specs/2026-10-19-product-readiness-design.md` §6 :91 原文：
  比例/坐标、力学/阶段、流程/公式、源图重建、去手写、自然插画）。
- tasks_dev_24.json：24 个开发集任务（6 组 × 4），**另设、不混入 120 分母**
  （PRD-09 `docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:132`）。
- freeze_manifest.json：两份任务文件的 SHA-256 冻结清单；评测门运行前校验，
  防"看到结果后改夹具"（面向结果）。修改任务集必须重新冻结并留下新 frozen_at。

纪律（本生成器消灭的失败）：
1. 事实预期由题族参数**独立计算**（numeric/ordering/count……），从不来自任何
   模型/驱动输出——满足 PRD-09 :132 "解析可计算的图用独立预期核验，不能只读
   模型自己输出的断言"。
2. 盲测题不得用于调 prompt（设计§6 :91）；本集一经冻结即为启用判定输入，
   改 prompt 后的启用判定须换新的未见盲测集。
3. 每个任务必带 review_source（评审来源）字段；当前只能"模型标注，无人工
   专家"，如实标注（PRD-09 :132 "只用模型审评时明确证据限制"）。
4. 生成完全确定（固定种子与 frozen_at），重跑得到逐字节相同输出。

图类型与六类样例的对应（`docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md`
:154-164 设计表）：ratio_bar/function_area_plot/phase_diagram/qa_form/force_diagram/
wave_superposition 等；原生问答表单（截图 4，4E.2）**另验**，不入本 120 图义分母
（设计§6 :91 "原生表单另验"）。桌面 33 场景研究夹具
（`.jez/artifacts/flexible-js-drawing-2026-10-17/output/programs.json`，程序
work/cork/waves）在可对应的任务上以 research_fixture_ref 引用，供核查。

用法：
    python tools/figure_eval/build_frozen_tasks.py
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from random import Random

HERE = Path(__file__).resolve().parent

FROZEN_AT = "2026-10-25T00:00:00+08:00"  # 批次 20 T5 冻结时刻（固定，保证可重现）
BLIND_SEED = 20261025
DEV_SEED = 20261025

REVIEW_SOURCE = {
    "annotator": "model_annotated",
    "human_expert": False,
    "model_note": "冻结时按题族参数生成事实预期；无人工专家复核",
    "evidence_limitation": "模型标注，无人工专家；分歧未决不计合格（PRD-09 :132）",
}

FIXTURE_WORK = ".jez/artifacts/flexible-js-drawing-2026-10-17/output/programs.json#work"
FIXTURE_CORK = ".jez/artifacts/flexible-js-drawing-2026-10-17/output/programs.json#cork"
FIXTURE_WAVES = ".jez/artifacts/flexible-js-drawing-2026-10-17/output/programs.json#waves"


def _num(key: str, expected: float, tol: float, critical: bool, unit: str | None = None):
    e = {"kind": "numeric", "key": key, "expected": expected, "tolerance": tol,
         "critical": critical}
    if unit is not None:
        e["unit"] = unit
    return e


def _labels(key: str, expected: list[str], critical: bool):
    return {"kind": "label_set", "key": key, "expected": expected, "critical": critical}


def _absent(key: str, expected: list[str], critical: bool):
    return {"kind": "label_absent", "key": key, "expected": expected, "critical": critical}


def _dir(key: str, expected: str, critical: bool):
    return {"kind": "direction", "key": key, "expected": expected, "critical": critical}


def _order(key: str, expected: list[str], critical: bool):
    return {"kind": "ordering", "key": key, "expected": expected, "critical": critical}


def _count(key: str, expected: int, critical: bool):
    return {"kind": "count", "key": key, "expected": expected, "critical": critical}


# ---------------------------------------------------------------------------
# 题族：每族一个 builder(rng, idx) -> task dict（不含 task_id/set，由外层补）
# ---------------------------------------------------------------------------

def fam_ratio_bar(rng: Random, i: int) -> dict:
    a, b = rng.choice([(3, 1), (1, 1), (2, 3), (5, 2), (4, 1), (3, 2), (7, 3)])
    return {
        "figure_type": "ratio_bar",
        "sample_category_4e": "截图1 比例条（3:1/1:1 精确比例、标签可读，非截图图片）",
        "problem_statement": (
            f"画一条线段并分成两段，使两段长度比为 {a}:{b}；"
            f"在图中以可读标签标注比值 “{a}:{b}”，不得用照片截图充当。"
        ),
        "fact_expectations": [
            _num("segment_length_ratio", a / b, 1e-9, True),
            _labels("ratio_label", [f"{a}:{b}"], True),
            _count("segment_count", 2, False),
        ],
    }


def fam_coordinate_point(rng: Random, i: int) -> dict:
    x, y = rng.choice([(-3, 2), (2, -4), (5, 5), (-2, -3), (4, 1), (-5, 4), (0, 3), (6, -2)])
    quadrant = ("第一" if x > 0 and y > 0 else "第二" if x < 0 and y > 0 else
                "第三" if x < 0 and y < 0 else "第四")
    return {
        "figure_type": "coordinate_point_plot",
        "sample_category_4e": "比例/坐标类：坐标精确标注",
        "problem_statement": (
            f"在平面直角坐标系中标出点 P({x},{y})，画出该点到两坐标轴的虚线垂线，"
            f"并标注其所在象限（{quadrant}象限）。"
        ),
        "fact_expectations": [
            _num("point_x", float(x), 1e-9, True),
            _num("point_y", float(y), 1e-9, True),
            _labels("point_label", [f"P({x},{y})"], False),
        ],
    }


def fam_number_line(rng: Random, i: int) -> dict:
    p, q, r, s = rng.choice([(1, 2, 2, 3), (1, 3, 1, 2), (2, 3, 3, 4), (3, 4, 1, 4),
                             (1, 5, 2, 5), (2, 5, 3, 5), (3, 8, 1, 2)])
    v1, v2 = p / q, r / s
    ordered = [f"{p}/{q}", f"{r}/{s}"] if v1 < v2 else [f"{r}/{s}", f"{p}/{q}"]
    return {
        "figure_type": "number_line",
        "sample_category_4e": "比例/坐标类：数轴刻度与顺序",
        "problem_statement": (
            f"在数轴上标出 {p}/{q} 与 {r}/{s} 的位置（刻度一致、间距按真实值），"
            f"并用从小到大的顺序标注两个数。"
        ),
        "fact_expectations": [
            _order("ascending_order", ordered, True),
            _count("marked_points", 2, False),
            _num(f"value_of_{p}_{q}".replace("/", "_"), v1, 1e-9, False),
        ],
    }


def fam_map_scale(rng: Random, i: int) -> dict:
    n, d = rng.choice([(10000, 3), (50000, 4), (25000, 6), (100000, 2), (20000, 5),
                       (5000, 8), (10000, 7)])
    return {
        "figure_type": "map_scale_segment",
        "sample_category_4e": "比例/坐标类：比例尺换算",
        "problem_statement": (
            f"地图比例尺为 1:{n}，图上两地距离 {d} 厘米。画一条带刻度的线段表示该段"
            f"路程，并标注实际距离 {d * n} 厘米。"
        ),
        "fact_expectations": [
            _num("real_distance_cm", float(d * n), 1e-6, True, unit="cm"),
            _labels("scale_label", [f"1:{n}"], False),
        ],
    }


def fam_fx_area(rng: Random, i: int) -> dict:
    return {
        "figure_type": "function_area_plot",
        "sample_category_4e": "截图2 F-x 面积（F(x)=F0(1-x/h)，x=h/2 时面积=3F0h/8）",
        "research_fixture_ref": FIXTURE_WORK,
        "problem_statement": (
            "画出 F(x)=F0(1−x/h) 在 0≤x≤h 上的图像，用阴影表示 0 到 h/2 与 x 轴围成"
            "的面积，并给出该面积以 W/(F0h) 计的读数（解析值 3/8=0.375）。"
            "这是函数绘图 oracle，不假设真实拔塞题总拉力终点为 0。"
        ),
        "fact_expectations": [
            _num("area_W_over_F0h_at_half", 0.375, 1e-9, True),
            _labels("axis_labels", ["F/F₀", "x/h"], True),
            _num("curve_value_at_x_equals_h", 0.0, 1e-9, True),
        ],
    }


def fam_force_diagram(rng: Random, i: int) -> dict:
    if i % 2 == 0:
        return {
            "figure_type": "force_diagram_incline",
            "sample_category_4e": "截图5 受力图（方向遵循物理条件）",
            "problem_statement": (
                "物块静止在倾角为 30° 的斜面上。画出受力示意图：重力 G 竖直向下、"
                "支持力 N 垂直斜面、摩擦力 f 沿斜面向上（阻碍下滑趋势）。"
                "不得把摩擦力画成水平方向。"
            ),
            "fact_expectations": [
                _dir("friction_direction", "along_incline_up", True),
                _dir("gravity_direction", "down", True),
                _labels("force_labels", ["重力G", "支持力N", "摩擦力f"], True),
            ],
        }
    return {
        "figure_type": "force_diagram_cork",
        "sample_category_4e": "截图5 受力图（摩擦/气压合力按实际向下标注，不照搬横箭头误导）",
        "research_fixture_ref": FIXTURE_CORK,
        "problem_statement": (
            "向上拔瓶塞时，画木塞受力示意：拉力 F 向上，瓶内气压与摩擦的合力按实际"
            "方向向下标注；不得沿用原截图用横箭头“区分力”的画法。"
        ),
        "fact_expectations": [
            _dir("pressure_friction_resultant_direction", "down", True),
            _dir("pull_direction", "up", True),
            _labels("force_labels", ["拉力F"], False),
        ],
    }


def fam_phase_diagram(rng: Random, i: int) -> dict:
    return {
        "figure_type": "cork_phase_diagram",
        "sample_category_4e": "截图6 拔塞阶段（x=0、h/2、h 相对位移、无裁切、公式不显示原始 TeX）",
        "research_fixture_ref": FIXTURE_CORK,
        "problem_statement": (
            "画拔塞过程的三个阶段图：u=x/h = 0、1/2、1 时木塞相对瓶口基准的位置"
            "（u=1 时木塞底部到达瓶口），瓶口基准线保持水平，位移按真实比例，"
            "公式以可读文本呈现而不是原始 TeX。"
        ),
        "fact_expectations": [
            _order("phase_order", ["u=0", "u=1/2", "u=1"], True),
            _num("u_at_half_height", 0.5, 1e-9, True),
            _labels("baseline_label", ["瓶口基准"], False),
        ],
    }


def fam_wave_superposition(rng: Random, i: int) -> dict:
    a1, a2 = rng.choice([(1.0, 0.5), (2.0, 1.0), (1.5, 1.5), (0.8, 0.4), (2.5, 1.5)])
    return {
        "figure_type": "wave_superposition",
        "sample_category_4e": "新组合样例：波叠加/轨迹采样（只新增模型程序与数据）",
        "research_fixture_ref": FIXTURE_WAVES,
        "problem_statement": (
            f"两列同频同相正弦波振幅分别为 {a1:g} 与 {a2:g}，画出各自波形与叠加后的"
            f"合成波形，并标注合成振幅 {a1 + a2:g}。"
        ),
        "fact_expectations": [
            _num("resultant_amplitude", a1 + a2, 1e-9, True),
            _count("wave_count", 2, False),
            _labels("wave_labels", ["合成"], False),
        ],
    }


def fam_flow_card(rng: Random, i: int) -> dict:
    a, b, c, d = rng.choice([(2, 3, 5, 7), (3, -2, 1, 4), (5, 2, 2, 8), (4, 1, 1, 9),
                             (7, -3, 1, 4), (6, 5, 2, 11)])
    return {
        "figure_type": "flow_card",
        "sample_category_4e": "截图3 解题流程（原生卡片与公式，不把流程强制画进 Canvas）",
        "problem_statement": (
            f"做一张解一元一次方程 {a}x+{b}={c}x+{d} 的流程卡：按“移项 → 合并同类项 → "
            "系数化为 1”三步组织，每步给出该方程的具体算式。"
        ),
        "fact_expectations": [
            _order("step_order", ["移项", "合并同类项", "系数化为1"], True),
            _count("step_count", 3, False),
        ],
    }


def fam_formula_render(rng: Random, i: int) -> dict:
    a, b, c = rng.choice([(1, -3, 2), (2, 4, -6), (1, 2, 1), (3, -5, -2), (1, -4, 4),
                          (2, -7, 3)])
    return {
        "figure_type": "formula_render",
        "sample_category_4e": "流程/公式类：公式标注与判别式",
        "problem_statement": (
            f"渲染一元二次方程 {a}x²{'+' if b >= 0 else ''}{b}x{'+' if c >= 0 else ''}{c}=0 "
            f"的求根公式图示：用颜色/引线标出 a={a}、b={b}、c={c} 的对应位置，"
            f"并标注判别式 Δ=b²−4ac={b * b - 4 * a * c}。公式以可读形式呈现，"
            "不得泄漏原始 TeX。"
        ),
        "fact_expectations": [
            _num("discriminant", float(b * b - 4 * a * c), 1e-9, True),
            _labels("coefficient_labels", ["a", "b", "c"], True),
        ],
    }


def fam_conversion_table(rng: Random, i: int) -> dict:
    kmh = rng.choice([36, 54, 72, 90, 108, 18, 126])
    return {
        "figure_type": "conversion_table",
        "sample_category_4e": "流程/公式类：单位换算表",
        "problem_statement": (
            f"画一张单位换算表：{kmh} km/h 换算为 m/s（除以 3.6），表内给出换算算式"
            "与结果（保留三位有效数字）。"
        ),
        "fact_expectations": [
            _num("converted_ms", round(kmh / 3.6, 3), 0.01, True, unit="m/s"),
            _labels("unit_labels", ["km/h", "m/s"], False),
        ],
    }


def fam_labeled_rebuild(rng: Random, i: int) -> dict:
    pool = [["入射光线", "法线", "反射光线", "反射角"], ["电源", "开关", "灯泡", "电流表"],
            ["点A", "点B", "点C", "边AB"], ["重心O", "支点P", "拉力F"]]
    labels = [x.strip() for x in rng.choice(pool)]
    return {
        "figure_type": "labeled_source_rebuild",
        "sample_category_4e": "源图重建：标注完整保留",
        "problem_statement": (
            f"按源图重建题面示意图，保留全部 {len(labels)} 个印刷标注："
            + "、".join(labels) + "；位置关系与源图一致，不补造源图没有的元素。"
        ),
        "fact_expectations": [
            _labels("preserved_labels", labels, True),
            _count("label_count", len(labels), False),
        ],
    }


def fam_axis_rebuild(rng: Random, i: int) -> dict:
    lo, hi, ticks = rng.choice([(-2, 6, 9), (0, 10, 11), (-5, 5, 11), (0, 100, 6),
                                (-10, 10, 6), (0, 1, 5)])
    return {
        "figure_type": "axis_rebuild",
        "sample_category_4e": "源图重建：坐标系范围与刻度",
        "problem_statement": (
            f"重建源图的坐标轴：保留取值范围 [{lo}, {hi}] 与共 {ticks} 个刻度"
            "（含端点），刻度均匀、标数字可读。"
        ),
        "fact_expectations": [
            _num("axis_min", float(lo), 1e-9, True),
            _num("axis_max", float(hi), 1e-9, True),
            _count("tick_count", ticks, False),
        ],
    }


def fam_grid_redraw(rng: Random, i: int) -> dict:
    r, c = rng.choice([(3, 4), (4, 6), (5, 5), (2, 8), (6, 4), (4, 4), (5, 3)])
    return {
        "figure_type": "grid_redraw",
        "sample_category_4e": "源图重建：格点关系保持",
        "problem_statement": (
            f"按源图重绘方格网：{r} 行 {c} 列共 {r * c} 格，格线均匀、行列为整数计数，"
            "不改变格点关系。"
        ),
        "fact_expectations": [
            _count("cell_count", r * c, True),
            _num("row_count", float(r), 1e-9, True),
            _num("column_count", float(c), 1e-9, True),
        ],
    }


def fam_remove_marks(rng: Random, i: int) -> dict:
    kept = ["题干第(1)问", "题干第(2)问", "图1", "坐标轴"]
    marks = ["红笔勾", "对号", "波浪线"]
    return {
        "figure_type": "handwriting_mark_removal",
        "sample_category_4e": "去手写：仅去手写、保留印刷",
        "problem_statement": (
            "去除图中的手写批注（" + "、".join(marks) + "），完整保留印刷内容（"
            + "、".join(kept) + "）；保留内容不得被误删或模糊。"
        ),
        "fact_expectations": [
            _labels("kept_printed_labels", kept, True),
            _absent("removed_handwriting", marks, True),
        ],
    }


def fam_answer_erase(rng: Random, i: int) -> dict:
    stem = ["解：设未知数为 x", "由题意列方程", "移项得"]
    answer = ["x=5", "答：x=5"]
    return {
        "figure_type": "answer_stroke_erase",
        "sample_category_4e": "去手写：擦除作答笔迹",
        "problem_statement": (
            "擦除学生作答笔迹（" + "、".join(answer) + "），保留题干与解答步骤的印刷"
            "文字（" + "、".join(stem) + "）；被擦除内容不得残留可辨认字迹。"
        ),
        "fact_expectations": [
            _labels("kept_stem_labels", stem, True),
            _absent("erased_answer", answer, True),
        ],
    }


def fam_clean_scan(rng: Random, i: int) -> dict:
    kept = ["选择题第1题", "选择题第2题", "填空题第3题"][: rng.choice([2, 3])]
    return {
        "figure_type": "scan_stain_cleanup",
        "sample_category_4e": "去手写：去污渍保印刷",
        "problem_statement": (
            "去除扫描污渍与噪点，保留印刷题号与文字（" + "、".join(kept) +
            "）；净化结果不冒充原像素修复，仅作语义清图。"
        ),
        "fact_expectations": [
            _labels("kept_print_labels", kept, True),
            _count("kept_label_count", len(kept), False),
        ],
    }


def fam_cell_diagram(rng: Random, i: int) -> dict:
    return {
        "figure_type": "plant_cell_illustration",
        "sample_category_4e": "自然插画：教学示意",
        "problem_statement": (
            "画一张植物细胞教学示意插画（自然风格、非照片），标注细胞壁、细胞核、"
            "液泡三个结构，名称与指向部位正确对应。"
        ),
        "fact_expectations": [
            _labels("organelle_labels", ["细胞壁", "细胞核", "液泡"], True),
        ],
    }


def fam_free_fall(rng: Random, i: int) -> dict:
    return {
        "figure_type": "free_fall_illustration",
        "sample_category_4e": "自然插画：物理情景",
        "problem_statement": (
            "画自由落体示意插画：小球自静止下落，速度 v 与重力加速度 g 的方向均竖直"
            "向下；不得把速度方向画成水平或向上。"
        ),
        "fact_expectations": [
            _dir("velocity_direction", "down", True),
            _dir("gravity_direction", "down", True),
            _labels("vector_labels", ["v", "g"], False),
        ],
    }


def fam_lever_scene(rng: Random, i: int) -> dict:
    a, b = rng.choice([(3, 1), (2, 1), (4, 1), (5, 2), (3, 2)])
    return {
        "figure_type": "lever_illustration",
        "sample_category_4e": "自然插画：生活情景",
        "problem_statement": (
            f"画用撬棒撬石头的情景插画：支点位置正确，动力臂与阻力臂长度比 {a}:{b}，"
            f"标注“支点”“动力臂”“阻力臂”。"
        ),
        "fact_expectations": [
            _num("arm_length_ratio", a / b, 1e-9, True),
            _labels("lever_labels", ["支点", "动力臂", "阻力臂"], True),
        ],
    }


GROUPS: list[tuple[str, str, list, list[int]]] = [
    # (组 key, 组中文名, 题族 builders, 盲测配额——顺序即生成顺序，和为 20)
    ("proportion_coordinates", "比例/坐标",
     [fam_ratio_bar, fam_coordinate_point, fam_number_line, fam_map_scale],
     [6, 5, 4, 5]),
    ("mechanics_phases", "力学/阶段",
     [fam_fx_area, fam_force_diagram, fam_phase_diagram, fam_wave_superposition],
     [6, 5, 5, 4]),
    ("flow_formula", "流程/公式",
     [fam_flow_card, fam_formula_render, fam_conversion_table],
     [7, 7, 6]),
    ("source_rebuild", "源图重建",
     [fam_labeled_rebuild, fam_axis_rebuild, fam_grid_redraw],
     [7, 7, 6]),
    ("handwriting_removal", "去手写",
     [fam_remove_marks, fam_answer_erase, fam_clean_scan],
     [7, 7, 6]),
    ("natural_illustration", "自然插画",
     [fam_cell_diagram, fam_free_fall, fam_lever_scene],
     [7, 7, 6]),
]


def _group_tag(group_key: str) -> str:
    return {
        "proportion_coordinates": "PROPCOORD",
        "mechanics_phases": "MECHPHASE",
        "flow_formula": "FLOWFORM",
        "source_rebuild": "SRCREBLD",
        "handwriting_removal": "DEHANDWR",
        "natural_illustration": "NATILLA",
    }[group_key]


def build_tasks(set_name: str, quotas: list[int] | None) -> tuple[list[dict], dict]:
    """生成一个任务集。quotas=None 时每组取 4 个（每族至少 1 个，循环补足）。"""
    tasks: list[dict] = []
    group_counts: dict[str, int] = {}
    for gi, (gkey, gname, builders, blind_quota) in enumerate(GROUPS):
        rng = Random((BLIND_SEED if set_name == "blind" else DEV_SEED) * 31 + gi)
        made = 0
        if quotas is None:
            plan: list = []
            k = 0
            while len(plan) < 4:
                plan.append(builders[k % len(builders)])
                k += 1
            family_plan = plan
        else:
            family_plan = []
            for f, q in zip(builders, blind_quota):
                family_plan.extend([f] * q)
        for fi, builder in enumerate(family_plan):
            task = builder(rng, fi)
            task_id = f"{'BLD' if set_name == 'blind' else 'DEV'}-{_group_tag(gkey)}-{made + 1:03d}"
            tasks.append({
                "task_id": task_id,
                "set": set_name,
                "group": gkey,
                "group_name": gname,
                **task,
                "requires_figure": True,
                "native_form_separate": False,
                "review_source": dict(REVIEW_SOURCE),
            })
            made += 1
        group_counts[gkey] = made
    return tasks, group_counts


def _write_json(path: Path, payload: dict) -> str:
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    path.write_text(text, encoding="utf-8", newline="\n")
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def main() -> None:
    # 盲测按组配额生成（6 组 × 20）；开发集每族至少 1 个、共 4 个/组
    blind, blind_groups = build_tasks("blind", [q for _, _, _, q in GROUPS])
    assert len(blind) == 120, len(blind)
    assert set(blind_groups.values()) == {20}, blind_groups

    dev, dev_groups = build_tasks("dev", None)
    assert len(dev) == 24, len(dev)
    assert set(dev_groups.values()) == {4}, dev_groups

    blind_ids = {t["task_id"] for t in blind}
    dev_ids = {t["task_id"] for t in dev}
    assert blind_ids.isdisjoint(dev_ids)

    blind_sha = _write_json(HERE / "tasks_blind_120.json", {
        "set": "blind",
        "frozen_at": FROZEN_AT,
        "task_count": len(blind),
        "policy": (
            "PRD-09 盲测集：冻结后不得修改；不得用于调 prompt；启用判定须用未见集"
            "（设计§6 :91 / PRD-09 :132）。修改须重新冻结并留审计记录。"
        ),
        "tasks": blind,
    })
    dev_sha = _write_json(HERE / "tasks_dev_24.json", {
        "set": "dev",
        "frozen_at": FROZEN_AT,
        "task_count": len(dev),
        "policy": "开发集：另设，不计入 120 盲测分母（PRD-09 :132），可用于调 prompt/驱动调试。",
        "tasks": dev,
    })

    generator_sha = hashlib.sha256(
        (HERE / "build_frozen_tasks.py").read_bytes()
    ).hexdigest()
    manifest = {
        "manifest_version": 1,
        "frozen_at": FROZEN_AT,
        "generator_sha256": generator_sha,
        "policy": (
            "评测门每次运行前校验下列 sha256；不一致即拒绝运行（防面向结果改夹具）。"
            "变更任务集必须重新冻结：重跑 build_frozen_tasks.py 并在新 frozen_at 下"
            "留审计记录。"
        ),
        "files": {
            "tasks_blind_120.json": {
                "sha256": blind_sha, "task_count": 120, "group_counts": blind_groups,
            },
            "tasks_dev_24.json": {
                "sha256": dev_sha, "task_count": 24, "group_counts": dev_groups,
            },
        },
    }
    (HERE / "freeze_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8", newline="\n",
    )
    print(f"blind=120 sha={blind_sha[:12]} dev=24 sha={dev_sha[:12]}")


if __name__ == "__main__":
    main()
