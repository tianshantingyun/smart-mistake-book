# 批次 42 WS8 发版前置（计划项 61–70）实跑记录

- 日期：2026-11-04（实跑时间 2026-11-03 11:00–12:30 本地）
- 计划落点：`.zcode/plans/plan-2026-11-04-batch-42.md:99-109`（WS8 61–70）；研究输入 = R6 `docs/research/2026-11-04-release-readiness.md`、R5 `docs/research/2026-11-04-lockfile-window.md`、`docs/research/2026-11-03-keeper-run-feasibility.md`、`docs/research/2026-11-03-16kb-page-testing.md`
- 纪律声明：本轮**零 git 写**、零密钥入库、未改任何构建脚本/热区文件（`core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、`Application/Root` 均未触碰）。新增文件只有本文与 `2026-11-04-release-gates-draft.md` 两份交付物；所有机器产物落在 `build/ws8-batch42/`（gitignored）。
- 标记：【本轮实跑】= 本会话执行并读到输出；【本轮实读】= 本会话读码；【未跑】= 未执行，不得当通过。

---

## 0. 结论摘要

1. **`assembleRelease` 流水线在本机可完整跑通（R8 + shrinkResources + lintVital），但只在“一次性本地签名钥匙”下成立**：四变量实测 0/4 → `:app:validateReleaseSigning` exit=1（负控成立）；用 %TEMP% 生成的一次性 key 注入四个 `RELEASE_*` 后 `:app:assembleRelease` **exit 0 / 7m03s / 466 tasks（141 executed）**，两个 flavor 的 release APK 与 mapping 全部产出。该 APK **不是生产签名**，`apksigner` 证书 DN = `CN=WS8 Ephemeral Local Only …`、sha256 `9dc07e9f…`，不得当作发布产物（§2.1）。
2. **R8 mapping 首次与本轮 APK 完成字节级绑定**：mapping 893,641 行 / 3,760 个项目类 / `compiler_version 9.3.16`；抽取 5 个混淆短名在**本轮 APK 的 dex** 中逐一命中（5/5），负控 0（§2.2）。四个 sidecar 文件（mapping/seeds/usage/configuration）sha256 全部算出并落档。
3. **“release APK 冒烟”在设备上真跑通，且跑在 16KB 页设备上**：`ps16k_x86_64`（x86_64 镜像、`getconf PAGE_SIZE=16384`）装上 `app-strictOffline-release.apk`，设备侧 APK sha256 与构建产物**逐字符相等**；两次冷启 `Status: ok / COLD`（1058 ms、876 ms）；手动录入→入库、复习判词→“今日已完成/连续 1 天”、导出 A4→`错题-W-第1版.pdf`（1 页、13,562 B、`%PDF-1.4`）三条链路全部走通，9 类崩溃签名在 5 份 logcat 中**全 0**（§2.3）。
4. **计划对 16KB 的前提有误**：本机 x86_64 Windows 宿主**能**跑 16KB —— `ps16k_x86_64` 冷启 28.9 s，`getconf PAGE_SIZE=16384`、`ro.boot.hardware.cpu.pagesize=16384`、SDK 36。更强的一条：OCR 触发时 linker 日志 `Load …/base.apk!/lib/x86_64/libmlkit_google_ocr_pipeline.so … : ok`，随后 `OCR process succeeded via visionkit pipeline` ⇒ **16KB 内核下真实装载了随包 16KB 对齐的 native 库并完成计算**。因此项 63 不该写“x86_64 宿主不可运行”，而应写“x86_64 宿主可运行（证据在此）；arm64 仍 BLOCKED”（§2.4）。
5. **签名侧缺口是“没有身份断言”，不是“不会构建”**：`validateReleaseSigning` 只查四个变量非空；keystore 可打开性（G-REL-11）、产出证书身份（G-REL-09）、mapping 与 APK 的 sidecar 归档（G-REL-06）三条**至今没有门**，本轮只把 09 的判据落到“报出 DN 与指纹”（§2.5）。
6. **依赖钉版门复跑 PASS 且负控真红**：`check_dependency_pins.py` exit 0（quickjs 1.0.15 / kotlin-stdlib 2.3.21），`--expect-kotlin-runtime 2.4.10` exit 1（§2.6）。`check_stdlib_compat.py` 0 unresolved（23,847 引用 / 329 构件）。
7. **KD-72 仍是“窗口关闭”，且本轮找到两个具体门闩**：在 `git archive HEAD` 的无锁副本（3,320 文件、0 个 lockfile）里 `lockAllDependencies --write-locks` **两次均失败**，失败点分别是 `:app:localFirstBenchmarkCompileClasspath` 与 `:app:localFirstDebugAndroidTestCompileClasspath` 的变体歧义（Gradle 无法在 `:core:data:releaseApiElements` / `:app:localFirstDebugApiElements` 之间选择）。**0 个锁写出**，共享工作树未被写入（§2.7）。
8. **Keeper 0.19.0 “不改 settings” 在配置层可执行**：插件 marker POM 200、`com.android.tools:r8:9.1.31` POM 200；在副本里把插件 apply 到 `:app` 后 `:app:tasks` **BUILD SUCCESSFUL 52 s exit 0**，且副本 `settings.gradle.kts` 与真树逐字节相同。K1–K6 的 L8 合并执行**未跑**（需 D1/D3 裁定 + release androidTest 全链）（§2.8）。
9. **供应链快照落盘**：340 个解析坐标中 328 个取到 license 文本（12 个上游 POM 无 `<licenses>`）；正文以 Apache-2.0 系为主（252 + 34 + 15 + 4 + 1 + 1），另有 ML Kit ToS 7、Android SDK License 4、Play Core ToS 3、BSD-3 3、GPL-2-with-Classpath 1、EPL-1.0 1。40 个直接依赖的 `maven-metadata.xml` 全部取到，35 个落后于上游 `release`（详见 §2.9，均为钉版决策，不等于缺陷）。

---

## 1. 基线

| 项 | 值 | 证据 |
|---|---|---|
| HEAD | `faab17f5`（`faab17f58b01eab78d5c08a9f843b3af3df2fecf`） | 【本轮实跑】`git rev-parse --short HEAD` |
| 工作树 | 记录时 634 条 dirty/untracked；暂存区 0 | 【本轮实跑】`git status --porcelain \| wc -l`；`git diff --cached --name-only \| wc -l` |
| versionCode / versionName | 2 / `0.2.0`（flavor 后缀 `-offline` / `-localfirst`） | 【本轮实跑】`aapt2 dump badging` |
| minSdk / targetSdk / compileSdk / buildTools | 23 / 36 / 37 / 37.0.0 | `app/build.gradle.kts:16-17,28-29`；badging |
| ABI | `arm64-v8a`、`x86_64` | `app/build.gradle.kts:45-47`；APK 内 14 个 `.so` |
| JDK / Gradle / AGP / R8 | Temurin 21.0.11 / 9.6.1 / 9.3.0 / 9.3.16 | `java -version`；`mapping.txt:2` |
| `RELEASE_*` 变量 | 4/4 UNSET | 【本轮实跑】逐变量存在性检查 |
| release signingConfig | 空壳（无 storeFile/alias） | `app/build.gradle.kts:19-24`；`configureReleaseSigningIfAvailable()` `:124-142`；`validateReleaseSigning` `:144-158` |
| 签名门挂载面 | 任务名含 `Release` 且以 `bundle`/`assemble`/`package` 开头 | `app/build.gradle.kts:161-167` |
| `mappingFileUploadEnabled` | 未设置 | `app/build.gradle.kts:87-99` |

---

## 2. 逐项结果

### 2.1 项 61：`assembleRelease` + R8 mapping 产出与 checksum

**负控（变量缺失）**：

```
./gradlew --console=plain :app:validateReleaseSigning        → EXIT=1
> Release signing is missing required environment variables:
  RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD, RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD
BUILD FAILED in 5s；1 actionable task: 1 executed
```

**正跑（一次性本地钥匙，仓外）**：

```bash
# 仓外 %TEMP% 生成 PKCS12，口令仅存 %TEMP% 文件并以环境变量注入；仓内无任何密钥字面量
keytool -genkeypair -keystore <TEMP>/ws8-ephemeral-b42.jks -storetype PKCS12 \
  -alias ws8ephemeral -keyalg RSA -keysize 2048 -validity 2 \
  -dname "CN=WS8 Ephemeral Local Only, OU=NotForRelease, O=Local, C=CN"
export RELEASE_{KEY_ALIAS,KEY_PASSWORD,STORE_FILE,STORE_PASSWORD}=...
./gradlew --console=plain :app:assembleRelease      → BUILD SUCCESSFUL in 7m 3s
  466 actionable tasks: 141 executed, 325 up-to-date   （11:03:41 → 11:10:48）
```

| 产物 | 字节 | sha256 |
|---|---|---|
| `apk/strictOffline/release/app-strictOffline-release.apk` | 155,816,991 | `724ce71834c227db79266149267d52c1b55a23d3cbceb2b93ce3930cba342b44` |
| `apk/localFirst/release/app-localFirst-release.apk` | 155,817,571 | `d25b35cc2d7a7719a7774081f0d080b86d40f81070ee7a0c27c865fdbdfa5625` |
| `mapping/strictOfflineRelease/mapping.txt` | 126,196,894 | `c5b2d17080e2af0614886fd66f5a1fb304f3f2f4a6a244f654d5e00a83a5dc7e` |
| `mapping/localFirstRelease/mapping.txt` | 126,196,894 | `c5b2d17080e2af0614886fd66f5a1fb304f3f2f4a6a244f654d5e00a83a5dc7e` |
| `…/strictOfflineRelease/seeds.txt` | 549,169 | `a88a586cbb13e963b6f1f8ebf6e877dc66a26a1e812bf182bf3488d6cdff0e9d` |
| `…/strictOfflineRelease/usage.txt` | 8,150,449（116,874 行） | `46a8e844a9499fb8dd7132990db0146c13c4713e5b43745aed860f2b317e2c04` |
| `…/strictOfflineRelease/configuration.txt` | 85,108 | `c158760c5a42eca9b68fd1035d15363d653db73f1441e6eb05f101e1283d6849` |

> 两个 flavor 的 mapping 逐字节相同（同 sha256）：flavor 只改 `applicationId`/`versionName` 常量，不改类名映射表；`seeds.txt` 不同。**这条一致性本身是“同一次构建”的旁证，不能替代 sidecar 绑定**。

**APK 内嵌源身份**：`META-INF/version-control-info.textproto` → `revision: "faab17f58b01eab78d5c08a9f843b3af3df2fecf"`（= HEAD），但工作树 dirty ⇒ 该 APK **不可由 HEAD 字节复现**，G-REL-00 仍按 FAIL 记（§4）。

### 2.2 项 61 续：mapping 结构 / 混淆真实性 / mapping↔APK 绑定

| 检查 | 命令 | 结果 |
|---|---|---|
| 头部三行 | `head -3 mapping.txt` | `# compiler: R8` / `# compiler_version: 9.3.16` / `# min_api: 23` |
| 规模 | `wc -l` / `grep -c '^com\.tingyun\.smartmistakebook\..* ->'` | 893,641 行 / 3,760 个项目类 |
| 混淆真实 | `grep -m3 … -> [a-z][a-z0-9]*:` | `BackgroundWriterQuiesceKt$… -> ds:`、`BatchImportDriverWorker$doWork$1 -> pv:`、`CapabilityFormViewModel -> z50:`、`… -> a60:`、`… -> lt0:` |
| 绑定探针 | Python：抽取 5 个短名，在 APK 全部 `classes*.dex` 里数 `L<name>;` | `ds=1 pv=1 z50=1 a60=2 lt0=1`；负控 `Lzzzzz9;` = **0** |
| R8 keep 回归 | `grep -A8` 三段 `usage.txt` | `WorkDatabase_Impl:` / `CommonComponentRegistrar:` / `ChineseTextRecognizerOptions:` 三段**均无 `void <init>()`** |

> 绑定探针的边界照旧：只证“mapping 里的名字存在于该 APK 的 dex 字符串池”，不证逐成员一致；逐成员一致靠“同一次构建 + sidecar sha”的流程约束（§2.5）。

### 2.3 项 62：release APK 冒烟（设备，16KB 页）

设备：`emulator-5620` = `ps16k_x86_64`（`sdk_gphone16k_x86_64`，SDK 36，`x86_64`，`PAGE_SIZE=16384`）。**选它而不是 5562/5566/5584**：后三台正被其它轨道使用（5566 前台是本项目 app），装包会替换同名包/毁他线状态；5620 为本轮新起，独占。

```bash
adb -s emulator-5620 install -r app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
#   Perf: Streamed Install → Success
adb -s emulator-5620 shell sha256sum <pm path 展开后的 base.apk>
#   724ce718…342b44   ← 与构建产物逐字符相等
adb … am force-stop …; adb … am start -W -n …/.MainActivity
#   #1 Status: ok / LaunchState: COLD / TotalTime: 1058 ms / pid 4130
#   #2 Status: ok / LaunchState: COLD / TotalTime: 876 ms
```

**三条链路（全部在 release+R8 包上完成，截图见 `build/ws8-batch42/smoke-screen*.png`）**：

| 链路 | 操作 | 可核结果 |
|---|---|---|
| 录入 | 录入 → 手动建题 → 填题面 → 存入错题本 | 页面 “已经存入错题本 … 这道题是新建入库的”；错题本列表 `1 道题` |
| 复习 | 复习根页 `1 道计划复习` → 开始今日复习 → 选“对” | “已记录：对（顺利回忆）”；根页 `1 道今日已完成 · 连续复习 1 天` |
| 导出 | 详情 → 导出 A4 → 选择 PDF → 开始导出 | `错题-W-第1版.pdf · 已完成 · 共 1 页`；设备文件 13,562 B，`%PDF-1.4`（pull 到 `build/ws8-batch42/smoke-export.pdf`） |

**崩溃签名扫描**（`FATAL EXCEPTION` / `StartupException` / `NoSuchMethodException` / `ClassNotFoundException` / `NoClassDefFoundError` / `UnsatisfiedLinkError` / `MlKitException` / `SIGSEGV` / `ANR in`）：`smoke-coldstart1.txt`、`smoke-after-save.txt`、`smoke-after-export.txt`、`smoke-after-review.txt`、`smoke-coldstart2.txt` 五份 logcat **全 0**。

**未覆盖（不得当通过）**：`libquickjs.so`（JS 视觉引擎需真实 Provider）、`libLiteRt.so`（端侧稠密腿未在 UI 上触发）在本轮**未装载**；`libmlkit_google_ocr_pipeline.so` 已在 OCR 路径装载（§2.4）。打印/`writeSubset` 属 WS2，不在本轨。

### 2.4 项 63：16KB —— 计划前提被实测推翻

**宿主机结论（改口径）**：x86_64 Windows 宿主**可以**运行 16KB 页。

| 证据 | 值 |
|---|---|
| AVD | `ps16k_x86_64`（`abi.type=x86_64`，镜像 `system-images;android-36;google_apis_ps16k;x86_64`） |
| 冷启 | `Boot completed in 28900 ms`（`-no-snapshot -no-boot-anim -gpu swiftshader_indirect`，端口 5620） |
| 页大小 | `getconf PAGE_SIZE`=**16384**；`ro.boot.hardware.cpu.pagesize`=**16384**；`ro.product.page_size` 为空（该属性在此镜像不填，不能当判据） |
| 镜像指纹 | `google/sdk_gphone16k_x86_64/emu64xa16k:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys` |
| 装载证据 | `nativeloader: Load …base.apk!/lib/x86_64/libmlkit_google_ocr_pipeline.so … : ok` → `OCR process succeeded via visionkit pipeline` |

**静态面（本轮新产物重跑）**：

```
zipalign -c -P 16 4 <两个 release apk>       → exit 0 / exit 0
unzip -l <apk> | grep '\.so$'                → 14 条，全 Stored；7×arm64-v8a + 7×x86_64；无 32 位
python <ELF 判据>（PT_LOAD p_align ≥ 0x4000；RELRO end%0x4000==0 或余数页无可写 LOAD）
  → 14/14 PASS（5 个 mod=0 + 9 个 own-LOAD(exempt)）
负控：com.google.mlkit:text-recognition-bundled-common AAR 内 jni/armeabi-v7a/libmlkit_google_ocr_pipeline.so
  → FAIL（LOAD#0/#1/#2 p_align=0x1000）  ← 判据能区分
```

**arm64 等待清单（BLOCKED，外部硬件）**：

1. 设备：Pixel 8/8a（Android 15 QPR1+）、Pixel 9 系列（QPR2+）、Pixel 9a（16+）开启 “Boot with 16KB page size”，或 arm64 宿主上的 Cuttlefish 16KB。
2. 前置断言：`getprop ro.product.cpu.abi` ∈ {arm64-v8a, arm64}；`getconf PAGE_SIZE`=16384（否则先重启到 16KB）。
3. 装 `app-strictOffline-release.apk`（**生产签名版**）→ 冷启 → 必须各触发一次 `libquickjs.so`（JS 视觉引擎）、`libmlkit_google_ocr_pipeline.so`（OCR）、`libLiteRt.so`（端侧稠密腿）→ 检查 `adb logcat | grep -Ei 'linker|RELRO|SIGSEGV|UnsatisfiedLinkError'` 为 0。
4. 记录 `nativeloader: Load … !/lib/arm64-v8a/… : ok` 三条（与 §2.4 同格式）作为“真装载”证据，而不是只看冷启。

### 2.5 项 64：签名配置占位清单（不落密钥）

| 项 | 事实 | 出处 |
|---|---|---|
| 变量名（唯一契约） | `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` / `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` | `app/build.gradle.kts:125-128`；`:147-150` |
| 注入时机 | 求值期 `configureReleaseSigningIfAvailable()`；**缺任一则静默跳过**（不报错） | `:124-142` |
| 门 | `validateReleaseSigning` 只判“非空”；挂载到含 `Release` 且以 `bundle`/`assemble`/`package` 开头的任务 | `:144-167` |
| 缺的断言 | ① keystore 能打开且 alias 是 `PrivateKeyEntry`；② 产出 APK 的证书指纹 == 发布指纹；③ DN 不得是 `R04 Local`/`CI Dry Run`/`Android Debug`/本轮 `WS8 Ephemeral` | 本轮读码 + `apksigner` 输出 |
| 仓内密钥面 | `git ls-files` 命中 `*.jks/*.keystore/*.p12/*.pem` = 0；`.gitignore:18-20` 已挡 | 【本轮实跑】 |
| 本轮一次性钥匙 | 仓外 `%TEMP%/ws8-ephemeral-b42.jks`（PKCS12，2,724 B，有效期 2 天）；**证书 sha256 `9dc07e9f5ce9a409f38596ef89b39ec8ea561feb26d3c61dfbf1a958c6056e20`**；DN `CN=WS8 Ephemeral Local Only, OU=NotForRelease, O=Local, C=CN`；签名方案 v1=true、v2=true、v3/v4=false | 【本轮实跑】`apksigner verify --print-certs --verbose`（`build/ws8-batch42/apksigner-strictOffline.txt`） |

**发布者需提供（C 类，机器不能替）**：生产 keystore 文件（仓外）+ 四变量值（只经环境变量/密钥服务注入）+ Play App Signing 决策（上传密钥 vs 托管，决定 G-REL-09 的期望指纹）+ v3/v4 策略。

### 2.6 项 65：`mapping.txt` 保留策略与版本绑定

**保留策略（可执行版）**：

1. **不进 git**：`.gitignore:15` 的 `**/build/` 已覆盖；单 variant 就 126 MB，入库不可接受。
2. **命名**：`mapping-<applicationId>-<versionCode>-<versionName>-<variant>.txt`（本轮示例：`mapping-com.tingyun.smartmistakebook.offline-2-0.2.0-offline-strictOfflineRelease.txt`）。
3. **落点**：仓外对象存储/制品库，与本轮 APK/AAB 同目录；仓内只留 ≤2 KB 的 `release-artifacts.json`（sidecar）。
4. **寿命**：≥ 该版本在 Play 的整个可下载期（崩溃栈回溯窗口）；建议“版本下线后再留 12 个月”。
5. **Play 侧二选一**：(a) `release { mappingFileUploadEnabled = true }`（AAB 自动带；当前**未设置**）；(b) 手工上传。二者都没做时，“Play Console 里的栈可读”不成立。
6. **绑定字段（sidecar，本轮已产出全部输入）**：`git.revision`（= APK 内 textproto 值）、`versionCode/versionName`（= badging）、`apk.sha256`、`mapping.sha256`、`mapping.compilerVersion`（= 9.3.16）、`r8.{seeds,usage,configuration}.sha256`、`signing.certSha256/certDn`、`sixteenKb.{zipalignExit, elfPass, elfFail}`。

> 本轮**没有**新增 sidecar 生成脚本：现有证据（sha256 表 + 绑定探针 + apksigner 输出）已能机器复核；再加脚本属“未指认失败的机制”，留待 G-REL-06 真需要自动化时随 CI 一起落（见 §5 纪律）。

### 2.7 项 66：依赖钉版门复跑（含负控）

```
python -I tools/ci/check_dependency_pins.py                          → EXIT=0
  resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']
  OK: quickjs checksums, upstream POM requirement and resolved runtime pins match
python -I tools/ci/check_dependency_pins.py --expect-kotlin-runtime 2.4.10 → EXIT=1   （负控红，符合预期）
python -I tools/ci/check_lockfiles.py                                → EXIT=0
  OK: … KD-72 保持 open … 13 个未跟踪锁文件 … 缺锁 project：:benchmark
python -I tools/ci/check_release_manifest.py                         → EXIT=0（9 checked + 4 debug merged，5 个 debug-only 组件双向一致）
python -I tools/ci/check_r04_keep_rules.py                           → EXIT=0（两文件 `-dontobfuscate` 0 命中）
python -I tools/ci/check_stdlib_compat.py                            → EXIT=0（0 unresolved / 23,847 唯一 kotlin/* 引用 / 329 构件；11 坐标 unscanned）
```

注意：钉版门消费的是 **release 变体**的 `lintVitalAnalyze*Release` 中间产物 —— 本轮先跑完 `assembleRelease` 才复跑它，因此这次 exit 0 是“本轮产物”上的结论；`clean` 之后单跑该门会红（脚本自带缺失分支），不是“跳过”。

### 2.8 项 67：KD-72 锁文件窗口（隔离副本实做，失败 → 等待清单）

**副本构造**（不写 `.git`、不动真树）：

```
git archive --format=tar HEAD | tar -x -C /d/smb-ws8-lock     → 3,320 个文件
cp local.properties /d/smb-ws8-lock/                          （sdk.dir）
find /d/smb-ws8-lock -maxdepth 2 -name '*lockfile' | wc -l    → 0    ← 无旧锁，合并语义不成干扰
GRADLE_USER_HOME 走共享（java -Dgradle.user.home="D:\smart mistake book\.gradle" … GradleWrapperMain）
```

**两次尝试，两种失败，0 个锁写出**：

| 尝试 | 命令 | 结果 | 失败点（原文） |
|---|---|---|---|
| A | `lockAllDependencies --write-locks`（副本原样） | exit 1 / 10 s | `Could not resolve … ':app:localFirstBenchmarkCompileClasspath'` → 无法在 `:core:data:releaseApiElements` 等变体间选择（benchmark 构建类型在库模块无对应变体） |
| B | 同上，副本内把 `Benchmark` 配置排除后再跑（**仅副本改动**） | exit 1 / 12 s | `Could not resolve … ':app:localFirstDebugAndroidTestCompileClasspath'` → 无法在 `:app:localFirstDebugApiElements` 的 `android-app-symbol-for-data-binding` / 其它变体间选择 |

`find /d/smb-ws8-lock -maxdepth 2 -name '*lockfile'` 两次均为 **0**；真树未被移动/新增任何锁文件（`git status --porcelain | grep -i lockfile` 仍是原有 14 条 untracked）。

**最小等待清单（按依赖顺序）**：

1. **W1 修 benchmark 解析面**：`:app:localFirstBenchmarkCompileClasspath` 必须可解析（给库模块补 `matchingFallbacks`，或在可锁配置集合里显式排除 benchmark 并记录理由）。**不做完，写锁第一步就红。**
2. **W2 修 androidTest 自引用变体歧义**：`:app:<flavor>DebugAndroidTestCompileClasspath` 对 `:app:<flavor>DebugApiElements` 的歧义需先定位（疑似 `android-app-symbol-for-data-binding` 变体属性未被消费端声明），否则第二关就红。
3. **W3 预期还有同类歧义**：2 flavor × 2 buildType（benchmark/internal）× {compile,runtime,unitTest,androidTest} 共数十个配置，逐个修复后再跑；每修一个就复跑一次 `--write-locks`，不做“一次改多处”。
4. **W4 幂等复跑**：写完后连续两次 `--write-locks`，`sha256sum` 逐字节相同（R5 §4.4 步骤 3）。
5. **W5 覆盖冒烟**：`assembleLocalFirstDebug`、`:benchmark:assemble`、`compileStrictOfflineDebugUnitTestKotlin`、`compileStrictOfflineDebugAndroidTestKotlin`、`:core:domain:testClasses`、`:app:lintLocalFirstDebug`；出现 `Resolved … which is not part of the dependency lock state` 即覆盖不全。
6. **W6 拷回 + 单笔提交**：15 个文件（13 模块锁 + `benchmark/gradle.lockfile` + `settings-gradle.lockfile`），LF 换行；提交期间任何 build 脚本变更都要整批重写。

**结论**：KD-72 **维持 open**；本轮把“窗口关闭”从“树脏”改写为“**配置面解析不通过**”这一更硬的阻塞，且是两次可复现的实跑。

### 2.9 项 68：供应链 license / 更新度快照（只读）

**输入**：13 个未跟踪 `gradle.lockfile`（1,202 条坐标×配置）+ `gradle/libs.versions.toml` + POM（repo1/google Maven，HTTPS 白名单，拒绝 DTD/ENTITY，2 MB 上限）。
**产物**：`build/ws8-batch42/dependency-licenses.tsv`（340 行）、`dependency-updates.tsv`（40 行）。

| 指标 | 值 |
|---|---|
| 唯一坐标 | 340 |
| 取到 `<licenses>` 文本 | **328**；12 个上游 POM 无 license 标签（guava/failureaccess/listenablefuture、commonmark-ext-*、auto-value-annotations），属上游元数据缺失，**不等于**无授权 |
| Apache-2.0 系合计 | 252 + 34 + 15 + 4 + 1 + 1 = **307** |
| 其它 | ML Kit ToS 7、Android SDK License 4、Play Core ToS 3、BSD-3-Clause 3、GPL-2.0-with-Classpath-exception 1、EPL-1.0 1 |
| 直接依赖（catalog 钉版） | 40；`maven-metadata.xml` **40/40 取到**；pinned == upstream `release` 5 个，落后 35 个 |
| 动态版本 | `libs.versions.toml` 无 `+`/`latest.*`/`SNAPSHOT` |

**需人工判断的落后项（不是缺陷，但发版前应逐条裁定）**：`litert 2.1.6 → 2.3.2`（minSdk 24 的已知阻断，见 catalog 注释）、`compose-bom 2026.06.00 → 2026.09.00`、`room3 3.0.0 → 3.1.0-alpha03`（alpha，不追）、`okhttp 5.4.0 → 5.5.1`、`navigation-compose 2.9.8 → 2.10.2`、`work 2.10.5 → 2.12.0`、`desugar_jdk_libs 2.0.3 → 2.1.5`、`sqlite-jdbc 3.53.4.0 → 3.54.1.0`，其余为 alpha/beta 轨道。**本轮不动版本**（发版前置不是升级窗口）。

### 2.10 项 69：Keeper（R-04）可执行性验证（不改 settings）

| 门 | 类别 | 本轮结果 |
|---|---|---|
| S1 插件 marker 可取 | 网络 | `https://repo1.maven.org/…/io.github.usefulness.keeper.gradle.plugin/0.19.0/*.pom` → **HTTP 200** |
| S3 R8 构件可得 | 网络 | `https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.1.31/r8-9.1.31.pom` → **HTTP 200** |
| S2 仓库模式 | 读码 | `settings.gradle.kts:10` = `FAIL_ON_PROJECT_REPOS`；插件默认 `automaticR8RepoManagement=true` 会触碰 `project.repositories` ⇒ 运行期必须显式关掉 |
| **配置层可执行** | 实跑（副本） | 把 `alias(libs.plugins.keeper)` 加到 `:app`（+ catalog/root `apply false`）后：`java … GradleWrapperMain --console=plain --no-configuration-cache :app:tasks` → **BUILD SUCCESSFUL in 52s，exit 0** |
| settings 未改 | 实跑 | 副本 `settings.gradle.kts` 与真树 `git diff --no-index` **无差异**（0 行输出，exit 0） |
| 插件任务面 | 实跑 | `:app:tasks` 输出中**没有** Keeper 命名的任务（它挂在既有 `test*` 任务的 doLast 上，与 R8 研究 §2.3 一致） |
| K1–K6（L8 合并实际效果） | **未跑** | 需 D1/D3 裁定 + `assembleStrictOfflineReleaseAndroidTest`（release androidTest 全链）+ 设备两例；本轮只到“配置层可执行” |

> 结论口径：**Keeper 0.19.0 在本仓“不改 settings”可配置（实跑证明），但“可运行”仍未证明**。不得写成“R-04 已可执行”。

### 2.11 项 70：release-gates 草案

落盘为 `docs/research/2026-11-04-release-gates-draft.md`（G-REL-00…G-REL-28，每条含命令/判据/类别/本轮状态）。本文不重复表格。

---

## 3. 本轮改动与产物清单

| 项 | 内容 |
|---|---|
| 新增文档 | `docs/research/2026-11-04-ws8-release-prefight.md`（本文）、`docs/research/2026-11-04-release-gates-draft.md` |
| 机器产物（gitignored） | `build/ws8-batch42/`：`assemble-release.log`、`gate-signing-vars.log`、`apksigner-strictOffline.txt`、`gate-pins*.log`、`gate-check_*.log`、`lock-write{,2}.log`、`keeper-probe1.log`、`smoke-*.png`、`smoke-*.txt`、`smoke-export.pdf`、`dependency-{licenses,updates}.tsv` |
| 仓外一次性 | `%TEMP%/ws8-ephemeral-b42.jks`（+ 口令文件），**不入库**；用完可删 |
| 隔离副本 | `D:\smb-ws8-lock`（3,320 文件；含两处仅限副本的补丁：Benchmark 过滤、Keeper apply）。**不拷回任何文件** |
| 源码改动 | **无** |

**“新增必须指认失败”自查**：本轮没有新增任何源码/脚本/依赖/配置项。两份文档是计划项 70 与记录义务的直接交付；`build/ws8-batch42/*` 是证据，不是机制。唯一“新增”的运行时对象是一次性钥匙，它消灭的失败是“没有生产钥匙时整条 release 流水线无法被验证”，且被限定在仓外 + 2 天有效期 + DN 显式标注。

---

## 4. BLOCKED / UNVERIFIED / 残余风险

**BLOCKED（外部条件，本机不可解）**

1. **生产签名**：四变量 0/4；真实 keystore 与 Play App Signing 决策只能由发布者给。本轮 APK 一律按“本地测试签名包”处理。
2. **arm64 16KB 运行时**：无 arm64 设备/宿主；等待清单见 §2.4。
3. **AAB 页对齐检查**：本机无 `bundletool-all`（缓存里的 1.18.3 是库 jar，非 fat jar）；C 类。
4. **KD-72 写锁**：配置面解析失败（§2.8），不是本轨能单方面修好的（要 build 脚本改动 + 决策）。

**UNVERIFIED（不得当通过）**

1. 构建确定性：未做两次 clean 构建 `cmp`；冷构建（`--offline --no-daemon`）未跑。
2. 真 retrace：未用 mapping 还原一条真实混淆栈（本机缓存无 `com.android.tools:r8` 可执行入口）。
3. `libquickjs.so` / `libLiteRt.so` 的 16KB 运行时装载（未触发的路径）。
4. `mappingFileUploadEnabled` 对 Play 上传的实际效果。
5. Keeper K1–K6 的实际 L8 合并结果。
6. `check_stdlib_compat.py` 的 11 个 unscanned 坐标（脚本自报覆盖非全量）。
7. 供应链 12 个无 `<licenses>` 的上游 POM（照实记缺失，不推断许可）。

**残余风险**

1. 本轮 APK 由 dirty 树构建（revision=HEAD 但树不干净）⇒ 线上崩溃栈与源码行号对不上；任何“发版就绪”结论必须等 clean 树重建。
2. 一次性钥匙若被误当生产钥匙使用（DN 已带 `NotForRelease`，但仍需人在发布流程里核指纹）。
3. 16KB 只在 x86_64 ps16k 镜像上验证；arm64 的 linker 行为未证。
4. 锁门 exit 0 是“跳过态”，不能引用为“依赖已钉死”。

---

## 5. 未跑（如实）

`clean` 冷构建与确定性对比、`bundleRelease`/AAB、`bundletool`、retrace、arm64 16KB、Keeper L8 实跑、KD-72 覆盖冒烟、Play 上传、生产签名——均未执行，理由与前置见 §4。
