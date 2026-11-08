#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""H 域（合规与文案）可执行验收门（批次 44）。

## 它消灭的具体失败

批次 43 之前，H 域的结论停在散文与“已登记”：`docs/ai-content-disclosure-copy.md`
写了三档文案，`docs/known-defects.md` 写了 KD-75/KD-84/KD-85，但没有一条能复跑
的断言。后果是台账只能引用“某文件存在”，无法回答“显式标识现在到底加在哪几条链
上”“隐式标识的 7 个键是否仍与官方模板同序”“文案是否还承诺着已被推翻的现状”。
本脚本把 H 域里**源码/文本可判定**的部分逐条变成带退出码的断言，一条断言一行，
失败即 exit 1；缺口（未落地/外部依赖）以 FAIL 明确输出，不计为通过。

边界（同样重要）：本脚本只读仓库文本，**不证明运行时行为**。设备导出后由
pypdf/PdfRenderer 读回 `/AIGC`、TalkBack/截图走查、真人语感与审美不在本脚本能力
内，台账对这些行标 UNVERIFIED/BLOCKED。真实 Provider、生产签名、CAC 备案号、
Play Console data safety 表单属外部条件，一律 BLOCKED 并指向 runbook。

用法：

    python -I tools/ci/h_compliance_gate.py --topic aigc-explicit
    python -I tools/ci/h_compliance_gate.py --all
    python -I tools/ci/h_compliance_gate.py --topic aigc-explicit --only H-001,H-002

输出：每行 `H-NNN PASS|FAIL <detail>`，末行 `EXIT=0|1`。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

TOPICS = {
    "aigc-explicit": list(range(1, 10)),
    "aigc-implicit": list(range(10, 19)),
    "privacy": list(range(19, 28)),
    "data-safety": list(range(28, 37)),
    "minor": list(range(37, 46)),
    "disclaimer": list(range(46, 55)),
    "export-marking": list(range(55, 64)),
    "error-copy": list(range(64, 73)),
    "i18n": list(range(73, 82)),
    "typography": list(range(82, 91)),
}

CAPTION = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt"
PLANNER = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt"
DOCX = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRenderer.kt"
EXPORTER = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt"
COPY = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt"
POLICY = "core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcContentPolicy.kt"
SCHEMA = "core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcSchema.kt"
INJECTOR = "core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcInjector.kt"
READER = "core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcReader.kt"
METADATA = "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadata.kt"
TELEMETRY = "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryConsent.kt"
ATTRS = "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryAttributes.kt"
PRIV_FLOW = "PRIVACY-DATA-FLOW.md"
PRIV_CURRENT = "docs/current/privacy-and-data-safety.md"
DISCLOSURE = "docs/ai-content-disclosure-copy.md"
DECISIONS = "docs/agent-first-refactor-decisions-2026-09-23.md"
KD = "docs/known-defects.md"
SECONDARY = "app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt"
PROFILE = "feature/profile/src/main/java/com/tingyun/smartmistakebook/feature/profile/ProfileRoute.kt"
NOTIFICATIONS = "app/src/main/kotlin/com/tingyun/smartmistakebook/MistakeExportNotifications.kt"
MANIFEST = "app/src/main/AndroidManifest.xml"
MANIFEST_LOCAL = "app/src/localFirst/AndroidManifest.xml"
MANIFEST_OFFLINE = "app/src/strictOffline/AndroidManifest.xml"
BACKUP_RULES = "app/src/main/res/xml/backup_rules.xml"
EXTRACT_RULES = "app/src/main/res/xml/data_extraction_rules.xml"
STRINGS = "app/src/main/res/values/strings.xml"
CAPTION_TEST = "core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaptionTest.kt"
AIGC_DELIVERY = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt"
PAPER_EDIT = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEdit.kt"
AIGC_JVM_TEST = "core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporterAigcJvmTest.kt"
AIGC_DEVICE_TEST = "core/export/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporterAigcInstrumentedTest.kt"
COPY_TEST = "core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopyTest.kt"
XMP_TEST = "core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureAigcXmpTest.kt"
TELEMETRY_TEST = "core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryComplianceCriteriaTest.kt"

SRC_DIRS = ("app/src/main", "core", "feature")
BUILD_MARK = "/build/"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8", errors="replace")


def count(rel: str, needle: str) -> int:
    return read(rel).count(needle)


def literals(rel: str) -> list[str]:
    """抽取 Kotlin 双引号字面量内容（忽略 KDoc 与代码标识符）。"""
    return re.findall(r'"([^"\n]*)"', read(rel))


def main_sources(suffix: str = ".kt") -> list[Path]:
    """产品主源码：只走已知的 src/main 根，避开 build/.worktrees 的递归开销。"""
    files: list[Path] = []
    patterns = [
        f"app/src/main/**/*{suffix}",
        f"core/*/src/main/**/*{suffix}",
        f"feature/*/src/main/**/*{suffix}",
    ]
    for pattern in patterns:
        files.extend(path for path in ROOT.glob(pattern) if path.is_file())
    return files


def grep_main(pattern: str) -> list[str]:
    rx = re.compile(pattern)
    hits: list[str] = []
    for path in main_sources():
        for line_no, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            if rx.search(line):
                hits.append(f"{path.relative_to(ROOT).as_posix()}:{line_no}")
    return hits


def gradle_files() -> list[Path]:
    files: list[Path] = []
    patterns = [
        "build.gradle.kts", "settings.gradle.kts", "gradle/libs.versions.toml",
        "app/build.gradle.kts", "core/*/build.gradle.kts", "feature/*/build.gradle.kts",
    ]
    for pattern in patterns:
        for path in ROOT.glob(pattern):
            if path.is_file():
                files.append(path)
    return files


def h001() -> tuple[bool, str]:
    ok = (
        'internal const val AI_GENERATED_IMAGE_CAPTION = "本图由 AI 生成"' in read(CAPTION)
        and "AI" in read(CAPTION)
        and "生成" in read(CAPTION)
    )
    return ok, "固定文案常量存在且含 AI/生成 两要素"


def h002() -> tuple[bool, str]:
    text = read(PLANNER)
    ok = (
        text.count("if (isAiGeneratedExportImage(") >= 2
        and text.count("add(line(AI_GENERATED_IMAGE_CAPTION") >= 2
        and "PdfLineStyle.META" in text
    )
    return ok, "PDF 题目区图与块图两条分支都消费同一图注常量"


def h003() -> tuple[bool, str]:
    n = count(DOCX, "textParagraph(AI_GENERATED_IMAGE_CAPTION")
    return n >= 3, f"DOCX 图注调用点={n}（题目图/块图/解答图三条分支）"


def h004() -> tuple[bool, str]:
    text = read(CAPTION)
    ok = (
        "VisualRole.CLEAN_IMAGE.name," in text
        and "VisualRole.TUTOR_EXPLANATION.name," in text
        and "QUESTION_DIAGRAM" in text
        and "-> true" in text
        and "else -> false" in text
    )
    return ok, "CLEAN_IMAGE/解答图为真，QUESTION_DIAGRAM 等非生成角色为假"


def h005() -> tuple[bool, str]:
    hits = grep_main(r"AI 创作|智能辅助|由模型优化|AI创作")
    return not hits, f"产品主源码弱化措辞命中={len(hits)}"


def h006() -> tuple[bool, str]:
    hits = grep_main(r"showAigc|disableAigc|allowAigc|aigcEnabled|AIGC_ENABLED|captionEnabled")
    return not hits, f"标识开关类符号命中={len(hits)}（第四条义务性，不设关闭）"


def h007() -> tuple[bool, str]:
    definitions = sum(
        p.read_text(encoding="utf-8", errors="replace").count('= "本图由 AI 生成"')
        for p in main_sources()
    )
    consumers = count(PLANNER, "AI_GENERATED_IMAGE_CAPTION") + count(DOCX, "AI_GENERATED_IMAGE_CAPTION")
    ok = definitions == 1 and consumers >= 5
    return ok, f"字面定义={definitions}，常量消费点={consumers}"


def h008() -> tuple[bool, str]:
    ui_files = list((ROOT / "feature/tutor/src/main").rglob("*.kt")) + list((ROOT / "core/ui/src/main").rglob("*.kt"))
    hits = [p.as_posix() for p in ui_files if "AI 生成" in p.read_text(encoding="utf-8", errors="replace")]
    return bool(hits), f"讲题/配图界面显式角标命中={len(hits)}（G1/G2 接线未落地）"


def h009() -> tuple[bool, str]:
    ok = (
        count(AIGC_JVM_TEST, "@Test") >= 10
        and count(CAPTION_TEST, "@Test") >= 6
        and "const val GENERATION: Int = 2" in read(POLICY)
        and "aigc-policy-gen-${PdfAigcContentPolicy.GENERATION}" in read(EXPORTER)
    )
    return ok, "JVM 用例数与判据代际哨兵同期存在（11/6, gen=2）"


def h010() -> tuple[bool, str]:
    text = read(SCHEMA)
    ok = (
        "KEY_ORDER: List<String> = listOf(" in text
        and text.count("KEY_") >= 7
        and "val KEY_LABEL: String = \"Label\"" in text
        and "val KEY_PRODUCE_ID: String = \"ProduceID\"" in text
    )
    keys = re.findall(r'val KEY_[A-Z0-9_]+: String = "([A-Za-z0-9]+)"', text)
    return ok and keys == ["Label", "ContentProducer", "ProduceID", "ReservedCode1", "ContentPropagator", "PropagateID", "ReservedCode2"], f"7 键与顺序={keys}"


def h011() -> tuple[bool, str]:
    text = read(SCHEMA)
    ok = (
        'const val LABEL_AI_GENERATED: String = "1"' in text
        and "DOCUMENT_SHA256_PATTERN" in text
        and "[a-f0-9]{64}" in text
        and "PdfAigcMetadata.document(input.inputSha256)" in read(EXPORTER)
    )
    return ok, "Label=1；ProduceID=输入 sha256（64 位小写 hex）"


def h012() -> tuple[bool, str]:
    text = read(SCHEMA)
    ok = (
        'const val DEFAULT_CONTENT_PRODUCER: String = "SmartMistakeBook"' in text
        and "ContentProducer" in text
        and "validateForWrite" in text
        and "requirePrintableAscii" in text
    )
    return ok, "提供者常量 + 写前 ASCII/键集校验（fail-closed）"


def h013() -> tuple[bool, str]:
    inj = read(INJECTOR)
    reader = read(READER)
    ok = "/AIGC " in inj and "dictionary.entries[\"AIGC\"]" in inj and 'dictionary["AIGC"]' in reader
    return ok, "注入/读回都以 Info 字典 /AIGC 文本串为唯一载体"


def h014() -> tuple[bool, str]:
    ok = (
        "fun shouldLabel(" in read(POLICY)
        and "hasAiGeneratedImage || hasModelParsedText" in read(POLICY)
        and "MODEL_DOCUMENT_PARSE" in read(POLICY)
        and "blockEvidence" in read("core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfEligibility.kt")
    )
    return ok, "AI 图或 MODEL_DOCUMENT_PARSE 任一命中即打标，派生自 blockEvidence"


def h015() -> tuple[bool, str]:
    text = read(EXPORTER)
    ok = (
        "if (!PdfAigcContentPolicy.shouldLabel(" in text
        and "hasModelParsedText = input.hasModelParsedText" in text
        and "return\n    }" in text
    )
    return ok, "纯用户内容 shouldLabel=false 时早退，不改写字节"


def h016() -> tuple[bool, str]:
    text = read(METADATA)
    ok = (
        "TAG_IMAGE_DESCRIPTION, AI_GENERATED_IMAGE_DESCRIPTION" in text
        and "TAG_SOFTWARE" in text
        and "TAG_ARTIST" in text
        and "TAG_USER_COMMENT" in text
        and "TAG_XMP" in text
        and "aigcXmpPacket" in text
    )
    return ok, "图片隐式标识写 EXIF 四字段 + TC260 XMP，随文件字节走"


def h017() -> tuple[bool, str]:
    text = read(METADATA)
    ok = (
        "resolveGeneratedFigureMetadata" in text
        and "Label" in text
        and "AI 生成" in text
        and "aiGenerated=false" in text
    )
    return ok, "读回只认规范 Label==1；缺标识/伪造包按 false（fail-closed）"


def h018() -> tuple[bool, str]:
    docx_text = read(DOCX)
    kd = read(KD)
    gaps = []
    if "/AIGC" not in docx_text:
        gaps.append("DOCX 无文档级 /AIGC")
    if re.search(r"^## KD-84 \(open", kd, re.MULTILINE):
        gaps.append("KD-84 部分页打印丢标")
    if re.search(r"^## KD-85 \(open", kd, re.MULTILINE):
        gaps.append("KD-85 模型改卷文本漏标")
    covered = "/AIGC" in docx_text and not gaps
    return covered, "文件级隐式标识覆盖缺口：" + "；".join(gaps) if gaps else "全部交付格式与打印子集均有文件级隐式标识"


def h019() -> tuple[bool, str]:
    text = read(PRIV_FLOW)
    ok = (
        "local-first" in text
        and "API keys" in text
        and "unrelated mistakes" in text
        and "Backup archives" in text
    )
    return ok, "本地优先与“不发什么”清单在隐私数据流文件中"


def h020() -> tuple[bool, str]:
    text = read(PRIV_CURRENT)
    ok = all(k in text for k in ("In-app privacy disclosure", "Data deletion", "Play Data safety answers"))
    return ok, "应用内披露/删除/Play data safety 草案三节齐全"


def h021() -> tuple[bool, str]:
    text = read(SECONDARY)
    ok = (
        'SecondaryHeader(title = "数据与隐私"' in text
        and "本机数据清单" in text
        and "NetworkMode.LOCAL_FIRST" in text
        and "NetworkMode.STRICT_OFFLINE" in text
    )
    return ok, "应用内“数据与隐私”页按联网模式分支如实说明"


def h022() -> tuple[bool, str]:
    hits: list[str] = []
    rx = re.compile(r"(?i)firebase|admob|appsflyer|umeng|com\.google\.android\.gms\.ads|analytics")
    for path in gradle_files():
        for line_no, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            if rx.search(line):
                hits.append(f"{path.relative_to(ROOT).as_posix()}:{line_no}")
    return not hits, f"分析/广告 SDK 依赖命中={len(hits)}"


def h023() -> tuple[bool, str]:
    main = read(MANIFEST)
    local = read(MANIFEST_LOCAL)
    offline = read(MANIFEST_OFFLINE)
    ok = (
        "POST_NOTIFICATIONS" in main
        and "RECEIVE_BOOT_COMPLETED" in main
        and "android.permission.INTERNET" in local
        and "tools:node=\"remove\"" in offline
        and "android.permission.INTERNET" in offline
    )
    return ok, "主清单最小权限；联网 flavor 显式声明、离线 flavor 显式移除"


def h024() -> tuple[bool, str]:
    hits = grep_main(r"https?://[^\" ]*(privacy|policy)")
    return bool(hits), f"已发布隐私政策 URL 命中={len(hits)}（需 Play/开发者域名，外部阻塞）"


def h025() -> tuple[bool, str]:
    disclosure = read(DISCLOSURE)
    priv = read(PRIV_CURRENT) + read(PRIV_FLOW)
    ok = "未成年人个人信息保护章节" in disclosure and "未成年人" in priv
    return ok, "应用文案引用的未成年人专章在现有隐私文本中是否存在"


def h026() -> tuple[bool, str]:
    text = read(SECONDARY)
    ok = (
        "删除全部数据？" in text
        and "会删除本机数据库、题图、设置和临时文件" in text
        and "不会自动备份" in text
    )
    return ok, "删除确认文案与“数据库/题图/设置/临时文件”范围一致"


def h027() -> tuple[bool, str]:
    profile = read(PROFILE)
    ok = (
        'title = "数据与隐私"' in profile
        and 'testTag("profile_privacy_setting")' in profile
        and "onOpenDataPrivacy" in profile
    )
    return ok, "设置页入口与回调接线存在"


def h028() -> tuple[bool, str]:
    text = read(TELEMETRY)
    ok = (
        "TelemetryConsentState.UNKNOWN," in text
        and "val isEnabled: Boolean get() = state == TelemetryConsentState.GRANTED" in text
        and "telemetryBuildEnabled: Boolean = false" in text
    )
    return ok, "首装 UNKNOWN=OFF，构建开关默认 false"


def h029() -> tuple[bool, str]:
    text = read(TELEMETRY)
    ok = (
        'val endpoint: String = ""' in text
        and "build.endpoint.isNotBlank()" in text
        and "hasInternetPermission" in text
    )
    return ok, "v1 端点为空的 fail-closed 上传门"


def h030() -> tuple[bool, str]:
    text = read(ATTRS)
    ok = (
        "ALLOWED_KEYS" in text
        and "toWireMap" in text
        and not re.search(r"val [a-zA-Z]+: String\?", text)
        and "没有任何 `String` 字段" in text
    )
    return ok, "属性袋无自由文本字段，键集白名单固定"


def h031() -> tuple[bool, str]:
    text = read(TELEMETRY)
    ok = (
        "TelemetryAgeBand.UNDER_14 -> guardianConfirmed" in text
        and "TelemetryAgeBand.AGE_14_17 -> guardianConfirmed" in text
        and "TelemetryAgeBand.UNDISCLOSED, null -> false" in text
    )
    return ok, "14 岁以下与 14–17 均要求监护人确认；未披露年龄 fail-closed"


def h032() -> tuple[bool, str]:
    text = read(TELEMETRY)
    test = read(TELEMETRY_TEST)
    ok = (
        "consent.policyVersion == currentPolicy.version" in text
        and "consent.policyHash == currentPolicy.hash" in text
        and "withdraw" in test
        and "V6 under14 without guardian is blocked" in test
    )
    return ok, "政策版本/哈希一致才放行；撤回与年龄门有用例"


def h033() -> tuple[bool, str]:
    import os
    import subprocess

    env = dict(os.environ, PYTHONIOENCODING="utf-8")
    proc = subprocess.run(
        [sys.executable, "-I", "-X", "utf8", str(ROOT / "tools/ci/check_telemetry_compliance.py")],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env,
    )
    tail = [line for line in proc.stdout.splitlines() if line.strip()][-1:] or [""]
    return proc.returncode == 0, f"check_telemetry_compliance exit={proc.returncode}；{tail[0]}"


def h034() -> tuple[bool, str]:
    text = read(PRIV_CURRENT)
    ok = (
        "Data collected: none by the app itself" in text
        and 'val endpoint: String = ""' in read(TELEMETRY)
    )
    return ok, "data safety“未收集”与 v1 无上传端点一致"


def h035() -> tuple[bool, str]:
    main = read(MANIFEST)
    rules = read(BACKUP_RULES) + read(EXTRACT_RULES)
    ok = (
        'android:allowBackup="false"' in main
        and rules.count('<exclude domain="database" path="." />') >= 3
        and "disableIfNoEncryptionCapabilities" in rules
    )
    return ok, "系统备份/迁移关闭，全部域排除；备份仅走用户显式导出"


def h036() -> tuple[bool, str]:
    return False, "Play Console data safety 表单需开发者账号与已发布 URL，外部阻塞"


def h037() -> tuple[bool, str]:
    text = read(DECISIONS)
    return ("G7 未成年人叠加" in text and "裁定 2026-10-25" in text), "G7 最严解释对齐裁定已登记"


def h038() -> tuple[bool, str]:
    text = read(DECISIONS)
    ok = "完整“未成年人模式”**不建设**" in text or "完整\"未成年人模式\"**不建设**" in text
    return ok, "完整模式不建设的理由（无内容池/推荐流/消费场景）已留痕"


def h039() -> tuple[bool, str]:
    hits = grep_main(r"guardianConsent|guardian_consent|监护人同意")
    return bool([h for h in hits if "telemetry" not in h]), f"产品侧监护人同意机制命中={len(hits)}（仅遥测门）"


def h040() -> tuple[bool, str]:
    hits = grep_main(r"年龄确认|ageConfirm|AgeConfirm|UNDER_14")
    ui = [h for h in hits if "feature/" in h]
    return bool(ui), f"年龄确认 UI 命中={len(ui)}（落地批次待排）"


def h041() -> tuple[bool, str]:
    hits = grep_main(r"家长须知|家长模式|监护人说明")
    return bool(hits), f"“家长须知”入口命中={len(hits)}"


def h042() -> tuple[bool, str]:
    hits = grep_main(r"累计使用时长|使用时长提醒|22:00|静默时段")
    return bool(hits), f"防沉迷最小集（时长提醒/静默时段）命中={len(hits)}"


def h043() -> tuple[bool, str]:
    priv = read(PRIV_CURRENT)
    return "未成年人个人信息" in priv, "隐私文本中的未成年人专章"


def h044() -> tuple[bool, str]:
    ui_sources = [
        p for p in main_sources()
        if p.as_posix().startswith(("/app/src/main", "/feature/")) or "/feature/" in p.as_posix()
    ]
    hits = [
        literal
        for path in ui_sources
        for literal in re.findall(r'"([^"\n]*)"', path.read_text(encoding="utf-8", errors="replace"))
        if re.search(r"私信|好友|关注|评论|聊天室", literal)
    ]
    return not hits, f"界面模块社交/私信/评论文案命中={len(hits)}"


def h045() -> tuple[bool, str]:
    text = read(TELEMETRY_TEST)
    ok = "V6 under14 without guardian is blocked and undisclosed age fails closed" in text
    return ok, "14 岁以下无监护人拒绝的用例在树"


def h046() -> tuple[bool, str]:
    text = read(DISCLOSURE)
    ok = all(k in text for k in ("## 1. 一句话版", "## 2. 一段版", "## 3. 完整版"))
    return ok, "首启/关于/完整三档文案齐全"


def h047() -> tuple[bool, str]:
    text = read(DISCLOSURE)
    ok = "措辞统一用\"AI 生成\"" in text and "AI 创作" in text and "不设用户关闭开关" in text
    return ok, "固定措辞与不弱化规则已写死"


def h048() -> tuple[bool, str]:
    text = read(DISCLOSURE)
    ok = "可能存在错误或偏差" in text and "不作为答案的唯一依据" in text
    return ok, "免责表述存在且指向教材/老师"


def h049() -> tuple[bool, str]:
    text = read(DISCLOSURE)
    ok = "未成年人使用提示" in text and "产品主动说明" in text and "不是《标识办法》的法定义务" in text
    return ok, "未成年人提示按主动说明表述，不冒充法定义务"


def h050() -> tuple[bool, str]:
    text = read(DISCLOSURE)
    ok = "不得恶意删除、篡改、伪造、隐匿标识" in text and "标识不能被关闭或去除" in text
    return ok, "第十条红线与不可去除承诺在文案中"


def h051() -> tuple[bool, str]:
    about = grep_main(r"关于本应用|AI 生成内容说明|ai_disclosure")
    return bool(about), f"文案嵌入点（关于页/首启弹窗）命中={len(about)}（热区未空出）"


def h052() -> tuple[bool, str]:
    text = read(DISCLOSURE)
    stale = "尚未写入文件级隐式信息" in text and "PDF 不保留文件级隐式元数据" in text
    implemented = "PdfAigcInjector().inject" in read(EXPORTER)
    return not (stale and implemented), "文案与 PDF 隐式标识现状一致（KD-75 已关闭；KD-84/KD-85 仍开放）"


def h053() -> tuple[bool, str]:
    text = read(DISCLOSURE)
    return "【备案号】" not in text and "【上线编号】" not in text and "【模型名称】" not in text, "备案号/上线编号/模型名占位符是否回填"


def h054() -> tuple[bool, str]:
    text = read(DISCLOSURE)
    ok = "GB 45438-2025" in text and "2025-09-01 施行" in text and "深度合成管理规定" in text
    return ok, "双轨法规基线与施行日期引用"


def h055() -> tuple[bool, str]:
    return ("isAiGeneratedExportImage(input.questionImageRole)" in read(PLANNER)), "单题题目区图注分支"


def h056() -> tuple[bool, str]:
    planner = read(PLANNER)
    return ("if (isAiGeneratedExportImage(block.role))" in planner), "批量/卷面块级图注按角色逐图判断"


def h057() -> tuple[bool, str]:
    text = read("core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEdit.kt")
    return ("AI_GENERATED_IMAGE_CAPTION" in read(PLANNER) and "MistakePdfPaperEdit" in text), "改卷面走同一 planner，图注不丢"


def h058() -> tuple[bool, str]:
    meta = read(METADATA)
    test = read("core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportVisualRoleTest.kt")
    return ("原字节嵌入" in meta and "docx embeds the question image" in test), "DOCX 原字节嵌入保留图片隐式元数据"


def h059() -> tuple[bool, str]:
    return ("QUESTION_DIAGRAM" in read(CAPTION) and "else -> false" in read(CAPTION)), "非生成角色不伪标"


def h060() -> tuple[bool, str]:
    text = read(CAPTION)
    return ("不进 `inputSha256`" in text or "不进 inputSha256" in text) and "META" in read(PLANNER), "图注渲染时派生，不进输入指纹"


def h061() -> tuple[bool, str]:
    delivery = read(AIGC_DELIVERY)
    device_test = read(AIGC_DEVICE_TEST)
    ok = (
        "relabelSubset(prepared, subsetBytes)" in delivery
        and "prepared.aigcMetadataJson" in delivery
        and "PdfAigcInjector().inject(subsetBytes, metadataJson)" in delivery
        and "partial-page print must keep the document-level /AIGC" in device_test
        and "prepared.inputSha256, subsetReadBack.produceId" in device_test
        and "print-subset-relabeled.pdf" in device_test
    )
    return ok, (
        "部分页打印子集按 prepared 同一份 /AIGC JSON 重注入（KD-84）；"
        "设备用例断言 Label/ProduceID/页数并落盘子集供宿主 pypdf 独立读回"
        if ok else
        "部分页打印 writeSubset 未按 prepared 的 /AIGC 重注入（KD-84 回退）"
    )


def h062() -> tuple[bool, str]:
    kd = read(KD)
    if re.search(r"^## KD-85 \(open", kd, re.MULTILINE):
        return False, "模型改卷文本未纳入 /AIGC 判据（KD-85 open；修复落点跨 feature/tutor 热区，需主会话授权）"
    paper = read(PAPER_EDIT)
    ok = "modelTextApplied" in paper and "sourceInput.hasModelParsedText || modelTextApplied" in paper
    return ok, (
        "模型改卷文本已纳入 /AIGC 判据（KD-85 closed 且 MistakePdfPaperEdit 带模型文本置位）"
        if ok else
        "KD-85 已关闭但 MistakePdfPaperEdit 缺少模型改卷文本置位，判据未落码"
    )


def h063() -> tuple[bool, str]:
    return False, "图注在真实设备/PDF 上的字号、对比度、不遮挡属人工视觉复核"


def h064() -> tuple[bool, str]:
    text = read(COPY)
    bad = re.findall(r"Exception|Error:|错误码|堆栈|NullPointer", text)
    return (not bad and text.count("。") >= 10), f"技术术语命中={len(bad)}，中文句号条数={text.count('。')}"


def h065() -> tuple[bool, str]:
    text = read(COPY)
    ok = (
        '"这道题的内容超出了单题 A4 导出的范围，题目仍会原样保留。"' in text
        and '"这道题的内容超出了单题 Word 文档的范围，题目仍会原样保留。"' in text
        and '"这次没有整理出完整的 A4 文件，请稍后重新导出。"' in text
        and '"这次没有整理出完整的 Word 文档，请稍后重新导出。"' in text
    )
    return ok, "PDF 文案说 A4、DOCX 文案说 Word，两套交付物各自成句"


def h066() -> tuple[bool, str]:
    """H-066：诊断包导出失败时，学生可见文案不得拼接原始异常 message。

    批次 46 修掉硬编码第 826 行（那只是 `}`，永远不含 failure.message，属假通过）。
    改为按内容定位导出回调：在 `exportDiagnostics` 的 rememberLauncher 块内，
    断言 (a) 固定失败文案存在，(b) 该块不出现异常 message 拼接，(c) 取消异常
    仍被重抛（不被吞进文案）。
    """
    text = read(SECONDARY)
    start = text.find("val exportDiagnostics = rememberLauncherForActivityResult(")
    end = text.find("if (confirmDelete)", start + 1)
    if start < 0 or end <= start:
        return False, "找不到 exportDiagnostics 回调块（内容锚点失效）"
    block = text[start:end]
    fixed_copy = "导出失败，本机记录不受影响。请稍后重试。"
    leak_patterns = (
        r"diagnosticsMessage\s*=\s*[^\n]*\b(?:failure|exception|error|throwable)\.message",
        r"diagnosticsMessage\s*=\s*[^\n]*\$\{[^}]*\.message",
    )
    leaks = [pattern for pattern in leak_patterns if re.search(pattern, block)]
    rethrows_cancel = ("catch (cancelled: CancellationException)" in block
                       and "throw cancelled" in block)
    ok = fixed_copy in block and not leaks and rethrows_cancel
    return ok, (f"固定文案={fixed_copy in block} 拼接命中={len(leaks)} "
                f"取消重抛={rethrows_cancel}")


def h067() -> tuple[bool, str]:
    text = read(COPY)
    exporter = read(EXPORTER)
    ok = "没有整理出完整" in text and "请稍后重新导出" in text and "pending" in exporter
    return ok, "失败文案说“未产出完整文件”且导出层删 pending 不发布"


def h068() -> tuple[bool, str]:
    text = read(COPY)
    bad = re.findall(r"sha256|content://|/data/|API[_ ]?KEY|Bearer", text)
    return not bad, f"导出文案中的敏感/内部标识命中={len(bad)}"


def h069() -> tuple[bool, str]:
    text = read(COPY_TEST)
    ok = text.count("@Test") >= 5 and "every render failure and batch reason has copy" in text
    return ok, "导出文案契约用例覆盖全部失败枚举"


def h070() -> tuple[bool, str]:
    text = read(NOTIFICATIONS)
    ok = 'title = "这次导出没有完成"' in text and "text = failureMessage" in text and "VISIBILITY_PRIVATE" in text
    return ok, "失败通知标题/正文为学生可读文案，锁屏私有"


def h071() -> tuple[bool, str]:
    text = read(EXPORTER)
    ok = (
        "catch (failure: PdfAigcInjectionException)" in text
        and "GENERATED_PDF_INVALID" in text
        and "pending" in text
    )
    return ok, "注入失败映射 GENERATED_PDF_INVALID，不产出无标记文件"


def h072() -> tuple[bool, str]:
    chinese = [
        lit for lit in literals(COPY)
        if re.search(r"[\u4e00-\u9fff]", lit) and "${" not in lit
    ]
    text = " ".join(chinese)
    english = re.findall(r"\b(?!PDF\b|Word\b|A4\b)[A-Za-z]{5,}\b", text)
    return not english, f"中文文案中的英文整词={sorted(set(english))[:5]}"


def h073() -> tuple[bool, str]:
    text = read(STRINGS)
    return ('<string name="app_name">智能错题本</string>' in text and "android:label=\"@string/app_name\"" in read(MANIFEST)), "应用名资源与清单引用一致"


def h074() -> tuple[bool, str]:
    res = ROOT / "app/src/main/res"
    qualifiers = sorted(p.name for p in res.glob("values-*"))
    return not qualifiers, f"额外 locale 资源目录={qualifiers}（产品当前仅中文）"


def h075() -> tuple[bool, str]:
    hits = grep_main(r'Text\("')
    return not hits, f"硬编码 Text 字面量文件命中={len(hits)}（未做资源抽取）"


def h076() -> tuple[bool, str]:
    return False, "supportsRtl=true 但无 RTL 布局/文案走查证据"


def h077() -> tuple[bool, str]:
    hits = [h for h in grep_main(r"SimpleDateFormat|DateTimeFormatter") if "feature/" in h]
    return not hits, f"界面日期格式硬编码点={len(hits)}（未做本地化格式抽取）"


def h078() -> tuple[bool, str]:
    res = ROOT / "app/src/main/res"
    plurals = [p.as_posix() for p in res.rglob("*.xml") if "<plurals" in p.read_text(encoding="utf-8", errors="replace")]
    return not plurals, f"plurals 资源={len(plurals)}（中文无复数，N/A）"


def h079() -> tuple[bool, str]:
    chinese = [
        lit for lit in literals(COPY) + literals(SECONDARY)
        if re.search(r"[\u4e00-\u9fff]", lit) and "${" not in lit
    ]
    text = " ".join(chinese)
    # 只把“连续 3 个以上英文单词”视为未翻译英文句子；OpenAI/PDF/A4 等品牌与格式名不算。
    sentences = re.findall(r"\b[A-Za-z]{3,}(?:\s+[A-Za-z]{3,}){2,}\b", text)
    return not sentences, f"未翻译英文句子={sentences[:3]}"


def h080() -> tuple[bool, str]:
    return False, "无应用内语言切换；系统 locale 变更后的行为未测"


def h081() -> tuple[bool, str]:
    return False, "英文商店详情/隐私政策本地化未产出（发布工程外部依赖）"


def h082() -> tuple[bool, str]:
    text = read(COPY) + read(DISCLOSURE)
    return "AI 生成" in text and "AI生成" not in text, "中英混排保留“AI 生成”空格"


def h083() -> tuple[bool, str]:
    text = read(COPY) + read(DISCLOSURE)
    bad = re.findall(r"[\u4e00-\u9fff][,;][\u4e00-\u9fff]", text)
    return not bad, f"中文间半角逗号/分号命中={len(bad)}"


def h084() -> tuple[bool, str]:
    text = read(COPY) + read(DISCLOSURE)
    return "..." not in text, f"半角省略号命中={text.count('...')}"


def h085() -> tuple[bool, str]:
    text = read(COPY) + read(DISCLOSURE)
    bad = re.findall(r"[\u4e00-\u9fff][?!:][\u4e00-\u9fff]", text)
    return not bad, f"中文间半角问号/叹号/冒号命中={len(bad)}"


def h086() -> tuple[bool, str]:
    text = read(COPY) + read(DISCLOSURE)
    bad = re.findall(r"[Ａ-Ｚａ-ｚ０-９]", text)
    return not bad, f"全角拉丁/数字命中={len(bad)}"


def h087() -> tuple[bool, str]:
    text = read(COPY) + read(DISCLOSURE)
    variants = re.findall(r"AI生成|人工智能创作", text)
    return not variants, f"“AI 生成”无空格变体命中={len(variants)}"


def h088() -> tuple[bool, str]:
    text = read(COPY) + read(DISCLOSURE)
    return "\u3000" not in text and "\t" not in text, "用户可见文案无全角空格/Tab"


def h089() -> tuple[bool, str]:
    return False, "错误文案语感与信息密度属人工/母语者复核"


def h090() -> tuple[bool, str]:
    return False, "设备字体回退、标点宽度与暗色可读性需截图/TalkBack 走查"


CHECKS = {
    1: h001, 2: h002, 3: h003, 4: h004, 5: h005, 6: h006, 7: h007, 8: h008, 9: h009,
    10: h010, 11: h011, 12: h012, 13: h013, 14: h014, 15: h015, 16: h016, 17: h017, 18: h018,
    19: h019, 20: h020, 21: h021, 22: h022, 23: h023, 24: h024, 25: h025, 26: h026, 27: h027,
    28: h028, 29: h029, 30: h030, 31: h031, 32: h032, 33: h033, 34: h034, 35: h035, 36: h036,
    37: h037, 38: h038, 39: h039, 40: h040, 41: h041, 42: h042, 43: h043, 44: h044, 45: h045,
    46: h046, 47: h047, 48: h048, 49: h049, 50: h050, 51: h051, 52: h052, 53: h053, 54: h054,
    55: h055, 56: h056, 57: h057, 58: h058, 59: h059, 60: h060, 61: h061, 62: h062, 63: h063,
    64: h064, 65: h065, 66: h066, 67: h067, 68: h068, 69: h069, 70: h070, 71: h071, 72: h072,
    73: h073, 74: h074, 75: h075, 76: h076, 77: h077, 78: h078, 79: h079, 80: h080, 81: h081,
    82: h082, 83: h083, 84: h084, 85: h085, 86: h086, 87: h087, 88: h088, 89: h089, 90: h090,
}

# 未落地/外部依赖/人工残余：这些编号 FAIL 是“缺口如实暴露”，不是脚本故障。
GAPS = {8, 18, 24, 25, 36, 39, 40, 41, 42, 43, 51, 52, 53, 61, 62, 63, 66,
        75, 76, 77, 80, 81, 89, 90}


def run(ids: list[int]) -> int:
    failures = 0
    for number in ids:
        ok, detail = CHECKS[number]()
        print(f"H-{number:03d} {'PASS' if ok else 'FAIL'} {detail}")
        if not ok:
            failures += 1
    print(f"EXIT={0 if failures == 0 else 1}")
    return 0 if failures == 0 else 1


def main(argv: list[str] | None = None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="H 域合规与文案可执行门")
    parser.add_argument("--topic", choices=sorted(TOPICS))
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--only", help="逗号分隔的 H-NNN 编号，仅跑这些")
    args = parser.parse_args(argv)

    if args.only:
        ids = sorted(int(token.strip().split("-")[-1]) for token in args.only.split(",") if token.strip())
    elif args.topic:
        ids = TOPICS[args.topic]
    elif args.all:
        ids = sorted(CHECKS)
    else:
        parser.print_help()
        return 2
    return run(ids)


if __name__ == "__main__":
    sys.exit(main())
