#!/usr/bin/env bash
# F 域启动/首帧测量（批次 44）。只读设备状态 + 启停已安装 app，不构建、不改源码。
# 用法: bash f-startup.sh <serial> <cold_rounds> <hot_rounds> <outdir>
set -u
ADB=${ADB:-/c/Android/Sdk/platform-tools/adb.exe}
SERIAL=${1:?serial}
COLD=${2:-7}
HOT=${3:-5}
OUT=${4:?outdir}
PKG=com.tingyun.smartmistakebook.localfirst
ACT=com.tingyun.smartmistakebook.localfirst/com.tingyun.smartmistakebook.MainActivity
mkdir -p "$OUT"

log() { printf '%s\n' "$*" | tee -a "$OUT/startup-run.log"; }

: > "$OUT/startup-cold.txt"
: > "$OUT/startup-hot.txt"
: > "$OUT/gfxinfo.txt"
: > "$OUT/logcat-displayed.txt"

log "START $(date -u +%Y-%m-%dT%H:%M:%SZ) serial=$SERIAL cold=$COLD hot=$HOT"

# 设备身份
{
  echo "serial=$SERIAL"
  echo "model=$($ADB -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
  echo "device=$($ADB -s "$SERIAL" shell getprop ro.product.device | tr -d '\r')"
  echo "abi=$($ADB -s "$SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "sdk=$($ADB -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "page_size=$($ADB -s "$SERIAL" shell getconf PAGESIZE | tr -d '\r')"
  echo "boot_id=$($ADB -s "$SERIAL" shell cat /proc/sys/kernel/random/boot_id | tr -d '\r')"
  echo "apk_path=$($ADB -s "$SERIAL" shell pm path "$PKG" | head -1 | tr -d '\r' | sed 's/package://')"
  echo "apk_sha256=$($ADB -s "$SERIAL" shell "sha256sum \$(pm path "$PKG" | head -1 | sed 's/package://')" | awk '{print $1}' | tr -d '\r')"
  echo "loadavg_before=$($ADB -s "$SERIAL" shell cat /proc/loadavg | tr -d '\r')"
  echo "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$OUT/device-startup-meta.txt"

# 冷启：先 reset gfxinfo，再 force-stop，间隔 2s
for i in $(seq 1 "$COLD"); do
  "$ADB" -s "$SERIAL" shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
  "$ADB" -s "$SERIAL" shell am force-stop "$PKG"
  sleep 2
  start=$(date +%s%3N)
  out=$("$ADB" -s "$SERIAL" shell am start -W -n "$ACT" 2>&1 | tr -d '\r')
  end=$(date +%s%3N)
  wall=$((end-start))
  printf 'cold#%s wall_ms=%s\n%s\n---\n' "$i" "$wall" "$out" >> "$OUT/startup-cold.txt"
  # 首帧/gfxinfo：每轮单独落盘
  "$ADB" -s "$SERIAL" shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo-cold$i.txt" 2>&1
  printf 'gfxinfo cold#%s\n' "$i" >> "$OUT/gfxinfo.txt"
  grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Slow UI thread|Number Slow issue draw" "$OUT/gfxinfo-cold$i.txt" >> "$OUT/gfxinfo.txt" 2>/dev/null
  echo '---' >> "$OUT/gfxinfo.txt"
  sleep 1
done

# 热启：HOME 后同任务前置
for i in $(seq 1 "$HOT"); do
  "$ADB" -s "$SERIAL" shell input keyevent KEYCODE_HOME
  sleep 1
  out=$("$ADB" -s "$SERIAL" shell am start -W -n "$ACT" 2>&1 | tr -d '\r')
  printf 'hot#%s\n%s\n---\n' "$i" "$out" >> "$OUT/startup-hot.txt"
  sleep 1
done

# Displayed 日志与内存快照
"$ADB" -s "$SERIAL" logcat -d -s ActivityTaskManager:I ActivityManager:I 2>/dev/null | grep -E "Displayed $PKG" | tail -20 > "$OUT/logcat-displayed.txt"
"$ADB" -s "$SERIAL" shell dumpsys meminfo "$PKG" > "$OUT/meminfo-after-startup.txt" 2>&1
{
  echo "loadavg_after=$($ADB -s "$SERIAL" shell cat /proc/loadavg | tr -d '\r')"
  echo "finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} >> "$OUT/device-startup-meta.txt"

# 解析：从 am start -W 提取 TotalTime/LaunchState；输出可复算统计
python -I - "$OUT" <<'PY'
import re, statistics, sys, json
from pathlib import Path
out = Path(sys.argv[1])
def parse(path):
    text = path.read_text(encoding="utf-8", errors="replace")
    rows = []
    for block in text.split("---"):
        m = re.search(r"(cold#\d+|hot#\d+)", block)
        t = re.search(r"TotalTime:\s*(\d+)", block)
        s = re.search(r"LaunchState:\s*(\w+)", block)
        if m and t:
            rows.append({"round": m.group(1), "total_ms": int(t.group(1)),
                         "state": s.group(1) if s else None})
    return rows
cold = parse(out/"startup-cold.txt")
hot = parse(out/"startup-hot.txt")
def stats(vals):
    if not vals: return {}
    vals = sorted(vals)
    return {"n": len(vals), "min": vals[0], "p50": statistics.median(vals),
            "p95": vals[min(len(vals)-1, int(round(0.95*(len(vals)-1))))],
            "max": vals[-1], "mean": round(sum(vals)/len(vals), 1)}
summary = {"cold": stats([r["total_ms"] for r in cold]),
           "hot": stats([r["total_ms"] for r in hot]),
           "cold_rounds": cold, "hot_rounds": hot}
if len(cold) >= 3:
    first = [r["total_ms"] for r in cold[:2]]
    rest = [r["total_ms"] for r in cold[2:]]
    summary["warmup_first2_mean"] = round(sum(first)/len(first), 1)
    summary["warmup_plateau_mean"] = round(sum(rest)/len(rest), 1)
    summary["warmup_delta_ms"] = round(summary["warmup_first2_mean"] - summary["warmup_plateau_mean"], 1)
(out/"startup-stats.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
print(json.dumps(summary, ensure_ascii=False, indent=2))
# 超限判定（模拟器口径，仅记录，不作真机产品判定）
target = 2000
over = [r for r in cold if r["total_ms"] > target]
print(f"COLD_OVER_TARGET count={len(over)}/{len(cold)} target_ms={target}")
print("EXIT=0")
PY
