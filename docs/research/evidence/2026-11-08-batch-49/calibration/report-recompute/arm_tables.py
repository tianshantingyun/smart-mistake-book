#!/usr/bin/env python3
"""把 *-decisions.json 的 hex 浮点转成十进制，生成报告用的臂表（Markdown）。

安全写法：只读脚本父目录白名单文件，写死输出到 stdout。
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
PARENT = os.path.dirname(HERE)
ALLOWED = {"g6-decisions.json", "weight-decisions.json", "threshold-readings.json", "run-manifest.json"}


def safe_open(name):
    if os.path.basename(name) != name or ".." in name or name not in ALLOWED:
        raise SystemExit(f"拒绝读取: {name!r}")
    path = os.path.normpath(os.path.join(PARENT, name))
    if os.path.dirname(path) != os.path.normpath(PARENT):
        raise SystemExit(f"越界: {path!r}")
    return path


def load(name):
    with open(safe_open(name), encoding="utf-8") as handle:
        return json.load(handle)


def dec(value):
    """hex 字符串 -> 十进制（含负号形式 -0x1.8p3）；null 原样返回。"""
    if value is None:
        return None
    if isinstance(value, str) and "0x" in value:
        try:
            return float.fromhex(value)
        except ValueError:
            return value
    return value


def fmt(value, digits=4):
    if value is None:
        return "null"
    if isinstance(value, str):
        return value
    return f"{value:+.{digits}f}"


def ci_text(pair):
    if not pair:
        return "null"
    lo, hi = dec(pair[0]), dec(pair[1])
    return f"[{lo:+.4f}, {hi:+.4f}]"


def main():
    g6 = load("g6-decisions.json")
    weight = load("weight-decisions.json")

    print("### G6 arms (dec)\n")
    print("| arm | f | n | median Δ (Y) | ≥1 KC? | #Δ>0 | dev median | guard | pass | reason | bootstrap 95% |")
    print("|---|---|---|---|---|---|---|---|---|---|---|")
    for arm in g6["arms"]:
        med = dec(arm["median_delta"])
        print(
            f"| {arm['id']} | {arm['f']:.2f} | {arm['n']} | {fmt(med)} | "
            f"{'yes' if med is not None and med >= 1.0 / 30 else 'no'} | {arm['positive']} | "
            f"{fmt(dec(arm['dev_median_delta']))} ({arm['dev_positive']}/{arm['dev_n']}) | "
            f"{arm['guard_ok']} | {arm['pass']} | {arm['reason']} | {ci_text(arm.get('bootstrap_ci95'))} |"
        )
    print(f"\nadopted_f = {g6['adopted_f']}; verdict = {g6['verdict']}; no_equal_time_cells = {g6['no_equal_time_cells']}\n")

    print("### weight arms (dec)\n")
    print("| arm | coordinate | ×  | n | median Δ (Y) | ≥1 KC? | #Δ>0 | dev median | guard | pass | reason | bootstrap 95% |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|")
    for arm in weight["arms"]:
        med = dec(arm["median_delta"])
        print(
            f"| {arm['id']} | {arm['coordinate']} | {arm['multiplier']:.3f} | {arm['n']} | {fmt(med)} | "
            f"{'yes' if med is not None and med >= 1.0 / 30 else 'no'} | {arm['positive']} | "
            f"{fmt(dec(arm['dev_median_delta']))} ({arm['dev_positive']}/{arm['dev_n']}) | "
            f"{arm['guard_ok']} | {arm['pass']} | {arm['reason']} | {ci_text(arm.get('bootstrap_ci95'))} |"
        )
    print(f"\npassing = {weight['passing']}; adopted = {weight['adopted']}; verdict = {weight['verdict']}\n")

    print("### threshold readings (pooled dev+holdout, n=20)\n")
    thr = load("threshold-readings.json")
    print("| truth | policy | day | n | coverage (median) | min | max | misjudge median | structural_zero |")
    print("|---|---|---|---|---|---|---|---|---|")
    for agg in thr["aggregates"]:
        cov = dec(agg["coverage_median"])
        mis = dec(agg["misjudge_rate_median"])
        print(
            f"| {agg['truth_arm']} | {agg['policy']} | {agg['day']} | {agg['n']} | "
            f"{cov:g} | {agg['coverage_min']} | {agg['coverage_max']} | {mis:g} | {agg['structural_zero']} |"
        )


if __name__ == "__main__":
    main()
