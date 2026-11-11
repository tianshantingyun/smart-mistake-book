#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""行级复核 checks 的域内判别力自检（批次 47 WS2）。

## 它消灭的具体失败

`acceptance_report.py` 的 `REVIEW_CHECKS_SHARED` 只在**同一组**非 `ledger_row`
checks 被 **>= 5 行**逐字节共用时才报违规。这留下一个空档：

- 一行可以与**另一行**逐字节共用同一套 checks（2–4 行一组），报告不报；
- 于是一行自己的 claim（例如“回车后页面标签仍在”）没有任何一条只属于
  这一行的复算判据，判 PASS 时无法指认“哪条证据支持这一行”；
- 台账上写着 `checks_scope=row-specific`，实际 checks 仍是别行的复制，
  这是**标签行级、内容共享**的假判别力。

本脚把判据收紧到**行**：同一域内，每一行的非 `ledger_row` checks 里，
至少要有 1 条在**本域其他行中不出现**（整条 check 逐字节比较）。
同时把 >= 5 行共用的指纹组一并报出，与 `acceptance_report.py` 口径对齐。

它只证明“这一行有属于自己的、可复算的 check”。它**不**证明该 check 的
语义足以支撑 claim，也不构成第二人/第二模型的独立复核。

## 用法

    python -I tools/ci/check_row_specific_checks.py --domain I
    python -I tools/ci/check_row_specific_checks.py            # 全部域
    python -I tools/ci/check_row_specific_checks.py --domain I --out build/x.json

exit 0 = 过滤后的每一行都至少 1 条域内独有 check，且没有 >= 5 行共用的组；
exit 1 = 存在 0 条域内独有 check 的行，或存在 >= 5 行共用的指纹组；
exit 2 = 输入不可读（缺目录 / JSON 解析失败）。
"""

from __future__ import annotations

import argparse
import collections
import json
import sys
from pathlib import Path

SHARED_GROUP_THRESHOLD = 5


def domain_of(row_id: str) -> str:
    """I-001 -> I；D0-001 -> D0；FIX-T04 -> FIX；GATE-035 -> GATE。"""
    return row_id.rsplit("-", 1)[0] if "-" in row_id else row_id


def check_key(check: dict) -> str:
    """整条 check 的规范字节串；与 report 的逐字节共用口径一致。"""
    return json.dumps(check, ensure_ascii=False, sort_keys=True)


def load_rows(rows_dir: Path) -> list[dict]:
    rows: list[dict] = []
    for path in sorted(rows_dir.glob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise SystemExit(f"READ_ERROR {path}: {exc}")
        if not isinstance(data, dict) or not data.get("id"):
            continue
        checks = [c for c in data.get("checks") or [] if isinstance(c, dict)]
        body = [c for c in checks
                if (c.get("name") or c.get("check")) != "ledger_row"]
        rows.append({
            "id": str(data["id"]),
            "verdict": data.get("verdict"),
            "checks_scope": data.get("checks_scope"),
            "file": path.name,
            "keys": [check_key(c) for c in body],
            "fingerprint": _fingerprint(body),
        })
    return rows


def _fingerprint(body: list[dict]) -> str | None:
    if not body:
        return None
    payload = [json.dumps(c, ensure_ascii=False, sort_keys=True) for c in body]
    import hashlib
    return hashlib.sha256("\n".join(payload).encode("utf-8")).hexdigest()


def evaluate(rows: list[dict]) -> dict:
    # 每个 key 出现在多少“行”里（同一行重复列两次只算一次）
    key_rows: dict[str, set[str]] = collections.defaultdict(set)
    for row in rows:
        for key in set(row["keys"]):
            key_rows[key].add(row["id"])

    per_row: list[dict] = []
    for row in rows:
        unique = [k for k in dict.fromkeys(row["keys"]) if len(key_rows[k]) == 1]
        per_row.append({
            "id": row["id"],
            "domain": domain_of(row["id"]),
            "verdict": row["verdict"],
            "checks_scope": row["checks_scope"],
            "non_ledger_checks": len(row["keys"]),
            "domain_unique_checks": len(unique),
            "unique_example": json.loads(unique[0]) if unique else None,
            "fingerprint": row["fingerprint"],
        })

    groups = collections.Counter(r["fingerprint"] for r in rows if r["fingerprint"])
    shared_groups = [
        {"fingerprint": fp, "rows": n}
        for fp, n in sorted(groups.items(), key=lambda kv: (-kv[1], kv[0]))
        if n >= SHARED_GROUP_THRESHOLD
    ]
    failing = [r["id"] for r in per_row if r["domain_unique_checks"] == 0]
    by_domain: dict[str, dict] = {}
    for r in per_row:
        stats = by_domain.setdefault(r["domain"], {
            "rows": 0, "rows_without_unique": 0,
            "shared_suite_label_rows": 0,
        })
        stats["rows"] += 1
        if r["domain_unique_checks"] == 0:
            stats["rows_without_unique"] += 1
        if r["checks_scope"] == "shared-suite":
            stats["shared_suite_label_rows"] += 1
    return {
        "rows_dir": None,
        "rows": len(per_row),
        "by_domain": by_domain,
        "rows_without_domain_unique_check": failing,
        "shared_check_groups_ge5": shared_groups,
        "ok": not failing and not shared_groups,
        "detail": per_row,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rows-dir", default="docs/acceptance/verdicts/rows")
    parser.add_argument("--domain", action="append", default=None,
                        help="只检查这些域前缀（可重复；缺省=全部）")
    parser.add_argument("--out", default=None)
    args = parser.parse_args(argv)

    rows_dir = Path(args.rows_dir)
    if not rows_dir.is_dir():
        print(f"ROWS_DIR_MISSING {rows_dir}", file=sys.stderr)
        return 2

    rows = load_rows(rows_dir)
    if args.domain:
        wanted = {d.upper() for d in args.domain}
        rows = [r for r in rows if domain_of(r["id"]).upper() in wanted]

    result = evaluate(rows)
    result["rows_dir"] = str(rows_dir)
    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.out:
        out = Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text + "\n", encoding="utf-8")
    print(text)
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
