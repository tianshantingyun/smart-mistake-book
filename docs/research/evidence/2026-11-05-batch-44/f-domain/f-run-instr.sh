#!/usr/bin/env bash
# F 域仪器化测量（批次 44）：跑一个已安装用例，50ms 轮询目标进程 PSS，落原始输出与 JSON。
# 用法: bash f-run-instr.sh <serial> <runner_pkg> <target_pkg> <class[#method]> <tag> <outdir>
set -u
ADB=${ADB:-/c/Android/Sdk/platform-tools/adb.exe}
SERIAL=${1:?serial}
RUNNER=${2:?runner pkg}
TARGET=${3:?target pkg}
CLASS=${4:?class}
TAG=${5:?tag}
OUT=${6:?outdir}
mkdir -p "$OUT"
RAW="$OUT/$TAG.instr.txt"
PSS="$OUT/$TAG.pss.txt"
CMD="$OUT/$TAG.cmd.log"
REMOTE="/data/local/tmp/pss-$TAG.log"

APK_PATH=$("$ADB" -s "$SERIAL" shell pm path "$TARGET" | head -1 | tr -d '\r' | sed 's/package://')
APK_SHA=$("$ADB" -s "$SERIAL" shell "sha256sum '$APK_PATH'" | awk '{print $1}' | tr -d '\r')

"$ADB" -s "$SERIAL" shell "rm -f '$REMOTE'"
# 设备侧 50ms PSS 采样，避免每次 adb 往返
"$ADB" -s "$SERIAL" shell "nohup sh -c 'while :; do p=\$(pidof $TARGET); if [ -n \"\$p\" ]; then echo \"\$(date +%s%3N) \$(grep \"^Pss:\" /proc/\$p/smaps_rollup 2>/dev/null | awk \"{print \\\$2}\")\" >> $REMOTE; fi; sleep 0.05; done' >/dev/null 2>&1 &" >/dev/null
sleep 1
"$ADB" -s "$SERIAL" shell am force-stop "$TARGET"
started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
"$ADB" -s "$SERIAL" shell am instrument -w -r -e class "$CLASS" "$RUNNER/androidx.test.runner.AndroidJUnitRunner" > "$RAW" 2>&1
rc=$?
finished=$(date -u +%Y-%m-%dT%H:%M:%SZ)
"$ADB" -s "$SERIAL" shell "pkill -f 'pss-$TAG.log'" >/dev/null 2>&1 || true
MSYS_NO_PATHCONV=1 "$ADB" -s "$SERIAL" pull "$REMOTE" "$PSS" >/dev/null 2>&1 || true

python -I - "$RAW" "$PSS" "$CMD" "$TAG" "$SERIAL" "$APK_SHA" "$CLASS" "$started" "$finished" "$rc" "$TARGET" <<'PY'
import json, re, sys
from pathlib import Path
raw_p, pss_p, cmd_p, tag, serial, sha, cls, started, finished, rc, target = sys.argv[1:12]
raw = Path(raw_p).read_text(encoding="utf-8", errors="replace")
# AndroidJUnitRunner: OK (n tests) / FAILURES!!! / INSTRUMENTATION_CODE: -1
ok = re.search(r"OK \((\d+) tests?\)", raw)
fail = "FAILURES!!!" in raw or "INSTRUMENTATION_FAILED" in raw or "Process crashed" in raw
tests = int(ok.group(1)) if ok else 0
time_m = re.search(r"Time:\s*([0-9.]+)", raw)
samples = []
pss_text = Path(pss_p).read_text(encoding="utf-8", errors="replace") if Path(pss_p).exists() else ""
for line in pss_text.splitlines():
    parts = line.split()
    if len(parts) == 2 and parts[1].isdigit():
        samples.append(int(parts[1]))
peak = max(samples) if samples else None
first = samples[0] if samples else None
cmd = []
cmd.append(f"TAG={tag}")
cmd.append(f"SERIAL={serial}")
cmd.append(f"TARGET={target}")
cmd.append(f"APK_SHA256={sha}")
cmd.append(f"CLASS={cls}")
cmd.append(f"STARTED={started}")
cmd.append(f"FINISHED={finished}")
cmd.append(f"AM_INSTRUMENT_SHELL_EXIT={rc}")
cmd.append(f"TESTS={tests} FAILURES={1 if fail else 0}")
cmd.append(f"TIME_S={time_m.group(1) if time_m else 'unknown'}")
cmd.append(f"PSS_FIRST_KB={first} PSS_PEAK_KB={peak} PSS_SAMPLES={len(samples)}")
if ok and not fail:
    cmd.append("RESULT=OK")
    cmd.append("EXIT=0")
else:
    cmd.append("RESULT=FAIL")
    cmd.append("EXIT=1")
Path(cmd_p).write_text("\n".join(cmd) + "\n", encoding="utf-8")
meta = {"tag": tag, "serial": serial, "apk_sha256": sha, "target_package": target,
        "class": cls, "started": started, "finished": finished,
        "tests": tests, "failed": bool(fail), "time_s": time_m.group(1) if time_m else None,
        "pss_first_kb": first, "pss_peak_kb": peak, "pss_samples": len(samples),
        "result": "OK" if (ok and not fail) else "FAIL"}
Path(str(cmd_p).replace(".cmd.log", ".device.json")).write_text(
    json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(meta, ensure_ascii=False, indent=2))
PY
exit 0
