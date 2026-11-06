# -*- coding: utf-8 -*-
"""metamorphic_checks.py 的机制测试（批次 44 / D0 item 7，计划 §0 蜕变测试）。

消灭的具体失败：没有 oracle 时把“看起来对”当通过。每条关系必须有可执行的
输入变换与必守断言；关系失败要给出反例，且缺夹具时 exit 2，不得判 PASS。

运行：
    python -I -m pytest tools/tests/test_metamorphic_checks.py -q
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCRIPT = REPO / "tools" / "ci" / "metamorphic_checks.py"


def _run(*args: str) -> tuple[subprocess.CompletedProcess, dict | None]:
    with tempfile.TemporaryDirectory() as td:
        out = Path(td) / "meta.json"
        proc = subprocess.run(
            [sys.executable, "-I", str(SCRIPT), *args, "--out", str(out)],
            capture_output=True, text=True, cwd=str(REPO),
        )
        data = json.loads(out.read_text(encoding="utf-8")) if out.exists() else None
        return proc, data


class MetamorphicChecksTest(unittest.TestCase):
    def test_demo_runs_all_relations(self) -> None:
        proc, data = _run("--demo")
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertEqual(len(data["relations"]), 12)
        self.assertTrue(all(r["ok"] for r in data["relations"]))
        self.assertEqual({r["domain"] for r in data["relations"]},
                         {"render", "export", "retrieval", "scheduling"})

    def test_missing_pairs_exit_two(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            proc, _ = _run("--pairs-dir", str(Path(td) / "nope"))
            self.assertEqual(proc.returncode, 2, proc.stdout + proc.stderr)

    def test_broken_relation_returns_counterexample(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            pairs = Path(td)
            d = pairs / "render"
            d.mkdir(parents=True)
            before = {"nodes": [{"kind": "label", "text": "a b"}], "theme": "light"}
            after = {"nodes": [{"kind": "label", "text": "a c"}], "theme": "light"}
            (d / "reflow_text.before.json").write_text(json.dumps(before), encoding="utf-8")
            (d / "reflow_text.after.json").write_text(json.dumps(after), encoding="utf-8")
            proc, data = _run("--pairs-dir", str(pairs), "--only", "render/reflow_text")
            self.assertEqual(proc.returncode, 1, proc.stdout + proc.stderr)
            self.assertFalse(data["relations"][0]["ok"])
            self.assertTrue(data["relations"][0]["counterexample"])

    def test_unrelated_retrieval_addition_preserves_scores(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            pairs = Path(td)
            d = pairs / "retrieval"
            d.mkdir(parents=True)
            before = {"docs": [{"id": "d1", "text": "alpha"}, {"id": "d2", "text": "beta"}],
                      "queries": [{"q": "alpha", "top1": "d1", "scores": {"d1": 0.9, "d2": 0.1}}]}
            after = {"docs": [{"id": "d1", "text": "alpha"}, {"id": "d2", "text": "beta"},
                              {"id": "d3", "text": "gamma"}],
                     "queries": [{"q": "alpha", "top1": "d1", "scores": {"d1": 0.9, "d2": 0.1}}]}
            (d / "append_unrelated.before.json").write_text(json.dumps(before), encoding="utf-8")
            (d / "append_unrelated.after.json").write_text(json.dumps(after), encoding="utf-8")
            proc, data = _run("--pairs-dir", str(pairs), "--only", "retrieval/append_unrelated")
            self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
            self.assertTrue(data["relations"][0]["ok"])


if __name__ == "__main__":
    unittest.main()
