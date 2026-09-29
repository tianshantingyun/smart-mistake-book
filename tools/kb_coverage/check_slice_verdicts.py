# -*- coding: utf-8 -*-
"""切片判定自检门：判定员产物（judgment-verdicts/*.csv）收拢前的独立复算。

## 它消灭的失败

判定员各自写 CSV，自检是自述；任何一批写错表头/漏行/给不存在的节点/写非法 type，
到了 `materialize --write` 会变成悬空绑定或整批硬失败（旧通道的老失败形态）。
本门按**切片文件**逐批复算：行数、表头、action 枚举、MATERIAL 五字段与三值 type、
节点在包里真实存在（按科）、NEW 提案格式、无 ASCII 双引号、content 按字面 `\\n` ≤4 段。

## 用法

    python tools/kb_coverage/check_slice_verdicts.py [--dir knowledge-production/judgment-verdicts]
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import pack_io  # noqa: E402

SLICES = REPO / "tools/kb_coverage/tables/judgment_slices"
COLS = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title", "summary",
        "applicability", "content", "boundary", "note", "midx"]
TYPES = {"CONCEPT_EXPLANATION", "METHOD_MODEL", "MISCONCEPTION_GUIDE"}
_NEW = re.compile(r"NEW:([A-Z]+)/(\S+)")


def _pack_nodes() -> dict[str, set[str]]:
    pack = pack_io.load_json(pack_io.pack_path())
    out: dict[str, set[str]] = {}
    for subject, _topic, point in pack_io.iter_points(pack):
        out.setdefault(subject, set()).add(point["slug"])
    return out


def check_one(csv_path: Path, slice_path: Path, nodes: dict[str, set[str]]) -> list[str]:
    errors: list[str] = []
    with csv_path.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        cols = reader.fieldnames or []
        rows = list(reader)
    if cols != COLS:
        return [f"表头不符：{cols}"]
    want = 0
    slice_keys: set[tuple[str, str]] = set()
    subject_of: dict[tuple[str, str], str] = {}
    for line in slice_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        import json as _json
        rec = _json.loads(line)
        want += 1
        slice_keys.add((rec["chunk_rel"], rec["chunk_id"]))
        subject_of[(rec["chunk_rel"], rec["chunk_id"])] = rec.get("subject") or ""
    if len(rows) != want:
        errors.append(f"行数 {len(rows)} ≠ 切片 {want}")
    seen: set[tuple[str, str]] = set()
    for row in rows:
        key = (row.get("chunk_rel") or "", row.get("chunk_id") or "")
        tag = f"{key[0].split('/')[-1]}#{key[1]}"
        if key not in slice_keys:
            errors.append(f"{tag}: 键不在切片里")
        elif key in seen:
            errors.append(f"{tag}: 键重复")
        seen.add(key)
        action = (row.get("action") or "").strip()
        if action not in ("MATERIAL", "SKIP"):
            errors.append(f"{tag}: 非法 action {action!r}")
            continue
        if action == "SKIP":
            if not (row.get("note") or "").strip():
                errors.append(f"{tag}: SKIP 缺 note 理由")
            m = _NEW.search(row.get("note") or "")
            if m and m.group(1) not in nodes:
                errors.append(f"{tag}: NEW 提案的科目 {m.group(1)} 非法")
            continue
        subject = subject_of.get(key, "")
        node = (row.get("node_slug") or "").strip()
        if node not in nodes.get(subject, set()):
            errors.append(f"{tag}: 节点不存在 {subject}/{node}")
        if (row.get("type") or "").strip() not in TYPES:
            errors.append(f"{tag}: 非法 type {(row.get('type') or '').strip()!r}")
        for field in ("title", "summary", "applicability", "content", "boundary"):
            if not (row.get(field) or "").strip():
                errors.append(f"{tag}: 缺 {field}")
        content = row.get("content") or ""
        if len(content.split("\\n")) > 4:
            errors.append(f"{tag}: content 超过 4 段")
        if (row.get("boundary") or "").startswith("定位："):
            errors.append(f"{tag}: boundary 是定位占位")
        for field in ("title", "summary", "applicability", "content", "boundary"):
            if '"' in (row.get(field) or ""):
                errors.append(f"{tag}: {field} 含 ASCII 双引号")
    return errors


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dir", type=Path, default=REPO / "knowledge-production/judgment-verdicts")
    args = ap.parse_args(argv)
    nodes = _pack_nodes()
    files = sorted(args.dir.glob("*.csv"))
    failures: list[tuple[str, list[str]]] = []
    stats = {"files": 0, "rows": 0, "material": 0, "skip": 0, "new_proposals": 0}
    for path in files:
        slice_path = SLICES / path.name[:-4]
        if not slice_path.exists():
            failures.append((path.name, [f"找不到切片 {slice_path.name}"]))
            continue
        stats["files"] += 1
        errors = check_one(path, slice_path, nodes)
        rows = list(csv.DictReader(path.open(encoding="utf-8-sig", newline="")))
        stats["rows"] += len(rows)
        stats["material"] += sum(1 for r in rows if (r.get("action") or "") == "MATERIAL")
        stats["skip"] += sum(1 for r in rows if (r.get("action") or "") == "SKIP")
        stats["new_proposals"] += sum(1 for r in rows if "NEW:" in (r.get("note") or ""))
        if errors:
            failures.append((path.name, errors))
    print(f"已检 {stats['files']} 个判定文件 / {stats['rows']} 块："
          f"MATERIAL {stats['material']}、SKIP {stats['skip']}、NEW 提案 {stats['new_proposals']}")
    print(f"不过 {len(failures)} 个")
    for name, errors in failures:
        print(f"  ✗ {name}")
        for err in errors[:6]:
            print(f"      {err}")
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
