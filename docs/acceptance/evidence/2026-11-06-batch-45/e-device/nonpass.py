#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""台账非 PASS 行清单（机械分类，不读 summary）。

消 灭 的 失 败 ：人工翻 1200 行会漏行/串域；本脚本按行首 ID 的域前缀分组，
输出 FAIL / BLOCKED / UNVERIFIED 三张表，并给出每域计数。

用法: python -I nonpass.py > nonpass.txt
"""

from __future__ import annotations

import re
import sys
from collections import Counter, defaultdict

LEDGER = "docs/acceptance/ledger.md"
ROW = re.compile(r"^\|\s*([A-Z][A-Z0-9]*-\d{3})\s*\|")


def main() -> int:
    rows: list[tuple[str, str, str, str]] = []
    with open(LEDGER, encoding="utf-8") as handle:
        for line in handle:
            m = ROW.match(line)
            if not m:
                continue
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) != 6:
                print(f"BAD_ROW id={m.group(1)} cells={len(cells)}")
                continue
            rows.append((cells[0], cells[1], cells[2], cells[5]))
    counts = Counter(r[3] for r in rows)
    by_domain = defaultdict(Counter)
    for _id, domain, _check, verdict in rows:
        by_domain[domain][verdict] += 1

    print(f"TOTAL={len(rows)} COUNTS={dict(sorted(counts.items()))}")
    print("DOMAIN TOTAL PASS FAIL BLOCKED UNVERIFIED")
    for domain in sorted(by_domain):
        c = by_domain[domain]
        print(f"{domain} {sum(c.values())} {c['PASS']} {c['FAIL']} {c['BLOCKED']} {c['UNVERIFIED']}")
    for verdict in ("FAIL", "BLOCKED", "UNVERIFIED"):
        print(f"\n## {verdict} ({counts[verdict]})")
        for _id, domain, check, v in rows:
            if v == verdict:
                print(f"{_id}\t{domain}\t{check[:150]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
