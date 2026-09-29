# -*- coding: utf-8 -*-
"""裁决合并器（merge_candidate_verdicts）的用例：六档裁决必须落成正确的四类产物。

覆盖：
① GAP/MATERIAL_ONLY/NEW_POINT → authored_materials.jsonl（13 键序正确、绑定 id 正确、
   slug 由 idx 派生、新点材料的绑定指向新建 slug）；
② NEW_POINT 的父主题精确命中既有主题 → new_points_rows.csv；命中不了 → placement_review.csv；
③ 材料层 PROMOTE → 沿用候选 slug、sourceId 走登记表变换且必须已登记（未登记进隔离）；
④ COVERED → coverage_register.csv；REJECT/NEEDS_REVIEW/MERGE_INTO → rejects.csv；
⑤ 非法输入（未知 verdict / 节点不存在 / 新点字段不合规）进 quarantine，不静默丢；
⑥ 干跑不落盘。
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

from kb_build import merge_candidate_verdicts as M  # noqa: E402
from kb_build import new_content as nc  # noqa: E402
from kb_build import pack_io  # noqa: E402

DIR_COLS = M.DIR_COLS
MAT_COLS = M.MAT_COLS


def _row(cols: list[str], values: dict) -> dict:
    return {c: values.get(c, "") for c in cols}


def _write_csv(path: Path, cols: list[str], rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=cols)
        writer.writeheader()
        writer.writerows(rows)


class MergeCandidateVerdictsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-verdicts-", dir=pack_io.REPO / "build"))
        pack = {
            "schemaVersion": 2, "packId": "test-pack", "taxonomyVersion": "test-pack",
            "subjects": [{"subject": "MATH", "topics": [
                {"slug": "t1", "name": "主题一", "knowledgePoints": [
                    {"slug": "node-a", "name": "节点甲", "aliases": ["节点甲"], "kind": "CONCEPT",
                     "boundary": "定位：测。真边界。", "sourceLocator": "人教版高中教材（2019）",
                     "prerequisiteSlugs": []}]}]}],
        }
        (self.tmp / pack_io.PACK_NAME).write_text(json.dumps(pack, ensure_ascii=False), encoding="utf-8")
        sidecar = {"schemaVersion": 2, "packId": "test-pack", "sources": [{
            "sourceId": "registry-kb-math-knowledge-list:math", "subject": "MATH",
            "sourceType": "AUTHORIZED_EDUCATION_MATERIAL", "title": "知识清单", "publisher": "曲一线",
            "edition": "2027版", "sourceUri": "u", "licenseStatus": "REFERENCE_ONLY",
            "contentFingerprint": "X", "importedAtEpochMillis": 1000,
            "contentUsePolicy": "REVIEWED_SYNTHESIS_ONLY", "licenseExpression": None,
            "licenseUri": None, "attributionText": "结构化总结。"}], "materials": []}
        (self.tmp / "moe-2025-teaching-support-v2-01.json").write_text(
            json.dumps(sidecar, ensure_ascii=False), encoding="utf-8")
        pack_io.use_directory(self.tmp)

        self.vdir = self.tmp / "verdicts"
        _write_csv(self.vdir / "dir_batch_000.csv", DIR_COLS, [
            _row(DIR_COLS, {"idx": "1", "subject": "MATH", "name": "覆盖项", "verdict": "COVERED",
                            "evidence": "m-cover"}),
            _row(DIR_COLS, {"idx": "2", "subject": "MATH", "name": "缺口项", "verdict": "GAP",
                            "node_slug": "node-a", "material_title": "缺口补料",
                            "material_type": "METHOD_MODEL", "material_summary": "摘要。",
                            "material_content": "结论一行。", "material_boundary": "边界。"}),
            _row(DIR_COLS, {"idx": "3", "subject": "MATH", "name": "新点项", "verdict": "NEW_POINT",
                            "point_name": "新考点甲", "kind": "CONCEPT", "parent_hint": "主题一",
                            "material_title": "新点首料", "material_type": "CONCEPT_EXPLANATION",
                            "material_summary": "摘要。", "material_content": "结论。",
                            "material_boundary": "边界。"}),
            _row(DIR_COLS, {"idx": "4", "subject": "MATH", "name": "悬空新点", "verdict": "NEW_POINT",
                            "point_name": "新考点乙", "kind": "CONCEPT", "parent_hint": "不存在的主题",
                            "material_title": "料", "material_type": "CONCEPT_EXPLANATION",
                            "material_summary": "s", "material_content": "c",
                            "material_boundary": "b"}),
            _row(DIR_COLS, {"idx": "5", "subject": "MATH", "name": "装饰串", "verdict": "REJECT",
                            "reason": "封面"}),
            _row(DIR_COLS, {"idx": "6", "subject": "MATH", "name": "疑点", "verdict": "NEEDS_REVIEW",
                            "reason": "存疑"}),
            _row(DIR_COLS, {"idx": "7", "subject": "MATH", "name": "内容属他点", "verdict": "MATERIAL_ONLY",
                            "node_slug": "node-a", "material_title": "补面",
                            "material_type": "CONCEPT_EXPLANATION", "material_summary": "s",
                            "material_content": "c", "material_boundary": "b"}),
            _row(DIR_COLS, {"idx": "8", "subject": "MATH", "name": "坏行", "verdict": "FANCY"}),
            _row(DIR_COLS, {"idx": "9", "subject": "MATH", "name": "悬空绑", "verdict": "GAP",
                            "node_slug": "不存在", "material_title": "t",
                            "material_type": "METHOD_MODEL", "material_summary": "s",
                            "material_content": "c", "material_boundary": "b"}),
        ])
        _write_csv(self.vdir / "mat_180_200.csv", MAT_COLS, [
            _row(MAT_COLS, {"idx": "181", "slug": "math-cand-one", "subject": "MATH",
                            "verdict": "PROMOTE", "node_slug": "node-a",
                            "adjudicated_type": "METHOD_MODEL", "material_title": "候选材料一",
                            "material_content": "候选结论。"}),
            _row(MAT_COLS, {"idx": "182", "slug": "math-cand-two", "subject": "MATH",
                            "verdict": "MERGE_INTO", "node_slug": "node-a",
                            "covering_material_slug": "m-cover"}),
            _row(MAT_COLS, {"idx": "183", "slug": "math-cand-three", "subject": "MATH",
                            "verdict": "REJECT", "reason": "题目派生"}),
        ])
        entries = [
            {"slug": "math-cand-one", "subject": "MATH", "type": "DERIVATION",
             "title": "候选标题一", "aliases": [], "summaryMarkdown": "候选摘要。",
             "applicabilityMarkdown": "适用。", "contentMarkdown": "候选结论。",
             "boundaryMarkdown": "候选边界。", "derivationKind": "REVIEWED_SYNTHESIS",
             "sourceId": "registry:kb:math:knowledge-list", "sourceLocator": "知识清单 P1",
             "bindings": [], "pageImage": ""},
        ]
        (self.tmp / "mat_entries.jsonl").write_text(
            "".join(json.dumps(e, ensure_ascii=False) + "\n" for e in entries), encoding="utf-8")

    def tearDown(self):
        pack_io.reset_directory()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _build(self):
        return M.build(pack_io.REPO, verdicts_dir=self.vdir,
                       entries_path=self.tmp / "mat_entries.jsonl")

    def test_build_buckets(self):
        built = self._build()
        self.assertEqual([], built["errors"])
        self.assertEqual(4, len(built["materials"]))          # GAP + NEW_POINT + MATERIAL_ONLY + PROMOTE
        self.assertEqual(1, len(built["new_points"]))
        self.assertEqual(1, len(built["coverage"]))
        self.assertEqual(4, len(built["rejects"]))            # dir REJECT + NEEDS_REVIEW + mat MERGE_INTO + mat REJECT
        self.assertEqual(1, len(built["placement"]))          # 悬空父主题的新点
        reasons = "; ".join(q["reasons"] for q in built["quarantine"])
        self.assertIn("未知 verdict", reasons)
        self.assertIn("节点不存在", reasons)

    def test_material_shape(self):
        built = self._build()
        by_slug = {m["slug"]: m for m in built["materials"]}
        self.assertIn("cmp-mat-00002", by_slug)               # idx=2 的 GAP
        gap = by_slug["cmp-mat-00002"]
        self.assertEqual(list(nc.MATERIAL_KEYS), list(gap))   # 13 键序逐字
        self.assertEqual("kb:moe-2025-four-subjects-v1:math:atomic:node-a",
                         gap["bindings"][0]["knowledgeNodeId"])
        self.assertEqual("registry-kb-math-knowledge-list:math", gap["sourceId"])
        new_point_mat = by_slug["cmp-mat-00003"]
        self.assertIn("atomic:新考点甲", new_point_mat["bindings"][0]["knowledgeNodeId"])
        kept = by_slug["math-cand-one"]
        self.assertEqual("registry-kb-math-knowledge-list:math", kept["sourceId"])
        self.assertEqual("METHOD_MODEL", kept["type"])

    def test_write_outputs(self):
        built = self._build()
        out = self.tmp / "out"
        M.write_all(pack_io.REPO, out, built)
        rows = [json.loads(l) for l in
                (out / "authored_materials.jsonl").read_text(encoding="utf-8").splitlines()]
        self.assertEqual(4, len(rows))
        points = list(csv.DictReader((out / "new_points_rows.csv").open(encoding="utf-8", newline="")))
        self.assertEqual([("MATH", "新考点甲", "t1")],
                         [(r["subject"], r["name"], r["parent_topic_slug"]) for r in points])
        placement = list(csv.DictReader(
            (out / "placement_review.csv").open(encoding="utf-8", newline="")))
        self.assertEqual(["新考点乙"], [r["point_name"] for r in placement])
        coverage = list(csv.DictReader(
            (out / "coverage_register.csv").open(encoding="utf-8", newline="")))
        self.assertEqual(["m-cover"], [r["evidence"] for r in coverage])


    def test_capacity_blocked_node_not_written(self):
        """节点已有 ≥4 条材料（合规规范 §4.4：第 5 条起不可见）→ 补料不写，落 capacity_blocked。"""
        doc = json.loads((self.tmp / "moe-2025-teaching-support-v2-01.json")
                         .read_text(encoding="utf-8"))
        for i in range(4):
            doc["materials"].append({
                "slug": "m-extra-%d" % i, "subject": "MATH", "type": "METHOD_MODEL",
                "title": "补位材料%d" % i, "summaryMarkdown": "s", "applicabilityMarkdown": "a",
                "contentMarkdown": "c", "boundaryMarkdown": "b",
                "derivationKind": "REVIEWED_SYNTHESIS",
                "sourceId": "registry-kb-math-knowledge-list:math",
                "sourceLocator": "loc", "reviewedAtEpochMillis": 2000,
                "bindings": [{"knowledgeNodeId": "kb:test-pack:math:atomic:node-a",
                              "role": "PRIMARY"}]})
        (self.tmp / "moe-2025-teaching-support-v2-01.json").write_text(
            json.dumps(doc, ensure_ascii=False), encoding="utf-8")
        built = self._build()
        blocked = built["capacity_blocked"]
        self.assertEqual(3, len(blocked))          # GAP + MATERIAL_ONLY + PROMOTE 全绑 node-a
        self.assertEqual({"node-a"}, {b["node_slug"] for b in blocked})
        self.assertEqual({4}, {b["existing_materials"] for b in blocked})
        slugs = [m["slug"] for m in built["materials"]]
        self.assertEqual(["cmp-mat-00003"], slugs)  # 只剩绑新点的那条


if __name__ == "__main__":
    unittest.main()
