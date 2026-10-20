# -*- coding: utf-8 -*-
"""判定源 ↔ 包文本漂移检查：`material_judgments/*.csv` 与包内同 slug 材料的 content/boundary 对拍。

## 它消灭的失败

判定表是教学材料入库的**判定源**（`materialize.py` 按行落成 sidecar 材料）；
sidecar 是**实际分发**的文本。两者是同一条材料的两份拷贝，但更新路径不唯一：
文本修复/边界修复可以只写 sidecar，也可以只写判定表。只要有人只改一边，
"看判定表复核"与"端上实际读到的内容"就会分叉——而且两边都各自自洽，现有
内容门（只查包、或只查表）都看不出来。

本检查按**同一个键**（material slug，生成公式见 `judgments.material_slug`，
与 `materialize.write` 同源）把两份文本逐字对齐：

- `content`（strip 后）↔ `material.contentMarkdown`（strip 后）
- `boundary`（strip 后；空则回退 `summary.strip()`，与 materialize 的写入规则一致）
  ↔ `material.boundaryMarkdown`

`strip` 不是放宽：materialize 落盘时本来就调用 `.strip()`，比较的是**落盘后的
同一形态**；strip 之后仍逐字比较（含标点、空白、Markdown 标记）。

## 读数口径

`value = content 差异字段数 + boundary 差异字段数 + 判定行有/包内无条数`。
- 文本差异：同 slug 但 content/boundary 不等，容差 0；
- 判定行有/包内无：MATERIAL 行算出了 slug，包里没有这条材料——"查不了"不能判绿；
- 包内 `ext-*` 材料没有对应 MATERIAL 判定行：只作提示，不判红（材料也可能来自
  authored/registry 批次；其中同一 slug 的判定行是 SKIP 的，说明"源已判不入库、
  包里还留着"，会在 detail 里单独报数）。

## 用法

    PYTHONPATH=tools python -m kb_coverage.judgment_drift
    PYTHONPATH=tools python -m kb_coverage.judgment_drift --json
    PYTHONPATH=tools python -m kb_coverage.judgment_drift --root build/kb-staging
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
if str(REPO / "tools") not in sys.path:
    sys.path.insert(0, str(REPO / "tools"))

from kb_build import pack_io  # noqa: E402
from kb_coverage import judgments  # noqa: E402

SLUG_PREFIXES = tuple(f"ext-{subj3}-" for subj3 in judgments.SUBJ3.values())


def load_rows() -> list[dict]:
    """判定表全部行（5 片拼接；与 materialize / check_exam_leak 同一读法）。"""
    return judgments.load_all()


def load_pack_materials() -> list[dict]:
    """当前工作目录（staging 或 --root/成品）的全部材料。"""
    return [material for _path, material in pack_io.load_materials()]


def expected_text(row: dict) -> tuple[str, str]:
    """判定行落盘后的 (contentMarkdown, boundaryMarkdown)，与 materialize.py 逐条同源。"""
    summary = (row.get("summary") or "").strip()
    content = (row.get("content") or "").strip()
    boundary = (row.get("boundary") or "").strip() or summary
    return content, boundary


def compare(rows: list[dict], materials: list[dict]) -> dict:
    """对拍判定表 MATERIAL 行与包内材料（纯函数，便于测试）。

    返回：
      material_rows / judged_slugs / duplicate_slugs / matched
      content_diffs / boundary_diffs / missing / unlocatable
      orphan_ext / orphan_ext_skip / orphan_examples
      value = len(content_diffs) + len(boundary_diffs) + len(missing) + len(unlocatable)
    """
    by_slug = {m["slug"]: m for m in materials if m.get("slug")}
    material_rows = [
        r for r in rows if (r.get("action") or "").strip() == "MATERIAL"
    ]

    material_actions: dict[str, set[str]] = {}
    for r in rows:
        slug = judgments.material_slug(
            r, judgments.part_of(r.get("chunk_rel") or ""))
        if slug is None:
            continue
        material_actions.setdefault(slug, set()).add(
            (r.get("action") or "").strip())

    content_diffs: list[dict] = []
    boundary_diffs: list[dict] = []
    missing: list[dict] = []
    unlocatable: list[dict] = []
    seen: dict[str, int] = {}

    for r in material_rows:
        subject = judgments.part_of(r.get("chunk_rel") or "")
        slug = judgments.material_slug(r, subject)
        if slug is None:
            unlocatable.append({
                "chunk_rel": r.get("chunk_rel") or "",
                "chunk_id": r.get("chunk_id") or "",
                "midx": (r.get("midx") or "").strip(),
                "node_slug": r.get("node_slug") or "",
            })
            continue
        seen[slug] = seen.get(slug, 0) + 1
        material = by_slug.get(slug)
        if material is None:
            missing.append({
                "slug": slug,
                "node_slug": r.get("node_slug") or "",
                "chunk_id": r.get("chunk_id") or "",
                "midx": (r.get("midx") or "").strip(),
            })
            continue
        expected_content, expected_boundary = expected_text(r)
        actual_content = material.get("contentMarkdown") or ""
        actual_boundary = material.get("boundaryMarkdown") or ""
        if expected_content != actual_content:
            content_diffs.append({
                "slug": slug, "node_slug": r.get("node_slug") or "",
                "expected": expected_content, "actual": actual_content,
            })
        if expected_boundary != actual_boundary:
            boundary_diffs.append({
                "slug": slug, "node_slug": r.get("node_slug") or "",
                "expected": expected_boundary, "actual": actual_boundary,
            })

    judge_slugs = set(seen)
    orphans = sorted(
        slug for slug in by_slug
        if slug.startswith(SLUG_PREFIXES) and slug not in judge_slugs
    )
    orphan_skip = [
        slug for slug in orphans
        if "SKIP" in material_actions.get(slug, set())
    ]

    return {
        "material_rows": len(material_rows),
        "judged_slugs": len(judge_slugs),
        "duplicate_slugs": {s: n for s, n in seen.items() if n > 1},
        "pack_materials": len(materials),
        "matched": len(judge_slugs) - len(missing),
        "content_diffs": content_diffs,
        "boundary_diffs": boundary_diffs,
        "missing": missing,
        "unlocatable": unlocatable,
        "orphan_ext": len(orphans),
        "orphan_ext_skip": len(orphan_skip),
        "orphan_examples": orphans[:20],
        "value": (len(content_diffs) + len(boundary_diffs)
                  + len(missing) + len(unlocatable)),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="判定源 ↔ 包文本漂移检查")
    parser.add_argument("--root", type=Path, default=None,
                        help="包目录（默认：staging 存在则 staging，否则成品）")
    parser.add_argument("--json", action="store_true", help="JSON 输出")
    args = parser.parse_args(argv)

    if args.root is not None:
        pack_io.use_directory(args.root)
    try:
        result = compare(load_rows(), load_pack_materials())
    finally:
        if args.root is not None:
            pack_io.reset_directory()

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=1))
    else:
        print(f"判定表 MATERIAL 行 {result['material_rows']}；唯一 slug {result['judged_slugs']}；"
              f"包内材料 {result['pack_materials']}；同 slug 命中 {result['matched']}")
        print(f"content 差异 {len(result['content_diffs'])}；"
              f"boundary 差异 {len(result['boundary_diffs'])}；"
              f"判定行有/包内无 {len(result['missing'])}；"
              f"无法定位 slug {len(result['unlocatable'])}")
        print(f"包内 ext-* 材料无 MATERIAL 判定行 {result['orphan_ext']}"
              f"（其中判定源为 SKIP {result['orphan_ext_skip']}）")
        for d in result["content_diffs"][:20]:
            print(f"  [content] {d['slug']} ← {d['node_slug']}")
            print(f"    expected={d['expected'][:100]!r}")
            print(f"    actual  ={d['actual'][:100]!r}")
        for d in result["boundary_diffs"][:20]:
            print(f"  [boundary] {d['slug']} ← {d['node_slug']}")
        for d in result["missing"][:20]:
            print(f"  [missing] {d['slug']} ← {d['node_slug']} "
                  f"({d['chunk_id']}{d['midx']})")
        for d in result["unlocatable"][:20]:
            print(f"  [unlocatable] {d['chunk_rel']} {d['chunk_id']}{d['midx']}"
                  f" → {d['node_slug']}")
        if result["duplicate_slugs"]:
            print(f"  重复 slug {len(result['duplicate_slugs'])} 个（取首行比较）")
    return 1 if result["value"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
