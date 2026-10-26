# R1：PDF 类导出物的隐式标识要求与写入选型（驱动 T4）

- 日期：2026-10-26（批次 23）
- 性质：只研究、不落地；未改任何代码、未运行 Gradle、未做 git 写操作
- 输入：批次 23 计划 `.zcode/plans/plan-2026-10-26-batch-23-device-gates-lockfiles.md`；批次 20 R3 `2026-10-25-ai-content-labeling-compliance.md`；批次 21 R2 `2026-10-25-implicit-labeling-xmp-selection.md`
- 结论口径：**"法规未要求 PDF 携带隐式标识"不成立**——TC260 官方文本文件指南 §6.3 明文规定了 PDF 的载体与写法。但对"本 App 当前这份只含 AI 图、不含 AI 生成文本的 PDF"是否被该指南**直接触发**，属于适用范围解释问题，不是豁免。推荐按官方 PDF 方法补写 `/AIGC`，而不是只保留显式图注。

---

## 0. 结论摘要

1. **官方 PDF 方法存在且明确**：TC260-PG-20258A《网络安全标准实践指南——人工智能生成合成内容标识方法 文件元数据隐式标识 文本文件（V1.0-202508）》§5.1 c) 把 `pdf` 列为文本文件格式，§6.3 规定：存储位置为 **Document Information Dictionary**，写入方式为 **`/AIGC({"Label":"value1",...,"ReservedCode2":"value7"})`**。本文档已从 tc260.org.cn 直接下载该指南 PDF（20 页，PDF 1.7，3,272,625 字节，sha256 `6bf6c118…3b0e`，与第三方研究引用的 hash 前缀一致），条文为原文提取，证据等级**高**。
2. **适用范围是"AI 生成合成文本内容"**：20258A §1 范围与 §3.1 定义均限定为"人工智能生成合成文本内容"；§5.2 另说"其他文件格式可参考处理"。本 App 的 PDF 由确定性渲染器组装；按现有导出输入与生产写入点，正文是用户录入/OCR 转录/备注，AI 产物是 **图片**（`CLEAN_IMAGE`、`TUTOR_EXPLANATION`）。**复核补记（2026-10-26，见 §10.3）**：原文这里写"当前没有 AI 生成文本块"，该断言不被代码支持——`MODEL_DOCUMENT_PARSE` 证据可进入已提交文档，导出层没有 provenance 字段；写入判据必须同时覆盖模型解析文本，不能只按"仅 AI 图"。图片指南 TC260-PG-20259A 只覆盖 **图片文件**（HEIF/HEIC、JPEG、PNG、TIFF、WebP、GIF），**不含 PDF**。因此"AI 图装进 PDF"这一组合没有专属条款，落在两份指南之间的空档，而不是被明确豁免。
3. **显式图注与隐式标识是并列义务**：《人工智能生成合成内容标识办法》第四条第二款要求下载/复制/导出时文件含**显式**标识；第五条另行要求在生成合成内容的**文件元数据**中加**隐式**标识。现有逐图图注"本图由 AI 生成"（`core/export/.../AiGeneratedImageCaption.kt:27`）满足第四条第二款，**不能替代第五条**。所以"显式图注已足"只在"该 PDF 不在第五条/20258A 范围内"的前提下成立；按监管保守口径不建议这样认定。
4. **推荐写入方案**：**不引第三方库**，在现有 `android.graphics.pdf.PdfDocument` 产物上做 **append-only 增量更新**，把 `/AIGC` 写进 Info 字典；仅当 PDF 实际含 AI 生成图时写；**不写 XMP**——TC260 的 PDF 载体就是 Info 字典，XMP 是图片文件的载体（20259A §6）。
5. **已做 PoC（本日实际执行）**：在一份真实经典 xref PDF（TC260 官方指南本身）上追加 552 字节增量段，`pypdf` 读回 `/AIGC` 完整 JSON，`PyMuPDF` 仍能打开 20 页，原有 `/Author` `/CreationDate` `/ModDate` `/Producer` `/Title` 全部保留。命令与输出见 §8。
6. **pdfbox-android 否决**：Apache-2.0，但最后发布 `2.0.27.0`（2023-01-02）、最后提交 2023-12-29、125 个 open issues；AAR 3,254,019 字节（classes.jar 1,756,101 字节 / 902 个 class / assets 4,634,456 字节），POM 还传递 BouncyCastle 1.72 三件（bcprov 9,342,484 + bcpkix 1,022,720 + bcutil 677,620 字节）；minSdk 14 可用，但需要 `PDFBoxResourceLoader.init(context)`；整文档加载/重写，内存与风险都远超"写一个 Info 键"。
7. **落地受热区限制**：`core/export` 当前有 5 个 M 文件（`build.gradle.kts`、`MistakeExportJobRunner.kt`、3 个测试），本批不改代码。若主会话确认 `MistakePdfExporter.kt` / `MistakePdfEligibility.kt` 可动，按 §4 步骤落地；否则 T4 以本文为"如实登记 + 可执行补丁计划"，**不得声称已合规**。

---

## 1. 法规与指南核对

### 1.1 《人工智能生成合成内容标识办法》原文（2026-10-26 从 cac.gov.cn 提取）

来源：<https://www.cac.gov.cn/2025-03/14/c_1743654684782215.htm>（本日 curl 200，23,354 字节，正文提取）。

- **第四条第二款**："服务提供者提供生成合成内容下载、复制、导出等功能时，应当确保文件中含有满足要求的显式标识。"
- **第五条**："服务提供者应当按照《互联网信息服务深度合成管理规定》第十六条的规定，在生成合成内容的文件元数据中添加隐式标识，隐式标识包含生成合成内容属性信息、服务提供者名称或者编码、内容编号等制作要素信息。"
  - 同条第三款："文件元数据是指按照特定编码格式嵌入到文件头部的描述性信息，用于记录文件来源、属性、用途等信息内容。"
- **第十条第二款**："任何组织和个人不得恶意删除、篡改、伪造、隐匿本办法规定的生成合成内容标识，不得为他人实施上述恶意行为提供工具或者服务，不得通过不正当标识手段损害他人合法权益。"
- **第十一条**："服务提供者开展标识活动的，还应当符合相关法律、行政法规、部门规章和强制性国家标准的要求。"

**对本 App 的直接含义**：
- 第四条第二款 = 导出 PDF 必须有显式标识 → 现有逐图图注已覆盖（G3，批次 20 落地）。
- 第五条 = 生成合成内容的**文件元数据**必须有隐式标识 → PDF 是否算"生成合成内容的文件"是解释点；但 PDF 是 AI 图离开 App 后的唯一载体，图片自身 EXIF/XMP 在重绘进 PDF 时已丢失（`GeneratedFigureMetadata.kt:19-21` 的边界说明），因此保守口径应补 PDF 级隐式标识。
- 第十条 = 不得伪造 → 只有 `QUESTION_DIAGRAM` 或纯用户内容的 PDF **不得**标成 AI 生成；写入条件必须与"实际含 AI 图"严格绑定。

### 1.2 GB 45438-2025 的可及性与字段来源

| 项 | 事实 | 证据等级 |
|---|---|---|
| 标准状态 | 《网络安全技术 人工智能生成合成内容标识方法》，**现行、强制性国标**，发布 2025-02-28，实施 2025-09-01 | 高（openstd 著录页本日 WebFetch：`现行`/`2025-02-28`/`2025-09-01`） |
| 全文获取 | openstd 著录页只有 JS "在线预览/下载"控件，无直接 URL；本日 `curl` 其 `showGb?type=online` 返回 **302** 回著录页；TC260 曾托管的 `.../2025-03-15/1742009439794081593.pdf` 本日返回 **23,681 字节 HTML 登录壳**，不是 PDF | 全文**未取得**，UNVERIFIED |
| 字段结构来源 | TC260-PG-20259A §6 b) 明文引用"**GB 45438-2025 附录 E 规定字符串**"；20258A §6.3 给出同一 7 字段 JSON 模板：`{"Label","ContentProducer","ProduceID","ReservedCode1","ContentPropagator","PropagateID","ReservedCode2"}` | 中高（两份官方指南一致；GB 原文未核） |

**结论**：字段名与 7 字段模板有两份官方指南交叉，可信；GB 45438-2025 的逐条正文（尤其附录 E 是否允许省略 4 个可选字段）**仍未核实**，不得当成已验证事实。

### 1.3 TC260 指南家族与逐条原文

**发布批次**：2025-08 发布 6 项实践指南，覆盖文本、图片、音频、视频的元数据隐式标识、元数据安全防护与检测框架（来源：安全内参 <https://www.secrss.com/articles/82518>，本日 WebFetch；原文明确"6份文件明确了……文本、图片、音频、视频等内容的文件元数据隐式标识方法、元数据安全防护指南以及生成合成内容检测框架"）。已知编号：20257A 视频、**20258A 文本**、**20259A 图片**、202510A 音频、202511A 安全防护（20258A/20259A 已由官方 PDF 自身确认；20257A/202510A/202511A 为第三方研究 `wiltodelta/remove-ai-watermarks` 的 `docs/research-sweep-2026-09.md` 转述，未逐一下载，标"待验证"）。

#### 1.3.1 TC260-PG-20258A（文本文件）——PDF 的官方方法

- 标题页原文：`TC260-PG-20258A / 网络安全标准实践指南 / ——人工智能生成合成内容标识方法文件 / 元数据隐式标识文本文件 / （V1.0-202508） / 全国网络安全标准化技术委员会秘书处 / 2025 年08 月`
- 官方 PDF：<https://www.tc260.org.cn/tc260/sjzn/202508/f0a84ada0c11458e9cb6da56c1cb2426/files/760387fb30e94c058c31b8c2b2230020.pdf>
- 本日实测：HTTP 200，`application/pdf`，3,272,625 字节，PDF 1.7，20 页；sha256 `6bf6c1189208856099bfbf648bdf386da288b1df00f430502b395b874dbf3b0e`（第三方研究引用前缀 `6bf6c118…3b0e`，一致）。
- **§1 范围**（原文）："本文件给出了人工智能生成合成文本内容的文件元数据隐式标识方法，包括针对不同文本文件格式的具体方案。本文件适用于指导人工智能生成合成文本内容服务提供者和网络信息内容传播服务提供者开展文件元数据隐式标识活动。"
- **§3.1 定义**："人工智能生成合成文本内容：利用人工智能技术生成、合成的文本信息。"
- **§5.1 文本文件格式**（原文节选）：
  - "a) 基于OOXML 规范的文本文件，如docx、pptx、xlsx、dotx、xltx、potx；"
  - "c) 基于PDF 规范的文本文件，如pdf；"
  - "e) 基于Markdown 规范的文本文件，如md；"（其余 b/d/f/g 为 UOF/OFD/xmind/posm、posf）
- **§5.2 方法概述**（原文）："针对本文件5.1 所列各类格式的文本文件，本文件给出了元数据隐式标识方案，**其他文件格式可参考处理**。"
- **§6.3 基于PDF 的文本文件**（原文，PDF 第 9 页/正文第 4 页）：
  > a) 存储位置为Document Information Dictionary;
  > b) 写入方式为：
  > /AIGC({"Label":"value1","ContentProducer":"value2","ProduceID":"value3","ReservedCode1":"value4","ContentPropagator":"value5","PropagateID":"value6","ReservedCode2":"value7"})。
- **注意**：§6.3 只写 Info 字典，**没有**要求 XMP 元数据流；XMP 是图片指南的载体。

#### 1.3.2 TC260-PG-20259A（图片文件）——确认 XMP 载体，但不覆盖 PDF

- 标题页原文：`TC260-PG-20259A / 网络安全标准实践指南 / ——人工智能生成合成内容标识方法文件 / 元数据隐式标识图片文件 / （V1.0-202508） / 全国网络安全标准化技术委员会秘书处 / 2025 年08 月`
- 官方 PDF：<https://www.tc260.org.cn/tc260/sjzn/202508/7227bfecdf344c14abc647fbbcf69edf/files/37774d2b3149456ba7179a81bc07d3d5.pdf>
- 本日实测：HTTP 200，2,190,188 字节，PDF 1.7，11 页；sha256 `e4f7fe820a181b2e24a5e3214b751e67c04672ec68fb4d66484da4e77b02a02f`（第三方引用前缀 `e4f7fe82…a02f`，一致）。
- **§1 范围**："本文件给出了人工智能生成合成**图片内容**的文件元数据隐式标识方法……"
- **§5 概述**："图片文件格式一般包括HEIF/HEIC、JPEG/JPG、PNG、TIFF、WebP、GIF 等。"
- **§6 图片文件的元数据隐式标识方案**（原文节选）：
  > a) RDF 中新增自定义命名空间TC260，URI 为 http://www.tc260.org.cn/ns/AIGC/1.0/。该命名空间已有其他内容时，应按属性更新，不应整体覆盖；
  > b) 在TC260 空间下AIGC 键值中填入GB 45438-2025 附录E 规定字符串，且按照下文实现序列化：
  > `<TC260:AIGC>{"Label":"value1","ContentProducer":"value2","ProduceID":"value3","ReservedCode1":"value4","ContentPropagator":"value5","PropagateID":"value6","ReservedCode2":"value7"}</TC260:AIGC>`；
  > c) 不同格式文件写入元数据信息时，写入方式包括：1）JPEG/JPG 文件，将元数据写入APP1 中标签名为XMP 的字段；2）PNG 文件，将元数据写入类型为iTXt 的XMP 字段；3）TIFF 文件，将元数据写入IFD0。tag 为0x2BC，Field type 类型为7；4）GIF ……5）webP ……6）HEIF/HEIC ……
- **结论**：
  - 图片指南的格式清单**没有 PDF**；它不覆盖"图片被嵌进 PDF"的场景。
  - 它**确认了**项目 `GeneratedFigureMetadata.kt:154` 的命名空间 URI `http://www.tc260.org.cn/ns/AIGC/1.0/` 是官方口径——此前 R2 §5-U2/"单一来源待验证"到此可关闭（仅 URI 与属性名，字段可选性仍未闭合）。
  - 它也确认官方模板是 7 字段；本项目图片路径目前只写 3 字段（`GeneratedFigureMetadata.kt:176-180`），属于**需要复核的差异**（见 §6 U4），但不是本 T4 的改动范围。

### 1.4 适用范围矩阵（本 App 的 PDF 落在哪一格）

| 内容类型 | 文件格式 | 适用指南 | 官方载体 | 本 App 现状 |
|---|---|---|---|---|
| AI 生成**文本** | PDF | 20258A §6.3 | Info 字典 `/AIGC` | **可能含模型解析文本**（§10.3：`MODEL_DOCUMENT_PARSE` 可经 CAPTURE_PARSE 进入已提交文档，导出层无 provenance；原"无 AI 文本"断言不成立） |
| AI 生成**图片** | 图片文件（PNG/JPEG/…） | 20259A §6 | XMP `TC260:AIGC` | 已落（EXIF 四字段 + XMP 三字段） |
| AI 生成**图片** | **PDF** | **无专属条款**；20258A §5.2"其他文件格式可参考处理" + 办法 §5 保守解释 | 可复用 20258A §6.3 的 Info `/AIGC` | **当前无 PDF 级隐式标识**（本次要补的缺口） |
| AI 生成**图片** | DOCX | 图片字节原样嵌入，XMP/EXIF 随字节保留；若文档含 AI 文本，则 20258A §6.1 要求 `docProps/custom.xml` 的 `AIGC` 属性 | OOXML custom property | 图片字节保留；**无 `docProps/custom.xml`**（`MinimalDocxWriter.kt:75-90` 只写 content types/rels/document/media） |
| 纯用户/OCR 内容 + `QUESTION_DIAGRAM` | PDF | 不触发《办法》生成内容标识 | — | **不得写 `/AIGC`**（第十条禁止伪造） |

**判断链**：
1. 20258A §6.3 = PDF 的官方隐式标识方法，存在性不是问题。
2. 20258A 的适用对象是"AI 生成合成**文本**内容"；当前 PDF 的 AI 产物是图片，文本不是 AI 生成 → 字面上不触发 20258A。
3. 20259A 的适用对象是"AI 生成合成**图片**内容"的**图片文件**；PDF 不是图片文件 → 也不触发 20259A。
4. 但办法 §5 的义务对象是"生成合成内容的**文件元数据**"，PDF 是 AI 图离开 App 后的实际文件；两份指南都没有说"PDF 里的 AI 图不需要标识"。因此这是**空档**，不是豁免。
5. 空档的处置有两条合法路径：
   - 保守路径（推荐）：按 20258A §5.2 "其他文件格式可参考处理" + §6.3 的同一载体写 `/AIGC`；
   - 解释路径：由法务/合规确认"仅含 AI 图、不含 AI 文本的 PDF 不触发 20258A"，则只需保留显式图注并登记理由。
   **本研究的建议是保守路径**：成本低（§4），且能消灭"PDF 离开 App 后无任何机器可读 AI 来源"这一具体失败。

### 1.5 "显式图注是否足够"的合规判断

| 义务 | 条文 | 现有实现 | 是否满足 | 证据等级 |
|---|---|---|---|---|
| 显式标识（导出文件） | 办法 §4(2) | PDF 内每张 AI 图下"本图由 AI 生成"（`AiGeneratedImageCaption.kt:27`；PDF 侧 `MistakePdfPagePlanner.kt:148-149`、`:162-163`、`:412-413` 三处；DOCX 侧 `DocxMistakeRenderer.kt:237-239`、`:294-296`、`:329-331` 三处） | **是**（文件内含用户可见文字提示） | 中高：办法原文高；GB 45438 对文字提示/5% 规格的细节未核（V1） |
| 隐式标识（文件元数据） | 办法 §5 + §11（GB 45438） | PDF 无 AIGC 文档级元数据（只有 Skia 的 `/Producer`）；图片 EXIF/XMP 在重绘时丢失 | **否**（若 PDF 在 §5 范围内） | 高：代码事实；"PDF 是否在范围内"=解释（中） |
| 不得伪造 | 办法 §10 | 仅 AI 角色图加图注；`QUESTION_DIAGRAM` 不加 | 是（现有判据已按角色单源） | 高 |

**结论**：
- 对**显式**义务，图注足够（在 GB 45438 细节未核的前提下，按办法第四条的文字提示口径判断）。
- 对**隐式**义务，图注不是元数据，不能替代；"显式图注已足"这个结论**不成立**于"PDF 在 §5 范围内"的情形。
- 因此 T4 不应只做文案与登记；应补 PDF 级 `/AIGC`，或由法务明确裁定不触发并留下书面理由。

### 1.6 对 T4 的直接回答（任务书条件分支）

任务书写："若结论是'法规未要求 PDF 携带隐式标识，显式图注已足'，给出条文明细并说明 T4 只需文案与登记。"

**本研究的结论不是这个分支**：
- 法规/官方指南**有** PDF 隐式标识方法（20258A §6.3），所以"法规未要求 PDF"为假。
- 当前 PDF 的 AI 内容是图片、不是文本，20258A 的字面触发条件不满足；20259A 又不覆盖 PDF。这是**范围空档**，需要解释，不能直接当成"不要求"。
- 保守且低成本的处置是补 `/AIGC`；若主会话/法务选择"不触发"解释，则应把 §1.3.1 的条文、§1.4 的矩阵与理由写入登记，**不能只写"法规未要求"**（那与官方指南原文冲突）。

---

## 2. 现有导出链：是字节组装还是第三方库？

### 2.1 判定：平台 `android.graphics.pdf.PdfDocument`（Skia PDF 后端），两者都不是

证据（本日读取）：
- `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DeterministicMistakePdfRenderer.kt:62`：`val document = PdfDocument()`
- 同文件 `:151-156`：`FileOutputStream(output)` → `document.writeTo(buffered)` → `flush` → `fd.sync`
- `core/export/build.gradle.kts` 依赖只有 `:core:model`、`:core:domain`、`androidx.core.ktx`、`kotlinx.serialization.json`；**没有任何 PDF 第三方库**（`grep -rn "pdfbox\|PdfBox" --include=*.kts --include=*.toml --include=*.kt core/ app/ feature/` 无命中）
- 打印子集另起一份 `PdfDocument`：`core/export/.../MistakePdfDelivery.kt:227`，`writeTo` 在 `:258-260`

**结论**：PDF 由 Android 平台 API 生成（底层 Skia），不是手写 PDF 对象组装，也不是 pdfbox/iText 等库。要加文档级元数据，只能在**产物字节**上做后处理，或换库重写整条渲染链。

### 2.2 Android/Skia 产物的结构（决定最小注入方式）

- **AOSP `PdfDocument.java` 没有元数据 API**：本日抓取 `aosp-mirror/platform_frameworks_base` 的 `graphics/java/android/graphics/pdf/PdfDocument.java`（459 行），`grep -n "metadata\|Metadata"` **零命中**；公开方法只有 `startPage/finishPage/writeTo/close` 与 native 声明。→ 无法通过平台 API 设置 Info/XMP。
- **JNI 用默认 Metadata**：AOSP `libs/hwui/jni/pdf/PdfDocument.cpp:89` 为 `SkPDF::MakeDocument(stream)`（无 metadata 参数），即 Skia 默认值。
- **Skia 一定写 Info 字典**：`skia/src/pdf/SkPDFDocument.cpp:281` `fInfoDict = emit(*SkPDFMetadata::MakeDocumentInformationDict(fMetadata))`；`:155` `trailerDict.insertRef("Info", infoDict)`；`SkPDFMetadata.cpp:68-89` 只写非空的标准键（Title/Author/Subject/Keywords/Creator/Producer/CreationDate/ModDate），默认 `Producer = "Skia/PDF mXX"`（`include/docs/SkPDFDocument.h:121`）。
- **经典 xref 表**：`SkPDFDocument.cpp:104` `s->writeText("xref\n0 ")`；footer 在 `:146-162`，`trailer` + `/Root` + `/Info`，`/ID` 仅在 UUID 非零时写（`:157`）。Android 默认 UUID 为零 → **无 `/ID`**。
- **XMP 只在 PDF/A 时写**：`SkPDFDocument.cpp:635-637` `if (fMetadata.fPDFA) docCatalog->insertRef("Metadata", fXMP)`；Android 默认非 PDF/A → **没有 `/Metadata` 流**。
- **无加密/线性化**：Android 默认 Metadata 不设加密，Skia 不线性化。→ 我们的产物是"经典 xref + 必有 `/Info` + 无 `/ID` + 无 XMP + 无 Encrypt"的最简形态。

**这直接决定**：TC260 要的 `/AIGC` 应写进 **Info 字典**；不需要碰 Catalog，也不需要 XMP 流。最小侵入方案是**追加一个增量更新段，重定义 Info 对象**。

### 2.3 现有导出管线与"写在哪里"的精确接缝

`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt`：

| 行 | 行为 | 对注入的意义 |
|---|---|---|
| `:114` | `fun prepare(input)` | 单份导出的入口 |
| `:120-122` | `store.findExisting(input)` 命中则直接返回缓存 | **旧缓存不会自动带新标识**（见 §2.4） |
| `:128` | `DeterministicMistakePdfRenderer.render(input, temporaryPdf)` | 产物落临时文件 |
| `:129` | 字节上限检查 | 注入后应再查一次（标签会增加几百字节） |
| `:134` | `validatedPageCount(temporaryPdf)` → `PdfRenderer` 打开 | **注入后天然的 PDF 解析验证门** |
| `:135` | `store.writeDigest(..., sha256File(temporaryPdf))` | 摘要必须覆盖**注入后的最终字节** |
| `:136` | `store.publish(...)` | 发布到缓存目录 |
| `:295` | `PreparedPdfStore.findExisting` 只校验目录名/摘要/文件对，不读元数据 | 缓存复用点 |
| `:358-378` | `publish` 先 `findExisting`，命中则直接返回 | **若旧缓存被 `findExisting` 接受，新标签会被丢弃** |

### 2.4 缓存失效是必须处理的牵动面（不是可选优化）

- 缓存目录名 = `input.inputSha256`（`MistakePdfExporter.kt:382-385`），而 `inputSha256` 由 `MistakePdfEligibility.kt:185-186` 的 `exportFingerprint("mistake-export-v3", ...)` 计算。
- `findExisting` 只按摘要/文件对校验，**不检查 PDF 是否带 `/AIGC`**；`publish` 又先 `findExisting`。因此：功能上线后，**老用户的暖缓存会继续返回没有 `/AIGC` 的 PDF**，且新一轮渲染结果会被 `publish` 丢弃。
- 处理方案（三选一，推荐 A）：
  - **A（推荐）**：把 `"mistake-export-v3"` 升为 `"mistake-export-v4"`（`MistakePdfEligibility.kt:186`）。一次性失效全部旧缓存与旧 token；改动一行 + 相关 golden/断言更新。语义最干净，不需要在复用路径做元数据读取。
  - **B**：`findExisting` 增加"含 AI 图时必须能读到 `/AIGC`"校验；读不到就删除该缓存目录并返回 null；`publish` 需配合允许替换旧目录。改动更局部，但多一条读路径与删除逻辑。
  - **C**：改缓存目录名（`EXPORT_DIRECTORY = "mistake_pdf_exports_v2"`）。实现最省事，但旧目录不再被新 store 的清理逻辑扫描，最多泄漏 64MB 直到系统清缓存；不推荐。
- 无论选哪个，`PreparedMistakePdf.verifyIntegrity`（`MistakePdfExporter.kt:64-73`）要求摘要与最终字节一致，所以注入必须在 `writeDigest` 之前完成。

### 2.5 其它出口（枚举，不漏）

| 出口 | 现状 | 是否带 `/AIGC` | 处置 |
|---|---|---|---|
| 单份/批量导出（`MistakePdfExporter.prepare`） | 渲染 → 摘要 → 缓存 → 交付 | 否 | **本次主落点** |
| 打印整份（`PreparedPdfPrintWriter.copyWhole`，`MistakePdfDelivery.kt:208-213`） | 直接流式复制 prepared 文件 | 随 prepared 文件（若主落点已注入则有） | 无需改 |
| 打印部分页（`writeSubset`，`:215-260`） | `PdfRenderer` 逐页重绘 → 新 `PdfDocument` → `writeTo` | **否**（重绘丢元数据） | 若"打印到 PDF"算导出，需在写入前对子集缓冲并注入；否则登记残留。建议至少登记 |
| DOCX 导出（`MinimalDocxWriter`） | 自写 OPC 包，只有 `[Content_Types].xml`、`_rels/.rels`、`word/document.xml`、媒体 | **无 `docProps/custom.xml`** | 20258A §6.1 的对称缺口；本次登记，不在 T4 PDF 范围 |
| 分享/保存（`MistakePdfDocumentSaver.save`） | 复制 prepared 文件字节 | 随 prepared 文件 | 无需改 |

---

## 3. 方案比较

### 3.1 pdfbox-android（`com.tom-roush:pdfbox-android`）

**实测事实（本日下载/反编译/查元数据，非文档推断）**：

| 项 | 事实 | 来源/命令 |
|---|---|---|
| 最新版本 | `2.0.27.0`；Maven `lastUpdated=20230102065914`（2023-01-02） | `curl https://repo1.maven.org/maven2/com/tom-roush/pdfbox-android/maven-metadata.xml` |
| 维护度 | GitHub 最后提交 2023-12-29（dependabot bump）；`pushed_at=2024-03-18`；open issues 125；stars 1,195；未归档 | `gh api repos/TomRoush/PdfBox-Android`、`.../commits?per_page=1` |
| License | **Apache-2.0**（POM 与 GitHub 一致） | POM `licenses`、repo metadata |
| 体积 | AAR **3,254,019 字节**；`classes.jar` **1,756,101 字节 / 902 个 class**；`assets/` **4,634,456 字节 / 112 个文件**；另有 `proguard.txt` | `curl` + `unzip -l` + `du -sb`（本日） |
| 传递依赖 | POM 声明 `bcprov-jdk15to18:1.72`（9,342,484 字节）、`bcpkix-jdk15to18:1.72`（1,022,720）、`bcutil-jdk15to18:1.72`（677,620）；合计 **11,042,824 字节**。AAR 中只有 2 个 class 引用 BouncyCastle（`PublicKeySecurityHandler`、`SecurityProvider`） | POM + class 字节扫描；`curl -I` 取 Content-Length |
| minSdk | AAR manifest `minSdkVersion=14`；README "Requires API 19 or greater for full functionality"；项目 minSdk 23 → 可用 | `cat aar/AndroidManifest.xml`、README |
| 初始化 | 必须在使用前 `PDFBoxResourceLoader.init(getApplicationContext())` | README |
| 元数据 API | `PDDocument.getDocumentInformation()`、`PDDocumentInformation.setCustomMetadataValue(String,String)`、`PDDocumentCatalog.getMetadata()/setMetadata()`、`PDMetadata.importXMPMetadata(byte[])`、`PDDocument.save(OutputStream)/saveIncremental` | `javap` 实测（本日，§8） |
| 处理方式 | `PDDocument.load(...)` 把整份 PDF 读进 COS 内存模型；`save` 全量重写，`saveIncremental` 增量写 | `javap` + PDFBox 2.0 语义 |

**评价**：
- 能做：`getDocumentInformation().setCustomMetadataValue("AIGC", json)` 一行即可写 `/AIGC`；XMP 也有 `PDMetadata.importXMPMetadata`。
- **不成比例**：为了一个 Info 键引入 3.25MB AAR + 11MB 传递依赖（即使 R8/exclude，也要处理 `-dontwarn`、ResourceLoader 初始化、锁文件、license/NOTICE、供应链审计）；库自 2023 年起无新发布，125 个 open issues；整文档加载对 8MB 单份/24MB 批量 PDF 有内存风险。
- **结论：不引**。除非手写方案在设备上被证伪，才把它作为回退。

### 3.2 手写 PDF 对象注入（append-only 增量更新）

**工作量估计**（基于两份独立开源实现的规模与本文 PoC）：
- 生产代码 ~150-250 行：尾部扫描/解析 trailer（~60）、Info 对象重写 + PDF 字符串转义（~40）、xref/trailer 追加（~40）、适用性校验/失败路径（~30）。
- 测试 ~150-250 行：合成 PDF 夹具（有/无 `/Info`、有 `/ID`、损坏、加密、含已存在 `/AIGC` 的幂等）、真实 `PdfDocument` 产物仪器化用例。
- 合计约 0.5-1 人日（不含设备门等待）。

**算法（本项目版）**：
1. 读文件头 `%PDF-`；读尾部 64KB。
2. 拒绝：含 `/Encrypt`、含 `/Linearized`、找不到最后一个 `startxref`/`trailer`、trailer 缺 `/Root`。
3. 解析 trailer 的 `/Size`、`/Root`、可选 `/Info`、可选 `/ID`。
4. 取 `/Info` 对象：读其字典体（`obj` 到 `endobj` 之间、`<<` 到最后一个 `>>`），若已有 `/AIGC` 则替换而不是重复追加。
5. 生成新 Info 对象（同一对象号、generation 0）：原字典体 + `/AIGC (转义后的 JSON)`；字符串转义 `\`、`(`、`)`、控制字符；值保持 ASCII（本项目的 producer/id 都是 ASCII；若未来有中文，改用 UTF-16BE hex 字符串）。
6. 在文件尾追加：新 Info 对象 + `xref` 子段（该对象号 1 条）+ `trailer`（`/Size` 不变或 max+1、`/Root` 原值、`/Info` 指向该对象、`/ID` 原样复制、`/Prev` 旧 startxref）+ `startxref` + `%%EOF`。
7. 失败即抛（fail-closed），由导出层转成 `GENERATED_PDF_INVALID` 或新增失败枚举；不得静默产出无标识 PDF。

**风险与缓解**：

| 风险 | 说明 | 缓解 |
|---|---|---|
| PDF 语法边界 | xref 流/对象流、加密、线性化、`/Prev` 链、generation 号、字符串里的 `>>` | 输入契约限定"本 App 的 Skia 产物"（经典 xref、必有 Info、无 Encrypt/Linearized）；不符合即 fail-closed |
| 字节偏移错误 | xref 偏移必须精确到字节；追加段编码必须 ASCII/UTF-8 一致 | PoC 已验证（§8）；单元测试用合成夹具 + 真实 PDF 双跑 |
| 读者兼容 | PDFium（`PdfRenderer`）、Adobe、浏览器是否接受增量段 | `validatedPageCount` 注入后立即用 `PdfRenderer` 打开；设备用例必须跑 |
| 签名/线性化失效 | 增量更新会使数字签名与线性化失效 | 本 App 产物无签名、无线性化；解析到即拒绝 |
| 非 ASCII 值 | PDF 文字串默认 PDFDocEncoding，中文会乱码 | 本项目值保持 ASCII；否则用 UTF-16BE `<FEFF...>` hex 串 |
| 重复注入 | 同一文件二次注入会重复 `/AIGC` 键 | 幂等：先检测并替换；用例覆盖 |
| 缓存/发布 | 旧缓存绕过新标签（§2.4） | 指纹升版或复用校验 |
| 打印子集 | `writeSubset` 重绘丢标签（§2.5） | 缓冲后注入，或登记残留 |
| 失败可用性 | fail-closed 会导致导出失败 | 设备用例覆盖正常产物；只有解析到非预期结构才失败，且错误可观测 |

**独立开源先例（许可证已核；只作模式参考，不复制无许可证代码）**：

| 项目 | 语言/平台 | 做法 | License | 维护度 | 结论 |
|---|---|---|---|---|---|
| `wangzhishou/OneBox` `feature/common/.../AigcFileMetadataWriter.kt` | Kotlin/Android | `PdfAigcInfoInjector`：追加增量 Info 更新 + 尾部注释兜底；写 `/AIGC` 为 PDF 字面串 | Apache-2.0 | 903 stars，2026-10-23 有推送 | 可参考；**不要学它的尾部注释兜底**（非标准，且不消灭标准读者的失败） |
| `midori52000/ArkPilot` `Agent/.../AigcPdfInjector.ets` | ArkTS/鸿蒙 | 纯 append 增量段；校验 `%PDF`/`/Encrypt`/`/Linearized`；有 Info 则重定义、无则新建；追加 xref/trailer `/Prev` | **无 license** | 3 stars，2026-09-01 | **只读参考，不复制代码** |
| `wanglei581/YITIJI` `services/api/src/common/pdf/aigc-label.ts` | TypeScript | 用 pdf-lib 设置 Info `/AIGC`（值是 JSON 字符串，不是分开的键） | **无 license** | 0 stars，2026-10-25 | 只读参考（证明载体口径） |
| `Stahl-G/briefloop` `src/briefloop/export_labeling.py` | Python | pypdf `add_metadata({'/AIGC': json})` | MIT | 9 stars，2026-10-17 | 可参考；其注释明确"TC260-PG-20258A §§6.1/6.3" |

### 3.3 不做（只保留显式图注）

- **仅在**法务/合规书面确认"当前只含 AI 图、不含 AI 文本的 PDF 不触发 20258A/办法 §5"时成立。
- 若采用，T4 的交付物是：§1.3.1 条文明细 + §1.4 矩阵 + 书面理由 + 显式图注现状证据；**不能写"法规未要求 PDF 隐式标识"**，因为官方指南明文有 PDF 方法。
- 风险：监管/平台审核按"文件元数据里有 AIGC"核验时，PDF 会被判无隐式标识；且图片自身的 XMP 在 PDF 重绘后确实丢失。

### 3.4 XMP 路线（任务书问"哪种方案能最小侵入地写入 XMP"）

- **先纠正前提**：TC260 对 PDF 的载体是 **Info 字典 `/AIGC`**（20258A §6.3），不是 XMP。XMP 是图片文件的载体（20259A §6）。
- 如果非要在 PDF 里写 XMP：需要在 Catalog 加 `/Metadata` 引用，即**重定义 Catalog 对象** + 新增 XMP 流对象，再追加 xref/trailer。工作量约为 Info 方案的 2 倍，风险更高（Catalog 字典复制、对象流/xref 流兼容），且**不消灭任何 Info 方案未消灭的合规失败**。
- pdfbox-android 可做（`PDMetadata.importXMPMetadata` + `catalog.setMetadata`），但同样要整文档加载/重写。
- **结论：不写 XMP**；按 20258A §6.3 写 Info 字典即可。

---

## 4. 推荐方案与落地步骤

### 4.1 选型

**推荐：手写 `PdfAigcInfoInjector`（append-only Info 字典增量更新），零新依赖；不写 XMP。**

理由：
1. 官方 PDF 载体就是 Info 字典，方案与条文一一对应。
2. 现有产物是 Skia 的最简形态（经典 xref、必有 Info、无 ID/Encrypt/Linearized），解析面最小。
3. pdfbox-android 的体积/维护/内存代价与"写一个键"不成比例。
4. 已有 3 份独立实现证明该路径可行；PoC 已在真实 PDF 上跑通读回。

### 4.2 落点与改动面（若热区允许）

| 文件 | 改动 | 说明 |
|---|---|---|
| **新增** `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/PdfAigcInfoInjector.kt` | 注入器 + 读回器（`readAigc(file): String?`） | 新文件，避开 5 个 M 文件 |
| `core/export/.../MistakePdfExporter.kt:128-135` | 在 `render` 之后、`temporaryPdf.length()` 与 `validatedPageCount` 之前调用注入 | 注入后的大小/页数/摘要都以最终字节为准 |
| `core/export/.../MistakePdfEligibility.kt:186` | `"mistake-export-v3"` → `"mistake-export-v4"` | 旧缓存失效（§2.4 A 方案） |
| `core/export/.../MistakePdfDelivery.kt:215-260` | （可选）子集打印缓冲后注入 | 若认定"打印到 PDF"是导出；否则登记残留 |
| `core/export/.../MinimalDocxWriter.kt` | （不在 T4 范围）补 `docProps/custom.xml` | 20258A §6.1 对称缺口，单独登记 |
| `core/export/build.gradle.kts` | **无改动**（不引库） | 不需要锁文件新增依赖 |

**热区说明**：`git status --porcelain core/export` 本日显示 5 个 M：`build.gradle.kts`、`MistakeExportJobRunner.kt`、`MistakePdfEligibilityTest.kt`、`MistakePdfPagePlannerTest.kt`、`MistakePdfPlannerGoldenTest.kt`。`MistakePdfExporter.kt` / `MistakePdfEligibility.kt` 当前不在 M 列表，但整个 `core/export` 被列为"他线在飞热区"。**本批不落代码**；若主会话确认可动，按本表执行；否则以本文登记，T4 不声称完成。

### 4.3 写入判据（避免伪造标识）

只有当 PDF 实际会渲染出 AI 生成图时才写 `/AIGC`。判据复用 `isAiGeneratedExportImage` 的口径（`AiGeneratedImageCaption.kt:29-37`）：

- `input.cleanImageLocalUri != null` 且 `questionImageRole` 为 `null` 或 `CLEAN_IMAGE` → AI；
- `input.blocks.filterIsInstance<MistakePdfBlock.Image>()` 中任一 `role` 为 `null`/`CLEAN_IMAGE`/`TUTOR_EXPLANATION` → AI；
- `input.solutionImages` 非空（只允许 `TUTOR_EXPLANATION`）→ AI；
- `questionImageRole == QUESTION_DIAGRAM` 且没有其它 AI 图 → **不写**（题目自带示意图不是模型产物）；
- `MistakePdfBlock.Figure`（程序化图形）→ **不写**（确定性代码绘制，不是 AI 生成）。

建议把该判据做成一个 `internal fun containsAiGeneratedExportContent(input): Boolean`，与图注共用 `isAiGeneratedExportImage`，避免两套口径漂移。

### 4.4 `/AIGC` 值（7 字段，官方模板）

```json
{"Label":"1","ContentProducer":"SmartMistakeBook","ProduceID":"<input.inputSha256>","ReservedCode1":"","ContentPropagator":"","PropagateID":"","ReservedCode2":""}
```

- `Label="1"`：内容含 AI 生成合成内容（与图片路径一致；`"2"/"3"` 是传播平台的"可能/疑似"语义，本方不写）。
- `ContentProducer="SmartMistakeBook"`：名称口径，与 `GeneratedFigureMetadata.kt:164` 一致；27 位编码口径仍是单源待验证（R2-U4），未采纳。
- `ProduceID=input.inputSha256`：64 位 hex，可对账到导出输入指纹；ASCII，无引号/反斜杠，PDF 字面串安全。
- 4 个可选字段写空串：本方是生成方，不是传播平台，不冒充 `ContentPropagator`；`ReservedCode1/2` 无安全数据可写。
- **与图片路径的差异**：图片路径目前只写 3 字段（`GeneratedFigureMetadata.kt:176-180`），官方模板是 7 字段。PDF 建议按 7 字段模板；图片路径的 3 字段是否足够列为 U4 复核项，不在 T4 改动。

### 4.5 注入算法（可直接照做）

1. 读文件头，要求 `%PDF-`；读尾部 `min(64KB, size)`。
2. 拒绝条件（fail-closed）：`/Encrypt`、`/Linearized`、找不到最后一个 `startxref`、找不到 `trailer`、trailer 无 `/Root`、`/Size` 非法。
3. 解析 trailer：`/Size`、`/Root`、`/Info`（本 App 必有）、`/ID`（本 App 默认无）。
4. 读 `/Info` 对象：从 xref 表取偏移（或从尾部向前搜 `<num> 0 obj`），取 `obj` 到 `endobj` 的字典体；若字典体已含 `/AIGC`，替换其值而不是追加第二个键。
5. 构造新 Info 对象：`<num> 0 obj\n<< <原字典体> /AIGC (<escaped json>) >>\nendobj\n`；转义 `\` `(` `)` 以及 `\r` `\n` `\t` `\b` `\f`；非 ASCII 时改用 UTF-16BE hex 串（本项目不需要）。
6. 在文件尾追加：新 Info 对象 → `xref\n<num> 1\n<offset 10位> 00000 n \n` → `trailer\n<< /Size <max(size,num+1)> /Root <root> 0 R /Info <num> 0 R /ID <原值（含方括号的数组，若有）> /Prev <旧startxref> >>\nstartxref\n<xrefOffset>\n%%EOF\n`。
7. 任何一步失败 → 抛 `MistakePdfExportException(GENERATED_PDF_INVALID, cause)`（或新增枚举），由调用方 fail-closed。

**为什么不用"新建对象号 + trailer /Info 指向新对象"**：也可以，但会多一个对象号、要改 `/Size`；重定义原 Info 对象更贴近"更新字典"，且 PoC 已验证。两种都符合 PDF 增量更新语义；若原 Info 字典体解析失败，回退为"新对象 + trailer /Info 指向它"更稳。

### 4.6 失败处理与可观测性

- 注入失败 = 该次导出失败（与"生成图写不进标识 = 生成失败"的既有口径一致，`GeneratedFigureMetadata.kt:78-79` 的 KDoc）。
- 不要静默降级为"无标识 PDF"；如果要保留可用性，必须把降级事件写入可见日志/诊断，并登记为合规缺口。
- 不要学 OneBox 的 `%AIGCUTF8HEX` 尾部注释兜底：它不是 PDF 标准，标准读者不会读，且增加了"看起来有标识"的假象；不消灭任何标准读者的失败。

### 4.7 批次与顺序

1. 先补 JVM 单元测试（合成 PDF 夹具）→ 红。
2. 加 `PdfAigcInfoInjector` + 调用点 → 绿。
3. 升指纹版本 → 更新 golden/断言。
4. 仪器化：真实 `PdfDocument` 产物 → 注入 → `PdfRenderer` 页数 + 读回 `/AIGC`。
5. 设备上跑打印子集路径（若决定覆盖）。
6. 复核 DOCX 对称缺口（不在本 T4）。

---

## 5. 可验证判据（读回 PDF 元数据断言）

> 本批未运行 Gradle/仪器化；以下是落地批必须执行的判据。已运行的只有 §8 的 Python PoC。

### 5.1 JVM 单元测试（`core/export/src/test`）

夹具：手工构造的最小经典 xref PDF（或从真实 Skia 产物截取的小样），覆盖：
- 有 `/Info`、无 `/ID`、无 `/Encrypt`（本 App 形态）→ 注入后 trailer `/Info` 指向新对象，`/AIGC` 可读，原 `/Producer` 保留；
- 有 `/ID` → `/ID` 原样复制；
- 无 `/Info` → 新对象 + `/Size+1`；
- 已存在 `/AIGC` → 幂等替换，不重复；
- `/Encrypt` / `/Linearized` / 坏 trailer → fail-closed（抛异常，不产出文件）；
- 非 ASCII 值 → 走 UTF-16BE hex 或明确拒绝（本项目值 ASCII，测试钉住契约）。

断言：
- `readAigc(out) == expectedJson`；
- `PdfRenderer` 页数与注入前一致（若 JVM 侧无 PdfRenderer，则用独立解析器校验 `/Root`/页树未被改）；
- 输出字节的 SHA-256 与 `writeDigest` 记录一致；
- 原 Info 键（`/Producer`）仍在。

### 5.2 仪器化测试（`core/export/src/androidTest`）

用真实的 `DeterministicMistakePdfRenderer.render` 生成一份含 AI 图的 PDF（或直接调 `MistakePdfExporter.prepare`），然后：
1. `PreparedMistakePdf.verifyIntegrity()` == true；
2. `PdfRenderer` 打开 prepared 文件，页数与渲染计划一致（注入后仍可解析）；
3. 读回 `/AIGC`：`Label=="1"`、`ContentProducer=="SmartMistakeBook"`、`ProduceID==input.inputSha256`；
4. `sha256File(prepared.file) == prepared.sha256`；
5. 负向：只含 `QUESTION_DIAGRAM`/纯文本的输入 → 读不到 `/AIGC`（不伪造标识）。

**独立读回**：测试侧可用以下任一方式，避免"写读同源"：
- `androidTestImplementation` 引 `pdfbox-android`（仅测试 APK，3.25MB；生产 APK 不受影响）→ `PDDocument.load(file).documentInformation.getCustomMetadataValue("AIGC")`；
- 或落地验收时用外部 `pypdf`/`PyMuPDF` 对拉回的产物做一次独立断言（命令见 §5.4）。
- 不建议只用一个"自己写的读回器"证明自己写的注入器；至少要有一次独立读者。

### 5.3 命令（登记，未运行）

```bash
# JVM 单测（以实际门禁脚本串行调用为准）
./gradlew :core:export:testDebugUnitTest --tests "*PdfAigc*"

# 仪器化（设备/模拟器）
./gradlew :core:export:connectedDebugAndroidTest --tests "*PdfAigc*"

# 拉回产物后独立读回（示例；需把 <file> 替换为实际路径）
python -c "import pypdf; print(pypdf.PdfReader('<file>').metadata.get('/AIGC'))"
```

### 5.4 已执行的独立读回（PoC，真实 PDF）

见 §8：`pypdf` 读回完整 `/AIGC`，`PyMuPDF` 仍能打开 20 页，原 Info 键保留。

---

## 6. 待验证项（不得当成已证）

| # | 项 | 当前证据 | 等级/状态 | 消解动作 |
|---|---|---|---|---|
| U1 | GB 45438-2025 全文与附录 E（7 字段是否必须全写、可选字段能否省略） | openstd 只有著录；`showGb` 302；TC260 旧 PDF URL 返回 23,681B HTML | **未取得** | 人工用现代浏览器/标准全文系统预览，或向 TC260 索取；落地前复核 |
| U2 | 20258A 是否直接适用于"只含 AI 图、不含 AI 文本"的 PDF | §1/§3.1 限定"文本内容"；§5.2"其他文件格式可参考处理" | **解释，未定**（中） | 法务/合规书面裁定；本文建议保守按 §6.3 写 |
| U3 | Android PDFium（`PdfRenderer`）是否接受追加的增量更新段 | PoC 用 pypdf/PyMuPDF 通过；未跑设备 | **未验证** | 仪器化用例：注入后 `PdfRenderer` 打开并比对页数 |
| U4 | 图片路径只写 3 字段 vs 官方模板 7 字段 | 官方 20259A §6 b) 是 7 字段；项目 `GeneratedFigureMetadata.kt:176-180` 是 3 字段 | **差异待复核** | 单独小批：是否补空串 4 字段；不要在本 T4 顺手改 |
| U5 | 5% 显式水印规格是否适用于 PDF 内图注 | GB 原文未核；R3 §2.2/V1 二手一致 | **待验证** | 随 U1 核 GB 附录；PDF 图注是文字提示，不是图片内水印，倾向不适用 |
| U6 | pdfbox-android 引入后的 APK 增量（R8 后） | 只有原始 AAR/BC 体积，未构建 | **未测** | 若回退到该库，先在分支构建对比 |
| U7 | `ContentProducer` 用名称 vs 27 位编码 | 办法 §5 允许"名称或者编码"；R2-U4 编码结构单源 | **口径已选名称，法律待复核** | 随 U1/法务 |
| U8 | 打印子集（`writeSubset`）是否属于"导出" | 办法 §4(2) 列举"下载、复制、导出"；打印到 PDF 未明示 | **解释** | 产品/法务裁定；本文建议至少登记 |
| U9 | DOCX 的 `docProps/custom.xml` 对称缺口 | `MinimalDocxWriter` 无 docProps | **事实，未修** | 单独批次；不在 T4 PDF 范围 |

---

## 7. 来源清单（本日实际访问/下载/执行）

**官方**
- 《人工智能生成合成内容标识办法》全文（cac.gov.cn，本日 curl 200，正文提取第四/五/十/十一条）：<https://www.cac.gov.cn/2025-03/14/c_1743654684782215.htm>
- GB 45438-2025 著录页（openstd，本日 WebFetch：现行/2025-02-28/2025-09-01）：<https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=F32EA2A561F1886CD8D606513512D547>
- TC260-PG-20258A 官方 PDF（本日下载 200，3,272,625B，20 页，sha256 `6bf6c1189208856099bfbf648bdf386da288b1df00f430502b395b874dbf3b0e`）：<https://www.tc260.org.cn/tc260/sjzn/202508/f0a84ada0c11458e9cb6da56c1cb2426/files/760387fb30e94c058c31b8c2b2230020.pdf>
- TC260-PG-20259A 官方 PDF（本日下载 200，2,190,188B，11 页，sha256 `e4f7fe820a181b2e24a5e3214b751e67c04672ec68fb4d66484da4e77b02a02f`）：<https://www.tc260.org.cn/tc260/sjzn/202508/7227bfecdf344c14abc647fbbcf69edf/files/37774d2b3149456ba7179a81bc07d3d5.pdf>
- TC260 6 项指南发布报道（安全内参，本日 WebFetch：6 份文件覆盖文本/图片/音频/视频元数据、安全防护、检测框架）：<https://www.secrss.com/articles/82518>
- TC260 指南页（本日 WebFetch 只显示 2026 条目，未含 2025-08 列表）：<https://www.tc260.org.cn/portal/article/303/533e0475b511479aaadf5cdd81182caf>

**GBT/平台/第三方（用于交叉与实现参考）**
- GB 45438 附录 E 字符串的第三方引用与 20258A 编号/URL：`wiltodelta/remove-ai-watermarks` `docs/research-sweep-2026-09.md`、`docs/watermarking-landscape.md`、`docs/43.01-research/sources.md`（GitHub raw，本日读取）
- OneBox（Apache-2.0，903 stars）：`feature/common/src/main/java/com/shifenmiao/common/file/AigcFileMetadataWriter.kt`（PDF 增量注入）、`AigcInfoGenerator.kt`（7 字段 JSON）——<https://github.com/wangzhishou/OneBox>
- ArkPilot（**无 license**，只读参考）：`Agent/entry/src/main/ets/backend/AigcPdfInjector.ets`、`AigcProvenance.ets`——<https://github.com/midori52000/ArkPilot>
- YITIJI（**无 license**，只读参考）：`services/api/src/common/pdf/aigc-label.ts`（pdf-lib Info `/AIGC`）——<https://github.com/wanglei581/YITIJI>
- briefloop（MIT）：`src/briefloop/export_labeling.py`（pypdf `add_metadata({'/AIGC': json})`）——<https://github.com/Stahl-G/briefloop>
- pdfbox-android：<https://github.com/TomRoush/PdfBox-Android>；Maven Central：<https://repo1.maven.org/maven2/com/tom-roush/pdfbox-android/2.0.27.0/pdfbox-android-2.0.27.0.aar>、`...pom`
- AOSP `PdfDocument.java`：<https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/graphics/java/android/graphics/pdf/PdfDocument.java>
- AOSP `PdfDocument.cpp`（JNI，`SkPDF::MakeDocument(stream)`）：<https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/libs/hwui/jni/pdf/PdfDocument.cpp>
- Skia `SkPDFDocument.cpp` / `SkPDFMetadata.cpp` / `SkPDFDocument.h`：<https://raw.githubusercontent.com/google/skia/main/src/pdf/SkPDFDocument.cpp> 等

**项目内（本日读取）**
- `core/export/build.gradle.kts`（无 PDF 第三方依赖）
- `core/export/.../DeterministicMistakePdfRenderer.kt:62`、`:151-156`（`PdfDocument` + `writeTo`）
- `core/export/.../MistakePdfExporter.kt:114-141`（prepare 管线）、`:295`（findExisting）、`:358-378`（publish）
- `core/export/.../MistakePdfEligibility.kt:185-186`（`mistake-export-v3` 指纹）、`:318-370`（导出输入字段）
- `core/export/.../MistakePdfDelivery.kt:208-260`（整份复制 / 子集重绘）
- `core/export/.../MinimalDocxWriter.kt:75-90`（OPC 包，无 docProps）
- `core/export/.../AiGeneratedImageCaption.kt:27`（显式图注）
- `core/data/.../capture/GeneratedFigureMetadata.kt:154`（XMP 命名空间）、`:164`（producer）、`:176-180`（3 字段 JSON）、`:19-21`（PDF 丢元数据边界）
- `git status --porcelain core/export`（5 个 M，见 §4.2）

---

## 8. 执行记录（本日实际运行）

### 8.1 官方指南下载与 hash

```
$ sha256sum tc260-20258a.pdf
6bf6c1189208856099bfbf648bdf386da288b1df00f430502b395b874dbf3b0e *tc260-20258a.pdf
$ sha256sum tc260-20259a.pdf
e4f7fe820a181b2e24a5e3214b751e67c04672ec68fb4d66484da4e77b02a02f *tc260-20259a.pdf
```

- 20258A：`file` → PDF 1.7，20 页；PyMuPDF metadata `producer = Microsoft: Print To PDF`，`creationDate = D:20250828234800+08'00'`（与 2025-08-28 发布日一致）。
- 20259A：11 页，PDF 1.7。
- 第三方 `fortil/watermark-detector/docs/43.01-research/sources.md` 引用的 hash 前缀分别为 `6bf6c118…3b0e`、`e4f7fe82…a02f`，与本日下载一致。
- GB 45438：`https://www.tc260.org.cn/upload/2025-03-15/1742009439794081593.pdf` 返回 **200 text/html 23,681 字节**（登录壳），不是 PDF；openstd `showGb?type=online` 返回 **302**。

### 8.2 PDF 增量写入 + 独立读回 PoC（Python，内存中完成）

对 `tc260-20258a.pdf`（真实经典 xref PDF，trailer `/Info 747 0 R /Root 1 0 R /Size 748`）：

```
size,root,info = 748 1 747
info object offset 3257361
info object head: b"\n747 0 obj\n<<\n/Author ()\n/CreationDate (...)\n/Producer (Microsoft: Print To PDF)\n/Title (...)"
appended bytes 552 output size 3273177
pypdf pages: 20
pypdf /AIGC: {"Label":"1","ContentProducer":"SmartMistakeBook","ProduceID":"6bf6c1189208856099bfbf648bdf386da288b1df00f430502b395b874dbf3b0e","ReservedCode1":"","ContentPropagator":"","PropagateID":"","ReservedCode2":""}
pypdf metadata keys: ['/Author', '/CreationDate', '/ModDate', '/Producer', '/Title', '/AIGC']
pymupdf pages: 20 producer: Microsoft: Print To PDF
```

**证明**：append-only Info 增量更新在真实 PDF 上可写、可读、不破坏页树、保留原 Info 键。**未证明**：Android PDFium/`PdfRenderer` 的接受度（U3）、Skia 实际产物的形态（本次用 Microsoft Print to PDF；Skia 形态由 §2.2 源码证据支持）。

### 8.3 pdfbox-android 构件实测

```
$ ls -l pdfbox-android-2.0.27.0.aar
3,254,019 bytes
$ unzip -l aar/classes.jar | grep -c "\.class"
902
$ du -sb aar/assets
4,634,456
$ javap -classpath out com.tom_roush.pdfbox.pdmodel.PDDocumentInformation | grep -E "setTitle|setProducer|setCustomMetadataValue"
  public void setTitle(java.lang.String);
  public void setProducer(java.lang.String);
  public void setCustomMetadataValue(java.lang.String, java.lang.String);
$ javap -classpath out com.tom_roush.pdfbox.pdmodel.common.PDMetadata
  public void importXMPMetadata(byte[]) throws java.io.IOException;
$ javap -classpath out com.tom_roush.pdfbox.pdmodel.PDDocumentCatalog | grep -i metadata
  public com.tom_roush.pdfbox.pdmodel.common.PDMetadata getMetadata();
  public void setMetadata(com.tom_roush.pdfbox.pdmodel.common.PDMetadata);
```

- Maven `lastUpdated=20230102065914`；GitHub 最后提交 `2023-12-29T03:06:16Z`；`pushed_at=2024-03-18`；open issues 125；license Apache-2.0。
- AAR manifest `minSdkVersion=14`；README 要求 `PDFBoxResourceLoader.init(getApplicationContext())`。
- 只有 2 个 class 引用 BouncyCastle（`PublicKeySecurityHandler`、`SecurityProvider`），但 POM 仍传递三件 BC 1.72。

### 8.4 项目现状核对

```
$ git status --porcelain core/export
 M core/export/build.gradle.kts
 M core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportJobRunner.kt
 M core/export/src/test/kotlin/.../MistakePdfEligibilityTest.kt
 M core/export/src/test/kotlin/.../MistakePdfPagePlannerTest.kt
 M core/export/src/test/kotlin/.../MistakePdfPlannerGoldenTest.kt

$ grep -rn "pdfbox\|PdfBox" --include="*.kts" --include="*.toml" --include="*.kt" core/ app/ feature/ | head
（无命中）
```

---

## 9. 一句话给主会话

**PDF 隐式标识不是"法规未要求"：TC260-PG-20258A §6.3 明确要求在 Info 字典写 `/AIGC({7字段JSON})`；当前 PDF 只含 AI 图、不含 AI 生成文本，属于两份指南之间的范围空档，不是豁免。推荐零依赖的手写增量 Info 注入（PoC 已读回通过），在 `MistakePdfExporter.prepare` 的 render 与 digest 之间调用；同时升 `mistake-export-v3 → v4` 失效旧缓存。风险主要是 Android PDFium 对增量段的接受度与缓存/打印子集两条旁路，需设备用例闭环。**

---

## 10. 批次 23 T4 落地裁定与复核补记（2026-10-26，本轨追加）

> 本节由 T4 实施轨读完 R1 与相关源码后追加；不改写上文结论，只记录本批处置与对适用面的一处修正。

### 10.1 本批不落代码（保守分支）

- 本批实施纪律为"T4 只登记不动导出链"（主会话下发的共享纪律原文）；`core/export` 当前 5 个 M 文件（`build.gradle.kts`、`MistakeExportJobRunner.kt`、`MistakePdfEligibilityTest.kt`、`MistakePdfPagePlannerTest.kt`、`MistakePdfPlannerGoldenTest.kt`），且计划 §0 的干净落点（`.zcode/plans/plan-2026-10-26-batch-23-device-gates-lockfiles.md:8`）不含 `core/export`。
- 因此 T4 **不新增** `PdfAigcInfoInjector`、不改 `MistakePdfExporter.prepare`、不升 `mistake-export-v3`、不写读回测试。合规状态如实登记为"PDF 无文档级隐式标识"，见 `docs/known-defects.md` KD-75；**不声称合规，也不声称"法规未要求"**。

### 10.2 任务书"写文档级 XMP"的前提需纠正

任务书原文是"在 core/export 的 PDF 输出里写文档级 XMP（字段按 R1 配方）"，与 R1 §0.4/§3.4 冲突：TC260-PG-20258A §6.3 规定 PDF 的载体是 **Document Information Dictionary** 的 `/AIGC`；XMP 是 20259A 的**图片文件**载体，且本 App 的 Skia 产物默认没有 `/Metadata` 流（§2.2）。若将来落地，应按 Info 字典写（§4.5 算法），**不要写 XMP**——写 XMP 要重定义 Catalog + 新增流对象，风险/工作量约 2 倍，且不消灭 Info 方案未消灭的失败。R1"不写 XMP"的结论维持不变。

### 10.3 复核新发现（高）：写入判据"仅 AI 图"漏掉模型解析文本

R1 §1.4（:92）断言"当前 PDF 无 AI 生成文本块（正文为题目/选项/备注/OCR 转录）"，§4.3（:295-305）据此把 `/AIGC` 的写入条件限定为"实际会渲染出 AI 生成图"。逐文件读取后，该前提**不被代码支持**：

1. `CaptureParseInput` 在 `OpenAiModelTaskAdapters.kt:64` 经 `payload.toCapturedDocument(...)` 产出文档。
2. 模型返回的 `"paragraph"` 在 `OpenAiModelResponseParsers.kt:190` 变成 `ContentBlock.Paragraph`；`"figure_request"`（`:218`）与 `"diagram_note"`（`:235`）也变成 `ContentBlock.Paragraph`（正文为"图示：…"占位）。
3. 同一 parser 给每个块写 `provenance = QuestionBlockProvenance.MODEL_DOCUMENT_PARSE`（`:261`）；枚举定义在 `CapturedQuestionDocument.kt:210`，证据列表字段在 `:17`。提交校验只要求 `reviewStatus ∈ {LOCAL_POLICY_ACCEPTED, USER_CONFIRMED}`（`:359-362`），不要求 provenance 改变；因此**已提交**的文档仍可带 `MODEL_DOCUMENT_PARSE` 证据。
4. 导出资格层只取 `state.questionDocument.document.blocks`（`MistakePdfEligibility.kt:118-119`），只把 `blocks` 传进 `MistakePdfExportInput`（`:170`）；`MistakePdfExportInput`（`:318-370`）没有 provenance 字段。`core/export/src/main` 对 `blockEvidence`/`QuestionBlockProvenance` 零命中（本日 grep），`core/export` 内也没有任何 `MODEL_DOCUMENT_PARSE` 用法；测试夹具只构造 `USER_CORRECTION`/`USER_TRANSCRIPTION`，因此"模型解析文本进入导出"这条路径完全未被现有测试覆盖。

**后果**：只要题面来自 CAPTURE_PARSE，PDF 就可能含模型产出的文本块，而导出层无法区分。按"仅 AI 图"判据写 `/AIGC` 会漏掉这类 PDF；反之若一律写，又可能把纯用户文本标成 AI 生成（《办法》第十条）。因此落地前必须先解决 provenance 传递，不能在"PDF 无模型文本"这一未证前提上写判据。

**修正后的写入判据**（替代 §4.3 的单一口径）：`containsAiGeneratedExportContent = isAiGeneratedExportImage(any role) || documentHasModelParsedText`。第二项的实现二选一：(a) 在 `MistakePdfExportInput` 增加块级 provenance（并同步 `inputSha256`/golden）；(b) 在资格层先派生"文档含 `MODEL_DOCUMENT_PARSE` 证据"的布尔值随输入传递。两种都改 `core/export`，本批不做。

**范围解释仍开放**：模型结构化转写是否构成 20258A 的"生成合成文本内容"属于 U2 同类解释，本轨不裁定；但保守路径不能以"没有模型文本"作为前提。

### 10.4 若热区空出后的最小落地顺序（登记，未执行）

1. 先写 JVM 夹具测试（合成经典 xref；有/无 `/Info`、有 `/ID`、已存在 `/AIGC`、`/Encrypt`、`/Linearized`、坏 trailer）→ 红。
2. 新增 `PdfAigcInfoInjector`（append-only；7 字段 JSON；ASCII；幂等替换；fail-closed）→ 绿。
3. 在 `MistakePdfExporter.prepare` 的 `render` 之后、字节上限/`validatedPageCount`/`writeDigest` 之前调用（§2.3）。
4. 升 `mistake-export-v3 → v4`（`MistakePdfEligibility.kt:186`），更新受影响断言/金样；planner golden 只哈希计划结构（`MistakePdfPlannerGoldenTest.kt:115-116`），预计不与注入字节冲突，但资格层指纹测试会受影响。
5. 仪器化：真实 `PdfDocument` 产物 → 注入 → `PdfRenderer` 页数 + 独立读者读回 `/AIGC`；负向（纯 `QUESTION_DIAGRAM`/用户文本且无模型解析文本）读不到。
6. 若"打印到 PDF"被认定为导出，`MistakePdfDelivery.writeSubset` 需缓冲后注入，否则登记残留（U8）。
7. DOCX 的 `docProps/custom.xml` 对称缺口另案（U9）。

### 10.5 本轨实际执行与未执行

- 执行：读取 R1 全文、上述源码文件、`git status --porcelain core/export`（5 个 M）；未运行 Gradle、未运行设备、未写代码、未写测试。
- 未执行：§5 的全部判据、§8.2 PoC 的复跑、Android PDFium 接受度（U3）。
