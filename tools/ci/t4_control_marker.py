#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T4 控制轮可核标记（批次 41 / WS4 item 27）。

## 它消灭的具体失败

批次 40 的隔离结论卡在"T4 关联机制上不可能，但无法做去 T4 控制轮"：
`step04-isolation.md` 的 I50-6/I50-7 是"6 处写点回退、文件级备份/还原、sha 校验"，
但没有任何**机器可核**的产物——构建日志里没有标记、控制臂没有引擎 sha，谁也
无法从证据判断"这次构建到底含不含 T4 打点"。批次 39 只能写"机制反证"。

本工具只做**可核标记**，不执行回退（回退是 `app/src/main/.../VisualJsClientEngine.kt`
的临时改动，属落码轨/主会话授权范围）：

1. `emit --build-log <log>`：读当前引擎源码，记录
   - `engine_sha256`（整文件）；
   - 6 处打点行的行号 + 行级 sha256（5 个语义写点，`readyAt` 两处成功出口）；
   - 构建日志是否含 `T4_REVERT_APPLIED`；
   - `arm`: `ON_PRODUCTION_T4_PRESENT`（6 处打点在场）或
     `OFF_PRODUCTION_T4_REMOVED`（0 处且日志声明已回退）。
2. `verify --marker <json>`：重算当前树，引擎 sha 或行级 sha 漂移即失败。

## fail-closed

- 日志声明 `T4_REVERT_APPLIED` 但源码仍有打点行 → **无效**（exit 1）；
- 没有声明回退但打点数 ≠ 6 → **无效**（既不是完整 ON 也不是 OFF）；
- 缺构建日志/缺引擎文件 → exit 2（没有"通过"可言）。

## 用法

    python -I tools/ci/t4_control_marker.py emit \
        --build-log build/control/build.log --out build/control/t4.marker.json
    python -I tools/ci/t4_control_marker.py verify \
        --marker build/control/t4.marker.json

配合 `tools/ci/run_isolation_pair.sh --off-arm-production-t4`：只有 `verify` 通过、
且标记 `arm=OFF_PRODUCTION_T4_REMOVED` 时，配对报告才允许讨论 T4 因果。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ENGINE_PATH = "app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt"
REVERT_MARKER = "T4_REVERT_APPLIED"
EXPECTED_ANCHORS = 6
ANCHOR_NEEDLES = (
    "timestamps.bindRequestedAtMillis =",
    "timestamps.bindConnectedAtMillis =",
    "timestamps.readyAtMillis =",
    "timestamps.executeAcceptedAtMillis =",
    "timestamps.firstByteAtMillis =",
)
EXIT_OK = 0
EXIT_INVALID = 1
EXIT_INPUT = 2


def _sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _sha256_text(text: str) -> str:
    return _sha256_bytes(text.encode("utf-8"))


def find_anchors(source: str) -> list[dict]:
    """定位 T4 打点行；返回行号（1-based）与行级 sha（去行尾空白后）。"""
    anchors = []
    for number, line in enumerate(source.splitlines(), start=1):
        stripped = line.strip()
        if any(needle in stripped for needle in ANCHOR_NEEDLES):
            anchors.append({
                "line": number,
                "anchor": next(needle for needle in ANCHOR_NEEDLES if needle in stripped),
                "line_sha256": _sha256_text(stripped),
            })
    return anchors


def build_marker(repo: Path, build_log: str) -> dict:
    engine = repo / ENGINE_PATH
    if not engine.is_file():
        return {"ok": False, "reasons": ["engine_missing"]}
    raw = engine.read_bytes()
    source = raw.decode("utf-8", errors="replace")
    anchors = find_anchors(source)
    claims_reverted = REVERT_MARKER in build_log

    reasons: list[str] = []
    if claims_reverted and anchors:
        reasons.append("marker_claims_removed_but_anchors_present")
    if not claims_reverted and len(anchors) != EXPECTED_ANCHORS:
        reasons.append("anchor_count")
    if claims_reverted and anchors:
        arm = "INVALID"
    elif claims_reverted:
        arm = "OFF_PRODUCTION_T4_REMOVED"
    else:
        arm = "ON_PRODUCTION_T4_PRESENT"

    return {
        "ok": not reasons,
        "reasons": reasons,
        "arm": arm,
        "engine_path": ENGINE_PATH,
        "engine_sha256": _sha256_bytes(raw),
        "anchor_count": len(anchors),
        "expected_anchor_count": EXPECTED_ANCHORS,
        "anchors": anchors,
        "revert_marker_seen": claims_reverted,
        "build_log_sha256": _sha256_text(build_log),
        "generated_at_epoch": time.time(),
    }


def verify_marker(repo: Path, marker: dict) -> dict:
    reasons: list[str] = []
    current = build_marker(repo, REVERT_MARKER if marker.get("revert_marker_seen") else "")
    if not current.get("ok") and marker.get("arm") != "OFF_PRODUCTION_T4_REMOVED":
        reasons.extend(current.get("reasons", []))
    if current.get("engine_sha256") != marker.get("engine_sha256"):
        reasons.append("engine_sha_drift")
    if marker.get("arm") == "OFF_PRODUCTION_T4_REMOVED":
        if current.get("anchor_count") != 0:
            reasons.append("off_arm_anchors_present")
    elif marker.get("arm") == "ON_PRODUCTION_T4_PRESENT":
        if current.get("anchor_count") != EXPECTED_ANCHORS:
            reasons.append("on_arm_anchor_count_drift")
        current_lines = {(item["line"], item["line_sha256"]) for item in current.get("anchors", [])}
        marker_lines = {(item["line"], item["line_sha256"]) for item in marker.get("anchors", [])}
        if current_lines != marker_lines:
            reasons.append("anchor_line_drift")
    else:
        reasons.append("marker_arm_unrecognized")

    return {
        "ok": not reasons,
        "reasons": reasons,
        "arm": marker.get("arm"),
        "engine_sha256": current.get("engine_sha256"),
        "anchor_count": current.get("anchor_count"),
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="auditable T4 control-round marker")
    sub = parser.add_subparsers(dest="command", required=True)

    emit = sub.add_parser("emit", help="record the current engine state against a build log")
    emit.add_argument("--repo", default=str(REPO))
    emit.add_argument("--build-log", required=True)
    emit.add_argument("--out", required=True)

    verify = sub.add_parser("verify", help="re-check a marker against the current tree")
    verify.add_argument("--repo", default=str(REPO))
    verify.add_argument("--marker", required=True)

    args = parser.parse_args(argv)
    repo = Path(args.repo)

    if args.command == "emit":
        try:
            build_log = Path(args.build_log).read_text(encoding="utf-8", errors="replace")
        except OSError as failure:
            print(f"t4_control_marker: cannot read build log: {failure}", file=sys.stderr)
            return EXIT_INPUT
        marker = build_marker(repo, build_log)
        if marker.get("reasons") == ["engine_missing"]:
            print("t4_control_marker: engine source missing", file=sys.stderr)
            return EXIT_INPUT
        target = Path(args.out)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(marker, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(marker, ensure_ascii=False))
        return EXIT_OK if marker["ok"] else EXIT_INVALID

    try:
        marker = json.loads(Path(args.marker).read_text(encoding="utf-8"))
    except (OSError, ValueError) as failure:
        print(f"t4_control_marker: cannot read marker: {failure}", file=sys.stderr)
        return EXIT_INPUT
    result = verify_marker(repo, marker)
    print(json.dumps(result, ensure_ascii=False))
    return EXIT_OK if result["ok"] else EXIT_INVALID


if __name__ == "__main__":
    sys.exit(main())
