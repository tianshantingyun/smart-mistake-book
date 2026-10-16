# -*- coding: utf-8 -*-
"""verify_judgment_fp 用例 v2：判据收窄为「fp 锚定迁移窗守卫」，钉住**复用假阳性类**。

## 它消灭的失败（v1 → v2）

v1 的判据是「映射条目 `old != new` 且判定表引用了 old ⇒ 判定未重映射」。这条推理把
**两个 id 空间**当成一个：key_remap 的 old 是 P0.2 重排**之前**的稠密编号
（`sha256(rel)[:10]-NNN`），判定行的键却是重排**之后**的现空间 id，两者同前缀同位数、
稠密重叠——重排后新行会顶掉别的块的旧序号。于是"判定引用了某个 old 键"只说明该
in-use id 恰好落在历史口分布里，证明不了任何未重映射。实树实测：v1 判据命中 6,011 条，
逐条核 fp 后**全部**是复用类（`池[old].fp != 条目.fp`）。单例（背景单例，本批探针复核）：
判定行键 `5c62da9e03-1133`（化学 53，池中 fp 4644e3fc5d68）而映射表那一条
1133→1446 的 fp 是 0151fd444c17（另一块）。

## v2 判据（可靠替代）

只在**内容锚**成立时报"疑似"：`old != new` 且判定表引用 old 且 池中仍有 old 且
条目 fp 非空且 `池[old].fp == 条目.fp`。fp 相等 = 旧 id 上仍持同一内容，这是迁移窗
（重映射未落全，或内容相同的另一行占了旧序号）的特征，两种可能都只能人工裁决，
故报「疑似未重映射」而**不自动处置**；fp 不等 = 旧序号已被他块占用（复用），不报。

真实断链的其余形态由既有四项覆盖：判定悬空 / 判定撞键 / 新键不在池 / fp 不一致
（实树四项读数均为 0，见批报）。本文件同时钉住这四项与「池行无 fp」「池键重复」。

## 红样例（先红后绿）

`test_reuse_class_old_id_held_by_another_block_not_flagged`、
`test_mixed_identity_and_migration_window_only_nonidentity_counts` 在 v1 实现下报红
（v1 把复用当未重映射）；`test_migration_window_suspect_flagged` 断言的
「疑似未重映射」文案在 v1 下不存在。三者先对 v1 跑出红、改实现后转绿。
"""

from __future__ import annotations

import contextlib
import csv
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))

from kb_coverage import judgments  # noqa: E402
from kb_coverage import verify_judgment_fp as vjf  # noqa: E402

REMAP_COLS = ("chunk_rel", "old_chunk_id", "new_chunk_id", "fp")


def _judgment(chunk_rel, chunk_id):
    return dict(chunk_rel=chunk_rel, chunk_id=chunk_id, action="MATERIAL",
                node_slug="n", type="CONCEPT_EXPLANATION", title="t", summary="s",
                applicability="a", content="判定改写内容", boundary="b", note="", midx="")


class JudgmentFpTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="jfp-"))
        self.chunks = self.tmp / "extracted_chunks.jsonl"
        self.judgments = self.tmp / "material_judgments"
        self.remap = self.tmp / "key_remap.csv"
        for name, path in (("CHUNKS", self.chunks), ("JUDGMENTS", self.judgments),
                           ("REMAP", self.remap)):
            p = mock.patch.object(vjf, name, path)
            p.start()
            self.addCleanup(p.stop)

    def _write_pool(self, rows):
        with self.chunks.open("w", encoding="utf-8") as fh:
            for r in rows:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")

    def _write_remap(self, rows):
        with self.remap.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=list(REMAP_COLS), lineterminator="\n")
            w.writeheader()
            w.writerows(rows)

    def _write_judgments(self, rows):
        """判定表写进 self.judgments 片目录（与生产同一份 write_all）。"""
        judgments.write_all(rows, self.judgments)

    def _run(self):
        """跑门一次，返回 (exit, stdout, stderr)。问题清单只打前 20 条，夹具保持小。"""
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            rc = vjf.main()
        return rc, out.getvalue(), err.getvalue()

    def _problems(self, out: str) -> int:
        """从 stdout 摘要行解析问题数（首个以「判定 」开头的行）。"""
        line = [ln for ln in out.splitlines() if ln.startswith("判定 ")][0]
        return int(line.rsplit("问题 ", 1)[1])

    # ---- v2 判据：复用假阳性类 / 迁移窗正样例 ----------------------------------

    def test_reuse_class_old_id_held_by_another_block_not_flagged(self):
        """**红样例（复用假阳性类）**：池[old] 已被另一块占用（fp 不同）。

        v1 判据只看「old != new 且判定引用 old」，必报；v2 要求 fp 相等才报——
        池[old].fp != 条目.fp ⇒ 旧序号已被他块复用，判定行指的就是现空间那块。
        """
        self._write_pool([
            {"rel_path": "a.pdf", "chunk_id": "x-001", "fp": "OTHER-BLOCK"},
            {"rel_path": "a.pdf", "chunk_id": "x-002", "fp": "MOVED"},
        ])
        self._write_remap([{"chunk_rel": "a.pdf", "old_chunk_id": "x-001",
                            "new_chunk_id": "x-002", "fp": "MOVED"}])
        self._write_judgments([_judgment("a.pdf", "x-001")])
        rc, out, err = self._run()
        self.assertEqual(0, rc, f"复用被当成未重映射：{err!r}")
        self.assertEqual(0, self._problems(out))

    def test_migration_window_suspect_flagged(self):
        """正样例：旧 id 仍持**同一内容**（fp 相等）⇒ 迁移窗特征，报「疑似」待裁决。"""
        self._write_pool([
            {"rel_path": "a.pdf", "chunk_id": "x-001", "fp": "SAME"},
            {"rel_path": "a.pdf", "chunk_id": "x-002", "fp": "SAME"},
        ])
        self._write_remap([{"chunk_rel": "a.pdf", "old_chunk_id": "x-001",
                            "new_chunk_id": "x-002", "fp": "SAME"}])
        self._write_judgments([_judgment("a.pdf", "x-001")])
        rc, out, err = self._run()
        self.assertEqual(1, rc, "旧 id 仍持旧内容的疑似行被漏放")
        self.assertEqual(1, err.count("疑似未重映射"), f"{err!r}")
        self.assertEqual(1, self._problems(out))

    def test_migration_window_suspect_needs_nonempty_entry_fp(self):
        """内容锚必须有内容：条目 fp 为空时不得凭「空 == 空」报疑似。"""
        self._write_pool([
            {"rel_path": "a.pdf", "chunk_id": "x-001", "fp": ""},
            {"rel_path": "a.pdf", "chunk_id": "x-002", "fp": ""},
        ])
        self._write_remap([{"chunk_rel": "a.pdf", "old_chunk_id": "x-001",
                            "new_chunk_id": "x-002", "fp": ""}])
        self._write_judgments([_judgment("a.pdf", "x-001")])
        rc, out, err = self._run()
        self.assertEqual(0, err.count("疑似未重映射"), f"空 fp 造出疑似：{err!r}")
        self.assertEqual(1, err.count("池行无 fp"), f"{err!r}")
        self.assertEqual(1, self._problems(out))

    def test_identity_entry_no_longer_flagged(self):
        """恒等条目（old == new）：池里那条就是它自己；v2 的 fp 相等子句对它恒真，
        必须靠 old != new 排除，否则又会造出恒等假阳性（v1 实测 2,109 条）。"""
        self._write_pool([{"rel_path": "a.pdf", "chunk_id": "x-001", "fp": "fp1"}])
        self._write_remap([{"chunk_rel": "a.pdf", "old_chunk_id": "x-001",
                            "new_chunk_id": "x-001", "fp": "fp1"}])
        self._write_judgments([_judgment("a.pdf", "x-001")])
        rc, out, err = self._run()
        self.assertEqual(0, rc, f"恒等条目不该拦门：{err!r}")
        self.assertEqual(0, self._problems(out))

    def test_true_dangling_covered_by_dangling_check(self):
        """v1 非恒等用例按新语义改写：真断链（旧键已不在池中）由「判定悬空」覆盖，
        不靠「疑似未重映射」——防修过头：判据收窄后这条链仍必须被拦。"""
        self._write_pool([{"rel_path": "a.pdf", "chunk_id": "x-002", "fp": "fp2"}])
        self._write_remap([{"chunk_rel": "a.pdf", "old_chunk_id": "x-001",
                            "new_chunk_id": "x-002", "fp": "fp2"}])
        self._write_judgments([_judgment("a.pdf", "x-001")])
        rc, out, err = self._run()
        self.assertEqual(1, rc, "真断链被漏放")
        self.assertEqual(1, err.count("判定悬空"), f"{err!r}")
        self.assertEqual(0, err.count("疑似未重映射"), f"{err!r}")

    def test_mixed_identity_and_migration_window_only_nonidentity_counts(self):
        """混合：同 old 下恒等与非恒等并存，且非恒等条目 fp 与池[old] 相等 →
        只有非恒等那条报「疑似」，恒等那条不计入（v1 原混合用例按新语义改写）。"""
        self._write_pool([
            {"rel_path": "a.pdf", "chunk_id": "x-001", "fp": "SAME"},
            {"rel_path": "a.pdf", "chunk_id": "x-002", "fp": "SAME"},
        ])
        self._write_remap([
            {"chunk_rel": "a.pdf", "old_chunk_id": "x-001",
             "new_chunk_id": "x-001", "fp": "SAME"},
            {"chunk_rel": "a.pdf", "old_chunk_id": "x-001",
             "new_chunk_id": "x-002", "fp": "SAME"},
        ])
        self._write_judgments([_judgment("a.pdf", "x-001")])
        rc, out, err = self._run()
        self.assertEqual(1, rc, "非恒等条目被漏放")
        self.assertEqual(1, err.count("疑似未重映射"), f"恒等误报或非恒等漏报：{err!r}")
        self.assertEqual(1, self._problems(out))

    # ---- 既有四项检查：判定悬空 / 判定撞键 / 新键不在池 / fp 不一致 -------------

    def test_remap_entry_new_missing_and_fp_mismatch(self):
        """「新键不在池 / fp 不一致」两项检查对象不变，且不掺入「疑似」行。"""
        self._write_pool([
            {"rel_path": "b.pdf", "chunk_id": "y-002", "fp": "REAL"},
            {"rel_path": "c.pdf", "chunk_id": "z-001", "fp": "Z1"},
        ])
        self._write_remap([
            {"chunk_rel": "a.pdf", "old_chunk_id": "x-001",
             "new_chunk_id": "x-999", "fp": "P1"},        # 新键不在池
            {"chunk_rel": "b.pdf", "old_chunk_id": "y-001",
             "new_chunk_id": "y-002", "fp": "WRONG"},     # fp 与池对不上
        ])
        self._write_judgments([_judgment("c.pdf", "z-001")])
        rc, out, err = self._run()
        self.assertEqual(1, rc)
        self.assertEqual(1, err.count("新键不在池"), f"{err!r}")
        self.assertEqual(1, err.count("fp 不一致"), f"{err!r}")
        self.assertEqual(0, err.count("疑似未重映射"), f"{err!r}")
        self.assertEqual(2, self._problems(out))

    def test_judgment_dangling_collision_and_dup_pool_key(self):
        """判定悬空 / 判定撞键 / 池键重复三项：检查对象不变。"""
        self._write_pool([
            {"rel_path": "a.pdf", "chunk_id": "x-003", "fp": "A"},
            {"rel_path": "a.pdf", "chunk_id": "x-003", "fp": "B"},   # 池内撞键
        ])
        self._write_judgments([_judgment("a.pdf", "x-003"),          # 命中两条 → 判定撞键
                               _judgment("b.pdf", "y-404")])         # 池中无 → 判定悬空
        rc, out, err = self._run()
        self.assertEqual(1, rc)
        self.assertEqual(1, err.count("判定撞键"), f"{err!r}")
        self.assertEqual(1, err.count("判定悬空"), f"{err!r}")
        self.assertEqual(1, err.count("池存在"), f"{err!r}")
        self.assertEqual(3, self._problems(out))

    def test_pool_row_without_fp_flagged(self):
        """池行无 fp：判定命中唯一池行但 fp 为空 → 报（无映射表，走检查项 2）。"""
        self._write_pool([{"rel_path": "a.pdf", "chunk_id": "x-001", "fp": ""}])
        self._write_judgments([_judgment("a.pdf", "x-001")])
        rc, out, err = self._run()
        self.assertEqual(1, rc)
        self.assertEqual(1, err.count("池行无 fp"), f"{err!r}")


if __name__ == "__main__":
    unittest.main()
