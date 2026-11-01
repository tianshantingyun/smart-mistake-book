# E-09 持久化语义收口三套件：提交 9 / 仓库 4 / 结构门 4（全 PASS）

| 字段 | 值 |
|---|---|
| evidence-id | E-09 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 三（计划任务 3–5 的设备面；判据 §4.5） |
| gate-or-kd | checkpoint demote/evict 接线、跨 program CAS、题面 link 拒 STAGING、snapshotId 复派生 |
| verdict | **PASS**：`OK (9 tests)` / `OK (4 tests)` / `OK (4 tests)`；三套件 17 例全绿 |
| executed-at | 2026-10-29T06:41:49–06:42:07+0800 |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `database-debug-androidTest.apk` / `data-debug-androidTest.apk` |
| apk-sha256 | `4e54f0de…c7da2d`（database）/ `34151d4d…e7b952`（data），设备侧一致 |
| apk-version | （测试包 versionName 空） |
| exit-code | 三条命令均宿主 shell `EXIT=0`（非判据） |
| raw-log | `step09-visual-commit.log`（135 行 / 6982 B）· `step10-visual-repo.log`（65 行 / 3300 B）· `step11-structure-gates.log`（65 行 / 3245 B）+ 各自 `-logcat.txt` |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

判据 §4.5：本批持久化收口（计划任务 3–5）必须有三套设备面：
`TutorVisualCommitInstrumentedTest`（9，含新增 `snapshotIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite`）、
`TutorVisualCommitRepositoryInstrumentedTest`（4，含 `stageThenCommitStillDemotesThePreviousLatestCheckpoint`）、
`TutorVisualStructureGatesInstrumentedTest`（4，消息 link 跨 program/STAGING、题面 pin STAGING/跨 program、
demote/evict 在提交事务内真的执行）。类名与方法名均在 E-00 的 dex 门内。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562

$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualCommitInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step09-visual-commit.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step09-visual-commit-logcat.txt

$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepositoryInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step10-visual-repo.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step10-visual-repo-logcat.txt

$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step11-structure-gates.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step11-structure-gates-logcat.txt
```

## 3. 结果摘要

| 套件 | tests run | failures/errors/skipped | `STATUS_CODE: 0` | Time |
|---|---|---|---|---|
| `TutorVisualCommitInstrumentedTest` | 9 | 0/0/0 | 9 | 2.009 s |
| `TutorVisualCommitRepositoryInstrumentedTest` | 4 | 0/0/0 | 4 | 1.123 s |
| `TutorVisualStructureGatesInstrumentedTest` | 4 | 0/0/0 | 4 | 1.341 s |

三份 logcat 9 类签名均 0（E-10）。

## 4. 关键输出（有界）

```text
Time: 2.009
OK (9 tests)

Time: 1.123
OK (4 tests)

Time: 1.341
OK (4 tests)
```

本批新增/关键用例逐条（`codes=['1','0']` = 启动 + 通过）：

```text
snapshotIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite                 codes=['1','0']
stageThenCommitStillDemotesThePreviousLatestCheckpoint                        codes=['1','0']
diskFullRejectsBeforeAnyVisualRowIsWritten                                    codes=['1','0']
messageLinkRejectsForeignKeyProgramAndStagingSnapshots                        codes=['1','0']
revisionPinRejectsStagingAndForeignKeyProgramButAcceptsSameProgramLive        codes=['1','0']
evictionDeletesOnlyUnpinnedStagingIntermediates                               codes=['1','0']
commitTransactionExecutesDemoteAndEvict                                       codes=['1','0']
```

`commitTransactionExecutesDemoteAndEvict` 与 `evictionDeletesOnlyUnpinnedStagingIntermediates` 绿 =
计划任务 4（checkpoint 保留接线，demote/evict 在提交事务内执行、只删未 pin 的 STAGING 中间态）在设备面成立；
`messageLinkRejectsForeignKeyProgramAndStagingSnapshots` / `revisionPinRejectsStagingAndForeignKeyProgramButAcceptsSameProgramLive` 绿 =
计划任务 5（同 program CAS + 题面 link 拒 STAGING）成立；
`snapshotIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite` 绿 = snapshotId 写库前复派生并比对成立。

## 5. 失败 / 跳过 / 异常

无。**UNVERIFIED（本轨范围外）**：计划任务 6 的"内容唯一键含 renderer/字体版本"已推迟到批次 32，
本批无对应设备用例；`TutorVisualStructureGatesInstrumentedTest` 不覆盖该面。

## 6. 脱敏与去噪

- 删除：无。
- 保留：三套件 summary、关键用例状态码。
- 扫描：R4 §4.4 → 0 命中。
- 二进制/截图：无。

## 7. 判定

PASS（3/3 套件，17/17 用例）。判据 §4.5 要求的三套件全部执行且全绿；新增方法名确在 APK 内（E-00 dex 门）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step09-visual-commit{,.log,-logcat.txt}`（同前缀）
  · `step10-visual-repo{...}` · `step11-structure-gates{...}`
- 行数/大小：135 / 65 / 65 行（log）；logcat 99 / 86 / 140 行
- 说明：`build/` 被 `.gitignore:15` 忽略。
