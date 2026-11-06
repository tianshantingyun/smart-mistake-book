#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""切分金标门（AUD-08）红绿判定器：冻结校验 → 度量 → 0.9/0.05 判定 → 报告/证据/裁定落盘。

## 它消灭的具体失败

度量脚本 `measure_split_accuracy.py` 自开端口起"只报数"：exit 0 表示度量完成、**不等于
门通过**（脚本头部自述）。于是出现三件没有任何门会红的事：

  ① 判门步骤不存在：0.5714 vs 0.9 是过是不过，全靠读记录的人自己心算；
  ② 金标冻结与度量脱节：度量可以拿一份被改过字节的金标跑出一套"漂亮数字"，
     而 validate 的 C1–C7 从未在度量前被强制执行（两条命令各自独立）；
  ③ 证据无处落盘：门判定的输入 sha、命令、exit code、逐页裁定没有固定格式，
     复算对照无从下手（R4 §3.7 验收："金标 sha、应用导出 sha、设备/DB 版本、
     命令、exit code 齐全"）。

本脚本 = 判门步骤：先跑 `validate_split_golden.validate`（C1–C7 全过才继续，带病金标
一律 exit 2 拒判），再以 `--json` 调 `measure_split_accuracy.py` 拿机器口径指标，
按**闭边界** ①≥0.9 且 ③≤0.05 判定（与度量脚本常量逐字一致，阈值出处随报告落盘），
最后产出确定性 JSON 报告（`--report-json`）、证据文件（`--evidence-out`）与
逐页裁定（`--rulings-out`）。

## 报告里必须有的三块（2026-11-02 批次 40）

- `golden.evidenceGradeLevel`：等级从产物读（v1 走冻结常量映射 G2）；报告**不硬编码**
  `model-labeled` 字符串。
- `hardRedLines.fragmentedQuestions`：切残硬红线（required=0）。n≥20 时 1/20=0.05 会通过
  ③ 的闭边界，所以"门后自动化"必须以 `gate.passed && hardRedLines.satisfied` 为前提。
- `scope`：门值适用范围声明（只对本次金标 sha × 应用导出 sha 成立，**不外推**为
  "当前设备库/该模型在 155 页上的准确率"）。

## 逐页裁定与来源（只记录，不进口径）

证据 §5 逐页表写 file / 来源 / 金标题数 / 应用框数 / ①一致 / IoU≥0.5 / 切残。"来源"取自
应用导出的页级 `nameSource`+`jobId`（`export_split_eval.py` 写入）；导出没有溯源块时写
`UNVERIFIED（导出无溯源块）`（4C 历史导出即此形态），**不以文件名反推来源**。来源字段
不参与任何分子/分母/阈值。`--rulings-out` 写一 run 一份的 `rulings.json`（见 `rulings.py`），
门不可判（NOT_EVALUABLE）时**不生成**——不可判 ≠ 没发现问题。

## 自动化裁定（本脚本不切任何产品开关）

裁定 37 / 4C③：**门不过不允许自动入库（门后自动化不启用），切残不得自动入库**。
门**过**也不在本脚本启用任何东西——"门后自动化"属产品裁定（4C③），须另立 PRD/裁定。
本脚本对产品代码零写入、零网络；"门不过自动化不启用"由仓库事实保证：仓内不存在
门控开关/自动入库实现（2026-11-02 实测 grep，见 GATE.md §0）。

## 退出码（与度量脚本同语义扩展）

  0  门 PASS（①≥0.9 且 ③≤0.05，本次度量）
  1  门 FAIL（度量完成、阈值未达）——自动化不启用
  2  不可判门：金标缺失/冻结校验失败（C1–C7）/ 度量输入坏 / 报告/证据/裁定写不了 —— 自动化不启用
  3  不可判门：应用导出缺失（如实报缺，不猜指标）—— 自动化不启用

## 用法（仓库根）

    python tools/split_golden/check_split_gate.py
        [--golden tools/split_golden/tables/split_golden_v1.json]   # 默认即此
        [--sidecar <file>]                                          # 默认 <golden>.sha256
        [--app build/split-eval/app-split.json]                     # 默认即此
        [--report-json <file>]                                      # 确定性 JSON 报告落盘
        [--evidence-out <file>]                                     # R4 模板精神证据 md（不覆盖已有）
        [--rulings-out <dir>]                                       # <dir>/<runId>.json（不覆盖已有）

门说明（五类指标的分母/阈值/样本量/统计区间/严重度与未实现项的如实登记）见
同目录 `GATE.md`；工具链总览见 `README.md`。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import secrets
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

HERE: Path = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import validate_split_golden as V  # noqa: E402
import rulings as RULINGS  # noqa: E402

REPO: Path = HERE.parents[1]
MEASURE_SCRIPT: Path = HERE / "measure_split_accuracy.py"
DEFAULT_GOLDEN: Path = V.DEFAULT_FILE
DEFAULT_APP: Path = REPO / "build" / "split-eval" / "app-split.json"

COUNT_GATE = 0.9
FRAGMENT_GATE = 0.05
REPORT_SCHEMA_VERSION = 1
MEASURE_TIMEOUT_SECONDS = 300
EVIDENCE_MAX_BYTES = 64 * 1024          # R4：单证据文件 ≤64 KiB
EVIDENCE_MAX_LINES = 400                # R4：单证据文件 ≤400 行

# 阈值出处（不许无声改值；改值必须先裁定并留旧值对照——R4 §3.7 验收原文）。
_STAGE1_SOURCE = {
    "file": "docs/research/2026-10-14-4c-stage1-record.md",
    "lines": ":19-22",
    "quote": ("本轮门式（计划 E4 的建议值，标\"立项自定\"）：① ≥ 0.9 且 ③ ≤ 0.05；"
              "② 只度量、不在门式内。低于门 → 不启用门后自动化（4C③）"),
}
THRESHOLDS: dict[str, dict[str, Any]] = {
    "countExactMin": {
        "value": COUNT_GATE,
        "comparison": ">=",
        "metric": "① 题数完全一致率 countExactRate（分母=可比金标页数）",
        "source": dict(_STAGE1_SOURCE,
                       also_stated_in={"file": "tools/split_golden/measure_split_accuracy.py",
                                       "constant": "COUNT_GATE = 0.9"}),
    },
    "fragmentMax": {
        "value": FRAGMENT_GATE,
        "comparison": "<=",
        "metric": "③ 切残率 fragmentRate（分母=金标总题数；切残不得自动入库）",
        "source": dict(_STAGE1_SOURCE,
                       also_stated_in={"file": "tools/split_golden/measure_split_accuracy.py",
                                       "constant": "FRAGMENT_GATE = 0.05"}),
    },
}
AUTOMATION_RULING = ("门后自动化不启用（裁定 37 / 4C③：门不过不允许自动入库，切残不得自动入库）。"
                     "本门脚本不切任何产品开关；开启门后自动化须另立产品裁定（4C③）。")

# 测试经 G.LAST_REPORT 读取最近一次 main() 的报告（避免为测试改 run 的签名）。
LAST_REPORT: dict | None = None


def wilson95(successes: int, n: int, z: float = 1.96) -> list[float]:
    """Wilson 95% 区间（描述性统计，非门；样本量 7 页/16 题时区间极宽，见 GATE.md）。"""
    if n == 0:
        return [0.0, 1.0]
    p = successes / n
    denom = 1 + z * z / n
    center = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return [max(0.0, center - half), min(1.0, center + half)]


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _describe_golden(golden: Path, sidecar: Path,
                     doc: dict | None, failures: list[str]) -> dict:
    meta: dict[str, Any] = {"file": golden.name, "path": str(golden),
                            "sha256": _sha256(golden) if golden.is_file() else None,
                            "sidecar": sidecar.name,
                            "freezeValidated": not failures,
                            "freezeFailures": failures}
    if isinstance(doc, dict):
        pages = doc.get("pages")
        if isinstance(pages, list):
            meta["pageCount"] = len(pages)
            meta["questionCount"] = sum(
                len(page.get("boxes") or []) for page in pages
                if isinstance(page, dict))
        meta["evidenceGrade"] = doc.get("evidenceGrade")
        meta["generatedAt"] = doc.get("generatedAt")
        # 等级必须从产物读（v1 走常量映射），报告不得硬编码"model-labeled"。
        level, recomputable, _level_failures = V.evidence_level(doc)
        meta["evidenceGradeLevel"] = level
        meta["evidenceLevelRecomputable"] = recomputable
        meta["evidenceLevelSource"] = (
            "artifact_field" if recomputable
            else ("frozen_constant_mapping" if level else None))
    return meta


def _describe_app(app: Path) -> dict:
    exists = app.is_file()
    return {"file": app.name, "path": str(app), "exists": exists,
            "sha256": _sha256(app) if exists else None,
            "bytes": app.stat().st_size if exists else None}


def _base_report() -> dict:
    return {"reportSchemaVersion": REPORT_SCHEMA_VERSION,
            "gate": {"passed": None, "state": None},
            "automation": {"enabled": False, "ruling": AUTOMATION_RULING},
            "commands": [],
            "perPage": [],
            "metrics": None,
            "hardRedLines": None,
            "scope": None,
            "thresholds": json.loads(json.dumps(THRESHOLDS))  # 深拷贝，防调用方改常量
            }


# ---------------------------------------------------------------- 范围声明（R5 §5.5）

# 范围声明的金标侧理由：随金标版本走（--golden 决定）。v1 文本为历史冻结表述，逐字保留；
# v2 为批次 48 金标形态（24 页、G1、dev/acceptance 16/8 切分）。应用导出通道的身份
# （设备 DB 导出 vs 代理通道）不在本脚本硬编码——它随导出的 source 块走。
SCOPE_REASONS_V1 = (
    "金标 v1 的 7 页不是 155 页总体的概率样本：sampleRule 只记录 155→24 的系统抽样，"
    "24→7 的入集准则未落产物，且自承 24 张里只有 2 张被人眼打开过",
    "训练/开发/验收切分未做：同一批 7 页既调门又验门",
    "证据等级是代理金标（v1 为 G2：模型标注、无人工专家），不是人工金标",
)
SCOPE_REASONS_V2 = (
    "金标 v2 的 24 页是 155 页数据集按步长 6 的系统抽样，不是概率样本；"
    "标注为单模型盲标（G1，model-labeled），无人工专家复核，坐标为模型估读（±0.01–0.02）",
    "v2 已做 dev/acceptance（16/8）切分：dev 16 页参与了切分修复调参，acceptance 8 页"
    "只复算一次；门值不外推为未参与调参页面的准确率",
)


def build_scope(report: dict) -> dict:
    """门值的适用范围：任何引用门值的文本都必须带这句，不得外推（R5 §5.5）。"""
    golden = report.get("golden") or {}
    app = report.get("app") or {}
    level = golden.get("evidenceGradeLevel") or "UNKNOWN"
    metrics = report.get("metrics")
    if metrics:
        count = report["gate"]["count"]
        fragment = report["gate"]["fragment"]
        statement = (
            f"本门值（① {metrics['countExact']['successes']}/"
            f"{metrics['countExact']['denominator']}={count['value']:.4f}、"
            f"③ {metrics['fragment']['successes']}/"
            f"{metrics['fragment']['denominator']}={fragment['value']:.4f}）只对"
            f" 金标 sha `{(golden.get('sha256') or '')[:12]}…` × 应用导出 sha "
            f"`{(app.get('sha256') or '')[:12]}…` 这份样本成立；"
            "**不等于**当前设备库切分准确率，也**不等于**该模型在 155 页数据集上的准确率"
            f"（证据等级 {level}）。")
    else:
        statement = (
            f"本轮门未判定（{(report.get('gate') or {}).get('state')}）：**没有任何门值成立**，"
            "**不等于**\"未发现问题\"，也不得引用历史数字代替本轮结果。")
    return {"notGeneralizable": True,
            "statement": statement,
            "appliesTo": {"goldenSha256": golden.get("sha256"),
                          "appSha256": app.get("sha256")},
            "evidenceGradeLevel": level,
            "reasons": list(SCOPE_REASONS_V1 if "v1" in (golden.get("file") or "")
                            else SCOPE_REASONS_V2)}


def build_hard_red_lines(report: dict) -> dict:
    """切残硬红线（`GATE.md:42`）：不依赖 0.05 阈值，切残题数必须为 0。

    它消灭的具体失败：金标总题数 n≥20 时 1/20=0.05 ≤ 0.05 会让门式放行 1 道切残题，
    与"切残题干不得入库"红线直接冲突；报告此前不显式给出这条红线。
    """
    value = int(report["metrics"]["fragment"]["successes"])
    return {"fragmentedQuestions": {"value": value, "required": 0,
                                    "satisfied": value == 0}}


def run(golden: Path, sidecar: Path, app: Path,
        measure_script: Path = MEASURE_SCRIPT) -> dict:
    """判门 + 范围声明/硬红线。任何不可判门状态都保持 automation.enabled=False。"""
    report = _run_gate(golden, sidecar, app, measure_script)
    report["scope"] = build_scope(report)
    if report.get("metrics"):
        report["hardRedLines"] = build_hard_red_lines(report)
    return report


def _run_gate(golden: Path, sidecar: Path, app: Path,
              measure_script: Path = MEASURE_SCRIPT) -> dict:
    report = _base_report()

    # 1) 金标集版本/冻结：C1–C6 全过才允许判门（带病金标一律 exit 2）。
    if not golden.is_file():
        failures = [f"C1: 金标文件不存在：{golden}"]
        doc = None
    else:
        doc, failures = V.validate(golden, sidecar)
    report["golden"] = _describe_golden(golden, sidecar, doc, failures)
    report["app"] = _describe_app(app)
    if failures:
        report["freezeFailures"] = failures
        report["gate"]["state"] = "NOT_EVALUABLE_FREEZE"
        return report

    # 2) 应用导出：缺失 = 如实报缺（不猜指标）。
    if not app.is_file():
        report["gate"]["state"] = "NOT_EVALUABLE_APP_MISSING"
        return report

    # 3) 度量（--json 机器口径，子进程隔离，超时兜底）。
    command = [sys.executable, str(measure_script),
               "--golden", str(golden), "--app", str(app), "--json"]
    report["commands"].append({"purpose": "measure", "command": command})
    try:
        proc = subprocess.run(command, capture_output=True, text=True,
                              encoding="utf-8", errors="replace",
                              timeout=MEASURE_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        report["commands"][-1]["exitCode"] = None
        report["measureError"] = f"度量子进程超时（>{MEASURE_TIMEOUT_SECONDS}s）"
        report["gate"]["state"] = "NOT_EVALUABLE_DATA"
        return report
    report["commands"][-1]["exitCode"] = proc.returncode
    if proc.returncode != 0:
        report["measureError"] = (proc.stdout or proc.stderr or "").strip()[:2000]
        report["gate"]["state"] = "NOT_EVALUABLE_DATA"
        return report
    try:
        measure_report = json.loads(proc.stdout)
    except ValueError:
        report["measureError"] = "度量 --json 输出不是合法 JSON：" + (proc.stdout or "")[:2000]
        report["gate"]["state"] = "NOT_EVALUABLE_DATA"
        return report
    metrics = measure_report.get("metrics") or {}
    if not isinstance(metrics.get("countExact"), dict) or \
            not isinstance(metrics.get("fragment"), dict):
        report["measureError"] = "度量 JSON 缺 countExact/fragment 指标块"
        report["gate"]["state"] = "NOT_EVALUABLE_DATA"
        return report

    # 4) 判门（闭边界：>= 与 <=；阈值出处见 THRESHOLDS）。
    count_block = metrics["countExact"]
    fragment_block = metrics["fragment"]
    count_value = float(count_block["value"])
    fragment_value = float(fragment_block["value"])
    count_met = count_value >= COUNT_GATE
    fragment_met = fragment_value <= FRAGMENT_GATE
    report["metrics"] = {
        "countExact": dict(count_block, wilson95=wilson95(
            int(count_block["successes"]), int(count_block["denominator"]))),
        "fragment": dict(fragment_block, wilson95=wilson95(
            int(fragment_block["successes"]), int(fragment_block["denominator"]))),
        "iou": metrics.get("iou"),
    }
    report["gate"] = {"passed": bool(count_met and fragment_met),
                      "state": "PASS" if (count_met and fragment_met) else "FAIL",
                      "count": {"value": count_value, "threshold": COUNT_GATE,
                                "comparison": ">=", "met": count_met},
                      "fragment": {"value": fragment_value, "threshold": FRAGMENT_GATE,
                                   "comparison": "<=", "met": fragment_met}}
    report["perPage"] = measure_report.get("pages") or []
    measure_app = measure_report.get("app") or {}
    report["app"]["appSchemaVersion"] = measure_app.get("appSchemaVersion")
    report["app"]["source"] = measure_app.get("source")
    report["appIntegrityMismatchPages"] = measure_report.get("integrityMismatchPages") or []
    report["appSource"] = measure_app.get("source")
    report["measure"] = {"reportSchemaVersion": measure_report.get("reportSchemaVersion"),
                         "caliber": measure_report.get("caliber"),
                         "missingAppPages": measure_report.get("missingAppPages"),
                         "extraAppPages": measure_report.get("extraAppPages")}
    return report


# ---------------------------------------------------------------- 证据落盘（R4 模板精神）

def _page_source_cell(row: dict) -> str:
    """逐页来源：有 nameSource 写来源+job 定位；缺页/无溯源如实标注，不用文件名冒充。"""
    if row.get("appPageMissing"):
        return "应用缺页（0 框计入）"
    name_source = row.get("appNameSource")
    if not isinstance(name_source, str) or not name_source:
        return "UNVERIFIED（导出无溯源块）"
    job = row.get("appJobId")
    index = row.get("appPageIndex")
    if isinstance(job, str) and job and isinstance(index, int) \
            and not isinstance(index, bool):
        return f"{name_source}（`{job}#p{index}`）"
    return name_source


def render_evidence(report: dict, generated_at_utc: str) -> str:
    """R4 模板精神：E-ID 表 + 产物 sha + 命令/exit + 判定 + UNVERIFIED + 体积自查。"""
    gate = report["gate"]
    state = gate["state"]
    exit_code = {"PASS": 0, "FAIL": 1, "NOT_EVALUABLE_FREEZE": 2,
                 "NOT_EVALUABLE_DATA": 2, "NOT_EVALUABLE_APP_MISSING": 3}[state]
    lines: list[str] = []
    lines.append("# AUD-08 切分金标门 —— 本轮门判定证据（R4 模板精神）")
    lines.append("")
    lines.append(f"- 生成时刻（UTC）：{generated_at_utc}")
    lines.append(f"- 门状态：**{state}**（exit {exit_code}）")
    lines.append(f"- 自动化：{report['automation']['ruling']}")
    lines.append(f"- 门阈值：①≥{COUNT_GATE}（立项自定）且 ③≤{FRAGMENT_GATE}；"
                 "出处见 GATE.md 与报告 thresholds（改值须先裁定并留旧值对照）")
    lines.append("")
    lines.append("## 1. E-ID 表")
    lines.append("")
    lines.append("| E-ID | 步骤 | 判定 | 证据（本文件节） |")
    lines.append("|---|---|---|---|")
    freeze_ok = not report.get("freezeFailures")
    lines.append(f"| E-A08-T1 | 金标冻结校验（validate C1–C6+C7） | "
                 f"{'PASS（子步骤）' if freeze_ok else 'FAIL'} | §2 |")
    if state in ("PASS", "FAIL"):
        lines.append("| E-A08-T2 | 度量（measure --json） | PASS（子步骤） | §3、§5 |")
        lines.append(f"| E-A08-T3 | 门判定（0.9/0.05 闭边界） | {state} | §4 |")
    elif state == "NOT_EVALUABLE_APP_MISSING":
        lines.append("| E-A08-T2 | 度量 | **未执行**（应用导出缺失） | §6 |")
        lines.append("| E-A08-T3 | 门判定 | **不可判**（不猜指标） | §6 |")
    else:
        lines.append("| E-A08-T2 | 度量 | **未执行/失败** | §6 |")
        lines.append("| E-A08-T3 | 门判定 | **不可判** | §6 |")
    lines.append("")
    lines.append("## 2. 产物身份（sha256）")
    lines.append("")
    lines.append("| 产物 | 路径 | 字节 | sha256 |")
    lines.append("|---|---|---|---|")
    golden = report["golden"]
    app = report["app"]
    lines.append(f"| 金标 {golden['file']} | `{golden['path']}` | — | `{golden['sha256']}` |")
    if app["exists"]:
        lines.append(f"| 应用导出 {app['file']} | `{app['path']}` | {app['bytes']} | `{app['sha256']}` |")
    else:
        lines.append(f"| 应用导出 | `{app['path']}` | **缺失** | — |")
    source = report.get("appSource")
    if app["exists"]:
        if isinstance(source, dict) and source.get("present"):
            counts = json.dumps(source.get("nameSourceCounts"), ensure_ascii=False,
                                sort_keys=True)
            lines.append(f"| 应用导出 source 块 | 有（db sha256 `{source.get('dbSha256')}`；"
                         f"页名来源 {counts}） | — | — |")
            lines.append(f"| 应用导出输入版本 | app schemaVersion "
                         f"{app.get('appSchemaVersion')}；db user_version "
                         f"{source.get('dbUserVersion')}；快照身份 "
                         f"`{source.get('snapshotIdentitySha256')}`（{source.get('snapshotMode')}）；"
                         f"载荷 `{source.get('payloadSha256')}`；生产者 "
                         f"`{source.get('producerSha256')}`（{source.get('producerContract')}）"
                         " | — | — |")
        else:
            lines.append("| 应用导出 source 块 | **缺失**（页名/设备来源 UNVERIFIED，"
                         "不由文件名反推） | — | — |")
    lines.append("")
    lines.append("## 3. 命令与退出码（可复跑）")
    lines.append("")
    if report["commands"]:
        for index, entry in enumerate(report["commands"], start=1):
            exit_text = entry.get("exitCode")
            lines.append(f"{index}. `{Path(entry['command'][0]).name} "
                         + " ".join(entry["command"][1:]) + f"` → exit {exit_text}")
    else:
        lines.append("（本轮无度量命令——未走到度量步）")
    lines.append("")
    lines.append("## 4. 指标与门判定")
    lines.append("")
    metrics = report["metrics"]
    if metrics:
        count = report["gate"]["count"]
        fragment = report["gate"]["fragment"]
        count_w = metrics["countExact"]["wilson95"]
        fragment_w = metrics["fragment"]["wilson95"]
        lines.append(f"- ① 题数完全一致率 = {metrics['countExact']['successes']}/"
                     f"{metrics['countExact']['denominator']} = {count['value']:.4f} "
                     f"（Wilson95 {count_w[0]:.4f}–{count_w[1]:.4f}，描述性区间非门）；"
                     f"门 {count['value']:.4f} >= {COUNT_GATE} → {'过' if count['met'] else '未过'}")
        iou = metrics.get("iou") or {}
        if iou:
            lines.append(f"- ② 边界 IoU≥0.5 题占比 = {iou.get('successes')}/{iou.get('denominator')}"
                         f" = {iou.get('value', 0):.4f}（只度量，不设门）")
        lines.append(f"- ③ 切残率 = {metrics['fragment']['successes']}/"
                     f"{metrics['fragment']['denominator']} = {fragment['value']:.4f} "
                     f"（Wilson95 {fragment_w[0]:.4f}–{fragment_w[1]:.4f}，描述性区间非门）；"
                     f"门 {fragment['value']:.4f} <= {FRAGMENT_GATE} → {'过' if fragment['met'] else '未过'}")
        lines.append(f"- 门判定：**{state}**（① {count['value']:.4f} vs {COUNT_GATE}；"
                     f"③ {fragment['value']:.4f} vs {FRAGMENT_GATE}）")
        red = (report.get("hardRedLines") or {}).get("fragmentedQuestions")
        if red:
            lines.append(f"- 切残硬红线（不依赖 0.05 阈值）：切残题数 {red['value']} / 要求 "
                         f"{red['required']} → "
                         f"{'满足' if red['satisfied'] else '未满足（切残不得入库）'}")
    else:
        lines.append(f"- n/a：{state}，无指标可判（不伪造数字）")
    lines.append("")
    lines.append("## 5. 逐页裁定")
    lines.append("")
    if report["perPage"]:
        lines.append("| file | 来源 | 金标题数 | 应用框数 | ①一致 | IoU≥0.5 达标 | 切残 |")
        lines.append("|---|---|---|---|---|---|---|")
        for row in report["perPage"]:
            lines.append(f"| {row['file']} | {_page_source_cell(row)} |"
                         f" {row['goldQuestions']} | {row['appBoxes']} |"
                         f" {'一致' if row['consistent'] else '不一致'} | {row['iouOk']} |"
                         f" {row['fragmentedCount']} |")
    else:
        lines.append("（无逐页表——度量未执行）")
    lines.append("")
    lines.append("## 6. 未验证 / UNVERIFIED（如实登记）")
    lines.append("")
    if report.get("freezeFailures"):
        lines.append(f"- 冻结校验失败（未判门）：{len(report['freezeFailures'])} 处，"
                     "首条：" + report["freezeFailures"][0])
    if state == "NOT_EVALUABLE_APP_MISSING":
        lines.append(f"- 应用导出缺失：本轮全部指标与门判定 **UNVERIFIED**"
                     f"（{app['path']} 不存在）")
    if report.get("measureError"):
        lines.append(f"- 度量失败：{report['measureError'][:300]}")
    level = report["golden"].get("evidenceGradeLevel") or "UNKNOWN"
    recomputable = report["golden"].get("evidenceLevelRecomputable")
    lines.append(f"- 金标证据等级：{level}（"
                 + ("产物字段可复算（C7 已检查该等级要求的登记字段）" if recomputable
                    else "v1 冻结常量映射，**产物不可复算**：哪两个标注员、什么模型/提示词"
                         "版本、是否真的互不可见，全部 UNVERIFIED")
                 + "）；原始 evidenceGrade="
                 + f"`{report['golden'].get('evidenceGrade')}`。真实人工正确率 **UNVERIFIED**，"
                   "不得当作人工金标引用")
    if isinstance(source, dict) and source.get("present"):
        lines.append(f"- 应用导出来源块：present（db sha256 `{source.get('dbSha256')}`；"
                     f"db user_version {source.get('dbUserVersion')}；逐页 nameSource 见 §5）")
    else:
        lines.append("- 应用导出来源块：**缺失**——逐页来源与设备/DB 版本绑定 "
                     "**UNVERIFIED**（不以文件名反推）")
    if report.get("appIntegrityMismatchPages"):
        lines.append("- job 记账题数/页数与导出行数不一致（integrity=false）："
                     + "、".join(report["appIntegrityMismatchPages"]))
    scope = report.get("scope") or {}
    if scope.get("statement"):
        lines.append(f"- **适用范围（不可外推）**：{scope['statement']}")
        for reason in scope.get("reasons") or []:
            lines.append(f"  - {reason}")
    lines.append("- 门后自动化：始终未启用（仓内无门控开关；开启属 4C③ 产品裁定，不在工具职责内）")
    lines.append("")
    text = "\n".join(lines) + "\n"
    byte_len = len(text.encode("utf-8"))
    line_count = text.count("\n")
    header = (f"<!-- 体积自查：{line_count} 行 / {byte_len} B；"
              f"R4 上限 {EVIDENCE_MAX_LINES} 行 / {EVIDENCE_MAX_BYTES} B -->\n")
    return header + text


def write_evidence(report: dict, evidence_path: Path) -> None:
    """写证据文件；已存在则拒绝（一次运行一份），超 R4 体积上限则拒绝落盘。"""
    if evidence_path.exists():
        raise RuntimeError(f"证据文件已存在，不覆盖（一次运行一份，重跑换名）：{evidence_path}")
    text = render_evidence(report, datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))
    byte_len = len(text.encode("utf-8"))
    line_count = text.count("\n")
    if byte_len > EVIDENCE_MAX_BYTES or line_count > EVIDENCE_MAX_LINES:
        raise RuntimeError(f"证据文件超 R4 上限（{line_count} 行 / {byte_len} B > "
                           f"{EVIDENCE_MAX_LINES} 行 / {EVIDENCE_MAX_BYTES} B），拒绝落盘：{evidence_path}")
    evidence_path.parent.mkdir(parents=True, exist_ok=True)
    evidence_path.write_text(text, encoding="utf-8", newline="")


def _exit_code(report: dict) -> int:
    return {"PASS": 0, "FAIL": 1, "NOT_EVALUABLE_FREEZE": 2,
            "NOT_EVALUABLE_DATA": 2, "NOT_EVALUABLE_APP_MISSING": 3}[report["gate"]["state"]]


def main(argv: list[str] | None = None) -> int:
    global LAST_REPORT
    parser = argparse.ArgumentParser(description="切分金标门红绿判定器（AUD-08；冻结→度量→0.9/0.05）")
    parser.add_argument("--golden", type=Path, default=DEFAULT_GOLDEN)
    parser.add_argument("--sidecar", type=Path, default=None,
                        help="默认 <golden>.sha256")
    parser.add_argument("--app", type=Path, default=DEFAULT_APP)
    parser.add_argument("--report-json", type=Path, default=None,
                        help="确定性 JSON 报告落盘路径（已存在则报错）")
    parser.add_argument("--evidence-out", type=Path, default=None,
                        help="R4 模板精神证据 md 落盘路径（已存在则报错）")
    parser.add_argument("--rulings-out", type=Path, default=None,
                        help="逐页裁定目录（写 <dir>/<runId>.json；已存在即拒绝；"
                             "门不可判时不生成）")
    args = parser.parse_args(argv)
    sidecar = args.sidecar or args.golden.with_name(args.golden.name + ".sha256")

    report = run(args.golden, sidecar, args.app)
    LAST_REPORT = report
    state = report["gate"]["state"]
    code = _exit_code(report)
    generated_at_utc = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    # Windows 文件名不允许 ':'：runId 用紧凑 UTC 形式（R5 的示例带冒号，仅作语义参考；
    # 落盘名必须跨平台可用）。`generatedAtUtc` 仍保留标准 ISO-8601。
    run_id = f"{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}-{secrets.token_hex(4)}"
    command = ([sys.executable, str(Path(__file__).resolve())]
               + (list(argv) if argv is not None else sys.argv[1:]))

    print(f"切分金标门判定（AUD-08）：{state}（exit {code}）")
    print(f"  金标：{report['golden']['path']}（sha256 {report['golden']['sha256']}，"
          "冻结校验 "
          f"{'PASS（子步骤）' if not report.get('freezeFailures') else 'FAIL'}；"
          f"证据等级 {report['golden'].get('evidenceGradeLevel') or 'UNKNOWN'}）")
    print(f"  应用导出：{report['app']['path']}"
          + (f"（sha256 {report['app']['sha256']}）" if report["app"]["exists"] else " —— **缺失**"))
    if report.get("freezeFailures"):
        print(f"  冻结校验失败 {len(report['freezeFailures'])} 处（带病金标不判门）：")
        for failure in report["freezeFailures"][:10]:
            print(f"    - {failure}")
    if report.get("measureError"):
        print(f"  度量失败：{report['measureError'][:300]}")
    if report["metrics"]:
        count = report["gate"]["count"]
        fragment = report["gate"]["fragment"]
        print(f"  ① countExactRate = {report['metrics']['countExact']['successes']}/"
              f"{report['metrics']['countExact']['denominator']} = {count['value']:.4f} "
              f"（门 {count['value']:.4f} >= {COUNT_GATE} → {'过' if count['met'] else '未过'}）")
        print(f"  ③ fragmentRate = {report['metrics']['fragment']['successes']}/"
              f"{report['metrics']['fragment']['denominator']} = {fragment['value']:.4f} "
              f"（门 {fragment['value']:.4f} <= {FRAGMENT_GATE} → {'过' if fragment['met'] else '未过'}）")
        print(f"  门判定：{state}（阈值出处见 GATE.md；改值须先裁定并留旧值对照）")
    print(f"  自动化：{AUTOMATION_RULING}")

    for label, path in (("--report-json", args.report_json),
                        ("--evidence-out", args.evidence_out)):
        if path is None:
            continue
        try:
            if label == "--report-json":
                if path.exists():
                    raise RuntimeError(f"报告文件已存在，不覆盖：{path}")
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(report, ensure_ascii=False, sort_keys=True,
                                           indent=2) + "\n", encoding="utf-8", newline="")
            else:
                write_evidence(report, path)
        except RuntimeError as failure:
            print(f"切分金标门判定 FAIL：{failure}")
            return 2
        print(f"  已落盘 {label}: {path}")

    if args.rulings_out is not None:
        if state not in ("PASS", "FAIL"):
            # 不可判 ≠ 没发现问题：没有度量就没有逐页裁定，不得伪造。
            print(f"  未生成 rulings：门状态 {state} 不可判（不伪造逐页裁定）")
        else:
            try:
                document = RULINGS.build_document(report, run_id=run_id,
                                                  generated_at_utc=generated_at_utc,
                                                  command=command)
                path = RULINGS.write_document(document, args.rulings_out)
            except (ValueError, OSError) as failure:
                print(f"切分金标门判定 FAIL：{failure}")
                return 2
            print(f"  已落盘 --rulings-out: {path}")
    return code


if __name__ == "__main__":
    sys.exit(main())
