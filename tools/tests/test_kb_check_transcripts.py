# -*- coding: utf-8 -*-
"""转写质量门的用例。

合成用例把 `transcription_ledger` 的三条入口（manifest / 账本 / 转写）替换成内存里的小集合，
逐条钉住六条判据"该报就报、不该报不报"；真实产物用例只钉"门跑得起来且账本与源同规模"
（不钉 0 问题——当前 27 页未修，钉 0 会让用例随转写进度反复改）。
"""

from __future__ import annotations

import io
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))

from kb_coverage import check_transcripts as ct  # noqa: E402
from kb_coverage import transcription_ledger as tl  # noqa: E402


def require_real_manifest(manifest: Path | None = None) -> None:
    """真实产物用例的守卫（口径同 `test_kb_transcription_ledger.require_real_artifacts`）。

    `build/2027-53-pages/manifest_slim.json` 是 build/ 产物（不入版本控制），
    干净检出上缺席：显式 skip + 理由，而不是让 `tl.load_manifest()` 抛 SystemExit
    把整条 CI 变红。产物在场 ⇒ 放行，原断言照跑。
    """
    manifest = Path(manifest) if manifest is not None else tl.MANIFEST
    if not manifest.is_file():
        raise unittest.SkipTest(
            "缺真实产物（干净检出无 build/，见 .gitignore:14）：%s"
            "（页清单，先跑 scan_render_pages.py 重建）" % manifest)


def _row(subject="数学", page=1, **kw):
    row = {"subject": subject, "page": page, "span": "1-2", "status": "done",
           "gate": "pass", "plan": "审计-轻", "page_kind": "叙述", "verdict": "",
           "truncated": "", "chars": 400, "items_min": "", "numbered": 8,
           "uncertainties": 0, "pdf_rel": "book.pdf"}
    row.update(kw)
    return row


class CheckTranscriptsTest(unittest.TestCase):
    def _run(self, rows, texts, manifest):
        with mock.patch.object(tl, "build_rows", lambda: rows), \
             mock.patch.object(tl, "load_transcripts", lambda: texts), \
             mock.patch.object(tl, "load_manifest", lambda: manifest):
            buf = io.StringIO()
            with redirect_stdout(buf):
                rc = ct.main([])
        return rc, buf.getvalue()

    def test_clean_set_passes(self):
        rows = [_row(page=1), _row(page=2)]
        texts = {("数学", 1): {"text": "$a+b=b+a$。", "span": "1-2"},
                 ("数学", 2): {"text": "定义：$A\\cup B$。", "span": "1-2"}}
        rc, out = self._run(rows, texts, [{"subject": "数学", "total_pages": 2}])
        self.assertEqual(0, rc, out)
        self.assertIn("全部通过", out)

    def test_ledger_size_mismatch_reported(self):
        rc, out = self._run([_row(page=1)], {("数学", 1): {"text": "文字够长的一段内容。", "span": "1-2"}},
                            [{"subject": "数学", "total_pages": 2}])
        self.assertEqual(1, rc)
        self.assertIn("账本页数 1 != 应有 2", out)

    def test_duplicate_page_reported(self):
        rows = [_row(page=1), _row(page=1)]
        texts = {("数学", 1): {"text": "文字够长的一段内容。", "span": "1-2"}}
        rc, out = self._run(rows, texts, [{"subject": "数学", "total_pages": 2}])
        self.assertEqual(1, rc)
        self.assertIn("账本重复页", out)

    def test_page_outside_span_reported(self):
        rc, out = self._run([_row(page=3, span="1-2")],
                            {("数学", 3): {"text": "文字够长的一段内容。", "span": "1-2"}},
                            [{"subject": "数学", "total_pages": 4}])
        self.assertEqual(1, rc)
        self.assertIn("落在 span", out)

    def test_defect_and_odd_dollar_reported(self):
        texts = ("结论：$T=\\1a$ 的取值。", "$a=b$ 与 $c=d$ 不成对$。")
        for text in texts:
            with self.subTest(text=text):
                rows = [_row(page=1, span="1-1")]
                rc, out = self._run(rows, {("数学", 1): {"text": text, "span": "1-1"}},
                                    [{"subject": "数学", "total_pages": 1}])
                self.assertEqual(1, rc)
                self.assertIn("文本残迹", out)

    def test_count_gate_reported(self):
        rows = [_row(page=1, span="1-1", items_min="120")]
        rc, out = self._run(rows, {("数学", 1): {"text": "文字够长的一段内容。", "span": "1-1"}},
                            [{"subject": "数学", "total_pages": 1}])
        self.assertEqual(1, rc)
        self.assertIn("闸门 fail", out)

    def test_uncertain_marker_mismatch_reported(self):
        rows = [_row(page=1, span="1-1", uncertainties=2)]
        rc, out = self._run(rows, {("数学", 1): {"text": "文字够长的一段内容。", "span": "1-1"}},
                            [{"subject": "数学", "total_pages": 1}])
        self.assertEqual(1, rc)
        self.assertIn("【不确定】计数不一致", out)

    def test_empty_text_reported(self):
        rows = [_row(page=1, span="1-1")]
        rc, out = self._run(rows, {("数学", 1): {"text": "   ", "span": "1-1"}},
                            [{"subject": "数学", "total_pages": 1}])
        self.assertEqual(1, rc)
        self.assertIn("text 为空", out)

    def test_missing_pages_do_not_count_as_problems(self):
        # 缺失页由档位（补齐）负责，门只对 done 页下判——否则 S3 跑之前门一直红，失去信号
        rows = [_row(page=1, span="1-1"), _row(page=2, span="1-1", status="missing", gate="pending")]
        rc, out = self._run(rows, {("数学", 1): {"text": "文字够长的一段内容。", "span": "1-1"}},
                            [{"subject": "数学", "total_pages": 2}])
        self.assertEqual(0, rc, out)


class RealArtifactTest(unittest.TestCase):
    def test_gate_runs_and_reports_the_real_ledger_size(self):
        require_real_manifest()
        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = ct.main([])
        out = buf.getvalue()
        rows = tl.build_rows()
        self.assertIn(rc, (0, 1))
        self.assertIn(f"页数 {len(rows)}", out)
        self.assertIn("闸门：", out)


class RealArtifactSkipTest(unittest.TestCase):
    """证明 skip 分支可达：manifest 缺席 ⇒ skip；patch 到空路径后用例自己走 skip。"""

    def test_absent_manifest_raises_skip_with_reason(self):
        with tempfile.TemporaryDirectory() as td:
            absent = Path(td) / "manifest_slim.json"
            with self.assertRaises(unittest.SkipTest) as caught:
                require_real_manifest(absent)
            self.assertIn("manifest_slim.json", str(caught.exception))
            self.assertIn("干净检出无 build/", str(caught.exception))

    def test_present_manifest_passes_the_guard(self):
        with tempfile.TemporaryDirectory() as td:
            manifest = Path(td) / "manifest_slim.json"
            manifest.write_text('{"subjects": []}', encoding="utf-8")
            require_real_manifest(manifest)     # 不抛 = 守卫放行

    def test_gate_case_turns_into_skip_when_manifest_absent(self):
        """把真实用例在「manifest 缺席」条件下跑：必须 SkipTest，不是 SystemExit/ERROR。"""
        case = RealArtifactTest("test_gate_runs_and_reports_the_real_ledger_size")
        with tempfile.TemporaryDirectory() as td:
            absent = Path(td) / "manifest_slim.json"
            with mock.patch.object(tl, "MANIFEST", absent):
                with self.assertRaises(unittest.SkipTest):
                    case.test_gate_runs_and_reports_the_real_ledger_size()


if __name__ == "__main__":
    unittest.main()
