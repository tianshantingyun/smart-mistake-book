# -*- coding: utf-8 -*-
"""`kb_build.gate.exam_leak` 门的契约测试。

它盯的失败：题目形态的材料（题干/选项/答案/解析改写）在 CI 里**没有任何守卫**——
`kb_coverage.check_exam_leak` 一直只是离线检查（实测 exit 1 也照常入包）。
本门把同一套判据接进 `gate.evaluate()`，容差 0：只要有一条**未豁免**命中就红。

两侧都钉住：
- 正侧：仓库当前状态（6 条真题派生转 SKIP、295 条逐条豁免后）门值必须是 0；
- 反侧：未豁免命中 ⇒ 1 且 detail 指得出是哪条；豁免**指纹不符不生效**；
  判定表缺失 ⇒ 报"检查不可执行"而不是静默变绿；豁免表重复键 ⇒ 拒绝。

测试用临时判定表/豁免表打桩（patch `check_exam_leak.JUDGMENTS/EXEMPTIONS`），
只有正侧读仓库真表。
"""
from __future__ import annotations

import csv
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from kb_build import gate
from kb_coverage import check_exam_leak as cel

COLS = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title",
        "summary", "applicability", "content", "boundary", "note", "midx"]
EX_COLS = ["chunk_rel", "chunk_id", "midx", "material_slug", "fields_fp",
           "class", "reason", "reviewed_at"]

CLEAN = "加速度是速度对时间的变化率，其方向与速度变化量的方向一致。" * 3
HIT = "本题答案为 C，故选 C。" + CLEAN


def _write_judgments(path: Path, rows: list[list[str]]) -> Path:
    with path.open("w", encoding="utf-8-sig", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(COLS)
        w.writerows(rows)
    return path


def _row(chunk_id: str, slug: str, content: str, midx: str = "") -> list[str]:
    return ["srcA/1.docx", chunk_id, "MATERIAL", slug, "CONCEPT_EXPLANATION",
            "加速度", "概要", "适用性", content, "边界", "注", midx]


def _fp(content: str) -> str:
    return cel.fields_fp({"title": "加速度", "content": content,
                          "summary": "概要", "applicability": "适用性"})


def _write_exemptions(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=EX_COLS)
        w.writeheader()
        w.writerows(rows)


def _ex(chunk_id: str, fp: str) -> dict:
    return {"chunk_rel": "srcA/1.docx", "chunk_id": chunk_id, "midx": "",
            "material_slug": f"ext-mat-x-{chunk_id}", "fields_fp": fp,
            "class": "answer_meta", "reason": "方法材料里的作答指引，非题目答案",
            "reviewed_at": "2026-10-11"}


class ExamLeakGateTest(unittest.TestCase):

    def test_metric_key_and_repo_state_is_zero(self):
        """正侧：清理后的仓库状态（转 SKIP 6 + 逐条豁免 295）门值必须为 0。

        这个 0 是 2026-10-11 清理的收口读数；若日后有新命中且未复核，本断言必须变红。
        """
        m = gate.exam_leak_metric()
        self.assertEqual("exam_leak", m.key)
        self.assertEqual(0, m.value, m.detail)
        self.assertTrue(m.ok)

    def test_unexcused_hit_is_red_and_points_at_row(self):
        with tempfile.TemporaryDirectory() as td:
            path = _write_judgments(Path(td) / "j.csv", [_row("c1", "力的合成", HIT)])
            with mock.patch.object(cel, "JUDGMENTS", path), \
                    mock.patch.object(cel, "EXEMPTIONS", Path(td) / "none.csv"):
                m = gate.exam_leak_metric()
        self.assertEqual(1, m.value)
        self.assertTrue(any("力的合成" in line and "c1" in line for line in m.detail))

    def test_exempted_hit_is_green(self):
        with tempfile.TemporaryDirectory() as td:
            path = _write_judgments(Path(td) / "j.csv", [_row("c1", "力的合成", HIT)])
            ex_path = Path(td) / "e.csv"
            _write_exemptions(ex_path, [_ex("c1", _fp(HIT))])
            with mock.patch.object(cel, "JUDGMENTS", path), \
                    mock.patch.object(cel, "EXEMPTIONS", ex_path):
                m = gate.exam_leak_metric()
        self.assertEqual(0, m.value)
        self.assertTrue(m.ok)

    def test_fingerprint_mismatch_makes_it_red_again(self):
        """豁免只覆盖复核过的那版文本；文本改写后指纹不符 ⇒ 重新计入。"""
        with tempfile.TemporaryDirectory() as td:
            path = _write_judgments(Path(td) / "j.csv", [_row("c1", "力的合成", HIT)])
            ex_path = Path(td) / "e.csv"
            _write_exemptions(ex_path, [_ex("c1", "0" * 16)])
            with mock.patch.object(cel, "JUDGMENTS", path), \
                    mock.patch.object(cel, "EXEMPTIONS", ex_path):
                m = gate.exam_leak_metric()
        self.assertEqual(1, m.value)

    def test_missing_judgment_table_is_not_silently_green(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "nope.csv"
            with mock.patch.object(cel, "JUDGMENTS", path):
                m = gate.exam_leak_metric()
        self.assertEqual(1, m.value)
        self.assertTrue(any("检查不可执行" in line for line in m.detail))

    def test_duplicate_exemption_key_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            path = _write_judgments(Path(td) / "j.csv", [_row("c1", "力的合成", HIT)])
            ex_path = Path(td) / "e.csv"
            _write_exemptions(ex_path, [_ex("c1", _fp(HIT)), _ex("c1", _fp(HIT))])
            with mock.patch.object(cel, "JUDGMENTS", path), \
                    mock.patch.object(cel, "EXEMPTIONS", ex_path):
                m = gate.exam_leak_metric()
        self.assertEqual(1, m.value)
        self.assertTrue(any("检查不可执行" in line for line in m.detail))

    def test_clean_rows_are_green(self):
        with tempfile.TemporaryDirectory() as td:
            path = _write_judgments(Path(td) / "j.csv", [
                _row("c1", "加速度", CLEAN),
                ["srcA/1.docx", "c2", "SKIP", "", "", "", "", "", "", "", "", ""],
            ])
            with mock.patch.object(cel, "JUDGMENTS", path), \
                    mock.patch.object(cel, "EXEMPTIONS", Path(td) / "none.csv"):
                m = gate.exam_leak_metric()
        self.assertEqual(0, m.value)
        self.assertEqual([], m.detail)


if __name__ == "__main__":
    unittest.main()
