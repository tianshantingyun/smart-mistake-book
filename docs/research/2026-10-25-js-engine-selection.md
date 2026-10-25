# R1：Android JS 沙箱执行引擎选型（批次 20 / 4E JS 后端地基）

日期：2026-10-25。任务来源：批次 20 计划 R1（`.zcode/plans/plan-2026-10-25-batch-20-4e-js-beta-prep.md` §3），决定 T4 QuickJS 接入。本报告全部证据为当日新取（命令与 URL 逐条标注），并与既有契约 `docs/current/visual-generation-contract.md`（2026-10-18 裁定）交叉核对。

## 0. 结论（一句话）

**推荐维持既有裁定：dokar3/quickjs-kt v1.0.15（钉 commit `a081755bcdb18ee6afa7ffee25b8fa976f06d31f`，绑定层 Apache-2.0 + 内嵌 QuickJS 上游 MIT），但 T4 必须消费"钉源码 + NDK r28c 自建产物"，不得直接消费 Maven 发布 AAR（其 GNU_RELRO 结束未 16 KB 对齐，本日本地解析复核成立）。** 其余三个候选按硬判据淘汰：Zipline 官方自述不作不可信代码安全边界；quickjs-ng 无维护中的 Android 绑定、自建 JNI 与计划 T4 "直接采用选型库，不自己写 JNI" 直接冲突；Rhino 官方 it-android 声明 minSdk 26（不覆盖本项目 23）且 Android 上仅解释执行、无独立 JS 堆上限。

## 1. 判定表

判据列对齐任务书（沙箱硬终止/内存上限/minSdk 23/16 KB/x86_64+arm64/体积/维护度/license/契约契合）。"✓"= 当日核到一手证据；"✗"= 当日核到一手反证。

| 判据 | dokar3/quickjs-kt v1.0.15 | Cash App Zipline 1.28.0（quickjs JNI 底座在 monorepo 内） | quickjs-ng v0.17.0 + 自建 JNI | Mozilla Rhino 1.9.1 |
|---|---|---|---|---|
| 沙箱硬终止（库内协作中断） | ✓ `evaluationTimeoutMillis` / `interruptEvaluation()` / 协程取消可中断忙循环；`QuickJsInterruptedException`（README Interrupting evaluations 节） | 底座 JNI 有 `JS_SetInterruptHandler`，但 Kotlin 面向签名模块加载（quickjs-jni.cpp:80-86） | ✓ C API `JS_SetInterruptHandler`（quickjs.h:1191-1193） | △ 仅指令观察器（Context.java:2427+，抛 Error 终止，协作式） |
| 沙箱硬终止（进程级） | 不由库提供——按契约 §4 由 `:visual_js` 隔离进程 + watchdog `killProcess(myPid())` 承担；库 issue #240 证明仅靠协作中断不够 | ✗ README："does not offer a sandbox or process-isolation and should not be used to execute untrusted code" | 需自建全部（进程隔离/watchdog/AIDL） | 需自建（同左，且无堆上限配合） |
| 内存上限 | ✓ `memoryLimit`/`maxStackSize`/`memoryUsage`（QuickJs.kt:106-118） | 底座 JNI 有 `setMemoryLimit`/`setGcThreshold`（quickjs-jni.cpp:100-116），公开面不可用 | ✓ C API `JS_SetMemoryLimit`（quickjs.h:515） | ✗ 无独立于 JVM 堆的 JS 堆上限 |
| minSdk 23 | ✓ 钉源码 `gradle/libs.versions.toml` `android-minSdk = "23"`（带 ref=a081755 取得） | ✓（其 catalog `minSdk = "23"`） | —（无产物，自建时自定） | ✗ 官方 it-android README："minSdk is 26 (see #1785)"；"runs only in interpreted mode" |
| 16 KB 对齐 | 源码带 `-Wl,-z,max-page-size=16384`（native/CMakeLists.txt:133）；**发布 AAR 实测 PT_LOAD=0x4000 但 GNU_RELRO 结束未对齐**（见 §3.1，本地解析） | 未测（已被信任模型否决） | 需自建时自行保证并自验 | 不适用（纯 JVM 无 .so） |
| x86_64（模拟器）+ arm64 | ✓ 发布 AAR 含四 ABI（arm64-v8a 924,936 B、x86_64 911,248 B、armeabi-v7a 656,988 B、x86 879,640 B，本地解包实测） | 未测 | 自建自担 | ✓ 纯 JVM |
| 体积 | 4 ABI .so 合计 3.22 MiB（未压缩）；AAR 下载 1.83 MB；按 app 实际 arm64+x86_64 ≈ 1.76 MiB（本地实测） | 未测（整链还引入 loader/cryptography 等模块） | 引擎源码体积同级，但绑定自担 | 单 jar 1,647,024 B ≈ 1.57 MiB（本地实测 HTTP 头） |
| 维护度 | v1.0.15（2026-09-03）、v1.0.14（2026-08-15）、v1.0.13（2026-08-14），repo pushedAt 2026-10-20；单人维护者（bus factor 低）；issue #240 开着（job 队列卡死协作中断失效，2026-09-26） | 1.28.0（2026-09-29），pushed 2026-10-23，2313★，Cash App 生产使用 | v0.17.0（2026-09-18），pushed 2026-10-24，4012★，社区活跃 | 1.9.1（2026-02-15），pushed 2026-10-25，4639★，Mozilla 系维护 |
| license | 绑定层 Apache-2.0（LICENSE.txt 首行，当日读取）+ 内嵌 bellard/quickjs MIT（子模块钉 04be246，LICENSE 文件当日读取） | Apache-2.0 | MIT | MPL-2.0（"The majority of Rhino is licensed under the MPL 2.0"，LICENSE.txt 当日读取） |
| 契约契合（RenderScene：JSON 进→绘制指令出，无 IO 无网络） | ✓ 探针已实测默认无 fetch/document/window/process/require（probe findings 第 2 项）；纯计算 + 原生校验回放即契约 §3.2/§5 形态 | ✗ 面向 Kotlin/JS 编译模块 + 签名验证，非任意字符串求值 | 可达但全部自担 | △ 可行但解释执行慢、无堆上限，配不了契约 §4 预算表 |
| 判定 | **采纳（钉源码自建）** | 淘汰 | 淘汰（本批；留作后备） | 淘汰 |

参考项：**AndroidX JavaScriptEngine 1.1.1**——今日从 Google Maven 下载 AAR 解 manifest 字符串池，直接读到 `android:minSdkVersion="26"`；且 maven-metadata（lastUpdated 2026-09-23）确认 1.1.1 仍是最新。minSdk 26 > 23，与契约 §2 裁定一致，继续排除。

## 2. 证据与命令记录（当日新取）

检索通道：`gh` CLI（已认证）+ curl + 本地 Python 解析。WebFetch 直连 developer.android.com 超时，改用 webReader 成功取得两页官方文档（16 KB page-sizes 与 JavaScriptEngine releases）。

- `gh repo view dokar3/quickjs-kt --json ...` → Apache-2.0、v1.0.15（2026-09-03）、pushedAt 2026-10-20、未归档。
- `gh api repos/dokar3/quickjs-kt/branches/main` → main HEAD = `a081755...` 与契约钉的 commit 一致，即下面所有无 ref 取的文件都等于钉定源码。
- `gh api "repos/dokar3/quickjs-kt/contents/gradle/libs.versions.toml?ref=a081755..."` → `android-minSdk = "23"`、`android-compileSdk = "36"`。
- `gh api repos/dokar3/quickjs-kt/contents/quickjs/build.gradle.kts` → `ndk { abiFilters += listOf("x86","x86_64","armeabi-v7a","arm64-v8a") }`；CMake 外链原生构建。
- `gh api repos/dokar3/quickjs-kt/contents/quickjs/src/commonMain/kotlin/com/dokar/quickjs/QuickJs.kt` → `memoryLimit`(L106-108)、`maxStackSize`(L113)、`memoryUsage`(L118)、`evaluationTimeoutMillis`(L128)、`interruptEvaluation()`(L138)、`compile()`(L241)、`close()`(L296)、`companion.create(jobDispatcher)`(L314)。
- README Interrupting evaluations 节（同 API 取）→ 协程取消可中断忙 JS；`evaluationTimeoutMillis` 超时抛 `QuickJsInterruptedException`；它是 `QuickJsException` 子类，catch 顺序要求。
- 子模块：`gh api "repos/dokar3/quickjs-kt/contents/quickjs/native/quickjs?ref=a081755..."` → 钉 `04be246001599f5995fa2f2d8c91a0f198d3f34c`（bellard/quickjs，2026-06-16 提交；上游 VERSION=2026-06-04）。该 commit 的 LICENSE 文件为 MIT 全文（2017-2021 版权）。GitHub 仓库页 license 字段显示 NOASSERTION 属检测问题，以文件内容为准。
- 开放 issue：#240 "evaluate() never returns when a job keeps queuing the next one, even with interruptEvaluation()"（2026-09-26 开）——直接支撑契约"引擎 timeout 是协作中断、close 可能等锁，均不是硬终止"的立场。
- Zipline：仓库无独立 quickjs-android repo（`gh api search/repositories?q=quickjs+user:cashapp` 空）；JNI 在 monorepo（`update_quickjs.sh`、`zipline/native/quickjs`、`quickjs-jni.cpp`）。README L188-189 原文："It does not offer a sandbox or process-isolation and should not be used to execute untrusted code."。`zipline/native/quickjs-jni.cpp` 确有 setMemoryLimit/setGcThreshold/setInterruptHandler JNI 符号，但 Kotlin 公开面（QuickJs.kt，nativeMain）围绕模块加载与签名验证。minSdk=23（其 libs.versions.toml）。
- quickjs-ng：`gh repo view quickjs-ng/quickjs` → MIT、v0.17.0（2026-09-18）、pushed 2026-10-24。`quickjs.h` → `JS_SetMemoryLimit`(L515)、`JS_SetGCThreshold`(L519)、`JS_SetInterruptHandler`(L1191-1193)。`gh api "search/repositories?q=quickjs-ng+android"` → 无维护中的 Android 绑定（唯一命中为无关 0★ 仓库）。
- Rhino：`gh repo view mozilla/rhino` → 1.9.1（2026-02-15）、pushed 2026-10-25。LICENSE.txt → "The majority of Rhino is licensed under the MPL 2.0"。`rhino/src/main/java/org/mozilla/javascript/Context.java` L2424-2466 → `setInstructionObserverThreshold(int)` + `observeInstructionCount` 回调"throws Error to terminate the script"，且注明观察计数"only affects JVM byte code generation"。`it-android/README.md` Restrictions 节原文："minSdk is 26 (see #1785 for background)"、"runs only in interpreted mode"、JavaAdapter 等特性受限。
- AndroidX JavaScriptEngine：maven-metadata（`https://dl.google.com/dl/android/maven2/androidx/javascriptengine/javascriptengine/maven-metadata.xml`，curl）→ latest/release = 1.1.1。下载 `javascriptengine-1.1.1.aar` 解 `AndroidManifest.xml` 字符串池 → `<uses-sdk android:minSdkVersion="26" />`。
- 官方 16 KB 文档（webReader 重取 `developer.android.com/guide/practices/page-sizes`）：Google Play 2027-02-01 起 64 位更新必须支持 16 KB；NDK r28+ 默认对齐；RELRO 完整判定 = `(VirtAddr + MemSiz) % 0x4000 == 0`，或 RELRO 与其他只读数据同 LOAD 且后续可写 LOAD 从独立 16 KB 页边界开始，否则运行时 `SIGSEGV`。

### 2.1 发布 AAR 的 16 KB 实测（本地命令，非转述）

命令：`curl -sL -o $TMP/qjs.aar https://repo1.maven.org/maven2/io/github/dokar3/quickjs-kt-android/1.0.15/quickjs-kt-android-1.0.15.aar` + Python zipfile 解 ELF64 program headers（完整输出留存本节）：

```
--- arm64-v8a ---
LOAD   off=0x0000000 vaddr=0x0000000 filesz=0x0ba5d0 memsz=0x0ba5d0 flags=RE  align=0x4000
LOAD   off=0x00ba5d0 vaddr=0x00be5d0 filesz=0x0061b8 memsz=0x006a30 flags=RW  align=0x4000
LOAD   off=0x00c0788 vaddr=0x00c8788 filesz=0x000010 memsz=0x0002f8 flags=RW  align=0x4000
GNU_RELRO off=0x00ba5d0 vaddr=0x00be5d0 filesz=0x0061b8 memsz=0x006a30  → end=0xc5000, end%16384=4096
--- x86_64 ---
LOAD   off=0x0000000 vaddr=0x0000000 filesz=0x0c4be0 memsz=0x0c4be0 flags=RE  align=0x4000
LOAD   off=0x00c4be0 vaddr=0x00c8be0 filesz=0x006198 memsz=0x006420 flags=RW  align=0x4000
LOAD   off=0x00cad78 vaddr=0x00d2d78 filesz=0x000010 memsz=0x0002e8 flags=RW  align=0x4000
GNU_RELRO off=0x00c4be0 vaddr=0x00c8be0 filesz=0x006198 memsz=0x006420  → end=0xcf000, end%16384=12288
```

判定：PT_LOAD 对齐 0x4000（16 KB）✓，与契约 §2"PT_LOAD 对齐、RELRO 结束未对齐"一致。按官方完整规则，RELRO 虽独占其所在 RW LOAD（memsz 相等），但下一个可写 LOAD 起点（arm64 0xc8788 / x86_64 0xd2d78）不在 16 KB 页边界上，严格读法不满足豁免条件；本机无 NDK（`C:\Android\Sdk` 下无 ndk 目录，实测）无法跑 llvm-readelf 交叉工具复核，最终合规判定留给 T8 对**自建产物**按官方方法核验。结论不变：发布 AAR 不可直接消费，须钉源码以 NDK r28c（`28.2.13676358`，契约 §2 已钉）重建。

### 2.2 体积实测

同一次下载解包：`jni/arm64-v8a/libquickjs.so` 924,936 B、`jni/x86_64` 911,248 B、`jni/armeabi-v7a` 656,988 B、`jni/x86` 879,640 B，四 ABI合计 3.22 MiB（未压缩）；AAR 文件 1,828,904 B。Rhino 1.9.1 jar 经 `curl -sI` 读 Content-Length = 1,647,024 B（HTTP 200，repo1.maven.org）。

## 3. 与既有契约/计划的裁定对齐

- 契约 §2（visual-generation-contract.md:21-23）已裁 quickjs-kt v1.0.15 钉源码 + NDK r28c 重建，并列 AndroidX（minSdk 26）与 Zipline（非安全边界）的排除理由。本日全部复核成立，且补了两条更硬的证据：Rhino it-android 官方 minSdk 26（契约未列 Rhino，本报告补齐第四候选）；发布 AAR 的 RELRO 反证由本地 ELF 解析直接复现。
- 计划 T4 红线"直接采用选型库，不自己写 JNI"→ quickjs-ng+自建 JNI 被该红线淘汰，除非 quickjs-kt 停维护（见 §6 后备触发条件）。
- 探针先例（`.jez/artifacts/android-visual-runtime-probe-2026-10-17/`，`probe/build.gradle.kts:18` 用坐标 `io.github.dokar3:quickjs-kt-android:1.0.15`）证明消费与编译可行；正式产物改走钉源码自建（16 KB 阻断门），坐标仅作对照。

## 4. 集成配方（T4 输入）

**模块与端口（整轨摘除设计）**：按契约 §5（visual-generation-contract.md:102）：core:domain 定义 `VisualExecutionEngine` 端口；app 实现组合 core:visual-js 客户端（QuickJS 执行）+ core:visual（内容布局/回放）；core:data 只见端口。摘除 = 删 core:visual-js 模块 + DI 解绑，WG-02/03/10 与 IMAGE_MODEL 链不受影响；JS 方法保持 UNAVAILABLE（4E.2 门前不广告——计划"已裁边界"）。

**依赖坐标**：
- 消费发布物（仅对照/探针）：`io.github.dokar3:quickjs-kt-android:1.0.15`（Maven Central，本日 curl 验证存在；探针 build.gradle.kts:18 原句）。
- **正式产物：钉源码 `https://github.com/dokar3/quickjs-kt/tree/a081755bcdb18ee6afa7ffee25b8fa976f06d31f`（= main HEAD，本日核）+ 子模块 quickjs@04be246 + NDK r28c 28.2.13676358 自建 AAR**，落本地仓库（仿 litert 的"构建期派生本地 AAR"先例，libs.versions.toml 注释有案）。构建依赖 SHA 一并记录，不动态追新。

**初始化（每次事务新上下文，契约 §3.2）**：
```kotlin
val quickJs = QuickJs.create(singleThreadDispatcher) // :visual_js 专用 dispatcher
quickJs.memoryLimit = 16L * 1024 * 1024   // 契约 §4 预算表：堆 16 MiB
quickJs.maxStackSize = 256L * 1024        // 栈 256 KiB
quickJs.evaluationTimeoutMillis = 100     // 单 JS 阶段协作期限 100 ms（软）
// bootstrap：封闭 Date/Math.random，注入 input.seed 的 SDK PRNG（契约 §3.2）
// 每事务：evaluate(sourceJs) → prepare/render 各一次 → close()
```

**终止 API 三层**（契约 §4，visual-generation-contract.md:66-70）：
1. 协作：`evaluationTimeoutMillis` / `interruptEvaluation()` / 调用协程取消 → `QuickJsInterruptedException`（注意 issue #240：job 自续队列可绕过协作中断——不得当硬终止）。
2. 服务硬期限：`:visual_js` 隔离进程内 watchdog 线程 + `killProcess(myPid())`；AIDL `oneway terminate(generation)` 原子核对 generation 后自杀。**库不提供这一层，是 T4 自建面。**
3. 宿主：废弃 generation → unbind → Binder death 确认 → 最多重建一次；programHash 熔断。

**错误面**：`QuickJsException` 为基类；`QuickJsInterruptedException extends QuickJsException`（catch 顺序：先中断后异常）；`memoryLimit` 触发 = 引擎 OOM 异常路径（探针第 4 项已验：堆上限触发 OOM）；close 可能等待引擎锁（契约 §4 原文），不得在 Binder/主线程调用。所有错误经 AIDL 有界诊断回传，超限/崩溃统一走文字降级 + 原帧保留。

**ABI/16 KB**：四 ABI 全带（含 armeabi-v7a，minSdk 23 需要）；x86_64 供模拟器设备轨；自建产物以 NDK r28c + 源码内既有 `-Wl,-z,max-page-size=16384`（native/CMakeLists.txt:133）产出，RELRO 终验归 T8（llvm-readelf 按官方两步法，本机当前无 NDK，未运行——非本报告范围）。

**边界重申（计划已裁）**：T4 只落运行库与执行端口；工具面/提示词/能力快照仍只声明 IMAGE_MODEL；JS 保持 UNAVAILABLE 直到 4E.2 的门。

## 5. 风险

- **单人维护者**（dokar3）：bus factor 低。缓解：钉 commit 源码自建已把外部漂移冻结；触发后备（§6）的条件写明。
- **协作中断已知缺口**（issue #240 开放）：不影响本架构（硬终止在进程层），但 T4 的仪器化测试必须包含"job 队列自续"用例，不得只测 `while(true)`。
- **RELRO 严格读法不合规**：若 NDK r28c 重建后 RELRO 结束仍不对齐（低概率，r28 lld 默认处理），则需给链接加 padding 方案——届时回写契约并升级为阻断，不换引擎。
- Maven 坐标 namespace 是 `io.github.dokar3`（非 `com.dokar`），文档引用时别写错。

## 6. 后备触发条件（记档，不本批执行）

若 quickjs-kt 停维护且出现阻断级缺陷：后备 = quickjs-ng（MIT、活跃、C API 同构：JS_SetMemoryLimit/JS_SetInterruptHandler）+ 自建 JNI——但那违反 T4 "不自写 JNI"红线，须先呈用户裁定；届时也可评估 AndroidX JavaScriptEngine（若项目 minSdk 升 26+）。Rhino 因 minSdk 26 + 无堆上限，不列为后备。

## 7. 未验证项（如实登记）

- 本机无 NDK/llvm-readelf：发布 AAR 的 RELRO 判定基于自写 ELF 解析（Python 直读 program headers），未经官方工具交叉复核；自建产物的 16 KB 终验归 T8。
- 未运行任何 Gradle 构建/设备测试（门禁由脚本串行执行——共享纪律）。
- Zipline 体积未实测：其被信任模型一票否决，无量化必要。
- Rhino `#1785` PR 内容未深读（标题为 slot map 重构，minSdk 26 的事实以 it-android README 原文为准，单一来源标"待验证"仅指"为何升 26"的背景，不影响淘汰结论）。
- quickjs-kt 与 kotlinx-serialization 转换器子模块（quickjs-converter-ktxserialization）未评估：契约 §3.2 决定 JSON 走原生有界解析，不依赖库转换器。

## 8. 来源

- 契约：`docs/current/visual-generation-contract.md`（§2 选型、§3.2 JS 协议、§4 隔离/预算、§5 端口架构）
- 计划：`.zcode/plans/plan-2026-10-25-batch-20-4e-js-beta-prep.md` §3 R1、§4 T4
- 探针：`.jez/artifacts/android-visual-runtime-probe-2026-10-17/research-findings.md`、`probe/build.gradle.kts`
- https://github.com/dokar3/quickjs-kt （tree `a081755bcdb18ee6afa7ffee25b8fa976f06d31f`；README / LICENSE.txt / quickjs/build.gradle.kts / native/CMakeLists.txt / QuickJs.kt / .gitmodules / issues #240）
- https://github.com/bellard/quickjs （子模块 `04be246...`，LICENSE/VERSION）
- https://github.com/cashapp/zipline （README L188-189；native/quickjs-jni.cpp；gradle/libs.versions.toml；releases）
- https://github.com/quickjs-ng/quickjs （v0.17.0；quickjs.h L515/L519/L1191-1193）
- https://github.com/mozilla/rhino （LICENSE.txt；rhino/src/main/java/org/mozilla/javascript/Context.java L2424-2466；it-android/README.md Restrictions）
- https://repo1.maven.org/maven2/io/github/dokar3/quickjs-kt-android/1.0.15/quickjs-kt-android-1.0.15.aar （.so 尺寸/ELF 解析实测对象）
- https://repo1.maven.org/maven2/org/mozilla/rhino/1.9.1/rhino-1.9.1.jar （Content-Length 实测）
- https://dl.google.com/dl/android/maven2/androidx/javascriptengine/javascriptengine/maven-metadata.xml 与 `javascriptengine-1.1.1.aar`（minSdk 26 实测）
- https://developer.android.com/guide/practices/page-sizes （16 KB/RELRO 官方判定，webReader 当日重取）
- https://developer.android.com/jetpack/androidx/releases/javascriptengine （webReader 当日重取）
- https://github.com/android/ndk/releases/tag/r28c （NDK 钉版，沿契约 §2）
