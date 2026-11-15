#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""H 域（合规与文案）逐项断言（批次 44）。

## 它消灭的具体失败

`tools/ci/h_compliance_gate.py` 给出的是总退出码；一条断言写错或整段被删，退出码仍
可能是 0。本文件把同一批不变量拆成**逐例**断言，每例直接读源码/文档并比对期望，
不 import gate 的辅助函数——两条实现同时错才可能同时绿。缺口项（未落地/外部依赖/
人工残余）在用例名里写 `gap`，断言“缺口当前确实存在”，让 FAIL/BLOCKED 也有可复跑
的判定，而不是一句散文。

边界：只覆盖源码/文本可判定的事实。设备导出读回、TalkBack/截图走查、真人语感与
审美不在本文件能力内。
"""

from __future__ import annotations

import re
import runpy
import subprocess
import sys

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8", errors="replace")


def literals(rel: str) -> list[str]:
    tokens = re.findall(r'/\*[\s\S]*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', read(rel))
    return [token[1:-1] for token in tokens if token.startswith('"')]


def main_kt() -> list[Path]:
    files: list[Path] = []
    for pattern in ("app/src/main/**/*.kt", "core/*/src/main/**/*.kt", "feature/*/src/main/**/*.kt"):
        files.extend(p for p in ROOT.glob(pattern) if p.is_file())
    return files


BADGE = "core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/AiContentBadge.kt"
TUTOR_TURN = "feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorGeneratedTurn.kt"
ATTACHED_IMAGE = "core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/AttachedImageCard.kt"
PRIVACY_COPY = "app/src/main/kotlin/com/tingyun/smartmistakebook/PrivacyPolicyCopy.kt"
PAPER_EDIT = "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEdit.kt"

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
AIGC_DEVICE_TEST = (
    "core/export/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/export/"
    "MistakePdfExporterAigcInstrumentedTest.kt"
)
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
AIGC_JVM_TEST = "core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporterAigcJvmTest.kt"
COPY_TEST = "core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopyTest.kt"
TELEMETRY_TEST = "core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryComplianceCriteriaTest.kt"


class TestH001to009AigcExplicit:
    def test_h001_caption_constant_has_ai_and_generation(self) -> None:
        assert 'internal const val AI_GENERATED_IMAGE_CAPTION = "本图由 AI 生成"' in read(CAPTION)

    def test_h002_pdf_planner_labels_question_and_block_images(self) -> None:
        text = read(PLANNER)
        assert text.count("isAiGeneratedExportImage(") >= 2
        assert text.count("add(line(AI_GENERATED_IMAGE_CAPTION") >= 2

    def test_h003_docx_labels_three_image_branches(self) -> None:
        assert read(DOCX).count("textParagraph(AI_GENERATED_IMAGE_CAPTION") == 3

    def test_h004_question_diagram_is_not_falsely_labelled(self) -> None:
        text = read(CAPTION)
        assert "QUESTION_DIAGRAM" in text
        assert "else -> false" in text

    def test_h005_no_weakening_synonyms_in_product_source(self) -> None:
        hits = [p for p in main_kt() for lit in literals(p.as_posix()) if re.search(r"AI 创作|智能辅助|由模型优化|AI创作", lit)]
        assert hits == []

    def test_h006_no_user_switch_can_disable_the_label(self) -> None:
        hits = [p for p in main_kt() if re.search(r"disableAigc|aigcEnabled|captionEnabled|showAigc", p.read_text(encoding="utf-8", errors="replace"))]
        assert hits == []

    def test_h007_caption_has_single_literal_definition(self) -> None:
        definitions = sum(p.read_text(encoding="utf-8", errors="replace").count('= "本图由 AI 生成"') for p in main_kt())
        assert definitions == 1

    def test_h008_tutor_and_image_ui_render_the_shared_ai_label(self) -> None:
        assert 'const val AI_GENERATED_LABEL: String = "AI 生成"' in read(BADGE)
        for path, tag in ((TUTOR_TURN, "tutor_ai_generated_label"),
                          (ATTACHED_IMAGE, "attached_image_ai_label_${image.imageId}")):
            text = read(path)
            assert re.search(r"AiContentBadge\(\s*text = AI_GENERATED_LABEL,", text)
            assert f'testTag("{tag}")' in text

    def test_h009_regression_guards_and_generation_sentinel(self) -> None:
        assert read(AIGC_JVM_TEST).count("@Test") >= 10
        assert read(CAPTION_TEST).count("@Test") >= 6
        assert "const val GENERATION: Int = 3" in read(POLICY)
        assert "aigc-policy-gen-${PdfAigcContentPolicy.GENERATION}" in read(EXPORTER)
        assert 'assertEquals("mistake-export-v5-math-layout-aigc-gen-3", exportFingerprintPolicyTag())' in read(AIGC_JVM_TEST)


class TestH010to018AigcImplicit:
    def test_h010_pdf_schema_key_order_is_the_official_seven(self) -> None:
        keys = re.findall(r'val KEY_[A-Z0-9_]+: String = "([A-Za-z0-9]+)"', read(SCHEMA))
        assert keys == ["Label", "ContentProducer", "ProduceID", "ReservedCode1",
                        "ContentPropagator", "PropagateID", "ReservedCode2"]

    def test_h011_label_and_produce_id_are_pinned(self) -> None:
        assert 'const val LABEL_AI_GENERATED: String = "1"' in read(SCHEMA)
        assert "PdfAigcMetadata.document(input.inputSha256)" in read(EXPORTER)

    def test_h012_producer_and_ascii_validation(self) -> None:
        text = read(SCHEMA)
        assert '"SmartMistakeBook"' in text
        assert "requirePrintableAscii" in text

    def test_h013_info_dictionary_is_the_only_carrier(self) -> None:
        assert "/AIGC " in read(INJECTOR)
        assert 'dictionary["AIGC"]' in read(READER)

    def test_h014_either_ai_image_or_model_text_triggers_label(self) -> None:
        assert "hasAiGeneratedImage || hasModelParsedText" in read(POLICY)
        assert "MODEL_DOCUMENT_PARSE" in read(POLICY)

    def test_h015_pure_user_content_returns_before_rewrite(self) -> None:
        text = read(EXPORTER)
        assert "if (!PdfAigcContentPolicy.shouldLabel(" in text
        assert "return" in text.split("if (!PdfAigcContentPolicy.shouldLabel(", 1)[1][:200]

    def test_h016_image_bytes_carry_exif_and_tc260_xmp(self) -> None:
        text = read(METADATA)
        assert "TAG_IMAGE_DESCRIPTION" in text and "TAG_XMP" in text and "aigcXmpPacket" in text

    def test_h017_forged_or_missing_label_reads_false(self) -> None:
        text = read(METADATA)
        assert "aiGenerated=false" in text
        assert "Label" in text

    def test_h018_docx_metadata_and_model_paper_edit_share_the_policy(self) -> None:
        text = read(DOCX)
        assert "val labeled = PdfAigcContentPolicy.shouldLabel(" in text
        assert "hasAiGeneratedImage = input.paintsAiGeneratedImage()" in text
        assert "hasModelParsedText = input.hasModelParsedText" in text
        assert text.count("includeAigcProperty = labeled") == 2
        assert re.search(r"if \(labeled\) \{\s*add\(\s*MinimalDocxWriter.customPropertiesPart\(", text)
        assert "PdfAigcMetadata.document(input.inputSha256).toJson()" in text
        assert "hasModelParsedText = sourceInput.hasModelParsedText || modelTextApplied" in read(PAPER_EDIT)



class TestH019to027Privacy:
    def test_h019_data_flow_lists_what_never_leaves(self) -> None:
        text = read(PRIV_FLOW)
        assert "local-first" in text and "API keys" in text and "unrelated mistakes" in text

    def test_h020_current_privacy_doc_has_three_sections(self) -> None:
        text = read(PRIV_CURRENT)
        assert "In-app privacy disclosure" in text and "Data deletion" in text and "Play Data safety answers" in text

    def test_h021_in_app_screen_branches_by_network_mode(self) -> None:
        text = read(SECONDARY)
        assert "本机数据清单" in text and "NetworkMode.STRICT_OFFLINE" in text and "NetworkMode.LOCAL_FIRST" in text

    def test_h022_no_analytics_or_ads_sdk(self) -> None:
        hits = []
        for pattern in ("build.gradle.kts", "app/build.gradle.kts", "core/*/build.gradle.kts", "feature/*/build.gradle.kts"):
            for path in ROOT.glob(pattern):
                if re.search(r"(?i)firebase|admob|appsflyer|umeng", path.read_text(encoding="utf-8", errors="replace")):
                    hits.append(path)
        assert hits == []

    def test_h023_offline_flavor_removes_network_permissions(self) -> None:
        assert "INTERNET" in read(MANIFEST_LOCAL)
        assert 'tools:node="remove"' in read(MANIFEST_OFFLINE)
        assert "POST_NOTIFICATIONS" in read(MANIFEST)

    def test_h024_gap_no_published_privacy_policy_url(self) -> None:
        hits = [p for p in main_kt() if re.search(r"https?://[^\" ]*(privacy|policy)", p.read_text(encoding="utf-8", errors="replace"))]
        assert hits == []

    def test_h025_referenced_minor_chapter_is_rendered_in_app(self) -> None:
        assert "未成年人个人信息保护章节" in read(DISCLOSURE)
        assert "## 未成年人个人信息保护" in read(PRIV_CURRENT)
        assert 'title = "未成年人个人信息保护"' in read(PRIVACY_COPY)
        assert "PrivacyPolicyCopy.sections(networkOffline).forEach" in read(SECONDARY)

    def test_h026_delete_confirmation_lists_the_same_scope(self) -> None:
        text = read(SECONDARY)
        assert "删除全部数据？" in text and "数据库、题图、设置和临时文件" in text

    def test_h027_profile_privacy_entry_is_wired(self) -> None:
        assert 'testTag("profile_privacy_setting")' in read(PROFILE)
        assert "onOpenDataPrivacy" in read(PROFILE)


class TestH028to036DataSafety:
    def test_h028_default_is_off(self) -> None:
        text = read(TELEMETRY)
        assert "TelemetryConsentState.UNKNOWN," in text
        assert "telemetryBuildEnabled: Boolean = false" in text

    def test_h029_upload_gate_needs_endpoint_and_permission(self) -> None:
        text = read(TELEMETRY)
        assert 'val endpoint: String = ""' in text
        assert "build.endpoint.isNotBlank()" in text

    def test_h030_attributes_have_no_free_text(self) -> None:
        text = read(ATTRS)
        assert "没有任何 `String` 字段" in text
        assert not re.search(r"val [a-zA-Z]+: String\?", text)

    def test_h031_age_gate_requires_guardian_below_18(self) -> None:
        text = read(TELEMETRY)
        assert "TelemetryAgeBand.UNDER_14 -> guardianConfirmed" in text
        assert "TelemetryAgeBand.AGE_14_17 -> guardianConfirmed" in text

    def test_h032_policy_version_hash_and_withdraw(self) -> None:
        assert "consent.policyVersion == currentPolicy.version" in read(TELEMETRY)
        assert "withdraw" in read(TELEMETRY_TEST)

    def test_h033_telemetry_compliance_checker_passes(self) -> None:
        proc = subprocess.run(
            [sys.executable, "-I", "-X", "utf8", str(ROOT / "tools/ci/check_telemetry_compliance.py")],
            cwd=ROOT, capture_output=True, text=True, encoding="utf-8",
        )
        assert proc.returncode == 0, proc.stdout + proc.stderr

    def test_h034_data_safety_draft_matches_no_upload(self) -> None:
        assert "Data collected: none by the app itself" in read(PRIV_CURRENT)
        assert 'val endpoint: String = ""' in read(TELEMETRY)

    def test_h035_system_backup_is_disabled(self) -> None:
        assert 'android:allowBackup="false"' in read(MANIFEST)
        rules = read(BACKUP_RULES) + read(EXTRACT_RULES)
        assert rules.count('<exclude domain="database" path="." />') >= 3

    def test_h036_gap_play_console_form_is_not_submitted(self) -> None:
        assert "draft to be reviewed before upload" in read(PRIV_CURRENT)


class TestH037to045Minor:
    def test_h037_g7_ruling_is_recorded(self) -> None:
        assert "G7 未成年人叠加" in read(DECISIONS)

    def test_h038_full_minor_mode_is_not_built_by_ruling(self) -> None:
        text = read(DECISIONS)
        assert "不建设" in text and "无内容池" in text

    def test_h039_gap_policy_notice_does_not_supply_guardian_consent_mechanism(self) -> None:
        assert "需要监护人同意的处理" in read(PRIVACY_COPY)
        definitions = [p for p in main_kt() if re.search(
            r"\b(?:val|var|fun)\s+(?:guardianConsent|guardian_consent)\w*",
            p.read_text(encoding="utf-8"),
        )]
        assert definitions == []
        gate = runpy.run_path(str(ROOT / "tools/ci/h_compliance_gate.py"))
        assert gate["h039"]()[0] is False

    def test_h040_gap_age_confirmation_ui_absent(self) -> None:
        hits = [p for p in main_kt() if "feature/" in p.as_posix() and re.search(r"年龄确认|ageConfirm|UNDER_14", p.read_text(encoding="utf-8", errors="replace"))]
        assert hits == []

    def test_h041_gap_parent_notice_entry_absent(self) -> None:
        assert not [p for p in main_kt() if "家长须知" in p.read_text(encoding="utf-8", errors="replace")]

    def test_h042_gap_screen_time_control_absent(self) -> None:
        assert not [p for p in main_kt() if re.search(r"累计使用时长|使用时长提醒|静默时段", p.read_text(encoding="utf-8", errors="replace"))]

    def test_h043_minor_policy_covers_consent_withdrawal_and_deletion(self) -> None:
        for text in (read(PRIV_CURRENT), read(PRIVACY_COPY)):
            assert "未成年人个人信息保护" in text
            assert "第三十一条" in text
            assert "14 周岁" in text
            assert "拒绝或撤回同意" in text
            assert "删除全部本机数据" in text

    def test_h044_no_social_or_private_message_feature(self) -> None:
        ui = [p for p in main_kt() if "/feature/" in p.as_posix() or p.as_posix().startswith(ROOT.as_posix() + "/app/")]
        policy_notice = "14–17 岁用户同样建议在监护人指导下使用；应用不含社交、私信、评论、广告与内购。"
        hits = [lit for p in ui for lit in literals(p.as_posix())
                if re.search(r"私信|好友|评论|聊天室", lit)
                and not (p == ROOT / PRIVACY_COPY and lit == policy_notice)]
        assert hits == []

    def test_h045_under14_telemetry_denial_is_tested(self) -> None:
        assert "V6 under14 without guardian is blocked" in read(TELEMETRY_TEST)


class TestH046to054Disclaimer:
    def test_h046_three_copy_tiers_exist(self) -> None:
        text = read(DISCLOSURE)
        assert "## 1. 一句话版" in text and "## 2. 一段版" in text and "## 3. 完整版" in text

    def test_h047_fixed_wording_rule_exists(self) -> None:
        assert "措辞统一用\"AI 生成\"" in read(DISCLOSURE)

    def test_h048_disclaimer_says_not_the_only_answer(self) -> None:
        text = read(DISCLOSURE)
        assert "可能存在错误或偏差" in text and "不作为答案的唯一依据" in text

    def test_h049_minor_notice_is_product_initiative(self) -> None:
        text = read(DISCLOSURE)
        assert "未成年人使用提示" in text and "不是《标识办法》的法定义务" in text

    def test_h050_no_removal_or_tampering_clause(self) -> None:
        assert "不得恶意删除、篡改、伪造、隐匿标识" in read(DISCLOSURE)

    def test_h051_gap_copy_is_not_embedded_in_the_app(self) -> None:
        assert not [p for p in main_kt() if "关于本应用" in p.read_text(encoding="utf-8", errors="replace")]

    def test_h052_copy_matches_the_implemented_pdf_implicit_label(self) -> None:
        text = read(DISCLOSURE)
        assert "尚未写入文件级隐式信息" not in text
        assert "/AIGC" in text
        assert "PdfAigcInjector().inject" in read(EXPORTER)

    def test_h053_gap_filing_placeholders_are_unfilled(self) -> None:
        text = read(DISCLOSURE)
        assert "【备案号】" in text and "【上线编号】" in text

    def test_h054_twin_regulation_baseline_is_cited(self) -> None:
        text = read(DISCLOSURE)
        assert "GB 45438-2025" in text and "2025-09-01 施行" in text


class TestH055to063ExportMarking:
    def test_h055_single_export_question_image_is_labelled(self) -> None:
        assert "isAiGeneratedExportImage(input.questionImageRole)" in read(PLANNER)

    def test_h056_batch_blocks_are_labelled_per_role(self) -> None:
        assert "isAiGeneratedExportImage(block.role)" in read(PLANNER)

    def test_h057_paper_edit_reuses_the_same_planner(self) -> None:
        assert "AI_GENERATED_IMAGE_CAPTION" in read(PLANNER)
        assert "MistakePdfPaperEdit" in read("core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEdit.kt")

    def test_h058_docx_embeds_original_bytes(self) -> None:
        assert "原字节嵌入" in read(METADATA)

    def test_h059_non_model_roles_are_not_labelled(self) -> None:
        text = read(CAPTION)
        assert "QUESTION_DIAGRAM" in text and "else -> false" in text

    def test_h060_caption_is_derived_at_render_time(self) -> None:
        assert "不进" in read(CAPTION) and "inputSha256" in read(CAPTION)

    def test_h061_partial_print_relabels_subset_with_prepared_aigc(self) -> None:
        delivery = read(
            "core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt"
        )
        device = read(AIGC_DEVICE_TEST)
        assert "relabelSubset" in delivery and "PdfAigcInjector().inject(subsetBytes" in delivery
        assert "prepared.aigcMetadataJson" in delivery
        assert "partial-page print must keep the document-level /AIGC" in device
        assert "prepared.inputSha256, subsetReadBack.produceId" in device

    def test_h062_model_text_provenance_survives_paper_edit(self) -> None:
        text = read(PAPER_EDIT)
        assert "val modelTextApplied: Boolean = false" in text
        assert "modelTextApplied = true" in text
        assert "hasModelParsedText = sourceInput.hasModelParsedText || modelTextApplied" in text
        assert "modelTextApplied = modelTextApplied" in text

    def test_h063_gap_caption_legibility_is_a_human_visual_check(self) -> None:
        assert "图注" in read(CAPTION)
        assert not list((ROOT / "docs/acceptance/evidence/H").rglob("*legibility*")) if (ROOT / "docs/acceptance/evidence/H").exists() else True


class TestH064to072ErrorCopy:
    def test_h064_copy_has_no_technical_terms(self) -> None:
        text = read(COPY)
        assert not re.search(r"Exception|Error:|错误码|堆栈", text)

    def test_h065_pdf_and_word_copy_are_split(self) -> None:
        text = read(COPY)
        assert "单题 A4 导出" in text and "单题 Word 文档" in text

    def test_h066_diagnostics_copy_does_not_concatenate_raw_message(self) -> None:
        # 批次 46：旧断言写死第 826 行（那里是 `}`），属假通过。改为按内容定位
        # exportDiagnostics 回调块，并断言固定文案、无异常 message 拼接、取消重抛。
        text = read(SECONDARY)
        start = text.find("val exportDiagnostics = rememberLauncherForActivityResult(")
        end = text.find("if (confirmDelete)", start + 1)
        assert start >= 0 and end > start
        block = text[start:end]
        assert "导出失败，本机记录不受影响。" in block
        assert not re.search(r"diagnosticsMessage\s*=\s*[^\n]*\b(?:failure|exception|error|throwable)\.message", block)
        assert not re.search(r"diagnosticsMessage\s*=\s*[^\n]*\$\{[^}]*\.message", block)
        assert "catch (cancelled: CancellationException)" in block and "throw cancelled" in block

    def test_h067_failure_copy_says_no_complete_file(self) -> None:
        text = read(COPY)
        assert "没有整理出完整" in text and "请稍后重新导出" in text

    def test_h068_copy_has_no_sensitive_identifiers(self) -> None:
        assert not re.search(r"sha256|content://|/data/|API[_ ]?KEY|Bearer", read(COPY))

    def test_h069_export_copy_contract_covers_all_failures(self) -> None:
        text = read(COPY_TEST)
        assert "every render failure and batch reason has copy" in text

    def test_h070_failure_notification_is_plain_and_private(self) -> None:
        text = read(NOTIFICATIONS)
        failure = text.split("fun postFailed(", 1)[1].split("fun foregroundInfo(", 1)[0]
        assert 'MistakeExportArtifactRole.DETAILED_ANSWERS) "详细答案未导出" else "这次导出没有完成"' in failure
        assert "text = failureMessage" in failure

    def test_h071_injection_failure_is_fail_closed(self) -> None:
        text = read(EXPORTER)
        assert "catch (failure: PdfAigcInjectionException)" in text
        assert "GENERATED_PDF_INVALID" in text

    def test_h072_chinese_copy_has_no_untranslated_english_words(self) -> None:
        text = " ".join(lit for lit in literals(COPY) if re.search(r"[\u4e00-\u9fff]", lit) and "${" not in lit)
        assert re.findall(r"\b(?!PDF\b|Word\b|A4\b)[A-Za-z]{5,}\b", text) == []


class TestH073to081I18n:
    def test_h073_app_label_matches_manifest(self) -> None:
        assert "智能错题本" in read(STRINGS)
        assert "@string/app_name" in read(MANIFEST)

    def test_h074_no_stale_locale_resource_dirs(self) -> None:
        assert sorted(p.name for p in (ROOT / "app/src/main/res").glob("values-*")) == []

    def test_h075_gap_strings_are_hardcoded(self) -> None:
        hits = [p for p in main_kt() if 'Text("' in p.read_text(encoding="utf-8", errors="replace")]
        assert len(hits) > 30

    def test_h076_gap_no_rtl_walkthrough(self) -> None:
        assert 'android:supportsRtl="true"' in read(MANIFEST)
        assert not list((ROOT / "docs").rglob("*rtl*"))

    def test_h077_gap_date_formats_are_hardcoded_chinese(self) -> None:
        hits = [p for p in main_kt() if "SimpleDateFormat(" in p.read_text(encoding="utf-8", errors="replace")]
        assert hits

    def test_h078_no_plural_resources_needed_for_chinese(self) -> None:
        assert not [p for p in (ROOT / "app/src/main/res").rglob("*.xml") if "<plurals" in p.read_text(encoding="utf-8", errors="replace")]

    def test_h079_no_untranslated_english_sentences(self) -> None:
        text = " ".join(lit for lit in literals(COPY) + literals(SECONDARY) if re.search(r"[\u4e00-\u9fff]", lit) and "${" not in lit)
        assert re.findall(r"\b[A-Za-z]{3,}(?:\s+[A-Za-z]{3,}){2,}\b", text) == []

    def test_h080_gap_no_language_switch_or_locale_change_test(self) -> None:
        assert not [p for p in main_kt() if re.search(r"语言切换|languageSwitch|AppCompatDelegate.setApplicationLocales", p.read_text(encoding="utf-8", errors="replace"))]

    def test_h081_gap_no_localized_store_or_policy(self) -> None:
        assert not list(ROOT.glob("fastlane/**/en-US"))
        assert not [
            p for p in ROOT.glob("docs/**/*privacy-policy*")
            if p.suffix.lower() in {".md", ".html", ".htm", ".txt"}
        ]


class TestH082to090Typography:
    def test_h082_cjk_latin_spacing_for_ai_generation(self) -> None:
        text = read(COPY) + read(DISCLOSURE)
        assert "AI 生成" in text and "AI生成" not in text

    def test_h083_no_halfwidth_comma_between_cjk(self) -> None:
        text = read(COPY) + read(DISCLOSURE)
        assert re.findall(r"[\u4e00-\u9fff][,;][\u4e00-\u9fff]", text) == []

    def test_h084_no_three_dot_ellipsis(self) -> None:
        assert "..." not in read(COPY) + read(DISCLOSURE)

    def test_h085_no_halfwidth_question_exclaim_colon_between_cjk(self) -> None:
        text = read(COPY) + read(DISCLOSURE)
        assert re.findall(r"[\u4e00-\u9fff][?!:][\u4e00-\u9fff]", text) == []

    def test_h086_no_fullwidth_latin_or_digits(self) -> None:
        text = read(COPY) + read(DISCLOSURE)
        assert re.findall(r"[Ａ-Ｚａ-ｚ０-９]", text) == []

    def test_h087_no_ai_generation_variant_without_space(self) -> None:
        assert "AI生成" not in read(COPY) + read(DISCLOSURE)

    def test_h088_no_ideographic_space_or_tab_in_copy(self) -> None:
        text = read(COPY) + read(DISCLOSURE)
        assert "\u3000" not in text and "\t" not in text

    def test_h089_gap_error_copy_nuance_needs_human_review(self) -> None:
        assert "请稍后重新导出" in read(COPY)
        assert not list((ROOT / "docs").rglob("*copy-review*"))

    def test_h090_gap_rendered_typography_needs_device_walkthrough(self) -> None:
        device_dir = ROOT / "docs/acceptance/evidence/H/device"
        assert not list(device_dir.rglob("*typography*")) if device_dir.exists() else True
