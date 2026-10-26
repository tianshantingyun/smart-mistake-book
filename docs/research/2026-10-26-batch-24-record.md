# 批次 24 记录（2026-10-26）：隔离门最后一例 + KD-70 夹具 + 4E.2 准入 + 台账收尾

> 记录时点：2026-10-26（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对计划、基线、四研究文档、当前源码/测试、`git status`、`build/` 产物与设备证据目录后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未提交/推送、未改产品代码。** 本文件是记录员本轮唯一新增交付；另有文档同步两处：`docs/REFACTOR-MASTER-LINE.md`（顶部 + 4E/补修行）与 `docs/research/algorithm-version-ledger.md`（§3.34 零 bump），见 §7；证据 `00-index.md` 的 §7/§9 记录回链也已补（§3.8）。
> 计划：`.zcode/plans/plan-2026-10-26-batch-24-isolation-final-kd70.md`（下称"计划"）。
> 基线：`docs/research/2026-10-26-batch-24-baseline.md`（下称"基线"；基线本身是未跟踪新文件）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`。仓内没有本批门禁日志（`build/` 下只有设备轨日志），§4 只作转录。
> **本记录不声称"批次全绿"**：KD-66 的 hard+500 ms 关闭门未达；KD-69 的生产签名与中文 OCR 未验；KD-70 只测 x86_64/4 KB 且精确 `mallocSize` 未打印；KD-72 锁未入库、负控未做；KD-73 全解析图 API 兼容无证明；KD-75 零代码；KD-76 的 warm-up/ready 未做；arm64 16 KB 运行时未测；真实 Provider 无凭据、模型质量 UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。

## 1. 范围与基线

### 1.1 计划范围（计划 §1）

- **做**：R1 Binder death 确认语义与 isolatedProcess SIGKILL 时序（驱动 T1）；R2 QuickJS memoryLimit 记账语义与确定性 OOM 夹具（驱动 T2）；R3 4E.2 准入清单；R4 UI 队列排序设计；T1 `abandon()` 死亡确认修复 + 加载态超时处理（app visualjs）；T2 KD-70 夹具/分类修复（core/data visual）；T3 KD-74 关闭与 KD-72 状态同步；T4 4E.2 准入检查清单落盘；T5 热区条件轨；设备轨（隔离复跑、KD-70 P2、R8 release 冒烟、16KB 抽验、备份/迁移抽验）；证据落盘、双门、双复核、修复、记录、提交推送。
- **不做**（计划 §1/§2）：锁文件生成（等干净树）；4E.2 交互/保真实现与 JS 广告；PDF `/AIGC` 实现（core/export 热）；G1/G2 等热区实现（除非 T5 判定干净）；真实 Provider；Wave X。

### 1.2 沿用裁定（本批执行，不得改）

1. **隔离门全绿前 JS 不广告**；本批设备绿只证明 T1 修复，不等于广告门。
2. **4E.2 交互/保真不做**；本批只做进程层设备验证、准入清单与缺陷登记。
3. **AIDL 只以 Gradle 任务验证**，禁止 `aidl.exe` 双输入形式。
4. **AAR 钉版不动**：`quickjs-kt-android:1.0.15`；`kotlin-stdlib` 维持 2.3.21。
5. **锁文件不生成不提交**（KD-72 等干净树，本批只同步状态文本）。
6. **KD-74 关闭以"记录回链 + 证据 + 主会话等价裁定"三者齐备为准**（裁定已写批次 23 记录 §8）。
7. **真实 Provider 无凭据**：模型质量 UNVERIFIED。

### 1.3 基线摘要与记录时点复核

- **HEAD**：记录时点 `8bb79efd`（`git rev-parse --short HEAD`）；计划/基线写 `5bc8bdf8`。`8bb79efd` 是批次 23 记录提交 `5bc8bdf8` 之上的 KB 线提交（`git log --oneline -3`：`8bb79efd feat(kb)…` → `5bc8bdf8 docs(5b,4e,beta)…` → `f8a362ee feat(5b,4e,beta)…`）。**记录员未 `git fetch`，origin/main 实时 hash UNVERIFIED。**
- **工作树**：基线冻结 `669` 行（200 M + 469 ??）；记录时点 `691` 行 = **213 M + 478 ??**（`git status --porcelain | wc -l`、`grep -c '^ M'`、`grep -c '^??'`）。增量来自本批落点与他线继续在飞，不能按模块 `git add`。
- **schema**：`STUDY_DATABASE_VERSION = 69`（`core/database/.../StudyDatabase.kt:134`）；请求 schema `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`（`core/model/.../ModelTasks.kt:546-547`）；`VisualJsWire.PROTOCOL_VERSION = 1`（`app/.../visualjs/VisualJsProtocol.kt:31`）。本批零 DB/请求 schema、零版本串 bump（算法台账 §3.34）。
- **热区记录时点计数**：`feature/library` **43**、`feature/tutor` **59**、`feature/capture` **16**、`feature/review` **12**、`core/ui` **35**、`core/export` **6**、`core/domain` **5**、`core/data` **15**、`core/database` **9**、`feature/profile` **4**（`git status --porcelain -- <dir> | wc -l`）；`SmartMistakeBookRoot.kt`、`SecondaryScreens.kt`、`settings.gradle.kts`、`.gitignore` 均 M。**热区未清空。**
- **落点干净度**：T1/T2 的六个代码/测试文件记录时点均为 `M`（`VisualJsClientEngine.kt`、`VisualJsIsolationInstrumentedTest.kt`、`QuickJsVisualExecutionEngine.kt`、`VisualJsFailureClassification.kt`、`VisualJsFailureClassificationTest.kt`、`QuickJsVisualExecutionEngineInstrumentedTest.kt`）；`docs/known-defects.md`、`docs/current/visual-generation-contract.md` 为 `M`；六份研究文档与基线、准入清单为 `??`；证据目录 9 个 `.md` 为 `??`。基线"落点干净"只是开工时点，**本批目标文件已按纪律整文件纳入**。
- **锁文件**：`find . -name 'gradle.lockfile'` = **13**；另有 `settings-gradle.lockfile`（267 B），锁文件合计 **14**；`git ls-files '*gradle.lockfile'` = 0；`git status` 可见 **13 条**未跟踪锁条目（`core/markdown/gradle.lockfile` 被未跟踪目录折叠成 `?? core/markdown/`）；`benchmark/gradle.lockfile` 不存在。**不得原样提交（KD-72 open）。**
- **证据体量（记录员实测，含本轮索引回链补写）**：`docs/research/evidence/2026-10-26-batch-24/` 9 个 `.md`、**1,269 行**、内容 **67,839 B**（`cat *.md | wc -lc`）、`du -ck *.md` = **84 KiB**、`du -sk --exclude=.mimosa` = **88 KiB**、含 `.mimosa/` 的 `du -sk` = **94 KiB**；最大文件 `00-index.md` 18,457 B / 195 行。未超 R4 单文件 64 KiB/400 行与单批 512 KiB/16 个上限。索引 `:7` 已同步为 1,269 行；此前的 1,263 行是索引补写前时点数；`84/88 KiB` 与该索引一致。
- **计划与实际的时点差**：① HEAD 多一个 KB 线提交；② T4 工单称 58 项，记录时点准入清单实测 63 项（`grep -cE '^\| (P|S|M|D|E|A|Q|V|R|X)-[0-9]{2} \|'`），多出的行来自复核修复环回填（P-08…P-11、A-10 等）；③ 设备轨为完成 E-03 实际运行了 Gradle `assemble*`（与"本轨不跑 Gradle"的共享纪律字面冲突，已披露，见 §3.7/§5 #9）；④ R2 目标文件开工时在盘（未跟踪早期稿），T2 整文件重写并注明。

## 2. 四研究线与复核结论

### 2.1 R1 Binder death 确认语义与 SIGKILL 时序（`docs/research/2026-10-26-binder-death-confirmation.md`，462 行）

- **四个回调的权威语义已核**（AOSP 镜像源码，`developer.android.com` 本机不可达）：`binderDied` 是唯一按 binder 实例证明进程死亡的事件；`onServiceDisconnected` 是框架 `DeathMonitor` 收到同一 `BR_DEAD_BINDER` 后 post 到主线程的回调；`onBindingDied` 是 AMS bring-down 的另一条路径，不是普通 SIGKILL 的主回调；`onNullBinding` 只表示 `onBind()` 返回 null。`unbindService` 只解注册框架的 `DeathMonitor`，**不会撤销客户端自己的 `DeathRecipient`**。
- **"先 `unlinkDeath` 再等 death"是结构性丢事件**：`unlinkToDeath` 返回 `true` 即保证 `binderDied()` 不再被调用；HEAD 的 `abandon()` 在等待前解注册并清空 `link.binder`，而 `link.death` 唯一完成点是 `markDead` → E-01c 的 16 ms 时序下 1 s 内必然等不到（批次 23 四次独立复现）。
- **可靠判据**：per-bind `DeathRecipient` + 保持 proxy 强引用 + 有界等待；`isBinderAlive` 不是主动探测（只读 `BpBinder::mAlive`，仅在收到 `BR_DEAD_BINDER` 或事务返回 `DEAD_OBJECT` 时清零），保留注册时轮询才有意义；`pingBinder` 只能作可选诊断；超时只能 fail-closed，不能重建。
- **冷启动的正确处理不是只加大超时**：推荐"状态回调/加载态 + warm-up/ready"，A（15 s 首连）只作必要条件；首连 **15 s**、重建 **10 s**、死亡确认 **2 s**、宿主阶段 2.5 s，最坏约 **19.5 s**（不是 1500/2500 ms 硬上界）。稳态 1500/2500 ms 不动；冷启动失败不得写 `programHash` 熔断。
- **16 ms 的正确读法**：E-01b 的 16 ms 是 **attach→SIGKILL**，不是 death 延迟；仓库没有 ms 级 death 延迟实测，2 s/50 ms 是产品选择、不是平台 SLA。
- **对工作树修复的源码审查（20:08 快照）**：顺序、2 s 预算、超时分支 `markDead`、迟到死亡丢弃、16 ms 文案已收口；**剩余 4 个缺口**：① 首个 execute 的冷启动 1500 ms 误杀要 warm-up/ready（本批未做）；② 连接超时仍永久 `unavailable`，无 LOADING/可重试态；③ `close()` 的死亡等待仍在 `unlinkDeath` 后（只作诊断）；④ 修复与新增用例未设备复跑，且无 death 延迟实测。记录时点复核：③ 已在复核修复环改为 `markDead`（§5 #15）；④ 在 R1 落盘时点未复跑，批次 24 设备轨已补 E-01/E-01b（§3.7）；①② 仍在。
- **UNVERIFIED**：无 Gradle/JVM/仪器化/设备执行；无 ms 级 death 延迟测量；API 23–27 与 OEM/冻结/深睡未测；`pingBinder` 阻塞性未量化。

### 2.2 R2 QuickJS memoryLimit 记账与确定性 OOM 夹具（`docs/research/2026-10-26-quickjs-memory-accounting.md`，511 行）

- **`mallocSize=177216` 不是失败点，而是 OOM 之后异常展开时的释放结果**。主机制：`JS_CallInternal` 异常出口走 `done:` 释放该帧所有局部变量与栈值（`quickjs.c:20690`、`:20699-20709`）；`__JS_FreeValueRT` 对引用计数归零的对象立即 `free_zero_refcount`（`:6474-6485`），`free_object` 逐个 `free_property`（`:6341-6353`），属性里的 `next` 引用被释放后**级联**清空整条链表（`free_property` 调 `JS_FreeValueRT`，`:6097-6117`）。旧夹具的链只由局部 `head` 与节点 `next` 持有，`JS_Eval` 返回前就被同步释放。
- **次要机制同向但不能当主因**：`JS_NewObjectFromShape` 前会 `js_trigger_gc`（`:5619`），但阈值每次触发后抬到当前水位 1.5 倍，后续分配不再满足触发条件，不能解释为"频繁 GC"。
- **不是超时**：P2 的 64 ms（批次 23 日志）远小于 10 s 协作期限；超时路径是 typed `QuickJsInterruptedException` → `PHASE_TIMEOUT`，不会落 `[UNATTRIBUTED]`。
- **夹具修复**：每轮把链表头钉到 `globalThis.__visualHeapProbeHead`，给链一个 GC 根；全局对象本身就是根（`JS_MarkContext` `:2717-2728`）。保留 50 万轮有界循环、小对象、无大 realloc/长字符串。
- **分类口径**：90% 阈值与 `MEMORY_LIMIT` 映射不变；比值主证据带 `PENDING_CALIBRATION`（内部枚举 + 诊断字面量），引擎原文 `InternalError: out of memory` 不带；90% × 16,777,216 = **15,099,495**（不是 14,400,000）。
- **设备回填（批次 24 E-02）**：x86_64/API 34/4 KB 上 20/20，P2 绿（`MEMORY_LIMIT` + `attributed by memoryUsage` + `mallocLimit=16777216` + `PENDING_CALIBRATION` + `mallocSize != -1`），精确 `mallocSize` 未打印；arm64/16 KB 未测；P2 用例总耗时 98 ms、用例内 `cooperativeTimeoutMillis=10_000`（非默认 100 ms）。
- **固有限制（如实登记）**：真实程序把唯一引用放在局部/栈上时，OOM 后仍落 `UNATTRIBUTED` → `INVALID_PROGRAM`；锚定夹具只是测试仪器，不改变产品的可观测性。R2 文档 `:296` 仍写用例 KDoc 的 90% 数字是 14,400,000、"待 T2 校正"，而 KDoc 已是 15,099,495；分类器 `:93` 仍写"唯一设备数据是 E-02 的 mallocSize=177216"——两处为时点残留（§5 #7）。

### 2.3 R3 4E.2 准入清单（`docs/research/2026-10-26-4e2-entry-checklist.md`，465 行）

- **六项必需项里只有"策略/类型骨架"部分落**：`FigureGenerationCapabilities`/`FigureGenerationPolicy`/`FigureArtifact.Program` 已存在；SceneV1 编译器/validator/state codec/SDK、真实内容度量、交互/表单、保真验证、双后端执行均为 **0 落或硬关闭占位**。
- **六项之外的五个横切缺口**：生产装配（无 `AndroidVisualExecutionEngine`、无 `core:visual-js`）、JS 持久化（无 program/snapshot/message 表）、回放/导出（无 painter、无 `VisualSnapshotPdfBlock`）、源证据（`TutorQuestionContext` 无原题资产）、发布矩阵/真实 Provider（API 23/arm64/arm64 16 KB/release R8、真实质量未验）。
- **最小可广告子集建议 MVA-JS-STATIC**：只广告"静态 SceneV1"——无 `reduce`、无原生控件/滑块/表单、无动画、无 SVG；交互/表单后置 4E.2，复杂源/保真扩展后置 4E.3。**静态子集仍须先补齐**：SceneV1 编译器 + SDK、真实内容度量、painter/PDF 回放、持久化、导出、双后端执行与质量门；**当前不具备广告条件**。
- **"半步"不可广告**：纯几何程序能跑、`VisualExecutionEngine` 端口契约用例绿、手写 JS 能渲染一张图，都不能作为"JS 可用"的证据。
- **设备判据**：隔离 suite 6/6（但该 6/6 已被 KD-76 指出不含 warm-up/ready）、引擎 20/20（或裁定的等价 P2）、真实度量设备用例、compiler/painter/PDF 同快照、静态端到端、迁移/备份/导出、广告-执行一致、发布矩阵（API 23/arm64/arm64 16 KB/release R8）。
- **时点差**：R3 写"release R8 本批未重跑"；批次 24 E-03 已跑 release 冷启动 ×2（一次性本地 key），但生产签名/中文 OCR/`usage.txt` 全量穷举仍缺，R3 的其余缺口不因此关闭。

### 2.4 R4 产品与 UI 队列排序（`docs/research/2026-10-26-ui-queue-sequencing.md`，897 行）

- **七项一句话判定**：G1/G2 显式标识（P0，零代码，第一档）；AWAITING_VERIFICATION UI（数据层已有终态、UI 映射成 `Idle`，第一档小批收口）；5b 知识点复习（不是零起点，入口/会话/队列/3E 已在，剩余 KD-68 诚实预览 + F8/F9/F18 + 真实数据验收，第二档）；5c 讲题/错因（只有自由文本，受 D-1/AUD-11/schema 70，第四档最后）；阶段 6 档案设置（薄弱点展示已有、行不可点、无"未练习"，第三档）；6B-3 app 注入（端口/提示词/ViewModel 在，app 未注入，且受 `core:data` 依赖边界约束，第二档先设计）；AUD-08 切分金标门（门已定义、金标已冻结、脚本可复跑，但门 0.5714 < 0.9、5/7 页无应用输出、`app-split.json` 无仓内生产者，第一档工具链可先做）。
- **三个必须纠正的现状认知**：5b 不是"未开工"；6B-3 不是"把端口接上"那么小（模块依赖图不依赖 `core/export`/`feature:tutor`）；AUD-08 的验收不是"把 0.5714 提到 0.9"，而是门可复跑 + 自动入库继续关闭。
- **T5 条件轨结论（基线冻结时点）**：`feature/library` 29 M + 11 ??、`feature/tutor` 37 M + 21 ??、`core/ui` 11 M + 24 ??、`core/domain` 4 M、`feature/profile` 3 M——声明热区全部未空出；唯一干净的是 `tools/split_golden`。**本批 T5 只能登记，不能做 UI/生产代码。**
- **推荐顺序（热区清空后串行）**：G2 → G1 → AWAITING → 5b-1/KD-68 → AUD-08 → 6B-3 → 5b-2/F8-F9-F18 → 阶段 6 → 5c；若 3D 先收口，5b-2 前移到 6B-3 之前。AUD-08 工具半边可在热区清空前先做。
- **待裁**：MVA 质量门是否缩小（120 题盲测 90/95/90/关键错误 0）、KD-66 hard+500 ms 是否作为广告硬门、4E.3 是否单独立项、架构归位时机；真实 Provider 不可用时只能做到 fake 端口可见 + 质量 UNVERIFIED。

### 2.5 研究复核结论

- 四份研究文档均在盘、有来源分级与未验证清单；本批实施与它们逐项对齐。R1 推荐的 warm-up/ready 未被 T1 实现（已登记 KD-76），R2 的锚定夹具与 `PENDING_CALIBRATION` 已落，R3 的准入清单被 T4 升级为 63 项可执行表，R4 的排序未被本批执行（热区未空）。
- 发现两类时点漂移：① R2 `:296` 的 14,400,000 与分类器 `:93` 的"唯一设备数据 177216"已被 E-02 取代但文本未改；② R3 的"release 本批未重跑"已被 E-03 部分取代，但生产签名/OCR 仍缺。
- 零版本串：DB schema 69、请求 schema 18、`LearningCoreVersions.*`、`ReviewPlannerV2` 全部未动（算法台账 §3.34）。

## 3. 五实施轨 + 集成 + 设备 + 证据轨

> 判定口径：**代码级完成** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / JVM 工件 / 只读日志 / 无证据=UNVERIFIED）。文件清单按工单 JSON 与 `git status` 交叉核对（§7）。
>
> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 abandon 死亡确认 + 冷启动预算 | 部分 | **代码修复成立 / 设备 6/6 / KD-66 仍 open** | 修复与两代 APK 设备绿；warm-up/ready、LOADING/重试、死亡未确认负向用例未做 |
> | T2 KD-70 夹具/分类 | 部分 | **代码+夹具+设备绿 / KD-70 仍 open** | E-02 20/20、P2 绿；精确值、arm64/16 KB、慢设备时限未验 |
> | T3 台账收尾 | 完成 | **完成（文档）** | KD-74 三条件齐备关闭；KD-72 仍 open；两处文本口径残留 |
> | T4 准入清单 | 完成 | **完成（文档）** | 63 项检查表落盘；MVA 不成立；T4 摘要称 58 项、交叉引用措辞过宽 |
> | T5 条件轨 | 完成 | **完成（登记）** | 新增 KD-76；热区未清空，未做 UI/生产代码 |

### 3.1 T1 `abandon()` 死亡确认 + 冷启动首连预算 —— **代码修复成立 / 设备 6/6 / KD-66 仍 open**

- 文件（2）：`app/src/main/kotlin/.../visualjs/VisualJsClientEngine.kt`（M，diff 共 185 行变更，插入+删除合计）、`app/src/androidTest/kotlin/.../visualjs/VisualJsIsolationInstrumentedTest.kt`（M，共 120 行变更）。
- **abandon 顺序**：`abandon()` 先 `markAbandoned()` → `terminate(generation)`（oneway）→ `unbindService` → `awaitDeathConfirmation(link)`；**确认后才 `unlinkDeath`**（`VisualJsClientEngine.kt:580-607`）。`awaitDeathConfirmation` 以 `link.death`（由 `markDead` 完成）为主判据、`isBinderAlive` 每 50 ms 轮询兜底、2 s 超时；超时最后一刻 `isBinderAlive=false` 也先 `markDead`（`:622-647`）。未确认才 `unavailable=true`（fail-closed），日志写明"late death is dropped and never used to rebuild"（`:594-601`）。**它消灭的失败**：批次 23 E-01b/E-01c 的 `the first abandon did not confirm death`（服务 16 ms 内已 SIGKILL，客户端 1 s 内收不到本应到达的 binder death）。
- **预算**：首连 `FIRST_CONNECT_TIMEOUT_MILLIS = 15_000L`（`:920`）、重建 `RECONNECT_TIMEOUT_MILLIS = 10_000L`（`:926`）、死亡确认 `DEATH_CONFIRM_TIMEOUT_MILLIS = 2_000L`（`:933`）、轮询 `DEATH_POLL_INTERVAL_MILLIS = 50L`（`:936`）；`connectLocked` 用 `connections.get() == 0` 区分首连/重建（`:545-549`），重建预算在 `connectLocked` 统一扣（`:516-524`）。
- **复核修复环的两个代码修复**（已在当前树）：① `Link.markConnected` 与 `markDead` 共用 `stateLock`，`connectLocked` 用 `isConnected()` 在锁内复查 `dead`（`:557`、`:794-799`、`:837` 起），消灭"死亡与发布 CONNECTED 交错"竞态；② `close()` 对当前 link 调 `markDead("the client was closed", unexpected=false)`，完成 `connected`/`death`、终结 activeCall，首连/重建中的 `connectLocked` 立即返回 null（`:224-244`），不再等满 15 s。
- **用例（6 例）**：`theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`:216-242`）新增"重建后真实 `execute` 返回 `Rendered`"断言（BUSY 会红）与第二次确认后不再 bind；新增 `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`（`:251-263`），`DelayedAttachContext` 只把 `onServiceConnected` 推迟 7.6 s（`:385`、`:397-414`），不推迟 `bindService`/真实 fork。
- **设备（E-01/E-01b）**：两代 debug APK 各 `OK (6 tests)`，`Time: 23.945` / `22.208`；6 个用例逐个 `1→0`；批次 23 的 abandon 红未复现；冷启动 7.6 s 用例通过；`death was not confirmed` 告警 0（`evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:124`、`step01b-visualjs-isolation-rerun.md:102`、`00-index.md:53-57`）。**记录员未复跑设备，只实读判定行与原始日志摘要。**
- **未达/残余**：① 连接超时仍一次失败即永久 `unavailable`，无 R1 §5.3 的 LOADING + 500 ms 重试；② 服务端 warm-up/ready 未实现（KD-76）；③ `executeMutex` 排队无上界（`:84`、`:167`）；④ 死亡未确认路径没有专门设备负向用例（6 例只覆盖确认路径）；⑤ `isBinderAlive` KDoc 的"两条独立事实互为兜底"措辞仍强于 AOSP 语义（R1 §4.2），无交错测试；⑥ "迟到 death 被丢弃"措辞过强：实际仍会走 `markDead`/`handleDeath`，只是 `unavailable` 让 `rebuildAfterDeath` 早退（不影响不重建的保证）。**KD-66 保持 open**：现有 watchdog 用例以 ≤6 s 判死，弱于 hard+500 ms。

### 3.2 T2 KD-70 夹具/分类 —— **代码+夹具+设备绿 / KD-70 仍 open**

- 文件（5）：`core/data/.../visual/QuickJsVisualExecutionEngine.kt`（M，9 行变更）、`core/data/.../visual/VisualJsFailureClassification.kt`（M，53 行变更）、`core/data/src/test/.../visual/VisualJsFailureClassificationTest.kt`（M，41 行变更）、`core/data/src/androidTest/.../visual/QuickJsVisualExecutionEngineInstrumentedTest.kt`（M，60 行变更）、`docs/research/2026-10-26-quickjs-memory-accounting.md`（新，整文件纳入）。
- **夹具**：`HEAP_LIST_SOURCE` 保留 50 万轮 `head = { next: head }`，每轮追加 `globalThis.__visualHeapProbeHead = head;`（`QuickJsVisualExecutionEngineInstrumentedTest.kt:761-770`）；P2 断言 `MEMORY_LIMIT`、`attributed by memoryUsage`、`mallocLimit=16777216`、`PENDING_CALIBRATION`、`mallocSize != -1`（`:300-324`），KDoc 写明 `mallocSize >= 15,099,495`。
- **分类器**：新增内部 `VisualJsCalibration`（`VisualJsFailureClassification.kt:87-100`）；比值主证据的 `MEMORY_LIMIT` 带 `PENDING_CALIBRATION`，引擎原文直证不带（`:166-207`）；90% 阈值 `NEAR_LIMIT_PERCENT` 与 `reason` 映射未动。
- **JVM 矩阵**：`VisualJsFailureClassificationTest` 15 例（`grep -c '@Test'`），新增 E-02 数值形状 `177216/16777216 → UNATTRIBUTED`、90% 边界带标记、直接前缀不带标记、Java null/`Invalid evaluation handle` 近上限带标记等。**T2 未运行 Gradle/JVM/设备；记录员未运行。** 15 例的"绿"没有本批执行证据（分类测试只在代码层改），不能写成已执行。
- **设备（E-02）**：`OK (20 tests)`、`Time: 8.275`、failures/skipped 0、7 类崩溃签名 0；P2 转绿，`mallocSize >= 15,099,495` 由断言约束，**精确值 UNVERIFIED**；只测 x86_64/API 34/4 KB；P2 用例总耗时 98 ms、协作预算 10 s（`step02-quickjs-oom.md:10`/`:57-61`/`:139-149`、`00-index.md:58-65`）。
- **未达/残余**：① 精确 `mallocSize` 未打印；② arm64/16 KB 未测（R2 §5 的两 ABI 要求未满足）；③ 慢设备/默认 100 ms 下可能先 `PHASE_TIMEOUT`；④ 栈根分配的真实 OOM 仍 `UNATTRIBUTED`；⑤ 丢失哨兵 + 堆 ≥90% 时故意的 `throw null` 会被归 `MEMORY_LIMIT`（at-threshold 用例明示）；⑥ 非严格 `InternalError: out of memory` 前缀落 `INVALID_PROGRAM`；⑦ 协调器无 `VisualExecutionEngine` 注入、`UNATTRIBUTED` 命中 0（准入清单 P-11/A-01）。**KD-70 保持 open**，是否关闭由主会话按 Reopen condition 裁定。

### 3.3 T3 台账收尾 —— **完成（文档）**

- 文件（4）：`docs/known-defects.md`（M）、`docs/research/2026-10-26-batch-23-record.md`（M）、`docs/research/evidence/2026-10-26-batch-23/00-index.md`（M）、`docs/research/2026-10-26-dependency-governance.md`（M）。
- **KD-74 关闭**：标题改为 `## KD-74 (closed 2026-10-26（批次 24 T3）…)`（`docs/known-defects.md:2768`），新增批次 24 T3 Progress（`:2778`）与证据路径；批次 23 记录 §4 末补记 "KD-74 closed"（`docs/research/2026-10-26-batch-23-record.md:202`）；批次 23 索引 §4/§5/§7 同步（`evidence/2026-10-26-batch-23/00-index.md:76/:89/:115/:119`）。三条件逐项成立：记录回链、15 个已跟踪 `.md` 证据（E-00…E-09）、主会话 `llvm-readobj` 等价裁定（批次 23 记录 `:415`）。**关闭仅指证据可复核，不声称设备门全绿**：T1 在关闭文本中仍写 1/5 红、KD-70 P2 仍红、arm64 16 KB 未执行——这些是批次 23 时点状态，已被批次 24 E-01/E-02 部分取代（见 §3.7），关闭本身不受影响。
- **KD-72 保持 open**：新增批次 24 T3 只读核对 Progress（`known-defects.md:2746`），确认 13 条 git 可见未跟踪锁条目、`git ls-files` = 0、`benchmark` 缺锁、默认 `LockMode.DEFAULT`、13/14 个锁不得原样提交、等干净树重生成；剩余四条件保留（`:2748`）。同时更正 `docs/research/2026-10-26-dependency-governance.md:11` 的旧"无任何锁文件 + LENIENT"。
- **KD-66/69/70 保持 open**：KD-66 以 E-01b/E-01e（批次 23 5 例 4 绿 1 红）为批次 23 时点、批次 24 E-01/E-01b 6/6 取代设备状态，剩余关闭门仅 hard+500 ms（`:2596-2607`）；KD-69 以 E-04（internal PASS）与 E-03（release 冷启动 ×2）回填，剩余生产签名/中文 OCR/`usage.txt` 穷举（`:2676`）；KD-70 以 E-02（20/20、P2 绿）回填，剩余 P2 精确值/arm64 16 KB/关闭裁定（`:2696`/`:2705`）。
- **文本残留**：KD-72 `:2746` 与 `dependency-governance.md:11` 写"盘上实为 14 个 `gradle.lockfile`"，实测是 **13 个 `gradle.lockfile` + 1 个 `settings-gradle.lockfile`**；批次 23 索引 `:67` 仍把 KD-74 条目标在 `:2744`/`:2750`（实际 `:2768`/`:2778`）。KD-74 关闭 Progress `:2778` 仍写"T1 仍 1/5 红、KD-70 P2 仍红"，未带批次 23 时点标签（§5 #3/#4/#5）。

### 3.4 T4 4E.2 准入检查清单 —— **完成（文档）**

- 文件（1）：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（新，336 行）。
- **结构**：10 组 **63 项**可执行检查（P 前提 11、S 场景/编译器 8、M 度量 4、D 持久化 5、E 导出 5、A 双后端/广告 10、Q 质量 6、V 验证 4、R 发布 5、X 架构/横切 5），每项含现状/缺口/证据位置/验收用例/责任轨，并与契约 §4/§5、4E 计划、R3、KD-66/70/76 交叉引用。
- **对 R3 的修正**：R3 把隔离广告门定义为 6/6；KD-76 指出 6/6 不含 warm-up/ready，本表升级为"6/6 + KD-76 ③④⑤"（P-07），并新增 P-08 启动面审计、P-09 队列/背压、P-10 死亡未确认 fallback、P-11 `[UNATTRIBUTED]` 前缀消费、A-10 每操作 6 次派发/预算 ledger（这些是复核修复环的回填）。
- **结论**：**MVA 不成立**，`availableMethods` 不得加入 JS；P-01（6/6）与 P-02（x86_64/4 KB P2 绿）已由 E-01/E-01b/E-02 达到，但 P-02 的 arm64/16 KB、P-07 warm-up/ready、P-08…P-11 与 S/M/D/E/A/Q/V/R/X 各组未落。
- **口径差**：T4 工单摘要称"58 项"；记录时点实测 **63 行**（多出的行来自复核回填）。摘要称"与 4E 计划批次二至六及 WG-01～10 交叉引用"，实际文档引用 4E 计划 §2–§7（批次一至五 + 退出门，无"批次六"）与 WG-01～08/10，未引用 WG-09（§5 #6）。

### 3.5 T5 条件轨 —— **完成（登记）**

- 文件（2）：`docs/known-defects.md`（M，KD-76）、`docs/current/visual-generation-contract.md`（M，§4:72 回链）。
- **KD-76 登记**（`known-defects.md:2805-2821`）：6/6 隔离门不含 warm-up/ready；冷启动首个 `execute` 仍可能被 1500 ms watchdog 误杀（E-01d）。`Fix direction` 给出两种实现形态（服务内 warm-up 或首请求装载单独计时），并明确 warm 服务上的真实挂死仍须 1500 ms 硬杀。**该条消灭的失败**：在隔离门沉降态 6/6 变绿后放行广告，而真实用户冷启动后的第一个请求仍可能被误杀；不新增运行时机制，只补广告门判据。
- **R4 九项核对**：G1/G2、AWAITING UI、5b-1/KD-68、AUD-08、6B-3、5b-2、阶段 6、5c 均已有追踪载体，未重复建条目；T5 未做 UI/生产代码（热区全部脏）。唯一可动的 `tools/split_golden` 不在 T5 指派内，仅登记。
- **热区核对**：T5 未触碰 feature/*、core/ui、Root、SecondaryScreens、settings.gradle.kts、core/export、.gitignore；其两处落点 `known-defects.md`、`visual-generation-contract.md` 均在允许落点内。**归因更正**：契约在 T5 开工时已由 T1 改脏（基线 19:34 时仍干净），本批改动来自 T1（15 s/10 s/2 s/19.5 s）与 T5（KD-76 回链），不是 T3/T4；`known-defects.md` 同时含 T1/T2/T3/T5 的改动，提交时整文件纳入并注明。
- **KD-76 措辞残留**：Impact 仍写"该失败发生在每个新装/重启用户的首个请求"（`:2813`），证据只有一台冷启动模拟器（负载 5.25）5/5 红与沉降后 1/5 红；"仍可能"有证据，"每个用户"是外推（§5 #8）。

### 3.6 集成

- **无生产接线**：`VisualJsClientEngine` 仍只在仪器化用例构造；`core/data` 协调器不引用 `VisualExecutionEngine`；服务内内容度量仍是 `UnwiredVisualContentMeasurer`（`VisualJsService.kt:310`），只有纯几何程序可通；JS 仍 `UNAVAILABLE`、不广告。
- **模块边界**：本批未新增 Gradle 模块、未改 `settings.gradle.kts`（该文件仍是他线 M，含 `:core:markdown` include）；Service/AIDL/client 仍在 app；`core/data`/`core/domain` 不引用 AIDL。
- **构建/供应链**：未改 AAR 钉版、未改 stdlib 约束、未生成/提交锁文件；零 DB/请求 schema 变更；零算法版本 bump（台账 §3.34）。

### 3.7 设备轨（记录员未复跑；以下均来自 E-file 与原始日志，记录员实读判定行）

> 环境：`emulator-5554`（`test_device`，API 34，x86_64，4 KB）与 `emulator-5556`（`ps16k_x86_64`，API 36，x86_64，**16384**）。协议 = `adb install -r -t` + `am instrument -w -r -e class ...`，未跑 `connectedAndroidTest`。产物身份见 `evidence/2026-10-26-batch-24/00-index.md` §2（5 个 APK + release mapping sha256/字节数；设备侧 `pm path` + `sha256sum` 复核）。
>
> **纪律偏离（如实披露）**：设备轨为完成 E-03 实际运行了 Gradle `:app:validateReleaseSigning`（exit=1，缺四个 `RELEASE_*`）与 `:app:assembleStrictOfflineRelease`（BUILD SUCCESSFUL 7m 8s），与共享纪律"不运行 Gradle（门禁由脚本串行执行）"字面冲突；该轨在 `00-index.md:9` 与 `step03-r8-release-smoke.md:48` 披露，属为取得 release 设备证据所必需的偏离，是否接受由主会话裁定。

| 项 | E-ID | 判定 | 关键事实（记录员实读） |
|---|---|---|---|
| 环境/产物身份 | E-00 | PASS（信息性） | 两台设备；5 个 APK + mapping sha256/字节数；release 证书 `CN=Batch24 Local Smoke`，非生产证书（`step00-environment.md:78-86`、判定 `:112-114`） |
| 隔离门首轮 | E-01 | PASS（6/6） | debug `53c3a453…6eba`，`OK (6 tests)`、`Time: 23.945`；abandon 转绿、7.6 s 冷启动用例绿、`death was not confirmed` 0（`step01-visualjs-isolation.md:124`） |
| 隔离门复跑 | E-01b | PASS（6/6） | 21:37 他线重建后的当前安装件 `782cebd6…73c6`，`OK (6 tests)`、`Time: 22.208`；测试 APK `343b637a…08c0e`（`step01b-visualjs-isolation-rerun.md:102`） |
| QuickJS 全量 | E-02 | PASS（20/20） | P2 绿；`OK (20 tests)`、`Time: 8.275`；x86_64/API 34/4 KB；精确 `mallocSize` 未打印（`step02-quickjs-oom.md:139-149`） |
| release R8 冷启动 | E-03 | PASS（2×ok/COLD） | 2405 / 1245 ms；PID 存活；9 类崩溃签名 0；**一次性本地 key，生产签名 UNVERIFIED**；中文 OCR 未跑（`step03-r8-release-smoke.md:172-182`、签名限制 `:40-50`） |
| 16KB 运行时 | E-04 | PASS（3/3） | `ps16k_x86_64`、`PAGE_SIZE=16384`；QuickJS/LiteRT 原生库真实装载（`step04-page-size-16k.md:105-114`） |
| 备份 | E-05 | PASS（34/34） | 无跳过、无崩溃签名（`step05-backup-restore.md:87-89`） |
| 迁移 | E-06 | PASS（抽验） | 演练 5/6（1 个设计内 `assume` 跳过，需 `smb_d1_kill_stage=true`）；全量矩阵 15/15，含 1→当前逐版本，`Time: 1,431.852`（`step06-migration.md:136-142`） |

- **隔离门结论**：T1 修复在两代 debug APK 上均成立，批次 23 的 abandon 红未复现；但 **KD-66 的 hard+500 ms 关闭门未覆盖**，6/6 不得作为 JS 广告依据（KD-76），JS 继续不广告。
- **KD-70 结论**：P2 新夹具在 x86_64/4 KB 上成立；arm64/16 KB、精确值、慢设备时限是缺口；KD-70 不因 E-02 自动关闭。
- **KD-69 结论**：release 冷启动不回退已在一次性本地 key 包上验证；生产签名、中文 OCR、`usage.txt` 全量穷举、`ExportPdfWorker` 单独触发仍缺，不能宣告 KD-69 完整 Reopen condition 达成。
- **16KB 结论**：x86_64 16 KB 运行时成立；**arm64 16 KB 运行时仍未测**，KD-74 的该缺口不因 E-04 关闭。
- **备份/迁移**：抽验范围内通过；`killProcess` 交接的显式崩溃用例未跑，不是该项的关闭证据。

### 3.8 证据轨 —— **按 R4 落盘并独立复核；本记录补回链**

- **落盘**：`docs/research/evidence/2026-10-26-batch-24/`，9 个 `.md`（`00-index.md` + `step00`…`step06`），1,269 行、内容 67,839 B、84 KiB/88 KiB（不含/含 `.mimosa`），未超 R4 上限（§1.3）。
- **独立复核**：证据落盘员重算 5 个 APK + mapping sha256/字节数、重扫 8 类崩溃签名、复核两台设备指纹与 `PAGE_SIZE`、重跑体量与脱敏扫描（`00-index.md` §6：secrets/credential/emails 0 命中；home-path 1 命中是扫描命令自身的正则文本）。**记录员本轮只读核对体量与判定行，未复算哈希、未重扫。**
- **回链**：`00-index.md` §4 已把 KD-74 关闭回链到批次 23 记录 `:202`/`:415` 与 KD-74 条目；§7 的"批次 24 记录 §4 回链"与 §9 的记录待办在本记录 §4 完成后成立；`00-index.md` 已按索引性质更新（不是 step 历史文件）。本记录 §4 的每个设备数字均给出 `E-ID + evidence/…:line`。
- **未落盘/边界**：`build/device-track-batch24/` 只作原始全量，不是关闭证据；`.mimosa/` 钩子状态被忽略、不是证据；release mapping 本体不提交；arm64 16 KB、生产签名、精确 `mallocSize`、KD-66 hard+500 ms、中文 OCR 均未落盘。

## 4. 门禁与设备证据

> **记录员未运行 Gradle/设备**。以下"工单给定"行来自任务材料，不是本轮实证；设备数字来自 E-file/原始日志，记录员只实读。

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 1 轮） | **exit=0**（工单给定） | 无本批仓内日志；记录员未复跑。 |
| 修复环复跑 | **exit=0**（工单给定） | 同上。 |
| 强门（第 1 轮） | **exit=0**（工单给定） | 同上。 |
| 设备隔离门 | **PASS 6/6 ×2（E-01/E-01b）** | `evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:124`、`step01b-visualjs-isolation-rerun.md:102`；原始日志 `build/device-track-batch24/step01-isolation.log`、`step01b-isolation-rerun.log`（均 `OK (6 tests)`）。 |
| 设备 KD-70 | **PASS 20/20（E-02）** | `evidence/2026-10-26-batch-24/step02-quickjs-oom.md:139-149`；P2 绿、精确值 UNVERIFIED。 |
| 设备 release R8 | **PASS 2×ok/COLD（E-03）** | `evidence/2026-10-26-batch-24/step03-r8-release-smoke.md:172-182`；生产签名 UNVERIFIED。 |
| 设备 16KB | **PASS 3/3（E-04）** | `evidence/2026-10-26-batch-24/step04-page-size-16k.md:105-114`；arm64 16 KB 未测。 |
| 设备备份 | **PASS 34/34（E-05）** | `evidence/2026-10-26-batch-24/step05-backup-restore.md:87-89`。 |
| 设备迁移 | **PASS 5/6 + 设计跳过；矩阵 15/15（E-06）** | `evidence/2026-10-26-batch-24/step06-migration.md:136-142`。 |
| 生产 release 签名 | **UNVERIFIED（exit=1）** | `:app:validateReleaseSigning` 失败，缺 `RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`/`RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`（`build/device-track-batch24/validate-release-signing.log` 尾部）；未写入任何凭据。 |
| 真实 Provider | **UNVERIFIED** | 无凭据、未调用；模型质量/费用未验；JS 不广告。 |

**记录员对门禁的保留**：单测门/修复环/强门的 `exit=0` 没有本批日志落在仓内，记录员未运行 Gradle；设备数字均有 E-file，但均来自设备轨/证据落盘轨，记录员只实读判定行与原始日志摘要、未复跑。故本记录不把"门禁全绿/设备全绿"写成可审计结论。

## 5. 复核发现与处置

> **口径说明**：任务材料包含两组复核：①"修复环第 1 轮"称 **11 条**全部处置，其中 #3/#4 是代码修复（`VisualJsClientEngine.kt`）；但随附 11 行表格的 #3/#4 分别是 KD-74/KD-69 与锁计数，均非代码修复，**口径无法与表格对齐**。②"复核发现与处置"列出 **25 条**，本节按 25 条逐条核对当前树；11 条中的文档项已被 25 条覆盖（KD-70/66/74/69、锁计数、行号漂移、准入清单、R2 数字、KD-76 措辞、Gradle 偏离、T5 归因、迟到 death 措辞）。
> `已修` = 当前树文本/代码已按发现改；`已登记` = 已写入 KD/研究/准入清单但事项未闭合；`残留` = 当前树仍未改且未关闭；`已披露` = 属于过程事实，记录如实说明、不做代码修复。

| # | 位置 | 发现 | 级别 | 本批处置（当前树核对） |
|---|---|---|---|---|
| 1 | `docs/known-defects.md:2688/:2696/:2705`；`evidence/2026-10-26-batch-24/step02-quickjs-oom.md:10/:58-61`；`00-index.md:45` | KD-70 仍把批次 23 的 19/20、P2 红、`mallocSize=177216` 当作最新状态；批次 24 E-02 已 20/20、P2 绿 | 中 | **已修**：KD-70 批次 23 段标注"批次 23 E-02/时点"，新增批次 24 设备轨 Progress（`:2696`/`:2705`）；裸 E-02 改为"批次 23 E-02/批次 24 E-02"。精确 `mallocSize` 未打印仍是事实缺口，KD 保持 open。 |
| 2 | `docs/known-defects.md:2583/:2596/:2598/:2607`；`docs/current/visual-generation-contract.md:72` | KD-66 同节保留旧 5 s/1 s/8.5 s 与新 15 s/10 s/2 s/19.5 s；契约仍写设备复跑 UNVERIFIED | 中 | **已修**：旧值标为"批次 22 时点、已被批次 24 Progress 取代"（`:2583`）；批次 24 设备轨 Progress（`:2607`）；契约 §4:72 写 6/6 与"旧 UNVERIFIED 作废"。 |
| 3 | `docs/known-defects.md:2778`（KD-74 Progress）、`:2676`（KD-69） | KD-74 关闭 Progress 仍写"T1 仍 1/5 红"，未带批次 23 限定；KD-69 未提 E-03 | 低 | **部分修**：KD-69 已写 E-03 release 冷启动 ×2 与生产签名/OCR 限制（`:2676`）。KD-74 `:2778` 仍写 T1 1/5 红、KD-70 P2 红，未带"批次 23 时点"标签——**残留（低）**；不影响 KD-74 关闭（R4 红绿中性）。 |
| 4 | `docs/known-defects.md:2746`；`docs/research/2026-10-26-dependency-governance.md:11` | 文本称"盘上实为 14 个 `gradle.lockfile`"；实测 13 个 `gradle.lockfile` + 1 个 `settings-gradle.lockfile` = 14 个锁文件 | 低 | **残留**：实测 `find . -name 'gradle.lockfile'` = 13，`settings-gradle.lockfile` 267 B 在盘；两处文本仍写"14 个 gradle.lockfile"。13 条 git 可见、0 已跟踪、`:benchmark` 缺锁、默认 DEFAULT、不得原样提交的结论正确。 |
| 5 | `evidence/2026-10-26-batch-23/00-index.md:67`；`docs/known-defects.md:2768/:2778` | T1/T2/T5 插入后行号漂移：索引仍把 KD-74 条目标在 `:2744`/`:2750`，实际标题 `:2768`、Progress `:2778` | 低 | **残留**：批次 23 索引未改（证据历史文件，记录员不改）；本记录给出正确行号。KD-74 关闭本身成立。 |
| 6 | `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:13/:98/:100/:122-123/:127` | 检查表 P-01/P-02/P-03/P-04 仍写批次 23 状态；摘要称 58 项/交叉引用批次二至六 + WG-01～10 | 低 | **部分修**：当前表已按 E-01/E-01b/E-02 回填（P-01 6/6、P-02 20/20、P-03 20/20、P-04 已更新），实测 63 行（不是 58）；"MVA 不成立"仍成立。摘要的 WG-09/"批次六"交叉引用仍不准确——**残留（低）**。 |
| 7 | `docs/research/2026-10-26-quickjs-memory-accounting.md:296`；`VisualJsFailureClassification.kt:93` | R2 仍说 KDoc 的 90% 数字是 14,400,000 且"待 T2 校正"（实际 KDoc 已 15,099,495）；分类器 KDoc 仍写"唯一设备数据是 E-02 的 177216"（E-02 已让比值分支设备转绿） | 低 | **残留**：两处文本均未改；记录员不改代码，登记为文档/KDoc 残留。判据/阈值本身正确。 |
| 8 | `docs/known-defects.md:2813` | KD-76 的 Impact 写"该失败发生在每个新装/重启用户的首个请求"，证据只有一台冷启动模拟器 5/5 红与沉降后 1/5 红 | 低 | **残留**："仍可能"有证据，"每个用户"是外推；建议收窄措辞，本批未改。 |
| 9 | `build/device-track-batch24/validate-release-signing.log`、`build-release.log`、`00-index.md:9` | 设备轨实际运行了 Gradle（validate exit=1、assemble release BUILD SUCCESSFUL 7m 8s），与"不运行 Gradle"的共享纪律字面不一致 | 低 | **已披露**：`00-index.md:9` 与 `step03-r8-release-smoke.md:48` 均写明为取得 E-03 所必需；是否接受由主会话裁定，不隐瞒。 |
| 10 | T5 工单摘要 vs `docs/current/visual-generation-contract.md:72`；`known-defects.md` | T5 称两文件"开工即被同批 T3/T4 改脏"；实际契约在 T5 开工时已由 T1 改脏（基线 19:34 仍干净），改动来自 T1（15/10/2/19.5 s）与 T5（KD-76 回链）；known-defects 含 T1/T2/T3/T5 | 低 | **已纠正（记录）**：§3.5/§7 按实际归属写；提交时 `visual-generation-contract.md` 整文件纳入并注明 T1+T5，`known-defects.md` 整文件纳入并注明 T1/T2/T3/T5。 |
| 11 | `VisualJsClientEngine.kt:594-605` | 日志写"迟到 death 被丢弃且永不据此重建"；超时与 `unlinkDeath` 之间到达的 death 仍走 `markDead`/`handleDeath`，只是 `unavailable=true` 让重建早退 | 低 | **残留**：行为保证（不重建）成立，措辞过强；不改代码。 |
| 12 | `VisualJsClientEngine.kt:557/:794-799/:837` 起 | 仍有 death-before-registration 竞态：`onServiceConnected` 检查后无条件置 `CONNECTED`，可能覆盖已写入的 DEAD | 中 | **已修（代码）**：`Link.markConnected` 与 `markDead` 共用 `stateLock`；`connectLocked` 用 `isConnected()` 在锁内复查 `dead`；已死 link 不会被当连接返回。**无确定性交错测试**（KD-66 `:2609` 自认）。 |
| 13 | `VisualJsClientEngine.kt:486-503/:167/:920-936`；契约 `:72` | 加载态没有无限等待，但 `ensureConnected` 在宿主 `withTimeout` 之外：首连 15 s、重建 10 s、宿主阶段 2.5 s、死亡确认 2 s，最坏约 19.5 s；首连超时永久关闭，无加载/重试态；`executeMutex` 排队无上界 | 中 | **部分修**：`close()` 已用 `markDead` 完成等待（§5 #15）；LOADING/重试与 `executeMutex` 上界仍未做，契约 §4:72 已如实写 19.5 s 与无上界排队——**已登记，未消除**。 |
| 14 | `VisualJsClientEngine.kt:609-621`；R1 `:232-248` | KDoc 称 death 事实与 `isBinderAlive` 是"两条独立事实互为兜底"；研究指出 `isBinderAlive` 只读 `mAlive`，若通知丢失且无失败事务，两者可同时为 true；该分支无测试 | 低 | **残留（措辞）+ 已登记**：KDoc 已含"未注册 death 且无失败事务则不清零"的 caveat，但"互为兜底"仍强；6/6 设备绿只证明 death 路径（logcat `death was not confirmed`=0）。 |
| 15 | `VisualJsClientEngine.kt:224-244` | `close()` 仍先 `unlinkDeath` 再 `death.await()`（诊断用），与 abandon 修复方向相反 | 低 | **已修**：`close()` 现在对当前 link 调 `markDead("the client was closed", unexpected=false)`，完成 `connected`/`death`、终结 activeCall、解注册/解绑，首连/重建立即返回 null。 |
| 16 | `docs/known-defects.md:2705`；`step02-quickjs-oom.md:18/:148`；准入清单 `:122` | P2 证据口径窄于 R2 §5：只测 x86_64/4 KB，arm64/16 KB 未测；P2 98 ms、协作预算 10 s；清单只写"P2 设备绿" | 中 | **已登记**：KD-70 `:2705` 与准入清单 P-02 均写两 ABI/时限/精确值缺口；**未消除**。 |
| 17 | `VisualJsFailureClassification.kt:166-207`；KD-70 `:2707`；准入清单 P-11 `:131` | 新判据不改 `reason`/阈值，但三类边界仍在：栈根分配仍 `UNATTRIBUTED`；丢失哨兵 + 堆 ≥90% 时故意 `throw null` 也归 `MEMORY_LIMIT`；非严格 OOM 前缀落 `INVALID_PROGRAM`；协调器无引擎注入、`UNATTRIBUTED` 命中 0 | 中 | **已登记**：KD-70 `:2707` 与准入清单 P-11/A-01 写明；**未消除**。 |
| 18 | `VisualJsIsolationInstrumentedTest.kt`、`step01*`/`step02*`、`00-index.md` | 设备证据与台账/测试 KDoc 状态矛盾（KD/契约写 UNVERIFIED，E-01/E-02 已绿） | 中 | **已修**：测试 KDoc（`:44-52`）已写 6/6；KD-66/70、契约 :72、准入清单均已回填；E-01/E-01b/E-02 raw log 与 APK dex 中的新串可复核。 |
| 19 | `00-index.md:67`、`known-defects.md:2768/:2778`、批次 23 记录 `:202/:415` | KD-74 三条件逐项成立：记录回链、15 个已跟踪 `.md`、主会话等价裁定 | 低 | **已核**：KD-74 关闭成立；`git ls-files docs/research/evidence/` = 15、批次 23 目录 15 个 `.md`、记录 `:202`/`:415` 均在。索引行号漂移见 #5。 |
| 20 | 准入清单（复核前） | 清单漏了契约 §4:62/:72 的隔离启动面审计（Application 早退、构造/字段/companion、ContentProvider/Startup、无文件/网络绑定） | 高 | **已登记**：新增 P-08（`:128`），写明 guard 位置、未 dumpsys、无能力负向证据；**未实现**。 |
| 21 | 准入清单（复核前）；`VisualJsClientEngine.kt:167`；`VisualJsService.kt:186-210` | 清单漏了契约 §4:64 的队列/背压与消费者校验（1 在飞 + 2 待绘、latest-wins、跨 program 按序、BUSY 保留原帧、调用身份/描述符方向长度） | 中 | **已登记**：新增 P-09（`:129`），写明客户端只串行、无 latest-wins/待绘队列；**未实现**。 |
| 22 | 准入清单 `:246/:121/:124`；测试类 6 个 `@Test` | 清单要求 P-01/P-04 覆盖"确认/未确认"两条路径，但 6 例无死亡未确认用例；代码只置 `unavailable=true` 并写日志，无 fallback UI | 中 | **已登记**：新增 P-10（`:130`），写明缺负向设备用例与 fallback；**未实现**。 |
| 23 | 准入清单；契约 `:86/:155/:202` | 清单漏了每逻辑操作 6 次 Provider 派发 ledger/预算（累计源码 128 KiB、新源码布局 ≤6、JS 墙钟 ≤10 s、单份 payload ≤512 KiB） | 中 | **已登记**：新增 A-10（`:190`），写明当前无"6 次/128 KiB/6 次布局/10 s/512 KiB"可执行门；**未实现**。 |
| 24 | 准入清单；契约 `:58/:186/:202`；`QuickJsVisualExecutionEngine.kt:813` | 清单漏了 input/metrics 深冻结、旧请求 codec/逻辑操作指纹兼容、API 35+ 16 KB 明列口径；引擎只 `Object.freeze(Math)` | 低 | **部分**：R-03/R-05 覆盖 arm64 16 KB/AAR/ELF；**深冻结与旧请求 codec/指纹兼容仍未登记为门**——残留（低）。 |
| 25 | 本批文件清单 vs 热区清单；`git status --short` | T5 未触碰热区 | 低 | **已核**：T5 文件为 `known-defects.md` + `visual-generation-contract.md`，均非 feature/core/ui/Root/SecondaryScreens/settings/core/export/.gitignore；工作树中的热区 M 为他线既有。 |

**记录员追加核对（不在原清单，低）**：

1. `REFACTOR-MASTER-LINE.md` 与 `algorithm-version-ledger.md` 在本批开工时干净、无批次 24 文本；记录员按工单"同步"要求补了顶部/4E/补修行与 §3.34 零 bump。`docs/agent-first-refactor-decisions-2026-09-23.md` 本批**不涉**（无新裁定落账），未改。
2. 设备轨的 Gradle 偏离（#9）与 T4 的 58→63 项差异（#6）均属过程事实，记录如前。
3. 本记录 §4 的 device 行均按 R4 §7.2 写成 `E-ID + evidence/…:line`；`build/` 只作 raw-log 提及，不当关闭证据。

## 6. 遗留 / UNVERIFIED / 待裁

**工程门**

1. **门禁 `exit=0` 无仓内日志**（单测门/修复环/强门均为工单给定）；记录员未运行 Gradle。提交前建议主会话把命令与 tail 落盘到 `build/`（KD-74 不以门禁日志为判据）。
2. **KD-66 关闭门未达**：现有 watchdog 用例以 ≤6 s 判死，弱于 hard+500 ms；6/6 设备绿不等于 KD-66 关闭。
3. **KD-76 warm-up/ready 未落**：冷启动首个 `execute` 仍可能被 1500 ms watchdog 误杀；6/6 不得作为 JS 广告依据。
4. **AIDL 门**：本批未跑 Gradle AIDL 任务；`aidl.exe` 双输入禁用规则继续有效。

**T1**

5. **LOADING/重试未实现**：连接超时仍一次失败即永久 `unavailable`；`executeMutex` 排队无上界；最坏等待约 19.5 s。
6. **死亡未确认负向用例缺失**：6 例只覆盖确认路径；`isBinderAlive` 兜底分支无测试。
7. **warm-up/ready 与首连预算分离**：7.6 s 用例只证明首连预算，不证明 1500 ms 误杀消失。

**T2 / KD-70**

8. **精确 `mallocSize` 未打印**：只能确证 ≥15,099,495，目标 ≥99% 未覆盖。
9. **arm64/16 KB 未测**：只测 x86_64/API 34/4 KB；慢设备/默认 100 ms 可能先 `PHASE_TIMEOUT`。
10. **分类边界**：栈根分配、丢失哨兵+高堆的 `throw null`、非严格 OOM 前缀；协调器无引擎注入、`[UNATTRIBUTED]` 命中 0。
11. **R2/分类器 KDoc 时点残留**：14,400,000 与"唯一设备数据 177216"未改（§5 #7）。

**T3 / 台账**

12. **KD-72 锁口径文本错误**：13 个 `gradle.lockfile` + 1 个 `settings-gradle.lockfile` 被写成"14 个 gradle.lockfile"；锁未重生成、负控未做、`benchmark` 缺锁。
13. **批次 23 索引行号漂移**：KD-74 仍标 `:2744`/`:2750`，实际 `:2768`/`:2778`。
14. **KD-74 关闭文本仍含批次 23 设备红**：未带时点标签；不影响关闭（R4 红绿中性）。
15. **KD-75 仍 open、零代码**：PDF 无 `/AIGC`；`MODEL_DOCUMENT_PARSE` 文本判据未接；Android PDFium 接受度（U3）未验。

**T4 / 准入**

16. **MVA 不成立**：SceneV1 编译器/SDK、真实度量、painter/PDF、持久化、导出、双后端、质量门、发布矩阵均未落；JS 不得加入 `availableMethods`。
17. **P-07/P-08/P-09/P-10/P-11/A-10 未实现**：warm-up/ready、启动面 dumpsys 审计、队列/背压、死亡未确认 fallback、`[UNATTRIBUTED]` 消费、6 次派发 ledger 均只有门/文本。
18. **T4 口径差**：摘要 58 项 vs 实测 63 项；WG-09/"批次六"交叉引用不准确。

**设备 / 证据**

19. **生产 release 签名 UNVERIFIED**：一次性本地 key；release 中文 OCR 未跑；`usage.txt` 未全量穷举；`ExportPdfWorker` 未单独触发。
20. **arm64 16 KB 运行时未测**；16KB 只证明 x86_64 装载。
21. **`smb_d1_kill_stage` 显式崩溃用例未跑**；迁移/备份只是抽验范围。
22. **debug APK 在批次中被他线重建**：E-01 与 E-01b 固定两代哈希；引用时以 sha256 为准。
23. **设备轨运行 Gradle**：与共享纪律字面冲突，已披露，待主会话裁定。

**热区 / 他线 / 待裁**

24. **G1/G2、AWAITING、5b-2、阶段 6、6B-3、5c、AUD-08** 均因热区未空出，本批不做；R4 已给出顺序与最小范围。
25. **R4 待裁**：MVA 质量门是否缩小、KD-66 hard+500 ms 是否作为广告硬门、4E.3 是否单独立项、架构归位时机。
26. **真实 Provider / Wave X** 不在本批；模型质量 UNVERIFIED。
27. **origin/main 未 fetch**：远端实时 hash 未核。
28. **Mimosa 无完整扫描结论**，不宣称项目安全。

## 7. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话在提交前复跑单测门/强门并留 `exit=0` 日志；**不要把 13 个未跟踪 `gradle.lockfile` 与 `settings-gradle.lockfile` 加入提交**；`core/export`、feature 四模块、core/ui、Root、SecondaryScreens、`settings.gradle.kts`、`.gitignore`、docs/current 其余 M 一律排除（热区）。本节按 ≤6 路径/块列出**建议纳入**的本批文件（新文件标"新"，M 标"改"）：

- **块 1（T1）**：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt`（M）、`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`（M）
- **块 2（T2 主代码）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt`（M）、`.../VisualJsFailureClassification.kt`（M）
- **块 3（T2 测试）**：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualJsFailureClassificationTest.kt`（M）、`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineInstrumentedTest.kt`（M）
- **块 4（研究 R1/R2/R3/R4）**：`docs/research/2026-10-26-binder-death-confirmation.md`（新）、`docs/research/2026-10-26-quickjs-memory-accounting.md`（新）、`docs/research/2026-10-26-4e2-entry-checklist.md`（新）、`docs/research/2026-10-26-ui-queue-sequencing.md`（新）
- **块 5（基线 + 准入清单）**：`docs/research/2026-10-26-batch-24-baseline.md`（新）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（新）
- **块 6（KD/契约）**：`docs/known-defects.md`（M；**开工干净，本批含 T1/T2/T3/T5 改动，整文件纳入并注明**）、`docs/current/visual-generation-contract.md`（M；**T5 开工时已由 T1 改脏，本批 T1+T5 改动，整文件纳入并注明**）
- **块 7（设备证据首）**：`docs/research/evidence/2026-10-26-batch-24/00-index.md`、`step00-environment.md`、`step01-visualjs-isolation.md`、`step01b-visualjs-isolation-rerun.md`、`step02-quickjs-oom.md`、`step03-r8-release-smoke.md`
- **块 8（设备证据尾）**：`step04-page-size-16k.md`、`step05-backup-restore.md`、`step06-migration.md`
- **块 9（主线/台账 + 本记录）**：`docs/REFACTOR-MASTER-LINE.md`（M；顶部 + 4E/补修，整文件纳入）、`docs/research/algorithm-version-ledger.md`（M；§3.34 零 bump）、`docs/research/2026-10-26-batch-24-record.md`（本文件）
- **块 10（T3 批次 23 文档同步；建议单独提交）**：`docs/research/2026-10-26-batch-23-record.md`（M）、`docs/research/evidence/2026-10-26-batch-23/00-index.md`（M）、`docs/research/2026-10-26-dependency-governance.md`（M）

**明确排除（他线在飞/不安全，一律不动）**：

- 13 个 `*/gradle.lockfile` 与 `settings-gradle.lockfile`（未跟踪，与 HEAD 不一致，KD-72 未关闭；等干净树重生成后再单独提交）。
- `core/export/**`（6 项）、`feature/{capture,review,tutor,library}/**`、`core/ui/**`、`app/src/main/kotlin/.../SmartMistakeBookRoot.kt`、`SecondaryScreens.kt`、`app` 其余开工即脏 M、`settings.gradle.kts`、`.gitignore`。
- `core/domain`、`core/data`、`core/database` 的他线 M（全路径见基线 §2.1/§2.2）；`feature/profile`。
- `docs/current` 其余 M、`docs/research` 他线未跟踪记录（`2026-10-26-library-layout-refinement.md`、`2026-10-26-ui-copy-refinement.md`、`2026-10-26-ui-state-polish.md`、`2026-10-26-detail-images-direct-display.md` 等）、`docs/superpowers/**`、`tools/kb*`、`knowledge-production/**`。
- `build/device-track-batch24/**`（原始全量，`.gitignore` 忽略）与 `build/agent-batch24/**`（他线产物，不入提交）。
- `gradle/libs.versions.toml`、`app/build.gradle.kts`（本批零 diff）。
- `docs/research/evidence/2026-10-26-batch-23/` 的其余文件（只纳入块 10 点名的 `00-index.md`；step 历史文件不改）。

> **T3 的批次 23 文档同步**：`docs/research/2026-10-26-batch-23-record.md`、`docs/research/evidence/2026-10-26-batch-23/00-index.md`、`docs/research/2026-10-26-dependency-governance.md` 也被 T3 改动，建议按**块 10（≤3 路径）**随 KD-74 关闭一起提交；若主会话要求最小提交，可只带 `known-defects.md` 与 `batch-24-record.md`，但这会让 KD-74 的记录回链在提交面不完整——记录员建议纳入。

**提交说明**：本批建议提交 10 块，每块 ≤6 路径；`docs/known-defects.md`、`docs/current/visual-generation-contract.md`、`docs/REFACTOR-MASTER-LINE.md`、`docs/research/algorithm-version-ledger.md` 均**整文件纳入并在提交说明中注明"整文件纳入，含既有/他线改动"**。提交/推送由主会话执行；非快进 merge，不 rebase。

## 8. 主会话实跑与收口状态（工单给定；待主会话追加提交/推送）

- **门禁（工单给定）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`。**仓内无本批门禁日志**；记录员未运行 Gradle。
- **设备（工单给定 + E-file）**：隔离 6/6 ×2、KD-70 20/20、release 冷启动 ×2、16KB 3/3、备份 34/34、迁移 5/6 + 15/15；证据按 R4 落盘 9 个 `.md`/84 KiB（内容）/1,269 行，脱敏 0 命中。
- **提交/推送**：截至本记录时点，**未提交、未推送**；建议清单与排除项见 §7。提交由主会话执行；非快进 merge，不 rebase。
- **本批不声称全绿**：KD-66 hard+500 ms 未达、KD-69 生产签名/中文 OCR 未验、KD-70 精确值/arm64 未验、KD-72 锁未入库、KD-73 无证明、KD-75 零代码、KD-76 warm-up/ready 未做、arm64 16 KB 未测、真实 Provider UNVERIFIED、JS 不广告。
- Mimosa 提交仍无完整扫描结论，不宣称项目安全。

## 附录：本记录员实际执行的只读核对（可复跑；未运行 Gradle/设备/任何 git 写命令）

```bash
# 计划/基线/四研究/证据文件读取（Read/sed/grep）
git rev-parse --short HEAD                 # 8bb79efd
git log --oneline -3                       # 8bb79efd / 5bc8bdf8 / f8a362ee
git status --porcelain | wc -l             # 691
git status --porcelain | grep -c '^ M'     # 213
git status --porcelain | grep -c '^??'     # 478
for d in feature/library feature/tutor feature/capture feature/review core/ui core/export \
         core/domain core/data core/database feature/profile; do
  printf "%-18s " "$d"; git status --porcelain -- "$d" | wc -l; done
#   → 43 / 59 / 16 / 12 / 35 / 6 / 5 / 15 / 9 / 4

# 版本串与协议
grep -n "STUDY_DATABASE_VERSION =" core/database/.../StudyDatabase.kt          # :134 = 69
grep -n "TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION =\|CURRENT_SCHEMA_VERSION =" \
  core/model/.../ModelTasks.kt                                                # :546-547 = 18
grep -n "PROTOCOL_VERSION" app/.../visualjs/VisualJsProtocol.kt               # :31 = 1

# 锁文件（只读）
find . -name 'gradle.lockfile' -not -path './.git/*' | wc -l                  # 13
ls -la settings-gradle.lockfile                                               # 267 B
git status --porcelain | grep -ci lockfile                                    # 13
git ls-files '*gradle.lockfile' | wc -l                                       # 0

# T1 代码锚点
grep -n "FIRST_CONNECT_TIMEOUT_MILLIS\|RECONNECT_TIMEOUT_MILLIS\|DEATH_CONFIRM_TIMEOUT_MILLIS\|DEATH_POLL_INTERVAL_MILLIS\|private suspend fun abandon\|awaitDeathConfirmation\|late death" \
  app/.../visualjs/VisualJsClientEngine.kt
#   → :580 / :589 / :599-600 / :622 / :920 / :926 / :933 / :936
grep -n "@Test" app/src/androidTest/.../visualjs/VisualJsIsolationInstrumentedTest.kt   # 6
grep -n "COLD_START_ATTACH_DELAY_MILLIS\|DelayedAttachContext" 同文件               # :385 / :397

# T2 代码锚点
grep -n "visualHeapProbeHead" core/data/src/androidTest/.../QuickJsVisualExecutionEngineInstrumentedTest.kt  # :767
grep -c "@Test" 同文件                                                         # 20
grep -c "@Test" core/data/src/test/.../VisualJsFailureClassificationTest.kt    # 15
grep -n "PENDING_CALIBRATION\|VisualJsCalibration" core/data/.../VisualJsFailureClassification.kt  # :87-100 / :166-207

# 证据体量与判定行（只读）
ls docs/research/evidence/2026-10-26-batch-24/*.md | wc -l                    # 9
cat docs/research/evidence/2026-10-26-batch-24/*.md | wc -lc                 # 1269 67839
du -ck docs/research/evidence/2026-10-26-batch-24/*.md | tail -1             # 84 total
du -sk --exclude=.mimosa docs/research/evidence/2026-10-26-batch-24           # 88
for f in docs/research/evidence/2026-10-26-batch-24/step0*.md; do grep -n "^## 7\. 判定" "$f"; done
#   → step00:112 / step01:122 / step01b:100 / step02:139 / step03:172 / step04:105 / step05:87 / step06:136

# 原始设备日志（只读）
grep -E "OK \(|Time:" build/device-track-batch24/step01-isolation.log        # OK (6 tests) / 23.945
grep -E "OK \(|Time:" build/device-track-batch24/step01b-isolation-rerun.log # OK (6 tests) / 22.208
grep -E "OK \(|Time:" build/device-track-batch24/step02-kd70.log             # OK (20 tests) / 8.275
tail -6 build/device-track-batch24/build-release.log                         # BUILD SUCCESSFUL in 7m 8s
sed -n '1,60p' build/device-track-batch24/validate-release-signing.log       # 缺 RELEASE_* 四变量，BUILD FAILED

# 台账/契约锚点
grep -n "^## KD-6[6-9]\|^## KD-7[0-9]" docs/known-defects.md
#   → KD-66:2573 / KD-67:2615 / KD-68:2642 / KD-69:2664 / KD-70:2682 / KD-71:2713 /
#     KD-72:2732 / KD-73:2756 / KD-74:2768 / KD-75:2782 / KD-76:2805
sed -n '72p' docs/current/visual-generation-contract.md                      # 批次 23/24 设备回填 + 19.5 s + KD-76
grep -cE '^\| (P|S|M|D|E|A|Q|V|R|X)-[0-9]{2} \|' \
  docs/research/2026-10-26-batch-24-4e2-admission-checklist.md               # 63
grep -n "盘上实为" docs/known-defects.md docs/research/2026-10-26-dependency-governance.md
sed -n '67p' docs/research/evidence/2026-10-26-batch-23/00-index.md          # 仍写 :2744/:2750
```

> 未执行：任何 `./gradlew`、`adb`、`am instrument`、`connectedAndroidTest`；任何 git 写命令。门禁/设备/研究结论均标注来源（工单材料/证据 E-file/只读日志/研究文档/原始 build 日志），不冒充本记录员实跑。证据链接检查与 `git diff --check` 在写完本记录后执行（结果见 §9）。

## 9. 本记录的验证结果（记录员实跑）

- **证据链接检查（R4 §7.5）**：在 `docs/research` 下对记录内所有 `evidence/<file>.md(:line)` 引用执行 `test -f` 与行号范围检查：**16 条引用全部存在、行号在范围内，0 失败**（脚本输出 `evidence refs: 16 / problems: none`）。
- **文档链接检查**：记录内 51 条 `docs/...md(:line)` 引用全部存在、行号在范围内，0 失败（脚本输出 `docs refs: 51 / problems: none`）。
- **补丁/空白检查**：`git diff --check` → **exit=0**（仅行尾 CRLF 提示，无空白错误）。
- **未执行**：Gradle、设备、git add/commit/push。
