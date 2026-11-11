#!/usr/bin/env python3
"""批次 49 标定报告 · 补充读数（只读 CSVs）

安全写法：路径一律经 `safe_open` 校验——只允许读取脚本所在目录（标定证据目录）下的
白名单文件，拒绝任何 `..` 或目录外路径。

输出（供报告引用）：
  1. 每层每臂的 NO_EQUAL_TIME 计数与等时相对误差中位
  2. 基线 Y 的留出/开发分布（5 场考试科覆盖的算术平均）
  3. G6 各臂 max_daily_items / days_at_item_cap 中位数与合计（机制解释：MAX_BEAM_STEPS=20 封顶）
  4. 掌握阈值三组的 coverage / retention / misjudged 中位数（dev 与 holdout 分层）
  5. 前缀同一性汇总
"""
import csv
import importlib
import json
import os
import statistics

HERE = os.path.dirname(os.path.abspath(__file__))
PARENT = os.path.dirname(HERE)

# 白名单：文件必须位于 PARENT（标定证据目录）且名字在此集合内
ALLOWED = {
    "g6-runs.csv",
    "weight-runs.csv",
    "thresholds.csv",
    "prefix-identity.json",
    "run-manifest.json",
    "g6-decisions.json",
    "weight-decisions.json",
    "threshold-readings.json",
    "decision-recompute.txt",
}


def safe_open(name):
    if os.path.basename(name) != name or ".." in name:
        raise SystemExit(f"非法文件名: {name!r}")
    if name not in ALLOWED:
        raise SystemExit(f"不在白名单: {name!r}")
    path = os.path.normpath(os.path.join(PARENT, name))
    if os.path.dirname(path) != os.path.normpath(PARENT):
        raise SystemExit(f"越界路径: {path!r}")
    return path


def fnum(text):
    text = (text or "").strip()
    return None if text == "" else float(text)


def load(name):
    with open(safe_open(name), newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def med(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def main():
    g6, weight, thresholds = load("g6-runs.csv"), load("weight-runs.csv"), load("thresholds.csv")

    print("== 1. NO_EQUAL_TIME 与等时误差 ==")
    for label, rows, key in (
        ("G6", g6, lambda r: r["f"]),
        ("weights", weight, lambda r: r["arm"]),
    ):
        groups = {}
        for row in rows:
            groups.setdefault((row["layer"], key(row)), []).append(row)
        print(f"-- {label} --")
        for (layer, arm), group in sorted(groups.items()):
            timed = [r for r in group if (r.get("y") or "").strip()]
            no_eq = len(group) - len(timed)
            errs = [e for e in (fnum(r.get("equal_time_rel_error")) for r in timed) if e is not None]
            print(
                f"  {layer:<8}{str(arm):<20} rows={len(group):>3} timed={len(timed):>3} "
                f"no_equal_time={no_eq:>3} rho_err_median={med(errs) if errs else 'null'}"
            )
        print()

    print("== 2. 基线 Y（5 场考试窗口覆盖的算术平均）==")
    for layer in ("dev", "holdout"):
        ys = sorted(
            {
                round(fnum(r["baseline_y"]), 6)
                for r in g6
                if r["layer"] == layer and fnum(r.get("baseline_y")) is not None
            }
        )
        print(f"  {layer:<8} distinct={len(ys)} min={min(ys) if ys else None} "
              f"median={statistics.median(ys) if ys else None} max={max(ys) if ys else None}")
        print(f"           values={ys}")
    print()

    print("== 3. G6 每日题数封顶（MAX_BEAM_STEPS=20）==")
    for f_value in sorted({float(r["f"]) for r in g6}):
        for layer in ("dev", "holdout"):
            rows = [r for r in g6 if float(r["f"]) == f_value and r["layer"] == layer
                    and (r.get("y") or "").strip()]
            if not rows:
                continue
            caps = [fnum(r["days_at_item_cap"]) for r in rows]
            maxd = [fnum(r["max_daily_items"]) for r in rows]
            print(f"  f={f_value:.2f} {layer:<8} n={len(rows):>2} "
                  f"days_at_cap_median={med(caps)} days_at_cap_sum={sum(c for c in caps if c)} "
                  f"max_daily_items_median={med(maxd)}")
    print()

    print("== 4. 掌握阈值读数（coverage / retention / misjudged 中位数）==")
    groups = {}
    for row in thresholds:
        key = (row["layer"], row["truth_arm"], row["policy"], int(row["day"]))
        groups.setdefault(key, []).append(row)
    for key in sorted(groups, key=lambda k: (k[0], k[1], k[2], k[3])):
        layer, truth, policy, day = key
        rows = groups[key]
        print(f"  {layer:<8}{truth:<8}{policy} day={day:>3} n={len(rows):>2} "
              f"coverage_med={med([fnum(r['coverage']) for r in rows])} "
              f"share={med([fnum(r['coverage_share']) for r in rows])} "
              f"misjudge_med={med([fnum(r['misjudged_rate']) for r in rows])} "
              f"ret30->60={med([fnum(r['retention_from_day30_at_60']) for r in rows])} "
              f"ret30->90={med([fnum(r['retention_from_day30_at_90']) for r in rows])} "
              f"ret30->120={med([fnum(r['retention_from_day30_at_120']) for r in rows])}")
    print()

    print("== 5. 前缀同一性 ==")
    with open(safe_open("prefix-identity.json"), encoding="utf-8") as handle:
        prefix = json.load(handle)
    checks = prefix["checks"]
    good = sum(1 for c in checks if c["review_log_prefix_equal"] and c["daily_workload_prefix_equal"])
    worlds = sorted({(c["layer"], c["seed"]) for c in checks})
    print(f"  checks={len(checks)} both_equal={good} all_equal_flag={prefix['all_equal']}")
    print(f"  worlds covered={len(worlds)} layers={sorted({w[0] for w in worlds})}")


if __name__ == "__main__":
    importlib.reload(statistics)
    main()
