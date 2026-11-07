#!/usr/bin/env python3
"""生成报告 JSON 的内容（只打印到 stdout，不写任何文件）。

调用方负责重定向：
  python -I build_report_json.py > ../../../2026-11-08-review-calibration-report.json

只读标定证据目录下的白名单文件；所有数字从证据 JSON 转换（hex -> decimal），
不做任何重新解释。裁决字段与执行者 JSON 逐字一致（本会话已独立复算核对）。
"""
import json
import os
import sys

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
    if value is None:
        return None
    if isinstance(value, str) and "0x" in value:
        return float.fromhex(value)
    return value


def num_or_null(value):
    d = dec(value)
    return None if d is None else round(d, 6)


def rewrite_arm(arm):
    out = dict(arm)
    for key in ("median_delta", "dev_median_delta", "min_effect_one_kc"):
        if key in out:
            out[key + "_decimal"] = num_or_null(out[key])
    if "bootstrap_ci95" in out:
        ci = out["bootstrap_ci95"]
        out["bootstrap_ci95_decimal"] = None if not ci else [round(dec(ci[0]), 6), round(dec(ci[1]), 6)]
    return out


def build():
    g6 = load("g6-decisions.json")
    weight = load("weight-decisions.json")
    thr = load("threshold-readings.json")
    manifest = load("run-manifest.json")

    per_seed = []
    for row in g6["table"]:
        entry = {"seed": row["seed"]}
        for f in ("0.150000", "0.250000", "0.350000", "0.500000"):
            value = row.get(f"f={f}")
            entry[f"f={f}"] = {"hex": value, "decimal": num_or_null(value)}
        per_seed.append(entry)

    return {
        "meta": {
            "batch": "49",
            "document": "docs/research/2026-11-08-review-calibration-report.md",
            "model_based": True,
            "prereg": "docs/research/2026-11-08-review-calibration-prereg.md",
            "prereg_sha256": "042776e829fe943a736007074ef77ac0e191f7bbeca0169b58d3d1a3be96b843",
            "prereg_sha256_verified_in_report_session": True,
            "head": "ba7b5624ea0c2bc3f09dcee247f5852e4f428122",
            "head_verified_in_report_session": True,
            "file_sha256": manifest["file_sha256"],
            "file_sha256_baseline_before_seam": manifest["file_sha256_baseline"],
            "file_sha256_verified_in_report_session": True,
            "holdout_run_id": manifest["holdout_run_id"],
            "holdout_run_id_proves_single_execution": False,
            "holdout_run_id_note": "cross-process-rerun 复用了同一 id（环境变量固定）；单次性以两份 manifest 的 started_at/run 数 + 逐位相同 digest 为证据",
            "primary_metric": "exam_subject_coverage_in_window",
            "g6_grid": manifest["g6_grid"],
            "g6_baseline_f": manifest["g6_baseline_f"],
            "weight_grid": manifest["weight_grid"],
            "rho_grid": manifest["rho_grid"],
            "equal_time_tolerance": 0.015,
            "brier_delta_tolerance": 0.005,
            "dev_seeds": manifest["dev_seeds"],
            "holdout_seeds": manifest["holdout_seeds"],
            "simulations": {
                "physical_runs": manifest["simulation_runs_physical"],
                "logical_runs": manifest["simulation_runs_logical"],
                "prereg_budget_range": "1380-1480",
                "budget_deviation": "约 2.1-2.3 倍；权重臂按预注册 5.3 套 rho 网格产生额外 run，8.1 的 run 矩阵低估了这部分",
                "started_at": manifest["started_at"],
                "finished_at": manifest["finished_at"],
                "wall_clock_seconds": manifest["wall_clock_seconds"],
            },
            "thresholds_evaluated_not_changed": manifest["thresholds_evaluated_not_changed"],
            "decision_authority": "预注册 4.3（G6）/ 5.4（权重）；默认分支 = KEEP",
            "report_session_ran_gradle": False,
            "report_session_ran_simulation": False,
            "report_session_touched_device_or_real_db": False,
        },
        "g6": {
            "verdict": g6["verdict"],
            "adopted_f": g6["adopted_f"],
            "current_value": 0.5,
            "recommended_value": 0.5,
            "action": "KEEP_CURRENT",
            "grid_endpoint_excluded": True,
            "no_equal_time_cells": g6["no_equal_time_cells"],
            "arms": [rewrite_arm(a) for a in g6["arms"]],
            "holdout_per_seed_delta": per_seed,
        },
        "weights": {
            "verdict": weight["verdict"],
            "adopted": weight["adopted"],
            "passing": weight["passing"],
            "at_most_one_coordinate": True,
            "adopted_coordinate": None,
            "arms": [rewrite_arm(a) for a in weight["arms"]],
            "coordinate_recommendations": [
                {"coordinate": "due", "current": 5.0, "source": "AlgorithmConstants.kt:114",
                 "recommended": 5.0, "action": "KEEP_CURRENT",
                 "reason": "最好臂 x1.25 中位 +0.0833，但符号 6/8、护栏不过、且为网格端点"},
                {"coordinate": "weakness", "current": 3.0, "source": "AlgorithmConstants.kt:115",
                 "recommended": 3.0, "action": "KEEP_CURRENT",
                 "reason": "x1.25 7/10 最接近，仍差 1 个 world 且为端点；x0.75 只有 n=2"},
                {"coordinate": "exam", "current": 2.0, "source": "AlgorithmConstants.kt:119",
                 "recommended": 2.0, "action": "KEEP_CURRENT",
                 "reason": "三臂中位都 <= 1 KC；x1.25 在 dev 只有 2/10 同号（方向不一致）"},
                {"coordinate": "kc-drop", "current": 2.5,
                 "source": "ReviewPlannerV2.kt:53 DEFAULT_KC_DROP_WEIGHT（单源；旧 ReviewPlanner.kt:22 已删）",
                 "recommended": 2.5, "action": "KEEP_CURRENT",
                 "reason": "x0.75 6/9 且为端点；x0.875 5/8"},
                {"coordinate": "diversity", "current": {"family": 0.3, "source": 0.2, "max_penalty": 1.5},
                 "source": "AlgorithmConstants.kt:123-125", "recommended": "不变",
                 "action": "KEEP_CURRENT",
                 "reason": "x0.75 7/8 最接近但为端点且 CI 下界为 0；x1.125 中位为负"},
                {"coordinate": "avoidance", "current": 1.0, "source": "AlgorithmConstants.kt:118",
                 "recommended": 1.0, "action": "NOT_MEASURED",
                 "reason": "模拟里 ReviewCandidate.avoidance 恒为 false"},
            ],
        },
        "mastery_thresholds": {
            "change_proposed": False,
            "candidates": [
                {"id": "T0", "stability_days": 21.0, "retention": 0.90, "status": "CURRENT"},
                {"id": "T1", "stability_days": 14.0, "retention": 0.90, "status": "READING_ONLY"},
                {"id": "T2", "stability_days": 21.0, "retention": 0.85, "status": "READING_ONLY"},
            ],
            "aggregate_note": "threshold-readings.json 的 n=20 合并 dev+holdout；逐层原始行见 thresholds.csv",
            "aggregates": [
                {
                    "truth_arm": a["truth_arm"], "policy": a["policy"], "day": a["day"], "n": a["n"],
                    "coverage_median": num_or_null(a["coverage_median"]),
                    "coverage_min": a["coverage_min"], "coverage_max": a["coverage_max"],
                    "misjudge_rate_median": num_or_null(a["misjudge_rate_median"]),
                    "structural_zero": a["structural_zero"],
                }
                for a in thr["aggregates"]
            ],
            "retention_note": "锚点第 30 天；holdout 层分母仅 1-2 个 KC；第 30 天掌握集为空时记 null",
            "constraint": "ask 硬约束：21 天 / 0.9 不改；本批不产生提案",
        },
        "guards": {
            "G1_daily_budget": "PASS：21/21 臂 overrun_strict = overrun_tolerance = 0",
            "G2_non_exam_starvation": "VIOLATED：每个臂至少 1 个 holdout world 违反",
            "G3_kc_gap_p95": "VIOLATED：多数臂；基线 kcGapP95 在 7/10 holdout world 就是 90 天",
            "G4_brier_delta": "PASS：21/21 臂 |median brier delta| <= 0.0076（最差为改善方向）",
            "reachability": "无任何臂能在 10 个 holdout world 上同时过 G2 与 G3 —— 本世界形状下门不可达",
        },
        "mechanism": {
            "beam_step_cap": {"constant": "ReviewPlannerV2.kt:1133 MAX_BEAM_STEPS = 20",
                              "applied_at": "ReviewPlannerV2.kt:192 maxSteps = minOf(MAX_BEAM_STEPS, timeBudgetSeconds / minDuration)",
                              "observed": "每个臂、每层 max_daily_items 中位都是 20.0；典型 world 有 22-31 天顶着 20 题上限",
                              "reading": "G6 的扩容增量买不到更多选题步数，只改变同样的 20 题里选哪些"},
            "baseline_y": {"dev": {"min": 0.666667, "median": 0.733333, "max": 0.8},
                           "holdout": {"min": 0.566667, "median": 0.733333, "max": 0.833333}},
            "no_equal_time": {"g6_holdout": "12/40", "weights_holdout": "46/170",
                              "g6_all": "22/80", "weights_all": "92/340"},
        },
        "decision": {
            "constants_changed": False,
            "version_bump": False,
            "version": "review-planner-v4",
            "golden_regenerated": False,
            "golden_note": "若采纳 G6，三组金样应零差异（金样夹具显式传 budgetSeconds = 1800，预注册 11.3）；本批未采纳，故未重生成",
            "model_based": True,
        },
        "machine_checks": [
            {"id": "1", "rule": "预注册未被篡改", "status": "PASS", "ran_in_report_session": True},
            {"id": "2", "rule": "代码基线可复现", "status": "PARTIAL",
             "ran_in_report_session": True,
             "note": "HEAD 与 11 个文件哈希匹配 manifest；manifest 钉的是实验后（seam 后）哈希，与预注册 1.1 的实验前基线有 3 个文件不同（预期）"},
            {"id": "3", "rule": "种子层不混用", "status": "NOT_RUN_IN_REPORT_SESSION", "ran_in_report_session": False},
            {"id": "4", "rule": "同 seed 逐位一致", "status": "PASS", "ran_in_report_session": True,
             "note": "determinism.json 3/3；本会话跨进程比对 7/7 与 28/28 digest 相同"},
            {"id": "5", "rule": "默认臂 == 基线（seam S3）", "status": "NOT_RUN_IN_REPORT_SESSION", "ran_in_report_session": False},
            {"id": "6", "rule": "golden 世界进 CI", "status": "NOT_RUN_IN_REPORT_SESSION", "ran_in_report_session": False,
             "note": "SimulationDeterminismContractTest 钉 55fe92cc...aacfe（seed=5001/60 天/120 题）"},
            {"id": "7", "rule": "前缀同一性", "status": "PASS", "ran_in_report_session": True, "note": "120/120"},
            {"id": "8", "rule": "日预算不超发", "status": "PASS", "ran_in_report_session": True},
            {"id": "9", "rule": "等时合格率已报", "status": "PASS", "ran_in_report_session": True},
            {"id": "10", "rule": "无数据写 null", "status": "PASS", "ran_in_report_session": True},
            {"id": "11", "rule": "holdout 只跑一次", "status": "PARTIAL_VIOLATION_REGISTERED", "ran_in_report_session": True,
             "note": "cross-process-rerun 重跑了 holdout seed 901（digest 相同）；run id 复用，不能证明单次执行"},
            {"id": "12", "rule": "掌握阈值未动", "status": "PASS", "ran_in_report_session": True,
             "note": "AlgorithmConstants.kt 哈希等于实验前基线；实测 21.0 / 0.9"},
            {"id": "13", "rule": "金样差异被记录", "status": "NOT_APPLICABLE", "ran_in_report_session": True},
            {"id": "14", "rule": "裁决函数可复算", "status": "PASS", "ran_in_report_session": True,
             "note": "recompute_decisions.py：n/guard/pass 21/21 全等；median 差 <= 1e-6（CSV 6 位小数）；positive 在 2 个臂上差 1（f=0.15、diversity x0.75），根因是 |delta|~1e-16 被舍成 0，以全精度 JSON 为准"},
        ],
        "data_quality": [
            {"issue": "落盘 CSV 只有 6 位小数", "effect": "|delta| < 1e-6 不可分辨；1 处 world（seed 907, f=0.15）的全精度差 0x1.0p-53 在 CSV 显示为 0",
             "impact": "严格 delta>0 会把浮点尘记成正号，2 个臂的 positive 计数 +1；均远低于门 8，不改任何裁决",
             "recommendation": "下批预注册给 delta>0 加数值门槛（如 1e-9）"},
            {"issue": "threshold-readings.json 的 n=20 合并 dev+holdout", "effect": "聚合值不是 holdout-only；逐层原始值在 thresholds.csv"},
            {"issue": "W-slip 误判率结构性为 0", "effect": "该臂读数无信息量；JSON 标 structural_zero=true"},
            {"issue": "holdout_run_id 两次相同", "effect": "id 不能证明单次执行；改用两份 manifest 的 started_at/run 数 + digest 逐位相同作证据"},
        ],
        "blocked": [
            {"item": "真库 per-reason / G6 因果效应", "status": "BLOCKED", "reason": "本批零真库样本、无反事实"},
            {"item": "A 线 FSRS 拟合与 fsrsBeatsBaseline 上线门", "status": "BLOCKED_NOT_RUN", "reason": "driver manifest：A-line not run in this ask"},
            {"item": "参数可辨识性 / 参数恢复", "status": "BLOCKED", "reason": "predictableSampleCount 千级 < 5000"},
            {"item": "avoidance 权重", "status": "NOT_MEASURABLE", "reason": "模拟候选 avoidance 恒 false"},
            {"item": "A0（无考试记录臂）", "status": "NOT_RUN", "reason": "无考试窗口时 Y 无定义"},
            {"item": "容量预览路径的 G6 净效应", "status": "OUT_OF_SCOPE", "reason": "模拟只走计划队列路径"},
        ],
        "unverified": [
            "异机 / 异 JDK 复现",
            "本报告会话未跑 Gradle；测试计数来自执行者 XML 记录",
            "设备 / UI / soak（计划 P3，不在本报告范围）",
        ],
        "reproduce": [
            "python -I -X utf8 report-recompute/recompute_decisions.py g6-runs.csv weight-runs.csv",
            "python -I -X utf8 report-recompute/supplementary_readings.py",
            "python -I -X utf8 report-recompute/arm_tables.py",
            "python -I -X utf8 build_report_json.py > ../../../../2026-11-08-review-calibration-report.json",
            "sha256sum docs/research/2026-11-08-review-calibration-prereg.md",
            "git rev-parse HEAD",
        ],
    }


if __name__ == "__main__":
    report = build()
    sys.stdout.write(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
