# 4E.2 MVA-JS-STATIC 剩余项排序（批次 28 R2，2026-10-28）：painter 落层后的分批规划

> **性质**：只读审计 + 本文件唯一写盘产物。零代码、零 Gradle、零设备执行、零 git 写操作。本文件是**排序建议**，不是排期裁定；批次归属由主会话定（沿准入清单 §0"责任人轨是建议归属"同一语义，`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:21`，下称**清单**）。本文件**不是广告许可**：JS 仍 `UNAVAILABLE`、不广告；MVA 不成立维持。
>
> **时点**：HEAD `8116ad98`（= origin/main 本地跟踪引用，未 fetch）；工作树为批次 28 基线冻结态（673 行 = 201 M + 472 ??，基线 `docs/research/2026-10-28-batch-28-baseline.md:5` 实测，本文件未复冻）。批次 28 的 T1（修复后设备全绿复跑）与 T2（painter 落码）**尚未发生**——本文按"批次 28 两项完成后"为排序起点，这正是任务命题"painter 落层后"。
>
> **输入**：计划 `.zcode/plans/plan-2026-10-28-batch-28-scenev1-device-painter.md`（21 行已完整读）；清单 v3（69 行，含附录 D 批次 27 补记）；复判 `docs/research/2026-10-27-mva-reeval.md`；批次 27 记录 `docs/research/2026-10-27-batch-27-record.md`；批次 28 基线（上引）；契约 `docs/current/visual-generation-contract.md`；KD 台账 `docs/known-defects.md`（KD-72 `:2746`、KD-75 `:2796`、KD-76 `:2819`、KD-77 `:2855`，本文件写作前 `grep "^## KD-7[256]"` 实测锚）；R1 `docs/research/2026-10-27-scenev1-compiler-design.md` §4（宿主映射点，经基线 §5 转录核对）；本轮只读 grep（§5）。
>
> **口径**：`已落` = 当前树有代码且有测试载体（本轮 grep 实测），执行证据一律转录 E-file，不是本轮实跑；`未落` = 源码零命中（本轮 grep 实测的记"本轮复核"）。**0 项在本轮转"通过"**；MVA 成立条件仍是清单 §1.4（`:85`）的完整链在设备闭合且广告面与执行面一致。

---

## 0. 结论速览

批次 27 把 MVA 静态子集的**编译器/SDK、真实内容度量、深冻结**三块落层（记录 §3.1–3.3）；批次 28 在飞两项（设备全绿复跑 + painter）完成后，"执行引擎能出 DrawOps"这一段就闭合了。但清单 §1.4 的 MVA 判据是"**同一规则→模型选方法→一张图→保存重启→PDF**"的完整链——目前链上还断着**画（painter 只有 Canvas 半边）、存（持久化四表零命中，本轮 grep 复核）、导（PDF 回放与导出块零命中，本轮复核）、选（广告面六处全拒）**四环。剩余项按依赖排序为四批主线 + 两条尾部轨：

| 序 | 建议批次 | 内容（清单行 ID） | 依赖 | 阻塞/边界 |
|---|---|---|---|---|
| 0 | **批次 28（在飞）** | T1 修复后设备全绿复跑；T2 painter（DrawOps V1→Canvas + Rendered→Bitmap 冒烟） | — | 一切设备面的公共前提（批次 27 隔离 10 红未复验，记录 §6 #1） |
| 1 | **批次 29** | PDF 回放半场：painter 参数化到 `PdfDocument` page.canvas + 屏/PDF 一致门（S-04 往返 / S-06 PDF 半 / §2 行 6 / E-05 判据雏形）+ R-05 许可证载体（零成本并轨） | 批次 28 T2 | core:data visual / app visualjs 落点内可做；零热区 |
| 2 | **批次 30** | 持久化快照/引用核心：D-01 四表（DB v70+）/ D-02 身份+CAS / D-05 冷恢复（D-03 checkpoint/GC 可裁半） | 批次 29（RenderScene 可序列化往返） | core/database 8 M 开工即脏（整文件纳入或等空）；DB 68/18 已占用（基线 §1） |
| 3 | **批次 31** | 双后端执行装配：A-01～A-06/A-08/A-13 + P-11 前缀消费 + P-12 ②③ + M-04 度量诊断 + Q-01 policy 负向 | 与批次 30 互相独立，可换序 | 落点 core/data visual+model、core/model（本轮干净）；**广告开关不动**（仍 fail-closed） |
| 4 | **批次 32** | 纵向链与质量门：V-01 全链设备用例 + V-02 方法切换 + A-09 广告-执行一致 + A-10 逐阈值 + Q-04 六类 fixture；P-04/P-09/P-10 设备负向并入同轮 | 批次 29+30+31 | 设备轮复用同一夹具 |
| 5 | **发布矩阵批（挂起）** | R-04 R8 面（解除路径三选一，**主会话裁定**）+ R-01/KD-77 API 23（需装镜像）+ R-02/R-03 arm64/16 KB（本机原理性不可行） | 主会话裁定 + 硬件/网络窗口 | 本机不可采证面（批次 25 R4 定论） |
| 6 | **热区解锁批（挂起）** | S-06/S-07 UI 卡片 + X-01 归位（settings.gradle.kts）；E-01～E-04 导出衔接 + KD-75（core/export）；Q-06 布局/无障碍（core/ui）；D-04/D-06/V-03/V-04/X-02/X-03 | 热区空出或主会话授权 | 不在自由排序范围 |
| — | **持续/裁定项** | Q-05 120 题盲测（真实 Provider 凭据——UNVERIFIED 纪律）；KD-72 锁窗口（窗口驱动，批次 28 基线 §6 判"未开"）；KD-76 ③ 残余与 R3 文本升级（主会话） | 各归其位 | — |

排序理由（三条）：① **T1 全绿是公共前提**——批次 27 设备隔离 13 例 10 红是接线缺陷、修复已在树但**修复后设备复验 UNVERIFIED**（记录 §6 #1），在复跑绿之前任何新设备面都无从谈起；② **painter 是链上"画"环的解锁件**——它解锁批次 29 的 PDF 回放与屏/PDF 一致门（这是 MVA 链"PDF"一环的必要件，清单 §2 行 6）；③ **持久化（30）与双后端（31）互不依赖**，先后可换序，但都必须在 V-01 全链（32）之前；广告开关（`availableMethods` 加 JS）是**全部门绿后的最后动作**，不是批次 31 的动作（清单 §0 勾选规则 5、§2 行 10）。

**GitHub 优先纪律的结论（本轮复核）**：painter 不引第三方库——R1 §0 已核 14+ 候选全部否决为依赖（`docs/research/2026-10-27-scenev1-compiler-design.md` §0，记录 §2.1 转录）；本轮对 R1 引用的映射参考 lottie-android 做了 license/维护度复核：**Apache-2.0、35,757 stars、pushed 2026-02-15、未归档**（`gh api repos/airbnb/lottie-android`，本轮实跑）——仍适用为矩阵桥接参考，不构成新依赖。持久化用仓内既有 Room 链；PDF /AIGC 按 KD-75 fix direction **不引第三方库**（`docs/known-defects.md:2811`）。

---

## 1. 一、剩余项逐项（对照清单 v3 × 批次 27 落层后现状）

> 分组按任务命题的五桶给出；五桶未覆盖、但清单要求且 MVA 链必需的前提残余，单列为组 F 并注明超出命题五桶。

### 1.1 组 A：PDF 回放面（painter 下半场）

| # | 项 | 现状（本轮复核时点） | 依据 |
|---|---|---|---|
| A1 | **painter 的 PDF 半边**：同一 DrawOps 渲染器打到 `PdfDocument` page.canvas（清单 §2 行 6"同一 `RenderScene` 在屏幕与 PDF 的结构/位置一致"、S-06、E-05） | **未落**。painter 本体是批次 28 T2 的落码对象（计划 §1"T2 painter 落码…PDF 回放留后"）；本轮 grep `class AndroidScenePainter` 全仓 0；契约 §5.1 表"core:visual 承担回放"行维持"不存在"（契约 `:115`）。R1 §4 已给映射点："'PDF：同一 op 开关打在 `PdfDocument` page.canvas（painter 共用）；SVG writer 为 4E.2 附加交付'"（基线 `2026-10-28-batch-28-baseline.md:127` 转录原文） | 清单 `:107`/`:146`/`:179`；契约 §8 `:192`"PDF/SVG 不重新执行 JS，不重新采样曲线或移动标签" |
| A2 | **RenderScene/DrawOps 序列化往返**（S-04 后半："不可变序列化显示列表…编译金样 + 序列化往返 + 无函数断言"） | **部分落**。DTO 语义已立：`VisualDrawOps.kt` sealed 六原语（Save/Concat/Clip/DrawPath/Label/Restore，`:95-140`）+ `VisualDrawOpsValidator.kt`（536 行，词表/预算/有限性/配平/stats 复算，批次 27 记录 §3.1）+ `Rendered` 增 `drawCommandsJson`/`drawSchemaVersion`（`core/domain/.../VisualExecutionEngine.kt:245-246`）。**序列化往返（drawCommandsJson → 结构化 → 再渲染一致）无测试**；无函数/脚本断言由 sealed 词表结构性保证、无专项用例行 | 清单 `:144`；批次 27 记录 §3.1（本轮核 `VisualDrawOps.kt`/`VisualDrawOpsValidator.kt` 在树） |
| A3 | **core:visual 模块 + AndroidScenePainter 归位**（S-06 前半 + X-01 的 visual 半） | **未落**。`settings.gradle.kts` grep "visual" 0 命中（本轮复核）；`AndroidVisualExecutionEngine` 0 命中（本轮复核）。批次 28 painter 按基线落 `core/data/.../visual`（R1 建议归属）或 app visualjs，属**过渡态**——契约 §5.1 `:119` 归位条件不变："4E.2 真正开放 JS 前，把 Service/client/AIDL 迁入 core:visual-js（需改 settings.gradle.kts，须热区空出）" | 清单 `:146`/`:235`；契约 §5.1 `:110-119`；`settings.gradle.kts` 是热区 |
| A4 | **静态子集硬拒绝的口径差**（S-05） | **大部落层 + 一处待裁**。编译器已拒非空 `controls`（`visual-compiler.js:731-732` `SCENE_FIELD_CONTROLS "must be an empty array in V1"`）、整体拒 `form`（`:738` `SCENE_FIELD_UNAVAILABLE`）——本轮实读。**hitRegions 按编译器现状允许非空**（`:952-955` 只做数组+预算校验并透传进 DrawOps `:1077`），而清单 §1.1（`:59`）写 MVA 子集"`controls[]`、`hitRegions[]`、`form?` 必须为空/不存在"——两口径存在差异，需主会话对齐（hit-region 命中测试本属交互面；静态 MVA 是否允许带 hitRegions 的场景裁定后才能写 S-05 的第三条负向） | 清单 `:59`/`:145`；编译器 `:731-732`/`:738`/`:952-955`（本轮实读） |

### 1.2 组 B：持久化快照/引用（D 组核心）

| # | 项 | 现状 | 依据 |
|---|---|---|---|
| B1 | **D-01 四张表迁移**：`tutor_visual_program` / `tutor_visual_snapshot` / `tutor_message_visual`（+保存题 `problem_revision_visual`）与 operation/attempt 同事务 | **未落**（本轮复核：`grep -rln "tutor_visual_program\|tutor_visual_snapshot\|tutor_message_visual\|problem_revision_visual"` 于 core/database + core/data → 0 文件）。DB 版本 69（`StudyDatabase.kt:134`，本轮复核）；**visual 落表须取 70+ / 请求 schema 19+**——基线 §1 实测 DB=69、`TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION=18`（`ModelTasks.kt:546`），且脏文档 `data-schema.md:26` 仍写 67/17（漂移已登记基线 §9 #2） | 清单 `:164`；契约 §8 `:161-176`；基线 §1 |
| B2 | **D-02 身份与 CAS**：visualId=programId 不可变；源码/basis/state 变即新 program；CAS + staging/LIVE；正文/trace/link 同事务 | **未落**（无表即无仓库层） | 清单 `:165`；契约 §8 `:166`/`:172`/`:184` |
| B3 | **D-03 checkpoint/GC**：初始+最新两份；每 program 4 MiB、全局 32 MiB；固定引用不删；磁盘不足拒提交 | **未落** | 清单 `:166`；契约 §8.1 `:180-182` |
| B4 | **D-05 冷恢复不执行 JS**：字体/renderer 匹配才从冻结记录重建原生 artifact；不匹配读预览/文字；未知版本只读 | **未落**。依赖 A2 的可序列化 DTO 与持久化载体（契约 §5 `:121`："持久化的 RenderScene 必须是可序列化 DTO…冷恢复…不再执行 JS"） | 清单 `:168`；契约 §5 `:121`、§8 `:190` |
| B5 | **D-06 cache-only resolver**（旧 attachedImages 打开不出网） | **部分落**：显式再生成接线已测（WG-05，清单 `:169`）；cache-only resolver 与"打开旧消息 0 派发"观测无行无门 | 清单 `:169`；契约 §8 `:188` |

### 1.3 组 C：持久化/导出衔接（E 组 + D-04）

| # | 项 | 现状 | 依据 |
|---|---|---|---|
| C1 | **D-04 备份含 JS 程序/快照/预览**；`LOCAL_VISUAL_PREVIEW` canonical；缺必需字节拒绝完整包 | **未落** | 清单 `:167`；契约 §8 `:176` |
| C2 | **E-01 `VisualSnapshotPdfBlock` 接 PDF/DOCX**；导出冻结 `{programHash,state,timeMs,seed,layoutEnvironment,RenderScene}`；导出不执行 JS | **未落**（本轮 grep `VisualSnapshotPdfBlock` 全仓 0）。**core/export 是热区**（5 M，基线 §2） | 清单 `:175`；契约 §8 `:192` |
| C3 | **E-02 role/revision 过滤 + E-03 exportFingerprint + E-04 导出 0 派发/0 JS 观测** | **未落**（JS 无载体） | 清单 `:176-178` |
| C4 | **E-05 屏/PDF 一致**：同一快照双端金样；比较机制 = 画布指令序列序列化相等或金样像素 diff（R2 §三） | **未落**；判据载体依赖 A1 | 清单 `:179` |
| C5 | **KD-75 PDF /AIGC**（登记项，不阻断 MVA 但导出合规必需） | **open**（`docs/known-defects.md:2796`）。fix direction 已定（`:2811`）：不引第三方库、`PdfAigcInfoInjector` append-only、写入判据 (a) AI 图 + (b) `MODEL_DOCUMENT_PARSE` 文本、缓存 `mistake-export-v3`→`v4`、打印子集 `writeSubset` 处置。**core/export 热区空出前不动** | KD-75 `:2796-2815`；清单 §3.5 登记项 `:181` |

### 1.4 组 D：双后端执行与质量门（A 组 + V 组 + Q 组残余）

| # | 项 | 现状 | 依据 |
|---|---|---|---|
| D1 | **A-01 coordinator JS 分支**：注入 `VisualExecutionEngine`、本地执行 0 远程派发记 attempt、不回 `UNAVAILABLE` | **未落**：非 IMAGE 直接 `UNAVAILABLE`（`DefaultFigureGenerationCoordinator.kt:182-190`，清单 v3 现状未变）；批次 27/28 均未动广告面（批次 27 记录 §3.1"能力快照/提示词/契约文档本批零改动"）。装配形态注意：契约 §5.1 的 app 组合层 `AndroidVisualExecutionEngine` 尚不存在（本轮 grep 0），X-01 归位前的装配方案须主会话确认（R3 建议不允许"先在 app 模块广告静态、后归位"，清单 §5 #5 `:279`） | 清单 `:187`；契约 §9 `:202` |
| D2 | **A-02 工具环两路 + A-03 native schema 双方法** | **未落**：`TutorToolLoop.kt:344-348` 拒 program/非 IMAGE；`OpenAiModelProtocol.kt:443-459` `method.enum` 仅 IMAGE_MODEL | 清单 `:188-189` |
| D3 | **A-04/A-05/A-06 prompt/能力/描述三处同源 + A-08 basis 指纹 + A-13 全入口同管线 grep 门** | **未落**（A-13 部分落：三入口均走 coordinator，grep 门未立） | 清单 `:190-192`/`:194`/`:199` |
| D4 | **P-11 `[UNATTRIBUTED]` 跨进程消费** | **未落**：分类器生成前缀（`VisualJsFailureClassification.kt:127`），协调器无引擎注入、`UNATTRIBUTED` 命中 0（A-01 同根） | 清单 `:134` |
| D5 | **P-12 ② 显式重试预算承继 + ③ 解除断言 + 客户端熔断 JVM 专项** | **部分落（G-7① 设备绿已有）**：生产侧无 `explicitUserRetry=true` 调用点（全仓仅测试 4 处，复判 §1.4）；解除路径无直接断言；JVM 状态机专项未建 | 清单 `:135`；复判 §1.4 |
| D6 | **M-04 度量诊断可区分**（度量失败不得折成"程序错"，否则模型反复修 program） | **未落**：批次 27 T2 的 `VisualMeasurementEnvironmentException` 仍折引擎既有 catch → `INVALID_PROGRAM`（记录 §3.2"环境构造与后端输出异常 fail-closed…折 INVALID_PROGRAM"）——诊断语义与 M-04 的"单独分类"要求不符 | 清单 `:158`；批次 27 记录 §3.2 |
| D7 | **Q-01 policy JS pixel-fidelity 负向 + Q-02 PIXEL_CLEANUP 映射裁定** | **Q-01 缺失**（JS 分支只查参数交互，`FigureGenerationPolicy.kt:185-218` 无 pixel 检查）；Q-02 待主会话 | 清单 `:205-206` |
| D8 | **A-10 逐阈值边界 + "两份最终图/文字降级"子门；A-11 三协议 sourceJs 保真 21 向量；A-12 golden 扩测** | **A-10 部分落**（ledger 已接图像通道，逐阈值用例未跑）；A-11 未落（批次 27 设备轨的"A-11"按行义属 P-08 ④，本行维持未落——清单 `:197` 口径注记）；A-12 部分落（基础件有，扩测行无） | 清单 `:196-198` |
| D9 | **V-02 方法切换 + A-09 广告-执行一致设备用例** | **未落**（依赖 D1 装配） | 清单 `:195`/`:217` |
| D10 | **V-01 静态纵向链设备用例**：tool → coordinator → 隔离引擎 → 真实度量 → compiler → painter → UI 卡片 → 保存/重启 → PDF，全程 0 Provider 派发 | **未落**；依赖批次 29/30/31 + S-07 UI 卡片 | 清单 `:109`/`:216` |
| D11 | **Q-04 六类 fixture + 新组合独立预期；Q-06 布局/无障碍矩阵；Q-05 120 题盲测** | **Q-04/Q-06 未落**（Q-06 依赖 core/ui 热区）；**Q-05 UNVERIFIED**（真实 Provider 无凭据——沿用纪律） | 清单 `:208-210` |

### 1.5 组 E：R8/发布矩阵面

| # | 项 | 现状 | 依据 |
|---|---|---|---|
| E1 | **R-04 release R8 冒烟（QuickJS JNI 面）** | **阻塞（三重独立实证，批次 27 step03）**：① R8 把 AIDL/协议/引擎类型全部改名 → 重签 test 包后 app 进程首触 `NoClassDefFoundError Intrinsics/Trace`；② instrument 签名门（debug 签 test vs 本地 key 签 release）；③ 非 exported 服务 shell/root 拒启。解除路径三选一（keep 规则 / 可测 release 变体 / 判据改口径）**归主会话**（记录 §6 #3）。本轮复核：`app/proguard-rules.pro` 仅 Room/MLKit keep，**无 visualjs/quickjs keep**；三个 release 变体全部 `isMinifyEnabled = true`（`app/build.gradle.kts:63/73/82`）。外部佐证：在 minified 构建上跑 instrumented test 因缺 keep rules 而碎是 AGP 已知缺陷（Google issue tracker #126429384）；社区通行解法是专属 test proguard 文件 / `testProguardFiles` 或专用 testable 变体（**单一来源，待验证**） | 批次 27 记录 §3.6 step03/§6 #3；清单 `:228`；本轮 grep proguard 文件 |
| E2 | **R-01 API 23 运行时（= KD-77）** | **未测**：本机 SDK 无 `platforms;android-23` 与任何 android-23 系统镜像（KD-77 `:2857` 实测清单）；需有网络可装镜像的机器 | KD-77 `:2855-2863`；清单 `:225` |
| E3 | **R-02 arm64 / R-03 arm64 16 KB / P-02 arm64 轴** | **本机原理性不可采证**：x86_64 宿主对 arm64 镜像拒载（批次 25 R4 定论，清单 `:226` 转录）；ps16k AVD 是 x86_64（API 36/16384 页，基线 §8）——R-03 需 **API ≥ 35 且 arm64 且 16384 页**设备，本机无一满足 | 清单 `:226-227`、P-02 `:125`；基线 §8 |
| E4 | **R-05 许可证载体** | **真缺**（本轮复核：`find app/src/main/assets core/*/src/main/assets` 仅 compose-richtext/open-claude-ui/dense，无 quickjs-kt（MIT）/QuickJS 文本）。零设备、零热区、主机级——可并入最近任一批次 | 清单 `:229`；本轮 find |

### 1.6 组 F：跨组支撑前提残余（超出命题五桶，清单要求、MVA 链必需——明确登记）

| # | 项 | 现状 | 依据 |
|---|---|---|---|
| F1 | **P-04 双态成对设备证据 + 29.5 s worst-case 实测 + "可见/交互生命周期限次重调度"调用方层** | LOADING/有界重试已落 + 注入形状设备绿；双态成对（`am force-stop` 立即 vs ≥2 min 沉降）未按定义执行；29.5 s 链未实测；生命周期层未落 | 清单 `:127`；复判 §1.1 |
| F2 | **P-07 ③ 残余**：产品进程/UI 首请求（非测试 APK 调用方）、低内存条件、`READY_TIMEOUT=10 s` 设备校准；**R3 广告门文本 6/6→含 ③④⑤ 升级（归主会话）** | ③ PARTIAL 不判定达成；④ 强证据、⑤ 行为层 ×2 轮绿已立（批次 26 E-02/E-03）；warm-up 成本已出数（p50=686/p95=725 ms，仅 x86_64 口径，批次 27 E-04） | 清单 `:130`；KD-76 `:2843`/`:2851` |
| F3 | **P-08 残余**：A-11（行义 P-08 ④）隔离进程内 Socket/filesDir **负向用例**未执行（批次 27 以观测落盘，负向仍缺；需隔离进程内 runner 或产品 seam——构建/产品决策） | 观测级成立（SELinux/DAC 双拒实测），进程内负向缺 | 批次 27 记录 §3.6 step02/§6 #5；清单 `:131`/`:197` |
| F4 | **P-09 残余**：经 `engine.execute` 的**客户端队列层**设备负向（4 program 并发 → 第 4 个 BUSY / latest-wins）未执行（批次 26 T2 未含该判据——批次 26 记录 §6 #13）；死亡清队语义待主会话确认（契约 `:72` 双标"待确认"） | JVM 队列状态机 10 例 + 服务端层设备 BUSY 已绿 | 清单 `:132`；复判 §1.3 |
| F5 | **P-10 死亡未确认 fail-closed**：2 s 内不重建/不放大 + fallback 呈现 | **未落**：代码只置 `unavailable=true` 写日志（`VisualJsClientEngine.kt:823` 仅契约引用注释，本轮复核）；无专属设备负向、无 fallback UI（UI 面归热区；负向用例可在 app visualjs 落点内落） | 清单 `:133`；本轮 grep |
| F6 | **X-01 架构归位（settings.gradle.kts 热区）/ X-02 源证据 / X-03 strictOffline（依赖 B 组）** | 未落 | 清单 `:235-237` |

---

## 2. 二、每项最小范围与建议批次

> "最小范围"以**能被清单对应行判据验收**为下界，不扩；每项标注最重依赖。批次号为建议（主会话裁定）。

### 批次 29：PDF 回放 + 屏/PDF 一致（painter 下半场）

| 项 | 最小范围 | 验收锚（清单行） |
|---|---|---|
| A1 painter→PDF | painter 增加 Canvas 目标参数化（屏幕 `Canvas` 与 `PdfDocument` page `Canvas` 同一 op 开关，零分支差异；`ptPerDu` 由目标栏宽计算，契约 §8 `:192`）；设备冒烟 = 一份合法 SceneV1 的 Rendered 同时渲屏与渲 PDF 页 | §2 行 6、S-06 后半 |
| A2 序列化往返 | JVM：`drawCommandsJson` → 结构化 DrawOps → 再序列化 byte-identical（编译器确定性已有同型先例：Node 台三态 byte-identical，记录 §3.1）；一条"无函数/回调"专项断言 | S-04 |
| C4（雏形） | 屏/PDF 一致门判据载体：同一 Rendered 双端跑 painter，画布指令序列序列化相等（R2 §三 判据先立，金样像素 diff 备选、阈值先金样固定） | E-05 判据列 |
| E4 R-05 载体 | 把 quickjs-kt（MIT）/QuickJS 许可文本落入仓内 assets/NOTICE（载体位置主会话裁定）；`find ... -iname "*licen*"` 门归档 | R-05 |

**阻塞/边界**：无热区（落点 core:data visual / app visualjs）；A4 的 hitRegions 口径差若在本批裁定，S-05 第三条负向同批补。

### 批次 30：持久化快照/引用核心

| 项 | 最小范围 | 验收锚 |
|---|---|---|
| B1（D-01） | Room v70 迁移：四表 + 外键/索引 + 备份资产枚举同步；**DB 版本取 70+、请求 schema 19+**（68/18 已占用，基线 §1）；全历史迁移 + 新 schema 导出 + 旧行不动 | D-01、§2 行 9 |
| B2（D-02） | 仓库层：visualId=programId 不可变（源码/basis/state 变即新 program）、CAS、staging/LIVE、正文/trace/link 同事务；DAO 仪器化：取消/存储满/CAS 冲突/重复提交 | D-02 |
| B4（D-05） | 冷恢复分支：不执行 JS（隔离面结构性保证 + 断言）、字体/renderer 匹配才重建、未知版本只读 | D-05 |
| B3（D-03，可裁半） | checkpoint 初始+最新两份 + 磁盘不足拒提交；每 program 4 MiB/全局 32 MiB GC 可随 V-03 批次补 | D-03 |

**阻塞/边界**：core/database 8 文件开工即脏——按共享纪律"目标文件变脏则整文件纳入并注明或停项登记"；若他线短期不收敛，本批可与批次 31 换序或停项登记。

### 批次 31：双后端执行装配（代码落层，**广告开关不动**）

| 项 | 最小范围 | 验收锚 |
|---|---|---|
| D1（A-01） | coordinator 注入 `VisualExecutionEngine`（X-01 归位前先在现 DirectEngine 装配——**装配方案须主会话确认**，清单 §5 #5）；JS proposal 改正向执行 + 保留负向（X-04 规则）；attempt 记录 + 0 远程派发 | A-01 |
| D2（A-02/A-03） | 工具环接受 `method=JS`+`program`（program 只进独立字段，两路同果）；native schema `method.enum` 加 JS + `program` 字段；既有负向只转正向并保留负向 | A-02/A-03 |
| D3（A-04～A-06/A-08/A-13） | prompt 按真实能力注入 schema/SDK 签名/最小样例；`TutorCapabilitySnapshot` 双就绪位；工具描述动态化；basis 指纹纳 program 摘要；`FigureGenerationRequest(` 恰 3 处 grep 门立 | A-04/A-05/A-06/A-08/A-13 |
| D4（P-11） | `[UNATTRIBUTED]` 前缀经 AIDL→宿主/协调器保留并消费（低堆注入负向） | P-11 |
| D5（P-12 ②③） | 生产 `explicitUserRetry=true` 调用点（本地重试入口——UI 面若触热区则只落非 UI 装配半边）+ 预算承继断言（绑定 A-10 ledger）；"一次成功显式重试解除熔断"直接断言 + 客户端熔断 JVM 专项 | P-12 |
| D6（M-04） | 度量环境/后端失败从 `INVALID_PROGRAM` 分离为可区分分类（回喂模型不再反复修 program） | M-04 |
| D7（Q-01/Q-02） | `FigureGenerationPolicy` JS 分支补 `requiresPixelFidelity=true` 拒绝 + 负向；Q-02 映射裁定（主会话）后落测试 | Q-01/Q-02 |
| D8（A-10/A-11/A-12） | 逐阈值边界用例（第 7 次派发拒、128 KiB+1 B 拒、JS 墙钟 >10 s 拒、513 KiB payload 拒、第 3 份最终图拒、换 method 计数不重置、耗尽→文字降级）；三协议 × 7 向量 sourceJs 参数化（JVM）；golden 扩测（新字段 null 不出现新键） | A-10/A-11/A-12 |

**边界**：落点 core/data（visual + model 面）与 core/model（本轮均干净或仅 lockfile ??）；**能力快照/prompt/契约广告文本零改动直到全门绿**（沿用批次 27 纪律，记录 §3.1）；A-04 的 JS 静态 prompt 测试在门绿前只落代码不接通 `availableMethods`。

### 批次 32：纵向链与质量门（设备轮）

| 项 | 最小范围 | 验收锚 |
|---|---|---|
| D10（V-01） | 一条全链仪器化用例：tool proposal → coordinator → 隔离引擎 → 真实度量 → compiler → painter → UI 卡片（依赖热区；若 core/ui 未空出，可用测试内最小渲染消费替代 UI 卡片段、如实标注降级）→ 保存/重启 → PDF，全程 0 Provider 派发观测（复用 `FigureHttpTransportBypassObservationTest` 模式） | V-01、§2 行 8 |
| D9（V-02/A-09） | JS 失败→IMAGE_MODEL、图像不佳→JS 双向切换（同 operationId、不新增确认、预算不重置）；广告 JS 后执行端不回 `UNAVAILABLE` | V-02/A-09 |
| D8 余量 | A-10 逐阈值的设备半边 | A-10 |
| D11（Q-04） | 六类 fixture 静态子集 + 新组合（波叠加/轨迹采样），独立预期 = fixture + expected 结构 JSON 断言（非人工目测） | Q-04、§2 行 7 |
| F1/F4/F5 并轨 | P-04 双态成对（force-stop 立即 vs ≥2 min 沉降）+ 29.5 s worst-case 实测；P-09 客户端队列设备负向；P-10 死亡未确认负向（fallback UI 若触热区，先落 fail-closed 行为断言并如实标注 UI 半边未落） | P-04/P-09/P-10 |

### 发布矩阵批（挂起，主会话裁定 R-04 路径后单列）

| 项 | 最小范围 | 边界 |
|---|---|---|
| E1（R-04） | 三选一路径落地后，release 变体上跑隔离套件"往返 + watchdog"两例 + `unzip -l \| grep libquickjs.so`（清单 R-04 判据列现成）；keep-rules 路径需评估把 AIDL/协议/引擎类名保留的混淆面代价；testable-release 变体路径需新 build type | 主会话裁定；app build 文件是否脏需开工复核 |
| E2（R-01/KD-77） | 装 `platforms;android-23` + x86_64 镜像 → 临时 AVD → v21 APK 最小冒烟（KD-77 fix direction 原文） | 需可下载镜像的机器；本机纪律禁下载时停项登记 |
| E3（R-02/R-03/P-02 arm64） | arm64 运行时 + API ≥ 35 arm64 16384 页 16 KB 套件集（隔离 6/6+ 引擎 20/20 + 页断言） | 本机原理性不可行；需 arm64 硬件/云设备，主会话排资源 |

### 热区解锁批（挂起）

S-06 模块抽取 + S-07 UI 卡片 + X-01 归位（同一窗：settings.gradle.kts + core/ui）；E-01～E-04 + KD-75（同一窗：core/export 整批，KD-75 fix direction"热区空出后，须整批落地"原文 `:2811`）；Q-06（core/ui）；D-04 备份 + D-06 cache-only + V-03/V-04 + X-02/X-03（随 30/31 批次能力自然补齐）。

### 持续/裁定项（不占批次序）

- **Q-05**：120 题盲测 90/95/90/关键错误 0——真实 Provider 无凭据，UNVERIFIED 纪律（清单 `:209`）；缩小门值必须主会话正式改写并留记录（清单 §5 #1 `:275`）。
- **KD-72**：窗口驱动（批次 28 基线 §6 判"锁窗口未开"），解锁条件见 §3。
- **A4 hitRegions 口径差**（§1.1）：主会话一句话裁定，批次 29 或 31 顺手落实。
- **P-08 命令修正吸收**：R2 G-1 ① `pidof`→`ps -A` 等价命令待下次清单同步轨更新（记录 §6 #8）。

---

## 3. 三、KD-76 / KD-72 / KD-75 解锁条件汇总

### KD-76（warm-up/ready；`docs/known-defects.md:2819` open）——JS 广告的直接门

已立（不再重复要求）：④ 强证据（warm 服务挂死 1500 ms 硬期限 SIGKILL，kill 行实测 ~1.52–1.55 s + hard+500 ms 三轮绿，批次 26 E-02）；⑤ 行为层 ×2 轮绿（加载态三用例沉降/冷启动全绿）；warm-up 装载成本已出数（p50=686/p95=725/max 738 ms、26/26 ready，批次 27 E-04，仅 x86_64/API 34 模拟器口径）。

**剩余解锁条件（全部出数后才可判达成）**：
1. **③ 判定达成**：产品进程/UI 首请求（非测试 APK 内生产 client 引擎调用方）在冷启动（含 CPU 竞争、低内存条件）下首个 `execute` 返回 `Rendered` 且日志无该请求 hard-deadline 签名——批次 26 E-03 冷启动轮 13/13 全绿但负载轻于批次 23 E-01d、调用方非产品进程，故 PARTIAL 不判定（`step03-kd76-criteria.md` §5/§7，KD-76 `:2843`）。
2. **29.5 s worst-case 启动链**（首连 15 s + ready 10 s + 宿主 2.5 s + 死亡确认 2 s，契约 §4 `:72`）设备实测。
3. **`READY_TIMEOUT=10 s` 设备校准**：当前对实测 p95 余量 ≈14×（churn 尾 ≈5.7×），但仅 x86_64 模拟器口径（E-04 §6）。
4. **修复后设备全绿复跑**（批次 28 T1）：隔离套件当前红（T1 接线缺陷，修复在树）——③ 的任何再判定以此为前置。
5. **关闭另一半（归主会话）**：R3 广告门文本 6/6 → 含 ③④⑤ 的升级（KD-76 `:2831`"R3 广告门的 6/6 文本必须同步改为包含 warm-up/ready 用例"）+ 加载态 UI/文案面收尾。

### KD-72（dependency locking；`:2746` open）——窗口驱动，不阻塞 MVA 但阻塞锁门生效

1. **窗口前提**：他线 6 个 build 面（settings.gradle.kts、core/ui、core/export、feature/tutor、.gitignore、core/markdown 整目录）落定入库——批次 28 基线 §6 实测"无一干净"，判"锁窗口未开"。
2. **干净可提交树上重生成**：`./gradlew --no-configuration-cache --no-parallel --console=plain lockAllDependencies --write-locks`（KD-72 fix direction `:2766` 原文口径）；现有 13 条 git 可见锁（盘上 14 个）与 HEAD 不一致、**不得原样 `git add`**（`:2760`）。
3. **覆盖补齐**：`:benchmark` 补锁；`:core:markdown` 模块入库时同步锁；`buildscript`/`pluginManagement` 不锁为登记残余。
4. **负控一次**：人为抬升某传递依赖版本在解析期被锁状态判红（Reopen condition `:2768`）。
5. **CI step 实跑验证**：`Dependency lockfiles are up to date` step 的 Gradle 行为当前 UNVERIFIED（`:2758`）。

### KD-75（PDF /AIGC 隐式标识；`:2796` open）——热区门，不阻断 MVA 但阻断导出合规声明

1. **窗口前提**：core/export 热区（5 M）空出——fix direction 明写"热区空出后，须整批落地"（`:2811`）。
2. **实现整批**：`PdfAigcInfoInjector`（append-only 增量更新 Info 字典 `/AIGC` 7 字段；遇 `/Encrypt`、`/Linearized`、坏 trailer fail-closed）；不写 XMP；不引第三方库。
3. **写入判据双覆盖**：(a) AI 图现行口径 + (b) 文档含 `MODEL_DOCUMENT_PARSE` 文本（实现二选一：`MistakePdfExportInput` 携带块级 provenance，或资格层派生布尔随输入传递）——漏 (b) 会重演"看起来有标识、实际漏标"。
4. **缓存升版**：`mistake-export-v3`→`v4`，否则旧暖缓存继续返回无 `/AIGC` 的 PDF。
5. **验收闭环**：JVM 合成 xref 夹具矩阵 + 设备 PDFium 接受增量段（R1 U3）+ 独立读者（pdfbox-android 或外部 pypdf/PyMuPDF）读回 + 纯用户内容负向 + 打印子集 `writeSubset` 处置（`:2813-2815`）。
6. **与 MVA 的关系**：清单 §3.5 登记项明示"不阻断 MVA，但不得声称合规"（`:181`）——广告文案不得把"有显式图注"说成"已满足隐式标识"。建议与 C2/C3（E-01～E-04）同窗整批落地，避免 core/export 两轮开箱。

---

## 4. 依据与来源

**仓内（本轮实读/实跑 grep）**：
- 计划：`.zcode/plans/plan-2026-10-28-batch-28-scenev1-device-painter.md`（§0:5-10、§1:14）
- 清单 v3：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（§1.2:63-74、§1.4:85、§2:99-112、§3 各行、§5:273-281、附录 D:440-485）
- 复判：`docs/research/2026-10-27-mva-reeval.md`（§1、§2、§3）
- 批次 27 记录：`docs/research/2026-10-27-batch-27-record.md`（§3.1-3.6、§3.7 step03、§6、§9）
- 批次 28 基线：`docs/research/2026-10-28-batch-28-baseline.md`（§1、§3、§5、§6、§7、§8）
- 契约：`docs/current/visual-generation-contract.md`（§4:62-72、§5:92-104、§5.1:106-119、:121、§8:161-196、§9:202-210）
- KD 台账：`docs/known-defects.md`（KD-72 `:2746-2768`、KD-75 `:2796-2815`、KD-76 `:2819-2853`、KD-77 `:2855-2863`）
- 源码锚（本轮 grep 实测）：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOps.kt:76-82,95-140`、`VisualDrawOpsValidator.kt`、`QuickJsVisualExecutionEngine.kt`（jsStringLiteral `:756`/`:1034`）、`core/data/src/main/resources/visual/visual-compiler.js:270-272,438-450,731-738,952-955,1077`、`core/domain/.../VisualExecutionEngine.kt:210,245-246`、`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt:823`、`app/build.gradle.kts:63-84`、`app/proguard-rules.pro:7-19`、`core/database/.../StudyDatabase.kt:134`
- E-file（转录，未复跑）：`docs/research/evidence/2026-10-27-batch-26/step03-kd76-criteria.md`、`docs/research/evidence/2026-10-27-batch-27/step03-r04-release-roundtrip.md`、`step04-warmup-cost.md`

**外部（本轮实跑/检索）**：
- lottie-android 元数据：`gh api repos/airbnb/lottie-android` → Apache-2.0 / 35,757 stars / pushed 2026-02-15 / 未归档（本轮实跑；R1 §4 映射参考适用性复核）
- R8 测试面：Google Issue Tracker [issuetracker.google.com/issues/126429384](https://issuetracker.google.com/issues/126429384)（"Instrumentation testing for minified build types easily breaks due to missing keep rules"——官方 tracker，问题定性采纳）；社区解法（test proguard 文件/testable 变体）：[tir38/android-proguard-test-problem](https://github.com/tir38/android-proguard-test-problem/blob/master/README.md)、[Stack Overflow: instrumentation test with library minification](https://stackoverflow.com/questions/28237696/android-instrumentation-test-does-not-find-classes-from-library-project)——**单一来源，待验证**，仅作 R-04 三选一路径的旁证，不替代主会话裁定
- 16 KB RELRO 官方豁免口径（契约 §2 已引）：[developer.android.com/guide/practices/page-sizes](https://developer.android.com/guide/practices/page-sizes#check-relro)

---

## 5. 未验证项与边界（如实）

1. **本轮零 Gradle、零 adb/am instrument、零 git 写**；所有设备绿/红均为 E-file 与台账转录，不是本轮实跑。本文件是唯一写盘产物。
2. **批次 28 T1/T2 未发生**：修复后设备全绿复跑、painter 落码均以计划为准，本文按"完成后"排序；若 T1 复跑仍红，序 1 起点顺延。
3. **origin/main 远端实时 hash 未核**（未 fetch，沿基线 §10 口径）。
4. **A-11/A-13/A-10 等行现状**以清单 v3 文本 + 本轮关键 grep（painter/持久化/PDF 符号零命中、DB=69、proguard keep 面）复核为准；未逐行重放清单附录 A/B/C/D 的全部历史命令。
5. **R8 测试面的社区解法**（testable 变体/testProguardFiles）为单一来源待验证；R-04 解除路径的三选一裁定权在主会话，本文不预选。
6. **Q-05/真实 Provider、arm64/真机、生产 release 签名、低内存/高负载 warm-up**：UNVERIFIED（沿用批次 27 记录 §6 与纪律）。
7. 批次归属（29/30/31/32/挂起）是**建议**；主会话排期时如调整顺序，"广告开关在全门绿后"与"T1 全绿为设备面前提"两条不变。
