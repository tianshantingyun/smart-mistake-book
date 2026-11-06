#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""逐页裁定文档（`rulings.json`）的构建与落盘 —— AUD-08 item 30。

## 它消灭的具体失败

门脚本此前只产出一次性报告（`--report-json`）与证据 md：两次判门的报告混在一起
无法判断哪份更新；"这一页判了什么、依据哪一组几何、谁判的"每次运行都被覆盖成新文本。
更具体地，三种错误此前都会发生且没有门会红：

  ① 机器算出的"不一致"被读成"有人工裁定过"（报告里没有 `decidedBy.kind`）；
  ② 人工改过的框在下次判门时被脚本"重算"覆盖，改动无痕（裁定不进 append-only 文件）；
  ③ 同名页换了框仍复用旧裁定（裁定只绑定文件名，不绑定几何摘要）。

因此本模块只做一件事：把 `check_split_gate.py` 的**确定性度量结果**渲染成
一 run 一份、append-only 的 `rulings.json`，并在落盘前用 `validate_rulings.py`
自检（不变量不过就拒绝落盘——不写自相矛盾的裁定）。

## 与"标签裁定"的分界

本文件只写**门裁定**（脚本对"系统输出 vs 冻结金标"的计算）。金标本身的框对不对、
是谁标的，属**标签裁定**，冻结在 golden v2 的 `labelers[]/adjudication` 里（见
`docs/research/2026-11-02-aud08-annotation.md` §2.1）。两者字段名不共用：
这里 `decidedBy.kind` 只可能是 `automated`（`agentId` 以 `script:` 开头）。

## 落盘

`--rulings-out <dir>` → `<dir>/<runId>.json`；已存在即拒绝（一次运行一份）。
序列化 `json.dumps(..., ensure_ascii=False, sort_keys=True, indent=2) + "\\n"`，
UTF-8、LF。`runId` 与每页 `decidedAtUtc` 是**运行身份**，不参与"同输入重跑逐字节一致"
的比较（`rulings` 数组本身是内容）。
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

HERE: Path = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import validate_rulings  # noqa: E402  （同目录；它不反向导入本模块，无环）

RULING_SCHEMA_VERSION = 1
# 主判词优先级（后者覆盖前者）；数值子判据全部保留，优先级只决定一个主判词。
VERDICT_PRIORITY = ("NOT_EVALUABLE", "FRAGMENTED", "MISSING_IN_APP",
                    "INCONSISTENT_COUNT", "CONSISTENT")
RULE_IDS = {
    "NOT_EVALUABLE": "not-evaluable",
    "FRAGMENTED": "fragment-0.5",
    "MISSING_IN_APP": "app-page-presence",
    "INCONSISTENT_COUNT": "count-v2",
    "CONSISTENT": "count-v2+iou-maxweight-0.5+fragment-0.5",
}
IOU_THRESHOLD = 0.5


def canonical_sha256(value: object) -> str:
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                                     separators=(",", ":")).encode("utf-8")).hexdigest()


def _tool_identity() -> dict:
    gate = Path(__file__).resolve().with_name("check_split_gate.py")
    return {"file": gate.name,
            "sha256": hashlib.sha256(gate.read_bytes()).hexdigest()}


def _verdict(row: dict) -> str:
    """门裁定主判词（R5 §2.2）：NOT_EVALUABLE > FRAGMENTED > MISSING_IN_APP >
    INCONSISTENT_COUNT > CONSISTENT。当前数据里 NOT_EVALUABLE 不出现（不可判的运行
    直接不生成 rulings），保留该枚举供未来使用。"""
    if row.get("appPageMissing"):
        return "MISSING_IN_APP"
    if row.get("fragmentedGoldIndices"):
        return "FRAGMENTED"
    if not row.get("consistent"):
        return "INCONSISTENT_COUNT"
    return "CONSISTENT"


def _ruling(row: dict, *, generated_at_utc: str, level: str | None,
            tool_sha256: str) -> dict:
    verdict = _verdict(row)
    fragmented = list(row.get("fragmentedGoldIndices") or [])
    return {
        "pageId": row["file"],
        "pageIdKind": "golden_filename",
        "appRef": {
            "pageId": row.get("appPageId"),
            "pageIdKind": row.get("appPageIdKind"),
            "jobId": row.get("appJobId"),
            "pageIndex": row.get("appPageIndex"),
            "nameSource": row.get("appNameSource"),
            "pagePresent": not row.get("appPageMissing"),
        },
        "gold": {"questionCount": row["goldQuestions"],
                 "boxesSha256": row["goldBoxesSha256"]},
        "app": {"boxCount": row["appBoxes"],
                "boxesSha256": row["appBoxesSha256"]},
        "diff": {
            "countDelta": row["appBoxes"] - row["goldQuestions"],
            "countVerdict": "CONSISTENT" if row["consistent"] else "INCONSISTENT_COUNT",
            "iouByGoldIndex": list(row["matchedIous"]),
            "iouOkCount": row["iouOk"],
            "fragmentPiecesByGoldIndex": list(row["fragmentPiecesByGoldIndex"]),
            "fragmentedGoldIndices": fragmented,
            "appPagePresent": not row.get("appPageMissing"),
            "unmatchedAppBoxCount": row["unmatchedAppBoxCount"],
        },
        "ruling": {
            "verdict": verdict,
            "redLineHit": bool(fragmented),
            "decidedBy": {
                "kind": "automated",
                "agentId": f"script:check_split_gate.py@sha256:{tool_sha256[:12]}",
                "ruleId": RULE_IDS[verdict],
            },
            "decidedAtUtc": generated_at_utc,
            "evidenceGradeLevel": level,
        },
    }


def _summary(rulings: list[dict]) -> dict:
    verdicts = [entry["ruling"]["verdict"] for entry in rulings]
    return {
        "pages": len(rulings),
        "consistent": verdicts.count("CONSISTENT"),
        "inconsistentCount": verdicts.count("INCONSISTENT_COUNT"),
        "fragmented": verdicts.count("FRAGMENTED"),
        "missingInApp": verdicts.count("MISSING_IN_APP"),
        "notEvaluable": verdicts.count("NOT_EVALUABLE"),
    }


def build_document(report: dict, *, run_id: str, generated_at_utc: str,
                   command: list[str]) -> dict:
    """门报告 → rulings 文档（纯函数，不落盘）。门不可判时拒绝构建。"""
    state = (report.get("gate") or {}).get("state")
    if state not in ("PASS", "FAIL"):
        raise ValueError(f"门状态 {state!r} 不可判：不得伪造逐页裁定（不生成 rulings）")
    golden = report["golden"]
    app = report["app"]
    source = app.get("source") or {}
    level = golden.get("evidenceGradeLevel")
    tool = _tool_identity()
    rows = report.get("perPage") or []
    rulings = [_ruling(row, generated_at_utc=generated_at_utc, level=level,
                       tool_sha256=tool["sha256"]) for row in rows]
    gate = report["gate"]
    document = {
        "rulingSchemaVersion": RULING_SCHEMA_VERSION,
        "run": {
            "runId": run_id,
            "generatedAtUtc": generated_at_utc,
            "tool": tool,
            "command": list(command),
        },
        "inputs": {
            "golden": {
                "file": golden.get("file"),
                "sha256": golden.get("sha256"),
                "pageCount": golden.get("pageCount"),
                "evidenceGrade": golden.get("evidenceGrade"),
                "evidenceGradeLevel": level,
                "evidenceLevelRecomputable": golden.get("evidenceLevelRecomputable"),
            },
            "app": {
                "file": app.get("file"),
                "sha256": app.get("sha256"),
                "appSchemaVersion": app.get("appSchemaVersion"),
                "snapshotIdentitySha256": source.get("snapshotIdentitySha256"),
                "payloadSha256": source.get("payloadSha256"),
                "dbUserVersion": source.get("dbUserVersion"),
            },
            "caliber": (report.get("measure") or {}).get("caliber") or "v2",
            "thresholds": {key: {"value": meta["value"],
                                 "comparison": meta["comparison"]}
                           for key, meta in report["thresholds"].items()},
        },
        "gate": {
            "state": state,
            "passed": bool(gate["passed"]),
            "count": dict(gate["count"]),
            "fragment": dict(gate["fragment"]),
        },
        "hardRedLines": report.get("hardRedLines"),
        "scope": report.get("scope"),
        "rulings": rulings,
        "summary": _summary(rulings),
    }
    document["rulingsSha256"] = canonical_sha256(rulings)
    return document


def write_document(document: dict, directory: Path) -> Path:
    """校验后写 `<directory>/<runId>.json`；已存在即拒绝（一次运行一份）。"""
    failures = validate_rulings.validate_document(document)
    if failures:
        raise ValueError("裁定文档自检失败（拒绝落盘）：" + "；".join(failures[:5]))
    run_id = document["run"]["runId"]
    if not isinstance(run_id, str) or not run_id.strip():
        raise ValueError("runId 为空，拒绝落盘")
    path = Path(directory) / f"{run_id}.json"
    if path.exists():
        raise FileExistsError(f"裁定文件已存在，不覆盖（一次运行一份）：{path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(document, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
                    encoding="utf-8", newline="")
    return path
