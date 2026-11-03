# 多模块 dependency locking 的写入窗口与 KD-72 可行路径（批次 42 R5，2026-11-04）

> 线：R5 研究（计划 `.zcode/plans/plan-2026-11-04-batch-42.md:20` = 研究项 5，对应实施项 67「KD-72 锁文件窗口：隔离副本尝试 `--write-locks`，失败则最小等待清单」）。
> 纪律：**零 Gradle、零 git 写、零锁文件动作、零设备**。只读 git / 只读盘上文件 / 一次只读 Python 门脚本（`check_lockfiles.py` 的 `check()`，注入 tracked 列表）/ 官方文档与 gradle/gradle 源码/issue。
> 历史基线：`docs/research/2026-11-02-kd72-lock-window.md`（分支 B）、`docs/research/2026-10-28-lock-window-and-batch30-prep.md`（W1–W7）、`docs/research/2026-10-27-lockfile-execution-plan.md`（分支 A 执行序列）。**本文不重写这些内容**，只在与本轮实测冲突时逐条更正。

---

## 0. 结论速览

1. **`--write-locks` 的真实语义比历次记录里写的更"窄"也更"黏"**：
   - 只写**本次构建解析过**的配置；未解析的配置不写（官方原文 "create or update the lock state for each resolved configuration during that build execution"）。
   - **是合并（merge）不是重写**：写入前会先读入已有锁文件（`loadLockState()` 无条件加载），再按 lockId 覆盖本次解析过的条目，**未解析的旧条目原样保留**（源码 `DefaultDependencyLockingProvider.java:121-129,218-231`）。历次记录把这写成 "overwritten"，严格说只对"被解析的 lockId"成立。
   - **构建失败不写盘**：写入挂在 `BuildModelLifecycleListener.beforeModelDiscarded(model, buildFailed)` 且 `if (!buildFailed)`（`DefaultDependencyManagementServices.java:525-534`）。
   - 锁内容**逐字节可复现**：模块行 `Collections.sort`、依赖→lockId 用 `TreeMap`、配置名 `.sorted()`，无时间戳、无绝对路径（本轮实测 14 个文件的 grep 全零命中）。
2. **因此"隔离副本写锁"在机制上成立**（锁内容 = f(解析输入集合, 旧锁文件)，与仓库绝对路径无关），**前提是把旧锁文件这个输入也归一化**：写入是合并语义，所以副本里必须和真树一样处于"无旧锁"状态，否则拷回来的内容会掺进旧条目。
3. **真正卡住 KD-72 的不再是"热区 build 文件脏"**：本轮实测 `git status --porcelain -- '*.gradle.kts' 'gradle.properties' 'gradle/libs.versions.toml' 'gradle/wrapper/*' .gitignore` **只输出 ` M .gitignore`**，而 `.gitignore` 不是依赖解析输入。历次"窗口关闭"判据里的 W1/W2（树收口）对**写入**这一步已经不再是前置，对**提交**那一步仍然是。
4. **新的头号风险是覆盖完整性**：把盘上 13 个锁当作"已跟踪"喂进 `check_lockfiles.py` → **36 条错**（本轮实跑，与批次 41 记录的"严格模拟 36 错"同数）。现有锁只覆盖 debug flavor 的 `*CompileClasspath` + 工具链配置，多数模块**没有 `*RuntimeClasspath`、没有 unitTest/androidTest**。只写锁不做冒烟验证，落地即红门。
5. **裁定（研究结论，非执行）**：KD-72 走 **分支 A′（隔离副本写入 → 幂等复跑 → 拷回 → 门校验 → 负控）**，在门禁阶段串行执行；写入本身**不需要**等树收口，**提交**必须紧接写入且期间无任何 build 脚本变更。最小等待清单见 §6。

---

## 1. 本轮读数（全部为本次实跑/实读）

| 读数 | 命令 | 结果 |
|---|---|---|
| HEAD | `git rev-parse --short HEAD` | `faab17f5` |
| 工作树 | `git status --porcelain \| wc -l` | **543**（`105 " M"` + `444 "??"`） |
| 暂存区 | `git diff --cached --name-only \| wc -l` | **0** |
| 已跟踪锁 | `git ls-files '*gradle.lockfile' \| wc -l` / `git ls-files '*lockfile' \| wc -l` | **0** / **0** |
| 未跟踪锁 | `git status --porcelain \| grep -i lockfile` | **14** 条（13× `<module>/gradle.lockfile` + `settings-gradle.lockfile`） |
| 解析输入面 | `git status --porcelain -- '*.gradle.kts' 'gradle.properties' 'gradle/libs.versions.toml' 'gradle/wrapper/*' .gitignore` | **仅 ` M .gitignore`** |
| 旧格式残留 | `ls gradle/dependency-locks` | 不存在 |
| `:benchmark` 锁 | `ls benchmark/gradle.lockfile` | 不存在（目录内只有 `build.gradle.kts`、`src`） |
| settings 锁内容 | `head settings-gradle.lockfile` | 5 行，仅 `empty=incomingCatalogForLibs0`（**零外部坐标**） |
| 锁总量 | `cat … \| wc -c`；`wc -l app/gradle.lockfile` | **101,573 B**；app 263 行 |
| 锁内容绝对路径 | `grep -nE "[A-Za-z]:[\\\\/]"` 与 `grep -nE "=([A-Za-z]:[\\\\/]\|/)"`（14 文件） | **零命中** |
| 锁文件换行 | `grep -q $'\r'` | **LF**；`git check-attr -a` → `text: auto`；`core.autocrlf=true` |
| 门脚本（跳过态） | `python -I tools/ci/check_lockfiles.py` | `EXIT=0`，提示"13 个未跟踪锁文件…不能原样提交；缺锁 project：:benchmark" |
| 门脚本（强制态，注入 tracked） | `python -I -c "… check(root, tracked=[13 个路径])"` | **36 errors**（§5.2 明细） |
| 隔离副本规模 | `git archive --format=tar HEAD \| tar -t \| wc -l`；`… \| wc -c` | **3,865 条目 / 469,749,760 B（≈470 MB）** |
| wrapper 是否随 HEAD | `git ls-files gradlew gradlew.bat gradle/wrapper/*` | 4 个全部已跟踪（复制可自举） |
| `local.properties` | `git ls-files local.properties`；`cat` | **未跟踪**（`.gitignore:14`），内容 `sdk.dir=C\:\\Android\\Sdk`；`/c/Android/Sdk` 存在 |
| Gradle 发行版 | `ls .gradle/wrapper/dists` | **`gradle-9.6.1-bin` 已在本树 `.gradle`**（wrapper 声明 `gradle-9.6.1-bin.zip` + sha256） |
| 依赖缓存 | `ls .gradle/caches/modules-2/files-2.1 \| wc -l` | **142** 个 group 目录（缓存已填充） |
| JDK | `java -version`；`$JAVA_HOME` | Temurin **21.0.11**；`C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` |
| 磁盘 | `df -h /d` | D: 364 GB 可用（470 MB 副本无压力） |

> 与历史记录的差异（须登记）：`core/markdown` 已入库（`git ls-files core/markdown` = 17），故"盘上 14 = git 可见 14"（不再有 13/14 的折叠口径差异）；`settings.gradle.kts`、`core/ui/build.gradle.kts`、`feature/tutor/build.gradle.kts`、`core/export/build.gradle.kts` **本轮实测均干净**（2026-11-02 记录里 W1 = 1/6 脏，2026-10-28 记录里 J1 红；本轮为 1/6 脏且唯一残项是非解析输入的 `.gitignore`）。

---

## 2. `--write-locks` 的副作用清单

### 2.1 写什么、写在哪

| 事实 | 来源 | 对本仓的后果 |
|---|---|---|
| 每个 project 一个 `gradle.lockfile`，位于 project 根；文件名常量 `UNIQUE_LOCKFILE_NAME = "gradle.lockfile"` | 官方文档（锁文件位置/格式）+ gradle/gradle `LockFileReaderWriter.java:55` | 14 个 included project → 理论 14 个文件；`settings` 侧另有 `settings-gradle.lockfile`（本仓实测存在，仅 `empty=incomingCatalogForLibs0`） |
| 行格式 `group:artifact:version=configA,configB`；空解析写 `empty=configA,…` | 官方文档 + 源码 `EMPTY_RESOLUTIONS_ENTRY = "empty="`（`:60`） | 锁文件是"坐标 × 配置名"的纯函数，见 §4.1 |
| 只写**本次构建解析过**的配置 | 官方文档："create or update the lock state for each resolved configuration during that build execution" | 未解析 = 无锁状态；后续真实构建解析到它时按 DEFAULT 判红（§5.3） |
| `--write-locks` 是全局旗标，不能按 project/配置收窄 | 官方文档（"This command will lock all resolvable configurations that have locking enabled"） | 只能用聚合任务一次性写全，不能"只写 app" |
| 空锁状态时**删除**该 project 的锁文件（`writeUniqueLockfile`: `if (lockState.isEmpty()) deleteQuietly(...)`）；但 `buildFinished` 有 `uniqueLockStateLoaded` 门——本次一个可锁配置都没解析到时**既不写也不删** | 源码 `LockFileReaderWriter.java:210-218` + `DefaultDependencyLockingProvider.java:232-247` | 现有 14 个文件会解析到东西，本项实际无影响；若要"清掉某模块的锁"，必须显式删文件，不能指望写锁自动清 |
| 顺带清理 Gradle ≤6.3 的旧格式 `<lockId>.lockfile` | 源码 `cleanupLegacyLockFiles`（`:234-239`） | 本仓无 `gradle/dependency-locks/`，实际无副作用 |

### 2.2 合并语义（历次记录最大的口径偏差）

`DefaultDependencyLockingProvider`（master，与 9.6.1 jar 内字符串一致）：

- `loadLockState(String lockId, …)` **无条件**先 `loadLockState()` 把整份旧文件读进 `allLockState`（`:121-123,181-190`）；写锁模式下跳过校验分支（`if (!writeLocks || partialUpdate)`）。
- `persistResolvedDependencies(...)` 只 `allLockState.put(lockId, modulesOrdered)`（`:224-230`）。
- `buildFinished()` 成功时一次性 `writeUniqueLockfile(allLockState)`（`:232-247`）。

⇒ **`--write-locks` = "旧文件 ∪ 本次解析结果（后者覆盖同名 lockId）"**。

可操作结论（这是本文最重要的一条工程推论）：

1. 盘上 14 个旧锁（生成于 2026-10-26，绑定他线未提交的 build 改动）如果原样留在树里再写，**旧条目会残留**，产出的锁不是"纯解析结果"。
2. 隔离副本必须和真树处于**同一锁状态输入**下，才保证"副本写出的内容 = 真树写出的内容"。干净输入 = **无锁文件**（HEAD 里本来就没有，`git ls-files` = 0）。
3. 所以建议把 14 个旧锁**移出树**（移到 `.tmp/`，不删除、不入库），再在副本里写；否则按 §4.2 的等式不成立，拷回内容无法证明与真树一致。

### 2.3 失败与中断

| 事实 | 来源 |
|---|---|
| 构建失败**不写盘** | `DefaultDependencyManagementServices.java:525-534`：`beforeModelDiscarded(model, buildFailed)` 内 `if (!buildFailed) dependencyLockingProvider.buildFinished()`；官方文档同句（"Gradle won't write the lock state to disk if the build fails"） |
| 写入发生在"模型丢弃"时（一次性、每 project 一次），不是逐配置落盘 | 同上 |
| `--write-locks` **不做**锁校验 | `loadLockState` 在 `writeLocks` 时返回 `EMPTY_LOCK_CONSTRAINT`，即"当作没有锁"来解析 |

⇒ 写锁**绿**只证明"解析全部成功"，**不证明**锁覆盖了真实构建会用到的配置；覆盖要靠 §5.3 的冒烟验证与 §6 的负控。

### 2.4 配置缓存（本仓 `gradle.properties` 默认开启）

| 事实 | 来源 | 影响 |
|---|---|---|
| 命中 `--write-locks` 时 Gradle 主动**不复用**配置缓存，并重新 store 一条：`case startParameter.isWriteDependencyLocks -> "… as configuration cache cannot be reused due to --write-locks"` | gradle/gradle `DefaultConfigurationCache.kt:473-481`（master）；9.6.1 jar 的 `DefaultConfigurationCache.class` 常量池含 `isWriteDependencyLocks` / `--write-locks` 字符串 | 即使忘了 `--no-configuration-cache` 也不会写坏锁；只会让配置缓存 miss 一次 |
| 任务带 `notCompatibleWithConfigurationCache` 时，该任务在图中会导致配置状态**被丢弃**（不是 build 失败），除非 `configuration-cache-problems=warn` | 官方 javadoc（`Task.notCompatibleWithConfigurationCache`，9.6.1） | 本仓 `build.gradle.kts:47` 的 `resolveLockableClasspaths` 已声明；canonical 命令仍应显式 `--no-configuration-cache`（与 CI 同参），避免"一次 BC 写入顺带把 CC 状态清掉"这种对共享树的隐形副作用 |
| 锁文件是配置缓存的**输入** | 官方文档 `configuration_cache.html`（配置输入列表含 "dependency lock files"） | 锁入库后每次改锁都会使 CC 失效——正常，无需处理 |

### 2.5 明确不被 `--write-locks` 覆盖的面（本仓相关）

| 面 | 现状 | 判定 |
|---|---|---|
| `buildscript` / `pluginManagement` classpath | 根 `build.gradle.kts` 只有 `subprojects { dependencyLocking { lockAllConfigurations() } }`；官方文档的 buildscript 锁文件名是 `buildscript-gradle.lockfile`，本仓不存在 | **不锁**。`settings-gradle.lockfile` 本仓实测只有 `incomingCatalogForLibs0` 的空解析，**不含插件坐标** ⇒ 供应链面对"插件版本被抬"无锁约束（KD-72 范围外的已知残余，登记不扩大范围） |
| `files(...)` 本地依赖 | `core/data/build.gradle.kts:186` `implementation(files(patchLitertApiAar))`（AAR 由任务在 `build/litert-patched/` 派生） | 不是模块依赖，不进锁；但**副本必须能跑出这个任务**（本项只需解析，不需要它产出，见 §4.3 风险 R3） |
| detached configuration（插件/任务内 `configurations.detachedConfiguration(...)`） | 不在 project 配置容器内，`lockAllConfigurations()` 不适用 | 不锁，也不会因缺锁判红（无锁状态可容忍于非锁定配置？见 §5.3 的判据边界，标**待验证**） |
| `:benchmark` | `com.android.test` + baselineprofile 插件 + `targetProjectPath = ":app"` | 写入会覆盖它（`subprojects` 含 benchmark）；现状缺锁，需写入后核对 |

---

## 3. 与共享工作树 / 并发构建的冲突

### 3.1 机制层（官方 + 源码）

| 事实 | 来源 | 含义 |
|---|---|---|
| 依赖缓存用**文件锁**支持多进程并发，但"only supported if the different Gradle processes can communicate together" | 官方 9.6.1 文档 `dependency_caching.html`「Cache locking」 | 同机多进程共享 `GRADLE_USER_HOME` 是**受支持**形态；容器/不同机共享缓存不安全（本机不涉及） |
| 文件锁**等待 + 重试**，默认超时 **60 s**，超时抛 `LockTimeoutException`，文案 `"Timeout waiting to lock %s. It is currently in use by another process."`；debug 日志 `"Waiting to acquire {} lock on {}."` | gradle/gradle `DefaultFileLockManager.java`（master；`DEFAULT_LOCK_TIMEOUT = 60000`） | 同一 project 目录里第二个构建会**排队**，超过 60 s 直接失败——不是"自动让路" |
| 同一 project 目录的两个构建争用同一套 project cache 锁 | **推理**（由"共享缓存用文件锁"+"单一 `.gradle` 目录"推出；官方文档未找到明文句子，两次检索零命中） | 标**待验证**；门禁期可用 §9 的探针命令取证 |

### 3.2 本仓已发生的实证

- 批次 41 P18 窗口内他线在同一工作树跑了 6 次 Gradle（03:00–03:13）并改了两个文件，`exit=0` 但**不构成干净窗口**（`docs/research/2026-11-03-batch-41-record.md:234`，F21）。这就是"共享工作树 + 并发构建"在**读**方向的后果：结论仍可成立，但归因被污染。
- 对**写**方向，后果更重：`--write-locks` 把整棵树的锁状态（10 万字节 / 15 个文件）一次性重算，期间任何别的线"顺手"改 build 脚本，写出来的锁就绑定了未提交的解析图——这正是 2026-10-26 那批旧锁报废的原因。

### 3.3 共享工作树下的四个具体冲突面

| # | 冲突 | 触发条件 | 症状 | 规避 |
|---|---|---|---|---|
| C1 | project cache 锁争用 | 同一目录并发 Gradle | 排队；>60 s → `LockTimeoutException`（构建整体失败，且失败时**不写锁**，so 最坏是白跑） | 串行；网关期预约窗口 |
| C2 | 解析图被污染 | 写入期间别人改 `*.gradle.kts`/catalog | 锁绑定未提交的图 → 与 HEAD 不一致 | 隔离副本（§4）根本性消除 |
| C3 | 缓存写入 | 首次解析 release/lint/androidTest 配置会新下载构件 | 共享 `.gradle/caches` 变大；文件锁保证安全 | 可接受；如需完全隔离则用独立 `GRADLE_USER_HOME`（代价：全量重下） |
| C4 | 内存/CPU | `org.gradle.jvmargs=-Xmx6g` + `org.gradle.parallel=true` | 与其他线抢资源，构建变慢甚至 OOM | 写入命令显式 `--no-parallel`（与 CI 同参）；窗口内不并行别的重活 |

---

## 4. 隔离副本可行性

### 4.1 关键问题：锁内容是否与仓库绝对路径无关？

**是（有边界）。** 三条独立证据：

1. **实证**：14 个锁文件里 `[A-Za-z]:[\\/]` 与行首/`=` 后 `/` 的绝对路径扫描**零命中**；内容只有 `group:artifact:version=配置名` 与 `empty=…`（§1）。
2. **源码**：写入路径 `writeUniqueLockfile` → `getModulesOrdered` = `Collections.sort` 后的 `group:name:version` 字符串；依赖→配置映射用 `TreeMap`，配置名 `.sorted()` 拼接；无时间、无主机、无路径（`DefaultDependencyLockingProvider.java:248-256`、`LockFileReaderWriter.java:250-262`）。
3. **官方**：格式定义就是 `group:artifact:version` + 配置名列表（9.6.1 `dependency_locking.html`）。

**边界（路径/环境仍会影响解析结果的输入）**：

- `local.properties` 的 `sdk.dir`（本仓 `C:\Android\Sdk`）——影响 AGP 能否配置，不影响锁内容；
- 运行 JDK（本轮 `21.0.11`）——影响 `org.gradle.jvm.version` 属性选择，**必须与真树同 JDK**；
- OS/ABI 相关变体选择（本仓 ABI 走 `abiFilters`，不参与 Gradle 变体选择）；
- 仓库内容（`google()`/`mavenCentral()` 的上游不可变假设）；
- **旧锁文件**本身（§2.2 合并语义）——这是最容易漏掉的一项。

### 4.2 等价性命题（本方案的立论）

设 `R` = 解析输入集合（`settings.gradle.kts`、所有 `build.gradle.kts`、`gradle/libs.versions.toml`、`gradle.properties`、wrapper 版本、`local.properties` 的 SDK、JDK 版本、仓库地址），`L0` = 写入前的锁状态，`res(R)` = 该构建解析到的 lockId→模块集合，则

```
write(R, L0) = (L0 \ dom(res(R))) ∪ res(R)
```

- **真树**：`L0` = 14 个旧锁（非空）→ 结果掺旧条目。
- **副本**：`L0` = ∅（HEAD 无锁）→ 结果 = `res(R)` 纯函数。
- 两者相等 ⇔ 真树也取 `L0 = ∅`。⇒ **先把 14 个旧锁移出树**，等式成立，副本产物可整段拷回。

这正是历次"必须在干净树上重生成"要求的**机制级解释**：真正的约束不是"树要干净"，而是"**解析输入要走 HEAD、锁状态输入要为空**"。

### 4.3 副本的构造与风险

| 项 | 做法 | 风险 |
|---|---|---|
| 快照 | `git archive HEAD`（3,865 条目 / 470 MB，**不写 .git**）→ 解到空目录 | 必须排除 `.worktrees/`（`git archive` 天然只含 HEAD 跟踪内容） |
| 未跟踪但必需 | `local.properties`（`sdk.dir`）、可选 `.toolchains`（本仓不存在，不用） | 漏拷 → AGP 配置期报 "SDK location not found" |
| Gradle 发行版 | `GRADLE_USER_HOME` 指向真树 `.gradle`（已有 9.6.1 + 142 个 group 模块缓存） | 共享缓存写入（C3）；如要零共享需自建 `GRADLE_USER_HOME` → 全量重下 |
| JDK | 与真树同 `JAVA_HOME`（`21.0.11`） | 换 JDK ⇒ 锁内容可能与真树不同（属性选择） |
| 网络 | **不要 `--offline`**（release/lint/androidTest 配置的构件可能从未在本机下载过；`--offline` 下解析失败 = 不写锁） | `--offline` 可行性本轮 **UNVERIFIED** |
| R3：本地 AAR | `core/data` 的 `litertApiUpstream` 需解析到上游 AAR（模块依赖，写锁会覆盖它）；`patchLitertApiAar` 是**执行期任务**，本方案不跑它，无需产物 | 若将来把写入扩成"顺带编译"，此处会要求 `build/` 产物 |
| R4：路径形态 | 真树路径含空格（`D:\smart mistake book`），`gradle.properties` 有 `android.overridePathCheck=true`、并有"Windows 用 SUBST ASCII 路径跑 Gradle"的注释（`gradle.properties:11-13`） | 副本目录建议**无空格短路径**（如 `D:\lockprobe`），避免 AGP/工具链把空格当分隔符 |
| R5：`:benchmark` | `com.android.test` + `targetProjectPath=":app"`，配置期会拉 app 变体 | 写入时一并解析；`check_lockfiles.py` 对 `com.android.test` 不做 unitTest/androidTest 要求（脚本 `:126`） |

### 4.4 副本内的命令序列（门禁阶段执行，本轨不跑）

```bash
# 0) 真树：把 14 个旧锁移出（不删、不入库），并记录清单
mkdir -p .tmp/lock-stale-20261104
git status --porcelain | grep -i lockfile | awk '{print $2}' | xargs -I{} mv {} .tmp/lock-stale-20261104/
git status --porcelain | grep -ci lockfile        # 期望 0

# 1) 建副本（不写 .git）
mkdir -p /d/lockprobe && git archive --format=tar HEAD | tar -x -C /d/lockprobe
cp .gitignore /d/lockprobe/.gitignore             # 仅为让 Gradle 行为一致（可选）
cp local.properties /d/lockprobe/local.properties # sdk.dir=C:\Android\Sdk

# 2) 写入（与 CI 同参；共享 GRADLE_USER_HOME 复用已填充缓存）
cd /d/lockprobe
GRADLE_USER_HOME="/d/smart mistake book/.gradle" JAVA_HOME="$JAVA_HOME" \
  ./gradlew --no-configuration-cache --no-parallel --console=plain \
    lockAllDependencies --write-locks

# 3) 幂等复跑（CI 的同类判据）：第二次写必须零 diff
find . -maxdepth 2 \( -name 'gradle.lockfile' -o -name 'settings-gradle.lockfile' \) -print0 \
  | xargs -0 sha256sum | sort -k2 > /tmp/lock-sha1.txt          # 期望 15 行
GRADLE_USER_HOME=... ./gradlew --no-configuration-cache --no-parallel --console=plain \
  lockAllDependencies --write-locks
find . -maxdepth 2 \( -name 'gradle.lockfile' -o -name 'settings-gradle.lockfile' \) -print0 \
  | xargs -0 sha256sum | sort -k2 | diff - /tmp/lock-sha1.txt    # 必须无输出

# 4) 覆盖冒烟（在副本里，锁已在位）：CI 用到的解析面
GRADLE_USER_HOME=... ./gradlew --no-configuration-cache --console=plain \
  :app:assembleLocalFirstDebug :benchmark:assemble \
  :app:compileStrictOfflineDebugUnitTestKotlin :app:compileStrictOfflineDebugAndroidTestKotlin \
  :core:domain:testClasses :app:lintLocalFirstDebug
# 任何 "Resolved … which is not part of the dependency lock state" ⇒ 覆盖不全，回 §5.3

# 5) 拷回 15 个文件（保持 LF；不加 -a 避免带权限位）
cd /d/lockprobe && find . -maxdepth 2 \( -name 'gradle.lockfile' -o -name 'settings-gradle.lockfile' \) \
  | while read f; do cp "$f" "/d/smart mistake book/$f"; done
# 期望拷回 15 个：13 个 <module>/gradle.lockfile + benchmark/gradle.lockfile + settings-gradle.lockfile
```

### 4.5 副本方案不能消灭的四件事

1. **覆盖完整性**（§5.2/5.3）——副本只保证"写出来的是解析结果"，不保证"解析面齐全"。
2. **负控**（人为抬版必须判红）——必须真跑一次破坏性实验。
3. **提交时点耦合**——锁必须在"最后一次 build 脚本变更"之后写入、且提交前不再变更。
4. **插件 classpath 无锁**（§2.5）——不在 KD-72 修复范围，只登记。

---

## 5. KD-72 的可行路径

### 5.1 路径选择

| 分支 | 内容 | 裁定 |
|---|---|---|
| A（2026-10-27 执行计划原案） | 干净树上直接写 | **不再必要**：写锁对源码洁净度无要求；且"等树收口"永远等不到（543 条） |
| B（2026-10-28/11-02 结论） | 窗口不可行、零动作 + 等待清单 | **本轮修正**：不可行的判据被替换（见 §5.4） |
| **A′（本文建议）** | 隔离副本写 → 幂等复跑 → 覆盖冒烟 → 拷回 → 门校验 → 负控 → 与 build 脚本同批提交 | **采纳** |

### 5.2 现状缺口（实跑明细）

`check_lockfiles.py` 的强制态判据（注入 tracked 列表后实跑，等价的 `python -I tools/ci/check_lockfiles.py` 在锁入库后就会进入该分支）：

- **36 条错**，分布：
  - `:app`：缺 androidTest 配置（1）
  - 12 个模块缺 `*RuntimeClasspath`（`core:model/domain/database/data/export/ui/markdown`、`feature:capture/library/profile/review/tutor`）
  - 10 个 Android 模块缺 unitTest + androidTest（`core:database/data/export/ui/markdown`、`feature:*5`）
  - `:core:model`、`:core:domain` 另缺 JVM test 配置（2）
  - `:benchmark` 缺锁文件（1）
  - 合计 1 + 12 + 10 + 10 + 2 + 1 = **36**，与实跑列表逐条对齐
- 与批次 41 记录 `:133`「锁 14 个未跟踪、严格模拟 36 错」**完全同数**——本轮独立复现，说明这不是脚本口径变了，而是那批锁确实不可提交。

### 5.3 覆盖完整性：`--write-locks` 与"真实构建"之间的缝

- DEFAULT 语义（9.6.1 文档原文）："Existing entries in the lock state must be matched in the build"、"A version mismatch or **missing resolved module** causes a build failure"、"Resolution result must not contain extra dependencies compared to the lock state"。
- 官方 tracker 的现成反例：gradle/gradle **#21396（open，label `in:dependency-locking`）**——`lockAllConfigurations()` + `--write-locks` 之后，`testCompileClasspath` 上**同版本**的模块因为只出现在 `testRuntimeClasspath` 条目下而判红（`Resolved 'org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21' which is not part of the dependency lock state`）。
- ⇒ 本仓的 8 个 app 变体（2 flavor × 4 buildType）+ androidTest/unitTest + `:benchmark` + lint/kover 都会被 CI 解析；锁只要漏一个 lockId，**落地即红 CI**。
- 本仓聚合任务的覆盖面：`dependencies`（逐 project，解析容器内可解析配置）+ `resolveLockableClasspaths`（按名字后缀 `*CompileClasspath`/`*RuntimeClasspath`）。现有锁的配置清单（§1 表 + 下列实测）说明**旧锁远未覆盖**：
  - `app`：13 个配置全是 `localFirstDebug*` + 工具链（无 strictOffline、无 release、无 androidTest、无 unitTestRuntime）；
  - 大部分 feature/core 模块：只有 `debugCompileClasspath` + 工具链（无 runtime）；
  - `core/data` 含 `litertApiUpstream`（说明 `dependencies` 确实解析了自定义配置）；`core/database` 含 `_agp_internal_debug_kspClasspath`（同上）。
- 结论：`resolveLockableClasspaths` 是否真能补齐 runtime/test/androidTest 面 **UNVERIFIED**（KD-72 台账亦记"该任务未运行 Gradle 验证"）。所以**写入之后必须有覆盖冒烟**，否则等于把"未验证的覆盖"提交上去。

### 5.4 判据的替换（为什么"窗口关闭"不再成立）

| 2026-10-28/11-02 判据 | 本轮为何替换 |
|---|---|
| J1/W1「六文件入库干净」 | 本轮实测只剩 `.gitignore`（非解析输入）→ **对写入不再是阻塞**；对提交仍是（它属热区，不归本轨） |
| J3/W2「`git status` = 0」 | 与锁内容的因果链不成立（锁 = f(解析输入, 旧锁)）。改为可判定的**解析输入面**判据 + **锁状态输入为空**判据 |
| W4「14 个旧锁不得 `git add`」 | 依然成立，且升级为"必须移出树"，因为合并语义会把旧条目带进新锁 |
| W5「`:benchmark` 缺锁」 | 依然成立，但由写入自动覆盖（需核对） |
| W6「负控从未执行」 | 依然成立，仍是关闭判据之一 |
| W7「Gradle 未跑」 | 依然成立（本轨禁跑） |

---

## 6. 最小等待清单（更新版）

### 6.1 真阻塞（不做完就不能声称 KD-72 有进展）

| # | 等待项 | 判据（可机器核） | 本轮实测 | 责任 |
|---|---|---|---|---|
| G1 | **门禁窗口**（唯一能跑 Gradle 的阶段，串行） | 写入期间同目录无第二个 Gradle；日志无 `Waiting to acquire … lock` > 60 s | 未满足（本轨禁跑） | 门禁轨 |
| G2 | **锁状态输入归一** | `git status --porcelain \| grep -ci lockfile` = 0（旧锁已移出 `.tmp/`） | 当前 14 条未跟踪 | 门禁轨（先移出再写） |
| G3 | **解析输入 = HEAD** | `git status --porcelain -- '*.gradle.kts' 'gradle.properties' 'gradle/libs.versions.toml' 'gradle/wrapper/*'` 为空 | **已满足**（仅 `.gitignore` M，非解析输入） | 维持；写入后到提交前不得再变 |
| G4 | **写入成功** | `lockAllDependencies --write-locks` exit=0；日志含 `Persisted dependency lock state for :…`（per project） | UNVERIFIED | 门禁轨 |
| G5 | **幂等** | 第二次写后 `git status --porcelain -- '*.lockfile'` 无输出 / sha256 全等（CI 同款判据 `.github/workflows/android-check.yml:75-92`） | UNVERIFIED | 门禁轨 |
| G6 | **覆盖冒烟** | 副本内跑 CI 解析面（`assembleLocalFirstDebug`、`:benchmark:assemble`、`compileStrictOfflineDebug*UnitTest/AndroidTest`、`lintLocalFirstDebug`）**零** "not part of the dependency lock state" | UNVERIFIED | 门禁轨 |
| G7 | **门脚本** | 15 个锁 `git add` 后 `python -I tools/ci/check_lockfiles.py` exit=0（`git ls-files` 读索引，暂存即可） | 今天强制态 36 错 | 门禁轨 |
| G8 | **负控** | 人为把某传递依赖抬一个版本 → 解析期红（错误含 "not part of the dependency lock state" 或版本不符）；还原后复绿 | 从未执行 | 门禁轨 |
| G9 | **提交顺序** | 15 个锁与"最后一次 build 脚本变更"同批提交；提交前无 `*.gradle.kts`/catalog 改动 | 未开始 | 主会话（本轨不做 git 写） |

### 6.2 不再是阻塞（可以销账的旧等待）

- **W1 六文件入库**：实质满足（`settings.gradle.kts`、`core/ui`、`feature/tutor`、`core/export` 的 build 脚本均干净；`core/markdown` 已入库；仅 `.gitignore` M，且它不是解析输入）。
- **W2 树收口**：对**写入**不是前置；对**提交**仍需（543 条里含他线改动，提交必须分块且不含热区）。
- **J1c「HEAD 侧已去 richtext-commonmark / 已引入 serialization」**：已被现状覆盖（build 脚本干净，HEAD 即现状）。

### 6.3 外部排序依赖（会一次作废重写）

- **Keeper / R-04 裁定**（批次 42 实施项 69）：若 `usefulness/keeper` 落进 `pluginManagement`，`settings-gradle.lockfile` 会新增插件 classpath 坐标、`:app` 锁可能新增 R8 坐标 → **锁必须排在它之后写**，否则要写两遍。当前 `settings-gradle.lockfile` 只有 `empty=incomingCatalogForLibs0`，无插件坐标。

---

## 7. 未验证 / 待验证（如实）

| # | 项 | 等级 | 说明 |
|---|---|---|---|
| U1 | `resolveLockableClasspaths` 是否真能把 runtime/unitTest/androidTest 写进锁 | **UNVERIFIED** | 本轨禁跑 Gradle；只能由 G4/G6 证明 |
| U2 | 写入耗时 / 是否需要联网 | **UNVERIFIED** | 推断需要联网（release/lint 配置的构件可能未缓存）；`--offline` 可行性未知 |
| U3 | 同 project 目录并发构建的官方明文 | **推理 + 待验证** | 官方文档两次检索零命中；机制依据是 `DefaultFileLockManager` 的等待/60 s 超时。取证命令见 §9 |
| U4 | 9.6.1 与 master 的差异 | **待验证** | §2 的源码引用多为 master；9.6.1 本地 jar 只核到常量池字符串（`isWriteDependencyLocks`、`"Write locks is enabled"`、`--write-locks`、`gradle.lockfile`、`empty=`、`# This is a Gradle generated file…`）与行为一致 |
| U5 | `settings-gradle.lockfile` 的生成机制（前缀 `settings-`） | **单一来源（源码推断）** | 官方文档只明写 `buildscript-gradle.lockfile`；本仓实测文件存在、5 行、零坐标 |
| U6 | detached configuration 在 DEFAULT 下是否真的不判红 | **推断** | 无锁状态容忍只对"配置有 lockId 但没条目"明确；detached 配置不进容器，理论上不参与校验 |
| U7 | 完整写入窗口的"干净"定义（是否有别的输入） | **置信但未穷举** | 已排除：源码、`build/` 产物、`.gradle` 项目缓存；保留：JDK 版本、`local.properties`、上游仓库内容 |

---

## 8. 来源与采纳/拒绝理由

| 来源 | 用途 | 采纳/拒绝 |
|---|---|---|
| https://docs.gradle.org/9.6.1/userguide/dependency_locking.html | 锁模式（DEFAULT 三句）、`--write-locks` 语义、`--update-locks`、锁文件格式/位置 | **采纳**（版本与 wrapper 9.6.1 对齐的官方文档） |
| https://docs.gradle.org/current/userguide/dependency_locking.html | "create or update the lock state for each resolved configuration"、"Gradle won't write the lock state to disk if the build fails"、"If a lock state already exists, it will be overwritten" | **部分采纳**：前两句与 §2 源码一致；第三句与源码的**合并语义**矛盾，按源码+9.6.1 口径表述为"按 lockId 覆盖"（记录差异） |
| https://docs.gradle.org/9.6.1/userguide/dependency_caching.html | "Cache locking"：文件锁支持多进程，但要求进程间可通信 | **采纳** |
| https://docs.gradle.org/9.6.1/javadoc/org/gradle/api/Task.html | `notCompatibleWithConfigurationCache`：不兼容任务使配置状态被丢弃（非 build 失败） | **采纳** |
| https://docs.gradle.org/current/userguide/configuration_cache.html | 锁文件属配置缓存输入 | **采纳**（补充；未找到"locking 与 CC 不兼容"的表述，因此**不主张**该说法） |
| https://github.com/gradle/gradle （`DefaultFileLockManager.java`） | 等待/重试、`DEFAULT_LOCK_TIMEOUT=60000`、超时文案 | **采纳**（官方仓库源码；master 版本差异已知 U4） |
| 同上（`DefaultDependencyLockingProvider.java`） | 合并语义、`buildFinished` 一次写、`Collections.sort` | **采纳** |
| 同上（`DefaultDependencyManagementServices.java:525-534`） | 仅 `!buildFailed` 时写盘 | **采纳** |
| 同上（`LockFileReaderWriter.java`） | `gradle.lockfile` 文件名、`empty=`、空状态删除、legacy 清理、`TreeMap`/`sorted()` | **采纳** |
| 同上（`DefaultConfigurationCache.kt:473-481`） | `--write-locks` 强制配置缓存不可复用 / 重新 store | **采纳（版本差异 U4）** |
| https://github.com/gradle/gradle/issues/21396（open，`a:bug` + `in:dependency-locking`） | "同版本、别的配置下有条目，仍判 not part of the dependency lock state" 的官方 tracker 反例 | **采纳**（说明覆盖必须按 lockId 齐全；issue 未关闭 ⇒ 该缺口仍可能有） |
| 本仓 `tools/ci/check_lockfiles.py` + 本轮注入 tracked 的实跑 | 36 错明细、门的真实判据 | **采纳**（一手） |
| 本仓 `build.gradle.kts:36-92`、`benchmark/build.gradle.kts`、`app/build.gradle.kts:54-96`、`core/data/build.gradle.kts:120-187` | 聚合任务、锁范围、变体面、本地 AAR | **采纳**（一手源码） |
| 本仓 `docs/research/2026-11-03-batch-41-record.md:133,234,443` | 36 错同数交叉验证；并发 Gradle 实证 | **采纳** |
| 本仓 `docs/research/2026-11-02-kd72-lock-window.md`、`2026-10-28-lock-window-and-batch30-prep.md`、`2026-10-27-lockfile-execution-plan.md` | W1–W7 与分支 A 执行序列 | **采纳为历史基线**，W1/W2 判定按本轮实测更新（§5.4） |
| https://github.com/palantir/gradle-consistent-versions | 另一套"锁"方案 | **拒绝**：机制不同（自管 `versions.lock` + 插件），不能解决 Gradle 原生锁窗口；引入即改构建面 |
| https://www.w3tutorials.net/blog/configuration-cache-enabled-causes-build-to-fail/ ；Medium「All gradle commands」 | 配置缓存/命令行科普 | **拒绝**：二手、无版本、无源码依据 |
| stackoverflow「Gradle build stuck at Waiting to acquire shared lock」、discuss.gradle.org 并发缓存锁 | 并发锁症状 | **未采纳为事实**：单一来源且非官方；只作 U3 的旁证，不作为判据 |
| 两次 `docs.gradle.org` 检索「write-locks + configuration cache 不兼容」 | 该说法的官方根据 | **零命中 ⇒ 不采纳该说法**（改为按源码"强制 CC miss"表述） |

---

## 9. 可重跑命令（本轮实际执行）

```bash
git rev-parse --short HEAD                                              # faab17f5
git status --porcelain | wc -l                                          # 543
git status --porcelain | cut -c1-2 | sort | uniq -c                     # 105 M / 444 ??
git diff --cached --name-only | wc -l                                   # 0
git ls-files '*gradle.lockfile' | wc -l                                 # 0
git status --porcelain | grep -ci lockfile                              # 14
git status --porcelain -- '*.gradle.kts' 'gradle.properties' \
  'gradle/libs.versions.toml' 'gradle/wrapper/*' .gitignore             # 仅 " M .gitignore"
ls benchmark/gradle.lockfile                                            # No such file
grep -nE "[A-Za-z]:[\\\\/]" app/gradle.lockfile settings-gradle.lockfile core/*/gradle.lockfile feature/*/gradle.lockfile   # 零命中
for f in app core/model …; do grep -oE "=.*" "$f/gradle.lockfile" | … | sort -u; done   # 配置覆盖表（§5.3）
python -I tools/ci/check_lockfiles.py                                   # EXIT=0（跳过态）
python -I -c "import sys;from pathlib import Path;sys.path.insert(0,'.');from tools.ci.check_lockfiles import check;print(check(Path('.').resolve(), tracked=[...13 路径...]))"   # 36 errors
git archive --format=tar HEAD | tar -t | wc -l                          # 3865
git archive --format=tar HEAD | wc -c                                   # 469749760
git ls-files gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties local.properties  # 前 4 个有，local.properties 无
cat local.properties                                                    # sdk.dir=C\:\\Android\\Sdk
java -version                                                           # Temurin 21.0.11
ls .gradle/wrapper/dists                                               # gradle-9.6.1-bin
ls .gradle/caches/modules-2/files-2.1 | wc -l                           # 142
df -h /d                                                                # 364G 可用
```

门禁阶段新增取证命令（本轨未跑，供 G1/U3）：

```bash
# U3：同目录并发是否排队（第二个构建在第一个运行中启动，观察 --info 日志）
./gradlew --no-configuration-cache --no-parallel --console=plain :core:domain:test --info > /tmp/a.log 2>&1 &
./gradlew --no-configuration-cache --no-parallel --console=plain :core:model:test  --info > /tmp/b.log 2>&1
grep -E "Waiting to acquire|Timeout waiting to lock" /tmp/b.log
```

---

## 10. 对批次 42 实施项 67 的直接输入

1. **不要**再按"等树收口"排期；按 §6.1 G1–G9 排。
2. 执行顺序固定：**移出旧锁（G2）→ 副本写（G4）→ 幂等复跑（G5）→ 覆盖冒烟（G6）→ 拷回 → 暂存后跑门（G7）→ 负控（G8）→ 与 build 脚本同批提交（G9）**。
3. 若 G6 出现 `not part of the dependency lock state`：**不得**用 `LockMode.LENIENT` 或删条目来变绿；正确修法是扩大解析面（在 `resolveLockableClasspaths` 里对 `*CompileClasspath`/`*RuntimeClasspath` 之外被解析的 lockId 显式 `resolve()`，或保持 `dependencies` + 逐变体 `--configuration` 解析），然后回到 G4 重写。这条要写进修复环的判据。
4. 若 G4/G6 通过但 G5 有 diff：说明解析结果不稳定（最可能是上游 `-SNAPSHOT`/动态版本或仓库内容变化），按 FAIL 处理并登记，不得提交。
5. 本项**不改** `settings.gradle.kts`、不改 `LockMode`、不引入 verification-metadata、不锁插件 classpath（`build.gradle.kts` 的注释 `:32-33` 已把 verification-metadata 排除在外，保持原裁定）。
