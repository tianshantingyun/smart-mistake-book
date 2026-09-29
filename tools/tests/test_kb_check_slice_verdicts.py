# -*- coding: utf-8 -*-
"""切片判定自检门（check_slice_verdicts）的用例：好批全过、每类缺陷都能报出。

覆盖：行数不符 / 键不在切片 / 键重复 / 非法 action / MATERIAL 缺字段与非法 type /
悬空节点 / content 超 4 段 / boundary 占位 / ASCII 双引号 / SKIP 缺理由 / NEW 提案科目非法。
"""

from __future__ import annotations

import csv
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from kb_build import pack_io  # noqa: E402
from kb_coverage import check_slice_verdicts as C  # noqa: E402


def _write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=C.COLS)
        writer.writeheader()
        writer.writerows(rows)


def _row(**over) -> dict:
    base = {"chunk_rel": "src/a.pdf", "chunk_id": "a1", "action": "MATERIAL",
            "node_slug": "node-a", "type": "METHOD_MODEL", "title": "标题",
            "summary": "摘要", "applicability": "PRIMARY 适用", "content": "一行结论。",
            "boundary": "真边界。", "note": "", "midx": ""}
    base.update(over)
    return base


class CheckSliceVerdictsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-slice-gate-", dir=pack_io.REPO / "build"))
        pack = {"schemaVersion": 2, "packId": "test-pack", "taxonomyVersion": "test-pack",
                "subjects": [{"subject": "MATH", "topics": [{"slug": "t1", "name": "主题一",
                             "knowledgePoints": [
                                 {"slug": "node-a", "name": "节点甲", "aliases": ["节点甲"],
                                  "kind": "CONCEPT", "boundary": "定位：测。真边界。",
                                  "sourceLocator": "人教版高中教材（2019）", "prerequisiteSlugs": []}]}]}]}
        (self.tmp / pack_io.PACK_NAME).write_text(json.dumps(pack, ensure_ascii=False),
                                                 encoding="utf-8")
        pack_io.use_directory(self.tmp)
        self.slice = self.tmp / "s.jsonl"
        self.slice.write_text(
            json.dumps({"chunk_rel": "src/a.pdf", "chunk_id": "a1", "subject": "MATH",
                        "heading": "h", "text": "t", "fp": "f1"}) + "\n"
            + json.dumps({"chunk_rel": "src/a.pdf", "chunk_id": "a2", "subject": "MATH",
                          "heading": "h", "text": "t", "fp": "f2"}) + "\n", encoding="utf-8")
        self.csv = self.tmp / "v.csv"

    def tearDown(self):
        pack_io.reset_directory()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _check(self) -> list[str]:
        return C.check_one(self.csv, self.slice, C._pack_nodes())

    def test_good_batch_passes(self):
        _write_csv(self.csv, [_row(), _row(chunk_id="a2", action="SKIP", node_slug="", type="",
                                           title="", summary="", applicability="", content="",
                                           boundary="", note="题目派生")])
        self.assertEqual([], self._check())

    def test_defects_reported(self):
        _write_csv(self.csv, [
            _row(chunk_id="a2", type="DERIVATION"),
            _row(chunk_id="zz"),                      # 键不在切片
            _row(chunk_id="a1", node_slug="不存在"),
            _row(chunk_id="a1", content="1\\n2\\n3\\n4\\n5"),
            _row(chunk_id="a1", boundary="定位：待补。"),
            _row(chunk_id="a1", title='含 "引号"'),
            _row(chunk_id="a1", action="SKIP", note="NEW:WRONG/x"),
            _row(chunk_id="a1", action="SKIP", note=""),
            _row(chunk_id="a1", summary=""),
        ])
        joined = "; ".join(self._check())
        self.assertIn("行数", joined)
        self.assertIn("键不在切片", joined)
        self.assertIn("非法 type", joined)
        self.assertIn("节点不存在", joined)
        self.assertIn("content 超过 4 段", joined)
        self.assertIn("boundary 是定位占位", joined)
        self.assertIn("ASCII 双引号", joined)
        self.assertIn("NEW 提案的科目", joined)
        self.assertIn("SKIP 缺 note", joined)
        self.assertIn("缺 summary", joined)


if __name__ == "__main__":
    unittest.main()
