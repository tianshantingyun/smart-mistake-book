#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""FIX 域复发门（批次 45 WS3 / item 48：每个修复单一个门）。

## 它消灭的具体失败

批次 44 的 FIX 100 行整域没有发生：既没有修复单，也没有任何一条能在改动后
重新判定“这个缺陷是修好了还是又回来了”的检查。后果是三种假绿：

1. **修了没锁**：`SchedulingSettingsScreen` 的滑杆夹回 band、`SecondaryScreens`
   的失败文案去掉 `failure.message`——只要有人把这一行改回去，台账仍写 PASS；
2. **没修却当作修了**：KD-84/KD-85 还 open，行里却可以凭一句散文关掉；
3. **外部阻塞被悄悄抬成通过**：真实 Provider、arm64、生产签名、Play Console
   这些没有设备/账号就跑不出来的项，补两条本地 cmd/test 就能被写成 PASS。

本工具给 20 个修复单各配一道门，门只做两种判定：

- **已修项**：断言修复面的关键结构仍在（夹回 band 的映射函数、不再拼接异常、
  闭合路径夹具……）。结构被删/被改回 → 门红。
- **阻塞项**：断言 (a) runbook 里有该单的出口条目，(b) 台账里该单覆盖的源 ID
  没有在缺少 `review`/`device` 证据的情况下被写成 PASS。任一条不成立 → 门红。

门**锁住当前判词**：真要关闭 KD-84/KD-85 这类条目，必须同时提交独立读回证据
并显式更新本门，而不是让判词随文档漂移。

用法：

    python -I tools/ci/fix_relapse_gate.py --all
    python -I tools/ci/fix_relapse_gate.py --gate FIX-T01
    python -I tools/ci/fix_relapse_gate.py --all --root <fixture>
    python -I tools/ci/fix_relapse_gate.py --all --ledger <fixture-ledger.md>

输出：每行 `FIX-TNN PASS|RED <detail>`，末两行统计与 `EXIT=0|1`。
退出码：0 全绿；1 有红门；2 参数错误（未知门/台账不可读）。

不自称证明运行时行为：门只读仓库文本与台账，设备/Provider 面仍由 runbook 出口承载。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import acceptance_model as M  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"
RUNBOOK = "docs/acceptance/runbook.md"
KD = "docs/known-defects.md"

SCREEN = "app/src/main/kotlin/com/tingyun/smartmistakebook/SchedulingSettingsScreen.kt"
SLIDER_TEST = "app/src/test/kotlin/com/tingyun/smartmistakebook/SchedulingRetentionSliderValueTest.kt"
SECONDARY = "app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt"
DISCLOSURE = "docs/ai-content-disclosure-copy.md"
EXPORTER = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt"
DELIVERY = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt"
PAPER_EDIT = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEdit.kt"
DOCX = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRenderer.kt"
T04_EVIDENCE_LOG = "docs/research/evidence/2026-11-06-batch-47/h-domain/h-061-pypdf-b47.log"
T05_EVIDENCE_DIR = "docs/research/evidence/2026-11-09-batch-50/aigc"
T07_EVIDENCE_DIR = "docs/research/evidence/2026-11-09-batch-50/aigc"
RESTORE_TEST = (
    "core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/"
    "TutorVisualRebuildRestoreInstrumentedTest.kt"
)
LOCKFILE_GATE = "tools/ci/check_lockfiles.py"


def read(root: Path, rel: str) -> str:
    path = root / rel
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


# --------------------------------------------------------------------------
# 已修项：锁住修复面
# --------------------------------------------------------------------------


def gate_t01(root: Path) -> tuple[bool, str]:
    """F-055/F-057：滑杆读数先落 band 再进 SchedulingOptions。"""
    screen = read(root, SCREEN)
    test = read(root, SLIDER_TEST)
    checks = {
        "夹回函数": "fun retentionFromSlider(value: Float): Double" in screen,
        "band 常量": "coerceIn(RETENTION_MIN, RETENTION_MAX)" in screen,
        "滑杆走夹回": "retentionFromSlider(value)" in screen,
        "原始读数已退场": "desiredRetention = value.toDouble()" not in screen,
        "端点回归测试": "retentionFromSlider(0.97f)" in test and "retentionFromSlider(0.7f)" in test,
    }
    missing = [name for name, ok in checks.items() if not ok]
    if missing:
        return False, "滑杆夹回被改回或回归测试丢失：" + "、".join(missing)
    return True, "滑杆端点仍经 coerceIn 落 band，端点回归用例在位"


def gate_t02(root: Path) -> tuple[bool, str]:
    """H-066：诊断包失败文案不拼接原始异常 message（同族扩展到备份/校验/删除/清理/恢复）。"""
    text = read(root, SECONDARY)
    raw = re.search(r"\$\{\s*failure\.message|\.message\.orEmpty\(\)", text)
    if raw:
        return False, f"学生可见文案重新拼接了原始异常 message：{raw.group(0)}"
    if "failure is BackupRestoreDatabaseClosedException" not in text:
        return False, "域内异常（人工措辞）的转述分支被一并删除，恢复失败文案失去可读细节"
    if "请稍后重试" not in text:
        return False, "失败文案未回落到不含实现细节的固定措辞"
    return True, "7 处失败文案均不拼接原始异常 message；域内异常保留人工措辞"


def gate_t03(root: Path) -> tuple[bool, str]:
    """H-052：免责声明文案与 PDF 隐式标识现状一致。"""
    disclosure = read(root, DISCLOSURE)
    exporter = read(root, EXPORTER)
    stale = (
        "尚未写入文件级隐式信息" in disclosure
        and "PDF 不保留文件级隐式元数据" in disclosure
    )
    implemented = "PdfAigcInjector().inject" in exporter
    if stale and implemented:
        return False, "文案仍称 PDF 未写入文件级隐式信息，而导出链已注入 /AIGC"
    return True, "文案未与导出链现状冲突（注入在树：%s）" % implemented


def gate_t04(root: Path) -> tuple[bool, str]:
    """KD-84：部分页打印子集必须带 prepared 的文档级 /AIGC；关闭要有独立读回证据。"""
    kd = read(root, KD)
    delivery = read(root, DELIVERY)
    closed_marker = "KD-84 (closed" in kd
    relabel = "relabelSubset" in delivery and "PdfAigcInjector().inject(subsetBytes" in delivery
    if not (closed_marker and relabel):
        return False, "KD-84 判词与代码不一致（closed 标记=%s，子集重注入=%s）" % (closed_marker, relabel)
    evidence = root / T04_EVIDENCE_LOG
    if not evidence.is_file():
        return False, "KD-84 已关闭但独立读回证据缺失：%s" % T04_EVIDENCE_LOG
    log = evidence.read_text(encoding="utf-8", errors="replace")
    produce_ids = re.findall(r'"ProduceID":"([0-9a-f]{64})"', log)
    pages = set(re.findall(r"pages= (\d+)", log))
    if len(set(produce_ids)) != 1 or len(produce_ids) < 2:
        return False, "独立读回的 ProduceID 不一致或缺失（%d 份）" % len(produce_ids)
    if not {"4", "1"} <= pages:
        return False, "独立读回缺少整份/子集页数对（pages=%s）" % sorted(pages)
    return True, (
        "KD-84 关闭态一致：子集重注入在树，宿主 pypdf 读回子集 /AIGC 存在、"
        "ProduceID 与整份产物/输入指纹一致、页数 4→1（所选页数）"
    )


def gate_t05(root: Path) -> tuple[bool, str]:
    """KD-85：模型改卷文本必须纳入判据；关闭要有设备 + 独立读回证据（2026-11-09 关闭态）。"""
    kd = read(root, KD)
    paper = read(root, PAPER_EDIT)
    closed_marker = "KD-85 (closed" in kd
    wired = (
        "modelTextApplied" in paper
        and "hasModelParsedText = sourceInput.hasModelParsedText || modelTextApplied" in paper
    )
    if not (closed_marker and wired):
        return False, "KD-85 判词与代码不一致（closed 标记=%s，toDraft 并入模型文本=%s）" % (
            closed_marker,
            wired,
        )
    device = root / T05_EVIDENCE_DIR / "green-device-kd85.log"
    readback = root / T05_EVIDENCE_DIR / "pdf-independent-readback.log"
    if not device.is_file() or not readback.is_file():
        return False, "KD-85 已关闭但设备/独立读回证据缺失：%s" % T05_EVIDENCE_DIR
    device_log = device.read_text(encoding="utf-8", errors="replace")
    readback_log = readback.read_text(encoding="utf-8", errors="replace")
    if "OK (2 tests)" not in device_log:
        return False, "KD-85 设备用例未全绿（缺 OK (2 tests)）"
    positive = "kd85-model-edited-labeled.pdf" in readback_log and "/AIGC present: True" in readback_log
    negative = (
        "kd85-manual-edited-unlabeled.pdf" in readback_log
        and "/AIGC present: False" in readback_log
    )
    if not (positive and negative):
        return False, "KD-85 独立读回缺正向 /AIGC 或手工编辑负向（无伪标）"
    return True, (
        "KD-85 关闭态一致：modelTextApplied 并入 toDraft；设备 OK (2 tests)；"
        "pypdf 读回模型改写→/AIGC、手工编辑→无 /AIGC"
    )


def gate_t06(root: Path) -> tuple[bool, str]:
    """C-065：恢复回放的绘制夹具必须是闭合轮廓（零面积夹具曾造成假 FAIL）。"""
    text = read(root, RESTORE_TEST)
    closed = '["Z"]' in text and '"pathCommands":4' in text
    open_line = '[["M",0.0,0.0],["L",10.0,10.0]]' in text
    if closed and not open_line:
        return True, "恢复腿夹具是闭合三角形（M/L/L/Z），落墨断言有分辨力"
    return False, "恢复腿夹具退回开口两点线段/统计不匹配：零面积会让墨量断言失去分辨力"


def gate_t07(root: Path) -> tuple[bool, str]:
    """H-018 子项：DOCX 文档级 AIGC 标识（2026-11-09 关闭态，20258A §6.1 载体）。"""
    docx = read(root, DOCX)
    writes = (
        "labeled = PdfAigcContentPolicy.shouldLabel(" in docx
        and "customPropertiesPart" in docx
        and "PdfAigcMetadata.document(input.inputSha256)" in docx
    )
    if not writes:
        return False, "DOCX 未按 20258A §6.1 写 docProps/custom.xml 的 AIGC 属性"
    readback = root / T07_EVIDENCE_DIR / "docx-independent-readback.log"
    if not readback.is_file():
        return False, "DOCX 已写 AIGC 但独立读回证据缺失：%s" % T07_EVIDENCE_DIR
    log = readback.read_text(encoding="utf-8", errors="replace")
    positive = (
        "model-text-labeled.docx" in log
        and "docProps/custom.xml present: True" in log
        and "name= AIGC" in log
    )
    negative = (
        "pure-user-unlabeled.docx" in log
        and "docProps/custom.xml present: False" in log
    )
    if not (positive and negative):
        return False, "DOCX 独立读回缺正向 AIGC 属性或纯用户负向（无伪标）"
    return True, (
        "DOCX 关闭态一致：docProps/custom.xml 写 name=AIGC 的七键 JSON；"
        "宿主独立读回正向在、纯用户内容不在"
    )


# --------------------------------------------------------------------------
# 阻塞项：登记 + 台账不得把外部阻塞抬成 PASS
# --------------------------------------------------------------------------

BLOCKED_TICKETS: dict[str, dict] = {
    "FIX-T08": {
        "title": "真实 Provider 端到端",
        "ids": ["A-010", "D-082", "D-083", "D-084", "D-085", "D-086", "D-087", "D-088", "D-089", "D-090"],
    },
    "FIX-T09": {
        "title": "arm64 设备矩阵",
        "ids": ["F-006", "F-015", "F-024", "F-033", "F-042", "F-051", "F-060",
                "F-069", "F-078", "F-087", "J-035", "J-037", "J-038", "J-039", "J-042", "J-043"],
    },
    "FIX-T10": {
        "title": "生产签名与指纹",
        "ids": ["J-019", "J-020", "J-021", "J-024", "J-025", "J-027"],
    },
    "FIX-T11": {
        "title": "版本号单调与版本源",
        "ids": ["J-048", "J-051", "J-052", "J-086"],
    },
    "FIX-T12": {
        "title": "AAB 16KB / ABI / quickjs",
        "ids": ["J-033", "J-034", "J-041", "J-088"],
    },
    "FIX-T13": {
        "title": "干净树与产物 revision",
        "ids": ["J-001", "J-002", "J-006"],
    },
    "FIX-T14": {
        "title": "商店素材与 Play 控制台",
        "ids": ["J-017", "J-073", "J-074", "J-075", "J-076", "J-077", "J-078", "J-079",
                "J-080", "J-081", "J-082", "J-083", "J-084", "J-085", "J-089", "J-090"],
    },
    "FIX-T15": {
        "title": "成对产物升级/降级/回滚",
        "ids": ["J-055", "J-056", "J-057", "J-061", "J-065", "J-068", "J-069"],
    },
    "FIX-T16": {
        "title": "青少年模式",
        "ids": ["H-039", "H-040", "H-041", "H-042", "H-043"],
    },
    "FIX-T17": {
        "title": "AIGC 显式角标 G1/G2",
        "ids": ["H-008"],
    },
    "FIX-T18": {
        "title": "隐私政策 / data safety / 备案 / 英文材料",
        "ids": ["H-024", "H-025", "H-036", "H-051", "H-053", "H-081"],
    },
    "FIX-T19": {
        "title": "无障碍真实驱动 / IME / 旋转",
        "ids": ["I-007", "I-061", "I-070", "I-075", "I-079"],
    },
    "FIX-T20": {
        "title": "供应链与台账协议门",
        "ids": ["G-087", "G-088", "D0-030", "D0-081"],
    },
}


def _registered(root: Path, ticket: str) -> tuple[bool, str]:
    runbook = read(root, RUNBOOK)
    if f"| {ticket} |" not in runbook:
        return False, f"runbook §13 缺 {ticket} 的出口条目"
    start = runbook.find(f"| {ticket} |")
    row = runbook[start:].splitlines()[0]
    if "复跑条件=" not in row:
        return False, f"runbook 的 {ticket} 条目没有写复跑条件"
    return True, ""


def _ledger_rows(root: Path, ledger: Path) -> dict[str, M.Row]:
    del root  # 台账路径由参数决定，root 只服务仓库文件读取
    rows, _issues = M.parse_ledger(ledger)
    return {row.row_id: row for row in rows}


def _blocked_gate(ticket: str, root: Path) -> tuple[bool, str]:
    spec = BLOCKED_TICKETS[ticket]
    ok_reg, detail = _registered(root, ticket)
    if not ok_reg:
        return False, detail
    if not _LEDGER_PATH[0].exists():
        return False, f"台账不可读：{_LEDGER_PATH[0]}"
    rows = _ledger_rows(root, _LEDGER_PATH[0])
    lifted = []
    for row_id in spec["ids"]:
        row = rows.get(row_id)
        if row is None:
            lifted.append(f"{row_id}（台账缺行）")
            continue
        if row.verdict != "PASS":
            continue
        kinds = {item.kind for item in row.evidence()}
        if "review" in kinds or "device" in kinds:
            continue
        lifted.append(f"{row_id}（PASS 但无 review/device 证据）")
    if lifted:
        return False, "外部阻塞被抬成通过：" + "、".join(lifted[:6])
    return True, f"{len(spec['ids'])} 个源 ID 均未在缺 review/device 的情况下写成 PASS"


_LEDGER_PATH: list[Path] = [DEFAULT_LEDGER]


def gate_t20(root: Path) -> tuple[bool, str]:
    """T20 在通用登记/台账判定之外，再加一条：锁文件门仍声明“尚未落地”。"""
    ok, detail = _blocked_gate("FIX-T20", root)
    if not ok:
        return False, detail
    gate_text = read(root, LOCKFILE_GATE)
    if "锁尚未落地" not in gate_text:
        return False, "check_lockfiles 不再显式声明“锁尚未落地”放行态，KD-72 口径可能被改写"
    return True, detail + "；锁门放行态仍有显式声明"


GATES = {
    "FIX-T01": gate_t01,
    "FIX-T02": gate_t02,
    "FIX-T03": gate_t03,
    "FIX-T04": gate_t04,
    "FIX-T05": gate_t05,
    "FIX-T06": gate_t06,
    "FIX-T07": gate_t07,
    "FIX-T20": gate_t20,
}
for _ticket in BLOCKED_TICKETS:
    if _ticket != "FIX-T20":
        GATES[_ticket] = (lambda tid: (lambda root: _blocked_gate(tid, root)))(_ticket)

GATE_ORDER = [
    "FIX-T01", "FIX-T02", "FIX-T03", "FIX-T04", "FIX-T05", "FIX-T06", "FIX-T07",
    "FIX-T08", "FIX-T09", "FIX-T10", "FIX-T11", "FIX-T12", "FIX-T13", "FIX-T14",
    "FIX-T15", "FIX-T16", "FIX-T17", "FIX-T18", "FIX-T19", "FIX-T20",
]

TITLES = {
    "FIX-T01": "复习滑杆端点崩溃（F-055/F-057）",
    "FIX-T02": "诊断包失败文案拼异常（H-066）",
    "FIX-T03": "免责声明文案与现状不一致（H-052）",
    "FIX-T04": "部分页打印丢 /AIGC（KD-84：H-018/H-061）",
    "FIX-T05": "模型改卷文本漏标（KD-85：H-018/H-062）",
    "FIX-T06": "恢复回放零面积夹具（C-065）",
    "FIX-T07": "DOCX 无文档级 /AIGC（H-018）",
    "FIX-T08": "真实 Provider 端到端（A-010/D-082..D-090）",
    "FIX-T09": "arm64 设备矩阵（F 十项 + J-035..J-043）",
    "FIX-T10": "生产签名与指纹（J-019..J-027）",
    "FIX-T11": "版本号单调与版本源（J-048/J-051/J-052/J-086）",
    "FIX-T12": "AAB 16KB/ABI/quickjs（J-033/J-034/J-041/J-088）",
    "FIX-T13": "干净树与产物 revision（J-001/J-002/J-006）",
    "FIX-T14": "商店素材与 Play 控制台（J-017/J-073..J-090）",
    "FIX-T15": "成对产物升级/降级/回滚（J-055..J-069）",
    "FIX-T16": "青少年模式（H-039..H-043）",
    "FIX-T17": "AIGC 显式角标 G1/G2（H-008）",
    "FIX-T18": "隐私政策/data safety/备案/英文材料（H-024..H-081）",
    "FIX-T19": "无障碍真实驱动/IME/旋转（I-007..I-079）",
    "FIX-T20": "供应链与台账协议门（G-087/G-088 + D0-030/D0-081）",
}


# --------------------------------------------------------------------------
# 自突变证明：门不是橡皮图章
# --------------------------------------------------------------------------

# 每单给出“把修复改回去/把阻塞抬成通过”的最小突变。证明分两步：
# 未突变副本必须 PASS（否则突变失败不能归因于突变本身），突变副本必须 RED。
COPIES: dict[str, list[str]] = {
    "FIX-T01": [SCREEN, SLIDER_TEST],
    "FIX-T02": [SECONDARY],
    "FIX-T03": [DISCLOSURE, EXPORTER],
    "FIX-T04": [KD, DELIVERY],
    "FIX-T05": [
        KD,
        PAPER_EDIT,
        T05_EVIDENCE_DIR + "/green-device-kd85.log",
        T05_EVIDENCE_DIR + "/pdf-independent-readback.log",
    ],
    "FIX-T06": [RESTORE_TEST],
    "FIX-T07": [DOCX, KD, T07_EVIDENCE_DIR + "/docx-independent-readback.log"],
}

MUTATIONS: dict[str, list[tuple[str, str, str]]] = {
    "FIX-T01": [(SCREEN, "retentionFromSlider(value)", "value.toDouble()")],
    "FIX-T02": [(SECONDARY, "请稍后重试。", "请稍后重试。${failure.message.orEmpty()}")],
    "FIX-T03": [(DISCLOSURE, "# AI 生成内容用户说明 · 产品文案（G5 载体）", "PDF 尚未写入文件级隐式信息；PDF 不保留文件级隐式元数据\n# AI 生成内容用户说明 · 产品文案（G5 载体）")],
    "FIX-T04": [(DELIVERY, "internal object PreparedPdfPrintWriter", "// PdfAigcInjector\ninternal object PreparedPdfPrintWriter")],
    "FIX-T05": [(PAPER_EDIT, "hasModelParsedText = sourceInput.hasModelParsedText || modelTextApplied", "hasModelParsedText = false")],
    "FIX-T06": [(RESTORE_TEST, '["Z"]', '["L",0.0,0.0]')],
    "FIX-T07": [(DOCX, "labeled = PdfAigcContentPolicy.shouldLabel(", "labeled = false && PdfAigcContentPolicy.shouldLabel(")],
}


def _copy_files(root: Path, rels: list[str], dest: Path) -> None:
    for rel in rels:
        src = root / rel
        if not src.exists():
            continue
        target = dest / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(src.read_text(encoding="utf-8", errors="replace"), encoding="utf-8")


def _lift_row_to_pass(ledger_text: str, row_id: str) -> str | None:
    """把一行改成“PASS + 只有 cmd/test 证据”，模拟把外部阻塞抬成通过。"""
    lines = ledger_text.splitlines(keepends=True)
    for index, line in enumerate(lines):
        if not line.startswith(f"| {row_id} |"):
            continue
        parts = line.rstrip("\n").split("|")
        if len(parts) != 8:
            return None
        parts[5] = " cmd:tools/ci/fix_relapse_gate.py; test:app/build/test-results/x.xml "
        parts[6] = " PASS "
        lines[index] = "|".join(parts) + "\n"
        return "".join(lines)
    return None


def prove_red(ticket: str, root: Path) -> tuple[bool, str]:
    """返回 (证明成立?, detail)。未突变 PASS + 突变 RED 才算成立。"""
    import tempfile

    with tempfile.TemporaryDirectory(prefix="fix-red-") as tmp:
        tmp_root = Path(tmp)
        ledger = _LEDGER_PATH[0]
        if ticket in MUTATIONS:
            _copy_files(root, COPIES[ticket], tmp_root)
            baseline, _ = GATES[ticket](tmp_root)
            if not baseline:
                return False, "未突变副本已红，突变证明无法归因"
            for rel, old, new in MUTATIONS[ticket]:
                target = tmp_root / rel
                text = target.read_text(encoding="utf-8")
                if old not in text:
                    return False, f"突变锚点不在 {rel}，证明失效"
                target.write_text(text.replace(old, new, 1), encoding="utf-8")
            mutated, _ = GATES[ticket](tmp_root)
            if mutated:
                return False, "把修复改回去后门仍绿（门没有分辨力）"
            return True, "改回缺陷后门转红"

        # 阻塞项：登记行缺失、或源 ID 被抬成 PASS（无 review/device），两种都必须红。
        _copy_files(root, [RUNBOOK, LOCKFILE_GATE], tmp_root)
        ledger_text = ledger.read_text(encoding="utf-8")
        shutil_ledger = tmp_root / "ledger.md"
        shutil_ledger.write_text(ledger_text, encoding="utf-8")
        _LEDGER_PATH[0] = shutil_ledger
        baseline, detail = GATES[ticket](tmp_root)
        if not baseline:
            _LEDGER_PATH[0] = ledger
            return False, f"未突变副本已红：{detail}"
        runbook_path = tmp_root / RUNBOOK
        original = runbook_path.read_text(encoding="utf-8")
        stripped = "\n".join(
            line for line in original.splitlines()
            if not line.startswith(f"| {ticket} |")
        )
        runbook_path.write_text(stripped + "\n", encoding="utf-8")
        without_registration, _ = GATES[ticket](tmp_root)
        runbook_path.write_text(original, encoding="utf-8")
        if without_registration:
            _LEDGER_PATH[0] = ledger
            return False, "删掉 runbook 出口后门仍绿"
        first_id = BLOCKED_TICKETS[ticket]["ids"][0] if ticket in BLOCKED_TICKETS else "G-087"
        lifted = _lift_row_to_pass(ledger_text, first_id)
        if lifted is None:
            _LEDGER_PATH[0] = ledger
            return False, f"台账里找不到 {first_id}，证明失效"
        shutil_ledger.write_text(lifted, encoding="utf-8")
        after_lift, _ = GATES[ticket](tmp_root)
        _LEDGER_PATH[0] = ledger
        if after_lift:
            return False, f"{first_id} 被抬成 PASS 后门仍绿"
        return True, "删出口/抬 PASS 两种突变都让门转红"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="FIX 域复发门")
    parser.add_argument("--root", default=str(ROOT))
    parser.add_argument("--ledger", default=str(DEFAULT_LEDGER))
    parser.add_argument("--gate", action="append", default=None,
                        help="只跑指定门，可重复；缺省等于 --all")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--prove-red", action="store_true",
                        help="附加自突变证明：未突变副本绿、改回缺陷后红")
    args = parser.parse_args(argv)

    root = Path(args.root)
    _LEDGER_PATH[0] = Path(args.ledger)
    selected = args.gate or GATE_ORDER
    unknown = [tid for tid in selected if tid not in GATES]
    if unknown:
        print("UNKNOWN_GATE: " + ", ".join(unknown), file=sys.stderr)
        return 2

    passed = 0
    red = 0
    for ticket in selected:
        try:
            ok, detail = GATES[ticket](root)
        except M.LedgerError as exc:
            print(f"{ticket} RED 台账不可读：{exc}")
            red += 1
            continue
        mark = "PASS" if ok else "RED"
        if ok:
            passed += 1
        else:
            red += 1
        print(f"{ticket} {mark} [{TITLES[ticket]}] {detail}")
        if args.prove_red:
            proven, proof_detail = prove_red(ticket, root)
            print(f"{ticket} RED-PROOF {'OK' if proven else 'BROKEN'} {proof_detail}")
            if not proven:
                red += 1

    print(f"TOTAL: {len(selected)} gates, {passed} pass, {red} red")
    print(f"EXIT={0 if red == 0 else 1}")
    return 0 if red == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
