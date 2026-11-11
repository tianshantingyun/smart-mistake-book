#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""切分准确率度量（4C② 门）：金标 vs 应用导出 —— 三项指标 + 逐页表（stdout，无副作用、不联网）。

它消灭的具体失败：门值此前只存在于计划与记录的文字里（4C 阶段记录自证：grep
`题数一致率|切残率` 于 docs/、tools/ 0 命中），没有可重跑的脚本 ⇒ 复算结果无从对照、
口径一变无从核查；且 v1 口径把**单题页结构上不可能拆分**记成模型不一致，门被定义缺陷
压低（题数一致率分子少了 2 页，见下）。

## 口径（v3，2026-11-10 批次 51；阈值与分母不动：① 0.9 / ③ 0.05）

v3 只改「0 框」的解释，不改任何阈值或分母：逐页先按导出判定分类，再判一致。
- `ACCEPTED_SPLIT`：`outcomeStatus=OK` + `decision=SPLIT` + 2..12 个可用区域 ⇒ 题数相等才一致。
- `ACCEPTED_NO_SPLIT`：`OK` + `PASS` + 0 框 ⇒ 单题金标页（`questionCount<2`）一致；多题页不一致
  （模型把 N 题读成整页 1 题）。PASS 是「整页一题、无需切分」的**被接受判定**，必须记满分。
- `SPLIT_UNUSABLE`：`OK` + `SPLIT` 但 0 框（消费侧 `isUsableSplitRegionSet` 判不可用）⇒ 不一致
  （产品没切成任何东西，不是弃答）。
- `ABSTAIN`：`OK` + `RECAPTURE`/`NEED_MORE_IMAGE` ⇒ 题数记 `null`（不是 0），单列 `abstain`；
  进 ① 按 ITT 记不一致（样本是「每页至少一道完整可见题」的条件样本，弃答按定义就是错判）。
- `NOT_EVALUABLE_CHANNEL`：`outcomeStatus != OK`、无 `decision`、契约整轮拒收 ⇒ 单列
  `notEvaluable`；进 ① 按 ITT 记不一致（产品这一页什么都没给用户）。
- `MISSING_APP`：导出缺该页 ⇒ 不一致（`GATE.md`「缺页就是漏」）。
- `V2_FALLBACK`：**整份导出**没有 `decision`/`outcomeStatus`（历史设备导出
  `export_split_eval.py` 的 SELECT 不含这两列）⇒ 回落 v2 规则；单题页标
  `singleQuestionUnverified`，报告计 `singleQuestionUnverifiedCount` 且 `caliber="v2-fallback"`
  （不得声称 v3 严格口径）。按页回落会让判定型导出里缺页/失败页重新变成假阳性，故只按整份导出回落。
分母恒为可比金标页数；弃答与通道失败都不剔除（剔除分母等于奖励多弃答）。

## 口径（v2，历史；保留为 countExactV2 对照，2026-10-14 修正 ①）

① 题数完全一致率 countExactRate = 一致页数 / 可比金标页数
   - 多题金标页（questionCount >= 2）：应用区域数 == 金标题数 ⇒ 一致，否则不一致（原口径）。
   - 单题金标页（questionCount < 2）：期望是「不拆分」。应用侧最小可提交拆分为 2 块
     （`isUsableSplitRegionSet()` 要求区域数 ∈ 2..12，见 core/domain/src/main/kotlin/
     com/tingyun/smartmistakebook/core/domain/CaptureWorkflowRepository.kt:375-382），
     单题页结构上不会产生合法拆分：应用输出 0 区域 ⇒ 一律记一致；> 0 区域 ⇒ 记不一致。
     （v1 把「金标 1 vs 应用 0」按数值不等记不一致——是定义错，不是模型错。
      v3 起这条只在 `V2_FALLBACK` 的历史导出上生效；有判定字段时只有被接受的 PASS 才记一致。）

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
        [--golden <file>] [--app <file>] [--json]

`--json`：stdout 只输出一份机器可读 JSON 报告（与人读输出同一份计算，单一事实源），
供 `check_split_gate.py` 判门消费；同输入重跑逐字节一致。人读路径（默认）不变：
同输入重跑输出逐字节一致（2026-10-27 起仅在门判定后追加一行"自动化："裁定说明，
指标、逐页表与门式一字未动）。

## 逐页来源（透传，不猜，不参与口径）

导出器（`export_split_eval.py`）在每页写 `nameSource`/`jobId`/`pageIndex`（v2 起还有
`pageId`/`pageIdKind`/`integrity`），文件级写 `source` 块（v2 起含 `producer`/
`db.userVersion`/`snapshot`/`payload`）。`--json` 的逐页行透传为 `appNameSource`/
`appJobId`/`appPageIndex`/`appPageId`/`appPageIdKind`/`appJobQuestionCountMatches`/
`appProvenance`，文件级摘要进 `app.source`（含 `dbUserVersion`/`snapshotIdentitySha256`/
`payloadSha256`/`producerSha256`）。**导出没有溯源块时这些字段为 null/false**
（4C 历史导出即此形态）——不得由文件名反推来源。来源只做记录，不进入任何分子/分母/阈值。

逐页行还给出裁定所需的**几何与子判据**（`goldBoxesSha256`/`appBoxesSha256`/
`fragmentPiecesByGoldIndex`/`fragmentedGoldIndices`/`unmatchedAppBoxCount`）：
`unmatchedAppBoxCount = 应用框数 − 1:1 配对数`（配对数 = min(金标,应用)；超出者此前
完全不进任何数）。口径本身一字未动。

纯读：不写任何文件、不联网，结果只打印到 stdout。
"""

from __future__ import annotations

import argparse
import hashlib
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
# 应用导出可携带的页级溯源键（export_split_eval.py 写出）；缺省一律记 null，不猜。
PROVENANCE_KEYS = ("nameSource", "jobId", "pageIndex", "sourceUri", "jobStatus", "pageCount",
                   # v2 起：稳定页 id 与 job 计数一致性（只透传，不进口径）
                   "pageId", "pageIdKind", "integrity",
                   # v3 起：harness 导出逐页写判定；设备导出没有这两列 ⇒ 整份回落 v2。
                   "outcomeStatus", "decision")

# 口径 v3 的逐页页类（只分类，不参与任何阈值）。
PAGE_CLASS_ACCEPTED_SPLIT = "ACCEPTED_SPLIT"
PAGE_CLASS_SPLIT_UNUSABLE = "SPLIT_UNUSABLE"
PAGE_CLASS_ACCEPTED_NO_SPLIT = "ACCEPTED_NO_SPLIT"
PAGE_CLASS_ABSTAIN = "ABSTAIN"
PAGE_CLASS_NOT_EVALUABLE_CHANNEL = "NOT_EVALUABLE_CHANNEL"
PAGE_CLASS_MISSING_APP = "MISSING_APP"
PAGE_CLASS_V2_FALLBACK = "V2_FALLBACK"
ABSTAIN_DECISIONS = ("RECAPTURE", "NEED_MORE_IMAGE")
SPLIT_REGION_COUNT_RANGE = (2, 12)

# 自动化裁定（既有裁定 37 / 4C③，本脚本只声明、不执行——没有任何产品开关可切）。
AUTOMATION_RULING = ("门后自动化不启用（裁定 37 / 4C③：门不过不允许自动入库，切残不得自动入库）。"
                     "本脚本不切任何产品开关。")
AUTOMATION_RULING_PASS = ("门本次通过；门后自动化是否启用属产品裁定（4C③），"
                          "本脚本不切任何产品开关。")


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


def canonical_sha256(value: object) -> str:
    """几何摘要（裁定记录用）：键序/分隔符固定 ⇒ 同值同字节。

    它消灭的具体失败：逐页裁定只绑定文件名时，同名页换了框仍会复用旧裁定；
    摘要让"这次判的到底是哪一组框"可核。
    """
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True,
                         separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


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


def _load_app_document(path: Path) -> tuple[list[dict], dict[str, dict], dict | None, object]:
    """-> (pages, 每页溯源, 文件级 source 块, 导出的 schemaVersion)。缺失时为空，绝不猜。"""
    doc = _read_json(path, "应用导出")
    if not isinstance(doc, dict):
        raise DataError(f"应用导出根不是 JSON 对象：{type(doc).__name__}")
    raw_pages = doc.get("pages")
    if not isinstance(raw_pages, list):
        raise DataError("应用导出缺少 pages 数组")
    pages: list[dict] = []
    provenance: dict[str, dict] = {}
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
        provenance[name] = {key: raw[key] for key in PROVENANCE_KEYS if key in raw}
    raw_source = doc.get("source")
    return (pages, provenance, raw_source if isinstance(raw_source, dict) else None,
            doc.get("schemaVersion"))


def load_app(path: Path) -> list[dict]:
    """-> [{file, boxes}]，顺序即文件内页序（应用导出无题数字段，框数即题数）。"""
    return _load_app_document(path)[0]


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

def count_verdict_v2(gold_count: int, app_count: int) -> tuple[bool, str]:
    """历史口径：只看两个整数（单题页 0 框即一致）。保留为 v3 的 `countExactV2` 对照。"""
    if gold_count < 2:
        if app_count == 0:
            return True, "一致（单题页：不拆分=期望）"
        return False, f"不一致（单题页却被拆 {app_count} 块）"
    if app_count == gold_count:
        return True, "一致"
    if app_count == 0:
        return False, f"不一致（金标 {gold_count} vs 应用 0，漏拆）"
    return False, f"不一致（金标 {gold_count} vs 应用 {app_count}）"


def classify_page(gold_count: int, app_count: int, decision: object,
                  outcome_status: object, app_page_missing: bool) -> str:
    """口径 v3 逐页页类（判定输入只来自导出，不猜）。"""
    if app_page_missing:
        return PAGE_CLASS_MISSING_APP
    if outcome_status != "OK":
        return PAGE_CLASS_NOT_EVALUABLE_CHANNEL
    if decision is None:
        return PAGE_CLASS_NOT_EVALUABLE_CHANNEL
    if decision == "SPLIT":
        minimum, maximum = SPLIT_REGION_COUNT_RANGE
        # 导出只写消费侧判「可用」之后的区域（assemble_export.py:33-41）；0 框 =
        # 模型想切但区域被整组拒绝（如 005144 的互相重叠框），不是弃答。
        return (PAGE_CLASS_ACCEPTED_SPLIT if minimum <= app_count <= maximum
                else PAGE_CLASS_SPLIT_UNUSABLE)
    if decision == "PASS":
        return PAGE_CLASS_ACCEPTED_NO_SPLIT
    if decision in ABSTAIN_DECISIONS:
        return PAGE_CLASS_ABSTAIN
    return PAGE_CLASS_NOT_EVALUABLE_CHANNEL


def count_verdict(gold_count: int, app_count: int,
                  decision: object = None, outcome_status: object = None,
                  app_page_missing: bool = False,
                  decision_source: str = "export") -> tuple[bool, str]:
    """① 判定（v3）：返回 (是否一致, 说明)。

    `decision_source="absent"` 只用于整份导出没有判定字段的历史设备导出：行为逐字
    回落 [count_verdict_v2]，避免把「未知」当「不一致」改写历史读数。
    """
    if decision_source == "absent":
        return count_verdict_v2(gold_count, app_count)
    page_class = classify_page(gold_count, app_count, decision, outcome_status,
                               app_page_missing)
    if page_class == PAGE_CLASS_ACCEPTED_SPLIT:
        if app_count == gold_count:
            return True, "一致（SPLIT，区域可用且题数相同）"
        if app_count == 0:
            return False, f"不一致（金标 {gold_count} vs 应用 0，漏拆）"
        return False, f"不一致（金标 {gold_count} vs 应用 {app_count}）"
    if page_class == PAGE_CLASS_ACCEPTED_NO_SPLIT:
        if gold_count < 2 and app_count == 0:
            return True, "一致（PASS：整页一道题，无需切分）"
        return False, f"不一致（PASS 把 {gold_count} 道题读成整页 1 道）"
    if page_class == PAGE_CLASS_SPLIT_UNUSABLE:
        return False, "不一致（SPLIT 但区域不可用：产品未切）"
    if page_class == PAGE_CLASS_ABSTAIN:
        return False, f"不一致（弃答 {decision}，按 ITT 计）"
    if page_class == PAGE_CLASS_NOT_EVALUABLE_CHANNEL:
        detail = f"status={outcome_status!r}, decision={decision!r}"
        return False, f"不一致（通道不可评：{detail}，按 ITT 计）"
    if page_class == PAGE_CLASS_MISSING_APP:
        return False, "不一致（应用缺该页，按 0 框计）"
    return False, "不一致（未知页类）"


def format_iou_column(gold_boxes: list[dict], matched: dict[int, float]) -> str:
    parts = []
    for index in range(len(gold_boxes)):
        parts.append(f"{matched[index]:.3f}" if index in matched else "—")
    return "/".join(parts) if parts else "（无题）"


# ---------------------------------------------------------------- 口径计算（人读与 --json 单一事实源）

def _page_metric(page: dict, app_boxes: list[dict], app_page_missing: bool,
                 app_provenance: dict | None = None,
                 decision_source: str = "export") -> dict:
    """单页 ①②③ 计算（唯一实现；人读逐页行与 --json 报告都消费它）。

    `app_provenance` 只透传导出器写的来源（nameSource/jobId/...）；缺失即 null/False，
    不让"文件名看起来像真的"冒充"库里有这个页名"。
    """
    gold_boxes = page["boxes"]
    gold_count = page["questionCount"]
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
    provenance = app_provenance or {}
    decision = provenance.get("decision")
    outcome_status = provenance.get("outcomeStatus")
    strict = decision_source != "absent"
    ok, reason = count_verdict(gold_count, len(app_boxes), decision=decision,
                               outcome_status=outcome_status,
                               app_page_missing=app_page_missing,
                               decision_source=decision_source)
    v2_ok, _v2_reason = count_verdict_v2(gold_count, len(app_boxes))
    single_unverified = (not strict) and gold_count < 2
    page_class = (classify_page(gold_count, len(app_boxes), decision, outcome_status,
                                app_page_missing)
                  if strict else PAGE_CLASS_V2_FALLBACK)
    # 题数口径的显式取值：弃答/通道失败/缺页是 `null`（没有判定），不是 0 框；
    # PASS 是 "none"（整页不拆分）。框集仍如实记 appBoxes，供 ②/③ 使用。
    if page_class == PAGE_CLASS_ACCEPTED_SPLIT:
        metric_question_count: object = len(app_boxes)
    elif page_class == PAGE_CLASS_ACCEPTED_NO_SPLIT:
        metric_question_count = "none"
    elif page_class == PAGE_CLASS_SPLIT_UNUSABLE:
        metric_question_count = 0
    elif page_class == PAGE_CLASS_V2_FALLBACK:
        metric_question_count = len(app_boxes)
    else:
        metric_question_count = None
    integrity = provenance.get("integrity")
    return {
        "file": page["file"],
        "goldQuestions": gold_count,
        "appBoxes": len(app_boxes),
        "questionCountForMetric": metric_question_count,
        "consistent": ok,
        "consistentV2": v2_ok,
        "reason": reason,
        "pageClass": page_class,
        "appDecision": decision,
        "appOutcomeStatus": outcome_status,
        "singleQuestionUnverified": single_unverified,
        "legacyConsistent": (gold_count == len(app_boxes)),
        "matchedIous": [matched.get(index) for index in range(len(gold_boxes))],
        "iouOk": iou_ok,
        "fragmentedCount": len(fragmented),
        "fragmentedGoldIndices": fragmented,
        "fragmentPiecesByGoldIndex": pieces,
        "fragmentedByThreshold": {str(threshold): count
                                  for threshold, count in threshold_counts.items()},
        # 配对只覆盖 min(金标, 应用) 个框；超出者此前完全不进任何数（R5 §2.3）。
        "unmatchedAppBoxCount": len(app_boxes) - len(pairs),
        "goldBoxesSha256": canonical_sha256(gold_boxes),
        "appBoxesSha256": canonical_sha256(app_boxes),
        "appPageMissing": app_page_missing,
        "appPageId": provenance.get("pageId"),
        "appPageIdKind": provenance.get("pageIdKind"),
        "appJobQuestionCountMatches": (integrity.get("jobQuestionCountMatches")
                                       if isinstance(integrity, dict) else None),
        "appJobPageCountMatches": (integrity.get("jobPageCountMatches")
                                   if isinstance(integrity, dict) else None),
        "appNameSource": provenance.get("nameSource"),
        "appJobId": provenance.get("jobId"),
        "appPageIndex": provenance.get("pageIndex"),
        "appProvenance": bool(provenance),
    }


def _matched_from_row(row: dict) -> dict[int, float]:
    return {index: value for index, value in enumerate(row["matchedIous"])
            if value is not None}


def _aggregate(rows: list[dict], app_pages: list[dict] | None) -> dict:
    """页级行 → 指标/门值（人读摘要与 --json 报告共用；分母口径见各字段）。"""
    page_count = len(rows)
    total_gold_questions = sum(row["goldQuestions"] for row in rows)
    consistent_pages = sum(1 for row in rows if row["consistent"])
    v2_consistent_pages = sum(1 for row in rows if row["consistentV2"])
    legacy_consistent_pages = sum(1 for row in rows if row["legacyConsistent"])
    changed_pages = [row["file"] for row in rows
                     if row["consistent"] != row["legacyConsistent"]]
    changed_pages_v2 = [row["file"] for row in rows
                        if row["consistent"] != row["consistentV2"]]
    abstain_pages = [row["file"] for row in rows
                     if row["pageClass"] == PAGE_CLASS_ABSTAIN]
    not_evaluable_pages = [row["file"] for row in rows
                           if row["pageClass"] == PAGE_CLASS_NOT_EVALUABLE_CHANNEL]
    single_unverified_pages = [row["file"] for row in rows
                               if row["singleQuestionUnverified"]]
    iou_ok_total = sum(row["iouOk"] for row in rows)
    fragment_total = sum(row["fragmentedCount"] for row in rows)
    fragment_by_threshold = {
        str(threshold): sum(row["fragmentedByThreshold"][str(threshold)] for row in rows)
        for threshold in FRAGMENT_THRESHOLDS
    }
    count_rate = consistent_pages / page_count if page_count else 0.0
    v2_count_rate = v2_consistent_pages / page_count if page_count else 0.0
    iou_rate = iou_ok_total / total_gold_questions if total_gold_questions else 0.0
    fragment_rate = fragment_total / total_gold_questions if total_gold_questions else 0.0
    legacy_count_rate = legacy_consistent_pages / page_count if page_count else 0.0
    golden_name_set = {row["file"] for row in rows}
    extra_pages = [page["file"] for page in (app_pages or [])
                   if page["file"] not in golden_name_set]
    missing_app_pages = [row["file"] for row in rows if row["appPageMissing"]]
    integrity_mismatch_pages = [row["file"] for row in rows
                                if row["appJobQuestionCountMatches"] is False
                                or row["appJobPageCountMatches"] is False]
    decision_source = ("absent" if rows and all(
        row["pageClass"] == PAGE_CLASS_V2_FALLBACK for row in rows) else "export")
    caliber = "v2-fallback" if decision_source == "absent" else "v3"
    return {
        "countExact": {"successes": consistent_pages, "denominator": page_count,
                       "value": count_rate},
        # 旧口径整份样本重算（只作对照；不参与门式）。
        "countExactV2": {"successes": v2_consistent_pages, "denominator": page_count,
                         "value": v2_count_rate},
        "iou": {"successes": iou_ok_total, "denominator": total_gold_questions,
                "value": iou_rate, "threshold": IOU_THRESHOLD,
                "informationalOnly": True},
        "fragment": {"successes": fragment_total, "denominator": total_gold_questions,
                     "value": fragment_rate,
                     "thresholdSensitivity": dict(fragment_by_threshold)},
        "countRate": count_rate,
        "iouRate": iou_rate,
        "fragmentRate": fragment_rate,
        "legacyCountExact": {"successes": legacy_consistent_pages,
                             "denominator": page_count, "value": legacy_count_rate},
        "changedPages": changed_pages,
        "changedPagesV2": changed_pages_v2,
        "abstain": {"count": len(abstain_pages), "pages": abstain_pages,
                    "rate": len(abstain_pages) / page_count if page_count else 0.0},
        "notEvaluable": {"count": len(not_evaluable_pages), "pages": not_evaluable_pages,
                         "rate": len(not_evaluable_pages) / page_count if page_count else 0.0},
        "singleQuestionUnverifiedCount": len(single_unverified_pages),
        "singleQuestionUnverifiedPages": single_unverified_pages,
        "caliber": caliber,
        "decisionSource": decision_source,
        "pageCount": page_count,
        "totalGoldQuestions": total_gold_questions,
        "gates": {"count": {"threshold": COUNT_GATE, "met": count_rate >= COUNT_GATE},
                  "fragment": {"threshold": FRAGMENT_GATE,
                               "met": fragment_rate <= FRAGMENT_GATE}},
        "gatePassed": count_rate >= COUNT_GATE and fragment_rate <= FRAGMENT_GATE,
        "missingAppPages": missing_app_pages,
        "extraAppPages": extra_pages,
        "integrityMismatchPages": integrity_mismatch_pages,
    }


def _describe_golden(golden_path: Path, golden_pages: list[dict]) -> dict:
    return {"file": golden_path.name,
            "sha256": hashlib.sha256(golden_path.read_bytes()).hexdigest(),
            "bytes": golden_path.stat().st_size,
            "pageCount": len(golden_pages),
            "questionCount": sum(len(page["boxes"]) for page in golden_pages)}


def _describe_app_source(source: dict | None) -> dict:
    """文件级溯源摘要（导出器写的 `source` 块）。缺失即 present=false，不补默认值。

    v2 的输入版本字段（`db.userVersion`/快照身份/载荷身份/生产者 sha）原样透传：
    跨 schema 比较切分准确率没有意义，报告里必须能看出这份导出产自哪一版库。
    """
    if not isinstance(source, dict):
        return {"present": False, "dbSha256": None, "dbUserVersion": None,
                "snapshotIdentitySha256": None, "snapshotMode": None,
                "payloadSha256": None, "producerSha256": None,
                "producerContract": None, "nameSourceCounts": None, "pageMap": None}
    db = source.get("db") if isinstance(source.get("db"), dict) else {}
    snapshot = source.get("snapshot") if isinstance(source.get("snapshot"), dict) else {}
    payload = source.get("payload") if isinstance(source.get("payload"), dict) else {}
    producer = source.get("producer") if isinstance(source.get("producer"), dict) else {}
    counts = source.get("nameSourceCounts")
    return {"present": True, "dbSha256": db.get("sha256"),
            "dbUserVersion": db.get("userVersion"),
            "snapshotIdentitySha256": snapshot.get("identitySha256"),
            "snapshotMode": snapshot.get("mode"),
            "payloadSha256": payload.get("sha256"),
            "producerSha256": producer.get("sha256"),
            "producerContract": producer.get("contract"),
            "nameSourceCounts": counts if isinstance(counts, dict) else None,
            "pageMap": source.get("pageMap")}


def _describe_app(app_path: Path, app_pages: list[dict] | None,
                  app_source: dict | None = None,
                  app_schema_version: object = None) -> dict:
    exists = app_pages is not None
    return {"file": app_path.name, "exists": exists,
            "appSchemaVersion": app_schema_version if exists else None,
            "sha256": hashlib.sha256(app_path.read_bytes()).hexdigest() if exists else None,
            "bytes": app_path.stat().st_size if exists else None,
            "pageCount": len(app_pages) if exists else None,
            "boxCount": (sum(len(page["boxes"]) for page in app_pages) if exists else None),
            "source": _describe_app_source(app_source)}


def build_report(golden_path: Path, golden_pages: list[dict], app_path: Path) -> dict:
    """--json 机器报告（与入读同一份单页/汇总计算）。应用导出缺失时如实报缺（不判门）。"""
    app_exists = app_path.is_file()
    report: dict = {
        "reportSchemaVersion": 1,
        "caliber": "v3",
        "golden": _describe_golden(golden_path, golden_pages),
        "app": _describe_app(app_path, None),
        "automation": {"enabled": False, "ruling": AUTOMATION_RULING},
    }
    if not app_exists:
        report.update({"pages": [], "metrics": None, "gates": None, "gatePassed": None,
                       "legacyCountExact": None, "countExactV2": None,
                       "changedPages": [], "changedPagesV2": [],
                       "abstain": None, "notEvaluable": None,
                       "singleQuestionUnverifiedCount": None,
                       "singleQuestionUnverifiedPages": [],
                       "decisionSource": None,
                       "missingAppPages": [], "extraAppPages": []})
        return report
    app_pages, app_provenance, app_source, app_schema_version = _load_app_document(app_path)
    # 回落是**整份导出**的属性：设备导出的 SELECT 不含 decision/outcomeStatus。
    # 按页回落会让判定型导出里的缺页（无页记录 ⇒ 无判定字段）重新变成假阳性。
    decision_source = ("export" if any(
        "decision" in provenance or "outcomeStatus" in provenance
        for provenance in app_provenance.values()) else "absent")
    app_by_file = {page["file"]: page["boxes"] for page in app_pages}
    rows = [_page_metric(page, app_by_file.get(page["file"], []),
                         page["file"] not in app_by_file,
                         app_provenance.get(page["file"]),
                         decision_source=decision_source)
            for page in golden_pages]
    report["app"] = _describe_app(app_path, app_pages, app_source, app_schema_version)
    report["pages"] = rows
    agg = _aggregate(rows, app_pages)
    report["metrics"] = {"countExact": agg["countExact"], "iou": agg["iou"],
                         "fragment": agg["fragment"]}
    report["gates"] = agg["gates"]
    report["gatePassed"] = agg["gatePassed"]
    report["legacyCountExact"] = agg["legacyCountExact"]
    report["countExactV2"] = agg["countExactV2"]
    report["changedPages"] = agg["changedPages"]
    report["changedPagesV2"] = agg["changedPagesV2"]
    report["abstain"] = agg["abstain"]
    report["notEvaluable"] = agg["notEvaluable"]
    report["singleQuestionUnverifiedCount"] = agg["singleQuestionUnverifiedCount"]
    report["singleQuestionUnverifiedPages"] = agg["singleQuestionUnverifiedPages"]
    report["caliber"] = agg["caliber"]
    report["decisionSource"] = agg["decisionSource"]
    report["missingAppPages"] = agg["missingAppPages"]
    report["extraAppPages"] = agg["extraAppPages"]
    report["integrityMismatchPages"] = agg["integrityMismatchPages"]
    return report


def _print_json_error(message: str, code: int) -> int:
    print(json.dumps({"reportSchemaVersion": 1, "error": message, "gatePassed": None,
                      "automation": {"enabled": False, "ruling": AUTOMATION_RULING}},
                     ensure_ascii=False, sort_keys=True, indent=2))
    return code


# ---------------------------------------------------------------- 主流程

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="切分准确率度量（4C② 门；口径 v3，历史导出回落 v2）")
    parser.add_argument("--golden", type=Path, default=DEFAULT_GOLDEN)
    parser.add_argument("--app", type=Path, default=DEFAULT_APP)
    parser.add_argument("--json", action="store_true",
                        help="stdout 只输出机器可读 JSON 报告（供 check_split_gate.py 消费）")
    args = parser.parse_args(argv)

    if not args.golden.is_file():
        message = f"切分准确率度量 FAIL：金标文件不存在：{args.golden}"
        if args.json:
            return _print_json_error(message, 2)
        print(message)
        return 2
    try:
        golden_pages = load_golden(args.golden)
    except DataError as failure:
        message = f"切分准确率度量 FAIL：{failure}"
        if args.json:
            return _print_json_error(message, 2)
        print(message)
        return 2

    if args.json:
        try:
            report = build_report(args.golden, golden_pages, args.app)
        except DataError as failure:
            return _print_json_error(f"切分准确率度量 FAIL：{failure}", 2)
        print(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2))
        return 0 if report["app"]["exists"] else 3

    print("切分准确率度量（4C② 门，口径 v3：单题页只有被接受的不拆分才一致；"
          "阈值 0.9 / 0.05 未动）")
    print("数据源")
    print(f"  金标：{args.golden}（{args.golden.stat().st_size} B，"
          f"{len(golden_pages)} 页 / {sum(len(p['boxes']) for p in golden_pages)} 题）")

    if not args.app.is_file():
        print(f"  应用：{args.app} —— **缺失**（如实报缺：无法度量，无逐页表）")
        print("指标：n/a（应用导出缺失；请先在设备上导出 build/split-eval/app-split.json）")
        print(f"自动化：{AUTOMATION_RULING}")
        return 3

    try:
        app_pages, app_provenance, _app_source, _app_schema_version = \
            _load_app_document(args.app)
    except DataError as failure:
        print(f"切分准确率度量 FAIL：{failure}")
        return 2

    decision_source = ("export" if any(
        "decision" in provenance or "outcomeStatus" in provenance
        for provenance in app_provenance.values()) else "absent")
    app_by_file = {page["file"]: page["boxes"] for page in app_pages}
    print(f"  应用：{args.app}（{args.app.stat().st_size} B，"
          f"{len(app_pages)} 页 / {sum(len(p['boxes']) for p in app_pages)} 框；"
          f"判定字段：{'有（严格 v3）' if decision_source == 'export' else '无（整份回落 v2）'}）")

    # ------------------------------------------------------------ 逐页
    print()
    print("逐页明细（按金标页序；配对 = 该页最大权 1:1 配对的金标题 IoU，'—'=未配对）")
    header = (f"{'file':<24} | {'金标题数':>6} | {'应用题数':>6} | {'页类':<22}"
              f" | {'①一致性(v3)':<30} | {'配对 IoU（按金标题序）':<34}"
              f" | {'≥0.5':>4} | {'切残':>4}")
    print(header)
    print("-" * len(header))

    rows: list[dict] = []
    for page in golden_pages:
        name = page["file"]
        has_app_page = name in app_by_file
        row = _page_metric(page, app_by_file.get(name, []), not has_app_page,
                           app_provenance.get(name),
                           decision_source=decision_source)
        rows.append(row)
        suffix = "" if has_app_page else "（应用缺该页，按 0 框计）"
        print(f"{row['file']:<24} | {row['goldQuestions']:>6} | {row['appBoxes']:>6} |"
              f" {row['pageClass']:<22} | {row['reason']:<30}"
              f" | {format_iou_column(page['boxes'], _matched_from_row(row)):<34} |"
              f" {row['iouOk']:>4} | {row['fragmentedCount']:>4}{suffix}")

    agg = _aggregate(rows, app_pages)
    count_rate = agg["countRate"]
    fragment_rate = agg["fragmentRate"]
    gate_pass = agg["gatePassed"]

    print()
    print(f"指标（① 分母=可比金标页数 {agg['pageCount']}；"
          f"②③ 分母=金标总题数 {agg['totalGoldQuestions']}）")
    print(f"  ① 题数完全一致率 countExactRate = {agg['countExact']['successes']}/{agg['countExact']['denominator']}"
          f" = {count_rate:.4f}")
    print(f"     门：{count_rate:.4f} >= {COUNT_GATE} ->"
          f" {'过' if count_rate >= COUNT_GATE else '未过'}")
    print(f"     口径：{agg['caliber']}（判定字段来源：{agg['decisionSource']}；"
          "单题页只有被接受的 PASS 才记一致）")
    if agg["countExactV2"]["value"] != count_rate:
        print(f"     旧口径（v2，0 框即单题一致）同一样本对照 = "
              f"{agg['countExactV2']['successes']}/{agg['countExactV2']['denominator']}"
              f" = {agg['countExactV2']['value']:.4f}；v3 改判页"
              f"（{len(agg['changedPagesV2'])} 页）："
              + "、".join(agg["changedPagesV2"]))
    print(f"  弃答（RECAPTURE/NEED_MORE_IMAGE）：{agg['abstain']['count']}/"
          f"{agg['pageCount']} = {agg['abstain']['rate']:.4f}"
          "（只报数、不设门；进 ① 按 ITT 记不一致）")
    print(f"  通道不可评（status!=OK/无判定/契约拒收）：{agg['notEvaluable']['count']}/"
          f"{agg['pageCount']} = {agg['notEvaluable']['rate']:.4f}"
          "（按 ITT 记不一致）")
    if agg["singleQuestionUnverifiedCount"]:
        print(f"注意：{agg['singleQuestionUnverifiedCount']} 页单题页没有 decision/outcomeStatus"
              "（历史设备导出），已回落 v2 并计入 singleQuestionUnverifiedCount；"
              f"caliber={agg['caliber']}，不得声称 v3 严格口径")
    if agg["legacyCountExact"]["value"] != count_rate:
        print(f"     原口径（v1，数值相等即一致）对照 = "
              f"{agg['legacyCountExact']['successes']}/{agg['legacyCountExact']['denominator']}"
              f" = {agg['legacyCountExact']['value']:.4f}；改判页（{len(agg['changedPages'])} 页）："
              + "、".join(agg["changedPages"]))
        print("     差异原因：v1 把单题页「金标 1 vs 应用 0」按数值不等记不一致（定义错）；"
              "v3 还纠正弃答/通道失败被记一致的假阳性（见上两行）。")
    print(f"  ② 边界 IoU≥0.5 题占比 iouRate = {agg['iou']['successes']}/{agg['iou']['denominator']}"
          f" = {agg['iouRate']:.4f}（只度量，不设门）")
    print(f"  ③ 切残率 fragmentRate = {agg['fragment']['successes']}/{agg['fragment']['denominator']}"
          f" = {fragment_rate:.4f}")
    print(f"     门：{fragment_rate:.4f} <= {FRAGMENT_GATE} ->"
          f" {'过' if fragment_rate <= FRAGMENT_GATE else '未过'}")
    sensitivity = "、".join(
        f"{threshold}: {agg['fragment']['thresholdSensitivity'][str(threshold)]}"
        f"/{agg['totalGoldQuestions']}"
        for threshold in FRAGMENT_THRESHOLDS)
    print(f"     阈值敏感性 0.3/0.4/0.5/0.6 -> {sensitivity}")
    print()
    print(f"门判定（① ≥ {COUNT_GATE} 且 ③ ≤ {FRAGMENT_GATE}，阈值未动）："
          f"{'true' if gate_pass else 'false'}"
          + ("" if gate_pass else
             f"（{'① ' + format(count_rate, '.4f') + ' < ' + format(COUNT_GATE, '.1f') if count_rate < COUNT_GATE else ''}"
             f"{'；' if count_rate < COUNT_GATE and fragment_rate > FRAGMENT_GATE else ''}"
             f"{'③ ' + format(fragment_rate, '.4f') + ' > ' + format(FRAGMENT_GATE, '.2f') if fragment_rate > FRAGMENT_GATE else ''}）"))
    print(f"自动化：{AUTOMATION_RULING if not gate_pass else AUTOMATION_RULING_PASS}")
    if agg["missingAppPages"]:
        print(f"注意：应用导出缺以下金标页（按 0 框计）：{'、'.join(agg['missingAppPages'])}")
    if agg["extraAppPages"]:
        print(f"注意：应用导出多出金标没有的页（不进入任何分母）：{'、'.join(agg['extraAppPages'])}")
    if agg["integrityMismatchPages"]:
        print("注意：以下页的 job 记账题数/页数与导出行数不一致（integrity=false，"
              f"门报告如实列出）：{'、'.join(agg['integrityMismatchPages'])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
