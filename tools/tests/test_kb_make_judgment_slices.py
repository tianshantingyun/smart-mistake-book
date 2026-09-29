# -*- coding: utf-8 -*-
"""kb_coverage.make_judgment_slices 的契约测试。

覆盖：未判定键分流后只切 JUDGE 类、按来源分组、每片 ≤ --size、行 schema、
文件名稳定、重跑产物一致（确定性/幂等）、--pilot 每来源只出第 1 片、
--list 只打印路径不写盘、mechanical_skips.csv schema 同判定表且 action=SKIP、
重判队列键按未判定处理、已判定键排除。块池/判定表/重判队列/输出目录全部
用临时文件打桩，不读 18 万行真实块池。

跑法（与仓库其余 tools/tests 一致）：
    python -m unittest discover -s tools/tests -t tools -p "test_kb_make_judgment_slices.py"
"""

from __future__ import annotations

import csv
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from kb_coverage import make_judgment_slices as mjs
from kb_coverage import prescreen_chunks as pc

GOOD_TEXT = (
    "加速度是速度对时间的变化率，其方向与速度变化量的方向一致。"
    "加速度是矢量，在国际单位制中单位是米每二次方秒。"
)
JUDGMENT_COLS = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title",
                 "summary", "applicability", "content", "boundary", "note", "midx"]


def _write_pool(path: Path, records: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as fh:
        for r in records:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")


def _write_csv(path: Path, cols: list[str], rows: list[list[str]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(cols)
        w.writerows(rows)


def _pool_fixture() -> list[dict]:
    """两来源 6 块：2 JUDGE（srcA）、2 JUDGE（srcB）、1 题目 SKIP、1 重复键 SKIP。"""
    return [
        {"rel_path": "srcA/1.docx", "chunk_id": "a1", "fp": "fp1", "heading": "加速度", "text": GOOD_TEXT},
        {"rel_path": "srcA/1.docx", "chunk_id": "a2", "fp": "fp2", "heading": "速度", "text": GOOD_TEXT},
        {"rel_path": "srcB/2.docx", "chunk_id": "b1", "fp": "fp3", "heading": "加速度", "text": GOOD_TEXT},
        {"rel_path": "srcB/2.docx", "chunk_id": "b2", "fp": "fp4", "heading": "加速度", "text": GOOD_TEXT},
        # 题目派生 → 机械 SKIP（进 mechanical_skips.csv，不进切片）
        {"rel_path": "srcA/1.docx", "chunk_id": "a3", "fp": "fp5", "heading": "例 1", "text": GOOD_TEXT},
        # 同 (源,chunk_id) 重复行 → SKIP-重复键
        {"rel_path": "srcA/1.docx", "chunk_id": "a2", "fp": "", "heading": "速度", "text": "重复键行"},
    ]


class SliceOutputTests(unittest.TestCase):
    """分组 / 每片 ≤ size / 行 schema / mechanical_skips schema。"""

    def _run(self, td: str, argv: list[str]) -> int:
        pool = Path(td) / "chunks.jsonl"
        judged = Path(td) / "judged.csv"
        rejudge = Path(td) / "rejudge.csv"
        slices = Path(td) / "slices"
        skips = Path(td) / "mechanical_skips.csv"
        _write_pool(pool, _pool_fixture())
        _write_csv(judged, JUDGMENT_COLS, [])
        _write_csv(rejudge, ["chunk_rel", "chunk_id", "midx", "node_slug", "type", "note"], [])
        with mock.patch.object(pc, "CHUNKS", pool), \
                mock.patch.object(pc, "JUDGMENTS", judged), \
                mock.patch.object(pc, "REJUDGE", rejudge), \
                mock.patch.object(mjs, "SLICES_DIR", slices), \
                mock.patch.object(mjs, "MECH_SKIPS", skips):
            return mjs.main(argv)

    def test_grouping_size_and_row_schema(self):
        with tempfile.TemporaryDirectory() as td:
            rc = self._run(td, ["--size", "2"])
            self.assertEqual(0, rc)
            slices = Path(td) / "slices"
            files = sorted(p.name for p in slices.glob("*.jsonl"))
            # srcA 2 JUDGE 块 → 1 片；srcB 2 JUDGE 块 → 1 片
            self.assertEqual(["srcA__1.jsonl", "srcB__1.jsonl"], files)
            for name in ("srcA__1.jsonl", "srcB__1.jsonl"):
                rows = [json.loads(l) for l in (slices / name).read_text(encoding="utf-8").splitlines()]
                self.assertLessEqual(len(rows), 2)
                for r in rows:
                    self.assertEqual(["chunk_rel", "chunk_id", "heading", "text", "subject", "fp"],
                                     list(r.keys()))
            # 每片 2 块：srcA 只含 a1/a2，srcB 只含 b1/b2（题目 SKIP 块 a3 不进切片）
            got = sorted(r["chunk_id"] for r in
                         (json.loads(l) for l in (slices / "srcA__1.jsonl").read_text(encoding="utf-8").splitlines()))
            self.assertEqual(["a1", "a2"], got)

    def test_mechanical_skips_schema_and_action(self):
        with tempfile.TemporaryDirectory() as td:
            rc = self._run(td, ["--size", "10"])
            self.assertEqual(0, rc)
            skips = Path(td) / "mechanical_skips.csv"
            rows = list(csv.DictReader(skips.open(encoding="utf-8-sig", newline="")))
            self.assertEqual(JUDGMENT_COLS, list(rows[0].keys()))
            self.assertEqual({"SKIP"}, {r["action"] for r in rows})
            notes = {r["chunk_id"]: r["note"] for r in rows}
            self.assertEqual("题目派生：标题是题号/来源", notes["a3"])
            self.assertEqual("同 (源,chunk_id) 重复行", notes["a2"])
            # JUDGE 块绝不进 SKIP 清单
            self.assertNotIn("a1", notes)


class DeterminismPilotListTests(unittest.TestCase):
    """确定性（重跑产物一致）、--pilot、--list。"""

    def _paths(self, td: str):
        return (Path(td) / "chunks.jsonl", Path(td) / "judged.csv", Path(td) / "rejudge.csv",
                Path(td) / "slices", Path(td) / "mechanical_skips.csv")

    def _fixture(self, td: str, argv: list[str]):
        pool, judged, rejudge, slices, skips = self._paths(td)
        _write_pool(pool, [
            # srcA：5 个 JUDGE 块 → size=2 时 3 片
            *[{"rel_path": "srcA/1.docx", "chunk_id": f"a{i}", "fp": f"fp{i}",
               "heading": "加速度", "text": GOOD_TEXT} for i in range(1, 6)],
            {"rel_path": "srcB/2.docx", "chunk_id": "b1", "fp": "fpb", "heading": "加速度", "text": GOOD_TEXT},
        ])
        _write_csv(judged, JUDGMENT_COLS, [])
        _write_csv(rejudge, ["chunk_rel", "chunk_id", "midx", "node_slug", "type", "note"], [])
        with mock.patch.object(pc, "CHUNKS", pool), \
                mock.patch.object(pc, "JUDGMENTS", judged), \
                mock.patch.object(pc, "REJUDGE", rejudge), \
                mock.patch.object(mjs, "SLICES_DIR", slices), \
                mock.patch.object(mjs, "MECH_SKIPS", skips):
            return mjs.main(argv)

    def test_rerun_is_byte_identical(self):
        with tempfile.TemporaryDirectory() as td:
            self.assertEqual(0, self._fixture(td, ["--size", "2"]))
            _, _, _, slices, skips = self._paths(td)
            first = {p.name: p.read_bytes() for p in slices.glob("*.jsonl")}
            first["skips"] = skips.read_bytes()
            self.assertEqual(0, self._fixture(td, ["--size", "2"]))
            second = {p.name: p.read_bytes() for p in slices.glob("*.jsonl")}
            second["skips"] = skips.read_bytes()
            self.assertEqual(first, second)

    def test_pilot_only_first_slice_per_source(self):
        with tempfile.TemporaryDirectory() as td:
            self.assertEqual(0, self._fixture(td, ["--size", "2", "--pilot"]))
            _, _, _, slices, _ = self._paths(td)
            self.assertEqual(sorted(["srcA__1.jsonl", "srcB__1.jsonl"]),
                             sorted(p.name for p in slices.glob("*.jsonl")))
            rows = [json.loads(l) for l in (slices / "srcA__1.jsonl").read_text(encoding="utf-8").splitlines()]
            self.assertEqual(2, len(rows))  # 只切第 1 片，不是全部 5 块

    def test_list_prints_paths_and_writes_nothing(self):
        with tempfile.TemporaryDirectory() as td:
            import contextlib
            import io

            pool, judged, rejudge, slices, skips = self._paths(td)
            _write_pool(pool, [
                *[{"rel_path": "srcA/1.docx", "chunk_id": f"a{i}", "fp": f"fp{i}",
                   "heading": "加速度", "text": GOOD_TEXT} for i in range(1, 6)],
                {"rel_path": "srcB/2.docx", "chunk_id": "b1", "fp": "fpb", "heading": "加速度", "text": GOOD_TEXT},
            ])
            _write_csv(judged, JUDGMENT_COLS, [])
            _write_csv(rejudge, ["chunk_rel", "chunk_id", "midx", "node_slug", "type", "note"], [])
            buf = io.StringIO()
            with mock.patch.object(pc, "CHUNKS", pool), \
                    mock.patch.object(pc, "JUDGMENTS", judged), \
                    mock.patch.object(pc, "REJUDGE", rejudge), \
                    mock.patch.object(mjs, "SLICES_DIR", slices), \
                    mock.patch.object(mjs, "MECH_SKIPS", skips), \
                    contextlib.redirect_stdout(buf):
                rc = mjs.main(["--size", "2", "--list"])
            self.assertEqual(0, rc)
            lines = buf.getvalue().splitlines()
            self.assertEqual(["srcA__1.jsonl", "srcA__2.jsonl", "srcA__3.jsonl", "srcB__1.jsonl"],
                             [Path(l).name for l in lines])
            self.assertFalse(slices.exists())   # --list 不写盘
            self.assertFalse(skips.exists())    # 机械 SKIP 清单也不写


class JudgedAndRejudgeTests(unittest.TestCase):
    """已判定键排除、重判队列键按未判定处理。"""

    def test_rejudge_queue_key_is_sliced(self):
        with tempfile.TemporaryDirectory() as td:
            pool = Path(td) / "chunks.jsonl"
            judged = Path(td) / "judged.csv"
            rejudge = Path(td) / "rejudge.csv"
            slices = Path(td) / "slices"
            skips = Path(td) / "mechanical_skips.csv"
            _write_pool(pool, [
                # q1：判定表里有（坏 type），重判队列也有 → 按未判定处理，JUDGE 类应进切片
                {"rel_path": "srcA/1.docx", "chunk_id": "q1", "fp": "fpq1", "heading": "加速度", "text": GOOD_TEXT},
                # j1：判定表里有、不在队列 → 已判定，排除
                {"rel_path": "srcA/1.docx", "chunk_id": "j1", "fp": "fpj1", "heading": "加速度", "text": GOOD_TEXT},
            ])
            _write_csv(judged, JUDGMENT_COLS, [
                ["srcA/1.docx", "q1", "SKIP", "某节点", "DERIVATION", "", "", "", "", "", "", ""],
                ["srcA/1.docx", "j1", "MATERIAL", "某节点", "CONCEPT_EXPLANATION", "t", "s", "a", "c", "b", "n", ""],
            ])
            _write_csv(rejudge, ["chunk_rel", "chunk_id", "midx", "node_slug", "type", "note"],
                       [["srcA/1.docx", "q1", "", "某节点", "DERIVATION", "待重判"]])
            with mock.patch.object(pc, "CHUNKS", pool), \
                    mock.patch.object(pc, "JUDGMENTS", judged), \
                    mock.patch.object(pc, "REJUDGE", rejudge), \
                    mock.patch.object(mjs, "SLICES_DIR", slices), \
                    mock.patch.object(mjs, "MECH_SKIPS", skips):
                rc = mjs.main(["--size", "10"])
            self.assertEqual(0, rc)
            rows = [json.loads(l) for l in (slices / "srcA__1.jsonl").read_text(encoding="utf-8").splitlines()]
            self.assertEqual(["q1"], [r["chunk_id"] for r in rows])


class PriorityOrderTests(unittest.TestCase):
    """53 知识清单来源最高优先：切片清单顺序即判定队列顺序（用户 2026-09-30 指定）。"""

    def test_priority_source_sorts_first(self):
        self.assertLess(mjs.source_priority("2027版高中《53知识清单》彩色版（数学）"),
                        mjs.source_priority("2026年新高考资料"))
        self.assertLess(mjs.source_priority("53知识清单·化学"), mjs.source_priority("27版五三"))

    def test_list_puts_priority_source_first(self):
        with tempfile.TemporaryDirectory() as td:
            import contextlib
            import io

            pool = Path(td) / "chunks.jsonl"
            judged = Path(td) / "judged.csv"
            rejudge = Path(td) / "rejudge.csv"
            slices = Path(td) / "slices"
            skips = Path(td) / "mechanical_skips.csv"
            _write_pool(pool, [
                {"rel_path": "2026年新高考资料/1.docx", "chunk_id": "x1", "fp": "fx",
                 "heading": "加速度", "text": GOOD_TEXT},
                {"rel_path": "2027版高中《53知识清单》彩色版（数学）/a.pdf", "chunk_id": "m1",
                 "fp": "fm", "heading": "知识点01", "text": GOOD_TEXT},
            ])
            _write_csv(judged, JUDGMENT_COLS, [])
            _write_csv(rejudge, ["chunk_rel", "chunk_id", "midx", "node_slug", "type", "note"], [])
            buf = io.StringIO()
            with mock.patch.object(pc, "CHUNKS", pool), \
                    mock.patch.object(pc, "JUDGMENTS", judged), \
                    mock.patch.object(pc, "REJUDGE", rejudge), \
                    mock.patch.object(mjs, "SLICES_DIR", slices), \
                    mock.patch.object(mjs, "MECH_SKIPS", skips), \
                    contextlib.redirect_stdout(buf):
                rc = mjs.main(["--size", "10", "--list"])
            self.assertEqual(0, rc)
            names = [Path(line).name for line in buf.getvalue().splitlines()]
            self.assertTrue(names[0].startswith("2027版高中"), names)


if __name__ == "__main__":
    unittest.main()
