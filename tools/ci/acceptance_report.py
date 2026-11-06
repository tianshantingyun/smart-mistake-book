#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验收台账报告器：PASS 证据审计（批次 44 / D0 item 3）。

## 它消灭的具体失败

台账里写 `PASS`，证据只有一句 summary、一个日志文件名，或一个 `-4` 跳过轮，
报告工具仍给 exit 0。批次 42 §2.3 的 15 例零执行 + `OK (15 tests)` 就是这么
被记成绿的。本工具把每条 PASS 收敛为可判定条件：

1. ≥2 类**独立**证据（`cmd` / `test` / `device` / `review`；`file`/`hash` 不算）；
2. 所有证据路径必须存在（仓库相对路径）；
3. `cmd` 日志里 exit 必须可读且为 0，且日志不得含 `-4` / AssumptionViolated /
   SKIP 等“跳过门”标记；
4. `test` XML 的 failures/errors 必须为 0，tests 不能为 0；
5. `review` JSON 必须有 `reviewer` + `run_id` + 与当前台账一致的 `ledger_sha256`，
   且 reviewer 不得在 `authors` 里；`verdict` 必须是 PASS/FAIL/BLOCKED/UNVERIFIED；
   批次 46 起还要求 `checks[]` 非空、至少一条 `ledger_row` 命中当前行、至少一条证据
   check 能从磁盘复算，且 `evidence_sha256` 覆盖并匹配台账证据；
6. `summary` / 批次 `record` 类 Markdown 不得作为证据；
7. `CMD:` 行含 `<...>` 占位（类名未展开、不可复跑）的日志不算运行记录；
8. 设备 JSON 自带 `verdict`/`counts` 判词时必须为 PASS 且确有执行；
9. 报告记录被审计树的 HEAD 与 dirty 路径数，便于把证据绑到源快照。

## 批次 46 修复环（findings 1–3）

旧实现只用 `any(e.kind == "review")` 统计 `pass_with_review`，从不打开
`checks[]`，于是“旧 run 复核换绑新台账 sha”“同一套件 check 复制给 7 行”
都能把计数刷到 888。现在 `_check_review` 调用
`acceptance_review.validate_review()` 逐项复算证据字节与可判定字段：

- `checks` 必须非空，且至少有一条 `ledger_row` 命中当前行（id/verdict/line/sha）；
- 至少一条证据 check 能从磁盘重算出相同值（sha256 / XML counts / cmd EXIT /
  device serial 等）；
- `evidence_sha256` 必须覆盖台账证据并逐文件匹配；
- `run_id` 必须带 ISO 时间戳；若 JSON 声明了 `ledger_batch`，`run_id` 里的批次号
  不得早于它（防“旧 batch44 复核换绑 batch46 台账”）。

`pass_with_review` 只数**校验通过**的复核；有 token 但校验失败的进入
`pass_with_invalid_review`，并在 `--require-complete` 下阻断。

## 用法

    python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md
    python -I tools/ci/acceptance_report.py --ledger <fixture> --partial

退出码：0 无违规且无 FAIL 行；1 有违规或 state=FAIL；2 台账不可读/参数错误。
`--require-complete` 额外把 UNVERIFIED/BLOCKED 与无效复核视为阻断。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import acceptance_model as M  # noqa: E402
import acceptance_review as R  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"

EXIT_RE = re.compile(r"(?im)^\s*(?:EXIT(?:_CODE)?|exit(?:_code)?)\s*[:=]\s*(-?\d+)\s*$")
EXIT_BRACKET_RE = re.compile(r"(?i)\[(?:exit|exit_code)\s*[=: ]\s*(-?\d+)\]")
SKIP_MARKER_RE = re.compile(
    r"(?i)(INSTRUMENTATION_STATUS_CODE:\s*-4|AssumptionViolated|"
    r"\bSKIPPED\b|\bSKIP\b|(?:^|\s)-4(?:\s|$)|"
    r"POST[-_ ]?GATE[^\n]{0,80}INVALID|postGate\s*[:=]\s*INVALID)"
)
SUMMARY_RE = re.compile(r"(?i)(summary|摘要|汇总|复盘)")
RECORD_RE = re.compile(r"(?i)(record|记录)")
UNVERIFIED_RE = re.compile(r"(?i)(UNVERIFIED|未验证|NOT EVALUATED|不可判定)")
PLACEHOLDER_RE = re.compile(r"<[^<>\n]{1,120}>")
# 同一组非 ledger_row checks 被 ≥N 行 PASS 共用，就不再具备行级判别力。
REUSE_WARN_THRESHOLD = 5


def _rel(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.as_posix()


def read_exit_code(text: str) -> tuple[int | None, str]:
    matches = EXIT_RE.findall(text) + EXIT_BRACKET_RE.findall(text)
    if not matches:
        return None, "no exit marker"
    unique = {int(value) for value in matches}
    if len(unique) != 1:
        return None, f"ambiguous exit markers {sorted(unique)}"
    return unique.pop(), "ok"


def summary_like(target: str) -> bool:
    name = Path(target).name
    if SUMMARY_RE.search(name):
        return True
    if name.lower().endswith(".md") and RECORD_RE.search(Path(name).stem):
        return True
    return False


def _check_cmd(evidence: M.Evidence, path: Path, row: M.Row, out: list[dict]) -> None:
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        out.append({"id": row.row_id, "code": "EVIDENCE_UNREADABLE", "detail": f"{path}: {exc}"})
        return
    for line in text.splitlines():
        if re.match(r"(?i)^\s*(?:cmd|command)\s*:", line) and PLACEHOLDER_RE.search(line):
            # `CMD: ./gradlew --tests <E-domain classes>` 不是运行记录：类名没展开，
            # 命令不可复跑，不能作为第二类独立证据（批次 44 复核 finding 4）。
            out.append({"id": row.row_id, "code": "PLACEHOLDER_CMD",
                        "detail": f"{evidence.target}: {line.strip()[:120]}"})
            break
    code, reason = read_exit_code(text)
    if code is None:
        out.append({"id": row.row_id, "code": "CMD_EXIT_UNREADABLE",
                    "detail": f"{evidence.target}: {reason}"})
    else:
        attr = evidence.attr("exit")
        if attr is not None:
            try:
                attr_code = int(attr)
            except ValueError:
                attr_code = None
            if attr_code is not None and attr_code != code:
                out.append({"id": row.row_id, "code": "CMD_EXIT_ATTR_MISMATCH",
                            "detail": f"{evidence.target}: attr={attr_code} log={code}"})
        if code != 0:
            out.append({"id": row.row_id, "code": "CMD_EXIT_NONZERO",
                        "detail": f"{evidence.target}: exit={code}"})
    match = SKIP_MARKER_RE.search(text)
    if match:
        out.append({"id": row.row_id, "code": "CMD_LOG_SKIP_MARKER",
                    "detail": f"{evidence.target}: {match.group(0)!r}"})


def _check_test(evidence: M.Evidence, path: Path, row: M.Row, out: list[dict]) -> None:
    if path.suffix.lower() != ".xml":
        return
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        out.append({"id": row.row_id, "code": "TEST_XML_UNREADABLE",
                    "detail": f"{evidence.target}: {exc}"})
        return
    if root.tag != "testsuite":
        suites = root.findall("testsuite")
        if len(suites) != 1:
            out.append({"id": row.row_id, "code": "TEST_XML_AMBIGUOUS",
                        "detail": f"{evidence.target}: {len(suites)} testsuite"})
            return
        root = suites[0]
    try:
        tests = int(root.get("tests", "0"))
        failures = int(root.get("failures", "0"))
        errors = int(root.get("errors", "0"))
        skipped = int(root.get("skipped", "0"))
    except ValueError as exc:
        out.append({"id": row.row_id, "code": "TEST_XML_UNREADABLE",
                    "detail": f"{evidence.target}: {exc}"})
        return
    if tests <= 0:
        out.append({"id": row.row_id, "code": "TEST_XML_NO_TESTS",
                    "detail": f"{evidence.target}: tests={tests}"})
    if failures or errors:
        out.append({"id": row.row_id, "code": "TEST_XML_FAILURES",
                    "detail": f"{evidence.target}: failures={failures} errors={errors}"})
    if tests > 0 and skipped >= tests:
        out.append({"id": row.row_id, "code": "TEST_XML_ALL_SKIPPED",
                    "detail": f"{evidence.target}: skipped={skipped}/{tests}"})


def _batch_of(text: str | None) -> int | None:
    match = re.search(r"batch[-_]?(\d+)", str(text or ""), re.I)
    return int(match.group(1)) if match else None


RUN_TS_RE = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:Z|[+-]\d{2}:\d{2})")


def _check_review(evidence: M.Evidence, path: Path, row: M.Row, out: list[dict],
                  ledger_sha: str | None = None, root: Path | None = None,
                  row_dirs: list[Path] | None = None,
                  index: dict[str, list[Path]] | None = None,
                  context: dict | None = None) -> dict:
    """复核 JSON 的字段 + 内容校验。返回 {valid, fingerprint, run_id, errors}。"""
    result = {"valid": False, "fingerprint": None, "run_id": None, "errors": []}
    start = len(out)
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        out.append({"id": row.row_id, "code": "REVIEW_INVALID",
                    "detail": f"{evidence.target}: {exc}"})
        return result
    if not isinstance(data, dict):
        out.append({"id": row.row_id, "code": "REVIEW_INVALID",
                    "detail": f"{evidence.target}: not an object"})
        return result
    for key in ("reviewer", "run_id", "ledger_sha256"):
        if not data.get(key):
            out.append({"id": row.row_id, "code": "REVIEW_MISSING_FIELD",
                        "detail": f"{evidence.target}: 缺 {key}"})
    verdict = data.get("verdict")
    # 批次 45 复核 finding 5：domain-review 曾用 SAMPLED_REVIEW / PASS_WORKTREE /
    # PASS_WITH_REVIEW 等自造判词，报告只查字段不查枚举。复核判词必须落在
    # 台账同一套 verdict 上，细粒度说明放到 verdict_detail / scope 字段。
    if not isinstance(verdict, str) or verdict.strip().upper() not in M.VERDICTS:
        out.append({"id": row.row_id, "code": "REVIEW_VERDICT_INVALID",
                    "detail": f"{evidence.target}: verdict={verdict!r}"})
    if isinstance(verdict, str) and verdict.strip().upper() != row.verdict:
        out.append({"id": row.row_id, "code": "REVIEW_VERDICT_MISMATCH",
                    "detail": f"{evidence.target}: review={verdict!r} ledger={row.verdict}"})
    declared_sha = data.get("ledger_sha256")
    if declared_sha and not re.fullmatch(r"[0-9a-fA-F]{64}", str(declared_sha)):
        out.append({"id": row.row_id, "code": "REVIEW_INVALID",
                    "detail": f"{evidence.target}: ledger_sha256 不是 64 hex"})
    if declared_sha and ledger_sha and str(declared_sha).lower() != ledger_sha.lower():
        # 复核必须绑定“这一版”台账；否则把旧 run 的复核 JSON 挪到新台账即可伪造。
        out.append({"id": row.row_id, "code": "REVIEW_LEDGER_SHA_MISMATCH",
                    "detail": f"{evidence.target}: declared={declared_sha} actual={ledger_sha}"})
    reviewer = data.get("reviewer")
    authors = data.get("authors") or []
    if reviewer and isinstance(authors, list) and reviewer in authors:
        out.append({"id": row.row_id, "code": "REVIEW_SELF",
                    "detail": f"{evidence.target}: reviewer 在 authors 内"})
    if data.get("self_review") is True:
        out.append({"id": row.row_id, "code": "REVIEW_SELF",
                    "detail": f"{evidence.target}: self_review=true"})

    # 批次 46 修复 finding 1：run_id 自带批次号不得早于复核声明的台账批次。
    # 旧复核换绑新 sha 的典型形态是 run_id 仍为 batch44、ledger_sha 却是 batch46。
    run_id = data.get("run_id")
    result["run_id"] = str(run_id) if run_id else None
    if run_id and not RUN_TS_RE.search(str(run_id)):
        out.append({"id": row.row_id, "code": "REVIEW_RUN_ID_FORMAT",
                    "detail": f"{evidence.target}: run_id 缺 ISO 时间戳: {run_id!r}"})
    ledger_batch = data.get("ledger_batch")
    run_batch = _batch_of(str(run_id) if run_id else "")
    if ledger_batch is not None and run_batch is not None:
        try:
            if int(ledger_batch) > run_batch:
                out.append({"id": row.row_id, "code": "REVIEW_RUN_PREDATES_LEDGER",
                            "detail": f"{evidence.target}: run batch={run_batch} < "
                                      f"ledger batch={ledger_batch}"})
        except (TypeError, ValueError):
            out.append({"id": row.row_id, "code": "REVIEW_INVALID",
                        "detail": f"{evidence.target}: ledger_batch={ledger_batch!r}"})

    checks = data.get("checks")
    if not isinstance(checks, list) or not checks:
        out.append({"id": row.row_id, "code": "REVIEW_MISSING_CHECKS",
                    "detail": f"{evidence.target}: checks 为空"})
    else:
        # 去掉 ledger_row 后的 check 指纹：行级判据应随行变化。逐字节相同的
        # 指纹意味着这份“行级复核”只是同一套件结论的复制（finding 3）。
        payload = [json.dumps(c, ensure_ascii=False, sort_keys=True)
                   for c in checks
                   if isinstance(c, dict)
                   and (c.get("name") or c.get("check")) != "ledger_row"]
        result["fingerprint"] = hashlib.sha256("\n".join(payload).encode("utf-8")).hexdigest()

    root = root or Path(".")
    validated = R.validate_review(
        data, row, ledger_sha or "", root, list(row_dirs or []), index, context or {})
    for error in validated["errors"]:
        out.append({"id": row.row_id, "code": "REVIEW_CHECK_FAILED",
                    "detail": f"{evidence.target}: {error}"})
    result["errors"] = validated["errors"]
    result["valid"] = (len(out) == start) and not validated["errors"]
    return result


def _check_device(evidence: M.Evidence, path: Path, row: M.Row, out: list[dict]) -> None:
    if path.suffix.lower() != ".json":
        return
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        out.append({"id": row.row_id, "code": "DEVICE_EVIDENCE_UNREADABLE",
                    "detail": f"{evidence.target}: {exc}"})
        return
    if not isinstance(data, dict):
        out.append({"id": row.row_id, "code": "DEVICE_EVIDENCE_UNBOUND",
                    "detail": f"{evidence.target}: not an object"})
        return
    if not data.get("serial"):
        out.append({"id": row.row_id, "code": "DEVICE_EVIDENCE_UNBOUND",
                    "detail": f"{evidence.target}: 缺 serial"})
    if not (data.get("apk_sha256") or data.get("apk_sha")):
        out.append({"id": row.row_id, "code": "DEVICE_EVIDENCE_UNBOUND",
                    "detail": f"{evidence.target}: 缺 apk_sha256"})
    # 设备 JSON 自带判词时以它为准：真实 FAIL / 零执行不能被 cmd 的 EXIT=0 洗白。
    verdict = data.get("verdict")
    if isinstance(verdict, str) and verdict.strip().upper() not in {"", "PASS"}:
        out.append({"id": row.row_id, "code": "DEVICE_EVIDENCE_VERDICT",
                    "detail": f"{evidence.target}: verdict={verdict!r}"})
    counts = data.get("counts") if isinstance(data.get("counts"), dict) else {}
    for key in ("failures", "errors"):
        value = counts.get(key)
        if isinstance(value, int) and value > 0:
            out.append({"id": row.row_id, "code": "DEVICE_EVIDENCE_FAILURES",
                        "detail": f"{evidence.target}: counts.{key}={value}"})
    if data.get("failed") is True:
        out.append({"id": row.row_id, "code": "DEVICE_EVIDENCE_FAILURES",
                    "detail": f"{evidence.target}: failed=true"})
    evaluated = counts.get("evaluated")
    if isinstance(evaluated, int) and evaluated == 0:
        out.append({"id": row.row_id, "code": "DEVICE_EVIDENCE_NOT_EVALUATED",
                    "detail": f"{evidence.target}: counts.evaluated=0"})


def _git_binding(root: Path) -> tuple[str | None, int | None]:
    """记录被审计树的源快照：HEAD 与 dirty 路径数。非 git 树返回 (None, None)。"""
    try:
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=str(root),
            capture_output=True, text=True, timeout=15,
        )
        if head.returncode != 0:
            return None, None
        status = subprocess.run(
            ["git", "status", "--porcelain", "-uall"], cwd=str(root),
            capture_output=True, text=True, timeout=60,
        )
        dirty = None
        if status.returncode == 0:
            dirty = sum(1 for line in status.stdout.splitlines() if line.strip())
        return head.stdout.strip(), dirty
    except (OSError, subprocess.SubprocessError):
        return None, None


def evaluate(ledger: str | Path, repo_root: str | Path, partial: bool = False) -> dict:
    ledger_path = Path(ledger)
    ledger_sha = hashlib.sha256(ledger_path.read_bytes()).hexdigest()
    rows, issues = M.parse_ledger(ledger_path)
    violations: list[dict] = [
        {"id": "", "code": issue["code"], "detail": f"line {issue.get('line', 0)}: {issue['detail']}"}
        for issue in issues
    ]
    identity = M.identity_issues(rows)
    if partial:
        identity = [i for i in identity
                    if i["code"] not in {"MISSING_ROW", "TOTAL_COUNT", "DOMAIN_COUNT"}]
    violations.extend({"id": "", "code": i["code"], "detail": i["detail"]} for i in identity)

    warnings: list[dict] = []
    root = Path(repo_root)
    index = R.basename_index(root)
    counts = {verdict: 0 for verdict in M.VERDICTS}
    for row in rows:
        counts[row.verdict] = counts.get(row.verdict, 0) + 1
    by_id = {row.row_id: row for row in rows}

    # direct_recount 类 check 声明的是全局计数；先在循环前算好，复核时可比对。
    review_dir = root / "docs" / "acceptance" / "verdicts" / "rows"
    row_json_count = len(list(review_dir.glob("*.json"))) if review_dir.is_dir() else 0
    pass_missing_json = 0
    for row in rows:
        if row.verdict != "PASS":
            continue
        reviews = [e for e in row.evidence() if e.kind == "review"]
        if not reviews or any(not M.resolve(root, e.target).exists() for e in reviews):
            pass_missing_json += 1
    context = {
        "pass_count": counts.get("PASS", 0),
        "row_json_count": row_json_count,
        "pass_missing_json": pass_missing_json,
    }

    review_states: dict[str, list[dict]] = {}
    for row in rows:
        if row.verdict != "PASS":
            for evidence in row.evidence():
                if not M.resolve(root, evidence.target).exists():
                    warnings.append({"id": row.row_id, "code": "NON_PASS_EVIDENCE_MISSING",
                                     "detail": f"{evidence.kind}:{evidence.target}"})
            continue

        evidence_items = row.evidence()
        if not evidence_items:
            violations.append({"id": row.row_id, "code": "PASS_EMPTY_EVIDENCE",
                               "detail": "PASS 行没有任何证据"})
            continue
        independent = {e.kind for e in evidence_items if e.kind in M.INDEPENDENT_KINDS}
        if len(independent) < 2:
            violations.append({
                "id": row.row_id, "code": "PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE",
                "detail": f"独立证据类={sorted(independent)}（file/hash 不计）",
            })
        if any(summary_like(e.target) for e in evidence_items):
            violations.append({"id": row.row_id, "code": "SUMMARY_AS_EVIDENCE",
                               "detail": "summary/批次 record 不得作为证据"})
        if "<" in row.method and ">" in row.method:
            violations.append({"id": row.row_id, "code": "PLACEHOLDER_METHOD",
                               "detail": row.method})

        row_dirs = [M.resolve(root, e.target).parent for e in evidence_items]
        states: list[dict] = []
        for evidence in evidence_items:
            path = M.resolve(root, evidence.target)
            if not path.exists():
                violations.append({"id": row.row_id, "code": "EVIDENCE_PATH_MISSING",
                                   "detail": f"{evidence.kind}:{evidence.target}"})
                continue
            if evidence.kind == "cmd":
                _check_cmd(evidence, path, row, violations)
            elif evidence.kind == "test":
                _check_test(evidence, path, row, violations)
            elif evidence.kind == "review":
                states.append(_check_review(evidence, path, row, violations, ledger_sha,
                                            root, row_dirs, index, context))
            elif evidence.kind == "device":
                _check_device(evidence, path, row, violations)
            # file/hash 只要求存在；sha256 校验由证据哈希清单负责。
        review_states[row.row_id] = states

    valid_review_ids = {
        row_id for row_id, states in review_states.items()
        if states and all(state["valid"] for state in states)
    }
    token_review_ids = set(review_states)
    invalid_review_ids = token_review_ids - valid_review_ids
    fingerprints = Counter(
        state["fingerprint"]
        for row_id in sorted(valid_review_ids)
        for state in review_states[row_id]
        if state["fingerprint"]
    )
    shared_groups = {fingerprint: count for fingerprint, count in fingerprints.items()
                     if count >= REUSE_WARN_THRESHOLD}
    for fingerprint, count in sorted(shared_groups.items(),
                                     key=lambda item: (-item[1], item[0])):
        warnings.append({
            "id": "", "code": "REVIEW_CHECKS_SHARED",
            "detail": f"同一组非 ledger_row checks 被 {count} 行 PASS 共用 "
                      f"(fp={fingerprint[:12]})；行级判别力不成立",
        })
    row_specific_ids = {
        row_id for row_id in valid_review_ids
        if all(state["fingerprint"] and fingerprints[state["fingerprint"]] == 1
               for state in review_states[row_id])
    }
    evidence_batches: Counter[str] = Counter()
    for row_id in valid_review_ids:
        for evidence in by_id[row_id].evidence():
            if evidence.kind == "review":
                continue
            match = re.search(r"batch[-_]?(\d+)", evidence.target, re.I)
            evidence_batches[match.group(1) if match else "unlabeled"] += 1
    review_errors = [
        {"id": row_id, "errors": state["errors"]}
        for row_id in sorted(invalid_review_ids)
        for state in review_states[row_id]
        if state["errors"]
    ]

    unverified = counts.get("UNVERIFIED", 0)
    failed = counts.get("FAIL", 0)
    blocked = counts.get("BLOCKED", 0)
    acceptance_complete = unverified == 0 and failed == 0 and blocked == 0
    if violations or failed:
        state = "FAIL"
    elif acceptance_complete:
        state = "PASS"
    elif blocked:
        state = "BLOCKED"
    else:
        state = "UNVERIFIED"
    head, dirty_paths = _git_binding(root)
    exit_code = 1 if (violations or state == "FAIL") else 0
    return {
        "ledger": _rel(ledger_path, root),
        "ledger_sha256": ledger_sha,
        "head": head,
        "dirty_paths": dirty_paths,
        "total": len(rows),
        "counts": counts,
        "state": state,
        "pass_with_two_kinds": sum(
            1 for row in rows
            if row.verdict == "PASS" and len(row.independent_kinds()) >= 2
        ),
        # DoD §1.5 把独立复核列为 PASS 前提。批次 46 修复：只有逐项复算通过、
        # 且 checks 非空、绑当前台账 sha 的复核才计入；token 存在但校验失败
        # 的单独计数，不再被 888 这个数字掩盖。
        "pass_with_review": len(valid_review_ids),
        "pass_without_review": sum(
            1 for row in rows
            if row.verdict == "PASS" and not any(e.kind == "review" for e in row.evidence())
        ),
        "pass_with_invalid_review": len(invalid_review_ids),
        "pass_with_row_specific_review": len(row_specific_ids),
        "review_shared_check_groups": [
            {"fingerprint": fingerprint, "rows": count}
            for fingerprint, count in sorted(shared_groups.items(),
                                             key=lambda item: (-item[1], item[0]))
        ],
        "review_evidence_batches": dict(sorted(evidence_batches.items())),
        "review_validation_errors": review_errors,
        "unverified": unverified,
        "acceptance_complete": acceptance_complete,
        "violations": violations,
        "warnings": warnings,
        "ok": not violations and state != "FAIL",
        "exit_code": exit_code,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="验收台账 PASS 证据审计")
    parser.add_argument("--ledger", default=str(DEFAULT_LEDGER))
    parser.add_argument("--repo-root", default=str(ROOT))
    parser.add_argument("--out", help="写入 JSON 报告")
    parser.add_argument("--partial", action="store_true",
                        help="测试用：不要求 1200 行完整 ID 集合")
    parser.add_argument("--require-complete", action="store_true",
                        help="终裁门：仍有 UNVERIFIED/FAIL/BLOCKED 或无效复核时 exit 1")
    args = parser.parse_args(argv)

    ledger = Path(args.ledger)
    try:
        report = evaluate(ledger, args.repo_root, partial=args.partial)
    except M.LedgerError as exc:
        print(f"LEDGER_ERROR: {exc}", file=sys.stderr)
        return 2
    if args.require_complete and not report["acceptance_complete"]:
        report["violations"].append({
            "id": "", "code": "NOT_ACCEPTANCE_COMPLETE",
            "detail": f"UNVERIFIED={report['counts'].get('UNVERIFIED', 0)} "
                      f"FAIL={report['counts'].get('FAIL', 0)} "
                      f"BLOCKED={report['counts'].get('BLOCKED', 0)}",
        })
        report["ok"] = False
        report["exit_code"] = 1
    if args.require_complete and report["pass_without_review"]:
        # DoD §1.5：没有独立复核的 PASS 不能进入终裁（findings 1/21）。
        report["violations"].append({
            "id": "", "code": "PASS_WITHOUT_REVIEW",
            "detail": f"{report['pass_without_review']} 个 PASS 行没有 review 证据",
        })
        report["ok"] = False
        report["exit_code"] = 1
    if args.require_complete and report["pass_with_invalid_review"]:
        # 批次 46 修复 finding 2：review 文件存在不再等于复核成立。
        report["violations"].append({
            "id": "", "code": "PASS_REVIEW_INVALID",
            "detail": f"{report['pass_with_invalid_review']} 个 PASS 行的 review "
                      f"未通过逐项复算",
        })
        report["ok"] = False
        report["exit_code"] = 1
    if args.require_complete and report["review_shared_check_groups"]:
        # 批次 46 修复 finding 3：同一组 checks 复制给多行，只证明套件跑过。
        report["violations"].append({
            "id": "", "code": "REVIEW_CHECKS_SHARED",
            "detail": f"{len(report['review_shared_check_groups'])} 组非 ledger_row "
                      f"checks 被 ≥{REUSE_WARN_THRESHOLD} 行共用，行级判别力不成立",
        })
        report["ok"] = False
        report["exit_code"] = 1
    payload = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.out:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(payload, encoding="utf-8", newline="\n")
    print(payload)
    return report["exit_code"]


if __name__ == "__main__":
    sys.exit(main())
