#!/usr/bin/env bash
# 批次 51 数据安全设备轮（串行；emulator-5590）。
# 逐类运行 core:data 备份/裁剪/行数/换机演练用例，每类独立日志 + 原始退出码。
set -u
ADB=${ADB:-/c/Android/Sdk/platform-tools/adb.exe}
SERIAL=${1:-emulator-5590}
OUT=${2:?outdir}
RUN=com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p "$OUT"

run() {
  local name=$1; shift
  local log="$OUT/$name.log"
  {
    echo "# $name"
    echo "cmd=$ADB -s $SERIAL shell am instrument -w -r $* $RUN"
    echo "started_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } > "$log"
  "$ADB" -s "$SERIAL" shell am instrument -w -r "$@" "$RUN" >> "$log" 2>&1
  echo "EXIT=$?" >> "$log"
  echo "finished_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$log"
  echo "--- $name: $(grep -E '^OK \(|^FAILURES' "$log" | tail -1) $(grep '^EXIT=' "$log")"
}

run 08-instrument-backup-34 -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest
run 09-instrument-trimmer -e class com.tingyun.smartmistakebook.core.data.backup.BackupSnapshotTrimmerInstrumentedTest
run 10-instrument-rowcounts -e class com.tingyun.smartmistakebook.core.data.backup.BackupRowCountsCoverageInstrumentedTest
run 11-instrument-drill-masks -e smb_allow_data_reset true -e class "com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest#fullMaskFifteenMigrationDrillRoundTripsEveryTableAndSettings,com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest#subsetMaskNineMigrationDrillDropsSessionsAndRebuildsBothSubjects,com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest#subsetMaskThirteenMigrationDrillKeepsSessionsAndSettings"
run 12-instrument-drill-corrupted -e smb_allow_data_reset true -e class "com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest#corruptedSelectionStopsBeforeSwapLeavingNoHalfDatabase"
# 阶段 A：按设计杀死自身进程（预期进程死亡、本调用非零退出）
run 13-instrument-drill-killstage -e smb_allow_data_reset true -e class "com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest#killProcessHandoffStagesArchiveThenDies" -e smb_d1_kill_stage true
# 阶段 B：新进程读交接件恢复（阶段 A 的交接件必须已落盘）
run 14-instrument-drill-restore-handoff -e smb_allow_data_reset true -e class "com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest#restoreFromHandoffAfterProcessDeath"
