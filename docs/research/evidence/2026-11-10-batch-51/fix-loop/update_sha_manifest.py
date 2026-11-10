#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""按清单逐项重算并只更新变化的 sha256 行（不增删条目）。

修复环改了 mva-gate/README.md 与 gate/ 下 4 个机器可读产物；本脚本读取原
SHA256SUMS 的条目顺序，重算每个文件的 sha，若变化则替换该行，并复算全清单。
不会把未登记的新文件偷偷加入，也不会删除条目。

用法（任意 cwd）：python -I -X utf8 update_sha_manifest.py <清单路径> <基准目录>
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path


def sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("usage: update_sha_manifest.py <manifest> <base-dir>", file=sys.stderr)
        return 2
    manifest = Path(argv[1]).resolve()
    base = Path(argv[2]).resolve()
    lines = manifest.read_text(encoding="utf-8").splitlines()
    changed = []
    out = []
    for line in lines:
        if not line.strip():
            out.append(line)
            continue
        # 兼容 `hash  path` 与 `hash *./path` 两种 sha256sum 形态。
        declared, rest = line.split(" ", 1)
        rel_path = rest.lstrip(" *")
        path = base / rel_path
        if not path.is_file():
            print(f"missing: {path}", file=sys.stderr)
            return 1
        actual = sha(path)
        if actual != declared:
            changed.append(rel_path)
            line = f"{actual} {rest}"
        out.append(line)
    manifest.write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")

    bad = []
    for line in out:
        if not line.strip():
            continue
        declared, rest = line.split(" ", 1)
        if sha(base / rest.lstrip(" *")) != declared:
            bad.append(rest)
    print(f"entries={len([l for l in out if l.strip()])} changed={len(changed)}")
    for rel in changed:
        print(f"  updated {rel}")
    print(f"mismatches={len(bad)} {bad[:5]}")
    return 0 if not bad else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
