# -*- coding: utf-8 -*-
"""DrawOps V1 → fact_expectations 的事实提取器（批次 51 / G4 T27）。

**它消灭的具体失败**：MVA 的 JS 臂此前没有任何"图上的事实"来源——`figure_eval_gate`
的 `JsPort` 只回传场景引用（`scene_sha256/scene_bytes/content_ids`），事实核验永远是
`UNVERIFIED`，门读到 0 个可评样本。本模块把**产品回放的同一条显示列表**读成
`fact_expectations` 的值，并让"读不出来"显式 fail-closed，而不是猜或默认为通过。

判定对象（依据 `docs/research/2026-11-10-mva-facts-judge.md` §2-§4）：

- 输入是 DrawOps V1（`VisualDrawOpsParser.serialize` 同形）——painter 真正回放的东西；
- 文本不在显示列表里：`label` 只带 `contentId`，payload 来自本事务 prepare 批的度量
  记录（设备适配器必须同批记录，见 `MvaJsArmAdapterInstrumentedTest`）；
- `path` 是局部坐标 → 按 save/concat/restore 栈回放到根空间；`clip` 是烘焙根坐标 →
  当可见性掩码；`label` 已由编译器投影到根空间 → **不再套栈**（再乘一次是双重变换）；
- `alpha == 0` 的元素画不出来 → 不计入文本/计数。

三条纪律（每条对应一个具体失败）：

1. **不读预期值。** `extract()` 的输入只有显示列表、度量记录、要读的 key 名；值全部
   由代码从结构算出。预期值只出现在调用方的 `check_fact_expectations` 里。
2. **像素不可判 = `UNDECODABLE`，不是 0、不是通过。** `label_absent` 的夹具值是类名
   （"红笔勾/对号/波浪线"），显示列表里没有这些字；文本级 disjoint 会假接受（研究
   §3.6 实证）。此类 key 一律不输出值。
3. **未知 key 不兜底。** 未登记解码规则的 key 直接 `ValueError`，与新 kind 落进
   "视觉能判"的静默退化同型。

已知宽松点（报告必须带）：
- `label_set` 的提取是"全部可见文本"，判定仍是既有 subset 语义（多报不失败）；
- `axis_min/max`、`converted_ms`、`discriminant`、`real_distance_cm` 只证明"图上印了
  这个数"，不证明"图与所印数值几何一致"（研究 §3.1 的边界）；
- 角色绑定（A2）只提供 `bind_scene_nodes` 的 SceneV1 id → op 索引能力；依赖角色的
  decoder 本批**未实现**，一律 `UNDECODABLE`。

运行：
    python -m pytest tools/figure_eval/test_fact_extract.py -q
"""

from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass, field
from typing import Any, Iterable

DECODED = "DECODED"
UNDECODABLE = "UNDECODABLE"

# 几何容差与事实容差分离（研究 §3.0 R2）：下列容差只用于聚类/对齐，绝不参与事实比较。
GEOMETRY_EPS = 1e-6
COLLINEAR_OFFSET_TOL = 0.5
COLLINEAR_DIR_TOL = 1e-3
ROW_TOLERANCE_DU = 6.0
MARKER_MAX_EDGE_DU = 12.0
CARD_MIN_AREA_DU2 = 100.0

H_ALIGN = {"left": 0.0, "center": 0.5, "right": 1.0}
V_ALIGN = {"top": 0.0, "middle": 0.5, "bottom": 1.0}

IDENTITY = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


class Undecodable(Exception):
    """该事实在当前产物形态下没有判定信息（不输出值，任务标 NOT_EVALUABLE）。"""


def _undecodable(reason: str) -> Undecodable:
    return Undecodable(reason)


# ---------------------------------------------------------------------------
# 回放：仿射栈 + 裁剪掩码
# ---------------------------------------------------------------------------

def compose(m: tuple, n: tuple) -> tuple:
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


def apply(m: tuple, x: float, y: float) -> tuple[float, float]:
    a, b, c, d, e, f = m
    return (a * x + c * y + e, b * x + d * y + f)


def _flatten_cubic(p0, p1, p2, p3, steps: int = 16) -> list[tuple[float, float]]:
    points = []
    for index in range(1, steps + 1):
        t = index / steps
        mt = 1.0 - t
        x = (mt ** 3) * p0[0] + 3 * (mt ** 2) * t * p1[0] + 3 * mt * (t ** 2) * p2[0] + (t ** 3) * p3[0]
        y = (mt ** 3) * p0[1] + 3 * (mt ** 2) * t * p1[1] + 3 * mt * (t ** 2) * p2[1] + (t ** 3) * p3[1]
        points.append((x, y))
    return points


def transform_path(commands: list, m: tuple) -> dict:
    """DrawOps path → 根空间子路径。

    返回 `{"subpaths": [{"points": [...], "structural": [...], "closed": bool,
    "commands": [...]}], "all_points": [...]}`。
    `structural` 是顶点序列（M/L/C 终点）——分段计数的依据是路径结构，不是像素间隙。
    """
    subpaths: list[dict] = []
    current: dict | None = None
    last_point: tuple[float, float] | None = None
    closed = False
    for command in commands:
        code = command[0]
        if code == "M":
            if current is not None:
                subpaths.append(current)
            point = apply(m, float(command[1]), float(command[2]))
            current = {"points": [point], "structural": [point], "closed": False, "commands": []}
            last_point = point
            closed = False
        elif code == "L":
            if current is None:
                raise ValueError("path does not start with M")
            point = apply(m, float(command[1]), float(command[2]))
            current["points"].append(point)
            current["structural"].append(point)
            last_point = point
        elif code == "C":
            if current is None or last_point is None:
                raise ValueError("path does not start with M")
            c1 = apply(m, float(command[1]), float(command[2]))
            c2 = apply(m, float(command[3]), float(command[4]))
            end = apply(m, float(command[5]), float(command[6]))
            current["points"].extend(_flatten_cubic(last_point, c1, c2, end))
            current["structural"].append(end)
            last_point = end
        elif code == "Z":
            if current is None or not current["points"]:
                raise ValueError("close without an open subpath")
            current["closed"] = True
            current["points"].append(current["points"][0])
            current["structural"].append(current["structural"][0])
            last_point = current["structural"][0]
        else:
            raise ValueError(f"unknown path command {code!r}")
    if current is not None:
        subpaths.append(current)
    for subpath in subpaths:
        if not subpath["closed"] and len(subpath["points"]) >= 2:
            first, last = subpath["points"][0], subpath["points"][-1]
            if abs(first[0] - last[0]) <= GEOMETRY_EPS and abs(first[1] - last[1]) <= GEOMETRY_EPS:
                subpath["closed"] = True
    return {"subpaths": subpaths,
            "all_points": [point for subpath in subpaths for point in subpath["points"]]}


def replay(ops: list[dict], use_matrix_stack: bool = True) -> dict:
    """按 DrawOps 语义回放。

    - `path`：局部坐标 → 套当前矩阵；
    - `clip`：烘焙根坐标 → 不套矩阵，收进裁剪栈；
    - `label`：根坐标 → 不套矩阵（编译器已投影）；
    - `alpha == 0` 的 path/label 不参与（画不出来）。
    """
    stack: list[tuple] = []
    matrix = IDENTITY
    clips: list[dict] = []
    subpaths: list[dict] = []
    labels: list[dict] = []
    ops_count = len(ops)

    for index, op in enumerate(ops):
        kind = op.get("op")
        if kind == "save":
            stack.append(matrix)
        elif kind == "restore":
            if not stack:
                raise ValueError(f"ops[{index}] restore without save")
            matrix = stack.pop()
        elif kind == "concat":
            values = tuple(float(value) for value in op["m"])
            if len(values) != 6:
                raise ValueError(f"ops[{index}] concat needs 6 values")
            if use_matrix_stack:
                matrix = compose(matrix, values)
        elif kind == "clip":
            path = transform_path(op["path"], IDENTITY)
            clips.append({"subpaths": path["subpaths"], "rule": op.get("rule", "nonzero")})
        elif kind == "path":
            if float(op.get("alpha", 1.0)) <= 0.0:
                continue
            path = transform_path(op["path"], matrix)
            for subpath in path["subpaths"]:
                subpath["op_index"] = index
                subpaths.append(subpath)
        elif kind == "label":
            labels.append({
                "contentId": op["contentId"],
                "x": float(op["x"]),
                "y": float(op["y"]),
                "hAlign": op.get("hAlign", "left"),
                "vAlign": op.get("vAlign", "top"),
                "alpha": float(op.get("alpha", 1.0)),
                "op_index": index,
            })
        else:
            raise ValueError(f"unknown op {kind!r}")

    return {"subpaths": subpaths, "labels": labels, "clips": clips,
            "ops_count": ops_count}


# ---------------------------------------------------------------------------
# 裁剪掩码（点内判定）
# ---------------------------------------------------------------------------

def _crossings(point: tuple[float, float], ring: list[tuple[float, float]]) -> int:
    """射线法交叉数（ring 闭合；含端点时用半开区间避免重复计数）。"""
    x, y = point
    count = 0
    for (x1, y1), (x2, y2) in zip(ring, ring[1:] + ring[:1]):
        if y1 == y2:
            continue
        if (y1 > y) != (y2 > y):
            t = (y - y1) / (y2 - y1)
            if x < x1 + t * (x2 - x1):
                count += 1
    return count


def _inside_polygons(point: tuple[float, float], polygons: list[list[tuple[float, float]]],
                     rule: str) -> bool:
    if not polygons:
        return True
    if rule == "evenodd":
        total = sum(_crossings(point, polygon) for polygon in polygons)
        return total % 2 == 1
    # nonzero: 带符号环绕数
    x, y = point
    winding = 0
    for polygon in polygons:
        for (x1, y1), (x2, y2) in zip(polygon, polygon[1:] + polygon[:1]):
            if y1 <= y:
                if y2 > y and (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1) > 0:
                    winding += 1
            elif y2 <= y and (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1) < 0:
                winding -= 1
    return winding != 0


def _inside_clips(point: tuple[float, float], clips: list[dict]) -> bool:
    for clip in clips:
        rings = [subpath["points"] for subpath in clip["subpaths"] if len(subpath["points"]) >= 2]
        if not _inside_polygons(point, rings, clip["rule"]):
            return False
    return True


# ---------------------------------------------------------------------------
# 观测（与 key 无关的一次回放）
# ---------------------------------------------------------------------------

def _segments(subpaths: list[dict]) -> list[dict]:
    segments = []
    for subpath in subpaths:
        points = subpath["structural"]
        for (x1, y1), (x2, y2) in zip(points, points[1:]):
            dx, dy = x2 - x1, y2 - y1
            length = math.hypot(dx, dy)
            if length <= GEOMETRY_EPS:
                continue
            ux, uy = dx / length, dy / length
            if ux < -GEOMETRY_EPS or (abs(ux) <= GEOMETRY_EPS and uy < 0.0):
                ux, uy = -ux, -uy
            segments.append({
                "ux": ux, "uy": uy, "offset": -uy * x1 + ux * y1,
                "x1": x1, "y1": y1, "x2": x2, "y2": y2, "length": length,
                "op_index": subpath.get("op_index"),
            })
    return segments


def _runs(segments: list[dict]) -> list[dict]:
    clusters: list[dict] = []
    for segment in segments:
        for cluster in clusters:
            if (abs(cluster["ux"] - segment["ux"]) <= COLLINEAR_DIR_TOL
                    and abs(cluster["uy"] - segment["uy"]) <= COLLINEAR_DIR_TOL
                    and abs(cluster["offset"] - segment["offset"]) <= COLLINEAR_OFFSET_TOL):
                cluster["segments"].append(segment)
                break
        else:
            clusters.append({"ux": segment["ux"], "uy": segment["uy"],
                             "offset": segment["offset"], "segments": [segment]})
    runs = []
    for cluster in clusters:
        ux, uy = cluster["ux"], cluster["uy"]
        spans = []
        for segment in cluster["segments"]:
            t1 = ux * segment["x1"] + uy * segment["y1"]
            t2 = ux * segment["x2"] + uy * segment["y2"]
            spans.append((min(t1, t2), max(t1, t2), segment))
        spans.sort(key=lambda item: item[0])
        runs.append({
            "ux": ux, "uy": uy, "offset": cluster["offset"],
            "segments": [item[2] for item in spans],
            "lengths": [item[2]["length"] for item in spans],
            "span": (spans[0][0], max(item[1] for item in spans)),
        })
    return runs


def _shapes(subpaths: list[dict], clips: list[dict]) -> tuple[list[dict], bool]:
    shapes = []
    partially_clipped = False
    for subpath in subpaths:
        points = subpath["points"]
        if len(points) < 4:
            continue
        xs = [point[0] for point in points]
        ys = [point[1] for point in points]
        bbox = (min(xs), min(ys), max(xs), max(ys))
        area = 0.0
        ring = points + (points[:1] if points[0] != points[-1] else [])
        for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
            area += x1 * y2 - x2 * y1
        area = abs(area) / 2.0
        inside_all = all(_inside_clips(point, clips) for point in points)
        if not inside_all:
            inside_any = any(_inside_clips(point, clips) for point in points)
            if inside_any:
                partially_clipped = True
            continue
        if not subpath["closed"]:
            continue
        shapes.append({
            "bbox": bbox,
            "area": area,
            "center": ((bbox[0] + bbox[2]) / 2.0, (bbox[1] + bbox[3]) / 2.0),
            "op_index": subpath.get("op_index"),
            "points": points,
        })
    return shapes, partially_clipped


def _normalize_text(text: str) -> str:
    """R6：只做去首尾空白 / 行分隔→空格 / 负号归一；不做 NFKC 或大小写折叠。"""
    cleaned = text.strip().replace("\r\n", " ").replace("\r", " ").replace("\n", " ")
    return cleaned.replace("\u2212", "-").replace("\uff0d", "-")


def _label_anchor(label: dict, metric: dict | None) -> tuple[float, float]:
    if metric is None:
        return label["x"], label["y"]
    width = float(metric.get("widthDu", 0.0))
    height = float(metric.get("heightDu", 0.0))
    baseline = float(metric.get("baselineDu", 0.0))
    h_align = H_ALIGN.get(label["hAlign"], 0.0)
    v_align = V_ALIGN.get(label["vAlign"], 0.0)
    return (
        label["x"] + width * h_align,
        label["y"] + height * v_align - baseline,
    )


def observe(draw_ops: dict, metrics: Iterable[dict], use_matrix_stack: bool = True) -> "Obs":
    metrics_by_id = {}
    for metric in metrics or []:
        content_id = metric.get("contentId")
        if content_id:
            metrics_by_id[content_id] = metric

    replayed = replay(draw_ops["ops"], use_matrix_stack=use_matrix_stack)
    subpaths = replayed["subpaths"]
    clips = replayed["clips"]

    labels = []
    labels_without_text = 0
    for label in replayed["labels"]:
        metric = metrics_by_id.get(label["contentId"])
        text = metric.get("payload") if metric else None
        text = _normalize_text(text) if isinstance(text, str) and text.strip() else None
        anchor = _label_anchor(label, metric)
        entry = dict(label)
        entry["text"] = text
        entry["anchor_x"], entry["anchor_y"] = anchor
        if text is None:
            labels_without_text += 1
        labels.append(entry)

    visible_labels = []
    for label in labels:
        if label["alpha"] <= 0.0:
            continue
        if not _inside_clips((label["anchor_x"], label["anchor_y"]), clips):
            continue
        visible_labels.append(label)

    shapes, partially_clipped = _shapes(subpaths, clips)
    segments = _segments(subpaths)
    return Obs(
        draw_ops=draw_ops,
        subpaths=subpaths,
        segments=segments,
        runs=_runs(segments),
        shapes=shapes,
        labels=labels,
        visible_labels=visible_labels,
        labels_without_text=labels_without_text,
        clips=clips,
        partially_clipped=partially_clipped,
    )


@dataclass
class Obs:
    draw_ops: dict
    subpaths: list[dict]
    segments: list[dict]
    runs: list[dict]
    shapes: list[dict]
    labels: list[dict]
    visible_labels: list[dict]
    labels_without_text: int
    clips: list[dict]
    partially_clipped: bool
    scene_binding: dict[str, dict] = field(default_factory=dict)
    roles: dict[str, dict] = field(default_factory=dict)

    # -- 便捷视图 --------------------------------------------------------
    @property
    def visible_texts(self) -> list[str]:
        if self.labels_without_text:
            raise _undecodable(
                f"{self.labels_without_text} 条可见 label 没有 prepare 批文本（contentId→payload）")
        texts = []
        seen = set()
        for label in self.visible_labels:
            if label["text"] not in seen:
                seen.add(label["text"])
                texts.append(label["text"])
        return texts

    @property
    def horizontal_runs(self) -> list[dict]:
        return [run for run in self.runs if abs(run["uy"]) <= COLLINEAR_DIR_TOL]

    @property
    def vertical_runs(self) -> list[dict]:
        return [run for run in self.runs if abs(run["ux"]) <= COLLINEAR_DIR_TOL]

    def visible_shapes(self) -> list[dict]:
        return self.shapes

    def op_for_role(self, role: str) -> int:
        entry = self.roles.get(role)
        if entry is None:
            raise _undecodable(f"角色 {role!r} 未在任何 SceneV1 节点 id 上声明（A2 角色绑定缺失）")
        return entry["op_index"]


# ---------------------------------------------------------------------------
# SceneV1 角色绑定（A2 载体：node.id → op 索引）
# ---------------------------------------------------------------------------

def bind_scene_nodes(scene: dict, draw_ops: dict) -> dict[str, dict]:
    """按编译器的遍历序把 SceneV1 节点 id 对齐到 path/label op 索引。

    **它消灭的具体失败**：DrawOps V1 的 path/label op 没有任何 id（`VisualDrawOps.kt:96-140`），
    作者用节点 id 声明的角色在编译后不可见，A2 事实只能靠猜元素。编译器按
    "组 save→clip→concat→children→restore"、叶子按树序发射，因此 path/label 节点序与
    path/label op 序一一对应；本函数用该对应把 id 绑回 op 索引，并用 stats 复算交叉校验。

    不对齐即 `ValueError`（fail-closed）：角色错绑会让数值看起来"通过"，比不可判更坏。
    """
    path_nodes: list[dict] = []
    label_nodes: list[dict] = []

    def walk(node: dict) -> None:
        node_type = node.get("type")
        if node_type == "group":
            if node.get("visible") is False:
                return  # 编译器整棵子树不发射
            for child in node.get("children") or []:
                walk(child)
        elif node_type == "path":
            path_nodes.append(node)
        elif node_type == "label":
            label_nodes.append(node)
        else:
            raise ValueError(f"unknown scene node type {node_type!r}")

    root = scene.get("root")
    if isinstance(root, dict):
        walk(root)

    path_ops = [index for index, op in enumerate(draw_ops["ops"]) if op.get("op") == "path"]
    label_ops = [index for index, op in enumerate(draw_ops["ops"]) if op.get("op") == "label"]

    stats = draw_ops.get("stats") or {}
    if isinstance(stats.get("labels"), int) and stats["labels"] != len(label_ops):
        raise ValueError(
            f"scene/ops 标签数不一致：scene {len(label_nodes)}，ops {len(label_ops)}，"
            f"stats {stats['labels']}")
    if len(path_nodes) != len(path_ops):
        raise ValueError(f"scene 路径节点 {len(path_nodes)} 与 path op {len(path_ops)} 不对齐")
    if len(label_nodes) != len(label_ops):
        raise ValueError(f"scene 标签节点 {len(label_nodes)} 与 label op {len(label_ops)} 不对齐")

    binding: dict[str, dict] = {}
    for node, op_index in zip(path_nodes, path_ops):
        node_id = node.get("id")
        if node_id:
            if node_id in binding:
                raise ValueError(f"SceneV1 节点 id 重复：{node_id!r}")
            binding[node_id] = {"node_type": "path", "op_index": op_index,
                                "node": node, "op": draw_ops["ops"][op_index]}
    for node, op_index in zip(label_nodes, label_ops):
        node_id = node.get("id")
        if node_id:
            if node_id in binding:
                raise ValueError(f"SceneV1 节点 id 重复：{node_id!r}")
            binding[node_id] = {"node_type": "label", "op_index": op_index,
                                "node": node, "op": draw_ops["ops"][op_index]}
    return binding


def roles_from_map(binding: dict[str, dict], role_map: dict[str, str]) -> dict[str, dict]:
    roles = {}
    for role, node_id in (role_map or {}).items():
        if node_id not in binding:
            raise ValueError(f"角色 {role!r} 指向不存在的 SceneV1 节点 id {node_id!r}")
        roles[role] = binding[node_id]
    return roles


# ---------------------------------------------------------------------------
# 数值/文本小工具
# ---------------------------------------------------------------------------

_NUMBER_RE = re.compile(r"(?<![\w.])[-+]?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?")
_FRACTION_RE = re.compile(r"^\s*([-+]?\d+)\s*/\s*(\d+)\s*$")


def _as_number(text: str) -> float | None:
    if not text:
        return None
    candidate = text.strip()
    fraction = _FRACTION_RE.match(candidate)
    if fraction:
        denominator = int(fraction.group(2))
        if denominator == 0:
            return None
        return int(fraction.group(1)) / denominator
    if re.fullmatch(r"[-+]?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?", candidate):
        return float(candidate)
    return None


def _numbers_in(text: str) -> list[float]:
    return [float(match.group(0)) for match in _NUMBER_RE.finditer(text or "")]


def _is_horizontal(run: dict) -> bool:
    return abs(run["uy"]) <= COLLINEAR_DIR_TOL


def _is_vertical(run: dict) -> bool:
    return abs(run["ux"]) <= COLLINEAR_DIR_TOL


def _unique_positions(values: Iterable[float], tolerance: float = COLLINEAR_OFFSET_TOL) -> list[float]:
    ordered = sorted(values)
    unique: list[float] = []
    for value in ordered:
        if not unique or abs(value - unique[-1]) > tolerance:
            unique.append(value)
    return unique


def _label_rows(obs: Obs) -> list[list[dict]]:
    """可见标签按 anchor_y 聚行（容差 ROW_TOLERANCE_DU），行内按 x 排序。"""
    rows: list[list[dict]] = []
    for label in sorted(obs.visible_labels, key=lambda item: item["anchor_y"]):
        for row in rows:
            if abs(row[0]["anchor_y"] - label["anchor_y"]) <= ROW_TOLERANCE_DU:
                row.append(label)
                break
        else:
            rows.append([label])
    for row in rows:
        row.sort(key=lambda item: item["anchor_x"])
    return rows


def _label_columns(obs: Obs) -> list[list[dict]]:
    columns: list[list[dict]] = []
    for label in sorted(obs.visible_labels, key=lambda item: item["anchor_x"]):
        for column in columns:
            if abs(column[0]["anchor_x"] - label["anchor_x"]) <= ROW_TOLERANCE_DU:
                column.append(label)
                break
        else:
            columns.append([label])
    for column in columns:
        column.sort(key=lambda item: item["anchor_y"])
    return columns


def _numeric_row(rows: list[list[dict]], *, min_items: int = 2,
                 require_spread: bool = True) -> list[tuple[float, float, dict]]:
    """在若干行里选"数值标签最多、位置有跨度"的一行，返回 [(position,value,label)]。"""
    best: list[tuple[float, float, dict]] = []
    for row in rows:
        entries = []
        for label in row:
            value = _as_number(label["text"] or "")
            if value is None:
                continue
            entries.append((label["anchor_x"], value, label))
        if len(entries) < min_items:
            continue
        if require_spread and len({round(item[0], 3) for item in entries}) < 2:
            continue
        if len(entries) > len(best):
            best = entries
    if len(best) < min_items:
        raise _undecodable("图上没有可用的数值刻度行（至少 2 个数值标签且位置有跨度）")
    return best


def _numeric_column(columns: list[list[dict]], *, min_items: int = 2) -> list[tuple[float, float, dict]]:
    best: list[tuple[float, float, dict]] = []
    for column in columns:
        entries = []
        for label in column:
            value = _as_number(label["text"] or "")
            if value is None:
                continue
            entries.append((label["anchor_y"], value, label))
        if len(entries) < min_items:
            continue
        if len({round(item[0], 3) for item in entries}) < 2:
            continue
        if len(entries) > len(best):
            best = entries
    if len(best) < min_items:
        raise _undecodable("图上没有可用的数值刻度列（至少 2 个数值标签且位置有跨度）")
    return best


def _fit_linear(points: list[tuple[float, float]]) -> tuple[float, float]:
    """最小二乘 value = a·position + b；退化输入抛 Undecodable。"""
    n = len(points)
    if n < 2:
        raise _undecodable("标定点不足，无法拟合轴")
    mean_x = sum(point[0] for point in points) / n
    mean_y = sum(point[1] for point in points) / n
    denominator = sum((point[0] - mean_x) ** 2 for point in points)
    if denominator <= GEOMETRY_EPS:
        raise _undecodable("标定点位置无跨度，无法拟合轴")
    slope = sum((point[0] - mean_x) * (point[1] - mean_y) for point in points) / denominator
    return slope, mean_y - slope * mean_x


def _markers(obs: Obs) -> list[dict]:
    """标记点候选：小的闭合图形（bbox 最大边 ≤ MARKER_MAX_EDGE_DU）。"""
    candidates = []
    for shape in obs.shapes:
        x0, y0, x1, y1 = shape["bbox"]
        if max(x1 - x0, y1 - y0) <= MARKER_MAX_EDGE_DU and shape["area"] > 1.0:
            candidates.append(shape)
    return candidates


def _cards(obs: Obs) -> list[dict]:
    return [shape for shape in obs.shapes if shape["area"] > CARD_MIN_AREA_DU2]


def _card_labels(obs: Obs, cards: list[dict]) -> list[dict]:
    items = []
    for card in cards:
        x0, y0, x1, y1 = card["bbox"]
        pad = 2.0
        inside = [
            label for label in obs.visible_labels
            if label["text"]
            and x0 - pad <= label["anchor_x"] <= x1 + pad
            and y0 - pad <= label["anchor_y"] <= y1 + pad
        ]
        items.append({"shape": card, "labels": [label["text"] for label in inside]})
    return items


def _sort_by_dominant_axis(items: list[dict]) -> list[dict]:
    if len(items) < 2:
        return items
    xs = [item["shape"]["center"][0] for item in items]
    ys = [item["shape"]["center"][1] for item in items]
    if (max(ys) - min(ys)) >= (max(xs) - min(xs)):
        return sorted(items, key=lambda item: (item["shape"]["center"][1], item["shape"]["center"][0]))
    return sorted(items, key=lambda item: (item["shape"]["center"][0], item["shape"]["center"][1]))


# ---------------------------------------------------------------------------
# 解码器注册表（key → kind + 类 + 解码函数）
# ---------------------------------------------------------------------------

LABEL_SET_KEYS = (
    "axis_labels", "baseline_label", "coefficient_labels", "force_labels",
    "kept_print_labels", "kept_printed_labels", "kept_stem_labels", "lever_labels",
    "organelle_labels", "point_label", "preserved_labels", "ratio_label",
    "scale_label", "unit_labels", "vector_labels", "wave_labels",
)

COUNT_KEYS = (
    "cell_count", "kept_label_count", "label_count", "marked_points",
    "segment_count", "step_count", "tick_count", "wave_count",
)

ORDERING_KEYS = ("ascending_order", "phase_order", "step_order")

DIRECTION_KEYS = (
    "friction_direction", "gravity_direction",
    "pressure_friction_resultant_direction", "pull_direction", "velocity_direction",
)

NUMERIC_KEYS = (
    "area_W_over_F0h_at_half", "arm_length_ratio", "axis_max", "axis_min",
    "column_count", "converted_ms", "curve_value_at_x_equals_h", "discriminant",
    "point_x", "point_y", "real_distance_cm", "resultant_amplitude",
    "row_count", "segment_length_ratio", "u_at_half_height",
    "value_of_1_2", "value_of_1_3",
)

LABEL_ABSENT_KEYS = ("erased_answer", "removed_handwriting")


def _dec_visible_texts(obs: Obs) -> list[str]:
    return obs.visible_texts


def _dec_label_count(obs: Obs) -> int:
    return len(obs.visible_labels)


def _dominant_run(obs: Obs) -> dict:
    if not obs.runs:
        raise _undecodable("图上没有线段（path 为空或全部被裁掉）")
    return max(obs.runs, key=lambda run: (len(run["segments"]), sum(run["lengths"])))


def _dec_segment_count(obs: Obs) -> int:
    if not obs.runs:
        return 0
    return len(_dominant_run(obs)["segments"])


def _dec_segment_ratio(obs: Obs) -> float:
    run = _dominant_run(obs)
    if len(run["segments"]) != 2:
        raise _undecodable(f"主游程有 {len(run['segments'])} 段，不是可判的两段比例")
    first, second = run["lengths"]
    if second <= 0:
        raise _undecodable("主游程第二段长度为 0")
    return first / second


def _dec_step_count(obs: Obs) -> int:
    return len(_cards(obs))


def _dec_step_order(obs: Obs) -> list[str]:
    cards = _cards(obs)
    if len(cards) < 2:
        raise _undecodable(f"卡片形状 {len(cards)} 个，不足以判顺序")
    items = _card_labels(obs, cards)
    if any(len(item["labels"]) != 1 for item in items):
        counts = [len(item["labels"]) for item in items]
        raise _undecodable(f"卡片内标签数不唯一：{counts}（不猜）")
    ordered = _sort_by_dominant_axis(items)
    return [item["labels"][0] for item in ordered]


def _dec_marked_points(obs: Obs) -> int:
    return len(_markers(obs))


def _dec_row_count(obs: Obs) -> float:
    positions = _unique_positions(run["offset"] for run in obs.horizontal_runs)
    if len(positions) < 2:
        raise _undecodable("水平线少于 2 条，无法判行数")
    return float(len(positions) - 1)


def _dec_column_count(obs: Obs) -> float:
    positions = _unique_positions(run["offset"] for run in obs.vertical_runs)
    if len(positions) < 2:
        raise _undecodable("竖直线少于 2 条，无法判列数")
    return float(len(positions) - 1)


def _dec_cell_count(obs: Obs) -> int:
    rows = _dec_row_count(obs)
    columns = _dec_column_count(obs)
    return int(round(rows * columns))


def _axis_tick_entries(obs: Obs) -> list[tuple[float, float, dict]]:
    return _numeric_row(_label_rows(obs))


def _dec_tick_count(obs: Obs) -> int:
    entries = _axis_tick_entries(obs)
    return len(_unique_positions(entry[0] for entry in entries))


def _dec_axis_min(obs: Obs) -> float:
    return min(value for _, value, _ in _axis_tick_entries(obs))


def _dec_axis_max(obs: Obs) -> float:
    return max(value for _, value, _ in _axis_tick_entries(obs))


def _dec_converted_ms(obs: Obs) -> float:
    labels = [label for label in obs.visible_labels
              if label["text"] and re.search(r"m\s*/\s*s", label["text"])]
    if not labels:
        raise _undecodable("图上没有带 m/s 的换算结果标签")
    values = _numbers_in(labels[-1]["text"])
    if not values:
        raise _undecodable("m/s 标签里没有数值")
    return values[-1]


def _dec_discriminant(obs: Obs) -> float:
    labels = [label for label in obs.visible_labels
              if label["text"] and ("Δ" in label["text"] or "\\Delta" in label["text"])]
    if not labels:
        raise _undecodable("图上没有判别式（Δ=…）标签")
    text = labels[-1]["text"]
    tail = text.split("=")[-1] if "=" in text else text
    values = _numbers_in(tail)
    if not values:
        raise _undecodable("判别式标签里没有数值")
    return values[-1]


def _dec_real_distance_cm(obs: Obs) -> float:
    labels = [label for label in obs.visible_labels
              if label["text"] and re.search(r"(cm|厘米)", label["text"])]
    if not labels:
        raise _undecodable("图上没有厘米标注")
    candidates: list[float] = []
    for label in labels:
        candidates.extend(_numbers_in(label["text"]))
    if not candidates:
        raise _undecodable("厘米标注里没有数值")
    # 实际距离 = 图上标注的最大厘米值（图上距离 < 实际距离；报告的宽松点见模块头）。
    return max(candidates)


def _axis_origin(obs: Obs) -> tuple[float | None, float | None]:
    """最长水平/竖直游程的位置（x 轴 y、y 轴 x）；缺失记 None。"""
    horizontal = obs.horizontal_runs
    vertical = obs.vertical_runs
    axis_y = None
    axis_x = None
    if horizontal:
        longest = max(horizontal, key=lambda run: sum(run["lengths"]))
        axis_y = longest["offset"]
    if vertical:
        longest = max(vertical, key=lambda run: sum(run["lengths"]))
        # 竖直方向的单位向量被规范化为 (0,1)，法向 n=(-1,0) ⇒ offset = -x；还原成 x。
        axis_x = -longest["offset"]
    return axis_x, axis_y


def _dec_point_coordinate(obs: Obs, axis: str) -> float:
    markers = _markers(obs)
    if len(markers) != 1:
        raise _undecodable(f"标记点候选 {len(markers)} 个，不唯一（角色未声明）")
    marker = markers[0]
    axis_x, axis_y = _axis_origin(obs)
    if axis == "x":
        entries = _numeric_row(_label_rows(obs))
        if axis_x is not None:
            # 排除坐标轴原点标签：它在 y 轴上，其 x 位置不是 x 值的刻度位置。
            entries = [entry for entry in entries
                       if entry[2]["anchor_x"] > axis_x + ROW_TOLERANCE_DU]
        slope, intercept = _fit_linear([(position, value) for position, value, _ in entries])
        return slope * marker["center"][0] + intercept
    entries = _numeric_column(_label_columns(obs))
    if axis_y is not None:
        entries = [entry for entry in entries
                   if entry[2]["anchor_y"] < axis_y - ROW_TOLERANCE_DU]
    slope, intercept = _fit_linear([(position, value) for position, value, _ in entries])
    return slope * marker["center"][1] + intercept


def _dec_point_x(obs: Obs) -> float:
    return _dec_point_coordinate(obs, "x")


def _dec_point_y(obs: Obs) -> float:
    return _dec_point_coordinate(obs, "y")


def _fraction_label(obs: Obs, text: str) -> dict:
    matches = [label for label in obs.visible_labels if label["text"] == text]
    if len(matches) != 1:
        raise _undecodable(f"分数标签 {text!r} 出现 {len(matches)} 次，不唯一")
    return matches[0]


def _nearest_marker_position(obs: Obs, label: dict) -> tuple[float, float]:
    """分数标签对应的标记点位置：取离标签锚点最近且唯一的标记点；没有就用标签锚点。"""
    markers = _markers(obs)
    anchors = [(marker["center"][0], marker["center"][1]) for marker in markers]
    distances = [
        (math.hypot(anchor[0] - label["anchor_x"], anchor[1] - label["anchor_y"]), anchor)
        for anchor in anchors
    ]
    near = [anchor for distance, anchor in distances if distance <= 30.0]
    if len(near) > 1:
        raise _undecodable(f"分数标签 {label['text']!r} 附近有 {len(near)} 个标记点，不唯一")
    if len(near) == 1:
        return near[0]
    return label["anchor_x"], label["anchor_y"]


def _dec_fraction_value(obs: Obs, fraction_text: str) -> float:
    label = _fraction_label(obs, fraction_text)
    position_x, _ = _nearest_marker_position(obs, label)
    entries = _numeric_row(_label_rows(obs))
    slope, intercept = _fit_linear([(position, value) for position, value, _ in entries])
    return slope * position_x + intercept


def _dec_value_of_1_2(obs: Obs) -> float:
    return _dec_fraction_value(obs, "1/2")


def _dec_value_of_1_3(obs: Obs) -> float:
    return _dec_fraction_value(obs, "1/3")


def _axis_band(obs: Obs) -> tuple[float, float] | None:
    try:
        entries = _axis_tick_entries(obs)
    except Undecodable:
        return None
    ys = [entry[2]["anchor_y"] for entry in entries]
    return (min(ys) - ROW_TOLERANCE_DU, max(ys) + ROW_TOLERANCE_DU)


def _dec_ascending_order(obs: Obs) -> list[str]:
    band = _axis_band(obs)
    members = []
    for label in obs.visible_labels:
        if not label["text"] or _as_number(label["text"]) is None:
            continue
        if band is not None and band[0] <= label["anchor_y"] <= band[1]:
            continue
        members.append(label)
    if len(members) < 2:
        raise _undecodable("可排序的成员标签少于 2 个（角色未声明）")
    members.sort(key=lambda label: label["anchor_x"])
    return [label["text"] for label in members]


def _dec_wave_count(obs: Obs) -> int:
    raise _undecodable("曲线子路径聚类未实现（wave_count 需按 C 段极值分组）")


def _dec_requires_role(obs: Obs, key: str) -> Any:
    raise _undecodable(f"{key} 需要作者角色声明（A2）；本批未实现该角色的解码器")


def _dec_label_absent(obs: Obs) -> Any:
    raise _undecodable(
        "label_absent 的夹具值是类名（如“红笔勾/对号/波浪线”），显示列表里没有这些字；"
        "文本级 disjoint 会假接受（研究 §3.6 实证），必须走已校准的 C 通道")


DECODERS: dict[str, tuple[str, str, Any]] = {}


def _register(keys: Iterable[str], kind: str, fact_class: str, decoder: Any) -> None:
    for key in keys:
        if key in DECODERS:
            raise AssertionError(f"duplicate decoder for {key}")
        DECODERS[key] = (kind, fact_class, decoder)


_register(LABEL_SET_KEYS, "label_set", "A1", _dec_visible_texts)
_register(("segment_count",), "count", "A1", _dec_segment_count)
_register(("step_count",), "count", "A1", _dec_step_count)
_register(("label_count",), "count", "A1", _dec_label_count)
_register(("kept_label_count",), "count", "A1", _dec_label_count)
_register(("marked_points",), "count", "A1", _dec_marked_points)
_register(("cell_count",), "count", "A1", _dec_cell_count)
_register(("tick_count",), "count", "A1", _dec_tick_count)
_register(("wave_count",), "count", "A2", _dec_wave_count)
_register(("step_order",), "ordering", "A1", _dec_step_order)
_register(("ascending_order",), "ordering", "A1", _dec_ascending_order)
_register(("phase_order",), "ordering", "A2",
          lambda obs: _dec_requires_role(obs, "phase_order"))
_register(("segment_length_ratio",), "numeric", "A1", _dec_segment_ratio)
_register(("row_count",), "numeric", "A1", _dec_row_count)
_register(("column_count",), "numeric", "A1", _dec_column_count)
_register(("axis_min",), "numeric", "A1", _dec_axis_min)
_register(("axis_max",), "numeric", "A1", _dec_axis_max)
_register(("converted_ms",), "numeric", "A1", _dec_converted_ms)
_register(("discriminant",), "numeric", "A1", _dec_discriminant)
_register(("real_distance_cm",), "numeric", "A1", _dec_real_distance_cm)
_register(("point_x",), "numeric", "A1", _dec_point_x)
_register(("point_y",), "numeric", "A1", _dec_point_y)
_register(("value_of_1_2",), "numeric", "A1", _dec_value_of_1_2)
_register(("value_of_1_3",), "numeric", "A1", _dec_value_of_1_3)
_register(("arm_length_ratio",), "numeric", "A2",
          lambda obs: _dec_requires_role(obs, "arm_length_ratio"))
_register(("u_at_half_height",), "numeric", "A2",
          lambda obs: _dec_requires_role(obs, "u_at_half_height"))
_register(("curve_value_at_x_equals_h",), "numeric", "A2",
          lambda obs: _dec_requires_role(obs, "curve_value_at_x_equals_h"))
_register(("area_W_over_F0h_at_half",), "numeric", "A2",
          lambda obs: _dec_requires_role(obs, "area_W_over_F0h_at_half"))
_register(("resultant_amplitude",), "numeric", "A2",
          lambda obs: _dec_requires_role(obs, "resultant_amplitude"))
_register(DIRECTION_KEYS, "direction", "A2",
          lambda obs: _dec_requires_role(obs, "direction"))
_register(LABEL_ABSENT_KEYS, "label_absent", "C", _dec_label_absent)


@dataclass
class Extraction:
    facts: dict[str, dict]
    obs: Obs

    @property
    def decoded(self) -> dict[str, Any]:
        return {key: entry["value"] for key, entry in self.facts.items()
                if entry["status"] == DECODED}

    @property
    def undecodable(self) -> dict[str, str]:
        return {key: entry["reason"] for key, entry in self.facts.items()
                if entry["status"] == UNDECODABLE}

    def as_output_facts(self) -> list[dict]:
        """喂给 `figure_eval_gate.check_fact_expectations` 的形状（只含已判定的值）。"""
        return [{"key": key, "value": value} for key, value in self.decoded.items()]


def _json_object(document: Any) -> dict:
    if isinstance(document, str):
        document = json.loads(document)
    if not isinstance(document, dict):
        raise ValueError("draw ops document must be a JSON object")
    if "ops" not in document or not isinstance(document["ops"], list):
        raise ValueError("draw ops document must carry an ops array")
    return document


def extract(
    draw_ops: dict | str,
    metrics: Iterable[dict] | None = None,
    *,
    keys: Iterable[str],
    scene: dict | str | None = None,
    role_map: dict[str, str] | None = None,
    replay_matrix_stack: bool = True,
) -> Extraction:
    """从一份 DrawOps V1 文档读出给定 key 的事实值。

    @param keys 要读的事实 key；未登记 → `ValueError`（不默认"视觉能判"）。
    @param scene 可选的 SceneV1 场景；给出时做 node.id → op 索引绑定（A2 载体）。
    @param role_map 可选的 role → SceneV1 节点 id 映射；本批依赖角色的 decoder 仍未实现。
    @param replay_matrix_stack 测试用的反向对照开关（关掉后 concat 不回放，必须读错）。
    """
    document = _json_object(draw_ops)
    key_list = list(keys)
    unknown = [key for key in key_list if key not in DECODERS]
    if unknown:
        raise ValueError(f"未知事实 key：{unknown!r}（不默认可判，需先登记解码规则）")

    obs = observe(document, metrics or [], use_matrix_stack=replay_matrix_stack)
    if scene is not None:
        scene_object = json.loads(scene) if isinstance(scene, str) else scene
        obs.scene_binding = bind_scene_nodes(scene_object, document)
        obs.roles = roles_from_map(obs.scene_binding, role_map or {})

    facts: dict[str, dict] = {}
    for key in key_list:
        kind, fact_class, decoder = DECODERS[key]
        entry = {"key": key, "kind": kind, "class": fact_class}
        try:
            value = decoder(obs)
        except Undecodable as failure:
            entry.update(status=UNDECODABLE, reason=str(failure))
        else:
            entry.update(status=DECODED, value=value)
            if isinstance(value, float) and not math.isfinite(value):
                entry.update(status=UNDECODABLE, reason="解码结果非有限数")
                entry.pop("value", None)
        facts[key] = entry
    return Extraction(facts=facts, obs=obs)


# ---------------------------------------------------------------------------
# 任务级记录（供 mva_gate / 报告消费；不在这里判门）
# ---------------------------------------------------------------------------

def task_record(task: dict, extraction: Extraction) -> dict:
    """把一次提取整理成"任务可评性"记录。

    - 任一 critical 事实 `UNDECODABLE` ⇒ `evaluable=false`（该题 NOT_EVALUABLE，
      不进任何比率的分母，也不记 0 命中）；
    - 非 critical 事实不可判只登记，不阻断任务。
    """
    expectations = task.get("fact_expectations") or []
    critical = {item["key"] for item in expectations if item.get("critical")}
    undecodable = extraction.undecodable
    blocking = sorted(key for key in undecodable if key in critical)
    return {
        "task_id": task["task_id"],
        "facts": extraction.decoded,
        "fact_status": {key: entry["status"] for key, entry in extraction.facts.items()},
        "undecodable": undecodable,
        "blocking_undecodable": blocking,
        "evaluable": not blocking,
    }


def summarize(records: Iterable[dict]) -> dict:
    records = list(records)
    total = len(records)
    evaluable = sum(1 for record in records if record["evaluable"])
    blocking: dict[str, int] = {}
    for record in records:
        for key in record["blocking_undecodable"]:
            blocking[key] = blocking.get(key, 0) + 1
    return {
        "tasks": total,
        "evaluable": evaluable,
        "coverage": (evaluable / total) if total else None,
        "partial": evaluable < total,
        "blocking_by_key": dict(sorted(blocking.items())),
    }
