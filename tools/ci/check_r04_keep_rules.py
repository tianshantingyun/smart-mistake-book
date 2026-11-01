#!/usr/bin/env python3
"""R-04 keep-rules 门：app 模块的 proguard 规则文件不得关闭混淆（批次 35 T3）。

`app/r04-test-keep.pro` 头部注释自批次 34 起声称："The R-04 gate greps the proguard
rule files for that directive and requires 0 hits in each"——但该门此前**不存在于仓内**
（`grep -rn "dontobfuscate" tools/ --include=*.py --include=*.ps1 --include=*.sh` = 0 命中，
见 `docs/research/2026-10-30-batch-35-baseline.md` §三.6"幽灵门"），只以一次性手工 grep
活在批次 34 证据（`00-index.md` §R-04）里。本脚本把声称变成可复跑的机器门。

它消灭的具体失败：有人往 app 模块任一 proguard 规则文件加入 `-dontobfuscate`——R8 停止
改名，而 R-04 轨道要验证的场景（release 构建确实发生 R8 重命名）被静默取消。K1-K6 往返
证据全部建立在"混淆开着"这个前提上；没有门时，前提的破坏在构建日志里没有任何独立信号。

口径（与 .pro 声称逐字对齐，只查这一条指令，不扩大范围）：

- 只匹配 `-dontobfuscate`：大小写不敏感、词边界（`-dontobfuscateall` 这类词中前缀不算）。
  `-dontshrink`/`-dontoptimize` 不在本门范围——R-04 场景取决于**重命名**（obfuscation），
  不是收缩/优化（`proguard-android-optimize.txt` 本来就开优化）。
- `#` 到行尾是注释（ProGuard 规则文件语法）。只在**规则文本**里找指令：注释里提到指令名
  （r04-test-keep.pro 头部的自述、本文件）不算命中，因为 R8 不执行注释；`#-dontobfuscate`
  这种被注释掉的规则同样不是活规则。
- 默认检查 `app/build.gradle.kts` 实际接线的两个文件（release `proguardFiles` :77/:85/:94、
  release androidTest `testProguardFiles` :98）；默认目标文件缺失本身判 FAIL（接线坏了）。

运行（仓库根，可复跑）：

    python tools/ci/check_r04_keep_rules.py
    echo $?    # 0=每个文件 0 命中；1=有命中或文件缺失

负控旋钮（验证门本身会红——位置参数替换默认目标，仿 check_dependency_pins 的
`--expect-kotlin-runtime`）：

    python tools/ci/check_r04_keep_rules.py path/to/whatever.pro
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# 与 app/build.gradle.kts 的实际接线一致（批次 34 R-04 配置，1c3f4ed8）。
DEFAULT_RULE_FILES = ("app/proguard-rules.pro", "app/r04-test-keep.pro")

# `#` 到行尾是注释；指令只在规则文本里生效。
COMMENT_MARK = "#"
# 大小写不敏感 + 词边界：抓 `-dontobfuscate`/`-DontObfuscate`，不抓词中前缀。
DIRECTIVE = re.compile(r"(?i)(?:^|\s)-?dontobfuscate\b")


def violating_lines(text: str) -> list[tuple[int, str]]:
    """返回含 `-dontobfuscate` 的**规则行**（1-based 行号 + 去注释后的规则文本）。"""
    violations: list[tuple[int, str]] = []
    for number, line in enumerate(text.splitlines(), start=1):
        rule_part = line.split(COMMENT_MARK, 1)[0]
        if DIRECTIVE.search(rule_part):
            violations.append((number, rule_part.strip()))
    return violations


def scan_file(path: Path) -> list[tuple[int, str]]:
    if not path.is_file():
        raise FileNotFoundError(f"proguard 规则文件不存在：{path}")
    return violating_lines(path.read_text(encoding="utf-8-sig"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="R-04 gate: -dontobfuscate must have 0 hits in each proguard rule file")
    parser.add_argument(
        "files", nargs="*", type=Path,
        help="rule files to check (default: the two files wired in app/build.gradle.kts; "
             "override = negative-control knob for testing this gate itself)")
    args = parser.parse_args(argv)

    targets = list(args.files) or [ROOT / rel for rel in DEFAULT_RULE_FILES]

    failed = False
    for target in targets:
        try:
            violations = scan_file(target)
        except (OSError, UnicodeDecodeError) as failure:
            print(f"FAIL: {target}: {failure}", file=sys.stderr)
            failed = True
            continue
        if violations:
            for number, rule in violations:
                print(f"FAIL: {target}:{number}: -dontobfuscate in rule line: {rule}",
                      file=sys.stderr)
            failed = True
        else:
            print(f"OK: {target}: 0 hits (rule lines contain no -dontobfuscate)")

    if failed:
        print("R-04 keep-rules gate FAILED: an obfuscation-disabling directive is present in "
              "a proguard rule file — the R8-renaming scenario R-04 verifies is cancelled; "
              "K1-K6 roundtrip evidence must not be trusted until it is removed",
              file=sys.stderr)
        return 1
    print(f"OK: -dontobfuscate 0 hits in each of {len(targets)} proguard rule file(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
