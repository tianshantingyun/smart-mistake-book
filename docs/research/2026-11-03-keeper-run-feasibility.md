# R8 Keeper/L8 在 AGP 9.3 的实跑可行性与 `testProguardFiles` 判据（批次 41）

> 研究轨 R8（计划 `.zcode/plans/plan-2026-11-03-batch-41.md:23`），关联实施项 WS9-55（`:99`「R-04 Keeper 实跑尝试（只读验证，不改 settings）」）。
>
> **边界**：本文件由研究子代理落盘。本会话**未运行 Gradle、未执行 adb/设备、未做 git 写、未改任何构建文件**。实跑面 = 文件读取、`git status/log`、`gh api`、`curl`、`javap`（AGP 9.3.0 缓存 jar）、`dexdump`/`unzip`/`apksigner`（2026-10-31 产物）、`python tools/ci/check_r04_keep_rules.py`。
> **前置（本会话全读）**：`docs/research/2026-11-02-keeper-l8.md`（R6）、`docs/research/2026-11-02-keeper-l8-patch-draft.md`、`docs/research/2026-11-02-batch-40-record.md` §8/§9、`docs/research/2026-11-03-batch-41-baseline.md`。
> **声明口径**：`[实跑]` = 本会话执行的命令与其输出；`[实读]` = 本会话读到的仓内文件行；`[上游源码]` = 本会话 `gh api` 取的 tag 源码行；`[引用]` = 仅引自上述前置文档、本会话未复验；`UNVERIFIED` = 未执行/未取得。

## 0. 结论（三问三答）

| 问题 | 判定 |
|---|---|
| **Q1** Keeper 在 AGP 9.3 / AGP 8.x 的接入方式（L8 rule merge） | AGP 9.3 → `io.github.usefulness:keeper-gradle-plugin:0.19.0`（`ApplicationExtension` + `ApplicationAndroidComponentsExtension`，L8 合并三件事）；AGP 8.x → 只有 `com.slack.keeper:keeper:0.16.1`（`AppExtension`，L8 合并两件事），但它在本仓**不可用**：AGP 9 已移除 `AppExtension`（apply 即崩 #152）、compileSdk 37 平台目录不匹配（#155）、且它只以 AGP 8.5.0/Gradle 8.8 为基线构建。见 §2。 |
| **Q2** 只用 `testProguardFiles`（不引入 Keeper）能否验证 K1–K6 | **不能**。`testProguardFiles` 只进 test 侧 R8；本仓实测它在 2026-10-31 产物上**没有**阻止 test L8 对 `j$` 的 482 次改名，K6 的结构因仍在。K2/K3/K4/K5 在未引入 Keeper 的现产物上本已满足，K6 是唯一红门且不在 `testProguardFiles` 的作用面。见 §3。 |
| **Q3** Keeper 0.19.0 + 不改 `settings.gradle.kts` 能否验证 K1–K6 | **有条件可行**。三道静态门全过（§4.1：marker 可解析 / `FAIL_ON_PROJECT_REPOS` 用 `automaticR8RepoManagement=false` 绕开 / R8 9.1.31 从现有 `google()` 可取自足性待证）。但三道运行门全不满足：**Gradle 未跑**（纪律）、**release 签名 4 个环境变量全 UNSET**（K2 会在 `validateReleaseSigning` 停）、**无设备**（K6）；另需先裁定 D2/D3（mapping 门 v2 / 测试 keep 案 A-B）。见 §4、§5。 |

一句话：`testProguardFiles` 是 R8 面的旋钮，而 K6 的碰撞在 **L8 面**；不碰 `settings.gradle.kts` 的可行修法仍是 Keeper 0.19.0 的三件事（merge / clear / `testedMappingFile` 重定向），其在本仓能否跑通属 **UNVERIFIED**（U1）。

## 1. 本会话证据基线

### 1.1 构建面（[实读]，行号为本会话 `grep -n` 实测）

| 项 | 值 | 证据 |
|---|---|---|
| AGP | `9.3.0` | `gradle/libs.versions.toml:2` |
| Kotlin | `2.3.21` | `gradle/libs.versions.toml:3` |
| Gradle wrapper | `9.6.1`（`distributionSha256Sum=9c0f7fae…a32c9e14`） | `gradle/wrapper/gradle-wrapper.properties:3-4` |
| AGP 自己的 Gradle 最低线 | **9.5.0** | `javap -v` AGP 9.3.0 `VersionCheckPlugin` 常量池 `#264 = "9.5.0"`（"Minimum supported Gradle version is …"）[实跑] |
| compileSdk / buildTools | `37` / `37.0.0` | `app/build.gradle.kts:16-17` |
| minSdk / targetSdk | `23` / `36` | `app/build.gradle.kts:28-29` |
| core library desugaring | 开启 | `app/build.gradle.kts:113` |
| R-04 测试变体门 | 默认 `debug`；`-Psmbk.testBuildType=release` 切 release | `app/build.gradle.kts:12,65` |
| 测试 keep 入口 | `release { testProguardFiles("r04-test-keep.pro") }` | `app/build.gradle.kts:98` |
| 签名门 | 任何「含 Release 且以 bundle/assemble/package 开头」的任务 dependsOn `validateReleaseSigning` | `app/build.gradle.kts:144-166` |
| settings 仓库模式 | `RepositoriesMode.FAIL_ON_PROJECT_REPOS`；`google()`+`mavenCentral()` | `settings.gradle.kts:10-15` |
| pluginManagement | `google()`/`mavenCentral()`/`gradlePluginPortal()` | `settings.gradle.kts:2-5` |
| 平台目录 | `platforms/` 只有 `android-23`、`android-34`、`android-37.0`（**无 `android-37`**） | `ls C:/Android/Sdk/platforms/` [实跑] |
| HEAD / 工作树 | `62522930` / 511 条 `git status --porcelain` | `git rev-parse --short HEAD`；`git status --porcelain｜wc -l` [实跑] |
| 触面文件清洁 | `gradle/libs.versions.toml`、根 `build.gradle.kts`、`app/build.gradle.kts`、`app/r04-test-keep.pro`、`app/proguard-rules.pro`、`settings.gradle.kts` 全部无输出 | `git status --porcelain -- <六路径>` [实跑]（W2 满足） |
| release 签名环境变量 | `RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`/`RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD` **4/4 UNSET**（只判存在性，未打印值） | `[ -n "${!v}" ]` [实跑] |
| keep 门 | `python tools/ci/check_r04_keep_rules.py` → 两文件 0 hits，**EXIT=0** | [实跑] |
| 依赖钉版门覆盖 | `tools/ci/check_dependency_pins.py` 无 `keeper`/`r8` 字样 | `grep` [实跑] |

### 1.2 产物级基线（2026-10-31 22:19–22:23 窗口，[实跑]；**不绑定 HEAD 62522930**）

| 读数 | 主包 | 测试包 |
|---|---|---|
| APK 字节 | 155,807,759 | 1,132,404 |
| mapping 字节 | 125,077,913 | 5,922,291 |
| `# L8 Desugaring Mapping` 段 | 1 | 1 |
| `^j$… -> j$…` 行数 | **505** | **482** |
| dex 中 `Class descriptor : 'Lj$/…'` 定义数 | **505**（classes2.dex） | **482**（classes2.dex） |
| 碰撞字面量 | `j$.util.DesugarCollections$SynchronizedMap -> j$.util.g:`（mapping `:872295`） | `j$.util.Comparators$NaturalOrderComparator -> j$.util.g:`（mapping `:49568`） |
| 测试 L8 中间 dex | — | `desugar_lib_dex/strictOfflineReleaseAndroidTest/…/classes1000.dex` = **422,268 B** |
| 证书 SHA-256 | `9fd478d96b3e8f6f67e7a2a28a25a36b665cbe56edde6f8c82ea6f6a1f633969`（DN `CN=R04 Local`） | **同值** |
| `libquickjs.so` | 2 条（zip 条目） | — |
| 测试类名 `VisualJsIsolationInstrumentedTest` | — | 35 处（classes.dex） |

命令（可复跑）：

```bash
M=app/build/outputs/mapping/strictOfflineRelease/mapping.txt
T=app/build/outputs/mapping/strictOfflineReleaseAndroidTest/mapping.txt
grep -cF 'DesugarCollections$SynchronizedMap -> j$.util.g' "$M"   # 1
grep -cF ' -> j$.util.g:' "$T"                                    # 1
grep -cE '^j\$.* -> j\$' "$M"; grep -cE '^j\$.* -> j\$' "$T"       # 505 / 482
grep -c '# L8 Desugaring Mapping' "$M"; grep -c '# L8 Desugaring Mapping' "$T"  # 1 / 1
"C:/Android/Sdk/build-tools/37.0.0/dexdump.exe" -d app/build/tmp-r6/dex/classes2.dex \
  | grep -c -F "Class descriptor  : 'Lj\$/"                      # 482
"C:/Android/Sdk/build-tools/37.0.0/apksigner.bat" verify --print-certs \
  app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk | grep "SHA-256"
```

> 这组数字有两个用途：① 作为「引入 Keeper 前」的对照基线；② 作为 §3.2 的反证——`r04-test-keep.pro:74` 的 `-keep class j$.** { *; }` 于 `b31b5d02`（2026-10-31 00:42）入库，比该构建早约 22 小时，**构建产物仍显示 482 条 j$ 改名**。

## 2. L8 rule merge：两条 AGP 线各做什么

### 2.1 碰撞的物理成因

`coreLibraryDesugaring` 开启后，AGP 为**主变体**与**测试变体**各跑一次 `L8DexDesugarLibTask`，各自把 `java.*` 降级桩打成 `j$/…` 类并**独立分配短名**；两次运行互不协调。合并 instrumentation classloader 先加载 test dex，于是 test dex 可能占用主包已改名的短名（实测 `j$.util.g` 被 `NaturalOrderComparator` 占用，而主包把 `DesugarCollections$SynchronizedMap` 改成了 `j$.util.g`），运行期 `new j$/util/g(map)` 命中无 `Map` 构造器的枚举比较器 → `NoSuchMethodError`。

崩溃真迹（本会话 `grep`）：`build/device-track-batch35/r04-k6-logcat.txt:86`（`NoSuchMethodError: No direct method <init>(Ljava/util/Map;)V in class Lj$/util/g; … base.apk!classes2.dex`），另 `:141/:240/:274` 同签名。

### 2.2 AGP 8.x：`slackhq/Keeper 0.16.1`（L8 合并 = 两件事）

[上游源码] `repos/slackhq/Keeper@0.16.1` `KeeperPlugin.kt`：

| 动作 | 行 |
|---|---|
| `MIN_GRADLE_VERSION = 8.0`，apply 时 `check` | `:92,103-106` |
| 取 **`AppExtension`**（旧 DSL） | `:26,109` |
| 把 test L8 的 `keepRules` 并入 app L8 的 `keepRulesConfigurations` | `:158,167` |
| `testTask … doLast { clearDir(desugarLibDex) }` 清空 test 侧 L8 dex | `:210` |
| 把 `testVariant.runtimeConfiguration.proguardFiles()` 与 `testVariant.proguardFiles` 追加进 app R8 `configurationFiles` | `:295,297` |

AGP 8.x **没有** `testedMappingFile` 重定向分支——那是 AGP ≥ 9.1 才需要（见 2.3）。已知限制（本会话 `gh` 实取）：

- `#152`（OPEN，2026-07-04 更新）：AGP 9.0.0 + 默认 `android.newDsl=true` 下 apply 即崩 `Extension of type 'AppExtension' does not exist`。
- `#155`（OPEN，2026-08-12 建）：**compileSdk 37 不支持**——API 37 平台以 minor-version 目录 `platforms/android-37.0` 安装，0.16.1 只查 `platforms/android-37`，报 `No android.jar found! Expected … platforms/android-37/android.jar`。**本机正是这种布局**（§1.1）。
- `#136`（CLOSED，2024-02-14）：`FAIL_ON_PROJECT_REPOS` 下崩溃，报错原文 `repository 'R8 releases repository for use with Keeper' was added by plugin 'com.android.internal.application'`——与本仓 `settings.gradle.kts:10` 同构。
- `#119`（CLOSED，2023-07-25）：`AGP 8.1.0: NoSuchMethodError L8DexDesugarLibTask.getKeepRulesFiles`——私有 AGP API 逐版断裂的典型样本。
- 发布面：最新发布 `0.16.1 / 2024-06-28`（`gh api repos/slackhq/Keeper/releases`），之后无代码提交；AWS 维护方在 `#145` 公开「寻求新家」。

**对本仓判定：不可用。** 两重独立否决即可排除：AGP 9 `android.newDsl=true` 下 `AppExtension` 不存在（0.16.1 发布于 AGP 8.5.0/Gradle 8.8 时代，无 AGP 9 支持），以及 compileSdk 37 的平台目录布局（本机实测无 `android-37`）。

### 2.3 AGP 9.3：`usefulness/keeper 0.19.0`（L8 合并 = 三件事）

[上游源码] `repos/usefulness/keeper@v0.19.0`（tag `43ed9544…`）`KeeperPlugin.kt`：

| 动作 | 行 | 说明 |
|---|---|---|
| 改用 `ApplicationExtension` + `ApplicationAndroidComponentsExtension` | `:20,22,97-102` | 这就是 fork 能在 AGP 9 上 apply 的根因（上游 #152 的修法） |
| `MIN_GRADLE_VERSION = 9.0.0`，apply 时 `check` | `:84,93-94` | **AGP 8.x 线不可能用 0.19.0**（Gradle 8 会被拒） |
| ① app L8 `keepRulesConfigurations.addAll(testTask.keepRules…)` | `:173-177` | 主 L8 成为测试所需 j$ 类的超集 |
| ② `testTask … doLast { clearDir(desugarLibDex) }` | `:206-213` | 测试 APK 不再携带独立改名的 L8 dex（碰撞载体被移除） |
| ③ AGP ≥ 9.1：`testedMappingFile <- appR8Task.mappingFile`（`afterEvaluate` 中 `setFrom`） | `:151-172` | 规避 `app L8 ← test L8 ← test R8` 任务环 |
| ④ `configurationFiles.from(testVariant.proguardFiles)` 等 | `:287-293` | **把测试 keep 规则喂进主包 R8**（§3.4 的保真度问题） |
| ⑤ `infer…KeepRulesForKeeper` 用 R8 `TraceReferences` CLI 推 keep | `InferAndroidTestKeepRules.kt:107-145` | classpath = `keeperR8` 配置 |
| ⑥ `automaticR8RepoManagement=true` 时 `with(project.repositories){ maven{…r8-releases/raw} }` | `InferAndroidTestKeepRules.kt:175-188` | 与 `FAIL_ON_PROJECT_REPOS` 直接冲突 |
| ⑦ 平台目录兼容 `android-<N>.<minor>` 回退 | `KeeperPlugin.kt:299-315` | 即 #155 的修法，本机 `android-37.0` 命中 |
| `emitDebugInformation` 默认 `false`；`automaticR8RepoManagement` 默认 **`true`** | `KeeperExtension.kt:30-31,42-43` | 接入选型必须显式关掉后者 |
| `traceReferences.arguments` 默认含 `--map-diagnostics:MissingDefinitionsDiagnostic error info` | `KeeperExtension.kt:85` | **默认就是严格**；补丁草案「不加该参数」的前提需更正（§8 采纳说明） |

版本对位（本会话 `gh`/`curl` 实取）：tag `v0.19.0` 的 `gradle/libs.versions.toml:2,10` = `google-agp 9.3.0`、`google-r8 9.1.31`；`gradle-wrapper.properties:3` = `gradle-9.6.1-bin.zip`；Maven Central `keeper-gradle-plugin` metadata `<latest>=0.19.0`（lastUpdated `20260723124411`）；插件 jar 127,132 B、sha256 `fdaf7e0780b04157b904b0f14b283ef7bccaeb29bfcff474611d729bdd346eb5`；仓库 pushed 2026-10-28、Apache-2.0、**0 star**、唯一 open issue 是依赖机器人。

### 2.4 已知限制汇总（按线）

| 限制 | AGP 8.x（0.16.1） | AGP 9.3（0.19.0） | 本会话证据 |
|---|---|---|---|
| AGP 新 DSL | 崩（#152） | 已修 | `gh issue view 152`；源码 `:97-102` |
| compileSdk 37 | 崩（#155） | 修（`:299-315`） | `gh issue view 155`；`ls platforms` = `android-37.0` |
| `FAIL_ON_PROJECT_REPOS` | 崩（#136） | 同类冲突，需 `automaticR8RepoManagement=false` | `gh issue view 136`；`KeeperExtension.kt:30-31` |
| 私有 AGP API | `:109`、`:210` 逐版断裂（#119） | 同风险，钉 AGP 9.3.0 | `gh issue view 119` |
| L8 mapping 未清 | `:210` 只清 dex | `:206-213` 只清 dex | 源码实读（→ §4.3 门 ② 仍为 1） |
| 测试 keep 外溢主包 | `:295-297` | `:287-293` | 源码实读（→ §3.4） |
| R8 构件来源 | `automaticR8RepoManagement` | 同上 + thin jar 自足性未证 | §4.2 |
| 维护面 | 2024-06 后停更 | 单维护者、0 star、无 0.20 | `gh api repos/...` |
| Gradle 最低线 | 8.0 | **9.0** | 源码 `:84,92` |

## 3. `testProguardFiles` 到底管什么（本文件核心）

### 3.1 AGP 9.3.0 的 DSL 事实（[实跑] `javap` 本机缓存 `gradle-api-9.3.0.jar`）

```
com.android.build.api.dsl.VariantDimension:
  public abstract java.util.List<java.io.File> getTestProguardFiles();
  public abstract java.lang.Object testProguardFile(java.lang.Object);
  public abstract java.lang.Object testProguardFiles(java.lang.Object...);
```

`javap -v` 显示该方法**无 `Deprecated`/`ReplacedByIncubating` 注解**——AGP 9.3.0 仍是一等 DSL，本仓 `app/build.gradle.kts:98` 的用法在 9.3.0 上成立（批次 40 记录 §12.1 的配置期成功亦为旁证，[引用]）。

官方语义（`app/r04-test-keep.pro:6-8` 引文一致）：「Adds proguard rule files to be used when **processing test code**」——即进 **test 侧 R8**。本会话无法连通 `developer.android.com`（WebFetch 两次 443 超时、`curl -m 25` 000），故以 jar 内 API 存在性 + 仓内行为为准，官方 DSL 页面文字标 UNVERIFIED（U6）。

### 3.2 它管不到 L8：两条独立证据

**证据 A（结构）**：L8 任务的 keep 输入不是 proguard 文件集合，而是**字符串列表**。

```
javap -p com.android.build.gradle.internal.tasks.L8DexDesugarLibTask  (AGP 9.3.0 jar)
  ListProperty<String>  getKeepRulesConfigurations()
  RegularFileProperty   getKeepRules()        // 输出：生成的 keep 规则
  DirectoryProperty     getDesugarLibDex()
  RegularFileProperty   getOutputMappingFile()
  …（无任何 proguardFiles/FileCollection of .pro）
```

`testProguardFiles` 是 `VariantDimension` 上的文件列表，消费方是 R8 任务；L8 任务只认 `keepRulesConfigurations`（字符串）与自身 `keepRules`（输出）。Keeper 之所以要 `:173-177` 手工把 `testTask.keepRules` 读成 `List<String>` 再喂给 app L8，正因为**没有**现成 DSL 能把 proguard 文件送进 L8。AGP 没有「清空 test L8 dex」的 DSL，Keeper 只能 `doLast { clearDir(...) }`（`:206-213`）。

**证据 B（本仓反证）**：`app/r04-test-keep.pro:74` 已有 `-keep class j$.** { *; }`（`b31b5d02`，2026-10-31 00:42 入库）。若 `testProguardFiles` 能约束 test L8，test dex 里 j$ 类应保原名；但 2026-10-31 22:19/22:23 的产物实测：test mapping 仍 482 条 `j$… -> j$…`、`NaturalOrderComparator -> j$.util.g`（`:49568`）、test dex `classes2.dex` 仍 482 个 `Lj$/…` 类定义、test L8 中间 dex 仍 422,268 B。

> 该反证的唯一软点：该构建的源快照未与 `b31b5d02` 绑定（批次 40 只对 10:13 的 debug 四包做过快照）。因此写作「强推断」，其确证动作已放进 §5 Step 6 的 K6 预期——若按现树重建后 test dex 仍有 j$ 定义，则结构因未消，K6 必红。

### 3.3 逐门判定：只用 `testProguardFiles` 能否验证 K1–K6

| 门 | 判据（不放宽） | 现树/现产物状态（[实跑] 基线） | 仅靠 `testProguardFiles` 能否满足 |
|---|---|---|---|
| K1 | 步 1 红基线确实红，红因 = R8 改名/签名门 | 红基线真迹在 `r04-k6-logcat.txt:86`（2026-10-29）；**当前树未重跑** | 无关。红因是 L8 碰撞，不是 keep 规则不足；加规则不改变「红基线」的存在性 |
| K2 | `BUILD SUCCESSFUL` 且两个 release 产物都在 | 2026-10-31 产物在盘（主 155.8 MB / 测 1.13 MB） | 本已满足；不是失败点 |
| K3 | 两 APK 证书 SHA-256 相同 | 两者均 `9fd478d9…3969`、DN `CN=R04 Local` | 本已满足；`testProguardFiles` 不影响签名 |
| K4 | `-dontobfuscate` 两文件 0；release mapping 有真实改名 | keep 门 EXIT=0；main mapping 505 条 j$ 改名 + 全量 app/库改名 | 本已满足（main `configuration.txt` 实测 `^-keep class com.tingyun` = 0，测试 keep 未进主包） |
| K5 | `libquickjs.so` 在包；测试类名未改名 | zip 2 条 `libquickjs.so`；test dex 35 处类名 | 本已满足 |
| K6 | 两例 `OK (2 tests)`，logcat 三类签名 0 | 现树**无当前证据**；历史红为 `Lj$/util/g` | **不能**。修 j$ 需要 L8 侧 merge+clear；修 `androidx/tracing/Trace` 那半在 `app/proguard-rules.pro:35-39`（与 `testProguardFiles` 无关，且已存在） |

**结论（Q2）**：K1–K6 里真正需要「修」的只有 K6，而 K6 的两半——j$ 碰撞与 `Trace` 悬空——分别落在 L8 侧与主包 keep 侧，`testProguardFiles` 两处都不在作用面。因此「用 `testProguardFiles` 验证 K1–K6」在语义上不成立：它是把 R8 面已有的旋钮再拧一圈，不会改变 L8 面的输出。

**可检验预测**（写死，供门禁阶段对账）：按现树 `-Psmbk.testBuildType=release` 重建后，不引入 Keeper 时：

- test APK `classes*.dex` 的 `Lj$/…` 定义数 **> 0**（预期 ≈482）；
- test mapping ` -> j$.util.g:` = **1**；
- 设备跑 K6 两例会复现 `NoSuchMethodError … Lj$/util/g;`（若 `Trace` 那半已修，则只剩 j$ 这一条签名）。

### 3.4 引入 Keeper 后 `testProguardFiles` 的语义被改写（必须同步的两处）

Keeper `:287-293` 把 `testVariant.proguardFiles` 追加进**主包** R8。本仓该文件含：

```proguard
# app/r04-test-keep.pro:22-29
-keep class com.tingyun.smartmistakebook.** {
    <init>(...);
    @org.junit.Test <methods>;
    …
}
```

后果与两处「牵动面」：

1. **`app/r04-test-keep.pro:9-10` 的声明失效**——现文写「these rules apply to the androidTest (test) dex only. The main APK's obfuscation surface is still governed by app/proguard-rules.pro alone」。开了 Keeper 后这句为假，补丁必须同步改注释（否则文档与行为相反）。
2. **K4 实验保真度下降**——主包 R8 收进广域 `-keep class com.tingyun.smartmistakebook.**` 后，R-04 变体的 app 类整体保名；K4「release mapping 有真实改名」仍可由库类通过，但「app 代码真的被混淆」这一实验前提被削弱。

两案（须裁定，不擅自改）：**案 A** 保留广域 keep、接受保真度下降；**案 B** 收窄为 `-keepclasseswithmembers class com.tingyun.smartmistakebook.** { <init>(...); @org.junit.Test <methods>; }` + 生命周期注解 `-keepclassmembers`。案 B 加强主包混淆面（不是放宽门），但改变测试 keep 语义，K5b/K6 必须重跑。

## 4. 不改 `settings.gradle.kts` 的可行性（三道静态门 + 三道运行门）

### 4.1 静态门（本会话可判）

| 门 | 判据 | 实测 | 结论 |
|---|---|---|---|
| S1 插件 marker 解析 | 现有 `pluginManagement.repositories` 能取到 `io.github.usefulness.keeper` | `curl` Maven Central marker POM → **200**（1,497 B）；metadata `<release>=0.19.0`；`settings.gradle.kts:2-5` 已含 `mavenCentral()` | **过** |
| S2 仓库模式冲突 | `automaticR8RepoManagement=false` 后不再触碰 `project.repositories` | `settings.gradle.kts:10` = `FAIL_ON_PROJECT_REPOS`；Keeper 默认 `true`（`KeeperExtension.kt:30-31`）会走 `with(project.repositories)`（`InferAndroidTestKeepRules.kt:180-188`）；上游 `#136` 的报错原文与本仓同构 | **必须显式关掉**，关后**过** |
| S3 R8 构件可得 | `com.android.tools:r8:9.1.31` 从现有 `google()` 可取 | `curl` Google Maven POM → **200**；jar → **200 / 18,963,189 B** | **过**（构件可得）；**自足性未证**（U3） |

不需要编辑 `settings.gradle.kts` 的任何一项：插件走 `pluginManagement`，R8 走 `dependencyResolutionManagement.google()`。补丁面 = `gradle/libs.versions.toml` + 根 `build.gradle.kts` + `app/build.gradle.kts`（三者均非热区、本会话实测干净）。

### 4.2 运行门（本会话不可满足）

| 门 | 判据 | 实测 | 结论 |
|---|---|---|---|
| R1 Gradle 可跑 | 门禁阶段执行 | 纪律：研究轨不跑 Gradle | **未执行** |
| R2 签名可用 | `assemble*Release*` 前 `validateReleaseSigning` 通过 | 4 个 `RELEASE_*` **全 UNSET**；`app/build.gradle.kts:160-166` 把 `validateReleaseSigning` 挂到所有含 `Release` 且以 `bundle/assemble/package` 开头的任务 | **当前必失败**（`GradleException: Release signing is missing …`） |
| R3 设备 | K6 需安装两包并 `am instrument` | 无设备操作 | **未执行** |

> R2 是硬阻塞：`assembleStrictOfflineReleaseAndroidTest` 名称含 `Release` 且以 `assemble` 开头 → 命中 `dependsOn("validateReleaseSigning")`。门禁阶段必须先注入四个签名环境变量（值不入库、不打印）。

### 4.3 引入 Keeper 后旧 mapping 门 ② 必然仍为 1（须 v2 裁定）

Keeper 只清 `desugarLibDex`（`:206-213`），**不清 L8 的 `outputMappingFile`**。现产物 main/test 两份 mapping 各已有 1 个 `# L8 Desugaring Mapping` 段（§1.2 实测），故 Keeper 后 test mapping 的 ` -> j$.util.g:` 预计仍为 **1**（推断，U2）。旧 ② 硬门会把「运行时已修好」的构建判红。建议 v2（与 R6 §5.3 一致，须 D2）：

| 预检 | v1 | v2（建议） | Keeper 后期望 |
|---|---|---|---|
| ① | 主 mapping 含 `…SynchronizedMap -> j$.util.g` | 主 dex 有该类定义（原名或映射目标）；短名仅诊断 | 结构过 |
| ② | test mapping ` -> j$.util.g:` = 0 | **test APK `classes*.dex` 的 `Lj$/…` 定义数 = 0**；旧 grep 降为诊断 | 0 |
| ③ | 主包 dex 含 `androidx/tracing/Trace` | 不变 | ≥1 |
| ④（新） | — | 主/测 dex 类描述符交集 = 0 | 0 |
| ⑤（新） | — | `desugar_lib_dex/strictOfflineReleaseAndroidTest/**/*.dex` 无正尺寸文件 | 空 |

## 5. 可执行命令序列与预期输出

> 标注：`[已跑]` = 本会话执行过；`[未跑]` = 本会话未执行（禁 Gradle / 无设备），预期值来自源码与历史基线，属 UNVERIFIED。全部在 `D:/smart mistake book` 下执行。

### Step 0 静态前置（只读，可随时跑）

```bash
git status --porcelain -- gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts \
  app/r04-test-keep.pro app/proguard-rules.pro settings.gradle.kts
echo "TOUCH_EXIT=$?"
python tools/ci/check_r04_keep_rules.py; echo "KEEP_GATE_EXIT=$?"
curl -s -o /dev/null -w "marker=%{http_code}\n" \
  https://repo1.maven.org/maven2/io/github/usefulness/keeper/io.github.usefulness.keeper.gradle.plugin/0.19.0/io.github.usefulness.keeper.gradle.plugin-0.19.0.pom
curl -s -o /dev/null -w "r8pom=%{http_code}\n"  https://dl.google.com/android/maven2/com/android/tools/r8/9.1.31/r8-9.1.31.pom
curl -s -o /dev/null -w "r8jar=%{http_code} %{size_download}\n" https://dl.google.com/android/maven2/com/android/tools/r8/9.1.31/r8-9.1.31.jar
ls C:/Android/Sdk/platforms/
for v in RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD RELEASE_STORE_FILE RELEASE_STORE_PASSWORD; do
  [ -n "${!v}" ] && echo "$v=SET" || echo "$v=UNSET"; done
```

预期（= 本会话实测）：`TOUCH_EXIT=0` 且无文件输出；`KEEP_GATE_EXIT=0`（两文件 0 hits）；`marker=200`；`r8pom=200`；`r8jar=200 18963189`；`platforms/` 含 `android-37.0`、无 `android-37`；签名四项 `UNSET`（门禁阶段若仍 UNSET，Step 2 必失败）。

### Step 1 应用补丁（**写操作，非本轨**；前置 = D1/D3 裁定）

落点三文件（`gradle/libs.versions.toml` 加 `keeper = "0.19.0"` 与 plugin alias；根 `build.gradle.kts` 加 `alias(libs.plugins.keeper) apply false`；`app/build.gradle.kts` 加 `keeper { automaticR8RepoManagement.set(false); emitDebugInformation.set(true) }` + `androidComponents.beforeVariants { if (r04TestBuildType == "release" && builder.name == "strictOfflineRelease") it.optInToKeeper() }`）。逐行 diff 见 `docs/research/2026-11-02-keeper-l8-patch-draft.md` §1–§2；本文件只补三处更正：

1. `emitDebugInformation.set(true)` 会新增 `build/intermediates/keeper/l8-diagnostics/<task>/patchedL8Rules.pro`，仅作证据，不影响产物；
2. `traceReferences.arguments` 的默认值**已经**包含 `--map-diagnostics:MissingDefinitionsDiagnostic error info`（`KeeperExtension.kt:85`），补丁不需要加、也不该再叠一份；
3. 若选案 B，`app/r04-test-keep.pro` 与被 Keeper 改写的语义要同批改，且 `app/r04-test-keep.pro:9-10` 的「test dex only」注释必须删除。

### Step 2 K1/K2（红基线 → 绿构建）

```bash
# 首次启用前必须清掉旧的 test L8 产物，否则 UP-TO-DATE 让 Keeper 的 doLast 不执行
rm -rf app/build/intermediates/desugar_lib_dex/strictOfflineReleaseAndroidTest
./gradlew :app:assembleStrictOfflineReleaseAndroidTest -Psmbk.testBuildType=release --console=plain
echo "BUILD_EXIT=$?"
ls -l app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk \
      app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk
```

预期 `[未跑]`：`BUILD SUCCESSFUL`、`BUILD_EXIT=0`、两文件存在（量级参照 §1.2：主 ~155.8 MB、测 ~1.13 MB；若主包显著膨胀 >10%，先查 §3.4 的广域 keep 外溢再定案 A/B）。**必须保持仓库默认的 configuration cache 开启**（`gradle.properties:2` `org.gradle.configuration-cache=true`）；不得用 `--no-configuration-cache` 掩盖插件 CC 问题，CC 红按上游风险登记并上报。若先报 `Release signing is missing required environment variables` → 归 R2（环境），不是 Keeper 缺陷。若报 `Repository … was added by …` → 归 S2（`automaticR8RepoManagement` 未关）。若 `infer…KeepRulesForKeeper` 报 R8 `NoClassDefFoundError` → 归 U3（thin jar 不自足），走 R6 §4.3 回退 1/2，仍不编辑 settings。

K1（红基线）在本会话**无当前树证据**：历史真迹为 `build/device-track-batch35/r04-k6-logcat.txt:86`（2026-10-29，早于 `b31b5d02` 与当前 HEAD）。门禁阶段若要 K1 的当前树证据，需先在**未启用 Keeper**的构型上按 Step 4–6 跑一次并留 logcat。

### Step 3 K3（证书同值）

```bash
A=C:/Android/Sdk/build-tools/37.0.0/apksigner.bat
$A verify --print-certs app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk | grep "SHA-256"
$A verify --print-certs app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk | grep "SHA-256"
```

预期 `[已跑，2026-10-31 产物]`：两行同为 `9fd478d96b3e8f6f67e7a2a28a25a36b665cbe56edde6f8c82ea6f6a1f633969`。Keeper 不参与签名，重建后应仍同值；不同即归环境（两包由不同 signingConfig 签），不得改判据。

### Step 4 K4（不关混淆 + 真实改名）

```bash
python tools/ci/check_r04_keep_rules.py; echo "KEEP_GATE_EXIT=$?"        # 期望 0
grep -c '^-dontobfuscate' app/proguard-rules.pro app/r04-test-keep.pro    # 期望 0 0
grep -cE '^[a-zA-Z0-9_$.]+ -> [a-zA-Z0-9_$]+:$' \
  app/build/outputs/mapping/strictOfflineRelease/mapping.txt             # 期望 ≫ 1000（真改名）
```

预期 `[部分已跑]`：keep 门 EXIT=0（本会话实测）；`-dontobfuscate` 0/0（门脚本已覆盖）；mapping 改名行数量级与 §1.2 基线同阶。**若落案 A**，主 mapping 中 `com.tingyun.smartmistakebook.**` 段退化为 identity，K4 只能靠库类满足——须在证据里写清，不得声称「app 类被混淆」。

### Step 5 K5（native + 测试类名）

```bash
MAIN=app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
TEST=app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk
unzip -l "$MAIN" | grep -c libquickjs.so          # 期望 ≥1（基线 2）
unzip -p "$TEST" classes.dex | grep -a -c -F 'VisualJsIsolationInstrumentedTest'   # 期望 ≥1（基线 35）
```

预期 `[已跑，旧产物]`：`2` 与 `35`。

### Step 6 K6（设备两例 + 三类签名）

```bash
A=adb; S=<device-serial>
$A -s "$S" install -r -t "$MAIN"
$A -s "$S" install -r -t "$TEST"
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater,com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch41/r04-k6.log
$A -s "$S" logcat -v threadtime -d > build/device-track-batch41/r04-k6-logcat.txt
grep -cE "NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError" \
  build/device-track-batch41/r04-k6-logcat.txt      # 期望 0
```

两方法名本会话已核实存在：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt:123` 与 `:245`。预期 `[未跑]`：`OK (2 tests)`、`INSTRUMENTATION_CODE: -1`、三类签名 `0`。若仍出 `NoSuchMethodError … Lj$/util/g;` → 先查 §4.3 的门 ⑤（test L8 dex 是否清空）与 opt-in 是否命中（`builder.name == "strictOfflineRelease"`），不得改判据。

### Step 7 L8 合并后的 v2 门（须 D2）

```bash
mkdir -p build/tmp-keeper/main-dex build/tmp-keeper/test-dex
unzip -o -q "$MAIN" 'classes*.dex' -d build/tmp-keeper/main-dex
unzip -o -q "$TEST" 'classes*.dex' -d build/tmp-keeper/test-dex
D=C:/Android/Sdk/build-tools/37.0.0/dexdump.exe
# ②（硬门，期望 0）
for f in build/tmp-keeper/test-dex/classes*.dex; do
  printf '%s ' "$f"; $D -d "$f" | grep -c -F "Class descriptor  : 'Lj\$/"; done
# ③（硬门，期望 ≥1；大字 dex 用 unzip+grep 更快）
unzip -p "$MAIN" classes.dex | grep -a -c -F 'Landroidx/tracing/Trace;'
# ④（硬门，期望 0）
for f in build/tmp-keeper/main-dex/classes*.dex; do $D -d "$f" | sed -n "s/.*Class descriptor  : '\(.*\)'.*/\1/p"; done | sort -u > build/tmp-keeper/main-desc.txt
for f in build/tmp-keeper/test-dex/classes*.dex; do $D -d "$f" | sed -n "s/.*Class descriptor  : '\(.*\)'.*/\1/p"; done | sort -u > build/tmp-keeper/test-desc.txt
comm -12 build/tmp-keeper/main-desc.txt build/tmp-keeper/test-desc.txt | wc -l
# ⑤（期望无输出）
find app/build/intermediates/desugar_lib_dex/strictOfflineReleaseAndroidTest -name '*.dex' -size +0
# 旧 ② 诊断值（预期仍为 1，不作硬门）
grep -cF ' -> j$.util.g:' app/build/outputs/mapping/strictOfflineReleaseAndroidTest/mapping.txt
```

预期 `[已跑基线，未跑 Keeper 后]`：现产物 ② = **482**、③ = **1**、④ > 0（碰撞即交集非空）；Keeper 后 ② → 0、⑤ 空、④ → 0，旧 ② 预计仍 1。

## 6. K1–K6 判据的归属（谁改、谁验）

| 门 | 变更点 | 不经 Keeper 能否满足 | 经 Keeper 后由谁保证 |
|---|---|---|---|
| K1 | 无（红基线是观测） | 是（已红） | 观测项，不因修复改变 |
| K2 | 无 | 是 | 构建 |
| K3 | 无 | 是 | 签名配置 |
| K4 | 案 A 会削弱 app 段改名（§3.4） | 是（现产物已过） | 案 B 才能保住保真度 |
| K5 | 无（Keeper 不改 native/测试类名） | 是 | 构建 |
| K6 | **是**（L8 merge + clear） | **否** | Keeper `:173-177` + `:206-213` |

## 7. 阻塞与未验证

### 7.1 阻塞

1. **B1 Gradle 未跑**：本轨纪律禁 Gradle，K2/K6 全部 `UNVERIFIED`；实施项 WS9-55「实跑尝试」需门禁阶段单独执行（且无并发）。
2. **B2 签名环境变量缺失**：4/4 UNSET；`assembleStrictOfflineReleaseAndroidTest` 命中 `validateReleaseSigning`（`app/build.gradle.kts:163-168`）→ K2 现在必失败。需门禁阶段注入（不得入库/打印）。
3. **B3 D1/D2/D3 未裁定**：是否采纳 Keeper、mapping 门 v2、测试 keep 案 A/B。未定前补丁不得应用（否则旧 ② 门会误杀，或 K4 实验前提被静默削弱）。
4. **B4 KD-72 锁窗口未开**：工作树 511 条，插件/R8 会新增锁坐标；按纪律不得重生成/提交锁。

### 7.2 UNVERIFIED

| # | 项 | 状态 |
|---|---|---|
| U1 | Keeper 0.19.0 × 本仓完整构建图（AGP 9.3.0 / Gradle 9.6.1 / strictOfflineRelease / CC 开）的 K2 | 未跑 |
| U2 | Keeper 后 test mapping 旧 ② 是否仍为 1 | 推断为 1，未测 |
| U3 | Google Maven thin R8 9.1.31 在完整 fat app/test jar 上的 `TraceReferences` 运行 | 仅证构件可得（200/18,963,189 B） |
| U4 | `testProguardFiles` 阻止 test L8 改名失败这一反证的源快照绑定（§3.2 证据 B） | 产物未绑 `b31b5d02`；强推断 |
| U5 | 插件 POM 的 `kotlin-stdlib 2.4.0` 与本仓 KGP 2.3.21 的 buildscript 解析 | POM 已证 2.4.0（`curl`），兼容性未跑 |
| U6 | 官方 DSL 页面对 `testProguardFiles` 的原文 | `developer.android.com` 两次 443 超时；以 jar API 存在性替代 |
| U7 | AGP 9.3.0 R8 mapping 非确定性（issue 554586985）对 ①/② 字面量的影响 | issue 存在（issuetracker 摘要），「9.3.3 修复」仅 [引用]，未独立确认 |
| U8 | K6 两例设备绿 + 三类签名 0 | 无设备 |
| U9 | 主包 APK 体积/方法数相对 §1.2 基线的变化 | 未跑 |

## 8. 来源与采纳/拒绝

### 8.1 仓内（本会话实读/实跑）

| 来源 | 用途 |
|---|---|
| `settings.gradle.kts:2-5,10-15` | 插件仓库面、`FAIL_ON_PROJECT_REPOS` |
| `gradle/libs.versions.toml:2-3`；`gradle/wrapper/gradle-wrapper.properties:3-4` | AGP/Kotlin/Gradle 钉版 |
| `app/build.gradle.kts:12,16-17,28-29,64-65,95-98,113,144-168` | 变体门、compileSdk、desugar、`testProguardFiles`、签名门 |
| `app/r04-test-keep.pro:1-11,22-29,74` | keep 语义与「test dex only」声明、j$ 规则 |
| `app/proguard-rules.pro:35-39` | `androidx.tracing.Trace` 主包 keep |
| `tools/ci/check_r04_keep_rules.py` | 本会话实跑 EXIT=0 |
| 2026-10-31 产物与 `app/build/tmp-r6/{main-dex,dex}` | §1.2 全部基线数（`grep`/`dexdump`/`apksigner`） |
| `build/device-track-batch35/r04-k6-logcat.txt:86,141,240,274` | K1 红基线真迹 |
| `app/src/androidTest/.../VisualJsIsolationInstrumentedTest.kt:123,245` | K6 两方法名 |
| `docs/research/2026-11-02-keeper-l8.md`、`…-patch-draft.md`、批次 40 记录 §8/§9 | 前置研究（本文件在其上做三处更正） |

### 8.2 外部（URL + 本会话取得方式）

| 来源 | 取法 | 用于 | 采纳/拒绝 |
|---|---|---|---|
| `https://github.com/usefulness/keeper`（`gh api repos/usefulness/keeper`） | `gh` | pushed 2026-10-28、Apache-2.0、0 star、唯一 open issue 为依赖机器人 | 采纳（维护面薄，须钉 0.19.0） |
| `https://github.com/usefulness/keeper/releases/tag/v0.19.0` | `gh api .../releases` | `v0.19.0 / 2026-07-23` 为最新发布 | 采纳 |
| `raw v0.19.0`：`KeeperPlugin.kt`、`InferAndroidTestKeepRules.kt`、`KeeperExtension.kt`、`KeeperVariantMarker.kt`、`gradle/libs.versions.toml`、`gradle-wrapper.properties` | `gh api contents?ref=v0.19.0` + `base64 -d` + `grep/sed` | §2.3 全部机制行、`:84` Gradle ≥9.0、`:299-315` API37 回退、默认参数 | 采纳（一手源码） |
| `https://repo1.maven.org/maven2/io/github/usefulness/keeper-gradle-plugin/maven-metadata.xml` | `curl` | `<latest>/<release>=0.19.0`、`lastUpdated=20260723124411` | 采纳 |
| `…/io.github.usefulness/keeper/…/0.19.0/…pom` | `curl` | marker 200（S1） | 采纳 |
| `…/keeper-gradle-plugin-0.19.0.jar` | `curl` + `sha256sum` | 127,132 B / `fdaf7e07…346eb5`（供应链登记） | 采纳 |
| `…/keeper-gradle-plugin-0.19.0.pom` | `curl` | 依赖 `kotlin-stdlib 2.4.0`（U5） | 采纳为事实、兼容性标未验证 |
| `https://github.com/slackhq/Keeper/issues/152` | `gh issue view` | AGP 9 `AppExtension` 崩溃（OPEN） | 采纳（决定排除 0.16.1） |
| `https://github.com/slackhq/Keeper/issues/155` | `gh issue view` | compileSdk 37 → `platforms/android-37` 不存在（OPEN） | 采纳（本机布局实证同构） |
| `https://github.com/slackhq/Keeper/issues/136` | `gh issue view` | `FAIL_ON_PROJECT_REPOS` 崩溃原文 | 采纳 |
| `https://github.com/slackhq/Keeper/issues/119` | `gh issue list` | AGP 8.1 `getKeepRulesFiles` 私有 API 断裂 | 采纳（作「逐版断裂」样本） |
| `https://github.com/slackhq/Keeper/releases` | `gh api` | 最新 0.16.1 / 2024-06-28 | 采纳 |
| `https://dl.google.com/android/maven2/com/android/tools/r8/9.1.31/{r8-9.1.31.pom,jar}` | `curl` | 200 / 200+18,963,189 B（S3） | 采纳 |
| AGP 9.3.0 `gradle-api-9.3.0.jar`（本机 Gradle 缓存） | `javap -v` | `testProguardFiles` 存在且未弃用（§3.1） | 采纳（强于网页，因为可执行） |
| AGP 9.3.0 `gradle-9.3.0.jar`（本机缓存） | `javap -v` | `VersionCheckPlugin` 常量 `9.5.0`；`L8DexDesugarLibTask` 输入签名（§3.2） | 采纳 |
| `https://developer.android.com/reference/tools/gradle-api/9.3/.../ApplicationBuildType` | WebFetch ×2 / `curl -m 25` | 官方 DSL 原文 | **未采纳（不可达）**：443 超时/000；改以 jar API 判定，页面文字标 U6 |
| `https://developer.android.com/build/releases/agp-9-3-0-release-notes` | `search_text` | 仅取得标题/摘要级命中 | **未采纳为事实**：正文未取到，AGP 9.3 兼容表（Gradle min 9.5.0）改由 AGP jar 常量自证 |
| `https://issuetracker.google.com/issues/554586985` | `search_text` | R8 mapping 非确定性 issue 存在（2026-08-30） | 部分采纳：issue 存在采纳；「9.3.3 修复」仅 [引用]（U7） |

### 8.3 对前置文档的三处更正

1. **`--map-diagnostics:MissingDefinitionsDiagnostic` 不是「默认没有」**：`KeeperExtension.kt:85` 的 `traceReferences.arguments` 默认已含 `… error info`。补丁草案的「先让 Keeper 默认严格跑 / 不加该参数」结论不变，但理由要改成「默认已严格」。
2. **`testProguardFiles` 在 AGP 9.3.0 未被弃用**：`javap -v` 无 `Deprecated` 注解（R6 只按仓内使用推断，未验 API）。
3. **`android-37.0` 是硬事实而非推断**：本机 `platforms/` 只有 `android-37.0`，这使上游 #155 从「可能」变为「必然命中」，也让 fork `:299-315` 的回退成为选型的必要条件。

## 9. 待裁定（研究不代裁）

1. **D1** 是否采纳 Keeper 0.19.0（R-04 选项 A）；否则维持长期阻塞并登记「L8 侧无 AGP 公开 API 可用」。
2. **D2** 是否批准 §4.3 的 mapping 门 v2（旧 ② 会把可用构建判红）。
3. **D3** 测试 keep 案 A（保真度下降、改动最小）或案 B（收窄规则、需重跑 K5b/K6）。
4. **D4** 门禁阶段是否具备四个 `RELEASE_*` 变量以跑 K2；否则 WS9-55 只能出静态结论并记 UNVERIFIED。
