# -*- coding: utf-8 -*-
"""备份选择清单四方一致性门的机制测试（批次 41 / WS1 item 7）。

只验证**门脚本本身**：真实仓库当前四方一致；任一侧被改坏时非零退出（exit 1）；
缺文件/无法解析时 exit 2（不把"没读到"当一致）。不验证任何设备/恢复行为。

批次 43 WS7 S11 补：v4 冻结夹具（`tools/backup_fixtures/v4/`）的字节、
sidecar 与 golden 也必须在门的消费面内——此前只有代码侧独立复算的空包摘要，
重打/重算夹具不会被任何门抓住。

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
    G.DATABASE_PATH,
    G.DESIGN_PATH,
    G.GOLDEN_V4_PATH,
]

FIXTURE_DIR = Path(G.FIXTURE_DIR)
FIXTURE_PACKS = (
    "closure4-schema72-mask15.smbk",
    "forged-count.smbk",
    "dropped-nonempty.smbk",
)


class BackupPlanSyncGateTest(unittest.TestCase):

    def _copy_tree(self, root: Path) -> None:
        for relative in FILES:
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(REPO / relative, target)
        for name in FIXTURE_PACKS:
            for suffix in (name, name + ".sha256"):
                shutil.copy2(REPO / FIXTURE_DIR / suffix, root / FIXTURE_DIR / suffix)

    def test_repository_is_currently_synced(self) -> None:
        result = G.evaluate(REPO)
        self.assertTrue(result["ok"], [c for c in result["failed"]])
        self.assertEqual(0, G.main([]))

    def test_rule_sets_do_not_absorb_adjacent_declarations(self) -> None:
        rules = G.parse_rules(REPO / G.RULES_PATH)
        self.assertEqual(["knowledge_search_material_feature"], rules["materialFeatureTables"])
        self.assertEqual({"tutor_reply_group", "tutor_reply_version", "tutor_reply_version_answer"},
                         set(rules["replyVersionTables"]))
        self.assertNotIn("smbk-selection-v6", rules["materialFeatureTables"])

    def test_database_schema_drift_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._copy_tree(root)
            database = root / G.DATABASE_PATH
            text = database.read_text(encoding="utf-8")
            self.assertIn("const val STUDY_DATABASE_VERSION = 76", text)
            database.write_text(text.replace("const val STUDY_DATABASE_VERSION = 76",
                                             "const val STUDY_DATABASE_VERSION = 77", 1), encoding="utf-8")
            result = G.evaluate(root)
            self.assertFalse(result["ok"])
            self.assertIn("schemaVersion: code==StudyDatabase", [c["name"] for c in result["failed"]])
            self.assertEqual(1, G.main(["--repo", str(root)]))

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
            self.assertIn('"identityHash": "5e6ec85c861c772d5106a5c2d771d5bb"', text)
            design.write_text(
                text.replace(
                    '"identityHash": "5e6ec85c861c772d5106a5c2d771d5bb"',
                    '"identityHash": "00000000000000000000000000000000"',
                    1,
                ),
                encoding="utf-8",
            )
            self.assertEqual(1, G.main(["--repo", str(root)]))

    def test_machine_block_marker_drift_exits_two(self) -> None:
        """v3 begin/end 不成对（本轮 KD-80 复发形态）不得被当成"一致"。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._copy_tree(root)
            design = root / G.DESIGN_PATH
            text = design.read_text(encoding="utf-8")
            self.assertIn("\n<!-- 6c2-plan-v3:end -->\n", text)
            design.write_text(
                text.replace("\n<!-- 6c2-plan-v3:end -->\n", "\n<!-- 6c2-plan-v3:closed -->\n", 1),
                encoding="utf-8",
            )
            self.assertEqual(2, G.main(["--repo", str(root)]))

    def test_v4_fixture_byte_drift_is_detected(self) -> None:
        """重打包/篡改冻结夹具字节必须被门抓住（此前门完全不读夹具）。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._copy_tree(root)
            pack = root / FIXTURE_DIR / FIXTURE_PACKS[0]
            data = bytearray(pack.read_bytes())
            data[len(data) // 2] ^= 0xFF
            pack.write_bytes(bytes(data))
            self.assertEqual(1, G.main(["--repo", str(root)]))

    def test_v4_golden_digest_drift_is_detected(self) -> None:
        """golden 的摘要被改（而夹具字节未动）必须被门抓住。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._copy_tree(root)
            golden_path = root / G.GOLDEN_V4_PATH
            golden = json.loads(golden_path.read_text(encoding="utf-8"))
            golden["closureDigest"] = "0" * 64
            golden_path.write_text(json.dumps(golden, ensure_ascii=False), encoding="utf-8")
            self.assertEqual(1, G.main(["--repo", str(root)]))

    def test_v4_golden_row_counts_drift_is_detected(self) -> None:
        """golden 的行数被改但摘要没跟着改 → 独立复算不相等，必须 exit 1。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._copy_tree(root)
            golden_path = root / G.GOLDEN_V4_PATH
            golden = json.loads(golden_path.read_text(encoding="utf-8"))
            golden["rowCounts"]["problem"] = 2
            golden_path.write_text(json.dumps(golden, ensure_ascii=False), encoding="utf-8")
            self.assertEqual(1, G.main(["--repo", str(root)]))

    def test_missing_v4_fixture_exits_two_instead_of_passing(self) -> None:
        """夹具缺失时没有"一致"可言，必须是 exit 2 而不是通过。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._copy_tree(root)
            (root / FIXTURE_DIR / FIXTURE_PACKS[0]).unlink()
            self.assertEqual(2, G.main(["--repo", str(root)]))


if __name__ == "__main__":
    unittest.main()
