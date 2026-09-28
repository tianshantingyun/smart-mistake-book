# -*- coding: utf-8 -*-
"""块池键唯一性门：唯一键数 == 行数 → exit 0。

materialize 以 `(rel_path, chunk_id)` 建 dict，撞键会静默只留末行丢内容——
所以这条门必须常绿：任一重复键即 exit 1。

用法：
    python tools/kb_coverage/verify_pool_keys.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_coverage.pool_path import POOL_PATH  # noqa: E402

CHUNKS = POOL_PATH


def main(argv: list[str] | None = None) -> int:
    rows = 0
    keys: set[tuple[str, str]] = set()
    dup = 0
    with CHUNKS.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            rows += 1
            rec = json.loads(line)
            key = (rec.get("rel_path") or "", rec.get("chunk_id") or "")
            if key in keys:
                dup += 1
            keys.add(key)
    print(f"块池 {rows} 行 / 唯一键 {len(keys)} / 重复键 {dup}")
    if len(keys) != rows:
        print("键不唯一：materialize 会静默丢块", file=sys.stderr)
        return 1
    print("键唯一性门通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
