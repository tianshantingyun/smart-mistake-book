# E-12 KD-80 四项关闭门核销：C1 PASS / C2 PASS / C3 PARTIAL / C4 UNVERIFIED → **KD-80 保持 open**

| 字段 | 值 |
|---|---|
| evidence-id | E-12 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 三（判据 §3 KD-80 关闭判据） |
| gate-or-kd | **KD-80**（`docs/known-defects.md:2963` 登记；`:2971` Reopen/关闭条件；`:2973` 批次 31 T5 Progress） |
| verdict | **PARTIAL**：80-C1/C2 满足；80-C3 设备半边满足、JVM 半边本轨 UNVERIFIED；80-C4 台账要求项在当前 34 例中**零断言** → 不满足关闭规则 |
| executed-at | 2026-10-29T06:31:29（C1）· 06:43:05（C3 设备）· 06:45:11（C2 比对） |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | 四包（E-00 §3） |
| apk-sha256 | 见 E-00 §3 |
| apk-version | `0.2.0-offline`（app）/ 测试包空 |
| exit-code | C2 脚本 `EXIT=0`（`RESULT=PASS`）；`grep -rln tutor_visual` 0 命中（grep 退出码 1 = 无匹配，非失败） |
| raw-log | `build/device-track-batch31/step13-kd80-c2.log` · `step01-backup.log` · `step12-trimmer.log` |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

KD-80 台账原文四项：**80-C1** 备份 34/34 设备绿；**80-C2** 表清单与 schema 逐名一致（可复跑）；
**80-C3** 裁剪器/选择计划单测绿；**80-C4** 备份/恢复后旧行与视觉四表引用关系保持。
关闭规则：`80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4`。缺项 → open 或逐项写部分关闭理由。

## 2. 命令（可复跑）

```bash
# 80-C1（见 E-01）
$A -s emulator-5562 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner

# 80-C2（S 取当前 STUDY_DATABASE_VERSION）
S=70 python build/device-track-batch31/kd80-c2-compare.py; echo "EXIT=$?"

# 80-C3 设备半边（见 E-11）
$A -s emulator-5562 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupSnapshotTrimmerInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner

# 80-C4 覆盖检查
grep -rln "tutor_visual" core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/ | wc -l
```

## 3. 80-C1：备份 34/34（E-01）

`OK (34 tests)`，0 失败/错误/跳过；批次 30 的 8 条红例逐条 `STATUS_CODE=0`；
`BackupSnapshotTrimException`=0、`expected:<69>`=0。**满足**。

## 4. 80-C2：表清单与 `70.json` 逐名一致（本轮实跑）

```text
SCHEMA=70 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90
ONLY_PLAN=[]
ONLY_JSON=[]
DUP=[]
VIEWS_MATCH=True
PLAN_VERSION=70 JSON_VERSION=70
PLAN_HASH=11a8a701bae767a8a3c5bc810e4743a4 JSON_HASH=11a8a701bae767a8a3c5bc810e4743a4
RESULT=PASS
EXIT=0
```

90 张实体 + 2 视图、双向差集空、无重复、版本与 identityHash 逐字相同。**满足**。
（S=71 时脚本会因 `71.json` 缺失 `EXIT=2`；本批未升 schema，不适用。）

## 5. 80-C3：裁剪器/选择计划单测

| 半边 | 结果 | 判据 |
|---|---|---|
| 设备 `BackupSnapshotTrimmerInstrumentedTest` | `OK (9 tests)`（E-11） | **满足** |
| JVM `SmbkSelectionPlanTest` | **本轨未执行**（12 例，只读计数；属 Gradle 单测任务，超出本轨 assemble* 允许面） | **UNVERIFIED（本轨）** |

门禁轨本轮报"单测门第 1 轮 exit=0"（工单转录，非本轨 E-file）。按"未跑 = 未验证"，
**80-C3 只能记 PARTIAL**；不以工单转录写成满足。

## 6. 80-C4：旧行与视觉四表引用关系（**缺口未变**）

```text
grep -rln "tutor_visual" core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/ | wc -l
0
```

`BackupRestoreInstrumentedTest` 中没有任何对 `tutor_visual_program` / `tutor_visual_snapshot` /
`tutor_visual_message_link` / `tutor_visual_revision_pin` 的行数或外键引用断言。34/34 绿只证明
"归档/恢复路径没崩 + 清单同步"，**不证明四表引用关系保持**。

处置二选一（判据 §3.4）：① 在备份 34 例内补一条显式断言（v70 库含视觉四表行 → 备份 → 恢复 →
行数/引用保持）；② 如实登记 UNVERIFIED。本轨选 ②：目标文件 `BackupRestoreInstrumentedTest.kt` 是他线
` M`（`core/data` 禁改热区），补断言属测试落码，不由设备轨代写。

**80-C4 = UNVERIFIED（台账要求未被任何用例覆盖）。**

## 7. 判定

| 项 | 状态 |
|---|---|
| 80-C1 | **PASS** |
| 80-C2 | **PASS** |
| 80-C3 | **PARTIAL**（设备 9/9 绿；JVM 12 例本轨 UNVERIFIED） |
| 80-C4 | **UNVERIFIED**（零断言） |

**KD-80 保持 open**：关闭规则未满足。可写"80-C1/80-C2 已核销"，但不得写"KD-80 closed"。
剩余两项的归属：80-C3 JVM 半边 = 门禁轨 E-file；80-C4 = 测试落码（他线/记录员裁定是否本批补断言）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step13-kd80-c2.log`（8 行）；C1/C3 见 E-01/E-11 的 raw-log
- 行数/大小：C2 8 行 / 238 B
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
