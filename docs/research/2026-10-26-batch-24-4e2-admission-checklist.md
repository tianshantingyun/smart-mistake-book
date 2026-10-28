# 4E.2 准入门检查表（批次 24 T4 落盘，2026-10-26；v2 批次 25 T4 增量合并，2026-10-27；v3 批次 26 T5 复判合并，2026-10-27）

> **性质**：只写文档，零代码、零 Gradle、零设备执行、零 git 写操作。**本表不是广告许可**：JS 仍 `UNAVAILABLE`、不广告；任何一行“过”都必须另有当前命令/用例输出与落盘证据，不能拿本表的“已落”或 R3 的研究结论当门已通过。
>
> **版本与时点**：**v3**（2026-10-27，批次 26 T5 复判合并；版本与时点以本节为准）。v1 = 批次 24 T4（2026-10-26 落盘，随 `aabae781` 提交，63 行）。v2 = 批次 25 T4（2026-10-27）并入 R2 审计（`docs/research/2026-10-27-contract-admission-gap.md`，下称 **R2**）第四部分增量表：**6 条新增行**（S-09、P-12、A-11、A-12、A-13、D-06）与 **14 处改判据**（P-04、P-05、P-08、P-09、A-07、A-10〔含现状改写〕、R-01、R-02、R-03、R-04、R-05、S-06、E-05、Q-04），合并后 69 行。**v3 = 批次 26 T5（2026-10-27）**并入 R2 复判（`docs/research/2026-10-27-mva-reeval.md`，下称**复判**）§1 增量表：只刷新 **P-04/P-07/P-09/P-12** 四行现状（含 P-12"未落"勘误）与总体判定句的理由表述、§6 一条半步、§0 记号一行；**不新增行、不重复建行（仍 69 行），MVA 不成立的结论维持**（复判 §2），0 项转"通过"。合并红线（R2 §四）不变：不重复建 P-08/P-09/P-10/P-11/A-10 行；不动 §0 勾选规则与 X-04；全部判据仍是**待执行定义**（R2 G-1..G-13），v3 本轮零 Gradle/零设备/零 git 写。**v3 补记（批次 27 T5 状态同步，2026-10-27）**：批次 26 设备证据已落盘（`docs/research/evidence/2026-10-27-batch-26/`，E-01…E-05：隔离 13+2、KD-66 关闭门三轮绿、KD-76 ③④⑤ PARTIAL、R8 release + 备份 PASS），本补记只刷新 P-07/P-12/R-02/R-04 四行现状与 §0 记号行、新增附录 D 核对；不新增行（仍 69 行）、0 项转"通过"、MVA 不成立维持、KD-76 open 维持（JS 不广告）；契约 :72 的 KD-76 状态句同步刷新（队列"待主会话确认"标注未动，归 T4）。**v3 补记 2（批次 28 T3 状态同步，2026-10-28）**：按 R2 剩余项排序（`docs/research/2026-10-28-mva-remaining-order.md`，下称 **R2 排序**）与批次 28 计划（`.zcode/plans/plan-2026-10-28-batch-28-scenev1-device-painter.md`）刷新 painter/PDF 回放/持久化/双后端四组行——S-04/S-05/S-06 现状按批次 27/28 落层刷新（DrawOps DTO 已立、painter 过渡落点已裁定 `core:data` `visual` 包、hitRegions 口径差登记待裁），E-05/R-05/§2 行 6 行内注记，D 组与 A 组表后组级排序注记，§0 责任轨五行归属注记；**批次 28 T1（设备复跑）与 T2（painter 落码）在飞未出数，本补记只登记设计落盘（`docs/research/2026-10-28-painter-design.md`）与排序建议，不预填设备/落码判定**；0 项转"通过"、行数 69 不变、MVA 不成立维持、KD-76 open 维持（JS 不广告）；核对见附录 E。
>
> **输入**：R3 `docs/research/2026-10-26-4e2-entry-checklist.md`（下称 **R3**）；计划 `.zcode/plans/plan-2026-10-26-batch-24-isolation-final-kd70.md`（下称 **计划**）；契约 `docs/current/visual-generation-contract.md` §4/§5；4E 计划 `docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md`；批次 23 设备证据 `docs/research/evidence/2026-10-26-batch-23/`；批次 24 基线 `docs/research/2026-10-26-batch-24-baseline.md`；KD 台账（含批次 24 T5 本轮新增的 **KD-76**）；本轮只读 grep（附录 A）；v2 增量合并核对（附录 B，2026-10-27）。
>
> **对 R3 的一处修正**：R3 把隔离广告门定义为“`VisualJsIsolationInstrumentedTest` 6/6”。KD-76（`docs/known-defects.md` KD-76，open，按标题定位）指出 6/6 **不含**冷启动首个 `execute` 被 1500 ms watchdog 误杀的 warm-up/ready 用例，按 6/6 放行会在每个新装/重启用户的首个请求上必败。本表把该门升级为“6/6 + KD-76 的 ③④⑤（或主会话裁定的等价用例）”，见 P-07。
>
> **时点与漂移**：v1 时点 HEAD `5bc8bdf8`；v2 时点 HEAD `d783ae84`（= origin/main），合并前清单工作树干净（`git status --porcelain` 0 行，v1 已随 `aabae781` 提交），合并后本文件为批次 25 T4 的 `M`（附录 B）。**v3 时点 HEAD `405c8373`（批次 25 收口提交 `961c6a0e` 之后）**；v3 时点批次 26 T1/T2 设备轨未出数（`docs/research/evidence/` 无 batch-26 目录，v3 实测；批次 26 T1 轨已把 hard+500 ms 用例落码并登记 KD-66，设备执行未发生）——本表设备状态一律以批次 25 E-file 为准，不预填批次 26 设备判定。批次 25 T1/T2/T3 在飞改动已随 `961c6a0e` 收口，`app/.../visualjs` 与 `core/data/.../visual` 行号仍随批次 26 T1 落码继续漂移，实施时按符号名重定位。**设备证据已回填**：批次 24 E-01/E-01b（隔离 suite 6/6）、E-02（KD-70 全量 20/20、P2 绿）已在 `docs/research/evidence/2026-10-26-batch-24/` 落盘；批次 25 E-01（隔离 11/12 + Race 2/2，1 红 = A-14 夹具缺陷已修未复跑）在 `docs/research/evidence/2026-10-27-batch-25/` 落盘。本表**未重新执行**任何设备用例，只转录 E-file 与原始日志。T1/T2 改后的设备状态不再是 UNVERIFIED，但 P-02 仍只覆盖 x86_64/4 KB（见 P-02）。
>
> **口径**：`已落` = 当前树有代码且有可执行测试/证据目录，但**本轮未运行**；`部分落` = 类型/骨架/单侧实现存在，广告所依赖的真实路径不可用；`未落` = 源码零命中或只有注释/计划文字；`未验` = 在飞改动或从未执行。没有一行在本轮被标成“通过”。
>
> **当前总体判定（本文件时点）**：**MVA 不成立**。前提门 P-01 已由批次 24 E-01/E-01b 达到 6/6，P-02 在 x86_64/4 KB 上设备绿（P2 比值分支已成立），但 P-02 的 arm64/16 KB 覆盖、P-07 warm-up/ready、P-08 隔离启动面审计、P-09 队列/背压、P-10 死亡未确认 fallback、P-11 `[UNATTRIBUTED]` 前缀消费均未落；S/M/D/E/A/Q/V/R/X 各组仍存在未落项；因此 `availableMethods` 不得加入 JS。**v2 复核（批次 25 T4，2026-10-27）**：维持不成立——P-07 warm-up/ready 未落层（KD-76 仍 open，`docs/known-defects.md:2811`），**warm-up/ready 落层且设备验证前不得广告**（§2 行 1b、P-07、§5 #2 三处口径一致）；v2 新增的 P-12/S-09/A-11/A-12/A-13/D-06 与改判据行在设备/JVM 证据落地前同样不构成门。**v3 复核（批次 26 T5，2026-10-27）**：结论**维持不成立**，但 v2 复核句中“P-07 warm-up/ready 未落层”及 v1 句把 P-07/P-09 列入“均未落”的**理由表述已过时**（复判 §4 #4）——P-07 代码层已落且 ⑤ 拿到首份（部分）设备证据、P-09 代码 + JVM 全落 + 设备 2/3、P-12“未落”是口径错误（熔断代码与设备断言自批次 20/21/22 已存在，四行刷新见 §3.1 与复判 §1）。现行依据改为复判 §2：广告面六处仍全拒、S/M/D/E/A/Q/V/R/X 组类型零命中未变、隔离面 12+2 未全绿（A-14 修复未复跑）、KD-66 hard+500 ms 用例落码但设备执行未发生、真实 Provider UNVERIFIED。**warm-up/ready 落层且设备验证前不得广告**口径维持（P-07 ③④⑤ 判据未达，KD-76 open）。

---

## 0. 责任轨记号与勾选规则

“责任人轨”是**建议归属**，不是已分配排期；`后续-*` 均未立项，由主会话决定批次与顺序；其中 `core/ui`、`core/export`、`feature/*`、`settings.gradle.kts` 仍是禁区，相关轨必须等热区空出或被明确授权。

| 记号 | 含义 | 当前状态 |
|---|---|---|
| `24-T1` | 本批隔离 `abandon()`/冷启动修复（app visualjs） | 代码在飞，设备未验 |
| `24-T2` | 本批 KD-70 夹具/分类（core/data visual） | 代码在飞，设备未验 |
| `24-T3` | 本批 KD/记录/文档同步 | 进行中 |
| `24-设备` | 本批设备证据轨（Gradle/am instrument/E-file） | 计划内 |
| `26-T1`/`26-T2`/`26-T5` | 批次 26 计划任务：KD-66 hard+500 ms 用例（T1，已落码登记 KD-66）/隔离套件 12+2 复跑 + KD-76 ③④⑤ 设备判据（T2）/台账契约同步（T5，即本 v3 合并） | 计划 `.zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md:14`；T1 落码且三轮独立绿（KD-66 已于批次 26 关闭）；设备轨已出数（E-01…E-05：隔离 13+2、KD-76 ③④⑤ PARTIAL、R8 release + 备份 PASS）——v3 补记（批次 27 T5） |
| `后续-C` | SceneV1 类型/validator/compiler/state codec（core:model） | 未立项 |
| `后续-L` | 真实度量/排版/painter/回放（core:visual） | 未立项；模块不存在（批次 28 T3 注记：R2 排序——painter 屏半边归批次 28 T2（在飞），PDF 回放半场 + S-04 序列化往返 + 屏/PDF 一致门雏形归批次 29，模块归位归热区解锁批挂起） |
| `后续-U` | UI 显示消费（core:ui + feature/tutor） | 未立项；热区 |
| `后续-P` | 持久化/迁移/备份/GC（core:database + core:data） | 未立项（批次 28 T3 注记：R2 排序——持久化核心 D-01/D-02/D-05 + D-03 checkpoint 半场建议归批次 30，与批次 31 可换序；core/database 8 M 开工即脏为边界；D-04/D-06 随 30/31 能力自然补齐） |
| `后续-E` | PDF/DOCX/print/share 导出（core/export） | 未立项；热区（批次 28 T3 注记：R2 排序——E-01～E-04 与 KD-75 归热区解锁批挂起，core/export 空出后整批落地） |
| `后续-W` | 双后端接线（coordinator/tool/parser/schema/prompt/capability） | 未立项（批次 28 T3 注记：R2 排序——双后端执行装配建议归批次 31，与批次 30 可换序；广告开关不动，`availableMethods` 加 JS 是全部门绿后的最后动作；A-09/V-01/V-02/Q-04 归批次 32 设备轮） |
| `后续-Q` | 保真/质量门（PRD-09 冻结任务集） | 未立项（批次 28 T3 注记：R2 排序——Q-04 六类 fixture 建议归批次 32；Q-01 建议归批次 31；Q-05 真实 Provider 凭据 UNVERIFIED 维持） |
| `后续-R` | 发布矩阵（API 23/arm64/16 KB/release R8） | 未立项（批次 28 T3 注记：R2 排序——发布矩阵批挂起：R-04 解除路径三选一、R-01 需可装镜像机器、R-02/R-03 本机原理性不可采证，均归主会话裁定/硬件窗口；R-05 载体建议批次 29 零成本并轨） |
| `后续-A` | 架构归位（`core:visual-js` + `AndroidVisualExecutionEngine`） | 未立项；需改 settings |
| `主会话` | 只能由主会话作出的裁定 | 见 §5 |

**勾选规则**：

1. **设备行**必须在 E-file 里同时有：`am instrument` 命令、`INSTRUMENTATION_CODE`/summary、APK sha256、按 R4 规范脱敏；只有“之前跑过”不算。
2. **JVM 行**必须有具体模块任务、退出码与失败摘要；本轮没有跑任何任务，故本表 JVM 行也全部未勾。
3. **代码存在不算证据**：`已落` 只表示“将来跑该用例时不会因类型缺失而不可编译”。
4. **不许删/放宽既有负向用例**：`aJavascriptProposalIsNotExecutedInThisWave`（`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationCoordinatorTest.kt:197-220`）、`jsMethodIsRejectedOnBothRoutes`（`.../model/TutorFigureMethodDualParseTest.kt:208-222`）、`theNativeSchemaAdvertisesOnlyImageModelAndTheNewPayloadFields`（同文件 `:255-276`）、`FigurePromptParityTest` 的 fail-closed 断言（`:48-50`、`:77`）只能**改为正向执行断言并保留对应负向**，不能删除或改成“跳过”。
5. **任何一行未过，JS 就不进入 `availableMethods`**；不允许“先广告静态、交互以后再说”的半步，也不允许只在提示词/schema 一侧开放。

---

## 1. 最小可广告子集（MVA-JS-STATIC）

### 1.1 定义

**只广告“静态 SceneV1”**：`FigureGenerationMethod.JS` 可以作为 `availableMethods` 的一员被模型选择，但能力位与场景面固定为：

- 能力位：`supportsParametricInteraction=false`、`supportsPixelEditing=false`、`supportsLayerComposition=false`；不提供图像编辑/生成（`FigureGenerationCapabilities` 字段见 `core/model/.../FigureGeneration.kt:135-153`）。
- SceneV1 只允许 `group/path/label`；`controls[]`、`hitRegions[]`、`form?` 必须为空/不存在；**`reduce`、动画/transport、事件、草稿、SVG 全部不广告、不解析、不执行**。
- `requiresParametricInteraction=true` 的目标对 JS 拒绝（现有 policy 已做，`FigureGenerationPolicy.kt:212-217`）；`requiresPixelFidelity=true` 的目标对 JS 拒绝（**当前缺失**，`FigureGenerationPolicy.kt:185-218` 没有该检查，IMAGE 分支在 `:298-306` 才有）。
- 模型仍可按真实能力为任意 `purpose` 选 JS 或 IMAGE_MODEL；**不得**引入“某用途只准 JS/只准图像模型”的路由表（契约 §1 `:9-13`；计划 `:60-61`）。
- 同一 operation 内允许 JS 失败后修 program，或说明理由后切 IMAGE_MODEL；客户端不暗换；切换不换 operation、不清预算（契约 §1 `:11`、§7 `:155`）。

### 1.2 “广告 JS”的完整含义

广告不是改一句提示词，而是以下六处**同源**同时变：

1. `TutorCapabilitySnapshot` 的方法级就绪（JS runtime ready 不依赖 image backend）；
2. `RoomModelTaskRepository` 组装工具集时的真实能力；
3. `FigurePromptAssembler` 注入的 `availableMethods` + VisualProgram/Scene schema + SDK 签名 + 最小样例；
4. `TutorToolDescriptions` 长短描述；
5. native function schema 与 Route B/原生 parser 的 `method/program` 接受分支；
6. `DefaultFigureGenerationCoordinator` 的 JS 执行分支（注入 `VisualExecutionEngine`，本地执行 0 远程派发但仍记 attempt）。

任一处漏改，都会出现“广告了但执行端拒绝”或“没广告但模型能伪造”的裂缝（R3 §1.5/§1.6）。当前六处全部是拒绝/隐藏态：`FigurePromptAssembler.kt:40-43`、`:108-111` 对 JS fail-closed；`OpenAiModelProtocol.kt:443-459` 的 `method.enum` 只有 `IMAGE_MODEL`；`TutorToolLoop.kt:344-348` 拒 `program`/非 IMAGE；`DefaultFigureGenerationCoordinator.kt:182-190` 对非 IMAGE 回 `UNAVAILABLE`；`Capabilities.kt:67-78` + `RoomModelTaskRepository.kt:959` 的 `imageBackendReady` 只看图像后端。

### 1.3 明确不做（MVA 外）

- **4E.2 交互/表单**：`reduce`、原生控件/滑块、`NativeTutorFormV1`、动画/transport、命中语义、草稿、`strictOffline` 交互、SVG writer（计划 `:20`；契约 §6 `:125-141`）。在 MVA 阶段，这四项要在能力快照/prompt/解析/执行四处**同时关闭**（契约 §3.2 `:58`）。
- **4E.3 复杂源与保真扩展**：整页/复杂自然图/跨科质量覆盖、layer composition（计划 `:21` 只有一行边界，无可执行判据）。
- **纯几何图**不是 MVA：技术上今天能在隔离进程跑通 `prepare` 返回空（`VisualJsService.kt:310` 的 `UnwiredVisualContentMeasurer` 只允许空批次），但没有标签/公式，教学价值不足；不能包装成“JS 可用”（R3 §3.5）。
- **只改提示词/schema**、**只接通引擎不做 compiler/painter**、**让 IMAGE_MODEL 冒充交互**，都不是 MVA（R3 §3.5）。

### 1.4 准入的一句话判据

计划 `:23` 的原话是“先取得‘同一规则→模型选方法→一张图→保存重启→PDF’的完整链”。MVA 的成立条件就是这条链在设备上闭合，并且广告面与执行面一致；**不是**“引擎能跑一个程序”“Scene JSON 能被解析”“PDF 里能嵌一张预览 PNG”中的任何单点。

### 1.5 与 4E 阶段的关系（避免把 MVA 读成 4E.2 全量）

4E.1 的交付边界是“所有入口先共用策略/请求/协调器；先复用图像方法验通，再使 JS 静态成为并列可选方法”（计划 `:19`）。**本表的 MVA-JS-STATIC 就是 4E.1 的 JS 静态后端**；4E.2 的交互/表单（计划 `:20`）不会因为本表全绿而自动开放。R3 标题里的“4E.2 准入门”应严格理解成：**JS 静态后端可否被广告**；4E.2 的全量能力仍另有一组门（R3 §4.9），不在本表内。

因此任何广告文案都必须同时说清两件事：当前只广告静态 SceneV1；交互/表单/动画/SVG 未启用。不能把“静态 MVA 通过”写成“4E.2 完成”。

---

## 2. MVA 必须设备绿的用例（一句话答案）

> 记号：**设备** = 必须仪器化/真机或模拟器执行；**JVM** = 必要但不充分；**新增** = 当前仓内不存在，实施时新增；**已红/已绿** = 批次 23 E-file；**未验** = 批次 24 在飞或从未执行。

| # | 用例（类/方法） | 类型 | 现状 | 依据 | 责任人轨 |
|---|---|---|---|---|---|
| 1 | `VisualJsIsolationInstrumentedTest` **6/6**：`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`、`aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`、`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`、`theClientRebuildsAtMostOnceAfterConfirmedAbandons`、`aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`、`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`（**这是下限，不是完整门**） | 设备 | **批次 24 E-01/E-01b 均 `OK (6 tests)`**（两代 debug APK；批次 23 的 4/5 是历史） | E-01 `evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:124`、E-01b `step01b-visualjs-isolation-rerun.md:102`、`00-index.md:43-44`；测试名见附录 A 的 `@Test` 计数 | `24-T1` + `24-设备` |
| 1b | warm-up/ready：冷启动（含 CPU 竞争）首个 `execute` 成功且日志无该请求的 watchdog hard deadline；warm 服务挂死仍 1500 ms 内 SIGKILL；ready 超时/首连失败走明确加载态、不永久 `unavailable` | 设备 | **新增**；KD-76 open；现有冷启动用例只推迟 attach，不制造原生装载竞争；③④⑤ 三组设备用例（或主会话裁定等价），JVM 只覆盖 ready 状态机 | KD-76 `docs/known-defects.md:2811-2836`；watchdog 现行 arm 点 `VisualJsService.kt:377`/`:484-491`；`VisualExecutionEngine.kt:126`；E-01d `:65`/`:72` | `24-T1` + `24-设备` + `主会话` |
| 2 | `QuickJsVisualExecutionEngineInstrumentedTest` **20/20**，或主会话裁定的等价 P2 判据 | 设备 | **批次 24 E-02 20/20，P2 绿**；但只测 x86_64/API 34/4 KB，R2 §5 要求的 arm64/16 KB 未测；精确 `mallocSize` 未打印（断言只约束 ≥15,099,495）；P2 用例总耗时 98 ms、用例内协作预算 10 s（非默认 100 ms） | E-02 `evidence/2026-10-26-batch-24/step02-quickjs-oom.md:10`/`:57-61`/`:141-149`、`00-index.md:45`；KD-70 `docs/known-defects.md:2686` | `24-T2` + `24-设备` + `主会话` |
| 3 | 真实 `VisualContentMeasurer` 的 text/math 设备用例（含 `inkBounds`） | 设备 | **新增**：服务仍注入 `UnwiredVisualContentMeasurer` | `VisualJsService.kt:310`；`VisualJsProtocol.kt:394-406`；R3 §1.2/§4.2 | `后续-L` |
| 4 | `SceneValidatorTest` / `SceneCompilerTest` / `VisualStateCodecTest` / `VisualSdkContractTest` | JVM（设备金样另列） | **新增**；类型零命中 | 计划 `:101`；R3 §4.3；附录 A 的零命中类扫描 | `后续-C` |
| 5 | `NativeContentLayoutInstrumentedTest` / `MathContentLoweringTest` | 设备 | **新增** | 计划 `:101`；R3 §4.3 | `后续-L` |
| 6 | `AndroidScenePainter` 同一 `RenderScene` 在屏幕与 PDF 的结构/位置一致 | 设备 + 导出 | **新增**；painter 零命中（v3 补记 2 注记：painter 屏半边批次 28 T2 在飞，落点 `core:data` `visual` 包过渡态；PDF 半边——同一 DrawOps 渲染器打 `PdfDocument` page.canvas、零分支差异——R2 排序建议批次 29） | 契约 §5 `:102`；计划 `:97`；R3 §4.3 | `后续-L` + `后续-E` |
| 7 | 六类 fixture 的静态子集：截图 1 比例条、2 F-x 面积、5 受力图、6 拔塞阶段 + 新组合（波叠加/轨迹采样），带独立预期 | 设备 | **新增** | 计划 `:156-168`；R3 §4.3 | `后续-Q` |
| 8 | JS 静态纵向链：tool proposal → coordinator → 隔离引擎 → 真实度量 → compiler → UI 卡片 → 保存/重启 → PDF/DOCX，全程 0 Provider 派发 | 设备 | **新增** | R3 §4.4；计划 `:23`/`:172-176` | `后续-W` + `24-设备` |
| 9 | 迁移/备份/导出含 JS 程序/快照/预览行：旧行不动、缺字节拒绝完整包、导出 0 JS 执行 | 设备 | **新增**；当前只有 `figure_generation_*` 两张审计表（v69） | 契约 §8 `:161-190`；计划 `:126-136`/`:148-150`；R3 §4.5 | `后续-P` + `后续-E` |
| 10 | 广告-执行一致：广告 JS 后执行端不返回 `UNAVAILABLE`，本地执行 0 远程派发且 attempt 有记录 | 设备 | **新增**；当前 `FigureGenerationCoordinatorTest.kt:197-220` 钉的是 `UNAVAILABLE` | R3 §4.6；契约 §9 `:202` | `后续-W` + `24-设备` |
| 11 | 发布矩阵：API 23 运行时、arm64 运行时、arm64 16 KB 运行时、release R8 冒烟 | 设备 | API 34/4 KB 已跑，x86_64 16 KB 已跑 3/3，arm64 ELF 已跑；其余未测；v2：16 KB 门口径升级见 R-03（API ≥ 35 + arm64 + 16384 页 + 隔离 6/6 + KD-70 20/20 套件集），R8 冒烟不覆盖 QuickJS JNI 面（见 R-04） | R3 §4.8；E-03 `:113`、E-09 `:119`；KD-69 `:2660` | `后续-R` + `24-设备` |

**不属于 MVA 设备门**（后置到 4E.2 全量）：`NativeTutorFormV1`、reduce 队列、动画 1000 帧 0 写、拖动 checkpoint、strictOffline 交互、控件/命中区无障碍（计划 `:145-147`；R3 §4.9）。这些在 MVA 阶段必须**不广告、不解析、不执行**。

---

## 3. 检查表主体

### 3.1 P：前提门（隔离与引擎）

| ID | 检查项 | 现状 | 缺口 | 证据位置 | 验收用例（类型） | 责任人轨 |
|---|---|---|---|---|---|---|
| P-01 | 隔离 suite 6/6 设备绿（**下限**；完整门还需 P-07/P-10） | **批次 24 E-01/E-01b 两代 debug APK 均 6/6**（`OK (6 tests)`）；`abandon()` 已按“先保注册再确认死亡”修复，首连 15 s/重建 10 s/确认 2 s | 6/6 设备证据已齐；仍不覆盖 hard+500 ms、warm-up/ready、死亡未确认 | `VisualJsClientEngine.kt:578-640`（abandon/awaitDeathConfirmation）、`:920`/`:926`/`:933`；E-01 `evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:124`、E-01b `step01b-visualjs-isolation-rerun.md:102` | 6 个方法各一次设备绿，E-file 落盘（设备） | `24-T1` + `24-设备` |
| P-02 | KD-70 P2 转绿或官方等价判据 | **批次 24 E-02：20/20，P2 绿**（reason=`MEMORY_LIMIT`、`attributed by memoryUsage`、`PENDING_CALIBRATION`、`mallocSize != -1`） | **口径窄于 R2 §5**：只测 x86_64/API 34/4 KB；arm64/16 KB 未测；精确 `mallocSize` 未打印（≥15,099,495 由断言约束）；P2 用例总耗时 98 ms、用例内协作预算 10 s（非默认 100 ms），慢设备可能先 `PHASE_TIMEOUT`；关闭由主会话裁定 | E-02 `evidence/2026-10-26-batch-24/step02-quickjs-oom.md:10`/`:57-61`/`:141-149`；R2 `docs/research/2026-10-26-quickjs-memory-accounting.md:371`/`:385`；KD-70 `:2686`；`QuickJsVisualExecutionEngineInstrumentedTest.kt:300`（P2）、`:761`（夹具） | P2 设备绿（两 ABI 各一次）或主会话书面等价判据 + 证据（设备） | `24-T2` + `24-设备` + `主会话` |
| P-03 | 引擎其余 19 例不回退（P1/P3/P4/P5、close/并发/取消） | 批次 24 E-02 **20/20**（x86_64/API 34/4 KB） | 20/20 已成立；`VisualJsFailureClassificationTest` JVM 本轮未重跑；arm64 覆盖见 P-02 | E-02 `evidence/2026-10-26-batch-24/step02-quickjs-oom.md:141-149`；R3 §4.2 | 全量 20 例设备绿 + `VisualJsFailureClassificationTest` JVM 绿（设备+JVM） | `24-T2` + `24-设备` |
| P-04 | 冷启动/加载态可解释：首连预算 15 s、重建 10 s、死亡确认 2 s | **代码已落 + 注入形状设备绿（部分）（v3 刷新，复判 §1.1）**：LOADING/有界重试已落（批次 25 T2，随 `961c6a0e` 收口）——attach/ready 预算超时 = loading 形状（`VisualJsClientEngine.kt:708-711`/`:764-765`；loading 旗标豁免重建预算检查 `:598-602`/`:633`/`:640`，死亡/握手成功清旗标 `:684-686`/`:753-754`；§9 收口 R15 修复把 handleDeath 清零移到 ABANDONED 短路之后，竞态确定性消除），下一次 execute 重新 bind + 等 ready（轮询 `:785-796`，间隔 100 ms `:1233`，`READY_TIMEOUT_MILLIS=10_000` `:1230`，测试 seam `:809`/`:1176`）；`close()` 在 attach/ready 等待中立即终结；**死亡与结构性失败仍锁存**（`:686`/`:723`），"最多重建一次"记账不变。设备证据：批次 24 E-01/E-01b 7.6 s 延迟 attach 用例绿 + 批次 25 E-01 新增 2 例注入形状绿（`aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching`、`aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds`，E-01 `step01-visualjs-isolation.md:86-92`/`:173-175`）——均为沉降态 + 注入形状，非真实冷启动 attach 分布 | ① R2 §三 双态成对证据（冷启动 = `am force-stop` 后立即跑同用例、沉降 = force-stop 后 ≥2 min，窗口落 E-file）未按定义执行——批次 25 设备轮是"emu 冷启动 + 150 s 沉降"单态（`00-index.md:24-26`）；② "满队列 BUSY 后由可见/交互生命周期限次重新调度"调用方生命周期层未落（契约 `:72` 自认属 P-04）；③ 29.5 s worst-case 未设备实测（`00-index.md:95-96` 缺口 7）；④ KD-76 ③ 真实冷启动首请求未执行 | `VisualJsClientEngine.kt:708-711`/`:764-765`/`:598-602`；`VisualJsClientEngine.kt:1206`/`:1212`/`:1219`（15 s/10 s/2 s，v3 实测锚）；E-01 `:86-92`/`:173-175`；复判 §1.1；契约 `:72`（29.5 s） | `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram` 双态成对设备绿（v2 双态定义不变）+ 29.5 s worst-case 实测（设备）；复判 §3 #5 建议并入批次 26 T2 同一设备轮 | `24-T1` + `26-T2` |
| P-05 | KD-66 `hard+500 ms` 门或明确不声称硬上界 | 现有 watchdog 用例以 ≤6 s 判死，弱于关闭门；KD-66 open（E-01/E-01b 6/6 不改变该结论） | 关闭门未达；能力文案尚未裁定 | KD-66 `docs/known-defects.md:2573-2663`；R3 §4.1/§5.2 #2 | 要么设备 hard+500 ms 绿，要么 prompt/文案不承诺硬上界并记录（设备/文档）；v2 文案机械判据（R2 §三）：`grep -rnE "1500 ms\|硬上界\|硬终止"` 于 `FigurePromptAssembler.kt`（core/data/model）与 `TutorToolDescriptions.kt`（core/model）→ 0 命中，或命中处均为否定句（人工确认并记录） | `主会话` + `24-设备` |
| P-06 | 文档常量与实现一致（KD-66 Progress 旧写 5 s/1 s/8.5 s；UNVERIFIED 文本） | **已回填**：KD-66 把 5 s/1 s/8.5 s 标注为批次 22 时点并指向 15 s/10 s/2 s/19.5 s；T1 的“设备复跑 UNVERIFIED”由批次 24 E-01/E-01b 6/6 取代；契约 §4:72 同步 | 无（文本层；本轮 grep 见附录 A） | `docs/known-defects.md:2583`/`:2598`/新批次 24 设备轨 Progress；`docs/current/visual-generation-contract.md:72`；`VisualJsClientEngine.kt:920-933` | grep 常量与 KD 文本一致（主机） | `24-T3` |
| P-07 | **warm-up/ready 门（KD-76）**：冷启动首个 `execute` 不被一次性原生装载误杀；客户端只在 ready 后发首个请求；ready 超时 fail-closed 且有加载态 | **部分落（代码层已落 + ④ 强证据/⑤ 行为层 ×2 轮设备证据在、③ PARTIAL 不判定达成；v3 补记（批次 27 T5）按批次 26 E-01/E-02/E-03 刷新，本门未过）**：服务 `onCreate` 即 warm-up、完成置单调 `ready`（`VisualJsService.kt:75`/`:113`/`:127-132`，warm-up 失败 killSelf `:135`），ready 前 execute 不排队、不进 watchdog（`:242-246`），ready 后每请求仍 arm 1500 ms（`:375-376`）；客户端握手后轮询 `isReady()`（预算 10 s，`VisualJsClientEngine.kt:785-796`/`:1230`，间隔 100 ms `:1233`）再交付首个请求，attach/ready 超时走 loading、不锁存 `unavailable`（`:708-711`/`:764-765`）；AIDL `isReady()`（PROTOCOL_VERSION 1→2，`IVisualJsService.aidl:38`）。**设备证据（v3 从"零"刷新为"首份（部分）"）**：批次 25 E-01 的 T2 三例设备首验绿（E-01 `step01-visualjs-isolation.md:86-92`/`:173-175`），其中 warm-up 中 acquire 有界拒绝例与 P-04 的两例 loading 注入形状例构成 **⑤ 行为层首份设备证据**——均为沉降态 + 注入形状，非真实冷启动 attach 分布；**③ 尝试成功、不判定达成（PARTIAL，批次 26 E-03）**：真实冷启动（boot_completed 后不沉降，loadavg 0.75）即跑 13/13 全绿（Time 64.072）、5 条 kill 行逐条归因负向夹具设计内注入、零意外杀/零首请求杀、冷态 warm-up(~1.9 s)→首请求 `Rendered` 全链成立；但负载轻于批次 23 E-01d（boot 0.75/跑后 4.31 vs 5.25）、调用方是测试 APK 内生产 client 引擎非产品进程/UI 首请求、29.5 s 链与低内存 UNVERIFIED（`step03-kd76-criteria.md` §5/§7）；**④ 强证据（弱口径解除，v3 补记）**：warm 服务（ready 门后）#240 挂死被 1500 ms 硬期限 SIGKILL（kill 行实测提交后 ~1.52–1.55 s，`step03` §4.1）+ 调用方 hard+500 ms 有界返回三轮绿（E-02）+ 新 generation 无 BUSY；旧 6 s 计时（`withTimeoutOrNull(6_000L)`，`VisualJsIsolationInstrumentedTest.kt:202`/`:552`，按符号定位）共存、降级为服务级回归观察非本判据计时门；**⑤ 行为层证据 ×2 轮绿**（加载态三用例：首连超时/ready 超时/warm-up acquire——沉降轮 E-01 与冷启动轮 E-03 全绿，批次 25 注入形状为首份）；**⑤ 收尾仍未做**（加载态 UI/文案面与 R3 原文 6/6 门文本，归主会话） | 缺口 = ③ 产品进程/UI 首请求、29.5 s worst-case 启动面、低内存条件、`READY_TIMEOUT=10 s` 设备校准，均 UNVERIFIED；**warm-up 实测装载成本/收益归批次 27 设备轨**（`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md:12` 设备轨列"warm-up 装载成本实测"）；③④⑤ 设备判据批次 26 T2 已执行（E-01/E-02/E-03），KD-76 维持 open | KD-76 `docs/known-defects.md`（批次 26 T5 合并块，按标题定位）；warm-up/ready `VisualJsService.kt:104-138`；`VisualExecutionEngine.kt:126`；E-01 `:86-92`/`:173-175`；复判 §1.2 | ③ 冷启动/CPU 竞争首个 execute `Rendered` 且无 hard deadline；④ warm 服务挂死仍在 1500 ms 内被杀（复判 §3 #3：与 T1 hard+500 ms 同夹具，建议一次落两个时限断言）；⑤ ready 超时走加载态（设备） | `24-T1` + `24-设备` + `主会话` |
| P-08 | **隔离启动面审计（契约 §4:62；启用门 §9:202）**：Application 在 Room/备份/维护 filesDir/Provider/密钥工厂前短路；构造/字段/companion 初始化审计；实际进入隔离进程的 ContentProvider/AndroidX Startup 用 `dumpsys` 审计；隔离进程不申请文件/网络主机绑定 | **部分落**：guard 在 `SmartMistakeBookApplication.onCreate` 最前（`:287-304`；v2 附录 B 复核 `:294`/`:300-301`），字段审计目前只在注释；ContentProvider/Startup 仍未 `dumpsys`（契约 §4:72 自认）；“隔离进程不申请文件/网络主机绑定”无独立证据；**批次 27 T4 同步：剩余缺口（① dumpsys 审计 ② providers 0 命中 ③ 进程内断言 ④ 网络/filesDir 负向 ⑤ Room 未在隔离进程打开——设备+文档）待批次 27 设备轨**（计划 `.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md:12` 设备轨列"P-08 dumpsys 审计"），出数前维持"部分落"、0 项转"通过" | `dumpsys activity providers` 证据 + 构造期审计记录 + 文件/网络能力负向证据；v2 已给可执行命令与断言（R2 G-1） | 契约 §4:62/:72、§9:202；`SmartMistakeBookApplication.kt:287-304`；`AndroidManifest.xml:64-72`；R2 G-1 | 主机静态：`grep -A3 "<provider" app/build/intermediates/merged_manifests/<变体>/process*/AndroidManifest.xml` → 合并清单 6 个 Provider（BatchImportFileProvider、androidx.core.content.FileProvider、MistakePdfFileProvider、MistakeDocxFileProvider、androidx.startup.InitializationProvider、MlKitInitProvider）均无 `android:process=":visual_js"`（v2 附录 B 实证）。设备（R2 G-1）：① `adb shell pidof com.tingyun.smartmistakebook:visual_js` 非空；② `adb shell dumpsys activity providers \| grep visual_js` → 0 命中；③ 进程内断言 `Process.isIsolated()==true` 且 `myUid != applicationInfo.uid`、`queryContentProviders(processName=":visual_js")` 返回空；④ 负向：隔离进程内 `Socket(...).connect()` 连接失败、`context.filesDir` 写文件失败（隔离 UID 无应用数据目录）；⑤ `adb logcat -d --pid=<visual_js pid> \| grep -iE "sqlite\|room"` → 0 命中（Room 未在隔离进程打开）（设备+文档） | `24-T1` + `24-设备` |
| P-09 | **队列/背压与消费者校验（契约 §4:64）**：全局 1 在飞 + 最多 2 待绘、每 program 只留最新 state、跨 program 按序、满队列 BUSY 保留原帧、可见/交互生命周期限次重调度；消费者验证服务调用身份、描述符方向与长度 | **代码 + JVM 全落、设备 2/3（1 红 = A-14 夹具缺陷已修未复跑）；门未过（v3 刷新——v2"未落"已失真：`executeMutex` 现仅存 KDoc，`VisualJsClientEngine.kt:65`/`:106`/`:227`）**：`VisualJsFrameQueue.kt`（216 行）——全局 1 在飞（`inFlight` `:70`/`:88-89`/`:174-180`）、最多 2 待绘（`MAX_PENDING_FRAMES=2` `:206`，满判 `:101`）、同 program latest-wins 原地替换保 FIFO 位（`:95`，旧等待者回 BUSY）、满队列 BUSY 且在飞/待绘帧一概不动、`close()` 闩死清队、`dropPending` 死亡清队不闩死、调用方取消出队；客户端接入 `VisualJsClientEngine.kt:108`（常驻消费者）/`:214`（入队判定短临界区）/`:235-299`（`executeFrame` 会话体：出队**二次熔断复查** `:242-250`、独立 `sessionResult`、每帧新取 requestId/generation）；消费者校验：宿主回调身份过滤抽纯函数 `VisualJsResultFrameFilter`（伪造 generation/requestId/UID/null requestId 全拒、全中才受，JVM 5 例）+ 服务端 `requireHost` 逐事务 UID、双向 256 KiB 有界读保持 | ① A-14 复跑（批次 26 T2 判据 12+2 全绿）；② R2 G-2 的**客户端队列层**设备负向未执行——现有设备 BUSY 用例在服务端层（`aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight`，`VisualJsIsolationInstrumentedTest.kt:523`），经 `engine.execute` 的"4 program 并发 → 1 在飞 + ≤2 待绘 + 第 4 个 BUSY / latest-wins"只在 JVM 队列状态机验过；③ "可见/交互生命周期限次重调度"调用方层未落（P-04 面）；④ 死亡清队回 `ENGINE_CLOSED` 仍待主会话确认；取消在飞帧不再 abandon worker 已裁定确认采纳（取消只出队自己的等待帧、不打断在飞执行，批次 27 T4 定稿）（批次 25 记录 §3.3 `:121`；契约 `:72`） | 契约 §4:64；复判 §1.3；JVM 10 例 = `VisualJsFrameQueueTest.kt` 5 例 + `VisualJsResultFrameIdentityTest.kt` 5 例（复核轨实跑 `python tmp/verify_batch25_integration_jvm_tests.py` → COMPILE_OK + 两类各 `OK (5 tests)`，**本表未重跑**，转录自批次 25 记录 §3.3/R18）；设备 2/3（批次 25 E-01 `step01-visualjs-isolation.md:86-92`：`anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead` 与 `aSecondConcurrentRequest...` 绿、`aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot` 红 = A-14，E-01 维持 PARTIAL） | JVM（R2 G-2 判据不变，`VisualJsFrameQueueTest` 已覆盖 1 在飞 + ≤2 待绘 + 第 4 个不同 program BUSY、latest-wins 不丢其他 program、排队中取消出队、close 闩死、死亡清队后重建可再收帧）。设备：经 `engine.execute` 的客户端队列负向（4 program 并发 → 第 4 个 BUSY / latest-wins；复判 §3 #6 建议并入批次 26 T2 同轮）+ A-14 复跑；伪造身份/方向判据不变（设备+JVM） | `24-T1` + `24-设备` + `26-T2` |
| P-10 | **死亡未确认路径（契约 §4:68）**：abandon 2 s 未确认时 fail-closed（不重建、不并发放大）并展示 fallback/加载态；确认/未确认两条路径都有设备负向证据 | **部分落**：代码在 `VisualJsClientEngine.kt:588-603` 置 `unavailable=true` 并写日志；**无 fallback UI/加载态**；测试类 6 个 `@Test` 无“死亡未确认”用例；E-01 logcat `death was not confirmed`=0（只覆盖确认路径） | 负向设备用例 + fallback 呈现 | 契约 §4:68；`VisualJsClientEngine.kt:588-603`；E-01 `step01-visualjs-isolation.md:69`/`:104-105`；`VisualJsIsolationInstrumentedTest.kt` 6 个 `@Test` | 人为延迟/丢失 death：2 s 内 fail-closed、不再 bind、后续 `ENGINE_CLOSED`；UI 显示 fallback（设备+UI） | `24-T1` + `24-设备` |
| P-11 | **`[UNATTRIBUTED]` 诊断跨进程保留并消费**（分类器 → AIDL → 宿主/协调器），不得折成普通 `INVALID_PROGRAM` 文案；三类已知边界（栈根分配、丢失哨兵+高堆、非严格 OOM 前缀）如实登记 | **未落**：分类器生成前缀（`VisualJsFailureClassification.kt:127`/`:197-201`），但协调器无 `VisualExecutionEngine` 注入、`UNATTRIBUTED` 命中 0（A-01）；无 AIDL/宿主侧断言 | 前缀保留/消费门 + 边界记录 | 契约 §4:90；R2 §3.3；`VisualJsFailureClassification.kt:127`；`DefaultFigureGenerationCoordinator.kt`（0 命中） | JVM/设备：消息丢失低堆 → 诊断以 `[UNATTRIBUTED]` 开头且宿主未当已分类；A-01 注入引擎后仍保留 | `后续-W` + `24-设备` |
| P-12 | **programHash 熔断与本地重试预算承继（契约 §4:70）**：watchdog 硬杀/引擎崩溃后，同 programHash 的自动重发被拒（重建只恢复运行环境，不自动重跑刚才超时/崩溃的源码）；用户显式本地重试可用且**预算承继**（不开新逻辑操作）；源码 SHA 变更后熔断解除 | **部分落（v3 勘误，复判 §1.4——v2"未落"是口径错误：把 R2 L-1"63 行清单缺 P-12 行"的**清单文本**结论误读成代码层结论；复判 §4 #3 登记为新发现）**：熔断代码自批次 20/21/22 已存在——端口字段 `explicitUserRetry`（`core/domain/.../VisualExecutionEngine.kt:72`，引入 `29b46f21` 批次 20）、引擎侧 `fusedProgramDigests`（`QuickJsVisualExecutionEngine.kt:168`/`:194`，`1a6bf486`/`69e5f591`）、客户端 `fusedDigests`（`VisualJsClientEngine.kt:112`，`69e5f591`）；**设备断言自批次 22 已存在**：`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy` 内显式断言同 hash 自动重发 `PROGRAM_FUSED`（`VisualJsIsolationInstrumentedTest.kt:318`，批次 27 T5 实测现值；随批次 24 E-01/E-01b、批次 25 E-01 与批次 26 E-01（13/13）绿）→ **G-7 ① 设备绿已有**；批次 25 T3 把熔断并入队列路径：入队前预检 `VisualJsClientEngine.kt:188-193`、出队二次复查 `:242-250`、宿主阶段超时熔断 `:277`、死亡熔断 `:440`/`:516`/`:915`、显式重试成功后解除 `:294-297`；`Frame` 携带 digest/`explicitUserRetry`（`VisualJsFrameQueue.kt:59`/`:62`）。结构性事实：digest = 源码 SHA-256（`figureSha256Hex`，`:187`）→ G-7 ③ 结构性成立 | **G-7 ② 显式重试预算承继无任何证据**：生产侧无 `explicitUserRetry=true` 调用点——全仓仅测试构造 4 处（仪器化 `:323`/`:327`/`:338`、契约测试 `VisualExecutionEngineContractTest.kt:425`）；生产 `VisualExecutionRequest` 唯一构造在 wire 层 `VisualJsProtocol.kt:185`，L3 客户端无生产装配（契约 `:72`）；预算承继依赖 A-10 ledger × 引擎装配。**③ 无直接断言**（设备换 program `Rendered` 是旁证非门）；"一次成功的显式重试即解除熔断"（`:294-297`）无专门断言；**客户端熔断 JVM 状态机专项测试未建**（现有 JVM 仅端口契约层 `VisualExecutionEngineContractTest.kt:422` + fake `:198`；`VisualJsFrameQueueTest` 5 例只测队列状态机不触熔断） | 契约 §4:70；复判 §1.4（G-7 三断言对照表）；R2 G-7/L-1（L-1 的 grep 对象是清单文本，非代码）；`git log -S` 引入提交 `29b46f21`/`1a6bf486`/`69e5f591`（复判 §5 转录，本表未重跑） | JVM 熔断状态机专项 + 设备（R2 G-7 三断言）：① 已设备绿、补"ledger/attempt 计数不变"侧断言（待 A-01/A-10 装配）；② 预算承继绑定 A-01/A-10 生产装配（`后续-W`，复判 §3 #8 不单独立批）；③ 解除路径补直接断言 + 客户端熔断 JVM 专项（复判 §3 #7 曾建议并入批次 26 T2；**批次 26 T2 已执行且未含本行判据**——批次 26 记录 §6 #13，归属待主会话排期）（JVM+设备） | `24-T1` + `后续-W`（26-T2 已执行、未含本行判据，批次 26 记录 §6 #13；批次 27 设备轨亦不含） |

### 3.2 S：SceneV1、编译器与回放

| ID | 检查项 | 现状 | 缺口 | 证据位置 | 验收用例（类型） | 责任人轨 |
|---|---|---|---|---|---|---|
| S-01 | `VisualProgram/VisualScene/VisualSceneValidator/VisualSceneCompiler/VisualStateCodec` 落地，`ValidatedScene` 只能由 validator 产生 | **未落**：五个类零命中 | 全部类型与受控构造 | 计划 `:93`；契约 §5 `:94`；附录 A 的零命中类扫描 | `SceneValidatorTest`/`SceneCompilerTest`/`VisualStateCodecTest`（新增 JVM） | `后续-C` |
| S-02 | `visual-sdk-v1.js` 静态子集：`prepare/render` + `line/arrow/circle/arc/axes/polyline`；无 `reduce`、无 Date/random/import/host | **未落**：无 `.js` 资产；bootstrap 只封闭 Date/替换 Math.random | SDK 资产与契约 | 计划 `:94`；契约 §3.2 `:47`；`QuickJsVisualExecutionEngine.kt:809-814`；附录 A 的 SDK 资产检查 | `VisualSdkContractTest`（新增 JVM）+ 隔离内执行（设备） | `后续-C` |
| S-03 | 严格拒绝：未知字段/重复 JSON 键/原型污染键/悬空引用/NaN/Infinity/越界/树深/字节/展开路径预算 | **部分落**：引擎只做字节→JSON→contentId 保守校验 | 完整 validator | 契约 §4 `:90`、§5 `:95`；`QuickJsVisualExecutionEngine.kt:647-684`（KDoc `:643-645` 自认归 4E.2） | `SceneValidatorTest` 负向套件（新增 JVM） | `后续-C` |
| S-04 | `RenderScene` 为不可变序列化显示列表，不含函数/脚本/回调；屏幕与 PDF 同 painter | **部分落（v3 补记 2，批次 28 T3 按 R2 排序 §1.1 A2 刷新）**：DTO 语义已立——`VisualDrawOps.kt` sealed 六原语（Save/Concat/Clip/DrawPath/Label/Restore）+ `VisualDrawOpsValidator.kt`（词表/预算/有限性/配平/stats 复算，批次 27 落码）+ `Rendered` 增 `drawCommandsJson`/`drawSchemaVersion`（`core/domain/.../VisualExecutionEngine.kt:245-246`，本轮实测锚）；**序列化往返（drawCommandsJson → 结构化 → 再序列化一致）无测试**；无函数断言由 sealed 词表结构性保证、无专项用例行；painter 屏半边批次 28 T2 在飞（设计 `2026-10-28-painter-design.md`），本注记时点 painter 类名 grep 0 | 序列化往返测试、无函数专项断言、painter（DTO 已落） | 契约 §5 `:102`；计划 `:97`；`VisualDrawOps.kt:95-140`；R2 排序 §1.1 A2 | 编译金样 + 序列化往返 + 无函数断言（JVM/设备） | `后续-C` + `后续-L`（R2 排序：序列化往返 JVM 半场归批次 29；painter 屏半边批次 28 T2） |
| S-05 | MVA 静态子集对 `controls[]/hitRegions[]/form?` 非空直接拒绝 | **部分落 + 一处口径差待裁（v3 补记 2，批次 28 T3 按 R2 排序 §1.1 A4 刷新——旧"未落：无 DTO"已过时）**：编译器已拒非空 `controls`（`visual-compiler.js:732` `SCENE_FIELD_CONTROLS` "must be an empty array in V1"）与整体拒 `form`（`:738` `SCENE_FIELD_UNAVAILABLE`，本轮实测锚）；**hitRegions 按编译器现状允许非空**（`:950-975` 只做数组+预算校验、非空不拒，并经 `:1077-1088` 序列化进 DrawOps 输出）——与本表 §1.1 "`controls[]`、`hitRegions[]`、`form?` 必须为空/不存在"存在口径差（hit-region 命中测试本属交互面），S-05 第三条负向（hitRegions）待主会话对齐口径后落（R2 排序：批次 29 或 31 顺手） | hitRegions 口径裁定 + 对应负向 | R3 §3.1；契约 §3.2 `:58`、§5 `:94`；`visual-compiler.js:732`/`:738`/`:950-975`/`:1077-1088`（本轮实测）；R2 排序 §1.1 A4 | `SceneValidatorTest` 三条负向（新增 JVM） | `后续-C` |
| S-06 | `core:visual` 模块 + `AndroidScenePainter` 共用 Compose/PDF | **未落**：模块与类零命中（v3 补记 2 维持"未落"，补落点注记：painter 屏半边落点已裁定 `core:data` `visual` 包过渡态——`VisualDrawOpsPainter.kt` `internal`、纯计算/应用层分离，设计 `2026-10-28-painter-design.md` §3.1；不新建 `core:visual` 模块（`settings.gradle.kts` 热区），模块归位条件不变；落码批次 28 T2 在飞、本注记时点类名 grep 0） | 模块、真实布局、painter | `settings.gradle.kts:19-34`（无 visual 模块）；契约 §5.1 `:115`；计划 `:54`/`:96`；附录 A 的零命中类/模块检查 | 同 `RenderScene` 屏幕/PDF 结构位置一致（设备+导出）；v2 比较机制（R2 §三）：同一 `RenderScene` 输入经 Compose 与 PDF 两 painter，比较画布指令序列序列化相等，或金样像素 diff（阈值先金样固定） | `后续-L`（R2 排序：模块归位归热区解锁批挂起；painter 屏半边批次 28 T2） |
| S-07 | UI 消费：`core:ui/VisualSceneCard`、feature 从持久 link 显示 | **未落**：`VisualSceneCard/VisualControls/VisualSemantics` 零命中；feature/tutor 对 `visualId` 零引用 | UI 组件与生产消费点 | 计划 `:142`；契约 §10 `:216`；附录 A 的 UI/feature 引用检查 | 仪器化：静态场景卡片从 DB link 显示（设备） | `后续-U` |
| S-08 | 数学真实 lowering：复用 `MathBudget` parser/AST，未知 TeX 回可读文本，不增第三套公式/坐标逻辑 | **未落**：`MathContentLowering` 零命中 | lowering 与金样 | 契约 §5 `:123`；计划 `:96`/`:100`；附录 A 的零命中类扫描 | `MathContentLoweringTest`（新增 JVM/设备） | `后续-L` |
| S-09 | **input/metrics 深冻结（契约 §3.2:58）**：bootstrap 在把 `input`/`metrics` 交给用户源码前对对象图深 `Object.freeze`；JS 程序在 prepare/render 内改写（严格模式）抛 `TypeError`、该请求按裁定分类拒绝、渲染输出不含被改值；Kotlin 度量端口返回类型无 mutable 访问器 | **未落**：bootstrap 仅 `Object.freeze(Math)`（`QuickJsVisualExecutionEngine.kt:813`；`:810` `Date = undefined`、`:812` `Math.random` 替换之外无 input/metrics 冻结，v2 附录 B 复核）；`:676` 的 contentId 检查是事务引用白名单，不是对象深冻结 | 深冻结实现 + 程序内改写负向 + 端口类型审计 | 契约 §3.2 `:58`；R2 G-4/1A#4；`QuickJsVisualExecutionEngine.kt:809-814`；`VisualExecutionEngine.kt:196-213`（度量端口接口） | JVM 隔离引擎用例（R2 G-4）：程序内 `input.viewport.w = 1`、向 `metrics` push → `TypeError`、请求拒绝、输出不含被改值；度量端口无 mutable 访问器的接口审计记录落盘（JVM+文档） | `后续-C` + `24-T1` |

### 3.3 M：真实内容度量

| ID | 检查项 | 现状 | 缺口 | 证据位置 | 验收用例（类型） | 责任人轨 |
|---|---|---|---|---|---|---|
| M-01 | 度量端口返回完整布局 artifact：宽/高/baseline/`inkBounds`/实际字号/摘要 | **部分落**：端口有宽高/baseline/字号/digest，**无 `inkBounds`** | `inkBounds` 与类型化布局 | `core/domain/.../VisualExecutionEngine.kt:196-213`（KDoc 自认 `:196-197`） | 端口/度量批次测试（新增 JVM） | `后续-L` |
| M-02 | 隔离服务替换 `UnwiredVisualContentMeasurer` | **未落**：服务注入空实现，任何非空度量抛 `VisualMeasurementUnavailableException`，引擎折成 `INVALID_PROGRAM` | 真实 measurer 接线 | `VisualJsService.kt:310`；`VisualJsProtocol.kt:394-406`；`QuickJsVisualExecutionEngine.kt:336-345`；附录 A 的 Unwired 接线检查 | 带文字/公式程序的设备用例（新增，设备） | `后续-L` |
| M-03 | 真实字形度量/字体白名单/后台布局/12..48 sp/fontScale/累计 4 MP/单 bitmap 16 MiB | **未落** | 排版实现与预算测试 | 契约 §4 `:90`、§5 `:99-100`/`:121`；计划 `:96`/`:100` | `NativeContentLayoutInstrumentedTest`（新增，设备） | `后续-L` |
| M-04 | 诊断语义：度量未接线不能折成“程序错”，否则回喂模型会反复修 program | **未落**：当前折 `INVALID_PROGRAM` | 可区分诊断 | 批次 22 记录 `:232`/`:251`；R3 §1.2 | 负向用例断言 `measurement unavailable` 单独分类（JVM/设备） | `后续-W` |

### 3.4 D：持久化、引用与恢复

| ID | 检查项 | 现状 | 缺口 | 证据位置 | 验收用例（类型） | 责任人轨 |
|---|---|---|---|---|---|---|
| D-01 | JS 四类记录：`tutor_visual_program/snapshot/message_visual`（+保存题 `problem_revision_visual` 引用），与 operation/attempt 同事务 | **未落**：DB v69 只有 `figure_generation_operation/attempt`；`tutor_visual` 零命中 | 表/迁移/外键/索引/备份枚举 | `StudyDatabase.kt:134`；`69.json` 两表；契约 §8 `:161-173`；附录 A 的 DB 版本/表检查 | 全历史迁移 + 新 schema 导出 + 旧行不动（设备） | `后续-P` |
| D-02 | `visualId=programId` 不可变身份；源码/basis/state 变即新 program；CAS + staging/LIVE；正文/trace/link 同事务 | **未落** | 仓库与事务实现 | 契约 §8 `:166`/`:172`/`:184`；计划 `:128-129`/`:133` | DAO/仪器化：取消、存储满、CAS 冲突、重复提交（设备） | `后续-P` |
| D-03 | checkpoint/GC：初始/最新两份；每 program 4 MiB、全局 32 MiB；固定引用不删；磁盘不足拒提交 | **未落** | checkpoint 与回收 | 契约 §8.1 `:180-182`；计划 `:132` | 保存/删会话/GC/固定引用（设备） | `后续-P` |
| D-04 | `LOCAL_VISUAL_PREVIEW` 预览 canonical 资产；备份含程序/快照/预览；缺必需字节拒绝完整包 | **未落** | 常量、备份枚举、完整性 | 契约 §8 `:176`；计划 `:130`/`:134`；R3 §4.5 | 备份/恢复 suite（设备） | `后续-P` |
| D-05 | 冷恢复不执行 JS；字体/renderer 不匹配读预览/文字；未知版本只读 | **未落** | 恢复分支与版本门 | 契约 §5 `:121`、§8 `:190`；计划 `:129` | 进程重建/版本不匹配（设备） | `后续-P` |
| D-06 | **旧 attachedImages cache-only resolver 与“打开旧消息不出网”（契约 §8:188）**：打开含旧 attachedImages 的历史消息仅查本地缓存；缺失映射 UI 呈现显式再生成动作；打开动作 0 网络派发、0 计费 | **部分落**：显式再生成接线已测（WG-05：`AttachedImageRegenerationWiringTest.kt:24` 旧 attachedImages 的显式重新生成走真实生产协调器；入口 `AttachedImageGenerator.kt:155`，R2 L-3）；cache-only resolver 与“打开旧消息 0 派发”无行、无门 | cache-only resolver + 打开旧消息 0 派发观测 | 契约 §8 `:188`；R2 G-9/L-3 | JVM/设备（R2 G-9）：复用 `FigureHttpTransportBypassObservationTest` 的 0 派发观测模式——离线打开旧消息成功且 0 派发/0 计费；缺失映射时显式再生成动作存在（JVM/设备） | `后续-P` + `后续-W` |

> **批次 28 T3 排序注记（v3 补记 2，2026-10-28；R2 排序 §0/§2）**：D-01/D-02/D-05 与 D-03（checkpoint 初始+最新两份 + 磁盘不足拒提交为下界；每 program 4 MiB/全局 32 MiB GC 可随 V-03 批次补）建议归**批次 30**（持久化快照/引用核心，依赖 S-04 的可序列化 DTO）；**D-01 落表须 DB 70+/请求 schema 19+**（现 69/18 已占用，`StudyDatabase.kt:134` 本轮实测 69）；**core/database 8 文件开工即脏为边界**——按共享纪律整文件纳入并注明或停项登记，若他线短期不收敛本批可与批次 31 换序或停项登记（R2 排序 §2）。D-04/D-06 随批次 30/31 能力自然补齐（热区解锁批窗口）。本组行现状本补记未逐行刷新；0 项转"通过"。

### 3.5 E：导出（PDF/DOCX/print/share）

| ID | 检查项 | 现状 | 缺口 | 证据位置 | 验收用例（类型） | 责任人轨 |
|---|---|---|---|---|---|---|
| E-01 | `VisualSnapshotPdfBlock` 接 PDF/DOCX；冻结 `{programHash,state,timeMs,seed,layoutEnvironment,RenderScene}`；导出不执行 JS | **未落**：`VisualSnapshotPdfBlock` 零命中；core/export 仍是旧图像口径 | 导出块与冻结记录 | 计划 `:148`；契约 §8 `:192`；附录 A 的模块/零命中检查 | PDF/DOCX 实际嵌入 + 0 JS 执行（设备） | `后续-E` |
| E-02 | role/revision 过滤：题目区仅当前 revision 的 `QUESTION_DIAGRAM`，`TUTOR_EXPLANATION` 仅 `includeSolution` 解析区，默认练习卷不泄答案 | 旧图有 role 测试，JS 无载体 | JS 版过滤 | 契约 §8 `:194`；计划 `:150` | 含答案过程图的默认卷/显式解析区（设备） | `后续-E` |
| E-03 | `exportFingerprint` 覆盖快照 SHA/字体/renderer/布局/time/seed；版本升级使旧缓存失效 | **未落** | 指纹字段 | 计划 `:148`；契约 §8 `:192` | JVM 指纹测试 + 缓存失效（JVM） | `后续-E` |
| E-04 | PDF/DOCX/打印/分享 0 模型派发、0 JS 执行；重复导出不新增 attempt | **未落**（JS 无载体） | 观测用例 | 契约 §8 `:192`；计划 `:148`/`:281` | 导出观测 0 派发（设备） | `后续-E` |
| E-05 | 同一快照屏幕/PDF 结构/位置一致，不重采样曲线/标签；布局宽度变化生成新快照 | **未落**（v3 补记 2 注记：R2 排序建议批次 29——判据雏形 = 同一 `Rendered` 双端跑 painter、画布指令序列序列化相等，金样像素 diff 备选、阈值先金样固定；依赖 painter 屏半边批次 28 T2） | 同 painter 与金样 | 契约 §8 `:192`；计划 `:166` | 同快照双端金样（设备+导出）；v2 比较机制同 S-06（R2 §三）：画布指令序列序列化相等或金样像素 diff，阈值先金样固定 | `后续-E` |

> 登记项（不阻断 MVA，但不得声称合规）：KD-75 的 PDF `/AIGC` 隐式标识仍 open（`docs/known-defects.md:2774`），`core/export` 是热区，本批只登记。广告/导出文案不得把“有显式图注”说成“已满足隐式标识”。

### 3.6 A：双后端执行与广告面

| ID | 检查项 | 现状 | 缺口 | 证据位置 | 验收用例（类型） | 责任人轨 |
|---|---|---|---|---|---|---|
| A-01 | Coordinator 有 JS 分支：注入 `VisualExecutionEngine`，本地执行 0 远程派发但记 attempt；广告后不回 `UNAVAILABLE` | **未落**：非 IMAGE 直接 `UNAVAILABLE`，构造参数无引擎 | JS 分支与装配 | `DefaultFigureGenerationCoordinator.kt:125-134`/`:182-190`；测试 `:197-220`；R3 §1.6 | 把 `aJavascriptProposalIsNotExecutedInThisWave` 改为正向 + 保留负向（JVM） | `后续-W` |
| A-02 | `TutorToolCall` 接受 `method=JS` + `program`，program 只进独立字段；native/Route B 两路同果 | **未落**：`require(figureProgram == null)`、只许 IMAGE | 构造器与两路解析 | `TutorToolLoop.kt:344-348`/`:401`；`TutorFigureMethodDualParseTest.kt:208-222` | 两路接受 JS + 负向（缺 program、超 64 KiB、program 放正文）（JVM） | `后续-W` |
| A-03 | native schema 同时广告 IMAGE_MODEL 与 JS，JS 只在独立 `program` 字段 | **未落**：`method.enum` 只有 `IMAGE_MODEL` | schema 与描述 | `OpenAiModelProtocol.kt:443-459`；测试 `:255-276` | `theNativeSchemaAdvertisesOnlyImageModel...` 改为双方法 + program 字段（JVM） | `后续-W` |
| A-04 | Prompt assembler 按真实能力注入 schema/SDK 签名/最小样例；去掉 JS fail-closed | **未落**：装配期直接 `require(JS !in availableMethods)` | 动态能力与正文 | `FigurePromptAssembler.kt:40-43`/`:70-73`/`:108-111`；`FigurePromptParityTest.kt:48-50`/`:77` | JS 静态能力 prompt 测试：含 schema/SDK/样例、不含 reduce/form/controls（JVM） | `后续-W` |
| A-05 | `TutorCapabilitySnapshot` 方法级就绪：JS runtime ready 时 `GENERATE_FIGURE` 可广告，不依赖 image backend | **未落**：`imageBackendReady` 只看图像后端 | 双就绪位 | `Capabilities.kt:67-78`；`RoomModelTaskRepository.kt:959`；`TutorCapabilitySnapshotTest.kt:76` | JVM 双就绪矩阵 + 设备广告-执行一致（JVM/设备） | `后续-W` |
| A-06 | `TutorToolDescriptions` 动态描述 method/program，不再写“未配置图像模型就不会生成” | **未落**：长短描述仍是 kind 口径 | 描述文案 | `TutorToolDescriptions.kt:56-68`/`:89-93` | JVM 描述快照测试（JVM） | `后续-W` |
| A-07 | `TutorVisualContext` 生产注入（最近源码/state/诊断/能力版本） | **部分落**：类型在，生产零构造；构造器已 require 三预算（`TutorVisualContext.kt:50-62`，v2 附录 B 复核），但无钉住边界的测试行 | 注入点 | `TutorVisualContext.kt:23-78`；R3 §1.5 | 修复回合上下文集成用例（设备）；v2 边界负向（R2 G-13）：`TutorVisualContextTest` 源码 65 536+1 B、state 16 384+1 B、诊断 2 048+1 字符各一条负向 + 集成侧“放不下时不自动重试”断言 | `后续-W` |
| A-08 | basis 指纹纳入 program 摘要（或入口显式 basis）；两个不同 program 不得同指纹 | **未落**：只拼 operation/sourceSha/imagePrompt | basis 设计 | `FigureGeneration.kt:615-630`；`DefaultFigureGenerationCoordinator.kt:519`；R3 §1.6 | 两个 program 指纹不同 + 旧请求兼容（JVM） | `后续-W` |
| A-09 | 广告-执行一致设备用例：ads JS → 执行成功，而非 `UNAVAILABLE`；0 远程派发 | **未落** | 设备证据 | R3 §4.6；契约 §9 `:202` | 新增 `VisualJsAdvertiseThenExecuteInstrumentedTest`（建议名，设备） | `后续-W` + `24-设备` |
| A-10 | **每逻辑操作的派发与 JS 预算 ledger**：单操作 ≤6 次 Provider 模型执行派发、累计接收源码（含失败尝试）≤128 KiB、新源码布局尝试 ≤6、累计 JS 阶段墙钟 ≤10 s、单份工具 payload 解码后 JSON ≤512 KiB；operation/attempt ledger 与 `FigureGenerationTrace` 不得因换方法重置；**单逻辑操作最多提交两份最终图**；**任一预算耗尽即文字降级并使用原派发余额终答**（v2 补两条子门，契约 §4:86；R2 L-5） | **部分落（v2 现状改写，R2 G-3；v1“图像通道未接 ledger”已过时）**：图像通道**已接** ledger——`DefaultFigureGenerationCoordinator.kt:126` 注入 `RemoteModelDispatchLedger`、`:253-281` 先 `reserve` 后建通道（v2 附录 B 复核 `:126`/`:254`）；ledger“同一逻辑操作最多 6 次、讲题留 1 次终答”与预算耗尽拒绝已实现（`RemoteModelDispatchLedger.kt:17`/`:114`）；契约 §7:155“尚未接 ledger”一句过时（转 T5，R2 §五） | 逐阈值边界证据 + “两份最终图/文字降级”两条子门 | 契约 §4:86、§7:155、§9:202；`DefaultFigureGenerationCoordinator.kt:126`/`:253-281`；`RemoteModelDispatchLedger.kt:17`/`:114`；既有 ledger 测试载体 `ConfiguredTutorFigureGeneratorTest`、`AttachedImageRegenerationWiringTest` 等（R2 G-3） | 每逻辑操作逐项计数：第 7 次派发拒绝（若既有 ledger 用例已覆盖则引用用例名，不重复建）、累计源码 128 KiB+1 B 拒绝、第 7 次新布局拒绝、JS 墙钟 >10 s 拒绝、单份 payload 513 KiB 拒绝；同 operationId 换 method 计数不重置；提交第 3 份最终图拒绝；任一预算耗尽返回文字降级而非新任务；attempt 行随计数落 `figure_generation_attempt`（JVM/设备） | `后续-W` + `24-设备` |
| A-11 | **Route B 三协议 sourceJs 保真负向套件（契约 §7:151）**：`AnthropicMessagesProtocol`/`GeminiGenerateContentProtocol`/`OpenAiResponsesProtocol` 各自 normalize/reconstructedBody 对 sourceJs 的原始换行、反斜杠、中文/emoji、空 content、截断、取消逐协议测试，不得只测 OpenAI 的两个 parser | **未落**：三协议文件在 `core/data/.../model/wire/`（`supportsNativeTools = false` 实证，v2 附录 B）；A-02 只写“native/Route B 两路同果”，无逐协议保真向量；**批次 27 T4 口径注记**：批次 27 计划/工单把"隔离进程网络/filesDir 负向"标注为 A-11（`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md:12` 设备轨）——按本表行义该面属 **P-08 验收列 ④**（随批次 27 设备轨）；本行（Route B 三协议 sourceJs 保真）按行义属 JVM 参数化轨（`后续-W`），不随批次 27 设备轨转状态，维持"未落" | 三协议 × 7 向量参数化套件 | 契约 §7 `:151`；R2 G-8/L-2 | JVM 参数化（R2 G-8）：三协议各喂 7 类向量（原始换行/反斜杠/中文/emoji/空 content/截断/取消）——前 5 类 sourceJs **UTF-8 逐字节相等**；截断/取消显式拒绝、绝不进入执行；21 向量全绿才可声称“Provider 已支持”（JVM） | `后续-W` |
| A-12 | **旧请求/逻辑操作指纹兼容门（契约 §8:186、§9:202）**：新增可空上下文字段（首个即 TutorVisualContext 进请求）必须 `EncodeDefault.NEVER` 或精确旧 schema 投影；扩真实旧请求 golden + 逻辑操作指纹向量——旧 payload 解码语义等价、当前代码对旧输入的编码输出与 golden **逐字节一致**（新可空字段为 null 时不出现新键）；`ModelTasks.kt:1106` 旧 v9 语义回归保留 | **部分落**：基础件已有——`ModelTasks.kt:738-739` `encodeDefaults/explicitNulls`、`:832-842` `logicalOperationJson`/`legacyFingerprintJson`、`ModelTaskFingerprintStabilityTest`（v2 附录 B grep 42 个 `@Test`）；但无“新增可空上下文必须 NEVER/投影 + golden 扩测”行 | visual 上下文扩测行与 golden 载体 | 契约 §8 `:186`、§9 `:202`；R2 G-5/1A#5；`ModelTasks.kt:736-742`/`:832-842`/`:1106` | `./gradlew :core:model:test --tests "*ModelTaskFingerprintStability*"` + 相关 `:core:data:test`（由脚本串行执行）：既有 golden 全绿且 golden 文件 diff 为零；新字段仅在新值非 null 时出现于编码输出（JVM） | `后续-W` |
| A-13 | **全入口同管线（契约 §3.1:35、§1:7）**：不各维护一套方法路由——三个 `FigureGenerationRequest(` 构造点（净化/attachedImages 再生成/讲题）全部经同一 `FigureGenerationCoordinator` 端口与同一 policyVersion/proposal 校验，无入口私有 method 路由分支 | **部分落**：三入口均走 coordinator 建链，但无逐入口断言行，架构 grep 门未立 | 架构 grep 门 + 逐入口同果断言 | 契约 §3.1 `:35`、§1 `:7`；R2 G-10/L-4；`ConfiguredCleanImageGenerator.kt:100`、`AttachedImageGenerator.kt:155`、`TutorFigureGenerator.kt:377`（v2 附录 B 复核恰 3 处） | ① 主机 grep 门：`grep -rn "FigureGenerationRequest(" core/data/src/main` → 恰 3 处，出现第 4 处即失败；② JVM（R2 G-10）：三入口 + IMAGE_PIPELINE_CLASSIFY 各构造 JS proposal，fake 引擎断言同一 policyVersion/proposal 校验、同果（JVM+主机） | `后续-W` |

> **批次 28 T3 排序注记（v3 补记 2，2026-10-28；R2 排序 §0/§2）**：A-01～A-08、A-10（JVM 半）、A-11、A-12、A-13 与 P-11 前缀消费、P-12 ②③、M-04、Q-01 建议归**批次 31**（双后端执行装配；**广告开关不动**——仍 fail-closed，`availableMethods` 加 JS 是全部门绿后的最后动作，沿 §0 勾选规则 5；能力快照/prompt/契约广告文本零改动直到全门绿）；A-10 设备半边与 A-09/V-01/V-02/Q-04 建议归**批次 32**（纵向链与质量门设备轮）；批次 30（持久化）与 31 互不依赖、可换序，但都在 V-01 全链（32）之前（R2 排序）。两处待主会话：**A-01 装配形态**（X-01 归位前装配点——§5 #5 不允许"先在 app 模块广告静态、后归位"）、**Q-02 映射裁定**。本组行现状本补记未逐行刷新；0 项转"通过"。

### 3.7 Q：保真、质量与负向门

| ID | 检查项 | 现状 | 缺口 | 证据位置 | 验收用例（类型） | 责任人轨 |
|---|---|---|---|---|---|---|
| Q-01 | JS proposal 对 `requiresPixelFidelity=true` 拒绝 | **缺失（真实 policy 漏洞）**：JS 分支只查参数交互，IMAGE 分支才查 pixel | 负向门 + 测试 | `FigureGenerationPolicy.kt:185-218` vs `:298-306`；R3 §1.4 | `FigureGenerationPolicyTest` 新增 JS pixel 负向（JVM） | `后续-W` |
| Q-02 | `PIXEL_CLEANUP` 的 purpose→target 映射核对：语义是“保留原像素”时 `requiresPixelFidelity` 必须为 true | 入口传 `false` | 映射裁定/修正 | `ConfiguredCleanImageGenerator.kt:103-107`；R3 §1.4 | 入口 target 映射测试（JVM） | `主会话` + `后续-W` |
| Q-03 | 参数交互不足时 JS 拒绝；现有负向测试保持绿 | 已落 | 无（回归即可） | `FigureGenerationPolicy.kt:212-217`；`FigureGenerationPolicyTest.kt:291` | 现有 JVM 用例复跑（JVM） | `后续-W` |
| Q-04 | 六类 fixture 静态子集 + 新组合，独立预期；学科正确性与渲染 fixture 分开 | **未落** | fixture 与 oracle | 计划 `:156-168`；R3 §4.3 | 截图 1/2/5/6 + 波叠加/轨迹采样（设备）；v2 oracle 载体定义（R2 §三）：“独立预期” = fixture + expected 结构 JSON 断言（非人工目测截图） | `后续-Q` |
| Q-05 | 120 题盲测 90/95/90/关键错误 0，或主会话正式缩小并改写门值 | **未落**；真实 Provider 无凭据，质量 UNVERIFIED | 质量记录/裁定 | 设计 `:91`；计划 `:324`；契约 §9 `:202-210`；R3 §4.7/§5.2 #1 | PRD-09 盲测记录（真实 Provider 另测） | `主会话` + `后续-Q` |
| Q-06 | 布局/无障碍：360/600/840 dp、fontScale 1/1.3/2、TalkBack、横屏/旋转、后台恢复、无水平溢出 | **未落**（JS 无 UI） | 布局与无障碍 | 计划 `:166`；契约 §6 `:137` | 新增仪器化矩阵（设备） | `后续-Q` + `后续-U` |

### 3.8 V：端到端纵向链

| ID | 检查项 | 现状 | 缺口 | 证据位置 | 验收用例（类型） | 责任人轨 |
|---|---|---|---|---|---|---|
| V-01 | 静态纵向链：tool → coordinator → isolated → 真实度量 → compiler → UI → 保存/重启 → PDF/DOCX；0 Provider 派发 | **未落** | 全链用例 | R3 §4.4；计划 `:23`/`:250` | `VisualGenerationStaticEndToEndInstrumentedTest`（建议名，设备） | `后续-W` + `24-设备` |
| V-02 | 方法切换：JS 失败 → IMAGE_MODEL、图像不佳 → JS；同 operationId、不新增确认、预算不重置、无双重载荷 | **未落** | 切换用例 | 契约 §1 `:11`、§7 `:155`；计划 `:67`/`:120` | 双向切换 + 同 operation 断言（设备/JVM） | `后续-W` |
| V-03 | 保存题/删除会话/GC：删会话不删保存题图；固定引用不回收；进程重建读最近 checkpoint；strictOffline 可重放已持久程序且 0 派发 | **未落** | 跨用例 | 契约 §6 `:141`、§8.1 `:180-184`；计划 `:132` | 三组仪器化（设备） | `后续-P` + `后续-W` |
| V-04 | 正文/trace/visual link 同事务；失败/取消清 staging；迟到结果不覆盖新 revision | **未落** | 事务与竞态 | 契约 §8.1 `:184`；计划 `:133` | 取消/迟到/staging 仪器化（设备） | `后续-P` |

### 3.9 R：发布矩阵

| ID | 检查项 | 现状 | 缺口 | 证据位置 | 验收用例（类型） | 责任人轨 |
|---|---|---|---|---|---|---|
| R-01 | API 23 运行时加载/执行/终止 | **未测** | 设备证据 | 契约 §9 `:202`；R3 §4.8 | API 23 AVD/真机（设备）；v2 套件集口径（R2 §三）：隔离 6/6 + KD-70 20/20 构成“运行时加载/执行/终止”判据，另记录 `adb shell getprop ro.build.version.sdk` | `后续-R` + `24-设备` |
| R-02 | arm64 运行时加载/执行/终止 | **未测**（v3 补记·批次 27 T5：批次 27 计划把"R8 下隔离往返"标注为 R-02（`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md:12` 设备轨）——按本表行义该面属 **R-04**（release 变体 QuickJS JNI 面）；本行仍为 arm64 运行时，且本机 x86_64 宿主对 arm64 镜像原理性拒载（批次 25 R4 定论），本机不可采证） | 设备证据；**"R8 隔离往返"证据归批次 27 设备轨**（R-04 行已同步登记承接） | R3 §4.8；批次 23 记录 `:164`；批次 27 基线 §7.1/§10.1（标签失配裁定："按清单行义同步，不按计划 §0:6 的括注"） | arm64 设备（设备）；v2 套件集口径同 R-01（隔离 6/6 + KD-70 20/20） | `后续-R` |
| R-03 | arm64 16 KB 运行时（**v2 口径修订，R2 G-6**）：AVD/真机须 **API ≥ 35 且 arm64 且页大小 16384**（先 `adb shell getconf PAGE_SIZE` 确认）；套件集 = ① `VisualJsIsolationInstrumentedTest` 6/6 + ② `QuickJsVisualExecutionEngineInstrumentedTest` 20/20（含 P2）+ ③ `runtimePageSizeIs16KB` 装载断言；证据按 R4 规范落 E-file | **未测**：现行 16 KB 证据仅批 24 E-04（ps16k_x86_64/API 36/16384 页/3/3，**仅装载断言**）；隔离 6/6 与 KD-70 20/20 全部跑在 API 34/x86_64/4 KB（step02 头表），从未在任何 16 KB 设备执行（v2 附录 B 复核） | 16 KB 设备上的隔离与 P2 套件执行证据 | 批 24 E-04 `evidence/2026-10-26-batch-24/step04-page-size-16k.md:10`/`:16-19`；E-03 `:113`；E-09 `:119`；批次 24 基线 §7 | ③ 在 API ≥ 35/arm64/16384 设备返回 16384 且 ①② 全绿（设备） | `后续-R` + `24-设备` |
| R-04 | release R8 冒烟（internal R8 绿不等于 release）；**v2 补 QuickJS JNI 面（契约 §2:22；R2 G-11/L-8）**：release（R8）变体 APK 上跑 `VisualJsIsolationInstrumentedTest` 至少“往返 + watchdog”两例，且 `unzip -l <release.apk> \| grep libquickjs.so` 非空 | 批 24 E-03 release 冷启动冒烟 PASS（`am start` ×2，x86_64，一次性本地 key 签名）；KD-69 open；**无 QuickJS/visualjs 执行面**（v2 附录 B 复核）；批次 26 E-04 同型复跑 PASS（`assembleStrictOfflineRelease` exit=0 + 两次冷启动 ok/COLD + 备份 34/34，仍无 QuickJS/visualjs 执行面） | QuickJS JNI 面的 release 证据；E-03 不能充当本门；**批次 27 设备轨承接 release 变体隔离往返两例**（批次 27 计划"R-02 R8 隔离往返"按本表行义归本行，`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md:12`） | KD-69 `:2674-2693`；E-04 `:142`；批 24 E-03 `evidence/2026-10-26-batch-24/step03-r8-release-smoke.md:10`/`:18`；契约 §2 `:22` | release 变体冷启动/Worker/OCR（设备）；v2：release 下 AIDL 往返返回 `Rendered` 且 watchdog 硬杀路径可用（两例绿） | `后续-R` + `24-设备` |
| R-05 | AAR 钉版与 kotlin-stdlib 2.3.21 不变；16 KB ELF 证据保持；**v2 补许可证载体门（契约 §2:22/§9:202；R2 G-12/L-7）**：quickjs-kt（MIT）/QuickJS 许可文本须有仓库载体（资产文件或 NOTICE 汇总，载体位置可由主会话裁定）；钉版 commit/sha256 回归核对不漂移 | 已裁定并记录；E-09 14/14×2；但 quickjs-kt/QuickJS 许可文本**当前 0 命中（真缺载体）**——app assets 仅 compose-richtext/open-claude-ui、core/data assets 仅 dense 模型（v2 附录 B 复核）（v3 补记 2 注记：R2 排序建议批次 29 零成本并轨——许可文本落仓内 assets/NOTICE，载体位置主会话裁定） | 许可证载体；sha 漂移核对 | 契约 §2 `:22`；E-09 `:119`；KD-73 `:2748`；R2 G-12 | 回归构建/ELF 扫描（主机）；v2（R2 G-12）：`find app/src/main/assets core/*/src/main/assets -type f \| grep -i licen` 应含 quickjs-kt（MIT）/QuickJS 条目；`2026-10-25-16kb-arm64-api23-build.md` 的 sha 记录未漂移 | `后续-R` |

### 3.10 X：横切、架构与源证据

| ID | 检查项 | 现状 | 缺口 | 证据位置 | 验收用例（类型） | 责任人轨 |
|---|---|---|---|---|---|---|
| X-01 | 开放 JS 前归位：Service/client/AIDL → `core:visual-js`；app 的 `AndroidVisualExecutionEngine` 组合三层；端口/wire 不变 | **未落**：无模块、无类（仅注释出现） | 模块与装配 | 契约 §5.1 `:110-119`；计划 `:51-57`/`:98`；`settings.gradle.kts:19-34`；附录 A 的模块/零命中检查 | 依赖/架构测试 + 迁移后隔离 suite 复跑（JVM/设备） | `主会话`（R3 §5.2 #4）+ `后续-A` |
| X-02 | 源证据：`VisualSourceBasis` + `sourceSeenDispatchId`；Plan 能取到原题资产或可追溯结构化源；同批准备不冒充已发送 | **未落**：`TutorQuestionContext` 无原题资产；两符号零命中 | 源证据门 | 契约 §3.1 `:37`；计划 `:117`；R3 §2.3 | 缺源/错图/SHA 变化/授权不足/预算不足（JVM/设备） | `后续-W` |
| X-03 | strictOffline：禁止新外发/新源码，但已持久程序可重布局/交互且 0 Provider 派发 | **未落**（JS 无持久载体） | 策略与测试 | 契约 §6 `:141`；计划 `:120` | strictOffline 重放（设备） | `后续-P` + `后续-W` |
| X-04 | 既有负向测试只可转正向并保留负向，不得删除/放宽；不得把 `UNAVAILABLE` 测试改成“跳过” | 纪律项；当前测试仍钉旧行为 | 实施时逐条核对 | R3 §1.6/§4.6；测试见 §0 规则 4 | code review 清单 + 用例名 grep（主机） | `后续-W` + 复核 |
| X-05 | OpenAI SSE 工具增量聚合门（WG-04a）已落，保留回归；不得回退为“半段参数可执行” | **已落**：`reconstructedChatCompletion` 聚合 `delta.tool_calls`，按 index/终态/预算 | 无（回归） | `OpenAiSse.kt:135-230`/`:447`；`OpenAiChatCompletionsProtocol.kt:66`；`OpenAiSseToolCallAggregationTest.kt:23` | 现有 JVM 套件复跑（JVM） | `后续-W` |

---

## 4. 与契约 §4/§5 和 4E 计划的交叉引用

| 检查组 | 契约锚点 | 4E 计划锚点 | WG / 其他 |
|---|---|---|---|
| P 前提门 | §4 `:62-72`（隔离/启动面/队列/终止/watchdog/重建/死亡未确认）、`:74-90`（预算表）、`:86`（每操作预算与 512 KiB payload）、§4:72（KD-66 与 warm-up/ready 缺口） | §2 `:74-80`、§7 `:172-176` | KD-66、KD-70、**KD-76（warm-up/ready）**；P-08 启动面、P-09 队列、P-10 死亡未确认、P-11 前缀消费、P-12 熔断（§4:70） |
| S 编译器/场景/回放 | §5 `:92-102`（SceneV1/RenderScene）、§5.1 `:106-119` | §3 `:93-101` | WG-04、WG-07；v2 增 S-09 深冻结（§3.2:58） |
| M 真实度量 | §3.2 `:51-53`、§4 `:90`、§5 `:99-100`/`:121` | §3 `:96-97`/`:100` | — |
| D 持久化/恢复 | §8 `:161-176`、§8.1 `:178-190` | §4/§5 `:126-136` | WG-06、WG-08；v2 增 D-06 cache-only（§8:188） |
| E 导出 | §8 `:192-196` | §6 `:148-151`、`:166` | WG-07 |
| A 双后端/广告 | §1 `:9-13`、§3 `:29-31`/`:58`、§4:86、§7:155、§9 `:202` | §4 `:107-120` | WG-01～05、WG-10；A-10 每操作 6 次派发/累计预算；v2 增 A-11 三协议保真（§7:151）、A-12 指纹兼容（§8:186）、A-13 全入口同管线（§3.1:35） |
| Q 保真/质量 | §9 `:202-210` | §6 `:154-168`、§11 `:321-325` | PRD-09 |
| V 纵向链 | §9 `:202-204` | `:23`、§7 `:172-176` | WG-04/06/07/08 |
| R 发布矩阵 | §9 `:202` | §2 `:89` | — |
| X 架构/源证据 | §3.1 `:37`、§5 `:104`、§5.1 `:106-119`、§6 `:141` | §1 `:51-57`、§4 `:117` | WG-02 |

**契约 §4 的五处硬约束在 MVA 中不得被解释掉**：

1. `§4:68` “宿主先废弃 generation，再请求终止/解绑，通过 Binder death 确认旧实例终结后最多重建一次……死亡未确认时展示 fallback，不能并发放大进程数。” —— P-01/P-04 的实现与设备证据必须覆盖“确认/未确认”两条路径，不能用“unbind 即死亡”替代；**未确认路径的专门门是 P-10**，P-01 的 6/6 只覆盖确认路径。
2. `§4:72` 的落地状态已明确“**KD-66 保持 open**”且“本节仍不得读成‘进程级硬终止已验证’”。因此 MVA 文案不得引用“1500 ms 硬上界”作为卖点，除非 P-05 关闭。
3. `§4:62` 的隔离启动面（Application 早退、构造/字段/companion、ContentProvider/AndroidX Startup、无文件/网络主机绑定）是 P-08 的独立门；P-01 的 UID/往返用例不能替代 `dumpsys` 审计。
4. `§4:64` 的队列/背压（1 在飞 + 最多 2 待绘、每 program 最新 state、跨 program 按序、BUSY 保留原帧、限次重调度）与消费者校验（调用身份、描述符方向/长度）是 P-09；AIDL 形状存在不等于实现。
5. `§4:86`、`§7:155`、`§9:202` 的每逻辑操作 6 次派发、累计 128 KiB 源码、≤6 次新源码布局、累计 JS 阶段 10 s、单份 payload 512 KiB 与持久 ledger/trace 是 A-10；只写“0 远程派发但记 attempt”或“预算不重置”不构成该门。

**契约 §5 的两处硬约束**：

1. `§5:104` “`core:data` 只通过该端口做来源/幂等/持久化/CAS，不直接引用 AIDL/原生排版。” —— 当前 `QuickJsVisualExecutionEngine` 落 `core:data` 是已登记偏差；X-01 归位前，广告会让偏差长期化（R3 §2.1）。
2. `§5:121` 持久化 RenderScene 必须是可序列化 DTO，冷恢复不执行 JS；字体/renderer 不匹配时读原预览/文字。D-05 与 E-05 必须按这条验收，不能“有 PNG 就算”。

---

## 5. 待主会话裁定（本表不自裁）

1. **MVA 质量门是否缩小**：现行契约/计划是 120 题盲测 + 首次 90/预算内 95/每组 90/关键错误 0（设计 `:91`；计划 `:324`）。只广告静态子集是否仍要求完整 120 题？缩小必须**正式改写门值并留记录**，不能默认跳过（R3 §5.2 #1）。
2. **KD-76 warm-up/ready 的实现形态**：服务内 `onCreate`/首请求前 warm-up，还是首请求装载单独计时、装载后才 arm watchdog？两者都必须保证 warm 服务上的真实挂死仍 1500 ms 硬杀（KD-76 `:2807`）。同时决定是否把 R3 的“6/6”文本正式升级为含 ③④⑤ 的门（本表已按升级口径写，但 R3 原文未改）。
3. **KD-66 `hard+500 ms` 是否作为广告硬门**：现有 watchdog 用例以 ≤6 s 判死。R3 建议：广告可以不以关闭 KD-66 为前提，但能力/文案不得声称硬上界；若以“进程级硬终止”为卖点，必须先关闭（R3 §5.2 #2）。
4. **4E.3 是否单独立项**：计划 `:21` 只有一行边界，没有任务/验收节；在广告范围从“静态”扩到“复杂源/保真扩展”前，必须先补可执行计划（R3 §5.2 #3）。
5. **架构归位时机**：契约 §5.1 `:119` 要求 4E.2 开放 JS 前迁入 `core:visual-js` + `AndroidVisualExecutionEngine`。是否允许“先在 app 模块广告静态、后归位”？R3 建议不允许（R3 §5.2 #4）。
6. **`PIXEL_CLEANUP` 的 target 语义**：若业务语义确为“保留原像素的编辑/去污”，`ConfiguredCleanImageGenerator.kt:103-107` 的 `requiresPixelFidelity=false` 就是入口侧漏洞；需主会话决定改入口还是改语义（R3 §1.4；Q-02）。
7. **责任轨排期**：§0 的 `后续-*` 是建议归属，未立项；谁先做、如何在热区空出后串行，需主会话排期。

---

## 6. 风险与“不能当证据”的半步

- **把“隔离绿”当“JS 绿”**：隔离只证明进程/UID/终止/往返；不证明场景可编译、可显示、可保存、可导出（R3 §5.3）。
- **把“隔离 6/6”当完整隔离门**：KD-76 指出 6/6 不含 warm-up/ready；沉降态绿不证明冷启动首个请求不被 1500 ms watchdog 误杀（KD-76 `:2811-2836`）。
- **把“纯几何跑通”当“教学图可用”**：`UnwiredVisualContentMeasurer` 仍会把任何带字图拒成 `INVALID_PROGRAM`（`VisualJsService.kt:310`）。
- **把“policy 接受 JS”当“双后端已配对”**：coordinator/tool/parser/schema 四处仍硬拒（A-01～A-03）。
- **把“fidelity 字段存在”当“保真已验”**：没有 oracle/质量记录，且 JS 分支未拒 pixel-fidelity（Q-01）。
- **把“internal R8 绿”当“release 可发”**：KD-69 仍要求 release 不回退；API 23/arm64 运行时也未测（R-01/R-02/R-04）。
- **把“PDF 有显式图注”当“隐式标识合规”**：KD-75 仍 open，`core/export` 是热区，本批不实现（§3.5 登记项）。
- **把“P2 x86_64 绿”当“两 ABI 已验”**：E-02 只测 x86_64/API 34/4 KB；arm64/16 KB 未测，且 P2 用例 98 ms、用例内协作预算 10 s（非默认 100 ms），慢设备可能先 `PHASE_TIMEOUT`（P-02）。
- **把“LOADING/有界重试已落 + 注入形状设备绿”当“加载/重试态已实现”**：批次 25 已落 LOADING/有界重试代码层且两例注入形状设备绿（v3 刷新，取代 v2 的“close() 完成等待点”条目）；剩余缺口是双态真实冷启动成对证据、29.5 s worst-case 实测与“可见/交互生命周期限次重新调度”调用方层（P-04）。
- **把“死亡未确认的日志”当“fallback 已实现”**：代码只置 `unavailable=true` 并写日志，没有 UI fallback/加载态；6 个设备用例也不含未确认负向路径（P-10）。
- **把“[UNATTRIBUTED] 有前缀”当“宿主已消费”**：分类器写了前缀，但协调器无引擎注入、`UNATTRIBUTED` 命中 0；AIDL/宿主是否保留前缀没有断言（P-11）。
- **把“ledger 已接线”当“预算门已验证”**：接线是现状（A-10 v2 现状改写），逐阈值边界、“两份最终图/文字降级”用例未跑（R2 G-3）。
- **把“16 KB 装载断言绿”当“16 KB 套件绿”**：E-04 只有 `runtimePageSizeIs16KB` 装载断言；隔离 6/6 与 KD-70 20/20 从未在任何 16 KB 设备执行（R-03 v2）。
- **本表自身**：所有“已落”都只是代码存在；本轮没有运行任何 Gradle/设备用例，**0 项可勾选**（附录 A 仅只读 grep）。

---

## 附录 A：本轮实跑核对（只读；未跑 Gradle/设备）

> 下列是本轮**实际执行过的同类只读命令摘要**（路径用 `...` 缩写，实际执行使用完整路径；行内注释为实际输出）。没有把未执行的命令写成已执行。

```bash
cd "D:/smart mistake book"
git rev-parse --short HEAD                                             # 5bc8bdf8
git status --porcelain -- app/src/main/kotlin/.../visualjs \
  app/src/androidTest/kotlin/.../visualjs core/data/src/main/.../visual # 4 个 M（T1/T2 在飞）
grep -n "@Test" app/src/androidTest/kotlin/.../VisualJsIsolationInstrumentedTest.kt   # 6 个
grep -n "FIRST_CONNECT_TIMEOUT\|RECONNECT_TIMEOUT\|DEATH_CONFIRM" \
  app/src/main/kotlin/.../VisualJsClientEngine.kt                     # 15_000 / 10_000 / 2_000
sed -n '185,220p' core/model/.../FigureGenerationPolicy.kt             # 185-218 无 pixel 检查
grep -n "require(FigureGenerationMethod.JS" core/data/.../FigurePromptAssembler.kt  # 40、108
grep -n '"IMAGE_MODEL"' core/data/.../OpenAiModelProtocol.kt           # 453 唯一枚举值
grep -n "imageBackendReady" core/model/.../Capabilities.kt core/data/.../RoomModelTaskRepository.kt # :71/:959
grep -n "require(figureProgram\|require(figureMethod" core/model/.../TutorToolLoop.kt # 344/347
grep -n "method != FigureGenerationMethod.IMAGE_MODEL" core/data/.../DefaultFigureGenerationCoordinator.kt # 182
for p in VisualSceneCompiler VisualSceneValidator AndroidScenePainter AndroidContentLayout \
  MathContentLowering SceneSvgWriter NativeTutorForm VisualSceneCard VisualSnapshotPdfBlock; do \
  grep -rn "class $p" --include=*.kt core app feature | grep -v /build/ | wc -l; done  # 全 0
grep -rn "VisualSceneCard\|VisualControls\|VisualSemantics" --include=*.kt core app feature | grep -v /build/ | wc -l # 0
find app core feature -type f -name "visual-sdk*" | wc -l              # 0
find app/src/main/assets -type f | wc -l                               # 3（仅 open-source 许可文本，无 .js）
grep -c "reduce" core/data/.../QuickJsVisualExecutionEngine.kt          # 0
grep -n "UnwiredVisualContentMeasurer" app/src/main/.../VisualJsService.kt app/src/main/.../VisualJsProtocol.kt # 310/394
grep -rn "QuickJs\|visualjs\|VisualExecutionEngine\|VisualScene" core/ui/src core/export/src # 0
grep -rn "VisualScene\|visualId" feature/tutor/src                      # 0
sed -n '15,40p' settings.gradle.kts                                   # include 仅 app/core/feature/benchmark，无 visual 模块
grep -n "STUDY_DATABASE_VERSION" core/database/.../StudyDatabase.kt    # :134 = 69
grep -o '"tableName": *"[^"]*"' core/database/schemas/.../69.json | grep figure # 2 张
grep -n "deltaHasToolCalls\|reconstructedChatCompletion" core/data/.../OpenAiSse.kt # :84/:157（聚合已落）
grep -n "^## KD-66\|^## KD-70\|^## KD-74\|^## KD-76" docs/known-defects.md # :2573 open / :2682 open / :2768 closed / :2805 open
sed -n '410,430p' app/src/main/.../VisualJsService.kt                  # armWatchdog 在请求开始即 arm 1500 ms
sed -n '120,132p' core/domain/.../VisualExecutionEngine.kt             # SERVICE_HARD_DEADLINE=1500L
```

**时点说明**：`docs/known-defects.md` 在本轮写作期间被批次 24 T5 追加了 **KD-76**（`:2797`）。因此本表不沿用 R3 的“6/6 即隔离门”结论，而是升级为“6/6 + warm-up/ready 用例”（P-07）。`known-defects.md` 仍在被 T3/T5 修改，行号可能继续漂移。

**已读但未运行**：`VisualJsIsolationInstrumentedTest`、`QuickJsVisualExecutionEngineInstrumentedTest`、`FigureGenerationPolicyTest`、`TutorCapabilitySnapshotTest`、`OpenAiSseToolCallAggregationTest`、`VisualExecutionEngineContractTest` 全部只做源码/测试名核对。

**本轮未执行**：任何 `./gradlew`、`adb`、`am instrument`、`connectedAndroidTest`；任何 git 写命令；任何设备/E-file 复跑。设备绿引用批次 23 E-file（历史）与批次 24 E-file（E-01/E-01b/E-02，当前），**不是本轮实跑**。

**线号漂移提醒**：T1/T2 仍在改 `VisualJsClientEngine.kt`、`VisualJsIsolationInstrumentedTest.kt`、`QuickJsVisualExecutionEngine.kt`、`VisualJsFailureClassification.kt`；本表行号以 2026-10-26 本轮只读时点为准，实施时需按符号名重新定位。`VisualJsClientEngine.kt` 的常量行在本轮 close/竞态修复后为 `:920`/`:926`/`:933`（P-01/P-04/P-06 已同步）。

---

## 附录 B：批次 25 T4 合并核对（v2，2026-10-27；只读）

> v2 只做文档合并：R2 第四部分增量表并入正文（6 新增行 + 14 处改判据）。零代码、零 Gradle、零设备、零 git 写。下列为本轮**实际执行**的只读核对命令与关键输出；R2 G-1..G-13 判据本身**未执行**，仍是待执行定义。

```bash
git rev-parse --short HEAD                                        # d783ae84（= origin/main）
git status --porcelain -- docs/research/2026-10-26-batch-24-4e2-admission-checklist.md
# → 合并前 0 行（干净；v1 已随 aabae781 提交）；合并后本文件为批次 25 T4 的 M
grep -cE "^\| [PSMDEAQVRX]-[0-9]+ \|" docs/research/2026-10-26-batch-24-4e2-admission-checklist.md
# → 合并前 63（与 HEAD aabae781 一致，R2 §0 对账成立）；合并后 69（63 + 6 新增）
grep -n "^## KD-76" docs/known-defects.md                         # v2 时点 :2805 open（当时 P-07 引用有效）；复核 1 轮（2026-10-27）重定位：现 :2811 open，P-07 引用已按 :2811-2836 更新
grep -n "Object.freeze\|Date = undefined\|Math.random = function" \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt
# → :810 Date=undefined、:812 Math.random 替换、:813 Object.freeze(Math)；无 input/metrics 冻结（S-09 现状）
grep -n "同一逻辑操作最多\|预算已耗尽" core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/RemoteModelDispatchLedger.kt
# → :17（6 次上限+讲题留 1 次终答）、:114（预算耗尽拒绝）（A-10 现状改写依据）
grep -n "private val ledger\|ledger.reserve" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/DefaultFigureGenerationCoordinator.kt
# → :126 注入、:254 reserve（A-10 现状改写依据；R2 记 :253-281 区间）
grep -n "encodeDefaults\|explicitNulls\|logicalOperationJson\|legacyFingerprintJson" \
  core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt
# → :738-739、:832-842（A-12 基础件）
grep -c "@Test" core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/ModelTaskFingerprintStabilityTest.kt
# → 42（R2 记“≥15”；本轮实测 42）
grep -n "require" core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorVisualContext.kt
# → :50-62 三预算 require（A-07 v2）
grep -rn "FigureGenerationRequest(" core/data/src/main --include=*.kt
# → 恰 3 处：ConfiguredCleanImageGenerator.kt:100、AttachedImageGenerator.kt:155、TutorFigureGenerator.kt:377（A-13 grep 门基线）
grep -rln "supportsNativeTools = false" core/data/src/main --include=*.kt
# → wire/GeminiGenerateContentProtocol.kt、wire/AnthropicMessagesProtocol.kt、wire/OpenAiResponsesProtocol.kt（A-11）
grep -rli "quickjs" app/src/main/assets core/data/src/main/assets | wc -l   # → 0（R-05/L-7 真缺载体）
grep -A3 "<provider" app/build/intermediates/merged_manifests/localFirstDebug/processLocalFirstDebugManifest/AndroidManifest.xml | grep "android:name"
# → 6 个 Provider（BatchImportFileProvider/androidx FileProvider/MistakePdfFileProvider/MistakeDocxFileProvider/InitializationProvider/MlKitInitProvider），均无 :visual_js process（P-08 静态基线）
grep -n "executeMutex" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt
# → 仅 :84/:167（P-09 现状“未落”在 v2 时点成立）；find app/src -name "*QueueTest*" → 0 命中
grep -n "inFlight\|STATUS_BUSY" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt
# → :85/:186-189/:200/:210/:235（P-09 服务端单在飞/BUSY 与槽位释放路径）
grep -n "verdict\|device-avd\|device-page-size" docs/research/evidence/2026-10-26-batch-24/step04-page-size-16k.md
# → PASS 3/3、ps16k_x86_64、16384（R-03：仅装载断言的既有 16 KB 证据）
grep -n "device-api\|device-abi\|device-page-size" docs/research/evidence/2026-10-26-batch-24/step02-quickjs-oom.md
# → API 34 / x86_64 / 4096（隔离与 KD-70 从未在任何 16 KB 设备执行）
grep -n "verdict\|device-abi" docs/research/evidence/2026-10-26-batch-24/step03-r8-release-smoke.md
# → release 冷启动 ×2 PASS、x86_64、一次性本地 key 签名（R-04：无 QuickJS 面）
```

**v2 未执行**：任何 Gradle/adb/dumpsys/am instrument；R2 G-1..G-13 的任何判据；任何 git 写命令。**MVA 复核结论**：P-07 仍未过（KD-76 open，v2 时点 `:2805`，复核 1 轮重定位 `:2811-2836`；代码层已落、设备证据为零），warm-up/ready 落层且设备验证前不得广告；v2 不改变 §0 勾选规则，0 项转“通过”。

---

## 附录 C：批次 26 T5 复判合并核对（v3，2026-10-27；只读）

> v3 只做文档合并：R2 复判（`docs/research/2026-10-27-mva-reeval.md`）§1 增量表并入 P-04/P-07/P-09/P-12 四行 + 总体判定句理由（§4 #4）+ §6 一条半步（§4 #5）+ §0 记号一行；同批按批次 25 记录 §9 收口与复判 §4 #6 刷新契约 :72 两处“设备复跑 UNVERIFIED”（并按记录 §3.3 `:121` 给队列“取消在飞帧不再 abandon worker”补待主会话确认标注）与 KD-66/KD-76 状态块（KD-76 两 Progress 块措辞合并）。零代码、零 Gradle、零设备、零 git 写。下列为本轮**实际执行**的只读核对命令与关键输出；复判/R2 判据本身未执行，0 项转“通过”。

```bash
git rev-parse --short HEAD                                        # 405c8373
git status --porcelain -- docs/research/2026-10-26-batch-24-4e2-admission-checklist.md \
  docs/current/visual-generation-contract.md docs/known-defects.md
# → v3 合并前三文件均 0 行（干净）；合并后为本批 T5 的 M。
#   例外：docs/known-defects.md 在本 T5 读取与编辑之间被他线批次 26 T1 轨先行写入
#   KD-66「批次 26 T1 落码」Progress 块（+2 行，KD-76 锚 :2813→:2815）；按共享纪律
#   整文件纳入本批并在此注明——该块内容本 T5 未改动。
grep -cE '^\| [PSMDEAQVRX]-[0-9]+ \|' docs/research/2026-10-26-batch-24-4e2-admission-checklist.md
# → 69（v3 不新增行：只刷新 P-04/P-07/P-09/P-12 四行的现状/缺口/证据/验收列）
ls docs/research/evidence/                                        # 无 2026-10-27-batch-26（设备轨未出数）
grep -n "_TIMEOUT_MILLIS\|_POLL_INTERVAL_MILLIS" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt | grep "const val"
# → :1206 FIRST_CONNECT 15s / :1212 RECONNECT 10s / :1219 DEATH_CONFIRM 2s / :1230 READY 10s / :1233 READY_POLL 100ms
grep -n "MAX_PENDING_FRAMES\|inFlight" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsFrameQueue.kt
# → :70/:88-89/:101/:174-180/:206=2（KDoc :16-17）
grep -n "withTimeoutOrNull(6_000L)\|PROGRAM_FUSED" app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt
# → :202/:552（④ 弱口径两处）/ :318（G-7 ① 设备断言）；行号随批次 26 T1 落码漂移（grep -c "@Test" = 13）
grep -n "fusedDigests\|figureSha256Hex" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt
# → :112/:187/:188-193/:242-250/:277/:294-297/:440/:516/:915（P-12 熔断锚）
grep -rn "explicitUserRetry = true" --include=*.kt app core | grep -v /build/
# → 仅测试构造 4 处：仪器化 :323/:327/:338 + VisualExecutionEngineContractTest.kt:425（G-7 ② 现状）
grep -n "VisualExecutionRequest(" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt   # :185 唯一生产构造
grep -n "isReady" app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsService.aidl   # :38
grep -n "PROTOCOL_VERSION" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt   # :35 = 2
grep -n "val ready = AtomicBoolean\|warmUpOnJsExecutor\|killSelf" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt
# → :75/:113/:127-132/:135（warm-up/ready 服务端锚）
grep -c "@Test" app/src/test/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsFrameQueueTest.kt \
  app/src/test/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsResultFrameIdentityTest.kt   # 5 / 5（JVM 10 例在盘，未重跑）
grep -n "^## KD-66\|^## KD-76" docs/known-defects.md              # :2573 open / :2817 open（v3 全部编辑落地后的终值；T5 前时点为 :2815，他线 T1 块插入前为 :2813）
sed -n '86,92p;173,175p' docs/research/evidence/2026-10-27-batch-25/step01-visualjs-isolation.md
# → 逐例 1→0 计数（T2 三例 + T3 两绿一红）与绿侧结论行（P-04/P-07/P-09 引用锚）
```

**v3 未执行**：任何 Gradle/adb/dumpsys/am instrument；R2 复判/复判判据的任何执行；任何 git 写命令。设备绿一律转录批次 25 E-file（`evidence/2026-10-27-batch-25/step01-visualjs-isolation.md`），不是本轮实跑；批次 26 T1/T2 设备轨未出数，本表不预填任何设备判定。**MVA 复核结论（v3）**：维持不成立（复判 §2）；**warm-up/ready 落层且设备验证前不得广告**口径维持（P-07 ③④⑤ 判据未达，KD-76 open）；0 项转”通过”。

---

## 附录 D：批次 27 T5 状态同步与行号勘误收尾核对（v3 补记，2026-10-27；只读）

> **范围**：批次 26 设备轨（E-01…E-05）出数后，按批次 27 计划 T5（`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md:12`）做状态同步——KD-76 设备判据口径块（`docs/known-defects.md`）与契约 :72 的 KD-76 状态句按批次 26 证据刷新（③ PARTIAL 不判定达成／④ 强证据解除弱口径／⑤ 行为层 ×2 轮绿），本表 P-07/P-12/R-02/R-04 四行与 §0 记号行同步；R-02/R-04 补”R8 隔离往返”标签映射注记（批次 27 计划把该面标为 R-02，按批次 27 基线 §7.1/§10.1 裁定”按清单行义同步、不按计划 §0:6 括注”归 R-04 行义，证据归批次 27 设备轨）；”warm-up 实测装载成本”缺口标注归批次 27 设备轨（KD-76 口径块 + P-07 缺口列）。**零代码、零 Gradle、零设备、零 git 写。0 项转”通过”、行数 69 不变、MVA 不成立维持、KD-76 open 维持（JS 不广告）。契约 :72 的队列”待主会话确认”两处标注未动（归 T4）。**下列为本轮**实际执行**的只读核对命令与关键输出。

```bash
git rev-parse --short HEAD                                        # caa0726a
git status --porcelain -- docs/known-defects.md docs/current/visual-generation-contract.md \
  docs/research/2026-10-26-batch-24-4e2-admission-checklist.md     # 编辑前 0 行（三文件干净）；编辑后 3 个 M（本补记）
grep -cE '^\| [PSMDEAQVRX]-[0-9]+ \|' docs/research/2026-10-26-batch-24-4e2-admission-checklist.md   # 69（不新增行）

# 行号勘误收尾（批次 26 复核确认的 step03 §9 更正传播复核）：
grep -cF “UnsatisfiedLinkError” build/device-track-batch26/step03-coldboot-logcat.txt   # 1（系统进程 PID 3177 噪声，step03 §9 已归因）
sed -n '298p;327p' app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt
#   → :298 = val first = engine.execute(issue240Request())；:327 = val secondDeath = engine.execute(issue240Request(explicitUserRetry = true))
#     （§9 更正值 :299→:298/:321→:327 经源码逐行实测正确）
grep -rn “:299\|:321” docs/known-defects.md docs/research/2026-10-26-batch-24-4e2-admission-checklist.md \
  docs/current/visual-generation-contract.md
#   → 命中均为无关锚或自引（清单 §11 `:321-325` 契约锚、known-defects `StudyReviewPlannerService.kt:299`、
#     本附录 D 自引的勘误命令/输出文本）；#240 注入锚旧值在台账零残留，step03 §9 勘误传播完整——收尾完成，无需再改。
# v3 锚现值复核（成立）：withTimeoutOrNull(6_000L)=:202/:552、PROGRAM_FUSED=:318、
#   aSecondConcurrentRequest…=:523、门用例=:373、explicitUserRetry=true 构造=:323/:327/:338、
#   客户端常量=:1206/:1212/:1219/:1230/:1233、VisualJsService warm-up/ready=:104-138/:375-377。

# KD-76 Symptom 漂移锚勘误（批次 25 warm-up 落层 + 批次 26 T1 入类所致，已在 KD-76 补时点注）：
grep -n “aColdStartAttachBeyondTheOldConnectBudget\|COLD_START_ATTACH_DELAY_MILLIS” \
  app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt
#   → 用例现 :274、夹具 KDoc 现 :760-761、常量现 :763（旧引 :249-260/:379-381/:383）
grep -n “armWatchdog” app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt   # :377 调用/:484 定义（旧引 :413-427）

# 契约/台账 KD 标题锚现值（R-04 行的 KD-69 锚已随本补记改现值；其余见下”未改项”）：
grep -n “^## KD-66\|^## KD-69\|^## KD-70\|^## KD-72\|^## KD-76\|^## KD-77” docs/known-defects.md
#   → :2573(closed)/:2674(closed)/:2694(closed)/:2746(open)/:2819(open)/:2853(open)
git diff --check                                                  # exit=0（仅 CRLF 提示）
```

**实测漂移、如实登记且未改（归属不在本任务四项内，交后续清单复核轨/T4/主会话）**：

- P-01 行”仍不覆盖 hard+500 ms”已过时（KD-66 已于批次 26 关闭、门用例三轮独立绿）。
- P-04 行”④ KD-76 ③ 真实冷启动首请求未执行”已过时（E-03 尝试、PARTIAL）；测试 seam `readyTimeoutOverrideForTest` 现于 `VisualJsClientEngine.kt:1182`（v3 记 `:1176`，同文件未变、v3 时点偏移）。
- P-09 行”设备 2/3（1 红 = A-14 夹具已修未复跑）”已过时（批次 26 E-01 13/13 全绿、A-14 转绿、R15 复绿）。
- P-02 行与 §2 行 2 的 KD-70 锚 `:2686` → 现 `:2694`；P-05 行 KD-66 区间 `:2573-2663` → 现节 `:2573-2745`；附录 C 的 KD-76”终值 `:2817`”→ 现 `:2819`（均为批次 26 记录员关闭/回填块插入后的行移，历史时点值保留不改）。
- v1 时代锚（批次 25 落层所致）：S-02/S-09 的 `QuickJsVisualExecutionEngine.kt:809-814` → 现 `:847-852`（bootstrap；`Date = undefined` 现 `:848`、`Math.random` 替换现 `:850`、`Object.freeze(Math)` 现 `:851`）；`contentId 引用检查 :676` 现位于 `validateScene` `:645-686` 区间内（保守面仍成立）；M-02/§1.3/§6/§2 行 3 的 `VisualJsService.kt:310` → 现 `:382`（Unwired 接线点）、`VisualJsProtocol.kt:394-406` → 现 `:398-408`。**时点限定**：以上"现值"为本补记本轮实测；本轮收尾时 `QuickJsVisualExecutionEngine.kt`/`QuickJsVisualExecutionEngineInstrumentedTest.kt` 已转 M、`app/.../visualjs/VisualContentMeasuring.kt` 与 `tools/visual_compiler/` 已出现 ??（同批实施轨在飞）——后续读者一律按符号名重锚，不得沿用行号。
- 批次 26 记录 §3.5 步三行所引 5 条 kill 归因锚（`:387`/`:533`/`:298`/`:327`/`:201`）经源码实测全部现值成立（其 `:298`/`:327` 已带”§9 更正后”标注）。

**v3 补记未执行**：任何 Gradle/adb/dumpsys/am instrument；R2/复判判据的任何执行；任何 git 写命令。设备绿一律转录批次 26 E-file（`docs/research/evidence/2026-10-27-batch-26/`），不是本轮实跑。

---

## 附录 E：批次 28 T3 状态同步核对（v3 补记 2，2026-10-28；只读）

> **范围**：批次 28 计划 T3（`.zcode/plans/plan-2026-10-28-batch-28-scenev1-device-painter.md:14`）四项——① KD-76 状态按批次 27 装载成本证据与剩余解锁条件刷新（`docs/known-defects.md` KD-76 节内新增批次 28 T3 Progress 块）；② 本表 painter/PDF 回放/持久化/双后端四组行按 R2 排序（`docs/research/2026-10-28-mva-remaining-order.md`，下称 R2 排序）刷新（§0 责任轨六行、S-04/S-05/S-06 行现状刷新、§2 行 6/E-05/R-05 行内注记、§3.4/§3.6 组级排序注记）；③ 契约 §5/§5.1 painter 落层状态同步（`docs/current/visual-generation-contract.md` 三处：§5.1 表头时点、`core:visual` 回放行现状、归位段注记）；④ 本附录即核对清单。**零代码、零 Gradle、零设备、零 git 写。0 项转"通过"、行数 69 不变、MVA 不成立维持、KD-76 open 维持（JS 不广告）。**批次 28 T1（设备复跑）与 T2（painter 落码）在飞未出数，本补记只登记设计落盘与排序建议，不预填设备/落码判定。下列为本轮**实际执行**的只读核对命令与关键输出。

```bash
git rev-parse HEAD                                                # 8116ad98f6d0a7b6...（= origin/main，批次 27 收口提交）
git status --porcelain -- docs/known-defects.md docs/current/visual-generation-contract.md \
  docs/research/2026-10-26-batch-24-4e2-admission-checklist.md     # 编辑前 0 行（三文件干净）；编辑后 3 个 M（本补记 2）
grep -cE '^\| [PSMDEAQVRX]-[0-9]+ \|' docs/research/2026-10-26-batch-24-4e2-admission-checklist.md   # 69（不新增行）
# painter 落码未发生（批次 28 T2 在飞；S-04/S-06/契约 :115 的"未出数"表述据此）：
grep -rn "class AndroidScenePainter\|class VisualDrawOpsPainter\|object VisualDrawOpsPainter" --include=*.kt core app | wc -l   # 0
ls core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/ | wc -l   # 10（批次 27 收口态，无 Painter 文件）
# 批次 27 E-04 数字对源（KD-76 新块引用源）：
grep -n "p50\|p95" docs/research/evidence/2026-10-27-batch-27/step04-warmup-cost.md
#   → :10 出数 p50=686/p95=725/max 738、16+10 全部 ready、arm64/真机/低内存 UNVERIFIED；:66-68 样本/分位数表；:123-124 PASS（出数）
# 契约 painter 锚全量与 §6 面核对：
grep -n "AndroidScenePainter\|painter" docs/current/visual-generation-contract.md
#   → :102（§5 处方，未动）、:110/:115/:119（§5.1，本补记三处更新）、:190/:192（§8，不在任务命名节，未动）；
#     §6（:125-141）painter 零锚——任务命题"契约 §5/§6"中 §6 无 painter 状态可同步，如实登记
# S-05 刷新引用源（编译器现状，本轮实读）：
grep -n "SCENE_FIELD_CONTROLS\|SCENE_FIELD_UNAVAILABLE\|hitRegions" core/data/src/main/resources/visual/visual-compiler.js
#   → :732 controls 空数组门 / :738 form 拒绝 / :950-975 hitRegions 数组+预算校验非空不拒 / :1077-1088 序列化进 DrawOps 输出
# D 组注记引用源：
grep -n "STUDY_DATABASE_VERSION" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt   # :134 = 69
grep -n "^## KD-76\|^## KD-77" docs/known-defects.md              # :2819（KD-76 标题锚不变）/ :2857（KD-77 因本补记块插入行移 +2）
git diff --check                                                  # exit=0（仅既有 CRLF 提示，同附录 D 口径）
```

**漂移登记（本补记引起/本轮发现，如实登记且未改）**：
- **KD-77 标题锚行移**：known-defects.md 在 KD-76 节内插入批次 28 T3 块（2 行）后，KD-77 标题 `:2855` → 现 `:2857`；R2 排序文档所引 `KD-77 :2855-2863` 随之行移（按标题定位不受影响）。R2 排序文档属同批他任务产物，本任务不改。
- **S-02 现状已过时（超出本补记四组范围，未改）**：S-02 现状"无 `.js` 资产"已因批次 27 落地 `core/data/src/main/resources/visual/visual-compiler.js` 与 `visual-sdk.js`（本轮 `find` 实测两文件在盘）而失真；S-01 所列五个类名本轮 grep 仍 0 命中（按类名口径其"未落"仍成立）。S-01/S-02 与 `后续-C` 行的全面刷新留后续清单复核轨。
- **批次 27 E-04 "26/26" 口径注**：E-file 原文为"16+10 全部到达 ready"（套件 churn 组 16 + 控制组 10）；KD-76 批次 27 设备轨块作"26/26"，本补记新块作"16+10=26/26"，三处语义一致。

**附录 E 未执行**：任何 Gradle/adb/dumpsys/am instrument；R2 排序判据的任何执行；任何 git 写命令。设备绿与装载成本数字一律转录批次 27 E-file（`docs/research/evidence/2026-10-27-batch-27/step04-warmup-cost.md`），不是本轮实跑；painter 落码/设备复跑（批次 28 T1/T2）在飞未出数，本补记不预填判定。
