#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""切分准确率度量（4C② 门）：金标 vs 应用导出 —— 三项指标 + 逐页表（stdout，无副作用、不联网）。

它消灭的具体失败：门值此前只存在于计划与记录的文字里（4C 阶段记录自证：grep
`题数一致率|切残率` 于 docs/、tools/ 0 命中），没有可重跑的脚本 ⇒ 复算结果无从对照、
口径一变无从核查；且 v1 口径把**单题页结构上不可能拆分**记成模型不一致，门被定义缺陷
压低（题数一致率分子少了 2 页，见下）。

## 口径（v2，2026-10-14 修正 ①；阈值不动：① 0.9 / ③ 0.05）

① 题数完全一致率 countExactRate = 一致页数 / 可比金标页数
   - 多题金标页（questionCount >= 2）：应用区域数 == 金标题数 ⇒ 一致，否则不一致（原口径）。
   - 单题金标页（questionCount < 2）：期望是「不拆分」。应用侧最小可提交拆分为 2 块
     （`isUsableSplitRegionSet()` 要求区域数 ∈ 2..12，见 core/domain/src/main/kotlin/
     com/tingyun/smartmistakebook/core/domain/CaptureWorkflowRepository.kt:375-382），
     单题页结构上不会产生合法拆分：应用输出 0 区域 ⇒ 一律记一致；> 0 区域 ⇒ 记不一致。
     （v1 把「金标 1 vs 应用 0」按数值不等记不一致——是定义错，不是模型错。）

② 边界 IoU≥0.5 题占比 iouRate = IoU≥0.5 的配对金标题数 / 金标总题数（分母=金标题数）。
   每页做最大权一对一（1:1）配对（以配对 IoU 之和最大为目标）；应用框少于金标时，
   未配对的金标题按未达标计。原口径，未改。

③ 切残率 fragmentRate = 切残金标题数 / 金标总题数（分母=金标题数）。
   切残 = 同一道金标题的题干被应用切成 ≥2 个区域。判定：每个应用框分派给「与该框重叠面积
   / 该框自身面积」最大、且比例 ≥ 0.5 的金标题（即该框主要是这道题的碎片）；同一道金标题
   收到 ≥2 个碎片 ⇒ 该题切残；未被任何框覆盖的题不算切残（那是「漏」，只体现在 ① 与 ②）。
   原口径，未改；输出附 0.3/0.4/0.5/0.6 阈值敏感性。

门式（阈值不动）：① ≥ 0.9 且 ③ ≤ 0.05；② 只度量、不设门。**本脚本只报数**，exit 0 表示
度量完成（不等于门通过）；输入不可用 exit 2；应用导出缺失 exit 3。

数据源：金标 tools/split_golden/tables/split_golden_v1.json；应用导出
build/split-eval/app-split.json（设备 DB 快照，非现场重跑）。

用法（仓库根）：

    python tools/split_golden/measure_split_accuracy.py
        [--golden <file>] [--app <file>]

纯读：不写任何文件、不联网，结果只打印到 stdout；同输入重跑输出逐字节一致。
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO: Path = Path(__file__).resolve().parents[2]
DEFAULT_GOLDEN: Path = REPO / "tools" / "split_golden" / "tables" / "split_golden_v1.json"
DEFAULT_APP: Path = REPO / "build" / "split-eval" / "app-split.json"

IOU_THRESHOLD = 0.5
FRAGMENT_THRESHOLDS = (0.3, 0.4, 0.5, 0.6)
COUNT_GATE = 0.9
FRAGMENT_GATE = 0.05
BOX_KEYS = ("left", "top", "right", "bottom")


class DataError(Exception):
    """输入数据不可用于度量（缺文件 / 形状坏 / 自相矛盾）。"""


# ---------------------------------------------------------------- 载入与校验

def parse_box(raw: object, where: str) -> dict[str, float]:
    if not isinstance(raw, dict):
        raise DataError(f"{where} 不是对象：{type(raw).__name__}")
    box: dict[str, float] = {}
    for key in BOX_KEYS:
        value = raw.get(key)
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise DataError(f"{where}.{key} 不是数字：{value!r}")
        box[key] = float(value)
    if not box["left"] < box["right"] or not box["top"] < box["bottom"]:
        raise DataError(f"{where} 是空/反向框（left<right、top<bottom 不成立）：{box}")
    return box


def _read_json(path: Path, label: str) -> object:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as failure:
        raise DataError(f"{label}读取失败：{path}（{failure}）") from failure
    try:
        return json.loads(text)
    except ValueError as failure:
        raise DataError(f"{label}不是合法 JSON：{path}（{failure}）") from failure


def load_golden(path: Path) -> list[dict]:
    """-> [{file, questionCount, boxes}]，顺序即文件内页序。"""
    doc = _read_json(path, "金标")
    if not isinstance(doc, dict):
        raise DataError(f"金标根不是 JSON 对象：{type(doc).__name__}")
    raw_pages = doc.get("pages")
    if not isinstance(raw_pages, list) or not raw_pages:
        raise DataError("金标缺少非空 pages 数组")
    pages: list[dict] = []
    seen: set[str] = set()
    for index, raw in enumerate(raw_pages):
        where = f"金标 pages[{index}]"
        if not isinstance(raw, dict):
            raise DataError(f"{where} 不是对象（{type(raw).__name__}）")
        name = raw.get("file")
        if not isinstance(name, str) or not name.strip():
            raise DataError(f"{where}.file 不是非空字符串：{name!r}")
        if name in seen:
            raise DataError(f"金标 file 重复：{name!r}（同一页算两次 ⇒ 指标被无声加权）")
        seen.add(name)
        count = raw.get("questionCount")
        boxes_raw = raw.get("boxes")
        if isinstance(count, bool) or not isinstance(count, int):
            raise DataError(f"金标 {name!r}.questionCount 不是整数：{count!r}")
        if not isinstance(boxes_raw, list):
            raise DataError(f"金标 {name!r}.boxes 不是数组（{type(boxes_raw).__name__}）")
        if count != len(boxes_raw):
            raise DataError(
                f"金标 {name!r} questionCount={count} != len(boxes)={len(boxes_raw)}——"
                "本口径的分母依赖二者一致；先跑 validate_split_golden.py 修金标"
            )
        boxes = [parse_box(box, f"金标 {name!r}.boxes[{j}]") for j, box in enumerate(boxes_raw)]
        pages.append({"file": name, "questionCount": count, "boxes": boxes})
    return pages


def load_app(path: Path) -> list[dict]:
    """-> [{file, boxes}]，顺序即文件内页序（应用导出无题数字段，框数即题数）。"""
    doc = _read_json(path, "应用导出")
    if not isinstance(doc, dict):
        raise DataError(f"应用导出根不是 JSON 对象：{type(doc).__name__}")
    raw_pages = doc.get("pages")
    if not isinstance(raw_pages, list):
        raise DataError("应用导出缺少 pages 数组")
    pages: list[dict] = []
    seen: set[str] = set()
    for index, raw in enumerate(raw_pages):
        where = f"应用导出 pages[{index}]"
        if not isinstance(raw, dict):
            raise DataError(f"{where} 不是对象（{type(raw).__name__}）")
        name = raw.get("file")
        if not isinstance(name, str) or not name.strip():
            raise DataError(f"{where}.file 不是非空字符串：{name!r}")
        if name in seen:
            raise DataError(f"应用导出 file 重复：{name!r}")
        seen.add(name)
        questions = raw.get("questions")
        if not isinstance(questions, list):
            raise DataError(f"应用导出 {name!r}.questions 不是数组"
                            f"（{type(questions).__name__}）")
        boxes = [parse_box(box, f"应用导出 {name!r}.questions[{j}]")
                 for j, box in enumerate(questions)]
        pages.append({"file": name, "boxes": boxes})
    return pages


# ---------------------------------------------------------------- 几何

def box_area(box: dict[str, float]) -> float:
    return (box["right"] - box["left"]) * (box["bottom"] - box["top"])


def intersection_area(a: dict[str, float], b: dict[str, float]) -> float:
    width = min(a["right"], b["right"]) - max(a["left"], b["left"])
    height = min(a["bottom"], b["bottom"]) - max(a["top"], b["top"])
    if width <= 0.0 or height <= 0.0:
        return 0.0
    return width * height


def iou(a: dict[str, float], b: dict[str, float]) -> float:
    inter = intersection_area(a, b)
    union = box_area(a) + box_area(b) - inter
    return inter / union if union > 0.0 else 0.0


def max_weight_matching(weights: list[list[float]]) -> list[tuple[int, int]]:
    """最大权一对一（1:1）配对；weights[金标][应用]。

    返回 [(gold_index, app_index)]，权重和最大；页内框数 ≤ 12（应用侧契约上限），
    行数取较小边、列掩码 DP，规模安全。平局取先迭代到的组合（确定序 ⇒ 可重跑）。
    """
    gold_count = len(weights)
    app_count = len(weights[0]) if gold_count else 0
    if gold_count == 0 or app_count == 0:
        return []
    if gold_count <= app_count:
        table, flipped = weights, False
    else:
        table = [[weights[g][a] for g in range(gold_count)] for a in range(app_count)]
        flipped = True
    rows, cols = len(table), len(table[0])
    # layers[k][mask] = (该状态最优权重和, 前驱 mask, 本步行, 本方列)，mask 恰有 k 位
    layers: list[dict[int, tuple[float, int | None, int, int]]] = [
        {0: (0.0, None, -1, -1)}
    ]
    for row in range(rows):
        next_dp: dict[int, tuple[float, int | None, int, int]] = {}
        for mask, (value, _prev, _row, _col) in layers[-1].items():
            for col in range(cols):
                if mask & (1 << col):
                    continue
                new_mask = mask | (1 << col)
                new_value = value + table[row][col]
                current = next_dp.get(new_mask)
                if current is None or new_value > current[0]:
                    next_dp[new_mask] = (new_value, mask, row, col)
        layers.append(next_dp)
    final = layers[-1]
    best_mask = max(final, key=lambda mask: final[mask][0])
    pairs: list[tuple[int, int]] = []
    mask: int | None = best_mask
    depth = rows
    while mask:
        _value, prev, row, col = layers[depth][mask]
        pairs.append((row, col))
        mask = prev
        depth -= 1
    if flipped:                       # table 行=应用、列=金标 ⇒ 换回 (gold, app)
        return [(col, row) for row, col in pairs]
    return pairs


def fragment_pieces(gold_boxes: list[dict], app_boxes: list[dict],
                    threshold: float) -> list[int]:
    """每个金标题收到的「残片」数：应用框分派给重叠占自身面积比最大且 ≥ threshold 的题。"""
    pieces = [0] * len(gold_boxes)
    for app_box in app_boxes:
        area = box_area(app_box)
        if area <= 0.0:
            continue
        best_index: int | None = None
        best_ratio = 0.0
        for gold_index, gold_box in enumerate(gold_boxes):
            ratio = intersection_area(app_box, gold_box) / area
            if ratio > best_ratio:
                best_index, best_ratio = gold_index, ratio
        if best_index is not None and best_ratio >= threshold:
            pieces[best_index] += 1
    return pieces


# ---------------------------------------------------------------- 口径判定

def count_verdict(gold_count: int, app_count: int) -> tuple[bool, str]:
    """① 修正口径：单题金标页（<2）期望「不拆分」。返回 (是否一致, 说明)。"""
    if gold_count < 2:
        if app_count == 0:
            return True, "一致（单题页：不拆分=期望）"
        return False, f"不一致（单题页却被拆 {app_count} 块）"
    if app_count == gold_count:
        return True, "一致"
    if app_count == 0:
        return False, f"不一致（金标 {gold_count} vs 应用 0，漏拆）"
    return False, f"不一致（金标 {gold_count} vs 应用 {app_count}）"


def format_iou_column(gold_boxes: list[dict], matched: dict[int, float]) -> str:
    parts = []
    for index in range(len(gold_boxes)):
        parts.append(f"{matched[index]:.3f}" if index in matched else "—")
    return "/".join(parts) if parts else "（无题）"


# ---------------------------------------------------------------- 主流程

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="切分准确率度量（4C② 门；口径 v2）")
    parser.add_argument("--golden", type=Path, default=DEFAULT_GOLDEN)
    parser.add_argument("--app", type=Path, default=DEFAULT_APP)
    args = parser.parse_args(argv)

    if not args.golden.is_file():
        print(f"切分准确率度量 FAIL：金标文件不存在：{args.golden}")
        return 2
    try:
        golden_pages = load_golden(args.golden)
    except DataError as failure:
        print(f"切分准确率度量 FAIL：{failure}")
        return 2

    print("切分准确率度量（4C② 门，口径 v2：单题金标页期望=不拆分；阈值 0.9 / 0.05 未动）")
    print(f"数据源")
    print(f"  金标：{args.golden}（{args.golden.stat().st_size} B，"
          f"{len(golden_pages)} 页 / {sum(len(p['boxes']) for p in golden_pages)} 题）")

    if not args.app.is_file():
        print(f"  应用：{args.app} —— **缺失**（如实报缺：无法度量，无逐页表）")
        print("指标：n/a（应用导出缺失；请先在设备上导出 build/split-eval/app-split.json）")
        return 3

    try:
        app_pages = load_app(args.app)
    except DataError as failure:
        print(f"切分准确率度量 FAIL：{failure}")
        return 2

    app_by_file = {page["file"]: page["boxes"] for page in app_pages}
    golden_names = {page["file"] for page in golden_pages}
    gold_names_in_order = [page["file"] for page in golden_pages]
    extra_pages = [page["file"] for page in app_pages if page["file"] not in golden_names]
    print(f"  应用：{args.app}（{args.app.stat().st_size} B，"
          f"{len(app_pages)} 页 / {sum(len(p['boxes']) for p in app_pages)} 框；设备 DB 快照）")

    # ------------------------------------------------------------ 逐页
    print()
    print("逐页明细（按金标页序；配对 = 该页最大权 1:1 配对的金标题 IoU，'—'=未配对）")
    header = (f"{'file':<24} | {'金标题数':>6} | {'应用题数':>6} | {'①一致性(v2)':<26}"
              f" | {'配对 IoU（按金标题序）':<34} | {'≥0.5':>4} | {'切残':>4}")
    print(header)
    print("-" * len(header))

    total_gold_questions = 0
    consistent_pages = 0
    legacy_consistent_pages = 0
    changed_pages: list[str] = []
    iou_ok_total = 0
    fragment_5_total = 0
    fragment_by_threshold = {threshold: 0 for threshold in FRAGMENT_THRESHOLDS}
    missing_app_pages: list[str] = []

    for page in golden_pages:
        name = page["file"]
        gold_boxes = page["boxes"]
        gold_count = page["questionCount"]
        if name in app_by_file:
            app_boxes = app_by_file[name]
            suffix = ""
        else:
            app_boxes = []
            suffix = "（应用缺该页，按 0 框计）"
            missing_app_pages.append(name)

        weights = [[iou(gold_box, app_box) for app_box in app_boxes] for gold_box in gold_boxes]
        pairs = max_weight_matching(weights)
        matched = {gold_index: weights[gold_index][app_index] for gold_index, app_index in pairs}
        iou_ok = sum(1 for value in matched.values() if value >= IOU_THRESHOLD)

        pieces = fragment_pieces(gold_boxes, app_boxes, IOU_THRESHOLD)
        fragmented = [index for index, count in enumerate(pieces) if count >= 2]
        threshold_counts = {
            threshold: sum(1 for count in fragment_pieces(gold_boxes, app_boxes, threshold)
                           if count >= 2)
            for threshold in FRAGMENT_THRESHOLDS
        }

        ok, reason = count_verdict(gold_count, len(app_boxes))
        legacy_ok = (gold_count == len(app_boxes))
        if ok != legacy_ok:
            changed_pages.append(name)

        total_gold_questions += gold_count
        consistent_pages += int(ok)
        legacy_consistent_pages += int(legacy_ok)
        iou_ok_total += iou_ok
        fragment_5_total += len(fragmented)
        for threshold, count in threshold_counts.items():
            fragment_by_threshold[threshold] += count

        print(f"{name:<24} | {gold_count:>6} | {len(app_boxes):>6} | {reason:<26}"
              f" | {format_iou_column(gold_boxes, matched):<34} | {iou_ok:>4} |"
              f" {len(fragmented):>4}{suffix}")

    page_count = len(golden_pages)
    count_rate = consistent_pages / page_count if page_count else 0.0
    iou_rate = iou_ok_total / total_gold_questions if total_gold_questions else 0.0
    fragment_rate = fragment_5_total / total_gold_questions if total_gold_questions else 0.0
    legacy_count_rate = legacy_consistent_pages / page_count if page_count else 0.0
    gate_pass = count_rate >= COUNT_GATE and fragment_rate <= FRAGMENT_GATE

    print()
    print(f"指标（① 分母=可比金标页数 {page_count}；②③ 分母=金标总题数 {total_gold_questions}）")
    print(f"  ① 题数完全一致率 countExactRate = {consistent_pages}/{page_count}"
          f" = {count_rate:.4f}")
    print(f"     门：{count_rate:.4f} >= {COUNT_GATE} ->"
          f" {'过' if count_rate >= COUNT_GATE else '未过'}")
    if legacy_count_rate != count_rate:
        print(f"     原口径（v1，数值相等即一致）对照 = {legacy_consistent_pages}/{page_count}"
              f" = {legacy_count_rate:.4f}；改判页（{len(changed_pages)} 页）："
              + "、".join(changed_pages))
        print("     差异原因：单题金标页（questionCount<2）结构上不会产生合法拆分"
              "（应用最小拆分为 2 块），v1 按数值不等记不一致是定义错。")
    print(f"  ② 边界 IoU≥0.5 题占比 iouRate = {iou_ok_total}/{total_gold_questions}"
          f" = {iou_rate:.4f}（只度量，不设门）")
    print(f"  ③ 切残率 fragmentRate = {fragment_5_total}/{total_gold_questions}"
          f" = {fragment_rate:.4f}")
    print(f"     门：{fragment_rate:.4f} <= {FRAGMENT_GATE} ->"
          f" {'过' if fragment_rate <= FRAGMENT_GATE else '未过'}")
    sensitivity = "、".join(
        f"{threshold}: {fragment_by_threshold[threshold]}/{total_gold_questions}"
        for threshold in FRAGMENT_THRESHOLDS)
    print(f"     阈值敏感性 0.3/0.4/0.5/0.6 -> {sensitivity}")
    print()
    print(f"门判定（① ≥ {COUNT_GATE} 且 ③ ≤ {FRAGMENT_GATE}，阈值未动）："
          f"{'true' if gate_pass else 'false'}"
          + ("" if gate_pass else
             f"（{'① ' + format(count_rate, '.4f') + ' < ' + format(COUNT_GATE, '.1f') if count_rate < COUNT_GATE else ''}"
             f"{'；' if count_rate < COUNT_GATE and fragment_rate > FRAGMENT_GATE else ''}"
             f"{'③ ' + format(fragment_rate, '.4f') + ' > ' + format(FRAGMENT_GATE, '.2f') if fragment_rate > FRAGMENT_GATE else ''}）"))
    if missing_app_pages:
        print(f"注意：应用导出缺以下金标页（按 0 框计）：{'、'.join(missing_app_pages)}")
    if extra_pages:
        print(f"注意：应用导出多出金标没有的页（不进入任何分母）：{'、'.join(extra_pages)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
