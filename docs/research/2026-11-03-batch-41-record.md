<!-- 体积自查：411 行 / 约 49 KiB；批次记录不设 R4 上限（同批次 40 记录口径），保持可读。本文件由批次 41 记录员落盘。 -->

# 批次 41 记录（2026-11-02/03）：批次 40 复核修复 + 计划剩余推进（84 项）

> **来源与边界**：本文件由 **批次 41 记录员**（工作流 P19）落盘，是本次 ask 的**唯一写入**。本轮**未运行 Gradle、未执行 adb/设备、未做 git 写**；实跑过的只有文件读取、`git rev-parse/status/log/show`、Python 静态解析、`python -I tools/ci/check_backup_plan_sync.py`（exit=0）、`python -I tools/split_golden/check_split_gate.py`（exit=1）与各类 `grep/wc`。
>
> **声明口径**：本文件凡写“完成/已落”只表示**载体在树 + 有可指认证据**；不等于本记录员复跑过 Gradle/设备。状态严格分三值：完成 / 部分 / 未执行（UNVERIFIED）。所有 JVM/设备执行数字都标明来源（工作流 P11/P12/P18 日志或 E 文件），**没有一条被写成“本轮记录员实跑通过”**。
>
> **TrackOutcome 来源**：工作流轨的 `TrackOutcome` 原文取自本 run 各子代理会话的最后一次 `submit_result`（`C:/Users/听云/.zcode/cli/rollout/model-io-sess_dwf-dwfrun-7bc8ab3b-5898-4709-b4b7-6e090eb40ab5-actor_{12..29}_1.jsonl`，逐字抽取、未改写）。**不是** workflow `report()` 的汇总文本；被 summary 省略的反例另见 §5。

- 计划：`.zcode/plans/plan-2026-11-03-batch-41.md`
- 基线：`docs/research/2026-11-03-batch-41-baseline.md`
- 证据索引：`docs/research/evidence/2026-11-03-batch-41/00-index.md`
- 输入：批次 40 记录 `docs/research/2026-11-02-batch-40-record.md` §8/§9
- 执行设备（设备轨）：emulator-5584（AVD `kb_test_device`，API 34，x86_64，4 vCPU，page-size 4096）
- 构建窗：2026-11-03T00:34–00:46（localFirst 四包）；设备窗：00:45–01:20；证据窗：01:25–01:54；修复环/环后门：02:45–03:16

## 0. 口径与基线

### 0.1 84 项口径（计划自述 55，实列 62——按 55 行映射，不丢编号）

计划 §2 标题写 **55 项**，但正文编号实际为 **1..62**（本记录员 `python -I` 正则解析：`count 62 first [1,2,3] last [60,61,62] missing []`），9+62+20 = **91**，与计划自述的 84 不符。该矛盾已由独立核对发现（§5 F1）登记，**计划未改**。

为同时满足“84 项逐项对账”和“不丢编号项”，本记录采用如下映射：

- 研究 **9 行**（R1–R9），与计划 §1 一一对应。
- 实施 **55 行**：WS1 10 + WS2 7 + WS3 7 + WS4 7 + **WS5 3**（32+33、34+35、36+37）+ WS6 5 + WS7 5 + WS8 4 + **WS9 3**（52+53、54+55、56+57）+ **WS10 4**（58、59、60、61+62）= **55**。合并的 7 行在表内以“32+33”等标注，子项细节在 §3/§4 展开。
- 流程 **20 行**（PR1–PR20），定义见 §1.3；工作流阶段号另列，避免与证据目录的 E-41-Pxx 混淆（§5 F16）。

### 0.2 基线事实（本记录员本轮实读/实跑）

| 项 | 值 | 证据 |
|---|---|---|
| HEAD | `62522930bc8bae51be1523c954f39449e054b6af` | 本轮 `git rev-parse HEAD`；与基线 §1.1 同值 |
| origin/main | 同值（基线 `git ls-remote` 实测） | 基线 §1.1 |
| 工作树 | **600 条**（记录时）；暂存 0；门禁时点 584（P11/P12）/ 599（P18）/ 600（记录时） | 本轮 `git status --porcelain \| wc -l`；`git diff --cached --name-only` = 0；P11/P18 日志 header |
| DB schema | **73**；`73.json` = **92 entities / 2 views / identityHash `1d086050204a0da1ed6b6e3299dd9733`** | `core/database/.../StudyDatabase.kt:142`；本轮 `python -I` 读 `73.json` |
| schema JSON 计数 | `core/database/schemas` 下 **75** 个 `*.json` = 73 个版本化 + 2 个 `.mimosa/hook-state` 工具状态 | 本轮 `find ... -name '*.json' \| wc -l`；E-41-P12 §5；`git check-ignore` 命中 `.gitignore:29` |
| 请求 schema | 19 | `core/model/.../ModelTasks.kt:566-567`；基线 §1.3 |
| 备份清单 | 73 / 92 / trim 84 / closure 5 / `smbk-selection-v5` | `SmbkSelectionPlan.kt:54,64,70,215,224`；四方门逐项 OK（§3.4） |
| 热区 | **5 M + 9 ??**（core/domain 1、core/export 3 test、core/ui 1；5 个 feature 锁、`nul`、3 个 core 锁）——均为批次 40 既有他线面，**非本批产物** | 本轮 `git status --porcelain -- feature/ core/ui/ core/export/ core/domain/ settings.gradle.kts` |
| 本批落点 | 35 Kotlin（13 新 / 22 改）、2 golden、tools/ci 9+1 patch、tools/tests 7、研究 12、证据 7、文档 5；**热区 0** | 集成员 TrackOutcome（actor 21）；本轮 `git status` 复核 |
| 记录文件 | 落盘前**不存在**（本文件即交付） | 本轮 `test -f ...record.md` → no |

### 0.3 时间线与“文档先于设备”的硬事实

1. **11-02 23:39**：WS10 文档/台账落盘（`docs/REFACTOR-MASTER-LINE.md`、决策台账、`known-defects.md`）。其内容写“WS5 无批次 41 证据文件”“KD-75 无设备执行”。
2. **11-03 00:34–00:46**：设备轨重建四包（app `5793300c…`、app test `0e906482…`、data test `82a6f21e…`、db test `6f55edbf…`），源快照 1287 文件、diff=0。
3. **00:45–01:20**：schema 73 七套件、KD-75 10/10、KD-76 单轮、isolation 三跑（全 `-4`）、签名扫描。
4. **01:25–01:54**：E-41-D01…D06、E-41-P11…P13、00-index 落盘。
5. **02:45–03:16**：修复环改 `ExportedSchemaContractTest`（排除隐藏目录 + 回归例）与 `ReviewCapacityPreviewReader` KDoc；P18 环后强门与定向单测。
6. **03:30**：KB 线又跑了一次 `core:data`，覆盖 `build/test-results` 下 XML（只剩 `GoldenRetrievalJvmTest`），因此 **WS7 的逐类 XML 已不可回读**。

> 结论：**WS10 文档的“无设备执行”是 23:39 时点值，已被 00:45 后的 D02/D03 取代**；本记录不按文档旧句裁定设备面，也不改那三份文档（§5 F5）。

### 0.4 源漂移（设备绿的可复现边界）

- D01 源快照：1287 文件，`src-sha-before/after` = `297b2070…`；修复后 `fc93c37b…`（仅 PDF 测试 1 文件 / 4 行）。
- 01:16–01:22 KB 线改 `core/database/src/main` + `core/data/src`；`src-sha-now` 与 after-fix 差 21 行、**12 个不同文件**（独立核对复算；00-index 写“21 文件”不准确，§5 F19）。
- **当前树不能逐字节复现本轮设备绿**；本文件所有设备结论只绑定 §4 的 E 文件与 APK sha。

## 1. 84 项逐项对账

> 状态词：**完成**=载体在树 + 有证据（记录员实读/实跑，或 P11/P12/P18/E 文件可指认）；**部分**=主链在树但有明确缺口；**未执行**=无载体或无执行证据；**维持**=缺陷/口径不变。

### 1.1 研究线 9 行

| # | 研究题 / 落盘 | 关键裁定（TrackOutcome 原文摘录） | 状态 | 未验证 |
|---|---|---|---|---|
| R1 | FSRS 多科每日上限 → `2026-11-03-fsrs-daily-cap-allocation.md` | “总量落在‘根=全天剩余预算’这一层……科只是子配额，按 (距考天数↑, 缺口↓, subjectId↑) 贪心取 cap_i=min(demand_i,剩余)……再平衡必须先在入参里排除考试已取 id” | 完成 | Gradle 未跑；2.25× 为读码推定 |
| R2 | 备份包格式兼容 → `2026-11-03-backup-format-compat.md` | “接受 closure 4（N-1 窗口 {4,5}），但只在‘不可变规则表’下接受……被删表必须‘声明 0 且实测 0/缺失’才允许丢弃，否则整包拒绝” | 完成 | 未构造真实 closure-4 包；单一官方来源已标 |
| R3 | Room 重基/孤儿 schema → `2026-11-03-room-version-rebase.md` | “保留 72.json 与 72→73，但在该迁移里显式 DROP 分叉表……下一次形状变化走 74” | 完成 | 设备生产库未探测；DROP 安全性未抽样 |
| R4 | 16KB 页测试 → `2026-11-03-16kb-page-testing.md` | “本机 16KB 运行时验证走 ps16k_x86_64……arm64 由真机或 arm64 宿主承担” | 完成 | 文档时点写“未启动”，WS9 后已启动——时点差见 §5 F15 |
| R5 | 低负载判定 → `2026-11-03-low-load-gating.md` | “低负载窗必须由宿主+adb shell 侧判定……instrumentation 内因 SELinux 读不到 /proc/stat……只能做 fail-closed 自证探针” | 完成 | 5 min 正式门当时未跑；后续 WS4/D05 已跑 |
| R6 | PDF 读者容忍 → `2026-11-03-pdf-reader-tolerance.md` | “四读者对‘能打开’极宽容、对‘元数据落在期望对象上’极不宽容……设备最小矩阵 M1/M2/M3” | 完成 | 12 夹具设备行为未跑；Acrobat 无一手证据 |
| R7 | 错因→话术映射 → `2026-11-03-error-feedback-mapping.md` | 四类 × 反馈结构 / 练习与调度策略；话术模板为设计提案 | 完成 | 本项目错因标注 0 条；无 A/B/结局数据 |
| R8 | Keeper/L8 可行性 → `2026-11-03-keeper-run-feasibility.md` | “接 usefulness/keeper 0.19.0……必须显式 automaticR8RepoManagement=false；testProguardFiles 单独用不能验证 K1–K6” | 完成 | K2/K6 未跑；签名 4 变量 UNSET |
| R9 | instrumentation PDF 校验 → `2026-11-03-instrumentation-pdf-verification.md` | “PdfDocument→真实文件→ParcelFileDescriptor→PdfRenderer……独立读者用 host pypdf；PdfRenderer 只证明平台 PDFium 接受” | 完成 | Skia 产物形态与 PDFium 接受度由 D03 闭环；API 35 类不可用 |

### 1.2 实施 55 行

| # | 交付物 | 落点 / 证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| 1 | Scheduler 多科合并总量 ≤ 日容量 | `ReviewCapacityPolicy.allocateSubjectCaps`；`ReviewCapacityScheduler.kt:213-221` init `require`；`ReviewCapacitySchedulerTest` 8/8（P18 focus） | **完成（前提已变）** | 基线 D1 证明 HEAD 已不是“每科各拿整份”；本批真实增量是“紧迫度配额 + 去重前置 + 单次爬坡” |
| 2 | ExamQuota + MissedDay 合并去重总量 | `ExamQuotaPlanner.kt:122` `picked<=quota`；`MissedDayRebalancePolicy.kt:33,48` 去重前置；用例 8+7（P18 focus） | 完成 | — |
| 3 | Preview 逐科与总量 | `ReviewCapacityPreview` 增 subjects/totalGranted/daily+max 及 init 断言；用例 6/6 | **部分** | 对抗复核 F12：`rebalanceBacklogCount` 在 rebalance=null 时归 0；`totalGranted` 只算考试通道；未修 |
| 4 | `ErrorCauseGate` 拒 `SELF_REPORT` | 生产码在 HEAD `ErrorCauseGate.kt:43`（`git show HEAD` 实测）；本批只加硬规则用例，`ErrorCauseGateTest` 9/9 | **完成（回归锁）** | 不可能“先红后绿”——生产码已在批次 40 提交 `e89476ea` |
| 5 | closure 4→5 兼容读侧 | `ClosureRuleTables.kt`（新）、`SmbkArchiveCodec.kt`（+192/−?）、`AndroidBackupRepository.kt`；`SmbkArchiveCodecTest` 35/35 | **部分** | v4 夹具由同套规则自证；设备 `BackupSelectionRestoreInstrumentedTest` 未跑（§5 F3） |
| 6 | 旧包恢复用例（成功+新表空+行数对账） | 同 5；`SmbkArchiveCodecTest.kt:1486/1528/1570` | **部分** | 设备恢复级用例未执行；只 JVM |
| 7 | Trimmer identityHash fail-closed 用例 | `BackupSnapshotTrimmerInstrumentedTest.kt:335`；D02 `OK (10 tests)`（含新增 1 例） | 完成 | 绑定 data APK `82a6f21e…` |
| 8 | `check_backup_plan_sync.py` 四方一致性 | `tools/ci/check_backup_plan_sync.py`；本记录员实跑 **exit=0**；`test_backup_plan_sync` 4 passed | 完成 | CI 未挂载（只 patch） |
| 9 | CI 挂载说明/本地用法 | `tools/ci/check_backup_plan_sync.ci.patch`（14 行）；`git apply --check` exit=0 | 完成（草案） | 未改 `.github`；CI 行为未验证 |
| 10 | 记录 §8 发现 14/15/16 回写 | 本文件 §5 F2/F5；`ws6-error-cause-chain.md` §6 | 完成（本文件） | 14 已由生产码闭环；15 前提已变；16 读侧已兼容 |
| 11 | PDF 公开 API 往返（表型） | `app/src/androidTest/.../pdf/PdfAigcDeviceRoundTripInstrumentedTest.kt`（10 @Test）；D03 `OK (6)+4×OK (1)` | 完成 | 绑定 app test `0e906482…` |
| 12 | 流型 xref | 同 11 | 完成 | 同上 |
| 13 | 幂等二次注入 | 同 11；`classic-table-twice` 与 labeled 完整 sha 相同 | 完成 | 同上 |
| 14 | 畸形 PDF fail-closed 四类 | 同 11；四方法各自独立进程 | **部分** | typed error 真覆盖；“不落半成品”断言空转（§5 F13） |
| 15 | 内容政策打标判据 | 同 11；模型文本强制标、纯用户不标 | 完成 | 同上 |
| 16 | 设备实跑证据（OK + APK sha + 源快照） | E-41-D01/D03；app `5793300c…`、test `0e906482…` | 完成 | 只绑本机/本 APK；首红原始 log 已丢失 |
| 17 | `core/export` 产品接缝 patch 草案 | `ws2-core-export-seam-patch.md`（未应用；409 行超 R4 400 行） | 完成（草案） | `core/export` 仍零 `PdfAigc*` 引用；未编译 |
| 18 | 单轮 instrumentation 入口 | `VisualJsConnectStageCalibrationInstrumentedTest.kt:280-316,1046`；D04 单轮 | 完成 | 只跑 1 轮 |
| 19 | 20 次独立 `am instrument` 汇总脚本 | `tools/ci/run_kd76_rounds.sh`；`bash -n` 通过 | 完成（工具） | 20 轮未跑 |
| 20 | 报表层 G1 红/样本不足不输出 PASS | `Kd76ReportVerdict.kt`；`Kd76ReportVerdictTest` 7 例 | **部分** | app 模块 JVM 用例本批未执行；旧单进程 20 轮入口仍可发 PASS（§5 F11） |
| 21 | M=逐轮 p95 计算脚本 | `tools/ci/summarize_kd76.py`；8 例 pytest 绿 | 完成（工具） | 无真实 20 轮数据；缺 per-round sha 假绿（§5 F10） |
| 22 | 29.5s 链分段脚本 | `tools/ci/kd76_chain_segments.py`；6 例 pytest 绿；和=29500 | 完成（工具） | 真实逐轮 XML 未跑 |
| 23 | 设备 20 轮 | — | **未执行** | UNVERIFIED；无 M/n/G1/G2 主口径 |
| 24 | KD-76 台账回写（M/n/G1/G2） | `known-defects.md` KD-76 段 | **部分** | 只写“无 20 轮/M 值”，未回填单轮 n=1/20 G2 NOT EVALUABLE（§5 F9） |
| 25 | 低负载前门程序化 | `tools/ci/lowload_gate.py`（13 例）；D05 快照/标定/门 PASS | 完成 | instrumentation 自证 3/3 UNGATED（§4.4） |
| 26 | OFF/ON 配对轮 | `run_isolation_pair.sh` + `isolation_pair_report.py`（15 例）；WS4 6 run | **部分** | 判词 UNCONVERGED；两轮隔离证据互斥（§2.2-1） |
| 27 | T4 控制轮可核标记 | `t4_control_marker.py`（4 例）；emit/verify exit=0 | **部分** | 生产侧控制轮未做；verify 不与已安装 APK 对拍（§5 F8） |
| 28 | 红集判定规则 | `isolation_pair_report.py`；只在低负载窗内比较、`-4/UNKNOWN` 不算通过 | **部分** | 缺方法假绿（§5 F7，高） |
| 29 | 设备实跑（15 例单臂 + 配对） | WS4：6 run×15/15 PASS（app `665cd5e0…`）；D05：3 run 全 `-4`（app `5793300c…`） | **部分（未收敛）** | 两套 APK、两套结论；均不能记“通过” |
| 30 | 证据落盘 | `ws4-isolation.md`、`step05-isolation-lowload.md` | 完成 | 两份需并读 |
| 31 | 结论口径（禁“T4 排除”） | WS4 §7、D05 §4-5 均明确禁止 | 完成 | 无“T4 排除/通过”字样 |
| 32+33 | 重建 APK + 迁移矩阵 1→73 | D01（四包 exit=0、4×install Success）；D02 matrix `OK (16 tests)` 221.36 s | 完成 | 绑定 §4.1 sha |
| 34+35 | 备份/rowCounts/trimmer/c4/drill | D02 backup 34/34、rowcounts 2/2、trimmer 10/10、c4 8/8、drill 4+2 | 完成 | drill 2 例为两阶段，不计单次 skip=0 |
| 36+37 | 错因账本设备 1 例 + 崩溃签名九类 | D02 errorcause 3/3；D06 17 份 logcat 九类全 0 | 完成（带覆盖边界） | worker 账本全 0 是“未执行”而非“健康” |
| 38 | extractor→gate→retractor 组合 | `ErrorCauseChainTest` 6/6（P18 focus） | 完成 | — |
| 39 | 设备 DAO 候选/被拒/ACTIVE 唯一/撤销 | `ErrorCauseLedgerInstrumentedTest.kt:155`；D02 3/3 | 完成 | 绑定 db APK `6f55edbf…` |
| 40 | 撤销不留半行（事务） | 同文件 `:268` 触发器注入 | 完成 | 设备随 3/3 执行；若 `RAISE(ABORT)` 行为不同需改注入点（未发生） |
| 41 | 生产装配接缝 | `ErrorCauseBookingService.kt`（唯一入口、默认门关闭、C1–C5） | 完成 | 未接 app/feature（热区） |
| 42 | 文档回写 | `ws6-error-cause-chain.md` + 本文件 | 完成 | — |
| 43 | adapter 请求/响应 + schema 19 拒绝 | `PaperEditAdapterTest` +2 例；`ModelTasks.kt:489-491` 守卫 | 完成（间接） | core:data 全模块 1648/3（3 红全 KB）→ 该类无失败；逐类 XML 已被 03:30 覆盖 |
| 44 | gateway 失败分类 + 预算/ledger | `OpenAiCompatibleModelGatewayTest` +2；`PaperEditLedgerInstrumentedTest`（1 @Test） | **部分** | JVM 间接通过；设备 ledger 未执行 |
| 45 | 提示词版本 golden | 两份 golden + `PaperEditPromptGoldenTest`（3 例）；Python 复算 MATCH | 完成 | JVM 未逐类回读 |
| 46 | 装配接缝（接口 + 判据） | `PaperEditModelPort`（core:model）+ `RepositoryPaperEditModelPort`；9 例 | 完成 | 零生产接线（热区） |
| 47 | 台账 | 本文件 + WS7 TrackOutcome | 完成（载体） | `feature/tutor` 第二份提示词仍欠账 |
| 48 | 门值复算（FAIL 维持） | 本记录员实跑 `check_split_gate.py` **exit=1**：① 4/7=0.5714、③ 0/16 | 完成 | 阈值零改动 |
| 49 | 人工金标协议 | `2026-11-03-aud08-human-gold-protocol.md` | 完成（协议） | 协议未执行；无 G3/G4/G5 |
| 50 | 结果面向禁令自查脚本 | `check_eval_label_leakage.py` + 11 例；扫描 24 脚本、error 0/warning 3 | 完成 | 静态 AST，不覆盖动态路径 |
| 51 | 逐页裁定统计与文档 | `GATE.md` §3.1 + `step-aud08-*`；7 页裁定 | 完成 | 7 页不可外推 |
| 52+53 | 16KB ps16k 运行 + arm64 可用性 | WS9：ps16k_x86_64 boot、PAGE_SIZE=16384、install Success、旧 test APK `OK (3 tests)`；arm64 `FATAL` 拒载 | **部分** | APK `735e42ab…` 早于 HEAD，不记设备绿；arm64 本机不可用 |
| 54+55 | KD-72 锁窗口 + Keeper 实跑尝试 | 锁 14 个未跟踪、严格模拟 36 错；Keeper 静态门 PASS、运行门 UNSET | **部分** | 窗口仍关闭；K1–K6 未跑；settings 未改 |
| 56+57 | CI 红诊断 + 依赖钉版门 | CI run 41402632275 failure，HEAD 缺 `is PaperEditInput`；`check_dependency_pins.py` exit=0 | **部分** | 修复只在工作树且含热区；本批未落 CI 证据文件 |
| 58 | 契约 V1.1 P0 草案（C-1/C-6/C-10） | `2026-11-03-contract-v1.1-p0-seams.md` | 完成（草案） | `docs/current` 7 M，未并入 |
| 59 | 准入清单刷新 | 附录 N 69 行：✅20/◐34/✗10/?3/⛔1/§1，0 项转通过 | 完成 | 运行时结论多为转录 |
| 60 | MASTER-LINE 批次 41 行 | `docs/REFACTOR-MASTER-LINE.md:4-5` | **部分** | 内容与后期 D02/D03 矛盾（§5 F5），需回填 |
| 61+62 | 决策台账 + KD 台账更新 | `docs/agent-first-refactor-decisions-2026-09-23.md:3766`；`known-defects.md` KD-75/76/80/83 | **部分** | KD-75/76 旧句未随设备证据回填；KD-83 新开 |

### 1.3 流程 20 行

> 工作流阶段号：`P0` 基线、`P0b` 研究复核、`P10` 集成、`P11` 单测门、`P12` 强门、`P13` 设备、`P14` 证据、`P15/P16` 复核、`P17` 修复、`P18` 环后强门、`P19` 记录。**§3 的 P11/P12/P18 即这套阶段号**，不是证据目录的 E-41-Pxx（§5 F16）。

| # | 流程项 | 落点/证据 | 状态 | 未验证 / 备注 |
|---|---|---|---|---|
| PR1 | 基线核对（P0） | `2026-11-03-batch-41-baseline.md` §1–§8 | 完成 | 时点值；D1/D2 已改判 |
| PR2 | 研究交叉复核（P0b） | `research-crosscheck.md` | 完成 | 单来源按待验证 |
| PR3 | 集成（P10） | 集成员 TrackOutcome；35 Kotlin 包名/283 import/61 pytest/四方门 | 完成 | 零 Gradle |
| PR4 | 单测门（P11） | `build/batch41-unit.log`：3924 tests / **6 failed**；exit=1 | **FAIL** | 3 红=`.mimosa` 环境产物，3 红=KB；见 §3.1 |
| PR5 | 强门（P12） | `build/batch41-strong.log`：第 5 次 exit=0；前 4 次宿主工具链 | 完成（有强度边界） | androidTest 编译 UP-TO-DATE；无 APK sha 绑定 |
| PR6 | 设备轨（P13） | E-41-D01…D06 | 部分 | KD-76 单轮、isolation 未通过 |
| PR7 | 证据（P14） | `00-index.md` + 16 个 `.md` + rulings | 完成 | 证据目录预算已满（16/16） |
| PR8 | 独立核对（P15） | 独立核对 TrackOutcome：18 findings | 完成 | 以日志/源码为主 |
| PR9 | 对抗复核（P16） | 对抗复核 TrackOutcome：11 findings | 完成 | 实跑假绿探针 |
| PR10 | 修复环（P17） | 修 `ExportedSchemaContractTest` + `ReviewCapacityPreviewReader` KDoc | **部分** | 复核 findings 未传入 ask（§5 F20）；2 个高项未修 |
| PR11 | 环后强门（P18） | `batch41-strong-postfix*.log` 4 次 exit=0；定向单测 db 236/0、focus 110/0 | 完成（有并发瑕疵） | 全模块仍 3 KB 红；他线并发（§3.3） |
| PR12 | 记录（P19） | 本文件 | 完成 | — |
| PR13 | WS1 脚本子件 | 四方门本记录员复跑 **exit=0**；pypdf 7/7（E-41-P13） | 完成 | CI 未挂 |
| PR14 | WS2 设备证据子件 | E-41-D03 + dependencies | 完成 | 首红 log 丢失 |
| PR15 | WS3 汇总报表子件 | `summarize_kd76`/`kd76_chain_segments` 14 例；D04 单轮 | 部分 | 无 20 轮主口径 |
| PR16 | WS4 判定规则子件 | `isolation_pair_report` 15 例；WS4 UNCONVERGED | 部分 | 缺方法假绿未修 |
| PR17 | WS5 崩溃签名子件 | E-41-D06：17 份 logcat 九类 0 | 完成（覆盖受限） | worker 账本无证据 |
| PR18 | WS6 事务断言子件 | `ErrorCauseLedgerInstrumentedTest` 3/3（D02） | 完成 | 触发器行为随设备套件成立 |
| PR19 | WS7 失败分类子件 | `OpenAiCompatibleModelGatewayTest` +2；core:data 全模块 3 红全 KB | 部分 | ledger 设备用例未跑 |
| PR20 | WS8 禁令脚本子件 | `check_eval_label_leakage.py` 11 例；扫描 24 脚本 | 完成 | 静态 AST |

## 2. 各轨结果（TrackOutcome 原文事实）

### 2.1 轨状态总表（原文 `status` 字段）

| 轨 | TrackOutcome `track` / `status` | 关键原文事实（节选，未改写） |
|---|---|---|
| WS1 | “WS1 缺陷修复” / **部分** | “WS1 八项全部落码/用例；按纪律未运行 Gradle，故 Kotlin 编译与 androidTest 均为 UNVERIFIED。” |
| WS2 | “WS2” / **部分** | “新增 …PdfAigcDeviceRoundTripInstrumentedTest.kt，10 个…用例……core/export 接缝 diff 草案……未应用。” |
| WS3 | “WS3 KD-76” / **部分** | “单轮 instrumentation 入口……一次进程只跑一轮……设备 20 轮未执行……未用单进程 20 轮冒充。” |
| WS4 | “WS4 isolation …” / **部分** | “6 个 run 全部 15/15 PASS、0 skip……但 5/6 个 post-gate INVALID……机器判词 **UNCONVERGED**……不得写 T4 因果或 T4 排除。” |
| WS6 | “WS6 错因端到端” / **部分** | “五项载体全部在树……JVM 单测、androidTest 编译、设备执行均 UNVERIFIED，需门禁阶段单独跑。” |
| WS7 | “WS7 6B-3 端到端” / **部分** | “43–47 逐项落地……未运行 Gradle……门禁结论一律 UNVERIFIED。” |
| WS8 | “WS8 AUD-08” / **完成** | “48：……FAIL 维持（① 4/7=0.5714 < 0.9，③ 0/16=0.0000 ≤ 0.05），阈值/口径/分母零改动。” |
| WS9 | “WS9 环境/构建” / **部分** | “16KB……运行时断言成立……不能记为设备绿；arm64……FATAL……拒载。” |
| WS10 | “WS10 文档/台账” / **完成** | “五项全部落盘……KD-75 追加……在树但无设备 E-file” |
| 集成 | “集成” / **完成** | “本批 78 个文件……热区 0 个……35 个 Kotlin 文件包名与路径 100% 一致……283 条 import……unresolved=0。” |
| 设备轨 | “设备轨” / **部分** | “schema 73 全绿……KD-75 首次实跑红……改为 targetContext.filesDir……10/10 绿……isolation……15 例全部 -4。” |
| 证据轨 | 非 TrackOutcome（字符串） | “单测门是 6 红，不是 3 红……强门的‘androidTest 编译通过’是 UP-TO-DATE 假象……没有可绑定的 sha。” |
| 独立核对 | ReviewOutcome（18 findings） | “存在 8 个中等问题……未发现高严重度的假绿。” |
| 对抗复核 | ReviewOutcome（11 findings） | “调度器的总量上限与 closure 4 兼容读侧本身站得住……证据工具存在可复现的假绿路径。” |
| 修复环 | “修复环（P17）” / **部分** | “本轮可复现的批次内门禁红只有单测门 core:database 的 ExportedSchemaContractTest 3 例……修复……4/4 绿。” |

### 2.2 必须并读的三处轨间冲突

1. **isolation 两套结论**：WS4 E 文件记“6 run×15/15 PASS（app `665cd5e0…`、test `3d621283…`），5/6 post-gate INVALID → UNCONVERGED”；设备轨 D05 记“3 次 am instrument 全 `-4`，code0=0（app `5793300c…`、test `0e906482…`）”。两者 APK 不同、时间不同（WS4 为 11-02 19:00–21:05；D05 为 11-03 01:03–01:19），**不能合并成“15 例已执行且通过”**。批次级裁定：**未收敛，未通过，不得写 T4 排除**。
2. **WS10 文档 vs 设备证据**：MASTER-LINE/决策台账/KD-75 写“无设备执行/WS5 无证据”，但 D02/D03 已是 PASS。原因是文档 23:39 早于设备 00:50（§0.3）。**以 E 文件为准，文档需回填**（§5 F5）。
3. **WS7 “未跑” vs 全模块 1648/3**：WS7 TrackOutcome 写“门禁一律 UNVERIFIED”；P18 的 core:data 全模块跑出 1648 tests / 3 failed（3 红全 KB），说明 WS7 的 JVM 用例实际被执行且无失败，但**逐类 XML 已被 03:30 KB 跑覆盖**，只能作间接证据。

## 3. 门禁

> **编号澄清**：本节 P11=单测门、P12=强门、P18=环后强门（工作流阶段号）。证据目录把环后强门命名为 `E-41-P11`、单测门命名为 `E-41-P12`，与本套编号**冲突**（§5 F16）；引用时一律带上下文。

### 3.1 P11 单测门（`build/batch41-unit.log`，00:20）

| 项 | 值 |
|---|---|
| 命令 | 12 模块 `testDebugUnitTest --continue`（`core:markdown` 为 NO-SOURCE） |
| 结果 | **3924 tests / 6 failed / 0 skipped；exit=1**（`BUILD FAILED in 2m 10s`） |
| 红 1–3 | `core:database/ExportedSchemaContractTest`：`.mimosa/hook-state/*.json` 被 `walkTopDown()` 当 schema 读 |
| 红 4–6 | `core:data/knowledge`：`ProductionLexicalLegExportTest` MRR 0.6178 vs 0.6130；`Stage1LexicalLabTest` 0.1593 vs 0.1607；`Stage2LexicalScoresExportTest` 命中 101 vs 100 |
| 归因 | 3 红=工作区工具产物（非产品码）；3 红=KB 他线；**产品码面 0 新红，但门本身是红的**——不得写“单测门通过” |
| 修复 | P17 改测试排除隐藏目录 + 回归例；P18 `core:database` 236/0（exit=0） |

> 批次 40 的“仅 3 例红”是因为 `:core:database:testDebugUnitTest` 当时 UP-TO-DATE 未执行；本次强制重跑才首次暴露 schema 3 红（E-41-P12 §3）。

### 3.2 P12 强门（`build/batch41-strong.log`，00:31–00:36）

| 项 | 值 |
|---|---|
| 任务集 | 四模块 androidTest 编译 + 两 flavor app androidTest 编译 + `:app:assembleLocalFirstDebug --continue` |
| exit | 第 1–4 次 = **1**（PowerShell 5.1 三元、ACL 守卫、npm `cmd` 遮蔽、`GRADLE_OPTS` 未加引号——均为宿主工具链/调用参数）；第 5 次 = **0** |
| 结果 | `BUILD SUCCESSFUL in 21s`；289 tasks：2 executed / 287 up-to-date；`e:` 0 条 |
| 强度边界 | 6 个 androidTest Kotlin 编译任务**全部 UP-TO-DATE / NO-SOURCE**，本轮没有一次真实重编；该门 APK 被 00:46 覆盖，**无可绑定 sha** |

### 3.3 P18 环后强门 + 定向单测（03:00–03:16）

| 运行 | 日志 | exit | 结果 |
|---|---|---|---|
| 强门 attempt1（03:00:26） | `batch41-strong-postfix.log` | **0** | 289 tasks / 8 executed；app 两 flavor androidTest 编译真实执行 |
| 强门 attempt2（03:13:11） | `batch41-strong-postfix-attempt2.log` | **0** | 289 / 3；编译 UP-TO-DATE |
| 强门 attempt3（03:14:17，全局 `--rerun`） | 追加于主日志 | **0** | 未强制 androidTest 编译 |
| 逐任务 `--rerun`（03:16:06） | `batch41-strong-postfix-forced-compile.log` | **0** | `core:data`/`core:database`/app 两 flavor androidTest Kotlin **真实重编**；`:core:export` 仍 UP-TO-DATE、`:core:ui` NO-SOURCE |
| 定向单测（core:data + core:database） | `batch41-unit-postfix.log` | **1** | `core:data` 1648 / 3 failed（全 KB）；`core:database` UP-TO-DATE |
| `core:database --rerun` | `batch41-unit-postfix-db-rerun.log` | **0** | 236 / 0；`ExportedSchemaContractTest` 4/4 |
| 修复相关类定向 | `batch41-unit-postfix-focus.log` | **0** | 110 / 0（容量 48、ErrorCause 15、SmbkArchive/SmbkSelection 47） |
| KB 3 红取数 | `batch41-unit-postfix-kb-observed.log` | **1** | 3 / 3（原样复现） |

- **并发瑕疵（如实）**：P18 窗口内他线在同一工作树多次跑 Gradle（03:00:17、03:01:03、03:04:24、03:05:18、03:08:13、03:13:35），且改 `TutorToolContextQueryTextTest.kt`、`RoomKnowledgeBaseStore.kt`；“门禁阶段单独运行且无并发”**未被满足**。exit=0 只证明当前输入下任务图成功，**不是干净窗口产物**。
- **APK**：`b7bd6d10…`（183,649,185 B，03:13:40）；与源快照不同时点（他线 03:07 仍在写），**不构成设备绿绑定**。
- **未跑**：12 模块完整单测门（修复后）、冷构建、`:core:export`/`:core:ui` 强制重编、任何设备套件。

### 3.4 本记录员亲手实跑的两道门（非 Gradle）

| 门 | 命令 | 结果 |
|---|---|---|
| 备份四方一致性 | `python -I tools/ci/check_backup_plan_sync.py` | **exit=0**；closure 5 / schema 73 / hash `1d086050…` / 92 表 / trim 84 / v4 91 键全 OK；末行 `PASS 四方一致（code / §9.11 / golden / 73.json）` |
| AUD-08 切分门 | `python -I tools/split_golden/check_split_gate.py` | **exit=1**；① 4/7=0.5714（<0.9）；③ 0/16=0.0000（≤0.05）；**FAIL 维持，阈值零改动** |

## 4. 设备证据（只写 E 文件可证）

> 设备轨 E 文件：`docs/research/evidence/2026-11-03-batch-41/`。设备 emulator-5584（API 34 / x86_64 / page-size 4096）。**本记录员未复跑设备**；以下为 E 文件原文事实。

### 4.1 产物身份（E-41-D01 §3）

| 产物 | 字节 | sha256 |
|---|---|---|
| app localFirst | 183602999 | `5793300c5f453a45715e9846f1e16756dac1300e04dd68d026e6f7957bccd796` |
| app androidTest（修复前/后） | 1691911 / 1692019 | `683cfafd…af89e` / `0e906482a0a98d1e4b9f5bb56a3262a80a32d9f6404b80495289b22cec4e3aca` |
| data androidTest | 194469464 | `82a6f21e05419a9e9316373fe31f467824c082e9d08db107c99141c353ffac02` |
| database androidTest | 209357634 | `6f55edbf52a72fca123ecb94992689f9a618357936458e557a58ccfc1d49291b` |
| app strictOffline（仅隔离诊断） | 192586802 | `9a5f6a6e85e18eef6145b45f3307a72bc4031e3ea508f95f01f15a3dac2faaba` |
| androidTest strictOffline | 1691033 | `d300d8855c05a0ad8bdb8a372eebca3538bbebd0a960f36eeb4cbed7a7c9a721` |

- 源快照 1287 文件，before→after `diff=0`（`297b2070…`）；修复 PDF 测试后 `fc93c37b…`（1 文件 / 4 行）。
- 四包 `adb install -r -t` 4×Success；设备侧 `sha256sum` 与宿主逐字节一致。
- `73.json` 资产 393415 B / `c7e7586a…`，version 73 / 92 表 / 2 view / hash `1d086050…`。

### 4.2 schema 73 设备矩阵（E-41-D02）

| 套件 | 实测 | Time | code0 / skip |
|---|---|---|---|
| `FullMigrationMatrixInstrumentedTest` | `OK (16 tests)` | 221.36 s | 16 / 0 |
| `BackupRestoreInstrumentedTest` | `OK (34 tests)` | 20.104 s | 34 / 0 |
| `BackupRowCountsCoverageInstrumentedTest` | `OK (2 tests)` | 2.111 s | 2 / 0 |
| `BackupSnapshotTrimmerInstrumentedTest` | `OK (10 tests)` | 36.223 s | 10 / 0 |
| `BackupVisualReferenceInstrumentedTest`（c4） | `OK (8 tests)` | 7.618 s | 8 / 0 |
| `ErrorCauseLedgerInstrumentedTest` | `OK (3 tests)` | 0.614 s | 3 / 0 |
| `DeviceMigrationDrillInstrumentedTest` | `OK (6 tests)` | 7.283 s | 4 / 2（两阶段补跑） |

- 计数与计划差异：trimmer 9→**10**（+WS1 identityHash 例）；错因账本 1→**3**（+WS6 两例）。
- drill 阶段 A `shortMsg=Process crashed.` 为设计；阶段 B `OK (1 test)`；`-4` 不计绿。
- **未执行**：`BackupSelectionRestoreInstrumentedTest`（closure-4 恢复级用例）不在本次 7 套件内（§5 F3）。

### 4.3 KD-75（E-41-D03）

- 首次红：`Tests run: 6, Failures: 4` + 4×`Failures: 1`；栈为 `cannot create evidence directory .../localfirst.test/files/...`。机制：instrumentation 跑在被测应用 uid（10193 vs 10194）→ EACCES。
- 最小修复：`instrumentation.context.filesDir` → `instrumentation.targetContext.filesDir`（只改 untracked 测试文件；sha `eeae8609…` → `cf44836c…`；未改断言）。
- 修复后：`OK (6 tests)` + 4×`OK (1 test)`，**10/10、skip=0**；测试 APK `0e906482…`（宿主=设备）。
- 独立读者：pypdf 6.16.2，7/7 文件可开、页数/尺寸一致、`/AIGC` 仅 labeled、7 键、`Label="1"`；`classic-table-twice` 与 labeled **完整 sha 相同**（幂等）。
- 边界：首红原始 log 在重跑前被 `rm -rf pdf/` 覆盖，只剩会话文本；不覆盖 `core/export` 真实导出链。

### 4.4 KD-76（E-41-D04）

- 单轮入口 `-e smb.kd76.round 1..20`；脚本每轮独立 `am instrument`，默认 reboot。
- 单轮样本（`KD76_SAMPLE`）：bind **710 ms**、warmup **206 ms**、readyGap 16 ms、firstByte 228 ms、total **1160 ms**；bootCount=2；设备自报 APK `5793300c…`。
- 汇总：`n=1/20 | G1: green`；`G2: NOT EVALUABLE`；“no verdict may be derived from this run; do not quote a p95”。
- **主口径 M、20 轮 p95、G1/G2 判词均未评估**。

### 4.5 isolation（E-41-D05 + E-41-WS4）

- D05：shell 低负载门 PASS（wakeup p95 24.10 ms / 限 35.59；busy p95 0.98% / 限 4.51），但 instrumentation 自证 **3/3 次 `UNGATED reason=own_cpu_busy`**（own_cpu≈3.1–3.5 s / window≈3.1 s）；15 例全 `-4`、code0=0。**NOT PASSED**。
- WS4：6 个 run 全 15/15 PASS、0 skip（app `665cd5e0…`）；pre-gate 6/6 PASS，post-gate **5/6 INVALID**（busy_max 6.9–10.4% vs 6.74%；ctxt 742–918 vs 632）；`pair.json` → **UNCONVERGED**、paired=false；T4 控制轮**未做**。
- 两套证据不互相替代（§2.2-1）。任何“isolation 通过/T4 排除”表述均不成立。

### 4.6 崩溃签名（E-41-D06）

- 17 份 logcat；九类（`FATAL EXCEPTION`/`ANR in`/`NoSuchMethodError`/`NoClassDefFoundError`/`UnsatisfiedLinkError`/`SIGSEGV`/`CANNOT LINK`/`failed to map segment`/`death was not confirmed`）**全 0**。
- **0 不等于健康**：isolation 未起 `:visual_js`，worker 账本四项也全 0；KD-76 未落全量 logcat；p10 未跑。

## 5. 复核发现与处置

> 来源：独立核对（18 findings）与对抗复核（11 findings）的 `submit_result` 原文；加上本记录员本轮实读/实跑。`status` 列区分“已独立复核”（本轮读/跑过）与“转录/未复核”。修复环（P17）只拿到两条自审修复，**未收到 findings 原文**（§5 F20），因此下列多数中/高项**未修**。

| # | 发现 | 位置 | 严重度 | 状态 | 处置 |
|---|---|---|---|---|---|
| F1 | 计划 §2 实列 62 项却写 55，总账 84 应为 91 | 计划 `:26,110-113` | 中 | 已独立复核（本轮解析 62） | 本文件 §0.1 用 55 行映射，保留全部编号；计划未改 |
| F2 | WS1 1/4/7 的“缺陷”前提在 HEAD 已成立（每科整份 budget 已修、Gate 已拒 SELF_REPORT、Trimmer 已校验 hash） | `ReviewCapacityScheduler.kt` HEAD `:68-70`；`ErrorCauseGate.kt:43`；`BackupSnapshotTrimmer.kt:285` | 中 | 已独立复核（`git show HEAD`） | item 1 改按“紧迫度配额/去重前置/单次爬坡”记账；item 4/7 只记回归锁 |
| F3 | closure-4 恢复级用例（`BackupSelectionRestoreInstrumentedTest`）未在任何设备套件执行 | E-41-D02 §1 套件表；`run-index.txt` | 中 | 已独立复核（读 E 文件/索引） | 记 UNVERIFIED；设备轨需在绑定 APK 上补跑 |
| F4 | 单测门 6 红（3 `.mimosa` + 3 KB）：产品码面 0 新红，但门本身未达“0 红”；3 条 schema 红是本批工具产物 | `batch41-unit.log`；`ExportedSchemaContractTest.kt` | 中 | 已独立复核（日志/代码） | P17 修复 schema 3 红（236/0）；KB 3 红归他线，未改 |
| F5 | MASTER-LINE/决策台账/KD-75 写“无设备执行/WS5 无证据”，与 D02/D03 矛盾 | `REFACTOR-MASTER-LINE.md:4-5`；决策台账 `:3783-3784`；`known-defects.md:2904` | 中 | 已独立复核（三处原文 + E 文件） | 时间线原因（文档 23:39 < 设备 00:50）；本记录以 E 文件为准，文档待回填 |
| F6 | isolation 两套互斥结论（0 例执行 vs 6×15/15）且 APK 不同 | `00-index.md:71`；`ws4-isolation.md:11,31`；E-41-D05 | 中 | 已独立复核（两份原文） | §2.2/§4.5 并陈；批次裁定 UNCONVERGED / NOT PASSED |
| F7 | `isolation_pair_report` 对 OFF 臂缺失方法不判 SKIP/UNKNOWN，可假绿 `PAIRED_RED_SET_IDENTICAL` | `isolation_pair_report.py:281-308,349-388` | **高** | 已独立复核（读码；复核员有实跑探针） | **未修**（P17 未覆盖）；在修好前不得以该报告作配对通过证据 |
| F8 | T4 控制轮结构不可成立：两臂同一 COMPONENT；`verify` 只比源码树，不比已安装 APK | `run_isolation_pair.sh:191-222`；`t4_control_marker.py:123-149` | **高** | 已独立复核（读码） | **未修**；“T4 排除”仍禁止 |
| F9 | KD-76 台账未回填单轮 n=1/20、G1/G2 判词 | `known-defects.md` KD-76 段；E-41-D04 | 低 | 已独立复核 | 本文件 §4.4 记录；台账待回填 |
| F10 | `summarize_kd76` 把空 per-round sha 整轮剔除；bootCount 不设门 | `summarize_kd76.py:363-373` | 中 | 已独立复核（读码；复核员有探针） | **未修**；20 轮若跑，必须另加“每轮都有 sha”判据 |
| F11 | 旧单进程 20 轮入口仍在，`Kd76ReportVerdict` 接受 `requiredSamples/gateMillis` 覆盖 | `VisualJsConnectStageCalibrationInstrumentedTest.kt:144,787-810`；`Kd76ReportVerdict.kt:78-83` | 中 | 已独立复核（读码） | **未修**；主口径判词仍可能被单进程/放宽参数伪造 |
| F12 | Preview 的 `rebalanceBacklogCount` 在 rebalance=null 时归 0；`totalGranted` 只算考试通道 | `ReviewCapacityPreviewReader.kt:136,140`；`Scheduler.kt:124-130` | 中 | 已独立复核（读码） | **未修**；item 3 记“部分” |
| F13 | 畸形 PDF 的 `assertFalse(target.exists())` 空转（target 只在成功分支创建） | `PdfAigcDeviceRoundTripInstrumentedTest.kt:292-304` | 低 | 已独立复核（读码） | **未修**；typed error 断言仍有效 |
| F14 | closure v4 夹具由同套规则/摘要自证，无历史真实 v4 包 | `SmbkArchiveCodecTest.kt:1486-1638` | 低 | 已独立复核（读码；对抗复核有键集比对） | 记残余风险；需真实旧包或历史 digest golden |
| F15 | R4 文档写“未启动 ps16k”，WS9 后续已启动（文档/产物时点差） | `2026-11-03-16kb-page-testing.md:22,268,308`；`build/ws9-batch41/ps16k-boot.log` | 低 | 转录（R4 摘要 + WS9 TrackOutcome） | 文档未改；本记录并列时点 |
| F16 | 证据目录 E-41-P11=环后强门、E-41-P12=单测门，与工作流 P11=单测门、P12=强门、P18=环后强门冲突 | `00-index.md §0`；`p11-postfix-strong-gate.md`；`p12-unit-gate.md` | 中 | 已独立复核（读两份 E 文件 + 工作流脚本） | 本文件 §3 开头显式澄清；不合并编号 |
| F17 | E 文件停在 01:52–01:54，未含 02:45–03:16 修复环；P12/E 仍写“6 红”，P11/E 的“环后强门”其实是 00:36 的 P12 | E-41-P11/P12；`batch41-*-postfix*.log` | 中 | 已独立复核（文件 mtime + 日志时间） | §3 以 03:xx 日志为准；E 文件待补记 |
| F18 | WS7 ledger 设备用例、WS6 closure-4 设备用例均未跑 | `PaperEditLedgerInstrumentedTest.kt:70`；`BackupSelectionRestoreInstrumentedTest.kt:214` | 低 | 已独立复核（读码/设备索引） | UNVERIFIED |
| F19 | 00-index 写“差 42 行/21 文件”，按路径 join 实为 12 个文件/21 条 diff 行 | `00-index.md:31`；`src-sha-after-fix.txt` vs `src-sha-now.txt` | 低 | 已独立复核（本轮 join 复算：3 新增 + 9 改） | 本记录 §0.4 采用 12 文件 |
| F20 | 修复环 ask 未携带 findings（workflow `:440-451` 未插值），P17 只能自审 | `.zcode/workflow-drafts/batch-41.ts:440-451` | 中 | 已独立复核（读脚本） | 登记为流程缺陷；F7/F8 等因此未修 |
| F21 | P18 期间他线并发 Gradle/改源，“门禁无并发”未满足 | `batch41-unit-postfix*.log`/`strong-postfix*.log` header 与 daemon 记录 | 中 | 已独立复核（日志） | exit 仍可证任务图成功；不称干净窗口 |
| F22 | 工作树 3 个 Kotlin 文件全 CRLF（`IsolationArms`/`LowLoadProbe`/`VisualJsIsolation`），`.gitattributes` 要求 LF | 集成员 TrackOutcome；`git check-attr` 未逐项 | 低 | 未复核（转录） | 建议 WS4/主会话打包前归一；本记录未改 |

## 6. 遗留、阻塞与 UNVERIFIED

### 6.1 阻塞（需主会话/产品裁定或热区清空）

1. **KD-75**：设备往返已 10/10 绿（绑定 `0e906482…`），但 `core/export` 仍零接线（热区），真实导出链未验证；接缝只出 patch 草案。
2. **KD-76**：单轮入口可用，但 20 轮主口径 M/n/G1/G2 未跑；旧单进程 20 轮入口仍未阻断（F11）。
3. **isolation/T4**：WS4 配对 UNCONVERGED、D05 全 `-4`；T4 生产侧控制轮未做；F7/F8 两个工具假绿未修。
4. **KD-83/closure 4**：读侧兼容已落码，但无真实 v4 包/设备恢复证据；`BackupSelectionRestore` 未在绑定 APK 上跑。
5. **CI 转绿**：HEAD `KnowledgeQuizTasks.kt` 缺 `is PaperEditInput` 分支；修复只在工作树且 `core/export` 属热区。
6. **KD-72 锁窗口**：14 个未跟踪锁严格模拟 36 错；窗口仍关闭。
7. **Keeper/R-04**：4/4 签名变量 UNSET、Gradle 未跑；D1/D2/D3 未裁定。
8. **`docs/current` 7 M**：契约 P0 只能保持草案。
9. **WS7 生产装配**：`feature/tutor` 第二份提示词与 PDF→PaperEditInput 映射仍在热区。

### 6.2 非本批红（边界登记）

- `core:data/.../knowledge/` 3 例 KB 词面 golden：本批未改；P18 实测值仍在漂移（MRR 0.6178、Stage1 0.1593、命中 101）。
- `.mimosa/hook-state/*.json` 导致的 schema 契约红：P17 已让测试排除隐藏目录，`core:database` 236/0；但 `.mimosa` 仍会写入该目录，其他递归扫描器仍有同类风险。

### 6.3 UNVERIFIED（不得写通过）

- 任何 Gradle 编译/单测门/强门：本记录员**零 Gradle**；P11/P12/P18 的结果均来自日志转录。
- 修复后 12 模块完整单测门（只跑了 core:data + core:database）。
- 冷构建；`:core:export`/`:core:ui` 的 androidTest 强制重编。
- 设备 10:13/00:46 APK 之外的任何设备结论；当前树不能逐字节复现本轮设备绿。
- KD-76 20 轮、M、p95、G1/G2 正式判词。
- isolation 15 例在批次 APK 上的实际行为；OFF/ON 配对、T4 控制轮。
- WS7 ledger 设备用例、WS6 closure-4 设备恢复用例。
- 16KB 真机/arm64；`mapping.txt`；Keeper K1–K6。
- AUD-08 人工金标正确率、自动决策开关。
- WS9 的 CI 修复（只在工作树）。

## 7. 提交清单建议（每块 ≤6 路径；排除热区/锁文件/tmp）

> 只是建议，**本记录员未做 git 写**；提交前逐路径 `git status`/`git diff --cached` 核对。热区（`feature/*`、`core/ui`、`core/export`、`core/domain`、`settings.gradle.kts`、Application/Root）与锁文件、`.mimosa`、`build/`、tmp、`C\357\200\272...diff` 均不得进入本批提交。

| 块 | 路径（≤6） | 备注 |
|---|---|---|
| B1 | `ReviewCapacityPolicy.kt`、`ReviewCapacityScheduler.kt`、`ExamQuotaPlanner.kt`、`MissedDayRebalancePolicy.kt`、`ReviewCapacityPreviewReader.kt`、`StudyReviewPlannerService.kt` | WS1 主码；PreviewReader 含 P17 KDoc 修正 |
| B2 | `ReviewCapacityPolicyTest.kt`、`ReviewCapacitySchedulerTest.kt`、`ExamQuotaPlannerTest.kt`、`MissedDayRebalancePolicyTest.kt`、`ReviewCapacityPreviewReaderTest.kt` | WS1 测试；容量两文件含“去重前置”改写 |
| B3 | `ClosureRuleTables.kt`、`SmbkArchiveCodec.kt`、`AndroidBackupRepository.kt` | closure 兼容；后两者为共享 M，逐 hunk 核对 |
| B4 | `SmbkArchiveCodecTest.kt`、`BackupSelectionRestoreInstrumentedTest.kt`、`BackupSnapshotTrimmerInstrumentedTest.kt` | closure/trimmer 测试 |
| B5 | `check_backup_plan_sync.py`、`check_backup_plan_sync.ci.patch`、`test_backup_plan_sync.py` | 四方防漂移门 |
| B6 | `ErrorCauseGateTest.kt`、`ErrorCauseChainTest.kt`、`ErrorCauseBookingService.kt` | WS6；Gate 生产码不在本批 |
| B7 | `ErrorCauseLedgerInstrumentedTest.kt` | WS6 设备 3/3 |
| B8 | `PdfAigcDeviceRoundTripInstrumentedTest.kt` | WS2 设备 10/10；untracked |
| B9 | `VisualJsConnectStageCalibrationInstrumentedTest.kt`、`Kd76ReportVerdict.kt`、`Kd76ReportVerdictTest.kt` | KD-76 入口/判词 |
| B10 | `run_kd76_rounds.sh`、`summarize_kd76.py`、`kd76_chain_segments.py` | KD-76 工具 |
| B11 | `test_summarize_kd76.py`、`test_kd76_chain_segments.py` | KD-76 工具测试 |
| B12 | `lowload_gate.py`、`run_isolation_pair.sh`、`isolation_pair_report.py`、`t4_control_marker.py` | WS4 工具；F7/F8 未修，提交不等于可用 |
| B13 | `test_lowload_gate.py`、`test_isolation_pair_report.py`、`test_t4_control_marker.py` | WS4 工具测试 |
| B14 | `LowLoadProbe.kt`、`IsolationArms.kt`、`VisualJsIsolationInstrumentedTest.kt` | 全 CRLF（F22），建议先归一 |
| B15 | `PaperEditModelPort.kt`、`RepositoryPaperEditModelPort.kt`、`PaperEditModelPortTest.kt`、`PaperEditPromptGoldenTest.kt` | WS7 接缝/提示词 |
| B16 | `paper-edit-prompt-golden.txt`、`paper-edit-prompt-golden-no-note.txt`、`PaperEditLedgerInstrumentedTest.kt`、`PaperEditAdapterTest.kt` | golden + 设备 ledger；AdapterTest 为共享 M |
| B17 | `OpenAiCompatibleModelGatewayTest.kt` | 共享 M；仅提交本批 2 例 hunk |
| B18 | `GATE.md`、`check_eval_label_leakage.py`、`test_check_eval_label_leakage.py`、`step-aud08-gate-recompute.md`、`step-aud08-prohibition-scan.md`、`rulings/20261102T143319Z-4c83a01e.json` | AUD-08 |
| B19 | 研究 R1–R6 六份 `.md` | 按计划 §1 文件名 |
| B20 | 研究 R7–R9 三份 + `research-crosscheck.md` + `contract-v1.1-p0-seams.md` + `aud08-human-gold-protocol.md` | 6 份 |
| B21 | `00-index.md`、`step01`、`step02`、`step03`、`step04`、`step05` | 设备证据 1 |
| B22 | `step06`、`ws2-core-export-seam-patch.md`、`ws4-isolation.md`、`ws6-error-cause-chain.md`、`p11-postfix-strong-gate.md`、`p12-unit-gate.md` | 设备证据 2（E 文件需补 03:xx 修复环） |
| B23 | `p13-ws1-subpieces.md`、`2026-11-03-batch-41-baseline.md`、`2026-11-03-batch-41-record.md` | 本条记录放最后 |
| B24 | `docs/known-defects.md`、`docs/REFACTOR-MASTER-LINE.md`、`docs/agent-first-refactor-decisions-2026-09-23.md`、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | 共享 M；逐 hunk；需按 F5 回填设备事实 |
| B25 | `ExportedSchemaContractTest.kt` | P17 修复；与 core/database 契约测试同块 |

- **不得提交**：14 个 `gradle.lockfile`、`feature/tutor/.../nul`、`.mimosa/`、`.pyc`、`build/`、`%TEMP%/`、`.agent_*`、`C\357\200\272Users...diff`、热区 M 文件。
- 共享 M 面（`SmbkArchiveCodec`、`AndroidBackupRepository`、`StudyReviewPlannerService`、`PaperEditAdapterTest`、`OpenAiCompatibleModelGatewayTest`、`docs/known-defects.md` 等）提交前必须逐 hunk 核对，不能把他线 hunk 记入本批。

## 8. 回链

- 计划：`.zcode/plans/plan-2026-11-03-batch-41.md` §1/§2/§3/§4/§5。
- 基线：`docs/research/2026-11-03-batch-41-baseline.md`（D1/D2 改判、D4 真缺口、D5/D6 口径）。
- 证据索引：`docs/research/evidence/2026-11-03-batch-41/00-index.md`（§0 编号映射、§2 产物身份、§4 可证性、§6 缺口、§8 脱敏）。
- 设备 E 文件：`step01-rebuild-install.md`、`step02-schema73-device.md`、`step03-kd75-pdf-roundtrip.md`、`step04-kd76-single-round.md`、`step05-isolation-lowload.md`、`step06-crash-signatures.md`、`p11-postfix-strong-gate.md`、`p12-unit-gate.md`、`p13-ws1-subpieces.md`、`ws4-isolation.md`、`ws6-error-cause-chain.md`、`ws2-core-export-seam-patch.md`。
- 研究：R1–R9 九份 `2026-11-03-*.md`（§1.1 列名）。
- 台账：`docs/known-defects.md`（KD-75/76/80/83）、`docs/REFACTOR-MASTER-LINE.md:4-5`、`docs/agent-first-refactor-decisions-2026-09-23.md:3766`、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` 附录 N。
- 原始日志（gitignored，不作关闭证据）：`build/batch41-unit.log`、`batch41-strong*.log`、`batch41-unit-postfix*.log`、`batch41-strong-postfix*.log`、`build/device-track-batch41/`、`build/ws4-isolation/`、`build/evidence-batch41-p13/`。
- 本文件不替代主会话裁定：KD-75 设备面可记绿（绑定 `0e906482…`）但 `core/export` 未接；KD-76 维持 open（无 20 轮 M）；isolation **UNCONVERGED / NOT PASSED**，禁止“T4 排除”；KD-83 维持 open；schema 73 设备门只对本轮 APK/源快照成立。
