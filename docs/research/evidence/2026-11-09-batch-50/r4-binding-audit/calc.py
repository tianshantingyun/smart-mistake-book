# -*- coding: utf-8 -*-
"""B50 R-4：D-1 分层抽样样本量 / **Wilson 单侧 95% 下界** 的实算表（可复跑）。

主判据口径（与计划文字一致）：先把分层加权估计折成"有效样本量" n_eff = p̂(1-p̂)/SE²，
再对 (k = p̂·n_eff, n = n_eff) 套 Wilson 单侧 95% 下界。该口径比分层 Wald 下界
保守 0.08–0.23pp（见输出 wald_vs_wilson）。

输入全部来自本仓实测：
  - 现行成品包层规模：<OUT>/frame_probe.json（本会话实跑）
  - 批次9 round2 / round1 报告：build/agent-batch9/d1/precision/{round2/report.json,round1-report.json}
用法：python frame_probe.py > frame_probe.json 之后
      python calc.py [输出目录]        # 默认 build/b50-research
输出：控制台 JSON（<OUT>/d1_design.json）。
"""
from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path


def find_repo(start):
    """向上找含 tools/kb_build/pack_io.py 的目录作为仓库根（脚本可放在任意子目录）。"""
    for p in [start, *start.parents]:
        if (p / "tools" / "kb_build" / "pack_io.py").exists():
            return p
    raise SystemExit("找不到仓库根（缺 tools/kb_build/pack_io.py）")


REPO = find_repo(Path(__file__).resolve().parent)
# 输出目录：默认仓库内 build/b50-research；可用 argv[1] 指到证据目录
OUT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else REPO / "build/b50-research"

Z1 = 1.6448536269514722   # 单侧 95%
Z2 = 1.959963984540054    # 双侧 95%
SEED = 20261109           # Monte Carlo 固定种子（可复现要求，非安全用途）


# ---------- 基础统计 ----------

def wilson(k: float, n: float, z: float) -> tuple[float, float, float]:
    """Wilson 得分区间（与 tools/kb_coverage/binding_precision.py 同式）。"""
    if n <= 0:
        return float("nan"), 0.0, 1.0
    p = k / n
    den = 1 + z * z / n
    center = (p + z * z / (2 * n)) / den
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / den
    return p, max(0.0, center - half), min(1.0, center + half)


def strat(layers: dict[str, dict], z: float) -> dict:
    """分层加权估计 + Wald 区间（含 FPC 对照），与仓内统计器同口径。"""
    total = sum(v["N"] for v in layers.values())
    point = var = var_fpc = 0.0
    for h, v in layers.items():
        w = v["N"] / total
        n = v["n"]
        p = v["k"] / n if n else 0.0
        point += w * p
        if n > 1:
            vv = p * (1 - p) / n
            var += w * w * vv
            fpc = max(0.0, (v["N"] - n) / (v["N"] - 1)) if v["N"] > 1 else 1.0
            var_fpc += w * w * vv * fpc
    se = math.sqrt(var)
    return {"point": point, "se": se, "se_fpc": math.sqrt(var_fpc),
            "lo1": point - z * se, "hi1": point + z * se,
            "lo1_fpc": point - z * math.sqrt(var_fpc), "total": total}


def wilson_neff(point: float, se: float, z: float = Z1) -> float:
    """分层估计 → 有效样本量 → Wilson 单侧下界（主判据）。"""
    if se <= 0:
        return point
    n_eff = point * (1 - point) / (se * se)
    return wilson(point * n_eff, n_eff, z)[1]


def gate_lo(layers: dict[str, dict], z: float = Z1) -> float:
    s = strat(layers, z)
    return wilson_neff(s["point"], s["se"], z)


def alloc_prop(Ns: dict[str, int], n_total: int) -> dict[str, int]:
    """按层规模比例分配（最大余数法，确定性）。"""
    total = sum(Ns.values())
    raw = {h: n_total * Ns[h] / total for h in Ns}
    base = {h: int(math.floor(raw[h])) for h in Ns}
    rem = n_total - sum(base.values())
    order = sorted(Ns, key=lambda h: (-(raw[h] - base[h]), h))
    for h in order[:rem]:
        base[h] += 1
    return base


def solve_min_p(Ns: dict[str, int], alloc: dict[str, int], target=0.95, z=Z1) -> float:
    """所有层真实率相同的假设下，使门值（Wilson-on-n_eff）>= target 的最小 p。"""
    lo, hi = 0.90, 0.999999
    for _ in range(200):
        mid = (lo + hi) / 2
        layers = {h: {"N": Ns[h], "n": alloc[h], "k": mid * alloc[h]} for h in Ns}
        if gate_lo(layers, z) >= target:
            hi = mid
        else:
            lo = mid
    return hi


def mc_power(p: float, Ns: dict[str, int], alloc: dict[str, int],
             trials: int = 2000, seed: int = SEED) -> float:
    """Monte Carlo 功效：真值 p 下，门值 >= 0.95 的比例（含 p̂ 与 SE 的实现波动）。"""
    rng = random.Random(seed)
    hit = 0
    for _ in range(trials):
        layers = {}
        for h in Ns:
            k = sum(1 for _ in range(alloc[h]) if rng.random() < p)
            layers[h] = {"N": Ns[h], "n": alloc[h], "k": k}
        if gate_lo(layers) >= 0.95:
            hit += 1
    return hit / trials


def max_errors(Ns: dict[str, int], alloc: dict[str, int], target=0.95, z=Z1) -> int:
    """给定分配，最多允许多少条判错仍能过门（错误按各层 n 等比摊到层内）。"""
    n = sum(alloc.values())
    best = -1
    for err in range(0, n + 1):
        layers = {}
        for h in Ns:
            e_h = round(err * alloc[h] / n)
            layers[h] = {"N": Ns[h], "n": alloc[h], "k": alloc[h] - e_h}
        if gate_lo(layers, z) >= target:
            best = err
        else:
            break
    return best


# ---------- 主表 ----------

def main() -> int:
    fp = json.loads((OUT / "frame_probe.json").read_text(encoding="utf-8", errors="replace"))
    r2 = json.loads((REPO / "build/agent-batch9/d1/precision/round2/report.json")
                    .read_text(encoding="utf-8"))
    r1 = json.loads((REPO / "build/agent-batch9/d1/precision/round1-report.json")
                    .read_text(encoding="utf-8"))
    res: dict = {}

    # --- 1. 两个框 ---
    F_cur = {k: v["size"] for k, v in fp["layers"].items() if v["size"] > 0}
    F_r2 = {k: v["size"] for k, v in r2["layers"].items()}
    res["frames"] = {"current_pack": F_cur, "round2_pack": F_r2}

    # --- 2. 复现 round2 并给两种单侧下界 ---
    lay = {h: {"N": v["size"], "n": v["sampled"], "k": v["correct"]} for h, v in r2["layers"].items()}
    r2s = strat(lay, Z2)
    r2s1 = strat(lay, Z1)
    res["round2_reproduction"] = {
        "point_match": abs(r2s["point"] - r2["weighted"]["precision"]) < 1e-12,
        "ci_match": (abs(r2s["lo1"] - r2["weighted"]["approx_ci95"][0]) < 1e-9
                     and abs(r2s["hi1"] - r2["weighted"]["approx_ci95"][1]) < 1e-9),
        "point": r2s["point"], "se": r2s["se"],
        "two_sided_lo": r2s["lo1"], "two_sided_hi": r2s["hi1"],
        "one_sided_wald_z165": r2s1["lo1"],
        "one_sided_wilson_neff": wilson_neff(r2s["point"], r2s["se"]),
        "passes_0.95": wilson_neff(r2s["point"], r2s["se"]) >= 0.95,
    }

    # round1 vs round2 差异
    lay1 = {h: {"N": v["size"], "n": v["sampled"], "k": v["correct"]} for h, v in r1["layers"].items()}
    r1s = strat(lay1, Z2)
    se_d = math.sqrt(r1s["se"] ** 2 + r2s["se"] ** 2)
    diff = r2s["point"] - r1s["point"]
    zz = diff / se_d
    res["round1_vs_round2"] = {
        "r1_point": r1s["point"], "r2_point": r2s["point"], "diff": diff,
        "se_diff_indep": se_d, "z": zz,
        "two_sided_p": 2 * (1 - 0.5 * (1 + math.erf(abs(zz) / math.sqrt(2)))),
    }

    # --- 3. n 需求：在 round2 观测率上要多少样本才够 0.95 单侧 ---
    need = []
    for ntotal in (600, 1200, 1600, 2000, 4000, 8000, 20000, 50000):
        a = alloc_prop(F_r2, ntotal)
        if any(a[h] > F_r2[h] for h in a):
            need.append({"n_total": ntotal, "alloc": a, "infeasible": True})
            continue
        layers = {h: {"N": F_r2[h], "n": a[h], "k": a[h] * r2["layers"][h]["precision"]} for h in F_r2}
        need.append({"n_total": ntotal, "alloc": a, "gate_lo": gate_lo(layers)})
    res["n_needed_at_round2_rates"] = need

    # --- 4. 设计前缘：现行框上，各 n 的过门阈值 p* / 允许错误数 / MC 功效 ---
    frontier = []
    for ntotal in (600, 900, 1200, 1500, 1600, 2000, 3000):
        a = alloc_prop(F_cur, ntotal)
        frontier.append({"n_total": ntotal, "alloc": a,
                         "min_p_to_pass": solve_min_p(F_cur, a),
                         "max_errors": max_errors(F_cur, a),
                         "mc_power": {str(p): mc_power(p, F_cur, a) for p in (0.96, 0.965, 0.97)}})
    res["frontier_current_frame"] = frontier

    # --- 4b. 防遮蔽的偏置分配（小层加样，牺牲极少的阈值换取 per-layer 护栏） ---
    guard = []
    for label, a in (
        ("prop-1200", alloc_prop(F_cur, 1200)),
        ("guard-1200-950/250", {"overfull": 950, "normal": 250}),
        ("guard-1200-900/300", {"overfull": 900, "normal": 300}),
        ("guard-1600-1300/300", {"overfull": 1300, "normal": 300}),
        ("guard-1600-1200/400", {"overfull": 1200, "normal": 400}),
    ):
        guard.append({"design": label, "alloc": a, "n_total": sum(a.values()),
                      "min_p_to_pass": solve_min_p(F_cur, a),
                      "max_errors": max_errors(F_cur, a),
                      "mc_power": {str(p): mc_power(p, F_cur, a) for p in (0.965, 0.97)}})
    res["guard_allocations"] = guard

    # --- 5. 反事实：旧框（round2）同法前沿 ---
    frontier_old = []
    for ntotal in (600, 1200, 2000):
        a = alloc_prop(F_r2, ntotal)
        frontier_old.append({"n_total": ntotal, "alloc": a,
                             "min_p_to_pass": solve_min_p(F_r2, a),
                             "max_errors": max_errors(F_r2, a)})
    res["frontier_round2_frame"] = frontier_old

    # --- 6. 零错误门槛（方法对照） ---
    def min_n_zero(z: float) -> int:
        n = 1
        while wilson(n, n, z)[1] < 0.95:
            n += 1
        return n
    res["zero_error_thresholds"] = {
        "wilson_one_sided_z165": min_n_zero(Z1),
        "wilson_two_sided_z196": min_n_zero(Z2),
        "exact_cp_one_sided": math.ceil(math.log(0.05) / math.log(0.95)),
        "exact_cp_two_sided": math.ceil(math.log(0.025) / math.log(0.95)),
        "paper_n100_recomputed_2s": 0.025 ** (1 / 100),
        "paper_n50_recomputed_2s": 0.025 ** (1 / 50),
        "paper_reported_n100": 0.9630,
        "paper_reported_n50": 0.9286,
    }

    # --- 7. 零关键错误的上界（rule of three：1 - 0.05^(1/n)） ---
    tot = sum(F_cur.values())
    res["zero_critical_error_bound"] = {
        str(n): {"rate_ub_one_sided": 1 - 0.05 ** (1 / n),
                 "population_ub": (1 - 0.05 ** (1 / n)) * tot}
        for n in (600, 1200, 1600, 2000)}

    # --- 8. 分层权重敏感性：各层掉 1pp / 10pp 对加权点估计的影响 ---
    base_p = {h: 0.97 for h in F_cur}
    sens = {}
    for h in F_cur:
        for drop in (0.01, 0.10):
            q = dict(base_p)
            q[h] -= drop
            sens[f"{h}-{int(drop*100)}pp"] = sum(F_cur[k] / tot * q[k] for k in F_cur)
    res["layer_masking_sensitivity"] = {"base": 0.97, **sens}

    # --- 9. 遮罩容忍度：小层真值一路掉到多少，门仍会误判通过 ---
    a = {"overfull": 1300, "normal": 300}
    tol = {}
    for p_n in (0.95, 0.9, 0.8, 0.7, 0.6, 0.5, 0.3):
        layers = {"overfull": {"N": F_cur["overfull"], "n": a["overfull"], "k": 0.97 * a["overfull"]},
                  "normal": {"N": F_cur["normal"], "n": a["normal"], "k": p_n * a["normal"]}}
        s = strat(layers, Z1)
        tol[str(p_n)] = {"weighted": s["point"], "gate_lo": wilson_neff(s["point"], s["se"]),
                         "passes": wilson_neff(s["point"], s["se"]) >= 0.95}
    res["masking_tolerance_1600_1300_300"] = tol

    # --- 10. per-layer 护栏：n_normal 下，层真值 p 对应的 Wilson 单侧下界 ---
    res["per_layer_guardrail"] = {
        str(n_n): {str(p): wilson(round(p * n_n), n_n, Z1)[1]
                   for p in (0.90, 0.92, 0.93, 0.95, 0.97)}
        for n_n in (250, 300, 400)}

    # --- 11. 两次查看的 α 膨胀 ---
    res["two_look_alpha"] = {
        "same_z_both_looks_upper_bound": 1 - (1 - 0.05) ** 2,
        "bonferroni_each_alpha": 0.025,
        "bonferroni_each_z": Z2,
        "obf_like_first_z": 2.5758293035489004,
    }

    # --- 12. Wald vs Wilson-on-n_eff 的口径差 ---
    wv = {}
    for n, p in ((600, 0.97), (1200, 0.9594), (1200, 0.965), (1600, 0.9588),
                 (1600, 0.961), (1600, 0.965), (1600, 0.97)):
        wald = p - Z1 * math.sqrt(p * (1 - p) / n)
        wl = wilson_neff(p, math.sqrt(p * (1 - p) / n))
        wv[f"n{n}_p{p}"] = {"wald": wald, "wilson_neff": wl, "delta_pp": (wald - wl) * 100}
    res["wald_vs_wilson"] = wv

    # --- 13. 低真值区（0.95–0.96）的功效：这就是门的分辨率下限 ---
    lowp = {}
    for label, a in (("prop-1600", alloc_prop(F_cur, 1600)),
                     ("guard-1600-1300/300", {"overfull": 1300, "normal": 300})):
        lowp[label] = {str(p): mc_power(p, F_cur, a) for p in (0.95, 0.955, 0.958, 0.96)}
    res["power_low_precision"] = lowp

    print(json.dumps(res, ensure_ascii=False, indent=1))
    (OUT / "d1_design.json").write_text(json.dumps(res, ensure_ascii=False, indent=1),
                                        encoding="utf-8", newline="\n")
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
