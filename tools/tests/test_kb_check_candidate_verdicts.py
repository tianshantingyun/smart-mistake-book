# -*- coding: utf-8 -*-
"""裁决表自检门（check_candidate_verdicts）的用例：好坏两侧都要钉住。

① 合规批次全过（目录批 + 材料批）；
② 行数不符 / 非法 verdict / 悬空绑定 / 缺材料字段 / 例题标题 / ASCII 双引号 逐一报出；
③ 坏批让整个门 exit 1（不许"总体通过"）。
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

from kb_build import check_candidate_verdicts as C  # noqa: E402
from kb_build import pack_io  # noqa: E402


def _write_csv(path: Path, cols: list[str], rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=cols)
        writer.writeheader()
        writer.writerows(rows)


def _row(cols: list[str], values: dict) -> dict:
    return {c: values.get(c, "") for c in cols}


class CheckCandidateVerdictsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-cv-", dir=pack_io.REPO / "build"))
        pack = {"schemaVersion": 2, "packId": "test-pack", "taxonomyVersion": "test-pack",
                "subjects": [{"subject": "MATH", "topics": [{"slug": "t1", "name": "主题一",
                             "knowledgePoints": [
                                 {"slug": "node-a", "name": "节点甲", "aliases": ["节点甲"],
                                  "kind": "CONCEPT", "boundary": "定位：测。真边界。",
                                  "sourceLocator": "人教版高中教材（2019）", "prerequisiteSlugs": []}]}]}]}
        (self.tmp / pack_io.PACK_NAME).write_text(json.dumps(pack, ensure_ascii=False),
                                                  encoding="utf-8")
        pack_io.use_directory(self.tmp)
        self.base = self.tmp / "cand"
        (self.base / "dir_batches").mkdir(parents=True)
        (self.base / "verdicts").mkdir(parents=True)
        (self.base / "dir_batches" / "batch_000.jsonl").write_text(
            '{"idx": 0}\n{"idx": 1}\n', encoding="utf-8")
        (self.base / "dir_batches" / "batch_001.jsonl").write_text(
            '{"idx": 2}\n', encoding="utf-8")

    def tearDown(self):
        pack_io.reset_directory()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_good_batch_passes(self):
        _write_csv(self.base / "verdicts" / "dir_batch_000.csv", C.DIR_COLS, [
            _row(C.DIR_COLS, {"idx": "0", "subject": "MATH", "name": "甲", "verdict": "COVERED",
                              "evidence": "m-1"}),
            _row(C.DIR_COLS, {"idx": "1", "subject": "MATH", "name": "乙", "verdict": "GAP",
                              "node_slug": "node-a", "material_title": "补料",
                              "material_type": "METHOD_MODEL", "material_content": "一行。",
                              "material_boundary": "边界。", "evidence": "definition 关键词"}),
        ])
        nodes = C._pack_nodes()
        errors = C.check_dir_batch(self.base / "verdicts" / "dir_batch_000.csv",
                                   self.base / "dir_batches" / "batch_000.jsonl", nodes)
        self.assertEqual([], errors)

    def test_bad_batch_reports_each_defect(self):
        _write_csv(self.base / "verdicts" / "dir_batch_001.csv", C.DIR_COLS, [
            _row(C.DIR_COLS, {"idx": "2", "subject": "MATH", "name": "丙", "verdict": "FANCY",
                              "evidence": "x"}),
        ])
        _write_csv(self.base / "verdicts" / "dir_batch_002.csv", C.DIR_COLS, [
            _row(C.DIR_COLS, {"idx": "3", "subject": "MATH", "name": "丁", "verdict": "GAP",
                              "node_slug": "不存在", "material_title": "例 1",
                              "material_type": "DERIVATION", "material_content": '"炸"',
                              "evidence": ""}),
            _row(C.DIR_COLS, {"idx": "4", "subject": "MATH", "name": "戊", "verdict": "REJECT"}),
        ])
        nodes = C._pack_nodes()
        errors = C.check_dir_batch(self.base / "verdicts" / "dir_batch_001.csv",
                                   self.base / "dir_batches" / "batch_001.jsonl", nodes)
        self.assertTrue(any("非法 verdict" in e for e in errors))
        problems = C.check_dir_batch(self.base / "verdicts" / "dir_batch_002.csv",
                                     self.base / "dir_batches" / "batch_001.jsonl", nodes)
        joined = "; ".join(problems)
        self.assertIn("绑定节点不存在", joined)
        self.assertIn("material_type", joined)
        self.assertIn("ASCII 双引号", joined)
        self.assertIn("例题", joined)
        self.assertIn("缺 reason", joined)
        self.assertIn("行数", joined)

    def test_main_exit_codes(self):
        _write_csv(self.base / "verdicts" / "dir_batch_000.csv", C.DIR_COLS, [
            _row(C.DIR_COLS, {"idx": "0", "subject": "MATH", "name": "甲", "verdict": "COVERED",
                              "evidence": "m-1"}),
            _row(C.DIR_COLS, {"idx": "1", "subject": "MATH", "name": "乙", "verdict": "REJECT",
                              "reason": "装饰串", "evidence": "封面/版权页"}),
        ])
        self.assertEqual(0, C.main(["--base", str(self.base)]))
        _write_csv(self.base / "verdicts" / "dir_batch_001.csv", C.DIR_COLS, [
            _row(C.DIR_COLS, {"idx": "2", "subject": "MATH", "name": "丙", "verdict": "COVERED"}),
        ])
        self.assertEqual(1, C.main(["--base", str(self.base)]))


if __name__ == "__main__":
    unittest.main()
