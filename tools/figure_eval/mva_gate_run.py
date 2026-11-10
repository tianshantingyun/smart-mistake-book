# -*- coding: utf-8 -*-
"""MVA JS 臂门读数装配器（批次 51 / G4 T29）。

把三类产物合成为 `mva_gate.gate_reading` 需要的记录形状：

1. 设备适配器产物（`MvaJsArmAdapterInstrumentedTest`：record/drawops/scene/png）；
2. 确定性事实提取（`fact_extract`，A1 类）；
3. VLM 判官事实（`mva_vlm_judge`，C 通道；只覆盖 direction/ordering/count/label_absent）。

纪律：

- **只有 critical 事实全部可判的题才进入门读数**；缺任一 critical 事实 → `evaluable=false`，
  该题 `first_attempt_facts=None/final_facts=None`（mva_gate 记无样本，不记 0 命中）。
- **不伪造事实**：`facts` 只来自提取器/判官的实际输出；没有值就不写。
- 门值仍由 `mva_gate.gate_reading` 计算（阈值 90/95/90/0、p95 30s/120s 不动）。
- 同时产出逐题 `task_records.json`（含缺失 key、判官回答、adapter 诊断），供复核。
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

import fact_extract as fx  # noqa: E402
import figure_eval_gate as fem  # noqa: E402
import mva_gate  # noqa: E402


def load_tasks(path: Path) -> dict[str, dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    return {task["task_id"]: task for task in payload["tasks"]}


def index_adapter(adapter_out: Path) -> dict[str, dict]:
    index: dict[str, dict] = {}
    for path in sorted(adapter_out.glob("*.record.json")):
        record = json.loads(path.read_text(encoding="utf-8"))
        task_id = record.get("task_id")
        if not task_id:
            continue
        base = path.name.replace(".record.json", "")
        draw = adapter_out / (base + ".drawops.json")
        scene = adapter_out / (base + ".scene.json")
        png = adapter_out / (base + ".png")
        index[task_id] = {
            "record": record,
            "drawops": draw if draw.is_file() else None,
            "scene": scene if scene.is_file() else None,
            "png": png if png.is_file() else None,
        }
    return index


def index_judge(judge_out: Path | None) -> dict[str, dict]:
    if not judge_out:
        return {}
    path = judge_out / "judge_records.json"
    if not path.is_file():
        return {}
    records = json.loads(path.read_text(encoding="utf-8"))
    return {record["task_id"]: record for record in records}


def index_author(author_manifest: Path | None) -> dict[str, dict]:
    if not author_manifest:
        return {}
    if not author_manifest.is_file():
        return {}
    manifest = json.loads(author_manifest.read_text(encoding="utf-8"))
    return {record["task_id"]: record for record in manifest.get("records", [])}


def build(tasks_path: Path, adapter_out: Path, judge_out: Path | None,
          author_manifest: Path | None, out_dir: Path) -> dict:
    tasks = load_tasks(tasks_path)
    adapter = index_adapter(adapter_out)
    judge = index_judge(judge_out)
    author = index_author(author_manifest)
    out_dir.mkdir(parents=True, exist_ok=True)

    gate_records: dict[str, dict] = {}
    task_records = []
    adapter_status = Counter()
    author_status = Counter()
    missing_by_key: Counter = Counter()
    evaluable_by_group: Counter = Counter()
    total_by_group: Counter = Counter()
    judge_answered_facts = 0

    for task_id, task in tasks.items():
        group = task["group"]
        total_by_group[group] += 1
        entry = adapter.get(task_id)
        author_record = author.get(task_id) or {}
        if author_record:
            author_status[author_record.get("status")] += 1
        if entry is None:
            adapter_status["MISSING"] += 1
            task_records.append({
                "task_id": task_id, "group": group,
                "author_status": author_record.get("status"),
                "adapter_status": "MISSING", "evaluable": False,
                "verdict": "NOT_EVALUABLE", "reason": "适配器没有该题 record",
            })
            continue

        record = entry["record"]
        status = record.get("status")
        adapter_status[status] += 1
        render_latency_ms = record.get("latency_ms")
        author_elapsed_ms = author_record.get("elapsed_ms")
        # 设计 §6 的「首张合格图 p95」是请求→首图：作者模型耗时 + 设备渲染耗时。
        # 缺任一侧就记 None（不猜），门读数侧据此不给 p95 样本。
        end_to_end_latency_ms = (
            author_elapsed_ms + render_latency_ms
            if isinstance(author_elapsed_ms, (int, float))
            and isinstance(render_latency_ms, (int, float))
            else None
        )
        base = {
            "task_id": task_id, "group": group,
            "author_status": author_record.get("status"),
            "adapter_status": status,
            "adapter_reason": record.get("reason"),
            "adapter_diagnostics": (record.get("diagnostics") or "")[:512],
            "latency_ms": render_latency_ms,
            "render_latency_ms": render_latency_ms,
            "author_elapsed_ms": author_elapsed_ms,
            "end_to_end_latency_ms": end_to_end_latency_ms,
        }
        if status != "RENDERED" or entry["drawops"] is None:
            task_records.append({**base, "evaluable": False, "verdict": "NOT_EVALUABLE",
                                 "reason": "渲染未产出 DrawOps"})
            continue

        draw_ops = json.loads(entry["drawops"].read_text(encoding="utf-8"))
        scene = json.loads(entry["scene"].read_text(encoding="utf-8")) if entry["scene"] else None
        metrics = record.get("measurement") or []
        keys = [fact["key"] for fact in task["fact_expectations"]]
        extraction = fx.extract(draw_ops, metrics, keys=keys, scene=scene)
        facts = dict(extraction.decoded)

        judge_record = judge.get(task_id)
        judge_facts = (judge_record or {}).get("facts") or {}
        for key, value in judge_facts.items():
            if key in keys and key not in facts:
                facts[key] = value
                judge_answered_facts += 1

        missing = [key for key in keys if key not in facts]
        critical_missing = [
            fact["key"] for fact in task["fact_expectations"]
            if fact.get("critical") and fact["key"] not in facts
        ]
        for key in missing:
            missing_by_key[key] += 1
        evaluable = not critical_missing
        verdict = None
        if evaluable:
            verdict = fem.check_fact_expectations(
                [{"key": key, "value": value} for key, value in facts.items()],
                task["fact_expectations"],
            )
            evaluable_by_group[group] += 1

        stats = record.get("stats") or {}
        has_label_facts = any(fact["kind"] in ("label_set", "label_absent")
                              for fact in task["fact_expectations"])
        render_ok = True
        geometry_ok = int(stats.get("pathCommands") or 0) > 0
        text_ok = (int(stats.get("labels") or 0) > 0) if has_label_facts else True

        task_records.append({
            **base,
            "evaluable": evaluable,
            "verdict": ("ACCEPTED" if verdict and verdict["accepted"]
                        else ("REJECTED" if evaluable else "NOT_EVALUABLE")),
            "facts": facts,
            "judge_facts": judge_facts,
            "missing_keys": missing,
            "critical_missing_keys": critical_missing,
            "mismatches": (verdict or {}).get("mismatches", []),
            "render_ok": render_ok,
            "geometry_ok": geometry_ok,
            "text_ok": text_ok,
        })

        if evaluable:
            gate_records[task_id] = {
                "task_id": task_id,
                "first_attempt_facts": facts,
                "final_facts": facts,
                "latency_ms": end_to_end_latency_ms,
                "render_latency_ms": render_latency_ms,
                "render_ok": render_ok,
                "geometry_ok": geometry_ok,
                "text_ok": text_ok,
                "accepted_self_report": False,
                "critical_error_register": [],
            }

    fixture = mva_gate.verify_fixture(HERE)
    blind = mva_gate.load_tasks(HERE / mva_gate.BLIND_FILE)
    dev = mva_gate.load_tasks(HERE / mva_gate.DEV_FILE)
    isolation = mva_gate.blind_dev_isolation(blind, dev)
    readings = {
        arm: mva_gate.gate_reading(blind, gate_records if arm == "JS" else {},
                                   arm=arm, latency_threshold_ms=threshold)
        for arm, threshold in (
            ("IMAGE_MODEL", fem.GATES["first_image_p95_ms_image_model_max"]["value"]),
            ("JS", fem.GATES["first_image_p95_ms_js_max"]["value"]),
        )
    }
    preflight = {
        "schema_version": mva_gate.SCHEMA_VERSION,
        "generated_by": "tools/figure_eval/mva_gate_run.py",
        "fixture": fixture,
        "isolation": isolation,
        "readings": readings,
    }
    summary = {
        "generated_by": "tools/figure_eval/mva_gate_run.py",
        "tasks": len(tasks),
        "author_status_counts": dict(sorted(author_status.items())),
        "adapter_status_counts": dict(sorted(adapter_status.items())),
        "evaluable_tasks": len(gate_records),
        "evaluable_coverage": len(gate_records) / len(tasks) if tasks else None,
        "evaluable_by_group": dict(sorted(evaluable_by_group.items())),
        "total_by_group": dict(sorted(total_by_group.items())),
        "judge_answered_facts": judge_answered_facts,
        "missing_facts_by_key": dict(sorted(missing_by_key.items())),
        "gate_readable": bool(tasks and len(gate_records) == len(tasks)),
        "note": ("gate_readable 只在全部题 critical 事实可判时为真；否则这是 "
                 "NOT_EVALUABLE/partial 的回归集描述统计，不是启用门值"),
    }
    (out_dir / "task_records.json").write_text(
        json.dumps(task_records, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (out_dir / "gate_records.json").write_text(
        json.dumps(gate_records, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (out_dir / "mva_preflight.json").write_text(
        json.dumps(preflight, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (out_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="MVA JS 臂门读数装配")
    parser.add_argument("--tasks", required=True)
    parser.add_argument("--adapter-out", required=True)
    parser.add_argument("--judge-out")
    parser.add_argument("--author-manifest")
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    summary = build(
        Path(args.tasks), Path(args.adapter_out),
        Path(args.judge_out) if args.judge_out else None,
        Path(args.author_manifest) if args.author_manifest else None,
        Path(args.out),
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
