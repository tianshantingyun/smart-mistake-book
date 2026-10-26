# -*- coding: utf-8 -*-
"""依赖锁文件门（tools/ci/check_lockfiles.py）的自测。

它的价值在于把"锁没落地"与"锁落了一半"区分开：前者只提示、不假绿；
后者逐项报出缺模块、缺运行期配置、缺测试配置、以及脱离 include 列表的锁。
每条断言都配一个正例或反例，避免脚本静默通过。
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from ci import check_lockfiles as lockfiles

ANDROID_APP = "plugins {\n    alias(libs.plugins.android.application)\n}\n"
ANDROID_LIB = "plugins {\n    alias(libs.plugins.android.library)\n}\n"
ANDROID_TEST = "plugins {\n    alias(libs.plugins.android.test)\n}\n"
JVM = "plugins {\n    alias(libs.plugins.kotlin.jvm)\n}\n"


def write_settings(root: Path, projects: list[str]) -> None:
    body = "\n".join(f'    "{project}",' for project in projects)
    (root / "settings.gradle.kts").write_text(f"include(\n{body}\n)\n", encoding="utf-8")


def write_project(
    root: Path,
    project: str,
    build_text: str,
    configs: list[str] | None,
) -> None:
    directory = root / "/".join(project.lstrip(":").split(":"))
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "build.gradle.kts").write_text(build_text, encoding="utf-8")
    if configs is None:
        return
    lines = [
        "# This is a Gradle generated file for dependency locking.",
        "# This file is expected to be part of source control.",
    ]
    for index, config in enumerate(configs):
        lines.append(f"com.example:lib{index}:1.0={config}")
    (directory / "gradle.lockfile").write_text("\n".join(lines) + "\n", encoding="utf-8")


def android_app_configs() -> list[str]:
    return [
        "localFirstDebugCompileClasspath",
        "localFirstDebugRuntimeClasspath",
        "localFirstDebugUnitTestCompileClasspath",
        "localFirstDebugAndroidTestRuntimeClasspath",
    ]


def android_lib_configs() -> list[str]:
    return [
        "debugCompileClasspath",
        "debugRuntimeClasspath",
        "debugUnitTestCompileClasspath",
        "debugAndroidTestRuntimeClasspath",
    ]


def jvm_configs() -> list[str]:
    return [
        "compileClasspath",
        "runtimeClasspath",
        "testCompileClasspath",
        "testRuntimeClasspath",
    ]


def android_test_configs() -> list[str]:
    return ["debugCompileClasspath", "debugRuntimeClasspath"]


class CheckLockfilesTest(unittest.TestCase):
    def test_parse_included_projects_handles_multiple_blocks(self) -> None:
        text = 'include(":app", ":core:model")\ninclude(\n  ":benchmark",\n)\n'
        self.assertEqual(
            [":app", ":core:model", ":benchmark"],
            lockfiles.parse_included_projects(text),
        )

    def test_no_tracked_lockfiles_is_a_notice_not_a_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_settings(root, [":app"])
            write_project(root, ":app", ANDROID_APP, None)

            errors, notice = lockfiles.check(root, tracked=[])

            self.assertEqual([], errors)
            self.assertIn("尚未落地", notice)
            self.assertIn("缺锁 project", notice)
            self.assertIn(":app", notice)

    def test_missing_project_lockfile_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_settings(root, [":app", ":core:model"])
            write_project(root, ":app", ANDROID_APP, android_app_configs())
            write_project(root, ":core:model", JVM, None)

            errors, _ = lockfiles.check(root, tracked=["app/gradle.lockfile"])

            self.assertTrue(any(":core:model" in error and "缺" in error for error in errors), errors)

    def test_untracked_lockfile_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_settings(root, [":app", ":core:model"])
            write_project(root, ":app", ANDROID_APP, android_app_configs())
            write_project(root, ":core:model", JVM, jvm_configs())

            errors, _ = lockfiles.check(root, tracked=["app/gradle.lockfile"])

            self.assertTrue(
                any(":core:model" in error and "未入库" in error for error in errors),
                errors,
            )

    def test_android_lock_without_test_or_runtime_configs_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_settings(root, [":app"])
            write_project(root, ":app", ANDROID_APP, ["localFirstDebugCompileClasspath"])

            errors, _ = lockfiles.check(root, tracked=["app/gradle.lockfile"])

            joined = "\n".join(errors)
            self.assertIn("RuntimeClasspath", joined)
            self.assertIn("unitTest", joined)
            self.assertIn("androidTest", joined)

    def test_jvm_lock_without_test_config_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_settings(root, [":core:model"])
            write_project(root, ":core:model", JVM, ["compileClasspath", "runtimeClasspath"])

            errors, _ = lockfiles.check(root, tracked=["core/model/gradle.lockfile"])

            self.assertTrue(any("test" in error for error in errors), errors)

    def test_complete_lock_set_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_settings(root, [":app", ":core:model", ":benchmark"])
            write_project(root, ":app", ANDROID_APP, android_app_configs())
            write_project(root, ":core:model", JVM, jvm_configs())
            write_project(root, ":benchmark", ANDROID_TEST, android_test_configs())

            errors, notice = lockfiles.check(
                root,
                tracked=[
                    "app/gradle.lockfile",
                    "core/model/gradle.lockfile",
                    "benchmark/gradle.lockfile",
                ],
            )

            self.assertEqual([], errors)
            self.assertIn("3 个 included project", notice)

    def test_lockfile_for_unincluded_project_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_settings(root, [":app"])
            write_project(root, ":app", ANDROID_APP, android_app_configs())
            write_project(root, ":core:markdown", ANDROID_LIB, android_lib_configs())

            errors, _ = lockfiles.check(
                root,
                tracked=["app/gradle.lockfile", "core/markdown/gradle.lockfile"],
            )

            self.assertTrue(
                any("core/markdown" in error and "不在 settings" in error for error in errors),
                errors,
            )

    def test_settings_gradle_lockfile_is_not_a_project_lock(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write_settings(root, [":app"])
            write_project(root, ":app", ANDROID_APP, android_app_configs())
            (root / "settings-gradle.lockfile").write_text("empty=incomingCatalogForLibs0\n", encoding="utf-8")

            errors, _ = lockfiles.check(
                root,
                tracked=["settings-gradle.lockfile", "app/gradle.lockfile"],
            )

            self.assertEqual([], errors)


if __name__ == "__main__":
    unittest.main()
