# 批次 35 设备判据（T1 前置：新增用例 + R-04 K1–K6 + 15 套件不回退 + 重建重装/源快照门）

> **性质**：本轨只做**判据与命令清单**；设备执行归设备轨。本轨**未运行 Gradle、未 adb install、
> 未执行任何 `am instrument`、未做任何 git 写操作**。文中所有 `OK (N tests)` 都是**通过判据**，
> 不是本轮结果。本轮实跑的命令只有只读 `python`（§四.2 的 S=71 静态比对，`RESULT=PASS`/`EXIT=0`）、
> `grep -c '@Test'` 计数循环、`git status --porcelain`、`sed -n`/`grep` 源码摘录与 `ls` 工具链核对。
>
> 计划来源：`.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md` §1 任务 1/3/13/14/15、§2、§3。
> 上游判据与设计（本文件逐条对齐，不重定义）：
> - K1–K6 原文：`docs/research/2026-10-29-snapshot-identity-wal-and-r8.md:565-577`；可执行命令原型 `:510-563`。
> - 对账负向用例与谓词收紧设计：`docs/research/2026-10-30-reconcile-and-unattributed.md:69-83`（§1.4）。
> - P-11/P-12/M-04/Q-01 落点设计：同文件 `:87-114`（§2）、`:118-137`（§3）、`:140-184`（§4）。
> - 清单判据行：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:134`（P-11）、`:135`（P-12）、
>   `:158`（M-04）、`:209`（Q-01）、`:301`（"有前缀≠已消费"警示）。
> - 命令模板与归因规则继承：`docs/research/2026-10-29-batch-34-device-criteria.md`（§2/§3.2/§4.4/§5）。
> - 批次 35 基线：`docs/research/2026-10-30-batch-35-baseline.md`（修复环六落点、R-04 配置在树）。

---

## 0. 结论速览（执行前口径）

| 项 | 目标 | 现树事实（本轨实读） | 设备结果（待设备轨） |
|---|---|---|---|
| 本地 attempt 真库 | `OK (1 test)`，语义底线见 §1.1 | 文件在树但 **untracked**（`git status --porcelain` 实测 `??`）；1 个 `@Test`（`grep -c` = 1） | 待跑 |
| 对账 JS 区分 | 现树 3 例不回退；若谓词收紧+负向 A/B 落地则 `OK (5 tests)` | `FigureGenerationReconcilerInstrumentedTest.kt` 已提交、3 个 `@Test`；两个 JS 正向例 `:229`/`:289` 在树；收紧未落 | 待跑 |
| P-11 设备面 | 隔离套件新增 wire 往返例（**必须在设备**，JVM 无 org.json）；套件 ≥13 全绿 | `VisualJsIsolationInstrumentedTest.kt` 13 例；grep `UNATTRIBUTED` = 0 命中（缺口仍在） | 待跑 |
| P-12③ 设备面 | 隔离套件补"显式重试成功后同 hash 解熔断"直接断言；`:318` 的 `PROGRAM_FUSED` 基线不回退 | 生产 `explicitUserRetry=true` 调用点 = 0（grep 实跑） | 待跑 |
| M-04 / Q-01 | Q-01 纯 JVM；M-04 负向按推荐设计落 JVM；**引擎邻居门**：`QuickJsVisualExecutionEngineInstrumentedTest`（26 例）必须复跑 | 折点 `QuickJsVisualExecutionEngine.kt:381-390` 实读确认；引擎套件 26 例、已提交 | 待跑 |
| R-04 K1–K6 | 按 §二 全部执行；K1 红、K2–K6 绿 | 配置在树（`app/build.gradle.kts:12/:65/:98`）；K1–K6 未执行（批次 34 记录 `:152`）；`tools/` 无门脚本（grep=0） | 待跑 |
| 15 套件不回退 | 计数逐套件 = 34/2/5/9/5/15/4/3/12/12/16/24/16/13/2，全绿 | 本轮 grep 复读逐套件一致（§三.2） | 待跑 |
| 重建重装 + 源快照 | 先于一切设备命令；find 集含 `core/model/src` **且本批必须再含 `core/domain/src`** | 批次 34 判据 `:284-314` 已含 model；本批 M-04/P-11 落点在 core/domain（§四.1） | 待跑 |

**硬门（先于一切设备命令）**：必须先按 §四 重建重装。批次 34 的 15 套件绿轮对应 18:43 构建源，
本批新增/补强的 5 个设备用例（对账 3 + 本地序号 1 写于 20:57–21:06）与 R-04 配置**都不在那份 APK 里**
（`docs/research/2026-10-29-batch-34-record.md:228-231`），不得引用旧绿轮。

---

## 一、批次 35 新增用例的设备判据

### 1.1 本地 attempt 真库（step16-local-ordinal）

- 文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationLocalAttemptOrdinalInstrumentedTest.kt`
  （**untracked**，`git status --porcelain` 实测——实现轨必须先落提交，设备轨构建时该文件必须在树）。
- 1 个 `@Test`：`localAttemptsUseNegativeOrdinalsDisjointFromTheRemoteLedgerSequence`（`:36`）。
- 真库入口：`StudyDatabaseFactory.open`（`:41`）；被测 DAO 半边：`FigureGenerationDao.kt:125-148`
  的 `@Transaction insertLocalAttempt`（`ordinal = (minLocalAttemptOrdinal ?: 0) - 1`，`:133-134` 实读）。
- **语义底线**（设备轨不得改断言；缺任何一条 = 该例判红）：
  1. 同一 operation 两次本地尝试得 **-1、-2**、attempt_id 不同、格式 `figure-attempt:<op>:-1:JS`（`:79-86`）；
  2. 远程先占序号 1 后本地仍从 -1 起；远程 2 仍可写入（`:88-97`）；
  3. 远程序号 1 的重复写入撞唯一索引 ABORT（`:99-115`）；
  4. 重开只读口：4 行、序号恰为 `-2、-1、1、2`、负序行 `method='JS' ∧ reservation_id IS NULL`、
     正序行 `method='IMAGE_MODEL'`（`:118-157`）。
- 通过条件：`am instrument -w -r` 摘要 **`OK (1 tests)`**；方法名在 `-r` 流中；logcat 窗口无
  `FATAL EXCEPTION`/`SQLiteException`/`AssertionError`；dex 门含类名与方法名（§四.6）。
- 运行命令（serial 重新登记后）：

```bash
run step16-local-ordinal com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.FigureGenerationLocalAttemptOrdinalInstrumentedTest
```

### 1.2 对账 JS 区分（step17-reconcile-js）

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationReconcilerInstrumentedTest.kt`
（已提交、干净；3 个 `@Test`，`grep -c` = 3）。

**现树 3 例（不回退底线）**：

| # | 方法 | 行 | 钉住什么 |
|---|---|---:|---|
| 1 | `staleRunningRowsAreTerminalAfterAProcessKillAndAreNeverRedispatched` | `:34` | 僵尸 RUNNING→FAILED + 本进程 RUNNING 不误杀；IMAGE_MODEL 假成功（SUCCEEDED+无产物）→FAILED 带 `RECONCILED_UNCONFIRMED_SUCCESS`；产物列不清除；不自动重派（4/4 行）+ 幂等二跑 0/0 |
| 2 | `succeededJavascriptAttemptWithoutAssetIsNotReconciledAsFalseSuccess` | `:229` | **JS 区分正向**：本地 JS 成功（output NULL、ordinal -1）对账后保持 SUCCEEDED，0/0 |
| 3 | `javascriptSuccessKeepsItsOperationSucceededWhileImageAttemptIsReconciled` | `:289` | **JS 区分混合**：IMAGE_MODEL 假成功被纠、JS 真成功保留、父操作保持 SUCCEEDED |

**增量分支（由主会话是否采纳 `…reconcile-and-unattributed.md:69-83` 的谓词收紧决定）**：

- **未收紧** → 目标 **`OK (3 tests)`**。
- **收紧 + 负向 A/B 落地** → 目标 **`OK (5 tests)`**，新增两例的判据：
  - **负向 A**：夹具插入 `method='JS' ∧ reservation_id=<名义预留值> ∧ SUCCEEDED ∧ output_asset_id IS NULL`
    （**订正（复核轮）**：`reservation_id` 无任何外键约束——实体仅 `operation_id`/`output_asset_id` 两条
    RESTRICT 外键（`FigureGenerationEntities.kt:111-116`/`:117-122`；原引 `:117-121` 实为 output_asset_id
    那条），夹具直接写值即可，**无需**先建预留行——已落树夹具即如此构造（`FigureGenerationReconcilerInstrumentedTest.kt:397`
    直插 `reservationId="reservation-js-x"`，`PRAGMA foreign_keys=ON`（`FigureGenerationAuditFixtures.kt:164`）下落库成立）。
    上游设计 `…reconcile-and-unattributed.md:76` 同句亦误引该外键，待同批订正）
    → 对账后该尝试 FAILED + 诊断含 `RECONCILED_UNCONFIRMED_SUCCESS`、父操作 FAILED；
  - **负向 B**：同一操作里"负向 A 行 + 本地真 JS 行（reservation_id NULL、SUCCEEDED）"→ 本地行保持
    SUCCEEDED、父操作因真本地行保持 SUCCEEDED（确认谓词只认真本地行，不认异常行）。
- 被测 SQL 半边（现值实读）：假成功候选 `FigureGenerationReconcileDao.kt:126-128`
  `status='SUCCEEDED' AND output_asset_id IS NULL AND method <> 'JS'`；操作级确认 `:171-174`
  `confirmed.output_asset_id IS NOT NULL OR confirmed.method = 'JS'`。收紧后分别变为
  `AND NOT (method='JS' AND reservation_id IS NULL)` 与 `OR (confirmed.method='JS' AND confirmed.reservation_id IS NULL)`。
- **规则**：设备目标数 = 构建前 `grep -c '@Test'` 读数；设备报告数 < 源计数 = D（旧包），停该步。
  实现轨若落了负向用例但主会话拒收紧（用例必红），属**实现轨时序错误**，按 §五 A 类归因，不是设备轨放宽理由。

运行命令：

```bash
run step17-reconcile-js com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.FigureGenerationReconcilerInstrumentedTest
```

### 1.3 P-11 设备面：`[UNATTRIBUTED]` 前缀跨 AIDL 保留（隔离套件新增例）

**设备面判定：存在，且必须在设备。** 依据：wire 往返门落不了 JVM——`VisualJsProtocol` 用
`org.json`，而 app 模块 JVM 测试依赖仅 junit（`docs/research/2026-10-30-reconcile-and-unattributed.md:101`，
`grep -rl "org.json" app/src/test/` = 0）；清单 P-11 行的验收类型是"JVM/设备"，其中**跨进程保留**半边只能设备
（真 Binder + 真 PFD + 真 org.json）。清单 `:301` 警示原话："把'[UNATTRIBUTED] 有前缀'当'宿主已消费'"——
无设备 wire 断言时 P-11 的 wire 半边 **UNVERIFIED**，不得写"P-11 已核销"。

判据（新增例落入 `VisualJsIsolationInstrumentedTest`，app 测试 APK）：

1. 注入"丢失哨兵 + 低堆"程序（制造 `UNATTRIBUTED` 分类的形态），经**完整客户端链路**（bind → AIDL →
   isolated worker → 回传）后，宿主侧断言收到 `Rejected`：
   `reason == INVALID_PROGRAM && diagnostics.startsWith("[UNATTRIBUTED]")`；
2. **双通道一致**：AIDL `diagnostics` String 参数与 payload（PFD）信封内的 summary 逐字一致
   （服务侧同一 `summary` 双写，`VisualJsService.kt:605-612` 口径）；
3. 前缀字面量只认 `[UNATTRIBUTED]`（`VisualJsFailureClassification.kt:141` 实读；
   `QuickJsVisualExecutionEngine.kt:130-131`：对外折 `INVALID_PROGRAM`、诊断以该前缀开头）。
   现状缺口：隔离套件 grep `UNATTRIBUTED` = **0 命中**（本轨实跑）——该例落地前缺口成立。
4. 引擎级同形断言已在树不回退：`QuickJsVisualExecutionEngineInstrumentedTest.kt:565`
   `nullThrownAtLowMemoryIsUnattributedInvalidProgram`（`:580` 断言前缀）、`:512`
   `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage`、`:585` `forgedOutOfMemoryMessageIsInvalidProgram`。
5. AIDL 面本身仍按硬约束**只以 Gradle 任务验证**（`aidl = true`，`app/build.gradle.kts:102-110`）——
   设备用例验证的是运行面，不替代也不违反该约束。

计数口径：套件基线 **13**；本例落地后目标 = 构建前 grep 计数（预计 **14**）。设备报告数 < 源计数 = D。

### 1.4 P-12③ 设备面：解熔断直接断言（隔离套件新增例）

**设备面判定：存在。** 清单 P-12 行 `:135`：③"解除路径补直接断言"；设计
`…reconcile-and-unattributed.md:135`：客户端熔断真实现依赖 Android 运行面，**专项落设备**。

- **基线不回退**：`VisualJsIsolationInstrumentedTest.kt:318` 已断言同 programHash 自动重发被
  `PROGRAM_FUSED` 拒（G-7① 设备绿）——本批任何改动后该断言必须仍绿。
- **新增直接断言**：显式重试（`explicitUserRetry=true`）成功后，同 programHash 再次执行得
  `Rendered`（熔断解除，`VisualJsClientEngine.kt:294-297` 的 `fusedDigests.remove` 路径）。
  生产调用点现状：`explicitUserRetry=true` 生产调用点 = **0**（grep 实跑，命中全为测试/wire 解码）——
  设备例经由隔离套件的测试夹具构造 true 请求，属测试构造，不是生产接线；生产调用点半边（P-12②）
  是协调器/模型字段改动，其判据在 JVM（预算承继：同 operationId → 1 操作行 / attempt −1、−2 /
  账本零触碰 / 0 远程派发，`…reconcile-and-unattributed.md:132-134`），**无设备面**。

计数口径：与 §1.3 合并计入隔离套件基线 13 → 落地后 14/15。

### 1.5 M-04 / Q-01 设备面判定

| 项 | 设备面 | 判据 |
|---|---|---|
| **Q-01**（JS 分支补 `requiresPixelFidelity=true` 拒绝） | **无**。`FigureGenerationPolicy` 是纯校验门（core/model），清单 `:209` 验收类型就是"JVM" | JVM 负向（`FigureGenerationPolicyTest`）+ 对照例（`requiresPixelFidelity=false` 照常 Accepted）；设备轨无动作 |
| **M-04**（度量/后端失败与 `INVALID_PROGRAM` 分离） | **无新增设备例**（推荐案 A 的负向落 JVM：恒抛 measurer → `MEASUREMENT_UNAVAILABLE` + 协调器 UNAVAILABLE + 审计带标记；对照例：度量条目超预算仍 `INVALID_PROGRAM`，`…reconcile-and-unattributed.md:165`）。清单 `:158` 允许"JVM/设备"，JVM 半边即满足 | 但触发**引擎邻居门**（下条） |
| **引擎邻居门**（M-04/P-11 的强制复跑） | **有**：M-04 折点在 `QuickJsVisualExecutionEngine.kt:381-390`（`measurer.measure` catch → `INVALID_PROGRAM`，本轨实读确认）；前缀常量/枚举可能牵动 `VisualJsFailureClassification` 与 core/domain 端口枚举 | `QuickJsVisualExecutionEngineInstrumentedTest`（**26 例**，core/data androidTest，已提交 a7b28a47）必须在本批 APK 上复跑：`OK (26 tests)`（或构建前计数）。理由：修复环后必须复跑强门；该套件是引擎分类/熔断/冻结面的最强设备门，且不在批次 34 的 15 套件内（其最近整轮设备证据早于批次 34 的 model 模块改动）。**特别核对**：现树该套件无"度量失败→INVALID_PROGRAM"断言（grep 实证），M-04 落地后若它新增该面用例，期望值必须写成新枚举值，不得把旧 `INVALID_PROGRAM` 期望留着改产品 |

---

## 二、R-04 的 K1–K6 判据与可执行命令

配置在树（实读）：`app/build.gradle.kts:12`（`smbk.testBuildType` 属性门，默认 debug）、`:65`
（`testBuildType = r04TestBuildType`）、`:87-98`（release 块 `isMinifyEnabled=true` +
`testProguardFiles("r04-test-keep.pro")`）、`:124-158`（release 签名由 `RELEASE_*` 环境变量求值期注入 +
`validateReleaseSigning` 缺变量即失败）。`app/r04-test-keep.pro` 36 行，`-keep` 只保测试类构造器与
`@Test/@Before/@After/@BeforeClass/@AfterClass` 方法 + androidx.test/junit 运行时（`:18-36`）。
**幽灵门现状**：`:13-14` 注释声称的"R-04 gate greps the proguard rule files"在 `tools/` 无实现
（`grep -rn "dontobfuscate" tools/` = 0，本轨实跑）——计划任务 15 的二选一（落 `tools/ci` 脚本或改注释）
由实现轨处理；在脚本落地前，K4 的 grep 以本节命令为准。

**轨道纪律**：R-04 是独立设备状态舞步——它会把 release 签名主包装上设备。**要么放在 debug 15 套件轨之后，
要么结束后立即恢复 debug 态**（§二.7）；任何 debug 轨证据不得在设备处于 release 态时采集。R-04 构建窗口
同样执行 §四 的源快照门（release 构建前后各取一次）。

### 2.1 步 0：一次性本地 keystore + 签名环境（口令只走环境变量，不落仓/文档/测试）

```bash
: "${R04_STOREPASS:?先 export R04_STOREPASS=<本机一次性口令，随机生成，勿提交>}"
keytool -genkeypair -v -keystore build/r04-local.jks -alias r04local \
  -keyalg RSA -keysize 2048 -validity 3650 \
  -storepass "$R04_STOREPASS" -keypass "$R04_STOREPASS" \
  -dname "CN=R04 Local, OU=Local, O=Local, L=Local, S=Local, C=CN"
export RELEASE_STORE_FILE=build/r04-local.jks
export RELEASE_STORE_PASSWORD="$R04_STOREPASS"
export RELEASE_KEY_ALIAS=r04local
export RELEASE_KEY_PASSWORD="$R04_STOREPASS"
```

`build/` 已被 gitignore（批次 34 判据 `:514` 实测）；这是**一次性本地 key**，生产签名维持 UNVERIFIED 不变。

### 2.2 步 1（K1）：红基线确实红

```bash
./gradlew :app:assembleStrictOfflineRelease :app:assembleStrictOfflineDebugAndroidTest --console=plain
echo "BUILD_EXIT=$?"                        # 必须 0（release 构建本身能成；validateReleaseSigning 过 = env 就位）
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe
S=emulator-5562                              # 执行前按 §四.7 重新登记；5566/5584 属他线，禁碰
$A -s "$S" install -r -t app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
$A -s "$S" install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch35/r04-step1-red.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch35/r04-step1-red-logcat.txt
```

两个方法名实读于 `VisualJsIsolationInstrumentedTest.kt:116` 与 `:191`。

**K1 通过**： instrument 失败，且红因是**签名门**（`INSTRUMENTATION_STATUS: Error=Permission Denial … does not
have a signature matching the target`，批次 27 E-03 §3.1 形态）或 **R8 改名崩溃**（`Process crashed` +
`NoClassDefFoundError: Lkotlin/jvm/internal/Intrinsics;` 形态，E-03 §3.2）。
**K1 不通过**：instrument 绿了（配置把日常轨改道了，回查属性门），或红因是缺环境变量（`validateReleaseSigning`
的 GradleException——那是基础设施问题，先修步 0，不算红基线）。

### 2.3 步 2（K2）：属性门打开后的 release 测试轨构建

```bash
./gradlew :app:assembleStrictOfflineReleaseAndroidTest -Psmbk.testBuildType=release --console=plain
echo "BUILD_EXIT=$?"
ls -l app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk \
      app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk
```

**K2 通过**：`BUILD_EXIT=0` 且两个产物都在（androidTest 产物路径含 `androidTest/strictOffline/release/`，
文件名 `app-strictOffline-release-androidTest.apk`）。失败先查 `testProguardFiles` 是否被属性门包住
（`app/build.gradle.kts:95-98`）。

### 2.4 步 3（K3/K4/K5）：产物级判据（不需要设备）

```bash
# K3 证书：两份 APK 的签名证书 SHA-256 必须相同（androidTest 继承 tested variant 签名的实测判定点）
C:/Android/Sdk/build-tools/37.0.0/apksigner.bat verify --print-certs \
  app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk   | tee build/device-track-batch35/r04-k3-main.txt
C:/Android/Sdk/build-tools/37.0.0/apksigner.bat verify --print-certs \
  app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk | tee build/device-track-batch35/r04-k3-test.txt

# K4a 关闭混淆指令必须为 0（两个文件都要）
grep -cE "dontobfuscate|dontshrink|dontoptimize" app/proguard-rules.pro app/r04-test-keep.pro   # 期望 0 0
# K4b release mapping 必须存在且含真实改名行
find app/build/outputs/mapping -name "mapping.txt" -print      # 应含 strictOfflineRelease/mapping.txt
MAPPING=$(find app/build/outputs/mapping -path "*strictOfflineRelease*" -name "mapping.txt" | head -1)
test -n "$MAPPING" && test -s "$MAPPING" && echo "MAPPING_OK=$MAPPING"
grep -c " -> " "$MAPPING"                                      # > 0（真实改名行存在）

# K5a libquickjs.so 在 release 主包（双 ABI）
unzip -l app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk | grep libquickjs.so
# K5b 测试类名在 androidTest dex 中未被改名
C:/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer.bat dex packages \
  app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk \
  | grep -c "com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest"   # >= 1
# K5b 等价兜底（无 apkanalyzer 时）：
unzip -p app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk 'classes*.dex' \
  | grep -a -c "VisualJsIsolationInstrumentedTest"                                       # >= 1
```

- **K3 通过**：两份输出中证书 SHA-256 逐字相同。不同 ⇒ 需显式给 androidTest 指定 `signingConfig`
  （研究 §7 U4 预案）——**处置归主会话裁定**；不得用 apksigner 重签测试包凑同（批次 27 的重签是
  旧路线的产物操作，本路线的 K3 考的就是 AGP 原生继承，重签即改判据）。
- **K4 通过**：K4a 两文件各 0；K4b mapping 非空且改名行 > 0。若 `tools/ci` 门脚本已落（计划任务 15
  的二选一），执行它并以脚本 exit=0 为准，本 grep 作为可复跑等价命令保留。
- **K5 通过**：K5a 非 空（期望 arm64-v8a + x86_64 两条）；K5b ≥ 1（测试类按原名存活）。
  K5b 查不到 ⇒ keep 规则不足——扩规则属实现轨改动，扩后**从步 2 重跑**。

### 2.5 步 4（K6）：设备两例往返 + NoClassDefFoundError 计数

```bash
$A -s "$S" install -r -t app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
$A -s "$S" install -r -t app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater,com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch35/r04-k6.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch35/r04-k6-logcat.txt
grep -cE "NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError" build/device-track-batch35/r04-k6-logcat.txt   # 期望 0
```

**K6 通过**：摘要 **`OK (2 tests)`**（两方法名都在 `-r` 流中，不是单方法子集跑两次）；三类崩溃签名计数 **0**。
**K6 失败处置**：仍是 `NoClassDefFoundError` ⇒ 本仓组合下该路线不成立，**按"阻塞"回报**，不得改写判据、
不得换 internal 变体、不得引入 `-dontobfuscate`（判据不放宽，`…wal-and-r8.md:576-577`）。

### 2.6 步 5：回归（证明没把日常轨改道）

```bash
./gradlew :app:assembleStrictOfflineDebugAndroidTest --console=plain     # 不带属性
ls -l app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
```

通过：不带属性时构建成功且产物回到 `…/debug/` 路径（`testBuildType` 回落 debug）。
`data/database` 两模块测试 APK 不受属性影响（属性门只在 app 模块，`app/build.gradle.kts:65`）。

### 2.7 步 6：恢复 debug 态（R-04 轨收尾硬要求）

```bash
$A -s "$S" uninstall com.tingyun.smartmistakebook.offline
$A -s "$S" uninstall com.tingyun.smartmistakebook.offline.test
# 然后按 §四.5 重建重装 debug 四包；未恢复前不得采集任何 debug 轨证据
```

### 2.8 K1–K6 判据总表（转引 `…wal-and-r8.md:565-577`，不得放宽）

| # | 判据 | 期望 | 失败归因 |
|---|---|---|---|
| K1 | 步 1 红基线确实红，红因是 R8 改名/签名门 | 红 | 红因是缺签名环境变量 → 环境问题，先修步 0 |
| K2 | 步 2 `BUILD SUCCESSFUL` 且两个 release 产物都在 | 绿 | 先查 `testProguardFiles` 是否被属性门包住 |
| K3 | 两份 APK 证书 SHA-256 相同 | 绿 | 不同 ⇒ androidTest 显式 `signingConfig`（主会话裁定），禁重签凑同 |
| K4 | `dontobfuscate`（含 shrink/optimize）两文件 0，release mapping 有真实改名 | 绿 | 有关闭指令 ⇒ 实验无效，不得判 R-04 |
| K5 | `libquickjs.so` 在包；测试类名在 androidTest dex 未被改名 | 绿 | 类名查不到 ⇒ keep 规则不足，扩后从步 2 重跑 |
| K6 | 两例 `OK (2 tests)`，logcat 三类签名 0 | 绿 | 仍 `NoClassDefFoundError` ⇒ 本仓组合下路线不成立，按"阻塞"回报 |

证据头最低字段：HEAD、工作树状态、源快照 before/after diff（§四.3，release 构建窗口）、两 APK sha256、
两份证书 SHA-256（K3）、mapping 行数（K4）、dex 类名计数（K5）、`OK (2 tests)` 摘要与两方法名（K6）、
签名计数、恢复 debug 态的 uninstall/install 记录。**生产签名 UNVERIFIED、arm64 缺口维持登记。**

---

## 三、15 套件不回退清单

### 3.1 套件与目标（计数为本轮 `grep -c '@Test'` 实读，与批次 34 绿轮逐套件一致）

| step | 套件 | APK | 类 | 现树 | 不回退目标 |
|---|---|---|---|---:|---|
| 01 | 备份 | data | `…core.data.backup.BackupRestoreInstrumentedTest` | 34 | `OK (34 tests)` |
| 02 | rowCounts | data | `…backup.BackupRowCountsCoverageInstrumentedTest` | 2 | `OK (2 tests)` |
| 03 | 结构门 | database | `…TutorVisualStructureGatesInstrumentedTest` | 5 | `OK (5 tests)` |
| 04 | 裁剪器 | data | `…backup.BackupSnapshotTrimmerInstrumentedTest` | 9 | `OK (9 tests)` |
| 05 | C4 视觉四表 | data | `…backup.BackupVisualReferenceInstrumentedTest` | 5 | `OK (5 tests)` |
| 06 | 迁移矩阵 1→71 | database | `…FullMigrationMatrixInstrumentedTest` | 15 | `OK (15 tests)` |
| 07 | 69→70 | database | `…TutorVisualSnapshotMigration_69_70InstrumentedTest` | 4 | `OK (4 tests)` |
| 08 | 70→71 | database | `…TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest` | 3 | `OK (3 tests)` |
| 09 | 提交 | database | `…TutorVisualCommitInstrumentedTest` | 12 | `OK (12 tests)` |
| 10 | 仓库 | data | `…visual.TutorVisualCommitRepositoryInstrumentedTest` | 12 | `OK (12 tests)` |
| 11 | 一致门（原型） | data | `…visual.VisualDrawOpsParityInstrumentedTest` | 16 | `OK (16 tests)` |
| 12 | painter | data | `…visual.VisualDrawOpsPainterInstrumentedTest` | 24 | `OK (24 tests)` |
| 13 | PDF | data | `…visual.VisualDrawOpsPdfPainterInstrumentedTest` | 16 | `OK (16 tests)` |
| 14 | 隔离 | app | `…visualjs.VisualJsIsolationInstrumentedTest` | 13 | ≥13 且 = 构建前计数（P-11/P-12③ 落地则 14/15） |
| 15 | flags | app | `…visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest` | 2 | `OK (2 tests)` |

**批次 35 增量套件（不占 15 套件名额，目标 = 构建前源计数）**：

| step | 套件 | APK | 现树 | 目标 |
|---|---|---|---:|---|
| 16 | 本地 attempt 负序号 | data | 1 | `OK (1 tests)`（§1.1） |
| 17 | 对账 JS 区分 | data | 3 | `OK (3 tests)`；收紧落地则 `OK (5 tests)`（§1.2） |
| 18 | 引擎邻居门 | data | 26 | `OK (26 tests)`（§1.5；M-04/P-11 触引擎侧代码时**强制**） |

**不回退判定**：设备 `OK (N)` == 构建前源计数 == 上表现树列；任何一例红、或设备数 < 源计数 = 回退（D 先行归因），
**停该步重建重装**，不得为对齐删改测试。

### 3.2 构建前计数复读（写进证据头；本轨实跑输出）

```bash
for f in <§3.1 全部 18 个文件路径>; do printf "%3d %s\n" "$(grep -c '@Test' "$f")" "$f"; done
```

本轮实读：34/2/5/9/5/15/4/3/12/12/16/24/16/13/2 + 增量 1/3/26，与 §3.1 表一致。
设备轨**必须重跑一次**（源可能在本轮之后继续变），以构建前复读为准。

### 3.3 串行执行（模板同批次 34 判据 §4.4，目录换 batch35）

```bash
D=build/device-track-batch35; mkdir -p "$D"
run() {  # run <step> <pkg> <class>
  step="$1"; pkg="$2"; cls="$3"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    "$pkg/androidx.test.runner.AndroidJUnitRunner" | tee "$D/$step.log"
  $A -s "$S" logcat -v threadtime -d > "$D/$step-logcat.txt"
}
# step01–step15 与批次 34 判据 §4.4 逐字相同（类名/APK 见 §3.1），此处不重复；
# 追加：
run step16-local-ordinal com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.FigureGenerationLocalAttemptOrdinalInstrumentedTest
run step17-reconcile-js com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.FigureGenerationReconcilerInstrumentedTest
run step18-engine-neighbor com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest
```

逐步汇总与签名扫描（15+3 份全部）：

```bash
for f in "$D"/step*.log; do echo "== $(basename "$f")"; grep -E "OK \(|Tests run:|Failures:|Error|Skipped|Time:" "$f" | tail -4; done
for f in "$D"/*-logcat.txt; do
  printf "%s: " "$(basename "$f")"
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" "$f"
done   # 期望全 0
```

---

## 四、重建重装硬门与源快照门（含 core/model/src；本批扩 core/domain/src）

### 4.1 find 集合（批次 35 口径）

批次 34 教训：864 文件的快照漏了 `core/model/src`（`…batch-34-record.md:222-226`），判据已在
`…batch-34-device-criteria.md:284-314` 扩入。**批次 35 再扩**：M-04 枚举值落 core/domain 端口
（`VisualExecutionEngine.kt:285` 处，`…reconcile-and-unattributed.md:162`）、P-11 前缀常量晋升到
core/domain 端口层（同文件 `:106`）——两处都在计划任务 13/14 的落点内。故：

```bash
D=build/device-track-batch35; mkdir -p "$D"
find app/src core/model/src core/data/src core/database/src core/domain/src -type f -print0 | sort -z \
  | xargs -0 sha256sum > "$D/src-sha-before.txt"
```

- 构建后重取 `src-sha-after.txt`，`diff -u` 非空 = **SOURCE CHANGED DURING BUILD**，前一轮日志作废、重建重装。
- 开工时若实现轨还改了其他模块的 `src`（以 `git status --porcelain` 核对），把该模块 `src` **一并加入** find 集。
- core/domain 开工即脏（他线在飞）——快照记录的是**哈希**，不要求干净；证据头注明"快照含他线在飞内容"
  即可。门的目的是**构建窗口内源稳定**，不是工作树洁净。

### 4.2 构建前一致性门（先于构建；静态半边本轮已实跑）

```bash
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt | head -1)
P=$(sed -n 's/.*const val SCHEMA_VERSION: Int = \([0-9][0-9]*\).*/\1/p' \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt | head -1)
echo "V=$V P=$P"                       # 必须 71 / 71（DB schema 保持 71，计划 §2）
test -f core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json || { echo "STOP"; exit 1; }
```

静态比对脚本（S=71，与批次 34 判据 §1.4 逐字相同）**本轨实跑输出**：

```text
SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90
ONLY_PLAN=[] ONLY_JSON=[] DUP=[] VIEWS_MATCH=True
PLAN_VERSION=71 JSON_VERSION=71
PLAN_HASH=714c0ecb85ee5edf74f0013740148138 JSON_HASH=714c0ecb85ee5edf74f0013740148138
RESULT=PASS
EXIT=0
```

设备轨在构建前**重跑一次**（源可能再变）；`RESULT=FAIL`/`EXIT≠0` 不得进入设备步骤。

### 4.3 构建（只允许 assemble*，禁 connectedAndroidTest；锁文件不提交、tmp/ 不得 git add）

```bash
./gradlew --console=plain \
  :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest \
  :core:database:assembleDebugAndroidTest
echo "BUILD_EXIT=$?"          # 必须 0
# 随后按 §四.1 取 src-sha-after.txt 并 diff —— 非空即 SOURCE CHANGED，重建。
```

### 4.4 四包 sha256 与安装字节核对（命令同批次 34 判据 §2.5）

```bash
sha256sum app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk \
          app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk \
          core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
          core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
for apk in <上列四包>; do $A -s "$S" install -r -t "$apk"; done        # 4× Success
for p in com.tingyun.smartmistakebook.offline com.tingyun.smartmistakebook.offline.test \
         com.tingyun.smartmistakebook.core.data.test com.tingyun.smartmistakebook.core.database.test; do
  path=$($A -s "$S" shell pm path "$p" | tr -d '\r' | sed 's/^package://')
  $A -s "$S" shell sha256sum "$path"                                    # 与宿主逐字节一致
done
```

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` → 先 `adb uninstall <精确包名>` 再装，舞步记入证据。
只有安装成功且 sha 对上，才允许跑 §三。

### 4.5 schema asset 门（rowCounts/结构门用例的权威源，同批次 34 §4.2）

```bash
APK=core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
unzip -l "$APK" | grep -q "assets/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json" \
  || { echo "MISSING 71.json"; exit 1; }
unzip -p "$APK" "assets/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json" | sha256sum   # 进证据头
```

### 4.6 dex 符号门（防旧包跑新门；批次 34 §4.3 基础上**追加**）

`need()` 函数定义同批次 34 判据 `:456-464`。在原有三包符号清单（批次 34 `:469-542`，全部保留不删）之上追加：

```bash
need core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
  FigureGenerationLocalAttemptOrdinalInstrumentedTest \
  localAttemptsUseNegativeOrdinalsDisjointFromTheRemoteLedgerSequence \
  FigureGenerationReconcilerInstrumentedTest \
  staleRunningRowsAreTerminalAfterAProcessKillAndAreNeverRedispatched \
  succeededJavascriptAttemptWithoutAssetIsNotReconciledAsFalseSuccess \
  javascriptSuccessKeepsItsOperationSucceededWhileImageAttemptIsReconciled \
  QuickJsVisualExecutionEngineInstrumentedTest \
  nullThrownAtLowMemoryIsUnattributedInvalidProgram
need app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk \
  VisualJsIsolationInstrumentedTest \
  workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater \
  theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState
```

**通用规则**：构建前 grep 出的每个新增 `@Test` 方法名（对账负向 A/B、P-11 wire 例、P-12③ 解熔断例——
方法名以实现轨落树为准）都必须出现在对应测试包 dex 中；任一 `MISSING` = 旧包/漏装（D），停，重建重装。

### 4.7 步 0：设备再登记（每次会话重新登记，serial 不得沿用记忆）

```bash
$A devices -l                     # 5566/5584 属他线，禁碰；默认 5562
$A -s "$S" shell getprop ro.build.version.sdk       # 期望 34
$A -s "$S" shell getprop ro.product.cpu.abi         # 期望 x86_64
$A -s "$S" shell getconf PAGE_SIZE                  # 期望 4096
$A -s "$S" shell getprop ro.build.fingerprint
```

五值进证据头；换设备则注明不能跨设备比较像素（一致门）。

**顺序（不可颠倒）**：§4.7 登记 → §4.2 一致门 → §4.1 快照 before → §4.3 构建 → §4.1 快照 after diff →
§4.4 安装与 sha → §4.5 asset 门 → §4.6 dex 门 → §三 套件串行 → §二 R-04 轨（独立舞步）→ §二.7 恢复 debug 态。

---

## 五、红例归因（先 A/B/D，最后 C；规则继承批次 34 判据 §5）

绝对禁止（继承批次 34 `:682-689`）：删/弱化断言、`@Ignore`/`Assume` 跳过、`-e class ...#method` 过滤红例、
改小 `OK (N)` 的 N、先改测试再找原因、以旧批次绿轮替代当前树证据。

**批次 35 专用陷阱**：

| 现象 | 先判 | 处置 |
|---|---|---|
| step16 红 `UNIQUE constraint failed`（远程 1 重复写入没 ABORT） | **A**（夹具 attempt_id/序号构造错）→ **C**（`insertLocalAttempt` 序号链吃掉正区间） | 不得放宽 ABORT 断言 |
| step16 红 序号非 -1/-2 | **D**（旧包无新 DAO 逻辑）→ **C**（`MIN(attempt_ordinal)-1` 回归） | dex 门查类名；重建重装 |
| step17 红 JS 成功被改判 FAILED | **D**（旧包：DAO 无 `method <> 'JS'`，批次 34 修复不在包内）→ **C**（对账谓词回归） | 先核设备包 sha 与 dex；不得删该例 |
| step17 负向 A 红没纠（仍 SUCCEEDED） | **A**（实现轨落了用例没落收紧）→ **C**（收紧 SQL 错） | 用例与谓词必须同轮落树；不得把负向例改 skip |
| step17 负向 B 红真本地行被误杀 | **C**（确认谓词没认 `reservation_id IS NULL`） | 不得改用例期望 |
| step14 新 P-11 例红 前缀丢失/不在首位 | **D**（旧包）→ **B**（`boundedDiagnostics` 截断口径——截断从头部保留，前缀丢失即异常）→ **C**（AIDL/服务侧没保留） | 不得把 `startsWith` 断言改成 contains |
| step14 新 P-12③ 例红 仍 `PROGRAM_FUSED` | **A**（夹具没把 `explicitUserRetry=true` 传进 frame）→ **C**（`:294-297` 解除路径回归） | 不得改用例 |
| step18 引擎套件红 新枚举值未映射/显式拒绝 | **D**（wire 两端不同构建）→ **C**（`decodeRejection` 缺映射名） | wire 两端同构建强制相等（`…reconcile-and-unattributed.md:162`）；重建 |
| K1 不红 | **A/D** | 配置改道日常轨或环境缺失；回查属性门与步 0 |
| K6 红 `NoClassDefFoundError` | **D**（本路线不成立的实测形态） | 按"阻塞"回报；不得改判据/换变体/加 `-dontobfuscate` |

归因记录最低字段（同批次 34 `:720-723`）：红例名+行号+失败消息+第一项目帧+四源核对（逐源 `path:line`）+
类别（A/B/C/D）+处置+重建 sha256+绿轮 summary。缺任一 = 归因不成立。

---

## 六、本轨未验证项

1. **一切设备结果 UNVERIFIED**：本轨零 Gradle、零 adb、零 git 写；全部 `OK (N)` 为判据不是结果。
2. `FigureGenerationLocalAttemptOrdinalInstrumentedTest.kt` **untracked、未编译**（`git status` 实测）——
   实现轨提交前不得进设备轨；编译风险点：`BeginLocalFigureGenerationAttemptCommand` 字段签名、
   `StudyDatabaseFactory.open` 可见性（同模块已提交 reconciler 测试在用，风险低）。
3. 计划新增用例（对账负向 A/B、P-11 wire 例、P-12③ 解熔断例）**尚未落树**——方法名/计数以实现轨落树后
   构建前 grep 为准；主会话是否采纳谓词收紧与 M-04 案 A 决定 §1.2/§1.5 的分支。
4. R-04 K1–K6 端到端 UNVERIFIED（批次 34 记录 `:152` 自记）；`testProguardFiles` 在本仓组合下是否真让
   test dex 套用主包 mapping UNVERIFIED（`…wal-and-r8.md:587-589`）；androidTest 签名继承 UNVERIFIED（K3 实测决定）。
5. 隔离套件 `:318` 的 `PROGRAM_FUSED` 断言现值来自批次 27 T5（清单 `:135` 转录），本轨未复跑——设备轨以
   本轮 `-r` 流实测为准。
6. 设备在线状态未重新登记（§4.7 由设备轨执行）；`emulator-5562` 是默认 serial 不是本轮实测绑定。
7. 生产签名、arm64/16KB release 面维持既有缺口（批次 27 E-03 §8），本批判据不覆盖。

---

## 七、来源

- 计划：`.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md:23-26,35-45,47-57`
- K1–K6 原文与命令原型：`docs/research/2026-10-29-snapshot-identity-wal-and-r8.md:510-577`
- 对账设计（收紧 SQL + 负向 A/B）：`docs/research/2026-10-30-reconcile-and-unattributed.md:69-83`
- P-11/P-12/M-04/Q-01 设计：同上 `:87-114`、`:118-137`、`:140-184`
- 清单判据行：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:134,135,158,209,301`
- 命令模板/归因/硬门：`docs/research/2026-10-29-batch-34-device-criteria.md:262-344,364-402,456-542,557-668,679-723`
- 批次 34 设备事实（新用例不在绿轮）：`docs/research/2026-10-29-batch-34-record.md:194-231,236-239`
- 基线（R-04 配置、修复环落点）：`docs/research/2026-10-30-batch-35-baseline.md:49-56,101-108`
- 本轮实读代码：`FigureGenerationLocalAttemptOrdinalInstrumentedTest.kt:36,41,79-157`；
  `FigureGenerationReconcilerInstrumentedTest.kt:34,229,289`；`FigureGenerationDao.kt:125-134`；
  `FigureGenerationReconcileDao.kt:126-128,171-174`；`QuickJsVisualExecutionEngine.kt:130-131,381-390`；
  `VisualJsFailureClassification.kt:141`；`VisualJsIsolationInstrumentedTest.kt:116,191,318`；
  `QuickJsVisualExecutionEngineInstrumentedTest.kt:455,512,565,585`（26 `@Test`）；
  `app/build.gradle.kts:12,65,87-98,102-110,124-158`；`app/r04-test-keep.pro:13-14,18-36`
- 本轮实跑：S=71 静态比对（PASS/EXIT=0）、18 文件 `grep -c '@Test'` 循环、`git status --porcelain`
  （本地 attempt 文件 `??`）、`grep -rn dontobfuscate tools/`（0）、`sed -n` 源码摘录、
  `ls` build-tools/cmdline-tools（37.0.0 与 apkanalyzer.bat 在位）。
