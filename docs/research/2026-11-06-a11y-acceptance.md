<!-- 体量自查：519 行 / 46,381 B（`wc -lc docs/research/2026-11-06-a11y-acceptance.md` 实测，≤65,536 B）。 -->

# 批次 44 · I 域（可用性与无障碍）验收研究：可自动判定面、不可判定面与 90 项检查构成

> 落盘：`docs/research/2026-11-06-a11y-acceptance.md`。本文是**研究件**，不是验收裁定：它给判据、给方法、给构成，**不签任何项的 PASS**。
>
> 依据的输入：计划 `.zcode/plans/plan-2026-11-05-batch-44-acceptance.md`（§1 验收机制 `:15-34`、§2 I 域 `:64-65`、§6 硬约束 `:84-89`、§7 `:91-95`）；批次 43 收口 `docs/research/2026-11-05-batch-43-record.md` §9（`:538-578`）；交叉复核 `docs/research/evidence/2026-11-06-batch-44/research-crosscheck.md`（M6/M10/M12、`:186` 装备状态）。
>
> **本轮亲手实跑（逐字见 §4）**：`adb devices`、4 台模拟器的 API/密度/`font_scale`/`night`/TalkBack 包、`uiautomator dump` ×3（竖屏/200% 字号/横屏）、`exec-out screencap` ×4、像素与语义树规则脚本 `build/a11y-probe/probe.py`（我写的临时探针，**不是交付物**）、Google Maven `maven-metadata.xml`/POM 的 HTTP 取件、Gradle 缓存枚举。
>
> **本轮未做**：任何 Gradle 任务、任何 `am instrument`、任何 TalkBack 启用、任何 git 写。**90 项逐项执行未做**——因此本文全部 90 项的当前状态是「未执行」，不是 PASS（§5.1）。

---

## 0. 结论摘要

1. **自动面确实存在，但只有一半。** 90 项按可判定性分成 4 类：**A 类 45 项**（零新依赖的双机器证据可判）、**B 类 35 项**（机器可检测，但缺第二证据类或需策略裁定）、**C 类 8 项**（需独占装置的交互式走查 → BLOCKED）、**D 类 2 项**（人工语感 → 永久 UNVERIFIED）。**可机器判定率 45/90 = 50.0%**，其余 45 项不得计 PASS。
2. **计划的构件名写错了。** 官方给的是 `ui-test-junit4-accessibility` + `enableAccessibilityChecks()`（Compose 1.8.0+），**不是** `espresso-accessibility`（那是 View/Espresso 路径）。见 §1.1 逐字引文与 §7.1。
3. **自动检查不能替代 TalkBack 人工，这一点有官方一手原文**：Compose 页写 "An essential way of testing accessibility is a form of manual testing"；ATF README 写 "Other rules require human judgment, such as whether or not a contentDescription makes sense to all users"。所以"脚本化 TalkBack 走查"只能替代**可达、可激活、顺序**三件事，不能替代**读得对不对**（§1.2）。
4. **本项目已具备脚本化 TalkBack 装置**：`app/src/androidTest/.../TalkBackRuntimeInstrumentedTest.kt`（opt-in `-e talkback_audit true`，内核触摸 + `AccessibilityEvent` 等待 + traversal JSON），2026-10-25 曾 1/1 通过一条路径（`docs/research/2026-10-25-ui-completion-audit.md:38`）。它证的是"一条路径可走通"，**不是**"全页可听"。
5. **派生出的三条硬事实（本轮实测，改变了检查设计）**：
   - **语义树导出零依赖可行**：`uiautomator dump` 直接给出 Compose 语义（`resource-id` 来自 `testTagsAsResourceId`、`content-desc`、`bounds`、`clickable/focusable/selected/checked/enabled/scrollable`），本机 5584 首屏 36 节点 / 5 可点击（§4.1）。
   - **不能按"每节点"判标签**：Compose 把 label 与 clickable 分散在**bounds 完全相同**的多个节点上（5584 首屏 8 组同 bounds 簇，例 `[42,612][1038,754]` 上同时有 clickable View、带 `content-desc=录入第一道题` 的 View、`Button`）。朴素规则会把**全部按钮**误报为"无标签"（§4.2、§3.3-R1）。
   - **200% 字号不是等比**：`font_scale=2.0` 下首屏问候文本高 75 px → 129 px = **1.72×**，官方页也明写字号缩放是 "non-linear scale"。任何"断言正好 2 倍"的检查都会假红（§4.3、§1.3）。
6. **两条候选缺陷（仅自动面发现，未定级）**：
   - 横屏下 `review_start_button` 高 **40.0 dp**（竖屏 57.9 dp），**低于 48dp 门**；该控件 `enabled=false`。
   - 同一控件的禁用态文字对比度 **2.53:1**（`(135,145,155)` on `(221,231,224)`），低于 4.5:1。
   二者都落在**禁用控件**上：WCAG 1.4.3 明确豁免"inactive UI component"，而项目门 `docs/current/release-gates.md:56` 没有写豁免。**这是策略缺口，不是代码缺陷的结论**（§3.3-R2、§4.5）。

---

## 1. 官方事实（逐条给 URL、逐字引文、采纳/拒绝）

### 1.1 Compose 自动无障碍检查：构件、版本、开关、边界

来源：`https://developer.android.com/develop/ui/compose/accessibility/testing`（页面自述 `Last updated 2026-10-22 UTC`，本轮抓取正文）。逐字：

- "An essential way of testing accessibility is a form of manual testing: by turning accessibility services, like TalkBack or Switch Access, on, and checking if everything works as expected."
- "In conjunction with manual verification, you should also use automated testing to flag any potential issues that could impact user experience as you make continual changes to your app."
- "For automated accessibility testing, you can also use the Accessibility Test Framework—the same underlying framework that powers Accessibility Scanner and accessibility checks in Espresso—to perform some accessibility-related checks automatically, **starting with Compose 1.8.0**."
- "To enable the checks, add the **`ui-test-junit4-accessibility`** dependency, call **`enableAccessibilityChecks()`** in the `AndroidComposeTestRule`, and trigger an action or `tryPerformAccessibilityChecks`."
- "Other available checks look for accessibility issues with **color contrast, small touch target size, or elements' traversal order**."
- 示例里还有 `AccessibilityValidator().setThrowExceptionFor(AccessibilityCheckResult.AccessibilityCheckResultType.WARNING)`——即**默认只对 ERROR 抛**，WARNING 需显式升级，否则门会把警告当绿。

**采纳**：构件名、开关、版本门槛、"manual 是 essential"。**拒绝**：计划 §0 `:10` 的 "`enableAccessibilityChecks`（espresso-accessibility）"这一括注（见 §7.1）。

### 1.2 ATF 的覆盖清单与"必须人工"的一手原文

来源：`https://github.com/google/Accessibility-Test-Framework-for-Android`（Apache-2.0，524★，master）。README 逐字：

> "Some good practices can be checked by automated tools, such as if a View has a contentDescription. **Other rules require human judgment, such as whether or not a contentDescription makes sense to all users.**"

`src/main/java/.../framework/checks/` 目录本轮实取，**14 个检查类**：`ClassNameCheck`、`ClickableSpanCheck`、`DuplicateClickableBoundsCheck`、`DuplicateSpeakableTextCheck`、`EditableContentDescCheck`、`ImageContrastCheck`、`LinkPurposeUnclearCheck`、`RedundantDescriptionCheck`、`SpeakableTextPresentCheck`、`TextContrastCheck`、`TextSizeCheck`、`TouchTargetSizeCheck`、`TraversalOrderCheck`、`UnexposedTextCheck`。

**采纳**：作为"自动覆盖 vs 人工残项"的分界证据；作为 D 类（不可判定）的唯一权威依据。**注意**：ATF 本体是 **View/AccessibilityNodeInfo** 层，Compose 侧由 `ui-test-junit4-accessibility` 桥接——它检查的是**语义树投影**，不是 Compose 内部状态。

### 1.3 字号缩放：非线性，且官方要求"不破、不重叠、不隐藏"

来源：`https://developer.android.com/develop/ui/compose/accessibility/scalable-content`（`Last updated 2026-10-22 UTC`）。逐字：

- "**Note:** This approach leverages Android's underlying font scaling system, which applies a non-linear scale for better readability. Therefore, a zoomChange of 2.0 might not result in text that appears exactly twice as large..."
- "Build for all scales: Test your UI against both in-app scaling and system-wide font or display settings. **Check that your app's layouts adapt correctly without breaking, overlapping, or hiding content.**"

**采纳**："非线性"直接改变 200% 项的断言写法（§5.2 I4-06 的 1.4–2.2 区间而不是 ==2.0）；"不破/不重叠/不隐藏"把 200% 项从审美判断降为几何判断。

### 1.4 对比度阈值与两条容易踩空的细则

来源：`https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html`（`Updated 06 September 2026`）。逐字：

- "The visual presentation of text and images of text has a contrast ratio of at least **4.5:1**"，"Large Text ... at least **3:1**"；large = "at least 18 point or 14 point bold"。
- "**Incidental** Text or images of text that are part of an **inactive user interface component** ... have no contrast requirement."
- "Inactive User Interface Components: User Interface Components that are not available for user interaction (e.g., **a disabled control**) are not required to meet contrast requirements."
- "the computed values **should not be rounded** (e.g., 4.499:1 would not meet the 4.5:1 threshold)."
- 相对亮度公式：`L = 0.2126R + 0.7152G + 0.0722B`，分量 `<=0.04045` 走 `/12.92`，否则 `((c+0.055)/1.055)^2.4`；`contrast = (L1+0.05)/(L2+0.05)`。

**采纳**：公式、阈值、不四舍五入。**必须并读项目门**：`docs/current/release-gates.md:56` 写 "Interactive touch regions must be at least 48dp and not overlap; normal text must meet 4.5:1 contrast on its actual background. No shrinking text to hide overflow"——**没有**禁用豁免。二者冲突处置见 §7.3。

### 1.5 48dp

来源：`https://developer.android.com/guide/topics/ui/accessibility/views/apps-views`（`Last updated 2026-10-23`）。本轮只取到官方页的检索摘要（全文抓取被网络阻断，见 §4.7）："We recommend that each interactive UI element have a focusable area, or touch target size, of at least **48dp×48dp**."
**采纳为口径**（另有项目门 `release-gates.md:56` 与 `release-gates.md:22` 两条独立同向表述：`"core TalkBack, large font, and 48dp checks pass"`）。**保留**：摘要非全文，按"单来源+摘要"记，不用于任何"达标"结论。

### 1.6 采纳/拒绝总表

| 来源 | 用于 | 采纳/拒绝 | 理由 |
|---|---|---|---|
| `developer.android.com/.../compose/accessibility/testing`（2026-10-22） | 构件名/开关/manual 边界 | 采纳 | 一手原文；与交叉复核 M6 一致 |
| `github.com/google/Accessibility-Test-Framework-for-Android` | 14 检查类 + human judgment | 采纳 | 一手 README + 目录实取 |
| `developer.android.com/.../accessibility/scalable-content`（2026-10-22） | 非线性缩放、不破不重叠 | 采纳 | 一手原文 |
| `w3.org/WAI/WCAG22/Understanding/contrast-minimum.html`（2026-09-06） | 阈值/公式/豁免/不四舍五入 | 采纳 | 一手原文 |
| `developer.android.com/.../views/apps-views`（2026-10-23） | 48dp | **部分采纳** | 只取到摘要，未取全文 |
| `developer.android.com/reference/.../junit4/accessibility/package-summary` | API 稳定性注解 | **UNVERIFIED** | 未取正文；`@ExperimentalTestApi` 与否未证 |
| `dl.google.com/dl/android/maven2/.../ui-test-junit4-accessibility/` | 依赖可得性 | 采纳（本轮 curl 实测） | `maven-metadata.xml` 含 1.11.3；POM HTTP 200 |
| 计划 §0 `:10` 的 "espresso-accessibility" | 构件选择 | **拒绝** | Compose 场景构件不同（§7.1） |
| "脚本化 TalkBack 可替代人工验收" | 验收替代 | **部分拒绝** | 只能替代可达/激活/顺序（§1.1/§1.2） |
| FlakeEcho 工具 | flaky | **拒绝（沿用批次 44 交叉复核 M7）** | 需 EdXposed/API30/AGP8.0，本仓 AGP 9.3.0 |

---

## 2. 仓内事实（本轮实读/实跑）

### 2.1 依赖面：没有 accessibility 构件

- `app/build.gradle.kts:200-206` 的 androidTest 依赖只有 `compose.ui.test.junit4`、`test.ext.junit`、`espresso.core`、`espresso.intents`。**无** `ui-test-junit4-accessibility`、**无** ATF。
- 本地 Gradle 缓存实枚举：`~/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/` = `ui / ui-android / ui-geometry-android / ui-graphics-android / ui-test-manifest / ui-text-android / ui-tooling* / ui-unit-android / ui-util-android`；**无任何 `*accessibility*` 组**；`ui-android` 只有 `1.11.3`。
- 依赖可得性实跑（本轮）：`curl https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-junit4-accessibility/maven-metadata.xml` → 含 `1.11.3`、`lastUpdated 20261021…`；`.../1.11.3/ui-test-junit4-accessibility-1.11.3.pom` → **HTTP 200**；POM 依赖含 `ui-test-accessibility:1.11.3`、`ui-test-junit4:1.11.3`。
- 结论：**引入是"技术上可行 + 纪律上未授权"**（计划 §6 `:88` 不引新依赖；必须单独提交并过供应链复核）。本批零新依赖，因此 §5 的 A 类全部按"语义树 + 像素"路线设计，不依赖该构件。

### 2.2 语义载体（导出能力已有）

| 事实 | 证据 |
|---|---|
| `testTagsAsResourceId = true` 已挂根节点 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:393` |
| 唯一 testTag 521 个 | `grep -rho 'testTag("[^"]*"' --include=*.kt app/src/main core/ui/src/main feature/*/src/main \| sort -u \| wc -l` → `521` |
| `semantics/contentDescription` 出现数 | app 11 / core/ui 104 / feature:capture 22 / library 56 / profile 10 / review 19 / tutor 33（逐目录 `grep -rn ... \| wc -l`） |
| 测试里已用语义树字符串 | `RootExperienceInstrumentedTest.kt:346`、`RootTutorFailClosedInstrumentedTest.kt:359`（`onRoot().printToString(maxDepth = 8)`） |
| 语义属性写法已存在 | `Components.kt:101-108`（`contentDescription`）、`TutorToolTraceLine.kt:76-92`（`stateDescription`）、`ClaudeSplitPane.kt:75-76`（`setProgress`） |

### 2.3 已有 TalkBack 装置（决定 C 类能否降级）

`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/TalkBackRuntimeInstrumentedTest.kt`：
- `:25` `assumeTrue(arguments.getString("talkback_audit") == "true")` → **默认 skip**（这正是交叉复核 M12 的"skip 冒充绿"典型，必须映射 BLOCKED）；
- `:35-37` 断言 `dumpsys accessibility` 含 `TalkBackService` 且 `touchExplorationEnabled=true`；
- `:46-58` 与**宿主侧硬件输入驱动**做文件握手（`hardware-input-request.json` / `-response.txt`）；
- `:66-88` 逐次 `executeAndWaitForEvent(TYPE_VIEW_ACCESSIBILITY_FOCUSED)` 并落 `talkback-traversal.json`（text/description/resourceId/selected/state）；
- `:110` 断言双击后真的切换到目标页。
- 历史结论：`docs/research/2026-10-25-ui-completion-audit.md:38` 记 1/1 通过一条路径，并自限 "不把本例扩成全页面人耳朗读验收"。

### 2.4 主题与方向（影响 I5/I9 的构成）

- **没有暗色方案**：`core/ui/.../Theme.kt` 只 import `lightColorScheme`（`:7`、`:155`）；全仓主源码 `grep -rn "darkColorScheme\|isSystemInDarkTheme\|DayNight\|MODE_NIGHT"` → **0 命中**。
- **未固定方向**：`app/src/main/AndroidManifest.xml` 的 `MainActivity` 无 `screenOrientation`、无 `configChanges` → 旋转走默认重建路径（可测）。

### 2.5 设备面（本轮 `adb` 实测）

| serial | API | 密度 | app 已装 | `font_scale` | `night` | TalkBack 包 |
|---|---|---|---|---|---|---|
| emulator-5584 | 34 | 420 | 是 | `1.0` | no | 有 |
| emulator-5566 | 34 | 420 | 是（**APK 与设备库 schema 不匹配，见 §4.8**） | `1.0` | no | 有 |
| emulator-5570 | 23 | — | 否 | — | — | — |
| emulator-5562 | 34 | — | —（`offline`） | — | — | — |

`pm list packages` 命中 `com.google.android.marvin.talkback`、`com.google.android.marvin.talkbackoverlay`、`com.google.android.tts`。

---

## 3. 可判定面：证据类、判定类与四个规则陷阱

### 3.1 证据类（PASS 需 ≥2 个**互不派生**的类）

| 类 | 内容 | 为什么它与别的不独立 |
|---|---|---|
| **E1** | 仪器化测试 XML（`TEST-*.xml`，含 skipped 计数） | 与 E2 独立：走 Compose 测试规则/断言 |
| **E2** | 语义树导出（`uiautomator dump` XML 或 `printToString()`） | 与 E1 独立：走系统 AccessibilityNodeInfo 序列化 |
| **E3** | 像素采样（`exec-out screencap` + 规则） | 与 E1/E2 独立：只看渲染结果 |
| **E4** | 系统运行输出（`logcat -d`、`dumpsys accessibility`、`dumpsys activity`） | 与 E2 独立：进程/系统层 |
| **E5** | 静态源扫描（`grep`/lint） | **不得单独支撑 PASS**（交叉复核 M10） |
| **E6** | TalkBack 走查产物（traversal JSON + 截图 + `dumpsys accessibility`） | 与 E2 独立：真实服务+内核触摸 |
| **E7** | 人工判定 | 不计入机器证据 |

### 3.2 判定类

| 类 | 含义 | 本批可否得 PASS |
|---|---|---|
| **A** | 零新依赖、可拿 ≥2 个机器证据类直接判 PASS/FAIL | 可（45 项） |
| **B** | 机器**能检测**，但缺第二证据类或需先裁定策略/阈值 | 裁定或补装置前不可（35 项） |
| **C** | 需独占装置窗口（TalkBack/IME/真机） | BLOCKED（8 项） |
| **D** | 只有人能判（语义是否说得对、语感、审美） | 永久 UNVERIFIED（2 项） |

### 3.3 四个必须写进工具的规则陷阱（本轮均有实测支撑）

- **R1：标签判定必须按 bounds 合并。** 5584 首屏 8 组同 bounds 簇；`[42,612][1038,754]` 上三个节点分别是"clickable View"、"带 `content-desc=录入第一道题` 的 View"、"Button"。按节点判 → 5 个可点击控件里 2 个被判"无标签"（假红）。规则：先按 `(x1,y1,x2,y2)` 聚簇，簇内任一节点有非空 `text`/`content-desc` 即视为有标签（§4.2）。
- **R2：禁用态必须显式分类。** `review_start_button` 竖屏 57.9dp、横屏 **40.0dp**，且 `enabled=false`；其文字 2.53:1。WCAG 豁免非活跃组件，项目门没写。规则：输出里必须带 `enabled` 维度的计数（`fail(enabled=true)` / `note(enabled=false)`），由裁定决定后者算 FAIL 还是 EXEMPT；**不得静默放过，也不得直接判红**。
- **R3：字号缩放按区间判，不按倍数判。** 2.0 的 `font_scale` 实测 1.71–1.72×；官方明写 non-linear。规则：`1.2 <= ratio <= 2.2` 且"节点集合不减少、无重叠、无溢出"。
- **R4：skip 不是通过。** `TalkBackRuntimeInstrumentedTest` 与 `NativeLibraryPageSizeInstrumentedTest:53` 都用 `assumeTrue`；JUnit XML 的 `skipped=N` 必须映射 `BLOCKED` 且**不进"已验"分母**（交叉复核 M12；计划 §1 D0 项 3/4 的 skip 语义）。

---

## 4. 本轮实跑探针（可行性证据，不是 90 项的裁决）

> 全部在 `emulator-5584`（API 34 / 1080×2400 / density 420 / scale 2.625）上执行；一次性设置改动均已恢复（§4.9）。探针脚本 `build/a11y-probe/probe.py` 属 `build/`（gitignored），**不是交付物**；正式工具是 D0 项 9 的 `tools/ci/a11y_audit.py`（§8.1）。

### 4.1 语义树导出（E2 可用）

```text
adb -s emulator-5584 shell am start -n com.tingyun.smartmistakebook.localfirst/com.tingyun.smartmistakebook.MainActivity
adb -s emulator-5584 shell uiautomator dump /sdcard/window_dump.xml      → UI hierchary dumped
adb -s emulator-5584 pull /sdcard/window_dump.xml build/a11y-probe/window_dump_5584.xml  → 12,882 B
```
解析结果：**36 节点 / 5 clickable / 11 有 text / 2 有 content-desc**；属性集 = `bounds, checkable, checked, class, clickable, content-desc, enabled, focusable, focused, index, long-clickable, package, password, resource-id, scrollable, selected, text`。
可定位的 `resource-id`（即 testTag）：`root_review`、`review_state_title`、`review_state_supporting`、`review_capture_button`、`review_start_button`、`nav_review`、`nav_tutor`、`nav_library`、`nav_profile`。
**注意**：dump **不含** `importantForAccessibility`、字号（sp）、前景/背景色、`liveRegion`、`progressBarRangeInfo`——这四类信息决定了若干项只能归 B（§5）。

### 4.2 48dp / 重叠 / 顺序（E2 规则可判）

```text
python -I build/a11y-probe/probe.py build/a11y-probe/window_dump_5584.xml build/a11y-probe/shot-100.png 420 "fontScale=1.0"
== fontScale=1.0 density=420 scale=2.625 nodes=36 clickable=5
touch targets <48dp: none
clickable overlaps: none
lowest contrast ratios: 2.53 / 5.94 / 7.23
```
同 bounds 簇（8 组）与标签合并见 §3.3-R1；合并后**重复可播报标签 = 0**（`录入第一道题`、`暂无待复习题` 各只出现在一组簇内）。
顺序规则：5 个 clickable 的文档序 = 几何序 `review_capture_button → review_start_button → nav_tutor → nav_library → nav_profile`（`doc == geo` 为 True）。

### 4.3 200% 字号（E2+E3 可判，且证明"非线性"）

```text
adb -s emulator-5584 shell settings put system font_scale 2.0
adb -s emulator-5584 shell am force-stop … ; am start … ; sleep 9
adb -s emulator-5584 shell uiautomator dump /sdcard/fs200.xml ; pull …
adb -s emulator-5584 shell settings delete system font_scale     → 恢复 1.0
```
节点 bounds 对比（竖屏）：问候 `[42,84][354,159]` → `[42,84][576,213]`（高 75→129 px = **1.72×**，宽 312→534 = **1.71×**）；"今日复习"高 61→106 px = 1.74×。
200% 下：`touch targets <48dp: none`、`clickable overlaps: none`、节点数仍 36、无节点越出 1080×2400。

### 4.4 暗色/夜间（E3 可判：应用恒浅色）

```text
adb -s emulator-5584 shell cmd uimode night yes ; force-stop ; start ; screencap
adb -s emulator-5584 shell cmd uimode night no
```
像素对比（同分辨率、同页面）：`mean abs diff 0.14`、`max 158`、`differing>8 = 0.13%`（差异集中在状态栏时钟），左上角背景同为 `(247,246,242)`。
→ **应用不随系统夜间模式变化**；"暗色主题"在本仓**不存在**，I5 因此改写为"主题一致性/系统夜间不破坏浅色渲染"，而不是"暗色对比度达标"（§7.4）。

### 4.5 横屏（E2 可判，暴露一条 40dp）

```text
adb -s emulator-5584 shell settings put system accelerometer_rotation 0 ; settings put system user_rotation 1
dump → root [0,0][2400,1080]  ← 确认横屏
```
| 控件 | 竖屏 | 横屏 | enabled |
|---|---|---|---|
| `review_capture_button` | 379.4×54.1 dp | 568.0×**54.1** dp | true |
| `review_start_button` | 379.4×57.9 dp | 568.0×**40.0** dp | **false** |
| `nav_tutor/library/profile` | ≈99×64.0 dp | ≈224.6×**64.0** dp | true |
横屏下 `touch targets <48dp: [('review_start_button', 568.0, 40.0)]`、重叠 0。

### 4.6 对比度采样（E3 可判，方法本身有规则要求）

采样规则：节点框内**众数色为背景**，取占比 ≥0.3% 且亮度离背景最远的颜色为前景（我先试过"最暗 5% 为前景"，会把深色按钮上的白字误判成 1.00:1——这是必须写进工具的失败模式）。结果：

| 文本 | 背景 | 前景 | 比值 |
|---|---|---|---|
| `暂无待复习题`（禁用） | `(221,231,224)` | `(135,145,155)` | **2.53** |
| `录入第一道题`（主按钮） | `(35,112,74)` | `(255,253,252)` | 5.94 |
| 支持说明 | `(247,246,242)` | `(82,82,82)` | 7.23 |
| 问候 | `(247,246,242)` | `(21,21,21)` | 16.89 |

### 4.7 外部取件通道（说明取舍）

`curl https://developer.android.com/...` → `Failed to connect ... (28)`；`WebFetch` 同一主机 → `UND_ERR_CONNECT_TIMEOUT`。改用 `searchd.extract_content` 成功取到 §1.1/1.2/1.3 全文与 §1.4 全文；`views/apps-views` 只取到检索摘要（故 §1.5 记"部分"）。`repo1.maven.org`/`dl.google.com` 的 curl 可达（HTTP 404→目录不列表，具体 POM 200）。**"一次通道失败"不等于"来源不存在"**，本文按此处置。

### 4.8 一次被排除的误判（写进纪律）

在 `emulator-5566` 上设 `font_scale=2.0` 后 app 起不来。logcat 显示 **`IllegalStateException: Room cannot verify the data integrity ... Expected identity hash: 1d086050204a0da1ed6b6e3299dd9733, found: 4d2c8e0f54148a3a4690da34d4307e21`**（`BaseRoomConnectionManager.checkIdentity`）。归因 = **该机已装 APK 与设备库 schema 不匹配**，与字号无关（`1d086050…` 与批次 43 记录的库 hash 同值，`docs/research/2026-11-05-batch-43-record.md:42`）。**纪律**：设备异常必须先排除装置态再归因产品；本批 200% 结论因此只在 5584 上取。

### 4.9 设备恢复核对

```text
font=1.0   Night mode: no   user_rotation=0 accel=1   top=com.tingyun.smartmistakebook.localfirst/MainActivity
```

---

## 5. 本批 90 项检查的构成

### 5.1 计数（先给分母，再给项）

| 主题 | 项数 | A | B | C | D |
|---|---|---|---|---|---|
| I1 TalkBack 语义 | 10 | 5 | 3 | 1 | 1 |
| I2 触摸目标 48dp | 10 | 8 | 1 | 1 | 0 |
| I3 对比度 | 10 | 5 | 5 | 0 | 0 |
| I4 字号 200% | 10 | 6 | 3 | 1 | 0 |
| I5 主题/暗色一致性 | 10 | 4 | 6 | 0 | 0 |
| I6 焦点与遍历顺序 | 10 | 4 | 5 | 1 | 0 |
| I7 空态/错误可读 | 10 | 4 | 4 | 1 | 1 |
| I8 键盘与输入 | 10 | 2 | 6 | 2 | 0 |
| I9 横竖屏 | 10 | 7 | 2 | 1 | 0 |
| **合计** | **90** | **45** | **35** | **8** | **2** |

- **A 45 项（50.0%）**：零新依赖、两条机器证据即可在门禁阶段判 PASS/FAIL。
- **B 35 项**：拆成 **31 项"缺第二证据类/需交互装置"**、**2 项"需策略裁定"**（I2-09、I3-03）、**2 项"两者都要"**（I1-09、I7-06）。
- **C 8 项**：需独占装置窗口（TalkBack 6 条：I1-07/I2-10/I4-10/I6-10/I7-09/I9-10；IME/键盘 2 条：I8-09/I8-10）→ **BLOCKED**。
- **D 2 项**：语义是否"说得对"、空态文案是否"说得清楚"→ **永久 UNVERIFIED**。
- **本批不得计 PASS 的项 = 45**（35 B + 8 C + 2 D）。计划 §7 `:93` 的"1200 项无空项"因此必须允许 `BLOCKED/UNVERIFIED` 项存在，且分母按域扣减（交叉复核取舍 5）。

### 5.2 逐项表（每项一个可执行方法 + 证据类）

方法记号（全部可复跑；`$D`=设备 serial，`$A`=包名，`$S`=设置后的状态）：
- **M1** `adb -s $D shell uiautomator dump /sdcard/d.xml && adb -s $D pull /sdcard/d.xml $OUT` → 应用规则 R1–R7
- **M2** `adb -s $D exec-out screencap -p > $OUT.png` → 规则 C1（众数背景/最远前景，§4.6）或 C2（像素差）
- **M3** 仪器化断言：`composeRule.onRoot(useUnmergedTree = true/true).printToString()` + `assert*`（`RootExperienceInstrumentedTest.kt:346` 已有先例）
- **M4** `adb -s $D logcat -d` / `adb -s $D shell dumpsys …`
- **M5** 静态扫描（仅辅助）
- **M6** `am instrument -e talkback_audit true …` + 宿主硬件输入驱动 → `talkback-traversal.json`
- **M7** 人工判定

规则记号：**R1** bounds 合并标签；**R2** 48dp/重叠/屏内（按 density 换算 dp）；**R3** 遍历序 vs 几何序；**R4** 缩放比区间 1.2–2.2；**R5** 前景众数对比度；**R6** 节点集合/状态差分（旋转、字号、夜间）；**R7** 状态属性（selected/checked/enabled/stateDescription/password）存在性。

| # | 检查 | 方法 | 证据 | 类 |
|---|---|---|---|---|
| I1-01 | 每个可点击目标（bounds 合并后）有非空 text 或 content-desc | M1+R1 | E2+E1 | A |
| I1-02 | 同屏不存在"同 bounds 簇内 ≥2 个不同非空标签"的重复播报 | M1+R1 | E2+E1 | A |
| I1-03 | 无 `clickable=true` 且合并后仍无任何标签的节点 | M1+R1 | E2+E3（截图佐证该处确有图形） | A |
| I1-04 | 装饰性图形（分隔线/背景/纯装饰图标）不进入语义树 | M1+M5 | E2+E5 | B（需逐页装饰清单，否则无法区分"缺失"与"故意装饰"） |
| I1-05 | 可选中控件暴露 `selected`/`checked`/`checkable` | M1+R7 | E2+E1 | A |
| I1-06 | 语义合并粒度符合逐页期望（一张卡片读一条，而非 5 段） | M1（merged/unmerged 对比）+ M3 | E2+E1 | B（需先写逐页合并期望表） |
| I1-07 | TalkBack 实际朗读内容 == 树上的 text/desc（不吞字、不重复） | M6 | E6 | C（需独占装置窗口） |
| I1-08 | 空态/状态标题与说明是文本节点（非图片/非空 desc） | M1 | E2+E1 | A |
| I1-09 | 动态状态（进度/失败/重试）有 `stateDescription` 或 `liveRegion` | M1+M5 | E2+E5 | B（dump 不含 liveRegion；需裁定是否必须） |
| I1-10 | 朗读文案是否"说得对"、无歧义、无中英混读 | M7 | E7 | D |
| I2-01 | 竖屏首屏全部 clickable ≥48×48dp | M1+R2 | E2+E1 | A |
| I2-02 | 横屏同页全部 clickable ≥48×48dp | M1+R2 | E2+E1 | A |
| I2-03 | `font_scale=2.0` 下全部 clickable ≥48×48dp | M1+R2 | E2+E1 | A |
| I2-04 | 10 条用户旅程的落地页逐页 ≥48dp（逐页扫描） | M1+R2 ×10 | E2+E1 | A |
| I2-05 | 相邻 clickable 矩形不重叠 | M1+R2 | E2+E1 | A |
| I2-06 | clickable 完整在屏内，宽高 > 0 | M1+R2 | E2+E1 | A |
| I2-07 | 图标按钮（无文字、仅 desc）≥48dp | M1+R1+R2 | E2+E1 | A |
| I2-08 | 底部导航 ≥48dp 且标签文本不越出项容器 | M1+R2 | E2+E3 | A |
| I2-09 | **禁用控件是否受 48dp 约束**（实测横屏 40.0dp） | M1+R2（按 `enabled` 分组） | E2 | B（需裁定；WCAG 2.5.8 豁免非活跃组件，项目门未写） |
| I2-10 | TalkBack 双击实际命中区 == 语义 bounds | M6 | E6 | C |
| I3-01 | 正文文本 ≥4.5:1（浅色，逐页） | M2+C1 | E3+E1 | A |
| I3-02 | 大文本按 3:1 判定（需 pt 字号） | M2+C1 + M3（semantics 字号） | E3+E1 | B（dump 无字号；需从测试侧取 `TextUnit`） |
| I3-03 | **禁用文本是否受 4.5:1 约束**（实测 2.53:1） | M2+C1（按 `enabled` 分组） | E3 | B（需裁定，同 I2-09） |
| I3-04 | 非文本 UI（图标/边框/开关轨道）≥3:1（WCAG 1.4.11） | M2+C1（区域裁剪） | E3+E1 | B（前景/背景需逐控件定义） |
| I3-05 | 选中/未选中 chip、开关状态色差可辨 | M2+C1 + M1+R7 | E3+E2 | A |
| I3-06 | 主按钮文字对比（实测 5.94:1） | M2+C1 | E3+E1 | A |
| I3-07 | 错误/失败文案对比 | M2+C1 | E3+E1 | A |
| I3-08 | 空态说明文字对比 | M2+C1 | E3+E1 | A |
| I3-09 | 图片/图卡上的叠加文字对比 | M2+C1（多区域采样取最差） | E3+E1 | B（背景非纯色，需多区域与最差规则） |
| I3-10 | 系统"高对比度文本"开启后仍可读 | M1+M2（`settings put secure high_text_contrast_enabled 1`） | E3+E2 | B（需装置态切换与策略） |
| I4-01 | 200% 下无文本裁切（省略号/截断） | M2+C2 + M3 | E3+E1 | B（树里 text 是全文，裁切只能从像素/布局判） |
| I4-02 | 200% 下 clickable 不重叠 | M1+R2 | E2+E1 | A |
| I4-03 | 200% 下仍全部 ≥48dp | M1+R2 | E2+E1 | A |
| I4-04 | 200% 下无节点越出屏宽 | M1+R6 | E2+E3 | A |
| I4-05 | 200% 下底部导航标签不溢出容器 | M1+R2 | E2+E3 | A |
| I4-06 | 200% 缩放比在 1.2–2.2 区间（非线性，实测 1.72） | M1+R4 | E2+E1 | A |
| I4-07 | 200% 下关键动作（录入/开始/三键）仍可点击 | M1+R7 | E2+E1 | A |
| I4-08 | 200% 下长文（讲题正文/列表）可读且可滚动 | M1（`scrollable`）+ M3 | E2+E1 | B（需长内容夹具与"可读"口径） |
| I4-09 | 200% 下输入框不被 IME 遮挡 | M1+M2（焦点+IME 弹出） | E2+E3 | B（需交互装置） |
| I4-10 | 200% + TalkBack 组合 | M6 | E6 | C |
| I5-01 | 应用不声明暗色方案（源扫描 0 + 运行时颜色不变） | M5+M2+C2 | E5+E3 | A |
| I5-02 | 系统夜间开关下像素差 <1%（实测 0.13%） | M2+C2 | E3+E1 | A |
| I5-03 | 夜间下背景/前景色值不变（角像素抽样） | M2+C1 | E3+E2 | A |
| I5-04 | 夜间下状态栏图标仍可读 | M2（状态栏区域采样） | E3 | B（需系统栏策略） |
| I5-05 | 浅色渲染下全页对比度仍达标（复用 I3-01 样本） | M2+C1 | E3+E1 | A |
| I5-06 | 对话框/浮层 surface 在夜间不出现半暗混排 | M2+C2（打开对话框后） | E3+E2 | B（需交互） |
| I5-07 | Snackbar/Toast 在夜间对比不退化 | M2+C1（触发后） | E3+E2 | B（需交互） |
| I5-08 | PDF/图片查看器背景在夜间不退化 | M2+C2 | E3+E2 | B（需进入查看器） |
| I5-09 | 系统 force-dark 未命中 Compose 内容 | M2+C2 + M4（`dumpsys` 无 forceDark） | E3+E4 | B（需系统级证据） |
| I5-10 | 非主路径页（设置/关于/提醒）同样恒浅色 | M1+M2+C2（逐页） | E2+E3 | B（需逐页驱动） |
| I6-01 | 语义树遍历序 == 几何序（上→下、左→右） | M1+R3 | E2+E1 | A |
| I6-02 | 每个可交互节点 `focusable=true` | M1+R7 | E2+E1 | A |
| I6-03 | 无零尺寸/屏外可聚焦节点 | M1+R2 | E2+E1 | A |
| I6-04 | 底部导航顺序固定（review→tutor→library→profile） | M1+R3 | E2+E1 | A |
| I6-05 | 同一语义组不被重复聚焦（合并后无重复目标） | M1+R1+R3 | E2+E1 | B（需逐页组期望） |
| I6-06 | 对话框打开时焦点进入对话框内 | M1（开/关差分） | E2+E1 | B（需交互装置） |
| I6-07 | 对话框关闭后焦点回到触发点 | M1（开/关差分） | E2+E1 | B（需交互装置） |
| I6-08 | 硬件键盘 Tab 顺序与语义序一致 | M4（`input keyevent 61`）+ M1 | E2+E4 | B（需键盘装置） |
| I6-09 | 自定义 `traversalIndex`/`isTraversalGroup` 与视觉一致 | M5+M1 | E5+E2 | B（需逐页声明表） |
| I6-10 | TalkBack 实际聚焦序列 == dump 顺序 | M6 | E6 | C |
| I7-01 | 空态标题与说明是文本节点 | M1 | E2+E1 | A |
| I7-02 | 空态主行动存在且 `clickable=true` | M1+R7 | E2+E1 | A |
| I7-03 | 错误态文案非空且可读 | M1（错误夹具） | E2+E1 | A |
| I7-04 | 错误态有恢复行动且可点击 | M1+R7 | E2+E1 | A |
| I7-05 | 加载态暴露进度语义（`progressBarRangeInfo`） | M3（dump 无此属性） | E1+E2 | B（需测试侧断言） |
| I7-06 | 失败/不可用不伪装成功（文案与状态一致） | M1+M3 | E2+E1 | B（需裁定"伪装"判据 + 交互夹具） |
| I7-07 | 空态在 200% 下不裁切/不溢出 | M1+R2+M2 | E2+E3 | B（像素判裁切） |
| I7-08 | 空态 CTA 在横竖屏均 ≥48dp | M1+R2 | E2+E1 | B（需旋转装置，本轮仅一条样本） |
| I7-09 | 空态被 TalkBack 朗读一次（不重复、不吞） | M6 | E6 | C |
| I7-10 | 空态文案是否说得清楚、信息量是否够 | M7 | E7 | D |
| I8-01 | 硬件 Tab 可达全部控件且无跳过 | M4+M1 | E2+E4 | B（需键盘） |
| I8-02 | Enter/Space 激活按钮 | M4+M1 | E2+E4 | B（需键盘） |
| I8-03 | 输入框获焦时 IME 不遮挡目标 | M1+M2 | E2+E3 | B（需 IME） |
| I8-04 | IME 收起后布局恢复原状 | M1（收/放差分） | E2+E1 | B（需 IME） |
| I8-05 | 输入框有可读 label（desc 或可见 hint 文本） | M1+R1 | E2+E1 | A |
| I8-06 | 敏感字段（模型设置/密钥）`password=true` 且不被朗读 | M1+R7 | E2+E1 | A |
| I8-07 | 键盘导航无焦点陷阱 | M4+M1 | E2+E4 | B（需键盘） |
| I8-08 | 返回键先收 IME 再退页 | M4+M1 | E2+E4 | B（需 IME） |
| I8-09 | 真实中文 IME composing 提交 | M4+M1 | E2+E4 | C（历史仅一次 Gboard 证据，`2026-10-25-ui-completion-audit.md:32`） |
| I8-10 | 全键盘 + 200% 组合 | M4+M1 | E2+E4 | C（需专用装置窗口） |
| I9-01 | 旋转后语义节点集合稳定（同状态同页） | M1+R6 | E2+E1 | A |
| I9-02 | 旋转后无 clickable 重叠 | M1+R2 | E2+E1 | A |
| I9-03 | 旋转后全部 ≥48dp（本轮横屏实测 40.0dp） | M1+R2 | E2+E1 | A |
| I9-04 | 旋转后无文本水平溢出/越界 | M1+R6 | E2+E3 | A |
| I9-05 | 旋转后底部导航完整在屏内 | M1+R2 | E2+E1 | A |
| I9-06 | 旋转过程无崩溃/无 ANR | M4 | E4+E1 | A |
| I9-07 | 旋转后草稿/滚动位置保持 | M1（旋转前后差分） | E2+E1 | B（需逐旅程夹具） |
| I9-08 | 旋转后焦点与选中态不丢 | M1+R7 | E2+E1 | B（需交互装置） |
| I9-09 | 旋转 + 200% 组合仍满足 48dp/无重叠 | M1+R2 | E2+E1 | A |
| I9-10 | 旋转 + TalkBack 组合 | M6 | E6 | C |

---

## 6. 残余：不可判定与阻塞（逐 ID 给原因与解除条件）

### 6.1 永久 UNVERIFIED（2 项，D 类）

| ID | 为什么机器判不了 | 唯一依据 |
|---|---|---|
| I1-10 | "朗读文案说得对不对"是语义/语感判断；ATF README 原文即承认 "human judgment" | `https://github.com/google/Accessibility-Test-Framework-for-Android` README |
| I7-10 | 同上，且涉及"信息量是否足够"的产品判断 | 同上 |

**处置**：这两项**不得由本线签字通过**；只能由人工/产品侧裁定，或在稿件里显式保留 UNVERIFIED（计划 §6 `:89` 的边界条款）。

### 6.2 BLOCKED（8 项，C 类）

| ID | 阻塞点 | 解除条件（runbook 要点） |
|---|---|---|
| I1-07 / I2-10 / I6-10 / I7-09 / I9-10 / I4-10 | TalkBack 走查需：启用服务 + 内核触摸驱动进程 + 独占设备 | ①`settings put secure enabled_accessibility_services com.google.android.marvin.talkback/…TalkBackService` + `accessibility_enabled 1`；②宿主跑硬件输入驱动（读写 `hardware-input-request.json`）；③`am instrument -e talkback_audit true`；④结束后恢复设置；⑤`dumpsys accessibility` 留证。装置已存在（`TalkBackRuntimeInstrumentedTest.kt`），缺的是**排期与独占窗口**。 |
| I8-09 | 真实中文 IME（Gboard）需要交互与输入法状态 | 同装置窗口；历史证据仅 1 次（`2026-10-25-ui-completion-audit.md:32`），不构成本批 PASS |
| I8-10 | 全键盘 + 200% 需键盘装置 + 字号切换 + 独占 | 同上 |

> 注：TalkBack 项并非"原理上不可自动"，而是"本批无独占窗口"。若给出窗口，I1-07/I2-10/I6-10/I7-09/I9-10 可升为 A（E6+E3 两类证据）；I4-10/I8-10 仍受组合态限制。

### 6.3 待裁定 / 待补证据（35 项，B 类）

- **策略裁定 2 项**：I2-09（禁用控件 48dp）、I3-03（禁用文本 4.5:1）——建议一次性裁定"禁用态豁免"并写入 `docs/acceptance` 口径，否则 I2/I3/I9 会反复假红（本轮 40.0dp 与 2.53:1 都是禁用态）。
- **两者都要 2 项**：I1-09（liveRegion 是否必须 + dump 无该属性）、I7-06（"伪装成功"判据 + 交互夹具）。
- **缺第二证据类/需交互装置 31 项**：I1-04/06、I3-02/04/09/10、I4-01/08/09、I5-04/06/07/08/09/10、I6-05/06/07/08/09、I7-05/07/08、I8-01/02/03/04/07/08、I9-07/08。每一项的缺口已在 §5.2 的"方法"列写明（需字号来源、需交互、需逐页期望表、需装置态切换）。

### 6.4 本轮"未执行"声明（对 90 项整体）

90 项**没有一项**在本轮被执行。本轮只执行了 §4 的 6 类可行性探针（语义树、48dp/重叠/顺序、200%、夜间、横屏、对比度），它们证明**方法可跑**，不构成任何项的 PASS。按计划 §7 `:94` 的口径，这些项在验收台账里的当前值应为 `NOT RUN`。

---

## 7. 与计划/纪律的冲突与裁定建议

### 7.1 构件名：`espresso-accessibility` → `ui-test-junit4-accessibility`

计划 §0 `:10` 写 "`enableAccessibilityChecks`（espresso-accessibility）"。官方页明确 Compose 侧是 `ui-test-junit4-accessibility` + `enableAccessibilityChecks()`（Compose 1.8.0+），ATF 是底层框架（Espresso 也用它，但那是 View 路径）。**建议**：计划措辞改为"Compose 侧 `ui-test-junit4-accessibility`（底层 ATF）"。

### 7.2 新依赖：技术上可行，纪律上未授权

- 事实：`ui-test-junit4-accessibility:1.11.3` 在 Google Maven 可得（本轮 POM HTTP 200），与缓存中 `ui-android 1.11.3` 同版；缓存里**没有**该构件，门禁阶段需联网解析。
- 纪律：计划 §6 `:88` 不引新依赖，必须单独提交 + 供应链复核。
- **建议（本批采纳的路线）**：本批走**零依赖**路线（E2+E3 规则），把 `enableAccessibilityChecks` 登记为"下一批的独立提交项"。覆盖差要写清：零依赖规则能覆盖 ATF 14 类中的 `SpeakableTextPresent`、`DuplicateSpeakableText`、`TouchTargetSize`、`TraversalOrder`、`TextContrast`（像素法）、`DuplicateClickableBounds`；**不能**可靠覆盖 `TextSizeCheck`（无字号）、`ImageContrastCheck`（图内语义）、`ClickableSpanCheck`、`EditableContentDescCheck`、`LinkPurposeUnclearCheck`、`ClassNameCheck`、`UnexposedTextCheck`。
- 若授权引入：`enableAccessibilityChecks()` 默认只对 **ERROR** 抛，门禁必须 `setThrowExceptionFor(WARNING)` 或显式统计 WARNING，否则等于放行。

### 7.3 禁用态豁免：项目门与 WCAG 不一致（必须先裁定）

- WCAG：非活跃组件无对比度要求（§1.4 逐字）。
- 项目门 `docs/current/release-gates.md:56`："Interactive touch regions must be at least 48dp and not overlap; normal text must meet 4.5:1"——**无豁免**。
- 实测：横屏 `review_start_button` 40.0dp、禁用文字 2.53:1。
- **建议**：在 `docs/acceptance/dod.md`（D0 项 2）里写明三选一——(a) 严格按项目门，禁用态也 FAIL；(b) 采 WCAG 豁免，禁用态记 `EXEMPT` 但不计 PASS；(c) 区分"可交互的禁用态"（仍应 ≥48dp）。不裁定则该域永远出"红/绿"二义。

### 7.4 "暗色"在本仓不存在

计划 §2 I 列了"暗色"。实测：无 `darkColorScheme`、无 `isSystemInDarkTheme`、夜间模式下像素几乎不变（0.13%）。**建议**：I5 定位从"暗色对比度"改为"主题一致性与系统夜间不破坏渲染"，并把"是否提供暗色"登记为产品决策缺口（不计 PASS、不计 FAIL）。若裁定发布必须支持暗色，则 I5 全组转 FAIL（缺功能），而不是 UNVERIFIED。

### 7.5 skip 语义与本域的具体落点

本域的 skip 源至少两处：`TalkBackRuntimeInstrumentedTest:25`（未传参即 skip）、`NativeLibraryPageSizeInstrumentedTest:53`（4KB 装置 skip，跨 J 域）。`acceptance_report.py` 必须把 `skipped` 单列并映射 `BLOCKED`；`acceptance_lint.py` 增加规则"证据链里出现 skipped 结果的用例不得支撑 PASS"（与交叉复核 M12 一致）。

### 7.6 证据独立性：本域最容易被"自证"打穿的三处

1. **拿 `printToString()` 的字符串当"语义正确"的证明**——它只证明"树是这样"，不证明"这样读是对的"。
2. **拿截图当对比度证明**——截图是渲染结果，抗锯齿/子像素会改变采样值；本文 §4.6 已给出必须固定的采样规则（众数背景 + 最远前景 + 不取最暗像素）。
3. **拿"无崩溃"当"可访问"**——`logcat` 无异常只覆盖进程存活。

---

## 8. 落点与门禁契约

### 8.1 `tools/ci/a11y_audit.py`（D0 项 9）的规格建议

- 输入：`--dump <uiautomator xml>`（可多次）、`--shot <png>`、`--density <dpi>`、`--rules label,touch48,overlap,order,contrast,state`、`--expect <screen-spec.json>`。
- 必须先做**前置断言**：dump 的根 bounds 与 `wm size` 一致、截图为非纯色/非全黑（历史黑帧：`docs/research/2026-10-14-4c-stage1-record.md:64`）。前置失败 → `INVALID`，不是 FAIL。
- 输出：JSON（`rule, node_bounds, label, value, threshold, verdict, evidence_refs`）+ 退出码（`0` 全过 / `1` 有 FAIL / `2` INVALID 或输入缺失）。
- **必须**内置 §3.3 的 R1–R4 与本次 §4.6 的对比度采样规则；**必须**按 `enabled` 分组输出（R2）。
- 不做的事：不判语义正确性、不判审美、不读 `build/` 产物当输入。

### 8.2 与 `acceptance_report.py` / `acceptance_lint.py` 的对接

- 每条 verdict 的 `evidence[]` 至少 2 条，且 `class` 互异（E1..E4/E6）；`E5`（静态）单独出现 → `lint` 拒绝。
- `skipped>0` 的 XML 只能映射 `BLOCKED`。
- 每个 PASS 必须绑定 `run-id + APK sha + 设备 serial + 设置快照`（`font_scale`、`night`、`density`、`user_rotation`）——本轮已证明这些设置会显著改变结论（§4.3/4.4/4.5），不记设置等于结论不可复现。

### 8.3 执行 runbook（本域）

1. 设备独占声明 → `adb devices` 记 serial 与 `getprop ro.build.version.sdk`、`wm density`。
2. 设置快照：`settings get system font_scale`、`cmd uimode night`、`settings get system user_rotation`、`settings get system accelerometer_rotation`。
3. 每组态（默认 / 200% / 横屏 / 夜间）各跑：`dump` + `screencap` + 规则 → 落 `build/a11y-batch44/<state>/`。
4. TalkBack 组单独窗口：启用 → `am instrument -e talkback_audit true` → 取 `talkback-traversal.json` → 恢复设置。
5. 数据核对：`dump` 节点数与 `getprop`/`wm size` 一致；截图非纯色；把每项 verdict 写进 `docs/acceptance/verdicts/`。

---

## 9. 回链与自证边界

### 9.1 回链

- 计划：`.zcode/plans/plan-2026-11-05-batch-44-acceptance.md`（§1 D0 项 9 `:26`、§2 I 域 `:64-65`、§6 `:84-89`、§7 `:91-95`）。
- 上一批：`docs/research/2026-11-05-batch-43-record.md` §9（`:538-578`）。
- 交叉复核：`docs/research/evidence/2026-11-06-batch-44/research-crosscheck.md`（M6 `:66`、M10、M12、§3.5 装置）。
- 项目门：`docs/current/release-gates.md:22`、`:56`。
- 装置：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/TalkBackRuntimeInstrumentedTest.kt:25,35,46,66,110`。
- 历史证据：`docs/research/2026-10-25-ui-completion-audit.md:32,38`。
- 临时探针（非交付物）：`build/a11y-probe/{probe.py,window_dump_5584.xml,dump-5584-fs200.xml,dump-5584-landscape.xml,shot-100.png,shot-5584-dark.png,shot-5584-landscape.png}`。

### 9.2 本文的边界

- 本文**不裁定**任何一项的 PASS/FAIL；90 项全部 `NOT RUN`。
- 本文的"可判定"是**方法层**结论（规则可执行、证据可得、陷阱已知），不是"已达标"。
- 两条候选缺陷（40.0dp、2.53:1）只报事实与测量方法，不定级、不改代码（本线禁改 `core/ui`/`feature/*`/`app` 热区）。
- 48dp 的官方全文未取（仅摘要），已在 §1.5 标注；`enableAccessibilityChecks` 的 API 稳定性注解未取（§1.6 UNVERIFIED）。
- 设备结论只绑 `emulator-5584`（API 34 / x86_64 / 420dpi）+ 会话内已装 APK；**未绑 APK sha**（本轮未取 `pm path`/签名摘要），因此不得作为发版证据。

### 9.3 体量与自检（本轮实跑）

```text
wc -lc docs/research/2026-11-06-a11y-acceptance.md   →  514 行 / 46,040 B（≤65,536 B）
# §5.2 表逐行解析：90 行、9 组各 10、ID 无缺失无重复
# 分类计数：A 45 / B 35 / C 8 / D 2 = 90（与 §5.1 一致）
```
自检脚本只读解析本文件（`| I\d-\d\d ` 行首），未执行任何检查项。
