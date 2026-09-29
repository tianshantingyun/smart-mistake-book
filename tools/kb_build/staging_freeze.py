# -*- coding: utf-8 -*-
"""staging 冻结对拍：写入窗口始末对 staging 全量文件做哈希清单，检出并发写入。

## 它消灭的失败

`build/kb-staging/` 被 `.gitignore` 排除（`**/build/`）——`git status` 对"另一个会话
是不是正在写 staging"**零可见性**（两会话可以同时写同一个 pack 而 git 一声不响）。
本仓库历史上有过实伤（`apply_unbound_bindings` 分批重写把 890 行抹成 2 行、
`build_alias_table` 用过期快照覆盖成品别名），根因都是"写前不知道别人在写"。
写入窗口的唯一可行检出手段就是对拍：窗口开始前记全部文件的 sha256，
窗口结束后再算一遍——**出现本任务预期之外的增/删/改，就是并发写入，停下来升级**。

## 用法

    python tools/kb_build/staging_freeze.py --snapshot  # 记当前 staging 指纹
    python tools/kb_build/staging_freeze.py --compare   # 与上次指纹对拍（变了 exit 1）

指纹默认写 `build/kb-staging/.staging-freeze.json`（staging 内，不随 git 走）。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import pack_io  # noqa: E402

FREEZE_NAME = ".staging-freeze.json"


def _digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def scan(work_dir: Path) -> dict[str, str]:
    """staging 内全部 JSON 的 {文件名: sha256}（指纹文件本身除外）。"""
    out: dict[str, str] = {}
    for path in sorted(work_dir.glob("*.json")):
        if path.name == FREEZE_NAME:
            continue
        out[path.name] = _digest(path)
    return out


def compare(old: dict[str, str], new: dict[str, str]) -> dict[str, list[str]]:
    added = sorted(set(new) - set(old))
    removed = sorted(set(old) - set(new))
    changed = sorted(name for name in set(old) & set(new) if old[name] != new[name])
    return {"added": added, "removed": removed, "changed": changed}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    group = ap.add_mutually_exclusive_group(required=True)
    group.add_argument("--snapshot", action="store_true", help="记当前 staging 指纹")
    group.add_argument("--compare", action="store_true", help="与上次指纹对拍")
    args = ap.parse_args(argv)
    work_dir = pack_io.work_dir()
    freeze = work_dir / FREEZE_NAME
    current = scan(work_dir)
    if args.snapshot:
        freeze.write_text(json.dumps(current, ensure_ascii=False, indent=1, sort_keys=True) + "\n",
                          encoding="utf-8")
        print(f"已记 {len(current)} 个文件的指纹 → {freeze.name}")
        return 0
    if not freeze.exists():
        print("没有指纹文件：先跑 --snapshot")
        return 2
    old = json.loads(freeze.read_text(encoding="utf-8"))
    diff = compare(old, current)
    if not any(diff.values()):
        print(f"staging 未变（{len(current)} 个文件）——写入窗口干净")
        return 0
    print("staging 有变动：")
    for kind, names in diff.items():
        if names:
            print(f"  {kind}: {', '.join(names)}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
