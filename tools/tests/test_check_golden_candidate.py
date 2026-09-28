# -*- coding: utf-8 -*-
"""`kb_coverage.check_golden_candidate` 的契约测试（正反两侧）。

正侧：用**真包**里 12 条真实 (subject, slug, topic) 组一个合法批次 ⇒ 通过。
反侧：各改坏一处 ⇒ 必须被拒，且**拒绝理由是对应的那条断言**（断言 stdout 里的 C-编号，
而不是只看退出码——否则"因为别的原因挂了"会伪装成通过）。

为什么批次要 12 条：风格代理量（数值/公式 ≥12、辨析 ≥8）是**批次级**判据，
单条候选必然触发 C8。测试必须造出能过批次判据的形态，才测得到单条缺陷。

判据见 `docs/kb-golden-v2-protocol.md` §1 与 §5。
"""

from __future__ import annotations

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from kb_coverage import check_golden_candidate as checker

REPO = Path(__file__).resolve().parents[2]
PACK = REPO / "core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json"
GOLDEN = REPO / "tools/kb_coverage/tables/golden_queries_v1.json"

BATCH_SIZE = 12


def real_triples(count: int) -> list[tuple[str, str, str]]:
    """从真包里按 (subject, slug) 唯一地取 count 组 (subject, slug, topic)。"""
    pack = json.loads(PACK.read_text(encoding="utf-8"))
    seen: set[tuple[str, str]] = set()
    out: list[tuple[str, str, str]] = []
    for subject in pack["subjects"]:
        for topic in subject["topics"]:
            for point in topic.get("knowledgePoints") or []:
                key = (subject["subject"], point["slug"])
                if key in seen:
                    continue
                seen.add(key)
                out.append((subject["subject"], point["slug"], topic["slug"]))
                if len(out) == count:
                    return out
    raise AssertionError(f"包里的知识点不足以取 {count} 组")


def make_batch() -> list[dict]:
    """12 条：全部含数字与"为什么"，满足 C8 的两条代理量。"""
    return [
        {
            "chapter": chapter,
            "expectedSlug": slug,
            "query": f"第{i + 1}步为什么不能直接把 {i + 2} 代入公式算，要先判断条件吗？",
            "subject": subject,
        }
        for i, (subject, slug, chapter) in enumerate(real_triples(BATCH_SIZE))
    ]


class CandidateCheckTest(unittest.TestCase):
    def _run(self, candidate: list[dict]) -> tuple[int, str]:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "candidate.json"
            path.write_text(json.dumps(candidate, ensure_ascii=False), encoding="utf-8")
            buffer = io.StringIO()
            with contextlib.redirect_stdout(buffer):
                code = checker.main(["--candidate", str(path), "--golden", str(GOLDEN), "--pack", str(PACK)])
            return code, buffer.getvalue()

    def test_well_formed_batch_passes(self):
        code, output = self._run(make_batch())
        self.assertEqual(0, code, output)
        self.assertIn("机械校验通过", output)

    def test_wrong_chapter_is_rejected_with_c4(self):
        batch = make_batch()
        batch[0]["chapter"] = "这不是任何 topic 的 slug"
        code, output = self._run(batch)
        self.assertEqual(1, code)
        self.assertIn("C4 #1", output)

    def test_unknown_slug_is_rejected_with_c3(self):
        batch = make_batch()
        batch[2]["expectedSlug"] = "绝对不存在的知识点名"
        code, output = self._run(batch)
        self.assertEqual(1, code)
        self.assertIn("C3 #3", output)

    def test_subject_mismatch_is_rejected_as_unknown_pair(self):
        batch = make_batch()
        batch[1]["subject"] = "PHYSICS" if batch[1]["subject"] != "PHYSICS" else "MATH"
        code, output = self._run(batch)
        self.assertEqual(1, code)
        self.assertIn("C3 #2", output)

    def test_duplicate_of_existing_golden_query_is_rejected_with_c5(self):
        existing = json.loads(GOLDEN.read_text(encoding="utf-8"))
        batch = make_batch()
        batch[0]["query"] = existing[0]["query"]
        code, output = self._run(batch)
        self.assertEqual(1, code)
        self.assertIn("C5", output)

    def test_short_query_without_cjk_is_rejected_with_c2(self):
        batch = make_batch()
        batch[0]["query"] = "abc 123"
        code, output = self._run(batch)
        self.assertEqual(1, code)
        self.assertIn("C2 #1", output)


if __name__ == "__main__":
    unittest.main()
