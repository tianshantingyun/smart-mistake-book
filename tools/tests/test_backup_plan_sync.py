# -*- coding: utf-8 -*-
"""备份选择清单四方一致性门的机制测试（批次 41 / WS1 item 7）。

只验证**门脚本本身**：真实仓库当前四方一致；任一侧被改坏时非零退出（exit 1）；
缺文件/无法解析时 exit 2（不把"没读到"当一致）。不验证任何设备/恢复行为。

运行（仓库根）：
    python -m unittest discover -s tools/tests -t tools -k backup_plan_sync
"""
from __future__ import annotations

import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "ci"))

import check_backup_plan_sync as G  # noqa: E402

FILES = [
    G.PLAN_PATH,
    G.RULES_PATH,
    G.TEST_PATH,
    G.SCHEMA_PATH,
    G.DESIGN_PATH,
]


class BackupPlanSyncGateTest(unittest.TestCase):

    def _copy_tree(self, root: Path) -> None:
        for relative in FILES:
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(REPO / relative, target)

    def test_repository_is_currently_synced(self) -> None:
        result = G.evaluate(REPO)
        self.assertTrue(result["ok"], [c for c in result["failed"]])
        self.assertEqual(0, G.main([]))

    def test_identity_hash_drift_exits_one(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._copy_tree(root)
            schema = root / G.SCHEMA_PATH
            data = json.loads(schema.read_text(encoding="utf-8"))
            data["database"]["identityHash"] = "deadbeef" * 8
            schema.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
            try:
                self.assertEqual(1, G.main(["--repo", str(root)]))
            except SystemExit as exit_code:  # main() 返回码，不抛；防御性保留
                self.assertEqual(1, exit_code.code)

    def test_missing_file_exits_two_instead_of_passing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._copy_tree(root)
            (root / G.TEST_PATH).unlink()
            self.assertEqual(2, G.main(["--repo", str(root)]))

    def test_machine_block_drift_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._copy_tree(root)
            design = root / G.DESIGN_PATH
            text = design.read_text(encoding="utf-8")
            self.assertIn('"identityHash": "1d086050204a0da1ed6b6e3299dd9733"', text)
            design.write_text(
                text.replace(
                    '"identityHash": "1d086050204a0da1ed6b6e3299dd9733"',
                    '"identityHash": "00000000000000000000000000000000"',
                    1,
                ),
                encoding="utf-8",
            )
            self.assertEqual(1, G.main(["--repo", str(root)]))


if __name__ == "__main__":
    unittest.main()
