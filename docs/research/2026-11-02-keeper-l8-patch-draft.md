# Keeper L8 补丁草案（批次 40 / WS6 item 37，2026-11-02）

> **状态：草案，未落码、未应用、未跑 Gradle。** `settings.gradle.kts` **零编辑**（本轮 `git status --porcelain -- settings.gradle.kts` 为空）；本文只给可审 diff 与兼容性判据。
> 上游事实与裁定输入全部来自 R6 `docs/research/2026-11-02-keeper-l8.md`（全读）；本文不重复其证据链，只把 §4 的片段落成可直接 `git apply` 的补丁形态，并把 §5 的判据压成执行清单。
> 前置：R6 §9 的 D1（是否采纳 Keeper）、D3（测试 keep 案 A/B）、D4（锁窗口）**均未裁定**。任一未定，本草案不得应用。

## 0. 对象与结论

- 上游 `slackhq/Keeper 0.16.1`：AGP 9 apply 即崩（issue #152 OPEN），**否决**。
- 可行对象：`usefulness/keeper 0.19.0`（Apache-2.0；tag 以 AGP 9.3.0 + Gradle 9.6.1 构建；Maven Central `keeper-gradle-plugin-0.19.0.module` 的 `createdBy.gradle.version=9.6.1`）。
- 本仓精确组合：AGP 9.3.0 / Gradle 9.6.1 / Kotlin 2.3.21 / compileSdk 37。**本仓完整构建图 × Keeper 仍未跑过**（R6 U1）。
- 硬约束：`settings.gradle.kts:10` `RepositoriesMode.FAIL_ON_PROJECT_REPOS` ⇒ 必须 `automaticR8RepoManagement.set(false)`，否则 apply 阶段直接失败。

## 1. 补丁 A（最小接入；对应 R6 案 A，不测试 keep 收窄）

适用：只验证 Keeper 能否修掉测试 L8 碰撞，不改 `app/r04-test-keep.pro`。若 D3 选案 B，另加 §2。

### 1.1 `gradle/libs.versions.toml`

```diff
--- a/gradle/libs.versions.toml
+++ b/gradle/libs.versions.toml
@@ -21,6 +21,7 @@
 baselineprofile = "1.5.0-rc01"
 uiautomator = "2.3.0"
 profileinstaller = "1.4.0"
+keeper = "0.19.0"
 junit = "4.13.2"
 androidx-junit = "1.3.0"
@@ -124,6 +125,7 @@
 kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
 androidx-room3 = { id = "androidx.room3", version.ref = "room3" }
 android-test = { id = "com.android.test", version.ref = "agp" }
 androidx-baselineprofile = { id = "androidx.baselineprofile", version.ref = "baselineprofile" }
+keeper = { id = "io.github.usefulness.keeper", version.ref = "keeper" }
```

### 1.2 根 `build.gradle.kts`

```diff
--- a/build.gradle.kts
+++ b/build.gradle.kts
@@ -7,6 +7,7 @@
     alias(libs.plugins.ksp) apply false
     alias(libs.plugins.kover) apply false
     alias(libs.plugins.androidx.room3) apply false
     alias(libs.plugins.androidx.baselineprofile) apply false
+    alias(libs.plugins.keeper) apply false
 }
```

### 1.3 `app/build.gradle.kts`

```diff
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@ -1,4 +1,6 @@
+import com.slack.keeper.optInToKeeper
+
 plugins {
     alias(libs.plugins.android.application)
     alias(libs.plugins.compose.compiler)
     alias(libs.plugins.androidx.baselineprofile)
+    alias(libs.plugins.keeper)
 }
@@ -119,6 +121,24 @@
     packaging {
         resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
     }
 }
 
+// R-04 / Keeper：只在 -Psmbk.testBuildType=release 的 strictOfflineRelease 上 opt-in。
+// 消灭的失败：androidTest 的 L8 desugar dex 与主包短名碰撞（R-04 预检②的结构性红）。
+keeper {
+    // 本仓 settings 设了 FAIL_ON_PROJECT_REPOS（settings.gradle.kts:10）。
+    // Keeper 默认 true 会执行 project.repositories.maven { … }
+    // （InferAndroidTestKeepRules.kt:180-187），直接被 Gradle 判死；
+    // 必须关掉，默认 R8 9.1.31 从现有 google() 解析。
+    automaticR8RepoManagement.set(false)
+    // 证据面：输出 l8-diagnostics/<task>/patchedL8Rules.pro 与 classes/jars 清单。
+    emitDebugInformation.set(true)
+}
+
+androidComponents {
+    beforeVariants { builder ->
+        // 仅 strictOfflineRelease；日常 debug 轨零影响。
+        if (r04TestBuildType == "release" && builder.name == "strictOfflineRelease") {
+            builder.optInToKeeper()
+        }
+    }
+}
+
 // Populates the release signing config at evaluation time; silently skips when env vars are absent.
 fun configureReleaseSigningIfAvailable() {
```

**不加入** `keeperR8("com.android.tools:r8:9.1.31")`：插件默认值就是 9.1.31；显式坐标只会让 `app/gradle.lockfile` 多一条、需额外锁窗口，且不消灭新的失败。只有 K2 证明 Google thin jar 不可用时，才按 R6 §4.3 回退 1/2。

**不加入** `--map-diagnostics:MissingDefinitionsDiagnostic`：先让 Keeper 默认严格跑；若 K2 红在缺失类，按 R6 §5.5 归因后再决定，并记录，不得静默降级。

## 2. 补丁 B（可选；对应 R6 §4.4 案 B，收窄测试 keep）

适用：D3 选案 B。Keeper `KeeperPlugin.kt:287-293` 会把 `testVariant.proguardFiles` 追加进**主包** R8；本仓 `app/r04-test-keep.pro:22-29` 的广域 `-keep class com.tingyun.smartmistakebook.**` 会让 R-04 实验变体的 app 类整体保名。案 B 收窄为"只保含 `@Test` 的类"。

```diff
--- a/app/r04-test-keep.pro
+++ b/app/r04-test-keep.pro
@@ -22,13 +22,16 @@
 # 1) Instrumentation loads test classes reflectively by name
 #    (`am instrument -e class <fqcn>#<method>`), so test class names must survive.
--keep class com.tingyun.smartmistakebook.** {
+# 案 B：只保"有构造器 + 至少一个 @Test 方法"的类；app 主类没有 @Test，不会命中。
+-keepclasseswithmembers class com.tingyun.smartmistakebook.** {
     <init>(...);
     @org.junit.Test <methods>;
-    @org.junit.Before <methods>;
-    @org.junit.After <methods>;
-    @org.junit.BeforeClass <methods>;
-    @org.junit.AfterClass <methods>;
 }
+# 生命周期方法按注解保（类未被上一条保留时自然无效果）。
+-keepclassmembers class * {
+    @org.junit.Before <methods>;
+    @org.junit.After <methods>;
+    @org.junit.BeforeClass <methods>;
+    @org.junit.AfterClass <methods>;
+}
```

验证：K5b（测试类名在 androidTest dex 未改名）仍过；K6 两例仍绿；主包 `configuration.txt` 不再出现广域 `-keep class com.tingyun.smartmistakebook.**`。案 B **不是放宽门**（它加强主包混淆面），但改变了测试 keep 语义，须 D3 裁定。

## 3. 兼容性判据（应用前后必须逐条记录）

### 3.1 前置窗口 W1–W7（R6 §5.1）

| # | 条件 | 本轮实测 |
|---|---|---|
| W1 | 主会话裁定 R-04 关闭路径 = 选项 A | **未裁定** |
| W2 | 触面文件干净 | ✅ 本轮 `git status --porcelain -- gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/r04-test-keep.pro app/proguard-rules.pro` 为空 |
| W3 | 干净待提交树（KD-72 四条） | ❌ 564 条（131 M + 433 ??）；见 `docs/research/2026-11-02-kd72-lock-window.md` |
| W4 | `settings.gradle.kts` 零编辑 | ✅ 本轮为空 |
| W5 | 钉版 + 字节登记 + pin 门扩展 | 部分：钉版在本文；字节见 R6 §4.5；`check_dependency_pins.py` 不覆盖 keeper/R8 |
| W6 | mapping 门 v2 裁定 | **未裁定**；旧 ② 硬门会误杀 |
| W7 | 测试 keep 案 A/B 裁定 | **未裁定** |

### 3.2 K1–K6（不放宽，R6 §5.2 原文）

| # | 判据 | 期望 |
|---|---|---|
| K1 | 步 1 红基线确实红，红因是 R8 改名/签名门 | 红 |
| K2 | 步 2 `BUILD SUCCESSFUL` 且两个 release 产物都在 | 绿 |
| K3 | 两份 APK 证书 SHA-256 相同 | 绿 |
| K4 | `-dontobfuscate`（含 shrink/optimize）两文件 0，release mapping 有真实改名 | 绿 |
| K5 | `libquickjs.so` 在包；测试类名在 androidTest dex 未被改名 | 绿 |
| K6 | 两例 `OK (2 tests)`，logcat 三类签名 0 | 绿 |

K2 必须在仓库默认 **configuration cache 开启**（`gradle.properties:2`）下跑；不得用 `--no-configuration-cache` 掩盖插件 CC 问题。

### 3.3 L8 合并后的 mapping 门 v2（R6 §5.3；须 D2 裁定）

| 预检 | v1 | v2（建议） | Keeper 后期望 |
|---|---|---|---|
| ① | 主 mapping 含 `DesugarCollections$SynchronizedMap -> j$.util.g` | 主 dex 有该类定义（原名或映射目标）；mapping 短名仅诊断 | 结构过 |
| ② | 测试 mapping ` -> j$.util.g:` = 0 | 测试 APK `classes*.dex` 的 `Lj$/…` **定义数 = 0**；旧 grep 降为诊断 | 0 |
| ③ | 主包 dex 含 `androidx/tracing/Trace` | 不变 | ≥1 |
| ④ 新增 | — | 主/测 dex 类描述符交集 = 0 | 0 |
| ⑤ 新增 | — | `desugar_lib_dex/strictOfflineReleaseAndroidTest/**/*.dex` 无正尺寸文件 | 空 |

首次 K2 前必须清 `app/build/intermediates/desugar_lib_dex/strictOfflineReleaseAndroidTest` 或 `--rerun-tasks`，否则 `L8DexDesugarLibTask` UP-TO-DATE 会让 `doLast` 不执行、旧 dex 残留（R6 §5.3.3）。

## 4. 失败归因（应用后）

按 R6 §5.5 表逐项归因：`AppExtension` 崩 → 用错上游；`FAIL_ON_PROJECT_REPOS` → `automaticR8RepoManagement` 未关；R8 运行期缺类 → thin jar 不自足，走 §4.3 回退；K6 仍 `NoSuchMethodError j$/util/g` → 测试 L8 dex 未清空（⑤ 非空）或 opt-in 未生效；`Duplicate mapping` → AGP 9.3.0 已知 #522844908/#554586985，本批不升 AGP。**不得**用 `-dontobfuscate`/`-dontshrink`/`-dontoptimize`/删例/换 internal 变体。

## 5. 回滚（若 K2/K6 无法归因到本批）

1. 还原 `gradle/libs.versions.toml`、根 `build.gradle.kts`、`app/build.gradle.kts`。
2. `app/r04-test-keep.pro`、`app/proguard-rules.pro` 保留不删；若案 B 已落，按 D3 裁定回退到案 A 或保留收窄版，**不得**回滚成"无规则"。
3. 锁：不要手改；KD-72 窗口满足后按干净树重生成。
4. 清 `app/build/intermediates/keeper/` 与 `app/build/intermediates/desugar_lib_dex/strictOfflineReleaseAndroidTest/`，避免 build cache 复用。
5. 核验：`git diff HEAD --stat -- gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts` 为空；`python tools/ci/check_r04_keep_rules.py` exit 0；测试 APK 重新带 L8 dex（j$ 定义 >0）。

## 6. 来源

| 类型 | 来源 | 用途 |
|---|---|---|
| 研究 | `docs/research/2026-11-02-keeper-l8.md:9-18,104-168,170-268,270-308,321-520` | 上游事实、机制对位、补丁草案、判据、替代方案 |
| 插件源码 | `usefulness/keeper@v0.19.0` `KeeperPlugin.kt`：`:84,93,131,178,210,260-293`；`InferAndroidTestKeepRules.kt:180-187`；`KeeperVariantMarker.kt:16,33,36`（本轮 `gh api` 实取） | `optInToKeeper` 包名、`automaticR8RepoManagement`、`emitDebugInformation`、testProguardFiles 传播 |
| 本仓 | `settings.gradle.kts:1-12`；`gradle/libs.versions.toml:21-24,124-127`；`build.gradle.kts:1-11`；`app/build.gradle.kts:1-5,12,65,98,121`；`app/r04-test-keep.pro:22-29`；`app/proguard-rules.pro:35-39`；`gradle.properties:2` | 补丁锚点与热区/仓库模式 |
| 本轮实跑 | `git status --porcelain -- <触面>`（空）；`gh api` 取 `KeeperVariantMarker.kt` 与 `KeeperPlugin.kt` | W2/W4 与 API 签名 |
| 锁窗口 | `docs/research/2026-11-02-kd72-lock-window.md` | W3 / D4 |
