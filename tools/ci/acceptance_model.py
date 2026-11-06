#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验收台账的共享解析模型（批次 44 / D0 item 1–6）。

## 它消灭的具体失败

批次 41–43 连续三批出现“计划写 100、实账 104”的口径漂移；同时 report / lint /
trace 三个工具若各自解析 Markdown 表格，任何一处空格或列数差异都会让结论互相
矛盾。本模块是唯一解析器：ID 集合、域计数、证据 token 语法、verdict 合法集都在
这里定义，三个消费者只读同一份结果。

## 台账行格式

```text
| ID | 域 | 检查 | 方法 | 证据 | verdict |
```

- `ID`：`D0-001` / `A-001` / `J-090` / `FIX-001` / `GATE-001`，三位数字。
- `检查`/`方法` 内不得含 `|`（生成器把 `|` 换成 `／`）。
- `证据`：`-` 表示无；否则用 `;` 分隔多个 token，token 语法
  `kind:repo/relative/path#k=v,k2=v2`。
- verdict：`PASS` / `FAIL` / `BLOCKED` / `UNVERIFIED`。

证据 kind：`cmd`（命令日志）/ `test`（测试 XML/JSON）/ `device`（设备输出）/
`review`（独立复核 verdict）/ `file`（产物）/ `hash`（哈希清单）。
独立证据类只认 `cmd/test/device/review`；`file/hash` 是辅助，不能凑 PASS。
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

VERDICTS = ("PASS", "FAIL", "BLOCKED", "UNVERIFIED")
EVIDENCE_KINDS = ("cmd", "test", "device", "review", "file", "hash")
INDEPENDENT_KINDS = ("cmd", "test", "device", "review")

DOMAINS = (
    ("D0", "验收基础设施", 100),
    ("A", "功能旅程", 90),
    ("B", "数据完整性", 90),
    ("C", "视觉与渲染", 90),
    ("D", "模型链路", 90),
    ("E", "复习与算法", 90),
    ("F", "性能与稳定", 90),
    ("G", "安全与隐私", 90),
    ("H", "合规与文案", 90),
    ("I", "可用性与无障碍", 90),
    ("J", "发布工程", 90),
    ("FIX", "修复与加固", 100),
    ("GATE", "门禁与终裁", 100),
)

DOMAIN_TITLES = {key: title for key, title, _ in DOMAINS}
DOMAIN_COUNTS = {key: count for key, _, count in DOMAINS}
TOTAL_ITEMS = sum(DOMAIN_COUNTS.values())

ROW_RE = re.compile(r"^\|\s*([A-Z][A-Z0-9]*-\d{3})\s*\|")
TOKEN_RE = re.compile(r"^([a-z]+):(.+)$")
ATTR_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)=(.*)$")


class LedgerError(ValueError):
    """台账结构错误；调用方转成 exit 2。"""


@dataclass(frozen=True)
class Evidence:
    kind: str
    target: str
    attrs: tuple[tuple[str, str], ...] = ()
    raw: str = ""

    def attr(self, name: str, default: str | None = None) -> str | None:
        for key, value in self.attrs:
            if key == name:
                return value
        return default

    def as_dict(self) -> dict:
        return {"kind": self.kind, "target": self.target, "attrs": dict(self.attrs)}


@dataclass
class Row:
    row_id: str
    domain: str
    check: str
    method: str
    evidence_text: str
    verdict: str
    line_no: int = 0
    evidence_items: list[Evidence] = field(default_factory=list)

    def evidence(self) -> list[Evidence]:
        if self.evidence_items:
            return list(self.evidence_items)
        items, _ = parse_evidence(self.evidence_text)
        self.evidence_items = items
        return list(items)

    def independent_kinds(self) -> set[str]:
        return {e.kind for e in self.evidence() if e.kind in INDEPENDENT_KINDS}

    def as_dict(self) -> dict:
        return {
            "id": self.row_id,
            "domain": self.domain,
            "check": self.check,
            "method": self.method,
            "evidence": self.evidence_text,
            "verdict": self.verdict,
            "line": self.line_no,
        }


def domain_of(row_id: str) -> str:
    return row_id.rsplit("-", 1)[0]


def expected_ids() -> list[str]:
    ids: list[str] = []
    for key, _, count in DOMAINS:
        ids.extend(f"{key}-{index:03d}" for index in range(1, count + 1))
    return ids


def parse_evidence(text: str) -> tuple[list[Evidence], list[str]]:
    """解析证据单元格。返回 (证据列表, 错误列表)。空/`-` 返回空列表。"""
    stripped = (text or "").strip()
    if not stripped or stripped in {"-", "—", "n/a", "N/A"}:
        return [], []
    items: list[Evidence] = []
    errors: list[str] = []
    for raw in stripped.split(";"):
        token = raw.strip()
        if not token:
            continue
        match = TOKEN_RE.match(token)
        if not match:
            errors.append(f"BAD_EVIDENCE_TOKEN: {token!r}（应为 kind:path#attr）")
            continue
        kind, rest = match.group(1), match.group(2)
        if kind not in EVIDENCE_KINDS:
            errors.append(f"BAD_EVIDENCE_KIND: {kind!r}（允许 {', '.join(EVIDENCE_KINDS)}）")
            continue
        target, _, attr_text = rest.partition("#")
        target = target.strip()
        if not target:
            errors.append(f"EMPTY_EVIDENCE_PATH: {token!r}")
            continue
        attrs: list[tuple[str, str]] = []
        if attr_text:
            for pair in attr_text.split(","):
                pair = pair.strip()
                if not pair:
                    continue
                am = ATTR_RE.match(pair)
                if not am:
                    errors.append(f"BAD_EVIDENCE_ATTR: {pair!r}")
                    continue
                attrs.append((am.group(1), am.group(2)))
        items.append(Evidence(kind=kind, target=target, attrs=tuple(attrs), raw=token))
    return items, errors


def format_evidence(items: list[Evidence]) -> str:
    if not items:
        return "-"
    parts = []
    for item in items:
        text = f"{item.kind}:{item.target}"
        if item.attrs:
            text += "#" + ",".join(f"{k}={v}" for k, v in item.attrs)
        parts.append(text)
    return "; ".join(parts)


def _clean_cell(value: str) -> str:
    return value.replace("|", "／").strip()


def format_row(row: Row) -> str:
    return (
        f"| {row.row_id} | {row.domain} | {_clean_cell(row.check)} | "
        f"{_clean_cell(row.method)} | {_clean_cell(row.evidence_text)} | {row.verdict} |"
    )


def parse_ledger_text(text: str, source: str = "<text>") -> tuple[list[Row], list[dict]]:
    rows: list[Row] = []
    issues: list[dict] = []
    seen: dict[str, int] = {}
    for line_no, raw in enumerate(text.splitlines(), start=1):
        line = raw.rstrip("\r")
        if not ROW_RE.match(line):
            continue
        parts = line.split("|")
        # 行首/行尾的管道产生两个空串。
        if len(parts) != 8 or parts[0].strip() or parts[-1].strip():
            issues.append({"line": line_no, "code": "ROW_COLUMN_COUNT",
                           "detail": f"期望 6 列，实得 {len(parts) - 2}: {line[:160]}"})
            continue
        _, row_id, domain, check, method, evidence_text, verdict, _ = parts
        row_id = row_id.strip()
        row = Row(
            row_id=row_id,
            domain=domain.strip(),
            check=check.strip(),
            method=method.strip(),
            evidence_text=evidence_text.strip(),
            verdict=verdict.strip(),
            line_no=line_no,
        )
        if row.verdict not in VERDICTS:
            issues.append({"line": line_no, "code": "BAD_VERDICT",
                           "detail": f"{row_id}: {row.verdict!r}"})
        expected_domain = domain_of(row_id)
        if row.domain != expected_domain:
            issues.append({"line": line_no, "code": "DOMAIN_MISMATCH",
                           "detail": f"{row_id}: 行域 {row.domain!r} != id 域 {expected_domain!r}"})
        _, ev_errors = parse_evidence(row.evidence_text)
        for detail in ev_errors:
            issues.append({"line": line_no, "code": "BAD_EVIDENCE", "detail": f"{row_id}: {detail}"})
        if row_id in seen:
            issues.append({"line": line_no, "code": "DUPLICATE_ID",
                           "detail": f"{row_id} 首次见 :{seen[row_id]}，重复于 :{line_no}"})
        else:
            seen[row_id] = line_no
        rows.append(row)
    if not rows:
        issues.append({"line": 0, "code": "NO_ROWS", "detail": f"{source} 未解析到任何台账行"})
    return rows, issues


def parse_ledger(path: str | Path) -> tuple[list[Row], list[dict]]:
    path = Path(path)
    if not path.exists():
        raise LedgerError(f"台账不存在: {path}")
    return parse_ledger_text(path.read_text(encoding="utf-8"), source=str(path))


def identity_issues(rows: list[Row]) -> list[dict]:
    """ID 集合、域计数、总数。返回 issue dict（无 line）。"""
    issues: list[dict] = []
    actual = [row.row_id for row in rows]
    expected = expected_ids()
    expected_set = set(expected)
    actual_set = set(actual)
    for row_id in sorted(expected_set - actual_set):
        issues.append({"code": "MISSING_ROW", "detail": f"缺少 {row_id}"})
    for row_id in sorted(actual_set - expected_set):
        issues.append({"code": "UNEXPECTED_ROW", "detail": f"多余 {row_id}"})
    for key, _, count in DOMAINS:
        got = sum(1 for row in rows if row.domain == key)
        if got != count:
            issues.append({"code": "DOMAIN_COUNT",
                           "detail": f"{key}: 期望 {count}，实得 {got}"})
    if len(actual) != TOTAL_ITEMS:
        issues.append({"code": "TOTAL_COUNT",
                       "detail": f"期望 {TOTAL_ITEMS}，实得 {len(actual)}"})
    return issues


def resolve(repo_root: str | Path, target: str) -> Path:
    """把台账里的仓库相对路径解析到磁盘；绝对路径原样返回。"""
    path = Path(target)
    if path.is_absolute():
        return path
    return Path(repo_root) / path


def ledger_sha256(path: str | Path) -> str:
    import hashlib

    return hashlib.sha256(Path(path).read_bytes()).hexdigest()
