<!-- 体积自查：见文末 §7（落盘后实测 wc -lc）。批次 43 研究交叉复核员。 -->

# 批次 43 研究交叉复核（6 题判定 + 冲突 + 硬约束）

| 字段 | 值 |
|---|---|
| evidence-id | `B43-RESEARCH-CROSSCHECK-01` |
| executed-at | 2026-11-03（本机时间）；文件名按计划批次日的 `2026-11-05` |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86`（`git rev-parse HEAD`，本轮实跑） |
| 工作树 | 写前 `git status --porcelain \| wc -l` → **511**；写后 **512**（+1 = 本文件）；写后拆分 `89 M + 423 ??`（逐项实跑）。基线件「-uall 3722」是逐文件口径，不可混引 |
| 执行者 | 研究交叉复核员（子代理），共享工作树，**只读；本轮唯一写入 = 本文件** |
| 未执行 | 任何 Gradle、任何 `am instrument`、任何 APK 构建/安装、任何 git 写、任何 emulator 启动、任何 Provider 调用 |
| 输入 | 计划 `.zcode/plans/plan-2026-11-05-batch-43.md`（§1 六题 / §2 实施 / §4 硬约束 / §5 验收）；批次 40–42 研究件；`docs/research/2026-11-05-batch-43-baseline.md`；批次 42 记录 §9.2/§9.3/§9.5 |
| verdict | 六题中 **2 题已够用**（R5，R1 主体）、**2 题需新研究**（R3、R4）、**2 题为冲突**（R2、R6）；另发现 **1 项计划内自相矛盾**（WS9 item 63 vs 热区）与 **1 项新增机制重复**（WS9 items 61–62 已有三重覆盖） |

> 口径：**实跑** = 本轮在本机执行并读退出码/输出；**实读** = 本轮读到仓内源码/文档原文（给出行号）；**转录** = 引既有 E 文件/研究件；**推定** = 未证推断，逐条标注。

---

## 0. 六题判定总表

| 题 | 计划要求 | 判定 | 一句话理由（证据见 §1） |
|---|---|---|---|
| **R1** KD-75 C2 | 「模型解析文本」的可判定来源与打标边界 | **主体已够用；边界需补研究** | 来源、判据函数、指纹含 provenance 三项在批次 40 `2026-11-02-pdf-incremental-aigc.md:377-404` 已答；**未答**的是 4 个输入构造点/批量/卷面/草稿 DTO 的传播与 DOCX 边界（§1.1） |
| **R2** KD-76 A6 | 同一构建两次独立 20 轮协议（时间分离/冷启动/合并规则） | **冲突（高）＋ 需补研究** | 权威预注册是「两批各 10 轮、合并 n=20」（`2026-11-01-kd76-stats.md:268`），计划 item 12 写「各自 n/p95/G1/G2，不合并 n」，item 14/15 写「两次各 20 轮」；两种口径互斥。另 C4 定义冲突成立（§1.2） |
| **R3** isolation | `-4` 零执行根因 + `own_cpu_busy` 自证探针设计 | **需新研究** | 根因未定（三台设备形状不同）；witness **能力不存在**（本轮 grep 零命中）；我给出可证伪假设与算术依据（§1.3） |
| **R4** 干净树发布构建 | `git archive` 副本 + 离线 Gradle 可行性与步骤 | **需补研究（前置条件类）** | 副本步骤/规模已有；**新事实**：Gradle 9.6.1 分发与依赖缓存都在**工作区** `.gradle`，`tools/android-env.ps1` 把 `GRADLE_USER_HOME` 指到工作区 → 新目录默认**不可**离线（§1.4） |
| **R5** 契约 7 M | 归属与并入协议 | **已够用** | 7 M 全属 4E/PRD/UI 他线（本轮逐个读 diff）；patch 只触 `visual-generation-contract.md`（不在 7 M 内），`git apply --check` **exit 0**（本轮实跑）；并入前置与顺序在 `2026-11-03-contract-v1.1-p0-seams.md:19-31`（§1.5） |
| **R6** 计划收尾 | PRD/6C/4E/5B/5b/5c 逐项状态与外部依赖 | **冲突（需更正）** | 盘点件写于批次 42 **回滚前**：§9.1 把「6C-A1 6C-2 重裁」列为可做、§10.2 WS1 写「6C-2 五 mask」，与计划 §5 不可变清单直接冲突（§1.6） |

---

## 1. 逐题

### 1.1 R1：KD-75 C2 的可判定来源与打标边界

**已答（本轮实读，确认批次 40 研究仍与代码一致）**

| 事实 | 位置（实读） |
|---|---|
| 判据函数存在且三分支 | `core/model/src/main/kotlin/.../pdf/PdfAigcContentPolicy.kt:29-38`：`shouldLabel = hasAiGeneratedImage \|\| hasModelParsedText`，另有 `hasModelParsedText(provenanceNames)` |
| provenance 有持久化载体 | `CapturedQuestionDocument.kt:181`（`val provenance: QuestionBlockProvenance`）；`:210` 枚举含 `MODEL_DOCUMENT_PARSE` |
| 已写入 DB 的题面快照 | `73.json` 的 `problem_revision.question_document_snapshot`（本轮 `python -I json` 实读列名），整份 `CapturedQuestionDocument` 由 `CapturedQuestionDocumentCodec` 编解码 |
| 导出层**手里就有** provenance 载体 | `core/export/.../MistakePdfEligibility.kt:75-87`（`checkReady(state)` 的 `state.questionDocument`）→ 同一函数 `:87` 起遍历 `document.document.blocks`；`blockEvidence` 与 `blocks` 同级可取 |
| 当前断点 | `core/export/.../MistakePdfExporter.kt:236` `hasModelParsedText = false` 硬编码；KDoc `:229-231` 自述「补 (b) 判据时必须同时改哨兵代际名」 |
| 判据保守性已裁定 | `2026-11-02-pdf-incremental-aigc.md:404`：证据里**只要出现过** `MODEL_DOCUMENT_PARSE` 就打标（宁可多标）；`:951` U6 法务解释未定 |
| 指纹已含 provenance | 同文 `:403`：`documentFingerprint` 已进 `inputSha256`，provenance 变化自然换键；**标签 schema 版本**才需进指纹 |

**未答（R1 的真工作量，本轮实读枚举）**：`MistakePdfExportInput` 是 **public 类 + `internal constructor`**，主码有 **4 个**构造点，新增字段必须逐个传播，否则批量/卷面路径会静默丢失判据：

1. `MistakePdfEligibility.kt:161`（单题，唯一能拿到 `blockEvidence` 的点 → 派生布尔应**只在这里**做）；
2. `MistakePdfBatchEligibility.kt:105`（批量：由 4 个单题输入重建**新** input，当前不聚合任何 per-question 旗标；`buildBatchExportInput` 若不带 `any{...}`，含解析文本的批量导出会漏标）；
3. `MistakePdfPaperEdit.kt:219`（卷面编辑：字段逐个复制，新增字段必须显式带上；否则编辑一次即丢标）；
4. `MistakePaperDraftStore.kt:187`（草稿 JSON DTO 反序列化：新增字段必须进 DTO；**旧草稿没有该字段** → 需定 fail-closed 规则：重新派生 / 拒绝 / 视为 false 但仅限无 provenance 的旧快照）。

另有三处版本前缀，item 4「字面版本升版」必须同时覆盖（各自独立，不是一个常量）：`"mistake-export-v3"`（`MistakePdfEligibility.kt:186`）、`"mistake-batch-v3"`（`MistakePdfBatchEligibility.kt:96`）、`"mistake-paper-edit-v1"`（`MistakePdfPaperEdit.kt:448`），外加 `MistakePdfExporter.kt:199` 的 `AIGC_POLICY_SENTINEL = ".aigc-policy-gen-1"`。

**边界（必须写进 R1，否则 C2 会被误判为已覆盖）**：`/AIGC` 注入只存在于 PDF（`labelPdfInPlace`）。DOCX 导出是**真实产品路径**（`MistakeDocxExporter.kt:59,77`；装配点 `SmartMistakeBookApplication.kt:438`），OOXML 无 PDF Info 字典，**没有任何 /AIGC 机制**。C2 只能声明为「PDF 链」，DOCX 上的模型解析文本/生成图属**既有同类缺口**，应单列登记，不得用 C2 绿暗示已覆盖。

**结论**：R1 不需要重新论证「provenance 是否可判」；需要的是**传播面与边界**研究 + 一份「新增字段 → 4 构造点 → 3 指纹 + 哨兵 → 旧草稿」的落点表。**禁止**在导出层重读文档（`2026-11-02-pdf-incremental-aigc.md:402` 已说明该层拿不到 `blockEvidence`）。

### 1.2 R2：KD-76 A6 与 C4

**冲突 1（高，判据口径）**——同一代号有两套样本设计：

| 来源 | 口径 |
|---|---|
| 权威预注册 `2026-11-01-kd76-stats.md:268` | 「默认**两批各 10 轮、合并 n=20**；n=10 时必须声明 p95≡max」 |
| A6 原文 `2026-11-01-kd76-stats.md:235` | 「关闭/维持证据要求**两批独立冷启动均过 G1/G2**」 |
| 批次 42 研究 `2026-11-04-kd76-20-rounds.md:121-137` | 方式 A = 批 A 1–10 + 批 B 11–20 **合并** n=20；方式 B = 单批 20 轮（A6 未满足）；合并规则 6 条 |
| 计划 `plan:9`（§0）与 item 11/12/14/15（`plan:39-43`） | 「两次**独立 20 轮**」「各自 n/p95/G1/G2，**不合并 n**」→ 总 40 个 boot |

「两批各 10 合并」与「两次各 20 分别判词」是**互斥实验设计**，且后者是新预注册（A10 预注册纪律要求跑前固定；不能在已有 10+10 预注册之外事后换口径）。二者只能选一，且选后者必须重新冻结 pre-registration（n、估计量、门、outlier 规则）后才可开跑。

**冲突 2（高，C4 定义）**——计划 §0 `plan:10` 把 C4 写成「产品进程首请求/其他 API」，而权威定义 C4 = `abandon` → `awaitDeathConfirmation`（预算 2 000 ms，`2026-11-01-kd76-stats.md:169`、`:273`）；「产品进程/UI 首请求」是 KD-76 的 **③ 判据面**。代码侧只有布尔：`VisualJsConnectStageCalibrationInstrumentedTest.kt:516` 取 `engine.abandonForTest()` 后直接断言，**无毫秒夹钟**；`kd76_chain_segments.py:138-140` 的 `--c4-max-ms` 由调用方手工传入，不是实测。故 item 16「29.5s 链四段实测与相加报告」在 C4 段**当前不可达**（要么加 host 侧夹钟 seam，要么如实写「C4 = 布尔 + 2 000 ms 预算，无 ms 分布」）。

**工具现状（本轮实读）**：

- `run_kd76_rounds.sh:74` 已有 `--first-round`（10+10 编号可用）；但 `KD76_ENV` 行（`:337`、`:407`）**无 batch 身份字段**；`--apk/--test-apk` 存在（`:76`）。
- `summarize_kd76.py` 无 `--batch`/无 drift 分组（本轮 `grep batch` 零命中）；轮号必须恰为 1..20（`:483-484`），重复轮 → NOT EVALUABLE（`:390-392`），**多余轮号**（21..40）→ NOT EVALUABLE（`:484-487`）。因此「两次同编号 1..20 各跑一遍再拼」会被结构性拒绝；但「10+10 拼成一个 n=20」会被接受为**单一**样本集，无法从证据层证明它来自两次独立会话。
- `Kd76ReportVerdict.kt:25,31,80-84`：n<20 → NOT_EVALUABLE，门 = 常量 5 000 ms，调用方不可放宽（F11 已收口）。

**结论**：R2 必须先裁定样本口径（10+10 合并 vs 2×20 分别判），再谈「时间分离/冷启动证明/合并规则」；工具补 `--batch A|B` 只对「证明两批存在」有意义，不改变判词。**任何把两个不同 APK 的单批 n=20 相加成 n=40 的写法都不成立**（批次 42 记录 S16 已登记）。

### 1.3 R3：isolation `-4` 根因与 witness

**已确认机制（本轮实读）**

1. `-4` = `Assume.assumeTrue` 失败（`LowLoadProbe.kt:203-211`）；`isolation_pair_report.py:66` 映射 `-4 → SKIP`，`:337-344` 把 SKIP/UNKNOWN 计入 `skipped_or_unknown`，配对不成立。故 `OK (15 tests)` + `skip=15` **不是通过**。
2. 探针**每进程只测一次**并缓存失败原因（`LowLoadProbe.kt:196-215`）→ 一次 `own_cpu_busy` 使 15 条全 `-4`，与用例行为无关。这是「15 例同时零执行」的**结构性原因**（必然性来自缓存，不是来自某个用例）。
3. 探针测量 = `/proc/self/stat` 的 utime+stime（`:156-166`）÷ `windowMs`（`:99-123`），阈值 `OWN_CPU_LIMIT_FRACTION = 0.80`（`:65`，KDoc `:64` 写「单核语义」）。比值是**平均占用核数**（CPU-秒 / 墙钟-秒）——这是正确的量纲，但阈值对「instrumentation 进程自身启动成本」极紧。

**根因假设（推定，可证伪）**：探针窗口与 **instrumentation 启动窗（类加载/JIT/测试框架初始化）重叠**，`own_cpu` 主要是本进程启动成本，而非宿主争用。支持证据（实读 E 文件数值 + 算术）：

| 设备/run | own_cpu_ms | window_ms | 比值 | 判定分支 |
|---|---:|---:|---:|---|
| 5584 ×3（批次 41 D05） | 3190 / 3170 / 3490 | 3136 / 3116 / 3100 | 1.017 / 1.017 / 1.126 | `own_cpu_busy` |
| 5562 r1-OFF / r1-ON（批次 42 WS4） | 5650 / 5090 | 5046 / 4211 | 1.120 / 1.209 | `own_cpu_busy` |
| 5566 OFF / ON（批次 42 设备轨） | 2060 / 2900 | 4888 / 3613 | 0.421 / 0.803 | `jitter_p95_absolute` / `own_cpu_busy`（**贴线**） |
| WS10 一次 | 3260 | 3004 | 1.085 | `own_cpu_busy` |

六次命中比值 **> 1.0**，说明本进程**同时用满约 1 个核以上**；5584 三次几乎相同（3190/3170/3490）说明是**确定性启动成本**，不像随机宿主争用（争用应抖动）。5566 OFF 的 0.42 证明探针**并非必然**触发 own_cpu——这正好给出对照组。

**可证伪的实验设计（R3 应交付）**：
- 同一套件跑三臂：①现状（首个 `@Before` 测）；②首次测量前插入显式 warm-up（如先跑一个空 instrumentation 方法/类加载预触）；③同一进程内**测两次**（首测 + 套件尾测）。
- 判据：若 ②/③ 让 `own_cpu/window` 掉到阈值下 → 根因是启动成本，修法是「基线扣除/稳定窗后测」；若仍 >0.8 → 这是测试自身固有成本，**不得**靠调阈值洗绿，应改为「相对基线」判据并写明代价。
- **禁止**只做「把 0.80 调大」——那是放宽 fail-closed 门，不是修根因。

**witness：能力不存在（本轮 grep 实读）**：`VisualJsIsolationInstrumentedTest.kt`（15 个 `@Test`，`@Before lowLoadGate` 在 `:141-144`）与 `IsolationArms.kt` 对 `filesDir`/`writeText`/`FileOutputStream`/`witness` **零命中**。ON 臂的 `SMB_T4_HOOK: begin`（`IsolationArms.kt:96-104`）在 `base.evaluate()` **之前**打印，而 assumption 在 `@Before` 抛异常 → `begin` 只证明「被调度进入」，**不证明用例体执行**。因此计划 item 19 是**新增能力**，且必须定义三层语义：`scheduled` / `body-entered` / `body-completed`，并让 `isolation_pair_report.py` 解析（当前只解析 gate 行与 instrumentation 摘要，`:111/132/148`）。

### 1.4 R4：干净树发布构建

**已有（可沿用，不必重做）**：`git archive` 副本步骤与规模（`2026-11-04-lockfile-window.md:173-191`；`ws8-release-prefight.md:195`）；release 门清单与命令形状（`2026-11-04-release-readiness.md:375-388,445`）；两次 `--write-locks` 失败点（`ws8:18`）。

**本轮实跑的新事实**：

```text
$ git ls-tree -r HEAD --name-only | wc -l                 → 3397（文件）
$ git archive --format=tar HEAD | tar -t | wc -l          → 3949（含目录条目）
$ git archive --format=tar HEAD | wc -c                   → 471531520（≈471.5 MB）
$ git ls-tree -r HEAD --name-only | grep -c gradle.lockfile → 0
```

- 副本**不含**：`local.properties`（`.gitignore:14`；本机内容仅 `sdk.dir`）、`.gradle/`（`:5`）、`.toolchains/`（`:10`，本机本就不存在）、`.android/`、锁文件（HEAD 0 个）。
- `tools/android-env.ps1` 强制工作区在 `D:\`（`:6-7`）并 SUBST `S:`；它把 `GRADLE_USER_HOME` 指到 `<workspace>/.gradle`（`:197`）、`ANDROID_USER_HOME` 指到 `<workspace>/.android`（`:191`）、`ANDROID_HOME` 指到 `<workspace>/.toolchains/android-sdk`（`:186-189`）。**本机 `.toolchains` 不存在**（`[ -d .toolchains ]` 实跑 → ABSENT；`subst` 无映射），而批次 41/42 门禁日志记录的是 `ANDROID_HOME=C:\Android\Sdk`（`build/batch41-strong.log:6`，转录）→ **哪套 env 是规范入口未定**，属 R4 必须先钉的前置。
- **Gradle 9.6.1 分发与依赖缓存都在工作区**：`.gradle/wrapper/dists/gradle-9.6.1-bin/.../gradle-9.6.1/bin/gradle` 存在、`*.zip.ok` 存在；`.gradle/caches/modules-2` 有 144 个 group 目录。用户主目录 `~/.gradle` **没有** `wrapper/dists`。
- 结论：**新目录 + 默认 env 下 `./gradlew` 会尝试联网下载 `gradle-9.6.1-bin.zip`**（本地只剩 `*.zip.ok` 标记，zip 本身已删；wrapper 下载是否受 `--offline` 约束为**未逐字复核**的 Gradle 行为，须在门禁阶段实测）→ 计划 item 37 的「离线构建」只在两种情况下可行：(a) 把工作区 `.gradle` 复制/链接到副本或显式 `--gradle-user-home`/`GRADLE_USER_HOME` 指向它；(b) 允许首次联网。**这是 R4 必须写明的步骤，否则「离线 exit 0」会被误读为环境无关。**
- release 变体硬门：`app/build.gradle.kts:125-152` 读 4 个 `RELEASE_*` 环境变量；`:158-166` 所有 `*Release*` 的 `bundle/assemble/package` 任务 `dependsOn("validateReleaseSigning")`，缺变量**直接失败**。因此干净副本要出**签名** release APK 必须注入签名；不出签名只能构建 debug/localFirst flavor。
- 依赖锁定：根 `build.gradle.kts:37-38` `lockAllConfigurations()`，HEAD **无**锁文件 → 副本解析走 DEFAULT 模式（无锁可用），**不产生** KD-72 的钉版证据；这与「干净树 + 锁文件一起入库」的 KD-72 目标仍不一致。
- 其它前置：JDK 21（`java -version` 实跑）、`C:\Android\Sdk` 存在、用户 `~/.gradle/gradle.properties` 写死 `-Djava.io.tmpdir=D:/gtmp`（`/d/gtmp` 存在）——**换机/换目录必须重设**，属 runbook 项。
- **最关键的语义风险**：工作树 511 条未提交改动 → `git archive HEAD` 得到的是**不含本批改动**的源码。任何「干净树构建的 APK」必须指名它对应哪个提交；本批若未提交就构建，出来的是批次 42 收口的代码。

**结论**：R4 不是「可行性未知」，而是「可行性有前提、前提可枚举」。决定性验证（`--offline` exit 0、mapping/checksum、确定性 `cmp`）仍须门禁阶段实跑，本轮不跑。

### 1.5 R5：`docs/current` 7 M 与并入协议

**本轮实跑/实读**：

```text
$ git diff --stat -- docs/current
 7 files changed, 99 insertions(+), 5 deletions(-)
$ git apply --check docs/research/2026-11-04-contract-v1.1-p0-applicable.patch
 exit 0（未应用、未写盘）
$ grep "^+++ \|^--- " …p0-applicable.patch
 --- a/docs/current/visual-generation-contract.md
 +++ b/docs/current/visual-generation-contract.md
```

- 7 个 M 的归属：本轮实跑 `git diff --stat` + 实读 `architecture-contract.md` 全 diff（新增 `## Planned visual runtime (4E, not implemented)` + 依赖图）；其余 6 个文件的内容归属引基线件 §4 的逐文件表（**转录**）——结论一致：**无一属 KD-75/76/isolation**。
- patch 目标文件 `visual-generation-contract.md` **不在** 7 M 内且工作树干净 → 「逐文件冲突清单」为空；**不可应用的理由只剩热区纪律**，不是冲突。
- 并入前置与顺序已有：`2026-11-03-contract-v1.1-p0-seams.md:19-31`（C-1 → C-6 → C-10，三段同批 + 台账同步 §5）。
- **唯一实质冲突**：`data-schema.md` 新增段仍写「Room 67 / 请求 17」，与现 73/19 冲突；整并入会造两套版本号，与四方门同源要求相悖（基线 §4 已登记，我实读 diff 确认）。

**结论**：R5 只需一页「并入协议」的裁定文本（应用时点、是否同批修 data-schema 陈旧版本、台账同步清单），无需新调研。计划 item 45 的「否则出逐文件冲突清单」在本树上的答案是**空清单**。

### 1.6 R6：计划收尾盘点

- 盘点件存在且已入库：`docs/research/2026-11-04-batch-42-inventory.md`（371 行，§8 剩余 73 项 / §9 分派 / §10 批次 43 草案）。
- **但它是批次 42 回滚前写的**，与不可变清单冲突的地方必须显式作废：
  1. `inventory:232-240`（§9.1）把「6C-A1 6C-2 重裁 JVM+设备」列为**本线可做**；
  2. `inventory:290-303`（§10.2）WS1 写「6C-2 **五 mask** 的 JVM/设备矩阵」「mask=8 证据闭包」；
  3. `inventory:309-315`（§10.4/§10.5）验收要求「mask=0 不删本机内容、mask=8 证据闭包」。
  以上三项均与 `plan:5` 不可变清单（mask=8 不提供、mask=0 禁止、本批只可加防漂移测试）**直接冲突**，以计划 §5/§4 为准。
- 盘点的其余部分仍可用：§8 的 73 项分类、§9.2 需用户 5 项、§9.3 外部 6 项、§9.4 他线 7 项、§9.5 热区 25 项。
- 数字口径：计划 §2 实列 **78** 项（本轮 `python -I` 实跑：`count 78 missing [] dups []`），计划 §2 标题与合计行仍写 74（`plan:24,131`）→ 实际账 6+78+20 = **104**（基线 F1 成立）。记录按 78 行对账，不改计划文件。

---

## 2. 冲突与发现清单

| # | 发现 | 证据 | 严重度 | 处置 |
|---|---|---|---|---|
| **C1** | **A6 样本口径冲突**：权威预注册「两批各 10、合并 n=20」vs 计划「两次各 20、各自判词、不合并」 | `2026-11-01-kd76-stats.md:268`、`:235`；`plan:39-43`；`2026-11-04-kd76-20-rounds.md:121-137` | **高（判据）** | 主会话二选一并冻结；选 2×20 必须重写 pre-registration 后才跑（A10） |
| **C2** | **C4 定义冲突**：计划把 C4 当「产品进程首请求」，权威 C4 = 死亡确认 2 000 ms；代码只有布尔无 ms | `plan:10`；`2026-11-01-kd76-stats.md:169,273`；`VisualJsConnectStageCalibrationInstrumentedTest.kt:516`；`kd76_chain_segments.py:138-140` | **高（判据）** | 裁定 C4 按 stats 定义；item 16 要么加夹钟 seam，要么如实标「无 ms」 |
| **C3** | **盘点件仍主张重裁 6C-2**，与不可变清单冲突 | `inventory:232-240,290-303,309-315`；`plan:5` | **高（纪律）** | 在记录里显式作废这三段；不得据其排期 |
| **C4** | **6C-2 文档层仍有未提交的「0/8 合法」残留**（行为层正确） | `core/domain/.../BackupRepository.kt:33,58`（`git diff` 2/2 行，**未提交**；HEAD 原文是「拒绝 0/8」）；`app/.../SecondaryScreens.kt:1195,1205-1210,1249,1258`（42+/35−，未提交）；行为层 `SmbkSelectionPlan.kt:79-81,637-642`、`BackupSnapshotTrimmer.kt:399-402` 仍拒绝 | **高（文档一致 + 热区）** | 行为层无漂移（可写「6C-2 未变」）；`core/domain` 是热区 → item 63「三方一致」**今天必红**，只能由主会话授权清残留或把断言范围限定为非热文件 |
| **C5** | **WS9 items 61–62 已有三重覆盖**，新增测试需重新指认消灭的失败（§12.2） | 单测：`SmbkSelectionPlanTest.kt:444`（allowedMasks 精确相等）、`:458-473`（0/8 与其余 13 个 mask 逐个拒绝）；域层 `BackupSelectionTest.kt:31,47,55-60`；门：`check_backup_plan_sync.py` 输出含 `allowedMasks == [9,13,15]`（基线件 §1.5 实跑，**转录**） | 中（机制重复） | 要么把新测试指向**未覆盖面**（恢复入口/设备/UI），要么合并为「同一向量在多层的断言」，不写重复机制 |
| **C6** | **witness 不存在**；`begin` 不能证明用例体执行 | `IsolationArms.kt:96-104`；`VisualJsIsolationInstrumentedTest.kt:141-144`；本轮 grep 零命中 | **高（能力缺）** | item 19 按「新增能力」排期；先定义三层 witness 语义 |
| **C7** | **离线干净构建的前置未写明**：Gradle 分发与依赖缓存都在工作区 `.gradle`（用户主目录无 `wrapper/dists`），`local.properties` 不入库；且 `android-env.ps1` 与门禁日志的 `ANDROID_HOME` 不一致 | 本轮实跑：`.gradle/wrapper/dists/gradle-9.6.1-bin/.../bin/gradle` 存在、`~/.gradle/wrapper/dists` 不存在；`tools/android-env.ps1:197`；`.gitignore:5,10,14` | 中 | R4 写「缓存复用」步骤；否则 item 37 的「离线」不可复现 |
| **C8** | **DOCX 导出无 /AIGC 机制**，C2 若只做 PDF 会被误读为全覆盖 | `MistakeDocxExporter.kt:59,77`；`SmartMistakeBookApplication.kt:438`；`/AIGC` 注入仅 `MistakePdfExporter.kt:233-248` | 中 | R1 写明「C2 = PDF 链」；DOCX 单列 |
| **C9** | 计划 item 53 的「S9/S12」编号与实际内容不符（S9=WS6 设备断言目标错；S12=isolation 零执行；而「懒开库装置缺陷」是批次 42 §2.2/§6.2 的未编号缺陷） | 批次 42 记录 `:275,411`、`:449`；`plan:91` | 低 | 记录里改按**内容**指认，避免三处 S 编号互相顶替 |

---

## 3. 硬约束清单（本批可执行口径）

> 每条给出处与「怎么被抓住」，不新增机制。

- **H1（不可变）** 6C-2 三项不变：`ALLOWED_MASKS == {9,13,15}`（`BackupSelection.kt:166`、`SmbkSelectionPlan.kt:79-81`）、mask=8 不提供、mask=0 禁止（`SmbkSelectionPlan.kt:637-642`）、派生索引恒裁（`BackupSnapshotTrimmer.kt:399-402`、`SmbkSelectionPlan.kt:194`）。本批**只**允许新增防漂移断言，不改行为、不改 schema、不改 `availableMethods`。
- **H2（热区）** `core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、Application/Root 禁改。**已知冲突**：`core/domain/BackupRepository.kt`（未提交 0/8 KDoc）只能由主会话授权后清；`app/.../SecondaryScreens.kt` 非热区但含 UX 回归（`:1149` 去掉 `removable` 锁后点 0/8 被静默吞掉、原因文案被删），**本批不回滚也不提交**。
- **H3（core/export 范围）** 计划 §4 原文是「`core/export` 只做 KD-75 相关最小 hunk」（`plan:137`），**不是**「只做 MistakePdfExporter」；但 C2/C4 实际会触 4 个构造点 + 3 个指纹前缀 + 哨兵（§1.1），属**数据模型级**改动，须主会话先授权范围，再按「先红用例 → 改」推进。
- **H4（C2 来源规则）** provenance 只从 `state.questionDocument.blockEvidence` 派生（`MistakePdfEligibility.kt:60-87`），**禁止**在导出层重读文档；判据保守（任一 `MODEL_DOCUMENT_PARSE` 即打标）；C2 作用域 = PDF，DOCX 显式排除并单列缺口。
- **H5（KD-76 样本）** 在 C1 裁定前**不得开跑任何 A6 批次**；不得把两次不同 APK 的单批 n=20 相加；每轮必须有 host `boot_id` 且 before==after（`summarize_kd76.py:483-487,413-446`）；`n<20` 只能 NOT_EVALUABLE（`Kd76ReportVerdict.kt:80-84`）。
- **H6（KD-76 链段）** C4 未夹钟前，报「四段实测」必须写「C4 无 ms、仅 2 000 ms 预算 + 布尔」；Σ 四段 max 只能作上界，不加 p95、不做单例（`2026-11-01-kd76-stats.md:273`；批次 42 记录 §1.2 item 31）。
- **H7（isolation 口径）** `OK (15 tests)` 与 `skip=-4 ×15` 并存时**不得**写通过；报告必须并列 `code0 / -2 / -1 / -4`；witness 未落地前不得写「用例已执行」。
- **H8（设备绿绑定）** 任何设备结论必须绑 APK sha + 源快照；门禁阶段一旦改代码/重建，此前设备证据作废（批次 42 §0.3 先例）。
- **H9（发布）** 不写生产密钥；release 打包需 4 个 `RELEASE_*`（`app/build.gradle.kts:125-152,158-166`）；「发版就绪」至少需 干净树 + 签名 + arm64 三项，arm64 在当前 x86_64 宿主 **BLOCKED**（基线 §6.2 原文 `FATAL … not supported by the QEMU2 emulator on x86_64 host`）。
- **H10（docs/current）** 只出 patch、不应用（热区）；若日后并入，必须同批修 `data-schema.md` 的 67/17 → 73/19，并同步台账（`contract-v1.1-p0-seams.md:120-133`）。
- **H11（门禁纪律）** Gradle 只在门禁阶段串行；禁 `:app:connectedLocalFirstDebugAndroidTest`；不提交锁文件（本轮实测 3 个未跟踪 `gradle.lockfile`）；不提交 `.gradle`/`.toolchains`/`local.properties`/密钥。
- **H12（新增必指认）** 每个新增测试/机制必须指名它消灭的**具体失败**；WS9 items 61–62 若照字面新增即为重复机制（C5），须改指向或合并。
- **H13（计数）** 记录按 **78** 个实施项对账（6+78+20=104），「74/100」登记为计划口径缺陷（第三批同族）。
- **H14（外推禁令）** 「isolation 因果」「T4 排除」「28 套件全绿」「KD-75 已关闭」「KD-76 A6 已满足」在本批证据下均不成立；只能写实测到的那一层。

---

## 4. 对批次 43 基线件（F1–F7）的交叉复核

| 基线发现 | 本轮结论 | 依据 |
|---|---|---|
| F1 计划实列 78（自述 74） | **成立** | 本轮 `python -I` 实跑：`count 78 missing [] dups []`，行号 27..126 |
| F2 C4 定义冲突 | **成立** | `2026-11-01-kd76-stats.md:169,273`；`VisualJsConnectStageCalibrationInstrumentedTest.kt:516` |
| F3 回滚只回滚域层，文档层残留 | **成立**，并补两点：① `BackupRepository.kt` 的 0/8 句是**未提交**工作树改动（HEAD 原文正确）；② `SecondaryScreens.kt` 不在热区，但 `:1149` 去掉 `removable` 后 0/8 点击**静默无效**、原因文案被删，属 UX 回归 | 本轮 `git diff`（2/2 行、42/35 行）+ 读 HEAD 原文 |
| F4「item 4 与计划 §4『只做 MistakePdfExporter』冲突」 | **需修正**：计划 §4 原文为「`core/export` 只做 KD-75 相关最小 hunk」；「只允许 `MistakePdfExporter`」是 `inventory:312` 的措辞。冲突真实存在，但**引错了约束来源** | `plan:137` vs `inventory:312` |
| F5 witness 不存在 | **成立** | 本轮 grep 零命中；`IsolationArms.kt:96-104` 证明力边界 |
| F6 patch 可干净应用、7 M 属他线 | **成立** | 本轮 `git apply --check` exit 0；`git diff --stat` 99/5；实读 `architecture-contract.md` diff 为 4E 规划段 |
| F7 S10 漂到 `:3296`、S11/S12 未修 | **成立** | 本轮实读 `known-defects.md:3278-3300`：KD-83 段起 `:3282`、`关闭` 块 `:3294`、`:3296` 仍挂 KD-75 正文（无 `## KD-` 标题） |

---

## 5. 本轮实跑命令与关键输出（可重跑）

```bash
python -I -c "…re.match(r'^(\d+)\.\s', line)…" .zcode/plans/plan-2026-11-05-batch-43.md   # count 78 / missing [] / dups []
git rev-parse HEAD                                    # 0fef943641f53560b92ce2afec4da22bf52f5e86
git status --porcelain | wc -l                        # 511
git status --porcelain -- docs/current core/domain core/ui core/export   # 7 docs/current + 2 core/domain + 1 core/ui + 3 core/export(全 test) + 3 未跟踪 lockfile
git diff --stat -- docs/current                       # 7 files, 99 insertions(+), 5 deletions(-)
git apply --check docs/research/2026-11-04-contract-v1.1-p0-applicable.patch   # exit 0（未应用）
grep "^+++ \|^--- " docs/research/2026-11-04-contract-v1.1-p0-applicable.patch # 仅 visual-generation-contract.md
git diff -- core/domain/src/main/kotlin/.../BackupRepository.kt               # 2/2 行（0/8 KDoc）
git diff -- app/src/main/kotlin/.../SecondaryScreens.kt                       # 42/35 行
git show HEAD:core/domain/src/main/kotlin/.../BackupRepository.kt | sed -n '28,40p'  # HEAD 仍是「拒绝 0/8」
git ls-tree -r HEAD --name-only | wc -l               # 3397
git ls-tree -r HEAD --name-only | grep -c gradle.lockfile   # 0
git archive --format=tar HEAD | tar -t | wc -l        # 3949
git archive --format=tar HEAD | wc -c                 # 471531520
ls .gradle/wrapper/dists/gradle-9.6.1-bin/*/gradle-9.6.1/bin/gradle   # 存在
ls ~/.gradle/wrapper/dists                            # 不存在（No such file or directory）
ls .gradle/caches/modules-2/files-2.1 | wc -l          # 144 个 group
grep -rn "apkSha256" app/src/androidTest/.../VisualJsConnectStageCalibrationInstrumentedTest.kt  # :195/:248/:323/:415/:719
grep -n "batch" tools/ci/summarize_kd76.py            # 零命中（无 batch 概念）
```

> 本轮**未**运行 Gradle / adb / `am instrument` / emulator / Provider，也**未**复跑 `tools/ci/check_backup_plan_sync.py`（该门输出引自基线件 §1.5，转录而非本轮实跑）。

---

## 6. 未验证 / 边界

| 项 | 状态 | 原因 |
|---|---|---|
| A6 两种口径的裁定 | **待主会话** | 计划与权威预注册互斥（C1） |
| C4 毫秒分布 | **UNVERIFIED** | 代码只有布尔（`VisualJsIsolationInstrumentedTest.kt:516`） |
| isolation 根因 | **推定（未证）** | §1.3 的启动成本假设需三臂实验；本轮不跑设备 |
| witness 设计的三层语义 | **未实现（能力缺）** | 本轮只给语义边界，落地属 WS3 |
| `--offline` 干净树构建 exit 0 | **未跑** | 属门禁阶段；本轮只核前置（分发/缓存/SDK/签名） |
| release 签名 / arm64 16KB 运行时 | **BLOCKED** | 需密钥与 arm64 宿主（基线 §6.2） |
| KD-75 C1/C3/C5–C8 设备判定 | **UNVERIFIED** | 本轮未跑设备，仅实读既有 E 文件数值 |
| `SmbkSelectionPlanTest` / 单测门 | **未跑** | 本轮禁 Gradle；C5 的结论基于源码实读 |
| 7 M 是否会在他线继续变动 | **未知** | 共享工作树，本快照 mtime 全为 2026-11-01 14:07 |

---

## 7. 体量自查与回链

- 回链：`plan-2026-11-05-batch-43.md` §1/§2/§4/§5；`2026-11-05-batch-43-baseline.md` §2/§3/§4/§7；批次 42 记录 §9.2/§9.3/§9.5；`2026-11-04-kd76-20-rounds.md` §2.6/§5；`2026-11-01-kd76-stats.md` §4.1/§5/§7；`2026-11-02-pdf-incremental-aigc.md` §3.7；`2026-11-03-contract-v1.1-p0-seams.md` §1/§5/§6；`2026-11-04-batch-42-inventory.md` §8–§10；`2026-11-04-lockfile-window.md` §3/§6；`2026-11-04-release-readiness.md` §6.2。
- 体积（落盘后实测）：`wc -lc` → **268 行 / 33,158 B**，在 evidence 规范上限（400 行 / 64 KiB）内。
- 本文件**不替代主会话裁定**，也不改任何计划/代码/台账。可写的唯一硬结论：**六题中 R5 已够用；R1 主体够用、需补传播面与边界；R3、R4 需新研究；R2 的 A6 样本口径与 C4 定义、R6 的盘点件 6C-2 段均为冲突，须先裁定再实施。**
