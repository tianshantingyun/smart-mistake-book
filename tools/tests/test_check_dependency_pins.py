# -*- coding: utf-8 -*-
"""依赖钉版门的自测（批次 22 T3）。

它消灭的失败：门的语义只能靠"在真仓库跑一次"证明——解析图重建、AAR 或目录注释变化后，
没人能区分"门绿"与"门根本没检查那条不变量"。每条断言都配 positive 与 negative 两例：

- positive：POM 声明 == 上游台账、解析出的 stdlib == 编译版本、sha256 相符 → 无错；
- negative：解析版本漂移 / POM 声明漂移 / 缺声明 / quickjs 坐标漂移 / 字节不符 → 各自报错。

脚本本身不跑 Gradle：它只读 catalog、Gradle 缓存里的构件与 AGP 生成的解析图。
"""

from __future__ import annotations

import hashlib
import tempfile
import unittest
from pathlib import Path

from ci import check_dependency_pins as pins

POM_WITH_STDLIB = """<project>
  <dependencies>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-stdlib</artifactId>
      <version>{version}</version>
    </dependency>
  </dependencies>
</project>
"""

POM_WITHOUT_STDLIB = "<project><dependencies></dependencies></project>"

RESOLVED_GRAPH = """<dependencies>
  <package roots="io.github.dokar3:quickjs-kt-android:1.0.15@aar"/>
  <package roots="org.jetbrains.kotlin:kotlin-stdlib:2.3.21@jar"/>
  <package roots="androidx.core:core-ktx:1.18.0@aar"/>
</dependencies>
"""


def consistent_facts(**overrides):
    """批次 22 路径 A 下的正确事实；overrides 用来构造 negative 用例。"""
    base = dict(
        quickjs_version="1.0.15",
        upstream_stdlib="2.4.10",
        expected_runtime_stdlib="2.3.21",
        compile_stdlib="2.3.21",
        pom_stdlib="2.4.10",
        resolved_quickjs={"1.0.15"},
        resolved_stdlib={"2.3.21"},
    )
    base.update(overrides)
    return base


class CatalogVersionTest(unittest.TestCase):
    def test_reads_the_entry(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            catalog = Path(tmp) / "libs.versions.toml"
            catalog.write_text('[versions]\nkotlin = "2.3.21"\n', encoding="utf-8")
            self.assertEqual("2.3.21", pins.catalog_version("kotlin", catalog))

    def test_missing_entry_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            catalog = Path(tmp) / "libs.versions.toml"
            catalog.write_text('[versions]\nkotlin = "2.3.21"\n', encoding="utf-8")
            with self.assertRaises(SystemExit):
                pins.catalog_version("quickjs-kotlin-stdlib", catalog)


class PomParseTest(unittest.TestCase):
    def test_extracts_the_declared_stdlib_version(self) -> None:
        self.assertEqual(
            "2.4.10",
            pins.pom_kotlin_stdlib_version(POM_WITH_STDLIB.format(version="2.4.10")),
        )

    def test_returns_none_without_a_stdlib_dependency(self) -> None:
        self.assertIsNone(pins.pom_kotlin_stdlib_version(POM_WITHOUT_STDLIB))


class ResolvedGraphTest(unittest.TestCase):
    def test_extracts_quickjs_and_stdlib_versions(self) -> None:
        quickjs, stdlib = pins.parse_resolved_versions(RESOLVED_GRAPH)
        self.assertEqual({"1.0.15"}, quickjs)
        self.assertEqual({"2.3.21"}, stdlib)

    def test_rejects_doctype_before_parsing(self) -> None:
        doctype_graph = '<!DOCTYPE dependencies [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>' + RESOLVED_GRAPH
        with self.assertRaises(SystemExit):
            pins.parse_resolved_versions(doctype_graph)


class EvaluatePinsTest(unittest.TestCase):
    def test_positive_consistent_facts_have_no_errors(self) -> None:
        self.assertEqual([], pins.evaluate_pins(**consistent_facts()))

    def test_negative_resolved_runtime_drift_is_flagged(self) -> None:
        errors = pins.evaluate_pins(**consistent_facts(resolved_stdlib={"2.4.10"}))
        self.assertTrue(any("resolved kotlin-stdlib" in error for error in errors), errors)

    def test_negative_pom_declaration_drift_is_flagged(self) -> None:
        errors = pins.evaluate_pins(**consistent_facts(pom_stdlib="2.3.21"))
        self.assertTrue(any("upstream" in error for error in errors), errors)

    def test_negative_missing_pom_declaration_is_flagged(self) -> None:
        errors = pins.evaluate_pins(**consistent_facts(pom_stdlib=None))
        self.assertTrue(any("does not declare" in error for error in errors), errors)

    def test_negative_quickjs_coordinate_drift_is_flagged(self) -> None:
        errors = pins.evaluate_pins(**consistent_facts(resolved_quickjs={"1.0.16"}))
        self.assertTrue(any("resolved quickjs" in error for error in errors), errors)

    def test_negative_artifact_errors_are_propagated(self) -> None:
        errors = pins.evaluate_pins(
            **consistent_facts(artifact_errors=["AAR sha256 mismatch: x -> y"]),
        )
        self.assertIn("AAR sha256 mismatch: x -> y", errors)


class ChecksumTest(unittest.TestCase):
    def test_matching_sha256_has_no_errors(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            artifact = Path(tmp) / "quickjs.aar"
            artifact.write_bytes(b"payload")
            expected = hashlib.sha256(b"payload").hexdigest()
            self.assertEqual([], pins.checksum_errors([(artifact, expected, "AAR")]))

    def test_mismatched_sha256_is_flagged(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            artifact = Path(tmp) / "quickjs.aar"
            artifact.write_bytes(b"payload")
            errors = pins.checksum_errors([(artifact, "0" * 64, "AAR")])
            self.assertEqual(1, len(errors), errors)
            self.assertIn("AAR sha256 mismatch", errors[0])


if __name__ == "__main__":
    unittest.main()
