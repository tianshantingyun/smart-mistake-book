# 3D · D-0「绑定可信度字段」内核侧 · 完成记录（2026-10-07/08）

> 依据：台账裁决 27（为 `bindings[]` 增加裁定状态 + 来源 + 时间戳）；实施计划
> `docs/research/2026-10-07-d0-binding-verdict-plan.md`（`a7e12a1f`）；阶段 5 门核验
> `docs/research/2026-10-04-stage5-gate-check.md`（D-0 曾是 3D 硬门第一阻塞点）。
>
> 执行方式：动态工作流 `dwfrun-883c9655`（两批 + 综合门 + 收尾）——工作流在综合门 DB 全套
> 中止（唯一失败 = 已知边际性能门，环境性，见 §4），两批产物已提交；**收尾由协调方接管**
> （复核修复轮、门取证、记录与主线更新）。
>
> 交付边界：**内核侧载体已落**（codec v3 + 记录字段 + 契约校验 + 端到端 drill + 写侧契约）；
> **真实包内尚无任何三元组**——字段填充属 KB 线 D-5 写入窗口（含「改侧车 + bump contentVersion」
> 一条动作，见契约 §5.2）。

## 1. 提交

| 批次 | 提交 | 内容 |
|---|---|---|
| 计划 | `a7e12a1f` | D-0 内核侧实施计划（codec v3 可选三元组 + drill + 写侧契约；零 schema） |
| 批 1 | `bb045d54` | 侧车 `schemaVersion` 2→3（accepted `{1,2,3}`；v1/v2 键集逐字不变）+ v3 专属可选裁定三元组（all-or-none + 词表 + 时间戳≥0 + 未知键仍拒）+ `core/model` 两枚枚举 + `KnowledgeTeachingMaterialNodeBindingRecord` 三个带默认值可空字段 + 契约侧三元组一致性校验 + 严格性测试（8 用例） |
| 批 2 | `3f7fe776` | 端到端 drill（仪器化：合成 v3 侧车 → 真 codec → 真包校验 → 真 `BundledKnowledgeBaseInstaller.reconcile` → `applyKnowledgeContentUpdate` → `replaceMaterialBindings` → 真 DB；四条断言含「DB 端如实不含」）+ 写侧契约文档 + `reconcile` 可见性 `private→internal`（零行为） |
| 修复轮 | `27454c0a` | NONE 语义修正（`ProblemCatalog.kt` KDoc + 契约 §3/§4.1）+ 契约四处补丁（§4.2 的 17 行缺口、457/316/9 非互斥口径、§5.2 装机快路径陷阱、§3 近名轴提醒）+ 计划口径更正 + v1 独立负向用例（夹具按 schemaVersion 分支）+ drill SQL 参数化 + 包校验注释修正与未用 import 删除 |

零 schema（侧车 `schemaVersion=3` 是内容格式版本，非 DB 版本）；`LearningCoreVersions` 不动；
`core/database/schemas` 零改动。

## 2. 交付内容（每项指认它消灭的失败）

| 交付 | 消灭的具体失败 |
|---|---|
| codec v3 可选三元组 | 语义判定只能停在一次性 CSV 里（裁决 27 原文）；v1/v2 严格性逐字不变，旧包语义零漂移 |
| all-or-none 约束 | 半写三元组让读者分不清「从未裁定」与「裁定了但字段丢了」——后者不可表示 |
| `verdict`/`verdictSource` 编译期枚举 | 词表越界静默通过（字符串字段的默认行为） |
| 领域记录三字段（带默认值） | 既有 ~12 处构造点被破坏；空载体不破坏相等性/幂等比对 |
| 契约侧一致性校验 | 非 codec 路径（Room 写侧）写出不一致三元组 |
| 端到端 drill | 「落库即丢弃」从潜在事故变成显式断言（DB 端如实不含）；同时钉住材料必须自带来源这一调和器行为 |
| 写侧契约文档 | KB 写侧按错误口径落账：NONE 当解绑指令、`material_rebind.csv` 写 REBIND、只写侧车不 bump manifest 导致字段静默不到设备、判决绑定已消失的行被静默丢掉或搬到错误绑定上 |

## 3. 门证据

| 门 | 结果 |
|---|---|
| 批 1 门（工作流第 1 轮）：全量 JVM + `core:data` 定向仪器化 | 绿 |
| 批 2 门（工作流第 1 轮）：全量 JVM + `core:data` 定向仪器化 4 类 | 绿 |
| 修复轮：全量 JVM（`test --continue --rerun-tasks`，最终树） | **2414 / 0 / 0 / 0**（2026-10-08 06:00，344 个结果 XML 求和） |
| 修复轮：`core:data` 定向仪器化 4 类（drill / reconciliation / installer / update-drill） | **4 / 0 / 0 / 0**（BUILD SUCCESSFUL 34m15s，2026-10-08 06:38；`time=2027.033s`） |
| 综合门：全量 JVM（工作流） | exit=0 |
| 综合门：DB 全套（工作流，劣化机上） | **230 例 229 绿 1 红** = `LibraryCatalogScalePerformanceInstrumentedTest.catalogFacetsPlanAndLatency`（SECTION facet p95=239ms vs 本地裸预算 200ms）——**环境性，归因与恢复见 §4** |
| 综合门：DB 全套（协调方重跑，劣化机上） | **230 例 229 绿 1 红** = 同一条（SECTION facet p95=221ms，样本 197–255；53m51s） |
| 综合门：DB 全套（**冷启 `-wipe-data` 后**，最终树） | ✅ **230 / 0 / 0 / 0**（BUILD SUCCESSFUL **11m59s**，`time=709.192s`，2026-10-08 09:06）——同一套件、同一代码，环境恢复即全绿 |
| 综合门：app 全套（三次） | ① 劣化机（DB 后清场）：53 例 1 红 = `MistakeExportDeliveryInstrumentedTest.saveAndShare…`（`RootViewWithoutFocusException`，`mCurrentFocus`=systemui ANR 弹窗）；② guest 重启后：53 例 2 红（同一条 + `RootExperienceInstrumentedTest.submittedReviewItemIsAlreadyAdvanced…`；ANR 列表含 phone/GMS）；③ **冷启 `-wipe-data` 后：✅ 53 / 0 / 0 / 0（100 秒）**——同一代码，环境恢复即全绿 |
| 综合门：R8 冒烟（冷启机） | `:app:assembleLocalFirstRelease` ✓（4m23s，带 RELEASE_* 签名）→ 装机 ✓ → monkey 启动 ✓（`com.tingyun.smartmistakebook.localfirst`）→ PID **8290** → logcat 四类崩溃标记 **0** → 截图正常（复习主页 + 四栏导航）→ APK sha256 前缀 `c0fd972ee5ffc40e` |

## 4. SECTION facet 边际门与 app UI 用例 · 环境性归因（本轮实测，非 D-0 回归）

**结论：两次全量运行的红都来自同一件事——模拟器实例累积态劣化（该 qemu 实例连续跑了两天多），
与 D-0 无因果；冷启 `-wipe-data` 后同一代码全绿。** 证据链：

1. **D-0 碰不到这条路径**：批 1/批 2 九个文件全部在知识侧（codec/记录/契约/枚举/测试/文档），
   `library_catalog` 视图、`LibraryQueryDao`、`problem_classification_binding`、
   `learner_problem_memory_state` 与其索引**一行未动**；零 schema、零索引、零查询改动。
2. **结构（主）断言仍绿**：失败只发生在计时 backstop；EQP 结构断言（sectionFacets 走分类索引、
   facet 记忆态连接走复合索引、无整表扫）全部通过——计划形状与 10-05 修复后一致。
3. **同批对照实验（2026-10-08 05:37）**：`PerformanceGateTest` 6/6 绿，但其打印的数字与
   10-05 基线**同比例上浮 1.3–1.5×**：

   | 路径 | 10-05 基线 | 2026-10-08 实测 | 倍数 |
   |---|---|---|---|
   | page 首屏 | 197ms | 285ms | 1.45 |
   | page（章节+掌握筛选） | 47ms | 73ms | 1.55 |
   | count（筛选） | 46ms | 63ms | 1.37 |
   | facet SUBJECT | 62ms | 79ms | 1.27 |
   | facet SECTION | **181ms** | **247ms** | 1.36 |
   | 共享夹具建库（5 万行） | 60,149ms | **525,802ms** | **8.7** |

   SECTION 是这批路径里最慢的一条（10-05 报告原文：「181ms / 200ms 是本批最紧的一条（90% 预算）；
   若后续环境抖动导致红，它会是第一个信号（结构断言仍绿）」）——181×1.36≈246ms，正好越过 200ms。
4. **四次连续失败数字一致**（非尖刺）：综合门（工作流）239ms（样本 215–241）、重启前单跑 225ms
   （193–250）、重启后单跑 247ms（210–262）、综合门重跑（协调方，最终树）**221ms（197–255）**。
   `adb reboot`（guest 重启）**不能**恢复——这是关键排除项：劣化不在 guest 的启动状态里，
   而在**模拟器进程/AVD 累积态**里（该 qemu 实例自 10-05 起连续跑了两天多，期间跑过多次
   5 万行夹具与全量套件）。
5. **决定性恢复实验（2026-10-08 08:36–08:45）**：`adb emu kill` + **冷启 `-wipe-data`**
   （`emulator.exe -avd test_device -wipe-data -no-snapshot-save -no-boot-anim -gpu
   swiftshader_indirect`，文档化恢复路径）后：
   - 同一 `LibraryCatalogScalePerformanceInstrumentedTest` **1m23s 全绿**：SUBJECT p95=**60ms**
     （10-05 基线 62）、**SECTION p95=173ms（绿；10-05 基线 181）**、MASTERY p95=**121ms**（基线 126）
     ——三条路径全部回到基线 ±5%，此前 SECTION 稳定 221–247ms。
   - app 全套 **53/0/0（100 秒）**——同一套件在劣化机上要 16–21 分钟且 UI 用例被系统 ANR 打红。
   - 结论：**红是模拟器累积态劣化，不是 D-0、也不是宿主负载**；劣化期 guest 内
     `com.android.systemui` / `com.android.phone` / `com.google.android.gms.persistent` 持续 ANR，
     抢焦点打红 UI 用例（`RootViewWithoutFocusException`）。冷启后一并消失。
6. **预算口径**：本地裸预算 200ms 是仓库自定的「本地严格」口径（`PerformanceGateTest` KDoc：
   「local strict runs keep the raw targets」）；CI 用 `ciSlowRunner=1` 系数 ×4=800ms，
   按该口径本项无风险。**本记录不主张放宽本地预算**——那需要用户裁定；此处只做归因。
7. **登记（环境纪律）**：性能门/UI 用例在长时运行后转红时，先按冷启 `-wipe-data` 复核再归因代码；
   本次四红 + 三红（app）全部由冷启一次性消解，未见任何 D-0 相关项。

## 5. 独立复核与处置

工作流复核轮（批 1/批 2 各一轮）+ 修复轮协调方复核，全部发现与处置：

| # | 发现（来源） | 处置 |
|---|---|---|
| P1-1 | 契约 §4.2 让 `REBIND` 落在改绑后的新绑定上，与词表语义相反（批 2 复核） | 已修（`3f7fe776`）：§4.2 改为「不许写 `REBIND`」，二选一交 D-5 钉死 |
| P1-2 | 契约 §4.1 对「被判绑定已不存在」的 445 行无规则（批 2 复核） | 已修（`3f7fe776`）：新增落账前提 + 实测分布 + 登记 §7.4 |
| P1-3 | 契约 §4.3 小写计数写反（`keep`/`bind`）（批 2 复核） | 已修（`3f7fe776`） |
| M-1 | **NONE 语义与权威 CSV 不符**：KDoc 称「内容不属于任何知识点…不应保留」，而 09-25 轮 23 行 NONE 全是「留人工/需拆分」（abstain），10-02/10-06 轮才是「题干残片→建议释放」；且 6/23 行后续被改判（证明非终局） | **修复轮已修**：KDoc + 契约 §3/§4.1 按四轮实读重写（复核者独立复算确认 23/23 行无「应删除」字样、6 行改判属实） |
| L-1 | v1 无独立负向用例（只靠读码） | **修复轮已补**：v1 带三元组仍拒 + v1 两键解 null 两条用例；夹具 sources 按版本分支（复核确认非恒真、真走 v1 分支） |
| L-2 | 三处文档引 `codec.kt:142` 已失准 | 本记录同批更新阶段 5 门核验文档；台账/设计 spec 属他线在飞文件，**登记不动** |
| L-3 | all-or-none 规则两处实现（codec 按「键在」/契约按「值非空」）有漂移面；包校验注释称「解码时已校验」过时 + 未用 import | **修复轮已修**：注释改为准确描述（codec 做结构校验、契约只在写路径）、删除未用 import；两处实现保留（不同入口的纵深防御），注释互指 |
| L-4 | 装机快路径（contentVersion 相同即跳过）会让「只写侧车不 bump manifest」的落账静默失效 | **修复轮已修**：契约 §5.2 增条（含真实行号 `:43-51` / `:98-101`） |
| L-5 | 契约 §4.2 未覆盖 `to_node_slug` 也已不是现绑的 17 行 | **修复轮已修**：新增该段（复核复算 1585/1568/17 一致） |
| L-6 | 计划与契约对 `material_rebind.csv` 口径相反 | **修复轮已修**：计划原文改写为「不落 `REBIND`——见 §4.2」+ 口径更正引注 |
| L-7 | 457/316/9 读起来像划分（实为 53 条跨 ≥2 档） | **修复轮已修**：加非互斥说明 |
| L-8 | drill「包级可见」断言用的是测试内聚合 pack，未登记边界 | **修复轮已修**：KDoc ② 补边界说明 |
| L-9 | 台账引用行号只存在于他线未提交工作树（HEAD 悬空） | **修复轮已修**：契约头改为「工作树版 :2315-2318，HEAD 尚无此段」 |
| L-10 | drill 用字符串拼接把 materialId 送进 rawQuery（非注入面，但会被照抄） | **修复轮已修**：`readRows` 加 `selectionArgs`，改参数化 |
| L-11 | 复核发现：§5.2 逐包行号 `:91-93` 实为 `:98-101`；§4.1 把 10-06 的 20 行一刀切（其中 1 行是公式卡无承接节点）；包校验注释「only checks」过宽；plan 作废原文仍留 | **修复轮二次收口已修**（4 处表述级） |
| — | 复核自报的 tests=1/333.498s 与磁盘 XML 不一致（历史证据引用失准） | 登记：本记录只引磁盘/本轮实测产物 |

## 6. 未覆盖 / 登记

1. **DB 落库**：三元组不落 `knowledge_teaching_material_node_binding`（待消费方出现时再开 schema）。
2. **Python 键集守卫**：promote 链路的 binding 键集门——KB 线（否则「staging 写坏一个键 → promote
   全绿 → 装机整包拒」）。
3. **真实包字段填充**：属 KB 线 D-5 写入窗口；落账清单必须含「改侧车 + bump `contentVersion`」。
4. **「生效裁定」合并表 + 未落账/需重裁清单**：多轮覆盖规则（237 条跨轮材料）+ 445 行（判决绑定已
   消失）+ 17 行（`to_node_slug` 也已不是现绑）——写侧落账前必须先产出。
5. **他线文档里的旧行号引用**（`docs/agent-first-refactor-decisions-2026-09-23.md`、
   `docs/superpowers/specs/2026-09-25-agent-first-refactor-design.md`）——不碰他线在飞文件。
6. **本机性能基线漂移**（§4）：SECTION facet 本地裸预算在当前宿主机不可达；需在安静机器/CI 复测。
7. **bm25/rank FTS5 换库**（3C 遗留，待用户裁定）不影响本批。

## 7. 对阶段 5 硬门的影响

D-0 的**内核侧载体**已落并取证；D-0 的**验收门**（判定随绑定进真实包、可回归）要等 KB 线 D-5
写入窗口把裁定写进包（届时按契约 v3，含 manifest bump）。**D-1（语义精确率 ≥0.95）/ D-2（结构
完整性）/ D-3（检索质量）仍未过** → **阶段 5 维持挂起**（主线 5 行与阶段 5 门核验文档同批更新）。
