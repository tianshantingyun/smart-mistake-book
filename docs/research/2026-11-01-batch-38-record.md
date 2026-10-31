# 批次 38 记录（2026-11-01）：三倍任务量批——V-02 方法切换 / V-03·V-04 持久化 / Q-02·Q-04 / A-09 + 隔离设备负向 / M-01·M-03·S-08 / D-04·D-06 / X-02·X-03 / 协议预算 + 三研究三预研 + 设备 24 套件 223 例全绿

> 记录时点：2026-11-01（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 写入，依据：计划 `.zcode/plans/plan-2026-11-01-batch-38-triple.md`（**22 行，本会话完整实读**）、批次 38 基线（`docs/research/2026-11-01-batch-38-baseline.md`，116 行）、三研究文档（R1 切换 `2026-11-01-method-switching-and-transactions.md` **330 行** / R2 度量 `2026-11-01-measure-artifact-and-budget.md` **293 行** / R3 持久化 `2026-11-01-preview-source-evidence.md` **160 行**，行数本会话 wc 实读）、三预研（KD-76 判据 134 行 / V-01④ PDF 腿 112 行 / R-04 Keeper 87 行）、设备证据 `docs/research/evidence/2026-11-01-batch-38/`（**15 个 .md，内容 74,019 B**，00-index **107 行**——本会话 ls/wc 复核）、`build/device-track-batch38/` 原始产物 67 文件（不入仓，ls 实测）、各轨工单摘要（转录）、复核轨发现 27 条与修复环两轮处置。**记录员未运行 Gradle、未执行任何设备命令、未做任何 git 写操作、未改任何 Kotlin/JS/Gradle 产品代码**；本轮改动 = 本记录 + 四件台账同步（§附录 A；其中 `known-defects.md` 与准入清单为共享脏文件——开工时已 M（T12 台账轨 + 复核修复轮在飞），整文件纳入并注明）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 3 轮 `exit=1`；修复环第 2 轮处置（§5.0）；强门（修复环后）`exit=0`；单测门复跑 `exit=1`；修复环后强门复跑 `exit=0`。**单测门失败构成未随工单传入**——记录员本会话对 2026-11-01 当日 JUnit XML 普查（`python -I` 解析，§6）：133 套件中**唯一红例 = `FusedRecallSupersetTest` 3 例 2 失败**（core/data knowledge 面，06:57，KB 线域，非批次 38 足迹），批次 38 core/data 侧 11 个新/扩 JVM 套件**无当日 XML**（执行证据 UNVERIFIED）；exit=1 的确切构成归因 UNVERIFIED。**强门绿不能替代单测门绿**（证据索引 §5.5 同口径）。
> **本记录不声称"批次全绿"**：设备 24 套件 223 例全绿（以复跑/末轮为准）但 **V-03/V-04 三个新设备套件只编译进 dex 未执行**（索引 §5.1）；批次 38 core/data JVM 套件无出数；单测门 exit=1 ×2 未归因；R-04 **维持阻塞**（本批只预研不实施）；KD-72/73/76 open 维持；JS 仍不广告（`availableMethods` 未动、生产装配恒三参）；4E.2 MVA 不成立；生产签名/arm64/16KB/真机 UNVERIFIED。
> 计划：批次 38 = 三倍任务量批（用户 2026-10-31「这一批直接三倍任务吧」），**25 项 → 13 轨**（计划 §1：A 纵向链与切换 5 / B 隔离设备负向 3 / C 度量与数学 3 / D 持久化与源证据 4 / E 协议与预算 4 / F 判据与解锁预研 3 / G 环境与台账 3）。

## 1. 范围与基线

### 1.1 计划范围与实际执行面

- **实际执行面**：三研究落盘（R1/R2/R3）+ 三预研落盘（F.20/F.21/F.22，只读/设计）+ 13 轨落码/判据/台账 + 集成交接（零接线）+ 设备轨五步（24 套件 223 例）+ 证据落盘（15 .md）+ 复核轨（27 条发现，2 中 25 低）+ 修复环两轮 + 本记录与台账同步。
- **与计划的偏差（全部显式登记）**：
  1. **单测门 exit=1 ×2**（第 3 轮与复跑）：失败构成未随工单传入；当日 XML 普查唯一红为 KB 线 `FusedRecallSupersetTest`（§6）——归因 UNVERIFIED，收口前须主会话取得失败清单。
  2. **V-03/V-04 设备套件未跑**：T2 轨三个新套件（Race 3 例 / Rebuild-Restore 3 例 / Retention 2 例）随两窗口构建**编译进 dex 但运行归该轨判据，本轨未执行**（索引 §5.1 原文转录）——其语义断言面全部 UNVERIFIED。
  3. **设备轨对 T2 两未跟踪测试文件的跨轨机械修复（升级授权，diff 留痕 step01 §2）**：`TutorVisualCommitRaceInstrumentedTest.kt` 补 `TutorVisualIdentity` import（build.log:381 `Unresolved reference`）、`TutorVisualRebuildRestoreInstrumentedTest.kt:228` 局部函数补 `suspend`（build.log:382）——纯机械、零断言改动，修复后 BUILD3_EXIT=0；主会话提交该两文件时应知悉含设备轨修复行（复核发现 #5 独立复核一致）。
  4. **计划第 6 项写 P-09、工单为 A-09**（与 inventory 计划第 6 项及基线 A-09 行一致，按 ask 执行——T4 轨 KDoc 登记）。
  5. **Q-02 未停项**：主会话预裁（语义"保留原像素"的 PIXEL_CLEANUP 一律 requiresPixelFidelity=true、policy 无条件拒绝）经语义三面钉死（枚举文/prompt 用途段/R3 §1.4）后**按预裁落地，未回主会话改裁**——研究未证明存在合法"可重绘"语义（T3 轨裁定，§3.3）。
- **不做（全部未做）**：JS 广告与 `availableMethods`；V-02 生产接线（X-01 归位裁定待主会话，runner 全仓无生产调用方）；M-03×V-02 交互的行为裁定（只登记，不预钉未裁行为）；R-04 Keeper 接入（触 settings.gradle.kts 热区，只做预研文档）；契约 `:208`（docs/current 7 M 热区未清空，登记不落笔）；锁文件提交（KD-72 分支 B）；`connectedAndroidTest`；真实 Provider；任何 git 写。
- **能力快照 / prompt / 广告文本零改动（沿裁定）**：生产装配 `SmartMistakeBookApplication.kt:364-368` 恰三参（复核 #20 实证）；`toolDeclarationsFor` js 双就绪位 `false/false` 逐位不变（`RoomModelTaskRepository.kt:963-965`，复核 #20）；`toolLoopPromptSuffix` null 时零字节追加（A-11 载体对 prompt 冻结面无影响，`OpenAiModelTaskAdapters.kt:662-673`）。KD-82 imagePrompt 的 JSON-Schema `maxLength` 4096→1024 属**协议 schema 数值对齐**（E 轨口径），非能力广告面。
- **DB schema 保持 71**：`StudyDatabase.kt:139` `STUDY_DATABASE_VERSION = 71`（本会话实读）；本批 DB 面 4 文件（`DatabaseContract.kt` 白名单扩值 +7、`StudyDbValue.kt` 常量 +8、`RoomStudyDatabase.kt` 端口查询 +11、`TutorVisualPort.kt` 端口方法 +15）合计 +41 行，`git diff` 无 CREATE/ALTER/version/Migration（集成轨实读转录）；entity/ 目录干净；`71.json` 无改动（S=71 门 PASS，§4）。

### 1.2 沿用裁定与纪律（本批执行，未改）

1. **热区禁改**：feature/tutor、feature/library、feature/review、feature/capture、feature/profile、core/ui 全部、core/markdown 整目录、core/export 全部、core/domain（开工即脏 M）、core/data 他线 M 面（dense/knowledge/`RoomMistakeDetailRepository`/`RoomTutorToolRunner`/SafeTruncation）、settings.gradle.kts、.gitignore、app Root 与开工即脏 M、docs/current 其余 M、docs/research 他线未跟踪记录、tools/kb*、tools/dense_build、tools/kb_coverage、knowledge-production。本批触碰的共享脏文件（`FigureGenerationPolicy.kt`〔Q-02 三 hunk + X-02 源证据门与他线在飞 hunk 共存，整文件纳入并按 KDoc 划分归属〕、`known-defects.md`、准入清单等）均"整文件纳入并注明"。
2. **Q-02 主会话预裁**：语义"保留原像素"的 PIXEL_CLEANUP 一律 `requiresPixelFidelity=true`、policy 无条件拒绝——本批落地（§3.3）。
3. **R-04 维持阻塞**：Keeper 解锁触 settings.gradle.kts 热区，只做预研文档不动热区（F.22）；**预研重要勘误**：最小接入不需编辑 settings.gradle.kts（pluginManagement 已含 mavenCentral+gradlePluginPortal），实际触面=3 个构建文件+settings-gradle.lockfile+KD-72 锁窗口——台账表述更正归主会话。
4. **设备绿绑定源快照（含 core/model/src）**：两构建窗口 before/after diff 全 0（1220 文件，索引 §4/E-01）。
5. 锁文件与 `tmp/` 不提交；禁 `connectedAndroidTest`；每次 commit 前主会话 `git diff --cached --name-only` 对照申报清单（批次 37 §10.1 加固条款）。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`147c4b9feb8d8830c695ab216f3ae14b04d4fdc0`（本会话 `git rev-parse HEAD`；设备轨窗口 HEAD=`dcfdc5b5`，落盘后半段 KB 线提交 `2bb4582a`→`147c4b9f` 两步前移——00-index §8 登记口径：KB 提交只把开工前已 M 的工作树文件入史、不改工作树字节，源快照窗口绑定不受影响；`147c4b9f` 的存在由本会话 git log 佐证，归属 KB 线）。
- **工作树**：**746 条**（`git status --porcelain | wc -l`；232 M + 514 ??，本会话实跑）——T11 轨读值 730（246+484）、复核轨读值 748（233+515）、证据索引自报 786→789，多线在飞持续滚动，差值未逐项追认 UNVERIFIED。
- **DB schema**：71；**KD 台账**：82 条（KD-72/73/76 open、KD-77/80/81 closed、KD-82 open 维持）。
- **docs/current**：7 条 M（热区未清空 → 契约 `:208` 维持登记分支不落笔，T12 实跑同集合）。

## 2. 三研究线

### 2.1 R1 方法切换与事务研究（`2026-11-01-method-switching-and-transactions.md`，330 行，驱动 V-02/T2）

核心裁定（工单转录 + 行锚抽验）：

- **最小实现=调用侧编排**（core/data 入口适配层）+ **同 operationId 第二次 coordinator.generate**：协调器/policy/审计/账本/DAO 零改动——审计面天然支持：`insertOperation=IGNORE`（同 operationId 重放不覆盖，`FigureGenerationDao.kt:27-28`）、attempt 行 ABORT 唯一（`:103-104`）、操作终态由最后尝试覆写（`:87-101`）；账本 per-operation 计数跨方法累计（`MAX_DISPATCHES=6`，`ModelTasks.kt:61`；`attemptOrdinal=logicalDispatchCount`，`RoomFigureDispatchLedger.kt:107`）。
- **方向不对称是结构性的**：JS→IMAGE 可环内机械回退（`imageModelProposal` 工厂现成，`FigureGenerationPolicy.kt:74-91`）；IMAGE→JS（"图像不佳"）的 program 只能来自模型——最小实现=失败诊断经有界 TutorVisualContext 回喂下一次已有预算内模型回合（契约 §7），**不做机械回退**。
- **切换触发判据 fail-closed**：可归属程序错才回退；ENGINE_CLOSED/[UNATTRIBUTED]/账本 Denied/admitFinalFigure 拒绝一律不触发。
- 设计含 8 个 JVM/设备用例（§2.5）、V-03 三组仪器化 7 例（§3.2）、V-04 九个竞态面断言表（§4.2）、外部参考评分表（resilience4j/Temporal/outbox，gh api 实证健康度）。候选 A（协调器内部 IMAGE→JS）与候选 C（白烧派发）否决，取候选 B（落码同型，§3.1）。

### 2.2 R2 文本排版度量与预算研究（`2026-11-01-measure-artifact-and-budget.md`，293 行，驱动 M-01/M-03/S-08）

- **M-01**：类型化布局 artifact 字段语义表 + `inkBounds` 坐标约定（`Paint.getTextBounds` 口径、**无包含不变式**〔允许负坐标/越盒〕、**不进 digest**）+ 消费面映射 + 受热区约束的两步推进（端口五字段 inkBounds 增补=core/domain 热区解锁后再提）。
- **M-03**：4MP 按 **du 口径**（2048²）、16MiB 按实际 px×4B 分配前置门、**两门独立判**（4MP×4B=16MiB 数字巧合自洽已注记，不得合并）、负向矩阵 N1–N9。
- **S-08**：`MathContentLowering` 落 core/model 收编 `parseAndBuildBox`+`ReadableMathText` 既有链（两段式降级：预算越界+真解析异常），**不合并三处现有消费方**。
- **GitHub 评分**：jlatexmath 全家族 **GPL 一票否决**（LICENSE 实读）、Markwon Apache-2.0 但捆绑 GPL 组件仅作设计参考、androidx 为唯一可引实现。

### 2.3 R3 预览/持久化源证据研究（`2026-11-01-preview-source-evidence.md`，160 行，驱动 D-04/D-06/X-02/X-03）

四轨源证据全部闭合（全部来自该轨实读/实跑，命令与 path:line 随文记录）：

- **D-04**：`LOCAL_VISUAL_PREVIEW` 主树 0 命中（开工时点）；但"缺必需字节拒绝完整包"两侧判据已存在且自动覆盖预览引用——六站点谓词 `CanonicalAssetReferenceSites.kt:31-54` 第 6 支即 `tutor_visual_snapshot.preview_asset_id`（schema 71 建 RESTRICT FK）、成包侧 `AndroidBackupRepository.resolveReferencedAssetFiles`（:374-386）fail-closed、恢复侧 `verifyStagedAssetClosure`（:1192-1218）交换前逐位拒收。缺口收窄为三：① `SourceAssetType` 常量；② 预览 PNG 写侧（生产零写入方）；③ 设备 suite 预览向量。
- **D-06**：cache-only resolver 半边已落码且语义完整（`resolveOne` 只读、缺失回 NOT_GENERATED、出网仅经显式 regenerate）；缺口=0 派发/0 计费观测门（基线"5 例"滞后，实为 7 @Test 全为显式生成臂）。
- **X-02**：两符号全仓 0 命中维持（开工时点）；可挂载体已定位（`FigureSourceEvidence`/账本 reservation/attempt 回写点）；真缺口=`TutorQuestionContext`（feature/tutor 热区）无原题资产字段、Respond 只发 studentImageAssetRefs。
- **X-03**：strictOffline 现状=构建变体；静态冷恢复半边已落码；本地 JS attempt `reservation_id=NULL`+operation 0/0 是"0 远程派发"的现成正向证据位；缺口=①档交互重布局的 strictOffline 设备向量。
- **GitHub 参考评分**（gh api 2026-11-01 实跑，license 逐仓取证）：coil-kt/coil A、android/nowinandroid B+、restic B、contentauth/c2pa-rs B-、in-toto/attestation C+、sqldelight C（不采用）。**结论：四轨均无需引第三方依赖，参考价值在语义对齐。**

## 3. 实施轨 + 集成（13 轨逐轨）

> 判定口径：**落码成立** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 门禁工单给定 / JUnit XML 读值 / 无证据 = UNVERIFIED）。各轨文件 git 状态（M/??）本会话逐一实跑核对（§10）。

| 轨 | 工单状态 | 本记录判定 | 详节 |
|---|---|---|---|
| V-02 方法切换 | 完成 | **runner + JVM 7 例落树（??）；生产接线零调用方（X-01 待裁）；M-03×V-02 交互双侧 KDoc 登记在场（本会话实读）** | §3.1 |
| T2 V-03/V-04 | 完成 | **2 处实现缝 + 5 测试文件（JVM 12 例 + 设备 8 例）落树；按实读缺口矩阵只补真缺向量；设备执行未跑（索引 §5.1）** | §3.2 |
| T3 Q-02+Q-04 | 完成 | **Q-02 预裁落地（policy 有效位 + 入口归位，JVM 5 例）+ Q-04 五组三件套 + JVM 10 例 + Node 漂移门（94 passed、5/5 byte-identical 实跑复现）** | §3.3 |
| T4 A-09 | 完成 | **app androidTest 注入式 3 例（711 行）落树；设备 3/3 绿（复跑轮）** | §3.4 |
| P-09/P-10/P-04/P-05 | 完成 | **三件 app androidTest 套件（279/285/264 行）落树；设备 E-03/E-04/E-05 各 1/1 绿；core/data 侧不落文件（指认不出未覆盖失败，偏差登记）** | §3.5 |
| T6 M-01/M-03/S-08 | 完成 | **artifact DTO + inkBounds + 预算器（4MP 接线引擎）+ MathContentLowering 落树；JVM 全绿（XML 读值 ×7 套件）** | §3.6 |
| T7 D-04/D-06 | 完成 | **常量+白名单+唯一写口+JVM 3 例+设备 3 例（C4 5→8）+ 观测门 7→10 落树；D-04 生产接线未做** | §3.7 |
| T8 X-02/X-03 | 完成 | **VisualSourceBasis + 源证据门 + seen 观测 + StrictOfflineVisualRelayout 判定门 + 3 测试（17/7/9 例）落树** | §3.8 |
| E 协议与预算 | **部分** | **四项全部落码（KD-82 imagePrompt 1024 / A-10 6 例 / A-11 载体 + 21 例 / A-12 46 例）；JVM 出数缺 core/data 当日 XML** | §3.9 |
| F 判据与解锁预研 | 完成 | **三份预研文档落盘（134/112/87 行）；零 Gradle/零设备/零 git 写** | §3.10 |
| T11 锁窗口 | 完成 | **分支 B——锁窗口不可行（W1 六文件全红等），零动作，KD-72 open** | §3.11 |
| T12 台账 | 完成 | **清单 17 行补记 14 + known-defects KD-73/76/77 三块；契约 :208 登记不落笔** | §3.12 |
| T13 条件轨 | 完成 | **分支 D 九项只读复核全部仍 pending；新漂移仅 AUD-08 锚 +1；零文件落盘** | §3.13 |
| 集成交接 | 完成 | **四项中两项已是接线完成态、一项设计留待、一项无可新增消费点——零代码改动** | §3.14 |

### 3.1 V-02 方法切换（任务 1，落 core/data visual）

- **按 R1 §2.2 候选 B 落地**：新增 `FigureMethodSwitchRunner`（internal，单方法 `generateWithMethodFallback(request, primary, imageFallback)`）——一次切换 = 同 request（同 operationId，`TutorFigureGenerator.kt:330` 派生不含 method）的第二次 coordinator.generate，**协调器/policy/审计/账本/DAO 零改动**。
- **切换判据 fail-closed**（R1 §2.4）：仅 JS 主尝试可切——FAILED+可归属拒绝前缀（程序侧五类 + HARD_DEADLINE_EXCEEDED，引用枚举常量非字面量）→ 切；UNAVAILABLE、[UNATTRIBUTED]、异常形状诊断 → 不切；REJECTED+JS 专面预算文案（锚 `FigureOperationLocalBudgets.kt:59/:72-74/:79-81`）→ 允许转 IMAGE 一次；账本 Denied→FAILED 无分类前缀 → 不切；取消无 catch 如实传播。`explicitUserRetry` 原样透传（远程路径结构上不读它，不借道解 JS 熔断）。fallback 由调用方以 `FigureGenerationPolicy.imageModelProposal`（:74-91 现成工厂）构造，reason 登记前一 attempt 有界诊断摘要；返回 null 如实回主结果。
- **测试先红后绿**：`FigureMethodSwitchTest`（**7 例**，本会话 grep -c 同值；真协调器+fake 引擎/账本/通道/审计）——切换成功（例 1 全链审计断言；例 2 设施类不切）、切换后预算语义（例 3 账本门不绕行不重置、例 4 同 operationId 跨轮序号 1→2 续接、例 5 JS 墙钟耗尽仍可转 IMAGE 一次、例 6 最终图上限分派前拒 fallback 不发起）、双重载荷拒绝（例 7 全程恰一产物 ImageAsset、JS 腿 FAILED 无产物、fallback 提案零 program 残留、恰一次 SUCCEEDED）。复核 #7/#21 独立复核三组重点全过。
- **牵动面枚举**（任务三，转录）：审计（操作行 INSERT IGNORE 保留首个 JS 派生指纹、per-attempt 才是载荷身份、两调用间操作行短暂复活、进程恰死由 findStaleOperations 判僵尸——Runner KDoc 登记）；账本（per-operation 持久计数跨方法共用、JS 不 reserve、切换后 IMAGE 预留被拒终态 FAILED+真实计数）；policy（fallback proposal 第二次 generate 全量重验；宿主须两方法都真实声明）。
- **KDoc 登记（复核修复轮第 2 轮处置，本会话实读在场）**：`FigureMethodSwitchRunner.kt` 类 KDoc 表 **M-03×V-02 行**（`INVALID_PROGRAM: LAYOUT_PIXEL_BUDGET` 落入可归属切换集=现行为、"越界属程序侧确定性超限还是输入侧越量"为**开放裁定点**归主会话、改裁定须同步两处注记）+ companion `ATTRIBUTABLE_SWITCH_REJECTIONS` 指向句（:150-154）；`VisualLayoutBudget.kt:31-36` 同登记。**残余缺口（处置申报）**：LAYOUT_PIXEL_BUDGET 场景专项用例未落（FigureMethodSwitchTest 现 7 例无此场景；用例随行为裁定同批落，不预钉未裁行为）——已登记进台账 V-02 行（清单 v3 补记 15）。
- **IMAGE→JS**（"图像不佳"）按 R1 裁定不做机械回退，KDoc 登记为模型回合驱动（载体 `TutorVisualContext.kt:23` 在树但 A-07 生产零构造，接线归 X-01）。
- **生产接线无调用方**：全仓 grep 仅 runner+测试（复核 #19 同判）；KDoc "前置依赖（如实登记）"节明文 main 暂无调用方——不抢 X-01 主会话裁定。

### 3.2 T2 V-03/V-04（任务 2/3）

- **落码**：2 处实现缝（均主代码最小增量、开工前干净文件）+ 5 个测试文件（JVM **12 例**〔`VisualSnapshotRestoreReadSideTest`——T2 摘要申报 11 例系欠报，复核 #1 实测更正；本会话 grep -c=12 同值〕+ 设备 **8 例**）。
- **实现缝各自灭的失败**：S1 `readLatestLiveTutorVisualSnapshot`（`TutorVisualPort.kt`+`RoomStudyDatabase.kt`）灭"恢复续读无端口级出口——调用方只能跨层摸 internal DAO 或手写 SELECT 致 LIVE 谓词/排序口径漂移"；S2 `VisualSnapshotRestoreReadSide`（core/data）灭"行→恢复记录映射无生产权威侧 + content_ids 损坏被静默当空集"。
- **每条用例灭掉的失败（要点转录）**：V-03 ① 删会话把保存题图/恢复续读源一起带走（裸 FK 负向之上首次走通生产删除路径端到端）；② 启动对账删掉被题面固定的 STAGING 预备行；③ 重建续读无端口出口/读错 checkpoint；④ 恢复腿被接上引擎/派发且无声（无引擎装配+三张派发账面 0 行）；⑤ 写侧行载与恢复门读列脱节；⑥ JVM status→live 口径漂移/content_ids 松散解析/映射"修好"版本列/读侧引用引擎网络类型。V-04 ⑦ 预算拒绝后同轮假死/半行残留；⑧ B 的 GC 使 A 手里租约失效的后半边（合成 DEFAULT_VISUAL_STAGING_LEASE_MILLIS KDoc 完整闭环）；⑨ 迟到结果把消息指针引向未提升/悬空快照（CAS EXISTS 的 LIVE 门）。
- **不重复新增**：研究建议的组 1 例 3（预算压力不删被固定事实）经核对已被批次 30–33 既有用例覆盖（staging 行集不变断言+gates pin 谓词），未新增第三份同型预算用例（新增必须指认纪律）。
- **执行状态**：三个设备套件**已编译进 dex（经偏差 3 的授权机械修复）但未执行**（索引 §5.1）；JVM 12 例**无当日 XML**（§6）——两半边全部 UNVERIFIED，本记录不声称其绿。

### 3.3 T3 Q-02 + Q-04（任务 4/5）

- **Q-02 裁定研究结论：无合法可重绘语义，按预裁落地，未停项**。语义三面钉死：枚举文（`FigureGeneration.kt:55-56`）、prompt 用途段（`FigurePromptAssembler.kt:302-303`）、R3 §1.4（`2026-10-26-4e2-entry-checklist.md:111`）；`requiresPixelFidelity` 契约语义是"只能走支持该能力的 EDIT"（`FigureGeneration.kt:113-114`、`FigureGenerationPolicy.kt:360` 门），不是逐位相同，故 IMAGE 净化链不受影响。
- **落地两处**：① core/model policy 权威——新增 private `effectiveRequiresPixelFidelity(request)`=宿主显式位∨(purpose==PIXEL_CLEANUP)，`validateJsProposal` 与 `validateImageModelProposal` 两道 pixel 门改用有效位（不接收宿主声明降级；这是 target 语义规整，不是用途→方法路由，prompt/能力快照/广告文本零改动）；② 入口声明面归位——`ConfiguredCleanImageGenerator.kt:109` `requiresPixelFidelity = true`（本会话实读；开工时该文件 git 干净、本轨修改）。现网影响闭合：PIXEL_CLEANUP 全仓仅净化入口使用；净化链=EDIT+IMAGE_MODEL_ONLY(supportsPixelEditing=true) 照常放行；grep 实证无既有 PIXEL_CLEANUP×JS 用例翻红。
- **红→绿构造性钉子**：修复前 policy 只看 target 位而入口传 false，PIXEL_CLEANUP+JS proposal 会被放行——新测试第 1 例正钉该旧漏洞形状，修复后 TARGET_UNSATISFIABLE。JVM 正/负例 **5 例**（`FigureGenerationPixelCleanupMappingTest`；XML 出数 **5/0**，§6）。
- **Q-04**：按 R3 §4.3 静态 MVA 必测子集落 1/2/5/6+新组合（截图 3=原生卡片、4=4E.2 表单不属 JS 静态子集）。语料 `core/data/src/test/resources/visual/fixtures/q04/` 三件套×5+README（**16 文件**；fixture.json=SceneV1+metrics+labels+subject 学科声明参数、drawops.json=生产编译器逐字节真实输出非手写、oracle.json=独立渲染结构预期含按 label 公式独立推导的终位置）。fixture 与 oracle 分文件分断言；学科正确性与渲染 fixture 分开（subject 块+测试内独立重算：比例 3:1、shoelace 面积=3F0h/8=1500、受力方向单位向量、阶段映射、波形顶点叠加函数重算；两条分离方向反证）。JVM 正/负例 **10 例**（`VisualFigureFixtureOracleTest`——T3 摘要申报 8 例系欠报，复核 #2 更正；本会话 grep -c=10 同值；构成：1 oracle 全量匹配+5 学科正例+1 几何篡改参数化负例+2 分离反证+1 malformed 按码拒）。配套漂移门 `tools/visual_compiler/compile_fixtures.mjs`（生产同源编译器重编译 vs 提交 drawops 逐字节比对，--write 重生成）。
- **验证证据（T3 轨 Node 侧实跑；Gradle 按纪律未运行）**：`node tools/visual_compiler/run_tests.mjs` → 94 passed, 0 failed；`compile_fixtures.mjs` verify → 5/5 byte-identical；Node 口径预验证 oracle 结构 5/5、学科数学 5/5、分离负例 2/2。复核 #9 于复核轨会话实跑两命令**复现同值**（94 passed / 5/5 byte-identical 无需 --write）——记录员未重跑该两命令，转录复核轨结果。
- **整文件纳入注明**：`FigureGenerationPolicy.kt` 开工即脏 M（他线 X-02 在飞并行写入，diff 由 +4 涨至 +42 证实）——本轨 3 hunk 与 X-02 hunk 文本不重叠，提交时整文件纳入、归属按各自 KDoc 划分（复核 #11 亲读两线 hunk 共存在位）。

### 3.4 T4 A-09 广告-执行一致性注入式设备用例（任务清单第 6 项）

- 新文件 `app/src/androidTest/.../visualjs/VisualJsAdvertiseThenExecuteInstrumentedTest.kt`（**711 行**——工单申报 712 系换行计数差，复核 #3 更正；3 用例；未触任何热区/生产源/既有测试）。
- 正例：测试装配注入"生产默认 IMAGE_MODEL_ONLY+JS"能力快照 + `FigurePromptAssembler.assemble` 同源组装 + 真隔离 `VisualJsClientEngine`/帧输入经工厂第 4/5 参注入 → GENERATED+Program 产物+programId 公式+**0 远程派发四断言位**（审计 operation reserved/used=0 且 SUCCEEDED；attempt 恰一行 method='JS' 且 reservation_id IS NULL；预置父操作 dispatch_count 整窗零触碰；Program 绝非 ImageAsset+`networkRequestsAllowed=false`+UnreachableConfigurationStore tripwire）；再对同一 program 直取引擎二次执行钉 Rendered 本体与 `c0-<digest8>` contentId 形状，复钉直取执行不写审计。
- 负控一：生产三参装配（不注入引擎）→ UNAVAILABLE、无产物、无 attemptId、审计无行。负控二：默认 IMAGE_MODEL_ONLY+引擎注入 → policy 门拒（REJECTED，"本机未接通 JS 方法，不得假装可用"逐字）、无审计行、账本零触碰——**如实登记**：coordinator 级默认快照的真实归宿是 REJECTED 而非 UNAVAILABLE（`FigureGenerationPolicy.kt:135-140`→`DefaultFigureGenerationCoordinator.kt:176-184` 实读证实），UNAVAILABLE 属生产装配腿。
- **设备证据（E-02，只读转录）**：首跑 2/3 绿、红例按 D 归因（服务侧 L2 看门狗 1500 ms kill、九类崩溃签名 0），step21b 同 APK 复跑 **OK (3 tests)** 判 PASS。
- 生产广告开关（js 双就绪位 false/false、figureMethods 默认、轮次/执行器 fail-closed 门、app 三参装配）零改动。复核 #20 专项：能力快照/广告文本零 diff、`FigureGenerationCoordinationFactory.create` 生产调用点恰三参。

### 3.5 P-09/P-10/P-04/P-05 隔离设备负向（任务 B.6/B.7/B.8）

三件新增 app androidTest 套件（均 1 @Test，279/285/264 行，?? 未跟踪；零 Gradle/零 adb/零 git 写/零生产代码与既有套件改动）：

- **P-09** `VisualJsClientFrameQueueDeviceInstrumentedTest`：补准入清单 :132 缺口②"经 engine.execute 的客户端队列设备负向"——真 `:visual_js` 进程上 4 program 并发（1 在飞+2 待绘+第 4 个 BUSY full / 同 program latest-wins 旧等待者 BUSY superseded 且新 state 内容级渲染 / 跨 program FIFO 完成序单调）；确定性窗口=abandonForTest 真实终止 worker 后的"恰好一次重建"（≥2 s warm-up）；断言含 BUSY 诊断前缀钉子。**设备 E-03 1/1 绿**。
- **P-10** `VisualJsDeathUnconfirmedFailClosedInstrumentedTest`：补清单 :133 死亡未确认设备负向——UnconfirmableDeathWorkerContext 假 binder 确定性跑满宿主 2500 ms+死亡确认 2 s 两条预算：有界 HARD_DEADLINE_EXCEEDED、端到端 ≤2500+2000+500 ms 计时门（KD-66 门同式、不追加容差）、unavailableForTest 锁存、bindCount 恒 1、显式重试仍 ENGINE_CLOSED 且诊断含 "unconfirmed death"、同源码非重试 PROGRAM_FUSED。UI fallback 呈现半边不可从 androidTest 落——如实登记未覆盖，本套件钉住呈现层赖以取材的引擎事实。**设备 E-04 1/1 绿**。
- **P-04/P-05** `VisualJsReconnectBudgetInstrumentedTest`：补预算三元组唯一缺的"重建 10 s"注入形设备例（首连 15 s=既有 `aFirstConnectTimeout` :705、死亡确认 2 s=本批 P-10 套件，三者成套）——attach 推迟 11 s：首连必成功使归因单一化、重建必 10 s 超时→loading 如实失败、诊断钉 "10000 ms"、不锁存、重试仍允许、晚到 attach 收尾不锁存。**设备 E-05 1/1 绿**。
- **KD-66 hard+500ms 回归确认（P-05 半边）**：关闭门用例 `aHungWorkerReleasesTheCallerWithinHardPlus500ms` 现树 :479、门常量 :890-891（hard+500 相对式）、断言 A1–A7 实读完整未放宽、文件 git 干净；P-05 机械判据 grep（`1500 ms|硬上界|硬终止` 于 `FigurePromptAssembler.kt`/`TutorToolDescriptions.kt`）= 0 命中；KD-66 closed 块 known-defects.md:2573（批次 28 关闭）——设备复跑归设备轨。
- **偏差登记（core/data androidTest 未落文件）**：三个任务的全部语义面都在 app 模块且依赖 app-internal 测试 seam；core/data 侧同语义面已被既有设备绿覆盖（引擎级单在飞 BUSY=core/data 引擎套件、服务层消费者校验=A-14 三例批次 26 绿、伪造帧=JVM 五例）——在 core/data 新造文件无法指认它消灭的任何未被覆盖失败（新增必须指认纪律），故不落；若主会话裁定仍需 core/data 载体请给出具体失败面。
- 他线在飞观察（该轨登记）：本会话进行中同包新增 A-09 面 ?? 文件（非该批）且 `VisualContentMeasuring.kt` 被置 M（T6 轨产出）——三文件与其零冲突。

### 3.6 T6 度量轨 M-01/M-03/S-08（任务 9/10/11，按 R2 §3/§4/§5）

- **M-01 完整布局 artifact**：① 新增 public 类型化 DTO `VisualMeasuredLayoutArtifact`（core/data visual）：kind/宽/高/baseline/实际字号/摘要/lines/degraded/inkBounds 九字段，init 强制 lines-null⇔(math 且未降级)、墨迹有限且有序、**无包含不变式**（允许负坐标/越盒），KDoc 内置完整消费点枚举表（逐字段）；② app 度量器新增墨迹计算：后端 seam 增 `textInkBoundsPx`（`Paint.getTextBounds` 口径逐段联合，空段不计入、全空→(0,0,0,0)），math 墨迹第一版=盒自身，新增唯一折算点 `toArtifact`，`measure()` 先产 artifact 再投影端口五字段（单一事实来源）；③ 端口五字段照旧映射零改动（core/domain 热区禁改）；metricsJson wire 形状零改动。
- **M-03 排版预算**：① 新增 `VisualLayoutBudget`（public）：`MAX_TOTAL_LAYOUT_PIXELS_DU=4_194_304`（du 口径 2048²）、`MAX_BITMAP_BYTES=16_777_216`、码 LAYOUT_PIXEL_BUDGET/BITMAP_BYTE_BUDGET，纯函数 boxPixelsDu(ceil 积)/totalLayoutPixelsDu(Double 累加防溢出)/transactionBudgetDiagnostic/requireBitmapBytesWithinBudget(分配前拒)；KDoc 写死"两门独立判，16MiB÷4B=4MP 是巧合级对齐不得合并"+"JS 侧不镜像"；② 已接线唯一本批可达执行点：引擎度量结算处（`QuickJsVisualExecutionEngine.kt:418-421`，measurer 返回后、render 前，越界折 INVALID_PROGRAM+码）——映射判断：prepare 派生的确定性超限与"度量批次构造器预算→INVALID_PROGRAM"同族，折 ENGINE_CLOSED 会反向误归因成设施失败；预览 createBitmap 前置门接线归 D-04 轨（其 Persister 未引用本预算，无冲突）；③ 12..48sp/fontScale 门已落（复用零改动）；负向 N1/N2/N4/N6 门函数半边补齐，N5/N6 设备半边/N7 随 D-04 分配点落地（测试 KDoc 登记）。
- **S-08 数学 lowering**：新增 `MathContentLowering`（core/model，纯 Kotlin 零 android 依赖）：`lower(input,fontSizePx)` 唯一入口，Result.Box/FallbackText，两段式降级（预算越界携 budget error+防御 catch，现树无 throw 点经 MathAst.kt 实读确认）；readable=**原文透传非重建**（与 ReadableMathText 同源原则）；app `measureMathBox`/`measureContentEntry` 改调它（行为零变化：降级仍度量原文、金样数字不变、MATH 分支只 lower 一次；`VisualContentMeasuring.kt:404` 接线本会话实读）；未知命令仍是字面 atom（宽容解析不是失败面，测试钉住防误改）；坐标语义=几何唯一来源是 MathBoxBuilder，回放批解锁 PAINTER_LABEL_MATH 时直接折 VisualLabelContent.Math 无需复制降级判断。
- **热区纪律**：仅只读 core/domain；core/ui、core/export、core/markdown、feature/*、settings.gradle.kts、.gitignore 零改动；开工前复核全部目标文件 clean；既有脏测试文件（VisualDrawOpsParserTest/FigureGenerationAuditRecorderTest，批次 30 遗留）未触碰。
- **JVM 出数（XML 读值，本会话解析）**：`MathContentLoweringTest` 8/0、`VisualMeasuredLayoutArtifactTest` 9/0、`VisualLayoutBudgetTest` 13/0、`VisualContentMeasuringTest` 16/0、`VisualMeasuredLayoutInkBoundsTest` 9/0、`VisualMeasureEnvironmentBudgetTest` 5/0（§6；core/data 侧三件 XML 时间 03:46、app 侧 06:41）。

### 3.7 T7 D-04 预览 canonical 资产 + D-06 cache-only 观测门（任务 12/13）

- **D-04**：① `StudyDbValue.SourceAssetType` 增 `LOCAL_VISUAL_PREVIEW` 常量（TEXT 值，零 schema）；`DatabaseContract.sourceAssetTypes` 白名单同步扩值（沿 GENERATED_FIGURE 先例：该类型经 registerCanonicalSourceAsset 落库、不经 validateSourceAsset）；② 新增 `TutorVisualPreviewAssetPersister`（core/data/visual）：唯一预览写口——预览 PNG 按共享常量类型落成 canonical 资产（走 vault 统一预算面，内容寻址幂等）→ 登记资产行 → 返回记录供 `TutorVisualSnapshotInput.previewAssetId`；forProduction 装配镜像 AttachedImageGeneratorFactory 的 persist+register 组合；顺序即语义（引用建立时资产行必然已在，RESTRICT FK 不会被拒）。③ 测试：JVM 3 例（常量钉死+登记同体+空字节先拒+登记失败穿透）；设备 3 例追加到 `BackupVisualReferenceInstrumentedTest`（5→8）：⑥ 预览与 program/快照同包走归档→恢复、字节逐位核对+V-R10 全绿；⑦ 第三方缺页包被 verifyStagedAssetClosure 交换前拒绝、原库不动；⑧ 成包侧引用字节缺失 → create 抛"备份未完成"不发半包。**设备 E-09 C4 5→8 含此三新例、全绿**。
- **D-06**：`FigureHttpTransportBypassObservationTest` **7→10 例**，新增"入口四"三臂（miss→NOT_GENERATED / 完好命中→Ready / 损坏→DAMAGED），每臂双观测位：MockWebServer 真实通道下 `server.requestCount==0` + WiringLedger(granted=true) events 为空（哪怕预算充足打开消息也一次都不问账本=0 派发/0 计费）；`attachedGenerator` 加带默认值的 lookupExisting/isIntact 参数，既有 7 例零改动。
- **指认消灭的失败（12.2，各配 KDoc）**：常量+白名单（source_type 落在未知取值面/字面量漂移）；唯一写口（调用点自拼三步漏登记行→RESTRICT FK 拒快照提交且无可读原因）；设备⑥⑦⑧（成包漏收第六类站点/恢复侧闭合门只是纸面语义/发出恢复必失败的半包）；D-06 三臂（resolve 被接上自动生图→翻旧消息被静默扣生成额度）。
- **影响面闭合（12.1）**：source_type 读者全查（六个消费面），无遗漏分支（转录于轨摘要）。
- **登记（复核 #17）**：写侧无生产调用方——`TutorVisualPreviewAssetPersister`/`forProduction` main 源集零调用点、`previewAssetId` 生产仍恒 null；本批交付唯一写口工具类本身，快照提交接线归后续批；台账 D-04 行如升级"已落"须限定"写侧/读侧已落、生产装配未接线"。

### 3.8 T8 X-02 源证据 + X-03 strictOffline 重布局（任务 14/15）

- **X-02**：① core/model 新增 `VisualSourceBasis`（subject/revision+canonical 引用/SHA 成对+允许披露范围+有界可追溯 structuredFacts 及其 sha256）与 `VisualSourceSeenRecord`（sourceSeenDispatchId+contentSha256）；② `FigureGenerationPolicy.validateImageModelProposal` 新增源证据门：basis 未授权披露原图时任何把源图字节送出网的 IMAGE_MODEL 派发拒绝（`SOURCE_DISCLOSURE_UNAUTHORIZED`），canonical 引用/SHA 与实际源证据不一致 fail-closed（`SOURCE_EVIDENCE_MISMATCH`）；basis=null 旧调用整段跳过逐位零变化；③ coordinator 在 markDispatched（与账本 used=1 同点）后、仅当源字节非空且 attempt 行真实写成时产出 seen 记录——"同批准备不冒充已发送"由类型分离+attemptId 门保证（复核 #23 独立复核通过：三门先于预留、账本零调用断言、两口携带、outcome 不变量钉 attemptId）。
- **X-03**：core/data 新增 `StrictOfflineVisualRelayout` 纯函数判定门——输入只有 `PersistedVisualProgram`（类型上无"新源码"入口）+ localEngineAvailable + programFused + 可注入兼容集；判定序：引擎关→RUNTIME 未知→熔断→①档放行，降级带三码接 `VisualSnapshotRestore` ②/③档（既有判定不复制）；"禁新外发"由重布局走协调器 JS 路径的结构保证（该路径不触账本）。
- `TutorQuestionContext`（feature/tutor 热区）与 Respond 原题图接线按研究 §3.3 归热区窗口批，本批未动。
- 测试：`VisualSourceBasisTest` 17 例、`VisualSourceSeenDispatchObservationTest` 7 例、`StrictOfflineVisualRelayoutTest` 9 例（本会话 grep -c 同值；core/model 侧 SourceBasis XML 出数 17/0，§6）。

### 3.9 E 协议与预算（任务 16/17/18/19：A-11 请求半边、A-12 扩测、A-10 跨 method 合计、KD-82 imagePrompt 残余）—— 工单状态"部分"

- **KD-82 imagePrompt 残余**：schema 广告面 4096→⌊4096/4⌋=1024 码点（`OpenAiModelProtocol.kt:561` `.MAX_IMAGE_PROMPT_UTF8_BYTES / 4`，本会话实读；单点；本地 4096 字节门三执行点零改动），`ProgramSchemaMaxLengthAlignmentTest` **4→7 例**（钉子翻转 1024 + ASCII 上界正例 + 4 字节恰边界 4096B 正例 + 1025 码点=4100B 超面负例 fail-closed 含 "4096 UTF-8 bytes" 字节计数消息；先红后绿：对旧 4096 广告面为红）。**KD-82 状态维持 open**——本批为协议 schema 数值对齐非能力广告面裁定，JVM 出数缺当日 XML（§6）；台账块由本记录员落（§附录 A）。
- **A-10 跨 method 合计**：`FigureGenerationLocalBudgetBoundaryTest` **5→6 例**：同 operationId 先 JS（引擎产出第 1 份）后 IMAGE（第 2 份）后第 3 份 IMAGE 在开后端/预留/通道/进引擎前拒（四计数器逐一钉死），并断言 JS 腿 0 预留；门本体按 operationId 计数与 method 无关（批次 36 已在树），本批为覆盖闭合、无新门。设备 E-11 复跑 3/3 绿（含跨 method 计数不重置位+终答位子门）。
- **A-11 请求半边**：渲染载体落 `OpenAiModelTaskAdapters`（`toolLoopPromptSuffix` 新增 visualContext 参数，Plan/Respond 两调用位接线、Lobby 不接；null 时零字节追加=prompt 与接线前逐字节相同，冻结面保持）；信标常量 VISUAL_SOURCE/STATE_BEGIN/END 内部可见；visualContextBlock 按 §7.1 渲染 capabilityVersion/最近一份程序源码/当前 state/截断诊断。`ModelWireProtocolSourceJsFidelityTest` **15→21 例**：三协议 × 4 文本向量表 UTF-8 逐字节断言 + Respond 半边 + null 不落块 + state/诊断/能力版本/有界字节声明专项。
- **A-12**：`ModelTaskFingerprintStabilityTest` **42→46 例**（既有 42 例未动一字）：v17↔v18 跨版本逻辑指纹稳定、v17 旧行读回两指纹与存库一致、真载体改变指纹、legacy v17 行携带真载体构造期 fail-closed 双半拒绝（ModelTasks 构造守卫此前零测试）。
- **执行状态**：全部 JVM 出数 UNVERIFIED（core/data 当日无 XML；core/model `ModelTaskFingerprintStabilityTest` XML 出数 **46/0**，§6）。

### 3.10 F 判据与解锁预研（任务 20/21/22）

三份预研文档落盘（共 333 行），零 Gradle/零设备/零 git 写、未触热区：

- **任务 20 KD-76 校准**（`2026-11-01-kd76-ready-timeout-calibration.md`，134 行）：三时刻两分段观测口径（客户端无分段日志=实跑 grep 证实，给最小计时 seam 设计 `connectStageTimestampsForTest`）；条件矩阵 C1–C4；三档判定规则（≤2s 维持、2–5s 登记余量、>5s 阻塞上报+调常量牵动面 6 项）；29.5s 链两口径方案；既有分段证据盘点（attach 4.6–7.5s E-01d、warm-up p50=686/p95=725/max738ms E-04 等）；③ 产品进程首请求结构性依赖生产装配批（生产恒三参）已如实登记。
- **任务 21 V-01④ PDF 腿**（`2026-11-01-v01-pdf-leg-exposure-design.md`，112 行）：逐文件 grep 实证不可达符号面；三方案评分后推荐方案 A=core:data main 单新文件薄公开测试缝 `VisualChainTestSeams`（opaque 签名不透 internal 类型、入参用公共 `TutorVisualSnapshotRecord`、app 套件 3→4 例），配 grep 门防生产旁路；**硬前提=R-04 release 轨须 keep（机制②同型，P-12③ 移交项先例）或等 Keeper 自动推断 keep**；触面/回滚/决策点齐备。
- **任务 22 R-04 Keeper**（`2026-11-01-r04-keeper-prerequest.md`，87 行）：上游 slackhq/Keeper 0.16.1 在 AGP 9 结构性不可用（issue #152 实证 apply 即崩；2024-05 起孤儿化）；可行对象=活跃 fork **usefulness/keeper v0.19.0**（Apache-2.0、Maven Central 0.19.0、AGP 9.0→9.5.0 连续 CI 证据 PR 均 MERGED、SDK 37 支持命中本仓 compileSdk=37、L8 双机制+AGP9.1 mapping 分支经 fork 源码 gh api 实读确认）——AGP 9.3.0 兼容性获上游 CI 级强证据，本仓最终验证仍须沙盒+K2+三值预检+K6 全链（**UNVERIFIED 显式**）；勘误候选：最小接入不需编辑 settings.gradle.kts，实际触面=3 个构建文件（现均 git-clean 实证）+settings-gradle.lockfile+KD-72 锁窗口。**R-04 维持阻塞不因预研改变。**

### 3.11 T11 锁窗口（任务 23）

- 按 LP §1 原样复跑 J1–J4，逐项对照 W1–W7：**分支 B——锁窗口不可行**，与批次 29 R3 及批次 30–36 复核同判；零动作，KD-72 保持 open，13 条 git 可见未跟踪锁与盘上 14 个锁文件一律不得 git add。
- 逐项：W1 六文件入库红（5 M + ?? core/markdown/build.gradle.kts；J1b/J1c 三门 RED/MISSING）；W2 树收口红（porcelain=730 持续在动）；W3 暂存区无锁绿；W4 跟踪锁 0/盘上 14/未跟踪 13；W5 :benchmark 缺锁未变；W6 锁负控从未执行；W7 环境面实测绿（java 21.0.11、SDK android-37、build-tools 37.0.0 在；Gradle 本体未跑属纪律）。
- J4 两门 exit 0：check_dependency_pins.py（quickjs 1.0.15 / stdlib 2.3.21）、check_lockfiles.py（skip 模式）。六 build 面新声明只存在于未提交工作树侧，HEAD 侧仍是旧解析图；六文件全部在批次 38 热区 → 零改动合法。窗口打开路径：W1 等六文件随热区收口入库 → W2 等树收口 → W4/W5 由分支 A 的 A1/A2 自然覆盖 → W6=A5 → W7=A0。本线全只读。

### 3.12 T12 台账（任务 24）

- 清单 17 行（V-02/V-03/V-04/Q-02/Q-04/A-09/P-09/P-10/P-04/P-05/M-01/M-03/S-08/D-04/D-06/X-02/X-03）按批次 38 基线+三研究文档（他线未跟踪记录只转录）+批次 37 记录刷新：头部追加 v3 补记 14，17 行各加行内注记（grep 计数=17）；口径=0 项转"通过"、69 行不变、MVA 不成立维持、availableMethods 未动。
- 契约 :208：实跑 `git status --porcelain -- docs/current/` = 7 条 M → 热区未清空 → 走登记分支不落笔（:208 KD-81 快照身份边界句本轮实读仍在树）。
- KD-76/77/73 状态滚动落 known-defects.md 三块（**:2826/:2909/:2945**，本会话 grep 定位同值）：KD-73 open（台账轨复跑 `check_stdlib_compat.py` → OK: 0 unresolved … 23847 refs、EXIT=0）；KD-76 open（校准缺口 UNVERIFIED 清单复核一致、判据设计文档未落盘〔该轨时点表述；F.20 三预研件随后落盘，见 §3.10〕、`READY_TIMEOUT_MILLIS=10_000L` 现锚 :1251）；KD-77 closed 维持。

### 3.13 T13 条件轨（任务 25）

- 分支 D（热区仍脏→按既有序位复核登记）执行，零文件落盘、零 git 写、零 Gradle、零热区触碰。热区布尔实核与脚本判定一致：feature/library 43、feature/tutor 59、core/ui 35（与批次 37 §3.6 登记逐字同值）；其余 core/domain 5/core/export 6/core/markdown 1/core/data 65/settings.gradle.kts 1/feature/review 12/feature/profile 4/feature/capture 16 全脏。
- 九排队项按 ui-queue-sequencing.md §2 既有序位逐项复核：全部仍 pending、载体均在、声明热区目标文件均 M（ExplicitRedrawService.kt 保持干净）。新漂移仅一处：MASTER-LINE AUD-08 审计行锚自批次 37 登记的 :158 漂至 :159（+1），归因批次 37 自身台账滚动行（fc8d3768），性质同既往已登记漂移不重复建条。KD-68 锚 :2652 未动且 open；`ui-queue-sequencing.md:46` 仍写"schema 70（预计；当前 69）"而现树 71——批次 34 原登记仍有效。KD 台账 82 条同值，零新建。

### 3.14 集成交接 —— 无需接线（零代码改动）

- **V-02 切换与审计/账本 → 不接线（明说）**：生产接线被设计显式留待（Runner KDoc"前置依赖"；R1 :67/:75/:302 三处同口径）；生产 coordinator.generate 调用点=AttachedImageGenerator.kt:200、TutorFigureGenerator.kt:406、ConfiguredCleanImageGenerator.kt:144、RoomTutorToolRunner.kt:1191（generator.generate，coordinator.generate 在其下游 TutorFigureGenerator:406——表述更正见复核 #4）——现在接线=改他线热区文件+抢先 X-01 裁定，均越权。审计/账本半边零接线需求（runner 仅依赖 core.domain 接口；一次切换=同 operationId 第二次 generate，语义由协调器既有结构承担）。
- **M-01 artifact 消费点 → 已接线处齐备**：① `VisualContentMeasuring.kt:67-72` measure() 每次度量先产 artifact（构造即校验）、:476 toArtifact 唯一折算点；② `QuickJsVisualExecutionEngine.kt:418-421` 预算结算（M-03 同点）。其余逐项排除：端口五字段 inkBounds 增补=core/domain 热区；持久化=需 DB schema 改动（本批禁）；JS 编译器 metrics 注入=metricsJson wire 零改动；painter-PDF/math 回放=core/export 热区+回放批；缓存命中=依赖持久化。相邻登记：引擎 :417 注释"预览尺寸并入同一累计是 D-04 轨的接线点"——TutorVisualPreviewAssetPersister 确未引用 VisualLayoutBudget（grep 实证），系 T6/T7 双轨登记的留待接线点，不擅自造缝。
- **D-06 resolver 调用点 → 生产装配既有，无需接线**：resolveOne（:88-97）纯只读三态；生产装配 `AttachedImageGeneratorFactory.kt:69/:100-101` 既有（两文件 git 干净=本批未触碰）；本批增量为观测门测试（7→10）。
- **A-11 渲染载体 → 已是接线完成态**：toolLoopPromptSuffix :662-673（null 零字节追加）；Plan :307-316/Respond :407-413 两调用位传参、Lobby :500 三参不接；信标常量 :60-63 + visualContextBlock :762-773 在树。上游生产者不接是有意冻结态（`TutorVisualContext.kt:23` KDoc 明言生产入口不构造、不允许拿它广告或执行 JS；grep 全 main 无构造点）——生产者接线归 X-01。
- **零广告改动确认**：广告面三文件（FigurePromptAssembler/Capabilities/RoomModelTaskRepository）porcelain 零输出全干净；js 双就绪位 false/false 逐位不变；availableMethods 全 main 仅 FigurePromptAssembler 读取、无写入；生产三入口仍硬编码 IMAGE_MODEL_ONLY。范围注明：KD-82（预批准）在 OpenAiModelProtocol.kt 单点改动属协议 schema 数值对齐非能力广告面；`TutorToolDescriptions.kt` KNOWLEDGE_READ 文案 M 为他线在飞未触碰。
- **零 DB schema 改动确认**：STUDY_DATABASE_VERSION=71（文件 git 干净）；本批 DB 面 4 文件 diff 合计 +41、无 CREATE/ALTER/version/Migration。他线观察：`TutorConversationDatabaseContract.kt` +8/−1 增 role 派生读模面（非新表列），其 schema 效应归该线+门禁裁定。
- **import 无悬空（静态核验）**：9 个新生产文件+2 个 M 文件新增引用的符号全部定位到声明（逐文件 path:line 清单随集成工单在案；编译级归门禁）。

## 4. 设备与证据

> 环境：`emulator-5562`（test_device / API 34 / x86_64 / PAGE_SIZE 4096；5566/5584 他线禁碰、5596 API 23 机未跑本轨）。设备轨时点 HEAD=`dcfdc5b5`。零 `connectedAndroidTest`、零 git 写。串行五步全部执行并按 R4 落盘（15 个 .md，74,019 B——本会话 ls/cat 复核）。

### 4.1 产物身份（00-index §2）

| 产物 | sha256 | 用途 |
|---|---|---|
| app 主包 | `94c82daab45da2fc…828c9` | 24 套件轨（Gradle up-to-date，输入集合=现树） |
| app androidTest | `33e1d6a24c5b785a…161fa` | 24 套件轨 |
| database androidTest | `7baf8c4624e14ec6…35c1` | database 五套件 |
| data androidTest | `e86fce38d62c59b2…bcc0` | 视觉/备份/A-10 组（窗口 B，含授权修复） |

- keep 门 OK×3/EXIT=0；S=71 静态门 PASS（PLAN_HASH=JSON_HASH=`714c0ecb…`）；源快照 **1220 文件**（含 core/model/src）两构建窗口 before/after diff 全 0；四包建成（BUILD2/BUILD3 EXIT=0）+安装+设备端 sha256 逐字节一致；dex 符号门 app 19/database 21/data 30 全 OK；71.json 资产门过。
- **data APK 首建被 T2 两文件编译错误阻塞**（build.log:381-382）→ 升级授权两处最小机械修复（偏差 3）→ BUILD3_EXIT=0。

### 4.2 套件总账（E-02…E-11，00-index §3/§4）

- **24 套件 / 223 例全绿**（0 跳过、0 崩溃签名）：A-09 3、P-09 1、P-10 1、P-04/P-05 1、隔离 15、flags 2、V-01 app 3、结构门 5、迁移 15、69→70 4、70→71 3、提交 12、备份 34、rowCounts 2、裁剪器 9、C4 8（含 D-04 三新例）、仓库 12、parity 16、painter 24、PDF 16、本地序号 1、对账 6、引擎 27、A-10 3（含跨 method 计数不重置位+终答位子门）——设备 OK(N) 与构建前 grep 计数逐一相符；唯一计数差 C4 5→8 为本批 D-04 落点，按构建前复读为准执行并全绿。
- **首跑红例 3 条**（A-09 正例、隔离 P-12③、隔离冷启动）同签名=服务端 L2 看门狗 1500ms 超限杀 worker，归因 **D 环境**（4 模拟器高负载窗突发停顿；非确定性分布、纯几何程序也红、同 APK 整套装件复跑全绿 step21b OK(3)/step14b OK(15)），红例原文逐条留痕（step12 §5）。
- **步骤五（E-12）**：26 窗九类崩溃签名全 0、warm-up failed 全 0、worker spawn/died/kill 逐窗对账成立，无进程放大。
- **步骤四（E-13）**：KD-76 校准按"若判据就绪"条件判定**不成立未执行**（计时 seam `connectStageTimestampsForTest` 引擎+测试两侧 0 命中；零代码近似被预研自判精度不足；无低 RAM 档）——登记 UNVERIFIED，KD-76 维持 open。
- **未执行项（索引 §5 如实登记）**：V-03/V-04 三个新套件已编译进 dex 但运行归该轨判据未执行；V-01 降级变体未修复（singleOrNull 夹具形态仍在 `core/data/…/V01VisualChainInstrumentedTest.kt:238/:563/:595`，2/4 已知红）→ 按判据不进套件单；arm64/16KB/真机口径维持既有缺口。
- 原始全量日志在 `build/device-track-batch38/`（67 文件，不入仓；本会话 ls 同值）。

## 5. 复核发现与处置

### 5.0 修复环两轮（工单给定）

- **第 1 轮**：逐条处置（第 2 轮工单自述"与第 1 轮同文"——两条复核发现重复上报）。
- **第 2 轮（本轮实读现树复核）**：
  - **发现 1（M-03×V-02 交互未登记，中）——已处置**：结构性事实不变（引擎接线 `QuickJsVisualExecutionEngine.kt:418-421`、切换集含 INVALID_PROGRAM）；"两处 KDoc 均未登记"**现已不成立**——`FigureMethodSwitchRunner.kt`（§2.4 表 M-03×V-02 行 + companion 指向句）与 `VisualLayoutBudget.kt:31-36`（执行点交互登记段）grep 实测在场（**本会话 sed 实读复验同值**）；行为未改，裁定开放归主会话（处置在"切换或登记"两分支中执行了**登记分支**）；**残余缺口**："无 LAYOUT_PIXEL_BUDGET 场景用例"仍真（grep exit 1、@Test=7）——未直接补测试（本轨不跑 Gradle，无法验证的新测试有再破构建风险，且行为待裁定不预钉），改为把缺口**登记进台账 V-02 行**（清单 v3 补记 15 在树）。
  - **发现 2（台账清单行时点混杂，中）——处置申报原文被工单截断**（仅存"## 发"开头，处置正文未随工单传入）；**落盘证据在树**：准入清单头部 **v3 补记 15（批次 38 复核修复轮 G.24 回填，2026-11-01）**已在（本会话 Read 实读），刷新 S-08/M-01/M-03/D-04/A-09/Q-02/Q-04/V-02/X-02/X-03 十行 + M-03×V-02 交互登记 + V-02 行 LAYOUT_PIXEL_BUDGET 用例缺口登记——处置动作的事实载体可独立验证，截断本身如实登记。
- **门禁序列（工单给定）**：单测门第 3 轮 exit=1 → 修复环第 2 轮 → 强门（修复环后）exit=0 → 单测门复跑 exit=1 → 修复环后强门复跑 exit=0。

### 5.1 复核轨发现（27 条；工单"已独立复核"标注原样转录）

| # | 级别 | 位置 | 发现（压缩转录） | 状态 |
|---|---|---|---|---|
| 1 | 低 | VisualSnapshotRestoreReadSideTest | T2 摘要两处写"JVM 11 例"，实测 12 个 @Test 欠报 1；T12 台账"Restore 12"与实测一致未受染 | 已独立复核 |
| 2 | 低 | VisualFigureFixtureOracleTest | T3 摘要写"8 例"，实测 10 个 @Test 欠报 2；构成列举与实际测试名逐条对得上，仅总数算错 | 已独立复核 |
| 3 | 低 | VisualJsAdvertiseThenExecuteInstrumentedTest | T4 摘要写"712 行"，实测 711（换行计数差）；@Test=3 一致 | 已独立复核 |
| 4 | 低 | 集成摘要 | RoomTutorToolRunner.kt:1191 实为 generator.generate（TutorFigureGenerator 入口），coordinator.generate 在下游 :406；调用面结论不受影响 | 已独立复核 |
| 5 | 低 | 两设备测试文件 | 设备轨授权机械修复属跨轨写入他轨产出文件——已登记非越权；主会话提交 T2 文件时应知悉含修复行 | 已独立复核 |
| 6 | 低 | 证据目录/设备全绿 | 24 套件 223 例全绿结论无法在本轨独立复跑（禁 Gradle/无设备授权）维持 UNVERIFIED；静态面已核（15 .md/107 行/74,019 B/计数表） | **未复核** |
| 7 | 低 | FigureMethodSwitchRunner/Test | V-02 三项重点实测通过：无双重载荷（例 7）、预算不破坏账本（例 3/4/6）、判据 fail-closed（例 2）；' JS ' token 判别有真实分辨力 | 已独立复核 |
| 8 | 低 | FigureGenerationPolicy/ConfiguredCleanImageGenerator | Q-02 落地一致：有效位=宿主位∨PIXEL_CLEANUP、两道门同收、入口归位；无既有 PIXEL_CLEANUP×JS 用例翻红成立 | 已独立复核 |
| 9 | 低 | q04 fixtures + compile_fixtures.mjs | 产物齐全、两道 Node 门实跑复现（94 passed / 5/5 byte-identical）；oracle 独立性抽查成立 | 已独立复核 |
| 10 | 低 | AttachedImageGenerator + 观测测试 | D-06 不挡合法出网：resolveOne 纯只读三态、默认参数注入既有 7 例零改动；10 @Test=7+3 离线臂双观测位 | 已独立复核 |
| 11 | 低 | VisualSourceBasis 等 7 文件 | X-02/X-03 落树一致；与 T3 同文件 hunk 共存（归属划分登记基础成立） | 已独立复核 |
| 12 | 低 | OpenAiModelProtocol/TaskAdapters + 4 测试 | KD-82 maxLength=/4 单点、A-11 双调用位+Lobby 不接、测试计数 7/6/46/21 逐一相符 | 已独立复核 |
| 13 | 低 | T11/T13 只读结论 | 锁窗口分支 B 与条件轨三点复核成立；porcelain 持续滚动结论方向不变 | 已独立复核 |
| 14 | **中** | QuickJsVisualExecutionEngine:418-421 × FigureMethodSwitchRunner:151-158 | M-03×V-02 跨特性交互：4MP 超限折 INVALID_PROGRAM 落入可归属切换集→自动 JS→IMAGE 切换+远程预留（语义上更接近排除项 INPUT_OVER_BUDGET 的"输入侧"）；有界性由 6 上限管总量；建议主会话裁定 | 已独立复核 → **处置见 §5.0（登记分支）** |
| 15 | **中** | 准入清单补记 14 多行 | 台账清单行时点混杂：补记 14 多行"未落/0 命中维持"为开工基线时点，落码后未刷新——方向保守非虚报，但同一次补记内行间时点不一致，收口前必须统一清算 | 已独立复核 → **处置见 §5.0（v3 补记 15 在树）** |
| 16 | 低 | VisualLayoutBudget:34/:109 | M-03 的 16MiB 单 bitmap 门无生产执行点（requireBitmapBytesWithinBudget 仅定义+测试引用；createBitmap 生产命中均无该门前置）；如实口径="4MP 已接线、16MiB 纯门待预览分配点落地" | 已独立复核 |
| 17 | 低 | TutorVisualPreviewAssetPersister:31/:68 | D-04 写侧无生产调用方（main 零调用点、previewAssetId 恒 null）；台账升级"已落"须限定"写侧/读侧已落、生产装配未接线" | 已独立复核 |
| 18 | 低 | known-defects KD-82 | KD-82 残余半边本批落码但台账无批次 38 Progress 块——台账滞后于落码，收口时需登记（**本记录员已落，§附录 A**） | 已独立复核 |
| 19 | 低 | FigureMethodSwitchRunner:88-91 | V-02 编排层无生产调用方（X-01 待裁）——KDoc 已如实登记，非虚报 | 已独立复核 |
| 20 | 低 | SmartMistakeBookApplication:364-368 + A-09 套件 | 【通过项】A-09 变相接通广告未发现：快照/广告零 diff、生产装配恒三参、测试装配注入+两负控自带 | 已独立复核 |
| 21 | 低 | FigureMethodSwitchTest + Runner | 【通过项】V-02 验收行静态实证：同 request 实例、判据锚点同源、账本计数续接、唯一产物、explicitUserRetry 不借道 | 已独立复核 |
| 22 | 低 | FigureGenerationPolicy + CleanImageGenerator | 【通过项】Q-02 与预裁一致；5 向量测试含旧漏洞形状正钉 | 已独立复核 |
| 23 | 低 | Coordinator:547-558 + VisualSourceBasis + 观测测试 | 【通过项】X-02 语义边界成立：三门先于预留、seen 仅真实出网后产出、basis=null 旧调用零变化 | 已独立复核 |
| 24 | 低 | VisualContentMeasuring + MathContentLowering + 两 DTO | 【通过项】度量 artifact 不破坏既有消费者：contentId/digest 形状不变、wire 五字段不变、readable==原文透传、单一共享 lowering 入口 | 已独立复核 |
| 25 | 低 | OpenAiModelProtocol:548-565 + AttachedImageGenerator | 【通过项】cache-only 不挡合法刷新（显式再生成独立入口）；KD-82 1024=⌊4096/4⌋ 派生正确且为收紧 | 已独立复核 |
| 26 | 低 | known-defects + 00-index + check_stdlib_compat.py | 【通过项】可实测台账声称逐值复核一致（KD-73 门 23847/329/0 EXIT=0 实跑吻合、KD-76 open+JS 不广告与生产装配一致、机械修复纯机械零断言改动、record 未落笔为索引自declared） | 已独立复核 |
| 27 | 低 | 总体口径 | 未验证项如实登记：本轨未运行 Gradle/JVM/设备；Q-04 oracle 学科重算未逐字段复算 | 已独立复核 |

## 6. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 3 轮 / 复跑） | **exit=1 ×2**（工单给定） | **失败构成未随工单传入**。记录员当日 XML 普查（`python -I` 解析 2026-11-01 mtime 全部 JUnit XML，core/app 四模块）：133 套件中**唯一红 = `FusedRecallSupersetTest`（core/data knowledge，06:57）tests=3 failures=2**——KB 线域、非批次 38 足迹；exit=1 的确切构成归因 UNVERIFIED。批次 38 core/data 侧 11 个新/扩 JVM 套件无当日 XML |
| 强门（修复环后 ×2） | **exit=0 ×2**（工单给定） | 记录员未复跑（禁 Gradle）；强门绿不替代单测门绿 |
| 修复环第 2 轮 | 处置在树 | §5.0；发现 1 登记在场（KDoc 双侧，本会话 sed 实读）+ 缺口登记进台账 V-02 行（补记 15）；发现 2 处置申报截断、落盘证据（补记 15）在树 |
| 批次 38 新 JVM（XML 读值） | **7 套件全绿** | core/model（03:46）：FigureGenerationPixelCleanupMappingTest **5/0**、MathContentLoweringTest **8/0**、VisualSourceBasisTest **17/0**、ModelTaskFingerprintStabilityTest **46/0**；app（06:41）：VisualContentMeasuringTest **16/0**、VisualMeasuredLayoutInkBoundsTest **9/0**、VisualMeasureEnvironmentBudgetTest **5/0**——执行=门禁/构建轮，记录员解析 XML 未复跑 Gradle |
| 批次 38 core/data JVM（11 套件） | **UNVERIFIED** | FigureMethodSwitchTest 7 / VisualFigureFixtureOracleTest 10 / VisualSourceSeenDispatchObservationTest 7 / StrictOfflineVisualRelayoutTest 9 / VisualLayoutBudgetTest 13 / VisualMeasuredLayoutArtifactTest 9 / TutorVisualPreviewAssetPersisterTest 3 / FigureHttpTransportBypassObservationTest 10 / FigureGenerationLocalBudgetBoundaryTest 6 / ProgramSchemaMaxLengthAlignmentTest 7 / ModelWireProtocolSourceJsFidelityTest 21——@Test 计数本会话 grep -c 实测同值，但无当日执行 XML |
| 设备 24 套件 | **223 例全绿**（索引 §4；复核轨未复核） | §4.2；首跑 3 红归因 D 环境被同 APK 绿复跑取代；0 跳过 0 崩溃签名 |
| keep 规则门 / S=71 门 | OK×3 EXIT=0 / PASS | 设备轨 + 索引 §8 复核两轮实跑 |
| 源快照绑定 | 两窗口 diff 全 0，1220 文件 | `src-sha-*`、索引 §8 #9 复算 |
| dex 符号门 / 资产门 | app 19 / database 21 / data 30 全 OK / 71.json 过 | 索引 §8 #10 |
| KD-73 stdlib 门 | OK，EXIT=0 | T12 复跑：23847 refs / 329 构件 / 0 unresolved |
| KD-76 校准 | UNVERIFIED（条件不成立未执行） | E-13；计时 seam 0 命中；KD-76 维持 open |
| 生产签名 / arm64 / 16KB / 真机 | UNVERIFIED | 维持既有缺口 |

## 7. 遗留 / UNVERIFIED / 待裁

**门禁与执行面**

1. **单测门 exit=1 ×2 未归因（最高优先收口项）**：失败清单未随工单传入；当日 XML 普查唯一红=FusedRecallSupersetTest（KB 线域）为线索非定论；批次 38 core/data 11 个 JVM 套件无出数。收口前主会话须取得逐套件失败清单并复跑至 exit=0（或明示已知红口径）。
2. **V-03/V-04 三设备套件未执行**（Race 3/Rebuild 3/Retention 2，已编译进 dex）——语义断言面（删会话保留题图、租约、迟到结果、重建续读等）全部 UNVERIFIED；执行归该轨判据与套件单。
3. **V-01 降级变体 2/4 已知红未修复**（singleOrNull 形态仍在）；其 3 行级按键取修复待主会话裁定（批次 37 遗留口径不变）。
4. **M-03 16MiB 分配前置门无生产执行点**（纯函数+测试钉子；接线归预览分配点落地）；**D-04 写侧无生产调用方**（previewAssetId 恒 null，快照提交接线归后续批）——台账升级"已落"须带限定语。
5. **A-10 协调器级"IMAGE+JS 混合合计第 3 份拒绝"向量**仍无直接协调器级测试（JVM FigureMethodSwitchTest + E-11 设备位共同守卫）。

**待裁（归主会话）**

6. **M-03×V-02 交互行为裁定**：LAYOUT_PIXEL_BUDGET 越界折 INVALID_PROGRAM 落入可归属切换集（现行为=切换+远程预留、6 上限不重置）；"程序侧确定性超限 vs 输入侧越量"开放；X-01 生产接线前裁定；专项用例随裁定同批落（不预钉未裁行为）。
7. **V-02 生产接线（X-01 归位）**：runner 无生产调用方；四个生产 coordinator.generate 调用点中 RoomTutorToolRunner 为他线热区 M——归位批裁定接线点。
8. **R-04 维持阻塞**：Keeper 解锁路径已预研（usefulness/keeper v0.19.0 fork，AGP 9 CI 级证据；本仓验证 UNVERIFIED）；实际触面勘误候选（3 构建文件+lockfile+KD-72 窗口，不需动 settings.gradle.kts）归主会话采纳与否。
9. **KD-82 open 维持**：imagePrompt 半边 Fix direction ① 已落码（1024 码点）+ 测试改钉，但 JVM 出数缺当日 XML、关闭裁决（广告解冻批另一半）未发生；最坏 4× 过度广告已消除，"不得把 schema 写成与本地门同一口径"禁令继续有效（1024 码点仍非字节口径）。
10. **KD-72 open（分支 B）**；**KD-73 open**（设备/R8 半边未做）；**KD-76 open**（校准 UNVERIFIED，seam 落码+低 RAM 档为前置）。
11. **契约 `:208` 过时句**：docs/current 7 M 热区未清空 → 登记不落笔（补记 14/15 沿登记分支）。
12. **修复环第 2 轮发现 2 处置申报原文截断**（仅"## 发"）；落盘证据（补记 15）在树可验，申报与落盘的差异留档。
13. **生产签名 / arm64 真机 / 16KB release / API 23 全套件**维持 UNVERIFIED。

**口径**

14. **4E.2 MVA 仍不成立**：0 项转"通过"、69 行不变；本批落的是载体/门/观测位，双后端就绪、质量门、JS 广告均未宣称。
15. **工作树计数滚动**（730→746→748→786→789 各时点读值）差值未逐项追认；他线在飞内容功能正确性归各线。
16. **设备轨对 T2 两文件的授权机械修复**随文件提交（块 2 注明）。

## 8. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前按门禁序列复跑单测门/强门并留日志（单测门当前 exit=1 未归因，见 §7.1）；`git diff --cached --name-only` 对照本清单，申报外路径单独 reset；共享脏文件整文件纳入并在提交说明注明本批与他线来源（`FigureGenerationPolicy.kt` 归属按 Q-02/X-02/他线各自 KDoc 划分）。按 ≤6 路径/块：

- **块 1（V-02 切换）**：`core/data/src/main/.../visual/FigureMethodSwitchRunner.kt`（??）、`core/data/src/test/.../visual/FigureMethodSwitchTest.kt`（??）
- **块 2（V-03/V-04 实现缝+设备套件）**：`core/database/src/main/.../port/TutorVisualPort.kt`（M）、`core/database/src/main/.../RoomStudyDatabase.kt`（M；**整文件纳入**——S1 端口查询）、`core/data/src/main/.../visual/VisualSnapshotRestoreReadSide.kt`（??）、`core/data/src/test/.../visual/VisualSnapshotRestoreReadSideTest.kt`（??）、`core/data/src/androidTest/.../visual/TutorVisualRebuildRestoreInstrumentedTest.kt`（??，含设备轨授权修复行）、`core/data/src/androidTest/.../visual/TutorVisualCommitRaceInstrumentedTest.kt`（??，含设备轨授权修复行）
- **块 3（V-03 Retention + D-04 写侧）**：`core/database/src/androidTest/.../TutorVisualSavedProblemRetentionInstrumentedTest.kt`（??）、`core/data/src/main/.../visual/TutorVisualPreviewAssetPersister.kt`（??）、`core/data/src/test/.../visual/TutorVisualPreviewAssetPersisterTest.kt`（??）、`core/data/src/androidTest/.../backup/BackupVisualReferenceInstrumentedTest.kt`（M；**整文件纳入**——5→8 追加）
- **块 4（Q-02/Q-04）**：`core/model/src/main/.../FigureGenerationPolicy.kt`（M；**整文件纳入**——Q-02 三 hunk+X-02 源证据门+他线在飞 hunk 共存，归属按 KDoc）、`core/data/src/main/.../capture/ConfiguredCleanImageGenerator.kt`（M）、`core/model/src/test/.../FigureGenerationPixelCleanupMappingTest.kt`（??）、`core/data/src/test/.../visual/VisualFigureFixtureOracleTest.kt`（??）、`tools/visual_compiler/compile_fixtures.mjs`（??）
- **块 5（Q-04 语料）**：`core/data/src/test/resources/visual/fixtures/q04/`（?? 目录，16 文件）
- **块 6（M-01/M-03/S-08 core 侧）**：`core/data/src/main/.../visual/VisualMeasuredLayoutArtifact.kt`（??）、`core/data/src/main/.../visual/VisualLayoutBudget.kt`（??）、`core/data/src/test/.../visual/VisualLayoutBudgetTest.kt`（??）、`core/data/src/test/.../visual/VisualMeasuredLayoutArtifactTest.kt`（??）、`core/model/src/main/.../MathContentLowering.kt`（??）、`core/model/src/test/.../MathContentLoweringTest.kt`（??）
- **块 7（M-01 app 侧+引擎接线）**：`core/data/src/main/.../visual/QuickJsVisualExecutionEngine.kt`（M；**整文件纳入**——+12 预算结算接线）、`app/src/main/.../visualjs/VisualContentMeasuring.kt`（M）、`app/src/test/.../visualjs/VisualContentMeasuringTest.kt`（M）、`app/src/test/.../visualjs/VisualMeasuredLayoutInkBoundsTest.kt`（??）、`app/src/test/.../visualjs/VisualMeasureEnvironmentBudgetTest.kt`（??）
- **块 8（X-02/X-03）**：`core/model/src/main/.../VisualSourceBasis.kt`（??）、`core/model/src/main/.../FigureGeneration.kt`（M；**整文件纳入**）、`core/data/src/main/.../visual/DefaultFigureGenerationCoordinator.kt`（M；**整文件纳入**——seen 产出点）、`core/data/src/main/.../visual/StrictOfflineVisualRelayout.kt`（??）、`core/model/src/test/.../VisualSourceBasisTest.kt`（??）、`core/data/src/test/.../visual/VisualSourceSeenDispatchObservationTest.kt`（??）
- **块 9（X-03 测试 + A-10 扩测）**：`core/data/src/test/.../visual/StrictOfflineVisualRelayoutTest.kt`（??）、`core/data/src/test/.../visual/FigureGenerationLocalBudgetBoundaryTest.kt`（??，批次 36 遗留未入仓件 5→6）
- **块 10（E 协议与预算）**：`core/data/src/main/.../model/OpenAiModelProtocol.kt`（M；**整文件纳入**——KD-82 imagePrompt 单点）、`core/data/src/main/.../model/OpenAiModelTaskAdapters.kt`（M；**整文件纳入**——A-11 载体）、`core/data/src/test/.../model/ProgramSchemaMaxLengthAlignmentTest.kt`（M，4→7）、`core/data/src/test/.../model/wire/ModelWireProtocolSourceJsFidelityTest.kt`（M，15→21）、`core/model/src/test/.../ModelTaskFingerprintStabilityTest.kt`（M，42→46）、`core/data/src/test/.../visual/FigureHttpTransportBypassObservationTest.kt`（M；**整文件纳入**——D-06 7→10）
- **块 11（A-09+隔离设备负向）**：`app/src/androidTest/.../visualjs/VisualJsAdvertiseThenExecuteInstrumentedTest.kt`（??）、`app/src/androidTest/.../visualjs/VisualJsClientFrameQueueDeviceInstrumentedTest.kt`（??）、`app/src/androidTest/.../visualjs/VisualJsDeathUnconfirmedFailClosedInstrumentedTest.kt`（??）、`app/src/androidTest/.../visualjs/VisualJsReconnectBudgetInstrumentedTest.kt`（??）
- **块 12（研究+预研）**：`docs/research/2026-11-01-method-switching-and-transactions.md`、`docs/research/2026-11-01-measure-artifact-and-budget.md`、`docs/research/2026-11-01-preview-source-evidence.md`、`docs/research/2026-11-01-kd76-ready-timeout-calibration.md`、`docs/research/2026-11-01-v01-pdf-leg-exposure-design.md`、`docs/research/2026-11-01-r04-keeper-prerequest.md`（均 ??）
- **块 13（基线+证据）**：`docs/research/2026-11-01-batch-38-baseline.md`（??）、`docs/research/evidence/2026-11-01-batch-38/`（15 个 .md）
- **块 14（记录+台账，整文件纳入）**：`docs/research/2026-11-01-batch-38-record.md`（本文件，新）、`docs/known-defects.md`（M；KD-73/76/77 批次 38 块 + KD-82 批次 38 块）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（M；v3 补记 14/15/16）、`docs/REFACTOR-MASTER-LINE.md`（M；批次 38 滚动行）、`docs/agent-first-refactor-decisions-2026-09-23.md`（M；批次 38 节）

**明确排除（他线在飞/不安全）**：`settings.gradle.kts`、`.gitignore`；`feature/**`、`core/ui`、`core/domain`、`core/markdown`、`core/export`；core/data 他线 M（dense/knowledge 面〔含 FusedRecallSupersetTest 当日红例域〕、`RoomMistakeDetailRepository.kt`、`RoomTutorToolRunner.kt`、SafeTruncation 面、`TutorConversationDatabaseContract.kt`、`TutorToolDescriptions.kt` 等）；core/database 其余 M；app 其余 M（Root、ReviewReminder*、SecondaryScreens、ExportPdfWorker、StartupStateBanner、SmartMistakeBookDestinations 等）；`docs/current/**`；docs/research 他线未跟踪记录；`build/**`（含设备原始日志）；`tmp/**`；全部 `*.lockfile`；`tools/kb*`、`tools/dense_build`、`tools/kb_coverage`、`knowledge-production/**`。

## 9. 收口状态

- **研究**：R1 330 行（候选 B 调用侧编排/方向不对称/切换判据 fail-closed/8 用例设计/外部评分）；R2 293 行（artifact 语义+inkBounds 口径/4MP·16MiB 两门独立/N1–N9/MathContentLowering 设计/jlatexmath GPL 否决）；R3 160 行（四轨源证据闭合/GitHub 参考评分/无需引第三方依赖）；三预研 333 行（KD-76 判据/V-01④ 方案 A/Keeper fork 可行性）。未验证项如实。
- **实施**：13 轨落码（V-02 runner+7 例、V-03/V-04 两缝+5 文件、Q-02 有效位+Q-04 fixtures、A-09 3 例、P-09/10/04-05 三套件、M-01/M-03/S-08、D-04 常量+写口、D-06 观测 10 例、X-02 源证据、X-03 判定门、A-11/A-12/A-10/KD-82 imagePrompt、三预研、锁窗口分支 B、台账补记 14/15/16、条件轨分支 D）；集成零接线。
- **设备**：24 套件 223 例全绿（emulator-5562；首跑 3 红归因 D 环境同 APK 复跑取代）；源快照 1220 文件两窗口 diff 0；keep 门/S=71/dex 门/资产门全过；KD-76 校准条件不成立未执行；V-03/V-04 套件编译未跑、V-01 降级变体未跑（如实登记）。
- **门禁**：强门 exit=0 ×2（工单给定）；**单测门 exit=1 ×2 未归因（本记录最高优先遗留）**；批次 38 新 JVM 中 7 套件有当日 XML 全绿读值（记录员解析）、core/data 11 套件无 XML UNVERIFIED。
- **台账同步（本轮记录员写入，§附录 A）**：`docs/known-defects.md`（KD-82 批次 38 块）、`docs/REFACTOR-MASTER-LINE.md`（批次 38 滚动行）、准入清单（v3 补记 16）、`docs/agent-first-refactor-decisions-2026-09-23.md`（批次 38 节）。
- **本批不声称全绿/全就绪**：单测门未闭合；V-03/V-04 设备断言未出数；R-04 阻塞未解除；KD-72/73/76/82 open；JS 仍 UNAVAILABLE 不广告（availableMethods 未动、生产装配恒三参）；4E.2 MVA 不成立；生产签名/arm64 UNVERIFIED。

## 10. 本记录的验证结果（记录员实跑/实读，写盘前执行）

```bash
cd "D:/smart mistake book"
git rev-parse HEAD                                   # 147c4b9feb8d8830c695ab216f3ae14b04d4fdc0
git status --porcelain | wc -l                       # 746（232 M + 514 ??，多线在飞滚动）

# 三研究/预研/基线（wc -l）：R1=330 / R2=293 / R3=160 / F20=134 / F21=112 / F22=87 / baseline=116
# 证据目录：ls = 15 个 .md；00-index.md = 107 行；cat *.md | wc -c = 74019
# 设备原始产物：build/device-track-batch38/ = 67 文件（不入仓）

# 各轨申报文件 git 状态逐一核对（git status --porcelain -- <50 路径>）：
#   18 M + 31 ??（含 q04 目录）；与各轨申报一致

# @Test 计数（grep -c '@Test'）：MethodSwitch=7 / RestoreReadSide=12 / FixtureOracle=10 /
#   PixelCleanup=5 / MathLowering=8 / SourceBasis=17 / Fingerprint=46 / SourceSeen=7 /
#   StrictOffline=9 / LayoutBudget=13 / MeasuredArtifact=9 / PreviewPersister=3 / HttpBypass=10 /
#   LocalBudget=6 / MaxLength=7 / WireSourceJs=21 / ContentMeasuring=16 / InkBounds=9 / EnvBudget=5
# 设备套件行数：A-09=711 / P-09=279 / P-10=285 / P-0405=264 / Rebuild=426 / Race=322 / Retention=388

# 2026-11-01 当日 JUnit XML 普查（python -I 解析 core/app 四模块 test-results，133 套件）：
#   唯一红 = FusedRecallSupersetTest tests=3 failures=2（core/data knowledge，06:57，KB 线域）
#   批次 38 绿读值：PixelCleanup 5/0、MathContentLowering 8/0、SourceBasis 17/0、Fingerprint 46/0（03:46）；
#     ContentMeasuring 16/0、InkBounds 9/0、EnvBudget 5/0（06:41）
#   批次 38 core/data 侧 11 套件无当日 XML（当日 core/data 仅 2 件 knowledge XML）

# 锚点实读（sed/grep）：
#   FigureMethodSwitchRunner.kt 类 KDoc M-03×V-02 行 + companion 指向句（:150-154）+ :88-91 前置依赖节
#   VisualLayoutBudget.kt :31-36 交互登记段；OpenAiModelProtocol.kt:561 .MAX_IMAGE_PROMPT_UTF8_BYTES / 4
#   ConfiguredCleanImageGenerator.kt:109 requiresPixelFidelity = true
#   VisualContentMeasuring.kt:404 MathContentLowering.lower；StudyDatabase.kt:139 STUDY_DATABASE_VERSION = 71
#   known-defects.md 批次 38 块 :2826/:2909/:2945；准入清单 v3 补记 15 在树；docs/current porcelain = 7 M

# T3 Node 门（94 passed / 5/5 byte-identical）= 复核轨会话实跑（复核发现 #9），记录员转录未重跑

# 记录落盘复核：ls -l + wc -l docs/research/2026-11-01-batch-38-record.md → 390 行 / 79,097 B / ?? 未跟踪
# 台账同步后 git status：known-defects.md、REFACTOR-MASTER-LINE.md、准入清单、agent-first-decisions 均 M（后两件开工时干净、本轮改动即批次 38 行/节）；git diff --cached --name-only = 0（暂存区空，零 git 写）

# 未执行：任何 ./gradlew、adb install / am instrument、connectedAndroidTest；
#         任何 commit/push/add/reset；任何设备写；任何产品代码改动
```

## 附录 A：台账同步（本轮记录员写入）

| 文件 | 写入内容 |
|---|---|
| `docs/research/2026-11-01-batch-38-record.md` | 本文件（00-index §7/§8 登记的记录员落笔位在此收口；§4 全部以 E-ID + path:line 回链） |
| `docs/known-defects.md` | KD-82 条目追加"批次 38 记录员收口"Progress 块（imagePrompt 残余半边 Fix direction ① 落码登记 +1024 码点口径 + JVM 出数 UNVERIFIED〔无当日 XML〕+ 状态 open 维持 + 禁令口径更新）。**该文件为共享脏文件（开工时已 M：T12 的 KD-73/76/77 批次 38 块在飞），整文件纳入并注明**；本轨只追加不重写既有块 |
| `docs/REFACTOR-MASTER-LINE.md` | 新增"最近计划维护：2026-11-01（批次 38 …）"滚动行；原批次 37 行降为"上次维护"。开工时该文件 git 干净，本轮改动即本行 |
| `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | 头部追加 **v3 补记 16（批次 38 记录员收口，2026-11-01）**：P-09/P-10/P-04 三行设备证据刷新（E-03/E-04/E-05，补记 14"设备面 UNVERIFIED 维持（本批零设备）"已过时）、V-03/V-04 两行载体落码注记（三套件在树 2+3+3 例、设备执行未发生、JVM RestoreReadSide 12 例）、D-06 观测门现树 10 @Test（含离线打开三臂；补记 14/15 时点 7 例为当时读值）+ JVM 出数 UNVERIFIED、A-10 行 E-11 复跑 3/3 注记；**该文件为共享脏文件（开工时已 M：T12 补记 14 + 复核修复轮补记 15 在飞），整文件纳入并注明**；0 项转"通过"、69 行不变、MVA 不成立维持、availableMethods 未动 |
| `docs/agent-first-refactor-decisions-2026-09-23.md` | 追加"批次 38 三倍任务量批——Q-02 预裁落地 + V-02 调用侧编排与 M-03×V-02 开放裁定点 + KD-82 imagePrompt 半边 + R-04 Keeper 预研（2026-11-01）"节（裁定内容 + 边界/待裁） |

以上均为 docs 追加/刷新；零 Kotlin/JS/Gradle 产品代码改动、零 git 写、零 Gradle、零设备写。

## 10. 主会话收口（2026-11-01，提交与推送执行者实跑）

- **单测门复核**：工作流报"第 3 轮 exit=1"为时点态（V-02 测试交接后仍在改）；主会话实跑 11 模块 `--continue` → **`UNIT_EXIT=0`、`BUILD SUCCESSFUL in 2m 41s`**，FigureMethodSwitchTest 7/0、VisualSnapshotRestoreReadSideTest **12/0**（T2 申报 11 为欠报，台账已记实值）、VisualFigureFixtureOracleTest **10/0**（申报 8，欠报 2）。
- **M-03×V-02 跨特性裁定（复核【中】发现的处置）**：`LAYOUT_PIXEL_BUDGET`（4MP 排版预算越界）**不触发 JS→IMAGE 切换**——资源侧限制不烧付费派发（与排除面 INPUT_OVER_BUDGET 同族）；`FigureMethodSwitchRunner.isAttributableJsFailure` 加令牌排除 + 用例 8（8/8 绿，`--rerun-tasks` 实跑），KDoc 裁定点已裁。
- **台账回填**：KD-82 批次 38 块（imagePrompt 1024 对齐，关闭待门禁+广告批）；清单 v3 补记 15（17 行现树状态一次清算，0 项转通过、69 行不变）。
- **双门**：单测门 exit=0；强门待本轮修复后复跑（见下）。
- **R-04/KD-76/V-01④**：维持批次 37 裁定（Keeper 推迟热区、校准判据已备、④ 腿方案归批次 39）。
