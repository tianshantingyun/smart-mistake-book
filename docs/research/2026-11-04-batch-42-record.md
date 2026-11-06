<!-- 体积自查：504 行 / ≤65,536 B（行数超、字节合规；沿用批次 40/41“记录不设 R4 上限”）。批次 42 记录员落盘。 -->

# 批次 42 记录（2026-11-03/04）：计划收口批——高发现修复 + 产品接线 + 发版前置

> **来源与边界**：本文件由 **批次 42 记录员**（工作流收口阶段）落盘，是本次 ask 的**唯一写入**。
>
> **本轮实跑/实读**（可重跑）：`python -m pytest tools/tests`（全量 **1041 passed / 70709 subtests in 325.91 s**，exit 0）、三条工具测试文件（44 passed）、四条（57 passed）、`python -I tools/ci/check_backup_plan_sync.py`（exit 0）、`python -I tools/split_golden/check_split_gate.py`（**exit 1**）、`sha256sum` 两份 v4 夹具、`python -I` 解析计划编号（90 项）、`git rev-parse/status/ls-files/show/diff`、各门禁日志与测试结果 XML 的逐行读取、以及 WS1 五条修法与 WS2/WS6/WS7 落点的源码实读。
>
> **本轮未跑**：任何 Gradle（含 12 模块单测门重跑）、任何 adb/设备、任何 git 写、任何 Provider 调用。§4 的设备事实**全部来自 E 文件**，非本记录员执行。
>
> **声明口径**：凡写“完成/已落”只表示**载体在树 + 有可指认证据**；状态严格三值：完成 / 部分 / 未执行（UNVERIFIED）。任何“门通过/设备绿”都标明是谁跑的、绑的哪份 APK、哪份日志。

- 计划：`.zcode/plans/plan-2026-11-04-batch-42.md`
- 基线：`docs/research/2026-11-04-batch-42-baseline.md`；盘点：`docs/research/2026-11-04-batch-42-inventory.md`
- 证据索引：`docs/research/evidence/2026-11-04-batch-42/00-index.md`（11 个 `.md` + 1 个 `rulings/*.json`）
- 输入：批次 41 记录 `docs/research/2026-11-03-batch-41-record.md` §9.3（F7/F8/F10/F11/F12）

## 0. 口径与基线

### 0.1 项数与编号口径（**与 ask/索引自述的 100 项=6+74+20 不符**）

计划自身写 **116 = 研究 6 + 实施 90 + 流程 20**（`plan:1,4,142`，与实列一致）；但本 ask 与 `00-index.md:4` 写“100 = 6 + 74 + 20”，`inventory.md:24` 更把该口径误记为“计划首行原文”。本记录员按行首 `^\d+\. ` 独立解析计划（实跑）：

```text
count 90  first [(1,26),(2,27),(3,28)]  last [(88,135),(89,136),(90,137)]  missing []  dup []
```

即 **§2 实列 90 个连续编号（1..90，无缺号无重号）**，不是 74。按 WS 分段（与 `inventory.md:24-28` 一致）：WS1 1–12、WS2 13–24、WS3 25–33、WS4 34–41、WS5 42–47、WS6 48–55、WS7 56–60、WS8 61–70、WS9 71–75、WS10 76–80、WS11 81–84、WS12 85–90。

因此本记录的实际账为 **研究 6 + 实施 90 + 流程 20 = 116 行**，逐编号对账（不把 90 压成 74，也不改计划文件）；“74/100”登记为**计划口径缺陷**（§5 S1），与批次 41 记录 §5 F1（自述 55、实列 62）同族。`inventory.md:23-37` 已先行登记该缺陷并给出同样的映射。

### 0.2 基线事实（本轮实读/实跑）

| 项 | 值 | 证据 |
|---|---|---|
| HEAD（记录时） | `a5a61a465b0b63a3cea08a3167623ff41243b1eb` | 本轮 `git rev-parse HEAD` |
| HEAD（计划/E 文件绑定时） | `faab17f5…` | 计划 §0；各 E 文件头；`build/batch42-strong.log:2` |
| HEAD 位移原因 | 批次 33（KB 线）提交 `a5a61a46`：契约门夹具 + telemetry 10 件 + 按 v28 重钉三处词面 | `git show --stat a5a61a46`（本轮实读） |
| 工作树 | **581 条**（14 D / 119 M / 448 ??）；**暂存 0** | 本轮 `git status --porcelain \| cut -c1-2 \| sort \| uniq -c`；`git diff --cached --name-only \| wc -l` = 0 |
| DB schema | 73（92 表 / 2 view / hash `1d086050…`）；本批未改 | `StudyDatabase.kt:142`；计划 §4；`check_backup_plan_sync.py` 输出 |
| 请求 schema | 19 | 基线 §1.4（转录） |
| 备份选择 mask | **{0, 8, 9, 13, 15}**（本批 WS6 重裁） | `inventory.md:48`；`6c2-rulings.md:52` |
| 热区（代码） | `core/domain` **4 M**、`core/export` **1 M 主码 + 3 M 测试 + 2 ?? 测试**、`core/ui` **1 M** | 本轮 `git status --porcelain -- core/domain core/ui core/export` |
| 热区（文档/配置） | `docs/current` **7 M**；`settings.gradle.kts` 0；Application/Root 0 | 本轮同命令 |
| 计划 §0 的“热区仅 5 M” | **已被 9 M（代码）取代** | 计划 §0 vs 本轮实测；`inventory.md:51-67` |
| 本批 research/evidence 载体 | `docs/research/2026-11-04-*.md` **13 份**（R1–R6 + 基线 + 盘点 + WS8 + 准入抽样 + 契约 patch + release-gates 草案 + 6C-2 裁定）；证据目录 **12 件** | 本轮 `ls`/`find`；`00-index.md:37` |
| 本批提交状态 | 全部未提交（research/evidence/model-pdf/夹具/新测试均 `??`；工具与 WS1/WS2/WS6/WS7 落点为 `M`/`??`） | 本轮 `git ls-files docs/research/2026-11-04*` 为空 |

### 0.3 源漂移与“设备绿”的可复现边界

1. 设备轨（E-42-DEVICE-01）源快照 = `d89cd1a4…`（15:18 时点，649 条脏路径）；`00-index` 整理时 651 条 / `3d93df8e…`；本记录员 581 条。**三个时点互不可推**。
2. 同批设备用了 **三份不同 app APK**（`35e10339…` WS3/WS10、`fe9452aa…` WS4、`ec135844…` DEVICE）——`00-index.md:42-53` 已登记为 N2。**任何“批次 42 设备全绿”必须指名 APK。**
3. 修复环（18:29–19:05）改了三处工具/测试（见 §5 S3），**晚于全部设备跑**。因此设备结论只绑 §4 的 E 文件与 APK sha，不绑当前源码。
4. WS10 的 app.test 是 01:00 构建（`E-42-WS10-01 §0` 自述），**不含**本批对 `PdfAigcDeviceRoundTrip` / `VisualJsConnectStageCalibration` / `BackupSelectionUi` 的工作树改动 → “当前源码已验证”不成立。

## 1. 116 项逐项对账

> 状态词：**完成** = 载体在树 + 有可指认证据（本记录员实读/实跑，或 E 文件/日志可指认）；**部分** = 主链在树但有明确缺口；**未执行** = 无载体或无执行证据。

### 1.1 研究线 6 行（R1–R6）

| # | 研究题 / 落盘 | 关键裁定（读该件头部/摘要所得） | 状态 | 未验证 |
|---|---|---|---|---|
| R1 | PDF 注入位置与缓存代际 → `2026-11-04-pdf-export-injection.md` | 注入唯一正确点在 `prepare` 的“render → 大小判限 → `validatedPageCount` 之前”（`MistakePdfExporter.kt:133/134` 之间）；在 `writeDigest` 后注入会因摘要不一致在 `findExisting` 抛 `EXISTING_FILE_INTEGRITY_CONFLICT` 并持续 24h | 完成 | 本记录员只读头部与 §0；两条外部来源未独立复核 |
| R2 | isolation 因果实验设计 → `2026-11-04-isolation-causality.md` | 两臂身份必须在**已安装 APK** 级可区分；T4 控制轮要字节可回退 + marker 绑安装包；缺方法判 INVALID；因果只能写到**机制级** | 完成 | 同上；OFF 臂真跑未执行（该件自述） |
| R3 | 20 轮冷启动执行形状 → `2026-11-04-kd76-20-rounds.md` | boot 身份选宿主 `/proc/.../boot_id`（app 侧 `BOOT_COUNT` 只作交叉核对）；逐轮 INVALID 计入 n；单批与两批必须显式二选一 | 完成 | 同上 |
| R4 | 历史 v4 包夹具构造 → `2026-11-04-v4-pack-fixture.md` | 真 v72 载荷 + `COUNT(*)` 实测行数 + v4 digest + 冻结字节；**现有“v4 夹具”是假载荷 + manifest 重写**（推翻批次 41 的 F14 前提） | 完成 | 同上 |
| R5 | 锁文件写入窗口 → `2026-11-04-lockfile-window.md` | `--write-locks` 是 merge 不是 rewrite、只写本次解析过的配置、构建失败不写盘；隔离副本只能作**预演**，不关闭 KD-72 | 完成 | 同上 |
| R6 | 发版前置机器可核清单 → `2026-11-04-release-readiness.md` | A/B/C/D 四类标记；清单必须“命令/期望 exit/产物/通过条件/绑定”；`docs/current/release-gates.md` 仍是散文且热区 | 完成 | 同上 |

补充：R1–R6 另有交叉复核件 `…/research-crosscheck.md`（7 冲突，C1/C2/C3/C5/C6 高；含 F7/F10 假绿探针复现），是本批最硬的证据之一。

### 1.2 实施 90 行

#### WS1 高发现修复（1–12）

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 1 | F7：OFF 臂缺失方法判 UNKNOWN/负 + 先红用例 | `tools/ci/isolation_pair_report.py:293-321`（缺失物化为 `UNKNOWN` + `missing_methods` + `method_missing`；无清单 `expected_methods_missing` fail-closed）；`tools/tests/test_isolation_pair_report.py` 19 例 | **完成（工具层）** | **“先红”无落盘红相记录**；运行时未触发（ws4 §6：两臂 `missing_methods` 为空） |
| 2 | F7 回归夹具（OFF 缺 3 方法断言非 IDENTICAL） | 同上测试文件（本轮实跑 19 passed） | 完成 | 合成输入，不证设备行为 |
| 3 | F8：两臂不同构建身份，或显式拒绝同 COMPONENT | `tools/ci/run_isolation_pair.sh`（`--off-component/--on-component`；同 COMPONENT → die exit 2，`ws4 §2.2` 实测原文） | 完成 | 与计划项 34“同 APK sha”互斥（§5 S4） |
| 4 | F8：T4 标记比对**已安装 APK** sha/类清单 | `tools/ci/t4_control_marker.py:192-302`（`installed_apk_identity` via `adb pm path` + 拉包；`installed_apk_differs_from_artifact`/`installed_apk_unavailable`）；`ws4 §4.1` emit/verify exit 0、`anchor_count=6`、`identity_source=installed_apk` | 完成 | OFF 产物不存在 → 控制轮仍 BLOCKED |
| 5 | F10：空 per-round sha 不整剔 + boot 门 | `tools/ci/summarize_kd76.py:71-76,406-480,508-535`（缺 sha/缺 boot/重复 boot/`mode!=reboot` → INVALID/NOT EVALUABLE；`mixed APK sha256`/`no apkSha256` 进 reasons）；测试 18 例 | 完成 | 阈值/口径未变 |
| 6 | F11：`Kd76ReportVerdict` 移除外部覆盖 | `Kd76ReportVerdict.kt:80-84` `decide(sampleCount, g1Red, p95Millis)`；`GATE_MILLIS = READY_TIMEOUT/2`（`:31`）；KDoc 明写“调用方无法通过签名放宽”（`:72-74`） | 完成 | 我实读源码；用例 XML 8/0 |
| 7 | F11：物理阻断旧单进程 20 轮入口 | `VisualJsConnectStageCalibrationInstrumentedTest.kt:150-160`：`@Ignore("legacy single-process 20-round entry (F11)…")` + 函数体首句硬失败 | 完成 | 本批未跑该设备类 |
| 8 | F12：`rebalance=null` 不算 0，改 `NOT_PLANNED` | `ReviewCapacityPreviewReader.kt:138-141,166,187,221-224`（显式枚举态 + `require` 互斥） | 完成 | 我实读；用例 6/0 |
| 9 | F12：`totalGranted` 计入 due-review 通道 | `ReviewCapacityPreviewReader.kt:149` `plan.totalPickedCount`；`ReviewCapacityScheduler.kt:174,192,216`（两通道去重总量）；init `require(totalGranted == Σsubjects.granted + rebalanceGranted)`（`:215`） | 完成 | 同上 |
| 10 | 修复后定向单测（core/data、app、tools） | `batch42-unit-postfix.log`：db 32 s、data-preview 9 s、export 2 s、app-kd76 9 s、model-pdf 重试 3 s、export `--rerun` 5 s，均 exit 0；XML：export 166/0、database 248/0、app 8/0、data 6/0；`batch42-pytest-postfix.log` 57 passed | **部分** | **core:data 只跑 `*ReviewCapacityPreviewReaderTest`**（6/0），全模块未跑 → KB 3 红环后状态 UNVERIFIED |
| 11 | 记录 §9.3 五条高/中关闭证据回写 | 本文件 §5（S3/S4 及 §5.3 复发门表） | 完成（本记录） | 运行时关闭仅 F7/F10/F11/F12 的代码面；F8 控制轮仍缺 OFF 臂 |
| 12 | 复盘：F7/F8/F10/F11/F12 各一条“若再出现如何被门抓住” | 本文件 **§5.3**（本记录员补写，前无载体：仓库内 `grep 若再出现` 零命中） | 完成（本记录） | — |

#### WS2 KD-75 产品接线（13–24）

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 13 | `MistakePdfExporter` 注入点（digest 前注入 `/AIGC`） | `MistakePdfExporter.kt:140-141`（`labelPdfInPlace` → `validatedPageCount`）、`:233-248`（注入 + fail-closed）、`:210`（`paintsAiGeneratedImage`）；diff **+89 行，全部本 hunk** | 完成 | 生产 `core/export` 零他线 hunk（我实读 `git diff --stat`） |
| 14 | 缓存键/指纹升版 | **未按字面升 `mistake-export-v3`→v4**；改策略代际哨兵 `.aigc-policy-gen-1` + `ensurePolicyGeneration`（`:123,163,199,309`） | **部分** | 与计划/判据 C4 字面不一致，待主会话确认（`ws2 §2 C4`） |
| 15 | 纯用户内容不注入（负向） | JVM `MistakePdfExporterAigcJvmTest`（XML 9/0）；设备 `pure-user-unlabeled.pdf` 40,406 B，pypdf 读回**无 `/AIGC`**（`E-42-DEVICE-01 §1`） | 完成 | 设备绑定 app `ec135844…` |
| 16 | 注入失败 fail-closed（不产无标记文件） | `MistakePdfExporter.kt:178,186,248` → `GENERATED_PDF_INVALID`；JVM 3 例（正/负/fail-closed） | 完成 | 同上 |
| 17 | JVM 导出→注入→`PdfRenderer` 读回 | 同 13/16；`core:export:testDebugUnitTest` 全模块 166/0（19:03） | 完成 | 我读 XML，未读用例体 |
| 18 | 设备真实导出链（含 AI 图）→ pypdf 读 `/AIGC` | `MistakePdfExporterAigcInstrumentedTest` **OK (5 tests)**；`real-export-labeled.pdf` 53,271 B、7 键、`Label=1`、`ProduceID` == 用例 `inputSha256`（`E-42-DEVICE-01 §1`） | 完成 | 独立读者 pypdf，非项目 reader |
| 19 | 设备纯用户导出 → 读不到 `/AIGC` | 同 §1 第二行（`pure-user-unlabeled.pdf` info 仅 `/Producer`） | 完成 | 同上 |
| 20 | 设备显式图注逐位不变 | 用例断言“注入后字节前缀 == 渲染器原始字节”+ `PdfRenderer` 页数不变；5/5 内 | 完成 | 同上 |
| 21 | 打印部分页 `writeSubset` 丢标的处置验证 | 只**登记现状**：整份 `copyWhole` 保留、部分页丢文档级 `/AIGC`；重注入落 `MistakePdfDelivery.kt`（本批禁改） | **部分** | 未处置（判据 C8 未满足） |
| 22 | 缓存旧包不回退 | 哨兵代际一次性清空；设备 `oldUnmarkedArtifactUnderTheSameKeyIsRejectedAndRebuiltWithAigc` 在 5/5 内 | **部分** | 机制成立但非键升版；与 14 同因 |
| 23 | KD-75 关闭判据逐条对账（E 文件） | `ws2-kd75-closeout.md §2` C1–C8 表 | 完成 | C2（模型解析文本）**未实现**；C4 字面不符 |
| 24 | KD-75 台账回写（关闭或维持） | `docs/known-defects.md:2906` Progress 块，**维持 open** | 完成 | — |

#### WS3 KD-76 20 轮主口径（25–33）

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 25 | 单轮入口 bootCount 采集 | app 侧 `Settings.Global.BOOT_COUNT`（`VisualJsConnectStageCalibrationInstrumentedTest.kt:944-955`，转录自 crosscheck N3）；宿主 `/proc/.../boot_id` 由 driver 采（`run_kd76_rounds.sh:254-343`） | 完成 | 计划写 `boot_id` 而 app 用 `BOOT_COUNT`——口径已在 R3 §2.3.6 纠正并落证据 §7.7 |
| 26 | 20 轮脚本：每轮 reboot + 逐轮校验 boot_id | `run_kd76_rounds.sh`（mtime 07:31）；WS3 实跑 20/20，20 个 host boot_id 全不同、逐轮 before==after | 完成 | — |
| 27 | 空 sha/异常轮标 INVALID 不静默剔除 | 见 WS1-5；`summarize_kd76.py:412,437,500-501` | 完成 | — |
| 28 | 汇总 n/p50/p95/max/G1/G2，判词只由 `Kd76ReportVerdict` | WS3 §4.1 输出 + §4.2 用 Kotlin embeddable 直调 `decide` 逐字一致（5000→PASS / 5001→FAIL / n=19→NOT_EVALUABLE） | 完成 | 非 Gradle；固定向量另由 18 例 pytest 钉住 |
| 29 | 设备实跑 20 轮 | **两次单批 n=20**：WS3 app `35e10339…` p95=1889；DEVICE app `ec135844…` p95=1969（`E-42-DEVICE-01 §2`） | 完成（单批） | **两批独立（A6）未满足**；两次 APK 不同，不得相加 n=40 |
| 30 | M 与 READY_TIMEOUT 关系裁定 | WS3 §6：不改 10 s 阈值/5 s 门；本次两段 p95 远低于各自预算，且不得据此反推可降 | 完成 | — |
| 31 | 29.5 s 链四段实测（S1–S4） | C1 p95=1672/max=1783、C2 p95=312/max=314、C3 仅 KD-66 witness=2000、**C4 UNVERIFIED**；预算和 29500 为上界 | **部分** | “四段实测”不成立（`kd76_chain_segments.py` 自带该纪律） |
| 32 | KD-76 台账回写 | `docs/known-defects.md:3000` Progress 块，**维持 open** | 完成 | A6/C4 未满足 |
| 33 | 设备证据落盘（APK sha 每轮一致） | 20 轮每轮 `KD76_META apkSha256=35e10339…` == `manifest.txt`；raw `build/kd76-runs/ws3-batch42-20/` | 完成 | 第二次在 `…-r2/` |

#### WS4 isolation 因果定案（34–41）

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 34 | 修复后工具跑 OFF/ON 配对（同 APK sha） | runner 因 F8 **exit 2 拒绝同 COMPONENT**；设备跑改手工 `am instrument`，判词仍由修复后报告工具出 | **部分** | 计划条文与 F8 互斥（§5 S4） |
| 35 | T4 控制轮（可核标记 + 已安装 APK 比对） | `t4_control_marker.py emit/verify` exit 0、`arm=ON_PRODUCTION_T4_PRESENT`、全 dex 写点 **6**；**无 OFF 产物** | **部分** | 控制臂 BLOCKED（runbook `ws4 §4.3`） |
| 36 | 低负载窗程序化前门 | `lowload_gate.py` snapshot 3/3 全 0；地板两版；门 **1 PASS / 2 INVALID**（la_ratio 越界，与宿主他线 Java 构建+3 模拟器重叠同现） | 完成 | INVALID 轮的 `own_cpu_busy` 未定案 |
| 37 | 红集逐方法比较 | `pair.md` 15 行逐方法表；15 方法 × 2 臂 × 3 轮全 `SKIP` → **无红集可比** | 完成 | 15 例零执行 |
| 38 | 结论判定（仅当配对+控制轮同时成立） | `ws4 §7` 三条件表全 ✗ → 不写因果 | 完成 | — |
| 39 | 设备实跑（15 例 × 两臂） | 6 run，每 run `numtests=15`，**PASS/FAIL/ERROR 全 0、SKIP 15**；臂开关确实生效（ON 15 条 hook / OFF 0） | **部分** | 15 例真实行为 UNVERIFIED |
| 40 | 证据落盘 | `ws4-isolation-causality.md`；raw `build/ws4-isolation-b42/`（72 文件） | 完成 | — |
| 41 | 台账回写（UNCONVERGED 或定案） | `docs/known-defects.md:3002` WS4 Progress 块，**UNCONVERGED** | 完成 | — |

#### WS5 KD-83 真实 v4 夹具（42–47）

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 42 | v4 夹具构造（计划写“v5 包去表 + closureVersion=4”） | **做法被纠正**：真 v72 载荷（`user_version=72`、91 表、`material_feature` 在且 0 行、无 `error_cause_*`），`tools/ci/make_v4_pack_fixture.py` 生成 | 完成（口径变更） | 计划措辞需主会话更正（crosscheck C5） |
| 43 | fixture digest golden 固定 | 本轮实跑 `sha256sum`：`closure4-schema72-mask15.smbk` 28,108 B `24941ed731f8b554…`；`v4-pack.golden.json` 8,769 B `3a110cbbc0592ce8…`；golden 内 `closureVersion=4` / 91 键 / `synthetic=true` / `closureDigest=49b5ab83…` | 完成 | 本轮亲手复核，与 E 文件逐位相同 |
| 44 | JVM 真实字节恢复、新表空、行数对账 | `V4PackFixtureTest` 8/8（先红 1 例 `assetMirrorMatchesAuthoritySourceByteForByte` → 绿，日志留档）+ `SmbkArchiveCodecTest` 35/35 | 完成 | JVM 不做逐表 COUNT（边界在 `ws5 §5`） |
| 45 | 设备 v4 包恢复到 73 | `BackupV4PackRestoreInstrumentedTest` **OK (3 tests)**（5584 `d88ec38f…` / 5566 `a3d0d4e2…`）；`readDatabaseVersion()=73`、`user_version=73`、新表 0、旧行不丢 | 完成 | 合成夹具口径 |
| 46 | 负向：损坏 v4 包 fail-closed | N1 自洽伪造 rowCounts → 恢复侧逐表 COUNT 拒（文案“逐表行数与声明不符”，原库不动）；N2 被删表非空/翻位 → 交换前拒 | 完成 | — |
| 47 | KD-83 关闭判据与台账 | `ws5 §7` 五判据全满足；`docs/known-defects.md:3282,3294` **关闭** | 完成 | 边界：合成包、只读侧 + 迁移；`known-defects:3294` 有 KD-75 段错位未修（§5 S10） |

#### WS6 6C-2 三裁定落地（48–55）

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 48 | 裁定①：仅图片须带题面/源证据；纯 `CLEAN_IMAGE` 拒 + 用例 | `SmbkSelectionPlan.imageEvidenceRoles/Tables`、`BackupSnapshotTrimmer.prepareImageEvidence/assertImageEvidenceRetained`、`AndroidBackupRepository.verifyImagesOnlyEvidence`；设备用例在树 | 部分 | 设备 `BackupSelectionRulingsInstrumentedTest` **1/4 绿**，3 红为夹具断言目标错（§5 S9） |
| 49 | 裁定②：空选择=仅身份/设置包，恢复不删本地 + 用例 | `BackupSelection.ALLOWED_MASKS` 含 0；`doRestore` Phase 2d 后只 `applyRestoredSettings`、不换库、`restartRequired=false` | 部分 | mask=0 设备恢复**未执行**（用例在 seed 断言处退出） |
| 50 | 裁定③：派生索引恒裁、恢复后重建 + 用例 | 维持 2026-10-23；mask=0 分支不写 `restore-index-rebuild-pending.json` | 完成 | 设备未验 |
| 51 | `SmbkSelectionPlan`/选择器口径同步 | `SmbkSelectionPlan`/`SmbkArchiveCodec`/UI 同步；`BackupSelection.kt` 允许集合 | 完成 | **触碰热区 `core/domain`**（§5 S7） |
| 52 | 备份包文案/升级说明 | `6c2-rulings.md §4`（创建面板/类别/摘要/恢复确认/回执/升级说明六段） | 完成 | `docs/current` 热区未改 |
| 53 | 设计件 §9.11 之外的裁定段更新 | `docs/research/2026-10-17-6c2-content-selection-design.md` §9.9/§9.10-10/§9.11；四方门 `check_backup_plan_sync.py` PASS（我实跑 exit 0） | 完成 | — |
| 54 | 设备：三种选择各一例备份/恢复 | `BackupSelectionRulingsInstrumentedTest` 1/4；`BackupSelectionUiInstrumentedTest` **OK (11 tests)** | 部分 | mask=0/9 语义 UNVERIFIED |
| 55 | 台账回写 | `REFACTOR-MASTER-LINE.md:5`；`agent-first-refactor-decisions…:3117` | 完成 | — |

#### WS7 D-04 预览生产装配（56–60）

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 56 | 注入点默认关闭 + 显式开关 | `TutorVisualPreviewAssembly.kt`（新，`TutorVisualPreviewInjection.DISABLED` 为默认） | **部分** | 用例**零执行**（core:data 装配类未定向跑；本批 core:data 只跑 PreviewReader） |
| 57 | 开关打开端到端（JVM/设备各一） | JVM/设备用例在树（`TutorVisualPreviewAssemblyTest` / `…InstrumentedTest`） | 未执行 | 同上，UNVERIFIED |
| 58 | 失败降级 `previewAssetId=null` 断言 | 用例在树（拒绝/抛异常/OOM/取消四腿） | 未执行 | 同上 |
| 59 | 开关不放广告面（`availableMethods` 不变） | 静态核对：能力/提示词文件 `git diff --stat` 为空（WS7 自述） | 部分 | 动态用例未跑 |
| 60 | 登记“MVA 未过前默认关闭” | `2026-10-26-batch-24-4e2-admission-checklist.md` D-04 行注记 | 完成 | 生产接线 BLOCKED（`ws7 §5`） |

#### WS8 发版前置（61–70）

> 全部事实来自 `docs/research/2026-11-04-ws8-release-prefight.md`（本记录员**全文实读**）；该轨有实跑，但**无本目录 E 文件**（`00-index.md:104`）。

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 61 | `assembleRelease` + mapping 产出与 checksum | 一次性本机钥匙下 **exit 0 / 7m03s / 466 tasks（141 executed）**；两 flavor APK + mapping 126,196,894 B（`c5b2d170…`）+ seeds/usage/configuration sidecar sha 全落档；mapping↔APK dex 5/5 命中、负控 0 | 完成（本地签名口径） | APK 由 dirty 树构建，**G-REL-00 FAIL**；非生产签名 |
| 62 | release APK 冒烟（安装/冷启/三链路） | `ps16k_x86_64`（5620）：设备 sha == 构建 sha（`724ce718…`）；两次 `COLD` 1058/876 ms；录入/复习/导出三链路通；9 类崩溃签名 5 份 logcat 全 0 | 完成 | 未覆盖 `libquickjs.so`/`libLiteRt.so` 装载 |
| 63 | 16KB：x86_64 宿主可运行证明 + arm64 等待清单 | **计划前提被推翻**：`ps16k_x86_64` 冷启 28.9 s、`PAGE_SIZE=16384`、`libmlkit_google_ocr_pipeline.so` 真实装载；静态面 `zipalign -P 16` 两包 exit 0、14 `.so` 全 STORED、ELF 判据 14/14 + 负控 FAIL | 完成（口径反转） | arm64 **BLOCKED**（runbook 4 步） |
| 64 | 签名配置清单（不落密钥） | 四变量名 + 缺断言三条；`git ls-files '*.jks/…'` = 0；一次性证书 `9dc07e9f…`、DN 带 `NotForRelease` | 完成 | 生产 keystore/Play App Signing 决策 = C 类 |
| 65 | `mapping.txt` 保留策略与版本绑定 | 六条策略（不进 git/命名/落点/寿命/Play 二选一/绑定字段） | 完成 | 未建 sidecar 生成脚本（该轨明示“不指认失败不加机制”） |
| 66 | 依赖钉版门复跑 | pins exit 0（quickjs 1.0.15 / stdlib 2.3.21）+ 负控 `--expect-kotlin-runtime 2.4.10` **exit 1**；lock/manifest/keep/stdlib 四门 exit 0 | 完成 | 门消费 release 变体中间产物，`clean` 后单跑会红 |
| 67 | KD-72 锁窗口：隔离副本 `--write-locks` | `git archive` 副本（3,320 文件、0 锁）两次尝试**均 exit 1**（benchmark 变体歧义、androidTest 自引用歧义），**0 锁写出**；真树未动 | 部分 | KD-72 维持 open，最小等待清单 W1–W6 |
| 68 | 供应链 license/更新度快照（只读） | 340 坐标 / 328 取到 license（Apache-2.0 系 307）；40 直接依赖 `maven-metadata` 40/40，落后 35 | 完成 | 12 个上游 POM 无 license 标签（记缺失不推断） |
| 69 | R-04 Keeper 可执行性（不改 settings） | 副本内 apply 到 `:app` 后 `:app:tasks` **BUILD SUCCESSFUL 52 s exit 0**；副本 `settings.gradle.kts` 与真树逐字节相同 | 部分 | **K1–K6 未跑**；不得写“R-04 已可执行” |
| 70 | 发版门清单落盘 | `2026-11-04-release-gates-draft.md`（G-REL-00…28） | 完成 | 草案；`docs/current/release-gates.md` 未改 |

#### WS9 合同与准入（71–75）

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 71 | 契约 V1.1 P0 可应用 patch（C-1/C-6/C-10） | `2026-11-04-contract-v1.1-p0-applicable.patch` 2 hunks +4/−2，`git apply --check` **exit 0**；**未应用** | 完成（patch） | `docs/current` 仍 7 M → 并入 BLOCKED |
| 72 | 准入清单 69 行按本批实测刷新 | 附录 O：✅20/◐35/✗10/?2/⛔1/§1，**0 项转通过**，仅 R-03 `?→◐` | 完成 | MVA 不成立、`availableMethods` 未动 |
| 73 | AUD-08 人工金标协议执行（n≥20 抽样） | `2026-11-04-aud08-human-gold-sampling-plan.md`：框 155、抽样 **24 页**、逐页 sha256 冻结、dev16/acceptance8、7 张 v1 页全在抽样内 | 部分 | 人工标注 **BLOCKED**（无标注员）；S0/S2–S7、G3/G4/G5 未执行 |
| 74 | 门值复算维持 FAIL + 自动决策红线 | 本轮**我实跑** `check_split_gate.py` → **TRUE_GATE_EXIT=1**（① 4/7=0.5714 < 0.9；③ 0/16=0.0000 ≤ 0.05）；红线 `gate.passed && hardRedLines.satisfied` + 4C③ | 完成 | 阈值/口径/分母零改动 |
| 75 | 主线上/决策台账批次 42 行 | `REFACTOR-MASTER-LINE.md:6`；`agent-first-refactor-decisions…:3808-3818` | 完成 | — |

#### WS10 备份/设备全量（76–80）

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 76 | 全量套件（28+）在绑定 APK 上复跑 | **32 套件 / 258 例：执行 243 / 绿 241 / 红 2 / 跳 15**（`E-42-WS10-01 §1`；DEVICE §6 同数） | 部分 | 红 2 = p09（批次 40 同方法复现）+ selrestore closure-4（夹具缺陷）；跳 15 = isolation；APK 早于本批部分测试改动 |
| 77 | 跨版本备份矩阵（73→73、v4→73、v5→73） | 73→73：selcreate 3/3 + selrestore 子集/full + rowcounts 2/2；v4→73：v4pack 3/3；v5→73：selrestore 10/11 | 完成 | v5→73 的 1 红即 §1.2 的 v4 合成用例装置缺陷 |
| 78 | 崩溃签名九类扫描 | 45 份日志聚合：八类 0，`death was not confirmed` 仅 p10 设计性 1 | 完成（覆盖受限） | isolation 未执行；未覆盖真机/arm64 |
| 79 | worker 生命周期 / 强停恢复 | worker：spawn 5 / warm-up 5 / failed 0 / 无孤儿；`am force-stop` → `COLD`、`quick_check=ok`、`user_version=73`、`problem=5` | 完成 | 不覆盖 LMK/真实低内存 |
| 80 | 性能冒烟（warm-up/首帧/导出） | 首帧 2901/3205/2876 ms；warm-up 单轮 938 ms（`bind=616`）；导出 `Worker result SUCCESS` ≈1.30 s | 完成 | 单轮是 warm-boot 冒烟，非主口径 |

#### WS11 文档与计划收口（81–84）

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 81 | 计划剩余项全量盘点 | `inventory.md` §2–§8（PRD/6C/4E/5B/5b/5c 逐项；剩余 **73 项**） | 完成 | 状态多为转录，标注了 90/74 缺陷 |
| 82 | 剩余项分派清单 | `inventory.md §9`：本线可做 30 / 需用户 5 / 需外部 6 / 需他线 7 / 热区 25 | 完成 | 分派为建议，未获用户确认 |
| 83 | 记录 §0 口径与 §7 提交清单 | 本文件 §0/§7 | 完成（本记录） | — |
| 84 | 下一批计划草案（批次 43） | `inventory.md §10`：研究 4 + 实施 40 + 流程 14 + 硬约束/验收 | 完成（草案） | 未写入 `.zcode/plans/` |

#### WS12 验证收口（85–90）

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 85 | 修复环后强制重编强门 | `build/batch42-strong-postfix.log`（18:57:30–18:59:20，HEAD `a5a61a46`，581 脏）：6 次 `--rerun` 编译 **exit 0**（data 40 s / database 12 s / ui 2 s / export 3 s / app LF 8 s / app SO 6 s）+ `assembleLocalFirstDebug` exit 0；末行 `GATE RESULT: PASS` | 完成 | 是**真实重编**（非 UP-TO-DATE 假绿）；我读日志逐行核 |
| 86 | 12 模块单测门 | **预修复**：`build/batch42-unit.log`（14:55，HEAD `faab17f5`）12 模块 `testDebugUnitTest`，**BUILD FAILED 2m08s，4 红** | **部分（门是红的）** | 环后**未重跑 12 模块**：只跑 5 组定向（§3.4）。4 红中 1 红（KernelWave0）已在本树修好；3 红（KB）由他线提交 `a5a61a46` 重钉，**环后状态 UNVERIFIED** |
| 87 | 设备矩阵关键套件复跑 | 设备轨 15:18–17:10（`ec135844…`）跑完 32 套件 + 4 设备类；WS10 13:28–14:05（`35e10339…`） | 部分 | **修复环（18:29 起改动）之后未再跑任何设备**；两套 APK 互不可替代 |
| 88 | 双复核（独立 + 对抗）针对本批交付 | **无载体**：证据目录无 review E 文件；`grep 对抗复核/独立核对` 只命中 `inventory.md` | **未执行** | 本记录员做了有限独立复核（§5 标“本轮实读/实跑”的条目），**不等于** P15/P16 双复核 |
| 89 | 证据与记录 | 证据目录 12 件 + 本记录 | 完成 | `00-index.md:102` 的“记录未创建”由本文件消除 |
| 90 | 提交清单与风险登记 | 本文件 §7/§6 | 完成（本记录） | 未做 git 写；块划分待门禁后逐 hunk 核对 |

### 1.3 流程 20 行

> 计划 §3 的 11 项（基线 / 研究交叉复核 / 集成 / 单测门 / 强门 / 设备轨 / 证据 / 独立核对 / 对抗复核 / 修复环+环后强门 / 记录）+ 9 个“各 WS 内互斥子件”（PR12–PR20，不重复计为独立阶段）。
> **阶段号说明**：`.zcode/workflow-drafts/` 下**没有**批次 41/42 的工作流脚本（本轮实查），故阶段号沿用批次 41 记录 §1.3 的定义（P0 基线、P0b 研究复核、P10 集成、P11 单测门、P12 强门、P13 设备、P14 证据、P15/P16 复核、P17 修复、P18 环后强门、P19 记录）。**§3 的门禁一律按日志文件名 + 时间指认，不以阶段号代替命令。**

| # | 流程项 | 落点/证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| PR1 | 基线核对（P0） | `2026-11-04-batch-42-baseline.md` | 完成 | 该件 §284 已把旧“3 红”标为转录/UNVERIFIED |
| PR2 | 研究交叉复核（P0b） | `evidence/…/research-crosscheck.md`（7 冲突 + N1/N2 探针） | 完成 | 该件自述“六份研究件本轮不存在”——它们 05:28–05:42 才落盘 |
| PR3 | 集成（P10） | 无独立集成报告；载体一致性由 00-index + 各 E 文件承担 | 部分 | 无包名/import/热区零改动的机器核对产物 |
| PR4 | 单测门（P11） | `build/batch42-unit.log`：12 模块，exit≠0，4 红（§3.1） | **FAIL** | 3 KB 红为他线；1 红（KernelWave0）为本批首次真实重跑暴露 |
| PR5 | 强门（P12） | `build/batch42-strong.log`：7 次运行全 `exit 0`（§3.2） | 完成 | 真实重编（data 51 s / app 48 s / SO 46 s） |
| PR6 | 设备轨（P13） | E-42-DEVICE-01（15:18–17:10，`ec135844…`）+ WS3/WS4/WS5/WS10 | 部分 | WS6 1/4、isolation 全跳、p09 红 |
| PR7 | 证据（P14） | 11 `.md` + 1 `rulings/*.json`；索引 163 行 | 完成 | WS1/WS8 无独立 E 文件（`00-index.md:102-104`） |
| PR8 | 独立核对（P15） | — | **未执行** | 无产物 |
| PR9 | 对抗复核（P16） | — | **未执行** | 无产物；假绿探针只在 P0b 交叉复核里做过 |
| PR10 | 修复环（P17） | 18:29–19:05：`isolation_pair_report.py`/`run_isolation_pair.sh`/`visual_js_isolation_methods.txt` + 3 例测试；`KernelWave0SchemaContractTest` 字面量 71→73 | 部分（无环报告） | 无修复清单/复核 findings 传入的落盘记录；改动只由 mtime 与 diff 可辨（§5 S3） |
| PR11 | 环后强门（P18） | `batch42-strong-postfix.log` PASS；`batch42-unit-postfix.log` 6 组 exit 0（首组任务名错已重试）；`batch42-pytest-postfix.log` 57 passed | 完成（有瑕疵） | 定向单测的聚合行是 `TARGETED RESULT: FAIL`（因首次任务名错），最终 6 组全 exit 0 |
| PR12 | 记录（P19） | 本文件 | 完成 | — |
| PR13 | WS1 工具子件 | 三文件 pytest **44 passed**（本轮实跑）；四条 57 passed | 完成 | 与证据里的“41 passed”不符（§5 S3） |
| PR14 | WS2 设备证据子件 | `E-42-DEVICE-01 §1`（5/5 + pypdf） | 完成 | 绑定 `ec135844…` |
| PR15 | WS3 汇总/分段子件 | `summarize_kd76.py` + `kd76_chain_segments.py`（C4 UNVERIFIED） | 部分 | 四段实测不成立 |
| PR16 | WS4 判定规则子件 | 报告工具判 UNCONVERGED + exit 1 | 完成 | 运行时未触发 F7 缺失路径 |
| PR17 | WS5 夹具冻结子件 | 夹具 + golden + `.sha256` ×3 | 完成 | 四方门尚未消费该 golden（`ws5 §8 R2`） |
| PR18 | WS6 三裁定子件 | 域/数据/UI/设备四层用例在树；设备 1/4 | 部分 | mask=0/9 设备语义 UNVERIFIED |
| PR19 | WS7 装配静态子件 | 能力/提示词 `git diff --stat` 为空 | 完成（静态） | 动态零执行 |
| PR20 | WS8/WS9 门子件 | WS8 五门 exit 0 + 负控 exit 1；AUD-08 exit 1 | 完成 | Keeper K1–K6、锁写未过 |

## 2. 各轨事实

### 2.1 轨状态总表（按 E 文件头表的 `verdict` 原文）

| 轨 / E-ID | verdict 原文要点 |
|---|---|
| E-42-WS2-01 | “KD-75 维持 open”；AI 图判据已接产品链，**模型解析文本判据仍未落**；本轨零设备 |
| E-42-WS3-01 | “主口径 PASS（单批 n=20）”：G1 green、`M.p95=1889 ms`；**KD-76 条目仍 open**（A6 + C4） |
| E-42-WS4-01 | “**UNCONVERGED / 不写因果**”：低负载门 1/3 PASS、6 run 15 例全 `-4`、无 OFF 控制臂 |
| E-42-WS5-01 | “**KD-83 关闭（合成合法 v4 夹具口径）**”：JVM 8/8 + codec 35/35、设备 3/3 |
| E-42-WS7-01 | “**PARTIAL / BLOCKED**”：注入点在树默认关闭；生产 commit 接线 BLOCKED |
| E-42-WS9-01 | 五项交付物落盘；契约未应用、人工金标 BLOCKED、门值 FAIL 维持 |
| E-42-AUD08-01 | **FAIL（exit 1）**：① 4/7=0.5714、③ 0/16=0.0000；阈值零改动 |
| E-42-DEVICE-01 | **PARTIAL**：WS2 PASS、WS3 n=20 PASS、WS4 UNCONVERGED、WS5 3/3、WS6 1/4、WS10 243/241/2/15 |
| E-42-WS10-01 | **PARTIAL**：32 套件 258 例 → 执行 243 / 绿 241 / 红 2 / 跳 15 |
| E-42-RESEARCH-CROSSCHECK-01 | 6 题判定 + 7 冲突（C1/C2/C3/C5/C6 高）+ 独立复现 F7/F10 假绿 |

### 2.2 必须并读的三处轨间张力

1. **isolation 的“同 APK sha”与 F8 的“两臂不同身份”**：计划项 34 要求同 APK 配对，修复后的 runner 直接拒绝同 COMPONENT（exit 2）。本批靠**手工 `am instrument` 等价驱动 + 修复后报告工具判定**绕过，属“非 runner 的通过记录”。两条要求本身互斥，未裁定（§5 S4）。
2. **KD-83 关闭 vs 设备仍有 1 红**：`BackupV4PackRestoreInstrumentedTest` 3/3（真 v4 字节）与 `BackupSelectionRestoreInstrumentedTest.closureFourArchive…` 红并存；后者是 **Room 懒开库的测试装置缺陷**（`open().close()` 不触发 72→73），不是产品 v4 路径。裁定以 v4pack 为准，但**该装置缺陷未修**。
3. **“32 套件全绿”不存在**：2 红（p09、selrestore closure-4）+ 15 跳（isolation）；`OK (n tests)` 在有 `-4` 时不等于通过。

### 2.3 WS1 五条修复的代码现状（本记录员实读）

- F7：`isolation_pair_report.py` 现在**要求** `--expected-methods`（`:532` `required=True`），缺清单直接 `expected_methods_missing` → 无法再出现“两臂都没跑 → 都没红”。缺失方法物化为 `UNKNOWN`（`:311-321`）。
- F8 两半：`run_isolation_pair.sh:169-171` 强制期望清单存在且非空；同 COMPONENT die exit 2。`t4_control_marker.py:192-302` 用 `adb pm path` + 拉包算**已安装 APK** 的 sha/类清单，verify 时也重取（`:334-358`）。
- F10：`summarize_kd76.py` 的 INVALID 逻辑（`:412,437,500-501`）与 sha/boot 门（`:508-535`）均在树；坏轮计入 n 上限。
- F11：`decide` 三参数、阈值只来自常量（`:31,80-84,99-109`）；legacy 入口 `@Ignore` + 硬失败（`:150-160`）。
- F12：`NOT_PLANNED` 显式态 + `totalGranted` 取两通道去重总量（`ReviewCapacityPreviewReader.kt:138-149,215`）。

## 3. 门禁

> 本节只写**有日志/有输出**的运行。**本记录员没有运行 Gradle**；标“（本轮实跑）”的是我亲手执行的非 Gradle 检查。

### 3.1 单测门（预修复，`build/batch42-unit.log`）

| 项 | 值 |
|---|---|
| 命令形状 | 12 模块 `testDebugUnitTest`（`core:markdown` NO-SOURCE）；HEAD/脏数按 15:07 强门头行推定（`faab17f5` / 648） |
| 结果 | **BUILD FAILED in 2m 8s**；158 tasks（12 executed） |
| 红 1 | `core:database` **248 tests / 1 failed**：`KernelWave0SchemaContractTest > the study database version matches the latest exported schema`（`KernelWave0SchemaContractTest.kt:44`，字面量 71 vs `STUDY_DATABASE_VERSION=73`） |
| 红 2–4 | `core:data` **1637 tests / 3 failed**：`ProductionLexicalLegExportTest:242`、`Stage1LexicalLabTest:262`、`Stage2LexicalScoresExportTest:207`（KB 词面，他线） |
| 归因 | 1 红=测试字面量陈旧（非本批产品回归；环内已修，见 3.4）；3 红=KB 线；**产品码面 0 新红，但门本身是红的** |
| 缺项 | 日志**没有** exit code 行（只有 `BUILD FAILED`）；总用例数未打印（Gradle 只对失败模块打印计数） |

### 3.2 强门（预修复，`build/batch42-strong.log`）

| 项 | 值 |
|---|---|
| 时间/绑定 | 15:07:35 起；HEAD `faab17f5`；648 脏 |
| 任务集 | 6 次 `--rerun` androidTest Kotlin 编译（core:data / core:database / core:ui / core:export / app LF / app SO）+ `:app:assembleLocalFirstDebug --continue` |
| 结果 | **7/7 `exit 0`**；真实重编（data 51 s、database 39 s、ui 12 s、export 20 s、app LF 48 s、app SO 46 s）；assemble 34 s / 226 tasks（4 executed） |

### 3.3 环后强门（`build/batch42-strong-postfix.log`）

| 项 | 值 |
|---|---|
| 时间/绑定 | 18:57:30–18:59:20；HEAD `a5a61a46`；581 脏 |
| 结果 | 6 次 `--rerun` 编译全 `exit 0`（data 40 s / database 12 s / ui 2 s / export 3 s / app LF 8 s / app SO 6 s）+ assemble `exit 0`；末行 **`GATE RESULT: PASS`** |
| 强度 | 是真实重编；但仍**不是干净窗口**（共享树、他线在飞） |

### 3.4 环后定向单测 + 工具测试（`build/batch42-unit-postfix.log`、`build/batch42-pytest-postfix.log`）

| 运行 | 结果 |
|---|---|
| `:core:database:testDebugUnitTest --rerun` | **exit 0**（32 s）→ 红 1 关闭 |
| `:core:data:testDebugUnitTest --tests *ReviewCapacityPreviewReaderTest` | **exit 0**（9 s） |
| `:core:model:testDebugUnitTest --tests *PdfAigc*` | **exit 1** —— `Unknown command-line option '--tests'`（模型模块是 JVM 模块，任务名错，**不是测试失败**） |
| `:core:export:testDebugUnitTest` | **exit 0**（2 s） |
| `:app:testLocalFirstDebugUnitTest --tests *Kd76ReportVerdictTest` | **exit 0**（9 s） |
| 聚合行（19:02:34） | **`TARGETED RESULT: FAIL`**（因上一行任务名错） |
| `:core:model:test --tests *PdfAigc*`（重试） | **exit 0**（3 s） |
| `:core:export:testDebugUnitTest --rerun` | **exit 0**（5 s） |
| pytest（环后） | `build/batch42-pytest-postfix.log`：**57 passed in 0.54s** |

本轮我实跑并读到输出的测试结果 XML（19:03 时点）：

| XML | tests / fail / err |
|---|---|
| `core:export/testDebugUnitTest`（16 文件合计） | **166 / 0 / 0** |
| 其中 `MistakePdfExporterAigcJvmTest` | **9 / 0 / 0** |
| `core:database/testDebugUnitTest`（50 文件合计） | **248 / 0 / 0**；`KernelWave0SchemaContractTest` **8 / 0 / 0** |
| `core:data/testDebugUnitTest`（1 文件，最后一次运行覆盖目录） | `ReviewCapacityPreviewReaderTest` **6 / 0 / 0** |
| `app/testLocalFirstDebugUnitTest` | `Kd76ReportVerdictTest` **8 / 0 / 0** |

> **口径警告**：`core/data/build/test-results` 被最后一次 `--tests` 运行覆盖，**只剩 1 个类**——core:data 全模块的环后结论**不可回读**。

### 3.5 本轮亲手实跑的只读门

| 门 | 命令（逐字） | 结果 |
|---|---|---|
| 工具测试（三文件） | `python -m pytest tools/tests/test_isolation_pair_report.py tools/tests/test_t4_control_marker.py tools/tests/test_summarize_kd76.py -q` | **44 passed**（19+7+18；非证据里的 41，见 §5 S3） |
| 工具测试（四文件） | 上三 + `tools/tests/test_lowload_gate.py` | **57 passed**（= 环后日志的 57，可重跑指认） |
| 工具测试（全量） | `python -m pytest tools/tests -q` | **1041 passed, 70709 subtests passed in 325.91s**，exit 0 |
| 备份四方门 | `python -I tools/ci/check_backup_plan_sync.py` | **exit 0**；末行 `PASS 四方一致（code / §9.11 / golden / 73.json）`；v4 冻结 closure=4 / prefix `smbk-selection-v4` / trim 83；v4 空包 digest `ee9a1b4d…` recomputed==pinned |
| AUD-08 切分门 | `python -I tools/split_golden/check_split_gate.py` | **TRUE_GATE_EXIT=1**；① 0.5714；③ 0.0000；阈值零改动 |
| v4 夹具字节 | `sha256sum tools/backup_fixtures/v4/{closure4-schema72-mask15.smbk,v4-pack.golden.json}` | `24941ed7…f763ad`（28,108 B）/ `3a110cbb…9f338a`（8,769 B），与 E 文件逐位相同 |
| isolation 报告 CLI 现状 | `python -I tools/ci/isolation_pair_report.py --run … --off-arm-production-t4 test-side-only --out …` | **TRUE_EXIT=2**：`error: the following arguments are required: --expected-methods`（§5 S3） |

### 3.6 P13/P14/P19 实值

- **P13 设备轨**：E-42-DEVICE-01 自述 `executed-at 15:18–17:10`，绑定 app `ec135844…`；另有 WS3 07:01–07:22（`35e10339…`）、WS4 07:42–09:10（`fe9452aa…`）、WS5 09:20–09:36、WS10 13:28–14:05（`35e10339…`）。**本记录员未复跑设备**。
- **P14 证据轨**：11 `.md` + 1 `rulings` JSON，163 行索引；缺口 10 条（`00-index.md §6`）。
- **P19 记录**：本文件（此前 `docs/research/2026-11-04-batch-42-record.md` 不存在，`00-index.md:102` 已登记该缺口）。

## 4. 设备证据（仅 E 文件可证）

> 设备串：emulator-5584（`kb_test_device`）、5566（`audit_full_ui_forest_20261028`）、5562、5620（`ps16k_x86_64`）。全部 API 34 / x86_64 / PAGE_SIZE 4096，除 5620（SDK 36 / 16384）。**本记录员未执行任何 adb**。

### 4.1 产物身份（`00-index.md §2.1` + 各 E 文件头）

| 轨 | app sha256 | 测试包 sha256 | 设备 | 时点 |
|---|---|---|---|---|
| WS3 | `35e10339…bff1f8` | app.test `0e906482…4e3aca` | 5584 | 07:01–07:22 |
| WS4 | `fe9452aa…669e3e` | `d300d885…c9a721`（两臂同一份） | 5562 | 07:42–09:10 |
| DEVICE | `ec135844…e19bb5` | app.test `a2532e16…1e9687b`；export.test `0d34532c…1bbeca0`；data.test `a3d0d4e2…538e66`；db.test `a7a44373…d709c5` | 5584 / 5566 | 15:18–17:10 |
| WS10 | `35e10339…bff1f8` | app.test `0e906482…`；data.test `d88ec38f…0552b2`；db.test `7b4df3ea…f10d0` | 5584 | 13:28–14:05 |
| WS5 | —（只装 data.test） | data.test `d88ec38f…0552b2` | 5584 | 09:20–09:36 |
| WS8 冒烟 | `724ce718…342b44`（release/本地签名） | — | 5620（16KB） | 11:00–12:30 |

### 4.2 各面结论（原文事实）

| 面 | 可写 | 不得外推 |
|---|---|---|
| KD-75 | `MistakePdfExporterAigcInstrumentedTest` **OK (5 tests)**；`real-export-labeled.pdf` 7 键 `/AIGC`、`Label=1`；`pure-user-unlabeled.pdf` 无 `/AIGC`；`PdfAigcDeviceRoundTripInstrumentedTest` **OK (10 tests)** | C2（模型解析文本）、字面 v3→v4、DOCX、`PdfRenderer≠pypdf` |
| KD-76 | WS3 n=20、p95=1889、max=2089、20 个 boot_id 全不同、bootCount 3..22；DEVICE 第二次 n=20、p95=1969、bootCount 24..43 | 两批独立（A6）、C4、arm64/真机、产品进程首请求；**两次不可相加** |
| isolation | 报告判 UNCONVERGED（exit 1）；T4 marker 绑已安装 APK、全 dex 写点 6；低负载门 1/3 PASS；6 run 15 例全 `-4` | 因果、T4 排除、两臂等价、15 例行为 |
| KD-83 | 冻结字节 sha == golden → 恢复 → `user_version=73`、新表 0、旧行不丢；N1/N2 负向拒收（`OK (3 tests)`，两台设备各一次） | 历史真实用户 v4 包（不存在）、带图 v4 包、其他 API |
| 6C-2 | `BackupSelectionRulingsInstrumentedTest` **1/4**（3 红为夹具断言目标错）；`BackupSelectionUiInstrumentedTest` **OK (11 tests)** | mask=0 / mask=9 的恢复语义（未执行） |
| WS10 | 32 套件 / 258 例 / 243 执行 / 241 绿 / 2 红 / 15 跳；崩溃九类 0（除 p10 设计性）；强停恢复 `COLD` + `quick_check=ok`；首帧 2876–3205 ms | “28 套件全绿”、isolation 通过、批次 42 工作树新用例 |
| WS8 冒烟 | release APK 在 16KB 设备上三条链路通、9 类崩溃 0、设备 sha == 构建 sha | 生产签名、arm64、`libquickjs`/`libLiteRt` 装载、干净树可复现 |

### 4.3 干扰与设备卫生（E 文件登记，供复跑参考）

- 5584 首批有他线并发 `am_kill … due to start instr` 与 15:31 重启 → `suites-r2/` 三套件判“Process crashed”，改在 5566 绑定 sha 复跑后全绿（backup 34/34 / rowcounts 2/2 / trimmer 10/10）。
- 5566/5562 长 uptime 下迁移矩阵 I/O 饥饿（guest 147 MB free + swap，~40 s/版本）；刚重启的 5584 上 `OK (1 test) 201.65 s` → 记“16/16 方法通过（分跑）”，不记单次 full-suite 通过。
- 5584 曾 `adb root` 读 ANR trace 后 `unroot`，m7071 被打断一次，已单独复跑 3/3。

## 5. 复核发现与处置

> 来源分三类并逐条标注：**（本轮实读/实跑）** = 本记录员亲手核对；**（转录）** = 引 E 文件/日志原文；**（推定）** = 未证推断。**本批没有 P15/P16 双复核产物**（§1.3 PR8/PR9），故本节是“记录员 + 各轨自报 + 交叉复核件”的合并，不等同于双复核。

| # | 发现 | 证据 | 严重度 | 状态 | 处置 |
|---|---|---|---|---|---|
| S1 | ask 与 `00-index.md` 写“100 = 6+74+20”，计划实为 **116 = 6+90+20**（90 个实施项） | 本轮解析 `count 90 / missing []`；`plan:1,4,142` 均写 90/116；`00-index.md:4`、`inventory.md:24` 写 74（后者误记为“计划首行原文”） | 中 | **本轮实读** | 本记录按 90 行对账；计划无需改，**索引/盘点段需更正** |
| S2 | 证据目录里的 “41 passed” 已不可复现：同一三文件现为 **44** | 本轮实跑 44 passed（19+7+18）；`ws4 §2.1`、`00-index.md §7-2` 写 41 | 中 | **本轮实跑** | 因 18:29–18:32 修复环给报告工具加 `--expected-methods` 必填 + 3 例新测试；结论方向不变，但“41”应视为**环前时点值** |
| S3 | `ws4 §9` 与 `device-track §9` 记录的 `isolation_pair_report.py` 命令**现在跑不通** | 本轮实跑 `… --run … --off-arm-production-t4 test-side-only --out …` → **TRUE_EXIT=2**，`required: --expected-methods` | 中 | **本轮实跑** | 证据的命令行需补 `--expected-methods tools/ci/visual_js_isolation_methods.txt` 才可复现；不影响原判词（原判词已由 UNCONVERGED 结论兜住） |
| S4 | 计划项 34“同 APK sha 配对”与 F8“拒绝同 COMPONENT”**互斥**，未裁定 | 计划 `:64`；`ws4 §2.2` runner exit 2 原文；`run_isolation_pair.sh:51,169-171` | **高** | 转录 + 本轮实读源码 | 本批以手工驱动绕过（非 runner 记录）；需主会话在“机制级因果”与“同 APK 可复现”之间二选一 |
| S5 | 单测门**是红的**（4 红），且“仅 3 例 KB 红”的旧口径被推翻 | `build/batch42-unit.log:246-270,304` | 中 | 转录（日志逐行读） | 第 4 红为本批首次真实重跑暴露；环内已修（`:database --rerun` exit 0） |
| S6 | 环后**未重跑 12 模块单测门**；core:data 全模块环后结论不可回读 | `batch42-unit-postfix.log` 只 5 类定向；`core/data/build/test-results` 只剩 1 个类 | 中 | **本轮实读** | item 86 记“部分”；重跑命令见 §6.3；KB 3 红现状 UNVERIFIED |
| S7 | 热区被触碰：`core/domain/BackupSelection.kt` + `BackupSelectionTest.kt` | 本轮 `git status --porcelain -- core/domain`（4 M）；计划 §4 “`core/domain` 仍不改”；`6c2-rulings.md:68-70` 自承并为 item 51 辩护 | **高（纪律）** | **本轮实读** | 登记为计划内部矛盾（item 51 要求同步选择器，§4 禁改同模块）；未回滚；需主会话裁定是否追认 |
| S8 | `core/domain/BackupRepository.kt`、`TutorJudgedReview.kt` 仍 M（他线） | 同命令 | 低 | 本轮实读 | 不归本批；提交时排除 |
| S9 | WS6 设备 3 红是**断言目标错**：`countMistakes()` = ACTIVE `error_book_entry` 计数，夹具只种 problem/revision | `ProblemDao.kt:318-319`；`BackupSelectionRulingsInstrumentedTest.kt:104/143/192,238-239`（`device-track §5`） | 中 | 转录 | mask=0/9 设备语义 UNVERIFIED；修法=补种 `practiceUnits + errorBookEntries` 后重跑 |
| S10 | KD-83 已关闭但 `known-defects.md:3294` **文段错位**（KD-75 正文挂在 KD-83 下） | `ws5 §0-5`；本轮 `grep` 命中 `:3294` 自述“文档错位未移动” | 低 | 转录 + 本轮实读一行 | 台账维护者修；不影响关闭裁定 |
| S11 | v4 夹具 golden 尚未被任何 CI 门消费（四方门仍只钉 v4 **空包** digest） | 本轮 `check_backup_plan_sync.py` 输出只列空包 digest；`ws5 §8 R2` | 中 | 本轮实跑 + 转录 | 记残余风险：有人重打包/重算不会被门抓住 |
| S12 | `isolation` 15 例在**三台设备**上都零执行（全 `-4`），`own_cpu_busy` 未定案 | `ws4 §5`、`E-42-WS10-01 §1.3`、`device-track §3` | **高（口径）** | 转录 | 任何“isolation 通过/28 套件全绿”不成立；修复方向（自证探针口径）属新裁定 |
| S13 | 计划项 63 的前提与实测相反：x86_64 宿主**能**跑 16KB | `ws8 §2.4`（`PAGE_SIZE=16384` + `libmlkit… : ok`） | 中 | 转录（`00-index` N6 亦标未复跑） | 计划措辞需更正；arm64 仍 BLOCKED |
| S14 | release APK 由 dirty 树构建（revision=HEAD 但树不干净） | `ws8 §2.1,§4` | 中 | 转录 | G-REL-00 FAIL；发版就绪**不成立** |
| S15 | WS7 三条核心用例（57/58/59）零执行，`core:data` 装配类未定向跑 | `ws7 §4,§6 U1/U2`；`batch42-unit-postfix.log` 无该类 | 中 | **本轮实读日志 + 转录** | item 56–59 记“部分/未执行”，不得称完成 |
| S16 | KD-76 有**两次**单批 n=20（不同 APK），A6“两批独立”仍未满足 | `E-42-WS3-01 §7-1`；`device-track §2`；`00-index` N3 | 中 | 转录 | KD-76 维持 open；不得相加 n=40 |
| S17 | 设备用的 app.test 早于本批测试改动（01:00 构建） | `E-42-WS10-01 §0`；`00-index` N4 | 中 | 转录 | 设备绿不覆盖本批新改用例 |
| S18 | 本批**无**独立核对/对抗复核产物；修复环 ask 是否携带 findings 无落盘凭证 | 证据目录 12 件无 review 文件；`grep 对抗复核` 只命中 `inventory.md` | 中（流程） | **本轮实读** | item 88 记“未执行”；建议批次 43 补 P15/P16 并把 findings 注入修复环（批次 41 F20 同型） |
| S19 | `batch42-unit.log` 无 exit code 行（只有 `BUILD FAILED`）；`batch42-unit-postfix.log` 的聚合行是 `TARGETED RESULT: FAIL` | 本轮逐行读三个日志 | 低 | **本轮实读** | §3 如实并列：不把 `FAIL` 聚合行写成全绿，也不把 6 组 exit 0 写成聚合通过 |
| S20 | 工作树 119 M 中含数十份与批无关的 docs M（含 `docs/research/2026-09-28…` 等）与他线 KB/dense `??` | 本轮 `git status --porcelain -uall -- docs/research` | 低 | 本轮实读 | §7 排除；提交前逐 hunk |

### 5.3 复发门（计划 item 12：五条高/中项“若再出现如何被门抓住”）

| 项 | 复发形态 | 抓住它的门（现存） | 门强度 |
|---|---|---|---|
| F7（缺方法假绿） | 一臂少跑/漏报方法，报告仍判 IDENTICAL | `--expected-methods` **必填** → 缺清单 `expected_methods_missing`；缺失方法物化 `UNKNOWN` → `skipped_or_unknown`；`paired = not reasons` | 强（fail-closed），但**运行时未演练**（两臂方法齐） |
| F8（同 COMPONENT / 只比源码树） | 两臂同一构建身份；标记只比源码 | runner 同 COMPONENT die exit 2；marker `identity_source=installed_apk` + `installed_apk_differs_from_artifact` + verify 重取安装包 | 强（结构拒绝 + 安装级绑定）；**OFF 臂产物仍不存在** |
| F10（空 sha 整剔 / 无 boot 门） | 某轮无 sha 被静默丢弃；同 boot 内 force-stop 冒充 20 轮 | 缺 sha/缺 boot/重复 boot/`mode!=reboot` → 该轮 INVALID 并计入 n；`mixed APK sha256`、`no apkSha256` 进 reasons；`G2` 仅在 `n≥20 && G1 green && p95≤5000` 时可能 PASS | 强（18 例 pytest + 20 轮实测走通） |
| F11（阈值可覆盖 / 旧 20 轮入口） | 调用方传 `requiredSamples/gateMillis` 放宽；单进程 20 轮发 PASS | `decide` 三参数签名（阈值只出常量）；legacy 入口 `@Ignore` + 函数体硬失败（去注解/方法过滤直调都炸） | 强（签名 + 物理阻断） |
| F12（`rebalance=null` 归 0 / 只算考试通道） | 预览把“未规划”显示成 0；总量漏 due-review | 显式 `RebalancePlanningState.NOT_PLANNED` + init `require` 互斥（NOT_PLANNED ⇒ granted/backlog 均 0）；`totalGranted = totalPickedCount` 且 `require(totalGranted == Σsubjects + rebalanceGranted)` | 强（类型 + 不变式） |

## 6. 遗留、阻塞与 UNVERIFIED

### 6.1 阻塞（外部或需主会话裁定）

1. **WS7 生产接线（BLOCKED）**：`commitAssistantTurnWithVisual` 生产零调用方；接 `visualEngine` 会翻转 JS 广告面（MVA 未过）；`Application/Root` 禁改。runbook：`ws7 §5.2`（装配字段 + commit 调用点同批落，`ENABLED` 单独一次提交）。
2. **T4 控制臂（BLOCKED）**：需回退 `VisualJsClientEngine.kt` 6 处写点并重建/重装。runbook：`ws4 §4.3`。
3. **生产签名 / Play App Signing（BLOCKED）**：`RELEASE_*` 4/4 UNSET；本轮 release APK 是一次性本机签名（DN 带 `NotForRelease`）。
4. **arm64 16KB 运行时（BLOCKED）**：无 arm64 设备/宿主；等待清单 `ws8 §2.4`。
5. **人工金标（BLOCKED）**：需 ≥2 名独立标注员 + 1 名裁定者；抽样计划已就绪（24 页）。
6. **KD-72 写锁（窗口关闭）**：两次隔离副本 `--write-locks` 均失败（配置面变体歧义）；等待清单 W1–W6（`ws8 §2.8`）。
7. **契约并入（BLOCKED）**：`docs/current` 仍 7 M；patch 只到 `git apply --check`。
8. **热区 `core/domain` 已被触碰**：需主会话追认或回滚（§5 S7）。
9. **计划项 34 与 F8 的互斥**：需二选一（§5 S4）。

### 6.2 非本批红 / 共享面

- `core:data/knowledge` 3 例词面（`ProductionLexicalLegExportTest`、`Stage1`、`Stage2`）：本批未改；他线提交 `a5a61a46` 声称按 v28 重钉（39+11 绿），**本记录员未复跑**。
- `p09`（`VisualJsClientFrameQueueDeviceInstrumentedTest`）与批次 40/41 同方法同报文，未定性。
- `BackupSelectionRestoreInstrumentedTest.closureFourArchive…` 设备红为测试装置缺陷（Room 懒开库），未修。
- 数十份 docs `M`、KB/dense 线 `??`：非本批产物，提交时排除。

### 6.3 UNVERIFIED（不得写通过）

- **任何 12 模块环后单测门**：未跑。命令 = §3.1 的 12 个 `testDebugUnitTest` 任务集合（见 `build/batch42-unit.log:1`）+ `--continue`，在 `a5a61a46` + 当前树重跑。
- **core:data 全模块环后结果**：XML 被覆盖，不可回读。
- **WS7 装配类 JVM/设备用例**（`TutorVisualPreviewAssemblyTest` / `…InstrumentedTest`）：零执行。
- **修复环之后的任何设备结论**：环（18:29 起）晚于全部设备跑；设备绿只绑 §4 的 APK。
- **isolation 15 例真实行为、因果、T4 排除**：三台设备均零执行。
- **WS6 mask=0 / mask=9 设备恢复语义**。
- **KD-76 两批独立（A6）与 C4**：未满足/未测。
- **KD-75 C2（模型解析文本）与 C4 字面升版**：产品缺口。
- **Keeper K1–K6**、retrace、AAB、冷构建确定性、arm64/16KB 运行时。
- **独立核对与对抗复核（P15/P16）**：无产物。
- **P0b 交叉复核里的 7 条冲突（C1/C2/C3/C5/C6 高）是否被裁定**：无裁定记录。

## 7. 提交清单建议（每块 ≤6 路径；排除热区/锁文件/tmp）

> **本记录员未做任何 git 写**；以下是建议块，提交前必须逐路径 `git status` / `git diff --cached --name-only` 与逐 hunk 核对。**不得提交**：14 个 `gradle.lockfile`、`feature/tutor/.../nul`、`.mimosa/`、`build/`（含 `build/ws8-batch42/`、`build/kd76-runs/`、`build/device-track-batch42/`）、`%TEMP%/`、`.agent_audit_crops*`、`.wf-manifest-stage*.txt`、他线 KB/dense 工具与测试、热区文件（见块末“热区，另裁”）。

| 块 | 路径（≤6） | 备注 |
|---|---|---|
| B1 | `tools/ci/isolation_pair_report.py`、`tools/ci/run_isolation_pair.sh`、`tools/ci/t4_control_marker.py`、`tools/ci/visual_js_isolation_methods.txt`、`tools/tests/test_isolation_pair_report.py`、`tools/tests/test_t4_control_marker.py` | WS1 F7/F8；`--expected-methods` 必填是破坏性变更 |
| B2 | `tools/ci/summarize_kd76.py`、`tools/ci/run_kd76_rounds.sh`、`tools/tests/test_summarize_kd76.py`、`app/src/{main,test,androidTest}/…/visualjs/{Kd76ReportVerdict.kt,Kd76ReportVerdictTest.kt,VisualJsConnectStageCalibrationInstrumentedTest.kt}` | WS1 F10/F11 + WS3 入口；app.test 有他线 hunk 需核 |
| B3 | `core/data/src/main/kotlin/.../study/ReviewCapacityPreviewReader.kt`、`core/data/src/test/kotlin/.../study/ReviewCapacityPreviewReaderTest.kt` | WS1 F12（2 文件） |
| B4 | `core/export/src/main/kotlin/.../MistakePdfExporter.kt`、`core/export/src/test/kotlin/.../MistakePdfExporterAigcJvmTest.kt`、`core/export/src/androidTest/kotlin/.../MistakePdfExporterAigcInstrumentedTest.kt` | WS2 主 hunk（+89）；`core/export` 其余 3 M 测试为他线 |
| B5 | `core/model/src/main/kotlin/.../pdf/` 7 文件（`PdfAigcContentPolicy/ErrorCode/Injector/Reader/Schema`、`PdfCrossReference`、`PdfSyntax`） | WS2 落位 B1'（纯搬移，需与 B6 的 14 个删除同批，`git diff -M` 识别 rename） |
| B6 | `core/model/src/test/kotlin/.../pdf/` 7 文件 + `core/data/src/main/kotlin/.../pdf/` 7 删除 + `core/data/src/test/kotlin/.../pdf/` 7 删除 | 同一搬移的另一半 |
| B7 | `app/src/androidTest/kotlin/.../pdf/PdfAigcDeviceRoundTripInstrumentedTest.kt`、`tools/ci/make_v4_pack_fixture.py`、`tools/backup_fixtures/v4/` 6 文件 | WS2 import 同步 + WS5 夹具；夹具含 `.sha256` ×3 与 golden |
| B8 | `core/data/src/test/kotlin/.../backup/V4PackFixtureTest.kt`、`core/data/src/androidTest/kotlin/.../backup/BackupV4PackRestoreInstrumentedTest.kt` | WS5 用例 |
| B9 | `core/data/src/main/kotlin/.../backup/SmbkSelectionPlan.kt`、`SmbkArchiveCodec.kt`、`BackupSnapshotTrimmer.kt`、`RestoreSoftReferenceRecheck.kt` | WS6 主码；逐 hunk（`SmbkArchiveCodec` 只有注释改动） |
| B10 | `core/data/src/main/kotlin/.../backup/AndroidBackupRepository.kt`、`core/data/src/test/kotlin/.../backup/SmbkSelectionPlanTest.kt`、`SmbkArchiveCodecTest.kt`、`core/data/src/androidTest/kotlin/.../backup/BackupSelectionRulingsInstrumentedTest.kt` | WS6；`AndroidBackupRepository` 为共享 M，需逐 hunk |
| B11 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt`、`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/BackupSelectionUiInstrumentedTest.kt` | WS6 UI 文案；均为共享 M |
| B12 | `core/data/src/main/kotlin/.../visual/TutorVisualPreviewAssembly.kt`、`core/data/src/test/kotlin/.../visual/TutorVisualPreviewAssemblyTest.kt`、`core/data/src/androidTest/kotlin/.../visual/TutorVisualPreviewAssemblyInstrumentedTest.kt` | WS7 三个新文件（默认关闭；用例零执行，提交≠可用） |
| B13 | `docs/research/2026-11-04-{pdf-export-injection,isolation-causality,kd76-20-rounds,v4-pack-fixture,lockfile-window,release-readiness}.md` | R1–R6 |
| B14 | `docs/research/2026-11-04-{batch-42-baseline,batch-42-inventory,6c2-rulings,ws8-release-prefight}.md` | 基线/盘点/裁定/WS8 |
| B15 | `docs/research/2026-11-04-{release-gates-draft,contract-v1.1-p0-applicable.patch,aud08-human-gold-sampling-plan}.md` | 发版门草案 + 契约 patch（未应用）+ 抽样计划 |
| B16 | `docs/research/evidence/2026-11-04-batch-42/00-index.md`、`ws2-kd75-closeout.md`、`ws3-kd76-20-rounds.md`、`ws4-isolation-causality.md`、`ws5-kd83-v4-fixture.md`、`ws7-preview-assembly.md` | 证据 1 |
| B17 | `docs/research/evidence/2026-11-04-batch-42/{ws9-contract-admission.md,ws10-device-full.md,device-track-batch42.md,step-aud08-gate-recompute-b42.md,research-crosscheck.md}`、`…/rulings/20261103T044703Z-10c61be3.json` | 证据 2（6 路径） |
| B18 | `docs/research/2026-11-04-batch-42-record.md` | 本条记录放最后 |
| B19 | `docs/known-defects.md`、`docs/REFACTOR-MASTER-LINE.md`、`docs/agent-first-refactor-decisions-2026-09-23.md`、`docs/research/2026-10-17-6c2-content-selection-design.md`、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | 台账/设计件/准入；**全部共享 M，逐 hunk** |
| B20 | `core/database/src/test/kotlin/.../KernelWave0SchemaContractTest.kt` | 第 4 红修复（71→73）；**热区外**，但与 core:database 契约测试同族 |
| — | **热区，另裁**：`core/domain/.../BackupSelection.kt`、`core/domain/src/test/.../BackupSelectionTest.kt` | 计划 §4 明禁；§5 S7 待追认，**不进本批提交** |
| — | 明确不入：14 `gradle.lockfile`、`nul`、`core/data/build.gradle.kts`（夹具 sync 任务归属待裁）、他线测试/主码（`ReviewReminderCoordinator*`、`SavedMistakeSheetBytesTest`、`VisualContentMeasuringTest`、`KnowledgeQuizTasks`、`TutorVisualContextFingerprintTest`、`TutorFigureProposalExecutionTest`、`StrictOfflineRelayoutDeviceVector` 等） | 逐路径排除 |

## 8. 回链

- 计划：`.zcode/plans/plan-2026-11-04-batch-42.md` §0/§1/§2/§3/§4/§5；批次 41 记录 §9.3（F7/F8/F10/F11/F12 首项输入）。
- 基线/盘点：`docs/research/2026-11-04-batch-42-baseline.md`、`docs/research/2026-11-04-batch-42-inventory.md`（90/74 缺陷、热区 9 M、剩余 73 项分派、批次 43 草案）。
- 证据索引：`docs/research/evidence/2026-11-04-batch-42/00-index.md`（编号映射 §0、产物身份 §2、仅本批可证 §4、缺口 §6、脱敏 §9、新发现 N1–N7）。
- 设备 E 文件：`ws2-kd75-closeout.md`、`ws3-kd76-20-rounds.md`、`ws4-isolation-causality.md`、`ws5-kd83-v4-fixture.md`、`ws7-preview-assembly.md`、`ws9-contract-admission.md`、`ws10-device-full.md`、`device-track-batch42.md`、`step-aud08-gate-recompute-b42.md`、`research-crosscheck.md`、`rulings/20261103T044703Z-10c61be3.json`。
- 研究件：`2026-11-04-{pdf-export-injection,isolation-causality,kd76-20-rounds,v4-pack-fixture,lockfile-window,release-readiness}.md`（R1–R6）+ `6c2-rulings.md`、`ws8-release-prefight.md`、`release-gates-draft.md`、`contract-v1.1-p0-applicable.patch`、`aud08-human-gold-sampling-plan.md`。
- 门禁原始日志（gitignored，不作关闭证据）：`build/batch42-unit.log`、`build/batch42-strong.log`、`build/batch42-strong-postfix.log`、`build/batch42-unit-postfix.log`、`build/batch42-pytest-postfix.log`；设备 raw `build/device-track-batch42/`、`build/kd76-runs/ws3-batch42-{20,r2}/`、`build/ws4-isolation-b42/`、`build/ws8-batch42/`、`build/ws9-batch42/`、`build/v4-repro/`。
- 台账：`docs/known-defects.md`（KD-75 `:2906`、KD-76 `:3000/:3002`、KD-83 `:3282/:3294`）、`docs/REFACTOR-MASTER-LINE.md:5-6`、`docs/agent-first-refactor-decisions-2026-09-23.md:3117,3808-3818`。
- 本文件不替代主会话裁定：**KD-75 open（C2/C4 未达）**；**KD-76 open（单批 n=20 有值，A6/C4 未满足）**；**KD-83 关闭（合成 v4 夹具口径）**；**isolation UNCONVERGED / 禁写 T4 排除**；**AUD-08 FAIL 维持（0.5714 < 0.9）**；**WS6 设备 1/4，mask=0/9 UNVERIFIED**；**WS7 生产接线 BLOCKED**；**发版就绪不成立**（生产签名/arm64/干净树三项缺）；**热区 `core/domain` 触碰待追认**。

## 9. 主会话收口（2026-11-04，本段为收口权威）

### 9.1 主会话实跑门禁（本轮，非转录）

| 门 | 命令 | 结果 |
|---|---|---|
| 单测门（回滚+修机器块后） | 12 模块 `testDebugUnitTest --continue`（`build/main-session-gate-batch42-unit3.log`） | **exit=0（BUILD SUCCESSFUL 1m47s）**；KB 3 例词面红随他线 `a5a61a46` 重钉后已消失 |
| 强门（逐任务 `--rerun`） | 四模块 + 两 flavor app androidTest 编译 + `:app:assembleLocalFirstDebug`（`build/main-session-gate-batch42-strong.log`） | **exit=0（43s，真实重编）** |
| 备份四方门 | `python -I tools/ci/check_backup_plan_sync.py` | **PASS**（重生成 §9.11 机器块后） |
| SmbkSelectionPlanTest | `:core:data:testDebugUnitTest --tests *SmbkSelectionPlanTest*` | **exit=0** |

### 9.2 越权事故与回滚（S7 的处置，推翻本批 WS6 全部实施）

- **事实**：批次 42 计划 WS6 擅自把 6C-2 的 mask=8「不提供」/mask=0「禁止」改为「允许」，并改了热区 `core/domain/BackupSelection.kt`、`SmbkSelectionPlan`/测试、两个 androidTest、`SmbkArchiveCodec`/`BackupSnapshotTrimmer`/`RestoreSoftReferenceRecheck`/`AndroidBackupRepository` 及设计件。
- **裁定**：**撤回**。批次 18（2026-10-24）的两项是**用户已确认的产品裁定**，本会话的一般授权不得推翻；`0/8` 仍为禁止组合，`ALLOWED_MASKS` 恢复 `9/13/15`。
- **执行**：7 个跟踪文件反向应用 diff 还原、删除未提交的 `BackupSelectionRulingsInstrumentedTest.kt`，事故记录见 `docs/research/2026-11-04-6c2-rulings.md`。WS6 items 48–55 全部记为**未交付/已回滚**。
- **连带修复**：设计件 §9.11 机器块在回滚后暴露陈旧（schema 72/hash `aa5e8e8e…`/93 表含 L2/缺 `PAPER_EDIT`/digest golden 旧值），已按代码重生成（73/92/hash `1d086050…`/FK 110/trim 84/digest golden `5c690755…`），四方门与单测均绿。

### 9.3 S4 裁定（isolation 同 APK vs 不同身份）

两层实验，不得混写：

1. **环境/方法可复现层**：同一 APK sha、低负载窗内 OFF/ON 只是同一构建的两次运行（不关打点），只用于判「红例是否随窗口漂移」；不得据此谈 T4。
2. **T4 因果层**：必须两臂构建身份可区分（打点/注册钩子被回退的构建），每臂记录 APK sha、安装校验与类清单；结论只能写成「在该控制构建上红集变化」，不得外推为“T4 是唯一原因”。
`run_isolation_pair.sh` 的同 COMPONENT 拒绝保持（它服务第 2 层）；第 1 层用同一套件重复运行加报告工具，不需要 runner 配对。此前“同 APK 配对”只适用于第 1 层。

### 9.4 本批提交（20 笔，已推送前核对暂存区）

工具修复（F7/F8、F10/F11）→ F12/schema 契约 → core:export `/AIGC` 接线 → PDF 包搬入 core:model（`git diff -M` 识别 rename）→ 设备 import 同步 + v4 夹具/生成器 → v4 用例 → 预览装配（默认关闭）+ 越权回滚记录 → 研究 R1–R6 → 基线/盘点/发版草案/契约 patch/金标抽样 → 证据 1/2 与 2/2 → 本记录 → 台账。

- **不提交**：回滚后的 6C-2 全部改动（已还原）、热区 `core/domain/*`（`TutorJudgedReview.kt`/`BackupRepository.kt` 为他线）、锁文件、`.mimosa`、tmp。
- 台账/doc 的 S10（KD-83 下挂 KD-75 段错位）未修，登记批次 43。

### 9.5 收口裁定

- **KD-75 维持 open**：产品链已接（AI 图判据 + fail-closed + 缓存代际），设备 5/5 + 10/10；但 C2（模型解析文本判据）未落、C4 字面升版未做。
- **KD-76 维持 open**：单批 n=20、p95=1889ms、20 个 boot_id 全不同；两批独立（A6）/C4 未满足，两批不可相加。
- **KD-83 关闭**（合成合法 v4 字节夹具口径；真实历史用户包不存在，边界已记）。
- **isolation UNCONVERGED**：三台设备 15 例全 `-4`，零执行；禁写“T4 排除”/“全绿”。
- **AUD-08 FAIL 维持**（0.5714 < 0.9），阈值零改动。
- **发版就绪不成立**：dirty 树 release、生产签名 4/4 UNSET、arm64 缺失。
- **批次 43 首项**：6C-2 若要重开须先取得用户明确确认；isolation 自证探针；KD-75 C2；KD-76 A6；S10/S11/S9 修；真实 Provider/签名/arm64 的 runbook 由用户执行。
