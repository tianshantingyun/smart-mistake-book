#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""`rulings.json` 的机械校验器（AUD-08 item 30 的负控面）。

## 它消灭的具体失败

裁定文件若只靠"生成它的脚本"保证正确，任何人手改一处数字（把 `iouOkCount` +1、
把 `decidedBy.kind` 从 `automated` 改成 `human`、换掉一个 `boxesSha256`）都不会被发现，
而这份文件正是"逐页判了什么"的唯一证据。本校验器把 R5 §2.4 的六条不变量与字段语义
全部机械化，**不读度量脚本、不重算几何**——它只检查文件自身是否自洽：

  R1 schema 版本 / R2 运行身份 / R3 输入绑定（sha256 形状）
  R4 逐页记录：题数=IoU 列表长度、iouOkCount=阈值计数、切残索引=碎片计数、
     countDelta=框数差、主判词与子判据一致
  R5 内容摘要：`rulingsSha256` 覆盖 `rulings` 数组（改一处摘要即红）
  R6 summary 与逐页判词计数一致
  R7 门判定：`passed` 与阈值闭边界复算一致，`state` 与 `passed` 一致
  R8 硬红线：切残题数 = 含切残页数之和，`satisfied` 仅在 0 时为真
  R9 裁定人：kind ∈ {automated,human,model}；automated 的 agentId 必须以
     `script:` 开头（机器判定不得填人名），human/model 不得以 `script:` 开头

**这不是防篡改**：谁能改文件谁就能重算摘要。它防的是"改了一处却以为文件没变"，
并把"机器裁定 vs 人工裁定"的语义变成可失败的检查。

用法（仓库根）：

    python tools/split_golden/validate_rulings.py --file build/split-eval/rulings/<runId>.json

exit 0 = 全部不变量通过；1 = 有不变量失败；2 = 文件不可读/不是 JSON。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

RULING_SCHEMA_VERSION = 1
_ISO_8601_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$")
_SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
_VERDICTS = {"CONSISTENT", "INCONSISTENT_COUNT", "MISSING_IN_APP",
             "FRAGMENTED", "NOT_EVALUABLE"}
_DECIDED_BY_KINDS = {"automated", "human", "model"}
_IOU_THRESHOLD = 0.5


def _sha_shape(value: Any) -> bool:
    return isinstance(value, str) and bool(_SHA256_RE.match(value))


def _is_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _is_number(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def _check_run(doc: dict) -> list[str]:
    failures: list[str] = []
    run = doc.get("run")
    if not isinstance(run, dict):
        return ["R2: run 不是对象"]
    if not isinstance(run.get("runId"), str) or not run["runId"].strip():
        failures.append("R2: run.runId 不是非空字符串")
    if not isinstance(run.get("generatedAtUtc"), str) \
            or not _ISO_8601_RE.match(run["generatedAtUtc"]):
        failures.append("R2: run.generatedAtUtc 不是 ISO-8601")
    tool = run.get("tool")
    if not isinstance(tool, dict) or not tool.get("file"):
        failures.append("R2: run.tool 缺 file")
    elif not _sha_shape(tool.get("sha256")):
        failures.append("R2: run.tool.sha256 不是 64 位小写 hex")
    command = run.get("command")
    if not isinstance(command, list) or not command \
            or not all(isinstance(part, str) for part in command):
        failures.append("R2: run.command 必须是非空字符串数组")
    return failures


def _check_inputs(doc: dict) -> list[str]:
    failures: list[str] = []
    inputs = doc.get("inputs")
    if not isinstance(inputs, dict):
        return ["R3: inputs 不是对象"]
    golden = inputs.get("golden")
    app = inputs.get("app")
    if not isinstance(golden, dict):
        failures.append("R3: inputs.golden 不是对象")
        golden = {}
    if not isinstance(app, dict):
        failures.append("R3: inputs.app 不是对象")
        app = {}
    for label, block in (("golden", golden), ("app", app)):
        if not _sha_shape(block.get("sha256")):
            failures.append(f"R3: inputs.{label}.sha256 不是 64 位小写 hex")
    if not _is_int(golden.get("pageCount")):
        failures.append("R3: inputs.golden.pageCount 不是整数")
    thresholds = inputs.get("thresholds")
    if not isinstance(thresholds, dict) or not {
            "countExactMin", "fragmentMax"} <= set(thresholds):
        failures.append("R3: inputs.thresholds 缺 countExactMin/fragmentMax")
    return failures


def _check_ruling(index: int, entry: Any) -> list[str]:
    where = f"rulings[{index}]"
    if not isinstance(entry, dict):
        return [f"R4: {where} 不是对象"]
    failures: list[str] = []
    for key in ("pageId", "pageIdKind", "appRef", "gold", "app", "diff", "ruling"):
        if key not in entry:
            failures.append(f"R4: {where} 缺 {key}")
    if failures:
        return failures
    if not isinstance(entry["pageId"], str) or not entry["pageId"].strip():
        failures.append(f"R4: {where}.pageId 不是非空字符串")
    gold, app, diff, ruling = entry["gold"], entry["app"], entry["diff"], entry["ruling"]
    for label, block in (("gold", gold), ("app", app), ("diff", diff), ("ruling", ruling)):
        if not isinstance(block, dict):
            failures.append(f"R4: {where}.{label} 不是对象")
    if failures:
        return failures
    for label, block, count_key in (("gold", gold, "questionCount"),
                                    ("app", app, "boxCount")):
        if not _is_int(block.get(count_key)):
            failures.append(f"R4: {where}.{label}.{count_key} 不是整数")
        elif not _sha_shape(block.get("boxesSha256")):
            failures.append(f"R4: {where}.{label}.boxesSha256 不是 64 位小写 hex")
    ious = diff.get("iouByGoldIndex")
    if not isinstance(ious, list):
        return failures + [f"R4: {where}.diff.iouByGoldIndex 不是数组"]
    gold_count = gold.get("questionCount")
    if _is_int(gold_count) and len(ious) != gold_count:
        failures.append(f"R4: {where} gold.questionCount={gold_count} != "
                        f"len(iouByGoldIndex)={len(ious)}")
    expected_ok = sum(1 for value in ious if _is_number(value) and value >= _IOU_THRESHOLD)
    if diff.get("iouOkCount") != expected_ok:
        failures.append(f"R4: {where} iouOkCount={diff.get('iouOkCount')!r} != "
                        f"IoU≥{_IOU_THRESHOLD} 计数 {expected_ok}")
    pieces = diff.get("fragmentPiecesByGoldIndex")
    if not isinstance(pieces, list) or not all(_is_int(count) for count in pieces):
        failures.append(f"R4: {where}.diff.fragmentPiecesByGoldIndex 不是整数数组")
        pieces = []
    elif _is_int(gold_count) and len(pieces) != gold_count:
        failures.append(f"R4: {where} len(fragmentPieces)={len(pieces)} != "
                        f"gold.questionCount={gold_count}")
    expected_fragmented = [position for position, count in enumerate(pieces) if count >= 2]
    if diff.get("fragmentedGoldIndices") != expected_fragmented:
        failures.append(f"R4: {where} fragmentedGoldIndices={diff.get('fragmentedGoldIndices')!r}"
                        f" != 碎片计数推导 {expected_fragmented}")
    box_count = app.get("boxCount")
    if _is_int(box_count) and _is_int(gold_count) \
            and diff.get("countDelta") != box_count - gold_count:
        failures.append(f"R4: {where} countDelta={diff.get('countDelta')!r} != "
                        f"app.boxCount-{gold_count}={box_count - gold_count}")
    if diff.get("countVerdict") not in ("CONSISTENT", "INCONSISTENT_COUNT"):
        failures.append(f"R4: {where} countVerdict 取值非法")
    if not isinstance(diff.get("appPagePresent"), bool):
        failures.append(f"R4: {where}.diff.appPagePresent 不是布尔")

    verdict = ruling.get("verdict")
    if verdict not in _VERDICTS:
        failures.append(f"R4: {where} verdict={verdict!r} 不在 {sorted(_VERDICTS)}")
    if not isinstance(ruling.get("redLineHit"), bool):
        failures.append(f"R4: {where}.ruling.redLineHit 不是布尔")
    elif ruling["redLineHit"] != bool(expected_fragmented):
        failures.append(f"R4: {where} redLineHit={ruling['redLineHit']} != "
                        f"存在切残题 {bool(expected_fragmented)}")
    # 主判词必须与子判据一致（优先级只决定一个主判词，不丢子判据）
    if verdict == "FRAGMENTED" and not expected_fragmented:
        failures.append(f"R4: {where} verdict=FRAGMENTED 但无切残题")
    if verdict == "MISSING_IN_APP" and diff.get("appPagePresent") is not False:
        failures.append(f"R4: {where} verdict=MISSING_IN_APP 但 appPagePresent 非 false")
    if verdict == "INCONSISTENT_COUNT" and diff.get("countVerdict") != "INCONSISTENT_COUNT":
        failures.append(f"R4: {where} verdict=INCONSISTENT_COUNT 但 countVerdict 非不一致")
    if verdict == "CONSISTENT" and (diff.get("countVerdict") != "CONSISTENT"
                                    or expected_fragmented
                                    or diff.get("appPagePresent") is not True):
        failures.append(f"R4: {where} verdict=CONSISTENT 但子判据不支持")
    if not isinstance(ruling.get("decidedAtUtc"), str) \
            or not _ISO_8601_RE.match(ruling.get("decidedAtUtc") or ""):
        failures.append(f"R4: {where}.ruling.decidedAtUtc 不是 ISO-8601")
    return failures


def _check_decided_by(doc: dict) -> list[str]:
    failures: list[str] = []
    for index, entry in enumerate(doc.get("rulings") or []):
        ruling = entry.get("ruling") if isinstance(entry, dict) else None
        decided = ruling.get("decidedBy") if isinstance(ruling, dict) else None
        where = f"rulings[{index}].ruling.decidedBy"
        if not isinstance(decided, dict):
            failures.append(f"R9: {where} 不是对象")
            continue
        kind = decided.get("kind")
        agent = decided.get("agentId")
        if kind not in _DECIDED_BY_KINDS:
            failures.append(f"R9: {where}.kind={kind!r} 不在 {sorted(_DECIDED_BY_KINDS)}")
        if not isinstance(agent, str) or not agent.strip():
            failures.append(f"R9: {where}.agentId 不是非空字符串")
        elif kind == "automated" and not agent.startswith("script:"):
            failures.append(f"R9: {where} 机器裁定必须用 script: 前缀的 agentId，实测 {agent!r}")
        elif kind in ("human", "model") and agent.startswith("script:"):
            failures.append(f"R9: {where} {kind} 裁定不得填脚本身份 {agent!r}")
        if not isinstance(decided.get("ruleId"), str) or not decided["ruleId"].strip():
            failures.append(f"R9: {where}.ruleId 不是非空字符串")
    return failures


def _check_gate(doc: dict) -> list[str]:
    gate = doc.get("gate")
    if not isinstance(gate, dict):
        return ["R7: gate 不是对象"]
    failures: list[str] = []
    if gate.get("state") not in ("PASS", "FAIL"):
        failures.append(f"R7: gate.state={gate.get('state')!r} 不是 PASS/FAIL")
    if not isinstance(gate.get("passed"), bool):
        return failures + ["R7: gate.passed 不是布尔"]
    count = gate.get("count")
    fragment = gate.get("fragment")
    if not isinstance(count, dict) or not _is_number(count.get("value")):
        return failures + ["R7: gate.count.value 不是数字"]
    if not isinstance(fragment, dict) or not _is_number(fragment.get("value")):
        return failures + ["R7: gate.fragment.value 不是数字"]
    thresholds = ((doc.get("inputs") or {}).get("thresholds") or {})
    count_min = (thresholds.get("countExactMin") or {}).get("value")
    fragment_max = (thresholds.get("fragmentMax") or {}).get("value")
    if not _is_number(count_min) or not _is_number(fragment_max):
        return failures + ["R7: inputs.thresholds 缺数值，无法复算 gate.passed"]
    expected = float(count["value"]) >= float(count_min) and \
        float(fragment["value"]) <= float(fragment_max)
    if gate["passed"] != expected:
        failures.append(f"R7: gate.passed={gate['passed']} 与闭边界复算 {expected} 不一致")
    if (gate.get("state") == "PASS") != gate["passed"]:
        failures.append("R7: gate.state 与 gate.passed 不一致")
    return failures


def _check_summary(doc: dict) -> list[str]:
    summary = doc.get("summary")
    rulings = doc.get("rulings")
    if not isinstance(summary, dict):
        return ["R6: summary 不是对象"]
    if not isinstance(rulings, list):
        return ["R6: rulings 不是数组"]
    verdicts = []
    for entry in rulings:
        ruling = entry.get("ruling") if isinstance(entry, dict) else None
        if isinstance(ruling, dict):
            verdicts.append(ruling.get("verdict"))
    expected = {
        "pages": len(rulings),
        "consistent": verdicts.count("CONSISTENT"),
        "inconsistentCount": verdicts.count("INCONSISTENT_COUNT"),
        "fragmented": verdicts.count("FRAGMENTED"),
        "missingInApp": verdicts.count("MISSING_IN_APP"),
        "notEvaluable": verdicts.count("NOT_EVALUABLE"),
    }
    failures = [f"R6: summary.{key}={summary.get(key)!r} != 逐页计数 {value}"
                for key, value in expected.items() if summary.get(key) != value]
    return failures


def _check_hard_red_lines(doc: dict) -> list[str]:
    red = doc.get("hardRedLines")
    rulings = doc.get("rulings")
    if red is None:
        return []
    if not isinstance(red, dict):
        return ["R8: hardRedLines 不是对象"]
    block = red.get("fragmentedQuestions")
    if not isinstance(block, dict):
        return ["R8: hardRedLines.fragmentedQuestions 不是对象"]
    fragmented_questions = sum(
        len(entry["diff"]["fragmentedGoldIndices"])
        for entry in (rulings or [])
        if isinstance(entry, dict)
        and isinstance(entry.get("diff"), dict)
        and isinstance(entry["diff"].get("fragmentedGoldIndices"), list))
    failures: list[str] = []
    if block.get("required") != 0:
        failures.append(f"R8: hardRedLines.fragmentedQuestions.required="
                        f"{block.get('required')!r} != 0（红线）")
    if block.get("value") != fragmented_questions:
        failures.append(f"R8: hardRedLines.fragmentedQuestions.value={block.get('value')!r}"
                        f" != 切残题数 {fragmented_questions}")
    if block.get("satisfied") != (fragmented_questions == 0):
        failures.append("R8: hardRedLines.fragmentedQuestions.satisfied 与切残题数不一致")
    return failures


def validate_document(doc: Any) -> list[str]:
    """跑完 R1–R9，返回全部失败（空 = 通过）。"""
    if not isinstance(doc, dict):
        return [f"R1: 根不是 JSON 对象（{type(doc).__name__}）"]
    failures: list[str] = []
    if doc.get("rulingSchemaVersion") != RULING_SCHEMA_VERSION:
        failures.append(f"R1: rulingSchemaVersion={doc.get('rulingSchemaVersion')!r}"
                        f" != {RULING_SCHEMA_VERSION}")
    failures.extend(_check_run(doc))
    failures.extend(_check_inputs(doc))
    rulings = doc.get("rulings")
    if not isinstance(rulings, list):
        return failures + ["R4: rulings 不是数组"]
    golden = ((doc.get("inputs") or {}).get("golden") or {})
    if _is_int(golden.get("pageCount")) and len(rulings) != golden["pageCount"]:
        failures.append(f"R4: len(rulings)={len(rulings)} != "
                        f"inputs.golden.pageCount={golden['pageCount']}")
    seen: set[str] = set()
    for index, entry in enumerate(rulings):
        failures.extend(_check_ruling(index, entry))
        if isinstance(entry, dict) and isinstance(entry.get("pageId"), str):
            if entry["pageId"] in seen:
                failures.append(f"R4: pageId 重复：{entry['pageId']!r}")
            seen.add(entry["pageId"])
        ruling = entry.get("ruling") if isinstance(entry, dict) else None
        if isinstance(ruling, dict) and ruling.get("evidenceGradeLevel") \
                != golden.get("evidenceGradeLevel"):
            failures.append(f"R4: rulings[{index}].ruling.evidenceGradeLevel="
                            f"{ruling.get('evidenceGradeLevel')!r} != "
                            f"inputs.golden.evidenceGradeLevel="
                            f"{golden.get('evidenceGradeLevel')!r}")
    digest = doc.get("rulingsSha256")
    expected_digest = hashlib.sha256(json.dumps(
        rulings, ensure_ascii=False, sort_keys=True,
        separators=(",", ":")).encode("utf-8")).hexdigest()
    if digest != expected_digest:
        failures.append("R5: rulingsSha256 与 rulings 内容不一致（文件被改过或摘要未同步）")
    failures.extend(_check_gate(doc))
    failures.extend(_check_summary(doc))
    failures.extend(_check_hard_red_lines(doc))
    failures.extend(_check_decided_by(doc))
    return failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="逐页裁定文档（rulings.json）机械校验")
    parser.add_argument("--file", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        doc = json.loads(args.file.read_text(encoding="utf-8"))
    except OSError as failure:
        print(f"裁定校验 FAIL：读取失败 {args.file}（{failure}）")
        return 2
    except ValueError as failure:
        print(f"裁定校验 FAIL：不是合法 JSON（{failure}）")
        return 2
    failures = validate_document(doc)
    if failures:
        print(f"裁定校验 FAIL：{len(failures)} 处不变量失败（{args.file.name}）")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print(f"裁定校验 PASS：{args.file.name}")
    print(f"  · 页数 {doc['summary']['pages']}；判词 "
          + " / ".join(f"{key} {value}" for key, value in sorted(doc["summary"].items())
                       if key != "pages"))
    print(f"  · 门状态 {doc['gate']['state']}；rulingsSha256={doc['rulingsSha256'][:16]}…")
    return 0


if __name__ == "__main__":
    sys.exit(main())
