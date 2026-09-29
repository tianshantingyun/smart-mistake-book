# -*- coding: utf-8 -*-
"""候选区合并准备工具（prep_candidate_merge）的用例：机械口径必须可复算、不漂移。

四个方向：
① 目录层四档机械命中（name/alias/星标剥离/未命中）逐条分对，星标与长句标记不误报；
② 命中条目必须内联节点与该节点材料摘要（代理不必再翻 48MB 侧车）；
③ 材料层三分类（已在包/有差异/未入包）按字段逐一比对，差异字段名列得出来；
④ 批次切分与产物清单齐整（--write 落盘、dry-run 不落盘）。
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

from kb_build import pack_io, prep_candidate_merge as P  # noqa: E402

LONG_NAME = "配方法：主要用于二次函数或可化为二次函数的函数，要特别注意自变量的取值范围"


def _write_json(path: Path, doc) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(doc, ensure_ascii=False, indent=1), encoding="utf-8")


def _node(name: str, slug: str, aliases=None) -> dict:
    return {"slug": slug, "name": name, "aliases": aliases or [name], "kind": "CONCEPT",
            "boundary": "定位：测试。真边界文本。", "sourceLocator": "人教版高中教材（2019）",
            "prerequisiteSlugs": []}


class PrepCandidateMergeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-prep-cand-", dir=pack_io.REPO / "build"))
        _write_json(self.tmp / pack_io.PACK_NAME, {"schemaVersion": 2, "packId": "test-pack",
                                                   "taxonomyVersion": "test-pack",
                                                   "subjects": [{"subject": "MATH", "topics": [
                                                       {"slug": "t1", "name": "主题一",
                                                        "knowledgePoints": [
                                                            _node("节点甲", "node-a", ["别名甲"])]}]}]})
        cand = self.tmp / "knowledge-research" / "candidates" / "math"
        for folder, subject in (("physics", "PHYSICS"), ("chemistry", "CHEMISTRY"),
                                ("biology", "BIOLOGY")):
            other = self.tmp / "knowledge-research" / "candidates" / folder
            _write_json(other / "knowledge-directory-summary.json",
                        {"subject": subject, "nodeCount": 0, "nodes": []})
            _write_json(other / "teaching-support-candidates.json",
                        {"materialCount": 0, "materials": []})
        _write_json(cand / "knowledge-directory-summary.json", {"subject": "MATH", "nodeCount": 5, "nodes": [
            {"name": "节点甲", "aliases": ["节点甲"], "definition": "定义甲",
             "topicId": "t", "source": "知识清单", "sourceFile": "专题01.docx"},
            {"name": "别名甲", "aliases": ["别名甲"], "definition": "定义乙",
             "topicId": "t", "source": "知识清单", "sourceFile": "专题01.docx"},
            {"name": "节点甲★★☆☆☆", "aliases": ["节点甲★★☆☆☆"], "definition": "定义丙",
             "topicId": "t", "source": "知识清单", "sourceFile": "专题01.docx"},
            {"name": "正式区没有的考点", "aliases": ["正式区没有的考点"], "definition": "定义丁",
             "topicId": "t", "source": "知识清单", "sourceFile": "专题01.docx"},
            {"name": LONG_NAME, "aliases": [LONG_NAME], "definition": "定义戊",
             "topicId": "t", "source": "知识清单", "sourceFile": "专题01.docx"},
        ]})
        _write_json(cand / "teaching-support-candidates.json", {"materialCount": 3, "materials": [
            {"slug": "m-in", "subject": "MATH", "type": "METHOD_MODEL", "title": "材料一",
             "summaryMarkdown": "同", "contentMarkdown": "同", "boundaryMarkdown": "同",
             "applicabilityMarkdown": "同", "sourceId": "registry:kb:math:handout"},
            {"slug": "m-diff", "subject": "MATH", "type": "METHOD_MODEL", "title": "材料二",
             "summaryMarkdown": "候选侧旧", "contentMarkdown": "候选侧旧", "boundaryMarkdown": "同",
             "applicabilityMarkdown": "同", "sourceId": "registry:kb:math:handout"},
            {"slug": "m-missing", "subject": "MATH", "type": "CONCEPT_EXPLANATION", "title": "材料三",
             "summaryMarkdown": "新", "contentMarkdown": "新", "boundaryMarkdown": "新",
             "applicabilityMarkdown": "新", "sourceId": "registry:kb:math:handout"},
        ]})
        _write_json(self.tmp / "moe-2025-teaching-support-v2-01.json", {"materials": [
            {"slug": "m-in", "subject": "MATH", "type": "METHOD_MODEL", "title": "材料一",
             "summaryMarkdown": "同", "contentMarkdown": "同", "boundaryMarkdown": "同",
             "applicabilityMarkdown": "同",
             "bindings": [{"knowledgeNodeId": "kb:test-pack:math:atomic:node-a",
                           "role": "PRIMARY"}]},
            {"slug": "m-diff", "subject": "MATH", "type": "METHOD_MODEL", "title": "材料二",
             "summaryMarkdown": "成品侧新", "contentMarkdown": "成品侧新", "boundaryMarkdown": "同",
             "applicabilityMarkdown": "同",
             "bindings": [{"knowledgeNodeId": "kb:test-pack:math:atomic:node-a",
                           "role": "PRIMARY"}]},
        ]})
        pack_io.use_directory(self.tmp)

    def tearDown(self):
        pack_io.reset_directory()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_classify_and_write(self):
        rc = P.main(["--write", "--root", str(self.tmp), "--batch-size", "2"])
        self.assertEqual(rc, 0)
        out = self.tmp / P.OUT_DIR
        summary = json.loads((out / "summary.json").read_text(encoding="utf-8"))
        self.assertEqual(summary["dir"], {"name_hit": 1, "alias_hit": 1, "star_hit": 1,
                                          "unmatched": 2, "star": 1, "long": 1, "ambiguous": 0})
        self.assertEqual(summary["material"], {"already_in": 1, "divergent": 1, "missing": 1})
        self.assertEqual(summary["batches"], 3)

        rows = [json.loads(line) for line in
                (out / "dir_entries.jsonl").read_text(encoding="utf-8").splitlines()]
        by_name = {row["name"]: row for row in rows}
        hit = by_name["节点甲"]
        self.assertEqual(hit["mech"], "name_hit")
        self.assertEqual(hit["node"]["slug"], "node-a")
        self.assertEqual([m["slug"] for m in hit["node_materials_sample"]], ["m-in", "m-diff"])
        self.assertEqual(by_name["别名甲"]["mech"], "alias_hit")
        self.assertEqual(by_name["节点甲★★☆☆☆"]["mech"], "star_hit")
        self.assertEqual(by_name["正式区没有的考点"]["mech"], "unmatched")
        self.assertIsNone(by_name["正式区没有的考点"]["node"])
        self.assertTrue(by_name[LONG_NAME]["long"])
        self.assertFalse(hit["long"])

        ops = {row["slug"]: row for row in
               csv.DictReader((out / "mat_ops.csv").open(encoding="utf-8", newline=""))}
        self.assertEqual(ops["m-in"]["status"], "already_in")
        self.assertEqual(ops["m-diff"]["status"], "divergent")
        self.assertEqual(ops["m-diff"]["diff_fields"], "summaryMarkdown;contentMarkdown")
        self.assertEqual(ops["m-missing"]["status"], "missing")
        missing = [json.loads(line) for line in
                   (out / "mat_entries.jsonl").read_text(encoding="utf-8").splitlines()]
        self.assertEqual([m["slug"] for m in missing], ["m-missing"])
        self.assertTrue((out / "dir_batches" / "batch_000.jsonl").exists())
        pack_nodes = list(csv.DictReader(
            (out / "pack_nodes.csv").open(encoding="utf-8", newline="")))
        self.assertEqual([row["slug"] for row in pack_nodes], ["node-a"])
        self.assertEqual(pack_nodes[0]["aliases"], "别名甲")
        node_mats = list(csv.DictReader(
            (out / "node_materials.csv").open(encoding="utf-8", newline="")))
        self.assertEqual(sorted(row["material_slug"] for row in node_mats), ["m-diff", "m-in"])
        self.assertEqual({row["node_slug"] for row in node_mats}, {"node-a"})

    def test_dry_run_writes_nothing(self):
        rc = P.main(["--root", str(self.tmp)])
        self.assertEqual(rc, 0)
        self.assertFalse((self.tmp / P.OUT_DIR).exists())


if __name__ == "__main__":
    unittest.main()
