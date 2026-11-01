# 批次 37 设备判据（T1：R-04 K1–K6 重跑 + V-01 app 版 + A-10 修复版 + 15+ 套件不回退 + 重建重装/源快照门）

> **性质**：本轨只做**判据与命令清单**；设备执行归设备轨。本轨**未运行 Gradle、未 adb install、
> 未执行任何 `am instrument`、未做任何 git 写操作、未改任何产品/规则/测试文件**。
> 文中所有 `OK (N tests)` 都是**通过判据**，不是本轮结果。
> 本轨实跑（全部只读）：keep 门脚本、S=71 静态比对、20 个套件 `grep -c '@Test'`、K6 两例方法名行号、
> A-10/V-01 夹具形态摘录、源快照 find 集计数、`git status`/`git rev-parse`（附录 A）。
>
> 计划：`.zcode/plans/plan-2026-10-31-batch-37-v01-isolated-r04-k6.md`（已完整读；本判据对应 §1.1/§1.2/§1.3 与 §2 任务 3）。
> 上游判据与设计（本文件逐条对齐，**不重定义、不放宽**）：
> - K1–K6 原文与命令原型：`docs/research/2026-10-30-batch-35-device-criteria.md` §二（`:155-310`）；
>   命令模板/`need()`/归因继承：`docs/research/2026-10-29-batch-34-device-criteria.md`（§2.5/§4.3/§4.4/§5）。
> - K6 机制证据、三值预检实测与两个合规上设备选项：`docs/research/2026-10-31-r04-k6-verify.md`（下称 R2 verify，
>   §一/§二/§三/§四）；K6 判据与命令（R1 §5.1）：`docs/research/2026-10-30-r04-k6-keep-rules.md:123-138`。
> - 批次 36 执行状态与欠账（K5 半边、K6 无执行、步 5/步 6 未跑、step19/step20 红、预检②不过）：
>   `docs/research/2026-10-30-batch-36-record.md` §4.4/§4.5/§7。
> - 批次 37 现树基线（HEAD 漂移、V-01/A-10 夹具形态、设备 3 台）：`docs/research/2026-10-31-batch-37-baseline.md`。

---

## 0. 结论速览（执行前口径）

| 项 | 目标（判据，不放宽） | 现树事实（本轨 2026-10-31 实读/实跑） | 设备结果（待设备轨） |
|---|---|---|---|
| R-04 K1–K6 | K1 红、K2–K5 绿、K6 = **两例 `OK (2 tests)` + logcat 三类签名 0**；全绿 → 关闭裁决；仍红按阻塞登记 | keep 两规则已入库（`b31b5d02`，三文件 git 干净）；keep 门本轨实跑 OK×3/EXIT=0；K1–K4 绿、K5 半边、**K6 无执行证据**（批次 36 record §4.5）；K6 两方法现树 `:123`/`:245` | 待跑 |
| 三值预检 | ①≥1、②=0、③≥1；② 不过 = 机制①（j$ L8 改名碰撞）产物级成立 → 走 §1.5 两分支 | 对批次 36 产物对实测：①=1 过、②=1 **不过**（结构性不可满足，R2 verify §三）、③=1 过 + dexdump 类定义级加固 | 新 K2 产物上必须重跑 |
| V-01 app 版 | 迁移套件注入**真 `VisualJsClientEngine`（`:visual_js` 隔离进程）**+ `VisualFrameInputs`；4 例全绿 | 套件仍在 core/data（未迁移）、注入进程内 `QuickJsVisualExecutionEngine`（`:667-668`）、对 visualjs import=0、度量器夹具缺陷在树（`:225/:550/:582`）——T2 落码前形态 | 待跑 |
| A-10 修复版 | `seedModelTask` 按命令前置落库 **QUEUED**；3 例全绿；"耗尽→终答位保留"子门由第 3 例证明 | 夹具仍 `createModelTask`（初态 WAITING_FOR_MODEL，`ModelTaskTransactionDao.kt:204`）；守卫 `ModelTaskDatabasePort.kt:52-54`；合法迁移 `WAITING_FOR_MODEL→QUEUED`（`ModelTasks.kt:43`）——T3 落码前形态 | 待跑 |
| 15+ 套件不回退 | 20 步全绿；每步 `OK (N)` == 构建前 grep 计数 | 本轨 2026-10-31 计数 = 34/2/5/9/5/15/4/3/12/12/16/24/16/**15**/2 + 1/6/27 + V01 4 + A10 3（§三.1） | 待跑 |
| 重建重装 + 源快照 | 先于一切设备命令；find 集**含 `core/model/src`**（计划底线五模块） | 五模块集现树 1173 文件；批次 36 快照实际为全集 1548（§4.1 登记） | 待跑 |

**硬门（先于一切设备命令）**：§四 重建重装 + 源快照门。批次 36 的 18 套件绿轮与 K1–K4 产物绑定的是
批次 36 窗口源（1548 文件快照）；**本批 T2/T3/T4 落码不在那批 APK 里**，不得引用旧绿轮替代本批设备证据
（"新增用例必须重跑、修复环后复跑强门"，计划 §3）。

---

## 一、R-04 K1–K6 重跑判据与命令（K6 新期望 = 两例绿 + 三类签名 0）

### 1.1 判据总表（批次 35 §2.8 原文转引，`batch-35-device-criteria.md:297-306`；逐字不放宽）

| # | 判据 | 期望 | 失败归因（原文） |
|---|---|---|---|
| K1 | 步 1 红基线确实红，红因是 R8 改名/签名门 | 红 | 红因是缺签名环境变量 → 环境问题，先修步 0 |
| K2 | 步 2 `BUILD SUCCESSFUL` 且两个 release 产物都在 | 绿 | 先查 `testProguardFiles` 是否被属性门包住 |
| K3 | 两份 APK 证书 SHA-256 相同 | 绿 | 不同 ⇒ androidTest 显式 `signingConfig`（主会话裁定），禁重签凑同 |
| K4 | `dontobfuscate`（含 shrink/optimize）两文件 0，release mapping 有真实改名 | 绿 | 有关闭指令 ⇒ 实验无效，不得判 R-04 |
| K5 | `libquickjs.so` 在包；测试类名在 androidTest dex 未被改名 | 绿 | 类名查不到 ⇒ keep 规则不足，扩后从步 2 重跑 |
| K6 | **两例 `OK (2 tests)`，logcat 三类签名 0** | 绿 | 仍 `NoClassDefFoundError` ⇒ 本仓组合下路线不成立，按"阻塞"回报 |

- **K6 通过条件（批次 35 §2.5 原文）**：摘要 **`OK (2 tests)`**（两方法名都在 `-r` 流中，不是单方法子集跑两次）；
  三类崩溃签名（`NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError`）计数 **0**。
- **K6 失败处置（原文）**：不得改写判据、不得换 internal 变体、不得引入 `-dontobfuscate`（判据不放宽）。
- **判据口径登记（沿批次 36 record §3.2/§7.6）**：K4 的权威口径 = 注释感知 python 门
  `python tools/ci/check_r04_keep_rules.py`（本轨 2026-10-31 实跑：OK/OK/OK、`KEEP_GATE_EXIT=0`）；
  批次 35 K4a 字面 grep 在 HEAD 计 **1/0**（`app/r04-test-keep.pro:17` 头注释含 `-dontobfuscate` 字样）——
  登记口径差，不为凑字面 grep 改历史注释；K4b（mapping 非空 + 改名行 > 0）照旧。

### 1.2 本批相对批次 35/36 的变化点（仅四处，其余逐字沿批次 35 §2.1–2.7）

1. 落盘目录换 `build/device-track-batch37/`。
2. K5 **dex 计数半边必须补齐**（批次 36 欠账）：`apkanalyzer dex packages … | grep -c "…VisualJsIsolationInstrumentedTest"` ≥1
   （批次 35 实测 275；等价兜底 `unzip -p … | grep -a -c "VisualJsIsolationInstrumentedTest"` ≥1，R2 verify 对批次 36 产物实测 35——
   两口径单位不同，各自登记，不得互替）。
3. **三值产物预检必须在新 K2 产物上重跑**（§1.5）——批次 36 增补门 + R2 verify 实测的②不过机制裁定。
4. **步 5 回归 + 步 6 恢复 debug 态是本批硬要求**（批次 36 窗口止于 K4，两项未执行——record §4.5 末）。

### 1.3 步 0–步 2（K1/K2）——命令照批次 35 §2.1/§2.2/§2.3 逐字

步 0（一次性本地 keystore + `RELEASE_*` 环境变量；口令只走环境变量，不落仓/文档/测试）、
步 1（K1 红基线：release 主包 + **debug** 测试包装机 → `am instrument` 单方法 → 期望
`Permission Denial … signature matching` 或 R8 改名崩溃红）、步 2（K2：`-Psmbk.testBuildType=release`
构建两 release 产物）——**命令与通过条件逐字同批次 35 §2.1–2.3**（`batch-35-device-criteria.md:171-223`），
仅落盘目录换 `build/device-track-batch37/`。K2 产物核验：

```bash
ls -l app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk \
      app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk
sha256sum <上列两包>    # 新产物身份进证据头（批次 36 窗口值 1c7fe3f7…/03d1d426… 仅作对照，本批必须不同）
```

### 1.4 步 3（K3/K4/K5）——命令照批次 35 §2.4 逐字（目录换 batch37；本批补 K5b）

```bash
# K3 证书（两份输出 SHA-256 逐字相同）
C:/Android/Sdk/build-tools/37.0.0/apksigner.bat verify --print-certs app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk            | tee build/device-track-batch37/r04-k3-main.txt
C:/Android/Sdk/build-tools/37.0.0/apksigner.bat verify --print-certs app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk | tee build/device-track-batch37/r04-k3-test.txt
# K4 权威门（注释感知）+ K4b mapping
python tools/ci/check_r04_keep_rules.py ; echo "KEEP_GATE_EXIT=$?"          # 期望 OK×3、EXIT=0
MAPPING=app/build/outputs/mapping/strictOfflineRelease/mapping.txt
test -s "$MAPPING" && grep -c " -> " "$MAPPING"                              # > 0
# K5a so 双 ABI；K5b 测试类 dex 计数（本批必补）
unzip -l app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk | grep libquickjs.so   # arm64-v8a + x86_64 两条
C:/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer.bat dex packages \
  app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk \
  | grep -c "com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest"   # >= 1（落 build/device-track-batch37/r04-k5b-count.txt）
unzip -p app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk 'classes*.dex' \
  | grep -a -c "VisualJsIsolationInstrumentedTest"                           # >= 1（兜底口径）
```

### 1.5 三值产物预检（批次 36 增补门；**必须对批次 37 新 K2 产物重跑**；② 不过走两分支）

命令（R2 verify §2.2 固定串，`-F`、`$` 禁用 BRE；对两 APK sha 一致的新产物）：

```bash
grep -cF 'DesugarCollections$SynchronizedMap -> j$.util.g' app/build/outputs/mapping/strictOfflineRelease/mapping.txt              # ① 期望 >=1
grep -cF ' -> j$.util.g:' app/build/outputs/mapping/strictOfflineReleaseAndroidTest/mapping.txt                                    # ② 期望 0
mkdir -p build/device-track-batch37/main-dex && unzip -o -q app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk 'classes*.dex' -d build/device-track-batch37/main-dex
grep -a -c -F 'androidx/tracing/Trace' build/device-track-batch37/main-dex/classes.dex                                             # ③ 期望 >=1
# ③ 加强（防"仅字符串命中"假阳性；R2 verify §2.2 同款）：
C:/Android/Sdk/build-tools/37.0.0/dexdump.exe -d build/device-track-batch37/main-dex/classes.dex | grep -B1 -A6 "Class descriptor.*'Landroidx/tracing/Trace;'"
C:/Android/Sdk/build-tools/37.0.0/dexdump.exe -d build/device-track-batch37/main-dex/classes.dex | grep -E "name.*: '(beginSection|endSection|forceEnableAppTracing)'"
```

**分支规则（R2 verify §四.3 原文口径，归主会话/设备轨裁定，本判据不代裁）**：

- **分支 P1（②=0）**：三值全过 → 直接按 §1.6 上设备。
- **分支 P2（②≠0，预期形态）**：机制①（j$ L8 改名碰撞）在新产物上产物级成立——R2 verify §三已证其在本仓
  AGP 9.3 + desugar_jdk_libs 2.0.3 架构下**结构性不可满足**（j$ 改名由每变体独立 L8 编译治理，用户
  `testProguardFiles` 规则到不了 L8 面；`-keep class j$.** { *; }` 已进测试 R8 配置 `configuration.txt:75`
  仍 476 个改名）。此时两个合规选项：
  - **选项 A（结构解锁，改构建面）**：slackhq/Keeper 或等价 L8 规则合并——触 `settings.gradle.kts`（热区禁改）
    与新依赖钉版，**未经主会话裁定不得实施**；
  - **选项 B（证据优先，零改动）**：按批次 37 计划 §1.3（更晚的主会话裁定）执行 K1–K6 全链，预检②不过
    登记为背景证据；**该口径切换须主会话显式确认**。若主会话维持批次 36 预检硬门（"任一不满足不上设备"），
    则 K6 设备步阻塞在预检——**两分支结论都按"阻塞"登记，R-04 不虚关**。
- 无论哪条分支：K1–K4/K5 的绿不替代 K6；K6 设备步的判据与命令在两分支下**完全相同**（§1.6）。
- **明确禁止（两选项共同）**：放宽预检②或 K6 判据、引入 `-dontobfuscate/-dontshrink/-dontoptimize`、
  换 internal 变体、删例、以 K1–K4 绿替代 K6。

### 1.6 步 4（K6）：设备两例往返 + 三类签名计数

命令（批次 35 §2.5 逐字，目录换 batch37；serial `$S` 按 §4.7 重新登记）：

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe
$A -s "$S" install -r -t app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
$A -s "$S" install -r -t app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater,com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch37/r04-k6.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch37/r04-k6-logcat.txt
grep -cE "NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError" build/device-track-batch37/r04-k6-logcat.txt   # 期望 0
```

- 两例方法名现树行号：`VisualJsIsolationInstrumentedTest.kt:123`、`:245`（本轨 2026-10-31 grep 实跑核对；
  批次 35 引 `:116/:191` 为该时点行号，以现树为准）。
- **通过**：`OK (2 tests)` 且两方法名都在 `-r` 流 + 三类签名计数 0 → R-04 具备关闭条件
  （连同 K1–K5 全绿 + 步 5/步 6 + 证据头全字段；关闭裁决归台账轨，设备轨只交证据）。
- K6 的运行面 = `-e class` 仅两方法；V-01 迁移套件与 P-12③ 例（`anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse`，
  消费 internal `fuseDigestForTest`）**不在 K6 运行面**。若主会话裁定扩大 release 轨套件单，internal seam 成员
  须逐条补 keep 并给理由（批次 36 record §7.4 移交项口径），否则明确不进 release 轨套件单。

### 1.7 K6 失败归因表（R2 verify §4.2 六行，逐字转录——设备轨逐条对号，禁止跳过归因改判据）

| 现象 | 先判 | 处置（禁止事项） |
|---|---|---|
| instrument **未跑完即 Process crashed**，logcat `NoSuchMethodError … Lj$/util/g; <init>(Ljava/util/Map;)V`（WorkManager 启动栈） | **机制①**（j$ L8 改名碰撞） | 按"阻塞"回报；**不得**改判据、不得加 `-dontobfuscate`、不得换 internal 变体、不得删例 |
| instrument 崩溃路径报 `NoClassDefFoundError/ClassNotFoundException: androidx.tracing.Trace` | **机制②复现**（keep 未真落新产物） | 先对新产物重跑预检③与 dexdump 类定义检查；查 K2 是否真用现树 `.pro`（keep 三 sha + configuration.txt）；仍复现按阻塞 |
| 两例**跑完但断言红**（`Tests run: 2, Failures: N`，无三类签名） | **A/B/C/D 常规归因** | dex 门/包 sha 先行（D）；不得为绿放宽断言；该红不构成 K6 签名红，但 K6 判据仍未满足 |
| instrument 绿（`OK (2 tests)` + 签名 0） | — | **K6 过** |
| 红因是 `Permission Denial … signature matching` | **环境/舞步** | 装错包（debug 测试包 vs release 主包）或签名 env 缺失——按 K1/K2 处置，不算 K6 红 |
| `INSTRUMENTATION_FAILED` 且设备数 < 2 或方法名缺一 | **D（旧包）/命令错误** | 核两包 sha 与 `-e class` 参数；重建重装 |

归因记录最低字段（批次 34 §5 传承）：红例名+行号+失败消息+第一项目帧+四源核对（逐源 `path:line`）+
类别（A/B/C/D/机制①/机制②）+处置+重建 sha256+绿轮 summary。缺任一 = 归因不成立。

### 1.8 步 5 回归 + 步 6 恢复 debug 态（R-04 轨收尾硬要求；批次 36 欠账，本批必补）

```bash
# 步 5：不带属性构建回落 debug（证明没把日常轨改道）
./gradlew :app:assembleStrictOfflineDebugAndroidTest --console=plain
ls -l app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
# 步 6：恢复 debug 态（未恢复前不得采集任何 debug 轨证据）
$A -s "$S" uninstall com.tingyun.smartmistakebook.offline
$A -s "$S" uninstall com.tingyun.smartmistakebook.offline.test
# 然后按 §四.4 重建重装 debug 四包
```

**轨道纪律（批次 35 §二原文）**：R-04 是独立设备状态舞步——**要么放在 debug 20 步轨之后，要么结束后立即
恢复 debug 态**；任何 debug 轨证据不得在设备处于 release 态时采集。

### 1.9 R-04 证据头最低字段（批次 35 §2.8 末段 + 本批增补）

HEAD（`5ee1f90f…` 谱系，基线 §一）、工作树状态、源快照 before/after diff（§4.1，含所用 find 集说明）、
keep 三文件 sha before/after（`r04-test-keep.pro`/`proguard-rules.pro`/`build.gradle.kts`，批次 36 E-04 模式）、
两 APK sha256、两份证书 SHA-256（K3）、keep 门输出（K4）、mapping 行数、K5a 双 ABI 行 + K5b 两口径计数、
**三值预检三值 + dexdump 类定义级验证输出**、`OK (2 tests)` 摘要与两方法名、签名计数、
步 5 回归输出、步 6 uninstall/install 记录。生产签名 UNVERIFIED、arm64 缺口维持登记。

---

## 二、V-01 app 版与 A-10 修复版的设备判据

> T2（V-01 迁移）/T3（A-10 夹具修复）落码前，现树仍是批次 36 形态（本轨实读核对，§0 表）。
> 下列判据以**落码后的构建前 grep 复读**为准；类名/文件名/计数以 T2/T3 落树为准，本判据给期望形态与红线。

### 2.1 V-01 app 版（计划 §1.1：迁移 + 真隔离引擎 + 两处夹具修复）

**落点**：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visual/`（包 `com.tingyun.smartmistakebook.visual`，
套件类名以 T2 落树为准；下称 V01App 套件），跑在 app 测试 APK
（`app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk`，
runner `com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner`）。

**语义底线（判据，缺任何一条 = V-01 不收口，即使全绿）**：

1. **真隔离引擎**：注入 `VisualJsClientEngine`（构造形态 = app androidTest 既有先例
   `VisualJsIsolationInstrumentedTest.kt:120` `VisualJsClientEngine(context)`；服务声明
   `app/src/main/AndroidManifest.xml:69-72` `android:process=":visual_js"` + `android:isolatedProcess="true"`）。
   迁移套件源文件对 `com.tingyun.smartmistakebook.visualjs` 的 import ≥1、
   对 `QuickJsVisualExecutionEngine` 的引用 = 0（grep 核对——进程内引擎只允许出现在 core/data 降级变体）。
2. **`VisualFrameInputs` 注入**（`fun interface`，`core/data/src/main/.../visual/VisualFrameInputs.kt:19`；
   app 依赖 core/data → 可见，基线 §3.1 已核）。
3. **4 例全绿**：`OK (4 tests)`（或构建前 grep 计数）：① 生成腿 + 0 Provider 派发四断言位；
   ② 生产装配负控（不注入引擎 → UNAVAILABLE 且零审计行）；③ 冻结重放（重开库 → restore → 重绘逐位相等）；
   ④ PDF（`writeFile` + `PdfWriteIntegrity` + `PdfRenderer` 回读）。
4. **夹具缺陷①的设备级证据**：③④ 两例绿 = 度量器单次使用缺陷根治（批次 36 红因
   `IllegalStateException: one label entry must be measured`，`V01VisualChainInstrumentedTest.kt:550/:582`
   的 `measured.singleOrNull()` 形态不得以放宽断言（first/last/判空跳过）变绿——归因时核对夹具 diff）。
5. **夹具缺陷②（零派发见证不变量保持）**：用例①的四断言位（审计 `reserved/used=0`、
   attempt `method='JS' ∧ reservation_id IS NULL`、账本零触碰、产物恒 `Program`）在迁移后**不得缩水**
   （grep 核对断言面）。基线 §3.1 登记的现树缺口（见证③仅在用例①活跃，C/D 不 seed 父操作行）的补齐
   落点归 T2 按计划语义落码：若 C/D 补齐见证观测对象，其断言进入判据；若维持仅①活跃，须 KDoc 显式标注
   ——两种形态都不构成判红，但**不得静默丢失既有断言**。
6. **降级变体处置**：core/data 侧留进程内引擎变体、显式标注降级口径（计划 §1.1，不删）。该变体若进
   本批设备套件单，**必须全绿**（计数=构建前 grep）——其两红例的夹具修复必须覆盖它，或它明示不进
   套件单并在证据头登记；**不允许已知红例留在套件单里**（这是 §三 不回退判据的推论，非新裁定）。
   V-01 的收口语义只由 app 版满足，降级变体不替代语义底线第 1 条。

**运行命令**（step 名不得覆盖批次 36 证据文件）：

```bash
run step19-v01-app com.tingyun.smartmistakebook.offline.test \
  com.tingyun.smartmistakebook.visual.V01VisualChainInstrumentedTest     # 类名以 T2 落树为准
```

### 2.2 A-10 修复版（计划 §1.2：QUEUED 夹具 + 终答位子门补证）

**落点**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureDispatchBudgetBoundaryInstrumentedTest.kt`
（git 干净，3 例现树 `:50`/`:84`/`:122`；runner `com.tingyun.smartmistakebook.core.data.test`）。

**判据**：

1. **夹具修复形态**：`seedModelTask`（现树 `:173-196`）落库为 **QUEUED**——合法路径 = `createModelTask`
   （初态 `WAITING_FOR_MODEL`，`ModelTaskTransactionDao.kt:204`）→ `transitionModelTask`
   （`WAITING_FOR_MODEL → QUEUED` 是状态机合法迁移，`ModelTasks.kt:43`）或等价端口命令；
   `ReserveModelTaskRemoteDispatchCommand` 的前置守卫（`expectedStatus == QUEUED` 等，
   `ModelTaskDatabasePort.kt:42-58`）不再被夹具击穿。**禁止绕过端口直改 DB status**（归因时核对夹具 diff）。
2. **3 例全绿**：`OK (3 tests)`（或构建前 grep 计数）：`aSeventhOperationDispatchIsDeniedWithTheRealCount`（`:50`）、
   `switchingMethodDoesNotResetTheOperationDispatchCount`（`:84`）、
   `theChatFinalAnswerSlotSurvivesFigureBudgetExhaustion`（`:122`）。
3. **"耗尽→终答位保留"子门由第 3 例证明，不得以另两例绿替代**（批次 36 record §4.4 原文）。
   第 3 例断言面（本轨实读 `:120-160`，不得放宽）：5 次图像派发 Granted → 第 6 次
   `BUDGET_EXHAUSTED` 且消息含 `上限 5 次` 与 `另为讲题终答保留 1 次` →
   `reserveModelTaskRemoteDispatch`（`expectedStatus=QUEUED`）**applied=true、logicalDispatchCount=6、
   budgetExhausted=false**。

**运行命令**：

```bash
run step20b-a10-budget com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.FigureDispatchBudgetBoundaryInstrumentedTest
```

### 2.3 失败归因（两套件专用，先 A/D 后 C；规则继承批次 34 §5/批次 35 §五）

| 现象 | 先判 | 处置（禁止事项） |
|---|---|---|
| V01App ③④ 红 `one label entry must be measured` | **A**（T2 未真正根治度量器单次使用/又复用）→ **C** | 不得把 `singleOrNull` 放宽为 first/contains |
| V01App 任一例红 `IllegalStateException`/绑定超时（`:visual_js` 进程未起） | **D**（旧包无迁移套件）→ **环境**（isolatedProcess 在模拟器的可用性） | dex 门查新类名；重建重装；不得把真隔离引擎换回进程内引擎 |
| V01App ① 红审计行非 0/账本被触碰 | **A**（夹具 networkRequestsAllowed/注入缝错）→ **C**（协调器 JS 路径回归调 ledger） | 四断言位任一为真即证伪；不得删断言位 |
| V01App grep 出 `QuickJsVisualExecutionEngine` import | **语义底线违反**（T2 未按计划迁移） | 该套件即使全绿也不构成 V-01 收口——按阻塞登记 |
| A-10 红 `Only a queued model task may reserve a remote dispatch` | **A**（T3 夹具仍未落 QUEUED） | 不得放宽守卫、不得改 expectedStatus 传参凑绿 |
| A-10 第 3 例红 `budgetExhausted=true`/`applied=false` | **D**（旧包）→ **C**（`reserveChatFinalAnswer` 保留位回归） | 先核包 sha 与 dex；不得改断言 |
| 两套件设备数 < 源计数 | **D（旧包）** | 重建重装；不得为对齐删改测试 |

---

## 三、15+ 套件不回退清单（本批 20 步）

### 3.1 套件与目标（计数列 = 本轨 2026-10-31 `grep -c '@Test'` 实跑；**设备轨构建前必须重跑，以重跑值为准**）

| step | 套件 | APK | 类 | 2026-10-31 现树 | 不回退目标 |
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
| 14 | 隔离（含 P-12③ 例） | app | `…visualjs.VisualJsIsolationInstrumentedTest` | 15 | `OK (15 tests)` |
| 15 | flags | app | `…visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest` | 2 | `OK (2 tests)` |
| 16 | 本地 attempt 负序号 | data | `…visual.FigureGenerationLocalAttemptOrdinalInstrumentedTest` | 1 | `OK (1 tests)` |
| 17 | 对账 JS 区分 | data | `…visual.FigureGenerationReconcilerInstrumentedTest` | 6 | `OK (6 tests)` |
| 18 | 引擎邻居门 | data | `…visual.QuickJsVisualExecutionEngineInstrumentedTest` | 27 | `OK (27 tests)` |
| 19 | **V-01 app 版（本批新增落点）** | app | `…visual.V01VisualChainInstrumentedTest`（包 `…smartmistakebook.visual`，以 T2 落树为准） | 4（core/data 旧位） | `OK (4 tests)`（= 构建前计数） |
| 20b | **A-10 修复版复跑** | data | `…visual.FigureDispatchBudgetBoundaryInstrumentedTest` | 3 | `OK (3 tests)`（含终答位子门） |

（可选 step21：core/data 降级变体——若 T2 保留且进套件单，目标 = 构建前计数全绿；不进则在证据头登记，§2.1 第 6 条。）

### 3.2 计数规则与不回退判定

- 设备 `OK (N)` == 构建前 `grep -c '@Test'` 复读 == 上表现树列；T2/T3 若再新增用例，期望值随之以
  **构建前复读**为准（新增用例必须重跑，计划 §3）。
- 任何一例红、或设备数 < 源计数 = 回退（**D 先行归因**：dex 门/包 sha 核对旧包）→ **停该步重建重装**，
  不得为对齐删改测试、不得 `@Ignore`/`Assume` 跳过、不得 `-e class ...#method` 过滤红例（批次 34 `:682-689` 绝对禁止清单）。

### 3.3 串行执行（模板同批次 34 §4.4/批次 35 §三.3，目录换 batch37）

```bash
D=build/device-track-batch37; mkdir -p "$D"
run() {  # run <step> <pkg> <class>
  step="$1"; pkg="$2"; cls="$3"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    "$pkg/androidx.test.runner.AndroidJUnitRunner" | tee "$D/$step.log"
  $A -s "$S" logcat -v threadtime -d > "$D/$step-logcat.txt"
}
# step01–step18：类名/APK 与批次 35 判据 §3.1/§3.3 逐字相同（step14 期望已 15、step17 期望 6、step18 期望 27）；
# 追加 step19-v01-app（§2.1）与 step20b-a10-budget（§2.2）。
# 构建前计数复读（进证据头；全 20 文件）：
for f in <§3.1 全部 20 个文件路径>; do printf "%3d %s\n" "$(grep -c '@Test' "$f")" "$f"; done
# 逐步汇总与 9 类签名扫描（20 份全部）：
for f in "$D"/step*.log; do echo "== $(basename "$f")"; grep -E "OK \(|Tests run:|Failures:|Error|Skipped|Time:" "$f" | tail -4; done
for f in "$D"/*-logcat.txt; do
  printf "%s: " "$(basename "$f")"
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" "$f"
done   # 期望全 0
```

---

## 四、重建重装硬门与源快照门（含 core/model/src）

### 4.1 源快照 find 集合（计划底线：含 `core/model/src`）

```bash
D=build/device-track-batch37; mkdir -p "$D"
find app/src core/model/src core/data/src core/database/src core/domain/src -type f -print0 | sort -z \
  | xargs -0 sha256sum > "$D/src-sha-before.txt"       # 本轨 2026-10-31 计数 = 1173 文件（设备轨重取）
```

- 构建后重取 `src-sha-after.txt`，`diff -u` 非空 = **SOURCE CHANGED DURING BUILD**：前一轮日志作废、重建重装。
- **计划底线 = 五模块集**（本批 T2/T3/T4 全部落点——`app/src`、`core/data/src`、`core/model/src`——都在其内，
  T4 的 KD-82 maxLength 载体 `core/data/.../OpenAiModelProtocol.kt:552/:583/:619/:652` 亦被覆盖）。
- **登记**：批次 36 快照实际为更宽全集（`build/device-track-batch36/src-sha-after.txt` 1548 行，含
  `feature/*/src core/ui/src core/export/src core/markdown/src`，本轨逐路径复核）。设备轨可选该全集以
  加强窗口绑定；无论用哪套，**证据头注明所用集合**；门的目的是构建窗口内源稳定，不是工作树洁净
  （core/domain 开工即脏 4 M——快照记哈希，照常入集）。
- R-04 窗口（§一）另取 **keep 三文件 sha before/after**（`app/r04-test-keep.pro`、`app/proguard-rules.pro`、
  `app/build.gradle.kts`；批次 36 E-04 模式；本轨实跑三文件 git 干净）。

### 4.2 构建前一致性门（先于构建；前两项本轨 2026-10-31 已实跑，设备轨重跑）

```bash
python tools/ci/check_r04_keep_rules.py ; echo "KEEP_GATE_EXIT=$?"    # 本轨实测 OK×3、EXIT=0
# S=71 静态比对（批次 34 §1.4 原命令）——本轨实测：
#   SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90 / ONLY_PLAN=[] ONLY_JSON=[] DUP=[] VIEWS_MATCH=True
#   PLAN_VERSION=71 JSON_VERSION=71 / PLAN_HASH=JSON_HASH=714c0ecb85ee5edf74f0013740148138 / RESULT=PASS EXIT=0
# V/P 双读（批次 35 §4.2 原命令）：V=71（StudyDatabase.kt:139）P=71；71.json 在树（本轨 ls 实证）
# keep 三文件 git 干净（本轨 git status --porcelain 实测空）——若设备轨开工时变脏，按共享纪律整文件纳入并注明
```

`RESULT=FAIL`/`EXIT≠0` 不得进入设备步骤。DB schema 硬约束 = 71（计划 §3）。

### 4.3 构建（只允许 assemble*，禁 connectedAndroidTest；锁文件与 tmp/ 不提交）

```bash
./gradlew --console=plain \
  :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest \
  :core:database:assembleDebugAndroidTest
echo "BUILD_EXIT=$?"          # 必须 0；随后 §4.1 取 after 快照并 diff
```

### 4.4 四包 sha256 与安装字节核对（批次 35 §4.4 原命令；serial 按 §4.7）

```bash
sha256sum app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk \
          app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk \
          core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
          core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe
for apk in <上列四包>; do $A -s "$S" install -r -t "$apk"; done        # 4× Success
for p in com.tingyun.smartmistakebook.offline com.tingyun.smartmistakebook.offline.test \
         com.tingyun.smartmistakebook.core.data.test com.tingyun.smartmistakebook.core.database.test; do
  path=$($A -s "$S" shell pm path "$p" | tr -d '\r' | sed 's/^package://')
  $A -s "$S" shell sha256sum "$path"                                    # 与宿主逐字节一致
done
```

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` → 先 `adb uninstall <精确包名>` 再装，舞步记入证据。
**只有安装成功且 sha 对上，才允许跑 §三。**

### 4.5 schema asset 门（批次 34/35 原命令）

```bash
APK=core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
unzip -l "$APK" | grep -q "assets/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json" || { echo "MISSING 71.json"; exit 1; }
unzip -p "$APK" "assets/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json" | sha256sum   # 进证据头
```

### 4.6 dex 符号门（`need()` 定义批次 34 §4.3 `:456-464` 逐字；基础集 = 批次 34 `:469-542` + 批次 35 §4.6 追加，全部保留不删；本批再追加）

```bash
# 本批追加（app 测试包）：K6 两方法 + P-12③ 例 + V01App 套件（类名以 T2 落树为准）
need app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk \
  VisualJsIsolationInstrumentedTest \
  workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater \
  theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState \
  anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse \
  V01VisualChainInstrumentedTest
# 本批追加（data 测试包）：A-10 三例
need core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
  FigureDispatchBudgetBoundaryInstrumentedTest \
  aSeventhOperationDispatchIsDeniedWithTheRealCount \
  switchingMethodDoesNotResetTheOperationDispatchCount \
  theChatFinalAnswerSlotSurvivesFigureBudgetExhaustion
```

**通用规则**：构建前 grep 出的每个新增/迁移 `@Test` 方法名都必须出现在对应测试包 dex 中；
任一 `MISSING` = 旧包/漏装（D），停，重建重装。

### 4.7 设备再登记（每次会话重新登记，serial 不得沿用记忆）

```bash
$A devices -l                     # 2026-10-31 基线实跑：5562/5584（API 34）+ 5596（API 23）三台在线
$A -s "$S" shell getprop ro.build.version.sdk       # 本轨默认 5562 期望 34
$A -s "$S" shell getprop ro.product.cpu.abi         # 期望 x86_64
$A -s "$S" shell getconf PAGE_SIZE                  # 期望 4096
$A -s "$S" shell getprop ro.build.fingerprint
```

- **5562 = 本轨默认**（批次 35/36 同）；5566/5584 属他线**禁碰**（批次 35 §4.7 口径）；5596 = API 23
  KD-77 冒烟专用机，**不跑本轨任何套件**。五值进证据头；换设备则注明不能跨设备比较像素（一致门）。

### 4.8 顺序（不可颠倒）

§4.7 登记 → §4.2 一致门 → §4.1 快照 before → §4.3 构建 → §4.1 快照 after diff → §4.4 安装与 sha →
§4.5 asset 门 → §4.6 dex 门 → §三 20 步串行（step01–18 → step19-v01-app → step20b-a10-budget）→
§一 R-04 轨（独立舞步：K1→K2→K3/K4/K5→三值预检→（分支 P1/P2）→K6）→ §1.8 步 5 回归 → 步 6 恢复 debug 态。

---

## 五、红例归因总规则

- 先 **A 夹具 → B 他线 → D 旧包/环境 → 最后 C 产品回归**（批次 34 §5 序）；K6 专用六行表见 §1.7。
- **绝对禁止**（批次 34 `:682-689`）：删/弱化断言、`@Ignore`/`Assume` 跳过、`-e class ...#method` 过滤红例、
  改小 `OK (N)` 的 N、先改测试再找原因、以旧批次绿轮替代当前树证据、放宽 R-04 判据、引入
  `-dontobfuscate`、换 internal 变体。
- 归因记录最低字段：红例名+行号+失败消息+第一项目帧+四源核对（逐源 `path:line`）+类别+处置+
  重建 sha256+绿轮 summary。缺任一 = 归因不成立。

## 六、本轨未验证项（全部显式，不当事实用）

1. **一切设备结果 UNVERIFIED**：本轨零 Gradle、零 adb、零 git 写；全部 `OK (N)` 为判据不是结果。
2. **T2/T3/T4 落码未发生**（本轨核对时点）：V-01 迁移套件、A-10 QUEUED 修复、KD-82 对齐都不在树——
   §二 的类名/包名/计数是期望形态，设备轨以落树后构建前 grep 为准。
3. **三值预检在批次 37 新 K2 产物上的读数**：预期 ①③ 过、② 不过——是 R2 verify §2.3 的**预测**，
   非实测；实测归设备轨（§1.5）。
4. **K6 崩溃形态**（机制① `NoSuchMethodError Lj$/util/g; <init>(Ljava/util/Map;)V`）是 R2 verify §3.5 的
   **预测**，非设备实测；批次 35 红是最近一次实测（`build/device-track-batch35/r04-k6-logcat.txt:86/:92/:110`）。
5. **分支 P2 的口径切换**（预检硬门 vs 计划 §1.3 全链重跑）须主会话显式确认——本判据只给两分支与共同禁止，
   不代裁（R2 verify §四.3 原文）。
6. **V01App 套件在 release 轨的套件单问题**：K6 运行面仅两方法不受影响；扩大套件单则 internal seam
   keep 逐条补（§1.6，批次 36 record §7.4 移交项）。
7. 设备在线状态为 2026-10-31 基线转引，执行窗须重新登记（§4.7）；serial 与包残留状态 UNVERIFIED。
8. 生产签名（一次性本地 key 之外）、arm64/16KB release 面、API 23 全套件（KD-77 冒烟未含 instrument）
   维持既有缺口，本批判据不覆盖。

## 七、来源

- 计划：`.zcode/plans/plan-2026-10-31-batch-37-v01-isolated-r04-k6.md`（§1.1/§1.2/§1.3、§2 任务 3、§3 硬约束）
- K1–K6 判据与命令：`docs/research/2026-10-30-batch-35-device-criteria.md:155-310`（§二全文）；
  K6 原文 `docs/research/2026-10-29-snapshot-identity-wal-and-r8.md:565-577`（转引）
- K6 机制/三值预检/两分支/归因表：`docs/research/2026-10-31-r04-k6-verify.md`（§一.1/§二/§三/§四）
- K6 判据 R1 版：`docs/research/2026-10-30-r04-k6-keep-rules.md:123-138`
- 批次 36 执行欠账：`docs/research/2026-10-30-batch-36-record.md` §4.4（step19/20 红因）、§4.5（K5/K6/步 5/步 6）、
  §7（遗留 1–4）
- 批次 37 基线：`docs/research/2026-10-31-batch-37-baseline.md`（§一 HEAD、§三 V-01 形态与构造先例、
  §四 A-10 红因链、§五 keep/K1–K6 现状、§七 设备）
- 命令模板/`need()`/归因：`docs/research/2026-10-29-batch-34-device-criteria.md:456-542,557-668,679-723`
- 本轨实读代码：`VisualJsIsolationInstrumentedTest.kt:123/:245/:120`；`VisualJsClientEngine.kt:100`；
  `app/src/main/AndroidManifest.xml:69-72`（基线 §3.2 实读转引）；`VisualFrameInputs.kt:19-31`；
  `V01VisualChainInstrumentedTest.kt:118/:141/:225/:411/:550/:582/:667-668`；
  `FigureDispatchBudgetBoundaryInstrumentedTest.kt:50/:84/:122-160/:173-196`；
  `ModelTaskDatabasePort.kt:42-58`；`ModelTasks.kt:42-52`；`OpenAiModelProtocol.kt:552-652`（grep）

## 附 A：本轨 2026-10-31 实跑命令与输出（可重跑）

```bash
cd "D:/smart mistake book"
python tools/ci/check_r04_keep_rules.py ; echo "KEEP_GATE_EXIT=$?"
#   → OK: …proguard-rules.pro: 0 hits / OK: …r04-test-keep.pro: 0 hits / OK: -dontobfuscate 0 hits in each of 2 …
#   → KEEP_GATE_EXIT=0
S=71 python - <<'PY' …（批次 34 §1.4 逐字脚本）…
#   → SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90 / … / RESULT=PASS ；S71_EXIT=0
for f in <20 个套件文件>; do printf "%3d %s\n" "$(grep -c '@Test' "$f")" "$f"; done
#   → 34/2/5/9/5/15/4/3/12/12/16/24/16/15/2 + 1/6/27 + 4 + 3（§三.1 表逐一相等）
grep -n "workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater\|theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState" \
  app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt   # :123/:245
find app/src core/model/src core/data/src core/database/src core/domain/src -type f | wc -l               # 1173
wc -l build/device-track-batch36/src-sha-after.txt                                                        # 1548（批次 36 全集）
git rev-parse HEAD                                                                                        # 5ee1f90f…
git status --porcelain -- app/r04-test-keep.pro app/proguard-rules.pro app/build.gradle.kts               # 空（三文件干净）
git log --oneline -1 -- app/r04-test-keep.pro                                                             # b31b5d02
# 未执行：任何 ./gradlew、adb install/am instrument、connectedAndroidTest、任何 git 写操作、任何产品/测试/规则文件改动
```
