# -*- coding: utf-8 -*-
"""merge_text_judgments 的切片轮改造用例：目录口径 / SKIP 保留 / 3 值白名单 / 幂等追加。

背景（协议 v1.1）：判定产物按片落在 `knowledge-production/judgment-verdicts/`；
SKIP 是显式裁定也必须落表；type 白名单收紧到 3 值；追加进判定表必须幂等（既有键跳过）。
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
from kb_coverage import merge_text_judgments as M  # noqa: E402


def _write_csv(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=M.HDR)
        writer.writeheader()
        writer.writerows(rows)


def _row(**over) -> dict:
    base = {"chunk_rel": "数学/1.docx", "chunk_id": "aaaa111111-001", "action": "MATERIAL",
            "node_slug": "node-a", "type": "METHOD_MODEL", "title": "标题", "summary": "摘要",
            "applicability": "PRIMARY", "content": "一行。", "boundary": "边界。", "note": "",
            "midx": ""}
    base.update(over)
    return base


class _MergeFixture(unittest.TestCase):
    """共用夹具：一个只含 node-a 的包 + 一条块池记录 + 空的临时判定表。"""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-merge-slice-", dir=pack_io.REPO / "build"))
        pack = {"schemaVersion": 2, "packId": "test-pack", "taxonomyVersion": "test-pack",
                "subjects": [{"subject": "MATH", "topics": [{"slug": "t1", "name": "主题一",
                             "knowledgePoints": [
                                 {"slug": "node-a", "name": "节点甲", "aliases": ["节点甲"],
                                  "kind": "CONCEPT", "boundary": "定位：测。真边界。",
                                  "sourceLocator": "人教版高中教材（2019）", "prerequisiteSlugs": []}]}]}]}
        (self.tmp / pack_io.PACK_NAME).write_text(json.dumps(pack, ensure_ascii=False),
                                                 encoding="utf-8")
        pack_io.use_directory(self.tmp)
        # 池：一条块（哈希前缀 aaaa111111 = 1.docx）
        self.pool = self.tmp / "chunks.jsonl"
        self.pool.write_text(json.dumps({"rel_path": "数学/1.docx", "chunk_id": "aaaa111111-001",
                                         "subject": "MATH", "heading": "h", "text": "t", "fp": "f"})
                             + "\n", encoding="utf-8")
        self.vdir = self.tmp / "verdicts"
        self.target = self.tmp / "judgments.csv"

    def tearDown(self):
        pack_io.reset_directory()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _merge(self):
        orig = (M.CHUNKS, M.OUT_CSV, M.OUT_NEW)
        M.CHUNKS, M.OUT_CSV, M.OUT_NEW = self.pool, self.tmp / "merged.csv", self.tmp / "new.json"
        try:
            return M.merge("", self.vdir)
        finally:
            M.CHUNKS, M.OUT_CSV, M.OUT_NEW = orig


class MergeTextJudgmentsSliceModeTest(_MergeFixture):
    def test_skip_is_kept_and_types_tightened(self):
        _write_csv(self.vdir / "s1.csv", [
            _row(),
            _row(action="SKIP", node_slug="", type="", title="", summary="", applicability="",
                 content="", boundary="", note="装饰页"),
            _row(type="DERIVATION"),          # 旧白名单的 7 值之一 → 现在必须被拒
        ])
        res = self._merge()
        self.assertEqual(2, len(res["merged"]))          # MATERIAL + SKIP
        self.assertEqual(1, res["stats"]["skips"])
        self.assertTrue(any("type=DERIVATION" in p for p in res["problems"]))

    def test_unknown_node_becomes_new(self):
        _write_csv(self.vdir / "s1.csv", [_row(node_slug="完全不存在")])
        res = self._merge()
        self.assertEqual("NEW:完全不存在", res["merged"][0]["node_slug"])
        self.assertEqual(1, res["repairs"]["未知节点转 NEW"])

    def test_new_proposal_becomes_skip_row(self):
        """`NEW:` 行不落表为 MATERIAL（表内实测 0 条 NEW、materialize 不认）——转 SKIP+note，草稿另存。"""
        _write_csv(self.vdir / "s1.csv", [_row(node_slug="NEW:MATH/某新点")])
        res = self._merge()
        orig = M.OUT_NEW_FULL
        M.OUT_NEW_FULL = self.tmp / "new_full.csv"
        try:
            applied = M.apply_to_judgments(res["merged"], self.target)
        finally:
            M.OUT_NEW_FULL = orig
        self.assertEqual(1, applied["added"])
        self.assertEqual(1, applied["new_proposals"])
        rows = list(csv.DictReader(self.target.open(encoding="utf-8-sig", newline="")))
        self.assertEqual("SKIP", rows[0]["action"])
        self.assertEqual("", rows[0]["node_slug"])
        self.assertTrue(rows[0]["note"].startswith("NEW:MATH/某新点"))
        self.assertEqual("", rows[0]["content"])
        full = list(csv.DictReader((self.tmp / "new_full.csv").open(encoding="utf-8-sig", newline="")))
        self.assertEqual(1, len(full))
        self.assertEqual("NEW:MATH/某新点", full[0]["node_slug"])

    def test_apply_is_idempotent(self):
        _write_csv(self.vdir / "s1.csv", [_row(), _row(action="SKIP", node_slug="", type="",
                                                       title="", summary="", applicability="",
                                                       content="", boundary="", note="重复")])
        res = self._merge()
        first = M.apply_to_judgments(res["merged"], self.target)
        self.assertEqual(2, first["added"])
        second = M.apply_to_judgments(res["merged"], self.target)
        self.assertEqual(0, second["added"])
        self.assertEqual(2, second["skipped_existing"])
        self.assertEqual(2, second["total"])
        # 再合并一遍新片（同键的块）→ 依然不重复
        third = M.apply_to_judgments(res["merged"], self.target)
        self.assertEqual(2, third["total"])


class MergeTextJudgmentsResyncTest(_MergeFixture):
    """`--resync` 的两种同步：既有行字段级覆盖 + 同块新增 midx 的**补行**。

    补行是 2026-10-02 修的真缺陷：判重按 (chunk_rel, chunk_id)，判定侧给已入表的块
    追加第二条材料（midx=b/c…）时会被整批静默丢掉（实测 20 条 append 全丢）。
    """

    def _seed_table(self, rows):
        return M.apply_to_judgments(rows, self.target)

    def _table(self):
        with self.target.open(encoding="utf-8-sig", newline="") as fh:
            return list(csv.DictReader(fh))

    def test_without_resync_changed_fields_stay_stale(self):
        self._seed_table([_row()])
        res = M.apply_to_judgments([_row(content="改后。")], self.target)
        self.assertEqual(0, res["added"])
        self.assertEqual(1, res["skipped_existing"])
        self.assertEqual("一行。", self._table()[0]["content"])   # 默认关：表里还是旧内容

    def test_resync_overwrites_only_differing_fields(self):
        self._seed_table([_row()])
        res = M.apply_to_judgments([_row(content="改后。")], self.target, resync=True)
        self.assertEqual(1, res["resynced"])
        self.assertEqual(0, res["added"])
        self.assertTrue(any("content" in k for k in res["resynced_keys"]), res["resynced_keys"])
        self.assertEqual("改后。", self._table()[0]["content"])

    def test_new_midx_row_is_dropped_without_resync(self):
        """旧行为留档：同块第二条材料在默认模式下进不了表。"""
        self._seed_table([_row()])
        res = M.apply_to_judgments([_row(), _row(midx="b", content="第二条。")], self.target)
        self.assertEqual(0, res["added"])
        self.assertEqual(1, res["total"])
        self.assertEqual(1, len(self._table()))

    def test_resync_appends_new_midx_row(self):
        self._seed_table([_row()])
        res = M.apply_to_judgments([_row(), _row(midx="b", content="第二条。")],
                                   self.target, resync=True)
        self.assertEqual(1, res["added"])
        self.assertEqual(1, res["skipped_existing"])
        self.assertEqual(2, res["total"])
        self.assertTrue(any("（补行）" in k for k in res["resynced_keys"]), res["resynced_keys"])
        rows = self._table()
        self.assertEqual(["", "b"], [r["midx"] for r in rows])
        self.assertEqual("第二条。", rows[1]["content"])

    def test_resync_append_is_idempotent(self):
        self._seed_table([_row()])
        merged = [_row(), _row(midx="b", content="第二条。")]
        M.apply_to_judgments(merged, self.target, resync=True)
        again = M.apply_to_judgments(merged, self.target, resync=True)
        self.assertEqual(0, again["added"])
        self.assertEqual(0, again["resynced"])
        self.assertEqual(2, again["total"])

    def test_resync_appended_then_rewritten_row_syncs(self):
        """补行之后再修正正文（材料重落的常态）：同键同 midx 走字段级覆盖。"""
        self._seed_table([_row()])
        M.apply_to_judgments([_row(midx="b", content="初稿。")], self.target, resync=True)
        res = M.apply_to_judgments([_row(midx="b", content="定稿。")], self.target, resync=True)
        self.assertEqual(0, res["added"])
        self.assertEqual(1, res["resynced"])
        self.assertEqual("定稿。", self._table()[1]["content"])

    def test_resync_appended_new_proposal_becomes_skip(self):
        self._seed_table([_row()])
        orig = M.OUT_NEW_FULL
        M.OUT_NEW_FULL = self.tmp / "new_full.csv"
        try:
            res = M.apply_to_judgments(
                [_row(midx="c", node_slug="NEW:MATH/某新点")], self.target, resync=True)
        finally:
            M.OUT_NEW_FULL = orig
        self.assertEqual(1, res["added"])
        row = self._table()[1]
        self.assertEqual("SKIP", row["action"])
        self.assertEqual("", row["node_slug"])
        self.assertEqual("c", row["midx"])
        self.assertTrue(row["note"].startswith("NEW:MATH/某新点"))


if __name__ == "__main__":
    unittest.main()
