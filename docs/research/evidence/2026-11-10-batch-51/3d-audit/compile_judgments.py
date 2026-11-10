# -*- coding: utf-8 -*-
"""合并 32 批判定原件 -> judgments.csv / errors.csv / stats.json，并计算 D-1 门值。

主判据（R2 §3.1，唯一）：
    p_hat = Σ_h w_h * p_h                       w_h = N_h / N
    Var   = Σ_h w_h^2 * p_h(1-p_h)/n_h * FPC_h  FPC_h = (N_h-n_h)/(N_h-1)
    n_eff = p_hat(1-p_hat) / Var
    L     = Wilson_lower(k=p_hat*n_eff, n=n_eff, z=1.6448536)   # 单侧 95%
    PASS iff L >= 0.95
护栏（R2 §2.5，非门值）：每层 Wilson 单侧 95% 下界 >= 0.90。

批次 51 差异：`judged_at` 不再硬编码，逐批取 `verdicts_raw/batch-NN.tsv` 的 mtime
（ISO 本地时区），并把来源写进 metadata；避免把判定时间写成未发生的常量。

用法：python -I -X utf8 compile_judgments.py
只读 verdicts_raw/、sample.jsonl、judge_input/；只写本目录三个产物。
"""

from __future__ import annotations

import csv
import hashlib
import json
import math
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW = HERE / "verdicts_raw"
HEADER = ["material_slug", "verdict", "target_node_slug", "confidence", "evidence_quote", "note"]
Z_ONE_SIDED_95 = 1.6448536269514722
AUDIT_ID = "B51-D1-2026-11-10"
MODEL_ID = "deepseek/deepseek-flash (ZCode harness)"
GATE = 0.95
GUARDRAIL = 0.90
DEFF = 1.05


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def wilson_lower(k: float, n: float, z: float = Z_ONE_SIDED_95) -> float:
    if n <= 0:
        return 0.0
    p = k / n
    denom = 1.0 + z * z / n
    center = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return max(0.0, center - half)


def file_mtime_iso(path: Path) -> str:
    return datetime.fromtimestamp(path.stat().st_mtime).astimezone().isoformat(timespec="seconds")


def main() -> int:
    manifest = json.loads((HERE / "manifest.json").read_text(encoding="utf-8"))
    layers = manifest["frame"]["layers"]
    samples = {}
    for line in (HERE / "sample.jsonl").read_text(encoding="utf-8").splitlines():
        rec = json.loads(line)
        samples[rec["material_slug"]] = rec

    raw_files = sorted(RAW.glob("batch-*.tsv"))
    if len(raw_files) != 32:
        raise SystemExit(f"判定原件应为 32 件，实得 {len(raw_files)}")
    prompt_sha = sha256_file(HERE / "JUDGE_PROMPT.md")

    rows = []
    problems = []
    judged_at = {}
    for path in raw_files:
        batch = path.stem.replace("batch-", "")
        batch_sha = sha256_file(HERE / "judge_input" / f"batch-{batch}.json")
        judged_at[batch] = file_mtime_iso(path)
        with path.open(encoding="utf-8", newline="") as fh:
            reader = csv.DictReader(fh, delimiter="\t")
            if reader.fieldnames != HEADER:
                problems.append(f"{path.name}: 表头不符 {reader.fieldnames}")
            for r in reader:
                slug = r["material_slug"]
                rec = samples.get(slug)
                if rec is None:
                    problems.append(f"{path.name}: {slug} 不在样本")
                    continue
                quote = r["evidence_quote"]
                m = rec["material"]
                hay = "\n".join(str(m.get(k) or "") for k in
                                ("title", "summaryMarkdown", "applicabilityMarkdown",
                                 "contentMarkdown", "boundaryMarkdown"))
                quote_ok = bool(quote) and quote in hay
                if not quote_ok:
                    problems.append(f"{slug}: 引文非逐字子串")
                if r["verdict"] not in ("CORRECT", "REBIND", "NONE"):
                    problems.append(f"{slug}: verdict={r['verdict']}")
                rows.append({
                    "material_slug": slug,
                    "layer": rec["layer"],
                    "subject": rec["subject"],
                    "verdict": r["verdict"],
                    "target_node_slug": r["target_node_slug"],
                    "confidence": r["confidence"],
                    "evidence_quote": quote,
                    "quote_is_substring": "true" if quote_ok else "false",
                    "note": r["note"],
                    "labeler_kind": "model",
                    "model_id": MODEL_ID,
                    "prompt_sha256": prompt_sha,
                    "batch_sha256": batch_sha,
                    "batch": int(batch),
                    "judged_at": judged_at[batch],
                    "blind": "true",
                })
    if len(rows) != 1600:
        problems.append(f"判定行数 {len(rows)} != 1600")
    if len({r["material_slug"] for r in rows}) != len(rows):
        problems.append("material_slug 重复")
    if problems:
        for p in problems[:20]:
            print("PROBLEM\t" + p)
        raise SystemExit(2)

    rows.sort(key=lambda r: (r["layer"], r["material_slug"]))
    out = HERE / "judgments.csv"
    with out.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    # 错误项清单
    pack_id = manifest["pack"]["pack_id"]

    def node_id(subject: str, slug: str) -> str:
        return f"kb:{pack_id}:{subject.lower()}:atomic:{slug}" if slug else ""

    err = HERE / "errors.csv"
    with err.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["material_slug", "layer", "subject", "current_node_id", "current_node_slug",
                    "current_node_name", "verdict", "target_node_id", "target_node_slug",
                    "confidence", "evidence_quote", "note"])
        for r in rows:
            if r["verdict"] == "CORRECT":
                continue
            rec = samples[r["material_slug"]]
            w.writerow([r["material_slug"], r["layer"], r["subject"],
                        rec["binding"]["knowledgeNodeId"], rec["node"]["node_slug"],
                        rec["node"]["name"], r["verdict"],
                        node_id(r["subject"], r["target_node_slug"]), r["target_node_slug"],
                        r["confidence"], r["evidence_quote"], r["note"]])

    per_layer = {}
    point = 0.0
    var = 0.0
    var_nofpc = 0.0
    for layer, meta in layers.items():
        rs = [r for r in rows if r["layer"] == layer]
        n = len(rs)
        k = sum(1 for r in rs if r["verdict"] == "CORRECT")
        N = meta["size"]
        w = meta["weight"]
        p = k / n
        fpc = (N - n) / (N - 1) if N > 1 else 1.0
        point += w * p
        var += w * w * p * (1 - p) / n * fpc
        var_nofpc += w * w * p * (1 - p) / n
        per_layer[layer] = {
            "N": N, "n": n, "correct": k, "errors": n - k, "p": p, "weight": w,
            "fpc": fpc, "wilson_lower_95": wilson_lower(k, n),
            "wilson_lower_95_nofpc": wilson_lower(k, n),
            "guardrail_pass": wilson_lower(k, n) >= GUARDRAIL,
            "error_counts": {
                "REBIND": sum(1 for r in rs if r["verdict"] == "REBIND"),
                "NONE": sum(1 for r in rs if r["verdict"] == "NONE"),
            },
        }
    se = math.sqrt(var)
    se_nofpc = math.sqrt(var_nofpc)
    n_eff = point * (1 - point) / var
    n_eff_nofpc = point * (1 - point) / var_nofpc
    n_eff_deff = point * (1 - point) / (var * DEFF)
    lower = wilson_lower(point * n_eff, n_eff)
    lower_nofpc = wilson_lower(point * n_eff_nofpc, n_eff_nofpc)
    lower_deff = wilson_lower(point * n_eff_deff, n_eff_deff)
    per_layer_guard = all(v["guardrail_pass"] for v in per_layer.values())
    stats = {
        "audit_id": AUDIT_ID,
        "gate": {"criterion": "one-sided 95% Wilson lower bound of stratified weighted binding precision",
                 "threshold": GATE, "z": Z_ONE_SIDED_95},
        "guardrail": {"criterion": "per-layer one-sided 95% Wilson lower bound",
                      "threshold": GUARDRAIL, "deff_sensitivity": DEFF},
        "sample": {"total": len(rows), "layers": {k: v["n"] for k, v in per_layer.items()}},
        "judgments": {"correct": sum(1 for r in rows if r["verdict"] == "CORRECT"),
                      "rebind": sum(1 for r in rows if r["verdict"] == "REBIND"),
                      "none": sum(1 for r in rows if r["verdict"] == "NONE")},
        "estimate": {
            "point": point, "se": se, "n_eff": n_eff,
            "lower_95": lower,
            "point_nofpc": point, "se_nofpc": se_nofpc,
            "lower_95_nofpc": lower_nofpc,
            "se_deff105": math.sqrt(var * DEFF), "lower_95_deff105": lower_deff,
            "pass": lower >= GATE,
        },
        "per_layer": per_layer,
        "per_layer_guardrail_pass": per_layer_guard,
        "overall_pass": bool(lower >= GATE and per_layer_guard),
        "verdict": ("PASS (main + guardrail)" if (lower >= GATE and per_layer_guard)
                    else "PASS main / FAIL guardrail" if lower >= GATE
                    else "FAIL (main)"),
        "metadata": {"labeler_kind": "model", "model_id": MODEL_ID,
                     "prompt_sha256": prompt_sha,
                     "judged_at": "per-row from verdicts_raw/*.tsv mtime",
                     "judged_at_by_batch": judged_at,
                     "blind_to_layer": True, "human_expert": False},
    }
    (HERE / "stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=1) + "\n",
                                     encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
