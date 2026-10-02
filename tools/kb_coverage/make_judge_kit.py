# -*- coding: utf-8 -*-
"""判定员工具包：从当前 staging 包生成"可 grep 的节点清单"，供 300 块/片的判定代理使用。

## 它消灭的失败

1. 每个判定代理原本都要自己打开 4MB 的 `build/kb-staging/moe-2025-four-subjects-v1.json`
   并解析 `subjects[].topics[].knowledgePoints[].slug` —— 一次几万 token、还容易抄错 slug
   （抄错就是 `节点不存在`，整行作废）。改成**扁平清单**，代理直接 grep。
2. **薄料账本过期**（验收报告 §4.6 登记）：`knowledge-production/node-material-gaps-2026-09.csv`
   里至少 3 个物理 slug 已被 `point_merge` 合并掉、包内不存在。本工具按**当前包**重算
   `thin_nodes.tsv`，不再让代理照着过期账本找不存在的节点。
3. 节点级 ≤4 条材料的上限（P2 发现：1,954 个节点超限、21,001 条材料进不了预算）——
   `overfull_nodes.tsv` 把已满节点显式列出，代理不再往满节点上继续堆（堆了永远不显示）。

**材料数从侧车算**，不是主包：主包的 `knowledgePoints[].materials` 实测恒为空数组（0 条），
材料全部在 `moe-2025-teaching-support-v2-*.json` 的 `materials[].bindings[].knowledgeNodeId` 里。
照主包数会把 3,572 个节点全算成"零材料/薄料"——那是本工具第一版踩过的坑，故直接复用
`kb_build.report_material_gaps.counts()`（同一个口径，避免两处各算一套）。

## 用法

    python tools/kb_coverage/make_judge_kit.py [--out build/agent-input/judge-kit]
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from kb_build import pack_io  # noqa: E402
from kb_build.report_material_gaps import counts as material_counts  # noqa: E402

REPO = TOOLS.parent
THIN_MAX = 1      # 与 node-material-gaps 口径一致：恰 1 条材料 = thin
FULL_AT = 4       # 每节点仅前 4 条材料可见


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", type=Path, default=REPO / "build/agent-input/judge-kit")
    args = ap.parse_args(argv)
    out: Path = args.out
    out.mkdir(parents=True, exist_ok=True)

    pack = pack_io.load_json(pack_io.pack_path())
    cnt = material_counts()
    thin, full = [], []
    for s in pack["subjects"]:
        subject = s["subject"]
        rows = []
        for t in s["topics"]:
            topic_slug = t.get("slug") or ""
            for kp in t.get("knowledgePoints") or []:
                n = cnt.get((subject, kp["slug"]), 0)
                rows.append((topic_slug, kp["slug"], kp.get("name") or kp["slug"], n))
                if n <= THIN_MAX:
                    thin.append((subject, topic_slug, kp["slug"], kp.get("name") or kp["slug"], n))
                if n >= FULL_AT:
                    full.append((subject, kp["slug"], n))
        rows.sort()
        path = out / f"nodes_{subject}.tsv"
        with path.open("w", encoding="utf-8", newline="\n") as fh:
            fh.write("# topic_slug\tnode_slug\tnode_name\tmaterial_count\n")
            for r in rows:
                fh.write("\t".join([r[0], r[1], r[2], str(r[3])]) + "\n")
        print(f"{subject}: {len(rows)} 点 → {path.name}")

    with (out / "thin_nodes.tsv").open("w", encoding="utf-8", newline="\n") as fh:
        fh.write("# subject\ttopic_slug\tnode_slug\tnode_name\tmaterial_count"
                 f"（本表按当前包重算；material_count<={THIN_MAX} 才算薄料）\n")
        for r in sorted(thin):
            fh.write("\t".join([r[0], r[1], r[2], r[3], str(r[4])]) + "\n")
    with (out / "overfull_nodes.tsv").open("w", encoding="utf-8", newline="\n") as fh:
        fh.write(f"# subject\tnode_slug\tmaterial_count（>={FULL_AT} 即只显示前 {FULL_AT} 条，不要再堆）\n")
        for r in sorted(full):
            fh.write("\t".join([r[0], r[1], str(r[2])]) + "\n")
    print(f"thin_nodes.tsv: {len(thin)} 条；overfull_nodes.tsv: {len(full)} 条 → {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
