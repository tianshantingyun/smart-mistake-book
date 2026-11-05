#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""蜕变关系库（批次 44 / D0 item 7，计划 §0 蜕变测试）。

## 它消灭的具体失败

渲染/导出/检索/调度没有廉价 oracle。只说“看起来对”无法验收。本工具把四类
必守关系写成可执行断言：

| 域 | 关系 | 输入变换 | 必守 |
|---|---|---|---|
| render | `reflow_text` | 文本换行/空白重排 | 规范化文本与节点序列不变 |
| render | `theme_swap` | light→dark | 文本/几何/顺序不变 |
| render | `font_scale_reflow` | fontScale 1→2 | 文本不变且节点仍在 viewBox |
| export | `append_item` | 追加一个错题条目 | 页数不减、既有文本前缀保持 |
| export | `normalize_whitespace` | 空白规范化 | 规范化文本与页数不变 |
| export | `deterministic_hash` | 同输入再导出 | 输出字节哈希相同 |
| retrieval | `duplicate_doc` | 语料追加同文重复文档 | top1 与既有分数不变 |
| retrieval | `reorder_corpus` | 语料顺序反转 | top1 与分数不变 |
| retrieval | `append_unrelated` | 追加无关文档 | 既有文档分数不变 |
| scheduling | `add_late_review` | 追加一次逾期复习 | 间隔不缩短、到期不提前 |
| scheduling | `same_day_shift` | 同日时间平移 | 到期日不变 |
| scheduling | `add_failure` | 追加一次失败 | 间隔不增加 |

## 两种模式

- `--demo`：用内置合成对证明 12 条关系可执行（自测/CI 烟测）；
- `--pairs-dir DIR`：读取真实产物对
  `DIR/<domain>/<relation>.before.json` 与 `.after.json`，校验关系。
  缺夹具 exit 2，绝不判 PASS。

退出码：0 全部成立；1 有反例；2 缺夹具/参数错误。
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parents[2]

WS_RE = re.compile(r"\s+")


def norm_text(value: str) -> str:
    return WS_RE.sub(" ", value or "").strip()


def _labels(doc: dict) -> list[dict]:
    return [node for node in doc.get("nodes", []) if node.get("kind") == "label"]


def _node_signature(doc: dict) -> list[tuple]:
    signature = []
    for node in doc.get("nodes", []):
        signature.append((
            node.get("kind"), norm_text(node.get("text", "")),
            round(float(node.get("x", 0)), 6), round(float(node.get("y", 0)), 6),
            round(float(node.get("w", 0)), 6), round(float(node.get("h", 0)), 6),
        ))
    return signature


def _fail(reason: str, before: Any, after: Any) -> tuple[bool, str, dict]:
    return False, reason, {"before": before, "after": after}


def check_render_text(before: dict, after: dict) -> tuple[bool, str, dict]:
    a = [norm_text(n.get("text", "")) for n in _labels(before)]
    b = [norm_text(n.get("text", "")) for n in _labels(after)]
    if a != b:
        return _fail("reflow 后规范化文本不一致", a, b)
    if len(before.get("nodes", [])) != len(after.get("nodes", [])):
        return _fail("节点数变化", len(before.get("nodes", [])), len(after.get("nodes", [])))
    return True, "", {}


def check_render_theme(before: dict, after: dict) -> tuple[bool, str, dict]:
    if _node_signature(before) != _node_signature(after):
        return _fail("主题切换改变了文本或几何", _node_signature(before), _node_signature(after))
    return True, "", {}


def check_render_font_scale(before: dict, after: dict) -> tuple[bool, str, dict]:
    a = [norm_text(n.get("text", "")) for n in _labels(before)]
    b = [norm_text(n.get("text", "")) for n in _labels(after)]
    if a != b:
        return _fail("fontScale 变化改变了文本", a, b)
    view = after.get("viewBox") or before.get("viewBox")
    if not view or len(view) != 4:
        return _fail("缺 viewBox，无法判边界", view, view)
    min_x, min_y, max_x, max_y = view
    for node in after.get("nodes", []):
        x, y = float(node.get("x", 0)), float(node.get("y", 0))
        w, h = float(node.get("w", 0)), float(node.get("h", 0))
        if x < min_x or y < min_y or x + w > max_x or y + h > max_y:
            return _fail("fontScale 后节点越出 viewBox", node, view)
    return True, "", {}


def check_export_append(before: dict, after: dict) -> tuple[bool, str, dict]:
    if int(after.get("page_count", 0)) < int(before.get("page_count", 0)):
        return _fail("追加条目后页数减少", before.get("page_count"), after.get("page_count"))
    a = before.get("extracted_text", [])
    b = after.get("extracted_text", [])
    if b[: len(a)] != a:
        return _fail("既有文本前缀未保持", a, b)
    return True, "", {}


def check_export_whitespace(before: dict, after: dict) -> tuple[bool, str, dict]:
    a = [norm_text(x) for x in before.get("extracted_text", [])]
    b = [norm_text(x) for x in after.get("extracted_text", [])]
    if a != b:
        return _fail("空白规范化改变了文本", a, b)
    if before.get("page_count") != after.get("page_count"):
        return _fail("空白规范化改变了页数", before.get("page_count"), after.get("page_count"))
    return True, "", {}


def _stable_hash(doc: dict) -> str:
    return hashlib.sha256(
        json.dumps(doc, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


def check_export_deterministic(before: dict, after: dict) -> tuple[bool, str, dict]:
    if _stable_hash(before) != _stable_hash(after):
        return _fail("同输入再次导出哈希不同", _stable_hash(before), _stable_hash(after))
    return True, "", {}


def _retrieval_invariant(before: dict, after: dict) -> tuple[bool, str, dict]:
    after_queries = {q.get("q"): q for q in after.get("queries", [])}
    for query in before.get("queries", []):
        key = query.get("q")
        match = after_queries.get(key)
        if match is None:
            return _fail("查询在变换后消失", key, None)
        if query.get("top1") != match.get("top1"):
            return _fail("top1 改变", query.get("top1"), match.get("top1"))
        for doc_id, score in (query.get("scores") or {}).items():
            new_score = (match.get("scores") or {}).get(doc_id)
            if new_score is None or abs(float(new_score) - float(score)) > 1e-9:
                return _fail(f"既有文档 {doc_id} 分数改变", score, new_score)
    return True, "", {}


def check_retrieval_duplicate(before: dict, after: dict) -> tuple[bool, str, dict]:
    return _retrieval_invariant(before, after)


def check_retrieval_reorder(before: dict, after: dict) -> tuple[bool, str, dict]:
    return _retrieval_invariant(before, after)


def check_retrieval_append(before: dict, after: dict) -> tuple[bool, str, dict]:
    return _retrieval_invariant(before, after)


def _cards(doc: dict) -> dict[str, dict]:
    return {card.get("id"): card for card in doc.get("cards", [])}


def check_scheduling_late(before: dict, after: dict) -> tuple[bool, str, dict]:
    a, b = _cards(before), _cards(after)
    for card_id, item in a.items():
        other = b.get(card_id)
        if other is None:
            return _fail("卡片消失", card_id, None)
        if float(other.get("interval_days", 0)) < float(item.get("interval_days", 0)):
            return _fail(f"{card_id} 间隔缩短", item.get("interval_days"), other.get("interval_days"))
        if str(other.get("due", "")) < str(item.get("due", "")):
            return _fail(f"{card_id} 到期提前", item.get("due"), other.get("due"))
    return True, "", {}


def check_scheduling_same_day(before: dict, after: dict) -> tuple[bool, str, dict]:
    a, b = _cards(before), _cards(after)
    for card_id, item in a.items():
        other = b.get(card_id)
        if other is None:
            return _fail("卡片消失", card_id, None)
        if item.get("due") != other.get("due"):
            return _fail(f"{card_id} 同日平移改变了到期日", item.get("due"), other.get("due"))
    return True, "", {}


def check_scheduling_failure(before: dict, after: dict) -> tuple[bool, str, dict]:
    a, b = _cards(before), _cards(after)
    for card_id, item in a.items():
        other = b.get(card_id)
        if other is None:
            return _fail("卡片消失", card_id, None)
        if float(other.get("interval_days", 0)) > float(item.get("interval_days", 0)):
            return _fail(f"{card_id} 失败后间隔反而增加",
                         item.get("interval_days"), other.get("interval_days"))
    return True, "", {}


# ---------------------------------------------------------------- transforms


def t_render_reflow(before: dict) -> dict:
    after = copy.deepcopy(before)
    for node in _labels(after):
        node["text"] = "\n".join((node.get("text") or "").split())
    return after


def t_render_theme(before: dict) -> dict:
    after = copy.deepcopy(before)
    after["theme"] = "dark" if before.get("theme") != "dark" else "light"
    return after


def t_render_font_scale(before: dict) -> dict:
    after = copy.deepcopy(before)
    after["fontScale"] = 2.0
    return after


def t_export_append(before: dict) -> dict:
    after = copy.deepcopy(before)
    after.setdefault("items", []).append({"id": "new", "text": "追加条目"})
    after["page_count"] = int(before.get("page_count", 0)) + 1
    after.setdefault("extracted_text", []).append("追加条目")
    return after


def t_export_whitespace(before: dict) -> dict:
    after = copy.deepcopy(before)
    after["extracted_text"] = [WS_RE.sub(" ", text).strip()
                               for text in before.get("extracted_text", [])]
    return after


def t_export_identity(before: dict) -> dict:
    return copy.deepcopy(before)


def t_retrieval_duplicate(before: dict) -> dict:
    after = copy.deepcopy(before)
    docs = after.setdefault("docs", [])
    if docs:
        duplicate = copy.deepcopy(docs[0])
        duplicate["id"] = "dup-" + str(duplicate.get("id"))
        docs.append(duplicate)
    return after


def t_retrieval_reorder(before: dict) -> dict:
    after = copy.deepcopy(before)
    after["docs"] = list(reversed(before.get("docs", [])))
    return after


def t_retrieval_append(before: dict) -> dict:
    after = copy.deepcopy(before)
    after.setdefault("docs", []).append({"id": "unrelated", "text": "无关文档"})
    return after


def t_scheduling_late(before: dict) -> dict:
    after = copy.deepcopy(before)
    for card in after.get("cards", []):
        card["interval_days"] = float(card.get("interval_days", 0)) + 2
        card["due"] = str(card.get("due", ""))
        card["reviews"] = list(card.get("reviews", [])) + [{"t": "late", "rating": "good"}]
    return after


def t_scheduling_shift(before: dict) -> dict:
    after = copy.deepcopy(before)
    for card in after.get("cards", []):
        review_t = card.get("review_time")
        if isinstance(review_t, str) and "T" in review_t:
            card["review_time"] = review_t.split("T")[0] + "T23:59:00"
    return after


def t_scheduling_failure(before: dict) -> dict:
    after = copy.deepcopy(before)
    for card in after.get("cards", []):
        card["interval_days"] = max(0.0, float(card.get("interval_days", 0)) - 1)
        card["reviews"] = list(card.get("reviews", [])) + [{"t": "again", "rating": "again"}]
    return after


@dataclass(frozen=True)
class Relation:
    name: str
    domain: str
    description: str
    transform: Callable[[dict], dict]
    check: Callable[[dict, dict], tuple[bool, str, dict]]


RELATIONS: tuple[Relation, ...] = (
    Relation("reflow_text", "render", "文本换行/空白重排", t_render_reflow, check_render_text),
    Relation("theme_swap", "render", "light/dark 切换", t_render_theme, check_render_theme),
    Relation("font_scale_reflow", "render", "fontScale 1→2", t_render_font_scale, check_render_font_scale),
    Relation("append_item", "export", "追加错题条目", t_export_append, check_export_append),
    Relation("normalize_whitespace", "export", "空白规范化", t_export_whitespace, check_export_whitespace),
    Relation("deterministic_hash", "export", "同输入再导出", t_export_identity, check_export_deterministic),
    Relation("duplicate_doc", "retrieval", "追加同文重复文档", t_retrieval_duplicate, check_retrieval_duplicate),
    Relation("reorder_corpus", "retrieval", "语料顺序反转", t_retrieval_reorder, check_retrieval_reorder),
    Relation("append_unrelated", "retrieval", "追加无关文档", t_retrieval_append, check_retrieval_append),
    Relation("add_late_review", "scheduling", "追加逾期复习", t_scheduling_late, check_scheduling_late),
    Relation("same_day_shift", "scheduling", "同日时间平移", t_scheduling_shift, check_scheduling_same_day),
    Relation("add_failure", "scheduling", "追加一次失败", t_scheduling_failure, check_scheduling_failure),
)

RELATION_BY_KEY = {f"{relation.domain}/{relation.name}": relation for relation in RELATIONS}


def demo_samples() -> dict[str, dict]:
    return {
        "render/reflow_text": {
            "viewBox": [0, 0, 300, 200], "theme": "light",
            "nodes": [{"kind": "label", "text": "alpha beta gamma", "x": 10, "y": 10, "w": 80, "h": 12},
                      {"kind": "path", "x": 0, "y": 0, "w": 100, "h": 100}],
        },
        "render/theme_swap": {
            "viewBox": [0, 0, 300, 200], "theme": "light",
            "nodes": [{"kind": "label", "text": "alpha", "x": 10, "y": 10, "w": 80, "h": 12}],
        },
        "render/font_scale_reflow": {
            "viewBox": [0, 0, 300, 200], "fontScale": 1.0,
            "nodes": [{"kind": "label", "text": "alpha", "x": 10, "y": 10, "w": 80, "h": 12}],
        },
        "export/append_item": {
            "items": [{"id": "a", "text": "A"}], "page_count": 1, "extracted_text": ["A"],
        },
        "export/normalize_whitespace": {
            "items": [{"id": "a", "text": "A  B"}], "page_count": 1, "extracted_text": ["A  B"],
        },
        "export/deterministic_hash": {
            "items": [{"id": "a", "text": "A"}], "page_count": 1, "extracted_text": ["A"],
        },
        "retrieval/duplicate_doc": {
            "docs": [{"id": "d1", "text": "alpha"}, {"id": "d2", "text": "beta"}],
            "queries": [{"q": "alpha", "top1": "d1", "scores": {"d1": 0.9, "d2": 0.1}}],
        },
        "retrieval/reorder_corpus": {
            "docs": [{"id": "d1", "text": "alpha"}, {"id": "d2", "text": "beta"}],
            "queries": [{"q": "alpha", "top1": "d1", "scores": {"d1": 0.9, "d2": 0.1}}],
        },
        "retrieval/append_unrelated": {
            "docs": [{"id": "d1", "text": "alpha"}, {"id": "d2", "text": "beta"}],
            "queries": [{"q": "alpha", "top1": "d1", "scores": {"d1": 0.9, "d2": 0.1}}],
        },
        "scheduling/add_late_review": {
            "cards": [{"id": "c1", "interval_days": 3, "due": "2026-11-10"}],
        },
        "scheduling/same_day_shift": {
            "cards": [{"id": "c1", "interval_days": 3, "due": "2026-11-10",
                       "review_time": "2026-11-05T08:00:00"}],
        },
        "scheduling/add_failure": {
            "cards": [{"id": "c1", "interval_days": 3, "due": "2026-11-10"}],
        },
    }


def run_relation(relation: Relation, before: dict, after: dict) -> dict:
    ok, reason, counterexample = relation.check(before, after)
    return {
        "name": relation.name, "domain": relation.domain,
        "description": relation.description, "ok": ok,
        "reason": reason, "counterexample": counterexample,
    }


def run_demo() -> dict:
    samples = demo_samples()
    results = []
    for relation in RELATIONS:
        key = f"{relation.domain}/{relation.name}"
        before = samples[key]
        after = relation.transform(before)
        results.append(run_relation(relation, before, after))
    return _payload(results)


def run_pairs(pairs_dir: Path, only: str | None = None) -> dict:
    if not pairs_dir.is_dir():
        raise FileNotFoundError(str(pairs_dir))
    relations = list(RELATIONS)
    if only:
        key = only if "/" in only else None
        if key is None:
            matches = [r for r in RELATIONS if r.name == only]
            if len(matches) == 1:
                relations = matches
            else:
                raise ValueError(f"未知关系 {only!r}")
        elif key not in RELATION_BY_KEY:
            raise ValueError(f"未知关系 {only!r}")
        else:
            relations = [RELATION_BY_KEY[key]]
    results = []
    missing: list[str] = []
    for relation in relations:
        base = pairs_dir / relation.domain / relation.name
        before_path = base.with_name(base.name + ".before.json")
        after_path = base.with_name(base.name + ".after.json")
        if not before_path.exists() or not after_path.exists():
            missing.append(str(base))
            continue
        before = json.loads(before_path.read_text(encoding="utf-8"))
        after = json.loads(after_path.read_text(encoding="utf-8"))
        results.append(run_relation(relation, before, after))
    if missing:
        raise FileNotFoundError("缺夹具: " + ", ".join(missing))
    return _payload(results)


def _payload(results: list[dict]) -> dict:
    return {
        "relations": results,
        "ok": all(item["ok"] for item in results),
        "exit_code": 0 if all(item["ok"] for item in results) else 1,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="蜕变关系校验")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--demo", action="store_true")
    mode.add_argument("--pairs-dir")
    parser.add_argument("--only")
    parser.add_argument("--out")
    args = parser.parse_args(argv)
    try:
        report = run_demo() if args.demo else run_pairs(Path(args.pairs_dir), args.only)
    except (FileNotFoundError, ValueError, json.JSONDecodeError) as exc:
        print(f"METAMORPHIC_ERROR: {exc}", file=sys.stderr)
        return 2
    payload = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.out:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(payload, encoding="utf-8", newline="\n")
    print(payload)
    return report["exit_code"]


if __name__ == "__main__":
    sys.exit(main())
