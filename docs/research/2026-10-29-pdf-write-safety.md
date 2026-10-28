# PDF 写盘可靠性研究（批次 30 T5 驱动）

> 研究轨产物，落盘于 2026-10-29。对象是计划 `.zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md` §1 第 6 项（KD-79 修复：`PdfDocument.writeTo` 走跟踪流 + 回读校验，补负向"流中途失败/磁盘满模拟"）。
>
> **本轮边界（先声明，后人别读成已验）**：零 Gradle、零编译、零设备、零 git 写。全部平台结论取自 AOSP / Skia / OpenJDK / androidx / okio / commons-io **源码实读**（`gh api` 取 raw，命令与输出见 §7）；设备侧事实引自批次 29 证据 `docs/research/evidence/2026-10-28-batch-29/step04-pdf-parity.md`（下称"E-04"）。`developer.android.com` 参考页两次 `WebFetch` 均 `Connect Timeout`（§7.3），故一律以源码 javadoc 为准。
>
> 本文件不改任何热区文件；§2.6 的两处生产面问题只做登记与归因，**修复需由批次拥有者裁 KD**。

---

## 0. 结论（供 T5 直接采纳）

| # | 结论 | 依据 |
|---|---|---|
| 1 | API 34 的 `PdfDocument.writeTo` **确实**吞掉 OutputStream 的 Java 异常：native 写适配器 `SkJavaOutputStream::write` 在 `ExceptionCheck()` 后执行 `ExceptionDescribe()` → `ExceptionClear()` → `return false`，Java 侧只看到 `writeTo` 正常返回 | `CreateJavaOutputStreamAdaptor.cpp:243-250`（§2.2） |
| 2 | 同一行为在 **API 35 / 36 未变**（行号 253-263），`PdfDocument.java` 的 `writeTo`/`nativeWriteTo` 三版逐字相同 | §2.5 |
| 3 | 吞掉之后 SkPDF **继续生成**：`SkWStream::write` 的 bool 返回值在 SkPDF 写点被忽略，`writeTo` 无返回值可查 → 结果是**结构自洽但内容缺失的 PDF + 调用方认为成功**（持续失败=前缀截断；瞬时失败=中段缺块、页数仍对，见 §2.2） | §2.3 |
| 4 | `flush()/close()` 在这条路径上**根本不会被调用**（`SkDocument::close` 不 flush），所以跟踪流里 `flush`/`close` 覆写对本 API 是惰性防御，真正起作用的是两个 `write` | §2.4 |
| 5 | 单靠"跟踪流记首失败"仍漏两类失败：**静默短写**（委托流不抛但少收字节）与 **Error 型抛**（外层 `catch (Exception)` 不接）。补齐代价极小：滚动尾窗查 `%%EOF` + 接受字节计数 | §3.1、§5 |
| 6 | 产品级"写成功"最小判据 = **D1 无首失败 ∧ D2 字节>0 且尾部含 `%%EOF` ∧ D3（文件目标）`fd.sync` 成功 + 同目录 `renameTo` 返回 true + 重开 `PdfRenderer` 页数相符**；D0（`writeTo` 不抛）单独**不成立** | §5 |
| 7 | GitHub 侧**没有**比仓内既有实现更合适的原子写/校验写件：androidx `AtomicFile.finishWrite` **吞** sync/close/rename 失败（会把 fail-open 装回去）；okio / commons-io 的原子移动走 `java.nio.file.Files`（**API 26+**，与本仓 minSdk 23 冲突，KD-11 已同类替换过） | §4 |
| 8 | **新发现（本批外溢，报而不改）**：KD-79 机制在 `core:export` 有**两处生产面**，其中 `MistakePdfDelivery.writeSubset` 的**无缓冲**写法当前就是 fail-open | §2.6 |

---

## 1. 调用链总览（为什么"异常消失"是设计后果而不是偶发）

```text
PdfDocument.writeTo(OutputStream)            PdfDocument.java:169-176   (void, throws IOException 声明)
 └─ nativeWriteTo(long, OutputStream, byte[] chunk)   PdfDocument.java:247 / PdfDocument.cpp:136-142 (void)
     └─ PdfDocument::write(SkWStream*)        PdfDocument.cpp:88-100
         ├─ SkPDF::MakeDocument(stream)       PdfDocument.cpp:89  → SkPDFDocument.cpp:632-640
         ├─ beginPage / drawPicture / endPage   PdfDocument.cpp:93-97
         └─ document->close()                 PdfDocument.cpp:99
             └─ SkDocument::close()           SkDocument.cpp:51-69   (无 flush)
                 └─ SkPDFDocument::onClose()  SkPDFDocument.cpp:568-610
                     └─ wStream->writeText(...)  SkPDFDocument.cpp:96-135 等（忽略 bool）
                         └─ SkWStream::write  SkStream.h:238 (虚函数, 返回 bool)
                             └─ SkJavaOutputStream::write  CreateJavaOutputStreamAdaptor.cpp:220-257
                                 └─ JNI CallVoidMethod(OutputStream.write)  :243-244
                                     └─ 抛异常 → ExceptionCheck :245
                                         → ExceptionDescribe :246
                                         → ExceptionClear :247
                                         → return false :249
```

链上**每一跳都没有把失败送回 Java**：`SkJavaOutputStream` 返回 bool 给 C++，C++ 不查；`PdfDocument::write` 是 `void`；`nativeWriteTo` 是 `void`；`PdfDocument.writeTo` 的签名虽然声明 `throws IOException`（`PdfDocument.java:167`），但唯一可能抛它的位置（`nativeWriteTo`）不产生异常。**javadoc 承诺与实现不符**，这是 KD-79 的本质。

---

## 2. 机制核实

### 2.1 出错点与吞掉点（逐行）

`CreateJavaOutputStreamAdaptor.cpp`（android-14.0.0_r1）`SkJavaOutputStream::write`：

```cpp
220:    virtual bool write(const void* buffer, size_t size) {
...
234:            env->SetByteArrayRegion(storage, 0, requested, ...);
236:            if (env->ExceptionCheck()) {
237:                env->ExceptionDescribe();
238:                env->ExceptionClear();
239:                ALOGD("--- write:SetByteArrayElements threw an exception\n");
240:                return false;
241:            }
243:            fEnv->CallVoidMethod(fJavaOutputStream, gOutputStream_writeMethodID,
244:                                 storage, 0, requested);
245:            if (env->ExceptionCheck()) {
246:                env->ExceptionDescribe();
247:                env->ExceptionClear();
248:                ALOGD("------- write threw an exception\n");
249:                return false;
250:            }
252:            buffer = (const char*)buffer + requested;
253:            size -= requested;
254:            fBytesWritten += requested;
255:        }
256:        return true;
257:    }
```

- **两处**都会吞：`SetByteArrayRegion`（宿主 buffer 异常，几乎不会发生）与 `CallVoidMethod`（**委托流抛异常，实际路径**）。
- `ExceptionDescribe()` 把栈打印到 `System.err` —— 这正是 E-04 里 230 条 `java.io.IOException: simulated write failure` 的来源（同一 chunk 一次，`PdfDocument.java:79` 的 `mChunk = new byte[4096]`）。
- `ExceptionClear()` 之后 JNI 状态干净，**异常不会在 native 返回时冒泡**。
- `fBytesWritten += requested`（`:254`）只在写成功后才累加 —— 说明 native 内部**知道**实际写出多少字节，但该值没有任何 Java 出口（`bytesWritten()` 只被 SkPDF 用于偏移表）。

### 2.2 失败之后偏移表仍"自洽"（为什么截断产物看起来像完整 PDF）

`CallVoidMethod` 抛异常后直接 `return false`，**本 chunk 的剩余部分不会重试**，`buffer`/`size` 也未推进（`:249` 早于 `:252-254`）——即该 4 KB 窗口整块丢失。

关键在于 `fBytesWritten += requested` 只在**写成功之后**执行（`:254`），而 `SkPDFOffsetMap` 的偏移全部取 `s->bytesWritten()`（`SkPDFDocument.cpp:55` `markStartOfDocument`、`:61` `markStartOfObject`）。因此：

- 丢失的 chunk **不**被计入偏移；其后所有对象的偏移**整体前移**，与磁盘上真实字节位置**仍然一致**。
- 产物不是"xref 指向错位"的坏文件，而是"**某个对象少了一块内容、结构仍自洽**"的 PDF。PDFium 很可能正常打开、页数也正确。

因此失败形态分两种，必须分开说：

| 失败形态 | 产物 | 可观测 |
|---|---|---|
| **持续失败**（每次 write 都抛，E-04 的夹具就是这种，230 条 logcat） | 纯**前缀**：第一块之后什么都没有，`%%EOF` 也没写出 | 尾标缺失（D2）+ 重开失败（D3） |
| **瞬时失败**（只丢一块，之后恢复） | **中段缺块**、尾部完整、页数正确 | **D1 抓得到**（跟踪流记到了那次异常）；若委托流是**不抛**的静默丢块，则 D1/D2/D3 全抓不到（§3.5 残留类） |

E-04 用"截断"概括是对**它那个夹具**的准确描述，但不是机制的全部：真正决定产物形状的是"失败是否持续"，而偏移表的自洽性解释了为什么中间型的损坏不会被"能不能打开"发现。

### 2.3 SkPDF 为什么不中止

`SkWStream::write` 是纯虚函数、返回 `bool`（`SkStream.h:238`），`writeText` 也返回 `bool`（`SkStream.h:257-260`）。但 SkPDF 的写点按"不会失败"写：

- `SkPDFDocument.cpp:113` `static void end_indirect_object(SkWStream* s) { s->writeText("\nendobj\n"); }` —— 返回值被丢弃（`void` 函数）。
- `SkPDFDocument.cpp:96-135` `serializeHeader` / `serialize_footer` 同样直接调用 `wStream->writeText(...)`，不检查。

因此 false 只是"这一次没写进去"，文档生成流程照常跑完 `onClose`，最终 `%%EOF` 也照样"写"（并被吞掉，如果失败发生在尾部）。**本会话未穷举 SkPDF 全部写点**，但已读的这几处足以支撑"不存在中止路径"的结论；若要更严格，需全仓 grep `->write(` 的返回值使用。

### 2.4 `flush`/`close` 在这条路径上不出现（对跟踪流设计有直接影响）

- `SkDocument::close()`（`SkDocument.cpp:51-69`）只调 `onClose(fStream)` 并把 `fStream` 置空，**不调用 `fStream->flush()`**。
- `SkPDFDocument::onClose`（`SkPDFDocument.cpp:568-610`）以 `serialize_footer(...)` 结束，**无 flush**。
- `SkPDFDocument` 析构里 `this->close();`（`:226-227`），同样不 flush。
- `nativeWriteTo` 里 `delete skWStream` 只析构 C++ 包装（`PdfDocument.cpp:141`），不触达 Java `close()`。

结论：**`PdfDocument.writeTo` 只调用 Java `OutputStream.write(byte[],int,int)`**——`gOutputStream_writeMethodID` 在 `CreateJavaOutputStreamAdaptor.cpp:295` 注册为 `write([BII)V`，**不走 `write(int)`**。`PdfWriteFailureTrackingStream`（`VisualDrawOpsPdfPainter.kt:129-152`）覆写 `flush`/`close`/`write(int)` 属**惰性防御**：无害，但不要把它们当成"已覆盖"的证据。

顺带记录一个不对称性（源码实读、**未实测**）：`SkJavaOutputStream::flush()`（`CreateJavaOutputStreamAdaptor.cpp:259-261`）**不**做 `ExceptionCheck/Clear`，若将来路径真调用到 flush 且抛异常，异常会以 pending 状态跨 JNI 边界回到 Java——即 flush 失败**可能**逃逸而 write 失败不会。这反过来说明：**当前所有"吞掉"都只发生在 write**，跟踪流必须盯的是 write。

### 2.5 版本范围（API 34 / 35 / 36 同构）

| 载体 | android-14.0.0_r1 | android-15.0.0_r1 | android-16.0.0_r1 |
|---|---|---|---|
| `CreateJavaOutputStreamAdaptor.cpp` 吞点 | `:237-238`、`:246-247` | `:253-254`、`:262-263` | `:253-254`、`:262-263` |
| `PdfDocument.java` `writeTo` / `nativeWriteTo` | `:169-176` / `:247`（`private native void`） | 同左 | 同左 |
| `PdfDocument.cpp` `nativeWriteTo` 返回值 | `void`（`:136-142`） | —（未逐行比对，见 §8） | —（未逐行比对） |

三版的 `ExceptionDescribe()+ExceptionClear()` 均在（grep 实测，§7.1），故**不要按"升级到新 API 就自愈"设计**。

### 2.6 外溢：KD-79 机制在 `core:export` 的两处生产面（报而不改）

计划 §1 第 6 项只覆盖 `core:data` 的 painter；但同一机制在热区 `core:export` 有两条实活路径。以下均为源码实读，**未上设备**。

**(a) `DeterministicMistakePdfRenderer` —— 目前 fail-closed，但靠巧合**

```kotlin
153:                document.writeTo(buffered)
154:                buffered.flush()
155:                fileOutput.fd.sync()
```
（`core/export/.../DeterministicMistakePdfRenderer.kt:151-156`，`buffered = BufferedOutputStream(fileOutput)` 在 `:152`）

- native 每次写 4096 B（`PdfDocument.java:79`）；`BufferedOutputStream` 默认 8192（OpenJDK `BufferedOutputStream.java:57-59`；**Android libcore 为同源 ojluni 分支，逐行同一性本会话未核** → 见 §7.3/§8，本结论按"待验证"强度使用）。
- `write(byte[],off,len)` 在 `len < buf.length` 时只入缓冲（`:117-125` 的**相反分支**）；缓冲满时 `flushBuffer()` 调 `out.write(buf,0,count)`（`:79-84`），**若 `out.write` 抛异常，`count` 不清零**（`count = 0` 在写成功之后那行，`:82`），因此下一轮/最后的 `flush()` 会**重试**同一块 → 异常最终在 `:154` `buffered.flush()` 上抛回我们的代码 → `MistakePdfExporter.prepare` 的 `finally` 删临时目录（`MistakePdfExporter.kt:140-142`，`prepare` 整体 `:114-144`）。
- **脆弱点**：这条保护完全依赖"4096 < 8192"。一旦有人改成 `BufferedOutputStream(fileOutput, 4096)`（`len >= buf.length` 走直写分支 `OpenJDK:117-125`），异常被 native 吞掉、无缓冲可重试 → **静默截断**。这是隐式契约，代码里没有任何注释钉它。

**(b) `MistakePdfDelivery.writeSubset` —— 当前就是 fail-open**

```kotlin
257:                        FileOutputStream(destination.fileDescriptor).use { output ->
258:                            document.writeTo(output)
259:                            output.flush()
260:                        }
```
（`core/export/.../MistakePdfDelivery.kt:257-261`）

- 无 `BufferedOutputStream`：每个 4096 B chunk 直写 `FileOutputStream.write`；写失败（`ENOSPC`、打印 spooler 侧 `EPIPE`、`EIO`）→ native 清异常 → `output.flush()` 是空操作 → 函数正常返回。
- 调用方 `PreparedPdfPrintDocumentAdapter.onWrite`（`MistakePdfDelivery.kt:146-155`）在 `writeSubset` 之后**只查取消标志**，随即 `callback.onWriteFinished(pages)` —— 打印框架拿到的是截断 PDF，我们这层报成功。
- 该分支只在"用户选了部分页"时走；全量页走 `copyWhole` + sha256 比对（`MistakePdfDelivery.kt:128-138`），那条路是对的。

**建议**（不在本轨实施，热区 + KD-75）：由批次拥有者裁一条 KD，把 (b) 列为与 KD-79 同源的独立生产缺陷；(a) 至少加注释钉住"缓冲必须 > 4096"或在调用点包跟踪流。**收紧方向与 KD-79 修复同型，零新机制。**

---

## 3. 可靠检测方案对比

四个方案按"能不能真的证明写成功"排列。术语：**吞掉面** = 该方案看不见的失败类。

### 3.1 方案 A：跟踪流（现状，`VisualDrawOpsPdfPainter.kt:129-152`）

| 项 | 内容 |
|---|---|
| 机制 | 包装委托流，`write(int)`/`write(byte[],off,len)`/`flush`/`close` 入口 `catch (Exception)` → 记首个 → **重抛**；`writeTo` 返回后查 `firstFailure` |
| 能抓 | 任何从 Java 抛出的 `Exception`：`ENOSPC`/`EIO`/`EPIPE`/自定义、以及未来若有 flush 失败 |
| 吞掉面 | ① **静默短写**：委托流不抛但少写（缓冲被丢弃、委托是"黑洞"实现、`write` 返回值语义被违反）；② **Error**：`catch (Exception)` 不接 `OutOfMemoryError`/`StackOverflowError`，而 JNI 的 `ExceptionCheck` 对任何 `Throwable` 都返回 true → 一样被 `ExceptionClear` 吞掉；③ 生成侧的语义缺失（少字形、丢 op —— 非本判据范围） |
| 代价 | 每次 write 一个 try/catch + 一个分支；零分配；零 API 变化 |
| 判据强度 | 必要不充分 |

**为什么"重抛"要保留**：重抛让 native 依旧按"本次写失败"处理（`return false`），行为与裸传一致；记录只是旁路。若改成"记录后吞掉"，SkPDF 会以为写成功而继续，语义反而更差。

### 3.2 方案 B：字节计数 + 尾标校验（本轨建议补进 A）

| 项 | 内容 |
|---|---|
| 机制 | 委托写成功后在计数器上累加 `len`；同时维护一个滚动尾窗（**建议保留最后 1024 B**）；`writeTo` 返回后断言 `accepted > 0 && tail.contains("%%EOF")` |
| 能抓 | 方案 A 的吞掉面 ① 中的**尾标缺失类**：零字节成功、尾部静默截断、任何导致 `%%EOF` 未写出的短写 |
| 吞掉面 | ① **中段静默丢块**（丢一块后继续正常写，尾标完整、页数正确 —— 见 §2.2）；② 内容层错误（截断位置恰好在 `%%EOF` 之后被补齐的伪造流、页面内容缺失） |
| 代价 | O(1)：一个 `Long` + 一个 `ByteArray(1024)`；每 chunk 一次 `System.arraycopy`（≤1024 B；按 4096 B chunk 算是 25% 内存拷贝，仍可忽略） |
| 前提 | SkPDF 固定以 `"\n%%EOF"` 结尾、**其后无换行**（`SkPDFDocument.cpp:135` `wStream->writeText("\n%%EOF")`）。**不要**假设跨 Skia 版本稳定 → 写成"在最后 1024 B 内出现 `%%EOF`"更稳；本轨建议按尾窗 1024 B 实现，判据仍能抓住"尾部整段丢失" |
| 关于"比对预期字节数" | **预算是不可知的**：SkPDF 不暴露 `fBytesWritten`（`CreateJavaOutputStreamAdaptor.cpp:216-218` 仅 C++ 内可见）。可行的"预期"只有两类：(i) 参考产物——同一文档 dry-run 到 `ByteArrayOutputStream` 再比对长度；但 SkPDF 元数据里的 UUID 依赖 `SkPDFMetadata::CreateUUID(metadata)`（`SkPDFDocument.cpp:261`），默认 metadata 下确定性**未由本会话实测证明**，若将来时间戳非零则长度就会漂移；(ii) `File.length()` 与计数比对——只能抓"委托流谎报"。**结论：不要以"预知字节数"作为产品判据**，只保留 `>0` + 尾标 + （文件目标时）与实际行长比对 |
| 判据强度 | 与 A 合取后覆盖"可观测的字节完整性"；**中段静默丢块仍不可见**（见吞掉面 ①） |

### 3.3 方案 C：写临时文件 + `fd.sync` + 同目录 `renameTo`

| 项 | 内容 |
|---|---|
| 机制 | 目标目录内建 `*.pending-<uuid>` 文件 → 写 → `FileOutputStream.getFD().sync()` → `renameTo(最终名)`（**检查 bool 返回值**）→ 失败删临时件 |
| 能抓 | ① 读者永不看到半成品（原子可见性）；② 崩溃后残留可识别（`.pending-` 前缀）；③ `sync` 失败会被 `sync()` 自己抛出（**与 androidx `AtomicFile` 相反**，见 §4 表） |
| 吞掉面 | 截断本身（rename 只是搬运，不校验内容）；跨文件系统 rename（`EXDEV`）会返回 **false 而不是抛异常** —— 必须查返回值；目录项未 fsync（掉电后 rename 可能丢失，数据仍在临时名） |
| 代价 | 一次额外 open/rename；`fd.sync()` 是**真实刷盘**，在 UFS/eMMC 上 3–30 ms 量级（**本会话未测量**，属经验值，勿当证据引用） |
| 与 minSdk 23 | `File.renameTo`、`FileDescriptor.sync` 均为 API 1；**不要**用 `java.nio.file.Files.move(..., ATOMIC_MOVE)`（API 26+，KD-11 已同类替换） |
| 仓内先例 | `MistakePdfExporter.publish`（`MistakePdfExporter.kt:358-379`）正是"临时目录 + `renameTo` + 查 bool + 失败落 `FILE_PUBLISH_FAILED`" |
| 判据强度 | 解决"可见性/持久性"，**不解决**"内容是否完整" |

### 3.4 方案 D：写后重新打开校验（`PdfRenderer` 回读页数）

| 项 | 内容 |
|---|---|
| 机制 | 落盘后 `ParcelFileDescriptor.open(file, MODE_READ_ONLY)` → `PdfRenderer(pfd)` → `pageCount`；要求 `pageCount == 期望页数`（painter 当前恒 1 页） |
| 能抓 | 结构性截断（失 xref/trailer）、页缺失、非 PDF 垃圾 |
| 吞掉面 | **内容保真**：xref 完好但内容流被清零/替换的 PDF 仍能开出正确页数；PDFium 对损坏文件的**修复式加载**可能"成功"（详见下） |
| 异常类型 | 不要只 catch `IOException`。`PdfUtils.cpp:45-80` `forwardPdfiumError`：`FPDF_ERR_FORMAT` → `IOException("file not in PDF format or corrupted")`；`FPDF_ERR_PAGE` → `IOException("page not found or content error")`；但 `FPDF_ERR_UNKNOWN`/default → **`java.lang.Exception`（受检）**；`openPage` 失败 → `IllegalStateException("cannot load page")`（`PdfRenderer.cpp:44-49`）。**必须 `catch (Exception)`**，与仓内 `MistakePdfExporter.validatedPageCount`（`:165-183`）一致 |
| 前提 | 必须是**可 seek 的 fd**（`PdfRenderer.java:162-168` 用 `lseek`/`fstat` 探针，不可 seek 抛 `IllegalArgumentException("file descriptor not seekable")`）；纯流目标无法用 |
| 代价 | 一次 PDFium 初始化 + 页对象（毫秒级；**未在本轮测量**）；不读页面像素，不 `openPage` 也能取 `pageCount`（`:200-203`） |
| minSdk | `PdfRenderer` API 21+，23 安全 |
| 判据强度 | 对"文件目标"是最强的一档 |

### 3.5 矩阵

| 失败类 | A 跟踪流 | B 计数+尾标 | C 临时+rename+sync | D 重开校验 |
|---|---|---|---|---|
| 写时抛 `IOException`（ENOSPC/EIO） | ✅ | ✅ | ⚠️ 不改变写路径（异常先到调用方，需与 A 合用） | ✅（落盘后才发现） |
| 静默短写（不抛，尾部） | ❌ | ✅ | ❌ | ✅（大概率，依赖 PDFium 修复式加载） |
| 中段静默丢块（不抛，尾标完整、页数对） | ❌ | ❌ | ❌ | ❌（**已知残留类**，只有参考产物比对或 D4 栅格对拍能看见） |
| 零字节成功 | ❌ | ✅ | ❌ | ✅ |
| 崩溃/掉电半成品对读者可见 | ❌ | ❌ | ✅ | ❌ |
| `sync` 失败 | ❌ | ❌ | ✅（自行 sync 并抛出） | ❌（内容在 page cache 里也读得对） |
| 内容保真（字形/op 丢失） | ❌ | ❌ | ❌ | ❌（属一致性门原型，非写成功判据） |
| 代价 | ~0 | ~0 | 一次 rename + 一次 fsync | 一次 PDFium 打开 |

### 3.6 组合建议（最小、无冗余）

- **painter 内部（流目标，T5 可改）**：A + B。A 已有；B 用纯层 helper 实现以便 JVM 单测。
- **调用方（文件目标）**：C + D。这不是新发明——仓内 `MistakePdfExporter.prepare`（`:114-144`）+ `validatedPageCount`（`:165-183`）+ `publish`（`:358-379`）已经是这套组合的成熟实现，复用即可，**不要另起一套**。
- 不引入第三套机制：D 已经隐含"C"，A+B 已覆盖 D 看不见的写入期失败。

---

## 4. GitHub 参考实现评分

评分口径（1–5 分，**本研究的判断，非业界基准**）：**契合度** = 能否直接满足"§5 的 D1∧D2∧D3"，尤其是否 fail-closed；**兼容** = minSdk 23 与零新增依赖；**维护** = `pushed_at` / 活跃度实测（§7.2）。许可一律 Apache-2.0（逐仓 `gh api repos/<r> --jq .license.spdx_id` 实读），无 copyleft 风险。

| 仓库 | 关键实现（实读位置） | 契合 | 兼容 | 维护 | 总分 | 裁定 |
|---|---|---|---|---|---|---|
| **仓内 `MistakePdfExporter`**（非 GitHub，仅作基线） | `prepare:114-144`、`validatedPageCount:165-183`、`publish:358-379`（`renameTo` 查 bool） | 5 | 5 | — | **5.0** | **首选复用**：零依赖、minSdk 安全、异常全走 `MistakePdfExportException` 有码族 |
| `androidx/androidx` · `androidx.core.util.AtomicFile` | `finishWrite:119-132`；`sync:214-221`（`catch (IOException) {}` → **return false**）；`rename:223-236`（失败只 `Log.e`）；`failWrite:139-154` | **1** | 5（已是依赖 `core/data/build.gradle.kts:172`） | 5（`pushed 2026-10-28`，6121 ★） | **3.0** | **不可作为成功判据**：`finishWrite` 吞 `fd.sync` 失败、吞 `close` 的 `IOException`、`rename` 失败只打日志 → **把 fail-open 原样装回**。仅可用作"写字节的载体"，判据仍须自带 |
| `square/okio` | `FileSystem.kt:152-159`（`SYSTEM` 在有 `java.nio.file.Files` 时选 `NioSystemFileSystem`）；`NioSystemFileSystem.kt:76-78`（`Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`）；`JvmFileHandle.kt:72-75`（`protectedFlush() = randomAccessFile.fd.sync()`） | 3（`FileHandle.flush` 的 fsync 是加分项） | **1**（`Files` = API 26+，minSdk 23 直接踩 KD-11 同类坑；且新增 okio 3.x 依赖） | 5（`pushed 2026-10-26`，9054 ★） | **3.0** | 不引入。若将来升 minSdk≥26，其 `FileHandle` + `atomicMove` 是干净选项 |
| `apache/commons-io` | `FileUtils.moveFile:2591`、带 `CopyOption...` 的重载 `:2611`；`copyFile:810` | 2（`moveFile` 默认不用 `ATOMIC_MOVE`，需显式传；无 fsync） | 1（移动走 `java.nio.file.Files` → API 26+；JVM jar 体积） | 5（`pushed 2026-10-28`，1087 ★） | **2.7** | 不引入 |
| `TomRoush/PdfBox-Android` | 纯 JVM 的 `PDDocument.load(File)` + `getNumberOfPages()`，可替代 `PdfRenderer` 做回读（不依赖 `ParcelFileDescriptor`） | **1**（与平台 `PdfRenderer` 能力重叠，却重得多） | 2（纯 Java，minSdk 无碍；体积数 MB 级） | 2（`pushed 2024-03-18`，1196 ★，**125 open issues**） | **1.7** | 不引入；`PdfRenderer` 已够 |
| `Kotlin/kotlinx-io` | **未能取到源码路径**（`contents/core/jvm/src/kotlinx/io/files` → 404，§7.2） | — | — | 5（`pushed 2026-10-27`，1551 ★） | **弃评** | UNVERIFIED，不选 |

**一句话**：外部库要么把失败吞掉（androidx），要么踩 minSdk 23（okio/commons-io）。**"原子写/校验写"在本仓的最优解是复用自己的 export 序列，而不是加依赖**——这也符合"新增必须指认它消灭的失败"：引入它们不会消灭任何当前未消灭的失败，反而各自带回一个。

---

## 5. "写成功"的最小可信判据

判据分级，逐级标注**它消灭的具体失败**（"新增必须指认"纪律）。

| 级 | 判据 | 消灭的具体失败 | 判定强度 | 落在哪 |
|---|---|---|---|---|
| **D0** | `writeTo` 不抛异常 | ——（**已知被设备否证**：E-04 §2.1 该路径异常从不抛出） | **不成立，禁止单独使用** | — |
| **D1** | 跟踪流 `firstFailure == null` | 所有 Java 可见的写期 I/O 失败被 native 静默吞掉（KD-79 本体） | 必要 | painter |
| **D2** | 接受字节 `> 0` **且** 尾窗（≥1024 B）内出现 `%%EOF` | ① 零字节成功；② 尾部静默截断（`%%EOF` 未写出）→ 否则"截断 PDF 报成功"；③ 方案 A 的尾标类盲区 | 必要（不覆盖中段静默丢块） | painter（纯层 helper） |
| **D3** | 文件目标：`fd.sync()` 成功 + 同目录 `renameTo` 返回 `true` + 重开 `PdfRenderer` 且 `pageCount == 期望页` | ① 仅写进 page cache 就报成功（掉电后文件为空/残缺）；② rename 失败返回 false 却被当成功；③ 结构性损坏（失 xref/trailer、页丢失） | 充分（结构性） | 调用方 |
| **D4** | 重开后按一致性门原型对拍栅格 | 内容保真（字形/op/alpha 丢失） | 超范围 | **不是写成功判据**；属批次 29 T4 原型门，仍标注 PROTOTYPE |

**产品级定义**：写成功 ⇔ **D1 ∧ D2 ∧ D3**（文件目标）；**流目标**只能主张 **D1 ∧ D2**，并在文档里写明"委托流的字节完整性由调用方负责"。

**为什么 D2 不是多余机制**：E-04 的 `pdfWriteFailureIsCoded` 已经证明"抛异常"这一路修好后就变绿；但**不抛的尾部静默截断不会被任何现有用例发现**。D2 的代价是每 chunk 一次 ≤1024 B 的滚动拷贝，换掉"截断 PDF 被当成成功并发布给用户"这一具体失败——符合"新增必须指向一个真实失败"。**它换不掉中段静默丢块**，那条路明确登记为残留（§6 末、§8）。

**为什么 D3 不在 painter 里**：painter 的契约是 `OutputStream`（`VisualDrawOpsPdfPainter.kt:66-71`），流不可 seek，`PdfRenderer` 用不上；硬塞会让纯流用例（一致门用 `ByteArrayOutputStream`，E-04 §3）无法运行。**放在调用方是接口约束决定的，不是偷懒。**

---

## 6. 负向用例设计（T5 可直接落成设备用例）

命名沿用现树风格（`pdfWriteFailureIsCoded` 等，`VisualDrawOpsPdfPainterInstrumentedTest.kt:447`）。**每条都写明"证明什么"与"不证明什么"**，避免后人把绿读成更强结论。

| ID | 场景 | 夹具 | 期望 | 证明 | 不证明 / 可行性 |
|---|---|---|---|---|---|
| **N-PDF-01** | 首写即抛（**现树已有**） | `FailingOutputStream`（`:479-487`，两个 `write` 均抛） | `PAINTER_PDF_IO` | D1 可达 | — |
| **N-PDF-02** | **流中途**抛 | 计数流：成功接收 k 个 chunk 后开始抛 | `PAINTER_PDF_IO`，且 `firstFailure` 为**首个**异常（后续重抛不得覆盖） | 记录语义是"首失败"；SkPDF 会继续尝试（E-04 logcat 230 次） | 不证明字节恰好丢在第 k 块之后 |
| **N-PDF-03** | **零字节成功**（黑洞流） | `write` 直接 `return`、不写不抛 | `PAINTER_PDF_IO`（**D2 触发**） | D2 消灭"尾标类静默成功" | **现树必然红**（D2 未实现）→ T5 的先红用例 |
| **N-PDF-04** | **尾部静默截断** | 只接受前 N 字节，其余丢弃且不抛 | `PAINTER_PDF_IO`（D2 尾标缺失） | 同上 | 不证明"内容正确"；不覆盖中段丢块（见下注） |
| **N-PDF-05** | **磁盘满模拟** | `IOException("ENOSPC")` 在第二次 write 抛出 | `PAINTER_PDF_IO`，消息含 `ENOSPC` | ENOSPC 类异常走到 D1 | **不是真 ENOSPC**：不覆盖内核 partial write + errno 路径（真 ENOSPC 需填满 `/data`，不可行，登记 UNVERIFIED） |
| **N-PDF-06** | **只读目标** | 缓存目录内建 `0444` 文件 → `FileOutputStream(file)` | 构造期 `FileNotFoundException`（EACCES）**先于** `writeTo`；调用方须折成有码失败 | 证明"只读"不是 D1 能接的失败类：**失败发生在 painter 之外** | painter 只收 `OutputStream`，本用例只能在调用方/测试内建 seam 断言；**不要**假装 painter 覆盖了它 |
| **N-PDF-07** | **写后重开校验** | 把合法 PDF 尾部截去 1/3，写到文件 | `PdfRenderer` 抛异常 **或** `pageCount != 1` | D3 能抓结构性截断 | PDFium 有修复式加载：**断言"不等于期望页数"而不是"必抛"**（本会话未在设备上验证 PDFium 的具体恢复行为 → 实现前先在 API 34 上跑一次取基线，别按猜测定断言） |
| **N-PDF-08** | **rename 失败** | 目标目录只读 / 目标已存在为目录 | `renameTo` 返回 `false` → 调用方 `FILE_PUBLISH_FAILED` | D3 的 bool 检查不是形式主义 | 仓内 `publish:375-377` 已按此处理，可直接复用断言 |
| **N-PDF-09** | **`Error` 型抛** | `write` 抛 `OutOfMemoryError` | **期望** `PAINTER_PDF_IO`；**现树** `catch (Exception)` 接不到 → 仍静默成功 | 记录一条**已知残留缺口**，不写成绿 | 修复只需把捕获放宽到 `Throwable`（存 `Throwable`）；**是否本批做由 T5 裁**（若做，必须同时保留"首个失败"语义） |
| **N-PDF-10** | **真 fsync 失败** | —— | —— | —— | **不可行**：无法在无 root 的设备上稳定制造 `fsync` 失败；登记 UNVERIFIED，改由代码审查 + 批次 30 D-05 冷恢复用例间接覆盖 |

**明确不做的用例（诚实登记，不是遗漏）**：**中段静默丢块**（丢一块、尾部照常写成功、页数正确）。它在 D1/D2/D3 下**全部不可见**（§2.2、§3.5），要抓只能拿"参考产物的字节流"比对，而后者已被否决（§3.2 "关于比对预期字节数"）。写一条会绿的用例去"证明"它被覆盖，是把缺口洗成绿的典型做法 —— **本轨拒绝**。它的归宿是批次 29 T4 的栅格一致性门（原型门，D4），且该门不得被引用为产品结论。

**正向用例（配套，防"全红即通过"）**

| ID | 场景 | 期望 |
|---|---|---|
| **P-PDF-01** | 正常写真实文件 | 无首失败；`accepted > 0`；尾标 `%%EOF`；`fd.sync` 成功；`renameTo` true；重开 `pageCount == 1` |
| **P-PDF-02** | 同一文档写两次 | 两次都满足 P-PDF-01；**不主张字节相等**（R1 §5.6 已裁定字节稳定非契约） |
| **P-PDF-03** | 现树 12 条绿例不回退 | E-04 §2 的 12 条（页尺寸 ceil、原点、同网格、alpha、背景、clip、label、hitRegions、确定性、ptPerDu、码族穿透）继续绿 |

**先红后绿的顺序**（TDD 纪律）：N-PDF-03/04 在现树必然红（D2 未实现）→ 落 D2 helper → 转绿；N-PDF-01/02 现树应已绿（D1 已实现）→ 作为回归基线先跑一遍确认，**不得**因为"反正会绿"跳过。N-PDF-09 记录为已知红/缺口，**不得**为了全绿而删掉断言。

---

## 7. 本轮实际执行的核查命令与输出（可复跑）

全部只读网络请求，未落盘临时文件（Bash 重定向被安全 hook 拒绝，改为直接打印）。

### 7.1 AOSP 源码

```bash
gh api "repos/aosp-mirror/platform_frameworks_base/contents/libs/hwui/jni/CreateJavaOutputStreamAdaptor.cpp?ref=android-14.0.0_r1" -H "Accept: application/vnd.github.raw" | grep -n "ExceptionClear\|ExceptionDescribe"
# 154/156(输入流)、237/238、246/247(输出流 write)
for T in android-14.0.0_r1 android-15.0.0_r1 android-16.0.0_r1; do
  gh api "repos/aosp-mirror/platform_frameworks_base/contents/libs/hwui/jni/CreateJavaOutputStreamAdaptor.cpp?ref=$T" -H "Accept: application/vnd.github.raw" | grep -n "ExceptionClear\|ExceptionDescribe"; done
# r1: 237/238,246/247 ; r15: 253/254,262/263 ; r16: 253/254,262/263
for T in android-14.0.0_r1 android-15.0.0_r1 android-16.0.0_r1; do
  gh api "repos/aosp-mirror/platform_frameworks_base/contents/graphics/java/android/graphics/pdf/PdfDocument.java?ref=$T" -H "Accept: application/vnd.github.raw" | grep -n "nativeWriteTo\|public void writeTo"; done
# 三版均为 169/175/247，逐字相同
```

`PdfDocument.cpp`（`:88-100`、`:136-142`、`:149-155`）、`SkPDFDocument.cpp`（`:96-135`、`:226-227`、`:261`、`:568-610`、`:632-640`）、`SkDocument.cpp`（`:51-69`）、`SkStream.h`（`:238`、`:257-260`）、`PdfRenderer.cpp`（`:44-49`、`:112-120`）、`PdfUtils.cpp`（`:45-80`、`:98-114`）、`PdfRenderer.java`（`:157-183`、`:200-203`）同法实读。

### 7.2 第三方与依赖事实

```bash
for R in androidx/androidx square/okio apache/commons-io TomRoush/PdfBox-Android Kotlin/kotlinx-io; do
  gh api "repos/$R" --jq '{stars:.stargazers_count,pushed:.pushed_at,archived:.archived,license:.license.spdx_id,issues:.open_issues_count}'; done
# androidx/androidx      6121★ 2026-10-28T16:22:54Z Apache-2.0 63 issues
# square/okio            9054★ 2026-10-26T20:54:01Z Apache-2.0 104
# apache/commons-io      1087★ 2026-10-28T12:37:25Z Apache-2.0 26
# TomRoush/PdfBox-Android 1196★ 2024-03-18T20:51:33Z Apache-2.0 125
# Kotlin/kotlinx-io      1551★ 2026-10-27T16:24:30Z Apache-2.0 112
gh api "repos/Kotlin/kotlinx-io/contents/core/jvm/src/kotlinx/io/files/SystemFileSystem.kt"   # → 404 Not Found（未能核实 API 面）
```

仓内事实：`grep -n "androidx.core\|okio" core/data/build.gradle.kts` → `:172 implementation(libs.androidx.core.ktx)`（**androidx.core 已是依赖**，okio 不是）；`grep -n minSdk app/build.gradle.kts core/data/build.gradle.kts` → `app:21 minSdk = 23`、`core/data:18 minSdk = 23`；KD-11（`docs/known-defects.md:412-447`）记录本仓已把 `Files.move(..., ATOMIC_MOVE)` 换成同目录 `renameTo` + copy fallback，因 minSdk 23。

### 7.3 通道失败（不是"不存在"）

- `WebFetch https://developer.android.com/reference/android/graphics/pdf/PdfDocument` → `Connect Timeout`（两次，IPv4 六个地址全超时）；`.../reference/kotlin/android/graphics/pdf/PdfDocument` 同。**改用 AOSP javadoc 源码**（`PdfDocument.java:160-176`），结论不依赖文档站。
- `curl https://raw.githubusercontent.com/...` → `EXIT=28`（超时），改走 `gh api`。
- `aosp-mirror/platform_libcore` → 404（该镜像不存此仓），故 `BufferedOutputStream` 语义引 OpenJDK 17 源码（Android libcore 为同源 ojluni 分支，**逐行同一性未核，标"待验证"**）。

---

## 8. 未验证项与剩余风险

| 项 | 状态 | 影响 |
|---|---|---|
| 设备复跑（painter 24 / PDF 13 / parity 11 / flags 2 / 隔离 13） | **未运行** | KD-79 的关闭条件仍只有"代码已改"，绿轮 UNVERIFIED（与批次 29 记录一致） |
| 本文件所有平台结论的设备侧复现 | **未运行** | 机制来自源码实读，行为来自 E-04 的 API 34 实测（230 条 logcat），两者一致 |
| 真 ENOSPC（内核 partial write + errno） | **不可行，未做** | N-PDF-05 只证明"IOException 类失败被转成有码"，不证明内核路径 |
| 真 `fsync` 失败 | **不可行，未做** | D3 的 sync 分支只有代码审查级保证 |
| PDFium 对截断 PDF 的修复式加载边界 | **未实测** | N-PDF-07 必须先在 API 34 上取基线，再写断言（禁止按猜测定） |
| SkPDF 全量写点是否都不检查返回值 | **部分核实**（`SkPDFDocument.cpp:96-135` 实读） | 若有某处检查并中止，"继续生成"的说法需收窄；不影响"吞掉 + 无出口"的主结论 |
| `PdfDocument.cpp` 在 API 35/36 的逐行同一性 | **只 grep 了 Java/adaptor** | 主结论不依赖（`nativeWriteTo` 的 `void` 在 Java 侧已定死） |
| androidx `AtomicFile` 在 minSdk 23 的运行时行为 | 未实测 | 其 fail-open 语义是源码事实，与运行时无关 |
| `SkPDF` 默认 metadata 下字节确定性 | **未证明** | 故明确否决"预知字节数"判据 |
| 跨 Skia 版本的尾标格式（`%%EOF` 后是否有换行） | 只核 android-14.0.0_r1 | D2 因此建议用"尾窗 1024 B 内出现"而非"精确结尾" |
| **中段静默丢块**（不抛、尾标完整、页数正确） | **D1/D2/D3 均不可见**（§2.2、§3.5） | 已知残留类；只能在 D4 栅格对拍（原型门）下暴露，**不得**作为产品判据；不可用"编一条会绿的用例"掩盖 |
| `MistakePdfDelivery.writeSubset` 的 fail-open 生产面 | **源码实读，未上设备** | 归因清楚（无缓冲 + native 吞），但"打印 spooler 侧是否另有校验"未知；需批次拥有者裁 KD 后再谈验证 |

---

## 9. 来源清单

**AOSP（android-14.0.0_r1 为主）**
- `libs/hwui/jni/CreateJavaOutputStreamAdaptor.cpp` — https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/libs/hwui/jni/CreateJavaOutputStreamAdaptor.cpp#L220-L261
- `libs/hwui/jni/pdf/PdfDocument.cpp` — https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/libs/hwui/jni/pdf/PdfDocument.cpp#L88-L142
- `graphics/java/android/graphics/pdf/PdfDocument.java` — https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/graphics/java/android/graphics/pdf/PdfDocument.java#L160-L176
- `graphics/java/android/graphics/pdf/PdfRenderer.java` — https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/graphics/java/android/graphics/pdf/PdfRenderer.java#L157-L203
- `libs/hwui/jni/pdf/PdfRenderer.cpp` / `PdfUtils.cpp` — https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/libs/hwui/jni/pdf/PdfUtils.cpp#L45-L114

**Skia（android-14.0.0_r1）**
- `src/pdf/SkPDFDocument.cpp` — https://github.com/aosp-mirror/platform_external_skia/blob/android-14.0.0_r1/src/pdf/SkPDFDocument.cpp#L96-L135
- `src/core/SkDocument.cpp` — https://github.com/aosp-mirror/platform_external_skia/blob/android-14.0.0_r1/src/core/SkDocument.cpp#L51-L69
- `include/core/SkStream.h` — https://github.com/aosp-mirror/platform_external_skia/blob/android-14.0.0_r1/include/core/SkStream.h#L238-L260

**OpenJDK**
- `BufferedOutputStream` — https://github.com/openjdk/jdk/blob/jdk-17-ga/src/java.base/share/classes/java/io/BufferedOutputStream.java#L57-L125

**参考实现**
- androidx `AtomicFile` — https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/util/AtomicFile.java#L119-L236
- okio `FileSystem`/`NioSystemFileSystem`/`JvmFileHandle` — https://github.com/square/okio/blob/main/okio/src/jvmMain/kotlin/okio/FileSystem.kt#L152-L159 、 `.../NioSystemFileSystem.kt#L76-L78` 、 `.../JvmFileHandle.kt#L72-L75`
- commons-io `FileUtils` — https://github.com/apache/commons-io/blob/master/src/main/java/org/apache/commons/io/FileUtils.java#L2591-L2646
- `TomRoush/PdfBox-Android` — https://github.com/TomRoush/PdfBox-Android

**仓内**
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainter.kt:66-152`
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterInstrumentedTest.kt:447-456, 479-487`
- `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DeterministicMistakePdfRenderer.kt:151-156`
- `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt:114-175, 215-266`
- `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt:50-76, 114-183, 358-379`
- `docs/research/evidence/2026-10-28-batch-29/step04-pdf-parity.md`（E-04）
- `docs/known-defects.md:2913-2921`（KD-79）、`:412-447`（KD-11）
- `.zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md` §1 第 6 项
