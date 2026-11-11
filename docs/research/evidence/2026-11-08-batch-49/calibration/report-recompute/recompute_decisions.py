#!/usr/bin/env python3
"""批次 49 标定报告 · 独立复算（只读 g6-runs.csv / weight-runs.csv）

用途：报告作者不看 driver 的 JSON 结论，仅用 CSV 原始行 + 预注册 §4.3/§5.4 规则重算
      每臂的 n / median delta / 符号一致数 / 四条护栏逐项状态 / pass / reason。
不写任何仓库文件；输出到 stdout（由调用方重定向到 decision-recompute.txt）。

规则（预注册 §3.2 / §4.3 / §5.4 逐条）：
  delta      = y - baseline_y                    （等时臂；y 为空 = NO_EQUAL_TIME，剔除）
  median     = 偶数取中间两值平均（与 driver.median 同定义）
  G1         = 该臂全部 holdout 行 overrun_strict==0 且 overrun_tolerance==0
  G2         = 全部 holdout 行 starved_subjects 为空
  G3         = 全部 holdout 行 kc_gap_p95 <= max(21, baseline_kc_gap_p95)
  G4         = median(holdout brier_delta) <= 0.005（跨 world 中位）
  pass       = n>=8 ∧ median>=1/30 ∧ positive>=8 ∧ G1∧G2∧G3∧G4 ∧ 非网格端点
"""
import csv
import statistics
import sys

MIN_EFFECT = 1.0 / 30.0
G4_TOL = 0.005
KEEP_G3_FLOOR = 21.0


def fnum(text):
    text = (text or "").strip()
    if text == "":
        return None
    return float(text)


def load(path):
    with open(path, newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def summarize(rows, arm_of, baseline_rows, endpoints):
    arms = {}
    for row in rows:
        arm = arm_of(row)
        arms.setdefault(arm, []).append(row)

    out = []
    for arm, arm_rows in arms.items():
        hold = [r for r in arm_rows if r["layer"] == "holdout"]
        hold_timed = [r for r in hold if fnum(r.get("y")) is not None]
        no_eq = len(hold) - len(hold_timed)
        deltas = [fnum(r["y"]) - fnum(r["baseline_y"]) for r in hold_timed]
        median = statistics.median(deltas) if deltas else None
        positive = sum(1 for d in deltas if d > 0.0)

        g1 = all(
            (fnum(r.get("overrun_strict")) or 0) == 0
            and (fnum(r.get("overrun_tolerance")) or 0) == 0
            for r in hold_timed
        )
        g1_rows = {
            "strict": sum(1 for r in hold_timed if (fnum(r.get("overrun_strict")) or 0) != 0),
            "tolerance": sum(1 for r in hold_timed if (fnum(r.get("overrun_tolerance")) or 0) != 0),
        }
        g2_bad = [
            (r["seed"], r["starved_subjects"])
            for r in hold_timed
            if (r.get("starved_subjects") or "").strip() != ""
        ]
        g3_bad = []
        g3_unknown = []
        baseline_used = {}
        for r in hold_timed:
            gap = fnum(r.get("kc_gap_p95"))
            base = fnum(r.get("baseline_kc_gap_p95"))
            if base is None:
                base = baseline_rows.get(r["seed"])
            if base is not None:
                baseline_used[r["seed"]] = base
            if gap is None or base is None:
                g3_unknown.append((r["seed"], gap, base))
                continue
            if gap > max(KEEP_G3_FLOOR, base):
                g3_bad.append((r["seed"], gap, base))
        brier_deltas = [fnum(r["brier_delta"]) for r in hold_timed if fnum(r.get("brier_delta")) is not None]
        g4_median = statistics.median(brier_deltas) if brier_deltas else None
        g4 = g4_median is None or g4_median <= G4_TOL

        # 与 driver 自己写进 CSV 的 guard_ok 逐行比对（只用于交叉核对，不参与裁决）
        driver_guard = [r.get("guard_ok", "").strip().lower() == "true" for r in hold_timed]
        mine_rows = []
        for r in hold_timed:
            gap = fnum(r.get("kc_gap_p95"))
            base = fnum(r.get("baseline_kc_gap_p95"))
            if base is None:
                base = baseline_rows.get(r["seed"])
            ok = (
                (fnum(r.get("overrun_strict")) or 0) == 0
                and (fnum(r.get("overrun_tolerance")) or 0) == 0
                and (r.get("starved_subjects") or "").strip() == ""
                and gap is not None
                and base is not None
                and gap <= max(KEEP_G3_FLOOR, base)
            )
            mine_rows.append(ok)
        agree = sum(1 for a, b in zip(driver_guard, mine_rows) if a == b)

        guard_ok = g1 and not g2_bad and not g3_bad and not g3_unknown and g4
        n = len(deltas)
        endpoint = arm in endpoints
        passed = (
            n >= 8
            and median is not None
            and median >= MIN_EFFECT
            and positive >= 8
            and guard_ok
            and not endpoint
        )
        if n < 8:
            reason = "UNDERPOWERED"
        elif median is not None and median < MIN_EFFECT:
            reason = "median_delta_below_one_kc"
        elif positive < 8:
            reason = "sign_consistency_below_8_of_10"
        elif not guard_ok:
            reason = "guardrail_violated"
        elif endpoint:
            reason = "grid_endpoint_not_adopted"
        else:
            reason = "passed"

        out.append(
            {
                "arm": arm,
                "n": n,
                "no_equal_time": no_eq,
                "median": median,
                "positive": positive,
                "g1": g1,
                "g1_rows": g1_rows,
                "g2_bad": g2_bad,
                "g3_bad": g3_bad,
                "g3_unknown": g3_unknown,
                "g4_median": g4_median,
                "guard_ok": guard_ok,
                "endpoint": endpoint,
                "pass": passed,
                "reason": reason,
                "driver_guard_agree": f"{agree}/{len(driver_guard)}",
            }
        )
    return out


def report(title, summary, label):
    print(f"== {title} ==")
    print(
        f"{'arm':<22}{'n':>3}{'noeq':>5}{'median':>12}{'pos':>5}"
        f"  {'G1':<3}{'G2':<3}{'G3':<3}{'G4':<3}{'guard':<7}{'endpoint':<9}{'pass':<6}{'driver':<7}reason"
    )
    for s in summary:
        med = "null" if s["median"] is None else f"{s['median']:+.6f}"
        g4 = "None" if s["g4_median"] is None else f"{s['g4_median']:+.4f}"
        print(
            f"{s['arm']:<22}{s['n']:>3}{s['no_equal_time']:>5}{med:>12}{s['positive']:>5}"
            f"  {str(s['g1'])[0]:<3}{str(not s['g2_bad'])[0]:<3}{str(not s['g3_bad'] and not s['g3_unknown'])[0]:<3}"
            f"{str(s['g4_median'] is None or s['g4_median'] <= G4_TOL)[0]:<3}"
            f"{str(s['guard_ok']):<7}{str(s['endpoint']):<9}{str(s['pass']):<6}"
            f"{s['driver_guard_agree']:<7}{s['reason']}   [brierΔmed={g4}]"
        )
    print()
    for s in summary:
        if s["g3_bad"]:
            print(f"  G3 violations {s['arm']}: " + ", ".join(
                f"seed={seed} gap={gap:.0f} base={base:.0f}" for seed, gap, base in s["g3_bad"]
            ))
        if s["g2_bad"]:
            print(f"  G2 violations {s['arm']}: {s['g2_bad']}")
    print()
    passing = [s["arm"] for s in summary if s["pass"]]
    print(f"  -> pass={passing if passing else 'NONE'}")
    print()


def main():
    g6_path, weight_path = sys.argv[1], sys.argv[2]
    g6 = load(g6_path)
    weight = load(weight_path)

    # 基线 kc_gap_p95（每 world 一个值）：G6 CSV 的 baseline_kc_gap_p95 列即为该 world 的基线。
    # 权重 CSV 没有这一列，必须由这里补齐（只收非空值；NO_EQUAL_TIME 行整行为空）。
    baseline_gap = {}
    for row in g6:
        if row["layer"] != "holdout":
            continue
        value = fnum(row.get("baseline_kc_gap_p95"))
        if value is not None:
            baseline_gap.setdefault(row["seed"], value)
    print(f"baseline kc_gap_p95 per holdout seed: {baseline_gap}")
    print()

    # 端点 = 网格上端 f=0.50（预注册 §4.3 的 R7）
    g6_arms = summarize(g6, lambda r: f"f={float(r['f']):.2f}", baseline_gap, {"f=0.50"})
    report("G6 (holdout, equal-time arms)", g6_arms, "G6")

    weight_arms = summarize(
        weight,
        lambda r: r["arm"],
        baseline_gap,
        {
            "W/due/x0.75", "W/due/x1.25",
            "W/weak/x0.75", "W/weak/x1.25",
            "W/exam/x0.875", "W/exam/x1.25",
            "W/kc-drop/x0.75",
            "W/diversity/x0.75", "W/diversity/x1.25",
        },
    )
    report("weights (holdout, equal-time arms)", weight_arms, "W")

    g6_rows = sum(1 for r in g6 if r["layer"] == "holdout")
    w_rows = sum(1 for r in weight if r["layer"] == "holdout")
    print(f"  holdout rows: g6-runs.csv={g6_rows}, weight-runs.csv={w_rows}")
    print(f"  NO_EQUAL_TIME (y 空) 合计: "
          f"g6={sum(1 for r in g6 if r['layer'] == 'holdout' and not (r.get('y') or '').strip())}, "
          f"w={sum(1 for r in weight if r['layer'] == 'holdout' and not (r.get('y') or '').strip())}")


if __name__ == "__main__":
    main()
