# -*- coding: utf-8 -*-
"""内容绑定裁定的"判定工具包"：把候选行需要的两侧证据压成小文件，给判定代理读。

它消灭的失败（2026-10-02 实测）：裁定一条候选需要**材料正文**（按 slug 落在 19 个
11 MB 的侧车里）与**两侧节点**（名/边界/别名，在主包）——每个判定代理自己去 grep
会把同一份数据读十几遍（慢且易读错版本）。本工具一次读齐、按批切件，
代理只需读几十 KB 的 JSON。

输入 = `audit_content_bindings --write [--out …]` 的候选表（八列）。
输出 = `<out-dir>/kit-<NN>.json`，每个是数组，元素含：
  idx（原表行号，从 0 起）/ slice / subject / material（slug/title/type/正文摘要与正文）/
  current（当前节点 slug/name/boundary/aliases）/ target（靶区节点，同上）/
  suggested（审计建议的目标节点，可为空）/ audit_note（审计的机械理由，**只作线索**）。

用法：
    PYTHONPATH=tools python -m kb_build.make_content_audit_kit \
        --candidates build/agent-batch2/content-audit-round2-candidates.csv \
        --out-dir build/agent-batch2/kit --per-file 25

只读：本工具不写包、不写表，只写 out-dir。
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

from kb_build import pack_io

DEFAULT_OUT = "build/content-audit-kit"
MAX_CONTENT = 1200  # 每条材料的正文上限（字符）；超出截断并标注


def load_nodes() -> dict[tuple[str, str], dict]:
    pack = pack_io.load_json(pack_io.pack_path())
    out: dict[tuple[str, str], dict] = {}
    for subject in pack["subjects"]:
        name = subject["subject"]

        def walk(objs) -> None:
            for topic in objs:
                for point in topic.get("knowledgePoints") or []:
                    out[(name, point["slug"])] = {
                        "slug": point["slug"],
                        "name": point["name"],
                        "boundary": point.get("boundary") or "",
                        "aliases": point.get("aliases") or [],
                    }
                walk(topic.get("topics") or [])

        walk(subject["topics"])
    return out


def load_materials() -> dict[str, dict]:
    out: dict[str, dict] = {}
    for path in pack_io.sidecar_paths():
        for material in pack_io.load_json(path).get("materials") or []:
            out[material["slug"]] = material
    return out


def material_view(material: dict | None) -> dict:
    if material is None:
        return {"missing": True}
    text = (material.get("contentMarkdown") or "")
    truncated = len(text) > MAX_CONTENT
    return {
        "slug": material.get("slug"),
        "title": material.get("title") or "",
        "type": material.get("type") or "",
        "summary": material.get("summaryMarkdown") or "",
        "content": text[:MAX_CONTENT],
        "content_truncated": truncated,
        "content_length": len(text),
        "source": material.get("sourceLocator") or "",
        "bound_nodes": sorted({b["knowledgeNodeId"].rsplit(":", 1)[-1]
                               for b in material.get("bindings") or []}),
    }


def build_records(rows: list[dict], nodes: dict, materials: dict) -> list[dict]:
    records = []
    for idx, row in enumerate(rows):
        subject = row["subject"].strip()
        current = nodes.get((subject, row["current_node_slug"].strip()))
        target = nodes.get((subject, row["slug"].strip()))
        suggested_slug = row["suggested_node_slug"].strip()
        records.append({
            "idx": idx,
            "slice": row["slice"].strip(),
            "subject": subject,
            "material": material_view(materials.get(row["material_slug"].strip())),
            "current": current or {"slug": row["current_node_slug"].strip(), "missing": True},
            "target": target or {"slug": row["slug"].strip(), "missing": True},
            "suggested": (nodes.get((subject, suggested_slug)) or {"slug": suggested_slug}) if suggested_slug else None,
            "audit_note": row["evidence"].strip(),
        })
    return records


def write_kits(records: list[dict], out_dir: Path, per_file: int) -> list[Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    paths = []
    for start in range(0, len(records), per_file):
        chunk = records[start:start + per_file]
        path = out_dir / f"kit-{len(paths) + 1:02d}.json"
        path.write_text(json.dumps(chunk, ensure_ascii=False, indent=1), encoding="utf-8")
        paths.append(path)
    return paths


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--out-dir", type=Path, default=Path(DEFAULT_OUT))
    parser.add_argument("--per-file", type=int, default=25)
    args = parser.parse_args(argv)

    if not args.candidates.exists():
        print(f"候选表不在场：{args.candidates}", file=sys.stderr)
        return 2
    with args.candidates.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        print("候选表为空", file=sys.stderr)
        return 2

    nodes = load_nodes()
    materials = load_materials()
    records = build_records(rows, nodes, materials)
    paths = write_kits(records, args.out_dir, args.per_file)
    print(f"候选 {len(records)} 行 → {len(paths)} 件（每件 ≤{args.per_file} 条）：")
    for path in paths:
        print(f"  {path.as_posix()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
