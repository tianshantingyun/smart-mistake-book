#!/usr/bin/env bash
# 批次 47 F 域复跑：跑一个已安装用例，50ms 轮询 PSS，并抓 MistakeBatchPerf logcat。
# 与批次 44 的 f-run-instr.sh 相比，本脚本额外保存 logcat（批次 47 修复 F-029/F-034/F-036
# 的“断言无原始日志”失败）。用法:
#   bash f47-run-instr.sh <serial> <runner_pkg> <class[#method]> <tag> <outdir>
set -u
ADB=${ADB:-/c/Android/Sdk/platform-tools/adb.exe}
SERIAL=${1:?serial}
RUNNER=${2:?runner pkg}
CLASS=${3:?class}
TAG=${4:?tag}
OUT=${5:?outdir}
mkdir -p "$OUT"
RAW="$OUT/$TAG.instr.txt"
PSS="$OUT/$TAG.pss.txt"
CMD="$OUT/$TAG.cmd.log"
LOG="$OUT/$TAG.logcat.txt"
PERF="$OUT/$TAG.perf.logcat.txt"
CRASH="$OUT/$TAG.crash.logcat.txt"
REMOTE="/data/local/tmp/pss-$TAG.log"

APK_PATH=$("$ADB" -s "$SERIAL" shell pm path "$RUNNER" | head -1 | tr -d '\r' | sed 's/package://')
APK_SHA=$("$ADB" -s "$SERIAL" shell "sha256sum '$APK_PATH'" | awk '{print $1}' | tr -d '\r')
ABI=$("$ADB" -s "$SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')
SDK=$("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')

"$ADB" -s "$SERIAL" shell "rm -f '$REMOTE'"
"$ADB" -s "$SERIAL" shell "nohup sh -c 'while :; do p=\$(pidof $RUNNER); if [ -n \"\$p\" ]; then echo \"\$(date +%s%3N) \$(grep \"^Pss:\" /proc/\$p/smaps_rollup 2>/dev/null | awk \"{print \\\$2}\")\" >> $REMOTE; fi; sleep 0.05; done' >/dev/null 2>&1 &" >/dev/null
sleep 1
"$ADB" -s "$SERIAL" logcat -c
"$ADB" -s "$SERIAL" shell am force-stop "$RUNNER"
started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
"$ADB" -s "$SERIAL" shell am instrument -w -r -e class "$CLASS" "$RUNNER/androidx.test.runner.AndroidJUnitRunner" > "$RAW" 2>&1
rc=$?
finished=$(date -u +%Y-%m-%dT%H:%M:%SZ)
"$ADB" -s "$SERIAL" shell "pkill -f 'pss-$TAG.log'" >/dev/null 2>&1 || true
MSYS_NO_PATHCONV=1 "$ADB" -s "$SERIAL" pull "$REMOTE" "$PSS" >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" logcat -d -v threadtime > "$LOG" 2>&1
"$ADB" -s "$SERIAL" logcat -d -v threadtime -s MistakeBatchPerf:I > "$PERF" 2>&1
"$ADB" -s "$SERIAL" logcat -b crash -d > "$CRASH" 2>&1

python -I - "$RAW" "$PSS" "$CMD" "$TAG" "$SERIAL" "$APK_SHA" "$CLASS" "$started" "$finished" "$rc" "$RUNNER" "$PERF" "$ABI" "$SDK" <<'PY'
import json, re, sys
from pathlib import Path
(raw_p, pss_p, cmd_p, tag, serial, sha, cls, started, finished, rc, target,
 perf_p, abi, sdk) = sys.argv[1:15]
raw = Path(raw_p).read_text(encoding="utf-8", errors="replace")
ok = re.search(r"OK \((\d+) tests?\)", raw)
fail = "FAILURES!!!" in raw or "INSTRUMENTATION_FAILED" in raw or "Process crashed" in raw
tests = int(ok.group(1)) if ok else 0
time_m = re.search(r"Time:\s*([0-9.]+)", raw)
pss_text = Path(pss_p).read_text(encoding="utf-8", errors="replace") if Path(pss_p).exists() else ""
samples = [int(p.split()[1]) for p in pss_text.splitlines() if len(p.split()) == 2 and p.split()[1].isdigit()]
perf_text = Path(perf_p).read_text(encoding="utf-8", errors="replace")
perf_lines = [ln.strip() for ln in perf_text.splitlines() if "MistakeBatchPerf" in ln]
elapsed = []
for ln in perf_lines:
    m = re.search(r"elapsedMs=(\d+)", ln)
    if m:
        elapsed.append(int(m.group(1)))
cmd = [f"TAG={tag}", f"SERIAL={serial}", f"TARGET={target}", f"APK_SHA256={sha}",
       f"ABI={abi}", f"SDK={sdk}", f"CLASS={cls}", f"STARTED={started}",
       f"FINISHED={finished}", f"AM_INSTRUMENT_SHELL_EXIT={rc}",
       f"TESTS={tests} FAILURES={1 if fail else 0}",
       f"TIME_S={time_m.group(1) if time_m else 'unknown'}",
       f"PERF_LINES={len(perf_lines)}",
       f"ELAPSED_MS={','.join(str(x) for x in elapsed) if elapsed else 'none'}",
       f"PSS_FIRST_KB={samples[0] if samples else None} PSS_PEAK_KB={max(samples) if samples else None} PSS_SAMPLES={len(samples)}"]
if ok and not fail:
    cmd += ["RESULT=OK", "EXIT=0"]
else:
    cmd += ["RESULT=FAIL", "EXIT=1"]
Path(cmd_p).write_text("\n".join(cmd) + "\n", encoding="utf-8")
meta = {"tag": tag, "serial": serial, "apk_sha256": sha, "abi": abi, "sdk": sdk,
        "target_package": target, "class": cls, "started": started, "finished": finished,
        "tests": tests, "failed": bool(fail), "time_s": time_m.group(1) if time_m else None,
        "perf_lines": len(perf_lines), "elapsed_ms": elapsed,
        "pss_first_kb": samples[0] if samples else None,
        "pss_peak_kb": max(samples) if samples else None, "pss_samples": len(samples),
        "result": "OK" if (ok and not fail) else "FAIL"}
Path(str(cmd_p).replace(".cmd.log", ".device.json")).write_text(
    json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(meta, ensure_ascii=False, indent=2))
PY
exit 0
