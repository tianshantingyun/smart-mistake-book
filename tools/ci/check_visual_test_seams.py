#!/usr/bin/env python3
"""VisualChainTestSeams 测试缝引用门（批次 39 D-04/V-01④；批次 38 T10 §2.2 配套）。

`VisualChainTestSeams`（core:data `visual` 包）是 V-01 ④ 腿的公开测试缝：它把 internal 的
restore 判定与 PDF 写侧以 opaque 公共操作暴露给 app androidTest。缝的合规性依赖两条引用
纪律，本脚本把两条都变成可复跑的机器门（`VisualChainTestSeams` KDoc"三条硬约束"第 1 条）：

1. **src/main 与 src/test 零引用**——生产旁路直绘 = restore 门被绕过（冷恢复判定序、
   解析复检、PDF 完整性 D1∧D2∧D3 全部被跳过）。缝的声明文件本身不计（排除其路径）。
2. **androidTest 至少 1 处引用**——只回滚用例不回滚缝时，缝成孤儿（主包 R8 收缩 + 死
   公开面）。门红即提醒两单元一起处理（批次 38 T10 §3 回滚表"中间态"行）。

R-04 交互（KDoc 硬约束第 2 条）：缝与其 internal 转调家族在 release 主包可被 R8 收缩；
引用本缝的用例在 `app/r04-test-keep.pro` 登记 keep 之前不得进 R-04 release 轨用例清单。
该登记义务归 R-04 批，本门只管引用纪律。

运行（仓库根，可复跑）：

    python tools/ci/check_visual_test_seams.py
    echo $?    # 0=两条纪律都成立；1=有违反

负控旋钮（验证门本身会红——位置参数替换扫描根，仿 check_r04_keep_rules）：

    python tools/ci/check_visual_test_seams.py path/to/any/dir
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# 缝的唯一定义点（KDoc 声明的路径；改路径必须同步本常量与 KDoc）。
SEAM_NAME = "VisualChainTestSeams"
DECLARATION_RELPATH = (
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/"
    "VisualChainTestSeams.kt"
)
# 扫描所有模块的 Kotlin 源（src/main、src/test、src/androidTest；testFixtures 不存在，
# 出现时按 main 同规则对待——目录名以 src/ 下的源集名判定）。
SOURCE_ROOTS = ("core", "app")


def kotlin_sources(root: Path) -> list[Path]:
    if not root.exists():
        return []
    return [p for p in root.rglob("*.kt") if p.is_file()]


def source_set_of(path: Path) -> str:
    # .../src/<sourceSet>/kotlin/... → <sourceSet>；非标准布局按 main 处理（保守）。
    for part in path.parts:
        if part in ("main", "test", "androidTest", "testFixtures"):
            return part
    return "main"


def strip_kotlin_comments(text: str) -> str:
    """剥掉 Kotlin 注释（`//` 行注释与 `/* */` 块注释，**含嵌套**——Kotlin 块注释可嵌套）。

    KDoc 里按名提及缝（如 keep 义务的交叉引用）是文档不是代码引用，不判违规；门管的是
    可执行引用。字符串字面量里的提及按引用计（保守方向：宁可误报让人看一眼，不漏报）。
    """
    out: list[str] = []
    index = 0
    length = len(text)
    depth = 0
    while index < length:
        char = text[index]
        nxt = text[index + 1] if index + 1 < length else ""
        if depth > 0:
            if char == "/" and nxt == "*":
                depth += 1
                index += 2
            elif char == "*" and nxt == "/":
                depth -= 1
                index += 2
            else:
                if char == "\n":
                    out.append("\n")  # 保留行号结构，便于违规定位。
                index += 1
            continue
        if char == "/" and nxt == "/":
            while index < length and text[index] != "\n":
                index += 1
            continue
        if char == "/" and nxt == "*":
            depth = 1
            index += 2
            continue
        out.append(char)
        index += 1
    return "".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("roots", nargs="*", help="override scan roots (negative control)")
    args = parser.parse_args()
    roots = [Path(r) for r in args.roots] or [ROOT / name for name in SOURCE_ROOTS]

    declaration = ROOT / DECLARATION_RELPATH
    if not declaration.exists():
        print(f"FAIL: seam declaration missing: {DECLARATION_RELPATH}")
        return 1

    production_hits: list[str] = []
    android_test_hits: list[str] = []
    for source in kotlin_sources_for(roots):
        rel = source.relative_to(ROOT) if source.is_relative_to(ROOT) else source
        if str(rel).replace("\\", "/") == DECLARATION_RELPATH:
            continue  # 声明文件本身不是引用。
        try:
            text = source.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as error:
            print(f"FAIL: cannot read {rel}: {error}")
            return 1
        if SEAM_NAME not in strip_kotlin_comments(text):
            continue
        source_set = source_set_of(source)
        if source_set in ("main", "test", "testFixtures"):
            production_hits.append(f"{rel} ({source_set})")
        elif source_set == "androidTest":
            android_test_hits.append(str(rel))

    failures: list[str] = []
    if production_hits:
        failures.append(
            "the test seam must have zero src/main and src/test references "
            f"(production bypass would skip the restore gates): {production_hits}"
        )
    if not android_test_hits:
        failures.append(
            "the test seam is orphaned: no androidTest references it; roll the seam back "
            "together with its cases or land the referencing suite"
        )

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1
    print(
        f"OK: {SEAM_NAME} has 0 main/test references and "
        f"{len(android_test_hits)} androidTest reference(s)"
    )
    return 0


def kotlin_sources_for(roots: list[Path]) -> list[Path]:
    sources: list[Path] = []
    for root in roots:
        sources.extend(kotlin_sources(root))
    return sources


if __name__ == "__main__":
    sys.exit(main())
