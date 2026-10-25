# 批次 20 记录（2026-10-25）：4E 收官（WG-02/03/10 + JS 地基）+ 五研究线 + beta 准备件

> 记录时点：2026-10-25（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对计划、基线、五研究文档、源码与 git 状态后，按工单给定材料（十轨交付、门禁、设备 evidence、修复环第 2 轮处置、复核发现）写入，并同步主线/决策台账/KD/PRD 计划/算法台账五处文档。
> **本记录员未运行 Gradle、未运行 adb/am instrument、未改任何代码、未执行任何 git 写操作、未提交/推送。** 文中门禁、设备与"测试全绿"结论来自工单给定材料（主会话/各轨/设备轨实跑后转录），记录员自跑的只有只读命令（见附录）。凡无当前执行证据的验收一律标 **UNVERIFIED**；设备轨数字在本文落盘前不可审计（基线 §8），本文 §3.12 即落盘载体。
> 计划：`.zcode/plans/plan-2026-10-25-batch-20-4e-js-beta-prep.md`（下称"计划"）。
> 基线：`docs/research/2026-10-25-batch-20-baseline.md`（下称"基线"，含 §8 复核修复环补记）。
> **门禁状态（工单给定）**：单测门第 3 轮 `exit=1`；修复环复跑 `exit=0`（材料未注明运行范围）；**强门未跑（单测门未过）**；修复环第 2 轮处置完成。本记录**不声称"批次全绿"**；提交建议附前置条件（§4/§7）。

## 1. 范围与基线

### 1.1 计划范围

- **做**（计划 `:22`）：五条外部研究（R1 JS 引擎选型 / R2 崩溃收集与反馈闭环 / R3 AI 内容标识合规 / R4 16KB·arm64·API23 构建面 / R5 G5 自适应出题先例）；4E WG-10 旁路迁移、WG-02 转写与源图证据、WG-03 保存净化与后台续跑；QuickJS 运行库接入（沙箱+硬终止+内存上限+16KB，**方法仍不广告**）；PRD-09 生图评测 harness（盲测冻结+预算清单+两后端配对框架，fake 驱动先行）；beta 反馈闭环（本地优先零外部服务）；AI 生成内容显式标识；16KB/arm64 构建配置与 .so 对齐核查；AUD-17 样本台账只读查询面；3E 排计划设备仪器化补缺；门禁/强门/设备/双复核/记录/提交/自动推送。
- **不做**（计划 `:24`）：G5 实现（只研究+提案，API 口径待用户裁）；4E.2 交互图/4E.3 保真升级（本批只落 JS 运行库地基）；真实 Provider 调用（无 key）；feature/tutor、feature/library、feature/review、feature/capture、core/ui、Root 任何改动；WG-09（随 5B 契约）；KB 线（他线）；Wave X 启用。

### 1.2 已裁边界（本批执行，不得改）

1. JS 方法本批**不广告**：工具面/提示词/能力快照仍只声明 IMAGE_MODEL；JS 仍 UNAVAILABLE 直到 4E.2 的门；QuickJS 只落运行库与执行端口。
2. G5 只研究+提案（R5），不实现；API 口径呈用户裁定。
3. PRD-09 评测：盲测任务与开发集分离且先落盘；评审来源字段必填；不准面向结果改夹具；真实 Provider 无 key，一切真实质量 UNVERIFIED。
4. 反馈闭环零外部服务、零新增隐私面（本地优先）。
5. 生成图/清图不冒充学生证据；WG-03 移除 TEXT_ONLY 捷径后 NO_FIGURE 只记终态不伪报已产图。
6. 真实 Provider 调用禁止（无凭据）。

### 1.3 基线摘要（引自基线文档）

- **HEAD 前移一档**：实测 `558185c9`（= origin/main，kb 批次 19 A1 别名臂），计划 §0 快照停在 `866122e9`；记录员本时点复测 HEAD/origin/main 仍同为 `558185c9`，工作树 **660 项（190 M + 470 ??）**（基线时点 157 M + 443 ??，他线继续在飞）。
- **T1 前提失效（基线 §3.1 重大不符）**：WG-10"三处直接图像执行旁路"在 HEAD 已全部走共享 coordinator（批次 14 `759148ba` 落迁移、批次 17 `ecb1b3e6` 最后改动；`DefaultFigureGenerationCoordinator.kt:339/:349` 为全库唯二 `channel.generate` 合法位置）——本批 T1 改写为"独立复核 + 交付缺失的 HTTP transport 层旁路观测测试"。
- **T2/T3 属真缺**（基线 §3.2/§3.3）：CAPTURE_PARSE 无 FigureGenerationProposal 绑定；TEXT_ONLY 捷径仍在（`CleanRedrawTaskRunner.kt:281-298`）；`CleanImageResult(bytes: ByteArray)` 回调未类型化。
- **schema 面**：DB schema 69（identityHash `6633b7c2fe223041c4a2927203853f4f`）、请求 schema 18（`ModelTasks.kt:546-547`）；70.json 未占用；本批零 schema 预期成立（记录员复测：`core/database/schemas/` 与 `ReviewPlannerV2.kt`/`LearningCoreVersions.kt`/`AdaptiveQuestionSelector.kt`/`app/src/main/AndroidManifest.xml` 均 clean）。
- **热区扩大**（基线 §2）：`CleanRedrawTaskPort.kt` 新 M（计划未点名）、core/data 主源码 2 文件 M（`RoomMistakeDetailRepository.kt`/`RoomTutorToolRunner.kt` 他线）、core/export 5 M、docs/research 9 M——提交分块按此重算（§7）。
- **原生库基数**：core:data 实际 5 种 .so（litert 三件套 + mlkit OCR + datastore counter；litert 族不出 x86）；本机**无 NDK**（llvm-readelf 不可用，R4 用等判据 Python 自写 ELF 解析替代）；zipalign 可用（build-tools 34/36/37，支持 `-P 16`）。

## 2. 五研究线（选型/提案/来源/待验证）+ 研究复核结论

产出全部落盘 `docs/research/`，逐条标来源与 license；单一来源标"待验证"（计划 §3）。

- **R1 JS 引擎选型**（`docs/research/2026-10-25-js-engine-selection.md`）：维持契约既有裁定——dokar3/quickjs-kt v1.0.15 钉 commit `a081755`（绑定层 Apache-2.0 + 内嵌 bellard/quickjs 04be246 MIT、minSdk 23、四 ABI 含 x86_64/arm64、memoryLimit/maxStackSize/interruptEvaluation API、与 RenderScene 纯计算契约契合），但 R1 原判 T4 必须消费钉源码 + NDK r28c 自建产物——发布 AAR 本日实测 GNU_RELRO 结束未 16KB 对齐（arm64 余 4096、x86_64 余 12288），R1 据此认为不可直接消费；硬终止三层中库只提供协作层，进程级 watchdog+killProcess 按契约 §4 自建；整轨摘除走 core:domain VisualExecutionEngine 端口 + core:visual-js 独立模块。**（复核裁决见本节末：R1 的阻断理由被证伪，消费形态悬置。）**
- **R2 崩溃收集与反馈闭环**（`docs/research/2026-10-25-crash-feedback-selection.md`）：自写最小 UncaughtExceptionHandler + ApplicationExitInfo(API30+) 启动自检 + 环形文件缓冲（filesDir/diagnostics，总量封顶）+ 复用既有 SAF CreateDocument/摘要复读导出——零新依赖、结构性零外发（strictOffline 无 INTERNET 权限物理不可联网）、零新增隐私面；API 23–29 ANR 缺口如实登记不伪覆盖。ACRA（Apache-2.0）留作"富设备上下文崩溃报告"后备。
- **R3 AI 内容标识合规**（`docs/research/2026-10-25-ai-content-labeling-compliance.md`）：《标识办法》（2025-09-01 施行）对本 App 的核心义务 = 显式标识（讲题界面提示 + 生成图角标 + PDF/DOCX 导出标识，第四条）与隐式标识（第五条三要素，已落但格式未按 TC260 XMP 规范）；T7 建议实做 G1/G2/G3，G4–G6 登记、G7 未成年人叠加单独呈用户裁定。
- **R4 16KB/arm64/API23 构建面**（`docs/research/2026-10-25-16kb-arm64-api23-build.md`，决定 T8）：gradle 侧唯一推荐改动 = app defaultConfig 加 `ndk{abiFilters+=setOf("arm64-v8a","x86_64")}`（ZIP 层 AGP 9.3.0 已实测自动 16KB 合规、零配置；ELF 层 arm64/x86_64 全过，唯一违规是 ML Kit 32 位 .so 且 Maven 实测已是最新无升级路径→只能裁 ABI，产品代价 = 放弃纯 armv7 设备需 T8 呈报）；quickjs-kt 1.0.15 AAR 四 ABI 实测全合规，**推翻 10-17 探针"必须源码重建"的 RELRO 判读——探针没应用官方"GNU_RELRO 独占 LOAD 段非零余数仍合规"豁免**，R1 可据此重裁。
- **R5 G5 自适应选题先例与 API 提案**（`docs/research/2026-10-25-g5-adaptive-selection-proposal.md`）：**提案（不实现，待用户裁定）**：推荐分阶段——选项 A（本地双端口：读装配器 + proso 式 Elo 预测器，零模型任务扩展，选择器一行不改）先行满足 5B 门"G5 有生产调用点"与 KF-16 收尾；选项 C（A + NO_SAFE_CANDIDATE 触发生成补池 + 确认卡 + 受控探索额度）为 5B 终态；选项 B（走廊参数透传入 KNOWLEDGE_QUIZ 让模型瞄准难度出题）无开源先例、真实 Provider 无 key 不可验证，不单独采用。三个裁定要点（A/C 取舍、Elo 状态取台账惰性派生还是落表、探索额度数值）呈用户裁定。
- **研究复核结论（双复核轨）**：逐线复核五条研究线结论，抽查关键来源（仓内一手文件逐行核对 + GitHub API/curl/官方文档/自写 ELF 解析/zipalign 复跑共 20 余项独立验证）。总体结论：**研究质量高**，仓内一手引用与外部 license/维护度事实几乎全部复核成立（quickjs-kt Apache-2.0 + bellard/quickjs MIT 子模块、issue #240 开放、Rhino minSdk 26、Zipline 非沙箱声明、catR GPL-3 一票否决、catsim/OATutor/proso/pyBKT license 与维护度、ML Kit 版本终点 16.0.1/17.0.0、zipalign ZIP 层两 APK 复跑全过、mlkit 32 位 ELF 违规复算成立、CAC《标识办法》条文逐条一致、R5 全部代码行引用逐字吻合）。**唯一高危问题是 R1 与 R4 的正面矛盾**：两者对同一 quickjs 发布 AAR 的同组 ELF 字节给出相反 16KB 判定——复核用自写 ELF 解析 + 官方 page-sizes 文档 + 官方 check_elf_alignment.sh 裁决：**R4 正确**（RELRO 独占 LOAD 且保护范围无可写数据→按官方豁免四 ABI 全合规，Google 自家 litert/mlkit 同构为归谬佐证），R1 的"严格读法"（要求下一可写 LOAD 落在 16KB 页边界）不成立，其"发布 AAR 不可直接消费→必须钉源码 NDK 重建"的**阻断理由错误**，且该错误判据已写入契约 `visual-generation-contract.md:22`；**T4 开工前必须先收口此矛盾**（源码重建若保留，理由只剩供应链钉版，且 T8 终验判据须改用官方两步法，否则 r28c 自建产物也会被同一错误口径判死）。次级问题：R2 未登记既有协程 backstop 对"自写 UncaughtExceptionHandler"的遮蔽缝隙（T6 需同接诊断缓冲——复核焦点③核到 `DiagnosticsBootstrap.recordCoroutineFailure` 生产者已在账本内，实现面已闭合，R2 文档登记缺失为小缺）；R3 的 G3（导出标识）推荐形态（图注/页脚）对第四条第二款的满足性未进未验证清单，T7 应挂条件；R5 未把"待裁定"说成可执行（状态栏/裁定要点/范围声明三重维护干净），仅 5B 验收门字面 vs 方向文本的读法差异应在裁定材料中并列呈报；R1/R4 的 ABI 面（四 ABI vs abiFilters 裁两位）需在 T4/T8 前对齐。**矛盾悬置的落点**：`core/domain/.../VisualExecutionEngine.kt:13-18` 如实登记冲突并按保守分支只落端口（零依赖），未掩盖。

## 3. 十实施轨 + 集成 + 设备轨

> 判定口径：**代码级完成** = 改动在当前树且记录员/复核静态核对属实；执行证据分列（设备实跑 / JVM 工件 / 独立编译执行 / 无证据=UNVERIFIED）。文件清单已逐个 `git status` 对上（附录）。

### 3.1 T1-WG10 旁路迁移 —— **部分**

- 文件（1，新）：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureHttpTransportBypassObservationTest.kt`。
- 交付：T1 前提已失效——三处直调旁路在 HEAD 已全部走共享 coordinator（批次 14–17 已迁移，与基线 §3.1 判断一致）；本轨独立复核三入口 + 传输层 + app 装配后，交付缺失的 **HTTP transport 层旁路观测测试**（三入口 × 拒绝=0/授予=1 + fail-closed 兜底）。测试用真实 `OpenAiImageGenerationChannel`+MockWebServer 以 `server.requestCount` 断言（:112-115 拒绝=0、:134 授予=1 对照臂、:227-275 未注入 coordinator 的 fail-closed 兜底）。测试是否绿属门禁，本轨未运行 Gradle。

### 3.2 T2-WG02 转写源证据 —— **完成**

- 文件（7）：`core/model/.../CapturedQuestionDocument.kt`（M）、`core/data/.../model/OpenAiModelResponseParsers.kt`（M）、`core/data/.../model/OpenAiModelTaskAdapters.kt`（M）、`core/data/.../capture/RoomCaptureWorkflowRepository.kt`（M，**整文件纳入**：另含并行 T3/WG-03 轨同文件 hunk——FigureArtifactRef 挂接改造；本轨未回退未改写他轨内容）、`core/model/src/test/.../CapturedFigureReferenceTest.kt`（新）、`core/data/src/test/.../model/CaptureParseFigureRequestParseTest.kt`（新）、`core/data/src/test/.../capture/CaptureParseFigureDecisionTest.kt`（新）。
- 交付：WG-02 落地——CAPTURE_PARSE 需图时返回同型 FigureGenerationProposal，parser 严格白名单 + 单一形状判据校验，图形经 `CapturedFigureReference`（绑定源页/裁切/草稿修订/源 SHA，`@EncodeDefault(NEVER)` 保旧文档指纹稳定）类型化进入题目文档；提交/续跑侧按绑定复用 proposal（换题/重裁/SHA 变化即失效）、缺省且需判定才调 IMAGE_PIPELINE_CLASSIFY、NEED_SOURCE/JS 仅记录不执行，七类验收用例全部以 JVM 测试钉住（Gradle 执行按纪律交门禁）。
- 兼容性（复核焦点②）：`@EncodeDefault(NEVER)` 保证空 figureReferences 不落键、旧文档 decode→重算指纹逐位稳定（`CapturedQuestionDocument.kt:27-33,394-405`；`CapturedFigureReferenceTest.kt:212`）；解析层 `FIGURE_GENERATION_WIRE_KEYS` 白名单 + policyVersion/purpose 本地写入（模型回填即拒）、figureReferences>8 整轮拒；提示词 PARSE_PROMPT 只许 GENERATE 用 IMAGE_MODEL 并明写"本构建未接通其他方法"——与"JS 本批不广告"边界一致。

### 3.3 T3-WG03 净化续跑 —— **部分**

- 文件（16）：`core/domain/.../CleanImageGenerator.kt`、`core/database/.../CleanRedrawTaskContract.kt`（开工即脏，整文件纳入并保留他线 hunk）、`core/database/.../entity/CleanRedrawTaskEntity.kt`、`core/data/.../capture/CleanRedrawTaskRunner.kt`、`core/data/.../capture/ConfiguredCleanImageGenerator.kt`、`core/data/.../capture/RoomCaptureWorkflowRepository.kt`、`core/data/.../capture/ExplicitRedrawService.kt`（含修复环 F3 修复 hunk，见 §4）、`core/domain/.../CaptureWorkflowRepository.kt`、`app/.../SmartMistakeBookApplication.kt`（T3 装配 hunk）+ 测试 5（CleanRedrawTaskRunnerTest/ExplicitRedrawServiceTest/ConfiguredCleanImageGeneratorTest/FigureGenerationWiringTest JVM + CleanRedrawTaskInstrumentedTest/CaptureWorkflowInstrumentedTest/CaptureWorkflowTestBase 设备）。
- 交付：4E WG-03——移除 TEXT_ONLY 冒充成功的捷径（NO_FIGURE 独立终态）、生成请求已发出未知停 AWAITING_VERIFICATION 不盲目补发、净化/挂接/回调链升级为类型化 FigureArtifactRef（canonical 图构造即验 sha、挂接核对通过才绑修订、provenance 按真实方法/模型填写、JS 快照位就位但不广告），测试面按验收六项扩展；Gradle 验证按纪律移交门禁。
- 兼容性（复核焦点②）：`clean_redraw_task` 旧行（含旧 TEXT_ONLY=SUCCEEDED 行）兼容、预算无双计——NO_FIGURE/AWAITING_VERIFICATION 只由新代码写入且均不可领取（`CleanRedrawTaskDao.kt:130-146` claim SQL OR 不含两新态）；`:168-179` finishAttempt 只写 status/last_failure/updated_at 不动 attempt_count→AWAITING_VERIFICATION→SUCCEEDED 覆写成立且不双计尝试；租约接线 staleBefore=0（`CleanRedrawTaskRunner.kt:187`）、对账传进程启动时刻（`:197-211`；`SmartMistakeBookApplication.kt:593,608`）；双向互斥：`runner.resumePending` 跳过 explicit 前缀行（`:214-217`）、`ExplicitRedrawService.resumePending` 只取前缀行（`:295-310`）、runExplicit 要求前缀（`:243`）；markAwaitingVerification 写不进就不发付费请求（`:336-341`）。
- **为什么"部分"**：F3 修复（服务层双分支）已落，但 **AWAITING_VERIFICATION 独立 UI 态 + "重新核实/放弃"路径**需要 core/domain 与 feature/library 的类型/文案改动（他线热区禁改）——登记遗留（§6），**本轨不声称 WG-03 全量收口**。

### 3.4 T4-QuickJS 接入（保守分支）—— **完成（端口+fake+契约用例）**

- 文件（3，全新）：`core/domain/.../VisualExecutionEngine.kt`、`core/domain/src/test/.../FakeVisualExecutionEngine.kt`、`core/domain/src/test/.../VisualExecutionEngineContractTest.kt`。
- 交付：T4 保守分支——core:domain 新增 VisualExecutionEngine 端口（沙箱白名单/双层终止/内存上限/单在飞/熔断语义，纯 JVM 无 Android 依赖）+ 测试 fake + 17 个契约用例；**零依赖引入、JS 方法不广告**，R1-R4 RELRO 裁决悬置已登记（`VisualExecutionEngine.kt:13-18`）。`:271-275` 把协作中断（PHASE_TIMEOUT）与 watchdog 硬终止（HARD_DEADLINE_EXCEEDED）分成两类拒因，防真实实现混层；close 幂等有契约用例（ContractTest:510）。
- 执行证据：轨声明"独立 Kotlin 2.3.21 编译器编译 EXIT=0，JUnitCore 4 连跑全绿"——**未见仓内留痕，记录员未复跑（Gradle 禁），该句按未复核登记**；设备轨步 1 另有 fake 端口 17/17 实跑（§3.12，转录）。真实引擎（.so 装载/沙箱/硬终止/内存上限）**UNVERIFIED——T4 保守分支无库可测，设计如此**。
- **T4 真实接入前置**：R1/R4 RELRO 矛盾收口（§2）；R1 §4 已预登记真实接入时 issue #240（job 队列自续绕过协作中断）必须进仪器化用例。

### 3.5 T5-生图评测 harness —— **完成**

- 文件（10，全新）：`tools/figure_eval/`{tasks_blind_120.json, tasks_dev_24.json, freeze_manifest.json, budget.json, build_frozen_tasks.py, figure_eval_gate.py, test_figure_eval_gate.py, README.md, reports/figure_eval_fake_run_report.json, reports/figure_eval_fake_run_report.md}。
- 交付：PRD-09 可先行半边——120 盲测任务（6 组×20）+ 24 开发集冻结为 SHA-256 manifest 防篡改夹具（题意/图类型/独立事实预期/评审来源字段齐，事实预期 288 条其中 209 条 critical），显式评测预算清单（六派发总门引用契约:138+ModelTasks.kt:61，费用未知记未知），两后端配对/切换框架（IMAGE_MODEL fake 驱动端到端跑通 + JS 留桩待 T4 端口），指标字段与采集点就位（首图 p50/p95/总时长/token/费用），门常量按 design§6:91/:95 与 PRD-09:133/:134 原文引用不发明；fake 运行产出 JSON/MD 报告工件、**18 项机制测试全绿**、真实 Provider 质量如实标 UNVERIFIED。
- 复核焦点⑤独立重算：freeze_manifest 的 files[*].sha256 与两任务文件实际 sha256 一致；120/24 task_id 集合不相交；review_source 与 fact_expectations 逐条非空（缺失=0）；`figure_eval_gate.py:174-178` 运行前强制 sha 校验；fake 报告明确"真实 Provider 质量：UNVERIFIED（无凭据…不构成任何真实质量结论）""费用：unknown（未知费用记未知、不写 0）"、开发集不计入 120 分母、JS 后端 NOT_WIRED；`python -m pytest tools/figure_eval/test_figure_eval_gate.py -q` → **18 passed in 0.12s**（复核实跑）。

### 3.6 T6 beta 反馈闭环（计划 §4 T6，R2 选型落地）—— **完成**

- 文件（17）：`app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics/` 8 件新（DiagnosticEvent/DiagnosticEventJson/CrashLedger/CrashHandler/AnrWatchdogMonitor/StartupExitReconciler/DiagnosticsBundleExporter/DiagnosticsBootstrap）+ `app/src/test/.../diagnostics/` 7 件新测试 + `SmartMistakeBookApplication.kt`（T6 接线 hunk）+ `SecondaryScreens.kt`（导出入口 hunk）。
- 交付：自写最小反馈闭环——崩溃/协程/看门狗 ANR 本地 JSONL 环形账本（**文件数×字节×条目三重封顶**：DEFAULT_MAX_FILES=6/64KiB/300 条，`CrashLedger.kt:154-156`）+ ApplicationExitInfo 启动对账"上次异常退出"只读提示 + 设置存储页"导出诊断包"（SAF+SHA-256 复读校验，`DiagnosticsBundleExporter.writeAndVerify`）；零新依赖、零 manifest 改动、零 DB schema、零外发；本地 JVM 测试先红后绿 **32/32 通过**（7 个测试文件 @Test 合计 32，与声明一致——复核计数核对）。
- 零外发证据（复核焦点③/⑤）：`DiagnosticsZeroEgressTest` 三断言（主 manifest 无 INTERNET/ACCESS_NETWORK_STATE、strictOffline `tools:node=remove` 物理移除、诊断源码禁网络 API token 清单）且 KDoc 如实声明"不做运行时抓包"（结构层证明）；记录员复测 `git status` app/src/main/AndroidManifest.xml clean、`app/build.gradle.kts` diff 仅 T8 的 ndk hunk；诊断包为结构性白名单（DiagnosticEvent 字段即全集：时间/类别/线程/消息/截断堆栈/版本三元组），残余风险=异常消息理论携带数据（评低）。
- API 23–29 ANR 覆盖缺口在 `AnrWatchdogMonitor.kt:12-13` 与 R2 文档如实登记（不伪覆盖）。

### 3.7 T7-AI 内容标识 —— **部分**

- 文件（6）：`core/export/.../AiGeneratedImageCaption.kt`（新）、`core/export/.../MistakePdfPagePlanner.kt`（M）、`core/export/.../DocxMistakeRenderer.kt`（M）、`core/export/src/test/.../MistakeExportVisualRoleTest.kt`（M）、`core/export/src/test/.../AiGeneratedImageCaptionTest.kt`（新）、`core/data/src/test/.../capture/GeneratedFigureProvenanceTest.kt`（新）。
- 交付：T7 落地——导出链（PDF+DOCX）AI 生成图显式图注与判定助手、隐式标识核验 + JVM 守门用例；**G1/G2 在禁改热区按指令登记遗留，G4/G5 按义务清单登记下批**。
- **记录员义务落盘：G3 已落、G1/G2 热区未落待下批，T7 不声称完成/已合规。** 具体：G3 = `AI_GENERATED_IMAGE_CAPTION="本图由 AI 生成"`（`AiGeneratedImageCaption.kt:27-37`，判据 CLEAN_IMAGE/TUTOR_EXPLANATION/role=null 逐图标识、QUESTION_DIAGRAM 不伪报宁缺勿假），生产挂点 `MistakePdfPagePlanner.kt:149/:163/:413` 与 `DocxMistakeRenderer.kt:239/:296/:331`；G1（P0 讲题对话 AI 提示，feature/tutor 热区）与 G2（P0 App 内生成图角标，core/ui 热区）**未落**——R3 §6 明示 G1+G2+G3 为本批 T7 实做面，P0 两项在 beta 前仍缺，属已知差距而非完成项（R3 §9.2 已登记）；G4/G5/G6 登记、G7 未成年人叠加呈用户裁定。
- 挂条件（复核）：R3 的 G3 推荐形态（图注/页脚）对《办法》第四条第二款"确保文件含合规显式标识"的满足性未进未验证清单——T7 后续应挂条件核验。

### 3.8 T8-构建面落地 —— **完成**

- 文件（1）：`app/build.gradle.kts`（defaultConfig 加 `ndk { abiFilters += setOf("arm64-v8a","x86_64") }`，全仓唯一 abiFilters 命中（`:39`），单文件单 hunk 可整轨回退；strictOffline/localFirst 两 flavor 均继承）。
- 交付：按 R4 §5 落地 16KB/arm64 构建配置；对主构建全部 21 条 .so 与 quickjs AAR 四 ABI 完成对齐核查实测——保留 ABI 全合规、裁剪对象（mlkit 32 位）实测 FAIL 证据落定、省 23.1MiB。
- 副作用（复核焦点④，需显式呈报）：**32 位设备（armv7/x86）从此不可安装**，minSdk 名义 23 与有效机型面（64 位 only）收窄——本机 AVD 均 x86_64（R4 §4.1 实测）不受影响；该决策不能只存在于代码注释，已在本记录与主线补修行显式化。
- **T8 终验判据修正（复核裁决）**：终验须改用官方两步法（zipalign -P 16 + 官方 check_elf_alignment.sh / 等判据 ELF 解析含 RELRO 豁免），否则 r28c 自建产物也会被 R1 的错误口径判死。

### 3.9 T9-样本台账 —— **完成**

- 文件（6）：`core/domain/.../LongRangeSampleLedger.kt`（新）、`core/domain/src/test/.../LongRangeSampleLedgerTest.kt`（新）、`core/data/src/test/.../study/LongRangeSampleLedgerReadTest.kt`（新）、`core/data/.../study/ReviewLogSink.kt`（M）、`core/data/.../study/RoomBackedStudyExperienceRepository.kt`（M）、`core/domain/.../StudyExperienceRepository.kt`（M）。
- 交付：AUD-17 合格长程样本台账只读分类视图——域层 LongRangeSampleLedger（分类器+聚合报告+明细查询，判据逐字复用 predictableSampleCount 同口径并有等价对拍用例钉住，`LongRangeSampleLedgerReadTest.kt:68`）+ ReviewLogSink 只读方法（读原始面含撤销行，`:191`）+ StudyExperienceRepository 默认只读出口（未来 Wave X 用，**本批不接 UI**）；400 门仅引用常量（gateFloor=MIN_SAMPLES_FOR_FITTING）不做达标判定，**零 schema、零投影/排程触碰、无启用开关**。复核：`grep LongRangeSampleLedger` 于 app/feature/core/ui 零命中（无 UI 接线）；KDoc 明写只读边界。

### 3.10 T10-3E 设备用例 —— **部分**

- 文件（1，新）：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlanner3EScanInstrumentedTest.kt`。
- 交付：真 Room 种子库（MATH+PHYSICS 多科、15 绑定题、15 份可出题材料走真写口、12 个离开今日并集的弱掌握点、真 DataStore 考试表）直跑 StudyReviewPlannerService，钉四面：第二档全库捞回+两个范围负向、FULL_LIBRARY_SCAN_DAILY_CAP 封顶与平局 id 定序、时间预算先/后于名额两向封顶、无考试日与窗口外考试的计划指纹逐位一致+G6 扫描读门（真端口计数）。
- 设备实跑（转录，§3.12 步 3）：首跑 FAILURES(3/3, DatabaseContractViolationException sourceUri)→夹具修复后复跑 exit=0 但 Tests run:3 Failures:3→**1 绿 2 红**；归因：`NewIntroductionPolicy.kt` 与 `StudyReviewPlannerService.kt` 均 git 干净=HEAD 态；源推导唯一可达形状=全超尺寸死锁分支（NewIntroductionPolicy `:122-129`）中 top 候选真实时长>当日总预算→sliceBudget=timeBudget、top 被预算档 deferred→granted=effectiveSlice−timeBudget<0（`:68` require 爆 IllegalArgumentException；机制读码证明见基线 §8.3）；JVM 对照 `StudyReviewPlannerExamWindowTest` 6/0/0 绿=该形状 JVM 未覆盖；**域层修复不在设备轨授权，登记 KD-64（§5/§6），待实施轨**。

### 3.11 集成

- T3 装配：`SmartMistakeBookApplication.kt`（clean_redraw 租约对账传进程启动时刻，`:593,608`）+ T6 接线（DiagnosticsBootstrap 安装：CrashHandler/AnrWatchdogMonitor/StartupExitReconciler/协程失败同接诊断账本）。
- T6 UI：`SecondaryScreens.kt` 存储页"导出诊断包"（SAF launcher）+ `diagnostics_exit_notice` 提示位。
- `SmartMistakeBookRoot.kt` **本批未改**（他线动画改造引用未跟踪 `core/ui/.../ClaudeMotion.kt`——开工与记录时点均 untracked；提交必须排除，§7）。
- T2/T5/T9/T10 免接线判定：T2 在既有采集装配内闭环；T5 为 tools/ 夹具+脚本（零生产接线）；T9 生产零消费（端口默认 null）；T10 为仪器化用例。

### 3.12 设备轨（五组命令 + exit + 用例数落盘——基线 §8.2 义务的执行）

> 来源：设备轨实跑后由工单转录至此（记录员未复跑、无设备）。环境 `emulator-5554`（API 34, x86_64）；协议 = assembleDebugAndroidTest + `adb install -r -t` + `am instrument -w -e class ...`，**全程未跑 connectedAndroidTest**。以下即基线 §8.2 所指"落盘前一律 UNVERIFIED"数字的落盘载体。

1. **装配（强门材料面）**：`./gradlew.bat :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest :core:export:assembleDebugAndroidTest` → **exit=0 BUILD SUCCESSFUL**；`adb install -r -t` 五 APK 全 Success（app=outputs/apk/localFirst/debug；三个库 androidTest APK 均自指 targetPackage=自身 .test 包，aapt dump xmltree 实证）；`:core:data:assembleDebug` 的 bundleDebugAar 失败为 litert 本地 AAR 结构性限制，与设备无关，未采用该任务（批次 18 同口径）。
2. **步 1 QuickJS（fake 端口）**：build 文件 grep quickjs 零命中；app-localFirst-debug.apk `aapt list` 实证 lib/ 仅 arm64-v8a+x86_64 两 ABI 共 11 条 .so（LiteRt×2、ClGl、androidx.graphics.path、datastore_counter、mlkit_ocr ×2 ABI）、**无任何 quickjs .so**；`:core:domain:test --tests VisualExecutionEngineContractTest` → XML **tests=17 failures=0 errors=0**（fake 端口）；真实引擎（.so 装载/沙箱/硬终止/内存上限）UNVERIFIED——T4 保守分支无库可测，设计如此。
3. **步 2 WG-02/03 设备**（`am instrument -w -e class ... core.data.test/androidx.test.runner.AndroidJUnitRunner`，全部 exit=0）：CleanRedrawTaskInstrumentedTest+GeneratedFigureMetadataInstrumentedTest OK(5)、CaptureWorkflowInstrumentedTest OK(17)、CaptureWorkflowRecoveryInstrumentedTest OK(15)、BatchImportRepositoryInstrumentedTest OK(15)＝**52/52**；HTTP 观测=0 旁路断言：FigureHttpTransportBypassObservationTest（JVM 定向）7/0/0；T2/T3 JVM 定向共 CaptureParseFigureRequestParse 11 + CaptureParseFigureDecision 13 + CleanRedrawTaskRunner 30 + ExplicitRedrawService 17 + ConfiguredCleanImageGenerator 7 + FigureGenerationWiring 2 + CapturedFigureReference(core:model) 14 全绿。
4. **步 3 T10**：StudyReviewPlanner3EScanInstrumentedTest 首跑 FAILURES(3/3, DatabaseContractViolationException sourceUri)→夹具修复后复跑 exit=0 但 Tests run:3 Failures:3→**1 绿 2 红**（fullLibraryScanRecalls… 绿；knowledgeQueueStopsAtTheTimeBudget… 与 noExamDayPlanIsFingerprintIdentical… 均 `java.lang.IllegalArgumentException: Granted slice must not be negative`，栈 `NewIntroductionPolicy$IntakeDecision.<init>(NewIntroductionPolicy.kt:68)`←decide(:151)←`StudyReviewPlannerService.createReviewPlan(:435)`）；归因与前置见 §3.10。
5. **步 4 备份/迁移核心组**（同协议，全部 exit=0）：BackupRestoreInstrumentedTest OK(34)、BackupSelectionRestore+BackupSelectionCreate+BackupSnapshotTrimmer OK(22)、FullMigrationMatrixInstrumentedTest OK(15)、DeviceMigrationDrillInstrumentedTest OK(6)、BackupLargeDatabaseScaleInstrumentedTest OK(1)——合计 **78 例**；批次 19 D1 记录为 7/7/3/7/33 且"最终树新增例 UNVERIFIED"，本轮按最终树 9/10/3/8/34 + 迁移 15 + D1×6 + 365MiB×1 实测补齐。
6. **步 5 T6 端到端**（emulator-5554, API34, x86_64）：`am start -W` Status ok TotalTime 2657ms；`am crash` → AndroidRuntime FATAL EXCEPTION shell-induced、进程终止；重启后 run-as 实读 files/diagnostics/：events-000001.jsonl=CRASH 事件（完整栈+versionName 0.2.0-localfirst+versionCode 2+flavor localFirst）、events-000002.jsonl=EXIT_CRASH 对账事件、session-watermark.txt；存储页 diagnostics_exit_notice 上屏文本="上次异常退出：应用崩溃。诊断信息只存在本机，可在设置的存储页导出诊断包。"；`cmd connectivity airplane-mode enable`（settings get global airplane_mode_on=1）后点 diagnostics_export→SAF Downloads SAVE→pull 1223B 诊断包含头（# format=1/versionName/versionCode/flavor/generatedAt/entries=2）+CRASH 全栈+EXIT_CRASH——**飞行模式中导出成功=零外发实证**；随后 airplane-mode disable。
7. **步 5 T7 标识**：AI_GENERATED_IMAGE_CAPTION="本图由 AI 生成"（`AiGeneratedImageCaption.kt`，判据 CLEAN_IMAGE/TUTOR_EXPLANATION/role=null 逐图标识）；生产挂点 MistakePdfPagePlanner.kt:149/:163/:413 与 DocxMistakeRenderer.kt:239/:296/:331；JVM MistakeExportVisualRoleTest:206 逐字断言图注行（15/0/0）、AiGeneratedImageCaptionTest 6/0/0、GeneratedFigureProvenanceTest 3/0/0；设备 MistakeExportVisualRoleInstrumentedTest OK(2)（真 Room 资产→真 PDF→像素层：讲解图不入题目区/排题目区之后）；**设备像素级图注文字未单独断言**（同一 planner 代码传递覆盖），如实登记。
8. **步 6 R8 冒烟**：全程 `logcat -d` 7654 行扫描——FATAL EXCEPTION ×1（07:26:50 即本轨注入的 T6 崩溃实验，时间戳与账本一致）、ANR in ×0、Fatal signal/tombstoned ×0、WATCHDOG ×0；重启 TotalTime 2358ms；`adb uninstall com.tingyun.smartmistakebook.localfirst` 与 `.localfirst.test` 均 Success；三个库 androidTest harness 包留存设备。
9. **纪律**：全程未跑 connectedAndroidTest；未改任何禁改热区；仅触碰 T10 交付文件一处（夹具修复，见 §3.10）；每步命令/exit/用例数如上，均设备轨本轮实跑（转录）。

## 4. 门禁与设备证据

| 门 | 状态 | 说明 |
| --- | --- | --- |
| 单测门（11 模块 `--continue`） | **第 3 轮 `exit=1`**；**修复环复跑 `exit=0`**（材料未注明运行范围） | 三发现 F1/F2/F3（§5 #18/#19/#20 对应）在第 3 轮提出、与第 1 轮逐字相同；修复环第 2 轮处置完成（下）。F3 的红测已随第 1 轮单测门实跑通过（1106 例仅 1 失败=在途误伤例，已根因修复） |
| 强门（六 androidTest 编译 + `assembleLocalFirstDebug`） | **未跑**（单测门未过） | 按计划顺序不进强门。旁证：设备轨装配步已成功构建 app 两 APK + 三库 androidTest APK（exit=0），但不等于完整强门清单——**强门仍为提交前置** |
| 设备 | 见 §3.12（九组命令/exit/用例数已落盘） | 全部裸 `am instrument`；协议不产 XML（基线 §8.1 根因），数字以本记录落盘为审计载体 |
| 真实 Provider | **UNVERIFIED** | 无凭据、未调用 |

**修复环第 2 轮处置摘要**：三条均属实、均已处置——**F3 已修**：`ExplicitRedrawService.request()` 对 AWAITING_VERIFICATION 双分支（本进程执行者还在跑→`Accepted` 进行中；执行者不在→如实 `Rejected("AWAITING_VERIFICATION")`），红测已随第 1 轮单测门实跑通过（1106 例仅 1 失败=在途误伤例，已根因修复），独立 UI 态+"重新核实/放弃"路径因 feature/library 与 core/domain 热区禁改登记遗留。**F2 已在 R3 文档 §9 登记**：G3 已落、G1/G2（P0）未落待下批、**T7 不得声称完成/已合规**。**F1 已在批次 20 基线文档 §8 落盘登记**：设备轨数字仓内零落盘产物实测、记录阶段义务、T10 崩溃机制读码证明、复跑命令清单。记录员义务三项已在本记录执行：①设备轨五组命令+exit+用例数落盘（§3.12，落盘前 UNVERIFIED）；②写明"G3 已落、G1/G2 热区未落待下批，T7 不声称完成/已合规"（§3.7）；③AWAITING_VERIFICATION 独立 UI 态与放弃路径登记为遗留（§3.3/§6）。

**逐条处置**：
- **F1【中】设备轨数字无落盘——属实（复核已亲证），本轮升级为落盘登记**。第 1 轮已实测：`grep -rn "52/52\|78/78" docs/ out/` 仅命中历史 KB 批次；androidTest-results mtime=2026-10-20；`out/*`=2026-09；`.tmp_t10/`=讲义 OCR；根因是 `am instrument` 直跑协议不产 XML。→ 本记录 §3.12 完成落盘；基线 §8.4 给出复跑命令清单。
- **F2【中】T7 只落 G3、G1/G2 P0 未落且未登记为缺口——属实**。→ R3 文档 §9.1/§9.2 补记登记；本记录 §3.7 明示"T7 不声称完成/已合规"；主线补修行同步。
- **F3【中】AWAITING_VERIFICATION 行无恢复路径且 UI 映射 Idle——属实，服务层已修**。→ `ExplicitRedrawService.kt:261-267` 双分支落树（在途=Accepted 进行中；执行者不在=如实 Rejected），注释明写该路径与独立 UI 态需 core/domain 与 feature/library 改动（他线热区）登记遗留；`ExplicitRedrawServiceTest` 扩展红测随第 1 轮单测门实跑通过；**独立 UI 态+"重新核实/放弃"路径为遗留（§6）**。

**提交前置条件（记录员意见）**：强门一次 `exit=0`（单测门修复环复跑已 `exit=0` 但范围未注明，建议提交前全量 11 模块再跑一次留痕）+ §6 遗留不阻塞提交但必须随记录入册。

## 5. 复核发现与处置（24 条，severity/status 按原复核）

| # | 位置 | 发现 | 级别 | 状态/处置 |
| --- | --- | --- | --- | --- |
| 1 | AttachedImageGenerator.kt:200、TutorFigureGenerator.kt:310-311/:406、ConfiguredCleanImageGenerator.kt:140、DefaultFigureGenerationCoordinator.kt:339/:349 | 重点1：WG-10 三处原直调旁路在当前树全部收编进共享 coordinator，出网点唯一（grep 全 core/data 生产码 channel.generate 仅 coordinator 两处；观测测试用 MockWebServer requestCount 断言） | 低 | 已独立复核；测试是否绿属门禁 |
| 2 | CleanRedrawTaskRunner.kt:320-322/:362-376/:343-345/:261-263、CleanRedrawTaskContract.kt:31/:42/:48 | 重点2：TEXT_ONLY 冒充捷径确已移除，NO_FIGURE/AWAITING_VERIFICATION 独立终态不伪报（finish(NO_FIGURE) 注释明写"不伪报已产图"；挂接成功才 SUCCEEDED；TERMINAL 列表含两新态；挂接前逐位核对 contentSha256、JsSnapshot 如实拒绝） | 低 | 已独立复核 |
| 3 | core/domain/build.gradle.kts（未修改）；全仓 grep quickjs=0；TutorToolLoop.kt:347；VisualExecutionEngine.kt | 重点3：T4 零依赖引入+方法面零 JS 广告（能力目录/PARSE_PROMPT 只许 IMAGE_MODEL 并明写"本构建未接通其他方法"；解析层接受 JS 字段位仅记录，执行门不建行不出网）。注意：T4 声称的"独立 Kotlin 2.3.21 编译 EXIT=0、JUnitCore 连跑全绿"未见仓内留痕，复核未复跑 | 低 | 已独立复核（编译句按未复核登记） |
| 4 | tools/figure_eval/（10 文件）；freeze_manifest.json；budget.json；figure_eval_gate.py:52-134/:174-178；design:88-97；followup-plan:128-140；ModelTasks.kt:61 | 重点4：T5 盲测/开发集分离落盘、review_source 必填、门常量引用而非发明——sha256 逐字一致、120/24 交集 0、事实预期 288/critical 209、六派发 contract:138 逐字、MAX_DISPATCHES=6、≥90%/≥95%/每组≥90%/关键=0、NO_FIGURE 不算成功、p95 30s/120s+未知费用；`pytest test_figure_eval_gate.py -q` → 18 passed in 0.12s（复核实跑） | 低 | 已独立复核 |
| 5 | DiagnosticsZeroEgressTest.kt；AndroidManifest.xml（无 diff）；app/build.gradle.kts（仅 ndk hunk）；CrashLedger.kt:154-156 | 重点5：T6 零外发证据成立（结构性证明+源码零网络 API+manifest 零改动+零新依赖）；三重封顶 6/64KiB/300 条属实；ANR API23-29 缺口如实登记 | 低 | 已独立复核 |
| 6 | AiGeneratedImageCaption.kt:27-37；MistakePdfPagePlanner/DocxMistakeRenderer 三处图注 hunk；R3 §6 G1-G7 | 重点5：T7 差距项与 R3 义务清单逐条对应成立；但 R3 建议本批实做的 G1/G2（P0）因热区禁改未落地，仅 G3（P1 导出图注）落地——P0 两项在 beta 前仍缺，属已知差距而非完成项 | 低 | 已独立复核 |
| 7 | LongRangeSampleLedger.kt；ReviewLogSink.kt:191；LongRangeSampleLedgerReadTest.kt:68；app/feature/core/ui grep=0 | 重点6：T9 无启用路径、纯只读、判据同源（400 门只引用常量不做达标判定） | 低 | 已独立复核 |
| 8 | androidTest-results（mtime 2026-10-20 陈旧）；out/（2026-09）；.tmp_t10/（无关 OCR）；全仓 grep '52/52\|78/78\|1 绿 2 红' | **【F1】设备轨全部运行数字（WG-02/03 52/52、备份/迁移 78/78、T6 端到端、T10 1绿2红、R8 冒烟、QuickJS fake 17/17）在仓内找不到任何落盘产物**。T10 用例源码真实完整、其声称暴露的崩溃机制从码上成立（NewIntroductionPolicy.kt:68 require vs :156 附近 granted=effectiveSlice−remaining 可为负）——不构成伪造证据，但按计划 §6 记录阶段必须把设备轨命令+退出码+结果数字落盘成批次记录，否则永远不可审计 | 中 | 未复核（复核无设备）→ **已处置：本记录 §3.12 落盘 + 基线 §8 登记（F1 处置）** |
| 9 | FigureGenerationAuditFixtures.kt（未跟踪新文件） | 本批产物中有一个未入任何轨文件清单的新文件：图生成审计台账的仪器化夹具（设备轨 WG-02/03 设备回归所用）——按共享纪律"整文件纳入并注明"，提交清单必须补登 | 低 | 已独立复核 → **§7 块 9 补登** |
| 10 | OpenAiModelResponseParsers.kt（toCapturedFigureProposal 与 FIGURE_GENERATION_WIRE_KEYS） | 【低】KDoc 与代码不一致：注释称 policyVersion 与 purpose"故意不在白名单里"，但白名单实际含 "purpose"，且另有 wirePurpose!=SOURCE_TRANSCRIPTION 即 throw 的值校验——行为仍 fail-closed，应改注释而非改行为 | 低 | 已独立复核 → 登记 §6（改注释不改行为，下批） |
| 11 | git status 全量对照（680 行）与 HOT_ZONES 清单 | 重点7：无热区越权改写；全部脏文件可归因（十轨声明文件逐一核到；app 6 个开工即脏 M 本批未触碰；新增脏的 SecondaryScreens/Application 恰为 T6/T3 声明文件；core/database 新脏 = T3 声明文件；settings.gradle.kts/.gitignore/core-export 其余改动指向他线） | 低 | 已独立复核 |
| 12 | R1 §3.1；R4 §2.4/§5；R2；R5 四文档 | R1-R4 研究声明与文档一致；R1/R4 的 RELRO 判读矛盾被如实登记并悬置，未掩盖（R1 含 AAR 实测原始输出与 gh 取证；R4 同余数复测+官方豁免引文+"与 10-17 探针结论相反、判读差异纯在判据"；21 条 .so ZIP 层实测表与 mlkit x86 FAIL 证据落盘；R2 判据表含 ACRA/Sentry/自写逐项；R5 三选项均标"待用户裁定、不实现"） | 低 | 已独立复核 |
| 13 | 五轨+设备轨 summary 的验证句 | 未独立复跑的声明清单（如实登记，非指控）：Gradle 单测/强门、设备轨数字、T4 独立编译、T6 JVM 32/32、T9/T2 JVM 用例执行。替代证据：测试源码全部在盘且断言与验收面一致（T2 三文件 38 @Test、T4 契约 17 @Test、T6 32 @Test、T9 9 @Test）；T5 的 18 项 pytest 复核实跑全绿；T6 @Test 计数与 32/32 吻合。无任何一轨把 UNVERIFIED 事实写成已验证 | 低 | 未复核（复核未运行 Gradle/设备） |
| 14 | VisualExecutionEngine.kt:13-18、R1 §2.1、R4 §2.4 | 焦点①：本批未引入任何库，供应链面为零。R1/R4 判定相反被正确悬置，只落端口+fake+契约用例；"R1 被官方豁免判据证伪"经官方原文（developer.android.com/guide/practices/page-sizes："GNU_RELRO is in its own LOAD segment"）核对成立；:271-275 双类拒因防混层；close 幂等有契约用例；R1 §4 已预登记 issue #240 仪器化用例义务 | 低 | 已独立复核 |
| 15 | CleanRedrawTaskContract.kt、CleanRedrawTaskDao.kt:130-146/:168-179、CleanRedrawTaskRunner.kt:261-263/:339-341/:214-217 | 焦点②a：clean_redraw_task 旧行（含旧 TEXT_ONLY=SUCCEEDED 行）兼容、预算无双计。NO_FIGURE/AWAITING_VERIFICATION 只由新代码写入且均不可领取；保存期/显式重绘两轨按 operation_id 前缀双向互斥，无双跑双付费；markAwaitingVerification 写不进就不发付费请求 | 低 | 已独立复核 |
| 16 | CapturedQuestionDocument.kt:27-33/:394-405、OpenAiModelResponseParsers.kt（白名单段）、CapturedFigureReferenceTest.kt:212 | 焦点②b：proposal 旧行读取兼容。@EncodeDefault(NEVER) 保证空 figureReferences 不落键、旧文档 decode→重算指纹逐位稳定，不把升级前旧题误判 CorruptSnapshot；新解析严格白名单+绑定字段全取请求侧 | 低 | 已独立复核 |
| 17 | DiagnosticEvent.kt:10-21、DiagnosticsBundleExporter.kt:31-43/:65-88、DiagnosticsZeroEgressTest | 焦点③：诊断包为结构性白名单，无 Key/租约/学生内容出口。生产者仅四处（CrashHandler 8K 截断堆栈/AnrWatchdogMonitor 固定文案/DiagnosticsBootstrap.recordCoroutineFailure/StartupExitReconciler 系统分类）；账本与导出包只含 DiagnosticEvent 字段；残余风险=异常消息理论携带数据（导出需用户显式操作且未发现拼接学生内容的生产者路径），评低 | 低 | 已独立复核 |
| 18 | R3 §6（G1/G2=P0）、AiGeneratedImageCaption.kt、docs/current/implementation-status.md（无批次 20 T7 记录） | **【F2】T7 只落了 G3，R3 标 P0 的 G1（讲题对话 AI 提示）与 G2（App 内生成图角标）未落且未在现有文档登记为缺口——若批次收口记录声称"T7 完成/已合规"即为研究结论放大**（全树 grep "本图由 AI 生成"等 export 外零命中） | 中 | 已独立复核 → **已处置：R3 §9.1/§9.2 登记 + 本记录 §3.7 明示不声称完成/已合规 + 主线补修行同步** |
| 19 | ExplicitRedrawService.kt:246-263/:367-403、CleanRedrawTaskRunner.kt:261-263 | **【F3】AWAITING_VERIFICATION 行无恢复路径且 UI 映射 Idle：显式重绘用户点击后进程死在生成轮→行永久停"待核实"；用户再点重绘被返回 Accepted 但永不执行，详情页还显示 Idle**（request 对该态落到 launchExecution→runAttempt 短路 SKIPPED；toExplicitRedrawState else→Idle；taskId 派生幂等换不回新行；无超时无放弃入口） | 中 | 已独立复核 → **已处置：服务层双分支修复落树（ExplicitRedrawService.kt:261-267，在途=Accepted/不在=如实 Rejected）+ 红测随第 1 轮单测门通过；独立 UI 态+放弃路径登记遗留（§6）** |
| 20 | app/build.gradle.kts:32-41（ndk abiFilters）、R4 §5-1 | 焦点④：T8 副作用受控——abiFilters 全仓唯一一处、flavor 继承、他线模块无原生面；但 32 位设备（armv7/x86）从此不可安装，minSdk 名义 23 与有效机型面（64 位 only）收窄的决策需在收口呈报中显式化，而非只写在注释里 | 低 | 已独立复核 → 本记录 §3.8/§6 + 主线补修行显式化 |
| 21 | freeze_manifest.json、tasks_blind_120.json、tasks_dev_24.json、reports/figure_eval_fake_run_report.md | 焦点⑤a（正面）：PRD-09 夹具冻结与分离纪律经独立重算全部成立（sha 重算比对 True、task_id 集合不相交、review_source/fact_expectations 缺失=0、gate 运行前强制 sha 校验）；fake 报告如实标注 UNVERIFIED、费用记 unknown 不写 0 | 低 | 已独立复核 |
| 22 | R5 抬头（"提案，待用户裁定"）、AdaptiveQuestionSelector.kt（不在本批 diff）、LongRangeSampleLedger.kt:1-30 | 焦点⑤b：G5 严格只研究+提案未实现（AdaptiveQuestionSelector 本批零改动）；R2 选型（自写最小 handler，不引 ACRA/停更 ANR 库）与实现一致（AnrWatchdogMonitor.kt:10-13 引用 SalomonBrys/ANR-Watchdog MIT 为灵感来源并说明零依赖理由）；T9 台账只读、无启用开关 | 低 | 已独立复核 |
| 23 | CleanImageGenerator.kt、CleanRedrawTaskContract.kt、RoomCaptureWorkflowRepository.kt | 共享文件他轨 hunk 核查（限制如实登记）：三文件合并结果内部自洽、无被回退的他轨内容迹象；但无法对照他轨改动前快照逐 hunk 归属。数据事实：工作树 660 变更/470 未跟踪（大量 .agent_* 截图/csv 为历史审计杂物，不在提交清单）；tools/figure_eval/__pycache__/ 已被 .gitignore:12 覆盖（check-ignore 实证） | 低 | 已独立复核 |
| 24 | 全部测试相关结论 | UNVERIFIED 登记项：所有单测门/强门/设备门结果本轮未独立复跑（禁 Gradle），"测试全绿"以脚本门输出为准；T1 的 HTTP transport 观测与 T10 的 3E 设备用例属仪器化层，本轮仅静态审读 | 低 | 未复核 |

## 6. 遗留 / UNVERIFIED / 待裁清单

**工程门（提交前置）**
1. **强门未跑**（单测门未过时按序跳过）——提交前必须一次 exit=0。
2. 单测门修复环复跑 `exit=0` 但材料未注明运行范围——建议提交前全量 11 模块复跑留痕。
3. T4 声明的"独立 Kotlin 2.3.21 编译 EXIT=0、JUnitCore 17 例连跑全绿"无仓内留痕——UNVERIFIED。
4. T6 JVM 32/32、T2/T9 JVM 定向、T1 观测测试：Gradle 执行证据归门禁转录，记录员未复跑。

**4E / JS（T2/T3/T4 遗留）**
5. **AWAITING_VERIFICATION 独立 UI 态 + "重新核实/放弃"路径**（feature/library 与 core/domain 热区，下批）——服务层双分支已修，UI 面未动。
6. **R1/R4 RELRO 矛盾收口**（T4 真实接入前置）：消费形态（发布 AAR 直接消费 vs 钉源码 NDK r28c 自建）悬置；源码重建若保留，理由只剩供应链钉版；契约 `visual-generation-contract.md:22` 的错误判据需按 R4/官方豁免改写；R1/R4 的 ABI 面（四 ABI vs abiFilters 裁两位）需在 T4/T8 前对齐。
7. **JS 方法不广告维持**：能力快照/工具面/提示词仍只声明 IMAGE_MODEL；4E.2/4E.3 未做（本批只落地基）；真实 QuickJS 引擎行为（.so 装载/沙箱/硬终止/内存上限）UNVERIFIED；真实接入时 issue #240（job 队列自续绕过协作中断）必须进仪器化用例（R1 §4 预登记）。
8. `OpenAiModelResponseParsers.kt` KDoc 与白名单不符（"purpose"实际在集合内且有值校验）——改注释不改行为，下批顺手修。

**T5 / 真实质量**
9. 真实 Provider 质量与费用：**UNVERIFIED**（无 key）；JS 后端留桩（NOT_WIRED）待 T4 端口接线；盲测 120 例未跑、门常量是启用目标非已达（PRD-09 退出口未过）。

**T6 / T7（beta 准备件差距）**
10. API 23–29 ANR 覆盖缺口（ApplicationExitInfo 需 API30+，看门狗/对账在该区间不工作）——如实登记不伪覆盖。
11. 零外发证明是结构层（manifest/权限/源码 token 清单），不做运行时抓包；异常消息理论携带数据的残余风险评低。
12. **T7 标识：G3 已落；G1（讲题界面提示）/G2（App 内生成图角标）P0 未落待下批（热区）；T7 不声称完成/已合规**；G4/G5 按义务清单登记下批；G7 未成年人叠加义务呈用户裁定；隐式标识未按 TC260 XMP 规范（GB 45438-2025）；G3 图注形态对《办法》第四条第二款的满足性挂条件核验；设备像素级图注文字未单独断言。

**T8 / 构建面**
13. **32 位设备（armv7/x86）从 abiFilters 生效起不可安装**——minSdk 名义 23 与有效机型面（64 位 only）收窄，产品呈报显式化（本记录 §3.8 与主线补修行已登记）。
14. 16KB 真机/模拟器装载冒烟未跑：本机 AVD 均 4KB 页 x86_64；`_ps16k` 图像包可下载（API 35–37.2，R4 §4.2 给命令），ps16k 装载冒烟按 R4 §6.3 留待后续设备轨；API23/arm64 真机矩阵未覆盖（宿主不可行）。
15. T8 终验判据须改官方两步法（zipalign -P 16 + check_elf_alignment.sh/等判据 ELF 解析含 RELRO 豁免）。

**T9 / T10 / 算法面**
16. T9 台账未接 UI（本批不接，未来 Wave X 用）；400 门只引用常量。
17. **KD-64（新登记）**：`NewIntroductionPolicy` 全超尺寸死锁分支 granted slice 可为负→IllegalArgumentException（T10 设备 2 红；JVM 未覆盖该形状）——域层修复不在设备轨授权，待实施轨。
18. T10 的 3E 设备用例 1 绿 2 红：修复前该用例面不闭合。

**待裁 / 他线 / 环境**
19. **G5 API 口径三裁定要点呈用户裁定**（A/C 取舍、Elo 状态台账惰性派生 vs 落表、探索额度数值）；R5 为提案不构成实现承诺；5B 验收门字面 vs 方向文本读法差异在裁定材料并列呈报。
20. 真实 Provider / Wave X 启用 / 5B 出题回收接线：均不在本批（计划已裁）。
21. `SmartMistakeBookRoot.kt` 他线动画改造引用未跟踪 `ClaudeMotion.kt`——提交必须排除；feature/*、core/ui、docs/current、tools/kb_*、knowledge-production 均他线在飞，本批未触碰。
22. 设备数字以本记录 §3.12 落盘为审计载体（协议不产 XML）；三个库 androidTest harness 包留存设备；`com.tingyun.smartmistakebook.localfirst(.test)` 已卸载。

## 7. 提交清单建议（记录员只读核对 git status 后列出；**提交由主会话执行**）

**前置条件**：强门 exit=0（+建议全量单测门复跑留痕）。本批文件实测 **79 路径**（67 代码/夹具/工具 + 12 文档），分块 add（≤6 路径/块）：

- **块 1（研究 R1–R4）**：`docs/research/2026-10-25-js-engine-selection.md`、`docs/research/2026-10-25-crash-feedback-selection.md`、`docs/research/2026-10-25-ai-content-labeling-compliance.md`、`docs/research/2026-10-25-16kb-arm64-api23-build.md`
- **块 2（R5 + 基线）**：`docs/research/2026-10-25-g5-adaptive-selection-proposal.md`、`docs/research/2026-10-25-batch-20-baseline.md`
- **块 3（T8 构建 + T1 观测）**：`app/build.gradle.kts`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureHttpTransportBypassObservationTest.kt`
- **块 4（T2 model 面）**：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/CapturedQuestionDocument.kt`、`core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/CapturedFigureReferenceTest.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelResponseParsers.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTaskAdapters.kt`
- **块 5（T2 data 面）**：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/CaptureParseFigureRequestParseTest.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/CaptureParseFigureDecisionTest.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomCaptureWorkflowRepository.kt`（整文件纳入：含 T3 hunk）
- **块 6（T3 契约/域）**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/CleanRedrawTaskContract.kt`（整文件纳入：保留他线 hunk）、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/CleanRedrawTaskEntity.kt`、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/CleanImageGenerator.kt`（整文件纳入：保留他线 hunk）、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/CaptureWorkflowRepository.kt`
- **块 7（T3 runner 面 + 装配）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/CleanRedrawTaskRunner.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/ConfiguredCleanImageGenerator.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/ExplicitRedrawService.kt`（含修复环 F3 修复 hunk）、`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt`（T3+T6 两轨 hunk）
- **块 8（T3 JVM 测试）**：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/CleanRedrawTaskRunnerTest.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/ExplicitRedrawServiceTest.kt`（含 F3 红测）、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/ConfiguredCleanImageGeneratorTest.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationWiringTest.kt`
- **块 9（T3 设备 + 夹具补登）**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/CleanRedrawTaskInstrumentedTest.kt`、`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/CaptureWorkflowInstrumentedTest.kt`、`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/CaptureWorkflowTestBase.kt`、`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationAuditFixtures.kt`（**复核 #9 补登**）
- **块 10（T4）**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt`、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/FakeVisualExecutionEngine.kt`、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngineContractTest.kt`
- **块 11（T5 夹具/脚本上）**：`tools/figure_eval/tasks_blind_120.json`、`tools/figure_eval/tasks_dev_24.json`、`tools/figure_eval/freeze_manifest.json`、`tools/figure_eval/budget.json`、`tools/figure_eval/build_frozen_tasks.py`、`tools/figure_eval/figure_eval_gate.py`
- **块 12（T5 测试/报告下）**：`tools/figure_eval/test_figure_eval_gate.py`、`tools/figure_eval/README.md`、`tools/figure_eval/reports/figure_eval_fake_run_report.json`、`tools/figure_eval/reports/figure_eval_fake_run_report.md`
- **块 13（T9）**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LongRangeSampleLedger.kt`、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/LongRangeSampleLedgerTest.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/LongRangeSampleLedgerReadTest.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewLogSink.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepository.kt`、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/StudyExperienceRepository.kt`
- **块 14（T10）**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlanner3EScanInstrumentedTest.kt`
- **块 15（T6 主体上）**：`app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics/DiagnosticEvent.kt`、`.../diagnostics/DiagnosticEventJson.kt`、`.../diagnostics/CrashLedger.kt`、`.../diagnostics/CrashHandler.kt`、`.../diagnostics/AnrWatchdogMonitor.kt`、`.../diagnostics/StartupExitReconciler.kt`
- **块 16（T6 主体下 + UI）**：`app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics/DiagnosticsBundleExporter.kt`、`.../diagnostics/DiagnosticsBootstrap.kt`、`app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt`
- **块 17（T6 测试上）**：`app/src/test/kotlin/com/tingyun/smartmistakebook/diagnostics/DiagnosticEventJsonTest.kt`、`.../diagnostics/CrashLedgerTest.kt`、`.../diagnostics/CrashHandlerTest.kt`、`.../diagnostics/AnrWatchdogMonitorTest.kt`、`.../diagnostics/StartupExitReconcilerTest.kt`、`.../diagnostics/DiagnosticsBundleExporterTest.kt`
- **块 18（T6 测试下 + T7 主）**：`app/src/test/kotlin/com/tingyun/smartmistakebook/diagnostics/DiagnosticsZeroEgressTest.kt`、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt`、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfPagePlanner.kt`、`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRenderer.kt`
- **块 19（T7 测试）**：`core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportVisualRoleTest.kt`、`core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaptionTest.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureProvenanceTest.kt`
- **块 20（记录 + 五处同步）**：`docs/research/2026-10-25-batch-20-record.md`、`docs/REFACTOR-MASTER-LINE.md`、`docs/agent-first-refactor-decisions-2026-09-23.md`、`docs/known-defects.md`、`docs/research/algorithm-version-ledger.md`、`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md`

**明确排除**（他线在飞，一律不动）：`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt`（他线动画改造引用未跟踪 `core/ui/.../ClaudeMotion.kt`）；app 其余 5 个开工即脏 M（LearningMasteryScreen/ReviewReminderCoordinator/ReviewReminderScreen/SchedulingSettingsScreen/StartupStateBanner）；feature/tutor|library|review|capture 全部；core/ui 全部（含 ClaudeMotion.kt 与其 androidTest）；core/data 他线 M（RoomMistakeDetailRepository.kt、RoomTutorToolRunner.kt）；core/export 他线 M（MistakeExportJobRunner.kt、build.gradle.kts、MistakePdfEligibilityTest.kt、MistakePdfPagePlannerTest.kt、MistakePdfPlannerGoldenTest.kt）；core/domain 他线 M（BatchImportRepository、MistakeDetailRepository、TutorJudgedReview 等）；settings.gradle.kts（:core:markdown）、.gitignore（.superdesign/tmp）；docs/current/**、docs/superpowers 他线改动、docs/research/ 他线未跟踪记录（2026-10-25-kb-final-acceptance-criteria-v1.md、2026-10-25-ui-completion-audit.md 等）；tools/kb_*、knowledge-production/**、`.agent_*` 审计杂物。

**安全边界**：Mimosa 无完整扫描结论，不宣称项目安全；本文件不含任何密钥/令牌/凭据；提交分块建议由主会话执行，非快进 merge 不 rebase。

## 附录：本记录员实际执行的只读核对（可复跑；未运行 Gradle/设备/任何 git 写命令）

```bash
# 计划/基线/批19记录/五研究文档全文或头部读取（Read/head）
git rev-parse HEAD origin/main   # 两值同为 558185c9e2945c52949d265645a23da0fa22521d
git status --porcelain=v1 | wc -l           # 660
git status --porcelain=v1 | grep -c "^ M"   # 190

# 十轨+设备轨+补登夹具逐文件 git status 对上（§3 与 §7 清单来源；全部 M/?? 与声明一致，零虚指）
git status --porcelain=v1 -- <轨文件路径清单…>   # T1–T10 全部对上；FigureGenerationAuditFixtures.kt=??（补登）

# 待同步文档开工时状态（全部 clean，安全编辑）
git status --porcelain=v1 -- docs/REFACTOR-MASTER-LINE.md docs/agent-first-refactor-decisions-2026-09-23.md \
  docs/known-defects.md docs/research/algorithm-version-ledger.md docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md
#   → 无输出（clean）

# 零 bump 判据（算法台账 §3.30 输入）
git status --porcelain=v1 -- core/database/schemas/ core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ReviewPlannerV2.kt \
  core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LearningCoreVersions.kt \
  core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AdaptiveQuestionSelector.kt app/src/main/AndroidManifest.xml
#   → 无输出（全部 clean）；grep -rn quickjs --include=*.kts --include=*.toml（排除 build/）→ 0 命中

# F3 修复在树核验
sed -n '240,275p' core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/ExplicitRedrawService.kt
#   → :263-267 AWAITING_VERIFICATION 双分支（inFlightExecutions→Accepted / 否则 Rejected("AWAITING_VERIFICATION")）

# KD 编号空间 / 研究文档章节锚点 / PRD-09 计划节 / 主线插入点
grep -n "^## KD-" docs/known-defects.md | tail -4   # 最高 KD-63 → 新缺陷登记为 KD-64 起
grep -n "^## \|^### " docs/research/2026-10-25-*.md # R3 §9.1/§9.2、R4 §1.5/§2.4/§5、R1 §2.1 等复核引用锚点存在
```

> 未执行：任何 `./gradlew`、`adb`、`am instrument`、`connectedAndroidTest`；任何 git 写命令。门禁/设备/修复环结论均标注来源（工单材料/设备轨 evidence/修复环材料/复核材料），不冒充本记录员实跑。

## 8. 主会话收口（2026-10-25，实跑证据）

> 本节由主会话在提交/推送前实测补记；§6"工程门"三项前置全部达成，提交 `29b46f21` 已落地。

- **单测门全量复跑**（11 模块 `--continue`，主会话实跑）：**exit=0**（BUILD SUCCESSFUL，修复环后的树未再变，227 任务全 UP-TO-DATE——修复环复跑 exit=0 的留痕成立）。
- **强门**（六 androidTest 编译 + `:app:assembleLocalFirstDebug`）：**exit=0**（BUILD SUCCESSFUL，10 executed / 261 up-to-date）。
- **提交**：`29b46f21`（79 文件，24,354 插入/158 删除），按 §7 的 20 块分块 add；**排除项未触碰**（Root、app 其余 5 开工即脏 M、feature/\*、core/ui、他线 core:data/export/domain M、settings.gradle.kts、.gitignore、docs/current、tools/kb_\*、.agent_\* 杂物）。
- **推送**：GitHub 443 短时不可达（连接重置 + 连接超时共 6 次），第 7 次重试成功——**两笔已上 origin/main：`247f5e2c..fa817ad6`**（代码 `29b46f21` + 本文档尾 `fa817ad6`）。
- Mimosa 提交仍无完整扫描结论（scanner_enobufs）——不宣称项目安全。
- **待用户裁定项提醒**（本批研究产出，未裁不动）：① G5 选项 A/C 取舍 + Elo 状态落表与否 + 探索额度（R5 文档）；② T4 消费形态（quickjs-kt 发布 AAR 直接消费 vs 钉源码 NDK r28c 自建）+ RELRO 豁免判据回写契约；③ 32 位设备（armv7/x86）不可安装的机型面收窄（T8 已按研究结论落地 abiFilters，本条为知悉性呈报）；④ G7 未成年人标识叠加义务（R3）。
