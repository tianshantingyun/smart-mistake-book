#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""跑批次 49 标定实验并落盘证据（测试只打印带标记的 stdout，不写文件）。

用法：
    python -I tools/ci/run_batch49_calibration.py

输出固定到 docs/research/evidence/2026-11-08-batch-49/calibration/（不接受任意路径）。
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "research" / "evidence" / "2026-11-08-batch-49" / "calibration"
MARK = "[batch49-artifact] "


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    env["BATCH49_CALIBRATION"] = "run"
    proc = subprocess.run(
        [
            str(ROOT / "gradlew.bat") if os.name == "nt" else str(ROOT / "gradlew"),
            "--console=plain",
            ":core:domain:test",
            "--tests",
            "*ReviewCalibrationExperimentTest*",
        ],
        cwd=ROOT,
        env=env,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    text = proc.stdout + "\n" + proc.stderr
    current: str | None = None
    buf: list[str] = []
    written: list[str] = []

    def flush() -> None:
        nonlocal current, buf
        if current and buf:
            target = (OUT / current).resolve()
            if not target.is_relative_to(OUT.resolve()):
                raise SystemExit(f"artifact escaped output root: {target}")
            target.write_text("\n".join(buf).rstrip() + "\n", encoding="utf-8")
            written.append(current)
        current, buf = None, []

    for line in text.splitlines():
        if line.startswith(MARK):
            flush()
            current = line[len(MARK):].strip()
            continue
        if current is not None:
            # Gradle 会在任务输出前后插入自己的行；只收标记后的连续内容，
            # 遇到 Gradle 任务行即结束当前产物。
            if line.startswith("> Task") or line.startswith("BUILD "):
                flush()
            else:
                buf.append(line)
    flush()

    print(f"gradle_exit={proc.returncode} artifacts={len(written)}")
    for name in written:
        print(f"  {name}")
    if proc.returncode != 0:
        print(text[-4000:])
    return proc.returncode


if __name__ == "__main__":
    sys.exit(main())
