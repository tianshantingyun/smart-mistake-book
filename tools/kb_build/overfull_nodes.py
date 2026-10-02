# -*- coding: utf-8 -*-
"""超配节点清单：一个节点挂了多于 LIMIT(=4) 条材料——这是**分诊名单**，不是"死绑定"清单。

**运行时的真实机制**（2026-10-02 按现行代码实读；旧「每节点 ≤4 条」条数门已废）：
- 取数上界 `MAX_TEACHING_MATERIAL_CANDIDATES = 1,024` 是**整批**材料的行数上界，不是展示上限；
- 展示只由全局字符预算 `MAX_TEACHING_REFERENCE_MARKDOWN_CHARS = 20,000` 决定：按
  角色秩 → 重教类型优先级 → title → material_id 排序后逐条装填，装不下的跳过、继续看下一条
  （`TutorTeachingReferenceSelector.select`，`RoomTutorTeachingReferenceRepository.kt`）；
- 所以一个节点堆 139 条**不等于**「第 5 条起永远看不见」：它们仍按序参与预算竞争，
  只是排序靠后的材料在预算耗尽时被跳过，而排序（同类型内只按 title/material_id）与质量无关。
- 「>4 条」在本工具里是**节点粒度需要复核**的分诊口径（该拆点 / 该合并 / 该改绑），
  不是「这些绑定已经失效」；处置口径见 docs/kb-outstanding-research-2026-10-02.md ①-3。

**现状（2026-10-02 实测）**：
- 知识点 3,866 个，全部至少 1 条绑定（零材料 0 个）；材料 50,383 条，各恰好 1 条绑定；
- 节点内 >4 条：**2,518 个**，挂着 **47,538** 条材料，其中节点内排序第 5 位及以后的绑定 **37,466 条**；
  另有 ≥4 条 2,754 个（含 =4 条 236 个）；单节点最大 139 条 = BIOLOGY「基因工程」。
- 计数口径：按 (subject, slug) 聚合（包内有 9 个跨科同名 slug，按 slug 单键聚合会得到不同数字）；
  每条材料只计其唯一一条绑定。

**复算**（逐条可重跑）：
- 超配：本工具。材料卷枚举走 `pack_io.sidecar_paths()`——按卷索引的清单取卷；不要用文件系统
  glob：`moe-2025-teaching-support-v2-*.json` 会命中卷索引
  `moe-2025-teaching-support-v2-index.json`（只有 `packId`/`sidecars` 两个键）而 `KeyError: 'materials'`。
- 零材料 / 薄料：`PYTHONPATH=tools python -m kb_build.report_material_gaps`。

本工具产出施工清单：逐节点按**运行时真实排序**（角色秩 → 重教类型优先级 → title → material_id）
列出全部绑定，标出节点内前 4 条与第 5 位及以后的材料，供逐条裁决（留 / 改绑 / 解绑）。

  用法： PYTHONPATH=tools python -m kb_build.overfull_nodes            # 报告
        PYTHONPATH=tools python -m kb_build.overfull_nodes --write    # 写 CSV
"""

from __future__ import annotations

import argparse
import csv
import re
from collections import Counter, defaultdict

from kb_build import pack_io

OUT_NAME = "overfull_nodes.csv"
LIMIT = 4

_PUNCT = re.compile(r"[\s，。、；：（）()［］\[\]【】“”\"'·—\-_/\\|!?？！,.:;]+")


def norm(text: str) -> str:
    return _PUNCT.sub("", text or "")


def bigrams(text: str) -> set[str]:
    """2-gram 集合。**只用来做分诊，不用来做判定**——本仓已实测：零重合里约 54% 本来就是对的。"""
    t = norm(text)
    return {t[i:i + 2] for i in range(len(t) - 1)} or {t}

# 与 KnowledgeTeachingMaterialDao 的 CASE 逐值一致（权威表达在
# TutorTeachingReferenceSelector.reTeachPriority，两处漂移会让"预算给了谁"变成未定义）。
TYPE_PRIORITY = {
    "MISCONCEPTION_GUIDE": 0,
    "WORKED_EXAMPLE": 1,
    "METHOD_MODEL": 2,
    "DERIVATION": 3,
    "CONCEPT_EXPLANATION": 4,
    "REPRESENTATION_GUIDE": 5,
    "COMPLETE_SOLUTION": 6,
}
ROLE_RANK = {"PRIMARY": 0, "SUPPORTING": 1}


def load():
    """读包（节点名单）与全部材料卷的绑定。

    工作目录 = `pack_io.work_dir()`（默认 staging；缺包时从成品目录种子）。
    材料卷清单以 `pack_io.sidecar_paths()` 为准（读卷索引；Kotlin loader 读同一份索引）——
    **不要退化成文件系统 glob**：`moe-2025-teaching-support-v2-*.json` 会命中卷索引本身
    （只有 packId/sidecars 两个键），把索引当材料卷读入即 `KeyError: 'materials'`。
    """
    pack = pack_io.load_json(pack_io.pack_path())
    by_node: dict[str, list[dict]] = defaultdict(list)
    for path in pack_io.sidecar_paths():
        for material in pack_io.load_json(path)["materials"]:
            for binding in material.get("bindings") or []:
                by_node[binding["knowledgeNodeId"]].append({
                    "materialId": material["slug"],
                    "title": material["title"],
                    "type": material["type"],
                    "role": binding.get("role", "PRIMARY"),
                    "chars": sum(len(material.get(f) or "") for f in
                                 ("summaryMarkdown", "applicabilityMarkdown",
                                  "contentMarkdown", "boundaryMarkdown")),
                })
    names: dict[str, tuple[str, str, str]] = {}
    for subject in pack["subjects"]:
        name = subject["subject"]
        by_slug = {t["slug"]: t for t in subject["topics"]}
        for topic in subject["topics"]:
            chain, cursor = [], topic
            while cursor is not None:
                chain.append(cursor["name"])
                parent = cursor.get("parentSlug")
                cursor = by_slug.get(parent) if parent else None
            chain.reverse()
            chapter = chain[1] if len(chain) > 1 else ""
            for point in topic.get("knowledgePoints") or []:
                node_id = f"kb:{pack['packId']}:{name.lower()}:atomic:{point['slug']}"
                names[node_id] = (name, chapter, point["name"])
    return by_node, names


def sort_key(item: dict):
    return (ROLE_RANK.get(item["role"], 2), TYPE_PRIORITY.get(item["type"], 7),
            item["title"], item["materialId"])


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--sample", type=int, default=6)
    parser.add_argument("--top", type=int, default=0, help="只看最严重的 N 个节点")
    args = parser.parse_args(argv)

    by_node, names = load()
    overfull = []
    for node_id, items in by_node.items():
        if len(items) > LIMIT:
            info = names.get(node_id, ("?", "?", node_id))
            items = sorted(items, key=sort_key)
            for item in items:
                # 只做分诊：零 2-gram 重合 = 机械上可判为"疑似错绑"，但**不能据此直接解绑**
                # ——本仓实测零重合里约 54% 本来就是对的（正确但表述不同）。
                item["weak"] = not (bigrams(item["title"]) & bigrams(info[2]))
            overfull.append((len(items), node_id, info, items))
    overfull.sort(key=lambda row: (-row[0], row[1]))

    total_materials = sum(len(v) for v in by_node.values())
    excess = sum(count - LIMIT for count, _i, _n, _s in overfull)
    held = sum(count for count, *_ in overfull)
    n_ge = sum(1 for items in by_node.values() if len(items) >= LIMIT)
    n_eq = sum(1 for items in by_node.values() if len(items) == LIMIT)
    weak_total = sum(1 for _c, _i, _n, items in overfull for it in items if it["weak"])
    weak_excess = sum(1 for _c, _i, _n, items in overfull
                      for it in items[LIMIT:] if it["weak"])
    print(f"{len(overfull):,} 个超配节点（同一节点 > {LIMIT} 条材料）；它们挂着 {held:,} 条材料，"
          f"其中超出 {excess:,} 条是节点内排序第 {LIMIT + 1} 位及以后的绑定。")
    print(f"运行时没有每节点条数门：整批取数上界 1,024 条、展示由 20,000 字符预算按排序逐条装填"
          f"（装不下才跳过）——上列绑定不是「永久不可见」，只是排序靠后在预算争用中吃亏。")
    print(f"全部有绑定材料 {total_materials:,} 条，落在 {len(by_node):,} 个节点上"
          f"——超配节点占 {held * 100 // max(total_materials, 1)}% 的材料；"
          f"节点内 ≥{LIMIT} 条共 {n_ge:,} 个（其中 ={LIMIT} 条 {n_eq:,} 个）。")
    print(f"其中与节点名零 2-gram 重合（疑似错绑；只做分诊不做判定）：{weak_total} 条；"
          f"这当中排在超配段（第 {LIMIT + 1} 位起）的 {weak_excess} 条是优先复核对象。")
    print()
    print(f"—— 最严重的 {min(args.sample, len(overfull))} 个（先 = 节点内前 {LIMIT} 条；"
          f"后 = 第 {LIMIT + 1} 位起，按序竞争预算；疑 = 疑似错绑）——")
    rows = overfull[:args.top or args.sample]
    for count, node_id, (subject, chapter, name), items in rows:
        print(f"■ {count} 条  [{subject}] {chapter} / {name}")
        for index, item in enumerate(items):
            mark = "先" if index < LIMIT else "后"
            flag = "疑" if item["weak"] else "  "
            print(f"    {mark}{flag}  [{TYPE_PRIORITY.get(item['type'], 7)}] {item['type'][:20]:<21}"
                  f" {item['chars']:>6} 字  {item['title'][:44]}")
        print()

    if args.write:
        path = pack_io.work_dir() / OUT_NAME
        with path.open("w", encoding="utf-8", newline="") as fh:
            writer = csv.writer(fh)
            writer.writerow(["node_id", "subject", "chapter", "node_name", "material_count",
                             "verdict_keep_or_move", "rank", "role", "type", "title",
                             "material_slug", "chars", "weak_match"])
            for count, node_id, (subject, chapter, name), items in overfull:
                for index, item in enumerate(items):
                    writer.writerow([node_id, subject, chapter, name, count, "", index + 1,
                                     item["role"], item["type"], item["title"],
                                     item["materialId"], item["chars"],
                                     "yes" if item["weak"] else ""])
        print(f"已写出 {path.relative_to(pack_io.REPO)}（{sum(c for c, *_ in overfull)} 行，"
              f"verdict 列留空待裁决）")

    by_chapter = Counter()
    for count, _node_id, (subject, chapter, _name), _items in overfull:
        by_chapter[f"{subject} {chapter}"] += count
    print()
    print("按章（前 10）：")
    for chapter, count in by_chapter.most_common(10):
        print(f"  {count:>5} 条  {chapter}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
