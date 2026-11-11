# -*- coding: utf-8 -*-
"""office_extract 内容指纹与写前守卫用例。

它消灭的失败：
1. `_fp` 只哈希 CJK，公式/数字/拉丁字母变化不改变指纹 → 判定账本静默复用旧结果；
2. 旧池行的 CJK-only `fp` 与新全内容指纹混用 → 重跑时重复写入同一块；
3. 追加块池前不备份、不检查他线改写 → 半批写入后无法回滚/覆盖别人的行。
"""

from __future__ import annotations

import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from kb_coverage import office_extract as oe  # noqa: E402
from kb_coverage.content_identity import content_sha, canonical_text  # noqa: E402


class FingerprintTest(unittest.TestCase):
    def test_full_content_changes_invalidate(self):
        self.assertNotEqual(oe._fp("设 $x=1$，求值。"), oe._fp("设 $x=2$，求值。"))
        self.assertNotEqual(oe._fp("作用力 10 N"), oe._fp("作用力 20 N"))
        self.assertNotEqual(oe._fp("符号 ABC"), oe._fp("符号 ABD"))
        self.assertEqual(oe._fp("同一块内容"), content_sha("同一块内容"))

    def test_newline_and_unicode_form_are_canonicalized(self):
        self.assertEqual(oe._fp("a\r\nb"), oe._fp("a\nb"))
        self.assertEqual(oe._fp("é"), oe._fp("e\u0301"))
        self.assertEqual(oe._fp("a\r\nb"), content_sha(canonical_text("a\r\nb")))

    def test_seen_keys_recompute_full_hash_for_legacy_cjk_only_rows(self):
        tmp = Path(tempfile.mkdtemp(prefix="office-fp-"))
        pool = tmp / "extracted_chunks.jsonl"
        text = "设 $x=1$，求值。"
        pool.write_text(json.dumps({
            "rel_path": "a.docx", "chunk_id": "abc-001", "text": text,
            "fp": "legacy-cjk-only-hash",
        }, ensure_ascii=False) + "\n", encoding="utf-8")
        try:
            with mock.patch.object(oe, "CHUNKS", pool):
                _, fps = oe._seen_keys()
            self.assertIn(content_sha(text), fps,
                          "旧行必须按全内容重算，才能与新口径去重")
            self.assertNotIn("legacy-cjk-only-hash", fps)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)


class RunGuardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="office-run-"))
        self.pool = self.tmp / "extracted_chunks.jsonl"
        self.backup = self.pool.with_name(self.pool.name + ".bak")
        self.states = {
            "a.docx": {"rel_path": "a.docx", "subject": "MATH", "ext": ".docx",
                       "state": "PENDING"},
        }
        self.patches = [
            mock.patch.object(oe, "CHUNKS", self.pool),
            mock.patch.object(oe.es, "load_states", lambda: self.states),
            mock.patch.object(oe.es, "mark", lambda *a, **k: None),
        ]
        for p in self.patches:
            p.start()
        self.addCleanup(lambda: [p.stop() for p in reversed(self.patches)])
        self.addCleanup(lambda: shutil.rmtree(self.tmp, ignore_errors=True))

    def _chunk(self, text="同一块正文。"):
        return {"rel_path": "a.docx", "subject": "MATH", "chunk_id": "abc-001",
                "heading": "第一章", "text": text, "fp": oe._fp(text)}

    def test_repeated_run_writes_once_and_backs_up_existing_pool(self):
        with mock.patch.object(oe, "extract_file", lambda rel, subj: [self._chunk()]):
            first = oe.run(None, None)
        self.assertEqual(1, first["chunks"])
        self.assertEqual("", first["backup"], "首次运行池不存在，无需备份")
        lines = [l for l in self.pool.read_text(encoding="utf-8").splitlines() if l]
        self.assertEqual(1, len(lines))

        with mock.patch.object(oe, "extract_file", lambda rel, subj: [self._chunk()]):
            second = oe.run(None, None)
        self.assertEqual(0, second["chunks"], "重跑不得重复写入")
        self.assertEqual(1, second["deduped"])
        self.assertEqual(1, second["skipped_seen"])
        self.assertTrue(self.backup.exists(), "追加前必须备份已有块池")
        self.assertEqual(1, len([l for l in self.pool.read_text(encoding="utf-8").splitlines()
                                 if l]))

    def test_write_guard_aborts_when_pool_changed_during_batch(self):
        # 先放一行已有内容，保证有备份；用受控签名模拟“第一块写完后他线追加”。
        self.pool.write_text(json.dumps({
            "rel_path": "old.docx", "chunk_id": "old-001", "text": "已有内容。",
            "fp": oe._fp("已有内容。"),
        }, ensure_ascii=False) + "\n", encoding="utf-8")
        self.states["b.docx"] = {"rel_path": "b.docx", "subject": "MATH", "ext": ".docx",
                                 "state": "PENDING"}
        signatures = iter([(1, 1), (1, 1), (2, 2), (3, 3)])
        with mock.patch.object(oe, "extract_file", lambda rel, subj: [self._chunk("新块。")]):
            with mock.patch.object(oe, "_file_signature", lambda p: next(signatures)):
                with self.assertRaises(RuntimeError):
                    oe.run(None, None)
        self.assertTrue(self.backup.exists(), "守卫失败前已有写前备份")


if __name__ == "__main__":
    unittest.main()
