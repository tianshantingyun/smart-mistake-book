#!/usr/bin/env bash
# 渲染文本采集编排（§2.1–2.5 的原始证据；判读交给 render_judge.py）。
# 用法: render_capture.sh <apk> <out-dir> <pkg> <test-pkg> [serial] [rounds]
# 输出: <out-dir>/render-capture.txt（logcat -d -s RenderCapture 全量）
# 退出码: 0=采集完成；2=前置失败；3=设备操作失败。本脚本不出采纳判词。
set -euo pipefail
APK="${1:?usage: render_capture.sh <apk> <out-dir> <pkg> <test-pkg> [serial] [rounds]}"
OUT="${2:?}"; PKG="${3:?}"; TEST_PKG="${4:?}"; SERIAL="${5:-}"; ROUNDS="${6:-3}"
ADB="${ADB:-/c/Android/Sdk/platform-tools/adb.exe}"
S() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }
mkdir -p "$OUT"; CAP="$OUT/render-capture.txt"; : > "$CAP"
[ -f "$APK" ] || { echo "ABORT: apk missing" >&2; exit 2; }
for i in $(seq 1 "$ROUNDS"); do
  S uninstall "$PKG" >/dev/null 2>&1 || true
  S install -t "$APK" >/dev/null || { echo "ABORT: install failed" >&2; exit 3; }
  S logcat -c
  # §6 登记（执行轮 2026-11-07）：同 first_query_20x.sh——am instrument 组件位必须是
  # 注册 runner（androidx.test.runner.AndroidJUnitRunner），测试类名走 -e class；
  # 契约不变（仪器失败仍 ABORT exit 3）。
  S shell am instrument -w -e round "$i" \
    -e class com.tingyun.smartmistakebook.core.data.knowledge.KnowledgeReadRenderCaptureInstrumentedTest \
    "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner" \
    >/dev/null || { echo "ABORT: capture instrument failed (round $i)" >&2; exit 3; }
  echo "===== round=$i =====" >> "$CAP"
  S logcat -d -s RenderCapture | sed 's/\r$//' >> "$CAP"
done
grep -q "RENDER_BEGIN" "$CAP" || { echo "ABORT: no RENDER blocks captured" >&2; exit 3; }
exit 0
