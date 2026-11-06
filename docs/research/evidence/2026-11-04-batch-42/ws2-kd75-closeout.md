# WS2 / KD-75 关闭判据对账（批次 42，2026-11-04）

| 字段 | 值 |
|---|---|
| evidence-id | E-42-WS2-01（旧标识 `WS2-2026-11-04-kd75-closeout` 保留为别名） |
| convention-version | 1 |
| batch | 2026-11-04-batch-42 |
| step | 计划 §2 WS2 items 13–24（`.zcode/plans/plan-2026-11-04-batch-42.md:40-51`） |
| gate-or-kd | KD-75 |
| verdict | **KD-75 维持 open**；(a) AI 图判据已接产品链，(b) 模型解析文本判据仍未落；本轨全部设备/独立读者判据 UNVERIFIED（设备面由 E-42-DEVICE-01 §1 另行给出，且仍不构成 KD-75 关闭） |
| executed-at | 2026-11-03（本机时间；文档名按计划批次 42 的 2026-11-04） |
| operator | WS2 落码轨 |
| head | `faab17f5`（索引整理时 `git rev-parse HEAD` 同值；本文件成文时未自记） |
| device-serial | **N/A**（本轨零设备；AI 图链设备证据见 E-42-DEVICE-01 §1，serial emulator-5584） |
| apk-sha256 | **N/A**（本轨零构建/零安装；设备面绑定见 E-42-DEVICE-01 §0） |
| exit-code | 本轨零 Gradle/零 adb；仅 §1.5 standalone 编译/运行三步 `EXIT=0`（`OK (48 tests)`），无 shell exit code 落盘 |
| raw-log | 无（standalone 编译器输出只在会话内，未落盘；`core:export` JVM/设备用例未编译未运行） |
| record-ref | 计划 WS2 13–24；R1 `docs/research/2026-11-04-pdf-export-injection.md`；批次 41 记录 §9.3（`docs/research/2026-11-03-batch-41-record.md:431-438`） |
| sanitized | 是（无密钥/账号/Cookie） |
| 本轨动作 | 落码 + 测试在树；**零 Gradle、零设备、零 pypdf 执行**（批次纪律：Gradle 只在门禁阶段串行运行） |

---

## 1. 本批落点（可逐 hunk 核对）

| # | 文件 | 改动 | 对应计划项 |
|---|---|---|---|
| 1 | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/`（7 主文件） | 从 `core/data/.../pdf` 纯搬移，包名改 `core.model.pdf`；逐文件 `diff` 仅 package 行不同 | 落位裁定 B1' |
| 2 | `core/model/src/test/kotlin/.../model/pdf/`（6 测试 + `PdfTestFixtures`） | 同上纯搬移 | 落位裁定 B1' |
| 3 | `core/export/.../MistakePdfExporter.kt` | ① `prepare` 在 `findExisting` 前 `ensurePolicyGeneration`；② 大小判限后、`validatedPageCount` 前 `labelPdfInPlace`；③ `reopenVerified` 在 `findExact` 前同一代际清空；④ 新 `paintsAiGeneratedImage` / `labelPdfInPlace` / `ensurePolicyGeneration`；⑤ 哨兵 `.aigc-policy-gen-1`、注入前 12 MiB 守卫 | 13–16、22 |
| 4 | `core/export/src/test/.../MistakePdfExporterAigcJvmTest.kt` | 判据矩阵 6 例 + `labelPdfInPlace` 正/负/失败闭包 3 例 | 15、16、17（JVM 可执行部分） |
| 5 | `core/export/src/androidTest/.../MistakePdfExporterAigcInstrumentedTest.kt` | 真实导出链 5 例（打标/负向/幂等/旧 token/打印整份 vs 部分页） | 18–21、22 |
| 6 | `app/src/androidTest/.../PdfAigcDeviceRoundTripInstrumentedTest.kt` | 6 条 import 改到 `core.model.pdf` | 落位裁定 B1' |

## 1.5 本轨实际执行（standalone Kotlin/JUnit，非 Gradle）

为证明"纯搬移无行为变化"，用 Gradle 缓存里的 Kotlin 2.3.21 编译器与 JUnit 4.13.2 直接编译/运行
`core/model` 的 PDF 包（不启动 Gradle，不违反门禁阶段串行纪律）：

```text
# 主码编译（7 文件）→ EXIT=0
java -cp <kotlin-compiler-embeddable;stdlib;reflect;coroutines;annotations> \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -classpath <stdlib> -jvm-target 17 \
  -d /tmp/pdf-main-out core/model/src/main/kotlin/.../model/pdf

# 测试编译（6 测试 + fixture，classpath 含 junit/hamcrest）→ EXIT=0
java ... K2JVMCompiler -classpath <stdlib;junit;hamcrest;/tmp/pdf-main-out> -d /tmp/pdf-test-out ...

# 运行 → OK (48 tests)，EXIT=0
java -cp <stdlib;junit;hamcrest;/tmp/pdf-main-out;/tmp/pdf-test-out> org.junit.runner.JUnitCore \
  com.tingyun.smartmistakebook.core.model.pdf.{PdfAigcContentPolicyTest,PdfAigcMalformedTest,\
PdfAigcRoundTripTest,PdfAigcSchemaTest,PdfAigcStreamInjectionTest,PdfAigcTableInjectionTest}
```

结果：**48/48 PASS**，与批次 41 记录的源 `@Test` 数一致。**范围限制**：只覆盖 `core/model` 的纯
JVM PDF 包；`core:export` 的主码/JVM 测试（Android 类型）与设备用例仍未编译、未运行。

## 2. 关闭判据逐条对账

判据原文见 `docs/known-defects.md:2898`（Reopen condition）。

| # | 判据 | 当前状态 | 证据（本轨实读/实写） | 判定 |
|---|---|---|---|---|
| C1 | 含 AI 图的导出 PDF 在设备上被**独立读者**读回正确 `/AIGC` | 代码与设备用例在树；**未编译、未跑设备、未跑 pypdf** | `MistakePdfExporter.kt` 注入点；`MistakePdfExporterAigcInstrumentedTest.realExportWithAiImageIsLabeled…` 把产物写到 `filesDir/pdf-aigc-export/run-<stamp>/real-export-labeled.pdf` 并在 logcat 打 `evidence=` | **UNVERIFIED**（未运行） |
| C2 | 含 `MODEL_DOCUMENT_PARSE` 文本的导出 PDF 读回 `/AIGC` | **未实现**：`MistakePdfExportInput` 无 provenance，判据实参固定 `hasModelParsedText = false` | R1 `docs/research/2026-11-04-pdf-export-injection.md` §3.3；本批禁改 `MistakePdfEligibility.kt` / batch / paper-edit / draft 四处 | **FAIL（本批不可达）** |
| C3 | 纯用户内容 PDF 读不到 `/AIGC` | 代码与设备用例在树；未运行 | `MistakePdfExporterAigcJvmTest.labelPdfInPlace leaves pure user content byte identical`；设备 `pureUserContentExportHasNoAigcInfoEntry` | **UNVERIFIED**（未运行） |
| C4 | 旧缓存"指纹已升版且无旧无标识产物" | **未按字面升 v3→v4**；按 R1 §4.3 改为策略代际哨兵 + 一次性清空，覆盖 `prepare` 与 `reopenVerified` 两条路 | `MistakePdfExporter.kt` 的 `ensurePolicyGeneration`；设备 `oldUnmarkedArtifactUnderTheSameKeyIsRejectedAndRebuiltWithAigc` | **部分**：机制在树、未运行；与判据字面不同，需主会话确认可接受替代 |
| C5 | 显式图注逐位不变 | 设备用例以"注入后字节前缀 == 渲染器原始字节"断言；未运行 | `MistakePdfExporterAigcInstrumentedTest` 的 `assertArrayEquals(baseline, labeled[0..baseline.size))` | **UNVERIFIED**（未运行） |
| C6 | 设备 PDFium 接受增量段 | 生产路径在注入后调用 `validatedPageCount`（`PdfRenderer` 真实打开）；用例断言页数不变；未运行 | `MistakePdfExporter.prepare` 顺序；`realExport…` 的 `pageCountOf(baseline) == prepared.pageCount` | **UNVERIFIED**（未运行） |
| C7 | 设备日志落盘（R4）后关闭 | 未跑设备 | — | **UNVERIFIED** |
| C8 | 打印部分页 `writeSubset` 元数据丢失的处置 | 只登记现状：整份保留 `/AIGC`、部分页丢失；重注入落在 `MistakePdfDelivery.kt`（本批禁改） | R1 §6；设备 `wholePrintKeepsAigcWhilePartialPrintDropsTheDocumentLevelEntry` | **未处置**（登记） |

**结论：KD-75 不能关闭。** 关闭所需的设备独立读者证据（C1/C3/C5/C6/C7）全部未执行；(b) 判据（C2）仍缺；C4 与判据字面不一致。

## 3. 门禁阶段必须执行的命令（本轨未运行）

```bash
# 0) 纯搬移核对：应只显示 package 行（git diff -M 识别 rename）
git diff -M --stat -- core/model core/data app/src/androidTest

# 1) 单测（core:model 的 PDF 包 + core:export 的判据/注入）
./gradlew :core:model:test --tests '*PdfAigc*'
./gradlew :core:export:testLocalFirstDebugUnitTest --tests '*MistakePdfExporterAigcJvmTest'

# 2) 编译（export 与 app androidTest；禁止 :app:connectedLocalFirstDebugAndroidTest）
./gradlew :core:export:compileLocalFirstDebugKotlin --rerun-tasks
./gradlew :core:export:assembleLocalFirstDebugAndroidTest --rerun-tasks
./gradlew :app:assembleLocalFirstDebugAndroidTest --rerun-tasks

# 3) 设备：core:export 的 AIGC 用例
adb install -r -t core/export/build/outputs/apk/androidTest/localFirst/debug/core-export-localFirst-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.export.MistakePdfExporterAigcInstrumentedTest \
  com.tingyun.smartmistakebook.core.export.test/androidx.test.runner.AndroidJUnitRunner

# 4) 独立读者：拉回设备证据文件后
adb shell run-as <app-pkg> cat files/pdf-aigc-export/run-<stamp>/real-export-labeled.pdf > /tmp/labeled.pdf
python -I -c "import pypdf,sys; r=pypdf.PdfReader(sys.argv[1]); print(len(r.pages)); print(r.metadata)" /tmp/labeled.pdf
# 期望：/AIGC 为 7 键 JSON，Label=1、ProduceID=输入指纹；pure-user-unlabeled.pdf 无 /AIGC
```

## 4. 残余风险（登记，不本批修）

1. **(b) 判据缺失**：含 `MODEL_DOCUMENT_PARSE` 文本的导出仍可能漏标 → 补判据要改四个禁改文件，并同步 `AIGC_POLICY_SENTINEL` 代际号。
2. **指纹字面未升版**：C4 靠哨兵清空而非 `mistake-export-v3 → v4`；跨渠道/降级构建若哨兵名未换代，仍有旧产物命中窗口（R1 §4.5）。
3. **部分页打印丢标**：`writeSubset` 重绘新 `PdfDocument`，文档级 `/AIGC` 丢失；重注入方案在 R1 §6.2，落 `MistakePdfDelivery.kt`（禁改）。
4. **注入器峰值内存**：12 MiB 守卫挡住 24 MiB 批量上限；超限导出变为明确 `PDF_SIZE_LIMIT_EXCEEDED`（R1 §5.3）。
5. **独立读者未执行**：pypdf 结果必须由门禁/设备轨回填，不能用 `PdfAigcReader` 自证替代。
