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
#     两者在证据里由 `KD76_ENV ... mode=` 区分（force-stop 模式 20 轮 boot_id 相同；
#     reboot 模式必须得到 20 个不同的宿主 boot_id）。
#   - 每轮在 `am instrument` 前后各读一次宿主 `/proc/sys/kernel/random/boot_id`；
#     缺失/重复/轮中变化/测量前 :visual_js 已存在 → 该轮 INVALID（不补轮）。
#   - `--first-round` 用于两批 10+10：批 A 1..10、批 B 11..20，全局轮号唯一；
#     合并只合并原始样本，不合并两批 p95。
#   - A6（批次 43 / WS2 item 11）：`--batch-id A|B --run-id <nonce> --a6-plan <file>`
#     打开"同构建两次独立"证据面——manifest 增 `batchId/runId/a6PlanSha256/
#     installedAppSha256/installedTestSha256/deviceGenerationId/installGenerationId/
#     逐文件哈希`，每轮 `KD76_ENV` 增 `roundStartedUtc/roundFinishedUtc/screenAwake/
#     pidWorkerAfter/warmupFinishedCount/warmupFailedCount/la1/nproc`，并在批末写
#     `manifest.sha256`。判词仍只由 `summarize_kd76.py`（每批）与
#     `kd76_a6_verify.py`（两批合取）给出，本脚本不判词。
#   - 只操作 `--serial` 指定的设备；不做 git 写；不改 APK（`--apk/--test-apk` 仅在
#     开跑前安装一次）。
#
# 用法（仓库根）：
#   tools/ci/run_kd76_rounds.sh --serial emulator-5584
#   tools/ci/run_kd76_rounds.sh --serial emulator-5584 --mode force-stop --rounds 3
#   tools/ci/run_kd76_rounds.sh --serial emulator-5584 \
#       --apk app/build/outputs/apk/offline/debug/app-offline-debug.apk \
#       --test-apk app/build/outputs/apk/androidTest/offline/debug/app-offline-debug-androidTest.apk
#   tools/ci/run_kd76_rounds.sh --serial emulator-5584 --batch-id A --run-id a1b2c3 \
#       --a6-plan build/kd76-runs/a6/a6-plan.txt --out build/kd76-runs/a6/batchA
#
# 退出码：0 = 全部 20 轮 instrumentation 通过且每轮都落到 SAMPLE 行；
#         1 = 有轮失败/缺样本行；2 = 用法或设备/包前置条件不满足（没有"通过"可言）。

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROUNDS=20
FIRST_ROUND=1
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
BATCH_ID=""
RUN_ID=""
A6_PLAN=""
IDENTITY_JSON=""
PYTHON_BIN="${PYTHON:-python}"

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
    --first-round) FIRST_ROUND="${2:-}"; shift 2 ;;
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
    --batch-id) BATCH_ID="${2:-}"; shift 2 ;;
    --run-id) RUN_ID="${2:-}"; shift 2 ;;
    --a6-plan) A6_PLAN="${2:-}"; shift 2 ;;
    --identity-json) IDENTITY_JSON="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1 (use --help)" ;;
  esac
done

A6_MODE=0
if [ -n "$BATCH_ID" ] || [ -n "$RUN_ID" ] || [ -n "$A6_PLAN" ]; then
  A6_MODE=1
  [ -n "$BATCH_ID" ] || die "--batch-id is required when any A6 flag is used"
  case "$BATCH_ID" in A|B) ;; *) die "--batch-id must be A or B, got '$BATCH_ID'" ;; esac
  [ -n "$RUN_ID" ] || die "--run-id is required in A6 mode (two batches must be distinguishable)"
  [ -f "$A6_PLAN" ] || die "--a6-plan not found: $A6_PLAN"
  # 预注册轮号：A6 的每批都必须完整 1..20（10+10 不是 A6）。
  [ "$ROUNDS" -eq 20 ] && [ "$FIRST_ROUND" -eq 1 ] \
    || die "A6 mode requires --rounds 20 --first-round 1 (got $ROUNDS/$FIRST_ROUND)"
  [ "$MODE" = "reboot" ] || die "A6 mode requires --mode reboot"
fi

[ -n "$SERIAL" ] || die "--serial (or ANDROID_SERIAL) is required; refusing to guess a device"
case "$MODE" in
  reboot|force-stop) ;;
  *) die "--mode must be reboot or force-stop, got '$MODE'" ;;
esac
case "$ROUNDS" in
  ''|*[!0-9]*) die "--rounds must be an integer, got '$ROUNDS'" ;;
esac
[ "$ROUNDS" -ge 1 ] && [ "$ROUNDS" -le 20 ] || die "--rounds must be in 1..20, got $ROUNDS"
case "$FIRST_ROUND" in
  ''|*[!0-9]*) die "--first-round must be an integer, got '$FIRST_ROUND'" ;;
esac
# 两批 10+10 合并时批 A 用 1..10、批 B 用 11..20；全局轮号必须仍在 1..20 内，
# 否则汇总脚本会判 unexpected round ids。
[ "$FIRST_ROUND" -ge 1 ] && [ $((FIRST_ROUND + ROUNDS - 1)) -le 20 ] \
  || die "--first-round=$FIRST_ROUND with --rounds=$ROUNDS exceeds the 1..20 main-criterion range"
LAST_ROUND=$((FIRST_ROUND + ROUNDS - 1))
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

# 宿主侧 APK sha 是设备绿的身份锚：app 自报的 sha 可能缺行，汇总不能只信 app。
host_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    printf 'unknown'
  fi
}
TARGET_APK_SHA="<already-installed>"
TEST_APK_SHA="<already-installed>"
if [ -n "$APK" ]; then TARGET_APK_SHA="$(host_sha256 "$APK")"; fi
if [ -n "$TEST_APK" ]; then TEST_APK_SHA="$(host_sha256 "$TEST_APK")"; fi

# A6 身份面：只信宿主拉包算出的已安装 sha 与设备/安装代际（协议 §3.1–§3.3）。
# 非 A6 运行不产出这些字段；A6 运行缺任何一项都不得静默继续。
DEVICE_NPROC="$(adb_s shell nproc 2>/dev/null | tr -d '\r')"
AVD_NAME="NA"
EMULATOR_INSTANCE="NA"
INSTALLED_APP_SHA=""
INSTALLED_TEST_SHA=""
DEVICE_GENERATION_ID=""
INSTALL_GENERATION_ID=""
FIRST_INSTALL_TIME=""
LAST_UPDATE_TIME=""
A6_PLAN_SHA=""
if [ "$A6_MODE" -eq 1 ]; then
  A6_PLAN_SHA="$(host_sha256 "$A6_PLAN")"
  [ "$A6_PLAN_SHA" != "unknown" ] || die "cannot hash --a6-plan (no sha256sum/shasum)"
  if [ -z "$IDENTITY_JSON" ]; then
    IDENTITY_JSON="$OUT_DIR/identity.json"
    "$PYTHON_BIN" -I "$REPO_ROOT/tools/ci/kd76_identity.py" \
      --serial "$SERIAL" --app-package "$TARGET_PACKAGE" \
      --test-package "${TARGET_PACKAGE}.test" \
      --work-dir "$OUT_DIR/identity" --json >"$IDENTITY_JSON" \
      || die "identity collection failed (see $OUT_DIR/identity.json.stderr); refusing to run A6 without installed/device identity"
  fi
  [ -s "$IDENTITY_JSON" ] || die "identity json is empty: $IDENTITY_JSON"
  json_field() {
    "$PYTHON_BIN" -I - "$IDENTITY_JSON" "$1" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    print(json.load(handle).get(sys.argv[2], ""))
PY
  }
  INSTALLED_APP_SHA="$(json_field installedAppSha256)"
  INSTALLED_TEST_SHA="$(json_field installedTestSha256)"
  DEVICE_GENERATION_ID="$(json_field deviceGenerationId)"
  INSTALL_GENERATION_ID="$(json_field installGenerationId)"
  FIRST_INSTALL_TIME="$(json_field appFirstInstallTime)"
  LAST_UPDATE_TIME="$(json_field appLastUpdateTime)"
  AVD_NAME="$(json_field avdName)"
  EMULATOR_INSTANCE="$(json_field emulatorInstanceId)"
  for pair in "installedAppSha256:$INSTALLED_APP_SHA" "installedTestSha256:$INSTALLED_TEST_SHA" \
              "deviceGenerationId:$DEVICE_GENERATION_ID" "installGenerationId:$INSTALL_GENERATION_ID"; do
    [ -n "${pair#*:}" ] || die "identity json missing ${pair%%:*}"
  done
  # 安装后（--apk）再拉一次 sha：安装产物与已安装字节必须一致，否则这一批绑错构建。
  if [ -n "$APK" ] && [ "$TARGET_APK_SHA" != "$INSTALLED_APP_SHA" ]; then
    die "installed app sha $INSTALLED_APP_SHA != --apk $TARGET_APK_SHA (install did not take effect)"
  fi
  if [ -n "$TEST_APK" ] && [ "$TEST_APK_SHA" != "$INSTALLED_TEST_SHA" ]; then
    die "installed test sha $INSTALLED_TEST_SHA != --test-apk $TEST_APK_SHA (install did not take effect)"
  fi
fi

BOOT_IDS_FILE="$OUT_DIR/boot-ids.txt"
: >"$BOOT_IDS_FILE"

cat >"$OUT_DIR/manifest.txt" <<EOF
kd76-run
mode=$MODE
serial=$SERIAL
targetPackage=$TARGET_PACKAGE
component=$COMPONENT
testClass=$TEST_CLASS
testMethod=$TEST_METHOD
rounds=$ROUNDS
firstRound=$FIRST_ROUND
lastRound=$LAST_ROUND
preregisteredN=20
estimator=nearest-rank
gateMillis=5000
settleSeconds=$SETTLE_SECONDS
startedUtc=$STARTED_UTC
deviceModel=$DEVICE_MODEL
deviceFingerprint=$DEVICE_FINGERPRINT
deviceSdk=$DEVICE_SDK
sourceHead=$GIT_HEAD
sourceDirtyPaths=$GIT_DIRTY
targetApk=${APK:-<already-installed>}
testApk=${TEST_APK:-<already-installed>}
targetApkSha256=$TARGET_APK_SHA
testApkSha256=$TEST_APK_SHA
batchId=${BATCH_ID:-<none>}
runId=${RUN_ID:-<none>}
a6Plan=${A6_PLAN:-<none>}
a6PlanSha256=${A6_PLAN_SHA:-<none>}
installedAppSha256=${INSTALLED_APP_SHA:-<none>}
installedTestSha256=${INSTALLED_TEST_SHA:-<none>}
deviceGenerationId=${DEVICE_GENERATION_ID:-<none>}
installGenerationId=${INSTALL_GENERATION_ID:-<none>}
firstInstallTime=${FIRST_INSTALL_TIME:-<none>}
lastUpdateTime=${LAST_UPDATE_TIME:-<none>}
deviceNproc=${DEVICE_NPROC:-<unknown>}
avdName=$AVD_NAME
emulatorInstanceId=$EMULATOR_INSTANCE
replacementCount=0
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

# 宿主侧轮次身份。boot_id 是"本次 boot"的内核 UUID（proc_random, 0444）；
# app 读不到 /proc，宿主 adb 是主证明。bootCount 是 app Settings.Global 的交叉核对。
read_boot_id() {
  adb_s shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r\n'
}
read_host_boot_count() {
  adb_s shell settings get global boot_count 2>/dev/null | tr -d '\r\n'
}
read_pid() {
  adb_s shell pidof "$1" 2>/dev/null | tr -d '\r\n'
}
read_apps_frozen() {
  adb_s shell dumpsys activity 2>/dev/null | tr -d '\r' \
    | grep -m1 'Apps frozen:' | sed 's/.*Apps frozen:[[:space:]]*//' || true
}

# 轮次因宿主身份缺陷（boot_id 不可读/重复/轮中变化、worker 已热）作废时，
# 仍写一份 XML 并明确标 INVALID——不是"缺文件"，也不补一轮凑 20。
write_invalid_round() {
  local round="$1" message="$2" env_line="$3"
  local xml="$OUT_DIR/round-$(printf '%02d' "$round").xml"
  {
    printf '%s\n' '<?xml version="1.0" encoding="UTF-8"?>'
    printf '<testsuite name="%s" tests="1" failures="1" errors="0" skipped="0" time="0">\n' \
      "$(xml_attr "$TEST_CLASS")"
    printf '  <testcase classname="%s" name="%s" time="0">\n' \
      "$(xml_attr "$TEST_CLASS")" "$(xml_attr "$TEST_METHOD")"
    printf '    <system-out><![CDATA[\n'
    xml_cdata "$env_line"
    printf '\nKD76_SAMPLE round=%s status=INVALID reason=%s\n' \
      "$round" "$(printf '%s' "$message" | tr '\n' ' ')"
    printf '\n]]></system-out>\n'
    printf '    <failure message="%s">%s</failure>\n' \
      "$(xml_attr "$message")" "$(xml_cdata "$message")"
    printf '  </testcase>\n</testsuite>\n'
  } >"$xml"
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
  local boot_before boot_after host_boot pid_target pid_worker apps_frozen
  local pid_worker_after round_started_utc round_finished_utc screen_awake la1
  local warmup_finished_count warmup_failed_count
  local boot_error="" env_line=""

  # 轮前身份：reboot 模式必须拿到宿主 boot_id，且与之前所有轮不同；测量前
  # :visual_js 必须不存在（否则测到的是热进程/解冻，不是冷启动）。
  boot_before="$(read_boot_id)"
  host_boot="$(read_host_boot_count)"
  pid_target="$(read_pid "$TARGET_PACKAGE")"
  pid_worker="$(read_pid "${TARGET_PACKAGE}:visual_js")"
  apps_frozen="$(read_apps_frozen)"

  if [ "$MODE" = "reboot" ]; then
    if [ -z "$boot_before" ]; then
      boot_error="host boot_id is unreadable before round $round"
    elif grep -Fxq -- "$boot_before" "$BOOT_IDS_FILE"; then
      boot_error="host boot_id repeated before round $round: $boot_before (same boot as an earlier round)"
    fi
  fi
  if [ -n "$pid_worker" ]; then
    boot_error="${boot_error:+$boot_error; }hot :visual_js worker already running before round $round (pid $pid_worker)"
  fi
  if [ -n "$boot_error" ]; then
    env_line="KD76_ENV round=$round mode=$MODE bootIdBefore=$boot_before bootIdAfter= hostBootCount=$host_boot pidTarget=$pid_target pidWorker=$pid_worker appsFrozen=$apps_frozen"
    write_invalid_round "$round" "$boot_error" "$env_line"
    log "round $round INVALID: $boot_error"
    return 1
  fi
  if [ -n "$boot_before" ]; then
    printf '%s\n' "$boot_before" >>"$BOOT_IDS_FILE"
  fi

  load_before="$(adb_s shell cat /proc/loadavg 2>/dev/null | tr -d '\r' || true)"
  adb_s logcat -c >/dev/null 2>&1 || true
  # 协议 §3.5：轮前检查与测量之间的竞态——清空 logcat 之后再读一次 :visual_js。
  pid_worker_after="$(read_pid "${TARGET_PACKAGE}:visual_js")"
  if [ -n "$pid_worker_after" ]; then
    env_line="KD76_ENV round=$round mode=$MODE bootIdBefore=$boot_before bootIdAfter= hostBootCount=$host_boot pidTarget=$pid_target pidWorker=$pid_worker pidWorkerAfter=${pid_worker_after:-} batchId=${BATCH_ID:-<none>} runId=${RUN_ID:-<none>}"
    write_invalid_round "$round" "hot :visual_js worker appeared after logcat reset before round $round (pid $pid_worker_after)" "$env_line"
    log "round $round INVALID: worker appeared after logcat reset (pid $pid_worker_after)"
    return 1
  fi
  round_started_utc="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
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
  round_finished_utc="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  duration=$((end_ts - start_ts))
  load_after="$(adb_s shell cat /proc/loadavg 2>/dev/null | tr -d '\r' || true)"
  boot_after="$(read_boot_id)"

  # logd 是异步落 buffer；instrumentation 进程退出后给一次短暂 flush，避免把
  # "本轮有样本" 误判成 "没抓到 KD76_SAMPLE"（sleep 在轮次计时之外，不影响段值）。
  sleep 0.5
  local kd_lines
  kd_lines="$(adb_s logcat -d -v brief 2>/dev/null | tr -d '\r' | grep -E 'KD76_(ROUND|META|SAMPLE)|visual_js warm-up (finished|failed)' || true)"
  # logcat 可能把同一行重复（多 buffer/多 tag），保留原始顺序，汇总脚本按 round 取唯一行。
  local sample_line
  sample_line="$(printf '%s\n' "$kd_lines" | grep -E "KD76_SAMPLE round=${round} status=SAMPLE" | tail -n 1 || true)"
  # 协议 §3.5：worker 生命周期见证。成功轮必须恰好 1 条 finished、0 条 failed；
  # 0 条说明 warm-up 从未发生（测到的不是 ready 后的首个请求），>1 条说明中途重建。
  local warmup_finished_count warmup_failed_count
  warmup_finished_count="$(printf '%s\n' "$kd_lines" | grep -c 'visual_js warm-up finished' || true)"
  warmup_failed_count="$(printf '%s\n' "$kd_lines" | grep -c 'visual_js warm-up failed' || true)"
  local screen_awake la1
  screen_awake="$(adb_s shell dumpsys power 2>/dev/null | tr -d '\r' | grep -m1 'mWakefulness=' | sed 's/.*mWakefulness=//' || true)"
  la1="$(printf '%s' "$load_before" | awk '{print $1}')"

  local failures=0 failure_message=""
  if [ "$MODE" = "reboot" ] && [ "$boot_after" != "$boot_before" ]; then
    # 轮中重启/掉线重连会重置 uptimeMillis；该轮的段值不可比，标 INVALID。
    # 追加在原始 KD76_SAMPLE 之后，汇总按 round 取最后一行（INVALID 生效）。
    kd_lines="${kd_lines}
KD76_SAMPLE round=$round status=INVALID reason=host boot_id changed during round $round ($boot_before != $boot_after)"
    failures=1
    failure_message="host boot_id changed during round $round ($boot_before != $boot_after)"
  elif [ "$rc" -ne 0 ]; then
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
  elif [ "$warmup_finished_count" -ne 1 ]; then
    failures=1
    failure_message="expected exactly one 'visual_js warm-up finished' witness, got $warmup_finished_count (worker never ready or restarted)"
  elif [ "$warmup_failed_count" -ne 0 ]; then
    failures=1
    failure_message="$warmup_failed_count 'visual_js warm-up failed' line(s); the worker never reached ready"
  fi

  {
    printf '%s\n' '<?xml version="1.0" encoding="UTF-8"?>'
    printf '<testsuite name="%s" tests="1" failures="%s" errors="0" skipped="0" time="%s">\n' \
      "$(xml_attr "$TEST_CLASS")" "$failures" "$duration"
    printf '  <testcase classname="%s" name="%s" time="%s">\n' \
      "$(xml_attr "$TEST_CLASS")" "$(xml_attr "$TEST_METHOD")" "$duration"
    printf '    <system-out><![CDATA[\n'
    xml_cdata "KD76_ENV round=$round mode=$MODE bootIdBefore=$boot_before bootIdAfter=$boot_after hostBootCount=$host_boot pidTarget=$pid_target pidWorker=$pid_worker pidWorkerAfter=${pid_worker_after:-} appsFrozen=$(printf '%s' "$apps_frozen" | tr -s ' ' '_') loadBefore=$(printf '%s' "$load_before" | tr -s ' ' '_') loadAfter=$(printf '%s' "$load_after" | tr -s ' ' '_') batchId=${BATCH_ID:-<none>} runId=${RUN_ID:-<none>} roundStartedUtc=$round_started_utc roundFinishedUtc=$round_finished_utc screenAwake=$screen_awake warmupFinishedCount=$warmup_finished_count warmupFailedCount=$warmup_failed_count la1=$la1 nproc=${DEVICE_NPROC:-<unknown>}"
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
for round in $(seq "$FIRST_ROUND" "$LAST_ROUND"); do
  log "round $round (${FIRST_ROUND}..${LAST_ROUND}): preparing ($MODE)"
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
BOOT_IDS_CSV="$(tr '\n' ',' <"$BOOT_IDS_FILE" | sed 's/,$//')"
{
  printf 'finishedUtc=%s\n' "$FINISHED_UTC"
  printf 'failedRounds=%s\n' "$FAILED"
  printf 'roundsRequested=%s\n' "$ROUNDS"
  printf 'bootIds=%s\n' "$BOOT_IDS_CSV"
  # 逐文件哈希链（协议 §3.6）：汇总端/composer 重算；只防"无意改坏/局部改"，
  # 防不了全量重造（协议 §7.2）。
  for file in "$OUT_DIR"/round-*.xml; do
    [ -f "$file" ] || continue
    printf 'roundFileSha256.%s=%s\n' "$(basename "$file")" "$(host_sha256 "$file")"
  done
  for file in "$OUT_DIR"/round-*.am.txt; do
    [ -f "$file" ] || continue
    printf 'amFileSha256.%s=%s\n' "$(basename "$file")" "$(host_sha256 "$file")"
  done
} >>"$OUT_DIR/manifest.txt"

# manifest 自身哈希：抄进批次记录/外部日志才算外部锚（同目录内自洽不算）。
host_sha256 "$OUT_DIR/manifest.txt" >"$OUT_DIR/manifest.sha256"

log "done: $((ROUNDS - FAILED))/$ROUNDS rounds passed; evidence in $OUT_DIR"
log "next: python -I tools/ci/summarize_kd76.py '$OUT_DIR'"
if [ "$FAILED" -ne 0 ]; then
  exit 1
fi
exit 0
