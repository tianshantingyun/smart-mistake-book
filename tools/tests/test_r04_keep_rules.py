# -*- coding: utf-8 -*-
"""R-04 keep-rules 门（tools/ci/check_r04_keep_rules.py）的自测（批次 35 T3）。

`app/r04-test-keep.pro:12-14` 自批次 34 起声称的门此前不存在（幽灵门，见
`docs/research/2026-10-30-batch-35-baseline.md` §三.6）；本测试把门的判定表钉死，
消灭"门静默失效"这个具体失败：只在**规则文本**里找 `-dontobfuscate`——注释里的
字面量不算、词中前缀不算、大小写变体算、文件缺失算 FAIL。每条断言配一个正例或
反例。末条把门打到**真树**上（默认两个接线文件 0 命中），谁往规则文件里加指令，
这条先红。

运行（仓库根）：
    python -m pytest tools/tests/test_r04_keep_rules.py -q
    python -m unittest discover -s tools/tests -t tools -p "test_r04_keep_rules.py" -v
"""

from __future__ import annotations

import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from ci import check_r04_keep_rules as gate

REPO = Path(__file__).resolve().parents[2]


class ViolatingLinesTest(unittest.TestCase):
    """纯判定表：一行规则文本什么时候算命中。"""

    def test_clean_rules_have_no_hits(self):
        text = ("-keep class com.tingyun.x.Y { *; }\n"
                "# -keepclassmembers 用于 @Test 方法\n"
                "-keepclassmembers class * { @org.junit.Test <methods>; }\n")
        self.assertEqual([], gate.violating_lines(text))

    def test_directive_line_is_caught(self):
        self.assertEqual([(1, "-dontobfuscate")],
                         gate.violating_lines("-dontobfuscate\n"))

    def test_indented_directive_is_caught(self):
        self.assertEqual([(2, "-dontobfuscate")],
                         gate.violating_lines("-keep class A {*;}\n  -dontobfuscate  \n"))

    def test_comment_mention_is_not_a_hit(self):
        # R8 不执行注释；.pro 头部自述提到指令名不算（否则门会自咬）。
        self.assertEqual([], gate.violating_lines("# -dontobfuscate is forbidden here\n"))

    def test_commented_out_directive_is_not_a_hit(self):
        self.assertEqual([], gate.violating_lines("#-dontobfuscate\n"))

    def test_directive_in_inline_comment_is_not_a_hit(self):
        self.assertEqual([], gate.violating_lines("-keep class A {*;} # -dontobfuscate\n"))

    def test_case_variant_is_caught(self):
        self.assertEqual([(1, "-DontObfuscate")],
                         gate.violating_lines("-DontObfuscate\n"))

    def test_word_prefix_is_not_caught(self):
        self.assertEqual([], gate.violating_lines("-dontobfuscateall-is-not-a-directive\n"))


class ScanAndMainTest(unittest.TestCase):
    """文件面与退出码：门必须会红。"""

    def _pro(self, tmp: Path, text: str) -> Path:
        target = tmp / "rules.pro"
        target.write_text(text, encoding="utf-8")
        return target

    def test_missing_file_fails_the_gate(self):
        with tempfile.TemporaryDirectory() as raw:
            text, code = self._run_main([str(Path(raw) / "absent.pro")])
        self.assertEqual(1, code)
        self.assertIn("FAIL", text)

    def test_dirty_file_fails_with_line_number(self):
        with tempfile.TemporaryDirectory() as raw:
            bad = self._pro(Path(raw), "-keep class A {*;}\n-dontobfuscate\n")
            text, code = self._run_main([str(bad)])
        self.assertEqual(1, code)
        self.assertIn("rules.pro:2", text)

    def test_clean_file_passes(self):
        with tempfile.TemporaryDirectory() as raw:
            clean = self._pro(Path(raw), "# only comments\n-keep class B {*;}\n")
            text, code = self._run_main([str(clean)])
        self.assertEqual(0, code)
        self.assertIn("0 hits", text)

    def test_real_tree_passes(self):
        """真树集成：默认两个接线文件必须 0 命中；加了指令这条先红。"""
        text, code = self._run_main([])
        self.assertEqual(0, code, text)
        for rel in gate.DEFAULT_RULE_FILES:
            self.assertTrue((REPO / rel).is_file(), rel)

    @staticmethod
    def _run_main(argv: list[str]) -> tuple[str, int]:
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = gate.main(argv)
        # stdout+stderr 合并返回：断言只做子串匹配，不关心流归属。
        return out.getvalue() + "\n" + err.getvalue(), code


if __name__ == "__main__":
    unittest.main()
