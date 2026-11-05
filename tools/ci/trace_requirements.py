#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""需求→测试→证据可追溯（批次 44 / D0 item 6）。

## 它消灭的具体失败

台账行写了 `test: no/such/Test.kt`、`<JourneyTest>` 或只有一句 command，
却仍被当作“已有落点”。本工具逐行抽取落点（命令里的工具/测试路径、`test:`、
`device:`、`review:`、`file:`），输出缺口清单：

- `PLACEHOLDER`：方法里还有 `<...>`；
- `MISSING_TEST_PATH`：`pytest`/`test:` 指向的文件不存在；
- `MISSING_TOOL_PATH`：`python -I`/`python` 指向的脚本不存在；
- `MISSING_EVIDENCE_PATH`：`review:`/`file:`/`hash:` 指向的文件不存在；
- `DEVICE_EVIDENCE_MISSING`：方法声明设备执行，但证据里没有 `device:`；
- `PASS_WITHOUT_TEST_LANDING`：PASS 行没有 test/device 落点；
- `PASS_WITH_UNVERIFIED_METHOD`：PASS 行方法以 `UNVERIFIED:` 开头；
- `BLOCKED_WITHOUT_RUNBOOK`：BLOCKED 行没有指向 runbook 出口；
- `NO_LANDING_POINT`：方法为空。

退出码：0 无缺口；1 有缺口；2 台账不可读。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import acceptance_model as M  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"

PLACEHOLDER_RE = re.compile(r"<[^<>]{1,120}>")
PY_RE = re.compile(r"python(?:\s+-I)?\s+([^\s;]+)")
PYTEST_RE = re.compile(r"pytest\s+([^\s;]+)")
KIND_PATH_RE = re.compile(r"^(test|review|file|hash):\s*(\S+)$")


def _landings(method: str) -> tuple[list[tuple[str, str | None]], list[str]]:
    """返回 ([(kind,target)], placeholders)。kind ∈ test/tool/device/gradlew。"""
    landings: list[tuple[str, str | None]] = []
    placeholders = PLACEHOLDER_RE.findall(method)
    for chunk in method.split(";"):
        chunk = chunk.strip()
        if not chunk:
            continue
        if chunk.startswith(("BLOCKED:", "UNVERIFIED:")):
            continue
        kind_match = KIND_PATH_RE.match(chunk)
        if kind_match:
            kind, target = kind_match.groups()
            # `path#method` 的 # 后面是测试方法锚点，不属于文件路径。
            target = target.split("#", 1)[0]
            landings.append(("test" if kind == "test" else "path", target))
            continue
        if chunk.startswith("device:"):
            landings.append(("device", None))
            continue
        if chunk.startswith("cmd:"):
            command = chunk[4:].strip()
        else:
            command = chunk
        if "./gradlew" in command or "gradlew " in command:
            landings.append(("gradlew", None))
        for target in PYTEST_RE.findall(command):
            if target.startswith("-"):
                continue
            landings.append(("test", target))
        for target in PY_RE.findall(command):
            if target.startswith("-"):
                # `python -I -m pytest <path>`：`-m` 是解释器选项，不是脚本路径。
                continue
            landings.append(("tool", target))
    return landings, placeholders


def trace(ledger: str | Path, repo_root: str | Path, partial: bool = False) -> dict:
    ledger_path = Path(ledger)
    rows, issues = M.parse_ledger(ledger_path)
    root = Path(repo_root)
    gaps: list[dict] = [
        {"id": "", "code": issue["code"], "detail": f"line {issue.get('line', 0)}: {issue['detail']}"}
        for issue in issues
    ]
    if not partial:
        gaps.extend({"id": "", "code": i["code"], "detail": i["detail"]}
                    for i in M.identity_issues(rows))

    traced: list[dict] = []
    for row in rows:
        row_gaps: list[dict] = []
        evidence = row.evidence()
        if row.verdict == "BLOCKED":
            if not row.method.strip() or row.method.strip() in {"-", "—"}:
                row_gaps.append({"id": row.row_id, "code": "NO_LANDING_POINT",
                                 "detail": "BLOCKED 行没有 runbook/出口"})
            elif ("runbook" not in row.method.lower()
                  and "runbook" not in row.evidence_text.lower()):
                # 批次 45 复核 finding 7：BLOCKED 只写 issue、不写出口时，
                # “可签字的阻塞清单”无法复核——拿到条件后按哪一节复跑没有答案。
                row_gaps.append({"id": row.row_id, "code": "BLOCKED_WITHOUT_RUNBOOK",
                                 "detail": row.method[:160]})
            for item in evidence:
                if not M.resolve(root, item.target).exists():
                    row_gaps.append({"id": row.row_id, "code": "MISSING_EVIDENCE_PATH",
                                     "detail": f"{item.kind}:{item.target}"})
            gaps.extend(row_gaps)
            traced.append({
                "id": row.row_id,
                "requirement": row.check,
                "verdict": row.verdict,
                "tests": [],
                "evidence": [e.as_dict() for e in evidence],
                "gaps": [g["code"] for g in row_gaps],
            })
            continue
        device_evidence = any(e.kind == "device" for e in evidence)
        landings, placeholders = _landings(row.method)
        if placeholders:
            row_gaps.append({"id": row.row_id, "code": "PLACEHOLDER",
                             "detail": f"{row.method}（{len(placeholders)} 个占位）"})
        if not row.method.strip() or row.method.strip() in {"-", "—"}:
            row_gaps.append({"id": row.row_id, "code": "NO_LANDING_POINT",
                             "detail": "方法为空"})
        if row.verdict == "PASS" and row.method.startswith("UNVERIFIED:"):
            row_gaps.append({"id": row.row_id, "code": "PASS_WITH_UNVERIFIED_METHOD",
                             "detail": row.method})
        # 证据列里的 test/device 本身就是执行落点：D/I 域多行方法只写工具
        # 命令，测试 XML 与设备结果在证据列（批次 44 复核 finding 15）。
        has_test_landing = (
            any(kind in {"test", "device", "gradlew"} for kind, _ in landings)
            or any(item.kind in {"test", "device"} for item in evidence)
        )
        if row.verdict == "PASS" and not has_test_landing:
            row_gaps.append({"id": row.row_id, "code": "PASS_WITHOUT_TEST_LANDING",
                             "detail": f"方法/证据无 test/device 落点: {row.method}"})
        for kind, target in landings:
            if target is None:
                if kind == "device" and not device_evidence:
                    row_gaps.append({"id": row.row_id, "code": "DEVICE_EVIDENCE_MISSING",
                                     "detail": "方法声明设备执行，但无 device 证据"})
                continue
            if "<" in target or ">" in target:
                continue
            path = M.resolve(root, target)
            if path.exists():
                continue
            code = {"test": "MISSING_TEST_PATH", "tool": "MISSING_TOOL_PATH",
                    "path": "MISSING_EVIDENCE_PATH"}.get(kind, "MISSING_PATH")
            row_gaps.append({"id": row.row_id, "code": code, "detail": target})
        for item in evidence:
            if row.verdict != "PASS":
                continue
            if not M.resolve(root, item.target).exists():
                row_gaps.append({"id": row.row_id, "code": "MISSING_EVIDENCE_PATH",
                                 "detail": f"{item.kind}:{item.target}"})
        gaps.extend(row_gaps)
        traced.append({
            "id": row.row_id,
            "requirement": row.check,
            "verdict": row.verdict,
            "tests": [target for kind, target in landings if kind in {"test", "gradlew"} and target],
            "evidence": [e.as_dict() for e in evidence],
            "gaps": [g["code"] for g in row_gaps],
        })

    domains: dict[str, int] = {}
    for row in rows:
        domains[row.domain] = domains.get(row.domain, 0) + 1
    return {
        "ledger": ledger_path.as_posix(),
        "total": len(rows),
        "domains": domains,
        "gaps": gaps,
        "rows": traced,
        "ok": not gaps,
        "exit_code": 0 if not gaps else 1,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="需求→测试→证据追溯")
    parser.add_argument("--ledger", default=str(DEFAULT_LEDGER))
    parser.add_argument("--repo-root", default=str(ROOT))
    parser.add_argument("--out")
    parser.add_argument("--partial", action="store_true")
    args = parser.parse_args(argv)
    try:
        report = trace(args.ledger, args.repo_root, partial=args.partial)
    except M.LedgerError as exc:
        print(f"LEDGER_ERROR: {exc}", file=sys.stderr)
        return 2
    payload = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.out:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(payload, encoding="utf-8", newline="\n")
    print(json.dumps({k: v for k, v in report.items() if k != "rows"},
                     ensure_ascii=False, indent=2))
    return report["exit_code"]


if __name__ == "__main__":
    sys.exit(main())
