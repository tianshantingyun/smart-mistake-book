# QuickJS memoryLimit / OOM 语义与确定性探针（R3，批次 22）

- 决策/研究日期：2026-10-26
- 适用：KD-70（QuickJS 内存上限路径未达）、批次 22 T2（OOM 探针与分类修复）、4E.2 隔离进程前置
- 状态：**源码与既有设备日志级研究完成；本轮未运行 Gradle / 设备轨**（探针与设备命令见 §7）。凡"预期"均按源码推导，未标为已通过。

---

## 0. 结论（TL;DR）

1. **memoryLimit 是运行时分配器字节计数上限，不是 JS 对象图大小。** bellard QuickJS 的 `JS_SetMemoryLimit(rt, limit)` 只写 `malloc_state.malloc_limit`；`js_def_malloc` 用 `malloc_size + size > malloc_limit` 拒绝，`js_def_realloc` 用 `malloc_size + size - old_size > malloc_limit` 拒绝（`quickjs.c:2153-2209`、`:2221-2224`，commit `04be246`）。成功分配按 `usable_size + MALLOC_OVERHEAD` 记账（非 Apple 为 8 字节，`:58-62`、`:2168`），所以 16 MiB 是**近似**硬上限，边缘可能略微超出。
2. **超限抛什么：** `js_malloc/js_mallocz/js_realloc/js_realloc2` 在分配器返回 NULL 时调用 `JS_ThrowOutOfMemory` → `JS_ThrowInternalError("out of memory")` → 普通 `InternalError` Error 对象（可被 JS `try/catch` 捕获；`JS_Throw` 明确把 `current_exception_is_uncatchable` 置 FALSE，`quickjs.c:7352-7360`）。
3. **最关键的反例：** 若**连 Error 对象本身都分配不出来**，`JS_ThrowError2` 改抛 `JS_NULL`（源码注释原文："out of memory: throw JS_NULL to avoid recursing"，`quickjs.c:7638-7655`，`obj = JS_NULL` 在 `:7649`）。quickjs-kt 1.0.15 把 `JS_NULL` 映射成 Java `null`（`mapping/js_value_to_jobject.c:409-411`），Kotlin 侧 `reason.toString()` 把它折叠成**字符串 `"null"`**（`QuickJs.jni.kt:837-843`）。KD-70 设备诊断 `program failed in phase prepare: null` 由此产生——不是"AAR 丢消息"这么笼统，而是 **QuickJS 主动抛 JS_NULL + 库层 `null.toString()`** 的确定性组合。
4. **单字符串上限不是 1 MiB：** `JS_STRING_LEN_MAX = (1 << 30) - 1`（1,073,741,823 个码元，约 1 GiB；`quickjs.c:212`）。rope 拼接阈值是 512 / 8192 / depth 60（`:214-219`），rope 节点只存左右引用和小结构，不立即展平；`s = s + s` 可在**极小堆**下把长度推到 2^30 并抛 `InternalError: string too long`（`js_new_string_rope`，`:4897-4899`）。这正是批次 21 旧夹具红例的机制（设备日志 `step2-quickjs-oom-detail.log` 有 `at prepare (visual-program.js:2:60)` 的 stack 证据）。
5. **在 16 MiB 下"必然先撞堆"的程序**是**平坦内存分配**：单次超大分配（如 64 MiB `ArrayBuffer`），或大量小对象/小字符串的累计，且不通过 rope 把单字符串长度推到 2^30。前者失败时**留有大量余量**，OOM Error 可以构造 → message 可读；后者若失败分配小到 Error 对象放不下，就走 JS_NULL → `"null"`。
6. **探针分层：** P1 单次 64 MiB `ArrayBuffer`（message 可读，验 `MEMORY_LIMIT` 主路径）；P2 小对象链表（无 headroom，确定性复现 `"null"`）；P3 rope 翻倍（`string too long` 负控，证明"字符串上限 ≠ 堆上限"）；P4 `throw null`（低内存下的 `"null"` 负控 → `UNKNOWN`）；P5 `RangeError('out of memory')`（伪造文案负控）。
7. **分类规格：** 类型优先（quickjs-kt 只有 `QuickJsInterruptedException` 可区分）→ 消息严格前缀 `InternalError: out of memory`（不再用宽泛 `contains`）→ 仅对"消息丢失哨兵"（`null`、`"null"`、`Invalid evaluation handle: -1`）读 `memoryUsage`，`mallocSize ≥ 90% mallocLimit` 归 `MEMORY_LIMIT` → 都不确定时 `UNKNOWN` 兜底。取证点在 phase 内、`close()` 前。

---

## 1. 版本、构件与证据等级

| 项 | 值 | 证据 |
|---|---|---|
| quickjs-kt 版本 | **v1.0.15**，tag `a081755bcdb18ee6afa7ffee25b8fa976f06d31f`，发布 2026-09-03 | `gh api repos/dokar3/quickjs-kt/tags`、`.../releases` |
| AAR 坐标 | `io.github.dokar3:quickjs-kt-android:1.0.15`（批次 21 钉版） | 既有研究 `docs/research/2026-10-25-quickjs-aar-consumption.md` |
| 内置 QuickJS | **bellard/quickjs**，子模块 commit `04be246001599f5995fa2f2d8c91a0f198d3f34c`（2026-06-16） | `.gitmodules`（`url = https://github.com/bellard/quickjs.git`）+ tree 子模块 SHA |
| 引擎 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt` | 本会话通读 |
| 仪器化用例 | `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineInstrumentedTest.kt` | 本会话通读 |
| 既有设备日志 | `build/device-track-batch21/step2-quickjs-oom-detail.log`、`step2-oom-attribution.log` | 本会话实读 |

**证据等级说明：**

- **源码级（高置信）**：以上游 tag 的源码逐行读取为准；AAR 二进制**未反编译**，按"发布 tag 与 AAR 同源"的既有钉版证据使用。若 AAR 与 tag 不一致，§3 的 JNI/Kotlin 结论需要重核（列入 §7 未验证项）。
- **设备日志级（中置信）**：批次 21 设备轨只观察到两种失败形态（`string too long`、`"null"`），**没有**观察到 message 可读的 OOM；本研究的 P1 预测尚未设备验证。
- **推断（待设备验证）**：所有"P1/P2 预期形态"与 90% 阈值。

**本会话实际执行的命令（摘要）：**

```text
gh api repos/dokar3/quickjs-kt/git/trees/a081755...?recursive=1 --jq '.tree[].path'
gh api repos/dokar3/quickjs-kt/contents/.../{QuickJsException.kt,QuickJs.jni.kt,MemoryUsage.kt,README.md}?ref=a081755...
gh api repos/dokar3/quickjs-kt/contents/quickjs/native/jni/{quickjs_jni.c,exception_util.c,js_value_util.c,promise_rejection_handler.c}?ref=a081755...
gh api repos/dokar3/quickjs-kt/contents/quickjs/native/jni/mapping/js_value_to_jobject.c?ref=a081755...
gh api -H "Accept: application/vnd.github.raw" repos/bellard/quickjs/contents/{quickjs.c,quickjs.h,doc/quickjs.texi,Makefile}?ref=04be246...
gh api -H "Accept: application/vnd.github.raw" repos/quickjs-ng/quickjs/contents/quickjs.c?ref=master
gh issue view 798/1773/1181/468 --repo ...
gh search issues --repo dokar3/quickjs-kt "{OOM|out of memory|memoryLimit}"
git log/show（只读）核对批次 21 测试历史
cat build/device-track-batch21/step2-quickjs-oom-detail.log
cat build/device-track-batch21/step2-oom-attribution.log
```

**未执行**：任何 Gradle 命令、任何 `adb` / 设备命令（本任务纪律禁止）。设备轨命令只在 §7 给出，供 T2/设备轨执行。

---

## 2. 上游语义

### 2.1 bellard QuickJS（AAR 实际内置的引擎）

#### 2.1.1 上限如何生效

```c
static void *js_def_malloc(JSMallocState *s, size_t size)
{
    if (unlikely(s->malloc_size + size > s->malloc_limit))
        return NULL;
    ptr = malloc(size);
    if (!ptr) return NULL;
    s->malloc_count++;
    s->malloc_size += js_def_malloc_usable_size(ptr) + MALLOC_OVERHEAD;
    return ptr;
}
```
（`quickjs.c:2153-2171`）

```c
static void *js_def_realloc(JSMallocState *s, void *ptr, size_t size)
{
    old_size = js_def_malloc_usable_size(ptr);
    if (s->malloc_size + size - old_size > s->malloc_limit)
        return NULL;
    ptr = realloc(ptr, size);
    ...
    s->malloc_size += js_def_malloc_usable_size(ptr) - old_size;
}
```
（`quickjs.c:2182-2209`）

- `JS_SetMemoryLimit(rt, limit)` 只是 `rt->malloc_ctx.malloc_state.malloc_limit = limit;`（`:2221-2224`）。默认 `malloc_limit = -1`（`:2074`），即不限。
- **检查用请求字节，记账用 `usable_size + 8`**（非 Apple，`:58-62`、`:2168`）。因此一个"通过检查"的分配可以按 usable size 把计数推过上限；16 MiB 是近似值，不是精确水位。
- **只统计 runtime 分配器**：Java/JNI 侧的源码、JSON、Java 字符串、线程栈都不计入。引擎的 64 KiB 源码、16 KiB state 由宿主另设预算。
- **不触发 GC**：`JS_SetMemoryLimit` 不做 GC；GC 由 `js_trigger_gc` 在对象分配时按 `malloc_gc_threshold`（初始 256 KiB，成倍/1.5 倍增长）触发（`:1780-1799`、`:2083`、`:5613-5619`）。P2 的链表对象全部可达，GC 不会腾出空间。
- **可被 JS 捕获**：OOM 是普通 Error；`JS_Throw` 把 `current_exception_is_uncatchable` 明确置 FALSE（`:7352-7360`）。quickjs-ng #1773 的实测表也显示 memoryLimit 拒绝在同步/异步各路径都可 `catch` 并继续（见 §2.2）。

#### 2.1.2 超限抛什么

```c
void *js_malloc(JSContext *ctx, size_t size)
{
    ptr = js_malloc_rt(ctx->rt, size);
    if (unlikely(!ptr)) {
        JS_ThrowOutOfMemory(ctx);
        return NULL;
    }
    return ptr;
}
```
（`quickjs.c:1832-1841`；`js_mallocz`、`js_realloc`、`js_realloc2` 同型，`:1843-1878`）

```c
JSValue JS_ThrowOutOfMemory(JSContext *ctx)
{
    JSRuntime *rt = ctx->rt;
    if (!rt->in_out_of_memory) {
        rt->in_out_of_memory = TRUE;
        JS_ThrowInternalError(ctx, "out of memory");
        rt->in_out_of_memory = FALSE;
    }
    return JS_EXCEPTION;
}
```
（`quickjs.c:7778-7787`）

```c
static JSValue JS_ThrowError2(...)
{
    vsnprintf(buf, sizeof(buf), fmt, ap);
    obj = JS_NewObjectProtoClass(ctx, ctx->native_error_proto[error_num], JS_CLASS_ERROR);
    if (unlikely(JS_IsException(obj))) {
        /* out of memory: throw JS_NULL to avoid recursing */
        obj = JS_NULL;
    } else {
        JS_DefinePropertyValue(ctx, obj, JS_ATOM_message,
                               JS_NewString(ctx, buf), ...);
        if (add_backtrace) { build_backtrace(...); }
    }
    ret = JS_Throw(ctx, obj);
    return ret;
}
```
（`quickjs.c:7638-7658`）

**两种形态，取决于"余量够不够构造 Error 对象"：**

| 情形 | JS 侧 pending exception | 消息 |
|---|---|---|
| 分配失败但仍有足够余量构造 Error | `InternalError` 对象 | `out of memory` |
| 分配失败且 Error 对象也构造失败 | `JS_NULL` | **无消息**（库层表现为 `null`） |

补充：
- `JS_ThrowOutOfMemory` 有 `in_out_of_memory` 重入守卫；重入时直接返回 `JS_EXCEPTION`、不覆盖 pending。正常用户代码路径不会在守卫窗口内分配，outer `JS_ThrowError2` 最终仍会落 `JS_NULL`。
- OOM 抛出时 `rt->in_out_of_memory == TRUE`，`JS_ThrowError` 因此**不建 backtrace**（`:7666-7673`）；但异常向上穿过 JS 栈帧时，`is_backtrace_needed`/`build_backtrace` 可能补栈（`:20660-20670`）。有 headroom 时能补，无 headroom 时 pending 是 `JS_NULL`，不会尝试。

#### 2.1.3 16 MiB 的基线

上下文与内置对象在 `JS_SetMemoryLimit` 之前创建，但其分配已计入 `malloc_size`；设置后这些内存仍在计数里。正常工程内 QuickJS runtime+context 基线远小于 16 MiB（批次 21 设备 12/13 方法绿即证明基线未把预算吃穿）。精确基线只能设备读 `memoryUsage.mallocSize`（§3.2）。

#### 2.1.4 `memoryUsage` 观测面

`JS_ComputeMemoryUsage(rt, &s)` 直接复制 `malloc_count`、`malloc_size`、`malloc_limit`（`quickjs.c:6928-6938`），其余字段遍历对象图统计；**这些字段的读取不依赖分配**（函数体只累加、不 malloc；会分配的是 `JS_DumpMemoryUsage`，`quickjs.c:7224` 起，不是这里）。quickjs-kt 暴露为：

- `QuickJs.memoryLimit: Long`（setter 经 `jsMutex` 同步调 JNI `setMemoryLimit`，`QuickJs.jni.kt:200-206`；JNI `quickjs_jni.c:403-412`）；
- `QuickJs.memoryUsage: MemoryUsage`（getter 同样持 `jsMutex`，`QuickJs.jni.kt:218-222`；JNI `quickjs_jni.c:494-530`）；
- `MemoryUsage.mallocSize`（memoryLimit 检查使用的同一计数器）、`mallocLimit`、`memoryUsedSize` 等 26 字段（`MemoryUsage.kt:6-33`）。

---

### 2.2 quickjs-ng 对照（不是 AAR 的引擎，只作交叉验证）

| 项 | bellard `04be246` | quickjs-ng `master`（2026-10-24） |
|---|---|---|
| "不限"的表示 | `malloc_limit = -1`（`:2074`） | `malloc_limit = 0`，检查用 `limit - 1`（`:1991-1992`、`:2318`） |
| malloc 检查 | `malloc_size + size > limit` | `... > limit - 1`（`:2014-2015`） |
| realloc 检查 | `malloc_size + size - old_size > limit` | `... > limit - 1`（`:2061-2062`） |
| Error 构造失败 | `JS_NULL`（`:7638-7658`，`:7649`） | `JS_NULL`（`:8444-8452`，源码注释同） |
| stack overflow | `InternalError("stack overflow")`（`:7789-7792`） | `RangeError("Maximum call stack size exceeded")`（`:8576`） |
| `repeat` 超长 | `RangeError("invalid string length")`（`:46395`） | `RangeError("string too long")`（`:48101-48102`） |
| 配置上限下是否可终止执行 | 否，普通可捕获 Error | 否；#1773 明确"opt-in terminal semantics"尚未实现 |

关键 issue 引证：

- **quickjs-ng #798**（closed）"Internal OOM exception broken in 0.8.0"：作者用 100,000 字节上限测 `'abc'.repeat(200_000)`，0.5.0 的异常 message 是 `"out of memory"`，0.8.0 变成 `null`。根因是 `JS_MakeError → JS_NewObjectProtoClass → js_new_shape` 在无内存时失败，且未被 `JS_MakeError` 处理；维护者答复"限制太低，连报告错误的内存都没有；把上限提高到 150,000 后正常"。**这就是"消息可读性取决于余量"的上游确认。**
- **quickjs-ng #1773**（open，设计讨论）"Discuss opt-in terminal semantics for configured memory limits"：作者实测 **只剩 1 字节余量时，同步 memoryLimit 拒绝会 `catch` 到 `null` 并继续**；各 Promise 路径同样可捕获；提议一个 opt-in 的"终端内存限制"契约（host 可识别、不可被 JS 捕获），当前**未实现**。同一 issue 明确指出 `JS_ComputeMemoryUsage` 不是"失败原因查询"。
- **quickjs-ng #1181**（closed）：上限低到 context 初始化都不够时 `JS_NewContext` 会崩；说明"低上限"本身有健壮性边界。16 MiB 远高于该区间，但这不是数学保证。
- **quickjs-ng #1813**（closed）：讨论"InternalError 是否应不可捕获"时，维护者点名"string too long 这类或许应该是另一种错误类型"——侧面确认 `string too long` 目前也是 `InternalError`，**不能只凭 `InternalError` 前缀判定 OOM**。
- **bellard/quickjs #468**（closed，CVE-2025-69654）：`qjs -m --memory-limit 1M` 先报 `InternalError: out of memory`，随后 runtime 清理断言崩溃；说明极低上限下的 OOM 清理不可靠。16 MiB 正常，但探针不应把上限压到接近基线。

---

### 2.3 可能"先于堆上限"触发的其他错误

在 16 MiB 上限、`maxStackSize = 256 KiB`、`evaluationTimeoutMillis = 100 ms`（引擎默认；OOM 用例放宽到 10 s）下：

| 错误 | 触发条件 | 消息 | 是否堆上限 |
|---|---|---|---|
| `string too long` | 单字符串**长度** > `JS_STRING_LEN_MAX = 2^30 - 1`；rope 拼接只增长度、不立即分配内容，可先于堆上限 | `InternalError: string too long` | **否**（旧夹具红例） |
| `invalid string length` / `string too long` | `String.prototype.repeat/padStart` 等长度检查超 `2^30-1`（bellard 用 `invalid string length`，ng 的 repeat 用 `string too long`） | `RangeError: ...` | 否 |
| `stack overflow` | 递归超过 256 KiB 栈 | bellard `InternalError: stack overflow`；ng `RangeError: Maximum call stack size exceeded` | 否（独立栈预算） |
| `invalid array buffer length` | `new ArrayBuffer(len)` 且 `len > INT32_MAX`（`quickjs.c:56764`）；在真正分配前就抛 | `RangeError` | 否 |
| `invalid array length` | 数组长度非法（如 `:9582`） | `RangeError` | 否 |
| 协作中断 | 死循环 / 单阶段超过 `evaluationTimeoutMillis` | `QuickJsInterruptedException`（Java 层） | 否 |

**关键推导（回答"1 MiB 上限"问题）：** `JS_STRING_LEN_MAX` 是 **2^30-1 ≈ 1 GiB**，不是 1 MiB。rope 拼接的阈值是 512（`JS_STRING_ROPE_SHORT_LEN`）与 8192（`SHORT2_LEN`），depth 60 才重平衡——这些都不是硬上限。因此：

- `s = s + s` 反复翻倍：堆只增加很小的 rope 节点，长度按 2 的幂增长；第 30 次左右长度超过 2^30-1，`js_new_string_rope` 抛 `InternalError: string too long`（`quickjs.c:4897-4899`）。旧夹具在 16 MiB 下正是这样失败的，设备日志的 stack 指向 `visual-program.js:2:60` 的 `+`。
- 要"先撞堆"，必须让**实际字节分配**先于字符串长度上限：单字符串远小于 2^30，且总分配量超过 16 MiB。P1（单次 64 MiB ArrayBuffer）与 P2（大量小对象）满足；P3（翻倍）故意不满足，用作负控。

---

## 3. quickjs-kt 1.0.15（AAR）如何暴露 OOM

### 3.1 异常类型：没有 OOM 专用类型

```kotlin
open class QuickJsException @JvmOverloads constructor(
    override val message: String?,
    val stack: String? = null,
) : Exception() { ... }

class QuickJsInterruptedException(message: String?) : QuickJsException(message)
```
（`QuickJsException.kt:10-13`、`:36-38`）

- 只有 `QuickJsException` 与子类 `QuickJsInterruptedException`；**没有** OOM/内存子类，也没有 error code。
- 所有 JS 错误（SyntaxError/TypeError/ReferenceError/InternalError/用户 throw）都走 `QuickJsException`；库内错误（`Already closed`、`Failed to define object` 等）也是同一类型。
- 库自带测试也只断言类型与消息包含（`EvalErrorsTest.kt:19-106`），没有 OOM 用例。

### 3.2 message 是否保留引擎原文：取决于 Error 对象是否构造成功

`js_error_to_java_error`（`mapping/js_value_to_jobject.c:29-158`）的规则：

1. 读 JS 异常的 `name`。`name` 为 undefined（非 Error 值）时，直接 `JS_ToCString(error)` 再用 `new_qjs_exception("%s", str)`（`:34-44`）。
2. 否则读 `message`（`:71-84`），拼成 `name + ": " + message`，有 stack 时再追加 `"\n" + stack`（`js_error_message_join`，`js_value_util.c:69-113`）。
3. 例外：若 `name` 恰好能 `FindClass` 到 JVM 类，则直接 `new` 那个 Java 类（`:50-106`）；JS 内置名（`InternalError`/`TypeError`/…）在默认包不存在，故实际都落回 `QuickJsException`。

因此：

| JS 侧情形 | JNI/Kotlin 侧结果 | 引擎看到的 `message` |
|---|---|---|
| 正常 Error（含 OOM 有余量时） | `QuickJsException("InternalError: out of memory\n<stack?>", stack)` | **保留原文** |
| Error 对象构造失败 → `JS_NULL` | `js_value_to_jobject` 返回 Java `null`（`:409-411`）；rejection handler 把 `null` 交给 Kotlin；`setUnhandledPromiseRejection` 执行 `reason.toString()` | **`"null"`**（库层合成，不是引擎文案） |
| JS_Eval 返回 `JS_EXCEPTION` 但 pending 是 `JS_NULL`，`store_evaluate_result` 静默返回 -1 | Kotlin 随后以 handle -1 调 `getEvaluateResultPromiseId`，JNI 抛 `Invalid evaluation handle: -1`（`quickjs_jni.c:1077`） | **`Invalid evaluation handle: -1`**（防御路径） |
| 格式化 message 的 malloc 失败 | `throw_qjs_exception_from_utf8` 回退 | `Cannot format QuickJS exception message.`（`exception_util.c:9`、`:34-39`） |

**`"null"` 的精确因果链（源码逐环）：**

1. `JS_ThrowOutOfMemory` → `JS_ThrowInternalError` → `JS_ThrowError2`；`JS_NewObjectProtoClass` 失败 → `JS_Throw(ctx, JS_NULL)`（`quickjs.c:7645-7655`）。
2. promise rejection reason = `JS_NULL`；`promise_rejection_handler` 调用 `js_value_to_jobject`（`promise_rejection_handler.c:16-20`）。
3. `js_value_to_jobject` 对 `JS_TAG_NULL` 直接 `return NULL`（`mapping/js_value_to_jobject.c:409-411`），**不抛 Java 异常**。
4. Kotlin `setUnhandledPromiseRejection(promiseId, reason = null)`：
   ```kotlin
   val exception = reason as? Throwable ?: QuickJsException(reason.toString())
   ```
   `reason.toString()` 在 Kotlin 的可空扩展里对 `null` 返回字符串 `"null"`（`QuickJs.jni.kt:837-843`）。
5. 引擎 `classifyJsException` 走 `else` 分支 → `INVALID_PROGRAM: program failed in phase prepare: null`。与设备实测一字不差。

另外，`getEvaluateResult` 对 rejected promise 有显式保护：

```c
// Avoid resetting jni exception to null
int res_tag = JS_VALUE_GET_TAG(js_result);
if (res_tag != JS_TAG_NULL && res_tag != JS_TAG_UNINITIALIZED) { ... }
```
（`quickjs_jni.c:1151-1180`）

即：JNI 明知 `JS_NULL` 会把 Java 异常"重置为 null"，所以这里不设异常，反而让第 4 步的 `"null"` 成为唯一信号。这是设计使然，不是偶发丢消息。

### 3.3 该库 issue 里的 OOM 讨论

`gh search issues --repo dokar3/quickjs-kt` 的实搜结果（2026-10-26）：

- 关键词 `OOM`：**0 条**；
- 关键词 `out of memory`：**0 条**；
- 关键词 `memoryLimit`：仅 **#240**（"evaluate() never returns when a job keeps queuing the next one…"），是挂死缺陷，与 OOM 语义无关；
- 关键词 `memory`：仅 #96（并发问题）、#25（`_platform_strcmp` 偶发）——均无关。

**结论：** quickjs-kt 上游没有 OOM 专项讨论，也没有文档化 message 契约；OOM 语义只能从内置 QuickJS + JNI 源码推导（本节）。"零命中 ≠ 不存在"：该库 issue 体量小，搜索只代表本会话可见范围。

### 3.4 为什么 `"null"` 不能直接当作 OOM

同一 `QuickJsException("null")` 至少还有三条用户可触发路径：

- `throw null;`（任意 phase 内）→ rejection reason = `JS_NULL` → 同上；
- `Promise.reject(null);`（未处理）→ `awaitEvaluateResult` 把 unhandled rejection 当失败 → 同上；
- `throw 'null';` → reason 是 JS 字符串 `"null"` → `js_value_to_jobject` 返回 Java String → `reason as? Throwable` 为 null → `QuickJsException("null")`。

因此 `"null"` 只能说明"错误对象丢失"，**不能**说明堆耗尽；必须辅以运行时堆证据（§6）。

---

## 4. 既有设备证据复核（批次 21）

### 4.1 旧夹具：`string too long`

`build/device-track-batch21/step2-quickjs-oom-detail.log`（本会话实读，1-6 行）：

```text
java.lang.AssertionError: actual: INVALID_PROGRAM: program failed in phase prepare: InternalError: string too long
    at prepare (visual-program.js:2:60)
    at <eval> (visual-prepare.js:1:30)
 expected:<MEMORY_LIMIT> but was:<INVALID_PROGRAM>
```

- 有 stack、位置在 `visual-program.js:2:60`（`s = s + s` 的 `+`）→ 证明是 rope 长度检查抛的 `InternalError`，不是 OOM。
- 与 §2.3 的机制一致：长度到 2^30 时抛，堆几乎没涨。

### 4.2 归因探针：`"null"`

`build/device-track-batch21/step2-oom-attribution.log`（本会话实读）：

```text
Error in allocationHeavyProgramIsRejectedAsMemoryLimit(...MemoryLimitAttributionProbeInstrumentedTest):
java.lang.AssertionError: expected:<MEMORY_LIMIT> but was:<INVALID_PROGRAM>
```

批次 21 记录 §3.8 第 4 条转录了同一次运行的 logcat：`DIAG allocation-heavy reason=INVALID_PROGRAM diagnostics=program failed in phase prepare: null`。探针源码（`a.push({x:1,y:'abcdefgh'})` 无限增长）已删，只留日志。

- 这与 §3.2 的 JS_NULL 链完全吻合：小对象持续增长，失败分配很小（~一个 JSObject），Error 对象放不下 → `JS_NULL` → `"null"`。
- **注意：** 该探针是 `while (true)`，靠 OOM 自己停止；如果 OOM 分类不生效但上限仍生效，它仍会因 OOM 停止；如果 memoryLimit 完全没设置，它会一直跑到协作超时。用链表 + 有界 for 循环更可控（§5.3）。

### 4.3 当前树上的 `OOM_SOURCE` 并未被设备复跑

当前夹具是 `new Array(1024 * 1024).fill(1)`（测试 `:435-446`）。`Array.prototype.fill` 是**逐元素 `JS_SetPropertyInt64`**（`quickjs.c:42370-42398`），1M 次属性写入会触发 fast-array 扩容；某个扩容 realloc 可能一次要求很大字节数，失败时**余量可能足够构造 OOM Error**，从而得到可读 message，也可能不够，得到 `"null"`。这不是确定性的：结果依赖对象布局、扩容策略、GC 时机。批次 21 记录明确写"未在设备重跑"。因此它既不能证明 OOM 路径，也不能作为 KD-70 的关闭证据。

---

## 5. 确定性探针设计

### 5.1 约束推导

要在 16 MiB 下**必然**先撞堆：

1. 单次分配或累计分配 > 16 MiB（扣掉 ~1 MiB 基线）。
2. 不把任何单字符串的**长度**推到 `2^30-1`；也不做会触发长度检查的 `repeat/padStart`。
3. 分配大小尽量**平坦**：不要依赖 fast-array 翻倍那种"失败请求可能很大"的路径，否则余量大小不可控。
4. 有界循环：即使 memoryLimit 失效（回归/配置错误），探针也要自己结束，不能靠超时兜底。
5. 分配必须**保持可达**：否则 GC 会回收，永远到不了上限。用链表/数组持有引用。
6. 放在 `visual.prepare` 内（引擎的 phase 内），而不是源码顶层：
   - 引擎只对 phase 的失败做统一分类；
   - 顶层失败走 `store_evaluate_result` 的防御路径，可能得到 `Invalid evaluation handle: -1` 而不是 `"null"`（§3.2）。
7. 用例方法名必须 DEX 安全（批次 21 教训：反引号空格名会让 D8 失败），用 camelCase。

### 5.2 P1：单次超大分配（message 可读的 OOM 主路径）

```js
const visual = {
  prepare: function () {
    // 64 MiB 单次分配：> 16 MiB memoryLimit，< INT32_MAX（ArrayBuffer 长度检查），
    // 不涉及任何字符串操作。请求被拒时预算仍剩约 15 MiB，足以构造 OOM Error。
    var buffer = new ArrayBuffer(64 * 1024 * 1024);
    return [];
  },
  render: function () { return {}; },
};
```

- 依据：`js_array_buffer_constructor3` 先做 `len > INT32_MAX` 检查（`quickjs.c:56764`），再 `js_mallocz(ctx, len)`（`:56789`）；64 MiB 通过长度检查，在 `js_def_malloc` 的 `malloc_size + size > limit` 处失败 → `JS_ThrowOutOfMemory` → 有 headroom，Error 构造成功。
- 预期：`Rejected(MEMORY_LIMIT, "js heap limit (16777216 bytes) exceeded in phase prepare: InternalError: out of memory…")`。
- 这是 KD-70 主用例；当前 `classifyJsException` 的 `contains("out of memory")` 就能接住（前提是 message 真的可读——这正是要在设备上证的）。

**P1b（备用，字符串路径但仍是堆上限）：**

```js
var s = 'x'.repeat(64 * 1024 * 1024); // 64 MiB < 2^30-1，长度检查通过；string_buffer_init2 一次性分配整串
```

`js_string_repeat` 先查 `val * len > JS_STRING_LEN_MAX`（`quickjs.c:46395`），64 MiB 通过；`string_buffer_init2` 调 `js_alloc_string(ctx, size, ...)` 一次分配（`:4006-4018`）→ 堆上限失败 → 有 headroom。当 `ArrayBuffer` 因构建配置不可用时用这条。

### 5.3 P2：小对象链表（无 headroom，确定性复现 `"null"`）

```js
const visual = {
  prepare: function () {
    // 每个节点只带一个 next 属性，用链表保持可达。
    // 不增长数组（避免大 realloc 在失败时留下大余量），不产生长字符串/rope，
    // shape 在第一次迭代后缓存，后续每轮只分配 JSObject + 小属性数组。
    var head = null;
    for (var i = 0; i < 500000; i++) {
      head = { next: head };
    }
    return [];
  },
  render: function () { return {}; },
};
```

- 500,000 次 × 至少 ~40 B/节点 = 20 MB > 16 MiB，保证即使单节点极小编码也会超限；若 memoryLimit 失效，循环有界结束（prepare 返回 `[]`，测试会以"成功"红掉，而不是挂死）。
- 每次迭代只分配一个同 shape 的小对象（shape 缓存；1 个属性 → 属性数组初始 `JS_PROP_INITIAL_SIZE = 2`，32 B），没有大 realloc、没有 shape 爆炸、没有 rope。
- **为什么 message 必然丢失（确定性论证）：** 用户失败的那次分配要么是 `js_malloc(ctx, sizeof(JSObject))`（`JS_NewObjectFromShape`，`quickjs.c:5613-5620`），要么是紧随其后的属性数组（2 × `sizeof(JSProperty)`，小于 JSObject）。1 个属性让模板 shape 的 `prop_size` 保持初始 2，所以**每轮最大的一次分配就是 `sizeof(JSObject)`**；失败意味着剩余额度小于它。而构造 OOM Error 至少要同一个 `JSObject` + 它自己的属性数组（≥2 × `sizeof(JSProperty)`）+ message 字符串，门槛严格更高，所以 `JS_NewObjectProtoClass` 必然失败，`JS_ThrowError2` 必然落 `JS_NULL`。这不是概率问题。
- 预期：`QuickJsException` 的 message 为 `"null"`（§3.2），`memoryUsage.mallocSize` 接近 `mallocLimit`。
- 修复前：`Rejected(INVALID_PROGRAM, "program failed in phase prepare: null")`（红）。
- 修复后：`Rejected(MEMORY_LIMIT, "…attributed by memoryUsage…mallocSize=…, mallocLimit=16777216…")`（绿）。

### 5.4 P3：rope 翻倍（`string too long` 负控，防回归）

```js
const visual = {
  prepare: function () {
    var s = 'x';
    for (var i = 0; i < 40; i++) { s = s + s; }
    return [];
  },
  render: function () { return {}; },
};
```

- 第 ~30 次长度超过 2^30-1，`js_new_string_rope` 抛 `InternalError: string too long`；堆只增加几十个 rope 节点，远低于 16 MiB。
- 预期：`Rejected(INVALID_PROGRAM, "InternalError: string too long…")`，且**不得**归为 `MEMORY_LIMIT`。
- 这条直接钉住旧夹具红例的机制；它同时证明"消息里有 `InternalError` 不等于 OOM"（呼应 quickjs-ng #1813）。

### 5.5 P4 / P5：`UNKNOWN` 与伪造文案负控

P4（低内存的 `"null"`，必须落 `UNKNOWN`）：

```js
const visual = {
  prepare: function () { throw null; },
  render: function () { return {}; },
};
```

- message = `"null"`，但 `mallocSize` 很低 → 不得归 `MEMORY_LIMIT`。
- 修复后预期：`Rejected(UNKNOWN, …)`；修复前：`INVALID_PROGRAM`（红，证明兜底生效）。

P5（伪造 OOM 文案，不得误报）：

```js
const visual = {
  prepare: function () { throw new RangeError('out of memory'); },
  render: function () { return {}; },
};
```

- message = `RangeError: out of memory`。旧 `contains("out of memory")` 会误报 `MEMORY_LIMIT`；严格前缀匹配后应为 `INVALID_PROGRAM`。
- 这是一条**行为变更**用例，建议随 T2 一起加；如果裁定不做，至少登记为已知边界。

### 5.6 预期异常形态总表

| 探针 | 失败时是否有 Error 余量 | `QuickJsException.message`（预期） | 目标 reason |
|---|---|---|---|
| P1 64 MiB `ArrayBuffer` | 有（~15 MiB） | `InternalError: out of memory`（可有 stack） | `MEMORY_LIMIT` |
| P1b 64 MiB `repeat` | 有（~15 MiB） | `InternalError: out of memory` | `MEMORY_LIMIT` |
| P2 50 万小对象链表 | 无（< 一个 Error） | `"null"` | `MEMORY_LIMIT`（堆证据归属） |
| P3 rope 翻倍 | 有（堆很低） | `InternalError: string too long` + stack | `INVALID_PROGRAM` |
| P4 `throw null` | 有（堆很低） | `"null"` | `UNKNOWN` |
| P5 `RangeError('out of memory')` | 有（堆很低） | `RangeError: out of memory` | `INVALID_PROGRAM` |
| 顶层 `JS_NULL` 防御路径 | 无 | `Invalid evaluation handle: -1` | 堆证据归属 / `UNKNOWN` |

### 5.7 探针风险与边界

- **OOM 可被 JS 捕获**：P1/P2 不写 `try/catch`，所以异常会传播为 phase 失败。如果有人给夹具加 catch，探针会"成功返回"，测试必须保证没有 catch。
- **GC**：P2 的链表全部可达，GC 不会回收；GC 成本计入 10 s 协作期限，500k 上限在 device 上应远小于该值（未实测）。
- **`memoryUsage` 读取本身**：源码级判断不分配（§2.1.4），但它要拿 `jsMutex`；若 watchdog 正在 `close()`，可能阻塞。规格只在"消息丢失哨兵"路径读，并 `runCatching`（§6.2）。
- **伪造**：用户程序可 `throw new InternalError('out of memory')` 或 `throw 'null'`。严格前缀 + 堆证据能挡住大部分，但不是认证机制；伪造的后果仍只是"有界拒绝被标错类"，无截断面。登记为已知边界。
- **跨 ABI**：x86_64/4 KB 与 arm64/16 KB 的 JSObject 大小、基线可能不同；P2 的 500k 上限与 90% 阈值留了余量，但仍需两种 ABI 各跑一次。
- **极低上限**：不要把 memoryLimit 压到接近基线来"让 OOM 更早发生"；上游在低上限下有清理崩溃/初始化崩溃记录（bellard #468、ng #1181），16 MiB 是产品预算，不应为测试改动。

---

## 6. 分类策略与实现规格（T2 可直接落地）

### 6.1 分层策略

```text
catch (QuickJsInterruptedException)        -> PHASE_TIMEOUT        // 类型层；必须先于 QuickJsException
catch (QuickJsException e)                  -> classify(...)
classify:
  1) message 严格前缀 "InternalError: out of memory"    -> MEMORY_LIMIT
  2) message 含 "not defined"                           -> SANDBOX_VIOLATION（维持现状）
  3) message 是"丢失哨兵"（null / "null" / "Invalid evaluation handle: -1"）
       + heap.mallocSize >= 90% heap.mallocLimit          -> MEMORY_LIMIT（attributed）
       + 否则                                            -> UNKNOWN
  4) 其他                                               -> INVALID_PROGRAM
```

**为什么类型层之后还要内存证据：** quickjs-kt 没有 OOM 类型；JS_NULL 被折叠成 `"null"`，与 `throw null` / `Promise.reject(null)` 不可区分。只有"消息丢失 + 堆几乎满"两个条件同时成立，才足以把失败归属为堆上限。这个条件不是猜测：§3.2 已证明"message 丢失"通常意味着 OOM Error 构造失败，即失败时余量小于一个 Error 对象；P2 的设计又保证失败分配很小，所以 `mallocSize` 会贴近 `mallocLimit`。

**为什么 UNKNOWN 而不是 INVALID_PROGRAM：** `"null"` + 低内存的失败可能是 `throw null`、unhandled `Promise.reject(null)`、或库层格式化失败；把它标成"程序不可求值"会让回喂诊断断言一个并不存在的程序错误。`UNKNOWN` 如实表达"引擎失败、原因不可归属"。

### 6.2 引擎代码草图（当前文件锚点：`:295-429`）

**(a) `runPhase` 内捕获堆快照（在 `finally { js.close() }` 之前）：**

```kotlin
// 只在"消息丢失哨兵"上取快照：普通 TypeError 不走这里，避免多拿一次 jsMutex。
var heapAtFailure: HeapSnapshot? = null
val js = QuickJs.create(jobDispatcher)
transaction.liveEngine.set(js)
try {
    js.memoryLimit = VisualExecutionBudget.MAX_HEAP_BYTES
    js.maxStackSize = VisualExecutionBudget.MAX_STACK_BYTES
    js.evaluationTimeoutMillis = cooperativeTimeoutMillis
    // ... bootstrap / source / phase 调用（现状不变）
} catch (jsError: QuickJsException) {
    if (isLostMessage(jsError.message)) {
        heapAtFailure = runCatching {
            js.memoryUsage.let { HeapSnapshot(it.mallocSize, it.mallocLimit) }
        }.getOrNull()
    }
    throw jsError
} finally {
    transaction.liveEngine.compareAndSet(js, null)
    runCatching { js.close() }
}
```

> 说明：`QuickJsInterruptedException` 也是 `QuickJsException` 子类，会进入上面的 catch；`isLostMessage` 对它为 false，不会多读内存，随后原样重抛，外层原有的 `catch (interrupted: QuickJsInterruptedException)` 仍然先命中。

**(b) 纯函数分类器（建议独立文件，便于 JVM 单测）：**

```kotlin
internal data class HeapSnapshot(
    val mallocSize: Long,
    val mallocLimit: Long,
) {
    /** 16 MiB 下 ≥90% = ≥14.4 MiB；见研究 §6.3 的阈值推导。 */
    fun isNearLimit(): Boolean =
        mallocLimit > 0 && mallocSize * 100 >= mallocLimit * 90
}

internal fun isLostMessage(raw: String?): Boolean =
    raw == null || raw == "null" || raw.startsWith("Invalid evaluation handle: -1")

internal fun classifyVisualJsFailure(
    phaseName: String,
    message: String?,
    heap: HeapSnapshot?,
): Pair<VisualExecutionRejection, String> {
    val raw = message
    val normalized = raw?.trim().orEmpty()
    val lowered = normalized.lowercase()
    return when {
        // 只认引擎生成的 InternalError 前缀；不认任意包含 "out of memory" 的程序文案。
        lowered.startsWith("internalerror: out of memory") ->
            VisualExecutionRejection.MEMORY_LIMIT to
                "js heap limit (${VisualExecutionBudget.MAX_HEAP_BYTES} bytes) exceeded " +
                "in phase $phaseName: ${normalized.take(120)}"

        lowered.contains("not defined") -> // 维持现状，不在本批收紧
            VisualExecutionRejection.SANDBOX_VIOLATION to
                "global is absent in phase $phaseName " +
                "(the sandbox has no IO/network/host objects): ${normalized.take(120)}"

        isLostMessage(raw) && heap?.isNearLimit() == true ->
            VisualExecutionRejection.MEMORY_LIMIT to
                "js heap limit (${VisualExecutionBudget.MAX_HEAP_BYTES} bytes) exceeded " +
                "in phase $phaseName (attributed by memoryUsage: " +
                "mallocSize=${heap.mallocSize}, mallocLimit=${heap.mallocLimit})"

        isLostMessage(raw) ->
            VisualExecutionRejection.UNKNOWN to
                "phase $phaseName failed with a message-less engine error " +
                "(raw=${raw ?: "null"}, mallocSize=${heap?.mallocSize ?: -1}, " +
                "mallocLimit=${heap?.mallocLimit ?: -1})"

        else ->
            VisualExecutionRejection.INVALID_PROGRAM to
                "program failed in phase $phaseName: ${normalized.take(160)}"
    }
}
```

> 若分类器独立成文件，`isLostMessage` 必须是 `internal`（`runPhase` 也要用），不能是 `private`。
> 现有 `rejected(...)` 会 `take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)`（512，`core/model/FigureGeneration.kt:586`），无需额外截断。

**(c) 枚举新增 `UNKNOWN`（`core/domain/.../VisualExecutionEngine.kt:258-288`）：**

```kotlin
/**
 * 引擎失败但无法归属（如 QuickJS 在堆耗尽时抛 JS_NULL，quickjs-kt 只留下字符串 "null"，
 * 且没有堆证据）。与 [INVALID_PROGRAM] 分开：不能据此断言程序有错。
 */
UNKNOWN,
```

同时更新枚举上方"每项对应一类宿主要区别处理"的 KDoc 分组（新增"无法归属"）。

### 6.3 阈值与哨兵

- **90% 的推导：** message 丢失通常来自 `JS_ThrowError2` 的 `JS_NewObjectProtoClass` 失败，即余量小于一个 Error 对象（约数百字节）；也可能是 JNI 字符串分配失败（罕见，见 §3.2 的 `"Cannot format…"`）。所以真正的"丢失型 OOM"通常 ≥99.9% 满；90%（14.4 MiB）留出安全边界，用于吸收 `usable_size` 记账误差与 GC/形状分配的抖动。若设备实测发现 P2 的 `mallocSize` 低于 14.4 MiB，先检查 P2 是否意外触发了大 realloc，再考虑调阈值；**不要为了变绿直接降到很低**。
- **哨兵：** `null`（Java null）、`"null"`（Kotlin 合成）、`"Invalid evaluation handle: -1"`（JNI 防御路径）。`Cannot format QuickJS exception message.` 暂不加入（它只可能在格式化 malloc 失败时出现，且目前引擎未观察到），列入 §7 观察项。
- **严格前缀：** `internalerror: out of memory`。不匹配裸 `out of memory`（可能是 `throw 'out of memory'`），不匹配 `RangeError: out of memory`。`string too long` / `stack overflow` / `invalid string length` 不归 `MEMORY_LIMIT`（`stack overflow` 的归属见 §6.6 决策点）。**已知理论边界：** 若某天应用默认包出现名为 `InternalError` 的 Java 类，`js_error_to_java_error` 的 `FindClass("InternalError")` 会命中并只传裸 message，严格前缀会漏掉；届时需把裸 `out of memory` 纳入并重新评估伪造面。

### 6.4 测试矩阵（先红后绿）

**JVM 纯函数测试（新增；不触碰原生库，符合 `QuickJsVisualExecutionEngineJvmTest` 的"JVM 不执行 QuickJS"不变量）：**

| 输入 | 期望 |
|---|---|
| `InternalError: out of memory\n at prepare`，heap=null | `MEMORY_LIMIT`，diagnostics 含 `16777216` |
| `null`，heap=(16_700_000, 16_777_216) | `MEMORY_LIMIT`，diagnostics 含 `attributed by memoryUsage` |
| `null`，heap=(1_000_000, 16_777_216) | `UNKNOWN` |
| `Invalid evaluation handle: -1`，heap=(16_760_000, 16_777_216) | `MEMORY_LIMIT` |
| `InternalError: string too long\n at prepare`，heap=(16_700_000, 16_777_216) | `INVALID_PROGRAM`（消息分支先于内存证据） |
| `RangeError: out of memory` | `INVALID_PROGRAM`（伪造文案） |
| `ReferenceError: fetch is not defined` | `SANDBOX_VIOLATION` |
| `TypeError: boom-js` | `INVALID_PROGRAM` |

写测试时先让它们因"分类器不存在/行为不符"变红，再落地实现。

**仪器化测试（`QuickJsVisualExecutionEngineInstrumentedTest`）：**

| 用例（DEX 安全名） | 修复前预期 | 修复后预期 |
|---|---|---|
| `memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit`（改用 P1） | 若 message 可读则绿；若设备仍落 `INVALID_PROGRAM`，记录 diagnostics 作为 AAR 证据 | 绿（`MEMORY_LIMIT`） |
| `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage`（P2） | 红（`INVALID_PROGRAM: … null`） | 绿（`MEMORY_LIMIT` + attributed 诊断） |
| `stringLengthCapIsNotAHeapOutOfMemory`（P3） | 绿（`INVALID_PROGRAM: … string too long`） | 绿（防回归） |
| `nullThrownAtLowMemoryIsUnattributedInvalidProgram`（P4，原 `...IsUnknown`） | 红（`INVALID_PROGRAM`） | 绿（`INVALID_PROGRAM` + `[UNATTRIBUTED]` 前缀；T2 裁定不新增 `UNKNOWN`） |
| `forgedOutOfMemoryMessageIsInvalidProgram`（P5，可选） | 红（旧 `contains` 误报 `MEMORY_LIMIT`） | 绿（`INVALID_PROGRAM`） |

> **T2 收口（2026-10-26）**：T2 最终裁定**不新增** `VisualExecutionRejection.UNKNOWN`；不可归属失败折成 `INVALID_PROGRAM`，以 `[UNATTRIBUTED]` 诊断前缀区分。因此上表 P4 的"修复后预期"改为 `INVALID_PROGRAM + 前缀`，用例更名为 `nullThrownAtLowMemoryIsUnattributedInvalidProgram`；本文 §6.5 伪代码里的 `VisualExecutionRejection.UNKNOWN` 是研究期提案，不是最终实现。设备轨复跑：T2 全量 20 例仅 P2 红（P2 的堆证据归属未在设备成立），KD-70 保持 open。

- 全部 OOM 用例保持 `cooperativeTimeoutMillis = 10_000L`、`hardDeadlineMillis = 30_000L`（现状 `:202-206`），避免 OOM 探针的分配过程被 100 ms 协作期限抢先中断成 `PHASE_TIMEOUT`。
- 旧断言不得放宽：P1 仍断言 `MEMORY_LIMIT`；P2 新增的断言补 `diagnostics.contains("16777216")`，与端口契约测试（`VisualExecutionEngineContractTest.kt:376-387`）一致。
- 测试名不要用反引号空格（D8 会失败，批次 21 `build-step1.log:373`）。

### 6.5 影响面清单（改一处要闭合的每一处）

| 落点 | 变更 | 为什么必须一起改 |
|---|---|---|
| `core/domain/.../VisualExecutionEngine.kt:258-288` | 新增 `UNKNOWN` + KDoc 分组 | 分类契约的公共类型；T1 的 AIDL/客户端若按枚举映射，必须能表达它（见下行） |
| `core/data/.../QuickJsVisualExecutionEngine.kt:295-429` | runPhase 快照 + 分类器重写 + 类 KDoc 错误面（`:98-103`） | 现有 KDoc 明写"只认 `out of memory`"，不改会与实现矛盾 |
| 新增内部纯函数（建议 `core/data/.../visual/VisualJsFailureClassification.kt`） | `HeapSnapshot` + `classifyVisualJsFailure` | 让 JVM 单测覆盖分类矩阵，消灭"分类逻辑只能设备验"的失败 |
| `core/data/src/androidTest/.../QuickJsVisualExecutionEngineInstrumentedTest.kt:191-220, 432-446` | P1 替换旧夹具；新增 P2/P3/P4(/P5) | 旧夹具非确定性、且再也测不到 `null` 路径 |
| `core/domain/src/test/.../FakeVisualExecutionEngine.kt` | 无需改（无 exhaustive `when`）；可选补 `UNKNOWN` 分支用例 | 影响面核对结论：不编译中断 |
| `core/domain/src/test/.../VisualExecutionEngineContractTest.kt` | 无需改（fake 不产生 UNKNOWN）；若加 fake `UNKNOWN` 用例再补 | 端口契约仍是同一验收 |
| `core/data/src/test/.../QuickJsVisualExecutionEngineJvmTest.kt` | 无需改；新增独立分类器测试文件 | 保持"JVM 不执行 QuickJS"不变量 |
| T1 的 AIDL / 主进程 client（尚未落树，工作区无 `.aidl`） | 结果状态枚举必须含 `UNKNOWN`，或显式映射为"不可归属失败" | 否则跨进程边界把 UNKNOWN 折成 INVALID_PROGRAM，前功尽弃 |
| `docs/known-defects.md` KD-70（`:2621-2629`） | 设备绿后按证据更新；本 R3 不改 | 关闭条件要求"以 MEMORY_LIMIT（或经裁定的等价判据）转绿 + memoryUsage 证据落盘" |
| `docs/current/visual-generation-contract.md` | **本批不改**（热区 + T7 授权范围外）；语义只落本研究与 KD-70 | 契约 §4 已给 16 MiB 与"不截断"，本规格不新增产品预算 |

### 6.6 待裁定点（T2 开工前确认）

1. **`UNKNOWN` 是否进公共枚举**（推荐：进；若 T1 的 AIDL 已冻结且来不及加，则退化为 `INVALID_PROGRAM` + 诊断前缀 `[UNATTRIBUTED]`，并在 KD-70 登记为临时口径）。
2. **`stack overflow` 是否归 `MEMORY_LIMIT`**：端口枚举 KDoc 写的是"JS 堆/栈超限"（`VisualExecutionEngine.kt:268-269`），契约 §4 表把堆/栈并列；但当前实现会把 `InternalError: stack overflow` 归 `INVALID_PROGRAM`。推荐本批**不改**（KD-70 只围堆上限），另开一条登记；若改，bellard 用 `InternalError: stack overflow`，ng 用 `RangeError: Maximum call stack size exceeded`，匹配要按 AAR 的 bellard 文案。
3. **90% 阈值**是否为最终值：先用 P2 设备实测 `mallocSize/mallocLimit`，再定；在实测前不要在代码里留一个"刚好能过"的魔法值，阈值和推导要写进注释。

### 6.7 不做什么

- 不放宽 `MEMORY_LIMIT` 断言、不把用例改成"有界拒绝即可"来变绿（批次纪律；KD-70 的关闭条件已写明"或经裁定的等价判据"）。
- 不调大 16 MiB 产品预算让 message 可读；预算在契约 §4，改它需要单独裁定。
- 不把 `"null"` 直接当 OOM；P4/P5 就是这条的钉子。
- 不切换 quickjs-ng：AAR 内置 bellard，切引擎不在本批裁定内，而且 ng 同样有 JS_NULL 语义（#1773）。
- 不在本 R3 里做隔离进程/AIDL（T1 的范围）；本规格只驱动 T2。

---

## 7. 未验证项与设备轨命令

### 7.1 未验证项（按风险排序）

1. **P1/P1b/P2/P3/P4/P5 均未在设备执行**；P1 的 message 可读性、P2 的 `"null"` 形态、P2 失败时的 `mallocSize` 都还是源码推导。
2. **AAR 二进制与 tag 源码同源**未在本会话反编译核对；本研究的 JNI/Kotlin 行号按 tag `a081755`。
3. **`memoryUsage` 在 OOM 后读取**是否在 x86_64/arm64 都稳定、耗时多少，未实测（源码判断不分配）。
4. **90% 阈值**是否在两种 ABI 上都合适，未实测。
5. 当前 1M-array 夹具若重跑会得到可读 message 还是 `"null"`，未实测（不再作为主用例）。
6. 顶层（源码求值阶段）JS_NULL 的 `Invalid evaluation handle: -1` 防御路径未构造复现。
7. `stack overflow` 的归属未决（§6.6）。
8. T1 的 AIDL 结果枚举尚未落树，`UNKNOWN` 的跨进程映射未定。
9. P2 的 500k 循环在低端设备上的耗时未测（预期 < 1 s，协作期限 10 s）。
10. 16 KiB 页 / arm64 ABI 上的 OOM 形态未测。

### 7.2 设备轨命令（由 T2/设备轨执行；本 R3 未运行）

```bash
# 构建（批次协议：门禁由脚本串行执行，R3 不跑 Gradle）
./gradlew :core:data:assembleDebugAndroidTest

# 安装 androidTest APK（路径以实际 variant 为准）
adb -s emulator-5554 install -r -t \
  core/data/build/outputs/apk/androidTest/debug/core-data-debug-androidTest.apk

# 逐方法真跑（不要加 -e log true；批次 21 已证明那是 log-only 不执行）
adb -s emulator-5554 shell am instrument -w \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner

# 诊断（P2 的堆证据、P1 的 message）
adb -s emulator-5554 logcat -d | grep -E \
  "out of memory|string too long|Invalid evaluation handle|attributed by memoryUsage|DIAG"
```

通过判据（与 KD-70 关闭条件一致）：

- P1：`reason == MEMORY_LIMIT`，diagnostics 含 `16777216` 与 `out of memory`；
- P2：`reason == MEMORY_LIMIT`，diagnostics 含 `attributed by memoryUsage` 及实际 `mallocSize/mallocLimit`；
- P3：`reason == INVALID_PROGRAM`，diagnostics 含 `string too long`；
- P4：`reason == UNKNOWN`；
- 设备日志里出现 P2 的 `mallocSize` 数值，作为"内存证据"落盘。

---

## 8. 来源

**上游源码（行号对 commit 固定）：**

- bellard/quickjs `quickjs.c` @ `04be246001599f5995fa2f2d8c91a0f198d3f34c`
  <https://github.com/bellard/quickjs/blob/04be246001599f5995fa2f2d8c91a0f198d3f34c/quickjs.c>
  （`JS_STRING_LEN_MAX`:212；rope 阈值:214-219；malloc/realloc:2153-2209；`JS_SetMemoryLimit`:2221-2224；`js_alloc_string`:2366-2382；`string_buffer_realloc`:4077-4095；`JS_ConcatString1`:4368-4369；`JS_NewStringLen`:4654-4655；`js_new_string_rope`:4868-4900；`JS_Throw`:7352-7360；`JS_ThrowError2`:7638-7658；`JS_ThrowOutOfMemory`:7778-7787；`JS_ThrowStackOverflow`:7789-7792；`JS_ComputeMemoryUsage`:6928-6938；`ArrayBuffer` 构造:56739-56815；`Array.prototype.fill`:42370-42398；GC:1780-1799、2083、5613-5619）
- bellard/quickjs `quickjs.h` @ 同 commit：`JS_SetMemoryLimit`:372、`JS_SetMaxStackSize`:375、`JSMemoryUsage`:431-445、`JS_ComputeMemoryUsage`:447、`JS_ThrowOutOfMemory`:678
  <https://github.com/bellard/quickjs/blob/04be246001599f5995fa2f2d8c91a0f198d3f34c/quickjs.h>
- bellard/quickjs `doc/quickjs.texi` @ 同 commit：Memory handling 898-904
  <https://github.com/bellard/quickjs/blob/04be246001599f5995fa2f2d8c91a0f198d3f34c/doc/quickjs.texi>

**quickjs-ng（对照）：**

- `quickjs.c` @ master 2026-10-24（`7a23d8e3…`）：`JS_STRING_LEN_MAX`:222、malloc 检查:1991-1992/2014-2015/2061-2062、`JS_SetMemoryLimit`:2437、`JS_ThrowError2`:8444-8452、`JS_ThrowOutOfMemory`:8563-8571、stack overflow:8576、repeat:48101-48102
  <https://github.com/quickjs-ng/quickjs/blob/master/quickjs.c>
- #798 Internal OOM exception broken in 0.8.0 <https://github.com/quickjs-ng/quickjs/issues/798>
- #1773 Discuss opt-in terminal semantics for configured memory limits <https://github.com/quickjs-ng/quickjs/issues/1773>
- #1181 Crash in JS_NewContext with low memory limit <https://github.com/quickjs-ng/quickjs/issues/1181>
- #1813 Make InternalErrors uncatchable by JS code? <https://github.com/quickjs-ng/quickjs/issues/1813>
- #1066 More robust out of memory handling (bellard) <https://github.com/quickjs-ng/quickjs/issues/1066>
- bellard #468 `qjs -m --memory-limit 1M` 清理断言（CVE-2025-69654） <https://github.com/bellard/quickjs/issues/468>

**quickjs-kt v1.0.15（tag `a081755bcdb18ee6afa7ffee25b8fa976f06d31f`）：**

- `QuickJsException.kt:10-38` <https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/src/commonMain/kotlin/com/dokar/quickjs/QuickJsException.kt>
- `QuickJs.kt:85-125`（memoryLimit / maxStackSize / memoryUsage 公共 API） <https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/src/commonMain/kotlin/com/dokar/quickjs/QuickJs.kt>
- `QuickJs.jni.kt:200-222`（setter/getter）、`:393-480`（evaluateInternal/evalAndAwait）、`:837-843`（`reason.toString()`）、`:953`（setMemoryLimit native） <https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/src/jniMain/kotlin/com/dokar/quickjs/QuickJs.jni.kt>
- `quickjs/native/jni/quickjs_jni.c:403-412`（setMemoryLimit）、`:494-530`（memoryUsage）、`:535-595`（handle_eval_result/store_evaluate_result）、`:789-812`（`JS_EVAL_FLAG_ASYNC`）、`:1028-1108`（invalid handle） <https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/native/jni/quickjs_jni.c>
- `quickjs/native/jni/exception_util.c:9-39,104-209`（异常构造与 `check_js_context_exception` 的 NULL 过滤） <https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/native/jni/exception_util.c>
- `quickjs/native/jni/js_value_util.c:69-113,115-196`（`js_error_message_join` / `js_error_to_string`） <https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/native/jni/js_value_util.c>
- `quickjs/native/jni/mapping/js_value_to_jobject.c:29-158,406-477`（`js_error_to_java_error`、`JS_NULL -> null`、Error 分派） <https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/native/jni/mapping/js_value_to_jobject.c>
- `quickjs/native/jni/promise_rejection_handler.c:7-21` <https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/native/jni/promise_rejection_handler.c>
- `MemoryUsage.kt:6-33` <https://github.com/dokar3/quickjs-kt/blob/a081755bcdb18ee6afa7ffee25b8fa976f06d31f/quickjs/src/commonMain/kotlin/com/dokar/quickjs/MemoryUsage.kt>
- 仓库 `.gitmodules`（bellard 子模块）与 tag/release 信息 <https://github.com/dokar3/quickjs-kt>
- #240 evaluate() never returns…（唯一 memoryLimit issue） <https://github.com/dokar3/quickjs-kt/issues/240>

**仓库内证据：**

- `build/device-track-batch21/step2-quickjs-oom-detail.log:1-6`（`InternalError: string too long` + stack）
- `build/device-track-batch21/step2-oom-attribution.log:1-52`（`expected:<MEMORY_LIMIT> but was:<INVALID_PROGRAM>`）
- `docs/research/2026-10-25-batch-21-record.md` §3.8 第 3/4 条（设备轨转录与 logcat `program failed in phase prepare: null`）
- `docs/known-defects.md:2621-2629`（KD-70 症状/关闭条件）
- `core/data/.../QuickJsVisualExecutionEngine.kt:295-429,584-588`（当前实现）
- `core/data/src/androidTest/.../QuickJsVisualExecutionEngineInstrumentedTest.kt:191-220,432-446`（当前用例/夹具）
- `core/domain/.../VisualExecutionEngine.kt:117-123,251-288`（预算与拒因枚举）
- `core/domain/src/test/.../VisualExecutionEngineContractTest.kt:376-387`（端口内存拒绝契约）
- `core/model/.../FigureGeneration.kt:586`（诊断 512 字符上限）
