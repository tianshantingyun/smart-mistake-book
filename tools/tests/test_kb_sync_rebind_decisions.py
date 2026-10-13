# -*- coding: utf-8 -*-
"""sync_rebind_decisions 的 (chunk_id, midx) 键回归用例。

## 它消灭的失败

裁定表里同一个 chunk_id 可以有多行（同块多条材料，midx ''/b/c…；本批实测 7 个
chunk_id 出现 2–3 行）。旧实现把匹配键定为 `chunk_id` 单键——`decided[chunk_id]`
后写覆盖先写，然后对源文件里**同块的所有行**应用同一个节点：两条不同 midx 的裁定
被串行错配（a 行的裁定盖到 b 行，或反过来），回写后的判定源与侧车节点不一致，
`merge_text_judgments --resync` 仍会把判定表拉回去、复发 slug 撞车。

本用例先于实现书写：旧逻辑下 `test_same_chunk_two_midx_rows_get_their_own_nodes`
必失败（两行同值），改键后必过。
"""

from __future__ import annotations

import csv
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
import sys

sys.path.insert(0, str(REPO / "tools"))
from kb_build import pack_io  # noqa: E402
from kb_coverage import sync_rebind_decisions as S  # noqa: E402

VERDICT_COLS = ["material_slug", "chunk_id", "sidecar_node", "judgment_node",
                "decided_node", "confidence", "reason"]
VERDICT_COLS_MIDX = VERDICT_COLS + ["midx"]


def _write_csv(path: Path, cols: list[str], rows: list[dict], eol: str = "\r\n") -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=cols, lineterminator=eol)
        writer.writeheader()
        writer.writerows({k: r.get(k, "") for k in cols} for r in rows)


def _src_row(**over) -> dict:
    base = {k: "" for k in S.HDR}
    base.update({"chunk_rel": "生物/1.docx", "chunk_id": "aaaa111111-001", "action": "MATERIAL",
                 "node_slug": "旧一", "type": "METHOD_MODEL", "title": "T1", "summary": "S1",
                 "applicability": "A1", "content": "C1", "boundary": "B1", "midx": ""})
    base.update(over)
    return base


def _verdict(**over) -> dict:
    base = {"material_slug": "ext-bio-aaaa111111-001", "chunk_id": "aaaa111111-001",
            "sidecar_node": "甲", "judgment_node": "旧一", "decided_node": "甲",
            "confidence": "high", "reason": "r", "midx": ""}
    base.update(over)
    return base


class SyncRebindDecisionsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-sync-rebind-", dir=pack_io.REPO / "build"))
        self.vdir = self.tmp / "jv"
        self.src = self.vdir / "s1.jsonl.csv"
        self.verdicts = self.tmp / "verdicts.csv"
        self._orig_verdicts, self._orig_const = S.VERDICTS, S.VERDICT_CSV
        S.VERDICTS = self.vdir

    def tearDown(self):
        S.VERDICTS, S.VERDICT_CSV = self._orig_verdicts, self._orig_const
        import shutil
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _seed_src(self) -> None:
        _write_csv(self.src, S.HDR, [
            _src_row(),
            _src_row(node_slug="旧二", title="T2", midx="b"),
            _src_row(chunk_id="bbbb222222-002", chunk_rel="生物/2.docx", node_slug="旧三"),
        ])

    def _src_rows(self) -> list[dict]:
        with self.src.open(encoding="utf-8-sig", newline="") as fh:
            return list(csv.DictReader(fh))

    def test_same_chunk_two_midx_rows_get_their_own_nodes(self):
        """核心回归：同 chunk_id 两行（midx ''/b）的裁定必须各改各自的行。

        旧逻辑（chunk_id 单键）下：decided['aaaa111111-001'] 被后一行（乙）覆盖，
        两行都写成乙 → 本断言在 first 行失败。
        """
        self._seed_src()
        before = self._src_rows()
        _write_csv(self.verdicts, VERDICT_COLS_MIDX, [
            _verdict(),
            _verdict(sidecar_node="乙", judgment_node="旧二", decided_node="乙", midx="b"),
            _verdict(material_slug="ext-bio-bbbb222222-002", chunk_id="bbbb222222-002",
                     sidecar_node="丙", judgment_node="旧三", decided_node="丙"),
        ])
        S.VERDICT_CSV = self.verdicts
        rc = S.main(["--write"])
        self.assertEqual(0, rc)
        rows = self._src_rows()
        self.assertEqual(["甲", "乙", "丙"], [r["node_slug"] for r in rows])
        # 只改 node_slug 一个字段：其余列逐字不变
        for b, a in zip(before, rows):
            for col in S.HDR:
                if col == "node_slug":
                    continue
                self.assertEqual(b[col], a[col], f"{a['chunk_id']}[{a['midx']}].{col}")

    def test_stale_stored_midx_row_is_matched_by_merge_derived_key(self):
        """源里存的 midx 是旧值（'b'），但 merge 会把唯一一行重排成 ''（组内序号）。

        裁定键 (chunk_id, '') 必须落到这一行，否则 resync 仍会回退（实测 450188d41e-013）。
        """
        _write_csv(self.src, S.HDR, [_src_row(node_slug="旧一", midx="b")])
        _write_csv(self.verdicts, VERDICT_COLS_MIDX, [_verdict()])   # 裁定 (cid, '') → 甲
        S.VERDICT_CSV = self.verdicts
        self.assertEqual(0, S.main(["--write"]))
        self.assertEqual("甲", self._src_rows()[0]["node_slug"])

    def test_decision_midx_maps_to_group_position_not_stored_midx(self):
        """同块三行存的 midx 是 b/c/d，merge 会重排成 ''/b/c。

        裁定 (chunk_id, 'b') 必须落在组内第 2 行（存 'c'），不是存 'b' 的第 1 行
        （实测 e14aad82a5-320：按存值写会改错行、resync 依旧回退）。
        """
        _write_csv(self.src, S.HDR, [
            _src_row(title="T-一", node_slug="旧一", midx="b"),
            _src_row(title="T-二", node_slug="旧二", midx="c"),
            _src_row(title="T-三", node_slug="旧三", midx="d"),
        ])
        _write_csv(self.verdicts, VERDICT_COLS_MIDX, [_verdict(
            sidecar_node="乙", judgment_node="旧二", decided_node="乙", midx="b")])
        S.VERDICT_CSV = self.verdicts
        self.assertEqual(0, S.main(["--write"]))
        self.assertEqual(["旧一", "乙", "旧三"], [r["node_slug"] for r in self._src_rows()])

    def test_legacy_table_without_midx_column_keys_on_empty_midx(self):
        """老裁定表无 midx 列 → 按 midx='' 处理：同块 midx='b' 的行不被动。"""
        self._seed_src()
        _write_csv(self.verdicts, VERDICT_COLS, [_verdict()])
        S.VERDICT_CSV = self.verdicts
        self.assertEqual(0, S.main(["--write"]))
        self.assertEqual(["甲", "旧二", "旧三"], [r["node_slug"] for r in self._src_rows()])

    def test_explicit_verdicts_path_overrides_default(self):
        """`--verdicts PATH` 用显式路径，而非模块默认常量。"""
        self._seed_src()
        other = self.tmp / "other.csv"
        _write_csv(self.verdicts, VERDICT_COLS, [_verdict()])                    # 默认说甲
        _write_csv(other, VERDICT_COLS, [_verdict(sidecar_node="丙", decided_node="丙")])
        S.VERDICT_CSV = self.verdicts
        self.assertEqual(0, S.main(["--verdicts", str(other), "--write"]))
        self.assertEqual("丙", self._src_rows()[0]["node_slug"])

    def test_report_mode_leaves_files_untouched(self):
        self._seed_src()
        _write_csv(self.verdicts, VERDICT_COLS, [_verdict()])
        S.VERDICT_CSV = self.verdicts
        before = self.src.read_bytes()
        self.assertEqual(0, S.main([]))
        self.assertEqual(before, self.src.read_bytes())

    def test_rerun_is_idempotent(self):
        self._seed_src()
        _write_csv(self.verdicts, VERDICT_COLS_MIDX, [_verdict(), _verdict(
            sidecar_node="乙", judgment_node="旧二", decided_node="乙", midx="b")])
        S.VERDICT_CSV = self.verdicts
        self.assertEqual(0, S.main(["--write"]))
        after_first = self.src.read_bytes()
        self.assertEqual(0, S.main(["--write"]))
        self.assertEqual(after_first, self.src.read_bytes())

    def test_conflicting_duplicate_key_raises(self):
        """同 (chunk_id, midx) 两条裁定给不同节点 → 拒绝写盘（不得静默取一个）。"""
        self._seed_src()
        _write_csv(self.verdicts, VERDICT_COLS, [
            _verdict(),
            _verdict(sidecar_node="乙", decided_node="乙", confidence="low"),
        ])
        S.VERDICT_CSV = self.verdicts
        with self.assertRaises(SystemExit):
            S.main(["--write"])
        self.assertEqual("旧一", self._src_rows()[0]["node_slug"])   # 未写盘

    def test_write_preserves_lf_eol(self):
        """LF 判定源写回后仍是 LF：旧实现硬编码 CRLF，会把整文件行尾翻掉（diff 噪音）。"""
        _write_csv(self.src, S.HDR, [_src_row()], eol="\n")
        _write_csv(self.verdicts, VERDICT_COLS, [_verdict()])
        S.VERDICT_CSV = self.verdicts
        self.assertEqual(0, S.main(["--write"]))
        raw = self.src.read_bytes()
        self.assertNotIn(b"\r\n", raw)
        self.assertEqual("甲", self._src_rows()[0]["node_slug"])

    def test_write_preserves_crlf_eol(self):
        """CRLF 判定源写回后仍是 CRLF（种子默认 CRLF；防未来又改回固定写法）。"""
        self._seed_src()
        _write_csv(self.verdicts, VERDICT_COLS, [_verdict()])
        S.VERDICT_CSV = self.verdicts
        self.assertEqual(0, S.main(["--write"]))
        raw = self.src.read_bytes()
        self.assertIn(b"\r\n", raw)
        self.assertEqual(raw.count(b"\n"), raw.count(b"\r\n"))

    def test_non_material_rows_are_not_rewritten(self):
        """源里同键是 SKIP 行（node_slug 为空）→ 不写节点（保留旧行为）。"""
        _write_csv(self.src, S.HDR, [_src_row(action="SKIP", node_slug="", type="", title="",
                                             summary="", applicability="", content="",
                                             boundary="", note="装饰页")])
        _write_csv(self.verdicts, VERDICT_COLS, [_verdict()])
        S.VERDICT_CSV = self.verdicts
        self.assertEqual(0, S.main(["--write"]))
        self.assertEqual("", self._src_rows()[0]["node_slug"])


if __name__ == "__main__":
    unittest.main()
