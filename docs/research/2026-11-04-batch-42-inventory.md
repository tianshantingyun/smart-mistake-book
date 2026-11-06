<!-- 体积自查：371 行 / 32,084 B；R4 上限 400 行 / 65536 B，未超。 -->
# 批次 42 · WS11 计划收口：剩余项盘点 / 分派 / §0 口径 / 批次 43 草案

| 字段 | 值 |
|---|---|
| evidence-id | `B42-WS11-INVENTORY-01` |
| executed-at | 2026-11-03（本机时间；文档名按计划批次 42 的 2026-11-04） |
| head | `faab17f58b01eab78d5c08a9f843b3af3df2fecf`（本项零 git 写） |
| 输入 | 计划 `.zcode/plans/plan-2026-11-04-batch-42.md`（§1 研究 6 / §2 实施 74 / §3 流程 20）；批次 41 记录 §9.3；本批基线 `2026-11-04-batch-42-baseline.md`；本批各轨证据 `evidence/2026-11-04-batch-42/` |
| 本项实跑 | `python -I` 解析计划编号；`git rev-parse/status/diff --stat/ls-files`；`grep -rln/-c` 约 20 条零命中/命中核对；`sed/head` 读码 |
| 本项未跑 | 任何 Gradle、任何 adb/设备、任何 git 写、任何 Provider 调用 |
| 声明 | 本文件只做**只读盘点与文档**。凡写“已落”只表示载体在树；执行结论一律标来源（本批 E 文件或转录），不写成“本项实跑通过”。 |

---

## 0. §0 口径（供批次 42 记录 §0 直接取用）

### 0.1 计划项数与实列编号（**与计划自述不符**）

计划首行写“本批 **100 项**（研究 6 + 实施 74 + 流程 20）”。本项用 `python -I` 按行首 `^\d+\. ` 解析（实跑）：

```text
count 90 first [1, 2, 3] last [86, 87, 88, 89, 90]
missing []
WS1 12 [1] [12]   WS2 12 [13] [24]   WS3 9 [25] [33]   WS4 8 [34] [41]
WS5 6 [42] [47]   WS6 8 [48] [55]    WS7 5 [56] [60]   WS8 10 [61] [70]
WS9 5 [71] [75]   WS10 5 [76] [80]   WS11 4 [81] [84]  WS12 6 [85] [90]
```

**实列 90 个连续编号（1..90，无缺号），不是 74。** 因此计划实际账为 **6 + 90 + 20 = 116**，与自述 100 差 16。§3 流程“20”的构成（11 项 + 9 个各 WS 内互斥子件）与计划原文一致，不动。

记录口径建议（不替记录员合并）：

- 实施面按 **90 行逐编号对账**（WS1 12 / WS2 12 / WS3 9 / WS4 8 / WS5 6 / WS6 8 / WS7 5 / WS8 10 / WS9 5 / WS10 5 / WS11 4 / WS12 6）。
- 把“74”登记为**计划口径缺陷**（与批次 41 记录 §5 F1 同族：计划自述 55、实列 62），不改计划文件、不把 90 压成 74。
- 汇总行写：`研究 6 + 实施 90（计划自述 74）+ 流程 20 = 116（计划自述 100）`。
- **§7 提交清单不在本项范围**：提交块必须门禁后逐 hunk 核对（热区 9 M、`docs/current` 7 M、14 D 的搬移配对、他线测试 hunk 均需排除），由记录员/主会话按 `git status` 时点值生成，本项不预填。

### 0.2 本批基线事实（本项实读；执行性结论另注来源）

| 项 | 值 | 证据 |
|---|---|---|
| HEAD | `faab17f5…` | 本项 `git rev-parse HEAD` |
| origin/main | 本地 ref 同值；**远端相等未证实** | 基线 §1.2 `git ls-remote` 两次 `curl 28` 失败 |
| 工作树 | **642 条**（14 D / 166 M / 462 ??；时点值，会话内仍在漂） | 本项 `git status --porcelain \| wc -l` + `cut -c1-2 \| sort \| uniq -c` |
| 14 个 D | `core/data/.../pdf/` 7 主文件 + 7 测试 = WS2 按裁定 B1' 纯搬移到 `core/model/.../pdf` | 本项 `git status --porcelain \| grep '^ D'`；WS2 证据 §1 第 1–2 行 |
| DB schema | 73（92 表 / 2 view / `1d086050…`） | 基线 §1.4 实读 `73.json` |
| 请求 schema | 19 | 基线 §1.4 实读 `ModelTasks.kt:566` |
| 备份选择 mask | **{0, 8, 9, 13, 15}**（本批 WS6 重裁） | 本项实读 `BackupSelection.kt:70,92`；`SmbkSelectionPlan.kt:97,635` |

### 0.3 热区现状：**已从计划 §0 的“5 M”扩到 9 M（代码）+ 7 M（docs/current）**

计划 §0 与基线 §2.1 都写“热区现仅 5 M”。本项实读（`git status --porcelain -- core/domain core/ui core/export feature docs/current settings.gradle.kts`）：

| 热区面 | 当前 M | 说明 |
|---|---|---|
| `core/domain` | **4**：`TutorJudgedReview.kt`（11-01 旧 M）、`BackupRepository.kt`、`BackupSelection.kt`（+14/−11）、`KnowledgeResearch.kt`（mtime 11-03 09:04–10:35） | 后三者是本批新出现的 M；`BackupSelection` 的 diff 含 6C-2 重裁。**与“core/domain 禁改”纪律不一致，本项只登记，不裁定** |
| `core/export` | **4**：`MistakePdfExporter.kt`（+89，本批 WS2 授权 hunk）、3 个测试 M（他线内容，基线 §2.1 已注） | 授权范围只覆盖 `MistakePdfExporter`；`MistakePdfEligibility.kt` 仍零 M |
| `core/ui` | 1：`Theme.kt`（旧 M） | 非本批 |
| `feature/*` | 0 代码 M；仅 6 个 `gradle.lockfile` + `feature/tutor/.../nul` | 锁文件不得提交 |
| `docs/current` | **7 M**（+99/−5） | 契约正文仍不得落笔；WS9 只出 patch（`git apply --check` exit=0，未应用） |
| `app` 主码 | 3 M：`ReviewReminderCoordinator.kt`、`SecondaryScreens.kt`、`visualjs/Kd76ReportVerdict.kt` | 前两者属他线/热区；`Kd76ReportVerdict.kt` 是本批 F11 修复 |

结论：**“热区 5 M”是批次 42 开工时点值，现树已不成立**；任何按“热区仅 5 M”写的排期或提交块都必须重核。

---

## 1. 判读规则

- **状态**：`已落` = 载体在树且有可指认证据；`部分` = 主链在树但有明确缺口；`未落` = 无载体；`阻塞` = 前置缺失/需裁定/需外部资源。
- **分类**（本项要求的“可做/热区/外部阻塞”三值，外加两个必分的旁支）：
  - **可做**：落点不在热区、无外部依赖、本线可独立推进。
  - **热区**：终态落点命中 `core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、`Application/Root`（本批纪律明令禁改）。
  - **外部**：需外部资源（真实 Provider 凭据、签名密钥、arm64 硬件、第三方接收 App、Wave X 数据）。
  - **需用户**：需用户/主会话裁定或合规路径决定，执行者不得代签。
  - **他线**：前置在他线（KB/D-4、3D 硬门、宿主低负载窗、阶段 7 顺序）。
- 每条剩余项只给**一个主分类**；跨类事实写进备注，不重复计数。

---

## 2. PRD（需求簇 R1–R11 → 跨阶段任务 PRD-01～11）

> 权威源：`docs/requirements-fused-final-2026-10-11.md`（R1–R11）；任务身份与勾选框：`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:36-157`（**全部未勾**——完成定义要求事实/投影/入口/失败路径四闭）；阶段状态：`docs/REFACTOR-MASTER-LINE.md:94-103`。
> PRD-02 无独立剩余项（KD-43 已 closed；整卷/改编扩展归 5B）；PRD-09 归 4E；PRD-10 是承接声明，拆到 6C/5B/5b/5c。

| ID | 剩余项 | 现状证据 | 状态 | 分类 |
|---|---|---|---|---|
| PRD-A1 | PRD-03 编辑→杀进程→重开→复习→导出 版本一致性设备全链 | `MistakeRevisionEditInstrumentedTest` 2 例（`editIsDurableAcrossReopenAndKeepsOldRevisionAndHistoryReadable`、`invalidEditIsRejected…`，本项实读 :42/:135）+ `ProblemRevisionEditDatabaseInstrumentedTest` 在树；MASTER §12 记 PRD-03 设备 5/0+2/0，但“杀进程/再复习/再导出”三段无设备记录 | 部分 | 可做 |
| PRD-A2 | PRD-03 A/B 并发编辑只接受 `expectedRevision` | 拒绝路径有设备例（`invalidEditIsRejected…`）；**A/B 两路并发**的独立设备例未见（验收原文 followup-plan:68） | 部分 | 可做 |
| PRD-A3 | PRD-04 真实接收应用打开 DOCX/PDF（≥1 次） | 验收原文：“仅弹分享面板不等于接收端拿到文件”（:79） | 未落 | 外部（无第三方接收 App） |
| PRD-A4 | PRD-06 首装四态 + 权限三档设备例 | 能力半边已落（`TutorCapabilitySnapshot`，MASTER §12 B）；四态/三档无整组设备证据 | 部分 | 可做 |
| PRD-A5 | PRD-07 备份性能门（p95 ≤2× 基线、无单次 >5 s 占锁） | 验收原文 :114；`PerformanceGateTest` 是**库检索**性能门（`core/database/.../PerformanceGateTest.kt:20-25`），不是备份门 | 未落 | 可做 |
| PRD-A6 | PRD-08 真实进程重建（旋转/回收/强停三分） | JVM 8/0 已记（MASTER §12 B）；真实进程重建未跑（同处明写） | 部分 | 可做 |
| PRD-A7 | PRD-11 本地任务线补“修改”步骤 | 批次 19 设备实跑 5 步，明写“「修改」步骤未覆盖”（followup-plan:153） | 部分 | 可做 |
| PRD-A8 | PRD-05 / PRD-11 学习线真实数据启用 | 3D 硬门 D-2/D-3/D-5 未共同收口（MASTER :89）；PRD-05 设备 24/0 只证隔离库 | 阻塞 | 他线（3D） |
| PRD-A9 | PRD-01 启用检查/状态页“未开放”登记 | 要求“测试库隔离 + 状态页明确未开放”（followup-plan:40）；状态页在 `docs/current`/feature | 未落 | 热区 |

**PRD-09（统一生图质量/提示词）**：harness 在树（120 盲测 + 24 开发集冻结、机制测试 30 passed，followup-plan:137-139）；真实 Provider 质量与 120 例盲测未跑 → 归 4E 行，不重复计数。

---

## 3. 6C（备份与恢复扩展）

> 权威源：`docs/research/2026-10-11-new-stages-spec.md:126-148`、`:202-209`；MASTER `:103`；设计件 `2026-10-17-6c2-content-selection-design.md` §9.9/§9.11。

| ID | 剩余项 | 现状证据 | 状态 | 分类 |
|---|---|---|---|---|
| 6C-A1 | 6C-2 重裁（0/8/9/13/15）落码后的 JVM + 设备执行 | 本批 WS6 落码；证据明写“本轮未跑 Gradle/设备（UNVERIFIED）”（`2026-11-04-6c2-rulings.md` 头部）；`allowedMasks` 已改为 5 值（本项实读 `SmbkSelectionPlan.kt:97,635`） | 部分 | 可做 |
| 6C-A2 | 6C 承接 WG-08：视觉程序/快照/共享 operation·attempt/预览/字体版本/canonical 字节纳入闭包 | spec:145-148 四条未勾；`SmbkSelectionPlan` 现有类别位仍是 4 类（题/图/会话/掌握） | 未落 | 热区（类别位在 `core/domain/BackupSelection.kt`） |
| 6C-A3 | 设计件 §9.9 五项未决中的三项：类别文案 / 无图子集 / 合并恢复 | 基线 §5.3 实读 :1610-1615“本轨不代签”；另两项（治理台账归类、VACUUM 耗时）可做 | 阻塞 | 需用户 |
| 6C-A4 | 设计件 §9.9 另两项（治理台账归类、VACUUM 耗时）落文档 | 同上；纯文档/测量 | 未落 | 可做 |
| 6C-A5 | v5→73 矩阵显式入证据 + W10-2 合成 v4 用例装置修复 | WS10 记 v5→73 10/11，唯一红是 `open().close()` 不触发 Room 懒开库（`ws10-device-full.md` §1.2/W10-2） | 部分 | 可做 |
| 6C-A6 | 跨阶段 7 迁移链重置后的旧包恢复 | spec:209 要求“最终加跨阶段 7 迁移链重置的旧包恢复”；阶段 7 未开工（MASTER :104） | 阻塞 | 外部（顺序） |

---

## 4. 4E（统一生图与 JS 后端）

> 权威源：`docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md:19-23`（4E.1/2/3 定义）；准入清单**附录 O** 69 行（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:1135` 起）：**✅20 / ◐35 / ✗10 / ?2 / ⛔1 / §1**，本批仅 R-03 `?→◐`，**0 项转通过**。
> 4E.1（共用 policy/coordinator/ledger + IMAGE_MODEL）已代码级落地；真实质量门属 PRD-09/外部。4E.2/4E.3 是以下剩余项的主战场。

| ID | 剩余项 | 现状证据 | 状态 | 分类 |
|---|---|---|---|---|
| 4E-A1 | P-01 isolation 配对/低负载复跑 | 本批 WS4：6 run×15 例全 `-4`、自证 `own_cpu_busy`、判词 UNCONVERGED（`ws4-isolation-causality.md` §5–§7） | 阻塞 | 他线（宿主并发/低负载窗） |
| 4E-A2 | P-04 29.5 s 链 C4 段实测 | C1 p95=1672 / C2 p95=312；C3 仅 witness、C4 UNVERIFIED（`ws3-kd76-20-rounds.md`） | 部分 | 可做 |
| 4E-A3 | P-08 隔离装配 dumpsys 审计 | guard 在 `SmartMistakeBookApplication`；无 dumpsys 审计记录 | 部分 | 可做 |
| 4E-A4 | P-09 生命周期限次重调度 + p09 设备红归因 | WS10 `p09` `Tests run: 1, Failures: 1`（已知红，未归因，W10-3） | 部分 | 可做 |
| 4E-A5 | P-10 fallback/加载态 UI 半边 | 死亡未确认 1 例设备绿（批次 38）；fallback UI 缺（附录 O P-10） | 未落 | 热区 |
| 4E-A6 | P-11 晋升 `core/domain` | 消费点在 core/data；提级未做（附录 O P-11） | 未落 | 热区 |
| 4E-A7 | P-12③ 熔断解除直接断言 + 客户端熔断专项 | `fusedDigests` 预检/复查/解除在树；专项缺（附录 O P-12） | 部分 | 可做 |
| 4E-A8 | S-01 VisualScene*/Compiler/StateCodec 五类名 | 本项复跑 `grep` = **0** | 未落 | 需用户（4E.3 范围裁定） |
| 4E-A9 | S-03 `VisualSceneValidator` | 本项复跑 = 0；引擎仅保守校验 | 未落 | 可做 |
| 4E-A10 | S-05 交互层命中消费 | `visual-compiler.js` hitRegions 透传；消费未落 | 未落 | 热区 |
| 4E-A11 | S-06 / X-01 `:visual_js` 独立模块归位 | 本项复跑 `grep visual settings.gradle.kts` = 0；过渡落点 `core:data.visual` | 未落 | 热区（settings） |
| 4E-A12 | S-07 VisualSceneCard/Controls/Semantics | 本项复跑命中 1（仅测试 KDoc） | 未落 | 热区 |
| 4E-A13 | S-09 隔离引擎 JVM 半边 | `DEEP_FREEZE_JS` 在树；JVM 隔离引擎缺 | 部分 | 可做 |
| 4E-A14 | M-01 度量端口提级 | artifact 含 `inkBounds`；端口提级未做 | 部分 | 热区（core/domain） |
| 4E-A15 | M-03 4 MP / 16 MiB 两门 JVM+设备复核 | `VisualLayoutBudget.kt:52,58` 接线；未复核 | 部分 | 可做 |
| 4E-A16 | D-01～D-06 六组设备向量（同事务/取消/GC/进程重建/离线打开） | 附录 O D 组 6 行全 ◐；设备向量缺 | 部分 | 可做 |
| 4E-A17 | E-01 `VisualSnapshotPdfBlock` | 本项复跑 = **0** | 未落 | 热区（core/export 非 exporter 文件） |
| 4E-A18 | E-02～E-05 导出 role 过滤/0 派发/双端金样 | 附录 O E 组 4 行 ✗ | 未落 | 热区（core/export） |
| 4E-A19 | A-07 上下文预算负向测试 | 类型在、生产零构造；负向未建 | 部分 | 可做 |
| 4E-A20 | A-10 “耗尽→文字降级”编排半边 | 预算常量在树；编排缺（附录 O A-10） | 部分 | 可做 |
| 4E-A21 | A-11/A-12 扩测 | 套件 21 / 46+7 @Test 实测；扩测未做 | 部分 | 可做 |
| 4E-A22 | Q-04 设备截图臂 | `q04/` 15 文件；设备臂缺 | 部分 | 可做 |
| 4E-A23 | Q-05 真实 Provider 120 盲测/质量门 | 无凭据；120 例未跑（PRD-09 注） | 阻塞 | 外部 |
| 4E-A24 | Q-06 JS UI fontScale 矩阵 | JS 无 UI；矩阵缺 | 未落 | 热区 |
| 4E-A25 | V-01④ PDF 腿设备证据 | app 套件 4/4 绿（批次 39）；④ 无本批设备证据 | 部分 | 可做 |
| 4E-A26 | V-02 生产接线（确认卡/补题入口） | `FigureMethodSwitchRunner` 在树；生产调用方 0 | 未落 | 热区 |
| 4E-A27 | V-03/V-04 设备执行 | 载体在树；设备 UNVERIFIED | 部分 | 可做 |
| 4E-A28 | R-01 API 23 全套件 | 附录 O `?`；仅 KD-77 最小冒烟（已 closed） | 未落 | 可做（`api23_smoke` AVD 在盘） |
| 4E-A29 | R-02 arm64 运行时 | 本机无 arm64 设备/宿主 | 阻塞 | 外部 |
| 4E-A30 | R-03 arm64 16 KB | x86_64 16 KB 已 ◐；arm64 结构性拒载 | 阻塞 | 外部 |
| 4E-A31 | R-04 Keeper K1–K6 + `settings` 裁定 | 配置层可执行（隔离副本 `:app:tasks` SUCCESSFUL）；K1–K6 未跑；关闭前置=主会话两合规路径裁定 | 阻塞 | 需用户 |
| 4E-A32 | R-05 许可快照入仓 | 328/340 坐标取到 license（`build/ws8-batch42/*.tsv`，gitignored）；tracked 载体仅 3 件 | 部分 | 可做 |
| 4E-A33 | X-03 strictOffline 关闭裁定 | 设备 `OK (1 test)`（批次 39）；关闭归主会话 | 阻塞 | 需用户 |
| 4E-A34 | P-05 文案不承诺硬上界半边 | 附录 O P-05：无执行记录；文案在 feature/tutor | 部分 | 热区 |

---

## 5. 5B（考前与纸质闭环）

> 权威源：`new-stages-spec.md:71-97`、`:189-194`；MASTER `:100`；PRD-10 条（followup-plan:146）。

| ID | 剩余项 | 现状证据 | 状态 | 分类 |
|---|---|---|---|---|
| 5B-A1 | G5 生产调用点（出题工具/确认卡） | `AdaptiveSelectionAssembler` 在树；KDoc 自述“**仍未接通**：确认流的生产调用点、Room 单列 UPDATE adapter、feature 消费者”（`AdaptiveSelectionAssembler.kt:74-79`）；生产构造 0（本项 grep） | 未落 | 热区 |
| 5B-A2 | G5 候选死端 KD-67（`contentVerified=false`） | KD-67 open（`known-defects.md:2639`） | 阻塞 | 热区（终态在 feature；核心侧可做） |
| 5B-A3 | G5 API 扩展口径 | 定稿注“需另裁 API 扩展”（台账 :2076-2077）；未裁 | 阻塞 | 需用户 |
| 5B-A4 | G6 考纲范围匹配 | 爬升已接（`StudyReviewPlannerService.kt:154-170,310`）；考纲范围依赖 KB 章表 + D-4 密度 | 未落 | 他线（KB/D-4） |
| 5B-A5 | G6 importance 字段接线 | MASTER :100 明写“importance 字段未接线（当前全按高档 1.0）” | 部分 | 热区 |
| 5B-A6 | G6 爬升常量校准（`0.5` PENDING） | `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION` 标 PENDING_CALIBRATION | 阻塞 | 外部（Wave X 数据） |
| 5B-A7 | G8 导出侧答案键链接线 | `MistakeExportRecordRow` 无 answerKey 字段族（本项实读 `MistakeExportRecordContract.kt:7-18`）；PRD-10 注明“未完成” | 未落 | 可做 |
| 5B-A8 | G8 真实落库仓储 | 仅 `InMemoryPaperAnswerRecycleStore`；无 Room 实现 | 未落 | 可做 |
| 5B-A9 | G8 判词通道实现 | 仅留 `PaperAnswerEvidenceFeedbackPort` 接口 + 编排；无生产实现 | 未落 | 可做 |
| 5B-A10 | G14 改编题版本链 + 复测回流 | spec:88 要求“先核生成与版本链，再接复测回流”；未开工 | 未落 | 热区 |
| 5B-A11 | 5B WG-09 图形承接 | spec:93-97 三条未勾；依赖 4E.1/.2 | 未落 | 他线（4E） |
| 5B-A12 | 整卷/改编答案键身份扩展 | PRD-10 明写只落“域层半边”；`SmbkSelectionPlan` 之外的类别位在 core/domain | 部分 | 热区 |

---

## 6. 5b（知识点/预算）

> 权威源：台账 `:2733`“阶段5拆 5a 题级判定/undo、5b 知识点/节奏、5c 讲题/错因”；MASTER `:99`、`:93`（3E）；spec `:182-187`。
> 5a 已落（三键/undo，设备 24/0；MASTER :99），不列剩余；5b 的 3E 前提（第二范围档 + 每日 10 封顶）已落（MASTER :93）。

| ID | 剩余项 | 现状证据 | 状态 | 分类 |
|---|---|---|---|---|
| 5b-A1 | 知识点复习卡片流 UI（同栏两入口） | F6 标 🔌“部分接线”（需求定稿 :103）；UI 在 feature/review | 部分 | 热区 |
| 5b-A2 | F8/M4 每日预算自定义重构 | 需求定稿 :115“TimeBudget 在、设置弱”；设置页在 feature/profile | 部分 | 热区 |
| 5b-A3 | 3E 真实数据设备复验 | `StudyReviewPlanner3EScanInstrumentedTest` 在树；MASTER :93 出口门只记“单测 ✓，真实数据复验待设备 UNVERIFIED” | 部分 | 可做 |
| 5b-A4 | 批量导入积压验证（PRD-10） | PRD-10 条原文要求“验证批量导入积压”；无设备记录 | 未落 | 可做 |
| 5b-A5 | 5b 启用门（3D 全门 + 3E） | 3D D-2/D-3/D-5 未收口；真实数据启用挂起 | 阻塞 | 他线（3D） |
| 5b-A6 | 备注回响 S1（`user_note` 永不出网） | 需求定稿 :105 定案；实现面在 feature/review | 未落 | 热区 |

---

## 7. 5c（讲题/错因）

> 权威源：MASTER `:99`；spec `:182-187`；批次 41 WS6 错因端到端（`ErrorCauseGate`/`ErrorCauseBookingService`/账本设备 3/3，批次 41 记录 §1.2 #38-41）。

| ID | 剩余项 | 现状证据 | 状态 | 分类 |
|---|---|---|---|---|
| 5c-A1 | 错因生产装配（`ErrorCauseBookingService` → app/feature） | 批次 41 记录 #41 明写“未接 app/feature（热区）” | 部分 | 热区 |
| 5c-A2 | 错因 UI 呈现 + 撤销（F14 错因标注部分） | 需求定稿 R2 :71 要求 undo 覆盖错因；无 UI 载体 | 未落 | 热区 |
| 5c-A3 | E4 启发式/直答模式开关 | 需求定稿 :133“阶段 5 默认引导”；开关未落 | 未落 | 热区 |
| 5c-A4 | E6 前置缺口诊断补救 | 机制在（`PrerequisiteRemediation`）；受 D-4 前置密度制约（审-A1 83.4% 孤立） | 部分 | 他线（R10/D-4） |
| 5c-A5 | E11 讲题历史检索 UI | 需求定稿 :135“会话有账本、检索 UI 待核” | 部分 | 热区 |
| 5c-A6 | 5c 配图（WG-04/09） | spec:186 要求动态图随 4E.2、静态新图依赖 4E.1；4E.2 未设备绿 | 阻塞 | 他线（4E） |

---

## 8. 剩余项汇总（73 项，按主分类）

| 分类 | 计数 | 构成 |
|---|---|---|
| **可做**（本线，非热区，无外部依赖） | **30** | PRD 6（A1/A2/A4/A5/A6/A7）+ 6C 3（A1/A4/A5）+ 4E 16（A2/A3/A4/A7/A9/A13/A15/A16/A19/A20/A21/A22/A25/A27/A28/A32）+ 5B 3（A7/A8/A9）+ 5b 2（A3/A4） |
| **热区** | **25** | PRD 1（A9）+ 6C 1（A2）+ 4E 11（A5/A6/A10/A11/A12/A14/A17/A18/A24/A26/A34）+ 5B 5（A1/A2/A5/A10/A12）+ 5b 3（A1/A2/A6）+ 5c 4（A1/A2/A3/A5） |
| **外部** | **6** | PRD 1（A3）+ 6C 1（A6）+ 4E 3（A23/A29/A30）+ 5B 1（A6） |
| **需用户** | **5** | 6C 1（A3）+ 4E 3（A8/A31/A33）+ 5B 1（A3） |
| **他线** | **7** | PRD 1（A8）+ 4E 1（A1）+ 5B 2（A4/A11）+ 5b 1（A5）+ 5c 2（A4/A6） |
| 合计 | **73** | PRD 9 + 6C 6 + 4E 34 + 5B 12 + 5b 6 + 5c 6 |

**未计入剩余（已落或已关闭，不重复计）**：PRD-02 主链（KD-43 closed，设备 24/0）；6C-1 设置迁移；6C-2 域/清单/裁剪器/恢复闭包（批次 18/19 代码级）；4E.1 共用 policy/coordinator/ledger；5a 三键/undo；3E 第二范围档；错因域层链（批次 41 设备 3/3）；KD-75 产品接线（本批落码，仍 open 但剩余项只有执行）；KD-76 单批 n=20 主口径（仍 open，剩余为 A6 两批独立 + C4）；KD-83（本批 closed）。

**注意（不重复计数但影响排期）**：KD-75 的“执行侧”剩余（设备真链 + pypdf + 独立读者 + 部分页处置）与 4E-A18/E 组、WS2 证据同批，不单列；KD-76 剩余（A6 两批独立 + C4 实测）挂在 4E-A2/P-07，不单列。

---

## 9. 剩余项分派清单

### 9.1 本线可做（30 项；建议批次 43 主体）

1. **6C 执行面（3）**：6C-A1 6C-2 重裁 JVM+设备；6C-A4 §9.9 文档两项；6C-A5 v5→73 矩阵 + W10-2 装置修复。
2. **4E 设备向量（8）**：4E-A2（C4 段）、A3（dumpsys）、A4（P-09 + p09 归因）、A15（M-03）、A16（D-01…D-06）、A22（Q-04 截图臂）、A25（V-01④）、A27（V-03/V-04）。
3. **4E 数据侧/扩测（6）**：4E-A7（P-12③）、A9（Validator）、A13（S-09）、A19（A-07）、A20（A-10）、A21（A-11/A-12 扩测）。
4. **4E 发布矩阵（2）**：4E-A28（R-01 API 23）、A32（R-05 许可快照入仓）。
5. **5B 数据侧（3）**：5B-A7（导出记录答案键链接）、A8（Room 答卷仓储）、A9（判词通道）。
6. **PRD 设备复验（6）**：PRD-A1/A2（PRD-03）、A4（PRD-06）、A5（PRD-07 性能门）、A6（PRD-08）、A7（PRD-11 修改步骤）。
7. **5b 设备（2）**：5b-A3（3E 真实数据）、A4（批量导入积压）。

### 9.2 需用户提供（5 项；执行者不得代签）

| 项 | 需要的决定/资源 | 现状 |
|---|---|---|
| 6C-A3 | 三项未决裁定：类别文案 / 无图子集 / 合并恢复 | 设计件 :1610-1615 明写“本轨不代签” |
| 4E-A8 | 4E.3 `VisualScene*` 五类名是否本阶段实现（影响 4E.2 交付面） | 五类名 0 命中；无裁定 |
| 4E-A31 | R-04 两条合规路径（Keeper/L8 合并 vs 其他） | 批次 37 §1.5 归主会话；settings 热区 |
| 4E-A33 | X-03 strictOffline 是否关闭 | 设备 1/1 绿；关闭归主会话 |
| 5B-A3 | G5 API 扩展口径 | 台账 :2076 待裁 |

### 9.3 需外部资源（6 项；给 runbook，标 BLOCKED）

| 项 | 缺什么 | 可执行替代 |
|---|---|---|
| PRD-A3 | 第三方接收 App（真实打开 DOCX/PDF） | 模拟器装一个文件管理器/办公 App 后跑；无法装则 BLOCKED |
| 6C-A6 | 阶段 7 迁移链重置完成 | 先登记等待清单；不提前跑 |
| 4E-A23 | 真实 Provider 凭据 + 人工评审 | 先跑 fake/机制；120 例盲测 BLOCKED |
| 4E-A29 | arm64 硬件/宿主 | 维持等待清单 |
| 4E-A30 | arm64 + 16 KB | 同上；x86_64 16 KB 已 ◐ |
| 5B-A6 | Wave X ≥400 合格长程样本 | 维持 PENDING_CALIBRATION，不取值 |

### 9.4 需他线（7 项）

| 项 | 依赖线 | 解除条件 |
|---|---|---|
| PRD-A8 / 5b-A5 | 3D（D-2/D-3/D-5） | 同包全过才开放真实学习写入 |
| 4E-A1 | 宿主低负载窗/并发治理 | 停他线 Gradle/模拟器并发，或改宿主 |
| 5B-A4 / 5c-A4 | KB/D-4 前置图密度、KB 章表 | D-4 补密度 + 考纲映射交付 |
| 5B-A11 / 5c-A6 | 4E.1/4E.2 设备绿 | 4E-A16/A27 等设备向量先过 |

### 9.5 热区（25 项；须先清空或改纪律）

热区清单见 §0.3。**注意**：本批已出现 `core/domain` 3 个新 M，与纪律冲突；若批次 43 仍维持“core/domain 禁改”，则 6C-A2、4E-A6/A14、5B-A12 等 4 项无法落地，必须由主会话先裁定“热区是否清空/是否授权具体文件”。

---

## 10. 批次 43 计划草案

> 前提：批次 42 的实际账是 116（见 §0.1），不是 100。按 §8，**非阻塞且非热区的剩余项只有 30 项**；把它们拆到可验收子件后约 40 个工作包。**因此批次 43 无法诚实地再做“5× = 100 项”**——要凑到 100 只能加水项，违反“不设水项”。
> 草案规模：**58 项 = 研究 4 + 实施 40 + 流程 14**。若主会话坚持 5×，需先解三门前置：① 热区清空（至少 `core/domain`、`core/ui`、`feature/*` 中与 4E.2/5B/5c 相关的面）；② 外部资源到位（Provider 凭据、签名密钥、arm64 硬件、接收 App）；③ 3D 硬门 + 低负载宿主。

### 10.1 研究线（4）

- R1 6C 表闭包：把视觉/共享 operation·attempt/预览/字体/canonical 字节纳入 `SmbkSelectionPlan` 的类别与裁剪序（不新增类别位时如何表达）→ `2026-11-05-6c-visual-closure.md`
- R2 5B 答案键链接线：`MistakeExportRecord` → 答卷身份/题版本/答案来源的可核映射（不改 schema 73 的可行形态）→ `2026-11-05-5b-answer-key-link.md`
- R3 PRD-07 备份性能门：冻结测量设备/负载、p95 ≤2× 与“无 >5 s 占锁”的可执行判据 → `2026-11-05-backup-perf-gate.md`
- R4 4E 设备向量合并执行形状：D-01…D-06 + V-03/V-04 + M-03 的串行清单、APK 绑定与失败归因表 → `2026-11-05-4e-device-vectors.md`

### 10.2 实施（40）

| WS | 内容 | 项数 |
|---|---|---|
| WS1 6C 执行 | 6C-2 五 mask 的 JVM/设备矩阵；W10-2 装置修复；v5→73 矩阵；§9.9 文档两项 | 6 |
| WS2 4E 设备向量 | D-01…D-06 六组 + V-03/V-04 + V-01④ + M-03 + Q-04 截图臂 | 10 |
| WS3 4E 数据侧 | P-12③、S-03、S-09、A-07、A-10、A-11/A-12 扩测 | 6 |
| WS4 发布矩阵 | R-01 API 23 全套件；R-05 许可快照入仓；KD-72 锁窗口只读复判 | 3 |
| WS5 5B 数据侧 | 答案键链接线；Room 答卷仓储；判词通道；G8 幂等/不重复计分用例 | 4 |
| WS6 PRD 设备复验 | PRD-03 整链 + A/B 并发；PRD-06 四态/三档；PRD-07 性能门；PRD-08 进程重建；PRD-11 修改步骤 | 6 |
| WS7 5b 设备 | 3E 真实数据复验；批量导入积压 | 2 |
| WS8 记录/台账 | 准入附录 P 四次清算；契约 patch 复跑（仍不应用）；KD 台账回写；批次 43 记录 §0 | 3 |

（6+10+6+3+4+6+2+3 = 40）

### 10.3 流程（14）

基线；研究交叉复核；集成；单测门；强门；设备轨；证据；独立核对；对抗复核；修复环；环后强门；记录；提交清单；风险登记。（批次 42 的 20 含 9 个 WS 内互斥子件，本草案不再重复计入。）

### 10.4 硬约束（沿批次 42，收紧两条）

- 热区仍禁改；**新增**：本批 `core/domain` 出现 3 个非授权 M，批次 43 开工前必须由主会话逐文件裁定“保留/回退/授权”，不得默认延续。
- `core/export` 只允许 `MistakePdfExporter` 最小 hunk；E 组其他文件（`VisualSnapshotPdfBlock` 等）须先取得授权。
- 不改 schema 73、不改 `availableMethods`、JS 不广告（MVA 未过）。
- 设备绿必须绑定源快照 + APK sha；20 轮必须 boot_id 可证；配对必须同 APK（F8 已物理拒绝同 COMPONENT，需先解“同 APK vs 可区分身份”矛盾）。
- 不提交锁文件/密钥/tmp；分块 ≤6 路径；外部阻塞项给 runbook 并标 BLOCKED。

### 10.5 验收

- 6C-2 五 mask 在设备上各一例成包/恢复（含 mask=0 不删本机内容、mask=8 证据闭包）。
- 4E 设备向量 D 组 6 组、V-01④、M-03 有设备证据；R-01 API 23 有 PASS/FAIL 明文。
- 5B 答案键链接线有 JVM + 设备各一例；G8 重传不重复计分。
- PRD-03/06/07/08/11 设备复验；PRD-07 性能门有数字或如实 FAIL。
- 双门 exit=0（除 KB 3 红）；本批新增用例先红后绿。

---

## 11. 本项实读/实跑清单与 UNVERIFIED

**实跑（本会话）**

```text
python -I 解析计划编号 → count 90 / missing [] / 各 WS 12,12,9,8,6,8,5,10,5,5,4,6
git rev-parse HEAD → faab17f58b01eab78d5c08a9f843b3af3df2fecf
git status --porcelain | wc -l → 642；cut -c1-2 → 14 D / 166 M / 462 ??
git status --porcelain -- core/domain core/ui core/export feature docs/current settings.gradle.kts → 9 代码 M + 7 docs/current M + 锁/nul
git diff --stat -- core/domain core/export core/ui → 10 文件 / +237 −39
grep -rln "class VisualSceneValidator|class VisualSceneCompiler|class VisualStateCodec|class VisualProgram\b|class VisualScene\b" --include=*.kt core app → 0
grep -rn "VisualSnapshotPdfBlock" --include=*.kt core app → 0
grep -n "visual" settings.gradle.kts → 0
grep -rln "AIGC" core/export/src/main --include=*.kt → 1（MistakePdfExporter.kt）
grep -n "ALLOWED_MASKS|allowedMasks" BackupSelection.kt / SmbkSelectionPlan.kt → {0,8,9,13,15}
grep -n "missing_methods|UNKNOWN" tools/ci/isolation_pair_report.py → F7 物化路径在
grep -n "installed_apk|adb" tools/ci/t4_control_marker.py → F8 已安装 APK 比对在
grep -n "bootIdBefore|INVALID|sha" tools/ci/summarize_kd76.py → F10 空 sha/boot 门在
```

**实读**：计划 §0–§5；批次 41 记录 §0/§1.2/§9.3；批次 42 基线 §1–§6；WS2/WS3/WS4/WS5/WS7/WS9/WS10 证据；6C-2 重裁；MASTER-LINE :78-105, :243-258；PRD 定稿与 followup-plan；new-stages-spec 5B/6B/6C 与 5a/5b/5c；准入附录 O；`known-defects.md` KD-67/75/76/83 段。

**UNVERIFIED（本项不得写通过）**

1. 任何 Gradle 编译/单测门/强门——本项零 Gradle。
2. 任何 adb/设备行为——本项零设备。
3. F11/F12 修复的 Kotlin 编译与用例执行——只读码确认签名/字段；`Kd76ReportVerdictTest` 与 `ReviewCapacityPreviewReaderTest` 未跑。
4. F7/F8/F10 修复的 pytest 结果——WS4 证据记 41 passed（转录），本项未复跑。
5. WS1–WS10 各轨的 JVM/设备结论——本项只读 E 文件转录。
6. 远端 `origin/main` 实际值（基线 `curl 28`）。
7. 批次 42 记录 §0 的最终定稿——本文件只提供可取的草稿口径，不替代记录员对账。

---

## 12. 回链

- 计划：`.zcode/plans/plan-2026-11-04-batch-42.md` §0/§2 WS11（81–84）/§4/§5。
- 上批：`docs/research/2026-11-03-batch-41-record.md` §9.3（F7/F8/F10/F11/F12 移交）、§9.4。
- 本批基线：`docs/research/2026-11-04-batch-42-baseline.md` §1.2/§1.4/§2.1/§5.3/§6。
- 本批证据：`docs/research/evidence/2026-11-04-batch-42/{ws2-kd75-closeout,ws3-kd76-20-rounds,ws4-isolation-causality,ws5-kd83-v4-fixture,ws7-preview-assembly,ws9-contract-admission,ws10-device-full}.md`。
- 4E 准入：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` 附录 O（:1135 起）。
- PRD：`docs/requirements-fused-final-2026-10-11.md` R1–R11；`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:36-157`。
- 阶段：`docs/REFACTOR-MASTER-LINE.md:78-105`（总表）、`:243-258`（§12 排期）。
- 新阶段 spec：`docs/research/2026-10-11-new-stages-spec.md:71-97,126-148,182-209`。
- 台账：`docs/known-defects.md`（KD-67/72/75/76/83）、`:2879`（KD-75 条目）、`:2898`（KD-75 Reopen condition）、`:2910`（KD-76 条目）。
