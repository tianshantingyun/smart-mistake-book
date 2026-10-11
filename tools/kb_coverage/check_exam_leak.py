# -*- coding: utf-8 -*-
"""判定表 MATERIAL 行的"例题派生"泄漏检查（4 条正则：题号/成套选项/答案语/题干尾）。

## 它消灭的失败

判定代理写回的材料里仍可能混入题目形态（题号标题、A/B/C/D 成套选项、
"答案/故选"语、短题干无结论）——题目入库违反"只要知识点"的底线。
判定阶段机器已挡过一轮，但判定表是**代理改写后的**最终内容，
入包前再用 `kb_build.audit_material_examples` 的**同一组正则**机械复核，
命中即出清单（按 node_slug 汇总）。

字段映射与 audit_material_examples.classify 一致：
title → title；contentMarkdown/summaryMarkdown/applicabilityMarkdown
← content/summary/applicability。只查 action=MATERIAL 行；不改判定表、
不改 materialize 逻辑。

## 误报豁免（tables/material_exam_exemptions.csv，2026-10-11 起）

4 条正则是有意放宽的机械判据（宁可多报），实测存在成批误报：方法材料里的
"得出答案/标准答案"、标题里的"真题命题背景"出处标注、适用性字段以"求…时使用"
收束等。逐条人工复核为误报的行进豁免表——**逐行**记键（chunk_rel+chunk_id+midx，
即材料身份）与**四字段内容指纹**（sha256[:16]）：文本一旦被改写，指纹变化，
豁免自动失效、该行重新变红（改写后的文本必须重新复核）。理由写在 reason 列。

退出码：存在**未豁免**命中 exit 1；命中全部已豁免 exit 0（残余=未豁免命中，
全量逐条打印）。豁免表里键已不再命中/指纹已变的行按"陈旧"列出，不判红——
它说明豁免可以在收口一轮后清理，但不会放过任何新命中。

用法：PYTHONPATH=tools python -m kb_coverage.check_exam_leak
"""

from __future__ import annotations

import csv
import hashlib
import sys
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import audit_material_examples as ame  # noqa: E402

JUDGMENTS = REPO / "tools/kb_coverage/tables/material_judgments.csv"
EXEMPTIONS = REPO / "tools/kb_coverage/tables/material_exam_exemptions.csv"

# 指纹覆盖 classify 实际读到的全部字段（title/content/summary/applicability）。
_FP_FIELDS = ("title", "content", "summary", "applicability")


def fields_fp(material: dict) -> str:
    """四字段内容指纹（sha256 截断 16 位；非安全用途，只用于让豁免随改写失效）。"""
    blob = "\x1f".join(material.get(k) or "" for k in _FP_FIELDS)
    return hashlib.sha256(blob.encode("utf-8")).hexdigest()[:16]


def check_rows(rows: list[dict]) -> list[dict]:
    """对 MATERIAL 行跑 4 条判据，返回命中清单（含材料键与内容指纹）。"""
    hits: list[dict] = []
    for r in rows:
        if (r.get("action") or "").strip() != "MATERIAL":
            continue
        fake = {
            "title": r.get("title") or "",
            "contentMarkdown": r.get("content") or "",
            "summaryMarkdown": r.get("summary") or "",
            "applicabilityMarkdown": r.get("applicability") or "",
        }
        is_example, why = ame.classify(fake)
        if is_example:
            hits.append({
                "node_slug": (r.get("node_slug") or "").strip() or "(空)",
                "chunk_rel": r.get("chunk_rel") or "",
                "chunk_id": r.get("chunk_id") or "",
                "midx": (r.get("midx") or "").strip(),
                "reason": why,
                "fields_fp": fields_fp({
                    "title": fake["title"], "content": fake["contentMarkdown"],
                    "summary": fake["summaryMarkdown"],
                    "applicability": fake["applicabilityMarkdown"],
                }),
            })
    return hits


def load_exemptions(path: Path | None = None) -> dict[tuple[str, str, str], dict]:
    """读豁免表；键=(chunk_rel, chunk_id, midx)。缺文件=无豁免；重复键=坏表报错。"""
    path = path or EXEMPTIONS
    if not path.exists():
        return {}
    with path.open(encoding="utf-8-sig", newline="") as fh:
        rows = list(csv.DictReader(fh))
    out: dict[tuple[str, str, str], dict] = {}
    for r in rows:
        key = (r.get("chunk_rel") or "", r.get("chunk_id") or "",
               (r.get("midx") or "").strip())
        if key in out:
            raise ValueError(f"豁免表重复键：{key}（{path}）")
        if not (r.get("reason") or "").strip() or not (r.get("fields_fp") or "").strip():
            raise ValueError(f"豁免表缺 reason/fields_fp：{key}（{path}）")
        out[key] = r
    return out


def split_hits(hits: list[dict], exemptions: dict) -> tuple[list[dict], list[dict], list[dict]]:
    """按（键 + 内容指纹）切分：未豁免命中 / 已豁免命中 / 陈旧豁免。

    指纹参与判定的意义：豁免只覆盖**复核过的那一版文本**；文本改写后
    指纹不再相等 → 该行重新算命中、豁免行转陈旧，不给"改写后继续被旧豁免罩住"留口子。
    """
    violations: list[dict] = []
    excused: list[dict] = []
    used: set[tuple[str, str, str]] = set()
    for h in hits:
        key = (h["chunk_rel"], h["chunk_id"], h["midx"])
        ex = exemptions.get(key)
        if ex is not None and (ex.get("fields_fp") or "") == (h.get("fields_fp") or ""):
            excused.append(h)
            used.add(key)
        else:
            violations.append(h)
    stale = [ex for key, ex in exemptions.items() if key not in used]
    return violations, excused, stale


def main(argv: list[str] | None = None) -> int:
    with JUDGMENTS.open(encoding="utf-8-sig", newline="") as fh:
        rows = list(csv.DictReader(fh))
    material = [r for r in rows if (r.get("action") or "").strip() == "MATERIAL"]
    hits = check_rows(rows)
    exemptions = load_exemptions()
    violations, excused, stale = split_hits(hits, exemptions)

    by_slug: dict[str, list[dict]] = defaultdict(list)
    for h in violations:
        by_slug[h["node_slug"]].append(h)
    reasons = Counter(h["reason"].split("：")[0] for h in hits)
    print(f"MATERIAL 行 {len(material)}；例题派生命中 {len(hits)}"
          f"（涉及 node_slug {len({h['node_slug'] for h in hits})} 个；"
          f"已豁免 {len(excused)}、待处理 {len(violations)}）")
    if stale:
        print(f"豁免表陈旧 {len(stale)} 条（键已不再命中或文本指纹已变；不判红，宜清理）：")
        for ex in stale[:20]:
            print(f"   ! {ex.get('chunk_id')}{ex.get('midx', '')} {ex.get('material_slug', '')}")
        if len(stale) > 20:
            print(f"   … 其余 {len(stale) - 20} 条见表")
    if not hits:
        print("无命中")
        return 0
    print("按判据（含已豁免）：")
    for why, n in reasons.most_common():
        print(f"   {n:>5}  {why}")
    if not violations:
        print()
        print(f"全部命中已逐条豁免（{len(excused)} 条，明细与理由见 "
              f"tables/material_exam_exemptions.csv）")
        return 0
    print()
    print("按 node_slug（待处理，命中行数 | slug）：")
    for slug in sorted(by_slug, key=lambda s: (-len(by_slug[s]), s)):
        print(f"   {len(by_slug[slug]):>5}  {slug}")
    print()
    print(f"残余（未豁免，共 {len(violations)} 条；逐条列出）：")
    for h in violations:
        print(f"   [{h['reason'][:26]}] {h['chunk_rel'][:44]} {h['chunk_id']}{h['midx']}"
              f" → {h['node_slug'][:30]}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
