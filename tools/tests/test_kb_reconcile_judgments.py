# -*- coding: utf-8 -*-
"""reconcile_judgments（P0.3）：12 死节点重指、698 坏 type 行移重判队列、幂等。

夹具复刻真实形态：一块可出多条材料（同 chunk 键、midx 区分），
重判队列按行身份 (chunk_rel, chunk_id, midx) 去重。
"""

from __future__ import annotations

import csv
import tempfile
import unittest
from pathlib import Path

from kb_coverage import reconcile_judgments as rj

ALLOWED = ("CONCEPT_EXPLANATION", "METHOD_MODEL", "MISCONCEPTION_GUIDE")


def _row(chunk_rel="a.pdf", chunk_id="0000000000-001", midx="", action="MATERIAL",
         node_slug="node-a", type_="CONCEPT_EXPLANATION", **over):
    r = {"chunk_rel": chunk_rel, "chunk_id": chunk_id, "action": action,
         "node_slug": node_slug, "type": type_, "title": "t", "summary": "s",
         "applicability": "a", "content": "c", "boundary": "b", "note": "", "midx": midx}
    r.update(over)
    return r


NODES = {"MATH": {"node-a", "survivor"}, "PHYSICS": {"p-node"}}
CHUNKS = {("a.pdf", "0000000000-001"): {"subject": "MATH"},
          ("b.pdf", "0000000000-002"): {"subject": "PHYSICS"}}
MERGE = {("MATH", "dead-slug"): "survivor"}


class PlanTest(unittest.TestCase):
    def test_redirects_merged_dead_slug(self):
        rows = [_row(node_slug="dead-slug")]
        pl = rj.plan(rows, CHUNKS, NODES, MERGE)
        self.assertEqual([], pl["problems"])
        self.assertEqual(1, len(pl["redirects"]))
        self.assertEqual("survivor", pl["redirects"][0][1])

    def test_twelve_dead_rows_redirect(self):
        # 真实形态：9 个死 slug 占 12 行（「逆序相加…」4 行）→ 12 行全部重指
        rows = [_row(chunk_rel=f"r{i}.pdf", chunk_id=f"0000000000-{i:03d}",
                      node_slug="dead-slug") for i in range(12)]
        pl = rj.plan(rows, {(f"r{i}.pdf", f"0000000000-{i:03d}"): {"subject": "MATH"}
                            for i in range(12)}, NODES, MERGE)
        self.assertEqual(12, len(pl["redirects"]))
        self.assertEqual(0, len(pl["problems"]))

    def test_698_bad_type_rows_move(self):
        # 真实构成：REPRESENTATION_GUIDE 519 / DERIVATION 150 / WORKED_EXAMPLE 25 /
        # COMPLETE_SOLUTION 4 = 698
        types = (["REPRESENTATION_GUIDE"] * 519 + ["DERIVATION"] * 150
                 + ["WORKED_EXAMPLE"] * 25 + ["COMPLETE_SOLUTION"] * 4)
        rows = [_row(chunk_rel=f"c{i}.pdf", chunk_id=f"9999999999-{i:03d}", type_=t)
                for i, t in enumerate(types)]
        chunks = {(f"c{i}.pdf", f"9999999999-{i:03d}"): {"subject": "MATH"}
                  for i in range(len(types))}
        pl = rj.plan(rows, chunks, NODES, MERGE)
        self.assertEqual(698, len(pl["moves"]))
        self.assertEqual(0, len(pl["redirects"]))
        self.assertEqual([], pl["problems"])

    def test_allowed_types_untouched(self):
        rows = [_row(type_=t) for t in ALLOWED]
        pl = rj.plan(rows, CHUNKS, NODES, MERGE)
        self.assertEqual(0, len(pl["moves"]))
        self.assertEqual(0, len(pl["redirects"]))
        self.assertEqual([], pl["problems"])

    def test_skip_rows_never_moved_even_with_bad_type(self):
        rows = [_row(action="SKIP", type_="", node_slug=""),
                _row(action="SKIP", type_="WORKED_EXAMPLE", node_slug="node-a")]
        pl = rj.plan(rows, CHUNKS, NODES, MERGE)
        self.assertEqual(0, len(pl["moves"]))
        self.assertEqual(0, len(pl["redirects"]))
        self.assertEqual([], pl["problems"])

    def test_missing_slug_without_merge_is_blocking(self):
        rows = [_row(node_slug="ghost")]
        pl = rj.plan(rows, CHUNKS, NODES, MERGE)
        self.assertEqual(1, len(pl["problems"]))
        with self.assertRaises(ValueError):
            rj.apply(pl, [_row(node_slug="ghost")], judgments_path=Path("x.csv"),
                     queue_path=Path("q.csv"))

    def test_survivor_missing_from_pack_is_blocking(self):
        merge = {("MATH", "dead-slug"): "also-ghost"}
        rows = [_row(node_slug="dead-slug")]
        pl = rj.plan(rows, CHUNKS, NODES, merge)
        self.assertTrue(pl["problems"])

    def test_cross_subject_slug_is_blocking(self):
        # chunk 属 MATH，node_slug 却是 PHYSICS 的 p-node → 跨科，拒绝静默改绑
        rows = [_row(node_slug="p-node")]
        pl = rj.plan(rows, CHUNKS, NODES, MERGE)
        self.assertTrue(any("无合并后继" in p for p in pl["problems"]), pl["problems"])

    def test_bad_type_takes_priority_over_dead_slug(self):
        # 坏 type 行直接进重判队列，不做死节点重指
        rows = [_row(node_slug="dead-slug", type_="DERIVATION")]
        pl = rj.plan(rows, CHUNKS, NODES, MERGE)
        self.assertEqual(1, len(pl["moves"]))
        self.assertEqual(0, len(pl["redirects"]))

    def test_sibling_with_good_type_not_moved(self):
        # 同一块两条材料：midx '' 好 type，midx 'b' 坏 type → 只动 'b'（回归：曾按块键误伤）
        rows = [_row(midx="", type_="METHOD_MODEL"),
                _row(midx="b", type_="WORKED_EXAMPLE")]
        pl = rj.plan(rows, CHUNKS, NODES, MERGE)
        self.assertEqual(1, len(pl["moves"]))
        self.assertEqual("b", pl["moves"][0]["midx"])


class ApplyTest(unittest.TestCase):
    def _apply(self, rows):
        with tempfile.TemporaryDirectory() as td:
            jp, qp = Path(td) / "judgments.csv", Path(td) / "queue.csv"
            pl = rj.plan(rows, CHUNKS, NODES, MERGE)
            stats = rj.apply(pl, rows, judgments_path=jp, queue_path=qp)
            with jp.open(encoding="utf-8", newline="") as fh:
                written = list(csv.DictReader(fh))
            with qp.open(encoding="utf-8", newline="") as fh:
                queued = list(csv.DictReader(fh))
            return stats, written, queued, pl, td, jp, qp

    def test_write_moves_bad_rows_to_skip_with_note(self):
        rows = [_row(midx="", type_="METHOD_MODEL"),
                _row(midx="b", type_="WORKED_EXAMPLE")]
        stats, written, queued, pl, *_ = self._apply(rows)
        self.assertEqual(1, stats["moved"])
        self.assertEqual(1, stats["queue_appended"])
        self.assertEqual("SKIP", written[1]["action"])
        self.assertEqual("已入重判队列(type=WORKED_EXAMPLE)", written[1]["note"])
        self.assertEqual("MATERIAL", written[0]["action"])  # 同块好行不受影响
        self.assertEqual(["chunk_rel", "chunk_id", "midx", "node_slug", "type", "note"],
                         list(queued[0].keys()))
        self.assertEqual(("a.pdf", "0000000000-001", "b"),
                         (queued[0]["chunk_rel"], queued[0]["chunk_id"], queued[0]["midx"]))
        self.assertEqual("WORKED_EXAMPLE", queued[0]["type"])

    def test_write_redirects_node_slug_only(self):
        rows = [_row(node_slug="dead-slug")]
        stats, written, queued, pl, *_ = self._apply(rows)
        self.assertEqual(1, stats["redirected"])
        self.assertEqual("survivor", written[0]["node_slug"])
        self.assertEqual("MATERIAL", written[0]["action"])
        self.assertEqual(0, stats["moved"])

    def test_write_preserves_row_count_and_order(self):
        rows = [_row(chunk_id="0000000000-001", node_slug="node-a"),
                _row(chunk_id="0000000000-001", midx="b", node_slug="node-a"),
                _row(chunk_rel="b.pdf", chunk_id="0000000000-002", node_slug="p-node")]
        stats, written, *_ = self._apply(rows)
        self.assertEqual(3, len(written))
        self.assertEqual(["", "b", ""], [r["midx"] for r in written])

    def test_idempotent_second_apply_appends_nothing(self):
        rows = [_row(type_="DERIVATION")]
        with tempfile.TemporaryDirectory() as td:
            jp, qp = Path(td) / "judgments.csv", Path(td) / "queue.csv"
            pl = rj.plan(rows, CHUNKS, NODES, MERGE)
            stats1 = rj.apply(pl, rows, judgments_path=jp, queue_path=qp)
            # 重跑：计划里已经是 SKIP 行 → 0 改写；队列按行身份去重 → 0 追加
            pl2 = rj.plan(rows, CHUNKS, NODES, MERGE)
            stats2 = rj.apply(pl2, rows, judgments_path=jp, queue_path=qp)
            self.assertEqual(0, len(pl2["moves"]))
            self.assertEqual(0, stats2["moved"])
            self.assertEqual(0, stats2["queue_appended"])
            with qp.open(encoding="utf-8", newline="") as fh:
                queued = list(csv.DictReader(fh))
            self.assertEqual(1, len(queued))
            self.assertEqual(1, stats1["queue_appended"])

    def test_queue_dedupes_on_row_identity(self):
        # 同一计划重复落盘：重判队列按 (chunk_rel, chunk_id, midx) 去重，不重复追加
        rows1 = [_row(type_="DERIVATION")]
        rows2 = [_row(type_="DERIVATION")]
        with tempfile.TemporaryDirectory() as td:
            jp, qp = Path(td) / "judgments.csv", Path(td) / "queue.csv"
            pl = rj.plan(rows1, CHUNKS, NODES, MERGE)
            s1 = rj.apply(pl, rows1, judgments_path=jp, queue_path=qp)
            s2 = rj.apply(pl, rows2, judgments_path=jp, queue_path=qp)
            self.assertEqual(1, s1["queue_appended"])
            self.assertEqual(0, s2["queue_appended"])
            with qp.open(encoding="utf-8", newline="") as fh:
                self.assertEqual(1, len(list(csv.DictReader(fh))))


if __name__ == "__main__":
    unittest.main()
