# -*- coding: utf-8 -*-
"""rekey_pool 用例：重排只动撞键 rel、按出现顺序确定性编号、幂等、判定重映射与 fp 对拍。

它消灭的失败很具体：块池键唯一化的核心不变式被改坏——① 重排动了键本就唯一的 rel
（会连带打断 sidecar slug 与状态机 output_ref）；② 重跑不幂等（映射表/判定表反复改写）；
③ 判定行引用撞键时被盲猜重绑到错误池行。这三条都钉在这里。
"""

from __future__ import annotations

import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))

from kb_coverage import rekey_pool as rk  # noqa: E402

JCOLS = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title", "summary",
         "applicability", "content", "boundary", "note", "midx"]


def _judgment(chunk_rel, chunk_id, action="MATERIAL", content="判定改写内容"):
    return dict(chunk_rel=chunk_rel, chunk_id=chunk_id, action=action,
                node_slug="n", type="CONCEPT_EXPLANATION", title="t", summary="s",
                applicability="a", content=content, boundary="b", note="", midx="")


class RekeyTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="rekey-"))
        self.chunks = self.tmp / "extracted_chunks.jsonl"
        self.backup = self.tmp / "extracted_chunks.jsonl.bak"
        self.remap = self.tmp / "key_remap.csv"
        self.judgments = self.tmp / "material_judgments.csv"
        self.mismatch = self.tmp / "fp_mismatch.csv"
        for name, path in (("CHUNKS", self.chunks), ("BACKUP", self.backup),
                           ("REMAP", self.remap), ("JUDGMENTS", self.judgments),
                           ("MISMATCH", self.mismatch)):
            p = mock.patch.object(rk, name, path)
            p.start()
            self.addCleanup(p.stop)

    def _write_pool(self, rows):
        with self.chunks.open("w", encoding="utf-8") as fh:
            for r in rows:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")

    def _write_remap(self, rows):
        with self.remap.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=list(rk.REMAP_COLS), lineterminator="\n")
            w.writeheader()
            w.writerows(rows)

    def _write_judgments(self, rows):
        with self.judgments.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=JCOLS, lineterminator="\n")
            w.writeheader()
            w.writerows(rows)

    def _pool_rows(self):
        return [json.loads(l) for l in self.chunks.read_text(encoding="utf-8").splitlines()
                if l.strip()]

    def test_renumber_plan_touches_only_collided_rels(self):
        base = rk.base_of("a.pdf")
        rows = [
            {"rel_path": "a.pdf", "chunk_id": f"{base}-001", "fp": "f1", "text": "t1"},
            {"rel_path": "a.pdf", "chunk_id": f"{base}-001", "fp": "f2", "text": "t2"},
            {"rel_path": "a.pdf", "chunk_id": f"{base}-002", "fp": "f3", "text": "t3"},
            {"rel_path": "b.pdf", "chunk_id": "bbbb-007", "fp": "f4", "text": "t4"},
            {"rel_path": "b.pdf", "chunk_id": "bbbb-003", "fp": "f5", "text": "t5"},
        ]
        new_rows, remap = rk.renumber_plan(rows)
        # a.pdf 撞键 → 按出现顺序重排 001..003；b.pdf 键唯一（虽有空洞）→ 一律不动
        self.assertEqual([f"{base}-001", f"{base}-002", f"{base}-003"],
                         [r["chunk_id"] for r in new_rows if r["rel_path"] == "a.pdf"])
        self.assertEqual(["bbbb-007", "bbbb-003"],
                         [r["chunk_id"] for r in new_rows if r["rel_path"] == "b.pdf"])
        self.assertEqual([f"f{i}" for i in (1, 2, 3)],
                         [r["fp"] for r in new_rows if r["rel_path"] == "a.pdf"],
                         "重排必须保持行内容（fp）不变")
        self.assertEqual(3, len(remap), "映射表含全部重排 rel 行（含 old==new）")

    def test_full_run_is_idempotent(self):
        base = rk.base_of("a.pdf")
        self._write_pool([
            {"rel_path": "a.pdf", "chunk_id": f"{base}-001", "fp": "f1", "text": "t1"},
            {"rel_path": "a.pdf", "chunk_id": f"{base}-001", "fp": "f2", "text": "t2"},
            {"rel_path": "b.pdf", "chunk_id": "bbbb-001", "fp": "f3", "text": "t3"},
        ])
        self._write_judgments([_judgment("b.pdf", "bbbb-001", action="SKIP")])
        rc = rk.main()
        self.assertEqual(0, rc)
        self.assertTrue(self.backup.exists(), "重排前必须备份块池")
        pool1 = self.chunks.read_text(encoding="utf-8")
        remap1 = self.remap.read_text(encoding="utf-8")
        j1 = self.judgments.read_text(encoding="utf-8")
        pool = self._pool_rows()
        keys = [(r["rel_path"], r["chunk_id"]) for r in pool]
        self.assertEqual(len(keys), len(set(keys)), "重排后键必须唯一")
        # 重跑：不变化
        rc2 = rk.main()
        self.assertEqual(0, rc2)
        self.assertEqual(pool1, self.chunks.read_text(encoding="utf-8"))
        self.assertEqual(remap1, self.remap.read_text(encoding="utf-8"))
        self.assertEqual(j1, self.judgments.read_text(encoding="utf-8"))

    def test_judgment_unique_old_key_is_remapped(self):
        base = rk.base_of("a.pdf")
        self._write_pool([
            {"rel_path": "a.pdf", "chunk_id": f"{base}-003", "fp": "f3", "text": "t3"},
            {"rel_path": "a.pdf", "chunk_id": f"{base}-001", "fp": "f1", "text": "t1"},
            {"rel_path": "a.pdf", "chunk_id": f"{base}-001", "fp": "f2", "text": "t2"},
        ])
        self._write_judgments([_judgment("a.pdf", f"{base}-003")])
        rc = rk.main()
        self.assertEqual(0, rc)
        with self.judgments.open(encoding="utf-8", newline="") as fh:
            got = list(csv.DictReader(fh))
        # 旧键 base-003 唯一 → 重映射为按出现顺序的新键 base-001，且池行 fp 与映射表一致
        self.assertEqual(f"{base}-001", got[0]["chunk_id"])
        self.assertFalse(self.mismatch.exists())

    def test_remap_judgments_fp_mismatch_recorded(self):
        self._write_remap([{"chunk_rel": "a.pdf", "old_chunk_id": "x-001",
                            "new_chunk_id": "x-002", "fp": "FAKE"}])
        self._write_judgments([_judgment("a.pdf", "x-001")])
        pool_rows = [{"rel_path": "a.pdf", "chunk_id": "x-002", "fp": "REAL"}]
        remapped, mismatches, n = rk.remap_judgments(pool_rows)
        self.assertEqual(0, remapped)
        self.assertEqual(1, len(mismatches))
        self.assertIn("fp 不一致", mismatches[0]["reason"])

    def test_remap_judgments_collision_unmatched_is_mismatch(self):
        self._write_remap([
            {"chunk_rel": "a.pdf", "old_chunk_id": "x-001", "new_chunk_id": "x-002", "fp": "A"},
            {"chunk_rel": "a.pdf", "old_chunk_id": "x-001", "new_chunk_id": "x-003", "fp": "B"},
        ])
        self._write_judgments([_judgment("a.pdf", "x-001")])
        pool_rows = [{"rel_path": "a.pdf", "chunk_id": "x-002", "fp": "A"},
                     {"rel_path": "a.pdf", "chunk_id": "x-003", "fp": "B"}]
        remapped, mismatches, n = rk.remap_judgments(pool_rows)
        self.assertEqual(0, remapped, "撞键匹配不上时不得盲猜重绑")
        self.assertEqual(1, len(mismatches))
        self.assertIn("撞键且内容指纹匹配不上", mismatches[0]["reason"])

    def test_remap_judgments_dangling_reference_is_mismatch(self):
        pool_rows = [{"rel_path": "a.pdf", "chunk_id": "x-002", "fp": "A"}]
        self._write_judgments([_judgment("b.pdf", "y-001")])
        remapped, mismatches, n = rk.remap_judgments(pool_rows)
        self.assertEqual(0, remapped)
        self.assertEqual(1, len(mismatches))
        self.assertIn("悬空", mismatches[0]["reason"])


if __name__ == "__main__":
    unittest.main()
