# 薄纵向切片走查（录入 → 复习 → 判定 → 档案）— 2026-10-11

> 数据：`C:\Users\听云\Pictures\错题数据集`（155 张 JPG，~8 MB/张，化学/生物试卷页）
> 设备：`test_device`（emulator-5554，API 34，1080×2400），`localFirstDebug` 包
> `com.tingyun.smartmistakebook.localfirst`（本次走查期间安装，应用数据全新）
> 证据：截图在 `build/walkthrough-shots/`（本地产物，不入库）；DB 结论均由
> `run-as … sqlite3 …/smart-mistake-book.db` 现场查询得出。
> 前提（用户已声明）：**模型服务尚未配置，模型相关步骤按"跳过并记录"处理**。

## 1. 结论（TL;DR）

**断在哪：链路在「整理 / 分题」处被模型配置挡住；录入侧本身完整闭环。**
在没有模型的前提下，复习 / 判定 / 档案三步因题库为空而不可达（只验到各自空态）。

- ✅ **录入（整卷/PDF → 多张照片 → 批量导入）**：40 张照片全部按页保存成功，
  2 个导入任务均 `COMPLETED`，0 需重试；40 条待处理草稿落库；强停应用后草稿全数幸存。
- ✅ **录入（相册 → 单张）**：另走通单页分支（第 41 张），直接进入整理页并建草稿。
- ⛔ **整理 / 分题**：点击后提示「请先在"我的"里配置模型服务，再整理相邻页面。」
  （`开始分题` → `batch_import_organization` 区域行内提示；页面详情页则为
  「配置好模型后，拍照题图才会交给模型整理」+「去设置」）。`split_import_job = 0`，
  未产生任何分题记录——**正确收口，无崩溃、无静默失败**。
- ⛔ **复习 / 判定 / 档案**：`problem_revision = 0`、`review_queue_item = 0`，
  各页均显示设计好的空态（复习首页「暂无学习记录」、错题本「还没有错题」、
  学习掌握「还没有学习记录」、智能体「当前模型还不能处理对话…」且发送键 `enabled=false`）。
- **样本条数清点**：**41 / 155 张**（26.5%）完成录入落库；40 条待处理（EDITING）
  + 1 条主动弃用（ABANDONED）。源资产表 41 行、全部 `PHOTO_PICKER`、
  尺寸 3072×4096 / 4096×3072（相机原图，无截图混入）。

## 2. 逐步记录

### 2.1 录入：批量导入（整卷）

| 步骤 | 观察 | 证据 |
| --- | --- | --- |
| 照片入相册 | 50 张推入 `/sdcard/Pictures/cuoti/`，`jobscheduler run … media.module -200` 强制扫描后 MediaStore 逐条出现 | `content query` 计数 10→50 |
| 选图 | 系统 Photo Picker（`PickMultipleVisualMedia`），多选模式 | 截图 03 |
| **单次选图上限 = 30** | 选择器底部栏出现 **"Select up to 30 items"**；达到 30 后不能再加。源码：`MAX_BATCH_IMPORT_PAGES = 30`（`core/domain/.../BatchImportRepository.kt:199`，`require(pages.size in 2..30)`），UI 以 `PickMultipleVisualMedia(MAX_BATCH_IMPORT_PAGES)` 传入（`feature/library/.../BatchImportRoute.kt:92`） | 截图 23 + 源码行号 |
| 第一批（10 张） | 「正在保存 10 页」→「10 页已保存 · 需重试 0 页 · 剩余 0 页」；进度条 + 暂停按钮 + "可以离开本页" | 截图 04/05 |
| 第二批（30 张） | 同一页面「再选一组照片」追加；「30 页已保存 · 需重试 0 页」 | 截图 24 |
| 落库核对 | `batch_import_job`：2 行（10 页 / 30 页），均 `COMPLETED`；`batch_import_page`：40 行全 `READY`；`problem_draft`：40 行（39 `EDITING` + 1 `ABANDONED`）；`files/source-assets/` 40 个文件 ≈ 146 MB | 现场 SQL |
| 原图转存 | 原始 ~8 MB → 落库 3.3–4.7 MB（**分辨率不变**，全分辨率重编码 JPEG）；`source_type` 全为 `PHOTO_PICKER` | `canonical_source_asset` |

待处理题目列表（录入页内）逐条展示：标题（本地 OCR 提取，如
「2.老试结束后,将答题卡交回。」——注意原文如此，含 OCR 错字）、状态副标题
（「题面还没整理完整，点此继续」/「上次没有完成，点此继续」）、时间戳与
「不再保留」动作。列表头部计数（该时刻）：「39 道临时题记录保留在本机 / 等你继续 38 道 · 需要处理 1 道」。

### 2.1b 录入：单张（相册）分支

`录入 → 相册` 打开**单选** Photo Picker（与整卷的多选不同），点选第 41 张后
**直接进入该页的整理页**（不先经过待处理列表）：原图显示正常、跨页补拍入口在、
整理区同样为模型门（「配置好模型后，拍照题图才会交给模型整理」+「去设置」）。
落库：`problem_draft` 增至 41 行（40 `EDITING` + 1 `ABANDONED`），
`canonical_source_asset` 增至 41 行（全 `PHOTO_PICKER`）。

### 2.2 运行态恢复（强停 → 重启）

`am force-stop` 后重新启动：`problem_draft` 仍为 10（当时批次数），待处理列表完整恢复。
——与既有 P0「运行态恢复」修复的预期一致。

### 2.3 主动弃用（不再保留）

点击「不再保留」→ 确认弹窗「不再保留这道题？『新拍题目』会从待处理列表移除，
也不会存入错题本。本机原图和记录会按原样保留到后续清理。」→ 确认后：
`problem_draft` 状态由 `EDITING` 变为 `ABANDONED`（**软删除**，行保留），
列表计数同步减一。弹窗文案与实际行为一致。

### 2.4 整理 / 分题（模型门）

- 整卷录入页：「开始分题」→ 行内橙字提示「请先在"我的"里配置模型服务，再整理相邻页面。」
- 页面详情页（第 1 页「点此继续」）：原图预览正常、跨页补拍入口在，
  但底部为「配置好模型后，拍照题图才会交给模型整理」+「去设置」按钮；无任何"跳过整理直接入库"路径。
- `split_import_job` / `split_import_question` 均为 0 行 —— 分题未启动，无脏数据。

### 2.5 复习 / 判定 / 档案（空态）

- 复习首页：早上好问候 + 「暂无学习记录」+ 禁用态「暂无待复习题」。
- 错题本：空态插图 + 「还没有错题：录入第一道错题…」+「录入」/「导出成果」。
- 学习掌握（档案）：空态 + 「你做过并保存的题会自动整理到相应科目和知识点，不需要手动填写。」+「去错题本看看」。
- 智能体：完整能力目录（讲一道题 / 录入并讲解 / 从错题本挑一道）+ 无模型门
  「当前模型还不能处理对话，先完成模型配置和能力测试。」+「检查模型设置」；
  输入框可聚焦但发送键 `enabled=false`。
- 大模型设置页：协议四选（OpenAI 兼容 / Responses / Anthropic / Gemini）、
  服务商 / 模型 ID / Base URL / API Key 表单 + 「安全保存配置」（未填时禁用）。
  **该页设置 `FLAG_SECURE`（`SecondaryScreens.kt:120`），截图必然全黑——预期隐私保护，非缺陷。**

## 3. 发现清单

### 3.1 产品行为（均为设计内，记录备查）

1. **单次选图上限 30 张**（`MAX_BATCH_IMPORT_PAGES = 30`），超出需「再选一组照片」追加；
   整卷任务按批计（job 粒度 10 页 / 30 页各一条）。
2. **草稿弃用是软删除**（`ABANDONED`），行与原图保留至"后续清理"——与弹窗文案一致；
   孤儿资产 GC 的宽限期逻辑在后续清理侧，本次未验证。
3. **本地 OCR 先行**：待处理草稿在打开详情后获得 OCR 标题（ML Kit 本地识别，无需模型），
   未打开的草稿标题保持「新拍题目」。
4. 原图**全分辨率重编码**入库（3072×4096 保持），体积约为原图一半。

### 3.2 无缺陷结论（本次实测范围内）

- 走查全程 logcat `FATAL EXCEPTION` 计数 = 0，无 ANR 记录。
- 无模型时所有入口均以**可读文案**收口，无崩溃、无静默失败、无半成品数据。

### 3.3 宿主工具注意（非产品缺陷，写给出走查的人）

- Git Bash 会把 `adb … /sdcard/...` 的参数改写为 `C:/Program Files/Git/sdcard/...`，
  adb 报 `secure_mkdirs failed`（若用 `tail -1` 过滤输出会**吞掉错误行**只剩摘要行）。
  所有含设备路径的 adb 调用须加 `MSYS_NO_PATHCONV=1`。
- Photo Picker 是 bottom sheet：**下拉手势会把它整个关掉**（"回到顶部"要慎用下拉）；
  其选中状态不暴露在 uiautomator dump 里，可靠计数只有底部栏 `Add (N)` / `Select up to N items` 文案。
- 选择器逐屏点选需在滑动后等待惯性停稳再取坐标，否则坐标失效会点错（本次第一版脚本因此
  出现过 ±10 的偏差，第二版改为"每点必核 `Add (N)`，不 +1 则撤销重试"后稳定）。

## 4. 未验证 / 边界

- **整理 / 分题 / 讲题 / 复习判定 / 掌握度回写：全部未验证**——阻塞于模型配置
  （需要真实 API Key；`TEST_SEED_MODEL_CONFIG` 调试广播通道同样需要 key）。
- 复习、判定、档案的**有数据形态**未验证（仅空态）。
- 导出成果（PDF）、存储与导出、复习排程、提醒偏好等设置页未逐项走查。
- 155 张数据只用了 41 张（受单次 30 上限与走查时长限制）；剩余 114 张未入机。
- 数据库层面：`problem_draft_revision` 80 行 = 40 草稿 × 2 修订（每草稿一致，
  对应"创建 + 一次内容修订"，为批量导入两批的 40 条草稿；单页第 41 条的修订行
  未单独核对）。

## 5. 下一步（解除断点所需）

1. 提供任一服务商的可用 API Key（或由用户在该模拟器上手动配置一次），
   走查即可继续：`开始分题` → 整理 → 入库 → 复习 → 判定 → 档案。
2. 若希望一次性走完剩余 115 张：分批 30 张/次循环「再选一组照片」即可（流程已实测可追加）。

---

# 下半段（2026-10-11 晚）：配置模型后的实测

> 用户在走查后提供了通用模型：opencode zen `https://opencode.ai/zen/go/v1`、
> `deepseek-v4.1-flash`（生图模型仍未提供，图片生成相关路径不在本轮范围）。

## 6. 模型配置与连通

- 用 debug 广播 `TEST_SEED_MODEL_CONFIG` 注入配置（DataStore 出现
  `model_configuration.preferences_pb`，设置页回读 provider/model/base_url 正确）。
- **首测失败**：能力测试「测试未通过：当前模型暂不兼容」，两探针均 HTTP 400；
  curl 复现得 `MissingSessionID` —— 该端点要求 `x-opencode-session` 头，应用不发。
- **修复 KD-31（`64c79456`）**：传输层按 host 补服务商兼容头（opencode.ai 精确匹配）
  + 单测 5 例。修复后同一配置：**「测试通过：图片读取和讲解都可用」**
  —— 同时证明该模型**支持视觉输入**（图片探针通过）。

## 7. 分题 / 整理 的实测结果（未过）

- 3 页批次「开始分题」→ UI「正在分好每一道题」；模型任务记录：
  **1 成功 / 3 永久失败（2×INVALID_RESPONSE、1×能力缺省为配置前残留）/ 2 可重试失败（TIMEOUT）**。
  成功那条的判定是 `NEED_MORE_IMAGE`（"第8题题干和选项被底部截断"等）——
  模型在按设计做"题面完整性"质量门。
- 单页整理（待处理第 1 条 → 点此继续）同样失败，重试后仍失败；
  UI 收口为「这道题暂时没准备好 / 这次没有完成，原图已经保存，可以重新处理」。
- **根因分析（curl 复现应用口径请求）**：
  - 该模型是**推理型**：响应含 `reasoning_content`（一次评估思考 4,522–5,759 token）。
  - 成功时：2048px 图 + 评估提示词 + json_object，**41.6s / 51.0s**、JSON 键集与枚举全部合规。
  - 把 `max_tokens` 限到 2000：思考吃满预算 → `content` 为空（可复现）——与
    INVALID_RESPONSE 的形态一致。
  - 应用任务预算 `MODEL_TASK_TIMEOUT_MILLIS = 120_000`；观测到应用侧流式任务存活 >205s 后 TIMEOUT。
- 结论：**分题/整理在当前模型下不可稳定通过**（登记 KD-33，未修）。

## 8. 内存天花板（本轮最大发现，登记 KD-32）

- 导入第 3 张测试照片时**进程 OOM 崩溃**：
  `FATAL EXCEPTION: DefaultDispatcher-worker-10 → java.lang.OutOfMemoryError`
  （Dalvik Heap alloc 134–147MB / 上限 192MB；Activity 被强关，`batch_import_job`
  留 PROCESSING、页全 READY，后由 WorkManager 重试自愈为 COMPLETED）。
- **知识包安装 OOM**：启动横幅「本地知识包尚未准备好 / 自动分类会暂缓」
  （`Bundled knowledge install failed → OutOfMemoryError`，诊断编号 142862027；
  重试装成后**重启复检再次 OOM**，编号 180194307；横幅在重启后才清除）。
- 同期 SQLite 连接池饿死：`unable to grant a connection ... for 60s`。
- 应用基线 Java 堆 ~130MB（上限 192MB），manifest 无 `largeHeap`；峰值来源未定位
  （已排除：待处理列表不加载图片；dense 模型件走 mmap 不计入 Java 堆）。

## 9. 下半段结论（断在哪）

在本机（test_device，192MB 堆上限）+ 本轮模型（推理型 deepseek-v4.1-flash）的组合下：

- ✅ 模型配置、连通、能力测试（含视觉）通过；
- ⛔ **分题 / 整理**：推理模型的预算与超时口径不匹配 + 严格响应契约 ⇒
  TIMEOUT / INVALID_RESPONSE 为主；成功时也会给出 `NEED_MORE_IMAGE` 质量判定
  （用户给的是带手/裁边的试卷页，题面常被判不完整）；
- ⛔ **入库 / 复习 / 判定 / 档案**：因整理未通过 + 知识包不可用（自动分类暂缓），
  本轮不可达。

**修复与登记**：KD-31 已修（`64c79456`）；KD-32（内存天花板）、KD-33（推理模型兼容）
已登记未修。**换用非推理模型 + 解决内存天花板后重跑下半段**是继续走通的先决条件。

---

# 第三段（2026-10-13）：KD-32 内存天花板与 KD-33 推理模型链路的修复验收

> 缘起：第二段登记的两条缺陷本轮一起修（两簇共 24 个文件：模型链路 16 个、内存修复 8 个；
> 截至本节记录时**均未提交**——`git status --porcelain` 逐文件核对：22 个 `M` + 2 个新增
> 测试 `??`，见 §14）。数据来源分四层，本节逐条标注：
> **① 走查**——test_device（emulator-5554，APK `localFirstDebug`，214,625,307B，构建于本机
> 10-13 02:24），2026-10-12 18:29–19:28 UTC（＝本机 10-13 02:29–03:28）；
> **② 独立核对**——从设备 DB、logcat、存档截图、`build/mem-sample.txt` 与源码逐条复算，
> 并更正走查的 7 处数字（§12）；
> **③ 独立复核**——静态读码 + Gradle 编译/单测/两条仪器化套件实跑（命令见 §13）；
> **④ 强门禁**——脚本跑：assemble + `:core:data` 全量回归通过=true（既有红
> `DenseRecallRerankerTest` 点名、不计）。
> 本节记录者只回读了源码行号与本地证据（APK mtime、截图清单、`mem-sample.txt` 列复算、
> 24 个文件 git 状态），**未重跑**设备命令与 Gradle；凡标"未复核／未验证"处即没有独立证据。

## 10. 本轮改动

### 10.1 模型链路（→ KD-33）

| 主题 | 落点（已回读源码） |
| --- | --- |
| 防呆上限 120s → 600s | `MODEL_TASK_CEILING_MILLIS = 600_000L`（`RoomModelTaskRepository.kt:1077`，`withTimeout` 调用点 `:350`）；KDoc 明示"防呆安全上限，不是输出/时长预算" |
| 空闲看门狗 300s（只改注释） | `READ_TIMEOUT_SECONDS = 300`（`OpenAiModelTransport.kt:454`）＝非流式首字节前静默上限／流式相邻 chunk 间隔上限；绝对上限 `CALL_TIMEOUT_SECONDS = 600`（`:456`） |
| 截断 → 可重试 | `ModelFailureCode.INCOMPLETE_OUTPUT`（`ModelTasks.kt:106`）；`IncompleteModelOutputException` 与 `TRUNCATING_FINISH_REASONS={length,content_filter}`（`OpenAiModelResponseParsing.kt:144` 附近、`:167`）；非流式 `OpenAiModelProtocol.kt:626`、流式重建 `OpenAiSse.kt:135` 命中即抛；网关 retryable（`OpenAiCompatibleModelGateway.kt:279`、`:421`、`:850`）；`ModelFailureRecovery.kt:29`；文案 `AppFailure.kt:258` / `TutorLobbyRoute.kt:428` |
| 影响面闭合（点名行号之外补的一处） | `toGatewayEvent`（`OpenAiCompatibleModelGateway.kt:417-421`）在 `catch(_: Exception)`（`:422`）前先分流 `IncompleteModelOutputException`——不分流则"有 content 的截断"（非流式与流式终态两条真实路径）仍被吞成 INVALID_RESPONSE |
| `reasoning_text` 拼写 | `OpenAiSse.kt:66-68` 认 `reasoning` / `reasoning_content` / `reasoning_text` |
| 归因日志（A4） | 仓库层 INVALID_RESPONSE 出口写异常类+消息（`RoomModelTaskRepository.kt:546-555`）；网关兜底 catch 同口径（`OpenAiCompatibleModelGateway.kt:422-426`）；用户可见文案不动 |

新增用例全部落在既有测试文件内：OpenAiSseTest×6、OpenAiModelProtocolMockWebServerTest×2、
OpenAiSseTransportTest×2、OpenAiCompatibleModelGatewayTest×2、AppFailureMappingTest×1、
ModelFailureRecoveryTest×1（14 个函数名本节记录者已逐一 grep 核对存在；独立复核计作
"A2/A3/A4 新增 11 条"——两处计数口径不一致，以函数名清单为准）。

### 10.2 内存修复（→ KD-32）

- **失败路径也释放包对象图**：`releaseResidentPacks()` 挪进 `finally`
  （`SmartMistakeBookApplication.kt:483-491`、`:658`）。诊断口径：安装失败态 idle Dalvik
  111,350KB vs 快路径健康 8,068KB，随后一次只用 11.7MB Java 堆的批量导入被这 ~100MB 挤到 OOM。
- **知识库材料差分改分页**：按 stableCode 分段、每段 500 行
  （`MATERIAL_READ_PAGE_SIZE`，`RoomKnowledgeContentReconciler.kt:403-415`、`:600`）；
  `groupBy` 换排序一次 + 二分定位（`:395-401`、`:580`）；DAO 半开区间分页查询替代
  LIKE（`KnowledgeTeachingMaterialDao.kt:73-97`）。逐行判据与退役集合口径逐字不变。
- **导入解码采样**：`import()` 按 `MAX_DECODE_EDGE_PX = 2048` + 纯函数
  `boundedDecodeSampleSize` 设 `inSampleSize`（`AndroidCanonicalAssetVault.kt:102-113`、
  `:492`、`:509`）——3072×4096（ARGB_8888 50.3MB）→ 1536×2048／12.6MB。
- KDoc 契约同步（`BundledKnowledgeBaseInstaller.kt:80-91`、`BundledKnowledgePackResources.kt:100-103`，无行为改动）；
  新增两个 JVM 测试文件：`RoomKnowledgeContentReconcilerMaterialScanTest`（4 例）、
  `CanonicalDecodeSamplingTest`（5 例）。

## 11. 设备实测数字

### 11.1 安装与启动

- `adb install -r` → `Performing Streamed Install / Success`；`lastUpdateTime`=2026-10-12
  18:29:05 UTC、`firstInstallTime` 仍为 2026-10-11 04:08:12（未重装）。数据保留：
  `model_task` 13 行 → 18 行；`problem_draft` 55 行 → **90 行**（88 `EDITING` + 2 `ABANDONED`）；
  走查报告里的"88"是 UI 计数（「88 道临时题记录」）不是表行数，表查询以 90 行为准。
- 启动 90s+ 无「本地知识包尚未准备好」横幅（截图 `w01-launch-90s.png`）；
  `grep -ci 'Bundled knowledge install failed'` 在存档 logcat（18:29:28–18:50:43，7502 行）
  与当前缓冲（19:11:28–19:27:52）均为 **0**。进程 pid 27462 连续（启动实测 18:29:42，
  走查报 18:29:27 差 15s——以 /proc 反推值为准）。

### 11.2 知识包全量安装（第一处峰值）

- APK 内 `knowledge/moe-2025-update-manifest.json` 的 `contentVersion`=32682dbc9b1a7d05
  vs 设备旧戳 627d8d43503e41c1（不等 → 必走全量）。
- logcat：`packs fully parsed in 29059 ms (2 packs)`（18:30:17）+
  `install finished in 1133477 ms (full path: parse + reconcile)`（18:48:40）
  ——**全量安装约 1,133s（≈18.9 分钟）**。
- `content_install_state`：`moe-2025-four-subjects-v1|32682dbc9b1a7d05|1791830920718|0`、
  `moe-2020-foundation-v1||1791830288532|0`；台账 `knowledge_search_feature`=623,774 ／
  `knowledge_node`=4,187 ／ `knowledge_teaching_material`=59,016。
- 全程无 OOM、无失败横幅、进程未死。
- 安装窗口的 Java 堆采样（走查报 Dalvik Alloc 86,377→107,245→107,094→107,094→120,968 KB）
  ——**无留存采样件，独立核对未能复核**（仅能确认 192MB 口径：`dalvik.vm.heapgrowthlimit=192m`）。

### 11.3 能力测试与真实评估

- 大模型设置（opencode / deepseek-v4.1-flash / `https://opencode.ai/zen/go/v1`）→
  uiautomator 出现「测试通过：图片读取和讲解都可用。」；logcat 三探针全 200：
  structured `status=200 bodyBytes=727`、image `bodyBytes=567`、tools `bodyBytes=734`；
  DataStore 三个 verified 标记（image/structured/function calling）均为 true（独立核对字节级实读）。
- 单页整理拿到真实评估（非「这次没有完成」）：`第7题只拍到A、B两个选项，C、D选项及作答区未入镜`
  ＋`题目内容还没拍全。继续补拍下一页，已保存的页面不会丢失。`（截图 `w21-organize.png`，
  19:09:50）；DB 佐证 `output_snapshot`：decision=NEED_MORE_IMAGE、issues[0]=MISSING_OPTIONS/
  BLOCKING、suggestedActions=[ADD_IMAGE]、modelVersion=deepseek-v4.1-flash，耗时 544.6s、SUCCEEDED。

### 11.4 长思考不再被 120s 砍掉（KD-33 设备实证）

- 装机后共 5 条 CAPTURE_ASSESS：SUCCEEDED 136.9s／191.2s／544.6s，INVALID_RESPONSE 55.9s／113.9s。
  三条 >120s 的均落成功终态——旧口径下必判 TIMEOUT。
- 全库唯一 `failure_code='TIMEOUT'`：`model-task-b01b8c4285ec0d31e0b83ffdac156eb2`，
  created 2026-10-11 15:11:49（装机前）。
- 但**新截断分类（INCOMPLETE_OUTPUT）本轮无设备实证**——5 条任务里没有该形态，只有单测覆盖。

### 11.5 整卷分题与批量导入

- 「开始分题」→ 边界评估任务（subject=`batch-boundary-d15aaac1bd58d0e7e171d0ce75fe0b30`）
  created 19:11:28.230／updated 19:13:22.082＝113.9s 后 PERMANENT_FAILURE/INVALID_RESPONSE；
  根因非超时：`IllegalArgumentException: Only a split decision may contain two to twelve
  question regions`（`core/model/.../CaptureAssessmentTasks.kt:126`，logcat 19:13:22.077）
  ——模型返回的 questionRegions 数量/形态不满足 2–12 约束，**解析严格度问题，本轮未修**。
- 分题结果与落账仍在：`split_import_job` 三条（`:1` READY count=11、`:2` READY=10、
  `capture-split:capture-split:batch-split:…:1` READY=11）、`split_import_question` 共 36 行
  =11+10+2+2+11，两页 `output_snapshot` 的 questionRegions 长度恰为 11/10；
  UI「切分完成，勾选要录入的题目 11/11」+ 第 0–3 题（截图 `w22-split-result.png`）。
  双前缀 job 的 `created_at`=19:02:07.832（跟随页 1 的 SPLIT 判定落账，**不是**分题动作新增）；
  双前缀来源：`CaptureModelTaskPolicy.kt:70` 与 `RoomCaptureWorkflowRepository.kt:360` 两处都加前缀。
- 批量导入 3 张 3072×4096（MediaStore `_id` 1000000105/106/107 =
  IMG20260810005902/005245/005114）：DB 三页全 READY、failure_code 空、attempt_count=1；
  3 条新 `canonical_source_asset` 均 **1536×2048**（源 1/2 采样，采样生效）、
  byteSize 1,512,061／1,372,183／1,294,520。
  走查引用的 UI 文案「已保存 3 页 · 需重试 0 页 · 剩余 0 页」**未出现在任何留存截图**，
  只能由 DB+源码推出（`BatchImportRepository.kt:100-103` 口径 + `BatchImportRoute.kt:557-560`）；
  且"已保存"只表示原图落库——**页 0 的模型评估实为 INVALID_RESPONSE（55.9s）**，
  只有页 1/页 2 评估成功（SPLIT 11/10）。

### 11.6 内存峰值与崩溃计数（第二处峰值）

- `build/mem-sample.txt`（80 个采样，t=3s…240s，18:59:37–19:04:27）**按同文件复算列最大值**：
  Native Heap Alloc 峰 **157,759 KB**、Heap Size 峰 192,772 KB（t=30s）；
  Dalvik Heap Alloc 峰 **25,670 KB**（走查报 19,116 KB 与文件不符——独立核对指出系读错列，
  方向结论不变）。列语义由 Native 一行的同一映射自证（第 6 列 Size／第 7 列 Alloc）。
- 对照 KD-32 崩溃窗口：Dalvik 134–147MB／192MB 上限；native 峰 195,449 KB（诊断口径，
  现记录在 `AndroidCanonicalAssetVault.kt:105-106`；docs/ 下无此数）。
- `FATAL EXCEPTION`=0、`OutOfMemoryError`=0（logcat -d 主缓冲 + `-b crash` 0 行 +
  `/data/tombstones`、`/data/anr` 为空且 mtime 停在 10-11）；pid 27462 全程未变。

## 12. 独立核对对本轮走查的更正（引用本节数字前必读）

1. **最新一条 `model_task` 的真实状态是 PERMANENT_FAILURE/INVALID_RESPONSE**——按 `created_at`
   与 `updated_at` 排序都是同一条（19:13:22 的整卷边界任务）；走查"按 updated_at 最新为
   SUCCEEDED"的补述不成立。ask 原句查询现返回 `INVALID_RESPONSE / INVALID_RESPONSE / SUCCEEDED`
   （走查输出是 19:11 分题动作之前的快照）。
2. **Dalvik 峰值列读错**：应为 25,670 KB（见 §11.6）。
3. 「已保存 3 页 · 需重试 0 页 · 剩余 0 页」无截图留存；页 0 评估为 INVALID_RESPONSE。
4. `batch_import_job batch-6ba9eefe…` 现为 **COMPLETED**（updated 19:13:22.126，走查记 PROCESSING），
   页 0/页 1 的 `boundary_after_status=FAILED`、页 2=PENDING；双前缀 capture-split job
   的 `created_at`=19:02:07（不是分题动作新增）。
5. `problem_draft` 表 90 行（88 `EDITING`）；走查的"88"是 UI 计数不是表计数。
6. 进程启动时刻实为 18:29:42（非 18:29:27）。
7. 知识包安装窗口堆采样无留存件、KD-32 的 native 195,449 KB 在 docs/ 下查无（现已见于源码注释）
   ——两项**无法独立复核**，其余结论（120s 放宽、两处峰值未打爆、全量安装成功、
   批量导入无崩溃、真实评估非假象）独立核对均逐条复算通过。

## 13. 仍未验证 / 遗留

**KD-33 侧（未覆盖项明细在 KD-33 条目里，此处只列验收缺口）**

- `INCOMPLETE_OUTPUT` 新分类**无设备实证**（装机后 5 条任务无该形态，仅单测）。
- 截断分流只落 Chat Completions 协议族；Anthropic（stop_reason=max_tokens）／
  Gemini（finishReason=MAX_TOKENS）／Responses（status=incomplete）未做分类（`AnthropicMessagesProtocol.kt:108`、
  `GeminiGenerateContentProtocol.kt:121`、`OpenAiResponsesProtocol.kt:96`）。
- 整卷「开始分题」的边界评估仍以 INVALID_RESPONSE 失败（2–12 区间约束，`CaptureAssessmentTasks.kt:126`）——未修。
- `MODEL_TASK_CEILING_MILLIS` KDoc 把"每轮 600s"写成"一条任务 600s"（实现上 `withTimeout`
  只包住一轮 `gateway.execute`，MAX_DISPATCHES=6 ⇒ 一条任务最多 ≈6×600s）——注释口径不一致。
- 流式 `tool_calls` 轮 `content=null` 形态（`OpenAiSse.kt:127-141`）仍判不可重试——是否真实发生未验证。
- DB 读路径 `ModelTaskTransactionDao.kt:435` 裸 `valueOf` 无降级兜底（仅降级方向风险）。

**KD-32 侧**

- `crop()`（`AndroidCanonicalAssetVault.kt:141-144`）与 `persistCleanImageBytes`（`:219-220`）
  仍整幅解码（允许至 8192/16MP，单帧≈64MB）；采样只覆盖 `import()`，批量拆页走的 crop 未覆盖。
- 采样路径无设备级用例（仪器化夹具为 40×80 小图）；>2048 源图的**分辨率语义变化**对
  OCR/裁剪精度的影响未验证。
- 知识包失败重试需重新全量解析（~16.7s parse 口径、~100MB 对象图；停在 RecoverableFailure 时
  每次 onResume／横幅重试都重付一次——`MainActivity.kt:33-34` → `refreshStudyExperience`）——有意取舍。
- 仪器化用例覆盖 3 页插入与幂等，无"跨段退役"断言。
- 未动 `android:largeHeap`、未改 manifest（诊断不支持"工作集本身超 192MB 且没有更小的修法"）。

**本轮执行的验证 vs 未执行项（避免误读为通过）**

- **未跑**：其余仪器化套件、lint、`tools/` 下 Python 测试；`core:data` 整模块编译；
  knowledge 包两处 KDoc-only 改动未单独编译。
- **本轮实跑（独立复核，命令原文与结果）**：
  `./gradlew.bat :app:compileLocalFirstDebugKotlin --rerun-tasks --offline`（BUILD SUCCESSFUL）、
  `:core:model:testDebugUnitTest` 416/0、`:core:database:testDebugUnitTest` 181/0、
  `:core:data:cleanTestDebugUnitTest :core:data:testDebugUnitTest` 731/0、
  `:feature:tutor/:feature:capture/:feature:library` 209/110/39 全 0 失败、
  `:core:database:connectedDebugAndroidTest --class=KnowledgeContentReconciliationInstrumentedTest` 7/7、
  `:core:data:connectedDebugAndroidTest` 17/17。
- **强门禁（脚本跑）**：assemble + `:core:data` 全量回归通过=true
  （既有红 `DenseRecallRerankerTest` 点名、不计）。
- 独立复核已核实**不成立**的怀疑点（备查）：分页段划分/前缀区间等价（纯函数 + 1,200 材料设备用例；
  上界 `:`→`;` 之间无字符）、排序假设安全（stable_code 为 `[a-z0-9-]+` slug）、
  `releaseResidentPacks` 两路径调用无并发破绽、无自动重试回路（RETRYABLE_FAILURE 不在
  TUTOR_LIVE_TASK_STATUSES，重试是用户动作且预算重置）、既有测试无截断夹具被偷改期望。

## 14. 本段结论与 KD 状态

- **KD-33 → fixed（2026-10-13，OpenAI Chat Completions 协议族；引用提交待补）**：
  600s 防呆上限 + 300s 空闲看门狗 + `finish_reason` 截断归为可重试 `INCOMPLETE_OUTPUT` +
  `reasoning_text` 拼写。设备实证是 timeout 口径（3 条 >120s 成功、0 条 TIMEOUT）；
  截断分类与协议族缺口见 §13。
- **KD-32 → 保持 open（两处峰值已修，剩余见条目）**：知识包全量安装 1,133s 跑完无 OOM/无横幅、
  批量导入 3 张 3072×4096 无崩溃（Native 峰 157,759 KB vs 崩溃 195,449 KB；
  Dalvik 峰 25,670 KB vs 崩溃 134–147MB）+ 全程 0 FATAL/0 OOM。未达项：crop/重绘整幅解码
  未覆盖、采样路径无设备用例、分辨率语义变化影响未验证。
- **两簇改动截至本节记录时未提交**（`git status` 逐文件核对：模型链路 16 个文件全 `M`
  ——含 `feature/tutor/.../TutorLobbyRoute.kt`；内存修复 8 个文件——6 个 `M` + 2 个新增测试 `??`）。
  提交后需把提交号补进 KD-32/KD-33 条目（"引用提交待补"）。

---

# 第四段（2026-10-13 设备 / 2026-10-14 记录）：4C 第一阶段——分题阻塞修复验收 + 真实模型下的下游走查

> 缘起：4C 批的第一、二步（真机核验 + 金标集/准确率门）连同薄切片的收口——分题阻塞修复
> （A1–A3）、KD-32/33 小尾巴（B1–B4）、真机核验、真实模型下的下游走查、金标与门。
> 数据来源分四层，本节逐条标注：**①「真机核验」**（emulator-5554，APK `localFirstDebug`，
> 2026-10-13 装机；全文见 `docs/research/2026-10-14-4c-stage1-record.md` §2）；
> **②「下游走查」**（同机同包，16:10–16:45；截图在 `build/walkthrough-shots/`，部分黑帧）；
> **③「独立核对」**（对①②的设备 DB / crash buffer / logcat / artifact / 源码逐条复核）；
> **④「独立复核」**（对代码改动的静态读码 + Gradle 实跑）。
> 本节记录者只回读了源码行号与本地证据，并实跑了 `:core:data:compileDebugAndroidTestKotlin`
> （BUILD FAILED，见 §21）与金标 validator（PASS）；**未重跑**设备命令与 Gradle 测试套件。

## 15. 本轮改动（两簇 + 小尾巴）

### 15.1 分题阻塞修复（A1–A3 → KD-34）

| 主题 | 落点 |
| --- | --- |
| A1 双变体提示词 | `OpenAiModelTaskAdapters.kt:100-139`：多页关系请求（`followingSourceAssets` 非空）专属变体——decision 限 PASS/RECAPTURE/NEED_MORE_IMAGE、禁 SPLIT、questionRegions 空、不报 MULTIPLE_QUESTIONS；`followingPageRelations` 规则与单页逐字相同 |
| A2 契约减负 | `CaptureAssessmentTasks.kt:110-127`：删 2..12/唯一/尺寸/重叠四条 require 及常量，只留 `decision == SPLIT || questionRegions.isEmpty()` 形状 |
| A2 职责下移 | 判据移入 core:domain `isUsableSplitRegionSet()`（`CaptureWorkflowRepository.kt:375-379`）；两个自动拆分入口过门：`captureAutoSplitRegionsUsable`（`CaptureModelTaskPolicy.kt:76-78`；`CaptureModelTaskCommands.kt:63-70` 不通过 → `CAPTURE_SPLIT_FAILED`、不切、整页草稿、可重试、手动框选保留）、`BatchSplitRecognizer.kt:86-87`（不通过 → `BatchSplitOutcome.NotASplit`） |
| A3 回归钉子 | `ModelTasksTest.pageRelationAssessmentToleratesPageWideQuestionRegions`、`OpenAiCaptureAssessmentAdapterTest` 两例、`BatchImportRepositoryInstrumentedTest.pageWideSplitRegionsDoNotKillThePageBoundaryEvaluation`（**该例编译不过，见 §19/§21**） |

（根因、curl 复现读数与判定复刻见 KD-34 与 4C 记录 §2。）

### 15.2 KD-32/33 小尾巴（B1–B4 → 两条 KD 的 2026-10-14 补记）

- B1 三协议族截断分流（Anthropic `stop_reason=max_tokens` / Gemini `finishReason=MAX_TOKENS` /
  Responses `status=incomplete`）→ `IncompleteModelOutputException`；
- B2 `crop()`/`persistCleanImageBytes()` 与 `import()` 同一采样口径
  （`AndroidCanonicalAssetVault.kt:108/145/226/509/520-521`）；
- B3 `ModelTaskTransactionDao.buildSnapshot` 未知 failure code 降级 UNKNOWN（`:440-441`）；
- B4 `MODEL_TASK_CEILING_MILLIS` KDoc 改"每一轮"口径（`RoomModelTaskRepository.kt:1066-1083`）。

## 16. 真机核验要点（4C①；全文见 4C 记录）

- 环境前提与本轮任务书冲突：emulator 为 `-wipe-data` 启动、包未装、无模型配置 ⇒ 核验方用应用内
  设置重建了配置（值同任务书指定；key 未回显、未入库）——"是否算『重新注入』"留给 owner 判读。
- **边界评估不再 INVALID_RESPONSE**：`batch-boundary-79303d32…` SUCCEEDED / 54,881ms /
  decision=PASS / `followingPageRelations=[NEXT_QUESTION×6]`；页 0–5 `NEXT_QUESTION`、
  page6=PENDING。（§13 遗留的"边界评估仍 INVALID_RESPONSE"至此闭环。）
- 口径偏差：任务书写"两页窗口/两张图"，实际失败请求带 **3 张图**（3 页→2 个 boundary→同窗一次
  请求）；补跑 2 图同形态失败 ⇒ 不依赖窗口大小。
- 页级 organize 仍有 2/7 不可重试 INVALID_RESPONSE（未归因）；导出 7 页 8 框，只有 2 页有分题
  输出；本次 relations 全 `NEXT_QUESTION` ⇒ 没有真的发生跨页合并。
- 3 张截图黑帧（应用窗口渲染失效，重启恢复）；设备改动（LatinIME disable）已还原。

## 17. 下游走查（入库 → 复习 → 判定 → 档案）

前提：从 4C 核验的 7 页批次里，page_index=1（金标 `…004304` 页）先经页面草稿「存入错题本」
入库 1 条（problem / revision / entry / practice_unit 各 1）。**断在第三步「判定」**；入库与
复习两步成立、档案一步如实为空。

### 17.1 入库

| 检查 | 结果 | 证据 |
|---|---|---|
| 有该题（库） | 通过 | problem 1 行 `problem-69b9cf95…`（subject=GENERAL，created 16:10:39 GMT）；entry 1 行（ACTIVE，`source_key=capture:draft-dd39411583c976626ea29f5e1a70f9c6`）；practice_unit 1 行（unit_key=whole-problem）；revision 1 行（title「16. 高效广谱的氯胺型缓释消毒剂二氯海因 DCDMH 的制备与含量测定」，source_type=CAPTURE_CONFIRMED） |
| 界面可见 | 通过（**冷启动后**） | 冷启动后错题本「1 道题 / 科目 GENERAL (1) / 16. …」；`library_catalog` count=1 |
| **异常：入库后同进程内仍空态** | 失败（已观察，未受控复现） | 题 16:10:39 已落库；同一进程 16:21:21 与 16:23:45 两张截图仍「还没有错题」；冷启动即出现。读码归因（未验证）：`LibraryRoute.kt:95-97` mistakeCount ← `uiState.totalCount`、`:128-132` 据此判 CATALOG_EMPTY；`LibraryViewModel.kt:112-113` bindRepository 同实例直接 return、返回 tab 无刷新入口 |

### 17.2 复习

| 检查 | 结果 | 证据 |
|---|---|---|
| 新题排入「今日复习」、理由含 NEWLY_ADDED | 通过 | review_plan 2 行（local_date=2026-10-13、planner_version=review-planner-v4）；review_queue_item 1 行（ordinal=0、PLANNED、MEDIUM、estimated=60s）；review_queue_reason 4 条 = CALIBRATION_CHECK / MISSING_KNOWLEDGE_EVIDENCE / **NEWLY_ADDED** / WEAK_KNOWLEDGE |
| 「开始今日复习」→ 会话建立、停在第 1 题 | 通过（会话建立；题目未推进） | `review-session-d537c2d2…` IN_PROGRESS/current_ordinal=0/state_version=0；UI「第 1 / 1 题 · 复做你保存的原题」+ 原题全文 +「去讲题判定」 |

### 17.3 判定（断点）

| 检查 | 结果 | 证据 |
|---|---|---|
| ① 「去讲题判定」首轮讲解 | **失败** | 点入后直接「这道题的讲解没有准备好。」（`tutor_conversation` +1 行、`model_task` 无新行）；点 composer「重试」→ `model-task-4249c8a1…`（TUTOR_PLAN，流式「正在思考…」）最终 PERMANENT_FAILURE / VALIDATING_OUTPUT / attempt_count=1 / INVALID_RESPONSE / retryable=0 /「这次没有准备好题面，请重新处理」 |
| ② 失败卡的「重试」 | **失败（App 崩溃）** | crash buffer 唯一一条 FATAL EXCEPTION（10-13 16:29:47.276，PID 13813）：`ImmutablePayloadConflictException: model_task tutor-plan:07c06a7e…tutor-plan-v18-figure-conditional:0 already exists with a different payload`，`ModelTaskTransactionDao.kt:233`（`sameImmutableRequest` 守卫；异常未捕获到主线程），进程消失。读码：同 requestId + 不同 payload（含 occurredAt/createdAt）即抛 |
| ③ 改走 composer「重试」 | **失败（判定未产生）** | 崩溃重启后首页变「继续今日复习」（16:32:33）、会话仍 IN_PROGRESS；重进「去讲题判定」不建任务；再点 composer「重试」→ 第二条 TUTOR_PLAN `model-task-442cef59…` SUCCEEDED（16:39:33）；讲题页出讲解 + 本地检查题（4 选项）；点提交 choice-2（`$n(Na_2S_2O_3) = 2\,n(DCDMH)$`）后选项全 `enabled=false`、零持久化：`tutor_message` 仍只 1 行（ASSISTANT，choice 字段全 NULL）、无新 model_task / assessment_event / presentation；等待 ≥2 分钟无变化 |
| ④ 结算核对 | **失败（全空）** | attempt_submission 0、review_log 0、attempt_event 0、assessment_evidence_attribution 0、independent_correct_observation 0；review_session 仍 IN_PROGRESS/0/0；队列项仍 PLANNED、reason_snapshot 不变（`review_log.source_kind` 列在但无行可查） |

旁证两条（机制未验证）：每次进讲题都新建 tutor_conversation（共 4 行、3 行 last_turn_ordinal=0）；
崩溃重启后需 composer 重试才能重走。

### 17.4 档案

| 检查 | 结果 | 证据 |
|---|---|---|
| 学习掌握无知识点行 | 通过（如实为空） | 整页空态「还没有学习记录 / 你做过并保存的题会自动整理到相应科目和知识点」 |
| 掌握度/归因表行数与归因 | 通过（0/0，与既有设计一致） | `learner_knowledge_mastery_state=0`、`applied_attempt_record=0`、`learner_problem_memory_state=0`、`problem_classification_binding=0`；`knowledge_node=4168`（不是缺目录）；唯一归属是伪兜底 `practice_unit_knowledge_binding` → `pseudo:GENERAL` / PSEUDO_FALLBACK。归因两层：本次判定根本没结算（`TutorJudgedReviewSettler.kt:88` 无判词 → NO_VERDICT 不写不推进）；即便结算，该通道按设计也不写归属（`:128-129` `attributions = emptyList()`，注释"只驱动题目级排期，不碰知识点掌握度"） |

### 17.5 本段走查结论

断在第三步「判定」：讲题首轮失败 → 失败卡的唯一出口「重试」**直接把 App 打崩**
（`ImmutablePayloadConflictException`）→ 换 composer 重试能出题，但提交后 UI 锁死、零持久化
⇒ attempt_submission / review_log / review_session 全部零推进。没有清数据、没有改代码；
唯一干预是崩溃后 `am force-stop` + `am start` 重启，以及一次冷启动用于区分"陈旧计数"与"查询为空"。

## 18. 金标集与准确率门（4C②；全文见 4C 记录 §3-§4）

- 金标集 `tools/split_golden/`（工作树未提交）：7 页 16 框，sha256 `f9e92c47…`，validator
  C1–C6 全绿（本记录者实跑，退出码 0）；证据等级 model-labeled, no human expert。
- 度量：题数完全一致率 0.2857（2/7）、边界 IoU≥0.5 题占比 0.4375（7/16，2 页内 7/8=0.875）、
  切残率 0（0/16）。
- 门（计划建议值 ①≥0.9 且 ③≤0.05）→ **false**；**门后自动化未启用**（本批不做 4C③）。

## 19. 更正与复核（引用前必读）

**独立核对（对真机核验/下游走查材料）**：核心事实逐条成立，更正三处：
1. 「canonical_source_asset 11 行」不成立 → 实测 15 = 7 整页 + 8 窄条（与"2 页各 4 框"自洽）。
2. 「入库③」点名的 artifact 是 launcher 截图（引用名错配）——观察本身另有 16:21/16:23 两张空态截图证据。
3. `known-defects.md:1233-1238` 引用错位（该处实为 KD-33 症状段）；旧抛点文本在 git HEAD `CaptureAssessmentTasks.kt:126`。
4. 部分可核：logcat 已轮转（`IllegalArgumentException=0` 只覆盖 16:23:54 之后）；能力测试
   200/401 原始行不可见；金标文件在核对方检索路径（docs、knowledge-production、out/build）
   未找到——实际在 `tools/split_golden/`（本记录者已验证）。
5. 独立核对另发现 draft-583b094e 的 `CAPTURE_PARSE=PROVIDER_REJECTED_INPUT`（真机核验材料未提及）。

**独立复核（对代码）**：A1–A3 方向正确、两个自动拆分入口都有消费侧判据
（`CaptureModelTaskPolicy.kt:78/88`、`BatchSplitRecognizer.kt:86/96/110`，无漏掉的调用点）；
B1–B4 无语义误伤。未闭合四处（详见 KD-34/33/32 补记）：A3① 钉子编译不过（high，本记录者已
复现）；多页轮契约容错只覆盖 SPLIT+区域 形状；B1 只覆盖非流式（三族流式重建体不带声明）；
A3 对已落库 INVALID_RESPONSE 的边界窗口无效。另核实：被删符号
（`isUsableQuestionRegion`/`overlapRatioOfSmaller`/`MIN|MAX_SPLIT_REGION_COUNT`）全仓零残留引用；
`ModelPromptPolicyVersions.CAPTURE_DOCUMENT` 未 bump（涉及已落库 manifest 授权，留给 owner）；
手动框选与「自动/手动框选」选择卡不受区域判据影响（`CaptureScreen.kt:556-568` 仍只在 Run 时出卡）。

## 20. 本段结论与 KD 状态

- **KD-34 登记为 fixed（2026-10-14，提交待补；A3① 钉子编译不过）**：跨页关系判定复用单页分题
  契约 → 修提示词（A1）+ 职责下移（A2）+ 钉子（A3）；设备实证边界评估 SUCCEEDED。
  **A3① 端到端钉子在当前树编译不过 ⇒ "端到端回归已覆盖"不成立，修好前不得如此声称。**
- **KD-33 补记**：①⑤⑥ 未覆盖项已补（B1/B4/B3，工作树未提交），残留见条目；②③④ 维持；
  新包页级 CAPTURE_ASSESS 2/7 不可重试 INVALID_RESPONSE（未归因）一并记录。
- **KD-32 补记**：① 已补（B2，工作树未提交）；② 仍缺——设备级用例被 androidTest 编译错误挡死。
- 下游走查新增三条 KD 级观察（本段 §17）：`ImmutablePayloadConflictException` 崩溃（失败卡重试）、
  讲题提交零持久化锁死、错题本同进程陈旧空态。**三条均未登记 KD 编号，本次仅如实记录，
  是否成条/归属留给 owner。**

## 21. 本轮执行的验证 vs 未验证（避免误读）

- **本记录者实跑**：`./gradlew.bat --offline :core:data:compileDebugAndroidTestKotlin` →
  BUILD FAILED（`:500:39`/`:509:34` 两处 Unresolved reference，见 §19）；
  `python tools/split_golden/validate_split_golden.py` → PASS 退出码 0；
  读取类核对（源码行号、golden JSON、`build/split-eval/*`）。
- **未跑**：设备命令（全部引用他方材料）；Gradle JVM 套件（引用独立复核实跑：`:core:domain:test`
  7/0、`ModelTasksTest` 31/0、`OpenAiCaptureAssessmentAdapterTest` 5/0、`CanonicalDecodeSamplingTest`
  6/0、三协议族测试 13/11/12、`CaptureModelTaskPolicyTest` 10/0、`ModelTaskIntegrityTest` 5/0）；
  准确率度量重算；双标注过程重放。
- **不可证**：logcat"全程无异常"（ring 轮转）；黑帧时刻的截图断言（以 dump 文本为准）。

**修复与提交状态**：本段涉及的代码改动（A1–A3、B1–B4）截至本节记录时均为**工作树未提交**；
KD-34/32/33 的提交号待补。
