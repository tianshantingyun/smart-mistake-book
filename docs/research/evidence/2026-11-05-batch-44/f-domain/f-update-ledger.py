#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 F 域 90 行写回验收台账（批次 44）。

只替换 `| F-xxx |` 行；其余 1110 行原样保留。写前校验：F 行恰好 90 条且
全部仍是骨架 UNVERIFIED/BLOCKED（证据为 -），避免覆盖别人的结果。
用法：
    python -I f-update-ledger.py --preview   # 写 build/f-preview-ledger.md
    python -I f-update-ledger.py --apply     # 原子替换 docs/acceptance/ledger.md
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
BASE = "docs/research/evidence/2026-11-05-batch-44/f-domain"
OUT = ROOT / BASE
LEDGER = ROOT / "docs" / "acceptance" / "ledger.md"


def dev(name: str) -> str:
    return f"device:{BASE}/{name}"


def cmd(name: str) -> str:
    return f"cmd:{BASE}/{name}"


def test(name: str) -> str:
    return f"test:{BASE}/{name}"


def fil(path: str) -> str:
    return f"file:{path}"


def hsh(name: str) -> str:
    return f"hash:{BASE}/{name}"


def join(*items: str) -> str:
    return "; ".join(items)


def soak_state() -> dict:
    p = OUT / "soak-stats.txt"
    if not p.exists():
        return {"done": False, "crashed": False}
    text = p.read_text(encoding="utf-8", errors="replace")

    def num(key: str):
        m = re.search(rf"{key}=(\d+)", text)
        return int(m.group(1)) if m else None

    actual = num("duration_actual_s") or 0
    return {"done": actual >= 7200, "actual_s": actual, "cycles": num("cycles"),
            "pss_max_kb": num("pss_max_kb"), "crashed": num("crashed") == 1,
            "crash_elapsed_s": num("first_crash_elapsed_s"),
            "dropbox_first": num("dropbox_first"), "dropbox_last": num("dropbox_last"),
            "crash_lines_max": num("crash_lines_max"), "monkey_failures": num("monkey_failures")}


def rows() -> list[tuple[str, str, str, str, str]]:
    out: list[tuple[str, str, str, str, str]] = []

    def add(prefix: str, n: int, check: str, method: str, evidence: str, verdict: str):
        out.append((f"{prefix}-{n:03d}", check, method, evidence, verdict))

    def block(prefix: str, start: int, items):
        for i, (c, m, e, v) in enumerate(items, start):
            add(prefix, i, c, m, e, v)

    cold = [
        ("冷启动：基线", "adb -s emulator-5566 shell am force-stop 后 am start -W 7 冷启 + 5 热启，解析 TotalTime",
         join(dev("startup.device.json"), cmd("startup.cmd.log"), dev("startup-cold.txt")), "PASS"),
        ("冷启动：阈值", "对照 PerformanceTargets.kt:18 的 2000ms；模拟器冷启 3001-3939ms，目标只对真机成立，不在模拟器判产品红绿",
         dev("startup.device.json"), "UNVERIFIED"),
        ("冷启动：超限", "统计器对 7/7 冷启超过 2000ms 标记 COLD_OVER_TARGET=7/7，未把超限静默记绿",
         join(cmd("startup.cmd.log"), dev("startup.device.json"), dev("startup-run.log")), "PASS"),
        ("冷启动：重复运行", "7 冷启 + 5 热启重复；冷启 p50=3302ms p95=3939ms max=3939ms",
         join(dev("startup.device.json"), cmd("startup.cmd.log"), dev("startup-hot.txt")), "PASS"),
        ("冷启动：设备", "设备身份 emulator-5566 / sdk_gphone64_x86_64 / x86_64 / API34 / PAGESIZE=4096 / boot_id / APK sha b8503f04",
         join(dev("startup.device.json"), cmd("startup.cmd.log"), dev("device-startup-meta.txt")), "PASS"),
        ("冷启动：arm64（BLOCKED）", "BLOCKED: 无 arm64 真机或镜像；换机后重跑冷启",
         fil("docs/acceptance/runbook.md"), "BLOCKED"),
        ("冷启动：日志", "logcat ActivityTaskManager Displayed 与 dumpsys gfxinfo 逐轮落盘",
         join(dev("logcat-displayed.txt"), dev("gfxinfo.txt"), cmd("startup.cmd.log")), "PASS"),
        ("冷启动：证据", "原始产物 + startup.device.json + sha256 清单",
         join(dev("startup.device.json"), cmd("startup.cmd.log"), hsh("f-evidence-sha256.txt")), "PASS"),
        ("冷启动：回归", "无同设备同构建历史基线；批次43 基线在 emulator-5584，不同实例",
         dev("startup.device.json"), "UNVERIFIED"),
    ]
    block("F", 1, cold)

    warm = [
        ("warm-up：基线", "冷启前2轮均值 3470ms vs 平台期 3308.6ms，warm-up 收敛 161.4ms",
         join(dev("startup.device.json"), cmd("startup.cmd.log"), dev("startup-cold.txt")), "PASS"),
        ("warm-up：阈值", "无 warm-up 数值目标；只记录收敛量，不伪造阈值",
         dev("startup.device.json"), "UNVERIFIED"),
        ("warm-up：超限", "无 warm-up 阈值，无法判超限",
         dev("startup.device.json"), "UNVERIFIED"),
        ("warm-up：重复运行", "7 冷启按首2/后5 分组重复计算",
         join(dev("startup.device.json"), cmd("startup.cmd.log"), dev("startup-hot.txt")), "PASS"),
        ("warm-up：设备", "emulator-5566 / x86_64 / API34 / PAGESIZE=4096 / APK sha b8503f04",
         join(dev("startup.device.json"), cmd("startup.cmd.log")), "PASS"),
        ("warm-up：arm64（BLOCKED）", "BLOCKED: 无 arm64 真机或镜像",
         fil("docs/acceptance/runbook.md"), "BLOCKED"),
        ("warm-up：日志", "逐轮 am start -W 原始输出与 logcat Displayed",
         join(dev("startup-cold.txt"), dev("logcat-displayed.txt"), cmd("startup.cmd.log")), "PASS"),
        ("warm-up：证据", "startup-stats.json + device JSON + sha256 清单",
         join(dev("startup.device.json"), cmd("startup.cmd.log"), hsh("f-evidence-sha256.txt")), "PASS"),
        ("warm-up：回归", "无同设备同构建历史 warm-up 基线",
         dev("startup.device.json"), "UNVERIFIED"),
    ]
    block("F", 10, warm)

    frame = [
        ("首帧：基线", "gfxinfo 逐轮 Total frames/Janky/percentile；冷启 p50 300-2150ms，7/7 轮有 janky 帧",
         join(dev("gfxinfo.txt"), dev("startup.device.json"), cmd("startup.cmd.log")), "PASS"),
        ("首帧：阈值", "500ms 首屏目标只对真机成立；模拟器 7/7 轮 janky，不作产品判定",
         dev("gfxinfo.txt"), "UNVERIFIED"),
        ("首帧：超限", "7/7 轮被标记 janky（100% 或 80%），未静默通过",
         join(dev("gfxinfo.txt"), cmd("startup.cmd.log")), "PASS"),
        ("首帧：重复运行", "7 轮 gfxinfo 独立 reset/采集",
         join(dev("gfxinfo.txt"), dev("startup.device.json"), cmd("startup.cmd.log")), "PASS"),
        ("首帧：设备", "emulator-5566 / API34 / x86_64 / APK sha b8503f04",
         join(dev("startup.device.json"), cmd("startup.cmd.log")), "PASS"),
        ("首帧：arm64（BLOCKED）", "BLOCKED: 无 arm64 真机或镜像",
         fil("docs/acceptance/runbook.md"), "BLOCKED"),
        ("首帧：日志", "logcat ActivityTaskManager Displayed 与 gfxinfo 原始输出",
         join(dev("logcat-displayed.txt"), dev("gfxinfo.txt"), cmd("startup.cmd.log")), "PASS"),
        ("首帧：证据", "gfxinfo 逐轮文件 + device JSON + sha256 清单",
         join(dev("startup.device.json"), cmd("startup.cmd.log"), hsh("f-evidence-sha256.txt")), "PASS"),
        ("首帧：回归", "无同设备历史首帧基线",
         dev("gfxinfo.txt"), "UNVERIFIED"),
    ]
    block("F", 19, frame)

    export = [
        ("导出耗时：基线", "60题64页 1.216/1.240/1.277s；产品单题 487/597/820ms；长文档 0.367/0.380s",
         join(cmd("export.cmd.log"), test("TEST-export-batch1.xml"), dev("export-batch1.device.json")), "PASS"),
        ("导出耗时：阈值", "用例内置小于5000ms 断言 3/3 OK；MistakeBatchPerf 516/553/538ms",
         join(test("TEST-export-batch1.xml"), test("TEST-export-batch2.xml"), cmd("export.cmd.log")), "PASS"),
        ("导出耗时：超限", "长文档 PAGE_LIMIT_EXCEEDED 负向路径 2/2 OK",
         join(test("TEST-export-long1.xml"), dev("export-long1.device.json"), cmd("export.cmd.log")), "PASS"),
        ("导出耗时：重复运行", "3 轮批量 + 3 轮产品 + 2 轮长文档",
         join(cmd("export.cmd.log"), test("TEST-export-batch2.xml"), test("TEST-export-product1.xml")), "PASS"),
        ("导出耗时：设备", "export.test APK 08ea / emulator-5566 / 原始设备 JSON",
         join(dev("export-batch1.device.json"), cmd("export.cmd.log")), "PASS"),
        ("导出耗时：arm64（BLOCKED）", "BLOCKED: 无 arm64 真机或镜像",
         fil("docs/acceptance/runbook.md"), "BLOCKED"),
        ("导出耗时：日志", "MistakeBatchPerf logcat 与 am instrument 原始输出",
         join(dev("export-batch1.instr.txt"), cmd("export.cmd.log")), "PASS"),
        ("导出耗时：证据", "export.cmd.log + JUnit XML + sha256 清单",
         join(cmd("export.cmd.log"), test("TEST-export-batch1.xml"), hsh("f-evidence-sha256.txt")), "PASS"),
        ("导出耗时：回归", "同 export.test APK 08ea：批次43 518/545/569ms vs 本轮 516/553/538ms，差小于3%；DB 门 p95 127ms 低于350ms 目标（5562 降级环境）",
         join(cmd("export.cmd.log"), test("TEST-export-batch1.xml"), cmd("db-perf.cmd.log"),
              fil(BASE + "/regression-baseline.json")), "PASS"),
    ]
    block("F", 28, export)

    preview = [
        ("预览耗时：基线", "PDF preview 0.332/0.359/0.378s；D-04 visual preview 0.581/0.581s",
         join(cmd("preview.cmd.log"), test("TEST-preview-pdf1.xml"), dev("preview-pdf1.device.json")), "PASS"),
        ("预览耗时：阈值", "无产品预览时延目标；只建基线",
         cmd("preview.cmd.log"), "UNVERIFIED"),
        ("预览耗时：超限", "无预览阈值，无法判超限",
         cmd("preview.cmd.log"), "UNVERIFIED"),
        ("预览耗时：重复运行", "PDF preview 3 轮 + D-04 2 轮",
         join(cmd("preview.cmd.log"), test("TEST-preview-pdf2.xml"), test("TEST-preview-visual1.xml")), "PASS"),
        ("预览耗时：设备", "export.test APK 08ea / data.test c994 / emulator-5566",
         join(dev("preview-pdf1.device.json"), cmd("preview.cmd.log")), "PASS"),
        ("预览耗时：arm64（BLOCKED）", "BLOCKED: 无 arm64 真机或镜像",
         fil("docs/acceptance/runbook.md"), "BLOCKED"),
        ("预览耗时：日志", "am instrument 原始输出与套件 Time",
         join(dev("preview-pdf1.instr.txt"), cmd("preview.cmd.log")), "PASS"),
        ("预览耗时：证据", "preview.cmd.log + JUnit XML + sha256 清单",
         join(cmd("preview.cmd.log"), test("TEST-preview-pdf1.xml"), hsh("f-evidence-sha256.txt")), "PASS"),
        ("预览耗时：回归", "批次43 PDF preview 0.346-0.373s vs 本轮 0.332-0.378s；D-04 0.652/0.672 vs 0.581，data.test APK 不同",
         join(cmd("preview.cmd.log"), test("TEST-preview-pdf1.xml"), fil(BASE + "/regression-baseline.json")), "PASS"),
    ]
    block("F", 37, preview)

    memory = [
        ("内存峰值：基线", "冷启稳态 PSS 156.8MB；批量导出宿主峰值 47.5-54.4MB；预览 28.4-45.1MB；D-04 65.8-66.3MB",
         join(cmd("memory.cmd.log"), dev("meminfo-after-startup.txt"), cmd("export.cmd.log")), "PASS"),
        ("内存峰值：阈值", "200MB 目标未定义进程/相位；产品 UI 仪器化宿主峰值 316MB 不可归因",
         cmd("memory.cmd.log"), "UNVERIFIED"),
        ("内存峰值：超限", "无干净的产品进程内导出相位峰值，无法判超限",
         cmd("memory.cmd.log"), "UNVERIFIED"),
        ("内存峰值：重复运行", "3 轮导出 + 3 轮预览 + 2 轮 D-04 PSS 采样",
         join(cmd("memory.cmd.log"), dev("export-batch1.device.json"), dev("preview-pdf1.device.json")), "PASS"),
        ("内存峰值：设备", "emulator-5566 / APK sha b8503f04 / export.test 08ea",
         join(dev("export-batch1.device.json"), cmd("memory.cmd.log")), "PASS"),
        ("内存峰值：arm64（BLOCKED）", "BLOCKED: 无 arm64 真机或镜像",
         fil("docs/acceptance/runbook.md"), "BLOCKED"),
        ("内存峰值：日志", "50ms PSS 采样原始文件与 memory.cmd.log",
         join(cmd("memory.cmd.log"), dev("export-batch1.pss.txt")), "PASS"),
        ("内存峰值：证据", "memory.cmd.log + device JSON + sha256 清单",
         join(cmd("memory.cmd.log"), dev("export-batch1.device.json"), hsh("f-evidence-sha256.txt")), "PASS"),
        ("内存峰值：回归", "无同设备历史 PSS 基线；批次43 为 emulator-5584 且口径不同",
         cmd("memory.cmd.log"), "UNVERIFIED"),
    ]
    block("F", 46, memory)

    s = soak_state()
    if s["crashed"]:
        soak_baseline = ("长时 2h：基线",
                         f"2h soak 在 {s['crash_elapsed_s']}s 发生 FATAL EXCEPTION（Desired retention 越界）；进程重启，2723s 人工停止，未完成 2h",
                         join(cmd("soak.cmd.log"), dev("soak.device.json"), dev("soak-crash-logcat.txt"),
                              dev("soak-crash-dropbox.txt"), dev("soak-samples.csv")), "FAIL")
        soak_over = ("长时 2h：超限",
                     f"2h 内至少 1 次 FATAL EXCEPTION（SchedulingSettingsScreen.kt:74 → SchedulingOptions 越界），超限路径命中",
                     join(cmd("soak.cmd.log"), dev("soak.device.json"), dev("soak-crash-logcat.txt"),
                          dev("soak-crash-dropbox.txt")), "FAIL")
    elif s["done"]:
        soak_baseline = ("长时 2h：基线",
                         f"monkey 驱动 {s['actual_s']}s；{s['cycles']} cycles；每约60s 采样 PSS/loadavg/dropbox/crash",
                         join(cmd("soak.cmd.log"), dev("soak.device.json"), dev("soak-stats.txt")), "PASS")
        soak_over = ("长时 2h：超限",
                     f"2h 内新增 crash/ANR=0（超限定义：出现崩溃/ANR），PSS max={s['pss_max_kb']}KB 仅登记",
                     join(cmd("soak.cmd.log"), dev("soak.device.json"), dev("soak-samples.csv")), "PASS")
    else:
        soak_baseline = ("长时 2h：基线", "2h soak 尚未完成，未取得完整 7200s 证据",
                         dev("soak-samples.csv"), "UNVERIFIED")
        soak_over = ("长时 2h：超限", "2h soak 尚未完成，无法判超限",
                     dev("soak-samples.csv"), "UNVERIFIED")
    soak = [
        soak_baseline,
        ("长时 2h：阈值", "无 2h 数值阈值；以无崩溃/ANR、PSS 有界为观察",
         cmd("soak.cmd.log"), "UNVERIFIED"),
        soak_over,
        ("长时 2h：重复运行", "单次 2h，未重复第二窗",
         cmd("soak.cmd.log"), "UNVERIFIED"),
        ("长时 2h：设备", "emulator-5566 / API34 / x86_64 / APK sha b8503f04",
         join(dev("soak.device.json"), cmd("soak.cmd.log")), "PASS"),
        ("长时 2h：arm64（BLOCKED）", "BLOCKED: 无 arm64 真机或镜像",
         fil("docs/acceptance/runbook.md"), "BLOCKED"),
        ("长时 2h：日志", "soak-samples.csv + monkey 原始日志",
         join(dev("soak-samples.csv"), dev("soak-monkey-1.log"), cmd("soak.cmd.log")), "PASS"),
        ("长时 2h：证据", "soak.cmd.log + device JSON + sha256 清单",
         join(cmd("soak.cmd.log"), dev("soak.device.json"), hsh("f-evidence-sha256.txt")), "PASS"),
        ("长时 2h：回归", "无历史 2h soak 基线",
         cmd("soak.cmd.log"), "UNVERIFIED"),
    ]
    block("F", 55, soak)

    anr = [
        ("ANR/崩溃签名：基线", "dropbox 中 app 条目 12 条，全部早于本轮窗口；crash buffer 0 行",
         join(dev("anr-crash.device.json"), cmd("anr-crash.cmd.log"), dev("dropbox-after.txt")), "PASS"),
        ("ANR/崩溃签名：阈值", "本轮窗口 2026-11-04T15:23Z..15:54Z 新增 ANR/crash=0（阈值 0）",
         join(cmd("anr-crash.cmd.log"), dev("logcat-crash.txt")), "PASS"),
        ("ANR/崩溃签名：超限", "在 5562 上 am crash 注入一次崩溃，dropbox data_app_crash 计数 0→1，签名被扫描器识别",
         join(cmd("anr-crash-overlimit-5562.cmd.log"), dev("anr-crash-overlimit-5562.device.json"),
              cmd("anr-crash.cmd.log")), "PASS"),
        ("ANR/崩溃签名：重复运行", "6 个仪器化套件 + 手工启停后 dropbox 计数稳定 12",
         join(cmd("anr-crash.cmd.log"), dev("dropbox-after.txt")), "PASS"),
        ("ANR/崩溃签名：设备", "emulator-5566 / APK sha b8503f04 / dropbox 原始快照",
         join(dev("anr-crash.device.json"), cmd("anr-crash.cmd.log")), "PASS"),
        ("ANR/崩溃签名：arm64（BLOCKED）", "BLOCKED: 无 arm64 真机或镜像",
         fil("docs/acceptance/runbook.md"), "BLOCKED"),
        ("ANR/崩溃签名：日志", "logcat crash buffer 与 dropbox 原始快照",
         join(dev("dropbox-after.txt"), cmd("anr-crash.cmd.log")), "PASS"),
        ("ANR/崩溃签名：证据", "anr-crash.cmd.log + device JSON + sha256 清单",
         join(cmd("anr-crash.cmd.log"), dev("anr-crash.device.json"), hsh("f-evidence-sha256.txt")), "PASS"),
        ("ANR/崩溃签名：回归", "无历史 ANR/crash 基线可比",
         cmd("anr-crash.cmd.log"), "UNVERIFIED"),
    ]
    block("F", 64, anr)

    death = [
        ("进程死亡恢复：基线", "am kill 后 pid 为空；重启 pid 23758；冷启 3219ms；UI root 恢复",
         join(dev("process-death.device.json"), cmd("process-death.cmd.log"), dev("process-death-ui.xml")), "PASS"),
        ("进程死亡恢复：阈值", "无进程死亡恢复时延目标",
         dev("process-death.device.json"), "UNVERIFIED"),
        ("进程死亡恢复：超限", "StorageOperationLifecycle 2/2 断言中断进程不恢复 phantom busy",
         join(test("TEST-process-death-unit.xml"), cmd("process-death-unit.cmd.log"),
              dev("process-death-unit.device.json")), "PASS"),
        ("进程死亡恢复：重复运行", "5566 手工 kill/restart + 5584 手工 kill/restart + 2 个 JUnit 方法",
         join(test("TEST-process-death-unit.xml"), cmd("process-death.cmd.log"),
              dev("process-death-repeat-5584.device.json")), "PASS"),
        ("进程死亡恢复：设备", "emulator-5566 / APK sha b8503f04 / 5584 复跑",
         join(dev("process-death.device.json"), cmd("process-death.cmd.log")), "PASS"),
        ("进程死亡恢复：arm64（BLOCKED）", "BLOCKED: 无 arm64 真机或镜像",
         fil("docs/acceptance/runbook.md"), "BLOCKED"),
        ("进程死亡恢复：日志", "kill/restart 原始日志与 UI hierarchy",
         join(dev("process-death-manual.log"), cmd("process-death.cmd.log")), "PASS"),
        ("进程死亡恢复：证据", "process-death.cmd.log + device JSON + sha256 清单",
         join(cmd("process-death.cmd.log"), dev("process-death.device.json"), hsh("f-evidence-sha256.txt")), "PASS"),
        ("进程死亡恢复：回归", "无历史进程死亡恢复基线",
         cmd("process-death.cmd.log"), "UNVERIFIED"),
    ]
    block("F", 73, death)

    bg = [
        ("后台限制/低电低存/多任务：基线", "doze 冷启 3455ms；battery saver 3342ms；rare bucket 3247ms；multitask HOT 474ms",
         join(dev("background.device.json"), cmd("background.cmd.log"), dev("background-restrictions-2.log")), "PASS"),
        ("后台限制/低电低存/多任务：阈值", "无后台限制时延目标；只建基线",
         cmd("background.cmd.log"), "UNVERIFIED"),
        ("后台限制/低电低存/多任务：超限", "低存储尝试：/data 98% 时启动 PID 存在、FATAL/ENOSPC 0，但 UI 未确认，仍 UNVERIFIED",
         join(cmd("background.cmd.log"), cmd("low-storage-5562.cmd.log")), "UNVERIFIED"),
        ("后台限制/低电低存/多任务：重复运行", "每场景 1 次，未重复",
         cmd("background.cmd.log"), "UNVERIFIED"),
        ("后台限制/低电低存/多任务：设备", "emulator-5566 / API34 / APK sha b8503f04",
         join(dev("background.device.json"), cmd("background.cmd.log")), "PASS"),
        ("后台限制/低电低存/多任务：arm64（BLOCKED）", "BLOCKED: 无 arm64 真机或镜像",
         fil("docs/acceptance/runbook.md"), "BLOCKED"),
        ("后台限制/低电低存/多任务：日志", "doze/battery/standby/multitask 原始日志",
         join(dev("background-restrictions-2.log"), cmd("background.cmd.log")), "PASS"),
        ("后台限制/低电低存/多任务：证据", "background.cmd.log + device JSON + sha256 清单",
         join(cmd("background.cmd.log"), dev("background.device.json"), hsh("f-evidence-sha256.txt")), "PASS"),
        ("后台限制/低电低存/多任务：回归", "无历史后台限制基线",
         cmd("background.cmd.log"), "UNVERIFIED"),
    ]
    block("F", 82, bg)

    assert len(out) == 90, len(out)
    return out


def format_row(r) -> str:
    rid, check, method, evidence, verdict = r
    if verdict == "PASS":
        tokens = [t.strip() for t in evidence.split(";") if t.strip()]
        if any(t.startswith("device:") for t in tokens):
            serial = "emulator-5562" if "5562" in evidence else "emulator-5566"
            if "device:" not in method:
                method = method + f"; device: {serial}"
        else:
            test_tokens = [t[len("test:"):] for t in tokens if t.startswith("test:")]
            if test_tokens and "test:" not in method:
                method = method + f"; test: {test_tokens[0]}"
    for text in (check, method):
        assert "|" not in text, text
    return f"| {rid} | F | {check} | {method} | {evidence} | {verdict} |"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    lines = LEDGER.read_text(encoding="utf-8").splitlines()
    idx = [i for i, line in enumerate(lines) if line.startswith("| F-")]
    if len(idx) != 90:
        print(f"F rows={len(idx)} expected 90", file=sys.stderr)
        return 2
    for i in idx:
        parts = [p.strip() for p in lines[i].strip().strip("|").split("|")]
        skeleton = (len(parts) >= 6 and parts[5] in {"UNVERIFIED", "BLOCKED"} and
                    (parts[4] == "-" or (parts[5] == "BLOCKED" and parts[4].startswith("file:"))))
        if not skeleton:
            print(f"refuse: line {i+1} not skeleton: {lines[i]}", file=sys.stderr)
            return 2
    new_rows = [format_row(r) for r in rows()]
    out_lines = list(lines)
    for slot, line in zip(idx, new_rows):
        out_lines[slot] = line
    text = "\n".join(out_lines) + "\n"
    if args.preview:
        p = ROOT / "build" / "f-preview-ledger.md"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding="utf-8")
        print(f"preview={p}")
        return 0
    if args.apply:
        tmp = LEDGER.with_suffix(".md.tmp")
        tmp.write_text(text, encoding="utf-8")
        os.replace(tmp, LEDGER)
        counts = {}
        for r in rows():
            counts[r[4]] = counts.get(r[4], 0) + 1
        print(f"applied rows=90 counts={counts}")
        return 0
    print("use --preview or --apply", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
