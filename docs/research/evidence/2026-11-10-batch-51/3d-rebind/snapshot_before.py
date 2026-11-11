# -*- coding: utf-8 -*-
"""批次51 G1 3D 改绑：改前快照（只读）。

读出 shipped + staging 两套包家族文件的 sha256/字节数、材料/绑定计数、
以及 B50-D1 errors.csv 43 条材料的改前绑定。不写包，只写证据目录。
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
from kb_build import pack_io  # noqa: E402

ERRORS = REPO / "docs/research/evidence/2026-11-09-batch-50/d1/errors.csv"
SHIPPED = REPO / "core/data/src/main/resources/knowledge"
STAGING = REPO / "build/kb-staging"
TABLE_FILES = [
    REPO / "tools/kb_build/tables/material_rebind.csv",
    REPO / "tools/kb_build/tables/material_bindings.csv",
]


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def family(root: Path) -> list[Path]:
    names = ["moe-2025-four-subjects-v1.json", "moe-2025-teaching-support-v2-index.json",
             "moe-2025-update-manifest.json"]
    names += [f"moe-2025-teaching-support-v2-{i:02d}.json" for i in range(1, 26)]
    return [root / n for n in names if (root / n).exists()]


def main() -> int:
    pack_io.use_directory(STAGING)
    try:
        pack = pack_io.load_json(STAGING / pack_io.PACK_NAME)
        mats = pack_io.load_materials()
        nodes = [(s["subject"], k["slug"]) for s in pack["subjects"]
                 for t in s["topics"] for k in t.get("knowledgePoints") or []]
    finally:
        pack_io.reset_directory()

    rows = list(csv.DictReader(ERRORS.open(encoding="utf-8-sig", newline="")))
    by_slug = {m["slug"]: (p, m) for p, m in mats}

    hashes = []
    for label, root in (("shipped", SHIPPED), ("staging", STAGING)):
        for p in family(root):
            hashes.append(f"{label}\t{p.relative_to(root)}\t{p.stat().st_size}\t{sha(p)}")
    for p in TABLE_FILES:
        hashes.append(f"table\t{p.relative_to(REPO)}\t{p.stat().st_size}\t{sha(p)}")
    (HERE / "before-hashes.tsv").write_text("\n".join(hashes) + "\n", encoding="utf-8")

    per_item = []
    for r in rows:
        slug = r["material_slug"]
        path, mat = by_slug[slug]
        per_item.append({
            "material_slug": slug,
            "layer": r["layer"],
            "subject": r["subject"],
            "verdict": r["verdict"],
            "current_node_slug": r["current_node_slug"],
            "target_node_slug": r["target_node_slug"],
            "confidence": r["confidence"],
            "evidence_quote": r["evidence_quote"],
            "sidecar": path.name,
            "before_bindings": [b["knowledgeNodeId"] for b in mat.get("bindings") or []],
        })
    state = {
        "pack": {
            "packId": pack["packId"],
            "taxonomyVersion": pack["taxonomyVersion"],
            "schemaVersion": pack["schemaVersion"],
            "coverage": pack["coverage"],
            "sourceUri": pack["sourceUri"],
            "sha256": sha(STAGING / pack_io.PACK_NAME),
        },
        "counts": {
            "nodes": len(nodes),
            "materials": len(mats),
            "bindings": sum(len(m.get("bindings") or []) for _p, m in mats),
            "materials_without_binding": sum(1 for _p, m in mats if not (m.get("bindings") or [])),
        },
        "items": per_item,
    }
    (HERE / "before-state.json").write_text(
        json.dumps(state, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(state["counts"], ensure_ascii=False))
    print(f"nodes={len(nodes)} items={len(per_item)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
