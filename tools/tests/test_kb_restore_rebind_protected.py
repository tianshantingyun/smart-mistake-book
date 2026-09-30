# -*- coding: utf-8 -*-
"""改绑保护还原（restore_rebind_protected）用例：改绑表优先、幂等、判定行转 SKIP。

覆盖：① 被替换过的材料从参照包还原（字段+绑定都回到改绑表目标）；② 判定表对应行转 SKIP 且 note 记因；
③ 幂等（第二次 0 还原）；④ dry-run 不写盘。
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
from kb_coverage import materialize as mz  # noqa: E402
from kb_coverage import restore_rebind_protected as R  # noqa: E402

HDR = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title", "summary",
       "applicability", "content", "boundary", "note", "midx"]


def _material(slug: str, node: str, title: str, mtype: str) -> dict:
    return {"slug": slug, "subject": "MATH", "type": mtype, "title": title,
            "summaryMarkdown": "s", "applicabilityMarkdown": "a", "contentMarkdown": "c",
            "boundaryMarkdown": "b", "derivationKind": "REVIEWED_SYNTHESIS",
            "sourceId": "x", "sourceLocator": "loc", "reviewedAtEpochMillis": 1,
            "bindings": [{"knowledgeNodeId": f"kb:test-pack:math:atomic:{node}",
                          "role": "PRIMARY"}]}


class RestoreRebindProtectedTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-restore-", dir=pack_io.REPO / "build"))
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
        # 当前（被替换后）：绑 node-b、字段是新的
        (self.tmp / "moe-2025-teaching-support-v2-01.json").write_text(
            json.dumps({"schemaVersion": 2, "packId": "test-pack", "sources": [],
                        "materials": [_material("ext-mat-aaaa111111-001", "node-b",
                                                "新裁决标题", "CONCEPT_EXPLANATION")]},
                       ensure_ascii=False), encoding="utf-8")
        pack_io.use_directory(self.tmp)
        # 参照包（替换前）：绑 node-a、旧字段
        self.ref = self.tmp / "ref"
        self.ref.mkdir()
        (self.ref / "moe-2025-teaching-support-v2-01.json").write_text(
            json.dumps({"schemaVersion": 2, "packId": "test-pack", "sources": [],
                        "materials": [_material("ext-mat-aaaa111111-001", "node-a",
                                                "旧裁决标题", "DERIVATION")]},
                       ensure_ascii=False), encoding="utf-8")
        # 表：改绑表 + 替换账 + 判定表 + 池
        self._orig = (R.REBIND, R.REPLACEMENTS, mz.JUDGMENTS)
        R.REBIND = self.tmp / "rebind.csv"
        R.REPLACEMENTS = self.tmp / "replacements.csv"
        mz.JUDGMENTS = self.tmp / "judgments.csv"
        R.REBIND.write_text("material_slug,from_node_slug,to_node_slug,evidence\n"
                            "ext-mat-aaaa111111-001,node-b,node-a,按内容归属重绑\n", encoding="utf-8")
        R.REPLACEMENTS.write_text("slug,old_node,new_node,sidecar\n"
                                  "ext-mat-aaaa111111-001,node-a,node-b,x.json\n", encoding="utf-8")
        with mz.JUDGMENTS.open("w", encoding="utf-8", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=HDR)
            writer.writeheader()
            writer.writerow({"chunk_rel": "数学/1.docx", "chunk_id": "aaaa111111-001",
                             "action": "MATERIAL", "node_slug": "node-b",
                             "type": "CONCEPT_EXPLANATION", "title": "新裁决标题",
                             "summary": "新", "applicability": "PRIMARY：新", "content": "新正文",
                             "boundary": "新边界", "note": "", "midx": ""})
        import kb_coverage.pool_path as pp
        self._orig_pool = pp.POOL_PATH
        pp.POOL_PATH = self.tmp / "chunks.jsonl"
        (self.tmp / "chunks.jsonl").write_text(json.dumps(
            {"rel_path": "数学/1.docx", "chunk_id": "aaaa111111-001", "subject": "MATH",
             "heading": "h", "text": "t", "fp": "f"}) + "\n", encoding="utf-8")

    def tearDown(self):
        R.REBIND, R.REPLACEMENTS, mz.JUDGMENTS = self._orig
        import kb_coverage.pool_path as pp
        pp.POOL_PATH = self._orig_pool
        pack_io.reset_directory()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_restore_and_flip(self):
        R.restore(self.ref, write=True)
        doc = json.loads((self.tmp / "moe-2025-teaching-support-v2-01.json")
                         .read_text(encoding="utf-8"))
        material = doc["materials"][0]
        self.assertEqual("旧裁决标题", material["title"])
        self.assertEqual("DERIVATION", material["type"])
        self.assertEqual("kb:test-pack:math:atomic:node-a",
                         material["bindings"][0]["knowledgeNodeId"])
        row = list(csv.DictReader(mz.JUDGMENTS.open(encoding="utf-8-sig", newline="")))[0]
        self.assertEqual("SKIP", row["action"])
        self.assertIn("material_rebind", row["note"])
        self.assertEqual("", row["content"])
        self.assertEqual(0, R.restore(self.ref, write=True)["restored"])   # 幂等

    def test_dry_run_writes_nothing(self):
        before = (self.tmp / "moe-2025-teaching-support-v2-01.json").read_bytes()
        R.restore(self.ref, write=False)
        self.assertEqual(before,
                         (self.tmp / "moe-2025-teaching-support-v2-01.json").read_bytes())


if __name__ == "__main__":
    unittest.main()
