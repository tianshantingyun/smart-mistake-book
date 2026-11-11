#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""只读复核：批次 50 错误材料与批次 51 样本/判定/错误清单的交集。

修复环用途：3D REPORT §6.2 与 record:184 曾写「B50 的 43 条错误材料在本批样本
中出现 0 条」。本脚本只读三份 CSV，重算：
  - B50 errors ∩ B51 sample（应为 4）
  - 这 4 条在 B51 judgments 的 verdict（应为 CORRECT）
  - B50 errors ∩ B51 errors（应为 0）
  - 两轮样本交集（应为 68/1600）

不改任何判定/样本/包。输出 JSON + CSV 到脚本同目录。
运行：python -I -X utf8 verify_b50_b51_overlap.py
"""

from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
B50_ERRORS = ROOT / "docs/research/evidence/2026-11-09-batch-50/d1/errors.csv"
B51_DIR = ROOT / "docs/research/evidence/2026-11-10-batch-51/3d-audit"
B51_SAMPLE = B51_DIR / "sample.csv"
B51_JUDGMENTS = B51_DIR / "judgments.csv"
B51_ERRORS = B51_DIR / "errors.csv"
OUT_JSON = HERE / "b50-b51-overlap.json"
OUT_CSV = HERE / "b50-b51-overlap.csv"


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def by_slug(rows: list[dict[str, str]]) -> dict[str, dict[str, str]]:
    out: dict[str, dict[str, str]] = {}
    for row in rows:
        slug = row.get("material_slug") or ""
        if slug:
            out[slug] = row
    return out


def main() -> int:
    b50_errors = read_csv(B50_ERRORS)
    b51_sample = read_csv(B51_SAMPLE)
    b51_judgments = read_csv(B51_JUDGMENTS)
    b51_errors = read_csv(B51_ERRORS)

    b50_by_slug = by_slug(b50_errors)
    b51_sample_by_slug = by_slug(b51_sample)
    b51_judgment_by_slug = by_slug(b51_judgments)
    b51_error_by_slug = by_slug(b51_errors)

    overlap = sorted(set(b50_by_slug) & set(b51_sample_by_slug))
    rows = []
    all_correct = True
    for slug in overlap:
        b50 = b50_by_slug[slug]
        sample = b51_sample_by_slug[slug]
        judgment = b51_judgment_by_slug.get(slug, {})
        verdict = judgment.get("verdict")
        if verdict != "CORRECT":
            all_correct = False
        rows.append({
            "material_slug": slug,
            "b50_layer": b50.get("layer"),
            "b50_verdict": b50.get("verdict"),
            "b50_target_node_slug": b50.get("target_node_slug"),
            "b51_layer": sample.get("layer"),
            "b51_verdict": verdict,
            "b51_confidence": judgment.get("confidence"),
            "layer_changed": b50.get("layer") != sample.get("layer"),
        })

    sample_overlap = sorted(set(b50_by_slug) | set(b51_sample_by_slug))
    # 两轮样本交集需要 B50 sample.csv，而不仅是 B50 errors.csv。
    b50_sample = read_csv(ROOT / "docs/research/evidence/2026-11-09-batch-50/d1/sample.csv")
    b50_sample_by_slug = by_slug(b50_sample)
    both_samples = sorted(set(b50_sample_by_slug) & set(b51_sample_by_slug))
    error_overlap = sorted(set(b50_by_slug) & set(b51_error_by_slug))

    result = {
        "inputs": {
            "b50_errors": str(B50_ERRORS.relative_to(ROOT)).replace("\\", "/"),
            "b50_sample": "docs/research/evidence/2026-11-09-batch-50/d1/sample.csv",
            "b51_sample": str(B51_SAMPLE.relative_to(ROOT)).replace("\\", "/"),
            "b51_judgments": str(B51_JUDGMENTS.relative_to(ROOT)).replace("\\", "/"),
            "b51_errors": str(B51_ERRORS.relative_to(ROOT)).replace("\\", "/"),
        },
        "counts": {
            "b50_errors": len(b50_errors),
            "b51_sample": len(b51_sample),
            "b51_judgments": len(b51_judgments),
            "b51_errors": len(b51_errors),
        },
        "b50_errors_in_b51_sample": len(overlap),
        "b50_errors_in_b51_sample_all_correct": all_correct,
        "b50_errors_intersect_b51_errors": len(error_overlap),
        "sample_overlap": len(both_samples),
        "sample_overlap_denominator": len(b51_sample),
        "rows": rows,
    }
    OUT_JSON.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with OUT_CSV.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]) if rows else [])
        if rows:
            writer.writeheader()
            writer.writerows(rows)

    print(json.dumps({k: result[k] for k in (
        "counts", "b50_errors_in_b51_sample",
        "b50_errors_in_b51_sample_all_correct",
        "b50_errors_intersect_b51_errors", "sample_overlap")},
        ensure_ascii=False, sort_keys=True))
    expected = (len(overlap) == 4 and all_correct
                and len(error_overlap) == 0 and len(both_samples) == 68)
    print(f"expected(4 correct, 0 error overlap, 68 sample overlap)={expected}")
    return 0 if expected else 1


if __name__ == "__main__":
    raise SystemExit(main())
