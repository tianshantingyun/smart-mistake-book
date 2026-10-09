# -*- coding: utf-8 -*-
"""prescreen_chunks 机械 SKIP 误杀修复的回归夹具（批次 6 根因报告 `build/agent-batch6/skip-rootcause.md` §4）。

三层夹具：
1. **真实块池夹具**（P1–P4 / N1–N5）：键 = `(rel_path, chunk_id)`，块正文取自
   `tools/kb_coverage/tables/extracted_chunks.rekeyed.jsonl`。该数据表不随仓库分发，
   缺失时整类跳过（`skipUnless`）——不做假通过；在本机（表在）即真实门。
2. **合成缺陷夹具**（F1 略$ / F2 限时 / F3 CIP / R1 判定单位 / R5 完整结论式）：
   不读池，任何环境都跑。
3. **合成池夹具**（N6 指纹重复 / N7 死分支 / F4 指纹取最全）：合成 jsonl 池驱动 `iter_classified`
   （指纹与重复键判据不在 `classify_chunk` 里，混用会测错层）。

跑法（与仓库其余 tools/tests 一致）：
    python -m unittest discover -s tools/tests -t tools -p "test_kb_prescreen*"
"""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from kb_build import audit_material_examples as ame
from kb_coverage import prescreen_chunks as pc

POOL = pc.CHUNKS

# —— P1：§1.1 表 16 条「成套选项」层确认误杀（改判 JUDGE） ——
P1_FALSE_KILLS = [
    "6c1d89cf2e-002", "264caef48f-010", "0ba0a43cf3-015", "ad2b24e970-003",
    "eb1f1b53d7-005", "fe528a7719-026", "9704f0896c-027", "27fd5f3de3-004",
    "2bd8a51c1f-002", "393432dd9c-002", "146a58287c-002", "f88c50d572-010",
    "c47ae8fe32-025", "f7eb7b44db-003", "03dce24682-002", "90a8086046-008",
]
# —— P2/P3：过短层确认误杀（同一「1 电荷」条目被切成 5 块） ——
P2_SHORT_FALSE_KILL = "e14aad82a5-775"          # 38 字
P3_SHORT_FALSE_KILL = "e14aad82a5-777"          # 30 字（报告未抽样，本轮新增）
P4_GROUP = ["e14aad82a5-775", "e14aad82a5-776", "e14aad82a5-777",
            "e14aad82a5-778", "e14aad82a5-779"]
# —— N1：同判据真题目（成套选项层 AGREE，全部实点 AGREE） ——
N1_TRUE_QUESTIONS = [
    "da7d35e7bf-007", "d500d05f94-005", "3e93e59a6a-058", "978cc4f08a-003",
    "7ba48831b9-046", "0fa89d2980-017", "d7969fb2b7-010", "3d7cc92736-011",
    "b7725e82a9-011", "685a4a9f63-055", "2210514519-019", "00d25adc90-017",
]
# —— N2/N3：真目录 / 真版权 ——
N2_TRUE_TOC = "5f510cdc4a-039"
N3_TRUE_COPYRIGHT = "289deb9ecd-1452"
# —— N4：触发词巧合但内容仍是题（限时 ×5、CIP 子串 ×2） ——
# `04fac7cba4-037`（DCIP）是唯一例外：CIP 词边界修好后池内**没有**其他判据接管它
# （`ame.classify` 对它返回 False：PPT 被切成一行一词，选项/答案判据都抓不到），
# 所以它由 SKIP 变 JUDGE（报告 §5 D3 的实测代价，见 gaps；不写成断言以免假装通过）。
N4_COINCIDENCE = ["0e96498e01-024", "5e92b9e392-051", "370fdfaa09-019",
                  "606e8ae960-012", "f87ab34a8e-008", "294376c3d7-236"]
N4_REASON_TAKEOVER = ["0e96498e01-024", "5e92b9e392-051", "370fdfaa09-019", "f87ab34a8e-008"]
# —— N5：过短层真非知识 ——
N5_SHORT_NON_KNOWLEDGE = ["445f88143b-001", "4f82967fc2-027", "d8f896c2a3-017"]


def _load_pool_by_id() -> dict[str, dict]:
    """按 chunk_id 建索引；id 重复会在这里暴露（夹具键 = (rel_path, chunk_id)，此处再校验唯一）。"""
    out: dict[str, dict] = {}
    with POOL.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            rec = json.loads(line)
            cid = (rec.get("chunk_id") or "").strip()
            if cid:
                out[cid] = rec
    return out


@unittest.skipUnless(POOL.exists(), f"块池数据表不存在：{POOL}")
class RealPoolFalseKillTests(unittest.TestCase):
    """P1–P4：17 条确认误杀改判 JUDGE（旧逻辑下必 SKIP）。"""

    @classmethod
    def setUpClass(cls):
        cls.pool = _load_pool_by_id()

    def _classify(self, cid: str) -> tuple[str, str]:
        rec = self.pool[cid]
        return pc.classify_chunk(rec.get("heading") or "", rec.get("text") or "")

    def test_p1_option_layer_false_kills_now_judge(self):
        """16 条「正文含成套选项」误杀：块内有知识段 → 送模型判定。"""
        for cid in P1_FALSE_KILLS:
            rec = self.pool[cid]
            # 夹具真实性：题目判据确实命中（旧逻辑就是因此把它判 SKIP 的）
            is_example, why = ame.classify({"title": rec.get("heading") or "",
                                            "contentMarkdown": rec.get("text") or ""})
            self.assertTrue(is_example, f"{cid} 不再命中题目判据，夹具失效")
            self.assertIn("成套选项", why, f"{cid} 命中判据不是成套选项：{why}")
            self.assertGreaterEqual(pc.knowledge_span_chars(rec.get("heading") or "",
                                                            rec.get("text") or ""), pc.MIN_CHARS)
            self.assertEqual(("JUDGE", ""), self._classify(cid), cid)

    def test_p2_p3_short_complete_statements_now_judge(self):
        """过短层误杀：38 字与 30 字的完整定义/规律句不再判过短。"""
        for cid in (P2_SHORT_FALSE_KILL, P3_SHORT_FALSE_KILL):
            rec = self.pool[cid]
            stripped = pc.re.sub(r"\s+", "", f"{rec.get('heading') or ''}\n{rec.get('text') or ''}")
            self.assertLess(len(stripped), pc.MIN_CHARS, f"{cid} 字数已 ≥ 门槛，夹具失效")
            self.assertTrue(stripped.endswith("。"), cid)
            self.assertEqual(("JUDGE", ""), self._classify(cid), cid)

    def test_p4_same_heading_group_has_no_too_short_skip(self):
        """同 heading「1 电荷」的 5 块：组内不得再有「过短」SKIP。"""
        for cid in P4_GROUP:
            verdict, why = self._classify(cid)
            self.assertNotEqual("SKIP", verdict, f"{cid} 仍被机械判掉：{why}")
            self.assertNotIn("过短", why, cid)


@unittest.skipUnless(POOL.exists(), f"块池数据表不存在：{POOL}")
class RealPoolNegativeTests(unittest.TestCase):
    """N1–N5：形态相近的真题目/目录/版权/广告/残句必须仍 SKIP（防止修过头）。"""

    @classmethod
    def setUpClass(cls):
        cls.pool = _load_pool_by_id()

    def _classify(self, cid: str) -> tuple[str, str]:
        rec = self.pool[cid]
        return pc.classify_chunk(rec.get("heading") or "", rec.get("text") or "")

    def test_n1_true_questions_still_skip_as_option_derived(self):
        for cid in N1_TRUE_QUESTIONS:
            self.assertEqual(("SKIP", "题目派生：正文含成套选项 A/B/C/D"),
                             self._classify(cid), cid)

    def test_n2_true_toc_still_skips(self):
        self.assertEqual(("SKIP", "目录（点线+页码）"), self._classify(N2_TRUE_TOC))

    def test_n3_true_copyright_still_skips(self):
        self.assertEqual(("SKIP", "版权/出版信息"), self._classify(N3_TRUE_COPYRIGHT))

    def test_n4_coincidence_trigger_words_still_skip(self):
        """触发词巧合（限时 / CIP 子串）的块仍 SKIP；能由题目判据接管的，理由由它接管。"""
        for cid in N4_COINCIDENCE:
            verdict, why = self._classify(cid)
            self.assertEqual("SKIP", verdict, f"{cid} 被放行：{why}")
        for cid in N4_REASON_TAKEOVER:
            self.assertEqual("题目派生：正文含成套选项 A/B/C/D", self._classify(cid)[1], cid)

    def test_n5_non_knowledge_short_blocks_still_skip_too_short(self):
        for cid in N5_SHORT_NON_KNOWLEDGE:
            verdict, why = self._classify(cid)
            self.assertEqual("SKIP", verdict, cid)
            self.assertIn("过短", why, f"{cid} 理由漂移：{why}")


class SegmentUnitTests(unittest.TestCase):
    """R1（判定单位从块降到段）+ R5（完整结论式）的合成夹具。"""

    def test_mixed_page_knowledge_plus_example_is_judged(self):
        """讲义正文 + 随附例题的混排页：一道随附例题不得否决整页知识。"""
        text = (
            "考点一　磁场及其对电流的作用\n"
            "1.磁场的基本性质：磁场对放入其中的磁体或通电导线有力的作用，"
            "磁感应强度是描述磁场强弱和方向的物理量。\n"
            "【例1】（2025·全国甲卷）下列说法正确的是（　　）\n"
            "A．甲　B．乙　C．丙　D．丁\n"
            "【答案】A\n"
        )
        self.assertEqual(("JUDGE", ""), pc.classify_chunk("", text))

    def test_knowledge_at_block_head_is_judged(self):
        """块首就是知识正文（标题是内容自带的）也算知识段。"""
        text = (
            "3．浓硫酸的作用：a．催化剂——加快反应速率；b．吸水剂——除去生成物中的水。\n"
            "4．饱和Na2CO3溶液的作用：中和挥发出的乙酸，溶解挥发出的乙醇。\n"
            "【例1】实验室用乙酸、丁醇制备乙酸丁酯。下列说法正确的是（　　）\n"
            "A．甲　B．乙　C．丙　D．丁\n"
        )
        self.assertEqual(("JUDGE", ""), pc.classify_chunk("", text))

    def test_pure_question_page_still_skips(self):
        """纯题目页（题干 + 选项 + 答案）：没有知识段，仍判题目派生。"""
        text = (
            "【例13】（2025·山东临沂·三模）空间存在一匀强电场，下列说法正确的是（　　）\n"
            "A．甲\nB．乙\nC．丙\nD．丁\n"
        )
        self.assertEqual(("SKIP", "题目派生：正文含成套选项 A/B/C/D"), pc.classify_chunk("", text))

    def test_answer_and_analysis_page_still_skips(self):
        """答案 + 解析 + 题干 + 选项（d500d05f94-005 形态）：解析续文不算知识段。"""
        text = (
            "答案　B\n"
            "解析　小球由静止摆至最低点，由机械能守恒定律有mgl＝mv，联立解得I＝8 kg· m/s。\n"
            "1.如图1所示，子弹以水平速度v0射向静止的木块，下列说法中正确的是(　　)\n"
            "A．甲　B．乙　C．丙　D．丁\n"
        )
        self.assertEqual(("SKIP", "题目派生：正文含成套选项 A/B/C/D"), pc.classify_chunk("", text))

    def test_ppt_token_stream_after_bare_marker_is_not_knowledge(self):
        """PPT 页被切成一行一词：光杆标题「考点二」不得把 token 流拼成知识段。"""
        text = (
            "slide e83\n【\n变式训练\n2 ·\n答案\n】\n考点二\n硫酸、\nSO\n3\n2-\n的检验\n考向\n2\n考查\n"
            "A\n．向\n2mL NH\n4\nHSO\n3\n溶液中滴加几滴酸性\nKMnO\n4\n溶液，观察到溶液褪色，"
            "说明\nHSO\n3\n-\n具有还原性，\nA\n项正确\nB\n．向\nNH\n4\nHSO\n3\n溶液中先滴加足量的稀硝酸，"
            "稀硝酸能将\nHSO\n3\n-\n氧化为\nSO\n4\n2-\n，对检验\nNH\n4\nHSO\n3\n溶液是否变质产生干扰，\nB\n项错误\n"
        )
        self.assertFalse(pc._marker_line("考点二"))       # 光杆标题不算知识锚
        self.assertEqual(0, pc.knowledge_span_chars("slide e83", text))
        verdict, why = pc.classify_chunk("slide e83", text)
        self.assertEqual("SKIP", verdict, why)
        self.assertIn("题目派生", why)

    def test_complete_statement_exemption_only_for_definitions(self):
        """R5：完整定义/规律句免过短；残句与孤立选项行不免。"""
        self.assertEqual(("JUDGE", ""), pc.classify_chunk(
            "1 电荷", "1)概念：经过摩擦的物体能够吸引轻小物体，我们就说它带有电荷。"))
        for text in ("本题选B。", "D. 电子的运动轨迹与其中的一条电场线重合",
                     "2026高考物理大一轮总复习讲义04：力的合成和分解（全国）"):
            verdict, why = pc.classify_chunk("", text)
            self.assertEqual("SKIP", verdict, text)
            self.assertEqual(f"过短（{len(pc.re.sub(r'\s+', '', text))} 字 < 40）", why, text)


class SubstringKnifeFixtures(unittest.TestCase):
    """F1–F3：`略$` / `限时` / `CIP` 三把无差别子串刀的合成夹具。"""

    def test_f1_blank_page_requires_standalone_lue(self):
        """F1：以「策略/简略/忽略」结尾的正文不得判空白/占位页；真「（略）」/「略。」仍判。"""
        verdict, why = pc.classify_chunk(
            "考点二", "思维建模  化工生产中的速率和平衡图像分析策略\n"
                       "本类图像题的解法是：先看斜率，再看面积，最后看截距，三步定位答案。")
        self.assertNotEqual("空白/占位页", why, verdict)
        self.assertEqual(("JUDGE", ""), (verdict, why))
        self.assertEqual(("SKIP", "空白/占位页"), pc.classify_chunk("", "（略）"))
        self.assertEqual(("SKIP", "空白/占位页"), pc.classify_chunk("", "略。"))
        self.assertEqual(("SKIP", "空白/占位页"), pc.classify_chunk("", "本页无正文"))

    def test_f2_ad_requires_real_ad_words(self):
        """F2：知识块含「限时」但无广告词 → 不得判广告宣传；真广告仍判。"""
        verdict, why = pc.classify_chunk(
            "考点一", "限时是相对论中的概念，指两个事件之间的时间间隔的测量结果。"
                       "本节讨论时间间隔与参照系的关系。")
        self.assertNotEqual("广告宣传", why, verdict)
        for text in ("扫码关注公众号，免费领取全套真题", "限时优惠，扫码抢购", "关注公众号 免费领取"):
            self.assertEqual(("SKIP", "广告宣传"), pc.classify_chunk("", text), text)

    def test_f3_cip_requires_word_boundary(self):
        """F3：`DCIP` / `CsCIPK11` 里的 CIP 不是版权信息；真版权页仍判。"""
        verdict, why = pc.classify_chunk(
            "考点二", "希尔反应中加入DCIP（二氯酚靛酚）作为电子受体，其颜色变化可指示光反应强度。"
                       "该实验说明光反应阶段产生还原剂。")
        self.assertNotEqual("版权/出版信息", why, verdict)
        verdict2, why2 = pc.classify_chunk(
            "p4", "基因CsCIPK11编码的蛋白参与钙信号转导，其表达量随胁迫时间延长而升高。"
                   "该基因属于CIPK家族，与植物的抗逆性有关。")
        self.assertNotEqual("版权/出版信息", why2, verdict2)
        self.assertEqual(("SKIP", "版权/出版信息"), pc.classify_chunk(
            "", "ISBN 978-7-107-33560-2 定价：68.00 元 版权所有 翻印必究 印张 12"))


def _write_pool(path: Path, records: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as fh:
        for r in records:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")


KNOW_A = ("加速度是速度对时间的变化率，其方向与速度变化量的方向一致。加速度是矢量，"
          "在国际单位制中单位是米每二次方秒。")
KNOW_B = ("速度是描述物体运动快慢的物理量，其方向与物体运动的方向一致。速度是矢量，"
          "在国际单位制中单位是米每秒。")


class FingerprintAndDeadBranchTests(unittest.TestCase):
    """N6/N7/F4：指纹重复（合成池）、死分支、指纹取最全副本。"""

    def _verdicts(self, td: str, records: list[dict]):
        pool = Path(td) / "chunks.jsonl"
        _write_pool(pool, records)
        with mock.patch.object(pc, "CHUNKS", pool), \
                mock.patch.object(pc, "JUDGMENTS", Path(td) / "absent.csv"):
            return [(rec.get("chunk_id"), verdict, why)
                    for rec, verdict, why in pc.iter_classified(pool, set())]

    def test_n6_fingerprint_duplicate_keeps_one_and_unique_fp_is_not_duplicate(self):
        with tempfile.TemporaryDirectory() as td:
            got = self._verdicts(td, [
                {"rel_path": "srcA/1.docx", "chunk_id": "n1", "fp": "fpN", "heading": "", "text": KNOW_A},
                {"rel_path": "srcA/1.docx", "chunk_id": "n2", "fp": "fpN", "heading": "", "text": KNOW_A},
                {"rel_path": "srcB/2.docx", "chunk_id": "n3", "fp": "fpU", "heading": "", "text": KNOW_A},
            ])
            self.assertEqual(("JUDGE", ""), (got[0][1], got[0][2]))
            self.assertEqual(("SKIP-重复", "同内容指纹重复（只留最全）"), (got[1][1], got[1][2]))
            # 唯一 fp 的块不得判重复
            self.assertEqual(("JUDGE", ""), (got[2][1], got[2][2]))

    def test_n7_dead_branches_are_reachable(self):
        """空块 / 纯页码/导航行 / 同 (源,chunk_id) 重复行（现池 0 命中，必须合成）。"""
        with tempfile.TemporaryDirectory() as td:
            got = dict((cid, (verdict, why)) for cid, verdict, why in self._verdicts(td, [
                {"rel_path": "srcA/1.docx", "chunk_id": "e1", "fp": "fpe1", "heading": "", "text": "  \n "},
                {"rel_path": "srcA/1.docx", "chunk_id": "e2", "fp": "fpe2", "heading": "12", "text": ""},
                {"rel_path": "srcA/1.docx", "chunk_id": "e3", "fp": "fpe3", "heading": "第 3 页", "text": ""},
                {"rel_path": "srcA/1.docx", "chunk_id": "e4", "fp": "fpe4", "heading": "加速度", "text": KNOW_A},
                {"rel_path": "srcA/1.docx", "chunk_id": "e4", "fp": "", "heading": "加速度", "text": "重复键行"},
            ]))
            self.assertEqual(("SKIP", "空块"), got["e1"])
            self.assertEqual(("SKIP", "纯页码/导航行"), got["e2"])
            self.assertEqual(("SKIP", "纯页码/导航行"), got["e3"])
            self.assertEqual(("SKIP-重复键", "同 (源,chunk_id) 重复行"), got["e4"])

    def test_f4_fingerprint_keeps_the_most_complete_copy(self):
        """F4：同 fp 两行，首行 150 字 / 次行 183 字 → 保留 183 字那行（对齐 fp=a243c3778020）。"""
        short_text = ("电场强度是描述电场强弱和方向的物理量。" * 12)[:150]
        long_text = ("磁感应强度是描述磁场强弱和方向的物理量。" * 12)[:183]
        self.assertLess(len(short_text), len(long_text))
        with tempfile.TemporaryDirectory() as td:
            got = self._verdicts(td, [
                {"rel_path": "srcA/1.docx", "chunk_id": "f1", "fp": "fpL", "heading": "", "text": short_text},
                {"rel_path": "srcA/1.docx", "chunk_id": "f2", "fp": "fpL", "heading": "", "text": long_text},
            ])
            self.assertEqual(("SKIP-重复", "同内容指纹重复（只留最全）"), (got[0][1], got[0][2]))
            self.assertEqual(("JUDGE", ""), (got[1][1], got[1][2]))   # 更全的副本被保留并参与判定


if __name__ == "__main__":
    unittest.main()
