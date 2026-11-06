#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""p09 三次设备跑的机械复算：kill→warm-up 重建延迟（不读叙述）。

消 灭 的 失 败 ：三重逻辑里“此次绿是否落在失败区”靠人工对时间戳，容易抄错。
本脚本按规则 `killing :visual_js worker` → 其后第一条 `visual_js warm-up finished`
的时间戳差分重算每次重建延迟，并与 1300 ms 构造窗口比较。

用法: python -I p09_recompute.py
"""

from __future__ import annotations

import datetime as dt
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
WINDOW_MS = 1300  # FIRST_FRAME_HEAD_START 400 + 3 * FRAME_SPACING 300
LINE = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d\.\d{3})\s+(.*)$")
KILL = "killing :visual_js worker"
WARM = "visual_js warm-up finished"


def parse_time(token: str) -> dt.datetime:
    return dt.datetime.strptime("2026-" + token, "%Y-%m-%d %H:%M:%S.%f")


def recompute(path: Path) -> list[float]:
    kills: list[dt.datetime] = []
    warms: list[dt.datetime] = []
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        m = LINE.match(raw.strip())
        if not m:
            continue
        when, msg = m.groups()
        if KILL in msg:
            kills.append(parse_time(when))
        elif WARM in msg:
            warms.append(parse_time(when))
    deltas: list[float] = []
    for kill in kills:
        later = [w for w in warms if w > kill]
        if later:
            deltas.append((later[0] - kill).total_seconds() * 1000.0)
    return deltas


def log_dir() -> Path:
    """原始 logcat 在 docs/research/evidence/2026-11-06-batch-45/ws7/；本地优先。"""
    local = HERE / "p09-run1-logcat.txt"
    if local.exists():
        return HERE
    return HERE.parents[3] / "research" / "evidence" / "2026-11-06-batch-45" / "ws7"


def main() -> int:
    rc = 0
    base = log_dir()
    session_dir = HERE.parents[3] / "research" / "evidence" / "2026-11-06-batch-45" / "e-device"
    if not (session_dir / "verify-p09-run1-logcat.txt").exists():
        session_dir = HERE
    print(f"LOGDIR={base}")
    print(f"SESSIONDIR={session_dir}")
    groups = [
        ("ws7", [(f"run{i}", base / f"p09-run{i}-logcat.txt") for i in (1, 2, 3)]),
        ("session", [(f"verify-run{i}", session_dir / f"verify-p09-run{i}-logcat.txt") for i in (1, 2, 3)]),
    ]
    for group, items in groups:
        for label, path in items:
            if not path.exists():
                print(f"{group}/{label}: SKIP (missing {path.name})")
                continue
            deltas = recompute(path)
            print(f"{group}/{label}: kills->rebuild deltas(ms)={[round(d) for d in deltas]}")
            if not deltas:
                print(f"{group}/{label}: NO DELTA (no kill->warm-up pair in log)")
                continue
            slow = [d for d in deltas if d > WINDOW_MS]
            print(f"{group}/{label}: n={len(deltas)} slow_region={len(slow)} "
                  f"fast_region={len(deltas)-len(slow)} first={round(deltas[0])} window={WINDOW_MS}")
    print("P09_RECOMPUTE=OK" if rc == 0 else "P09_RECOMPUTE=FAIL")
    return rc


if __name__ == "__main__":
    sys.exit(main())
