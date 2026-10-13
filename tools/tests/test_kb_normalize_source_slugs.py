# -*- coding: utf-8 -*-
"""normalize_source_slugs 的字节级回归用例。

## 它消灭的失败

判定源是逐行复核的产物（221 CRLF / 13 LF、两条带字段内换行）。归一若用
"整表重写"而不是"只拼接 node_slug 字段跨度"，会把未改行的字节（行尾、引号写法、
字段内换行）一起翻掉——diff 噪音淹没真正的 2,924 行改动；若把 SKIP/NEW 行也改，
会把显式不提炼的裁定弄脏。本用例把这三条钉死在夹具上（不依赖真实包，全用假数据）。
"""

from __future__ import annotations

import shutil
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_coverage import normalize_source_slugs as N  # noqa: E402

HDR = ",".join(N.HDR)


def _row(cid: str, action: str, node: str, note: str = "", content: str = "C") -> str:
    return ",".join(["物理/练习.docx", cid, action, node, "METHOD_MODEL", "T", "S",
                     "A", content, "B", note, ""])


class NormalizeSourceSlugsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="kb-norm-", dir=REPO / "build"))
        self.jv = self.tmp / "jv"
        self.jv.mkdir(parents=True)
        # 假包与假并点表：死一 → 存活一；死链 死二 → 死一 → 存活一（链式）
        self._orig = (N.load_pack_nodes, N.load_merge_map, N.load_aux_slugs,
                      N.subject_of_path, N.load_slice_subjects)
        N.load_pack_nodes = lambda: {"PHYSICS": {"存活一"}}         # type: ignore
        N.load_merge_map = lambda: {("PHYSICS", "死一"): "存活一", ("PHYSICS", "死二"): "死一"}  # type: ignore
        N.load_aux_slugs = lambda: (set(), set())                    # type: ignore
        N.subject_of_path = lambda rel: "PHYSICS"                    # type: ignore
        N.load_slice_subjects = lambda d: {}                         # type: ignore

    def tearDown(self):
        (N.load_pack_nodes, N.load_merge_map, N.load_aux_slugs,
         N.subject_of_path, N.load_slice_subjects) = self._orig
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _run(self, **kw) -> int:
        return N.main(["--dir", str(self.jv), "--report", str(self.tmp / "r.txt")] +
                      (["--write"] if kw.get("write") else []))

    def test_only_node_slug_changes_and_eol_preserved(self):
        crlf = "\r\n".join([
            HDR,
            _row("aaaa111111-001", "MATERIAL", "死一"),
            _row("aaaa111111-002", "SKIP", "死一", note="装饰页"),
            _row("aaaa111111-003", "MATERIAL", "NEW:某个提案"),
            _row("aaaa111111-004", "MATERIAL", "存活一"),
            _row("aaaa111111-005", "MATERIAL", "死二", content='"第一行\n第二行"'),
        ]) + "\r\n"
        (self.jv / "a.jsonl.csv").write_bytes(crlf.encode("utf-8"))
        lf = "\n".join([HDR, _row("bbbb222222-001", "MATERIAL", "死一")]) + "\n"
        (self.jv / "b.jsonl.csv").write_bytes(lf.encode("utf-8"))

        self.assertEqual(0, self._run(write=True))
        a = (self.jv / "a.jsonl.csv").read_bytes().decode("utf-8")
        b = (self.jv / "b.jsonl.csv").read_bytes().decode("utf-8")
        self.assertEqual(crlf.replace("MATERIAL,死一", "MATERIAL,存活一").replace("MATERIAL,死二", "MATERIAL,存活一"), a)
        self.assertEqual(lf.replace("MATERIAL,死一", "MATERIAL,存活一"), b)
        self.assertIn("SKIP,死一", a)                       # SKIP 行逐字不动
        self.assertIn("NEW:某个提案", a)                    # NEW: 行逐字不动
        self.assertIn("第一行\n第二行", a)                   # 字段内换行保留
        self.assertNotIn("\r\n", b)                          # LF 文件不引入 CRLF

    def test_idempotent(self):
        (self.jv / "a.jsonl.csv").write_bytes(
            ("\n".join([HDR, _row("aaaa111111-001", "MATERIAL", "死一")]) + "\n").encode("utf-8"))
        self.assertEqual(0, self._run(write=True))
        before = (self.jv / "a.jsonl.csv").read_bytes()
        self.assertEqual(0, self._run(write=True))
        self.assertEqual(before, (self.jv / "a.jsonl.csv").read_bytes())
        self.assertIn("待改 MATERIAL 行 0", (self.tmp / "r.txt").read_text(encoding="utf-8"))

    def test_cycle_is_an_anomaly_and_nothing_is_written(self):
        N.load_merge_map = lambda: {("PHYSICS", "死一"): "死二", ("PHYSICS", "死二"): "死一"}  # type: ignore
        text = "\n".join([HDR, _row("aaaa111111-001", "MATERIAL", "死一")]) + "\n"
        (self.jv / "a.jsonl.csv").write_bytes(text.encode("utf-8"))
        self.assertEqual(1, self._run(write=True))
        self.assertEqual(text, (self.jv / "a.jsonl.csv").read_bytes().decode("utf-8"))
        self.assertIn("cycle", (self.tmp / "r.txt").read_text(encoding="utf-8"))

    def test_unresolvable_node_is_an_anomaly_not_a_guess(self):
        text = "\n".join([HDR, _row("aaaa111111-001", "MATERIAL", "无处可去")]) + "\n"
        (self.jv / "a.jsonl.csv").write_bytes(text.encode("utf-8"))
        self.assertEqual(1, self._run(write=True))
        self.assertEqual(text, (self.jv / "a.jsonl.csv").read_bytes().decode("utf-8"))


if __name__ == "__main__":
    unittest.main()
