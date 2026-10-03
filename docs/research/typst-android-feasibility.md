# Typst on Android 可行性研究（阶段 4B 批 3 · B5）

> 日期：2026-10-03。范围：**只出研究，不实施**（计划 §批 3 B5：不进本批门禁，随批交付）。
> 结论一句话：**不立项 Typst 二期，维持参数化模板 + Canvas MathBox 路线**——许可放行，但体积门（≤15MB）
> 不满足、minSdk 26 与项目 23 冲突、编译性能无可信基准。复查条件见文末。
> 外部资料纪律：官方/一手来源优先；单一来源处标「待验证」；未找到处写「未找到 + 假设」，不编造。
> 体积数字均为 **2026-10-03 实测的产物原大小**（Maven Central / npm 原始字节数），不是 APK 增量猜测；
> APK 增量未实测（需真机对比安装包），本文如实标注。

## 0. 项目现状（冻结基线）

- 导出渲染：纯 Kotlin 分页器（`core/export/.../MistakePdfPagePlanner.kt`）+ Android `PdfDocument` 绘制
  （`DeterministicMistakePdfRenderer.kt`），公式经 `CanvasMathBoxRenderer` + `MathBox` 解析后矢量绘制；
  文本/CJK 全部走 Android 系统字体（`Typeface.DEFAULT`），**零额外资产**。
- 项目 `minSdk = 23`（`app/build.gradle.kts:21`；core 模块同为 23）。
- 性能现状：批量 60 题导出仪器化用例断言 < 5s（`MistakePdfExporterInstrumentedTest`，
  `pdfQuestions=60` 日志），单题为本地秒级任务。

## 1. 四问正文

### ① 许可证：能否商用嵌入？

| 组件 | 许可 | 来源（一手） |
|---|---|---|
| `typst/typst`（编译器本体） | Apache-2.0（GitHub API `license.spdx_id`；仓库含 LICENSE） | https://github.com/typst/typst |
| `fatihcatalkaya/java-typst`（JVM 封装 + WASM） | Apache-2.0 | https://github.com/fatihcatalkaya/java-typst |
| `dylibso/chicory`（JVM WASM 运行时） | Apache-2.0 | https://github.com/dylibso/chicory |
| `Myriad-Dreamin/typst.ts`（社区 WASM 构建） | Apache-2.0 | https://github.com/Myriad-Dreamin/typst.ts |

**结论**：Apache-2.0 允许闭源商用嵌入（保留 LICENSE/NOTICE、标注修改）。许可本身**不构成**否决项。

**待验证（许可链的第二段）**：Typst 编译链自带字体与第三方字体资产（Typst 官方仓库 `assets/fonts` 等），
以及本项目必须自备的**中文字体**——这些字体各自的许可未逐项核验，属"单一来源（仓库元数据）+ 未展开"，
标 **待验证**；如二期立项，必须先出字体清单与许可审计。GB 系强制标识与本路线无关（本文不展开）。

### ② WASM / NDK 两条路线的体积增量

**路线 A：java-typst（Typst 编译为 `wasm32-wasip1`，Chicory 纯 JVM 解释执行）**

- `io.github.fatihcatalkaya:java-typst:2.2.0` jar 实测 **35,981,116 B ≈ 34.3 MB**（Maven Central `Content-Length`，2026-10-03）；
  同一坐标 `2.0.0` 为 **16,646,804 B ≈ 15.9 MB**。jar 内含 wasm 模块（Rust `wasm32-wasip1` + `wasm-opt` 构建）。
- 其 Chicory 依赖（`runtime`/`wasm`/`wasi` 1.7.5）实测合计约 **424 KB**（151,620 + 213,420 + 59,497 B）。
- 官方 README 明说运行在 Android 上需 `minSdkVersion 26`（见 ③）。
- 即：**仅编译器本体就 ≥15MB 门**（最低的 2.0.0 已 15.9MB，最新 34.3MB），尚未计中文字体。

**路线 B：typst.ts 的 WASM + 自配运行时（Chicory / WebView）**

- npm `@myriaddreamin/typst-ts-web-compiler@0.7.0` 实测包内 `typst_ts_web_compiler_bg.wasm` =
  **28,325,178 B ≈ 27.0 MB**（npm tarball 实测，2026-10-03；包 unpackedSize 28.4 MB，wasm 占绝大部分）。
- 该包不含中文字体；WASM 路线无法读取 Android 系统字体（**假设，待验证**：Typst wasm 构建用自带
  font resolver，无系统字体搜索；若假设不成立体积结论也已被 ≥27MB 否决）。中文字体（如 Noto Sans CJK
  系列子集）需另行内置或下载，属**额外**体积/网络依赖。

**路线 C：NDK 交叉编译 libtypst 为 Android `.so`**

- **未找到**官方 Android 预编译产物或官方 NDK 构建说明（typst 官方仓库未提供 Android 目标；
  社区 typst.ts 仅有 Node 绑定 `typst-ts-node-compiler.android-arm64.node` = 52,682,792 B ≈ 50.2 MB，
  那是 Node 原生插件，不能直接当 Android JNI 库）。
- 4B 计划基线记录的「预计 +15–25MB」为**单一来源的经验估计，标待验证**；无一手构建产物可测，
  本路线在本文中不作为可承诺的数字，仅列为"若要做需先做构建可行性验证"。

**APK 增量口径说明**：jar/wasm 原大小 ≠ APK 增量（APK 会 deflate；wasm 本身已较难再压缩）。
本机未做 APK 对比实测（需要真机/产物对比的独立任务），因此**三条路线的 APK 增量均标 UNVERIFIED**；
但路线 A/B 的**下限**（15.9MB / 27.0MB 原大小）已足以判定不满足 ≤15MB 门，无需依赖压缩折算。

### ③ minSdk 26 与项目 23 的冲突 + 编译性能基准

- **冲突已证实**：java-typst README 原文 "Requires `minSdkVersion 26` (Android 8.0)."；
  项目 `minSdk = 23`。二选一：抬 minSdk（等于放弃 Android 6/7 设备，且与本项目 4A 以来的 23 基线冲突）
  或放弃该库。typst.ts + 自配 Chicory 理论上可保留 23（Chicory 为纯 JVM 库，README 未写 Android 最低 API；
  **单一来源，标待验证**——需实测 API 23 运行）。
- **编译性能**：**未找到** Typst WASM 在 Android 上的单题编译耗时基准。
  - Chicory 官方 README 自述定位：愿意"用性能换安全与简单"、"非目标：做最快的运行时"；
    AOT 编译器状态是 "Proof of concept"（来源：https://github.com/dylibso/chicory）。
  - java-typst 仓库含 `AotBenchmarkTest`（存在性来源：仓库文件列表），但无公开基准数字。
  - **假设（未验证）**：解释器路线单题文档需秒级～十秒级；而现有导出在仪器化中断言 60 题 < 5s，
    若 Typst 单题就秒级，会直接破坏"确认导出 → 秒出 PDF"的体验。此项必须在立项前用真机基准证伪/证实。
- 编译产物体积另见 ②（wasm 解析器 + 运行时上页，这里不重复）。

### ④ 与现有 MathBox 公式渲染的取舍

- **现状**：`CanvasMathBoxRenderer` 把（受限）LaTeX 解析为 `MathBox` 后直接画到 `PdfDocument` 画布，
  零依赖、JVM 可测的分页单元已把公式高度纳入分页；CJK 与数学字体全部复用系统字体。
- **Typst 的优势**：原生数学排版成熟（对齐、编号、分式/根式/矩阵的复杂排版远强于现有限定语法），
  模板与排版一体（不用自己维护分页/折行边界）。
- **代价**：引入**第二套数学语法**（Typst math vs 现有 LaTeX 子集）与第二个渲染引擎；现有公式用例/金样
  需全部重做对拍；字体资产与许可见 ①；体积/性能/API 见 ②③。
- **取舍结论**：一期收益不足以抵消"体积 ≥15MB + minSdk 26 冲突 + 性能无据 + 字体许可待审计"四项风险；
  **保留 Canvas MathBox**，公式排版继续走现有可测链路。

## 2. 结论门判定

| 门 | 判定 | 证据 |
|---|---|---|
| 许可允许商用嵌入 | ✅ 通过（Apache-2.0 全链） | ①的四条一手仓库元数据 |
| 体积增量 ≤ 15MB | ❌ **不通过** | java-typst 2.0.0 = 15.9MB（已超）/2.2.0 = 34.3MB；typst.ts wasm = 27.0MB |
| minSdk 兼容（项目 23） | ❌ 不通过（java-typst 需 26） | java-typst README |
| 编译性能有可信基准 | ❌ 未找到 | Chicory 自述不追求性能；无 Android 基准 |
| 与 MathBox 取舍 | 维持 MathBox（收益 < 代价） | ④ |

**最终结论：不立项 Typst 二期；维持现有参数化模板 + Canvas MathBox 路线。**

**复查条件（满足其一即重开研究）**：
1. 出现官方/第一方 Android 产物且既有体积实测 **≤15MB**（含中文渲染字体）；
2. 或 WASM 路线做到 minSdk ≤23 兼容（Chicory 实测可用）且真机基准证明单题文档编译 **≤数百 ms**；
3. 或产品明确接受"抬 minSdk 到 26 + 体积 >15MB"的取舍（需用户裁定，不属工程默认）。

## 3. 来源清单（URL）

- https://github.com/typst/typst —— Apache-2.0（GitHub API `license.spdx_id`；仓库 LICENSE）
- https://github.com/fatihcatalkaya/java-typst —— Chicory + WASM；README "Requires minSdkVersion 26 (Android 8.0)"；Apache-2.0
- https://github.com/dylibso/chicory —— "Chicory is a JVM native WebAssembly runtime"；"non-goal: Be the fastest runtime"；Apache-2.0
- https://github.com/Myriad-Dreamin/typst.ts —— 社区 WASM 构建；Apache-2.0
- https://repo1.maven.org/maven2/io/github/fatihcatalkaya/java-typst/2.2.0/java-typst-2.2.0.jar （HEAD `Content-Length: 35981116`，2026-10-03）
- https://repo1.maven.org/maven2/io/github/fatihcatalkaya/java-typst/2.0.0/java-typst-2.0.0.jar （HEAD `Content-Length: 16646804`，2026-10-03）
- https://repo1.maven.org/maven2/com/dylibso/chicory/{runtime,wasm,wasi}/1.7.5/ （HEAD：151620 / 213420 / 59497 B，2026-10-03）
- https://registry.npmjs.org/@myriaddreamin/typst-ts-web-compiler/-/typst-ts-web-compiler-0.7.0.tgz （包内 wasm 28,325,178 B，2026-10-03）

## 4. UNVERIFIED / 待验证（如实登记）

1. 三条路线的 **APK 增量**均未实测（只有产物原大小）；结论不依赖压缩折算，已按下限否决。
2. 中文字体必须自备、以及 WASM 不能读 Android 系统字体：**假设**（未做一手验证）；即使不成立，
   体积门已被 ≥15.9MB / ≥27MB 否决。
3. java-typst 2.2.0 jar 中 wasm 与 Java 类的占比未拆分（未解包），只测了整包大小。
4. NDK 路线"无官方产物"基于官方仓库检索未发现；"+15–25MB"为单一来源经验估计 → 待验证。
5. 字体资产许可（Typst 内置字体清单 + 中文字体）未逐项审计 → 待验证（若二期则前置）。
6. Typst WASM 单题编译耗时在 Android 上的基准：未找到（不是"不存在"，是本次未取得可信数据）。
