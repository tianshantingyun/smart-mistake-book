# 蜕变关系库：渲染 / PDF 导出 / 检索 / FSRS 调度 / 备份恢复

| 字段 | 值 |
|---|---|
| evidence-id | `B44-MR-LIB-01` |
| 日期 | 2026-11-04（按计划批次 44 命名 `2026-11-06`） |
| 基线 | `26f2327b`（批次 43 收口后端；与 `docs/research/2026-11-06-batch-44-baseline.md:7-9` 同值） |
| 产出性质 | **关系定义 + 可执行判定式 + 本轮实跑探针**；本文件**不改任何源码**，**不跑 Gradle**，**不上设备** |
| 服务对象 | 计划 §1 D0 item 7（`tools/ci/metamorphic_checks.py` 的"关系库"内容源）；计划 §2 C/E 域"无 oracle 的验收" |
| 判定口径 | 每条关系给出**输入变换 → 必守输出变化/不变 → 判定式（含容差）**；判定式只允许相等/不等/集合关系/带界不等式，不允许"看起来合理" |

> **与既有裁定的关系**：本文件不推翻批次 43 §9 收口（`docs/research/2026-11-05-batch-43-record.md:571-578`）：KD-76 维持 open、isolation 不定案、p09 未收敛、发版不成立、AUD-08 维持 FAIL。蜕变关系只回答"在没有金标准时怎么判"，不把任何 BLOCKED 项变成 PASS。
>
> **与不可变清单的关系**（6C-2：mask=8 不提供、mask=0 禁止、派生索引恒裁）：本文件在 B 域只**新增不变量关系**（B-03/B-04/B-06），不新增任何创建/恢复路径，也不放宽 `ALLOWED_MASKS`。

---

## 0. 结论摘要

1. **五域共 48 条关系**：渲染 10（R-01…R-10）、PDF 导出 10（P-01…P-10）、检索 8（S-01…S-08）、FSRS 调度 10（F-01…F-10）、备份恢复 10（B-01…B-10）。每条的判定式都在表内写死，可直接翻译成断言。
2. **本轮真跑了三类探针**（非转录）：
   - 仓内 Node 测试台 `node tools/visual_compiler/run_tests.mjs` → `94 passed, 0 failed`（Node v26.4.0）；
   - 自写关系探针（`%TEMP%/mt-probe/probe.mjs`，复用同一编译资产）→ `14 passed, 0 failed`：覆盖 R-01/R-02/R-04/R-05/R-06/R-07/R-08/R-10 的断言，并对 R-03 的矩阵列序回显、R-09 的预算边界各给出 1 个样本（**嵌套合成与 stats 不变性未在探针内**，见 §7.1 注）；
   - 仓内备份四方门 `python -I tools/ci/check_backup_plan_sync.py` → `PASS`，`EXIT=0`（digest 复算 / v4 冻结夹具逐位一致）。
3. **探针抓到三个真实的坑**，直接改变了 F 域关系的写法：
   - 区间倒函数在**夹取区**（`I` 被夹到 1 或 36500）不满足往返关系：`S=0.001` 时 `I=1`、`R=0.3457` 而非 0.9；`S=36148` 时 `I=36500`、误差 0.0993。→ F-03 必须带域前置。
   - 难度评级序在 `D=1.0` 处**退化**（GOOD 与 EASY 都被夹到 1.0），严格序不成立。→ F-08 必须写成"开区间内严格、闭边界处非严格"。
   - 稳定度评级链末段是**逐位相等**而非严格序：`S'(GOOD) == S'(EASY)`（`w16` 钉为 1.0）。→ F-07 若写成严格序，会逼着实现"补一个不存在的 Easy bonus"。这条是本轮探针把初稿写错的地方，已改正并留证（§5 要点）。
4. **能力边界（如实）**：Kotlin 侧的 PDF 计划器、`DenseFusion`、`FsrsScheduleMath`、备份裁剪/恢复**本轮一律未执行**——它们分别位于 `core/export`（只做已批准 hunk）、`core/domain`/`core/ui`/`feature/*`（热区禁改）、设备轨。这些域的"承载"列写的是**现有测试名或待建测试**，其 verdict 一律 **UNVERIFIED / BLOCKED**，不计 PASS。

---

## 1. 方法与来源

### 1.1 关系的标准形式

一条蜕变关系写作四元组：

```
MR = (T, Pre, Assert, Tol)
```

- `T`：**输入变换**（对同一条被测路径施加的、可程序化构造的改动）；
- `Pre`：**前置条件**（不满足时本关系不适用，既不算 PASS 也不算 FAIL）——本轮两个探针坑都属于这一类；
- `Assert`：**必守关系**（输出之间的变化或不变）；
- `Tol`：**容差**（浮点/栅格/取整；没有容差就是逐位相等）。

判定三值：`SATISFIED` / `VIOLATED` / `NOT_APPLICABLE`（前置不满足）。`VIOLATED` 必须带反例（输入、期望、实测三元组）。

### 1.2 为什么这五域需要 MR 而不是金标准

五域共同的失败形态是"**输出有一百万个合法值，没有唯一正确答案**"：渲染指令序列、PDF 分页结果、检索排序、复习间隔、归档摘要，都无法用人手写死期望值。蜕变关系把"看起来对"换成"两条相关输入之间必须成立的关系"，从而在没有 oracle 的前提下可判定。这正是蜕变测试的用途定义（arXiv `2310.00338` 摘要：MR 描述"输入变化引起的期望输出变化"；arXiv `2412.20692`：MT 用"跨相关输入-输出对的关系"替代 oracle）。

### 1.3 来源采纳表（URL + 采纳/拒绝理由）

| # | 来源 | 本轮取得方式 | 采纳结论 |
|---|---|---|---|
| S1 | https://arxiv.org/abs/2310.00338 《Towards a Complete Metamorphic Testing Pipeline》(Duque-Torres & Pfahl) | 本轮 WebFetch 成功（标题/作者/摘要已核对） | **采纳**：MR 的定义与"生成→约束→评估"结构用于 §1.1 的形式化 |
| S2 | https://arxiv.org/abs/2412.20692 《Test Adequacy for Metamorphic Testing: Criteria, Measurement, and Implication》(Fu, Sun, Zhang, Liu) | 本轮 WebFetch 成功 | **采纳（方法论）**：用于"关系强度需与源输入多样性一起报告"，落为 §7.3 的"每条关系必须记覆盖的输入类" |
| S3 | https://www.w3.org/TR/SVG11/coords.html | 本轮 WebFetch 成功 | **采纳**：`matrix(a,b,c,d,e,f)` 定义、嵌套变换"后乘（concatenate）"、列表按书写顺序应用 → R-02/R-03 的合成顺序判据 |
| S4 | https://www.w3.org/TR/SVG11/painting.html | 本轮 WebFetch 成功 | **采纳**：`nonzero` 与 `evenodd` 的点内判定定义 → `clip`/`hitRegions` 的 `rule` 校验旁证（`VisualDrawOpsParser.kt:339-352`） |
| S5 | AOSP `platform_frameworks_base` `graphics/java/android/graphics/DashPathEffect.java` | `gh api .../contents/...` + base64 解码，本轮实读源码 | **采纳（主源）**：构造器只检查 `intervals.length < 2` 并抛**无码** `ArrayIndexOutOfBoundsException`；源码**未强制偶数项**。→ R 域"奇数 dash 必拒"是**本仓自设严格性**（`VisualDrawOpsPainterPure.kt:46-51` 已如实标注），关系按"拒绝码"判，不按"AOSP 契约"判 |
| S6 | https://www.sqlite.org/lang_vacuum.html | 本轮 WebFetch 成功 | **采纳**：原文"the generated output database is a consistent snapshot of the original database" → B-01 往返关系的前提（快照是逻辑一致的） |
| S7 | https://github.com/open-spaced-repetition/py-fsrs | `gh api repos/...` 元数据成功（MIT、522★、`pushed 2026-08-09`）；README WebFetch **超时未取到正文** | **部分采纳**：作为算法上游的身份证据（仓内 `FsrsScheduleMath.kt:7-13` 自述"verbatim port … source-verified 2026-08-29"）；正文未取 → 公式级结论**只以仓内代码为准**，不引 py-fsrs 文档 |
| S8 | https://onlinelibrary.wiley.com/doi/abs/10.1002/stvr.70003 （MR Patterns, 2025） | 仅搜索引擎摘要 | **不采纳**：未取得正文，摘要不足以支撑任何具体关系；登记为待验证 |
| S9 | https://ieeexplore.ieee.org/document/9405312 （MT of Code Search Engines） | 仅摘要 | **不采纳（仅方向参考）**：检索域确实有 MT 先例，但五条 MR 的具体内容未取得；本文件检索域关系全部由**仓内不变量**推出 |
| S10 | https://arxiv.org/abs/2406.05397 （MR Generation 综述） | 仅摘要 | **不采纳**：同上，留作后续 MR 自动生成的检索入口 |
| S11 | `developer.android.com/reference/android/graphics/DashPathEffect`、`.../pdf/PdfDocument.PageInfo.Builder` | WebFetch **两次连接超时** | **未取得**：改用 S5 主源 + 仓内 KDoc 转录（`VisualDrawOpsPdfPainterPure.kt:20-26` 记 `PageInfo.Builder` 越界抛无码 IAE，标注为转录） |
| S12 | 仓内 `docs/research/2026-10-27-scenev1-compiler-design.md` | 本轮实读 | **采纳**：DrawOps 词表七条不变量（`:120-150`）、Canvas 映射点（`:171-180`） |
| S13 | 仓内 `docs/research/2026-10-28-screen-pdf-parity-gate.md` | 本轮实读 | **采纳**：屏/PDF **不是同一栅格器**，逐像素相等是错误判据（`:14`）；度量与阈值表（`:187-215`）→ P-07/R 域一致性关系的判据来源 |
| S14 | 仓内 `docs/research/fsrs-algorithm-gap-analysis.md` | 本轮实读 | **采纳**：1-based/0-based 换算的权威口径与历史 off-by-one 证据链（`:16-40`），支撑 F-10 的映射表断言 |

> 来源纪律说明：S1/S2/S3/S4/S6 是本轮**一手取到正文**的外部来源，可以支撑判据；S7 只有元数据，故只支撑"身份"不支撑"公式"；S8–S10 只有摘要，**一律不进入任何关系的依据**。

---

## 2. 渲染域（SceneV1 → painter）：R-01…R-10

被测面：`visual-compiler.js`（生成侧）→ `VisualDrawOpsParser`/`Validator`（消费侧）→ `VisualDrawOpsPainterPure`/`PdfPainterPure`（回放侧）。三层共用同一套不变量（`VisualDrawOpsValidator.kt:40` 把提交门直接委托给解析器；`VisualDrawOpsParser.kt:44-46` 声明"同一个解析实现，不存在两道门各写一套词表"）。

| ID | 输入变换 T | 必守关系（断言） | 判定式 / 容差 | 它消灭的具体失败 | 载体 |
|---|---|---|---|---|---|
| **R-01** | 同 `(scene, metrics, expect)` 编译两次，第二次用**全新 VM 上下文** | 两次输出**逐字节相同** | `out2 === out1`（字符串相等）；也允许跨上下文 `fresh === first` | 编译器偷偷引入时钟/随机/上下文残留，产物不可复现，快照与回放对比全部失效 | `run_tests.mjs:274-282`（3 条）；设备侧 `QuickJsSceneCompilerInstrumentedTest`。**本轮实跑 PASS** |
| **R-02** | 根组加平移 `matrix=[1,0,0,1,tx,ty]` | ① 所有 `path` op 的 `path` 数组**逐元素不变**（绘制路径保持局部坐标）；② 新增 `concat` 的 `m[4..5] == (tx,ty)`；③ `stats.pathCommands` 不变；④ `stats.ops` 恰 +3（save+concat+restore） | ①②逐值相等；③④整数相等 | 编译器把平移烘焙进路径坐标 → 非均匀缩放下 stroke 宽度失真（契约要求"stroke 随变换缩放"只能由 painter 在变换空间描边） | 新测（Node 侧即可）。**本轮探针 PASS**（tx=25,ty=−7） |
| **R-03** | 把矩阵 `M` 的组再包一层矩阵 `N` 的组 | 发射两个 `concat`，顺序为 `[N, M]`，且二者语义合成 = `N·M`（后乘）；合成矩阵**可逆**（`abs(det) ≥ 1e-12`） | 矩阵逐元素相对误差 ≤ 1e-12；`abs(det(N·M) - det(N)*det(M)) ≤ 1e-9` | 列序错位（Android `setValues` 是 `[a,c,e,b,d,f]`）与合成方向反了——两者都只在真机上表现为"图歪了/镜像了" | 新测（Node `concat` 序列）+ `VisualDrawOpsPainterPureTest`（装载列序已有）。**本轮探针只覆盖单矩阵按契约列序回显（90° 旋转），嵌套合成未测** |
| **R-04** | 外层组 `opacity=a` 包内层组 `opacity=b` | 所有后代 `path`/`label` 的 `alpha == a*b`；每条绘制 op **恰有一个** alpha 字段（预乘一次，不重复相乘） | `abs(alpha - a*b) ≤ 1e-12` | 组 alpha 未预乘 → 半透明区域叠色；预乘两次 → 越叠越淡（无任何诊断） | 新测。**本轮探针 PASS**（0.5×0.5=0.25） |
| **R-05** | 组加 `visible:false` | ① 该子树**不发射任何 op**；② 子树内部**仍按同一严格度校验**（未知字段/悬空 `pathId` 照拒） | ①`ops` 中无该子树贡献的 op；②抛 `SCENE_FIELD_UNKNOWN` / `SCENE_PATH_REF` 等具体码 | "不可见就跳过校验" → 埋一颗只在变得可见时才炸的雷 | 新测。**本轮探针 PASS**（无 op；`bogus` 字段仍拒） |
| **R-06** | 交换两个**互不引用**的兄弟子树 | ① 输出 op 序列是两个子树块的**置换**（块内相对次序不变）；② `stats` 四元组不变 | ①按父块切分后逐块比较；②四整数相等 | 编译器用哈希表/并行导致发射顺序不稳定 → 同一场景每次生成不同快照，缓存与差分全失效 | 新测。**本轮探针只覆盖 ①（fill 顺序随输入翻转），② 未断言** |
| **R-07** | 把 `["Q",cx,cy,x,y]` 作为路径命令 | 输出**只含** `M/L/C/Z`；该 Q 被提升为 `["C", x1,y1,x2,y2, x,y]`，其中 `x1,y1 = P0 + 2/3(P1−P0)`、`x2,y2 = P2 + 2/3(P1−P2)`、端点 = `P2` | 六个分量逐值相对误差 ≤ 1e-12；命令字母集合 ⊆ {M,L,C,Z} | Q 被近似成折线或控制点算错 → 圆弧/抛物线变大变小，肉眼"差不多"但金标永远对不上 | `run_tests.mjs`（Q 提升用例）。**本轮探针 PASS**（[20,40,40,40,60,0]） |
| **R-08** | 让 `N` 个 `path` 节点引用**同一条** `scene.paths` 定义 | ① `stats.pathCommands == N × len(definition)`；② 每个 `path` op 的 `path` 数组逐字节相同 | ①②相等 | 定义被"优化"成只展开一次（少画）或被深拷贝后改动（同源不同形） | 新测。**本轮探针覆盖 ①（2×5=10），② 未断言** |
| **R-09** | 节点数 256 → 257 | 256 通过且 `stats.nodes == 256`；257 抛 `SCENE_NODE_COUNT`，诊断 ≤512 字符且**不含** `not defined`（避免被宿主误分类为 `SANDBOX_VIOLATION`） | 异常码前缀相等 + 长度断言 | 预算只写在文档里没落在代码上 → 一次污染编译就能把宿主拖进 OOM/ANR | `run_tests.mjs`（257 拒 + 256 上限编译）+ 解析器 `MAX_OPS` 复算（`VisualDrawOpsParser.kt:73-76`）。**本轮探针：255 通过、257 抛 `SCENE_NODE_COUNT`；256 与诊断文本未断言** |
| **R-10** | SDK `arc(cx,cy,r,a0,a1)` 的 `sweep` 从 θ 增到 2θ | 段数 `== ceil(abs(sweep)/90°)`；每段端点在圆上（`abs(‖(x,y)−(cx,cy)‖ − r) ≤ 1e-9`）；末端点 = 圆上角度 `a0+sweep` 处 | 段数整数相等；端点几何容差 1e-9（float64） | 弧被拆成错误段数/端点漂移 → 圆不闭合、箭头歪斜；且该误差会一路进 PDF 与截图 | `run_tests.mjs`（弧拆分用例）+ SDK 侧。**本轮探针 PASS**（1 段，端点 (0,10)） |

**本域三条要点**

- **R-01 是其余九条的前提**：若编译不确定，任何"变换前后对比"都会被引擎噪声污染。仓内已把它拆成"同输入 / 全新上下文 / SDK PRNG 不泄漏"三例（`run_tests.mjs:274-282`），本轮 94 例全绿。
- **R-02 vs R-03 的分工**：R-02 只证"路径保持局部坐标"，R-03 证"矩阵按 SVG 语义后乘"。两者合起来才排除"把变换烘焙进坐标"这条最隐蔽的退化路径（`VisualDrawOps.kt:100-104`、`2026-10-27-scenev1-compiler-design.md:123` 不变量 2）。
- **R-09 的诊断文本约束不是洁癖**：`visual-compiler.js:38-40` 明确写了"诊断文案刻意避开 `not defined` 字样——宿主分类器把该短语归 `SANDBOX_VIOLATION`"。所以关系里必须同时断言**码**与**禁用短语**，否则"码对了但诊断文案把失败分类带偏"不会被抓。

---

## 3. PDF 导出域：P-01…P-10

被测面：`MistakePdfPagePlanner.plan()`（纯排版核心，不碰 `android.graphics`，文字宽度经 `PdfTextMeasure` 注入，`MistakePdfPagePlanner.kt:16-30`）→ `MistakePdfExporter`（写盘/指纹）→ `VisualDrawOpsPdfPainterPure`（图元页几何）。

| ID | 输入变换 T | 必守关系（断言） | 判定式 / 容差 | 它消灭的具体失败 | 载体 |
|---|---|---|---|---|---|
| **P-01** | 同一输入 `plan()` 两次（同一 `measure`） | 两次 `PdfPlan` **逐字段相等**：`pageCount`、每页 item 序列、`skipped` 列表 | 结构化相等（data class `equals`） | 分页结果依赖哈希序/时间/随机 → 同一份错题两次导出页数不同，用户与测试都无法复现 | `MistakePdfPlannerGoldenTest`；新增"两次 plan 相等"用例 |
| **P-02** | 在输入**尾部追加**一个非空块 | ① `pageCount(A+B) ≥ pageCount(A)`；② A 的前 k 页 item 序列是 A+B 的前缀（k = `pageCount(A)`，末页允许追加）；③ 若触发预算则必须抛 `PAGE_LIMIT_EXCEEDED`，**不得静默少画** | ①整数不等式；②前缀逐项相等；③异常码相等 | 贪心分页"回头重排"导致已排好的内容位移；超预算时静默截断（用户以为导全了） | 新测（`MistakePdfPagePlannerTest` 扩展） |
| **P-03** | 把内容拆成 A、B 两份分别导出 | `pages(A∪B) ≤ pages(A) + pages(B)` | 整数不等式 | 分页状态跨文档泄漏（页脚/空白区重复占用）→ 合并导出比分开导出多出成页浪费 | 新测 |
| **P-04** | `maxRenderedLines` 从 L 降到 L−1 | 若 L 处成功，则 L−1 处**或**成功且 item 序列相同，**或**抛 `PAGE_LIMIT_EXCEEDED`；不存在第三种结果 | 三值枚举 + 码相等（`MistakePdfPagePlanner.kt:67-69`） | "少一行却成功"——把超预算改成静默截断，正是 fail-closed 被悄悄放宽的形态 | 新测 |
| **P-05** | `layout.fontScale` 2 → 3（字号/行高因子 1.0 → 1.5，`PdfTypography` `:568-578`） | ① 每个 item 的高度**非减**；② `pageCount` **非减**，或转为 `PAGE_LIMIT_EXCEEDED`；③ 绝不出现"字号变大、页数变少" | ①②③全部为不等式 + 码枚举；高度容差 0（同一 Float 计算路径） | 字号与分页各自量一套（排版器用旧行高、绘制器用新行高）→ 文字压线/裁切，且只在某些字号下出现 | `MistakePdfPagePlannerLayoutTest`；新增单调性用例 |
| **P-06** | 用**线性假测量**（`width = c·len`）并把宽度/高度/边距/字号统一乘 k | 分页结构（每页 item 的索引集合序列）**逐位相同** | 集合序列相等 | 分页里混进绝对像素常数（边距/页脚未随缩放）→ 只在极端字号下破版 | 新测（`PdfTextMeasure` 注入正是为此存在，`:20-21`） |
| **P-07** | 同一输入在"AI 图注开关/判定"前后 | ① `PdfPlan` 与 `inputSha256` **不变**（图注是渲染派生，不进输入与指纹，`:34-36`）；② 渲染产物中每张 AI 图**恰一行**图注，非 AI 图 0 行 | ①结构化相等 + sha 相等；②计数相等 | 图注进指纹 → 同内容两次导出指纹不同、增量导出失效；图注漏画/重复画 → 合规标注缺失 | `MistakePdfExporterAigcJvmTest`、`AiGeneratedImageCaptionTest`；新增计数断言 |
| **P-08** | `columns` 1 → 2 | ① item **多重集与相对顺序不变**（不丢项、不重排）；② 每页 item 数不增；③ `pageCount(2col) ≤ pageCount(1col)` **仅在 ②③ 同时成立时**记 PASS，否则记 `NOT_APPLICABLE` 并落反例 | ①多重集相等；②③整数不等式或 `NOT_APPLICABLE` | 双栏把图片按栏宽缩小后高度也算错（宽高比丢失）→ 双栏比单栏还费页 | `MistakePdfPagePlannerLayoutTest`；新增多重集断言 |
| **P-09** | `viewBox` 或 `ptPerDu` 翻倍 | `widthPt' = ceil(2·w·p)` 且满足 `2·ceil(w·p) − 1 ≤ widthPt' ≤ 2·ceil(w·p)`；任意输入下 `1 ≤ widthPt,heightPt ≤ 14400`，越界抛 `PAINTER_PDF_PAGE_BOUNDS` | 整数不等式 + 码相等（`VisualDrawOpsPdfPainterPure.kt:61-92`） | `round/floor` 把右/下 ≤1pt 内容裁到页外；取整后 0/负值在 `PageInfo.Builder` 处炸成**无码** IAE | `VisualDrawOpsPdfPainterPureTest`（已有边界用例）；新增缩放不等式 |
| **P-10** | 在输出流写入中途注入 `IOException`（故障注入型） | 抛 `PAINTER_PDF_IO`（有码族）；**不返回**"看起来成功"的结果；目标文件不被登记为可用产物 | 异常码相等 + 产物状态断言（不存在或大小 0 且未被索引） | 裸 `IOException` 逃出码族 → 调用方无法分类；半截 PDF 被当成成功交付 | `PdfWriteIntegrityTest`；新增注入用例 |

**本域两条要点**

- **P-02/P-04 是"fail-closed 不可放宽"的机器化**。批次 43 §9 的核心教训是"不得删断言/放宽 required 来变绿"（`AGENTS.md` §4）；这两条把"超预算必须抛"写成关系，任何"改成截断"的补丁都会当场让关系 VIOLATED。
- **P-08 特意留了 `NOT_APPLICABLE` 出口**：双栏对宽图可能反而更费页（宽高比 + 栏宽），这不是缺陷。关系必须能表达"前置不满足"，否则测试会逼着实现去迎合一个错误断言。

---

## 4. 检索域：S-01…S-08

被测面：词面召回（`RoomKnowledgeBaseStore.readSubjectKnowledgeRecallCandidates`，`core/database/.../RoomKnowledgeBaseStore.kt:167-198`）→ 稠密腿重排（`core:data` 的 `OnDeviceDenseRecallReranker`）→ 排序域守卫（`DenseRecallOrdering`）。融合公式写死在 `DenseFusion.kt:21-59`：`score = α·denseNorm + (1−α)·lexNorm`，α=0.5，域内 min-max，缺失腿给 0，并列按 `node_id` 升序。

| ID | 输入变换 T | 必守关系（断言） | 判定式 / 容差 | 它消灭的具体失败 | 载体 |
|---|---|---|---|---|---|
| **S-01** | 让稠密腿返回**任意**结果（重复项/缺项/多项/乱序） | 最终候选集合与长度**等于**词面召回集；否则整体退回词面次序 | `set(out) == set(lexical)` 且 `size` 相等（`DenseRecallOrdering.kt:21-24`） | 模型侧再多错也只能"失效"，不能改变召回集合——否则 subject 作用域/limit 上限会被静默突破 | `DenseRecallOrdering` 单测（若缺则新测） |
| **S-02** | 候选 <2 条，或 `queryText` 为 null/blank | 稠密腿**不被调用**（不加载模型与 17MB 资产），输出 == 词面序 | 调用计数 == 0；序列逐位相等（`RoomKnowledgeBaseStore.kt:207-208`） | 为"跑一遍"而冷启动模型 → 首屏检索被 17MB 资产加载拖慢，且单条候选永远不变序 | 新测（seam 已有：reranker 是构造注入） |
| **S-03** | 对某一腿的所有原始分**加常数 c** | 该腿 min-max 归一化后**逐位不变**，融合次序不变 | `norm(x+c) == norm(x)`，容差 1e-9 相对（`DenseFusion.kt:24-35`） | 归一化写成 `x/max` 而非 `(x−min)/(max−min)` → 分数整体漂移就改变排序 | `DenseFusionTest`（夹具由参考实现现算，`:6-20`） |
| **S-04** | 置换两条腿 map 的**插入顺序** | `DenseFusion.order` 输出**逐位相同** | 序列相等（排序键 `(-score, node_id)`，`DenseFusion.kt:54-58`） | 依赖 `HashMap` 迭代序 → 同一查询在不同进程/不同 JVM 版本给出不同名次 | `DenseFusionTest` |
| **S-05** | α = 1（纯稠密）/ α = 0（纯词面） | α=1 时次序 == 稠密归一降序 + `nodeId` 升序；α=0 时 == 词面归一降序 + `nodeId` 升序 | 与离线参考 `best_first` 逐位一致 | 融合权重"顺手调参"改变判官口径，离线评测与端侧结果无法对账 | `DenseFusionTest`（10 例融合夹具） |
| **S-06** | 删除/改写 `knowledge_search_index_state` 版本锚点 | 整科重建；重建后**候选集合**与锚点正确时相同（集合不变，次序允许因重建变） | 集合相等；`INDEX_VERSION` 相等（`RoomKnowledgeBaseStore.kt:224-248`） | 抽取规则换版后旧截断行永久残留 → 检索"越用越差"且无诊断 | 设备轨（Room）。**本轮未执行 → BLOCKED** |
| **S-07** | `searchFeatures.size` 超过 `MAX_QUERY_FEATURES` | 抛 `IllegalArgumentException`（fail-closed），**不返回部分结果** | 异常类型相等 + 返回值不存在 | 超长查询被静默截断成另一个查询，用户看到的排序无从解释 | 新测（`RoomKnowledgeBaseStore.kt:178-180`） |
| **S-08** | `searchFeatures` 为空集 | 退化为 `readSubjectKnowledgeNodes(subject, min(limit,256))`，与稠密腿无关 | 结果序列与直接调用逐位相等（`RoomKnowledgeBaseStore.kt:181-183`） | 空查询走稠密腿 → 用空文本编码出一个"平均向量"，把无关节点排到前面 | 新测 |

**本域要点**

- **S-01 是本域的地基**：`DenseRecallOrdering` 的 KDoc 明说"模型侧再怎么错（重复、丢项、多给项），都只能让稠密腿失效，不能让它改变召回集合"（`DenseRecallOrdering.kt:11-14`）。所有其它关系都建立在"集合先对"之上。
- **S-03/S-05 的判据来自离线参考实现，不是重写公式**：夹具由 `gen_dense_device_fixture.py` 直接调用 `stage3_expectation.minmax/best_first` 现算（`DenseFusionTest.kt:6-20`）。所以这两条不是"两套口径互相印证"，而是"端侧 == 权威实现"。
- **S-06 是唯一必须上设备的**：Room + 索引状态表，JVM 够不着；本轮按纪律不跑 `am instrument`，记 BLOCKED 而非"未发现问题"。

---

## 5. FSRS 调度域：F-01…F-10

被测面：`FsrsScheduleMath`（FSRS-6 逐式移植，`core/domain/.../FsrsScheduleMath.kt:7-13`；**故意不移植 fuzzing，投影必须确定**）+ `FsrsEvidenceRatingMapper`（证据→评级，`:246-324`）。所有关系都写在**默认参数** `DEFAULT_PARAMETERS`（`:24-46`）与夹取域 `S∈[0.001,36500]`、`D∈[1,10]`（`:48-54`）内。

| ID | 输入变换 T | 必守关系（断言） | 判定式 / 容差 | 它消灭的具体失败 | 载体 |
|---|---|---|---|---|---|
| **F-01** | 同 `(S,D,R,rating,params)` 调两次 | 输出**逐位相同**（无 fuzz、无时钟） | `Double.toRawBits` 相等 | 移植时带进 py-fsrs 的 fuzzing → 同一学习历史两次重放得到不同间隔，投影不可重放 | 新测（`FsrsScheduleMath.kt:11-12` 已声明"deliberately not ported"） |
| **F-02** | `t` 从 0 递增；`S` 递增 | `R(0,S) == 1`；`R(t,S)` 对 t **严格递减**；对 S **严格递增**；`R ∈ [0,1]` | 严格不等 + 值域；容差 1e-12 | 遗忘曲线写成线性或指数 → 长间隔题的到期时间系统性偏早/偏晚 | `FsrsScheduleMathTest:22`（已有）；新增"对 S 单调" |
| **F-03** | 取 `I = intervalDays(S, r*, decay)` 再回算 `R(I,S)` | **前置**：`1 ≤ raw(S,r*) ≤ 36499.5`（即未被夹到 1 天或 36500 天上限）。域内：`abs(R(I,S) − r*) ≤ abs(∂R/∂t(I,S))·0.5 + 1e-9`（±0.5 天舍入的一阶界） | 实测：r*=0.90 最坏 0.024846 ≤ 界 0.028344；r*=0.95 最坏 0.014363 ≤ 界 0.015591 | 倒函数与正函数各写一套常数 → 设 0.9 实际给 0.7，复习节奏整体错位 | `FsrsScheduleMathTest:34`（默认保留率往返）；新增带前置的域内往返 |
| **F-04** | `S1<S2`；`r1<r2` | `I(S1,r*) ≤ I(S2,r*)`；`I(S,r1) ≥ I(S,r2)` | 整数不等式（含等号） | 舍入方向写反（`floor` 而非 half-to-even）→ 间隔整体偏短 | `FsrsScheduleMathTest:348,360`（tie/边界已有）；新增双单调 |
| **F-05** | 同日复习，评分为 HARD/GOOD/EASY | `S' ≥ S`；评分为 AGAIN 时 `S' < S`（未触夹取时严格） | 不等式，容差 1e-12 | 同日成功反而降稳定度（历史 off-by-one 的同族缺陷） | `FsrsScheduleMathTest:62,75`（已有） |
| **F-06** | 固定 `(D,S,rating)`，`R` 从 0 增到 1 | `nextRecallStability` 对 R **严格递减** | 严格不等（逐 0.01 采样，100 个点全严格） | 符号写反 → 越熟的题复习后稳定度涨得越多，间隔爆炸 | 新测。**本轮探针 PASS**（3 组样本全严格递减） |
| **F-07** | 固定 `(D,S,R)`，评分 HARD / GOOD / EASY | `S'(HARD) < S'(GOOD)`，且 `S'(GOOD) == S'(EASY)`（**精确相等**：`parameters[16]` 被钉为 1.0，"Easy bonus" 在本仓永不生效，`FsrsScheduleMath.kt:16-22`）；AGAIN 走 lapse 分支，`S'(AGAIN) = nextForgetStability(...) < S` | 严格不等 + 1e-12 内相等（禁止把 GOOD/EASY 写成严格序） | 评级未接到公式（0-based/1-based 混用）→ 差评反而涨稳定度；或误把 EASY 当成"额外奖励"去实现，凭空造出一个不存在的 bonus | `FsrsScheduleMathTest:100`（已有"easy bonus is neutral"）；新增链式断言。**本轮探针 PASS**（3 组样本：HARD<GOOD 严格、GOOD==EASY 逐位） |
| **F-08** | 同上，看 `nextDifficulty` | **前置**：`D ∈ (1,10)` 开区间。域内 `D'(AGAIN) > D'(HARD) > D'(GOOD) > D'(EASY)`；`D` 落在边界时只断言**非严格** `≥` | 开区间严格链；边界非严格链 | 均值回归项写错 → Good 复习反而把难度推高（`fsrs-algorithm-gap-analysis.md:16-30` 记录的 off-by-one 形态） | 新测。**本轮探针发现 D=1.0 处 GOOD/EASY 都夹到 1.0 → 严格序不成立**（详见下方要点） |
| **F-09** | 任意（含极端）输入 | `S' ∈ [0.001, 36500]`、`D' ∈ [1,10]`、`I ∈ [1,36500]`；非有限输入被 `require` 拒（不返回 NaN） | 值域断言 + 异常 | 夹取漏一处 → NaN/Inf 进数据库，之后所有下游都读到脏值 | `FsrsScheduleMathTest:147,313`（已有边界/长历史）；新增随机属性测试 |
| **F-10** | 证据 `(reason, weight)` 全表遍历 | 映射到评级的关系逐项成立：`SELF_REPORTED_RECALL` 的 `reported=EASY` 但 `scheduling=GOOD`（封顶）；`weight∈[0.8,0.9)`→GOOD、`[0.7,0.8)`→HARD；`INDEPENDENT_CORRECT` 且 `weight < 0.85−1e-6` → HARD；`MODEL_JUDGED_CORRECT` 恒 HARD、`MODEL_JUDGED_INCORRECT` 恒 AGAIN | 映射表逐项 `equals`（`FsrsScheduleMath.kt:260-321`） | 折扣链在写库前改写 weight 后，评级被"从 weight 反推"→ 落库评级与调度评级不一致（P6/P7 审计缺陷） | `FsrsScheduleMathTest:157,176,197,225,242,294`（已有，覆盖充分） |

**本域三条要点**

- **F-03 的前置条件不是可选的礼貌**。本轮参考模型探针（`python -I fsrs_probe.py`）实测：
  ```
  r*=0.90: max|R(I(S),S)-r*| = 0.545405  at S=0.00118 I=1 R=0.354595
  r*=0.80: max|R(I(S),S)-r*| = 0.445405  at S=0.00118 I=1 R=0.354595
  ```
  夹到 1 天时 `R(1,0.00118)=0.3546`，离 0.9 差 0.55。若关系不写域前置，会在 S 很小的新卡上**红**，然后有人会去"修"公式——这才是真正的危险。
- **F-08 的边界反例是探针抓到的**（`python -I fsrs_probe2.py`）：
  ```
  D=1.0 -> ['7.0270', '4.0106', '1.0000', '1.0000'] order ok: False
  D=5.0 -> ['8.3418', '6.6660', '4.9902', '3.3145'] order ok: True
  ```
  `D` 已在下界时 GOOD/EASY 都被夹到 1.0，严格序退化为非严格。关系必须把"开区间严格 / 边界非严格"写清，否则这条测试会在每次"难度已到底"的历史上误红。
- **F-07 的第三段是"相等"不是"严格序"——本轮探针抓到的第二个反例**。初稿把 `S'(GOOD) < S'(EASY)` 写进了关系，实测（`python -I fsrs_probe3.py`）：
  ```
  D=5.0 S=10.0 R=0.90 -> HARD=23.246875110 GOOD=32.026729482 EASY=32.026729482  H<G:True G==E:True
  D=8.0 S=100.0 R=0.70 -> HARD=246.923515355 GOOD=344.302486457 EASY=344.302486457  H<G:True G==E:True
  D=2.0 S=3.0 R=0.95 -> HARD=6.570089567 GOOD=8.936297917 EASY=8.936297917  H<G:True G==E:True
  ```
  原因写在 `FsrsScheduleMath.kt:16-22`：`parameters[16]` 被刻意钉为 1.0，因为 `schedulingRatingFor` 永不返回 EASY（主观"很容易"封顶 GOOD）。关系写成严格序会逼着实现去"补一个 bonus"，那才是把已关闭的缺陷重新打开。
- **F-09 与 F-01 一起构成"可重放"**：批次 43 的教训之一是"投影改了没人能回答改了什么"（`docs/research/algorithm-version-ledger.md:9-30`）。F-01（逐位确定）+ F-09（值域封闭）是任何投影 bump 前后都能跑的最小回归对。

**本域未覆盖（如实声明）**：`ReviewCapacityPolicy` 的日配额守恒（`dailyQuota`/`allocateByDayWeights`，`core/data/.../ReviewCapacityPolicy.kt:105-112,163-175`）与 FSRS 数学无耦合，属纯配额算术，本文件不纳入 F 域；若要在 E 域收口，需另立"容量关系"小节。

---

## 6. 备份恢复域：B-01…B-10

被测面：选择与闭包（`BackupSelection`）、机器清单与摘要（`SmbkSelectionPlan`）、快照裁剪（`BackupSnapshotTrimmer`）、归档编解码（`SmbkArchiveCodec`）、写侧装配（`AndroidBackupRepository`）。**不可变清单**：`ALLOWED_MASKS = {9,13,15}`，`0`/`8` 刻意不在其中（`BackupSelection.kt:161-168`），派生索引在两处恒裁（`BackupSnapshotTrimmer.kt:58-60`）。

| ID | 输入变换 T | 必守关系（断言） | 判定式 / 容差 | 它消灭的具体失败 | 载体 |
|---|---|---|---|---|---|
| **B-01** | `create(P)` → `restore` 到空库 | 逻辑内容相等：92 表 `rowCounts` 逐表相等；关键摘要相等；资产字节 SHA-256 相等 | 逐表整数相等 + sha 相等（快照一致性由 SQLite `VACUUM INTO` 保证，S6 原文） | 恢复后"少了三张表"或"图片在但字节没了"——只有逐表对拍才看得见 | `BackupRestoreInstrumentedTest`、`BackupRowCountsCoverageInstrumentedTest`（设备）；**本轮未执行** |
| **B-02** | 构造 `rowCounts` map 的**插入顺序**置换 | `closureDigest` **逐位相同** | 64 位 hex 相等（payload 按 key 排序，`SmbkSelectionPlan.kt:682-687`） | 摘要依赖 HashMap 序 → 同一份内容两台设备算出不同 digest，跨设备迁移被判"包坏了" | `check_backup_plan_sync.py`（Python 独立复算）**本轮实跑 PASS**；`SmbkSelectionPlanTest` |
| **B-03** | mask 9 → 13 → 15 | 内容集合单调包含：表集合 `⊆`，且每表行数 `≤` | 集合包含 + 逐表不等式 | 选"题+图"却带出会话；或选全量反而少表 | `SmbkSelectionPlanTest`（空库全选 golden）；新增包含关系用例 |
| **B-04** | mask ∈ {0, 8, 1, 2, 4, 5, 7, 11, …} | 抛 `IllegalArgumentException`，且**在打开任何快照文件之前**（无副作用） | 异常类型相等 + 目标文件不存在/未修改（`BackupSnapshotTrimmer.kt:58-59`、`BackupSelection.kt:136-145`） | `0`（重置包）或 `8`（孤儿资产）被放行 → 用户以为备份了，恢复后资产被回收清掉 | `SixC2RulingInvariantTest`、`SmbkSelectionPlanTest`（已有不变式） |
| **B-05** | 对同一快照 `trim` 两次 | 第二次 `trimmedTableCount == 0`；`rowCounts` 逐表相同；digest 相同 | 整数相等 + hex 相等 | 裁剪不幂等 → 反复备份把行越删越少（且每轮 digest 都"自洽"） | 设备轨（SQLite）。**本轮未执行 → BLOCKED** |
| **B-06** | mask = 15 | 用户四类表行数与裁剪前**逐表相同**（`assertSelectedRowsUnchanged`）；仅 2 张派生索引被裁 + FTS 重建 | 逐表整数相等（`BackupSnapshotTrimmer.kt:423`、`:58-60`） | "全选"顺手把用户数据也裁了——本批不可变清单第 3 条正是钉这个 | 设备轨 + `BackupRowCountsCoverageInstrumentedTest`。**本轮未执行** |
| **B-07** | 注入损坏：payload 翻 1 字节 / 改 manifest 1 字段 / 截断 | 校验**必拒**（SHA 不符 / `closureDigest` 不符 / `rowCounts` 键集不符），且实时库**不交换任何表** | 拒绝码/异常 + 实时库摘要 == 失败前 | 损坏包被"尽量恢复" → 半套数据覆盖现网，用户丢掉全部错题 | `SmbkArchiveCodecTest`、`V4PackFixtureTest`（冻结夹具 `forged-count.smbk` / `dropped-nonempty.smbk`） |
| **B-08** | 源库**新增一行错题**后再打包 | `problemCount` 与对应表 `rowCounts` 各 +1；其它表不变；`closureDigest` 改变 | 逐表整数差分 == {目标表:+1}；digest 不等 | 行数统计写死或漏表 → 回执"说 0 条"实际有 3 条（如实的回执是产品承诺） | `SmbkSelectionPlanTest` + 设备轨 |
| **B-09** | 读 v1/v2 无 `selection` 的包；读 `formatVersion≥3` 却缺 `selection` 的包 | 前者按**全量语义**读回（`selection == null`），行为与旧版逐字一致；后者**拒绝** | 前者字段相等；后者异常/拒绝码相等（`SmbkArchiveCodec.kt:20-40`、`:432-490`） | 旧包被猜成"只含某几类"而少恢复；或新包缺块被猜成全量而多恢复 | `SmbkArchiveCodecTest`、`BackupV4PackRestoreInstrumentedTest` |
| **B-10** | 恢复中途注入 IO 失败（故障注入型） | 实时库不被部分改写（交换前校验）；临时产物被清理；失败后实时库摘要 == 失败前 | 摘要相等 + 临时目录为空（`BackupTempArtifactCleanupTest`） | 恢复失败留下半套表 + 残留临时库，下一次恢复读到混合状态 | `BackupTempArtifactCleanupTest`、`RestoreRecoveryAttentionTest` |

**本域三条要点**

- **B-02 是本轮唯一"已在非设备面实跑通过"的 B 域关系**：`python -I tools/ci/check_backup_plan_sync.py` 输出 `digest: recomputed==test golden==block golden：recomputed=5c690755…`、`v4 fixture: bytes == golden == sidecar：3 夹具字节与 sidecar/golden 逐位一致`，末行 `PASS 四方一致（code / §9.11 / golden / 73.json）`，`EXIT=0`。它同时是本域"摘要必须与内容绑定"的独立第二来源（Python 实现 vs Kotlin 实现）。
- **B-03/B-04/B-06 只增不变量**，符合不可变清单"只可加不变量测试"的许可；它们**不新增**任何创建/恢复路径，也不触碰 `ALLOWED_MASKS`。
- **B-07 已有冻结夹具**：`tools/backup_fixtures/v4/` 里的 `forged-count.smbk`（伪造行数）与 `dropped-nonempty.smbk`（丢了非空表）就是"损坏必拒"的正例素材，不用现造。

---

## 7. 落点、执行计划与判定规则

### 7.1 本轮实际执行的探针（可复跑）

| # | 命令（逐字） | 结果 | 覆盖 |
|---|---|---|---|
| E1 | `node tools/visual_compiler/run_tests.mjs` | `94 passed, 0 failed`；`[timing] upper-bound scene … 2.6 ms on Node v26.4.0` | 编译器/词表/预算/确定性（R-01/R-07/R-09/R-10 的既有载体） |
| E2 | `node C:/Users/听云/AppData/Local/Temp/mt-probe/probe.mjs` | `14 passed, 0 failed` | R-01…R-10（关系可行性验证，14 条断言） |
| E3 | `python -I tools/ci/check_backup_plan_sync.py` | 末行 `PASS 四方一致（code / §9.11 / golden / 73.json）`，`EXIT=0` | B-02、B-07 的夹具与摘要复算 |
| E4 | `python -I fsrs_probe.py` | `r*=0.90 … 0.545405 at S=0.00118`；`monotonicity violations: 0` | F-03 前置条件、F-04 单调 |
| E5 | `python -I fsrs_probe2.py` | 域内最坏 0.024846 ≤ 界 0.028344；`D=1.0 order ok: False` | F-03 容差取值、F-08 边界反例 |
| E6 | `python -I fsrs_probe3.py` | 三组样本 `H<G:True G==E:True`（GOOD 与 EASY 逐位相等） | F-07 的"末段相等"，并纠正初稿的严格序写法 |

> E2/E4/E5/E6 的脚本在**仓外临时目录**（`%TEMP%/mt-probe/`），只读仓内资产，不写仓内任何文件。E2 复用 `run_tests.mjs` 的同源装配（同一 `visual-compiler.js` / `visual-sdk.js`）。E4/E5/E6 是**参考模型**（按代码公式重写的 Python），证明的是"公式层面成立/不成立"，**不证明 Kotlin 代码执行结果**——这一点在 F 域的 verdict 上仍然是 UNVERIFIED。

**E2 的两次"红"是探针自身写错，已修正后复跑**：第一次把节点预算错误码写成 `SCENE_NODE_LIMIT`（实际 `SCENE_NODE_COUNT`），第二次把弧端点的数组下标取错。修正后 14/14。这条如实记下来，是因为"探针红了先怀疑实现"正是本批要防的反射。

**E2 的覆盖边界（防止把"14 条断言"读成"10 条关系全证"）**：探针证到的是——R-01 逐字节确定、R-02 三条断言全部、R-04 alpha 预乘、R-05 无 op 且仍校验、R-06 次序随输入、R-07 Q→C 六个分量、R-08 引用展开计数、R-10 段数与端点、R-03 单矩阵回显、R-09 257 拒。**未证到**：R-03 的嵌套合成与 `det` 乘法、R-06 的 `stats` 不变、R-08 的"path 数组逐字节相同"、R-09 的 256 通过与否及诊断文本约束。这四项仍记 `UNVERIFIED`，不得因 E2 全绿被顺带当成通过。

### 7.2 承载面与阻塞

| 域 | 可立即执行（非热区/非设备） | 需批准（热区或已批准 hunk） | 需设备（本轮 BLOCKED） |
|---|---|---|---|
| 渲染 | R-01…R-10 全部可在 `tools/visual_compiler/` 扩测（Node，只读资产） | — | `QuickJsSceneCompilerInstrumentedTest`（真机 QuickJS） |
| PDF | P-09（`core/export` 纯函数测试）需已批准 hunk | P-01…P-08、P-10（`core/export` 测试源集） | 真写盘 + `PdfRenderer` 回读的字节级一致 |
| 检索 | S-01…S-05、S-07、S-08（`core:data`/`core:database` 测试） | — | S-06（Room + 索引状态表） |
| FSRS | —（`core/domain` 是热区） | F-01…F-10 全部需主会话裁定后落 `core/domain/src/test` | — |
| 备份 | B-02（已有 Python 门，可扩） | — | B-01、B-05、B-06、B-08、B-10 |

**关于 FSRS 的阻塞必须说清**：`core/domain` 是热区（本 ask 明令禁改）。因此 F 域 10 条关系**目前只有定义、没有落点**——写成"已覆盖"就是假绿。建议的处置：主会话批准后，把 F-01…F-10 作为**新测试文件**加到 `core/domain/src/test`（不改生产代码），一次提交。

### 7.3 判定规则（与验收替代机制对齐）

1. 每条关系的 verdict 只有 `SATISFIED` / `VIOLATED` / `NOT_APPLICABLE`；`VIOLATED` 必须给反例三元组。
2. **PASS 需 ≥2 类独立证据**：例如 F-03 = "Kotlin 测试 XML" + "参考模型探针 JSON"；B-02 = "Python 门输出" + "Kotlin 单测 XML"。同一脚本跑两次不算两类。
3. `skipped` 一律映射 **BLOCKED**，不得计入 PASS 分母（`assumeTrue` 的假绿风险见批次 44 基线 M12）。
4. **关系强度要报告**：按 S2 的 adequacy 视角，每条关系登记它覆盖的源输入类（正常/边界/退化/损坏）与数量；只跑一个正例的关系不得标"强"。
5. 新工具 `tools/ci/metamorphic_checks.py`（计划 D0 item 7）建议以本文件的表为内容源，落成 JSON：`{id, domain, transform, precondition, assertion, tolerance, carrier, status}`，`status` 初值一律 `UNVERIFIED`，由执行器回填。

### 7.4 新增关系各自消灭的失败（汇总，对应"新增必须指认"）

- 渲染 10 条：全部指向"编译产物不可复现/变换语义错位"，其中最贵的是 R-02（变换烘焙进坐标）与 R-09（预算只写在文档）。
- PDF 10 条：全部指向"分页静默丢内容/页几何越界无码异常"，最贵的是 P-02/P-04（fail-closed 被放宽）。
- 检索 8 条：指向"模型侧错误污染召回集合/排序不可复现"，最贵的是 S-01。
- FSRS 10 条：指向"公式移植的符号与基数错误"，最贵的是 F-03（倒函数不自洽）与 F-08（边界夹取）。
- 备份 10 条：指向"选择越权/损坏包被部分恢复/摘要不可比"，最贵的是 B-04 与 B-07。

没有一条是"更完备/更健壮"这类无法指认失败的新增。

---

## 8. 未验证与残余风险（不得写成通过）

| 项 | 状态 | 理由 |
|---|---|---|
| P-01…P-08、P-10 的 Kotlin 执行 | **UNVERIFIED** | `core/export` 只做已批准 hunk；本轮未跑 Gradle |
| 屏/PDF 逐像素一致性 | **不作为判据** | 两侧不是同一栅格器（Skia 写 / PDFium 读），逐像素必红且不可归因（`screen-pdf-parity-gate.md:14`）。本文件只用**相对门**（IoU/chanDist 阈值表 `:206-215`），且该阈值本身标注"初始值，未在设备上标定" |
| S-06 索引自愈 | **BLOCKED** | 需 `am instrument`；纪律禁跑 `:app:connectedLocalFirstDebugAndroidTest` |
| B-01/B-05/B-06/B-08/B-10 | **BLOCKED** | 需设备 SQLite/Room |
| F-01…F-10 的落点 | **BLOCKED（热区）** | `core/domain` 禁改；仅定义成立，执行未发生 |
| 真机字体/字重导致的文本度量差异 | **UNVERIFIED** | 本文件不涉及设备金标；P-06 只用线性假测量证明几何齐次性 |
| `PageInfo.Builder` 的正整数约束原文 | **未取得（转录）** | Android 官方页两次超时；仅依赖仓内 KDoc 转录与 P-09 的显式值域断言 |
| py-fsrs 公式正文 | **未取得** | README 超时；F 域全部结论以仓内 `FsrsScheduleMath.kt` 为准 |

---

## 9. 来源清单（一次列全，含未采纳）

**外部（本轮取得）**

1. Duque-Torres & Pfahl, *Towards a Complete Metamorphic Testing Pipeline*, arXiv:2310.00338 — https://arxiv.org/abs/2310.00338 （采纳：MR 定义/结构）
2. Fu, Sun, Zhang, Liu, *Test Adequacy for Metamorphic Testing: Criteria, Measurement, and Implication*, arXiv:2412.20692 — https://arxiv.org/abs/2412.20692 （采纳：adequacy 报告要求）
3. W3C, *SVG 1.1 Coordinate Systems, Transformations and Units* — https://www.w3.org/TR/SVG11/coords.html （采纳：matrix 六值、嵌套后乘、顺序）
4. W3C, *SVG 1.1 Painting: Fill Properties* — https://www.w3.org/TR/SVG11/painting.html （采纳：nonzero/evenodd）
5. AOSP `platform_frameworks_base`, `graphics/java/android/graphics/DashPathEffect.java` — https://github.com/aosp-mirror/platform_frameworks_base/blob/main/graphics/java/android/graphics/DashPathEffect.java （采纳：`length < 2` 抛无码异常、未强制偶数）
6. SQLite, *VACUUM*（含 `VACUUM INTO`） — https://www.sqlite.org/lang_vacuum.html （采纳：consistent snapshot 原文）
7. `open-spaced-repetition/py-fsrs` — https://github.com/open-spaced-repetition/py-fsrs （部分采纳：MIT / 522★ / pushed 2026-08-09，仅元数据；正文未取）

**外部（未采纳，登记备查）**

8. *Metamorphic Relation Patterns for Metamorphic Testing* (STVR 2025) — https://onlinelibrary.wiley.com/doi/abs/10.1002/stvr.70003 （仅摘要）
9. *Metamorphic Testing of Code Search Engines* (IEEE) — https://ieeexplore.ieee.org/document/9405312 （仅摘要）
10. *Metamorphic Relation Generation: State of the Art and Visions* — https://arxiv.org/abs/2406.05397 （仅摘要）
11. `developer.android.com` DashPathEffect / PdfDocument.PageInfo.Builder — 两次连接超时，未取得

**仓内（本轮实读）**

12. `docs/research/2026-10-27-scenev1-compiler-design.md`（词表七不变量 `:120-150`、Canvas 映射 `:171-180`）
13. `docs/research/2026-10-28-screen-pdf-parity-gate.md`（相对门 `:14`、度量 `:187-199`、阈值 `:206-215`）
14. `docs/research/fsrs-algorithm-gap-analysis.md`（1-based 换算与 off-by-one 证据链 `:16-40`）
15. `docs/research/2026-11-06-batch-44-baseline.md`（验收机制零存在 F1/F2/F3 `:277-279`）
16. 代码：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/{VisualDrawOps,VisualDrawOpsParser,VisualDrawOpsValidator,VisualDrawOpsPainterPure,VisualDrawOpsPdfPainterPure}.kt`
17. 代码：`core/data/src/main/resources/visual/visual-compiler.js`、`core/domain/.../FsrsScheduleMath.kt`、`core/database/.../{RoomKnowledgeBaseStore,DenseRecallOrdering}.kt`、`core/data/.../knowledge/dense/DenseFusion.kt`、`core/export/.../MistakePdfPagePlanner.kt`、`core/domain/.../BackupSelection.kt`、`core/data/.../backup/{SmbkSelectionPlan,BackupSnapshotTrimmer,SmbkArchiveCodec}.kt`

---

## 10. 复跑命令（供验收台账引用）

```bash
# E1 编译器 Node 测试台（非 Gradle）
node tools/visual_compiler/run_tests.mjs

# E2 关系探针（仓外脚本，只读仓内资产）
node "$TEMP/mt-probe/probe.mjs"          # Windows: C:/Users/<user>/AppData/Local/Temp/mt-probe/probe.mjs

# E3 备份四方门（纯 Python，只读）
python -I tools/ci/check_backup_plan_sync.py

# E4/E5/E6 FSRS 参考模型探针（仓外）
python -I "$TEMP/mt-probe/fsrs_probe.py"
python -I "$TEMP/mt-probe/fsrs_probe2.py"
python -I "$TEMP/mt-probe/fsrs_probe3.py"
```

**复跑判据**：E1 末行必须 `0 failed`；E2 必须 `0 failed`（14 条断言）；E3 必须 `EXIT=0`；E4/E5/E6 输出必须与 §7.1 表中数字一致（参考模型是确定性的，数字漂移即说明参数或公式被改，先查 `DEFAULT_PARAMETERS` 是否被 bump）。
