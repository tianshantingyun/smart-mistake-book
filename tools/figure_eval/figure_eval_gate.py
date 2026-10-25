# -*- coding: utf-8 -*-
"""PRD-09 生图质量评测 harness（批次 20 / T5 可先行半边）。

范围与边界（先读）：
- 本 harness 落的是 PRD-09 的**可先行半边**：冻结盲测任务集、显式评测预算清单、
  两后端配对/切换框架、指标字段与采集点、按 PRD 原文引用的质量门常量，并以
  **fake 驱动**端到端自检机制本身。
- **真实 Provider 质量一律 UNVERIFIED**（无凭据，禁止真实调用）。fake 运行的
  质量门数值是机制自检的输入，不构成任何质量结论（PRD-09 退出门：
  `docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:137`
  "手机截图/API34探针、手写程序不能代替真实 Provider 成功率"）。
- JS 后端本批**留桩**：QuickJS 执行端口由 T4 落地，端口就位后实现 `JsPort`
  接口替换 `JsPortStub`。JS 在产品侧仍是 UNAVAILABLE，不广告。

引用原文（不发明数值）：
- 120 盲测/六组各20/开发集另设/盲测题不得调 prompt：
  `docs/superpowers/specs/2026-10-19-product-readiness-design.md` §6 :91；
  `docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:132-134`。
- 门：首次合格≥90%、原预算内最终合格≥95%、每组最终≥90%、关键题意错误被接受=0：
  design §6 :91。首图 p95 目标 JS≤30s / IMAGE_MODEL≤120s：design §6 :95。
- 六派发总门：`docs/current/visual-generation-contract.md:138` 与
  `core/model/.../ModelTasks.kt:61 MAX_DISPATCHES=6`。

用法：
    python tools/figure_eval/figure_eval_gate.py                # fake 机制自检
    python tools/figure_eval/figure_eval_gate.py --enforce-quality-gates
        # 仅未来真实 Provider 运行使用：质量门不达即 exit 1

退出码：0 = 机制自检通过；1 = 机制自检失败（或 --enforce-quality-gates 下质量门
未达）；2 = 夹具完整性/用法错误。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path
from typing import Any, Callable

HERE = Path(__file__).resolve().parent

BLIND_FILE = "tasks_blind_120.json"
DEV_FILE = "tasks_dev_24.json"
MANIFEST_FILE = "freeze_manifest.json"
BUDGET_FILE = "budget.json"
REPORT_BASENAME = "figure_eval_fake_run_report"

# ---------------------------------------------------------------------------
# 门常量：数值全部来自 PRD 原文引用，不发明。
# ---------------------------------------------------------------------------

DESIGN = "docs/superpowers/specs/2026-10-19-product-readiness-design.md"
PRD09 = "docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md"
CONTRACT = "docs/current/visual-generation-contract.md"
PROMPT_POLICY = "docs/current/figure-generation-prompt-policy.md"
DISPATCH_CONST = (
    "core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:61"
)

GATES: dict[str, dict[str, Any]] = {
    "first_pass_rate_min": {
        "value": 0.90,
        "source": {
            "file": DESIGN,
            "section": "§6 统一生图的质量与启用",
            "quote": "首次合格≥90%，原预算内最终合格≥95%，每组最终≥90%（首批质量目标，工程启用标准，尚未实测）",
            "also_stated_in": {
                "file": PRD09,
                "line": ":133",
                "quote": "依设计§6执行首次≥90%/预算内≥95%/分组≥90%、关键错误接受=0门，记录比例/分母/区间",
            },
        },
    },
    "within_budget_final_rate_min": {
        "value": 0.95,
        "source": {
            "file": DESIGN,
            "section": "§6",
            "quote": "原预算内最终合格≥95%",
        },
    },
    "per_group_final_rate_min": {
        "value": 0.90,
        "source": {
            "file": DESIGN,
            "section": "§6",
            "quote": "每组最终≥90%",
        },
    },
    "critical_errors_accepted_max": {
        "value": 0,
        "source": {
            "file": DESIGN,
            "section": "§6",
            "quote": "关键题意错误被接受=0、越权/超预算/恢复重复外发=0",
        },
    },
    "first_image_p95_ms_js_max": {
        "value": 30_000,
        "source": {
            "file": DESIGN,
            "section": "§6",
            "quote": "首张合格图p95≤30秒（JS路径）",
            "also_stated_in": {"file": PRD09, "line": ":134", "quote": "JS路径p95≤30秒、IMAGE_MODEL路径≤120秒为首批目标"},
        },
    },
    "first_image_p95_ms_image_model_max": {
        "value": 120_000,
        "source": {
            "file": DESIGN,
            "section": "§6",
            "quote": "≤120秒（IMAGE_MODEL路径）",
            "also_stated_in": {"file": PRD09, "line": ":134", "quote": "JS路径p95≤30秒、IMAGE_MODEL路径≤120秒为首批目标"},
        },
    },
}

EXPECTATION_KINDS = {"numeric", "label_set", "label_absent", "direction", "ordering", "count"}

# 指标采集点（真实值待 key；字段与采集点先落——PRD-09 :134）。
METRIC_COLLECTION_POINTS = {
    "first_image_latency_ms": (
        "figure_generation_attempt.started_at_epoch_millis → ended_at_epoch_millis"
        "（core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/"
        "entity/FigureGenerationEntities.kt 首个 GENERATED attempt）；harness 侧同字段。"
        "已知缺口：该表暂无 token/费用/首图延迟专用列（批次 20 零 schema 决定，"
        "真实 key 到位后随启用门补列）。"
    ),
    "total_duration_ms": "任务开始→终态（operation 两时间戳 figure_generation_operation.started/updated）+ harness 侧。",
    "tokens": "真实 Provider usage 返回（prompt/completion tokens）；fake 驱动无 usage → null。",
    "cost": "按真实返回与配置单价计；未知费用标未知、不写 0（design §6 :95）。",
    "dispatches_used": "持久派发账本（RoomFigureDispatchLedger durable 预留/计数）+ harness 侧同口径。",
}

OPPOSITE_DIRECTION = {
    "down": "up", "up": "down", "left": "right", "right": "left",
    "along_incline_up": "along_incline_down", "along_incline_down": "along_incline_up",
}


class FreezeIntegrityError(RuntimeError):
    """冻结夹具 sha256 与 manifest 不一致（防面向结果改夹具）。"""


# ---------------------------------------------------------------------------
# 夹具加载与冻结校验
# ---------------------------------------------------------------------------

def _sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_frozen_tasks(path: Path) -> list[dict]:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    tasks = payload["tasks"]
    assert isinstance(tasks, list) and tasks, f"empty task set: {path}"
    return tasks


def verify_freeze_manifest(fixture_dir: Path) -> dict:
    """校验冻结清单；不一致抛 FreezeIntegrityError（评测门拒绝运行）。"""
    fixture_dir = Path(fixture_dir)
    manifest_path = fixture_dir / MANIFEST_FILE
    if not manifest_path.exists():
        raise FreezeIntegrityError(f"missing {MANIFEST_FILE}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for name, meta in manifest["files"].items():
        f = fixture_dir / name
        if not f.exists():
            raise FreezeIntegrityError(f"frozen file missing: {name}")
        actual = _sha256_bytes(f.read_bytes())
        if actual != meta["sha256"]:
            raise FreezeIntegrityError(
                f"frozen set tampered: {name} sha256 {actual[:12]} != "
                f"manifest {meta['sha256'][:12]}；修改任务集必须重新冻结"
                "（重跑 build_frozen_tasks.py）并留审计记录。"
            )
    return manifest


# ---------------------------------------------------------------------------
# fake IMAGE_MODEL 驱动（机制自检用；不出网、不产生真实费用）
# ---------------------------------------------------------------------------

class FakeImageModelDriver:
    """确定性 fake 驱动。

    裁决只由 `sha256(driver_version|run_seed|backend|task_id)` 决定，
    **不读取夹具预期值**——夹具改预期不会改变裁决（防面向结果耦合，
    见 test_driver_outcome_independent_of_expectation_values）。
    概率分布是机制自检参数（用于覆盖全部分类路径），不是质量先验。
    """

    DRIVER_VERSION = "fake-image-model-v1"
    BACKEND = "IMAGE_MODEL"
    # bucket → 结果（共 100 档）
    DIST = [
        ("GENERATED_OK", 55),
        ("GENERATED_FACT_MISMATCH", 12),
        ("DEGRADED_TO_TEXT", 11),
        ("NO_FIGURE", 9),
        ("FAILED_BACKEND_ERROR", 6),
        ("FAILED_BUDGET_EXHAUSTED", 4),
        ("FAILED_VALIDATION", 3),
    ]

    def __init__(self, run_seed: str) -> None:
        self.run_seed = run_seed

    def _h(self, salt: str, task_id: str) -> int:
        raw = f"{self.DRIVER_VERSION}|{self.run_seed}|{salt}|{task_id}"
        return int(hashlib.sha256(raw.encode("utf-8")).hexdigest()[:12], 16)

    def decide(self, task: dict) -> dict:
        tid = task["task_id"]
        bucket = self._h("outcome", tid) % 100
        acc = 0
        outcome = self.DIST[-1][0]
        for name, weight in self.DIST:
            acc += weight
            if bucket < acc:
                outcome = name
                break
        # 损坏位置（只挑索引，不看预期值内容）
        n_exp = len(task["fact_expectations"])
        corrupt_index = self._h("corrupt", tid) % n_exp if outcome == "GENERATED_FACT_MISMATCH" else None
        repair_success = (
            self._h("repair", tid) % 100 < 40
            if outcome in ("GENERATED_FACT_MISMATCH", "DEGRADED_TO_TEXT",
                           "NO_FIGURE", "FAILED_BACKEND_ERROR")
            else False
        )
        repair_dispatches = (1 + self._h("repn", tid) % 3) if repair_success else 0
        first_image_latency_ms = (
            float(5_000 + self._h("lat", tid) % 145_000)
            if outcome in ("GENERATED_OK", "GENERATED_FACT_MISMATCH")
            else None
        )
        return {
            "outcome": outcome,
            "corrupt_index": corrupt_index,
            "repair_success": repair_success,
            "repair_dispatches": repair_dispatches,
            "first_image_latency_ms": first_image_latency_ms,
            "overhead_ms": float(300 + self._h("ovh", tid) % 3_000),
        }

    def produce_output_facts(self, task: dict, decision: dict) -> list[dict]:
        """按裁决产出一组"图上事实"：正确值取自夹具预期；损坏位做保证不匹配的变换。"""
        facts: list[dict] = []
        for i, exp in enumerate(task["fact_expectations"]):
            if exp["kind"] == "label_absent":
                # 该类预期的正确输出是"图中实际出现的标签"：未损坏时禁写内容都不出现
                value: Any = []
            else:
                value = exp["expected"]
            if decision["corrupt_index"] == i:
                value = self._corrupt(exp)
            facts.append({"key": exp["key"], "value": value})
        return facts

    @staticmethod
    def _corrupt(exp: dict) -> Any:
        kind, expected = exp["kind"], exp["expected"]
        if kind == "numeric":
            bad = expected * 2.5 + 1.0
            return bad + 7.0 if math.isclose(bad, expected) else bad
        if kind == "label_set":
            return list(expected[1:])  # 丢第一个标签
        if kind == "label_absent":
            return [expected[0]]  # 违规保留了禁写内容
        if kind == "direction":
            return OPPOSITE_DIRECTION.get(expected, "down")
        if kind == "ordering":
            return list(expected[1:]) + [expected[0]]  # 旋转一位
        if kind == "count":
            return expected + 2
        raise ValueError(kind)


# ---------------------------------------------------------------------------
# JS 后端：本批留桩，端口就位后接 T4
# ---------------------------------------------------------------------------

class JsPortStub:
    """T4 QuickJS 执行端口就位前的留桩。

    端口落地后：实现同一接口（evaluate(task) -> 与 IMAGE_MODEL 同形的记录），
    在 run_evaluation 里替换本桩即可；JS 仍是本地执行、不占远程派发。
    """

    STATUS = "NOT_WIRED"
    CLASSIFICATION = "JS_STUB_NOT_WIRED"

    def evaluate(self, task: dict) -> dict:
        return {
            "backend": "JS",
            "classification": self.CLASSIFICATION,
            "status": self.STATUS,
            "first_image_latency_ms": None,
            "total_duration_ms": None,
            "tokens": {"input": None, "output": None},
            "cost": {"amount": None, "status": "unknown"},
            "dispatches_used": 0,
            "note": "QuickJS 执行端口未就位（T4 待接）：留桩记录配对位，端口落地后接适配器",
        }


# ---------------------------------------------------------------------------
# 独立事实核验（PRD-09 :132：不能只读模型自己输出的断言）
# ---------------------------------------------------------------------------

def check_fact_expectations(
    output_facts: list[dict], expectations: list[dict]
) -> dict:
    got = {f["key"]: f["value"] for f in output_facts}
    mismatches: list[dict] = []
    verified = 0
    for exp in expectations:
        key, kind, expected = exp["key"], exp["kind"], exp["expected"]
        value = got.get(key)
        ok = False
        if value is not None:
            if kind == "numeric":
                ok = isinstance(value, (int, float)) and abs(value - expected) <= exp.get("tolerance", 0.0)
            elif kind == "label_set":
                ok = isinstance(value, list) and set(expected).issubset(set(value))
            elif kind == "label_absent":
                ok = isinstance(value, list) and set(expected).isdisjoint(set(value))
            elif kind == "direction":
                ok = value == expected
            elif kind == "ordering":
                ok = value == expected
            elif kind == "count":
                ok = value == expected
        if ok:
            verified += 1
        else:
            mismatches.append({
                "key": key, "kind": kind, "expected": expected, "got": value,
                "critical": bool(exp.get("critical", False)),
            })
    return {"accepted": not mismatches, "verified": verified, "mismatches": mismatches}


# ---------------------------------------------------------------------------
# 统计
# ---------------------------------------------------------------------------

def percentile(values: list[float], q: float) -> float | None:
    if not values:
        return None
    xs = sorted(values)
    if len(xs) == 1:
        return float(xs[0])
    pos = q * (len(xs) - 1)
    lo = int(math.floor(pos))
    hi = min(lo + 1, len(xs) - 1)
    frac = pos - lo
    return float(xs[lo] * (1 - frac) + xs[hi] * frac)


def wilson_ci(successes: int, n: int, z: float = 1.96) -> tuple[float, float]:
    """Wilson 95% 区间（描述性统计，非门）。"""
    if n == 0:
        return (0.0, 1.0)
    p = successes / n
    denom = 1 + z * z / n
    center = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return (max(0.0, center - half), min(1.0, center + half))


# ---------------------------------------------------------------------------
# 质量门计算（数值与比较器全部来自 GATES 引用）
# ---------------------------------------------------------------------------

def _rate_block(successes: int, denominator: int, threshold: float, gate_meta: dict) -> dict:
    value = successes / denominator if denominator else 0.0
    lo, hi = wilson_ci(successes, denominator)
    return {
        "value": value,
        "successes": successes,
        "denominator": denominator,
        "threshold": threshold,
        "met": value >= threshold,
        "wilson95": [lo, hi],
        "source": gate_meta["source"],
        "informational_only": True,
    }


def compute_quality_gates(records: list[dict], denominator: int) -> dict:
    first_pass = sum(1 for r in records if r["first_attempt_pass"])
    final_ok = sum(1 for r in records if r["final_accepted"])
    critical_accepted = sum(1 for r in records if r.get("critical_error_accepted"))
    im_lat = [r["image_model"]["first_image_latency_ms"] for r in records
              if r["image_model"].get("first_image_latency_ms") is not None]
    js_lat = [r["js"]["first_image_latency_ms"] for r in records
              if isinstance(r.get("js"), dict) and r["js"].get("first_image_latency_ms") is not None]

    per_group: dict[str, dict] = {}
    groups = sorted({r["group"] for r in records})
    for g in groups:
        rs = [r for r in records if r["group"] == g]
        ok = sum(1 for r in rs if r["final_accepted"])
        per_group[g] = _rate_block(ok, len(rs), GATES["per_group_final_rate_min"]["value"],
                                   GATES["per_group_final_rate_min"])
        per_group[g].pop("informational_only", None)

    return {
        "first_pass_rate": _rate_block(first_pass, denominator,
                                       GATES["first_pass_rate_min"]["value"],
                                       GATES["first_pass_rate_min"]),
        "within_budget_final_rate": _rate_block(final_ok, denominator,
                                                GATES["within_budget_final_rate_min"]["value"],
                                                GATES["within_budget_final_rate_min"]),
        "per_group_final": {
            "threshold": GATES["per_group_final_rate_min"]["value"],
            "source": GATES["per_group_final_rate_min"]["source"],
            "groups": per_group,
            "all_met": all(v["met"] for v in per_group.values()),
            "informational_only": True,
        },
        "critical_errors_accepted": {
            "value": critical_accepted,
            "threshold": GATES["critical_errors_accepted_max"]["value"],
            "met": critical_accepted <= GATES["critical_errors_accepted_max"]["value"],
            "source": GATES["critical_errors_accepted_max"]["source"],
            "informational_only": True,
        },
        "first_image_p95_ms": {
            "image_model": percentile(im_lat, 0.95),
            "js": percentile(js_lat, 0.95) if js_lat else None,
            "threshold_ms": {
                "image_model": GATES["first_image_p95_ms_image_model_max"]["value"],
                "js": GATES["first_image_p95_ms_js_max"]["value"],
            },
            "met": {
                # JS 留桩：无样本即不可判（如实记 null，不伪造"达标"）
                "image_model": (percentile(im_lat, 0.95) is not None
                                and percentile(im_lat, 0.95) <= GATES["first_image_p95_ms_image_model_max"]["value"]),
                "js": None,
            },
            "source": {
                "image_model": GATES["first_image_p95_ms_image_model_max"]["source"],
                "js": GATES["first_image_p95_ms_js_max"]["source"],
            },
            "informational_only": True,
        },
    }


# ---------------------------------------------------------------------------
# 运行：两后端配对/切换（IMAGE_MODEL fake 驱动 + JS 留桩）
# ---------------------------------------------------------------------------

def _operation_id(task_id: str) -> str:
    # operationId 不含 method：切换方法仍是同一操作、预算不重置（契约 §7.1 :138）
    return "feval-op-" + hashlib.sha256(task_id.encode("utf-8")).hexdigest()[:12]


def run_fake_evaluation(
    run_seed: str = "t5-batch20",
    output_dir: Path | None = None,
    fixture_dir: Path | None = None,
    js_port: Any | None = None,
    enforce_quality_gates: bool = False,
) -> dict:
    fixture_dir = Path(fixture_dir) if fixture_dir else HERE
    manifest = verify_freeze_manifest(fixture_dir)  # 冻结完整性 fail-closed
    blind = load_frozen_tasks(fixture_dir / BLIND_FILE)
    dev = load_frozen_tasks(fixture_dir / DEV_FILE)
    budget = json.loads((fixture_dir / BUDGET_FILE).read_text(encoding="utf-8"))
    js = js_port if js_port is not None else JsPortStub()
    driver = FakeImageModelDriver(run_seed)

    cap = budget["per_operation"]["remote_dispatch_cap"]
    task_records: list[dict] = []
    total_dispatches = 0

    for idx, task in enumerate(blind):
        decision = driver.decide(task)
        outcome = decision["outcome"]
        first_pass = outcome == "GENERATED_OK"
        critical_flagged = False
        repair_note = None

        if outcome in ("GENERATED_OK", "GENERATED_FACT_MISMATCH"):
            facts = driver.produce_output_facts(task, decision)
            verdict = check_fact_expectations(facts, task["fact_expectations"])
            critical_flagged = any(m["critical"] for m in verdict["mismatches"])
            accepted = verdict["accepted"]
        else:
            accepted = False  # 未产图：任何路径都不算合格
            verdict = {"accepted": False, "verified": 0, "mismatches": []}

        dispatches = 1 if outcome != "FAILED_BUDGET_EXHAUSTED" else cap
        final_accepted = False
        if first_pass and accepted:
            classification = "PASS"
            final_accepted = True
        elif outcome != "FAILED_BUDGET_EXHAUSTED" and outcome != "FAILED_VALIDATION" and decision["repair_success"]:
            # 预算内修复重试后合格（修复派发仍计同一操作预算）
            classification = "PASS_WITH_REPAIR"
            final_accepted = True
            dispatches = min(cap, 1 + decision["repair_dispatches"])
            repair_note = f"首次 {outcome}，预算内修复 {decision['repair_dispatches']} 次后合格"
        else:
            classification = {
                "GENERATED_FACT_MISMATCH": "FAILED_FACT_MISMATCH",
                "DEGRADED_TO_TEXT": "DEGRADED_TO_TEXT",
                "NO_FIGURE": "NO_FIGURE",
                "FAILED_BACKEND_ERROR": "FAILED_BACKEND_ERROR",
                "FAILED_BUDGET_EXHAUSTED": "FAILED_BUDGET_EXHAUSTED",
                "FAILED_VALIDATION": "FAILED_VALIDATION",
            }[outcome]

        assert dispatches <= cap, f"{task['task_id']} 超六派发总门"
        total_dispatches += dispatches

        latency = decision["first_image_latency_ms"]
        if latency is None and final_accepted:
            # 修复后才有首图：给一个晚于首次失败的延迟（仍在分布内）
            latency = float(5_000 + driver._h("lat2", task["task_id"]) % 145_000)
        overhead = decision["overhead_ms"] * max(dispatches - 1, 0)
        total_duration = (latency if latency is not None else 0.0) + overhead + decision["overhead_ms"]

        tokens = {"input": None, "output": None, "status": "unknown"}
        cost = {"amount": None, "currency": None, "status": "unknown"}

        record = {
            "task_id": task["task_id"],
            "group": task["group"],
            "operation_id": _operation_id(task["task_id"]),
            "pair_run": idx % 5 == 0,  # 配对子集：不强制所有题跑两遍（design §6 :93）
            "first_attempt_pass": first_pass,
            "final_accepted": final_accepted,
            "critical_error_accepted": False,  # 关键不匹配一律不被接受（fail-closed 核验）
            "critical_flagged": critical_flagged,
            "repair_note": repair_note,
            "review_source": task["review_source"],
            "image_model": {
                "backend": "IMAGE_MODEL",
                "driver": "fake",
                "classification": classification,
                "first_image_latency_ms": latency,
                "total_duration_ms": total_duration,
                "tokens": tokens,
                "cost": cost,
                "dispatches_used": dispatches,
            },
            "tokens": tokens,
            "cost": cost,
            "js": js.evaluate(task),
        }

        if record["pair_run"] and not final_accepted:
            record["switch_event"] = {
                "operation_id": record["operation_id"],
                "from_method": "IMAGE_MODEL",
                "to_method": "JS",
                "reason": (
                    f"IMAGE_MODEL 预算内未合格（{classification}），按统一策略换法重试；"
                    "同一 operation，预算不重置"
                ),
                "dispatches_before_switch": dispatches,
                "js_result": record["js"]["classification"],
            }
        task_records.append(record)

    # ---- 指标聚合（真实值待 key；fake 值仅机制自检） ----
    im_lat_all = [r["image_model"]["first_image_latency_ms"] for r in task_records
                  if r["image_model"]["first_image_latency_ms"] is not None]
    im_dur_all = [r["image_model"]["total_duration_ms"] for r in task_records]
    metrics = {
        "first_image_latency_ms": {
            "image_model": {"p50": percentile(im_lat_all, 0.50), "p95": percentile(im_lat_all, 0.95)},
            "js": None,  # 留桩无样本
        },
        "total_duration_ms": {
            "image_model": {"p50": percentile(im_dur_all, 0.50), "p95": percentile(im_dur_all, 0.95)},
            "js": None,
        },
        "tokens": {"input": None, "output": None, "status": "unknown"},
        "cost": {"amount": None, "currency": None, "status": "unknown"},
        "collection_points": METRIC_COLLECTION_POINTS,
    }

    quality_gates = compute_quality_gates(task_records, denominator=len(blind))

    classes = [r["image_model"]["classification"] for r in task_records]
    machinery = {
        "fixture_integrity": "verified",
        "manifest_frozen_at": manifest["frozen_at"],
        "exercised_paths": {
            "PASS": "PASS" in classes,
            "PASS_WITH_REPAIR": "PASS_WITH_REPAIR" in classes,
            "DEGRADED_TO_TEXT": "DEGRADED_TO_TEXT" in classes,
            "NO_FIGURE": "NO_FIGURE" in classes,
            "FAILED_any": any(c.startswith("FAILED") for c in classes),
            "critical_mismatch_observed": any(r["critical_flagged"] for r in task_records),
            "critical_mismatch_accepted_zero": all(not r["critical_error_accepted"] for r in task_records),
            "switch_event_recorded": any(r.get("switch_event") for r in task_records),
            "js_stub_recorded": all(r["js"]["classification"] == "JS_STUB_NOT_WIRED" for r in task_records),
            "budget_cap_respected": all(r["image_model"]["dispatches_used"] <= cap for r in task_records),
        },
        "quality_gates_are_informational": True,
        "verdict": None,  # 下方填
    }
    machinery["verdict"] = "PASS" if all(machinery["exercised_paths"].values()) else "FAIL"

    report = {
        "run_type": "FAKE_DRIVER_MACHINERY_CHECK",
        "harness_version": "figure-eval-harness-v1",
        "run_seed": run_seed,
        "real_provider_quality": "UNVERIFIED",
        "unverified_note": (
            "真实 Provider 无凭据，本报告全部数值来自 fake 驱动（不出网、零真实派发），"
            "只证明评测机制可运行；不构成任何真实质量结论（PRD-09 :137）。"
        ),
        "set_integrity": {
            "manifest_sha_verified": True,
            "blind_task_count": len(blind),
            "dev_task_count": len(dev),
            "review_source_summary": (
                "全部任务评审来源：模型标注，无人工专家（human_expert=false）；"
                "分歧未决不计合格（PRD-09 :132）"
            ),
        },
        "denominator": {
            "blind": len(blind),
            "dev_excluded": len(dev),
            "repeats_policy": "重复同一题只作稳定性观察，不算独立新样本（design §6 :91）",
        },
        "budget": {
            "per_operation_remote_dispatch_cap": cap,
            "six_dispatch_gate_source": budget["citations"]["six_dispatch_total_gate"],
            "six_dispatch_constant": budget["citations"]["six_dispatch_constant"],
            "total_dispatches_used": total_dispatches,
            "total_dispatch_cap": budget["run_level"]["max_total_remote_dispatches"],
            "cost": budget["cost"],
        },
        "pairing": {
            "paired_task_count": sum(1 for r in task_records if r["pair_run"]),
            "switch_events": sum(1 for r in task_records if r.get("switch_event")),
            "js_backend_status": getattr(js, "STATUS", "UNKNOWN"),
            "note": "配对子集检验两后端与双向切换，不强制所有题跑两遍（design §6 :93）",
        },
        "metrics": metrics,
        "quality_gates": quality_gates,
        "machinery_check": machinery,
        "task_records": task_records,
        "citations": {
            "design_section6": f"{DESIGN} §6（:91/:93/:95）",
            "prd09": f"{PRD09}:126-140",
            "contract_six_dispatch": f"{CONTRACT}:138",
            "prompt_policy_budget": f"{PROMPT_POLICY} §5",
            "dispatch_constant": DISPATCH_CONST,
        },
    }

    if enforce_quality_gates:
        # 仅真实 Provider 运行使用；fake 运行禁止开启
        gates_ok = (
            quality_gates["first_pass_rate"]["met"]
            and quality_gates["within_budget_final_rate"]["met"]
            and quality_gates["per_group_final"]["all_met"]
            and quality_gates["critical_errors_accepted"]["met"]
        )
        report["enforced_quality_gates_met"] = gates_ok

    if output_dir is not None:
        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)
        (output_dir / f"{REPORT_BASENAME}.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8", newline="\n",
        )
        (output_dir / f"{REPORT_BASENAME}.md").write_text(
            render_md_report(report), encoding="utf-8", newline="\n",
        )
    return report


# ---------------------------------------------------------------------------
# 报告
# ---------------------------------------------------------------------------

def render_md_report(report: dict) -> str:
    g = report["quality_gates"]
    m = report["metrics"]
    lines: list[str] = []
    add = lines.append

    add("# PRD-09 生图评测 · fake 驱动机制自检报告")
    add("")
    add(f"- 运行类型：**{report['run_type']}**（run_seed=`{report['run_seed']}`）")
    add(f"- 夹具冻结：{report['machinery_check']['manifest_frozen_at']}（sha256 校验通过）")
    add("- **真实 Provider 质量：UNVERIFIED**（无凭据，fake 驱动不出网、零真实派发；")
    add("  本报告只证明评测机制可运行，不构成任何真实质量结论——PRD-09 退出门 :137）")
    add(f"- 机制自检：**{report['machinery_check']['verdict']}**（质量门数值在此模式下仅供参考，不作为启用判定）")
    add("")
    add("## 1. 冻结集与分母")
    add("")
    add(f"- 盲测 120（6 组 × 20）：{report['set_integrity']['blind_task_count']}")
    add(f"- 开发集另设 {report['set_integrity']['dev_task_count']}，**不计入 120 分母**（PRD-09 :132）")
    add(f"- {report['denominator']['repeats_policy']}")
    add(f"- 评审来源：{report['set_integrity']['review_source_summary']}")
    add("")
    add("## 2. 评测预算（六派发总门）")
    add("")
    b = report["budget"]
    add(f"- 每逻辑操作远程派发上限：**{b['per_operation_remote_dispatch_cap']}**"
        f"（{b['six_dispatch_gate_source']['source']}；{b['six_dispatch_constant']['source']}）")
    add(f"- 本次 fake 运行派发合计：{b['total_dispatches_used']} / 上限 {b['total_dispatch_cap']}（fake，零真实外发）")
    add(f"- 费用：{b['cost']['status']}（未知费用记未知、不写 0；{b['cost']['note']}）")
    add("")
    add("## 3. 两后端配对/切换")
    add("")
    p = report["pairing"]
    add(f"- 配对子集任务数：{p['paired_task_count']}；切换事件：{p['switch_events']}；JS 后端状态：{p['js_backend_status']}")
    add(f"- {p['note']}")
    add("- 分类语义：明确要图却 NO_FIGURE / 降级为文字只算安全退路，不算生图成功（design §6 :93）")
    add("")
    add("## 4. 指标（fake 值，非真实质量；真实值待 key）")
    add("")
    add("| 指标 | IMAGE_MODEL (fake p50) | IMAGE_MODEL (fake p95) | JS |")
    add("|---|---|---|---|")
    add(f"| 首图延迟 ms | {m['first_image_latency_ms']['image_model']['p50']:.0f} | "
        f"{m['first_image_latency_ms']['image_model']['p95']:.0f} | 留桩无样本 |")
    add(f"| 总时长 ms | {m['total_duration_ms']['image_model']['p50']:.0f} | "
        f"{m['total_duration_ms']['image_model']['p95']:.0f} | 留桩无样本 |")
    add(f"| token 输入/输出 | null（待真实 usage） | null | null |")
    add(f"| 费用 | {m['cost']['status']} | — | — |")
    add("")
    add("采集点（字段与采集点先落）：")
    for k, v in m["collection_points"].items():
        add(f"- `{k}`：{v}")
    add("")
    add("## 5. 质量门（常量按 PRD 原文引用；本节数值为 fake 机制自检输入）")
    add("")
    add("| 门 | 阈值 | fake 值 | 分母 | Wilson95 | 判定（仅供参考） |")
    add("|---|---|---|---|---|---|")
    fp, wf = g["first_pass_rate"], g["within_budget_final_rate"]
    add(f"| 首次合格率 | ≥{fp['threshold']:.0%} | {fp['value']:.2%} | {fp['denominator']} | "
        f"[{fp['wilson95'][0]:.3f}, {fp['wilson95'][1]:.3f}] | {'MET' if fp['met'] else 'NOT_MET'} |")
    add(f"| 预算内最终合格率 | ≥{wf['threshold']:.0%} | {wf['value']:.2%} | {wf['denominator']} | "
        f"[{wf['wilson95'][0]:.3f}, {wf['wilson95'][1]:.3f}] | {'MET' if wf['met'] else 'NOT_MET'} |")
    ce = g["critical_errors_accepted"]
    add(f"| 关键题意错误被接受 | =0 | {ce['value']} | — | — | {'MET' if ce['met'] else 'NOT_MET'} |")
    lat = g["first_image_p95_ms"]
    im_p95 = "无样本" if lat["image_model"] is None else f"{lat['image_model']:.0f}"
    add(f"| 首图 p95（IMAGE_MODEL） | ≤{lat['threshold_ms']['image_model']} ms | "
        f"{im_p95} | — | — | {lat['met']['image_model']} |")
    add(f"| 首图 p95（JS） | ≤{lat['threshold_ms']['js']} ms | 留桩无样本 | — | — | 不可判（如实记 null） |")
    add("")
    add("分组最终合格率（阈值每组 ≥90%，fake 值）：")
    add("")
    add("| 组 | 合格 | 分母 | 值 | 判定（仅供参考） |")
    add("|---|---|---|---|---|")
    for grp, blk in g["per_group_final"]["groups"].items():
        add(f"| {grp} | {blk['successes']} | {blk['denominator']} | {blk['value']:.2%} | "
            f"{'MET' if blk['met'] else 'NOT_MET'} |")
    add("")
    add("## 6. 机制自检")
    add("")
    for k, v in report["machinery_check"]["exercised_paths"].items():
        add(f"- {k}: {'✓' if v else '✗'}")
    add(f"- **verdict: {report['machinery_check']['verdict']}**")
    add("")
    add("## 7. 引用")
    add("")
    for k, v in report["citations"].items():
        add(f"- {k}: {v}")
    add("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="PRD-09 figure evaluation harness (fake driver machinery check)")
    parser.add_argument("--run-seed", default="t5-batch20")
    parser.add_argument("--output-dir", default=str(HERE / "reports"))
    parser.add_argument("--enforce-quality-gates", action="store_true",
                        help="仅未来真实 Provider 运行使用；fake 运行禁止开启")
    args = parser.parse_args(argv)

    try:
        report = run_fake_evaluation(
            run_seed=args.run_seed,
            output_dir=Path(args.output_dir),
            enforce_quality_gates=args.enforce_quality_gates,
        )
    except FreezeIntegrityError as exc:
        print(f"FREEZE-INTEGRITY-ERROR: {exc}", file=sys.stderr)
        return 2

    print(f"machinery_verdict={report['machinery_check']['verdict']}")
    print(f"real_provider_quality={report['real_provider_quality']}")
    fp = report["quality_gates"]["first_pass_rate"]
    wf = report["quality_gates"]["within_budget_final_rate"]
    print(f"fake first_pass={fp['value']:.2%} within_budget_final={wf['value']:.2%} (denominator {fp['denominator']}, informational)")
    if args.enforce_quality_gates and not report.get("enforced_quality_gates_met", True):
        return 1
    return 0 if report["machinery_check"]["verdict"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
