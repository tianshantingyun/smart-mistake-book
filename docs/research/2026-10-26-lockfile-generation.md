# Gradle 9.6.1 依赖锁定：多模块 + 版本目录的生成、配置缓存交互与 CI 校验

> 线：R3（批次 23 T1 / KD-72 驱动）｜日期：2026-10-26｜落点：本文件
>
> **本线未运行 Gradle 的任何任务**（纪律：门禁与锁生成由脚本串行执行），未改构建/生产代码，未做 git 写操作。
> 本轮实际执行过：`curl` 取 Gradle 9.6.1/8.x 官方文档、`gh` 读 issue/PR/CI 日志、`javap` 检查本地 Gradle 9.6.1
> 发行包、`python tools/ci/check_dependency_pins.py`（只读门，EXIT=0）。所有"在本仓库跑 `--write-locks` 会怎样"
> 的结论一律标 **UNVERIFIED**，并给出可复跑的判据。

---

> **2026-10-26 复核修复回填**：本文件 §1 的"仓库无任何 gradle.lockfile"是**生成前**事实。批次 23 工作树已生成 13 个未跟踪锁（§10），但生成树包含他线未提交的构建改动、且只覆盖 debugCompileClasspath；这批锁不能原样提交。KD-72 保持 open。生成/CI 命令以根 `build.gradle.kts` 的 `lockAllDependencies` 为准。**本节之后的 §1/§5.3 旧文本保留为生成前研究记录，§10 是回填后的现状。**

---

## 0. 结论摘要（先给可执行结论）

| 问题 | 结论 |
|---|---|
| **生成用哪些任务** | **每个 subproject 各跑一次 `dependencies`**：`:app:dependencies`、`:core:*:dependencies`、`:feature:*:dependencies`、`:benchmark:dependencies`，共 **14 个任务**，一次调用传入。`dependencies` 会解析本 project 内所有"可声明、或继承自可声明配置"的可解析配置，因此 **主源、unitTest、androidTest、lint、KSP/编译插件 classpath 预期一次覆盖**（以实际生成的锁文件为准），不需要再单独跑 test 任务。root 不计（root 只有 plugins 块，`subprojects` 也不含 root）。 |
| **是否会超大输出** | 会。`dependencies` 是 report task，**`-q` 不会关掉报告输出**（Gradle #21445，维护者明确不做）；把 stdout 重定向到日志文件即可，但内存/CPU 成本仍在（Android 上偏重，#30480）。想要"完全无报告"的替代是文档里的 `resolveAndLockAll`，但它 **`notCompatibleWithConfigurationCache`**，且历史上对 Android 不适用（8.10 文档原话 "does not work for Android projects"）——只作试点，不作首选。 |
| **锁文件命名/位置** | 每个 project/subproject 根目录一个 **`gradle.lockfile`**；buildscript classpath 才有 `buildscript-gradle.lockfile`。本仓库预期 **14 个** `gradle.lockfile`（root 无；`:core:markdown` 是否入库见 §6 P5），必须入库。 |
| **LockMode 怎么选** | **保持默认 `LockMode.DEFAULT`**：有锁状态就严格校验（多/少/版本不符都失败），缺锁状态放行。`STRICT` 额外要求"已锁配置必须有锁状态"，适合防新模块漏锁，但在本仓库先只做探针；`LENIENT` 只钉 dynamic 版本、允许依赖增减，**不能用于供应链门**。 |
| **CI 校验形式** | **`--write-locks` 后看 `git status --porcelain -- '*.lockfile'`**（写锁-比 diff），并加一个"每个 included project 都有锁文件"的计数检查。`--update-locks` 是**定向更新器**，不是漂移检测器：它只更新被点名的模块，仍需 diff，且不会清理未解析配置里的陈旧条目。 |
| **配置缓存交互** | 锁文件是 CC 的**构建配置输入**；`--write-locks` / `--update-locks` 会让 CC **永不复用、强制重新 store**（Gradle 9.6.1 源码 `DefaultConfigurationCache.kt`）。生成步骤建议显式 `--no-configuration-cache`；CI 常规构建保持 CC 开启。 |
| **本仓库最大风险** | Gradle **9.6.x 在锁定生效时可能改变 runtime classpath 顺序**（#38057，9.6.1 补丁未修，计划 9.7.0）；`:core:markdown` 仍是未跟踪目录；`:core:data` 有构建期派生 AAR + 自定义 `litertApiUpstream`；KGP 在并行解析时有已知 race（#31875）；`app` 的 release 变体有签名门，不能用 assemble 系列生成锁。 |

---

## 1. 本仓库现状（本轮实读/实跑）

| 事实 | 证据 |
|---|---|
| Gradle wrapper **9.6.1**（distributionSha256Sum 已钉） | `gradle/wrapper/gradle-wrapper.properties:3-4` |
| 配置缓存开、并行开 | `gradle.properties:2-3` |
| **14 个 subproject + root**（15 个 Gradle project） | `settings.gradle.kts:19-34` |
| `subprojects { dependencyLocking { lockAllConfigurations() } }` | `build.gradle.kts:37-40` |
| 根 `configurations.configureEach` 把 `kotlin-stdlib` 压回 catalog 的 `kotlin`（2.3.21） | `build.gradle.kts:51-61` |
| AGP 9.3.0 / Kotlin 2.3.21 / KSP 2.3.10 | `gradle/libs.versions.toml:2-3,18` |
| ~~**仓库无任何 `gradle.lockfile`**~~ **生成前**：`git ls-files '*gradle.lockfile'` → 0；`app/core/*/feature/*/benchmark` 下 `ls` 无命中。**批次 23 生成后（§10）**：工作树有 13 个未跟踪项目锁（含 `:core:markdown`），`:benchmark` 仍无锁；不能原样提交 | 本轮 `git ls-files '*gradle.lockfile'` → 0；§10 |
| 当前 pin gate 真绿 | 本轮 `python tools/ci/check_dependency_pins.py` → `EXIT=0`，`resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']` |
| `:core:markdown` 是未跟踪目录（含 build.gradle.kts/src/build） | 本轮 `git status --porcelain -- core/markdown` → `?? core/markdown/` |
| CI 的 `paths` 已包含锁文件 | `.github/workflows/android-check.yml:15-17` |
| CI 目前**没有**锁文件专项 job/step；常规构建只在"解析到该配置"时才隐式校验 | `.github/workflows/android-check.yml:70-125` |
| KD-72 仍 open（locking 只有声明、无锁状态） | `docs/known-defects.md:2671-2681` |
| 根注释把当前默认模式写成 `LENIENT` | `build.gradle.kts:22` —— **事实错误**，默认是 `DEFAULT`（见 §2.4）；"缺状态不阻塞"的结论本身正确 |
| 研究文档 §2.6 的根命令 `./gradlew dependencies --write-locks` 在多项目下**只跑 root** | `docs/research/2026-10-26-dependency-governance.md:154`；官方 9.6.1 明确写 "in a multi project setup, `dependencies` is executed only on one project, typically the root project"。根 `build.gradle.kts:24-29` 的 14 任务写法才是对的 |

---

## 2. Q1：Gradle 9.6.1 的 locking / lockAllConfigurations 正确用法

### 2.1 `lockAllConfigurations()` 到底锁了什么

官方 9.6.1 原文（<https://docs.gradle.org/9.6.1/userguide/dependency_locking.html>）：

> "Only configurations that can be resolved will have lock state attached to them. Applying locking on non resolvable-configurations is a no-op."
>
> "The above will lock all **project** configurations, but not the **buildscript** ones."

`lockAllConfigurations()` 的实现是 `ConfigurationContainer.configureEach { resolutionStrategy.activateDependencyLocking() }`（本地 Gradle 9.6.1 发行包 `gradle-dependency-management-9.6.1.jar` 内 `DefaultDependencyLockingHandler` 的 `javap -c` 可见 `invokeinterface ConfigurationContainer.configureEach`）。因此：

- 对**之后才创建**的配置也生效（AGP 的变体配置、KSP/Kotlin 插件配置、`core:data` 的 `litertApiUpstream` 都在脚本求值后才出现 → 都被激活锁定）。
- **它是 per-project 的扩展**：写在 root 的 `dependencyLocking` 块里只作用于 root（本仓库 root 没有项目依赖 → 等于没锁）。本仓库写在 `subprojects {}` 里才是对的；新增模块若不在 `subprojects` 范围内，需要单独配置。
- 不可解析的配置（`implementation`、`api`、`ksp` 等纯声明面）被激活也是 no-op。
- **buildscript / settings 的插件 classpath 不在范围内**（见 §2.7）。本仓库插件全部走 `pluginManagement` + 版本目录，故插件解析链**没有**被锁。

### 2.2 生成：`--write-locks` 只写"本次构建真正解析过"的配置

官方原文：

> "To generate or update the lock state, add the `--write-locks` argument while invoking whatever tasks that would trigger the locked configurations to be resolved."
>
> "This will create or update the lock state for **each resolved configuration during that build execution**. If a lock state already exists, it will be overwritten."
>
> "Gradle won't write the lock state to disk if the build fails, preventing the persistence of potentially invalid states."

三个直接后果：

1. **不要用 `assemble` / `test` 任务图来生成**：那些任务只解析它们用到的配置（release/androidTest/lint/KSP 可能漏），而且本仓库 `app` 的 `assemble*Release` 会依赖 `validateReleaseSigning`（`app/build.gradle.kts:147-153`），无签名环境变量直接失败，**整个 `--write-locks` 不落盘**。
2. **按官方口径，构建失败时锁状态不会落盘**（"Gradle won't write the lock state to disk if the build fails"）。所以首次生成先单点排查，再一次跑全量；不要把 `--continue` 当作"先写成功部分"的手段。
3. `--write-locks` 会**覆盖**已有状态，但**只覆盖它解析到的 lock id**；未解析到的条目会被原样写回（`DefaultDependencyLockingProvider.buildFinished` 把整个 `allLockState` 写盘；`persistResolvedDependencies` 只更新本次解析的 lock id）。所以"删除某个依赖后只跑窄任务"会留下陈旧条目，最终在别的配置解析时炸（也见 open issue #39081：已读入的锁状态会覆盖删文件操作）。**生成与 CI 校验必须用同一个任务集合。**

### 2.3 锁文件命名、位置、格式

官方原文：

> "The lock state is preserved in a file named `gradle.lockfile`, located at the root of each project or subproject directory. The exception is the lockfile for the buildscript itself, which is named `buildscript-gradle.lockfile`."

格式（官方示例）：

```
# This is a Gradle generated file for dependency locking.
# Manual edits can break the build and are not advised.
# This file is expected to be part of source control.
com.google.code.findbugs:jsr305:3.0.2=classpath
...
empty=annotationProcessor
```

- 每行 `group:artifact:version=配置A,配置B`；`empty=` 行列出"已锁但解析为空"的配置。
- 条目与配置名按字母序，便于 diff。
- 可通过 `dependencyLocking { lockFile = ... }` 改名/改位置（本仓库不需要）。

本仓库预期路径（14 个，全部入库）：

```
app/gradle.lockfile
core/model/gradle.lockfile
core/domain/gradle.lockfile
core/database/gradle.lockfile
core/data/gradle.lockfile
core/export/gradle.lockfile
core/ui/gradle.lockfile
core/markdown/gradle.lockfile      # 条件：该模块是否随本批入库，见 §6 P5
feature/capture/gradle.lockfile
feature/review/gradle.lockfile
feature/tutor/gradle.lockfile
feature/library/gradle.lockfile
feature/profile/gradle.lockfile
benchmark/gradle.lockfile
```

root 不写（root 没有 `dependencyLocking`，也没有项目依赖）。`buildscript-gradle.lockfile` 不会出现（未开启 buildscript classpath 锁定）。

### 2.4 LockMode：默认是 `DEFAULT`，不是 `LENIENT`

Gradle 9.6.1 的 `LockMode`（`subprojects/core-api/src/main/java/org/gradle/api/artifacts/dsl/LockMode.java`）原文：

- `DEFAULT`："will load the lock state and verify resolution matches it"
- `STRICT`："in addition to the `DEFAULT` behaviour, will fail resolution if a locked configuration does not have lock state defined"
- `LENIENT`："will load the lock state, to anchor dynamic versions, but otherwise be lenient about modifications of the dependency resolution, allowing versions to change and module to be added or removed"

默认值已由字节码确认：`DefaultDependencyLockingProvider` 构造器里 `lockMode.convention(LockMode.DEFAULT)`（本地 9.6.1 jar `javap -c`）。因此：

| 模式 | 有锁状态 | 无锁状态 | 适用 |
|---|---|---|---|
| `DEFAULT`（当前） | 版本/模块增减不符 → **失败** | 放行 | **首批落地的推荐模式** |
| `STRICT` | 同 DEFAULT | **失败**（"Missing lock state for configuration ..."） | 锁状态覆盖完整后再开；本仓库先做探针 |
| `LENIENT` | 只钉 dynamic 版本，允许依赖增减 | 放行 | 不用于供应链门 |

**修正**：`build.gradle.kts:22` 的 "LENIENT 模式下锁门未生效" 应改为 "默认 `DEFAULT` 模式下，缺锁状态不触发校验，因此锁门未生效"。结论不变，但模式名会误导后来人。

**为什么本仓库先不要 `STRICT`**：`dependencies` 报告任务的默认集合不是字面意义上的"所有可解析配置"，而是"可声明、或继承自可声明配置"的子集（源码 `AbstractDependencyReportTask.getConfigurationsWithDependencies()` → `ConfigurationInternal.isDeclarableByExtension()`；见 §4.2）。AGP/KGP/Kover/JaCoCo 会创建一些工具配置；本仓库还有自定义 `litertApiUpstream`（`core/data/build.gradle.kts:104-114`）。这些配置里只要有一个"被 `lockAllConfigurations()` 激活、实际构建会解析、但生成时没有拿到锁状态"，`STRICT` 就会在构建期红，而 `DEFAULT` 只会让它保持未锁。**先用 DEFAULT 落地，另起一次 STRICT 探针（§5.6）再决定。**

### 2.5 `--update-locks` 的语义与边界

官方原文：

> "It takes a comma (`,`) separated list of module notations. In this mode, the existing lock state is still used as input to resolution, filtering out the modules targeted by the update."

通配符只允许出现在 group 或 name 的**末尾**：

- `org.apache.commons:*`（整组）
- `*:guava`（任意 group 的 guava）
- `org.springframework.spring*:spring*`（前缀）

关键点：

- 它是**定向更新器**，不是"漂移检测器"。CI 里跑 `--update-locks` 只会静默改锁，仍要靠 `git diff` 才能发现；而且它**不会**处理未被点名模块的漂移。
- 一次直接依赖升级可能牵动传递依赖；`--update-locks` 允许解析规则顺带更新其他模块，但**不保证删除**新版本不再需要的旧传递条目。依赖图变化大时，用完整 `--write-locks` 重写更稳。
- 与 `--write-locks` 同时给时，按 `--update-locks` 的局部更新语义处理（CC 源码把 `isWriteDependencyLocks` 定义为 `writeLocks && !isUpdateDependencyLocks`）。不要混用。

### 2.6 版本目录（`gradle/libs.versions.toml`）的关系

- catalog 只提供**请求版本**；锁文件记录**解析结果**。catalog 本身不存在"锁"这一说。
- 改 catalog / 加减依赖而不重新生成锁 → 在**解析到该配置的构建**里按 `DEFAULT` 失败（有锁状态时）。这正是想要的闸门。
- 本仓库的 `quickjs-kotlin-stdlib = 2.4.10` 只是记录上游 POM 事实；真正解析出的 stdlib 由根 `build.gradle.kts:51-61` 的 `useVersion` 决定。生成后 `core/data/gradle.lockfile` 里应出现 `org.jetbrains.kotlin:kotlin-stdlib:2.3.21`（若出现 2.4.10，说明规则没吃到，需先查再入库）。
- **catalog 更新与锁更新的顺序**：先改 catalog → 跑完整生成 → 审 diff → 一起提交。只提交 catalog 会让 CI 的锁检查红（预期）。

### 2.7 locking 不覆盖什么（本仓库的残余面）

| 不覆盖 | 证据/说明 | 本仓库影响 |
|---|---|---|
| buildscript classpath（含 `pluginManagement` 解析的插件） | 官方："project configurations, but not the buildscript ones" | 插件版本本身由 catalog 钉死；插件**传递依赖**没有锁。可接受，登记为残余风险 |
| settings 的插件解析 | 同上（settings buildscript 是另一层） | 同上 |
| project 间依赖 | 锁状态只持久化 `ModuleComponentIdentifier`（`DefaultDependencyLockingProvider.persistResolvedDependencies` 字节码签名） | 模块图不锁；不影响外部版本漂移 |
| file / 本地 AAR 依赖 | 同上（不是 module component） | `core:data` 的派生 `litert-api` AAR 不进锁；字节由 `gradle/litert-patched/README.md` 的 sha256 台账管 |
| source dependencies / detached configurations | 官方："Dependency locking does not currently apply to source dependencies."；detached 配置不在 `project.configurations` | 本仓库未发现 detached 配置；AGP 内部若使用则无法锁 |

---

## 3. Q2：`--write-locks` 与 configuration cache 的已知交互

### 3.1 锁文件是 CC 的构建配置输入

Gradle 9.6.1 配置缓存文档（<https://docs.gradle.org/9.6.1/userguide/configuration_cache.html>，"Build Configuration Inputs"）：

> "Gradle configuration files (Version Catalogs, dependency verification files, **dependency lock files**, `gradle.properties` files)"

含义：锁文件是配置输入；**如果生成动作改变了锁文件，下一次构建会因为输入指纹变化而重新配置**（至少一次）。无论 CC 是否复用，锁状态的正确性不受影响。

### 3.2 `--write-locks` / `--update-locks` 会让 CC 永不复用

Gradle 9.6.1 源码 `platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/DefaultConfigurationCache.kt:442-460`（节选）：

```kotlin
startParameter.isWriteDependencyLocks -> {
    val description = formatBootstrapSummary(
        "%s as configuration cache cannot be reused due to %s",
        ..., "--write-locks"
    )
    ...
    Store.withDescription(description)
}
startParameter.isUpdateDependencyLocks -> { ... "--update-locks" ... Store.withDescription(description) }
```

即：**带 `--write-locks` 的构建一定重新算 task graph、重新配置，并（默认）存一个新的 CC 条目**。`ConfigurationCacheStartParameter.kt:140-141` 也把二者列为独立开关。外部实证（home-assistant/android 的真实 CI 日志，Gradle 9.8.2）：

```
Calculating task graph as configuration cache cannot be reused due to --write-locks
...
BUILD SUCCESSFUL in 44s
Configuration cache entry stored.
```

（run：<https://github.com/home-assistant/android/actions/runs/40261405327>。）

**建议**：生成步骤显式加 `--no-configuration-cache`。理由不是正确性（CC 开也能写），而是**避免为一次"写完就作废"的构建序列化巨大的解析状态**（Android 工程上耗时/内存明显，#30480 的复现者把这点列为 CI 主要成本）。

### 3.3 文档里的 `resolveAndLockAll` 明确不是 CC 兼容任务

官方 9.6.1 的锁定文档给出自定义任务：

```kotlin
tasks.register("resolveAndLockAll") {
    notCompatibleWithConfigurationCache("Filters configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "$path must be run from the command line with the `--write-locks` flag"
        }
    }
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}
```

配置缓存调试文档把它当作 **"Declare Incompatible Tasks"** 的示例，并写：

> "Gradle discards the configuration state at the end of the build if an incompatible task is executed."

所以：用它生成时，**显式 `--no-configuration-cache`** 最干净；即使 CC 开着，CC 状态也会在构建结束时丢弃。注意：官方 snippet 是**单项目形态**；本仓库必须把该任务注册在每个子项目（`subprojects {}`/`allprojects {}`）并用根聚合任务 `dependsOn` 各子项目的同名任务，只写在 root 会只解析 root（与 §4.2 的 root-only 失败同型）。

### 3.4 `dependencies` 报告任务本身与 CC

- 外部实证：home-assistant/android 的 `alldependencies --write-locks` 在 `org.gradle.configuration-cache=true` 下成功（同一条日志，各项目的 `:dependencies` 任务执行、CC entry stored、无 "not compatible" 问题）。
- 该实证跑在 Gradle 9.8.2 + Kotlin 2.4.21；**本仓库 9.6.1 + 2.3.21 未实测**。Gradle 9.6.1 的报告任务源码通过 `getProject().getConfigurations()` 取配置（`AbstractDependencyReportTask`）；版本不同，按纪律标 **UNVERIFIED**，由门禁轨第一次跑时确认。

### 3.5 已知的 KGP 并行 race（与本仓库直接相关）

Gradle #31875（closed，转 JetBrains KT-74241）：

> "After upgrading to 8.12 `resolveAndLockAll` ... has become flaky... Something has been appended to this collector already ... adding `--no-parallel` appears to be a good workaround."

栈在 `KotlinStdlibConfigurationMetrics.collectMetrics` / `addStdlibDependency`，属于 KGP 统计，不是 locking 本身。KT-74241 标记为 Duplicate、affected 2.1.0，**是否在 2.3.21 已修 UNVERIFIED**。生成命令建议先加 `--no-parallel`（同时让 report 输出不交错）；若确认已修，再恢复并行。

---

## 4. Q3：覆盖 14 个 submodule 的最小任务集合

### 4.1 最小集合 = 14 个 `:project:dependencies`

| # | 任务 | 模块类型 | 预期覆盖（主源 + 测试源） |
|---|---|---|---|
| 1 | `:app:dependencies` | Android application + baselineprofile；2 flavor × 4 buildType | `localFirst/strictOffline` × `debug/release/benchmark/internal` 的 `Compile/RuntimeClasspath`；`*UnitTest*`；`*AndroidTest*`；`*LintChecksClasspath`；`androidLintTool` |
| 2 | `:core:model:dependencies` | Kotlin JVM | `compileClasspath`、`runtimeClasspath`、`testCompileClasspath`、`testRuntimeClasspath`、`kotlinCompilerClasspath`（若被报告） |
| 3 | `:core:domain:dependencies` | Kotlin JVM + Kover | 同上 + Kover 工具配置（是否被报告见 §4.2） |
| 4 | `:core:database:dependencies` | Android library + KSP + Room3 + jacoco | `debug/release` 的 `Compile/RuntimeClasspath`、`UnitTest*`、`AndroidTest*`、`*LintChecksClasspath`、`ksp*ProcessorClasspath`、`kspPluginClasspath` |
| 5 | `:core:data:dependencies` | Android library + jacoco + 本地派生 AAR | 同上 + 自定义 `litertApiUpstream`（若被报告则锁 `litert-api:2.1.6`） |
| 6 | `:core:export:dependencies` | Android library | 标准 Android library 配置 |
| 7 | `:core:ui:dependencies` | Android library + Compose | 标准 + Compose 编译器插件 classpath（若被报告） |
| 8 | `:core:markdown:dependencies` | Android library + Compose（**未跟踪**） | 标准；是否随本批入库待裁定（§6 P5） |
| 9 | `:feature:capture:dependencies` | Android library + Compose | 标准 |
| 10 | `:feature:review:dependencies` | Android library + Compose | 标准 |
| 11 | `:feature:tutor:dependencies` | Android library + Compose | 标准 |
| 12 | `:feature:library:dependencies` | Android library + Compose | 标准 |
| 13 | `:feature:profile:dependencies` | Android library + Compose | 标准 |
| 14 | `:benchmark:dependencies` | `com.android.test` + baselineprofile（`targetProjectPath=":app"`） | `debug` 变体的 compile/runtime；baselineprofile 相关配置（具体名字以生成的锁文件为准） |

**为什么不需要再跑 test 任务**：

- 官方 9.6.1："Run `gradle dependencies --write-locks`: This command will **lock all resolvable configurations that have locking enabled**."
- 实现层面，`dependencies` 报告的默认集合是"`isCanBeDeclared()` 或 `extendsFrom` 到这样的配置"（`AbstractDependencyReportTask.getConfigurationsWithDependencies()`，v9.6.1 源码 137-145 行；`ConfigurationInternal.isDeclarableByExtension()` 153-161 行）。Java/Android 的 `testCompileClasspath`/`debugUnitTestRuntimeClasspath`/`debugAndroidTestRuntimeClasspath` 都 `extendsFrom` 对应的 `*Implementation`，所以在集合内。
- 外部同类工程实证：home-assistant/android 的 `common/gradle.lockfile` 里同时出现 `debugCompileClasspath`、`debugRuntimeClasspath`、`debugUnitTestCompileClasspath`、`debugUnitTestRuntimeClasspath`、`debugAndroidTestCompileClasspath`、`debugAndroidTestRuntimeClasspath`、`debugLintChecksClasspath`、`kspReleaseKotlinProcessorClasspath` 等 50 个配置名（本轮下载 `https://raw.githubusercontent.com/home-assistant/android/main/common/gradle.lockfile` 统计）。
- **已知例外**：Gradle #21396（open）报告某些 Kotlin 依赖图下 `testCompileClasspath` 会漏传递项，导致后续编译报 "Did not resolve ... part of the dependency lock state"。本仓库若命中，按 §6 P8 处理，不能据此否定"`dependencies` 覆盖测试配置"这一总体结论。

**注意**：这里的"all"是官方口径；源码过滤器意味着不是字面上的"每一个可解析配置"。生成后要**按名字审一遍**锁文件里的配置集合，确认没有关键配置缺失（尤其 `kotlinCompilerPluginClasspath`、`ksp*`、`*AndroidTest*`）。缺了也不会在 `DEFAULT` 下报错——这是覆盖缺口，不是构建失败。

### 4.2 一条命令跑完：建议加聚合任务（不要硬编码 14 个路径）

官方明确 root 单跑 `dependencies` 只作用于一个 project：

> "In a multi project setup, note that `dependencies` is executed only on one project, typically the root project."

所以两种写法：

**A. 直接列 14 个任务（根注释现有写法，零改代码）**

```bash
./gradlew --no-configuration-cache --no-parallel --console=plain \
  :app:dependencies \
  :core:model:dependencies :core:domain:dependencies \
  :core:database:dependencies :core:data:dependencies \
  :core:export:dependencies :core:ui:dependencies :core:markdown:dependencies \
  :feature:capture:dependencies :feature:review:dependencies \
  :feature:tutor:dependencies :feature:library:dependencies \
  :feature:profile:dependencies :benchmark:dependencies \
  --write-locks > lockfile-generation.log 2>&1
```

**B. 根 `build.gradle.kts` 注册聚合任务（推荐）**

```kotlin
// 批次 23 R3：一条命令覆盖所有子项目；新模块自动纳入，避免手写 14 条路径漂移。
tasks.register("lockAllDependencies") {
    group = "dependency locking"
    description = "解析每个子项目的可锁配置（用 --write-locks 生成 gradle.lockfile）"
    setDependsOn(project.subprojects.flatMap { it.tasks.withType<DependencyReportTask>() })
}
```

```bash
./gradlew --no-configuration-cache --no-parallel --console=plain \
  lockAllDependencies --write-locks > lockfile-generation.log 2>&1
```

聚合任务**消灭的具体失败**：① 根 `dependencies` 只跑 root、14 个子项目静默无锁（官方口径 + #9373）；② 新增模块后手写任务列表漏项，锁门对新模块完全不生效。它不改变解析集合，只是把 14 个任务挂到一个名字下。（`DependencyReportTask` 在 Kotlin DSL 默认导入中；若 IDE 报未解析，显式 `import org.gradle.api.tasks.diagnostics.DependencyReportTask`。）

外部同类工程（home-assistant/android，Apache-2.0，3999 stars，2026-10-26 仍在推送）用的就是同一形态：

```kotlin
tasks.register("alldependencies") {
    setDependsOn(project.allprojects.flatMap { it.tasks.withType<DependencyReportTask>() })
}
```

CI 里 `./gradlew alldependencies --write-locks` + `git status --porcelain` 判红（`.github/workflows/pr.yml:90-106`）。可作高质量参考实现；本仓库不复制其代码，只采用形态。

### 4.3 如果一定要"无报告输出"：两条替代路径的取舍

| 方案 | 覆盖 | 输出 | 风险 | 结论 |
|---|---|---|---|---|
| **A. 14 × `dependencies --write-locks`** | 广（主/测试/AndroidTest/lint/ksp/工具 classpath，以实际锁文件为准） | 大；重定向即可，`-q` 无效 | Android 上内存/CPU 偏重（#30480）；首次可能较慢 | **首选** |
| **B. 文档 `resolveAndLockAll`（`configurations.filter { isCanBeResolved }`）** | 字面"全部可解析配置"，可能比 A 更广 | 无报告 | `notCompatibleWithConfigurationCache`；8.10 文档明说 "does not work for Android projects"（8.11 起删掉该句，但无 release note 证明已修复）；Gradle 维护者在 #30480 说 AGP 的配置需要 artifact view/transform；KGP 并行 race（#31875） | **只做试点**，`--no-configuration-cache --no-parallel`，不要作为首次落地 |
| **C. 构建任务图（`assemble*` / `test*` / `lint*`）** | 只覆盖被这些任务解析的配置；release/androidTest 易漏；无 flavor 组合覆盖 | 无报告 | 任务数远多于 14；本仓库 release 触发签名门；仍然漏 ksp/compiler classpath | 不推荐作为锁来源 |

**关于"无报告输出"的硬事实**：Gradle #21445 "No way to quiet `--write-locks`" 已 closed，维护者明确 "There is no intention of making report task silent when used with `-q`." 所以不要指望 `-q`；重定向日志是唯一不改变解析语义的做法。

**B2. 过滤版自定义 resolve 任务（只覆盖主/测试 classpath，无报告）**：如果输出/内存确实是硬约束，可以把文档的 `resolveAndLockAll` 收窄到 classpath 配置：

```kotlin
// 只作试点：DEFAULT 模式下未覆盖的配置保持未锁；不要与 STRICT 混用。
tasks.register("resolveClasspathsForLocking") {
    notCompatibleWithConfigurationCache("Filters configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "$path must be run with --write-locks"
        }
    }
    doLast {
        configurations.filter { c ->
            c.isCanBeResolved && (
                c.name.endsWith("CompileClasspath") || c.name.endsWith("RuntimeClasspath")
            )
        }.forEach { it.resolve() }
    }
}
```

运行：`--no-configuration-cache --no-parallel --write-locks`。它覆盖 `*CompileClasspath` / `*RuntimeClasspath`（含 `test*`/`androidTest*` 的 classpath），但**不覆盖** lint/KSP/compiler 工具配置；相较 A 是"更小但更窄"的集合。**必须注册在每个子项目**（`subprojects {}`）并用根聚合任务触发，否则只解析 root。**是否对 AGP 9.3 安全未验证**（维护者在 #30480 指出 AGP 有需要 artifact view/transform 的配置；filter 到 classpath 可避开大部分，但不是证明）。首次落地仍建议 A；B2 只在 A 的输出/内存被证明不可接受时试点。

### 4.4 锁文件会有多大（外部标尺，非本仓库实测）

home-assistant/android 的锁文件（本轮下载统计）：

| 文件 | 大小 | 非空依赖行 | 非空配置名数 | `empty=` 配置数 |
|---|---|---|---|---|
| `app/gradle.lockfile` | 513,835 B | 703 | 87 | 54 |
| `wear/gradle.lockfile` | 205,048 B | 616 | 52 | 31 |
| `common/gradle.lockfile` | 156,830 B | 513 | 50 | 26 |

本仓库 release 解析图约 166 个唯一坐标（`docs/research/2026-10-26-dependency-governance.md:40`），规模小于该工程；预计 13-14 个文件总计在 **数百 KB 量级**（app 最大），但这是**估算**，以实际生成为准。锁文件是文本、按字母序，适合入库；`:core:markdown` 若不入库，其锁文件也不能提交。

---

## 5. Q4：生成命令块与 CI 校验步骤

### 5.1 首次生成（门禁轨执行；本线未跑）

```bash
# 前置：JDK 21 + Android SDK 37/build-tools 37（AGP 在配置期就要 SDK）
# 说明：--no-configuration-cache 避免为一次性构建序列化 CC；
#       --no-parallel 避开 KGP 解析 race（#31875）并让日志可读。
./gradlew --no-configuration-cache --no-parallel --console=plain \
  lockAllDependencies --write-locks > lockfile-generation.log 2>&1
echo "EXIT=$?"

# 审产物（不要直接提交）
git status --porcelain -- '*.lockfile'
wc -l app/gradle.lockfile core/*/gradle.lockfile feature/*/gradle.lockfile benchmark/gradle.lockfile

# 关键不变量抽查
grep -n "org.jetbrains.kotlin:kotlin-stdlib:2.3.21" app/gradle.lockfile core/data/gradle.lockfile
grep -n "quickjs-kt-android" core/data/gradle.lockfile
grep -n "litert-api" core/data/gradle.lockfile        # 若 litertApiUpstream 被报告
grep -n "AndroidTest\|UnitTest\|LintChecks" app/gradle.lockfile | head
```

若还没加聚合任务，把 `lockAllDependencies` 换成 §4.2-A 的 14 个 `:mod:dependencies`。

**失败处理**：按官方口径，构建失败时锁状态**不会落盘**。先按报错模块单独跑 `:mod:dependencies` 定位；不要用 `--continue` 指望"先写成功的部分"。

### 5.2 CI 校验（写锁-比 diff）

推荐把锁检查做成 `.github/workflows/android-check.yml` 的一个独立 job（或 `check` job 的第一个 Gradle step）。它需要 Android SDK（不能只跑 JVM）：

```yaml
- name: Dependency lockfiles are up to date
  run: |
    set -euo pipefail
    ./gradlew --no-configuration-cache --no-parallel --console=plain \
      lockAllDependencies --write-locks > /tmp/lockfiles.log 2>&1 || {
        tail -n 200 /tmp/lockfiles.log
        exit 1
      }
    changed=$(git status --porcelain -- '*.lockfile')
    if [ -n "$changed" ]; then
      echo "$changed"
      git diff -- '*.lockfile' | sed -n '1,200p'
      echo "::error::gradle.lockfile 与解析结果不一致：请运行 ./gradlew lockAllDependencies --write-locks 并提交锁文件"
      exit 1
    fi
```

要点：

1. **必须用 `git status --porcelain`，不能只用 `git diff --exit-code`**：新增模块/新增配置会生成**未跟踪**的 `gradle.lockfile`，`git diff` 看不见。
2. **`--write-locks` 是检测手段，不是修复手段**：CI 不自动提交；开发者本地跑同一命令并提交 diff。若要自动更新，仿 home-assistant 的 `renovate-lockfiles.yml` 单独做（本仓库暂无此需求）。
3. **CI 常规构建保持 CC 开启**（不动 `gradle.properties`）；锁状态会在 `assembleLocalFirstDebug`、单测、lint、`assembleLocalFirstRelease` 等解析到对应配置时自动校验。锁检查 job 是**补盲**（尤其新增模块/新增配置），不是替代。
4. **触发路径已就绪**：`.github/workflows/android-check.yml:15-17` 已监听 `**/gradle.lockfile`，只需加 step。
5. **锁检查 job 的耗时**：home-assistant 大工程 44s（含依赖缓存）；本仓库预计同量级或更低，**UNVERIFIED**。若担心每个 PR 都跑，可用 `dorny/paths-filter` 只在 `gradle/libs.versions.toml` / `**/*.kts` / `**/gradle.lockfile` 变化时执行；但锁检查本身也能预热依赖缓存，直接放在 `check` job 里更简单。

### 5.3 每个 included project 都有锁文件的计数检查（低成本补盲）

> **2026-10-26 回填**：下面硬编码 `expected=13` 的旧方案已被证伪——13 个锁包含未跟踪的 `:core:markdown` 却漏掉 `:benchmark`（同一数字既"多"又"少"）。改用 `tools/ci/check_lockfiles.py`：它按 `settings.gradle.kts` 的 include 列表逐个要求锁，并在锁落地后强制 unitTest/androidTest/runtime 配置；锁尚未落地时只提示（不假绿）。旧片段保留为研究记录。

```bash
# 旧方案（已弃用；被 tools/ci/check_lockfiles.py 取代）
# :core:markdown 未入库时先按 13 计；模块落地后改回 14
expected=13   # 或 14（含 :core:markdown）
actual=$(git ls-files '*gradle.lockfile' | wc -l | tr -d ' ')
[ "$actual" -eq "$expected" ] || {
  echo "expected $expected gradle.lockfile files, found $actual"
  git ls-files '*gradle.lockfile'
  exit 1
}
```

这条检查**消灭的具体失败**：新模块被 include 但没有锁文件，而 `DEFAULT` 下所有构建都静默通过（锁门对新模块形同不存在）。硬编码计数的失败是：它只数文件个数，不核对"哪个 project 缺、哪个 project 多"，因此 `:core:markdown` 的未跟踪锁可以掩盖 `:benchmark` 的缺口（复核发现 #6）。新脚本按 include 列表核对并检查配置覆盖。

### 5.4 定向更新（开发者日常）

```bash
# 只更新某个模块（逗号分隔；通配符只允许尾部）
./gradlew --no-configuration-cache --no-parallel lockAllDependencies \
  --update-locks org.jetbrains.kotlin:kotlin-stdlib

# 升级 catalog 里某个直接依赖后，建议直接完整重写，避免陈旧传递条目
./gradlew --no-configuration-cache --no-parallel lockAllDependencies --write-locks
git diff -- '*.lockfile'
```

`--update-locks` 之后仍要跑一次完整 `--write-locks` 并看 diff，原因见 §2.2/§2.5（未解析配置的条目不会被清理）。

### 5.5 负向控制（证明锁门真的会红）

在临时分支上做，做完丢弃：

1. **改 catalog 版本不重生成**：把 `gradle/libs.versions.toml` 的 `core-ktx` 改一个 patch 版本，跑 CI 的 assemble/单测 → 预期 `Dependency lock state out of date`（只要该配置被解析）。
2. **加依赖不重生成**：在 `:core:model` 加一个 `implementation` → 预期解析期失败（"Resolved ... which is not part of the dependency lock state"）。
3. **删依赖不重生成**：删掉一条 → 预期失败（"Did not resolve ... which is part of the dependency lock state"）。
4. **`--update-locks` 定向**：只更新 `androidx.core:core-ktx`，检查 diff 只含目标 + 被解析规则牵动的传递项。
5. **恢复现场**：删除临时改动，重新生成并提交。

### 5.6 STRICT 探针（可选，不提交）

锁文件入库且 CI 绿后，在本地临时把 `build.gradle.kts:38-40` 改成：

```kotlin
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT   // 需要默认导入 org.gradle.api.artifacts.dsl.LockMode
}
```

然后**跑完整 CI 任务矩阵**（不只是 `dependencies`）：单测、`assemble*Debug`、`assemble*Release`（带测试签名）、lint、`:benchmark:assemble`、以及 `:core:data:patchLitertApiAar`。若出现 "Missing lock state for configuration ..."，把配置名记下来：
- 若该配置本应锁（运行期依赖），把它纳入生成任务/过滤器后重试；
- 若它是工具/内部配置且不值得锁，用 `resolutionStrategy.deactivateDependencyLocking()` 或 `unlockAllConfigurations()` 局部关掉（home-assistant 的 `lint/build.gradle.kts` 就是先例：**"Unlocks configuration since it doesn't work with the lint configuration and doesn't generate the lockfile for the lint targets"**）。
- 只有在整套矩阵无 missing-state 后，才考虑把 STRICT 入库。

---

## 6. 预判本仓库（AGP 9.3 + Kotlin 2.3.21）的坑

> 状态标记：**[实证]** = 本轮读到的官方/外部事实；**[预测]** = 由源码/构建文件推导，本仓库未跑；**[待验证]** = 需要门禁轨实跑。

### P1. Gradle 9.6.x 在锁定生效时会改变 runtime classpath 顺序 **[实证 + 预测]**

Gradle #38057（`lockAllConfigurations()` changes runtime classpath artifact ordering in 9.6.0-RC1，closed as duplicate #29275）：

- 9.6.0 的解析引擎变更 + locking 注入的 strict 约束，会把某些 fat-jar 与细粒度构件在 classpath 上的顺序改变；复现者得到 `NoSuchMethodError: ReferenceCountUpdater.setInitialValue`。
- 维护者："we will not revert the changes causing a different ordering in 9.6.0 compared to 9.5.1... you might have to skip 9.6.0"；修复计划在 **9.7.0**（feature preview，Gradle 10 默认）。
- 9.6.1 的 release notes 只列 3 个修复（non-interactive、artifact permissions、deadlock），**没有 ordering 修复**。

**对本仓库**：锁文件入库后，**release/benchmark/internal 变体必须做一次装机冒烟**（`assembleLocalFirstRelease` + 启动 + QuickJS/Room/ML Kit 路径）。现有 CI 已有 release 组装与设备轨；锁门落地后不要只跑单测。**[待验证]** 本仓库依赖图没有 netty 这类 fat-jar 冲突，风险可能低，但不能用"看起来没有"替代冒烟。

### P2. `dependencies` 的报告集合 ≠ 字面"所有可解析配置" **[实证 + 待验证]**

源码过滤器见 §4.2。`DEFAULT` 下漏掉的配置只是"不锁"，不会报错；`STRICT` 下才会在构建期暴露。

**验证方法**：生成后统计每个锁文件的配置名，和 AGP/KGP 在任务执行时实际解析的配置对照（最直接：在 CI 里跑一遍完整矩阵，开 STRICT 探针，收集 missing-state 列表）。**不要**在没有这份对照前声称"14 个模块已全覆盖"。

### P3. `:core:data` 的构建期派生 AAR 与自定义配置 **[预测 + 待验证]**

`core/data/build.gradle.kts:104-114` 创建 `litertApiUpstream`（`isCanBeConsumed=false`、`isCanBeResolved=true`、`isTransitive=false`），`:154-165` 注册 `PatchLitertApiAarTask`，`:186` 用 `implementation(files(patchLitertApiAar))` 把派生 AAR 放进 classpath。

可能的行为：

- `dependencies` 若把 `litertApiUpstream` 视为可声明/继承可声明配置，就会解析并锁 `com.google.ai.edge.litert:litert-api:2.1.6`；若没有，则它保持未锁。
- `dependencies` 报告如果仅为图解析、不解析 artifact，则不会触发 `patchLitertApiAar`；如果触发了，生成时会多跑一次 AAR 派生（需要 `litertApiUpstream` 能联网/命中缓存）。
- 本地 AAR 本身是 file dependency，**不会**进锁；字节由 `gradle/litert-patched/README.md` 的 sha256 台账管。

**判据**：生成后 `grep litert-api core/data/gradle.lockfile`；若没有且 `patchLitertApiAar` 在构建期会解析该配置，则 **不要开 STRICT**（否则 `:core:data:patchLitertApiAar` 会因 missing state 红）。

### P4. KGP 并行解析 race **[实证 + 待验证]**

#31875 / KT-74241：`resolveAndLockAll --write-locks --no-configuration-cache` 在 KGP 下偶发 `Something has been appended to this collector already`，`--no-parallel` 可绕过；受影响版本 2.1.0，2.3.21 是否已修未确认。

**处置**：生成命令固定 `--no-parallel`；若门禁轨实测并行也稳定，再恢复（不是必须）。

### P5. `:core:markdown` 未跟踪 **[实证]**

`settings.gradle.kts:27` 已 include，`core/markdown/` 仍是 `??`（本轮 `git status` 确认）。聚合任务会自动把它纳入生成：

- 若该模块的 build script 可配置，生成会产出 `core/markdown/gradle.lockfile`（未跟踪）。**锁文件不能单独提交一个未入库模块的锁**，否则 KD-72 的"每模块有锁"口径把未跟踪模块也算进去。
- 裁定二选一：① 该模块随本批入库（连同锁文件）；② 本批生成时 `-x :core:markdown:dependencies` 排除，锁文件计数按 13，等模块落地再补。**这是范围/所有权问题，应由主会话裁定**；本线只登记。

### P6. `app` 的 release 签名门 **[实证]**

`app/build.gradle.kts:147-153`：任何名字含 `Release` 且以 `bundle`/`assemble`/`package` 开头的任务都依赖 `validateReleaseSigning`，缺环境变量直接失败。

**后果**：不要用 `assembleRelease --write-locks` 生成；一个失败会让整次 `--write-locks` 不落盘。CI 的 release 步骤自己造了临时 keystore（`.github/workflows/android-check.yml:86-97`），锁生成步骤不应依赖它。

### P7. 锁文件会把 Kotlin/KSP/Compose 工具链 classpath 一起钉住 **[预测]**

home-assistant 的锁文件包含 `kotlinCompilerClasspath`、`kotlinCompilerPluginClasspath`、`kspPluginClasspath`、`androidLintTool`。本仓库 Android 模块走 **AGP 9 built-in Kotlin**（`docs/research/2026-10-26-dependency-governance.md:38`），这些配置是否存在、名字是否相同 **UNVERIFIED**。

**后果**：若被锁，Kotlin/KSP/Compose 插件升级必须重生成锁文件（预期的工作流成本）；若没被锁，工具链版本由 AGP/KGP 版本钉住（仍然是可复现的，只是不经过锁门）。生成后检查一下配置名，别把"没看到"当成"不存在"。

### P8. `testCompileClasspath` 漏传递依赖的上游 open issue **[实证 + 待验证]**

Gradle #21396（open，2022 起）：`resolveAndLockAll --write-locks` 写出的锁在 `testCompileClasspath` 上漏 `org.jetbrains:annotations:13.0` 等传递项，随后 `compileTestJava` 报 "Did not resolve ... part of the dependency lock state"；9.1 nightly 仍复现。评论里的 workaround 是对缺失模块跑 `--update-locks`。

本仓库 Kotlin 模块多，**有可能**遇到类似"同一模块在 runtime 锁到高版本、compile 需要低版本"的条目。**验证**：锁生成后跑全量单测/编译；若出现 "Did not resolve ... part of the dependency lock state"，按缺失坐标 `--update-locks`，并把该配置记录到锁覆盖审计里。

### P9. 版本目录/锁文件更新顺序 **[实证]**

catalog 是请求版本来源，锁文件是解析结果。只改 catalog 不重生成 → CI 的锁检查 job 红（预期）。团队流程必须把"改依赖 → 跑 `lockAllDependencies --write-locks` → 提交锁文件"写进 AGENTS/贡献说明，否则每个依赖 PR 都会被拦一次。home-assistant 的 AGENTS.md 就是这么做的（`./gradlew alldependencies --write-locks`）。

### P10. 生成需要 Android SDK，而不仅是 JVM **[实证]**

AGP 在配置期就要 `compileSdk=37` / `buildToolsVersion="37.0.0"`（`app/build.gradle.kts:9-10`）。锁检查 job 必须像 `check` job 一样 `android-actions/setup-android@v4` + JDK 21（`.github/workflows/android-check.yml:44-52`），不能只 `setup-java`。

### P11. 锁文件的行尾/跨平台 diff **[低风险，实证]**

`.gitattributes` 有 `* text=auto`，但没有 `*.lockfile text eol=lf`；Windows 本地生成 + `core.autocrlf=true` 可能在提交时被规范化。Gradle 锁文件本身是文本、按行；Linux CI 生成的结果是基准。若要消除 Windows 本地 diff 噪音，可在 `.gitattributes` 加 `*.lockfile text eol=lf`（本线未改，登记建议）。不要把"本地生成后 git status 显示整个文件变更"误判为依赖漂移。

### P12. `dependencies` 的报告输出会淹没 CI 日志 **[实证]**

#21445：`-q` 不生效，维护者不打算改。外部 CI 日志里还能看到并行下多个 `> Task :app:dependencies` 行交错。**处置**：`--console=plain` + 重定向到 `/tmp/lockfiles.log`，失败时只 `tail -200`；不要试图用 `-q` 或自定义 no-op renderer 省输出——#30480 证明 no-op renderer 若自己不做 `resolve()`，**锁根本不会写**（没有解析就没有锁状态；CC 开启时另说，但那是另一层行为）。

---

## 7. KD-72 关闭判据（本线不能关，只给判据）

1. 14（或裁定后的 13）个 `gradle.lockfile` 已生成并**入库**；`git ls-files '*gradle.lockfile'` 计数与 included project 数一致。
2. 锁文件内可查到关键不变量：`kotlin-stdlib:2.3.21`（core/data 尤其）、`quickjs-kt-android:1.0.15`（core/data）、各模块的 `*UnitTest*`/`*AndroidTest*` 配置名存在（或明确登记为工具配置不锁）。
3. CI 的锁检查 job 绿：`--write-locks` 后 `git status --porcelain -- '*.lockfile'` 为空。
4. 负向控制 1-3（§5.5）按预期红。
5. 常规 CI（单测、assemble debug/release、lint、benchmark、设备轨）在锁文件存在时全绿；release/设备冒烟覆盖 P1 的顺序风险。
6. 在 `docs/known-defects.md` 把 KD-72 标 closed，并写明"生成命令 + 锁文件数 + CI job 名 + 未覆盖面（buildscript/plugin、file AAR、project 依赖）"。

**当前状态**：本线未跑 Gradle，以上 1-6 全部 **UNVERIFIED**；KD-72 保持 open。

---

## 8. 未验证 / 待验证清单

1. **UNVERIFIED**：在本仓库（Gradle 9.6.1 + AGP 9.3.0 + Kotlin 2.3.21；14 个 subproject / 15 个 Gradle project）跑 14 × `dependencies --write-locks` 的实际结果、耗时、内存、生成文件数与体积、是否有配置解析失败。
2. **UNVERIFIED**：AGP 9.3 built-in Kotlin 下 `kotlinCompilerClasspath` / `kotlinCompilerPluginClasspath` / Compose 编译器插件 classpath 是否出现在 `dependencies` 报告里、是否被锁。
3. **UNVERIFIED**：`:core:data` 的 `litertApiUpstream` 是否被 `dependencies` 报告/锁定；生成是否会触发 `patchLitertApiAar`。
4. **UNVERIFIED**：`:benchmark:dependencies`（`com.android.test` + baselineprofile）会解析哪些配置、是否触发 `:app` 相关构建。
5. **UNVERIFIED**：`dependencies --write-locks` 在 Gradle 9.6.1 + CC 开时是否无 problem（9.8.2 外部实证成功，版本不同）。
6. **UNVERIFIED**：Kotlin 2.3.21 是否已修复 #31875/KT-74241 的并行 race。
7. **UNVERIFIED**：`testCompileClasspath` 是否在本仓库命中 #21396 的漏项模式。
8. **UNVERIFIED**：Gradle 9.6.1 是否包含 #38057 的 order 修复（release notes 未列；按未修处理）。
9. **单一来源**：`resolveAndLockAll` "does not work for Android projects" 的依据是 Gradle 8.10 文档原话 + 维护者 2024 年评论（#30480）；8.11 起该句被删但无 release note 说明修复。**不能据此说"9.6.1 已支持 Android"**，只能标"待验证"。
10. **估算**：本仓库锁文件总体积/行数（基于 home-assistant 的实测比例推算，未生成）。

---

## 9. 来源

**Gradle 官方文档（本轮 curl 实读）**

- Dependency locking（9.6.1）— <https://docs.gradle.org/9.6.1/userguide/dependency_locking.html>
- Configuration cache（9.6.1，Build Configuration Inputs 含 dependency lock files）— <https://docs.gradle.org/9.6.1/userguide/configuration_cache.html>
- Configuration cache debugging（9.6.1，`notCompatibleWithConfigurationCache` 示例与 "discards the configuration state" 原文）— <https://docs.gradle.org/9.6.1/userguide/configuration_cache_debugging.html>
- Gradle 9.6.1 release notes（仅 3 个修复，未含 ordering）— <https://docs.gradle.org/9.6.1/release-notes.html>
- Gradle 9.6.0 release notes — <https://docs.gradle.org/9.6.0/release-notes.html>
- 8.10 版 locking 文档（"This does not work for Android projects" 原话）— <https://docs.gradle.org/8.10/userguide/dependency_locking.html>
- 8.11 版 locking 文档（该句已删，改为 "native builds"）— <https://docs.gradle.org/8.11/userguide/dependency_locking.html>

**Gradle 源码（v9.6.1，本轮 curl 实读；行号以该 tag 为准）**

- `AbstractDependencyReportTask.getConfigurationsWithDependencies()`（declarable-by-extension 过滤）— <https://raw.githubusercontent.com/gradle/gradle/v9.6.1/platforms/software/software-diagnostics/src/main/java/org/gradle/api/tasks/diagnostics/AbstractDependencyReportTask.java>
- `ConfigurationInternal.isDeclarableByExtension()` — <https://raw.githubusercontent.com/gradle/gradle/v9.6.1/platforms/software/dependency-management/src/main/java/org/gradle/api/internal/artifacts/configurations/ConfigurationInternal.java>
- `DefaultConfigurationCache.kt:442-460`（`--write-locks` / `--update-locks` 强制 store）— <https://raw.githubusercontent.com/gradle/gradle/v9.6.1/platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/DefaultConfigurationCache.kt>
- `ConfigurationCacheStartParameter.kt:140-141` — <https://raw.githubusercontent.com/gradle/gradle/v9.6.1/platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/initialization/ConfigurationCacheStartParameter.kt>
- `LockMode.java`（DEFAULT/STRICT/LENIENT 语义）— <https://raw.githubusercontent.com/gradle/gradle/v9.6.1/subprojects/core-api/src/main/java/org/gradle/api/artifacts/dsl/LockMode.java>
- `configuration_cache_debugging.adoc:211-228`（不兼容任务示例）— <https://raw.githubusercontent.com/gradle/gradle/v9.6.1/platforms/documentation/docs/src/docs/userguide/optimizing-builds/configuration-cache/configuration_cache_debugging.adoc>

**本地 Gradle 9.6.1 发行包（本轮 `javap -c` 实读）**

- `gradle-dependency-management-9.6.1.jar` 内 `DefaultDependencyLockingHandler`：`lockAllConfigurations()` → `ConfigurationContainer.configureEach`。
- 同 jar 内 `DefaultDependencyLockingProvider`：`lockMode.convention(LockMode.DEFAULT)`；`buildFinished()` 写整个 `allLockState`；`persistResolvedDependencies` 只接收 `ModuleComponentIdentifier`。
- 路径：`<repo>/.gradle/wrapper/dists/gradle-9.6.1-bin/6y99habfxa15k7q92hjsszivc/gradle-9.6.1/lib/plugins/gradle-dependency-management-9.6.1.jar`（wrapper 配置见 `gradle/wrapper/gradle-wrapper.properties:3-4`）。

**GitHub issues / PR（本轮 `gh` 实读）**

- #30480 写锁在 Android 上的内存/CPU 成本；维护者："AGP ... exotic configuration setups ... artifact views and transforms" — <https://github.com/gradle/gradle/issues/30480>
- #31875 dependency locking 与 KGP 的并行 race；`--no-parallel` workaround — <https://github.com/gradle/gradle/issues/31875>；YouTrack KT-74241 — <https://youtrack.jetbrains.com/issue/KT-74241>
- #38057 9.6.0 锁定时 classpath 顺序变化（duplicate #29275，计划 9.7.0）— <https://github.com/gradle/gradle/issues/38057>、<https://github.com/gradle/gradle/issues/29275>
- #9373 多项目 `dependencies` 只跑 root（文档已按此更新）— <https://github.com/gradle/gradle/issues/9373>
- #21445 `-q` 不能静默依赖报告（closed，won't fix）— <https://github.com/gradle/gradle/issues/21445>
- #39081 已读入的锁状态在 buildFinished 写回，删除锁文件不生效（open）— <https://github.com/gradle/gradle/issues/39081>
- #21396 `testCompileClasspath` 漏传递项（open，9.1 nightly 仍复现）— <https://github.com/gradle/gradle/issues/21396>

**高质量同类实现（外部实证，Apache-2.0）**

- home-assistant/android 根 `build.gradle.kts`：`dependencyLocking { lockAllConfigurations() }` + `alldependencies`（聚合所有 `DependencyReportTask`）— <https://raw.githubusercontent.com/home-assistant/android/main/build.gradle.kts>
- `lint/build.gradle.kts`：`unlockAllConfigurations()` 及原因注释 — <https://raw.githubusercontent.com/home-assistant/android/main/lint/build.gradle.kts>
- CI 写锁 + `git status` 判红 — <https://raw.githubusercontent.com/home-assistant/android/main/.github/workflows/pr.yml>；自动更新锁 — <https://raw.githubusercontent.com/home-assistant/android/main/.github/workflows/renovate-lockfiles.yml>
- CI 实跑日志（CC 因 `--write-locks` 不复用、BUILD SUCCESSFUL 44s、CC entry stored）— <https://github.com/home-assistant/android/actions/runs/40261405327>
- 锁文件体量/配置覆盖样本 — <https://raw.githubusercontent.com/home-assistant/android/main/app/gradle.lockfile>、<https://raw.githubusercontent.com/home-assistant/android/main/common/gradle.lockfile>、<https://raw.githubusercontent.com/home-assistant/android/main/wear/gradle.lockfile>

**仓库内**

- KD-72 — `docs/known-defects.md:2671-2681`
- 依赖治理前序研究（§2.4/§2.6/§7）— `docs/research/2026-10-26-dependency-governance.md:111-164,420`
- 批次 23 基线（§8.4 `:core:markdown` 冲突、§5 锁现状）— `docs/research/2026-10-26-batch-23-baseline.md:157-178,281-284`
- 根构建 — `build.gradle.kts:13-61`
- wrapper / CC / 并行 — `gradle/wrapper/gradle-wrapper.properties:3-4`、`gradle.properties:2-3`
- 模块清单 — `settings.gradle.kts:19-34`
- 签名门 — `app/build.gradle.kts:147-153`
- `:core:data` 派生 AAR — `core/data/build.gradle.kts:104-114,154-165,186`
- `:core:database` KSP/Room3/jacoco — `core/database/build.gradle.kts:1-6`
- CI 路径/任务 — `.github/workflows/android-check.yml:15-17,44-52,70-125`
- 本轮 pin gate — `python tools/ci/check_dependency_pins.py` → `EXIT=0`，`resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`

---

## 10. 批次 23 实际生成结果与提交阻塞（2026-10-26 复核修复回填）

> 本节是**生成后**的事实，取代 §1 的"仓库无锁"和 §5.3 的 `expected=13` 计数。本轮未运行 Gradle。

### 10.1 生成了什么

| 文件 | 状态 | 配置覆盖（从锁文件右侧提取） |
|---|---|---|
| `app/gradle.lockfile` | 未跟踪 | `localFirstDebugCompileClasspath`、`localFirstDebugRuntimeClasspath`、`localFirstDebugUnitTestCompileClasspath`、工具链 |
| `core/{data,database,export,ui,markdown}/gradle.lockfile` | 未跟踪 | `debugCompileClasspath` + annotationProcessor/KSP/kotlinCompilerPluginClasspath |
| `core/{model,domain}/gradle.lockfile` | 未跟踪 | `compileClasspath` + 工具链 |
| `feature/{capture,review,tutor,library,profile}/gradle.lockfile` | 未跟踪 | `debugCompileClasspath` + 工具链 |
| `benchmark/gradle.lockfile` | **缺失** | — |
| `settings-gradle.lockfile` | 未跟踪 | `empty=incomingCatalogForLibs0`（settings 插件解析，不是项目锁；不应计入项目锁数量） |

共 13 个项目锁（含未跟踪模块 `:core:markdown`）+ 1 个 settings 锁；`git ls-files '*gradle.lockfile'` 仍为 0。

### 10.2 为什么不能原样提交

1. **与 HEAD 解析图不一致**：锁生成时的树含他线未提交的 `core/ui/build.gradle.kts`（`richtext.commonmark` → `project(":core:markdown")`）、`core/export/build.gradle.kts`（+kotlinx.serialization）、`feature/tutor/build.gradle.kts`（+`:core:export`）、`settings.gradle.kts`（+`:core:markdown`）。提交锁但不提交这些 build 改动时，HEAD 的 core/ui/app 仍解析 `com.halilibo.compose-richtext:richtext-commonmark`，而锁里没有它；core/export 的锁又含 HEAD 不解析的 `kotlinx-serialization-*`。Gradle DEFAULT 对"锁条目未被解析匹配"和"解析结果含锁外依赖"都判红（官方原文见 KD-72 Evidence）。
2. **配置覆盖不全**：`dependencies` 报告任务的 declarable 过滤只锁了 `debugCompileClasspath`（及工具链），没有 test/androidTest/runtime；CI 的 `:mod:test*` / `connectedDebugAndroidTest` 漂移不会被锁门拦截。根 `build.gradle.kts` 新增的 `resolveLockableClasspaths` 补这一点，但必须在干净树上重新 `--write-locks` 才生效。
3. **benchmark 缺锁 + core:markdown 未入库**：P5 的裁定未执行；`:core:markdown` 的锁不能脱离模块源码提交，`:benchmark` 没有任何锁。§5.3 的 `expected=13` 在"提交 13 个锁（含 core:markdown）"时会通过，却漏掉 benchmark——计数检查本身就是错的；改用 `tools/ci/check_lockfiles.py`（按 settings include 逐个要求锁，并在锁落地后强制测试/运行面配置）。

### 10.3 已落的门（未运行 Gradle）

- 根 `build.gradle.kts`：`lockAllDependencies` = 每个子项目 `dependencies` + `resolveLockableClasspaths`；后者解析所有 `*CompileClasspath` / `*RuntimeClasspath`（含 unitTest/androidTest/runtime）。
- `tools/ci/check_lockfiles.py` + `tools/tests/test_check_lockfiles.py`（9/9 通过）：锁未落地时只提示；一旦有跟踪锁，强制每个 included project 都有锁、Android app/lib 有 unitTest/androidTest、JVM 有 test，并拒绝未 include 模块的锁。实跑当前仓库 → exit 0，提示 13 个未跟踪锁不能原样提交，并点名 `:benchmark` 缺锁。
- CI：`Dependency lockfile manifest and coverage gate`（始终跑静态脚本）+ `Dependency lockfiles are up to date`（仅当有跟踪锁时跑 `lockAllDependencies --write-locks` 并比较 `git status --porcelain -- '*.lockfile'`）。**CI/Gradle 未运行，行为 UNVERIFIED。**

### 10.4 复跑入口

```bash
# 在干净、待提交的树上（他线 build 改动落定后）
rm -f app/gradle.lockfile core/*/gradle.lockfile feature/*/gradle.lockfile benchmark/gradle.lockfile
./gradlew --no-configuration-cache --no-parallel --console=plain lockAllDependencies --write-locks
git status --porcelain -- '*.lockfile'   # 应看到全部 included project 的新锁
python3 tools/ci/check_lockfiles.py
# 负控：改 catalog 版本 / 加 androidTest 依赖，确认 CI 的写锁-diff 或本脚本红
```
