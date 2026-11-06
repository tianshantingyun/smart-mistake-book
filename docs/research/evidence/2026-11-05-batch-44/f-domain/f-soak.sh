#!/usr/bin/env bash
# F 域 2h 稳定性 soak（批次 44）。在专用模拟器上以 monkey 驱动真实 UI，周期采样 PSS/ANR/crash。
# 用法: bash f-soak.sh <serial> <seconds> <outdir>
set -u
ADB=${ADB:-/c/Android/Sdk/platform-tools/adb.exe}
SERIAL=${1:?serial}
DURATION=${2:-7200}
OUT=${3:?outdir}
PKG=com.tingyun.smartmistakebook.localfirst
ACT=$PKG/com.tingyun.smartmistakebook.MainActivity
CSV="$OUT/soak-samples.csv"
mkdir -p "$OUT"
: > "$CSV"
echo "ts_utc,elapsed_s,cycle,pid,pss_kb,total_pss_kb,loadavg,dropbox_hits,crash_lines,monkey_rc" >> "$CSV"

start=$(date +%s)
"$ADB" -s "$SERIAL" shell svc power stayon true >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell am start -W -n "$ACT" >/dev/null 2>&1 || true
cycle=0
while :; do
  now=$(date +%s); elapsed=$((now-start))
  [ "$elapsed" -ge "$DURATION" ] && break
  cycle=$((cycle+1))
  "$ADB" -s "$SERIAL" shell monkey -p "$PKG" --throttle 1000 --pct-syskeys 0 --pct-appswitch 0 --pct-anyevent 0 -v 300 > "$OUT/soak-monkey-$cycle.log" 2>&1
  mrc=$?
  pid=$("$ADB" -s "$SERIAL" shell pidof "$PKG" 2>/dev/null | tr -d '\r')
  pss=""
  if [ -n "$pid" ]; then
    pss=$("$ADB" -s "$SERIAL" shell "grep '^Pss:' /proc/$pid/smaps_rollup 2>/dev/null | awk '{print \$2}'" | tr -d '\r')
  else
    "$ADB" -s "$SERIAL" shell am start -W -n "$ACT" >/dev/null 2>&1 || true
    sleep 2
    pid=$("$ADB" -s "$SERIAL" shell pidof "$PKG" 2>/dev/null | tr -d '\r')
  fi
  total=$("$ADB" -s "$SERIAL" shell dumpsys meminfo "$PKG" 2>/dev/null | awk '/TOTAL PSS:/{print $3; exit}' | tr -d '\r')
  load=$("$ADB" -s "$SERIAL" shell cat /proc/loadavg 2>/dev/null | tr -d '\r' | cut -d' ' -f1-3)
  db=$("$ADB" -s "$SERIAL" shell "dumpsys dropbox --print 2>/dev/null | grep -c '$PKG'" | tr -d '\r')
  cr=$("$ADB" -s "$SERIAL" shell logcat -b crash -d 2>/dev/null | wc -l | tr -d '\r')
  echo "$(date -u +%Y-%m-%dT%H:%M:%SZ),$elapsed,$cycle,$pid,$pss,$total,$load,$db,$cr,$mrc" >> "$CSV"
done
"$ADB" -s "$SERIAL" shell svc power stayon false >/dev/null 2>&1 || true
python -I - "$CSV" "$OUT/soak-summary.txt" "$DURATION" <<'PY'
import csv, sys
from pathlib import Path
rows=list(csv.DictReader(open(sys.argv[1],encoding='utf-8')))
pss=[int(r['pss_kb']) for r in rows if r['pss_kb'].isdigit()]
tot=[int(r['total_pss_kb']) for r in rows if r['total_pss_kb'].isdigit()]
db=[int(r['dropbox_hits']) for r in rows if r['dropbox_hits'].isdigit()]
cr=[int(r['crash_lines']) for r in rows if r['crash_lines'].isdigit()]
lines=[f"duration_target_s={sys.argv[3]}", f"cycles={len(rows)}",
       f"pss_samples={len(pss)} pss_min_kb={min(pss) if pss else None} pss_max_kb={max(pss) if pss else None}",
       f"total_pss_min_kb={min(tot) if tot else None} total_pss_max_kb={max(tot) if tot else None}",
       f"dropbox_first={db[0] if db else None} dropbox_last={db[-1] if db else None}",
       f"crash_lines_first={cr[0] if cr else None} crash_lines_last={cr[-1] if cr else None}",
       "EXIT=0"]
Path(sys.argv[2]).write_text("\n".join(lines)+"\n",encoding='utf-8')
print("\n".join(lines))
PY
