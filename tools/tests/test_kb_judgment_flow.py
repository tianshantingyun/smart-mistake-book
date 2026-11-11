# -*- coding: utf-8 -*-
"""判定流程 ↔ judge_ledger 接线用例（r6 三层键 + failed-only 重开）。

钉住三件事：
1. `work_key` 由 block_key/content_sha/processor_version 三层键组成：只改 chunk_id
   不改身份，改正文（公式/数字）必改身份；
2. 判决 CSV 已有、但某块缺行时，账本记 `failed/transient` 而不是静默 pending；
3. `reopen` 只放回 failed，judged 永不重开；CLI 五个子命令能真实跑通。
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

from kb_coverage import judge_ledger as jl  # noqa: E402
from kb_coverage import judgment_flow as flow  # noqa: E402
from kb_coverage import judgments  # noqa: E402

VERSION = "prompt:abc123|model:m@1|rules:r1|canon:text-nfc-lf-v1"


class JudgmentFlowTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-judgment-flow-"))
        self.db = self.tmp / "ledger.sqlite3"
        self.slices = self.tmp / "slices"
        self.verdicts = self.tmp / "verdicts"
        self.slices.mkdir()
        self.verdicts.mkdir()
        self.conn = jl.init_db(self.db, backup=False)

    def tearDown(self):
        self.conn.close()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _slice_row(self, chunk_id: str, text: str, heading: str = "第一章") -> dict:
        return {
            "chunk_rel": "src/a.docx",
            "chunk_id": chunk_id,
            "heading": heading,
            "text": text,
            "subject": "MATH",
            "fp": "cjk-only-fp",
        }

    def _write_slice(self, name: str, rows: list[dict]) -> Path:
        path = self.slices / f"{name}.jsonl"
        path.write_text(
            "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows),
            encoding="utf-8",
        )
        return path

    def _verdict_row(self, chunk_id: str, action: str = "MATERIAL", midx: str = "") -> dict:
        row = {column: "" for column in judgments.COLUMNS}
        row.update({
            "chunk_rel": "src/a.docx",
            "chunk_id": chunk_id,
            "action": action,
            "node_slug": "some-node",
            "type": "CONCEPT_EXPLANATION",
            "title": "标题",
            "summary": "摘要",
            "applicability": "适用",
            "content": "正文",
            "boundary": "边界",
            "note": "" if action == "MATERIAL" else "机械跳过",
            "midx": midx,
        })
        return row

    def _write_verdicts(self, name: str, rows: list[dict]) -> Path:
        path = self.verdicts / f"{name}.csv"
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=judgments.COLUMNS)
            writer.writeheader()
            writer.writerows(rows)
        return path

    def test_work_key_uses_three_keys_and_ignores_chunk_id_renumber(self):
        row = self._slice_row("abc-001", "设 $x=1$，求值。")
        bkey1, csha1, wkey1 = flow._identity(row, VERSION, 1)
        renumbered = {**row, "chunk_id": "abc-999"}
        bkey2, csha2, wkey2 = flow._identity(renumbered, VERSION, 1)

        self.assertEqual(bkey1, bkey2, "chunk_id 重排不得改变 block_key")
        self.assertEqual(csha1, csha2)
        self.assertEqual(wkey1, wkey2, "work_key 不得因 chunk_id 重排而变")

        changed = {**row, "text": "设 $x=2$，求值。"}
        bkey3, csha3, wkey3 = flow._identity(changed, VERSION, 1)
        self.assertEqual(bkey1, bkey3, "同一逻辑位置")
        self.assertNotEqual(csha1, csha3, "公式/数字变化必须改 content_sha")
        self.assertNotEqual(wkey1, wkey3, "内容变化必须是新的 work_key")

        _, _, other_version = flow._identity(row, "prompt:other|model:m@2|rules:r1|canon:v1", 1)
        self.assertNotEqual(wkey1, other_version, "换处理器版本是有意的新工作")

    def test_settle_marks_missing_verdict_row_failed_and_reopen_only_failed(self):
        self._write_slice("slice-a", [
            self._slice_row("abc-001", "设 $x=1$，求值。", heading="第一章"),
            self._slice_row("abc-002", "设 $y=2$，求值。", heading="第二章"),
        ])
        self._write_verdicts("slice-a", [self._verdict_row("abc-001")])

        ingested = flow.ingest_slices(self.conn, self.slices, processor_version=VERSION,
                                      backup=False)
        self.assertEqual({"files": 1, "rows": 2, "inserted": 2, "existing": 0}, ingested)
        self.assertEqual({"pending": 2, "judged": 0, "failed": 0, "total": 2},
                         flow.status(self.conn))

        settled = flow.settle_verdicts(self.conn, self.slices, self.verdicts,
                                       processor_version=VERSION, backup=False)
        self.assertEqual(1, settled["judged"])
        self.assertEqual(1, settled["failed"])
        self.assertEqual({"pending": 0, "judged": 1, "failed": 1, "total": 2},
                         flow.status(self.conn))

        failed = self.conn.execute(
            "SELECT * FROM block_ledger WHERE status = 'failed'").fetchone()
        self.assertIn("verdict row missing", failed["last_error"])
        self.assertEqual("transient", failed["failure_class"])
        self.assertEqual(1, failed["attempts"], "一次 settle = 一次 claim/attempt")
        failed_key = failed["work_key"]

        reopened = flow.reopen_failed(self.conn, max_attempts=3, backup=False)
        self.assertEqual([failed_key], reopened)
        self.assertEqual({"pending": 1, "judged": 1, "failed": 0, "total": 2},
                         flow.status(self.conn))
        self.assertEqual([], flow.reopen_failed(self.conn, max_attempts=3, backup=False),
                         "第二次重开幂等（judged 不重开、pending 不再中选）")

        # 补齐判决后再结账：reopened 行转 judged，已 judged 行保持不变。
        self._write_verdicts("slice-a", [
            self._verdict_row("abc-001"),
            self._verdict_row("abc-002"),
        ])
        settled = flow.settle_verdicts(self.conn, self.slices, self.verdicts,
                                       processor_version=VERSION, backup=False)
        self.assertEqual(1, settled["judged"])
        self.assertEqual(1, settled["unchanged"])
        self.assertEqual({"pending": 0, "judged": 2, "failed": 0, "total": 2},
                         flow.status(self.conn))
        rejudged = self.conn.execute(
            "SELECT * FROM block_ledger WHERE work_key = ?", (failed_key,)).fetchone()
        self.assertEqual(2, rejudged["attempts"], "reopen 后的再次结账保留并累加 attempts")

    def test_judged_rows_never_reopen_even_with_permanent_flag(self):
        self._write_slice("slice-b", [self._slice_row("abc-003", "设 $z=3$，求值。")])
        self._write_verdicts("slice-b", [self._verdict_row("abc-003")])
        flow.ingest_slices(self.conn, self.slices, processor_version=VERSION, backup=False)
        flow.settle_verdicts(self.conn, self.slices, self.verdicts,
                             processor_version=VERSION, backup=False)
        self.assertEqual(1, flow.status(self.conn)["judged"])

        self.assertEqual(
            [],
            flow.reopen_failed(self.conn, max_attempts=99, include_permanent=True,
                               reason="不应重开 judged", backup=False),
        )
        self.assertEqual(1, flow.status(self.conn)["judged"])

    def test_cli_ingest_settle_reopen_status_roundtrip(self):
        self._write_slice("slice-c", [
            self._slice_row("abc-004", "设 $a=4$，求值。"),
            self._slice_row("abc-005", "设 $b=5$，求值。", heading="第二章"),
        ])
        self._write_verdicts("slice-c", [self._verdict_row("abc-004")])

        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertEqual(0, flow.main([
                "--db", str(self.db),
                "ingest", "--slices", str(self.slices),
                "--processor-version", VERSION, "--no-backup",
            ]))
            self.assertEqual(0, flow.main([
                "--db", str(self.db),
                "settle", "--slices", str(self.slices),
                "--verdicts", str(self.verdicts),
                "--processor-version", VERSION, "--no-backup",
            ]))
            self.assertEqual(0, flow.main([
                "--db", str(self.db),
                "reopen", "--max-attempts", "3", "--no-backup",
            ]))
            self.assertEqual(0, flow.main(["--db", str(self.db), "status"]))

        text = output.getvalue()
        lines = [line for line in text.splitlines() if line.strip()]
        self.assertIn('"files": 1', text)
        self.assertIn('"judged": 1', text)
        self.assertIn('"failed": 1', text)
        self.assertIn('"reopened": 1', text)
        self.assertEqual({"pending": 1, "judged": 1, "failed": 0, "total": 2},
                         json.loads(lines[-1]))


if __name__ == "__main__":
    unittest.main()
