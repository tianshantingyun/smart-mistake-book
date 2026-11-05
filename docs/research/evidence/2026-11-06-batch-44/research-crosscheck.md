# 批次 44 §0 研究结论交叉复核（研究交叉复核员）

> 复核对象：`.zcode/plans/plan-2026-11-05-batch-44-acceptance.md` §0（`:8`–`:13`）六条"深度研究结论"，以及它们对 §1/§2/§6 的取舍（计划 `:88` 不引新依赖、`:68` J 域 16KB/AAB、`:63` H 域 AIGC、`:65` I 域无障碍、`:55` F 域稳定）。
>
> 复核日期：2026-11-04（本文所有在线抓取均在本轮完成；离线事实均为本轮实测，不转录既有记录）。
>
> 判定口径：外部断言要"一手原文 + 独立第二来源"才算**成立**；只拿到二手转述或只拿到论文编号以外信息的，记**部分成立**并给出缺口；拿不到原文的记 **UNVERIFIED**，不当成立。仓内断言要"源码/命令输出"直证。

## 0. 本轮实际做了什么（可复跑）

| 类别 | 动作 |
|---|---|
| 官方文档 | 抓取 `developer.android.com/guide/practices/page-sizes`（页面自述 Last updated 2026-10-16）、`/training/testing/ui-tests/screenshot`（2026-10-08）、`/develop/ui/compose/accessibility/testing`（2026-10-22）全文正文 |
| 论文 | 抓取 arXiv `2310.00338`、`2412.20692` 的 abs 页；抓取 `flakeecho.github.io` 正文 |
| 法规/标准 | 抓取国标平台 `openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=F32EA2A561F1886CD8D606513512D547`（GB 45438-2025 元数据页）；4 引擎聚合检索《标识办法》施行信息 |
| 第三方库 | GitHub API 实查 `cashapp/paparazzi`、`takahirom/roborazzi` |
| 仓内实测 | 对现存 release APK 跑 `zipalign -c -P 16 -v 4`；用自写 ELF 解析器读 14 个 `.so` 的 `PT_LOAD.p_align`/`PT_GNU_RELRO`；解开 AAB/APK 比对 ABI 与 zip 方法；`adb devices`/`getconf PAGE_SIZE`/`screencap`/`pm list packages` 探活 |

未做（明确声明）：未跑 Gradle（纪律：Gradle 只在门禁阶段串行跑）、未跑 adb androidTest、未取《标识办法》与 GB 45438-2025 条款原文、未取 Android 官方稳定性页正文。

## 1. 六条结论逐条判定

| # | 计划 §0 原话（`:行`） | 判定 | 关键证据 |
|---|---|---|---|
| A | `:8` "Google Play 2026 检查面 = 签名/AAB/16KB 页大小/API 级别/data safety/封闭测试；16KB 需检查 `.so` 的 `p_align` 与 AAB 内 native 库" | **部分成立，时间表与检查面都须改** | 官方页写明 16KB 强制点是 **2027-02-01**（不是 2026）；`p_align` 只是四项检查之一，**RELRO 漏了**；`普通封闭测试`只对 2023-11-13 之后创建的个人账号生效 |
| B | `:9` "官方推荐截图测试验证视觉属性；三条路线 Paparazzi / Roborazzi / Compose Preview Screenshot Testing（官方页）" | **半对半错：前半成立，后半不成立** | 官方页原句 "Screenshot testing is the recommended way to verify visual attributes in Compose UIs"；但同页只点名**官方 Compose Preview Screenshot Testing**（且标 *experimental*）+ "third-party tools"，**从未列出 Paparazzi/Roborazzi**（二者是 Cash App / takahirom 的第三方 Apache-2.0 项目） |
| C | `:9` "本仓不引新依赖：用设备 `adb exec-out screencap` + **仓内像素差工具**做基线回归" | **前提事实错误** | `tools/ci/` 全目录无任何截图/像素差脚本（见 §3.1 枚举）；像素 diff 需新写（PIL 已有先例，见 §2 M3） |
| D | `:10` "Compose 语义树 + `enableAccessibilityChecks`（**espresso-accessibility**）+ TalkBack 实机；官方明确自动检查不能替代 TalkBack 人工" | **结论成立，构件名错** | 官方页给的是 `ui-test-junit4-accessibility` + Compose **1.8.0+** 的 `enableAccessibilityChecks()`；espresso 的 accessibility 是另一条 View 系路径。官方原话 "manual testing … essential way" / "In conjunction with manual verification"，故"残余 UNVERIFIED"判断正确 |
| E | `:11` "蜕变测试（arXiv 2310.00338、2412.20692）" | **成立** | 2310.00338 = *Towards a Complete Metamorphic Testing Pipeline*（Duque-Torres & Pfahl, cs.SE）；2412.20692 = *Test Adequacy for Metamorphic Testing: Criteria, Measurement, and Implication*（Fu, Sun, Zhang, Liu, cs.SE）。两篇都是蜕变测试，编号与主题对得上 |
| F | `:12` "官方稳定性指南 + FlakeEcho 时序 flaky 研究" | **来源真实，但 FlakeEcho 不可采用** | `flakeecho.github.io` 真实：ISSTA 2024 投稿 *Reproducing Timing-dependent Flaky Tests in Android Apps via A Single Delay*，80 例/22 应用/73 例 1.71 轮复现。**但其前置是 EdXposed + API 30 镜像 + AGP 8.0**，本仓 AGP 9.3.0、无 Xposed，不能落地，只能当方法论引用 |
| G | `:13` "2025-09-01 起《标识办法》+ GB 45438—2025 双轨（显式+隐式元数据）；本仓 `/AIGC` 注入对应隐式标识" | **日期与标准号成立；"`/AIGC`=隐式"只对 PDF 成立，条款判据 UNVERIFIED** | 国标平台页：GB 45438-2025，发布 2025-02-28、**实施 2025-09-01**、归口中央网信办、现行；《标识办法》同日施行有两条独立页面佐证（`https://zlzx.zjamr.zj.gov.cn/...` 与 `https://ntc.whpu.edu.cn/info/1005/2222.htm`，均写两文件 2025-09-01 起实施）。但**条款原文未能取得**（平台只提供浏览器全文预览），仓内自己已标"二手转述待验证" |

## 2. 必须修正的假设（按危害排序）

### M1（高）16KB 时间表写错年份，会误导"本批必须跑运行时 16KB"
官方页原文（本轮抓取）："all apps targeting Android 15 (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on Google Play. **Starting February 1, 2027**, if your app updates don't support 16 KB memory page sizes, you won't be able to release these updates."
- 修正：16KB 是 **2027-02-01** 的更新门槛，不是 2026 的发布门槛；但本仓 `targetSdk = 36`（`app/build.gradle.kts:29`）落在 "API 35+" 范围内，所以**静态自检现在就该做**，只是不要写成"2026 Play 检查面"。
- 连带：`docs/kb-architecture-refactor-decisions-2026-09-21.md:48` 写的"2027-02 起影响 API 35+ 发布"与官方一致，是对的；计划 §0 反而是把时间提前了一年。

### M2（高）16KB 检查面缺三项，且"只看 p_align"会漏真实崩溃形态
官方页给出的检查是四件套，缺一不可：
1. `llvm-objdump -p lib.so | grep LOAD`，要求每个 LOAD 的 `align ≥ 2**14`；
2. **`GNU_RELRO` 的 `(VirtAddr + MemSiz) % 0x4000 == 0`** ——当 RELRO 与后续可写数据共处一个 LOAD 段时；这是 NDK r27 及以下构建的 `.so` 在 16KB 设备**段错误**的直接成因，`.p_align` 合规也照样崩；
3. `zipalign -c -P 16 -v 4 app.apk` 必须 "Verification successful"；
4. AAB 侧 `bundletool dump config --bundle=app.aab | grep alignment` 必须是 `PAGE_ALIGNMENT_16K`；另需 AGP ≥ 8.5.1（本仓 9.3.0，`gradle/libs.versions.toml:2`，满足）、NDK r28+（本仓 native 全部来自 AAR，不自建：`core/data/build.gradle.kts:229-234` 说明 quickjs 由 `quickjs-kt-android` 1.0.15 自带）。
额外两条被计划漏掉：
- **代码面 `PAGE_SIZE`/`mmap` 假设**（官方"Fix code and resolve runtime issues"）：主源无 `getpagesize`/`PAGE_SIZE` 调用；`tools/ci/` 里唯一命中是 `tools/ci/kd76_identity.py:194`（`getconf PAGE_SIZE` 采设备身份，不是扫描器）→ **代码面无扫描器**。
- **运行时门其实已存在**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/NativeLibraryPageSizeInstrumentedTest.kt`（批次 21 T6）。但它的 `runtimePageSizeIs16KB`（`:50`–`:60`）用 `assumeTrue(pageSize == 16384L)`（`:53`）→ 在 4KB 装置上**记为 skipped**；装载用例只覆盖 `quickjs`/`LiteRt`/`litert_jni` 三件，未覆盖 `libmlkit_google_ocr_pipeline`/`libandroidx.graphics.path`/`libdatastore_shared_counter`。
- 修正：J 域 16KB 项按上述 6 条拆分，且必须写明 CONFIG 前置（NDK/llvm-objdump、bundletool **本机均未安装**，见 §3.4），否则该项只能是 BLOCKED 而不是 PASS；运行时项引用既有用例并**按 skipped 判 BLOCKED，不得按"无失败"判绿**。

### M3（中）"仓内像素差工具"不存在；但"不引新依赖"仍成立
本轮枚举 `tools/ci/` 全部 31 个条目（29 个脚本/清单 + `__pycache__` + 1 个 `.ci.patch`），无 `screenshot_baseline.py`/`a11y_audit.py`/`acceptance_*.py`/`metamorphic_checks.py`/`trace_requirements.py`；`docs/acceptance/` 目录不存在。§0 `:9` 的"仓内像素差工具"是把 §1 D0 的**待建**交付物写成了**已有**事实。
- 修正：措辞改为"本批新建 `tools/ci/screenshot_baseline.py`"。可行性有据：仓内已经用 PIL 做图像比对（`tools/kb_coverage/dedupe_scans.py:28` `from PIL import Image`、`:74` `from PIL import Image, ImageChops`；`tools/kb_coverage/classify_page_kind.py:46`），所以"不引新依赖"这一取舍**成立**，只是工具要现写。

### M4（中）Paparazzi / Roborazzi 不是官方推荐路线
官方截图页正文（本轮抓取）只写："You can use third-party tools to create both instrumented and local screenshot tests. If you use Compose, you can use the official Compose Preview Screenshot Testing tool."，且该工具标注为 *experimental*。页面的工具分类维度是"host vs device + Layoutlib vs Robolectric Native Graphics"，**没有**给 Paparazzi/Roborazzi 任何点名背书。
GitHub API 实查（本轮）：`cashapp/paparazzi`（2,638★，Apache-2.0，pushed 2026-11-03，描述"Render your Android screens without a physical device or emulator"）、`takahirom/roborazzi`（1,078★，Apache-2.0，pushed 2026-11-01）。二者都是第三方、host 侧。
- 修正：§0 `:9` 删掉"官方推荐…三条路线"，改为"官方推荐截图测试作为 Compose 视觉属性验证手段（推荐的是官方 experimental 的 Compose Preview Screenshot Testing 与第三方库）；本仓选择设备截屏 + 自研像素差，**这是偏离官方推荐的自担风险路线**"，并在 J/C 域登记理由与残余风险。

### M5（中）设备截屏路线在本仓有已知失败史，基线必须绑定单一装置
官方页明写"Different platforms (Linux, Mac, Windows) produce slightly different screenshots"、"changes in libraries, operating systems or **hardware architecture** can create small changes"。设备截屏把这三类方差全部引入。本仓历史证据：`docs/research/2026-10-14-4c-stage1-record.md:64`（3 张全黑帧、15.5–15.9KB）、`docs/research/2026-10-16-verdict-chain-and-nine-track-batch-record.md:250`（4 帧 `exec-out screencap` 返回全黑 15,580B，同时进程存活/UI 树完整 → 判为宿主取帧异常）。
本轮实测这条路线**当前可用**：`emulator-5584` 上 `adb exec-out screencap -p` 得到 1,380,189 B PNG（PNG magic `89 50 4e 47`），灰度 min 6 / max 255 / 250 个不同灰度值 → 有内容；历史上被判定为黑帧的那几帧是 ~15.5KB（量级差两个数量级），本帧 1.38MB。
- 修正：screenshot_baseline.py 必须（a）先做"非黑帧/非纯色"前置断言，失败即 INVALID 而不是基线不匹配；（b）基线绑定 `serial + API + 密度 + 字体缩放 + 亮暗色`，写进文件名与 JSON；（c）跨装置不共享基线。

### M6（高）无障碍自动检查的构件名错，且会撞"不引新依赖"
官方页（本轮抓取）："add the **`ui-test-junit4-accessibility`** dependency, call `enableAccessibilityChecks()` in the `AndroidComposeTestRule`"，"starting with **Compose 1.8.0**"。计划写的是 "espresso-accessibility"——那是 `com.google.android.apps.common.testing.accessibility.framework` 的 View/Espresso 路径，Compose 场景不对。
仓内事实：`app/build.gradle.kts:200`–`:206` 的 androidTest 依赖里只有 `compose-ui-test-junit4`、`androidx.test.espresso.core`、`espresso-intents`（`gradle/libs.versions.toml:27,:100-101`），**没有** accessibility 构件；本地 Gradle 缓存 `~/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/` 下无 `ui-test-junit4-accessibility`，缓存根目录下也没有任何 `*accessibility*` 组（连 `ui-test-junit4` 都不在缓存里）。BOM 为 `compose-bom = "2026.06.00"`（`gradle/libs.versions.toml:4`），缓存中 `androidx.compose.ui/ui-android` 的实际版本是 **1.11.3** → 满足 ≥1.8.0 的 API 前提（该版本号取自缓存目录，非 Gradle 解析输出）。
- 修正：I 域自动检查项要么（a）按 §6 `:88` 走"单独提交 + 供应链复核"引入 `ui-test-junit4-accessibility`（API 前提已满足，构件需联网拉取）；要么（b）降级为"UIAutomator 语义树导出 + 自写规则（48dp 触摸目标、contentDescription 非空、对比度按像素采样）"，后者零依赖但只覆盖官方框架检查的子集，必须在台账里写明覆盖差。

### M7（中）FlakeEcho 只能引用方法论，不能引用工具
`flakeecho.github.io` 的 Prerequisites 明确：API 30 模拟器 + **EdXposed Framework** + AGP 8.0.0 + YAMP 插件。本仓 AGP 9.3.0（`gradle/libs.versions.toml:2`），无 Xposed，且引入 Xposed 属安全面禁改。它的可用结论只有一条方法学：**时序 flaky 应"定点复现失败"而不是"多跑几次洗绿"**——这条正好支撑计划"不用重试洗绿"。
- 修正：§0 `:12` 把 FlakeEcho 标为"方法学参考（工具不可用）"，并删掉任何"用 witness/重复运行即达官方稳定"的暗示。

### M8（高）稳定性/低负载窗在本机不可能 PASS，F 域条目会继承 INVALID
批次 43 收口已裁定：`LowLoadProbe` 分母改动是**放宽**、本机地板 **0.475 > 0.30 上限**、isolation 只能记"15 例真实执行 + witness 齐全"，禁写因果/T4 排除（`docs/research/2026-11-05-batch-43-record.md:562`–`:565`、`:575`）。计划 §2 F 域"长时 2h / ANR / 进程死亡恢复 / 后台限制"若沿用同一装置与同一低负载判据，结论只能是 BLOCKED。
- 修正：F 域每项标注"该判定是否依赖低负载窗"；依赖者先写 runbook（换机或另行裁定 nproc 伸缩），不得计 PASS。

### M9（高）AIGC 显式标识的判据文本仍是一手缺口，H 域不能靠"逐项核对"自动收口
- 已成立部分：GB 45438-2025 元数据（发布 2025-02-28、实施 2025-09-01、归口中央网信办、现行）来自国标平台；《标识办法》2025-09-01 施行由多条独立来源一致（多源检索，本轮引擎聚合返回）。
- 未成立部分：**条款原文取不到**。`openstd.samr.gov.cn` 该标准页只提供"在线预览/下载标准"，本轮抓取拿不到正文；仓内代码自己标注了同一缺口：`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt:14` "文案含"AI"与"生成"两要素（GB 45438-2025 对文字提示的要素口径，**二手转述**，见 R3 §2.2 V1 待验证）"。
- 另有一处与计划表述不一致：计划 `:13` 说"本仓 `/AIGC` 注入对应隐式标识"。实测 `/AIGC` 只在 PDF 链存在（`core/export/.../MistakePdfExporter.kt:197,:201,:235`、`MistakePdfEligibility.kt:457`、`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/pdf/PdfAigcContentPolicy.kt:6-21`），DOCX 链没有 `/AIGC` 机制但**有**显式图注（`DocxMistakeRenderer.kt:32` 引用 `AiGeneratedImageCaption`）。这与批次 43 的 KD-75 收窄口径一致（`docs/research/2026-11-05-batch-43-record.md:573`）。
- 修正：H 域按"格式 × 标识类型"矩阵拆：PDF(显式+隐式)、DOCX(仅显式)、自然语言改卷文本(KD-85 缺口)；并把"文字提示要素"标 UNVERIFIED，除非本批拿到标准正文。

### M10（中）"每项落到…证据文件之一"是自证漏洞
计划 §5 `:81` 允许四项之一，其中"证据文件"可以是一支手写的 md。这与用户"禁止拿 summary 当证据""PASS 需 ≥2 类独立证据"正相冲突。
- 修正：PASS 判定只承认**机器产物**（测试 XML / 命令日志 / 设备原始输出 / 工具 JSON）；md 只作索引与解释，不得单独支撑 PASS。

### M11（中）16KB/AAB 的现存产物不可用
见 §3.3：现存 `app-localFirst-release.aab` 是 **2026-09-30** 的旧包，且**仍带 `armeabi-v7a`/`x86`**（与 `app/build.gradle.kts:45-47` 的 `abiFilters` 冲突），`bundletool` 未安装。
- 修正：J 域"16KB/AAB 检查"必须显式包含"重新 `:app:bundleLocalFirstRelease` + `bundletool dump config`"，否则该项只能是 FAIL/ BLOCKED，不能拿旧 AAB 出 PASS。

### M12（高）"skip 即假绿"是本批最可能的系统性假阳性源
现实例：`NativeLibraryPageSizeInstrumentedTest.runtimePageSizeIs16KB` 用 `assumeTrue`（`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/NativeLibraryPageSizeInstrumentedTest.kt:53`）。本轮实测 4 台装置 `PAGE_SIZE` 全为 4096（§3.5）→ 该用例会以 **skipped** 收场；JUnit XML 里的 `failures=0` 会让"无失败"读起来像 PASS。仓库里同类 `assume*`/条件跳过不止这一处（本批 1000 项里凡"装置不满足前提"都会走这条）。
- 修正：`acceptance_report.py` 必须把 `skipped` 与 `failures` 分开统计，**skipped 一律映射 BLOCKED**（不是 PASS、也不计入"已验"分母）；`acceptance_lint.py` 增加一条"skip 不得出现在 PASS 证据链"的规则。这条同时是 D0 项 3/4/15（历史假绿回放）的必测样例：用这支用例在 4KB 装置上的 XML 当"skip 冒充绿"的负例。

## 3. 本轮一手实测（可直接作为 J 域基线证据）

### 3.1 工具面枚举（证 M3）
`tools/ci/` 现有 31 个条目（29 个脚本/清单 + `__pycache__` + `check_backup_plan_sync.ci.patch`：`a6_plan.py`、`check_*.py`、`isolation_*`、`kd76_*`、`lowload_gate.py`、`run_kb_checks.py` 等），**无**：`acceptance_report.py`、`acceptance_lint.py`、`trace_requirements.py`、`metamorphic_checks.py`、`screenshot_baseline.py`、`a11y_audit.py`、`verdicts/`。`docs/acceptance/` 不存在。

### 3.2 依赖面（证 M6）
```
gradle/libs.versions.toml:2   agp = "9.3.0"
gradle/libs.versions.toml:4   compose-bom = "2026.06.00"
gradle/libs.versions.toml:27  espresso = "3.7.0"
app/build.gradle.kts:200-206  androidTest: compose-ui-test-junit4 / test.ext.junit / espresso.core / espresso.intents（无 accessibility 构件）
~/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-android  →  1.11.3（Compose UI 实际版本，满足 ≥1.8.0）
缓存中确有：ui / ui-android / ui-geometry-android / ui-graphics-android / ui-test-manifest / ui-text-android /
            ui-tooling* / ui-unit-android / ui-util-android
缓存中没有：ui-test-junit4、ui-test-junit4-accessibility、任何 *accessibility* 组
```

### 3.3 16KB 静态面实测（APK）
命令与输出（本轮实跑）：
```
C:/Android/Sdk/build-tools/36.0.0/zipalign.exe -c -P 16 -v 4 \
  app/build/outputs/apk/localFirst/release/app-localFirst-release.apk
→ 末行 "Verification successful"，EXIT=0
```
APK 内 `.so` 共 14 个（arm64-v8a + x86_64 各 7），全部 `method=0`（STORED，未压缩）——满足"未压缩共享库才谈对齐"的前提。

ELF 段实测（自写解析器读 `PT_LOAD.p_align` 与 `PT_GNU_RELRO`，脚本落在仓外临时目录，不入仓）：
```
lib/arm64-v8a/libLiteRt.so                   p_align=[0x4000,0x4000,0x4000] OK RELRO own-LOAD
lib/arm64-v8a/libLiteRtClGlAccelerator.so    p_align=[0x4000,...]         OK RELRO own-LOAD
lib/arm64-v8a/libandroidx.graphics.path.so   p_align=[0x4000,...]         OK RELRO shared-LOAD mod16k=8192
lib/arm64-v8a/libdatastore_shared_counter.so p_align=[0x4000,...]         OK RELRO own-LOAD
lib/arm64-v8a/liblitert_jni.so               p_align=[0x4000,...]         OK RELRO own-LOAD
lib/arm64-v8a/libmlkit_google_ocr_pipeline.so p_align=[0x4000,...]        OK RELRO own-LOAD
lib/arm64-v8a/libquickjs.so                  p_align=[0x4000,0x4000,0x4000] OK RELRO own-LOAD
（x86_64 七件同形；仅 libandroidx.graphics.path.so 的 RELRO 余数为 12288）
```
**14/14 的 `PT_LOAD.p_align` 都是 `0x4000`（16KB）** → 官方的第 1 项检查通过。

RELRO 的两条"余数非零"要按官方**前置条件**判，不能一律判违规：
- arm64 `libandroidx.graphics.path.so`：LOAD#2(RW) `vaddr=0x5c40 memsz=0x390`（止于 `0x5fd0`），`GNU_RELRO vaddr=0x5c40 memsz=0x3c0`（止于 `0x6000`），`0x6000 % 0x4000 = 0x2000`。它所在的 16KB 页是 `0x4000–0x8000`，**该页后继没有任何可写数据**（下一个可写 LOAD 在 `vaddr=0x9fd0`，落在 `0x8000` 之后的新页）。
- x86_64 同形：RELRO 止于 `0x7000`，页 `0x4000–0x8000`，后继可写 LOAD 在 `0xa268`。
- 官方原文的公式前置是"when `GNU_RELRO` shares a `LOAD` segment with subsequent writable data"，且给出"RELRO 独占 LOAD 段时余数非零仍合规"的示例。据此本轮判定：**两例不构成违规**（这是我对官方示例的推断，不是官方对该二进制的裁决）。
- 反过来说：这正是 `app/build.gradle.kts:41-43` 已验证过的现象——被裁掉的 `armeabi-v7a`/`x86` 里存在 `p_align=4096` 与"RELRO 与可写数据共页"的组合。所以 **16KB 检查器必须实现这两个前置豁免**，否则会把 `libandroidx.graphics.path.so` 误报成 UNALIGNED，把 J 域门变成假红。

### 3.4 AAB 与工具前置（证 M11 / M2）
```
app/build/outputs/bundle/localFirstRelease/app-localFirst-release.aab   113,439,352 B   mtime 2026-09-30 02:34
  base/lib/ 下 ABI = {arm64-v8a, armeabi-v7a, x86, x86_64}（21 个 .so，全部 method=8 deflate）
app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab  （mtime 2026-09-30）
debug APK  app-localFirst-debug.apk  ABI = {arm64-v8a, x86_64}          （abiFilters 生效）
```
结论三条：
1. 现存 AAB 是 9 月的旧包，**带 32 位 ABI**，与当前 `abiFilters`（`app/build.gradle.kts:45-47`）不一致 → 不能作为 16KB/ABI 证据；必须重出 AAB。
2. `bundletool` 不在 PATH，`C:/Android/Sdk/ndk/` **不存在**（无 `llvm-objdump`/`llvm-readelf`）→ 官方第 1、4 项检查在本机需要先装工具或改用等价实现（本轮用自写解析器替代，只覆盖 `p_align`/RELRO，不覆盖 `llvm-readelf` 的其它告警）。
3. AAB 内 `.so` 是压缩存放的，这不构成缺陷（Play 会按 `PAGE_ALIGNMENT_16K` 重打包），所以**不能**用"AAB 里 .so 没对齐"当判据——判据只有 `bundletool dump config`。

### 3.5 设备面（证 M5/M8 的装置现状）
```
adb devices → emulator-5562/5566/5570/5584（4 台）
emulator-5562 api=34 pagesize=4096 abi=x86_64
emulator-5566 api=34 pagesize=4096 abi=x86_64
emulator-5570 api=23 pagesize=(getconf 缺失) abi=x86_64
emulator-5584 api=34 pagesize=4096 abi=x86_64
```
- **无 16KB 装置、无 arm64 装置** → 16KB 运行时验证与 arm64 真机验证都只能 BLOCKED（与计划 §6 `:87` 的 BLOCKED 口径一致，但计划没说明"16KB 运行时"也归此项）。
- 三台 API34 同 ABI 同页大小：截图基线可共享"同 API 同密度"的一组，但**跨 API 不共享**（API 23 那台另立基线）。
- TalkBack 可用：`pm list packages` 命中 `com.google.android.marvin.talkback`、`com.google.android.tts`、`com.android.systemui.accessibility.accessibilitymenu` → 计划"设备 TalkBack 脚本化走查"有装置基础（但"语义是否可理解"仍是人工残项）。

## 4. 对计划取舍的判定

| 取舍 | 计划原文 | 判定 | 理由 |
|---|---|---|---|
| 取舍 1：不引新依赖，用设备截屏 + 自研像素差做视觉验收 | `:9`、`:88` | **成立，但必须改叙述** | 官方推荐的是 host 侧截图测试；设备截屏是自担风险路线，且本仓有黑帧失败史。改为"已知偏离 + 前置非黑帧断言 + 基线绑定单装置"后可成立。像素差工具零新依赖可行（PIL 已有先例）。 |
| 取舍 2：无 oracle 处用蜕变测试 | `:11` | **成立，边界要收紧** | 两篇论文都是真蜕变测试文献；但蜕变关系只保"输入变换下的必守关系"，**不能证明语义正确**，也无法覆盖审美/语感。需预注册关系 + 容差 + 失败模式，否则会变成"自定规则自证"。 |
| 取舍 3：自动检查 + 脚本化 TalkBack 走查替代人工无障碍验收 | `:10` | **部分成立** | 官方明确 manual 是 "essential way"；计划把语感列 UNVERIFIED 正确。但自动侧构件要换名（M6），且引入即新依赖，与 `:88` 冲突需单列。 |
| 取舍 4：1000 项检查替代人工深层验收 | §1/§5 | **不成立为"验收替代"，成立为"覆盖 + 台账"** | "每项落到四处之一"允许手写 md 自证（M10）。改为机器产物 + ≥2 类独立证据 + 逐项 verdict 后，才是"可复核的覆盖"，而不是"替代人工结论"。 |
| 取舍 5：1200 项计数 | §5 `:80` | **不可由本文裁定** | 计数是计划自身设计；本文只指出计数**不等于**验收强度，需与 §7 `:93`("无空项")一起看，且 §2 十域里 F/部分 J/I 项先天只能 BLOCKED/UNVERIFIED，分母应显式扣减。 |

## 5. 补充来源（本文新引入，供计划回引）

| 主题 | 来源 | 用途 |
|---|---|---|
| 16KB 全部检查与时间表 | `https://developer.android.com/guide/practices/page-sizes`（Last updated 2026-10-16） | 替代 §0 `:8` 的模糊表述；给出 2027-02-01 门槛、p_align / RELRO / zipalign / bundletool 四件套、AGP≥8.5.1、NDK r28 |
| 截图测试推荐口径 | `https://developer.android.com/training/testing/ui-tests/screenshot`（2026-10-08） | 纠正 M4：官方只点名官方 experimental 工具 + 第三方泛称；并给出"平台/OS/架构差异会导致像素差"的官方措辞（支撑 M5） |
| Compose 无障碍自动检查 | `https://developer.android.com/develop/ui/compose/accessibility/testing`（2026-10-22） | 纠正 M6：`ui-test-junit4-accessibility` + Compose 1.8.0+；确认 manual 是 "essential way"（支撑 UNVERIFIED 残项） |
| 蜕变测试 | `https://arxiv.org/abs/2310.00338`、`https://arxiv.org/abs/2412.20692` | 确认 §0 `:11` 两条引用真实且同主题 |
| 时序 flaky | `https://flakeecho.github.io`（ISSTA 2024 投稿页） | 确认方法学；同时给出"工具需 EdXposed/API30/AGP8.0"的不可用证据（M7） |
| 16KB 第三方库现状 | `https://api.github.com/repos/cashapp/paparazzi`、`.../takahirom/roborazzi` | 证明二者是第三方 Apache-2.0（M4），并给出版本活跃度 |
| AIGC 标准 | `https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=F32EA2A561F1886CD8D606513512D547`；`https://std.samr.gov.cn/gb/search/gbDetailed?id=301E0388CB75788DE06397BE0A0AE1B4` | GB 45438-2025：发布 2025-02-28 / 实施 2025-09-01 / 现行 / 归口中央网信办（两个独立的国标平台页） |
| Play 目标 API | `https://support.google.com/googleplay/android-developer/answer/11926878`；`https://developer.android.com/google/play/requirements/target-sdk` | 2026-08-31 起新应用与更新须 target API 36（本仓 targetSdk=36，`app/build.gradle.kts:29`，当前合规） |
| 封闭测试 | `https://support.google.com/googleplay/android-developer/answer/14151465` | 12 测试者/14 天**只约束 2023-11-13 之后创建的个人账号** → 计划 `:8` 不能把它当无条件检查 |

## 6. 未验证 / 不可达（不得计 PASS）

1. **《标识办法》与 GB 45438-2025 条款原文**：国标平台仅提供浏览器全文预览，本轮 HTTP 抓取只能拿到元数据与目录级提示。→ H 域"文字提示要素""显式标识位置/尺寸"类判据保持 UNVERIFIED，直到取得正文（仓内 `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/AiGeneratedImageCaption.kt:14` 已自标同一缺口）。
2. **Android 官方稳定性页正文**：只确认 `/training/testing/instrumented-tests/stability` 存在于官方导航（页面标题 "Big test stability"），**正文未抓取**，故"官方稳定性指南支持 witness/隔离窗"一说未获原文支撑。
3. **Play 2025-05 16KB 博客**：未抓取原文（计划 `:8` 的第二个引注）。
4. **Compose 解析版本**：Gradle 未运行；缓存中 `androidx.compose.ui/ui-android` 为 1.11.3（≥1.8.0，`enableAccessibilityChecks` 的 API 前提成立），但"BOM 2026.06.00 → 1.11.3"的映射未经 Gradle 依赖解析输出确认。
5. **16KB 运行时**：无 16KB 装置（§3.5），也无 `check_elf_alignment.sh`/`llvm-readelf` 交叉验证（NDK 未装）；RELRO 的两例豁免是**文档推断**，需真机/官方工具复判。
6. **arm64 真机、生产签名、真实 Provider、人工金标**：与批次 43 一致，仍 BLOCKED（`docs/research/2026-11-05-batch-43-record.md:523`（生产签名 4/4 UNSET、arm64 未验证）、`:577`（发版不成立））。
7. **审美/语感类**：无可编码判据，UNVERIFIED。

## 7. 对 §1–§4 的落地改写（每条都指到具体落点）

1. §0 `:8` 改写为：`Google Play 2027-02-01 起对 target≥35 的 64 位更新强制 16KB；本地检查五件套 = LOAD p_align / RELRO 余数（带两条豁免）/ zipalign -c -P 16 / bundletool PAGE_ALIGNMENT_16K / 代码面 PAGE_SIZE 假设`；把"2026 检查面"里 API 级别改为"2026-08-31 起 target 36（本仓已满足）"，把"封闭测试"加账号类型条件。
2. §2 J 域新增三项：`AAB 重出 + bundletool config`（现 AAB 为 2026-09-30 旧包，含 4 ABI）、`16KB 运行时（BLOCKED + runbook：16KB 镜像/真机）`、`工具前置清单（NDK/llvm-objdump、bundletool 缺失）`。
3. §1 D0 项 8 明确"新写 `tools/ci/screenshot_baseline.py`，复用 PIL"，并把"非黑帧前置断言 + 基线绑定 serial/API/密度/字体缩放"写成验收条件（对应历史上 `docs/research/2026-10-14-4c-stage1-record.md:64` 的黑帧）。
4. §2 I 域：构件名改 `ui-test-junit4-accessibility`（Compose ≥1.8.0）；因 `:88` 不引新依赖，二选一并写明覆盖差（自动框架子集 vs 自写语义树规则）。
5. §2 H 域：按"格式 × 显式/隐式"矩阵重排（PDF 双轨、DOCX 仅显式、自然语言改卷文本 KD-85 缺），并把条款要素标 UNVERIFIED。
6. §2 F 域：每项标注是否依赖低负载窗；依赖者继承批次 43 的 INVALID（`:562`–`:565`），不得计 PASS。
7. §5 `:81` 改为"每项必须落到机器产物（测试 XML / 命令日志 / 设备原始输出 / 工具 JSON）；md 只作索引"。
8. §0 `:11`/`:12` 的引注保留，但 `:12` 增补"FlakeEcho 工具不可用（EdXposed/API30/AGP8.0），仅引方法学"。
9. §1 D0 项 3/4 增补 skip 语义（M12）：`skipped → BLOCKED`，且把 `core/data/.../NativeLibraryPageSizeInstrumentedTest.kt` 在 4KB 装置上的 XML 作为"skip 冒充绿"的负例夹具。

## 8. 本文自证边界

- 本文所有"实测"均为本轮命令输出；所有"官方原文"均为本轮抓取（页面自述更新时间随附）；所有"仓内事实"均给 `path:line`。
- 本文**没有**运行任何 Gradle 任务、任何 instrumented test、任何签名/发版动作；因此本文不对门禁是否通过、Apk 是否可发版作任何结论。
- 本文对 RELRO 两例"合规"的判断是**基于官方文档前置条件的推理**，不是工具裁决；已在 §6.5 标为需复判项。
