<!-- 体量自查：落盘后 `wc -lc` 见 §10。本文件是 docs/research 下的研究件（R3 视觉基线），不含可执行产物；工具落点见 §6，本轮不实现。 -->

# 无新依赖前提下的设备截图基线方案（批次 44 · R3 视觉基线）

| 字段 | 值 |
|---|---|
| evidence-id | `B44-R3-SCREENSHOT-01` |
| 执行者 | 视觉回归研究员（子代理），共享工作树，**只读仓内文件**；设备侧只做「截图 / 读属性 / 改后复原」 |
| 执行时间 | 2026-11-04 15:00–15:30（本机时间）；文件名按计划批次 44 的 `2026-11-06` |
| 设备 | `emulator-5584`（主）、`emulator-5566`（对照）；均 API 34 / x86_64 / 1080×2400 / 420dpi |
| 工具版本（本轮实跑） | adb 1.0.41（37.0.1-15733141）、Python 3.13.14、Git 2.55.0.windows.5 |
| 被测包 | `com.tingyun.smartmistakebook.localfirst`，versionName `0.2.0-localfirst`，versionCode 2，两机 `base.apk` sha256 均为 `9b832f9673179f4077a5d6559b1e84d5852df0ff82f08f2961002e5bd7e29e16` |
| 未执行 | 任何 Gradle 任务、任何 instrumented test、任何 APK 构建/安装/卸载、任何 git 写、任何 Provider 调用、任何 emulator 新起、任何 `adb root` |
| verdict | **方案成立且比预期更强**：单机 + 同一 APK + 已掩掉系统栏时，重复截图**逐像素为 0**（E3/E7），严格相等可比 Paparazzi 默认 0.01% 更严；但**朴素实现必假绿**——快照页（splash）本身稳定可复现（E10），朴素录基线会把 splash 当成首页且此后恒绿 |

> **口径**：**实跑**＝本轮命令与其真实输出；**实读**＝本轮读到的仓内源码/文档（给 `path:line`）；**转录**＝引用既有文件原文（标明出处）；**外部**＝本轮在线抓取或 GitHub API 返回值（给 URL）。
> 失败/空白一律显式写 **UNVERIFIED**；装置或前提不具备写 **BLOCKED**；**本文不给任何 PASS 判词**，只给可执行判据与实测噪声带。

---

## 0. 范围与不可越界项

本 ask 的交付物是**方案**（题：无新依赖前提下的设备截图基线方案）。因此本文给出：

1. 设备截图基线**能不能**替代人工视觉验收、能替代到什么颗粒度（§1、§6.5）；
2. 采集/断言/度量/阈值/矩阵/存储/**不可伪造**的完整契约（§6），可直接据此实现 `tools/ci/screenshot_baseline.py`；
3. 与 Paparazzi / Roborazzi / 官方 AGP test suites 的**条件化对比**：本批为何不引、什么条件一出现就必须引（§5）。

本文**不**实现 `tools/ci/screenshot_baseline.py`（计划 §1 D0 项 8），**不**改任何热区（`core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、Application/Root），**不**做 git 写。

6C-2 三项裁定（mask=8 不提供、mask=0 禁止、派生索引恒裁）在本方案里**只是图形见证对象**，不新增任何行为：备份/恢复相关截图一旦出现「8」或允许「0」的控件，即为裁定被破坏的像素级见证（§6.9）。

---

## 1. 结论摘要

### 1.1 六条结论

| # | 结论 | 证据 |
|---|---|---|
| C1 | **设备截图在同一序列/同一 APK/静止画面下是逐字节确定的**，两次连续 `exec-out screencap -p` sha256 相同；`pull` 路径与 `exec-out` 路径逐像素相同 | E1、E9 |
| C2 | **唯一的机内噪声源是系统栏**：跨进程重启后 282 px 差异全部落在 `(73,19)-(91,45)`＝状态栏时钟；按实测 insets 掩掉 `y<63`、`y≥2274` 后差异归 0 | E2、E3、E12 |
| C3 | **`font_scale` 与 `wm density` 是强信号（15.1% / 19.1%），暗色是零信号**：本仓主题只有 `lightColorScheme`，全仓无 `isSystemInDarkTheme`；`res/` 只有 `values/`（无 `values-night`、无 `values-en`） | E4、E5、E6、R3、R4 |
| C4 | **「稳定但错屏」是本方案的头号假绿源**：启动 ~1s 抓到的是 splash（与首页差 99.9998%，但 splash 自身 86,371 B 稳定可复现），跨机误抓是 launcher（差 100%）。只做「非黑帧」守卫**挡不住** | E8、E10、E14 |
| C5 | 因此 PASS 的门不是「像素差够小」，而是**六道前置断言 + 掩膜后像素判据**：身份（focus）、语义锚点、非崩溃、非黑帧、环境快照（APK sha/分辨率/密度/字号/亮暗/locale/serial）、静止 | §6.2、§6.4 |
| C6 | 本仓首页**不是逐像素稳定屏**：问候语按小时 4 档（`feature/review/.../ReviewRoute.kt:410-413`）、日期按天（`:416`）。不做掩膜时基线每天/每 4 小时失效一次 | R5、E13 |

### 1.2 采用 / 拒绝表

| 对象 | 判定 | 理由（指向证据） |
|---|---|---|
| 设备 `adb exec-out screencap -p` 作为采集通道 | **采用** | 本轮 4 台装置均可用、可复现（E1/E9）；仓内已有同源实现（`tools/android-smoke-qa.ps1:424-453`） |
| 「按百分比容差」作为**唯一**判据 | **拒绝** | 时钟噪声本身即 0.0109%（E2），已达 Paparazzi 默认 0.01% 阈值量级；靠调阈值会把真缺陷一起放进来（官方原文也自认此路会产生假正例，§4.1） |
| 先**掩膜系统栏 + 声明式动态节点**、再做严格像素判据 | **采用** | 掩膜后实测差异为 0（E3/E7），可得比 0.01% 更严的 L0 档 |
| 「非黑帧」守卫作为**唯一**稳定性守卫 | **拒绝（只能降为 INVALID 类守卫之一）** | splash 非黑、可复现、且与首页差 99.9998%（E10/E14） |
| 灰度/STD 作为「应用是否已渲染」启发式**判决** | **拒绝**（只作报告字段） | 首页 std 42.2，splash std 9.8，但合法空态屏可能同样稀疏；判决会误杀 |
| 官方 Compose Preview Screenshot Testing | **本轮不可用** | 官方页（2026-10-27）原文：独立插件 **已停止维护**，新路线要求 **AGP 9.5+**；本仓 AGP 9.3.0（§4.2、R7） |
| Paparazzi | **本轮不引** | 唯一活跃线是 `2.0.0-alpha05.1`（2026-09-28），CHANGELOG 只写到「AGP 9.0.0」支持，且该版改写了窗口/渲染模型、明确「goldens 需重录」；本仓 AGP 9.3.0（§4.3） |
| Roborazzi | **本轮不引** | 依赖 Robolectric（RNG 4.10 alpha1+）与 Gradle 插件；本仓零 Robolectric（§4.4、R8） |
| 自研 `tools/ci/screenshot_baseline.py`（纯 stdlib PNG 解码） | **采用** | 仓内无任何截图/像素差工具（R1）；无 Python 依赖清单，PIL/numpy 属环境自带不可钉（R2）；纯 stdlib 解码器本轮已实测可解 1080×2400 真机帧并覆盖 filter 1–4（E15） |

---

## 2. 仓内既存事实（R 系列，均本轮实读）

| id | 事实 | 出处 |
|---|---|---|
| R1 | `tools/ci/` **没有**任何截图/像素差脚本；计划 §0 把待建的 `screenshot_baseline.py` 写成了既有事实 | 本轮 `ls tools/ci` + `grep -rln "PIL\|numpy"`；同结论见 `docs/research/evidence/2026-11-06-batch-44/research-crosscheck.md` §3.1（M3） |
| R2 | 仓内**无** `requirements*.txt` / `pyproject.toml` / `setup.cfg`；PIL 10.4.0、numpy 2.5.1 是本机环境自带，未被任何仓内文件钉版本 | 本轮 `ls requirements*.txt pyproject.toml setup.cfg Pipfile` → 无命中；`python -c "import PIL"` → 10.4.0 |
| R3 | 主题**只有亮色**：`SmartLightColorScheme` 由 `lightColorScheme(...)` 构造，`SmartMistakeBookTheme` 直接使用它；全仓 `isSystemInDarkTheme` **0 命中** | `core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt:7`、`:155`、`:187`、`:190`；`grep -rn isSystemInDarkTheme --include=*.kt .` 无输出 |
| R4 | 资源只有 `values/`（`strings.xml`、`themes.xml`）→ 无 `values-night`、无 `values-en`；用户可见文案为中文单一语言 | `app/src/main/res/values/`；`find app/src/main/res -maxdepth 1 -type d` |
| R5 | 首页两块文本**随时间变化**：问候语 4 档（`in 5..10 早上好 / in 11..13 中午好 / in 14..17 下午好 / else 晚上好`）、日期 `SimpleDateFormat("M月d日 · EEEE")` | `feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/ReviewRoute.kt:410-413`、`:416` |
| R6 | 仓内已有截图采集函数：`shell screencap -p <dev>` → `pull` → 校验 PNG 签名（8 字节），并前后两次 `Assert-AppHealthy`；**无基线比对、无身份断言、无比对环境快照** | `tools/android-smoke-qa.ps1:424-453`（`Save-PageScreenshot`） |
| R7 | 构建面：AGP 9.3.0、Kotlin 2.3.21、Gradle 9.6.1、`sourceCompatibility = 17`；运行时 JDK 为 Temurin 21.0.11 | `gradle/libs.versions.toml:2`、`:3`；`gradle/wrapper/gradle-wrapper.properties:3`；`app/build.gradle.kts:114-115`；`java -version` |
| R8 | 全仓 `.kts/.toml/.gradle` **无** `robolectric` 命中 → 无 JVM/Robolectric 测试基建 | `grep -rn -i robolectric --include=*.kts --include=*.toml --include=*.gradle .` 无输出 |
| R9 | 底部四栏与路由名：`Review`/`Tutor`/`Library`/`Profile`，另有 `review/session`、`capture/entry`、`capture/batch`、`library/exports`、`mistake/{itemId}`、`settings/{capability,privacy,reminder,scheduling,storage}` | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoutes.kt:19-60` |
| R10 | 本仓**有黑帧失败史**：3 帧与 4 帧 `exec-out screencap` 全黑（15.5–15.9 KB），同期进程存活、UI 树可读 | 转录：`docs/research/2026-10-14-4c-stage1-record.md:64`、`docs/research/2026-10-16-verdict-chain-and-nine-track-batch-record.md:250` |
| R11 | 既有跨栅格研究已裁定：文本级指标只在「同设备 + 同 AA 状态 + 整像素原点」成立，**跨 fontScale/跨 API/跨设备必须先重基线** | 转录：`docs/research/2026-10-29-parity-calibration.md:111`（「明确不主张…」整段） |
| R12 | 文件体量门只针对 `.kt/.java`（`SRC_EXTS`），PNG 不受影响 | `tools/ci/check_file_size_gate.py:36-40` |
| R13 | 批次 43 收口状态：KD-76 open、isolation 不定案、p09 未收敛、AUD-08 FAIL、**发版就绪不成立** | 转录：`docs/research/2026-11-05-batch-43-record.md:573-578`、`:536` |

---

## 3. 设备实测（E 系列，本轮实跑）

全部命令均在本轮执行；`ADB="C:/Android/Sdk/platform-tools/adb.exe"`（`adb` 不在 PATH，`ANDROID_HOME=C:\Android\Sdk`）。

### E1 静止画面重复采集：逐字节相同

```text
$ $ADB -s emulator-5584 exec-out screencap -p > cap1.png ; sleep 2 ; ... > cap2.png
-rw-r--r-- 105248 cap1.png / 105248 cap2.png
$ sha256sum cap1.png cap2.png
7dfdfe02decbf2bb02299afa4f6fbee8d9b73a1cb6e25f48cd65ad1c5df4fd29 *cap1.png
7dfdfe02decbf2bb02299afa4f6fbee8d9b73a1cb6e25f48cd65ad1c5df4fd29 *cap2.png
```

PNG 头：`1080×2400, bit_depth=8, color_type=6 (RGBA)`。

### E2 跨进程重启：唯一差异是状态栏时钟

```text
$ am force-stop …localfirst ; am start -W -n …/MainActivity   → TotalTime: 7980
$ sleep 4 ; exec-out screencap -p > cap3.png                → 105799 B
```

逐像素差（本轮自写 stdlib 解码器）：

```text
differing = 282 px (0.0108796 %)  maxChannelDelta = 148
bbox = (73,19)-(91,45)           rows = 27
```

### E3 掩膜后差异归零

```text
排除 y<100 的行：          differing = 0, maxd = 0, bbox = None
按实测 insets 掩膜 63..2274： differing = 0, maxd = 0
```

### E4 暗色：**机身零信号**

```text
$ cmd uimode night yes ; sleep 3 ; exec-out screencap -p > cap_night.png
$ sha256(cap_night) == sha256(cap_night2)        （bfdb135e…，两次一致）
night vs light: differing = 3177 (0.1226 %)，bbox = (22,14)-(1005,48)
```

`bbox` 全部落在状态栏（y≤48）→ 应用机身**未变**。与 R3（light-only 主题）一致。

### E5 字号 1.3：强信号

```text
$ settings put system font_scale 1.3 ; sleep 3 ; screencap > cap_font130.png
font1.3 vs light: differing = 391528 (15.1052 %)，bbox = (22,15)-(1038,2295)，rows = 938
```

### E6 密度 480：强信号

```text
$ wm density 480 ; sleep 3 ; screencap > cap_dpi480.png
dpi480 vs light: differing = 494495 (19.0777 %)，bbox = (0,14)-(1079,2373)，rows = 1118
```

### E7 复原：回到时钟-only

```text
$ wm density reset ; settings put system font_scale 1.0 ; sleep 3 ; screencap
dpi reset vs light: differing = 324 (0.0125 %)，bbox = (74,19)-(91,46)
后续按 insets 掩膜复查：cap1 vs cap_font100 → 掩膜区差异 0
收尾核对：`settings delete system font_scale` 后立即读为 `null`；稍后再读为 `1.0`（**归因未定**：可能是系统回写，也可能是共享装置上他线设置；1.0 与默认等价）、`night=no`、`density=Physical 420`
```

→ **同一设备的状态是可逆、可复现的**；这也是「基线与环境快照绑定」可行的前提。

### E8 跨机对照：同 APK，但抓到的是 launcher（100% 差异，非渲染方差）

```text
5566/5584 上 base.apk sha256 均为 9b832f96…e16   （pm path + exec-out cat | sha256sum）
5566: am start -W → TotalTime 5051；sleep 4；screencap > cap_5566.png (1391203 B)
diff(cap1, cap_5566) = 2592000 / 2592000 px 差 = 100.0000 %
dumpsys window: mCurrentFocus = com.google.android.apps.nexuslauncher/…NexusLauncherActivity
logcat -b crash: java.lang.IllegalStateException: Room cannot verify the data integrity …
                 Expected identity hash: 1d086050…, found: 4d2c8e0f…
```

→ 崩溃原因（Room 身份哈希与设备旧库不符）属 B 域（迁移/设备数据面）线索，**本文不作判定**；对本方案的结论只有一条：**跨机截图不可共享基线，且必须做身份断言**。
（附带事实一：两机系统 locale 均为 `en-US`，应用文案仍是中文——与 R4 一致。附带事实二：本轮收尾时 5566 的 `font_scale=2.0`（非本轮设置，共享装置上他线所留）→ 这正是 A5 环境快照必须逐字段比对的现实理由。）

### E9 两条采集路径等价

```text
$ shell "screencap -p /sdcard/sb_b.png" ; pull … ; exec-out screencap -p > exec_now2.png
diff(pulled, exec_now2) = 0 px
$ 连续两次 shell screencap → t1/t2 均 105418 B，sha256 相同（76849f43…），diff = 0
```

**Windows/Git Bash 坑（本轮踩到）**：`adb pull /sdcard/x.png` 会被 MSYS 路径转换改写为 `C:/Program Files/Git/sdcard/x.png`，必须 `MSYS_NO_PATHCONV=1`；`adb shell screencap -p <path>` 分参数传递时曾回落为 usage，整串加引号后正常。这两条要进运行手册。

### E10 时序：~1s 抓的是 splash，且 splash 自身稳定

```text
$ am force-stop ; am start ; 立即 screencap > fast1.png ; sleep 0.5 ; > fast2.png ; sleep 4 ; > settled.png
diff(fast1, fast2)      = 3112 px，bbox = (398,2363)-(681,2373)   ← 手势条动画
diff(fast2, settled)    = 2591995 / 2592000 = 99.9998 %
目视 fast2.png = 居中 logo 的启动页；settled.png = 首页
文件体量：fast1 86371 B / fast2 86385 B（两次 splash 仅差手势条）
```

→ **固定 sleep 出基线 = 把 splash 记成首页**。这是本方案最重要的反假绿事实。

### E11 设备侧 APK 摘要（不可伪造的绑定锚）

```text
$ p=$(adb shell pm path <pkg> | sed 's/package://')
$ adb exec-out cat "$p" | sha256sum
5584: 9b832f9673179f4077a5d6559b1e84d5852df0ff82f08f2961002e5bd7e29e16
5566: 9b832f9673179f4077a5d6559b1e84d5852df0ff82f08f2961002e5bd7e29e16
```

### E12 系统栏 insets（掩膜的唯一来源，不硬编码）

```text
$ dumpsys window displays | grep cur=
init=1080x2400 420dpi  cur=1080x2400  app=1080x2337
$ dumpsys window windows   （StatusBar / NavigationBar0 的 Frames）
StatusBar:      frame=[0,0][1080,63]
NavigationBar0: frame=[0,2274][1080,2400]
```

### E13 语义锚点可用（身份断言的机读来源）

```text
$ shell "uiautomator dump /sdcard/ui.xml"   → 2.906 s，12,715 字符
texts: ['早上好，同学', '11月4日 · 星期三', '今日复习', '还没有可复习的题', …]
packages: {'com.tingyun.smartmistakebook.localfirst'}
```

→ 同时暴露 R5 的两块动态文本（问候语、日期），可用同一份 dump 的节点 bounds **精确掩膜**，无需手写像素坐标。

### E14 帧统计（黑帧守卫的实测锚）

| 帧 | 采样数（16px 栅格） | 不同颜色数 | 亮度 min/max | 亮度均值 | 亮度 std |
|---|---:|---:|---|---:|---:|
| 首页 `cap1.png` | 10,200 | 98 | 21 / 253 | 231.80 | 42.21 |
| splash `fast2.png` | 10,200 | 65 | 59 / 255 | 249.43 | 9.82 |
| launcher `cap_5566.png` | 10,200 | 1,897 | 10 / 255 | 82.90 | 90.96 |
| 合成纯黑 `black.png` | 10,200 | **1** | 0 / 0 | 0.00 | **0.00** |

→ 黑帧守卫用「不同颜色 ≤ 2 或 亮度 std < 1」可稳定抓住历史那种全黑帧（R10），但**抓不住 splash**（65 色 / std 9.8）→ 必须叠加身份断言。

### E15 解码器正确性与代价

```text
真机截图（18 张）的 PNG 行滤波分布：{1:…, 2:…, 3:…, 4:…}，即 filter 1/2/3/4 全被真数据覆盖
自造 color_type=2 + filter 0 的 1080×2400 帧可解（覆盖 filter 0）
1080×2400 RGBA 单帧 解码 + 逐像素比对：real 2.3 s
```

### E16 动画开关（本机默认全开）

```text
animator_duration_scale=1.0  transition_animation_scale=1.0  window_animation_scale=1.0
```

→ E10 的手势条抖动即由此而来；采集流程必须**自证静止**（连续两帧逐字节相同），而不是关动画（关动画属装置写操作，且改变被测状态）。

---

## 4. 外部事实与采纳/拒绝

### 4.1 官方截图测试页（2026-10-08）

来源：`https://developer.android.com/training/testing/ui-tests/screenshot`（页面自述 Last updated 2026-10-08 UTC）。抓取通道：本轮 `WebFetch` 对 `developer.android.com` 首次 `Connect Timeout`，按「单通道失败换通道」改用 `mcp__searchd__extract_content` 取全文，成功。

| 原文（逐字） | 本文用途 |
|---|---|
| "**Important:** Screenshot testing is the recommended way to verify visual attributes in Compose UIs." | 采纳「截图是可推荐手段」，但不等于「设备截图是推荐实现」 |
| "You can use third-party tools to create both instrumented and local screenshot tests. If you use Compose, you can use the official Compose Preview Screenshot testing tool." | 官方页**只点名官方工具 + 第三方泛称**，无 Paparazzi/Roborazzi 点名 → 计划 §0 的「三条路线（官方页）」表述不成立（与 crosscheck M4 同结论） |
| "Different platforms (Linux, Max, and Windows) produce slightly different screenshots." | 采纳：基线跨宿主不可比 → 本仓统一在**同一台设备**上采集 |
| "**Warning:** Even if your whole team uses the same platform to generate the screenshots, changes in libraries, operating systems or hardware architecture can create small changes." | 采纳并加严：**同一 AVD 镜像 + 同一 APK** 才算同一环境（E8 的 5566 即反例） |
| "Configure a tolerance based on a percentage of modified pixels or a percentage of total difference in pixel values." + "The disadvantage of this approach is that it could create false positives, and not catch errors that are either below the threshold, or erroneously considered similar enough." | **拒绝**把百分比容差当唯一判据（官方自认会漏检）；改为「掩膜后严格判据 + 分级降级」 |
| "To use a pixel-perfect screenshot comparator, you must make sure that your tests take screenshots in the same conditions." | 采纳：本方案 L0 档即在「同一条件」下的逐像素比较 |
| "**Key Point:** Avoid combinations of all the different scenarios in screenshot tests. Instead, choose screenshots that give you unique feedback." + "Start by checking your reference images into your repository, but limit their overall count." | **采纳并据此改计划**：不做「字体×密度×暗色×字号」全笛卡尔积（§6.5），基线入库但设体量上限（§6.6） |

### 4.2 官方 Compose Preview Screenshot Testing（2026-10-27）

来源：`https://developer.android.com/studio/preview/compose-screenshot-testing`（本轮抓取，Last updated 2026-10-27 UTC）

- 原文（页面首屏 Caution）："The standalone `com.android.compose.screenshot` plugin described on this page is **no longer maintained**. We recommend setting up screenshot tests with **AGP test suites** using Android Gradle Plugin (AGP) **9.5 or higher**."
- 要求（原文）：`android.experimental.enableScreenshotTest=true`；插件 `0.0.1-alpha16`；`@PreviewTest` 放在 `screenshotTest` source set；参考图在 `src/screenshotTestDebug/reference`；任务 `update{Variant}ScreenshotTest` / `validate{Variant}ScreenshotTest`；host 侧 Layoutlib 渲染。
- **采纳**：作为「将来首选路线」；**本轮拒绝**：本仓 AGP **9.3.0 < 9.5**（R7），且「不再维护」的独立插件路线不应引入发布门。（crosscheck 记的「experimental」在 2026-10-08 的导航页仍成立，但 2026-10-27 的本页已改口径为「停止维护」——两页不同日期，以本页为准。）

### 4.3 Paparazzi（GitHub API 实查 + CHANGELOG 实读）

```text
$ gh api repos/cashapp/paparazzi --jq '…'
{"archived":false,"default_branch":"master",
 "description":"Render your Android screens without a physical device or emulator",
 "full_name":"cashapp/paparazzi","license":"Apache-2.0",
 "pushed_at":"2026-11-03T01:59:28Z","stargazers_count":2638}
$ gh api repos/cashapp/paparazzi/releases --jq '.[0:6][]'
2.0.0-alpha05.1 2026-09-28   ← 最新
2.0.0-alpha05   2026-05-20
2.0.0-alpha04   2026-01-20 … (全部 2.0.0-alpha*)
```

CHANGELOG（`gh api …/contents/CHANGELOG.md`）关键行：

- `2.0.0-alpha05.1`：「…swaps layoutlib's simplified window and render model for the framework's… **Accessibility goldens, and goldens using the `V_SCROLL`, `H_SCROLL` and `FULL_EXPAND` rendering modes, change with this version and will need re-recording.**」；New 列表含「**[Gradle Plugin] Android Gradle Plugin 9.0.0**」与「Compose 1.11.2」。
- `2.0.0-alpha05`：「This release supports **pre-AGP 9.0** consumers.」「As of this release, consumers must build on **Java 21+** environments.」（后者在 alpha04 段）

**判定**：Apache-2.0、活跃（2026-11-03 有推送）、host 侧、无需设备——**优点明确**；但（a）唯一在维护的线是 **alpha**；（b）其声明支持的最高 AGP 为 **9.0.0**，本仓 **9.3.0** 未经其测试；（c）跨 alpha 版本 golden 会失效需重录（与「不可伪造」的基线稳定性目标冲突）；(d) 引入即新依赖，须单独提交并过供应链复核（计划 §6 `:88`）。→ **本轮不引**。

### 4.4 Roborazzi（GitHub API 实查 + README 实读）

```text
$ gh api repos/takahirom/roborazzi --jq '…'
{"archived":false,"default_branch":"main","description":"Make JVM Android integration test visible 🤖📸",
 "full_name":"takahirom/roborazzi","license":"Apache-2.0",
 "pushed_at":"2026-11-01T03:54:06Z","stargazers_count":1078}
latest release: 1.77.0 (2026-10-22)
```

README：`## Roborazzi now supports Robolectric Native Graphics (RNG)`；`## Add Robolectric`；"This library is dependent on Robolectric."；"To take screenshots, please use **Robolectric 4.10 alpha 1 or later**"；使用需 `io.github.takahirom.roborazzi:roborazzi-gradle-plugin`。

**判定**：Apache-2.0、活跃、有稳定版（1.77.0）、Compose 支持好；但**强依赖 Robolectric**，而本仓零 Robolectric（R8）→ 引入等于同时引入 JVM 测试基建 + 一款 Gradle 插件 + RNG 图形栈。→ **本轮不引**。

### 4.5 前序仓内研究的可复用结论

- `docs/research/2026-10-30-v01-chain-seams.md:200-201`（转录）：本仓视觉链的 painter 断言必须在真设备执行（QuickJS 仅设备可执行），JVM 截图方案「不适配」。
- `docs/research/2026-10-28-screen-pdf-parity-gate.md`、`docs/research/2026-10-29-parity-calibration.md`（转录）：±2 逐通道容差与 0.01% 面积阈有 Paparazzi 先例；文本级指标只在「同设备 + 同 AA + 整像素原点」成立。

→ 也就是说：**截图基线不能替代既有的屏/PDF 文本级 parity 门**，两者是不同颗粒度（§6.8）。

---

## 5. 路线对比与「何时必须引库」

| 维度 | 设备截图（本方案） | Paparazzi | Roborazzi | 官方 AGP test suites |
|---|---|---|---|---|
| 执行环境 | 真机/模拟器 | host（layoutlib） | host（Robolectric + RNG） | host（layoutlib） |
| 新增依赖 | **0** | 插件 + layoutlib（alpha 线） | 插件 + Robolectric + RNG | 插件（AGP 9.5+） |
| 覆盖真实 GPU/字体/输入法 | **是** | 否（非设备渲染） | 否（影子框架） | 否 |
| 覆盖本仓 QuickJS 设备侧视觉链 | **是**（唯一） | 否（转录 `v01-chain-seams.md:200`） | 否 | 否 |
| 组件级/每 PR 成本 | 高（设备启动＋导航） | 低 | 低 | 低 |
| 确定性 | 同机同 APK **逐字节**（E1/E9） | 跨机仍有平台差异（官方页 4.1） | 同 Paparazzi 类 | 同 Paparazzi 类 |
| 当前可落地性 | 立即可用 | AGP 9.3 > 其声明 9.0，alpha | 缺 Robolectric 基建 | 需 AGP 9.5+（本仓 9.3.0） |

### 5.1 本批不引依赖的理由（逐条可复核）

1. 计划 §6 `:88` 明确「不引新依赖…若必须引，单独提交并过供应链复核」——本批的交付是**验收替代机制**，不是测试基建换代。
2. 三条外部路线**没有一条同时满足「稳定版 + 兼容当前 AGP 9.3.0 + 零既有基建」**（§4.2–4.4）。
3. 设备截图能覆盖另外三条**结构上不可能覆盖**的面：真 GPU 光栅、QuickJS 设备侧视觉链、系统栏/输入法/真实字体树（转录 `v01-chain-seams.md:200`、官方页平台差异段）。
4. 纯 stdlib 解码器已实测够用（E15：2.3 s/帧，覆盖真数据全部滤波类型），**新增依赖换不来任何本批需要的判定能力**（依「新增必须指认它消灭的具体失败」：引库消灭不了任何当前失败）。

### 5.2 触发「必须引库」的条件（任一命中即必须在下一批立题，不得继续用设备截图勉强）

| # | 条件 | 为什么它把设备路线压垮 | 优先引哪个 |
|---|---|---|---|
| K1 | 需要**组件级**（非整屏）回归，或每 PR 触发 | 设备导航 + 采集无法进 PR 快车道（官方页：screenshot tests 比行为测试慢） | Paparazzi（Compose 直接 `snapshot{}`）或官方 AGP suites |
| K2 | 需要**种子数据**才能到达的屏（`mistake/{id}`、`review/session`、导出结果）超过 5 个 | 每屏造 fixture DB 的成本 > 引库成本 | Roborazzi（Robolectric 可注入宿主/数据） |
| K3 | AGP 升到 **≥ 9.5** | 官方路线成本骤降且是官方推荐 | **官方 AGP test suites**（首选，非 Paparazzi） |
| K4 | 基线数量 > **60** 或连续 3 轮验证的误报率 > 5% | 体量/维护成本越过官方「limit their overall count」 | 官方 AGP suites → 否则 Paparazzi |
| K5 | 需要**跨机/跨 API** 的视觉一致性（如平板/折叠） | 每台设备一套基线不可维护（R11：跨设备必须重基线） | host 侧渲染（Paparazzi/Roborazzi）承担形态矩阵，设备截图只留金标 |
| K6 | Paparazzi 发布**支持 AGP 9.3+ 的稳定版**（非 alpha） | 当前否决理由（alpha + AGP 9.0 上限）消失 | 重新评估 Paparazzi |

> 「什么时候引」写成 K1–K6 的目的：避免把「本批不引」读成「永远不引」。每条都带可观测阈值，命中即立题。

---

## 6. 方案契约（`tools/ci/screenshot_baseline.py`，本轮不实现）

### 6.1 CLI 与产物

```text
python tools/ci/screenshot_baseline.py record  --serial <S> --case <case-id> [--allow-update --reason <text>]
python tools/ci/screenshot_baseline.py verify  --serial <S> [--case <case-id>] [--json-out <path>]
python tools/ci/screenshot_baseline.py diff    --a <png> --b <png> [--mask-insets-from <json>]
```

- `record`：写 `<baseline-dir>/<case-id>/<env-sig>.png` + `manifest.json`；**仅当**工作树在基线目录与她处均无未提交改动时允许（防夹带）。
- `verify`：**只读**（不写基线、不改设备设置）；输出 JSON 报告 + 人类可读摘要；退出码 `0=PASS / 1=FAIL / 2=INVALID(前置断言失败) / 3=BLOCKED(环境不匹配)`。
- `diff`：纯函数式子命令，供负控测试与人工复核复算。
- 依赖：**Python stdlib 仅**（`zlib`/`struct`/`hashlib`/`json`/`subprocess`/`xml.etree`）。不 import PIL/numpy（R2：未被钉版本）。

### 6.2 采集前置断言（按顺序，任一失败 → INVALID，不进像素判定）

| # | 断言 | 实现（本轮已验证可用） | 消灭的具体失败 |
|---|---|---|---|
| A1 | **身份**：`mCurrentFocus` 的组件名 == 期望 `pkg/Activity` | E8 的 `dumpsys window` | 抓到 launcher 当首页（E8） |
| A2 | **语义锚点**：`uiautomator dump` 中 `package=` 集合 ⊆ {被测包}，且声明的锚点（text 或 resource-id）命中 | E13（2.9 s，可解析） | 抓到 splash（E10）：splash 无首页锚点 |
| A3 | **无崩溃**：本次 run 起点之后 `logcat -b crash` 无被测包 FATAL | 5566 的 crash buffer 可直接命中（E8） | 崩在启动页后截到系统界面 |
| A4 | **非黑帧**：16px 栅格采样，`distinct_colors ≥ 3` 且 `lum_std ≥ 1` | E14 四类帧实测 | 历史黑帧（R10） |
| A5 | **环境快照**：`serial / api / abi / wm size / density / font_scale / night / locale / APK sha256 / 分辨率 / insets` 全部等于 manifest 里记录的值 | E11、E12、E7 | 换了装置/密度/字号还拿旧基线判绿 |
| A6 | **静止**：连续两次 `screencap` **逐字节相同**（最多重试 N=10，间隔 0.4 s） | E1/E9 | 手势条动画、页面动画未停（E10：3112 px） |

> A6 用「两帧相同」而不是「关动画」：关动画要写 `settings put global …`，属装置写，且会改变被测状态（E16 实测三档全为 1.0）。

### 6.3 归一化与掩膜（掩膜必须可复算，不写死坐标）

`mask` = 下列矩形之并（坐标全部来自同一次运行的设备输出，写入报告）：

1. **状态栏**：`[0,0][W,h_status]` ← `dumpsys window windows` 的 `StatusBar` frame（实测 `[0,0][1080,63]`）。
2. **导航栏**：`[0,h_nav][W,H]` ← `NavigationBar0` frame（实测 `[0,2274][1080,2400]`）。
3. **声明式动态节点**（每 case 一份，默认空）：按 `text` 正则或 `resource-id` 从同一次 `uiautomator dump` 取 `bounds`，外扩 4 px。首页 case 必须声明两条：
   - 问候语：`^(早上好|中午好|下午好|晚上好)，同学$`（R5 `:410-413`）
   - 日期：`^\d{1,2}月\d{1,2}日 · \S+$`（R5 `:416`）
4. 掩膜不得覆盖「本 case 想验证的区域」；`record` 时把掩膜矩形与来源字段一并写入 manifest，`verify` 必须**重算**同一份掩膜（矩形不一致 → BLOCKED），防止「改掩膜把差异盖掉」。

实测支撑：掩掉 1+2 后 cap1 vs cap3 = 0 px（E3）；掩掉 1+2 后「暗色往返」「字号往返」的帧 = 0 px（E7）。若不掩 3，首页基线在跨小时/跨天必然红（R5）——这是**基线寿命**问题，不是产品缺陷。

### 6.4 度量与阈值（L0–L3 + 守卫），全部用本轮实测标定

比较量（在**未掩膜像素**上计算，分母 `N_u = 2,387,880`（1080×2211，E12 实测））：

- `d_px`：`max(|ΔR|,|ΔG|,|ΔB|) > τ_px` 的像素数
- `τ_px = 2`：逐通道 ±2 噪声带（Paparazzi `OffByTwo` 先例，转录 `2026-10-28-screen-pdf-parity-gate.md:72`）

| 档 | 判据 | 判词 | 实测锚 |
|---|---|---|---|
| **L0** | `d_px == 0` | **PASS**（严格） | 实测可达：E3/E7 均为 0 |
| **L1** | `d_px ≤ 0.01% × N_u`（≈ 239 px） | PASS（报告标注 `tolerance=L1`） | Paparazzi 默认 `maxPercentDifference=0.01`（转录同源） |
| **L2** | `0.01% < d_px ≤ 0.5% × N_u` | **WARN，不得自动计 PASS**（退出码按 FAIL 处理，必须人看报告） | 未掩膜时钟噪声 282–324 px（0.0109–0.0125%）正落此档 → 证明「调阈值」等于把时钟当红灯 |
| **L3** | `d_px > 0.5% × N_u` | **FAIL** | 字号 1.3 = 15.10%、密度 480 = 19.08%（E5/E6），是 L3 下限的 33–41 倍 |

**明确不主张**：本表阈值只对「同 serial + 同 APK sha + 同 API/密度/字号/亮暗/locale + 已掩膜 + 已静止」成立；跨任一项必须先 `record` 新基线（R11 同向）。`d_px` 是**整屏安全网**，不替代文本级 parity 门（§6.8）。

### 6.5 矩阵：不做事后笛卡尔积，四轴各自定「基线」或「不变量」

计划 §2 C 域写「字体/缩放/暗色/字号」。按官方「choose screenshots that give you unique feedback」（§4.1）与本轮实测信号强度，**逐轴裁定**：

| 轴 | 实测信号 | 处理方式 | 理由 |
|---|---:|---|---|
| 基准环境（1080×2400/420dpi/font 1.0/日间/API34/x86_64） | — | **基线**：每个 case × 1 张 | 基准 |
| 字号 `font_scale=1.3` | 15.10%（E5） | **基线**：仅 2 屏（首页、我的）；其余屏用「不裁切结构断言」 | 全屏基线 × N 屏会爆体量；结构断言（uiautomator 节点 `bounds` 均在屏内且不重叠）能抓住「字放大后截断」这一类真失败 |
| 密度 `density=480` | 19.08%（E6） | **基线**：仅 2 屏（首页、错题本） | 同上；密度已验证强信号，不需每屏 |
| 暗色 `night=yes` | **0（机身）**（E4） | **不变量**，不建基线 | 本仓 light-only（R3）。断言：暗色下掩膜区与日间**逐像素相等**；一旦有人加暗色主题，此断言立即红并强制补基线 |
| locale | **UNVERIFIED** | **暂不设轴** | 本仓无 `values-en`（R4），但本轮**没有做同机 locale A/B**（改 `persist.sys.locale` 需重启装置，超出本轮只读预算）→ 不写不变量，见 §7-U2 |
| 横竖屏 | 未测 | 下一批（需 `settings put system user_rotation` + `accelerometer_rotation`） | 本轮未做，不预设结论 |

首页 **不是**像素稳定屏（R5），必须走 §6.3 第 3 类掩膜；若不接受掩膜，则首页只能进「不变量」组（例如「问候语 ∈ 4 档之一」的结构断言），不能进基线组——两种都合法，但**必须显式选一种**并写进 case 定义。

### 6.6 基线存储与体量

- 位置：`tools/ci/screenshot_baselines/<case-id>/<env-sig>.png` + `manifest.json`（**与源码同仓**，官方建议入库；不用 LFS，避免 CI 额外依赖）。
- `env-sig`：`<serial>-api<api>-<W>x<H>-d<dpi>-fs<font_scale>-<light|dark>-<locale>` 的稳定哈希（8 hex），保证「环境一变即不匹配」。
- 体量上限：**≤ 60 张 / ≤ 8 MB**（单张实测 103–127 KB，E2/E4/E5/E6）。超限即触发 K4。
- PNG 不受体量门限制（R12），但仍禁把 `build/` 产物入仓。

### 6.7 不可伪造（8 条，逐条指认它消灭的伪造路径）

| # | 机制 | 消灭的伪造/误判路径 |
|---|---|---|
| F1 | **三链绑定**：`manifest.json` 记 `png_sha256`、`apk_sha256`（设备侧 `pm path`+`cat` 取，E11）、`tool_sha256`；三者由 `verify` **重算**比对 | 手改 JSON 的 verdict、换 APK、换工具 |
| F2 | **verify 只读**：不写基线、不改设备设置；`--allow-update` 是唯一写入口且必须带 `reason` | 「跑一次让它绿」 |
| F3 | **基线更新单独提交**：`record` 要求工作树在基线目录外**无未提交改动**；`verify` 要求基线目录 `git status --porcelain` 为空 | 代码改动 + 基线更新夹带在同一笔（最隐蔽的假绿） |
| F4 | **报告必须含原始证据指针**：每条 case 存 `png_sha256` + 原始 PNG 路径 + `dumpsys`/`uiautomator`/`props` 原始输出片段 | md/summary 单独冒充证据（计划 §5 允许「证据文件之一」，此处收紧） |
| F5 | **掩膜可复算**：掩膜矩形与其来源字段入报告，`verify` 重算并比对（§6.3-4） | 放大掩膜把差异盖掉 |
| F6 | **严格档默认**：缺省 L0；放宽到 L1/L2 必须显式参数，且参数写入报告 | 悄悄放宽阈值 |
| F7 | **前置断言不可跳过**：A1–A6 任一失败 → exit 2 INVALID，不产生 PASS 记录（计划 §1 项 4 的「禁止 UNVERIFIED 标 PASS」同向） | 用 PASS 覆盖「没验成」 |
| F8 | **双证据类**：PASS 需 (i) 机器 JSON 报告 + (ii) 设备原始 PNG（sha256 可复算）+ 至少一条 adb 原始输出；单类不进 PASS | 计划同条「PASS 需 ≥2 类独立证据」 |

> 边界（必须写清）：以上只保证「报告不能被手改后仍自洽」，**不**保证「基线本身是人工批准过的正确画面」。后者只能靠「基线变更必须单独提交 + 评审看 diff 图」这一人工环节；自动化处无法替代，标 **UNVERIFIED（人工审批）**。

### 6.8 与既有门的颗粒度分工

| 门 | 颗粒度 | 覆盖 | 不覆盖 |
|---|---|---|---|
| `screenshot_baseline.py`（本方案） | 整屏像素 | 版式塌陷、整块缺失、颜色主题漂移、系统栏意外改色、密度/字号导致的截断 | 1–2 px 文本位移的**归因**、AA 状态差异（R11 已证文本级需专用指标） |
| 既有屏/PDF parity 门（转录 `2026-10-29-parity-calibration.md`） | 文本掩膜/质心/墨量/TV | 文本替换、字形子集缺失、屏↔PDF 一致性 | 全屏版式、非文本区域 |
| 语义树/无障碍检查（I 域） | 节点 | 触摸目标、标签、对比度规则 | 视觉美观 |

→ 三者**互补**，不可互相顶替；本方案只在「整屏安全网 + 设备侧真实渲染」这一格上唯一。

### 6.9 与 6C-2 裁定的见证关系（只加不变量，不改行为）

- 备份/恢复相关 case 的基线里若出现任何含「8」的 mask 选项，或出现允许「0」的控件，则该基线**反向证明**裁定被破坏 → 这类 case 的 PASS 同时也是裁定的像素级见证。
- 该见证**只读**，不新增代码路径，不触碰 `core/domain`/`core/ui` 热区（符合本批禁改清单）。

---

## 7. 未验证 / 阻塞（不得计 PASS）

| id | 项 | 状态 | 复跑或解除条件 |
|---|---|---|---|
| U1 | 跨宿主/跨 OS 的逐字节一致性 | **UNVERIFIED**（本方案不依赖它：全部采集在同一台设备上） | 若将来在 CI 机上采集，须先做两机同帧比对 |
| U2 | locale 轴 | **UNVERIFIED**：本轮未做同机 A/B（需 `persist.sys.locale` + 重启，属装置写，超出本轮范围） | 在专用 AVD 上：`settings put system user_locale`（或 `persist.sys.locale`）+ `reboot`，各录一张后按 §6.4 判定 |
| U3 | 横竖屏、平板/折叠形态 | **未测** | 需 `settings put system accelerometer_rotation 0` + `user_rotation 1`，或另一 AVD |
| U4 | 需种子数据才能到达的屏（`mistake/{id}`、`review/session`、`library/exports`） | **BLOCKED** | 需 fixture DB 或应用内种子入口；否则只能靠引库路线（K2） |
| U5 | 跨装置基线共享 | **BLOCKED（按设计禁止）** | 实测反例 E8：同 APK 在两机上，一台因旧库崩在启动（Room identity hash），另一台正常 → 误抓 launcher |
| U6 | `emulator-5566` 的启动崩溃 | **不判定**（属 B 域线索） | 由迁移/数据面线复核 `Expected 1d086050… / found 4d2c8e0f…` |
| U7 | 工具实现本身（`tools/ci/screenshot_baseline.py`） | **本轮未写** | 本文 §6 即其验收契约；实现须配 `tools/tests/test_screenshot_baseline.py`，负控至少 4 条：黑帧→INVALID、splash→INVALID（A2）、单像素改动→L0 红、时钟-only→掩膜后 L0 绿 |
| U8 | 人工审美/语感（版式是否好看、文案是否得当） | **UNVERIFIED（不可编码）** | 不由本文签字通过，也不由任何自动门替代 |

---

## 8. 复跑命令（本轮全部实跑过）

```bash
ADB="C:/Android/Sdk/platform-tools/adb.exe"
S=emulator-5584
PKG=com.tingyun.smartmistakebook.localfirst
ACT=$PKG/com.tingyun.smartmistakebook.MainActivity

# 1) 设备与包身份
"$ADB" -s $S shell getprop ro.build.version.sdk
"$ADB" -s $S shell wm size ; "$ADB" -s $S shell wm density
p=$("$ADB" -s $S shell pm path $PKG | tr -d '\r' | sed 's/package://')
"$ADB" -s $S exec-out cat "$p" | sha256sum

# 2) 确定性（须两次同一 sha256）
"$ADB" -s $S exec-out screencap -p > /tmp/a.png ; sleep 2
"$ADB" -s $S exec-out screencap -p > /tmp/b.png
sha256sum /tmp/a.png /tmp/b.png

# 3) 环境（掩膜来源）
"$ADB" -s $S shell "dumpsys window displays" | grep cur=
"$ADB" -s $S shell "dumpsys window windows" | grep -E "StatusBar\}|NavigationBar0\}"

# 4) 身份锚点
MSYS_NO_PATHCONV=1 "$ADB" -s $S shell "uiautomator dump /sdcard/ui.xml"
MSYS_NO_PATHCONV=1 "$ADB" -s $S pull /sdcard/ui.xml /tmp/ui.xml

# 5) 矩阵轴（改完必须复原，见本轮收尾核对）
"$ADB" -s $S shell cmd uimode night yes          # 暗色 → 复原 no
"$ADB" -s $S shell settings put system font_scale 1.3   # → 复原 1.0 / delete
"$ADB" -s $S shell wm density 480                # → wm density reset
```

本轮收尾已复原并核对：`night=no`、`density=Physical 420`、`font_scale` 删除后读为 `null`、稍后读为 `1.0`（归因未定，见 E7）；设备临时文件（`/sdcard/*.png`、`/sdcard/ui.xml`）已删除。

---

## 9. 来源清单

| 主题 | 来源 | 本轮用法 |
|---|---|---|
| 截图测试官方口径（推荐、平台差异、容差与假正例、最小化组合、参考图入库） | `https://developer.android.com/training/testing/ui-tests/screenshot`（Last updated 2026-10-08 UTC） | 采纳 5 条、拒绝 1 条、指正 1 条（§4.1） |
| Compose Preview Screenshot Testing（停止维护 + AGP 9.5+） | `https://developer.android.com/studio/preview/compose-screenshot-testing`（Last updated 2026-10-27 UTC） | 判「本轮不可用」（§4.2） |
| Paparazzi 事实 | `https://api.github.com/repos/cashapp/paparazzi`、`…/releases`、`…/contents/CHANGELOG.md` | Apache-2.0、2638★、pushed 2026-11-03、最新 2.0.0-alpha05.1、支持 AGP 9.0.0、Java 21+（§4.3） |
| Roborazzi 事实 | `https://api.github.com/repos/takahirom/roborazzi`、`…/releases/latest`、`…/contents/README.md` | Apache-2.0、1078★、pushed 2026-11-01、1.77.0、依赖 Robolectric 4.10 alpha1+（§4.4） |
| 仓内前序裁定 | `docs/research/2026-10-29-parity-calibration.md`、`docs/research/2026-10-28-screen-pdf-parity-gate.md`、`docs/research/2026-10-30-v01-chain-seams.md`、`docs/research/2026-11-05-batch-43-record.md` | 转录（标「转录」处） |
| 批次 44 研究交叉复核 | `docs/research/evidence/2026-11-06-batch-44/research-crosscheck.md`（M3/M4/M5/M12） | 转录/同向引用 |

---

## 10. 本文自证边界

- 本文所有 **E 系列**均为本轮在本机执行的命令与真实输出（含失败：E8 的崩溃、E10 的 splash、E9 的 MSYS 路径坑）。**未跑 Gradle、未跑 instrumented test、未构建/安装 APK**，故不对任何门禁是否通过作结论。
- 本文所有 **R 系列**均给出 `path:line`；标「转录」的三处（R10、R11、R13）引用既有文件原文，未回读其一手来源。
- 本文所有外部引用均为本轮抓取（页面自述更新时间随附）或 GitHub API 返回值；**未**取 GraphQL/网页 UI 上的 star 实时值以外的任何二次转述。
- 「基线审批由人工完成」这一环**不可自动化**，已在 §6.7 与 §7-U8 标 UNVERIFIED；本文不给任何 PASS 判词。
- 体量：本文件落盘后 `wc -lc` 实测见下（纯文本，无图片）：

```text
$ wc -lc docs/research/2026-11-06-screenshot-baseline.md
540  docs/research/2026-11-06-screenshot-baseline.md        # 行数
43.7K docs/research/2026-11-06-screenshot-baseline.md        # 字节数（≈43.7 kB，UTF-8；本节自身也计入，微动 ±数十字节）
```
