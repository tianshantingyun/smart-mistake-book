#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G 域验收门的**负向对照**（批次 44）。

## 它消灭的具体失败

批次 42/43 的“绿”多次是“断言永远不会红”：检查写在文档里、或者断言只匹配一句
注释。本文件把三条 G 断言分别喂给**被改坏的输入副本**（允许备份、把 FileProvider
路径改成根、把明文流量打开），要求它们必须返回 False。任何一条仍然返回 True，
说明该断言已经不可能抓住对应失败——那比没有断言更糟。

不改仓库内的任何产品文件：每个用例把目标文件复制到临时目录，把 gate 的 ROOT
指向该目录后再调用同一个断言函数。
"""

from __future__ import annotations

import importlib.util
import shutil
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
GATE_PATH = ROOT / "tools" / "ci" / "g_security_gate.py"


def load_gate():
    spec = importlib.util.spec_from_file_location("g_security_gate_under_test", GATE_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


@pytest.fixture()
def sandbox(tmp_path: Path):
    gate = load_gate()
    original = gate.ROOT
    gate.ROOT = tmp_path
    try:
        yield gate, tmp_path
    finally:
        gate.ROOT = original


def place(root: Path, rel: str, source: str) -> Path:
    target = root / rel
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / source, target)
    return target


def test_allow_backup_enabled_must_fail(sandbox) -> None:
    gate, root = sandbox
    target = place(root, "app/src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
    text = target.read_text(encoding="utf-8").replace('android:allowBackup="false"', 'android:allowBackup="true"')
    target.write_text(text, encoding="utf-8")
    ok, detail = gate.chk_g019()
    assert ok is False, detail


PROVIDER_PATH_FILES = (
    "core/export/src/main/res/xml/mistake_pdf_export_paths.xml",
    "core/export/src/main/res/xml/mistake_docx_export_paths.xml",
    "core/data/src/main/res/xml/batch_import_file_paths.xml",
    "feature/capture/src/main/res/xml/capture_file_paths.xml",
)


def test_provider_root_path_must_fail(sandbox) -> None:
    gate, root = sandbox
    for name in PROVIDER_PATH_FILES:
        place(root, name, name)
    target = root / "feature/capture/src/main/res/xml/capture_file_paths.xml"
    target.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<paths xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <cache-path name="captured_images" path="." />\n'
        "</paths>\n",
        encoding="utf-8",
    )
    ok, detail = gate.chk_g064()
    assert ok is False, detail


def test_cleartext_enabled_must_fail(sandbox) -> None:
    gate, root = sandbox
    place(root, "app/src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
    target = place(root, "app/src/localFirst/res/xml/network_security_config.xml",
                   "app/src/localFirst/res/xml/network_security_config.xml")
    target.write_text(
        target.read_text(encoding="utf-8").replace(
            'cleartextTrafficPermitted="false"', 'cleartextTrafficPermitted="true"'),
        encoding="utf-8",
    )
    ok, detail = gate.chk_g029()
    assert ok is False, detail
