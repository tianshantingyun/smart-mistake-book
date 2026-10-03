# 阶段 4A · 错题本与录入 完成记录 · 2026-10-03

> 依据：主线 4A 行（`docs/REFACTOR-MASTER-LINE.md`）、phasing（`docs/superpowers/specs/2026-09-25-agent-first-refactor-phasing.md:38`）、
> L1–L7 定稿裁定（`docs/agent-first-refactor-decisions-2026-09-23.md:1058-1068`）、阶段计划 `docs/research/2026-10-03-stage4a-plan.md`（`af530add`）。
> **用户 2026-10-03 四项裁定**：下一阶段=4A；L1–L7 全做、四批；仅 S17 随 4A（S18/S20 留 3C 尾）；L7 取 **A 形态**（应用内落盘 + 完成通知 + 成果入口）。
> 实施方式：多 Agent 协作（工作流线因运行时沙箱挂死停档——脚本已按"禁止空跑步骤"修好留待重启后复用；见 §6）。

## 1. 交付与提交（四批）

| 批 | 提交 | 内容 | 版本面 |
|---|---|---|---|
| 批 1 | `857f4d09` | **L1** 删 TRASHED 死枚举三处 + 文案；**L2** 标题读侧单源 `revision.title`（视图/ProblemDao 两处，写侧双列与列不动）+ 分叉库态单源证明；**L5** LibrarySort 收敛两值（默认 RECENTLY_UPDATED）、删知识点筛选层、新增「录入时间段」（闭区间 [now−N, now]，UI 档位上下界生产化）；**S17** `observeActiveMistakes` 派生表 + LEFT JOIN 聚合（新旧 SQL 逐列等价） | schema **59→60**（视图重建，不动表行）；零算法 bump（台账 §3.16） |
| 批 2 | `7f109251` | **L3** 删离线自由文本编辑器；在线编辑器接「从知识树选择」（既有 `observeOrganizationOptions`/KB 读口 1..256）；Applied 与 PreservedUserCorrection **两态共用同一修改入口**（修掉复核 P1 可达性回归）；**L4** 录入界面新增「待处理题目」（`observePendingCaptures` 8 态 + 恢复两路 + 废弃 CAS 事务 `abandonPendingDraft`，标记不物删）；BatchImport 文案落点归一 | 零 schema |
| 批 3 | `f4ba32c5` | **L6** 单一 `Routes.capture(origin)`（未知来源 fail-fast）+ `CaptureEntryModeChooser` 方式选择（错题本=拍照/相册/整卷/PDF；讲题=拍照/相册）；错题本两并列入口 → 一个「录入」（含空态）；大厅/历史会话同指；批量/拆分复核为录入内部步骤；pipeline 零改动；文案统一（「整卷/PDF」为如实命名——全仓无 `OpenDocumentTree`） | 零 schema |
| 批 4 | `e89727ee` | **L7（A 形态）** `ExportPdfWorker`（expedited `CoroutineWorker`，不引入 FGS 类型；就绪门只挡 Initializing/FatalFailure；放弃必落 FAILED + 失败通知；僵尸 RUNNING 1h 对账）+ 完成通知（未授权不发不请求，退化到入口；深链 → hub）+ 「导出成果」入口（分享/保存/打印复用既有 `MistakePdfDelivery`）；渲染核心抽 JVM 可测单元；删除旧前台导出页；顺带修「超限谎报没有题」 | schema **60→61**（导出记录表）；零算法 bump（台账 §3.17） |

## 2. 退出门（phasing 原文三条）

| 门 | 结论 | 证据 |
|---|---|---|
| **列表与详情同源** | ✅ | 视图/迁移/`60.json` 三源逐字一致（复核逐字节比对）；`MistakeDetailDatabaseInstrumentedTest.catalogAndDirectoryReadRevisionTitleWhilePracticeUnitTitleStaysLegacy`（真实分叉库态：先断言 `practice_unit.title` 仍为旧值，再断言读者读 `第二版标题`）；旧 `unit.title` 残留读者仅 `PracticeUnitAssessmentDao`（讲题工件标题，登记为 L2 尾巴） |
| **录入一条流** | ✅ | 全仓无 `CaptureTutor`/`CaptureLibrary`/`library_batch_import` 导航来源；`RootNavigationPolicyTest`（路由解码 fail-fast 含 `assertThrows`）+ `CaptureEntryNavigationInstrumentedTest`；**真机走查**：错题本「录入」（三方式 + 待处理区）→ 大厅「录入并讲解」（两方式、无整卷/PDF）→ 整卷/PDF 步骤（「整卷录入」页）三张截图；第 4 类（历史会话空窗帧）登记 UNVERIFIED |
| **导出不随页丢** | ✅ | `MistakeExportBackgroundInstrumentedTest.exportIsStillRunningWhenThePageIsLeftAndCompletesAfterTheGateOpens`（可控闸门：离开时仍 RUNNING → 销毁详情页 → 放开闸门 → SUCCEEDED）+ `exportCompletesAfterTheDetailingPageIsLeftAndStaysRetrievable`（终态 + `reopenVerified` 真读 `%PDF` 头）+ `ExportPdfDriverInstrumentedTest`（真 WorkRequest 反射断言 expedited=true 与降级策略）+ 通知两态 + 深链消费点 |

## 3. 终门（最后代码状态，2026-10-03）

| 门 | 结果 |
|---|---|
| 全量 JVM（`test --continue --rerun-tasks`） | **2234 / 0 / 0** |
| core:database 仪器化全套（冷启模拟器） | **208 / 0 / 0**（矩阵 **1→61** 含 v60→61 带数据；迁移/契约/记录表/问题整理/性能门 6/0） |
| feature/library 仪器化全套 | **36 / 0 / 0**（含 hub 5 例） |
| app 全量仪器化 | **53 / 0 / 0**（含后台导出/通知/交付/深链/导航迁移类） |
| R8 冒烟（localFirst release，debug keystore 口径） | `assembleLocalFirstRelease` 成功（含 lintVital）；装机 **Success**、启动 **PID 8205**、logcat 零 `FATAL EXCEPTION/AndroidRuntime/ClassNotFound/NoSuchMethod/NoClassDefFound`；首屏 = 复习根态（截图 `build/4a-final-r8-smoke.png`） |

## 4. 独立复核（逐批）

| 批 | 结论 | 处置 |
|---|---|---|
| 批 1 | approve-with-notes | 复核员独立做 **S17 新旧 SQL 的 SQLite 差分模糊测试**（400 组随机夹具 + 5 对抗形态）与三源 SQL 逐字节比对；订正台账两处措辞（S17 覆盖分支、两值排序"互逆"）、时间段上界生产化（`createdTo=now`）、L2 尾巴与 S17 性能 UNVERIFIED 登记、计划引用订正（KD-7 误引） |
| 批 2 | approve-with-notes | **P1 可达性回归**（改过一次分类后重进详情不可再改）→ 抽 `OrganizationCorrectionPanel` 两态共用 + 「重进仍可改」仪器化；弹窗「已选/分组/章节路径」补断言；tutor-ready 废弃映射补用例 |
| 批 3 | approve-with-notes | **P1 假承诺**（「文件与目录」无目录能力）→ 改「整卷/PDF」+ 计划措辞订正；tutor 三处命名统一；KDoc「不可达」口径订正为「空窗帧」 |
| 批 4 | approve-with-notes | **P1 静默放弃**（RecoverableFailure 下 worker 三次重试后不落 FAILED、记录永久"正在整理"）→ 就绪门收紧 + 放弃必留痕 + 僵尸对账；真 expedited 反射断言、深链消费点断言、「离开瞬间仍在进行」可控闸门、死常量清理、预览面退场登记等 7 项 P2 全处置 |

## 5. 登记与遗留（不属 4A / 明示边界）

- **S17 性能未量化**（无专属 EXPLAIN/负载门；`PerformanceGateTest.insertTestData` 为既有空实现）——UNVERIFIED。
- **L2 尾巴**：`PracticeUnitAssessmentDao.kt:49` 的 `unit.title` 读者（讲题工件标题）在批 1 锚定范围外，双写成立时无分叉；将来「改写既有 revision」落地时需一并收口。`practice_unit.title` 列删除 = 死面候选（枢纽 + RESTRICT 子表重建风险，计划 §5②）。
- **S18/S20 留 3C 尾**（roadmap「与阶段 4 联动」注记被推迟，此处登记）。
- **知识树选项上界 256**（既有 KB 读口；MATH 真实包约 930 原子节点，超出只能手输补充）——显式边界，写进实现 KDoc。
- **废弃不回收存储**（`abandonPendingCapture` 只标记 ABANDONED，与 replace/split/endTurn 同口径；孤儿原图不回收）——既有 GC 语义未动。
- **A4 预览面退场**：`MistakePdfPreview` 随旧前台导出页删除后生产零消费者（台账 §3.17 登记；交付链覆盖仍在 core:export 仪器化）。
- **导出人工走查未闭环（UNVERIFIED）**：真实 OS 通知点击与真实 SAF 保存的人工走查未做——模拟器无模型配置，无法在无模型环境下造出库内题目（录入→整理需模型；首启知识包安装亦占用库连接）。可自动化环节已由真机仪器化覆盖（见 §2 第三条）。
- **首启知识包安装的观测**：`-wipe-data` 后首启有大批 `knowledge_search_feature` 写入 + SQLite 连接池 30s 等锁 + 高频 GC（≥1 分钟）；`Initializing` 态正是批 4 就绪门所服务的状态。

## 6. 工作流线附录（本阶段未启用但已修好）

- `dwfrun-74cdfb4a`（会话模型）与 `dwfrun-200a242d`（换 `c6d1a55c-…` 通道）两次投运均"executing 但 0 token/无文件改动/无进程"；停运行后连 `EvalWorkflowSnippet` 最小片段（单次 files.glob + git.status）也 45s/120s 超时 ⇒ **工作流沙箱执行层挂死**（详见记忆 `workflow-engine-model-creation-failure`）。
- 脚本已按用户规则"**禁止空跑步骤**"修好并留档 `.zcode/workflow-drafts/stage4a.dwf.ts`：每实施步带**先行条件**（锚点精确路径存在）与**完成条件**（清单非空/路径真实/工作树相对基线有真改动、不达标回灌补正）、设备先行条件覆盖四批、空修复/空提交/空相位全部设守；**重启 ZCode 后可用 `AmendWorkflow` 重投**（已结算内容免费）。
- 用户裁定：4A 以**多 Agent 协作**推进（本记录即其产物）。

## 7. 剩余风险

- **导出链的真实平台交互**（通知点击、SAF 保存、打印输出、API<31 前台路径）未在人工走查中闭环——发布前建议在有模型/有数据的真机补一次整屏走查。
- **渲染重构**（PagePlanner/Renderer 抽取）由复核逐行比对为行为等价（构造成立），但**无逐位 golden 摘要**——如需该级别的门需另建产物对拍。
- **排版/生图（4B）与 S18/S20（3C 尾）**不在本阶段范围。
