# -*- coding: utf-8 -*-
"""生图入口静态门（tools/ci/check_figure_entry_gates.py）的自测。

它消灭的失败：门的语义只能靠"真输入能过、坏输入必炸"证明——白名单漂移、扫描口径
退化（文件集 ≠ 出现次数）或前哨正则失配后，没人能区分"门绿"与"门根本没检查那条
不变量"。每条断言都配 positive 与 negative：

- positive：当前真树恰 3 处、三入口显式传 basis → 无缺口（A-13 基线逐字核对）；
- negative：白名单外第 4 处 / 白名单内缺失 / 同文件第 2 处 / 删显式 basis → 各自报错，
  且 CLI 退出码非零（判据原文："出现第 4 处即失败"）。

脚本本身不跑 Gradle、不联网；临时树只含门扫描所需的最小文件形状。
"""

from __future__ import annotations

import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from ci import check_figure_entry_gates as gates

REPO = Path(__file__).resolve().parents[2]

ENTRY_REQUEST_LINES = [
    "val generationRequest = FigureGenerationRequest(",
    '    operationId = "op",',
    '    basisFingerprint = figureBasisFingerprint("op", null, "prompt"),',
    ")",
]

FOURTH_ENTRY = (
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/other/"
    "SomeFourthEntry.kt"
)


def write_entry(
    root: Path,
    relpath: str,
    with_request: bool = True,
    with_basis: bool = True,
) -> None:
    path = root / relpath
    path.parent.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []
    if with_request:
        lines.append(ENTRY_REQUEST_LINES[0])
        lines.append(ENTRY_REQUEST_LINES[1])
    if with_basis:
        lines.append(ENTRY_REQUEST_LINES[2])
    if with_request:
        lines.append(ENTRY_REQUEST_LINES[3])
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_clean_tree(root: Path) -> None:
    for relpath in gates.ENTRY_WHITELIST:
        write_entry(root, relpath)


class CheckFigureEntryGatesTest(unittest.TestCase):
    def test_current_tree_passes_both_gates(self) -> None:
        errors, report = gates.check(REPO)

        self.assertEqual([], errors)
        self.assertEqual("PASS", report[-1])
        construction_lines = [line for line in report if line.startswith("  命中 ")]
        self.assertEqual(3, len(construction_lines), report)

    def test_a_fourth_construction_site_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            write_entry(root, FOURTH_ENTRY)

            errors, _ = gates.check(root)

            self.assertTrue(
                any("白名单外" in error and "SomeFourthEntry" in error for error in errors),
                errors,
            )

    def test_a_missing_entry_construction_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            missing = gates.ENTRY_WHITELIST[0]
            for relpath in gates.ENTRY_WHITELIST[1:]:
                write_entry(root, relpath)

            errors, _ = gates.check(root)

            self.assertTrue(
                any("缺失" in error and missing in error for error in errors),
                errors,
            )

    def test_a_duplicate_construction_in_one_entry_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            duplicated = gates.ENTRY_WHITELIST[1]
            write_clean_tree(root)
            path = root / duplicated
            path.write_text(
                "\n".join(ENTRY_REQUEST_LINES + ENTRY_REQUEST_LINES) + "\n",
                encoding="utf-8",
            )

            errors, _ = gates.check(root)

            self.assertTrue(
                any("同一文件出现第 2 处" in error and duplicated in error for error in errors),
                errors,
            )

    def test_removing_the_explicit_basis_pass_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            stripped = gates.ENTRY_WHITELIST[2]
            for relpath in gates.ENTRY_WHITELIST:
                write_entry(root, relpath, with_basis=(relpath != stripped))

            errors, _ = gates.check(root)

            self.assertTrue(
                any("不再显式传 basis" in error and stripped in error for error in errors),
                errors,
            )

    def test_cli_exit_code_reflects_the_gate(self) -> None:
        with tempfile.TemporaryDirectory() as good_tree, tempfile.TemporaryDirectory() as bad_tree:
            write_clean_tree(Path(good_tree))
            write_clean_tree(Path(bad_tree))
            write_entry(Path(bad_tree), FOURTH_ENTRY)

            good_out = io.StringIO()
            with contextlib.redirect_stdout(good_out):
                good_code = gates.main(["--root", good_tree])
            bad_out = io.StringIO()
            with contextlib.redirect_stdout(bad_out):
                bad_code = gates.main(["--root", bad_tree])

            self.assertEqual(0, good_code)
            self.assertIn("PASS", good_out.getvalue())
            self.assertEqual(1, bad_code)
            self.assertIn("FAIL", bad_out.getvalue())


if __name__ == "__main__":
    unittest.main()
