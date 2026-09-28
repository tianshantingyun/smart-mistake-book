# -*- coding: utf-8 -*-
"""kb_coverage.dedupe_judged_materials 的契约测试。

覆盖：`--dry-run` 可跑（退出码 0 且不写盘）、与 `--write` 互斥、按
(chunk_id,node_slug,type,title,content) 载荷判重保留第一条、`--write` 写回。
判定表与侧车全部打桩（mock pack_io.sidecar_paths），不读真实产物。

跑法（与仓库其余 tools/tests 一致）：
    python -m unittest discover -s tools/tests -t tools -p "test_kb_dedupe_judged_materials.py"
"""

from __future__ import annotations

import contextlib
import csv
import io
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from kb_build import pack_io
from kb_coverage import dedupe_judged_materials as djm

COLS = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title",
        "summary", "applicability", "content", "boundary", "note", "midx"]


def _row(chunk_id: str, slug: str, title: str, content: str, midx: str = "") -> list[str]:
    return ["数学/1.docx", chunk_id, "MATERIAL", slug, "CONCEPT_EXPLANATION",
            title, "概要", "适用性", content, "边界", "注", midx]


def _write(path: Path, rows: list[list[str]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(COLS)
        w.writerows(rows)


def _read(path: Path) -> list[dict]:
    with path.open(encoding="utf-8-sig", newline="") as fh:
        return list(csv.DictReader(fh))


class DryRunTests(unittest.TestCase):
    def test_dry_run_accepted_and_writes_nothing(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "j.csv"
            rows = [
                _row("c1", "节点甲", "标题", "内容A"),
                _row("c1", "节点乙", "标题", "内容A"),  # 载荷不同（slug 不同）→ 保留
                _row("c2", "节点丙", "标题", "内容B"),
                _row("c2", "节点丙", "标题", "内容B", midx="b"),  # 载荷相同 → 去重
            ]
            _write(path, rows)
            before = path.read_bytes()
            buf = io.StringIO()
            with mock.patch.object(djm, "JUDGMENTS", path), \
                    mock.patch.object(pack_io, "sidecar_paths", return_value=[]), \
                    contextlib.redirect_stdout(buf):
                rc = djm.main(["--dry-run"])
            self.assertEqual(0, rc)
            self.assertEqual(before, path.read_bytes())  # 不写盘
            self.assertIn("去重 1 行", buf.getvalue())

    def test_dry_run_conflicts_with_write(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "j.csv"
            _write(path, [_row("c1", "节点甲", "标题", "内容A")])
            with mock.patch.object(djm, "JUDGMENTS", path):
                with self.assertRaises(SystemExit) as ctx:
                    djm.main(["--dry-run", "--write"])
            self.assertEqual(2, ctx.exception.code)


class WriteTests(unittest.TestCase):
    def test_write_keeps_first_of_duplicate_payload(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "j.csv"
            _write(path, [
                _row("c2", "节点丙", "标题", "内容B", midx=""),
                _row("c2", "节点丙", "标题", "内容B", midx="b"),
            ])
            with mock.patch.object(djm, "JUDGMENTS", path), \
                    mock.patch.object(pack_io, "sidecar_paths", return_value=[]):
                rc = djm.main(["--write"])
            self.assertEqual(0, rc)
            rows = _read(path)
            self.assertEqual(COLS, list(rows[0].keys()))
            self.assertEqual(1, len(rows))
            self.assertEqual("", rows[0]["midx"])  # 保留第一条


if __name__ == "__main__":
    unittest.main()
