# -*- coding: utf-8 -*-
"""批次 51 · D-2 结构审计（只读）：近重复 / 超配 / 容量 / 历史 type。

与批次 50 版（`docs/research/evidence/2026-11-09-batch-50/d2/audit_d2.py`）的差异，仅三处：

1. **抽样框已冻结**（本批任务书第 2 条）：超配明细不再读 `build/kb-staging/overfull_nodes.csv`
   的“当前内容”，而是读冻结快照
   `docs/research/evidence/2026-11-10-batch-51/3d-audit/frame/overfull_nodes.snapshot.csv`，
   并在读取前校验 sha256 = `FRAME_SHA256`（同时校验 staging 原件未被改写）。
   **本脚本永不写 `build/kb-staging/overfull_nodes.csv`。**
2. 追加**只读的 v29 复算**：按 `tools/kb_build/overfull_nodes.py` 同口径（(subject,slug) 聚合、
   首绑定、>4、零 2-gram 分诊）在当前 shipped pack 上重算，落
   `overfull_recompute_v29.csv`，并与冻结框做节点/行级 diff（`frame-divergence.json`）。
   这是“冻结框 vs 当前包”的差异登记，**不是**新抽样框，也不覆盖冻结文件。
3. 身份区多记冻结框 sha 与 v29 包 sha。

口径与原版一致：超配是分诊名单，不是死绑定清单；保留策略 RETAIN_NO_CAP；容量 91 行逐行登记；
近重复 2 组按候选区表逐行裁决（退休未执行）；type 三值外 = 0 即单源。

只读：不写包、不改 staging、不跑 promote。
用法：python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/3d-d2d3/audit_d2_b51.py
"""

from __future__ import annotations

import csv
import hashlib
import io
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
KNOWLEDGE = REPO / "core/data/src/main/resources/knowledge"
PACK = KNOWLEDGE / "moe-2025-four-subjects-v1.json"
STAGING = REPO / "build/kb-staging"
OUT = Path(__file__).resolve().parent
FRAME = REPO / "docs/research/evidence/2026-11-10-batch-51/3d-audit/frame/overfull_nodes.snapshot.csv"
FRAME_SHA256 = "ce7d6823787204640a646854561ea85cbbf4f19c48a0809754a3b0f419c69d9d"
TYPE_VALUES = {"CONCEPT_EXPLANATION", "METHOD_MODEL", "MISCONCEPTION_GUIDE"}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_json(path: Path):
    with io.open(path, encoding="utf-8") as fh:
        return json.load(fh)


def sidecar_paths(base: Path) -> list[Path]:
    index = load_json(base / "moe-2025-teaching-support-v2-index.json")
    return [base / name.rsplit("/", 1)[-1] for name in index["sidecars"]]


def point_index(pack: dict) -> dict[tuple[str, str], dict]:
    out: dict[tuple[str, str], dict] = {}
    for subject in pack["subjects"]:
        for topic in subject["topics"]:
            for point in topic.get("knowledgePoints") or []:
                out[(subject["subject"], point["slug"])] = {
                    "name": point["name"],
                    "topic": topic["name"],
                }
    return out


def norm(text: str) -> str:
    return re.sub(r"[\s，。、；：（）()\[\]【】“”\"'·—\-_/\\|!?？！,.:;]+", "", text or "")


def bigrams(text: str) -> set[str]:
    t = norm(text)
    return {t[i:i + 2] for i in range(len(t) - 1)} or {t}


def pack_materials():
    materials = []
    for path in sidecar_paths(KNOWLEDGE):
        materials.extend(load_json(path)["materials"])
    return materials


def node_sort_key(item: dict):
    role_rank = {"PRIMARY": 0, "SUPPORTING": 1}
    type_priority = {
        "MISCONCEPTION_GUIDE": 0, "WORKED_EXAMPLE": 1, "METHOD_MODEL": 2, "DERIVATION": 3,
        "CONCEPT_EXPLANATION": 4, "REPRESENTATION_GUIDE": 5, "COMPLETE_SOLUTION": 6,
    }
    return (role_rank.get(item["role"], 2), type_priority.get(item["type"], 7),
            item["title"], item["material_id"])


def recompute_overfull(pack: dict):
    """与 tools/kb_build/overfull_nodes.py 同口径的只读复算；返回 (rows, summary)。"""
    by_node: dict[str, list[dict]] = defaultdict(list)
    for material in pack_materials():
        for binding in material.get("bindings") or []:
            by_node[binding["knowledgeNodeId"]].append({
                "material_id": material["slug"],
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

    rows = []
    overfull_nodes = 0
    for node_id, items in sorted(by_node.items()):
        if len(items) <= 4:
            continue
        overfull_nodes += 1
        subject, chapter, node_name = names.get(node_id, ("?", "?", node_id))
        items = sorted(items, key=node_sort_key)
        node_grams = bigrams(node_name)
        for index, item in enumerate(items):
            weak = not (bigrams(item["title"]) & node_grams)
            rows.append({
                "node_id": node_id, "subject": subject, "chapter": chapter,
                "node_name": node_name, "material_count": len(items),
                "verdict_keep_or_move": "", "rank": index + 1, "role": item["role"],
                "type": item["type"], "title": item["title"],
                "material_slug": item["material_id"], "chars": item["chars"],
                "weak_match": "yes" if weak else "",
            })
    summary = {
        "nodes_over_4": overfull_nodes,
        "materials_in_overfull": len(rows),
        "bindings_beyond_4": sum(1 for r in rows if r["rank"] > 4),
        "nodes_ge_4": sum(1 for items in by_node.values() if len(items) >= 4),
        "nodes_eq_4": sum(1 for items in by_node.values() if len(items) == 4),
        "max_material_count": max((len(v) for v in by_node.values()), default=0),
        "zero_2gram_total": sum(1 for r in rows if r["weak_match"] == "yes"),
        "zero_2gram_excess": sum(1 for r in rows if r["weak_match"] == "yes" and r["rank"] > 4),
        "materials_total": sum(len(v) for v in by_node.values()),
        "nodes_with_bindings": len(by_node),
    }
    return rows, summary


def main() -> int:
    summary: dict = {}

    # ---- 身份（release vs staging 同源核验）+ 冻结框校验 ----
    release_pack_sha = sha256(PACK)
    staging_pack = STAGING / "moe-2025-four-subjects-v1.json"
    staging_pack_sha = sha256(staging_pack) if staging_pack.exists() else None
    frame_sha = sha256(FRAME)
    staging_frame = STAGING / "overfull_nodes.csv"
    staging_frame_sha = sha256(staging_frame) if staging_frame.exists() else None
    summary["identity"] = {
        "release_pack_sha256": release_pack_sha,
        "staging_pack_sha256": staging_pack_sha,
        "staging_equals_release": staging_pack_sha == release_pack_sha,
        "pack_path": str(PACK.relative_to(REPO)),
        "frozen_frame": str(FRAME.relative_to(REPO)),
        "frozen_frame_sha256": frame_sha,
        "frozen_frame_sha_expected": FRAME_SHA256,
        "staging_overfull_sha256": staging_frame_sha,
        "staging_overfull_equals_frozen": staging_frame_sha == FRAME_SHA256,
    }
    assert staging_pack_sha == release_pack_sha, "staging 与 release 包不同源，审计口径不成立"
    assert frame_sha == FRAME_SHA256, "冻结抽样框 sha256 不符（文件被改动）"
    assert staging_frame_sha == FRAME_SHA256, "staging overfull_nodes.csv 已被改写（违反冻结）"

    pack = load_json(PACK)
    nodes = point_index(pack)
    materials = pack_materials()
    material_by_slug = {m["slug"]: m for m in materials}
    summary["materials_total"] = len(materials)
    summary["nodes_total"] = len(nodes)

    # ---- 超配：冻结框（权威抽样框）+ v29 只读复算（差异登记）----
    by_node: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for material in materials:
        bindings = material.get("bindings") or []
        if not bindings:
            continue
        parts = bindings[0]["knowledgeNodeId"].split(":")
        by_node[(parts[-3].upper(), parts[-1])].append(material)

    per_node: dict[str, dict] = {}
    with io.open(FRAME, encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            node_id = row["node_id"]
            acc = per_node.setdefault(node_id, {
                "subject": row["subject"],
                "slug": node_id.split(":")[-1],
                "node_name": row["node_name"],
                "topic": row["chapter"],
                "material_count": int(row["material_count"]),
                "zero_2gram_total": 0,
                "zero_2gram_excess": 0,
            })
            if row["weak_match"] == "yes":
                acc["zero_2gram_total"] += 1
                if int(row["rank"]) > 4:
                    acc["zero_2gram_excess"] += 1
    overfull = sorted(per_node.values(), key=lambda r: (-r["material_count"], r["subject"], r["slug"]))
    for acc in overfull:
        acc["excess_beyond_4"] = acc["material_count"] - 4

    with (OUT / "d2-overfull-ledger-frozen-frame.csv").open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=[
            "subject", "slug", "node_name", "topic", "material_count",
            "excess_beyond_4", "zero_2gram_total", "zero_2gram_excess",
            "disposition", "triage_flag",
        ])
        writer.writeheader()
        for row in overfull:
            if row["material_count"] > 100 or row["zero_2gram_excess"] >= 20:
                triage = "PRIORITY_REVIEW"
            elif row["zero_2gram_excess"] > 0:
                triage = "REVIEW"
            else:
                triage = "NONE"
            writer.writerow({**row, "disposition": "RETAIN_NO_CAP", "triage_flag": triage})

    v29_rows, v29_summary = recompute_overfull(pack)
    # 行级明细 16MB 级：落 build（gitignored，可重生成），证据目录只留节点级台账。
    scratch = REPO / "build" / "b51-d2d3-scratch"
    scratch.mkdir(parents=True, exist_ok=True)
    with (scratch / "overfull_recompute_v29.csv").open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(v29_rows[0].keys()) if v29_rows else ["node_id"])
        writer.writeheader()
        writer.writerows(v29_rows)
    with (OUT / "d2-overfull-recompute-v29-nodes.csv").open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=["node_id", "subject", "chapter", "node_name",
                                                "material_count", "excess_beyond_4",
                                                "zero_2gram_total", "zero_2gram_excess"])
        writer.writeheader()
        node_stats: dict[str, dict] = {}
        for row in v29_rows:
            acc = node_stats.setdefault(row["node_id"], {
                "subject": row["subject"], "chapter": row["chapter"],
                "node_name": row["node_name"], "material_count": row["material_count"],
                "weak": 0, "weak_excess": 0,
            })
            if row["weak_match"] == "yes":
                acc["weak"] += 1
                if row["rank"] > 4:
                    acc["weak_excess"] += 1
        for node_id, stats in node_stats.items():
            writer.writerow({
                "node_id": node_id, "subject": stats["subject"], "chapter": stats["chapter"],
                "node_name": stats["node_name"], "material_count": stats["material_count"],
                "excess_beyond_4": stats["material_count"] - 4,
                "zero_2gram_total": stats["weak"],
                "zero_2gram_excess": stats["weak_excess"],
            })

    frozen_ids = set(per_node)
    v29_by_node: dict[str, dict] = {}
    for row in v29_rows:
        acc = v29_by_node.setdefault(row["node_id"], {"count": row["material_count"],
                                                     "weak": 0, "weak_excess": 0})
        if row["weak_match"] == "yes":
            acc["weak"] += 1
            if row["rank"] > 4:
                acc["weak_excess"] += 1
    v29_ids = set(v29_by_node)
    count_changes = []
    for node_id in sorted(frozen_ids & v29_ids):
        before = per_node[node_id]["material_count"]
        after = v29_by_node[node_id]["count"]
        if before != after:
            count_changes.append({"node_id": node_id, "frozen_count": before, "v29_count": after})
    divergence = {
        "frozen_nodes": len(frozen_ids),
        "v29_nodes": len(v29_ids),
        "dropped_from_overfull": sorted(frozen_ids - v29_ids),
        "added_to_overfull": sorted(v29_ids - frozen_ids),
        "material_count_changed": count_changes,
        "frozen_summary": {
            "nodes_over_4": len(overfull),
            "materials_in_overfull": sum(r["material_count"] for r in overfull),
            "bindings_beyond_4": sum(r["excess_beyond_4"] for r in overfull),
            "zero_2gram_total": sum(r["zero_2gram_total"] for r in overfull),
            "zero_2gram_excess": sum(r["zero_2gram_excess"] for r in overfull),
        },
        "v29_summary": v29_summary,
        "note": "冻结框由任务书要求在 D-2 开工时快照（v28 时代产物，2026-11-08）；"
                "v29 复算只作差异登记，不替换冻结框、不写 build/kb-staging/overfull_nodes.csv",
    }
    (OUT / "frame-divergence.json").write_text(
        json.dumps(divergence, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    summary["overfull_frozen_frame"] = {
        "definition": "(subject,slug) 聚合、每条材料只计首个绑定、>4 条（工具口径）",
        "source": "docs/research/evidence/2026-11-10-batch-51/3d-audit/frame/"
                  "overfull_nodes.snapshot.csv（sha256 冻结；**未重写** build/kb-staging/overfull_nodes.csv）",
        "nodes_over_4": len(overfull),
        "materials_in_overfull": sum(r["material_count"] for r in overfull),
        "bindings_beyond_4": sum(r["excess_beyond_4"] for r in overfull),
        "max_material_count": overfull[0]["material_count"] if overfull else 0,
        "max_node": f'{overfull[0]["subject"]}/{overfull[0]["slug"]}' if overfull else None,
        "zero_2gram_total": sum(r["zero_2gram_total"] for r in overfull),
        "zero_2gram_excess": sum(r["zero_2gram_excess"] for r in overfull),
        "triage": {
            "priority_review": sum(1 for r in overfull
                                   if r["material_count"] > 100 or r["zero_2gram_excess"] >= 20),
            "review": sum(1 for r in overfull
                          if not (r["material_count"] > 100 or r["zero_2gram_excess"] >= 20)
                          and r["zero_2gram_excess"] > 0),
            "none": sum(1 for r in overfull if r["zero_2gram_excess"] == 0),
        },
        "retention_policy": "RETAIN_NO_CAP：运行时无每节点条数门（1,024 行整批上界 + 20,000 字符预算"
                            "按排序装填），全部材料保留、不解绑、不物删；分诊标记只决定复核顺序",
        "ledger": "d2-overfull-ledger-frozen-frame.csv",
    }
    summary["overfull_recompute_v29"] = {
        "source": "当前 shipped pack（release == staging）+ 卷索引枚举，工具同口径只读复算",
        "detail": "overfull_recompute_v29.csv",
        "summary": v29_summary,
        "divergence_vs_frozen_frame": "frame-divergence.json",
    }

    # ---- 容量 91 条逐行处置 ----
    capacity_path = REPO / "knowledge-production/candidate-merge/capacity_blocked.csv"
    with io.open(capacity_path, encoding="utf-8", newline="") as fh:
        capacity_rows = list(csv.DictReader(fh))
    dispositions = []
    for row in capacity_rows:
        key = (row["subject"].upper(), row["node_slug"])
        material = material_by_slug.get(row["slug"])
        in_pack = material is not None
        bound_node = ""
        if in_pack:
            bindings = material.get("bindings") or []
            bound_node = bindings[0]["knowledgeNodeId"].split(":")[-1] if bindings else ""
        if not in_pack and key not in nodes:
            disposition = "OBSOLETE_NODE"
        elif in_pack and bound_node == row["node_slug"]:
            disposition = "ALREADY_IN_PACK_SAME_NODE"
        elif in_pack:
            disposition = "IN_PACK_OTHER_NODE"
        else:
            disposition = "NOT_ADMITTED_HISTORICAL"
        dispositions.append({
            "source": row["source"],
            "subject": row["subject"],
            "node_slug": row["node_slug"],
            "slug": row["slug"],
            "title": row["title"],
            "existing_materials_at_block": row["existing_materials"],
            "node_still_in_pack": "yes" if key in nodes else "no",
            "material_already_in_pack": "yes" if in_pack else "no",
            "bound_node_slug": bound_node,
            "current_node_material_count": len(by_node.get(key, [])),
            "disposition": disposition,
            "reason": "拦截规则 MAX_VISIBLE=4 的运行时前提已被推翻（运行时无每节点条数门）；"
                      "逐行登记：已在包=内容线后续采纳（同节点/异节点需内容线复核），"
                      "不在包=历史候选，不据此补料/删料",
        })
    with (OUT / "d2-capacity-dispositions.csv").open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(dispositions[0].keys()) if dispositions else ["source"])
        writer.writeheader()
        writer.writerows(dispositions)
    capacity_counts = Counter(d["disposition"] for d in dispositions)
    summary["capacity"] = {
        "rows": len(dispositions),
        "already_in_pack_same_node": capacity_counts.get("ALREADY_IN_PACK_SAME_NODE", 0),
        "in_pack_other_node": capacity_counts.get("IN_PACK_OTHER_NODE", 0),
        "not_admitted_historical": capacity_counts.get("NOT_ADMITTED_HISTORICAL", 0),
        "obsolete_node": capacity_counts.get("OBSOLETE_NODE", 0),
        "node_missing": sum(1 for d in dispositions if d["node_still_in_pack"] == "no"),
        "disposition": "逐行登记；不补、不删",
        "producer_premise": "tools/kb_build/merge_candidate_verdicts.py:165 MAX_VISIBLE = 4 "
                            "与 :209-216 ≥4 即拦——运行时无该门，规则已废止",
        "producer_patch": "批次50 补丁 d2-capacity-deprecation.patch 仍 NOT_APPLIED（在飞文件不抢占）",
        "ledger": "d2-capacity-dispositions.csv",
    }

    # ---- 候选区近重复 2 组（逐行裁决；退休未执行）----
    near_path = REPO / "knowledge-production/candidate-merge/near_duplicate_review.csv"
    with io.open(near_path, encoding="utf-8", newline="") as fh:
        near_rows = list(csv.DictReader(fh))
    near_out = []
    for row in near_rows:
        slugs = [s for s in row["slugs"].split(";") if s]
        in_pack = [s for s in slugs if s in material_by_slug]

        def content_len(slug: str) -> int:
            material = material_by_slug.get(slug) or {}
            return len(material.get("contentMarkdown") or "")

        keep = max(in_pack, key=lambda s: (content_len(s), -ord(s[0]))) if in_pack else ""
        if len(in_pack) == 2:
            keep = sorted(in_pack, key=lambda s: (-content_len(s), s))[0]
            drop = sorted(set(in_pack) - {keep})[0]
            disposition = "ADJUDICATED_RETIRE_DEFERRED"
            verdict = f"真重复（同节点/同标题/正文近同）——保留 {keep}，退休 {drop}（未执行）"
        elif in_pack:
            drop = ""
            disposition = "PARTIAL_IN_PACK"
            verdict = f"仅 {keep} 在包，另一条未入包——保留在包条，候选条不再补"
        else:
            drop = ""
            disposition = "CANDIDATE_ONLY_NOT_ADMITTED"
            verdict = "两条候选均未入包，不入包"
        near_out.append({
            "subject": row["subject"],
            "node_slug": row["node_slug"],
            "title_key": row["title_key"],
            "slugs": row["slugs"],
            "node_in_pack": "yes" if (row["subject"].upper(), row["node_slug"]) in nodes else "no",
            "materials_in_pack": ",".join(in_pack) or "(none)",
            "keep_slug": keep,
            "retire_slug": drop,
            "content_len": ";".join(f"{s}:{content_len(s)}" for s in in_pack),
            "disposition": disposition,
            "verdict": verdict,
        })
    with (OUT / "d2-near-dup-dispositions.csv").open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(near_out[0].keys()) if near_out else ["subject"])
        writer.writeheader()
        writer.writerows(near_out)
    summary["near_duplicate"] = {
        "candidate_rows": len(near_out),
        "candidate_materials_in_pack": sum(1 for r in near_out if r["materials_in_pack"] != "(none)"),
        "pack_node_scan": "见 d2-near-dup-scan.json（tools/kb_build.scan_duplicate_nodes）",
        "material_level_scan": "见 d2-dedupe-dryrun.json（tools/kb_coverage.dedupe_near_dup_materials --dry-run）",
        "dispositions": {r["node_slug"]: r["disposition"] for r in near_out},
        "retire_deferred": [r["retire_slug"] for r in near_out if r["retire_slug"]],
        "ledger": "d2-near-dup-dispositions.csv",
    }

    # ---- 历史 type ----
    type_counts = Counter(m["type"] for m in materials)
    outside = {k: v for k, v in type_counts.items() if k not in TYPE_VALUES}
    with (OUT / "d2-type-distribution.csv").open("w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["type", "count", "in_3_value_set"])
        for name, count in type_counts.most_common():
            writer.writerow([name, count, "yes" if name in TYPE_VALUES else "no"])
    summary["type"] = {
        "materials_total": len(materials),
        "outside_3_value_set": sum(outside.values()),
        "outside_distribution": outside,
        "materialize_TYPES": "tools/kb_coverage/materialize.py:66 = 3 值",
        "merge_TYPES": "tools/kb_coverage/merge_text_judgments.py:47 = 3 值",
        "protocol": "docs/kb-chunk-judgment-protocol.md:62/90 = 3 值",
        "single_source": sum(outside.values()) == 0,
        "ledger": "d2-type-distribution.csv",
    }

    with (OUT / "d2-summary.json").open("w", encoding="utf-8") as fh:
        json.dump(summary, fh, ensure_ascii=False, indent=1)
        fh.write("\n")

    print(json.dumps(summary, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
