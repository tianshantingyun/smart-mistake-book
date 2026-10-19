# 批次 15 最小基线（PRD-01 / T0）

> 登记时点：2026-10-19（工作树当前状态；计划批次名为 2026-10-20）。
> 只读登记：本文件是 T0 的唯一产出。未运行 Gradle、未安装/启动 App、未改动任何代码。
> 所有命令均在 `D:\smart mistake book` 执行。

## 1. 版本控制基线

| 项 | 值 | 来源 |
| --- | --- | --- |
| HEAD | `dfe231bf5dee43aa11e4d7c1598a8cdd2a8e6f76`（短号 `dfe231bf`） | `git rev-parse HEAD` |
| origin/main | `dfe231bf5dee43aa11e4d7c1598a8cdd2a8e6f76` | `git rev-parse origin/main` |
| 与 origin/main 的领先/落后 | `0	0`（既不领先也不落后） | `git rev-list --left-right --count origin/main...HEAD` |
| 分支 | `main...origin/main`（无 ahead/behind 标记） | `git status -sb` |
| HEAD 提交 | `2026-10-19 20:58:22 +0800 docs(research): 批次14 记录补推送结果（e76ffd28..15d80be0）` | `git log -1 --format='%H %ci %s'` |

结论：基线 HEAD 与 origin/main 同一提交，没有未推送的提交。

## 2. 工作树

`git status --porcelain` 共 **496** 行 = **94 modified（` M`）+ 402 untracked（`??`）**（`wc -l` 实测）。

按目录归属（`git status --porcelain` 逐行统计）：

| 目录 | modified | untracked | 说明 |
| --- | ---: | ---: | --- |
| `app/` | 4 | 4 | 含未跟踪的 `CapabilityFormViewModel.kt`、`DatabaseRestartRequiredScreen.kt`（见 §7） |
| `core/` | 22 | 0 | 集中在 `core/data` 10、`core/database` 7、`core/export` 4、`core/domain` 1 |
| `feature/` | 10 | 3 | 集中在 `feature/tutor` 4、`feature/review` 3、`feature/library` 3 |
| `docs/` | 34 | 16 | `docs/research`、`docs/current`、`docs/history`、`docs/superpowers` 等 |
| `tools/` | 22 | 13 | `tools/kb_build`、`tools/kb_coverage` 等 |
| `knowledge-production/` | 1 | 11 | 知识生产临时产物 |
| 其他（仓库根） | 1 | 355 | 根级 `dynamic_gui_architecture_plan.md` 与大量审计裁图/临时文件 |

**归属声明**：以上改动全部是**他线在飞**（计划 §0 第 8 行：UV-01~11、KD-30、KD-35、KD-42、AGF-03、PRD-07 重启页等）。T0 不触碰、不代提交任何一条。计划 §0 第 13-14 行列出的他线占用文件（含 `OpenAiModelProtocol.kt`、`RoomModelTaskRepository.kt`、`TutorSessionPanel.kt`、`KnowledgeReviewSessionViewModel.kt`、`BatchImport*`、`MistakePdfPagePlanner.kt`、`SecondaryScreens.kt`、`LearningMasteryScreen.kt`、`ReviewReminderCoordinator.kt`、`docs/current/*`、`tools/kb_*`、`knowledge-production/*`）在本基线中均只读。

## 3. Schema 与依赖

### 3.1 Room schema

- `STUDY_DATABASE_VERSION = 67` — `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:130`；同文件 `:227` 把它传给数据库构建。
- 已落盘 schema：`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/67.json`（目录实测存在，`ls` 可见 `63..67.json`）。
- 批次 15 的唯一迁移是 **68**，归 T2 独占；请求 schema 保持 17（计划 §0 第 10 行、T2 文件清单第 58-60 行）。

### 3.2 模型请求 schema

- `CURRENT_SCHEMA_VERSION = 17` — `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:535-536`（`IMAGE_CLASSIFY_SOURCE_SHA_SCHEMA_VERSION = 17`，`CURRENT_SCHEMA_VERSION` 为其别名）。
- `MIN_SUPPORTED_SCHEMA_VERSION = 1` — 同文件 `:484`。

### 3.3 关键依赖版本（`gradle/libs.versions.toml`）

| 组件 | 版本 | 行 |
| --- | --- | ---: |
| Android Gradle Plugin | 9.3.0 | 2 |
| Kotlin | 2.3.21 | 3 |
| Compose BOM | 2026.06.00 | 4 |
| kotlinx-coroutines | 1.11.0 | 10 |
| kotlinx-serialization | 1.9.0 | 11 |
| Room3 | 3.0.0 | 12 |
| androidx.sqlite | 2.7.0 | 14 |
| sqlite-jdbc（测试） | 3.53.4.0 | 17 |
| KSP | 2.3.10 | 18 |
| JUnit | 4.13.2 | 24 |
| Espresso | 3.7.0 | 27 |
| LiteRT | 2.1.6 | 32 |
| OkHttp | 5.4.0 | 43 |
| WorkManager | 2.10.5 | 44 |

App 模块 SDK：`compileSdk = 37`、`minSdk = 23`、`targetSdk = 36`（`app/build.gradle.kts:9,21,22`）。

## 4. 入口清单（HEAD）

Gradle 模块共 13 个：`:app`、`:core:model`、`:core:domain`、`:core:database`、`:core:data`、`:core:export`、`:core:ui`、`:feature:capture`、`:feature:review`、`:feature:tutor`、`:feature:library`、`:feature:profile`、`:benchmark`（`settings.gradle.kts:19-34`）。

| 入口 | 位置（HEAD） |
| --- | --- |
| Activity 主入口 / `onCreate` | `app/src/main/kotlin/com/tingyun/smartmistakebook/MainActivity.kt:13,25` |
| 应用装配 / `onCreate` | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt:99,246` |
| 根导航 `NavHost` | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:312` |
| 根导航路由注册 | 同文件 `:320-1022`，`grep` 到 23 处 `composable(...)`（Review/Tutor/Library/Profile/ReviewSession/KnowledgeReviewSession/SharedCapture/BatchImport/ExportResults/MistakeDetail/Capability/LearningMastery/Storage 等） |
| 系统分享接收 | `MainActivity.kt:76-81`（`captureShareRequestFrom` → `sharedCaptureRequests`；消费点 `:83-89`） |
| 复习入口 | `feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/ReviewRoute.kt:49` |
| 错题本入口 | `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryRoute.kt:78` |
| 讲题入口 | `feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorRoute.kt:69` |

## 5. 四栏登记（本批基线时点）

| 栏 | 已具备（证据） | 缺口 / 未验证 |
| --- | --- | --- |
| **代码接线** | HEAD 与 origin/main 同一提交；13 个 Gradle 模块已 include；根导航 23 条路由已注册；`MainActivity` 已消费系统分享意图；批次 14 提交 `759148ba` 的提交信息包含 A8 手动建题生产者、4E.1 生图底座、6B-2 卷面编辑、KD-53/54 备份栈、A7 分享入口（`git log --oneline -5`；HEAD `dfe231bf` 是其后的 docs 提交）。 | 批次 15 的 T1~T8 尚未落地（计划 §1 的 A/B 批与 WG-05）；I1 集成未执行；HEAD 自身存在未解析引用，无法独立编译（§7）；他线在飞改动未提交，不能当作已接线。 |
| **模块检查** | 门禁命令集已在计划 §3 固定：9 个模块单测（`core:model/domain/data/database/export`、`feature:review/library/capture/tutor`）与 6 个 androidTest 编译目标 + `assembleLocalFirstDebug`；Room `67.json` 已落盘。 | **T0 未运行任何 Gradle**（铁律第 2 条），本基线没有任何当前编译/测试绿灯结果；已知红：`GoldenRetrievalInstrumentedTest`/`GoldenRetrievalJvmTest`（KB 在飞）、SECTION facet 边际门（环境）（计划 §3 第 221 行）；HEAD 编译会因 §7 的未跟踪符号失败。 |
| **设备全链** | 设备轨协议已固定：`emulator-5554`（test_device）/`emulator-5556`（audit_user_visible_20261016），`adb = C:/Android/Sdk/platform-tools/adb.exe`，禁止 `connectedLocalFirstDebugAndroidTest`（计划 D1 第 187-188 行）；历史证据：批次 14 A8 手动题设备全链 PASS（`docs/known-defects.md:2147`）、KD-53 校验侧 365,133,824 B 单条目过校验（`:2170-2171`）。 | 基线时点 `adb devices` 返回空列表（命令实测），无设备在线；批次 15 的设备项（KD-56 大库恢复、KD-54 并发写、PRD-04 双格式、KD-57 重试、PRD-03 编辑一致性、PRD-02 纠错/重放、回归）均未执行；KD-56 仍为 open。历史 PASS 不代表当前包。 |
| **真实质量** | 质量目标/PRD-09 仅存在于设计与后续计划，不在本批范围（计划 §1 第 20 行明确不做 PRD-09）；图义提示词仍为 v1 设计稿、未接入（`docs/current/figure-generation-prompt-policy.md:117`）。 | 模型凭据已随数据删除，本批不播种密钥，模型轨整块 UNVERIFIED（计划 D1 第 197 行、§6 第 244 行）；没有真实 Provider 的成功率/时延/成本/数学正确性数据，不能把 schema 或渲染通过当作真实质量。 |

## 6. 当前已知失败

### 6.1 `docs/known-defects.md` 的 open 条目（实测 grep 头部）

| ID | 级别 | 行 | 要点 / 批次 15 关系 |
| --- | --- | ---: | --- |
| KD-56 | high / P0 | 2214 | 大库恢复在 app 内 main 线程 `SQLException: Connection pool is closed` + 启动回滚；两次设备复现。本批 T1 必须关闭。 |
| KD-54 | medium | 2177 | 备份快照 `VACUUM INTO` 占写连接 601s；批次 14 让路后仍有 9 条 `unable to grant`、最坏 90.014s。本批 T1 处理。 |
| KD-43 | medium | 1933 | 错键判错后没有纠正/撤销/重放落点；同身份撞被拒行还会静默丢写。本批 T2（PRD-02）。 |
| KD-57 | medium | 2235 | 分享接收「再试一次」必然失败：授权在 `finally` 无条件撤销，`Expired` 分支不可达。本批 T7。 |
| KD-58 | medium | 2250 | 旧 `attachedImages` 兼容链静默失败：无预留重载默认抛、异常被吞成 null。本批 T6（WG-05）。 |
| KD-59 | low | 2265 | 「无预留→0 HTTP」接口层偏弱：旧重载可被覆盖、`FigureImageDispatchBinding` 公开可自造。本批 T6。 |
| KD-60 | low | 2279 | 图像预留与讲题工具环共享 `dispatch_count`，先烧工具轮会让配图被判 `BUDGET_EXHAUSTED`。本批 T6。 |
| KD-53 | medium | 2154 | 大库自建备份不可校验/不可恢复（单条目 100MB 上限 < 库体积）。批次 14 校验侧已过，恢复侧未达 = KD-56；本批 T1 验收含「KD-53 恢复侧」。 |
| KD-55 | low | 2197 | docx 嵌入图片直读 `file://` 不复核 sha/长度；批次 14 已按方向落地并有关闭记录（`:2207-2210`），头部仍标 open。不在本批主目标。 |
| KD-47 | low | 2027 | 设置恢复多键顺序写非原子，面向用户文案仍称「保持原样」；该串位于他线在飞 `SecondaryScreens.kt`，本批不动。 |
| KD-24 | open | 876 | 去截断实验（v2）净伤害词面路由已回滚；非本批目标。 |
| KD-27 | open | 1062 | CI `check` job 长期红 ⇒ status.md commit-back 冻结；非本批目标。 |
| KD-28 | open | 1099 | `kb_build` 9 科白名单抄两份（2026-09-28 判为接受边界）；非本批目标。 |
| KD-29 | open | 1111 | AI 生成合成内容标识未落；非本批目标。 |
| KD-32 | open | 1173 | 192MB 堆上限下知识包安装与批量导入 OOM；两处峰值已修，剩余未清；非本批目标。 |

**计划 §0 与文件条目的差异（照实登记）**：计划 §0 第 12 行只列 KD-56/54/43/57/58/59/60（+ KD-47 UI 文案），未列 KD-53/55/24/27/28/29/32；而 `docs/known-defects.md` 实测仍有上述 open 头部。以文件为准，计划清单应视为「本批要处理的 open 集」，不是 open 全集。

### 6.2 已知红测试与基线阻塞

- `GoldenRetrievalInstrumentedTest` / `GoldenRetrievalJvmTest`（KB 在飞）、SECTION facet 边际门（环境）——计划 §3 第 221 行点名照录；T0 未复跑。
- **HEAD 树自身不可独立编译**——见 §7；这是静态事实，不是 Gradle 运行结果。

### 6.3 本批关闭要求（计划口径）

- KD-56 必须关闭；KD-54、KD-43、KD-57、KD-58/59/60 各自按已知缺陷条目的关闭条件验收；KD-47 UI 文案不在本批（他线文件）。
- 任何不达项保持 open 并如实登记，不得用「模块绿」替代「设备全链」或「真实质量」（`docs/REFACTOR-MASTER-LINE.md:168`）。

## 7. HEAD 树不可独立编译（事实记录）

**结论**：HEAD 提交 `dfe231bf` 的 app 模块引用两个**未跟踪**文件中的符号；HEAD 树缺少这两个声明，因此 HEAD 树自身不可独立编译。本批不代提交他线文件，也不修 HEAD 的该问题（计划 §0 第 9 行、§5 第 235 行）。

可复核证据：

1. HEAD 树中没有这两个文件：
   - `git ls-tree -r HEAD --name-only | grep -E "CapabilityFormViewModel|DatabaseRestartRequiredScreen"` → 无输出（exit 1）。
   - `git ls-files --error-unmatch app/src/main/kotlin/com/tingyun/smartmistakebook/CapabilityFormViewModel.kt` → `error: pathspec ... did not match any file(s) known to git`；对 `DatabaseRestartRequiredScreen.kt` 同样报错。
2. 已提交代码确实引用这两个符号：
   - `git grep -n "CapabilityFormViewModel" HEAD -- 'app/*'` → `HEAD:app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt:118`（`val form: CapabilityFormViewModel = androidx.lifecycle.viewmodel.compose.viewModel()`）。
   - `git grep -n "DatabaseRestartRequiredScreen" HEAD -- 'app/*'` → `HEAD:app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:170`（`DatabaseRestartRequiredScreen(message = message, onClose = ...)`）。
   - 即：**SecondaryScreens → CapabilityFormViewModel；Root → DatabaseRestartRequiredScreen**（与计划 §0 第 9 行的对应关系一致，此处按实际文件写清）。
3. HEAD 树中没有对应声明：
   - `git grep -n "class CapabilityFormViewModel\|class DatabaseRestartRequiredScreen" HEAD` → 无输出。
4. 工作树中它们是未跟踪文件，声明存在：
   - `app/src/main/kotlin/com/tingyun/smartmistakebook/CapabilityFormViewModel.kt:14`：`internal class CapabilityFormViewModel : ViewModel()`。
   - `app/src/main/kotlin/com/tingyun/smartmistakebook/DatabaseRestartRequiredScreen.kt:16-17`：`@Composable internal fun DatabaseRestartRequiredScreen(message: String, onClose: () -> Unit)`。

**验证边界**：T0 被铁律第 2 条禁止运行 Gradle，故以上是「HEAD 树符号缺失」的静态证明，**未用 `compileLocalFirstDebugKotlin` 复验**；本批 I1 集成时不得引用 `CapabilityFormViewModel`/`DatabaseRestartRequiredScreen` 的新符号（计划第 184 行），也不得代提交这两个文件。

## 8. 本批纪律

- T0 只写本文件，不改代码、不运行 Gradle、不 `git add/commit/stash/checkout/reset`。
- 他线在飞文件一律不碰；共享工作树按计划 §5 的整文件纳入规则处理。
- 密钥不落盘、不回显、不进日志；模型轨本批整块 UNVERIFIED。
