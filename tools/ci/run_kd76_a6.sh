#!/usr/bin/env bash
# -*- coding: utf-8 -*-
#
# KD-76 A6 两批 20 轮的一体化驱动（批次 43 / WS2 item 11）。
#
# 它消灭的具体失败（协议 `docs/research/2026-11-05-kd76-a6-protocol.md` §4.2–§4.4、§7）：
#   - 手工跑两批时忘等间隔、忘复核设备未变、忘用同一构建 → 本脚本在批 B 开跑前
#     重取身份并逐项比对（app/test sha、设备代际、安装代际），不等间隔就等待；
#   - 两批用不同 APK（批次 42 的真实形状）→ 预注册 plan 在两批前写死，两批都从
#     **已安装** APK 拉包算 sha；批 B 默认不重装；
#   - 判词被脚本改写 → 本脚本只跑批次与调用工具；每批判词由 `summarize_kd76.py`
#     独立给出，A6 判词由 `kd76_a6_verify.py` 合取给出，本脚本不合并、不判词。
#
# 边界：
#   - 不做 git 写；不安装可选的 `--apk/--test-apk`（只在开跑前一次）。
#   - 不改门/样本量/估计量（plan 与摘要工具里的冻结值）。
#   - 失败不重跑洗绿：批级失败即按原样落盘，`maxReplacementBatches` 由 plan 约束；
#     本脚本不会自动替换批次。
#
# 用法（仓库根）：
#   tools/ci/run_kd76_a6.sh --serial emulator-5584 --out-root build/kd76-runs/a6
#   tools/ci/run_kd76_a6.sh --serial emulator-5584 --out-root build/kd76-runs/a6 \
#       --app-package com.tingyun.smartmistakebook.localfirst \
#       --min-separation-minutes 30
#
# 退出码：0 = A6 CONFIRMED；1 = A6 REFUTED；2 = A6 NOT EVALUABLE 或前置失败。

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"
OUT_ROOT=""
APP_PACKAGE="com.tingyun.smartmistakebook.localfirst"
TEST_PACKAGE=""
APK=""
TEST_APK=""
MIN_SEPARATION=30
PLAN_ID=""
PYTHON_BIN="${PYTHON:-python}"

usage() {
  awk 'NR >= 3 { if ($0 !~ /^#/) exit; sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
}

die() {
  printf 'run_kd76_a6: %s\n' "$*" >&2
  exit 2
}

log() {
  printf '[%s] %s\n' "$(date -u +%H:%M:%SZ)" "$*"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="${2:-}"; shift 2 ;;
    --out-root) OUT_ROOT="${2:-}"; shift 2 ;;
    --app-package) APP_PACKAGE="${2:-}"; shift 2 ;;
    --test-package) TEST_PACKAGE="${2:-}"; shift 2 ;;
    --apk) APK="${2:-}"; shift 2 ;;
    --test-apk) TEST_APK="${2:-}"; shift 2 ;;
    --min-separation-minutes) MIN_SEPARATION="${2:-}"; shift 2 ;;
    --plan-id) PLAN_ID="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1 (use --help)" ;;
  esac
done

[ -n "$SERIAL" ] || die "--serial (or ANDROID_SERIAL) is required"
[ -n "$OUT_ROOT" ] || die "--out-root is required"
case "$MIN_SEPARATION" in
  ''|*[!0-9]*) die "--min-separation-minutes must be an integer" ;;
esac
[ "$MIN_SEPARATION" -ge 10 ] || die "--min-separation-minutes must be >= 10 (hard floor)"
[ -n "$TEST_PACKAGE" ] || TEST_PACKAGE="${APP_PACKAGE}.test"

mkdir -p "$OUT_ROOT" || die "cannot create $OUT_ROOT"
OUT_ROOT="$(cd "$OUT_ROOT" && pwd)"
IDENTITY_JSON="$OUT_ROOT/identity.json"
PLAN="$OUT_ROOT/a6-plan.txt"
REPORT="$OUT_ROOT/a6-report.txt"
NONCE_A="$(date -u +%Y%m%dT%H%M%SZ)-A-$$"
NONCE_B="$(date -u +%Y%m%dT%H%M%SZ)-B-$$"

# 1) 可选：安装一次（之后两批都不再安装）。
if [ -n "$APK" ] || [ -n "$TEST_APK" ]; then
  ADB_BIN="${ADB:-}"
  if [ -z "$ADB_BIN" ]; then
    if command -v adb >/dev/null 2>&1; then ADB_BIN="$(command -v adb)";
    elif [ -n "${ANDROID_HOME:-}" ] && [ -x "${ANDROID_HOME//\\//}/platform-tools/adb.exe" ]; then ADB_BIN="${ANDROID_HOME//\\//}/platform-tools/adb.exe";
    else die "adb not found (set ADB or ANDROID_HOME)"; fi
  fi
  if [ -n "$APK" ]; then
    [ -f "$APK" ] || die "APK not found: $APK"
    log "installing app APK once: $APK"
    "$ADB_BIN" -s "$SERIAL" install -r -t "$APK" >"$OUT_ROOT/install-app.log" 2>&1 \
      || die "app install failed (see install-app.log)"
  fi
  if [ -n "$TEST_APK" ]; then
    [ -f "$TEST_APK" ] || die "test APK not found: $TEST_APK"
    log "installing test APK once: $TEST_APK"
    "$ADB_BIN" -s "$SERIAL" install -r -t "$TEST_APK" >"$OUT_ROOT/install-test.log" 2>&1 \
      || die "test APK install failed (see install-test.log)"
  fi
fi

# 2) 身份（已安装 sha / 设备代际 / 安装代际）——两批共用同一份。
log "collecting installed/device identity on $SERIAL"
"$PYTHON_BIN" -I "$REPO_ROOT/tools/ci/kd76_identity.py" \
  --serial "$SERIAL" --app-package "$APP_PACKAGE" --test-package "$TEST_PACKAGE" \
  --work-dir "$OUT_ROOT/identity" --json >"$IDENTITY_JSON" \
  || die "identity collection failed"

# 3) 预注册 plan（已存在则只校验，不覆盖）。
if [ -f "$PLAN" ]; then
  log "plan already exists; validating instead of overwriting: $PLAN"
  "$PYTHON_BIN" -I "$REPO_ROOT/tools/ci/a6_plan.py" validate --plan "$PLAN" \
    || die "existing plan is invalid; refusing to run"
else
  log "writing pre-registered plan (minSeparationMinutes=$MIN_SEPARATION)"
  "$PYTHON_BIN" -I "$REPO_ROOT/tools/ci/a6_plan.py" write \
    --identity-json "$IDENTITY_JSON" --out "$PLAN" \
    --plan-id "${PLAN_ID:-$(date -u +%Y-%m-%dT%H-%MZ)}" \
    --min-separation-minutes "$MIN_SEPARATION" \
    || die "plan write failed"
fi
PLAN_SHA="$("$PYTHON_BIN" -I - "$PLAN" <<'PY'
import hashlib, sys
print(hashlib.sha256(open(sys.argv[1], "rb").read()).hexdigest())
PY
)"
log "plan sha256=$PLAN_SHA"

# 4) 批 A。
log "batch A start (runId=$NONCE_A)"
"$REPO_ROOT/tools/ci/run_kd76_rounds.sh" \
  --serial "$SERIAL" --mode reboot --rounds 20 --first-round 1 \
  --target-package "$APP_PACKAGE" \
  --batch-id A --run-id "$NONCE_A" --a6-plan "$PLAN" --identity-json "$IDENTITY_JSON" \
  --out "$OUT_ROOT/batchA"
RC_A=$?
log "batch A exit=$RC_A (evidence kept as-is; no replacement run is started automatically)"

# 5) 间隔；同一构建/设备/安装未变的复核。
identity_field() {
  "$PYTHON_BIN" -I - "$1" "$2" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8")).get(sys.argv[2], ""))
PY
}
A_FINISHED="$(grep -m1 '^finishedUtc=' "$OUT_ROOT/batchA/manifest.txt" 2>/dev/null | cut -d= -f2-)"
[ -n "$A_FINISHED" ] || die "batch A manifest has no finishedUtc; cannot enforce separation"

WAIT_SECONDS="$("$PYTHON_BIN" -I - "$A_FINISHED" "$MIN_SEPARATION" <<'PY'
import sys
from datetime import datetime, timezone
finished = datetime.strptime(sys.argv[1], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
target = finished.timestamp() + int(sys.argv[2]) * 60
now = datetime.now(timezone.utc).timestamp()
print(max(0, int(target - now)))
PY
)"
if [ "$WAIT_SECONDS" -gt 0 ]; then
  log "waiting ${WAIT_SECONDS}s so batch B starts >= ${MIN_SEPARATION} min after batch A finished"
  sleep "$WAIT_SECONDS"
fi

log "re-checking installed/device identity before batch B"
"$PYTHON_BIN" -I "$REPO_ROOT/tools/ci/kd76_identity.py" \
  --serial "$SERIAL" --app-package "$APP_PACKAGE" --test-package "$TEST_PACKAGE" \
  --work-dir "$OUT_ROOT/identity-b" --json >"$OUT_ROOT/identity-b.json" \
  || die "identity re-check failed"
for field in installedAppSha256 installedTestSha256 deviceGenerationId installGenerationId; do
  before="$(identity_field "$IDENTITY_JSON" "$field")"
  after="$(identity_field "$OUT_ROOT/identity-b.json" "$field")"
  [ "$before" = "$after" ] \
    || die "identity changed between batches ($field: $before -> $after); refusing to run batch B as A6"
done

# 6) 批 B（不重装；用同一份 identity）。
log "batch B start (runId=$NONCE_B)"
"$REPO_ROOT/tools/ci/run_kd76_rounds.sh" \
  --serial "$SERIAL" --mode reboot --rounds 20 --first-round 1 \
  --target-package "$APP_PACKAGE" \
  --batch-id B --run-id "$NONCE_B" --a6-plan "$PLAN" --identity-json "$IDENTITY_JSON" \
  --out "$OUT_ROOT/batchB"
RC_B=$?
log "batch B exit=$RC_B"

# 7) 每批独立判词（分开保存），再做 A6 合取。
"$PYTHON_BIN" -I "$REPO_ROOT/tools/ci/summarize_kd76.py" "$OUT_ROOT/batchA" >"$OUT_ROOT/batchA/summary.txt" 2>&1
SUMM_A=$?
"$PYTHON_BIN" -I "$REPO_ROOT/tools/ci/summarize_kd76.py" "$OUT_ROOT/batchB" >"$OUT_ROOT/batchB/summary.txt" 2>&1
SUMM_B=$?
log "per-batch summarize exit: A=$SUMM_A B=$SUMM_B (reports in batchA/ and batchB/summary.txt)"

"$PYTHON_BIN" -I "$REPO_ROOT/tools/ci/kd76_a6_verify.py" \
  --plan "$PLAN" --batch-a "$OUT_ROOT/batchA" --batch-b "$OUT_ROOT/batchB" \
  >"$REPORT" 2>&1
RC_A6=$?
cat "$REPORT"
log "A6 verify exit=$RC_A6 (report: $REPORT)"
log "NOTE: the verdict above is the tool's; it does not cover C4, the 29.5 s chain, the product-process first request, arm64 or real devices"
exit "$RC_A6"
