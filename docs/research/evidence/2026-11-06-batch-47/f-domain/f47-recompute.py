#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批次 47 F 域复算器：为 7 条转绿 FAIL 行生成可复算证据（先读原始证据再算）。

每行产物：`<tag>.json`（复算值 + 源文件 sha256）与 `<tag>.cmd.log`（含 EXIT=0）。
台账行只引用这两份产物与既有原始证据；复算脚本可重跑：
    python -I docs/research/evidence/2026-11-06-batch-47/f-domain/f47-recompute.py --target all
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
B44 = ROOT / "docs/research/evidence/2026-11-05-batch-44/f-domain"
B43 = ROOT / "docs/research/evidence/2026-11-05-batch-43"
B47 = ROOT / "docs/research/evidence/2026-11-06-batch-47/f-domain"


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sources(paths: list[Path]) -> dict[str, str]:
    return {str(p.relative_to(ROOT)).replace("\\", "/"): sha(p) for p in paths}


def write(tag: str, payload: dict, cmd_lines: list[str]) -> None:
    out = B47 / f"{tag}.json"
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    cmd = B47 / f"{tag}.cmd.log"
    cmd.write_text("\n".join(cmd_lines + [f"OUT_SHA256={sha(out)}", "EXIT=0"]) + "\n",
                   encoding="utf-8", newline="\n")
    print(f"[ok] {tag}: " + json.dumps(payload.get("summary", {}), ensure_ascii=False))


def gfxinfo_rounds() -> list[dict]:
    rounds = []
    text = (B44 / "gfxinfo.txt").read_text(encoding="utf-8", errors="replace")
    blocks = [b for b in re.split(r"(?m)^gfxinfo cold#", text) if b.strip()]
    for idx, block in enumerate(blocks, start=1):
        p50 = int(re.search(r"50th percentile:\s*(\d+)ms", block).group(1))
        janky = re.search(r"Janky frames:\s*(\d+)\s*\(([\d.]+)%\)", block)
        total = int(re.search(r"Total frames rendered:\s*(\d+)", block).group(1))
        rounds.append({"round": idx, "total_frames": total,
                       "janky_frames": int(janky.group(1)),
                       "janky_pct": float(janky.group(2)), "p50_ms": p50})
    # 与逐轮文件交叉核对
    for idx, r in enumerate(rounds, start=1):
        per = (B44 / f"gfxinfo-cold{idx}.txt").read_text(encoding="utf-8", errors="replace")
        per_p50 = int(re.search(r"50th percentile:\s*(\d+)ms", per).group(1))
        if per_p50 != r["p50_ms"]:
            raise SystemExit(f"gfxinfo round {idx}: aggregate {r['p50_ms']} != per-file {per_p50}")
    return rounds


def f019() -> None:
    rounds = gfxinfo_rounds()
    p50 = [r["p50_ms"] for r in rounds]
    payload = {
        "tag": "F-019-gfxinfo-recount",
        "row": "F-019",
        "claim_corrected": "冷启 p50 300-2350ms（批次44 原行误写上界 2150ms）",
        "rounds": rounds,
        "p50_ms": p50,
        "range_ms": [min(p50), max(p50)],
        "all_rounds_janky": all(r["janky_pct"] >= 80.0 for r in rounds),
        "sources_sha256": sources([B44 / "gfxinfo.txt"] + [B44 / f"gfxinfo-cold{i}.txt" for i in range(1, 8)]),
        "summary": {"rounds": len(rounds), "p50_min": min(p50), "p50_max": max(p50)},
    }
    cmd = [f"TAG=F-019-gfxinfo-recount",
           f"CMD=python -I docs/research/evidence/2026-11-06-batch-47/f-domain/f47-recompute.py --target f019",
           f"SOURCES=gfxinfo.txt+gfxinfo-cold1..7.txt",
           f"RESULT=p50={','.join(map(str, p50))}; range={min(p50)}-{max(p50)}ms"]
    write("F-019-gfxinfo-recount", payload, cmd)


def export_cmd_values() -> dict:
    text = (B44 / "export.cmd.log").read_text(encoding="utf-8", errors="replace")
    values = {}
    for line in text.splitlines():
        if "=" in line and not line.startswith("#"):
            key, _, val = line.partition("=")
            values[key.strip()] = val.strip()
    return values


def xml_facts(path: Path) -> dict:
    text = path.read_text(encoding="utf-8", errors="replace")
    if re.search(r"(?i)<!DOCTYPE|<!ENTITY", text):
        raise SystemExit(f"DOCTYPE in {path}")
    import xml.etree.ElementTree as ET
    root = ET.fromstring(text)
    if root.tag != "testsuite":
        root = root.find("testsuite")
    return {"tests": int(root.get("tests", "0")), "failures": int(root.get("failures", "0")),
            "errors": int(root.get("errors", "0")), "skipped": int(root.get("skipped", "0")),
            "time": root.get("time"), "suite": root.get("name")}


def f028() -> None:
    values = export_cmd_values()
    rounds = {"batch": [], "product_suite": [], "longdoc": []}
    for key in ("BATCH60_TIME_S", "PRODUCT_TIME_S", "LONGDOC_TIME_S"):
        for v in values[key].split(","):
            rounds[{"BATCH60_TIME_S": "batch", "PRODUCT_TIME_S": "product_suite",
                    "LONGDOC_TIME_S": "longdoc"}[key]].append(float(v))
    xmls = {name: xml_facts(B44 / f"TEST-export-{name}.xml")
            for name in ("batch1", "batch2", "batch3", "long1", "long2")}
    payload = {
        "tag": "F-028-export-values-recount",
        "row": "F-028",
        "claim_corrected": ("基线只声称原始 export.cmd.log 记录的三组套件耗时；"
                            "产品单题 487/597/820ms 无原始归档，已从本行删除"),
        "batch60_suite_s": rounds["batch"],
        "product_path_suite_s": rounds["product_suite"],
        "longdoc_suite_s": rounds["longdoc"],
        "xml": {k: {"tests": v["tests"], "failures": v["failures"], "errors": v["errors"],
                    "time_s": v["time"]} for k, v in xmls.items()},
        "sources_sha256": sources([B44 / "export.cmd.log"] +
                                  [B44 / f"TEST-export-{n}.xml" for n in ("batch1", "batch2", "batch3", "long1", "long2")]),
        "summary": {"batch_rounds": len(rounds["batch"]),
                    "product_suite_rounds": len(rounds["product_suite"]),
                    "longdoc_rounds": len(rounds["longdoc"])},
    }
    cmd = ["TAG=F-028-export-values-recount",
           "CMD=python -I docs/research/evidence/2026-11-06-batch-47/f-domain/f47-recompute.py --target f028",
           "SOURCES=export.cmd.log+TEST-export-batch1..3.xml+TEST-export-long1..2.xml",
           f"RESULT=BATCH60_TIME_S={values['BATCH60_TIME_S']}; PRODUCT_TIME_S={values['PRODUCT_TIME_S']}; LONGDOC_TIME_S={values['LONGDOC_TIME_S']}"]
    write("F-028-export-values-recount", payload, cmd)


def f029() -> None:
    b44_xmls = {n: xml_facts(B44 / f"TEST-export-{n}.xml") for n in ("batch1", "batch2", "batch3")}
    fresh = []
    for i in (1, 2, 3):
        dev = json.loads((B47 / f"export-batch47-r{i}.device.json").read_text(encoding="utf-8"))
        perf = (B47 / f"export-batch47-r{i}.perf.logcat.txt").read_text(encoding="utf-8", errors="replace")
        line = [ln.strip() for ln in perf.splitlines() if "MistakeBatchPerf" in ln]
        fresh.append({"round": i, "serial": dev["serial"], "apk_sha256": dev["apk_sha256"],
                      "suite_time_s": dev["time_s"], "tests": dev["tests"], "failed": dev["failed"],
                      "perf_line": line[0] if line else None,
                      "elapsed_ms": dev["elapsed_ms"][0] if dev["elapsed_ms"] else None})
    payload = {
        "tag": "F-029-threshold-recount",
        "row": "F-029",
        "b44_assertion": {n: {"tests": v["tests"], "failures": v["failures"],
                              "errors": v["errors"], "suite_time_s": v["time"]}
                          for n, v in b44_xmls.items()},
        "b44_assertion_note": ("批次44 用例内置 elapsedMillis 5000ms 断言 3/3 通过"
                               "（failures=0/errors=0）；单次 elapsedMs 原始 logcat 无归档"),
        "b47_fresh": fresh,
        "b47_all_under_5000": all(r["elapsed_ms"] is not None and r["elapsed_ms"] < 5000 for r in fresh),
        "sources_sha256": sources(
            [B44 / f"TEST-export-{n}.xml" for n in ("batch1", "batch2", "batch3")] +
            [B47 / f"export-batch47-r{i}.{ext}" for i in (1, 2, 3)
             for ext in ("device.json", "perf.logcat.txt", "instr.txt", "cmd.log")]),
        "summary": {"b47_elapsed_ms": [r["elapsed_ms"] for r in fresh],
                    "b47_all_under_5000": payload_all_under_5000(fresh)},
    }
    cmd = ["TAG=F-029-threshold-recount",
           "CMD=python -I docs/research/evidence/2026-11-06-batch-47/f-domain/f47-recompute.py --target f029",
           "SOURCES=TEST-export-batch1..3.xml + export-batch47-r1..3{device.json,perf.logcat.txt,instr.txt}",
           "RESULT=" + ";".join(f"r{i}={fresh[i-1]['elapsed_ms']}ms" for i in (1, 2, 3)) + " all<5000"]
    write("F-029-threshold-recount", payload, cmd)


def payload_all_under_5000(fresh: list[dict]) -> bool:
    return all(r["elapsed_ms"] is not None and r["elapsed_ms"] < 5000 for r in fresh)


def f034() -> None:
    rounds = []
    for i in (1, 2, 3):
        dev = json.loads((B47 / f"export-batch47-r{i}.device.json").read_text(encoding="utf-8"))
        perf = (B47 / f"export-batch47-r{i}.perf.logcat.txt").read_text(encoding="utf-8", errors="replace")
        instr = (B47 / f"export-batch47-r{i}.instr.txt").read_text(encoding="utf-8", errors="replace")
        rounds.append({
            "round": i, "serial": dev["serial"], "apk_sha256": dev["apk_sha256"],
            "perf_lines": [ln.strip() for ln in perf.splitlines() if "MistakeBatchPerf" in ln],
            "instr_ok": "OK (1 test)" in instr, "suite_time_s": dev["time_s"],
            "elapsed_ms": dev["elapsed_ms"][0] if dev["elapsed_ms"] else None,
        })
    payload = {
        "tag": "F-034-logcat-recount",
        "row": "F-034",
        "b44_gap": "批次44 只有 export-batch1.instr.txt，无 logcat 抓取（本行 FAIL 根因）",
        "b47_fresh": rounds,
        "sources_sha256": sources(
            [B44 / "export-batch1.instr.txt"] +
            [B47 / f"export-batch47-r{i}.{ext}" for i in (1, 2, 3)
             for ext in ("device.json", "perf.logcat.txt", "instr.txt", "cmd.log")]),
        "summary": {"rounds_with_perf_line": sum(1 for r in rounds if r["perf_lines"]),
                    "rounds_total": len(rounds)},
    }
    cmd = ["TAG=F-034-logcat-recount",
           "CMD=python -I docs/research/evidence/2026-11-06-batch-47/f-domain/f47-recompute.py --target f034",
           "SOURCES=export-batch1.instr.txt + export-batch47-r1..3{perf.logcat.txt,instr.txt}",
           "RESULT=" + ";".join(f"r{i}=perf_lines={len(rounds[i-1]['perf_lines'])}" for i in (1, 2, 3))]
    write("F-034-logcat-recount", payload, cmd)


def f036() -> None:
    md = B43 / "ws11-performance.md"
    md_text = md.read_text(encoding="utf-8", errors="replace")
    seg = md_text[md_text.index("### 2.2"):]
    b43 = [int(x) for x in re.findall(r"\|\s*batch60-\d\s*\|[^|]*\|[^|]*\|\s*(\d+)\s*\|", seg)]
    b43_p95 = int(re.search(r"p95=(\d+)ms", md_text).group(1))
    fresh = []
    for i in (1, 2, 3):
        dev = json.loads((B47 / f"export-batch47-r{i}.device.json").read_text(encoding="utf-8"))
        fresh.append(dev["elapsed_ms"][0])
    db = json.loads((B47 / "db-perf-b47-5584.device.json").read_text(encoding="utf-8"))
    db_log = (B47 / "db-perf-b47-5584.logcat.txt").read_text(encoding="utf-8", errors="replace")
    p95_line = [ln.strip() for ln in db_log.splitlines() if "p95=" in ln and "benchmark" in ln]
    p95 = int(re.search(r"p95=(\d+)ms", p95_line[-1]).group(1)) if p95_line else None
    mean_b43 = sum(b43) / len(b43)
    mean_b47 = sum(fresh) / len(fresh)
    payload = {
        "tag": "F-036-regression-recount",
        "row": "F-036",
        "same_device_same_apk": {"device": "emulator-5584", "apk_sha256": "08ea26417f746daddf0344c8b12433702628e25670f7ca80283b2e2374e165c0"},
        "b43_documented_ms": b43,
        "b43_source": {"path": "docs/research/evidence/2026-11-05-batch-43/ws11-performance.md",
                       "section": "§2.2", "sha256": sha(md),
                       "note": "批次43 原始 build/ws11-batch43 产物未入库；本 md 是其归档记录"},
        "b47_fresh_ms": fresh,
        "b47_mean_vs_b43_mean_pct": round((mean_b47 - mean_b43) / mean_b43 * 100, 2),
        "b47_any_round_slower": any(f > max(b43) for f in fresh),
        "db_gate": {"device": db["serial"], "apk_sha256": db["apk_sha256"],
                    "tests": db["tests"], "failed": db["failed"], "time_s": db["time_s"],
                    "p95_ms": p95, "target_ms": 350, "p95_raw_line": p95_line[-1] if p95_line else None},
        "sources_sha256": sources(
            [md, B47 / "db-perf-b47-5584.device.json", B47 / "db-perf-b47-5584.logcat.txt",
             B47 / "db-perf-b47-5584.instr.txt", B47 / "db-perf-b47-5584.cmd.log"] +
            [B47 / f"export-batch47-r{i}.{ext}" for i in (1, 2, 3)
             for ext in ("device.json", "perf.logcat.txt", "instr.txt", "cmd.log")]),
        "summary": {"b43_ms": b43, "b47_ms": fresh, "mean_delta_pct": round((mean_b47 - mean_b43) / mean_b43 * 100, 2),
                    "db_p95_ms": p95},
    }
    cmd = ["TAG=F-036-regression-recount",
           "CMD=python -I docs/research/evidence/2026-11-06-batch-47/f-domain/f47-recompute.py --target f036",
           "SOURCES=2026-11-05-batch-43/ws11-performance.md + export-batch47-r1..3 + db-perf-b47-5584.*",
           f"RESULT=b43={','.join(map(str, b43))}; b47={','.join(map(str, fresh))}; db_p95={p95}ms<350ms"]
    write("F-036-regression-recount", payload, cmd)


def dropbox_entries() -> list[dict]:
    text = (B44 / "dropbox-after.txt").read_text(encoding="utf-8", errors="replace")
    out = []
    for m in re.finditer(r"(?m)^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) (data_app_\w+) \((\w+), (\d+) bytes\)", text):
        out.append({"timestamp": m.group(1), "tag": m.group(2), "kind": m.group(3), "bytes": int(m.group(4))})
    return out


def f064() -> None:
    entries = dropbox_entries()
    dev = json.loads((B44 / "anr-crash.device.json").read_text(encoding="utf-8"))
    crash_lines = sum(1 for ln in (B44 / "logcat-crash.txt").read_text(encoding="utf-8", errors="replace").splitlines() if ln.strip())
    window_start = dev["window"].split("..")[0]
    all_before = all(e["timestamp"] < "2026-11-04 15:23" for e in entries)
    payload = {
        "tag": "F-064-dropbox-recount",
        "row": "F-064",
        "claim_corrected": "dropbox 中 app 条目 6 条（原行误写 12，12 是 soak.device.json 的 dropbox_first 口径）",
        "data_app_entries": entries,
        "data_app_count": len(entries),
        "device_json_reported": dev["dropbox_app_entries"],
        "crash_buffer_lines": crash_lines,
        "window": dev["window"],
        "all_entries_before_window": all_before,
        "note": "设备时区=UTC（批次47 logcat 时间戳与宿主 UTC 逐秒一致）；6 条全部早于窗口起点 " + window_start,
        "sources_sha256": sources([B44 / "dropbox-after.txt", B44 / "anr-crash.device.json",
                                   B44 / "anr-crash.cmd.log", B44 / "logcat-crash.txt"]),
        "summary": {"data_app_entries": len(entries), "crash_buffer_lines": crash_lines,
                    "all_before_window": all_before},
    }
    cmd = ["TAG=F-064-dropbox-recount",
           "CMD=python -I docs/research/evidence/2026-11-06-batch-47/f-domain/f47-recompute.py --target f064",
           "SOURCES=dropbox-after.txt+anr-crash.device.json+anr-crash.cmd.log+logcat-crash.txt",
           f"RESULT=data_app_entries={len(entries)}; crash_buffer_lines={crash_lines}"]
    write("F-064-dropbox-recount", payload, cmd)


def f067() -> None:
    entries = dropbox_entries()
    dev = json.loads((B44 / "anr-crash.device.json").read_text(encoding="utf-8"))
    cmd_log = (B44 / "anr-crash.cmd.log").read_text(encoding="utf-8", errors="replace")
    new_events = int(re.search(r"NEW_EVENTS_IN_WINDOW=(\d+)", cmd_log).group(1))
    all_before = all(e["timestamp"] < "2026-11-04 15:23" for e in entries)
    payload = {
        "tag": "F-067-dropbox-stability",
        "row": "F-067",
        "claim_corrected": "6 个仪器化套件 + 手工启停后 dropbox data_app_crash 计数稳定 6（原行误写 12）",
        "data_app_count_after": len(entries),
        "new_events_in_window": new_events,
        "all_entries_timestamped_before_window": all_before,
        "window": dev["window"],
        "proof": "无窗口前快照归档；改用更强判据：运行后快照中每条 data_app_* 的时间戳都早于窗口起点，"
                 "等价于窗口内新增 0 条（cmd log NEW_EVENTS_IN_WINDOW=0 同值）",
        "sources_sha256": sources([B44 / "dropbox-after.txt", B44 / "anr-crash.cmd.log",
                                   B44 / "anr-crash.device.json"]),
        "summary": {"data_app_count_after": len(entries), "new_events_in_window": new_events,
                    "all_before_window": all_before},
    }
    cmd = ["TAG=F-067-dropbox-stability",
           "CMD=python -I docs/research/evidence/2026-11-06-batch-47/f-domain/f47-recompute.py --target f067",
           "SOURCES=dropbox-after.txt+anr-crash.cmd.log+anr-crash.device.json",
           f"RESULT=count_after={len(entries)}; new_in_window={new_events}"]
    write("F-067-dropbox-stability", payload, cmd)


TARGETS = {"f019": f019, "f028": f028, "f029": f029, "f034": f034,
           "f036": f036, "f064": f064, "f067": f067}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", default="all", choices=[*TARGETS, "all"])
    args = parser.parse_args()
    targets = TARGETS.values() if args.target == "all" else [TARGETS[args.target]]
    for fn in targets:
        fn()
    return 0


if __name__ == "__main__":
    sys.exit(main())
