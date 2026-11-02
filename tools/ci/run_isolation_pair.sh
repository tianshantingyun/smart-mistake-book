#!/usr/bin/env bash
# -*- coding: utf-8 -*-
#
# isolation OFF/ON 配对轮 runner（批次 41 / WS4 item 26/29）。
#
# 它消灭的具体失败：批次 40 只有单臂 15/15（step04-isolation.md §4），
# OFF/ON 配对、跑前/跑后低负载门、T4 控制轮都没有机器可核产物；批次 39 的三窗
# 红集（6/7/5 例）无法比较，因为没人知道哪一窗是干净的。
#
# 协议（R5 §5.1，逐项照做）：
#   1. calibrate 一次：settle → 30×10 s 采样 → 唤醒探针 → 地板（每换 AVD/宿主/APK 重做）；
#   2. 每轮每臂：**跑前门**（snapshot + 30×10 s 窗）→ `am instrument -r -w` →
#      **跑后门**；两次都 PASS 才把该 run 的 window 记为 PASS；
#      `--post-settle N`（默认 0）在 suite 退出后先等 N 秒再开 post 窗——本轮实测
#      post 窗首样本会吃进套件自身收尾（6 个 post 中 5 个 INVALID，见 E-41-WS4 §5.3），
#      下一轮建议 `--post-settle 60` 并预注册；默认 0 保持原协议。
#   3. 臂顺序交替 OFF ON / ON OFF（宿主负载随时间漂移，不写成 AAA BBB）；
#   4. 门不过也照跑：套件收到 `-e smb.lowload.gate INVALID` 后整套件 skip（-4），
#      报告工具把 -4 单列，**不算通过**；该 run 的 window 记 INVALID → UNCONVERGED；
#   5. 收尾调用 `tools/ci/isolation_pair_report.py` 出配对判定。只有
#      `--off-arm-production-t4 removed`（且 `t4_control_marker.py verify` 通过）时
#      报告才可能输出 T4 因果；默认 `test-side-only`。
#
# 边界（必须如实）：
#   - 本脚本不改源码、不构建、不安装 APK（APK 由门禁阶段单独构建；脚本只校验 sha）；
#   - `--off-arm-production-t4 test-side-only` 时 OFF 臂只是"测试侧钩子未注册"，
#     生产代码的 6 处 T4 打点仍在——这不是去 T4 控制轮，报告会拒绝 T4 因果；
#   - 一次完整跑（k=3、两臂、前后门）≈ 12 个 5 min 窗 + 6 次套件，约 75–90 min。
#
# 用法（仓库根）：
#   export PATH="$PATH:/c/Android/Sdk/platform-tools"
#   tools/ci/run_isolation_pair.sh --serial emulator-5584
#   tools/ci/run_isolation_pair.sh --serial emulator-5584 --rounds 4 \
#       --off-arm-production-t4 removed --t4-marker build/control/t4.marker.json
#   tools/ci/run_isolation_pair.sh --serial emulator-5584 --skip-gate   # 只出 UNGATED 证据
#   tools/ci/run_isolation_pair.sh --serial emulator-5584 --post-settle 60
#
# 退出码：0 = 报告判定 PAIRED_RED_SET_IDENTICAL / T4_CAUSAL；
#         1 = UNCONVERGED / DIVERGES_T4_UNVERIFIED；2 = 用法/设备/门脚本前置不满足。

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"
ADB="${ADB:-adb}"
OUT_DIR="build/ws4-isolation"
ROUNDS=3
ARMS=("OFF" "ON")
TEST_CLASS="com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest"
TARGET_PACKAGE="com.tingyun.smartmistakebook.offline"
COMPONENT="com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner"
FLOOR=""
SKIP_GATE=0
GATE_SAMPLES=30
GATE_INTERVAL=10
SETTLE=120
POST_SETTLE=0
OFF_ARM_T4="test-side-only"
T4_MARKER=""
APK=""
ROUND_TIMEOUT=600

usage() {
  # 头部注释块结束于首个非 `#` 行；写死行号会在注释增删后把 shell 代码当"用法"打印出来。
  awk 'NR >= 3 { if ($0 !~ /^#/) exit; sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
}

die() {
  printf 'run_isolation_pair: %s\n' "$*" >&2
  exit 2
}

log() {
  printf '[%s] %s\n' "$(date -u +%H:%M:%SZ)" "$*"
}

# 交给 native Python 的路径必须是 Windows 形状：Git Bash 的 `/d/...` 在 Windows
# Python 里会被解析成当前盘符下的 `\d\...`（本轮实测 report 打不开 `r1-OFF.logcat`）。
win_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s' "$1"
  fi
}

cd "$REPO_ROOT" || die "cannot cd to repo root: $REPO_ROOT"

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="${2:-}"; shift 2 ;;
    --adb) ADB="${2:-}"; shift 2 ;;
    --out-dir) OUT_DIR="${2:-}"; shift 2 ;;
    --rounds) ROUNDS="${2:-}"; shift 2 ;;
    --class) TEST_CLASS="${2:-}"; shift 2 ;;
    --floor) FLOOR="${2:-}"; shift 2 ;;
    --gate-samples) GATE_SAMPLES="${2:-}"; shift 2 ;;
    --gate-interval) GATE_INTERVAL="${2:-}"; shift 2 ;;
    --settle) SETTLE="${2:-}"; shift 2 ;;
    --post-settle) POST_SETTLE="${2:-}"; shift 2 ;;
    --off-arm-production-t4) OFF_ARM_T4="${2:-}"; shift 2 ;;
    --t4-marker) T4_MARKER="${2:-}"; shift 2 ;;
    --apk) APK="${2:-}"; shift 2 ;;
    --skip-gate) SKIP_GATE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[ -n "$SERIAL" ] || die "--serial (or ANDROID_SERIAL) is required"
[ "$ROUNDS" -ge 3 ] 2>/dev/null || die "--rounds must be >= 3 (R5 §5.3)"
case "$OFF_ARM_T4" in
  test-side-only|removed) ;;
  *) die "--off-arm-production-t4 must be test-side-only or removed" ;;
esac

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
MANIFEST="$OUT_DIR/manifest.txt"
: > "$MANIFEST"

log "serial=$SERIAL out=$OUT_DIR rounds=$ROUNDS arms=${ARMS[*]}"

"$ADB" -s "$SERIAL" get-state >/dev/null 2>&1 || die "device not ready: $SERIAL"
"$ADB" -s "$SERIAL" shell pm path "$TARGET_PACKAGE" >/dev/null 2>&1 \
  || die "target package not installed: $TARGET_PACKAGE"

# APK sha 绑定（R5 L-6）：门结果必须绑定"哪一份 APK"。
if [ -n "$APK" ]; then
  [ -f "$APK" ] || die "apk not found: $APK"
  sha256sum "$APK" | awk '{print "apk_sha256=" $1}' >> "$MANIFEST"
  printf 'apk_path=%s\n' "$APK" >> "$MANIFEST"
fi
printf 'serial=%s\nsource_head=%s\n' "$SERIAL" "$(git -C "$REPO_ROOT" rev-parse HEAD 2>/dev/null || echo unknown)" >> "$MANIFEST"

gate_once() {
  # $1 = label; echoes PASS/INVALID; writes $OUT_DIR/gate-$label.json
  local label="$1"
  if [ "$SKIP_GATE" -eq 1 ]; then
    python -I "$REPO_ROOT/tools/ci/lowload_gate.py" snapshot --adb "$ADB" --serial "$SERIAL" \
      --out "$(win_path "$OUT_DIR/snapshot-$label.json")" >/dev/null 2>&1 || true
    printf 'INVALID' > "$OUT_DIR/gate-$label.verdict"
    printf 'skip_gate_requested' > "$OUT_DIR/gate-$label.reason"
    return 0
  fi
  python -I "$REPO_ROOT/tools/ci/lowload_gate.py" gate \
    --adb "$ADB" --serial "$SERIAL" --floor "$(win_path "$FLOOR")" \
    --samples "$GATE_SAMPLES" --interval "$GATE_INTERVAL" \
    --out "$(win_path "$OUT_DIR/gate-$label.json")" \
    --emit-args "$(win_path "$OUT_DIR/gate-$label.args")" >/dev/null 2>&1
  local code=$?
  local verdict reason
  verdict=$(python -I - "$(win_path "$OUT_DIR/gate-$label.json")" <<'PY'
import json, sys
try:
    data = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception:
    print("INVALID")
    raise SystemExit
print(data.get("verdict", "INVALID"))
PY
)
  reason=$(python -I - "$(win_path "$OUT_DIR/gate-$label.json")" <<'PY'
import json, sys
try:
    data = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception:
    print("unreadable")
    raise SystemExit
print("_".join(data.get("reasons", []) or ["none"]))
PY
)
  printf '%s' "$verdict" > "$OUT_DIR/gate-$label.verdict"
  printf '%s' "$reason" > "$OUT_DIR/gate-$label.reason"
  printf 'gate %s verdict=%s reasons=%s exit=%s\n' "$label" "$verdict" "$reason" "$code" >&2
  printf 'gate,%s,%s,%s\n' "$label" "$verdict" "$reason" >> "$MANIFEST"
}

if [ "$SKIP_GATE" -eq 0 ]; then
  if [ -z "$FLOOR" ]; then
    FLOOR="$OUT_DIR/floor.json"
    log "calibrating floor -> $FLOOR (settle ${SETTLE}s + ${GATE_SAMPLES}x${GATE_INTERVAL}s)"
    python -I "$REPO_ROOT/tools/ci/lowload_gate.py" calibrate \
      --adb "$ADB" --serial "$SERIAL" --samples "$GATE_SAMPLES" \
      --interval "$GATE_INTERVAL" --settle "$SETTLE" --out "$(win_path "$FLOOR")" >/dev/null \
      || die "floor calibration failed"
  fi
  [ -f "$FLOOR" ] || die "floor not found: $FLOOR"
fi

if [ "$OFF_ARM_T4" = "removed" ]; then
  [ -n "$T4_MARKER" ] || die "--off-arm-production-t4 removed requires --t4-marker"
  python -I "$REPO_ROOT/tools/ci/t4_control_marker.py" verify --marker "$(win_path "$T4_MARKER")" >/dev/null \
    || die "T4 marker verification failed; OFF arm is not a T4-removed control"
  printf 'off_arm_production_t4=removed\n' >> "$MANIFEST"
else
  printf 'off_arm_production_t4=test-side-only\n' >> "$MANIFEST"
fi

RUN_ARGS=()
for round in $(seq 1 "$ROUNDS"); do
  if [ $((round % 2)) -eq 1 ]; then
    order=("OFF" "ON")
  else
    order=("ON" "OFF")
  fi
  for arm in "${order[@]}"; do
    label="r${round}-${arm}"
    log "round=$round arm=$arm pre-gate"
    gate_once "pre-$label"
    pre=$(cat "$OUT_DIR/gate-pre-$label.verdict")
    gate_arg="$pre"
    reason_arg=$(cat "$OUT_DIR/gate-pre-$label.reason")

    "$ADB" -s "$SERIAL" logcat -c >/dev/null 2>&1 || true
    log "round=$round arm=$arm instrument (gate=$gate_arg)"
    timeout "$ROUND_TIMEOUT" "$ADB" -s "$SERIAL" shell am instrument -r -w --no-window-animation \
      -e class "$TEST_CLASS" \
      -e smb.lowload.gate "$gate_arg" \
      -e smb.lowload.reason "$reason_arg" \
      -e smb.t4.arm "$arm" \
      "$COMPONENT" > "$OUT_DIR/$label.instrument.log" 2>&1
    instrument_code=$?
    "$ADB" -s "$SERIAL" logcat -d -v time > "$OUT_DIR/$label.logcat" 2>/dev/null || true

    log "round=$round arm=$arm post-gate (post-settle=${POST_SETTLE}s)"
    if [ "$POST_SETTLE" -gt 0 ]; then
      # 套件自身收尾（进程死亡/GC）会落进 post 窗的第一个 10 s 样本；settle 让残留
      # 先衰减，post 窗只回答"有没有外部闯入者"（R5 §5.1）。
      sleep "$POST_SETTLE"
    fi
    gate_once "post-$label"
    post=$(cat "$OUT_DIR/gate-post-$label.verdict")

    window="INVALID"
    if [ "$pre" = "PASS" ] && [ "$post" = "PASS" ]; then
      window="PASS"
    fi
    printf 'run,%s,%s,%s,%s,%s,%s\n' "$round" "$arm" "$window" "$instrument_code" \
      "$OUT_DIR/$label.instrument.log" "$OUT_DIR/$label.logcat" >> "$MANIFEST"
    RUN_ARGS+=("--run" "arm=$arm,round=$round,window=$window,log=$(win_path "$OUT_DIR/$label.instrument.log"),logcat=$(win_path "$OUT_DIR/$label.logcat")")
    log "round=$round arm=$arm window=$window instrument_exit=$instrument_code"
  done
done

report_json="$OUT_DIR/pair.json"
report_md="$OUT_DIR/pair.md"
python -I "$REPO_ROOT/tools/ci/isolation_pair_report.py" \
  "${RUN_ARGS[@]}" \
  --off-arm-production-t4 "$OFF_ARM_T4" \
  --out "$(win_path "$report_json")" --md "$(win_path "$report_md")" >/dev/null
report_code=$?

verdict=$(python -I - "$(win_path "$report_json")" <<'PY'
import json, sys
try:
    data = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception:
    print("UNREADABLE")
    raise SystemExit
print(data.get("verdict", "UNKNOWN"))
PY
)
log "pair verdict=$verdict report=$report_json"
printf 'verdict=%s\n' "$verdict" >> "$MANIFEST"
printf 'report=%s\n' "$report_json" >> "$MANIFEST"

case "$verdict" in
  PAIRED_RED_SET_IDENTICAL|T4_CAUSAL) exit 0 ;;
  *) exit 1 ;;
esac
