#!/usr/bin/env bash
# 批次 51：3E 每日累计配额设备复验 + 新 UI（5b/5c/6B-3/KD-67）设备用例。
# 串行（同一设备），每类独立日志 + 原始退出码。
set -u
ADB=${ADB:-/c/Android/Sdk/platform-tools/adb.exe}
SERIAL=${1:-emulator-5590}
OUT=${2:?outdir}
mkdir -p "$OUT"

run() {
  local name=$1; local runner=$2; shift 2
  local log="$OUT/$name.log"
  {
    echo "# $name"
    echo "runner=$runner"
    echo "args=$*"
    echo "started_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } > "$log"
  "$ADB" -s "$SERIAL" shell am instrument -w -r "$@" "$runner" >> "$log" 2>&1
  echo "EXIT=$?" >> "$log"
  echo "finished_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$log"
  echo "--- $name: $(grep -E '^OK \(|^FAILURES' "$log" | tail -1) $(grep '^EXIT=' "$log")"
}

DATA=com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
LIB=com.tingyun.smartmistakebook.feature.library.test/androidx.test.runner.AndroidJUnitRunner
APP=com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner

# 3E：全库扫描 / 时间预算 / 每日累计（KD-46 修复后口径）/ 无考试日指纹
run 20-instrument-3e-daily-cap $DATA -e class com.tingyun.smartmistakebook.core.data.study.StudyReviewPlanner3EScanInstrumentedTest
# KD-67：第二道校准题真 Room e2e
run 21-instrument-kd67 $DATA -e class com.tingyun.smartmistakebook.core.data.study.KnowledgeCalibrationQuestionRoomE2eInstrumentedTest
# 6B-3 改卷 UI + 5c 错因面板
run 22-instrument-library-6b3-5c $LIB -e class "com.tingyun.smartmistakebook.feature.library.NativePaperEditModelInstrumentedTest,com.tingyun.smartmistakebook.feature.library.NativePaperEditRouteInstrumentedTest,com.tingyun.smartmistakebook.feature.library.MistakeErrorCausePanelInstrumentedTest"
# 5c 真库写入接缝 + JS 回放 painter
run 23-instrument-5c-realrepo-js-painter $DATA -e class "com.tingyun.smartmistakebook.core.data.study.ErrorCauseReviewServiceInstrumentedTest,com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest"
# 5b 容量 / 新题预览 / AIGC 标签 / 能力屏
run 24-instrument-app-5b-aigc-capability $APP -e class "com.tingyun.smartmistakebook.ReviewCapacitySectionInstrumentedTest,com.tingyun.smartmistakebook.ReviewIntakeBacklogPreviewInstrumentedTest,com.tingyun.smartmistakebook.AiContentLabelsInstrumentedTest,com.tingyun.smartmistakebook.CapabilityScreenInstrumentedTest"
