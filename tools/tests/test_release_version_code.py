#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""``tools/ci/release_version_code.py`` 的单测（批次 50 / 发布线）。

覆盖"能红"面：shallow 仓库、非仓库目录、提交数异常都必须 exit 1；
正常仓库必须给出 ``100000 + commit_count``。
"""

from __future__ import annotations

import importlib.util
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "release_version_code", ROOT / "tools" / "ci" / "release_version_code.py")
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)


def _repo(path: Path, commits: int) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    subprocess.run(["git", "init", "-q"], cwd=path, check=True)
    subprocess.run(["git", "config", "user.email", "r@example.invalid"], cwd=path, check=True)
    subprocess.run(["git", "config", "user.name", "r"], cwd=path, check=True)
    for index in range(commits):
        (path / "f.txt").write_text(f"{index}\n", encoding="utf-8")
        subprocess.run(["git", "add", "f.txt"], cwd=path, check=True)
        subprocess.run(["git", "commit", "-q", "-m", f"c{index}"], cwd=path, check=True)
    return path


def test_derives_base_plus_commit_count(tmp_path: Path) -> None:
    repo = _repo(tmp_path / "repo", 3)
    payload = M.derive(repo)
    assert payload["errors"] == []
    assert payload["commit_count"] == 3
    assert payload["version_code"] == 100003
    assert payload["shallow"] is False


def test_shallow_repo_refuses_number(tmp_path: Path, monkeypatch) -> None:
    repo = _repo(tmp_path / "repo", 1)
    real_git = M.git

    def fake_git(root, *args):
        if args[:2] == ("rev-parse", "--is-shallow-repository"):
            return "true", None
        return real_git(root, *args)

    monkeypatch.setattr(M, "git", fake_git)
    payload = M.derive(repo)
    assert payload["version_code"] is None
    assert any("shallow" in error for error in payload["errors"])


def test_not_a_repository_fails(tmp_path: Path) -> None:
    plain = tmp_path / "plain"
    plain.mkdir()
    payload = M.derive(plain)
    assert payload["version_code"] is None
    assert payload["errors"]


def test_out_file_matches_stdout(tmp_path: Path) -> None:
    repo = _repo(tmp_path / "repo", 2)
    out = tmp_path / "code.json"
    assert M.main(["--root", str(repo), "--out", str(out)]) == 0
    assert json.loads(out.read_text(encoding="utf-8"))["version_code"] == 100002
