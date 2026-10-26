# 锁文件干净树前置检查（KD-72 驱动，批次 25 R3）

> 线：R3 锁文件前置｜日期：2026-10-27｜落点：本文件
>
> **本轮实际执行过的**：只读 git（status/diff/show/ls-tree/ls-files/check-ignore/log，无任何 git 写操作）、
> `python tools/ci/check_lockfiles.py`（只读静态门，exit 0）、`gh search issues` ×2、WebSearch ×1。
> **未运行 Gradle 的任何任务**；未改任何 build 文件；锁文件未生成、未提交。
> 所有"重生成后会怎样"的结论均为推导/判据，标 **待验证**，由门禁轨实跑确认。

---

## ⚠️ 0. 快照警告：树在本研究进行期间被他线实时变更

会话开始（gitStatus 快照 + 首轮 status）：全树 675 条目，全部为工作区 ` M`/`??`，无任何暂存条目。
本研究期间实测到三次状态跃迁：

| 时间点（2026-10-27） | status 总行数 | 工作区 M | 已暂存 A | ?? | 关键变化 |
|---|---|---|---|---|---|
| 会话开始 | 675 | 202 | 0 | 474 | core/markdown 为纯 `??` 目录 |
| 中段 | 676 | 202 | 0 | 474 | — |
| **01:01:00 复核** | 696 | 76 | 105 | 389 | **他线已暂存 231 文件**（`git diff --cached --stat`），含 core/markdown 模块、3 个 build 脚本、**全部 13 个项目锁 + settings 锁** |

HEAD 全程未动（`git log --oneline -2` → `d783ae84` / `aabae781`）。**门禁轨执行前必须重跑 `git status --porcelain=v1` 重新取快照**，本文所有状态均为 01:01 快照。

**阻塞级发现（需主会话裁定，本线未做任何处置）**：截至 01:01，他线已把批 23 生成的那批锁
（13 项目锁 + `settings-gradle.lockfile`）连同 markdown 迁移一起 `git add` 进暂存区。按根
`build.gradle.kts:19-24` 与 KD-72 台账（`docs/known-defects.md:2746-2748`）的裁定，这批锁
**不能原样提交**：实测（本轮）core/ui 锁不含 `richtext-commonmark`（grep=0，而 HEAD 构建脚本仍引用它）、
core/export 锁含 `kotlinx-serialization`×5（而 HEAD 构建脚本无此依赖；且 `core/export/build.gradle.kts`
在 01:01 快照中**仍未暂存**）。原样提交的后果：① `tools/ci/check_lockfiles.py` 因 `:benchmark` 缺锁判红
（`tools/ci/check_lockfiles.py:196-213`）；② DEFAULT 模式下 core/ui、core/export 在解析期报
"Did not resolve … part of the dependency lock state"（官方口径，见前序研究 §2.2）。若该暂存是有意的，
必须改为：把 `core/export/build.gradle.kts` 一并入库后**仍在干净树上重生成**（现状锁缺 test/androidTest/runtime
面，静态门照样红）——即无论如何都绕不开第 4 节的重生成流程。

---

## 1. 问一：只读 git 检查——哪些 build 文件仍待入库

### 1.1 解析图相关 build 文件（锁内容会因它们变化）

| 文件 | 01:01 状态 | diff 内容（本轮 `git diff` / `git diff --cached` 实读） | 对解析图的影响 |
|---|---|---|---|
| `settings.gradle.kts` | `M `（已暂存） | +`include(":core:markdown")`（1 行） | 新增一个会解析外部依赖的模块 → 新增 1 个项目锁 |
| `core/ui/build.gradle.kts` | `M `（已暂存） | `implementation(libs.richtext.commonmark)` → `implementation(project(":core:markdown"))` | core/ui 的运行时图**移除**外部坐标 `com.halilibo.compose-richtext:richtext-commonmark` |
| `feature/tutor/build.gradle.kts` | `M `（已暂存） | +`implementation(project(":core:export"))` | 仅 project 依赖；锁状态只记 ModuleComponent，project 依赖不进锁（前序研究 §2.7），**不改 feature/tutor 锁内容** |
| `core/markdown/build.gradle.kts` | `A `（新文件已暂存） | 整模块未跟踪→已暂存（LICENSE/README/build.gradle.kts/src/upstream-manifest.json/gradle.lockfile） | 新模块自身的解析图 → `core/markdown/gradle.lockfile` |
| **`core/export/build.gradle.kts`** | **` M`（未暂存）** | +`alias(libs.plugins.kotlin.serialization)`、+`implementation(libs.kotlinx.serialization.json)` | core/export 运行时图**新增** `kotlinx-serialization-*` 坐标；**不先入库则其锁无法与构建脚本对齐** |
| `.gitignore` | ` M`（未暂存） | +`.superdesign/tmp/` | 不影响解析图，但属"干净树"口径范围 |

### 1.2 已核实 clean 的构建面（本轮逐个 `git status --porcelain=v1 -- <file>`）

`build.gradle.kts`（根）、`app/build.gradle.kts`、`benchmark/build.gradle.kts`、`gradle.properties`、
`gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties`、`.gitattributes`、
`.github/workflows/android-check.yml`、`tools/ci/check_lockfiles.py` → 全部 clean（01:01 复核仍为 0 条目）。

### 1.3 锁文件现状（本轮实测）

- 磁盘 14 个 `gradle.lockfile`（13 项目锁 + `settings-gradle.lockfile`）+ `core/markdown/gradle.lockfile`；共 1272 行（wc -l）。
- `git ls-files '*gradle.lockfile'` → 0（HEAD 无任何跟踪锁）。
- `git check-ignore -v` 对三个样本锁 → exit 1（**无任何 ignore 规则命中**；`.gitignore` 的脏改动只加 `.superdesign/tmp/`）。
- 01:01 起 13 项目锁 + settings 锁**全部已暂存**（`A `，无 `AM`）。
- **`:benchmark/gradle.lockfile` 不存在**（`ls` 实证），暂存集中亦无。

### 1.4 "干净树重生成"还需等什么入库

1. **解析图最小集**：上表 6 个文件（4 个已暂存 + `core/export/build.gradle.kts`、`.gitignore` 2 个未暂存）。
   技术上锁内容只依赖这些；`core/export/build.gradle.kts` 不入库，core/export 的锁就永远对不齐。
2. **过程口径**：KD-72 写的是"干净、待提交的树"（`docs/known-defects.md:2748`）。其余 76 M + 389 ?? 为源码/
   文档/截图类，不影响解析图，但按台账口径应等整树提交完成。**建议门禁轨执行时以
   `git status --porcelain=v1` 无任何条目为准**（最严、最省裁定成本）。
3. **裁定项（阻塞）**：他线暂存中的那批锁必须 **unstage + 删除后重生成**，不能作为"锁已入库"提交
   （理由见 §0 阻塞级发现）。是否打断他线的提交准备，由主会话裁定。

---

## 2. 问二：benchmark 模块缺锁的补法

### 2.1 现状与成因

- 事实（批 23）：生成实跑 `exit=0`、`BUILD SUCCESSFUL in 2m 55s`、`228 actionable tasks: 22 executed, 206 up-to-date`
  （工单转录，`docs/research/2026-10-26-batch-23-record.md:185`），但 13/14 模块有锁、`:benchmark` 无锁。
- 生成时 `resolveLockableClasspaths` 尚不存在，只有各模块 `dependencies` 报告任务（根注释 14 任务列表，
  含 `:benchmark:dependencies`）。构建成功却无锁 → 仅两种解释：
  **(a)** `dependencies` 报告在 `com.android.test` 模块上未解析出任何"可声明/继承自可声明"的可解析配置，
  于是没有锁状态可写；**(b)** 生成后门禁轨清理掉了该文件。仓内无生成日志（同记录 :185"仓内无生成日志"），
  **两个假设均 UNVERIFIED**。
- 外部检索（gh `search issues --repo gradle/gradle` ×2 轮 + WebSearch ×1 轮）：无权威命中
  （唯一结果是 Charles University 一篇论文，非权威、单来源，**不采纳**）。3 轮关键词预算用尽，收敛。
  `com.android.test` × `dependencies` 报告的交互行为保持 UNVERIFIED。

### 2.2 补法（不需要新增任务，聚合任务已覆盖）

- `lockAllDependencies`（根 `build.gradle.kts:87-92`）= `subprojects.map { "${it.path}:dependencies" }` +
  `subprojects.map { "${it.path}:resolveLockableClasspaths" }`；`subprojects` 来自 settings include 列表
  （`settings.gradle.kts:33` 含 `:benchmark`）→ **`:benchmark` 两个任务都在聚合图内，无需单独跑、无需手写任务**。
- 关键差别：`resolveLockableClasspaths`（`build.gradle.kts:44-62`）在执行期按
  `isCanBeResolved && (name.endsWith("CompileClasspath") || name.endsWith("RuntimeClasspath"))` 过滤后
  **直接 `.resolve()`**，完全不依赖 `dependencies` 报告任务的 declarable 过滤——正好绕开 §2.1 假设 (a) 的失败模式。
  `benchmark` 预期配置：`debugCompileClasspath` / `debugRuntimeClasspath`（`com.android.test` 无 flavor；
  `benchmark/build.gradle.kts:15` 以 `missingDimensionStrategy("networkMode","localFirst")` 对齐 app）。
  baselineprofile 插件可能另有配置，**名字以生成物为准（待验证）**。
- 静态门对 benchmark 的口径（读码验证，`tools/ci/check_lockfiles.py:100-104,121-129`）：
  build 文件含 `com.android.test` → 走 android-test 分支，**不要求** unitTest/androidTest 配置，
  只要求 ≥1 条 `*CompileClasspath` + ≥1 条 `*RuntimeClasspath`。即 benchmark 锁的合格线就是两条 classpath。
- **判据（门禁轨生成后执行）**：`test -f benchmark/gradle.lockfile` 为真，且其配置面含两条 classpath。
- **fallback（仍缺锁时，按序）**：① 单跑 `:benchmark:dependencies --write-locks --no-configuration-cache`
  看报告实际渲染/解析了什么；② 仍无 → 属报告任务在 com.android.test 上的覆盖缺口，登记构建脚本级修复
  （例如对具名配置显式解析）——那是根 `build.gradle.kts` 改动，**不在本批落点，需主会话裁定**，本线只登记。

---

## 3. 问三：test/androidTest/runtime 锁覆盖的补全方案

### 3.1 现有 13 锁的配置面（本轮实测提取，`sed -n 's/^.*=//p' | tr ',' '\n' | sort -u`）

| 锁 | 实测配置面 | 缺口 |
|---|---|---|
| `app` | `localFirstDebug{Compile,Runtime}Classpath`、`localFirstDebugUnitTestCompileClasspath` + 工具链（androidApis/androidJdkImage/coreLibraryDesugaring/kotlin-extension/kotlinBuildToolsApiClasspath/kotlinCompilerPluginClasspath×3/annotationProcessor/ReverseMetadataValues） | 无 strictOffline×、无 release/benchmark/internal×、无 unitTest **Runtime**、无 **androidTest** 全部、无 lint |
| `core/{ui,export,markdown}` | `debugCompileClasspath` + 工具链 + `debugAnnotationProcessorClasspath` | 无 debugRuntime、无 test/androidTest、无 release |
| `core/database` | 同上 + KSP（`kspDebugKotlinProcessorClasspath`、`_agp_internal_debug_kspClasspath`） | 同上 |
| `core/data` | 同 core/ui 面 + **`litertApiUpstream`**（自定义配置被报告任务捕获并有锁） | 同上 |
| `core/{model,domain}`（JVM） | `compileClasspath` + `annotationProcessor`/`kotlinBuildToolsApiClasspath`/`kotlinCompilerPluginClasspathMain`/`kotlinScriptDefExtensions` | **无 `runtimeClasspath`、无 `testCompileClasspath`/`testRuntimeClasspath`** |
| `feature/*` ×5 | `debugCompileClasspath` + 工具链 | 同 android library |
| `benchmark` | — | 整锁缺失 |

结论：批 23 报告任务的 declarable 过滤比前序研究 §4.1 的预期**更窄**——连 JVM 的 test 面都没捕获。
这正是复核发现 #5 的实证形态。

### 3.2 补全机制（已实现，随干净树重生成生效）

`resolveLockableClasspaths`（根 `build.gradle.kts:44-62`）对每个子项目解析全部
`isCanBeResolved && (endsWith("CompileClasspath") || endsWith("RuntimeClasspath"))` 的配置。
与批 23 报告集取**并集**后，预期覆盖（配置名按 AGP 常规命名推导，**生成后按 §3.4 审计确认**）：

| 模块类型 | 并集预期覆盖 |
|---|---|
| `app`（2 flavor × 4 buildType：debug/release/benchmark/internal，`app/build.gradle.kts:13,45,50,58,69`） | 8 变体 × Compile/RuntimeClasspath + 8 × unitTest Compile/Runtime + androidTest 面（filter 全解析）+ 批 23 已捕获的工具链/annotationProcessor |
| Android library（core/{ui,export,database,data,markdown}、feature/*） | debug+release × Compile/Runtime + unitTest + androidTest + 工具链/KSP（报告集） |
| JVM（core/{model,domain}） | `compileClasspath`/`runtimeClasspath`/`testCompileClasspath`/`testRuntimeClasspath`（filter）+ 批 23 工具链 |
| `benchmark`（com.android.test） | `debugCompileClasspath`/`debugRuntimeClasspath`（filter；见 §2.2） |

### 3.3 并集之外的残余面（DEFAULT 模式不报错，登记不阻塞）

- **lint 面**：批 13 锁里没有任何 lint 配置名；`*LintChecksClasspath` 类后缀不匹配 filter → 不锁。
- **release 变体的 KSP processor classpath**（如 `kspReleaseKotlinProcessorClasspath`）：后缀
  `ProcessorClasspath` 不匹配 filter；批 23 报告集只捕获了 debug 变体。
- **JVM `kotlinCompilerClasspath`**：批 23 锁里没有；`gradle.properties:9`
  `kotlin.compiler.execution.strategy=in-process` 下可能根本不解析（推导，待验证）。
- 处置：重生成后按 §3.4 审计，残余项写入 KD-72 收口记录；`LockMode.STRICT` 探针（前序研究 §5.6）
  之前必须先解决或显式豁免。

### 3.4 门禁与审计（首个项目锁入库后全部生效）

`tools/ci/check_lockfiles.py`（读码 + 本轮实跑 exit 0）：
- 每 include project 必须有**已跟踪**锁（`settings.gradle.kts` 逐个核对）；
- 每锁 ≥1 条 `*CompileClasspath` + ≥1 条 `*RuntimeClasspath`；
- Android application/library 另需配置面含 `unittest` 与 `androidtest`；JVM 需含 `test`；
  `com.android.test` 只需两条 classpath（§2.2）；
- 拒绝 include 之外的锁文件（防未入库模块的锁单独提交）。
- `settings-gradle.lockfile` 不会被误计：`project_locks` 过滤 `endswith("/gradle.lockfile")`
  （`check_lockfiles.py:169-172`），settings 锁无 `/` 前缀路径 → 排除；也不进"include 外的锁"检查（:215）。

**配置面审计命令**（生成后跑，人工核对残余面）：

```bash
for f in app/gradle.lockfile core/*/gradle.lockfile feature/*/gradle.lockfile benchmark/gradle.lockfile; do
  echo "== $f"; sed -n 's/^.*=//p' "$f" | tr ',' '\n' | sort -u
done
```

---

## 4. 问四：锁重生成的完整前置清单与命令块（**本线未执行**）

### 4.1 前置清单（按序）

1. **[裁定]** 处置他线暂存中的 13+1 个锁：从暂存区撤下并删除（§0）。若他线提交已在途，先协调。
2. **[入库]** §1.1 全表 6 个文件随各自所属批次提交（尤其 `core/export/build.gradle.kts`；
   `core/markdown` 模块与 `settings.gradle.kts` include 同一提交——锁、模块、include 三者不可分离，R3 §6 P5）。
3. **[干净树]** `git status --porcelain=v1` 输出为空（或按主会话口径仅剩与解析图无关条目）。
4. **[环境]** JDK 21 + Android SDK 37 / build-tools 37（AGP 配置期需要，前序研究 §6 P10）；不需要签名环境变量
   （生成不含 assemble/bundle 系任务，签名门 `app/build.gradle.kts:147-153` 不会触发）。
5. **[预检]** `python tools/ci/check_dependency_pins.py` → 期望 exit 0（quickjs 1.0.15 / stdlib 2.3.21）。

### 4.2 命令块

```bash
# ---- 前置自查（时间戳快照；树在本研究期间是移动的）----
git status --porcelain=v1                       # 期望：空
git log --oneline -1                            # 确认 HEAD 已含上表 build 改动 + core/markdown

# ---- 步骤 1：清掉批 23 的陈旧锁（不得原样提交）----
git restore --staged -- '*.lockfile' 2>/dev/null   # 若仍在他线暂存区（§0）
rm -f app/gradle.lockfile core/*/gradle.lockfile \
      feature/*/gradle.lockfile benchmark/gradle.lockfile settings-gradle.lockfile
git status --porcelain -- '*.lockfile'             # 期望：空

# ---- 步骤 2：重生成（门禁轨执行；命令与 CI step 完全同参，android-check.yml:81-82）----
./gradlew --no-configuration-cache --no-parallel --console=plain \
  lockAllDependencies --write-locks > lockfile-generation.log 2>&1
echo "EXIT=$?"     # 官方口径：构建失败时锁不落盘；失败先按模块定位，勿用 --continue 抢救

# ---- 步骤 3：产物审计 ----
git status --porcelain -- '*.lockfile'
# 期望 15 个 ??：14 个项目锁（含 core/markdown 与 benchmark）+ settings-gradle.lockfile
wc -l app/gradle.lockfile core/*/gradle.lockfile feature/*/gradle.lockfile benchmark/gradle.lockfile
grep -c "org.jetbrains.kotlin:kotlin-stdlib:2.3.21" core/data/gradle.lockfile   # 期望 ≥1（useVersion 生效）
grep -c "quickjs-kt-android"                        core/data/gradle.lockfile   # 期望 ≥1
grep -c "kotlinx-serialization"                     core/export/gradle.lockfile # 期望 ≥1（构建脚本已入库）
grep -c "richtext-commonmark"                       core/ui/gradle.lockfile     # 期望 0（已换 project 依赖）
test -f benchmark/gradle.lockfile && \
  sed -n 's/^.*=//p' benchmark/gradle.lockfile | tr ',' '\n' | sort -u          # 期望含 debug 两条 classpath
# 配置面审计（§3.4），人工核对残余面（lint / release-KSP / kotlinCompilerClasspath）
for f in app/gradle.lockfile core/*/gradle.lockfile feature/*/gradle.lockfile benchmark/gradle.lockfile; do
  echo "== $f"; sed -n 's/^.*=//p' "$f" | tr ',' '\n' | sort -u
done

# ---- 步骤 4：静态门 ----
python tools/ci/check_lockfiles.py                 # 期望：OK: dependency locking 已落地：14 个 included project …

# ---- 步骤 5：负控（KD-72 剩余条件 ③；做完恢复现场）----
# 5a. 抬版：临时改 gradle/libs.versions.toml 某直接依赖 patch 版
#     → ./gradlew :core:model:dependencies            （无 --write-locks）
#     期望：解析期红（Dependency lock state out of date）
# 5b. 加依赖：在 :core:model 临时加 implementation → 期望
#     "Resolved … which is not part of the dependency lock state"
# 5c. 删依赖：临时删一条 → 期望 "Did not resolve … part of the dependency lock state"
# 5d. git restore gradle/libs.versions.toml core/model/build.gradle.kts && rm -f core/model/gradle.lockfile
#     并重跑步骤 2 恢复锁

# ---- 步骤 6：提交与 CI ----
# 锁文件与（若未入库）构建改动同一提交；推送后看 android-check.yml 两个 step：
#   "Dependency lockfile manifest and coverage gate"（始终跑）与
#   "Dependency lockfiles are up to date"（hashFiles('**/gradle.lockfile') 命中后每 PR 跑，
#    --write-locks 后 git status --porcelain -- '*.lockfile' 必须为空，android-check.yml:75-92）
# 注意：settings-gradle.lockfile 必须一并入库，否则 CI 的 porcelain 检查永远看到 1 个 ??（pathspec '*.lockfile' 含它）

# ---- 步骤 7：P1 冒烟（Gradle #38057 锁定改变 runtime classpath 顺序，9.6.1 未修）----
# 锁入库后 release/benchmark/internal 变体各做一次装机冒烟（QuickJS/Room/ML Kit 路径）
```

### 4.3 分支说明：`:core:markdown` 是否入库

- **分支 A（推荐，与现状一致）**：markdown 迁移（settings include + 模块 + core/ui 换依赖）入库 →
  14 个项目锁 + settings 锁，门按 14 核对。
- **分支 B（仅当主会话裁定 markdown 延后）**：include 不入库 → 生成时排除该模块
  （`lockAllDependencies --write-locks -x :core:markdown:dependencies -x :core:markdown:resolveLockableClasspaths`），
  锁计数 13，且其磁盘上的陈旧锁必须先删；门按 13 核对。**不允许**"模块不入库、锁入库"的中间态（门会红）。

### 4.4 KD-72 关闭判据

沿用前序研究 §7（生成入库、关键不变量、CI 双 step 绿、负控 1-3 红、常规 CI 绿 + P1 冒烟、台账收口写明未覆盖面）。
本线状态：以上全部未执行，KD-72 保持 open。

---

## 5. 未验证清单

1. **UNVERIFIED**：`lockAllDependencies --write-locks` 在干净树上的实际产物（文件数、benchmark 锁、配置面）——本线禁跑 Gradle。
2. **UNVERIFIED**：批 23 benchmark 缺锁的确切成因（§2.1 两个假设二选一或并存；仓内无生成日志）。
3. **UNVERIFIED**：`dependencies` 报告任务与 `com.android.test` 的交互（3 轮外部检索无权威命中）。
4. **UNVERIFIED**：并集覆盖矩阵中按 AGP 常规命名推导的配置名（以生成物 + §3.4 审计为准）。
5. **待验证（单一来源）**：`resolveAndLockAll` 系自定义 resolve 对 AGP 的安全性（#30480 维护者评论），前序研究已标；本仓库用的是收窄到 classpath 后缀的变体，同理。
6. **UNVERIFIED**：他线暂存行为的意图与后续（是否即将提交、是否已另行重生成锁）——本线只报告 01:01 快照。
7. UNVERIFIED：`kotlinCompilerClasspath` 在 in-process 编译策略下是否参与解析（§3.3）。

## 6. 来源

**本轮实跑（证据可复跑）**

- `git status --porcelain=v1`（三次：675/676/696 行）；`git log --oneline -2` → HEAD `d783ae84`
- `git diff`（4 个 build 文件 + `.gitignore`）；`git diff --cached --name-only --stat`（231 文件）
- `git show HEAD:settings.gradle.kts`（HEAD 无 `:core:markdown`）；`git ls-tree HEAD core/markdown/`（空）
- `git check-ignore -v core/markdown/gradle.lockfile benchmark/gradle.lockfile app/gradle.lockfile` → exit 1
- `python tools/ci/check_lockfiles.py` → `EXIT=0`，输出"13 个未跟踪锁…缺锁 project：:benchmark"
- 锁配置面提取与不变量 grep（§1.3/§3.1 的全部数字）；`gh search issues --repo gradle/gradle` ×2（空）
- WebSearch ×1（仅非权威论文命中，未采纳）

**仓库内（本轮实读）**

- 根构建与锁基建：`build.gradle.kts:19-33,36-62,86-92`
- 模块清单：`settings.gradle.kts:19-34`（工作树）/ HEAD 版无 `:core:markdown`
- benchmark 模块：`benchmark/build.gradle.kts:1-41`；app 变体：`app/build.gradle.kts:13,44-69`
- 静态门：`tools/ci/check_lockfiles.py:100-104,115-130,156-223`；CI 双 step：`.github/workflows/android-check.yml:70-92`
- KD-72 台账：`docs/known-defects.md:2732-2748`
- 前序研究（机制与官方引用）：`docs/research/2026-10-26-lockfile-generation.md`（§2.2/§4/§5.1/§6/§7/§10）
- 批 23 生成结果与门禁转录：`docs/research/2026-10-26-batch-23-record.md:56-59,111-115,185`
- core:markdown 相遇预判：`docs/research/2026-10-26-batch-23-baseline.md:176,281`

**外部（沿用前序研究已核来源，本轮未新增权威命中）**

- Gradle 9.6.1 dependency locking：<https://docs.gradle.org/9.6.1/userguide/dependency_locking.html>
- Gradle #30480（AGP 配置解析成本）：<https://github.com/gradle/gradle/issues/30480>
- Gradle #38057（9.6.x 锁定下 classpath 顺序）：<https://github.com/gradle/gradle/issues/38057>
- home-assistant/android 锁形态参照（Apache-2.0）：<https://raw.githubusercontent.com/home-assistant/android/main/build.gradle.kts>
