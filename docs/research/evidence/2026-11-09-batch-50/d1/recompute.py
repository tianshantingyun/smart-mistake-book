# -*- coding: utf-8 -*-
"""独立复算 D-1 门值：只读 manifest.json + judgments.csv，重算并核对 stats.json。

不 import compile_judgments.py（避免"用同一段代码证明自己"）。
用法：python -I -X utf8 recompute.py
产物：stats_recomputed.json；与 stats.json 不一致时 exit 2。
"""

from __future__ import annotations

import csv
import json
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
Z = 1.6448536269514722


def wilson_lower(k: float, n: float, z: float = Z) -> float:
    if n <= 0:
        return 0.0
    p = k / n
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

    point = 0.0
    var = 0.0
    per_layer = {}
    for layer, meta in layers.items():
        rs = [r for r in rows if r["layer"] == layer]
        n = len(rs)
        k = sum(1 for r in rs if r["verdict"] == "CORRECT")
        N = meta["size"]
        w = meta["weight"]
        p = k / n
        fpc = (N - n) / (N - 1)
        point += w * p
        var += w * w * p * (1.0 - p) / n * fpc
        per_layer[layer] = {"N": N, "n": n, "correct": k, "p": p,
                            "wilson_lower_95": wilson_lower(k, n)}
    se = math.sqrt(var)
    n_eff = point * (1.0 - point) / var
    lower = wilson_lower(point * n_eff, n_eff)
    computed = {
        "point": point, "se": se, "n_eff": n_eff, "lower_95": lower,
        "per_layer": per_layer,
        "pass_main": lower >= 0.95,
        "pass_guardrail": all(v["wilson_lower_95"] >= 0.90 for v in per_layer.values()),
    }
    stored = json.loads((HERE / "stats.json").read_text(encoding="utf-8"))
    checks = {
        "point": abs(computed["point"] - stored["estimate"]["point"]) < 1e-12,
        "se": abs(computed["se"] - stored["estimate"]["se"]) < 1e-12,
        "n_eff": abs(computed["n_eff"] - stored["estimate"]["n_eff"]) < 1e-9,
        "lower_95": abs(computed["lower_95"] - stored["estimate"]["lower_95"]) < 1e-12,
        "pass_main": computed["pass_main"] == stored["estimate"]["pass"],
        "pass_guardrail": computed["pass_guardrail"] == stored["per_layer_guardrail_pass"],
    }
    for layer in layers:
        checks[f"layer_{layer}_correct"] = (computed["per_layer"][layer]["correct"]
                                            == stored["per_layer"][layer]["correct"])
    out = {"computed": computed, "checks": checks, "all_match": all(checks.values())}
    (HERE / "stats_recomputed.json").write_text(json.dumps(out, ensure_ascii=False, indent=1) + "\n",
                                               encoding="utf-8")
    print(json.dumps(out, ensure_ascii=False, indent=1))
    return 0 if out["all_match"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
