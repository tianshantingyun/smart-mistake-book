#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""FIX-T05 / FIX-T07 的**关闭态**复读（2026-11-09 批次 50 AIGC 线）。

## 它消灭的具体失败

`tools/ci/fix_relapse_gate.py` 在 2026-11-09 把 KD-85（FIX-T05）与 DOCX 文档级标识
（FIX-T07）从"open 一致性"升级为"关闭态 + 证据"：只写 `closed` 标记或只加一行结构，
不足以让门转绿，必须同时存在设备读回与宿主独立读回。本文件用 A/B 两向钉住升级后的门：

- 绿：仓库真实文件 + 真实证据目录 → PASS；
- 红：把缺陷改回去写进临时副本 → RED。

本文件是新增文件（不修改批次 45 的逐例自测），避免与他线在飞的测试文件整文件抢占。
"""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "ci"))

import fix_relapse_gate as G  # noqa: E402


def _copy(tmp_path: Path, rel: str) -> Path:
    src = ROOT / rel
    dst = tmp_path / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)
    return dst


def _t05_fixture(tmp_path: Path) -> Path:
    _copy(tmp_path, G.KD)
    _copy(tmp_path, G.PAPER_EDIT)
    _copy(tmp_path, G.T05_EVIDENCE_DIR + "/green-device-kd85.log")
    _copy(tmp_path, G.T05_EVIDENCE_DIR + "/pdf-independent-readback.log")
    return tmp_path


def _t07_fixture(tmp_path: Path) -> Path:
    _copy(tmp_path, G.KD)
    _copy(tmp_path, G.DOCX)
    _copy(tmp_path, G.T07_EVIDENCE_DIR + "/docx-independent-readback.log")
    return tmp_path


def test_t05_closed_state_with_evidence_is_green(tmp_path):
    root = _t05_fixture(tmp_path)
    ok, detail = G.gate_t05(root)
    assert ok, detail


def test_t05_dropping_the_model_text_term_turns_gate_red(tmp_path):
    root = _t05_fixture(tmp_path)
    paper = root / G.PAPER_EDIT
    text = paper.read_text(encoding="utf-8")
    assert "sourceInput.hasModelParsedText || modelTextApplied" in text
    paper.write_text(
        text.replace(
            "hasModelParsedText = sourceInput.hasModelParsedText || modelTextApplied",
            "hasModelParsedText = sourceInput.hasModelParsedText",
            1,
        ),
        encoding="utf-8",
    )
    ok, _ = G.gate_t05(root)
    assert not ok


def test_t05_closed_marker_without_readback_evidence_turns_gate_red(tmp_path):
    root = _t05_fixture(tmp_path)
    (root / G.T05_EVIDENCE_DIR / "pdf-independent-readback.log").unlink()
    ok, _ = G.gate_t05(root)
    assert not ok


def test_t07_docx_custom_property_with_evidence_is_green(tmp_path):
    root = _t07_fixture(tmp_path)
    ok, detail = G.gate_t07(root)
    assert ok, detail


def test_t07_disabling_the_label_decision_turns_gate_red(tmp_path):
    root = _t07_fixture(tmp_path)
    docx = root / G.DOCX
    text = docx.read_text(encoding="utf-8")
    assert "labeled = PdfAigcContentPolicy.shouldLabel(" in text
    docx.write_text(
        text.replace(
            "labeled = PdfAigcContentPolicy.shouldLabel(",
            "labeled = false && PdfAigcContentPolicy.shouldLabel(",
            1,
        ),
        encoding="utf-8",
    )
    ok, _ = G.gate_t07(root)
    assert not ok


def test_t07_docx_claim_without_independent_readback_turns_gate_red(tmp_path):
    root = _t07_fixture(tmp_path)
    (root / G.T07_EVIDENCE_DIR / "docx-independent-readback.log").unlink()
    ok, _ = G.gate_t07(root)
    assert not ok
