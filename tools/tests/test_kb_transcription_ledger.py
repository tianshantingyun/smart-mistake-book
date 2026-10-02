# -*- coding: utf-8 -*-
"""页级账本与档位判据的用例。

两条线索：
1. **合成用例**钉住判据语义（signals 计数、compute_gate 的四种 fail、compute_plan 的档位规则）；
2. **真实产物用例**钉住账本与源的**一致性**（页数 = manifest 总页数、键唯一、done+missing 守恒、
   每行 gate/plan 与其判据函数自洽）——钉的是"账本永远由源重建"这条不变量，不钉具体页号，
   这样转写推进时用例不用改。
"""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))

from kb_coverage import transcription_ledger as tl  # noqa: E402


def missing_real_artifacts(manifest: Path | None = None) -> list[str]:
    """真实产物缺席清单（空 = 齐备）。manifest 路径可注入，供「产物缺席」用例构造。"""
    manifest = Path(manifest) if manifest is not None else tl.MANIFEST
    if manifest.is_file():
        return []
    return ["%s（页清单，先跑 scan_render_pages.py 重建）" % manifest]


def require_real_artifacts(manifest: Path | None = None) -> None:
    """产物缺席 ⇒ 显式 SkipTest（skip + 理由），而不是让 `tl.build_rows()` 抛
    SystemExit（unittest 报 ERROR，CI 在干净检出上就是这么红的——build/ 不入版本控制）。

    产物在场 ⇒ 直接放行，下面各条断言一条不加一条不减地照跑。
    """
    missing = missing_real_artifacts(manifest)
    if missing:
        raise unittest.SkipTest(
            "缺真实产物（干净检出无 build/，见 .gitignore:14）：" + "；".join(missing))


class SignalsTest(unittest.TestCase):
    def test_counts_each_signal(self):
        text = ("1. 并集 $A\\cup B$\n2. 交集 $A\\cap B$\n① 定义\n"
                "【图：一个示意图】\n【不确定：看不清右下角】")
        sig = tl.signals(text)
        self.assertEqual(2, sig["formulas"])
        self.assertEqual(1, sig["figs"])
        self.assertEqual(3, sig["numbered"])          # 1. / 2. / ①
        self.assertEqual(1, sig["circled"])
        self.assertEqual(1, sig["uncertainties"])
        self.assertGreater(sig["chars"], 20)

    def test_circled_marks_count_anywhere_but_formula_parens_do_not(self):
        # 依据（2026-09-25 试点实测）：代理把一块内容写成一两行时，行内圈号（②离子方程式：…）
        # 必须算；而行内 `f(2)`、`(0)` 这类公式括号不许算成编号。
        sig = tl.signals("（左栏续）②离子方程式：见 $f(2)$ 与 $g(0)$ 的取值。\n①定义：$x>0$")
        self.assertEqual(2, sig["numbered"], "行内圈号要算，公式括号不算")
        self.assertEqual(2, sig["circled"])

    def test_line_leading_digit_marks_count(self):
        sig = tl.signals("1. 定义\n2) 性质\n（3）结论\n正文里的 4) 不算")
        self.assertEqual(3, sig["numbered"])

    def test_empty_text_is_all_zero(self):
        sig = tl.signals("")
        self.assertEqual(0, sig["chars"])
        self.assertEqual(0, sig["formulas"])
        self.assertEqual(0, sig["numbered"])


class GateTest(unittest.TestCase):
    def _row(self, **kw):
        row = {"status": "done", "verdict": "", "truncated": "", "chars": 500,
               "items_min": "", "numbered": 10}
        row.update(kw)
        return row

    def test_missing_page_is_pending(self):
        self.assertEqual("pending", tl.compute_gate(self._row(status="missing")))

    def test_structural_truncation_fails(self):
        self.assertEqual("fail", tl.compute_gate(self._row(truncated="yes", text="占位")))

    def test_too_short_fails(self):
        self.assertEqual("fail", tl.compute_gate(self._row(chars=10)))

    def test_text_defects_fail(self):
        # 残迹判据与成品包同源（kb_build.gate.field_text_defects）
        self.assertEqual("fail", tl.compute_gate(self._row(), text="结论：$T=\\1a$"))
        self.assertEqual("fail", tl.compute_gate(self._row(), text="$a=b$ 与 $c=d$ 不成对$"))

    def test_audit_verdict_retranscribe_fails(self):
        self.assertEqual("fail", tl.compute_gate(self._row(verdict="RETRANSCRIBE")))

    def test_items_min_gate(self):
        # 清点说该有 120 条，转写里只数出 10 个编号 → 疑似整块漏
        self.assertEqual("fail", tl.compute_gate(self._row(items_min="120"), text="正常文本"))
        # 清点 21 条、写出 10 个编号 → 过（编号与式数不必一一对应）
        self.assertEqual("pass", tl.compute_gate(self._row(items_min="21"), text="正常文本"))

    def test_unnumbered_page_passes_with_composite_denominator(self):
        # 实测形态（CHEMISTRY p0208）：叙述/表格页的条目本来不带印刷编号——
        # 印刷编号 9、圈号 3、公式 19、图 1，按最小式/条口径 45 条。
        # 旧判据（只数编号）在这里机械上不可能过；复合分母（编号+公式+图块）下应过。
        row = self._row(items_min="45", numbered=12, formulas=19, figs=1, chars=3511)
        self.assertEqual("pass", tl.compute_gate(row, text="正常文本"))

    def test_thin_text_for_claimed_items_fails(self):
        # 实测真坏页形态：清点 187 条 / 字数 483（2.6 字一条）→ 不过（字数下限）
        row = self._row(items_min="187", numbered=20, formulas=0, figs=0, chars=483)
        self.assertEqual("fail", tl.compute_gate(row, text="短"))

    def test_composite_denominator_still_catches_missing_blocks(self):
        # 反向：清点 100 条，但稿子里编号+公式+图块只有 20 → 仍判漏
        row = self._row(items_min="100", numbered=10, formulas=8, figs=2, chars=6000)
        self.assertEqual("fail", tl.compute_gate(row, text="正常文本"))

    def test_clean_page_passes(self):
        self.assertEqual("pass", tl.compute_gate(self._row(), text="$a+b=b+a$。"))


class PlanTest(unittest.TestCase):
    def _row(self, **kw):
        row = {"status": "done", "page_kind": "叙述", "gate": "pass", "verdict": ""}
        row.update(kw)
        return row

    def test_missing_page_always_full(self):
        # 缺失页一律全协议：实测图像信号分不出"公式密排"（CHEM p2 墨迹 0.0084/行 38），
        # 靠页型猜它简单会放过坏页。
        for kind in ("叙述", "封面/扉页", "公式密排", "未定"):
            with self.subTest(kind=kind):
                self.assertEqual("补齐-全协议",
                                 tl.compute_plan(self._row(status="missing", page_kind=kind)))

    def test_failed_gate_or_retranscribe_wins(self):
        self.assertEqual("重转-全协议", tl.compute_plan(self._row(gate="fail")))
        self.assertEqual("重转-全协议", tl.compute_plan(self._row(verdict="RETRANSCRIBE")))

    def test_narrative_page_gets_light_audit(self):
        self.assertEqual("审计-轻", tl.compute_plan(self._row(page_kind="叙述")))
        self.assertEqual("审计-轻", tl.compute_plan(self._row(page_kind="封面/扉页")))

    def test_dense_or_unknown_gets_full_audit(self):
        for kind in ("公式密排", "图密集", "表格式", "未定", ""):
            with self.subTest(kind=kind):
                self.assertEqual("审计-全", tl.compute_plan(self._row(page_kind=kind)))


class RealArtifactTest(unittest.TestCase):
    """账本必须永远是"源的函数"：这一组用例在转写推进时不需要改。"""

    def setUp(self):
        require_real_artifacts()
        self.rows = tl.build_rows()
        self.tr = tl.load_transcripts()

    def test_ledger_covers_every_page_exactly_once(self):
        expect = sum(int(s["total_pages"]) for s in tl.load_manifest())
        keys = [(r["subject"], r["page"]) for r in self.rows]
        self.assertEqual(expect, len(self.rows))
        self.assertEqual(len(keys), len(set(keys)), "账本里有重复页")

    def test_done_and_missing_are_conserved(self):
        done = [r for r in self.rows if r["status"] == "done"]
        missing = [r for r in self.rows if r["status"] == "missing"]
        self.assertEqual(len(self.rows), len(done) + len(missing))
        # done 的定义就是"转写里有这一页的记录"
        self.assertEqual({(r["subject"], r["page"]) for r in done},
                         set(self.tr.keys()))

    def test_every_done_page_span_contains_its_page(self):
        for r in self.rows:
            if r["status"] != "done":
                continue
            a, b = r["span"].replace("_", "-").split("-")
            with self.subTest(page=(r["subject"], r["page"])):
                self.assertLessEqual(int(a), r["page"])
                self.assertLessEqual(r["page"], int(b))

    def test_gate_and_plan_are_consistent_with_their_functions(self):
        for r in self.rows:
            text = self.tr.get((r["subject"], r["page"]), {}).get("text", "")
            with self.subTest(page=(r["subject"], r["page"])):
                self.assertEqual(tl.compute_gate(r, text), r["gate"])
                self.assertEqual(tl.compute_plan(r), r["plan"])



    def test_multi_page_audit_row_contributes_no_per_page_count(self):
        # 实测 2026-09-25：审计多页行填的是**合计**（`MATH 57-70` 填 317），
        # 摊到每页会让计数闸门对整片误判（账本 fail 60 → 525）。多页行只带判决，不带页级清点数。
        import tempfile, csv as _csv, pathlib as _pl
        tmp = _pl.Path(tempfile.mkdtemp(prefix="audit-map-"))
        f = tmp / "transcript_audits.csv"
        with f.open("w", encoding="utf-8", newline="") as fh:
            w = _csv.DictWriter(fh, fieldnames=["subject", "pages", "verdict", "items_min",
                                                "items_numbered", "evidence"], lineterminator="\n")
            w.writeheader()
            w.writerow({"subject": "MATH", "pages": "57-70", "verdict": "ACCEPT",
                        "items_min": "317", "items_numbered": "60", "evidence": "整片清点"})
            w.writerow({"subject": "MATH", "pages": "550", "verdict": "ACCEPT",
                        "items_min": "20", "items_numbered": "5", "evidence": "单页"})
        old = tl.AUDITS
        tl.AUDITS = f
        try:
            m = tl.audit_map()
        finally:
            tl.AUDITS = old
        self.assertEqual("", m[("MATH", 60)]["items_min"], "多页行的合计不许当页级清点数")
        self.assertEqual("20", m[("MATH", 550)]["items_min"], "单页行的清点数照用")
        self.assertEqual("ACCEPT", m[("MATH", 60)]["verdict"])


    def test_same_page_records_are_joined_not_overwritten(self):
        # 实测 2026-09-27：一页通常有 5–15 个块（每块一行），旧实现只留最后一条 →
        # 账本看到的"整页正文"其实是最后一块（中位 3.4 字/条、322 页被计数闸误判）。
        import json as _json, tempfile, pathlib as _pl
        tmp = _pl.Path(tempfile.mkdtemp(prefix="led-j-"))
        (tmp / "MATH").mkdir()
        book = tmp / "MATH" / "书"
        book.mkdir()
        (book / "range_0001_0002.jsonl").write_text("\n".join([
            _json.dumps({"page": 1, "heading": "块1", "text": "第一块正文"}, ensure_ascii=False),
            _json.dumps({"page": 1, "heading": "块2", "text": "第二块正文"}, ensure_ascii=False),
            _json.dumps({"page": 1, "heading": "块3", "text": "第三块正文"}, ensure_ascii=False),
            _json.dumps({"page": 2, "heading": "", "text": "第二页正文"}, ensure_ascii=False),
        ]) + "\n", encoding="utf-8")
        old = tl.TRANSCRIPTS
        tl.TRANSCRIPTS = tmp
        try:
            got = tl.load_transcripts()
        finally:
            tl.TRANSCRIPTS = old
        self.assertIn("第一块正文", got[("MATH", 1)]["text"])
        self.assertIn("第二块正文", got[("MATH", 1)]["text"])
        self.assertIn("第三块正文", got[("MATH", 1)]["text"])
        self.assertEqual("第二页正文", got[("MATH", 2)]["text"])
        self.assertEqual("0001_0002", got[("MATH", 1)]["span"])


class RealArtifactSkipTest(unittest.TestCase):
    """证明 skip 分支可达（产物缺席 ⇒ skip；产物在场 ⇒ 守卫放行）。"""

    def test_absent_manifest_is_detected_and_raises_skip(self):
        with tempfile.TemporaryDirectory() as td:
            absent = Path(td) / "2027-53-pages" / "manifest_slim.json"
            with self.assertRaises(unittest.SkipTest) as caught:
                require_real_artifacts(absent)
            self.assertIn("manifest_slim.json", str(caught.exception))
            self.assertIn("scan_render_pages.py", str(caught.exception))

    def test_present_manifest_passes_the_guard(self):
        with tempfile.TemporaryDirectory() as td:
            manifest = Path(td) / "manifest_slim.json"
            manifest.write_text('{"subjects": []}', encoding="utf-8")
            require_real_artifacts(manifest)    # 不抛 = 守卫放行，原断言照跑

    def test_real_artifact_case_skips_when_manifest_absent(self):
        """把 RealArtifactTest 的 setUp 放到「manifest 缺席」条件下跑：必须 SkipTest。

        这是干净检出上 CI 走的那条分支（改前是 SystemExit → unittest ERROR）。
        """
        case = RealArtifactTest("test_ledger_covers_every_page_exactly_once")
        with tempfile.TemporaryDirectory() as td:
            absent = Path(td) / "manifest_slim.json"
            with mock.patch.object(tl, "MANIFEST", absent):
                with self.assertRaises(unittest.SkipTest) as caught:
                    case.setUp()
        self.assertIn("manifest_slim.json", str(caught.exception))

    def test_real_artifact_case_runs_when_manifest_present(self):
        """反向对照：manifest 在场时 setUp 不 skip、照常建账本（不是无条件跳过）。"""
        case = RealArtifactTest("test_ledger_covers_every_page_exactly_once")
        case.setUp()
        self.assertGreater(len(case.rows), 0)
        self.assertEqual(sum(int(s["total_pages"]) for s in tl.load_manifest()),
                         len(case.rows))


if __name__ == "__main__":
    unittest.main()
