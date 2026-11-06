# -*- coding: utf-8 -*-
"""T4 控制轮可核标记的机制测试（批次 41 / WS4 item 27）。

只验证"标记是否可核"：
- ON 标记必须记录引擎 sha 与 6 处打点行的行级 sha，以及 APK sha/类清单；
- 构建日志声明 `T4_REVERT_APPLIED` 但源码里打点行仍在 → 标记无效（不得写成已移除）；
- OFF 标记要求打点行为 0，且 verify 时引擎 sha/行级 sha/APK sha/类清单任一漂移即失败；
- 没有 APK 身份（源码树之外的构建身份）→ 标记无效。

运行（仓库根）：
    python -m unittest discover -s tools/tests -t tools -k t4_control_marker
"""
from __future__ import annotations

import json
import struct
import sys
import tempfile
import unittest
import zipfile
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


def _uleb128(value: int) -> bytes:
    encoded = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            encoded.append(byte | 0x80)
        else:
            encoded.append(byte)
            return bytes(encoded)


def minimal_dex(descriptors: tuple[str, ...]) -> bytes:
    """构造只含 string_ids/type_ids/class_defs 的最小 dex，供类清单解析测试。"""
    strings = [descriptor.encode("utf-8") for descriptor in descriptors]
    header_size = 0x70
    string_ids_off = header_size
    type_ids_off = string_ids_off + 4 * len(strings)
    class_defs_off = type_ids_off + 4 * len(strings)
    data_off = class_defs_off + 32 * len(strings)
    data = bytearray()
    string_offsets = []
    for text in strings:
        string_offsets.append(data_off + len(data))
        data += _uleb128(len(text)) + text
    file_size = data_off + len(data)
    dex = bytearray(data_off)
    dex[0:8] = b"dex\n035\x00"
    struct.pack_into("<I", dex, 0x20, file_size)
    struct.pack_into("<I", dex, 0x24, header_size)
    struct.pack_into("<I", dex, 0x28, 0x12345678)
    struct.pack_into("<I", dex, 0x38, len(strings))
    struct.pack_into("<I", dex, 0x3C, string_ids_off)
    struct.pack_into("<I", dex, 0x40, len(strings))
    struct.pack_into("<I", dex, 0x44, type_ids_off)
    struct.pack_into("<I", dex, 0x60, len(strings))
    struct.pack_into("<I", dex, 0x64, class_defs_off)
    struct.pack_into("<I", dex, 0x68, len(data))
    struct.pack_into("<I", dex, 0x6C, data_off)
    for index, offset in enumerate(string_offsets):
        struct.pack_into("<I", dex, string_ids_off + 4 * index, offset)
    for index in range(len(strings)):
        struct.pack_into("<I", dex, type_ids_off + 4 * index, index)
        struct.pack_into("<I", dex, class_defs_off + 32 * index, index)
        struct.pack_into("<I", dex, class_defs_off + 32 * index + 8, 0xFFFFFFFF)
    return bytes(dex) + bytes(data)


class MarkerTest(unittest.TestCase):

    def _repo(self, root: Path, body: str) -> Path:
        engine = root / T.ENGINE_PATH
        engine.parent.mkdir(parents=True, exist_ok=True)
        engine.write_text(body, encoding="utf-8")
        return root

    def _apk(self, root: Path, descriptors: tuple[str, ...] = ("Lx/Engine;",)) -> Path:
        apk = root / "app.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            archive.writestr("classes.dex", minimal_dex(descriptors))
            archive.writestr("AndroidManifest.xml", b"manifest")
        return apk

    def _installed(self, root: Path, descriptors: tuple[str, ...] = ("Lx/Engine;",)) -> dict:
        identity = T.file_apk_identity(self._apk(root, descriptors))
        identity.update({"serial": "emulator-test", "package": T.DEFAULT_PACKAGE})
        return identity

    def test_on_marker_records_engine_sha_and_six_anchors(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self._repo(Path(directory), ENGINE_BODY)
            marker = T.build_marker(
                root, "BUILD SUCCESSFUL\ntask: assembleLocalFirstDebug\n",
                apk_identity=T.file_apk_identity(self._apk(root)),
            )
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
            apk = self._apk(root)
            marker = T.build_marker(
                root, "T4_REVERT_APPLIED\nBUILD SUCCESSFUL\n",
                apk_identity=T.file_apk_identity(apk),
            )
            self.assertFalse(marker["ok"])
            self.assertIn("marker_claims_removed_but_anchors_present", marker["reasons"])
            self.assertEqual(T.EXIT_INVALID, T.main([
                "emit", "--repo", str(root), "--build-log", str(self._log(root, "T4_REVERT_APPLIED")),
                "--apk", str(apk), "--out", str(root / "marker.json"),
            ]))

    def _log(self, root: Path, text: str) -> Path:
        path = root / "build.log"
        path.write_text(text, encoding="utf-8")
        return path

    def test_off_marker_verifies_and_detects_engine_drift(self) -> None:
        reverted = "package x\n\nclass Engine {\n    // T4 seam removed for control round\n}\n"
        with tempfile.TemporaryDirectory() as directory:
            root = self._repo(Path(directory), reverted)
            apk = self._apk(root)
            marker_path = root / "marker.json"
            code = T.main([
                "emit", "--repo", str(root),
                "--build-log", str(self._log(root, "T4_REVERT_APPLIED\nBUILD SUCCESSFUL\n")),
                "--apk", str(apk), "--out", str(marker_path),
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

    def test_source_only_marker_is_invalid(self) -> None:
        """F8：没有 APK 身份时，源码树一致不能证明已安装包一致。"""
        with tempfile.TemporaryDirectory() as directory:
            root = self._repo(Path(directory), ENGINE_BODY)
            marker = T.build_marker(root, "BUILD SUCCESSFUL\n")
            self.assertFalse(marker["ok"])
            self.assertIn("no_apk_identity", marker["reasons"])
            self.assertEqual(T.EXIT_INVALID, T.main([
                "emit", "--repo", str(root),
                "--build-log", str(self._log(root, "BUILD SUCCESSFUL\n")),
                "--out", str(root / "marker.json"),
            ]))

    def test_installed_apk_class_drift_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = self._repo(Path(directory), ENGINE_BODY)
            installed = self._installed(root)
            marker = T.build_marker(
                root, "BUILD SUCCESSFUL\n", installed_identity=installed
            )
            self.assertTrue(marker["ok"], marker["reasons"])
            self.assertEqual([], T.verify_marker(root, marker, installed_identity=installed)["reasons"])

            drifted = self._installed(root, ("Lx/Other;",))
            result = T.verify_marker(root, marker, installed_identity=drifted)
            self.assertFalse(result["ok"])
            self.assertIn("apk_sha_drift", result["reasons"])
            self.assertIn("apk_classes_drift", result["reasons"])

            unavailable = T.verify_marker(root, marker)
            self.assertFalse(unavailable["ok"])
            self.assertIn("installed_apk_unavailable", unavailable["reasons"])

            # 构建产物与已安装包必须同源：两份身份都在也不等于可以跳过对拍。
            artifact = T.file_apk_identity(self._apk(root, ("Lx/Engine;",)))
            mismatch = T.build_marker(
                root, "BUILD SUCCESSFUL\n",
                apk_identity=artifact, installed_identity=drifted,
            )
            self.assertFalse(mismatch["ok"])
            self.assertIn("installed_apk_differs_from_artifact", mismatch["reasons"])

    def test_dex_class_descriptors_are_read(self) -> None:
        dex = minimal_dex(("Lx/Engine;", "Lx/Other;"))
        self.assertEqual(["Lx/Engine;", "Lx/Other;"], T.dex_class_descriptors(dex))


if __name__ == "__main__":
    unittest.main()
