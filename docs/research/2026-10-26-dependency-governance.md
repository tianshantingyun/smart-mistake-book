# 依赖治理研究：Gradle 锁定/校验、kotlin-stdlib 2.4.10 兼容判定、R8/Compose/KSP 牵动面

> 线：R2（批次 22 T3 驱动）｜日期：2026-10-26｜落点：本文件
>
> **本线未运行 Gradle、未运行 adb/am、未改任何构建/生产代码、未执行任何 git 写操作。**
> 本文所有"已核"均为本线当前轮实跑的命令或实读的文件；未实跑的项一律写 **UNVERIFIED**。
> 探针脚本与解包产物在系统临时目录（`%TEMP%\qjs\`），不属仓库，复现命令见 §5。

> **2026-10-26 复核返修补记（批次 22 修复轮）。** 仓库仍无任何 `gradle.lockfile`（`find` 0 命中；`git ls-files` 0），默认 `LockMode.LENIENT` 下 dependency locking **实际未生效**——本文推荐的"locking（主）"只有声明、没有锁状态，T3 只能记为部分属实（KD-72）。路径 A 的 stdlib 降级仍只有 quickjs AAR 的 88 条成员引用扫描与缓存 POM 样本，**不是完整解析图的 API 兼容证明**；`check_dependency_pins.py` 只校版本，不校 API（KD-73）。本轮回跑 `python tools/ci/check_dependency_pins.py` → exit 0（`resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`），`test_check_dependency_pins.py` 14/14 绿；这两项不改变上面的缺口结论。
>
> **2026-10-26 批次 24 T3 更正：上面的"仓库仍无任何 gradle.lockfile / 默认 LENIENT"是批次 22 修复轮的状态，已过时。** 当前工作树有 **13 条 git 可见的未跟踪锁条目**（`git status --porcelain | grep -i lockfile`），盘上实为 **14 个** `gradle.lockfile`（`core/markdown/gradle.lockfile` 被未跟踪目录折叠）；`git ls-files '*gradle.lockfile'` → 0；`:benchmark` 缺锁。锁模式是默认 **`DEFAULT`**（不是 `LENIENT`；根 `build.gradle.kts:19-33`）。这批锁生成于他线未提交的构建改动、与 HEAD 不一致，**不得原样提交**；须等干净树用 `lockAllDependencies --write-locks` 重生成。KD-72 保持 open。

---

## 0. 结论摘要（先给可执行结论）

| 问题 | 结论 |
|---|---|
| **Q1 用 dependency locking 还是 verification-metadata？** | **两者不是替代关系**：locking 钉"解析出的版本"，verification 钉"下载到的字节"。本项目现状（AAR 的 POM 静默把 kotlin-stdlib 从 2.3.21 抬到 2.4.10，仓库内零 diff）**正是 locking 的目标失败模式**，而字节级风险已由既有 `tools/ci/check_dependency_pins.py` 对 quickjs 三构件做了定向 sha256 门。**推荐：全项目启用 dependency locking（主）+ 保留定向 sha256 门；全量 verification-metadata 作为条件项，不在本批默认启用。** |
| **Q2 2.4.10 vs 2.3.21 兼容判定** | 该 AAR 的**字节码**只用到 2.3.21 已存在的 API：106 个 class 的 88 条 `kotlin/*` 成员引用在 2.3.21 与 2.4.10 两个 stdlib **全部可解析（unresolved=0）**；唯一"2.4 才有"的 `kotlin.concurrent.atomics.AtomicBoolean` 只出现在 Kotlin 元数据里，JVM 字段类型实为 `java.util.concurrent.atomic.AtomicBoolean`。POM 的 `kotlin-stdlib 2.4.10`（scope=compile）与 GMM 的 api/runtime `requires` 是**库作者构建声明**，不是字节码硬要求。**但元数据 `mv=[2,4,0]` 只能靠"向前兼容"读取，官方口径是 "preferably ... mostly"（不保证）**，所以这是一份需要逐版复核的"能用"，不是"合规"。 |
| **Q2 选哪条路** | **A（约束回 2.3.21）先行**：官方组合最正、编译/运行同版、探针证明可行；代价是**必须同步改 `check_dependency_pins.py:165-168` 的语义**（现脚本会把 A 判红，本线负控已实证），且每次 AAR 升级要重跑字节码扫描。**B（全项目升 2.4.10）当前被 KSP 阻断**（Maven Central 最新 KSP=2.3.12，官方页只声明支持 KGP 2.2.10–2.3.x；Kotlin 2.4 官方声明 Gradle 上限 9.5.0，本仓库 wrapper 是 9.6.1）。**C（维持现状+锁定）作为 A 失败时的回退**，并如实登记"catalog 的 `kotlin=2.3.21` 不等于实际编译 classpath 的 stdlib 2.4.10"。 |
| **Q3 R8/Compose/KSP 牵动** | 只动 stdlib 版本（A 或 C）**不牵动 Compose 编译器插件与 KSP**（二者是编译器/编译插件，版本由 Kotlin 版本驱动，与 stdlib jar 无关）；**只有升 Kotlin（B）才牵动**：Compose 编译器插件随之换版（2.4 有 Compose 行为变更），KSP 需要 2.4 兼容版本（当前不存在）。R8 侧：批次 21 的 `assembleLocalFirstRelease` 已在**包含 stdlib 2.4.10 + 该 AAR** 的 release APK 上跑通并启动无 FATAL，说明 R8 能吃 `mv=2.4.0` 类；A（降到 2.3.21）只会更旧、更安全。4E 既有仪器化用例跑 debug（不过 R8），**不能**替代 R8 验证。 |

---

## 1. 本仓库的既成事实（实读）

| 事实 | 证据 |
|---|---|
| 编译期 Kotlin = 2.3.21；KSP = 2.3.10；AGP = 9.3.0；Compose BOM = 2026.06.00 | `gradle/libs.versions.toml:3`、`:18`、`:2`、`:4` |
| quickjs 钉 1.0.15；catalog 另记一条 `quickjs-kotlin-stdlib = "2.4.10"` 作为"被 AAR 带进来的运行时版本"台账 | `gradle/libs.versions.toml:51`、`:56` |
| AAR 只在 core:data 消费 | `core/data/build.gradle.kts:193-199`（`:199` = `implementation(libs.quickjs.kt.android)`） |
| Gradle wrapper = 9.6.1（`distributionSha256Sum` 已钉） | `gradle/wrapper/gradle-wrapper.properties` |
| **配置缓存开启**、并行开启、`kotlin.incremental=false` | `gradle.properties:2-3`、`:12` |
| 14 个 included project + root（15 个 Gradle project） | `settings.gradle.kts` 的 `include(...)` |
| release / benchmark / internal 三个 buildType 都 `isMinifyEnabled = true`；**只有 release 配了 `proguardFiles`** | `app/build.gradle.kts:57-80`（proguardFiles 在 `:78`） |
| 自定义 R8 规则只有 Room/ML Kit 两条反射 keep | `app/proguard-rules.pro` |
| KSP + Room3 插件**只在 core:database** | `core/database/build.gradle.kts:1-6` |
| Compose 编译器插件在 8 个模块启用：app、core:ui、core:markdown、feature×5 | `grep -rln "compose.compiler" --include=build.gradle.kts`（排除 `.worktrees/`） |
| Android 模块用的是 **AGP 9 built-in Kotlin**（产物在 `intermediates/built_in_kotlinc`） | `core/database/build.gradle.kts:77` 注释 |
| 现状解析结果：**kotlin-stdlib = 2.4.10**（编译 catalog 写 2.3.21） | 本线实跑 `python tools/ci/check_dependency_pins.py` → `resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.4.10']`，`EXIT=0`；解析源 `app/build/intermediates/incremental/lintVitalAnalyzeLocalFirstRelease/localFirstRelease-artifact-dependencies.xml` |
| release 解析图规模：**166 个唯一坐标 / 177 个唯一文件** | 本线实跑 `python count_graph.py`（解析上述 XML） |
| CI 路径过滤含 `gradle/**`，**不含根 `gradle.lockfile`** | `.github/workflows/android-check.yml:5-21`（`:11` 是 `gradle/**`） |

---

## 2. Q1：dependency locking vs verification-metadata

### 2.1 官方定义的能力边界（Gradle 用户手册）

| 维度 | Dependency locking | Dependency verification |
|---|---|---|
| 钉住什么 | **解析出的模块版本**（含传递依赖） | **下载到的构件字节**（sha256 校验和 / PGP 签名） |
| 不覆盖什么 | 不钉构件内容：同版本被重新发布（换字节）看不出来；文档明确 "It locks **versions**, not ... artifact checksums/content hashes" | 不防"被投毒的依赖"本身（"not to prevent you from including vulnerable dependencies"）；**不校验 changing 依赖（SNAPSHOT）与本地产物** |
| 触发时机 | 解析期（选版本时） | 下载/校验期（解析已成功后） |
| 文件 | 每个 Gradle project 一个 `gradle.lockfile`（buildscript 另有 `buildscript-gradle.lockfile`） | 全仓一个 `gradle/verification-metadata.xml` |
| 冲突语义 | 锁定的版本以 **`strictly` 约束**注入；直接依赖声明高于锁定值 → **解析失败**（文档例：declared 1.2.0 + locked 1.0.0 → fail） | 缺条目/校验和不符 → 按 `--dependency-verification` 模式失败（**strict 是默认**） |
| 维护成本 | 依赖变更时用 `--update-locks` 定向更新；锁文件进版本控制 | 每次依赖/版本变动都要重新生成 XML（含 POM/GMM 元数据校验和，`verify-metadata` 默认 true），生成项需人工复核后把 origin 改成 "Verified" |
| 适用失败例 | **本项目**：AAR 的 `requires 2.4.10` 静默覆盖编译 2.3.21，仓库零 diff | 同版本 AAR 被换字节；上游发布链路被篡改 |

来源：<https://docs.gradle.org/current/userguide/dependency_locking.html>、<https://docs.gradle.org/current/userguide/dependency_verification.html>（两页均为本线 WebFetch 实读）。

### 2.2 落地步骤（官方命令）

**Dependency locking**

```kotlin
// 方案 1：按配置
configurations {
    compileClasspath { resolutionStrategy.activateDependencyLocking() }
}
// 方案 2：整个 project 的全部可解析配置（推荐用这个）
dependencyLocking {
    lockAllConfigurations()
}
// buildscript classpath 需单独开
buildscript {
    configurations.classpath { resolutionStrategy.activateDependencyLocking() }
}
```

```bash
# 生成/重写锁状态（构建失败则不写盘）
./gradlew dependencies --write-locks
# 定向更新（支持 group:* 通配）
./gradlew dependencies --update-locks org.jetbrains.kotlin:kotlin-stdlib
```

- 锁文件 `gradle.lockfile` 放在各自 project 根目录，官方要求 **commit 进版本控制**。
- 锁模式：默认（锁定项必须匹配且不得多出依赖）/ `LockMode.STRICT`（配置无锁状态也失败）/ `LENIENT`（仍钉 dynamic 版本，但允许解析变化——不要用它做加固）。
- 官方文档**没有** `--locked` 这个 flag（我们不要臆造）：CI 直接跑正常构建任务，有锁状态时自动校验。

**Dependency verification**

```bash
./gradlew --write-verification-metadata sha256        # 只校验和（推荐先这样）
./gradlew --write-verification-metadata sha256,pgp    # 校验和 + 签名
./gradlew --dependency-verification strict build      # CI 显式 strict（也是默认）
```

- 文件固定位置 `$PROJECT_ROOT/gradle/verification-metadata.xml`；生成本身会解析 root/subprojects/buildSrc/included builds/**插件** 的全部可解析配置。
- 只做校验和时需 `<verify-signatures>false</verify-signatures>`；官方提醒"checksums alone verify integrity but not authenticity"。
- 想只验某一个 group 而不验全图做不到（其余构件会因"缺校验和"在 strict 下失败，只能逐个 `trusted-artifacts` 放行）——这正是本项目**不**上全量 verification 的主因（见 §2.5）。

### 2.3 与配置缓存的兼容（本仓库 `org.gradle.configuration-cache=true`）

- 官方页没有一句"locking/verification 与配置缓存兼容"的直述；配置缓存页只把 **dependency lock files / dependency verification files 列为配置输入**（"Gradle configuration files (Version Catalogs, dependency verification files, dependency lock files, `gradle.properties` files)"），并说明不兼容特性会在 store 期作为 problem 报出。
- 版本门槛用 issue 追踪器实证（`gh api repos/gradle/gradle/issues/<n>` 实跑）：
  - `#13507 Configuration cache should support dependency locking` → **closed，milestone 6.6 RC1**（2020-07-07）；
  - `#13703 Add support for dependency locking when the configuration cache is enabled` → closed（同日）；
  - `#19716 Make dependency verification compatible with configuration cache` → **closed，milestone 8.1 RC1**（2023-03-09）；
  - `#24130 Fix writing dependency verification file when configuration cache is enabled` → **closed，milestone 8.1 RC1**。
- 本仓库 wrapper **9.6.1** ≫ 6.6 / 8.1 → 两者在配置缓存下均可用。**但"可用"不等于"本仓库实跑通过"**：本线禁跑 Gradle，故 `--write-locks`/`--write-verification-metadata` 在 9.6.1 + AGP 9.3.0 + 15 project 下的实际行为标 **UNVERIFIED**（§7）。

### 2.4 本项目适配（多模块 + 版本目录）

- 版本目录（`gradle/libs.versions.toml`）只提供坐标，与锁定/校验都不冲突；但**改 catalog 不会自动改锁文件**：CI 上"只改 catalog 不更新 lockfile"会按预期红——这是想要的闸门。
- 粒度选择：
  - 若 `subprojects { dependencyLocking { lockAllConfigurations() } }` → **14 个 `gradle.lockfile`**（14 个 subproject；root 只有 plugins 块、无项目依赖，需要时可在 root 另开一个）；
  - 若只锁 `:app`（`compileClasspath`/`runtimeClasspath`）→ 1 个锁文件即可覆盖**进入 APK 的全图**（含各 library 的 implementation 传递），但各模块的 unit test 配置不受钉。
  - 本仓库有 `:app` 两个 flavor × 多 buildType，配置名带变体前缀（如 `localFirstReleaseRuntimeClasspath`），用 `configurations.matching { it.name.endsWith("RuntimeClasspath") }` 易漏；**`lockAllConfigurations()` 更稳**。
- 已知成本（需实测确认，UNVERIFIED）：AGP + `lockAllConfigurations()` 首次 `--write-locks` 会解析大量变体配置（耗时、可能有个别无谓锁项），需在本地跑一次观察。
- **CI 缺口（实读）**：`.github/workflows/android-check.yml:5-21` 的 `paths` 只有 `gradle/**`，根目录的 `gradle.lockfile` **不在**触发列表里 → 落地时必须补 `"**/gradle.lockfile"`（`verification-metadata.xml` 在 `gradle/` 下，已被 `gradle/**` 覆盖）。
- `.gitignore` 当前没有任何 lock 相关条目（实读 `grep -n "lock" .gitignore` 无输出）→ 锁文件可正常入库，无需改 `.gitignore`（热区，本批不动）。

### 2.5 推荐：locking 为主 + 定向 sha256 门；verification 暂不全量

理由（按权重）：

1. **对着本项目的真实失败**：仓库唯一的版本漂移是"quickjs AAR 的 `requires 2.4.10` 把 stdlib 抬走"。locking 的锁定值以 `strictly` 注入解析，**这类漂移会在解析期失败**；而 verification 只在"字节变了"时失败，对这个失败模式无效。
2. **字节级风险已有更窄的门**：`tools/ci/check_dependency_pins.py` 对 AAR/POM/.module 三件做 sha256（`AAR_SHA256`、`POM_SHA256`、`MODULE_SHA256`）+ 校验解析图里的 quickjs/stdlib 版本，本线实跑 positive `EXIT=0`；本线实跑负控 `--expect-kotlin-runtime 2.3.21` → `EXIT=1`，输出正是
   `FAIL: POM requires kotlin-stdlib 2.4.10, pinned runtime is 2.3.21` 与
   `FAIL: resolved kotlin-stdlib ['2.4.10'] != pinned 2.3.21`。全量 verification 与它的差别只是"覆盖面"，不是"能力档次"。
3. **全量 verification 的体量与维护**：release 图 166 个坐标 + 元数据校验和，再加 14 个 project 的 test/androidTest 配置和插件 classpath，条目数会到数百；每次依赖升级都要重新生成并人工复核。对一个单人维护的仓库，收益/成本比不划算。
4. 若组织将来要求"全构件来源可审计"，再按 §2.2 的 `sha256`（不加 pgp）+ `<verify-metadata>false</verify-metadata>` 引入，把变更面限制在构件字节。

**可选加强（低成本）**：在 locking 之外，用 `LockMode.STRICT` 让"某配置没有锁状态"也失败，防止新加模块时漏锁。

### 2.6 落地步骤（给 T3 的可执行清单）

1. **根 `build.gradle.kts`**（本批落点，干净）加：

```kotlin
// 批次 22 T3：把版本漂移关进解析期。lockAllConfigurations 覆盖每个 project 的可解析配置。
subprojects {
    dependencyLocking {
        lockAllConfigurations()
        // lockMode = LockMode.STRICT   // 需要 import org.gradle.api.artifacts.dsl.LockMode（落地时以 IDE 补全为准）
    }
}
```

2. **生成锁文件**（本线未跑，命令照抄官方并限定到本仓库任务名）：

```bash
./gradlew dependencies --write-locks
# 期望产物：14 个 subproject 各一个 gradle.lockfile（root 无项目依赖，可不开）；先只看 diff 与体积，不提交
# 校验点（任选其一，按需）：
./gradlew :app:dependencyInsight --configuration localFirstReleaseRuntimeClasspath --dependency kotlin-stdlib
```

3. **CI**：`.github/workflows/android-check.yml` 的 `paths` 增 `"**/gradle.lockfile"`；构建命令保持常规（有锁即校验），**不要**加不存在的 `--locked`。
4. **失败判据**：
   - 把 catalog 里任一版本改掉而不跑 `--write-locks` → CI 必须红（锁状态不匹配）；
   - `--update-locks` 定向更新后 diff 只含目标模块。
5. 登记：锁文件条目里 `org.jetbrains.kotlin:kotlin-stdlib:<版本>` 的取值，必须与 §3 最终选择的路径一致（A → 2.3.21；C → 2.4.10），并同步 `gradle/libs.versions.toml:52-56` 的供应链注释口径（`docs/current/visual-generation-contract.md` 属 T7 修改范围，本线不动）。

---

## 3. Q2：kotlin-stdlib 2.4.10 vs 2.3.21 的兼容判定

### 3.1 构件事实（实读 + 实跑）

**AAR 元数据与字节码**

- `javap -v -p com/dokar/quickjs/QuickJs.class` → `kotlin.Metadata(mv=[2,4,0], k=1, xi=48)`；class file `major version: 55`（Java 11）。→ **该 AAR 由 Kotlin 2.4.x 编译**（2.4 系列写 `mv=2.4.0`）。
- classes.jar 内 **106 个 class**。
- 全量常量池扫描（`collect2.py`）：`kotlin/*` 直接引用 **132 条**，其中 **88 条是成员引用**（方法/字段），44 条是类引用。
- 类型面扫描（`collect3.py`，含 Utf8 描述符）：**66 个 `kotlin.*` 类型**在 2.3.21 与 2.4.10 两个 stdlib 里都存在；"缺失"的 8 个全部是 **Java 类型别名或调试态假类**：
  `kotlin/Exception`、`kotlin/Function0/1/2`、`kotlin/collections/LinkedHashMap`、`kotlin/collections/LinkedHashSet`（typealias → `java.util.*` / `kotlin.jvm.functions.*`），`kotlin/jvm/internal/FakeKt`（只出现在 SMAP 调试信息里）。
- 唯一看着"新"的类型 `kotlin/concurrent/atomics/AtomicBoolean` **只存在于 Kotlin 元数据 d2**（typealias）；`javap -p` 显示字段真实类型是：
  `private final java.util.concurrent.atomic.AtomicBoolean closed;` → 运行时不需要 2.1+ 的新包。

**成员可解析性（关键实验）**

```text
python check_members2.py <kotlin-stdlib-2.3.21.jar> refs.tsv   → checked 88 member refs; unresolved 0
python check_members2.py <kotlin-stdlib-2.4.10.jar> refs.tsv   → checked 88 member refs; unresolved 0
```

即：AAR 字节码引用的**每一个** stdlib 成员（含 Kotlin 编译器为 inline 函数生成的 `Intrinsics`、`SpreadBuilder`、协程 `ContinuationImpl`/`SuspendLambda`/`DebugProbesKt` 等）在 2.3.21 里都存在且签名一致。

**POM / GMM 语义**

- POM：`kotlin-stdlib 2.4.10, scope=compile`（`.../quickjs-kt-android-1.0.15.pom`，实读）。
- GMM（`quickjs-kt-android-1.0.15.module`，实读）：`releaseApiElements-published`（java-api）与 `releaseRuntimeElements-published`（java-runtime）**两个变体都声明** `{"module":"kotlin-stdlib","version":{"requires":"2.4.10"}}`，且 `createdBy.gradle.version = 9.7.1`。
- `requires` 是**软版本要求**（不是 `strictly`、不是 `reject`）→ Gradle 可以按冲突解决把它降下来（这也正是 locking 的工作方式）。
- 因此：**"POM 声明 2.4.10"= 库作者构建时的编译期依赖声明（Maven `compile` scope，按 Maven 语义编译与运行都可见）；它不是"字节码需要 2.4 API"的证明。** 本题的字节码证据（§3.1 两条）恰好证明后者不成立。

### 3.2 直接编译探针（本线实跑，非 Gradle）

用仓库 Gradle 缓存里的 **Kotlin 2.3.21 编译器**（`kotlin-compiler-embeddable-2.3.21.jar`）直接编译一个引用该 AAR 的探针：

```bash
java -cp "<kc-2.3.21>;<stdlib-2.3.21>;<reflect-2.3.21>;<script-runtime-2.3.21>;<coroutines-1.11.0>;<annotations-23.0.0>" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 11 \
  -classpath "<AAR classes.jar>;<kotlin-stdlib-2.3.21.jar>" \
  -d out23 Probe.kt
```

`Probe.kt` 内容（`q.version` / `q.evaluate<String>("1+1")` / `q.memoryUsage`）：

```kotlin
import com.dokar.quickjs.MemoryUsage
import com.dokar.quickjs.QuickJs

fun probeVersion(q: QuickJs): String = q.version
suspend fun probeEval(q: QuickJs): String = q.evaluate<String>("1+1")
fun probeUsage(q: QuickJs): MemoryUsage = q.memoryUsage
```

结果：**`EXIT 0`，无任何 metadata 版本报错**，产物含
`ProbeKt$probeEval$$inlined$evaluate$default$1.class`（证明 AAR 里 `evaluate` 的 **inline 体被成功内联**进 2.3.21 编译的代码）。
再对产物做一次常量池扫描：`checked 6 member refs; unresolved 0`（对 2.3.21）→ **被内联进调用方的代码同样不依赖 2.4 API**。

> 说明：第一次跑时唯一报错是 `cannot inline bytecode built with JVM target 11 into ... 1.8`——这是 JVM target 不匹配，与 metadata/API 无关；加 `-jvm-target 11` 后即 0（app 的 `compileOptions` 与 `core:domain`/`core:model` 都是 17，见 `app/build.gradle.kts:89-90`、`core/domain/build.gradle.kts`）。

**反向探针：2.3.21 编译器会不会"看见" 2.4 的 stdlib API？**

把 stdlib **2.4.10** 放进 classpath，编译使用 2.4 新 API 的源码（`List<Int>.isSorted()`、`UInt.toBigInteger()`，二者均为 2.4.0 新增）：

```text
EXIT 1
Probe24.kt:1:46: error: unresolved reference 'isSorted'.
Probe24.kt:3:27: error: unresolved reference 'toBigInteger'.
```

结论（本线实测）：**只要编译器是 2.3.21，classpath 上的 stdlib 是 2.4.10 也不会让 2.4 新 API 变得可用**。另外，把 classpath 换成 2.4.10 编译一段普通代码时**没有任何 stdlib 版本警告**（`EXIT 0`，stderr 空）——独立 CLI 层面如此；Gradle/KGP 是否额外告警 **UNVERIFIED**。

### 3.3 官方兼容规则（文献）

- **Kotlin 演进原则**（<https://kotlinlang.org/docs/kotlin-evolution-principles.html>，实读）：
  > "All binaries are backwards compatible; that means a newer compiler can read older binaries."
  > "Preferably (but we can't guarantee it), the binary format is mostly forwards compatible with the next language release, but not later ones (in the cases when new features are not used, for example, 1.9 can understand most binaries from 2.0, but not 2.1)."
  > "-api-version X.Y ... The compiler ignores declarations that use Kotlin standard library APIs introduced in later versions, including APIs referenced by compiler-generated code."
  → **2.3.21 读 2.4 产物正落在"preferably / not guaranteed"的那一格里**，与上面的探针结果（这个具体构件能读）不矛盾：能读是实测事实，"不保证"是官方承诺等级。
- **kotlin-metadata-jvm 文档**（<https://kotlinlang.org/docs/metadata-jvm.html>，实读）：`readLenient()` 可读"更新编译器生成的元数据"；`readStrict()` 只支持到 `JvmMetadataVersion.LATEST_STABLE_SUPPORTED` **+1 个版本**（例：库 2.1.0 能处理到 Kotlin 2.2.x）。→ 工具链里确实存在"向前一个版本"的窗口，但那是工具 API 的窗口，**不是编译器对任意 2.4 特性的保证**。
- **KGP 的 stdlib 自动依赖**（<https://kotlinlang.org/docs/gradle-configure-project.html>，实读）：
  > "A dependency on the standard library (stdlib) is added automatically to each source set. The version of the standard library used is the same as the version of the Kotlin Gradle plugin."
  > "If you declare a standard library dependency explicitly ... the Kotlin Gradle plugin won't override it or add a second standard library."
  → 本项目各模块自己要求 2.3.21，**唯一要求 2.4.10 的只有 quickjs AAR**（本线扫描：缓存内 78 个 POM + 54 个 `.module` 里，`kotlin-stdlib` 要求 ≥2.4.10 的**只有** `quickjs-kt-android-1.0.15`；其余最高 2.2.10 级别）。扫描范围 = 本机缓存样本，**不等于全图**。

### 3.4 三条路径

#### 路径 A：约束 kotlin-stdlib 回 2.3.21（推荐先试）

**改动面**

1. 根 `build.gradle.kts`（本批落点）：

```kotlin
subprojects {
    configurations.configureEach {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
        // 或带理由的版本：
        // resolutionStrategy.eachDependency {
        //     if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-stdlib") {
        //         useVersion("2.3.21")
        //         because("批次 22 R2：与编译期 Kotlin 对齐；AAR 字节码已核不依赖 2.4 API")
        //     }
        // }
    }
}
```

2. **`tools/ci/check_dependency_pins.py:165-168` 必须同步改**：现在的逻辑是
   `if pom_stdlib != pinned_stdlib: errors.append("POM requires kotlin-stdlib ..., pinned runtime is ...")`，
   即**硬要求"POM 要求 == 最终钉住的运行时版本"**。A 一旦落地，POM 仍写 2.4.10、运行时变 2.3.21，**这个门会立刻红**。
   正确语义应拆成两条不变量：
   - `POM 声明 == catalog 的 quickjs-kotlin-stdlib`（记录上游事实，2.4.10）；
   - `解析出的 runtime stdlib == catalog 的 kotlin`（本项目决定，2.3.21）。
3. catalog 注释口径同步（`gradle/libs.versions.toml:52-56` 的注释块）——说明"上游要求 2.4.10，本项目按 A 覆盖为 2.3.21，理由是 §3.1 字节码扫描"。
4. 全量单测门 + 设备轨（QuickJS 路径只有设备上才会 `System.loadLibrary`）。

**风险**

- **运行时降级**：APK 里的 stdlib 从 2.4.10 变 2.3.21。若将来 AAR 升级开始使用 2.4 API，会在设备上以 `NoSuchMethodError`/`NoClassDefFoundError` 形式暴露，且**编译期不会报错**（因为强制降版是静默的）。
  → 缓解：AAR 换版必然触发 `check_dependency_pins.py` 的 sha256 不匹配（红），届时**重跑 §3.1 的扫描**（脚本见 §5）。
- **元数据向前兼容依旧无保证**（A 不改变这一点：AAR 的 `mv` 仍是 2.4.0，编译器仍是 2.3.21）。A 只是把"stdlib 版本"这条边拉正。
- `force` 会作用于全部配置（含 test/androidTest），对测试无实质影响，但会让"某测试真的要 2.4"这类诉求被静默压掉。

**可验证判据**

- `./gradlew :app:dependencyInsight --configuration localFirstReleaseRuntimeClasspath --dependency kotlin-stdlib` → 2.3.21；
- `python tools/ci/check_dependency_pins.py` → exit 0（改完脚本后）；
- 单测 11 模块全绿 + 设备轨（含 KD-70 OOM 例）按批次 22 计划；
- 探针复跑 `check_members2.py` 两个 jar 都 unresolved 0（每次 AAR 升级都跑）。

#### 路径 B：全项目升 Kotlin 2.4.10

**改动面**：`gradle/libs.versions.toml:3`（kotlin）、`:18`（ksp）、Compose 编译器插件（`version.ref = kotlin`，自动跟随）、serialization 插件（同上）、`core:domain`/`core:model` 的 `kotlin-jvm` 插件；Android 模块走 AGP built-in Kotlin，其编译器版本如何被选起 **UNVERIFIED**（`core/database/build.gradle.kts:77` 只证明用的是 built-in 产物）。14 个模块全量重编。

**风险（按阻断级别排序）**

1. **KSP 无 2.4 版本**（硬阻断）：Maven Central `symbol-processing-api` 元数据（`curl` 实读）最新为 **2.3.12**，无任何 2.4.x；官方 KSP 页（<https://kotlinlang.org/docs/ksp-overview.html>）称 "The latest KSP version, 2.3.10, supports the following dependency versions: Kotlin Gradle plugin (KGP) 2.2.10–2.3.x"（该页口径停在 2.3.10，晚于 Maven 上的 2.3.12，页面可能滞后，但**没有任何一版声明支持 2.4**）。KSP 只在 `core:database`（Room3 代码生成）用，但那一处断了整个构建就断。
2. **Gradle 版本越界（文档）**：Kotlin 2.4 官方 what's new（<https://kotlinlang.org/docs/whatsnew24.html>）写 "Kotlin 2.4.0 is fully compatible with Gradle 7.6.3 through 9.5.0"；本仓库 wrapper 是 **9.6.1**，已超出官方声明区间。最低 AGP 要求 8.5.2（我们 9.3.0 满足）。
3. **Compose 行为变更**：2.4.0 改了 Compose 编译器（增量一致性、内部类型稳定性推断、`StrongSkipping`/`IntrinsicRemember` 升到 ERROR 级弃用）→ 需重跑 Compose/仪器化 UI 用例。
4. `-language-version=1.9` 在 2.4 被移除（K1 不再支持）——本项目没设 languageVersion（`grep` 无命中），暂不受影响。

**可验证判据**

- 先证明 KSP 有 2.4 兼容版本（`gh release list -R google/ksp` 出现 2.4.x，或官方表更新）——**当前不满足**；
- 再谈 `./gradlew :core:database:ksp…` + 单测门 + `assembleLocalFirstRelease` + 设备轨。

#### 路径 C：维持现状 + 锁定（回退方案）

**改动面**：只加 locking（§2.6），不加任何依赖变更。锁文件如实记录 `kotlin-stdlib:2.4.10`，`check_dependency_pins.py` 保持现状（POM==pinned==resolved==2.4.10，仍绿）。

**风险**：catalog 的 `kotlin = 2.3.21` 与"编译 classpath 上实际是 2.4.10"长期不一致（靠注释与门解释）；编译/运行 stdlib 不同版（虽然 §3.2 反向探针证明 2.4 新 API 在 2.3.21 编译器下不可见，实际可用面仍等于 2.3，**但这条依赖编译器默认 `apiVersion`，本线未从官方页拿到默认值 → UNVERIFIED**）；元数据向前兼容无保证（同 A）。

**可验证判据**：lockfile 含 2.4.10；pin gate exit 0；全量单测 + 设备轨绿。

### 3.5 判定与推荐

- **"能跑"的判定：成立。** 依据是两条实验（88 条成员引用双 jar 全解析；2.3.21 编译器直接编译 AAR 探针 exit 0 且 inline 成功）而不是"看起来像"。
- **"合规/有保证"的判定：不成立。** 官方对"旧编译器读新产物"只给 "preferably ... mostly"，不保证；AAR 的 `mv=2.4.0` 无法改变。
- **推荐顺序：A → C（回退）→ B（等 KSP 出 2.4 版本且 Gradle 进官方区间）。**
  A 把"编译器 / stdlib / 运行"三者对齐到 2.3.21，只留"读 2.4 元数据"这一条经实测可用的前向边；C 留两条前向边（stdlib + AAR）；B 今天直接撞 KSP 墙。

---

## 4. Q3：R8 与 4E 既有用例——stdlib 变化会不会牵动 Compose/KSP

> 本节只做**文献 + 仓库事实核对**，按要求不跑 Gradle。

### 4.1 仓库事实

| 事实 | 证据 |
|---|---|
| R8 只在 release/benchmark/internal 开（`isMinifyEnabled = true`），release 有 proguardFiles、另两个没有（KD-69 根因） | `app/build.gradle.kts:57-80` |
| 自定义 R8 规则仅 Room `WorkDatabase_Impl` 构造器 + 3 个 ML Kit registrar | `app/proguard-rules.pro` |
| AAR 自带 consumer 规则：`-keep,allowoptimization class com.dokar.quickjs.QuickJs/QuickJsException/JsProperty/JsFunction/JsObject { *; }` 与 `kotlin.UByteArray` | `.gradle/caches/.../transformed/quickjs-release/proguard.txt`（实读）；`kotlin/UByteArray.class` 在 2.3.21 与 2.4.10 两 jar 中都存在（§3.1 类型扫描） |
| **批次 21 已产出过含 stdlib 2.4.10 + 该 AAR 的 release APK，并装机启动无 FATAL** | `docs/research/2026-10-25-batch-21-record.md:174`（`:app:assembleLocalFirstRelease` BUILD SUCCESSFUL；523 行 logcat 中 FATAL/ClassNotFound/NoSuchMethod 全 0） |
| Compose 编译器插件在 8 个模块启用 | `grep -rln "compose.compiler" --include=build.gradle.kts`（app、core:ui、core:markdown、feature×5） |
| KSP + Room3 只在 core:database | `core/database/build.gradle.kts:1-6` |
| 4E 引擎实现与仪器化用例在 core:data（JVM 测试不触 QuickJs） | `core/data/build.gradle.kts:193-199`；`core/data/src/main/.../visual/QuickJsVisualExecutionEngine.kt`；`core/data/src/androidTest/.../visual/` |

### 4.2 结论

1. **只动 stdlib 版本（A 或 C）不牵动 Compose 与 KSP。**
   - Compose 编译器是**编译器插件**，其版本由 Kotlin 版本决定。官方："The Compose compiler has been merged into the Kotlin repository since Kotlin 2.0.0 ... the Compose compiler ships simultaneously with Kotlin and will always be compatible with Kotlin of the same version."（<https://kotlinlang.org/docs/compose-compiler-migration-guide.html>，实读）。stdlib jar 换版本不改变编译器插件。
   - KSP 同样是编译器插件（独立发版），与 stdlib jar 版本无关。
2. **升 Kotlin（B）才牵动两者**：Compose 编译器跟着换到 2.4.10 并有 2.4 行为变更（§3.4-B 第 3 条）；KSP 需要 2.4 兼容版本（不存在）。
3. **R8 侧**：
   - 现状（stdlib 2.4.10，`mv=2.4.0` 的 stdlib 与 AAR）已经被 R8 处理过一次并成功启动（批次 21 release 实证）。→ R8 **能吃** metadata 2.4.0 的类（至少不崩）。
   - 路径 A 让 stdlib 回到 2.3.21（`mv=2.3.0`），对本项目而言是**更旧**的输入 → 不新增 R8 面。
   - 路径 B 会让**项目自己的** class 也变成 `mv=2.4.0`，并叠加 Compose 编译器 2.4 的输出变化 → R8 面真正变化。
   - 未验证：R8 是否**正确重写**（而非仅"不崩"）metadata 2.4.0；`-keep class kotlin.UByteArray` 规则在降版后的行为（类在，规则可解析，但 keep 的必要性未复核）。
4. **4E 既有用例与 R8 的关系**：4E 的仪器化用例跑 debug（`assembleDebugAndroidTest`，不过 R8），它们**证明不了 R8 后的行为**；R8 后的 QuickJS/JNI 面仍需 release 变体冒烟（批次 22 设备轨已列 "R8 debug/release"）。JNI 面靠 AAR consumer 规则保 `QuickJs` 类名与方法名，规则由 AAR 自带，**与 stdlib 版本无关**。
5. KD-69（debug R8 FATAL）与本问题正交：根因是 `benchmark`/`internal` 无 `proguardFiles`（`app/build.gradle.kts:57-70`），不是 stdlib 版本。

---

## 5. 本线实跑的命令与产物（可复现）

全部在系统临时目录 `%TEMP%\qjs\`（`cygpath -w /tmp` = `C:\Users\听云\AppData\Local\Temp`），不落仓库。

| # | 命令 | 关键输出 |
|---|---|---|
| 1 | `python tools/ci/check_dependency_pins.py` | `resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.4.10']`，`EXIT=0` |
| 1b | `python tools/ci/check_dependency_pins.py --expect-kotlin-runtime 2.3.21`（负控，同时**证明路径 A 会打红现门**） | 两条 FAIL（POM 要 2.4.10 / resolved 2.4.10 ≠ pinned 2.3.21），`EXIT=1` |
| 2 | `javap -v -p com/dokar/quickjs/QuickJs.class` | `kotlin.Metadata(mv=[2,4,0], k=1, xi=48)`；`major version: 55` |
| 3 | `python collect2.py cls > refs.tsv` | `class count 106`；132 条 kotlin 引用（88 条成员引用） |
| 4 | `python check_classes.py <stdlib-2.3.21.jar> lenient_types.txt` | `present 66 missing 8`（缺失项全为 Java 别名/FakeKt） |
| 5 | `python check_members2.py <stdlib-2.3.21.jar> refs.tsv` | `checked 88 member refs; unresolved 0` |
| 6 | `python check_members2.py <stdlib-2.4.10.jar> refs.tsv` | `checked 88 member refs; unresolved 0` |
| 7 | `java -cp <compiler-embeddable-2.3.21 …> org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 11 -classpath <AAR classes>;<stdlib-2.3.21> -d out23 Probe.kt` | `EXIT 0`；产物含 `ProbeKt$probeEval$$inlined$evaluate$default$1.class` |
| 8 | `python collect2.py probe/out23 > refs_inlined.tsv` + `check_members2.py <2.3.21>` | `checked 6 member refs; unresolved 0`（内联后的引用也不越界） |
| 9 | 同 7 但 classpath 换 `stdlib-2.4.10`，源码用 2.4 新 API（`isSorted`/`UInt.toBigInteger`） | `EXIT 1`，两处 `unresolved reference` → **2.3.21 编译器不暴露 2.4 API** |
| 10 | 同 9 但源码退化为 `fun ok(): Int = 1` | `EXIT 0`，stderr 空 → 独立 CLI 下无 stdlib 版本告警 |
| 11 | `python count_graph.py`（解析 `localFirstRelease-artifact-dependencies.xml`） | `unique coordinates 166` / `unique files 177` |
| 12 | `python scan_poms.py` / `scan_modules.py` | 缓存内 **唯 quickjs** 要求 `kotlin-stdlib 2.4.10`；其余最高 2.2.10 级别 |
| 13 | `curl -s https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-api/maven-metadata.xml` | 最新 `2.3.12`，无 2.4.x；`lastUpdated 20260909175426` |
| 14 | `gh release list -R google/ksp --limit 8` | `2.3.12 (Latest, 2026-09-09)` |
| 15 | `curl -o /dev/null -w %{http_code}` 三个 Maven 坐标（stdlib / KGP / compose-compiler-gradle-plugin 2.4.10） | 均 `200` |
| 16 | `gh api repos/gradle/gradle/issues/{13507,13703,19716,24130}` | locking+CC → 6.6 RC1；verification+CC → 8.1 RC1 |

探针脚本（临时目录，内容已在上文给出）：`collect2.py`（常量池收集）、`collect3.py`（类型面收集）、`check_classes.py`、`check_members2.py`（含 facade 类 `XxxKt__Xxx` 继承查找、`<init>` 归一）、`count_graph.py`、`scan_poms.py`、`scan_modules.py`、`probe/run_compile_check2.py`、`probe/run_stdlib24_api_check.py`。

---

## 6. 来源

**官方文档**

- Gradle dependency locking — <https://docs.gradle.org/current/userguide/dependency_locking.html>
- Gradle dependency verification — <https://docs.gradle.org/current/userguide/dependency_verification.html>
- Gradle configuration cache — <https://docs.gradle.org/current/userguide/configuration_cache.html>
- Kotlin evolution principles（二进制/API 兼容与 `-api-version`）— <https://kotlinlang.org/docs/kotlin-evolution-principles.html>
- kotlin-metadata-jvm（`readLenient`/`readStrict` 与 +1 版本窗口）— <https://kotlinlang.org/docs/metadata-jvm.html>
- Kotlin Gradle plugin（stdlib 自动依赖同版本）— <https://kotlinlang.org/docs/gradle-configure-project.html>
- Kotlin 2.4 what's new（Gradle 7.6.3–9.5.0、AGP≥8.5.2、Compose 编译器变更）— <https://kotlinlang.org/docs/whatsnew24.html>
- Compose 编译器迁移指南（随 Kotlin 发版、同版本兼容）— <https://kotlinlang.org/docs/compose-compiler-migration-guide.html>
- KSP 概览（KSP 2.3.10 支持 KGP 2.2.10–2.3.x）— <https://kotlinlang.org/docs/ksp-overview.html>

**Maven / GitHub（实跑取证）**

- KSP 版本元数据 — <https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-api/maven-metadata.xml>
- KSP releases — <https://github.com/google/ksp/releases>
- Gradle issues — <https://github.com/gradle/gradle/issues/13507>、<https://github.com/gradle/gradle/issues/13703>、<https://github.com/gradle/gradle/issues/19716>、<https://github.com/gradle/gradle/issues/24130>

**仓库内**

- 批次 21 记录（release R8 实证、钉版门）— `docs/research/2026-10-25-batch-21-record.md:174`、`:184`、`:216`
- 计划 — `.zcode/plans/plan-2026-10-26-batch-22-visualjs-process.md:7`、`:21`

---

## 7. 未验证 / 待验证清单（逐条）

1. **UNVERIFIED**：`./gradlew dependencies --write-locks` 在本仓库（Gradle 9.6.1 + AGP 9.3.0 + 15 project + 配置缓存开）的实际输出、耗时、生成的锁文件数量与体积；`lockAllConfigurations()` 是否会遇到不可解析/无意义的 AGP 配置。本线禁跑 Gradle。
2. **UNVERIFIED**：`--write-verification-metadata sha256` 若不采用，仅作为条件项；其在本仓库的条目规模（估算基于 166 坐标 + 元数据，未实跑）。
3. **UNVERIFIED**：KGP/Gradle 是否在"stdlib 比编译器新"时打印告警——独立 `K2JVMCompiler` 实测无告警，KGP 层未验。
4. **UNVERIFIED**：AGP 9 built-in Kotlin 的**编译器版本选择规则**（是否由 `compose-compiler-gradle-plugin` / `kotlin-gradle-plugin` 在构建 classpath 上的版本决定）。`developer.android.com` 两次连接超时，未取到官方页。
5. **UNVERIFIED**：路径 A 落地后全量单测 + 设备轨（含 QuickJS 12/13、KD-70 OOM 例）的表现——设计判据已给，未执行。
6. **UNVERIFIED**：R8 是否**正确重写**（而非仅仅不崩）`mv=2.4.0` 元数据；`-keep class kotlin.UByteArray` 在降版后的必要性。
7. **单一来源**：KSP 对 KGP 2.4 的不支持——依据是 `kotlinlang.org/docs/ksp-overview.html` 的表格（KSP 2.3.10 → KGP 2.2.10–2.3.x）+ Maven Central 无 2.4.x 产物（两条独立事实，但都没有"KSP 明确拒绝 2.4"的原文）；KSP 2.3.11/2.3.12 是否内部已支持 2.4 未在任何官方文本中确认，**标"待验证"**，落实前应实测一次 `./gradlew :core:database:kspKotlin`。
8. **样本范围**：`kotlin-stdlib` 传递要求扫描只覆盖本机缓存的 78 个 POM + 54 个 `.module`，不等于 release 图全部 166 个坐标；结论"只有 quickjs 要求 2.4.10"的适用范围应写作"本机缓存样本内"。
9. **探针局限**：编译探针只验证"能编译 + 引用可解析"，不验证运行时行为；QuickJS 的运行时正确性仍以设备轨为准。
