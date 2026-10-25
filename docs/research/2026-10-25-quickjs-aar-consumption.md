# QuickJS AAR 消费研究（R1 → T3，批次 21，2026-10-25）

研究线：R1 QuickJS 消费。服务对象：T3（QuickJS 真接入：AAR 钉版 + 真引擎 VisualExecutionEngine + 沙箱/硬终止/内存仪器化 + issue #240 用例 + 契约 RELRO 回写）。
钉版主会话裁定（2026-10-25，不改）：消费发布 AAR `io.github.dokar3:quickjs-kt-android:1.0.15`，钉版本+构件 sha256 记录+不动态追新；自建 AAR 仅作运行时实测失败回退（判据：任一仪器化门红且归因 AAR 构件）。

所有源码引用均读自钉 commit **a081755bcdb18ee6afa7ffee25b8fa976f06d31f**（gh API 实取原文，非训练记忆）。

---

## 0. 结论速览

1. **a081755 就是 v1.0.15 的 tip，且是 main 最新提交**（release v1.0.15 发布于 2026-09-03T12:39:32Z，a081755 提交于 11:39:23Z，其后 main 无任何提交）。→ 钉 commit 与钉版本是同一件事。
2. **issue #240（evaluate 永不返回）状态 OPEN、零评论、无修复 PR**，在该钉版上必然存在。仪器化用例必须用 watchdog 形状（独立线程 + join 超时），**禁止**写成 `assertFailsWith<QuickJsInterruptedException>` 直跑——那会挂死测试进程。
3. **构件校验链已建立**：Maven Central 对该构件只发 `.sha1`/`.md5` sidecar（无 `.sha256`）；本研究下载 AAR/POM，本地计算 sha256，并与官方 `.sha1` sidecar 双向核对一致。sha256 记录见 §3。
4. **Gradle 消费零额外配置**：AAR 自带 `jni/<abi>/libquickjs.so`，AGP 自动打包；项目无需 packagingOptions/pickFirsts（全仓唯一 quickjs 生产者，grep 实证零现有依赖）；abiFilters {arm64-v8a,x86_64}（批次 20 已落 app/build.gradle.kts:39）会把 AAR 四 ABI 裁成两 ABI。
5. 沙箱 bootstrap = Kotlin 侧 `function()` 注入确定性 PRNG + 首个 `evaluate` 跑封闭脚本（Date 封闭、Math.random 替换、Object.freeze(Math)）。脚本运行时行为**本线未执行**（无引擎运行环境），标 UNVERIFIED，归 T3 设备门首验。

---

## 1. 钉版 API 面（任务一）

### 1.1 钉 commit 身份

- `gh api repos/dokar3/quickjs-kt/commits/a081755` → sha `a081755bcdb18ee6afa7ffee25b8fa976f06d31f`，2026-09-03T11:39:23Z，"Change JDK distribution from adopt to temurin"。
- `gh api repos/dokar3/quickjs-kt/releases` → **v1.0.15 published 2026-09-03T12:39:32Z**（同日、其后 1 小时）。
- `gh api repos/dokar3/quickjs-kt/commits?sha=main` 首条即 `a081755b` → **main 无后续提交，v1.0.15 = 最新发布版 = 钉 commit**。

### 1.2 创建方式与 dispatcher 要求

`quickjs/src/commonMain/kotlin/com/dokar/quickjs/QuickJs.kt`（commonMain expect，行号为本 commit 原文行号）：

- `quickJs { }` DSL（:20-27）：从**当前协程上下文取 CoroutineDispatcher** 作为 job dispatcher；上下文无 dispatcher 时抛 `UnsupportedOperationException`（:21-25）。`quickJs(moduleLoader) { }` 同（:38-48）。
- `quickJs(jobDispatcher) { }`（:55-62）与 `quickJs(jobDispatcher, moduleLoader) { }`（:71-85）：**显式传 dispatcher**，块结束 finally 自动 `close()`。
- `QuickJs.create(jobDispatcher: CoroutineDispatcher): QuickJs`（companion，:314）；`QuickJs.create(jobDispatcher, moduleLoader)`（:324）。@Throws(QuickJsException::class)。
- README Android 用法示例即 `QuickJs.create(Dispatchers.Default)`（README.md:73）或 DSL `quickJs { }`（:64）。

**dispatcher 的真实职责（线程模型核心）**：job dispatcher 只承载**异步绑定 job**（async function 绑定的协程、promise resolve/reject 回调），见 jni 实现 `quickjs/src/jniMain/kotlin/com/dokar/quickjs/QuickJs.jni.kt`:

- `coroutineScope = CoroutineScope(jobDispatcher + exceptionHandler)`（QuickJs.jni.kt:175）。
- `invokeAsyncFunction` 在该 scope 上 `coroutineScope.launch(context = session, start = LAZY)`（:707-744）。
- **JS 同步执行不跑在 job dispatcher 上，而是跑在 `evaluate()` 调用者线程**：`evalAndAwait` → `jsMutex.withLock { … evalBlock() }`，evalBlock 直接调 JNI `evaluate(context, globals, filename, code, asModule)`（:406-437, :448-450, :990-996）。`jsMutex`（:180）保证同一实例同时只有一个 JS 执行。
- Android 专属差异只有原生库装载：companion init `NativeLibraryLoader.loadLibrary("quickjs")`（QuickJs.jni.kt:1055）→ androidMain 实现 `System.loadLibrary("quickjs")`（`NativeLibraryLoader.android.kt:27`；可用系统属性 `com.dokar.quickjs.library.path`/`name` 覆写为显式 `System.load`，:10-25）。装载器源自 AndroidX 同名文件（文件头注释 :5-6）。

### 1.3 四个配置/控制项确切签名

均见 QuickJs.kt expect 声明 + QuickJs.jni.kt actual 实现：

| 成员 | 签名 | 默认值 | 语义与实现 |
|---|---|---|---|
| memoryLimit | `var memoryLimit: Long`（QuickJs.kt:108） | `-1`（不限，QuickJs.jni.kt:200） | **字节**。setter 在 jsMutex 下同步调 JNI `setMemoryLimit(runtime, globals, value)`（QuickJs.jni.kt:200-207；native 声明 :953）。超限抛 QuickJsException（引擎层 OOM）。 |
| maxStackSize | `var maxStackSize: Long`（QuickJs.kt:113） | `256 * 1024L`（256KB，QuickJs.jni.kt:209） | **字节**。setter 同上（:209-216；native :956）。 |
| evaluationTimeoutMillis | `var evaluationTimeoutMillis: Long`（QuickJs.kt:128） | `-1`（禁用，QuickJs.jni.kt:224） | **每次根 evaluate 的墙钟时限**：evalAndAwait 开始 `resetInterruptState(evaluationTimeoutMillis)`（:414），finally `resetInterruptState(0L)`（:434）。到点由原生 interrupt handler 判定（quickjs_interrupt.c:38-42），抛 `QuickJsInterruptedException`。**只计 JS 执行时间**（QuickJs.kt:126 明文；handler 只在 JS 执行中被采样，时钟为 CLOCK_MONOTONIC，quickjs_interrupt.c:19-27）；async 绑定内等待需调用方自行 `withTimeout {}`。 |
| interruptEvaluation | `fun interruptEvaluation()`（QuickJs.kt:138） | — | **任意线程可调**（QuickJs.kt:133-134 明文；实现 QuickJs.jni.kt:226-231 只取 interruptMutex、不取 jsMutex，JNI `requestInterrupt(interruptState)` :962 置原生 volatile 标志）。无评估运行时为 no-op（:134）。 |

补充（T3 直接要用的语义细节）：

- **取消即中断**：evaluate 协程被 cancel 时通过 `interruptOnCancel.invokeOnCompletion { if (cause is CancellationException) requestInterruptIfOpen() }` 钩到同一原生中断（QuickJs.jni.kt:415-422）。README:190-199 示例 `withTimeout(1000) { evaluate("while(true){}") }`。
- **中断异常类型**：`QuickJsInterruptedException`（QuickJs.jni.kt:430 抛出），**extends QuickJsException，必须先 catch 它**（README:214-215；`QuickJsException.kt` 同 commit 共 96 行）。
- **interrupt 后实例仍可用**：官方测试 `interruptEvaluationStopsInfiniteLoop` 中断后继续 `evaluate<Int>("1 + 1") == 2`（EvaluationInterruptTest.kt:72-91）。timeout 中断同理（:33-48）。
- **close() 线程模型**：任意线程可调；先 `requestInterruptIfOpen()` 再抢 jsMutex 释放原生资源（QuickJs.jni.kt:499-555）；在绑定回调内 close 会 qjsError（:500-502）。
- **内存仪器化**：`val memoryUsage: MemoryUsage`（QuickJs.kt:118；实现 QuickJs.jni.kt:218-222，JNI :971）。`MemoryUsage` 26 字段（MemoryUsage.kt:6-33），关键：`memoryUsedSize`（引擎堆现用字节）、`mallocSize/mallocCount`、`objCount/objSize`、`strCount/strSize`、`atomCount`。配合 `gc()`（QuickJs.kt:291）与 memoryLimit 即可落契约 §4 的 16 MiB 堆预算观测。

### 1.4 原生中断层语义（issue #240 判读所需）

`quickjs/native/common/quickjs_interrupt.c`（本 commit 实读）：

- 状态三字段 `requested / fired / deadline_ms`（:12-17），handler 由 QuickJS 在 JS 执行中周期调用（:29-44）。
- `requested` 置位后 handler 返回 1 中止当前 JS 执行并置 `fired=1`，**但不清 `requested`**（:34-37）；唯一清零点是 `qjs_interrupt_reset`（:66-73，即 Kotlin 侧每次根 evaluate 的开头/结尾）。
- deadline 分支同 handler（:38-42）。
- 推论（结构事实，供 §2 使用）：一次 `interruptEvaluation()` 的 `requested=1` 在整个根 evaluate 期间持续有效，会逐个打断后续每个 pending job 的 JS 执行；它**不能**阻止 JS 层"先排队再被打断"的模式，也不能让 job 队列排水循环退出。

### 1.5 官方中断测试形状（T3 仪器化模板）

`quickjs/src/commonTest/kotlin/com/dokar/quickjs/test/EvaluationInterruptTest.kt`（commonTest，本 commit 实读）四例：

1. `withTimeoutInterruptsInfiniteLoop`（:21-30）：`withTimeout(500){ evaluate<Unit>("while(true){}") }` → TimeoutCancellationException；随后同实例 `evaluate<Int>("1 + 1")==2`。
2. `evaluationTimeoutInterruptsInfiniteLoop`（:33-48）：`evaluationTimeoutMillis = 500`；快脚本先过、死循环抛 QuickJsInterruptedException、同实例可复用。
3. `cancellingCoroutineInterruptsInfiniteLoop`（:51-69）：跨协程 cancel；`function("started")` 同步屏障 + `withTimeout(5_000){ evalJob.join() }` watchdog。
4. `interruptEvaluationStopsInfiniteLoop`（:72-91）：另一协程调 `interruptEvaluation()`；同上 watchdog；中断后引擎可复用。

四例覆盖死循环/超时/取消/手动中断，**均不覆盖自续 job 队列**（#240）——上游测试盲区即本研究 §2。

---

## 2. issue #240 全文、修复状态与仪器化复现形状（任务二）

### 2.1 原文事实（`gh issue view 240 --repo dokar3/quickjs-kt` 实取）

- 标题："evaluate() never returns when a job keeps queuing the next one, even with interruptEvaluation()"
- 状态：**OPEN**；创建 2026-09-26T08:42:29Z；closedAt=null；labels=[]；**comments=[]（零评论）**。
- 正文要点：在 **1.0.15** 上遇到挂死。"若一个 job 先排队下一个 job 再自旋，`evaluate()` 永不返回，即使另一线程反复调 `interruptEvaluation()`。每个 job 确实被打断，但都在它已排队下一个 job 之后，`awaitEvaluateResult` 的 `while (executePendingJob(...))` 循环于是持续进行。它全程持有 `jsMutex`，其他线程的 `close()` 和 `memoryLimit` setter 也跟着自旋，该线程基本丢死。"

作者最小复现（issue 原文逐字）：

```kotlin
val js = QuickJs.create(Dispatchers.Default)
thread(isDaemon = true) {
    Thread.sleep(300)
    while (true) {
        js.interruptEvaluation()
        Thread.sleep(50)
    }
}
runBlocking {
    // never returns
    js.evaluate<Any?>("function f() { Promise.resolve().then(f); while (true) {} }\nf();")
}
```

作者建议修复方向（未实施）：在排水循环的 job 之间检查 interrupt（或 isClosed），置位后让 evaluate 以 QuickJsInterruptedException 失败。

### 2.2 修复状态核实

- issue OPEN、零评论、无关联 PR（gh 实取）。
- main 在钉 commit 后**无任何提交**（§1.1）→ 截至 2026-10-25 无修复。
- **结论：#240 在 v1.0.15 / a081755 上为已知存活缺陷。** 引用：https://github.com/dokar3/quickjs-kt/issues/240

### 2.3 结构核（钉版源码逐行对应，本研究验证）

issue 诊断与 a081755 源码三处结构完全吻合：

1. **排水循环无中断/isClosed 检查**：`awaitEvaluateResult` 的内层 `do { executePendingJob(context, globals); … } while (executed)`（QuickJs.jni.kt:589-592）在 jsMutex 内连续排空 job 队列；isClosed 检查（:586）只在外层 while 每轮一次，内层永不退出则永远到不了。
2. **全程持 jsMutex**：排水在 `jsMutex.withLock { … }` 内（:585）；`close()`（:515）与 `memoryLimit` setter（:202）都要拿同一把锁 → 挂死传染。
3. **中断标志不排空队列**：`requested` 置位持续整个根 evaluate（quickjs_interrupt.c:34-37, 66-73；Kotlin 侧只在根 evaluate 首尾 reset，QuickJs.jni.kt:414/434）→ 每个 job 在自旋点被打断（此时它已排队下一个 f），队列永不空。**根 evaluate 的异常只被记录进 evaluation.exception（:822-832、:460-461），因排水不退出而永远到不了 handleException（:626-645）**。
4. **evaluationTimeoutMillis 不能兜底 #240**（推论，标注为本线推理）：deadline 分支走同一 handler（quickjs_interrupt.c:38-42），过线后每个 f 依旧"先 `Promise.resolve().then(f)` 再在自旋点被打断"，队列仍不空，排水循环照样无限。唯一可靠缓解 = 上游修复 / 不让不可信脚本自续（无法静态保证）/ 引擎每请求一实例 + 独立线程 watchdog，把爆炸半径限为一条泄漏的 daemon 线程和一份未释放 runtime（accept-the-leak，登记风险台账）。

### 2.4 仪器化用例形状（写给 T3）

硬约束：在 1.0.15 上该场景**必然挂死且 close() 也挂死**（§2.3.2）→ 用例不得直跑断言异常，否则整个 androidTest 门被拖死。可行形状（全部要素均出自 §1.5 官方测试惯用法 + issue 复现）：

```kotlin
@Test fun issue240_selfQueuingJobHangs_KnownOpen() {
    val js = QuickJs.create(Dispatchers.Default)
    val done = CountDownLatch(1)
    // evaluate 放独立 daemon 线程（守护：泄漏不拦门）
    val evalThread = thread(isDaemon = true) {
        try {
            js.evaluate<Any?>(
                "function f() { Promise.resolve().then(f); while (true) {} }\nf();"
            )
        } catch (_: QuickJsInterruptedException) {
            // 上游未来修复后会走到这里
        } finally { done.countDown() }
    }
    thread(isDaemon = true) {
        while (!done.await(50, TimeUnit.MILLISECONDS)) js.interruptEvaluation()
    }
    // 观察型断言：v1.0.15 预期 5s 内不返回（issue 实证）；若返回=上游已修，
    // 该用例反转为"必须抛 QuickJsInterruptedException"。
    val returned = done.await(5, TimeUnit.SECONDS)
    // 挂死分支：不要调 js.close()（会挂在 jsMutex 上，§2.3.2）；记录并放行，
    // 泄漏的 daemon 线程随用例进程回收。
}
```

配套建议（T3 落引擎时同步登记）：
- 每张图脚本一个 QuickJs 实例 + evaluate 跑在带超时的独立协程（README:190-199 形状），把 #240 的最坏后果限定为单线程泄漏；
- 用例命名与文档显式标注 `KnownOpen(issue #240 @ 1.0.15)`，上游发修复版（>1.0.15）后按升级批反转断言；
- **未在本线运行该复现**（本线无 Gradle/设备权限，纪律禁跑）；实证来源为 issue 作者单源（主源、当事人），结构前提已在钉版源码逐行核实（§2.3）。

---

## 3. Maven Central 构件事实（任务三）

实取命令：`curl -s https://repo1.maven.org/maven2/io/github/dokar3/quickjs-kt-android/1.0.15/`（目录清单）+ 下载 .aar/.pom/.module/.aar.sha1/.pom.sha1 + 本地 `sha256sum`/`sha1sum` 计算。日期 2026-10-25。

### 3.1 坐标与校验

- 坐标：`io.github.dokar3:quickjs-kt-android:1.0.15`（packaging=aar）。来源三方一致：上游 `gradle.properties`（`GROUP=io.github.dokar3`、`VERSION_NAME=1.0.15`）、README:33、Maven Central 实际目录。
- **Maven Central 只提供 `.md5`/`.sha1` sidecar，无 `.sha256`** → sha256 为本研究本地计算（字节以 sha1 sidecar 锚定）：

| 构件 | sha256（本地计算） | sha1（官方 sidecar == 本地计算，一致） |
|---|---|---|
| quickjs-kt-android-1.0.15.aar（1,828,904 B） | `406c3efb80d5fa3495df4a7f2663be8cc30c974fe5c2368effa929de77cf0ed8` | `500962b09bcfd732aa530b96afe749fba6617a145` |
| quickjs-kt-android-1.0.15.pom（2,540 B） | `cc3ef4b3e8674771ef29d059c28935dd009e6dac7fb90765e497bd8414fe2bbc` | `71a99dbd5bc50f77127a5671eb729048ec0ea9bf` |

- T3 落钉版时把上表 sha256 记入决策/供应链台账；首次解析后可对 Gradle 缓存副本复算比对（未在本线执行 Gradle，属 T3）。
- license：绑定层 **Apache-2.0**（上游 LICENSE.txt = Apache License 2.0；POM `<license>` 同；gradle.properties `POM_LICENSE_NAME` 同）。AAR 内原生引擎为 git 子模块 **bellard/quickjs**（.gitmodules 实取：`url = https://github.com/bellard/quickjs.git`，另有 eteran/c-vector）——契约 :21"保留 MIT/QuickJS 许可证"指该引擎，本研究确认绑定层与其为两份独立许可。

### 3.2 POM 传递依赖（pom 实文）

| 依赖 | 版本 | scope |
|---|---|---|
| org.jetbrains.kotlin:kotlin-stdlib | 2.4.10 | compile |
| org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm | 1.11.0 | runtime（exclusions: kotlin-stdlib-common、kotlin-test-common、kotlin-test-annotations-common） |

`.module`（Gradle 实际消费，`published-with-gradle-metadata`）release 变体依赖同上（kotlinx-coroutines-core 按 KMP 变体解析到 jvm）。**与本项目版本对照**：项目 coroutines = 1.11.0（gradle/libs.versions.toml:10）**完全同版**；项目 kotlin = 2.3.21（:3），AAR 编译于 kotlin 2.4.10（上游 gradle/libs.versions.toml `kotlin = "2.4.10"`）→ Gradle 会把 kotlin-stdlib 解析到 2.4.10（取高），kotlinc 2.3.21 读 2.4 元数据预期为兼容性 warning 级（Kotlin 惯例 N 编译器可读 N+1 元数据）——**标 UNVERIFIED，T3 首次构建观察**，若报错回退选项为升项目 kotlin 或钉 stdlib 2.3.21（届时先裁后改）。

### 3.3 AAR 内 so 清单（unzip -l 实测）

| AAR 内路径 | 字节数 |
|---|---|
| jni/arm64-v8a/libquickjs.so | 924,936 |
| jni/armeabi-v7a/libquickjs.so | 656,988 |
| jni/x86/libquickjs.so | 879,640 |
| jni/x86_64/libquickjs.so | 911,248 |

- 每 ABI 一枚 `libquickjs.so`，**无 `libc++_shared.so`**（libc++ 静态链接）→ 无跨库 so 冲突面。
- 其余内容：`classes.jar`（191,578 B）、`R.txt`、`proguard.txt`、`AndroidManifest.xml`、`META-INF/com/android/build/gradle/aar-metadata.properties`。
- **aar-metadata：`minCompileSdk=36`**（aarMetadataVersion 1.0，无 desugaring 要求）。本项目 compileSdk=37（app/build.gradle.kts:9 等）→ 满足；AGP 会在低于 36 时显式报错。
- **AndroidManifest：package `com.dokar.quickjs`，minSdkVersion=23**，无组件/权限 → manifest 合并无事。本项目 minSdk=23（app/build.gradle.kts:21）恰好一致。
- **proguard.txt（AAR 自带 consumer rules，自动生效）**：keep `com.dokar.quickjs.QuickJs`、`QuickJsException`、`binding.JsProperty`、`binding.JsFunction`、`binding.JsObject`、`kotlin.UByteArray`（JNI 反射触达面）。我们自己的绑定回调用 lambda（无反射绑定类）则无需追加；若用 README:120-149 的 Reflection 绑定（JVM only）才需自写 keep 规则——本项目用不到。
- 16KB/RELRO：四 ABI 全合规（PT_LOAD 16384 + RELRO 独占 LOAD 官方豁免），批次 20 R4 实测（docs/research/2026-10-25-16kb-arm64-api23-build.md:126-132；官方判据引 https://developer.android.com/guide/practices/page-sizes ，豁免原文见该文档 §1.5）→ 契约 docs/current/visual-generation-contract.md:22 的"GNU_RELRO 结束未对齐=阻断门"旧判据按豁免改写即 T3 动作，本研究确认改写依据成立。

---

## 4. 沙箱 bootstrap 配方（任务四）

库 API 依据（commonMain，本 commit 实读）：

- `QuickJs.function(name) { args -> R }`：定义挂 globalThis 的函数绑定（Bindings.dsl.kt:24-36）。
- `QuickJs.define(name) { … }`：定义对象，scope 内 `property(name) { getter { … } / setter { … } }` 且**描述符可配 `configurable/writable/enumerable`**（Bindings.dsl.kt:79-126）——需要"不可改写"的宿主对象时用它，而不是靠 JS freeze。
- `evaluate<T>(code, filename="main.js", asModule=false)`（QuickJs.kt:282-286）：非模块模式全局求值，同一实例的多次 evaluate 共享同一 context → bootstrap 脚本先跑一次即对后续所有 evaluate 生效。
- 库**没有**删除/重定义已有全局的专门 API（QuickJs.kt 全文无此能力）→ 封闭内建只能靠 JS bootstrap（或 defineBinding 覆盖，行为依赖引擎 defineProperty 对 configurable 内建的处理，更绕不取）。

### 4.1 配方（Kotlin 侧 + JS bootstrap）

```kotlin
// 1) 实例创建后、任何用户图代码之前：
quickJs(Dispatchers.Default) {
    // 确定性 PRNG 源（种子派生自 figure requestId，保证重放逐位一致）
    function("__ktRandomNext") { _ -> prng.nextDouble() }   // Bindings.dsl.kt:24 形状
    memoryLimit = 16L * 1024 * 1024        // QuickJs.kt:108，契约 §4 堆预算
    maxStackSize = 256L * 1024             // QuickJs.kt:113（默认即此，显式落码）
    evaluationTimeoutMillis = 2_000L       // QuickJs.kt:128（只计 JS 时间，§1.3）
    // 2) 封闭脚本（asModule=false，全局生效一次）
    evaluate<Unit>(
        """
        globalThis.Date = undefined;            // 封闭 Date（无时间源）
        Math.random = function () { return __ktRandomNext(); };  // 确定性化
        Object.freeze(Math);                    // 防脚本改回 Math.random
        """.trimIndent()
    )
    // 3) 此后每次 evaluate 用户程序
}
```

要点与边界：

- **确定性保证链**：`Math.random` → Kotlin PRNG（种子入账）→ 同种子重放同序列；`Date` 封闭后脚本无法取墙钟。二者合并即消除脚本的两大非确定源；其余非确定源（迭代顺序等）由引擎保证。
- `Date` 封闭选 `= undefined`（而非 delete）：引用即 TypeError，语义显式；若 T3 发现图程序需要纯历法（无墙钟），可换成注入确定性 `Date.now` 的同配方（先裁后写）。
- `Object.freeze(Math)` 冻结后脚本不能改回 `Math.random`；局部作用域 shadow（`let random = …`）不受影响也无害（不逃逸）。
- 兜底内存门：memoryLimit 超限由引擎层抛 QuickJsException；观测面用 `memoryUsage.memoryUsedSize`（§1.3）。
- **UNVERIFIED（本线未执行，归 T3 设备门首验）**：① 覆盖内建（`globalThis.Date`/`Math.random` 赋值 + freeze）在 QuickJS 1.0.15 原生层的实际行为（按 ES 规范内建属性 writable+configurable，赋值应成功，但未实测）；② 封闭脚本与 Kotlin 绑定的 Double↔number 映射（builtinTypes 转换器，QuickJs.kt:165 addTypeConverters 面）。
- 协作中断语义提醒：async 绑定里的等待不消耗 evaluationTimeoutMillis（§1.3），本沙箱建议**不定义 async 绑定**（图程序纯同步计算），则 withTimeout+cancel（QuickJs.jni.kt:415-422）即完整硬终止链，叠加 #240 的每请求一实例隔离（§2.4）。

---

## 5. Gradle 消费形态（任务五）

### 5.1 依赖声明与 so 打包

```kotlin
// 目标模块（core/data 或 app，T3 定）build.gradle.kts
dependencies {
    implementation("io.github.dokar3:quickjs-kt-android:1.0.15")  // 钉版，sha256 见 §3.1
}
```

- **无需任何 packagingOptions/pickFirsts**。依据：AAR 内 so 为普通 `jni/<abi>/libquickjs.so`（§3.3），AGP 合并 jniLibs 时自动打包；pickFirsts 只在**两个依赖携带同名 so** 时需要——全仓 grep（*.kts/*.toml/*.kt，app/core/feature/gradle）确认本项目当前**零 quickjs 依赖**（仅注释/测试提及，`core/domain/src/main/kotlin/.../VisualExecutionEngine.kt:14` 等），AAR 是唯一 `libquickjs.so` 生产者；其自身无 `libc++_shared.so`，与 LiteRT/ML Kit 的 so 无同名交叠。
- 现状 `app/build.gradle.kts:93-95` packaging 块只有 `resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"`，**不需要为 quickjs 增行**。
- **abiFilters 交互**：AAR 携带四 ABI（§3.3），项目 defaultConfig `ndk { abiFilters += setOf("arm64-v8a", "x86_64") }`（app/build.gradle.kts:39，批次 20 已落）→ 打包只出 arm64-v8a + x86_64 两枚 so，armeabi-v7a/x86 被裁（主会话裁定 3 维持）。
- 16KB 对齐由 R4 静态实测背书（§3.3 末条）；**运行时装载归 T6/T3 设备轨（本线未运行）**。

### 5.2 测试类路径（README:42-56，Android 特有项）

- 桌面 JVM（本地 unit test）**不能**装载 Android 原生库 → 若某模块本地单测类路径进了 quickjs-kt-android，需按 README:48-53 做 UnitTestRuntimeClasspath 替换为 `io.github.dokar3:quickjs-kt-jvm:1.0.15`。
- **仪器化测试（androidTest）无需任何替换**（README:56）——本仓六个 androidTest 门跑在设备/模拟器上，直接消费 AAR。
- T3 落地时二选一：QuickJS 依赖不进任何本地单测类路径（推荐，最小面），或按需加替换块。

### 5.3 版本矩阵核对表

| 项 | AAR 要求 | 本项目 | 判定 |
|---|---|---|---|
| minCompileSdk | 36（aar-metadata） | 37（app/build.gradle.kts:9） | OK |
| minSdk | 23（AAR manifest） | 23（app/build.gradle.kts:21） | OK（恰好一致） |
| kotlinx-coroutines | 1.11.0（POM） | 1.11.0（libs.versions.toml:10） | 完全同版 |
| kotlin-stdlib | 2.4.10（POM） | kotlinc 2.3.21（libs.versions.toml:3） | stdlib 解析到 2.4.10；**UNVERIFIED：首 build 观察 metadata warning** |
| Android Gradle Plugin | ≥1.0.0（aar-metadata） | 9.3.0（libs.versions.toml:2） | OK |
| proguard/R8 | AAR 自带 consumer rules（§3.2） | R8 门 | 自动生效，无需追加 |

---

## 6. 给 T3 的映射与未验证清单

映射：§1.2→引擎创建/接线；§1.3→沙箱三参数与 interrupt 接线（VisualExecutionEngine 协作中断=QuickJsInterruptedException→FakeVisualExecutionEngine.kt:171 的对应真身）；§1.5→硬终止/超时仪器化模板；§2.4→issue #240 用例；§3.1→供应链台账（钉版+sha256）；§3.3→契约 :22 RELRO 回写依据；§4→沙箱 bootstrap；§5→build.gradle 落法。

未验证（本研究明确不背书，逐项待 T3/后续批实证）：
1. 沙箱 bootstrap 脚本运行时行为（Date 封闭/Math.random 替换/freeze 生效）——T3 设备门首验（§4.1）。
2. issue #240 复现未在本线运行（无 Gradle/设备，纪律禁跑）；挂死实证为 issue 作者单源（主源当事人），结构前提已在钉版源码逐行核实（§2.3）。
3. kotlin-stdlib 2.4.10 × kotlinc 2.3.21 组合的首 build 行为（预期 warning 级）（§3.2）。
4. AAR/POM sha256 为本地自算（上游无 .sha256 sidecar）；T3 解析后对 Gradle 缓存副本复算比对（§3.1）。
5. 16KB 16K 页设备真装载、x86_64 .so 运行时行为、R8 后 JNI 面——静态合规已核，运行时归设备轨（§5.1）。

---

## 7. 来源清单

上游源码/元数据（gh API @ a081755bcdb18ee6afa7ffee25b8fa976f06d31f 实读）：
- https://github.com/dokar3/quickjs-kt/tree/a081755bcdb18ee6afa7ffee25b8fa976f06d31f （钉版树；commit/releases/commits 由 gh api 实取）
- https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/src/commonMain/kotlin/com/dokar/quickjs/QuickJs.kt
- https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/src/jniMain/kotlin/com/dokar/quickjs/QuickJs.jni.kt
- https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/native/common/quickjs_interrupt.c
- https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/src/androidMain/kotlin/com/dokar/quickjs/NativeLibraryLoader.android.kt
- https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/src/commonMain/kotlin/com/dokar/quickjs/binding/Bindings.dsl.kt
- https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/src/commonTest/kotlin/com/dokar/quickjs/test/EvaluationInterruptTest.kt
- https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/src/commonMain/kotlin/com/dokar/quickjs/MemoryUsage.kt
- https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/README.md
- https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/.gitmodules 、 …/gradle.properties 、 …/LICENSE.txt
- https://github.com/dokar3/quickjs-kt/issues/240 （OPEN、零评论，2026-10-25 实取）

Maven Central（2026-10-25 下载+本地校验）：
- https://repo1.maven.org/maven2/io/github/dokar3/quickjs-kt-android/1.0.15/ （.aar/.pom/.module/.sha1 sidecar）

官方页size判据（经批次 20 R4 全文抓取）：
- https://developer.android.com/guide/practices/page-sizes

仓内前置研究（本批只读引用）：
- docs/research/2026-10-25-16kb-arm64-api23-build.md（批次 20 R4：AAR 四 ABI 16KB/RELRO 实测与官方豁免判据）
- docs/current/visual-generation-contract.md（:21-24 选型依据与 RELRO 回写点）
- core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt（引擎接口，T3 落真身处）
- app/build.gradle.kts、gradle/libs.versions.toml（本项目构建事实，§5.3）
