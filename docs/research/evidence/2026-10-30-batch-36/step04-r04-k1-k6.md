# E-04 R-04 K1–K6（keep 扩充后重跑；K1–K4 绿、K5/K6 无执行证据）

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-30-batch-36 |
| step | R-04 窗口（K1–K6） |
| gate-or-kd | R-04（release 轨混淆后设备往返）/ 计划任务 1–2 |
| verdict | PARTIAL |
| executed-at | 2026-10-30T08:53–08:59+08:00（宿主 mtime） |
| operator | 设备轨（原始日志执行者）；本文件由证据落盘员摘录，keep 门由落盘会话复跑 |
| head | `4f11cd5432e6c259038fc92bd2e975aee8d304b4` |
| worktree | 706 项（仅记录；keep 两文件 ` M`） |
| device-serial | `emulator-5562` |
| device-avd | 未采集 |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk`（155,749,569 B）/ `app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk`（1,118,864 B） |
| apk-sha256 | `1c7fe3f7892efb4392a3dd0985a56f0908394152d5ba91435a0ee17244b6b927` / `03d1d426d0cd083497a12ba07d3301bc8189182cef976acde2c6873c9bdf9b3e`（`r04-two-apk-sha.txt`；本会话 `sha256sum` 复算同值） |
| apk-version | —（未读） |
| exit-code | K2 `K2_BUILD_EXIT=0`（`r04-step2-build.log` 末行）；K4 `0`（本会话复跑）；K5/K6 无执行证据 |
| raw-log | `build/device-track-batch36/r04-*`（本机，不入仓）；K6 最近一次实测在 `build/device-track-batch35/r04-k6-logcat.txt` |
| record-ref | `docs/research/2026-10-30-batch-36-record.md`（未落盘） |
| sanitized | 是（本批 `00-index.md` §7；一次性 keystore 与口令载体在 `build/`，本文件不引用其内容） |

## 1. 目的与判据

判据：R-04 原判据不放宽（禁 `-dontobfuscate`、禁 internal 变体）；keep 扩充后**从步 2 重跑**全链；**K6 设备两例**（`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`、`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`）必须得到 `OK (2 tests)` 且崩溃签名 0，才可关闭。计划：`.zcode/plans/plan-2026-10-30-batch-36-v01-r04-k6.md:17-18`；重跑顺序与 K6 命令：`docs/research/2026-10-30-r04-k6-keep-rules.md:123-138`（该研究自述"未运行任何构建/设备用例"`:177`）。

## 2. 命令（可复跑）

```bash
# 步 0：一次性本地 keystore（RSA-2048/3650d）；口令只经环境变量传递，不落参数/不落日志
# K1 红基线：release 主包 + debug 测试包
./gradlew :app:assembleStrictOfflineRelease :app:assembleStrictOfflineDebugAndroidTest
adb -s emulator-5562 uninstall com.tingyun.smartmistakebook.offline
adb -s emulator-5562 uninstall com.tingyun.smartmistakebook.offline.test
adb -s emulator-5562 install -r -t app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
adb -s emulator-5562 install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
adb -s emulator-5562 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner   # → 签名门红

# K2：release 测试轨构建（release testProguardFiles 生效）
./gradlew :app:assembleStrictOfflineReleaseAndroidTest -Psmbk.testBuildType=release

# K3：两包证书
apksigner verify --print-certs app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk > r04-k3-main.txt
apksigner verify --print-certs app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk > r04-k3-test.txt

# K4：keep 门（脚本权威）
python tools/ci/check_r04_keep_rules.py               # 本会话复跑：0 hits / exit 0

# K6（判据原文，本批无执行证据）：
#   install -r -t 两包 → logcat -c → am instrument 两例 → logcat 取签名
```

- 工作目录：仓库根；`MSYS_NO_PATHCONV=1`；`JAVA_HOME` 用 Windows 形式（apksigner `.bat`）。

## 3. 结果摘要

| 步 | 断言 | 结果 | 证据（`build/device-track-batch36/`） |
|---|---|---|---|
| K1 | 签名不一致时 release 轨红基线成立（debug 测试包 vs release 主包），且非 R8 崩溃 | PASS | `r04-step1-build.log:811` `BUILD SUCCESSFUL in 3m 26s`；`r04-step1-red.log:2` `Permission Denial … does not have a signature matching the target`；`r04-step1-red-logcat.txt` 九类签名 **0** |
| K2 | release 测试轨可构建（`testBuildType=release`） | PASS | `r04-step2-build.log:543` `BUILD SUCCESSFUL in 56s`；末行 `K2_BUILD_EXIT=0`；产物含 `assembleStrictOfflineReleaseAndroidTest` |
| K3 | 主/测两包同一证书 | PASS | 两份均 `certificate SHA-256 digest: bb3e8dee8c1c08ad46cc9cd3c453af8da74dfb8f40a4c38ea346fa97104aa605`（`r04-k3-main.txt:2`、`r04-k3-test.txt:2`） |
| K4 | `-dontobfuscate` 规则文本 0 命中 | PASS | `r04-k4-keepgate.txt` 三行 OK；本会话复跑 `python tools/ci/check_r04_keep_rules.py` → 同上、`KEEP_GATE_EXIT=0` |
| K5 | so 双 ABI + 测试类未改名 | **未按判据执行** | 批内无 `r04-k5b-count.txt`；本会话只读补查：`unzip -l` 见 `lib/arm64-v8a/libquickjs.so`（924,936 B）与 `lib/x86_64/libquickjs.so`（911,248 B）；dex 计数未跑 |
| K6 | 设备两例往返 `OK (2 tests)` + 签名 0 | **无执行证据（无产物）** | `find build -maxdepth 2 -iname '*k6*'` 仅两件、均在 `batch35/`（`r04-k6-logcat.txt`、`r04-k6.log`）；`find build/device-track-batch36 \( -iname '*k6*' -o -iname '*k5*' \)` → **0** |

- 窗口源稳定：`r04-src-sha-diff.txt` 0 字节（before/after 各 257,585 B），且本会话 `sha256sum -c` 对现树 1548 文件全等（E-00 §3）。
- 窗口 keep 稳定：`r04-window-keep-sha-before.txt` 与 `r04-window-keep-sha-after2.txt` 三行 sha 逐字相同（`r04-test-keep.pro` `35cd987f…`、`proguard-rules.pro` `38901b59…`、`build.gradle.kts` `1bcc0247…`）→ 08:57/08:59 两包确实用扩充后的规则构建。

## 4. 关键输出（有界）

```text
r04-step1-red.log:2  Permission Denial: … package com.tingyun.smartmistakebook.offline.test does not
                     have a signature matching the target com.tingyun.smartmistakebook.offline
r04-step2-build.log:543  BUILD SUCCESSFUL in 56s … K2_BUILD_EXIT=0
r04-k3-main.txt:2  V2 Signer: certificate SHA-256 digest: bb3e8dee…
r04-k3-test.txt:2  V2 Signer: certificate SHA-256 digest: bb3e8dee…
r04-k4-keepgate.txt  OK: …proguard-rules.pro: 0 hits / OK: …r04-test-keep.pro: 0 hits / OK: -dontobfuscate 0 hits
```

### 4.1 keep 扩充逐条（"为什么需要"）

| 文件:行 | 规则 | 消灭的具体失败（崩溃签名） | 机理（规则内注释自述，已实读） |
|---|---|---|---|
| `app/r04-test-keep.pro:42-74` | `-keep class j$.** { *; }`（`:74`） | `NoSuchMethodError: No direct method <init>(Ljava/util/Map;)V in class Lj$/util/g;` | 主/测两包由**两次独立 R8** 产出、无改名协调；合并 classloader 先加载测试 dex，`j$.util.DesugarCollections$SynchronizedMap` 在主包被改名为 `j$.util.g`，而测试 dex 的 `j$.util.g` 是另一源类 → 按名解析到无 `Map` 构造器的类。全量 keep `j$.**` 使测试 dex 不再占用主包改名名、且成员为超集。选 `j$.**` 而非 `j$.util.**` 的理由：冲突与包无关（`j$/time` 等同理），`j$` 是 desugar 保留前缀，不在生产混淆面。带成员块的理由：仅 `-keep` 不保成员会把同名错误挪到别的成员。 |
| `app/proguard-rules.pro:20-38` | `-keep class androidx.tracing.Trace { void beginSection(String); void endSection(); void forceEnableAppTracing(); }`（`:35-38`） | `NoClassDefFoundError: Failed resolution of: Landroidx/tracing/Trace;` / `ClassNotFoundException: androidx.tracing.Trace` | 该类被 AGP 排除在测试 APK 之外、又被主包 R8 内联删除 → 两个 dex 都没有该类，测试 dex 的按名 `invoke-static` 悬空；`r04-test-keep.pro` 无法"凭空造类"，唯一落点是主包保类。成员集 = 测试 dex 实测引用的恰三个静态签名；主包无调用者，保的是类存在性。 |

- 与 HEAD 的差量：`git diff --stat -- app/r04-test-keep.pro app/proguard-rules.pro` → 两文件 `+34` / `+20` 行（HEAD 版 40 / 19 行；工作树 74 / 39 行）。**未使用** internal 变体、未引入 `-dontobfuscate`/`-dontshrink`/`-dontoptimize`（K4 门与脚本口径）。
- 研究支撑：`docs/research/2026-10-30-r04-k6-keep-rules.md` §二/§三（机制）、§四（取舍）、§五（重跑判据）。

## 5. R-04 最终裁决

**裁决：R-04 不关闭；按"阻塞"登记（K6 无执行证据）。关闭建议不成立。**

1. 关闭的必要条件（判据原文照抄、不放宽）：release 轨上 K6 两例 `OK (2 tests)` + 三类崩溃签名 0 + 设备绿绑定源快照。本批**只完成到 K4**：`build/device-track-batch36/` 无 K5/K6 产物（K5 仅本会话补查了 so 双 ABI 半边；dex 计数未跑），K6 两例在批内**无执行证据**（目录产物止于 08:59 的 K2 构建/K3/K4；是否存在未落盘的 K6 运行无法证明，按无证据处理）。
2. K6 最新一次实测仍是**批次 35 红**：`build/device-track-batch35/r04-k6-logcat.txt:86`、`:92`、`:110` 三类签名在案（`j$/util/g` `NoSuchMethodError`、`androidx.tracing.Trace` `NoClassDefFoundError`/`ClassNotFoundException`），批次 35 已按阻塞登记。本批的 keep 扩充正是针对这三类签名，但**"规则写了"不等于"K6 绿了"**——R-04 判的是 release 轨设备往返，必须实跑。
3. 因此本批不能给出"关闭建议"；也不能反过来判"红"——本批没有 K6 的新红证据。准确状态：**K1–K4 绿、K5 未按判据执行、K6 无执行证据 → R-04 阻塞（UNVERIFIED）**。
4. 解除阻塞的最小动作（实现轨）：按 `docs/research/2026-10-30-r04-k6-keep-rules.md:134` 的命令在 5562 上装两包、跑 K6 两例，取 `OK (2 tests)` 与 logcat 三类签名 0；再补 K5 的 dex 计数。全部成立后才可提关闭。
5. 不得以"K1–K4 绿 + 机制分析"替代 K6 设备往返；不得为变绿删例、换 internal 变体或加 `-dontobfuscate`。

## 6. 脱敏与去噪

- 删除：apksigner 的 `WARNING: META-INF/… not protected by signature` 重复行（每份数十条）未转录；Gradle 进度行未转录。
- 保留：`BUILD SUCCESSFUL`、`K2_BUILD_EXIT`、证书 SHA-256、Permission Denial 原文、keep 门输出。
- 一次性 keystore 与口令载体在 `build/`（gitignore 内），本文件不记录路径内容以外的任何值。
- 扫描：见 `00-index.md` §7；二进制/截图：无。

## 7. 判定

PARTIAL：K1/K2/K3/K4 在扩充后规则上 PASS（红基线、release 测试包构建、证书一致、keep 门 0 命中）；K5 未按判据执行（仅 so 双 ABI 半边经本会话补查成立）；**K6 无执行证据** → R-04 不可关闭，阻塞登记（不是新红，也不是绿）。
