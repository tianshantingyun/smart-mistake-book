# KB B35 渲染 v3：第二材料槽（异型）+ 2k 逐行预算 + 截断注 + 设备脚本先落盘：预注册（2026-11-06）

> **性质**：出数前冻结。§1–§4 写死；执行轮只允许在文末追加 §6，不得回改 §1–§4。
> **写作时点**：2026-11-07T11:43:32+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-06` 是批次日（沿用批次命名先例）。
> **HEAD**：`ba7b5624ea0c2bc3f09dcee247f5852e4f428122`（`git rev-parse HEAD` 实跑；本会话写作起点 HEAD 已从 `3007a2bc…` 前进——B35 导入臂（`docs/research/2026-11-06-kb-b35-import-readpath-prereg.md`，冻结 sha `db25508e…`）的读路径修复已入树，证据：`port/KnowledgePort.kt:59,70,232` 返回 `KnowledgeRecallResult`，`GoldenRetrievalInstrumentedTest.kt:124` 已用 `.candidates` 访问器）。
> **上游**：`docs/research/2026-11-03-kb-b34-render-v3-prereg.md`（下称 **b34-render**：第二槽/2k 预算/截断注/广告同步的冻结设计，本文件原文收编并更新行号）；`docs/research/2026-11-02-kb-b33-render-v3-prereg.md`（R3-v1，QR 依赖收窄的出处）；`docs/kb-batch34-2026-11-03.md` §2.8/§7#12/§10.5（W8 parked 与"三次未落地"的残留）。
> **本步唯一新增写盘**：本文件。未改代码/内容包/脚本；§0 的读数是本会话 `python -I` / `sha256sum` / `git` / `grep` 实读实算。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-07T11:43:32+0800
git rev-parse HEAD                             # ba7b5624ea0c2bc3f09dcee247f5852e4f428122
git status --porcelain | wc -l                 # 573
ls docs/research/2026-11-06-kb-b35-render-v3-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 573 项脏项属他线/已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`、`stash` 或 `reset`。
- 触碰面内已有他线 `M`（写前实读）：`core/data/.../study/RoomTutorToolRunner.kt`、`core/database/.../RoomStudyDatabase.kt`。§4.1 的 pre-sha 是**工作树值**；执行轮在其上编辑，不得回退。
- 与导入臂的共面（见 §0.4）：`RoomKnowledgeBaseStore.kt`、`port/KnowledgePort.kt`、`RoomStudyDatabase.kt` 已含导入臂改动（干净、已提交）；`RoomStudyDatabase.kt` 上另有未提交改动。

### 0.2 现状（源码实读，写前工作树值；行号为当前值）

| 事实 | 出处 |
|---|---|
| 每节点**恰一条**材料：`nodeMaterialRecords` → `readBestKnowledgeTeachingMaterialPerNode`（相关子查询 + `LIMIT 1`，minSdk 23 无窗口函数） | `RoomTutorToolRunner.kt:504-513`；`KnowledgeTeachingMaterialDao.kt:70-126`（sha 与 b34 pre 相同 `0a29d863…`，行号沿用） |
| 渲染：`"$prefix｜材料《${title}》" + excerpt`，标题原样、节选 ≤**120** 字可收缩 | `RoomTutorToolRunner.kt:476-483` |
| 预算：`BudgetedLines(MAX_TOOL_RESULT_CHARS − TRUNCATION_NOTE_RESERVE_CHARS)` = **2,000 − 240 = 1,760** | `RoomTutorToolRunner.kt:458`、`:75`；`MAX_TOOL_RESULT_CHARS = 2_000`（`TutorToolLoop.kt:489`） |
| 逐行裁剪：`addWithTrimmableExcerpt` 先缩节选、缩不下才整行丢；两种都计数 | `RoomTutorToolRunner.kt:929-949` |
| 截断注：`已截断：{dropped} 项未显示，{trimmed} 行节选缩短。` | `RoomTutorToolRunner.kt:491-492` |
| 无材料标记：`（该节点无材料）`（常量）；`$prefix｜$marker` | `RoomTutorToolRunner.kt:528`、`:472-473` |
| 展示节点数 5；节选上限 120 | `RoomTutorToolRunner.kt:522`、`:525` |
| 工具广告（长/短）仍是「绑定材料《标题》与正文节选」「材料《标题》」（单数） | `TutorToolDescriptions.kt:21-24`、`:102-103` |
| 广告快照逐字钉住上述两段 | `TutorToolDescriptionsSnapshotTest.kt`（sha `47c660cf…` = b34 pre，引用沿用） |
| 现有测试钉「单查询 5 个节点 → 材料段必须恰好 5 条」 | `UsageMaterialAllocationTest.kt:317` |
| 设备侧 KNOWLEDGE_READ 执行先例 | `RoomModelTaskToolLoopInstrumentedTest.kt:545-623` |
| 设备侧生产装配先例（探针仪器复用） | `GoldenRetrievalInstrumentedTest.kt:106-114`（`StudyDatabaseFactory.open` + `DenseRecallAssembly.reranker` + `BundledKnowledgeBaseInstaller.install`） |
| **设备探针脚本不存在** | `ls tools/kb_perf/` → 只有 `__pycache__/`、`recall_read_cost.py`；`first_query_20x.sh` **不存在**（b34 W7/W8 因此 NOT_RUN/parked） |
| 导入臂已落 | `KnowledgePort.kt:59,70,232`（`KnowledgeRecallResult` + `indexComplete`） |

### 0.3 静态可达性与预算（沿用 b34-render §0.3 的实算；背书 = 内容逐字节未变）

本会话实读主包 sha 前 20 位 `10ede1bf449de38ff21a`，与 b34-render §3.3 #5 登记的 `10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670` 逐字节同源 ⇒ 内容未变，静态数照抄：

| 项 | 值 |
|---|---:|
| 节点 / 材料（2025 sidecar） | 3,280 / 64,853 |
| 节点 ≥2 类型（异型第二槽可达） | **2,231** |
| ≥2 材料但只有 1 类型（无异型候选） | **811** |
| 节点材料数 = 1 | **238** |
| 材料标题最大长度 | **183** 字 |
| 正文预算 | 2,000 − 240 = **1,760** |
| 5 节点首选槽地板 | 5 × **61** = **305** 字符 |

- 第二槽只在 2,231/3,280 节点可达；238 + 811 个节点只有首选槽，**不是缺陷**。
- 执行轮若内容包升级，必须重算本表，不得沿用。

### 0.4 与导入臂的共面协调（写死）

1. **执行顺序**：本臂在导入臂（读路径只读化）**之后**执行；§4.1 #7/#8/#9 的 pre-sha = 写前工作树值（已含导入臂的 `KnowledgeRecallResult` 面）。
2. **回滚边界**：本臂回滚**不得**回退导入臂的读路径修复；共文件按"恢复到本臂写前实测值"处理（该值已含导入臂改动）。
3. `KNOWLEDGE_READ` 渲染消费的召回结果从 `List` 变为 `KnowledgeRecallResult`（`.candidates`）——本臂的渲染改动叠加在其上；若执行轮发现渲染段编译与此冲突，**停**并登记，不得改导入臂语义来迁就渲染。

### 0.5 b34 三次设备失败的教训 → 本批新纪律（写死）

b34 三个设备臂同因失败（`docs/kb-batch34-2026-11-03.md`）：

| 臂 | 失败形态 | 出处 |
|---|---|---|
| W6 路由 v2 | **ABORT**：`tools/kb_coverage/routing_eval_v2.py` 不存在——"静态门不存在，不得跑设备" | `:147-150` |
| W7 材料候选 | **blocked**：`tools/kb_perf/first_query_20x.sh` 不存在、`FirstQueryDuration` 无发出点、候选仪器缺失 → G-FIRST/zero-drift `NOT_RUN` | `:161-165` |
| W8 渲染 v3 | **parked**：JVM 绿但设备门全没跑，按"任何节点不满足即不采纳"回滚 | `:174` |

**本批纪律（本预注册的核心新增，写死）**：

1. 设备步骤**全部**写成可执行脚本（§3 逐字正文），执行轮**第 0 步**落盘并 commit（"先落盘"）——任何构建/安装/instrument 之前。
2. **门 0（脚本静态门）**：三脚本存在 + `bash -n` 通过 + `python -m py_compile` 通过。任一缺失/失败 ⇒ **ABORT**，`run.status` 写 `reason=缺少 <路径>：设备脚本未落盘（b35-render §3）`，**不得现场手敲命令代替脚本**。
3. 脚本正文在 §3 逐字冻结；执行轮发现环境不兼容（Windows Git Bash 差异等）只能改脚本文件并**在 §6 登记改动 + 重跑门 0**，不得改语义（输入/输出/退出码契约）。
4. 脚本只做编排与机械判读，**不出采纳判词**；采纳按 §2.5 由执行轮对照判据落笔。

---

## 1. 渲染 v3（冻结；b34-render §1 原文收编）

### 1.1 第二材料槽（异型各一条）

1. **首选槽（primary）**：现行 identity 教学序（`role → material_type → title → material_id`）最优材料；**选择语义零改**（仍走 `readBestKnowledgeTeachingMaterialPerNode`）。
2. **第二槽（secondary）**：同节点上，**类型不同于首选**的最优材料（同 identity 键序）；**同型不补**。
   - 节点无异型候选（238 单材料 + 811 单类型）→ 第二槽为空，只渲染首选；
   - 同一材料不得出现两次；两条都必须绑定在该节点。
3. **取数**：新增端口 `readTeachingMaterialCandidatesPerNode(subject, nodeIds)`，返回 `Map<nodeId, List<MaterialRecord>>`（identity 键序全部候选）；Kotlin 侧选首选与异型次选。不复用 `LIMIT 1`，不把类型秩塞进 SQL。
4. **上限/数量**：每节点 ≤ **2** 条；节点仍 **5** 个；材料总数 **5–10**。
5. **第二槽行形状**：`"${marker}·补充《${title}》" + excerpt`——不重复节点名称与 boundary；`marker` 与首选行同一代号。
6. **无材料节点**：只输出 `$prefix｜（该节点无材料）`。
7. **有材料但无异型**：只渲染首选行；**不**输出无材料标记。

### 1.2 预算与逐行裁剪（冻结）

- 正文预算 = 2,000 − 240 = **1,760**；**两遍渲染**：先 header + 5 条首选，再 5 条第二槽；首选先占预算。
- **首选槽地板** `MIN_PRIMARY_EXCERPT_CHARS = 61`：首选节选不得被裁到 60 及以下。让步顺序写死：① 不渲染任何第二槽；② 首选 boundary 截到 0；③ 截短 `《标题》`（保留 ≥1 字前缀 + `…`，仍以 `《…》` 包裹）；④ 仍不够 → 该首选行整行丢弃（记 `dropped`；判据失败，不是可接受形态）。
- **第二槽**：可用预算 ≥ skeleton+1 才渲染，节选收到剩余长度（≤120）；否则整行丢弃。第二槽标题**不**参与让步。
- 逐行成本沿用 `line.length + 1`；同一套 `BudgetedLines`。
- 计数：`droppedCount`（整行丢）、`trimmedCount`（节选缩短）、`titleTrimmedCount`（标题截短，新增）。

### 1.3 截断注（冻结）

- 原模板不变：`已截断：{dropped} 项未显示，{trimmed} 行节选缩短。`
- 仅当 `titleTrimmedCount > 0` 时追加 `，{titleTrimmedCount} 行标题缩短。`（同一行紧接原句）。
- 无任何裁剪 → 不输出截断注。

### 1.4 无材料标记（冻结，逐字不变）

- `（该节点无材料）` 逐字不变；不得改成"暂无材料"等。
- 有材料但第二槽为空 → 只渲染首选，**不**输出标记。

### 1.5 广告同步（冻结，逐字）

**长描述**（`purposeDescription`，`TutorToolDescriptions.kt:21-24`）：

```text
读取这道题相关知识点讲解材料（返回**代号**+名称+边界（≤80字）+**每节点最多两条**绑定材料（首选+补充；各含《标题》与正文节选（≤120字，公式/句界安全截断））；无材料节点显式标注「（该节点无材料）」；代号本会话有效，可直接作为 MASTERY_UPDATE 的 terms）
```

**短描述**（`nativePurposeDescription`，`:102-103`）：

```text
读取当前题相关知识点讲解材料（返回代号+名称+边界+每节点最多两条材料《标题》与正文节选≤120字；无材料显式标注）
```

- 快照测试两段同步为上述逐字文本；`RoomTutorToolRunner.kt:385` KDoc 同步为「每节点最多两条（首选+异型补充）」。
- 广告不得继续出现「一条材料」「绑定材料《标题》」（单数）描述结果形态——这是本臂要消灭的契约漂移。

### 1.6 每项新增消灭的具体失败（b34-render §1.6 原文沿用）

异型第二槽（单一视角拿不到第二视角）；同型不补（两条几乎一样的噪声）；61 地板+两遍渲染（第二槽挤垮首选）；标题让步（183 字标题让"每节点 >60"不可满足）；截断注+标题计数（标题截短静默变形）；广告同步（说一条给两条）；候选查询替代 `LIMIT 1`（结构上拿不到第二条）。

---

## 2. 判读（写死）

### 2.1 每节点必含 title，且首选节选 ≥61

- 每个有材料的渲染节点：至少一条材料行 `《…》` 非空，且**首选槽**节选长度 **≥61**（UTF-16 码元）。
- 无材料节点不参与本判据，只要求 §2.3 逐字标记；不得编造材料。
- 第二槽不参与（节选可 1–120 或整行丢）。
- 解析口径：按渲染后字符串，`《`/`》` 之间非空；节选按 UTF-16 长度计。
- **任何节点不满足 ⇒ 不采纳**；"第二槽挤掉首选"不是可接受降级。

### 2.2 总长 ≤ 2,000 字符

- `summaryMarkdown.length <= 2,000`（UTF-16 码元；含 header、截断注、全部材料行，不含工具外层 JSON）。
- 与 `BudgetedLines(1_760)` + 240 reserve 一致。

### 2.3 无材料标记不变

- 无材料节点逐字 `（该节点无材料）`；有材料节点不得出现该标记。
- `prefix` 形状、boundary ≤80 与现状逐字相同。
- `UsageMaterialAllocationTest` 标记用例保持绿；新增"有材料但无异型"用例（不得出现标记）。

### 2.4 契约广告同步

- `TutorToolDescriptionsSnapshotTest` 两段逐字等于 §1.5；测试绿。
- 全仓 grep：`KNOWLEDGE_READ` 相关文案不得再以「一条材料」/「绑定材料《标题》与正文节选」（单数）描述**结果形态**；命中即失败。

### 2.5 槽位合法性

- 每节点材料行 ∈ {0,1,2}；5 个节点行始终存在（材料行或标记行）；材料行总数 ∈ [0,10]（5 节点全有材料时 [5,10]）。
- 同节点无重复材料；两条都绑定该节点；第二槽 type ≠ 首选 type。
- 首选仍是 identity 最优（旧方法逐字不变）；第二槽是异型最优。

### 2.6 首查 p95 不劣

- **G-FIRST**：冷装（`uninstall → pm path 空 → install -t → logcat -c → am start -W`）后 T0 = `am start -W` 调用前；探针发第一次 `KNOWLEDGE_READ` 知识查询直到返回非空；记录 `T0 → 首次非空` 毫秒数。
- N ≥ **20** 次冷装/臂；p95 = nearest-rank；**`p95(post) ≤ p95(baseline)`**（同设备、同内容、同查询；baseline = pre-arm 源码构建的 APK 对，探针代码两臂逐字相同）；`max ≤ 10,000 ms`；baseline p95 不可得时以 **p95 ≤ 5,000 ms** 兜底。
- **口径边界（冻结，如实登记）**：探针仪器自带生产装配（§3.2 先例），读数含仪器内装配开销——**差分可比**（两臂同探针同装配），绝对值不外推产品进程首查。
- **G-READ（诊断）**：首次 `KNOWLEDGE_READ` 工具执行 `invoke → result` p95（N≥20）≤ baseline+10%，`max ≤1,000 ms`；只报告。
- 检索 p95 另报（AC B3：融合 ≤250ms / p50 ≤150ms）；本臂不改召回，红了先查装置，不归因渲染。

### 2.7 口径变更登记（必须写进批报）

- AC 的 F5「材料条数/查询 = 5」被本臂取代为「节点 5 个，材料 **5–10** 条（每节点 ≤2）」；F1/F3 分母不再固定（上限 1,290），本臂不承诺旧读数可比；`UsageMaterialAllocationTest.kt:317` 的「恰好 5 条」必须改为「5–10 条（每节点 ≤2）」。AC 文本更新另立记录步。

### 2.8 装置门（任一门红 ⇒ 不采纳；门 0 红另有 ABORT 纪律）

| # | 命令 | 通过条件 |
|---:|---|---|
| 0 | `bash -n tools/kb_perf/first_query_20x.sh tools/kb_perf/render_capture.sh && python -m py_compile tools/kb_perf/render_judge.py && test -f core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgeFirstQueryProbeInstrumentedTest.kt && test -f core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgeReadRenderCaptureInstrumentedTest.kt` | exit 0（**任何设备步骤之前**；缺 ⇒ ABORT） |
| 1 | `./gradlew :core:model:testDebugUnitTest --tests "*TutorToolDescriptionsSnapshotTest*" --console=plain` | exit 0 |
| 2 | `./gradlew :core:data:testDebugUnitTest --tests "*UsageMaterialAllocationTest*" --tests "*KnowledgeReadRecallBudgetTest*" --tests "*KnowledgeReadSecondSlotTest*" --console=plain` | exit 0 |
| 3 | `./gradlew :core:database:compileDebugUnitTestKotlin :core:database:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest --console=plain` | exit 0 |
| 4 | `./gradlew :app:assembleDebug --console=plain` | exit 0 |
| 5 | `tools/kb_perf/first_query_20x.sh …` + `tools/kb_perf/render_capture.sh …` + `python -I tools/kb_perf/render_judge.py …`（§3） | §2.6 p95 不劣；§2.1–2.3/§2.5 机械判读 exit 0 |

### 2.9 报告义务（§6 至少包含）

- 5 节点 × 2 槽实际渲染文本（逐节点：标题、节选长度、类型、是否第二槽）；首选节选 min/p50/max；总长；dropped/trimmed/titleTrimmed；第二槽可达性（静态 2,231；本次实际）。
- 广告两段逐字 + snapshot 结果；F5 口径变更登记。
- G-FIRST/G-READ 的 N、p50/p95/max、baseline 与 post（逐次样本落盘）；检索 p95。
- 门 0–5 exit code；§4 逐文件 post-sha；脚本若在执行轮被改，逐行 diff 登记。

---

## 3. 设备步骤脚本（先落盘；逐字冻结）

**落盘清单（5 件，执行轮第 0 步创建并 commit，先于一切设备步骤）**：

| # | 路径 | 作用 |
|---:|---|---|
| 1 | `tools/kb_perf/first_query_20x.sh` | G-FIRST/G-READ 冷装 N≥20 编排（b34 W7 缺失件） |
| 2 | `tools/kb_perf/render_capture.sh` | 渲染文本采集编排 |
| 3 | `tools/kb_perf/render_judge.py` | §2.1–2.2/§2.5 机械判读（纯解析，不出采纳判词） |
| 4 | `core/data/src/androidTest/.../knowledge/KnowledgeFirstQueryProbeInstrumentedTest.kt` | 探针仪器（装配先例 `GoldenRetrievalInstrumentedTest.kt:106-114`；KNOWLEDGE_READ 执行先例 `RoomModelTaskToolLoopInstrumentedTest.kt:545-623`） |
| 5 | `core/data/src/androidTest/.../knowledge/KnowledgeReadRenderCaptureInstrumentedTest.kt` | 渲染采集仪器（同上先例） |

- 仪器**契约**（冻结；实现细节允许执行轮落地，契约不得改）：装配走生产路径（`StudyDatabaseFactory.open` + `DenseRecallAssembly.reranker` + `BundledKnowledgeBaseInstaller.install`，**不得**用渲染替身/假 port）；`launchT0` 经 instrumentation arg 传入（宿主 `date +%s%3N` 毫秒）；G-FIRST 轮询周期 250 ms；输出走 logcat 固定 tag。
- `first_query_20x.sh` 逐字正文（冻结）：

```bash
#!/usr/bin/env bash
# G-FIRST / G-READ 首查探针编排（判读口径 = b35-render §2.6；b34-render §2.6 同源）。
# 用法: first_query_20x.sh <post-apk> <out-dir> <pkg> <test-pkg> [serial] [rounds] [baseline-apk]
# 输出: <out-dir>/samples-post.csv / samples-baseline.csv（round,kind,t0UtcMs,firstNonEmptyMs,gReadInvokeMs）
#       <out-dir>/env.txt（逐轮设备指纹与健康门读数）
# 退出码: 0=编排完成；2=前置/健康门失败；3=设备操作失败。本脚本不出采纳判词。
set -euo pipefail
POST_APK="${1:?usage: first_query_20x.sh <post-apk> <out-dir> <pkg> <test-pkg> [serial] [rounds] [baseline-apk]}"
OUT="${2:?missing out-dir}"; PKG="${3:?missing pkg}"; TEST_PKG="${4:?missing test-pkg}"
SERIAL="${5:-}"; ROUNDS="${6:-20}"; BASE_APK="${7:-}"
ADB="${ADB:-/c/Android/Sdk/platform-tools/adb.exe}"
S() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }
mkdir -p "$OUT"
[ -f "$POST_APK" ] || { echo "ABORT: post apk missing: $POST_APK" >&2; exit 2; }
[ -n "$BASE_APK" ] && [ ! -f "$BASE_APK" ] && { echo "ABORT: baseline apk missing" >&2; exit 2; }
# 健康门（冻结口径：loadavg 1min ≤1.0 且 MemAvailable ≥1GiB；逐轮落盘）
health() {
  local la mem
  la=$(S shell cat /proc/loadavg | cut -d' ' -f1)
  mem=$(S shell grep MemAvailable /proc/meminfo | awk '{print $2}')
  echo "$(date -u +%FT%TZ) loadavg1=$la memAvailKiB=$mem" >> "$OUT/env.txt"
  awk -v l="$la" 'BEGIN{exit (l<=1.0)?0:1}' && [ "$mem" -ge 1048576 ]
}
run_round() { # $1=apk $2=kind
  local apk="$1" kind="$2" i comp t0 line
  for i in $(seq 1 "$ROUNDS"); do
    health || { echo "ABORT: health gate red at round $i" >&2; exit 2; }
    S uninstall "$PKG" >/dev/null 2>&1 || true
    [ -z "$(S shell pm path "$PKG" | tr -d '\r')" ] || { echo "ABORT: pm path not empty" >&2; exit 3; }
    S install -t "$apk" >/dev/null || { echo "ABORT: install failed" >&2; exit 3; }
    S logcat -c
    comp=$(S shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$PKG" | tr -d '\r' | grep / | head -1)
    [ -n "$comp" ] || { echo "ABORT: launcher resolve failed" >&2; exit 3; }
    t0=$(date +%s%3N)
    S shell am start -W "$comp" >/dev/null
    line=$(S shell am instrument -w -e launchT0 "$t0" -e round "$i" \
      "$TEST_PKG/com.tingyun.smartmistakebook.core.data.knowledge.KnowledgeFirstQueryProbeInstrumentedTest" \
      | grep -E 'FIRST_QUERY_MS=|G_READ_MS=' | tr -d '\r' | paste -sd';' -)
    [ -n "$line" ] || { echo "ABORT: probe emitted no metrics (round $i)" >&2; exit 3; }
    fq=$(printf '%s' "$line" | grep -o 'FIRST_QUERY_MS=[0-9]*' | cut -d= -f2)
    gr=$(printf '%s' "$line" | grep -o 'G_READ_MS=[0-9]*' | cut -d= -f2)
    echo "$i,$kind,$t0,$fq,$gr" >> "$OUT/samples-$kind.csv"
  done
}
: > "$OUT/env.txt"
[ -n "$BASE_APK" ] && run_round "$BASE_APK" baseline
run_round "$POST_APK" post
exit 0
```

- `render_capture.sh` 逐字正文（冻结）：

```bash
#!/usr/bin/env bash
# 渲染文本采集编排（§2.1–2.5 的原始证据；判读交给 render_judge.py）。
# 用法: render_capture.sh <apk> <out-dir> <pkg> <test-pkg> [serial] [rounds]
# 输出: <out-dir>/render-capture.txt（logcat -d -s RenderCapture 全量）
# 退出码: 0=采集完成；2=前置失败；3=设备操作失败。本脚本不出采纳判词。
set -euo pipefail
APK="${1:?usage: render_capture.sh <apk> <out-dir> <pkg> <test-pkg> [serial] [rounds]}"
OUT="${2:?}"; PKG="${3:?}"; TEST_PKG="${4:?}"; SERIAL="${5:-}"; ROUNDS="${6:-3}"
ADB="${ADB:-/c/Android/Sdk/platform-tools/adb.exe}"
S() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }
mkdir -p "$OUT"; CAP="$OUT/render-capture.txt"; : > "$CAP"
[ -f "$APK" ] || { echo "ABORT: apk missing" >&2; exit 2; }
for i in $(seq 1 "$ROUNDS"); do
  S uninstall "$PKG" >/dev/null 2>&1 || true
  S install -t "$APK" >/dev/null || { echo "ABORT: install failed" >&2; exit 3; }
  S logcat -c
  S shell am instrument -w -e round "$i" \
    "$TEST_PKG/com.tingyun.smartmistakebook.core.data.knowledge.KnowledgeReadRenderCaptureInstrumentedTest" \
    >/dev/null || { echo "ABORT: capture instrument failed (round $i)" >&2; exit 3; }
  echo "===== round=$i =====" >> "$CAP"
  S logcat -d -s RenderCapture | sed 's/\r$//' >> "$CAP"
done
grep -q "RENDER_BEGIN" "$CAP" || { echo "ABORT: no RENDER blocks captured" >&2; exit 3; }
exit 0
```

- `render_judge.py` 逐字正文（冻结）：

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""渲染机械判读（b35-render §2.1–2.2/§2.3/§2.5；不出采纳判词，p95 判读另由执行轮按 §2.6 做）。
用法: python -I tools/kb_perf/render_judge.py <render-capture.txt> <out-json>
退出码: 0=全部机械判据过；1=任一判据红；2=输入畸形（缺 RENDER 块/字段）。
长度口径：优先采信 Kotlin 侧上报的 RENDER_TOTAL_LEN（String.length，UTF-16 码元）；
非 BMP 字符出现时以结构化上报为准并在 JSON 标注。"""
import json, re, sys

MARKER = "（该节点无材料）"

def main() -> int:
    raw = open(sys.argv[1], encoding="utf-8").read()
    blocks = re.findall(r"RENDER_BEGIN (round=\d+ q=\d+)(.*?)RENDER_END", raw, re.S)
    if not blocks:
        print("MALFORMED: no RENDER blocks", file=sys.stderr); return 2
    findings, ok = [], True
    for head, body in blocks:
        total = re.search(r"^RENDER_TOTAL_LEN=(\d+)$", body, re.M)
        if not total:
            findings.append({"block": head, "check": "total", "ok": False, "why": "missing RENDER_TOTAL_LEN"}); ok = False; continue
        total_len = int(total.group(1))
        total_ok = total_len <= 2000
        ok &= total_ok
        findings.append({"block": head, "check": "total<=2000", "ok": total_ok, "value": total_len})
        nodes = re.findall(r"^RENDER_NODE (.+)$", body, re.M)
        if not nodes:
            findings.append({"block": head, "check": "nodes", "ok": False, "why": "no RENDER_NODE lines"}); ok = False; continue
        text_lines = [l.split("RENDER_TEXT|", 1)[1] for l in body.splitlines() if "RENDER_TEXT|" in l]
        text = "\n".join(text_lines)
        for n in nodes:
            f = dict(kv.split("=", 1) for kv in n.split(" ") if "=" in kv)
            idx, has_material, rows = f["idx"], f["hasMaterial"] == "1", int(f["rows"])
            title_len = int(f.get("primaryTitleLen", "-1"))
            excerpt_len = int(f.get("primaryExcerptLen", "-1"))
            ptype, stype = f.get("primaryType", "-"), f.get("secondaryType", "-")
            node_ok = True
            if not has_material:
                node_ok &= rows == 0 and title_len == 0 and excerpt_len == 0
                node_ok &= MARKER in text
            else:
                node_ok &= 1 <= rows <= 2 and title_len > 0 and excerpt_len >= 61
                node_ok &= MARKER not in _node_segment(text, f)
                if rows == 2:
                    node_ok &= stype != "-" and stype != ptype
            ok &= node_ok
            findings.append({"block": head, "check": "node", "idx": idx, "ok": bool(node_ok),
                             "rows": rows, "titleLen": title_len, "primaryExcerptLen": excerpt_len,
                             "primaryType": ptype, "secondaryType": stype})
    json.dump({"ok": ok, "findings": findings}, open(sys.argv[2], "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    return 0 if ok else 1

def _node_segment(text: str, f: dict) -> str:
    """该节点代号行起、下一节点代号行（或文末）止的片段——标记归属检查用。"""
    code = f.get("code")
    segs = re.split(r"(?=^\d+\. )", text, flags=re.M) if code is None else re.split(
        rf"(?=^{re.escape(code)}\. )", text, flags=re.M)
    return segs[-1] if segs else text

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr); sys.exit(2)
    sys.exit(main())
```

- **判读归属边界（如实登记）**：`MARKER in text` / `_node_segment` 是文本级近似判读；逐节点标记归属的**权威**用例在 JVM（`KnowledgeReadSecondSlotTest`，§2.3）。judge 的角色 = 设备原始证据的机械复核，不替代 JVM 钉。
- **保留规则（对 b34 §3.4 的有意偏离，写死理由）**：采纳与回滚都**保留** §3 的 5 件——它们是设备基础设施，不是臂产物；删掉它们就是复刻 b34 的三次失败。回滚只恢复 §4.1 的 9 件与删除 `KnowledgeReadSecondSlotTest`。

---

## 4. 回滚（触碰清单 + pre/post sha）

### 4.1 A. 既有文件（pre-sha256 = 写前工作树值，本会话 `sha256sum` 实跑；post 由 §6 落）

| # | 路径 | 预期动作 | pre-sha256（实测） | porcelain | post-sha |
|---:|---|---|---|---|---|
| 1 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt` | 两遍渲染、异型第二槽、61 地板、标题让步、`titleTrimmedCount`、KDoc | `01652c2c3df218c9d4f0c09ef9b9996825f1f9326190dc5d0f73d6fb3333b73d` | **`M`（他线，不得回退）** | §6 |
| 2 | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolDescriptions.kt` | 长/短广告两段（§1.5 逐字） | `0aa0b5dd4286de2380c3a378b0da2577a1a870e6b3b430d286533d59a83e9226` | 干净 | §6 |
| 3 | `core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolDescriptionsSnapshotTest.kt` | 两段期望值 | `47c660cfcc291fd691c96ba5d6a09343d2f41e537dfc98ae2ee4a3aed081ccbe` | 干净 | §6 |
| 4 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/UsageMaterialAllocationTest.kt` | 「恰好 5 条」→「5–10 条」；第二槽/标记用例 | `1d96c68304e665702d40bd064a4554eeca73b60f2ec725af4913066370234612` | 干净 | §6 |
| 5 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeReadRecallBudgetTest.kt` | 预算/首节点用例按两槽重算 | `8623a584ccfe8666e8611a30dde3043f78713296fdb2463e229b39ac4388a876`（≠ b34 pre `2dddbf88…`，漂移已登记） | 干净 | §6 |
| 6 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeTeachingMaterialDao.kt` | 新增候选查询（每节点全部候选，identity 序） | `0a29d8634d76eaa46d7f74c82d48565286b0013bf392a706f9af9c7021e5a047` | 干净 | §6 |
| 7 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt` | 候选读取委托（保留旧方法；含导入臂面，不得回退） | `e630d54bb9c66c6feead753d4eb2e24963652ad311a8fccf2a2f0a3969b9c145` | 干净 | §6 |
| 8 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt` | `readTeachingMaterialCandidatesPerNode`（含导入臂面，不得回退） | `86c4aef6aa9350db3f28f6cd73a6c381e4fc9a1fd261806fd8ea97ccf2f3dbe8` | 干净 | §6 |
| 9 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt` | 委托新方法（含导入臂面 + 他线 `M`，不得回退） | `35305f894f95bb5ebcf6728fa199f88781477ffa3a650a497e66bb8cbc65c7e5` | **`M`（他线）** | §6 |

- 与 b34-render §3.1 的 pre-sha 相比，#1/#5/#7/#8/#9 已漂移（他线/导入臂）——以本表为准，b34 值仅作历史引用。

### 4.2 B. 新增文件

| # | 路径 | 动作 | 回滚处置 |
|---:|---|---|---|
| 1 | `core/data/src/test/kotlin/.../study/KnowledgeReadSecondSlotTest.kt` | 两槽/异型/同型不补/单材料/截断注/61 地板/有材料无标记 | 删除 |
| 2 | `core/database/src/androidTest/.../TeachingMaterialCandidatesPerNodeInstrumentedTest.kt` | 真 Room 候选排序与每节点 ≥2 | 删除 |
| 3 | `tools/kb_perf/first_query_20x.sh` | §3 逐字正文 | **保留**（设备基础设施） |
| 4 | `tools/kb_perf/render_capture.sh` | §3 逐字正文 | **保留**（设备基础设施） |
| 5 | `tools/kb_perf/render_judge.py` | §3 逐字正文 | **保留**（设备基础设施） |
| 6 | `core/data/src/androidTest/.../knowledge/KnowledgeFirstQueryProbeInstrumentedTest.kt` | §3 契约 | **保留**（设备基础设施） |
| 7 | `core/data/src/androidTest/.../knowledge/KnowledgeReadRenderCaptureInstrumentedTest.kt` | §3 契约 | **保留**（设备基础设施） |

### 4.3 C. 零触碰

1. `core/data/src/main/resources/knowledge/**`（pack `10ede1bf…`、manifest `28/ce8edb8f…`、dense 资产）——一个字节不改。
2. `core/database/.../KnowledgeSearchFeatureExtractor.kt`、`DenseFusion.kt`、`DenseRecallReranker.kt`、`ProblemOrganizationDao.kt`（b34-render §3.3 原表沿用）。
3. 金标 `tools/kb_coverage/tables/golden_queries_v2.json`、alias_map、学生数据表/备份恢复。
4. 导入臂的读路径修复面（`KnowledgeRecallResult`/`indexComplete`/`verifyKnowledgeSearchIndex`）——不得回退（§0.4-2）。
5. QR 分类器/秩表（若存在）——本臂不依赖，零改。

### 4.4 写前备份 / 回滚 / 采纳

1. 备份 §4.1 的 9 件到 `build/agent-batch35/render-v3-backup/`，落 sha256 全表；执行轮先重跑 §0.1 守卫并登记当轮 pre-sha（与 §4.1 逐件核对；不一致 = 工作树又前进，停并登记）。
2. 回滚：按备份覆盖 §4.1 + 删除 §4.2 #1/#2；**保留** #3–#7（§3 保留规则）；无需清库/迁移（渲染层，无 schema、无内容改动）。
3. 回滚后核验：`UsageMaterialAllocationTest` 回"恰好 5 条"口径、快照回旧两段、`KnowledgeReadSecondSlotTest` 不存在；门 1–2 复跑 exit 0。
4. 采纳：§2.1–2.5 + §2.6 + 门 0–5 全过才采纳；§6 登记全部读数与 post-sha。

---

## 5. 未做 / 未验证（写作时点）

- **未写任何代码/脚本**：§1 是冻结定义，§3 是冻结正文——执行轮第 0 步原样落盘。2,231 可达性沿用 b34 静态实算（背书 = pack sha 未变），不是渲染读数。
- **未跑 Gradle/JVM/设备**：门 0–5 全 `NOT_RUN`；G-FIRST/G-READ 未测；baseline APK 对未构建。
- **脚本正文未在本机试跑**：`bash -n`/`py_compile` 属执行轮门 0；Windows Git Bash 的 `cmd package resolve-activity`、`am instrument` 参数面若不兼容，只许改脚本并 §6 登记，不得改语义。
- **§2.6 的口径边界**：探针读数含仪器内装配开销（差分可比、绝对值不外推）——已冻结登记，不冒充产品首查。
- **预算最坏组合（183 字标题 × 5 节点）的实际裁剪路径未测**；执行轮按 §2.9 报。
- **第二槽对生成质量的影响未测**：本臂只证渲染形态/预算/交付安全，不证"模型讲得更好"。
- **judge 的 `_node_segment` 文本近似判读**：逐节点标记归属的权威在 JVM 用例（§3 判读归属边界），judge 不得单独作为 §2.3 的采纳证据。

---

## 6. 执行轮追加位（只追加，不改 §1–§4）

> 执行轮在此追加：写前/写后 HEAD 与 porcelain；§4.1 逐件 post-sha 与 §4.2 逐件 sha；第 0 步落盘 5 件的 commit id 与门 0 输出；脚本若被改的逐行 diff；门 1–4 exit code；5 节点 × 2 槽实际渲染文本、首选节选 min/p50/max、总长、dropped/trimmed/titleTrimmed；第二槽可达性（静态 2,231 / 本次实际）；广告两段逐字 + snapshot；F5 口径变更登记；G-FIRST/G-READ 逐次样本与 p50/p95/max（baseline/post）、检索 p95；render_judge.json 全文；§2 的采纳/回滚判词。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
