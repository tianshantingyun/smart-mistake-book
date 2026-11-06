# -*- coding: utf-8 -*-
"""判定账本（r6）用例：身份键、全内容失效、状态机、幂等与 failed-only 重开。

三条必须钉住的失败：
1. 公式/数字改了，旧判定仍被复用（旧 CJK-only 指纹不可见）；
2. chunk_id 重排后同一逻辑块被当成新块（重复计费/绑错内容）；
3. 同一块重复跑被重复计数（attempts 被 ingest 重置或多插行）。

另外钉住写前守卫（payload 哈希不符拒写）与写前备份（`.bak` 是写前状态）。
"""

from __future__ import annotations

import hashlib
import json
import shutil
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from kb_coverage import judge_ledger as jl  # noqa: E402
from kb_coverage.content_identity import content_sha  # noqa: E402

SEP = "\x1f"


class LedgerTestBase(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-ledger-"))
        self.db = self.tmp / "ledger.sqlite3"
        self.conn = jl.init_db(self.db, backup=False)

    def tearDown(self):
        self.conn.close()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _ingest(self, *, text="设 $x=1$，求结果。", locator=None, chunk_id=None,
                source_rel="src/a.docx", stage="judge", processor_version="p1", **over):
        return jl.ingest(
            self.conn,
            stage=stage,
            source_rel=source_rel,
            locator=locator if locator is not None else {"page": 3, "ordinal": 1},
            processor_version=processor_version,
            text=text,
            chunk_id=chunk_id,
            **over,
        )

    def _rows(self):
        return [dict(r) for r in self.conn.execute(
            "SELECT * FROM block_ledger ORDER BY created_at, work_key")]

    def _one(self, work_key):
        return dict(self.conn.execute(
            "SELECT * FROM block_ledger WHERE work_key = ?", (work_key,)).fetchone())


class IdentityTest(LedgerTestBase):
    def test_work_key_is_exact_four_part_formula(self):
        bkey = "b" * 64
        csha = "c" * 64
        expected = hashlib.sha256(
            SEP.join(("judge", bkey, csha, "prompt:abc|model:m@1")).encode("utf-8")
        ).hexdigest()
        self.assertEqual(expected, jl.work_key("judge", bkey, csha, "prompt:abc|model:m@1"))

    def test_content_sha_sees_formula_and_numbers_not_only_cjk(self):
        self.assertNotEqual(content_sha("设 $x=1$，求值。"), content_sha("设 $x=2$，求值。"))
        self.assertNotEqual(content_sha("作用力 10 N"), content_sha("作用力 20 N"))
        self.assertNotEqual(content_sha("符号 ABC"), content_sha("符号 ABD"))
        self.assertEqual(content_sha("同一块内容"), content_sha("同一块内容"))

    def test_block_key_ignores_chunk_id_but_requires_a_locator(self):
        with self.assertRaises(ValueError):
            jl.block_key("src/a.docx", {"chunk_id": "abc-001"})
        self.assertEqual(
            jl.block_key("src/a.docx", {"page": 3, "chunk_id": "abc-001"}),
            jl.block_key("src/a.docx", {"page": 3, "chunk_id": "abc-999"}),
            "chunk_id 不得进入 block_key",
        )

    def test_chunk_id_renumber_keeps_one_row_and_identity(self):
        """真实迁移形态：只重排 chunk_id，切片行序/heading/正文不变。"""
        row = {"chunk_rel": "src/a.docx", "heading": "第一章", "text": "同一块正文。",
               "subject": "MATH", "content_sha": content_sha("同一块正文。")}
        first = self.tmp / "slice-1.jsonl"
        second = self.tmp / "slice-2.jsonl"
        first.write_text(json.dumps({**row, "chunk_id": "abc-001"}, ensure_ascii=False) + "\n",
                         encoding="utf-8")
        second.write_text(json.dumps({**row, "chunk_id": "abc-099"}, ensure_ascii=False) + "\n",
                          encoding="utf-8")

        s1 = jl.ingest_jsonl(self.conn, first, stage="judge", processor_version="p1",
                             backup=False)
        s2 = jl.ingest_jsonl(self.conn, second, stage="judge", processor_version="p1",
                             backup=False)

        self.assertEqual({"rows": 1, "inserted": 1, "existing": 0}, s1)
        self.assertEqual({"rows": 1, "inserted": 0, "existing": 1}, s2)
        rows = self._rows()
        self.assertEqual(1, len(rows), "重排 chunk_id 不得新增第二行")
        self.assertEqual(0, rows[0]["attempts"])
        self.assertEqual("pending", rows[0]["status"])

    def test_formula_or_number_change_supersedes_old_current_row(self):
        old, created = self._ingest(text="设 $x=1$，求值。", chunk_id="abc-001")
        self.assertTrue(created)
        new, created2 = self._ingest(text="设 $x=2$，求值。", chunk_id="abc-001")
        self.assertTrue(created2)

        self.assertNotEqual(old["work_key"], new["work_key"])
        self.assertEqual(old["block_key"], new["block_key"], "同一逻辑位置身份不变")
        self.assertNotEqual(old["content_sha"], new["content_sha"], "公式变化必须改内容身份")
        rows = self._rows()
        self.assertEqual(2, len(rows))
        current = [r for r in rows if r["superseded_by"] is None]
        self.assertEqual([new["work_key"]], [r["work_key"] for r in current])
        self.assertEqual(new["work_key"], self._one(old["work_key"])["superseded_by"])
        self.assertEqual("pending", new["status"])
        # 被取代的旧 pending 行不得再被领取/判定；当前进度只数当前版本。
        claimed = jl.claim(self.conn, stage="judge", owner="w1")
        self.assertEqual([new["work_key"]], [r["work_key"] for r in claimed])
        with self.assertRaises(ValueError):
            jl.judge(self.conn, old["work_key"], result_path="old.json")
        self.assertEqual({"pending": 1, "judged": 0, "failed": 0, "total": 1},
                         jl.status_counts(self.conn, stage="judge"))
        self.assertEqual(2, jl.status_counts(self.conn, stage="judge",
                                             current_only=False)["total"])


class StateMachineTest(LedgerTestBase):
    def test_repeated_ingest_and_claim_do_not_double_count(self):
        row, created = self._ingest(chunk_id="abc-001")
        self.assertTrue(created)
        key = row["work_key"]

        for _ in range(2):
            again, created = self._ingest(chunk_id="abc-001")
            self.assertFalse(created)
            self.assertEqual(key, again["work_key"])
        self.assertEqual(1, len(self._rows()))
        self.assertEqual(0, self._one(key)["attempts"], "ingest 不得增加 attempts")

        claimed = jl.claim(self.conn, stage="judge", owner="w1")
        self.assertEqual([key], [r["work_key"] for r in claimed])
        self.assertEqual(1, self._one(key)["attempts"])
        self.assertEqual([], jl.claim(self.conn, stage="judge", owner="w2"),
                         "活跃租约期间不得被再次领取")

        # 重复 ingest 也不能把 attempts 重置
        self._ingest(chunk_id="abc-001")
        self.assertEqual(1, self._one(key)["attempts"])

        judged, changed = jl.judge(self.conn, key, result_path="result.json")
        self.assertTrue(changed)
        self.assertEqual("judged", judged["status"])
        again, changed = jl.judge(self.conn, key, result_path="result.json")
        self.assertFalse(changed)
        self.assertEqual([], jl.claim(self.conn, stage="judge", owner="w3"))
        self.assertEqual({"pending": 0, "judged": 1, "failed": 0, "total": 1},
                         jl.status_counts(self.conn, stage="judge"))

    def test_expired_lease_is_reclaimable_and_attempts_only_increments_on_claim(self):
        row, _ = self._ingest()
        key = row["work_key"]
        jl.claim(self.conn, stage="judge", owner="w1", lease_seconds=60,
                 now="2026-01-01T00:00:00+00:00")
        self.assertEqual([], jl.claim(self.conn, stage="judge", owner="w2", lease_seconds=60,
                                      now="2026-01-01T00:00:30+00:00"))
        claimed = jl.claim(self.conn, stage="judge", owner="w2", lease_seconds=60,
                           now="2026-01-01T00:02:00+00:00")
        self.assertEqual(1, len(claimed))
        self.assertEqual(2, self._one(key)["attempts"])
        self.assertEqual("w2", self._one(key)["lease_owner"])

    def test_failed_only_reopen_with_permanent_and_integrity_gates(self):
        # transient：正常重开，attempts 保留
        row, _ = self._ingest(text="transient 行")
        key = row["work_key"]
        jl.claim(self.conn, stage="judge", owner="w1")
        jl.fail(self.conn, key, failure_class="transient", error="provider timeout")
        self.assertEqual([key], jl.reopen_failed(self.conn, stage="judge", max_attempts=3))
        after = self._one(key)
        self.assertEqual("pending", after["status"])
        self.assertEqual(1, after["attempts"], "重开不得重置 attempts")
        self.assertEqual(1, after["reopen_epoch"])
        self.assertEqual([], jl.reopen_failed(self.conn, stage="judge", max_attempts=3),
                         "第二次重开必须幂等（0 行）")

        # permanent：默认不重开；显式 include_permanent + reason 才重开
        jl.claim(self.conn, stage="judge", owner="w2")
        jl.fail(self.conn, key, failure_class="permanent", error="bad input")
        self.assertEqual([], jl.reopen_failed(self.conn, stage="judge", max_attempts=3))
        with self.assertRaises(ValueError):
            jl.reopen_failed(self.conn, stage="judge", max_attempts=3,
                             include_permanent=True)
        self.assertEqual([key], jl.reopen_failed(self.conn, stage="judge", max_attempts=3,
                                                 include_permanent=True, reason="已修输入"))
        self.assertEqual(2, self._one(key)["reopen_epoch"])

        # integrity：永不重开
        row2, _ = self._ingest(text="integrity 行", locator={"page": 4, "ordinal": 1})
        key2 = row2["work_key"]
        jl.fail(self.conn, key2, failure_class="integrity", error="payload sha mismatch")
        self.assertEqual([], jl.reopen_failed(self.conn, stage="judge", max_attempts=10,
                                              include_permanent=True, reason="不应重开"))

        # judged 永不重开
        row3, _ = self._ingest(text="judged 行", locator={"page": 5, "ordinal": 1})
        key3 = row3["work_key"]
        jl.judge(self.conn, key3, result_path="r3.json")
        self.assertEqual([], jl.reopen_failed(self.conn, stage="judge", max_attempts=10,
                                              include_permanent=True, reason="不应重开"))

    def test_failed_row_requires_reopen_before_judge(self):
        row, _ = self._ingest()
        key = row["work_key"]
        jl.claim(self.conn, stage="judge", owner="w1")
        jl.fail(self.conn, key, failure_class="policy", error="blocked")
        with self.assertRaises(ValueError):
            jl.judge(self.conn, key, result_path="r.json")
        jl.reopen_failed(self.conn, stage="judge", max_attempts=2)
        judged, changed = jl.judge(self.conn, key, result_path="r.json")
        self.assertTrue(changed)
        self.assertEqual("judged", judged["status"])


class WriteGuardTest(LedgerTestBase):
    def test_payload_hash_mismatch_is_rejected_before_any_row(self):
        payload = self.tmp / "payload.txt"
        payload.write_text("设 $x=2$，求值。", encoding="utf-8")
        with self.assertRaises(jl.LedgerIntegrityError):
            jl.ingest(
                self.conn, stage="judge", source_rel="src/a.docx",
                locator={"page": 1, "ordinal": 1}, processor_version="p1",
                content_sha=content_sha("设 $x=1$，求值。"), payload_path=payload,
            )
        self.assertEqual([], self._rows(), "守卫失败不得落行")

        row, created = jl.ingest(
            self.conn, stage="judge", source_rel="src/a.docx",
            locator={"page": 1, "ordinal": 1}, processor_version="p1",
            content_sha=content_sha("设 $x=2$，求值。"), payload_path=payload,
        )
        self.assertTrue(created)
        self.assertEqual(str(payload), row["payload_path"])

    def test_ingest_jsonl_backs_up_db_before_writing(self):
        first, _ = self._ingest(text="已有行")
        second = {"chunk_rel": "src/b.docx", "chunk_id": "b-001", "heading": "第二章",
                  "text": "新增行", "subject": "MATH"}
        path = self.tmp / "slice.jsonl"
        path.write_text(json.dumps(second, ensure_ascii=False) + "\n", encoding="utf-8")

        stats = jl.ingest_jsonl(self.conn, path, stage="judge",
                                processor_version="p1", backup=True)
        self.assertEqual(1, stats["inserted"])
        backup = self.db.with_name(self.db.name + ".bak")
        self.assertTrue(backup.exists(), "写库前必须备份为 .bak")
        old = sqlite3.connect(str(backup))
        try:
            n = old.execute("SELECT COUNT(*) FROM block_ledger").fetchone()[0]
        finally:
            old.close()
        self.assertEqual(1, n, "备份必须是写前状态（只含已有行）")
        self.assertEqual(2, len(self._rows()))


if __name__ == "__main__":
    unittest.main()
