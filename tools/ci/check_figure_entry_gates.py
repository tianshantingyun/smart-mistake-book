#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生图入口静态门（A-13 grep 门 + A-08 入口侧显式 basis 静态前哨）。

它消灭的具体失败：

1. 第 4 个 `FigureGenerationRequest(` 构造点悄悄出现——A-13 的判据是"全入口同管线"：
   三个构造点（净化 `ConfiguredCleanImageGenerator` / attachedImages 再生成
   `AttachedImageGenerator` / 讲题 `TutorFigureGenerator`）必须全部经同一
   `FigureGenerationCoordinator` 端口与同一 policy 校验，**无入口私有方法路由**。
   没有这道门时，"恰好 3 处"只活在准入清单的附录注释里，第 4 个入口（自带私有
   method 路由分支的那种）落码后没人拦。
2. 白名单内的构造点消失或同一文件出现第二处——"恰 3 处"是**出现次数**判据，
   不是"文件数 ≤ 3"的宽松口径；少一处说明入口被改走了别的路径，多一处同样破坏
   全入口同管线的静态形状。
3. 入口不再显式传 basis 指纹（A-08 入口侧约束的静态前哨）——`FigureGenerationPolicy`
   的 `BASIS_FINGERPRINT_MISMATCH` 复核只对"显式传了 basis"的请求生效；某个入口
   把 `basisFingerprint = figure...` 这一行删掉后，policy 门对它静默失效，审计台账
   退回"coordinator 自行推导"而没人知道。三个入口各自必须保留显式传值一行。

语义（对齐 `check_file_size_gate.py` 的判据原文口径）：

- 门 1：对 ``core/data/src/main`` 全量扫描字节级 ``FigureGenerationRequest(``，
  命中集合必须与硬编码白名单**精确相等**（每文件恰 1 处、总数恰 3）；
  白名单外出现、白名单内缺失、或同一文件出现第 2 处，任一即 exit 1。
- 门 2：白名单三文件各自必须含 ``basisFingerprint = figure`` 显式传值；缺失即 exit 1。
- 不运行 Gradle、不联网；只读 ``core/data/src/main`` 下的文件字节。
- 退出码 0 = 通过，1 = 缺口（差异逐条打印，附命中清单）。

判据出处：准入清单 A-13 行判据 ①（``grep -rn "FigureGenerationRequest(" core/data/src/main``
→ 恰 3 处，出现第 4 处即失败）与 A-08 行入口侧约束（宿主显式传 basis 时以 program
摘要复核）；脚本化设计见 ``docs/research/2026-10-30-capability-driven-tools.md`` §4.3。
自测：``tools/tests/test_check_figure_entry_gates.py``。
"""

from __future__ import annotations

import argparse
import sys
from collections import Counter
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

SCAN_DIR = Path("core/data/src/main")
REQUEST_PATTERN = b"FigureGenerationRequest("
EXPLICIT_BASIS_PATTERN = "basisFingerprint = figure"

# A-13 grep 门基线（准入清单附录 :381 逐字一致；2026-10-30 复跑核对恰 3 处）。
ENTRY_WHITELIST = (
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/"
    "ConfiguredCleanImageGenerator.kt",
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/"
    "AttachedImageGenerator.kt",
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/"
    "TutorFigureGenerator.kt",
)


def scan_request_constructions(root: Path) -> list[tuple[str, int]]:
    """Byte-level ``file:line`` hits for ``FigureGenerationRequest(`` under scan dir.

    Byte-level on purpose: the checklist criterion is a bare ``grep -rn`` with no
    ``--include`` filter, so a 4th occurrence in any file kind must fail the gate.
    """

    hits: list[tuple[str, int]] = []
    base = root / SCAN_DIR
    if not base.is_dir():
        return hits
    for path in sorted(base.rglob("*")):
        if not path.is_file():
            continue
        data = path.read_bytes()
        start = 0
        while True:
            index = data.find(REQUEST_PATTERN, start)
            if index < 0:
                break
            line = data.count(b"\n", 0, index) + 1
            hits.append((path.relative_to(root).as_posix(), line))
            start = index + 1
    return hits


def check(root: Path) -> tuple[list[str], list[str]]:
    """Run both gates; return ``(errors, report_lines)``. Empty errors = pass."""

    hits = scan_request_constructions(root)
    per_file = Counter(relpath for relpath, _ in hits)
    report: list[str] = []
    errors: list[str] = []

    report.append(f"门1 FigureGenerationRequest( 构造点：共 {len(hits)} 处（判据：恰 3 处）")
    for relpath, line in hits:
        report.append(f"  命中 {relpath}:{line}")

    for expected in ENTRY_WHITELIST:
        count = per_file.get(expected, 0)
        if count == 0:
            errors.append(f"门1 白名单构造点缺失（入口被改走别的路径？）: {expected}")
        elif count > 1:
            errors.append(f"门1 同一文件出现第 {count} 处构造（恰 3 处判据被破坏）: {expected}")

    for relpath in sorted(per_file):
        if relpath not in ENTRY_WHITELIST:
            locations = [f"{relpath}:{line}" for file, line in hits if file == relpath]
            errors.append(
                "门1 白名单外的第 4 处构造点（必须改走统一 coordinator 管线，"
                f"而非新增私有构造）: {', '.join(locations)}"
            )

    for expected in ENTRY_WHITELIST:
        path = root / expected
        try:
            text = path.read_text(encoding="utf-8")
        except OSError as failure:
            errors.append(f"门2 无法读取入口文件（显式 basis 前哨失效）: {expected}（{failure}）")
            continue
        if EXPLICIT_BASIS_PATTERN not in text:
            errors.append(
                "门2 入口不再显式传 basis 指纹（A-08 policy 复核对它静默失效）: "
                f"{expected} 缺 `{EXPLICIT_BASIS_PATTERN}`"
            )

    if errors:
        report.append("FAIL")
    else:
        report.append("PASS")
    return errors, report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--root",
        default=str(REPO),
        help="repository root to scan (default: the repo this script lives in)",
    )
    args = parser.parse_args(argv)

    errors, report = check(Path(args.root))
    for line in report:
        print(line)
    if errors:
        for error in errors:
            print(f"  缺口 {error}")
        print("\n生图入口静态门 FAILED：见上方逐条缺口（A-13 恰 3 处 / A-08 显式 basis 前哨）。")
        return 1
    print("\n生图入口静态门 PASS：构造点恰 3 处且三入口均显式传 basis。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
