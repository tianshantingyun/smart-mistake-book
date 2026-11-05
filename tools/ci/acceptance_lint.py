#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验收台账反作弊 lint（批次 44 / D0 item 4、item 15）。

## 它消灭的具体失败

- **summary 当证据**：批次 41–43 的多份 `*-record.md` 被引用成“已验收”；
- **UNVERIFIED 当 PASS**：设备/Provider 未跑，行里还写 PASS；
- **跳过的门计通过**：批次 42 §2.3 的 15 例全 `-4`、零执行，JUnit 打印
  `OK (15 tests)`，被记成绿；
- **自证复核**：reviewer 就是作者。

## 规则

| code | 触发 |
|---|---|
| `SUMMARY_AS_EVIDENCE` | 证据文件名含 summary/摘要/汇总，或 `.md` 文件名含 record/记录 |
| `UNVERIFIED_AS_PASS` | PASS 行方法/证据/证据内容出现 UNVERIFIED/未验证/NOT EVALUATED |
| `SKIPPED_GATE_AS_PASS` | PASS 的 cmd 日志含 -4/AssumptionViolated/SKIP，或 exit≠0；test XML skipped>0/全跳过 |
| `PASS_EMPTY_EVIDENCE` | PASS 没有任何证据 |
| `PLACEHOLDER_METHOD` | PASS 方法里还有 `<...>` |
| `SELF_REVIEW` | review JSON 的 reviewer 在 authors 内或 self_review=true |
| `DUPLICATE_ID` | 同 ID 出现多次 |

退出码：0 无违规；1 有违规；2 台账不可读。
"""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import acceptance_model as M  # noqa: E402
from acceptance_report import (  # noqa: E402
    SKIP_MARKER_RE,
    UNVERIFIED_RE,
    read_exit_code,
    summary_like,
)

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"


def _file_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def _lint_test_xml(path: Path, row: M.Row, out: list[dict]) -> None:
    if path.suffix.lower() != ".xml":
        return
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return
    suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
    for suite in suites:
        try:
            tests = int(suite.get("tests", "0"))
            skipped = int(suite.get("skipped", "0"))
        except ValueError:
            continue
        if skipped > 0:
            out.append({"id": row.row_id, "code": "SKIPPED_GATE_AS_PASS",
                        "detail": f"{path.name}: skipped={skipped}/{tests}"})


def lint(ledger: str | Path, repo_root: str | Path, partial: bool = False,
         strict_warnings: bool = False) -> dict:
    ledger_path = Path(ledger)
    rows, issues = M.parse_ledger(ledger_path)
    violations: list[dict] = [
        {"id": "", "code": issue["code"], "detail": f"line {issue.get('line', 0)}: {issue['detail']}"}
        for issue in issues
    ]
    warnings: list[dict] = []
    if not partial:
        violations.extend({"id": "", "code": i["code"], "detail": i["detail"]}
                          for i in M.identity_issues(rows))

    root = Path(repo_root)
    reuse: Counter[str] = Counter()
    for row in rows:
        items = row.evidence()
        if any(summary_like(e.target) for e in items):
            violations.append({"id": row.row_id, "code": "SUMMARY_AS_EVIDENCE",
                               "detail": "summary/批次记录不能当证据"})
        if row.verdict != "PASS":
            continue
        if not items:
            violations.append({"id": row.row_id, "code": "PASS_EMPTY_EVIDENCE",
                               "detail": "PASS 行没有证据"})
            continue
        if "<" in row.method and ">" in row.method:
            violations.append({"id": row.row_id, "code": "PLACEHOLDER_METHOD",
                               "detail": row.method})
        if UNVERIFIED_RE.search(row.method) or UNVERIFIED_RE.search(row.evidence_text):
            violations.append({"id": row.row_id, "code": "UNVERIFIED_AS_PASS",
                               "detail": "方法/证据文本含 UNVERIFIED"})
        for evidence in items:
            reuse[evidence.target] += 1
            path = M.resolve(root, evidence.target)
            if not path.exists():
                continue
            text = _file_text(path) if path.suffix.lower() in {".md", ".txt", ".log", ".json"} else ""
            if UNVERIFIED_RE.search(text):
                violations.append({"id": row.row_id, "code": "UNVERIFIED_AS_PASS",
                                   "detail": f"{evidence.target} 内容含 UNVERIFIED"})
            if evidence.kind == "cmd":
                code, _ = read_exit_code(text)
                if code is not None and code != 0:
                    violations.append({"id": row.row_id, "code": "SKIPPED_GATE_AS_PASS",
                                       "detail": f"{evidence.target}: exit={code}"})
                match = SKIP_MARKER_RE.search(text)
                if match:
                    violations.append({"id": row.row_id, "code": "SKIPPED_GATE_AS_PASS",
                                       "detail": f"{evidence.target}: {match.group(0)!r}"})
            elif evidence.kind == "test":
                _lint_test_xml(path, row, violations)
            elif evidence.kind == "review":
                try:
                    data = json.loads(text)
                except json.JSONDecodeError:
                    data = {}
                reviewer = data.get("reviewer") if isinstance(data, dict) else None
                authors = data.get("authors") if isinstance(data, dict) else None
                if isinstance(data, dict) and (
                    data.get("self_review") is True
                    or (reviewer and isinstance(authors, list) and reviewer in authors)
                ):
                    violations.append({"id": row.row_id, "code": "SELF_REVIEW",
                                       "detail": f"{evidence.target}: reviewer 自证"})
    for target, count in reuse.items():
        if count > 10:
            warnings.append({"id": "", "code": "EVIDENCE_REUSED",
                             "detail": f"{target} 被 {count} 行 PASS 共用，需确认独立性"})
    if strict_warnings:
        # 同一证据被大量 PASS 行共用时，“2 类独立证据”只是名义成立；
        # CI 用严格模式把这条告警升级为门禁失败（批次 44 复核 finding 14）。
        violations.extend({"id": item["id"], "code": item["code"],
                           "detail": item["detail"]} for item in warnings)

    return {
        "ledger": ledger_path.as_posix(),
        "total": len(rows),
        "violations": violations,
        "warnings": warnings,
        "ok": not violations,
        "exit_code": 0 if not violations else 1,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="验收台账反作弊 lint")
    parser.add_argument("--ledger", default=str(DEFAULT_LEDGER))
    parser.add_argument("--repo-root", default=str(ROOT))
    parser.add_argument("--out")
    parser.add_argument("--partial", action="store_true")
    parser.add_argument("--strict-warnings", action="store_true",
                        help="把 EVIDENCE_REUSED 等告警升级为违规（CI 用）")
    args = parser.parse_args(argv)
    try:
        report = lint(args.ledger, args.repo_root, partial=args.partial,
                      strict_warnings=args.strict_warnings)
    except M.LedgerError as exc:
        print(f"LEDGER_ERROR: {exc}", file=sys.stderr)
        return 2
    payload = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.out:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(payload, encoding="utf-8", newline="\n")
    print(payload)
    return report["exit_code"]


if __name__ == "__main__":
    sys.exit(main())
