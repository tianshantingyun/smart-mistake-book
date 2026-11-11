# -*- coding: utf-8 -*-
"""批次51 3D：改后校验与逐条 before→after（只读）。

产出：
- after-hashes.tsv（shipped/staging 全家族 + 相关台账）
- per-item-before-after.csv（43 条：改前绑定 → 改后绑定 / DROPPED）
- verify.json（计数、悬空、包身份、staging==shipped、与改前哈希的差异清单）
"""
from __future__ import annotations

import csv
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[4]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import pack_io, update_manifest  # noqa: E402

SHIPPED = REPO / "core/data/src/main/resources/knowledge"
STAGING = REPO / "build/kb-staging"
TABLES = ["tools/kb_build/tables/material_rebind.csv",
          "tools/kb_build/tables/material_bindings.csv",
          "tools/kb_build/tables/point_delete.csv",
          "tools/kb_build/tables/alias_map.csv",
          "tools/kb_build/tables/chapter_by_node.csv"]
NAMES = ["moe-2025-four-subjects-v1.json", "moe-2025-teaching-support-v2-index.json",
         "moe-2025-update-manifest.json"] + \
        [f"moe-2025-teaching-support-v2-{i:02d}.json" for i in range(1, 26)]


def sha(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main() -> int:
    before = {}
    for line in (HERE / "before-hashes.tsv").read_text(encoding="utf-8").splitlines():
        kind, rel, size, digest = line.split("\t")
        before[(kind, rel)] = (int(size), digest)

    after = []
    for label, root in (("shipped", SHIPPED), ("staging", STAGING)):
        for n in NAMES:
            p = root / n
            after.append((label, n, p.stat().st_size, sha(p)))
    for rel in TABLES:
        p = REPO / rel
        after.append(("table", rel.replace("/", "\\"), p.stat().st_size, sha(p)))
    (HERE / "after-hashes.tsv").write_text(
        "\n".join("\t".join(str(x) for x in row) for row in after) + "\n", encoding="utf-8")

    changed = []
    for label, rel, size, digest in after:
        if label in ("shipped", "staging"):
            key = (label, rel)
            if key in before and before[key] != (size, digest):
                changed.append({"kind": label, "file": rel,
                                "bytes": [before[key][0], size],
                                "sha256": [before[key][1], digest]})
        else:
            key = ("table", rel)
            # 表格的相对路径统一成 / 便于与 before 比较
            key2 = ("table", rel.replace("\\", "/"))
            old = before.get(key) or before.get(key2)
            if old and old != (size, digest):
                changed.append({"kind": "table", "file": rel,
                                "bytes": [old[0], size], "sha256": [old[1], digest]})

    sync = all(sha(SHIPPED / n) == sha(STAGING / n) for n in NAMES)

    pack_io.use_directory(SHIPPED)
    try:
        pack = pack_io.load_json(SHIPPED / pack_io.PACK_NAME)
        sidecars = [pack_io.load_json(p) for p in pack_io.sidecar_paths()]
        index = pack_io.load_json(SHIPPED / pack_io.SIDECAR_INDEX_NAME)
        manifest = pack_io.load_json(SHIPPED / update_manifest.MANIFEST_NAME)
    finally:
        pack_io.reset_directory()

    nodes = {f"kb:{pack['packId']}:{s['subject'].lower()}:atomic:{k['slug']}"
             for s in pack["subjects"] for t in s["topics"]
             for k in t.get("knowledgePoints") or []}
    mats = {m["slug"]: m for sc in sidecars for m in sc["materials"]}
    bindings = [(m["slug"], b["knowledgeNodeId"]) for m in mats.values()
                for b in m.get("bindings") or []]
    dangling = [b for _s, b in bindings if b not in nodes]
    unbound = [s for s, m in mats.items() if not (m.get("bindings") or [])]

    # 逐条 before→after
    state = json.loads((HERE / "before-state.json").read_text(encoding="utf-8"))
    disp = {(r["material_slug"]): r for r in
            csv.DictReader((HERE / "dispositions.csv").open(encoding="utf-8", newline=""))}
    rows = []
    for item in state["items"]:
        slug = item["material_slug"]
        d = disp[slug]
        if slug in mats:
            after_ids = [b["knowledgeNodeId"] for b in mats[slug].get("bindings") or []]
            after_slug = ", ".join(x.rsplit(":", 1)[-1] for x in after_ids)
            action = d["final_action"]
        else:
            after_ids, after_slug, action = [], "DROPPED", "DROP_MATERIAL"
        rows.append({
            "material_slug": slug, "layer": item["layer"], "subject": item["subject"],
            "judge_verdict": item["verdict"], "final_action": action,
            "before_node_slug": item["current_node_slug"],
            "before_bindings": ";".join(item["before_bindings"]),
            "after_node_slug": after_slug, "after_bindings": ";".join(after_ids),
            "confidence": item["confidence"], "evidence_quote": item["evidence_quote"],
            "override_reason": d.get("override_reason", ""),
        })
    with (HERE / "per-item-before-after.csv").open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    expected_rebind = [r for r in rows if r["final_action"] == "REBIND"]
    expected_drop = [r for r in rows if r["final_action"] == "DROP_MATERIAL"]
    bad_rebind = []
    for r in expected_rebind:
        target = disp[r["material_slug"]]["final_target_node_slug"]
        if r["after_node_slug"] != target:
            bad_rebind.append((r["material_slug"], target, r["after_node_slug"]))
    bad_drop = [r["material_slug"] for r in expected_drop if r["after_node_slug"] != "DROPPED"]

    verify = {
        "counts": {"nodes": len(nodes), "materials": len(mats), "bindings": len(bindings),
                   "materials_before": state["counts"]["materials"]},
        "dangling": dangling, "unbound_materials": unbound,
        "rebind_expected": len(expected_rebind), "drop_expected": len(expected_drop),
        "bad_rebind": bad_rebind, "bad_drop": bad_drop,
        "pack_identity": {
            "packId": pack["packId"], "taxonomyVersion": pack["taxonomyVersion"],
            "packId_eq_taxonomyVersion": pack["packId"] == pack["taxonomyVersion"],
            "schemaVersion": pack["schemaVersion"], "coverage": pack["coverage"],
            "index_packId": index["packId"],
            "index_packId_eq_packId": index["packId"] == pack["packId"],
            "sidecar_count": len(index["sidecars"]),
        },
        "manifest": {
            "packId": manifest["packId"],
            "packId_eq_packId": manifest["packId"] == pack["packId"],
            "schemaVersion": manifest["schemaVersion"], "version": manifest["version"],
            "contentVersion": manifest["contentVersion"],
            "contentVersion_recomputed": update_manifest.content_version(pack, sidecars),
            "retired_count": len(manifest["retired"]),
            "last_retired": manifest["retired"][-1],
        },
        "staging_equals_shipped_bytes": sync,
        "changed_files": changed,
        "pack_sha256": sha(SHIPPED / pack_io.PACK_NAME),
    }
    verify["ok"] = (not dangling and not unbound and not bad_rebind and not bad_drop
                    and sync and not changed_unexpected(changed))
    (HERE / "verify.json").write_text(
        json.dumps(verify, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({k: v for k, v in verify.items() if k != "changed_files"},
                     ensure_ascii=False, indent=1))
    print("changed files:", len(changed))
    for c in changed:
        print("  ", c["kind"], c["file"], c["bytes"])
    return 0 if verify["ok"] else 1


def changed_unexpected(changed: list[dict]) -> list[str]:
    """改动文件必须落在：主包、17 个改绑/删料卷、台账、5 张表之一。"""
    allow_pack = {"moe-2025-four-subjects-v1.json", "moe-2025-update-manifest.json"}
    allow_sidecars = {
        "moe-2025-teaching-support-v2-01.json", "moe-2025-teaching-support-v2-02.json",
        "moe-2025-teaching-support-v2-03.json", "moe-2025-teaching-support-v2-04.json",
        "moe-2025-teaching-support-v2-05.json", "moe-2025-teaching-support-v2-06.json",
        "moe-2025-teaching-support-v2-07.json", "moe-2025-teaching-support-v2-09.json",
        "moe-2025-teaching-support-v2-11.json", "moe-2025-teaching-support-v2-12.json",
        "moe-2025-teaching-support-v2-14.json", "moe-2025-teaching-support-v2-15.json",
        "moe-2025-teaching-support-v2-16.json", "moe-2025-teaching-support-v2-17.json",
        "moe-2025-teaching-support-v2-19.json", "moe-2025-teaching-support-v2-23.json",
        "moe-2025-teaching-support-v2-24.json",
    }
    allow_tables = {p.replace("/", "\\") for p in TABLES} | set(TABLES)
    bad = []
    for c in changed:
        name = c["file"]
        if name in allow_pack or name in allow_sidecars or name in allow_tables:
            continue
        bad.append(name)
    return bad


if __name__ == "__main__":
    raise SystemExit(main())
