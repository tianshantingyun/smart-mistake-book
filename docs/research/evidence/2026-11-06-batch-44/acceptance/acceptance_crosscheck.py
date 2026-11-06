#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批次 44 验收报告的可复算交叉核对（只读仓库，不改台账/工具）。

## 它消灭的具体失败

`trace_requirements.py` 给 exit 1 时，若直接写「260 个缺口」而不区分
「真实缺失」与「解析器误报」，主会话无法判断门到底能不能过；
反过来若直接写「都是误报」又会掩盖真实缺口。本脚本逐条重算每个 gap：

- `MISSING_TEST_PATH`：去掉 `#方法名` 后重新 stat 文件；
- `MISSING_TOOL_PATH`：忽略 `-m` 这类 python 模块开关后重新 stat；
- `PASS_WITHOUT_TEST_LANDING`：看证据列是否已有 `device:` 落点；
- `MISSING_EVIDENCE_PATH` / `DEVICE_EVIDENCE_MISSING`：按原样统计（不重判）。

同时输出每行的 run 绑定（设备证据的 serial + recorded_at + boot_id 前 8 位，
否则 `ledger@<sha8>`），供 `docs/acceptance/verdicts.md` 直接引用。

用法：
    python -I docs/research/evidence/2026-11-06-batch-44/acceptance/acceptance_crosscheck.py \
        --ledger docs/acceptance/ledger.md \
        --trace  docs/research/evidence/2026-11-06-batch-44/acceptance/trace.json \
        --out    docs/research/evidence/2026-11-06-batch-44/acceptance/crosscheck.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]  # docs/research/evidence/<batch>/acceptance/ -> repo root
sys.path.insert(0, str(ROOT / "tools" / "ci"))

import acceptance_model as M  # noqa: E402

PY_RE = re.compile(r"python(?:\s+-I)?\s+([^\s;]+)")
PYTEST_RE = re.compile(r"pytest\s+([^\s;]+)")


def stat_ok(root: Path, target: str) -> bool:
    if target in {"-m", "-", ""}:
        return True
    if "<" in target or ">" in target:
        return True
    base = target.split("#", 1)[0]
    return M.resolve(root, base).exists()


def device_binding(root: Path, row: M.Row) -> str | None:
    for item in row.evidence():
        if item.kind != "device":
            continue
        path = M.resolve(root, item.target)
        if not path.exists():
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue
        serial = data.get("serial")
        recorded = data.get("recorded_at")
        boot = str(data.get("boot_id", ""))[:8]
        if serial and recorded:
            return f"{serial}@{recorded}#boot={boot}"
    return None


def normalized_landings(method: str) -> list[tuple[str, str | None]]:
    """按三种已知解析缺陷归一化后重新抽取落点。

    缺陷 1：`test: path#方法名` 的 `#方法名` 未剥离 → 文件被当成不存在；
    缺陷 2：`python -I -m pytest` 把 `-m` 当成脚本路径；
    缺陷 3：全角 `；` 未作为分隔符 → 整块落到命令行分支。
    """
    landings: list[tuple[str, str | None]] = []
    for chunk in method.replace("；", ";").split(";"):
        chunk = chunk.strip()
        if not chunk or chunk.startswith(("BLOCKED:", "UNVERIFIED:")):
            continue
        head = chunk.split(":", 1)
        kind = head[0].strip() if len(head) == 2 else ""
        rest = head[1].strip() if len(head) == 2 else chunk
        if kind in {"test", "review", "file", "hash"} and rest:
            target = rest.split()[0].split("#", 1)[0]
            landings.append(("test" if kind == "test" else "path", target))
            continue
        if chunk.startswith("device:"):
            landings.append(("device", None))
            continue
        command = chunk[4:].strip() if chunk.startswith("cmd:") else chunk
        if "./gradlew" in command or "gradlew " in command:
            landings.append(("gradlew", None))
        for target in PYTEST_RE.findall(command):
            landings.append(("test", target))
        for target in PY_RE.findall(command):
            if target.startswith("-"):
                continue
            landings.append(("tool", target))
    return landings


def normalized_gaps(root: Path, row: M.Row) -> list[dict]:
    gaps: list[dict] = []
    evidence = row.evidence()
    device_evidence = any(e.kind == "device" for e in evidence)
    landings = normalized_landings(row.method)
    for kind, target in landings:
        if target is None:
            if kind == "device" and not device_evidence:
                gaps.append({"code": "DEVICE_EVIDENCE_MISSING", "detail": "方法声明设备执行，但无 device 证据"})
            continue
        if "<" in target or ">" in target or target in {"-", "-m"}:
            continue
        if not stat_ok(root, target):
            code = {"test": "MISSING_TEST_PATH", "tool": "MISSING_TOOL_PATH",
                    "path": "MISSING_EVIDENCE_PATH"}.get(kind, "MISSING_PATH")
            gaps.append({"code": code, "detail": target})
    if row.verdict == "PASS":
        has_landing = any(k in {"test", "device", "gradlew"} for k, _ in landings)
        if not has_landing:
            gaps.append({"code": "PASS_WITHOUT_TEST_LANDING", "detail": row.method})
        for item in evidence:
            if not M.resolve(root, item.target).exists():
                gaps.append({"code": "MISSING_EVIDENCE_PATH",
                             "detail": f"{item.kind}:{item.target}"})
    return gaps


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="批次 44 验收交叉核对")
    parser.add_argument("--ledger", default=str(ROOT / "docs" / "acceptance" / "ledger.md"))
    parser.add_argument("--trace", default=None)
    parser.add_argument("--out", default=None)
    args = parser.parse_args(argv)

    ledger = Path(args.ledger)
    root = ROOT
    rows, issues = M.parse_ledger(ledger)
    by_id = {row.row_id: row for row in rows}
    ledger_sha = hashlib.sha256(ledger.read_bytes()).hexdigest()

    trace = json.loads(Path(args.trace).read_text(encoding="utf-8")) if args.trace else {"gaps": []}
    normalized_by_id = {row.row_id: normalized_gaps(root, row) for row in rows}
    classified: list[dict] = []
    for gap in trace.get("gaps", []):
        row = by_id.get(gap["id"])
        code = gap["code"]
        detail = gap["detail"]
        still_present = any(
            g["code"] == code for g in normalized_by_id.get(gap["id"], [])
        )
        kind = "real-gap" if still_present or not gap["id"] else "parser-artifact"
        classified.append({
            "id": gap["id"],
            "code": code,
            "detail": detail,
            "verdict": row.verdict if row else None,
            "class": kind,
        })

    summary: dict[str, dict[str, int]] = {}
    for item in classified:
        bucket = summary.setdefault(item["code"], {})
        bucket[item["class"]] = bucket.get(item["class"], 0) + 1

    verdict_counts: dict[str, int] = {}
    domain_counts: dict[str, dict[str, int]] = {}
    for row in rows:
        verdict_counts[row.verdict] = verdict_counts.get(row.verdict, 0) + 1
        domain_counts.setdefault(row.domain, {})
        domain_counts[row.domain][row.verdict] = domain_counts[row.domain].get(row.verdict, 0) + 1

    bindings = {}
    for row in rows:
        bindings[row.row_id] = device_binding(root, row) or f"ledger@{ledger_sha[:8]}"

    normalized_summary: dict[str, dict[str, int]] = {}
    normalized_rows: list[dict] = []
    for row in rows:
        gaps = normalized_by_id[row.row_id]
        if not gaps:
            continue
        normalized_rows.append({"id": row.row_id, "verdict": row.verdict,
                                "gaps": [g["code"] for g in gaps]})
        for gap in gaps:
            bucket = normalized_summary.setdefault(gap["code"], {})
            bucket[row.verdict] = bucket.get(row.verdict, 0) + 1

    payload = {
        "ledger": ledger.as_posix(),
        "ledger_sha256": ledger_sha,
        "total": len(rows),
        "parse_issues": issues,
        "verdict_counts": verdict_counts,
        "domain_counts": domain_counts,
        "gap_classification_raw": summary,
        "gaps": classified,
        "normalized_gap_counts": normalized_summary,
        "normalized_gap_rows": len(normalized_rows),
        "normalized_gaps": normalized_rows,
        "review_evidence_tokens": sum(
            1 for row in rows for e in row.evidence() if e.kind == "review"
        ),
        "bindings": bindings,
    }
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    if args.out:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding="utf-8", newline="\n")
    print(json.dumps({k: v for k, v in payload.items() if k not in {"bindings", "gaps"}},
                     ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
