# 批次 26 记录（2026-10-27）：KD-66 关门 + 隔离门复跑全绿 + 锁窗口登记 + AUD-08 工具半边

> 记录时点：2026-10-27（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对计划、基线、三研究文档、九件证据 E-file、`git status`、原始设备日志（`build/device-track-batch26/` 有界抽验）后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未提交/推送、未改产品代码。** 本记录员本簿新增交付共六处：本文件、`docs/known-defects.md`（KD-66 按格式关闭 + KD-76 设备证据回填，KD-72 未动）、`docs/current/visual-generation-contract.md`（:72 的 KD-66 状态句随关闭同步，牵动面闭合）、证据索引 `00-index.md` 两处回链（§1 记录行、§7 勾选）、`docs/REFACTOR-MASTER-LINE.md`（顶部滚动三段）、`docs/research/algorithm-version-ledger.md`（§3.36 零 bump），见 §8；`docs/agent-first-refactor-decisions-2026-09-23.md` **不涉**（本批无新产品裁定，见 §6 #14）。
> 计划：`.zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md`（下称"计划"，23 行已完整读）。
> 基线：`docs/research/2026-10-27-batch-26-baseline.md`（下称"基线"；未跟踪新文件，473 行）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 1 轮 `exit=0`；强门第 1 轮 `exit=0`；无非低发现、未进修复环。仓内没有本批门禁日志，§4 只作转录。
> **本记录不声称"批次全绿"**：KD-66 已按判据关闭（设备门三轮独立绿 + 本记录回链），但 **KD-76 保持 open**（③ 冷启动判据只完成"测试 APK 内生产 client 引擎"形状，产品进程/UI 首请求与 29.5 s worst-case 链 UNVERIFIED、负载轻于批次 23 E-01d；⑤ UI/文案面与 R3 广告门文本升级未做）；**KD-72 保持 open**（窗口不可行，锁未生成未入库）；AUD-08 门 **FAIL**（① 0.5714 < 0.9，门后自动化不启用）；arm64/16 KB 运行时、KD-77 API 23、生产 release 签名、真实 Provider/模型质量 UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。

## 1. 范围与基线

### 1.1 计划范围（计划 §1/§2）

- **做**：R1 KD-66 hard+500 ms 设备用例设计；R2 4E.2 MVA 复判（P-07/P-09/P-12 状态刷新，MVA 是否仍不成立）；R3 锁重生成执行预案（T0 判定驱动两分支）；R4 hot-zone 清空顺序再评估；T1 KD-66 hard+500 ms 用例编写（androidTest）；T2 隔离套件复跑修正（A-14+R15 后全绿判据）+ KD-76 ③④⑤ 设备判据执行；T3 锁文件重生成（窗口判定驱动）；T4 AUD-08 工具半边（tools/split_golden 计量/门脚本扩展）；T5 台账/契约同步；T6 热区条件轨；设备轨；证据落盘；双门；双复核；修复；记录；提交推送。
- **实际执行面**：三研究线落盘（R1/R2/R3）+ 四实施轨（T1/锁文件轨/T4/T5）+ 设备轨四步 + 证据落盘 + 本记录与同步；R4/T6 未在工单四实施轨中单列（热区登记沿批次 24 R4 顺序维持，本批无热区空出）；T3 归并锁文件轨的分支 B 登记（窗口未开，零生成）。

### 1.2 沿用裁定（本批执行，未改）

1. **JS 不广告**：KD-66 关闭不改变广告门（KD-76 open + MVA 不成立双门仍在，§6 #3）。
2. **4E.2 交互/保真与 PDF `/AIGC` 不做**。
3. **AIDL 只以 Gradle 任务验证**；本批设备轨只用 `assemble*`/`validateReleaseSigning` 与裸 `am instrument`（`connectedAndroidTest` 零次）。
4. **AAR 钉版 `quickjs-kt-android:1.0.15` 与 kotlin-stdlib 2.3.21 不变**（记录员复跑 `check_dependency_pins.py` exit 0，§附录 A）。
5. **KD-66 关闭判据 = hard+500 ms 设备用例绿 + 记录回链**（计划 §2:21）——本批两条均达成，KD-66 已关闭（§3.5、§8）；**KD-76 设备验证前不关闭**（③ 产品面 UNVERIFIED + 文本升级未做）；**KD-72 关闭判据 = 干净树重生成 15 锁 + `check_lockfiles.py` exit 0 + 负控 5a-5c**，他线暂存区的陈旧锁不得原样提交（R3 §0 判红继续有效，本批暂存区为 0 但判据未变）。
6. **真实 Provider 无凭据**：模型质量 UNVERIFIED。

### 1.3 基线摘要与锁窗口判定（记录时点实测）

- **HEAD**：记录时点 `405c8373`（`git rev-parse --short HEAD` 记录员实测），与计划 §0、基线 §1 一致；`405c8373` 是批次 25 记录收口纯文档提交（基线 §1 实证）。
- **工作树**：基线冻结（05:42:23）`673` 行（201 M + 472 ??，staged 0）；R3 §0 时点 `674`；**锁文件轨 T0 复评与本记录员记录时点均 `688` 行 = 206 M + 482 ??（记录员实测，较 R3 §0 净增 14，来源 = 本批 T1/T4/T5 落层 + 三研究文档 + 证据目录 + 他线在飞）**；`git diff --cached --name-only | wc -l` = **0（暂存区空，记录员实测 = 锁文件轨 J2 绿）**。
- **锁窗口判定（本批 T0/锁文件轨，两次独立复评同判）**：**分支 B——窗口不可行，只登记，不生成、不删除、不提交**。四判据（R3 预案 §1 J1–J4）：J1 红（六文件全未入库且未暂存：`settings.gradle.kts`/`core/ui`/`feature/tutor`/`core/export`/`.gitignore` 5 个 M + `?? core/markdown/build.gradle.kts`；HEAD `settings.gradle.kts` 无 `":core:markdown"` include、HEAD `core/ui/build.gradle.kts:30` 仍 `richtext.commonmark`、HEAD `core/export` 无 serialization）；J2 绿（暂存总数 0、暂存锁 0）；J3 红（整树 688 条未收口）；J4 绿（`check_dependency_pins.py` EXIT=0）。生成痕迹核查：`tmp-lockfile-generation.log` 与 `.tmp/` 均不存在（复核轨实测）——脚本未跑任何生成。**14 个盘上陈旧锁（13 项目锁含 `core/markdown/gradle.lockfile` + `settings-gradle.lockfile`）全部 ??、0 已跟踪、`benchmark` 锁不存在**；陈旧性双向实证：HEAD include 图 13 模块且无 markdown 而磁盘有该模块锁、HEAD `core/ui:30` 引用 richtext.commonmark 而磁盘 `core/ui/gradle.lockfile` 中该依赖计数=0。`check_lockfiles.py` 记录员复跑 **EXIT=0**（skip 口径："13 个未跟踪锁不能原样提交；缺锁 project：:benchmark"）。等待清单：W1=六文件入库（等 markdown 迁移线/6B-2 线批次提交）→ W2=整树收口（688 条）→ W3=暂存锁（当前绿）。
- **版本串**：DB schema 69、请求 schema 18、`PROTOCOL_VERSION` 2 全部未动（基线 §1 实测；算法台账 §3.36 零 bump）。
- **落点脏度**（记录时点）：`VisualJsIsolationInstrumentedTest.kt` 为 M（T1 唯一代码改动；主 visualjs 四文件与 aidl/ 均干净）；`docs/known-defects.md`、`docs/current/visual-generation-contract.md`、准入清单、`measure_split_accuracy.py` 为 M；三研究文档、基线、证据目录（9 个 .md）、T4 新增 4 文件 + `tools/tests/test_split_gate.py` 为 ??。
- **计划/基线与实际的两处时点差**：① 计划 §0:8 写"13+1 锁已被 git add 进暂存区、4 已暂存"——基线 §5 已登记该暂存态消失（staged=0），本批两次复评一致；② 设备轨任务书写隔离套件"12 例"——T1 新门用例入类后实为 **13 例**（E-01/00-index §4 已如实登记，记录员 grep `@Test` = 13）。

## 2. 三研究线与复核结论

### 2.1 R1 KD-66 hard+500 ms 用例设计（`docs/research/2026-10-27-kd66-hard-500ms-design.md`，274 行）

- **判据语义**：hard = 服务硬期限 1500 ms（`SERVICE_HARD_DEADLINE_MILLIS`，`core/domain/.../VisualExecutionEngine.kt:126`），+500 ms 是调用方从提交到拿到有界拒绝的门自带余量，门值 2000 ms **严格紧于**宿主 2500 ms 阶段期限（`VisualJsClientEngine.kt:269`）——宿主超时兜底路径（诊断 `"host phase deadline"`，不含 "died"）被归因断言与计时断言双重拦截，不可能蒙混（R1 §1/§3）。
- **测试面裁定**：client 引擎生产调用面（`VisualJsClientEngine.execute`），不用 harness 直连（harness 是测试专用代码，其上计时测的是测试代码）；注入原样复用 `issue240Request()`（自续 job + 自旋两件套——普通死循环会被 L1 协作中断打断，不构成 #240 形状，R1 §4.1-4.2）。
- **计时口径**：`SystemClock.uptimeMillis()` 同钟端到端相减、不扣除任何段；计时前 `connectForTest()`（蕴含 ready，warm-up 装载排除在计时外——KD-76 ④ 分界）+ 预热 execute `Rendered` 负控；**不追加任何容差**（R1 §4.3-4.5）。
- **断言清单 A1–A7**（§5.2）：A1 有界拒绝 / A2 `HARD_DEADLINE_EXCEEDED` / A3 并入 A1/A2 消息 / A4 诊断含 "died"（归因先于计时）/ A5 `elapsed ≤ hard+500`（门本体）/ A6 死亡后重建 / A7 连接数=2 且新程序 `Rendered`。防过断言边界（§5.3）：不断言 kill 精确时刻、不断言 daemon 泄漏消失、不重复 BUSY 背压覆盖。
- **重试与归因**：用例内不重试（熔断+重建预算使第二次尝试必假）；关闭证据要求**两轮独立运行均绿**（§4.5）；红时归因树（按时杀迟返回=产品缺陷／迟杀=环境／无 kill 行=L2 回归），归因信息必须进 E-file。
- **与既有用例关系 = 共存**（§6 裁定）：两处 6 s 死亡观察与状态机用例原样保留（删除断言需独立正当性），新用例为关闭门专属证据；连带修订点：`@Test` 12→13、套件判据文本相应更新、"KD-66 关闭还需记录回链，本设计文档本身不构成关闭"。
- **UNVERIFIED（自登记）**：未编译未设备（设计轨禁 Gradle）；500 ms 余量在真实设备负载分布下的实测裕度 UNVERIFIED；上游 2026-10-25 commit 引用 issue #240 的观察为单一来源待验证（§7）。

### 2.2 R2 MVA 复判（`docs/research/2026-10-27-mva-reeval.md`，173 行）

- **四行现状刷新**（增量表 §1）：P-04 = LOADING/有界重试代码已落 + 注入形状设备绿（部分），最坏等待 19.5 s→**29.5 s**；P-07 = ⑤ 首份（部分）设备证据在手、③ 未执行、④ 弱口径，**本门未过**；P-09 = 代码 + JVM 10 例全落、设备 2/3（1 红=A-14 夹具已修未复跑）；**P-12 勘误——v2"未落"是口径错误**（把"清单无行"读成"代码未落"）：熔断代码自批次 20/21/22 已存在（`git log -S` 实证 `29b46f21`/`1a6bf486`/`69e5f591`），`PROGRAM_FUSED` 设备断言自批次 22 随 E-01 各轮绿（G-7 ①已有设备绿），真缺的是显式重试预算承继（②，无任何证据）与解除路径断言。
- **MVA-JS-STATIC 维持不成立**（§2）：批次 25 落层全在前提门/执行面；广告面六处仍全拒、S/M/D/E 组类型零命中、DB 仍 v69、P-05/P-07/P-08/P-10/P-11/P-12 未过、隔离面当时 11/12、KD-66 门 NOT-WRITTEN。结论：`availableMethods` 不得加入 JS；v2 `:15` 判定句**结论维持、理由表述需刷新**。
- **给 T5 的漂移登记 6 条**（§4）与批次建议（§3：A-14 复跑/KD-66 用例/KD-76 ③④⑤ 归批次 26；P-04 双态、P-08 dumpsys、P-09 客户端队列负向、P-12 G-7 断言建议并入同设备轮）。**0 项转"通过"；本轮全部设备绿为转录非实跑**（头部口径）。

### 2.3 R3 锁重生成执行预案（`docs/research/2026-10-27-lockfile-execution-plan.md`，414 行）

- **T0 快照 S1–S10**（§0）：HEAD `405c8373`、staged 0、14 锁全 ?? 无提交史、六文件入库判据不满足、树 674 条、`check_lockfiles.py` EXIT=0（skip）、pins EXIT=0。**T0 判定 = 分支 B**。
- **J1–J4 窗口判定程序**（§1，<1 分钟全只读）：J1 六文件（工作树干净 + markdown/include 在 HEAD + HEAD 已换 richtext/已加 serialization）、J2 暂存无锁、J3 整树干净、J4 pins/java/SDK；分支选择规则与等待清单（J1 红→等 W1；J3 红→等 W2；J2 红→先处置 W3）。
- **分支 A 完整序列**（§2）：A0 前置自查 → A1 清 14 陈旧锁（unstage+rm，再生性产物无需回滚）→ 生成 → 审计 → `check_lockfiles.py` → 负控 5a-5c → 分块提交 → CI 双 step → P1 冒烟；每步带判据、失败归因（本批/他线/环境）与回滚。**分支 B**（§3）：只登记，子集预演若要删 `core/markdown/gradle.lockfile` 须主会话批准（热区）。机制论证一律引用批次 25 R3 不重复。
- **本轮执行边界**：预案轨只读（git 查询/两 CI 脚本/文件实读），未运行 Gradle、未改 build 文件；`§2/§3` 所有 Gradle 命令均由主会话门禁轨按预案串行执行（本批窗口未开，未执行）。

### 2.4 研究复核结论

- 三份研究文档均在盘、有来源分级与未验证清单；实施与它们逐项对齐：R1 的判据/断言面被 T1 逐条落码（A1–A7、门常量相对形式、共存裁定），R2 的增量表被 T5 完整并入清单 v3（69 行不变），R3 的 J1–J4 被锁文件轨复评（两次同判分支 B）并被设备轨 T0 引用。
- 零版本串：DB 69、请求 schema 18、`PROTOCOL_VERSION` 2 未动（算法台账 §3.36）。
- 无跨文档开口新增：R1 §7 的钉版升级观察（单一来源）登记于 §6 待裁；R2 §3 的批次建议中"并入同设备轮"部分未在本批设备轮执行（P-04 双态/P-08 dumpsys/P-09 客户端队列负向/P-12 G-7 断言仍缺，§6 #3/#13）。

## 3. 四实施轨 + 设备 + 证据轨

> 判定口径：**代码级完成** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 工具实跑 / 无证据=UNVERIFIED）。文件清单按工单 JSON 与 `git status` 交叉核对（§7）。
>
> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 KD66 用例 | 完成 | **落码成立（记录员实跑复核）/ 设备三轮独立绿 → KD-66 达关闭门并已关闭** | 落码轨本身未跑 Gradle/设备；执行证据归设备轨 |
> | 锁文件轨 | 完成 | **完成（分支 B 登记，零改动零生成）** | 窗口未开（J1/J3 红），KD-72 open |
> | T4 AUD-08 工具半边 | 完成 | **工具面闭环（记录员复跑 25/25 + 全量 833 例绿）/ 门 FAIL、自动化不启用 / 设备与模型执行归后续** | 门未过是数据事实非工具缺陷；CI 接线未做 |
> | T5 状态同步 | 完成 | **完成（清单 v3 + 契约 :72 + KD-76 合并；登记时点设备未出数）** | 设备出数后的回填由本记录员完成（§8） |
> | 设备轨 | 四步执行完毕 | **四步全执行：13/13+2/2、KD-66 门三轮绿、KD-76 ③④⑤ PARTIAL、R8+备份不回退** | 判定边界见 §3.5/§4 |

### 3.1 T1 KD-66 hard+500 ms 用例落码 —— **落码成立 / 设备三轮绿 / KD-66 已关闭**

- 文件（2）：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`（M，唯一代码改动）、`docs/known-defects.md`（M，KD-66 条目；现含记录员关闭块）。
- **红基线先证后写**：改前对全 visualjs androidTest 源（仅 2 文件）grep 无任何 hard+500 ms/elapsed 计时断言——`hard+500` 唯一命中是类 KDoc 自述"不能作为关闭门"（改前 :52），既有 watchdog 只以 `withTimeoutOrNull(6_000L)` 判死，无 `elapsed`、无 `SERVICE_HARD_DEADLINE_MILLIS` 引用（复核轨经 `git show HEAD` 实证；HEAD `@Test`=12、现 13）。"无 500 ms 断言即无法关闭 KD-66"由此钉住。
- **新用例** `aHungWorkerReleasesTheCallerWithinHardPlus500ms`（记录员实测 `:373`，类内第 13 例，`grep -c '@Test'`=13）：client 引擎生产调用面；`connectForTest()` 蕴含 ready（warm-up 排除在计时外）+ 预热 execute 断言 `Rendered` 负控；计时端到端 `SystemClock.uptimeMillis()` 同钟相减不扣除；断言 A1 有界拒绝、A2 `HARD_DEADLINE_EXCEEDED`、A4 诊断含 "died"（归因死亡路径、先于计时）、A5 `elapsed ≤ HARD_PLUS_500_GATE_MILLIS`（记录员实测 `:776-777`，相对形式 `SERVICE_HARD_DEADLINE_MILLIS + 500L`，500 ms 是门自带余量不追加容差；2000 ms 紧于宿主 2500 ms 期限）、A6 死亡后重建、A7 连接数=2 且新程序 `Rendered`。
- **与既有用例共存**（R1 §6）：diff 纯新增（复核轨实测 vs HEAD = 106 增/2 删，删的恰是被替换的 KDoc 两行），未动任何既有断言；类 KDoc 更新注明门由新用例承担、6 s 观察非门证据。
- **KD-66 条目**（落码时点 :2615 T1 块）：如实写"Gradle 门编译与设备执行均未发生……本条保持 open"——该缺口已由设备轨消除，现被记录员关闭块（:2619）取代（§8）。验证边界（落码轨）：非 Gradle 直编复用批次 25 脚本 `tmp/verify_batch25_t1_race_compile.py` → `MAIN_COMPILE_OK` + `TEST_COMPILE_OK`（exit 0）；零 Gradle、零设备、零 git 写。

### 3.2 锁文件轨（KD-72 / 计划 T3）—— **完成（分支 B 登记，零改动零生成）**

- 分支 B 判定与四判据复评见 §1.3；**本轨零文件改动**：全部命令只读，未运行 Gradle，未做 git 写操作，未 `git add` 任何锁。
- **14 个陈旧锁处置 = 维持原状**：不得 `git add`/提交（R3 §0 判红 + 计划 §0 判红条款）；本批不含任何把陈旧锁写入提交建议的内容（§7 排除清单）。
- `check_lockfiles.py` 现状 EXIT=0（skip 口径，明示"不能原样提交；缺锁 project：:benchmark"——记录员复跑同值）；KD-72 台账条目本轨未改（保持 open），设备轨式回填不适用；预估：每批次 T0 复评 <1 分钟（全只读），窗口开后按 R3 §2 预案执行（A2 全量生成参考批 23 实跑 2m55s + 审计/负控 4 次 Gradle 调用 + 3 块提交 + CI 双 step + P1 冒烟——均为预案引用值非本轨实测）。

### 3.3 T4 AUD-08 工具半边 —— **工具面闭环 / 门 FAIL、自动化不启用 / 设备与模型执行归后续**

- 文件（9）：`tools/split_golden/check_split_gate.py`（新）、`export_split_eval.py`（新）、`measure_split_accuracy.py`（M，新增 `--json` 机器口径）、`GATE.md`（新）、`README.md`（新）、`tools/tests/test_split_gate.py`（新，25 例）、证据 2 件（`step-aud08-tool-gate.md`/`step-aud08-gate-evidence-out.md`）、`docs/current/visual-generation-contract.md`（M，:200 切分门登记段）。
- **门判定器**：先强制金标冻结校验（带病金标 exit 2 拒判）→ 度量 → 按 0.9/0.05 **闭边界**判定，exit 0/1/2/3；阈值带出处（stage1 记录 :19-22"立项自定"）随报告落盘，报告含 wilson95 区间（描述性）与逐页裁定；门不过打印"门后自动化不启用"，**门过也不切任何产品开关**（`automation.enabled` 恒 false）。
- **导出器**：`export_split_eval.py` = R4 §3.7 指名的 DB 快照→`app-split.json` 生产者（只读 sqlite、同快照同 sha、页名三级解析逐页标 `nameSource`、拒重复页名/非法框）；**真设备 DB 实跑 UNVERIFIED**（本轨不启动模拟器）。
- **真实复算**：金标 v1（sha `f9e92c47…`）validate C1–C6 PASS；①=4/7=**0.5714**<0.9、③=0/16=**0.0** → 门 **FAIL（exit 1）**，与 stage1 记录 §9 v2 逐值一致（同一份 1482 B 存量导出、同口径、可复跑；该导出无溯源块，来源绑定 UNVERIFIED）。
- **测试**：`test_split_gate.py` 25 例先红后绿（红 = 实现前 `ModuleNotFoundError`）；覆盖阈值闭边界（0.9/0.05 恰过与恰不过）、报告形状、冻结校验联动、`--json` 同源、导出器机制、入口冒烟。**记录员实跑复核**：`python -m unittest tools.tests.test_split_gate` → `Ran 25 tests … OK`；CI 同口径 `python -m unittest discover -s tools/tests -t tools` → **`Ran 833 tests … OK`、EXIT=0**（与复核轨 pytest 833 passed 一致）。
- **规范/台账**：`GATE.md` 五类指标门说明（分母/阈值/样本量/统计区间/严重度；漏题间接、错误合并/重复率**未立项未实现**、train/dev/acceptance 未分均如实登记，不编造数值；"不得把 v1 称作人工金标"）；契约 :200 登记段（"模型标注代理金标，无人工专家……当前实测门 FAIL……门后自动化不启用，切残不得自动入库红线不变"）。**设备/模型执行与 CI 门禁接线如实登记为后续**（.github 不在本批落点）。

### 3.4 T5 台账/契约同步 —— **完成（登记时点设备未出数；设备回填归记录员，§8）**

- 文件（3）：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（M，v3）、`docs/known-defects.md`（M）、`docs/current/visual-generation-contract.md`（M）。
- **清单 v3**：R2 增量表并入——P-04/P-07/P-09/P-12 四行现状刷新（记录员实测四行在 `:127/:130/:132/:135`），P-12 勘误（v2 口径错误），总体判定句理由更新（**结论维持不成立**），§6 半步条目改写，§0 新增 26-T1/T2/T5 记号行；**不新增行（行数复算 = 69，记录员 grep 同值）、0 项转"通过"**；附录 C（`:399`）登记 T1 跨线先行写入 known-defects（+2 行、锚点漂移、`@Test`=13）。
- **契约 :72 两处刷新**（记录员实读现文）：warm-up/ready 子句 → "批次 25 设备轨注入形状首验绿——两类均绿，③ 真实冷启动首请求未达、④ 负控计时弱口径、⑤ UI/文案面未做，设备判据归批次 26 T2"；队列子句 → "设备侧仅服务端层 BUSY/有界读两例首验绿，经 `engine.execute` 的客户端队列负向未执行，归批次 26 T2"；队列"取消在飞帧不再 abandon worker"补**待主会话确认**标注（对齐批次 25 记录 §3.3）。旧短语"设备复跑 UNVERIFIED"0 残留（复核轨 grep）。
- **KD-76 块合并**（known-defects）：批次 25 两 Progress 块（T2/T3 与"T2 续"）合并为一块并消解"⑤ 仍未落"与"被本块取代"的措辞矛盾，设备判据不变；R15 旗标竞态修复与 A-14 夹具确认并入合并块；KD-66/KD-76 各追加"批次 26 T5 登记；设备轨未出数"块（:2617/:2847，现被记录员设备回填块取代）。批次 25 复核漂移项收尾：P-04 19.5s→29.5s（含 ready 10s 明细）、P-07 warm-up 落层 + ⑤ 首份（部分）、P-09 队列落层（锚点 + JVM 10 例 + 设备 2/3）。
- **核对清单通过**（复核轨）：清单 69 行、四行各 7 列、旧失真表述 grep 0 命中、契约旧短语 0 残留、`git diff --check` exit=0（仅 CRLF 提示）。**跨线在飞如实登记**：known-defects 在 T5 读取与编辑之间被 T1 轨先行写入 KD-66 块；T1 落码致测试类行号漂移（`@Test` 12→13，6 s 弱口径现 :202/:552），T5 全部台账引用按实测终值重写并标注"按符号定位"。**设备轨未出数如实**：登记时点 evidence 下无批次 26 目录，不预填判定。

### 3.5 设备轨（四步；记录员未复跑，以下转录自 E-file 并经原始日志有界抽验）

> 环境：`emulator-5554`（test_device，API 34，x86_64，PAGE_SIZE 4096）；`emulator-5556`（ps16k_x86_64）只读核（本批四步不含 KD-70/16KB）；5562/5584 属他线全程未触碰。HEAD `405c8373`；只跑 Gradle `assemble*`/`validateReleaseSigning` + 裸 `am instrument`，`connectedAndroidTest` 零次；git 写操作零次；产品/测试源码零改动。**任务书"12 例"实为 13 例（T1 新门用例入类）**。本批无设备环境故障；唯一一次 `adb reboot` 是 E-03 ③ 的判据本体。

| 步 | 判定 | 关键事实（E-file 锚点） |
|---|---|---|
| 一、隔离套件全量 | **PASS：Isolation 13/13 + Race 2/2** | `OK (13 tests)` Time 59.33 / `OK (2 tests)` Time 1.044（`step01` §3；记录员 grep 两 log 各命中）；批次 25 唯一红例 `aMalformedCallerRequest…`（A-14 夹具缺陷）修复后**设备转绿**，第三个负向子断言（null 回调→BAD_REQUEST 不占在飞槽）**首获设备证据**；R15 修复后 Race 复绿；崩溃签名 9 类 + `death was not confirmed` 全 0（`step01` §3/§5） |
| 二、KD-66 关闭门 | **达门：三轮独立运行全绿 → 关闭（记录员执行，§8）** | `aHungWorkerReleasesTheCallerWithinHardPlus500ms`：E-01 套件轮 + 聚焦 run2 `OK (1 test)` 3.142 s + run3 `OK (1 test)` 3.081 s（`step02` §3），超过 R1 §4.5（`:128`）两轮独立绿判据；每轮 logcat **恰 1 条** `hard deadline (1500 ms)` kill 行且请求 ID 各异（`747412e7`/`71ec7659`/`18a15171`，记录员对 run2/run3 logcat 逐条 grep 复核各=1），无隐藏重试；断言 A1–A7 完整执行、门常量未动、未放宽；logcat 锚点：warm-up finished→kill(+~1500 ms)→死亡后 32 ms 重建→新进程 ready→绿（`step02` §3） |
| 三、KD-76 ③④⑤ | **PARTIAL** | **④ 强证据**：warm 服务（ready 门后）#240 挂死被 1500 ms 硬期限 SIGKILL（kill 行实测提交后 ~1.52–1.55 s，`step03` §4.1）+ 调用方 hard+500 ms 有界返回（E-02 三轮）+ 新 generation 无 BUSY；**⑤ 行为层 ×2 轮绿**：加载态三用例（首连超时/ready 超时/warm-up acquire）沉降轮与冷启动轮全绿；**③ 尝试成功但边界如实**：`adb reboot` 冷启动（boot_completed 后不沉降，loadavg 0.75）即跑 13/13 全绿（Time 64.072；`step03-coldboot-suite.log` 记录员 grep 命中），5 条 kill 行逐条归因负向夹具设计内注入（门用例 `:387`、in-flight-1 `:533`、rebuild 用例 `:298`/`:327`〔§9 更正后〕、issue-240 `:201`），零意外杀、零首请求杀，冷态 warm-up(~1.9 s)→首个 execute `Rendered` 全链成立；但负载轻于批次 23 E-01d（0.75/4.31 vs 5.25）、调用方是测试 APK 内生产 client 引擎非产品进程/UI、29.5 s 链与低内存 UNVERIFIED——**③ 不判定达成**（`step03` §5） |
| 四、R8 release + 备份 | **PASS（不回退）** | `validateReleaseSigning` exit=1（无生产 `RELEASE_*`，原始证据）→ 一次性本地 key（`CN=Batch26 Local Smoke`，非生产证书）→ `assembleStrictOfflineRelease` exit=0（2m1s）→ 两次冷启动 `ok`/`COLD`（1086/524 ms，PID 存活）9 类签名 0（批次 25 同判据 827/470 ms）；备份 `BackupRestoreInstrumentedTest` **34/34**（Time 24.026，记录员 grep 命中）签名 0；设备已恢复 debug 态（三 install Success）（`step04`/`step05`） |

- **隔离门结论**：隔离面全绿成立（A-14 用例级设备证据补齐、R15 复绿、既有 10 例零回归）；JS 继续不广告（KD-76 open）。
- **KD-76 结论**：④ 强证据 + ⑤ 行为层 ×2 轮 + ③ 部分形状（测试 APK 内 client 引擎、负载轻）——**KD-76 不关闭**（③ 产品面 UNVERIFIED、R3 广告门文本升级未做，`step03` §7；关闭另一半归主会话）。

### 3.6 证据轨 —— **按 R4 落盘并独立复核；本记录补回链**

- **落盘**：`docs/research/evidence/2026-10-27-batch-26/` **9 个 `.md`**（00-index + step00–05 + step-aud08 ×2），记录员实测 **70,134 B**（单文件最大 `step03` 12,671 B / 188 行）；R4 上限（单文件 64 KiB/400 行、单批 16 个/512 KiB）未触。**时点差如实**：工单材料转述的"65 KiB/单文件 ≤159 行/11 KiB"是 `step03` §9 更正追加**前**的设备轨时点值，终值以索引 §6 与记录员实测为准（更正 +29 行：`UnsatisfiedLinkError` 1 条=系统进程噪声已归因、两处源码锚点 off-by-one `:299→:298`/`:321→:327`，判定不变）。
- **独立复核（索引 §6，复核轨实跑）**：脱敏扫描 secrets/credentials/emails 0 命中、home paths 1 命中=扫描正则自身（批次 24/25 同例）；`<local-smoke>` 占位无明文残留；E-file ↔ `build/device-track-batch26/` 原始日志一致性抽查吻合（13/13、kill 行、P2 面、COLD、34/34）。
- **回链**：索引 §7 的"KD-66/76 回链"勾选由本记录员完成（:128）；KD-66/KD-76 设备证据回填由本记录员完成（§8）。`build/device-track-batch26/` 只作原始全量，不是关闭证据。

## 4. 门禁与设备证据

> **记录员未运行 Gradle/设备**。"工单给定"行来自任务材料；设备数字来自 E-file，记录员只实读判定行与有界抽验原始日志（附录 A）。

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 1 轮） | **exit=0**（工单给定） | 无本批仓内日志；记录员未复跑。 |
| 强门（第 1 轮） | **exit=0**（工单给定） | 同上。 |
| 修复环 | **未进入**（工单给定：无非低发现） | 无修复环轮次。 |
| 设备隔离门 | **PASS 13/13 + 2/2（E-01）** | `step01-visualjs-isolation.md` §3；A-14 转绿、R15 复绿。 |
| 设备 KD-66 关闭门 | **PASS 三轮独立绿（E-01/E-02）→ KD-66 关闭** | `step02-kd66-hard-500ms.md` §3/§8；关闭块 `known-defects.md:2619`，回链本节。 |
| 设备 KD-76 ③④⑤ | **PARTIAL（E-03）** | `step03-kd76-criteria.md` §7；③ 不判定达成、KD-76 open。 |
| 设备 R8 release | **PASS 2×ok/COLD（E-04）** | `step04-r8-release-smoke.md` §3；生产签名 UNVERIFIED（validate exit=1）。 |
| 设备备份 | **PASS 34/34（E-05）** | `step05-backup-restore.md` §3。 |
| 锁文件生成 | **未执行（窗口不可行，分支 B）** | R3 预案 §0 + 锁文件轨 J1–J4（§1.3）；KD-72 open。 |
| AUD-08 切分门 | **FAIL（exit 1）：① 0.5714 < 0.9 → 门后自动化不启用** | `step-aud08-tool-gate.md` §3.5 + 契约 :200；工具面测试 25/25 + 833 全绿（记录员实跑）。 |
| 生产 release 签名 | **UNVERIFIED（exit=1）** | 缺 `RELEASE_*` 四变量；一次性本地 key `CN=Batch26 Local Smoke`，未写入任何凭据。 |
| 真实 Provider | **UNVERIFIED** | 无凭据、未调用；模型质量/费用未验；JS 不广告。 |

**记录员对门禁的保留**：各门 `exit=0` 没有本批日志落在仓内，记录员未运行 Gradle；设备数字均有 E-file 且记录员做了有界原始日志抽验，但未复跑。故本记录不把"门禁全绿"写成记录员实证，设备结论标注为"设备轨/复核轨实跑、记录员转录+抽验"。

## 5. 复核发现与处置

> **口径说明**：下表 R1–R21 转录自复核轨（"复核轨判定"列为其标注的 `已独立复核`/`未复核`）；末列为本记录员本轮核验口径——`实跑复核吻合` = 记录员本轮重跑了该检查并同值；`抽验吻合` = 记录员核了其中的可只读抽验子项；`转录` = 记录员未重跑、按复核材料与在盘证据转述。21 条全部级别=低，无中/高危发现，未进修复环。

| # | 位置 | 发现 | 复核轨判定 | 记录员核验口径 |
|---|---|---|---|---|
| R1 | `VisualJsIsolationInstrumentedTest.kt:373,:776-777`；`known-defects.md`（落码时点 :2615 块） | T1 落码与红基线声明全部证实：@Test 12→13、diff vs HEAD=106 增/2 删（删=被替换 KDoc 两行）、HEAD 无 elapsed/SERVICE_HARD_DEADLINE_MILLIS 引用、A1–A7 齐全无容差、6 s 用例共存 | 已独立复核 | **实跑复核吻合**（@Test=13、:373 用例名、:776-777 相对门常量，grep/sed 实测）。 |
| R2 | `VisualJsService.kt:485-499,:86-89`；测试 `:408-416`；`step02` §3 | T1 计时判据 flaky 评估：kill 结构性确定（独立 watchdog 线程 CAS+SIGKILL 不拿锁）、余量 ~450–480 ms（kill 行 ~1.52–1.55 s）、失败方向安全（环境慢只产生假红，2500 ms 兜底被 A4+A5 双拦截）、3 次独立绿；非构造性 flaky，残余=共享模拟器极端负载假红，设计已文档化接受 | 已独立复核 | 抽验吻合（run2/run3 kill 行各恰 1 条为记录员 grep；watchdog 读码未重做）。 |
| R3 | `build/device-track-batch26/step01-*.log`、`step03-coldboot-suite.log` | A-14+R15 修复后设备全绿：`OK (13 tests)` 59.33 / `OK (2 tests)` 1.044 实测；9 类签名在 1,922 行 logcat 全 0；A-14 第三子断言首获设备证据；夹具修复在 HEAD、断言未放宽；冷启动轮 5 条 kill 归因表逐条吻合 | 已独立复核 | 抽验吻合（OK(13)/OK(2)/OK(34) grep 命中；9 类签名全量 grep 未重跑）。 |
| R4 | 仓库根 git 态；`settings.gradle.kts`（HEAD）；`core/ui/build.gradle.kts:30`（HEAD）；两 CI 脚本 | 锁分支与窗口判定一致：J1 红（六文件未入库）、J2 绿（staged=0）、J3 红（688=206M+482??）、J4 绿；陈旧性双向实证；14 锁全未跟踪；check_lockfiles exit 0 skip 口径；无生成痕迹 | 已独立复核 | **实跑复核吻合**（staged/688/pins/check_lockfiles 记录员同值复跑）。 |
| R5 | `known-defects.md`（复核时点 KD-66 :2573 / :2615 / :2617、KD-72 :2744、KD-76 :2817/:2845）；记录文件（当时不存在） | KD-66/72/76 状态与证据一致、无状态冒进：三态均 open、台账写于设备出数前、关闭动作留记录员/主会话 | 已独立复核 | 转录 + 实读；**本文件即该"待办"的执行**（KD-66 已关、KD-76/72 维持 open，KD-72 锚回填后漂至 :2746）。 |
| R6 | `checklist:11,:127,:130,:132,:135,:399`；契约 `:70-72,:200` | 清单 v3 刷新正确：69 行实测、四行 v3 标记与内容命中（29.5 s 链、③④⑤ 口径、P-09 落层、P-12 勘误）、附录 C 登记 T1 跨线、唯一 19.5 s 残留是 P-06 行的历史转录非失真 | 已独立复核 | **实跑复核吻合**（69 行、四行锚、附录 C :399、契约 :72/:200 实读）。 |
| R7 | `step03` §5；`GATE.md:60-88`；契约 :200；`mva-reeval` 头部 | UNVERIFIED 冒充检查：未发现冒充——PARTIAL 非 PASS、关闭动作明写不在轨内、"未立项未实现"如实、"不得称人工金标"、validateReleaseSigning exit=1 以原始证据呈现 | 已独立复核 | 转录 + 契约 :200 实读吻合。 |
| R8 | `git diff HEAD` hunk 头；`git status`（visualjs/aidl/tools） | 热区越权检查：未发现——三台账文件 hunk 集中于声明改动区；主 visualjs 四文件与 aidl 干净（仅测试文件 M）；T4 新增 5 文件均 ?? 与清单一致；开工即脏 M 未触碰 | 已独立复核 | 抽验吻合（visualjs/aidl git status 干净、仅测试文件 M，记录员实测）。 |
| R9 | `check_split_gate.py:70-71,:214,:248`；`test_split_gate.py`；`tools/tests/` | T4 独立复现：25/25 passed；CI 同口径 833 passed + 70,794 subtests、exit 0；门判定 FAIL exit 1（① 0.5714、③ 0.0000）；阈值常量未动、闭边界、exit 0/1/2/3、"不切产品开关" | 已独立复核 | **实跑复核吻合**（unittest 25/25 OK + discover `Ran 833 tests … OK` EXIT=0，记录员本轮重跑；门判定器 exit 1 未重跑）。 |
| R10 | `evidence/2026-10-27-batch-26/`（9 个 .md）；`step03` §9；原始日志 | 证据落盘与更正独立复核：9 文件 70,134 B、最大 12,671 B·188 行、R4 上限未触；§9 更正只追加且内容证实（UnsatisfiedLinkError=系统进程 PID 3177 噪声、锚点更正经源码逐行实测）；step04 COLD/备份/validate 原始命中；无明文口令；5562/5584 零引用 | 已独立复核 | 抽验吻合（9 文件/70,134 B 记录员实测；体量时点差见 §3.6）。 |
| R11 | `build/device-track-batch26/`（设备执行）；pins 输出（java 项）；`build-release.log`；`step04-pid*.txt` | 未复核项如实声明：设备用例未由复核重跑、java 21.0.11 未复验、2m1s 构建时长未复读、release PID 未复读、"5562/5584 未触碰"只能证日志零引用（否定性命题） | 未复核 | 转录保留，并入 §6 #2。 |
| R12 | 测试 `:373-435,:770-777` + R1 设计 §5.2 | Q一：hard+500 ms 用例无假绿/假红机制——同钟端到端、门常量相对形式、归因先于计时、无追加容差、A3 并入 A1/A2 | 已独立复核 | 转录（:776-777/:373 已由 R1 实跑覆盖）。 |
| R13 | `step02` + `step01` E-file | Q一：三轮全绿证据文档内部一致、无隐藏重试迹象；但设备执行本身未由复核重放 | 未复核 | 转录（记录员同样未重放设备执行）。 |
| R14 | 基线 §5 + KD-72（:2744 时点） | Q二：锁分支未走重生成（T0=只登记）、判定有三条独立依据；陈旧锁仍全部未跟踪，无锁内容需审计 | 已独立复核 | 抽验吻合（check_lockfiles exit 0 复跑）。 |
| R15 | `check_dependency_pins.py` 实跑 + KD-72/KD-73 | Q二：stdlib 2.3.21 与 quickjs 1.0.15 不变量当前由钉版门保持（非锁内容）；解析源为旧 lintVital 产物、版本门不证 API 兼容（KD-73 open 沿用） | 已独立复核 | **实跑复核吻合**（pins EXIT=0，quickjs 1.0.15/stdlib 2.3.21）。 |
| R16 | `known-defects.md`（KD-66 :2573 / KD-76 / KD-72 :2744）+ 清单 v3 | Q三：无未达项被写成关闭——复核时点三态均 open；设备绿后关闭动作显式留给记录员/主会话+记录回链；记录文件当时不存在 | 已独立复核 | 转录；本记录员已执行关闭动作（§8），KD-76/72 维持 open。 |
| R17 | 清单 v3 | Q三：只刷新四行现状与理由、0 项转"通过"、MVA 不成立结论维持、行数 69 不变 | 已独立复核 | **实跑复核吻合**（grep 69 行）。 |
| R18 | `check_split_gate.py:70-71,:214-233,:373-375` + `measure:61-64` | Q四：门阈值 0.9/0.05 未弱化——常量未动、闭边界、无 CLI 覆盖口、fail-closed、阈值出处随报告落盘；measure 重构仅抽函数不改语义 | 已独立复核 | 抽验吻合（25/25 与 833 全绿为记录员实跑；常量行 diff 核对未重做）。 |
| R19 | `GATE.md` + 契约 :199-201 | Q四：门 FAIL 处置诚实——自动化不启用、切残红线独立于门值、未实现指标如实登记、代理金标口径不变 | 已独立复核 | 抽验吻合（契约 :200 实读）。 |
| R20 | `tools/tests/test_split_gate.py`（新文件） | Q五（观察项，非越权）：位于落点文字"tools/split_golden"之外，但 tools/tests 非热区、与既有同目录布局一致 | 已独立复核 | 转录（观察项，无需处置）。 |
| R21 | `step02` §3 + R1 设计 §4.5/§8 | Q一残余风险：A5 通过时不打印 elapsed 实值，调用方实际余量只能由 logcat 时间轴旁证（~450 ms）；500 ms 余量在真实负载分布下的裕度 UNVERIFIED（R1 自登记）；余量吃穿即真红属门语义非假绿通道 | 已独立复核 | 转录；登记 §6 #7。 |

**记录员追加核对（不在原清单）**：

1. **contract :72 的 KD-66 状态句同步（本记录员新增改动）**：关闭 KD-66 后，契约 :72 残留"**KD-66 保持 open**……现有 watchdog 用例以 ≤6 s 判死、弱于 hard+500 ms"将与台账矛盾（改一处先闭合牵动面）。已按最小改动把该句改为"**KD-66 已于 2026-10-27 批次 26 关闭**（关闭门原文不变……）"，保留 6 s 证据的历史定位与 #240 上游/JS 不广告边界；契约其余两个 hunk（T5 :72 刷新、T4 :200 登记）未触碰。
2. **证据索引两处回链**（`00-index.md:3-4`、`:128`）：按索引 §1 自身指示"由记录员后写；后写时回链本索引"完成，行数中性。
3. **工单材料体量值漂移归因**：设备轨转述"65 KiB/159 行/11 KiB"为 `step03` §9 更正前时点值；终值 70,134 B/188 行/12,671 B（记录员实测 = 索引 §6）。
4. `docs/agent-first-refactor-decisions-2026-09-23.md` **不涉**：KD-66 关闭是预登记判据（工单 §2）的执行而非新产品裁定；T3 两项队列语义决策与 T2 保守分支仍待主会话确认，未构成已裁定事项。
5. `MASTER-LINE`/`algorithm-version-ledger` 本批开工时干净，本记录员按工单"同步"要求补顶部滚动三段与 §3.36 零 bump。

## 6. 遗留 / UNVERIFIED / 待裁

**工程门**

1. **门禁 `exit=0` 无仓内日志**（单测门第 1 轮/强门第 1 轮均工单给定）；记录员未运行 Gradle。提交前建议主会话把命令与 tail 落盘 `build/`。
2. **设备用例未由记录员/复核轨重跑**：设备结论基于原始日志/证据文件/源码三方一致（复核轨 R3/R10 全量 grep + 记录员有界抽验），未产生新一轮设备数据；java 21.0.11、`assembleStrictOfflineRelease` 2m1s 时长、release PID 文件未复读（R11 转录）；"5562/5584 全程未触碰"是日志零引用支持的一致性结论，非可追溯证明。
3. **KD-76 open（本批不关闭）**：③ 只完成"测试 APK 内生产 client 引擎"形状——产品进程/UI 首请求、契约 §4 worst-case 29.5 s 链、低内存条件 UNVERIFIED；负载轻于批次 23 E-01d（boot 0.75/跑后 4.31 vs 5.25）；一次实跑一台 x86_64（API 34）；⑤ 的 UI/文案面与 R3 广告门文本升级未做（**关闭另一半，归主会话**）；`READY_TIMEOUT=10 s` 待设备校准；隔离进程内一次性装载实测成本 UNVERIFIED。**JS 不广告。**
4. **KD-72 open**：14 个陈旧锁维持原状、不得 `git add`/提交；W1 六文件入库（等 markdown 迁移线/6B-2 线批次提交）→ W2 整树收口（688 条）→ W3 暂存锁（当前绿）；窗口开后按 R3 §2 预案执行（生成参考批 23 实跑 2m55s——预案引用值非本轮实测）；负控 5a-5c 与 `:benchmark` 补锁判据未变。
5. **AIDL 门**：本批未跑 Gradle AIDL 任务（设备轨只用 assemble*/am instrument）；`aidl.exe` 双输入禁用规则继续有效。

**代码/复核残留**

6. **#240 上游 OPEN、daemon 线程泄漏登记形态不变**（KD-66 关闭不消除）；R1 §7 的上游 2026-10-25 commit 观察为单一来源待验证——上游发 >1.0.15 修复后按 Fix direction ② 反转观察用例，钉版升级前不动。
7. **T1 A5 通过时不打印 elapsed 实值**（断言消息仅失败时打印）：调用方实际余量只能由 logcat 时间轴旁证（~450 ms）；500 ms 余量在真实设备负载分布下的实测裕度 UNVERIFIED（R1 §4.5 设计自登记；环境慢吃穿余量即真红，属门语义非假绿通道）。
8. **T3 两项队列语义决策仍待主会话确认**（死亡/abandon 清队回 `ENGINE_CLOSED`；调用方取消在飞帧不 abandon worker）——契约 :72 仍标"待确认"；T2 保守分支两项待追认（不新增 `STATUS_NOT_READY`；warm-up 不加自身期限）。

**AUD-08 / 数据**

9. **门 FAIL → 门后自动化不启用**（切残不得自动入库红线不变）；错误合并/重复率**未立项未实现**；train/dev/acceptance 切分未做（需新冻结集 v2 + 产品裁定）；CI 门禁接线未做（.github 不在落点）；真设备 DB 上 `export_split_eval.py` 实跑与同 sha 复跑 UNVERIFIED；4C 存量导出无溯源块（来源绑定 UNVERIFIED）；真实模型/设备重测 UNVERIFIED（无凭据）；金标无人工专家（代理金标口径）。

**设备 / 证据**

10. **arm64/16KB UNVERIFIED**（批次 25 R4 §2 本机原理性不可行定论）；**KD-77 open**（API 23 镜像未装未测）；生产 release 签名 UNVERIFIED；中文 OCR 未跑；ps16k/KD-70/16KB 面本批未执行（任务书四步不含，5556 只读核登记 E-00）。
11. **`step03` §9 更正已入档**（`UnsatisfiedLinkError` 1 条=系统进程噪声已归因；锚点 `:299→:298`/`:321→:327`）；E-01 的 logcat 9 项签名全 0 不受影响；判定均不变。

**他线 / 台账 / 待裁**

12. **origin/main 未 fetch**：远端实时 hash 未核（HEAD `405c8373` 是否仍与远端一致 UNVERIFIED）；Mimosa 无完整扫描结论，不宣称项目安全。
13. **热区在飞未触碰**（feature 四模块+profile、core/ui、core/domain、core/data、core/database、core/export、core/markdown 整目录、Root/SecondaryScreens、settings.gradle.kts、.gitignore、docs/current 其余 M、docs/research 他线未跟踪记录、tools/kb、knowledge-production）；R2 §3 建议并入设备轮的四项（P-04 双态证据、P-08 dumpsys、P-09 客户端队列设备负向、P-12 G-7 ①③ 断言）本批未执行，仍缺。
14. **待主会话裁定汇总**：KD-76 关闭另一半（R3/契约广告门文本升级的归属与文案裁定）、T3 两项语义决策、T2 两项保守分支追认、锁重生成时点编排（W1/W2 依赖他线批次节奏）、AUD-08 CI 接线排期、#240 上游升级跟踪。

## 7. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前复跑单测门/强门并留 `exit=0` 日志；**14 个未跟踪锁文件（13 项目锁 + `settings-gradle.lockfile`）一律不得加入提交**（KD-72 open；R3 §0 判红继续有效；本批无"锁+构建文件"提交块——锁未生成）；热区与 docs/current 其余 M 一律排除。按 ≤6 路径/块（新 = ??，改 = M）：

- **块 1（T1 用例 + KD 台账）**：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`（改）、`docs/known-defects.md`（改；**整文件纳入并注明**：本批含 T1 块/T5 块 + 记录员 KD-66 关闭与 KD-76 设备回填，KD-72 区未动）
- **块 2（契约 + 准入清单 v3）**：`docs/current/visual-generation-contract.md`（改；**整文件纳入并注明**：T5 :72 刷新 + T4 :200 门登记 + 记录员 KD-66 关闭句）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（改）
- **块 3（三研究文档）**：`docs/research/2026-10-27-kd66-hard-500ms-design.md`（新）、`docs/research/2026-10-27-mva-reeval.md`（新）、`docs/research/2026-10-27-lockfile-execution-plan.md`（新）
- **块 4（T4 工具脚本与测试）**：`tools/split_golden/check_split_gate.py`（新）、`tools/split_golden/export_split_eval.py`（新）、`tools/split_golden/measure_split_accuracy.py`（改）、`tools/tests/test_split_gate.py`（新）
- **块 5（T4 门说明与 README）**：`tools/split_golden/GATE.md`（新）、`tools/split_golden/README.md`（新）
- **块 6（基线 + 证据索引 + AUD-08 证据）**：`docs/research/2026-10-27-batch-26-baseline.md`（新）、`docs/research/evidence/2026-10-27-batch-26/00-index.md`（新）、`docs/research/evidence/2026-10-27-batch-26/step-aud08-tool-gate.md`（新）、`docs/research/evidence/2026-10-27-batch-26/step-aud08-gate-evidence-out.md`（新）
- **块 7（设备证据前半）**：`docs/research/evidence/2026-10-27-batch-26/step00-environment.md`、`step01-visualjs-isolation.md`、`step02-kd66-hard-500ms.md`、`step03-kd76-criteria.md`（均新）
- **块 8（设备证据后半 + 本记录）**：`docs/research/evidence/2026-10-27-batch-26/step04-r8-release-smoke.md`、`step05-backup-restore.md`、`docs/research/2026-10-27-batch-26-record.md`（新）
- **块 9（主线/台账同步）**：`docs/REFACTOR-MASTER-LINE.md`（改；顶部滚动三段，整文件纳入）、`docs/research/algorithm-version-ledger.md`（改；§3.36 零 bump）

**明确排除（他线在飞/不安全，一律不动）**：

- 14 个未跟踪锁文件（`*/gradle.lockfile` ×13 含 `core/markdown/gradle.lockfile`〔折叠于 `?? core/markdown/`〕+ `settings-gradle.lockfile`）；`benchmark/gradle.lockfile` 不存在。
- `feature/{capture,review,tutor,library,profile}/**`、`core/{ui,domain,database,export}/**`、`core/data` 他线 M 与他线新文件（基线 §2.2 清单）、`core/markdown/` 整目录、`settings.gradle.kts`、`.gitignore`、app 开工即脏 M（Root/SecondaryScreens/Destinations/StartupStateBanner 等）、`docs/current` 其余 M、`docs/research` 他线未跟踪记录、`docs/superpowers/**`、`tools/kb*`、`knowledge-production/**`、`build/**`（含 `build/device-track-batch26/` 原始日志与 release-smoke.jks，`.gitignore` 忽略）、`tmp/`（本批无新脚本；批次 25 四脚本已随 `961c6a0e` 入库）。
- 批次 23/24/25 证据目录（本批零改动）。

**提交说明**：建议 9 块、每块 ≤6 路径；三个整文件纳入的 M 文档在提交说明中注明"整文件纳入，含本批多轨改动"。提交/推送由主会话执行；非快进 merge，不 rebase。

## 8. 收口状态（工单给定 + 记录员执行项；提交/推送待主会话）

- **门禁（工单给定）**：单测门第 1 轮 `exit=0`；强门第 1 轮 `exit=0`；无非低发现、未进修复环。仓内无本批门禁日志；记录员未运行 Gradle。
- **设备（工单给定 + E-file）**：四步执行完毕——隔离 13/13 + Race 2/2、KD-66 门三轮独立绿、KD-76 ③④⑤ PARTIAL、release 2×ok/COLD、备份 34/34；证据 9 个 `.md`/70,134 B 落盘，脱敏 0 命中；设备恢复 debug 态。
- **锁**：窗口不可行，未生成（分支 B 登记）；KD-72 open。
- **KD-66 关闭（记录员按工单指令执行）**：标题转 `closed 2026-10-27`（`known-defects.md:2573`）+ 关闭 Progress 块（`:2619`，取代 T1/T5 两块的出数前表述）+ 记录回链 = 本文件 §3.5/§4 + 契约 :72 状态句同步 + 索引回链；关闭语义边界（#240 上游 OPEN、daemon 泄漏登记形态、JS 不广告）已写入关闭块。**KD-76/KD-72 维持 open**（各自设备回填块 `:2849`/无改动）。
- **提交/推送**：截至本记录时点，**未提交、未推送**；建议清单与排除项见 §7。提交由主会话执行；非快进 merge，不 rebase。
- **主会话裁定待办**：§6 #14 的六项。
- **本批不声称全绿/全就绪**：KD-76 open（③ 产品面 UNVERIFIED、文本升级未做）、KD-72 open、AUD-08 门 FAIL 自动化不启用、生产签名/真实 Provider/arm64/KD-77 UNVERIFIED、JS 仍 `UNAVAILABLE` 不广告。
- Mimosa 提交仍无完整扫描结论——不宣称项目安全。

## 附录 A：本记录员实际执行的只读核对（可复跑；未运行 Gradle/设备/任何 git 写命令）

```bash
git rev-parse --short HEAD                                  # 405c8373
git status --porcelain | wc -l                              # 688
git status --porcelain | grep -c '^ M'                      # 206
git status --porcelain | grep -c '^??'                      # 482
git diff --cached --name-only | wc -l                       # 0（暂存区空）
git status --porcelain -- app/src/main/kotlin/.../visualjs/ \
  app/src/main/aidl/.../visualjs/                           # 空（仅 androidTest 测试文件 M）

# T1 落码锚点
grep -c "@Test" app/src/androidTest/.../visualjs/VisualJsIsolationInstrumentedTest.kt   # 13
sed -n '373p' 同文件                                          # fun aHungWorkerReleasesTheCallerWithinHardPlus500ms
sed -n '776,777p' 同文件                                     # HARD_PLUS_500_GATE_MILLIS = SERVICE_HARD_DEADLINE_MILLIS + 500L

# 设备原始日志有界抽验（build/ 不作关闭证据，只验 E-file 转录真实性）
grep -c "hard deadline (1500 ms)" build/device-track-batch26/step02-kd66-run2-logcat.txt \
  build/device-track-batch26/step02-kd66-run3-logcat.txt    # 1 / 1
grep -c "OK (13 tests)" build/device-track-batch26/step01-isolation.log \
  build/device-track-batch26/step03-coldboot-suite.log      # 1 / 1
grep -c "OK (2 tests)" build/device-track-batch26/step01-race.log          # 1
grep -c "OK (34 tests)" build/device-track-batch26/step05-backup.log       # 1

# 锁窗口/钉版门复跑（只读脚本）
python tools/ci/check_lockfiles.py                          # EXIT=0（skip 口径：13 未跟踪锁不能原样提交；缺 :benchmark）
python tools/ci/check_dependency_pins.py                    # EXIT=0（resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']）

# T4 工具面复跑（本轮唯一由记录员执行的测试）
python -m unittest tools.tests.test_split_gate              # Ran 25 tests in 0.921s — OK
python -m unittest discover -s tools/tests -t tools         # Ran 833 tests in 269.469s — OK，EXIT=0

# 证据体量
find docs/research/evidence/2026-10-27-batch-26 -name "*.md" | wc -l    # 9
find 同目录 -name "*.md" -printf "%s\n" | awk '{s+=$1} END {print s}'    # 70134

# 台账/契约/清单锚点（回填后现值）
grep -n "^## KD-66\|^## KD-72\|^## KD-76\|^## KD-77" docs/known-defects.md
#   → :2573(closed 2026-10-27 批次 26) / :2746(open) / :2819(open) / :2853(open)
grep -n "^\*\*Progress（2026-10-27 批次 26" docs/known-defects.md
#   → :2615(T1 落码) / :2617(T5 登记) / :2619(设备轨+记录员关闭) / :2847(KD-76 T5) / :2849(KD-76 设备回填)
grep -cE '^\| [PSMDEAQVRX]-[0-9]+ \|' docs/research/2026-10-26-batch-24-4e2-admission-checklist.md   # 69
grep -n "^| P-04\|^| P-07\|^| P-09\|^| P-12" 同清单            # :127 / :130 / :132 / :135
sed -n '72p;200p' docs/current/visual-generation-contract.md  # :72 KD-66 关闭句 + :200 切分门登记
grep -c "KD-66 已于 2026-10-27 批次 26 关闭" 同契约            # 1

# 未执行：任何 ./gradlew、adb、am instrument、connectedAndroidTest；任何 git 写命令；
#         tmp/verify_* 脚本（本批无新增）；门判定器 check_split_gate.py 的 exit 1 复跑。
```

## 9. 本记录的验证结果（记录员实跑，写盘后执行）

- **文档/证据引用检查**：记录内引用的 `docs/**.md(:line)` 与 step 文件锚点经写盘后 grep/sed 复核——known-defects 锚（:2573/:2615/:2617/:2619/:2746/:2819/:2847/:2849/:2853）、清单锚（69 行、:127/:130/:132/:135、:399）、契约锚（:72/:200）、E-file 判定行（step01 §3、step02 §3/§8、step03 §7/§9、step04 §3、step05 §3、00-index §3.1/§5/§6/§7）与 R1 设计 `:128` 均为实读实测值；`build/device-track-batch26/` 路径仅作 raw-log 提及，不当关闭证据。
- **补丁/空白检查**：`git diff --check` → **exit=0**（仅行尾 CRLF 提示，无空白错误；提示覆盖他线既有脏文件与本记录员六处文档改动）。
- **回填后行号自洽**：known-defects 回填两块（:2619 关闭块 +2 行、:2849 设备块 +2 行）后，KD-72 锚由回填前 `:2744` → 现值 `:2746`，KD-76 `:2817`→`:2819`，KD-77 `:2849`→`:2853`；本记录内 KD 标题锚已按现值书写，附录 A 注明回填前后关系。
- **未执行**：Gradle、设备、git add/commit/push；设备用例与门禁门未由本记录员复跑（口径见 §4 保留与 §6 #1/#2）。
