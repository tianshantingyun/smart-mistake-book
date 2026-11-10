#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批次 51 验收台账员：重生成三份哈希清单并自校验。

- docs/acceptance/evidence/2026-11-10-batch-51/REVIEWS.sha256
  覆盖 947 份行级 review JSON + verdict.schema.json（仓库相对路径）。
- docs/acceptance/evidence/2026-11-10-batch-51/EVIDENCE.sha256
  覆盖本验收层证据目录全部文件（相对该目录，`*` 前缀）。
- docs/research/evidence/2026-11-10-batch-51/acceptance/SHA256SUMS
  覆盖台账员证据目录全部文件（相对该目录，`*` 前缀）。

清单末不写自身。写完逐项复算，任何不一致 exit 1。
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
ROWS = ROOT / "docs" / "acceptance" / "verdicts" / "rows"
SCHEMA = ROOT / "docs" / "acceptance" / "verdicts" / "verdict.schema.json"
ACC = ROOT / "docs" / "acceptance" / "evidence" / "2026-11-10-batch-51"
RES = ROOT / "docs" / "research" / "evidence" / "2026-11-10-batch-51" / "acceptance"
REVIEWS = ACC / "REVIEWS.sha256"
ACC_EVIDENCE = ACC / "EVIDENCE.sha256"
RES_SUMS = RES / "SHA256SUMS"


def sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_lines(path: Path, lines: list[str]) -> None:
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def verify(path: Path, lines: list[str], base: Path) -> list[str]:
    bad: list[str] = []
    for line in lines:
        declared, rel = line.split("  ", 1)
        rel = rel.lstrip("*")
        actual = sha(base / rel)
        if actual != declared:
            bad.append(rel)
    return bad


def main() -> int:
    review_files = sorted(ROWS.glob("*.json")) + [SCHEMA]
    review_lines = [f"{sha(p)}  {p.relative_to(ROOT).as_posix()}" for p in review_files]
    write_lines(REVIEWS, review_lines)

    acc_files = sorted(p for p in ACC.rglob("*")
                       if p.is_file() and p not in {REVIEWS, ACC_EVIDENCE}
                       and ".mimosa" not in p.parts
                       and not p.name.endswith("-verify.log"))
    acc_lines = [f"{sha(p)}  {p.relative_to(ACC).as_posix()}" for p in acc_files]
    write_lines(ACC_EVIDENCE, acc_lines)

    res_files = sorted(p for p in RES.rglob("*")
                       if p.is_file() and p != RES_SUMS
                       and ".mimosa" not in p.parts
                       and not p.name.endswith("-verify.log"))
    res_lines = [f"{sha(p)}  {p.relative_to(RES).as_posix()}" for p in res_files]
    write_lines(RES_SUMS, res_lines)

    bad = (verify(REVIEWS, review_lines, ROOT)
           + verify(ACC_EVIDENCE, acc_lines, ACC)
           + verify(RES_SUMS, res_lines, RES))
    print(f"REVIEWS entries={len(review_lines)}")
    print(f"ACCEPTANCE EVIDENCE entries={len(acc_lines)}")
    print(f"RESEARCH EVIDENCE entries={len(res_lines)}")
    print(f"mismatches={len(bad)} {bad[:5]}")
    print(f"EXIT={0 if not bad else 1}")
    return 0 if not bad else 1


if __name__ == "__main__":
    raise SystemExit(main())
