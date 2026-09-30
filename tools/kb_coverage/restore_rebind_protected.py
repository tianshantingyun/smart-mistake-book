# -*- coding: utf-8 -*-
"""把「重判替换」误覆盖的**权威改绑材料**还原（改绑表优先）。

## 它消灭的失败

53 优先轮的重判替换把 118 条材料的绑定改成新判定所选节点，而这 118 条**全部**在权威改绑表
`material_rebind.csv`（1,237 行、逐条带 evidence 的裁定）里——等于用模型的新选择覆盖了已裁决的归属，
`test_kb_rebind` 的"改绑表对当前包幂等"契约当场破（实测 0→118 处改动）。
本工具按"改绑表优先"还原：材料记录（字段+绑定）从**参照包**（替换前状态）取回，
判定表里对应的 MATERIAL 行改 `SKIP`（note 记明原因），使包与改绑表重新一致。

## 用法

    python tools/kb_coverage/restore_rebind_protected.py --ref build/agent-input/ref-pack-v5
    python tools/kb_coverage/restore_rebind_protected.py --ref build/agent-input/ref-pack-v5 --write
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import pack_io  # noqa: E402
from kb_coverage import materialize as mz  # noqa: E402

REBIND = REPO / "tools/kb_build/tables/material_rebind.csv"
REPLACEMENTS = REPO / "build/agent-input/rejudged_replacements.csv"
NOTE = "归属以 material_rebind.csv 为准（重判未采用）"


def load_protected() -> dict[str, str]:
    """替换表 ∩ 改绑表 → {material_slug: 改绑表目标节点}。"""
    rebind: dict[str, str] = {}
    with REBIND.open(encoding="utf-8-sig", newline="") as fh:
        for row in csv.DictReader(fh):
            rebind[row["material_slug"]] = row["to_node_slug"]
    if not REPLACEMENTS.exists():
        return {}
    out: dict[str, str] = {}
    with REPLACEMENTS.open(encoding="utf-8-sig", newline="") as fh:
        for row in csv.DictReader(fh):
            slug = row["slug"]
            if slug in rebind:
                out[slug] = rebind[slug]
    return out


def load_ref_records(ref_dir: Path) -> dict[str, dict]:
    """参照包里按 slug 取材料记录（替换前状态）。"""
    out: dict[str, dict] = {}
    for path in sorted(ref_dir.glob("moe-2025-teaching-support-v2-*.json")):
        if path.name.endswith("index.json"):
            continue
        doc = json.loads(path.read_text(encoding="utf-8"))
        for material in doc.get("materials") or []:
            out[material["slug"]] = material
    return out


def restore(ref_dir: Path, write: bool) -> dict:
    protected = load_protected()
    ref = load_ref_records(ref_dir)
    state = mz._sidecar_state()
    index: dict[str, tuple[Path, dict]] = {}
    for path, doc in state["docs"].items():
        for material in doc["materials"]:
            index[material["slug"]] = (path, material)
    restored = missing = 0
    changed: dict[Path, dict] = {}
    for slug, target in protected.items():
        hit = index.get(slug)
        old = ref.get(slug)
        if hit is None or old is None:
            missing += 1
            continue
        path, material = hit
        if material.get("bindings") and material["bindings"][0]["knowledgeNodeId"].split(":")[-1] == target:
            continue  # 幂等
        material.clear()
        material.update(json.loads(json.dumps(old)))
        changed.setdefault(path, state["docs"][path])
        restored += 1
    # 判定表：对应行转 SKIP
    rows: list[dict] = []
    with mz.JUDGMENTS.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        cols = reader.fieldnames or []
        rows = list(reader)
    subjects = {}
    from kb_coverage.pool_path import POOL_PATH
    with POOL_PATH.open(encoding="utf-8") as fh:
        for line in fh:
            if not line.strip():
                continue
            d = json.loads(line)
            subjects[(d["rel_path"], d["chunk_id"])] = d.get("subject") or ""
    from kb_coverage.apply_rejudged_materials import material_slug
    flipped = 0
    for row in rows:
        if (row.get("action") or "") != "MATERIAL":
            continue
        subject = subjects.get((row["chunk_rel"], row["chunk_id"]), "")
        if subject not in mz.SUBJ3:
            continue
        if material_slug(subject, row["chunk_id"], row.get("midx") or "") not in protected:
            continue
        row["action"] = "SKIP"
        row["note"] = NOTE
        for field in ("node_slug", "type", "title", "summary", "applicability", "content", "boundary"):
            row[field] = ""
        flipped += 1
    if write:
        for path, doc in changed.items():
            pack_io.dump_json(doc, path)
        with mz.JUDGMENTS.open("w", encoding="utf-8", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=cols)
            writer.writeheader()
            writer.writerows(rows)
    return {"protected": len(protected), "restored": restored, "flipped_rows": flipped,
            "missing": missing}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--ref", type=Path, required=True, help="替换前状态的参照包目录")
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)
    stats = restore(args.ref, args.write)
    print(f"受保护 {stats['protected']} 条；还原 {stats['restored']} 条；判定表转 SKIP {stats['flipped_rows']} 行"
          f"；缺参照 {stats['missing']}")
    if not args.write:
        print("（dry-run：未写盘；加 --write 落盘）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
