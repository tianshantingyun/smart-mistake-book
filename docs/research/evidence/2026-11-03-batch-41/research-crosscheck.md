# 批次 41 研究交叉复核：R1–R9 对已有批次 40 研究件（只读）

| 字段 | 值 |
|---|---|
| evidence-id | `B41-RESEARCH-CROSSCHECK-01` |
| executed-at | 2026-11-02（本机时间；文档命名按计划批次 41） |
| head | `62522930bc8bae51be1523c954f39449e054b6af`（与 `origin/main` 同值，见批次 41 基线 §1.1） |
| 执行者 | 研究交叉复核员（子代理） |
| 授权范围 | 只读：读计划/记录/研究件/源码 + `git`/`grep`/`python` 只读命令 |
| 未执行 | 任何 Gradle 任务、任何设备/模拟器命令、任何 git 写操作、任何源码改动 |
| verdict | R1/R2/R3/R4/R6/R7/R9 **补做**（有可复用底座，不能从零起）；R5/R8 **照做**（现有研究件已够用）；另登记 **8 条冲突**，其中 2 条（C1/C2）是计划 §0 与 HEAD 直接矛盾 |

复核口径：本文件只记录本轮**实读/实跑**到的内容。计划 §0 的门禁数字（单测门 exit=1、强门 exit=0）来自批次 40 主会话，本轮按纪律不跑 Gradle，按"转录"处理，不计为通过（同 `2026-11-03-batch-41-baseline.md:14`）。

---

## 0. 本轮实读范围

| 类别 | 具体件 |
|---|---|
| 计划 | `.zcode/plans/plan-2026-11-03-batch-41.md`（125 行，全文） |
| 批次 40 记录 | `docs/research/2026-11-02-batch-40-record.md` §2 研究 8 行（:106-117）、§7（:269-279）、§8 22 条（:281-308）、§9（:310-341）、§12（:378-408） |
| 批次 40 研究件（8 份） | `pdf-incremental-aigc.md`、`error-taxonomy.md`、`fsrs-capacity.md`、`model-task-layering.md`、`aud08-annotation.md`、`keeper-l8.md`、`core-visual-module.md`、`kd71-race-review.md`；辅读 `ws3-review-budget-adjudication.md`、`2026-10-17-6c2-content-selection-design.md`、`2026-11-01-isolation-attribution.md` |
| 同批兄弟件 | `docs/research/2026-11-03-batch-41-baseline.md`（基线核对员；其 D1–D6 我逐条重核，见 §2 C1/C2） |
| 源码（实读） | `ReviewCapacityScheduler.kt`、`ReviewCapacityPreviewReader.kt`、`ErrorCauseGate.kt`、`SmbkSelectionPlan.kt`、`SmbkArchiveCodec.kt`、`StudyDatabase.kt`（迁移注册段）、`core/database/build.gradle.kts`（Room 插件配置）、`gradle/libs.versions.toml`；用例 `ReviewCapacitySchedulerTest.kt`、`ErrorCauseGateTest.kt` |

`R1–R9` 与批次 40 研究件的对应关系（8 份研究件并不一一对应 9 题）：

| 批次 41 题 | 有直接对应件 | 只部分相关 | 无对应件 |
|---|---|---|---|
| R1 多科合并总量 | `fsrs-capacity.md` | `ws3-review-budget-adjudication.md` | — |
| R2 备份格式兼容 | — | `2026-10-17-6c2-content-selection-design.md`（设计文档，非研究件） | ✔ |
| R3 Room 重基/孤儿 JSON | — | 批次 40 记录 §7（裁定结果，无工程实践） | ✔ |
| R4 16KB 页测试 | — | 无（各批记录仅写 UNVERIFIED） | ✔ |
| R5 低负载判定 | `2026-11-01-isolation-attribution.md` | 批次 40 记录 §8 #7 | — |
| R6 PDF 读者容忍度 | `pdf-incremental-aigc.md` | — | — |
| R7 错因→话术 | — | `error-taxonomy.md`（只到分类判据） | ✔ |
| R8 Keeper/L8 | `keeper-l8.md` | `2026-11-01-r04-keeper-prerequest.md` | — |
| R9 Instrumentation PDF 校验 | — | `pdf-incremental-aigc.md` §7.4 | ✔ |

---

## 1. 逐题判定（9 行）

| # | 研究题（计划 :16-24） | 判定 | 一句话依据 |
|---|---|---|---|
| R1 | FSRS/Anki 多科每日上限分配（合并总量口径） | **补做** | 时间预算合并与跨科考试日已在 `fsrs-capacity.md:181,195-199` 覆盖；**缺** count 级"一份容量分给各科"的舍入/优先级口径与 Anki 跨 deck 语义，且 HEAD 已实现一版（§2 C1） |
| R2 | 备份包格式前向/后向兼容（closure 版本协商、缺表按空） | **补做** | 全仓无该题研究件；只有设计文档一句"旧包永远可读"（`2026-10-17-6c2-content-selection-design.md:427`）与记录 §8 #16 的"待产品裁定" |
| R3 | Room 版本重基/孤儿 schema JSON 的工程实践 | **补做** | 记录 §7 只给裁定结果（rebase 到 71、72 作 orphan），无"如何重基、孤儿 JSON 何时可删、KSP 何时必须重跑"的实践结论；仓内是 Room3 3.0.0 + `schemaDirectory`（`core/database/build.gradle.kts:65`、`gradle/libs.versions.toml:12`） |
| R4 | Android 16KB 页大小测试方法（ps16k/arm64 可用性） | **补做** | 批次 38/39/40 记录一律"16KB UNVERIFIED"（`2026-11-02-batch-40-record.md:110,219`），无方法研究；基线只列出 `ps16k_arm64`/`ps16k_x86_64` 两个 AVD 名、未启动（`2026-11-03-batch-41-baseline.md:167-181`） |
| R5 | 模拟器低负载判定（loadavg/进程快照/采样窗） | **照做** | `2026-11-01-isolation-attribution.md:201-246` 已给 P1–P4 / G / A 三段判据与决策表，可直接复用；**但** loadavg 只能记录、不能作判据（同文 `:157`，见 §2 C4） |
| R6 | PDF 增量更新的读者容忍度与校验矩阵 | **补做** | `pdf-incremental-aigc.md` 有 16 行 JVM 矩阵（`:767-791`）与桌面两读者（`:873-889`），但 ①4/4 无落盘脚本/日志（记录 §8 #13）②无"读者 × 变体"容忍度矩阵 ③设备行未跑（`:917-928`） |
| R7 | 错因分类到反馈话术的教学映射 | **补做** | `error-taxonomy.md` 只到分类判据与落账门；"教学响应不同"仅作 E05 启用条件被提及（`:274,96`），无"类别→反馈措辞"映射 |
| R8 | Keeper/L8 在 AGP 9.3 的实跑可行性 | **照做** | `keeper-l8.md:7-14` 已定：slackhq 0.16.1 结构性不可用、采纳 usefulness/keeper 0.19.0（tag 自身 AGP 9.3.0 + Gradle 9.6.1）、`automaticR8RepoManagement=false` 硬约束、mapping 门三值（`:571-578` 勘误） |
| R9 | Instrumentation 侧 PDF 校验（PdfRenderer/seccomp/文件断言） | **补做** | `pdf-incremental-aigc.md:788-798` 只列 7 条设备步骤（PdfRenderer 打开、页数、读回 `/AIGC`）；**seccomp/沙箱下的文件断言与"设备内独立读者"完全未答**，设备用例也不在树（记录 §8 #5） |

### 1.1 各题必须新做的部分（不是重写全部）

- **R1（补做）**：在 `fsrs-capacity.md` 之上补三件——① count 级合并口径：各科应取数之和 > 单日容量时如何分（比例/最大余数/优先级），余数与并列如何裁；② Anki 语义边界：Anki 的每日上限是 **per-deck**，没有跨 deck 全局上限，因此"多科合并总量"是本仓的应用层裁定，不能引 Anki 当先例（已有事实：`fsrs-capacity.md:30`"新卡/复习每日上限是**张数**上限"）；③ 与 HEAD 实现对齐：`ReviewCapacityScheduler.kt:68-75` 已用 `allocateByDayWeights(remaining=dailyCapacity, dayWeights=desiredQuotas)` 按应取数比例分同一份容量，`ReviewCapacitySchedulerTest.kt:83-107` 已断"两科各 6 应取数 → 合并 6"。**不要**再写一份新算法。
- **R2（补做）**：核心是 closure 版本协商的**语义**而非格式：`SmbkSelectionPlan.CLOSURE_VERSION=5`（`:70`）是规则表版本，读侧 `SmbkArchiveCodec.kt:1162-1166` 对 `!=5` 直接 `SelectionRead.Invalid`，写侧 `:444-446` 同样硬钉。R2 必须回答：① closure-4 包按 v5 规则**重校验后接受**（推荐与否）还是拒绝并给升级文案；② "缺新表按空表恢复"与 `rowCounts` 对账如何共存（计划 item 5 要求"不静默丢行"，item 6 要求"行数对账"）；③ 旧 v1/v2 无 selection 包的既有通路不得回归（`MIN_READABLE_FORMAT_VERSION=1`，`:102`；`INTRODUCED_FORMAT_VERSION=3`，`:147`）。
- **R3（补做）**：要答"重基后的可复现路径"：`71.json`(90)/`72.json`(91, orphan)/`73.json`(92) 三份的定位（基线 §2.1-2.3 已实测）、orphan `72.json` 仍登记 `ERROR_CAUSE_MIGRATION_72_73`（`StudyDatabase.kt:422-424`）是过渡还是长期、KSP/room3 插件何时必须重跑（`schemaDirectory` 配置在 `core/database/build.gradle.kts:65`）、以及"同号冲突"规则（记录 §7 #4：先提交者占号、后到者重编号）如何写进工程实践。
- **R4（补做）**：方法层，不是跑一次：16KB 判定口径（`getconf PAGE_SIZE` / `ro.boot.*` / APK `zipalign -c -P 16` / ELF 段对齐）、`ps16k_arm64` 与 `ps16k_x86_64` 的可用性前提（镜像是否含 16KB kernel）、以及与"设备绿绑 APK sha"的关系（页大小属环境变量，必须与 serial/ABI 一并登记）。
- **R6（补做）**：把 `pdf-incremental-aigc.md:767-791` 的 16 行 JVM 矩阵升级为**容忍度矩阵**（读者 × 变体：表型/流型/`/Prev` 链/`/XRefStm`/线性化/加密），并把桌面两读者的 4/4 落成可复跑脚本 + 原始输出（记录 §8 #13 判定其"无落盘日志"）。设备侧 PDFium 行转 R9，不在 R6 重复。
- **R7（补做）**：类别 → 反馈结构的映射（四类 × "先说什么/给什么证据/下一步动作"），须与产品合同一致（`error-taxonomy.md:102` 引"错因不得成为可见分类/标签"），即**话术不得暴露内部四类码**；并明确"无证据不落账"在话术层的对应（`error-taxonomy.md:467` 降级期行为）。
- **R9（补做）**：必须回答 instrumentation 内的**可行性边界**：`PdfRenderer` 能开页/数页但**不能抽文本**；androidTest 进程受应用沙箱与 seccomp 约束，**不能 fork/exec 外部工具**（pypdf/PyMuPDF 只能桌面跑）；因此"设备内独立读者"只剩"纯 Java 的 PDF 解析库进 test 依赖"一条路（`pdf-incremental-aigc.md:796` 只提了候选，未核 license/版本/维护）。这三条不答，item 11–16 的设备用例会写成不可执行形态（KD-75 已因此 BLOCKED 一次）。

### 1.2 照做项的使用条件

- **R5**：直接引用 `2026-11-01-isolation-attribution.md` §3.2 的 P1–P4/G/A 三段，并把 G 的阈值（p50 ≤1.8s、p95 ≤6.0s、max ≤8.0s、`warm-up failed`=0、未配对 spawn ≤1）与其"N=1 绿窗标定"的局限一并带入（同文 `:222-226`）。禁止把 guest loadavg 写成通过判据——该文 `:153-157` 已给出反例（窗 3 起跑 loadavg 0.66，warm-up p50 2.20/p95 8.88/max 15.01）。
- **R8**：`keeper-l8.md` 的 D1–D7（`:539-548`）未裁之前，只能做**只读验证**（与计划 item 55"只读验证，不改 settings"一致）；`settings.gradle.kts` 不编辑是研究件自己的建议（`:9-10,571-578`），计划 §4 亦把它列为热区。R8 若要"补"，只允许补"本仓 Gradle/K2 实跑"这半边，且属门禁阶段，不得与研究阶段混淆。

---

## 2. 冲突登记（8 条；C1/C2 为高，均已独立复核）

> 判定口径：以 **HEAD 源码实读** 为准；批次 40 记录 §8 的条目是"复核时点读数"，若被同批后续提交覆盖，记录本身不因之失效，但**不得再作为批次 41 的缺陷前提**。

| # | 冲突 | 证据（本轮实读） | 影响 | 研究侧处置 |
|---|---|---|---|---|
| **C1** | 计划 §0 / WS1 item 1 称 `ReviewCapacityScheduler` 多科总量过载（依据记录 §8 #15），但 **HEAD 已修** | `ReviewCapacityScheduler.kt:46-47`"当日容量只有一份…绝不允许各拿整份"；`:68-75` 超容量时按应取数比例分同一份 `dailyCapacity`；`:98-117` 再平衡只吃 `dailyCapacity - examPicked.size`；用例 `ReviewCapacitySchedulerTest.kt:83-107`（"两科各 6 应取数 → 合并 6"，注释"修复前为 12"）、`:110-128`（"再平衡只能用剩下的 4 个名额"）。三文件相对 HEAD **无改动**（`git status --porcelain` 空），提交 `1d53cb4d` / `2e20d041`；`git show HEAD:…Scheduler.kt \| grep` 命中 `:46` 原文 | item 1 若照计划再改一遍 = 重复实现；R1 若照旧缺陷叙述写 = 结论与树不符 | R1 改口径为"**核验 + 残留语义**"，不重写算法；item 1 降级为"回归锁/边界用例"（是否计件由主会话裁） |
| **C2** | 记录 §8 #14 称 `ErrorCauseGate` 未查 `SELF_REPORT`，但 **HEAD 已修** | `ErrorCauseGate.kt:42-43`（`if (candidate.evidence.channel == ErrorCauseChannel.SELF_REPORT) return false`，注释"门自己兜底，不依赖 extractor"）；用例 `ErrorCauseGateTest.kt:99-111`（"self report can never auto book even when every other threshold passes"）。文件相对 HEAD 无改动，提交 `e89476ea` | 计划 WS1 item 4 的"门缺口"前提不成立 | item 4 降级为"既有硬规则回归锁"；记录 §9.1 的"门缺口保留 open"须回写为"已修"（item 10 正是回写项，落点在 §8 #14/#15/#16） |
| **C3** | 计划 §0 的"开批未闭合"6 条中 2 条（C1/C2）已被 HEAD 覆盖，另 4 条实测成立 | 成立项：KD-75（基线 `:238` grep 空）、KD-76（基线 `:239` 全仓 `smb.kd76` 空）、isolation 单臂 15/15（基线 `:240`）、closure 4→5 硬拒（基线 `:241` + 本轮 `SmbkArchiveCodec.kt:444,1162`） | 计划 §0 作为开批基线有一半失真 | 研究件必须自证基线时点，不得整体引用 §0 |
| **C4** | 计划 R5/item 25 的括号写"loadavg 窗"，而现有研究证明 loadavg **不可作判据** | `2026-11-01-isolation-attribution.md:157`"负载闸门必须用 warm-up 尾部分布，不能用 guest loadavg，也不能只看 sheet 面的 host loadavg"；`:209` P3"只记录不作硬门槛"；`:153-155` 窗 3 反例 | 若 item 25 按"loadavg 达标即低负载"实现，会重演批次 39 的无效窗 | item 25 落码时把 loadavg 降级为记录项，硬闸用 G 段（`同文 :211-218`） |
| **C5** | R2/item 5 的兼容读侧在记录里是"**待产品裁定、本轮不擅自实现**"，计划已直接要求实现 | 记录 `:302`（§8 #16 处置）、`:322`（§9.1-9）；计划 `:33`（item 5"closure 4→5 旧 v3 包**兼容读侧**"） | 属"用户/计划覆盖旧裁定"，不是冲突错误，但必须登记为**口径变更**，否则证据链会出现"未经裁定即改行为" | R2 研究件开头须写明"依据批次 41 计划 item 5，旧'待裁定'状态被覆盖"；设计上仍须"不得静默接受未知闭包、不得静默丢行" |
| **C6** | 计划 §2 标题"实施项（55 项）"与实列 **62** 项不符；§3 合计"84"据此也算错 | 本轮程序化计数：`^\d+\.\s` 命中 **62** 行，编号 1–62 无缺口（命令见 §5-①）；各 WS 表头 10/7/7/7/6/5/5/4/6/5 = 62。计划 `:26`、`:113` 写 55 与 84；按实列应为 9+62+20 = **91** | 与批次 40 §8 #12（"计划 80 项 vs 实列 82"）同类；验收时"完成 55 项"的口径会漏掉 7 项（最可能是 WS1 的 10 项与 WS10 的 5 项被按旧稿计） | 研究侧不改计划；本件登记，请主会话裁定计数口径（建议以实列 62 为准，逐项勾稽） |
| **C7** | 计划 §3 声称"各 WS 内独立判据/证据子件 8（**互斥计数**）" | 逐条比对：WS2"设备证据"（item 16）与流程项"设备轨 1"（`:111`）覆盖同一批设备运行；WS3"汇总报表"（item 19/21）与 WS1"该脚本挂 CI"（item 9）同为脚本交付但对象不同 | 若严格互斥，则 8 个子件中有 1–2 个不成立，合计 20 会退为 18–19 | 仅登记；不影响研究线判定 |
| **C8** | R1 若沿用"FSRS 提供考试配额/多科上限"的前提，与已有结论相反 | `fsrs-capacity.md:9-22` C2："exam date → daily quota 与 missed days rebalance **不是** FSRS/开源实现的算法能力"；证据 py-fsrs/fsrs-rs 无 exam/deadline/quota 标识、fsrs4anki-helper `deadline` 0 命中（`:26-38` 交叉验证表） | R1 题目措辞（"FSRS/Anki …"）容易被读成"照抄开源实现" | R1 必须显式写"FSRS 只提供 R/S/D 与目标保留率；每日上限分配是应用层裁定"，与 C2 对齐 |

### 2.1 另登记一条**读码观察**（非记录冲突，供 R1/item 2 裁定）

`ReviewCapacityScheduler` 计算 `deduplicatedCount`（`ReviewCapacityScheduler.kt:128-130` 用交集计数）但**不从 `rebalance.picked` 里剔除重复项**；`ReviewCapacityPlan.totalPickedCount`（`:156-162`）按并集去重计数。用例 `ReviewCapacitySchedulerTest.kt:60-82` 因此断出 `examPicked=2`、`rebalance.picked=4`、`totalPickedCount=4`、`dailyCapacity=6`——即**有 2 个名额被重复占用后空转**。

- 这是"名额浪费"，不是"总量超限"（`:127`、`:148` 的上界断言仍成立）。
- 计划 item 2 要求"合并去重后**总量口径**"，正好需要裁定：总量按并集还是按通道和？重复项应剔除并由再平衡补位，还是保留并只计数？
- 本轮**只读码与用例断言、未运行该用例**，不宣称其红绿。

---

## 3. 本批研究线的硬约束（H1–H14）

> 每条都指认它消灭的具体失败（全局 §12.2）；无指认的条目已删。

| # | 约束 | 消灭的具体失败 | 落点 |
|---|---|---|---|
| H1 | 每份研究件必须有：§0 结论表（结论/类型/证据三列）、来源清单（URL + 版本 + license + 维护快照）、**UNVERIFIED 段** | 结论与来源脱钩（批次 40 曾出现"93 表/73 注册齐全"虚报，记录 §8 #3） | 8 份批次 40 研究件体例（如 `keeper-l8.md:522-537` U1–U12） |
| H2 | 单一来源一律标"待验证"，不得用于支撑"可行/完成/无冲突"；摘要级来源不得当事实 | 用单一博客/摘要当定论（全局 §11.3） | 每份 §来源清单 |
| H3 | 版本兼容一票否决：Keeper 0.19.0×AGP 9.3.0×Gradle 9.6.1、Room3 3.0.0、PDF 设备库 × minSdk/compileSdk 37、16KB × 镜像 | 引入与工具链不兼容的依赖（批次 40 R8 Keeper 已因版本面被卡） | `libs.versions.toml:12`、`keeper-l8.md:7-14` |
| H4 | license 必须核到仓库根文件：Deckline 无 LICENSE → 只可语义参照；Deadline2 GPL-3.0 → 只借数学不复制代码；PDF 设备库须给 SPDX 与分发义务 | 复制 GPL/无许可代码进仓（`fsrs-capacity.md:49-63` 已示范处理） | R1、R6、R9 |
| H5 | 研究阶段**零 Gradle、零设备、零 git 写、零源码改动**；Gradle 只在门禁阶段单跑且无并发 | 研究件污染构建/树态（批次 40 §12.1 的串行纪律） | 全部 9 份 |
| H6 | 标注四分类：**仓内事实（path:line）/ 外部事实（URL）/ 计算值（命令）/ 假设**，四者不得混写 | "看起来是事实"的推断被当证据 | 全部 9 份 |
| H7 | 与 HEAD 冲突的旧结论必须以 HEAD 实读为准并登记（C1/C2 为模板）；不得照抄记录 §8 的时点值 | 以过期缺陷为前提写研究/改代码 | R1、R2、R3、R7 |
| H8 | 设备类结论必须声明 `serial / API / ABI / PAGE_SIZE / APK sha256 / 源快照`；桌面或 x86_64 模拟器结论不得外推到 arm64/16KB/真机 | 设备绿绑错包（批次 40 §6.1 只绑 10:13 APK，记录 §9.1-4 因此不能关闭） | R4、R6、R9 |
| H9 | 每份研究件必须给**可执行的验收判据**（命令或断言形态），并逐条指认"它消灭的具体失败" | 结论无法验收、新增无失败指认（全局 §12.2） | 全部 9 份 §验收 |
| H10 | 不得重开已裁定项：不改 schema 73（计划 `:118`）；不激活 `ReviewPlannerV2.VERSION`（`fsrs-capacity.md:399` S8，core/domain 热区）；closure 兼容读侧不得静默接受未知闭包、不得静默丢行 | 越权改热区/改版本号（计划 §4 热区表） | R1、R2、R3 |
| H11 | 输出路径严格按计划 §1：`docs/research/2026-11-03-<slug>.md`（9 个 slug 见 `:16-24`）；`evidence/2026-11-03-batch-41/` 只放复核/设备证据件 | 研究件与证据件混放、路径漂移 | 全部 9 份 |
| H12 | 自报计数必须与计划对账：研究线 **9**（R1–R9）；实施项计数以本件 C6 为待裁项（实列 62，计划写 55） | 再次出现"计划 80 vs 实列 82"式错账 | 全部 9 份 + 记录 |
| H13 | R5 判据：`loadavg`（host/guest）只记录；硬闸 = worker warm-up p50/p95/max + `warm-up failed`=0 + 未配对 spawn ≤1；标定样本 N=1 必须声明 | 无效低负载窗被当通过证据（`isolation-attribution.md:153-157`） | R5 |
| H14 | R1 必须显式声明 FSRS 的能力边界（C8），每日上限分配属应用层；不得把 Anki 的 per-deck 上限当作跨科全局上限的先例 | 用被证伪的前提支撑设计（`fsrs-capacity.md:9-22` C2） | R1 |

---

## 4. 未运行 / UNVERIFIED（不得写通过）

1. 任何 Gradle 编译、单测门、强门（本轮零 Gradle）。
2. `ReviewCapacitySchedulerTest` / `ErrorCauseGateTest` 的**实跑**结果——本件只读源码与断言文本，C1/C2/§2.1 均为读码结论。
3. 任何设备/模拟器动作（未 `adb`、未启动 AVD）；`ps16k_*` 的可启动性与页大小未验证。
4. 备份包读写行为、closure-4 包的实际恢复结果（只读判定代码，未执行）。
5. 研究件 R1–R9 的**外部来源**本轮未新增检索（本件只做交叉复核，不做新研究）。
6. 计划 §0 的单测门/强门数字（转录自批次 40 §12.1，未复现）。

---

## 5. 本轮执行的检查（可重跑）

```bash
# ① 计划计数与 R 行（本件 C6）
python - <<'PY'
import re
lines=open('.zcode/plans/plan-2026-11-03-batch-41.md',encoding='utf-8').read().splitlines()
nums=[int(re.match(r'^(\d+)\.',l).group(1)) for l in lines if re.match(r'^\d+\.\s',l)]
print(len(nums), nums[0], nums[-1], [n for n in range(1,nums[-1]+1) if n not in nums])
print(len([l for l in lines if re.match(r'^R\d\s',l)]))
PY
# 输出：62 1 62 [] / 9

# ② C1：修在 HEAD 而非仅工作树
git status --porcelain -- core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityScheduler.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacitySchedulerTest.kt
git log -1 --format="%h %s" -- core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityScheduler.kt
git show HEAD:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityScheduler.kt | grep -n "当日容量只有一份"

# ③ C2：SELF_REPORT 门与用例
grep -n "SELF_REPORT) return false" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ErrorCauseGate.kt
grep -n "self report can never auto book" core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ErrorCauseGateTest.kt

# ④ R2：closure 版本三处门
grep -n "CLOSURE_VERSION" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt
grep -n "closureVersion" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt

# ⑤ R3：Room 导出配置与孤儿 JSON
grep -n "schemaDirectory" core/database/build.gradle.kts
sed -n '415,425p' core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/ | tail -6

# ⑥ R5：低负载判据（loadavg 不作硬门槛）
grep -n "只记录不作硬门槛\|不能用 guest loadavg" docs/research/2026-11-01-isolation-attribution.md

# ⑦ R6/R9：PDF 设备与读者证据缺口
grep -n "设备未跑项\|未验证\|pdfbox" docs/research/2026-11-02-pdf-incremental-aigc.md | head -20
grep -rln "PdfAigc" app/src/androidTest core/export/src/androidTest core/data/src/androidTest   # 期望空
grep -rln "PdfRenderer" core/export/src/androidTest
```

本轮 ⑦ 实跑结果：`grep -rln "PdfAigc" app/src/androidTest core/export/src/androidTest core/data/src/androidTest` → **无输出、exit=1**（三处 androidTest 树内零 `/AIGC` 引用，与基线 §六"KD-75"行同判）；`grep -rln "PdfRenderer" core/export/src/androidTest` → 仅 `MistakePdfDeliveryRegressionInstrumentedTest.kt`（既有 PDF 交付回归用例，不涉 `/AIGC`）。

---

## 6. 给主会话（一句话）

9 题里只有 R5、R8 可直接复用现有研究件；R1/R2/R3/R4/R6/R7/R9 都要新做但都有底座。**最要紧的两条**：① 计划 §0 与 WS1 item 1/4 的缺陷前提已被 HEAD 覆盖（C1/C2，我独立复核过代码、用例与提交），照计划重做会重复实现；② 计划 §2 写"55 项"实列 **62** 项、合计应为 91 而非 84（C6），验收前须先裁计数口径，否则会有 7 项在收口时凭空消失。
