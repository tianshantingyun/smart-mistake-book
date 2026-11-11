# R-04 Keeper 预研：AGP 9.3 兼容性、接入步骤、热区窗口条件与回滚（2026-11-01，批次 38）

> 研究轨（判据预研，计划 `.zcode/plans/plan-2026-11-01-batch-38-triple.md` §1 F.22 任务 22）。**只读/设计**：零 Gradle、零设备命令、零代码改动、零 git 写；**`settings.gradle.kts` 热区未触碰**（本会话唯一写盘 = 本文件与两份同批研究文档）。
> 方法：仓内实读（R-04 全链研究/台账/构建文件/锁文件）+ 联网实证（`gh api` 实取上游/fork 仓库元数据、commits、issues、PR、release notes、插件源码、Maven Central metadata；web_reader 实取两份 README/docs/changelog 全文）。每条外部结论给命令出处；UNVERIFIED 显式。
> 前置输入：批次 37 R2 `docs/research/2026-10-31-r04-k6-verify.md`（§三 预检②结构性不可满足机制、§五.1 Keeper 初评）——本文件是其 §五.1 裁定输入的补全，不推翻其任何结论。

## 0. 一句话结论

**上游 slackhq/Keeper 0.16.1 在 AGP 9 上结构性不可用**（issue #152 实证：AGP 9.0.0 默认 `android.newDsl=true` 下插件 apply 即崩 `Extension of type 'AppExtension' does not exist`；上游 2024-05 起孤儿化，0.16.1 后无代码提交）。**可行对象 = 活跃 fork `usefulness/keeper` v0.19.0**（Apache-2.0、未归档、Maven Central 0.19.0/2026-07-23、repo pushed 2026-10-28）：v0.17.0 "AGP 9 support" → v0.18.0 "AGP 9.2 support" → v0.19.0 "Support SDK 37"（本仓 compileSdk=37 命中）→ 其 CI 已并 AGP 9.3.2/9.4.1/9.5.0 依赖更新 PR（#52/#65/#75 均 MERGED，最新 2026-10-28）→ **AGP 9.3.0 兼容性获上游 CI 级强证据（多源），但"本仓 keep 配置 × Keeper 0.19.0 × AGP 9.3.0"仍须沙盒构建实证（UNVERIFIED，归裁定后的实现批）**。Keeper 的两个 L8 动作正对本仓 K6 机制①的两个物理面（§1）。**接入触面勘误候选**：最小接入**不需要编辑 `settings.gradle.kts`**（pluginManagement 已含 mavenCentral + gradlePluginPortal，本会话实读）；实际触面 = 3 个构建文件（toml/root/app，均现干净）+ `settings-gradle.lockfile` 变更 + KD-72 锁重生成窗口——台账"触 settings.gradle.kts 热区"表述建议按此更正，是否维持热区待遇归主会话。**R-04 维持阻塞不因本预研改变**；本文件只把主会话两路径裁定（选项 A Keeper vs 选项 B 维持预检硬门口径）所需事实补齐。

## 1. Keeper 机制 ↔ 本仓 K6 机制①的对位（为什么它是"唯一发现的可落地先例"）

批次 37 R2 产物级实证（`2026-10-31-r04-k6-verify.md` §三）：j$ 改名由**每变体独立 L8 编译**治理，测试 mapping 476 改名/6 恒等（AGP 生成式规则 7 条逐一对应），主/测两 APK 各带一份独立改名 L8 dex，`j$.util.g` 双侧占用 = 碰撞物理载体；用户 `testProguardFiles` keep 到不了 L8 阶段（`-keep class j$.**` 已进测试 R8 配置 configuration.txt:75 仍零效果）。Keeper（fork 源码本会话 `gh api` 实读 `KeeperPlugin.kt`）正是补这两面的：

1. **L8 规则合并**："merges generated L8 rules from both the androidTest and target app … given to the target app `L8DexDesugarLibTask`"（docs/index.md 原文）→ 主包 L8 dex 成为超集，测试所需的 j$ 类在主包以主包改名存活。
2. **单 L8 dex**："forcing the use of a single dex file in the target app and preventing the inclusion of a backport dex file in the test app"（同上）→ 测试 APK 不再携带独立改名 L8 dex，**碰撞物理载体被移除**——直击预检②（测试 mapping ` -> j$.util.g:` 计数）不可满足的结构根因（R2 §3.6"必须改变产物架构"的架构改变本体）。
3. fork 增量（本会话源码实读）：`AndroidPluginVersion(9, 1).alpha(1)` 分支处理 "Since AGP 9.1, the app L8 task appends desugared-library entries to the final mapping"（与本仓两 mapping 的 `# L8 Desugaring Mapping` 段现象一致，R2 §3.1）并规避其引入的任务环——**fork 面对的正是本仓这条 AGP 9.1+ 管线**。

## 2. 候选对象评分（GitHub 优先纪律：license/维护度/兼容性证据）

| 对象 | 证据（本会话 gh/web_reader 实取） | 判定 |
|---|---|---|
| slackhq/Keeper 0.16.1（上游） | 最后 release 2024-06-28，built against AGP 8.5.0、min AGP 8.4.0（changelog 实读）；0.16.0 起 "no longer used by Slack"（讨论 #144 "seeking a new home"，open）；0.16.1 后 commits 仅合规文件（SECURITY.md/CODEOWNER，commits API 实取 2025-11/2026-06 各 2 条）；**issue #152（open）：AGP 9.0.0 + newDsl 下 apply 即崩** | **否决**（AGP 9 结构性不适用）；仅作机制出处与 L8 文档引用 |
| **usefulness/keeper v0.19.0（fork，推荐）** | Apache-2.0、未归档、pushed 2026-10-28（`gh repo view` 实取）；Maven Central `io.github.usefulness:keeper-gradle-plugin` 0.19.0（metadata 实取 `<release>0.19.0`，2026-07-23）+ Gradle Plugin Portal `io.github.usefulness.keeper`；release 线：v0.17.0（2026-02-13，"Initial fork release with AGP 9 support"）→ v0.18.0（2026-06-12，"AGP 9.2 support"，PR #33 MERGED）→ v0.19.0（2026-07-23，"Support SDK 37"）；AGP 依赖更新 PR 全 MERGED：9.3.1（#47）、9.3.2（#52，2026-08-24）、9.4.0（#54）、9.4.1（#65）、**9.5.0（#75，2026-10-28）**；L8 双机制 + AGP 9.1 mapping 分支在 fork 源码在场（§1-3） | **推荐对象**；AGP 9.3.x 兼容 = 上游 CI 级强证据（跨版本连续合并，非单一来源） |
| 其余路径（R2 §5.2 已否决：公共 DSL 喂 L8 / 主包对称 keep / 逐类反射 hack / applymapping / `-dontobfuscate`） | 批次 37 R2 三轮检索 + 产物证据 | 维持否决，不重开 |

## 3. AGP 9.3.0 兼容性裁定建议（给主会话）

- **上游证据**（多源、本会话实取）：fork 以 AGP 9 为起点维护，兼容面从 9.0 连续推到 9.5（§2 表）；其 `KeeperPlugin` 用新 DSL `ApplicationExtension`（#152 崩溃点已在 fork 修）；`compileSdk=37` 支持（本仓 `app/build.gradle.kts:16` `compileSdk = 37` 命中）。
- **残余风险（两条都真）**：① Keeper 自述 "uses private APIs from AGP and could break between releases"（docs/index.md）——fork 同样继承，9.3.0 精确组合未见逐版声明（9.3.2 CI ≠ 9.3.0 保证）；② fork 0★（单人维护面），需在钉版 + 供应链面登记（§5-W5）。
- **建议的验证链（实现批，裁定后执行；全部判据沿用批次 35 §2.5/§2.8 原文，不放宽）**：
  1. **沙盒冒烟**：本仓外 scratch 项目（AGP 9.3.0 + Gradle 9.6.1 + `io.github.usefulness.keeper` 0.19.0）apply 插件 + 一次 minified 构建成功（先验插件可用性，不动本仓文件）；
  2. 本仓 K2：`-Psmbk.testBuildType=release` 两产物 `BUILD SUCCESSFUL`（判据 K2 原文），**三值预检重跑**——预期 ①（主 mapping 含 SynchronizedMap→j$.util.g，"仍含"）过、**②（测试 mapping ` -> j$.util.g:` = 0）由结构性不可满足转可满足**（测试 APK 不再带独立 L8 dex——机制预测，出数为准）、③（主包 dex 按名含 androidx/tracing/Trace）继续过；
  3. K3 证书同值、K4 keep 门（`python tools/ci/check_r04_keep_rules.py`）、K5/K5b so 双 ABI + 测试类未改名、**K6 两例 `OK (2 tests)` + 三类签名 0**（判据原文）；
  4. 步 5 回归 + 步 6 恢复 debug 态（批次 35 §2.6–2.7 硬要求，批次 36/37 欠账）。
- **不实施 Keeper 的替代路径**（主会话另一支）：维持 R-04 阻塞 + 预检②结构性不可满足登记（批次 37 选项 B 已把机制①升级为设备级确证）——R-04 长期阻塞口径。本研究不代裁。

## 4. 接入步骤（设计稿；触面文件本会话实测全部干净，实施窗须重核）

> 前置：主会话裁定选项 A（§3）。步骤 1–3 为文件编辑；步骤 4–6 为构建/判据面。**步骤中不出现 `settings.gradle.kts` 编辑**。

1. `gradle/libs.versions.toml`：`[versions]` + `keeper = "0.19.0"`；`[plugins]` + `keeper = { id = "io.github.usefulness.keeper", version.ref = "keeper" }`。
2. 根 `build.gradle.kts`：`plugins {}` + `alias(libs.plugins.keeper) apply false`。
3. `app/build.gradle.kts`：`plugins {}` + `alias(libs.plugins.keeper)`（置于 `com.android.application` 之后——Keeper 只与 application 插件组合）；按 docs/configuration.md 形态注册变体 opt-in：`androidComponents { beforeVariants { builder -> if (<R-04 变体判定>) builder.optInToKeeper() } }`——**默认零配置 = no-op**（配置文档原文），opt-in 面建议仅 strictOffline* 变体且与 `-Psmbk.testBuildType=release` 属性门同窗（本仓 strictOffline 即官方推荐的 "staging 型专用测试变体" 同型，`app/build.gradle.kts:7-12` 属性门与 KDoc 在树）。
4. **锁文件窗口**：插件进 classpath → `settings-gradle.lockfile`（现 267 B，仅 `empty=incomingCatalogForLibs0`，本会话实读）新增坐标/或根锁文件生成。按 KD-72 纪律锁重生成必须在**干净待提交树**跑 `lockAllDependencies --write-locks`——现树 726 条（基线 §一），窗口条件见 §5-W3。
5. K2 + 三值预检 + K3/K4/K5（§3 验证链 2–3）。
6. K6 两例 + 步 5/步 6 收尾 + R4 证据落盘（§3 验证链 3–4）。

**副作用登记**：Keeper 会把 androidTest 编译挂进目标变体 minified 构建依赖图（docs/configuration.md "Advanced Configuration" 节；Slack 自述 infer+jar 任务 ~20 s 增量）——本仓 K2 本就要求两产物，可接受；strictOffline* opt-in 保证日常 debug 轨与正式 release 构建零影响（no-op 默认）。

## 5. 热区窗口条件（给主会话的裁定清单；本研究不代裁、未动任何热区）

| # | 条件 | 现状（本会话实跑核对） |
|---|---|---|
| W1 | 主会话裁定 R-04 关闭路径 = 选项 A（Keeper 结构解锁） | 未裁定（批次 37 收口"关闭前置=主会话两合规路径裁定"） |
| W2 | 触面文件干净：`gradle/libs.versions.toml`、根 `build.gradle.kts`、`app/build.gradle.kts`（+可选 `app/r04-test-keep.pro`） | **现均干净**（`git status --porcelain -- <五文件>` 空，实跑）；实施窗重核——"目标文件变脏则整文件纳入并注明或停项登记"纪律适用 |
| W3 | 锁窗口：干净待提交树（KD-72 未闭合四条：干净树重生成/:benchmark 缺锁/负控未执行/13 锁不得原样 add） | 不满足（工作树 726 条、热区全线非净，基线 §一/§二）→ 锁与 Keeper 改动须同窗同批提交 |
| W4 | **`settings.gradle.kts` 勘误裁定**：最小接入不编辑该文件（pluginManagement 已含 mavenCentral+gradlePluginPortal，本会话实读；`FAIL_ON_PROJECT_REPOS` 只管项目仓库，不挡 settings 级插件仓库）。台账"Keeper 解锁触 settings.gradle.kts 热区"（`docs/REFACTOR-MASTER-LINE.md:4`、批次 37 R2 §五.1）建议更正为"触 `settings-gradle.lockfile` + 三个构建文件 + KD-72 锁窗口"。**是否因此把 R-04 解锁出热区、或维持构建配置面整体热区待遇，归主会话**——本预研只更正事实表述，不单方面解锁 | 该文件他线在飞 diff（`+:core:markdown`，基线 §二）的事实独立成立 |
| W5 | 依赖治理：新 classpath 插件 = 新供应链面。钉版（catalog version）必须；可选按批次 22 T3 精神补 sha256 登记——`tools/ci/check_dependency_pins.py` 现只钉 quickjs 三构件 + stdlib 事实（脚本头注释实读），**不会自动覆盖插件坐标**，是否扩门归实现批 | — |
| W6 | 口径切换显式化：批次 37 R2 §4.3 选项 B 登记的"预检②上设备硬门 vs 计划 §1.3 全链重跑"张力，在选项 A 下由"②转为可满足"自然消解，但消解过程须在实施批记录里显式登记，不得静默 | — |

## 6. 回滚

- **单元**：revert 实施批 commit（或手工逆序：toml/根/app 三文件还原 + `lockAllDependencies --write-locks` 重生成锁）；无持久化状态、无 schema、无设备侧状态——回滚后设备侧只需步 6 恢复 debug 态（若 K6 已上过设备）。
- **回滚净化核验（可机检）**：① `git diff HEAD --stat -- gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts` = 空；② keep 门 `python tools/ci/check_r04_keep_rules.py` EXIT=0（`app/r04-test-keep.pro:74` 的 `-keep class j$.** { *; }` **建议保留不随回滚删除**——它是 R2 机制②确认有效的半边、对 `.pro` 面无害；若裁定删除则 K4 门重跑并在记录登记）；③ 可选观测：回滚后重建的测试 mapping 预检②应回归 1（独立 L8 dex 回来）——作为"回滚真实生效"的产物级确认。
- **触发条件**（失败先归因本批）：K2 构建失败/产物异常，且两轮修复不能归因到本批夹具或环境（归因框架沿批次 34 §5）→ 回滚 + 按阻塞登记；R-04 回到批次 37 末状态（预检②结构性不可满足机制证据仍有效，不因回滚失效）。
- 部分回滚：仅 opt-in 面收缩（beforeVariants 判定收窄到不再命中任何变体）= 等价 no-op，可作为"禁用而不摘除"的中间态（配置文档语义支持：零 opt-in = no-op）。

## 7. 未验证项

| # | 项 | 状态 |
|---|---|---|
| U1 | usefulness/keeper 0.19.0 × **本仓** AGP 9.3.0/Gradle 9.6.1 × strictOffline 变体名的端到端构建 | UNVERIFIED（沙盒 + K2 归裁定后实施批） |
| U2 | fork 对 `testProguardFiles` 用户规则的生命周期兼容（上游 0.14 曾有 `disableTestProguardFiles`、0.16 移除；本仓 `app/r04-test-keep.pro` 依赖该入口） | UNVERIFIED（fork 源码未逐行核对该面；K2 configuration.txt 核对归实施批） |
| U3 | opt-in 后任务名匹配（`interpolateL8TaskName("strictOfflineRelease")` → `l8DexDesugarLibStrictOfflineRelease`） | 机制在场（源码实读），实际任务名待 K2 日志核 |
| U4 | 单 L8 dex 后测试 dex 对 j$ 名字与主包 L8 dex 的运行期对齐（fork AGP 9.1 mapping 分支的处理深度） | 机制理解未逐行复核；最终以 K6 设备判据为准（判据本身不变） |
| U5 | 上游 issuetracker 126429384 状态（连接超时，沿批次 37 R2 未取到） | UNVERIFIED，仅 Keeper 转引 |
| U6 | fork 0.19.0 之后是否有 AGP 9.6 适配需求（本仓 AGP 9.3.0 不受影响，钉版时复核 release 线即可） | UNVERIFIED |

## 8. 来源（本会话实取，全部可重跑）

- 仓内：`docs/research/2026-10-31-r04-k6-verify.md`（§1.1/§三/§四.3/§五，全读）；`docs/REFACTOR-MASTER-LINE.md:4-5`（批次 37/36 收口段）；`settings.gradle.kts`（全读，未改）；`gradle/libs.versions.toml`（agp=9.3.0、[plugins] 区）；根/`app`/`core/data` build.gradle.kts（插件与依赖区、R-04 属性门）；`settings-gradle.lockfile`（267 B 实读）；`tools/ci/check_dependency_pins.py`（头 60 行）；锁文件跟踪态（`git ls-files` + `git status --porcelain | grep lockfile` = 13 ??）。
- 外部（gh CLI / web_reader / curl，均可重跑）：
  - `gh api repos/slackhq/Keeper/commits?per_page=8`；`gh api repos/slackhq/Keeper/discussions/144`；`gh issue view 152 --repo slackhq/Keeper`（AGP 9 崩溃原文 + fork 指路）
  - `gh repo view usefulness/keeper --json licenseInfo,pushedAt,latestRelease,isArchived,stargazerCount`；`gh api repos/usefulness/keeper/releases`（v0.17.0/0.18.0/0.19.0 notes）；`gh pr view 33|52|75 --repo usefulness/keeper`（AGP 9.2/9.3.2/9.5.0 均 MERGED）；`gh api repos/usefulness/keeper/contents/keeper-gradle-plugin/src/main/java/com/slack/keeper/KeeperPlugin.kt?ref=master`（L8 合并 + `AndroidPluginVersion(9,1).alpha(1)` 分支实读）；docs/index.md 与 configuration.md（web_reader 实取全文）
  - `curl -s https://repo1.maven.org/maven2/io/github/usefulness/keeper-gradle-plugin/maven-metadata.xml`（`<release>0.19.0</release>`）
  - slackhq.github.io/keeper/changelog/（0.16.0/0.16.1 built-against 与 HELP WANTED 原文）
