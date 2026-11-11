#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""FIX 域复发门的逐例自测（批次 45 WS3 / item 48）。

## 它消灭的具体失败

`tools/ci/fix_relapse_gate.py` 给出的是总退出码。若门本身写成恒真（例如只断言文件
存在、或把关键分支写成 `return True`），它仍会打印 `EXIT=0`，让“修复没被锁住”看起来
像绿。本文件对每个“已修项”做 A/B 两向：

- **绿**：直接读当前仓库文件，门必须 PASS；
- **红**：把缺陷改回去写进临时副本，门必须 RED。

红向不 import 门的辅助函数做判断，而是把门当黑盒再跑一次——门与测试同时错才可能
同时绿。阻塞项同样两向：缺 runbook 出口必须红；源 ID 被抬成“PASS 且无 review/device”
必须红。

边界：只读仓库文本与台账；设备/Provider 面仍由 runbook 出口承载，本文件不证明运行时行为。
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "ci"))

import fix_relapse_gate as G  # noqa: E402

RUNBOOK_REL = G.RUNBOOK
LEDGER_REL = "docs/acceptance/ledger.md"


def _tmp_file(tmp_path: Path, rel: str) -> Path:
    src = ROOT / rel
    dst = tmp_path / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(src.read_text(encoding="utf-8", errors="replace"), encoding="utf-8")
    return dst


def _fixture_ledger(tmp_path: Path, ids: list[str], verdict: str = "BLOCKED",
                    evidence: str = "-") -> Path:
    rows = ["| ID | 域 | 检查 | 方法 | 证据 | verdict |", "|---|---|---|---|---|---|"]
    for row_id in ids:
        domain = row_id.rsplit("-", 1)[0]
        rows.append(f"| {row_id} | {domain} | 检查 | BLOCKED: runbook | {evidence} | {verdict} |")
    path = tmp_path / "ledger.md"
    path.write_text("\n".join(rows) + "\n", encoding="utf-8")
    return path


@pytest.fixture(autouse=True)
def _restore_ledger_path():
    original = G._LEDGER_PATH[0]
    yield
    G._LEDGER_PATH[0] = original


# --------------------------------------------------------------------------
# 已修项：当前树绿
# --------------------------------------------------------------------------


def test_fixed_gates_are_green_on_current_tree():
    for ticket in ("FIX-T01", "FIX-T02", "FIX-T03", "FIX-T04", "FIX-T05", "FIX-T06", "FIX-T07"):
        ok, detail = G.GATES[ticket](ROOT)
        assert ok, f"{ticket} 在当前树上应为绿：{detail}"


# --------------------------------------------------------------------------
# 已修项：改回缺陷后必须红（逐例独立构造，不复用门的突变表）
# --------------------------------------------------------------------------


def test_t01_slider_clamp_removal_turns_gate_red(tmp_path):
    screen = _tmp_file(tmp_path, G.SCREEN)
    _tmp_file(tmp_path, G.SLIDER_TEST)
    assert G.gate_t01(tmp_path)[0] is True
    text = screen.read_text(encoding="utf-8").replace(
        "retentionFromSlider(value)", "value.toDouble()"
    )
    screen.write_text(text, encoding="utf-8")
    assert G.gate_t01(tmp_path)[0] is False


def test_t02_concatenated_exception_turns_gate_red(tmp_path):
    secondary = _tmp_file(tmp_path, G.SECONDARY)
    assert G.gate_t02(tmp_path)[0] is True
    text = secondary.read_text(encoding="utf-8").replace(
        "请稍后重试。", "请稍后重试。${failure.message.orEmpty()}", 1
    )
    secondary.write_text(text, encoding="utf-8")
    assert G.gate_t02(tmp_path)[0] is False


def test_t03_stale_copy_turns_gate_red(tmp_path):
    disclosure = _tmp_file(tmp_path, G.DISCLOSURE)
    _tmp_file(tmp_path, G.EXPORTER)
    assert G.gate_t03(tmp_path)[0] is True
    text = disclosure.read_text(encoding="utf-8").replace(
        "# AI 生成内容用户说明",
        "PDF 尚未写入文件级隐式信息；PDF 不保留文件级隐式元数据\n# AI 生成内容用户说明",
        1,
    )
    disclosure.write_text(text, encoding="utf-8")
    assert G.gate_t03(tmp_path)[0] is False


def test_t04_unread_back_injection_turns_gate_red(tmp_path):
    delivery = _tmp_file(tmp_path, G.DELIVERY)
    _tmp_file(tmp_path, G.KD)
    assert G.gate_t04(tmp_path)[0] is True
    text = delivery.read_text(encoding="utf-8").replace(
        "internal object PreparedPdfPrintWriter",
        "// PdfAigcInjector()\ninternal object PreparedPdfPrintWriter",
        1,
    )
    delivery.write_text(text, encoding="utf-8")
    assert G.gate_t04(tmp_path)[0] is False


def test_t05_hidden_passthrough_change_turns_gate_red(tmp_path):
    paper = _tmp_file(tmp_path, G.PAPER_EDIT)
    _tmp_file(tmp_path, G.KD)
    assert G.gate_t05(tmp_path)[0] is True
    text = paper.read_text(encoding="utf-8").replace(
        "hasModelParsedText = sourceInput.hasModelParsedText", "hasModelParsedText = false", 1
    )
    paper.write_text(text, encoding="utf-8")
    assert G.gate_t05(tmp_path)[0] is False


def test_t06_open_two_point_fixture_turns_gate_red(tmp_path):
    restore = _tmp_file(tmp_path, G.RESTORE_TEST)
    assert G.gate_t06(tmp_path)[0] is True
    text = restore.read_text(encoding="utf-8").replace('["Z"]', '["L",0.0,0.0]', 1)
    restore.write_text(text, encoding="utf-8")
    assert G.gate_t06(tmp_path)[0] is False


def test_t07_docx_claim_without_readback_turns_gate_red(tmp_path):
    docx = _tmp_file(tmp_path, G.DOCX)
    _tmp_file(tmp_path, G.KD)
    assert G.gate_t07(tmp_path)[0] is True
    text = docx.read_text(encoding="utf-8").replace(
        "internal object DocxMistakeRenderer", "// /AIGC\ninternal object DocxMistakeRenderer", 1
    )
    docx.write_text(text, encoding="utf-8")
    assert G.gate_t07(tmp_path)[0] is False


# --------------------------------------------------------------------------
# 阻塞项：登记 + 台账不得抬成 PASS
# --------------------------------------------------------------------------


def _blocked_fixture(tmp_path: Path, ticket: str, ledger: Path | None = None) -> Path:
    runbook = _tmp_file(tmp_path, RUNBOOK_REL)
    text = runbook.read_text(encoding="utf-8")
    if f"| {ticket} |" not in text:
        # 用真实 runbook 片段构造：补一行符合格式的出口条目。
        text += (
            f"\n| {ticket} | 高 | 阻塞项 | BLOCKED | 复跑条件=注入凭据/设备/账号后重跑 |\n"
        )
        runbook.write_text(text, encoding="utf-8")
    G._LEDGER_PATH[0] = ledger or _fixture_ledger(tmp_path, G.BLOCKED_TICKETS[ticket]["ids"])
    return tmp_path


def test_blocked_gate_is_green_when_registered_and_not_lifted(tmp_path):
    root = _blocked_fixture(tmp_path, "FIX-T08")
    ok, detail = G.GATES["FIX-T08"](root)
    assert ok, detail


def test_blocked_gate_without_runbook_row_is_red(tmp_path):
    root = _blocked_fixture(tmp_path, "FIX-T08")
    runbook = root / RUNBOOK_REL
    original = runbook.read_text(encoding="utf-8")
    runbook.write_text(
        "\n".join(line for line in original.splitlines() if not line.startswith("| FIX-T08 |")) + "\n",
        encoding="utf-8",
    )
    assert G.GATES["FIX-T08"](root)[0] is False


def test_blocked_gate_rejects_pass_without_review_or_device(tmp_path):
    ids = G.BLOCKED_TICKETS["FIX-T09"]["ids"]
    ledger = _fixture_ledger(tmp_path, ids, verdict="PASS", evidence="cmd:tools/ci/fix_relapse_gate.py; test:x.xml")
    root = _blocked_fixture(tmp_path, "FIX-T09", ledger=ledger)
    ok, detail = G.GATES["FIX-T09"](root)
    assert ok is False
    assert "PASS" in detail


def test_blocked_gate_accepts_pass_with_device_evidence(tmp_path):
    ids = G.BLOCKED_TICKETS["FIX-T09"]["ids"]
    ledger = _fixture_ledger(
        tmp_path, ids, verdict="PASS",
        evidence="device:docs/research/evidence/2026-11-06-batch-45/fix/device.json; cmd:x.log",
    )
    root = _blocked_fixture(tmp_path, "FIX-T09", ledger=ledger)
    ok, detail = G.GATES["FIX-T09"](root)
    assert ok, detail


def test_t20_requires_lockfile_gate_still_declares_not_landed(tmp_path):
    root = _blocked_fixture(tmp_path, "FIX-T20")
    gate_file = _tmp_file(root, G.LOCKFILE_GATE)
    assert G.gate_t20(root)[0] is True
    gate_file.write_text(
        gate_file.read_text(encoding="utf-8").replace("锁尚未落地", "锁已覆盖全模块"), encoding="utf-8"
    )
    assert G.gate_t20(root)[0] is False


# --------------------------------------------------------------------------
# CLI 契约：绿 exit 0 / 红 exit 1 / 未知门 exit 2
# --------------------------------------------------------------------------


def test_cli_exit_codes():
    import subprocess

    tool = str(ROOT / "tools" / "ci" / "fix_relapse_gate.py")
    green = subprocess.run([sys.executable, "-I", tool, "--gate", "FIX-T01"],
                           capture_output=True, text=True, cwd=str(ROOT))
    assert green.returncode == 0, green.stdout + green.stderr
    assert "EXIT=0" in green.stdout
    unknown = subprocess.run([sys.executable, "-I", tool, "--gate", "FIX-T99"],
                             capture_output=True, text=True, cwd=str(ROOT))
    assert unknown.returncode == 2
