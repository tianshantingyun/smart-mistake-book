#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""证据哈希清单（批次 44 / D0 item 14）。

## 它消灭的具体失败

验收证据只写“见 build/xxx”，没有哈希，无法证明终裁时读到的是哪一份字节；
文件被覆盖后旧结论仍被引用。本工具：

- 递归收集目录下所有普通文件，输出 `<sha256>  <repo-relative-posix-path>`；
- `--verify` 重算并与清单逐行比对：缺文件、哈希不符、清单外多文件都 exit 1；
- `--self-hash` 输出清单自身的 sha256，供终裁 JSON 绑定；
- 不读取/输出文件内容，只输出路径与哈希，避免把证据正文里的敏感信息带进清单。

## 用法

    python -I tools/ci/acceptance_hash_manifest.py \
        --root docs/research/evidence/2026-11-05-batch-44 \
        --out build/acceptance/evidence-sha256.txt --self-hash

    python -I tools/ci/acceptance_hash_manifest.py \
        --root docs/research/evidence/2026-11-05-batch-44 \
        --verify build/acceptance/evidence-sha256.txt

退出码：0 一致；1 不一致/缺失/多余；2 参数或路径错误。
"""

from __future__ import annotations

import argparse
import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _relative(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.resolve().as_posix()


def collect(root: Path, exclude: Path | None = None) -> list[Path]:
    files = [path for path in root.rglob("*") if path.is_file()]
    if exclude is not None:
        excluded = exclude.resolve()
        files = [path for path in files if path.resolve() != excluded]
    return sorted(files, key=lambda path: _relative(path, root))


def render(root: Path, files: list[Path]) -> str:
    return "".join(f"{_sha256(path)}  {_relative(path, root)}\n" for path in files)


def verify(root: Path, manifest: Path) -> list[dict]:
    issues: list[dict] = []
    if not manifest.exists():
        return [{"code": "MANIFEST_MISSING", "detail": manifest.as_posix()}]
    expected: dict[str, str] = {}
    for line_no, raw in enumerate(manifest.read_text(encoding="utf-8").splitlines(), start=1):
        if not raw.strip():
            continue
        parts = raw.split("  ", 1)
        if len(parts) != 2 or len(parts[0]) != 64:
            issues.append({"code": "MANIFEST_LINE_MALFORMED",
                           "detail": f"line {line_no}: {raw[:120]}"})
            continue
        expected[parts[1].strip()] = parts[0].lower()
    actual = {_relative(path, root): _sha256(path) for path in collect(root, exclude=manifest)}
    for rel, digest in actual.items():
        if rel not in expected:
            issues.append({"code": "UNLISTED_FILE", "detail": rel})
        elif expected[rel] != digest:
            issues.append({"code": "HASH_MISMATCH", "detail": rel})
    for rel in expected:
        if rel not in actual:
            issues.append({"code": "MISSING_FILE", "detail": rel})
    return issues


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="证据哈希清单生成/校验")
    parser.add_argument("--root", required=True)
    parser.add_argument("--out")
    parser.add_argument("--verify")
    parser.add_argument("--self-hash", action="store_true")
    args = parser.parse_args(argv)

    root = Path(args.root)
    if not root.is_absolute():
        root = ROOT / root
    if not root.is_dir():
        print(f"ROOT_NOT_DIR: {root}", file=sys.stderr)
        return 2

    if args.verify:
        manifest = Path(args.verify)
        if not manifest.is_absolute():
            manifest = ROOT / manifest
        issues = verify(root, manifest)
        for issue in issues:
            print(f"{issue['code']}: {issue['detail']}")
        if issues:
            return 1
        print(f"OK {manifest.as_posix()}")
        return 0

    out = Path(args.out) if args.out else None
    if out and not out.is_absolute():
        out = ROOT / out
    text = render(root, collect(root, exclude=out))
    if out:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding="utf-8", newline="\n")
    else:
        sys.stdout.write(text)
    if args.self_hash:
        if not out:
            print("SELF_HASH_REQUIRES_OUT", file=sys.stderr)
            return 2
        print(hashlib.sha256(out.read_bytes()).hexdigest())
    return 0


if __name__ == "__main__":
    sys.exit(main())
