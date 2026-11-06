<!-- 体积自查：407 行 / 42,531 B（落盘后 wc -lc 实测）。字节远低于 65,536 B；行数略超 400 —— R4 的 400 行上限写在「设备验证证据落盘规范」，形式上约束 docs/research/evidence/**，本文件与批次 43 基线件同口径（该件 460 行，见其文件头自述）。批次 43 R6 计划管理员落盘；本轮唯一写入 = 本文件。 -->

# 批次 43 · R6 计划收尾盘点：PRD / 6C / 4E / 5B / 5b / 5c 与 KD 台账逐项判定

| 字段 | 值 |
|---|---|
| evidence-id | `B43-R6-PLAN-CLOSURE-01` |
| executed-at | 2026-11-03（本机时间）；文档名按计划批次 43 的 `2026-11-05` |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86`（本轮 `git rev-parse HEAD`） |
| 工作树 | 本轮写作开始时 `git status --porcelain \| wc -l` → **512**；落盘后 **515**（+本文件 + 并发落盘的 R1/R5 两件）；`-uall` 时点值 **3724**；staged **0**（`git diff --cached --name-only` 输出为空） |
| 执行者 | 计划管理员（子代理），共享工作树，**只读；本轮唯一写入 = 本文件** |
| 未执行 | 任何 Gradle、任何 `am instrument`、任何 APK 构建/安装、任何 git 写、任何 Provider 调用、任何 emulator 启动 |
| 输入 | 计划 `.zcode/plans/plan-2026-11-05-batch-43.md`（全文实读）；批次 42 记录 §9.2/§9.3/§9.5；批次 42 盘点 `2026-11-04-batch-42-inventory.md`；批次 43 基线 `2026-11-05-batch-43-baseline.md`；研究交叉复核 `evidence/2026-11-05-batch-43/research-crosscheck.md`；`docs/known-defects.md`（83 条标题全量解析） |

> **口径**：**实跑** = 本轮本机执行并读输出；**实读** = 本轮读到仓内源码/文档原文（给行号）；**转录** = 引既有件原文（给文件:行）。状态词只用四个：**可做 / 热区 / 外部阻塞 / 已关闭**。
>
> **判定回答的是「本线能否动手」，不回答「做完是否转绿」。** 本批大量项（isolation、KD-76 A6）可动手但结果不由本线决定；把它们写成「可做」不等于承诺通过。
>
> **外部阻塞的内部区分**：`外部阻塞` 一词下用「解除方」列区分 **外部资源 / 需用户裁定 / 他线 / 顺序**。四值口径不增加第五类，避免与批次 42 的五值+两值混账。
>
> 缩写：`plan:NN` = `.zcode/plans/plan-2026-11-05-batch-43.md` 第 NN 行；`inventory:NN` = `docs/research/2026-11-04-batch-42-inventory.md`；`baseline:NN` = `docs/research/2026-11-05-batch-43-baseline.md`；`crosscheck:NN` = `docs/research/evidence/2026-11-05-batch-43/research-crosscheck.md`；`record:NN` = `docs/research/2026-11-04-batch-42-record.md`；`KD:NNNN` = `docs/known-defects.md` 第 NNNN 行。

---

## 0. 计数口径与盘点的三处作废

### 0.1 计划项数：自述 100，实列 104（第三批同族缺陷）

本轮按行首 `^\d+\.\s` 独立解析计划（实跑）：

```text
$ python -I - <<'EOF'  … re.match(r'^(\d+)\.\s', line) …
count 78
first [(1, 27), (2, 28), (3, 29)]  last [(76, 124), (77, 125), (78, 126)]
missing []  dups []
```

即 **§2 实列 78 个连续编号（1..78，无缺号无重号）**，不是 74；计划 `:1`、`:24`、`:131` 三处仍写 74/100 → 实际账 **6 + 78 + 20 = 104**。按 WS 分段（与 `plan:26-126` 一致）：WS1 1–10、WS2 11–18、WS3 19–28、WS4 29–36、WS5 37–44、WS6 45–50、WS7 51–56、WS8 57–60、WS9 61–64、WS10 65–68、WS11 69–72、WS12 73–78（10+8+10+8+8+6+6+4+4+4+4+6 = 78）。

本文件不改计划文件；「74/100」登记为**计划口径缺陷**（批次 41 记 55 vs 62、批次 42 记 74 vs 90，本批 74 vs 78 —— 连续第三批）。基线 F1 与交叉复核 §1.6 同判。

### 0.2 盘点基数是 73 项，本文件为 74 行（+1 新增、1 作废、0 拆分）

批次 42 盘点 §8 的 73 项（`inventory:213-222`）是**批次 42 回滚前**写的，有三段必须显式作废（`crosscheck:156-165` 已判，本文件照办）：

| 位置 | 原文主张 | 作废理由 |
|---|---|---|
| `inventory:232-240`（§9.1 第 1 条） | 「6C-A1 6C-2 重裁 JVM+设备」列为本线可做 | 重裁已被批次 42 §9.2 全部回滚（`record:519-521`）；用户 2026-10-24 裁定优先 |
| `inventory:290-303`（§10.2 WS1） | 「6C-2 **五 mask** 的 JVM/设备矩阵」「mask=8 证据闭包」 | 与 `plan:5` 不可变清单直接冲突（mask=8 不提供、mask=0 禁止） |
| `inventory:309-315`（§10.4/§10.5） | 验收要求「mask=0 不删本机内容、mask=8 证据闭包」 | 同上 |

因此：

- **6C-A1 作废** → 判定「已关闭（作废）」，但保留行号以维持跨批可追溯；
- **新增 6C-A7**（6C-2 文档层残留）——批次 42 回滚只回滚域层，`BackupRepository.kt` 与 `SecondaryScreens.kt` 仍声明 0/8 合法（`baseline:405`、`crosscheck:176`），这是 73 项里没有的**真实剩余项**；
- 其余 72 行按原 ID 沿用，不改名、不合并。

基数：**9（PRD）+ 7（6C）+ 34（4E）+ 12（5B）+ 6（5b）+ 6（5c）= 74 行**。与批次 42 的 73 项差异可逐项对账（§7.1）。

### 0.3 不可变清单的行为层现状（本轮实读）

| 事实 | 位置 |
|---|---|
| `ALLOWED_MASKS = linkedSetOf(9, 13, 15)` | `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt:166`（本轮 `grep -n "val ALLOWED_MASKS"` 实跑） |
| 0/8 被产品裁定拒绝的既有断言 | `core/data/src/test/kotlin/.../backup/SmbkSelectionPlanTest.kt:443-476`（本轮实读：`assertEquals(setOf(9,13,15), allowedMasks)`、逐 mask 0..15 拒绝、`isClosureConsistent(0/8)` 为真但 `isCreatableMask` 为假） |
| 0/8 的 KDoc 残留是**未提交改动** | `git show HEAD:…/BackupRepository.kt \| sed -n '28,40p'` → HEAD 原文「拒绝 0/8 与未闭合组合」；工作树 `:33`「0/8 自 2026-11-03 起可创建」、`:58`「mask 0/8/9/13/15」 |
| UI 层残留 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt:1195`、`:1208`、`:1258`（0/8「已合法」文案；`git diff --stat` 77 行改动） |

结论：**行为层零漂移**（可安全写「6C-2 未变」）；文档/UI 层有残留，且残留是未提交工作树改动，不是已入库事实。

---

## 1. 判定统计（74 行 + KD 17 条）

| 线 | 行数 | 可做 | 热区 | 外部阻塞 | 已关闭 |
|---|---:|---:|---:|---:|---:|
| PRD（A1–A9） | 9 | 6 | 1 | 2 | 0 |
| 6C（A1–A7） | 7 | 2 | 2 | 2 | 1 |
| 4E（A1–A34） | 34 | 17 | 11 | 6 | 0 |
| 5B（A1–A12） | 12 | 3 | 5 | 4 | 0 |
| 5b（A1–A6） | 6 | 2 | 3 | 1 | 0 |
| 5c（A1–A6） | 6 | 0 | 4 | 2 | 0 |
| **合计** | **74** | **30** | **26** | **17** | **1** |
| KD 台账（open 17 条） | 17 | 9 | 2 | 3 | 3 |

与批次 42 §8 的对账：可做 30 → 30（−6C-A1 作废、+4E-A1 由他线改判可做）、热区 25 → 26（+6C-A7）、外部 6+需用户 5+他线 7 = 18 → 17（4E-A1 移出）、已关闭 0 → 1（6C-A1）。总数 73 → 74。

4E-A1 改判的理由：本批 WS3 把 witness、`-4` 根因、低负载前门作为**可执行代码项**（`plan:49-52`），这些不需要窗口先通过；低负载窗降为**前置条件**（计划项 21 本身就是前门），不再作为解除方。判定回答「能否动手」，故为可做。

**KD 台账全量**：`docs/known-defects.md` 共 **83** 条 `## KD-` 标题（本轮 `python -X utf8 -I` 解析实跑：`total 83 / OPEN 17 / CLOSED-FIXED-RESOLVED 66`）。66 条关闭/修复/解决的条目本文件不逐条重判（判定依据是标题里的 `resolved/fixed/closed` 与日期，不是本轮复验），只对 17 条 open 逐条判定。

---

## 2. PRD（9 行）

> 权威源：`docs/requirements-fused-final-2026-10-11.md` R1–R11；任务身份 `docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:36-157`（全部未勾）；阶段 `docs/REFACTOR-MASTER-LINE.md:94-103`。行内现状转录自 `inventory:88-96`。

| ID | 剩余项 | 判定 | 依据 | 唯一出口 / 前置 |
|---|---|---|---|---|
| PRD-A1 | PRD-03 编辑→杀进程→重开→复习→导出 版本一致性设备全链 | **可做** | 两个用例在树（`MistakeRevisionEditInstrumentedTest:42/:135`，`inventory:88` 实读）；三段链无设备记录 | 批次 44 设备项（本批 WS4 是既有套件复基线，不含该新链） |
| PRD-A2 | PRD-03 A/B 并发编辑只接受 `expectedRevision` | **可做** | 拒绝路径有设备例；A/B 双路并发例未见（`inventory:89`） | 同上，与 A1 同批 |
| PRD-A3 | PRD-04 真实接收应用打开 DOCX/PDF（≥1 次） | **外部阻塞**（外部资源） | 验收原文「仅弹分享面板不等于接收端拿到文件」（`inventory:90`） | 装一个真实文件/办公 App 后跑一次；装不上则 BLOCKED |
| PRD-A4 | PRD-06 首装四态 + 权限三档设备例 | **可做** | 能力半边已落（`TutorCapabilitySnapshot`）；四态/三档无整组设备证据（`inventory:91`） | 批次 44 设备项 |
| PRD-A5 | PRD-07 备份性能门（p95 ≤2× 基线、无单次 >5 s 占锁） | **可做** | `PerformanceGateTest` 是库检索门不是备份门（`inventory:92`）；本批 WS11 69–71 是 warm-up/导出/内存，**不覆盖备份** | 批次 44；判据需先冻结（设备/负载/分母） |
| PRD-A6 | PRD-08 真实进程重建（旋转/回收/强停三分） | **可做** | JVM 8/0 已记；真实重建未跑（`inventory:93`）；本批 WS4-35 只覆盖「强停」一分 | 批次 44 补旋转/回收两分 |
| PRD-A7 | PRD-11 本地任务线补「修改」步骤 | **可做** | 批次 19 设备 5 步，明写「修改」未覆盖（`inventory:94`） | 批次 44 设备项 |
| PRD-A8 | PRD-05 / PRD-11 学习线真实数据启用 | **外部阻塞**（他线 3D） | 3D D-2/D-3/D-5 未共同收口（`inventory:95`） | 3D 三门同包全过后本线复跑；无第二条路 |
| PRD-A9 | PRD-01 启用检查/状态页「未开放」登记 | **热区** | 状态页在 `docs/current` / `feature`（`inventory:96`） | 主会话授权具体文件（见 §9.1） |

---

## 3. 6C（7 行）

> 权威源：`docs/research/2026-10-11-new-stages-spec.md:126-148,202-209`；设计件 `2026-10-17-6c2-content-selection-design.md` §9.9/§9.11。

| ID | 剩余项 | 判定 | 依据 | 唯一出口 / 前置 |
|---|---|---|---|---|
| 6C-A1 | 6C-2「重裁 0/8/9/13/15」落码后的 JVM + 设备执行 | **已关闭（作废）** | 前提被 `record:519-521` 全部回滚；`ALLOWED_MASKS` 实读 `BackupSelection.kt:166` = {9,13,15} | 无。若未来重开 0/8，须用户先明确重新确认（`record:525`） |
| 6C-A2 | 6C 承接 WG-08：视觉程序/快照/共享 operation·attempt/预览/字体版本/canonical 字节纳入闭包 | **热区** | 类别位在 `core/domain/BackupSelection.kt`（`inventory:109`） | 主会话授权 `BackupSelection.kt`（与不可变清单同文件，须单独说明不改 0/8 语义） |
| 6C-A3 | 设计件 §9.9 三项未决：类别文案 / 无图子集 / 合并恢复 | **外部阻塞**（需用户裁定） | 设计件 `:1610-1615` 明写「本轨不代签」（`inventory:110`） | 用户一句话定三项；不定则永久停在此处 |
| 6C-A4 | §9.9 另两项（治理台账归类、VACUUM 耗时）落文档 | **可做** | 纯文档/测量（`inventory:111`）；**本批计划无对应项** | 并入 WS6 文档项（≤1 小时）或批次 44 |
| 6C-A5 | v5→73 矩阵显式入证据 + W10-2 合成 v4 用例装置修复 | **可做** | WS10 记 v5→73 10/11，唯一红是 `open().close()` 不触发 Room 懒开库（`inventory:112`）；本批 WS4-30/31 + WS7-53 覆盖 | 本批可闭合（item 53 须按**内容**指认，见 §10 F6） |
| 6C-A6 | 跨阶段 7 迁移链重置后的旧包恢复 | **外部阻塞**（顺序） | spec:209；阶段 7 未开工（`inventory:113`） | 阶段 7 交付后单列一批；不提前跑 |
| 6C-A7 | **（新增）** 6C-2 文档层残留：KDoc/UI 仍声明 0/8 合法，点 0/8 被静默吞掉且无原因文案 | **热区** | `BackupRepository.kt:33,58`（core/domain 热区）；`SecondaryScreens.kt:1195,1208,1258`（非热区但属他线共享 M）；行为层正确（`SmbkSelectionPlanTest.kt:443-476` 实读） | 唯一出口：主会话授权清 `BackupRepository.kt` 两行 + 回滚 `SecondaryScreens.kt` 0/8 文案；否则 WS9-63「三方一致」今日必红（`crosscheck:176`） |

> **新增 6C-A7 指认的具体失败**：用户点「仅图片(8)」或「清空(0)」时，`toggleBackupCategory` 用 `mask in ALLOWED_MASKS` 拒绝，但 UI 已去掉 `removable` 锁（`baseline:405` 的 `:1149`）→ 点击**静默无效、无原因文案**（UX 回归）；同时 KDoc/UI 与代码、与四方门 `allowedMasks == [9,13,15]`（`baseline:92` 实跑输出）三方不一致。这不是「更完备」，是一个可复现的用户可见失败。

---

## 4. 4E（34 行）

> 权威源：`docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md:19-23`；准入清单附录 O 69 行，计数 **✅20 + ◐35 + ✗10 + ?2 + ⛔1 + §1 = 69**（`2026-10-26-batch-24-4e2-admission-checklist.md:1137` 实读），本批仍 0 项转通过。

| ID | 剩余项（简） | 判定 | 依据 | 唯一出口 / 前置 |
|---|---|---|---|---|
| 4E-A1 | P-01 isolation 配对 / 低负载复跑 | **可做** | 本批 WS3 19–28 正面覆盖；现状三台设备 15 例全 `-4`（`baseline:195-229`） | 前置：停他线并发取得低负载窗（本轮 `adb devices` 实跑仍 3 台在跑）；T4 臂另计（§9.2） |
| 4E-A2 | P-04 29.5 s 链 C4 段实测 | **可做** | WS2-16 覆盖；C1 p95=1672 / C2 p95=312 / C4 无 ms（`baseline:169-185`） | 只能报「三段 ms + C4 布尔/2000 ms 预算」，不得写「四段实测」（`crosscheck:194`） |
| 4E-A3 | P-08 隔离装配 dumpsys 审计 | **可做** | guard 在 `SmartMistakeBookApplication`；无审计记录（`inventory:126`）；本批计划无对应项 | 批次 44（只读 dumpsys，不动 Application） |
| 4E-A4 | P-09 生命周期限次重调度 + p09 设备红归因 | **可做** | WS7-54 只覆盖「归因」；限次重调度未落（`inventory:127`，附录 N `:819`） | 本批闭合「归因」；「重调度」批次 44 |
| 4E-A5 | P-10 fallback/加载态 UI 半边 | **热区** | 附录 O P-10（`inventory:128`） | 主会话授权 `feature/tutor` |
| 4E-A6 | P-11 晋升 `core/domain` | **热区** | 消费点在 core/data；提级未做（`inventory:129`） | 主会话授权 core/domain |
| 4E-A7 | P-12③ 熔断解除直接断言 + 客户端熔断专项 | **可做** | `fusedDigests` 预检/复查/解除在树；专项缺（`inventory:130`） | 批次 44 |
| 4E-A8 | S-01 `VisualScene*`/Compiler/StateCodec 五类名 | **外部阻塞**（需用户裁定） | 本轮批次 42 复跑 `grep` = 0（`inventory:131`）；4E.3 范围未裁 | 用户裁定 4E.3 是否本阶段实现 |
| 4E-A9 | S-03 `VisualSceneValidator` | **可做** | 零命中，引擎仅保守校验（`inventory:132`） | 批次 44（新增能力，须先指认失败：当前畸形 JSON 只靠保守校验） |
| 4E-A10 | S-05 交互层命中消费 | **热区** | hitRegions 透传、消费未落（`inventory:133`） | 主会话授权 UI 面 |
| 4E-A11 | S-06 / X-01 `:visual_js` 独立模块归位 | **热区** | `grep visual settings.gradle.kts` = 0（`inventory:134`） | 主会话授权 settings（纪律明禁） |
| 4E-A12 | S-07 VisualSceneCard/Controls/Semantics | **热区** | 命中 1（仅测试 KDoc）（`inventory:135`） | 同上 |
| 4E-A13 | S-09 隔离引擎 JVM 半边 | **可做** | `DEEP_FREEZE_JS` 在 `QuickJsVisualExecutionEngine.kt`（本轮 `git grep` 实跑）；JVM 隔离引擎缺 | 批次 44 |
| 4E-A14 | M-01 度量端口提级 | **热区** | 端口提级未做（`inventory:137`） | 主会话授权 core/domain |
| 4E-A15 | M-03 4 MP / 16 MiB 两门 JVM+设备复核 | **可做** | `VisualLayoutBudget.kt:52,58` 接线；未复核（`inventory:138`） | 批次 44 设备向量组 |
| 4E-A16 | D-01～D-06 六组设备向量 | **可做** | 附录 O D 组 6 行全 ◐（`inventory:139`） | 批次 44（与 A15/A25/A27 同批串行） |
| 4E-A17 | E-01 `VisualSnapshotPdfBlock` | **热区** | 零命中（`inventory:140`） | 主会话授权 core/export 非 exporter 文件 |
| 4E-A18 | E-02～E-05 导出 role 过滤/0 派发/双端金样 | **热区** | 附录 O E 组 4 行 ✗（`inventory:141`） | 同上；本批 `core/export` 只许 KD-75 hunk（`plan:137`） |
| 4E-A19 | A-07 上下文预算负向测试 | **可做** | 类型在、生产零构造（`inventory:142`） | 批次 44 |
| 4E-A20 | A-10「耗尽→文字降级」编排半边 | **可做** | 预算常量在树、编排缺（`inventory:143`） | 批次 44 |
| 4E-A21 | A-11/A-12 扩测 | **可做** | 套件 21 / 46+7 @Test（`inventory:144`） | 批次 44 |
| 4E-A22 | Q-04 设备截图臂 | **可做** | `q04/` 16 个 tracked 文件（本轮 `git ls-files \| grep -c q04/` = 16）；设备臂缺 | 批次 44 |
| 4E-A23 | Q-05 真实 Provider 120 盲测/质量门 | **外部阻塞**（外部资源） | 无凭据（`inventory:146`） | 凭据 + 人工评审到位后按 runbook 跑一次 |
| 4E-A24 | Q-06 JS UI fontScale 矩阵 | **热区** | JS 无 UI（`inventory:147`） | 与 4E.3 范围裁定同批 |
| 4E-A25 | V-01④ PDF 腿设备证据 | **可做** | app 套件 4/4 绿（批次 39）；④ 无本批设备证据（`inventory:148`） | 批次 44 设备向量组 |
| 4E-A26 | V-02 生产接线（确认卡/补题入口） | **热区** | 生产调用方 0（`inventory:149`） | 主会话授权 feature |
| 4E-A27 | V-03/V-04 设备执行 | **可做** | 载体在树、设备 UNVERIFIED（`inventory:150`） | 批次 44 设备向量组 |
| 4E-A28 | R-01 API 23 全套件 | **可做** | 附录 O `?`；仅 KD-77 最小冒烟（已 closed）；`api23_smoke*` AVD 在盘（`baseline:356`） | 批次 44（起 api23 AVD，须单机串行） |
| 4E-A29 | R-02 arm64 运行时 | **外部阻塞**（外部资源） | 无 arm64 设备/宿主（`baseline:366-376` 原文 FATAL） | 换 arm64 宿主或真机；等待清单 |
| 4E-A30 | R-03 arm64 16 KB | **外部阻塞**（外部资源） | x86_64 16 KB 已 ◐；arm64 结构性拒载（`inventory:153`） | 同 A29 |
| 4E-A31 | R-04 Keeper K1–K6 + `settings` 裁定 | **外部阻塞**（需用户裁定） | 配置层可执行；K1–K6 未跑；两条合规路径未裁（`inventory:154`） | 用户裁定路径；`settings.gradle.kts` 需同时授权 |
| 4E-A32 | R-05 许可快照入仓 | **可做** | 328/340 取到 license（`inventory:155`）；**计划 WS10-66 只写「快照」不写「入仓」** | 本批最多到快照；「入仓载体」须追加提交块（§10 F7） |
| 4E-A33 | X-03 strictOffline 关闭裁定 | **外部阻塞**（需用户裁定） | 设备 1/1 绿；关闭归主会话（`inventory:156`） | 用户一句话裁定 |
| 4E-A34 | P-05 文案不承诺硬上界半边 | **热区** | 文案在 `feature/tutor`（`inventory:157`） | 主会话授权 feature |

---

## 5. 5B / 5b / 5c（24 行）

> 权威源：`new-stages-spec.md:71-97,182-209`；MASTER `:99-100`。

### 5.1 5B（12 行）

| ID | 剩余项（简） | 判定 | 依据 | 唯一出口 / 前置 |
|---|---|---|---|---|
| 5B-A1 | G5 生产调用点（出题工具/确认卡） | **热区** | `AdaptiveSelectionAssembler.kt:74-79` KDoc 自述「仍未接通」（`inventory:167`） | 主会话授权 feature |
| 5B-A2 | G5 候选死端 KD-67 | **热区** | KD-67 open（`KD:2639`）；终态在 feature（`inventory:168`） | 与 KD-67 同一条出口（§6） |
| 5B-A3 | G5 API 扩展口径 | **外部阻塞**（需用户裁定） | 台账 `:2076-2077` 待裁（`inventory:169`） | 用户一句话定 API 扩展口径 |
| 5B-A4 | G6 考纲范围匹配 | **外部阻塞**（他线 KB/D-4） | 依赖 KB 章表 + D-4 密度（`inventory:170`） | D-4 补密度 + 考纲映射交付后复跑 |
| 5B-A5 | G6 importance 字段接线 | **热区** | MASTER `:100`「未接线，全按 1.0」（`inventory:171`） | 主会话授权 |
| 5B-A6 | G6 爬升常量校准（0.5 PENDING） | **外部阻塞**（外部资源） | `EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION` 标 PENDING_CALIBRATION（`inventory:172`） | Wave X ≥400 合格长程样本；在此之前不取值 |
| 5B-A7 | G8 导出侧答案键链接线 | **可做** | 本轮实读 `core/database/.../MistakeExportRecordContract.kt:7-18`：`MistakeExportRecordRow` 无 answerKey 字段族 | 批次 44（不改 schema 73 的可行形态需先研究） |
| 5B-A8 | G8 真实落库仓储 | **可做** | 本轮实读 `core/data/.../study/InMemoryPaperAnswerRecycleStore.kt:21,35`：`InMemoryPaperAnswerKeyChainStore` / `InMemoryPaperAnswerSheetStore` 两个内存实现；`git grep` 无 Room 类实现这两个端口 | 批次 44 |
| 5B-A9 | G8 判词通道实现 | **可做** | 端口 `core/domain/.../PaperAnswerEvidenceFeedback.kt:18` 仅接口 + `PaperAnswerSheetRecycler.kt:15` 消费；无生产实现（本轮实读） | 批次 44，与 A8 同批 |
| 5B-A10 | G14 改编题版本链 + 复测回流 | **热区** | spec:88；未开工（`inventory:176`） | 主会话授权 |
| 5B-A11 | 5B WG-09 图形承接 | **外部阻塞**（他线 4E） | spec:93-97 三条未勾（`inventory:177`） | 4E-A16/A27 设备向量先过 |
| 5B-A12 | 整卷/改编答案键身份扩展 | **热区** | 类别位在 core/domain（`inventory:178`） | 主会话授权 |

### 5.2 5b（6 行）

| ID | 剩余项（简） | 判定 | 依据 | 唯一出口 / 前置 |
|---|---|---|---|---|
| 5b-A1 | 知识点复习卡片流 UI（同栏两入口） | **热区** | F6 标「部分接线」（`inventory:189`） | 主会话授权 `feature/review` |
| 5b-A2 | F8/M4 每日预算自定义重构 | **热区** | 设置页在 `feature/profile`（`inventory:190`） | 同上 |
| 5b-A3 | 3E 真实数据设备复验 | **可做** | `core/data/src/androidTest/.../StudyReviewPlanner3EScanInstrumentedTest.kt` 在树（本轮 `git ls-files` 实跑）；真实数据复验 UNVERIFIED | 批次 44 设备项 |
| 5b-A4 | 批量导入积压验证（PRD-10） | **可做** | 无设备记录（`inventory:192`） | 批次 44 设备项 |
| 5b-A5 | 5b 启用门（3D 全门 + 3E） | **外部阻塞**（他线 3D） | 3D D-2/D-3/D-5 未收口（`inventory:193`） | 3D 同包全过 |
| 5b-A6 | 备注回响 S1（`user_note` 永不出网） | **热区** | 实现面在 `feature/review`（`inventory:194`） | 主会话授权 |

### 5.3 5c（6 行）

| ID | 剩余项（简） | 判定 | 依据 | 唯一出口 / 前置 |
|---|---|---|---|---|
| 5c-A1 | 错因生产装配（`ErrorCauseBookingService` → app/feature） | **热区** | 批次 41 #41 明写「未接 app/feature」（`inventory:204`） | 主会话授权 |
| 5c-A2 | 错因 UI 呈现 + 撤销 | **热区** | 无 UI 载体（`inventory:205`） | 同上 |
| 5c-A3 | E4 启发式/直答模式开关 | **热区** | 需求定稿 :133；开关未落（`inventory:206`） | 同上 |
| 5c-A4 | E6 前置缺口诊断补救 | **外部阻塞**（他线 R10/D-4） | 机制在（`PrerequisiteRemediation`）；受 D-4 密度制约（`inventory:207`） | D-4 补密度后复跑 |
| 5c-A5 | E11 讲题历史检索 UI | **热区** | 「会话有账本、检索 UI 待核」（`inventory:208`） | 主会话授权 |
| 5c-A6 | 5c 配图（WG-04/09） | **外部阻塞**（他线 4E） | spec:186；4E.2 未设备绿（`inventory:209`） | 4E 设备向量先过 |

---

## 6. KD 台账：17 条 open 逐条判定

> 83 条标题全量解析（本轮实跑）；66 条 resolved/fixed/closed 不重判。下表只判 open 的 17 条。行号 = 标题行。

| KD | 主题（标题摘要） | 判定 | 依据 | 唯一出口 / 前置 |
|---|---|---|---|---|
| KD-24（`:875`） | 去截断实验 v2 净伤害已回滚，词面侧不再以去截断方式修 | **外部阻塞**（他线 KB） | 标题原文「口径更正见末段」；属 KB 召回线 | KB 线交付（dense 兜底议题）；本线不接 |
| KD-27（`:1063`） | CI `check` job 长期红 → status.md commit-back 冻结 | **外部阻塞**（他线 CI） | 实测冻结自 2026-09-21；`if: success()` 结构 | CI 基础设施修复（他线）；本线只登记 |
| KD-28（`:1104`） | `kb_build` 9 科白名单抄两份 | **已关闭（接受边界）** | 标题「2026-09-28 判为接受边界」；Disposition 明写「不收敛」 | 无（台账标题 hygiene 见 §10 F8） |
| KD-29（`:1118`） | 材料/展示层无「AI 整理」标识 | **已关闭（接受边界）** | 标题 open，但 Disposition「用户裁定：暂不处理，登记为已知边界」 | 无；若用户改变取舍则重开 |
| KD-32（`:1180`） | 192 MB 堆上限下知识包安装/批量导入 OOM | **可做** | 两处峰值已修、B2 补采样；剩余见段内 | 批次 44 设备内存项（与 WS11-71 同族但不同判据） |
| KD-61（`:2447`） | 恢复关库前 750 ms 宽限是启发式 | **可做** | low；写门只覆盖两条仓库（段内原文） | 批次 44（接入写门或如实登记为边界） |
| KD-63（`:2542`） | deleteAllData 清标记时机留窄崩溃窗口 | **可做** | low；配套设备断言已过 | 批次 44（`core/data` 非热区） |
| KD-65（`:2575`） | `OpenAiModelResponseParsers` KDoc 与白名单不符 | **可做** | low；纯文档性，Fix direction = 改 KDoc 不改行为 | 批次 44（一行 KDoc） |
| KD-67（`:2639`） | G5-A 候选恒 `contentVerified=false` | **热区** | 核心侧三块已落；产品面在 feature（段内 Progress） | 主会话授权 feature；与 5B-A2 同一条 |
| KD-68（`:2666`） | 新题积压 UI 用 `preview` 而非 `previewBacklog` | **热区** | `feature/review` 热区（段内 Progress） | 主会话授权 |
| KD-71（`:2747`） | QuickJS close/execute 竞态 | **可做** | 修复已落；设备执行见批次 23 Progress | 批次 44 设备复跑 |
| KD-72（`:2784`） | dependency locking 无已跟踪锁文件 | **外部阻塞**（配置面/窗口） | 批次 42 WS8 两次隔离副本 `--write-locks` 均 exit 1、0 锁写出（`record:182`） | 先解配置面变体歧义（W1–W6 等待清单）；本批 WS10-67 只做窗口尝试 |
| KD-73（`:2828`） | 运行时 kotlin-stdlib 降回 2.3.21，证据只覆盖 quickjs AAR | **可做** | Fix direction 二选一；本批 WS10-66 依赖快照部分覆盖 | 批次 44（全解析图成员扫描 + R8/设备冒烟） |
| KD-75（`:2879`） | PDF 无 `/AIGC`；判据漏模型解析文本 | **可做** | 本批 WS1 1–9 覆盖 C2/C4；C8 无计划项（`record:104`） | 本批闭合 C2/C4；C8 唯一出口 = 授权 `MistakePdfDelivery.kt` 最小 hunk |
| KD-76（`:2910`） | 隔离门不含 warm-up/ready | **可做** | 本批 WS2 11–18；A6 口径待冻（`crosscheck:173`） | 前置：主会话一句话冻结 A6 口径（2×20 或 2×10 合并）；低内存/arm64 另计 |
| KD-78（`:3038`） | DrawOps V1 四缺口（①② 未核销） | **可做** | ③④ 已关闭；①② 在 `core/model` 校验器 + `visual-compiler.js`（非热区） | 批次 44 |
| KD-82（`:3256`） | `maxLength` 按码点计、本地门按字节计 | **已关闭（已知限制）** | 标题「known limitation，不排期」；Impact 明写「无安全/正确性漏洞」 | 无；广告解冻批二选一时重开 |

**KD 判定小计**：可做 9（32/61/63/65/71/73/75/76/78）、热区 2（67/68）、外部阻塞 3（24/27/72）、已关闭 3（28/29/82）。

---

## 7. 汇总对账

### 7.1 与批次 42 盘点 §8 的差异（逐项可核）

| 变化 | 项 | 理由 |
|---|---|---|
| 可做 −1 | 6C-A1 | 重裁已回滚，前提不存在（§0.2） |
| 可做 +1 | 4E-A1 | 本批 WS3 把 witness/根因/前门列为可执行项，低负载窗降为前置（§1 末段） |
| 热区 +1 | 6C-A7（新增） | 回滚只回滚域层，文档/UI 残留未清（`baseline:405`、`crosscheck:176`） |
| 外部 6/需用户 5/他线 7 → 17 | 其余保留 | 本文件把三类并入「外部阻塞」，用「解除方」列区分；4E-A1 移出后 18 → 17 |
| 已关闭 +1 | 6C-A1 | 作废 |

### 7.2 判定的边界

- 「可做」不等于「本批会做」：30 项可做里只有 4 项（4E-A1、4E-A2、4E-A4 的归因半边、6C-A5）落在本批计划范围，另加 KD 台账 2 条（KD-75/76）；其余 26 项见 §9.5。
- 「热区」不等于「永远不做」：只表示当前纪律下本线不能落笔，需主会话逐文件授权。
- 「外部阻塞」不等于「无进展」：本批对这些项能做的是 runbook、等待清单、口径冻结，不是交付。

---

## 8. 本批（批次 43）可做完的清单

> 定义：**计划项覆盖 + 无需外部输入 + 完成后有可指认证据**。不保证门绿；门绿由门禁阶段判定。执行状态在本文件写作时全部 **UNVERIFIED**（本批各 WS 与本文件并行，尚无落盘证据）。

### 8.1 可闭合（14 组）

| # | 可闭合的子项 | 计划项 | 闭合证据形态 |
|---|---|---|---|
| 1 | KD-75 C2：provenance 判据 + 4 构造点 + 3 指纹前缀 + 哨兵代际 | WS1-1,2,3,5 | JVM 三类内容用例（AI 图/解析文本/纯用户）+ 4 构造点逐个断言 |
| 2 | KD-75 C4：字面版本升版（`mistake-export-v3`/`mistake-batch-v3`/`mistake-paper-edit-v1` + 哨兵） | WS1-4,8 | 旧无标记缓存重导/拒绝用例 |
| 3 | KD-75 设备 C1/C3 复验（AI 图有标、纯用户无标） | WS1-6,7 | 设备 pypdf 读回（独立读者） |
| 4 | KD-76 C4 夹钟（死亡确认段毫秒） | WS2-16 | 新增 seam + 分段报告（不得写「四段实测」） |
| 5 | KD-76 ③ 产品进程首请求计时 | WS2-13 | 产品进程（非测试进程）计时入口 + 一次实测 |
| 6 | KD-76 A6 两批独立（同构建、时间分离、逐轮 boot_id） | WS2-11,12,14,15 | 两批各自 n/p95/G1/G2；不得相加 |
| 7 | isolation witness 能力 + 15 例单臂实跑 + 低负载前门 | WS3-19,20,21,22 | witness 文件（方法名/开始/结束/CPU 窗）+ 逐例 PASS/-2/-1/-4 分列 |
| 8 | isolation 层 1（同 APK OFF/ON 重复运行）环境漂移结论 | WS3-23,25,26 | 同 APK 两次运行的红集逐方法比较；**不得谈 T4** |
| 9 | 设备复基线 8 项（APK sha、迁移 1→73、备份 34/34、错因 3/3、PDF 10/10、崩溃九类、worker、32 套件） | WS4-29…36 | 每项绑 APK sha + 源快照 |
| 10 | 6C-A5：v5→73 矩阵 + W10-2 懒开库装置修复 | WS4-30,31 + WS7-53 | 10/11 的红转绿（或如实记红） |
| 11 | 4E-A4 的「p09 归因」半边 | WS7-54 | 环境/代码二选一的判定 + 证据 |
| 12 | S10（KD-83 段下 KD-75 错位）+ S11（v4 夹具 golden 入四方门） | WS7-51,52 | 台账段移动 + 门脚本消费真夹具（`24941ed7…`） |
| 13 | 台账/记录/收尾（MASTER 行、决策台账、KD 审计、批次 44 草案、记录 §0、提交清单） | WS6-46…50 + WS12-73…78 | 各文件落盘 |
| 14 | 6C-A4（§9.9 两项文档）——**计划未列，建议并入 WS6** | 无 | 设计件两段落盘（≤1 小时，纯文档） |

另有 WS10-68（明文密钥扫描，只报位置）与 WS11-69…71（warm-up/导出/内存基线）可在本批出数，但它们不对应 74 行里的任何一项（PRD-A5 是**备份**性能门，不是导出/首帧），因此不计入「可闭合子项」。

### 8.2 本批做不到（按原因）

| 项 | 计划项 | 为什么做不到 | 唯一出口 |
|---|---|---|---|
| KD-75 C8（部分页 `writeSubset` 丢 `/AIGC`） | 仅 WS1-9「逐条对账」 | 计划无处置项；处置落 `MistakePdfDelivery.kt` | 追加最小 hunk 授权（本批或批次 44 单列） |
| KD-76 低内存 / arm64 / 真机口径 | 无 | 计划未列 | 外部资源（arm64 宿主/真机/低内存装置） |
| isolation T4 控制臂（WS3-24） | 有 | 需回退生产写点并重建/重装；无 OFF 产物 | 主会话授权生产源码回退（一次性控制构建） |
| WS9-61,62（防漂移不变量） | 有 | **已是重复机制**：`SmbkSelectionPlanTest.kt:443-476` 已逐 mask 断言，域层 `BackupSelectionTest` + 四方门 `allowedMasks == [9,13,15]` 三重覆盖（`crosscheck:177`） | 改指向未覆盖面（恢复入口/设备/UI）或合并为「同向量多层断言」；照字面新增违反「新增必指认」 |
| WS9-63（设计件/KDoc/测试三方一致） | 有 | 文档层残留未清 → **今日必红** | 主会话授权清 `BackupRepository.kt:33,58` + 回滚 `SecondaryScreens.kt` 0/8 文案 |
| WS5-40（arm64 可用性） | 有 | 宿主架构级拒载（`baseline:372` 原文） | 换 arm64 宿主/真机 |
| WS8-57（人工金标） | 有 | 无标注员 | ≥2 名独立标注员 + 1 名裁定者 |
| WS6-45（契约并入） | 有 | `docs/current` 热区；patch 可干净应用但不得落笔（本轮 `git apply --check` exit 0 实跑） | 主会话授权 `docs/current`（并同批修 `data-schema.md` 的 67/17 → 73/19） |
| 4E-A32「入仓」 | WS10-66 只写「快照」 | 附录 O R-05 要仓库载体，计划字面不含入仓 | 追加提交块（许可文本/NOTICE） |
| 26 项可做未排入 | 无 | 本批是收尾批，计划未覆盖 | 批次 44（§9.5） |

---

## 9. 剩余项的唯一出口（按类，每类一条）

> 「唯一出口」= 除它之外没有第二条路能让该项前进。并列选项一律不写。

### 9.1 热区 26 项 → 主会话逐文件授权（一次给全，否则永久停）

需要授权的文件清单（本轮 `git status --porcelain -uall` 实跑 + `inventory:274`）：

| 面 | 文件 | 关联项 |
|---|---|---|
| `core/domain` | `BackupSelection.kt`、`BackupRepository.kt` | 6C-A2、6C-A7、4E-A6/A14、5B-A12 |
| `core/ui` | `Theme.kt`（他线 M） | 无本线项 |
| `feature/tutor` | 文案/UI/确认卡/预览 | 4E-A5/A10/A12/A26/A34、5B-A1 |
| `feature/review` | 卡片流/预算/检索/备注 | 5b-A1/A2/A6、5c-A2/A5、KD-68 |
| `feature/profile` | 预算设置 | 5b-A2 |
| `docs/current` | 7 个 M | PRD-A9、契约并入（WS6-45） |
| `settings.gradle.kts` | — | 4E-A11、4E-A31 |

**唯一出口**：主会话一次性裁定「授权哪些文件、授权到哪一批」；未列入的文件视为永久不做。逐项再问一次不构成出口。

### 9.2 外部阻塞 17 项 → 按解除方分三条（每条一个动作）

> 计数只算 74 行里的 17 项；KD 台账的 3 条外部阻塞（KD-24/27/72）单列在末行，不混入 17。

| 解除方 | 项（74 行内） | 唯一动作 |
|---|---|---|
| **用户裁定**（5 项） | 6C-A3、4E-A8、4E-A31、4E-A33、5B-A3 | 用户对每个裁点给一句话；**未裁的保持现状值**（6C-2 三项不动、`availableMethods` 不动、strictOffline 不关） |
| **外部资源**（5 项） | PRD-A3、4E-A23、4E-A29、4E-A30、5B-A6 | 资源到位（接收 App / Provider 凭据 / arm64 宿主 / Wave X 样本）后按 runbook 跑一次；在此之前任何「完成」声明不成立 |
| **他线 / 顺序**（7 项） | PRD-A8、5b-A5（3D）；5B-A4、5c-A4（KB/D-4）；5B-A11、5c-A6（4E 设备向量）；6C-A6（阶段 7 顺序） | 他线交付或阶段 7 开工后本线复跑；本线不能自解 |
| **KD 台账 3 条** | KD-24（KB 线）、KD-27（CI）、KD-72（配置面/窗口） | KD-24/27 随他线；KD-72 先解配置面变体歧义（W1–W6） |

> 不计入外部阻塞的两条**前置**（不改判定）：KD-76 的 A6 口径冻结、KD-75 的 `core/export` 范围授权（`crosscheck:173,191`）。它们是「开跑前必须有一句话」，不是「本线不能动手」。

### 9.3 已关闭 4 项 → 无出口；台账 hygiene 1 条

6C-A1（作废）、KD-28、KD-29、KD-82 无需出口。**唯一遗留动作**：WS6-49（KD 台账审计）时把这三条标题从 `open` 改为 `closed (accepted boundary / known limitation)`，使台账标题与 Disposition 一致（§10 F8）。

### 9.4 本批范围内的不可闭合项 → 见 §8.2（每条已给唯一出口）

### 9.5 26 项可做未排入 → 批次 44（唯一出口）

按四组拆包（沿用 `inventory:232-240` 的分组，剔除已作废的 6C-A1、扣除本批已覆盖的 4 项）：

1. **4E 设备向量（8）**：4E-A3/A9/A15/A16/A22/A25/A27/A28（API 23）；
2. **4E 数据侧/扩测（5）**：4E-A7/A13/A19/A20/A21；
3. **4E 许可入仓（1）**：4E-A32（快照已在 WS10-66，缺入仓载体）；
4. **PRD/5B/5b 复验（11）**：PRD-A1/A2/A4/A5/A6/A7 + 5B-A7/A8/A9 + 5b-A3/A4；
5. **6C 文档（1）**：6C-A4。

8+5+1+11+1 = **26**。另 KD 台账 5 条小修（KD-61/63/65/71/73）可与之并批。

批次 44 若仍要「5×」，只能靠热区授权或外部资源到位；否则按 40 项左右排（`inventory:280-281` 同判）。

---

## 10. 冲突与缺陷（本轮实读/实跑）

| # | 发现 | 证据 | 严重度 | 处置 |
|---|---|---|---|---|
| F1 | 计划自述 100 = 6+74+20，实列 **78** → 实账 104 | 本轮 `python -I` 解析：`count 78 / missing [] / dups []`；`plan:1,24,131` | 中（口径） | 记录按 78 对账；第三批同族缺陷，建议计划模板加机器校验 |
| F2 | KD-76 **C4 定义冲突**：计划写「产品进程首请求」，权威 C4 = 死亡确认 2 000 ms | `plan:12`（§0 KD-76 行）；`2026-11-01-kd76-stats.md:169,273`；`VisualJsConnectStageCalibrationInstrumentedTest.kt:516`（只有布尔） | **高（判据）** | 按 stats 定义执行 C4（加夹钟）；「产品进程首请求」单列为 ③ 残余 |
| F3 | 6C-2 **文档层残留**：KDoc/UI 声明 0/8 合法，行为层拒绝 | `BackupRepository.kt:33,58`（未提交；HEAD 原文正确）；`SecondaryScreens.kt:1195,1208,1258`；`ALLOWED_MASKS` 实读 `BackupSelection.kt:166` | **高（纪律 + 一致）** | 行为层可写「未变」；残留由主会话授权清（§9.1） |
| F4 | WS9-61/62 是**重复机制** | `SmbkSelectionPlanTest.kt:443-476` 本轮实读已覆盖；域层 + 四方门三重（`crosscheck:177`） | 中（机制） | 改指向未覆盖面或合并；照字面新增违反「新增必指认」 |
| F5 | WS9-63「三方一致」**今日必红** | F3 的残留未清；`crosscheck:176` | 高（可执行性） | 先授权清残留，否则该项无法以绿交付 |
| F6 | 计划 WS7-53 的「S9/S12」编号与实际内容不符（S9=WS6 设备断言目标错；S12=isolation 零执行；懒开库缺陷是未编号项） | `plan:91`；`record:408,411,449`；`crosscheck:181` | 低 | 记录里按**内容**指认「懒开库装置缺陷」，不用 S 编号互顶 |
| F7 | WS10-66 只写「依赖 license/维护快照」，附录 O R-05 要**仓库载体** | `plan:110`；准入 `:243`（R-05 行） | 中 | 4E-A32 本批只能到快照；入仓需追加提交块 |
| F8 | KD-28/29/82 标题仍写 `open`，Disposition 已是「接受边界/不处理/不排期」 | `KD:1104,1118,3256` 本轮实读 | 低（台账 hygiene） | WS6-49 改标题，不改判定 |
| F9 | 批次 42 盘点的三段（§9.1 / §10.2 / §10.4-10.5）与不可变清单冲突 | `inventory:232-240,290-303,309-315`；`plan:5` | 高（纪律） | 已在 §0.2 显式作废；不得据其排期 |
| F10 | PRD-A5（备份性能门）**不在** WS11 范围；WS11 是 warm-up/首帧/导出/内存 | `plan:115-118`；`inventory:92` | 中（覆盖缺口） | 4E/PRD 表已按「本批不覆盖」判定，不误记 |
| F11 | 6C-A1 作为「可做」存在于批次 42 盘点，实为作废项 | `record:519-521` | 高（前提） | 已改判「已关闭（作废）」 |
| F12 | 三台模拟器仍同时在跑，isolation/KD-76 的低负载窗当前不成立 | 本轮 `"C:/Android/Sdk/platform-tools/adb.exe" devices` → `emulator-5562/5566/5584` | 中（环境） | WS3-21 前门必须实测 PASS 才开跑；否则结果只能记 INVALID |

---

## 11. UNVERIFIED / 未跑（不得写成通过）

| 项 | 状态 | 原因 |
|---|---|---|
| 本批 78 个实施项的任何执行结果 | **UNVERIFIED** | 本文件写作时各 WS 并行，尚无 E 文件；本文件零 Gradle/零设备 |
| 12 模块单测门 / 强门 | **UNVERIFIED** | 纪律：Gradle 只在门禁阶段串行 |
| R1–R5 研究件 | **写作开始时不在盘；本文件不依赖它们** | 写作开始时 `ls docs/research/2026-11-05-*` 只有基线件；落盘时 R1（`kd75-model-text`）与 R5（`contract-merge-protocol`）已并发落盘，**本文件未读、未引用其结论**（研究结论以 `research-crosscheck.md` 为准） |
| KD-75 C1/C3/C5–C8 的设备判定 | **UNVERIFIED** | 只实读既有 E 文件与盘上 pypdf 产物（`baseline:136-145`） |
| KD-76 A6/C4、29.5 s 链四段、低内存 | **UNVERIFIED** | 同批次 42 口径，无新证据 |
| isolation 15 例真实行为、T4 排除 | **UNVERIFIED / BLOCKED** | 三台设备全 `-4`；witness 能力不存在（`baseline:247-256`） |
| 16 KB 运行时（`ps16k_x86_64`） | **UNVERIFIED** | 本轮未起第 4 台（`baseline:379-383` 理由） |
| arm64 / 生产签名 / 真实 Provider / 人工金标 | **BLOCKED** | `baseline:413-427` |
| 附录 O 69 行计数 | **转录** | `2026-10-26-batch-24-4e2-admission-checklist.md:1137`，本轮未重数 |
| 66 条已关闭 KD 的关闭质量 | **未重判** | 只读标题状态；重判不在本 ask 范围 |

> **本项无外部来源**：全部判定来自仓内计划、台账、源码与既有 E 文件（路径 + 行号已逐条给出），未调用 `gh` / WebSearch / WebFetch。因此没有外部 URL 可给，也不存在「单一来源待验证」的外部结论；研究纪律中的「换通道再判」在本项不适用（零外部缺口）。

---

## 12. 体量自查与回链

- **回链**：计划 `plan:1-144`；批次 42 记录 §9.2（回滚）/§9.3（S4 两层裁定）/§9.5（收口裁定）；批次 42 盘点 §8–§10；批次 43 基线 §1–§8；研究交叉复核 §0/§1.6/§2/§3。
- **不可变清单**：`plan:5`；`record:517-522`；`docs/research/2026-11-04-6c2-rulings.md:3-15`；行为层 `BackupSelection.kt:166`。
- **KD 台账**：`docs/known-defects.md` 83 条（open 17：`:875,1063,1104,1118,1180,2447,2542,2575,2639,2666,2747,2784,2828,2879,2910,3038,3256`）。
- **本文件不替代主会话裁定**：可写的事实只有一句——**30 项可做、26 项热区、17 项外部阻塞、1 项作废；本批计划覆盖 74 行里 4 项可做（4E-A1、4E-A2、4E-A4 的归因半边、6C-A5）与 KD 台账 2 条（KD-75/76），另有 14 组可闭合子件；剩余项的唯一出口已按类给全**。
- **体量实测**（落盘后 `wc -lc`）：**407 行 / 42,531 B**；与文件头一致。
