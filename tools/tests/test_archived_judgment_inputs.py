"""发布归档后的判定输入兼容性。"""
from __future__ import annotations

import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from kb_coverage import apply_text_fixes, merge_text_judgments


class ArchivedJudgmentInputsTest(unittest.TestCase):
    def test_archive_and_root_inputs_with_root_precedence(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            archive = root / "knowledge-production/archive/pre-release-workfiles"
            archive.mkdir(parents=True)
            (archive / ".agent_t_01.csv").write_text("archived", encoding="utf-8")
            (archive / ".agent_t_02.csv").write_text("older", encoding="utf-8")
            (root / ".agent_t_02.csv").write_text("current", encoding="utf-8")
            previous = Path.cwd()
            try:
                os.chdir(root)
                self.assertEqual(apply_text_fixes.load_products(), [
                    (".agent_t_01.csv", "archived"), (".agent_t_02.csv", "current")])
            finally:
                os.chdir(previous)
            with patch.object(merge_text_judgments, "REPO", root):
                self.assertEqual(merge_text_judgments.collect_files(), [
                    archive / ".agent_t_01.csv", root / ".agent_t_02.csv"])
                self.assertEqual(merge_text_judgments.collect_files(only=".agent_t_"), [
                    archive / ".agent_t_01.csv", root / ".agent_t_02.csv"])
                self.assertEqual(merge_text_judgments.collect_files(only=".agent_mp_"), [])
                self.assertEqual(merge_text_judgments.collect_files(directory=archive), [
                    archive / ".agent_t_01.csv", archive / ".agent_t_02.csv"])

    def test_mixed_directories_preserve_prefix_and_filename_order(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            archive = root / "knowledge-production/archive/pre-release-workfiles"
            archive.mkdir(parents=True)
            for name in (".agent_t_02.csv", ".agent_t2_01.csv"):
                (archive / name).write_text("archived", encoding="utf-8")
            (root / ".agent_t_01.csv").write_text("current", encoding="utf-8")
            with patch.object(merge_text_judgments, "REPO", root):
                self.assertEqual(merge_text_judgments.collect_files(), [
                    root / ".agent_t_01.csv", archive / ".agent_t_02.csv", archive / ".agent_t2_01.csv"])
                self.assertEqual(merge_text_judgments.collect_files(only="02"), [archive / ".agent_t_02.csv"])


if __name__ == "__main__":
    unittest.main()
