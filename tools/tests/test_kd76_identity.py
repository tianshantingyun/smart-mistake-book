# -*- coding: utf-8 -*-
"""KD-76 身份代际工具的机制测试（批次 43 / WS2 item 11）。

只验证**纯函数**：代际哈希的稳定性与字段敏感性、`dumpsys package` 解析。
adb 交互（拉包、getprop）由 `tools/ci/run_kd76_a6.sh` 的真实运行覆盖，不在此伪造。

运行（仓库根）：
    python -m pytest tools/tests/test_kd76_identity.py -q
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "ci"))

import kd76_identity as I  # noqa: E402

DUMPSYS = """Packages:
  Package [com.example.app] (abc):
    userId=10123
    pkg=Package{def com.example.app}
    versionCode=2 minSdk=23 targetSdk=36
    lastUpdateTime=2026-11-03 08:45:21
    signatures=PackageSignatures{e3ce40c version:2, signatures:[b5264c58], past signatures:[]}
    firstInstallTime=2026-11-02 22:59:44
"""


class Kd76IdentityTest(unittest.TestCase):

    def test_canonical_generation_is_order_independent(self) -> None:
        left = I.canonical_generation({"b": "2", "a": "1"})
        right = I.canonical_generation({"a": "1", "b": "2"})
        self.assertEqual(left, right)
        self.assertEqual(64, len(left))

    def test_canonical_generation_changes_with_any_field(self) -> None:
        base = I.canonical_generation({"serial": "emulator-5584", "avdName": "kb_test_device"})
        changed = I.canonical_generation({"serial": "emulator-5584", "avdName": "other"})
        self.assertNotEqual(base, changed)

    def test_canonical_generation_does_not_silently_drop_missing_fields(self) -> None:
        # 字段集合本身进入哈希：少一个键就换一个代际（不能被"空值"悄悄等价掉）。
        with_field = I.canonical_generation({"serial": "x", "avdName": ""})
        without_field = I.canonical_generation({"serial": "x"})
        self.assertNotEqual(with_field, without_field)
        # 空值与显式 <missing> 等价（调用方可以显式写占位，不改变语义）。
        explicit = I.canonical_generation({"serial": "x", "avdName": "<missing>"})
        self.assertEqual(with_field, explicit)

    def test_parse_dumpsys_package_extracts_install_generation_fields(self) -> None:
        fields = I.parse_dumpsys_package(DUMPSYS)
        self.assertEqual("2", fields["versionCode"])
        self.assertEqual("2026-11-02 22:59:44", fields["firstInstallTime"])
        self.assertEqual("2026-11-03 08:45:21", fields["lastUpdateTime"])
        self.assertEqual(64, len(fields["signaturesDigest"]))
        self.assertNotEqual(
            fields["signaturesDigest"], I.parse_dumpsys_package(DUMPSYS.replace("b5264c58", "ffffffff"))["signaturesDigest"]
        )

    def test_parse_dumpsys_package_missing_fields_stay_empty(self) -> None:
        fields = I.parse_dumpsys_package("Packages:\n")
        self.assertEqual("", fields["versionCode"])
        self.assertEqual("", fields["signaturesDigest"])

    def test_signatures_digest_ignores_the_unstable_object_hash(self) -> None:
        # 批次 43 实跑教训：`PackageSignatures{<objhash> ...}` 的对象哈希每次 reboot 都变；
        # 只哈希 `signatures:[...]` 列表才能让同一安装在 20 次 reboot 后仍是同一代际。
        first = I.parse_dumpsys_package(DUMPSYS)
        second = I.parse_dumpsys_package(DUMPSYS.replace("e3ce40c", "9f9f9f9"))
        self.assertEqual(first["signaturesDigest"], second["signaturesDigest"])
        different_sig = I.parse_dumpsys_package(DUMPSYS.replace("[b5264c58]", "[ffffffff]"))
        self.assertNotEqual(first["signaturesDigest"], different_sig["signaturesDigest"])


if __name__ == "__main__":
    unittest.main()
