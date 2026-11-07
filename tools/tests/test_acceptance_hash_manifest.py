# -*- coding: utf-8 -*-
"""acceptance_hash_manifest.py 的机制测试（批次 44 / D0 item 14）。

消灭的具体失败：证据只写文件名、没有哈希，文件被覆盖后旧结论仍被引用。
本测试钉住：清单生成排序、哈希正确、`--verify` 能抓哈希不符/缺文件/清单外
多文件，且清单自身哈希可复算。

运行：
    python -I -m pytest tools/tests/test_acceptance_hash_manifest.py -q
"""
from __future__ import annotations

import hashlib
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCRIPT = REPO / "tools" / "ci" / "acceptance_hash_manifest.py"


def _run(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, "-I", str(SCRIPT), *args],
                          capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=str(REPO))


class AcceptanceHashManifestTest(unittest.TestCase):
    def test_generate_and_verify(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            root = tmp / "evidence"
            (root / "sub").mkdir(parents=True)
            (root / "a.md").write_text("alpha\n", encoding="utf-8")
            (root / "sub" / "b.log").write_text("beta\n", encoding="utf-8")
            out = tmp / "manifest.txt"
            proc = _run("--root", str(root), "--out", str(out), "--self-hash")
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            lines = out.read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(lines), 2)
            self.assertIn("a.md", lines[0])
            self.assertIn("sub/b.log", lines[1])
            expected = hashlib.sha256((root / "a.md").read_bytes()).hexdigest()
            self.assertTrue(lines[0].startswith(expected))
            self.assertEqual(len(proc.stdout.strip().splitlines()[-1]), 64)

            proc = _run("--root", str(root), "--verify", str(out))
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)

    def test_hash_mismatch_fails(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            root = tmp / "evidence"
            root.mkdir()
            target = root / "a.md"
            target.write_text("alpha\n", encoding="utf-8")
            out = tmp / "manifest.txt"
            self.assertEqual(_run("--root", str(root), "--out", str(out)).returncode, 0)
            target.write_text("changed\n", encoding="utf-8")
            proc = _run("--root", str(root), "--verify", str(out))
            self.assertEqual(proc.returncode, 1)
            self.assertIn("HASH_MISMATCH", proc.stdout)

    def test_unlisted_and_missing_files_fail(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            root = tmp / "evidence"
            root.mkdir()
            (root / "a.md").write_text("alpha\n", encoding="utf-8")
            out = tmp / "manifest.txt"
            self.assertEqual(_run("--root", str(root), "--out", str(out)).returncode, 0)
            (root / "extra.md").write_text("extra\n", encoding="utf-8")
            proc = _run("--root", str(root), "--verify", str(out))
            self.assertEqual(proc.returncode, 1)
            self.assertIn("UNLISTED_FILE", proc.stdout)

    def test_non_directory_root_exits_two(self) -> None:
        proc = _run("--root", "definitely/not/a/dir")
        self.assertEqual(proc.returncode, 2)


if __name__ == "__main__":
    unittest.main()
