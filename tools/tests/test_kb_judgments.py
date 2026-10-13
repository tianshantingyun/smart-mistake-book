# -*- coding: utf-8 -*-
"""kb_coverage.judgments（判定表 5 片读写助手）的契约测试。

它盯的失败：判定表从单文件拆成按科目的 5 片后，全部 20 个读写点都改走这一个助手——
片归属的口径（`subject_of_path`，None→UNASSIGNED）、片间固定序、表头逐字 12 列、
`\\r\\n` 行尾、无 BOM，任意一条漂移都会让写入变成全表重写级 diff（十万行行尾翻转）
或让下游读到错片。这里用临时目录把每条契约钉住，全部不读真实判定表。

跑法（与仓库其余 tools/tests 一致）：
    python -m unittest discover -s tools/tests -t tools -p "test_kb_judgments.py"
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from kb_coverage import judgments


def _row(rel: str, cid: str, **over) -> dict:
    base = {c: "" for c in judgments.COLUMNS}
    base.update({"chunk_rel": rel, "chunk_id": cid, "action": "MATERIAL",
                 "node_slug": "n", "type": "CONCEPT_EXPLANATION",
                 "title": "t", "summary": "s", "content": "c", "boundary": "b"})
    base.update(over)
    return base


class PartOfTests(unittest.TestCase):
    """片归属 = `extraction_state.subject_of_path`，None（全科卷）→ UNASSIGNED。"""

    def test_four_subjects_and_fallback(self):
        self.assertEqual("MATH", judgments.part_of("数学/1.docx"))
        self.assertEqual("PHYSICS", judgments.part_of("物理/2.docx"))
        self.assertEqual("CHEMISTRY", judgments.part_of("化学/3.docx"))
        self.assertEqual("BIOLOGY", judgments.part_of("生物/4.docx"))
        # 一个路径部件里同时出现两个科目词（全科卷）→ 不猜，落 UNASSIGNED
        self.assertEqual("UNASSIGNED", judgments.part_of("全科卷/数学物理/5.docx"))
        self.assertEqual("UNASSIGNED", judgments.part_of(""))
        self.assertEqual("UNASSIGNED", judgments.part_of(None))

    def test_parts_constant_is_the_authority(self):
        self.assertEqual(("MATH", "PHYSICS", "CHEMISTRY", "BIOLOGY", "UNASSIGNED"),
                         judgments.PARTS)
        self.assertEqual(12, len(judgments.COLUMNS))
        self.assertTrue(judgments.parts_dir().name == "material_judgments")


class RoundTripTests(unittest.TestCase):
    """写-读逐字段相等 + 字节契约（5 片 / CRLF / 无 BOM / 表头逐字 12 列）。"""

    ROWS = [
        _row("数学/1.docx", "aaaa-001", content="第一行\n第二行（字段内换行）"),
        _row("物理/2.docx", "bbbb-001", title='带"引号"与,逗号的标题'),
        _row("化学/3.docx", "cccc-001", note="含回车\r的字段"),
        _row("生物/4.docx", "dddd-001", midx=" b "),     # midx 带空白：写出原样、键去空白
        _row("全科卷/数学物理/5.docx", "eeee-001"),
    ]

    def test_write_then_load_is_field_identical_and_parts_ordered(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td) / "material_judgments"
            counts = judgments.write_all(self.ROWS, base)
            self.assertEqual({p: 1 for p in judgments.PARTS}, counts)
            got = judgments.load_all(base)
            # 片间按 PARTS 序、片内保序：与输入逐字段相等
            self.assertEqual([dict(r) for r in self.ROWS], got)
            self.assertEqual(self.ROWS[0]["content"], got[0]["content"])

    def test_bytes_are_crlf_no_bom_header_verbatim(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td) / "material_judgments"
            judgments.write_all(self.ROWS, base)
            header = ",".join(judgments.COLUMNS).encode("utf-8")
            for part in judgments.PARTS:
                rows_p = [r for r in self.ROWS if judgments.part_of(r["chunk_rel"]) == part]
                raw = (base / f"{part}.csv").read_bytes()
                self.assertFalse(raw.startswith(b"\xef\xbb\xbf"), part)
                self.assertTrue(raw.startswith(header + b"\r\n"), part)
                # 记录终止符恒为 CRLF（= 1 表头 + 片内行数 + 字段内 CRLF）；
                # 字段内换行保持裸 LF（CSV 引号内原样），数得出来才算"没被改写"
                want_crlf = 1 + len(rows_p) + sum(v.count("\r\n") for r in rows_p for v in r.values())
                want_lf = sum(v.count("\n") for r in rows_p for v in r.values())
                self.assertEqual(want_crlf, raw.count(b"\r\n"), part)
                self.assertEqual(want_lf, raw.count(b"\n") - raw.count(b"\r\n"), part)
            # 反向钉住夹具本身能测到东西：确有 1 处字段内换行
            self.assertEqual(1, sum(v.count("\n") for r in self.ROWS for v in r.values()))

    def test_reload_then_rewrite_is_byte_identical(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td) / "material_judgments"
            judgments.write_all(self.ROWS, base)
            snap = {p.name: p.read_bytes() for p in sorted(base.glob("*.csv"))}
            judgments.write_all(judgments.load_all(base), base)
            self.assertEqual(snap, {p.name: p.read_bytes() for p in sorted(base.glob("*.csv"))})

    def test_empty_part_is_header_only_and_missing_part_is_empty(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td) / "material_judgments"
            judgments.write_all([_row("数学/1.docx", "aaaa-001")], base)
            self.assertEqual(1, len(judgments.load_all(base)))
            (base / "PHYSICS.csv").unlink()          # 片区迁移途中某片还没落盘
            self.assertEqual(1, len(judgments.load_all(base)))


class MissingTableTests(unittest.TestCase):
    """缺席语义：片目录不存在必须响亮失败（gate 靠它把"查不了"置红，不得静默判绿）。"""

    def test_missing_dir_raises(self):
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaises(FileNotFoundError):
                judgments.load_all(Path(td) / "absent")

    def test_single_csv_path_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            f = Path(td) / "j.csv"
            f.write_text("x", encoding="utf-8")
            with self.assertRaises(NotADirectoryError):
                judgments.load_all(f)
            with self.assertRaises(NotADirectoryError):
                judgments.write_all([], f)

    def test_bad_header_raises(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td) / "material_judgments"
            base.mkdir()
            (base / "MATH.csv").write_text("chunk_rel,chunk_id\nx,y\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                judgments.load_all(base)


class KeyTests(unittest.TestCase):

    def test_key_strips_midx_whitespace(self):
        self.assertEqual(("r", "c", "b"), judgments.key({"chunk_rel": "r", "chunk_id": "c", "midx": " b "}))
        self.assertEqual(("r", "c", ""), judgments.key({"chunk_rel": "r", "chunk_id": "c"}))


if __name__ == "__main__":
    unittest.main()
