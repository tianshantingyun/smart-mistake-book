#!/usr/bin/env bash
# -*- coding: utf-8 -*-
#
# KD-76 单轮 × 20 次独立 am instrument 采样脚本（批次 41 / WS3 item 19）。
#
# 它消灭的具体失败（批次 40 复核发现 #6 / 记录 §9.1-2）：
#   校准套件原本只有一个"单进程 20 轮"入口，设备轨无法按主口径形状取样；
#   本脚本把每一轮变成一次独立的 `am instrument` 进程，轮与轮之间 reboot
#   （主口径冷启动形状）或 force-stop（冷应用进程形状），安装保持不变，
#   逐轮落 XML/原始输出，交由 tools/ci/summarize_kd76.py 跨轮聚合。
#
# 边界（必须如实）：
#   - 本脚本**不聚合、不出判词**；G1/G2 只由 summarize_kd76.py 计算。
#   - `am instrument` 不产生 JUnit XML，因此本脚本把 runner 的 `am instrument -w`
#     输出 + logcat 中的 `KD76_*` 机器行包成 JUnit-XML 形状的 round-NN.xml；
#     `<system-out>` 是原始证据，不重写、不补造。
#   - reboot 模式用于主口径；force-stop 模式等价于"冷应用进程 + 冷/热 OS 页缓存"，
#     两者在证据里由 `KD76_ROUND ... bootCount=` 区分（force-stop 模式 20 轮 bootCount
#     相同；reboot 模式应得到 20 个不同值）。
#   - 只操作 `--serial` 指定的设备；不做 git 写；不改 APK（`--apk/--test-apk` 仅在
#     开跑前安装一次）。
#
# 用法（仓库根）：
#   tools/ci/run_kd76_rounds.sh --serial emulator-5584
#   tools/ci/run_kd76_rounds.sh --serial emulator-5584 --mode force-stop --rounds 3
#   tools/ci/run_kd76_rounds.sh --serial emulator-5584 \
#       --apk app/build/outputs/apk/offline/debug/app-offline-debug.apk \
#       --test-apk app/build/outputs/apk/androidTest/offline/debug/app-offline-debug-androidTest.apk
#
# 退出码：0 = 全部 20 轮 instrumentation 通过且每轮都落到 SAMPLE 行；
#         1 = 有轮失败/缺样本行；2 = 用法或设备/包前置条件不满足（没有"通过"可言）。

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROUNDS=20
MODE="reboot"
SERIAL="${ANDROID_SERIAL:-}"
OUT_DIR=""
APK=""
TEST_APK=""
TARGET_PACKAGE="com.tingyun.smartmistakebook.offline"
COMPONENT=""
TEST_CLASS="com.tingyun.smartmistakebook.visualjs.VisualJsConnectStageCalibrationInstrumentedTest"
TEST_METHOD="readRoundFromInstrumentationArg"
SETTLE_SECONDS=0
BOOT_TIMEOUT_SECONDS=360
ROUND_TIMEOUT_SECONDS=300

usage() {
  # 头部注释块结束于首个非 `#` 行；写死行号会在注释增删后把 shell 代码当"用法"打印出来。
  awk 'NR >= 3 { if ($0 !~ /^#/) exit; sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
}

die() {
  printf 'run_kd76_rounds: %s\n' "$*" >&2
  exit 2
}

log() {
  printf '[%s] %s\n' "$(date -u +%H:%M:%SZ)" "$*"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="${2:-}"; shift 2 ;;
    --mode) MODE="${2:-}"; shift 2 ;;
    --rounds) ROUNDS="${2:-}"; shift 2 ;;
    --out) OUT_DIR="${2:-}"; shift 2 ;;
    --apk) APK="${2:-}"; shift 2 ;;
    --test-apk) TEST_APK="${2:-}"; shift 2 ;;
    --target-package) TARGET_PACKAGE="${2:-}"; shift 2 ;;
    --component) COMPONENT="${2:-}"; shift 2 ;;
    --class) TEST_CLASS="${2:-}"; shift 2 ;;
    --method) TEST_METHOD="${2:-}"; shift 2 ;;
    --settle-seconds) SETTLE_SECONDS="${2:-}"; shift 2 ;;
    --boot-timeout-seconds) BOOT_TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --round-timeout-seconds) ROUND_TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1 (use --help)" ;;
  esac
done

[ -n "$SERIAL" ] || die "--serial (or ANDROID_SERIAL) is required; refusing to guess a device"
case "$MODE" in
  reboot|force-stop) ;;
  *) die "--mode must be reboot or force-stop, got '$MODE'" ;;
esac
case "$ROUNDS" in
  ''|*[!0-9]*) die "--rounds must be an integer, got '$ROUNDS'" ;;
esac
[ "$ROUNDS" -ge 1 ] && [ "$ROUNDS" -le 20 ] || die "--rounds must be in 1..20, got $ROUNDS"
[ -n "$COMPONENT" ] || COMPONENT="${TARGET_PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"

# ---------------------------------------------------------------- adb 解析

if [ -n "${ADB:-}" ]; then
  ADB_BIN="$ADB"
elif command -v adb >/dev/null 2>&1; then
  ADB_BIN="$(command -v adb)"
elif [ -n "${ANDROID_HOME:-}" ] && [ -x "${ANDROID_HOME//\\//}/platform-tools/adb.exe" ]; then
  ADB_BIN="${ANDROID_HOME//\\//}/platform-tools/adb.exe"
elif [ -n "${ANDROID_HOME:-}" ] && [ -x "${ANDROID_HOME//\\//}/platform-tools/adb" ]; then
  ADB_BIN="${ANDROID_HOME//\\//}/platform-tools/adb"
elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "${ANDROID_SDK_ROOT//\\//}/platform-tools/adb" ]; then
  ADB_BIN="${ANDROID_SDK_ROOT//\\//}/platform-tools/adb"
else
  die "adb not found (set ADB or ANDROID_HOME)"
fi

adb_s() {
  "$ADB_BIN" -s "$SERIAL" "$@"
}

adb_s get-state >/dev/null 2>&1 || die "device '$SERIAL' is not reachable via adb"
[ "$(adb_s get-state 2>/dev/null | tr -d '\r')" = "device" ] || die "device '$SERIAL' is not in 'device' state"

if [ -z "$OUT_DIR" ]; then
  OUT_DIR="$REPO_ROOT/build/kd76-runs/$(date -u +%Y%m%dT%H%M%SZ)-$SERIAL"
fi
mkdir -p "$OUT_DIR" || die "cannot create output dir: $OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

# ---------------------------------------------------------------- 前置检查

if [ -n "$APK" ]; then
  [ -f "$APK" ] || die "APK not found: $APK"
  log "installing target APK once: $APK"
  adb_s install -r -t "$APK" >"$OUT_DIR/install-target.log" 2>&1 || die "target APK install failed (see install-target.log)"
fi
if [ -n "$TEST_APK" ]; then
  [ -f "$TEST_APK" ] || die "test APK not found: $TEST_APK"
  log "installing test APK once: $TEST_APK"
  adb_s install -r -t "$TEST_APK" >"$OUT_DIR/install-test.log" 2>&1 || die "test APK install failed (see install-test.log)"
fi

adb_s shell pm path "$TARGET_PACKAGE" 2>/dev/null | grep -q '^package:' \
  || die "target package '$TARGET_PACKAGE' is not installed on $SERIAL"
adb_s shell pm list instrumentation 2>/dev/null | grep -qF "$COMPONENT" \
  || die "instrumentation component '$COMPONENT' not found (check --component/--target-package)"

DEVICE_FINGERPRINT="$(adb_s shell getprop ro.build.fingerprint 2>/dev/null | tr -d '\r')"
DEVICE_SDK="$(adb_s shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
DEVICE_MODEL="$(adb_s shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
GIT_HEAD="$(git -C "$REPO_ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_DIRTY="$(git -C "$REPO_ROOT" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
GIT_DIRTY="${GIT_DIRTY:-unknown}"
STARTED_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

cat >"$OUT_DIR/manifest.txt" <<EOF
kd76-run
mode=$MODE
serial=$SERIAL
targetPackage=$TARGET_PACKAGE
component=$COMPONENT
testClass=$TEST_CLASS
testMethod=$TEST_METHOD
rounds=$ROUNDS
settleSeconds=$SETTLE_SECONDS
startedUtc=$STARTED_UTC
deviceModel=$DEVICE_MODEL
deviceFingerprint=$DEVICE_FINGERPRINT
deviceSdk=$DEVICE_SDK
sourceHead=$GIT_HEAD
sourceDirtyPaths=$GIT_DIRTY
targetApk=${APK:-<already-installed>}
testApk=${TEST_APK:-<already-installed>}
note=am instrument does not emit JUnit XML; round-NN.xml is a faithful wrapper around the raw am output + KD76_* logcat lines
EOF

log "device=$SERIAL mode=$MODE rounds=$ROUNDS out=$OUT_DIR"
log "source HEAD=$GIT_HEAD dirtyPaths=$GIT_DIRTY"
if [ "$ROUNDS" -ne 20 ]; then
  log "WARNING: n=$ROUNDS != 20; summarize_kd76.py will report NOT EVALUABLE for the main criterion"
fi

# ---------------------------------------------------------------- 工具函数

xml_attr() {
  printf '%s' "$1" | sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' -e 's/"/\&quot;/g'
}

# CDATA 里唯一需要拆的是结束标记；拆成两段 CDATA 后 XML 仍等价。
# 同时删掉 XML 1.0 禁止的控制字符（设备输出/异常栈里可能出现），避免汇总脚本被
# 一个坏字节挡住了全部 20 轮证据。
xml_cdata() {
  printf '%s' "$1" \
    | tr -d '\000-\010\013\014\016-\037' \
    | sed 's/]]>/]]]]><![CDATA[>/g'
}

# GNU timeout 才支持 `timeout DURATION CMD...`；Windows 自带的 timeout.exe 语义不同
# （等待按键），不能拿来包 am instrument。
HAS_GNU_TIMEOUT=0
if command -v timeout >/dev/null 2>&1 && timeout --version 2>/dev/null | grep -q 'GNU coreutils'; then
  HAS_GNU_TIMEOUT=1
fi

wait_for_boot() {
  local deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
  local boot=""
  while [ "$SECONDS" -lt "$deadline" ]; do
    boot="$(adb_s shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    [ "$boot" = "1" ] && break
    sleep 2
  done
  [ "$boot" = "1" ] || return 1
  # boot_completed 之后 package manager 仍可能拒绝 pm 命令；只等它可用，不额外沉降。
  local pm_deadline=$((SECONDS + 120))
  while [ "$SECONDS" -lt "$pm_deadline" ]; do
    adb_s shell pm path "$TARGET_PACKAGE" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

prepare_round() {
  if [ "$MODE" = "reboot" ]; then
    adb_s reboot >/dev/null 2>&1 || return 1
    adb_s wait-for-device >/dev/null 2>&1 || return 1
    wait_for_boot || return 1
    adb_s shell input keyevent 224 >/dev/null 2>&1 || true
  else
    adb_s shell am force-stop "$TARGET_PACKAGE" >/dev/null 2>&1 || return 1
  fi
  if [ "$SETTLE_SECONDS" -gt 0 ]; then
    sleep "$SETTLE_SECONDS"
  fi
  return 0
}

# 单轮：返回 0=pass（且存在 SAMPLE 行），1=fail，2=环境/超时。
run_round() {
  local round="$1"
  local raw="$OUT_DIR/round-$(printf '%02d' "$round").am.txt"
  local xml="$OUT_DIR/round-$(printf '%02d' "$round").xml"
  local load_before load_after duration start_ts end_ts

  load_before="$(adb_s shell cat /proc/loadavg 2>/dev/null | tr -d '\r' || true)"
  adb_s logcat -c >/dev/null 2>&1 || true
  start_ts="$(date +%s)"

  local cmd=("$ADB_BIN" -s "$SERIAL" shell am instrument -w
    -e class "${TEST_CLASS}#${TEST_METHOD}"
    -e smb.kd76.round "$round"
    "$COMPONENT")

  if [ "$HAS_GNU_TIMEOUT" -eq 1 ]; then
    timeout "$ROUND_TIMEOUT_SECONDS" "${cmd[@]}" >"$raw" 2>&1
  else
    "${cmd[@]}" >"$raw" 2>&1
  fi
  local rc=$?
  end_ts="$(date +%s)"
  duration=$((end_ts - start_ts))
  load_after="$(adb_s shell cat /proc/loadavg 2>/dev/null | tr -d '\r' || true)"

  # logd 是异步落 buffer；instrumentation 进程退出后给一次短暂 flush，避免把
  # "本轮有样本" 误判成 "没抓到 KD76_SAMPLE"（sleep 在轮次计时之外，不影响段值）。
  sleep 0.5
  local kd_lines
  kd_lines="$(adb_s logcat -d -v brief 2>/dev/null | tr -d '\r' | grep -E 'KD76_(ROUND|META|SAMPLE)' || true)"
  # logcat 可能把同一行重复（多 buffer/多 tag），保留原始顺序，汇总脚本按 round 取唯一行。
  local sample_line
  sample_line="$(printf '%s\n' "$kd_lines" | grep -E "KD76_SAMPLE round=${round} status=SAMPLE" | tail -n 1 || true)"

  local failures=0 failure_message=""
  if [ "$rc" -ne 0 ]; then
    failures=1
    failure_message="am instrument exited $rc (timeout or transport failure)"
  elif grep -q 'FAILURES!!!' "$raw" 2>/dev/null; then
    failures=1
    failure_message="am instrument reported FAILURES!!!"
  elif grep -q 'INSTRUMENTATION_FAILED' "$raw" 2>/dev/null; then
    failures=1
    failure_message="am instrument reported INSTRUMENTATION_FAILED"
  elif ! grep -qE '^OK \([0-9]+ test' "$raw" 2>/dev/null; then
    failures=1
    failure_message="am instrument did not report 'OK (1 test)'"
  elif [ -z "$sample_line" ]; then
    failures=1
    failure_message="instrumentation passed but no KD76_SAMPLE status=SAMPLE line was found in logcat"
  fi

  {
    printf '%s\n' '<?xml version="1.0" encoding="UTF-8"?>'
    printf '<testsuite name="%s" tests="1" failures="%s" errors="0" skipped="0" time="%s">\n' \
      "$(xml_attr "$TEST_CLASS")" "$failures" "$duration"
    printf '  <testcase classname="%s" name="%s" time="%s">\n' \
      "$(xml_attr "$TEST_CLASS")" "$(xml_attr "$TEST_METHOD")" "$duration"
    printf '    <system-out><![CDATA[\n'
    xml_cdata "KD76_ENV round=$round mode=$MODE loadBefore=$(printf '%s' "$load_before" | tr -s ' ' '_') loadAfter=$(printf '%s' "$load_after" | tr -s ' ' '_')"
    printf '\n'
    printf '%s\n' "$kd_lines"
    printf '%s\n' '--- am instrument output ---'
    xml_cdata "$(cat "$raw")"
    printf '\n]]></system-out>\n'
    if [ "$failures" -ne 0 ]; then
      printf '    <failure message="%s">%s</failure>\n' \
        "$(xml_attr "$failure_message")" "$(xml_cdata "$(tail -n 20 "$raw")")"
    fi
    printf '  </testcase>\n</testsuite>\n'
  } >"$xml"

  if [ "$failures" -ne 0 ]; then
    log "round $round FAIL: $failure_message (see $raw)"
    return 1
  fi
  log "round $round OK ${duration}s"
  return 0
}

# ---------------------------------------------------------------- 主循环

FAILED=0
for round in $(seq 1 "$ROUNDS"); do
  log "round $round/$ROUNDS: preparing ($MODE)"
  if ! prepare_round; then
    log "round $round: device preparation failed (boot/force-stop timeout)"
    # 仍然写一份失败 XML，让汇总脚本看到"该轮没有样本"，而不是静默缺文件。
    cat >"$OUT_DIR/round-$(printf '%02d' "$round").xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="$(xml_attr "$TEST_CLASS")" tests="1" failures="1" errors="0" skipped="0" time="0">
  <testcase classname="$(xml_attr "$TEST_CLASS")" name="$(xml_attr "$TEST_METHOD")" time="0">
    <system-out><![CDATA[
KD76_ENV round=$round mode=$MODE preparation=FAILED
]]></system-out>
    <failure message="device preparation failed (reboot/force-stop timeout)">round $round never produced a sample</failure>
  </testcase>
</testsuite>
EOF
    FAILED=$((FAILED + 1))
    continue
  fi
  if ! run_round "$round"; then
    FAILED=$((FAILED + 1))
  fi
done

FINISHED_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
{
  printf 'finishedUtc=%s\n' "$FINISHED_UTC"
  printf 'failedRounds=%s\n' "$FAILED"
  printf 'roundsRequested=%s\n' "$ROUNDS"
} >>"$OUT_DIR/manifest.txt"

log "done: $((ROUNDS - FAILED))/$ROUNDS rounds passed; evidence in $OUT_DIR"
log "next: python -I tools/ci/summarize_kd76.py '$OUT_DIR'"
if [ "$FAILED" -ne 0 ]; then
  exit 1
fi
exit 0
