#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""E-061 设备证据的机械复算：不读 summary，只读原始 am instrument 输出与 XML。

消 灭 的 失 败 ：`am instrument -w` 在用例失败时仍可能打印 `OK (n tests)` 之外
的东西，人工读“看起来绿”会误判；本脚本把三份原始件的逐例状态码、用例名集合
与 XML 计数做三向对账，任一不一致即 exit 1。

用法: python -I recompute.py
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOG = HERE / "e061-errorcause-ledger.log"
XML = HERE / "e061-errorcause-ledger.xml"
DEV = HERE / "e061-errorcause-ledger.device.json"

STATUS = re.compile(r"^INSTRUMENTATION_STATUS:\s*([A-Za-z_]+)=(.*)$")
CODE = re.compile(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$")
OK = re.compile(r"^OK \((\d+) tests?\)\s*$", re.MULTILINE)


def main() -> int:
    text = LOG.read_text(encoding="utf-8", errors="replace")
    codes: list[int] = []
    names: list[str] = []
    for line in text.splitlines():
        m = CODE.match(line.strip())
        if m:
            codes.append(int(m.group(1)))
        m = STATUS.match(line.strip())
        if m and m.group(1) == "test":
            names.append(m.group(2))

    root = ET.parse(XML).getroot()
    cases = [c.get("name") for c in root.iter("testcase")]
    ok_match = OK.search(text)

    checks: list[tuple[str, bool, str]] = []
    checks.append(("log has 'OK (3 tests)'", bool(ok_match) and ok_match.group(1) == "3",
                   ok_match.group(0) if ok_match else "missing"))
    checks.append(("log start codes (1) == 3", codes.count(1) == 3, f"count(1)={codes.count(1)}"))
    checks.append(("log pass codes (0) == 3", codes.count(0) == 3, f"count(0)={codes.count(0)}"))
    bad = [c for c in codes if c in (-1, -2, -3, -4)]
    checks.append(("log has no error/failure/ignored/assumption code", not bad, f"bad={bad}"))
    checks.append(("log test names unique == 3", len(set(names)) == 3, f"names={sorted(set(names))}"))
    checks.append(("xml tests == 3", root.get("tests") == "3", f"tests={root.get('tests')}"))
    checks.append(("xml failures/errors/skipped all 0",
                   all(root.get(k) == "0" for k in ("failures", "errors", "skipped")),
                   f"{root.get('failures')}/{root.get('errors')}/{root.get('skipped')}"))
    checks.append(("xml testcase names == log names",
                   sorted(cases) == sorted(set(names)), f"xml={sorted(cases)}"))
    dev_text = DEV.read_text(encoding="utf-8", errors="replace")
    checks.append(("device.json counts match", '"tests": 3' in dev_text and '"evaluated": 3' in dev_text
                   and '"failures": 0' in dev_text and '"skipped": 0' in dev_text, "device.json"))
    checks.append(("device.json verdict PASS", '"verdict": "PASS"' in dev_text, "verdict"))

    failed = [c for c in checks if not c[1]]
    for name, ok, detail in checks:
        print(f"CHECK {name}: {'OK' if ok else 'MISMATCH'} ({detail})")
    print(f"CHECKS={len(checks)} MISMATCHES={len(failed)}")
    print("RECOMPUTE=OK" if not failed else "RECOMPUTE=MISMATCH")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
