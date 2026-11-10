# -*- coding: utf-8 -*-
"""研究探针（批次 51 / R3，2026-11-10）：DrawOps V1 → fact_expectations 的 A 通道提取。

输入：`probe_outputs.json`（`probe_compile.mjs` 用**生产同源**编译器产出的 DrawOps V1）。
输出：`probe_result.json` + 终端摘要。

它只做三件事，全部可复核：
1. **回放**：按 DrawOps 语义维护仿射栈（save/concat/restore），把 path 的局部坐标变换到根空间；
   **label 坐标不套栈**（编译器已把它们一次投影到根空间——见 `visual-compiler.js` label 分支
   与 `VisualDrawOpsPainter` 的 "setMatrix(root)" 舞步）。
2. **提取**（与预期值无关）：文本标签、闭合形状（卡片）、共线线段游程（任意方向）。
3. **判定**：把提取结果喂给仓内既有的 `check_fact_expectations`（`figure_eval_gate.py:642-672`），
   逐题读 accepted/mismatches——证明"提取与判定分离"。

另含一个**提取器反向对照**：故意不套仿射栈，证明"少回放一步就会读错"——这是 A 通道版的
"注入一处错误、判官必须变红"。

运行：
    python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/probe_extract.py
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[4]
sys.path.insert(0, str(REPO / "tools" / "figure_eval"))
import figure_eval_gate as fem  # noqa: E402


# ---------------------------------------------------------------------------
# 1) 回放：仿射栈
# ---------------------------------------------------------------------------

IDENTITY = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def compose(m, n):
    """m ∘ n（先 n 后 m）：x' = a·x + c·y + e；y' = b·x + d·y + f。"""
    a, b, c, d, e, f = m
    na, nb, nc, nd, ne, nf = n
    return (
        a * na + c * nb,
        b * na + d * nb,
        a * nc + c * nd,
        b * nc + d * nd,
        a * ne + c * nf + e,
        b * ne + d * nf + f,
    )


def apply(m, x, y):
    a, b, c, d, e, f = m
    return (a * x + c * y + e, b * x + d * y + f)


def transform_subpaths(path_commands, m):
    """DrawOps path → 根空间子路径列表（M 起新子路径；Z 只表示闭合）。"""
    subpaths, current = [], None
    for command in path_commands:
        code = command[0]
        if code == "M":
            point = apply(m, command[1], command[2])
            current = [point]
            subpaths.append(current)
        elif code == "L":
            if current is None:
                raise ValueError("path does not start with M")
            current.append(apply(m, command[1], command[2]))
        elif code == "C":
            if current is None:
                raise ValueError("path does not start with M")
            # 本探针只判直线段；曲线只记终点（真实提取器需按研究文档 §4.3 采样/展平）。
            current.append(apply(m, command[5], command[6]))
        elif code == "Z":
            continue
        else:
            raise ValueError(f"unknown path command {code!r}")
    return subpaths


def replay(ops, use_matrix_stack=True):
    """返回 (path_subpaths, labels)。label 坐标是根空间，不套栈。"""
    stack = []
    m = IDENTITY
    subpaths = []
    labels = []
    for op in ops:
        kind = op["op"]
        if kind == "save":
            stack.append(m)
        elif kind == "restore":
            m = stack.pop()
        elif kind == "concat":
            m = compose(m, tuple(op["m"])) if use_matrix_stack else m
        elif kind == "clip":
            # 本探针场景无 clip；真实提取器必须把 clip 当可见性掩码（研究文档 §4.3 风险）。
            pass
        elif kind == "path":
            subpaths.extend(transform_subpaths(op["path"], m))
        elif kind == "label":
            labels.append(
                {
                    "contentId": op["contentId"],
                    "x": float(op["x"]),
                    "y": float(op["y"]),
                    "hAlign": op["hAlign"],
                    "vAlign": op["vAlign"],
                }
            )
        else:
            raise ValueError(f"unknown op {kind!r}")
    return subpaths, labels


# ---------------------------------------------------------------------------
# 2) 提取（不看预期值）
# ---------------------------------------------------------------------------

H_ALIGN = {"left": 0.0, "center": 0.5, "right": 1.0}
V_ALIGN = {"top": 0.0, "middle": 0.5, "bottom": 1.0}


def label_anchors(labels, metrics_by_id, contents):
    """label op → (text, anchor_x, anchor_y)。

    文本不在显示列表里（DrawOps label 只有 contentId）：它来自**本事务 prepare 批**的
    contentId→payload 映射（真实事务由宿主侧 measurer 记录，见研究文档 §3.2）。
    锚点按对齐方式与度量反解（与 `visual-compiler.js` 同式）。
    """
    out = []
    for label in labels:
        metric = metrics_by_id.get(label["contentId"])
        text = contents.get(label["contentId"])
        if metric is None:
            out.append({"contentId": label["contentId"], "text": text, "x": label["x"], "y": label["y"]})
            continue
        width, height, baseline = metric["widthDu"], metric["heightDu"], metric["baselineDu"]
        anchor_x = label["x"] + width * H_ALIGN[label["hAlign"]]
        anchor_y = label["y"] + height * V_ALIGN[label["vAlign"]] - baseline
        out.append({"contentId": label["contentId"], "text": text, "x": anchor_x, "y": anchor_y})
    return out


def line_segments(subpaths, eps=1e-6):
    """子路径的每条相邻边 → 线段（任意方向）；单位方向做符号规范化，便于共线分组。

    **分段依据是路径结构（顶点/子路径边界），不是几何间隙**：一条被分成两段的直线若画成
    连续折线（M L L），两段之间没有像素间隙，只有顶点能证明"这里分了段"。
    """
    segments = []
    for points in subpaths:
        if len(points) < 2:
            continue
        for (x1, y1), (x2, y2) in zip(points, points[1:]):
            dx, dy = x2 - x1, y2 - y1
            length = math.hypot(dx, dy)
            if length <= eps:
                continue
            ux, uy = dx / length, dy / length
            if ux < -eps or (abs(ux) <= eps and uy < 0.0):
                ux, uy = -ux, -uy
            # 法向 n = (-uy, ux)；同一根直线上的线段法向投影相同。
            offset = -uy * x1 + ux * y1
            segments.append(
                {
                    "ux": ux,
                    "uy": uy,
                    "offset": offset,
                    "x1": x1,
                    "y1": y1,
                    "x2": x2,
                    "y2": y2,
                    "length": length,
                }
            )
    return segments


def collinear_runs(segments, dir_tol=1e-3, offset_tol=0.5, gap_tol=0.5):
    """按（方向, 法向偏移）聚簇 → 把首尾相接的线段并成游程（任意方向，不假设水平）。"""
    clusters = []
    for segment in segments:
        for cluster in clusters:
            same_dir = (
                abs(cluster["ux"] - segment["ux"]) <= dir_tol
                and abs(cluster["uy"] - segment["uy"]) <= dir_tol
            )
            if same_dir and abs(cluster["offset"] - segment["offset"]) <= offset_tol:
                cluster["segments"].append(segment)
                break
        else:
            clusters.append(
                {"ux": segment["ux"], "uy": segment["uy"], "offset": segment["offset"], "segments": [segment]}
            )
    runs = []
    for cluster in clusters:
        ux, uy = cluster["ux"], cluster["uy"]
        spans = []
        for segment in cluster["segments"]:
            t1 = ux * segment["x1"] + uy * segment["y1"]
            t2 = ux * segment["x2"] + uy * segment["y2"]
            spans.append((min(t1, t2), max(t1, t2)))
        spans.sort()
        ordered = [segment for _, segment in sorted(
            zip([span[0] for span in spans], cluster["segments"]), key=lambda pair: pair[0])]
        runs.append(
            {
                "ux": ux,
                "uy": uy,
                "offset": cluster["offset"],
                "segments": ordered,
                "lengths": [segment["length"] for segment in ordered],
                "span": (spans[0][0], max(span[1] for span in spans)),
            }
        )
    return runs


def closed_shapes(subpaths, min_area=100.0):
    """闭合且轴对齐包围盒面积 > min_area 的子路径 → bbox/面积/中心。"""
    shapes = []
    for points in subpaths:
        if len(points) < 4:
            continue
        xs, ys = [p[0] for p in points], [p[1] for p in points]
        bbox = (min(xs), min(ys), max(xs), max(ys))
        area = (bbox[2] - bbox[0]) * (bbox[3] - bbox[1])
        if area > min_area:
            shapes.append(
                {
                    "bbox": bbox,
                    "area": area,
                    "center": ((bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2),
                }
            )
    return shapes


def labels_in_shapes(shapes, anchors, pad=2.0):
    """给每个闭合形状找它内部的标签锚点；一个形状内**恰好 1 个**标签才算可判。"""
    ordered = []
    for shape in shapes:
        x0, y0, x1, y1 = shape["bbox"]
        inside = [
            a for a in anchors
            if x0 - pad <= a["x"] <= x1 + pad and y0 - pad <= a["y"] <= y1 + pad and a["text"]
        ]
        ordered.append({"shape": shape, "labels": [a["text"] for a in inside]})
    # 主散布轴由形状中心决定（不看标签内容，也不看预期顺序）。
    if len(ordered) >= 2:
        xs = [item["shape"]["center"][0] for item in ordered]
        ys = [item["shape"]["center"][1] for item in ordered]
        if (max(ys) - min(ys)) >= (max(xs) - min(xs)):
            ordered.sort(key=lambda item: (item["shape"]["center"][1], item["shape"]["center"][0]))
        else:
            ordered.sort(key=lambda item: (item["shape"]["center"][0], item["shape"]["center"][1]))
    return ordered


def derive(case, use_matrix_stack=True):
    """提取 → 观测 + 按 key 解码的事实值。解码只读 key 语义，不读 fact_expectations。"""
    metrics_by_id = {m["contentId"]: m for m in case["metrics"]}
    contents = case.get("contents", {})
    subpaths, labels = replay(case["drawOps"]["ops"], use_matrix_stack=use_matrix_stack)
    anchors = label_anchors(labels, metrics_by_id, contents)
    runs = collinear_runs(line_segments(subpaths))
    shapes = closed_shapes(subpaths)
    items = labels_in_shapes(shapes, anchors)

    observations = {
        "labels": [a["text"] for a in anchors if a["text"] is not None],
        "anchor_points": [(round(a["x"], 3), round(a["y"], 3)) for a in anchors],
        "runs": [
            {
                "dir": (round(r["ux"], 3), round(r["uy"], 3)),
                "offset": round(r["offset"], 3),
                "lengths": [round(v, 3) for v in r["lengths"]],
                "span": (round(r["span"][0], 3), round(r["span"][1], 3)),
            }
            for r in runs
        ],
        "closed_shapes": len(shapes),
        "shape_labels": [item["labels"] for item in items],
    }

    facts = {}
    if runs:
        dominant = max(runs, key=lambda r: (len(r["segments"]), sum(r["lengths"])))
        if len(dominant["segments"]) == 2:
            left, right = dominant["lengths"]
            facts["segment_count"] = 2
            facts["segment_length_ratio"] = left / right
    facts["ratio_label"] = observations["labels"]

    if shapes:
        facts["step_count"] = len(shapes)
        texts = [item["labels"] for item in items]
        if texts and all(len(group) == 1 for group in texts):
            facts["step_order"] = [group[0] for group in texts]

    # label_absent 与 label_set 都读"可见文本全集"：前者判 disjoint，后者判 subset。
    facts["visible_texts"] = observations["labels"]
    facts["kept_printed_labels"] = observations["labels"]
    facts["removed_handwriting"] = observations["labels"]
    return observations, facts


# ---------------------------------------------------------------------------
# 3) 判定：把事实喂给仓内既有的 check_fact_expectations
# ---------------------------------------------------------------------------

def verdict_for(facts, task):
    output = [{"key": key, "value": value} for key, value in facts.items()]
    return fem.check_fact_expectations(output, task["fact_expectations"])


CHECKS = [
    ("ratio-1to1-correct", "BLD-PROPCOORD-001", True, "1:1 几何 + 标签都应通过"),
    ("ratio-2to3-correct", "BLD-PROPCOORD-005", True, "2:3 几何 + 标签都应通过（同一提取器）"),
    ("ratio-1to1-rotated", "BLD-PROPCOORD-001", True, "竖直 1:1：按根空间方向判读仍应通过"),
    ("ratio-1to1-correct", "BLD-PROPCOORD-002", False, "1:1 图对 3:1 题必须失败"),
    ("ratio-2to3-correct", "BLD-PROPCOORD-001", False, "2:3 图对 1:1 题必须失败"),
    ("ratio-label-mismatch", "BLD-PROPCOORD-001", False, "几何正确、标签错：只有 label 失败"),
    ("flow-order-correct", "BLD-FLOWFORM-001", True, "三步卡片顺序正确"),
    ("flow-order-reversed", "BLD-FLOWFORM-001", False, "顺序颠倒：ordering 失败、count 仍 3"),
    ("handwriting-residue-present", "BLD-DEHANDWR-001", False,
     "文本级提取器只能靠 kept_printed_labels 失败；removed_handwriting 仍通过（见 per_fact）"),
    ("handwriting-residue-absent", "BLD-DEHANDWR-001", False,
     "只有 1 个印刷标签：label_set 的 subset 语义如实拒绝（本探针场景不是完整去手写图）"),
]


def main() -> int:
    outputs = json.loads((HERE / "probe_outputs.json").read_text(encoding="utf-8"))
    blind = json.loads((REPO / "tools/figure_eval/tasks_blind_120.json").read_text(encoding="utf-8"))["tasks"]
    by_id = {t["task_id"]: t for t in blind}
    cases = {c["name"]: c for c in outputs["cases"]}

    results = []
    for case_name, task_id, want, note in CHECKS:
        observations, facts = derive(cases[case_name])
        verdict = verdict_for(facts, by_id[task_id])
        results.append(
            {
                "case": case_name,
                "task_id": task_id,
                "expected_accept": want,
                "accepted": verdict["accepted"],
                "matches_expectation": verdict["accepted"] == want,
                "verified": verdict["verified"],
                "mismatches": verdict["mismatches"],
                "facts": {k: v for k, v in facts.items() if k != "visible_texts"},
                "observations": observations,
                "note": note,
            }
        )

    def single_fact(case_name, task_id, key, use_matrix_stack=True):
        facts = derive(cases[case_name], use_matrix_stack=use_matrix_stack)[1]
        exp = next(e for e in by_id[task_id]["fact_expectations"] if e["key"] == key)
        verdict = fem.check_fact_expectations([{"key": key, "value": facts.get(key)}], [exp])
        return {"value": facts.get(key), "accepted": verdict["accepted"]}

    per_fact = {
        "geometry_ok_label_wrong__segment_length_ratio": single_fact(
            "ratio-label-mismatch", "BLD-PROPCOORD-001", "segment_length_ratio"),
        "geometry_ok_label_wrong__ratio_label": single_fact(
            "ratio-label-mismatch", "BLD-PROPCOORD-001", "ratio_label"),
        # 关键：残留的 "x=5" 不在夹具的禁写**类名**清单里，文本级提取器把残留判为合格。
        "residue_present__removed_handwriting_text_level": single_fact(
            "handwriting-residue-present", "BLD-DEHANDWR-001", "removed_handwriting"),
        "residue_absent__removed_handwriting_text_level": single_fact(
            "handwriting-residue-absent", "BLD-DEHANDWR-001", "removed_handwriting"),
        "rotated__segment_length_ratio": single_fact(
            "ratio-1to1-rotated", "BLD-PROPCOORD-001", "segment_length_ratio"),
    }

    # 提取器反向对照：故意不回放 concat，看哪些事实变红（= A 通道"注入错误必须报错"）。
    control = {}
    for case_name in ("flow-order-correct", "ratio-1to1-rotated"):
        broken_facts = derive(cases[case_name], use_matrix_stack=False)[1]
        task_id = "BLD-FLOWFORM-001" if case_name.startswith("flow") else "BLD-PROPCOORD-001"
        key = "step_order" if case_name.startswith("flow") else "segment_length_ratio"
        exp = next(e for e in by_id[task_id]["fact_expectations"] if e["key"] == key)
        single = fem.check_fact_expectations([{"key": key, "value": broken_facts.get(key)}], [exp])
        control[case_name] = {
            "broken_value": broken_facts.get(key),
            "accepted_without_matrix_stack": single["accepted"],
            "note": "不回放 concat 时该事实应判错/缺失",
        }

    report = {
        "generated_by": "probe_extract.py",
        "checks": results,
        "per_fact_channel_independence": per_fact,
        "extractor_negative_control": control,
        "all_matches_expectation": all(r["matches_expectation"] for r in results),
    }
    (HERE / "probe_result.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(
        json.dumps(
            {
                "all_matches_expectation": report["all_matches_expectation"],
                "checks": [
                    {
                        "case": r["case"],
                        "task": r["task_id"],
                        "want": r["expected_accept"],
                        "got": r["accepted"],
                        "ok": r["matches_expectation"],
                        "mismatch_keys": [m["key"] for m in r["mismatches"]],
                        "facts": r["facts"],
                    }
                    for r in results
                ],
                "per_fact_channel_independence": per_fact,
                "extractor_negative_control": control,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0 if report["all_matches_expectation"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
