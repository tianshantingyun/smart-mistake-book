#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""按域生成验收台账骨架（批次 44 / D0 item 1）。

## 它消灭的具体失败

1. 计划口径与实账漂移（批次 41 记 55 vs 62、42 记 74 vs 90、43 记 74 vs 78）。
   本脚本把 **1200 = D0 100 + A–J 各 90 + FIX 100 + GATE 100** 写成唯一
   生成源；`--check` 对现有台账重算 ID 集合与域计数，漂移即 exit 1。
2. 重新生成时覆盖已裁定 verdict。`--write` 只补缺失行；已存在的行整行保留
   （v/check/method/evidence/verdict 都不动），第二次运行字节相同。

## 用法

    python -I tools/ci/acceptance_init.py --write
    python -I tools/ci/acceptance_init.py --check
    python -I tools/ci/acceptance_init.py --write --meta build/acceptance/meta.json

退出码：0 成功/无漂移；1 台账结构或计数漂移；2 参数/IO 错误。
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import acceptance_model as M  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"

HEADER = """\
<!-- 本文件由 tools/ci/acceptance_init.py 生成；已有行不会被重写。 -->
# 验收台账（批次 44）

> **本骨架初始 verdict 为 `UNVERIFIED`；已知外部阻塞（真实 Provider、生产签名、
> arm64）为 `BLOCKED` 并指向 runbook。** 它们都不是“通过”，也不得被引用成
> 通过。每一行只有在 `tools/ci/acceptance_report.py` 读到 ≥2 类独立证据且
> `tools/ci/acceptance_lint.py` 无违规后才能改为 `PASS`。
>
> - 计数：1200 项 = D0 100 + A–J 每域 90 + FIX 100 + GATE 100。
> - 行格式：`| ID | 域 | 检查 | 方法 | 证据 | verdict |`。
> - 证据 token：`kind:repo/relative/path#k=v`，多个用 `;` 分隔；kind ∈
>   `cmd/test/device/review/file/hash`。只有 `cmd/test/device/review` 计为
>   独立证据类；`file/hash` 是辅助。
> - `方法` 里的 `<...>` 是显式占位，缺失路径也会被
>   `tools/ci/trace_requirements.py` 列为 gap；`BLOCKED:` 是外部阻塞，
>   `UNVERIFIED:` 是人类残余/未测，二者都不冒充通过。
> - 台账来源：`docs/current/product-contract.md`、计划 §1–§4、批次 43 收口
>   （KD-76 open / isolation 不定案 / p09 未收敛 / 发版不成立）。
> - 不可变清单：6C-2 三项（mask=8 不提供、mask=0 禁止、派生索引恒裁）只可
>   加不变量测试，不得改行为。

## 验收替代边界

自动化不能替代的人类语感/审美项由 `UNVERIFIED` 与声明的残余承载，不由本台账
签字通过。真实 Provider、生产签名、arm64、人工金标缺失时登记 `BLOCKED` 并
指向 `docs/acceptance/runbook.md`。

"""


def _emit(rows: list[tuple[str, str, str, str]]) -> list[tuple[str, str, str, str]]:
    """(check, method, evidence, verdict) 归一化：去掉 `|`。"""
    return [
        (check.replace("|", "／"), method.replace("|", "／"), evidence, verdict)
        for check, method, evidence, verdict in rows
    ]


def _d0_items() -> list[tuple[str, str]]:
    report = "cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md"
    lint = "cmd: python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md"
    init = "cmd: python -I tools/ci/acceptance_init.py --check"
    trace = "cmd: python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md"
    items: list[tuple[str, str]] = []

    def add(category: str, checks: list[str], method: str) -> None:
        for check in checks:
            items.append((f"{category}：{check}", method))

    add("台账 ledger.md", [
        "文件存在、UTF-8 可读、含 1200 行数据行",
        "ID 唯一且属于 13 个域前缀",
        "D0 100 + A–J 各 90 + FIX 100 + GATE 100 计数成立",
        "每行六列齐全，检查/方法不得含竖线",
        "verdict 仅 PASS/FAIL/BLOCKED/UNVERIFIED",
        "证据 token 可按 kind:path#attrs 解析",
        "重新生成不覆盖已有 verdict（只补缺失行）",
    ], init)
    add("DoD dod.md", [
        "功能完成定义：主路径可复跑",
        "失败路径定义：错误可见且数据安全",
        "边界定义：空态/大数据/权限拒绝/进程死亡",
        "证据定义：≥2 类独立证据",
        "独立复核定义：reviewer 非作者且绑 run-id",
        "对抗复核定义：每域至少 1 条反例尝试",
        "DoD 与台账 verdict 的映射表存在",
    ], "file: docs/acceptance/dod.md")
    add("报告 acceptance_report.py", [
        "PASS 必须有 ≥2 类独立证据（cmd/test/device/review）",
        "所有证据路径必须存在",
        "cmd 日志 exit 可读且为 0",
        "test XML 的 failures/errors 为 0",
        "review 终裁含 reviewer/run_id 且非自证",
        "非法 verdict / 重复 ID / 缺行 → exit 1",
        "输出 JSON 并返回稳定 exit code",
    ], report)
    add("lint acceptance_lint.py", [
        "summary/批次记录不得当证据",
        "UNVERIFIED 不得标 PASS",
        "跳过的门不得计通过（-4/SKIP/AssumptionViolated）",
        "空证据 PASS 必须违规",
        "同一证据跨过多行 PASS 必须告警",
        "历史假绿夹具回放必须 exit 1",
        "自证复核必须违规",
    ], lint)
    add("逐项终裁 verdicts/", [
        "目录存在且有读取说明",
        "每个 PASS 有对应 verdict JSON",
        "verdict JSON 绑定 run-id",
        "reviewer 与 authors 不相交",
        "evidence 列表含 sha256",
        "不可伪造字段（run_id/ledger_sha256）存在",
        "JSON 可被 schema 校验",
    ], "review: docs/acceptance/verdicts/README.md")
    add("追溯 trace_requirements.py", [
        "需求 id 能定位到台账行",
        "方法里的测试路径必须存在",
        "证据路径必须存在",
        "`<...>` 占位符进入缺口清单",
        "设备方法必须有 device 证据",
        "PASS 行必须有 test/device 落点",
        "输出 JSON 与稳定 exit code",
    ], trace)
    add("蜕变 metamorphic_checks.py", [
        "渲染关系：reflow 后规范化文本不变",
        "导出关系：追加条目页数不减且前缀保留",
        "检索关系：追加无关文档不改变既有分数",
        "调度关系：追加逾期复习不缩短间隔",
        "每条关系有可执行变换与必守断言",
        "关系失败输出反例而不只是 false",
        "缺真实夹具时 exit 2，不得判 PASS",
    ], "cmd: python -I tools/ci/metamorphic_checks.py --demo")
    add("截图 screenshot_baseline.py", [
        "截图采集记录 serial 与时间",
        "基线文件记录 sha256",
        "PNG 解码支持 8-bit RGB/RGBA 非隔行",
        "像素差 MAE 与变化像素比例可计算",
        "阈值必须显式给出",
        "超阈值 exit 1",
        "报告 JSON 含 baseline/current 哈希",
    ], "cmd: python -I tools/ci/screenshot_baseline.py compare --baseline build/a.png --current build/b.png")
    add("无障碍 a11y_audit.py", [
        "uiautomator 语义树可导出/离线解析",
        "可点击节点触摸目标 ≥48dp",
        "可点击节点必须有 text 或 content-desc",
        "对比度缺颜色输入时标 UNVERIFIED",
        "WCAG 对比度 <4.5 判 FAIL",
        "违规 exit 1",
        "报告 JSON 含节点计数与违规码",
    ], "cmd: python -I tools/ci/a11y_audit.py audit --dump build/window_dump.xml")
    add("CI 挂载 acceptance_ci.patch", [
        "patch 只是提案，不改实际 workflow",
        "挂载 report 步骤",
        "挂载 lint 步骤",
        "挂载 trace 步骤",
        "失败阻断后续步骤",
        "与现有 workflow 无写冲突",
        "git apply --check 可验证",
    ], "file: tools/ci/acceptance_ci.patch")
    add("运行手册 runbook.md", [
        "复跑命令按域列出",
        "PASS/FAIL/BLOCKED/UNVERIFIED 判据",
        "证据归档路径与命名",
        "设备证据必须绑 APK sha 与源快照",
        "不可自动化的残余人工项声明",
        "无外部条件时的 BLOCKED 出口",
    ], "file: docs/acceptance/runbook.md")
    add("终裁协议", [
        "主会话签字字段",
        "run-id 与台账 sha256 绑定",
        "不可伪造字段清单",
        "独立复核 + 对抗复核双签",
        "冲突时保留双方判词",
        "终裁文件归档路径",
    ], "file: docs/acceptance/dod.md")
    add("拒绝伪造清单", [
        "reviewer 不得给自己签字",
        "证据不得引用 summary/批次记录",
        "截图证据必须绑 serial + APK sha",
        "命令日志必须含可读 exit",
        "设备证据必须含 serial",
        "UNVERIFIED 不得转写为 PASS",
    ], "file: docs/acceptance/dod.md")
    add("证据哈希清单", [
        "所有 E 文件有 sha256",
        "清单自身有 sha256",
        "重算命令可复跑",
        "缺失哈希即 fail",
        "路径使用仓库相对 POSIX 形式",
        "清单不含密钥/令牌",
    ], "cmd: python -I tools/ci/acceptance_hash_manifest.py "
       "--root docs/research/evidence/2026-11-05-batch-44 "
       "--out build/acceptance/evidence-sha256.txt --self-hash")
    add("历史回放演练", [
        "批次 41/42 假绿夹具存在",
        "report 对夹具 exit 1",
        "lint 对夹具 exit 1",
        "回放命令写入 runbook",
        "回放结果归档",
        "不得为让夹具变绿而放宽阈值",
    ], "cmd: python -I tools/ci/acceptance_lint.py --ledger tools/tests/fixtures/acceptance/fake_green_batch42.ledger.md")
    return items


A_JOURNEYS = (
    "拍照→分题→入库", "讲题", "复习三键", "详情编辑", "导出 PDF/DOCX",
    "备份/恢复", "知识库浏览", "掌握度", "提醒", "设置",
)
A_FACETS = (
    ("主路径", "device: adb -s $SERIAL shell am instrument -w -e class <Journey{n}AndroidTest>"),
    ("失败路径", "device: adb -s $SERIAL shell am instrument -w -e fault=<Journey{n}Fault>"),
    ("中断恢复", "device: adb -s $SERIAL shell am force-stop com.tingyun.smartmistakebook"),
    ("空态", "device: adb -s $SERIAL shell pm clear com.tingyun.smartmistakebook"),
    ("大数据", "device: adb -s $SERIAL shell am instrument -w -e size=<Journey{n}Large>"),
    ("权限拒绝", "device: adb -s $SERIAL shell pm revoke com.tingyun.smartmistakebook <Permission>"),
    ("旋转", "device: adb -s $SERIAL shell settings put system user_rotation <0|1>"),
    ("后台", "device: adb -s $SERIAL shell input keyevent KEYCODE_HOME"),
    ("杀进程", "device: adb -s $SERIAL shell am kill com.tingyun.smartmistakebook"),
)


def _a_items() -> list[tuple[str, str]]:
    items = []
    for index, journey in enumerate(A_JOURNEYS, start=1):
        for facet, method in A_FACETS:
            items.append((f"旅程「{journey}」：{facet}", method.format(n=index)))
    return items


BACKUP_TEST = "test: tools/tests/test_backup_plan_sync.py"
BACKUP_GATE = "cmd: python -I tools/ci/check_backup_plan_sync.py; " + BACKUP_TEST


def _b_items() -> list[tuple[str, str]]:
    items = []
    for version in range(1, 73):
        items.append((
            f"迁移 v{version} → v73：schema/数据/外键/索引/回滚保持",
            f"device: adb -s $SERIAL shell am instrument -w -e class "
            f"com.tingyun.smartmistakebook.core.database.migrations.MigrationV{version}ToV73Test",
        ))
    for mask in (9, 13, 15):
        for state in ("clean", "partial", "corrupt"):
            items.append((f"备份 mask={mask} 状态={state}：创建/恢复/拒绝口径", BACKUP_GATE))
    items.append(("备份 v4 跨版本恢复", "test: core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt"))
    items.append(("备份 v5 跨版本恢复", "test: core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt"))
    for corrupt in ("truncated header", "bad magic", "bad version", "missing entry",
                    "hash mismatch", "truncated payload", "duplicate entry"):
        items.append((f"损坏备份：{corrupt}", "test: core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt"))
    return items


C_PRIMITIVES = ("line", "rect", "circle", "arc", "arrow", "axis")
C_RELATIONS = ("确定性", "变换等变", "裁剪包含", "线宽缩放", "预算 fail-closed")
C_TOPICS = (
    ("编译器确定性", "test: app/src/test/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuringTest.kt"),
    ("屏幕 painter 与 PDF painter 一致", "test: core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPlannerGoldenTest.kt"),
    ("字体身份/版本", "test: app/src/test/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuringTest.kt"),
    ("fontScale 1/1.3/2", "device: adb -s $SERIAL shell settings put system font_scale <1|1.3|2>"),
    ("暗色主题", "device: adb -s $SERIAL shell cmd uimode night yes"),
    ("PDF 光栅差", "test: core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPlannerGoldenTest.kt"),
    ("退化降级（上一有效帧/文字说明）", "device: adb -s $SERIAL shell am instrument -w -e class StrictOfflineRelayoutDeviceVectorInstrumentedTest"),
    ("未知字段/节点/路径命令拒绝", "cmd: ./gradlew :core:data:testDebugUnitTest --tests *Visual*"),
    ("预算 fail-closed（像素/行数/深度）", "cmd: ./gradlew :core:data:testDebugUnitTest --tests *Visual*"),
    ("dash 形状门（偶数且 ≥2）", "cmd: ./gradlew :core:data:testDebugUnitTest --tests *DrawOps*"),
    ("stroke-only 门（必须有 fill）", "cmd: ./gradlew :core:data:testDebugUnitTest --tests *DrawOps*"),
    ("hitRegions 可非空且不绘制", "test: core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt"),
)
C_FACETS = ("正例", "失败路径", "边界", "证据", "回归")


def _c_items() -> list[tuple[str, str]]:
    items = []
    for primitive in C_PRIMITIVES:
        for relation in C_RELATIONS:
            items.append((f"SceneV1 原语 {primitive}：{relation}",
                          "test: core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPlannerGoldenTest.kt"))
    for topic, method in C_TOPICS:
        for facet in C_FACETS:
            items.append((f"{topic}：{facet}", method))
    return items


D_TOPICS = (
    ("prompt 版本/golden", "test: core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPlannerGoldenTest.kt"),
    ("请求 schema 19", "cmd: python -I tools/ci/check_eval_label_leakage.py"),
    ("解析失败分类", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewLogStateTest.kt"),
    ("预算/账本", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacitySchedulerTest.kt"),
    ("egress 授权", "cmd: python -I tools/ci/check_telemetry_compliance.py"),
    ("工具环（≤6 次总预算）", "cmd: ./gradlew :app:testLocalFirstDebugUnitTest"),
    ("paper-edit", "test: core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPaperEditTest.kt"),
    ("knowledge quiz", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ExamQuotaPlannerTest.kt"),
    ("重试/取消/进程重建", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityPolicyTest.kt"),
    ("真实 Provider（无密钥）", "BLOCKED: 无真实 Provider 密钥（见 docs/acceptance/runbook.md）"),
)
D_FACETS = ("正常", "失败", "边界", "取消", "重试", "预算", "日志", "证据", "BLOCKED 口径")


def _d_items() -> list[tuple[str, str]]:
    return [(f"{topic}：{facet}", method) for topic, method in D_TOPICS for facet in D_FACETS]


E_TOPICS = (
    ("FSRS 不变量", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySchedulingCalibrationTest.kt"),
    ("容量总量", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacitySchedulerTest.kt"),
    ("考试配额", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ExamQuotaPlannerTest.kt"),
    ("欠账再平衡", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityPolicyTest.kt"),
    ("掌握判定", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/MasteryIntervalMappingTest.kt"),
    ("错因候选", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewLogStateTest.kt"),
    ("错因落账", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewLogStateTest.kt"),
    ("错因撤销", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewLogStateTest.kt"),
    ("排序稳定性", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySchedulingCalibrationTest.kt"),
    ("时间界（04:00/时区/DST）", "test: core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerExamWindowTest.kt"),
)
E_FACETS = ("0 值", "1 值", "极大值", "边界时间", "重复输入", "回滚", "并发", "证据", "回归")


def _e_items() -> list[tuple[str, str]]:
    return [(f"{topic}：{facet}", method) for topic, method in E_TOPICS for facet in E_FACETS]


F_TOPICS = (
    ("冷启动", "device: adb -s $SERIAL shell am start -W -n com.tingyun.smartmistakebook/.MainActivity"),
    ("warm-up", "device: adb -s $SERIAL shell am start -W -n com.tingyun.smartmistakebook/.MainActivity"),
    ("首帧", "device: adb -s $SERIAL shell dumpsys gfxinfo com.tingyun.smartmistakebook"),
    ("导出耗时", "cmd: ./gradlew :core:export:testDebugUnitTest"),
    ("预览耗时", "cmd: python -I tools/ci/lowload_gate.py"),
    ("内存峰值", "device: adb -s $SERIAL shell dumpsys meminfo com.tingyun.smartmistakebook"),
    ("长时 2h", "device: adb -s $SERIAL shell am instrument -w -e duration=7200"),
    ("ANR/崩溃签名", "device: adb -s $SERIAL shell dumpsys dropbox --print"),
    ("进程死亡恢复", "device: adb -s $SERIAL shell am kill com.tingyun.smartmistakebook"),
    ("后台限制/低电低存/多任务", "device: adb -s $SERIAL shell cmd deviceidle force-idle"),
)
F_FACETS = ("基线", "阈值", "超限", "重复运行", "设备", "arm64（BLOCKED）", "日志", "证据", "回归")


def _f_items() -> list[tuple[str, str]]:
    return [(f"{topic}：{facet}", method) for topic, method in F_TOPICS for facet in F_FACETS]


G_TOPICS = (
    ("密钥", "cmd: python -I tools/ci/check_lockfiles.py"),
    ("日志", "cmd: python -I tools/ci/check_telemetry_compliance.py"),
    ("备份", "cmd: python -I tools/ci/check_backup_plan_sync.py"),
    ("外发", "cmd: python -I tools/ci/check_telemetry_compliance.py"),
    ("权限", "device: adb -s $SERIAL shell dumpsys package com.tingyun.smartmistakebook"),
    ("深链", "cmd: ./gradlew :app:testLocalFirstDebugUnitTest"),
    ("intent", "cmd: ./gradlew :app:testLocalFirstDebugUnitTest"),
    ("文件暴露", "cmd: python -I tools/ci/check_release_manifest.py"),
    ("剪贴板/截图", "device: adb -s $SERIAL shell am instrument -w -e class ClipboardScreenshotGuardTest"),
    ("明文扫描/依赖 CVE/供应链", "cmd: python -I tools/ci/check_dependency_pins.py"),
)
G_FACETS = ("静态扫描", "运行时", "负向", "边界", "脱敏", "证据", "阻塞", "回归", "复核")


def _g_items() -> list[tuple[str, str]]:
    return [(f"{topic}：{facet}", method) for topic, method in G_TOPICS for facet in G_FACETS]


H_TOPICS = (
    ("AIGC 显式标识", "test: core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporterAigcJvmTest.kt"),
    ("AIGC 隐式元数据", "test: core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporterAigcJvmTest.kt"),
    ("隐私政策", "file: PRIVACY-DATA-FLOW.md"),
    ("data safety 映射", "cmd: python -I tools/ci/check_telemetry_compliance.py"),
    ("青少年模式", "cmd: ./gradlew :app:testLocalFirstDebugUnitTest"),
    ("免责声明", "file: docs/ai-content-disclosure-copy.md"),
    ("导出水印", "test: core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporterAigcJvmTest.kt"),
    ("错误文案", "cmd: ./gradlew :app:testLocalFirstDebugUnitTest"),
    ("i18n", "cmd: python -I tools/ci/check_telemetry_compliance.py"),
    ("中文排版", "file: docs/acceptance/dod.md"),
)
H_FACETS = ("法规映射", "正例", "反例", "边界", "文案一致性", "证据", "外部依赖（BLOCKED）", "回归", "复核")


def _h_items() -> list[tuple[str, str]]:
    return [(f"{topic}：{facet}", method) for topic, method in H_TOPICS for facet in H_FACETS]


I_TOPICS = (
    ("TalkBack 语义", "device: adb -s $SERIAL shell uiautomator dump /sdcard/window_dump.xml"),
    ("触摸目标 48dp", "cmd: python -I tools/ci/a11y_audit.py audit --dump build/window_dump.xml"),
    ("对比度", "cmd: python -I tools/ci/a11y_audit.py audit --dump build/window_dump.xml --colors build/colors.json"),
    ("字号 200%", "device: adb -s $SERIAL shell settings put system font_scale 2.0"),
    ("暗色", "device: adb -s $SERIAL shell cmd uimode night yes"),
    ("焦点顺序", "device: adb -s $SERIAL shell input keyevent KEYCODE_TAB"),
    ("空态/错误可读", "cmd: ./gradlew :app:testLocalFirstDebugUnitTest"),
    ("键盘", "device: adb -s $SERIAL shell input keyevent KEYCODE_ENTER"),
    ("横竖屏", "device: adb -s $SERIAL shell settings put system user_rotation 1"),
    ("人工语感（残余）", "UNVERIFIED: 屏幕阅读器语感与审美无法自动判定（见 runbook）"),
)
I_FACETS = ("自动检查", "设备", "负向", "边界", "残余人工", "证据", "BLOCKED", "回归", "复核")


def _i_items() -> list[tuple[str, str]]:
    return [(f"{topic}：{facet}", method) for topic, method in I_TOPICS for facet in I_FACETS]


J_TOPICS = (
    ("干净树构建", "cmd: ./gradlew assembleLocalFirstRelease --offline"),
    ("R8/mapping", "cmd: python -I tools/ci/check_release_manifest.py"),
    ("签名/AAB", "BLOCKED: 无生产签名密钥（见 docs/acceptance/runbook.md）"),
    ("16KB/AAB 检查", "cmd: python -I tools/ci/check_release_manifest.py"),
    ("arm64", "BLOCKED: 无 arm64 真机（见 docs/acceptance/runbook.md）"),
    ("版本号", "cmd: python -I tools/ci/check_release_manifest.py"),
    ("升级/降级", "device: adb -s $SERIAL install -r build/app.apk"),
    ("回滚", "file: RELEASE.md"),
    ("商店素材", "file: RELEASE.md"),
    ("封闭测试清单", "file: RELEASE.md"),
)
J_FACETS = ("命令", "产物", "校验", "负向", "证据", "阻塞", "回归", "复核", "runbook")


def _j_items() -> list[tuple[str, str]]:
    return [(f"{topic}：{facet}", method) for topic, method in J_TOPICS for facet in J_FACETS]


FIX_AREAS = (
    ("数据迁移", ":core:data:testDebugUnitTest"),
    ("备份恢复", ":core:data:testDebugUnitTest"),
    ("并发事务", ":core:data:testDebugUnitTest"),
    ("渲染确定性", ":core:export:testDebugUnitTest"),
    ("导出 PDF", ":core:export:testDebugUnitTest"),
    ("导出 DOCX", ":core:export:testDebugUnitTest"),
    ("模型解析", ":core:data:testDebugUnitTest"),
    ("预算账本", ":core:data:testDebugUnitTest"),
    ("egress 授权", ":core:data:testDebugUnitTest"),
    ("工具环", ":app:testLocalFirstDebugUnitTest"),
    ("复习调度", ":core:data:testDebugUnitTest"),
    ("掌握判定", ":core:data:testDebugUnitTest"),
    ("错因账", ":core:data:testDebugUnitTest"),
    ("提醒", ":app:testLocalFirstDebugUnitTest"),
    ("性能", ":core:export:testDebugUnitTest"),
    ("崩溃恢复", ":app:testLocalFirstDebugUnitTest"),
    ("安全密钥", ":core:data:testDebugUnitTest"),
    ("日志脱敏", ":core:data:testDebugUnitTest"),
    ("AIGC 标识", ":core:export:testDebugUnitTest"),
    ("无障碍与文案", ":app:testLocalFirstDebugUnitTest"),
)
FIX_PHASES = (
    ("红测试", "cmd: ./gradlew {module} --tests *{area}*"),
    ("绿修复", "cmd: ./gradlew {module} --tests *{area}*"),
    ("回归", "cmd: ./gradlew {module}"),
    ("证据", "review: docs/acceptance/verdicts/FIX-review.json"),
    ("不可修登记", "file: docs/acceptance/runbook.md"),
)


def _fix_items() -> list[tuple[str, str]]:
    items = []
    for area, module in FIX_AREAS:
        for phase, method in FIX_PHASES:
            items.append((f"修复「{area}」：{phase}", method.format(module=module, area=area)))
    return items


GATE_CHECKS = (
    ("独立复核", "review: docs/acceptance/verdicts/GATE-{d}-independent.json"),
    ("对抗复核反例尝试", "review: docs/acceptance/verdicts/GATE-{d}-adversarial.json"),
    ("主会话终裁", "review: docs/acceptance/verdicts/GATE-{d}-main.json"),
    ("双门（单测 + 强门）", "cmd: ./gradlew testDebugUnitTest; cmd: ./gradlew :app:assembleLocalFirstDebug"),
    ("设备证据绑定 APK sha", "device: adb -s $SERIAL shell am instrument -w"),
    ("证据归档", "file: docs/acceptance/ledger.md"),
    ("证据哈希清单", "cmd: python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md"),
    ("记录回写", "file: docs/acceptance/runbook.md"),
    ("空项检查", "cmd: python -I tools/ci/acceptance_init.py --check"),
    ("批次 45 建议", "file: docs/acceptance/runbook.md"),
)


def _gate_items() -> list[tuple[str, str]]:
    items = []
    for domain, title, _ in M.DOMAINS:
        if domain in {"D0", "FIX", "GATE"}:
            continue
        for check, method in GATE_CHECKS:
            items.append((f"{domain} 域「{title}」：{check}", method.format(d=domain)))
    return items


def build_items() -> dict[str, list[tuple[str, str]]]:
    return {
        "D0": _d0_items(),
        "A": _a_items(),
        "B": _b_items(),
        "C": _c_items(),
        "D": _d_items(),
        "E": _e_items(),
        "F": _f_items(),
        "G": _g_items(),
        "H": _h_items(),
        "I": _i_items(),
        "J": _j_items(),
        "FIX": _fix_items(),
        "GATE": _gate_items(),
    }


def render_ledger(existing: list[M.Row], items: dict[str, list[tuple[str, str]]]) -> str:
    by_id = {row.row_id: row for row in existing}
    chunks = [HEADER.rstrip("\n")]
    for key, title, count in M.DOMAINS:
        chunk = [f"## {key} {title}（{count}）", "",
                 "| ID | 域 | 检查 | 方法 | 证据 | verdict |",
                 "|---|---|---|---|---|---|"]
        rows = items[key]
        if len(rows) != count:
            raise SystemExit(f"生成器计数错误：{key} 期望 {count}，实得 {len(rows)}")
        for index, (check, method) in enumerate(rows, start=1):
            row_id = f"{key}-{index:03d}"
            old = by_id.get(row_id)
            if old is not None:
                chunk.append(M.format_row(old))
            else:
                if method.startswith("BLOCKED:") or "arm64（BLOCKED）" in check:
                    verdict, evidence = "BLOCKED", "file: docs/acceptance/runbook.md"
                elif method.startswith("UNVERIFIED:"):
                    verdict, evidence = "UNVERIFIED", "file: docs/acceptance/runbook.md"
                else:
                    verdict, evidence = "UNVERIFIED", "-"
                chunk.append(M.format_row(M.Row(
                    row_id=row_id, domain=key, check=check, method=method,
                    evidence_text=evidence, verdict=verdict,
                )))
        chunks.append("\n".join(chunk))
    return "\n\n".join(chunks) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="生成/校验 1200 行验收台账骨架")
    parser.add_argument("--ledger", default=str(DEFAULT_LEDGER))
    parser.add_argument("--write", action="store_true", help="写入（只补缺失行）")
    parser.add_argument("--check", action="store_true", help="只校验 ID/域计数/verdict")
    parser.add_argument("--meta", help="把域计数写入 JSON")
    parser.add_argument("--force", action="store_true",
                        help="台账已有结构错误时仍写入（默认 fail-closed）")
    args = parser.parse_args(argv)

    ledger = Path(args.ledger)
    items = build_items()
    for key, _, count in M.DOMAINS:
        if len(items[key]) != count:
            print(f"生成器计数错误：{key} 期望 {count}，实得 {len(items[key])}", file=sys.stderr)
            return 2

    if args.check:
        if not ledger.exists():
            print(f"MISSING_LEDGER: {ledger}", file=sys.stderr)
            return 1
        rows, issues = M.parse_ledger(ledger)
        issues.extend(M.identity_issues(rows))
        for issue in issues:
            print(f"{issue['code']}: {issue['detail']}")
        return 1 if issues else 0

    existing: list[M.Row] = []
    if ledger.exists():
        try:
            existing, issues = M.parse_ledger(ledger)
        except M.LedgerError as exc:
            print(str(exc), file=sys.stderr)
            return 2
        blocking_codes = {"ROW_COLUMN_COUNT", "DUPLICATE_ID", "DOMAIN_MISMATCH",
                          "BAD_VERDICT", "BAD_EVIDENCE"}
        blocking = [issue for issue in issues if issue["code"] in blocking_codes]
        if blocking and not args.force:
            print(f"REFUSED: {ledger} 有 {len(blocking)} 个结构错误；"
                  "写入会丢行。修好或显式加 --force。", file=sys.stderr)
            for issue in blocking[:20]:
                print(f"  {issue['code']}: {issue['detail']}", file=sys.stderr)
            return 1
        if issues:
            print(f"{ledger} 有 {len(issues)} 个结构问题；仍只补缺失行。", file=sys.stderr)
            for issue in issues[:20]:
                print(f"  {issue['code']}: {issue['detail']}", file=sys.stderr)
    new_text = render_ledger(existing, items)
    if args.write or not ledger.exists():
        ledger.parent.mkdir(parents=True, exist_ok=True)
        old_text = ledger.read_text(encoding="utf-8") if ledger.exists() else None
        if old_text != new_text:
            ledger.write_text(new_text, encoding="utf-8", newline="\n")
            print(f"WROTE {ledger} ({len(new_text.splitlines())} lines)")
        else:
            print(f"UNCHANGED {ledger}")
    else:
        print("DRY-RUN（加 --write 落盘）")

    rows, issues = M.parse_ledger_text(new_text, str(ledger))
    issues.extend(M.identity_issues(rows))
    if args.meta:
        meta_path = Path(args.meta)
        meta_path.parent.mkdir(parents=True, exist_ok=True)
        counts = {key: sum(1 for row in rows if row.domain == key) for key, _, _ in M.DOMAINS}
        meta_path.write_text(json.dumps(
            {"total": len(rows), "domains": counts, "issues": issues},
            ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if issues:
        for issue in issues:
            print(f"{issue['code']}: {issue['detail']}", file=sys.stderr)
        return 1
    print(f"OK total={len(rows)} domains={len(M.DOMAINS)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
