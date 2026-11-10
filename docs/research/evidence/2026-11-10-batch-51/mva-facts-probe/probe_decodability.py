# -*- coding: utf-8 -*-
"""研究探针（批次 51 / R3，2026-11-10）：fact_expectations 的"从显示列表可判性"分类。

对 120 盲测集的 51 个不同 fact key 逐个给出通道内类别：

- ``A1``：**只看显示列表**（几何 + prepare 批文本）就能确定性解码，无需作者声明角色；
- ``A2``：需要作者在场景里**声明角色**（如 ``id`` 命名空间标记"哪条是摩擦力箭头"），
  但**数值仍由代码从 ops 算出**——角色只回答"这是哪个元素"，不回答"它等于多少"；
- ``D3``：显示列表里**没有判定所需的信息**（是"类名"而非字符串、是物理单位、需要栅格语义），
  A 通道不可判，必须由 C（视觉判官，经校准）判，或记 UNVERIFIED。

未知 key 一律报错（不默认落到"可判"）。输出 ``fact_decodability.json`` + 终端计数。

运行：
    python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/probe_decodability.py
"""

from __future__ import annotations

import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[4]
sys.path.insert(0, str(REPO / "tools" / "figure_eval"))
import mva_gate as mg  # noqa: E402  （读同一份冻结夹具的通道规则）

# key → (class, reason)
CLASSES: dict[str, tuple[str, str]] = {
    # numeric
    "segment_length_ratio": ("A1", "折线/子路径的共线边长度比；探针已实跑"),
    "arm_length_ratio": ("A2", "需要标出'支点/动力臂/阻力臂'三个角色，长度由 ops 算"),
    "point_x": ("A2", "需要'点标记'角色 + 由刻度标签拟合的轴标定"),
    "point_y": ("A2", "同上"),
    "axis_min": ("A1", "轴端刻度是印刷文本：解析文本数值"),
    "axis_max": ("A1", "轴端刻度是印刷文本"),
    "converted_ms": ("A1", "换算表单元格是印刷文本"),
    "discriminant": ("A1", "公式结果印刷为文本（Δ=…）"),
    "real_distance_cm": ("A1", "题面要求在图上**标注**实际距离（如 150000 厘米）→ 解析印刷文本；"
                                "注意：比例尺×图上长度的几何一致性无 du↔cm 换算，不在此列"),
    "row_count": ("A1", "网格横线数 − 1（几何计数）"),
    "column_count": ("A1", "网格竖线数 − 1（几何计数）"),
    "value_of_1_2": ("A2", "数轴标定（0/1 刻度）+ 标记点角色"),
    "value_of_1_3": ("A2", "同上"),
    "u_at_half_height": ("A2", "曲线角色 + 高度基准（容器/标尺）"),
    "curve_value_at_x_equals_h": ("A2", "曲线角色 + x=h 参考线的位置"),
    "area_W_over_F0h_at_half": ("A2", "填充区域积分 + 轴标定"),
    "resultant_amplitude": ("A2", "合成曲线角色 + 轴标定"),
    # label_set
    "axis_labels": ("A1", "可见文本集合"),
    "baseline_label": ("A1", "可见文本集合"),
    "coefficient_labels": ("A1", "可见文本集合"),
    "force_labels": ("A1", "可见文本集合"),
    "kept_print_labels": ("A1", "可见文本集合"),
    "kept_printed_labels": ("A1", "可见文本集合"),
    "kept_stem_labels": ("A1", "可见文本集合"),
    "lever_labels": ("A1", "可见文本集合"),
    "organelle_labels": ("A1", "可见文本集合"),
    "point_label": ("A1", "可见文本集合"),
    "preserved_labels": ("A1", "可见文本集合"),
    "ratio_label": ("A1", "可见文本集合；探针已实跑"),
    "scale_label": ("A1", "可见文本集合"),
    "unit_labels": ("A1", "可见文本集合"),
    "vector_labels": ("A1", "可见文本集合"),
    "wave_labels": ("A1", "可见文本集合"),
    # count
    "cell_count": ("A1", "闭合格子数（几何计数）"),
    "kept_label_count": ("A1", "可见文本条数"),
    "label_count": ("A1", "可见文本条数"),
    "marked_points": ("A1", "标记形状数（小圆/实心点）"),
    "segment_count": ("A1", "共线分段数（路径结构）；探针已实跑"),
    "step_count": ("A1", "闭合卡片数；探针已实跑"),
    "tick_count": ("A1", "垂直于轴的短线段数"),
    "wave_count": ("A1", "曲线条数（子路径聚类）"),
    # ordering
    "ascending_order": ("A1", "数轴标签按 x 排序 + 分数文本解析"),
    "phase_order": ("A2", "阶段标签需要'阶段序列'角色，顺序由锚点位置算"),
    "step_order": ("A1", "卡片内标签按卡片中心主散布轴排序；探针已实跑"),
    # direction
    "friction_direction": ("A2", "箭头角色 + 箭头尖端/底边的几何方向"),
    "gravity_direction": ("A2", "同上"),
    "pressure_friction_resultant_direction": ("A2", "同上"),
    "pull_direction": ("A2", "同上"),
    "velocity_direction": ("A2", "同上"),
    # label_absent
    "erased_answer": ("D3", "夹具值是'红笔勾/对号/波浪线'这类**类名**，不是字面串；文本级判定会把残留判过（探针已实证）"),
    "removed_handwriting": ("D3", "同上"),
}


def main() -> int:
    tasks = json.loads((REPO / "tools/figure_eval/tasks_blind_120.json").read_text(encoding="utf-8"))["tasks"]
    unknown = sorted({f["key"] for t in tasks for f in t["fact_expectations"] if f["key"] not in CLASSES})
    if unknown:
        raise SystemExit(f"未分类的 fact key（不默认给'可判'）：{unknown}")

    by_class = Counter()
    critical_by_class = Counter()
    per_group = defaultdict(Counter)
    facts = []
    for task in tasks:
        for fact in task["fact_expectations"]:
            cls, reason = CLASSES[fact["key"]]
            by_class[cls] += 1
            if fact.get("critical"):
                critical_by_class[cls] += 1
            per_group[task["group"]][cls] += 1
            facts.append(
                {
                    "task_id": task["task_id"],
                    "group": task["group"],
                    "key": fact["key"],
                    "kind": fact["kind"],
                    "class": cls,
                    "critical": bool(fact.get("critical")),
                    "reason": reason,
                }
            )

    tasks_with_d3 = sorted({f["task_id"] for f in facts if f["class"] == "D3"})
    tasks_all_a = sorted(
        {
            t["task_id"]
            for t in tasks
            if all(CLASSES[f["key"]][0] in ("A1", "A2") for f in t["fact_expectations"])
        }
    )
    tasks_with_a2 = sorted({f["task_id"] for f in facts if f["class"] == "A2"})
    tasks_all_a1 = sorted(
        {
            t["task_id"]
            for t in tasks
            if all(CLASSES[f["key"]][0] == "A1" for f in t["fact_expectations"])
        }
    )
    # 规则通道（mva_gate.fact_channel）× 可判类别：找出"规则说能判、显示列表其实判不了"的格子。
    cross = Counter()
    for task in tasks:
        for fact in task["fact_expectations"]:
            cross[(mg.fact_channel(fact), CLASSES[fact["key"]][0])] += 1

    report = {
        "generated_by": "probe_decodability.py",
        "source": "tools/figure_eval/tasks_blind_120.json",
        "classes": {
            "A1": "显示列表单独可判（几何/文本）",
            "A2": "显示列表 + 作者角色声明可判（数值仍由代码算）",
            "D3": "显示列表不可判（类名/物理单位/栅格语义）",
        },
        "totals": {
            "facts": len(facts),
            "by_class": dict(sorted(by_class.items())),
            "critical_by_class": dict(sorted(critical_by_class.items())),
            "tasks": len(tasks),
            "tasks_all_A": len(tasks_all_a),
            "tasks_all_A1": len(tasks_all_a1),
            "tasks_all_A1_ids": tasks_all_a1,
            "tasks_with_A2": len(tasks_with_a2),
            "tasks_with_D3": len(tasks_with_d3),
            "tasks_with_D3_ids": tasks_with_d3,
            "tasks_all_A_ids": tasks_all_a,
            "channel_x_class": {f"{channel} x {cls}": n for (channel, cls), n in sorted(cross.items())},
        },
        "per_group": {group: dict(sorted(counts.items())) for group, counts in sorted(per_group.items())},
        "facts": sorted(facts, key=lambda f: (f["task_id"], f["key"])),
    }
    (HERE / "fact_decodability.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(report["totals"], ensure_ascii=False, indent=2))
    print(json.dumps(report["per_group"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
