# 批次 25 设备证据索引（2026-10-27）

- 记录：`docs/research/2026-10-27-batch-25-record.md`（已生成 2026-10-27，记录员回链本索引 §3/§4 并勾掉 §7 第三项，同批次 24 先例）
- 计划：`.zcode/plans/plan-2026-10-27-batch-25-race-warmup-queue.md`
- 工作树 HEAD：`25ddb60273e32ebd4100e8deb9f965bb0dfd4476`
- 证据目录：`docs/research/evidence/2026-10-27-batch-25/`
- 本目录体积：**7 个 `.md` / 55,376 B 内容（设备轨时点，`find -printf '%s'` 求和）/ 69 KiB 磁盘（`du -sk`）**；上限 16 个 / 512 KiB（规范 §3.1）；证据落盘员终测（§9 追加后）见 §9.5
- 规范版本：`docs/research/2026-10-26-device-evidence-convention.md` v1
- 执行者：设备验证轨（subagent）；只跑 Gradle `assemble*`/`validateReleaseSigning` 与裸 `am instrument`，**未跑 `connectedAndroidTest`**；未做 git 写操作；未改产品/测试源代码（T3 红例只抓栈登记，修复权属 T3 轨/主会话）
- 本轨新增仓库内文件 = 本目录 6 个 `.md`；工作树开工即脏（他线在飞），其余改动非本轨

## 1. 环境

见 `step00-environment.md:1`。设备（本轨专用两台；5562/5584 属他线，全程未触碰）：

| serial | AVD | API | ABI | PAGE_SIZE | 用途 |
|---|---|---|---|---|---|
| emulator-5554 | test_device | 34 | x86_64 | 4096 | 隔离门（T1/T2/T3 首验）/ R8 release 冒烟 / 备份抽验 |
| emulator-5556 | ps16k_x86_64 | 36 | x86_64 | **16384** | KD-70 P2 重跑 + 16KB 装载门 |

**环境异常（如实）**：开工后两台目标机 guest system_services 先后楔死（`Can't find service:
package`；5554 crash buffer 有 `failed to set system property` 致命错 + ≥5 次 system_server
重启；guest `adb reboot` 无效——uptime 未复位）。宿主资源同时段健康（64 GB/10.7 GB 空闲，
CPU 25%），非宿主饥饿。恢复 = 逐台 `emu kill` + `-no-snapshot` 冷启动（证据
`step00-environment.md:53`）；此后所有安装/执行均在冷启动 + 沉降（150 s）后的设备上进行，
身份三值（AVD/API/PAGE_SIZE）每步前复核。

## 2. 产物身份

| 产物 | 本机路径 | sha256 | 备注 |
|---|---|---|---|
| app debug APK | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | `1af0f99af2e2bf3182f4838357ad2e5a93568ac4a33439524ffbde6214e7b737` | 隔离门/备份用 |
| app debug androidTest APK | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | `6623b0acebb3cca4bcf891051d0f5a41641d3d7e6c45750fcbac2d5302ec600c` | 含 T1 新类 + T2/T3 新用例 |
| core:data androidTest APK | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | `94df5c3931db09170dc8ac52db3a074c281bc2b8869bdba3ced724e08dea8590` | KD-70/16KB/备份用 |
| app release APK（一次性本地 key） | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk` | `824cd5b2e93eecbaeb6bf8695e780ba37c674cc99f0a8a76790d4b03bfb3735f`（155,699,091 B） | 冒烟用；cert `CN=Batch25 Local Smoke`，SHA-256 `97a31adc…1f6fe`，**非生产证书** |

三件套 debug 构建 exit=0（`BUILD SUCCESSFUL in 45s`）；release 构建 exit=0（`3m 11s`）；
`validateReleaseSigning` **exit=1**（生产 `RELEASE_*` 缺失，`step04-r8-release-smoke.md:46`）。
设备侧每步 `pm path`+`sha256sum` 与上表逐字节一致（各 step §2/§3）。

## 3. 步骤与结论

| E-ID | 步骤 | 门/KD | 设备 | 判定 | 证据（本目录相对路径:行） |
|---|---|---|---|---|---|
| E-00 | 环境/设备/产物身份/环境异常恢复 | — | 两台 | PASS（信息性） | `step00-environment.md:155` |
| E-01 | 隔离套件全量（Isolation 12 + Race 2） | 隔离面 / KD-76 首验 / §64 负向首验 / T1 竞态首验 | emulator-5554 | **PARTIAL（11/12 + 2/2；1 红=T3 夹具缺陷）** | `step01-visualjs-isolation.md:191` |
| E-02 | KD-66 hard+500 ms 专属用例 | KD-66 关闭门 | — | **NOT-WRITTEN（未写，如实登记）** | `step02-kd66-hard-500ms.md:48` |
| E-03 | KD-70 P2 重跑 + 16KB 门 | KD-70 / 16KB 门 | emulator-5556 | PASS（20/20 + 3/3；arm64 UNVERIFIED） | `step03-kd70-p2-ps16k.md:146` |
| E-04 | R8 release 冷启动 ×2 | KD-69 release 侧（不回退） | emulator-5554 | PASS（两次 ok/COLD；生产签名 UNVERIFIED） | `step04-r8-release-smoke.md:154` |
| E-05 | 备份/恢复核心类 | 备份门（不回退） | emulator-5554 | PASS（34/34） | `step05-backup-restore.md:94` |

### 3.1 关键结论（便于记录员转录）

- **隔离面 PARTIAL**：批次 24 既有 6 例零回归全绿；**T1 交错用例 2/2 设备首验绿**（握手期死亡
  不发布 CONNECTED、重建预算只扣一次；红→绿推演仍属代码层论证，本轮只有绿侧证据）；
  **T2 warm-up/ready 3 例设备首验绿**（warm-up 中 acquire 有界拒绝、首连超时如实 FAILED 不锁存
  + 有界重试、ready 超时不锁存 + 重新 bind）；**T3 三例中两例绿**（>256 KiB 信封有界拒、并发
  第二请求 BUSY）。**1 红**：`aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot`
  ——T3 新用例夹具在复用已关闭的 `pipe[0]`（`VisualJsIsolationInstrumentedTest.kt:374` 附近），
  binder 客户端编组即抛 `Bad file descriptor`，确定性红、与设备无关；用例级证据缺失
  （null-回调子断言无设备证据），修复=夹具一行级改动（新开 pipe），断言不放宽；修复后复跑
  命令见 `step01-visualjs-isolation.md:56`。
- **KD-66 关闭门未写**：全 androidTest 源核查无 hard+500 ms 专属用例（现有 watchdog ≤6 s，
  文件 KDoc `:52` 自认）；KD-66 维持 open，理由与批次 24 一致。
- **KD-70**：P2 判据在 **16KB × x86_64** 轴成立（`MEMORY_LIMIT` + `attributed by memoryUsage` +
  `mallocLimit=16777216` + `PENDING_CALIBRATION` + `mallocSize != -1`，P2 耗时 34 ms），
  与批次 24 的 4KB × x86_64 合成双页宽矩阵；**精确 mallocSize 仍 EXACT VALUE UNVERIFIED**；
  **arm64 轴 UNVERIFIED**（R4 §2：本机原理性不可行）——不得写"16KB 已验证"，只能写
  "16KB × x86_64 已验证；16KB × arm64 UNVERIFIED"；真机子句 UNVERIFIED。
- **KD-69（release 侧）**：批次 25 源的 release APK 两次冷启动 `ok/COLD`（827/470 ms）、PID
  存活、9 类签名 0，**相对批次 24 E-03 不回退**；生产签名 UNVERIFIED（`validateReleaseSigning`
  exit=1 原始证据）；中文 OCR 仍未跑（本步不做，不声称 KD-69 完整达成）。
- **备份**：34/34，相对批次 24 E-05 不回退。
- **JS 仍不广告**：本批设备证据不含任何"JS 可用"结论；KD-76 保持 open（设备判据只拿到
  E-01 的部分证据，warm-up/ready 的契约 §4 worst-case 29.5 s 面未在设备实测）。

## 4. 任务书四步对照

| 任务书步骤 | 结果 | 证据 |
|---|---|---|
| 一、隔离套件全量（含 T1/T2/T3 新用例）：预期全绿，红则抓栈 | Isolation 11/12 + Race 2/2；红例已抓栈并归因（T3 夹具缺陷，非环境/非他线） | `step01-visualjs-isolation.md:139`（栈）、`:149`（归因） |
| 二、KD-66 hard+500 ms 专属用例（若已写） | **未写** → 如实登记，KD-66 维持 open | `step02-kd66-hard-500ms.md:48` |
| 三、KD-70 P2 在 ps16k 重跑：验证同判据；arm64 按 R4 声明 UNVERIFIED | 20/20 同判据全绿（P2 34 ms）；16KB 门 3/3；arm64/真机/精确值 UNVERIFIED | `step03-kd70-p2-ps16k.md:117` |
| 四、R8 release 抽验与备份抽验（不回退） | release ×2 ok/COLD 签名 0（不回退）；备份 34/34（不回退）；设备恢复 debug 态 | `step04-r8-release-smoke.md:154`、`step05-backup-restore.md:94` |

## 5. 缺口与未落盘

1. **T3 红例待修复复跑**：A-14（畸形参数 BAD_REQUEST 不占在飞槽）用例级设备证据缺失；
   修复权属 T3 轨/主会话（本轨不改源代码），复跑命令 = `step01-visualjs-isolation.md:62`。
2. **KD-66 hard+500 ms 专属用例未写**（本批四步之二 → NOT-WRITTEN）。
3. **生产 release 签名 UNVERIFIED**（无 `RELEASE_*`；一次性本地 key `CN=Batch25 Local Smoke`）。
4. **KD-69 中文 OCR 未跑**（沿批次 24 缺口）。
5. **arm64 16KB 运行时 UNVERIFIED**（R4 §2：本机原理性不可行；`ps16k_arm64` AVD + 镜像已预置待 ARM 宿主）。
6. **P2 精确 `mallocSize` EXACT VALUE UNVERIFIED**（断言只证 ≥15,099,495）。
7. **KD-76 的契约 §4 worst-case 29.5 s 启动面未设备实测**（E-01 只覆盖 T2 三用例的注入形状，
   非真实冷启动 attach 分布）。
8. **guest 楔死根因未深挖**（两台同发，宿主健康；已按环境故障恢复并登记，`step00 §2.1`）。
9. 本批未跑迁移矩阵/`core:database` 套件（任务书四步不含；沿批次 24 E-06 结论）。

## 6. 脱敏与去噪（落盘后统一扫描，2026-10-27）

```bash
E=docs/research/evidence/2026-10-27-batch-25
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
find "$E" -type f -name '*.md' -printf '%s %p\n' | sort -n
find "$E" -type f -name '*.md' | wc -l
du -sk "$E"
```

结果：secrets **0**、credential assignments **0**（一次性口令在全部文件写作 `<local-smoke>`，
不落明文）、emails **0**、home paths **1 命中 = 本扫描命令自身的正则文本（`00-index.md:107`），
与批次 24 同例，人工复核无主目录泄露**。体量：7 个 `.md` / 69 KiB 磁盘（`du -sk`）；内容字节以 §9.5 终测为准（本行所记 55,376 B 为设备轨时点值，索引补记使字节漂移 +135 B）。
物理设备 serial、真实学习者数据、截图/二进制：均无。

## 7. 回链检查

- [x] 每个设备数字都有 `E-ID + path:line`（§3/§3.1/§4）
- [x] `docs/known-defects.md` 的 KD-66/69/70/76 Progress 引用本目录——**未回填**（本轨不改台账，
      属记录员/主会话回填项；建议引用行见 §3.1 各条）
- [x] 批次 25 记录 §4 的设备数字回链本索引——记录已生成（`docs/research/2026-10-27-batch-25-record.md` §4；记录员勾选，2026-10-27）
- [x] 每个 E-file 有 `sanitized` 字段且 §6 扫描 0 命中
- [x] 没有把 `build/` 路径当作关闭证据（只出现在 `raw-log`）
- [x] 文件均在 `docs/research/evidence/` 下（7/7 可跟踪；`git status` 该目录整体 `??`）
- [x] 模板占位：`<local-smoke>`（一次性口令占位）与 `<empty>` 无残留未填字段

## 8. 复跑入口（最小）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# 隔离门（含 T1/T2/T3 新用例）
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsHandshakeRaceInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# KD-70 / 16KB（必须在 ps16k）
$A -s emulator-5556 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5556 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.NativeLibraryPageSizeInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# 备份
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
```

构建见 `step00-environment.md:87`；release 构建的签名限制见 `step04-r8-release-smoke.md:46`；
设备环境异常与恢复见 `step00-environment.md:53`。

---

## 9. 证据落盘员第二轮复核与 KD-66/76 关闭判据结论（2026-10-27 追加）

> 本节由证据落盘员（subagent）追加，不改上方任何既有行（R4 §7.6：step 文件不改，索引为映射文件可追加）。追加时点 HEAD 仍为 `25ddb602`；本节全部命令/实读在本轮执行，known-defects 行号为 2026-10-27 复核时点值（该文件整文件 M、多线在飞，引用时按批次 24 索引 §9 同款"时点值+实读复核"口径处理）。`build/` 下原始日志为本机材料不入仓，本节引用只作 E-file 一致性复核，关闭判据只认各 step 文件。

### 9.1 第二轮复核结果（全部本轮实跑）

| 检查 | 命令/方法 | 结果 |
|---|---|---|
| 索引引用链接检查（R4 §7.5） | 对 00-index 内 `step*.md:NN`/`00-index.md:NN` 全量提取（15 个唯一引用）逐条 `test -f` + 行数范围 | **15/15 OK**，无 MISSING、无 OUT-OF-RANGE |
| 脱敏扫描（R4 §4.4 四条正则） | 同 §6 命令原样重跑 | secrets **0** / credential assignments **0** / emails **0** / home paths **1**（= `00-index.md:107` 扫描正则自身，与批次 24 同例） |
| E-file ↔ 原始日志一致性抽查 | `grep`/`tail -n`/`cat -n` 对 `build/device-track-batch25/` 原始日志 | 全部吻合：isolation `FAILURES!!! / Tests run: 12, Failures: 1`（raw `:254-255`）、`Time: 55.742`（`:225`）、红例栈 `Bad file descriptor`←`Parcel.nativeWriteFileDescriptor`←`VisualJsIsolationInstrumentedTest.kt:375`（raw `:55-62`）；race `OK (2 tests)`/`Time: 1.04`；kd70 `OK (20 tests)`/`Time: 7.451`（`:284`/`:286`）；16kb `OK (3 tests)`/`Time: 0.025`（`:46`/`:48`）；`PAGE_SIZE=16384`（ps16k logcat `:1086`，19:32:30.935）；P2 started 19:32:30.038→finished .072=34 ms（logcat `:781-782`）；backup `OK (34 tests)`/`Time: 20.739`（`:480`/`:482`）；coldstart 两次 `Status: ok`/`LaunchState: COLD`/827/470 ms/PID 4972/5074 逐字核对；validate 日志"缺四变量"原文在 **`:8`**（工单转录写 `:2` 为转录偏差，E-file 未写行号、不受影响）；`death was not confirmed`=0；isolation/ps16k/coldstart×2/backup 签名抽查全 0 |
| @Test 计数 vs summary | `grep -c '@Test'` 五个测试类 | 12 / 2 / 20 / 3 / 34，与 E-01/E-03/E-05 summary 一致 |
| T3 红例夹具根因实读 | `VisualJsIsolationInstrumentedTest.kt:343-386` | `:365` close `pipe[0]`（`:366` close `pipe[1]`）→ `:375` 第三负向调用复用已关闭 `pipe[0]`；前两个子断言（`:360-364`、`:367-371`）在异常点之前已执行——与 step01 §5 归因逐行一致 |
| KD-66 未写核查（重跑） | `grep -rn "500" app/src/androidTest/ core/data/src/androidTest/`（剔除 1500/2500 等后逐条人工分拣）+ `grep -rn "2_000\|2000\|hostReturn\|returnsBy"` 两个 visual androidTest 目录 | 相关命中仅 `VisualJsIsolationInstrumentedTest.kt:52`（KDoc 自认"≤6 s 判死，不能作为 KD-66 hard+500 ms 关闭门"）与 race 夹具轮询常量 `CONNECTED_POLL_MILLIS`/`REBUILD_SETTLE_MILLIS = 500L`（`VisualJsHandshakeRaceInstrumentedTest.kt:196/:203`，轮询间隔、非返回时限）；`2_000/2000` 命中均为 epoch/夹具时间戳（FigureGenerationReconciler `:66-109`、isolation `:620 timeMs=2_000L`）；watchdog 判死实测 `withTimeoutOrNull(6_000L)`（`VisualJsIsolationInstrumentedTest.kt:198`） |
| 树内 T2 落层佐证 | `grep -n "lastConnectFailure\|lastBindFailedOnLoading\|READY_TIMEOUT_MILLIS" app/src/main/kotlin/.../visualjs/VisualJsClientEngine.kt`；`grep -n isReady` AIDL；`grep -n PROTOCOL_VERSION` | `VisualJsClientEngine.kt:134/:143/:598` 存在（loading 形状：不写 `unavailable`、不扣重建预算、诊断区分 loading/结构失败）；`IVisualJsService.aidl:38 boolean isReady()`；`VisualJsProtocol.kt:35 PROTOCOL_VERSION = 2` |

### 9.2 KD-66 关闭判据结论：**未达关闭门，维持 open**

- 关闭门原文（`docs/known-defects.md:2615`，Reopen condition）："issue #240 形状在 hard+500ms 内返回拒绝、且后续请求可用"的设备用例。
- 本批事实：专属用例**未写**（E-02 `step02-kd66-hard-500ms.md:48`，NOT-WRITTEN；9.1 重跑 grep 佐证）；现有 watchdog 用例以 `withTimeoutOrNull(6_000L)` 判死（`VisualJsIsolationInstrumentedTest.kt:198`），弱于关闭门；T1 交错用例 2/2 设备绿（E-01，fake-binder，`step01-visualjs-isolation.md:39-42`）不覆盖 #240 形状（`docs/known-defects.md:2611` 同句自认）；#240 上游仍 OPEN、daemon 线程泄漏形状未变。
- 结论：**KD-66 不因批次 25 设备轨关闭**。关闭动作属主会话，本节只给判据对照，不代写 KD 状态。

### 9.3 KD-76 关闭判据结论：**未达关闭门，维持 open**

- 关闭门原文（`docs/known-defects.md:2823` 验收判据 / `:2825` Reopen condition）：设备冷启动（含 CPU 竞争）首个真实请求成功且非放宽获得；warm 服务挂死负控仍在 1500 ms 内被杀；R3/契约广告门文本已含该用例并回链证据。
- 逐项对照：
  - **首个真实请求（③）：未满足**。E-01 的 T2 三例是沉降态 + 注入形状（attach 推迟 16 s、fake not-ready binder），不是真实冷启动 attach 分布；契约 §4 worst-case 29.5 s 启动面未设备实测（§5 缺口 7）；隔离进程内一次性装载实测成本 UNVERIFIED（`docs/known-defects.md:2827`）。
  - **warm 负控（④）：部分**。E-01 内既有 watchdog 用例绿证明 warm 服务上挂死程序被 SIGKILL + 新 generation 无 BUSY，但计时口径 ≤6 s（`VisualJsIsolationInstrumentedTest.kt:198`），无"1500 ms 内"断言——与 KD-66 同一弱口径，不满足验收判据的时限形状。
  - **加载态（⑤）：行为层首份设备证据**。T2 两例设备绿（首连超时如实 FAILED 不锁存 + 下次 execute 有界重试、ready 超时不锁存 + 重新 bind，`step01-visualjs-isolation.md:173`）；树内佐证 `VisualJsClientEngine.kt:134/:143/:598`。"如实显示加载态"的 UI/文案面与 R3/准入清单广告门文本同步**未做**（`:2827`；T5 清单 §6 移交 T4/主会话）。
  - **R3 6/6 广告门文本升级：未完成**（仅契约 `:72` 标题括注 + warm-up 子句已落，T5 清单 §5 V-3 实测命中）。
- 结论：**KD-76 不因批次 25 设备轨关闭；JS 仍不广告（本批纪律）**。E-01 是 ⑤ 的首份（部分）设备证据，记录员/主会话可引用，但不得读成 ③④ 达成。

### 9.4 记录回链状态（记录员待办，如实）

- 批次 25 记录 `docs/research/2026-10-27-batch-25-record.md` **已生成（2026-10-27，记录员）**：§4 按 §3 表的 `E-ID + step:line` 映射回链并勾掉 §7 第三项；§7 第一项"KD-66/76 Progress 回填"也已由记录员完成（`known-defects.md` KD-66/KD-76 各新增"批次 25 设备轨 Progress"段，引本目录，均保持 open）。
- `docs/known-defects.md` 的 KD-66/KD-76 Progress 回填**已由记录员完成（2026-10-27）**：两 KD 各新增"Progress（2026-10-27 批次 25 设备轨；记录员回填）"段，按 9.2/9.3 结论转录（KD-66：hard+500 ms 用例仍未写 + T1 交错用例 2/2 设备绿，关闭门不变保持 open；KD-76：T2 三例绿为 ⑤ 首份部分证据、③④ 与 R3 文本同步未达，保持 open）；KD-69/70 已 closed（批次 24 裁定），本批设备证据不改变其裁定，未回填。

### 9.5 体量终测（本节追加后）

```bash
E=docs/research/evidence/2026-10-27-batch-25
find "$E" -type f -name '*.md' -printf '%s\n' | awk '{s+=$1} END {print s " B"}'
find "$E" -type f -name '*.md' | wc -l
du -sk "$E"
```

- 终测值：**7 个 `.md` / 63,372 B 内容 / 77 KiB 磁盘**（单文件 ≤64 KiB 且 ≤400 行、单批 ≤16 个且 ≤512 KiB 均未超；本值与写入后实测自洽，`du -sk` 含 `.mimosa/` 扫描状态目录）。
