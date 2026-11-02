# -*- coding: utf-8 -*-
"""T4 控制轮可核标记的机制测试（批次 41 / WS4 item 27）。

只验证"标记是否可核"：
- ON 标记必须记录引擎 sha 与 6 处打点行的行级 sha；
- 构建日志声明 `T4_REVERT_APPLIED` 但源码里打点行仍在 → 标记无效（不得写成已移除）；
- OFF 标记要求打点行为 0，且 verify 时引擎 sha/行级 sha 任一漂移即失败。

运行（仓库根）：
    python -m unittest discover -s tools/tests -t tools -k t4_control_marker
"""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "ci"))

import t4_control_marker as T  # noqa: E402

ENGINE_BODY = """package x

class Engine {
    fun a() {
        candidate.timestamps.bindRequestedAtMillis = SystemClock.uptimeMillis()
    }
    fun b() {
        timestamps.bindConnectedAtMillis = SystemClock.uptimeMillis()
    }
    fun c() {
        candidate.timestamps.readyAtMillis = SystemClock.uptimeMillis()
    }
    fun d() {
        candidate.timestamps.readyAtMillis = SystemClock.uptimeMillis()
    }
    fun e() {
        connection.timestamps.executeAcceptedAtMillis = SystemClock.uptimeMillis()
    }
    fun f() {
        timestamps.firstByteAtMillis = SystemClock.uptimeMillis()
    }
}
"""


class MarkerTest(unittest.TestCase):

    def _repo(self, root: Path, body: str) -> Path:
        engine = root / T.ENGINE_PATH
        engine.parent.mkdir(parents=True, exist_ok=True)
        engine.write_text(body, encoding="utf-8")
        return root

    def test_on_marker_records_engine_sha_and_six_anchors(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self._repo(Path(directory), ENGINE_BODY)
            marker = T.build_marker(root, "BUILD SUCCESSFUL\ntask: assembleLocalFirstDebug\n")
            self.assertTrue(marker["ok"], marker["reasons"])
            self.assertEqual("ON_PRODUCTION_T4_PRESENT", marker["arm"])
            self.assertEqual(6, marker["anchor_count"])
            self.assertEqual(6, len(T.find_anchors(ENGINE_BODY)))
            self.assertEqual(64, len(marker["engine_sha256"]))
            for anchor in marker["anchors"]:
                self.assertEqual(64, len(anchor["line_sha256"]))

    def test_claiming_revert_while_anchors_remain_is_invalid(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self._repo(Path(directory), ENGINE_BODY)
            marker = T.build_marker(root, "T4_REVERT_APPLIED\nBUILD SUCCESSFUL\n")
            self.assertFalse(marker["ok"])
            self.assertIn("marker_claims_removed_but_anchors_present", marker["reasons"])
            self.assertEqual(T.EXIT_INVALID, T.main([
                "emit", "--repo", str(root), "--build-log", str(self._log(root, "T4_REVERT_APPLIED")),
                "--out", str(root / "marker.json"),
            ]))

    def _log(self, root: Path, text: str) -> Path:
        path = root / "build.log"
        path.write_text(text, encoding="utf-8")
        return path

    def test_off_marker_verifies_and_detects_engine_drift(self) -> None:
        reverted = "package x\n\nclass Engine {\n    // T4 seam removed for control round\n}\n"
        with tempfile.TemporaryDirectory() as directory:
            root = self._repo(Path(directory), reverted)
            marker_path = root / "marker.json"
            code = T.main([
                "emit", "--repo", str(root),
                "--build-log", str(self._log(root, "T4_REVERT_APPLIED\nBUILD SUCCESSFUL\n")),
                "--out", str(marker_path),
            ])
            self.assertEqual(0, code)
            marker = json.loads(marker_path.read_text(encoding="utf-8"))
            self.assertEqual("OFF_PRODUCTION_T4_REMOVED", marker["arm"])
            self.assertEqual(0, marker["anchor_count"])
            self.assertEqual(0, T.main(["verify", "--repo", str(root), "--marker", str(marker_path)]))

            engine = root / T.ENGINE_PATH
            engine.write_text(ENGINE_BODY, encoding="utf-8")
            self.assertEqual(T.EXIT_INVALID,
                             T.main(["verify", "--repo", str(root), "--marker", str(marker_path)]))

    def test_missing_build_log_is_an_input_error(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self._repo(Path(directory), ENGINE_BODY)
            self.assertEqual(2, T.main([
                "emit", "--repo", str(root), "--build-log", str(root / "nope.log"),
                "--out", str(root / "marker.json"),
            ]))


if __name__ == "__main__":
    unittest.main()
