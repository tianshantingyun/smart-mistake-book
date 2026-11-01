# 批次 39 记录（2026-11-01）：接线与设备校准批——D-04 预览接线缝 + M-03 16MiB 分配前门 + V-01④ 公开测试缝 + KD-76 计时 seam + P-11 errorKind runner 半边 + 设备向量（N5-N7 / X-02 / X-03 / A-10 协调器级）+ 两研究 + 设备 28 套件 236 例（绿终判 26 套件 220 例）

> 记录时点：2026-11-01（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 写入，依据：计划 `.zcode/plans/plan-2026-11-01-batch-39-wiring-calibration.md`（**37 行，本会话完整实读**）、批次 39 基线（`docs/research/2026-11-01-batch-39-baseline.md`，**165 行**）、两研究文档（R1 预览接线 `2026-11-01-preview-wiring.md` **366 行** / R2 计时校准 `2026-11-01-kd76-timing-calibration.md` **252 行**，行数本会话 wc 实读；节锚 grep 实读——R1 §0:8/§1:29/§2:82/§3:204/§4:251/§5:303/§6:325/§7:336，R2 §0:10/§1:20/§2:45/§3:119/§4:159/§5:193/§6:209/§7:222/§8:231）、批次 38 F.20 预研（`2026-11-01-kd76-ready-timeout-calibration.md`，134 行，被 R2 四阶段设计细化覆盖）、设备证据 `docs/research/evidence/2026-11-01-batch-39/`（**10 个 .md**，本会话 cat|wc 实测 **63,651 B**；设备轨工单快照 48.9 KiB、00-index 落盘员复核自记 57,104 B——差异=落盘后 §4.1/§9/§10 增补，如实登记）、各轨工单摘要（转录）、复核发现 10 条与修复环第 1 轮处置。**记录员未运行 Gradle、未执行任何设备命令、未做任何 git 写操作、未改任何 Kotlin/JS/Gradle 产品代码**；本轮改动 = 本记录 + 四件台账同步（§附录 A；其中 `known-defects.md` 与准入清单为共享脏文件——开工时已 M〔台账轨补记 17/18 + 复核修复轮回填在飞〕，整文件纳入并注明）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 2 轮 `exit=0` → 修复环第 1 轮（非低发现 5/5 处置：3 修复 + 2 验证确认不改，§5.0）→ 单测门复跑 `exit=0` → 强门（修复环后）`exit=0` → 修复环后强门复跑 `exit=0`。**逐套件出数未随工单传入**——本批新增 JVM 套件（渲染器 9 例 / 计时 2 例 / RoomTutorToolRunnerTest·TutorToolTraceTest 增例）的逐套件断言面 UNVERIFIED（门级 exit=0 工单给定；记录员禁 Gradle 未复跑）。
> **本记录不声称"批次全绿"**：设备绿终判 26 套件 220 例，但 **isolation 三窗 15 例红未收敛**（D 环境主导、T4 关联不可证伪，阻塞上报主会话——R2 §7.6"seam 设备绿绑定"当前不成立）；**X-03 0/1 红 = A 类夹具缺陷**（修复+复跑欠账归落码批）；**KD-76 校准 UNVERIFIED**（设备校准用例未落码、androidTest 0 引用，出数结构性不可执行）；**D-04 生产装配零调用方**（previewAssetId 生产恒 null，"生产接线"任务实为接线缝+向量落地）；V-01 状态判定归主会话；KD-72/76/82 open 维持；JS 仍不广告（`availableMethods` 未动、生产装配恒三参）；4E.2 MVA 不成立；生产签名/arm64/16KB/真机 UNVERIFIED。
> 计划：批次 39 = **16 项 → 10 轨**（计划 §1：A 接线 4〔D-04 生产接线 / M-03 16MiB 接线 / P-11 errorKind 半边 / V-01④ seam〕/ B 设备校准与向量 6〔KD-76 seam + 校准 / A-10 协调器级 / X-02 / X-03 / Q-04 设备半边〕/ C 环境与台账 6〔KD-72 复核 / A-12·A-11 回归 / 台账回填 / V-02 裁定登记 / 热区条件轨 / S-02·S-09 核验〕）。

## 1. 范围与基线

### 1.1 计划范围与实际执行面

- **实际执行面**：两研究落盘（R1/R2）+ 七轨（T1 设备判据 / T2 D-04 接线 / T3 设备向量 / T4 计时 seam / T5 P-11 / T6 台账 / T7 条件轨）+ 集成交接（零接线，【无则明说】分支）+ 设备轨四步（28 套件 236 例）+ 证据落盘（10 .md）+ 复核轨（10 条发现，5 中 5 低）+ 修复环第 1 轮（5/5 处置）+ 本记录与台账同步。
- **与计划的偏差（全部显式登记）**：
  1. **计划任务 1"D-04 生产接线"实为"接线缝+设备向量"落地，生产装配点不存在且不可自行落**：生产 main 对渲染端口零调用方（`renderPreview`/`TutorVisualPreviewRendererFactory.create` 排除渲染器自身后 0 命中，00-index §10.2 本轮实跑）；生产装配点 = `SmartMistakeBookApplication.kt:364-368`（恒三参、`visualEngine` 默认 null）与 `DefaultFigureGenerationCoordinator`（他线在飞 M）——给生产造装配点会翻转 JS 能力广告，撞"广告开关/availableMethods 零改动、设备验证前 JS 不广告"红线。集成轨走【无则明说】分支（§3.8）；唯一真实插入点 = app ④ 设备例的 commit 路径（T2 已接线演示）。索引 §4.1 #1 由 ✅ 改判 ◐（不构成关闭建议）。
  2. **X-03 设备向量 0/1 红 = A 类夹具缺陷（确定性）**：期望值锚错 id 空间——夹具输入 `TutorVisualProgramInput` 六字段无 id（`StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt:165-173`），仓储落库 id 为内容六字段派生形 `visual-program-<64hex>`（`TutorVisualIdentity.kt:34-63`），断言 `:219-223` 把协调器派生式 `visual:{operationId}:{sha16}` 与之划等号，结构性不可能成立；两窗逐字节同红。**处置 = 不动夹具**（复核修复轮 §10.4：单落修复半会使已记录红窗与树脱钩），修复+复跑欠账归落码批同窗完成；X-03 判据语义（①档/0 派发）无设备证据。
  3. **isolation 三窗红未收敛（本批最大未决）**：`VisualJsIsolationInstrumentedTest` 三窗 15 例 6/7/5 红 FAIL，红形单一（帧执行超 1500ms 服务端 watchdog 杀→binder died/级联）、kill 计数 10/10/6（设计基数 5）、红例集合三窗各异（高方差）、同会话其余 199+ 例全绿——D 环境主导；T4 关联机制上不可能（kill 在服务进程侧，T4 只改宿主 6 处 volatile 打点）但设备轨禁改他线在飞文件无法做去 T4 控制轮 → 按 R2 §7.6"seam 设备绿绑定"当前不成立，裁定请求（低负载窗重跑 / T4-reverted 控制轮 / 接受 D 归因）已按 E-06 §2 上报主会话（§4.3）。
  4. **T3 轨落码内修正（本批新测试落树内，非放宽既有门）**：X-03 初稿中一条会假红的场景不等式断言被撤换为"帧到达序列 + 两帧均 ≠ 快照场景"的更强诚实断言（夹具 program 不消费 timeMs，播放帧场景差异无可断言面——KDoc 登记边界）。
  5. **三处申报数字更正（复核 #3 独立复核；本会话逐一复测同值）**：T2 申报渲染器 JVM"8 例"实为 **9**（`grep -c '@Test' TutorVisualPreviewRendererTest.kt` = 9）；集成申报引擎 diff"+156/−7"实为 **+150/−6**（T4 本轨申报正确；`git diff --numstat` 实测 `150 6`）；T1 申报 R2"253 行"实为 **252**（wc 实读）。
  6. **KD-76 校准出数（计划任务 6 半边）结构性不可执行**：T4 seam 已落码，但经 seam 的设备校准用例全仓 androidTest **0 引用**（E-08；本会话 grep 复核同值）→ 出数不可执行，READY_TIMEOUT 维持 10s；KD-76 open 维持。
- **不做（全部未做）**：JS 广告与 `availableMethods`（接 visualEngine 进生产装配会翻转广告，红线）；生产装配接线（归主会话裁定）；R-04 keep 行登记（`app/r04-test-keep.pro` clean 实证——缝/渲染器 keep 义务移交 R-04 批，此前引用用例不得进 release 轨清单）；契约 `:208`（docs/current 7 M 热区未清空，登记不落笔）；锁文件提交（KD-72 分支 B）；`connectedAndroidTest`；真实 Provider；任何 git 写。
- **能力快照 / prompt / 广告文本零改动（沿裁定）**：本批 4 个改文件 diff 与 2 个新 main 文件关键词扫描（`availableMethods|FigureGenerationCapabilities|广告|@Entity|@Database|CREATE TABLE|ALTER TABLE|MIGRATION_`）= 0 命中（集成轨实跑转录）；`availableMethods` 唯二装配点 clean（`FigurePromptAssembler.kt:105/:201` 与 core/model `FigureGeneration.kt:161` 仍 `setOf(IMAGE_MODEL)`，生产恒 IMAGE_MODEL_ONLY）。
- **DB schema 保持 71**：`StudyDatabase.kt:139` `internal const val STUDY_DATABASE_VERSION = 71`（本会话实读）；本批申报文件无 core/database 文件、无 schema 关键词命中（集成轨实跑转录）。

### 1.2 沿用裁定与纪律（本批执行，未改）

1. **热区禁改**：feature/tutor、feature/library、feature/review、feature/capture、feature/profile；core/ui 全部；core/markdown 整目录；core/export 全部；core/domain 开工即脏 M；core/data 他线 M（dense/knowledge/`RoomMistakeDetailRepository`/`RoomTutorToolRunner`/SafeTruncation/TutorToolDescriptions 面）；settings.gradle.kts；.gitignore；app Root.kt 与开工即脏 M；docs/current 其余 M；docs/research 他线未跟踪记录；tools/kb*、tools/dense_build、tools/kb_coverage；knowledge-production。例外登记：T5 轨开工实测 `RoomTutorToolRunner.kt` 已清空（基线 §2 同判），按计划任务 3 条件分支"若已清空则落"执行——该文件现 M 即 T5 落码内容（复核 #1 独立复核四 diff 零外来 hunk）；`VisualJsClientEngine.kt` 非热区（开工实测干净，会话中途 T4 落码变 M——T1 漂移登记，源快照照常记哈希绑定窗口）。
2. **能力快照 / prompt / 广告文本零改动；DB schema 71；广告开关/availableMethods 零改动**（§1.1 实证）。
3. **R-04 判据不放宽；Keeper 推迟 settings.gradle.kts 热区**；缝/渲染器 keep 义务合并登记（R1 §2.7/§4.2 决策输入 3），keep 行落地前引用两者的 release 轨用例不得进 R-04 清单（缝 KDoc 约束 2 + `tools/ci/check_visual_test_seams.py` grep 门三处在案）。
4. **设备绿绑定源快照（含 core/model）**：构建窗 1227 文件 diff=0（E-01）；新增用例必须重跑；修复环后复跑强门（工单序列 §6）。
5. 锁文件与 `tmp/` 不提交；禁 `connectedAndroidTest`；**commit 前逐路径核对暂存区**（`git diff --cached --name-only` 对照申报清单，不只数数量——批次 37/38 §10.1 两次卷入事件的加固条款）。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`684adb6eb646f0f1b62f2cb0b367c3be21607a8e`（本会话 `git rev-parse HEAD`；批次 39 开工时点 = `b95451c9`〔基线轨〕→ 判据轨 `a0641bb7`，落盘时点他线 KB docs 提交前移至 `684adb6e`——00-index §8 判例：不改工作树字节，源快照窗口绑定不受影响）。
- **工作树**：**784 条**（`git status --porcelain | wc -l`）、暂存区 **0**（本会话实跑）——批次内滚动读值：基线 712 → 判据/落码轨 719 → 台账 722 → 设备窗 737 → 记录时点 784，多线在飞持续滚动，差值未逐项追认 UNVERIFIED。
- **DB schema**：71；**KD 台账**：82 条（KD-72/76/82 open 维持）。
- **docs/current**：7 条 M（热区未清空 → 契约 `:208` 维持登记分支不落笔，台账轨附录 I.3 同集合）。

## 2. 两研究线

### 2.1 R1 预览接线研究（`2026-11-01-preview-wiring.md`，366 行，驱动 T2/T3）

零代码改动/零 Gradle/零设备/零 git 写，全部行号本会话实读；四任务结论（工单转录 + 节锚实读）：

- **任务一 D-04 预览接线设计**（§2）：渲染输入 = `Rendered.drawCommandsJson`（引擎已过 `VisualDrawOpsValidator` 复验，core/domain `VisualExecutionEngine.kt:236-239`）→ `VisualDrawOpsParser.parse` 恢复级复检 → `VisualDrawOpsPainter.draw`（pxPerDu=预览自定界 `min(1f, 1600px/maxDu)`，**不取设备 density 以保内容寻址幂等**）→ `Bitmap.compress(PNG)`；labelContent 由 public 的 `VisualMeasuredLayoutArtifact` 折算（复用 M-01/replay 批同一折算，不造第二套行段规则）。**时点**：仅 `commitAssistantTurnWithVisual` 路径（不进 stageSnapshot——STAGING 行 60 分钟租约会被回收）；顺序=渲染→Persister.persist（字节→行原子）→ record.sourceAssetId 填 previewAssetId → 仓储提交；不在仓储/引擎内部做（DB 层纯事务、JS 事务不掺位图）。**失败语义=有界降级**：门拒/绘制拒/persist 拒一律 previewAssetId=null+有界诊断+快照照常提交（依据：预览是③档显示缓存——`VERIFIED_FONT_MANIFESTS` 空集 `VisualSnapshotRestore.kt:125` 使今天每次冷恢复都是 ReadOnly，预览是唯一可见途径；快照完整性靠 drawCommands 不靠预览）；CancellationException 重抛；降级自愈=未引用资产行由六站点谓词 GC 回收（`CanonicalAssetReferenceSites.kt:31-54`）。**16MiB 分配前拒绝** = `requireBitmapBytesWithinBudget(wPx,hPx,4)` 在 createBitmap 前；策略上限 ≤2048px（2048²×4=恰达 16MiB），建议 1600px 沿 `BoundedLocalImage.MAX_PREVIEW_EDGE` 先例（1600²×4=10.24MB 留 36% 余量）；vault 三闸自洽。"文字+预览合计≤4MP"裁定在**预览生成侧**判（不在引擎结算处并入——4096² 合法画布否则被显示缓存问题连杀整图）；度量总数经通道甲（缝接受 `List<VisualMeasuredContent>` public 类型，暂传 null 则合并语义如实登记待接线）/通道乙（core:data 侧度量器 recording 装饰器，并发模型 UNVERIFIED）。
- **任务二 N5/N6/N7 判据**（§3）：测试位=core/data androidTest（同模块 internal 直达，零 seam）；全程形归 app V-01 链套件。N5：viewport 4096²du → 预览 du 面积 4× 预算 → `LAYOUT_PIXEL_BUDGET` 前缀+零分配+快照 LIVE 且 preview_asset_id NULL（防漂移钉：静默缩采样会让码与 null 断言同时红）。N6：2050²×ARGB_8888=16,810,000B>16MiB → `BITMAP_BYTE_BUDGET` 前缀+无 OOM+无位图句柄（向量对象是门不是策略）。N7：viewport 2048²du（独判恰达不拒的对照半例）+≥1 真实/合成度量文本盒合计越界 → 拒且只拒预览。N5(du 累计)/N6(单次分配) 分界=`VisualLayoutBudgetTest.kt:42-50`"防发现等价"的设备镜像。
- **任务三 V-01④ seam**（§4）：批次 38 T10 前提逐条复核仍成立（7 类 internal 符号未变、`VisualChainTestSeams` 不存在、testFixtures 三构建文件 grep ABSENT、app 3 例/变体 4 例、`r04-test-keep.pro` 在树）；维持方案 A（单新文件公开缝），三条新决策输入：androidx workmanager-testing TestDriver（gh 实查）是公开缝权威先例但其独立 artifact 形态需 settings.gradle.kts（热区）→ 反向支持 A；T2 最小形向量不需 seam → A 可让序不阻塞 N 判据；预览渲染器与缝的 R-04 keep 义务合并登记。回滚三段式：删 1 新文件+还用例增量（grep 门即回滚净度机检）。
- **任务四 外部参考**（§5）：gh api 实查元数据——Coil（Apache-2.0/11,956★/活跃）4.5/5 策略参考采纳；Glide（NOASSERTION/35,008★）4/5 备选；androidx TestDriver 5/5 形态权威不搬模块形态；SubsamplingScaleImageView（Apache-2.0/8,009★/2024-04 停更）2/5 不采纳；官方 Bitmap.Config 整页实取（ARGB_8888 "Each pixel is stored on 4 bytes" 逐字）→ 关闭 R2 §6 待验证项。**通道故障如实记录**：WebFetch developer.android.com 超时→换 web-reader 成功；gh search 两查零命中→换 gh api 成功。研究自报验证 20+ 项（grep/git status/gh api/官方文档实取）；N5/N6/N7 执行与缝签名编译 UNVERIFIED 归门禁+设备轨（doc §6 U1-U6）；修正记录：VisualSnapshotRestore 三处行号引用初稿有误，已按 grep 实查修正为 :100-125/:125/:147-152。

### 2.2 R2 KD-76 计时校准研究（`2026-11-01-kd76-timing-calibration.md`，252 行，驱动 T4/T5）

- **seam 设计**（§2）：`connectStageTimestampsForTest` = 按 generation 记账、挂在 Link 上的 **5 时刻戳/4 阶段段**——S-bind（bindService→markConnected 同锁发布）、S-warmup（连接发布→isReady 首真，客户端投影段，与 E-04 服务端口径相减即得 E-04 :34 明示缺失的 bind/握手排队段）、S-ready-gap（ready→execute ACCEPTED）、S-firstbyte（ACCEPTED→readResultPayload :551 首字节）；5 写点全在 `VisualJsClientEngine.kt` 内、@Volatile 单写者零新增锁、零生产行为改动。
- **READY_TIMEOUT=10s 校准判据**（§3）：主口径=经 seam 的 bind→ready 段、真实冷启动轮（adb reboot→sys.boot_completed=1 后立即 am instrument，不沉降）n≥10 报 p50/p95/max；维持判据 p95≤10s×1/2（现状 E-04 余量≈14×/churn 尾≈5.7×，known-defects:2897），超线只动单常量 :1251 并回写契约 :72，loading 语义/1500ms/2500ms 红线零触碰；注入形状零新增（DelayedAttach/NotReady 两既有夹具只管行为分支不产数字）；条件矩阵 C1–C4 沿批 38 预研。**anti-flaky 全套沿仓内 KD-66 三轮设备绿先例**（归因先于计时、单钟 uptimeMillis 相减、无扣除、相对门、n≥10 分位数、logcat 交叉归因）。
- **29.5s 链**（§4）：分段实测相加（S1+S2 冷启动 seam 实测、S3 沿 KD-66 hard+500 已绿门佐证、S4 沿 abandonForTest 路径），**明确否决单例端到端故障链**（四段满额互斥：attach 满额后同 link 不再有 ready 等待，拼单例是夹具接力非真实形状）。
- **参考评分**（§5）：KD-66 用例（最高）> macrobenchmark 方法论（中高，只借统计口径不引模块）> Chromium ChildProcessService（中，R1' 已引先例）> SystemClock 官方（中）> awaitility（**低，不引入**：与 awaitConnectedForTest/awaitReady 既有惯用法重复，指认不出新消灭的失败，且 instrumented 面兼容性摩擦单源待验证）。

## 3. 实施轨 + 集成 + 设备（逐轨）

> 判定口径：**落码成立** = 改动在当前树且记录员静态核对属实（git 状态/diff 规模/@Test 计数本会话逐一实跑，§10）；执行证据分列（设备实跑=E-ID / 门禁工单给定 / 无证据=UNVERIFIED）。

| 轨 | 工单状态 | 本记录判定 | 详节 |
|---|---|---|---|
| T1 设备判据 | 完成 | **判据+命令清单落树，零设备执行/零 Gradle/零 git 写/零热区编辑；执行归设备轨** | §3.1 |
| T2 D-04 接线 | 完成 | **5 新文件+1 改落树（渲染器/缝/JVM 9 例+设备 4 例/V01 3→4 例/门脚本）；生产装配点不存在（不虚报"已接线"）** | §3.2 |
| T3 设备向量 | 完成 | **3 新文件 1,902 行 8 @Test 落树；设备 7 绿 1 红（X-03 A 类）** | §3.3 |
| T4 计时 seam | 完成 | **引擎 +150/−6 + JVM 2 例落树；设备校准出数 UNVERIFIED** | §3.4 |
| T5 P-11 | 完成 | **四文件 +98/−10 落树（开工实测 runner 干净→条件分支"落"）；JVM 执行 UNVERIFIED** | §3.5 |
| T6 台账 | 完成 | **清单补记 17/18 + known-defects KD-72/76 两块 + 附录 I；契约 :208 不落笔** | §3.6 |
| T7 条件轨 | 完成 | **分支 D 九项只读复核零落盘；新漂移仅 AUD-08 锚 +1** | §3.7 |
| 集成 | 完成 | **【无则明说】分支：零文件编辑；D-04 生产装配点确不存在；import 0 悬空；零广告/零 schema 确认** | §3.8 |
| 设备 | 完成 | **四步串行；E-00…E-08；绿终判 26 套件 220 例；isolation 三窗红未收敛阻塞上报** | §3.9/§4 |

### 3.1 T1 设备判据（只产出判据与命令清单）

六项判据全部锚定现树源码与既有判据文档；计划与三份判据文档（preview-wiring 366 行 / KD-76 批 38 预研 134 行 / KD-76 批 39 R2 253 行〔实为 252，§1.1 偏差 5〕）完整实读。要点：判据组 A 预览行落库 5 断言（① status=LIVE 且 preview_asset_id=canonical 资产行 id 且 `source_type=LOCAL_VISUAL_PREVIEW`（`StudyDbValue.kt:58` 共享常量禁字面量）② RESTRICT FK 漏登记=整事务回滚 ③ 幂等 INSERT OR IGNORE 同内容 sha 同一行 ④ 真实重开库→③档 ReadOnly→显示预览 PNG（D-04 消灭的失败本体"冷恢复只剩文字"的设备钉）⑤ 备份/恢复 C4 ⑥⑦⑧ ride-along 不回退）；判据组 B N5/N6/N7（两门独立判、向量分开写不得合并——`VisualLayoutBudgetTest.kt:42-50` 防发现等价的设备镜像）；KD-76 四阶段时间戳/READY_TIMEOUT 校准/29.5s 分段方案与 anti-flaky 六条；X-02 五向量（预算不足臂复用 A-10 设备位不重复建门层用例）；X-03 ①档 0 远程派发三断言位；V-01④ 条件分支判据（seam 未落时严格口径维持 PARTIAL 3/4，不得以降级变体 4 例替代——其 2/4 已知红）；20+ 套件不回退底册（批 38 设备 24 套件 223 例，逐文件 @Test 计数核对相符）+ 重建重装硬门 9 步命令原型（批 37 判据 :342-451 + 批 38 E-01 实证）。**漂移登记**：`VisualJsClientEngine.kt` 开工时干净、会话中途变 M（T4 在飞）——该文件非热区、落码合法，按纪律整文件纳入并注明；落码后行号锚平移以常量名为稳定锚。全部设备执行本轨未跑 = UNVERIFIED 归设备轨；无阻塞项。

### 3.2 T2 D-04 接线（任务 1/2/4 + V-01④ seam 落码）

新增 5 文件 + 改 1 文件，零热区触碰、零 git 写、零 Gradle（纪律）：

- **`TutorVisualPreviewRenderer.kt`**（??，383 行，本会话 wc）：public 端口 `TutorVisualPreviewRenderer` + internal 机 `TutorVisualPreviewRendererImpl` + 公共工厂 `TutorVisualPreviewRendererFactory`（forProduction 组合 `TutorVisualPreviewAssetPersister.forProduction`，沿 FigureGenerationCoordinationFactory 先例）。管线=R1 §2.1 八步：4MP 准入（纯计算）→ `VisualDrawOpsParser.parse(rendered)` 恢复级复检 → math 盒边界（artifact 不携 MathBox，折算前置判 `PAINTER_LABEL_MATH`）→ artifact→`VisualLabelContent` 单一折算点 → pxPerDu 定界 → 16MiB 门（**`:332` 调 `requireBitmapBytesWithinBudget`，createBitmap 前**——复核修复轮注记在树）→ 分配/绘制/PNG 压缩 → Persister.persist → Accepted。失败语义=R1 §2.3 全表：有界失败折 Refused（code+有界诊断≤512）、快照照常提交、CancellationException 重抛、OOM 兜底折码。pxPerDu=`min(1f,1600/maxDu)` 不取设备 density；常量 1600 带 2048 硬上界 require。**生产装配点（SmartMistakeBookApplication/Coordinator）是他线热区零调用方——不虚报"已接线"，缝为独立可接线单元**。
- **M-03 16MiB 接线**：门接在 renderBounded 第 5 步 createBitmap 之前（IAE→Refused(`BITMAP_BYTE_BUDGET`)，分配不发生）；策略第一道+门第二道。至此 M-03 两门全接线（4MP=引擎结算处批 38、16MiB=预览分配点本批）。
- **`VisualChainTestSeams.kt`**（??，196 行，方案 A 单新文件）：`restoreDecision`（行→`VisualSnapshotRestoreReadSide.restoreRecordOf` 唯一权威映射→`VisualSnapshotRestore.restore` 原序→公共投影 Replay/ReadOnly）+ `writeSnapshotPdf`（parse→writeFile）；不透出任何 internal 类型；KDoc 写死三硬约束（仅 androidTest 引用 grep 门 / R-04 keep / 不透 internal）。**grep 门脚本 `tools/ci/check_visual_test_seams.py`（??，158 行）——本会话实跑 `python -I tools/ci/check_visual_test_seams.py` → `OK: VisualChainTestSeams has 0 main/test references and 1 androidTest reference(s)`、EXIT=0**（复核轮负控 FAIL/exit=1 转录在案）。`r04-test-keep.pro` 未动（keep 义务移交 R-04 批登记——复核 #8 确认零命中且文件 clean，非违规但 KDoc"一并登记"表述易误读，登记项维持）。
- **测试**：JVM **9 例**（`TutorVisualPreviewRendererTest`——N5 JVM 半边零写入口、N7 口径半边恰达/+1du²/通道甲退化、解析复检、math 边界、定界策略密度无关+1600 上限同源核对、请求校验；申报 8 系欠报，复核 #3 更正）；设备 **4 例**（`TutorVisualPreviewRendererInstrumentedTest`——N5 4096²→LAYOUT_PIXEL_BUDGET+零写+快照 LIVE 且 preview_asset_id NULL 防漂移钉、N6 门直调 2050²×4=16,810,000B 逐字对齐 JVM+注入 pxPerDu=2050/2048 证明门在分配前、N7 2048² 恰达不拒真渲染真 persist 真提交→Accepted+引用落库 vs +1du² 拒且只拒预览、取消重抛）；app V-01 套件 **3→4 例**（④ 例：预览全程形〔生产 vault 写侧→previewAssetId 落库→真实重开存活→`source_type=LOCAL_VISUAL_PREVIEW`〕+restore 双档〔注入清单 Replay/默认空清单 ReadOnly+previewVerified=true 时预览 id 透出〕+PDF 全腿〔PdfRenderer 单页/页尺寸/非空白/圆心路径墨〕；断言只用缝公共返回值+PdfRenderer 公共 API+像素数组）。
- **每新增物消灭的失败（12.2，KDoc 在树）**：端口=六步自拼漏步；分配前门=OOM 进程死亡；准入=满幅预览畅通；策略上界 require=静默永久降级；缝=④腿跨模块不可达；grep 门=生产旁路幽灵门；N 向量=分配前门无设备执行面+两门发现等价设备镜像缺失；app ④=冷恢复只剩文字。

### 3.3 T3 设备向量（任务 7/8/9）

3 个新文件（??；**1,902 行 / 8 @Test**，本会话 wc/grep 同值：跨方法 554 行 1 例、X-02 642 行 6 例、X-03 706 行 1 例），全部经 `DefaultFigureGenerationCoordinator` 全路径 + 真 Room 账本/审计；夹具逐型复用批次 36/38 实测件。未运行 Gradle（纪律）；以仓内 Gradle 缓存的 kotlin-compiler-embeddable 2.3.21 做了**离线类型检查（非门禁、非测试执行）**，三文件编译 EXIT=0。

- **任务 7（A-10 协调器级跨 method 合计，1 例）**：同一 operationId 先 JS（真 QuickJS 引擎真实渲染产出第 1 份，账本零触碰）后 IMAGE（真账本 SQL 预留产出第 2 份），第 3 份 IMAGE 在协调器 `admitFinalFigure`（`DefaultFigureGenerationCoordinator.kt:218`）被拒；钉四计数位+真库持久痕迹（attempt 仍 2 行、operation 仍 1 行 SUCCEEDED 1/1）+拒绝文案（"已产出 2 份最终图（上限 2）"）。与批次 38 基线 §10.3 一致：落协调器层、不重复门层（基线 §10 不符注记 3 同判）。
- **任务 8（X-02 设备向量，6 例）**：清单 :241 五名逐一落地——缺源（EDIT 无源字节→"图像编辑必须带已授权的源图字节"）、错图（资产 id ≠ 冻结 canonical 引用）、SHA 变化、授权不足（allowsImageDisclosure=false→"未授权披露原图"）、预算不足（先经生产账本 6 次真实预留耗尽，协调器授权完整的派发被真 BUDGET_EXHAUSTED 拒，FAILED 带"已派发 6"+"未出网"）；①–④ 共用"真库零写入"判据（operation/attempt 两表 0 行、dispatch_count 不动、通道/后端 0 次）；正控例钉"已发送"持久孪生（通道实收原字节、`outcome.sourceSeen.sourceSeenDispatchId==attemptId`、attempt 行 reservation_id 非空且 source_sha256==出网字节摘要）。Plan 侧缺口（`TutorQuestionContext` 无原题资产字段）维持 feature/tutor 热区禁改、登记不落码。
- **任务 9（X-03 strictOffline ①档交互重布局，1 例）**：真实链提交→真实 close+同名重开（冷恢复）→从 `tutor_visual_program` 行读回构造 `PersistedVisualProgram`→`StrictOfflineVisualRelayout.decide` ①档放行→协调器 JS 路径两帧交互重布局（拖动换视口、播放推进 timeMs）→双 GENERATED、源码逐字节=持久行原文（判定输出+引擎实收双重核对）→0 派发持久 witnesses（dispatch_count 不变、openBackend tripwire、operation 0/0、2 行 JS attempt 均 reservation_id NULL）。登记边界：夹具 program 不消费 timeMs，播放帧场景差异无可断言面，以帧到达序列为证（撤掉会假红的场景不等式断言，§1.1 偏差 4）。**设备执行 0/1 红 = A 类夹具缺陷**（§1.1 偏差 2；修复+复跑归落码批）。

### 3.4 T4 KD-76 计时 seam（任务 5）

`VisualJsClientEngine.kt` **+150/−6**（本会话 numstat 同值；开工前该文件干净实测为空、非热区）：

- `ConnectStageTimestamps` 顶层 internal 类（文件尾，与 VisualJsResultFrameFilter 同体例）：5 个 @Volatile 时刻戳（bindRequestedAt/bindConnectedAt/readyAt/executeAcceptedAt/firstByteAt）+ started() 门，0=未发生，per-request 覆盖写；`Link.timestamps` 成员随每次 bind 新建，按 generation 天然隔离。
- 6 处打点行/5 个语义写点（T4 时点锚，落码后以符号定位）：bindRequestedAt=connectLocked bindService 前一刻（:670）；bindConnectedAt=markConnected stateLock 锁内与 connections.incrementAndGet 同刻（:1161）；readyAt=awaitWorkerReady 两处 isReady 首真出口（:821/:840，含预算到点瞬间翻转的真出口——对 R2 §2.2 #3 的偏差①，保"readyAt==0 ⟺ 从未见到 ready"不变量）；executeAcceptedAt=runSession STATUS_ACCEPTED 分支（:401）；firstByteAt=readResultPayload 读循环首个成功 read（:570，取"最后请求覆盖写"而非"幂等仅首次"——偏差③，与 executeAcceptedAt 同语义保同请求配对）。构造器不加 private（偏差②，JVM 单测需构造、纯数据面无不变量）。
- 访问器 `connectStageTimestampsForTest(generation: Long? = null)`（:1288；link 空/未 started 返回 null；generation 过滤防重建后误读）。回滚=删类+6 打点行+1 访问器，生产路径零行为改动、既有 9 个 seam 零改动。
- **JVM 语义单测 `ConnectStageTimestampsTest.kt`**（??，2 例，本会话 grep -c 同值）：fresh 全零记录不得 started（防"未发生"被当 0ms 段长出数）；仅写 bindRequestedAt 的 bind 失败记录必须 started。**访问器过滤逻辑（generation 不匹配→null、takeIf{started()}）无测试覆盖**（复核 #10）——设备校准用例未落码，seam 消费端现为零验证面。
- **指认消灭的失败**：READY_TIMEOUT=10s 校准主口径无数据源（E-04 step04 §1 原文"不含：宿主 bind/握手排队（未测，无客户端侧计时 seam）"、known-defects:2897）；29.5s 链四段零实测（known-defects:2899(b)、契约 :72 名义值）；冷启动超预算无法分段归因（step03-kd76-criteria §5）；known-defects:2909 批次 38 登记的判据文档缺口由 R2+本落码收口（**KD-76 本体保持 open 不变**）。

### 3.5 T5 P-11 errorKind 半边（计划任务 3）

门核验：`git status --porcelain` 全量 + 精确路径复查，`RoomTutorToolRunner.kt` 及其测试开工时 CLEAN（基线 §2 同判）→ 满足计划任务 3"若已清空则落"分支，未登记停项（编号按基线 §10.1 按 P-12③ 对行，台账回写避免误刷 P-11 行）：

- **协议改**：`RoomTutorToolRunner.kt`（M **+49/−10**）新增稳定结果码常量（:1745-1761，与 FIGURE_UNAVAILABLE/FIGURE_FAILED 同置）；UNAVAILABLE 分支（:1254-1290）以 `result.diagnostics?.contains(UNATTRIBUTED_DIAGNOSTIC_PREFIX)` 判别——前缀常量同源复用 `VisualJsFailureClassification.kt:141` internal const（"不另造字面量"纪律）。
- **回喂口径**：失败形态模型侧只见 `[失败 <errorKind>]`（`OpenAiModelTaskAdapters.kt:679` 实读），单独成码即回喂语义：不混 figure_failed（不诱导修 program）、不混 figure_unavailable（不谎称通道未配置）；记录半 summary 常量。
- **渲染表**：`TutorToolTrace.kt`（M **+7/−0**）`tutorToolRefusalText` 新增行"本机绘图引擎这次出了故障，没有生成"（:421-435）+ 同名 private const；core/ui 零触碰。
- **协调器消费半边**已由批次 35 提交落树（`DefaultFigureGenerationCoordinator.kt:393-410` 在 HEAD，有已提交 JVM 门钉住）——本批剩余即 runner 半边，与任务范围逐字吻合。
- **影响面 12 处闭合**（12.1）：prompt/能力快照对现网可达路径逐字节不变（本构建 JS 两道门 fail-closed 且 X-01 未接线，新码生产暂不可达——保护直调 runTraced 调用者与 X-01 接线后路径）；DB schema/广告开关零改动。**新增用例 2 条**（先红后绿登记）：`RoomTutorToolRunnerTest`（M **+33/−0**）fake generator 回 UNAVAILABLE+前缀字面量（沿 FigureGenerationCoordinatorTest 跨进程契约钉法，对照臂既有用例 else 臂逐字节未动）+ `TutorToolTraceTest`（M **+9/−0**）渲染句。静态校验：四站点码串/summary/学生句逐字节一致（Python -I 比对）、花括号平衡、行尾随宿主文件、diff 边界恰 4 文件（+98/−10，本会话 numstat 逐一同值）。**登记**：M-04 度量失败标记同样折 UNAVAILABLE，其 runner 半边独立成码不在本 ask 范围（candidate 登记）；JVM 执行 UNVERIFIED 归门禁。

### 3.6 T6 台账（任务 11/12/13/14/16）

只改 2 个 docs 文件（清单当时 +88/−9、known-defects +4/0；**记录时点实测清单 +90/−9——差 +2 行为复核修复第 1 轮 D-04 行注记 + I.2/I.5 时点更正注记追加**，known-defects 现 +6/−0 含复核修复回填块）：

- **任务 16**：S-02/S-09 按现树核验刷新（批次 27 提交 `a7b28a47` 经 `git merge-base --is-ancestor` 实证属 HEAD 祖先；SDK 资产 284 行 `visualSdk` 冻结命名空间、两处命名差如实登记；深冻结 DEEP_FREEZE_JS + 防解冻守卫 + 7 用例设备绿两轮在档；缺口改写为 JVM 契约测试半边未落）。
- **任务 13**：V-02 行刷新（JVM 现 8 例含 LAYOUT_PIXEL_BUDGET 不切换裁定例；M-03×V-02 交互裁定已落 `FigureMethodSwitchRunner.kt:134-138` 排除位；设备半边 androidTest 0 命中实证）；A-09 行（本批零动作、`RoomModelTaskRepository.kt:964-965` 仍 false/false）；D-04 行（生产接线仍未落——复核修复第 1 轮注记已把命中面更正推进，行结论"生产接线仍未落"维持为真）；D-06 行（观测门 10 @Test 复测同值）；契约 :208 实读仍在树、docs/current 7 M → **不落笔、维持登记**；KD-76/72 滚动落 known-defects.md 两块（**:2913/:2796**）。
- **任务 14**：V-02 IMAGE→JS 裁定登记：不做机械回退维持（runner KDoc :44-46 原文 + R1 :75 候选 B 采用原文）；回喂模型路径=有界失败诊断经 TutorVisualContext 回喂下一次已有预算内模型回合、operationId 不变、接线归 X-01；JS→IMAGE 机械半边仅限可归属失败（排除面 runner :158-176 实读）。
- **任务 12**：A-11 现 **21 @Test**（批次 36 注记"15"滞后——请求半边 6 例 :321-:361 随批次 38 测试面入树）；A-12 稳定性 **46 @Test**（"42"滞后）；两套件 JVM 执行零 Gradle 未复跑=UNVERIFIED 归门禁串行。
- **附录 I（:654，I.1–I.6，v3 补记 17）**：命令与输出全量落盘；**v3 补记 18（复核修复第 1 轮）**随后追加（时点回填，见 §5.0）。不变式核验：§3 主体恰 69 行（awk 夹取实测）、0 项转通过、MVA 不成立维持、availableMethods 未动；HEAD 时点 `a0641bb7`。

### 3.7 T7 热区条件轨（任务 15）

分支 D 执行完毕（零文件落盘、零 git 写、零 Gradle、零热区触碰）。热区布尔开工实核与脚本判定一致：feature/library 43、feature/tutor 59、core/ui 35（与批次 37/38 登记逐字同值，clean=false → 分支 D）；其余 core/domain 5、core/export 6、core/markdown 1、core/data 32、settings.gradle.kts 1、feature/review 12、feature/profile 4、feature/capture 16、app 36，全脏。九排队项（G2→G1→AWAITING→5b-1/KD-68→AUD-08→6B-3→5b-2→阶段6→5c）逐项复核：全部仍 pending、载体均在、声明热区目标文件均 M（ExplicitRedrawService.kt 保持干净，:431 仍 Idle、:267 仍 AWAITING_VERIFICATION）。**漂移仅一处**：MASTER-LINE AUD-08 审计行锚自批次 38 登记的 :159 漂至 :160（+1），性质同既往维护滚动漂移不重复建条；KD-68 :2652 未动 open；`ui-queue-sequencing.md:46` 仍写"schema 70（预计；当前 69）"而现树 71——批次 34 原登记仍有效；KD 台账 82 条同值零新建。

### 3.8 集成交接 —— 按需接线走【无则明说】分支（零文件编辑）

- **D-04 接线 → 明说：生产接线本批无装配点可落**。生产调用方现状（grep 实跑，非转录）：`TutorVisualPreviewRendererFactory` 在 app/core/data/core/domain main 零调用方（命中仅渲染器自身）；`commitAssistantTurnWithVisual` main 排除定义后仅 KDoc 1 处（`VisualCheckpointRetention.kt:132`）。生产装配点缺位实证：`SmartMistakeBookApplication.kt:364-368` 对 `FigureGenerationCoordinationFactory.create` 仅传 3 参（`visualEngine` 缺省 null，`DefaultFigureGenerationCoordinator.kt:158/276` null→UNAVAILABLE）——生产视觉链未通引擎、生产 commit 路径不存在。不可自行接线的硬依据：R1 §2.2 明令不在仓储/引擎内部做预览；装配点=DefaultFigureGenerationCoordinator（他线在飞）；接 visualEngine 会翻转 JS 能力广告撞红线。现存唯一真实插入点（app ④ 设备例 commit 路径）T2 已接线：`V01VisualChainInstrumentedTest.kt` renderer→renderPreview→Accepted.previewAssetId→`TutorVisualSnapshotInput(previewAssetId=)`→仓储提交；透传缝 `TutorVisualCommitRepository.kt:103/:529` 在树；顺序即 R1 §2.2。
- **KD-76 seam 与校准判据 → 已落，无需接线**：T4 落码完整性核实（引用闭合本轮实核：linkLock :112、SystemClock import :12、connection=ensureConnected() :253 在树；JVM 单测与数据面字段逐一对齐）；校准判据文档在树（252 行 + 134 行）。校准出数/设备执行归设备轨，本轨 UNVERIFIED。
- **V-01④ seam 与套件 → 已落，签名逐参核实**：`VisualChainTestSeams.kt` 两公共操作转调点逐参吻合（`VisualSnapshotRestoreReadSide.restoreRecordOf` :37、`VisualSnapshotRestore.restore` :193-197、`VisualDrawOpsParser.parse` 双过载 :94-98/:136、`VisualDrawOpsPdfPainter.writeFile` :108-113、internal outcome 投影字段逐字段吻合 :133-151）；grep 门实跑 OK/EXIT=0。keep 登记未落（r04-test-keep.pro clean 实证）——引用用例不得进 R-04 release 轨清单，登记项维持。
- **import 悬空核验 = 0**：本批 11 文件的 84 个 `com.tingyun.*` import 逐一解析到（package,symbol）声明（首轮全 DANGLING 系路径含空格致 xargs 失效的检查脚本 bug，改 `-Z/-0` 管道后复跑修正）；④ 例内同文件引用全部在树。**此为静态解析级核验，非编译——编译归门禁串行（本轨禁 Gradle）**。
- **零广告改动 / 零 DB schema 改动 = 确认**（§1.1；工作树 737 条脏项属多线在飞，确认仅限本批申报文件集与点名 surface 文件，不是全树归属声明）。

### 3.9 设备轨（四步串行；详节 §4）

按判据串行执行四步，R4 证据落盘 10 个 .md（工单快照 48.9 KiB → 00-index 自记 57,104 B → 记录时点 63,651 B，脱敏 0 hits×4 维持）。零 git 写、零热区编辑、仅 assemble* 构建、全部套件经裸 am instrument、设备 emulator-5562（5566/5584 禁碰未动、5596 未跑）。

## 4. 设备与证据

> 环境：`emulator-5562`（AVD test_device / API 34 / x86_64 / PAGE_SIZE 4096；5566/5584 他线禁碰、5596 API 23 机未跑本轨）。设备轨窗口 HEAD=`a0641bb7`；落盘时点 `684adb6e`（他线 docs 提交，不改工作树字节——00-index §8 判例）。原始产物不入仓。

### 4.1 重建重装硬门与产物身份（E-01，全 PASS）

- keep 门 OK×3/KEEP_GATE_EXIT=0；S=71 静态门 RESULT=PASS（PLAN_HASH=JSON_HASH=`714c0ecb…`）；源快照 **1227 文件**（五模块集含 core/model）构建窗 diff=0；BUILD_EXIT=0（Gradle up-to-date 语义沿批 38 E-01 先例）；4×install Success；四包设备端 sha256 逐字节一致；71.json asset 门（378,961 B，sha `4a13d9d0…`）；dex 符号门 app 16 / data 41 / database 10 全 OK。

| 产物 | sha256（00-index §2） |
|---|---|
| app 主包 | `c149a2c34ca0a235…f76e231` |
| app androidTest | `e20d923a8e5d7619…178b17d` |
| data androidTest | `5b8ba39c83b625f3…1153c3` |
| database androidTest | `c5fae932c013859e…8bc1a4` |

### 4.2 步骤与套件总账（E-00…E-08，00-index §3/§4）

| E-ID | 步骤 | 判定 |
|---|---|---|
| E-00 | 环境与设备身份 | PASS |
| E-01 | 重建重装+源快照门（含 core/model）+asset/dex 门 | PASS |
| E-02 | 新设备例（V-01④/N 向量/X-02/X-03/A-10） | **PARTIAL**（④ 4 例内绿、N 4/4、X-02 6/6、A-10 1/1；X-03 0/1 A 类） |
| E-03 | app 套件首跑（7 套件） | PARTIAL（5 套件绿；isolation 6 红、v01app 1 红） |
| E-04 | core:data 套件（12 套件） | PASS（全绿 158 例） |
| E-05 | core:database 套件（5 套件） | PASS（4 套件绿 + matrix 复跑 15/15；首跑窗误停登记） |
| E-06 | 红例逐条归因（复跑+判别轮） | **PARTIAL**（v01app 4/4 绿取代首跑红；x03 两窗同红 A 类坐实；isolation 三窗红未收敛→阻塞上报） |
| E-07 | 崩溃签名 27 窗×8 类+生命周期收口 | PASS（216 项全 0；含 §9 本轨误停更正节） |
| E-08 | KD-76 seam 状态与校准出数条件 | **UNVERIFIED**（seam 已落码；设备校准用例 0 引用→出数不可执行） |

- **设备总账**：28 套件/236 例执行；绿终判 = **26 套件 220 例**（含首次取得设备证据的新例 12 个：N 向量 4 + X-02 6 + A-10 协调器级 1 + V-01④ 1；既有 208）；红终判 = 2 套件 16 例（isolation 15 例 FAIL 未收敛 + X-03 1 例 FAIL A 类夹具）；0 跳过；27 窗崩溃签名 0；matrix 窗无签名。
- **新例判据要点**（E-02）：N5/N6/N7+取消重抛 4/4 绿（拒在分配前、快照照常提交、恰达不拒、取消重抛）；X-02 五负向量+正向 witness 6/6；A-10 同 operationId 先 JS 后 IMAGE 第 3 份拒 1/1（真 ledger）；V-01④ 例首轮即绿、复跑窗套件 4/4 绿。
- **isolation 三窗红（E-06 §2，本批最大未决）**：红形全部为"帧执行超 1500ms 服务端 watchdog 杀→binder died/级联"，kill 计数 10/10/6（设计基数 5）、红例集合三窗各异（高方差）、同会话其余 199+ 例全绿 → D 环境主导；T4 关联机制上不可能（kill 在服务进程侧，T4 只改宿主 6 处 volatile 打点）但设备轨禁改他线在飞文件无法做去 T4 控制轮 → 按 R2 §7.6"seam 设备绿绑定"当前不成立，阻塞上报主会话裁定（低负载窗重跑 / T4-reverted 控制轮 / 接受 D 归因）。
- **X-03 两窗同红（E-02 §5/E-06 §1）**：expected `visual:operation-x03-relayout-chain:818cfa45…` / actual `visual-program-2760ca…`——归因链逐处实读复核成立（§1.1 偏差 2）。
- **操作误停登记（E-07 §9 更正节）**：matrix 首跑窗被本轨误判"v9 卡死"force-stop（字母序 ls 探针错觉，实况 v39/71 正常推进）；复跑窗 OK(15 tests) 取代。
- **未执行项（00-index §5 如实登记）**：V-03/V-04 轨 3 套件（CommitRace 3 / RebuildRestore 3 / SavedProblemRetention 2）按判据归其轨套件单，本轨未执行（同批 38 口径）；arm64/16KB/真机口径维持既有缺口（全部数字 x86_64/API 34 模拟器口径）。

## 5. 复核发现与处置

### 5.0 修复环第 1 轮（工单给定 + 00-index §10 在树，本会话实读）

非低发现 5 条，处置 **5/5（3 修复 + 2 验证确认不改）**，全部为只读核对+实跑 grep+追加式注记，零 Gradle/零设备/零 git 写：

- **发现 1（00-index §4.1 #3 欠报，中）→ 已修复**：计划任务 3（P-11 errorKind 半边）原记 ➖"热区维持/本轨零改动"；本轮 `git diff --numstat` 实测四文件 +98/−10 与 T5 申报逐字一致、四 diff 逐 hunk 读毕零外来 hunk → 索引 #3 行改判 ◐"落码在树"（JVM 执行仍 UNVERIFIED）。
- **发现 3（00-index §4.1 #1 关闭建议越界，中）→ 已修复**：任务 1 命题=生产装配缝，生产 main 零调用方实跑证实（`renderPreview`/`TutorVisualPreviewRendererFactory.create` 排除自身 0 命中；唯一 main 引用 `VisualChainTestSeams.kt:74` 为 KDoc 自钉 src/main 零引用的测试缝；`SmartMistakeBookApplication.kt:364-368` 恒三参）→ 索引 #1 行改 ◐ 并明示"不构成关闭建议"，关闭裁定归主会话须按"端口+向量绿、生产装配未接"重限定。
- **发现 2+4（known-defects:2913 T13 块 + 清单 :168 D-04 行/附录 I.5 台账 seam 状态滞留，中）→ 已修复（回填）**：T13 块②"seam 本体未落 0 命中"与 I.2/I.5 命中面均为落笔时点值，被同日 T4/T2 落码推进——回填=known-defects.md 增批次 39 复核修复 Progress 块（:2915，本会话实读在场）+ 清单 D-04 行注记 + I.2/I.5 时点更正注记；T13/I 原记录不回改（当时为真，落笔时间戳在案）。
- **发现 5（X-03 夹具断言，中）→ 验证确认不改**：归因链逐处独立复核成立（§1.1 偏差 2）；处置=不动夹具，"修复+复跑"欠账维持 ⛔ 归落码批同窗完成。

### 5.1 复核轨发现（10 条；工单"已独立复核"标注原样转录）

| # | 级别 | 位置 | 发现（压缩转录） | 状态 |
|---|---|---|---|---|
| 1 | 中 | 00-index §4.1 #3 + 设备/证据轨任务 3 转述 | 批次级任务 3（P-11）被记为"未覆盖/热区维持/零改动"，与现树矛盾（该文件 M 恰为 T5 落码）——欠报；热区纪律附注：T5 开工前树态干净性本轮无法独立复核，需主会话按 T5 开工核验记录裁定 | 已独立复核 → **处置见 §5.0** |
| 2 | 中 | known-defects:2913 + 清单:168 | 两处台账语句被本批落码推进为过时：① T4 已落 seam，"seam 本体未落 0 命中"现树为假；② D-04 行命中面描述过时（行结论"生产接线仍未落"仍真） | 已独立复核 → **处置见 §5.0（回填已落）** |
| 3 | 低 | 三处轨间数字申报 | 渲染器 JVM 实为 9 @Test（申报 8）；引擎 diff 实为 +150/−6（集成"+156/−7"歧义记法，T4 申报正确）；R2 实为 252 行（台账/集成报 252 正确） | 已独立复核（本会话复测同值） |
| 4 | 低 | 全部设备执行数字 | 未复核（本轨无法复核）：N 4/4、X-02 6/6、A-10 1/1、V-01④ 4/4、isolation 三窗 6/7/5 红、X-03 红、26 套件 220 例绿、崩溃签名全 0、dex 门、源快照、四包 sha——替代核验=四证据文件细读+内部算术自洽（28 套件 236 执行=220 绿+16 红）+命令字段可复跑+源码锚逐处对应；isolation 裁定请求已上报 | **未复核** |
| 5 | 中 | 00-index §4.1 #1 + 渲染器:120-122 + Application:364 | 计划任务 1 被标 ✅"关闭建议"，但生产装配点实际零调用方、previewAssetId 生产恒 null——若按索引建议关闭即台账反向虚报；失败语义半边本身完备 | 已独立复核 → **处置见 §5.0** |
| 6 | 中 | known-defects T13 块 + 清单附录 I.5/I.2 | 台账滞留与树态矛盾：两处均写 seam"0 命中/本体未落"而当前树已含 seam；台账块落笔时点早于 T4 落码（时间戳在案），非虚报而是待回填；风险在提交时点 | 已独立复核 → **处置见 §5.0** |
| 7 | 中 | X-03 设备向量 :165-173/:219-223 + E-02 §5/E-06 §1 | X-03 红（0/1）=A 类夹具缺陷，归因链独立核实成立（两 id 空间错配、两窗逐字节同红、非产品回归排除论证成立） | 已独立复核 → **处置见 §5.0（不动夹具）** |
| 8 | 低 | VisualChainTestSeams:24-29 + r04-test-keep.pro + 门脚本 | seam 生产零改动成立（门实跑通过+负控有效），但缝 KDoc 约束 2 的 keep 行"在 r04-test-keep.pro 一并登记"实际未落（零命中、文件未修改）——程序性护栏三处在案、非违规，但 KDoc 加粗表述易被误读为已完成 | 已独立复核 |
| 9 | 低 | 批次文件清单 vs 工作树 | 盘点缺口：`VisualSnapshotRestoreReadSide.kt`(+Test)（?? 新文件，`VisualChainTestSeams.kt:57` 直接调用）不在 ask 文件清单；`DefaultFigureGenerationCoordinator.kt`（+22，X-02 sourceSeen）与 `QuickJsVisualExecutionEngine.kt`（+12，M-03 4MP 结算门接线）为树上未提交 diff、既不在批次清单也不在他线 M 枚举——commit 前逐路径核对时需逐一路径注明归属 | 已独立复核（本会话 porcelain/numstat 复测同值） |
| 10 | 低 | ConnectStageTimestampsTest + 引擎:1288-1291 | 计时 seam 访问器过滤逻辑无任何测试覆盖（JVM 半边只测 2 条纯语义、未触访问器；设备校准用例未落码）——缺口已被 E-08 登记，非隐瞒；KD-76 出数前 seam 消费端为零验证面 | 已独立复核 |

## 6. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 2 轮 / 复跑） | **exit=0 ×2**（工单给定） | 记录员未复跑（禁 Gradle）；逐套件出数未随工单传入——本批新增 JVM 套件逐套件断言面 UNVERIFIED |
| 强门（修复环后 ×2） | **exit=0 ×2**（工单给定） | §5.0 修复环第 1 轮 5/5 处置后复跑 |
| 修复环第 1 轮 | 5/5 处置在树 | 00-index §10.1–§10.4 + known-defects 回填块 + 清单注记（本会话实读在场） |
| 设备 E-00…E-08 | §4.2 | 绿终判 26 套件 220 例；红终判 2 套件 16 例（isolation 15 未收敛 + X-03 1 A 类）；0 跳过、崩溃签名 27 窗×8 类全 0 |
| keep 门 / S=71 门 | OK×3 EXIT=0 / PASS（PLAN_HASH=JSON_HASH=714c0ecb…） | E-01 |
| 源快照绑定 | 1227 文件 diff=0 | E-01（含 core/model） |
| dex 符号门 / 资产门 | app 16 / data 41 / database 10 全 OK；71.json 过 | E-01 §5/§6 |
| 缝 grep 门（check_visual_test_seams.py） | OK，EXIT=0 | **本会话记录员实跑**：`python -I tools/ci/check_visual_test_seams.py` → `OK: VisualChainTestSeams has 0 main/test references and 1 androidTest reference(s)`；负控 FAIL/exit=1（复核轮转录） |
| KD-76 校准 | **UNVERIFIED（出数结构性不可执行）** | E-08：设备校准用例 androidTest 0 引用；READY_TIMEOUT 维持 10s；KD-76 open 维持 |
| 生产签名 / arm64 / 16KB / 真机 | UNVERIFIED | 维持既有缺口 |

## 7. 遗留 / UNVERIFIED / 待裁

**执行与设备面**

1. **isolation 三窗红未收敛（最高优先）**：裁定请求（低负载窗重跑 / T4-reverted 控制轮 / 接受 D 归因）归主会话；R2 §7.6 seam 设备绿绑定当前不成立——KD-76 校准与 V-01④ 关闭建议的绑定前提均受其牵制。
2. **X-03 修复+复跑欠账**：A 类夹具（两 id 空间错配）归落码批同窗完成；X-03 判据语义（①档/0 派发三断言位）无设备证据。
3. **KD-76 校准出数**：设备校准用例未落码（androidTest 0 引用）→ 出数不可执行；READY_TIMEOUT 维持 10s；落码后按"新增用例必须重跑"进套件单；**访问器过滤逻辑零测试覆盖**（复核 #10）。
4. **V-03/V-04 三套件未执行**（Race 3/Rebuild 3/Retention 2）——归其轨判据与套件单（同批 38 口径）。
5. **Q-04 设备半边（真机渲染金样）未覆盖**（00-index §4.1 #10）：载体归落码批、出数归设备轨后续批；arm64/16KB/真机口径维持。

**待裁（归主会话）**

6. **D-04 生产装配接线**：端口+设备向量绿、生产装配零调用方（previewAssetId 生产恒 null）；装配点=他线热区文件，且接 visualEngine 会翻转 JS 能力广告——归位批裁定（不构成关闭建议）。
7. **V-01 状态判定**：④ 腿设备证据已取得（首轮绿+复跑 4/4），关闭建议附 isolation 绑定登记；严格口径 PARTIAL→4/4 的判定归主会话。
8. **isolation 三窗红裁定 + T4 seam 设备绿绑定**（同 1）。
9. **R-04 keep 义务登记未落**：`app/r04-test-keep.pro` 零命中且 clean——缝/渲染器 keep 行归 R-04 批；此前引用两者的 release 轨用例不得进 R-04 清单。
10. **T5 开工前树态干净性**：RoomTutorToolRunner 开工时 CLEAN 系 T5 申报（基线 §2 同判），本轮无法独立复核其时点——归主会话按申报记录裁定（复核 #1 附注）。
11. **M-04 度量失败标记 runner 半边**：独立成码 candidate 登记（T5）；KD-82 open 维持（关闭待门禁+广告批裁定，本批零动作）。

**口径**

12. **4E.2 MVA 仍不成立**：0 项转"通过"、69 行不变；JS 仍不广告（`availableMethods` 未动、生产装配恒三参）；KD-72 open（分支 B）/KD-73 open/KD-76 open/KD-82 open。
13. **契约 `:208` 过时句**：docs/current 7 M 热区未清空 → 登记不落笔（补记 17/18 沿登记分支）。
14. **通道甲 measuredEntries 合并语义待接线**（T2 如实登记：暂传 null=预览独判，合并语义经缝后续接线）。
15. **工作树计数滚动**（712→719→722→737→784）差值未逐项追认；他线在飞内容功能正确性归各线；生产签名/arm64/16KB/真机 UNVERIFIED。

## 8. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前按门禁序列复跑单测门/强门并留日志；`git diff --cached --name-only` **逐路径**（不只数数量）对照本清单，申报外路径单独 `git reset <path>`；共享脏文件整文件纳入并在提交说明注明本批与他线来源；不声称全绿（isolation 三窗未收敛 + X-03 欠账在案）。按 ≤6 路径/块（`...` = `kotlin/com/tingyun/smartmistakebook`）：

- **块 1（D-04 渲染器+缝+缝依赖）**：`core/data/src/main/.../visual/TutorVisualPreviewRenderer.kt`（??）、`core/data/src/main/.../visual/VisualChainTestSeams.kt`（??）、`core/data/src/main/.../visual/VisualSnapshotRestoreReadSide.kt`（??，批 38 载体、缝 :57 直接依赖——不入库则缝不可编译）、`tools/ci/check_visual_test_seams.py`（??）
- **块 2（渲染器/缝测试）**：`core/data/src/test/.../visual/TutorVisualPreviewRendererTest.kt`（??）、`core/data/src/androidTest/.../visual/TutorVisualPreviewRendererInstrumentedTest.kt`（??）、`core/data/src/test/.../visual/VisualSnapshotRestoreReadSideTest.kt`（??）
- **块 3（V-01④ app 套件）**：`app/src/androidTest/.../visualjs/V01VisualChainInstrumentedTest.kt`（M，3→4 例，整文件纳入）
- **块 4（KD-76 计时 seam）**：`app/src/main/.../visualjs/VisualJsClientEngine.kt`（M，+150/−6，整文件纳入）、`app/src/test/.../visualjs/ConnectStageTimestampsTest.kt`（??）
- **块 5（P-11 runner 半边）**：`core/data/src/main/.../study/RoomTutorToolRunner.kt`（M，+49/−10，整文件纳入——本批 T5 落码）、`core/data/src/test/.../study/RoomTutorToolRunnerTest.kt`（M，+33/−0）、`core/model/src/main/.../TutorToolTrace.kt`（M，+7/−0）、`core/model/src/test/.../TutorToolTraceTest.kt`（M，+9/−0）
- **块 6（T3 设备向量）**：`core/data/src/androidTest/.../visual/FigureCrossMethodFinalFigureDeviceVectorInstrumentedTest.kt`（??）、`core/data/src/androidTest/.../visual/VisualSourceEvidenceDeviceVectorInstrumentedTest.kt`（??）、`core/data/src/androidTest/.../visual/StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt`（??；**含已知红例**，修复+复跑欠账归落码批——提交与修复同窗或明示红窗在案）
- **块 7（他线在飞 main 面，本批测试的直接依赖；归属按各自 KDoc/diff 注明）**：`core/data/src/main/.../visual/DefaultFigureGenerationCoordinator.kt`（M，+22/−0，X-02 sourceSeen 派发证据落点——块 6 X-02 正控例依赖）、`core/data/src/main/.../visual/QuickJsVisualExecutionEngine.kt`（M，+12/−0，M-03 4MP 结算门）
- **块 8（研究+基线+记录）**：`docs/research/2026-11-01-preview-wiring.md`（??）、`docs/research/2026-11-01-kd76-timing-calibration.md`（??）、`docs/research/2026-11-01-batch-39-baseline.md`（??）、`docs/research/2026-11-01-batch-39-record.md`（本文件，新）
- **块 9（设备证据）**：`docs/research/evidence/2026-11-01-batch-39/`（?? 目录，10 个 .md）
- **块 10（台账，整文件纳入）**：`docs/known-defects.md`（M；KD-72/76 批次 39 块 + 复核修复回填块 + 记录员收口块）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（M；补记 17/18/19 + 附录 I）、`docs/REFACTOR-MASTER-LINE.md`（M；批次 39 滚动行）、`docs/agent-first-refactor-decisions-2026-09-23.md`（M；批次 39 节）

**明确排除（他线在飞/不安全）**：`settings.gradle.kts`、`.gitignore`；`feature/**`、`core/ui`、`core/domain`、`core/markdown`、`core/export`；core/data 他线 M（dense/knowledge 面、`RoomMistakeDetailRepository.kt`、SafeTruncation 面、`TutorToolDescriptions.kt` 等）；core/database 其余 M；app 其余 M（Root、ReviewReminder*、SecondaryScreens、ExportPdfWorker、StartupStateBanner、SmartMistakeBookDestinations、LearningMasteryScreen、SchedulingSettingsScreen 等）；`docs/current/**`；docs/research 他线未跟踪记录；`build/**`（含设备原始日志）；`tmp/**`；全部 `*.lockfile`；`tools/kb*`、`tools/dense_build`、`tools/kb_coverage`、`knowledge-production/**`。

## 9. 收口状态

- **研究**：R1 366 行（预览接线八步管线/有界降级/N5-N7 判据/方案 A seam/外部评分）；R2 252 行（四阶段计时 seam/READY_TIMEOUT 校准判据/29.5s 分段方案/anti-flaky 六条/awaitility 不引入）；批 38 F.20 预研 134 行被 R2 细化覆盖。未验证项如实（两文档 §6/§7）。
- **实施**：七轨落码/判据/台账（渲染器+缝+9+4+4 例、设备向量 1902 行 8 例、引擎 seam +150/−6+2 例、P-11 四文件 +98/−10、台账补记 17/18、条件轨分支 D）；集成零接线（无则明说）。
- **设备**：28 套件 236 例执行、绿终判 26 套件 220 例（新例 12 首证）；isolation 三窗红未收敛（阻塞上报）；X-03 0/1 红 A 类；源快照 1227 文件 diff=0；keep/S=71/dex/资产门全过；KD-76 校准 UNVERIFIED。
- **门禁**：单测门 exit=0 ×2、强门 exit=0 ×2（工单给定）；修复环第 1 轮 5/5 处置。
- **台账同步（本轮记录员写入，§附录 A）**：`docs/known-defects.md`（KD-76 批次 39 记录员收口块）、`docs/REFACTOR-MASTER-LINE.md`（批次 39 滚动行）、准入清单（v3 补记 19）、`docs/agent-first-refactor-decisions-2026-09-23.md`（批次 39 节）。
- **本批不声称全绿/全就绪**：isolation 三窗未收敛；X-03 欠账；KD-76 校准未出数；D-04 生产装配未接；V-01 判定归主会话；R-04 keep 登记未落；KD-72/73/76/82 open；JS 仍 UNAVAILABLE 不广告；4E.2 MVA 不成立；生产签名/arm64 UNVERIFIED。

## 10. 本记录的验证结果（记录员实跑/实读，写盘前执行）

```bash
cd "D:/smart mistake book"
git rev-parse HEAD                                   # 684adb6eb646f0f1b62f2cb0b367c3be21607a8e
git status --porcelain=v1 | wc -l                    # 784；git diff --cached --name-only | wc -l = 0（暂存空，零 git 写）
git log --oneline -6                                 # 684adb6e/f0512a47/a0641bb7（批次 31A docs）→ b95451c9/fbab0b67/2eca3578（批次 38 收口）

# 研究/判据文档行数（wc -l）：preview-wiring=366 / kd76-timing-calibration=252 / kd76-ready-timeout-calibration=134 / batch-39-baseline=165
# 证据目录：ls = 10 个 .md（00-index + step00..step08）；cat *.md | wc -c = 63651

# 各轨申报文件 git 状态逐一核对（git status --porcelain -- <23 路径>）：
#   10 M + 13 ??（T2 六件/T3 三件/T4 两件/T5 四件/T6 两件/三研究文档/门脚本/evidence 目录），与各轨申报一致

# @Test 计数（grep -c '@Test'）：RendererTest=9 / RendererInstrumentedTest=4 / V01VisualChain=4（3→4）/
#   ConnectStageTimestampsTest=2 / CrossMethod=1 / SourceEvidence=6 / StrictOfflineRelayout=1
# 新文件行数（wc -l）：T3 三件 554+642+706=1902 / 渲染器 383 / 缝 196 / 门脚本 158

# diff 规模（git diff --numstat）：VisualJsClientEngine.kt=150/6（T4 申报一致，集成"+156/−7"更正）/
#   RoomTutorToolRunner.kt=49/10、RoomTutorToolRunnerTest.kt=33/0、TutorToolTrace.kt=7/0、TutorToolTraceTest.kt=9/0（T5 合计 +98/−10）/
#   V01VisualChainInstrumentedTest.kt=239/1 / DefaultFigureGenerationCoordinator.kt=22/0、QuickJsVisualExecutionEngine.kt=12/0（他线在飞，复核 #9）

# 缝 grep 门（本会话实跑）：python -I tools/ci/check_visual_test_seams.py
#   → OK: VisualChainTestSeams has 0 main/test references and 1 androidTest reference(s)；SEAM_GATE_EXIT=0

# 锚点实读：StudyDatabase.kt:139 STUDY_DATABASE_VERSION = 71；
#   known-defects.md KD-72 批 39 块 :2796 / KD-76 T13 块 :2913 / 复核修复回填块 :2915（grep 定位同值）；
#   清单 D-04 行 :168 复核修复注记在树、附录 I :654（v3 补记 17）、头部补记 18（复核修复轮）在树——本记录员收口件编号取 19；
#   00-index §7 回链"记录文件不存在"——本文件即落笔位收口；MASTER-LINE 头部最近计划维护=批次 38（本记录员滚动为 39）

# 未执行：任何 ./gradlew、adb 命令、connectedAndroidTest；任何 commit/push/add/reset；任何设备写；任何产品代码改动
```

## 附录 A：台账同步（本轮记录员写入）

| 文件 | 写入内容 |
|---|---|
| `docs/research/2026-11-01-batch-39-record.md` | 本文件（00-index §7 登记的记录员落笔位在此收口；§4 全部以 E-ID + path:line 回链） |
| `docs/known-defects.md` | KD-76 条目追加"批次 39 记录员收口"Progress 块（设备轨 E-06/E-08 证据落台账：校准出数结构性不可执行〔androidTest 0 引用〕→ READY_TIMEOUT 维持 10s；isolation 三窗红未收敛 → R2 §7.6 seam 设备绿绑定当前不成立、裁定请求已上报主会话；KD-76 open 维持、设备验证前 JS 不广告）。**该文件为共享脏文件（开工时已 M：台账轨 KD-72/76 块 + 复核修复回填块在飞），整文件纳入并注明**；KD-72（:2796 现值即终态：分支 B 维持）与 KD-82（本批零动作、open 维持）按证据零改动，不新增块 |
| `docs/REFACTOR-MASTER-LINE.md` | "最近计划维护"滚动为批次 39 行；原批次 38 行降为"上次维护"。开工时该文件 M（批次 38 滚动行在飞未提交），本轮改动即批次 39 行 |
| `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | 头部追加 **v3 补记 19（批次 39 记录员收口，2026-11-01）**：设备证据刷新（N5-N7/X-02/A-10/V-01④ 设备绿首证 12 例；X-03 0/1 红 A 类欠账归落码批；isolation 三窗未收敛；KD-76 校准 UNVERIFIED）、M-03 16MiB 分配前门已接线渲染器、P-11/P-12③ runner 半边落码（+98/−10）、D-04 行义"部分落"维持（生产装配零调用方）、V-01 判定归主会话、R-04 keep 登记未落——0 项转"通过"、69 行不变、MVA 不成立维持、availableMethods 未动。**该文件为共享脏文件（开工时已 M：补记 17/18 + 附录 I 在飞），整文件纳入并注明** |
| `docs/agent-first-refactor-decisions-2026-09-23.md` | 追加"批次 39 接线与设备校准批——D-04 预览接线缝与有界降级 + M-03 16MiB 分配前门 + V-01④ 公开测试缝 + KD-76 计时 seam（2026-11-01）"节（裁定内容 + 边界/待裁） |

以上均为 docs 追加/刷新；零 Kotlin/JS/Gradle 产品代码改动、零 git 写、零 Gradle、零设备写。

## 10. 主会话收口（2026-11-01，提交与推送执行者实跑）

- **双门**：单测门第 2 轮 exit=0（工作流）；修复环复跑 exit=0；强门（修复环后）exit=0。主会话本轮复跑待下方追加行（若有）。
- **四条裁定**：
  1. **P-11 errorKind 半边=已落**：T5 按条件授权（"若 git 干净则落"）落码，开工核验记录在案，diff 纯净零外来 hunk（复核 +98/−10 逐行实读）；证据索引"热区维持零改动"为时点滞后，以台账附录 J 为准。
  2. **D-04=◐（端口+向量绿、生产装配未接）**：索引的"关闭建议"被否——生产装配点（Application/Coordinator）是热区零调用方；接线归热区清空后。失败语义与 N 向量 4/4 绿不受影响。
  3. **V-01=4/4**：④ 腿经 test-only seam（`VisualChainTestSeams`，grep 门 + R-04 keep 义务登记在案）补上设备证据（首轮 code 0 + 复跑 OK (4 tests)）；生产装配另计。
  4. **isolation 三窗未收敛**：D 归因暂时接受（T4 机制反证=6 处 volatile 打点、kill 计数高方差），**批次 40 设备窗必须低负载复跑到 15/15；仍红则 T4-reverted 控制轮强制**。X-03 红例=A 类夹具缺陷，修复+复跑归批次 40。
- **台账回填**：known-defects KD-76 前更正块 + 清单附录 J（上方两笔已提交）。
