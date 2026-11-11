#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""发布算号器：``N = 100000 + git rev-list --count HEAD``。

存在理由（消灭的具体失败）：versionCode 若靠人手填，会漏升或算错；若在 Gradle 配置期读 git，
干净树（无 ``.git``）会失败、CI shallow clone 会把提交数算成 1（版本号倒退，Play 拒收）。
把 git 只放在发布脚本这一层，并显式拒绝 shallow 仓库，失败点前移到第 1 条命令。

判据：
- 仓库不是 shallow（``git rev-parse --is-shallow-repository`` == ``false``）；
- ``git rev-list --count HEAD`` 是正整数；
- ``N = 100000 + count`` 落在 Play 允许的 ``1..2100000000``；
- 任一条不满足 → exit 1，不输出可用号码。
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

BASE = 100_000
MAX_CODE = 2_100_000_000


def git(root: Path, *args: str) -> tuple[str | None, str | None]:
    """Return ``(stdout, error)``; never raises for a missing repo."""
    try:
        completed = subprocess.run(["git", *args], cwd=str(root), capture_output=True, text=True)
    except OSError as error:
        return None, f"{type(error).__name__}: {error}"
    if completed.returncode != 0:
        return None, (completed.stderr or completed.stdout).strip() or f"exit {completed.returncode}"
    return completed.stdout.strip(), None


def derive(root: Path) -> dict:
    payload: dict = {
        "root": root.as_posix(),
        "base": BASE,
        "commit_count": None,
        "shallow": None,
        "version_code": None,
        "formula": "version_code = 100000 + commit_count",
        "errors": [],
    }
    errors: list[str] = payload["errors"]
    shallow, error = git(root, "rev-parse", "--is-shallow-repository")
    if error:
        errors.append(f"git rev-parse --is-shallow-repository failed: {error}")
    else:
        payload["shallow"] = shallow == "true"
        if payload["shallow"]:
            errors.append("shallow clone: commit count would be wrong; fetch full history first")
    count_raw, error = git(root, "rev-list", "--count", "HEAD")
    if error:
        errors.append(f"git rev-list --count HEAD failed: {error}")
        return payload
    try:
        count = int(count_raw)
    except (TypeError, ValueError):
        errors.append(f"commit count is not an integer: {count_raw!r}")
        return payload
    payload["commit_count"] = count
    code = BASE + count
    if not (1 <= code <= MAX_CODE):
        errors.append(f"derived versionCode out of Play range 1..{MAX_CODE}: {code}")
    elif payload["shallow"] is False:
        payload["version_code"] = code
    return payload


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="由 git 提交数推导 release versionCode")
    parser.add_argument("--root", default=".")
    parser.add_argument("--out")
    args = parser.parse_args(argv)
    payload = derive(Path(args.root))
    if args.out:
        Path(args.out).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0 if not payload["errors"] else 1


if __name__ == "__main__":
    sys.exit(main())
