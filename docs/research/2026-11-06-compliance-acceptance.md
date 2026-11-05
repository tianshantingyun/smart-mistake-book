# R6 合规验收：AIGC 标识（办法 + GB 45438—2025）、隐私政策/data safety、青少年模式、导出水印

- 日期：2026-11-06（批次 44 · 计划 §2 H 域「合规与文案」输入件）
- 性质：**只研究 + 只读核查 + 可执行检查**；不改任何产品代码、不改热区、不落新依赖
- 用途：把 §2 H 域的合规面拆成**可指认检查项**（每项：义务来源 / 现状 / 判定 / 可执行方法 / 证据类别），供验收台账消费
- 边界：本文不构成法律意见；AIGC 义务主体判定沿用 R3 的"公开分发即构成"保守口径（`docs/research/2026-10-25-ai-content-labeling-compliance.md` §2.1）

---

## 0. 结论摘要（四题各一行）

| 题 | 一句话结论 | 判定口径 |
|---|---|---|
| AIGC 显式标识 | **导出侧已落（PDF/DOCX 逐图图注）**；**交互界面侧零落地**（讲题回复、App 内生成图展示均无标识）；用户说明文案只存在于 `docs/`，未接入 App | 12 项：已实现 6 / 未实现 5 / BLOCKED 1 |
| AIGC 隐式标识 | **图片** EXIF 四字段 + TC260 XMP 三字段在码；**PDF** `/AIGC` 7 键注入在码且**本轮由 pypdf 6.16.2 独立读回真实产物**；**DOCX 无文档级载体**；两条已知漏标路径（KD-85 模型改卷文本、KD-84 部分页打印）保持 open | 16 项：已实现 11 / 未实现 3 / BLOCKED 2 |
| 隐私政策 / data safety | **应用内无隐私政策入口**（`数据与隐私` 屏只有两段说明）；data safety 草稿存在但**口径与 Play 冲突**（写"不收集"，而 BYOK 明确把内容传出设备）；技术面（无第三方 SDK、HTTPS-only、`allowBackup=false`、遥测默认关且未接线）**是干净的** | 12 项：已实现 6 / 未实现 4 / BLOCKED 1 / 未验证 1 |
| 青少年模式 | **不构成《未成年人网络保护条例》第 43 条形定的义务缺项**（该条枚举网络游戏/直播/音视频/社交，本产品均不属）；《建设指南》app 侧能力（联动/时长/家长管理）**零落地**，属指导性缺项；14 岁以下监护人同意**无机制**（触发面随账号/遥测引入而放大） | 6 项：未实现 4 / 不适用 2 |
| 导出水印（可核清单） | 可核清单 10 条，其中 **4 条本轮有实测产物证据**，3 条只有代码/用例（**未跑**），1 条纯代码事实，2 条已知缺口/BLOCKED | 见 §6 |

**总体：本仓在"导出文件带 AI 标识"这一条上已具备可核证据链；在"用户看得见的标识""隐私政策入口""data safety 填表准确性"三处是明确未实现；GB 45438 具体尺寸口径仍因标准原文不可得而 BLOCKED。**

### 0.1 本轮实际执行的检查（不是转录，命令与输出逐字见 §8）

| # | 检查 | 命令要点 | 结果 |
|---|---|---|---|
| D-1 | 打标产物 `/AIGC` 存在性与 7 键结构 | `python -I` + `pypdf 6.16.2` 读 `build/device-track-batch42/ws2-pdf/real-export-labeled.pdf` | `Label=1`、`ContentProducer=SmartMistakeBook`、`ProduceID` 64 hex、7 键齐、`/Producer` 保留 |
| D-2 | 纯用户内容**不**被打标（"不伪报"负向对照） | 同上读 `pure-user-unlabeled.pdf` | 无 `/AIGC`、仅 `/Producer`、`%%EOF`=1 |
| D-3 | 注入只追加不改原字节 | `bl.startswith(bs)` on `pdfdocument-chinese-source/labeled` | **True**，追加 393 B |
| D-4 | 二次注入幂等 | sha256(`classic-table-labeled`) vs sha256(`classic-table-twice`) | **完全相同**（`ab76ae18f0353cbb…`），`/AIGC` 计数 1 |
| D-5 | 交互界面显式标识存在性 | `grep -rn "AI 生成\|AI生成\|内容由AI\|人工智能生成" app/src/main/kotlin core/ui/src feature \| wc -l` | **0** |
| D-6 | 应用内隐私政策入口 | `grep -rn "privacy\|Privacy" app/src/main core/ui/src`（排除 `DataPrivacyScreen`/文档名） | 仅路由常量与宿主接线，**无政策正文/链接** |
| D-7 | 青少年模式/时长管理能力 | `grep -rn "青少年\|未成年\|防沉迷\|使用时长\|休息提醒" app/src/main/kotlin core/ui/src feature` | **0** |
| D-8 | 第三方分析 SDK | `grep -in "firebase\|crashlytics\|analytics\|sentry\|umeng\|admob" gradle/libs.versions.toml app/build.gradle.kts core/*/build.gradle.kts` | **0 命中** |
| D-9 | 图片 XMP 端到端产物 | `grep -rl "TC260:AIGC" --include=*.png --include=*.jpg build .artifacts` | **0 命中**（无真实 Provider → 无生成图产物） |

**本轮未执行**：任何 Gradle 命令（纪律：Gradle 只在门禁阶段串行）、任何 adb/设备命令、任何真实 Provider 调用、任何人工金标比对。因此**本文没有任何一项可以标 PASS**——下文"已实现"只表示"载体在树且可指认"，不等于通过。

---

## 1. 判定规则与证据等级（本文自用，供台账复用）

### 1.1 证据等级

| 级 | 含义 | 本文来源 |
|---|---|---|
| **A** | 法规/国标/官方答问原文（本轮抓取） | §7 表，`cac.gov.cn` |
| **B** | 官方技术文档（本轮或前批抓取） | Play 政策（本轮通道不可达，见 §7.4）；Android 文档（前批） |
| **C** | 源码事实（`file:line`，本轮实读） | 各表"落点"列 |
| **D** | 产物实测（本轮亲跑命令 + 输出） | §0.1 D-1…D-9、§8 |
| **E** | 前批证据文件/日志（**非本轮运行**，只能指向，不能算本轮通过） | `docs/research/evidence/**`、`build/*.log` |
| **F** | 未验证/单一来源 | 各表"待验证"列 |

### 1.2 PASS 判定（写死，防止"看起来对"）

一条检查要判 **PASS** 必须同时满足：

1. 有 **≥2 类互相独立**的证据，且其中至少 1 类是 **C（源码）** 或 **D（本轮实跑）**；
2. **E 类单独出现不得判 PASS**（前批日志、他人证据文件、`OK (n tests)` 摘要都不算）；
3. `docs/` 里的文案、声明、草稿**不是**行为证据——只能证明"文字存在"，不能证明"功能存在"（本文全部 UI 类结论都用 grep 计数而非文档自述）；
4. 只在**当前树**上取得的行为证据才有效；产物必须绑定"哪份文件、什么时候、哪个 sha"。

按此规则，本文 §2–§6 的"已实现"多为 **C 类单类**（源码在树、本轮未跑测试/设备），因此它们的正确终裁是 **未验证（待复跑）**，不是 PASS。这是刻意的：本轮是研究工作流，门禁在 §4 阶段串行跑。

---

## 2. AIGC 显式标识（《办法》第三条/第四条）

**义务原文（A，本轮抓取，见 §7.1）**：显式标识是"以文字、声音、图形等方式呈现并可以被用户明显感知到的标识"；提供**下载、复制、导出**等功能时，"应当确保文件中含有满足要求的显式标识"。

| # | 检查项 | 义务 | 现状（C 事实） | 判定 | 可执行验收方法 | 证据类别 |
|---|---|---|---|---|---|---|
| **A-1** | 讲题对话中 AI 回复的用户可见提示（常驻提示条或每条回复尾部标注） | 第四条"交互场景界面" | **无**。全仓 UI 源码 `AI 生成` 类字样 **0 命中**（D-5）；`feature/tutor` 无任何披露组件 | **未实现** | `grep -rn "AI 生成\|内容由 AI" app/src/main/kotlin core/ui/src feature` 必须 ≥1 命中且落在 tutor 回复渲染路径；再用 `adb exec-out screencap` + 像素/文本断言（UIAutomator `android_ui_describe` 找文本节点）双证 | C + D（待补） |
| **A-2** | App 内 AI 生成图角标（`AttachedImageCard` READY 态） | 第四条"图片适当位置显著提示" | **无**。同上 0 命中 | **未实现** | 同上 grep + 设备截图；判据：图卡节点树内出现含 `AI` 与 `生成` 两要素的文本节点 | C + D（待补） |
| **A-3** | PDF 导出：每张 AI 图下固定图注 | 第四条第二款（导出文件） | **已实现**。`AiGeneratedImageCaption.kt:27` 固定文案 `本图由 AI 生成`；判据 `:30-37`（`null`/`CLEAN_IMAGE`/`TUTOR_EXPLANATION` 为真）；渲染 3 处 `MistakePdfPagePlanner.kt:148-149`、`:162-163`、`:412-413` | **已实现（待复跑）** | `./gradlew :core:export:testDebugUnitTest --tests "*AiGeneratedImageCaptionTest"`（6 例）+ `*MistakeExportVisualRoleTest`（断言 `line/META/16.00/false/0/本图由 AI 生成`，`MistakeExportVisualRoleTest.kt:204-206`） | C + E（用例在树未跑） |
| **A-4** | DOCX 导出：每张 AI 图下固定图注 | 同上 | **已实现**。`DocxMistakeRenderer.kt:237-239`、`:294-296`、`:329-331` 三处调用同一常量与同一判据 | **已实现（待复跑）** | `./gradlew :core:export:testDebugUnitTest --tests "*AiGeneratedImageCaptionTest"`；DOCX 侧由 `MistakeExportVisualRoleTest` 覆盖 | C + E |
| **A-5** | "不伪报"：题目自带图/OCR 转录/程序化 JS 图不得标成 AI 生成 | 第十条（不得伪造标识） | **已实现**。`QUESTION_DIAGRAM` 明确返回 false（`AiGeneratedImageCaption.kt:17-21,30-37`）；PDF 侧有独立负向产物对照（**D-2**：纯用户内容 PDF 无 `/AIGC`） | **已实现**,证据较强 | 负向产物：`pure-user-unlabeled.pdf` 无 `/AIGC`（本轮 D-2）；用例 `MistakeExportVisualRoleTest.kt:204-206` 与 `AiGeneratedImageCaptionTest.kt:63`（"题目自带示意图不能被伪报成 AI 生成"） | C + D + E |
| **A-6** | 标识文案要素：须同时含"AI/人工智能"与"生成/合成" | GB 45438（要素口径，**尺寸/位置口径待验证**） | **已实现**：`本图由 AI 生成` 两要素齐 | **已实现**（要素面）；**尺寸/位置面待验证** | 断言常量包含 `AI` 与 `生成`（用例在树）；GB 尺寸口径见 §7.3 BLOCKED | C；A（办法第四条）；F（GB 细则） |
| **A-7** | 用户说明/服务协议中说明标识方法与样式 | 第八条 | **部分**。文案齐备：`docs/ai-content-disclosure-copy.md` §1–§3 三档；**但未接入 App**：无"关于"页、无首启弹窗（该文件 §6 自述接线批未落） | **未实现（接线）** | `grep -rn "AI 生成内容说明\|关于" app/src/main/kotlin` 应有命中 + 设备导航可达截图；当前为 0 | C（负向）+ 文档 |
| **A-8** | 显式标识不提供关闭/去除入口 | 第四条义务性 + 第十条 | **已实现**：无任何开关；`availableMethods`/设置面均无去标项 | **已实现（待复跑）** | `grep -rn "关闭标识\|去除标识\|removeLabel" app/src/main/kotlin core` 应 0 命中（当前 0）；配合设置面截图 | C |
| **A-9** | PDF 中**模型解析文本**（非图）的显式标识 | 第四条第二款 + 第二条 | **未实现**：`hasModelParsedText` 只驱动**隐式** `/AIGC`（`MistakePdfExporter.kt:238-251`），导出卷面**无任何文字标识**说明该题面文本由模型解析 | **未实现（新发现，本文登记）** | 造一份"仅模型解析文本、无 AI 图"的导出：`PdfAigcReader` 应有 `/AIGC`，同时用 pypdf 抽文本应找不到 `AI 生成` 字样 → 双断言即复现缺口 | C + D（D 需设备导出） |
| **A-10** | 生成图**图片内**水印（非图注）满足 GB 尺寸规格 | 第四条 + GB 45438 | 产品未做图片内水印，只有图注 | **BLOCKED**（口径待标准原文） | 取得 GB 45438 原文后按附录核对；未取得前**不得**把"图注"写成"满足 GB 尺寸要求" | F |
| **A-11** | 隐式标识与显式标识**同时**出现在同一导出文件 | 第四/五条 | **已实现**：同一份 PDF 既有 `/AIGC`（D-1）也有逐图图注（A-3） | **已实现（待复跑）** | D-1 + A-3 两条证据组合；或设备导出后用 pypdf 读 `/AIGC` + 文本层 grep | C + D |
| **A-12** | 商店侧材料：AI 功能说明 + 标识样例 + 元数据说明 | 第七条 | **未实现**：`docs/research/2026-10-25-store-launch-materials-checklist.md` 第 5 项列出所需材料，仓库内无成型材料包 | **未实现** | 交付物检查：`docs/` 下存在三件套（功能说明/显式样例截图/隐式说明）且被商店提交清单引用 | C（负向） |

---

## 3. AIGC 隐式标识（《办法》第五条 + 第十一条 → GB 45438—2025）

**义务原文（A）**：在**文件元数据**中添加"生成合成内容属性信息、服务提供者名称或者编码、内容编号等制作要素信息"；第十一条要求标识活动同时符合**强制性国家标准**（即 GB 45438—2025）。

### 3.1 图片路径（PNG/JPEG）

| # | 检查项 | 现状 | 判定 | 可执行方法 | 证据 |
|---|---|---|---|---|---|
| **B-1** | EXIF 四字段（属性/提供者/编号/机器可读行） | **已实现**：`GeneratedFigureMetadata.kt:81-96` 写 `ImageDescription="AI 生成"`、`Software="SmartMistakeBook;model=…"`、`Artist=figureId`、`UserComment="AI-GENERATED;kind=…"` | 已实现（待复跑） | `GeneratedFigureMetadataInstrumentedTest`（4 例，设备） | C + E |
| **B-2** | TC260 XMP 三字段（`Label`/`ContentProducer`/`ProduceID`） | **已实现**：`:94` 同一次 `saveAttributes` 写 `TAG_XMP`；包体 `:185-193`；JSON `:176-179`；命名空间 `:154` | 已实现（待复跑） | `GeneratedFigureAigcXmpTest`（9 例，JVM，纯函数） | C + E |
| **B-3** | 写不进标识即生成失败（fail-closed，不放无标识图入库） | **已实现**：`:81-96` 抛异常；写入点 `AndroidCanonicalAssetVault.kt:373` 在落盘路径 | 已实现（待复跑） | 设备用例 `GeneratedFigureMetadataInstrumentedTest` + 代码路径确认 | C + E |
| **B-4** | 读回 fail-closed（别家 XMP/伪 tag/`Label≠1` 不算） | **已实现**：`:212-248` 要求最近 `rdf:Description` 作用域声明精确 URI；`isAigcAiGeneratedLabel` 只认 `"1"` | 已实现（待复跑） | 同上 JVM 用例组 | C + E |
| **B-5** | **端到端产物**：真实生成图文件里能读出 XMP | **未取得**：`build`/`.artifacts` 下 `TC260:AIGC` **0 命中**（D-9）——无真实 Provider，仓库里没有生成图产物 | **BLOCKED** | 需真实 Provider 生成一张图 → `exiftool`/`exifinterface` 读 `XMP-TC260:AIGC` | D（负向）+ 外部条件 |
| **B-6** | XMP 备份范围不等于 EXIF（XMP-only 时 model/purpose 为 null） | **已实现并为文档化边界**：`:34-38`（类 KDoc "备份范围"）与 `:118-147` 解析优先级 | 已实现（设计事实） | JVM 用例断言 XMP-only 时 `model==null` | C + E |

### 3.2 PDF 路径（文档级 `/AIGC`）

| # | 检查项 | 现状 | 判定 | 可执行方法 | 证据 |
|---|---|---|---|---|---|
| **B-7** | Info 字典 `/AIGC` 为**7 键**、`Label="1"`、`ProduceID`=输入指纹 | **已实现**：`PdfAigcSchema.kt:29-36`（字段）、`:61-74`（写前校验）、`:99` 长度上限、`:108` 文档级工厂 | **已实现，且本轮有产物实证** | D-1：pypdf 读回 7 键 + `ProduceID` 64 hex | **C + D** |
| **B-8** | 只追加修订，不改原字节 | **已实现**：`PdfAigcInjector.kt:16-22`（原文件 + 副本 + xref 段 + `%%EOF`） | **已实现 + 本轮实证** | D-3：`labeled.startswith(source)==True`，+393 B；`%%EOF`=2、`/Prev` 存在 | **C + D** |
| **B-9** | 幂等（同值二次注入字节不变） | **已实现**：`:55-56` 同值返回原字节 | **已实现 + 本轮实证** | D-4：`labeled` 与 `twice` sha256 完全相同；`/AIGC` 计数 1 | **C + D** |
| **B-10** | 判据覆盖"模型解析文本"（KD-75 C2） | **已实现**：`PdfAigcContentPolicy.kt:40-47`；`MistakePdfEligibility.kt:157-158` 从 `blockEvidence` 派生 → `:190` → `MistakePdfExportInput.hasModelParsedText:365` | 已实现（待复跑） | `PdfAigcContentPolicyTest`（7 例）+ `MistakePdfExporterAigcJvmTest`（11 例） | C + E |
| **B-11** | 判据覆盖"自然语言改卷"产生的模型文本（KD-85） | **未实现**：`MistakePdfPaperEdit.toDraft()` 只透传来源 `hasModelParsedText`（`docs/known-defects.md:3318-3326`），纯用户题被模型改写后仍判 false | **未实现（open，高）** | 造"纯用户来源 + 模型改卷"导出 → pypdf 应**无** `/AIGC` 即复现；修复后应有 | C（缺陷台账）+ D（待造） |
| **B-12** | 部分页打印/另存保留文档级标识（KD-84） | **未实现**：子集产物用新 `PdfDocument` 逐页重绘（`MistakePdfDelivery.kt:146`、`:215`），Info 不随页复制 | **未实现（open）** | 设备导出 → 选部分页打印 → pypdf 读子集产物应无 `/AIGC` | C + D（待造） |
| **B-13** | DOCX 文档级隐式标识 | **未实现**：`MinimalDocxWriter.kt:47-54` 只写 `[Content_Types].xml`/`_rels/.rels`/`word/document.xml`，**无 `docProps/core.xml`**；KD-75 关闭声明已把 DOCX 排除在外（批次 43 记录 §9.7） | **未实现（需裁定：DOCX 是否属义务面）** | 解压 DOCX 断言是否存在承载 AIGC 的元数据部件；当前必然不存在 | C |
| **B-14** | TC260 命名空间 URI 与字段名符合官方原文 | **待验证**：`AIGC_NAMESPACE_URI` 为单一来源（`GeneratedFigureMetadata.kt:150-154` 自述 R2 §5-U2） | **BLOCKED（标准/指南原文）** | 取得 TC260 指南原文后逐字比对；差一字只改该常量 | F |
| **B-15** | 标识代际与缓存键同步（改判据必须换代际） | **已实现**：`PdfAigcContentPolicy.GENERATION=2`（`:30`）→ `MistakePdfExporter.AIGC_POLICY_SENTINEL`（`:201-202`）→ 三方一致用例 | 已实现（待复跑） | `MistakePdfExporterAigcJvmTest` 的"三方一致"用例 | C + E |
| **B-16** | 注入坏文件时 fail-closed（不产出半成品） | **已实现**：`MistakePdfExporter.kt:238-258` 捕获 `PdfAigcInjectionException` → `GENERATED_PDF_INVALID`；pending 目录 finally 删除 | 已实现（待复跑） | `PdfAigcMalformedTest`（22 例）+ JVM fail-closed 用例 | C + E |

---

## 4. 隐私政策 / data safety

| # | 检查项 | 现状 | 判定 | 可执行方法 | 证据 |
|---|---|---|---|---|---|
| **C-1** | 应用内可访问的隐私政策（Play 明确要求"store listing **and within your app**"） | **未实现**：`SecondaryScreens.kt:586-612` 的"数据与隐私"屏只有"本机数据清单/智能服务"两段说明，**无政策正文、无链接、无版本号、无生效日期**；全仓 UI 中 `privacy` 只出现在路由常量与文档名（D-6） | **未实现** | `grep -rn "隐私政策" app/src/main core/ui/src` 应 ≥1 且能从设置导航到；再设备截图 | C（负向） |
| **C-2** | 商店页隐私政策公开 URL（HTTPS、非 PDF、无需登录） | **未实现/BLOCKED**：仓库内无公开 URL；未上架 | **BLOCKED** | 上架前提供 URL；`curl -I` 校验 200 + `content-type: text/html` | 外部条件 |
| **C-3** | 政策要件覆盖：权限清单、SDK 清单、数据出境（BYOK）、未成年人专章、删除路径 | **未实现**：`docs/current/privacy-and-data-safety.md`（41 行英文）与 `PRIVACY-DATA-FLOW.md`（22 行）只有原则陈述，**无权限清单表、无 SDK 清单、无未成年人专章**；`docs/current` 属热区，不得在本批改 | **未实现（热区受限）** | 交付后逐条勾选：权限（2 项）、SDK（0 项）、出境（BYOK）、未成年人、删除 | C（文档） |
| **C-4** | data safety 表单答案准确（含"传到设备外"必须披露） | **未实现（草稿口径冲突）**：`privacy-and-data-safety.md:35-36` 写 "Data collected: none by the app itself"，而 BYOK 请求确实把题面/消息/授权图传到用户配置的端点（`PRIVACY-DATA-FLOW.md:7-9`）；Play 口径要求披露 "transmitting data off a user's device"（前批引用，见 §7.4） | **未实现** | 拿 §7.4 的问题清单逐条对答；答案必须与 `PRIVACY-DATA-FLOW.md` 逐项对得上 | C + B（前批） |
| **C-5** | 无第三方分析/崩溃 SDK | **已实现**：依赖目录与模块构建脚本 0 命中（D-8） | 已实现 | 同 D-8 命令，另跑 `./gradlew :app:dependencies` 人工过一遍 | C + D |
| **C-6** | 权限面最小：仅 2 项、无高危/受限权限 | **已实现**：`AndroidManifest.xml:4-5` 仅 `POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED` | 已实现 | 同文件 + `tools/ci/check_release_manifest.py`（既有门） | C |
| **C-7** | 传输安全：HTTPS-only、拒内嵌凭据、DNS 钉扎、拒绝非公网地址 | **已实现**：`OpenAiModelTransport.kt:216-226`（scheme≠https 即抛；解析后全部地址必须 publicly routable）、`:228-236`（`FixedDns` 只认已批准 host） | 已实现（待复跑） | `OpenAiModelTransport` 相关 mock server 用例 | C + E |
| **C-8** | `allowBackup=false` + 禁明文流量 | **已实现**：`AndroidManifest.xml:12`、`:20` | 已实现 | 同文件 + `tools/ci/check_release_manifest.py` | C |
| **C-9** | "删除全部数据"可用且需显式确认 | **已实现**：`SecondaryScreens.kt:833-872`（确认对话框 → `backupRepository.deleteAllData()`）；入口 `:981` | 已实现（待复跑） | 设备用例 + 截图；删除后 `adb shell run-as` 看不到数据库 | C |
| **C-10** | 遥测默认关闭且未接线（"同意前 0 字节"） | **已实现**：`TelemetryConsent.kt:47` `telemetryBuildEnabled=false`；`TelemetrySignals.kt:55` 明确 v1 无上传通道；app 主码 0 处使用 `TelemetryConsentStore`（仅测试） | 已实现 | `grep -rn TelemetryConsentStore app/src/main` 应 0；`TelemetryConsentStoreInstrumentedTest` 断言未同意时不写 | C + E |
| **C-11** | 备份不含凭据/租约 | **声明在树**（`PRIVACY-DATA-FLOW.md:20-22`），**本轮未复核实现** | **未验证** | 跑既有备份门 `python -I tools/ci/check_backup_plan_sync.py` + 备份往返设备用例 + `unzip -l` 查包内文件 | E（既有门） |
| **C-12** | 未成年人个人信息：14 岁以下监护人同意机制 | **未实现**：全仓无年龄采集/监护人同意 UI（D-7 同批 grep 为 0；`TelemetryGate.ageGateSatisfied` 只在遥测模块内被调用，未接线） | **未实现**（触发面：一旦引入账号/遥测/云端存档即成硬义务） | `grep -rn "guardianConfirmed" app/src/main` 应 0（当前 0） | C |

---

## 5. 青少年模式

### 5.1 适用性先判（A 类证据，避免把"不适用"写成"缺项"）

- **《未成年人网络保护条例》第四十三条**（本轮抓取，§7.2）：设置未成年人模式的义务，主体被**枚举限定**为"网络游戏、网络直播、网络音视频、网络社交等服务提供者"。本产品是**本地错题本工具**，不含上述任一类服务 → **第 43 条不直接适用**。
- **第二十条第 1 款第 2 项**：对"未成年人用户数量巨大或者具有显著影响"的平台要求未成年人模式/专区 → 本产品当前不满足该量级，**不适用**（触发条件：用户规模或影响力变化）。
- **《移动互联网未成年人模式建设指南》（2024-11-15）**：适用范围含"移动互联网应用程序"，但通篇是**指导性**（"应当/推荐"混用，无罚则），且绝大多数条款面向有**内容池/推荐流/社交**的应用。本产品无内容流、无社交、无广告 → 相关条款多为**不适用**，剩余能力（联动、时长、家长管理）属**指导性缺项**。

| # | 检查项 | 义务来源 | 现状 | 判定 | 可执行方法 |
|---|---|---|---|---|---|
| **D-1** | 未成年人模式联动（随系统/终端一键切换） | 指南三（一）三方联动 | 无任何未成年人模式概念 | **未实现（指导性）** | `grep -rn "未成年人模式\|minorMode" app/src/main/kotlin` = 0（当前 0）；若实现则需读取系统设置并切换 UI 态 |
| **D-2** | 使用时段/时长管理 + 连续使用提醒 | 指南三、五（三）；条例 43（对本产品不适用） | **无**（D-7：`使用时长/休息提醒/防沉迷` 全 0） | **未实现（指导性）** | grep 同上；若实现需有可配置时长与到点提醒的设备证据 |
| **D-3** | 家长管理（时长/安装/内容类型可管，退出需家长验证） | 指南七 | 无 | **未实现（指导性）** | 需要家长验证组件的设备走查；当前无载体 |
| **D-4** | 分龄适龄内容池 | 指南五（一） | **不适用**：本产品无推荐内容流（用户自建错题） | **不适用** | 说明性判断；如未来加入推荐/内容广场则重评 |
| **D-5** | 默认关闭陌生人私信 | 指南五（三） | **天然满足**：无社交功能 | **不适用/已满足** | `grep -rn "私信\|chat\|message" app/src/main/kotlin` 只应命中本地提示，不应有收发通道 |
| **D-6** | 面向未成年人的主动说明（适用人群/使用建议） | 产品主动（非《办法》法定义务，R3 F2） | **部分**：文案在 `docs/ai-content-disclosure-copy.md` §2/§3 第 5 条，**未接入 UI** | **未实现（接线）** | 与 A-7 同一落点 |

> **口径提醒**：把 D-1…D-3 写成"违法"是错的；把它们写成"不需要做"也是错的（上架材料清单第 12 项按商店要求需准备适龄说明）。正确表述：**法定义务不触发，指导性能力缺位，商店材料待备。**

---

## 6. 导出水印：可核清单（10 条，逐条可判定）

> 定义：本文"导出水印"= 导出文件里关于"AI 生成"的**所有可见与不可见标记**（逐图图注 + 文档级 `/AIGC` + 部分页/打印产物），不含用户身份水印（产品无此功能，也未评估）。

| # | 可核命题 | 判定方法（命令/断言） | 现状 |
|---|---|---|---|
| **W-1** | 含 AI 图的 PDF 每张图下有且仅有一行 `本图由 AI 生成` | 设备导出 → `pdftotext`/pypdf 抽文本断言计数 == AI 图数；JVM 侧 `MistakeExportVisualRoleTest.kt:204-206` | 用例在树，**本轮未跑** |
| **W-2** | 含 AI 图的 DOCX 每张图下有同一行文案 | 解压 `word/document.xml` grep 文案计数 == AI 图数 | 用例在树，未跑 |
| **W-3** | 图注**不参与**导出指纹（同输入换渲染不换缓存键） | `MistakePdfExportInput` 无图注字段（`AiGeneratedImageCaption.kt:23-25`）→ 断言 `inputSha256` 构造不含文案 | 代码事实成立 |
| **W-4** | 文档级 `/AIGC` 存在且 7 键合法 | **本轮实测通过**：pypdf 读 `real-export-labeled.pdf` → 7 键、`Label=1`、`ProduceID` 64 hex（D-1） | **有 D 类证据** |
| **W-5** | 纯用户内容导出**不带** `/AIGC`（不伪报） | **本轮实测通过**：`pure-user-unlabeled.pdf` 无 `/AIGC`（D-2） | **有 D 类证据** |
| **W-6** | 打标只追加、原文件字节是输出前缀 | **本轮实测通过**：`startswith==True`，+393 B（D-3） | **有 D 类证据** |
| **W-7** | 重复打标幂等 | **本轮实测通过**：`labeled` == `twice`（sha 相同，D-4） | **有 D 类证据** |
| **W-8** | 部分页打印/另存的产物保留标识 | pypdf 读子集产物 → 当前**必然无** `/AIGC`（KD-84，`MistakePdfDelivery.kt:146,215`） | **已知缺口（FAIL）** |
| **W-9** | 导出的 PDF 仍是合法 PDF（注入后设备能打开） | `PdfRenderer(descriptor)` 打开并数页（`MistakePdfExporter.kt:173-175` 路径）；设备用例 `MistakePdfExporterAigcInstrumentedTest`（7 例） | 用例在树，未跑 |
| **W-10** | 图片内水印尺寸/位置满足 GB 45438 | 需标准原文附录；当前产品用图注而非图内水印 | **BLOCKED**（口径未取得，见 §7.3） |

**W-4…W-7 的复跑方式（任何人可重放）**：

```bash
cd "D:/smart mistake book"
python -I - <<'PY'
import hashlib, json, re
from pypdf import PdfReader
for p in ["build/device-track-batch42/ws2-pdf/real-export-labeled.pdf",
          "build/device-track-batch42/ws2-pdf/pure-user-unlabeled.pdf"]:
    r = PdfReader(p); info = r.metadata or {}
    print(p, "| /AIGC:", "/AIGC" in info, "| keys:", list(info.keys()))
    if "/AIGC" in info: print("  json:", json.loads(info["/AIGC"]))
s="build/device-track-batch41/pdf/device-evidence/pdfdocument-chinese-source.pdf"
l="build/device-track-batch41/pdf/device-evidence/pdfdocument-chinese-labeled.pdf"
bs, bl = open(s,'rb').read(), open(l,'rb').read()
print("append-only:", bl.startswith(bs), "| appended bytes:", len(bl)-len(bs))
h=lambda p: hashlib.sha256(open(p,'rb').read()).hexdigest()
print("idempotent:", h("build/device-track-batch41/pdf/device-evidence/classic-table-labeled.pdf")==
                       h("build/device-track-batch41/pdf/device-evidence/classic-table-twice.pdf"))
PY
```

> **产物身份边界（必须写明）**：上述 PDF 是**前批设备轨的盘上产物**（mtime 2026-11-03 15:21），不是本轮生成；它们证明"注入器在**那棵树上**的行为"，不证明当前树的二进制行为。pypdf 对源件已报 `incorrect startxref pointer(1)` 与字体解压告警（**源件本身如此**，非注入引入），且页面文本抽取为空（字体无 ToUnicode），因此**内容不变**只能用"前缀字节相等"证明，不能用文本抽取证明。

---

## 7. 法规与官方来源核对（本轮抓取；采纳/拒绝理由）

### 7.1 《人工智能生成合成内容标识办法》（A，本轮 WebFetch 全文）
`https://www.cac.gov.cn/2025-03/14/c_1743654684782215.htm` → **采纳**。

| 条 | 原文要点（本轮抓取） | 本文用法 |
|---|---|---|
| 第三条 | 显式标识=「以文字、声音、图形等方式呈现并可以被用户明显感知到的标识」；隐式标识=「采取技术措施在生成合成内容文件数据中添加的，不易被用户明显感知到的标识」 | A-1/A-2 的"用户可感知"判据；B 组区分显/隐 |
| 第四条 | 下载/复制/导出时「应当确保文件中含有满足要求的显式标识」 | A-3/A-4/A-9 的直接义务 |
| 第五条 | 元数据须含「生成合成内容属性信息、服务提供者名称或者编码、内容编号等制作要素信息」 | B-1/B-2/B-7 的要素表 |
| 第八条 | 「应当在用户服务协议中明确说明生成合成内容标识的方法、样式等规范内容」 | A-7 |
| 第十条 | 不得「恶意删除、篡改、伪造、隐匿」标识，也不得为他人提供此类工具 | A-5 不伪报 / A-8 无开关 |
| 第十一条 | 还须符合「相关法律、行政法规、部门规章和强制性国家标准」 | 引出 GB 45438 |
| 施行 | 2025-09-01 | 义务已生效（beta 未公开分发是义务触发面问题，不是时间问题） |

答记者问 `https://www.cac.gov.cn/2025-03/14/c_1743654685896173.htm` → **采纳（有限）**：它只概括"对文本、图片、音频、视频、虚拟场景添加文字、角标、语音、节奏等显式标识"，并重申导出/下载要求；**没有**逐类媒体的位置/尺寸数值 → **不能**用它替代 GB 45438 细则。

### 7.2 未成年人相关（A，本轮抓取）
- 《未成年人网络保护条例》`https://www.cac.gov.cn/2023-10/24/c_1699806932316206.htm` → **采纳**：第 43 条未成年人模式义务**枚举**为"网络游戏、网络直播、网络音视频、网络社交"；第 20 条第 1 款第 2 项针对"用户数量巨大或具有显著影响"的平台。→ 本文 §5.1 的"不直接适用"结论即由此条得出。
- 《移动互联网未成年人模式建设指南》`https://www.cac.gov.cn/2024-11/15/c_1733364304749288.htm` → **采纳（作为指导性依据）**：其"二、适用范围"覆盖"移动互联网应用程序"，但条文结构以"五（一）分龄内容池""五（三）功能限制""七、家长管理"为主，时长数字（<16 岁 1 小时、16–18 岁 2 小时、连续 30 分钟提醒、22:00–06:00 不服务）写在**终端**节且"均可家长豁免"；**未见**对应用"默认开启"的明文。→ 因此本文把 D-1…D-3 标"指导性缺项"而非"违法"。
- 《个人信息保护法》第三十一条 `https://www.cac.gov.cn/2021-08/20/c_1631050028355286.htm` → **采纳（逐字）**：「处理不满十四周岁未成年人个人信息的，应当取得未成年人的父母或者其他监护人的同意。…应当制定专门的个人信息处理规则。」→ C-12 的依据。

### 7.3 GB 45438—2025（**未能取得原文 → BLOCKED**）
- 元信息核对（本轮 WebFetch `openstd.samr.gov.cn` 详情页）：**现行**、发布 **2025-02-28**、实施 **2025-09-01**；页面提示「当前浏览器暂不支持标准全文在线预览服务」，无可用全文/PDF 下载通道。
- 网信办专家解读 `https://www.cac.gov.cn/2025-09/05/c_1758792061408012.htm`（本轮抓取）：只说明国标给出"显式标识的时长、尺寸，隐式标识的包含要素"等可操作方案，**不含任何具体数值或字段名**。
- 检索通道状态：`mcp__searchd__search_text` 四引擎全部不可用（`bing/brave/duckduckgo/yahoo` 均 `No results found`）、`google_news_rss` 0 结果；`tc260.org.cn` 指南页本轮抓取到的实践指南列表**不含** AIGC 图片/文档指南条目。
- **结论（不掩盖）**：此前 R3 记录的"文字高度 ≥ 画面最短边 5%"等数值，**本轮无法用官方原文复核**，只能维持"二手一致但未核原文"。因此：A-6 的**要素**判据可用（办法第四条+常识），**尺寸/位置**判据 → **BLOCKED**；任何人不得据此认为"图注已满足 GB 尺寸要求"，也不得反过来认定"不满足"。
- **解除条件（runbook）**：① 通过 openstd 的"下载标准"通道（需登录/付费）或单位标准库取得 GB 45438—2025 正文章节 5/6；② 若仍不可得，由法务/合规顾问出具"对本产品形态的适用性意见"，替代条款核对；③ 取得后只需复核 A-6、A-10、B-14 三项，不改代码（本产品用图注而非图内水印）。

### 7.4 Google Play（本轮通道不可达；引前批已核引文）
- 本轮 `WebFetch` 对 `support.google.com/googleplay/android-developer/answer/10787469`（Data safety）、`developer.android.com/develop/store/data-safety`、以及 `r.jina.ai` 代理**全部连接超时**（`Connect Timeout / ETIMEDOUT`）→ **本轮未取得新证据**。
- 前批已核引文（`docs/research/2026-11-02-kb-r6-ledger-and-16kb.md:782`，同 URL）：「所有开发者必须填；包括第三方库/SDK；"On-device access/processing... does not need to be disclosed"；但"transmitting data from your app off a user's device"要披露」。
- 前批已核引文（同文件 `:783`，隐私政策，源 URL `https://support.google.com/googleplay/android-developer/answer/9859455`）：「You must link to a privacy policy on your **store listing and within your app**, even if your app doesn't access personal or sensitive user data or target children.」
- 采信方式：**标 B/待本轮复核**。这两条直接支撑 C-1（应用内必须有入口）与 C-4（BYOK 出境必须披露）；它们是产品判定的关键，故**解除条件**写死：上架前必须由能访问 Google 官方文档的环境重抓一次并留存 URL + 抓取时间。

### 7.5 拒绝/降级的来源
| 来源 | 处理 | 理由 |
|---|---|---|
| 知乎/律所"5% 水印"转述（R3 §2.2 引） | **降级为待验证** | 二手转述，非标准原文；本轮无法交叉到原文 |
| 博客园 XMP 实现文（R3 §2.3 引） | **沿用为单一来源**，仅支撑 `AIGC_NAMESPACE_URI` 的实现选择 | 代码内已自述单一来源，B-14 保持 BLOCKED |
| 各商店（华为/小米）未成年人材料细则 | **不采** | 本轮未抓取，且商店细则高频变动；按 `store-launch-materials-checklist.md` §4 现有"待验证"保留 |

---

## 8. 本轮实跑命令与关键输出（可逐字重放）

### 8.1 `/AIGC` 7 键 + 负向对照（D-1/D-2）
```bash
cd "D:/smart mistake book" && python -I - <<'PY'
import json, os, time
from pypdf import PdfReader
for p in ["build/device-track-batch42/ws2-pdf/real-export-labeled.pdf",
          "build/device-track-batch42/ws2-pdf/pure-user-unlabeled.pdf"]:
    r = PdfReader(p); info = r.metadata
    print(p, os.path.getsize(p), "pages", len(r.pages), "keys", list(info.keys()) if info else None)
    if info and "/AIGC" in info:
        d = json.loads(info["/AIGC"]); print("  keys", list(d.keys()), "Label", d["Label"], "PIDlen", len(d["ProduceID"]))
PY
```
输出（要点）：`real-export-labeled.pdf 53271 pages 1 keys ['/Producer','/AIGC']`、`Label 1`、`ContentProducer SmartMistakeBook`、`ProduceID` 长度 64；`pure-user-unlabeled.pdf 40406 keys ['/Producer']`（无 `/AIGC`）。

### 8.2 只追加 + 幂等（D-3/D-4）
```bash
cd "D:/smart mistake book" && python -I - <<'PY'
import hashlib
s="build/device-track-batch41/pdf/device-evidence/pdfdocument-chinese-source.pdf"
l="build/device-track-batch41/pdf/device-evidence/pdfdocument-chinese-labeled.pdf"
bs,bl=open(s,'rb').read(),open(l,'rb').read()
print("append-only", bl.startswith(bs), "delta", len(bl)-len(bs))
h=lambda p: hashlib.sha256(open(p,'rb').read()).hexdigest()[:16]
print("idempotent", h("build/device-track-batch41/pdf/device-evidence/classic-table-labeled.pdf")==h("build/device-track-batch41/pdf/device-evidence/classic-table-twice.pdf"))
PY
```
输出：`append-only True delta 393`；`idempotent True`。

### 8.3 UI/隐私/青少年/SDK 面（D-5…D-8）
```bash
cd "D:/smart mistake book"
grep -rn "AI 生成\|AI生成\|内容由AI\|内容由 AI\|人工智能生成" --include=*.kt --include=*.xml app/src/main/kotlin core/ui/src feature | wc -l   # → 0
grep -rn "青少年\|未成年\|防沉迷\|使用时长\|休息提醒" --include=*.kt app/src/main/kotlin core/ui/src feature | wc -l                                  # → 0
grep -rn "privacy\|Privacy" --include=*.kt --include=*.xml app/src/main core/ui/src | grep -v DataPrivacyScreen                                               # → 仅路由/宿主接线
grep -in "firebase\|crashlytics\|analytics\|sentry\|umeng\|admob" gradle/libs.versions.toml app/build.gradle.kts core/*/build.gradle.kts | wc -l                 # → 0
```

### 8.4 未跑但必须跑的复跑集（本批 §4 阶段串行执行，逐条对应上文检查项）
```bash
./gradlew :core:model:testDebugUnitTest --tests "*PdfAigc*" \
          :core:export:testDebugUnitTest --tests "*Aigc*" --tests "*AiGeneratedImageCaption*" --tests "*MistakeExportVisualRole*" \
          :core:data:testDebugUnitTest --tests "*GeneratedFigureAigcXmp*"
# 设备（串行、非 connectedLocalFirstDebugAndroidTest）
./gradlew :core:export:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.export.MistakePdfExporterAigcInstrumentedTest
./gradlew :app:connectedDebugAndroidTest  # PdfAigcDeviceRoundTripInstrumentedTest（10 例）
python -I tools/ci/check_backup_plan_sync.py
python -I tools/ci/check_release_manifest.py
```
> 上述命令**本轮一条都没跑**；任何人引用它们时必须报告实际 exit code 与 XML/日志路径，不得只写 `OK (n tests)`。

---

## 9. 未实现项的最小修复落点（供批次 45 排期；本批不实施）

| 项 | 落点 | 热区？ | 备注 |
|---|---|---|---|
| A-1/A-2 交互界面显式标识 | `feature/tutor`（回复渲染）、`core/ui`（`AttachedImageCard`） | **热区，禁改** | 文案直接用 `docs/ai-content-disclosure-copy.md` §1；判据：文本节点含 `AI`+`生成` |
| A-7 用户说明接线 | `app/.../SecondaryScreens.kt`（新增"关于/AI 生成内容说明"）+ `SmartMistakeBookRoot.kt`（**Root 热区**） | 部分热区 | 文案已冻结，接线不改文案 |
| A-9 模型文本的显式标识 | `core/export`（已批准 hunk 范围内）→ 卷面加一行限定语 | 可改（限 hunk） | 需先与 A-3 判据合并成一次 `shouldLabel` 调用，禁止另起分支 |
| B-11/KD-85 | `core/export` + `feature/tutor`（热区） | 热区 | 缺陷台账已给两条方案（`known-defects.md:3326`），须主会话选一 |
| B-12/KD-84 | `core/export/MistakePdfDelivery.kt` 子集路径 | 可改 | 需保留 `PdfRenderer` 页面语义 |
| B-13 DOCX 文档级 | `core/export/MinimalDocxWriter.kt` + `docProps/core.xml` | 可改 | **先裁定**：DOCX 是否属"文件"义务面（R1 未给 DOCX 载体口径） |
| C-1/C-3 隐私政策 | `app`（入口）+ `docs/current`（正文，**热区**） | 热区 | 正文建议放 `docs/` 非热区新文件，再由设置页以 WebView/本地页承载 |
| C-12 监护人同意 | `core/data`（同意存储）+ `app`（UI） | 部分 | 与遥测同意共用 `TelemetryGate` 的年龄闸口径最省 |

---

## 10. 供验收台账消费的编号建议（H 域）

本文 40 项可直接映射为计划 §2 H 域的 H-01…H-40：

| 段 | 编号 | 项数 |
|---|---|---|
| AIGC 显式（§2 A-1…A-12） | H-01…H-12 | 12 |
| AIGC 隐式（§3 B-1…B-16） | H-13…H-28 | 16 |
| 隐私政策/data safety（§4 C-1…C-12） | H-29…H-40 | 12 |
| 青少年模式（§5 D-1…D-6） | H-41…H-46 | 6 |
| 导出水印（§6 W-1…W-10） | H-47…H-56 | 10 |

> 与计划"H 域 90 项"的差额（34 项）应由文案质量、免责声明、错误文案、i18n/中文排版等其余子题补齐——本文只覆盖合规硬面，不虚增。

---

## 11. 来源清单

**本轮抓取（A/B）**
1. 《人工智能生成合成内容标识办法》全文：https://www.cac.gov.cn/2025-03/14/c_1743654684782215.htm
2. 同上·答记者问：https://www.cac.gov.cn/2025-03/14/c_1743654685896173.htm
3. 网信办就 GB 45438—2025 的专家解读：https://www.cac.gov.cn/2025-09/05/c_1758792061408012.htm
4. GB 45438—2025 标准详情（openstd）：https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=F32EA2A561F1886CD8D606513512D547
5. 《未成年人网络保护条例》：https://www.cac.gov.cn/2023-10/24/c_1699806932316206.htm
6. 《移动互联网未成年人模式建设指南》：https://www.cac.gov.cn/2024-11/15/c_1733364304749288.htm
7. 《个人信息保护法》第三十一条：https://www.cac.gov.cn/2021-08/20/c_1631050028355286.htm

**本轮不可达（通道故障，非"不存在"）**
8. Play Data safety：https://support.google.com/googleplay/android-developer/answer/10787469 （连接超时 ×3 次，含 jina 代理）
9. Android Data safety 文档：https://developer.android.com/develop/store/data-safety （连接超时）
10. TC260 实践指南目录页：https://www.tc260.org.cn/portal/article/303/533e0475b511479aaadf5cdd81182caf （可访问，但当前列表不含 AIGC 图片/文档指南条目）

**仓内依据（C）**
11. `docs/research/2026-10-25-ai-content-labeling-compliance.md`（R3，义务映射与差距清单 G1–G8）
12. `docs/ai-content-disclosure-copy.md`（三档用户文案，冻结待接线）
13. `docs/research/2026-10-25-store-launch-materials-checklist.md`（上架材料 12 项）
14. `docs/current/privacy-and-data-safety.md`、`PRIVACY-DATA-FLOW.md`（隐私声明与数据流）
15. `docs/known-defects.md`：KD-75（`:2879` 关闭，范围收窄）、KD-84（`:3308` 部分页丢标识）、KD-85（`:3318` 模型改卷文本漏标）
16. `docs/research/2026-11-02-kb-r6-ledger-and-16kb.md:782,790`（Play 政策前批引文）
17. `docs/research/2026-11-05-batch-43-record.md:571-578`（§9.7 收口裁定：KD-75 范围、发版不成立）

---

## 12. 本文的未验证与边界（不写成通过）

1. **本轮零 Gradle、零设备、零 Provider**：所有"已实现（待复跑）"必须在 §4 门禁阶段用 §8.4 的命令复跑后才可谈 PASS。
2. **产物是前批盘上文件**（mtime 2026-11-03），只能证明"那棵树的行为"；当前树的二进制行为未被本文证明。
3. **GB 45438 尺寸/位置口径、TC260 命名空间 URI** 均未取得官方原文 → 相关 3 项（A-6 尺寸面、A-10、B-14）保持 BLOCKED/待验证。
4. **Play 两条关键要求**（应用内政策链接、BYOK 出境须披露）本轮无法重新抓取，引前批引文，上架前必须重抓。
5. **"审美/语感"类**（例如"标识是否足够醒目""说明文案是否好懂"）无法编码判定 → 按计划 §6 标 **UNVERIFIED**，不由本文签字。
6. 本文不含法律意见；AIGC 义务主体（BYOK 工具型 App 是否构成"服务提供者"）仍是 R3 的**假设**，公开分发前需法务复核。
