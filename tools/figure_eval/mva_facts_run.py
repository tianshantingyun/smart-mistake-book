# -*- coding: utf-8 -*-
"""MVA JS 臂事实核验运行器（批次 51 / G4 T27-T29 的读数面）。

输入（全部是设备轨产物，无网络）：
- `--tasks`：冻结夹具（`tasks_blind_120.json` 或 dev 集）；
- `--adapter-out`：`MvaJsArmAdapterInstrumentedTest` 的输出目录（`manifest.json` +
  每题 `.record.json` / `.drawops.json` / `.scene.json`，度量为 record 内嵌的
  `measurement`）。

输出：
- `facts_records.json`：逐题 `{task_id, adapter_status, facts, fact_status, undecodable,
  evaluable, verdict, mismatches}`；
- `facts_summary.json`：计数与覆盖率；
- 终端的精简摘要。

纪律：
- **不可评不是 0 分**：没有 RENDERED 产物或任一 critical 事实 UNDECODABLE 的题，
  `evaluable=false`，不进 accepted/rejected 比率的分母；
- **partial 不得读成 PASS**：`gate_readable` 只在 `evaluable_coverage == 1.0` 时为真；
- 判定走既有 `figure_eval_gate.check_fact_expectations`，不另写比较器。

运行：
    python -I -X utf8 tools/figure_eval/mva_facts_run.py \
        --tasks tools/figure_eval/tasks_blind_120.json \
        --adapter-out docs/research/evidence/2026-11-10-batch-51/mva/device-blind \
        --out docs/research/evidence/2026-11-10-batch-51/mva/device-blind/facts
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


def load_tasks(path: Path) -> dict[str, dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    return {task["task_id"]: task for task in payload["tasks"]}


def records_by_task(adapter_out: Path) -> dict[str, dict]:
    """扫 `*.record.json`，按 record 内的 task_id 建索引。

    不按 `manifest.json` 的 `input_file` 拼文件名：适配器磁盘名带序号后缀
    （`<name>-<index>.record.json`），拼出来的名字不存在。
    """
    index: dict[str, dict] = {}
    for path in sorted(adapter_out.glob("*.record.json")):
        record = json.loads(path.read_text(encoding="utf-8"))
        task_id = record.get("task_id")
        if task_id:
            index[task_id] = {"record": record, "path": path}
    return index


def run(tasks_path: Path, adapter_out: Path, out_dir: Path) -> dict:
    tasks = load_tasks(tasks_path)
    index = records_by_task(adapter_out)
    out_dir.mkdir(parents=True, exist_ok=True)

    records = []
    status_counts: Counter = Counter()
    undecodable_by_key: Counter = Counter()
    class_counts: Counter = Counter()
    accepted = 0
    rejected_by_facts = 0
    evaluable = 0

    for task_id, task in tasks.items():
        entry = index.get(task_id)
        if entry is None:
            records.append({
                "task_id": task_id,
                "adapter_status": "MISSING",
                "evaluable": False,
                "verdict": "NOT_EVALUABLE",
                "reason": "适配器没有该题的 record（未渲染或未跑）",
            })
            status_counts["MISSING"] += 1
            continue
        record = entry["record"]
        adapter_status = record.get("status")
        status_counts[adapter_status] += 1
        if adapter_status != "RENDERED":
            records.append({
                "task_id": task_id,
                "adapter_status": adapter_status,
                "adapter_reason": record.get("reason"),
                "diagnostics": (record.get("diagnostics") or "")[:512],
                "evaluable": False,
                "verdict": "NOT_EVALUABLE",
                "reason": "渲染未产出场景（无事实可判）",
            })
            continue

        draw_path = entry["path"].with_name(entry["path"].name.replace(".record.json", ".drawops.json"))
        if not draw_path.is_file():
            records.append({
                "task_id": task_id,
                "adapter_status": adapter_status,
                "evaluable": False,
                "verdict": "NOT_EVALUABLE",
                "reason": f"缺少 drawops 产物：{draw_path.name}",
            })
            continue
        draw_ops = json.loads(draw_path.read_text(encoding="utf-8"))
        scene_path = entry["path"].with_name(entry["path"].name.replace(".record.json", ".scene.json"))
        scene = json.loads(scene_path.read_text(encoding="utf-8")) if scene_path.is_file() else None
        metrics = record.get("measurement") or []

        keys = [fact["key"] for fact in task["fact_expectations"]]
        extraction = fx.extract(draw_ops, metrics, keys=keys, scene=scene)
        verdict = fem.check_fact_expectations(extraction.as_output_facts(), task["fact_expectations"])
        task_record = fx.task_record(task, extraction)
        for key, fact_entry in extraction.facts.items():
            class_counts[fact_entry["class"]] += 1
            if fact_entry["status"] == fx.UNDECODABLE:
                undecodable_by_key[key] += 1
        if task_record["evaluable"]:
            evaluable += 1
            if verdict["accepted"]:
                accepted += 1
            else:
                rejected_by_facts += 1
        records.append({
            "task_id": task_id,
            "group": task["group"],
            "figure_type": task["figure_type"],
            "adapter_status": adapter_status,
            "adapter_latency_ms": record.get("latency_ms"),
            "facts": extraction.decoded,
            "fact_status": task_record["fact_status"],
            "undecodable": extraction.undecodable,
            "blocking_undecodable": task_record["blocking_undecodable"],
            "evaluable": task_record["evaluable"],
            "verdict": "ACCEPTED" if verdict["accepted"] else "REJECTED",
            "mismatches": verdict["mismatches"],
        })

    denominator = len(tasks)
    summary = {
        "generated_by": "tools/figure_eval/mva_facts_run.py",
        "tasks": denominator,
        "adapter_status_counts": dict(sorted(status_counts.items())),
        "rendered": sum(1 for record in records if record.get("adapter_status") == "RENDERED"),
        "evaluable": evaluable,
        "evaluable_coverage": (evaluable / denominator) if denominator else None,
        "accepted": accepted,
        "rejected_by_facts": rejected_by_facts,
        "accept_rate_among_evaluable": (accepted / evaluable) if evaluable else None,
        "undecodable_by_key": dict(sorted(undecodable_by_key.items())),
        "fact_class_counts": dict(sorted(class_counts.items())),
        "gate_readable": bool(denominator and evaluable == denominator),
        "note": (
            "gate_readable 只在全部题 critical 事实可判（无 D3/A2 缺口）时为真；"
            "否则这是 NOT_EVALUABLE/partial 的描述统计，不是 MVA 门值"
        ),
    }
    (out_dir / "facts_records.json").write_text(
        json.dumps(records, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (out_dir / "facts_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="MVA JS 臂事实核验（只读设备产物）")
    parser.add_argument("--tasks", required=True)
    parser.add_argument("--adapter-out", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    summary = run(Path(args.tasks), Path(args.adapter_out), Path(args.out))
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
