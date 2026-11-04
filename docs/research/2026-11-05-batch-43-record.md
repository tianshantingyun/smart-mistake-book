<!-- 体量自查：536 行 / 65,518 B（≤65,536 B 目标；wc -lc 实测）。 -->

# 批次 43 记录（2026-11-05）：计划收尾批——KD 关闭、隔离定案、设备复基线、发版 runbook

> **来源与边界**：本文件由**批次 43 记录员**（工作流收口阶段）落盘，是本次 ask 的**唯一写入**。上一版同名文件是 2026-11-05 修复环整理的 128 行简版；本版按批次 42 记录的 §0–§8 结构重写并逐项对账，替代该简版作为本批记录。
>
> **本轮实跑/实读（逐字见 §3.3）**：`git rev-parse/status/ls-files`、计划编号解析（78）、四方门（**exit 0**）、AUD-08 门（**exit 1**）、A6 重算（**exit 2**）、三文件 pytest（**44 passed**）、witness 检查器（**exit 0 / EXECUTED=15**）、`pypdf` 重读两份 KD-75 PDF、门 JSON/测试 XML/两份门禁日志/源码行号实读。
>
> **本轮未跑**：任何 Gradle、adb/设备、git 写、Provider。§4 设备事实**全部来自 E 文件**；§3.4 测试 XML 是盘上产物，非本轮运行。
>
> **状态口径**：完成 = 载体在树 + 有可指认证据（本轮实读/实跑，或 E 文件/日志/产物可指认）；部分 = 主链在树但有明确缺口；未执行 = 无载体或无执行证据；BLOCKED = 外部条件缺失或本批纪律禁止。**`OK (n tests)` 与 `-4 ×15` 并列时不等于通过。**

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md`（§1 研究 6、§2 实施 **78**、§3 流程 20）
- 基线：`docs/research/2026-11-05-batch-43-baseline.md`；盘点：`docs/research/2026-11-05-plan-closure.md`
- 证据索引：`docs/research/evidence/2026-11-05-batch-43/00-index.md`（14 个 `.md` + 1 个 `rulings/*.json`）
- 输入：批次 42 记录 `docs/research/2026-11-04-batch-42-record.md` §9.2（越权回滚）/§9.3（S4 两层裁定）/§9.5（遗留）

## 0. 口径与基线

### 0.1 项数与编号口径（ask 写 100；实账 6+78+20=104）

本 ask 与计划标题原写“100 = 6 + 74 + 20”，计划 §2 原写“74”。本记录员按行首 `^\d+\.\s` 独立解析（实跑）：

```text
count 78  min 1  max 78  missing []  dups []
first [(1, 27), (2, 28)]  last [(77, 125), (78, 126)]
```

即 **§2 实列 78 个连续编号（1..78，无缺号无重号）**，实账 **6 + 78 + 20 = 104 行**。计划“订正”段（`plan:133-134`）已改标题/§2/合计为 78/104；基线 F1 与交叉复核独立同判。**“100/74”登记为计划口径缺陷（连续第三批：41 记 55 vs 62、42 记 74 vs 90、43 记 74 vs 78）**，本记录按 78 对账。

> `.zcode/` 被 `.gitignore:44` 忽略，计划不在版本控制内；修复环对计划的订正是**本地订正**，git 不可审计。

### 0.2 基线事实（本轮实读/实跑）

| 项 | 值 | 证据 |
|---|---|---|
| HEAD（记录时） | `0fef943641f53560b92ce2afec4da22bf52f5e86`（`0fef9436`） | 本轮 `git rev-parse HEAD` |
| HEAD = origin/main | 基线件自述 `git rev-list --left-right --count HEAD...origin/main` = `0 0`；**本轮未 fetch** | `2026-11-05-batch-43-baseline.md` §1.1；本轮未跑 fetch（git 写 refs 属禁止） |
| 工作树 | **609** 条（`porcelain`）/ **3835**（`-uall`）；**暂存 0** | 本轮 `git status --porcelain \| wc -l`、`-uall`、`git diff --cached --name-only \| wc -l` |
| 工作树时点差 | 基线 3722（`-uall`）→ 索引整理 3822 → 修复环 3832 → 本轮 3835；**互不可推** | 基线 §1.2；索引 §1；本记录 §5 S18 |
| DB schema | 73（92 表 / 2 view / hash `1d086050…`）；本批未改 | `core/database/.../StudyDatabase.kt:142`；四方门输出 |
| 备份选择 mask（行为面） | `ALLOWED_MASKS == {9,13,15}`；`require` 在 `:140`、`isCreatable` 在 `:90` | `core/domain/.../BackupSelection.kt:166`（本轮实读） |
| 热区（代码） | `core/domain` **2 M**（`BackupRepository.kt`、`TutorJudgedReview.kt`）+ 1 未跟踪锁；`core/ui` **1 M**（`Theme.kt`）+ 1 未跟踪锁；`core/export` **16 M**（5 主码 + 11 测试）+ 1 未跟踪锁 | 本轮 `git status --porcelain -- core/domain core/ui core/export` |
| 热区（文档/配置） | `docs/current` **7 M**；`settings.gradle.kts` 0；`SmartMistakeBookApplication.kt` 0 | 同上命令 |
| 本批 research 载体 | `docs/research/2026-11-05-*.md` **10 份**（R1–R6 + 基线 + 发版门 + WS6 收口 + 本记录） | 本轮 `ls` |
| 本批 evidence 载体 | **14 `.md` + 1 `rulings/*.json`**，全部未跟踪（`git ls-files` 计数 0） | 本轮 `ls`/`git ls-files` |
| 本批提交 | **0**（共享工作树，记录员不做 git 写） | 本轮 `git diff --cached` = 0；`git ls-files docs/research/2026-11-05-*` = 0 |

### 0.3 源漂移与“设备绿”的可复现边界

1. **6 份 app APK**（索引 §2.1）：WS2 `ec135844…`、WS3 正常 `028eb07e…` / 控制 `119812ff…`、WS4+WS11 `1aee470a…`、设备轨 `9b832f96…`、WS5 `a5fa0c4f…`。**无一份绑定最终树**；“全绿”必须指名 APK + serial + 次数。
2. **WS2 跑批次 42 的已安装字节**（与批次 42 索引 §2.1 逐字相同；`ws2-kd76-a6.md:14` 自述未重装/未重建）→ “同一构建两批”成立，“批次 43 构建”不成立。
3. **WS4 与 WS11 共用 5 包**（`ws11-performance.md:17`）。
4. **树持续漂移**：门禁 3801 脏 → 索引 3822 → 本轮 3835；设备结论只绑 §4 的 APK sha。
5. **部分运行晚于设备跑**：设备轨 08:44–11:30；两份 XML 为 `2026-11-04T04:59:49Z`（12:59）与 `05:02:13Z`（13:02）→ 设备绿不覆盖这些用例。

### 0.4 不可变清单与热区（本批执行口径）

- **6C-2 三项（2026-10-24 用户已确认）**：mask=8「不提供」、mask=0「禁止」、派生索引恒裁。本批**只加防漂移断言，不改行为**（`plan:5`；`6c2-rulings.md`）。
- **热区禁改**：`core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、`Application/Root`；`core/export` 只做 KD-75 最小 hunk。
- **纪律**：不提交锁/密钥/`build/`；Gradle 只在门禁阶段串行；禁 `connectedLocalFirstDebugAndroidTest`；外部项给 runbook 并标 BLOCKED。
- **实际影响**：行为面零漂移（四方门 `allowedMasks == [9,13,15]` EXIT=0）；文档层 0/8 残留未清（§5 S3）。

## 1. 104 项逐项对账

> 状态词见文首。每行只写该编号的交付物/判据与可指认证据；未验证项逐行标出。

### 1.1 研究线 6 行（R1–R6）

| # | 研究题 / 落盘 | 关键结论（读该件头部/摘要所得） | 状态 | 未验证 |
|---|---|---|---|---|
| R1 | KD-75 C2 模型解析文本 → `2026-11-05-kd75-model-text.md` | 来源 = `CapturedQuestionDocument.blockEvidence` 的 `MODEL_DOCUMENT_PARSE` provenance；导出层禁止重读文档；4 个 `MistakePdfExportInput` 构造点必须逐个传播；C2 只声明 **PDF 链**，DOCX 无 `/AIGC` 机制 | 完成 | 传播面研究为设计输入；实现证据在本批 WS1（§1.2） |
| R2 | KD-76 A6 协议 → `2026-11-05-kd76-a6-protocol.md` | 预注册 plan、两批身份/启动/时间/证据门、I0–I10 合取、不合并 n；**明确 C4 = 死亡确认 2 000 ms，不是“产品进程首请求”** | 完成 | 样本口径冲突未裁定（§5 S4）；两次 20 轮实跑在 WS2 |
| R3 | isolation 自证 → `2026-11-05-isolation-selfwitness.md` | `-4` 单独不能区分任何东西；可区分判据 = start 事件 + stack 首帧 + witness 三层 + 墙钟/CPU 窗；witness 能力本批新建；15 例 floor 合计 44 450 ms | 完成 | 根因假设由 WS3 实测收敛（§1.2 WS3-20） |
| R4 | 干净树发布构建 → `2026-11-05-clean-tree-release.md` | `git archive` 可行；**Gradle 分发与依赖缓存都在工作区 `.gradle`**，新目录默认不可离线；`android-env.ps1` 与门禁日志的 `ANDROID_HOME` 不一致是前置 | 完成 | `--offline` 决定性验证在 WS5 实跑 |
| R5 | 契约 7 M 归属 → `2026-11-05-contract-merge-protocol.md` | 7 个 M 中 6 个与分支提交 `32026050` 逐字节相同、README 是其 10 行 + 未提交层；patch 目标 `visual-generation-contract.md` **不在 7 M 内且干净**；“7 M 阻塞 patch”是错口径，真正阻塞是热区纪律 | 完成 | 合并干跑只读，未应用 |
| R6 | 计划收尾盘点 → `2026-11-05-plan-closure.md` | 74 行盘点 = 9+7+34+12+6+6；可做 30 / 热区 26 / 外部阻塞 17 / 已关闭 1；KD 17 条 open 逐条出口；§9.5 残余 26 项 | 完成 | 写作时各 WS 未收口，执行结果全部 UNVERIFIED（该件自述）；**§9.1/§10.2 的 6C-2 重裁段须作废**（交叉复核 C3） |

补充：交叉复核件 `research-crosscheck.md`（6 题：2 够用 / 2 需新研究 / 2 冲突；C1–C9 九条发现），是本批研究线最硬的对抗产物。

### 1.2 实施 78 行

#### WS1 KD-75 C2/C4（1–10）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 1 | 识别“模型解析文本”来源 + 判据函数 | `PdfAigcContentPolicy.hasModelParsedText(provenanceNames)`（`core/model/.../PdfAigcContentPolicy.kt:46`）；`MistakePdfEligibility.kt:157-159` 从 `blockEvidence` 派生（本轮 `git diff` 实读） | 完成 | 生产写入点 `OpenAiModelResponseParsers.kt:261` 为转录 |
| 2 | 含模型解析文本时强制注入 `/AIGC` | `MistakePdfExporter.kt` 侧 `hasModelParsedText = input.hasModelParsedText`（`git diff` 实读）；与 AI 图同一注入点 | 完成 | 注入实现本体在批次 42 已落，本批补判据来源 |
| 3 | 纯用户内容负向（不注入） | JVM `pure user text stays unlabeled`；设备 `pureUserContentExportHasNoAigcInfoEntry` + `pure-user-unlabeled-5570.pdf` 经 pypdf 读回无 `/AIGC` | 完成 | 见 §3.3 本轮 pypdf 重读 |
| 4 | 字面版本升版（缓存键/指纹含 AIGC 判据版本） | 单题前缀 → `"mistake-export-v4-aigc-gen-${GENERATION}"`（`MistakePdfEligibility.kt:461`）；哨兵 `.aigc-policy-gen-2`（`MistakePdfExporter.kt:202`；`GENERATION=2` 在 `PdfAigcContentPolicy.kt:30`）；批量/卷面键经各题 `inputSha256`/`sourceInputSha256` **传递**携带（`MistakePdfBatchEligibility.kt:121`；`MistakePdfPaperEdit.kt:233`）；草稿 `FORMAT_VERSION 2` 显式拒绝旧 v1（`MistakePaperDraftStore.kt:146,235`） | **完成（传递闭包）** | 批量/卷面前缀字面未改；台账“三处同源升版”措辞过宽（§5 S7） |
| 5 | JVM 三类内容用例（AI 图 / 解析文本 / 纯用户） | `MistakePdfExporterAigcJvmTest` 现 **11 个 `@Test`**（含解析文本无图、纯用户、sentinel/指纹/策略一致）；盘上 XML `11/0/0`，`2026-11-04T05:02:13Z` | **完成（产物级）** | 记录员未运行 Gradle；XML 非本轮产物 |
| 6 | 设备：解析文本导出 → pypdf 读回 `/AIGC` | 5584 `OK (7 tests)`、5570 `OK (7 tests)`；`model-text-labeled-5570.pdf` 2,514,765 B、sha `88f661a6…`、`/AIGC Label=1`、`contains_AIGC_bytes True`（本轮 pypdf 重读） | 完成 | 绑定 app `9b832f96…` + export.test `08ea2641…` |
| 7 | 设备：纯用户导出 → 无 `/AIGC` | `pure-user-unlabeled-5570.pdf` 2,492,555 B、sha `1d510294…`、metadata 空、无 `/AIGC` 字节（本轮重读） | 完成 | 同上 |
| 8 | 旧无标记缓存不回退（重导或拒绝） | 设备 `oldUnmarkedArtifactUnderTheSameKeyIsRejectedAndRebuiltWithAigc`、`modelParsedTextArtifactUnderTheOldKeyIsRejectedAndRebuiltWithAigc` 均绿 | 完成 | 机制 = 哨兵代际清理 + 键升级，非“拒绝旧文件” |
| 9 | KD-75 C1–C8 逐条对账 | `ws1-kd75-c2.md` §1：C1–C7 PASS（C2 限 `MODEL_DOCUMENT_PARSE`；C5–C7 由项目 reader/`PdfRenderer` 承担）；C8 未处置 → KD-84 | 完成 | 独立 pypdf 只覆盖 C2/C3 |
| 10 | 台账回写 | `docs/known-defects.md:2879` KD-75 **closed（PDF 链口径）**；`:3306` KD-84 新开；`:3318` KD-85 新开 | 完成 | 关闭范围见 §5 S15 |

#### WS2 KD-76 A6（11–18）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 11 | 同构建两次独立 20 轮脚本（时间分离 + 每轮 boot_id） | `tools/ci/run_kd76_a6.sh`、`kd76_identity.py`、`a6_plan.py`、`run_kd76_rounds.sh`（A6 字段 + 逐文件哈希链 + `manifest.sha256`）；预注册 plan sha `1cc29dc0…` | 完成 | 见 `ws2-kd76-a6.md` §1 |
| 12 | 两次样本独立判词（各自 n/p95/G1/G2），不合并 n | `kd76_a6_verify.py`（I0–I10 合取，拒绝 n=40/合并 p95）+ `test_kd76_a6_verify.py` 12 例；本轮实跑判词 **exit 2** | 完成 | — |
| 13 | 产品进程首请求（非测试进程）计时入口 | **BLOCKED（结构性）**：`SmartMistakeBookApplication.kt:364-368` 只传三参、`visualEngine` 默认 null（`DefaultFigureGenerationCoordinator.kt:885`）→ 产品进程 JS 恒 `UNAVAILABLE`（负控 `V01VisualChainInstrumentedTest.kt:276`）；需改 `Application`（热区） | BLOCKED | runbook `ws2-kd76-a6.md` §7 |
| 14 | 设备实跑第一次 20 轮 | a6b batch A：20 requested / **19 成功 / 1 产品失败**（round 1 `HARD_DEADLINE_EXCEEDED: worker died`）；boot_id 20/20 唯一；G1 RED | 完成（含产品红） | 不重跑洗绿（A7） |
| 15 | 设备实跑第二次 20 轮（同 APK sha） | a6b batch B：19 成功 / 1 产品失败（round 17 同型）；与 A 间隔 **30.47 min**；boot_id 与 A 交集 0 | 完成（含产品红） | 同构建 `ec135844…` |
| 16 | 29.5 s 链四段实测与相加报告 | C1/C2 有 n=19 分布（A p95 4205/2249 ms；B 5303/942 ms）；C3 仅 KD-66 witness（上界）；**C4 只有布尔**（`abandonForTest()` 返回 `Boolean`，`VisualJsClientEngine.kt:1205`） | **部分** | Σ 实测 max = UNVERIFIED；四段 p95 不可相加 |
| 17 | A6/C4 逐条对账 | `ws2-kd76-a6.md` §8.1/§8.2：A6-①/④/⑤/⑥ 绿，②/③ 因 G1 红不可判；C4-①/② 无毫秒、C4-③ 手工传参、C4-④ 计划定义与权威冲突 | 完成 | 见 §5 S4 |
| 18 | 台账回写 | `docs/known-defects.md:3010` KD-76 批次 43 WS2 Progress，**维持 open** | 完成 | — |

#### WS3 isolation 定案（19–28）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 19 | 自证探针（方法名/开始/结束/CPU 窗） | `IsolationWitness.kt`（新；rule/gate/body/suite + canary + 宿主注入 APK sha）；15 例体改 `= witnessBody {`（断言零改动）；检查器 + floor 合计 44 450 ms | 完成 | probe-04 78 行、`EXECUTED=15`、canary ok（本轮 checker exit 0） |
| 20 | `-4` 根因修复 | ① 探针每进程只测一次并缓存；② `own_cpu` 分母写成单核而 `utime+stime` 是全线程和。只改分母为 `windowMs * processors`（`LowLoadProbe.kt:143-148`），0.80/30 ms 未改；probe-04 → **15/15 EXECUTED** | 完成 | 属**放宽**（2 核有效阈值 1.6×window），必须如此表述（§5 S12） |
| 21 | 低负载窗程序化前门 | `snapshot/calibrate/gate` 实跑：snapshot 三项 0；地板 `nproc=2`、`la_ratio_p95=0.45`、`la_ratio_max=0.475`；门 **`INVALID`**（`0.51 > 0.50`、`0.545 > 0.30`） | 完成（未通过） | 地板高于绝对上限 → 本机无可判 PASS 的窗（§5 S13） |
| 22 | 15 例单臂实跑（witness 齐全） | probe-04：78 行 witness、`EXECUTED=15`、`violations=[]`、总 body 墙钟 69 360 ms ≥ 44 450 ms；逐例 floor 全过 | 完成 | 本轮重跑 checker exit 0（§3.3） |
| 23 | OFF/ON 同 APK 重复运行（环境漂移层） | 同 app `028eb07e…` + 同 test `8a63b236…`，六 run（OFF/ON × 3）：每 run PASS=15 / FAIL=0 / SKIP=0、witness 15 EXECUTED | 完成 | 窗仍 INVALID，只能作“未认证窗内稳定”描述 |
| 24 | T4 控制构建 + 安装 APK 比对 | 控制 `119812ff…`（0 打点）：`emit+verify` `ok=true`、`anchor_count=0`、`identity_source=installed_apk`；正常包 `028eb07e…` `anchor_count=6` | 完成 | 时间戳面两臂不可区分（`@After` 先关客户端 → 恒 null） |
| 25 | 红集逐方法比较 | 两臂 × 3 轮 × 15 方法 = 90 格，`FAIL/ERROR = 0`，红集为空且两臂相同；控制构建 15/15 PASS | 完成（描述性） | 窗 INVALID → 报告拒绝升格 `PAIRED_RED_SET_IDENTICAL` |
| 26 | 因果判定（仅两层都成立才写） | 第 1 层未成立（窗 INVALID），第 2 层身份成立但 k 未在认证窗内积累 → **不写因果** | 完成（不写） | 禁写“T4 排除”“isolation 通过” |
| 27 | 证据落盘 | `build/ws3-batch43/`（probe/六 run/门/控制 marker）+ `ws3-isolation-selfwitness.md` | 完成 | `build/` gitignored |
| 28 | 台账回写 | `docs/known-defects.md:3012` KD-76 批次 43 WS3 Progress，**配对仍 UNCONVERGED** | 完成 | — |

#### WS4 设备复基线（29–36）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 29 | 重建 post-rollback APK 并记录 sha | `:app:assembleLocalFirstDebug` + 4 个 androidTest 包，**EXIT=0**、358 tasks；app `1aee470a…`、app.test `cd35452e…`、data.test `48213049…`、database.test `671b3815…`、export.test `08ea2641…`；host/设备双向 sha 一致 | 完成 | 构建源 = HEAD + 578 脏（§5 S18） |
| 30 | 迁移矩阵 1→73 | `FullMigrationMatrixInstrumentedTest` **16/16**，`Time: 420.671 s`；循环 `for (version in 1..73)` 每版建库→开库→断言非破坏 | 完成 | 420.671 s 是设备速度差异，不记性能回归 |
| 31 | 备份 34/34 + rowCounts + trimmer + c4 | backup `OK (34 tests)`；rowcounts `OK (2 tests)`；trimmer `OK (10 tests)`；c4 `OK (8 tests)` | 完成 | `73.json` 92 实体 |
| 32 | 错因账本 3/3 | `ErrorCauseLedgerInstrumentedTest` `OK (3 tests)` | 完成 | — |
| 33 | PDF 设备 10/10 | `PdfAigcDeviceRoundTripInstrumentedTest` `OK (10 tests)`；`MistakePdfExporterAigcInstrumentedTest` `OK (7 tests)`（批次 42 记 5 例，现为 7 例） | 完成 | 本轨不读 PDF 字节 |
| 34 | 崩溃签名九类 | 34 份套件 logcat：前八类 **0**；第九类 `death was not confirmed` **2**（p10 + worker 各 1，均设计负向） | 完成（覆盖受限） | 无 Java 层崩溃 ≠ 原生库健康 |
| 35 | worker 生命周期 / 强停恢复 | 干净窗 spawn 5 / warm-up 5 / failed 0 / 无孤儿；强停后 `COLD`、`TotalTime 3312 ms`、`quick_check=ok`、`user_version=73` | 完成 | 主库行数 0 因起跑前 `pm clear`；不覆盖 LMK |
| 36 | 全量套件 32 套件（含 p09 定性） | **258 声明 / 243 执行 / 241 绿 / 2 红 / 15 跳**；p09 红（三次跑 红/绿/红，跨 1300 ms 重建阈值）；selrestore 红（懒开库装置缺陷，05:29 已修，设备轨 `OK (11)`） | **部分** | “全绿/通过”不成立（§5 S9/S10） |

#### WS5 发版前置（37–44）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 37 | `git archive` 干净副本 + 离线构建（成败都记） | 归档 3,397 文件 / 450 MiB / tar sha `bcfc649f…`；**HEAD 原样 EXIT=1**（`KnowledgeQuizTasks.kt:126` `when` 缺分支；`RoomStudyDatabase.kt` 6 处 override 断链）；HEAD+3 文件 **EXIT=0 / 4m02s / 412 tasks**；离线关键词 0 | 完成（HEAD FAIL） | 成功轮是增量；单次冷构建 UNVERIFIED |
| 38 | release R8 mapping/checksum（副本内） | mapping 892,986 行 / 3,754 项目类 / R8 9.3.16；5/5 短名命中、负控 0；APK `a5fa0c4f…`（155,816,984 B） | 完成 | 一次性本机签名 |
| 39 | 16KB ps16k 设备复核（x86_64） | `zipalign -P 16` exit 0；14 `.so` 全 Stored、ELF 14/14 PASS；5620 `PAGE_SIZE=16384`、设备 sha 一致、`COLD 1030 ms`、九类签名 0 | 完成 | 未触发三条随包 native 路径 |
| 40 | arm64 可用性/等待清单 | `FATAL \| Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host`；四步等待清单 | 完成（BLOCKED） | 外部硬件 |
| 41 | 签名配置 runbook（不落密钥） | 四变量契约 `app/build.gradle.kts:125-128`；缺一失败；仓内密钥 0、`RELEASE_*` 0；runbook 五步 | 完成（生产签名 BLOCKED） | 文档与代码不一致：`RELEASE.md:20` 的 `keystore.properties` 无人读 |
| 42 | mapping 保留与版本绑定 | 六条策略 + 来源三件套（`headSha`/`archiveSha256`/`overlay[]`）；字段全部算出 | **部分** | sidecar 文件**未生成**（G-REL-06/27 PARTIAL） |
| 43 | 依赖钉版门 + 供应链快照 | pins exit 0（quickjs 1.0.15 / stdlib 2.3.21）、负控 exit 1；346 坐标（334 有 license / 12 上游无标签）；42 直接依赖（37 落后 / 5 持平） | 完成 | 锁文件门走跳过态（§5 S21） |
| 44 | 发版门清单更新 | `2026-11-05-release-gates.md`（G-REL-00…29）；00/09/19 红、16/17 BLOCKED | 完成 | `docs/current/release-gates.md` 热区未改 |

#### WS6 合同与计划（45–50）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 45 | 契约 P0 patch：清空则应用，否则逐文件冲突清单 | `docs/current` 仍 **7 M**（99+/5−，暂存 0）→ 不应用；落盘 `contract-p0-conflict-list.json`（8 条：A 2/B 3/C 3）；patch 目标干净、`git apply --check` exit 0；`merge-tree` 干跑 25 冲突/7 文件 | 完成 | 只做只读检查，未应用 |
| 46 | 计划收尾盘点落盘 | `2026-11-05-plan-closure.md`（407 行；74 行盘点；可做 30 / 热区 26 / 外部 17 / 已关闭 1） | 完成 | 该件 §9.1/§10.2 的 6C-2 段须作废（C3） |
| 47 | MASTER-LINE 批次 43 行 | `docs/REFACTOR-MASTER-LINE.md:7-8`：WS3 与 WS6 两条批次 43 行 | **部分** | WS1/WS2/WS4/WS5/WS8/WS9 无 MASTER 行 |
| 48 | 决策台账批次 43 节 | `agent-first-refactor-decisions-2026-09-23.md:3828` 起“批次 43 契约 P0…”节 | 完成 | — |
| 49 | KD 台账全量审计 | 84 条标题（83 + KD-84）、**open 17 / closed 67**；逐条处置表；KD-28/29/82 标题未改（hygiene） | 完成 | 关闭质量未重判 |
| 50 | 批次 44 计划草案 | `.zcode/plans/plan-2026-11-05-batch-44-acceptance.md` 已在树且干净（96 行，1000 项验收）；补残余输入 26 + 5 条 KD 小修 | 完成 | 不另造第二份草案 |

#### WS7 遗留缺陷（51–56）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 51 | S10：KD-83 段下 KD-75 错位修正 | `docs/known-defects.md` 现为 `:3292 KD-83` → `:3306 KD-84` → `:3318 KD-85`，KD-83 段内不再挂 KD-75 正文 | 完成 | 本轮按标题行实读 |
| 52 | S11：v4 夹具 golden 纳入四方门 | 本轮实跑 `check_backup_plan_sync.py` 输出含 `v4 fixture: bytes == golden == sidecar`、`v4 fixture golden: declared shape…`、`closureDigest/rowCountsSha256` 重算行 → **EXIT=0** | 完成 | 见 §3.3 |
| 53 | S9/S12：`BackupSelectionRestore` 懒开库装置缺陷 | `BackupSelectionRestoreInstrumentedTest.kt:244-260`：改为 DAO 读强制开库 + `readDatabaseVersion()==73`；设备轨 data.test `318a13ef…` 复跑 `OK (11)` | 完成 | WS4 红早于该修复 |
| 54 | `p09` 红例定性 | WS4：三次跑 712/933 ms 红、2046 ms 绿 → “时序抖动”；设备轨：3 次 2 台设备同断言失败 → “代码/期望不一致” | **部分（未收敛）** | 两轨结论相反，引用必须带 APK + 次数（§5 S10） |
| 55 | `summarize_kd76` 其余边界复核 | 修复环把“每轮 2 条 `KD76_SAMPLE` 双写”改为只折叠**逐字相同**的 `(status,payload)`；本轮实跑三文件 **44 passed**；a6b 重算无 duplicate 行 | 完成 | 见 §5 S6 |
| 56 | `isolation_pair_report` 运行时演练（两臂方法齐） | `build/ws3-batch43/L1-pair.json`：六 run 输入、`missing_methods {OFF: [], ON: []}`、`verdict=UNCONVERGED`、`paired=false` | 完成 | 报告工具被真实数据演练；`run_isolation_pair.sh` 端到端**未执行**（S4 第 1 层不用 runner） |

#### WS8 AUD-08（57–60）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 57 | 人工金标抽样执行计划（BLOCKED 则 runbook） | `ws8-aud08-human-gold-runbook.md`：抽样框 155 → 系统抽样 24 页，sha 复算 24/24 一致；≥2 标注员 + 1 裁定者的输入条件 | **BLOCKED（外部）** | 标注/裁定/v2 全部未执行 |
| 58 | 门值复算（FAIL 维持） | 本轮实跑 `check_split_gate.py` → **FAIL / exit 1**：① 4/7 = 0.5714 < 0.9；③ 0/16 = 0.0000 ≤ 0.05；裁定 `validate_rulings` PASS | 完成 | 阈值/分母零改动 |
| 59 | 结果面向禁令复扫 | `ws8-aud08-prohibition-rescan.md`：error 0 / warning 3（既有）；七条禁令逐条通过 | 完成 | 静态扫描不覆盖 Kotlin/动态路径 |
| 60 | 审计报告 | `ws8-aud08-audit-report.md`：FAIL 维持；门值只对金标 `f9e92c47…` × 导出 `9b49b0c4…` 成立 | 完成 | 当前设备库准确率 UNVERIFIED |

#### WS9 6C-2 防漂移（61–64）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 61 | 不变量测试：mask=0/8 必须被拒绝 | `SixC2RulingInvariantTest.kt`（3 例，未跟踪）：回执 0/8 抛错、读侧拒自称 0/8、合法 9 正向对照；盘上 XML `3/0/0`，`2026-11-04T04:59:49Z` | **完成（产物级）** | 记录员未跑 Gradle；**红相未演示**（改行为才红，本批禁） |
| 62 | 不变量测试：`ALLOWED_MASKS == {9,13,15}` | 同文件 `allowed mask set is exactly nine thirteen fifteen in domain and data layers`；域层与数据层各自精确相等；四方门输出 `allowedMasks == [9, 13, 15]`（本轮实跑） | 完成 | — |
| 63 | 设计件/KDoc/测试三方一致断言 | 设计件机器块一致；**KDoc/UI 不一致**：`BackupRepository.kt:33,58` 与 `SecondaryScreens.kt:1195,1208-1210` 仍写 0/8 合法（工作树未提交残留） | **BLOCKED** | `core/domain` 热区 + `SecondaryScreens.kt` 他线共享 M；断言未新增、未运行 |
| 64 | 事故记录回链（`6c2-rulings.md`） | `docs/research/2026-11-04-6c2-rulings.md` 末节“回链：批次 43 WS9 防漂移钉子”，逐例列“消灭的具体失败” | 完成 | — |

#### WS10 安全/依赖（65–68）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 65 | Mimosa 完整审计重试 | jobId `scan-job-mw18lxy6…`、scanId `scan-2026-11-03T22-07-50…`、seal `d06d4e4b…`；`runStatus=inconclusive`、`completeness=partial`、13 findings（6 HIGH：1 跟踪 + 5 未跟踪） | 完成（无完整结论） | **不得称“安全/无漏洞”** |
| 66 | 依赖 license/维护快照 | 346 坐标（Apache 系 313、ML Kit ToS 7、Android SDK 4、Play Core 3、BSD 3、MIT 2、GPL+CPE 1、EPL 1、无声明 12）；直接依赖 42（37 落后） | 完成 | 12 个 POM 无 license，未上游取证 |
| 67 | 锁文件窗口（隔离副本）或等待清单 | 等待清单：14 未跟踪锁、0 已跟踪、`:benchmark` 缺锁、强制态 36 errors；**G3 转不满足**（两个解析输入脏） | **未执行（等待清单）** | 禁 Gradle；KD-72 维持 open |
| 68 | 明文密钥扫描（只报位置） | 强特征令牌 0；跟踪文件 2 处 `sk-` 为 model-task 标识、若干测试夹具字面量；未跟踪非忽略 0；敏感文件名 0 | 完成（覆盖内否定） | 历史内容/二进制未扫（UNVERIFIED） |

#### WS11 性能（69–72）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 69 | warm-up/首帧基线 | 冷启 7 轮 `4313/4266/3548/3467/3503/3432/3340 ms`，p50 3503；HOT 5 轮 182–214 ms，p50 189；gfxinfo 4 帧全 janky | 完成（模拟器） | 真机/arm64 BLOCKED |
| 70 | 导出耗时基线 | 产品路径单题 A4（记录表 created→finished）n=6：659/731/900/706/689/763 ms，p50≈719；60 题批量 518/545/569 ms（64 页） | 完成 | 冷/热缓存无可分辨差异 |
| 71 | 内存峰值（预览/导出） | 最小宿主峰值：60 题 48.6–55.4 MB、长文档 42.5–43.2、PDF 预览 42.4–43.9、D-04 67.2–72.7 MB；生产 app 冷启稳态 188.3 MB | **部分** | 生产进程“仅导出阶段”PSS UNVERIFIED（被 KB 批量更新挡住） |
| 72 | 性能回归结论 | 首个基线，**不宣称“无回归”**；DB `PerformanceGateTest` 6/6、搜索 p95 50 ms | **部分** | 无历史对照；模拟器不作产品判定 |

#### WS12 收尾（73–78）

| # | 交付物 / 判据 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 73 | 计划剩余项最终分派（本线/用户/他线） | 本记录 §1 + §6；R6 §9 三类唯一出口；WS6 §5.2 残余 26 + 5 条 KD 小修 | 完成 | 分派为建议，未获用户确认 |
| 74 | 外部阻塞 runbook | 本记录 §6.1：Provider/签名/arm64/人工金标/产品进程首请求/6C-2 item 63/isolation 低负载窗/锁文件 | 完成 | 执行者须为有权限者 |
| 75 | 记录 §0 口径 | 本文件 §0 | 完成（本记录） | — |
| 76 | 提交清单 | 本文件 §7 | 完成（本记录） | 未做 git 写 |
| 77 | 下一批建议 | 本文件 §6.4 | 完成（本记录） | — |
| 78 | 发版就绪判定 | 本文件 §7.1：**不成立** | 完成（本记录） | 四项硬阻塞 |

### 1.3 流程 20 行

> 阶段号沿用批次 41/42 定义（P0 基线、P0b 研究复核、P10 集成、P11 单测门、P12 强门、P13 设备、P14 证据、P15/P16 复核、P17 修复、P18 环后强门、P19 记录）。**§3 的门禁一律按日志文件名 + 时间指认，不以阶段号代替命令。**

| # | 流程项 | 落点/证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| PR1 | 基线核对（P0） | `2026-11-05-batch-43-baseline.md`（460 行；HEAD/origin/工作树/DB/schema/热区/KD 现状/F1–F7） | 完成 | 远端真实性依赖未 fetch 的本地 ref |
| PR2 | 研究交叉复核（P0b） | `research-crosscheck.md`：6 题判定 + C1–C9（C1/C2/C3 高）+ H1–H14 硬约束 | 完成 | 只读，未跑设备/门禁 |
| PR3 | 集成（P10） | 无独立集成报告；载体一致性由索引 §2/§3 与各 E 文件绑定 | **部分** | 无包名/import/热区零改动的机器核对产物 |
| PR4 | 单测门（P11） | `build/batch43-unit.log`：12 模块 `testDebugUnitTest`，末行 `BUILD SUCCESSFUL in 3m 2s`、158 tasks（62 executed） | **完成（任务级，有瑕疵）** | **无显式 exit-code 行、不逐例**；测试结果目录被后续定向运行覆盖（§3.4） |
| PR5 | 强门（P12） | `build/batch43-strong.log`（08:27:59–08:35:54）：run 5 `exit 1`（Kotlin in-process ICE），`--rerun` 后 exit 0；末行 `PASS with 1 infrastructure retry` | 完成（有瑕疵） | 非单次干净通过；HEAD `0fef9436`、3801 脏 |
| PR6 | 设备轨（P13） | `E-43-DEV-01`（08:44–11:30，`9b832f96…`）+ WS2/WS3/WS4/WS11/WS5 | **部分** | 6 份 APK；2 红 + 15 跳；无最终树绑定 |
| PR7 | 证据（P14） | 14 `.md` + 1 `rulings/*.json` + 213 行索引；脱敏扫描（secrets/credential/email 0；home path 3 处） | 完成 | WS6/WS7/WS9/WS12 无独立 E 文件（WS12 在本记录） |
| PR8 | 独立核对（P15） | — | **未执行** | 无独立 review E 文件；研究线有交叉复核，不等于交付物独立核对 |
| PR9 | 对抗复核（P16） | 修复环 findings **F1–F24** 已落（上一版记录 §5），并回写索引/证据 | **部分** | 无“对抗复核”E 文件；findings 来源与复现命令只部分可指认 |
| PR10 | 修复环（P17） | 计划口径订正（78/104）、`summarize_kd76` 重复行折叠、KD-75 证据补件、WS4 verdict 订正、索引 §6.3 订正、KD-84/85 新开 | 完成 | 无环报告；改动只由 mtime/文件内容可辨 |
| PR11 | 环后强门（P18） | 修复环只跑只读门与工具测试；**无 `batch43-strong-postfix.log`** | **未执行（全量）** | 盘上 SixC2/AIGC JVM XML（12:59/13:02）是定向产物，不能代替环后强门 |
| PR12 | 记录（P19） | 本文件 | 完成 | — |
| PR13 | WS1/WS2 工具子件 | KD-76 工具三文件本轮 **44 passed**；a6b 重算 **exit 2 / NOT EVALUABLE**；`summarize_kd76` 折叠修复 | 完成 | — |
| PR14 | WS3 witness 子件 | `isolation_witness_check.py` probe-04 `--require-executed --require-floors` → **exit 0**、`EXECUTED=15`、`violations=[]` | 完成 | 见 §3.3 |
| PR15 | WS4 设备子件 | 32 套件 258/243/241/2/15；matrix 16/16；backup 34/34；PDF 10/10 + 7/7 | 部分 | 2 红 + 15 跳 |
| PR16 | WS5 发版子件 | HEAD FAIL / HEAD+3 EXIT=0；16KB 静态 14/14 + 5620 设备；arm64 FATAL | 部分 | 冷构建确定性/三条 native 路径未测 |
| PR17 | WS6 契约子件 | `git apply --check` exit 0、冲突清单 8 条、merge-tree 25 冲突 | 完成 | 只读，未应用 |
| PR18 | WS8 门子件 | 本轮实跑 AUD-08 → **exit 1**；裁定校验 PASS | 完成 | — |
| PR19 | WS9 不变量子件 | `SixC2RulingInvariantTest` 3 例；XML 3/0/0；四方门 EXIT=0 | 完成（产物级） | 红相未演示；记录员未复跑 |
| PR20 | WS10/WS11 安全性能子件 | Mimosa inconclusive；license 346；密钥 0；性能四类基线 | 部分 | 无安全结论；真机/生产进程内存缺 |

## 2. 各轨事实

### 2.1 轨状态总表（按 E 文件头表 `verdict` 原文）

| 轨 / E-ID | verdict 原文要点 |
|---|---|
| `E-43-DEV-01` | **PARTIAL**（文件无 `verdict` 字段）：WS1 设备 7/7 ×2 + pypdf；WS2 A6 **NOT EVALUABLE**（两批 19/20 或 10/20、7/20）；WS3 witness 15/15 执行但低负载门为合成判据；WS4 34 套件 31 OK / 2 Process crashed / 1 红 / 15 `-4` |
| `B43-WS2-KD76-A6-01` | **A6 NOT EVALUABLE（exit 2）**；KD-76 维持 open；item 13 BLOCKED；item 16 部分 |
| `E-B43-WS3-01` | **不写因果**：15/15 在未认证窗内真实执行、witness 齐全；低负载门 INVALID；T4 控制构建身份可核 |
| `E-43-WS4-01` | **PARTIAL**（修复环把原 `PASS` 订正）：258/243/241/2/15；两红使“复基线通过”不成立 |
| `E-43-WS5-01` | **PARTIAL**：HEAD 不可构建；16KB 过；arm64/生产签名 BLOCKED |
| `B43-WS8-*` / `step-aud08` | 门 **FAIL / exit 1**（0.5714）；人工金标 BLOCKED；禁令复扫 0 违规 |
| `B43-WS10-01` | **无完整安全结论**（`inconclusive`）；license 快照落盘；锁窗口等待清单；密钥扫描覆盖内 0 |
| `B43-WS11-PERF-01` | **PARTIAL**：模拟器四类基线；真机/arm64 BLOCKED；生产进程导出内存 UNVERIFIED |
| `E-43-WS1-01` | **PARTIAL**：C1–C7 有设备实测；C2 只覆盖 `MODEL_DOCUMENT_PARSE`；C2 自然语言改卷另立 KD-85；item 5 JVM 逐例 UNVERIFIED（记录员按 XML 补为产物级） |
| `B43-RESEARCH-CROSSCHECK-01` | 六题：2 够用 / 2 需新研究 / 2 冲突；C1–C9 |

### 2.2 必须并读的轨间张力

1. **KD-76 A6 的样本口径**：权威预注册 `2026-11-01-kd76-stats.md:268` 写“两批各 10 轮、合并 n=20”；本批计划 item 11/12/14/15 写“两次各 20 轮、各自判词、不合并 n”。本批按计划跑了 2×20（`a6-plan.txt` `roundsPerBatch=20`），与权威预注册互斥。**未裁定**（§5 S4）。
2. **KD-75 关闭范围**：关闭只覆盖 **PDF 链 + `MODEL_DOCUMENT_PARSE` provenance**；DOCX 无 `/AIGC` 机制，自然语言改卷产生的模型文本未纳入判据（KD-85）。不得写成“模型生成内容已全覆盖”。
3. **p09 两轨相反**：WS4=flaky（红/绿/红，跨 1300 ms 重建阈值）；设备轨=确定性失败（3 次、2 台设备同断言）。任何单次结论都不成立。
4. **isolation 两层不得混写**：第 1 层（同 APK 环境漂移）窗 INVALID；第 2 层（T4 控制构建）身份成立但 k 未在认证窗内积累 → 只写“身份可核 + 15 例真执行”，不写因果/T4 排除。
5. **`OK (15 tests)` + 15×`-4` ≠ 通过**：WS4 与设备轨两处表都把二者并列，易被读成通过；索引 N12 已再钉一次。
6. **6 份 APK**：跨轨数字不可合并；“批次 43 设备全绿”不成立。

### 2.3 KD-75 C2/C4 的代码现状（本记录员实读 `git diff`）

- **判据来源**：`MistakePdfEligibility.kt:157` 从 `blockEvidence` 派生；字段必填无默认值（`:365`），四类构造点逐个传播（单题 `:190`、批量 `MistakePdfBatchEligibility.kt:121` `any`、卷面 `MistakePdfPaperEdit.kt:233` 透传、草稿 DTO 必填 + `FORMAT_VERSION=2` 拒绝 v1）。
- **注入/缓存键**：`MistakePdfExporter.kt:241` 使用该字段；sentinel 由 `GENERATION=2` 派生（`:202`）。单题键含 policy tag（`:193,461`），批量/卷面靠指纹传递（§5 S7）。
- **风险边界**：`toDraft()` 只透传来源布尔，纯用户被模型改写后不标 → KD-85（`:3318`）。

### 2.4 6C-2 行为面（本记录员实读 + 门输出）

- `BackupSelection.ALLOWED_MASKS = linkedSetOf(9, 13, 15)`（`BackupSelection.kt:166`）；`requireCreatable()` 对 mask∉ALLOWED 直接失败（`:140-145`）；`isCreatable = isClosed && mask in ALLOWED_MASKS`（`:90`）。
- 四方门本轮输出 `allowedMasks == block maskPolicy.closedMasks：code=[9, 13, 15] block=[9, 13, 15]`、`PASS 四方一致`、`EXIT=0`。
- **文档层残留**：`BackupRepository.kt:33,58` 与 `SecondaryScreens.kt:1195,1208-1210` 仍写 0/8 合法（工作树未提交；HEAD 原文正确）。行为零漂移，但 item 63 的“三方一致”按实读会红（§5 S3）。

## 3. 门禁

> 本节只写**有日志/有输出**的运行。**本记录员没有运行 Gradle/设备**；标“（本轮实跑）”的是我亲手执行的非 Gradle 只读检查。

### 3.1 单测门（`build/batch43-unit.log`，转录）

| 项 | 值 |
|---|---|
| 任务集 | 12 个模块 `testDebugUnitTest`（`core:data`、`core:database`、`core:domain`、`core:export`、`core:markdown`、`core:model`、`core:ui`、`feature:capture/library/profile/review/tutor`） |
| 结果 | 末行 **`BUILD SUCCESSFUL in 3m 2s`**；`158 actionable tasks: 62 executed, 96 up-to-date`；`grep -inE 'FAILED|BUILD FAILED'` **零命中** |
| 缺项 | **没有显式 exit-code 行**；不逐例列出测试名 → “绿”只到 Gradle 任务级 |
| 测试 XML | 目录被后续定向运行覆盖：`core/data` 仅 `SixC2RulingInvariantTest.xml`（3 例）、`core/export` 仅 `MistakePdfExporterAigcJvmTest.xml`（11 例）、`core/model`/`core/domain` 无文件 → **12 模块逐例总数不可回读** |

### 3.2 强门（`build/batch43-strong.log`，转录）

| 项 | 值 |
|---|---|
| 时间/绑定 | 2026-11-04 08:27:59–08:35:54；HEAD `0fef9436`；`dirty entries (porcelain -uall): 3801` |
| 任务集 | 6 次 `--rerun` androidTest Kotlin 编译（data/database/ui/export/app LF/app SO）+ `:app:assembleLocalFirstDebug --continue` |
| 结果 | run 1–4、6、7 均 exit 0；**run 5 `:app:compileLocalFirstDebugAndroidTestKotlin` exit 1**（`INFRA: Kotlin in-process ICE, LargeDynamicMappedBuffer/withMappedRangeFrom`）；`run 5-retry --rerun` exit 0；末行 **`GATE RESULT: PASS with 1 infrastructure retry (NOT a clean single-pass run)`** |
| 判读 | 不是干净单次通过；任何“双门干净 exit=0”必须注明该重试 |

### 3.3 本轮亲手实跑的只读检查（逐字命令 + 关键输出）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `git rev-parse HEAD` / `status \| wc -l` / `-uall` / `diff --cached` | `0fef9436…`；**609 / 3835 / 0** |
| 2 | `git ls-files …/evidence/2026-11-05-batch-43/ \| wc -l` | **0**（全部未跟踪） |
| 3 | 计划编号解析 `^\d+\.\s` | `count 78 min 1 max 78 missing [] dups []`；`first [(1,27)] last [(78,126)]` |
| 4 | `check_backup_plan_sync.py` | **EXIT=0**；`PASS 四方一致`；含 `allowedMasks == [9, 13, 15]`、`v4 fixture: bytes == golden == sidecar` |
| 5 | `check_split_gate.py` | **SPLIT_EXIT=1**；① `4/7 = 0.5714` 未过；③ `0/16 = 0.0000` 过；判定 FAIL |
| 6 | `kd76_a6_verify.py --plan …a6b… --batch-a … --batch-b …` | **A6_EXIT=2**；`A6: NOT EVALUABLE`；两批 `n=19/20, G1 RED, p95=UNVERIFIED`；无 duplicate 行 |
| 7 | `pytest test_summarize_kd76.py test_kd76_a6_verify.py test_kd76_identity.py -q` | **44 passed in 4.53s** |
| 8 | `isolation_witness_check.py --witness …probe-04… --require-executed --require-floors` | **EXIT=0**；`ok=true`、`violations=[]`、`EXECUTED=15`、`canary_ok=true` |
| 9 | 读 `gate-5566-1.json` / `floor-5566.json` / `L1-pair.json` | 门 `INVALID`（`la_ratio_p95/max`）；地板 `nproc=2, p95=0.45, max=0.475`；配对 `UNCONVERGED, paired=False, missing_methods={'OFF':[],'ON':[]}` |
| 10 | `pypdf` 重读 `ws1/pdfs/*-5570.pdf` | 正向 2,514,765 B、sha `88f661a6…`、`/AIGC Label=1`、含字节 **True**；负向 2,492,555 B、`1d510294…`、metadata `[]`、**False**；两份无 `-5570` 同名文件 **0 B** |
| 11 | `head/tail` 两份门禁日志；`grep` 源码行号 | 见 §3.1/§3.2；行号见 §2.3/§2.4 |

### 3.4 盘上测试产物（不是本轮运行）

| XML | tests / fail / err | 时间戳（UTC） | 归属 |
|---|---|---|---|
| `core/data/.../TEST-…SixC2RulingInvariantTest.xml` | **3 / 0 / 0** | `2026-11-04T04:59:49.183Z` | WS9 items 61/62 |
| `core/export/.../TEST-…MistakePdfExporterAigcJvmTest.xml` | **11 / 0 / 0** | `2026-11-04T05:02:13.242Z` | WS1 item 5 |
| 其余模块 XML | 被覆盖/缺失（core/data 1 文件、core/export 1 文件、core/model 0） | — | **不可回读 12 模块逐例总数** |

> 这两个 XML 证明“在某个时点该测试类通过”，不证明“当前树全量门通过”，也不证明“设备绑定该源码”。

### 3.5 门禁判读边界

- 单测门：任务级 PASS，**无逐例**；强门：**带 1 次基础设施重试**；两者都跑在共享脏树上。
- 修复环后**没有**环后强门；环后只有只读门（四方门）、工具 pytest 与上述定向 XML。
- `build/` 全部 gitignored，复核须重跑；`build/ws8-batch43/gate-report-b43.json` 三批逐字节相同（`944d0951…`）只说明输入未漂移，不说明准确率。

## 4. 设备证据（仅 E 文件/产物可证）

> 设备串：emulator-5584（`kb_test_device`，API 34/x86_64/4 vCPU/PAGE 4096）、5566（API 34/2 vCPU）、5570（API 23/x86_64）、5562（4 vCPU，事故弃用）、5620（`ps16k_x86_64`，API 36/PAGE 16384）。**本记录员未执行任何 adb**。

### 4.1 产物身份（索引 §2.1 + 各 E 文件头）

| 轨 | app sha256 | 测试包 sha256 | 设备 | 时点（本机） |
|---|---|---|---|---|
| WS2 A6 | `ec135844…e19bb5` | app.test `a2532e16…1e9687b` | 5584 | 2026-11-03 22:56–01:45 |
| WS3 正常 | `028eb07e…fea78f`（6 处 T4 打点） | `8a63b236…9d8518` | 5566（对照 5562） | 00:30–02:59 |
| WS3 控制 | `119812ff…3c23e4`（0 处打点） | 同 `8a63b236…` | 5566 | 同轨 |
| WS4 / WS11 | `1aee470a…9ed3cd` | app.test `cd35452e…5ed76d`；data.test `48213049…808154f`；database.test `671b3815…458fec`；export.test `08ea2641…e165c0` | 5584 | 03:04–03:52 / 06:50–07:14 |
| 设备轨（WS1–WS4） | `9b832f96…d7e29e16` | app.test `00bae341…105511a9c`；export.test `08ea2641…`；data.test `318a13ef…d9d3eea9`；database.test `cdeb8c7b…e09809d6fd` | 5584 / 5566 / 5570 | 08:44–11:30 |
| WS5 release | `a5fa0c4f…d3a4ef8`（strictOffline release） | —（release 无测试包） | 5620（16KB） | 04:01–04:46 |

`08ea2641…`（export.test）在 WS4 与设备轨同值——同一测试包、**不同 app APK**；跨轨数字不可合并。

### 4.2 各面结论（原文事实）

| 面 | 可写 | 不得外推 |
|---|---|---|
| KD-75 | 设备 `MistakePdfExporterAigcInstrumentedTest` **OK (7 tests)** × 5584/5570；`realExport…`、纯用户负向、旧键拒绝重建等 7 例；宿主 pypdf 6.16.2 独立读回 C2/C3（本轮重读一致） | DOCX、自然语言改卷（KD-85）、C5–C7 的独立读者一致、`PdfRenderer` 外部阅读器等价 |
| KD-76 | a6b 两批各 n=19/20、G1 RED、boot_id 20/20 唯一且 A∩B=∅、间隔 30.47 min；WS2 自述 attempt 1 身份门抓到签名对象哈希不稳定并修复 | A6 未满足（NOT EVALUABLE）；不得相加 n、不得合并 p95；item 13 BLOCKED；C4 无 ms |
| isolation | 15/15 **真实执行**（witness 78 行、`EXECUTED=15`、body 墙钟 69.4 s）；六 run 15 PASS；T4 控制身份 6/0 anchors 可核 | 低负载门 INVALID；**不写因果/T4 排除/两臂等价**；`smb.lowload.gate PASS` 是合成判据 |
| 设备复基线 | 5 包 sha 双向一致；258 声明/243 执行/241 绿/2 红/15 跳；matrix 16/16；backup 34/34；errorcause 3/3；pdfaigc 10/10；exportaigc 7/7；worker 无孤儿；强停 `COLD` + `quick_check=ok` | “全绿/通过”不成立；p09 未收敛；isolation 15 跳；arm64/16KB/真机/LMK 未覆盖 |
| p09 | WS4 三次跑 712/933 ms 红、2046 ms 绿；设备轨 3 次 2 台设备同断言失败 | 任何单次结论都不成立；引用必须带 APK + 次数 |
| AUD-08 | 门 FAIL/exit 1；① 0.5714；③ 0.0000；报告三批逐字节相同 | 当前设备库准确率、155 页总体、人工金标正确率 |
| 发版 | HEAD+3 文件可离线出包；16KB 静态 14/14 + x86_64 16KB 设备 COLD 1030 ms；mapping 892,986 行 | 发版就绪不成立；arm64/生产签名 BLOCKED；单次冷构建/确定性 UNVERIFIED |
| 安全/依赖 | Mimosa 完成但 `inconclusive`；license 346；密钥强特征 0；14 未跟踪锁 | 不得称“安全/无漏洞”；历史内容/二进制未扫；KD-72 维持 open |
| 性能 | 模拟器首基线（冷启 p50 3503 ms、单题导出 p50≈719 ms、最小宿主 PSS 42–55 MB）；DB 门 6/6、p95 50 ms | 真机/arm64 无；生产进程导出 PSS UNVERIFIED；不宣称“无回归” |

### 4.3 干扰与设备卫生（E 文件登记，供复跑参考）

- **5562 两次基础设施事故**：WS4 reboot 后内存 124 MB、`System has crashed`；设备轨 `service check activity: not found` → 该机任何结论为零。
- **5584 并发重装**：设备轨 09:07:54 另一进程重装 `core.database.test`，污染 matrix/dbcommit 两套件；5570 干净窗口复跑 `OK (16)` / `OK (12)`，归因并发重装，不归因代码。
- **5584 库态**：WS11 观测 229.65 MB 测试库 + 批量 `UPDATE knowledge_node` 占用连接池（30/60/90 s 告警），挡住生产进程导出内存测量；只登记，不判回归。后续轨用 5584 前先 `pm clear` 再播种。

## 5. 复核发现与处置

> 来源分三类并逐条标注：**（本轮实读/实跑）** = 本记录员亲手核对；**（转录）** = 引 E 文件/日志原文；**（修复环）** = 上一版记录 §5 的 24 条 findings。本批**没有** P15/P16 双复核产物，§5.1 是“记录员 + 各轨自报 + 研究交叉复核 + 修复环”的合并。

### 5.1 本轮发现

| # | 发现 | 证据 | 严重度 | 处置（含来源） |
|---|---|---|---|---|
| S1 | 计划原写 100/74，实列 78、实账 104 | 本轮解析 `count 78`；`plan:1,24,131`；订正段 `:133-134` | 中（口径） | 按 78 对账；登记为计划口径缺陷（第三批同族） |
| S2 | WS12 73–78 修复环前无载体 | 索引 §6.1 | 高（流程） | 上一版记录 + 本记录补齐 |
| S3 | 6C-2 文档层仍有 0/8 合法残留（行为正确） | `BackupRepository.kt:33,58`；`SecondaryScreens.kt:1195,1208-1210`（未提交） | 高（热区） | item 63 BLOCKED；runbook §6.1-6 |
| S4 | A6 样本口径冲突：10+10 合并 vs 2×20 各自判词 | `2026-11-01-kd76-stats.md:268`；`plan:39-43`；`a6-plan.txt` | 高（判据） | 未裁定；本批按 2×20 执行 |
| S5 | A6 三组实验全部 NOT EVALUABLE；失败轮不可合成失败率 | `ec135844…` 19/19；`9b832f96…` 10/20+7/20；批次 42 单批 n=20 | 高（口径） | 只逐轨陈述；不相加 n/p95 |
| S6 | `summarize_kd76` 曾被 `Log.i`+`println` 双写打穿 | `ws1-ws4-device-track.md:110`；修复环只折叠逐字相同；本轮重算无 duplicate | 高（工具） | 已修；内容不同的重复仍拒绝 |
| S7 | “三处同源升版”字面不成立：只升单题前缀 | `MistakePdfEligibility.kt:461`；批量 `:96` v3、卷面 `:450` v1；草稿 `FORMAT_VERSION=2` | 中（口径） | 关闭仍成立（传递入键）；台账措辞收窄 |
| S8 | HEAD `0fef9436` 不可编译 | `KnowledgeQuizTasks.kt:126`；`RoomStudyDatabase.kt` 6 处 override；HEAD+3 才 exit 0 | 高（发版） | 先提交 3 文件；不代做 git 写 |
| S9 | WS4 原 `PASS` 与 2 红/15 跳矛盾 | `ws4-device-rebaseline.md` 头表；修复环订正 | 中（口径） | 已订正；“复基线通过”不成立 |
| S10 | p09 两轨相反（flaky vs 确定性） | `ws4-device-rebaseline.md:193-209`；`ws1-ws4-device-track.md:232-243` | 高（未收敛） | item 54 部分；不单边裁定 |
| S11 | selrestore 红是测试装置缺陷，已在他处修复 | `BackupSelectionRestoreInstrumentedTest.kt:244-260`；设备轨 `OK (11)` | 中 | WS4 红早于修复；以设备轨为准 |
| S12 | `-4` 根因修复是**放宽**（分母由单核改整机） | `LowLoadProbe.kt:143-148`；2 核有效阈值 1.6×window | 高（口径） | 必须写“放宽”；外部负载仍由 shell 门判 |
| S13 | 本机低负载窗不可判 PASS（地板高于绝对上限） | `floor-5566.json` max=0.475；门 INVALID；`LA_MAX_ABS=0.30` 未改 | 高（环境） | 换机重标或裁定 nproc 伸缩；不改门 |
| S14 | witness/runner 源码晚于设备跑；runner 路径未演练 | `ws3-isolation-selfwitness.md` §0；`run_source=argument`；无 runner manifest | 中 | witness 只证“15 例执行” |
| S15 | KD-75 关闭范围过宽风险：DOCX/改卷未覆盖 | `MistakeDocxExporter.kt:59,77`；`MistakePdfPaperEdit.kt:233`；KD-85 | 高（覆盖假象） | 关闭写“PDF 链 + `MODEL_DOCUMENT_PARSE`” |
| S16 | KD-75 旧 PDF 数字不可复现；两份 0 B | `ws1-kd75-c2.md` §2/§3；本轮重读 2,514,765/2,492,555 B | 中（可追溯） | 只认 `*-5570.pdf` |
| S17 | identity 门抓到真缺陷（签名对象哈希不稳定） | `ws2-kd76-a6.md` §2.2 | 无（正面） | 建议保留为单测向量 |
| S18 | 6 份 APK、无最终树绑定；树持续漂移 | 索引 §2.1；本轮 609/3835 | 中 | 设备结论只绑 APK sha |
| S19 | AUD-08 仍 FAIL；金标 G2、导出无 `source` | 本轮门 exit 1；`ws8-aud08-audit-report.md` §5 | 高（数据质量） | 维持 FAIL；不改阈值 |
| S20 | Mimosa `inconclusive`、6 HIGH 只报位置 | `ws10-security-deps.md` §1.2/§1.3 | 中 | 不得称“安全” |
| S21 | 锁文件 G3 转不满足 + 14 未跟踪锁 | `ws10-security-deps.md` §3.2；两个解析输入脏 | 中 | KD-72 维持 open |
| S22 | 6C-2 不变量测试：上一版记 UNVERIFIED，盘上 XML 3/0/0 | `SixC2RulingInvariantTest.xml`（`04:59:49Z`） | 低（证据口径） | 记“产物级完成”；记录员未复跑 |
| S23 | “7 M 阻塞契约”是错口径；真正阻塞是热区纪律 | `ws6-contract-plan-closeout.md` §1.1；patch `--check` exit 0 | 中（口径） | patch 仍不应用 |
| S24 | 性能仅模拟器首基线；生产导出内存被 KB 卡住 | `ws11-performance.md` §0/§5.3 | 中 | 不宣称“无回归” |
| S25 | 发版不就绪：HEAD/签名/arm64/质量门 | WS5 §2.3/§5/§6；release-gates 00/09/19 红 | 高（发版） | §7.1 判“不成立” |

### 5.2 修复环 24 条 findings 的处置状态（上一版记录 §5，转录）

| # | 发现（摘要） | 级别 | 处置（本记录复核） |
|---|---|---|---|
| F1/F24 | 计划 74/100 vs 实列 78/104 | 中 | 已修：计划改 78/104（本地文件）；本记录按 78 对账 |
| F2 | WS12 无交付、记录不存在 | 高 | 已修：上一版记录 + 本记录 |
| F3/F18 | 6C-2 KDoc/UI 仍写 0/8；取消到 0/8 静默 | 高 | **BLOCKED**：热区/他线；行为未漂移（四方门 exit 0） |
| F4 | 每轮 2 条 `KD76_SAMPLE` 判重复 | 高 | 已修：只折叠逐字相同；本轮重算无 duplicate |
| F5 | A6 样本口径冲突 | 中 | 未裁定（§5 S4） |
| F6 | WS2 item 13 BLOCKED、16 部分 | 中 | 记为 BLOCKED/部分 |
| F7 | WS4 verdict 写 PASS 但 2 红/15 跳 | 中 | 已修：PARTIAL |
| F8 | p09 两轨相反 | 中 | 记未收敛 |
| F9 | HEAD 不可编译 | 高 | **BLOCKED**：分支已在工作树；修复需主会话提交 |
| F10 | 强门非单次干净通过 | 低 | §3.2 注明 |
| F11/F13/F25 | KD-75 可追溯性、C5–C7 非独立读者、0 B PDF | 中/低 | 已补 `ws1-kd75-c2.md`；pypdf 重读；0 B 标注 |
| F12/F14/F22/F23 | isolation 前门、witness/runner 时序、CPU 门放宽 | 中 | 文档订正；门/环境 BLOCKED |
| F15 | 6 份 APK、无树绑定 | 中 | §4 记录；无法消除 |
| F16 | `SixC2RulingInvariantTest` 执行无证据 | 低 | 上一版 UNVERIFIED；盘上 XML 3/0/0 |
| F17 | 自然语言改卷未打 `/AIGC` | 高 | **BLOCKED（KD-85）**；关闭声明已收窄 |
| F19 | WS9-63 三方一致断言未交付 | 中 | BLOCKED（同 F3） |
| F20 | 索引把 S10/S11/S12 写为“未见落地” | 中 | 已改为实证状态 |
| F21 | KD-75 台账 42,900 B 不可复现 | 低 | 已改为 2,514,765/2,492,555 B |

### 5.3 复发门（“若再出现如何被门抓住”）

| 失败形态 | 抓住它的门（现存） | 强度 |
|---|---|---|
| 两批/两组样本被相加成 n=40、p95 合并 | `kd76_a6_verify.py` I0–I10 合取，JSON 无 `merged_p95_millis`；单测 12 例 | 强（结构拒绝） |
| 每轮多条 SAMPLE/空 sha 被静默剔除 | `summarize_kd76` 只折叠逐字相同；缺 sha/缺 boot/重复 boot → INVALID；n<20 → NOT EVALUABLE | 强（单测 44 例） |
| `-4` 被读成“用例通过” | witness 三层 + canary + `--require-executed`；`OK (15)` 与 15×`-4` 并列时 checker 判 NOT_SCHEDULED | 强（本轮 checker exit 0 且 EXECUTED=15 才判执行） |
| 低负载门“合成 PASS” | shell 门 `lowload_gate.py gate` 与 instrumentation `-e smb.lowload.gate` 分离；报告要求 `window=INVALID` 时不升格配对结论 | 中（需人工不伪造参数） |
| 6C-2 行为漂移（0/8 被放宽） | `SixC2RulingInvariantTest` 3 例 + `allowedMasks` 精确相等 + 四方门 | 强（双层断言；红相未演示） |
| 导出打标判据漂移 / 旧缓存复用 | `policy generation is consistent across sentinel fingerprint and policy`；`GENERATION` 派生 sentinel 与前缀；旧键拒绝重建设备用例 | 强（JVM + 设备） |
| 契约 patch 落到错误分支版 | `git apply --check` + merge-tree 干跑 + 冲突清单 | 中（只读检查，非自动门） |

## 6. 遗留、阻塞与 UNVERIFIED

### 6.1 阻塞（外部或需主会话裁定）

1. **HEAD 不可编译（发版硬阻塞）**：`KnowledgeQuizTasks.kt` `when` + `RoomStudyDatabase.kt` override。解除 = 提交 3 文件后重跑 G-REL-00/01/02。
2. **生产签名（BLOCKED）**：`RELEASE_*` 4/4 UNSET；runbook `ws5-release-prefight.md` §6。
3. **arm64 16KB（BLOCKED）**：x86_64 宿主无法起 arm64 AVD；§5 等待清单。
4. **真实 Provider（BLOCKED）**：需凭据（环境变量/密钥服务，不落源码）。
5. **人工金标（BLOCKED）**：≥2 标注员 + 1 裁定者；runbook `ws8-aud08-human-gold-runbook.md`。
6. **产品进程首请求（BLOCKED，结构性）**：`SmartMistakeBookApplication.kt:364-368`；runbook `ws2-kd76-a6.md` §7。
7. **6C-2 item 63（BLOCKED）**：清 `BackupRepository.kt:33,58` + 回滚 `SecondaryScreens.kt` 0/8 文案；行为不动；runbook 见 `6c2-rulings.md` 末节。
8. **isolation 低负载窗（BLOCKED）**：地板 `la_ratio_max=0.475` > `LA_MAX_ABS=0.30`；换机重标或裁定 nproc 伸缩（改门属行为变更）。
9. **KD-72 写锁（BLOCKED）**：G1/G2/G3 未满足；G4–G8 未取证。
10. **A6 样本口径（需裁定）**：裁定前不得再开 A6 批次。

### 6.2 非本批红 / 共享面

- `p09` 两轨相反，未收敛；不得写“已修/必红”。
- WS11 的 KB 批量更新停顿是环境态，未对照，不判回归。
- `core/domain/TutorJudgedReview.kt`、`core/ui/Theme.kt`、`docs/current` 7 M、`core/data/build.gradle.kts`、`gradle/libs.versions.toml` 等：他线/共享，提交时排除。
- `KD-28/29/82` 标题 `open` 而 Disposition 是“接受边界/不排期”：台账 hygiene，需主会话裁定用词。

### 6.3 UNVERIFIED（不得写通过）

- **12 模块单测门的逐例结果**：只有 Gradle 任务级 `BUILD SUCCESSFUL`；测试 XML 已被覆盖。
- **修复环后的全量强门**：无 `batch43-strong-postfix.log`。
- **KD-75 item 5 的“本轮逐例运行”**：只到盘上 XML（11/0/0），记录员未跑 Gradle。
- **6C-2 不变量测试的红相**：未演示（改热区行为才能变红）；XML 3/0/0 为盘上产物。
- **KD-76 A6 / C4 / 29.5 s 四段**：A6 NOT EVALUABLE；C3 仅 witness；C4 无 ms；Σ max UNVERIFIED。
- **isolation 因果、T4 排除、两臂等价**：第 1 层未成立；禁止外推。
- **p09 定性**：两轨相反。
- **KD-75 自然语言改卷、DOCX、C5–C7 的独立读者一致**：KD-85 / 既有缺口 / 项目 reader。
- **AUD-08 当前设备库准确率、人工金标正确率**：无新 split 导出、无标注员。
- **发版：单次冷构建、构建确定性、retrace、AAB/bundletool、三条 native 真装载、生产签名**。
- **安全：Mimosa 完整结论、12 个无 license POM、git 历史内容密钥、release 包秘密扫描（G-REL-29）**。
- **性能：真机/arm64、生产进程内导出 PSS、warm 起、Macrobenchmark**。
- **WS6/WS7/WS9/WS12 的独立 E 文件**：不存在；WS12 在本记录。

### 6.4 下一批建议（WS12 item 77）

1. **先收口再开新批**：提交 HEAD 缺的 3 个文件（`KnowledgeQuizTasks.kt`、`StudyDatabasePort.kt`、`TutorVisualPort.kt`），让 `git archive HEAD` 可编译；否则任何发版/设备结论都无法绑定干净树。
2. **裁定 A6 样本口径**：在 10+10 合并与 2×20 各自判词之间二选一并冻结预注册；再决定是否重跑。
3. **6C-2 item 63**：取得授权后清 KDoc + 回滚 UI 文案 + 加三方一致断言；行为不动。
4. **KD-85**：主会话裁定“模型改卷文本是否计入 AIGC”；若计入，最小改动 = `MistakePdfPaperEdit` 显式来源位 + applier 置位（跨热区，需授权）。
5. **p09**：把 1300 ms 窗口改为显式锁定 in-flight（用例落点），先红后绿；不要在 WS4/设备轨之间二选一。
6. **isolation**：换机重标 `la_ratio_max` 地板，或裁定 nproc 伸缩口径；runner 路径在最终源上演练一次并落 manifest。
7. **发版**：提交 3 文件 → 生产签名 runbook → arm64 真机 16KB → sidecar 生成；再重跑 G-REL-00/01/02。

## 7. 提交清单建议（每块 ≤6 路径；排除热区/锁文件/build）

> **本记录员未做任何 git 写**；以下只是建议块，提交前必须逐路径与逐 hunk 核对。**不得提交**：14 个 `gradle.lockfile`、`build/*`（含 `kd76-runs/`、`ws3/ws4/ws8/ws10/ws11/wsdevice-batch43/`）、`.mimosa/`、仓外 `D:\smb-ws5-43`、热区文件（`core/domain/BackupRepository.kt`、`core/ui/Theme.kt`、`docs/current/*`、`SecondaryScreens.kt`）。

| 块 | 路径（≤6） | 备注 |
|---|---|---|
| B1 | `tools/ci/summarize_kd76.py`、`tools/tests/test_summarize_kd76.py`、`tools/ci/kd76_a6_verify.py`、`tools/tests/test_kd76_a6_verify.py`、`tools/ci/kd76_identity.py`、`tools/tests/test_kd76_identity.py` | WS2 工具 + F4 修复；`summarize` 的折叠是行为变更 |
| B2 | `tools/ci/a6_plan.py`、`tools/ci/run_kd76_a6.sh`、`tools/ci/run_kd76_rounds.sh` | WS2 预注册/两批驱动 |
| B3 | `tools/ci/isolation_witness_check.py`、`tools/ci/isolation_witness_expectations.json`、`tools/tests/test_isolation_witness_check.py`、`tools/ci/run_isolation_pair.sh` | WS3 witness 检查器 + runner 注入 |
| B4 | `app/src/androidTest/.../visualjs/IsolationWitness.kt`、`IsolationArms.kt`、`LowLoadProbe.kt`、`VisualJsIsolationInstrumentedTest.kt` | WS3 自证/门/15 例包裹；`LowLoadProbe` 含“放宽”语义变更 |
| B5 | `core/export/src/main/.../MistakePdfEligibility.kt`、`MistakePdfBatchEligibility.kt`、`MistakePdfExporter.kt`、`MistakePdfPaperEdit.kt`、`MistakePaperDraftStore.kt` | WS1 KD-75 最小 hunk（5 文件，+54/−7 主码） |
| B6 | `core/export/src/test/.../MistakePdfExporterAigcJvmTest.kt`、`MistakePdfEligibilityTest.kt`、`MistakePdfPaperEditTest.kt` | WS1 JVM 用例（其余 export 测试为他线，逐 hunk） |
| B7 | `core/data/src/test/.../backup/SixC2RulingInvariantTest.kt`、`core/data/src/androidTest/.../backup/BackupSelectionRestoreInstrumentedTest.kt` | WS9 不变量 + S12 装置修复；后者的其他 hunk 需核 |
| B8 | `tools/ci/check_backup_plan_sync.py`、`tools/tests/test_backup_plan_sync.py` | 四方门消费 v4 golden（S11）；机器块重生成 |
| B9 | `docs/research/2026-11-05-kd75-model-text.md`、`2026-11-05-kd76-a6-protocol.md`、`2026-11-05-isolation-selfwitness.md`、`2026-11-05-clean-tree-release.md`、`2026-11-05-contract-merge-protocol.md`、`2026-11-05-plan-closure.md` | R1–R6 |
| B10 | `docs/research/2026-11-05-batch-43-baseline.md`、`2026-11-05-release-gates.md`、`2026-11-05-ws6-contract-plan-closeout.md`、`2026-11-05-contract-p0-conflict-list.json` | 基线/发版门/WS6 收口/冲突清单 |
| B11 | `docs/research/evidence/2026-11-05-batch-43/00-index.md`、`ws1-kd75-c2.md`、`ws1-ws4-device-track.md`、`ws2-kd76-a6.md`、`ws3-isolation-selfwitness.md`、`ws4-device-rebaseline.md` | 证据 1 |
| B12 | `docs/research/evidence/2026-11-05-batch-43/ws5-release-prefight.md`、`ws8-aud08-audit-report.md`、`ws8-aud08-human-gold-runbook.md`、`ws8-aud08-prohibition-rescan.md`、`step-aud08-gate-recompute-b43.md`、`rulings/20261103T213902Z-c0dc8d30.json` | 证据 2（6 路径） |
| B13 | `docs/research/evidence/2026-11-05-batch-43/ws10-security-deps.md`、`ws11-performance.md`、`research-crosscheck.md`、`docs/research/2026-11-05-batch-43-record.md` | 证据 3 + 本记录 |
| B14 | `docs/known-defects.md`、`docs/REFACTOR-MASTER-LINE.md`、`docs/agent-first-refactor-decisions-2026-09-23.md`、`docs/research/2026-11-04-6c2-rulings.md` | 台账/设计件；全部共享 M，逐 hunk |
| — | **热区，另裁**：`core/domain/.../BackupRepository.kt`、`core/ui/.../Theme.kt`、`docs/current/*`、`app/.../SecondaryScreens.kt` | 计划 §4 明禁；待主会话授权/回滚，**不进本批提交** |
| — | 明确不入：14 锁文件、`core/data/build.gradle.kts`、`gradle/libs.versions.toml`、`nul`、其他线的 KB/dense 测试与主码（`ReviewReminderCoordinator*`、`SavedMistakeSheetBytesTest`、`VisualContentMeasuringTest`、`TutorVisualContextFingerprintTest`、`TutorFigureProposalExecutionTest` 等） | 逐路径排除 |

### 7.1 发版就绪判定（WS12 item 78）

**不成立。** 四项硬阻塞：

1. **HEAD 不可编译**（干净树出不了包；需先提交 3 个文件）。
2. **工作树脏且跨批不一致**（6 份 APK、无最终树绑定；“当前源码已验证”不成立）。
3. **生产签名缺失**（`RELEASE_*` 4/4 UNSET）、**arm64 未验证**。
4. **质量门未全绿**：p09 未收敛、isolation 未定案、AUD-08 FAIL 维持、强门非单次干净通过、单测门无逐例。

## 8. 回链

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md`（§1 六题、§2 1–78、§3 流程 20、§4 硬约束、§5 验收；§0 订正段 `:133-134`）。
- 基线/盘点：`docs/research/2026-11-05-batch-43-baseline.md`（F1–F7、KD/热区/门现状）、`docs/research/2026-11-05-plan-closure.md`（74 行盘点、§9.5 残余 26 项）。
- 证据索引：`docs/research/evidence/2026-11-05-batch-43/00-index.md`（§0 映射、§2.1 六 APK、§4 位绑定、§6 缺口、§8 N1–N12、§9 脱敏）。
- E 文件（14 `.md` + 1 JSON）：`ws1-kd75-c2`、`ws1-ws4-device-track`、`ws2-kd76-a6`、`ws3-isolation-selfwitness`、`ws4-device-rebaseline`、`ws5-release-prefight`、`ws8-aud08-{audit-report,human-gold-runbook,prohibition-rescan}`、`step-aud08-gate-recompute-b43`、`ws10-security-deps`、`ws11-performance`、`research-crosscheck`、`00-index`、`rulings/20261103T213902Z-c0dc8d30.json`。
- 研究件：R1–R6（`2026-11-05-{kd75-model-text,kd76-a6-protocol,isolation-selfwitness,clean-tree-release,contract-merge-protocol,plan-closure}.md`）+ `2026-11-05-release-gates.md` + `2026-11-05-ws6-contract-plan-closeout.md`。
- 门禁原始日志（gitignored，不作关闭证据）：`build/batch43-unit.log`、`build/batch43-strong.log`；原始产物 `build/kd76-runs/a6b/`、`build/ws3-batch43/`、`build/ws4-batch43/`、`build/ws8-batch43/`、`build/ws10/`、`build/ws11-batch43/`、`build/wsdevice-batch43/`；仓外 `D:\smb-ws5-43`（一次性副本/钥匙/日志，可整体删除）。
- 台账：`docs/known-defects.md`（KD-75 `:2879/:2910`、KD-76 `:2916/:3010/:3012`、KD-83 `:3292`、KD-84 `:3306`、KD-85 `:3318`）；`docs/REFACTOR-MASTER-LINE.md:7-8`；`docs/agent-first-refactor-decisions-2026-09-23.md:3828`。
- 上一批：`docs/research/2026-11-04-batch-42-record.md` §9.2/§9.3/§9.5；`docs/research/evidence/2026-11-04-batch-42/00-index.md`（APK/夹具对照口径）。
- 本文件不替代主会话裁定：**KD-75 closed（PDF 链 + `MODEL_DOCUMENT_PARSE` 口径；C8→KD-84、改卷→KD-85）**；**KD-76 open（A6 NOT EVALUABLE、item 13 BLOCKED、C4 无 ms）**；**isolation UNCONVERGED / 不写因果 / 15 例真执行但窗 INVALID**；**AUD-08 FAIL 维持（0.5714 < 0.9）**；**6C-2 行为未漂移、item 63 BLOCKED**；**p09 未收敛**；**发版就绪不成立**（HEAD 不可编译 + 生产签名 + arm64 + 质量门）。

## 9. 主会话收口（2026-11-04，本段为收口权威）

### 9.1 主会话实跑门禁（本轮，非转录）

| 门 | 命令 | 结果 |
|---|---|---|
| 单测门 | 12 模块 `testDebugUnitTest --continue`（`build/main-session-gate-batch43-unit.log`） | **exit=1**；8 例红全部在 KB/dense 线（`DenseAssetProvenanceTest` 2、`DenseLayoutIdentityTest` 4、`DenseRecallRerankerTest` 1、`ProductionLexicalLegExportTest` 1），他线正在替换 dense 资产；本批产品面 0 新红 |
| 强门 | 四模块 + 两 flavor app androidTest 编译逐任务 `--rerun` + `:app:assembleLocalFirstDebug`（`build/main-session-gate-batch43-strong.log`） | **exit=0（真实重编）** |
| 备份四方门 | `python -I tools/ci/check_backup_plan_sync.py` | PASS（批次 42 收口值；批次 43 已加 v4 golden 消费） |

### 9.2 热区残留回滚（S3 收口）

批次 42 回滚时遗漏的 6C-2 残留仍在工作树：`core/domain/BackupRepository.kt`（KDoc 说 0/8 可创建）、`app/.../SecondaryScreens.kt`（0/8 解锁 UI/文案）、`core/ui/Theme.kt`（PR#12 兼容别名，已无引用）。三者均**反向应用 diff 还原到 HEAD**，工作树 `git diff --numstat` 为 0。行为面仍为 `ALLOWED_MASKS={9,13,15}`；`SixC2RulingInvariantTest` 已随本批提交钉住该裁定。

### 9.3 S8 更正（HEAD 不可编译不成立）

记录 S8 称 HEAD `0fef9436` 不可编译、需先提交 3 文件；本轮在 HEAD 树上实跑 `:core:data:compileDebugKotlin`（单测门前置）与强门均成功，`KnowledgeQuizTasks.kt` 为 `core/model` 下已跟踪文件、无未提交 diff。**S8 为时点误判**，不按“需先提交 3 文件”处理。

### 9.4 S4 裁定（KD-76 A6 样本口径）

- **以预注册 `docs/research/2026-11-01-kd76-stats.md:268` 为准**：两批各 10 轮、合并 n=20；**不得**把批次 43 的 2×20 当作 A6 通过，也不得把 19+10+7 等失败轮次相加成 n。
- 失败轮按删失计入 n 上限，`NOT EVALUABLE` 输出保持；KD-76 维持 open。
- 计划 item 11/12/14/15 的“2×20 各自判词”作废，后续按 10+10 执行。

### 9.5 S12/S13 裁定（低负载门）

- `LowLoadProbe` 把分母从单核改为整机负载是**放宽**，必须在该工具 KDoc 与证据里写明；它不能单独认证“低负载窗”，外部仍以 shell 侧 loadavg 门为准。
- 本机地板 0.475 > 绝对上限 0.30 → 本机不能判 PASS；**不改门**，换机或另行裁定 nproc 伸缩前，isolation 只能记“15 例真实执行 + witness 齐全”，不得写因果/T4 排除。

### 9.6 本批提交（15 笔，已推送前核对暂存区）

KD-76 A6 工具/身份/汇总 → A6 驱动 + witness 检查器 → isolation witness 代码 → KD-75 C2 导出三处升版 → KD-75/6C-2 用例 → 四方门 v4 golden + 研究 R1–R4 → R5/R6/基线/发版门/WS6 → 证据 1/2/3 + 记录 → 本文。热区文件不入提交。

### 9.7 收口裁定

- **KD-75 关闭（范围收窄）**：仅覆盖 PDF 链 + `MODEL_DOCUMENT_PARSE` provenance 与 AI 图；DOCX 无 `/AIGC` 机制、自然语言改卷文本另立 KD-85，不得写成“模型生成内容全覆盖”。
- **KD-76 维持 open**（A6 未达、C4 未测）。
- **isolation 不定案**：15 例真实执行但窗 INVALID，禁写通过/T4 排除。
- **p09 未收敛**（flaky vs 确定性两轨相反），不单边裁定。
- **发版不成立**：版本门/签名/arm64/质量门四项硬阻塞。
- **AUD-08 FAIL 维持**（0.5714）。
