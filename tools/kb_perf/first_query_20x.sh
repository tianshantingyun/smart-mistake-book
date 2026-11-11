#!/usr/bin/env bash
# G-FIRST / G-READ 首查探针编排（判读口径 = b35-render §2.6；b34-render §2.6 同源）。
# 用法: first_query_20x.sh <post-apk> <out-dir> <pkg> <test-pkg> [serial] [rounds] [baseline-apk]
# 输出: <out-dir>/samples-post.csv / samples-baseline.csv（round,kind,t0UtcMs,firstNonEmptyMs,gReadInvokeMs）
#       <out-dir>/env.txt（逐轮设备指纹与健康门读数）
# 退出码: 0=编排完成；2=前置/健康门失败；3=设备操作失败。本脚本不出采纳判词。
set -euo pipefail
POST_APK="${1:?usage: first_query_20x.sh <post-apk> <out-dir> <pkg> <test-pkg> [serial] [rounds] [baseline-apk]}"
OUT="${2:?missing out-dir}"; PKG="${3:?missing pkg}"; TEST_PKG="${4:?missing test-pkg}"
SERIAL="${5:-}"; ROUNDS="${6:-20}"; BASE_APK="${7:-}"
ADB="${ADB:-/c/Android/Sdk/platform-tools/adb.exe}"
S() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }
mkdir -p "$OUT"
[ -f "$POST_APK" ] || { echo "ABORT: post apk missing: $POST_APK" >&2; exit 2; }
[ -n "$BASE_APK" ] && [ ! -f "$BASE_APK" ] && { echo "ABORT: baseline apk missing" >&2; exit 2; }
# 健康门（冻结口径：loadavg 1min ≤1.0 且 MemAvailable ≥1GiB；逐轮落盘）
# §6 登记（执行轮 2026-11-07）：③上一轮的 app+探针双导入会令 1min loadavg 在下一轮
#   门检查时仍 >1.0（实跑 round-2 必红），故门内先等绿（每 15s 复测，上限 15min）
#   再落盘判定——阈值与判定语义不变（仍要求每轮测前 loadavg1≤1.0 且 Mem≥1GiB）。
health() {
  local la mem waited=0
  while [ "$waited" -lt 900 ]; do
    la=$(S shell cat /proc/loadavg | cut -d' ' -f1)
    mem=$(S shell grep MemAvailable /proc/meminfo | awk '{print $2}')
    if awk -v l="$la" 'BEGIN{exit (l<=1.0)?0:1}' && [ "$mem" -ge 1048576 ]; then
      break
    fi
    sleep 15; waited=$((waited+15))
  done
  echo "$(date -u +%FT%TZ) loadavg1=$la memAvailKiB=$mem cooldownWaitedSec=$waited" >> "$OUT/env.txt"
  awk -v l="$la" 'BEGIN{exit (l<=1.0)?0:1}' && [ "$mem" -ge 1048576 ]
}
run_round() { # $1=apk $2=kind
  local apk="$1" kind="$2" i comp t0 line
  for i in $(seq 1 "$ROUNDS"); do
    health || { echo "ABORT: health gate red at round $i" >&2; exit 2; }
    S uninstall "$PKG" >/dev/null 2>&1 || true
    [ -z "$(S shell pm path "$PKG" | tr -d '\r')" ] || { echo "ABORT: pm path not empty" >&2; exit 3; }
    S install -t "$apk" >/dev/null || { echo "ABORT: install failed" >&2; exit 3; }
    S logcat -c
    comp=$(S shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$PKG" | tr -d '\r' | grep / | head -1)
    [ -n "$comp" ] || { echo "ABORT: launcher resolve failed" >&2; exit 3; }
    t0=$(date +%s%3N)
    S shell am start -W "$comp" >/dev/null
    # §6 登记（执行轮 2026-11-07）：① am instrument 组件位必须是注册 runner
    #   （androidx.test.runner.AndroidJUnitRunner），原正文把测试类名放在组件位 →
    #   INSTRUMENTATION_FAILED（实跑两次复现）；② 探针的 FIRST_QUERY_MS=/G_READ_MS=
    #   经 println 落 logcat（System.out 与 KnowledgeFirstQueryProbe 双 tag），am
    #   stdout 不含它们，改从 logcat -d 取行。输入/输出/退出码契约不变（指标缺失
    #   仍走下方 ABORT exit 3）。
    S shell am instrument -w -e launchT0 "$t0" -e round "$i" \
      -e class com.tingyun.smartmistakebook.core.data.knowledge.KnowledgeFirstQueryProbeInstrumentedTest \
      "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner" > "$OUT/instr-$kind-$i.txt" 2>&1 || true
    line=$(S shell logcat -d -v brief 2>/dev/null | grep -E 'FIRST_QUERY_MS=|G_READ_MS=' | tail -2 | tr -d '\r' | paste -sd';' -)
    [ -n "$line" ] || { echo "ABORT: probe emitted no metrics (round $i)" >&2; exit 3; }
    fq=$(printf '%s' "$line" | grep -o 'FIRST_QUERY_MS=[0-9]*' | cut -d= -f2)
    gr=$(printf '%s' "$line" | grep -o 'G_READ_MS=[0-9]*' | cut -d= -f2)
    echo "$i,$kind,$t0,$fq,$gr" >> "$OUT/samples-$kind.csv"
  done
}
: > "$OUT/env.txt"
[ -n "$BASE_APK" ] && run_round "$BASE_APK" baseline
run_round "$POST_APK" post
exit 0
