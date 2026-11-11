# 先红后绿记录（批次 49 修复环）

## 1. 校准卡文案（feature/review，JVM）

红：临时把 `KnowledgeConflictCard.kt` 的 consequence 恢复为旧文案
「校准题只多问一道，不会把这次复习算作完成。」

```bash
./gradlew :feature:review:testDebugUnitTest \
  --tests 'com.tingyun.smartmistakebook.feature.review.KnowledgeConflictCardTest' --console=plain
# KnowledgeConflictCardTest > theConsequenceCopyDoesNotPromiseAnExtraQuestion FAILED
# BUILD FAILED in 8s
```

绿：恢复为真实后果文案后：

```bash
./gradlew :feature:review:testDebugUnitTest \
  --tests 'com.tingyun.smartmistakebook.feature.review.KnowledgeConflictCardTest' --console=plain
# BUILD SUCCESSFUL in 5s; tests=6 failures=0
./gradlew :feature:review:testDebugUnitTest --console=plain
# BUILD SUCCESSFUL in 2s; feature:review tests=106 failures=0
```

## 2. F-057 / soak 退出码 / 设备绑定自述（Python 不变量）

新增 `tools/tests/test_batch49_repair_invariants.py`。对修复前的最小夹具
（`build/repair/red-before/`，回放旧 F-057 行、旧 `f-soak.sh`、旧 06/07 note、
旧 `cmd_exit` checks）实跑：

```bash
BATCH49_REPAIR_ROOT=build/repair/red-before \
  python -I -m pytest tools/tests/test_batch49_repair_invariants.py -q
# 4 failed in 0.16s
#  - F057VerdictTest.test_over_limit_row_keeps_the_positive_criterion_and_fails
#  - SoakExitEvidenceTest.test_rows_do_not_claim_cmd_exit_from_the_caveated_log
#  - SoakExitEvidenceTest.test_script_records_a_real_status_instead_of_the_literal
#  - DeviceBindingProvenanceTest.test_sidecars_do_not_claim_serial_apk_from_the_instrument_log
```

对修复后的树：

```bash
python -I -m pytest tools/tests/test_batch49_repair_invariants.py -q
# 4 passed
```

完整红样输出见 `red-before-replay.txt`。

## 3. 校准决策消费（设备）

修复前：屏幕只显示固定句「本次按校准题处理：作答仍计入这个知识点的掌握度。」，
选择器返回值被丢弃。修复后设备用例断言：

- ASK_CALIBRATION →「本次按校准题处理：选择器已选中校准题面（<itemId>）…」；
- NO_SAFE_CANDIDATE →「选择器未给出校准题（…）」；
- 全类 9/9 绿（`device/12-batch49-repair-artifacts.device.json`）。
