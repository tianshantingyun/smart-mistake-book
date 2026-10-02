# -*- coding: utf-8 -*-
"""scan_duplicate_nodes 的用例：规范化判据、分组/前缀逻辑、CLI（--json/--out）、真实包基线。

真实包基线（2026-10-02 本机实测）：规范化完全同名 **11 组**、前缀包含 **18 对**
（读数取 work_dir()，默认 staging，与成品目录同源；干净检出会自动从成品目录种子）。
内容改动让基线变化时：先复算，再更新数字并注明出处——不要删断言变绿。
"""

from __future__ import annotations

import contextlib
import csv
import io
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from kb_build import pack_io  # noqa: E402
from kb_build import scan_duplicate_nodes as S  # noqa: E402


def _point(slug: str, name: str) -> dict:
    return {"slug": slug, "name": name, "aliases": [name]}


def _material(slug: str, subject: str, node_slug: str) -> dict:
    return {"slug": slug, "subject": subject, "type": "CONCEPT_EXPLANATION", "title": slug,
            "bindings": [{"knowledgeNodeId":
                          f"kb:test-pack:{subject.lower()}:atomic:{node_slug}",
                          "role": "PRIMARY"}]}


class NormTest(unittest.TestCase):
    def test_parens_punctuation_and_conjunctions(self):
        self.assertEqual(S.norm("棱切球（与各棱相切的球）"), S.norm("棱切球"))
        self.assertEqual(S.norm("磁场与磁场的组合"), S.norm("磁场和磁场的组合"))
        self.assertEqual(S.norm("光的波粒二象性(长期争论后达成的共识)"), S.norm("光的波粒二象性"))

    def test_only_empty_suffixes_are_stripped(self):
        self.assertEqual(S.norm("装置气密性检查的方法"), S.norm("装置气密性检查"))
        # 「方法/计算/比较」是真实区别，不许剥（第一版扫描踩过的坑：合法细分被并组）
        self.assertNotEqual(S.norm("化学反应速率的计算方法"), S.norm("化学反应速率的比较"))
        # 短名不剥（len(s) > len(suf) + 3 的守卫）
        self.assertNotEqual(S.norm("直线的问题"), S.norm("直线"))


class GroupingLogicTest(unittest.TestCase):
    """纯函数：分组与前缀对的判据（不读盘）。"""

    def _run(self, nodes, counts):
        groups = S.group_nodes(nodes, counts)
        return S.same_groups(groups), S.prefix_pairs(groups)

    def test_same_group_merges_only_within_one_subject(self):
        nodes = {
            ("MATH", "a1"): ("棱切球（与各棱相切的球）", "t1"),
            ("MATH", "a2"): ("棱切球", "t1"),
            ("PHYSICS", "n1"): ("内能", "t2"),
            ("CHEMISTRY", "n2"): ("内能", "t3"),
        }
        counts = {("MATH", "a1"): 2, ("MATH", "a2"): 3,
                  ("PHYSICS", "n1"): 4, ("CHEMISTRY", "n2"): 1}
        groups = S.group_nodes(nodes, counts)
        same, pairs = S.same_groups(groups), S.prefix_pairs(groups)
        self.assertEqual([("MATH", "棱切球")], list(same))
        self.assertEqual([], pairs)
        # 跨科同名不并组，且材料计数按 (subject, slug) 各算各的（不跨科串数）
        self.assertEqual(4, groups[("PHYSICS", "内能")][0]["material_count"])
        self.assertEqual(1, groups[("CHEMISTRY", "内能")][0]["material_count"])

    def test_prefix_pair_requires_matching_topic_and_thin_short_side(self):
        nodes = {
            ("CHEMISTRY", "p1"): ("装置气密性检查", "化学必修第一册·第一章·实验"),
            ("CHEMISTRY", "p2"): ("装置气密性检查方法", "化学必修第一册·第一章·实验"),
            ("CHEMISTRY", "q1"): ("化学平衡的移动", "册·第一章·平衡"),
            ("CHEMISTRY", "q2"): ("化学平衡的移动规律", "册·第一章·速率"),
            ("CHEMISTRY", "r1"): ("氧化还原", "册·第一章·反应"),
            ("CHEMISTRY", "r2"): ("氧化还原反应", "册·第一章·反应"),
            ("CHEMISTRY", "s1"): ("溶液浓度的计算", "册·第一章·计量"),
            ("CHEMISTRY", "s2"): ("溶液浓度的计算方法", "册·第一章·计量"),
        }
        counts = {("CHEMISTRY", "p1"): 1, ("CHEMISTRY", "p2"): 10,
                  ("CHEMISTRY", "q1"): 1, ("CHEMISTRY", "q2"): 2,
                  ("CHEMISTRY", "r1"): 1, ("CHEMISTRY", "r2"): 2,
                  ("CHEMISTRY", "s1"): 3, ("CHEMISTRY", "s2"): 9}
        _same, pairs = self._run(nodes, counts)
        found = {(pair["subject"], pair["key"], pair["other_key"]) for pair in pairs}
        # q1/q2：主题末段不同（平衡 vs 速率）→ 合法细分，不收；
        # r1/r2：短名 4 字 < 5 → 不收；s1/s2：短的那侧 3 条 > 2 → 不收。
        self.assertEqual({("CHEMISTRY", "装置气密性检查", "装置气密性检查方法")}, found)


class CliFixtureTest(unittest.TestCase):
    """合成包 + 单卷：--json / --out 的形态与计数。"""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-scan-", dir=pack_io.REPO / "build"))
        pack = {
            "schemaVersion": 2, "packId": "test-pack", "taxonomyVersion": "test-pack",
            "subjects": [
                {"subject": "MATH", "topics": [{"slug": "t-math", "name": "立体几何",
                 "knowledgePoints": [_point("dup-a", "棱切球（与各棱相切的球）"),
                                     _point("dup-b", "棱切球")]}]},
                {"subject": "CHEMISTRY", "topics": [{"slug": "t-chem", "name": "化学必修第一册·第一章·实验",
                 "knowledgePoints": [_point("pre-1", "装置气密性检查"),
                                     _point("pre-2", "装置气密性检查方法"),
                                     _point("solo", "内能")]}]},
                {"subject": "PHYSICS", "topics": [{"slug": "t-phys", "name": "热学",
                 "knowledgePoints": [_point("cross", "内能")]}]},
            ],
        }
        (self.tmp / pack_io.PACK_NAME).write_text(
            json.dumps(pack, ensure_ascii=False), encoding="utf-8")
        materials = []
        for subject, node_slug, count in [("MATH", "dup-a", 2), ("MATH", "dup-b", 3),
                                          ("CHEMISTRY", "pre-1", 1), ("CHEMISTRY", "pre-2", 10),
                                          ("CHEMISTRY", "solo", 1), ("PHYSICS", "cross", 4)]:
            for index in range(count):
                materials.append(_material(f"{node_slug}-{index}", subject, node_slug))
        sidecar = {"schemaVersion": 2, "packId": "test-pack", "sources": [],
                   "materials": materials}
        (self.tmp / "moe-2025-teaching-support-v2-01.json").write_text(
            json.dumps(sidecar, ensure_ascii=False), encoding="utf-8")
        pack_io.dump_json(
            {"packId": "test-pack",
             "sidecars": ["knowledge/moe-2025-teaching-support-v2-01.json"]},
            self.tmp / pack_io.SIDECAR_INDEX_NAME)
        pack_io.use_directory(self.tmp)

    def tearDown(self):
        pack_io.reset_directory()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_counts_are_subject_scoped(self):
        counts = S.material_counts()
        self.assertEqual(4, counts[("PHYSICS", "cross")])
        self.assertEqual(1, counts[("CHEMISTRY", "solo")])

    def test_json_mode_returns_all_candidates(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            rc = S.main(["--json"])
        self.assertEqual(0, rc)
        payload = json.loads(out.getvalue())
        self.assertEqual(1, len(payload["same"]))
        self.assertEqual(1, len(payload["prefix_pairs"]))
        group = payload["same"][0]
        self.assertEqual(("MATH", "棱切球"), (group["subject"], group["key"]))
        self.assertEqual(["dup-a", "dup-b"], [node["slug"] for node in group["nodes"]])
        pair = payload["prefix_pairs"][0]
        self.assertEqual(("CHEMISTRY", "装置气密性检查", "装置气密性检查方法"),
                         (pair["subject"], pair["key"], pair["other_key"]))
        self.assertEqual(["pre-1"], [node["slug"] for node in pair["short"]])
        self.assertEqual(["pre-2"], [node["slug"] for node in pair["long"]])

    def test_out_writes_csv_with_all_rows(self):
        path = self.tmp / "dupes.csv"
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            rc = S.main(["--out", str(path)])
        self.assertEqual(0, rc)
        self.assertIn("规范化完全同名组：1 组", out.getvalue())     # 人类报告首行格式不变
        with path.open(encoding="utf-8", newline="") as fh:
            rows = list(csv.DictReader(fh))
        self.assertEqual(S.CSV_COLUMNS, list(rows[0].keys()))
        self.assertEqual(4, len(rows))        # 同名的 2 个节点 + 前缀对的 2 个节点
        self.assertEqual([("same", ""), ("same", ""), ("prefix", "short"), ("prefix", "long")],
                         [(row["kind"], row["side"]) for row in rows])


class RealPackBaselineTest(unittest.TestCase):
    """真实包基线（2026-10-02 实测）：规范化同名 11 组、前缀包含 18 对。"""

    def test_current_pack_baseline(self):
        result = S.scan()
        self.assertEqual(11, len(result["same"]),
                         "同名组基线变了；先复算核对（见本文件头），不要直接改数字")
        self.assertEqual(18, len(result["prefix_pairs"]),
                         "前缀对基线变了；先复算核对（见本文件头），不要直接改数字")

    def test_default_report_first_lines(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            rc = S.main([])
        self.assertEqual(0, rc)
        text = out.getvalue()
        self.assertEqual("规范化完全同名组：11 组", text.splitlines()[0])
        self.assertIn("前缀包含对：18 对（前 8）", text)


if __name__ == "__main__":
    unittest.main()
