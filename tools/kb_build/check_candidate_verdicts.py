# -*- coding: utf-8 -*-
"""裁决表自检门：收拢（merge_candidate_verdicts）之前独立复算全部批次产物。

## 它消灭的失败

裁决批自带自检，但那是被检者的自述——本仓库的纪律是"确定性命令自复算，不转述子代理结论"。
45 个目录批 + 5 个材料批里任何一批写错表头、漏行、写了非法 verdict、给了不存在的绑定节点，
到了写入窗口就会变成悬空绑定或假节点（旧通道"手工改包不可重放"的同类失败）。
本工具把"能不能收拢"变成一条可复跑的确定性门：全过才允许进 merge_candidate_verdicts。

## 用法

    python tools/kb_build/check_candidate_verdicts.py        # 全过 exit 0；有任何一批不过 exit 1
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import pack_io  # noqa: E402

BASE = Path("knowledge-production") / "candidate-merge"
DIR_VERDICTS = ("COVERED", "GAP", "NEW_POINT", "MATERIAL_ONLY", "REJECT", "NEEDS_REVIEW")
MAT_VERDICTS = ("PROMOTE", "MERGE_INTO", "REJECT", "NEEDS_REVIEW")
TYPES = {"CONCEPT_EXPLANATION", "METHOD_MODEL", "MISCONCEPTION_GUIDE"}
KINDS = {"CONCEPT", "PROCEDURE", "REASONING", "REPRESENTATION", "EXPERIMENT"}
DIR_COLS = ["idx", "subject", "name", "verdict", "node_slug", "point_name", "kind",
            "parent_hint", "material_title", "material_type", "material_summary",
            "material_content", "material_boundary", "evidence", "reason"]
MAT_COLS = ["idx", "slug", "subject", "verdict", "node_slug", "covering_material_slug",
            "adjudicated_type", "material_title", "material_content", "material_boundary",
            "evidence", "reason"]
_MAT_VERDICTS_NEEDING_DRAFT = {"GAP", "MATERIAL_ONLY", "NEW_POINT"}


def _pack_nodes() -> set[tuple[str, str]]:
    pack = pack_io.load_json(pack_io.pack_path())
    return {(s, p["slug"]) for s, _t, p in pack_io.iter_points(pack)}


def check_dir_batch(path: Path, batch_path: Path, nodes: set[tuple[str, str]]) -> list[str]:
    errors: list[str] = []
    want = sum(1 for line in batch_path.read_text(encoding="utf-8").splitlines() if line.strip())
    with path.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        cols = reader.fieldnames or []
        rows = list(reader)
    if cols != DIR_COLS:
        return [f"表头不符：{cols}"]
    if len(rows) != want:
        errors.append(f"行数 {len(rows)} ≠ 批输入 {want}")
    for row in rows:
        tag = f"idx={row.get('idx')}"
        verdict = (row.get("verdict") or "").strip()
        if verdict not in DIR_VERDICTS:
            errors.append(f"{tag}: 非法 verdict {verdict!r}")
            continue
        if not (row.get("evidence") or "").strip():
            errors.append(f"{tag}: evidence 为空")
        if verdict in _MAT_VERDICTS_NEEDING_DRAFT:
            for field in ("material_title", "material_type", "material_content"):
                if not (row.get(field) or "").strip():
                    errors.append(f"{tag}: {verdict} 缺 {field}")
            if (row.get("material_type") or "").strip() not in TYPES:
                errors.append(f"{tag}: material_type {(row.get('material_type') or '').strip()!r} 非法")
            if verdict in ("GAP", "MATERIAL_ONLY"):
                node = (row.get("node_slug") or "").strip()
                if (row.get("subject"), node) not in nodes:
                    errors.append(f"{tag}: 绑定节点不存在 {node!r}")
        if verdict == "NEW_POINT":
            if not (row.get("point_name") or "").strip():
                errors.append(f"{tag}: NEW_POINT 缺 point_name")
            if (row.get("kind") or "").strip() not in KINDS:
                errors.append(f"{tag}: kind {(row.get('kind') or '').strip()!r} 非法")
        if verdict in ("REJECT", "NEEDS_REVIEW") and not (row.get("reason") or "").strip():
            errors.append(f"{tag}: {verdict} 缺 reason")
        content = (row.get("material_content") or "")
        if content and len(content.split("\\n")) > 4:
            errors.append(f"{tag}: material_content 超过 4 行")
        for field in ("material_title", "material_summary", "material_content", "material_boundary"):
            if '"' in (row.get(field) or ""):
                errors.append(f"{tag}: {field} 含 ASCII 双引号")
        title = (row.get("material_title") or "")
        if title.startswith("例") or "典例" in title:
            errors.append(f"{tag}: material_title 是例题")
    return errors


def check_mat_part(path: Path, start: int, end: int, nodes: set[tuple[str, str]]) -> list[str]:
    errors: list[str] = []
    with path.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        cols = reader.fieldnames or []
        rows = list(reader)
    if cols != MAT_COLS:
        return [f"表头不符：{cols}"]
    if len(rows) != end - start + 1:
        errors.append(f"行数 {len(rows)} ≠ 区间 {start}-{end}")
    for row in rows:
        tag = f"idx={row.get('idx')}"
        verdict = (row.get("verdict") or "").strip()
        if verdict not in MAT_VERDICTS:
            errors.append(f"{tag}: 非法 verdict {verdict!r}")
            continue
        if verdict == "PROMOTE":
            node = (row.get("node_slug") or "").strip()
            if (row.get("subject"), node) not in nodes:
                errors.append(f"{tag}: 绑定节点不存在 {node!r}")
            for field in ("material_title", "material_content"):
                if not (row.get(field) or "").strip():
                    errors.append(f"{tag}: PROMOTE 缺 {field}")
            if (row.get("adjudicated_type") or "").strip() not in TYPES:
                errors.append(f"{tag}: adjudicated_type 非法")
        elif verdict == "MERGE_INTO" and not (row.get("covering_material_slug") or "").strip():
            errors.append(f"{tag}: MERGE_INTO 缺 covering_material_slug")
        elif verdict in ("REJECT", "NEEDS_REVIEW") and not (row.get("reason") or "").strip():
            errors.append(f"{tag}: {verdict} 缺 reason")
    return errors


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--base", type=Path, default=None)
    args = ap.parse_args(argv)
    root = pack_io.REPO
    base = args.base or (root / BASE)
    nodes = _pack_nodes()
    verdicts = base / "verdicts"
    batches = base / "dir_batches"
    failures: list[tuple[str, list[str]]] = []
    checked = 0
    for path in sorted(verdicts.glob("dir_batch_*.csv")):
        name = path.stem.replace("dir_", "")
        batch = batches / f"{name}.jsonl"
        if not batch.exists():
            failures.append((path.name, [f"找不到批输入 {batch.name}"]))
            continue
        checked += 1
        errors = check_dir_batch(path, batch, nodes)
        if errors:
            failures.append((path.name, errors))
    for path in sorted(verdicts.glob("mat_*.csv")):
        m = re.fullmatch(r"mat_(\d+)_(\d+)", path.stem)
        if not m:
            failures.append((path.name, ["文件名不是 mat_<start>_<end>.csv"]))
            continue
        checked += 1
        errors = check_mat_part(path, int(m.group(1)), int(m.group(2)), nodes)
        if errors:
            failures.append((path.name, errors))
    print(f"已检 {checked} 个裁决文件；不过 {len(failures)} 个")
    for name, errors in failures:
        print(f"  ✗ {name}")
        for err in errors[:6]:
            print(f"      {err}")
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
