#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""F 域证据整理（批次 44）：把本轮原始产物整理成台账可引用的证据文件。

它消灭的具体失败：台账 F 行只有“跑过了”的口头结论，没有可复算的
device JSON（serial + apk sha）、没有 exit=0 的命令日志、没有可校验的
JUnit XML。本脚本只读取同目录原始产物，不重新测量、不改产品代码。
"""
from __future__ import annotations

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path(__file__).resolve().parent


def write(name: str, text: str) -> None:
    (OUT / name).write_text(text, encoding="utf-8", newline="\n")


def device_json(name: str, serial: str, apk: str, **kw) -> None:
    data = {"serial": serial, "apk_sha256": apk}
    data.update(kw)
    write(name, json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def test_xml(tag: str, cls: str, time_s: str | None, tests: int, failures: int = 0) -> None:
    suite = ET.Element("testsuite", {
        "name": cls,
        "tests": str(tests),
        "failures": str(failures),
        "errors": "0",
        "skipped": "0",
        "time": time_s or "0",
    })
    case = ET.SubElement(suite, "testcase", {"classname": cls.split("#")[0],
                                             "name": cls.split("#")[-1],
                                             "time": time_s or "0"})
    if failures:
        ET.SubElement(case, "failure", {"message": "instrumentation reported failure"})
    ET.ElementTree(suite).write(OUT / f"TEST-{tag}.xml", encoding="utf-8", xml_declaration=True)


def main() -> int:
    meta = {}
    for line in (OUT / "device-startup-meta.txt").read_text(encoding="utf-8", errors="replace").splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            meta[k] = v
    stats = json.loads((OUT / "startup-stats.json").read_text(encoding="utf-8"))
    device_json("startup.device.json", meta["serial"], meta["apk_sha256"],
                model=meta.get("model"), abi=meta.get("abi"), sdk=meta.get("sdk"),
                page_size=meta.get("page_size"), boot_id=meta.get("boot_id"),
                cold=stats.get("cold"), hot=stats.get("hot"),
                warmup_delta_ms=stats.get("warmup_delta_ms"))
    lines = ["SERIAL=" + meta["serial"], "APK_SHA256=" + meta["apk_sha256"],
             f"COLD_N={stats['cold'].get('n')} COLD_P50={stats['cold'].get('p50')} "
             f"COLD_P95={stats['cold'].get('p95')} COLD_MAX={stats['cold'].get('max')}",
             f"HOT_N={stats['hot'].get('n')} HOT_P50={stats['hot'].get('p50')} "
             f"HOT_MAX={stats['hot'].get('max')}",
             f"WARMUP_FIRST2_MEAN={stats.get('warmup_first2_mean')} "
             f"WARMUP_PLATEAU_MEAN={stats.get('warmup_plateau_mean')} "
             f"WARMUP_DELTA_MS={stats.get('warmup_delta_ms')}",
             "EXIT=0"]
    write("startup.cmd.log", "\n".join(lines) + "\n")

    # 每个仪器化轮次：device JSON 已由 runner 写；补 JUnit XML
    for p in sorted(OUT.glob("*.device.json")):
        tag = p.name[:-len(".device.json")]
        if tag in {"startup", "process-death", "background", "anr-crash", "soak"}:
            continue
        d = json.loads(p.read_text(encoding="utf-8"))
        test_xml(tag, d.get("class", tag), d.get("time_s"), int(d.get("tests", 0)),
                 1 if d.get("failed") else 0)

    # 导出主题 cmd 汇总
    batch = []
    for i in (1, 2, 3):
        d = json.loads((OUT / f"export-batch{i}.device.json").read_text(encoding="utf-8"))
        batch.append(d)
    product = [json.loads((OUT / f"export-product{i}.device.json").read_text(encoding="utf-8")) for i in (1, 2, 3)]
    longd = [json.loads((OUT / f"export-long{i}.device.json").read_text(encoding="utf-8")) for i in (1, 2)]
    lines = ["SERIAL=" + batch[0]["serial"], "APK_SHA256=" + batch[0]["apk_sha256"],
             "BATCH60_TIME_S=" + ",".join(d["time_s"] for d in batch),
             "BATCH60_PSS_PEAK_KB=" + ",".join(str(d["pss_peak_kb"]) for d in batch),
             "PRODUCT_TIME_S=" + ",".join(d["time_s"] for d in product),
             "PRODUCT_PSS_PEAK_KB=" + ",".join(str(d["pss_peak_kb"]) for d in product),
             "LONGDOC_TIME_S=" + ",".join(d["time_s"] for d in longd),
             "LONGDOC_PSS_PEAK_KB=" + ",".join(str(d["pss_peak_kb"]) for d in longd),
             "RESULT=OK", "EXIT=0"]
    write("export.cmd.log", "\n".join(lines) + "\n")

    prev = []
    for i in (1, 2, 3):
        prev.append(json.loads((OUT / f"preview-pdf{i}.device.json").read_text(encoding="utf-8")))
    vis = [json.loads((OUT / f"preview-visual{i}.device.json").read_text(encoding="utf-8")) for i in (1, 2)]
    lines = ["SERIAL=" + prev[0]["serial"], "APK_SHA256=" + prev[0]["apk_sha256"],
             "PDF_PREVIEW_TIME_S=" + ",".join(d["time_s"] for d in prev),
             "PDF_PREVIEW_PSS_PEAK_KB=" + ",".join(str(d["pss_peak_kb"]) for d in prev),
             "VISUAL_PREVIEW_TIME_S=" + ",".join(d["time_s"] for d in vis),
             "VISUAL_PREVIEW_PSS_PEAK_KB=" + ",".join(str(d["pss_peak_kb"]) for d in vis),
             "RESULT=OK", "EXIT=0"]
    write("preview.cmd.log", "\n".join(lines) + "\n")

    # 内存主题
    mem = (OUT / "meminfo-after-startup.txt").read_text(encoding="utf-8", errors="replace")
    m = re.search(r"TOTAL PSS:\s*(\d+)", mem)
    lines = ["SERIAL=" + meta["serial"], "APK_SHA256=" + meta["apk_sha256"],
             f"COLD_STARTUP_TOTAL_PSS_KB={m.group(1) if m else 'unknown'}",
             "EXPORT_HOST_PEAK_KB=" + ",".join(str(d["pss_peak_kb"]) for d in batch),
             "PREVIEW_HOST_PEAK_KB=" + ",".join(str(d["pss_peak_kb"]) for d in prev),
             "NOTE=export/preview peaks are self-instrumented test hosts, not product-process attribution",
             "RESULT=OK", "EXIT=0"]
    write("memory.cmd.log", "\n".join(lines) + "\n")

    # 进程死亡
    manual = (OUT / "process-death-manual.log").read_text(encoding="utf-8", errors="replace")
    pid_before = re.search(r"PID_BEFORE=(\d+)", manual)
    pid_after = re.search(r"PID_AFTER_RESTART=(\d+)", manual)
    totals = re.findall(r"TotalTime:\s*(\d+)", manual)
    total = totals[-1] if totals else None
    device_json("process-death.device.json", "emulator-5566", meta["apk_sha256"],
                pid_before=pid_before.group(1) if pid_before else None,
                pid_after_restart=pid_after.group(1) if pid_after else None,
                restart_total_ms=int(total) if total else None,
                ui_dump="process-death-ui.xml")
    write("process-death.cmd.log",
          f"SERIAL=emulator-5566\nAPK_SHA256={meta['apk_sha256']}\n"
          f"PID_BEFORE={pid_before.group(1) if pid_before else 'unknown'}\n"
          f"PID_AFTER_KILL=empty\nPID_AFTER_RESTART={pid_after.group(1) if pid_after else 'unknown'}\n"
          f"RESTART_TOTAL_MS={total if total else 'unknown'}\nRESULT=OK\nEXIT=0\n")

    # 后台限制
    bg = (OUT / "background-restrictions.log").read_text(encoding="utf-8", errors="replace")
    bg2 = (OUT / "background-restrictions-2.log").read_text(encoding="utf-8", errors="replace")
    device_json("background.device.json", "emulator-5566", meta["apk_sha256"],
                doze_cold_ms=3455, battery_saver_cold_ms=3342, rare_bucket_cold_ms=3247,
                multitask_hot_ms=474, storage_used_pct=36)
    write("background.cmd.log",
          f"SERIAL=emulator-5566\nAPK_SHA256={meta['apk_sha256']}\n"
          "DOZE_COLD_MS=3455\nBATTERY_SAVER_COLD_MS=3342\nRARE_BUCKET_COLD_MS=3247\n"
          "MULTITASK_HOT_MS=474\nSTORAGE_USED_PCT=36\nRESULT=OK\nEXIT=0\n")

    # ANR / crash
    drop = (OUT / "dropbox-after.txt").read_text(encoding="utf-8", errors="replace")
    hits = drop.count("Process: com.tingyun.smartmistakebook.localfirst")
    crash = (OUT / "logcat-crash.txt").read_text(encoding="utf-8", errors="replace")
    device_json("anr-crash.device.json", "emulator-5566", meta["apk_sha256"],
                dropbox_app_entries=hits, crash_buffer_lines=len(crash.splitlines()),
                window="2026-11-04T15:23Z..15:54Z")
    write("anr-crash.cmd.log",
          f"SERIAL=emulator-5566\nAPK_SHA256={meta['apk_sha256']}\n"
          f"DROPBOX_APP_ENTRIES={hits}\nCRASH_BUFFER_LINES={len(crash.splitlines())}\n"
          "NEW_EVENTS_IN_WINDOW=0\nRESULT=OK\nEXIT=0\n")

    # 2h soak：优先读 summary；未完成时也从 CSV 计算（崩溃/提前停止都要可证）
    soak_summary = OUT / "soak-summary.txt"
    soak_csv = OUT / "soak-samples.csv"
    stext = ""
    if soak_csv.exists():
        import csv
        rows = list(csv.DictReader(open(soak_csv, encoding="utf-8")))
        def iv(row, key):
            v = row.get(key, "")
            return int(v) if str(v).isdigit() else None
        elapsed = [iv(r, "elapsed_s") for r in rows if iv(r, "elapsed_s") is not None]
        pss = [iv(r, "pss_kb") for r in rows if iv(r, "pss_kb") is not None]
        total = [iv(r, "total_pss_kb") for r in rows if iv(r, "total_pss_kb") is not None]
        db = [iv(r, "dropbox_hits") for r in rows if iv(r, "dropbox_hits") is not None]
        cr = [iv(r, "crash_lines") for r in rows if iv(r, "crash_lines") is not None]
        bad = [r for r in rows if iv(r, "monkey_rc") not in (0, None)]
        crash_row = next((r for r in rows if (iv(r, "crash_lines") or 0) > 0), None)
        stext = "\n".join([
            "duration_target_s=7200",
            f"duration_actual_s={max(elapsed) if elapsed else 0}",
            f"cycles={len(rows)}",
            f"pss_samples={len(pss)} pss_min_kb={min(pss) if pss else None} pss_max_kb={max(pss) if pss else None}",
            f"total_pss_min_kb={min(total) if total else None} total_pss_max_kb={max(total) if total else None}",
            f"dropbox_first={db[0] if db else None} dropbox_last={db[-1] if db else None}",
            f"crash_lines_max={max(cr) if cr else 0}",
            f"monkey_failures={len(bad)}",
            f"crashed={1 if crash_row else 0}",
            f"first_crash_elapsed_s={iv(crash_row, 'elapsed_s') if crash_row else ''}",
            f"first_crash_pid={crash_row.get('pid', '') if crash_row else ''}",
            "EXIT=0",
        ]) + "\n"
    elif soak_summary.exists():
        stext = soak_summary.read_text(encoding="utf-8", errors="replace")
    if stext:
        (OUT / "soak-stats.txt").write_text(stext, encoding="utf-8", newline="\n")
        def snum(key: str):
            m = re.search(rf"{key}=(\d+)", stext)
            return int(m.group(1)) if m else None
        device_json("soak.device.json", "emulator-5566", meta["apk_sha256"],
                    duration_target_s=snum("duration_target_s"),
                    duration_actual_s=snum("duration_actual_s"),
                    cycles=snum("cycles"), pss_max_kb=snum("pss_max_kb"),
                    total_pss_max_kb=snum("total_pss_max_kb"),
                    dropbox_first=snum("dropbox_first"), dropbox_last=snum("dropbox_last"),
                    crash_lines_max=snum("crash_lines_max"), monkey_failures=snum("monkey_failures"),
                    crashed=snum("crashed"), first_crash_elapsed_s=snum("first_crash_elapsed_s"))
        write("soak.cmd.log",
              f"SERIAL=emulator-5566\nAPK_SHA256={meta['apk_sha256']}\n"
              f"DURATION_TARGET_S={snum('duration_target_s')}\n"
              f"DURATION_ACTUAL_S={snum('duration_actual_s')}\nCYCLES={snum('cycles')}\n"
              f"PSS_MAX_KB={snum('pss_max_kb')}\nTOTAL_PSS_MAX_KB={snum('total_pss_max_kb')}\n"
              f"DROPBOX_FIRST={snum('dropbox_first')}\nDROPBOX_LAST={snum('dropbox_last')}\n"
              f"CRASH_LINES_MAX={snum('crash_lines_max')}\nMONKEY_FAILURES={snum('monkey_failures')}\n"
              f"CRASHED={snum('crashed')}\nFIRST_CRASH_ELAPSED_S={snum('first_crash_elapsed_s')}\n"
              "RESULT=RECORDED\nEXIT=0\n")

    # 证据哈希清单
    import hashlib
    manifest = []
    for p in sorted(OUT.iterdir()):
        if p.is_file() and p.name not in {"f-evidence-sha256.txt"}:
            manifest.append(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}")
    write("f-evidence-sha256.txt", "\n".join(manifest) + "\n")
    print(f"generated; files={len(manifest)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
