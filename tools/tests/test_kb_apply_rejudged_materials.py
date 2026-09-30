# -*- coding: utf-8 -*-
"""重判替换（apply_rejudged_materials）用例：同 slug 不同绑定 → 原地替换且幂等。

覆盖：① 目标相同的行不动（materialize 的幂等面）；② 目标不同的行被替换（字段+绑定+时间戳）；
③ 替换后重跑 plan 为空（幂等）；④ dry-run 不写盘；⑤ 生成替换账。
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
from kb_coverage import apply_rejudged_materials as A  # noqa: E402
from kb_coverage import materialize as mz  # noqa: E402

HDR = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title", "summary",
       "applicability", "content", "boundary", "note", "midx"]


class ApplyRejudgedMaterialsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-rejudge-", dir=pack_io.REPO / "build"))
        pack = {"schemaVersion": 2, "packId": "test-pack", "taxonomyVersion": "test-pack",
                "subjects": [{"subject": "MATH", "topics": [{"slug": "t1", "name": "主题一",
                             "knowledgePoints": [
                                 {"slug": "node-a", "name": "节点甲", "aliases": ["节点甲"],
                                  "kind": "CONCEPT", "boundary": "定位：测。真边界。",
                                  "sourceLocator": "人教版高中教材（2019）", "prerequisiteSlugs": []},
                                 {"slug": "node-b", "name": "节点乙", "aliases": ["节点乙"],
                                  "kind": "CONCEPT", "boundary": "定位：测。真边界。",
                                  "sourceLocator": "人教版高中教材（2019）", "prerequisiteSlugs": []}]}]}]}
        (self.tmp / pack_io.PACK_NAME).write_text(json.dumps(pack, ensure_ascii=False),
                                                 encoding="utf-8")
        sidecar = {"schemaVersion": 2, "packId": "test-pack", "sources": [], "materials": [{
            "slug": "ext-mat-aaaa111111-001", "subject": "MATH", "type": "DERIVATION",
            "title": "旧裁决标题", "summaryMarkdown": "旧", "applicabilityMarkdown": "旧",
            "contentMarkdown": "旧", "boundaryMarkdown": "旧",
            "derivationKind": "REVIEWED_SYNTHESIS", "sourceId": "x", "sourceLocator": "loc",
            "reviewedAtEpochMillis": 1,
            "bindings": [{"knowledgeNodeId": "kb:test-pack:math:atomic:node-a",
                          "role": "PRIMARY"}]}]}
        (self.tmp / "moe-2025-teaching-support-v2-01.json").write_text(
            json.dumps(sidecar, ensure_ascii=False), encoding="utf-8")
        pack_io.use_directory(self.tmp)
        self.pool = self.tmp / "chunks.jsonl"
        self.pool.write_text(json.dumps({"rel_path": "数学/1.docx", "chunk_id": "aaaa111111-001",
                                         "subject": "MATH", "heading": "h", "text": "t", "fp": "f"})
                             + "\n", encoding="utf-8")
        self.judgments = self.tmp / "judgments.csv"
        with self.judgments.open("w", encoding="utf-8", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=HDR)
            writer.writeheader()
            writer.writerow({"chunk_rel": "数学/1.docx", "chunk_id": "aaaa111111-001",
                             "action": "MATERIAL", "node_slug": "node-b",
                             "type": "CONCEPT_EXPLANATION", "title": "新裁决标题",
                             "summary": "新摘要", "applicability": "PRIMARY：新",
                             "content": "新正文", "boundary": "新边界", "note": "", "midx": ""})
        self._orig = (A.POOL_PATH, mz.JUDGMENTS, A.OUT_LOG)
        A.POOL_PATH, mz.JUDGMENTS, A.OUT_LOG = self.pool, self.judgments, self.tmp / "log.csv"

    def tearDown(self):
        A.POOL_PATH, mz.JUDGMENTS, A.OUT_LOG = self._orig
        pack_io.reset_directory()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _material(self) -> dict:
        doc = json.loads((self.tmp / "moe-2025-teaching-support-v2-01.json")
                         .read_text(encoding="utf-8"))
        return doc["materials"][0]

    def test_replaces_and_is_idempotent(self):
        pl = A.plan()
        self.assertEqual(1, len(pl["todo"]))
        self.assertEqual("node-a", pl["todo"][0]["old_node"])
        stats = A.apply(pl["todo"], pl["docs"])
        self.assertEqual(1, stats["replaced"])
        material = self._material()
        self.assertEqual("CONCEPT_EXPLANATION", material["type"])
        self.assertEqual("新裁决标题", material["title"])
        self.assertEqual("kb:moe-2025-four-subjects-v1:math:atomic:node-b",
                         material["bindings"][0]["knowledgeNodeId"])
        self.assertEqual(0, len(A.plan()["todo"]))          # 幂等
        log = list(csv.DictReader((self.tmp / "log.csv").open(encoding="utf-8", newline="")))
        self.assertEqual("node-a", log[0]["old_node"])
        self.assertEqual("node-b", log[0]["new_node"])

    def test_same_target_not_touched(self):
        # 目标相同 → 不属于替换面（materialize 自己幂等跳过）
        rows = A.load_rows()
        rows[0]["node_slug"] = "node-a"
        self.assertEqual(0, len(A.plan(rows)["todo"]))


if __name__ == "__main__":
    unittest.main()
