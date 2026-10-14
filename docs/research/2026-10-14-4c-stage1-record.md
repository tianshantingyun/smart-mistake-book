# 4C 第一阶段记录：现存切分链路真机核验 + 切分金标集 + 准确率红线门

> 日期：设备核验 2026-10-13（本机 UTC+8 晚间；设备时钟与 DB 均为 UTC）；金标冻结
> `2026-10-13T14:50:48Z`；本记录 2026-10-14。
> 定位：阶段 4C（错题本与录入二期，R3+R11）按裁定 37 四步走——① 现存链路真机核验 →
> ② 金标集 + 准确率红线门 → ③ 主路径化（门后自动化、确认过渡）→ ④ A5/A6/A7/A8/B5/C11。
> **本批只做 ①②**（本轮计划：`.zcode/plans/plan-sess_972ed30a-b136-40b0-8362-e7e2a8be3065.md`
> §0/§3–§5、§8）。
> 证据分层：设备事实来自本轮「真机核验」与「独立核对」两份实测材料（命令原文见各自材料）；
> 金标与度量来自「金标集」「准确率」两份材料；配套代码修复见 `docs/known-defects.md` KD-34
> （分题契约）与 KD-32/33 补记（B1–B4）。本记录者只回读了源码行号、金标文件与
> `build/split-eval/` 产物，并实跑了 validator 与 androidTest 编译检查（见 §7）；
> **未重跑任何设备命令**。

## 1. 范围与门口径

- 4C 出口门（主线 4C 行 / 需求定稿 A4）：整卷切题真机走通且准确率达门；切分准确率是红线
  ——**切残题干不得入库**；门不过不允许自动入库（"门后自动化、确认过渡"，裁定 37）。
- 本轮度量三项（计划 E3）：① 题数完全一致率 ② 边界 IoU≥0.5 题占比 ③ 切残率。
- 本轮门式（计划 E4 的**建议值**，标"立项自定"）：**① ≥ 0.9 且 ③ ≤ 0.05**；② 只度量、不在
  门式内。低于门 → 不启用门后自动化（4C③）。该门值目前只存在于本轮计划与本节记录，
  **尚未落盘为独立仓内产物**（本记录者 grep `题数一致率|切残率` 于 `docs/`、`tools/`：0 命中）。
  **v2 补记（2026-10-14）：度量脚本已落盘 `tools/split_golden/measure_split_accuracy.py`**
  （未提交工作树文件；门值首次成为可重跑产物，见 §9）。
- **门结果：false**（① 0.2857 < 0.9；③ 0.0 ≤ 0.05）。见 §4。
  **v2 口径修正后：① 0.5714，门仍 false——见 §9（修正前后数字 / 改判页 / 阈值未动）。**

## 2. 真机核验（4C①）

设备：`test_device`（emulator-5554，API 34），包 `com.tingyun.smartmistakebook.localfirst`
（`localFirstDebug`，versionName `0.2.0-localfirst`，`lastUpdateTime=2026-10-13 15:13:21`）。

### 2.1 环境前提（与本轮任务书前提冲突，需 owner 判读）

本轮任务书前提是"模型配置已在应用里、不要重新注入、不要清数据"，实测**不成立**：
- 这台 emulator 是 `-wipe-data` 启动的（`emulator.exe -avd test_device -wipe-data
  -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect`）；核验开始时包未安装
  （`pm list packages` 无该包、`/data/data` 无目录）、datastore 无 `model_configuration`。
- 无可用凭证则边界评估跑不起来，核验方用应用内「大模型设置」重建了配置（provider/model/
  base_url 与任务书指定一致：opencode zen / `deepseek-v4.1-flash` / `https://opencode.ai/zen/go/v1`），
  API Key 取自本机 `~/.local/share/opencode/auth.json` 的 opencode-go 条目（仅填入设备，
  未回显、未写入仓库）。**"这算不算『重新注入』"按前提被环境擦除来判读，留给 owner。**
- 设备被我方改动过一处再改回：为绕开 IME 合成文本重复，中途 disable 过 LatinIME，
  用完已 enable（结束时 `ime list -s` 可见 LatinIME 已启用）。

### 2.2 核验清单（逐条结果与证据）

| # | 检查 | 结果 | 证据摘要 |
|---|---|---|---|
| 1 | 环境前提核对（包/模型配置是否已在设备上） | **未达**——前提不成立 | 包未安装、无模型配置、无数据可保留；emulator 命令行含 `-wipe-data`；装机后 datastore 只有 `sleep_journal`，设置页四字段全空 |
| 2 | 安装 APK | 通过 | `adb install -r …app-localFirst-debug.apk` → Success；`versionName=0.2.0-localfirst versionCode=2` |
| 3 | 推送 7 张金标页并媒体扫描 | 通过 | `/sdcard/Pictures/split-golden/` 7 文件（字节数 7993927/7973828/8103481/7955161/6703933/8709641/6418822）；`content query` 7 行（`_id` 1000000019–25） |
| 4 | 重建模型配置 | 通过（前提冲突见 2.1） | datastore 可见 `is_configured / provider=opencode zen / base_url=…/zen/go/v1 / model_id=deepseek-v4.1-flash / protocol=openai-chat-completions-v1`（凭证字段未回显） |
| 5 | 应用自带「测试图片与讲解能力」 | 通过（首次 401，重存后通过） | logcat 三探针 200：structured 588B / image 788B / tools 691B；UI「测试通过：图片读取和讲解都可用。」首次保存后 401/89B 与错误 key 复现报文同形——**根因未深究，如实记录** |
| 6 | 全选导入 7 页并全部「已保存」 | 通过 | UI「正在保存 7 页 / 已保存 7 页 · 需重试 0 页」；`batch_import_job(batch-d3a052c6…)=COMPLETED`；7 页 READY、attempt_count=1 |
| 7 | 「开始分题」边界评估 | **通过（本轮核心正面结论）** | `model-task-cd3084cc…`（subject `batch-boundary-79303d32…`）SUCCEEDED/COMPLETE/无 failure_code/54,881ms；`output_snapshot` decision=PASS、`followingPageRelations=[NEXT_QUESTION×6]`；见 §2.3 |
| 8 | logcat `IllegalArgumentException` 原文 | **未达**——未复现，无原文可留 | `logcat -d | grep -c` = 0；但 ring 只回到 16:23:54，边界任务 15:48 发生 ⇒ "全程为 0"不可证（独立核对） |
| 9 | `boundary_after_status` 页 0–6 | 通过 | page0..5 全 `NEXT_QUESTION`、page6=PENDING；7 页 `split_after_status` 全 SETTLED；与 6 条关系自洽 |
| 10 | `split_import_job` / `split_import_question` | 通过（但只覆盖 2/7 页） | job=2（`split:batch-d3a052c6…:0` 与 `:4`，各 page_count=1、question_count=4、READY）；question=8 行；单页 job 使 `page_index` 列全为 0，真实页号在 job_id 末段 |
| 11 | 导出每页题目区域 | 通过（file 名为**推断**，DB 无原文件名） | `build/split-eval/app-split.json`（7 页 8 框）；文件名映射三条独立证据见 §5 |
| 12 | 至少 1 页存入错题本 | 通过（路径是逐题确认，不是「录入所选」） | 「录入所选（4）」只把 4 行 `split_import_question` PENDING→SAVED（写 `split_draft_id`），problem/revision/entry 仍 0（该页判定 BLOCKING、入口被 gate）；改走页面草稿「存入错题本」（page_index=1）后 problem/revision/entry/practice_unit 各 +1 |
| 13 | 页级 organize 的 CAPTURE_ASSESS 失败形态 | **失败（如实记录）** | 7 条页级 CAPTURE_ASSESS 中 page2/page6 = PERMANENT_FAILURE/INVALID_RESPONSE、`failure_retryable=0`、attempt_count=1；其余 5 条 SUCCEEDED——"按新口径失败可重试"没有出现（未归因，见 KD-33 补记） |
| 14 | 知识包全量安装 | 通过 | `packs fully parsed in 27900 ms (2 packs)`、`install finished in 226923 ms (full path)`；`content_install_state` 时间戳 15:14:07/15:17:26 与独立核对一致；无失败横幅 |
| 15 | 截图留证 | **部分未达**——3 张黑帧 | 有内容：`w01-launch.png` / `w03-home.png` / `w05-relaunch2.png` / `w07-committed.png`；纯黑：`w02-model-settings.png`、`w02b`、`w02c`、`w04-relaunch.png`、`w06-capability-pass.png`（15.5–15.9KB；MCP 截图与 `adb exec-out screencap` 字节一致；同期 launcher 截图正常、uiautomator 树可读 ⇒ 判为应用窗口渲染失效，强杀重启后恢复）。这些时刻改以 uiautomator dump + logcat 取证 |

### 2.3 设备侧结论

- **目标①达成**：新包上整卷「开始分题」的边界评估不再以 INVALID_RESPONSE 打死（KD-34 的旧
  症状消除；`batch_import_page` page 0–5 落 `NEXT_QUESTION`）。口径边界：logcat
  `IllegalArgumentException=0` 只覆盖 16:23:54 之后，不能表述为"全程无异常"。
- **目标②部分达成**：导出 7 页 / 8 框，但只有 2 页跑出分题区域，可度量样本远小于金标 7 页 16 框。
- 页级 organize 仍有 2/7 不可重试 INVALID_RESPONSE（未归因）；第 8 个 draft-583b094e
  （复用 page5 资源）的 `CAPTURE_PARSE` 以 `PROVIDER_REJECTED_INPUT` 失败（独立核对发现，
  真机核验材料未提及）——两项都另行记账。
- 中途一次「API Key 无效或已失效」（401）重存后转正常：现象未深究。

## 3. 金标集（4C②，E1/E2/E5）

- **产物路径**（工作树**未提交**，`git status` 为 `?? tools/split_golden/`）：
  - `tools/split_golden/tables/split_golden_v1.json`（3809 B，7 页 16 框）；
  - 旁车 `tools/split_golden/tables/split_golden_v1.json.sha256`；
  - 校验器 `tools/split_golden/validate_split_golden.py`。
- **冻结口径**：逐字段落盘为 canonical 字节（`ensure_ascii=False`、`sort_keys`、`indent=2`、
  单个 LF、无 BOM）；sha256 = `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f`
  （旁车一致；coreutils `sha256sum -c` 复核 OK）。
- **入集口径**：24 张抽样中，两人标注一致（题数一致且逐题 IoU≥0.6）的页才入集；入集
  7 页 / 16 框，丢弃 0 页——其余页本次未提供两员一致标注，未做丢弃判定。
- **证据等级**（validator 原文）：`model-labeled, no human expert; two independent labelers agreed`
  ——金标为**模型标注、无人工专家**，题框自身含标注误差。
- 分布（validator 输出）：科目 其他 4 / 化学 3；版面 整页 1 / 裁边 6；页清单：
  `IMG20260810003928.jpg`(化学/裁边/4框)、`…004304`(化学/整页/1框)、`…004353`(其他/裁边/2框)、
  `…005605`(化学/裁边/2框)、`…010349`(其他/裁边/4框)、`…011126`(其他/裁边/2框)、
  `…011800`(其他/裁边/1框)。
- **校验（本记录者实跑）**：`python tools/split_golden/validate_split_golden.py` →
  `拆题金标校验 PASS`，退出码 0；C1 冻结字节（sha 与旁车一致、canonical 逐字节相等、CRLF=0）、
  C2 schema 全过、C3/C4 页数与框数一致且框全部合法、C5 无重复文件、C6 页数 7≥7；
  `generatedAt=2026-10-13T14:50:48Z`。
- 门后自动化的解耦口径（计划 E5）：金标与度量脚本**不与被测实现耦合**，不为提分改口径。

## 4. 准确率度量与门结果（4C②，E3/E4）

- 数据源：`build/split-eval/app-split.json`（7 页 8 框，1482 B）+ 金标集。应用输出确取自设备
  DB：`split_import_question` 8 行与导出文件逐值完全相等（页 0、页 4 各 4 题）；配页另经独立
  核验（按 `pages.txt` 顺序从设备 `files/source-assets` 取 7 张图，与 `C:\Users\听云\Pictures\错题数据集`
  全 155 张做感知匹配，7/7 的 best 相关系数≈1.0000）。
- 分母口径：可比页=按文件名配对成功的页，实际 7 页（金标 7 页与应用 7 页文件名集合完全相同），
  不足 8 页，分母写死 7；每页按 IoU 做最大权一对一最佳配对（两页结果均为对角配对），
  题级分母=金标 16 题。

| 文件 | 金标题数 | 应用题数 | IoU≥0.5 可配对题 | 切残 |
|---|---|---|---|---|
| IMG20260810003928.jpg | 4 | 4 | 4 | 0 |
| IMG20260810004304.jpg | 1 | 0 | 0 | 0 |
| IMG20260810004353.jpg | 2 | 0 | 0 | 0 |
| IMG20260810005605.jpg | 2 | 0 | 0 | 0 |
| IMG20260810010349.jpg | 4 | 4 | 3 | 0 |
| IMG20260810011126.jpg | 2 | 0 | 0 | 0 |
| IMG20260810011800.jpg | 1 | 0 | 0 | 0 |

- **指标**：① 题数完全一致率 `countExactRate = 2/7 = 0.2857`；② 边界 IoU≥0.5 题占比
  `iouRate = 7/16 = 0.4375`（若只取有应用输出的 2 页，则 7/8 = 0.875）；③ 切残率
  `fragmentRate = 0/16 = 0.0`（应用在任何一页的框数都不多于金标、配对恒为 1:1；阈值
  0.3/0.4/0.5/0.6 结论一致）。边界值：`…010349` 第 4 题 IoU=0.475（差 0.025 未达标），
  其余 7 题 IoU 0.639–0.753。
- **门判定**：`① 0.2857 ≥ 0.9`？否 ⇒ **门 = false**。
- **门后自动化：未启用。** 三条依据：(a) 计划 E4"低于门 → 不启用门后自动化（4C③）"、
  §8"4C③ 取决于门值，本批不做"；(b) 门实测 false；(c) 仓内未见门控开关/自动入库实现
  （本记录者 grep `autoIngest|自动入库|accuracyGate|splitGate|gateEnabled` 于 `feature/`、`core/`
  ——0 命中），设备侧 split 结果仍需用户动作才入库（「录入所选」只置 SAVED；本次入库来自页面
  草稿的逐题确认）。
- **缺框原因（DB 佐证，非导出截断）**：其余 5 页无 `split_import_job` 行——
  `BatchSplitRecognizer.kt:86` 要求区域集过 `isUsableSplitRegionSet()`（2–12 个）才落库：
  page2/page6 = PERMANENT_FAILURE(INVALID_RESPONSE)、page1/page3/page5 模型 SUCCEEDED 但
  `questionRegions=[]`（page3 判 NEED_MORE_IMAGE）。另有设计约束：金标 2 个单题页
  （`…004304`/`…011800`）结构上不可能产生"拆题"，应用最小拆分为 2 块。

## 5. 口径、限制与推断（引用前必读）

- **file 名为推断**：DB 不存原文件名（`grep -a 'IMG20260810'` 扫 db 与 db-wal 均 0 命中，
  独立核对重跑成立）。页序按三条独立证据推断：① 唯一横构图 canonical（asset-90751113
  2048×1536）落在 page_index=1，而 7 张金标里只有 `…004304` 是 4096×3072 横构图；② 应用只对
  page 0 与 page 4 分出 4 框，金标里恰好只有 `003928` 与 `010349` 标了 4 题；③ 页面草稿正文
  （"16. …二氯海因 DCDMH…"、"化学试题 第6页，共8页"）与金标 04304 的 subject=化学 自洽。
  三条同时成立支持"按拍摄时间升序 = 页序"，但仍是推断而非库内事实。
- 样本仅 7 页 / 16 题，单页变化即 1/7 或 1/16；金标为模型标注、无人工专家，含标注误差。
- 切残率 0 不等于贴合好：16 题中 **8 题应用完全没出框**（属"漏"，集中在 5 个无拆题作业的
  页）——"漏"只体现在 ① 与 ②，不在 ③。
- 应用输出为设备 DB 快照（WAL 未 checkpoint，读取用只读 URI），非重新跑一遍的实时输出。
- 未单独度量：跨页合并质量、题级去重（均不在本批范围）。
- 边界评估本身落 `decision=PASS`、relations 全 `NEXT_QUESTION` ⇒ 本次没有真的发生跨页合并，
  合并路径的"质量"未被本批样本检验。

## 6. 独立核对与独立复核（对本记录素材的复核）

**独立核对**（对"真机核验"材料逐条复核）——核心事实全部成立，更正三处：
1. `canonical_source_asset` 行数：材料写"11 行（7 整页 + 4 窄条）"**不成立**，现值 15 = 7 整页 +
   8 窄条（8 条窄条与"2 页各 4 框"自洽，11 反而自相矛盾）。
2. 「入库③」证据点名的 artifact（`call_01_yuehgaf…png`）实为 launcher 截图（引用名错配）——
   但"同进程内错题本显示空态"的观察本身有 16:21 与 16:23 两张空态截图证据，成立。
3. `known-defects.md:1233-1238` 引用错位（那里是 KD-33 症状段）；旧抛点原文实际在 git HEAD 的
   `CaptureAssessmentTasks.kt:126`（可查）。
部分可核：logcat 已轮转（§2.2#8）；能力测试 200/401 的原始日志行与 artifact 证据不可见；
"金标 7 页 16 框"在 独立核对 的检索路径（docs、knowledge-production、out/build）未找到——
金标集实际落盘在 `tools/split_golden/`（本记录者已验证并跑过校验器，§3）。

**独立复核**（对代码修复的静态读码 + Gradle 实跑）——结论："A1–A3 方向正确、两个自动拆分入口
都有消费侧判据（`CaptureModelTaskPolicy.kt:78/88`、`BatchSplitRecognizer.kt:86/96/110`，无漏掉
的调用点）"；发现一处**硬伤**与四处未闭合，均已记入 KD-34/KD-33/KD-32：
- 硬伤（high）：A3① 端到端钉子编译不过（本轮记录者已实跑复现，见 §7）；
- 契约容错只覆盖 SPLIT+区域 形状（issue 侧耦合仍在，medium）；
- B1 三协议族截断分流只覆盖非流式轮（medium）；
- A3 对已落库 INVALID_RESPONSE 边界窗口无效（medium）；
- 两处注释/重复阈值硬编码（low）。

## 7. 本轮执行的验证 vs 未执行（避免误读）

**本记录者本轮实跑：**

- `python tools/split_golden/validate_split_golden.py` → `拆题金标校验 PASS`，退出码 0（§3）。
- `./gradlew.bat --offline :core:data:compileDebugAndroidTestKotlin` → **BUILD FAILED**：
  `BatchImportRepositoryInstrumentedTest.kt:500:39 Unresolved reference 'remainingBoundaryCount'`、
  `:509:34 Unresolved reference 'splitReadyJobId'`（BUILD FAILED in 6s）——KD-34 的 A3① 端到端
  钉子且整个 core:data androidTest 源集不可编译。
- 读取类核对：金标 JSON/旁车、`build/split-eval/{app-split.json,pages.txt,questions.txt}`、
  KD-34/33/32 相关源码行号（contract/domain/policy/commands/recognizer/vault/dao/repo/adapters/
  parsers）。
- grep：`autoIngest|自动入库|accuracyGate|splitGate|gateEnabled`（feature/、core/）0 命中；
  `题数一致率|切残率`（docs/、tools/）0 命中。

**引用他方实跑（非本记录者）：** 全部设备命令（真机核验/独立核对/下游走查）；JVM 套件
（独立复核：`:core:domain:test` 7/0、`:core:model:test` ModelTasksTest 31/0、
`:core:data:testDebugUnitTest` 5/0、`:feature:capture:testDebugUnitTest` 10/0、
`:core:database:testDebugUnitTest` ModelTaskIntegrityTest 5/0 等）；准确率度量与感知匹配。

**未跑 / 不可证：** ✅ 设备命令未重跑；✅ 度量未重算；✅ 金标入集过程（双标注）未重放；
✅ logcat"全程无异常"不可证（ring 轮转）；✅ 黑帧时刻的 UI 断言以 dump 文本为准。

## 8. 结论

- **4C①（真机核验）**：链路核验"半通过"——边界评估阻塞（KD-34 旧症状）已消除、导入→分题→
  入库通；页级 organize 仍 2/7 不可重试 INVALID_RESPONSE（未归因）；环境前提被 `-wipe-data`
  擦除导致"不要重新注入"须 owner 判读。
- **4C②（金标 + 门）**：金标集落盘且校验 PASS（sha `f9e92c47…`、7 页 16 框、模型标注）；
  准确率门 = **false**（题数一致率 0.2857 < 0.9；切残率 0.0 ≤ 0.05）；
  **门后自动化未启用，且本批不做 4C③**。
- 配套代码修复与遗留：`docs/known-defects.md` KD-34（分题契约；A3① 钉子已修复可运行、C5 收口
  与未闭合项状态见 KD-34 文内）、KD-33/KD-32 的 2026-10-14 补记与收口补记（B1–B4、C1–C3）；
  下游（入库→复习→判定→档案）实测见 `docs/research/2026-10-11-thin-vertical-slice-walkthrough.md`
  第四段。

## 9. 补记（v2 口径修正，2026-10-14；本节 2026-10-15 记）——「单题页期望=不拆分」的透明记账

**改了什么。** 只改 ① 题数完全一致率的定义：单题金标页（`questionCount < 2`）的期望是「不拆分」
——应用侧最小可提交拆分为 2 块（`isUsableSplitRegionSet()` 要求区域数 ∈ 2..12，
`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/CaptureWorkflowRepository.kt:375-382`，
本节记录者回读），单题页结构上不会产生合法拆分：应用输出 0 区域 ⇒ 一律记一致（v1 把「金标 1 vs
应用 0」按数值不等记不一致——是定义错，不是模型错；> 0 区域 ⇒ 记不一致）。多题页判据（应用区域数
== 金标题数）不变；② 边界 IoU≥0.5 与 ③ 切残率的定义不变。

**修正前后（同一数据源：`build/split-eval/app-split.json`，1482 B，7 页 8 框，设备 DB 快照；
未重跑设备）**

| 指标 | v1 | v2 | 说明 |
|---|---|---|---|
| ① 题数完全一致率 | 2/7 = 0.2857 | **4/7 = 0.5714** | 改判 2 页，均为不一致 → 一致 |
| ② 边界 IoU≥0.5 题占比 | 7/16 = 0.4375 | 7/16 = 0.4375（不变） | 只度量、不设门 |
| ③ 切残率 | 0/16 = 0.0000 | 0/16 = 0.0000（不变） | 0.3/0.4/0.5/0.6 阈值敏感性同结论：全 0/16 |

- **改判页（逐页明细由脚本打印）**：`IMG20260810004304.jpg`、`IMG20260810011800.jpg`（两页单题页，
  金标 1 vs 应用 0；v1 不一致 → v2 一致）。
- **仍不一致的 3 页**：`IMG20260810004353.jpg` / `IMG20260810005605.jpg` / `IMG20260810011126.jpg`
  ——都是多题页漏拆（应用 0 区域），是真实失败，不是定义问题。
- **阈值未动**：门 = ① ≥ 0.9 且 ③ ≤ 0.05（与 §1 记录的建议值一致；脚本常量 `COUNT_GATE = 0.9` /
  `FRAGMENT_GATE = 0.05`）。**门判定 = false**（0.5714 < 0.9）——这次修正**没有**把门改成通过。

**产物与可追溯性（如实记局限）.** 门值第一次有了可重跑的单一来源：
`tools/split_golden/measure_split_accuracy.py`（纯读：不写文件、不联网；输入不可用 exit 2、
应用导出缺失 exit 3；`--app/--golden` 可换路径）。但该脚本**仍未提交**（`git status` =
`?? tools/split_golden/measure_split_accuracy.py`），所以「阈值未动」无法用 git diff 追溯，只能与
本节及 §4 的冻结数字对照（脚本自己打印 v1 对照与改判页清单，可供逐项核对）。

**验证（本节记录者本轮实跑）**：`python tools/split_golden/measure_split_accuracy.py` 两次运行
stdout **逐字节一致**（sha256 `17bd67d7dda169c1393860189dafa06eb0e3a10616787d30279c32a575375740`，
exit 0），输出与本节数字逐项一致；`python tools/split_golden/validate_split_golden.py` → `拆题金标校验 PASS`、
exit 0（金标 sha256 `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f` 未变）。
独立复核（另跑）确认门仍 false，且单题页改判有实现依据——该约束在本批之前已记在 §4「缺框原因」。
