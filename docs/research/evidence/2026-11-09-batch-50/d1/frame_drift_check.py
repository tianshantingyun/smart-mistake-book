# -*- coding: utf-8 -*-
"""D-1 抽样框漂移的确定性量化（批次 50 修复环）。

**它消灭的具体失败**：`manifest.json` 记的抽样框（overfull_nodes.csv，sha `fce8269c…`，
63,102 行 / 2,482 节点 / 62,948 绑定）在抽样后于 2026-11-08 15:20 被 D-2 的
`kb_build.overfull_nodes --write` 原地重写（当前 `ce7d6823…`，62,944 行 / 2,481 节点）。
原 `sample_d1.py --check` 因此 match=false / exit 2，而 REPORT 仍写 match=true——
"抽样框不可复放"只靠人工口头断言，没人量化它对 1,600 条判定与门值的影响。

本脚本只读，不重抽、不改判定、不写任何 KB/证据正文；只写本目录 `frame-drift.json`
与 stdout。它回答三件事：

1. 复放状态：`sample_d1.py --check` 的真实 exit / 输出（原样记录，不改语义）；
2. 样本层归属：1,600 条已判定样本按**当前**框的层归属与 `judgments.csv` 的 layer 是否一致；
   若不一致，层权重与护栏分母都不可用（硬失败 exit 3）；
3. 敏感性：按当前 N 复算主判据（含 FPC）与两层护栏（预注册 no-FPC 口径 + FPC 口径），
   说明 `PASS main / FAIL guardrail` 的结论是否因框漂移翻转。

用法（仓库内任意目录均可）：
    python -I -X utf8 docs/research/evidence/2026-11-09-batch-50/d1/frame_drift_check.py
退出码：0 = 漂移已量化且结论不变；3 = 样本层归属错配（判定样本不可用）；
        4 = 主判据/护栏结论相对 stats.json 翻转（必须人工复核）。
"""

from __future__ import annotations

import csv
import json
import math
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import sample_d1 as S  # noqa: E402  （只复用 load_bindings/load_overfull/sample_layer，不触发写盘）

Z = 1.6448536269514722


def wilson_lower(k: float, n: float, z: float = Z) -> float:
    if n <= 0:
        return 0.0
    p = k / n
    denom = 1.0 + z * z / n
    center = (p + z * z / (2.0 * n)) / denom
    half = z * math.sqrt(p * (1.0 - p) / n + z * z / (4.0 * n * n)) / denom
    return max(0.0, center - half)


def _estimate(layer_sizes: dict[str, int], judgments: list[dict], total: int) -> dict:
    """按给定层规模复算主判据（含 FPC）与每层护栏（no-FPC 与 FPC 两种口径）。

    判定计数只来自 judgments.csv（n/k 固定）；变的是层权重与 FPC——这正是框漂移会动到的量。
    """
    point = 0.0
    var = 0.0
    per_layer = {}
    for layer, N in layer_sizes.items():
        rs = [r for r in judgments if r["layer"] == layer]
        n = len(rs)
        k = sum(1 for r in rs if r["verdict"] == "CORRECT")
        w = N / total
        p = k / n
        fpc = (N - n) / (N - 1)
        point += w * p
        var += w * w * p * (1.0 - p) / n * fpc
        n_eff_h = n / fpc
        per_layer[layer] = {
            "N": N, "n": n, "correct": k, "p": p, "weight": w, "fpc": fpc,
            "guardrail_nofpc": {"n": n, "k": k, "lower": wilson_lower(k, n),
                                "pass": wilson_lower(k, n) >= 0.90},
            "guardrail_fpc": {"n_eff": n_eff_h, "k_eff": p * n_eff_h,
                              "lower": wilson_lower(p * n_eff_h, n_eff_h),
                              "pass": wilson_lower(p * n_eff_h, n_eff_h) >= 0.90},
        }
    se = math.sqrt(var)
    n_eff = point * (1.0 - point) / var
    main_lower = wilson_lower(point * n_eff, n_eff)
    return {
        "point": point, "se": se, "n_eff": n_eff, "lower_95": main_lower,
        "main_pass": main_lower >= 0.95,
        "guardrail_nofpc_pass": all(v["guardrail_nofpc"]["pass"] for v in per_layer.values()),
        "guardrail_fpc_pass": all(v["guardrail_fpc"]["pass"] for v in per_layer.values()),
        "per_layer": per_layer,
    }


def main() -> int:
    manifest = json.loads((HERE / "manifest.json").read_text(encoding="utf-8"))
    judgments = list(csv.DictReader((HERE / "judgments.csv").open(encoding="utf-8", newline="")))

    frozen_meta = manifest["frame"]["layers"]["overfull"]["list"]
    sampling = {
        "path": frozen_meta["path"],
        "sha256": frozen_meta["sha256"],
        "rows": frozen_meta["rows"],
        "unique_node_ids": frozen_meta["unique_node_ids"],
        "layer_size": manifest["frame"]["layers"]["overfull"]["size"],
        "provenance": "manifest.json frame.layers.overfull.list / .size（抽样时点读数）",
    }

    # 当前框：只读当前 CSV + 现行包绑定。
    overfull_ids, current_list = S.load_overfull()
    bindings, _index, _sidecars = S.load_bindings()
    current_layer_size = sum(1 for b in bindings if b["node_id"] in overfull_ids)
    current = {
        "path": current_list["path"],
        "sha256": current_list["sha256"],
        "rows": current_list["rows"],
        "unique_node_ids": current_list["unique_node_ids"],
        "layer_size": current_layer_size,
        "provenance": "frame_drift_check.py 实读当前 build/kb-staging/overfull_nodes.csv + 现行包绑定",
    }

    # 1) 原样复放 sample_d1.py --check（记录真实 exit，不解释、不改判）。
    proc = subprocess.run(
        [sys.executable, "-I", "-X", "utf8", str(HERE / "sample_d1.py"), "--check"],
        cwd=str(S.REPO), capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    replay = {
        "command": "python -I -X utf8 docs/research/evidence/2026-11-09-batch-50/d1/sample_d1.py --check",
        "exit": proc.returncode,
        "stdout": (proc.stdout or "").strip(),
        "stderr": (proc.stderr or "").strip(),
        "match": '"match": true' in (proc.stdout or ""),
    }

    # 2) 已判定样本对当前框的层归属（judgments.csv 只记 material_slug，1 材料 1 绑定 → 反查 node_id）。
    node_by_material = {b["material_slug"]: b["node_id"] for b in bindings}
    missing = [r["material_slug"] for r in judgments if r["material_slug"] not in node_by_material]
    if missing:
        raise SystemExit(f"judgments.csv 有 {len(missing)} 条材料不在现行包：{missing[:5]}")
    mismatches = []
    for row in judgments:
        current_layer = ("overfull"
                         if node_by_material[row["material_slug"]] in overfull_ids else "normal")
        if row["layer"] != current_layer:
            mismatches.append({"material_slug": row["material_slug"],
                               "judged_layer": row["layer"],
                               "current_layer": current_layer})

    # 3) 按当前框 N 复算：主判据（含 FPC）+ 两层护栏（no-FPC 预注册口径 / FPC 口径）。
    total = len(bindings)
    current_N = {"overfull": current_layer_size, "normal": total - current_layer_size}
    sampling_N = {"overfull": sampling["layer_size"],
                  "normal": total - sampling["layer_size"]}
    estimate = _estimate(current_N, judgments, total)
    sampling_estimate = _estimate(sampling_N, judgments, total)

    # 4) 当前框重抽与已冻结样本的键差异（说明"不可复放"的幅度，不替换样本）。
    buckets = {"overfull": [], "normal": []}
    for b in bindings:
        buckets["overfull" if b["node_id"] in overfull_ids else "normal"].append(b)
    scope_sha, _parts = S.pack_scope_sha(_index, _sidecars)
    seed = S.sha256_bytes(f"{scope_sha}|{S.AUDIT_ID}".encode("utf-8"))
    re_picked = (S.sample_layer(buckets["overfull"], "overfull", S.N_OVERFULL, seed)
                 + S.sample_layer(buckets["normal"], "normal", S.N_NORMAL, seed))
    re_keys = {(r["layer"], r["material_slug"], r["node_id"]) for r in re_picked}
    frozen_keys = {(r["layer"], r["material_slug"], node_by_material[r["material_slug"]])
                   for r in judgments}
    resample_diff = len(frozen_keys - re_keys)

    stats = json.loads((HERE / "stats.json").read_text(encoding="utf-8"))
    unchanged = (estimate["main_pass"] == stats["estimate"]["pass"]
                 and estimate["guardrail_nofpc_pass"] == stats["per_layer_guardrail_pass"])
    # 同一段代码在"抽样时点 N"上必须逐位复现 stats.json——否则说明漂移复算换了公式。
    self_check = {
        "main_lower_match": abs(sampling_estimate["lower_95"]
                                - stats["estimate"]["lower_95"]) < 1e-12,
        "normal_guardrail_nofpc_match": abs(
            sampling_estimate["per_layer"]["normal"]["guardrail_nofpc"]["lower"]
            - stats["per_layer"]["normal"]["wilson_lower_95"]) < 1e-12,
    }
    if not all(self_check.values()):
        raise SystemExit(f"漂移复算未能复现 stats.json，公式不一致：{self_check}")

    out = {
        "audit_id": S.AUDIT_ID,
        "generated_by": "docs/research/evidence/2026-11-09-batch-50/d1/frame_drift_check.py",
        "checker_sha256": S.sha256_file(HERE / "frame_drift_check.py"),
        "sampling_frame": sampling,
        "current_frame": current,
        "delta": {
            "rows": current["rows"] - sampling["rows"],
            "unique_node_ids": current["unique_node_ids"] - sampling["unique_node_ids"],
            "layer_size": current["layer_size"] - sampling["layer_size"],
        },
        "replay": replay,
        "sample_layer_mismatches": len(mismatches),
        "sample_layer_mismatch_examples": mismatches[:5],
        "resample_under_current_frame": {"different_keys": resample_diff,
                                         "total_keys": len(frozen_keys)},
        "estimate_under_current_frame": estimate,
        "estimate_under_sampling_frame": sampling_estimate,
        "self_check_against_stats": self_check,
        "stats_json_baseline": {
            "main_lower_95": stats["estimate"]["lower_95"],
            "main_pass": stats["estimate"]["pass"],
            "guardrail_nofpc_pass": stats["per_layer_guardrail_pass"],
            "normal_guardrail_lower": stats["per_layer"]["normal"]["wilson_lower_95"],
        },
        "conclusion_unchanged": unchanged,
    }
    (HERE / "frame-drift.json").write_text(
        json.dumps(out, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(json.dumps({
        "replay_exit": replay["exit"],
        "sample_layer_mismatches": len(mismatches),
        "resample_different_keys": resample_diff,
        "main_lower": estimate["lower_95"],
        "main_pass": estimate["main_pass"],
        "guardrail_nofpc_lower_normal": estimate["per_layer"]["normal"]["guardrail_nofpc"]["lower"],
        "guardrail_nofpc_pass": estimate["guardrail_nofpc_pass"],
        "guardrail_fpc_lower_normal": estimate["per_layer"]["normal"]["guardrail_fpc"]["lower"],
        "guardrail_fpc_pass": estimate["guardrail_fpc_pass"],
        "sampling_frame_fpc_lower_normal": sampling_estimate["per_layer"]["normal"]["guardrail_fpc"]["lower"],
        "self_check_against_stats": self_check,
        "conclusion_unchanged": unchanged,
    }, ensure_ascii=False, indent=1))

    if mismatches:
        return 3
    if not unchanged:
        return 4
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
