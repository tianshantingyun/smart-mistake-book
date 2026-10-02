# -*- coding: utf-8 -*-
"""候选表落盘的防覆盖守卫（2026-10-02 实测事故的回归钉）。

事故：默认路径上的 `content_audit_2026-09-25.csv` 是**上一轮已裁定**的产物
（409 行，KEEP 330 / REBIND 56 / NONE 23）；新开一轮直接 `--write` 把它整表换成
496 行 verdict 全空的新候选——裁定记录当场丢失（靠 git 还原）。

现在：目标表已含非空 verdict ⇒ 拒绝写入（可用 `--out` 写别处或显式 `--force`）。
"""

from __future__ import annotations

import csv
import shutil
import tempfile
import unittest
from pathlib import Path

from kb_build import audit_content_bindings as acb
from kb_build import pack_io


def _row(material: str, verdict: str = "") -> dict:
    return {"subject": "MATH", "slug": "节点", "material_slug": material,
            "current_node_slug": "节点", "suggested_node_slug": "",
            "verdict": verdict, "evidence": "e", "slice": ""}


class WriteTableGuardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-audit-guard-", dir=pack_io.REPO / "build"))
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.target = self.tmp / "candidates.csv"

    def _verdicts(self) -> list[str]:
        with self.target.open(encoding="utf-8-sig", newline="") as handle:
            return [row["verdict"] for row in csv.DictReader(handle)]

    def test_first_round_writes_clean_candidates(self):
        acb.write_table([_row("m-1"), _row("m-2")], self.target)
        self.assertEqual(["", ""], self._verdicts())

    def test_refuses_to_clobber_adjudicated_table(self):
        acb.write_table([_row("m-1", "KEEP")], self.target)
        with self.assertRaises(ValueError) as ctx:
            acb.write_table([_row("m-9")], self.target)
        self.assertIn("裁定产物", str(ctx.exception))
        self.assertEqual(["KEEP"], self._verdicts(), "被拒后原裁定必须原样在位")

    def test_force_overwrites_on_request(self):
        acb.write_table([_row("m-1", "KEEP")], self.target)
        acb.write_table([_row("m-9")], self.target, force=True)
        self.assertEqual([""], self._verdicts())


if __name__ == "__main__":
    unittest.main()
