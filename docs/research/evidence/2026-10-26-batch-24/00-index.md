# 批次 24 设备证据索引（2026-10-26）

- 记录：`docs/research/2026-10-26-batch-24-record.md`（**已生成**；本索引先落盘，记录由记录员后写并完成回链，见 §9）
- 计划：`.zcode/plans/plan-2026-10-26-batch-24-isolation-final-kd70.md`
- 工作树 HEAD：`5bc8bdf868e1296049ab901cf1da69e136062006`
- 证据目录：`docs/research/evidence/2026-10-26-batch-24/`
- 本目录体积：**9 个 `.md` / 84 KiB 文件内容（`du -ck *.md`）/ 88 KiB 磁盘块（`du -sk --exclude=.mimosa`）/ 1,269 行**；含被忽略的 `.mimosa/` 钩子状态为 94 KiB（实测见 §6；规范上限 16 个 / 512 KiB）
- 规范版本：`docs/research/2026-10-26-device-evidence-convention.md` v1
- 执行者：设备验证轨（subagent）；只跑 Gradle `assemble*` 与裸 `am instrument`，**未跑 `connectedAndroidTest`**
- 本轨未改产品源代码；只新增本目录。工作树开工即脏（219 M + 480 ??，他线在飞）

## 1. 环境

见 `step00-environment.md:1`。设备：

| serial | AVD | API | ABI | PAGE_SIZE | 用途 |
|---|---|---|---|---|---|
| emulator-5554 | test_device | 34 | x86_64 | 4096 | 隔离门 / KD-70 / release 冒烟 / 备份迁移 |
| emulator-5556 | ps16k_x86_64 | 36 | x86_64 | **16384** | 16KB 运行时门 |

`adb devices -l` 另列 1 台物理设备与 `emulator-5562` / `emulator-5584`（他线在飞）；本轨未使用。

## 2. 产物身份

| 产物 | 本机路径 | 字节 | sha256 | versionName |
|---|---|---|---|---|
| app debug APK（E-01 用，21:31 构建） | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | 189,363,367 | `53c3a453219f10352535f4b2e8d4e3b7e4f1aa4dfadd31b089d90a8328026eba` | 0.2.0-offline |
| app debug APK（E-01b 用，21:37 他线重建；最终安装件） | 同上路径（已被重建覆盖） | 189,363,334 | `782cebd66eb12c8656ccc5fc518600cdf63ddd8572f7a6908d143e31930173c6` | 0.2.0-offline |
| app debug androidTest APK | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | 1,564,771 | `343b637af805798ac1dfadfe8f2ebd0c2a59f5aec390c9b6a3e8f35760e08c0e` | — |
| app release APK（一次性本地 key） | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk` | 155,699,070 | `597129283c4d83c3a320bf3dbe963a1dce32297b5a78caec448b618943c180a7` | 0.2.0-offline |
| core:data androidTest APK | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | 193,441,519 | `8ff654e95686ea5ef89a73cc797f6ae7688c720877b01a50700dff6d5d605281` | — |
| core:database androidTest APK | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` | 196,665,032 | `1196dd93abdd781f23fb92b42eee1c876283fcb0b852ff1fb5d98a0e3b33dba6` | — |
| release mapping.txt（不提交本体） | `app/build/outputs/mapping/strictOfflineRelease/mapping.txt` | 123,742,810 | `10f6d4cfe2bd8386ef1c88df706ba45b91b7c0a327221f482e78191e2eac7671` | — |

设备侧复核：`pm path` + `sha256sum` 与上表逐字节一致（debug 两代均复核；release 证书
`CN=Batch24 Local Smoke`，**不是生产发布证书**）。详见 `step00-environment.md:86`。证据落盘员本轮在盘上重算 5 个 APK + mapping 的 sha256/字节数，与上表逐项一致（2026-10-26）。

## 3. 步骤与结论

| E-ID | 步骤 | 门/KD | 设备 | 判定 | 证据（本批目录内相对路径:行） |
|---|---|---|---|---|---|
| E-00 | 环境 / 设备 / 产物身份 | — | 两台 | PASS（信息性） | `step00-environment.md:114` |
| E-01 | VisualJs 隔离复跑（21:31 构建件） | 设备功能门 / KD-66 | emulator-5554 | PASS（6/6） | `step01-visualjs-isolation.md:124` |
| E-01b | 同上复跑（21:37 重建件；supersedes E-01 的产物身份） | 设备功能门 / KD-66 | emulator-5554 | PASS（6/6） | `step01b-visualjs-isolation-rerun.md:102` |
| E-02 | QuickJS 全量（KD-70 P1–P5，T2 新夹具） | KD-70 | emulator-5554 | PASS（20/20；P2 绿） | `step02-quickjs-oom.md:141` |
| E-03 | release 变体（R8）冷启动 ×2 | KD-69 release 侧 | emulator-5554 | PASS（两次 ok/COLD） | `step03-r8-release-smoke.md:174` |
| E-04 | 16KB 运行时装载 | 16KB 门 / KD-74 | emulator-5556 | PASS（3/3；PAGE_SIZE=16384） | `step04-page-size-16k.md:107` |
| E-05 | 备份/恢复核心类 | 备份门 | emulator-5554 | PASS（34/34） | `step05-backup-restore.md:89` |
| E-06 | 迁移演练 + 全量迁移矩阵 | 迁移门 | emulator-5554 | PASS（5/6 + 设计跳过；矩阵 15/15） | `step06-migration.md:138` |

### 3.1 关键结论（便于记录员转录）

- **隔离门（KD-66）**：T1 的 `abandon()` 死亡确认修复在两次独立执行中均成立——6/6 全绿；
  `theClientRebuildsAtMostOnceAfterConfirmedAbandons` 在重建后真实 `execute` 返回 `Rendered`
  （BUSY 会红），第二次确认后不再 bind；冷启动 7.6 s 延迟 attach 用例通过（首连 15 s 预算）。
  logcat 无 `death was not confirmed` 告警（确认走 death/`isBinderAlive` 路径）。
  **但 KD-66 的关闭门仍是 hard+500 ms 设备用例，本类以 ≤6 s 判死，不能关闭 KD-66；JS 仍不广告。**
- **KD-70**：全量 20/20。P2 新夹具成立——reason=`MEMORY_LIMIT`、诊断含
  `attributed by memoryUsage` / `mallocLimit=16777216` / `PENDING_CALIBRATION`，且
  `mallocSize != -1`；由断言可推出的下限为 `mallocSize >= 15,099,495`（90%）。精确读数
  未打印（UNVERIFIED 精确值）。批次 23 的 `mallocSize=177216` 形状未复现。关闭与否由主会话裁定。
  **口径限制**：只测 x86_64/API 34/4 KB；R2 §5 要求的 arm64/16 KB 未测（`step02-quickjs-oom.md:18`/`:148`）。
  P2 用例总耗时 98 ms（logcat `started 13:33:46.398` → `finished .496`），用例内
  `cooperativeTimeoutMillis=10_000`（非默认 100 ms）；若换回默认 100 ms 或跑在慢速/arm64
  设备，存在先落 `PHASE_TIMEOUT` 而非 OOM 的风险。
- **KD-69（release 侧）**：`strictOffline release`（R8 + shrinkResources）两次冷启动均
  `Status: ok` / `LaunchState: COLD`（2405 / 1245 ms），PID 存活，9 类崩溃签名 0。
  **但 release 用一次性本地 key 签名（生产 `RELEASE_*` 缺失），且本步未跑中文 OCR**，
  不能据此声称 KD-69 完整 Reopen condition 达成。
- **16KB**：真实 16384 页内核上 3/3，`PAGE_SIZE=16384` 打印，QuickJS/LiteRT 原生库装载成功；
  arm64 16KB 运行时仍未测。
- **备份/迁移**：备份 34/34；迁移演练 opt-in 5/6（1 个 kill-stage 按设计 skip）；全量迁移矩阵
  15/15（含 1→当前逐版本，耗时 1,431.852 s）。

## 4. KD-74 覆盖（四类 + T1 + 产物身份）

KD-74 已于 2026-10-26 批次 24 T3 关闭：条目标题见 `docs/known-defects.md` KD-74（记录时点 `:2768`，2026-10-27 批次 25 T5 台账同步插入 KD-69/70 收口 Progress 后实读复核为 `:2774`），关闭 Progress 见同文件（记录时点 `:2778`，现 `:2784`）。三条件可逐项复核——① 记录回链 = `docs/research/2026-10-26-batch-23-record.md:202`（"KD-74 closed" 补记）与 `:415`（主会话等价裁定）；② 证据 = 批次 23 `docs/research/evidence/2026-10-26-batch-23/` 的 15 个 `.md`（`git ls-files docs/research/evidence/ | wc -l` = 15，E-00…E-09）；③ 主会话裁定 = 上述 `:415`。**本批 8 个 step 是对同一审计面的增量覆盖，不是关闭的必要条件，也不代表重开**；本批记录已生成（`docs/research/2026-10-26-batch-24-record.md`），其 §4 设备回链已补齐，见 §9。

| KD-74 要求 | E-ID | 状态 | 备注 |
|---|---|---|---|
| 每条命令的 `am instrument` summary / `INSTRUMENTATION_CODE` | E-01/01b/02/04/05/06 | 有 | 每个 step §4 均含 summary 与 `INSTRUMENTATION_CODE` |
| logcat 崩溃签名全量 grep（含 0 命中） | E-01/01b/02/03/04/05/06 | 有 | 每步给出命令与 0 计数；E-03 含 9 类 |
| 16KB `getprop`/`getconf` + 运行时装载 | E-04 | 有（运行时） | `getconf PAGE_SIZE`=16384、`PAGE_SIZE=` 打印；静态 ELF 沿用批次 23 E-03/E-09 |
| 备份/迁移用例输出 | E-05、E-06 | 有 | 34/34；5/6 + 15/15 |
| T1 修复后设备复跑 | E-01、E-01b | 有 | 两代 debug APK 各 6/6 |
| APK/mapping sha256 | E-00、§2 | 有 | 5 个 APK + release mapping（`10f6d4cf…7671`）；mapping 本体不提交 |

## 5. 缺口与未落盘

1. **生产 release 签名 UNVERIFIED**：本机没有 `RELEASE_*`，`validateReleaseSigning` 原始失败见
   `step03-r8-release-smoke.md:48`；release 冒烟包是一次性本地 key。
2. **KD-69 的中文 OCR 未在本批 release 步执行**：本步只做冷启动；OCR 判据沿用批次 23 E-04（internal）。
3. **KD-66 的 hard+500 ms 关闭门未覆盖**：现有 watchdog 用例以 ≤6 s 判死。
4. **P2 精确 `mallocSize` 未打印**：测试无 println，只有断言约束的区间。
5. **arm64 16KB 运行时未执行**：ps16k 是 x86_64 AVD。
6. **kill-stage 显式崩溃用例未跑**：需要 `-e smb_d1_kill_stage true`；批次 23 E-06 §9.2 有历史记录。
7. **debug APK 在批次中被他线重建**：E-01 与 E-01b 分别固定两代哈希；两者都对应同一份 T1 源码。
8. **release APK 是 21:35–21:42 的时点产物**：若他线随后改了源代码，不在该包内；引用时以 sha256 为准。
9. **本批未做静态 ELF/`zipalign` 复核**：沿用批次 23 E-03/E-09，不重复声明。
10. **批次记录已生成**：`docs/research/2026-10-26-batch-24-record.md` 已由记录员写入并完成 §4 回链（2026-10-26）；此条为索引落盘时点的历史记录。

## 6. 脱敏与去噪

```bash
E=docs/research/evidence/2026-10-26-batch-24
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
find "$E" -type f -name '*.md' -printf '%s %p\n' | sort -n
find "$E" -type f -name '*.md' | wc -l
du -sk "$E"
```

结果：

- secrets：0 命中
- credential assignments：0 命中（release 一次性口令在本目录写作 `<local-smoke>`，未落明文）
- emails：0 命中
- home paths：1 命中，为扫描命令自身的正则文本（`00-index.md:105`），不含真实用户路径；人工复核确认无主目录泄露
- 体量（索引补写后实测）：9 个 `.md` / 1,269 行；`du -ck *.md` = 84 KiB（文件内容），`du -sk --exclude=.mimosa` = 88 KiB（磁盘块）；单文件最大约 18.0 KiB（本索引）/ 195 行（本索引），远低于 64 KiB / 400 行
- 与工单 81 KiB 的差异：工单值是设备轨落盘时的原始 9 文件；本轨在 §4 补 KD-74 关闭回链、并新增 §9 记录回链待办后，索引由 11,621 B 增至约 17.0 KiB，故重测为 84/88 KiB。**step00–step06 的字节数未变**（见上方 `find` 输出），体量上限结论不变
- 工具状态：`2026-10-26-batch-24/.mimosa/` 是 Mimosa 钩子的会话状态（`bashMutation` 记录，`.gitignore:29` 忽略），**不是证据**，不得加入提交路径白名单；本目录 `du -sk` 的 94 KiB 与 88 KiB 之差即此目录（6 KiB）
- 物理设备 serial、真实学习者数据、截图/二进制：均无
- 复跑：本表数值由证据落盘员在本轮重新执行上述 `grep`/`find`/`du` 得到；无命中项均为逐条运行后的 `0`，不是"未发现"。原始日志复核（本机 `build/device-track-batch24/`，不入仓，仅核对转录）：7 个 `OK (N tests)`/`Time:` 与各 step 一致（6/22.208、6/23.945、20/8.275、3/0.039、34/67.333、6/14.555、15/1,431.852），7 个 logcat 的 8 类签名均 0，release 冷启动两文件与 step03 §4 逐行一致，`validate-release-signing.log:22` 原文匹配

## 7. 回链检查

- [x] 每个设备数字都有 `E-ID + path:line`（§3、§3.1；落盘员已逐条核对 §3 的 8 个 `step:line` 均指向各 step 的判定节）
- [x] KD-74 关闭回链：`docs/known-defects.md` KD-74 标题/关闭 Progress（记录时点 `:2768`/`:2778`；2026-10-27 批次 25 T5 台账同步后复核为 `:2774`/`:2784`） + `docs/research/2026-10-26-batch-23-record.md:202`/`:415` + 批次 23 的 15 个已跟踪 `.md`（结论见 §4；行号按 2026-10-26 记录时点复核）
- [x] `docs/known-defects.md` 的 **KD-66/69/70** Progress 引用本索引——已回填（KD-66 批次 24 设备轨 Progress、KD-70 批次 24 设备轨 Progress、KD-69 的 E-03 release 冒烟）；**行号勘误（2026-10-27 批次 25 T5 台账同步时实读复核）**：本行原写 `:2607`/`:2676`/`:2705`，其中 `:2676`/`:2705` 与 KD-70/KD-69 错位（`:2676` 是 KD-69 的 E-03 回填、`:2705` 是 KD-70 的批次 24 Progress）——正确对应：KD-66 → `:2607`（未漂移）、KD-70 → `:2709`、KD-69 → `:2678`（后两者因批次 25 T5 在 KD-69 条目内插入收口 Progress 各 +4 行）
- [x] 批次 24 记录 §4 的每个设备数字回链本索引——记录已生成（`docs/research/2026-10-26-batch-24-record.md`），§4 的 8 个设备数字（E-00…E-06）均带 `E-ID + evidence/2026-10-26-batch-24/<step>.md:<line>`
- [x] 每个 E-file 有 `sanitized` 字段且扫描 0 命中（§6；唯一 home-path 命中是扫描正则自身）
- [x] 没有把 `build/` 路径当作关闭证据（只出现在 `raw-log`）
- [x] 文件均在 `docs/research/evidence/` 下，`git check-ignore` 无输出（9/9 可跟踪）；`git status` 为 9 条 `??`
- [x] 模板占位：全目录仅 `<empty>`（`getprop` 原值）与 `<local-smoke>`（一次性口令占位）两处，均为 R4 允许的替换；无残留未填的模板字段（YYYY/NN 类标记全部替换）

## 8. 复跑入口（最小）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# 隔离门
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# KD-70
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# 16KB（必须在 ps16k）
$A -s emulator-5556 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.NativeLibraryPageSizeInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# 备份 / 迁移
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 shell am instrument -w -r -e smb_allow_data_reset true \
  -e class com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
```

构建见 `step00-environment.md:55`；release 构建的签名限制见 `step03-r8-release-smoke.md:48`。

## 9. 批次 24 记录回链：目标与待办（记录员 / 主会话）

**目标文件**：`docs/research/2026-10-26-batch-24-record.md`。本索引落盘时该文件**不存在**（记录由记录员后写）；本批 step 的 `record-ref` 字段与本节均指向该路径。本索引 §4/§6/§7/§9 由证据落盘员本轮补写；step00–step06 未改动（字节数与落盘时一致）。

记录 §4 必须包含下表每个设备数字，并按 R4 §7.2 写成 `E-ID` + 反引号内的 `evidence/2026-10-26-batch-24/文件名:行号`（行号为判定节，不是标题行）：

| E-ID | 必须回链的结论 | 引用 |
|---|---|---|
| E-00 | 两台设备身份；5 个 APK + mapping 的 sha256/字节数；设备侧哈希与 release 证书 | `evidence/2026-10-26-batch-24/step00-environment.md:78`（产物表首行，至 `:84`）；设备复核 `:86`；判定 `:114` |
| E-01 | 隔离门 6/6（debug `53c3a453…`） | `evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:124` |
| E-01b | 隔离门 6/6（当前安装件 `782cebd6…`） | `evidence/2026-10-26-batch-24/step01b-visualjs-isolation-rerun.md:102` |
| E-02 | KD-70 全量 20/20；P2=`MEMORY_LIMIT`，`mallocSize ≥ 15,099,495` | `evidence/2026-10-26-batch-24/step02-quickjs-oom.md:141` |
| E-03 | release 变体两次 `ok/COLD`；生产签名 UNVERIFIED | `evidence/2026-10-26-batch-24/step03-r8-release-smoke.md:174`（限制见 `:160`） |
| E-04 | 16KB 3/3，`PAGE_SIZE=16384` | `evidence/2026-10-26-batch-24/step04-page-size-16k.md:107` |
| E-05 | 备份 34/34 | `evidence/2026-10-26-batch-24/step05-backup-restore.md:89` |
| E-06 | 迁移演练 5/6 + 设计跳过 1；矩阵 15/15 | `evidence/2026-10-26-batch-24/step06-migration.md:138` |

待办与禁止项：

1. **KD-74 不要在本批记录里重新写新的 "KD-74 closed" 动作**。关闭已由批次 23 记录 `:202` 补记、`docs/known-defects.md:2768`/`:2778` 同步；本批只提供增量 E 证据。若需提及，写"沿用 `docs/research/2026-10-26-batch-23-record.md:202` 的 KD-74 closed"。
2. 记录内引用一律用 `evidence/...` 相对路径；**不得**把 `build/` 当关闭证据；没有 E-file 的数字写"工单转录"。
3. 记录写完后跑 R4 §7.5 链接检查（`grep -oE 'evidence/[A-Za-z0-9._/-]+\.md(:[0-9]+)?'` + `test -f`/行号范围），把结果写进记录"验证"节。
4. `docs/known-defects.md` 的 **KD-66/69/70** Progress 已由复核修复员回填本索引 §3/§3.1 与对应 `step:line`；本批**不关闭** KD-66（隔离门判据弱于 hard+500 ms）、KD-69（缺中文 OCR 与生产签名）、KD-70（关闭由主会话裁定，P2 仅设备实测绿）。
5. 提交路径白名单：显式列本目录 9 个 `.md`。不要按模块 `git add`；不要依赖忽略规则去处理 `.mimosa/`（`.gitignore:29` 会忽略它，但白名单里不列该目录）。
6. 完成后把 §7 的两个 `[ ]` 勾成 `[x]`，并在本节追加记录文件名与回链时间（索引是映射文件，可更新；step 文件不改历史行）。

**回链完成（2026-10-26，记录员）**：`docs/research/2026-10-26-batch-24-record.md` 已生成；§4 的 E-00…E-06 设备表逐项引用本索引与对应 step 判定行，§7 两个待办已勾；本索引 §4 的"记录尚未生成"表述已同步更新。记录 §9 记录链接检查（15 条真实引用 0 失败）与 `git diff --check`（exit=0）。
