# R2：隐式标识（G4）元数据字段结构与写入选型（驱动 T5）

- 日期：2026-10-25（批次 21）
- 性质：只研究不落地；所有检索/下载/反编译均于本日实际执行，来源逐条列出
- 上游：批次 20 R3 合规研究 `2026-10-25-ai-content-labeling-compliance.md` §6-G4（"TC260 XMP 字段结构二手待验证"= 其 V2 项）；本文把 V2 从单一来源升级为多源字段表，并回答"用什么写"
- 热区纪律：本文档为新建文件，未触碰任何他线在飞文件；未运行 Gradle（门禁纪律）

---

## 0. 结论摘要

1. **推荐：不引任何新库。** 用项目已锁定的 `androidx.exifinterface:exifinterface:1.4.1`（`gradle/libs.versions.toml:28`）的 **TAG_XMP 写入路径**承载 TC260 规范结构：`setAttribute(TAG_XMP, 包)` + `saveAttributes()`。该版本对 JPEG 写独立 APP1 段、对 PNG 写 iTXt 块（keyword `XML:com.adobe.xmp`）——**已对本项目实际发布的构件做字节码实测**（§3.1），非文档推断。
2. **任务前提纠正（重要）**：任务书前提"androidx.exifinterface 已知无 XMP 写"**不成立**——批次 20 R3 文档同句（"exifinterface 无公开 XMP 写 API"）亦不成立。反证见 §3.1（1.4.1 字节码含 public `TAG_XMP`、`writePngXmpItxtChunk`）与 §3.2（androidx 主分支 javadoc 明文写 XMP 写回规则）。
3. **需要"自写"的只有 XMP 包字符串本身**（约 20 行 Kotlin：xpacket 头尾 + x:xmpmeta/rdf:RDF 模板 + TC260 命名空间 + 三必填字段 JSON），容器写入（APP1/iTXt）全部由 exifinterface 完成。工作量合计 ≤0.5 天含测试。
4. **只写三个必填字段**：`Label`、`ContentProducer`、`ProduceID`（多源一致的必选性证据）。本 App 是生成方不是传播平台，《办法》第六条传播要素（ContentPropagator/PropagateID）与预留字段（ReservedCode1/2）不写。
5. **如实登记的未决点**：TC260 官方指南 PDF（TC260-PG-20259A）官网下载有会话门槛，**本日未能取得官方原文**；字段名/含义/必选性已多源交叉验证，但**逐格式载体在两份二手实现文之间冲突**（XMP vs EXIF UserComment/tEXt，§2.3）。推荐配方按"XMP 载体"落地并留一行切向空间；落地前建议人工登录官网下载 PDF 复核一次（一次人工动作可消掉全部载体歧义）。

---

## 1. 义务与字段结构（研究一）

### 1.1 法规层：义务三要素（官方原文，本日直接抓取 cac.gov.cn 全文）

《人工智能生成合成内容标识办法》（国信办通字〔2025〕2号，2025-09-01 施行）——**本日经 web_reader 取得全文**，关键条文原样引用：

- **第五条**（生成方隐式标识义务）："服务提供者应当在生成合成内容的**文件元数据**中添加隐式标识，隐式标识包含**生成合成内容属性信息、服务提供者名称或者编码、内容编号**等制作要素信息。鼓励服务提供者在生成合成内容中添加数字水印等形式的隐式标识。"
- **第六条第二款**（传播方叠加要素——解释了字段表为何有 7 项）："有前款第一项至第三项情形的，应当在文件元数据中添加生成合成内容属性信息、**传播平台名称或者编码、内容编号**等传播要素信息。"
- **第十一条**："服务提供者开展标识活动的，还应当符合相关法律、行政法规、部门规章和**强制性国家标准**的要求。"——即 GB 45438-2025（强制国标，2025-09-01 与《办法》同步施行）。

→ 字段表的映射：`Label`=属性信息；`ContentProducer`=服务提供者名称或编码；`ProduceID`=内容编号；`ContentPropagator`/`PropagateID`=第六条传播要素（仅传播平台填）；`ReservedCode1/2`=预留/防篡改。

### 1.2 标准与指南层

| 文件 | 性质 | 状态 | 来源 |
|---|---|---|---|
| GB 45438-2025《网络安全技术 人工智能生成合成内容标识方法》 | **强制性国标**（第十一条的"符合"对象） | 现行；全文无公开在线版（openstd 只有著录项；标准全文系统在线预览未开放本标准正文） | [openstd 著录页](https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=F32EA2A561F1886CD8D606513512D547)（批次 20 已核）；本日未重试正文 |
| TC260-PG-20259A《网络安全标准实践指南——人工智能生成合成内容标识方法 文件元数据隐式标识 图片文件》 | TC260 配套实践指南（2025-08-28，文号网安秘字〔2025〕118号，与 5 项同批；**"结合 GB 45438—2025 编制"**） | 现行；覆盖 HEIF/HEIC、JPEG/JPG、PNG、TIFF、WebP、GIF；**官方 PDF 未能取得**（见 §2.4） | [安全内参发布文](https://www.secrss.com/articles/82518)（本日 WebFetch 核：文号/附件 URL 在列）、[TC260 指南页](https://www.tc260.org.cn/portal/article/303/533e0475b511479aaadf5cdd81182caf)（本日 curl 实测 302 回首页壳） |

**官方 PDF 获取尝试记录（如实）**：附件 2（6 指南文本 RAR，`https://www.tc260.org.cn/upload/2025-08-28/1756396168261019065.rar`）curl 两次（无 Referer / 带 Referer）均返回 23,681 字节 HTML 登录壳页（tc260-webapp/login 结构）——官网对附件下载有会话门槛；gcmark.com 首页 curl 返回 538 字节 JS 壳。3 轮关键词内未找到官方文本的其他公开镜像 → **停止检索**，按"多源二手交叉"降级处理并在 §5 列待验证。

### 1.3 字段表（AIGC 隐式标识元数据）

JSON 结构（作为整体挂在 XMP 属性 `AIGC` 上；PNG 文本块口径则作为 tEXt 的 value）：

```json
{"Label":"1","ContentProducer":"…","ProduceID":"…","ReservedCode1":"…","ContentPropagator":"…","PropagateID":"…","ReservedCode2":"…"}
```

| 字段 | 含义（多源转述归纳） | 必选性 | 本 App 取值建议 | 证据等级 |
|---|---|---|---|---|
| `Label` | 内容是否 AI 生成合成；**"1"=AI 生成合成**；"2"/"3"为传播平台专用（可能/疑似） | **必填** | `"1"` | 高：腾讯云 CI + 阿里云 + 掘金(腾讯云生态) 三独立源一致 |
| `ContentProducer` | 服务提供者**编码或名称**；阿里云给出 27 位编码结构（格式码"00"+主体类型+统一社会信用代码+…，示例 `001191******M000100Y43`） | **必填** | `"SmartMistakeBook"`（名称口径，规避编码结构细节） | 高（3 源）；编码结构为**阿里云单源转述，待验证** |
| `ProduceID` | 内容制作编号（制作方溯源唯一识别编号） | **必填** | `figureId`（与现有 `GeneratedFigureProvenance.figureId` 同源，可对账） | 高（3 源） |
| `ReservedCode1` | 生产方提供的防数据篡改标识（预留；阿里云示例为 MD5 形态） | 选填 | **不写**（自写时只写规范必填字段） | 高（2 源：腾讯云、掘金均"选填"） |
| `ContentPropagator` | 传播方业务标识（《办法》第六条主体） | 选填 | **不写**（本 App 本地优先、无传播场景） | 高 |
| `PropagateID` | 传播编号 | 选填 | **不写** | 高 |
| `ReservedCode2` | 传播方防篡改标识（预留） | 选填 | **不写** | 高 |

### 1.4 命名空间与前缀

- URI `http://www.tc260.org.cn/ns/AIGC/1.0/`，前缀 `TC260`，属性名 `AIGC`（值为上述 JSON 字符串）。
- **证据等级：单一来源（[博客园实现文](https://www.cnblogs.com/aopstudio/p/19075002)，自述改写自官方指南示例代码），待验证**。批次 20 V2 的结论维持"待验证"，但其余字段结构已升级为多源。

### 1.5 逐格式载体：两份实现文冲突（如实呈现）

| 格式 | 口径 A：XMP 载体（[博客园](https://www.cnblogs.com/aopstudio/p/19075002)，2025-09） | 口径 B：EXIF/文本块载体（[CSDN 实现文](https://blog.csdn.net/u010131870/article/details/150218572)，2025-08） |
|---|---|---|
| JPEG | APP1 独立段（XMP 规范 Part 3 §3.3.2 位置），TC260 命名空间属性 `AIGC`=JSON | **EXIF `UserComment`** 写 JSON |
| PNG | iTXt（keyword `XML:com.adobe.xmp`） | **tEXt 块 key=`AIGC`**（value=JSON）或私有块 type=`AIGC` |
| GIF | —（指南未提及） | 注释扩展块 Comment Extension |
| WebP/HEIF | — | CSDN 称与 JPEG 同走 `UserComment` |

- 两文**都声称改写自官方指南**，字段 JSON 一致，载体不同。旁证：[aicheck365 拆解文](https://www.aicheck365.com/blog/ai-image-metadata-guide)称"国产平台惯例是在 EXIF UserComment 写入 JSON"；抖音平台规范（chinalawtranslate 转述）允许"新增扩展字段 aigc"或"已有 comment 字段"两种——说明**业界确有双载体实践**。冲突无法在官方原文缺失下载的情况下裁决。
- CSDN 文"PNG（自 1.5.0 起）"字样含义未明（该文自己未解释）——登记待验证，不影响本 App（vault 只产 PNG/JPEG，且 iTXt 是 PNG 标准块）。
- **对判定的影响**：推荐配方（§4）选 XMP 口径为主，理由：(a) XMP 是跨格式通用载体且 exifinterface 现成支持（零新代码容器）；(b) XMP 包对 JPEG/PNG 双格式行为一致；(c) 即便官方最终口径偏 UserComment，XMP 亦为"文件元数据中的隐式标识"，与 GB 结构要求不抵触，一行即可补 UserComment 双写。

---

## 2. 现状与本格式的接缝

- 现状实现：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadata.kt:61-72` 写 EXIF 四字段（ImageDescription/Software/Artist/UserComment），读回 fail-closed（`:75-96`）；写入点 `AndroidCanonicalAssetVault.kt:373`（写失败=生成失败）。
- 本格式边界：vault 只产 **PNG 或 JPEG**（`AndroidCanonicalAssetVault.kt:358-364`，`Bitmap.CompressFormat.JPEG/PNG` 二选一）——WebP XMP 不可写、HEIF 只读等库能力缺口**不在本格式路径上**。
- 现有 EXIF 四字段**保留不动**（自读回判据与对账依赖它们），TC260 结构是**新增一层**，不是替换。
- `GeneratedFigureMetadata.kt:17` 注释声称 ExifInterface "XMP 有读写支持"——本次实测证明该注释**方向正确**（批次 20 R3 的相反结论有误），落地时顺手修正注释与 R3 记录的口径。

---

## 3. 库选型（研究二）

### 3.1 关键实测：`androidx.exifinterface:1.4.1`（项目已锁定版本）的 XMP 写入能力

**实测命令与输出（本日执行）**：

```
$ grep -n "exifinterface" gradle/libs.versions.toml
28:exifinterface = "1.4.1"                      # 项目锁定版本
82:androidx-exifinterface = { module = "androidx.exifinterface:exifinterface", ... }

$ curl -sL -o exif-141.aar https://dl.google.com/android/maven2/androidx/exifinterface/exifinterface/1.4.1/exifinterface-1.4.1.aar   # size=75523
$ unzip exif-141.aar classes.jar && unzip classes.jar 'androidx/exifinterface/media/ExifInterface.class'
$ javap -p -classpath x androidx.exifinterface.media.ExifInterface | grep -i xmp
  public static final java.lang.String TAG_XMP;            # ← public 常量
  private static final int XMP_HANDLING_TIFF_700_ONLY;
  private static final int XMP_HANDLING_PREFER_SEPARATE;
  private static final int XMP_HANDLING_PREFER_TIFF_700_IF_PRESENT;
  static final byte[] PNG_ITXT_XMP_KEYWORD;                # "XML:com.adobe.xmp\0\0\0\0\0"
  private static final byte[] IDENTIFIER_XMP_APP1;
  private ... mXmpFromSeparateMarker;
  private static int getXmpHandlingForImageType(int);
  private void writePngXmpItxtChunk(...ByteOrderedDataOutputStream) throws java.io.IOException;   # ← PNG iTXt 写入方法在 1.4.1 里
$ javap -public ... | grep -E "TAG_XMP|setAttribute|saveAttributes|getAttributeBytes"
  public static final java.lang.String TAG_XMP;
  public java.lang.String getAttribute(java.lang.String);
  public void setAttribute(java.lang.String, java.lang.String);
  public void saveAttributes() throws java.io.IOException;
  public byte[] getAttributeBytes(java.lang.String);
```

**androidx 主分支源码交叉**（`gh api repos/androidx/androidx/contents/exifinterface/.../ExifInterface.java`，本日抓取）：

- 类 javadoc（103–127 行区）："Supported for writing: JPEG, PNG, WebP."；XMP 规则：JPEG 无任何 XMP 时 `setAttribute(TAG_XMP)` 后保存为**独立 APP1 段**（"If XMP is not present in either location, and is added with setAttribute, it is written as a standalone segment"，符合 XMP Spec Part 3 §3.3.2）。
- `getXmpHandlingForImageType`（4281–4305 行区）：`IMAGE_TYPE_JPEG → PREFER_TIFF_700_IF_PRESENT`（无 700 值即走独立段）；`IMAGE_TYPE_PNG → PREFER_SEPARATE`（走 iTXt）；`IMAGE_TYPE_WEBP → TIFF_700_ONLY`（源码自注："WebP supports a separate XMP chunk…ExifInterface doesn't currently read or write it"）。
- PNG 读路径（6398–6418 行区）识别 `PNG_ITXT_XMP_KEYWORD`；写路径（6676 行区起）`writePngXmpItxtChunk`。常量定义注释："The keyword and 5 null bytes defined by XMP spec part 3 table 9 (section 1.1.5)"（3115 行）。
- `gh api search/code` 确认 `TAG_XMP` 同时出现在 `exifinterface` 的 androidTest（官方自测覆盖）。

**判定依据级别**：构件字节码（我们实际发布的版本）+ 官方源码，**强**。未做项：真机/仪器化 round-trip 未运行（Gradle 禁令）——写入配方落地时以仪器化测试闭环（§4.3，命令已给不运行）。

### 3.2 候选判定表

| 候选 | 版本/最后发布 | License | XMP 写入能力 | Android/minSdk 兼容 | 维护度 | 判定 |
|---|---|---|---|---|---|---|
| **androidx.exifinterface**（项目在用） | 1.4.1 已锁；上游已出 1.4.2 / 1.5.0-alpha01（Google Maven maven-metadata lastUpdated=2026-10-21，本日 curl 实测） | Apache-2.0 | **JPEG APP1 + PNG iTXt 写入，1.4.1 字节码实测**（§3.1） | Google 官方库，minSdk 23 = 项目现状 | 活跃（Google 维护，2026-10 仍有更新） | ✅ **采纳** |
| Adobe XMPCore Java（`com.adobe.xmp:xmpcore`） | 6.1.11（2020-11-17，此后 6 年无版；Maven Central search API 本日实测全版本表） | POM 标 BSD-3-Clause；**历史混乱**：5.1.3 无 license 文件（[XMP-Toolkit-SDK issue #5](https://github.com/adobe/XMP-Toolkit-SDK/issues/5)）、6.1.10 被 Apache Tika 判不合规（[TIKA-3204](https://issues.apache.org/jira/browse/TIKA-3204)）、<5.1.3 有 XXE 漏洞（Snyk） | 仅 XMP **序列化**；不含 JPEG 段/PNG 块容器写入（还得自写容器） | 纯 Java；本日实测 class 文件 major=0x32（Java 6），Android 可运行 | 死：无官方源仓库（Adobe GitHub 只有 C++ SDK，issue #5/#94 询问 Java 版下落） | ❌ 不引：容器能力与 exifinterface 重叠、剩余价值（序列化）只需 20 行模板替代；license 溯源与供应链面均为负资产 |
| Ashampoo/xmpcore（KMP 移植；已迁 [StefanOltmann/xmpcore](https://github.com/Ashampoo/xmpcore)） | `com.ashampoo:xmpcore:1.6.1`（README 版本；57 commits；**原仓库已弃用**，本日 WebFetch 核实） | BSD（同 Adobe XMP SDK） | 仅序列化，且 README 自述**移除了 ByteArray/InputStream 读写** | KMP 宣称支持 Android | 个人维护、弃用迁移中 | ❌ 同上 + 弃用状态 |
| Apache Commons Imaging | 1.0.0-alpha6（2025-04-27，Maven Central API 实测；此前 alpha5=2024-04） | Apache-2.0 | 官方 [formatsupport 页](https://commons.apache.org/imaging/formatsupport.html)（本日检索引述，2025-04 更新）：可**嵌入** XMP 的只有 **GIF、PNG、TIFF；JPEG 不可**（JPEG 只读 XMP） | 纯 Java | Apache 活跃 | ❌ JPEG 缺口正中本格式路径；引整库做单点，违背最小面 |
| metadata-extractor（`com.drewnoakes`） | 2.19.0（2023-11-20，Maven Central API 实测） | Apache-2.0 | **只读**——README 原文 "a Java library **for reading** metadata from media files"，无任何写 API，无配套写入库（本日 WebFetch README 核实） | 纯 Java | 缓慢但稳定 | ❌ 只读，不满足"写"义务 |
| drewnoakes/adobe-xmp-core | GitHub "Reconstructed history … from Maven sources"（非发布构件） | — | — | — | 源码重建参考物 | ❌ 非正式发布物，不进依赖 |
| ihmily/metatoolkit（Python，旁证候选） | GitHub 在维护 | — | 用**自有命名空间**（`Xmp.metatoolkit.*`）非 TC260 结构（本日 clone 后 grep 全库无 `ContentProducer/ProduceID/TC260` 命中） | — | — | ❌ 非 kosher TC260 实现；从证据链剔除 |
| 自写 PNG chunk / JPEG 段级容器解析+写入 | — | — | — | — | — | ❌ **消灭不了任何 exifinterface 未消灭的失败**（容器写入它已做），新增即违反"每新增必指认"；仅在 exifinterface 被证伪时回退考虑 |

### 3.3 minSdk 结论

全部候选中唯一采纳者 exifinterface 已是项目依赖（minSdk 23，`core/data/build.gradle.kts:18`）；其余候选无一被采纳，minSdk 兼容性仅作记录（xmpcore Java 6 字节码实测可运行，但无意义）。

---

## 4. 推荐与字段写入配方（研究三 = 只自写"包"，不自写容器）

### 4.1 规范依据（自写包的每一段都有出处）

| 包片段 | 依据 |
|---|---|
| `<?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>` … `<?xpacket end="w"?>` | XMP Specification Part 1（序列化：xpacket 头尾；id 为规范固定 magic） |
| `<x:xmpmeta xmlns:x="adobe:ns:meta/">` + `<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">` | XMP Part 1 标准骨架 |
| `xmlns:TC260="http://www.tc260.org.cn/ns/AIGC/1.0/"` + `<TC260:AIGC>{json}</TC260:AIGC>` | TC260 指南字段结构（§1.4，单一来源待验证——落地前人工下载 PDF 复核） |
| JSON 三必填字段（Label/ContentProducer/ProduceID） | 《办法》第五条三要素 + 腾讯云/阿里云/掘金三源必选性（§1.3） |
| JPEG→APP1、PNG→iTXt 的容器落位 | 由 exifinterface 1.4.1 完成（§3.1 实测），本方不写容器字节 |

### 4.2 配方骨架（表述用；实际编码在落地批次做，含先红后绿测试）

改动面仅 `GeneratedFigureMetadata.kt`（`core/data/capture`——开工时复核脏区，当前不在禁改名单）：

```kotlin
// 1) 包构造（约 15 行）：数据类 → 3 字段 JSON（转义 & < >）→ 模板
private fun aigcJson(p: GeneratedFigureProvenance): String =
    """{"Label":"1","ContentProducer":"$PRODUCER_NAME","ProduceID":"${p.figureId}"}"""

private fun aigcXmpPacket(json: String): String = """
    <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
    <x:xmpmeta xmlns:x="adobe:ns:meta/">
     <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
      <rdf:Description rdf:about="" xmlns:TC260="http://www.tc260.org.cn/ns/AIGC/1.0/">
       <TC260:AIGC>${xmlEscape(json)}</TC260:AIGC>
      </rdf:Description>
     </rdf:RDF>
    </x:xmpmeta>
    <?xpacket end="w"?>""".trimIndent()

// 2) 写入（在 embedGeneratedFigureMetadata 现有四字段之后追加 2 行）：
exif.setAttribute(ExifInterface.TAG_XMP, aigcXmpPacket(aigcJson(provenance)))
// （saveAttributes() 已有，不改）

// 3) 读回（readGeneratedFigureMetadata 内追加）：
//    json = exif.getAttribute(ExifInterface.TAG_XMP)?.substringAfter("<TC260:AIGC>")
//             ?.substringBefore("</TC260:AIGC>")，解析 Label=="1" 与 ProduceID
//    aiGenerated 判据扩为：现有判据 || xmp Label=="1"（仍 fail-closed）
```

- `ContentProducer` 取值：`"SmartMistakeBook"`（名称口径，§1.3）；若后续监管口径要求编码，换 27 位结构（单源待验证项）。
- 传播字段与 ReservedCode 不写（§0.4）。
- PNG/JPEG 两格式同一配方（exifinterface 分流容器，调用方无感知）。

### 4.3 验收判据（落地批执行；本批 Gradle 禁令未运行）

- 先红后绿：扩展 `GeneratedFigureMetadataInstrumentedTest`（既有 `core/data/src/androidTest/.../capture/` 轨）加 PNG 与 JPEG 两条 round-trip：写入后 `metadata-extractor`（test 依赖或人工 exiftool）可读到独立 XMP 段/iTXt 且 `TC260:AIGC` JSON 三字段齐；读回 `aiGenerated==true`。
- 命令（登记待运行）：`./gradlew :core/data:connectedLocalFirstDebugAndroidTest --tests "*GeneratedFigureMetadata*"`（以门禁脚本实际串行调用为准）。
- 本批可复核的静态证据即 §3.1 字节码清单（已执行）。

### 4.4 备选（若人工复核官方 PDF 后载体的定为 UserComment 口径）

同一 JSON 一行写入 `TAG_USER_COMMENT`（现有管线零新增 API）即可满足 CSDN 口径；XMP 层保留无冲突。此为"一行切向"，不构成现在就双写的理由（避免无失败指向的多余机制）。

---

## 5. 待验证清单（继承批次 20 V2 并细化）

| # | 项 | 等级 | 消解动作 |
|---|---|---|---|
| U1 | TC260-PG-20259A 官方 PDF 全文（载体逐格式表、命名空间 URI、示例码） | 官网下载有会话门槛，本日 curl 两次均得登录壳 HTML | **人工登录 tc260.org.cn（或 gcmark.com）下载一次 PDF 复核**；这是唯一能裁决 §1.5 载体冲突的动作 |
| U2 | 命名空间 `http://www.tc260.org.cn/ns/AIGC/1.0/` 与前缀 `TC260`、属性名 `AIGC` | 单一来源（博客园引官方示例码） | 随 U1 |
| U3 | 逐格式载体（XMP vs EXIF UserComment vs PNG tEXt/AIGC 私有块） | 两实现文冲突 | 随 U1；推荐配方已留一行切向（§4.4） |
| U4 | 阿里云的 ContentProducer 27 位编码结构与 Label "2"/"3" 取值语义 | 单一来源转述 | 随 U1；本 App 用名称口径+Label="1" 规避 |
| U5 | GB 45438-2025 元数据隐式标识正文与其附录（知乎转述"附录E/F"，且该文把附录挂在《办法》名下，与《办法》无附录的事实相悖） | 二手且表述混乱 | 随 U1 或标准全文系统人工在线预览 |
| U6 | exifinterface 1.4.1 PNG iTXt 写入的运行时 round-trip | 字节码+官方源码强证据，**未跑真机**（Gradle 禁令） | §4.3 仪器化用例 |
| U7 | CSDN 文"PNG（自 1.5.0 起）"含义 | 单源未解释 | 不阻塞（iTXt 为 PNG 标准块）；随 U1 顺带核 |

---

## 6. 来源清单（全部为本日实际访问/抓取/下载；标注通道）

**官方**
- 《人工智能生成合成内容标识办法》全文（cac.gov.cn，web_reader 本日抓全文，第五/六/十一条原文引用）：https://www.cac.gov.cn/2025-03/14/c_1743654684782215.htm
- GB 45438-2025 著录页（openstd，批次 20 核；本日未重开）：https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=F32EA2A561F1886CD8D606513512D547
- TC260 6 指南发布（文号网安秘字〔2025〕118号、附件 URL，WebFetch）：https://www.secrss.com/articles/82518
- TC260 指南页（curl 实测 302 会话壳）：https://www.tc260.org.cn/portal/article/303/533e0475b511479aaadf5cdd81182caf
- TC260 附件 2 RAR（curl 两次均得 23,681B 登录壳 HTML——**官方原文未获取**）：https://www.tc260.org.cn/upload/2025-08-28/1756396168261019065.rar
- gcmark.com（curl 得 538B JS 壳）：https://gcmark.com

**TC260 字段/载体二手实现文（多源交叉）**
- 博客园（XMP 口径 + 命名空间，WebFetch 全文提取）：https://www.cnblogs.com/aopstudio/p/19075002
- CSDN（UserComment/tEXt 口径，WebFetch 全文提取）：https://blog.csdn.net/u010131870/article/details/150218572
- 腾讯云数据万象 AIGCMetadata（字段含义+必选性，WebFetch）：https://cloud.tencent.com/document/product/460/122024
- 阿里云 AIGC 图片标识指南（字段含义+Label 取值+ContentProducer 编码结构，WebFetch）：https://help.aliyun.com/zh/sase/getting-started/aigc-image-logo-add-guide
- 掘金·腾讯数据万象合规（字段表+必选性+EXIF/XMP 双嵌入口径，WebFetch）：https://juejin.cn/post/7587743345197088820
- aicheck365 拆解（UserComment JSON 惯例旁证，检索引述，原文 WebFetch 超时）：https://www.aicheck365.com/blog/ai-image-metadata-guide
- chinalawtranslate 抖音规范转述（双载体旁证，检索引述）：https://www.chinalawtranslate.com/抖音关于人工智能生成内容标识的水印与元数据规范

**库与构件（本日实测通道标注）**
- androidx ExifInterface 主分支源码（gh api 抓取，javadoc/`getXmpHandlingForImageType`/PNG iTXt 读写路径原文引用）：https://android.googlesource.com/platform/frameworks/support/+/androidx-main/exifinterface/ （经 github.com/androidx/androidx 镜像）
- exifinterface 1.4.1 构件（dl.google.com 下载+unzip+javap 字节码实测，§3.1）：https://dl.google.com/android/maven2/androidx/exifinterface/exifinterface/1.4.1/exifinterface-1.4.1.aar
- exifinterface 版本表（maven-metadata.xml，curl）：https://dl.google.com/android/maven2/androidx/exifinterface/exifinterface/maven-metadata.xml
- metadata-extractor README（WebFetch，"for reading" 原文）：https://github.com/drewnoakes/metadata-extractor
- xmpcore 版本表（Maven Central search API，curl）与 6.1.11 构件（repo1 下载+class 版本实测）：https://repo1.maven.org/maven2/com/adobe/xmp/xmpcore/6.1.11/xmpcore-6.1.11.jar
- xmpcore license 争议：https://github.com/adobe/XMP-Toolkit-SDK/issues/5 、https://issues.apache.org/jira/browse/TIKA-3204
- Ashampoo/xmpcore（WebFetch，弃用迁移+移除 IO 能力）：https://github.com/Ashampoo/xmpcore
- Commons Imaging 格式支持（检索引述官方 formatsupport 页，2025-04 更新）：https://commons.apache.org/imaging/formatsupport.html
- ihmily/metatoolkit（本日 sparse clone，grep 实测无 TC260 字段）：https://github.com/ihmily/metatoolkit
- drewnoakes/adobe-xmp-core（检索引述，"Reconstructed history"）：https://github.com/drewnoakes/adobe-xmp-core

**项目内（本日读取）**
- `gradle/libs.versions.toml:28`（exifinterface=1.4.1 锁版）
- `core/data/build.gradle.kts:18`（minSdk 23）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/GeneratedFigureMetadata.kt:61-96`（现状写入/读回）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/AndroidCanonicalAssetVault.kt:358-373`（格式边界与写入点）
- 批次 20 R3：`docs/research/2026-10-25-ai-content-labeling-compliance.md`（G4 差距与 V2 项的来源）
