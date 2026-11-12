# -*- coding: utf-8 -*-
"""Static figure-entry coverage and per-request fingerprint regression tests."""
from __future__ import annotations

import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from ci import check_figure_entry_gates as gates

REPO = Path(__file__).resolve().parents[2]
TUTOR_ENTRY = gates.ENTRY_WHITELIST[2]
LINEAGE_ENTRIES = gates.ENTRY_WHITELIST[3:]
UNEXPECTED_ENTRY = (
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/other/"
    "SomeUnexpectedEntry.kt"
)


def write_entry(root: Path, relpath: str, signatures=None) -> None:
    if signatures is None:
        signatures = gates.ENTRY_FINGERPRINTS.get(
            relpath, (("basisFingerprint", "figureBasisFingerprint"),),
        )
    path = root / relpath
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = []
    for index, (field, helper) in enumerate(signatures):
        lines.extend([
            f"val generationRequest{index} = FigureGenerationRequest(",
            '    operationId = operationId("op", nested("text ) ,")),',
            f'    {field} = {helper}("op", null, "prompt"),',
            ")",
        ])
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_clean_tree(root: Path) -> None:
    for relpath in gates.ENTRY_WHITELIST:
        write_entry(root, relpath)


class CheckFigureEntryGatesTest(unittest.TestCase):
    def _replace(self, root: Path, relpath: str, old: str, new: str) -> None:
        path = root / relpath
        text = path.read_text(encoding="utf-8")
        self.assertIn(old, text)
        path.write_text(text.replace(old, new), encoding="utf-8")

    def test_current_tree_passes_both_gates(self) -> None:
        errors, report = gates.check(REPO)
        self.assertEqual([], errors)
        self.assertEqual("PASS", report[-1])
        construction_lines = [line for line in report if line.startswith("  命中 ")]
        self.assertEqual(6, len(construction_lines), report)
        self.assertEqual(5, len(gates.ENTRY_WHITELIST))

    def test_clean_fixture_preserves_nested_calls_and_parentheses_in_literals(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            errors, report = gates.check(root)
            self.assertEqual([], errors)
            self.assertEqual("PASS", report[-1])

    def test_an_unexpected_construction_site_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            write_entry(root, UNEXPECTED_ENTRY)
            errors, _ = gates.check(root)
            self.assertTrue(any("白名单外" in e and "SomeUnexpectedEntry" in e for e in errors), errors)

    def test_an_unexpected_construction_in_a_non_kotlin_file_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            write_entry(root, UNEXPECTED_ENTRY + ".txt")
            errors, _ = gates.check(root)
            self.assertTrue(any("白名单外" in e and ".txt" in e for e in errors), errors)

    def test_each_missing_entry_construction_fails(self) -> None:
        for missing in gates.ENTRY_WHITELIST:
            with self.subTest(entry=missing), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                write_clean_tree(root)
                (root / missing).unlink()
                errors, _ = gates.check(root)
                self.assertTrue(any("缺失" in e and missing in e for e in errors), errors)

    def test_a_duplicate_construction_in_one_entry_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            duplicated = gates.ENTRY_WHITELIST[1]
            signature = gates.ENTRY_FINGERPRINTS[duplicated][0]
            write_entry(root, duplicated, (signature, signature))
            errors, _ = gates.check(root)
            self.assertTrue(any("同一文件出现第 2 处" in e and duplicated in e for e in errors), errors)

    def test_a_third_tutor_construction_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            signatures = gates.ENTRY_FINGERPRINTS[TUTOR_ENTRY]
            write_entry(root, TUTOR_ENTRY, signatures + (signatures[0],))
            errors, _ = gates.check(root)
            self.assertTrue(any("同一文件出现第 3 处" in e and TUTOR_ENTRY in e for e in errors), errors)

    def test_tutor_requires_both_constructions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            write_entry(root, TUTOR_ENTRY, (gates.ENTRY_FINGERPRINTS[TUTOR_ENTRY][1],))
            errors, _ = gates.check(root)
            self.assertTrue(any("缺失" in e and TUTOR_ENTRY in e for e in errors), errors)

    def test_each_image_entry_requires_its_explicit_basis(self) -> None:
        for entry in gates.ENTRY_WHITELIST[:2]:
            with self.subTest(entry=entry), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                write_clean_tree(root)
                self._replace(root, entry, "basisFingerprint = figureBasisFingerprint", "other = figureBasisFingerprint")
                errors, _ = gates.check(root)
                self.assertTrue(any("门2" in e and entry in e for e in errors), errors)

    def test_tutor_image_basis_cannot_replace_missing_js_basis(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            self._replace(root, TUTOR_ENTRY, "basisFingerprint = figureJsBasisFingerprint", "other = figureJsBasisFingerprint")
            errors, _ = gates.check(root)
            self.assertTrue(any("第 1 处构造" in e and TUTOR_ENTRY in e for e in errors), errors)

    def test_tutor_js_basis_cannot_replace_missing_image_basis(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            self._replace(root, TUTOR_ENTRY, "basisFingerprint = figureBasisFingerprint", "other = figureBasisFingerprint")
            errors, _ = gates.check(root)
            self.assertTrue(any("第 2 处构造" in e and TUTOR_ENTRY in e for e in errors), errors)

    def test_tutor_requires_one_of_each_helper(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            self._replace(root, TUTOR_ENTRY, "figureJsBasisFingerprint", "figureBasisFingerprint")
            errors, _ = gates.check(root)
            self.assertTrue(any("helper 错配" in e and TUTOR_ENTRY in e for e in errors), errors)

    def test_fully_qualified_js_helper_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            self._replace(root, TUTOR_ENTRY, "figureJsBasisFingerprint", "com.tingyun.smartmistakebook.core.model.figureJsBasisFingerprint")
            errors, _ = gates.check(root)
            self.assertEqual([], errors)

    def test_each_lineage_entry_requires_its_own_helper(self) -> None:
        for entry in LINEAGE_ENTRIES:
            with self.subTest(entry=entry), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                write_clean_tree(root)
                field, helper = gates.ENTRY_FINGERPRINTS[entry][0]
                self._replace(root, entry, f"{field} = {helper}", f"{field} = figureBasisFingerprint")
                errors, _ = gates.check(root)
                self.assertTrue(any("helper 错配" in e and entry in e for e in errors), errors)

    def test_each_lineage_entry_requires_explicit_lineage(self) -> None:
        for entry in LINEAGE_ENTRIES:
            with self.subTest(entry=entry), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                write_clean_tree(root)
                self._replace(root, entry, "lineageFingerprint =", "other =")
                errors, _ = gates.check(root)
                self.assertTrue(any("第 1 处构造" in e and entry in e for e in errors), errors)

    def test_each_lineage_entry_requires_the_lineage_argument(self) -> None:
        for entry in LINEAGE_ENTRIES:
            with self.subTest(entry=entry), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                write_clean_tree(root)
                self._replace(root, entry, "lineageFingerprint =", "basisFingerprint =")
                errors, _ = gates.check(root)
                self.assertTrue(any("helper 错配" in e and entry in e for e in errors), errors)

    def test_comment_or_literal_cannot_supply_the_missing_fingerprint(self) -> None:
        for replacement in (
            '// basisFingerprint = figureJsBasisFingerprint("op", null, "prompt"),',
            'note = "basisFingerprint = figureJsBasisFingerprint(op)",',
        ):
            with self.subTest(replacement=replacement), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                write_clean_tree(root)
                self._replace(root, TUTOR_ENTRY,
                              'basisFingerprint = figureJsBasisFingerprint("op", null, "prompt"),',
                              replacement)
                errors, _ = gates.check(root)
                self.assertTrue(any("第 1 处构造" in e and TUTOR_ENTRY in e for e in errors), errors)

    def test_nested_fingerprint_argument_cannot_replace_request_fingerprint(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            self._replace(root, TUTOR_ENTRY,
                          'basisFingerprint = figureJsBasisFingerprint("op", null, "prompt"),',
                          'source = Source(basisFingerprint = figureJsBasisFingerprint("op", null, "prompt")),')
            errors, _ = gates.check(root)
            self.assertTrue(any("第 1 处构造" in e and TUTOR_ENTRY in e for e in errors), errors)

    def test_unbalanced_constructor_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_clean_tree(root)
            entry = gates.ENTRY_WHITELIST[0]
            path = root / entry
            path.write_text(path.read_text(encoding="utf-8").rstrip()[:-1], encoding="utf-8")
            errors, _ = gates.check(root)
            self.assertTrue(any("括号未闭合" in e and entry in e for e in errors), errors)

    def test_cli_exit_code_reflects_the_gate(self) -> None:
        with tempfile.TemporaryDirectory() as good_tree, tempfile.TemporaryDirectory() as bad_tree:
            write_clean_tree(Path(good_tree))
            write_clean_tree(Path(bad_tree))
            write_entry(Path(bad_tree), UNEXPECTED_ENTRY)
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
