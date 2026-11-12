#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生图入口静态门，核对构造点与每个请求体的显式指纹来源。

当前五个入口文件共六个构造点。讲题入口分别构造 JS 和图像请求，详解与修改题目
入口通过 lineageFingerprint 绑定模型任务、完整输出及 blockId。每个构造点必须使用
该入口认可的指纹 helper，同文件中另一请求的 helper 无法替代。

构造扫描仍按 core/data/src/main 下所有文件的字节出现次数执行。白名单外构造、
缺失或额外构造、请求体缺少指纹或 helper 错配均返回 exit 1。该门只检查静态形状，
运行期 coordinator/policy 行为由对应业务测试验证。
"""

from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

SCAN_DIR = Path("core/data/src/main")
REQUEST_PATTERN = b"FigureGenerationRequest("
# Each tuple identifies one request's top-level argument and fingerprint helper.
ENTRY_FINGERPRINTS = {
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/"
    "ConfiguredCleanImageGenerator.kt": (("basisFingerprint", "figureBasisFingerprint"),),
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/"
    "AttachedImageGenerator.kt": (("basisFingerprint", "figureBasisFingerprint"),),
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/"
    "TutorFigureGenerator.kt": (
        ("basisFingerprint", "figureJsBasisFingerprint"),
        ("basisFingerprint", "figureBasisFingerprint"),
    ),
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/export/"
    "RoomDetailedSolutionRepository.kt": (("lineageFingerprint", "detailedSolutionFigureBasis"),),
    "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/"
    "RoomMistakeEditRepository.kt": (("lineageFingerprint", "mistakeEditFigureBasis"),),
}
ENTRY_WHITELIST = tuple(ENTRY_FINGERPRINTS)
EXPECTED_CONSTRUCTIONS = sum(len(entries) for entries in ENTRY_FINGERPRINTS.values())

KOTLIN_NON_CODE = re.compile(
    r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
)
FINGERPRINT_CALL = re.compile(
    r"^\s*(basisFingerprint|lineageFingerprint)\s*=\s*"
    r"(?:[A-Za-z_]\w*\.)*([A-Za-z_]\w*)\s*\("
)


def request_bodies(text: str) -> list[str]:
    """Extract balanced constructor bodies while ignoring comments and literals."""
    code = KOTLIN_NON_CODE.sub(lambda match: " " * len(match.group()), text)
    bodies = []
    for match in re.finditer(r"FigureGenerationRequest\(", code):
        depth = 1
        for index in range(match.end(), len(code)):
            if code[index] == "(":
                depth += 1
            elif code[index] == ")":
                depth -= 1
                if depth == 0:
                    bodies.append(code[match.end():index])
                    break
        else:
            raise ValueError("FigureGenerationRequest 构造括号未闭合")
    return bodies


def fingerprint_calls(body: str) -> list[tuple[str, str]]:
    """Read helper calls assigned directly to top-level fingerprint arguments."""
    arguments = []
    start = 0
    depth = 0
    for index, char in enumerate(body):
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif char == "," and depth == 0:
            arguments.append(body[start:index])
            start = index + 1
    arguments.append(body[start:])
    calls = []
    for argument in arguments:
        match = FINGERPRINT_CALL.match(argument)
        if match:
            calls.append(match.groups())
    return calls


def scan_request_constructions(root: Path) -> list[tuple[str, int]]:
    """Byte-level ``file:line`` hits for ``FigureGenerationRequest(`` under scan dir.

    Byte-level on purpose: the checklist criterion is a bare ``grep -rn`` with no
    ``--include`` filter, so an unexpected occurrence in any file kind must fail the gate.
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

    report.append(f"门1 FigureGenerationRequest( 构造点：共 {len(hits)} 处（判据：恰 {EXPECTED_CONSTRUCTIONS} 处）")
    for relpath, line in hits:
        report.append(f"  命中 {relpath}:{line}")

    for expected, signatures in ENTRY_FINGERPRINTS.items():
        count = per_file.get(expected, 0)
        expected_count = len(signatures)
        if count < expected_count:
            errors.append(
                f"门1 白名单构造点缺失（预期 {expected_count} 处，实际 {count} 处）: {expected}"
            )
        elif count > expected_count:
            errors.append(
                f"门1 同一文件出现第 {count} 处构造（预期 {expected_count} 处）: {expected}"
            )

    for relpath in sorted(per_file):
        if relpath not in ENTRY_FINGERPRINTS:
            locations = [f"{relpath}:{line}" for file, line in hits if file == relpath]
            errors.append(f"门1 白名单外的构造点: {', '.join(locations)}")

    for expected, signatures in ENTRY_FINGERPRINTS.items():
        try:
            text = (root / expected).read_text(encoding="utf-8")
            bodies = request_bodies(text)
        except (OSError, ValueError) as failure:
            errors.append(f"门2 无法解析入口请求体: {expected}（{failure}）")
            continue
        actual = []
        for number, body in enumerate(bodies, start=1):
            calls = fingerprint_calls(body)
            if len(calls) != 1:
                errors.append(
                    f"门2 第 {number} 处构造未显式传入唯一的 basis/lineage 指纹 helper: {expected}"
                )
            actual.extend(calls)
        if Counter(actual) != Counter(signatures):
            errors.append(
                f"门2 入口指纹 helper 错配: {expected}，预期 {signatures}，实际 {tuple(actual)}"
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
        print("\n生图入口静态门 FAILED：见上方构造点与请求体指纹缺口。")
        return 1
    print(f"\n生图入口静态门 PASS：构造点恰 {EXPECTED_CONSTRUCTIONS} 处，逐请求指纹 helper 一致。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
