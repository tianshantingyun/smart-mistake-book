# R8 minify 下的 WorkManager / Room keep 规则与 KD-69 根因

- 日期：2026-10-26
- 研究线：R4（R8 与 AndroidX 构建研究）
- 驱动任务：T5（KD-69 debug minify R8 修复）
- 计划：`.zcode/plans/plan-2026-10-26-batch-22-visualjs-process.md`
- 证据等级：根因由**本机构建产物实证**（批次 21 的 `app/build/outputs/mapping/**`，mtime 2026-10-26 03:07–03:12）；官方网页文档本环境不可达，关键文本经 3 个独立镜像交叉核对并标注；**本轮未运行任何 Gradle / 设备命令**（纪律禁止），修复后验收命令见 §8
- 本文未改动任何源码或构建文件；落点仅有本文件

## 0. 结论摘要

1. **官方 consumer rules 实际带了什么**（逐字来自 AAR，§2）：`androidx.room:room-runtime:2.6.1` 只带一条**无成员限定**的 `-keep class * extends androidx.room.RoomDatabase`；`androidx.work:work-runtime:2.10.5` 只保 Worker / InputMerger / WorkerParameters，**不保 Room 数据库，也不保 WorkDatabase_Impl**（它把这件事留给 Room）。Room **2.7.0 起**把规则改成 `-keep class * extends androidx.room.RoomDatabase { void <init>(); }`，Room3 3.0.0 自带 `{ void <init>(); }`。
2. **为什么仍 `NoSuchMethodException`**（§3）：AGP 9.3.0 的 `android.r8.strictFullModeForKeepRules` 默认 **true**（字节码实证），R8 9.3.16 在此模式下**不再为被 keep 的类隐式保留无参构造器**（R8 官方 compatibility FAQ 明文）。Room 2.6.1 的无成员规则只保住了类和类名，`WorkDatabase_Impl.<init>()` 无人引用 → 被裁 → `androidx.startup` 启动期 `Room.getGeneratedImplementation` 反射实例化失败。KD-69 记录的归因需要一处措辞修正（§9）：
   - 错的说法：`internal/benchmark` 没有 `proguardFiles` ⇒ 规则全没生效、类被剥掉；
   - 实测：**consumer rules 照常生效**（`configuration.txt:1025-1026` 有 Room 规则），被剥掉的是**无参构造器**；真正缺的是本项目 `app/proguard-rules.pro` 的两组显式 keep。
3. **本项目三个 buildType 的差异**（§4）：三者都 `isMinifyEnabled = true`；只有 `release` 声明了 `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`。AGP 9.3.0 会把默认 optimize 规则**全局注入**（`configuration.txt:1`），所以唯一实际差别就是项目规则文件；`matchingFallbacks += listOf("release")` **不**继承 `proguardFiles`。`internal` 与 `release` 的 R8 规则差集里，release 多出的非注释行恰好是 `app/proguard-rules.pro` 的 4 条规则（6 行）。
4. **标准修法**（§5）：给 `benchmark` / `internal` 补同一行 `proguardFiles(...)`（与 release 对齐），**不要**关 minify、**不要**关闭 strict full mode（AGP 10 弃用 / 11 移除）、**不要**去改 Room 依赖版本（T5 之外、归 R2）。release 的 A/B 已证明这组规则能保住构造器。
5. **debug minified 冒烟**（§7）：必须覆盖 ① 冷启动（WorkManager DB 反射构造）、② 应用 Room3 DB 首开、③ **ML Kit 三个 registrar 的惰性反射构造**（`internal` 实测同样被裁，是最可能的下一个崩溃点）、④ 一次 Worker 实际执行；构建期再加 `usage.txt` 断言，比设备冒烟更快定位。

## 1. 证据面与方法

### 1.1 工具链（本机实测）

| 项 | 值 | 出处 |
|---|---|---|
| AGP | 9.3.0 | `gradle/libs.versions.toml:2`；`configuration.txt:1` 记为 “Android Gradle plugin 9.3.0” |
| R8 | 9.3.16 | `app/build/outputs/mapping/localFirstInternal/mapping.txt:2`（`# compiler_version: 9.3.16`） |
| WorkManager | 2.10.5 | `gradle/libs.versions.toml:59` |
| Room（WorkManager 传递依赖） | androidx.room 2.6.1 | `work-runtime-2.10.5.pom:122-125`（`androidx.room:room-ktx:2.6.1`） |
| Room（项目自有 DB） | androidx.room3 3.0.0 | `gradle/libs.versions.toml:12,72` |
| strict full mode flag | 未在 `gradle.properties` 出现 | 通读 `gradle.properties`（全文 12 行），无 `android.r8.strictFullModeForKeepRules` / `android.enableR8.fullMode` |

批次 21 产物（本轮只读，未重跑）：

- `app/build/outputs/mapping/localFirstInternal/{configuration,seeds,usage,mapping}.txt`（03:07–03:08）
- `app/build/outputs/mapping/localFirstRelease/{configuration,seeds,usage,mapping}.txt`（03:10–03:12）
- `build/device-track-batch21/step5-r8-logcat.txt:372-410`（崩溃栈）
- `build/device-track-batch21/step5-r8-release-logcat.txt`（release 冒烟，523 行，0 FATAL —— 引批次 21 记录 §5）

### 1.2 本轮实际执行过的命令（可复现）

```bash
# 1) 直接读本机 Gradle 缓存里的 AAR consumer rules
unzip -p "$HOME/.gradle/caches/modules-2/files-2.1/androidx.room/room-runtime/2.6.1/3ebcd7683411c298dd8aeee635f452c96eaf319/room-runtime-2.6.1.aar" proguard.txt
unzip -p "$HOME/.gradle/caches/modules-2/files-2.1/androidx.work/work-runtime/2.10.5/af9c119ae91eb961d08a1d2809851e9a0b9abd90/work-runtime-2.10.5.aar" proguard.txt
unzip -p "$HOME/.gradle/caches/modules-2/files-2.1/androidx.room3/room3-runtime-android/3.0.0/5b34acf1a770ab2ee96c9d6c8a13f229522af0c7/room3-runtime.aar" proguard.txt

# 2) 对照上游：Room 2.7.0（KMP 后 AAR 名为 room-runtime-android）
curl -sO https://dl.google.com/dl/android/maven2/androidx/room/room-runtime-android/2.7.0/room-runtime-android-2.7.0.aar
unzip -p room-runtime-android-2.7.0.aar proguard.txt

# 3) 读 R8 实际行为（不是读配置，而是读 R8 输出）
grep -n "androidx.room.RoomDatabase" app/build/outputs/mapping/localFirstInternal/configuration.txt
grep -A6 "^androidx.work.impl.WorkDatabase_Impl:" app/build/outputs/mapping/localFirstInternal/usage.txt
grep -A5 "^androidx.work.impl.WorkDatabase_Impl:" app/build/outputs/mapping/localFirstRelease/usage.txt

# 4) 读 AGP 9.3.0 的 flag 默认值（字节码）
AGP_JAR="$HOME/.gradle/caches/modules-2/files-2.1/com.android.tools.build/gradle/9.3.0/453e47e6c26057ebce81709717308acecf26c5b3/gradle-9.3.0.jar"
unzip -o -q "$AGP_JAR" com/android/build/gradle/options/BooleanOption.class -d /tmp/agp2
javap -c -p /tmp/agp2/com/android/build/gradle/options/BooleanOption.class | grep -A12 "R8_STRICT_FULL_MODE_FOR_KEEP_RULES"

# 5) 读 Room3 上游 keep 规则源码
gh api repos/androidx/androidx/contents/room3/room3-runtime/src/androidMain/keepRules/rules.keep --jq .content | base64 -d
```

### 1.3 证据可靠性声明

- `configuration.txt` 是 AGP 合并后交给 R8 的**规则清单**，用于证明“某条规则是否被应用”。
- `usage.txt` / `mapping.txt` 是 **R8 实际执行结果**，用于证明“某个成员是否被裁 / 类名是否保留”。
- 注意本批实测到的一个坑：`localFirstRelease/configuration.txt` **没有** AAPT2 段（`grep -c "{ <init>(); }"`：internal 33 / release 3），但 release 的 `aapt_rules.txt` 存在（`aapt_proguard_file/localFirstRelease/.../aapt_rules.txt:6,28`）且 release 的 `MainActivity` 构造器确实被保留（`mapping.txt:482738+` 有 `void <init>()`，`usage.txt:99897` 未列 `<init>`）。结论：**验收断言以 `usage.txt` / `mapping.txt` 为准**，`configuration.txt` 只作辅助；该 dump 不对称的成因未查清（见 §10 UNVERIFIED）。

## 2. 官方 consumer rules 到底带了什么（逐字）

### 2.1 androidx.room:room-runtime:2.6.1（WorkManager 实际使用的版本）

`room-runtime-2.6.1.aar!/proguard.txt` 全文：

```
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-dontwarn androidx.lifecycle.LiveData
```

**没有成员限定**。这正是 R8 strict full mode 下不够用的形态（§3）。

该规则在本批 `internal` 构建中确实被应用（含 provenance 注释）：

- `app/build/outputs/mapping/localFirstInternal/configuration.txt:1025` — `# The proguard configuration file for the following section is androidx.room:room-runtime:2.6.1 (extracted file: ...\transformed\room-runtime-2.6.1\proguard.txt)`
- `...:1026` — `-keep class * extends androidx.room.RoomDatabase`

### 2.2 androidx.room:room-runtime-android:2.7.0（上游已修）

`room-runtime-android-2.7.0.aar!/proguard.txt` 全文首行：

```
-keep class * extends androidx.room.RoomDatabase { void <init>(); }
```

**上游自 2.7.0 起显式保留无参构造器**。这是“2.6.1 的规则本身不足以在 R8 新模式下保住构造器”的最直接旁证。

### 2.3 androidx.room3:room3-runtime:3.0.0（项目自有 DB 使用）

AAR 与 androidx-main 源码一致：

```
-keep class * extends androidx.room3.RoomDatabase { void <init>(); }
-dontwarn androidx.room3.paging.**
-dontwarn androidx.lifecycle.LiveData
```

- AAR：`androidx.room3/room3-runtime-android/3.0.0/.../room3-runtime.aar!/proguard.txt`
- 源码：<https://github.com/androidx/androidx/blob/androidx-main/room3/room3-runtime/src/androidMain/keepRules/rules.keep>

**后果**：项目自己的 `StudyDatabase_Impl`（`core/database/.../StudyDatabase.kt:143` 的 `@Database`）**没有**被裁构造器 —— `localFirstInternal/usage.txt:55090-55092` 只列了 `clearAllTables` 与一个 DAO getter，**没有 `<init>()`**。所以本批崩溃只可能来自 `androidx.room`（2.6.1）那一支，而不是项目的 room3 DB。

### 2.4 androidx.work:work-runtime:2.10.5

`work-runtime-2.10.5.aar!/proguard.txt` 全文：

```
# Keep ListenableWorker / Worker if not removed during shrinking
-keepnames class * extends androidx.work.ListenableWorker
# Keep all constructors on ListenableWorker, Worker if class is kept
-keepclassmembers public class * extends androidx.work.ListenableWorker {
    public <init>(...);
}

# We need to keep WorkerParameters for the ListenableWorker constructor that is used by the
# default instantiation strategy in WorkerFactory.
-keep class androidx.work.WorkerParameters

# Keep InputMerger if not removed during shrinking
-keepnames class * extends androidx.work.InputMerger
# Keep constructor on InputMerger if class is kept
-keepclassmembers class * extends androidx.work.InputMerger { void <init>(); }
```

要点：

- WorkManager **不**为自己的 `WorkDatabase_Impl` 提供任何 keep；它依赖 Room 的 consumer rule。Room 2.6.1 的规则在 strict full mode 下不足 → 这就是全局性的三方库组合缺陷，不是本项目独有（§3.5 有同型 issue）。
- 本项目自己的 Worker 构造器被保住了：`localFirstInternal/usage.txt:51434-51435` 的 `OrphanAssetGcWorker` 只列了 `MAX_ATTEMPTS`，未列 `<init>`。`ListenableWorker` 的 ctor keep 生效。
- 注意 `-keep class androidx.work.WorkerParameters` 同样是无成员限定；它没有无参构造器（`javap` 实测：只有带参数的构造器，由 WorkManager 内部直接调用），所以 strict full mode 不会影响它。

### 2.5 AGP 自己生成的 manifest keep（对照组）

`localFirstInternal/configuration.txt:1243-1318` 是 AAPT2 生成的规则段，**对本批列出的组件都带 `{ <init>(); }`**：

```
-keep class androidx.startup.InitializationProvider { <init>(); }      # :1249
-keep class com.tingyun.smartmistakebook.MainActivity { <init>(); }   # :1271
...
```

这解释了为什么 manifest 里声明的组件（Activity / Service / Receiver / Provider）不会因 strict full mode 而丢构造器 —— 也反衬出“只靠 `-keep class X`（无成员）保反射类”在 AGP 9 下是不安全的写法。

## 3. 为什么 `WorkDatabase_Impl.<init>` 会被裁掉（根因链）

### 3.1 事实链（每一步都有本机证据）

1. WorkManager 2.10.5 的 POM 把 `androidx.room:room-ktx:2.6.1` 带进运行时（`work-runtime-2.10.5.pom:122-125`）→ 类路径上同时存在 `androidx.room`（2.6.1，WorkManager 用）与 `androidx.room3`（3.0.0，项目用）。
2. `WorkDatabase_Impl` 的继承链（`javap` 实测 WorkManager AAR）：
   `androidx.work.impl.WorkDatabase_Impl extends androidx.work.impl.WorkDatabase extends androidx.room.RoomDatabase`，且有 `public androidx.work.impl.WorkDatabase_Impl()`。
3. Room 2.6.1 的 consumer rule（无成员限定）在本批 `internal` 中**确实生效**（`configuration.txt:1025-1026`）；它保住了类与类名（`mapping.txt:260719` 显示未改名），但**不等于**保住成员。
4. R8 在 strict full mode 下**不再为被 keep 的类隐式保留无参构造器**：
   - R8 官方 compatibility FAQ（full mode 差异列表）原文：
     > “The default constructor (`<init>()`) is not implicitly kept when a class is kept.”
     >
     > “Classes that are only instantiated using reflection should be kept explicitly with a `-keep` rule.”
   - 同一列表还有：“The default constructor (`<init>()`) is not implicitly kept for types which are only used with `ldc`, `instanceof` or `checkcast`.”
   - 对照 ProGuard 语义（Guardsquare 手册 `-keep` 节，WebFetch 原文）：
     > “If you specify a class, without class members, ProGuard only preserves the class and its parameterless constructor as entry points. It may still remove, optimize, or obfuscate its other class members.”
     这正是 Room 2.6.1 规则曾经够用的原因，也是 R8 full mode 与 ProGuard 的分歧点。
5. AGP 9.3.0 把该行为设为默认：`BooleanOption.R8_STRICT_FULL_MODE_FOR_KEEP_RULES` 的字节码（`gradle-9.3.0.jar!/com/android/build/gradle/options/BooleanOption.class`，`javap -c`）为
   `"R8_STRICT_FULL_MODE_FOR_KEEP_RULES"` / ordinal 30 / `"android.r8.strictFullModeForKeepRules"` / `iconst_1`（**默认 true**）/ `FutureStage VERSION_10_0` / `SoftlyEnforced(DeprecationTarget VERSION_11_0)`。
   版本语义按字节码标注并与 Zeus issue 的描述一致（AGP 9 默认开启、AGP 10 弃用、AGP 11 移除）；官方发布说明本环境不可达（§10-3）。
6. `WorkDatabase_Impl` 的无参构造器没有任何直接调用点（只被 Room 的 `Class.getDeclaredConstructor` 反射调用）→ 被裁。R8 自己把它写进 `usage.txt`：
   - `localFirstInternal/usage.txt:43128` — `androidx.work.impl.WorkDatabase_Impl:`
   - `...:43129` — `private volatile ... _rawWorkInfoDao`
   - `...:43130` — **`public void <init>()`** ← 被移除
   - `...:43131` — `public void clearAllTables()`
7. 运行期崩溃：`androidx.startup.InitializationProvider` → `WorkManagerInitializer` → `WorkManagerImpl.initialize` → `WorkDatabase.create` → `RoomDatabase.Builder.build` → `Room.getGeneratedImplementation` → `Class.getDeclaredConstructor()` → `NoSuchMethodException`。完整栈见 `build/device-track-batch21/step5-r8-logcat.txt:372-410`：

```
java.lang.RuntimeException: Unable to get provider androidx.startup.InitializationProvider: androidx.startup.StartupException: java.lang.NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []
  ...
  at androidx.room.Room.getGeneratedImplementation(Room.kt:56)
  at androidx.room.RoomDatabase$Builder.build(RoomDatabase.kt:1351)
  at androidx.work.impl.WorkDatabase$Companion.create(WorkDatabase.kt:165)
```

### 3.2 为什么 `release` 没崩（同批次 A/B）

`app/proguard-rules.pro:8-10` 本来就有一条显式规则：

```proguard
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
```

- release 的 R8 规则清单里有它：`localFirstRelease/configuration.txt:101`（同段还有 `:1048` 的 Room consumer rule）。
- release 的 `usage.txt:45476-45479` 中 `WorkDatabase_Impl` **没有** `<init>()`（只有 `_rawWorkInfoDao`、`clearAllTables`、`rawWorkInfoDao` 被裁）→ 构造器被保住。
- release 装机冒烟 523 行 logcat，FATAL/ANR/ClassNotFound/NoSuchMethod 全 0（批次 21 记录 §5）。

**这就是“同一份 consumer rule、两种结果”的受控对照**：差别只在有没有本项目那 4 条规则。

### 3.3 为什么 `internal` / `benchmark` 没拿到那 4 条规则

- `app/build.gradle.kts:58-64`（benchmark）与 `:65-70`（internal）只写了 `isMinifyEnabled = true`，**没有 `proguardFiles`**；只有 `:71-79`（release）在 `:78` 写了。
- `matchingFallbacks += listOf("release")`（`:59`、`:66`）**不会**继承 `proguardFiles`。实测：`localFirstInternal/configuration.txt` 里没有 `app/proguard-rules.pro` 的任何一行（`grep CommonComponentRegistrar` 为空）。
- 两个变体的规则差集（`diff` 后只看 release 独有、去注释）恰好是 6 行：

```text
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

-keep class com.google.mlkit.common.internal.CommonComponentRegistrar { <init>(); }
-keep class com.google.mlkit.vision.text.internal.TextRegistrar { <init>(); }
-keep class com.google.mlkit.vision.common.internal.VisionCommonRegistrar { <init>(); }
```

### 3.4 第二个（尚未爆的）缺口：ML Kit registrar

`internal` 同样把三个 registrar 的无参构造器裁掉了：

- `localFirstInternal/usage.txt:50717-50718` — `com.google.mlkit.common.internal.CommonComponentRegistrar:` / `public void <init>()`
- `...:50955-50956` — `com.google.mlkit.vision.common.internal.VisionCommonRegistrar:` / `public void <init>()`
- `...:51010-51011` — `com.google.mlkit.vision.text.internal.TextRegistrar:` / `public void <init>()`

release 保留了它们（`localFirstRelease/usage.txt` 中三者只列静态字段，无 `<init>`），因为 `app/proguard-rules.pro:17-19` 显式 keep。它们由 ComponentDiscovery 反射实例化、且是**惰性**的（首次中文 OCR 才触发），所以批次 21 的启动崩溃先发生在 WorkManager 上；**只修 WorkManager 这一条 keep 的话，下一次 OCR 会以同样形状崩**。T5 的补丁必须把整个 `proguard-rules.pro` 带进 internal/benchmark，而不是只补 Room 一条。

### 3.5 上游/社区的独立佐证（非官方，仅作交叉印证）

- `expo/expo#51337`（“SDK 58 release builds crash on launch: R8 strips WorkDatabase_Impl constructor”）：明确描述 “AGP 9, where R8 runs in strict full mode: a plain `-keep class X` rule no longer keeps the class's no-arg constructor”，并指出 Room 2.5.0 的规则无成员限定、`dexdump` 显示 `WorkDatabase_Impl` 无 `<init>`。与本案同一形状。
- `ZeusLN/zeus#4783`（“replace `android.r8.strictFullModeForKeepRules=false` with explicit keep rules before AGP 11”）：列出同型依赖（room-runtime 2.5.0、firebase-components 的 `ComponentRegistrar`、work-runtime 2.8.1 的 `InputMerger`），并说明该 flag “AGP 9 默认 true，AGP 10 弃用，AGP 11 移除”。与 §3.1-5 的字节码一致。
- `sAleksovski/react-native-android-widget#159`：同题崩溃报告。

以上为第三方仓库 issue，**不是** Google/AndroidX 官方文档；官方 compatibility FAQ 的规范 URL 见 §11，本环境不可达，文本经 3 个独立镜像逐字一致核对。

## 4. 本项目三个 buildType 的 minify / proguardFiles 差异

来源：`app/build.gradle.kts:57-80`（当前树，git 干净）。

| 变体 | minify | shrinkResources | debuggable | profileable | 签名 | proguardFiles | 实际结果 |
|---|---|---|---|---|---|---|---|
| `benchmark`（`:58-64`） | true（`:63`） | 未设 | false（`:60`） | true（`:61`） | debug（`:62`） | **无** | 规则=AGP 全局默认 + AAR consumer rules；缺项目规则 → 会复现 KD-69 |
| `internal`（`:65-70`） | true（`:69`） | 未设 | true（`:67`） | 未设 | debug（`:68`） | **无** | 同上（批次 21 实测 FATAL） |
| `release`（`:71-79`） | true（`:76`） | true（`:77`） | false（默认） | — | release（`:75`） | `:78` 默认 optimize + `proguard-rules.pro` | 正常（构造器保住） |
| `debug` | 未声明 → 不 minify | — | true | — | debug | — | 不适用 R8 |

补充事实：

1. **AGP 9.3.0 对 minify 变体全局注入 `proguard-android-optimize.txt`**：`localFirstInternal/configuration.txt:1` 的 provenance 写着 `Android Gradle plugin 9.3.0 (extracted file: ...\intermediates\default_proguard_files\global\proguard-android-optimize.txt-9.3.0)`，且 internal 里已含 native methods / Activity onClick / JavascriptInterface 等默认规则（`:31-57` 等）。所以 release 显式写 `getDefaultProguardFile(...)` 在当前 AGP 下是**与全局默认去重后的等价物**，真正起作用的是第二个参数 `"proguard-rules.pro"`。
2. **`internal` 独有 76 行**（diff 计数含注释；主体是 AAPT2 manifest keep 段 `:1243-1318`），release 的 `configuration.txt` 里这段缺失但 `aapt_rules.txt` 与实际 R8 输出都表明它生效（见 §1.3）。做规则对比时不要把它误读成“release 少规则”。
3. `internal` 与 `release` 的代码 shrink 行为在补丁后应对齐；`isShrinkResources` 仍只有 release 有，所以 internal 冒烟**不覆盖资源裁剪**。

## 5. 标准修法与候选裁定

| 方案 | 裁定 | 理由（指认它消灭的具体失败 / 为何拒绝） |
|---|---|---|
| A. 给 `benchmark`、`internal` 补 `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")` | **采纳** | 消灭 KD-69 的两个具体失败：`WorkDatabase_Impl.<init>` 被裁（启动 FATAL）与三个 ML Kit registrar `<init>` 被裁（OCR 崩溃）。与 release 规则集对齐，AGP 11 移除 flag 后仍然正确。 |
| B. 关闭 `internal`/`benchmark` 的 minify | 拒绝 | 消灭的是“红”，不是失败：minified 构建冒烟本身是 T5 的目的；关掉后 R8 缺陷只能在 release 上发现。 |
| C. `android.r8.strictFullModeForKeepRules=false` | 拒绝 | AGP 9 默认 true、AGP 10 弃用、AGP 11 移除（字节码 + Zeus issue）；只是把问题推迟到升级时，且会掩盖所有同形的三方库规则缺陷。 |
| D. 强制/升级 `androidx.room` 到 2.7+（让上游规则带 `<init>()`） | 拒绝（T5 范围外） | 依赖决策归 R2；WorkManager 2.10.5 POM 钉 room-ktx 2.6.1，强行 constraint 可能改变 WorkManager 自身 DB 行为，需独立验证。显式 keep 已足够且更小。 |
| E. 只在 `proguard-rules.pro` 再加一条 Room keep | 拒绝 | 规则已存在（`:8-10`），问题是该文件没被应用到 internal/benchmark；加规则不改构建配置等于没修。 |
| F. 把 `-keepclassmembers` 改成 `-keep class * extends androidx.room.RoomDatabase { <init>(); }` | 备选（不必须） | 现有写法依赖 Room consumer rule 先保住类；改成 `-keep` 可自足，但会扩大 keep 面、且当前 release 已验证有效，T5 保持最小改动。 |

## 6. T5 最小补丁（行级）

只改 `app/build.gradle.kts`，插入两行（缩进 12 空格，与 blocks 对齐）：

```diff
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@ buildTypes @@
         create("benchmark") {
             matchingFallbacks += listOf("release")
             isDebuggable = false
             isProfileable = true
             signingConfig = signingConfigs.getByName("debug")
             isMinifyEnabled = true
+            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
         }
         create("internal") {
             matchingFallbacks += listOf("release")
             isDebuggable = true
             signingConfig = signingConfigs.getByName("debug")
             isMinifyEnabled = true
+            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
         }
         release {
```

说明：

- 落点行号：`benchmark` 的 `isMinifyEnabled` 在 `app/build.gradle.kts:63`，新增行插在其后（新 `:64`）；`internal` 的 `isMinifyEnabled` 在 `:69`，新增行插在其后（新 `:70` 起，release 整体下移 2 行）。
- **绝对最小**变体：只写 `proguardFiles("proguard-rules.pro")`。在 AGP 9.3.0 下效果相同（默认 optimize 已全局注入，§4-1）；但保留 `getDefaultProguardFile(...)` 与 release 写法一致，且在“AGP 以后不再全局注入”的假设下仍正确。二选一即可，不要同时写两个调用。
- 不要顺手加 `isShrinkResources`、不要改 `matchingFallbacks`、不要动 flavor/签名 —— 都不指向 KD-69 的失败。
- `app/proguard-rules.pro` **不需要改**：`-keepclassmembers ... { <init>(); }`（`:8-10`）与三个 ML Kit keep（`:17-19`）在 release 上已被证明有效。可选（非必需）在注释里补一句“AGP 9 strict full mode 下 `-keep class` 不隐式保无参构造器”，避免后人误删。

## 7. debug minified 冒烟应测什么

`internal` 是本项目唯一“可调试 + minify”的变体，就是计划里说的 debug minified。冒烟分两层：

### 7.1 构建期静态断言（先跑，成本最低、定位最准）

| # | 断言 | 命令 | 修前（批次 21） | 修后期望 |
|---|---|---|---|---|
| S1 | 项目规则已进入 internal 规则集 | `grep -n "keepclassmembers class \* extends androidx.room.RoomDatabase" app/build/outputs/mapping/localFirstInternal/configuration.txt` | 无 | 有（行号不定） |
| S2 | WorkManager DB 构造器未被裁 | `grep -A6 "^androidx.work.impl.WorkDatabase_Impl:" app/build/outputs/mapping/localFirstInternal/usage.txt` | 含 `public void <init>()` | **不含** `<init>()` |
| S3 | 三个 ML Kit registrar 构造器未被裁 | 对 `CommonComponentRegistrar` / `VisionCommonRegistrar` / `TextRegistrar` 各跑 `grep -A2 "^<fqcn>:" .../usage.txt` | 各含 `public void <init>()` | **不含** `<init>()` |
| S4 | Room 反射所需类名未被混淆 | `grep -n "^androidx.work.impl.WorkDatabase_Impl ->" .../mapping.txt` | 原名为 `...WorkDatabase_Impl` | 同左（不得改名） |

### 7.2 设备运行期冒烟（覆盖静态断言抓不到的反射路径）

| # | 场景 | 为什么必须测（对应哪条失败） | 通过判据 |
|---|---|---|---|
| D1 | 冷启动到首帧（`am start` 后等 ≥10s） | WorkManager 在 `androidx.startup` 里立即建 `WorkDatabase`（KD-69 本体） | 进程仍存活；logcat 无 `FATAL EXCEPTION` / `StartupException` / `NoSuchMethodException` / `ClassNotFoundException` / `NoClassDefFoundError` |
| D2 | 杀掉进程再冷启动一次 | `Room` 数据库重新打开（DB 文件已存在路径） | 同 D1 |
| D3 | 打开依赖 Room3 的页面并触发一次查询（如复习/清单） | 项目自有 `StudyDatabase_Impl` 的构造器 + schema | 页面有数据、无 `IllegalStateException`/`Room` 异常 |
| D4 | 触发一次中文 OCR（拍照/导入一张已知含字图片） | 三个 ML Kit registrar 的**惰性**反射构造（§3.4 的下一颗雷） | 返回非空识别文本；logcat 无 `MlKitException` / `NoSuchMethodException` |
| D5 | 触发一次真实 Worker（如导出 PDF 或导入批处理） | `ListenableWorker` 构造器 + WorkDatabase 读写 + Worker 类名反射实例化 | Worker 完成（WorkInfo SUCCEEDED）；日志无 `WorkerFactory`/实例化错误 |
| D6 | 检查 WorkManager 初始化日志 | 确认初始化走的是真实 DB 而非降级 | `WM-WrkMgrInitializer: Initializing WorkManager with default configuration.` 之后无异常 |

注意事项：

- `internal` 的 `isShrinkResources=false`，**不覆盖**资源裁剪；资源裁剪继续由 release 冒烟覆盖（批次 21 已绿）。
- 合并清单（`app/build/intermediates/merged_manifest/localFirstInternal/processLocalFirstInternalMainManifest/AndroidManifest.xml:296-314`）里的 5 个 startup initializer 中，仅 WorkManager 的 DB 路径被破坏；其余 4 个的构造器实测都在（internal `usage.txt` 中 `EmojiCompatInitializer`/`ProfileInstallerInitializer` 只列静态字段；另三个不在 `usage.txt`），所以修好 D1 后不应再有下一个启动期崩溃 —— 但 D4 的 ML Kit 是**必测**，它不是 startup provider，D1 抓不到。
- 全量 351 个无参构造器在 internal 被裁（`grep -c "^    public void <init>()" usage.txt` = 351）；其中反射可达的已知项就是上述 Room DB + 3 个 registrar。修复后可用同一命令复查，并交叉比对合并清单与反射调用点，确认没有第三个雷。

## 8. 验证命令（给 T5 / 门禁，含修前诊断与修后验收）

> 遵守本批纪律：**本轮研究未执行任何 Gradle 命令**；以下命令由 T5 在串行门里执行。

### 8.1 修前诊断（当前树即可跑，不改代码）

```bash
# D-1 证明 consumer rule 确实被应用（不是“没有规则”）
grep -n "room-runtime:2.6.1" -A2 app/build/outputs/mapping/localFirstInternal/configuration.txt
# 期望：:1025 provenance + :1026 -keep class * extends androidx.room.RoomDatabase

# D-2 证明被裁的是构造器，不是类
grep -A6 "^androidx.work.impl.WorkDatabase_Impl:" app/build/outputs/mapping/localFirstInternal/usage.txt
# 期望：输出包含 public void <init>()

# D-3 对照组：release 未裁
grep -A5 "^androidx.work.impl.WorkDatabase_Impl:" app/build/outputs/mapping/localFirstRelease/usage.txt
# 期望：不包含 <init>()

# D-4 证明项目规则只进了 release
grep -n "keepclassmembers class \* extends androidx.room.RoomDatabase" app/build/outputs/mapping/localFirst*/configuration.txt
# 期望：只有 localFirstRelease 命中

# D-5 证明未用关闭 strict full mode 的逃逸口
grep -rn "strictFullModeForKeepRules\|enableR8.fullMode" gradle.properties
# 期望：无输出

# D-6 基线：internal 被裁无参构造器总数
grep -c "^    public void <init>()" app/build/outputs/mapping/localFirstInternal/usage.txt
# 期望：351（批次 21 基线）
```

### 8.2 修后验收

```bash
# 1) 构建 minified-debug 变体（T5 在串行门里跑）
./gradlew :app:assembleLocalFirstInternal

# 2) 静态断言（S1-S4）
grep -n "keepclassmembers class \* extends androidx.room.RoomDatabase" app/build/outputs/mapping/localFirstInternal/configuration.txt
grep -A6 "^androidx.work.impl.WorkDatabase_Impl:" app/build/outputs/mapping/localFirstInternal/usage.txt      # 不得含 <init>()
for c in com.google.mlkit.common.internal.CommonComponentRegistrar \
         com.google.mlkit.vision.common.internal.VisionCommonRegistrar \
         com.google.mlkit.vision.text.internal.TextRegistrar; do
  grep -A2 "^$c:" app/build/outputs/mapping/localFirstInternal/usage.txt     # 不得含 <init>()
done
grep -n "^androidx.work.impl.WorkDatabase_Impl ->" app/build/outputs/mapping/localFirstInternal/mapping.txt    # 必须原类名

# 3) 安装并冷启动
adb install -r app/build/outputs/apk/localFirstInternal/*.apk
adb logcat -c
adb shell am start -n com.tingyun.smartmistakebook.localfirst/com.tingyun.smartmistakebook.MainActivity
sleep 10
adb shell pidof com.tingyun.smartmistakebook.localfirst   # 必须非空
adb logcat -d | grep -E "FATAL EXCEPTION|StartupException|NoSuchMethodException|ClassNotFoundException|NoClassDefFoundError"
# 期望：无输出

# 4) 设备功能冒烟（D3-D5，按 §7.2）
#    - 打开复习/清单页触发 Room3 查询
#    - 中文 OCR 一次
#    - 触发一次导出 Worker
adb logcat -d | grep -E "WM-WrkMgrInitializer|Room|MlKit|WorkerFactory|FATAL"

# 5) release 回归（不得回退）
#    需要 RELEASE_* 四个环境变量（validateReleaseSigning 会在缺失时失败）
./gradlew :app:assembleLocalFirstRelease
#    重复 3) 的启动与 logcat 断言（release 也覆盖 isShrinkResources=true）

# 6) 可选：benchmark 变体（同一补丁生效）
./gradlew :app:assembleLocalFirstBenchmark
```

## 9. 对 KD-69 记录的措辞修正建议

`docs/known-defects.md:2605` 现文把根因写成“`benchmark` / `internal` 只设了 `isMinifyEnabled = true`，没有 `proguardFiles(...)`；只有 release 配了……R8 剥掉 WorkManager/Room 生成类”。建议改为：

> **根因**：AGP 9.3.0 起 `android.r8.strictFullModeForKeepRules` 默认 true，R8 不再隐式保留被 keep 类的无参构造器；Room 2.6.1 的 consumer rule 无成员限定，`WorkDatabase_Impl.<init>()` 被裁。consumer rules 本身**已生效**（`configuration.txt:1025-1026`）。release 因应用了 `app/proguard-rules.pro` 的 `-keepclassmembers ... { <init>(); }`（`configuration.txt:101`）而未崩；`internal`/`benchmark` 未配置 `proguardFiles`，该文件未参与 → 崩。`matchingFallbacks` 不继承 `proguardFiles`。

其它需要同步的两点：

- 标题里的“剥掉 WorkManager/Room 生成类”应精确为“**剥掉生成类的无参构造器**”；类本身被 Room consumer rule 保住（`mapping.txt:260719` 未改名，`usage.txt` 也列出该类）。
- 同一条缺陷还覆盖三个 ML Kit registrar（`usage.txt:50717-50718 / 50955-50956 / 51010-51011`），修复后应把 reopen condition 从“启动无 WorkDatabase_Impl 缺失”扩为“启动 + 一次 OCR 均无 NoSuchMethodException”。

## 10. UNVERIFIED / 未验证项与风险

1. **未跑 Gradle / 未跑设备**（本批纪律）：修复后的实际构建结果、设备冒烟均未验证；§8 的期望值是依据 release A/B 的预测，不是本轮实测。
2. **官方网页不可达**：`developer.android.com` 与 `r8.googlesource.com` 在本环境连接超时（curl 返回 000）。R8 compatibility FAQ 文本经 `sentiance/r8`、`johnsonlee/r8`、`dracula6322/r8-mirror` 三个独立 GitHub 镜像逐字一致核对；ProGuard 语义经 Guardsquare 手册页 WebFetch 核对。规范 URL 见 §11，**待有网环境用规范 URL 复核**。
3. **AGP 10 弃用 / 11 移除**来自 `BooleanOption.class` 字节码中的 `FutureStage(VERSION_10_0)` / `DeprecationTarget(VERSION_11_0)` 与 Zeus issue 的一致描述，未取得官方发布说明原文。
4. **`configuration.txt` 的 AAPT2 段在 release 缺失**（internal 有、release 无，但 `aapt_rules.txt` 与 R8 输出表明规则实际生效）：成因未查清，可能是 AGP 产物/缓存时序。该不对称不影响 Room 结论（结论以 `usage.txt` 为准），但说明配置 dump 不能单独作为“规则是否应用”的唯一证据。
5. **`androidx.room` 2.6.1 升级到 2.7+ 作为替代方案未验证**（未改依赖、未跑）：仅作为被拒候选记录。
6. **351 个被裁无参构造器中可能仍有其它反射入口**：已确认的反射可达项为 WorkManager DB + 3 个 ML Kit registrar；其余（如 `coil` factory、`tflite InterpreterFactoryImpl`、`DataMigrationInitializer`）未逐一验证是否被反射实例化。修复后建议用 §8.2 的 `usage.txt` 复查并交叉比对合并清单/metadata。
7. **ML Kit 运行时路径**（D4）未验证：需要真机/模拟器 + OCR 夹具，本轮未做。

## 11. 来源

官方 / 一手：

- R8 compatibility FAQ（规范 URL，本环境不可达）：<https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md>
  - 镜像（逐字一致，用于本轮核对）：<https://github.com/sentiance/r8/blob/master/compatibility-faq.md>、<https://github.com/johnsonlee/r8/blob/master/compatibility-faq.md>、<https://github.com/dracula6322/r8-mirror/blob/master/compatibility-faq.md>
- ProGuard 手册 `-keep` 语义（WebFetch 已读）：<https://www.guardsquare.com/manual/configuration/usage>
- androidx-main Room3 consumer rules：<https://github.com/androidx/androidx/blob/androidx-main/room3/room3-runtime/src/androidMain/keepRules/rules.keep>
- Google Maven 构件：
  - `androidx.room:room-runtime:2.6.1`：<https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/2.6.1/room-runtime-2.6.1.aar>
  - `androidx.room:room-runtime-android:2.7.0`：<https://dl.google.com/dl/android/maven2/androidx/room/room-runtime-android/2.7.0/room-runtime-android-2.7.0.aar>
  - `androidx.room3:room3-runtime-android:3.0.0`（本机缓存 AAR）
  - `androidx.work:work-runtime:2.10.5`（本机缓存 AAR + POM）

本机一手产物（本轮直接读取）：

- `app/build.gradle.kts:57-80`（三个 buildType）
- `app/proguard-rules.pro:1-19`
- `gradle/libs.versions.toml:2,12,59,72,108`
- `app/build/outputs/mapping/localFirstInternal/{configuration,usage,mapping}.txt`
- `app/build/outputs/mapping/localFirstRelease/{configuration,usage,mapping}.txt`
- `app/build/intermediates/aapt_proguard_file/localFirstRelease/processLocalFirstReleaseResources/aapt_rules.txt`
- `app/build/intermediates/merged_manifest/localFirstInternal/processLocalFirstInternalMainManifest/AndroidManifest.xml:296-314`
- `build/device-track-batch21/step5-r8-logcat.txt:372-410`
- `docs/known-defects.md:2603-2611`

社区佐证（非官方，仅交叉印证）：

- <https://github.com/expo/expo/issues/51337>
- <https://github.com/ZeusLN/zeus/issues/4783>
- <https://github.com/sAleksovski/react-native-android-widget/issues/159>
