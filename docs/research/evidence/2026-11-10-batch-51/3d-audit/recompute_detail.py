# -*- coding: utf-8 -*-
"""复算 D-1 的全部并列读数（只读；不 import compile/recompute）。

输出：
- 未加权正确率
- 分层加权点估计 / SE / n_eff / 单侧 95% Wilson 下界（主判据，FPC）
- 每层护栏：预注册 no-FPC Wilson；FPC 口径并列（n_eff = n / FPC）
- 主判据敏感性：no-FPC / deff=1.05

门阈值不在此处定义新值：GATE=0.95、GUARDRAIL=0.90 仅用于打印判定布尔值，
与 manifest/预注册一致；本脚本不改样本、不改判定、不改 stats.json。

用法：python -I -X utf8 recompute_detail.py
产物：stats_detail.json
"""

from __future__ import annotations

import csv
import json
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
Z_ONE_SIDED_95 = 1.6448536269514722
GATE = 0.95
GUARDRAIL = 0.90
DEFF = 1.05


def wilson_lower(p: float, n: float, z: float = Z_ONE_SIDED_95) -> float:
    """单侧 95% Wilson 下界；p 可为小数（k/n），n 可为有效样本量。"""
    if n <= 0:
        return 0.0
    denom = 1.0 + z * z / n
    center = (p + z * z / (2.0 * n)) / denom
    half = z * math.sqrt(p * (1.0 - p) / n + z * z / (4.0 * n * n)) / denom
    return max(0.0, center - half)


def main() -> int:
    manifest = json.loads((HERE / "manifest.json").read_text(encoding="utf-8"))
    layers = manifest["frame"]["layers"]
    rows = list(csv.DictReader((HERE / "judgments.csv").open(encoding="utf-8", newline="")))
    if len(rows) != 1600:
        raise SystemExit(f"judgments.csv 行数 {len(rows)} != 1600")
    if {r["layer"] for r in rows} != set(layers):
        raise SystemExit(f"层集合不符：{sorted({r['layer'] for r in rows})} vs {sorted(layers)}")

    total_correct = sum(1 for r in rows if r["verdict"] == "CORRECT")
    per_layer: dict[str, dict] = {}
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
        n_eff_fpc = n / fpc
        point += w * p
        var += w * w * p * (1.0 - p) / n * fpc
        var_nofpc += w * w * p * (1.0 - p) / n
        per_layer[layer] = {
            "N": N,
            "n": n,
            "correct": k,
            "errors": n - k,
            "p": p,
            "weight": w,
            "fpc": fpc,
            "n_eff_fpc": n_eff_fpc,
            "wilson_lower_95_nofpc": wilson_lower(p, n),
            "wilson_lower_95_fpc": wilson_lower(p, n_eff_fpc),
            "guardrail_pass_nofpc": wilson_lower(p, n) >= GUARDRAIL,
            "guardrail_pass_fpc": wilson_lower(p, n_eff_fpc) >= GUARDRAIL,
            "error_counts": {
                "REBIND": sum(1 for r in rs if r["verdict"] == "REBIND"),
                "NONE": sum(1 for r in rs if r["verdict"] == "NONE"),
            },
        }

    def estimate(v: float) -> dict:
        n_eff = point * (1.0 - point) / v
        lower = wilson_lower(point, n_eff)
        return {"se": math.sqrt(v), "n_eff": n_eff,
                "lower_95": lower, "pass_0.95": lower >= GATE}

    out = {
        "input": {"judgments": "judgments.csv", "rows": len(rows),
                  "manifest_audit_id": manifest.get("audit_id")},
        "unweighted": {"correct": total_correct, "n": len(rows),
                       "accuracy": total_correct / len(rows)},
        "weighted_point": point,
        "main_fpc": estimate(var),
        "sensitivity_nofpc": estimate(var_nofpc),
        "sensitivity_deff105": estimate(var * DEFF),
        "per_layer": per_layer,
        "guardrail_nofpc_all": all(v["guardrail_pass_nofpc"] for v in per_layer.values()),
        "guardrail_fpc_all": all(v["guardrail_pass_fpc"] for v in per_layer.values()),
    }

    stored = json.loads((HERE / "stats.json").read_text(encoding="utf-8"))
    checks = {
        "point": abs(point - stored["estimate"]["point"]) < 1e-12,
        "se": abs(out["main_fpc"]["se"] - stored["estimate"]["se"]) < 1e-12,
        "n_eff": abs(out["main_fpc"]["n_eff"] - stored["estimate"]["n_eff"]) < 1e-9,
        "lower_95": abs(out["main_fpc"]["lower_95"] - stored["estimate"]["lower_95"]) < 1e-12,
        "lower_95_nofpc": abs(out["sensitivity_nofpc"]["lower_95"]
                              - stored["estimate"]["lower_95_nofpc"]) < 1e-12,
        "lower_95_deff105": abs(out["sensitivity_deff105"]["lower_95"]
                                - stored["estimate"]["lower_95_deff105"]) < 1e-12,
    }
    for layer in layers:
        checks[f"layer_{layer}_correct"] = (per_layer[layer]["correct"]
                                            == stored["per_layer"][layer]["correct"])
    out["checks_vs_stats_json"] = checks
    out["all_match"] = all(checks.values())
    (HERE / "stats_detail.json").write_text(
        json.dumps(out, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(json.dumps(out, ensure_ascii=False, indent=1))
    return 0 if out["all_match"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
