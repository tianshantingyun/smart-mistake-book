# -*- coding: utf-8 -*-
"""候选区"量体"：把 16 万块分成【需模型判定】与【机械可判 SKIP】两类，报比例与按来源分布。

## 它消灭的失败

封包轮要处理的是**全部未判定块**（实测 171,461 键 / 块池 186,635 行）。若不分流就整批送模型：
- 大量块本来就**不该成为材料**（题目/答案/目录/封面/版权/纯页码），它们占了相当比例，
  让模型逐条判"这是不是知识点"是纯浪费，而且**题目一旦被模型改写成"知识点"就会违约入库**
  （用户底线：只要知识点不要题目）；
- 真需要模型做的是"把成体系的知识改写成材料 + 绑到知识点"，这必须是**少数块**。

所以本工具先按**机械判据**分流，产出可复核的比例与按来源分布——后面按这个数排工。

## 判据（四类"机械可判 SKIP" + 一类"需模型"）

1. **题目/答案/解析派生**：复用 `kb_build.audit_material_examples` 的四条客观判据
   （标题是题号/考试来源、成套 A/B/C/D、含答案/故选/解析语、短题干无结论）；
2. **非知识形态**：目录（点线+页码成行）、封面/版权/出版信息（ISBN、出版社、仅供…使用）、
   广告宣传语、纯页眉页脚/页码、空白与占位（如"本页无正文"）；
3. **过短**：去空白后 < 40 字（不足以承载一条材料）；
4. **重复内容**：同一内容指纹（fp）在池中出现多次 → 只留首次出现的那一条；
5. 其余 = **需模型判定**。

注意：本工具**只报告**，不动块池——`--json <path>` 把机器可读汇总落一份 JSON
（工作流读它决定 fan-out 规模）。没有 `--write` 开关。

用法：
    PYTHONPATH=tools python -m kb_coverage.prescreen_chunks
    PYTHONPATH=tools python -m kb_coverage.prescreen_chunks --json build/prescreen.json
"""

from __future__ import annotations

import argparse
import collections
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import audit_material_examples as ame  # noqa: E402
from kb_coverage.pool_path import POOL_PATH  # noqa: E402

CHUNKS = POOL_PATH
JUDGMENTS = REPO / "tools/kb_coverage/tables/material_judgments.csv"

MIN_CHARS = 40
# 非知识形态（确定性形态，不是内容判断）
_TOC_LINE = re.compile(r"(?:…{2,}|\.{3,})\s*\d{1,4}\s*$", re.M)
_COPYRIGHT = re.compile(r"ISBN|版权所有|出版发行|印张|字数|定价|书号|CIP|社址|邮编|开本|责任编辑")
_AD = re.compile(r"扫码|关注公众号|限时|优惠|赠送|免费领取|购买链接|天猫|京东|店铺")
_NAV = re.compile(r"^(?:第?\s*\d{1,4}\s*页|[•·\-—]?\s*\d{1,4}\s*[•·\-—]?)$")
_EMPTY = re.compile(r"本页无正文|此页空白|空白页|略\s*$")


def classify_chunk(heading: str, text: str) -> tuple[str, str]:
    """返回 ("SKIP"|"JUDGE", 理由)。判据顺序：形态 → 题目 → 过短。"""
    body = f"{heading}\n{text or ''}".strip()
    stripped = re.sub(r"\s+", "", body)
    if not stripped:
        return "SKIP", "空块"
    if _EMPTY.search(body):
        return "SKIP", "空白/占位页"
    if _COPYRIGHT.search(body) and len(stripped) < 400:
        return "SKIP", "版权/出版信息"
    if _AD.search(body):
        return "SKIP", "广告宣传"
    if _NAV.match(body.strip()):
        return "SKIP", "纯页码/导航行"
    # 目录：多数行都是"条目……页码"
    lines = [l for l in body.split("\n") if l.strip()]
    if lines and len(lines) >= 3:
        toc = len([l for l in lines if _TOC_LINE.search(l)])
        if toc >= max(3, int(len(lines) * 0.6)):
            return "SKIP", "目录（点线+页码）"
    # 题目派生：复用既有四条判据（字段名映射：heading→title，text→contentMarkdown）
    fake = {"title": heading or "", "contentMarkdown": text or ""}
    is_example, why = ame.classify(fake)
    if is_example:
        return "SKIP", f"题目派生：{why.split('：')[0]}"
    if len(stripped) < MIN_CHARS:
        return "SKIP", f"过短（{len(stripped)} 字 < {MIN_CHARS}）"
    return "JUDGE", ""


def load_judged_keys() -> set[tuple[str, str]]:
    import csv

    keys: set[tuple[str, str]] = set()
    if not JUDGMENTS.exists():
        return keys
    with JUDGMENTS.open(encoding="utf-8-sig", newline="") as fh:
        for r in csv.DictReader(fh):
            keys.add(((r.get("chunk_rel") or "").strip(), (r.get("chunk_id") or "").strip()))
    return keys


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--json", type=Path, help="把机器可读汇总写到这个路径")
    args = ap.parse_args(argv)

    judged = load_judged_keys()
    seen_fp: set[str] = set()
    seen_key: set[tuple[str, str]] = set()
    verdicts = collections.Counter()
    reasons = collections.Counter()
    by_source: dict[str, collections.Counter] = {}
    samples: dict[str, list[str]] = collections.defaultdict(list)
    rows = unjudged = dup_rows = 0

    with CHUNKS.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            rows += 1
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue
            key = (rec.get("rel_path") or "", rec.get("chunk_id") or "")
            if key in judged:
                continue
            unjudged += 1
            src = (rec.get("rel_path") or "?").split("/")[0]
            fp = rec.get("fp") or ""
            if fp and fp in seen_fp:
                verdict, why = "SKIP-重复", "同内容指纹重复（只留首次）"
                dup_rows += 1
            else:
                seen_fp.add(fp)
                if key in seen_key:
                    verdict, why = "SKIP-重复键", "同 (源,chunk_id) 重复行"
                else:
                    seen_key.add(key)
                    verdict, why = classify_chunk(rec.get("heading") or "", rec.get("text") or "")
            verdicts[verdict] += 1
            if why:
                reasons[why] += 1
            by_source.setdefault(src, collections.Counter())[verdict] += 1
            if why and len(samples[why]) < 2:
                samples[why].append(f"{src} | {(rec.get('heading') or '')[:30]} | {(rec.get('text') or '')[:60]}")

    judge = verdicts.get("JUDGE", 0)
    print(f"块池 {rows} 行；已判定跳过；**未判定 {unjudged} 块**")
    print(f"  → 需模型判定 JUDGE：**{judge}**（{judge / max(unjudged, 1):.1%}）")
    print(f"  → 机械可判 SKIP：{unjudged - judge}（{1 - judge / max(unjudged, 1):.1%}）")
    print("\nSKIP 理由：")
    for why, n in reasons.most_common():
        print(f"   {n:>7}  {why}")
    print("\n按来源（前 12，JUDGE / SKIP）：")
    for src, c in sorted(by_source.items(), key=lambda kv: -sum(kv[1].values()))[:12]:
        tot = sum(c.values())
        print(f"   {src[:34]:<34s} {tot:>7}  JUDGE {c.get('JUDGE', 0):>7}  SKIP {tot - c.get('JUDGE', 0):>7}")
    print("\n样例：")
    for why, ss in list(samples.items())[:8]:
        for s in ss[:1]:
            print(f"   [{why[:26]}] {s[:96]}")

    if args.json:
        payload = {
            "rows": rows, "unjudged": unjudged, "judge": judge, "skip": unjudged - judge,
            "dup_rows": dup_rows,
            "reasons": dict(reasons.most_common()),
            "by_source": {k: dict(v) for k, v in by_source.items()},
        }
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"\n→ 已写 {args.json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
