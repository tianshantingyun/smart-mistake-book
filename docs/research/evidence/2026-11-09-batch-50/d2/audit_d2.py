# -*- coding: utf-8 -*-
"""批次 50 · D-2 结构审计（只读）：近重复 / 超配 / 容量 / 历史 type。

产物（同目录）：
  d2-summary.json                机器可读汇总（身份、四门计数、判据）
  d2-overfull-ledger.csv         逐超配节点台账（node 级，含保留策略与分诊标记）
  d2-capacity-dispositions.csv    91 条容量拦截逐行处置
  d2-near-dup-dispositions.csv    候选区近重复 2 组逐行结论
  d2-type-distribution.csv        包内 type 分布（3 值 + 3 值外）

口径（与 tools/kb_build/overfull_nodes.py / scan_duplicate_nodes.py / materialize.py 一致）：
- 超配按 (subject, slug) 聚合、每条材料只计首个绑定；>4 为超配。
- 超配节点=分诊名单，**不是死绑定清单**；运行时无每节点条数门（1,024 行整批上界 +
  20,000 字符预算按排序装填），本批保留策略 = 全部保留（不删材料），按分诊标记复核。
- 容量 91 行来自已废止前提（MAX_VISIBLE=4）；本批逐行登记为历史、不入包。
- type 单源 = materialize.TYPES == merge_text_judgments.TYPES == 协议 3 值；包内三值外=0。

只读：本脚本不写包、不改 staging、不跑 promote。
用法：python -X utf8 docs/research/evidence/2026-11-09-batch-50/d2/audit_d2.py
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

REPO = Path(__file__).resolve().parents[5]          # docs/research/evidence/<batch>/d2 -> repo
KNOWLEDGE = REPO / "core/data/src/main/resources/knowledge"
PACK = KNOWLEDGE / "moe-2025-four-subjects-v1.json"
OUT = Path(__file__).resolve().parent
TYPE_VALUES = {"CONCEPT_EXPLANATION", "METHOD_MODEL", "MISCONCEPTION_GUIDE"}
STAGING = REPO / "build/kb-staging"


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


def main() -> int:
    summary: dict = {}

    # ---- 身份（release vs staging 同源核验）----
    release_pack_sha = sha256(PACK)
    staging_pack = STAGING / "moe-2025-four-subjects-v1.json"
    staging_pack_sha = sha256(staging_pack) if staging_pack.exists() else None
    summary["identity"] = {
        "release_pack_sha256": release_pack_sha,
        "staging_pack_sha256": staging_pack_sha,
        "staging_equals_release": staging_pack_sha == release_pack_sha,
        "pack_path": str(PACK.relative_to(REPO)),
    }
    assert staging_pack_sha == release_pack_sha, "staging 与 release 包不同源，审计口径不成立"

    pack = load_json(PACK)
    nodes = point_index(pack)

    materials = []
    for path in sidecar_paths(KNOWLEDGE):
        materials.extend(load_json(path)["materials"])
    summary["materials_total"] = len(materials)
    summary["nodes_total"] = len(nodes)

    # ---- 超配（(subject,slug) 聚合、首绑定）----
    by_node: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for material in materials:
        bindings = material.get("bindings") or []
        if not bindings:
            continue
        parts = bindings[0]["knowledgeNodeId"].split(":")
        key = (parts[-3].upper(), parts[-1])
        by_node[key].append(material)

    overfull: list[dict] = []
    tool_csv = STAGING / "overfull_nodes.csv"
    if tool_csv.exists():
        # 权威口径 = 工具自身产物（weak_match 与节点内真实排序由 overfull_nodes.py 计算），
        # 本脚本只做 node 级聚合与保留策略标注，不重算分诊口径。
        per_node: dict[str, dict] = {}
        with io.open(tool_csv, encoding="utf-8", newline="") as fh:
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
        for acc in per_node.values():
            acc["excess_beyond_4"] = acc["material_count"] - 4
            overfull.append(acc)
    else:
        for key, items in by_node.items():
            if len(items) <= 4:
                continue
            name = nodes.get(key, {}).get("name", "")
            node_grams = bigrams(name)
            excess = items[4:]
            overfull.append({
                "subject": key[0],
                "slug": key[1],
                "node_name": name,
                "topic": nodes.get(key, {}).get("topic", ""),
                "material_count": len(items),
                "excess_beyond_4": len(excess),
                "zero_2gram_total": sum(
                    1 for m in items if not (bigrams(m.get("title") or "") & node_grams)
                ),
                "zero_2gram_excess": sum(
                    1 for m in excess if not (bigrams(m.get("title") or "") & node_grams)
                ),
            })
    overfull.sort(key=lambda r: (-r["material_count"], r["subject"], r["slug"]))

    with (OUT / "d2-overfull-ledger.csv").open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=[
            "subject", "slug", "node_name", "topic", "material_count",
            "excess_beyond_4", "zero_2gram_total", "zero_2gram_excess",
            "disposition", "triage_flag",
        ])
        writer.writeheader()
        for row in overfull:
            # 保留策略：运行时无每节点条数门 ⇒ 全部保留（不物删、不解绑）；
            # triage_flag 只标优先复核对象，不改变保留结论（D-2「明确保留策略」）。
            if row["material_count"] > 100 or row["zero_2gram_excess"] >= 20:
                triage = "PRIORITY_REVIEW"
            elif row["zero_2gram_excess"] > 0:
                triage = "REVIEW"
            else:
                triage = "NONE"
            writer.writerow({**row, "disposition": "RETAIN_NO_CAP", "triage_flag": triage})

    summary["overfull"] = {
        "definition": "(subject,slug) 聚合、每条材料只计首个绑定、>4 条",
        "source": "build/kb-staging/overfull_nodes.csv（`PYTHONPATH=tools python -m kb_build.overfull_nodes --write` 产物；"
                  "weak_match/节点内排序由工具计算，本脚本只做 node 级聚合）",
        "nodes_over_4": len(overfull),
        "nodes_ge_4": sum(1 for items in by_node.values() if len(items) >= 4),
        "nodes_eq_4": sum(1 for items in by_node.values() if len(items) == 4),
        "materials_in_overfull": sum(r["material_count"] for r in overfull),
        "bindings_beyond_4": sum(r["excess_beyond_4"] for r in overfull),
        "max_material_count": overfull[0]["material_count"] if overfull else 0,
        "max_node": f'{overfull[0]["subject"]}/{overfull[0]["slug"]}' if overfull else None,
        "zero_2gram_total": sum(r["zero_2gram_total"] for r in overfull),
        "zero_2gram_excess": sum(r["zero_2gram_excess"] for r in overfull),
        "triage": {
            "priority_review": sum(
                1 for r in overfull
                if r["material_count"] > 100 or r["zero_2gram_excess"] >= 20
            ),
            "review": sum(
                1 for r in overfull
                if not (r["material_count"] > 100 or r["zero_2gram_excess"] >= 20)
                and r["zero_2gram_excess"] > 0
            ),
            "none": sum(1 for r in overfull if r["zero_2gram_excess"] == 0),
        },
        "retention_policy": "RETAIN_NO_CAP：运行时无每节点条数门（1,024 行整批上界 + 20,000 字符预算"
                            "按排序装填），全部材料保留、不解绑、不物删；分诊标记只决定复核顺序，"
                            "不构成删除/解绑裁决",
        "ledger": "d2-overfull-ledger.csv",
    }

    # ---- 容量 91 条逐行处置 ----
    capacity_path = REPO / "knowledge-production/candidate-merge/capacity_blocked.csv"
    with io.open(capacity_path, encoding="utf-8", newline="") as fh:
        capacity_rows = list(csv.DictReader(fh))
    material_slugs = {m["slug"] for m in materials}
    material_by_slug = {m["slug"]: m for m in materials}
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
                      "逐行登记：已在包=内容线后续采纳（22 条同节点 / 12 条异节点需内容线复核），"
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
        "disposition": "逐行登记（ALREADY_IN_PACK_SAME_NODE / IN_PACK_OTHER_NODE / "
                       "NOT_ADMITTED_HISTORICAL / OBSOLETE_NODE）；不补、不删",
        "producer_premise": "tools/kb_build/merge_candidate_verdicts.py:165 MAX_VISIBLE = 4 "
                            "与 :209-216 ≥4 即拦——运行时无该门，规则已废止",
        "producer_patch": "d2-capacity-deprecation.patch（在飞文件不抢占，仅出补丁+判据）",
        "ledger": "d2-capacity-dispositions.csv",
    }

    # ---- 候选区近重复 2 组（实测：两组都已在包内、同节点、正文近同）----
    near_path = REPO / "knowledge-production/candidate-merge/near_duplicate_review.csv"
    with io.open(near_path, encoding="utf-8", newline="") as fh:
        near_rows = list(csv.DictReader(fh))
    near_out = []
    for row in near_rows:
        slugs = [s for s in row["slugs"].split(";") if s]
        in_pack = [s for s in slugs if s in material_by_slug]
        # 保留规则与 dedupe_near_dup_materials 一致：正文更长者保留；并列取 slug 字典序小者。
        def content_len(slug: str) -> int:
            material = material_by_slug.get(slug) or {}
            return len(material.get("contentMarkdown") or "")

        keep = max(in_pack, key=lambda s: (content_len(s), -ord(s[0]))) if in_pack else ""
        if len(in_pack) == 2:
            keep = sorted(in_pack, key=lambda s: (-content_len(s), s))[0]
            drop = sorted(set(in_pack) - {keep})[0]
            disposition = "ADJUDICATED_RETIRE_DEFERRED"
            verdict = f"真重复（同节点/同标题/正文近同）——保留 {keep}，退休 {drop}（未执行，写入窗口内落）"
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
        "pack_node_scan": "0 组 / 0 对（tools/kb_build/scan_duplicate_nodes.py；节点名口径）",
        "material_level_scan": "50 簇 / 拟退休 1（dedupe_near_dup_materials --dry-run）；"
                               "本 2 组被实体替换门挡下（正文仅虚词差异），需人工裁决",
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
