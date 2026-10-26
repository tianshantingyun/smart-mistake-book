#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""依赖锁文件存在性/覆盖门（批次 23 T1 复核修复，KD-72）。

它消灭的具体失败：

1. 新模块被 include 但没有 ``gradle.lockfile``——Gradle ``DEFAULT`` 锁模式对
   "无锁状态"放行，``--write-locks`` 的 diff 也看不见从未解析过的模块
   （KD-72 复核发现 #6：``:benchmark`` 缺锁，而 ``:core:markdown`` 的未跟踪锁
   把缺口掩盖了）。
2. 锁文件只覆盖 ``debugCompileClasspath``——CI 的单测 / ``connectedAndroidTest``
   依赖漂移不会触发锁校验（复核发现 #5）。
3. 锁文件脱离未入库模块单独提交（R3 §6 P5：``:core:markdown`` 的锁不能在没有
   模块源码的情况下入库）。

语义：

- **锁尚未落地**（没有任何已跟踪的项目锁文件）时，本门只打印提示并放行。
  此时工作流的 ``--write-locks`` step 也会跳过；KD-72 仍是 open，不能把
  "没有锁"误报成通过。
- **一旦有第一个项目锁被跟踪**，本门强制：
  - ``settings.gradle.kts`` 里每个 ``include`` 的 project 都有已跟踪的
    ``<project>/gradle.lockfile``；
  - 每个锁至少有一条 ``*CompileClasspath`` 和一条 ``*RuntimeClasspath``；
  - Android application/library 还必须有 unitTest 与 androidTest 配置；
  - Kotlin JVM 模块必须有 test 配置；
  - 不允许出现"锁文件对应的 project 不在 include 里"的条目。

不运行 Gradle、不联网；只读 ``settings.gradle.kts``、各模块 build script 与锁文件。
退出码 0 = 通过/尚未落地，1 = 缺口。
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

INCLUDE_BLOCK_RE = re.compile(r"include\s*\((.*?)\)", re.DOTALL)
QUOTED_PROJECT_RE = re.compile(r'"(:[A-Za-z0-9_.:-]+)"')
LOCKFILE_NAME = "gradle.lockfile"


def parse_included_projects(settings_text: str) -> list[str]:
    """Return the ``:a:b`` project paths declared by every ``include(...)`` block."""

    projects: list[str] = []
    seen: set[str] = set()
    for block in INCLUDE_BLOCK_RE.findall(settings_text):
        for project in QUOTED_PROJECT_RE.findall(block):
            if project not in seen:
                seen.add(project)
                projects.append(project)
    return projects


def project_relative_dir(project: str) -> str:
    """``:core:model`` -> ``core/model``; ``:app`` -> ``app``."""

    return "/".join(project.lstrip(":").split(":"))


def project_lockfile(project: str) -> str:
    return f"{project_relative_dir(project)}/{LOCKFILE_NAME}"


def parse_lock_configs(lock_text: str) -> set[str]:
    """Collect every configuration name mentioned on the right side of ``=``.

    Gradle's lock format is ``group:artifact:version=configA,configB`` and
    ``empty=configA`` for locked-but-empty configurations. Comments start with
    ``#``. Unknown lines are ignored rather than guessed at.
    """

    configs: set[str] = set()
    for raw_line in lock_text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        _, separator, right = line.partition("=")
        if not separator:
            continue
        for name in right.split(","):
            name = name.strip()
            if name:
                configs.add(name)
    return configs


def reads_as_android_application_or_library(build_text: str) -> bool:
    return (
        "com.android.application" in build_text
        or "com.android.library" in build_text
        or "libs.plugins.android.application" in build_text
        or "libs.plugins.android.library" in build_text
    )


def reads_as_android_test(build_text: str) -> bool:
    return (
        "com.android.test" in build_text
        or "libs.plugins.android.test" in build_text
    )


def _has_config(configs: set[str], suffix: str) -> bool:
    return any(name.lower().endswith(suffix) for name in configs)


def _has_config_containing(configs: set[str], needle: str) -> bool:
    return any(needle in name.lower() for name in configs)


def validate_lock_configs(project: str, configs: set[str], build_text: str) -> list[str]:
    errors: list[str] = []
    if not _has_config(configs, "compileclasspath"):
        errors.append(f"{project}: 锁文件缺 *CompileClasspath 配置（运行期/测试面未锁）")
    if not _has_config(configs, "runtimeclasspath"):
        errors.append(f"{project}: 锁文件缺 *RuntimeClasspath 配置（运行期未锁）")
    if reads_as_android_application_or_library(build_text):
        if not _has_config_containing(configs, "unittest"):
            errors.append(f"{project}: Android 模块的锁缺 unitTest 配置（复核发现 #5）")
        if not _has_config_containing(configs, "androidtest"):
            errors.append(f"{project}: Android 模块的锁缺 androidTest 配置（复核发现 #5）")
    elif not reads_as_android_test(build_text):
        # Kotlin JVM modules use testCompileClasspath / testRuntimeClasspath.
        if not _has_config_containing(configs, "test"):
            errors.append(f"{project}: JVM 模块的锁缺 test 配置（复核发现 #5）")
    return errors


def _read_project_build_script(root: Path, project: str) -> str:
    directory = root / project_relative_dir(project)
    for name in ("build.gradle.kts", "build.gradle"):
        candidate = directory / name
        if candidate.is_file():
            return candidate.read_text(encoding="utf-8")
    return ""


def _tracked_lockfiles(root: Path) -> list[str]:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "*gradle.lockfile"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if result.returncode != 0:
        raise SystemExit(f"git ls-files failed: {result.stderr.strip()}")
    return [line.strip().replace("\\", "/") for line in result.stdout.splitlines() if line.strip()]


def check(root: Path, tracked: list[str] | None = None) -> tuple[list[str], str]:
    """Return (errors, notice). ``tracked`` is injectable for tests."""

    settings_path = root / "settings.gradle.kts"
    if not settings_path.is_file():
        return ["settings.gradle.kts not found"], ""
    settings_text = settings_path.read_text(encoding="utf-8")
    included = parse_included_projects(settings_text)
    if not included:
        return ["no include(...) projects parsed from settings.gradle.kts"], ""

    tracked = _tracked_lockfiles(root) if tracked is None else tracked
    tracked = [path.replace("\\", "/") for path in tracked]
    project_locks = {
        path for path in tracked
        if path.endswith(f"/{LOCKFILE_NAME}")
    }
    expected = {project_lockfile(project) for project in included}

    if not project_locks:
        untracked: list[str] = []
        for project in included:
            relative = project_lockfile(project)
            if (root / relative).is_file():
                untracked.append(relative)
        missing = [
            project for project in included
            if not (root / project_lockfile(project)).is_file()
        ]
        notice = (
            "dependency locking 尚未落地：没有任何已跟踪的 gradle.lockfile，"
            "KD-72 保持 open，本门跳过（CI 的 --write-locks step 同样跳过）"
        )
        if untracked:
            notice += f"；工作区存在 {len(untracked)} 个未跟踪锁文件（例如 {untracked[0]}），不能原样提交"
        if missing:
            notice += f"；缺锁 project：{', '.join(missing)}（DEFAULT 模式下不会报错）"
        return [], notice

    errors: list[str] = []
    for project in included:
        relative = project_lockfile(project)
        path = root / relative
        if relative not in project_locks:
            if path.is_file():
                errors.append(
                    f"{project}: {relative} 存在但未入库；锁必须与模块源码一起提交"
                )
            else:
                errors.append(
                    f"{project}: 缺 {relative}；DEFAULT 模式不会为缺锁 project 报错"
                )
            continue
        if not path.is_file():
            errors.append(f"{project}: 已跟踪的 {relative} 在工作区不存在")
            continue
        configs = parse_lock_configs(path.read_text(encoding="utf-8"))
        errors.extend(validate_lock_configs(project, configs, _read_project_build_script(root, project)))

    for relative in sorted(project_locks - expected):
        errors.append(
            f"{relative}: 锁文件对应的 project 不在 settings.gradle.kts 的 include 列表里"
            "（未入库模块的锁不能单独提交，R3 §6 P5）"
        )

    if errors:
        return errors, ""
    return [], f"dependency locking 已落地：{len(project_locks)} 个 included project 都有覆盖测试/运行面的锁文件"


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    errors, notice = check(root)
    if errors:
        print("依赖锁文件门失败：", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        print(
            "修复：在干净、待提交的树上运行 "
            "./gradlew --no-configuration-cache --no-parallel lockAllDependencies --write-locks，"
            "审 diff 后把锁文件与源码一起提交。",
            file=sys.stderr,
        )
        return 1
    print(f"OK: {notice}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
