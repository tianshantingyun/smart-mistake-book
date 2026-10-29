# E-08 提交 / 仓库两套 `TutorVisualCommitInstrumentedTest` + `TutorVisualCommitRepositoryInstrumentedTest`（PASS 11/11 + 8/8）

| 字段 | 值 |
|---|---|
| evidence-id | E-08 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 三（判据 §5.2–§5.4；计划任务 7–9） |
| gate-or-kd | 预算 / evict / pin / 租约 / digest 负向 |
| verdict | PASS |
| executed-at | 2026-10-29T11:27:09–11:27:16+08:00（`Time: 2.793` / `2.265`） |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/database/.../database-debug-androidTest.apk`（11 例）/ `core/data/.../data-debug-androidTest.apk`（8 例） |
| apk-sha256 | `90be7c2bc54da6f68067c421e1cc0cc8538f892dab77e1514a0d3f8d89115dda` / `de27b081942695474091500d0d7f4a53e8b70e5b3106f1c152f3e1ae2325f15c` |
| apk-version | （测试包空） |
| exit-code | 两次宿主 `EXIT=0`（非判据） |
| raw-log | `step06-visual-commit.log`（163 行）/ `step07-visual-repo.log`（121 行）+ 两份 logcat |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

- 提交套件构建前复读 **11**（批次 31 设备跑 9；新增 `snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite`，`:124`）。
- 仓库套件构建前复读 **8**（批次 31 设备跑 4；新增租约 1 + 事务内预算 2 + 源码预算 1）。
- 关键新增必须绿：digest==sha256(json) 负向、源码 64 KiB 超限拒绝、plan→command evict 端到端、旧 LATEST 降级。
- 判据 §5.2 的 “10 / 5” 是 09:58 快照，按 §1.3 以复读为准（11 / 8）。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
for pair in \
  "com.tingyun.smartmistakebook.core.database.TutorVisualCommitInstrumentedTest|com.tingyun.smartmistakebook.core.database.test|step06-visual-commit" \
  "com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepositoryInstrumentedTest|com.tingyun.smartmistakebook.core.data.test|step07-visual-repo"; do
  cls=${pair%%|*}; rest=${pair#*|}; pkg=${rest%%|*}; name=${rest#*|}
  $A -s $S logcat -c
  $A -s $S shell am instrument -w -r -e class "$cls" "$pkg/androidx.test.runner.AndroidJUnitRunner" \
    | tee "build/device-track-batch32/$name.log"
  $A -s $S logcat -v threadtime -d > "build/device-track-batch32/$name-logcat.txt"
done
```

## 3. 结果摘要

| 套件 | tests | failures | errors | skipped | `STATUS_CODE: 0` |
|---|---|---|---|---|---|
| 提交 | 11 | 0 | 0 | 0 | 11 |
| 仓库 | 8 | 0 | 0 | 0 | 8 |

- 两套 `INSTRUMENTATION_STATUS_CODE: -2` 均 0；`stack=` 均 0；logcat 9 类签名均 0（见 E-12）。

## 4. 关键输出（有界）

```text
Time: 2.793
OK (11 tests)

Time: 2.265
OK (8 tests)
```

仓库套件新增/加强用例（构建前实读）：

```text
commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand        # :184
freshForeignStagingLeaseIsNotEvictedByAnotherRequest                    # :258
perProgramBudgetIsReinforcedInsideTheTransactionAfterAStaleRead          # :300
globalBudgetIsReinforcedInsideTheTransactionAfterAStaleRead              # :350
snapshotOverSourceBudgetIsRejectedBeforeAnyWrite                        # :392
```

提交套件新增：

```text
snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite         # :124
```

## 5. 失败 / 跳过 / 异常

无。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary、逐用例状态码、新增用例名。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无。

## 7. 判定

PASS：11/11 与 8/8；**计划任务 7/9 的目标在现树已落码并上设备**——
- 事务内预算重读：`core/database/.../RoomStudyDatabase.kt:2165-2181`（`perProgramBudgetBytes` / 全局门在事务内抛出）；
- evict STAGING 租约条件：`core/database/.../dao/TutorVisualDao.kt:216-225`（owner / created_at）。
- 判据 §5.4 “未落”是 09:58 快照，已被现树取代。**仍不声称** DB 级触发器 backstop 生效（迁移实现裁定本批不加）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step06-visual-commit.log`（163 行 / 8,563 B）、`step07-visual-repo.log`（121 行 / 6,462 B）及两份 logcat。
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。

## 9. 更正（2026-10-29，落盘后复核）

- 原引用（§1、§4）把 `snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite` 标为 `:124`，
  错在 `:124` 不是该用例的物理行——`:124` 是上一用例的闭括号、`:127` 是 `@Test`；
  现更正为 **`:128`**（`fun snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite()`）。
  原行保留不改，判定不变（该用例 11/11 内实跑且 `STATUS_CODE: 0`）。
  复核命令：`sed -n '118,130p' core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualCommitInstrumentedTest.kt`。
