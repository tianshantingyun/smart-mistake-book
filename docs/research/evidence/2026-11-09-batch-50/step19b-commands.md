# 批次 50 · item 19–20 证据：轻量 CV 版面预处理 + 结构提示进 wire

- 日期：2026-11-09（本会话）
- 轨：切分 CV 预处理（计划 `.zcode/plans/plan-2026-11-09-batch-50-release.md` item 19–20）
- 设计依据：`docs/research/2026-11-09-split-cv-llm.md`（item 19 研究 + Kotlin 参考实现 + 参数实测）
- 范围声明：**只跑 dev 16 页**；**acceptance 8 页本会话零运行**（没有解码、没有几何测量、没有模型调用）。
  provider 侧（LLM-only / LLM+hint 两臂）与 `check_split_gate.py` **本会话未跑**（见 §5）。

## 1. 改动文件（本轨）

| 文件 | 类型 | 说明 |
|---|---|---|
| `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/layout/LayoutStructure.kt` | 新增 | 生产实现：Otsu 二值、纸面裁边窗、行谷分块、栏谷分栏（fail-closed）、题号候选（度量用）、提示块渲染 |
| `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt` | 改（最小 hunk） | `CaptureAssessmentInput.layoutHint`（`@EncodeDefault(NEVER)`）+ `MAX_CAPTURE_LAYOUT_HINT_CHARS = 1200` |
| `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTaskAdapters.kt` | 改（最小 hunk） | 单页切分提示词末尾逐字追加提示块；多页关系变体不追加 |
| `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/PageLayoutHint.kt` | 新增 | Android 端口：出网副本 → Bitmap → 灰度 → `LayoutStructure` → 提示块；失败一律 null |
| `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiCompatibleModelGateway.kt` | 改 | 读图后算提示、预算按带提示的输入记账、`requestBody` 用同一提示值；生产工厂显式装配 |
| `core/model/src/test/.../layout/LayoutStructureTest.kt` | 新增 | 12 例合成页面回归锁（含"候选不进提示词"） |
| `core/model/src/test/.../layout/LayoutStructureDevParityHarness.kt` | 新增 | dev 16 页 × Python 探针逐页对拍（夹具缺失即 skip，不伪造通过） |
| `core/model/src/test/.../LayoutHintContractTest.kt` | 新增 | 6 例：无 hint 指纹不漂、有 hint 是新请求、超限拒绝、预算同值 |
| `core/data/src/test/.../CaptureAssessmentLayoutHintPromptTest.kt` | 新增 | 4 例：追加不改句式、关系变体不带、请求体真的带上 |
| `core/data/src/androidTest/.../PageLayoutHintInstrumentedTest.kt` | 新增 | 3 例设备：真实 JPEG 两栏页出提示、坏字节退回 null、灰度 ITU-R 601 |

## 2. 先红后绿（真跑，命令与读数）

### 2.1 红（实现前的契约用例）

```text
./gradlew :core:model:test --tests "com.tingyun.smartmistakebook.core.model.layout.*" \
  --tests "com.tingyun.smartmistakebook.core.model.LayoutHintContractTest" --console=plain
→ BUILD FAILED（:core:model:compileTestKotlin）
e: .../LayoutHintContractTest.kt:37:28 Unresolved reference 'layoutHint'.
e: .../LayoutHintContractTest.kt:92:52 Unresolved reference 'MAX_CAPTURE_LAYOUT_HINT_CHARS'.
e: .../LayoutHintContractTest.kt:111:13 No parameter with name 'layoutHint' found.
```

**如实边界**：这次红是**编译级**（新字段/新 API 尚不存在），不是行为断言失败。
`LayoutStructure` 的算法本体来自 item 19 已实跑并 16/16 对拍过的 Kotlin 参考实现，
它是以"移植 + 回归锁"形式落地的，不是从零 TDD。

### 2.2 行为级红→绿（设备，灰度口径）

```text
adb -s emulator-5590 shell am instrument -w -e class ...PageLayoutHintInstrumentedTest ...
# 第一次：Tests run: 3, Failures: 1
#   grayConversionUsesItuR601Luma: expected:<150> but was:<149>
```

修的是**实现**（不是断言）：灰度从截断改为四舍五入，与探针 `PIL.convert("L")` 同口径
（`0.587×255 = 149.7`）。重跑：

```text
OK (3 tests)
```

### 2.3 绿：模块与相关类

```text
./gradlew :core:model:test --rerun --console=plain
→ BUILD SUCCESSFUL；69 个测试类 / 674 tests / 0 failures / 0 skipped
   （含新增 3 类：LayoutStructureTest 12 例、LayoutStructureDevParityHarness 1 例、LayoutHintContractTest 6 例）

./gradlew :core:data:testDebugUnitTest --tests "…CaptureAssessmentLayoutHintPromptTest" --console=plain
→ BUILD SUCCESSFUL；4 tests / 0 failures

./gradlew :core:data:testDebugUnitTest \
  --tests "…OpenAiCompatibleModelGatewayTest" \
  --tests "…OpenAiCaptureAssessmentAdapterTest" \
  --tests "…OpenAiModelProtocolMockWebServerTest" --console=plain
→ 81 tests completed, 3 failed
   3 个失败全部是**批次 49 已登记的既有失败**（同三条、同异常）：
   docs/research/evidence/2026-11-08-batch-49/unit-gate.md:85-87
   `ModelEgressAuthorizationException: 当前模型尚未验证这项能力…`
   本轨改动（读图后加提示）不在该异常路径上；**不记为本轨通过，也不记为回归**。

./gradlew :core:data:assembleDebugAndroidTest --console=plain
→ BUILD SUCCESSFUL
adb -s emulator-5590 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
→ Success
adb -s emulator-5590 shell am instrument -w -e class …PageLayoutHintInstrumentedTest …
→ OK (3 tests)
```

设备：`emulator-5590`（x86_64 / API 34）——**非真机**。

## 3. dev 16 页：Kotlin 生产实现 × Python 探针逐页对拍

命令：

```bash
./gradlew :core:model:test --tests "com.tingyun.smartmistakebook.core.model.layout.LayoutStructureDevParityHarness" --console=plain
```

夹具与参考（均为 `build/` 下的 gitignored 产物，由 item 19 的探针命令导出，本会话未重跑探针）：

- `.gray`：`build/layout-probe/gray/*.gray`（4 字节小端宽 + 4 字节小端高 + w×h 灰度）
- 参考读数：`build/layout-probe/dev-dump.json`
- 金标：`tools/split_golden/tables/split_golden_v2.json`（G1，24 页；只读 `split.dev`）

逐页结果：见 `step19b-dev-parity.txt`。汇总：

| 量 | 本轨（生产 Kotlin） | item 19 研究（Python 探针） | 判定 |
|---|---|---|---|
| 栏判与探针一致 | **16/16**（逐页 assertEquals） | 16/16 | 一致 |
| 行带块数与探针偏差 | **最大 2** | ±2（边缘钳位平滑差异） | 一致 |
| 栏判与金标 `layout` 不一致页 | **2**：`…04854`、`…11649` | 2（同两页，已登记例外） | 一致 |
| 提示块字符数 | **113–286** | 113–286 | 一致 |
| 题号候选数 / 金标题数 | 313 / 50 | 315 / 52 | 同量级 |
| 候选命中率（±1.5% 页高，1:1） | **0.1086** | 0.1111 | 一致（结论不变） |

**结论（与本轨设计一致，不得被读成"提示有效"）**：几何测量在 Kotlin 生产实现上与独立
Python 实现在 dev 16 页逐页对得上；题号候选的命中率仍是 0.11 量级，**因此候选不进提示词**
（`questionCandidatesNeverReachThePromptBlock` 是回归锁），进 prompt 的只有"纸面范围 / 单双栏
+ 栏谷 / 行距 / 段落分隔（≤8 条，按谷宽排序）"这些**测量值**。

### 3.1 三个 `0.5714` 失败页的几何读数（只读，不涉模型）

| 页 | 归属 | 本轨读数 |
|---|---|---|
| `IMG20260810005605.jpg` | v1 金标失败页；v2 **dev** | 单栏 / 14 块 / 行距 20 px / 提示 266 字符 |
| `IMG20260810011126.jpg` | v1 金标失败页；v2 **dev** | 单栏 / 16 块 / 行距 12 px / 提示 265 字符 |
| `IMG20260810004353.jpg` | v1 金标失败页；v2 **acceptance** | **未运行**（验收集一次性红线） |

如实说明：这三页当初的失败是"模型 0 框（漏拆）"，本轨只证明**现在有几何提示可发**；
"发了之后模型会不会给出 2 个框"在本会话**没有证据**（零 provider 调用）。

## 4. wire 契约（本轨唯一的行为面改动）

- `layoutHint = null` → 请求体/指纹与升级前**逐字节相同**（`LayoutHintContractTest` 三例钉住）。
- `layoutHint` 设值 → 进 `CaptureAssessmentInput` 编码与 `ModelTaskFingerprint`（新请求，预期）。
- 提示块上限 1200 字符，与渲染端 `PageStructure.MAX_HINT_CHARS` **同值**（用例钉住），
  超限在 `CaptureAssessmentInput.init` 当场拒绝，不静默截断。
- 只追加在**单页切分**提示词末尾；**多页关系变体不携带**（关系回合不分题）。
- 预算：`ProviderConsented` 路径的 `nonImageJsonUtf8Bytes` 用**带提示的输入**复算，
  所以"记账的请求"与"发出的请求"是同一份。

## 5. 未做 / 未验证（如实）

| 项 | 状态 | 原因 |
|---|---|---|
| acceptance 8 页 任何测量 | **未跑** | 验收集一次性；LLM 臂未就绪前跑它只会白白烧掉一次 |
| LLM-only / LLM+hint 两臂 | **未跑** | 本会话零 provider 调用：环境里没有 `SMB_REAL_PROVIDER_KEY`；`local.properties`（已 gitignore）里有一枚明文 key，**本轨没有用它发起任何请求**，也不把它复制进任何证据文件。建议：轮换该 key 并从 `local.properties` 移到环境变量/密钥库 |
| `check_split_gate.py` | **未跑** | 没有新的应用导出；跑它只会复算旧 4C 导出 |
| 门值 | **未变** | 阈值/口径/分母/金标零改动；`0.5714` 仍是当前门值 |
| 真机耗时 | **未测** | 只有 `emulator-5590`；单页 JVM 侧 1.8–28 ms（item 19），Android 侧解码耗时未测 |
| 坐标系归一（EXIF/旋转） | **未做** | 研究 §3.6 的硬前置；`…04854` 一页仍"坐标系未统一"，金标需 v3 补 `frame` |
| 题号候选进产品 | **刻意不做** | dev 实测命中率 0.1086（基准率 0.165）→ 会同时输给模型与门；候选只用于度量 |

## 6. 已登记的风险（本轨引入）

1. **提示不进指纹/不落库**：`layoutHint` 由网关从**出网副本**算出（只有那里才有与模型同源的字节），
   因此 `ModelTaskRequest` 里存的是 `layoutHint = null`。同一 request fingerprint 在
   **CV 参数变更后**可能对应不同的 wire 文本（同资产 sha 下确定性不变）。
   修法是把提示改成"派发前由调用方写入的输入字段"（研究 §1.2 的形态）——需要调用方持有页面字节，
   本会话未做（涉及 capture/batch 两条调用链的字节通路）。**引用本轨时不得声称"wire 文本被指纹覆盖"。**
2. **`PageLayoutHint.None` 是构造参数默认值**：JVM 单测缝。生产装配已显式传 `AndroidPageLayoutHint`
   （`ConfiguredModelGatewayFactory.create`），但"漏传即静默无提示"这条风险仍在——
   没有用例能证明生产工厂装配正确（工厂本身在 Android 侧）。
3. **dev 判出双栏仅 4 页**（`…04854` 旋转页、`…11649` 暗光页是已登记误判；真双栏 2 页），
   **acceptance 8 页 0 张双栏**：栏逻辑在验收集上是盲区（item 19 已登记）。
