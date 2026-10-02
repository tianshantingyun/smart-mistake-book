# -*- coding: utf-8 -*-
"""扫"近重复节点"候选：名字规范化后相同/包含的同科节点。

用途：错绑审计反复暴露同一类问题——**同一个知识点在库里有两个节点**（只差用字/标点/后缀），
材料在两者之间来回搬。本扫描把它们成组列出，供下一轮逐组裁定合并。

判据（机械，只做分组不做结论）：
- 规范化：去掉标点/括号内容、去掉末尾的「问题 / 的方法 / 的思路」空后缀、统一「与/及」为「和」；
- 同科内规范化名相同 → 记为 `same`；一方是另一方的前缀（短名 ≥5 字）→ 记为 `prefix`；
- 前缀对只收"同一主题内 + 短的那侧材料 ≤2 条"的形态（遗留空壳），合法细分（两侧都有料）不进。

2026-10-02 本机实测基线：规范化完全同名 **11 组**、前缀包含 **18 对**（测试钉住；口径见模块
底部 `scan()`）。数量随内容变化时先复算、再更新测试与文档，不要删断言。

  用法： PYTHONPATH=tools python -m kb_build.scan_duplicate_nodes                # 报告
        PYTHONPATH=tools python -m kb_build.scan_duplicate_nodes --out X.csv    # 落 CSV
        PYTHONPATH=tools python -m kb_build.scan_duplicate_nodes --json         # JSON 到 stdout
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from kb_build import pack_io  # noqa: E402

DROP_PAREN = re.compile(r"[（(][^（）()]*[）)]")
DROP_PUNCT = re.compile(r"[·、，,．.：:；;・\-—_/\\\[\]【】「」“”\"'’‘!！?？]")
# 只剥**空后缀**（不含信息量的尾巴）。刻意不含「方法/计算/比较/应用/规律」这类——
# 它们是知识点之间真正的区别（「化学反应速率的计算方法」≠「化学反应速率的比较」），
# 剥掉会把两个合法节点并成一组（第一版扫描实测踩过这个坑：32 组里混进一批合法细分）。
SUFFIXES = ("问题", "的方法", "的思路")
UNIFY = {"与": "和", "及": "和"}

CSV_COLUMNS = ["kind", "subject", "canonical", "other_canonical", "side",
               "node_slug", "node_name", "topic", "material_count"]


def norm(name: str) -> str:
    s = DROP_PAREN.sub("", name)
    s = DROP_PUNCT.sub("", s)
    for k, v in UNIFY.items():
        s = s.replace(k, v)
    for suf in SUFFIXES:
        if len(s) > len(suf) + 3 and s.endswith(suf):
            s = s[: -len(suf)]
    return s


def nodes_of(pack: dict) -> dict[tuple[str, str], tuple[str, str]]:
    """(subject, slug) -> (name, topic slug)。"""
    nodes: dict[tuple[str, str], tuple[str, str]] = {}
    for subject in pack["subjects"]:
        for topic in subject["topics"]:
            for point in topic.get("knowledgePoints") or []:
                nodes[(subject["subject"], point["slug"])] = (point["name"], topic["slug"])
    return nodes


def material_counts() -> dict[tuple[str, str], int]:
    """(subject, slug) -> 材料条数。

    每条材料只计**首个绑定**（包内材料恰好各 1 条绑定；多绑定材料按"首个"口径）。
    键控用 (subject, slug)：包内有 9 个跨科同名 slug，按 slug 单键会把别科的条数
    算进本节点（进而污染"短的那侧 ≤2 条"的筛选）。
    """
    counts: dict[tuple[str, str], int] = defaultdict(int)
    for path in pack_io.sidecar_paths():
        for material in pack_io.load_json(path)["materials"]:
            for binding in material.get("bindings") or []:
                parts = binding["knowledgeNodeId"].split(":")   # kb:<pack>:<subject>:atomic:<slug>
                counts[(parts[-3].upper(), parts[-1])] += 1
                break
    return counts


def group_nodes(nodes: dict, counts: dict) -> dict[tuple[str, str], list[dict]]:
    """(subject, 规范化名) -> [{slug, name, topic, material_count}, ...]。"""
    groups: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for (subject, slug), (name, topic) in nodes.items():
        groups[(subject, norm(name))].append({
            "slug": slug, "name": name, "topic": topic,
            "material_count": counts.get((subject, slug), 0),
        })
    return groups


def same_groups(groups: dict) -> dict[tuple[str, str], list[dict]]:
    return {key: items for key, items in groups.items() if len(items) > 1}


def prefix_pairs(groups: dict) -> list[dict]:
    pairs = []
    for (subject, key) in groups:
        for (other_subject, other_key) in groups:
            if other_subject != subject or other_key == key:
                continue
            if len(key) >= 5 and other_key.startswith(key) and len(other_key) > len(key):
                short, long = groups[(subject, key)], groups[(other_subject, other_key)]
                # 只收"同一主题内 + 短的那侧材料 ≤2 条"的形态：那才是遗留空壳，不是合法细分
                if (short[0]["topic"].split("·")[-1] == long[0]["topic"].split("·")[-1]
                        and min(item["material_count"] for item in short) <= 2):
                    pairs.append({"subject": subject, "key": key, "other_key": other_key,
                                  "short": short, "long": long})
    pairs.sort(key=lambda row: (row["subject"], row["key"], row["other_key"]))
    return pairs


def scan() -> dict:
    """返回 {"same": [...], "prefix_pairs": [...]}（顺序稳定，便于 diff/复算）。

    读数来自 `pack_io.work_dir()`（默认 staging；缺包时自动从成品目录种子）。
    """
    pack = pack_io.load_json(pack_io.pack_path())
    groups = group_nodes(nodes_of(pack), material_counts())
    same = same_groups(groups)
    return {
        "same": [{"subject": key[0], "key": key[1], "nodes": items}
                 for key, items in sorted(same.items(), key=lambda kv: (-len(kv[1]), kv[0]))],
        "prefix_pairs": prefix_pairs(groups),
    }


def print_report(result: dict) -> None:
    same, pairs = result["same"], result["prefix_pairs"]
    print(f"规范化完全同名组：{len(same)} 组")
    for group in same[:12]:
        print(f"  [{group['subject']}] 规范化「{group['key']}」")
        for node in group["nodes"]:
            print(f"      {node['name']}（材料 {node['material_count']}）｜topic={node['topic']}")
    print(f"\n前缀包含对：{len(pairs)} 对（前 8）")
    for pair in pairs[:8]:
        print(f"  [{pair['subject']}] 「{pair['key']}」 ⊂ 「{pair['other_key']}」")
        for node in pair["short"] + pair["long"]:
            print(f"      {node['name']}（材料 {node['material_count']}）")


def write_csv(result: dict, path: Path) -> int:
    """把 same 组与 prefix 对逐节点写成 CSV（同组/同对的行相邻）。返回行数。"""
    rows = []
    for group in result["same"]:
        for node in group["nodes"]:
            rows.append(["same", group["subject"], group["key"], "", "",
                         node["slug"], node["name"], node["topic"], node["material_count"]])
    for pair in result["prefix_pairs"]:
        for node in pair["short"]:
            rows.append(["prefix", pair["subject"], pair["key"], pair["other_key"], "short",
                         node["slug"], node["name"], node["topic"], node["material_count"]])
        for node in pair["long"]:
            rows.append(["prefix", pair["subject"], pair["other_key"], pair["key"], "long",
                         node["slug"], node["name"], node["topic"], node["material_count"]])
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(CSV_COLUMNS)
        writer.writerows(rows)
    return len(rows)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=None,
                        help="把全部候选（same + prefix）写成 CSV 到这个路径")
    parser.add_argument("--json", action="store_true",
                        help="把候选按 JSON 打到 stdout（不打人类可读报告）")
    args = parser.parse_args(argv)

    pack_path = pack_io.pack_path()
    if not pack_path.exists():
        raise SystemExit(
            f"缺少 {pack_path}：work_dir() 已尝试从成品目录种子——检出可能不完整。")
    result = scan()

    rows = None
    if args.out is not None:
        rows = write_csv(result, args.out)          # 先落盘：路径错误在打印之前失败

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=1))
    else:
        print_report(result)
        if args.out is not None:
            print(f"\n已写出 {args.out}（{rows} 行；same {len(result['same'])} 组 / "
                  f"prefix {len(result['prefix_pairs'])} 对）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
