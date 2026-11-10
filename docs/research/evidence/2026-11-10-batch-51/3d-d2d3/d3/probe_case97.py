# -*- coding: utf-8 -*-
"""批次 51 · D-3 诊断复算（只读）：golden case #97 的 117→116 单题翻转归因。

三件事，全部可复跑：

1. **别名变化**：`git show HEAD:<pack>`（v28）vs 当前 shipped pack（v29）——目标节点
   `超重与失重的判断方法` 与源节点 `加速度方向突变` 的 aliases 差集（name/boundary 未变）。
2. **索引特征变化**：按 `KnowledgeSearchFeatureExtractor.kt` 的 v1 规则
   （name + sorted(aliases) + boundary → `[^\\p{L}\\p{N}]+` 切片 → 16 片段 / 192 特征，
   平衡采样）在 v28 / v29 两包上复算该节点的 matched 特征数；本机 v29 复算值 = 4，
   与 Kotlin 导出 `build/production-lexical-leg.tsv`（query 97）逐位一致——这是本复算
   对当前包可信的直接校验。
3. **隔离**：固定 v29 稠密分与 v29 候选域，只把期望节点的词面计数 4 换回 v28 的 7，
   重跑 `DenseFusion.order` 等价实现——rank 7 → rank 4（命中）。证明翻转由该节点的
   词面计数单独造成，与稠密腿无关。

注意：本脚本是**诊断**，不是生产实现；port 只对 case #97 节点与 Kotlin 导出做过逐位校验
（4 == 4），不对全库承诺逐位一致（见 README「未验证/边界」）。

用法（仓库根）：python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/3d-d2d3/d3/probe_case97.py
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[6]
sys.path.insert(0, str(REPO / "tools" / "dense_build"))
sys.path.insert(0, str(REPO))

PACK_REL = "core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json"
GOLDEN_REL = "tools/kb_coverage/tables/golden_queries_v2.json"
INDEX = 97
TARGET_SLUG = "超重与失重的判断方法"
NON_SEARCH = re.compile(r"[^0-9A-Za-z\u00C0-\u024F\u0370-\u03FF\u0400-\u04FF\u4e00-\u9fff\u3400-\u4dbf]+")


def balanced_start_indexes(count):
    if count <= 0:
        return
    pending = [(0, count - 1)]
    while pending:
        first, last = pending.pop(0)
        middle = (first + last) // 2
        yield middle
        if first < middle:
            pending.append((first, middle - 1))
        if middle < last:
            pending.append((middle + 1, last))


def fragment_features(fragment, max_word_length=48):
    if 2 <= len(fragment) <= max_word_length:
        yield fragment
    for width in range(2, min(3, len(fragment)) + 1):
        for start in balanced_start_indexes(len(fragment) - width + 1):
            yield fragment[start:start + width]


def extract(texts, fragment_limit, feature_limit, max_word_length=48):
    fragments = []
    for raw in texts:
        for part in NON_SEARCH.split(raw.lower()):
            if part:
                fragments.append(part)
    if len(fragments) <= fragment_limit:
        selected = list(fragments)
    else:
        selected = [fragments[-1], fragments[0]]
        for index in balanced_start_indexes(len(fragments)):
            if len(selected) < fragment_limit and fragments[index] not in selected:
                selected.append(fragments[index])
    streams = [iter(list(fragment_features(f, max_word_length))) for f in selected]
    result, seen = [], set()
    while len(result) < feature_limit and streams:
        it = streams.pop(0)
        try:
            value = next(it)
        except StopIteration:
            continue
        if value not in seen:
            seen.add(value)
            result.append(value)
        streams.append(it)
    return selected, set(result)


def node_texts(point):
    texts = [point["name"]] + sorted(point.get("aliases") or [])
    if point.get("boundary"):
        texts.append(point["boundary"])
    return texts


def find_point(pack, slug):
    for subject in pack["subjects"]:
        for topic in subject["topics"]:
            for point in topic.get("knowledgePoints") or []:
                if point["slug"] == slug:
                    return subject["subject"], topic["name"], point
    raise SystemExit(f"节点不存在：{slug}")


def load_v28():
    out = subprocess.run(["git", "show", "HEAD:" + PACK_REL], capture_output=True, check=True)
    return json.loads(out.stdout.decode("utf-8"))


def main() -> int:
    v29 = json.loads((REPO / PACK_REL).read_text(encoding="utf-8"))
    v28 = load_v28()
    golden = json.loads((REPO / GOLDEN_REL).read_text(encoding="utf-8"))
    case = golden[INDEX]
    query = extract([case["query"]], 16, 128)[1]

    print(f"golden case #{INDEX}: expectedSlug={case['expectedSlug']}")
    for slug in (TARGET_SLUG, "加速度方向突变"):
        _s28, _t28, p28 = find_point(v28, slug)
        _s29, _t29, p29 = find_point(v29, slug)
        a28, a29 = set(p28.get("aliases") or []), set(p29.get("aliases") or [])
        print(f"alias diff [{slug}]: v28={len(a28)} v29={len(a29)} "
              f"added={sorted(a29 - a28)} removed={sorted(a28 - a29)} "
              f"name_same={p28['name'] == p29['name']} "
              f"boundary_same={p28.get('boundary') == p29.get('boundary')}")

    print("\n-- node-side index features (v1 rules: 16 fragments / 192 features) --")
    for label, pack in (("v28(HEAD)", v28), ("v29(now)", v29)):
        _s, _t, point = find_point(pack, TARGET_SLUG)
        texts = node_texts(point)
        selected, feats = extract(texts, 16, 192)
        fragments = [part for raw in texts for part in NON_SEARCH.split(raw.lower()) if part]
        matched = sorted(query & feats)
        print(f"{label}: aliases={len(point.get('aliases') or [])} fragments={len(fragments)} "
              f"selected={len(selected)} features={len(feats)} matched={len(matched)}")
        print(f"  matched features: {matched}")
        print(f"  acceleration terms in features: "
              f"加速={'加速' in feats} 加速度={'加速度' in feats} 速度={'速度' in feats}")
        print(f"  selected fragments: {selected}")

    print("\n-- isolation: v29 dense + candidate domain fixed; only the expected node's lex count swapped --")
    import numpy as np
    import dense_asset as D
    import stage3_expectation as S
    import stage3_device_sim as M

    root = D.repo_root(None)
    _rows, nodes, groups = D.atomic_layout(root)
    ids, matrix, scales = M.read_raw_asset(root.joinpath(*M.VECTOR_FILE.parts))
    group_of = {nid: (s, e) for nid, s, e in groups}
    name_of = {n["node_id"]: n["name"] for n in nodes}
    q_int8 = np.load(root / "build" / "dense-model" / "int8-queries.npy")
    q = (q_int8 / np.maximum(np.linalg.norm(q_int8, axis=1, keepdims=True), 1e-12))[INDEX]
    dense = {}
    for node in nodes:
        if node["subject"] != case["subject"]:
            continue
        start, end = group_of[node["node_id"]]
        best = -1e30
        for r in range(start, end):
            best = max(best, float(scales[r]) * float(np.dot(matrix[r].astype(np.float64), q)))
        dense[node["node_id"]] = best
    leg = M.lexical_rows(root.joinpath(*D.LEXICAL_LEG_RELATIVE.split("/")))
    cands = leg[INDEX][:M.CANDIDATE_LIMIT]
    domain = [nid for nid, _ in cands]
    lex = {nid: float(c) for nid, c in cands}
    expected_id = next(n["node_id"] for n in nodes
                       if n["subject"] == case["subject"]
                       and n["node_id"].endswith(":" + case["expectedSlug"]))

    def rank_of(counts):
        ranking = M.device_order(domain, dense, counts)
        return (ranking.index(expected_id) + 1) if expected_id in ranking else None, ranking

    for label, counts in (("v29 lexical (Kotlin leg)", lex),
                          ("only expected-node count 4->7", {**lex, expected_id: 7.0})):
        rank, ranking = rank_of(counts)
        dn = S.minmax(dict(dense))
        ln = S.minmax(dict(counts))
        fused = S.ALPHA * dn.get(expected_id, 0.0) + (1 - S.ALPHA) * ln.get(expected_id, 0.0)
        print(f"{label}: rank={rank} lex={counts[expected_id]} "
              f"dn={dn.get(expected_id):.4f} ln={ln.get(expected_id):.4f} fused={fused:.4f}")
        print("  top8:", [(name_of.get(nid, nid.split(':')[-1]), counts.get(nid)) for nid in ranking[:8]])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
