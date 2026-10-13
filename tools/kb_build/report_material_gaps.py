# -*- coding: utf-8 -*-
"""缺料登记册：哪些知识点没有材料、哪些只有一条（薄弱），一次算清、落表可追踪。

## 它消灭的失败

"还缺哪些节点的材料"过去只能靠跑门禁时看 `unbound_points` 一个数字（它只报零材料），
而"只有 1 条材料"的薄弱节点（实测 814 个）从来不可见 —— 补料没有依据，下一轮也无从对照。
本模块把两级缺口一次算清并落成**可复算的表**（每次入库后重跑即可对比增减）：

- `zero`：零材料节点（必须补；块池里没有干净来源时如实标注"待补源"）
- `thin`：只有 1 条材料的节点（薄弱，用于后续加厚）

表里带 `subject / slug / name / topic / material_count / tier / note`，`note` 记录补料线索
（例如"块池只有真题碎片"）。

用法：
    PYTHONPATH=tools python -m kb_build.report_material_gaps            # 只打印统计
    PYTHONPATH=tools python -m kb_build.report_material_gaps --write    # 落表 + 打印
"""

from __future__ import annotations

import argparse
import csv
from collections import Counter, defaultdict
from pathlib import Path

from kb_build import pack_io

OUT = Path(pack_io.REPO) / "knowledge-production" / "node-material-gaps-2026-09.csv"
FIELDS = ("subject", "slug", "name", "topic", "material_count", "tier", "note")

# 零材料节点的补料线索（人工维护：说明为什么缺、去哪里找）
KNOWN_GAPS = {
    ("MATH", "由线-面关系误解向量关系"):
        "块池里该主题只有真题碎片，无干净知识块；待从《空间向量》讲义/教材正文补一条材料",
    ("CHEMISTRY", "自然资源的开发利用"):
        "化学必修第二册第八章，块池无对应知识块（只有真题）；待从该章讲义或教材正文补一条材料",
    # 批9 C2 收口（2026-10-13）：超配改绑/建点后零材料的 14 个节点，逐条登记待补料或待并裁。
    # 原材料去向与理由见 build/agent-batch9/d2/overfull/_c2exec/ 的复扫与处置记录。
    ("MATH", "周期数列的周期确认与求和要点"):
        "批9 超配改绑后零材料（11 条→周期数列的常见形式）；待补一条本点汇总材料，或与「周期数列的常见形式」并裁",
    ("PHYSICS", "实验步骤"):
        "栏目式泛名（内容是油膜法步骤），原材料 7 条分属各实验点；待裁：rename/并入「用油膜法估测油酸分子的大小」或撤回",
    ("PHYSICS", "注意事项-x1"):
        "批9 超配改绑后零材料（10 条→各热学/光学实验点）；待补一条热学实验共性注意事项材料（本点名为「热学实验的注意事项」）",
    ("PHYSICS", "光学实验中的误差分析"):
        "批9 超配改绑后零材料（7 条→测量玻璃折射率/双缝干涉实验点）；待补一条跨光学实验的误差分析材料",
    ("PHYSICS", "必备实验仪器的原理及使用"):
        "批9 超配改绑后零材料（23 条→游标卡尺/螺旋测微器/欧姆表等专点）；待补仪器总览材料或并入仪器簇",
    ("CHEMISTRY", "焰色反应"):
        "建点后无材料（与「焰色试验-易错点」同题分流）；待补焰色原理与常见元素焰色记忆材料",
    ("CHEMISTRY", "位-构-性-关系的应用"):
        "批9 超配改绑后零材料（15 条→元素的-位-构-性-关系/元素推断的常用方法）；待与两节点对齐后补料或并裁",
    ("CHEMISTRY", "常见的装置"):
        "批9 超配改绑后零材料（7 条→安全辅助装置/常见仪器/防倒吸装置）；名泛（内容是喷泉装置变式），待 rename+补料",
    ("CHEMISTRY", "v2o5常用作so2转化为so3的催化剂"):
        "题面残句式命名（真知识点：SO2/SO3 分子结构与杂化）；5 条已改绑散落；待 rename+补一条结构材料",
    ("CHEMISTRY", "同系物-同分异构体的概念和判断易混易错点"):
        "批9 超配改绑后零材料（15 条→同分异构现象/同系物/五同概念辨析）；待补同系物-同分异构体易混辨析材料",
    ("CHEMISTRY", "蛋白质和氨基酸的结构与性质"):
        "批9 超配改绑后零材料（12 条→氨基酸的结构与性质/蛋白质的结构与性质）；待补成肽规律汇总材料",
    ("BIOLOGY", "细胞的分化与全能性"):
        "批9 超配改绑后零材料（35 条→细胞分化/细胞的全能性/干细胞）；待补汇总材料或并入两专点",
    ("BIOLOGY", "基因突变与细胞癌变"):
        "批9 超配改绑后零材料（35 条→细胞癌变/基因突变）；待补汇总材料或并入两专点",
    ("BIOLOGY", "克隆技术与伦理问题"):
        "建点后无材料（与「试管婴儿与设计试管婴儿的区别」同域）；待补克隆技术与伦理争议材料",
}


def counts() -> dict[tuple[str, str], int]:
    out: dict[tuple[str, str], int] = defaultdict(int)
    for sp in pack_io.sidecar_paths():
        for m in pack_io.load_json(sp)["materials"]:
            for b in m.get("bindings") or []:
                parts = b["knowledgeNodeId"].split(":")
                out[(parts[-3].upper(), parts[-1])] += 1
    return out


def rows() -> list[dict]:
    pack = pack_io.load_json(pack_io.pack_path())
    cnt = counts()
    out: list[dict] = []
    for subject in pack["subjects"]:
        for topic in subject["topics"]:
            for point in topic.get("knowledgePoints") or []:
                n = cnt.get((subject["subject"], point["slug"]), 0)
                if n > 1:
                    continue
                tier = "zero" if n == 0 else "thin"
                out.append({
                    "subject": subject["subject"],
                    "slug": point["slug"],
                    "name": point["name"],
                    "topic": topic["slug"],
                    "material_count": n,
                    "tier": tier,
                    "note": KNOWN_GAPS.get((subject["subject"], point["slug"]), ""),
                })
    out.sort(key=lambda r: (r["tier"] != "zero", r["subject"], r["slug"]))
    return out


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--out", type=Path, default=OUT,
                    help="台账落盘路径（默认沿用 2026-09 台账；新一期台账用 --out 指定，"
                         "旧台账不动）")
    args = ap.parse_args(argv)
    data = rows()
    by_tier = Counter(r["tier"] for r in data)
    by_subject = Counter((r["tier"], r["subject"]) for r in data)
    print(f"缺口共 {len(data)} 个：零材料 {by_tier['zero']}、仅 1 条材料 {by_tier['thin']}")
    for (tier, subject), n in sorted(by_subject.items()):
        print(f"   {tier:<5} {subject:<10} {n}")
    print("零材料清单：")
    for r in [r for r in data if r["tier"] == "zero"]:
        print(f"   [{r['subject']}] {r['name']}（{r['topic'][:32]}）— {r['note'][:60] or '待判定'}")
    if args.write:
        target: Path = args.out
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("w", encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=FIELDS)
            w.writeheader()
            w.writerows(data)
        print(f"→ 已写 {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
