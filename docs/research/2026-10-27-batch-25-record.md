# 批次 25 记录（2026-10-27）：竞态收尾 + warm-up/ready + 队列背压 + 准入清单补齐

> 记录时点：2026-10-27（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对计划、基线、四研究文档、T5 清单、当前源码/测试、`git status`、`tmp/verify_batch25_*` 脚本与设备证据目录后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未提交/推送、未改产品代码。** 本记录员本簿新增交付共三处：本文件、`docs/REFACTOR-MASTER-LINE.md`（顶部滚动三段 + 4E/补修行）与 `docs/research/algorithm-version-ledger.md`（§3.35 零 bump），另有 `docs/known-defects.md` 的 KD-66/KD-76 设备证据回填两段与证据索引 `00-index.md` 的记录回链三处（§1 记录行、§7 第三项勾选、§9.4 状态，均行数中性），见 §7。
> 计划：`.zcode/plans/plan-2026-10-27-batch-25-race-warmup-queue.md`（下称"计划"）。
> 基线：`docs/research/2026-10-27-batch-25-baseline.md`（下称"基线"；基线本身是未跟踪新文件，476 行）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`；修复环第 1 轮 4 条全处理；单测门复跑 `exit=0`。仓内没有本批门禁日志，§4 只作转录。
> **本记录不声称"批次全绿"**：隔离套件 11/12（1 红 = T3 新用例夹具缺陷，复核 1 轮已修但**未设备复跑**，A-14 用例级设备证据仍缺）；KD-66 的 hard+500 ms 关闭门用例未写（E-02 NOT-WRITTEN）；KD-76 ③④⑤ 设备判据未达（③ 为注入形状非真实冷启动、④ 计时弱口径 ≤6 s、⑤ UI/文案面未落）；arm64/16 KB 运行时 UNVERIFIED（本机原理性不可行）；16 KB"真机加载"子句 UNVERIFIED；精确 `mallocSize` UNVERIFIED；生产 release 签名 UNVERIFIED；中文 OCR 未跑；锁文件未生成未入库（KD-72 open）；真实 Provider 无凭据、模型质量 UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。

## 1. 范围与基线

### 1.1 计划范围（计划 §1/§2）

- **做**：R1（R1'）warm-up/ready 与队列/背压设计参考；R2 契约 §3.2/§4/§8/§9 全条目审计（准入清单漏项逐条映射）；R3 锁文件干净树前置检查；R4 arm64/16KB 与 KD-70 ABI 覆盖可行路径；T1 death-before-registration 竞态收尾 + 交错用例；T2 warm-up/ready 实现（KD-76）+ 加载态 + 测试；T3 队列/背压实现（§64）+ 测试；T4 准入清单补齐（MVA 复核）；T5 台账/契约状态同步；T6 热区条件轨；设备轨（隔离含新用例、KD-66 hard+500ms、P2 于 ps16k 16KB、R8 release 抽验、备份抽验）；证据落盘、双门、双复核、修复、记录、提交推送。
- **不做**（计划 §1/§2）：锁文件生成（前置未满足则只登记）；4E.2 交互/保真与 JS 广告（T2/T3 落的是 4E.2 前置件，不广告）；PDF `/AIGC`（core/export 热）；热区 UI 实现（除非 T6 判定干净）；真实 Provider；Wave X。

### 1.2 沿用裁定（本批执行，未改）

1. **JS 不广告**：本批所有设备绿只是 4E.2 前置件证据，不等于广告门；`availableMethods` 无 JS（§5 R12）。
2. **4E.2 交互/保真与 PDF `/AIGC` 不做**。
3. **AIDL 只以 Gradle 任务验证**（`aidl.exe` 双输入禁用；T2 的非 Gradle 直编五阶段脚本是其自身验证边界，不替代 AIDL 门）。
4. **AAR 钉版 `quickjs-kt-android:1.0.15` 与 kotlin-stdlib 2.3.21 不变**（本批零构建脚本改动）。
5. **锁文件不生成不提交**（KD-72 等干净树）；KD-66 保持 open 直到 hard+500 ms 设备用例；KD-76 在设备验证前不关闭。
6. **真实 Provider 无凭据**：模型质量 UNVERIFIED。
7. 设备证据按 R4 规范落盘（`docs/research/2026-10-26-device-evidence-convention.md` v1）。

### 1.3 基线摘要与记录时点复核（记录员实测）

- **HEAD**：记录时点 `25ddb602`（`git rev-parse --short HEAD`）；计划 §0 与基线冻结写 `d783ae84`。`25ddb602` 是 KB 线提交（`git show --stat`：7 文件、2,195 插入——`docs/known-defects.md` +10 行 = KD-77 登记、`docs/ops/kb-observability-handoff.md`、`docs/research/2026-10-26-kb-usage-r1-prereg.md`、`tools/kb_coverage/retest_usage_r1.py`、KB 文档两件、他线新测试一件），父提交 `d783ae84`（批次 24 记录收口）。**记录员未 `git fetch`，origin/main 实时 hash UNVERIFIED。**
- **工作树**：基线冻结（00:30:38）`673` 行（202 M + 471 ??，staged 0）；记录时点 `694` 行 = **212 M + 482 ??**（`git status --porcelain | wc -l`、`grep -c '^ M'`、`grep -c '^??'`）；`git diff --cached --stat` = **0 行（暂存区空）**。增量来自本批落点（T1/T2/T3 代码+测试、四研究文档、T5 清单、证据目录）与他线继续在飞，不能按模块 `git add`。
- **R3 快照警告的后续（记录员复核）**：R3 在 01:01 快照到他线把 231 文件（含 13 项目锁 + settings 锁）`git add` 进暂存区并判"不得原样提交"（R3 §0 阻塞级发现）；**记录时点暂存区已清空（staged=0），13+1 锁回到未跟踪**（12 个 `?? */gradle.lockfile` 直见 + `core/markdown/gradle.lockfile` 折叠在 `?? core/markdown/` 目录项内；`git ls-files '*gradle.lockfile'` = 0；`benchmark/gradle.lockfile` 不存在）。暂存→清空的中间过程发生在本记录员会话之外，**未核实由谁/如何处置**；锁仍未生成未入库，KD-72 保持 open，R3 的重生成前置清单（等 6 个解析图 build 文件入库 + 整树收口）仍然成立。
- **版本串**：`STUDY_DATABASE_VERSION = 69` 与请求 schema `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18` 未动（基线 §1）；`VisualJsWire.PROTOCOL_VERSION = 2`（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt:35`；基线时点为 1，**1→2 是本批 T2 的 wire 协议面变更**，AIDL 新增 `isReady()`（`app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsService.aidl:38`）。算法台账 §3.35 零 bump：协议版本不是算法版本串。
- **热区记录时点计数**（`git status --porcelain -- <dir> | wc -l`）：`feature/library` **43**、`feature/tutor` **59**、`feature/capture` **16**、`feature/review` **12**、`core/ui` **35**、`core/export` **6**、`core/domain` **5**、`core/data` **12**、`core/database` **9**、`feature/profile` **4**；`SmartMistakeBookRoot.kt`、`SecondaryScreens.kt`、`settings.gradle.kts`、`.gitignore` 均 M；`core/markdown/` 整目录未跟踪。**热区未清空**（T6 分支 D 依据）。
- **落点脏度**：本批目标文件已按纪律整文件纳入——`VisualJsClientEngine.kt`/`VisualJsService.kt`/`VisualJsProtocol.kt`/`QuickJsVisualExecutionEngine.kt`/AIDL 为 M，`VisualJsFrameQueue.kt`、`VisualJsHandshakeRaceInstrumentedTest.kt`、两个 JVM 测试、四研究文档、T5 清单、证据目录 7 个 `.md` 为 ??，`docs/known-defects.md`、`docs/current/visual-generation-contract.md`、T4 清单为 M。
- **计划与实际的时点差**：① HEAD 多一个 KB 线提交（KD-77 已随其入库，台账引用按 `:2838`）；② 设备轨开工后两台目标模拟器 guest `system_services` 先后楔死，逐台 `emu kill` + `-no-snapshot` 冷启动恢复，全部步骤在恢复态设备上执行（`evidence/2026-10-27-batch-25/step00-environment.md` §2.1、索引 §1）；③ R3 进行期间他线实时暂存 231 文件（R3 §0），记录时点已清空（见上）；④ T4 清单在复核 1 轮又更新（P-07 复判 + 附录重定位，见 §3.4/§5）。

## 2. 四研究线与复核结论

### 2.1 R1' warm-up/ready 与队列/背压（`docs/research/2026-10-27-visualjs-warmup-queue.md`，278 行）

- **warm-up/ready 推荐 KD-76 Fix direction ①**：三个独立来源交叉（[官方/AOSP] `ZygotePreload`（官方 preload 钩子，API 29+，定性依据不采用）、[AOSP] `Watchdog`（"arm 在已注册/已 ready 的对象上"→支持"ready 之前不 arm 程序 watchdog"）、[样例] Chromium `ChildProcessService`（onCreate 专用线程装载→`mLibraryInitialized`→未 ready refuse→`setupConnection` 一次性终结握手；BSD-3-Clause，pin `ed3e89c7…`））（R1' §2.1，pin 表 §1.1）。服务端 `onCreate` 后台 warm-up + AIDL 新增 `isReady()`（`PROTOCOL_VERSION` 1→2）+ 宿主 IO 轮询 ready（独立预算 10 s 起点值，待设备校准）超时 fail-closed **不置永久 `unavailable`**（R1' §2.3-4）。
- **队列/背压推荐路径甲**：`Channel(CONFLATED)`/`MutableStateFlow` 单独表达不了 §64 组合语义（跨 program FIFO + 每 program latest-wins + 容量 2 满→BUSY）；自定义 `ArrayDeque`(cap 2) + 短临界区 `Mutex` + `CompletableDeferred` 等待者 + 常驻消费者 `drainLoop`，**无新依赖**；被顶掉同 program 旧等待者回 `Rejected(BUSY, "superseded…")`（不共享新结果——ledger 指纹错配 + §8 revision 回显违规），满队列回 `Rejected(BUSY)` 且在飞/待绘帧一概不动；死亡/close 清队回 `ENGINE_CLOSED`（**后者需主会话确认**，R1' §3.2/§6-5）。
- **`executeMutex` 最小改造**：锁职责收缩为 `queueMutex` 只保护队列与 `inFlight`，会话跑锁外；预检/预算/熔断/编码保留在入队前；端口/服务端/AIDL（除 `isReady`）零改动（R1' §5.2）。
- **§64 → 14 条断言**（R1' §4 表 A-1…A-14）：现状已有绿 6 条（引批次 23/24 设备证据），新增 8 条；**"phase 字段"（A-4）是待裁定开口项**（wire 信封无 phase 字段；两种读法都成立，需 R2/主会话定谳后才能写断言——批次 25 未定谳，未进 T4 清单，见 §5 R23）。
- **§8 实现期修正（研究复核高危项）**：§2.3-1 草图"构造 `QuickJsVisualExecutionEngine` 即触发 `QuickJs.create`"**错误**（构造函数不引用 QuickJs 类型，`QuickJs.create` 全文件仅事务路径与 warm-up 两处）——照抄会产生 ready=true 但 `libquickjs.so` 未装载的假 ready；T2 按保守分支改为 companion `warmUpNativeRuntime()`（`QuickJs.create` + 一次 `void 0;` 空程序 evaluate），服务 `onCreate` 经 jsExecutor 调用（R1' §8）。
- **UNVERIFIED**：warm-up 实测收益、READY_TIMEOUT=10 s 校准、KD-76 ③④⑤ 设备判据（本轮 E-01 只拿到部分证据，见 §3.7）。

### 2.2 R2 契约准入对照审计（`docs/research/2026-10-27-contract-admission-gap.md`，236 行）

- **对账（§0）**：清单在 HEAD `aabae781` 已是 **63 行**（`git show HEAD | grep -cE '^\| [PSMDEAQVRX]-[0-9]+ \|'` = 63，工作树同 63）；任务点名六处缺口中**三项已有行**（§4:62 启动面 P-08、§4:64 队列 P-09、§86/§155/§202 ledger A-10，均为批次 24 复核回填），**三项真缺**（§3.2:58 深冻结、§8:186 旧请求/逻辑操作指纹、§9:202 API 35+ 16KB 口径）。
- **真漏项（第一部分 1A/1B，共 11 项）**：任务三项 + 自发现八项（L-1 §4:70 programHash 熔断与重试预算承继、L-2 §7:151 Route B 三协议 sourceJs 保真、L-3 §8:188 attachedImages cache-only 与打开旧消息 0 派发、L-4 §3.1:35 全入口同管线、L-5 §4:86 两份最终图/文字降级、L-6 §7:153 三预算负向、L-7 §9:202 许可证载体缺失（assets grep quickjs 0 命中）、L-8 §2:22 R8 后 JNI 面（批次 24 R8 冒烟仅冷启动，无 QuickJS 面））。
- **可执行判据（第二部分 G-1…G-13）**：dumpsys activity providers / `queryContentProviders(":visual_js")`、隔离进程网络/filesDir 负向、客户端队列 JVM 用例、ledger 阈值边界（128 KiB+1 / 第 7 次布局 / 10 s / 513 KiB / 两份最终图 / 文字降级）、bootstrap 深冻结负向、`EncodeDefault.NEVER`/投影 + golden 逐字节比对、API≥35+arm64+16384 页套件集（隔离 6/6 + KD-70 20/20 + 装载断言）、R8 下隔离往返、许可载体 find、三预算 64/16/2 KiB 负向等。
- **判据不可执行条目（第三部分，15 行）**：P-08/P-09 无命令与 harness、P-04 沉降无定义、P-05/Q-05/R-01..R-05/S-06/E-05/Q-04/A-07/A-10 等，逐行给了可执行化路径。
- **增量表（第四部分）**：6 新增行（S-09/P-12/A-11/A-12/A-13/D-06）+ 12 改判据行；**合并红线**：不重复建 P-08/P-09/P-10/P-11/A-10。
- **状态漂移转交 T5（第五部分）**：契约 §7:155"图像通道尚未接 ledger"已过时（`DefaultFigureGenerationCoordinator.kt:126/:253-281` 已注入并预留，`RemoteModelDispatchLedger.kt:17` 六次上限+终答位、`:114` 预算耗尽拒绝）；A-10 现状行同因过时。
- **边界**：只读审计 + 单文件落盘；零 Gradle/零设备/零 git 写；"契约 §7:155 过时"是读码裁定非主会话裁定（附录 B）。

### 2.3 R3 锁文件干净树前置检查（`docs/research/2026-10-27-lockfile-clean-tree-check.md`，296 行）

- **问一（6 个解析图 build 文件）**：`settings.gradle.kts`（+`:core:markdown` include）、`core/ui/build.gradle.kts`（richtext-commonmark→project）、`feature/tutor/build.gradle.kts`（+project :core:export）、`core/markdown/build.gradle.kts`（新模块）01:01 已暂存；`core/export/build.gradle.kts`（+kotlinx-serialization-json）与 `.gitignore` 未暂存。其余构建面（根/app/benchmark build、toml、gradle.properties、wrapper、`android-check.yml`、`check_lockfiles.py`）clean。
- **阻塞级发现（§0，01:01 快照）**：他线把批 23 的 13+1 个陈旧锁随 markdown 迁移暂存；锁生成于脏树且与 HEAD 解析图不符（core/ui 锁 `richtext-commonmark` grep=0 而 HEAD 构建脚本引用它、core/export 锁 kotlinx-serialization×5 而 HEAD 无此依赖）——原样提交将触发 `check_lockfiles.py` 因 `:benchmark` 缺锁判红 + DEFAULT 模式解析期红。**需主会话裁定撤下重生成**；记录时点暂存区已空（§1.3），该风险当前不成立，但重生成前置不变。
- **问二（benchmark 补锁）**：`lockAllDependencies`（根 `build.gradle.kts:87-92`）按 subprojects 聚合，`:benchmark` 的 dependencies 与 `resolveLockableClasspaths` 都在图内，无需新任务；批 23 生成 exit=0 却无 benchmark 锁的成因 **UNVERIFIED**（报告任务在 `com.android.test` 上未解析出可锁配置 / 事后清理，两假设；gh×2+WebSearch×1 无权威命中）。补法：`resolveLockableClasspaths`（`build.gradle.kts:44-62`）执行期按后缀过滤直接 resolve；判据 `test -f benchmark/gradle.lockfile`。
- **问三（配置面）**：13 锁实测——app 仅 localFirstDebug 三条 classpath、JVM 模块连 testCompile/testRuntime 都没有、core/data 有 `litertApiUpstream`；补全 = 报告集 ∪ `resolveLockableClasspaths`；残余面（lint、release 变体 KSP processor classpath、`kotlinCompilerClasspath`）登记后按 STRICT 探针处置；`settings-gradle.lockfile` 必须一并入库否则 CI porcelain 永远看到 1 个 ??。
- **问四（完整前置清单与命令块，第 4 节）**：6 文件入库→干净树→JDK21+SDK37→pin 预检→unstage+rm 陈旧锁→`--no-configuration-cache --no-parallel lockAllDependencies --write-locks`→产物审计（15 新锁、stdlib 2.3.21/quickjs/kotlinx-serialization/richtext 反向不变量、配置面审计）→`check_lockfiles.py` exit 0→负控 5a-5c→锁与构建改动同提交→CI 双 step→P1 #38057 release/设备冒烟。**本线全部未执行**；`:core:markdown` 两分支（入库=14 锁 / 延后=-x 排除=13 锁），禁止中间态。
- **UNVERIFIED**：干净树上 `--write-locks` 实际产物、benchmark 缺锁成因、`com.android.test`×报告任务交互、AGP 推导配置名、他线暂存意图。

### 2.4 R4 arm64/16KB 覆盖可行路径（`docs/research/2026-10-27-arm64-16kb-coverage.md`，249 行）

- **ps16k x86_64 证明边界**：能证明 16KB 内核运行、x86_64 ELF 的 16KB 页宽真实装载（含页级 RELRO 运行时面；批次 24 E-04 已在 ps16k 3/3 实证）；不能证明 arm64 指令集执行、arm64 `.so` 运行时装载（ps16k 只映射 `lib/x86_64/*`，APK 内 arm64 `.so` 永不被设备 linker 触碰——本轮 python zipfile 实测当前 APK 含 arm64+x86_64 各 7 库）、真机兼容模式、arm64 性能外推（P-1…P-5 / N-1…N-6）。
- **arm64 镜像可得但本机不可跑（§2 实证链）**：sdkmanager 下载 `system-images;android-36;google_apis_ps16k;arm64-v8a` r07（4.3 GB，exit 0）→ 建 AVD `ps16k_arm64` → emulator 37.1.11.0 引导即 **FATAL 硬拒载**：`Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host. System image must match the host architecture.`——不存在慢速 TCG 选项；官方发行说明口径 arm64 系统镜像仅 arm64 宿主（KVM/HVF）；x86_64 镜像的 ARM 支持是应用级翻译（API 30 时代，非 arm64 内核）；独立 `qemu-system-aarch64` 本机无二进制且需 Linux/ARM 宿主。**这是能力缺失定论，不是耗时问题。**
- **KD-70 P2 ps16k 重跑判据与批次 24 逐字一致不放宽**（§3.3：`MEMORY_LIMIT` + `attributed by memoryUsage` + `mallocLimit=16777216` + `PENDING_CALIBRATION` + `mallocSize != -1` ≥15,099,495）；新增覆盖轴 = **16KB×x86_64**；`mallocLimit` 为应用侧预算与页宽无关预期同值；夹具 `cooperativeTimeout=10 s` 有 100× 余量；跑绿不闭合契约 `visual-generation-contract.md:22` 的"16 KB 真机加载"子句（模拟器≠真机，**等价裁定权在主会话**）。
- **设备轨交付物（§4）**：证据头模板（serial/AVD/镜像 rev/API/ABI/PAGE_SIZE/APK sha256 双侧/逐例 STATUS_CODE/签名扫描）+ 5 条可粘贴句式（A 16KB×x86_64、B P2、C arm64 轴 UNVERIFIED 引 FATAL 实证、D 真机子句、E 性能外推）+ 门↔证据↔缺口映射表。
- **资产登记（§5）**：新增 4.3 GB arm64 镜像与 `ps16k_arm64` AVD 预置待用（ARM 宿主出现即一条命令重试），移除命令已给、未删除；他线 4 台模拟器收尾复核原样在线。

### 2.5 研究复核结论

- 四份研究文档均在盘、有来源分级与未验证清单；本批实施与它们逐项对齐：R1' 的 ①方向与队列路径甲被 T2/T3 落地（含 §8 草图修正），R2 的增量表被 T4 完整并入（69 行）、状态漂移转 T5 处理，R3 只登记未执行（锁仍 open），R4 的判据模板被设备轨采用（E-03 判定含 R4 §4.2 句式）。
- 零版本串：DB schema 69、请求 schema 18、`ReviewPlannerV2`、`LearningCoreVersions.*` 全部未动（算法台账 §3.35）；`PROTOCOL_VERSION` 1→2 是 wire 协议面变更（契约 :72 与准入清单 P-07 记录），不属算法版本。
- 两处跨文档开口：R1' A-4（phase 语义）与 R2 第五部分（§7:155 措辞）均已转交，批次 25 内未定谳（§6）。

## 3. 六实施轨 + 设备 + 证据轨

> 判定口径：**代码级完成** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / JVM 工件 / 非 Gradle 直编 / 无证据=UNVERIFIED）。文件清单按工单 JSON 与 `git status` 交叉核对（§7）。
>
> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 竞态收尾 | 完成 | **代码修复成立（独立复核）/ 设备 2/2 绿 / KD-66 仍 open** | 竞态本体 aabae781 已修，本批复核+交错用例；hard+500 ms 门未动 |
> | T2 warmup 落地 | 完成 | **代码层落地 / 设备 3 例首验绿 / KD-76 仍 open** | ③④⑤ 设备判据未达；⑤ UI/文案面未落；READY_TIMEOUT 待校准 |
> | T3 队列背压 | 完成 | **代码层落地 / JVM 10 例 + 设备 2/3 绿 / A-14 用例设备红（夹具缺陷已修未复跑）** | E-01 维持 PARTIAL；两项语义决策待主会话确认 |
> | T4 清单补齐 | 完成 | **完成（文档，69 行 v2 + 复核 1 轮更新）** | P-04/P-09 现状列与 :15 总结句有残留漂移 |
> | T5 状态同步 | 完成 | **完成（文档）** | KD-66/76 保持 open；KD-77 他线条目未触碰 |
> | T6 热区条件轨 | 完成 | **完成（登记，零改动）** | 热区未空出，九排队项全部 pending |

### 3.1 T1 竞态收尾 —— **代码修复成立（独立复核）/ 设备 2/2 绿 / KD-66 仍 open**

- 文件（4）：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt`（M）、`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsHandshakeRaceInstrumentedTest.kt`（新）、`docs/known-defects.md`（M，KD-66 补充段）、`tmp/verify_batch25_t1_race_compile.py`（新，不入提交则见 §7 块 11）。
- **竞态本体不重复修**：`:788-797` death-before-registration 修复已在 `aabae781` 落地（批次 24 复核 #3/#4），本轨按基线 §10.2-2 裁定只做独立复核——`markConnected` 与 `markDead` 共用 `stateLock`（markDead 先 CAS dead 再取锁；markConnected 锁内复查 dead/ABANDONED/closed 后才置 CONNECTED），`connectLocked` 用 `isConnected()`（同一把锁内 `!dead && state==CONNECTED`）复查；对 T2/T3 演进后的当前文件逐交错推演两序均收敛（markDead 先赢→不发布；markConnected 先赢→DEAD 覆写 CONNECTED）。
- **新增确定性交错用例（2 例，fake-binder 夹具不启动真实 `:visual_js`，不作隔离门证据）**：`markDeadWinningTheHandshakeRaceIsNeverPublishedAsConnected`（握手停在发布点前最后一个握手调用，markDead 在窗口内落地后放行 → `connectForTest=false`、connectionCount=0、bind 恰 1 次）；`aDeathDuringTheRebuildHandshakeSpendsTheRebuildBudgetExactlyOnce`（重建握手再输给死亡 → connectionCount 仍 1、bind 恰 2、不第三次 bind）。红→绿推演按回归形状确定性写进 KDoc（发布点退回无条件置 CONNECTED 时 connectionCount 必变）；**运行时红绿属设备/门禁轨，本轮只有绿侧证据**。`FakeWorkerService` 补 `isReady()=true`（协议 v2 强制）。
- **措辞改正（零行为改动）**：批次 24 §5 #11"迟到的 death 会被丢弃"按实际行为改正（abandon 未确认分支注释 + logcat 文本：迟到死亡仍走 markDead/handleDeath 补完等待者，重建被 fail-closed 门挡下）；`close()` unlinkDeath 核实 `aabae781` 已按 R1 修好，无需改动。
- **台账**：`known-defects.md` KD-66:2611"批次 25 T1 补充"段扩为全量事实；引擎文件行号引用因 T2/T3 在飞持续漂移，新增台账文本一律引用稳定符号/路径。
- **设备（E-01 内）**：Race 2/2（`OK (2 tests)`，Time 1.04，`step01-visualjs-isolation.md:96-100`）。

### 3.2 T2 warm-up/ready（KD-76 ①）+ 客户端 loading 语义 —— **代码层落地 / 设备 3 例首验绿 / KD-76 仍 open**

- 文件（10）：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt`（M，`warmUpNativeRuntime` :825）、`app/src/main/kotlin/.../visualjs/VisualJsService.kt`（M）、`VisualJsClientEngine.kt`（M）、`VisualJsProtocol.kt`（M，`:35` = 2）、`app/src/main/aidl/.../IVisualJsService.aidl`（M，`:38` isReady）、`VisualJsIsolationInstrumentedTest.kt`（M，harness awaitReady + T1 夹具补 isReady）、`VisualJsHandshakeRaceInstrumentedTest.kt`、`docs/known-defects.md`（KD-76 Progress）、`docs/current/visual-generation-contract.md`（:72）、R1' 文档（§8）、tmp 两脚本。
- **服务端**：`onCreate` 经 jsExecutor 完成一次性 warm-up（`warmUpNativeRuntime`：真实 `QuickJs.create` 装载 + 一次 `void 0;` 空程序 evaluate——修正 R1' §2.3-1"构造即装载"错误草图），完成置单调 `ready`；`execute` 在 `!ready` 时回 `STATUS_SHUTTING_DOWN` 且请求不排队（**"ready 之前不 arm 程序 watchdog"由该门结构性保证**——armWatchdog 唯一调用点在过 ready 门后的 jsExecutor.execute 路径）；warm-up 失败 killSelf。不新增 `STATUS_NOT_READY`（保守分支，待主会话追认）。
- **客户端（合成于同批 T3 队列版之上）**：握手成功后轮询 `isReady()`（100 ms 间隔 / `READY_TIMEOUT_MILLIS=10_000` 独立预算，`VisualJsClientEngine.kt:785-795`/`:1223`）；attach/ready 超时 = **loading 形状**——如实 `ENGINE_CLOSED` + loading 诊断（`lastConnectFailure` 区分 loading 与结构失败）、不锁存 `unavailable`、不扣重建预算（`lastBindFailedOnLoading` 旗标豁免 `:598-599`/`:633`/`:640`），下次 execute 重新 bind；死亡/结构失败照旧锁存，死亡路径重建照旧"最多一次"记账；loading 放弃后的晚到 attach 经 `Link.abandonedForLoading` 不锁存收尾；`close()` 在 attach/ready 等待中立即终结。
- **测试**：直连类 12 例（`grep -c '@Test'` = 12；HEAD 6 + T3 3 + 本轨 3 新用例：`anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled`、`aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching`、`aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds`）；harness `awaitReady` 适配全部既有用例（断言零放宽）。
- **KD-76 已按证据追加 Progress（保持 open，③④⑤ 设备判据 UNVERIFIED）**；契约 :72 补 worst-case 29.5 s（首连 15 + ready 10 + 宿主阶段 2.5 + 死亡确认 2）与客户端 loading 语义；R1' §8 登记草图修正。
- **执行证据边界**：未运行 Gradle/设备（本轨纪律）；非 Gradle 直编五阶段全绿（`tmp/verify_batch25_t2_compile.py` 五阶段 COMPILE_OK、exit=0）+ 既有引擎 JVM 回归 2/2（`tmp/verify_batch25_t2_jvm_regression.py`）——**这两项是轨内自述，记录员与证据落盘员均未重跑**（脚本在盘、依赖本机 Gradle 缓存路径，重跑属环境敏感操作），设备侧编译经 Gradle 三件套构建 exit=0（`evidence/2026-10-27-batch-25/00-index.md` §2）覆盖同一编译面。

### 3.3 T3 队列/背压（§64）—— **代码层落地 / JVM 10 例 + 设备 2/3 绿 / A-14 用例设备红（夹具缺陷，复核 1 轮已修未复跑）**

- 文件（6）：`app/src/main/kotlin/.../visualjs/VisualJsFrameQueue.kt`（新）、`VisualJsClientEngine.kt`（M，接入）、`app/src/test/kotlin/.../visualjs/VisualJsFrameQueueTest.kt`（新，5 例）、`VisualJsResultFrameIdentityTest.kt`（新，5 例）、`VisualJsIsolationInstrumentedTest.kt`（M，+3 负向）、`docs/current/visual-generation-contract.md`（M，:64/:72）。
- **队列语义（`VisualJsFrameQueue`）**：全局 1 在飞（`inFlight` 不变量，仅 submit 直跑分支与 `scheduleNext` 取队头分支置真）、最多 2 待绘（`ArrayDeque` cap=2）、同 program latest-wins（原地替换保 FIFO 位、旧等待者回 `Rejected(BUSY,'superseded…')`）、满队列回 BUSY 且在飞/待绘帧一概不动、跨 program first-enqueue FIFO、`close()` 闩死+清队、`dropPending`（死亡/abandon 清队但不闩死）、调用方取消经 `invokeOnCompletion` 出队。
- **引擎接入**：`executeOffMainThread` 尾部改为 submit+`waiter.await()`（预检/预算/熔断/编码原样保留在入队前）；旧 `executeMutex` 整段会话体迁为消费者侧 `executeFrame`（2500 ms withTimeout + 超时 abandon + NonCancellable 死亡确认全套原样）；二次熔断复查保留；会话结果与调用方 waiter 解耦（独立 `sessionResult` deferred，杜绝调用方取消污染超时竞态判定）；每帧出队时新取 requestId/连接（排队期间可能已重建）。服务端单在飞 `STATUS_BUSY` 原样保留为纵深防御；端口/服务端/AIDL（除 T2 的 isReady）零改动。
- **消费者校验**：既有校验全数在队列路径上保持（服务端 `requireHost` 逐事务查 UID、generation 核对、AIDL 双向 `in` PFD、双向 256 KiB 有界读）；宿主回调三重身份过滤（generation/requestId/workerUid）抽成纯函数 `VisualJsResultFrameFilter` 供 JVM 负向单测，`Binder.getCallingUid` 取值留在调用点，语义零改动。
- **测试**：JVM 5 例队列状态机 + 5 例身份过滤（伪造 generation/requestId/UID/null requestId 全拒、全中才受）；仪器化 3 例负向（畸形调用参数 BAD_REQUEST 不占在飞槽【A-14】、>256 KiB 信封有界拒 INPUT_OVER_BUDGET、并发第二请求服务端 BUSY——A-5① 直接覆盖）。集成 JVM 实跑：`python tmp/verify_batch25_integration_jvm_tests.py` → COMPILE_OK + 两类各 `OK (5 tests)`（复核轨实跑、记录员未重跑）。
- **A-14 设备红与修复**：设备首轮该用例确定性红——夹具在 `:365-366` 关闭 `pipe[0]/pipe[1]` 后 `:375` 第三负向调用复用已关闭描述符，binder 客户端编组即抛 `RuntimeException: Bad file descriptor`（栈顶 `Parcel.nativeWriteFileDescriptor`），事务未达服务端（`step01-visualjs-isolation.md:136-168`）；前两个子断言（null requestId/null 描述符）在异常点前已真实通过，null-回调子断言无设备证据。**复核 1 轮已修**（`VisualJsIsolationInstrumentedTest.kt:372-383`：第三调用新开 `callbackPipe`，三个 `STATUS_BAD_REQUEST` 断言与末尾合法 execute 断言一字未改；非 Gradle 编译验证过），**设备复跑未做（本批纪律）——A-14 用例级设备证据仍缺失，E-01 维持 PARTIAL**。
- **两项语义决策待主会话确认**：① worker 死亡/abandon 时清空待绘队列为 `ENGINE_CLOSED`（R1' §3.2 保守推荐，代码已按此落、KDoc/契约双标"待确认"；备选"留队待重建"未采用）；② 调用方取消在飞帧不再 abandon worker（旧实现取消即 terminate 会连坐顶掉全部排队帧；会话按自身 1500/2500 ms 预算跑完、结果丢弃）。

### 3.4 T4 准入清单补齐 —— **完成（文档，v2 = 69 行 + 复核 1 轮更新）**

- 文件（1）：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（M）。
- **v2 合并**：头部版本与时点节（v1=63 行随 `aabae781`；v2=6 新增行+14 处改判据=69 行，`grep -cE '^\| [PSMDEAQVRX]-[0-9]+ \|'` = **69** 记录员复算吻合；合并红线：不重复建 P-08/P-09/P-10/P-11/A-10）；6 条新增行各带检查方法与判据（P-12 programHash 熔断〔G-7〕、S-09 深冻结〔G-4〕、D-06 attachedImages cache-only〔G-9〕、A-11 Route B 保真〔G-8〕、A-12 指纹 golden〔G-5〕、A-13 全入口同管线〔G-10〕）；14 处改判据（P-08 dumpsys 命令〔G-1〕、P-09 队列 harness〔G-2〕、A-10 现状改写"图像通道已接 ledger"+两份最终图/文字降级〔G-3〕、A-07 三预算负向〔G-13〕、R-03 API≥35+arm64+16384 套件集〔G-6〕、R-04 R8 JNI 面〔G-11〕、R-05 许可载体门〔G-12〕、R-01/R-02 套件集、P-04 双态窗口、P-05 禁词 grep、S-06/E-05 比较机制、Q-04 oracle 定义）；MVA 复核（0 项转"通过"，warm-up/ready 落层且设备验证前不得广告）；§4 交叉引用补 6 行；§6 两条防误读；附录 B 实跑命令。
- **复核 1 轮追加（记录员实读）**：P-07 现状列改写为"**未验（代码层已落，批次 25 T2；复核 1 轮复判）**"并列服务端/客户端落点行号，附录 `:360`/`:394` 同步重定位（KD-76 现 `:2811-2836`）。
- **残留漂移（记录员实读，§5 R22）**：P-04（`:126`）与 §6 半步清单（`:294`）仍写"最坏等待口径仍约 19.5 s"（契约 :72 已是 29.5 s，差 ready 预算 10 s）；P-09（`:131`）现状列仍写"未落…无队列"（树内 `VisualJsFrameQueue` 已落 + JVM 10 例 + 设备 BUSY 用例）；`:15` 总体判定句仍把 P-07/P-09 列入"均未落"。

### 3.5 T5 台账/契约状态同步 —— **完成（文档）**

- 文件（5）：`docs/known-defects.md`（M）、`docs/current/visual-generation-contract.md`（M）、`docs/research/evidence/2026-10-26-batch-23/00-index.md`（M）、`evidence/2026-10-26-batch-24/00-index.md`（M）、`docs/research/2026-10-27-batch-25-t5-ledger-contract-sync.md`（新清单）。
- **状态同步**：KD-69/KD-70 标题 open→closed 2026-10-27（批次 24 记录 §9 主会话裁定 `:403`/`:404`），各新增收口 Progress，缺口如实保留（KD-70：精确 `mallocSize` EXACT VALUE UNVERIFIED 只证 ≥15,099,495、x86_64/4KB 单 ABI、98 ms/10 s 协作预算风险，缺口归 P-02/R-03+R4；KD-69：引 E-03，生产签名 UNVERIFIED、release 中文 OCR 未跑如实保留）；KD-66/KD-76 保持 open（hard+500 ms 门、设备验证前不关闭）。
- **本批 T1/T2/T3 回填**：KD-66 补 T1 确定性交错用例段（`:2611`，代码层）；KD-76 补 T2/T3 Progress（warm-up/ready 代码层已落；③④⑤ 设备判据未动；第二块开头显式 supersede 注记"上一块中『⑤ 仍未落』就代码层而言被本块取代，设备判据不变"）；契约 :72 标题括注 + warm-up 子句（"设备验证前不改变广告门"）；:64 队列句由 T3 线自行回填（T5 遇 file-modified 后重读确认保留，未重复）。
- **漂移修净（V-1…V-6 实跑）**：KD-66 旧 5s/1s/8.5s 与 UNVERIFIED 全部位于带"批次 22/23 时点、已被取代"标记的块内，无裸残留；KD-76 Impact"每个新装/重启用户"收窄为证据口径（E-01d 一台冷启动模拟器实捕一次、负载 5.25）；KD-74 关闭段补批次 24 复跑引用（带"批次 23 时点"标签）；两个 00-index 行号勘误三锚（KD-74 `:2774`/`:2784`、KD-66→`:2607`/KD-70→`:2709`/KD-69→`:2678`）。
- **边界**：KD-77（API 23 冒烟评估，10 行）系他线追加，本轨未触碰（现已随 `25ddb602` 入库，锚 `:2838`）；T4 活跃文件不改，移交行号漂移与 P-07 复判（复核 1 轮已处理 P-07 部分）。

### 3.6 T6 热区条件轨 —— **完成（登记，零改动）**

- 分支 D 执行：热区开工再核确认三面全脏（`feature/library` 43 行 M、`feature/tutor` 59 行 M、`core/ui` 35 行 M+??，与记录员记录时点计数一致），按批次 24 R4 顺序（G2→G1→AWAITING→5b-1/KD-68→AUD-08→6B-3→5b-2→阶段 6→5c）对九个排队项逐项复核登记，全部仍 pending 且载体在现锚点，未重复建任何 KD/条目/文件。要点：G2/G1 文案 grep 0 命中、载体在；AWAITING UI 态映射缺口现锚 `ExplicitRedrawService.kt:431`/`:263-267`（UI 经 `MistakeDetailRoute.kt:675` 插值泄漏原始串，非已修）；KD-68 open（`known-defects.md:2644`，T6 时点值；记录员回填两段 Progress 后现 :2646），代码锚 `ReviewRoute.kt:529`/`:558`；AUD-08 门行现锚 MASTER-LINE `:146`；6B-3 app 注入缺口原样（`grep paperEditAssistant app/src/main` 0 命中）；5b-2/阶段 6 无前移触发（3D 硬门仍开）；两处漂移登记（`LearningMasteryScreen.kt` 无 WeaknessRow 符号、5c 拆批锚点 `:174` 内容对不上）。本轨零改动、零文件落盘。

### 3.7 设备轨（记录员未复跑；以下来自 E-file 与索引，记录员实读判定行）

> 环境：`emulator-5554`（test_device，API 34，x86_64，4 KB）与 `emulator-5556`（ps16k_x86_64，API 36，x86_64，**16384**）；5562/5584 属他线全程未触碰。协议 = `adb install -r -t` + `am instrument -w -r -e class …`，未跑 `connectedAndroidTest`。**环境异常（如实）**：开工后两台目标机 guest system_services 先后楔死（`Can't find service: package`；5554 crash buffer 有 `failed to set system property` + ≥5 次 system_server 重启；guest `adb reboot` 无效），宿主同时段健康——恢复 = 逐台 `emu kill` + `-no-snapshot` 冷启动，沉降 150 s 后执行（`step00-environment.md` §2.1、索引 §1）。**测试 runner 实际包名 `com.tingyun.smartmistakebook.core.data.test`**（R4 §3.2 的 localfirst.test 系笔误，未采用）。

| 项 | E-ID | 判定 | 关键事实（记录员实读索引/step 判定行） |
|---|---|---|---|
| 环境/产物身份/异常恢复 | E-00 | PASS（信息性） | 两台设备；4 个 APK sha256 双侧一致；debug 三件套 `BUILD SUCCESSFUL in 45s`；release `3m 11s`；`validateReleaseSigning` **exit=1**（`00-index.md:37-39`） |
| 隔离套件全量 | E-01 | **PARTIAL（Isolation 11/12 + Race 2/2；1 红 = T3 夹具缺陷）** | `OK` 侧：批次 24 既有 6 例零回归、T2 warm-up/ready 3 例设备首验绿（KD-76 首份设备证据）、T3 信封超预算/并发 BUSY 2 例绿、T1 交错用例 2/2 绿；红例 `aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot` 栈顶 `Parcel.nativeWriteFileDescriptor`←`VisualJsIsolationInstrumentedTest.kt:375`，确定性红、客户端编组即抛（`step01-visualjs-isolation.md:10/:139-168/:191`；Time 55.742/1.04） |
| KD-66 hard+500 ms 专属用例 | E-02 | **NOT-WRITTEN（如实登记）** | 全 androidTest 源核查无该断言（现有 watchdog 以 `withTimeoutOrNull(6_000L)` 判死，`VisualJsIsolationInstrumentedTest.kt:198`；文件 KDoc `:52` 自认）；KD-66 维持 open（`step02-kd66-hard-500ms.md:48-52`） |
| KD-70 P2 @ ps16k + 16KB 门 | E-03 | **PASS（20/20 + 3/3；arm64 UNVERIFIED）** | `OK (20 tests)` Time 7.451 + `OK (3 tests)` Time 0.025；`PAGE_SIZE=16384`；P2 判据同批次 24 不放宽（五要素 + `mallocSize != -1`），P2 耗时 34 ms（4KB 上批 24 为 98 ms）；**16KB × x86_64 PROVED；arm64 轴/真机子句/精确 mallocSize UNVERIFIED**（`step03-kd70-p2-ps16k.md:117/:146`、索引 §3.1） |
| R8 release 冷启动 ×2 | E-04 | PASS（两次 ok/COLD；生产签名 UNVERIFIED） | `validateReleaseSigning` exit=1（生产 `RELEASE_*` 缺失）→ 一次性本地 key（`CN=Batch25 Local Smoke`）`assembleStrictOfflineRelease` exit=0 → 卸 debug 装 release，两次冷启动 `Status ok`/`LaunchState COLD`（827/470 ms）、PID 存活、9 类签名 0——相对批次 24 E-03 不回退；中文 OCR 未跑（`step04-r8-release-smoke.md:46/:154`） |
| 备份/恢复 | E-05 | PASS（34/34） | 恢复 debug 态后 `BackupRestoreInstrumentedTest` 34/34（Time 20.739）、签名 0——相对批次 24 E-05 不回退（`step05-backup-restore.md:94`） |

- **隔离门结论**：T1/T2/T3 落层的设备首验绿侧成立；但 **A-14 用例级证据缺失（红例夹具缺陷，修复已落树未复跑）**，KD-66 hard+500 ms 门未覆盖，6/6 时代的"隔离门全绿"在本批不成立——JS 继续不广告。
- **KD-70 结论**：P2 在 16KB × x86_64 轴成立，与批次 24 的 4KB × x86_64 合成双页宽矩阵；**arm64 轴 UNVERIFIED（R4 §2 本机原理性不可行）**，只能写"16KB × x86_64 已验证；16KB × arm64 UNVERIFIED"。
- **KD-76 结论**：T2 三例是 ⑤ 的首份（部分）设备证据；③ 未达（E-01 为沉降态+注入形状，非真实冷启动 attach 分布；契约 §4 worst-case 29.5 s 面未设备实测）、④ 部分（warm 负控计时 ≤6 s 弱口径）、⑤ 的 UI/文案面与 R3 文本同步未做——**KD-76 不关闭，JS 不广告**（索引 §9.3）。

### 3.8 证据轨 —— **按 R4 落盘并独立复核；本记录补回链**

- **落盘**：`docs/research/evidence/2026-10-27-batch-25/` 7 个 `.md`（00-index + step00…step05），记录员实测 **1,060 行 / 63,372 B 内容 / `du -ck` 72 KiB（`du -sk` 含 `.mimosa` 为 77 KiB）**，与索引 §9.5 终测一致（§7 头部 55,376 B 是设备轨时点值，索引补记 +135 B 漂移已注明）；未超单文件 64 KiB/400 行与单批 16 个/512 KiB 上限。
- **独立复核（索引 §9，证据落盘员追加）**：索引引用 15/15 OK；脱敏扫描 secrets/credentials/emails 0 命中（home paths 1 命中 = 扫描命令自身正则，同批次 24 先例）；E-file ↔ `build/device-track-batch25/` 原始日志一致性抽查全吻合（含红例栈、P2 起止 34 ms、PAGE_SIZE=16384、两次冷启动 ok/COLD）；`@Test` 计数 12/2/20/3/34 与 summary 一致；KD-66（§9.2）/KD-76（§9.3）关闭判据结论 = 未达关闭门、维持 open。
- **回链**：索引 §7 第三项"批次 25 记录 §4 回链"由本文件 §4 完成；KD-66/KD-76 的设备证据 Progress 回填由本记录员完成（§7）。`build/device-track-batch25/` 只作原始全量，不是关闭证据。

## 4. 门禁与设备证据

> **记录员未运行 Gradle/设备**。以下"工单给定"行来自任务材料，不是本轮实证；设备数字来自 E-file，记录员只实读判定行与索引。

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 1 轮） | **exit=0**（工单给定） | 无本批仓内日志；记录员未复跑。 |
| 修复环复跑 | **exit=0**（工单给定） | 同上。 |
| 强门（第 1 轮） | **exit=0**（工单给定） | 同上。 |
| 修复环第 1 轮 | **4 条全处理**（工单给定；材料截断见 §5） | 树内核实三处落点：A-14 夹具（`VisualJsIsolationInstrumentedTest.kt:372-383`）、TOCTOU 写序（`VisualJsClientEngine.kt:694-702`）、清单 P-07 复判+附录重定位（`checklist:129/:360/:394`）。 |
| 单测门复跑 | **exit=0**（工单给定） | 同上。 |
| 设备隔离门 | **PARTIAL 11/12 + 2/2（E-01）** | `evidence/2026-10-27-batch-25/step01-visualjs-isolation.md:191`；红例归因 `:149-168`；修复后复跑命令 `:56`。 |
| 设备 KD-66 | **NOT-WRITTEN（E-02）** | `step02-kd66-hard-500ms.md:48`；KD-66 维持 open。 |
| 设备 KD-70/16KB | **PASS 20/20 + 3/3（E-03）** | `step03-kd70-p2-ps16k.md:146`；P2 34 ms；arm64/真机/精确值 UNVERIFIED。 |
| 设备 release R8 | **PASS 2×ok/COLD（E-04）** | `step04-r8-release-smoke.md:154`；生产签名 UNVERIFIED（validate exit=1）。 |
| 设备备份 | **PASS 34/34（E-05）** | `step05-backup-restore.md:94`。 |
| 生产 release 签名 | **UNVERIFIED（exit=1）** | 缺 `RELEASE_*` 四变量；一次性本地 key `CN=Batch25 Local Smoke`，未写入任何凭据。 |
| 真实 Provider | **UNVERIFIED** | 无凭据、未调用；模型质量/费用未验；JS 不广告。 |

**记录员对门禁的保留**：各门 `exit=0` 没有本批日志落在仓内，记录员未运行 Gradle；设备数字均有 E-file 但来自设备轨/证据落盘轨，记录员只实读判定行、未复跑。故本记录不把"门禁全绿/设备全绿"写成可审计结论。

## 5. 复核发现与处置

> **口径说明**：任务材料包含两组复核。①"复核发现与处置"共 23 条（下表 R1…R23，转录自复核轨 JSON，`已独立复核` 为复核轨标注）；②"修复环第 1 轮：逐条处置结果（4 条全处理）"——任务材料只完整保留了 Finding 1+4（同指 A-14 夹具缺陷）一条，其余条目文本在工单转录中截断；记录员按树内核实到**三处**修复落点（A-14 夹具、TOCTOU 写序、清单 P-07/附录），**推断**截断的两条即其中 TOCTOU 与 P-07 复判（4 条中 1+4 同指一事），此推断未获工单原文证实，如实标注。
> `已修` = 当前树文本/代码已按发现改；`已登记` = 已写入 KD/研究/清单但未闭合；`残留` = 当前树仍未改且未关闭；`转录` = 记录员未独立重验、按复核材料转述；`实读` = 记录员本轮直接核过。

| # | 位置 | 发现 | 级别 | 本批处置（记录员核验口径） |
|---|---|---|---|---|
| R1 | `VisualJsClientEngine.kt`（markConnected/markDead/connectLocked/发布点） | T1 竞态消除读码成立，两序交错推演收敛；批次 24 §5 #11 措辞改正零行为改动 | 低 | **实读吻合**（dispose/handleDeath/ABANDONED 短路/写点读码一致；KD-66:2611 段与码一致）。 |
| R2 | `VisualJsHandshakeRaceInstrumentedTest.kt` | T1 两用例与声明一致；fake-binder 不作隔离门证据；红→绿为代码层论证 | 低 | **实读吻合**（文件在盘、`grep -c '@Test'` = 2、KDoc 边界自认）。 |
| R3 | `VisualJsService.kt:111-138/:242-251/:377` + `QuickJsVisualExecutionEngine.kt:824-838` | T2"ready 之前不 arm watchdog"结构性成立；warmUpNativeRuntime 真实触达装载点；`QuickJs.create` 全文件仅 ：421/:830 两处 | 低 | **实读吻合**（`warmUpNativeRuntime` :825；AIDL :38 isReady；`VisualJsProtocol.kt:35` = 2；`git show HEAD` 同文件 = 1）。 |
| R4 | `VisualJsClientEngine.kt`（loading 语义面） | T2 客户端 loading 语义落层属实：旗标豁免/晚到 attach/close 即时终结；12 例 = 6+3+3，断言零放宽 | 低 | **实读吻合**（`lastBindFailedOnLoading` :134/:598-599/:684-708/:753-770/:892；`abandonedForLoading` :995/:1019-1026；常量 :1223 区）。 |
| R5 | `VisualJsFrameQueue.kt` + 契约 :64/:72 | T3 队列语义与契约 §64 逐条一致；接入点保留预检/预算/熔断；`VisualJsResultFrameFilter` 纯函数 | 低 | **实读吻合**（FrameQueue/两个 JVM 测试在盘；契约 :64/:72 实读；服务端单在飞 BUSY 保留）。 |
| R6 | `VisualJsIsolationInstrumentedTest.kt:365/:375` + `step01:139-160` | T3 仪器化红例 = 夹具缺陷（复用已关闭 pipe[0]），设备轨归因正确；修复权属 T3 轨/主会话 | 中 | **实读吻合**；**复核 1 轮已修**（`:372-383` 新开 callbackPipe、断言不放宽）；设备复跑未做 → E-01 维持 PARTIAL。 |
| R7 | `checklist`（v2 合并面） | 六新增行 + 14 改判据全并入 = 69 行；无重复建行；附录 B 在盘 | 低 | **实读吻合**（`grep -cE` = 69）。 |
| R8 | `checklist:129/:15/:102/:124` | 清单 P-07 现状列与树内矛盾（T2 已落层）；行号漂移（KD-76 :2805→:2811、KD-70 :2682→:2686） | 中 | **部分修**：P-07 现状列与附录 :360/:394 已由复核 1 轮改写/重定位（实读）；**:15 总体判定句与 :102/:124 行号仍残留**（§5 R22）。 |
| R9 | `known-defects.md`（T5 漂移修净） | KD-69/70 转 closed 且缺口如实保留；KD-66/76 open；KD-76 Impact 收窄；旧值全带时点标记无裸残留 | 低 | **实读吻合**（KD 标题锚回填后 :2573/:2668/:2688/:2776/:2813/:2842 逐个 grep；回填前时点值 :2666/:2686/:2774/:2811/:2838）。 |
| R10 | 两个 00-index（批次 23/24） | 行号勘误三锚全部指向正确内容 | 低 | 转录（T5 V-1…V-6 实跑在盘；记录员未逐行复读索引全文）。 |
| R11 | `evidence/2026-10-27-batch-25/`（7 个 .md） | 体量/计数/判定行一致；记录文件确认不存在（记录员待办） | 低 | **实读吻合**（7 文件、1,060 行、63,372 B、72 KiB；@Test 12/2/20/3/34；本文件即该待办）。 |
| R12 | 全批落点 vs 禁改清单 | 无 UNVERIFIED 冒充（PROVED 全带"x86_64 × 16KB × API 36"限定）、无热区越权、生产代码无 `VisualJsClientEngine` 构造点 | 低 | **实读吻合**（热区计数 §1.3；JS 不广告与 KD-66/76 open 全线一致）。 |
| R13 | `tmp/verify_batch25_t1_race_compile.py`/`t2_compile.py`/`t2_jvm_regression.py` | 轨内非 Gradle 编译/JVM 回归结果未经独立重跑；脚本依赖本机 Gradle 缓存路径 | 低 | **未复核（保持）**：记录员同样未重跑（纪律 + 环境敏感）；四脚本与三个产物目录在盘（`tmp/verify-batch25-{t1-race,t2,integration}`）。 |
| R14 | `VisualJsClientEngine.kt:694-695/:1002-1019/:910-915` | **T2 attach 超时路径写序 TOCTOU**：`abandonedForLoading` 在 `markAbandoned()` 之后写，晚到 attach 可读到 ABANDONED 却见 flag=false → `markDead(unexpected=false)` → handleDeath 先置 `unavailable=true` → 首连超时重试被永久锁存（KD-76 ⑤ 回归形状） | 中 | **复核 1 轮已修（实读）**：`:694-702` 先置 `abandonedForLoading` 再 `markAbandoned`，注释写明 volatile 写序保证与反置后果；无设备证据（逻辑修复，原窗口极窄）。 |
| R15 | `VisualJsClientEngine.kt:884/:1124-1126/:853-857/:756/:599/:632-642` | **T2 ready 超时路径旗标恢复竞态**：`lastBindFailedOnLoading` 在 abandon 后置位，binder 线程并发 handleDeath 可在其后无条件清零 → 重建路径下"重建 warm-up 超时"序列下一次 execute 命中 `rebuildConsumed && !flag` → 永久锁存（窗口更窄） | 低 | **未消除（实读）**：现树 `:761-775` 注释只防御 abandon 内部同步 handleDeath（"旗标必须在 abandon 之后置位"），`:888-892` 清零仍无条件；未发现针对"并发 handleDeath 在置位之后执行"的修复标记。是否属修复环 4 条之一无法从截断材料确认——**移交主会话裁定**（修法候选：handleDeath 清零收敛进 ABANDONED 短路之后，或两值同锁发布）。 |
| R16 | `VisualJsIsolationInstrumentedTest.kt:358-378`；`00-index §3.1/§5 #1` | A-14 设备用例树内仍是坏夹具未修；批次若就此收口将带着一棵内含红灯测试的树 | 中 | **复核 1 轮已修（实读 :372-383）**；设备复跑未做，A-14 设备证据仍缺（§6 #2）。 |
| R17 | `VisualJsService.kt:127-138/:205-251/:286-292` + `QuickJsVisualExecutionEngine.kt:825-837` + R1' §8 | 假 ready 审计未发现路径（正面结论）；残留：READY_TIMEOUT=10 s 待校准、29.5 s 面未实测 | 低 | **实读吻合**；残留按 KD-76 open 登记。 |
| R18 | `VisualJsFrameQueue.kt` + `tmp/verify_batch25_integration_jvm_tests.py` | 队列/背压语义核验通过；复核轨独立实跑 JVM 集成绿（COMPILE_OK + 两类各 OK (5 tests)） | 低 | 转录 + 脚本在盘；记录员未重跑（同 R13 口径）。 |
| R19 | `00-index.md §3-§5/§9.2-§9.4` | 证据索引如实性核验通过（PARTIAL 不粉饰、NOT-WRITTEN 如实、UNVERIFIED 三值口径、脱敏 0） | 低 | **实读吻合**。 |
| R20 | `known-defects.md`（回填后锚：KD-69 :2668 / KD-70 :2688 / KD-66 :2573 / KD-76 :2813 / KD-77 :2842）；批次 24 记录 :403-405 | T5 未发现过度关闭：closed 均有批次 24 §9 裁定原文支撑；收口缺口逐条保留 | 低 | **实读吻合**。 |
| R21 | `known-defects.md` KD-76 两个 Progress 块 | 块 1"⑤ 仍未落"与块 2"被本块取代"表面相反（有显式 supersede 注记，不算虚假）；T5 清单另有第三种口径——后续读者误判 ⑤ 状态的温床 | 低 | **残留（实读）**：supersede 注记在；建议主会话下批收口时合并两块或加导航句，本批未改。 |
| R22 | `checklist:15/:126/:129/:131/:294/:394` vs 契约 :72 | T4 清单 v2 在同批 T2/T3 落层后产生未标记漂移：P-04 与 §6 仍写 19.5 s（契约已 29.5 s）；P-07/P-09 现状被树内证伪；两处 KD 行号漂移 | 低 | **部分修（实读）**：P-07 行与附录行号已由复核 1 轮处理；**P-04 :126、§6 :294 的 19.5 s、P-09 :131"未落"现状列、:15 总体判定句仍残留**——P-09 现状与最坏等待口径需下批按树内现状回填。 |
| R23 | 契约 :64/:70；`checklist`（grep phase 0 命中）；R1' :189/:237 | 契约 §64 phase 子句在准入清单无对应行、KD 无条目（R1' A-4 待裁定项有随批次收口被无声丢弃的风险；§4:70 软期限句同类次级） | 低 | **已登记未定谳**：R1' §4 A-4 两种读法并存，需 R2/主会话定谳后才能写断言（§6 #11）。 |

**记录员追加核对（不在原清单）**：

1. **修复环材料截断**：工单"修复环第 1 轮"仅存 Finding 1+4 详情；树内三处修复落点（R6/R14/R8-P07）与"4 条全处理"数量自洽（1+4 同指一事 → 3 处 distinct），但**第 2/3 条的原文与是否另有处置不可考**，如实登记（§5 开头）。
2. `docs/REFACTOR-MASTER-LINE.md`、`algorithm-version-ledger.md`、`agent-first-refactor-decisions-2026-09-23.md` 本批开工时干净；记录员按工单"同步"要求补了顶部/4E/补修行与 §3.35 零 bump；`agent-first-refactor-decisions` **不涉**（本批两项 T3 语义决策与 T2 保守分支均**待主会话确认**，尚未构成已裁定事项，不落裁定账）。
3. 本记录 §4 的设备行均按 R4 §7.2 写成 `E-ID + evidence/…:line`；`build/` 只作 raw-log 提及，不当关闭证据。

## 6. 遗留 / UNVERIFIED / 待裁

**工程门**

1. **门禁 `exit=0` 无仓内日志**（单测门第 1 轮/修复环复跑/强门第 1 轮/单测门复跑均工单给定）；记录员未运行 Gradle。提交前建议主会话把命令与 tail 落盘 `build/`。
2. **A-14 设备复跑未做**：修复已落树（`:372-383`），复跑命令 `step01-visualjs-isolation.md:56`；复跑前 E-01 维持 PARTIAL、A-14 无用例级设备证据。
3. **KD-66 关闭门未达**：hard+500 ms 专属用例未写（E-02 NOT-WRITTEN）；现有 watchdog ≤6 s 弱口径；KD-66 open。
4. **KD-76 未达关闭门**：③ 冷启动首请求未测（E-01 三例是注入形状；29.5 s 面未实测）、④ warm 负控 ≤6 s 弱口径、⑤ UI/文案面与 R3/准入清单文本同步未做；READY_TIMEOUT=10 s 待设备校准；隔离进程内一次性装载实测成本 UNVERIFIED；KD-76 open、JS 不广告。
5. **AIDL 门**：本批未跑 Gradle AIDL 任务（T2 直编脚本是轨内验证边界，不替代）；`aidl.exe` 双输入禁用规则继续有效。

**代码/复核残留**

6. **R15 旗标恢复竞态未消除**：`handleDeath` 无条件清零与 connectLocked 置位之间的并发窗口（低、窄）；修法候选已给，需主会话裁定是否本批后修复。
7. **KD-76 两个 Progress 块 ⑤ 措辞相反**（R21）：有 supersede 注记，建议下批合并。
8. **T4 清单漂移残留**（R22）：P-04/§6 的 19.5 s、P-09"未落"现状列、:15 总体判定句。
9. **R1' A-4 phase 语义开口**（R23）：需 R2/主会话定谳；§4:70 软期限句同类。
10. **T3 两项语义决策待主会话确认**：①死亡/abandon 清队 `ENGINE_CLOSED`（代码已按保守分支落）；②调用方取消在飞帧不 abandon worker。另有 T2 保守分支两项待追认（不新增 STATUS_NOT_READY；warm-up 不加自身期限）。

**T2/T3 证据边界**

11. **tmp 三脚本的轨内结果未独立重跑**（R13）：T1 编译、T2 五阶段直编、既有 JVM 回归 2/2 均为轨内自述；设备侧 Gradle 构建覆盖编译面。
12. **红→绿推演是代码层论证**：T1 交错用例与 T2 用例的"红条件"未在运行时注入验证（本轮只有绿侧证据）。

**设备 / 证据**

13. **arm64/16KB UNVERIFIED**（本机原理性不可行，R4 §2 定论）；契约 :22"16KB 真机加载"与"API 23/arm64 运行时"子句全缺；精确 `mallocSize` EXACT VALUE UNVERIFIED（断言只证 ≥15,099,495）。
14. **生产 release 签名 UNVERIFIED**（一次性本地 key）；中文 OCR 未跑（不声称 KD-69 完整达成）。
15. **guest 楔死根因未深挖**（环境故障已恢复并登记；两台同发、宿主健康）。
16. 本批未跑迁移矩阵/`core:database` 套件（任务书四步不含；沿批次 24 E-06 结论）。

**锁 / 台账**

17. **KD-72 open**：锁未生成未入库；R3 前置清单（6 build 文件入库→干净树→重生成→审计→负控→CI→P1 冒烟）全部未执行；记录时点暂存区已空但重生成仍等整树收口；`benchmark` 补锁判据 `test -f benchmark/gradle.lockfile`。
18. **KD-73/KD-75 仍 open**（stdlib 全解析图兼容无证明；PDF `/AIGC` 零代码）；KD-77 open（他线登记，API 23 冒烟未执行）。

**热区 / 他线 / 待裁**

19. **热区九排队项未做**（T6 分支 D；R4 顺序不变）；T6 登记的两处符号/锚点漂移待下批复核。
20. **真实 Provider / Wave X** 不在本批；模型质量 UNVERIFIED。
21. **origin/main 未 fetch**：远端实时 hash 未核（HEAD `25ddb602` 是否已推送 UNVERIFIED）。
22. **Mimosa 无完整扫描结论**，不宣称项目安全。
23. **待主会话裁定汇总**：R15 竞态处置、T3 两项语义决策、T2 两项保守分支追认、A-14 复跑编排、锁重生成时点、KD-76 ⑤ 合并措辞、T4 残留回填。

## 7. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话在提交前复跑单测门/强门并留 `exit=0` 日志；**不要把 13 个未跟踪 `gradle.lockfile` 与 `settings-gradle.lockfile` 加入提交**（KD-72 open；R3 §0 的"原样提交判红"结论继续有效）；热区与 docs/current 其余 M 一律排除。本节按 ≤6 路径/块列出**建议纳入**的本批文件（新 = ?，改 = M）：

- **块 1（T2 服务端/协议/AIDL + 引擎 warm-up）**：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt`（改）、`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt`（改）、`app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsService.aidl`（改）、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt`（改）
- **块 2（客户端引擎 + 队列）**：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt`（改）、`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsFrameQueue.kt`（新）
- **块 3（JVM 测试）**：`app/src/test/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsFrameQueueTest.kt`（新）、`app/src/test/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsResultFrameIdentityTest.kt`（新）
- **块 4（仪器化测试）**：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`（改）、`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsHandshakeRaceInstrumentedTest.kt`（新）
- **块 5（四研究文档）**：`docs/research/2026-10-27-visualjs-warmup-queue.md`（新）、`docs/research/2026-10-27-contract-admission-gap.md`（新）、`docs/research/2026-10-27-lockfile-clean-tree-check.md`（新）、`docs/research/2026-10-27-arm64-16kb-coverage.md`（新）
- **块 6（基线 + T5 清单 + T4 清单 v2）**：`docs/research/2026-10-27-batch-25-baseline.md`（新）、`docs/research/2026-10-27-batch-25-t5-ledger-contract-sync.md`（新）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（改）
- **块 7（KD/契约）**：`docs/known-defects.md`（改；**整文件纳入并注明**：本批含 T1/T2/T5 改动 + 记录员 KD-66/76 设备证据回填 + 他线 KD-77 已随 `25ddb602` 入库）、`docs/current/visual-generation-contract.md`（改；整文件纳入并注明 T3 :64/:72 + T2/T5 :72 改动）
- **块 8（设备证据首）**：`docs/research/evidence/2026-10-27-batch-25/00-index.md`、`step00-environment.md`、`step01-visualjs-isolation.md`、`step02-kd66-hard-500ms.md`、`step03-kd70-p2-ps16k.md`
- **块 9（设备证据尾 + 本记录）**：`docs/research/evidence/2026-10-27-batch-25/step04-r8-release-smoke.md`、`step05-backup-restore.md`、`docs/research/2026-10-27-batch-25-record.md`（本文件）
- **块 10（主线/台账同步）**：`docs/REFACTOR-MASTER-LINE.md`（改；顶部滚动三段 + 4E/补修行，整文件纳入）、`docs/research/algorithm-version-ledger.md`（改；§3.35 零 bump）
- **块 11（可选，主会话裁定）**：`tmp/verify_batch25_t1_race_compile.py`、`tmp/verify_batch25_t2_compile.py`、`tmp/verify_batch25_t2_jvm_regression.py`、`tmp/verify_batch25_integration_jvm_tests.py`（四个非 Gradle 验证脚本；批次 24 先例是复算脚本入库、`build/` 不入；若不入库则脚本留 `tmp/` 不提交，其结论口径已在 KD/记录中转述）

**明确排除（他线在飞/不安全，一律不动）**：

- 12 个 `*/gradle.lockfile`（直见）+ `core/markdown/gradle.lockfile`（折叠于 `?? core/markdown/`）+ `settings-gradle.lockfile`：共 13+1 个未跟踪锁（KD-72 open，等 R3 §4 前置后重生成再单独提交）。
- `feature/{capture,review,tutor,library}/**`、`core/ui/**`、`core/domain/**`、`core/export/**`、`core/database/**` 他线 M、`feature/profile/**`、`core/markdown/` 整目录（他线 markdown 迁移）、`app` 其余开工即脏 M（Root/SecondaryScreens/Destinations/StartupStateBanner 等）、`settings.gradle.kts`、`.gitignore`。
- `core/data` 他线 M 与他线新文件：`RoomMistakeDetailRepository.kt`、`RoomTutorToolRunner.kt`、`capture/` 3 个 androidTest、`model/`/`study/` 3 个 test、他线新建 `core/data/src/androidTest/.../knowledge/TeachingMaterialAllocationInstrumentedTest.kt`（基线 §1.2 点名排除）。
- `docs/current` 其余 M、`docs/research` 他线未跟踪记录（`2026-10-27-ui-visual-polish-and-delivery.md` 等）、`docs/superpowers/**`、`tools/kb*`、`knowledge-production/**`。
- `build/device-track-batch25/**`（原始全量，`.gitignore` 忽略）与 `build/` 其余产物；`gradle/libs.versions.toml`、各 `build.gradle.kts`（本批零 diff）。
- 批次 23/24 证据目录的其余文件（本批只经 T5 改动两个 `00-index.md`，已入块 6 之外——注意：这两个索引属批次 23/24 目录，建议随块 6 或单独小块提交，路径：`docs/research/evidence/2026-10-26-batch-23/00-index.md`、`docs/research/evidence/2026-10-26-batch-24/00-index.md`，各 1-2 行勘误）。

**提交说明**：本批建议提交 10-11 块，每块 ≤6 路径；`docs/known-defects.md`、`docs/current/visual-generation-contract.md`、`docs/REFACTOR-MASTER-LINE.md` 均**整文件纳入并在提交说明中注明"整文件纳入，含既有/他线改动"**。提交/推送由主会话执行；非快进 merge，不 rebase。

## 8. 主会话实跑与收口状态（工单给定；待主会话追加提交/推送）

- **门禁（工单给定）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`；修复环第 1 轮 4 条全处理；单测门复跑 `exit=0`。**仓内无本批门禁日志**；记录员未运行 Gradle。
- **设备（工单给定 + E-file）**：隔离 11/12 + Race 2/2（1 红 = T3 夹具缺陷，已修未复跑）、KD-66 用例 NOT-WRITTEN、KD-70 20/20 + 16KB 3/3（16KB × x86_64 PROVED）、release 冷启动 ×2 ok/COLD（一次性本地 key）、备份 34/34；证据按 R4 落盘 7 个 `.md`/63,372 B/1,060 行，脱敏 0 命中。
- **提交/推送**：截至本记录时点，**未提交、未推送**；建议清单与排除项见 §7。提交由主会话执行；非快进 merge，不 rebase。
- **主会话裁定待办**：KD-66/KD-76 关闭门均未达（维持 open，无需新裁定）；§6 #23 的七项待裁。
- **本批不声称全绿/全就绪**：A-14 设备证据缺、KD-66 hard+500 ms 未写、KD-76 ③④⑤ 未达、arm64/真机 16KB 未测、生产签名/中文 OCR 未验、锁未入库、真实 Provider UNVERIFIED、JS 仍 `UNAVAILABLE` 不广告。
- Mimosa 提交仍无完整扫描结论——不宣称项目安全。

## 附录 A：本记录员实际执行的只读核对（可复跑；未运行 Gradle/设备/任何 git 写命令）

```bash
git rev-parse --short HEAD                 # 25ddb602
git log --oneline -3                       # 25ddb602 / d783ae84 / aabae781
git status --porcelain | wc -l             # 694
git status --porcelain | grep -c '^ M'     # 212
git status --porcelain | grep -c '^??'     # 482
git diff --cached --stat | wc -l           # 0（暂存区空；R3 的 01:01 暂存已被清）
for d in feature/library feature/tutor feature/capture feature/review core/ui core/export \
         core/domain core/data core/database feature/profile; do
  printf "%-18s " "$d"; git status --porcelain -- "$d" | wc -l; done
#   → 43 / 59 / 16 / 12 / 35 / 6 / 5 / 12 / 9 / 4
git status --porcelain | grep -i lockfile  # 12 条直见 + ?? core/markdown/（锁折叠）+ settings
git ls-files '*gradle.lockfile' | wc -l    # 0

# 协议/引擎锚点
grep -n "PROTOCOL_VERSION" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt   # :35 = 2
grep -n "isReady" app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsService.aidl           # :38
grep -n "warmUpNativeRuntime" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt  # :825
grep -n "abandonedForLoading\|lastBindFailedOnLoading" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt
#   → 写序修复注释 :694-702（"复核 1 轮 TOCTOU 修复：先置 abandonedForLoading 再 markAbandoned"）
sed -n '358,386p' app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt
#   → :372 "复核 1 轮修复（A-14 设备红例）" 新开 callbackPipe，断言未放宽

# 测试计数（五类）
grep -c "@Test" …VisualJsIsolationInstrumentedTest.kt        # 12
grep -c "@Test" …VisualJsHandshakeRaceInstrumentedTest.kt    # 2
grep -c "@Test" …QuickJsVisualExecutionEngineInstrumentedTest.kt  # 20
grep -c "@Test" …NativeLibraryPageSizeInstrumentedTest.kt    # 3
grep -c "@Test" …BackupRestoreInstrumentedTest.kt            # 34

# 台账/契约/清单锚点
grep -n "^## KD-" docs/known-defects.md | tail -12
#   → 回填前时点：KD-66:2573 / KD-69:2666(closed) / KD-70:2686(closed) / KD-74:2774(closed) /
#     KD-76:2811(open) / KD-77:2838(open)；记录员回填两段设备 Progress（:2612 区 +4 行）后：
#     KD-66:2573 / KD-68:2646 / KD-69:2668(closed) / KD-70:2688(closed) / KD-74:2776(closed) /
#     KD-76:2813(open) / KD-77:2842(open)
sed -n '64p;72p' docs/current/visual-generation-contract.md  # §64 队列处方 + 批次 25 T2/T3 落层状态句
grep -cE '^\| [PSMDEAQVRX]-[0-9]+ \|' docs/research/2026-10-26-batch-24-4e2-admission-checklist.md  # 69
sed -n '129p' docs/research/2026-10-26-batch-24-4e2-admission-checklist.md  # P-07"代码层已落，批次 25 T2；复核 1 轮复判"
sed -n '126p;131p' 同文件                                     # P-04 仍 19.5 s / P-09 现状仍"未落"（残留）

# 证据体量与判定行（只读）
wc -l docs/research/evidence/2026-10-27-batch-25/*.md        # 1060 总行
cat docs/research/evidence/2026-10-27-batch-25/*.md | wc -lc # 1060 63372
du -ck docs/research/evidence/2026-10-27-batch-25/*.md | tail -1  # 72
ls tmp/ | grep -i batch25                                    # 4 脚本 + 3 产物目录
```

> 未执行：任何 `./gradlew`、`adb`、`am instrument`、`connectedAndroidTest`；任何 git 写命令；`tmp/verify_batch25_*.py` 四脚本未重跑（R13 口径）。门禁/设备/研究结论均标注来源（工单材料/E-file/研究文档/树内实读），不冒充本记录员实跑。

## 9. 本记录的验证结果（记录员实跑，写盘后执行）

- **文档/证据引用链接检查（R4 §7.5 口径）**：对记录内全部 `docs/**.md(:line)` 与 `step*/00-index.md(:line)` 引用做文件存在 + 行号范围检查（Python 脚本，正则提取 + `test -f` + 行数范围）：**37 条引用全部解析、0 失败**（脚本输出 `resolved doc/evidence refs: 37 / problems: 0`）；另有 5 处 `.../` 缩写显示路径（§3.2/§3.3/§7 文件清单体例，沿批次 24 记录同款）不可解析、按显示文本处理。
- **补丁/空白检查**：`git diff --check` → **exit=0**（仅行尾 CRLF 提示，无空白错误；提示覆盖他线既有脏文件与本记录员四处文档改动）。
- **记录员回填后的行号自洽**：`known-defects.md` 回填两段设备 Progress（:2612 区 +4 行）后，记录内 KD 标题锚已按现值书写（R9/R20/附录 A 注明回填前后双值）；T6 的 KD-68 `:2644` 保留 T6 时点值并括注现值 `:2646`。
- **未执行**：Gradle、设备、git add/commit/push。

## 9. 主会话收口（2026-10-27，实跑证据与裁定）

- **单测门**（11 模块 `--continue`）：**exit=0**（4 executed / 224 up-to-date，R15 修复后的树）。
- **强门**（六 androidTest 编译 + `:app:assembleLocalFirstDebug`）：**exit=0**（4 executed / 268 up-to-date）。
- **主会话两项收口修复**（复核 R15/发现#13 的处置）：
  1. **R15 旗标恢复竞态（裁定+修复）**：`handleDeath` 顶部无条件清零 `lastBindFailedOnLoading` 移到 ABANDONED 短路**之后**——loading 放弃的死亡（我们主动 terminate）不再清豁免，真实非预期死亡仍清；竞态确定性消除（不再依赖窄窗写序），`VisualJsClientEngine.kt` handleDeath 头注释已写明语义。
  2. **A-14 夹具确认**：修复轮已把第三负向调用改为新开 pipe（树内在位），设备红例归因成立；复跑归批次 26 设备轨。
- **提交**：`961c6a0e`（31 文件，4,979 插入/128 删除，含块 11 的 4 个 tmp 复算脚本与两个 00-index 勘误），按 §7 分块 add；13+1 个未跟踪锁未纳入。
- **推送**：远端基线已被他线推进到 `25ddb602`（KB 批次 24），本批 `961c6a0e` 快进推送成功并 `ls-remote` 复核一致。
- **本批不声称全绿**：设备 12 例隔离 11 绿 1 红（红=A-14 夹具缺陷，已修待复跑）；KD-66 hard+500ms 用例未写（E-02 如实 NOT-WRITTEN）；KD-76 ③④⑤ 设备判据未执行；锁文件未生成（他线已把批 23 的 13+1 陈旧锁 **git add 进暂存区**——原样提交将判红，见 R3 §0，跨线协调项）；arm64/16KB UNVERIFIED；真实 Provider UNVERIFIED；JS 仍不广告。
- Mimosa 提交仍无完整扫描结论（scanner_enobufs）——不宣称项目安全。
