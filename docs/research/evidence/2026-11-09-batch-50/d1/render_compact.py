# -*- coding: utf-8 -*-
"""把 judge_input/batch-*.json 渲染成紧凑纯文本（判定时读这个，省 token）。

用法：python -I -X utf8 render_compact.py
产物：judge_input/compact-NN.txt
"""

from __future__ import annotations

import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
JUDGE = HERE / "judge_input"


def one_line(s: str) -> str:
    return " / ".join(part.strip() for part in str(s).splitlines() if part.strip())


def render(path: Path) -> str:
    items = json.loads(path.read_text(encoding="utf-8"))
    out: list[str] = []
    for it in items:
        cur = it["current_node"]
        out.append(f"[{it['i']}] {it['material_slug']} | {it['subject']} | {it['type']}")
        out.append(f"T: {one_line(it['title'])}")
        out.append(f"X: {one_line(it['text'])}")
        out.append(f"N: {cur['name']} | {cur['kind']} | topic={cur['topic_name']} | {one_line(cur['boundary'])}")
        out.append("")
    return "\n".join(out)


def main() -> int:
    for path in sorted(JUDGE.glob("batch-*.json")):
        target = path.with_name(path.stem.replace("batch-", "compact-") + ".txt")
        target.write_text(render(path), encoding="utf-8")
        print(f"{path.name} -> {target.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
