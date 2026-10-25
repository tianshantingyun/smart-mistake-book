# 批次 21 记录（2026-10-25）：KD-64 修复 + G5-A 落地 + QuickJS 真接入 + 隐式标识/16KB 运行时研究 + 合规件

> 记录时点：2026-10-26（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对计划、基线、五研究文档、源码、测试结果与 `build/device-track-batch21/` 设备日志后，按工单给定材料（六轨交付、门禁、设备 evidence、修复环第 1 轮处置、复核发现）写入，并同步主线/决策台账/KD/算法台账/PRD-09 计划五处文档。
> **本记录员未运行 Gradle、未运行 adb/am instrument、未改任何生产代码、未执行任何 git 写操作、未提交/推送。** 文中门禁与设备数字来自工单转录（主会话/各轨/设备轨实跑后转录）；记录员自跑/实读的只有只读命令、`tools/ci/check_dependency_pins.py` 与 `pytest tools/figure_eval/test_figure_eval_gate.py`（见 §4 与附录）。凡无当前执行证据的验收一律标 **UNVERIFIED**。
> 计划：`.zcode/plans/plan-2026-10-25-batch-21-kd64-g5a-quickjs.md`（下称"计划"）。
> 基线：`docs/research/2026-10-25-batch-21-baseline.md`（下称"基线"）。
> **门禁状态（工单给定）**：单测门第 2 轮 `exit=0`；修复环复跑 `exit=0`；强门第 2 轮 `exit=0`。修复环第 1 轮处置完成（§4）。记录员未复跑 Gradle，且未在仓内找到本批门禁日志产物（§4），故不把"门禁全绿"写成记录员实证。
> **本记录不声称"批次全绿"**：T3 运行时门 12/13 绿、OOM 例红、issue #240 例必挂；T5 设备仪器化未跑；T2 生产未接线且候选死端；T7 应用内嵌入未做。逐项见 §3/§5/§6。

## 1. 范围与基线

### 1.1 计划范围

- **做**（计划 §2）：R1–R5 五条研究（QuickJS AAR 消费深挖/issue #240、TC260 XMP 隐式标识写入选型、ps16k 模拟器可行性、proso Elo 数学精读与参数映射、G5/G6 合规文案）；T1 KD-64 修复 + T10 设备复跑；T2 G5-A 双端口落地（隔离热区，生产调用点优先干净 seam，必要时登记一行接线遗留）；T3 QuickJS 真接入（AAR 钉版 + 真引擎 VisualExecutionEngine + 沙箱/硬终止/内存仪器化 + issue #240 用例 + 契约 RELRO 回写；隔离进程/AIDL 层留 4E.2 前置登记）；T4 PRD-09 JsPortStub 换真实引擎接线（仍不广告）；T5 G4 隐式标识（视 R2 选型成熟度落地或登记）；T6 ps16k 运行时装载验证（视 R3 可行性）；T7 G5/G6 合规件落盘；门禁/强门/设备/双复核/记录/提交/自动推送。
- **不做**（计划 §2）：G1/G2（feature/tutor + core/ui 热区）；AWAITING_VERIFICATION UI 态与放弃路径（feature/library）；5b/5c、阶段 6 UI、6B-3 注入、AUD-08（各自热区）；4E.2/4E.3 与 JS 广告门；`core/data` 他线 M（RoomMistakeDetailRepository/RoomTutorToolRunner）；真实 Provider 调用；Wave X 启用。

### 1.2 已裁边界（本批执行，不得改）

1. **G5 = 选项 A 先行**：本地双端口、零模型任务扩展、零提示词、零请求 schema；Elo 状态惰性重放派生（零新表）；选项 C 留 5B 终态后续批。proso 超参落码标 `PENDING_CALIBRATION`。
2. **QuickJS = 消费发布 AAR** `io.github.dokar3:quickjs-kt-android:1.0.15`：钉版本 + 构件 sha256 记录 + 不动态追新；自建 AAR 仅为运行时实测失败回退（判据：任一仪器化门红且归因 AAR 构件）。契约 `:22` RELRO 判据按官方豁免改写。
3. **32 位机型面收窄维持**：`abiFilters` 已含 arm64-v8a 与 x86_64，不回退。
4. **G7 未成年人叠加 = 按最严解释对齐**：随 G1/G2 落地，先登记台账。

### 1.3 基线摘要（引自基线文档 + 记录时点复测）

- **HEAD 漂移**：基线实测 `ce01508f`（= origin/main）；记录时点实测 **`2051748b` = origin/main**。两者之间只有 KB 他线一个 docs-only 提交（`ce01508f..2051748b` 共 4 文件 +849/-0：`docs/kb-batch21-2026-10-26.md`、`docs/kb-outstanding-work-2026-09-20.md`、`docs/research/2026-10-22-dense-union-prereg.md`、`docs/research/2026-10-26-dense-union-fusion-precedents.md`），**无代码/schema 变化**。本批代码基线仍按 `ce01508f` 引用，记录时点已按 `2051748b` 复核。
- **工作树**：基线 **160 M + 448 ??**；记录时点 **699 项 = 228 M + 471 ??**（`git status --porcelain=v1` 实数）。他线继续在飞，脏面只增不减。
- **schema**：`STUDY_DATABASE_VERSION = 69`（`StudyDatabase.kt:134`，`version =` `:233`）；请求 schema `CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`（`ModelTasks.kt:546-547`）。`core/database/schemas/`、`StudyDatabase.kt`、`ModelTasks.kt`、`app/src/main/AndroidManifest.xml` 记录时点均 git 干净；**70.json 未占用**。本批零 DB schema、零请求 schema。
- **热区比计划 §0 继续漂移**（基线 §2 实数；记录时点方向不变）：feature/tutor 19→28、feature/library 15→19、core/ui 9→10、feature/review 8→10、feature/capture 7→10、app 6→13；core/domain 基线 4 M，记录时点 **7 M**（其中本批 3：`AdaptiveQuestionSelector.kt`、`LearningCoreVersions.kt`、`NewIntroductionPolicy.kt`；他线 4：`BatchImportRepository.kt`、`MistakeDetailRepository.kt`、`SchedulingEvaluation.kt`、`TutorJudgedReview.kt`）。
- **计划 HOT_ZONES 未列的实脏区**：core/database 8 M（70.json 竞争风险载体）、core/export 5 M、core/data 测试面 8 M、docs/research 9 M、docs/superpowers 4 M、docs/history 3 M、docs 根散点 11 M、tools 3 M、根级 4 M、feature/profile 1 M（基线 §8.2）。
- **干净区前提的 T0 修正（重要）**：基线记录 `SecondaryScreens.kt` clean（`:111`）；开工/记录时点实测 **M（43+/43-，86 行改动，他线在飞）**，`SmartMistakeBookRoot.kt` 亦 M（他线动画引用未跟踪 `core/ui/ClaudeMotion.kt`）。T7 因此走 fallback：只落文案文件，不嵌入应用（§3.6）。
- **文档干净区矛盾（复核发现 #11/#24）**：计划 §0/§1 授权改 `docs/current/visual-generation-contract.md`（"文件已干净"），而共享纪律同时写"docs/current 全部"为热区；本批唯一热区重叠即该文件，改动仅 RELRO 判据一行（§3.3/§5 #24），无其他越权。
- **G5 seam 判定修正（基线 §4）**：计划 §5 风险"唯一 seam 是热区 VM"在装配期场景下**不成立**——队列装配点在干净 `StudyReviewPlannerService.currentKnowledgeReviewPlan`（core:data），装配期注入不需碰 feature/review；只有"会话中途换题"才触碰热区 VM。本批实现进一步暴露：装配器是 `internal`，feature/review 跨模块无法实例化，**一行接线并不存在**（§5 #3/#15）。
- **QuickJS 现状**：端口 + fake 在 core/domain（`VisualExecutionEngine.kt`、`FakeVisualExecutionEngine.kt`、`VisualExecutionEngineContractTest.kt`，基线均干净）；`libs.versions.toml` 基线无 quickjs 条目；契约 `:22` 原判"RELRO 结束未对齐=阻断门"待按官方豁免改写。
- **PRD-09 现状**：`tools/figure_eval/figure_eval_gate.py:288` 原 `JsPortStub`（STATUS=NOT_WIRED），`run_fake_evaluation(..., js_port)` 可注入；T4 换 JsPort 的接入点即此。
- **ps16k 环境**：本机已装 `system-images;android-36;google_apis_ps16k;x86_64`，AVD `ps16k_x86_64` 的 `config.ini` 实测 `image.sysdir.1=...google_apis_ps16k...`、`tag.id=page_size_16kb`、`tag.display=16 KB Page Size`（记录员只读复核，§2.2-R3）。

## 2. 主会话四项裁定与五研究线

### 2.1 主会话四项裁定（计划 §1 原文，2026-10-25 用户授权"一切由你来决定"；已落决策台账，不得改）

1. **G5 = 选项 A 先行**（`2026-10-25-g5-adaptive-selection-proposal.md` §3-A）：本地双端口（`AdaptiveCorrectnessPredictor` + `AdaptiveSelectionAssembler`），零模型任务扩展、零提示词、零请求 schema；**Elo 状态惰性重放派生（零新表）**——符合"固定账本重放逐位不变"纪律；选项 C（生成补池+确认卡+探索额度）留 5B 终态后续批，其判据（探索额度 k 等）届时先裁后写。proso 超参（0.8/0.05/猜测修正）按别家生产值落码并标 `PENDING_CALIBRATION`（真实效果 UNVERIFIED，Wave X 线）。
2. **T4 = 消费发布 AAR**：`io.github.dokar3:quickjs-kt-android:1.0.15` 钉版本 + 构件 sha256 记录 + 不动态追新。依据：R4 官方豁免判据下 AAR 四 ABI 16KB 合规；自建需 NDK r28c（本机无）；供应链面由钉版+构件校验+issue #240 仪器化用例+整轨摘除设计兜住。**自建 AAR 降级为运行时实测失败时的回退方案**（登记判据：任一仪器化门红且归因 AAR 构件）。契约 `visual-generation-contract.md:22` 的 RELRO 错误判据按官方豁免改写（本批做，文件已干净）。
3. **32 位机型面收窄 = 维持已落地呈报**（`abiFilters={arm64-v8a,x86_64}` 批次 20 已生效；armv7/x86 不可安装为既采决策，台账留痕，不回退）。
4. **G7 未成年人叠加 = 按最严解释对齐**：本产品面向中学生（大概率含未成年人），显式标识义务按最严口径执行（G1/G2 落地时一次到位）；隐式标识 TC260 规范映射（G4）本批研究+视选型落地；登记台账。

> 记录员按：裁定 1 的实现状态是"双端口代码 + 未接线"（§3.2），不是"G5 已可用"；裁定 2 的实现状态是"AAR 已接 + T3 运行时部分红"（§3.3），回退判据尚未触发也未裁决；裁定 4 的 G7 十项清单落决策台账，但应用内嵌入留 G1/G2 批（§3.6）。
> **编号提示**：计划 §1 中的"R4 官方豁免判据"指批次 20 的 16KB/RELRO 研究线（`docs/research/2026-10-25-16kb-arm64-api23-build.md`）；本批五研究线的 R4 是 proso Elo 数学（§2.2），两者不是同一编号体系。

### 2.2 五研究线（选型/提案/来源/待验证）

- **R1 QuickJS AAR 消费**（`docs/research/2026-10-25-quickjs-aar-consumption.md`）：
  - 钉 commit `a081755bcdb18ee6afa7ffee25b8fa976f06d31f` = v1.0.15 tip = main 最新提交（release v1.0.15 2026-09-03T12:39:32Z，其后 main 无提交）。
  - 构件：`quickjs-kt-android-1.0.15.aar` 1,828,904 B，sha256 `406c3efb80d5fa3495df4a7f2663be8cc30c974fe5c2368effa929de77cf0ed8`；POM sha256 `cc3ef4b3e8674771ef29d059c28935dd009e6dac7fb90765e497bd8414fe2bbc`。Maven Central 无 `.sha256` sidecar，sha256 为本地自算，官方 `.sha1` sidecar 双验一致（AAR `500962…`、POM `71a99d…`）。
  - Gradle 消费零 `packagingOptions`/`pickFirsts`：AAR 每 ABI 一枚 `libquickjs.so`、无 `libc++_shared`、全仓唯一 quickjs 生产者；`abiFilters` 把四 ABI 裁成 arm64-v8a + x86_64。
  - **issue #240**（evaluate 永不返回）在该钉版 **OPEN、零评论、无修复 PR、main 零后续提交**；job 自续挂死连 `close()` 都卡在 `jsMutex`，`evaluationTimeoutMillis` 不能兜底。仪器化用例必须 watchdog 形状（独立 daemon 线程 + join 超时 + 不调 `close()`），沙箱配方 = Kotlin `function()` 注入确定性 PRNG + 首个 evaluate 跑封闭脚本（`Date=undefined`、`Math.random` 替换、`Object.freeze(Math)`）。
  - 未验证：沙箱 bootstrap 的脚本运行时行为（R1 无引擎环境）；issue 状态为 2026-10-25 `gh` 单源实取，本记录员未重新拉 GitHub（§5 #1/#14）。
- **R2 隐式标识 XMP 选型**（`docs/research/2026-10-25-implicit-labeling-xmp-selection.md`）：
  - **不引新库**：用项目已锁定 `androidx.exifinterface:exifinterface:1.4.1`（`gradle/libs.versions.toml:28`）的 `TAG_XMP` 写入路径（JPEG 独立 APP1 段、PNG iTXt 块，1.4.1 字节码实测）+ 自写约 20 行 TC260 包模板；只写 `Label`/`ContentProducer`/`ProduceID` 三个规范必填字段。
  - **任务前提被推翻**："exifinterface 无 XMP 写"不成立（1.4.1 含 public `TAG_XMP`、`writePngXmpItxtChunk`；androidx 主分支源码同证）。
  - 未决：TC260 官方 PDF（TC260-PG-20259A）下载有会话门槛、本日未取得原文；命名空间 URI `http://www.tc260.org.cn/ns/AIGC/1.0/`、逐格式载体（XMP vs EXIF UserComment/tEXt）、阿里云 27 位编码、GB 45438-2025 附录均为单一来源/待验证（U1–U7）。容器运行时 round-trip 未跑（Gradle 禁令）。
  - license/维护度：采纳 exifinterface（Google 官方、Apache-2.0、活跃）；Adobe XMPCore（6.1.11、6 年无版、license 溯源混乱）、Ashampoo xmpcore（弃用迁移）、Commons Imaging（JPEG 不可写）、metadata-extractor（只读）均否决；理由逐项在 R2 §3.2。
- **R3 ps16k 模拟器可行性**（`docs/research/2026-10-25-ps16k-emulator-feasibility.md`）：
  - 路线**可行且已全链路实测跑通**：ps16k 系统镜像（`system-images;android-36;google_apis_ps16k;x86_64`，本机 `sdkmanager --list` 实测 50 条 ps16k）决定页大小（镜像 `kernel_cmdline.txt` 含 `page_shift=14`）；AVD 无页大小功能键（仅 `tag.id=page_size_16kb` 元数据）、emulator 无 qemu 页参数（`-help` grep 零命中）。
  - 建 AVD 后 **80 秒**启动（设备轨复跑冷启 15s，见 §3.8），`getconf PAGE_SIZE=16384`，R3 轮 192MB APK 安装 Success（设备轨复跑为 182,999,814 B），启动/导航/photo picker 全流程零 linker/RELRO/页错误；库级 dlopen 因应用懒加载未观测到，留 T6 按命令块执行（含 QuickJS 装载观测点）。
  - 记录时点复核：镜像目录与 AVD `config.ini` 存在且 `tag.id=page_size_16kb`（只读）。**未验证**：库级 dlopen/RELRO mprotect 的实际运行行为；arm64 ps16k 与相机路径；官方"模拟器最低 35.1.5"未逐字复核。
  - **与设备轨的差异**：R3 §1.4 记录 `ro.product.page_size`/`ro.boot.hardware.cpu.pagesize`=16384；批次 21 设备轨本轮这两项为空（§3.8 步三），页大小以 `getconf`+fingerprint 为准。
- **R4 proso Elo 数学精读**（`docs/research/2026-10-25-elo-predictor-spec.md`）：
  - 移植对象 = `adaptive-learning/proso-apps` 的 `PriorCurrentPredictiveModel`（MIT，`proso/models/prediction.py:164-236`，生产配置 `proso_config.yaml:4-5` 零覆盖）。
  - 关键公式：`p = guess + (1−guess)·sigmoid(skill)`，`guess=1/选项数`；答对 `current_skill += 3.4·(result−p)`、答错 `+=0.3·(result−p)`；首答门 `prior += α(u)·residual`、`difficulty −= α(i)·residual`，`α(n)=0.8/(1+0.05n)`；难度只在首答更新。
  - Elo 状态 = 台账前缀纯函数惰性重放（`eventSequence` 折叠，VOID/REPLACE 解析），水位缓存，GAP/CONFLICT fail-closed；全部超参标 `PENDING_CALIBRATION`。
  - 未验证：proso 超参在本项目数据上的真实效果；Pelánek 论文 PDF 两通道不可解析（公式权威=仓库源码）；旧仓 anti-boring-framework 404，固定双 K 形态无法取证；50ms/50k 为推算非实测；`SELECTOR_COMPOSITE` 是否含 `PREDICTOR_ELO` 由 T2 判定（本批判定：不进，见 §3.2）。
- **R5 合规文案/清单/未成年人**（`docs/research/2026-10-25-beta-compliance-copy.md`）：
  - 三档《标识办法》第四条用户侧文案（讲题+生图、显式/隐式标识、未成年人提示）；12 项应用商店上架材料清单（适用性逐项判定+条文依据）；10 项未成年人模式最小对齐清单（G7 最严解释落点）。
  - 关键核对：**《标识办法》全文无未成年人条款**（未成年人提示为产品主动说明，不写法定义务）；API 调用已备案大模型按 CAC 2026 年公告属地登记制且须公示模型备案号；算法备案因无舆论属性不触发；办法第七条产生商店侧 AI 服务说明+标识材料义务。
  - **复核修正（2026-10-26）**：R5 §2.3 原"导出（含 PDF）保留隐式标识"与实现不符（PDF 重绘丢文件级元数据），已改为"原图文件保留；PDF 仅显式图注"；产品文案同步修正。原句不再作为用户合规承诺。

### 2.3 研究复核结论

- **总体**：五条研究均有落盘文档、来源分级与待验证清单；本批代码实现与 R1/R2/R4 的签名/公式/字段表逐项对齐（记录员实读 §3 各文件）。未发现需要推翻研究结论的新事实；发现的是**实现/文案与研究的边界漂移**，均已处置或登记（§5）。
- **R1**：AAR/POM/.module 哈希经 `tools/ci/check_dependency_pins.py` 实跑复核（positive exit 0；negative control 指定 2.3.21 时 exit 1）；issue #240 的"OPEN/零评论/无修复"为 R1 文档 2026-10-25 `gh` 单源，本记录员未重新拉取，标**单一来源待验证（状态可能随上游变化）**；其钉版源码结构判读（排水循环持锁、中断不排空）与引擎 KDoc/测试形状一致。
- **R2**：项目锁定 exifinterface 1.4.1 与 `TAG_XMP` 调用点实读成立；**运行时 round-trip 未跑**（T5 仪器化未执行，§3.5/§5 #5），故"JPEG APP1 / PNG iTXt 由库分流"仍是字节码+源码级证据，不是设备证据。命名空间 URI 单源（U2）在代码中已标注并在 parser 中精确校验（§5 #20）。
- **R3**：镜像/AVD/`tag.id` 记录员只读复核存在；`getconf PAGE_SIZE=16384` 与 APK/QuickJS 16KB 装载数字来自设备轨转录，**仓内日志没有 `16384` 字样**（§4），属"工单转录、可复跑但未留日志"；新加的 `runtimePageSizeIs16KB` 断言尚未在设备轨执行（§5 #7）。
- **R4**：公式/超参与 proso 源码引用一致；本项目 JVM 用例 14/14 绿（XML 实测），但真实数据校准效果 UNVERIFIED（设计如此）。
- **R5**：条文核对为 R5 会话实抓；本记录员未重新抓取 CAC 原文，标**文档级证据**；R5 的 PDF 承诺错误已在 2026-10-26 修正，产品文案同步（§5 #2/#17）。合规件不构成法律意见，上架前仍需法务复核。

## 3. 六实施轨 + 集成 + 设备轨

> 判定口径：**代码级完成** = 改动在当前树且记录员/复核静态核对属实；执行证据分列（设备实跑 / JVM 工件 / 独立编译执行 / 无证据=UNVERIFIED）。文件清单已逐个 `git status` 对上（附录）。
>
> 状态总表（与工单 JSON 的差异已注明）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 KD-64 修复 | 完成 | **完成** | JVM 13/13 + 设备 3/3 + KD-64 收口 |
> | T2 G5-A 落地 | 完成 | **代码完成 / 生产未接线（partial/blocked）** | 装配器 internal、无生产调用点；生产候选 contentVerified 恒 false（死端） |
> | T3 QuickJS 真接入 | 完成 | **代码完成 / 运行时部分红（partial）** | 12/13 设备绿、OOM 红、issue #240 必挂、16KB 断言未跑、4E.2 未做 |
> | T4 JsPort 接线 | 完成 | **完成（harness）** | 30 项 pytest 实跑绿；JS 后端 NOT_AVAILABLE 如实 |
> | T5 隐式标识 | 完成 | **代码+JVM 完成 / 设备 UNVERIFIED** | 9/9 JVM；4 个仪器化用例未执行 |
> | T7 合规件 | 完成 | **合规件落盘完成 / 应用内嵌入遗留** | SecondaryScreens 他线脏，fallback |

### 3.1 T1 KD-64 修复 —— **完成**

- 文件（3）：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/NewIntroductionPolicy.kt`（M）、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/NewIntroductionPolicyTest.kt`（M）、`docs/known-defects.md`（M，KD-64 条目）。
- 交付：死锁分支 granted 记账公式改为**引入候选真实时长之和** `introduced.sumOf { it.estimatedDurationSeconds }`（`NewIntroductionPolicy.kt:181`）；全超预算形状如实 deferred、`granted=0`，不再出现负 slice；常规路径取值逐位不变。`IntakeDecision` require 现 `:80`，死锁分支 `:143-152`（KD-64 注释 `:135` 起）。
- 用例：`NewIntroductionPolicyTest` 现 **13 例**（新增 3 例：全超预算 deferred+granted 0、伴候选按真实时长 150、granted 记账 250≠100 的反例）。记录员实读 XML：`tests="13" failures="0" errors="0"`（`core/domain/build/test-results/test/TEST-...NewIntroductionPolicyTest.xml`，2026-10-26 05:20）；同文件另两个测试类 `AttemptConfidenceFluentErrorTest` 2/2、`AttemptConfidenceAssessmentTest` 5/5，三 XML 合计 **20/20**。T1 轨自报"红测 3 例→19/19 绿"为轨内运行集（与当前 XML 的 20 例不矛盾，差异为测试集边界）。
- 设备：`StudyReviewPlanner3EScanInstrumentedTest` → `OK (3 tests)`、`AM_EXIT=0`（`build/device-track-batch21/step1-kd64-run1.log`）。**T10 两红例转绿证据成立**。
- 文书：KD-64 已写 `fixed`，但 `Verified fixed` 段原仍为未来时、分支锚点写 `:144-152`；本记录已收口（§5 #9）。

### 3.2 T2 G5-A 落地 —— **代码完成 / 生产未接线（partial/blocked）**

- 文件（8）：`core/domain/.../EloCorrectnessPredictor.kt`（新）、`core/domain/src/test/.../EloCorrectnessPredictorTest.kt`（新）、`core/domain/.../LearningCoreVersions.kt`（M，新增 `PREDICTOR_ELO`）、`core/domain/.../AdaptiveQuestionSelector.kt`（M，新增 `AdaptiveDecision.predictionModelVersion`）、`core/domain/src/test/.../AdaptiveQuestionSelectorTest.kt`（M）、`core/data/.../study/AdaptiveSelectionAssembler.kt`（新）、`core/data/src/test/.../study/AdaptiveSelectionAssemblerTest.kt`（新）、`docs/research/algorithm-version-ledger.md`（M，§3.31）。
- 交付：`AdaptiveCorrectnessPredictor` 端口 + `EloCorrectnessPredictor`（proso 公式移植、超参逐字段 `PENDING_CALIBRATION`、台账前缀惰性重放纯函数）+ `PREDICTOR_ELO="predictor-elo-v1"`；`AdaptiveSelectionAssembler` 只读装配（quiz 台账有效流含 VOID/REPLACE 链解析、水位缓存、当次会话快照 + 错题 itemFamily 候选、先修稳定度 join）。零新表、零写入、零 ModelTaskKind/提示词/请求 schema。
- 版本口径：`PREDICTOR_ELO` **不进** `SELECTOR_COMPOSITE`（读侧纯折叠、无持久重放面）；预测身份由 `AdaptiveDecision.predictionModelVersion` 带到决策审计面（§5 #18 修复）。
- 用例：`EloCorrectnessPredictorTest` **14/14**、`AdaptiveSelectionAssemblerTest` **10/10**、`AdaptiveQuestionSelectorTest` **21/21**（XML 2026-10-26 05:20–05:22；工单 `tmp/verify_batch21.py` 汇总 69/69）。
- **未接线（如实）**：装配器是 `internal class`（`AdaptiveSelectionAssembler.kt:73`），feature/review 是另一 Gradle 模块，无法实例化；全仓除自身 KDoc 与测试外零引用。**"唯一 seam 是热区 VM、一行接线"在装配期不成立**（基线 §4 已修正，KDoc 现如实登记 `partial/blocked`）。本批不写热区，故无生产调用点。
- **生产候选死端（fail-closed，不是可放宽项）**：唯一生产题面写入方 `KnowledgeQuizItemStore.record` 固定 `verificationStatus = UNKNOWN`（`:173-176`）；装配器只把 `VERIFIED`/`USER_ASSERTED` 置 true（`:162-163`），错题候选恒 false（`:194`）；`AdaptiveQuestionSelector.safeBaseCandidates` 要求 `contentVerified=true`（`:241`），未核验分支返回 `CLARIFY_OR_CONFIRM`（`:288-292`）。feature/app 没有该决策消费者，也没有把快照行升级为 VERIFIED/USER_ASSERTED 的写方——**即使接线，G5-A 也到不了 ASK**。已登记 KD-67。
- 备注：`runCatching` 吞取消与缓存竞态已在复核后修复（`readOrNull` + `stateMutex`，§5 #19）。

### 3.3 T3 QuickJS 真接入 —— **代码完成 / 运行时部分红（partial）**

- 文件（9）：`gradle/libs.versions.toml`（M，钉版+sha256+传递依赖注释）、`core/data/build.gradle.kts`（M，`implementation(libs.quickjs.kt.android)`）、`core/data/.../visual/QuickJsVisualExecutionEngine.kt`（新）、`core/data/src/test/.../visual/QuickJsVisualExecutionEngineJvmTest.kt`（新）、`core/data/src/androidTest/.../visual/QuickJsVisualExecutionEngineInstrumentedTest.kt`（新）、`core/data/src/androidTest/.../visual/NativeLibraryPageSizeInstrumentedTest.kt`（新）、`docs/current/visual-generation-contract.md`（M，`:22` RELRO 判据）、`.github/workflows/android-check.yml`（M，依赖钉版门）、`tools/ci/check_dependency_pins.py`（新）。
- 交付：`core:data` 钉版消费 `io.github.dokar3:quickjs-kt-android:1.0.15`（sha256 双源复核）；`QuickJsVisualExecutionEngine` 真引擎（每事务新上下文 + 封闭 Date/种子 PRNG + 协作层与宿主硬期限两层终止 + issue #240 accept-the-leak）；JVM/仪器化用例；契约 `:22` RELRO 判据按官方豁免改写（"RELRO 独占 LOAD 段……不构成阻断门"）。
- **终止层真相（本批复核高优先）**：两层**不保证调用方上界**。`withTimeout(hard+500ms)` 包住的 `runPhase` 在 `withContext(jsDispatcher)` 内执行同步 JNI，协程取消不能抢占；issue #240 下 `execute()` 永不返回、`finally` 不执行、`inFlight` 不复位，后续只回 BUSY。KDoc 已如实写明（`QuickJsVisualExecutionEngine.kt:72-97`），T3 不能宣称"hard+500ms 必返回"或"引擎继续可用"。
- **issue #240**：钉版 OPEN/零评论/无修复；真实复现必挂（§3.8 步二）。仪器化用例已改为 R1 §2.4 的 watchdog 形状（`issue240SelfQueuingJobIsKnownOpenAndLeaksOnlyDaemonThreads`，`:305-347`：独立 daemon 线程 + `CountDownLatch.await(5s)` + 不调原始 `QuickJs.close()`），但**新形状尚未在设备轨执行**（设备日志只有旧形状的真跑挂死证据）。
- **内存上限**：`memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit` 设备上**红**——x86_64 实际 `INVALID_PROGRAM`（旧夹具 `InternalError: string too long`；改用分配型探针后仍 `program failed in phase prepare: null`），未拿到 `MEMORY_LIMIT`。`classifyJsException` 仍是消息启发式（只认 `"out of memory"`）。断言未放宽（符合纪律），但**内存仪器化路径未验证**（§5 #6/#21，KD-70）。
- **16KB**：ps16k `emulator-5606` 上 `aProgramRenders...`/`anInfiniteLoop...`/`closing...` 各 `OK (1 test)`（`step3-16k-quickjs.log`）；`NativeLibraryPageSizeInstrumentedTest` 旧版 `OK (2 tests)`（`step3-16k-nativeload.log`）。当前树已新增 `runtimePageSizeIs16KB`（断言 `16384`、4KB skip、打印 `PAGE_SIZE=`），**该第 3 例未在设备轨执行**（§5 #7）。
- **未做（登记 4E.2 前置）**：契约 §4 第三层——`:visual_js` `isolatedProcess` + AIDL 控制通道 + `killProcess(myPid())` 进程级硬终止；这是唯一能终止任何循环的层。契约 §5 的"app 实现"落位偏差（本实现落 core:data、未引用 AIDL/原生排版）已在 KDoc 登记。
- **供应链**：AAR/POM/.module 钉版与 CI 门已落（记录员实跑 positive exit 0、negative control exit 1）；**仍无 Gradle dependency locking / verification-metadata**，解析期 kotlin-stdlib 仍由 AAR 的 2.4.10 覆盖编译期 2.3.21（§5 #14）。回退自建 AAR 的判据（任一仪器化门红且归因 AAR 构件）**尚未裁决**：OOM 红更可能归因夹具/库异常消息而非 AAR 的 16KB 构件；issue #240 是上游缺陷，不是 AAR 构件问题。

### 3.4 T4 JsPort 接线 —— **完成（harness）**

- 文件（5）：`tools/figure_eval/figure_eval_gate.py`（M）、`tools/figure_eval/test_figure_eval_gate.py`（M）、`tools/figure_eval/README.md`（M）、`tools/figure_eval/reports/figure_eval_fake_run_report.json`（M）、`tools/figure_eval/reports/figure_eval_fake_run_report.md`（M）。
- 交付：以可注入 `VisualExecutionEngine` 适配器的 `JsPort` 替换 `JsPortStub`；支持 IMAGE_MODEL/JS 主后端切换与双向切换记录；JS 引擎未注入/不可用时如实 `NOT_AVAILABLE`；30 项机制测试；双后端 CLI 实跑；门常量/预算/冻结夹具零改动。
- 记录员实跑：`python -m pytest tools/figure_eval/test_figure_eval_gate.py -q` → **30 passed in 0.16s**（`EXIT=0`）。报告实读：`js_backend_recorded ✓`、`js_backend_honest_status ✓`、`js_never_claims_quality ✓`；`JS：NOT_AVAILABLE（driver=none；reason=未注入 VisualExecutionEngine 适配器…）`。
- 边界：真实 JS 后端只在设备轨可执行；报告中的"双后端切换实跑"= 切换框架跑通、IMAGE_MODEL 产出样本、JS 如实 `NOT_AVAILABLE`（无样本），**不等于 JS 后端质量/可用**；fake 报告不构成真实质量结论；真实质量/费用 UNVERIFIED；JS 仍不广告。

### 3.5 T5 隐式标识落地 —— **代码+JVM 完成 / 设备 UNVERIFIED**

- 文件（4）：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadata.kt`（M，+199/-16）、`core/data/src/test/.../capture/GeneratedFigureAigcXmpTest.kt`（新）、`core/data/src/androidTest/.../capture/GeneratedFigureMetadataInstrumentedTest.kt`（M，+154）、`docs/research/2026-10-25-ai-content-labeling-compliance.md`（M，G4 行更正）。
- 交付：按 R2 §4.2 配方，在"导出图资产"字节新增 TC260 AIGC 隐式标识层（只写 `Label`/`ContentProducer`/`ProduceID`，经 `TAG_XMP` 随 JPEG→独立 APP1/PNG→iTXt 由库分流）；DOCX 按原字节嵌入自动继承；PDF 维持已登记边界（重绘丢元数据，由 G3 显式图注覆盖）。显式图注 G3 与既有 EXIF 层零改动。
- JVM：`GeneratedFigureAigcXmpTest` **9/9**（XML 实测）：xpacket/命名空间、三字段、转义、往返、外来/损坏包 fail-closed、**同名 tag 但命名空间不同不算**、XMP-only 读回只得属性+编号、EXIF 优先、`Label=="1"` 才判 AI。
- 设备：`GeneratedFigureMetadataInstrumentedTest` 当前 **4 例**（PNG 写入+拷贝、TC260 PNG 与 EXIF 共存、JPEG APP1、非 AI 图不写），**本批设备轨没有该类的任何日志**（`find build/device-track-batch21 -iname '*GeneratedFigure*'` 零命中）→ **文件级写入→读回 UNVERIFIED**（§5 #5）。
- 未决：TC260 官方 PDF/命名空间 URI 单源（R2 U1–U5）；PDF 隐式标识边界已在 KDoc/文案/清单三处一致；G4 只写必填三字段，传播要素不写。

### 3.6 T7 合规件落地 —— **合规件落盘完成 / 应用内嵌入遗留**

- 文件（4）：`docs/ai-content-disclosure-copy.md`（新，G5 三档文案）、`docs/research/2026-10-25-store-launch-materials-checklist.md`（新，G6 12 项）、`docs/agent-first-refactor-decisions-2026-09-23.md`（M，G7 最严解释登记）、`docs/research/2026-10-25-beta-compliance-copy.md`（M，R5 复核修正）。
- 交付：G5 三档文案逐字落盘（一句话/一段/完整，可直接嵌入设置/关于/首启）；G6 上架材料清单 12 项（逐项适用/不适用+依据）；G7 十项最小对齐清单 + 遗留登记。
- **未嵌入（如实）**：`SecondaryScreens.kt` 开工即脏（M，43+/43-，他线在飞），`SmartMistakeBookRoot.kt` 亦 M；按共享纪律 fallback——不嵌入应用内，只落文案文件并登记嵌入点（`docs/ai-content-disclosure-copy.md` §6）。**T7 不声称"应用内已合规"**。
- 文案修正：PDF 只保留显式图注、不承诺文件级隐式标识；未成年人提示为产品主动说明（《标识办法》无未成年人条款）。
- 待验证：模型名称/备案号/上线编号占位符；小米商店软著细节、教育服务时段豁免条文；上架前法务复核。

### 3.7 集成

- **无生产接线**：T2 装配器不接线（热区 + internal）；T3 引擎无生产消费者（JS 仍不广告）；T4 只在 tools/figure_eval；T5 只在导出图资产字节层；T7 只落文档。`SmartMistakeBookRoot.kt`、`SmartMistakeBookApplication.kt` 本批未改。
- **CI/供应链**：`.github/workflows/android-check.yml` 新增 `Dependency pin gate`，调用 `python3 tools/ci/check_dependency_pins.py`（在 `lintVitalAnalyzeLocalFirstRelease` 后，解析图已生成）；脚本校验 AAR/POM/.module sha256、POM 传递 kotlin-stdlib 与 catalog runtime pin、解析图 quickjs/kotlin-stdlib 版本。
- **契约**：`docs/current/visual-generation-contract.md:22` 的 RELRO 判据已按官方豁免改写；`contract :22` 现文"RELRO 独占 LOAD 段……不构成阻断门"（§5 #23 指出其"余数页内无可写段"措辞不精确，结论不推翻）。

### 3.8 设备轨（工单转录；记录员未复跑、无设备）

> 环境：`emulator-5554`（API 34，x86_64，4KB）用于步一/二/四/五；`emulator-5606`（ps16k，API 36，x86_64，16KB）用于步三。协议 = `adb install -r -t` + `am instrument -w -e class ...`，**全程未跑 connectedAndroidTest**。以下数字均来自工单设备轨实跑后转录；日志在 `build/device-track-batch21/`。

1. **步一 KD-64（emulator-5554）**：`adb -s emulator-5554 shell am instrument -w -e class com.tingyun.smartmistakebook.core.data.study.StudyReviewPlanner3EScanInstrumentedTest com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner` → `OK (3 tests)`，`AM_EXIT=0`（`step1-kd64-run1.log`）。T10 两红例转绿。
2. **构建阻塞（本批新发现并修复）**：首跑 `:core:data:assembleDebugAndroidTest` `exit=1`，D8 报 `Space characters in SimpleName '...QuickJsVisualExecutionEngineInstrumentedTest$a program renders...' are not allowed prior to DEX version 040`（`build-step1.log:373`）——T3 交付的 14 个反引号空格测试方法名无法 dex。已把 14 个方法改为 DEX 安全标识符并在类 KDoc 注明原因（`QuickJsVisualExecutionEngineInstrumentedTest.kt:44` 起）；复跑 `assembleDebugAndroidTest` `exit=0`（`build-step1b.log`，BUILD SUCCESSFUL in 29s）。
3. **步二 QuickJS 逐方法真跑（emulator-5554，无 `-e log true`，timeout 100，13 方法）**：**12 绿**；`memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit` **红**：`java.lang.AssertionError: actual: INVALID_PROGRAM: program failed in phase prepare: InternalError: string too long`，独立复跑 3/3 同结果（`step2-quickjs-permethod.log`、`step2-quickjs-oom-detail.log:1-6`）。契约要求的"内存超限→MEMORY_LIMIT"在 x86_64 上未成立。
4. **OOM 归因探针（临时文件，跑完已删）**：改用分配型程序（`a.push({x:1,y:'abcdefgh'})` 无限增长，cooperative=5000ms/hard=10000ms）仍 `INVALID_PROGRAM`，诊断 `program failed in phase prepare: null`（`step2-oom-attribution.log`；logcat `DIAG allocation-heavy reason=INVALID_PROGRAM diagnostics=program failed in phase prepare: null`）→ 两类探针都拿不到 MEMORY_LIMIT，面在 AAR 异常消息丢失/非 `out of memory` 文案，不在探针本身。
5. **步二 issue #240 真跑必挂**：单独真跑 `... issue240SelfQueuingJobIsBoundedByTheHostHardDeadlineAndFusesTheProgram ...`（该名字是修复前的方法名；复核后已改为 `issue240SelfQueuingJobIsKnownOpenAndLeaksOnlyDaemonThreads`）→ `Terminated`，`AM_EXIT=124`（`step2-quickjs-issue240-real.log`）；类级真跑两次均 7 个点后无 summary（`step2-quickjs-run1.log` 首次 >13 分钟，`step2-quickjs-hang-proof.log`；第二次 `timeout 180` exit=143）。挂死现场线程栈：`quickjs-visual-js` 在 `com.dokar.quickjs.QuickJs.executePendingJob`←`awaitEvaluateResult`←`evalAndAwait`（utm=80069），`quickjs-visual-force-close` 在 `QuickJs.close`→`yieldThread` 抢 jsMutex（`step2-thread-dump.txt:458-560`），即 issue #240 的排水循环持锁形状。
6. **dry-run 日志警示**：所有加 `-e log true` 的调用（0.062s、`OK (14 tests)`）是 log-only 未真执行（`step2-quickjs-order.log`）；`step2-quickjs-issue240.log`（0.005s、`OK (1 test)`）亦为排序探针。**未作为任何通过证据**；文件未改名/未标注 DRY_RUN，本记录在此显式标注（§5 #10）。
7. **so 装载（4KB x86_64）**：test APK 含 `lib/x86_64/libquickjs.so`（`unzip -l` 输出）；`NativeLibraryPageSizeInstrumentedTest` 在 emulator-5554 → `OK (2 tests)`，实际 `System.loadLibrary('quickjs')/('LiteRt')/('litert_jni')`（`step2-4k-nativeload.log`）；挂死线程栈的本机帧来自 `base.apk (js_error_to_java_error... JS_CallInternal...)`，证明原生引擎真实执行。
8. **步三 16KB（emulator-5606，AVD `ps16k_x86_64`）**：冷启 **15s**；`getconf PAGE_SIZE`=16384、fingerprint 含 `sdk_gphone16k_x86_64`；装 `app-localFirst-debug.apk`（182,999,814 B）Success；monkey 启动 PID=3100；`logcat` grep `linker|dlopen|CANNOT LINK|failed to map|RELRO|SIGSEGV|FATAL EXCEPTION` 零命中。**注意**：本轮 `ro.product.page_size` 与 `ro.boot.hardware.cpu.pagesize` 为空（与 R3 §1.4 记录不同），页大小以 `getconf`+fingerprint 为准；仓内日志**没有 `16384` 字样**（记录员 `grep -rl 16384 build/device-track-batch21` 零命中）。
9. **步三 16KB 原生装载**：装 `core:data` androidTest APK Success；`aProgramRenders...`/`anInfiniteLoop...`/`closing...` 各 `OK (1 test)`（`step3-16k-quickjs.log`）；`NativeLibraryPageSizeInstrumentedTest`（旧版 2 例：quickjs + LiteRt + litert_jni）`OK (2 tests)`（`step3-16k-nativeload.log`）；两轮 logcat linker/RELRO/SIGSEGV 均零命中。收尾只杀 5606，5554/5562/5584 仍在。
10. **步四 备份/迁移核心组（emulator-5554，全部无 log 真跑）**：`FullMigrationMatrixInstrumentedTest` `OK (15 tests)`、`DeviceMigrationDrillInstrumentedTest` `OK (6 tests)`（`step4a-migration-d1.log`）；`BackupRestore` `OK (34)`、`BackupSelectionCreate` `OK (3)`、`BackupSelectionRestore` `OK (10)`、`BackupSnapshotTrimmer` `OK (9)`（`step4b-backup-four.log`）——合计 **77/77**，与批次 20 同树口径一致（未跑 365MiB scale 用例）。
11. **步五 R8**：`internal` 变体（minify=true、无 proguardFiles）装机启动崩溃 `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init>`←`Room.getGeneratedImplementation`（`step5-r8-logcat.txt:372-410`；根因 `app/build.gradle.kts:58-70`（`benchmark` `:58-64`、`internal` `:65-70`）未配 proguardFiles，只有 release `:71-80` 配了）。改用 release + 本机 debug keystore 的 `RELEASE_*` 环境变量（沿用前批 debug keystore 口径，无真实凭据）：`:app:assembleLocalFirstRelease` BUILD SUCCESSFUL 3m24s（`step5-r8-release-build.log`）；装机 Success、monkey 启动 PID=13393、523 行 logcat 中 `FATAL EXCEPTION/ANR in/Fatal signal/WATCHDOG/ClassNotFound/NoSuchMethod/NoClassDefFound` 全为 0（`step5-r8-release-logcat.txt`）、`adb uninstall com.tingyun.smartmistakebook.localfirst` Success。
12. **纪律**：全程未跑 `connectedAndroidTest`、未做任何 git 写操作；生产码零改动；两个被改/新增文件均为 T3 的未跟踪测试文件（`git status` 仅 `??` 两文件），未触碰他线热区。

## 4. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
| --- | --- | --- |
| 单测门（11 模块 `--continue`） | **第 2 轮 `exit=0`**；修复环复跑 `exit=0`（工单材料） | 记录员未复跑 Gradle；**仓内未找到本批门禁日志**（`build/` 下无 batch21 单测门产物）→ 状态为工单转录，不冒充本记录员实证。批次 20 的"他线 dense 金标三测试"红若仍存在，按工单材料归因照录（本批未单独点名复跑）。 |
| 强门（六 androidTest 编译 + `assembleLocalFirstDebug`） | **第 2 轮 `exit=0`**（工单材料） | 同上，无本批日志产物；设备轨的 `assembleDebugAndroidTest` 修复后 `exit=0`（`build-step1b.log`）是旁证，不等于完整强门。 |
| 设备 | 见 §3.8（12 组命令/exit/用例数已落盘） | 全部裸 `am instrument`；协议不产 XML；数字以本记录 §3.8 落盘为审计载体。 |
| 依赖钉版门 | **记录员实跑 `exit=0`**；负控 `exit=1` | `python tools/ci/check_dependency_pins.py` → `OK: quickjs AAR/POM checksums and resolved runtime pins match`（AAR/POM/.module 三哈希 + `resolved quickjs=['1.0.15'] kotlin-stdlib=['2.4.10']`）；`--expect-kotlin-runtime 2.3.21` → `FAIL: POM requires kotlin-stdlib 2.4.10...` / `FAIL: resolved kotlin-stdlib ['2.4.10'] != pinned 2.3.21`，`EXIT=1`。 |
| T4 机制测试 | **记录员实跑 30 passed** | `python -m pytest tools/figure_eval/test_figure_eval_gate.py -q` → `30 passed in 0.16s`，`EXIT=0`。 |
| JVM 测试工件 | **记录员实读 XML** | `core/data`：Assembler 10/0、AigcXmp 9/0、QuickJsJvm 2/0（2026-10-26 05:22）；`core/domain`：Elo 14/0、NewIntroduction 13/0、Selector 21/0（05:20）→ 合计 **69/69**。这些是**非 Gradle** 的直编/JUnitCore 工件（`tmp/verify_batch21.py`），不是单测门本体。 |
| 真实 Provider | **UNVERIFIED** | 无凭据、未调用；PRD-09 真实质量/费用未验。 |

**修复环第 1 轮处置结论（工单原文转录；其编号是修复环自己的编号，与 §5 的 25 条编号不是同一套）**：

- **属实并已修**：#1/#8、#2/#12、#3、#4、#6/#16、#7、#9、#13、#14、#15。
- **属实但只完成非热区部分、产品面仍阻塞**：#10（G5-A 死端）、#11（KD-64 UI 覆盖承诺）。
- **未发现可反驳的发现**；#4 的"只有 02:48 一个 XML"在核对时已过时（实有 8 个 04:38 XML），但"三套新用例无执行产物"属实，已补跑。
- **未运行 Gradle**（遵守约束）。JVM 验证用缓存 Kotlin 2.3.21 编译器 + JUnitCore 手动重编译执行；instrumented 用例只做到编译通过，设备轨未执行。
- 修复环给出的命令：`python tmp/verify_batch21.py` → 69/69 OK，exit 0；`python tools/ci/check_dependency_pins.py` → exit 0；负控（`--expect-kotlin-runtime 2.3.21`）→ exit 非 0；单测门复跑 exit=0。

**记录员对上述门禁的保留**：单测门/强门的 `exit=0` 没有本批日志落在仓内（`find build -maxdepth 1 -newermt ...` 只看到 KB 他线产物）；本记录不把"门禁全绿"写成可审计结论。设备数字均有 `build/device-track-batch21/*.log`，但 **16KB 的 `getconf`/页大小断言没有日志**，且新加的第 3 个 page-size 用例未执行；T5 仪器化零日志。

## 5. 复核发现与处置（25 条；severity/status 按原复核，处置为本记录员按当前树核对）

| # | 位置 | 发现 | 级别 | 复核状态 | 本批处置（当前树） |
| --- | --- | --- | --- | --- | --- |
| 1 | `QuickJsVisualExecutionEngineInstrumentedTest.kt:292-306`；`QuickJsVisualExecutionEngine.kt:76-78,164-183` | issue240 用例不是 R1 规定的 watchdog 形状；引擎 KDoc/测试注释仍宣称 hard+500ms 必返回、引擎继续可用；T3 应为部分/阻塞 | 高 | 已独立复核 | **已修（复核后）**：测试改为独立 daemon 线程 + `CountDownLatch.await(5s)` + 不调 `QuickJs.close()`（`:305-347`）；引擎 KDoc 明写"这一层不保证调用方上界……T3 是部分/阻塞"（`:72-97`）。新形状**未在设备轨执行** → 该形状运行时 UNVERIFIED。 |
| 2 | `docs/ai-content-disclosure-copy.md:28-30`；`GeneratedFigureMetadata.kt:15-17`；`MistakePdfPagePlanner.kt:35-36,145-149` | G5 文案称"导出（如保存、分享、导出 PDF）的内容会保留上述标识"，但 PDF 重绘丢文件级元数据，只有显式图注 | 高 | 已独立复核 | **已修**：文案改为"导出为 PDF 时……文件级隐式信息不会随 PDF 保留（该边界已登记，不再承诺 PDF 携带隐式标识）"；R5 §2.3 同日修正；`GeneratedFigureMetadata` KDoc 与 PDF 图注一致。 |
| 3 | `AdaptiveSelectionAssembler.kt:51-55`；`baseline:93` | T2 称唯一生产 seam 是热区 VM、一行接线；但装配器 `internal`、feature/review 跨模块无法实例化，全仓零生产引用，也无台账遗留条目 | 中 | 已独立复核 | **已修（文档如实化）**：类 KDoc 改为"internal、无法直接实例化、生产调用点不存在、G5-A 未接线（partial/blocked）"（`:51-66`）；本记录 §3.2 同步；代码仍未接线。 |
| 4 | 三套新 JVM 用例；`core/data/build/test-results/testDebugUnitTest/` | 新 JVM 用例在树但无执行产物（核对时只有 02:48 一个 XML）；"完成"会被读成已验证 | 中 | 已独立复核 | **已补跑**：`tmp/verify_batch21.py` 69/69（工单）；记录员实读 XML：Assembler 10/0、AigcXmp 9/0、QuickJsJvm 2/0（05:22），Elo 14/0、NewIntroduction 13/0（05:20）。仍非 Gradle 门。 |
| 5 | `GeneratedFigureMetadataInstrumentedTest.kt:100-243` | T5 的"文件级写入→读回"仪器化用例未在设备轨执行；TAG_XMP/iTXt 运行时行为 UNVERIFIED | 中 | 已独立复核 | **未处置**：当前树 4 例在盘；`build/device-track-batch21` 无该类日志 → 运行时 UNVERIFIED（§3.5/§6）。 |
| 6 | `QuickJsVisualExecutionEngineInstrumentedTest.kt:191-207`；引擎 `:94-95,407-411` | 名为"内存超预算"的用例并未触及 memoryLimit；旧夹具先撞 `string too long` → INVALID_PROGRAM；设备红 | 中 | 已独立复核 | **部分处置**：`OOM_SOURCE` 改为持续分配数组/对象（`:435-445`），注释说明旧夹具根因，断言未放宽；但**未在设备重跑**，分配型探针仍 `INVALID_PROGRAM`（`step2-oom-attribution.log`）→ 仍 UNVERIFIED。 |
| 7 | `NativeLibraryPageSizeInstrumentedTest.kt:42-50`；`step3-16k-nativeload.log` | "16KB 运行时 + quickjs/LiteRT 装载绿"缺一个运行中断言页大小=16384 的产物；旧测试 4KB 也会过；step3 日志无 `getconf` | 中 | 已独立复核 | **部分处置**：新增 `runtimePageSizeIs16KB`（`assumeTrue(pageSize==16384)`、`assertEquals(16384)`、打印 `PAGE_SIZE=`）；**未在设备执行**，step3 日志仍是旧版 `OK (2 tests)`。 |
| 8 | T3 文件清单 vs `NativeLibraryPageSizeInstrumentedTest.kt` | T3 文件清单漏列本批新增的 16KB/LiteRT/QuickJS 装载门；临时 OOM 探针已不在树 | 低 | 已独立复核 | **已处置**：本记录 §3.3 文件清单补登 `NativeLibraryPageSizeInstrumentedTest.kt`；OOM 探针已删，只在日志留痕。 |
| 9 | `docs/known-defects.md:2551,2561` | KD-64 状态头已 fixed，但 `Verified fixed` 仍未来时；分支锚点写 `:144-152`，实际 `:143-150`（现 `:143-152`） | 低 | 已独立复核 | **已处置**：本次同步改写为"域层修复 + 设备 3/3 复验"，锚点改 `:80`/`:143-152`/`:181`，并写入 `step1-kd64-run1.log` 证据；见 §6 同步文档。 |
| 10 | `step2-quickjs-order.log:371-375`；`step2-quickjs-issue240.log:1-20` | 证据目录有可被误读为全绿的 dry-run 日志（0.062s OK(14)、0.005s OK(1)），未标注 | 低 | 已独立复核 | **未改名，已显式标注**：本记录 §3.8 步二/步六明确"log-only 未真执行，不作为通过证据"；建议后续在文件名/首行加 `DRY_RUN`（未做，避免改设备轨产物）。 |
| 11 | ask 热区/干净区列表；计划 §0；T7 summary；`SecondaryScreens.kt` | 干净区声明与当前树不一致：ask 热区写"docs/current 全部"，同一 ask/计划又把 contract 列干净区并授权 T3 改 22 行；`SecondaryScreens.kt` 列干净区但实为 M | 低 | 已独立复核 | **已记录更正**：本记录 §1.3 写明矛盾；contract 改动仅 RELRO 一行且计划 §1 授权；SecondaryScreens 走 T7 fallback（不嵌入）；未发现本批越权改热区。 |
| 12 | `step5-r8-logcat.txt:372-402`；`step5-r8-release-logcat.txt` | minified debug R8 冒烟在 `WorkDatabase_Impl` 处 FATAL；release 冒烟绿；debug 红未归因/登记 | 低 | 已独立复核 | **已登记 KD-69**：根因 `app/build.gradle.kts:58-70` 的 `benchmark`（`:58-64`）/`internal`（`:65-70`）buildType minify=true 但无 `proguardFiles`；release `:71-80` 有。本批不改热区/构建配置，登记待修。 |
| 13 | 引擎 `:164-183,294-331`；测试 `:292-316` | hardDeadline+500ms 调用方上界不成立；`withTimeout` 不能抢占同步 JNI；#240 下用例可能挂死设备门；`inFlight` 不复位，后续 BUSY | 高 | 已独立复核 | **已修（文档）/功能未达**：KDoc 已如实写"不保证上界、T3 部分/阻塞"；测试改 watchdog；`inFlight` 不复位是 accept-the-leak 的已知形态。真正的进程级终止留 4E.2（§3.3/§6）。 |
| 14 | `libs.versions.toml:3,42-52,99`；`core/data/build.gradle.kts:193-199`；lint XML | 供应链：钉版只钉直接坐标，无 dependency locking/verification-metadata；AAR 的 POM 要求 kotlin-stdlib 2.4.10，静默覆盖项目编译 2.3.21；catalog 注释不是构建门 | 高 | 已独立复核 | **已部分处置**：新增 `tools/ci/check_dependency_pins.py` + CI `Dependency pin gate`；记录员实跑 positive exit 0、negative exit 1。**残留**：仍无 Gradle dependency locking/verification-metadata，解析期漂移只能靠构建后脚本捕获；见 §6。 |
| 15 | `KnowledgeQuizItemStore.kt:173-176`；`AdaptiveSelectionAssembler.kt:110-111,142-143,174`；`AdaptiveQuestionSelector.kt:227-230,275-283` | T2 生产候选全部 `contentVerified=false`；错题恒 false；selector 要求 true → CLARIFY_OR_CONFIRM；feature/app 无消费者、无升级写方 → fail-closed 死端 | 中 | 已独立复核 | **已登记 KD-67 + KDoc 如实化**：装配器 KDoc 写明死端（`:61-69`），测试 `assembled production candidates stay fail-closed...` 钉住；产品解需"核验写方 + CLARIFY 消费者"，不是放宽判据。 |
| 16 | `NewIntroductionPolicy.kt:143-170,274-312`；`StudyReviewPlannerService.kt:458-468`；`StudySnapshotBuilder.kt:122`；`ReviewRoute.kt:514-558`；`batch-intake-spec.md:73` | T1 把崩溃变"静默不发放"：IntakeDecision 无 unschedulable 标志、planner 只保留 introducedIds、UI 用 `preview` 而非 `previewBacklog`、`unschedulableCount` 无生产消费者；学生仍看到"约 M 天安排完" | 中 | 已独立复核 | **已登记 KD-68**：`NewIntroductionPolicy` KDoc 已写明 `preview` 是粗略算术、会隐藏超全预算项（`:199-210`）；UI 修正属 feature/review 热区，留接线批。 |
| 17 | `docs/ai-content-disclosure-copy.md:30`；`GeneratedFigureMetadata.kt:15-17`；`DeterministicMistakePdfRenderer.kt:187-197` | 与 #2 同：PDF 承诺不成立；core/export 无写 XMP 路径，只有显式图注 | 中 | 已独立复核 | **已修**（同 #2）。 |
| 18 | `LearningCoreVersions.kt:110-118,124-135`；`AdaptiveQuestionSelector.kt:291-296` | `PREDICTOR_ELO` 被排除在 `SELECTOR_COMPOSITE` 外，而 `AdaptiveDecision` 只带 selectorVersion；预测器版本在决策审计面不可区分 | 中 | 已独立复核 | **已修**：`AdaptiveDecision.predictionModelVersion` 新增（`:122-132`），ASK/ASK_CALIBRATION 填候选区间 `modelVersion`（`:210/:232`），测试断言非空/CLARIFY 为 null（SelectorTest `:216-236`）。 |
| 19 | `AdaptiveSelectionAssembler.kt:81,83,94,98,163,61-62,97-101` | `runCatching` 吞 `CancellationException`；`cachedWatermark/cachedState` 无锁，可能"新水位+旧状态" | 中 | 已独立复核 | **已修**：所有 suspend/IO 读口改 `readOrNull`（`CancellationException` 原样重抛，`:346-356`）；缓存读写由 `stateMutex` 串行（`:79-82`）；测试 `coroutine cancellation propagates...`（`:326`）。 |
| 20 | `GeneratedFigureMetadata.kt:102-117,184-198` | XMP 不是 EXIF 完整备份；parser 只按字面 tag 搜索，不校验命名空间，别家/伪造同名 tag 会被读成 AI | 中 | 已独立复核 | **已修**：读回只把 `aiGenerated`/`figureId` 从 XMP 补，model/purpose 如实 null（`resolveGeneratedFigureMetadata`）；`parseAigcXmpPacket` 要求 `rdf:Description` 作用域内精确 `xmlns:TC260=AIGC_NAMESPACE_URI`（`:226-250`）；JVM 用例覆盖"同名 tag 外来命名空间不算"（AigcXmpTest `:123`）。 |
| 21 | `QuickJsVisualExecutionEngineInstrumentedTest.kt:190-206` | T3 运行时验收证据缺失且已知红；OOM 例在 x86_64 为 INVALID_PROGRAM；本次会话无整门绿色结果；回退判据未裁决；不能称 T3 运行时已验证 | 中 | 未复核 | **部分处置**：设备轨已跑 13 方法（12 绿+OOM 红），issue #240 真跑挂死；新 watchdog/16KB 断言未跑；T3 降级为 partial（§3.3）。回退自建 AAR 判据**未裁决**（红例未归因 AAR 构件）。 |
| 22 | 引擎 `:122-157,194-198` | close/execute 竞态：第二次 closed 检查后、`currentTransaction.set` 前有窗口；并发 close 读到 null 不强弃，execute 仍会执行/返回成功 | 低 | 已独立复核 | **未处置**：代码仍为 `closed` 检查 → `inFlight` CAS → `currentTransaction.set`；`close()` 只读当前事务。已登记 KD-71（低）。 |
| 23 | `visual-generation-contract.md:22`；APK | 新行称 RELRO 独占 LOAD 段且"余数页内无可写段"；ELF 探针显示第三个可写 LOAD 从 RELRO 文件终点开始，落在同一 16KB 文件页内 | 低 | 已独立复核 | **未改文**：官方豁免结论仍成立（RELRO 与第二个 LOAD 完全同段），但"余数页内无可写段"措辞不精确；留待下次契约维护修正（§6）。 |
| 24 | 本批文件清单 vs 共享纪律热区；`docs/current/visual-generation-contract.md` | 唯一热区重叠是 contract；计划 §1/§0 明确授权（"文件已干净"），共享纪律又写"docs/current 全部"——指令矛盾；未发现其他未授权热区改动 | 低 | 已独立复核 | **已记录**：contract 仅 2 行（RELRO 判据）改动，按计划授权纳入提交并注明；`settings.gradle.kts` 仍为他线 M，不在本批清单；热区 dirty 文件做批次关键词 grep 零命中。 |
| 25 | `AdaptiveSelectionAssembler.kt:227-275`；`LongRangeSampleLedger.kt:145-190` | Elo 有效流包含全部已接受 knowledge-quiz 事件（首答/同日重答/重答），AUD-17 可预测长程样本排除 REVOKED/MODEL_JUDGED/REVEAL/FIRST_OF_CARD/SAME_DAY_REPEAT；口径不同 | 低 | 已独立复核 | **已记录**：算法台账 §3.31 追加"Elo 有效流 ≠ AUD-17 台账"注记；PRD-02 的 VOID/REPLACE 镜像与 LearningProjector 同语义，由 Assembler 测试覆盖（16 例组内含该类）。 |

## 6. 遗留 / UNVERIFIED / 待裁清单

**工程门**
1. **单测门/强门的 `exit=0` 无本批日志产物**（工单转录）；记录员未运行 Gradle。提交前建议主会话把 11 模块单测门 + 强门各复跑一次并留日志到 `build/`。
2. `tmp/verify_batch21.py` 是临时复核脚本（69/69、非 Gradle）；是否入提交由主会话定（本记录建议不入，见 §7）。
3. 依赖钉版门已入 CI，但**无 Gradle dependency locking / verification-metadata**；解析期 kotlin-stdlib 仍由 AAR 拉 2.4.10（编译 2.3.21）。未来 AAR 升级或解析变化由 `check_dependency_pins.py` 事后捕获。

**T1 / KD-64 产品面**
4. KD-64 崩溃已修并设备 3/3；**UI 仍用 `preview` 算术 + 固定 900s，未消费 `previewBacklog.unschedulableCount`**，超全预算题仍可能显示"约 M 天安排完"（KD-68，feature/review 热区）。

**T2 / G5-A**
5. **生产未接线**：`AdaptiveSelectionAssembler` 是 `internal`，feature/review 跨模块无法实例化；全仓零生产引用。
6. **候选死端**：生产题面 `verificationStatus=UNKNOWN` → `contentVerified=false` → 选择器恒 `CLARIFY_OR_CONFIRM`；无写方升级、无 CLARIFY 消费者（KD-67）。
7. Elo 超参全部 `PENDING_CALIBRATION`；真实效果 UNVERIFIED（Wave X）。
8. 装配器候选字段是保守映射（错题 `targetKind=FOUNDATION`、`informationValue=0.0`、`fatigueCost=0`；多节点候选预测 null），接线批需复核。

**T3 / QuickJS**
9. **issue #240**：钉版 OPEN；`execute()` 在自续 job 下永不返回，`close()` 卡死；两层终止不能给调用方上界；`inFlight` 不复位、后续 BUSY。4E.2 隔离进程 + AIDL + `killProcess` 是唯一进程级终止层（KD-66）。
10. **OOM 路径未验证**：x86_64 实际 INVALID_PROGRAM（string too long / prepare null），契约 `MEMORY_LIMIT` 未成立；`classifyJsException` 消息启发式（KD-70）。
11. **16KB 页大小断言未在设备执行**：新 `runtimePageSizeIs16KB` 在盘；step3 旧日志 `OK(2)` 无 `PAGE_SIZE=`；仓内无 `16384` 日志。设备轨数字以工单转录为准。
12. **设备轨 16KB 运行**：QuickJS 三方法 + 原生装载绿，但新 watchdog 形状未跑；`ro.product.page_size`/`ro.boot.hardware.cpu.pagesize` 本轮为空，与 R3 §1.4 不一致。
13. **回退自建 AAR 判据未裁决**：OOM 红更可能归因夹具/库异常消息；issue #240 是上游缺陷；未归因 AAR 构件。
14. **4E.2/4E.3 未做**：隔离进程、AIDL、共享 SceneV1 compiler、交互/保真升级均未落地；JS 仍 UNAVAILABLE、不广告。
15. **close/execute 竞态**（KD-71，低）。

**T4 / T5**
16. T4：真实 JS 后端只在设备轨可执行；fake 报告 JS `NOT_AVAILABLE`；真实质量/费用 UNVERIFIED；PRD-09 退出口未过。
17. T5：**4 个仪器化用例未跑**，文件级 XMP/JPEG/PNG 运行时 UNVERIFIED；TC260 官方 PDF、命名空间 URI、逐格式载体仍是 U1–U5；`ContentProducer` 名称口径/27 位编码未裁。
18. T5 的 PDF 边界已三处一致（KDoc/文案/清单），但 PDF 不携带隐式标识；任何文案不得承诺 PDF 保留隐式标识。

**T7 / 合规**
19. **G1/G2 应用内显式标识未落**（feature/tutor、core/ui 热区）；T7 不声称"应用内已合规"。
20. **G7 十项清单未嵌入**：设置页"家长须知"/时长提醒开关等随 G1/G2 接线批；完整"未成年人模式"不建设（台账留痕理由）。
21. 文案占位符【模型名称】【备案号】【上线编号】未回填；属地登记、模型备案号公示、商店材料、教育服务时段豁免等待验证；上架前法务复核。
22. **AWAITING_VERIFICATION 独立 UI 态 + "重新核实/放弃"路径**（feature/library 与 core/domain 热区）仍遗留（批次 20 起）。

**热区 / 他线 / 环境**
23. **G1/G2、5b/5c、阶段 6 UI、6B-3 注入、AUD-08** 均因热区未空出，本批不做（计划 §2）。
24. `SmartMistakeBookRoot.kt` 他线动画引用未跟踪 `core/ui/ClaudeMotion.kt`；feature 四模块、core/ui、core/database 8 M、core/export 5 M、core/domain 他线 4 M、settings.gradle.kts、docs/current 其余 7 M、docs/research 他线记录、tools/kb、knowledge-production 均他线在飞——提交排除（§7）。
25. 32 位设备（armv7/x86）不可安装为既采决策（裁定 3），本批不回退；minSdk 名义 23 与有效机型面（64 位 only）继续显式化。
26. 真实 Provider / Wave X / 5B 出题回收接线不在本批；G5 选项 C 与探索额度待后续裁定。

## 7. 提交清单建议（记录员只读核对 git status 后列出；**提交由主会话执行**）

**前置条件**：单测门/强门各一次留痕 `exit=0`（工单已报 exit=0，但无本批日志）；设备轨红例（QuickJS OOM/issue240）作为已知项随记录入册，不阻塞提交但不得写成全绿。

本批文件实测 **41 路径**（代码/测试/工具 25 + 文档 16），按 ≤6 路径/块分块：

- **块 1（T1 KD-64）**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/NewIntroductionPolicy.kt`、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/NewIntroductionPolicyTest.kt`
- **块 2（T2 域层）**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/EloCorrectnessPredictor.kt`、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/EloCorrectnessPredictorTest.kt`、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LearningCoreVersions.kt`、`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AdaptiveQuestionSelector.kt`、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/AdaptiveQuestionSelectorTest.kt`
- **块 3（T2 数据层）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/AdaptiveSelectionAssembler.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/AdaptiveSelectionAssemblerTest.kt`
- **块 4（T3 依赖/构建）**：`gradle/libs.versions.toml`、`core/data/build.gradle.kts`、`.github/workflows/android-check.yml`、`tools/ci/check_dependency_pins.py`
- **块 5（T3 引擎）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineJvmTest.kt`
- **块 6（T3 设备用例）**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineInstrumentedTest.kt`、`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/NativeLibraryPageSizeInstrumentedTest.kt`
- **块 7（T5 隐式标识）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadata.kt`、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureAigcXmpTest.kt`、`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadataInstrumentedTest.kt`
- **块 8（T4 harness）**：`tools/figure_eval/figure_eval_gate.py`、`tools/figure_eval/test_figure_eval_gate.py`、`tools/figure_eval/README.md`、`tools/figure_eval/reports/figure_eval_fake_run_report.json`、`tools/figure_eval/reports/figure_eval_fake_run_report.md`
- **块 9（T7 合规件）**：`docs/ai-content-disclosure-copy.md`、`docs/research/2026-10-25-store-launch-materials-checklist.md`、`docs/research/2026-10-25-beta-compliance-copy.md`
- **块 10（研究 R1–R4 上）**：`docs/research/2026-10-25-quickjs-aar-consumption.md`、`docs/research/2026-10-25-implicit-labeling-xmp-selection.md`、`docs/research/2026-10-25-ps16k-emulator-feasibility.md`、`docs/research/2026-10-25-elo-predictor-spec.md`
- **块 11（研究 R1–R4 下 + 基线）**：`docs/research/2026-10-25-batch-21-baseline.md`、`docs/research/2026-10-25-ai-content-labeling-compliance.md`
- **块 12（契约 + 算法台账）**：`docs/current/visual-generation-contract.md`（**热区重叠，计划 §1/§0 授权，仅 RELRO 判据一行；整文件纳入并注明**）、`docs/research/algorithm-version-ledger.md`（**开工即脏 M：T2 §3.31 + 本批 AUD-17 注记；整文件纳入，保留 T2 hunk**）
- **块 13（KD + 决策台账 + PRD-09 + 主线）**：`docs/known-defects.md`（**开工即脏 M：T1 KD-64 + 本批 KD-66~71；整文件纳入**）、`docs/agent-first-refactor-decisions-2026-09-23.md`（**开工即脏 M：T7 G7 登记 + 本批四项裁定；整文件纳入**）、`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md`、`docs/REFACTOR-MASTER-LINE.md`
- **块 14（本记录）**：`docs/research/2026-10-25-batch-21-record.md`

**明确排除**（他线在飞，一律不动）：`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt`（他线动画引用未跟踪 `core/ui/ClaudeMotion.kt`）；app 其余开工即脏 M（LearningMasteryScreen/ReviewReminderCoordinator/ReviewReminderScreen/SchedulingSettingsScreen/StartupStateBanner 等）；`SecondaryScreens.kt`（T7 已停手，不纳入本批）；feature/tutor|library|review|capture 全部；core/ui 全部（含 `ClaudeMotion.kt` 与 androidTest）；core/domain 他线 4 M（`BatchImportRepository.kt`、`MistakeDetailRepository.kt`、`SchedulingEvaluation.kt`、`TutorJudgedReview.kt`）；core/data 他线 M（`RoomMistakeDetailRepository.kt`、`RoomTutorToolRunner.kt`）与 8 个测试面 M；core/database 8 M；core/export 5 M；`settings.gradle.kts`；`.gitignore`；docs/current 其余 7 M；docs/research 他线未跟踪记录（`2026-10-26-kb-*`、`2026-10-25-ui-completion-audit.md` 等）；docs/superpowers 他线 4 M 中非本批文件；`tools/kb*`、`knowledge-production/**`；`build/` 设备/门禁日志与 `tmp/verify_batch21.py`（临时脚本，不入提交；若主会话要保留可复跑证据，建议移到 `tools/ci/` 后再提交）。

**安全边界**：本文件不含任何密钥/令牌/凭据；Mimosa 无完整扫描结论，不宣称项目安全；提交分块建议由主会话执行，非快进 merge 不 rebase。

## 附录：本记录员实际执行的只读核对（可复跑；未运行 Gradle/设备/任何 git 写命令）

```bash
# 计划/基线/批20记录/五研究文档全文或头部读取（Read/sed）
git rev-parse HEAD origin/main
#   → 两值同为 2051748bd6eaee54ba003ffcaba848e5881c2d3e
git diff --stat ce01508f..HEAD
#   → 4 docs 文件 +849/-0（KB 他线 docs-only，无代码/schema）
git status --porcelain=v1 | wc -l            # 699
git status --porcelain=v1 | grep -c '^ M'    # 228
git status --porcelain=v1 | grep -c '^??'   # 471

# schema/请求 schema 干净性
git status --porcelain -- core/database/schemas/ \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt \
  core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt \
  app/src/main/AndroidManifest.xml
#   → 无输出；StudyDatabase.kt:134 69 / ModelTasks.kt:546-547 18

# 逐文件 git status（T1/T2/T3/T5/T7 文件清单来源）
git status --porcelain -- core/domain core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadata.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureAigcXmpTest.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadataInstrumentedTest.kt \
  gradle/libs.versions.toml core/data/build.gradle.kts .github/workflows/android-check.yml tools/ci/check_dependency_pins.py
#   → 全部与本记录 §3/§7 清单一致；core/domain 另有 4 个他线 M 未纳入

# 测试工件（JVM；非 Gradle 门）
find core/data/build/test-results -name 'TEST-*.xml' | grep -E 'AdaptiveSelection|AigcXmp|QuickJs'
grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  core/data/build/test-results/testDebugUnitTest/TEST-com.tingyun.smartmistakebook.core.data.study.AdaptiveSelectionAssemblerTest.xml \
  core/data/build/test-results/testDebugUnitTest/TEST-com.tingyun.smartmistakebook.core.data.capture.GeneratedFigureAigcXmpTest.xml \
  core/data/build/test-results/testDebugUnitTest/TEST-com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineJvmTest.xml \
  core/domain/build/test-results/test/TEST-com.tingyun.smartmistakebook.core.domain.EloCorrectnessPredictorTest.xml \
  core/domain/build/test-results/test/TEST-com.tingyun.smartmistakebook.core.domain.NewIntroductionPolicyTest.xml
#   → 10/9/2/14/13，全 failures=0 errors=0（2026-10-26 05:20–05:22）

# 依赖钉版门（实跑；非 Gradle）
python tools/ci/check_dependency_pins.py
#   → OK: quickjs AAR/POM checksums and resolved runtime pins match; EXIT=0
python tools/ci/check_dependency_pins.py --expect-kotlin-runtime 2.3.21
#   → FAIL: POM requires kotlin-stdlib 2.4.10... / FAIL: resolved ... != pinned 2.3.21; EXIT=1

# T4 机制测试（实跑）
python -m pytest tools/figure_eval/test_figure_eval_gate.py -q
#   → 30 passed in 0.16s; EXIT=0

# 设备轨日志实读（未复跑）
ls -la build/device-track-batch21
tail -20 build/device-track-batch21/step1-kd64-run1.log          # OK (3 tests)
grep -E 'OK \(|FAILURES|INVALID_PROGRAM|MEMORY_LIMIT' build/device-track-batch21/step2-quickjs-permethod.log
cat build/device-track-batch21/step3-16k-quickjs.log             # 3 × OK (1 test)
cat build/device-track-batch21/step3-16k-nativeload.log           # OK (2 tests)
cat build/device-track-batch21/step4a-migration-d1.log            # 15 + 6
cat build/device-track-batch21/step4b-backup-four.log             # 34+3+10+9
grep -n 'FATAL EXCEPTION\|NoSuchMethodException' build/device-track-batch21/step5-r8-logcat.txt
grep -c 'FATAL EXCEPTION' build/device-track-batch21/step5-r8-release-logcat.txt   # 0
find build/device-track-batch21 -iname '*GeneratedFigure*'        # 零命中（T5 设备未跑）
grep -rl '16384' build/device-track-batch21                       # 零命中（16KB 页断言无日志）

# 关键源码锚点
grep -n 'allOversized\|grantedSliceSeconds' core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/NewIntroductionPolicy.kt
#   → allOversized :143；granted :181；require :80
grep -n 'predictionModelVersion' core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AdaptiveQuestionSelector.kt
#   → :132 / :303 / :310
grep -n 'issue240\|KnownOpen\|CountDownLatch' core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineInstrumentedTest.kt
#   → watchdog 形状 :305-347
grep -n 'declaresAigcNamespaceInScope' core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadata.kt
#   → 命名空间精确校验 :226-250
```

> 未执行：任何 `./gradlew`、`adb`、`am instrument`、`connectedAndroidTest`；任何 git 写命令。门禁/设备/研究结论均标注来源（工单材料/设备轨 evidence/修复环材料/研究文档），不冒充本记录员实跑。

## 8. 主会话收口（2026-10-26 凌晨，实跑证据）

- **单测门**（11 模块 `--continue`，主会话实跑）：**exit=0**（BUILD SUCCESSFUL，1 executed / 226 up-to-date——批次 21 修复环后的树未再变，core:data 测试任务重跑通过；本轮无他线红）。
- **强门**（六 androidTest 编译 + `:app:assembleLocalFirstDebug`）：**exit=0**（BUILD SUCCESSFUL，82 executed / 189 up-to-date）。
- **提交**：`1a6bf486`（41 文件，16,097 插入/2,203 删除），按 §7 的 14 块分块 add；排除项未触碰（Root、feature 四模块、core/ui、SecondaryScreens、settings.gradle.kts、docs/current 其余等）；`tmp/verify_batch21.py` 未入提交（临时复核脚本，69/69 为工单材料）。
- **推送**：`2051748b..1a6bf486` 已上 origin/main（首次尝试成功）。
- **本批不声称全绿**：设备轨 QuickJS 真引擎部分红（KD-66 issue #240 挂死、KD-70 OOM 分类），T3 维持 partial；KD-67/68/69/71 保持 open；T5 四仪器化用例与 16KB 新断言未设备执行；JS 仍不广告；真实 Provider UNVERIFIED。
- Mimosa 提交仍无完整扫描结论（scanner_enobufs）——不宣称项目安全。
