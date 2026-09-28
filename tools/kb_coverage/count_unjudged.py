# -*- coding: utf-8 -*-
"""三档键数：已判定 / 机械SKIP / 待判定（封包轮工作量口径）。

## 它消灭的失败

封包轮开工前需要**一个确定的口径**回答"还剩多少块要送模型判定"——
此前各工具各算各的（171,461 / 79.8k~98.1k 等数字混用，对不上账）。
本工具与 `make_judgment_slices` 共用 `prescreen_chunks.iter_classified`，
保证"待判定"数字 == 切片工具切出的块数，收口时可直接对账。

口径：
  已判定 = 判定表键 − 重判队列键（重判队列键判定作废，按未判定处理）
  机械SKIP = 未判定键中 prescreen 判据机械可判的部分
  待判定 = 未判定键中需模型判定的部分（= make_judgment_slices 切片块数）

用法：PYTHONPATH=tools python -m kb_coverage.count_unjudged
"""

from __future__ import annotations

import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_coverage import prescreen_chunks as pc  # noqa: E402


def counts() -> tuple[int, int, int]:
    """返回 (已判定, 机械SKIP, 待判定)。块池/判定表/重判队列路径用 prescreen 常量。"""
    judged = pc.load_judged_keys()
    rejudge = pc.load_rejudge_keys()
    effective_judged = judged - rejudge
    skip = judge = 0
    for _rec, verdict, _why in pc.iter_classified(pc.CHUNKS, effective_judged):
        if verdict == pc.JUDGED:
            continue
        if verdict == "JUDGE":
            judge += 1
        else:
            skip += 1
    return len(effective_judged), skip, judge


def main(argv: list[str] | None = None) -> int:
    judged, skip, judge = counts()
    print(f"已判定 {judged} / 机械SKIP {skip} / 待判定 {judge}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
