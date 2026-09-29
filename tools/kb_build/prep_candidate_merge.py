# -*- coding: utf-8 -*-
"""候选区合并（第 1 段 1a）只读准备：把两层候选的裁决输入落成统一口径的批次文件。

## 它消灭的失败

裁决代理此前各自从 4 个候选 JSON + 48MB 侧车里现取现算：归一化口径不一、
机械命中要重算且可能算得不一样、"这个节点已有哪些材料"每次都要重新翻侧车。
本工具把这些一次性算清、只读落盘（不读也不写 staging 之外的任何权威表），
代理只读批次文件即可开工——口径唯一、可复核、可重放。

## 两层输入

- 目录层：`knowledge-research/candidates/<subj>/knowledge-directory-summary.json`
  的 `nodes[]`（考点名+别名+定义+来源专题），共 3,536 条。定义是学生版填空式
  原文，**只能当"节点名/覆盖证据"用，不得当材料**（教材原文不入库）。
- 材料层：`<subj>/teaching-support-candidates.json` 的 `materials[]`，共 10,356 条。
  与成品侧车按 slug 对齐后分三类：已在包 / 有差异 / 未入包。

## 产出（默认 dry-run 只报数；--write 落盘）

`knowledge-production/candidate-merge/` 下：`dir_entries.jsonl`（逐条裁决输入，
命中节点时内联该节点与其材料摘要）、`dir_batches/batch_NNN.jsonl`（按 80 条切批）、
`mat_entries.jsonl`（未入包材料，逐条）、`mat_ops.csv`（10,356 条分类）、`summary.json`。
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import unicodedata
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import pack_io  # noqa: E402

CANDIDATES = Path("knowledge-research") / "candidates"
OUT_DIR = Path("knowledge-production") / "candidate-merge"
SUBJECTS = (("math", "MATH"), ("physics", "PHYSICS"), ("chemistry", "CHEMISTRY"),
            ("biology", "BIOLOGY"))
BATCH_SIZE = 80
LONG_NAME = 16
_STAR = "\u2605\u2606"
_WS = re.compile(r"\s+")


def norm(text: str) -> str:
    """口径唯一：NFKC 全半角归一 + 去所有空白（不剥其它字符，禁模糊匹配）。"""
    return _WS.sub("", unicodedata.normalize("NFKC", text or ""))


def strip_star(text: str) -> str:
    return (text or "").translate({ord(c): None for c in _STAR})


def load_pack_index(root: Path):
    """{subject: {"names": {norm: [slug...]}, "aliases": {norm: [slug...]}}} + 节点表。"""
    pack = pack_io.load_json(pack_io.pack_path())
    index: dict[str, dict] = {}
    nodes: dict[tuple[str, str], dict] = {}
    for subject, _topic, point in pack_io.iter_points(pack):
        bucket = index.setdefault(subject, {"names": {}, "aliases": {}})
        bucket["names"].setdefault(norm(point["name"]), []).append(point["slug"])
        for alias in point.get("aliases") or []:
            bucket["aliases"].setdefault(norm(alias), []).append(point["slug"])
        nodes[(subject, point["slug"])] = point
    return index, nodes


def load_node_materials(root: Path):
    """{（subject, slug): [{title, summary, content, type}...]}，按绑定取前 4 条。"""
    out: dict[tuple[str, str], list[dict]] = {}
    for path in pack_io.sidecar_paths():
        data = pack_io.load_json(path)
        for mat in data.get("materials") or []:
            for binding in mat.get("bindings") or []:
                node = binding.get("knowledgeNodeId") or ""
                parts = node.split(":")
                if len(parts) < 5:
                    continue
                key = (parts[-3].upper(), parts[-1])
                bucket = out.setdefault(key, [])
                if len(bucket) < 4:
                    bucket.append({
                        "slug": mat.get("slug"),
                        "type": mat.get("type"),
                        "title": mat.get("title"),
                        "summary": (mat.get("summaryMarkdown") or "")[:400],
                        "content": (mat.get("contentMarkdown") or "")[:400],
                    })
    return out


def load_dir_entries(root: Path):
    entries = []
    idx = 0
    for folder, subject in SUBJECTS:
        path = root / CANDIDATES / folder / "knowledge-directory-summary.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        for node in data.get("nodes") or []:
            entries.append({
                "idx": idx,
                "subject": subject,
                "name": node.get("name") or "",
                "aliases": node.get("aliases") or [],
                "definition": node.get("definition") or "",
                "topic_id": node.get("topicId") or "",
                "source": node.get("source") or "",
                "source_file": node.get("sourceFile") or "",
            })
            idx += 1
    return entries


def load_candidate_materials(root: Path):
    materials = []
    for folder, subject in SUBJECTS:
        path = root / CANDIDATES / folder / "teaching-support-candidates.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        for mat in data.get("materials") or []:
            materials.append(mat)
    return materials


def release_materials_by_slug(root: Path):
    out: dict[str, dict] = {}
    for path in pack_io.sidecar_paths():
        data = pack_io.load_json(path)
        for mat in data.get("materials") or []:
            out[mat.get("slug") or ""] = mat
    return out


def classify_dir_entries(entries, index, nodes, node_materials):
    stats = {"name_hit": 0, "alias_hit": 0, "star_hit": 0, "unmatched": 0,
             "star": 0, "long": 0, "ambiguous": 0}
    for entry in entries:
        subject = entry["subject"]
        bucket = index.get(subject, {"names": {}, "aliases": {}})
        name = norm(entry["name"])
        hit_slugs: list[str] = []
        mech = "unmatched"
        if name in bucket["names"]:
            mech = "name_hit"
            hit_slugs = bucket["names"][name]
        elif name in bucket["aliases"]:
            mech = "alias_hit"
            hit_slugs = bucket["aliases"][name]
        else:
            stripped = norm(strip_star(entry["name"]))
            if stripped in bucket["names"]:
                mech = "star_hit"
                hit_slugs = bucket["names"][stripped]
            elif stripped in bucket["aliases"]:
                mech = "star_hit"
                hit_slugs = bucket["aliases"][stripped]
        entry["norm_name"] = name
        entry["mech"] = mech
        entry["star"] = any(c in entry["name"] for c in _STAR)
        entry["long"] = len(name) > LONG_NAME
        entry["node_slugs"] = hit_slugs
        if len(hit_slugs) > 1:
            stats["ambiguous"] += 1
        stats[mech] += 1
        if entry["star"]:
            stats["star"] += 1
        if entry["long"]:
            stats["long"] += 1
        if hit_slugs:
            slug = hit_slugs[0]
            point = nodes.get((subject, slug)) or {}
            entry["node"] = {
                "slug": slug,
                "name": point.get("name"),
                "kind": point.get("kind"),
                "boundary": (point.get("boundary") or "")[:300],
                "aliases": (point.get("aliases") or [])[:10],
            }
            entry["node_materials"] = node_materials.get((subject, slug), [])
        else:
            entry["node"] = None
            entry["node_materials"] = []
    return stats


def classify_materials(candidates, release):
    rows = []
    missing = []
    stats = {"already_in": 0, "divergent": 0, "missing": 0}
    for mat in candidates:
        slug = mat.get("slug") or ""
        rel = release.get(slug)
        if rel is None:
            stats["missing"] += 1
            missing.append(mat)
            rows.append({"slug": slug, "subject": mat.get("subject"), "status": "missing"})
            continue
        diff_fields = []
        for field in ("title", "summaryMarkdown", "contentMarkdown",
                      "boundaryMarkdown", "applicabilityMarkdown"):
            if (mat.get(field) or "") != (rel.get(field) or ""):
                diff_fields.append(field)
        status = "divergent" if diff_fields else "already_in"
        stats[status] += 1
        rows.append({"slug": slug, "subject": mat.get("subject"), "status": status,
                     "diff_fields": ";".join(diff_fields)})
    return rows, missing, stats


def main(argv=None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="落盘；默认只报数")
    parser.add_argument("--root", type=Path, default=None,
                        help="候选树与产物的根（默认仓库根；测试夹具用）")
    parser.add_argument("--out", default=str(OUT_DIR))
    parser.add_argument("--batch-size", type=int, default=BATCH_SIZE)
    args = parser.parse_args(argv)

    root = args.root if args.root is not None else pack_io.REPO
    out_dir = root / args.out
    index, nodes = load_pack_index(root)
    node_materials = load_node_materials(root)
    entries = load_dir_entries(root)
    dir_stats = classify_dir_entries(entries, index, nodes, node_materials)
    candidates = load_candidate_materials(root)
    release = release_materials_by_slug(root)
    mat_rows, missing, mat_stats = classify_materials(candidates, release)

    print(f"目录层 {len(entries)} 条：{dir_stats}")
    print(f"材料层 {len(candidates)} 条：{mat_stats}")
    if not args.write:
        print("（dry-run：未落盘；加 --write 落盘）")
        return 0

    out_dir.mkdir(parents=True, exist_ok=True)
    with (out_dir / "dir_entries.jsonl").open("w", encoding="utf-8") as fh:
        for entry in entries:
            fh.write(json.dumps(entry, ensure_ascii=False) + "\n")
    batches = out_dir / "dir_batches"
    batches.mkdir(exist_ok=True)
    for start in range(0, len(entries), args.batch_size):
        chunk = entries[start:start + args.batch_size]
        name = f"batch_{start // args.batch_size:03d}.jsonl"
        with (batches / name).open("w", encoding="utf-8") as fh:
            for entry in chunk:
                fh.write(json.dumps(entry, ensure_ascii=False) + "\n")
    with (out_dir / "mat_entries.jsonl").open("w", encoding="utf-8") as fh:
        for mat in missing:
            fh.write(json.dumps(mat, ensure_ascii=False) + "\n")
    with (out_dir / "mat_ops.csv").open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=["slug", "subject", "status", "diff_fields"])
        writer.writeheader()
        for row in mat_rows:
            row.setdefault("diff_fields", "")
            writer.writerow(row)
    summary = {
        "dir_total": len(entries), "dir": dir_stats,
        "material_total": len(candidates), "material": mat_stats,
        "batches": (len(entries) + args.batch_size - 1) // args.batch_size,
        "batch_size": args.batch_size,
    }
    with (out_dir / "pack_nodes.csv").open("w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["subject", "slug", "name", "kind", "aliases", "boundary"])
        for (subject, slug), point in sorted(nodes.items()):
            writer.writerow([subject, slug, point.get("name"), point.get("kind"),
                             "|".join(point.get("aliases") or []),
                             (point.get("boundary") or "")[:200]])
    (out_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"已写入 {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
