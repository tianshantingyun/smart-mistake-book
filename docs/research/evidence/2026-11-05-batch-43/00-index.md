<!-- 体积自查：213 行 / 31,380 B（2026-11-05 修复环落盘后 wc -lc 实测）。R4 上限 400 行 / 65536 B。目录预算：14 个 .md + 1 个 rulings JSON（上限 16 个 .md） -->
# 批次 43 证据索引（2026-11-05 批次日；实跑跨 2026-11-03/04 本机时间）

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md`（**实列 78 个实施项**，见 §8-N9；标题与合计已于 2026-11-05 修复环订正为 78 / 104）
- 首项输入：批次 42 记录 `docs/research/2026-11-04-batch-42-record.md` §9.2（越权回滚）/§9.3（S4 两层裁定）/§9.5（遗留）
- 本文件范围：本目录内 E 文件的**元字段整理、编号映射、绑定与可证性边界**
- 字段口径 = `evidence-id / verdict / executed-at / head / device-serial / apk-sha256 / exit-code / raw-log / record-ref / sanitized`（本 ask 指定）
- 整理者只做**只读核对 + 文本整理**：未运行 Gradle、未跑设备、未改源码/热区、未做 git 写。本轮实跑的只读检查列在 §7，其余判定均为**转录**，逐行注明出处。**2026-11-05 修复环**另行做了：KD-76 工具重复行折叠（含单测）、两份 5570 PDF 的 pypdf 重读、计划项数订正；未跑 Gradle/设备。

## 0. 编号映射（先读这一节）

| E-ID | 文件 | 计划落点 | 判定（本索引转录，不代替主会话裁定） |
|---|---|---|---|
| `E-43-DEV-01` | `ws1-ws4-device-track.md` | §2 WS1 6/7/8、WS2、WS3 22–25、WS4 36 | **PARTIAL**（文件无 `verdict` 字段）：WS1 设备 2×7/7 + pypdf 读回；WS2 A6 **NOT EVALUABLE**（n=10/20、7/20）；WS3 witness 15/15 执行但低负载门为**合成判据**；WS4 34 套件中 31 OK / 2 `Process crashed` / 1 红 / 15×`-4` |
| `B43-WS2-KD76-A6-01` | `ws2-kd76-a6.md` | §2 WS2 11–18 | **A6 NOT EVALUABLE（exit 2）**；KD-76 **维持 open**；item 13 **BLOCKED**（结构性）；item 16 **部分** |
| `E-B43-WS3-01` | `ws3-isolation-selfwitness.md` | §2 WS3 19–28 | **不写因果**：15/15 在**未认证窗**内真实执行、witness 齐全；低负载门 `INVALID`；T4 控制构建**身份**可核 |
| `E-43-WS4-01` | `ws4-device-rebaseline.md` | §2 WS4 29–36 | **PARTIAL**（8 项已执行，结果含 2 红 + 15 跳；原 `verdict=PASS` 与正文 241 绿/2 红/15 跳矛盾，2026-11-05 修复环订正）：**不得记“通过”或“全绿”** |
| `E-43-WS5-01` | `ws5-release-prefight.md` | §2 WS5 37–44 | **PARTIAL**：**HEAD 不可构建**；16KB 静态+设备过；arm64 **BLOCKED**；生产签名 **BLOCKED** |
| `B43-WS8-57-HUMAN-GOLD-RUNBOOK-01` | `ws8-aud08-human-gold-runbook.md` | §2 WS8 57 | **BLOCKED（外部：无标注员）**；抽样可执行性复算 24/24 |
| `B43-WS8-59-PROHIBITION-RESCAN-01` | `ws8-aud08-prohibition-rescan.md` | §2 WS8 59 | **0 违规**（error 0 / warning 3 既有） |
| `B43-WS8-60-AUD08-AUDIT-REPORT-01` | `ws8-aud08-audit-report.md` | §2 WS8 60 | **AUD-08 = FAIL 维持**（① 0.5714 < 0.9） |
| `E-A08-T1/T2/T3` | `step-aud08-gate-recompute-b43.md` | §2 WS8 58 | T1/T2 **PASS（子步骤）**；T3 **FAIL（exit 1）**；阈值零改动 |
| `B43-WS10-01` | `ws10-security-deps.md` | §2 WS10 65–68 | **无完整安全结论**（Mimosa `inconclusive`）；license 快照落盘；锁窗口走**等待清单**；密钥扫描**覆盖内**零命中 |
| `B43-WS11-PERF-01` | `ws11-performance.md` | §2 WS11 69–72 | **PARTIAL**：模拟器四类基线实测；真机/arm64 **BLOCKED**；生产进程导出内存 **UNVERIFIED** |
| `E-43-WS1-01` | `ws1-kd75-c2.md` | §2 WS1 6/7/9（C1–C8 对账） | **PARTIAL**：C1–C7 有设备实测；C2/C3 另有宿主 pypdf 重读；C2 只覆盖 `MODEL_DOCUMENT_PARSE`，自然语言改卷模型文本另立 KD-85；item 5 JVM 逐例 UNVERIFIED |
| `B43-RESEARCH-CROSSCHECK-01` | `research-crosscheck.md` | §1 R1–R6 交叉复核 | 六题：2 够用 / 2 需新研究 / 2 **冲突**；另 9 条发现（C1–C9） |
| （机器产物） | `rulings/20261103T213902Z-c0dc8d30.json` | §2 WS8 58 | 门 `state=FAIL`、7 页；不单独作 E 文件 |

- 旧标识/别名：`E-43-WS4-01`、`WS3-2026-11-05`、`WS5-43`、`B43-RESEARCH-CROSSCHECK-01`。
- **E-ID 前缀不统一**（`E-43-*` / `E-B43-*` / `B43-*`）：按文件原样转录，不改名（登记为 §8-N11）。
- **本目录不覆盖全部计划项**：WS1（代码/JVM 侧）已由修复环补 `ws1-kd75-c2.md`；WS6（合同与计划）、WS7（遗留缺陷）、WS9（6C-2 防漂移）、WS12（收尾）**在本目录没有独立 E 文件**（WS12 的交付在批次记录）；缺口见 §6。

## 1. 环境与源绑定（整理时实测 + 各 E 文件自述）

| 项 | 值 | 依据 |
|---|---|---|
| HEAD | `0fef943641f53560b92ce2afec4da22bf52f5e86`（`0fef9436`） | 本轮 `git rev-parse HEAD`；与计划 §0、WS2/WS4/WS5/WS8/WS10/WS11/RESEARCH 头表、`build/batch43-strong.log:2` 同值 |
| 工作树 | `git status --porcelain` = **598**；`-uall` = **3822** | 本轮实测（共享工作树，他线在飞；不归因）。各 E 文件自述时点值：511（RESEARCH 写前）/ 540→555（WS2）/ 578（WS4、DEV 构建时）/ 590（WS10、WS11）/ 3801（强门 08:27）——**不同时刻，不可互推** |
| DB schema | 73 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:142`（本轮实读）；本批未改 schema |
| 6C-2 行为面 | `ALLOWED_MASKS = linkedSetOf(9, 13, 15)` | `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt:166`（本轮实读）；`require` 在 `:140`、`isCreatable` 在 `:90` |
| 计划项数 | **78**（`count 78 / min 1 / max 78 / dups [] / missing []`） | 本轮 `python -I` 实跑；计划曾写 74 → 6+78+20 = **104**；修复环已把计划标题/§2/合计同步为 78 / 104 |
| 研究件 | `docs/research/2026-11-05-*.md` 共 **9** 份 | 本轮 `ls`：6 份 R1–R6 + `batch-43-baseline`、`release-gates`、`ws6-contract-plan-closeout` |
| 本目录状态 | 13 `.md` + 1 `rulings/*.json`，**全部未跟踪**（`git ls-files` 计数 0） | 本轮实测（含本索引）；提交由主会话按块处理 |
| 台账 | KD-75 标题写 **closed 2026-11-05 批次 43 WS1**；KD-76 **open**；KD-83 closed | `docs/known-defects.md:2879` / `:2914` / `:3290`（本轮实读标题行） |

## 2. 产物身份（逐轨绑定；**跨轨不可合并**）

### 2.1 本批出现了 **6 份不同 app APK**（同一个“批次”里）

| 轨 | app sha256 | 测试包 sha256 | 设备 | 时点（本机） |
|---|---|---|---|---|
| WS2（`B43-WS2-KD76-A6-01`） | `ec135844…e19bb5` | app.test `a2532e16…1e9687b` | emulator-5584 | 22:56–01:45（2026-11-03/04） |
| WS3（`E-B43-WS3-01`）正常构建 | `028eb07e…fea78f`（6 处 T4 打点） | `8a63b236…9d8518` | emulator-5566（对照 5562） | 00:30–02:59 |
| WS3 控制构建 | `119812ff…3c23e4`（0 处打点） | 同上 `8a63b236…` | emulator-5566 | 同轨 |
| WS4（`E-43-WS4-01`）/ WS11（`B43-WS11-PERF-01`） | `1aee470a…9ed3cd` | app.test `cd35452e…5ed76d`；data.test `48213049…808154f`；database.test `671b3815…458fec`；export.test `08ea2641…e165c0` | emulator-5584 | 03:04–03:52 / 06:50–07:14 |
| WS1–WS4 设备轨（`E-43-DEV-01`） | `9b832f96…d7e29e16` | app.test `00bae341…105511a9c`；export.test `08ea2641…`；data.test `318a13ef…d9d3eea9`；database.test `cdeb8c7b…e09809d6fd` | 5584 / 5566 / 5570 | 08:44–11:30 |
| WS5（`E-43-WS5-01`）release | `a5fa0c4f…d3a4ef8`（strictOffline release） | —（release 无测试包） | emulator-5620（16KB） | 04:01–04:46 |

- **WS2 跑的是批次 42 的已安装字节**：`ec135844…` / `a2532e16…` 与批次 42 `E-42-DEVICE-01`（`docs/research/evidence/2026-11-04-batch-42/00-index.md:47`）**逐字符相同**；WS2 自述“本批未重装、未重新构建”（`ws2-kd76-a6.md:14`）。因此 WS2 的 A6 **不是批次 43 构建的产物**；其自述当前树产物 sha 为 `2449503…`（`ws2-kd76-a6.md:251`）。
- **WS4 与 WS11 共用同一套 5 包**（`1aee470a…` 等）；WS11 头表自述“与 WS4 逐位相同”（`ws11-performance.md:17`）。
- `08ea2641…`（export.test）在 WS4 与设备轨两轨同值——同一测试包，**不同 app APK**。
- 任何“批次 43 设备全绿”的说法都必须指名是哪一份 APK；本索引**不合并**跨轨数字。

### 2.2 冻结夹具与门产物（自述 + 本轮复核）

| 产物 | sha256 | 复核 |
|---|---|---|
| AUD-08 金标 `tools/split_golden/tables/split_golden_v1.json` | `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f` | 本轨**未复算**（WS8/step 自述） |
| AUD-08 应用导出 `build/split-eval/app-split.json` | `9b49b0c474d68409eba1617b888e7cf823507341ef63f54a65d9f4d68f4c2fd6` | 同上；`source` 块缺失 |
| 四份 AUD-08 证据产物 | `e74c4d4a…`（step）/ `9f3a1256…`（rulings）/ `e0c1f9d7…`（runbook）/ `29939352…`（rescan） | **本轮 `sha256sum` 实测，与自述逐位相同** |
| 门报告 `build/ws8-batch43/gate-report-b43.json` | `944d0951…`（三批逐字节相同） | 本轨**未复算**（`build/` gitignored） |

## 3. 步骤与判定（每行只转录对应 E 文件；本索引未复跑设备）

| E-ID | 覆盖 | 判定 | 锚点 |
|---|---|---|---|
| `E-43-DEV-01` §1 | KD-75 C2/C4 设备用例（`MistakePdfExporterAigcInstrumentedTest` 7 例） | 5584 `OK (7 tests)`、5570（API 23）同 7 例；宿主 **pypdf 6.16.2** 独立读回：模型解析文本产物 `contains_AIGC_bytes True`、纯用户产物 `False` | `ws1-ws4-device-track.md:40-68` |
| `E-43-DEV-01` §2 | KD-76 A6（同构建两批、30.2 min 分离） | 批 A **10/20**、批 B **7/20**；工具 `A6: NOT EVALUABLE`、exit 2；`p95_millis=null`。每轮 2 条 `KD76_SAMPLE`（`Log.i`+`println` 双写）已在 2026-11-05 修复环由 `summarize_kd76` 折叠**逐字相同**的重复行；折叠后两批仍因各 1 个真实失败轮（n=19/20、G1 RED）不可判，重复行不再是 disqualification | `:80-152` |
| `E-43-DEV-01` §3 | isolation witness + `-4` 根因 | 两次单臂 `OK (15 tests)`、witness 78 行、`EXECUTED=15`；`-4` = `Assume` fail-closed（缺 shell 判词）→ 归因**调用形状**，不是用例/产品缺陷 | `:158-192` |
| `E-43-DEV-01` §4 | WS4 34 套件（5584） | 31 套 OK；p09 红；matrix/dbcommit `Process crashed`（**并发重装**，5570 复跑 OK）；isolation 15×`-4` | `:198-251` |
| `B43-WS2-KD76-A6-01` | 同构建两批 20 轮（A6 工具链 + 设备） | 批 A n=19/20 G1 RED、批 B n=19/20 G1 RED → **A6 NOT EVALUABLE**；身份代际被自己的门抓到不稳定字段（真缺陷，已修并在设备验证）；item 13/16 见 §3 行 | `ws2-kd76-a6.md:26-35,124-180` |
| `E-B43-WS3-01` | isolation 自证、`-4` 根因、15 例实跑、T4 控制构建 | probe-04：witness 78 行、`EXECUTED=15`、`total_body_wall_ms=69 360 ≥ 44 450`；`-4` 根因含 **own_cpu 分母语义**（占单核 80% → 占整机 80%）；6/6 run 15 PASS；门 `INVALID`（`la_ratio_max 0.475 > 0.30`）→ **不写因果** | `ws3-isolation-selfwitness.md:22-155` |
| `E-43-WS4-01` | 5 包重建、32 套件、迁移矩阵、备份族、错因、PDF、崩溃签名、worker/强停 | 258 声明 / 243 执行 / **241 绿 / 2 红 / 15 跳**；matrix 16/16（420.671 s）；errorcause 3/3；pdfaigc 10/10；exportaigc **7/7**（批次 42 记 5 例） | `ws4-device-rebaseline.md:26-130` |
| `E-43-WS5-01` | 干净副本离线构建、R8 mapping、16KB、arm64、签名、mapping 保留、钉版门 | HEAD 首轮 **EXIT=1**（`KnowledgeQuizTasks.kt:126` `when` 非穷尽）；**HEAD+3 文件** EXIT=0（4m02s）；16KB 静态 14/14 + 5620 设备 COLD 1030 ms；arm64 `FATAL … not supported`；`RELEASE_*` 当前 0 个 | `ws5-release-prefight.md:21-32,86-115,160-215` |
| `B43-WS8-*` + `step-aud08` | AUD-08 四项 | 门 **FAIL / exit 1**；①`4/7=0.5714`、③`0/16=0.0000`；裁定 `validate_rulings` PASS；人工金标 BLOCKED；禁令复扫 0 违规 | `ws8-aud08-audit-report.md:13-33`；`step-aud08-gate-recompute-b43.md:29-35` |
| `B43-WS10-01` | Mimosa、license、锁窗口、密钥扫描 | Mimosa `runStatus=inconclusive`、13 findings（6 HIGH）；346 坐标 license 快照；14 未跟踪锁、强制态 36 errors；强特征令牌 0 | `ws10-security-deps.md:26-56,100-121,166-232` |
| `B43-WS11-PERF-01` | 启动/导出/内存/回归 | 冷启 7 轮 p50 3503 ms（模拟器）；HOT 189 ms；单题导出 p50≈719 ms；`PerformanceGateTest` 6/6、p95 50 ms；**不宣称无回归**（首个基线） | `ws11-performance.md:27-33,132-160` |
| `B43-RESEARCH-CROSSCHECK-01` | R1–R6 | 六题 2 够用 / 2 需新研究 / 2 冲突；C1（A6 样本口径）、C2（C4 定义）为高；C3（盘点件 6C-2 段）须作废 | `research-crosscheck.md:20-29,150-198` |

### 3.1 门禁日志（**无本目录 E 文件**，仅登记）

| 日志 | 读到的原文 | 说明 |
|---|---|---|
| `build/batch43-unit.log`（08:23） | 末行 `BUILD SUCCESSFUL in 3m 2s` / `158 actionable tasks: 62 executed, 96 up-to-date` | **无显式 exit-code 行**；不逐例列出测试名 → 单测门“绿”只到 Gradle 任务级 |
| `build/batch43-strong.log`（08:27:59–08:35:54） | `run 5 :app:compileLocalFirstDebugAndroidTestKotlin exit 1 (INFRA: Kotlin in-process ICE …)`；`run 5-retry --rerun exit 0`；`GATE RESULT: PASS with 1 infrastructure retry (NOT a clean single-pass run)` | 头两行记 `HEAD: 0fef9436`、`dirty entries (porcelain -uall): 3801` |

## 4. 仅本批可证项（位绑定；本 ask 的核心）

| 面 | 本批可写 | **只**由本批可证（绑定） | 不得外推 / UNVERIFIED |
|---|---|---|---|
| KD-76 A6 | 三组两批实验**全部 NOT EVALUABLE**：`ec135844…`（19/19 成功，含 1+1 产品失败）、`9b832f96…`（10/20 + 7/20，含 20 条 `KD76_SAMPLE` 重复行缺陷）、批次 42 `35e10339…`（单批 n=20、p95=1889 ms） | 各轨 APK sha + serial + `build/kd76-runs/a6b/`、`build/wsdevice-batch43/kd76-a6-5584/`、`build/kd76-runs/ws3-batch42-20/` | **A6 未满足**；不得相加 n、不得合并 p95；产品进程首请求 BLOCKED；C4 无毫秒分布；低内存/arm64/真机 UNVERIFIED |
| KD-76 失败率 | 本批三组里失败轮共 **1/19 + 1/19 + 10/20 + 13/20**（不同 APK、不同窗口） | 同上 | **不得合成“失败率 25/78”**；构建/窗口不同，只能逐轨陈述 |
| 29.5 s 链 | C1/C2 有 n=19 分布（A：p95 4205/2249 ms；B：5303/942 ms）；C3 仅 KD-66 witness（上界）；C4 只有布尔 | `build/kd76-runs/a6b/{batchA,batchB}`；`kd76_chain_segments.py` exit 0 | **“四段实测”不成立**；Σ max = UNVERIFIED；p95 不可相加 |
| KD-75 C2（PDF 链） | 无 AI 图、**只有模型解析文本**的导出带 `/AIGC`（Label=1）；纯用户无 `/AIGC`；同 key 旧无标记产物被拒并重建；5584 与 5570（API 23）各 7/7。**边界（修复环补）**：该判据来源是 `MODEL_DOCUMENT_PARSE`；自然语言改卷（`TutorPaperEditApplier` → `MistakePdfPaperEdit.toDraft()`）产生的模型文本仍透传来源的 `hasModelParsedText`，纯用户来源被模型改写后**不会**打标，已登记 KD-85 | app `9b832f96…` + export.test `08ea2641…` + 两份 PDF sha（`ws1-ws4-device-track.md:57-61`）；`PdfAigcContentPolicy.kt:47`（转录） | **DOCX 无任何 `/AIGC` 机制**（`MistakeDocxExporter.kt:59,77`，转录 `research-crosscheck.md:58`）→ C2 只能声明 PDF 链；独立 pypdf 只覆盖 C1–C3，C5–C7 由项目 `PdfAigcReader`/`PdfRenderer` 承担；`PdfRenderer` 打开 ≠ 外部阅读器一致 |
| KD-75 C4 | `PdfAigcContentPolicy.GENERATION == 2`（本轮实读 `PdfAigcContentPolicy.kt:30`）；三处前缀（single/batch/paper-edit）+ 哨兵需同时升版（转录 `research-crosscheck.md:56`） | `ws1-ws4-device-track.md:68` | JVM 三方一致用例**未由本目录任一 E 文件运行**（单测门不逐例） |
| isolation 执行真实性 | 15 例**确实执行**：witness 78 行、canary=true、`EXECUTED=15`、body 墙钟 69.4 s（vs 批次 42 的 820 ms 时间戳跨度） | app `028eb07e…` + test `8a63b236…` + `build/ws3-batch43/probe-04.*`；另一 APK `9b832f96…` 两次 run 同样 15/15 | **低负载门 PASS 不成立**：shell 门 `INVALID`（`la_ratio_max 0.475 > LA_MAX_ABS 0.30`）；`-e smb.lowload.gate PASS` 是合成判据 |
| isolation 因果 | T4 控制构建**身份**可区分（`028eb07e` vs `119812ff`，anchors 6 vs 0，emit/verify ok）；ON 臂时间戳读取**结构性为空**（`@After` 先关客户端） | `ws3-isolation-selfwitness.md:113-122` | **因果、T4 排除、两臂等价全部禁止**；第 1 层未成立 |
| `-4` 零执行根因 | = fail-closed 门在缺 shell 判词时按设计 skip（`LowLoadProbe.kt:234-244`）；同一 APK 给 gate 参数即 15/15 执行 | `ws1-ws4-device-track.md:179-188`；`ws3-isolation-selfwitness.md:45-76` | 不得读成“用例失败/产品缺陷”；`own_cpu` 分母由**单核**改为**整机容量**（`LowLoadProbe.kt:144,148`：`0.80 × windowMs` → `0.80 × windowMs × processors`），2 核机上有效阈值 = 1.6×window，这是**放宽**而非“分数 0.80 未动”；引用 gate 行时不得称“环境已认证” |
| 设备复基线 | 5 包 sha 双向一致；32 套件 258/243/241/2/15；matrix 1..73 循环 16/16；backup 34/34；errorcause 3/3；pdfaigc 10/10；exportaigc 7/7 | app `1aee470a…` + 4 个 test 包 + emulator-5584（`ws4-device-rebaseline.md:14-17`） | **“全绿/通过”不成立**：p09 未修、isolation 15 跳；**selrestore 已在 05:29 修复**（`BackupSelectionRestoreInstrumentedTest.kt` 懒开库改为 DAO 读 + `readDatabaseVersion()==73`），设备轨 `9b832f96…` 复跑 `OK (11)`；WS4 的该红早于修复。arm64/16KB/真机/LMK 未覆盖 |
| p09 | 本批**两轨结论相反**：WS4 = flaky（红/绿/红，跨 1300 ms 重建阈值）；设备轨 = 代码/用例期望不一致（3 次 2 台设备同断言失败） | `ws4-device-rebaseline.md:193-209`；`ws1-ws4-device-track.md:232-243` | **任何单次结论都不成立**；引用必须带 APK + 运行次数 |
| AUD-08 | 门 FAIL、①=0.5714、③=0.0000；报告 JSON 三批逐字节相同（`944d0951…`） | 金标 `f9e92c47…` × 导出 `9b49b0c4…` 这一份样本 | 当前设备库准确率、155 页总体、人工金标正确率 |
| 发版前置 | HEAD **不可编译**（至少两处断链）；HEAD+3 文件可离线出包（4m02s，`grep -ciE 'download'` 与 `'No cached'` 计数均 0）；16KB 静态 14/14 + x86_64 16KB 设备冷启 1030 ms | `git archive` tar `bcfc649f…` + overlay 3 文件 + APK `a5fa0c4f…` + `emulator-5620` | **“发版就绪”不成立**；arm64 硬件 BLOCKED；生产签名 BLOCKED；单次冷构建与构建确定性 UNVERIFIED |
| 供应链/安全 | Mimosa 扫描**完成但不完整**（`inconclusive`）；license 346 坐标；锁定 14 未跟踪、强制态 36 errors；强特征令牌 0 | `build/ws10/` 产物 + `scan-2026-11-03T22-07-50…` 封印 | **不得称“安全”/“无漏洞”**；历史内容、二进制未扫；KD-72 维持 open |
| 性能 | 模拟器首基线（冷启 p50 3503 ms、单题导出 p50 719 ms、最小宿主 PSS 42–55 MB）；DB 性能门 6/6、p95 50 ms | app `1aee470a…` + 5584 | 真机/arm64 无；生产进程内导出 PSS UNVERIFIED；**不宣称“无回归”** |
| 6C-2 | 行为面未变：`ALLOWED_MASKS == {9,13,15}`、mask=8/0 拒绝 | `BackupSelection.kt:166`（本轮实读）+ `SmbkSelectionPlan.kt` | 本目录**没有 WS9 E 文件**；`SixC2RulingInvariantTest.kt` 存在于树（未跟踪、mtime 05:59），单测门不逐例 → 其执行与结果 **UNVERIFIED** |

## 5. 同批其他文件与原始产物（非本目录 E 文件，仅索引/登记，不背书）

- 研究/记录（9 份 `docs/research/2026-11-05-*.md`）：R1 `kd75-model-text`、R2 `kd76-a6-protocol`、R3 `isolation-selfwitness`、R4 `clean-tree-release`、R5 `contract-merge-protocol`、R6 `plan-closure`、`batch-43-baseline`、`release-gates`（WS5 item 44）、`ws6-contract-plan-closeout`。
- 契约件：`docs/research/2026-11-05-contract-p0-conflict-list.json`（R5 产物）、`docs/research/2026-11-04-contract-v1.1-p0-applicable.patch`（未应用）。
- 原始日志（gitignored，不作关闭证据）：`build/kd76-runs/a6b/`、`build/wsdevice-batch43/`（含 WS1 pypdf 输入）、`build/ws3-batch43/`、`build/ws4-batch43/`、`build/ws8-batch43/`、`build/ws10/`、`build/ws11-batch43/`、仓外 `D:\smb-ws5-43`（一次性副本/钥匙/日志，可整体删除）。
- 门禁日志：`build/batch43-unit.log`、`build/batch43-strong.log`（见 §3.1；无 E 文件）。

## 6. 缺口与未验证（汇总，不得填平）

1. **批次 43 记录已补**：`docs/research/2026-11-05-batch-43-record.md` 由 2026-11-05 修复环创建（WS12 73–78 的最终分派、外部 runbook、§0 口径、提交清单、下一批建议、发版判定）；本索引的 `record-ref` 现指向它。该记录是**修复环整理件**，不等于主会话收口裁定。
2. **WS1 独立 E 文件已补**：`docs/research/evidence/2026-11-05-batch-43/ws1-kd75-c2.md`（修复环）；它把 C1–C8 逐条与「设备轨 + 本轮独立 pypdf 实读」绑定，并写明独立读者只覆盖 C1–C3。**item 5（JVM 三类内容用例）仍无逐例执行证据**（单测门不逐例）→ JVM 侧维持 UNVERIFIED。
3. **WS6 / WS7 / WS9 / WS12 无 E 文件**：合同只出 patch/冲突清单（R5 + `ws6-contract-plan-closeout`）；S10/S11/S12 已在本轮复核为**已落地**（S10 = KD-84 独立条目、KD-83 段不再挂 KD-75 正文；S11 = 四方门消费 v4 golden，`check_backup_plan_sync.py` EXIT=0；S12 = 懒开库测试改为 DAO 读 + `readDatabaseVersion()==73`），selrestore 设备轨 `OK (11)`；**p09 仍未修**；WS9-63 仍 BLOCKED（见 §6.11）。`SixC2RulingInvariantTest.kt` 未跟踪，本轮因另一 Gradle 任务在飞未重跑 → 执行与结果 UNVERIFIED。
4. **强门不是单次通过**：`build/batch43-strong.log` 明确 `PASS with 1 infrastructure retry`；任何“双门干净 exit=0”的说法需注明重试。
5. **设备绿与当前树不对齐**：WS2 绑批次 42 的 `ec135844…`；WS4/WS11 绑 `1aee470a…`；设备轨绑 `9b832f96…`；当前树仍在被并发编辑（598 脏、3822 `-uall`）→ “当前源码已验证”不成立。
6. **p09 两轨结论冲突未裁定；selrestore 已收敛**：p09（WS4=flaky vs 设备轨=确定性）仍是两轨相反，双方都不构成“已修/必红”；selrestore 的 WS4 红发生在 03:04–03:52，早于 05:29 的懒开库修复，设备轨后续 `OK (11)` 是当前状态。
7. **isolation 第 1 层 BLOCKED**：该 AVD 地板 `la_ratio_max=0.475` > `LA_MAX_ABS=0.30`，本机不存在可判 PASS 的窗；改门属行为变更，未做。
8. **外部阻塞（BLOCKED）**：真实 Provider、生产签名（`RELEASE_*` 4/4 UNSET）、arm64 硬件（x86_64 宿主无法起 arm64 AVD）、人工金标（≥2 标注员 + 1 裁定者）。
9. **`build/` 产物不可提交**：门报告、Mimosa 封印、pypdf 输入、性能原始日志均 gitignored，复核须重跑。
10. **5562 两次基础设施事故**：WS4 §1.3（reboot 后内存 124 MB、`System has crashed`）与设备轨 §2.1（`service check activity: not found`）→ 该机上任何结论为零。
11. **WS9 item 63（设计件/KDoc/测试三方一致）BLOCKED**：行为层 `ALLOWED_MASKS={9,13,15}` 未漂移（本轮 `check_backup_plan_sync.py` EXIT=0），但工作树 `core/domain/.../BackupRepository.kt:33,58` 与 `app/.../SecondaryScreens.kt:1195,1208` 仍写 0/8 合法；`core/domain` 是热区、`SecondaryScreens.kt` 为他线共享 M，本批均不改（runbook 见批次 43 记录的阻塞节）。

## 7. 本次整理实跑的只读检查（可重跑）

```bash
# 1) 源绑定
git rev-parse HEAD                                   # 0fef943641f53560b92ce2afec4da22bf52f5e86
git status --porcelain | wc -l                       # 598
git status --porcelain -uall | wc -l                 # 3822
git ls-files docs/research/evidence/2026-11-05-batch-43/ | wc -l   # 0（全部未跟踪）

# 2) AUD-08 四份证据产物 sha（与 E 文件自述逐位相同）
sha256sum docs/research/evidence/2026-11-05-batch-43/{step-aud08-gate-recompute-b43.md,rulings/20261103T213902Z-c0dc8d30.json,ws8-aud08-human-gold-runbook.md,ws8-aud08-prohibition-rescan.md}
# → e74c4d4a… / 9f3a1256… / e0c1f9d7… / 29939352…

# 3) 不可变量与 schema
grep -n "STUDY_DATABASE_VERSION" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
# → 142:internal const val STUDY_DATABASE_VERSION = 73
grep -n "ALLOWED_MASKS" core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt
# → :166 val ALLOWED_MASKS: Set<Int> = linkedSetOf(9, 13, 15)

# 4) 计划项数（78，不是 74）
python -I -c "…re.match(r'^(\d+)\.\s', line)…"
# → count 78 / min 1 / max 78 / dups [] / missing []

# 5) 门禁日志尾（只读）
tail -3 build/batch43-unit.log      # BUILD SUCCESSFUL in 3m 2s
tail -12 build/batch43-strong.log   # GATE RESULT: PASS with 1 infrastructure retry
```

- 五项都由本索引整理者在本轮执行并读到输出；**其余判定均为转录**，未复跑设备/Gradle。

## 8. 本批新增发现（本索引登记，供记录/台账引用）

| # | 发现 | 证据 | 归因/处置 |
|---|---|---|---|
| N1 | **KD-76 的“批次 43”其实是 4 组实验、3 份 app APK**：WS2 `ec135844…`（19/19）、设备轨 `9b832f96…`（10/20、7/20）、批次 42 DEVICE `ec135844…`（同 APK 另一窗口）、批次 42 WS3 `35e10339…`（单批 n=20、p95 1889 ms） | §2.1；`ws2-kd76-a6.md:11-16`；`ws1-ws4-device-track.md:83-152`；批次 42 索引 §2.1 | 任何 A6/失败率/回归结论必须先对齐 APK 与窗口；本索引不合并 |
| N2 | **WS2 的 A6 跑在批次 42 的已安装字节上**（未重装、未重建） | `ws2-kd76-a6.md:14`；批次 42 索引 `:47` 同 sha | 该证据只能作“同一构建两批”的自洽性证据，**不是批次 43 构建**；KD-76 仍 open |
| N3 | **`summarize_kd76` 曾被测试侧双写打穿**：`Log.i` + `println` 产生 2 条同内容 `KD76_SAMPLE`，工具判“每轮多条终态样本” → 即使 n=20 也无法出 p95 | `ws1-ws4-device-track.md:110`；`VisualJsConnectStageCalibrationInstrumentedTest.kt:866-869` | **已修（2026-11-05 修复环）**：`summarize_kd76` 只折叠**逐字相同**的 `(status, payload)`，内容不同的重复（先 FAIL 后 SAMPLE）仍拒绝；新增/既有 50 例工具测试全绿；A6 两批现按真实失败轮（n=19/20、G1 RED）判 NOT EVALUABLE |
| N4 | **p09 两轨结论相反**（WS4 flaky / 设备轨确定性失败）；selrestore 的 WS4 红早于 05:29 修复，设备轨 `OK (11)` | `ws4-device-rebaseline.md:193-215`；`ws1-ws4-device-track.md:232-243` | p09 两轨 APK 不同；引用必须带 APK + 次数；“已修/必红”均不成立。selrestore 以设备轨当前状态为准 |
| N5 | **身份字段不稳定被自己的门抓到**：`PackageSignatures{<objhash>}` 的对象哈希随 system_server 变，20 次 reboot 后误判“重装”；修复后 attempt 2 复检通过 | `ws2-kd76-a6.md:72-83` | 真缺陷 + 真修复；这正是 A6 装置存在的价值，建议保留为该工具的单测向量 |
| N6 | **HEAD 不可编译**（干净树出不了包），比批次 42 的“树脏”更硬 | `ws5-release-prefight.md:86-115` | 发版阻塞项；需先把 3 个文件提交，否则任何“发版就绪”不成立 |
| N7 | **isolation 地板高于绝对上限**：该 AVD `la_ratio_max=0.475` > `LA_MAX_ABS=0.30` → 本机无可判 PASS 的窗；`own_cpu` 分母已从单核改为整机容量（2 核机有效阈值 1.6×window），属**放宽**，引用时必须如此表述 | `ws3-isolation-selfwitness.md:45-101`；`LowLoadProbe.kt:144,148` | 物理事实 + 探针口径变更；解除需换机重标地板或主会话裁定按 nproc 伸缩 |
| N8 | **KD-75 台账已按 WS1 关闭，但关闭范围是 PDF 链**：DOCX 无 `/AIGC` 机制，属既有同类缺口 | `docs/known-defects.md:2879`；`research-crosscheck.md:58` | 关闭声明必须写“PDF 链”，DOCX 单列；否则形成覆盖假象 |
| N9 | **计划实列 78 项**，标题/合计仍写 74 → 账为 6+78+20=104 | 本轮 `python -I` 实跑；`plan:24,131`；`research-crosscheck.md` §1.6 | 记录按 78 行对账；“74/100”登记为计划口径缺陷（第三批同族） |
| N10 | **Mimosa 扫描器已可用但结论不完整**（`inconclusive` / `partial`，威胁建模阶段 0 入口） | `ws10-security-deps.md:42-56` | 不得写“安全通过”；6 HIGH 只报位置 |
| N11 | **E-ID 前缀三种风格并存**（`E-43-*` / `E-B43-*` / `B43-*`） | §0 表 | 只影响检索，不改名；后续批次建议单一前缀 |
| N12 | `E-43-WS4-01` 的 §2 表把 isolation 的 `OK (15 tests)` 与 15 跳并列，`E-43-DEV-01` §4 同样 → **易被读成通过** | `ws4-device-rebaseline.md:80,223`；`ws1-ws4-device-track.md:222` | 两文件都写了“不得记为通过”，索引在此再钉一次：**`OK (15 tests)` + 15×`-4` ≠ 通过** |

## 9. 脱敏扫描（本目录统一跑一次，本轮整理后）

```bash
E=docs/research/evidence/2026-11-05-batch-43
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken|storePassword|keyPassword)[[:space:]]*[:=][[:space:]]*[^ <]' "$E"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E"
grep -RInE 'C:.Users.[^ ]+|/home/[^/ ]+|/Users/[^/ ]+' "$E"
```

| 扫描 | 结果（不含本文件） | 处置 |
|---|---|---|
| secrets（强特征） | **0 命中** | — |
| credential assignments | **0 命中** | — |
| emails | **0 命中** | — |
| home paths | **3 命中**：`ws10-security-deps.md:36`（Mimosa `scanDir`）、`ws8-aud08-human-gold-runbook.md:32,58`（数据集目录） | 均为本机用户目录，**无凭据**；建议作者脱敏为 `<HOME>/…`，不阻断本批定案 |
| 人工通读 | 已过 | 无学习者内容、无凭据、无真机序列号；仅模拟器 serial、包名与仓内相对路径 |

## 10. 回链与维护

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md` §1/§2/§4/§5；§1 六题 → 9 份研究件。
- 上一批：`docs/research/2026-11-04-batch-42-record.md` §9.2/§9.3/§9.5；`docs/research/evidence/2026-11-04-batch-42/00-index.md`（APK/夹具对照口径）。
- 本目录每个 E 文件均含自述字段表；缺失项按上文写 `N/A` / `未记录`，不填猜测值。
- 本索引**不替代主会话裁定**：KD-75 关闭（**PDF 链**口径，DOCX 缺口单列）；KD-76 **open**（A6 未满足、C4 无 ms、product-first BLOCKED）；isolation **UNCONVERGED / 不写因果**；AUD-08 **FAIL 维持**；KD-72 **open**；发版就绪 **不成立**（HEAD 不可编译 + 签名 + arm64）。

## 11. 体量自查

- 落盘后实测：`wc -lc docs/research/evidence/2026-11-05-batch-43/00-index.md` → **213 行 / 31,380 B**（2026-11-05 修复环订正后的终值）。
- 目录：**14 个 `.md`** + 1 个 `rulings/*.json`（上限 16 个 `.md`）。
