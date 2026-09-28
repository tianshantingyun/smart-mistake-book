# -*- coding: utf-8 -*-
"""kb_coverage.prescreen_chunks 的契约测试。

覆盖四类机械可判 SKIP 判据（题目派生 / 非知识形态 / 过短 / 重复内容）与
"其余需模型判定 JUDGE"的分流、SKIP 理由文案，以及 `--json` 汇总输出
（含重复键 / 指纹重复 / 已判定键排除）。块池与判定表用临时文件打桩，
不读 18 万行真实块池。

跑法（与仓库其余 tools/tests 一致）：
    python -m unittest discover -s tools/tests -t tools -p "test_kb_prescreen_chunks.py"
"""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from kb_coverage import prescreen_chunks as pc

# 无任何机械触发词、去空白后 ≥ 40 字的知识叙述 → JUDGE
GOOD_TEXT = (
    "加速度是速度对时间的变化率，其方向与速度变化量的方向一致。"
    "加速度是矢量，在国际单位制中单位是米每二次方秒。"
)


def _write_pool(path: Path, records: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as fh:
        for r in records:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")


class ClassifyChunkTests(unittest.TestCase):
    """判据 1（题目派生）、判据 2（非知识形态）、判据 3（过短）与 JUDGE 路径。"""

    def test_plain_knowledge_is_judge(self):
        self.assertEqual(("JUDGE", ""), pc.classify_chunk("加速度", GOOD_TEXT))

    def test_title_is_exam_number_skips(self):
        verdict, why = pc.classify_chunk("（2025·全国甲卷）第 3 题", GOOD_TEXT)
        self.assertEqual(("SKIP", "题目派生：标题是题号/来源"), (verdict, why))

    def test_options_block_skips(self):
        verdict, why = pc.classify_chunk("", "A. 甲 B. 乙 C. 丙 D. 丁")
        self.assertEqual(("SKIP", "题目派生：正文含成套选项 A/B/C/D"), (verdict, why))

    def test_answer_marker_skips(self):
        verdict, why = pc.classify_chunk("", "该题答案为 C，故选 C 项")
        self.assertEqual(("SKIP", "题目派生：正文含答案/故选等判定语"), (verdict, why))

    def test_stem_tail_without_conclusion_skips(self):
        verdict, why = pc.classify_chunk("", "求物体的加速度")
        self.assertEqual(("SKIP", "题目派生：正文是短题干且无结论"), (verdict, why))

    def test_toc_dotted_lines_skips(self):
        text = "第一章 集合……1\n第二章 函数……12\n第三章 数列……35"
        self.assertEqual(("SKIP", "目录（点线+页码）"), pc.classify_chunk("", text))

    def test_copyright_page_skips(self):
        text = "ISBN 978-7-107-33560-2 定价：68.00 元 版权所有 翻印必究"
        self.assertEqual(("SKIP", "版权/出版信息"), pc.classify_chunk("", text))

    def test_ad_text_skips(self):
        text = "扫码关注公众号，免费领取全套真题，限时优惠"
        self.assertEqual(("SKIP", "广告宣传"), pc.classify_chunk("", text))

    def test_nav_page_number_skips(self):
        self.assertEqual(("SKIP", "纯页码/导航行"), pc.classify_chunk("第 12 页", ""))

    def test_empty_block_skips(self):
        self.assertEqual(("SKIP", "空块"), pc.classify_chunk("", "   \n "))

    def test_placeholder_page_skips(self):
        self.assertEqual(("SKIP", "空白/占位页"), pc.classify_chunk("", "本页无正文"))

    def test_too_short_skips_with_char_count(self):
        self.assertEqual(("SKIP", "过短（10 字 < 40）"), pc.classify_chunk("", "质点做匀加速直线运动"))


class MainJsonTests(unittest.TestCase):
    """判据 4（重复键 / 指纹重复）、已判定键排除与 `--json` 输出。"""

    def _pool(self) -> list[dict]:
        return [
            # 题目派生（标题题号）→ SKIP
            {"rel_path": "srcA/1.docx", "chunk_id": "c1", "fp": "fp1",
             "heading": "例 1", "text": "某质点沿直线运动，求其位移大小。"},
            # 知识叙述 → JUDGE
            {"rel_path": "srcA/1.docx", "chunk_id": "c2", "fp": "fp2",
             "heading": "加速度", "text": GOOD_TEXT},
            # 同 (源,chunk_id) 重复行（fp 空 → 走重复键分支）→ SKIP-重复键
            {"rel_path": "srcA/1.docx", "chunk_id": "c2", "fp": "",
             "heading": "加速度", "text": "重复键行"},
            # 同内容指纹重复（fp2 已见）→ SKIP-重复，只留首次
            {"rel_path": "srcB/2.docx", "chunk_id": "c4", "fp": "fp2",
             "heading": "无关标题", "text": "重复指纹内容……"},
            # 空块 → SKIP
            {"rel_path": "srcB/2.docx", "chunk_id": "c5", "fp": "",
             "heading": "", "text": "  "},
            # 已判定键（在 material_judgments.csv 里）→ 整行排除
            {"rel_path": "srcA/1.docx", "chunk_id": "c0", "fp": "fp0",
             "heading": "已判定", "text": GOOD_TEXT},
        ]

    def test_json_report_counts_reasons_and_sources(self):
        with tempfile.TemporaryDirectory() as td:
            pool = Path(td) / "chunks.jsonl"
            judged = Path(td) / "judged.csv"
            judged.write_text(
                "chunk_rel,chunk_id,action,node_slug,type,title,summary,applicability,content,boundary,note\n"
                "srcA/1.docx,c0,MATERIAL,某节点,CONCEPT_EXPLANATION,t,s,a,c,b,n\n",
                encoding="utf-8-sig",
            )
            _write_pool(pool, self._pool())
            out = Path(td) / "nested" / "report.json"
            with mock.patch.object(pc, "CHUNKS", pool), mock.patch.object(pc, "JUDGMENTS", judged):
                rc = pc.main(["--json", str(out)])
            self.assertEqual(0, rc)
            payload = json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual(6, payload["rows"])
            self.assertEqual(5, payload["unjudged"])  # 已判定键 c0 被排除
            self.assertEqual(1, payload["judge"])
            self.assertEqual(4, payload["skip"])
            self.assertEqual(1, payload["dup_rows"])
            self.assertEqual(1, payload["reasons"]["题目派生：标题是题号/来源"])
            self.assertEqual(1, payload["reasons"]["同 (源,chunk_id) 重复行"])
            self.assertEqual(1, payload["reasons"]["同内容指纹重复（只留首次）"])
            self.assertEqual(1, payload["reasons"]["空块"])
            self.assertEqual({"SKIP": 1, "JUDGE": 1, "SKIP-重复键": 1},
                             payload["by_source"]["srcA"])
            self.assertEqual({"SKIP-重复": 1, "SKIP": 1}, payload["by_source"]["srcB"])

    def test_json_parent_dir_created_and_skip_invariant(self):
        with tempfile.TemporaryDirectory() as td:
            pool = Path(td) / "chunks.jsonl"
            _write_pool(pool, self._pool())
            out = Path(td) / "a" / "b" / "report.json"
            with mock.patch.object(pc, "CHUNKS", pool), \
                    mock.patch.object(pc, "JUDGMENTS", Path(td) / "absent.csv"):
                rc = pc.main(["--json", str(out)])
            self.assertEqual(0, rc)
            payload = json.loads(out.read_text(encoding="utf-8"))
            # 无已判定表时全部 6 块未判定：2 JUDGE + 4 SKIP
            self.assertEqual(6, payload["unjudged"])
            self.assertEqual(2, payload["judge"])
            self.assertEqual(4, payload["skip"])
            self.assertEqual(payload["unjudged"], payload["judge"] + payload["skip"])

    def test_main_without_json_flag_returns_zero(self):
        with tempfile.TemporaryDirectory() as td:
            pool = Path(td) / "chunks.jsonl"
            _write_pool(pool, self._pool())
            with mock.patch.object(pc, "CHUNKS", pool), \
                    mock.patch.object(pc, "JUDGMENTS", Path(td) / "absent.csv"):
                self.assertEqual(0, pc.main([]))


if __name__ == "__main__":
    unittest.main()
