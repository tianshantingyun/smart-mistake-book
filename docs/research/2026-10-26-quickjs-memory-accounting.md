# QuickJS memoryLimit 记账语义与确定性 OOM 夹具（R2，批次 24 T2）

| 项 | 值 |
|---|---|
| 日期 | 2026-10-26 |
| 归属 | 批次 24 R2（驱动 T2）；落点 `docs/research/2026-10-26-quickjs-memory-accounting.md` |
| 适用 | KD-70 P2（消息丢失 + 堆证据）、仪器化 `QuickJsVisualExecutionEngineInstrumentedTest`、分类器 `VisualJsFailureClassification`、`PENDING_CALIBRATION` |
| 性质 | **源码级推演 + 既有设备证据复核**。本轮未运行 Gradle、未编译、未执行任何 JVM/仪器化/设备用例、未做 git 写操作 |
| 上游基线 | bellard/quickjs `04be246001599f5995fa2f2d8c91a0f198d3f34c`；quickjs-kt `v1.0.15` tag `a081755bcdb18ee6afa7ffee25b8fa976f06d31f` |
| 前置研究 | R3 `docs/research/2026-10-26-quickjs-oom-semantics.md`（上游语义与 P1–P5 设计）、批次 24 基线 §6（`docs/research/2026-10-26-batch-24-baseline.md:340-380`） |
| 版本说明 | 本文件在开工时已存在（未跟踪，同一 R2 任务早期稿）。本版**整文件纳入并修订**：主因固定为“`JS_CallInternal` 异常出口的局部变量引用计数级联释放”；补 `memoryLimit` 传递链、`mallocSize` 计量口径、AAR `.so` 符号核对、logcat 时序、500k 尺寸推导、备选夹具与比值判据表；修正早期稿“接近上限后每次分配都频繁触发 GC”的错误。 |

---

## 0. 结论（TL;DR）

1. **memoryLimit 是运行时分配器字节计数上限，不是 JS 对象图大小**（R3 §0/§2 已证）。`memoryUsage.mallocSize` 是同一计数器；它只回答“此刻还占着多少分配器字节”，不回答“失败发生在多少字节时”。
2. **设备 P2 的 `mallocSize=177216` 不是失败点，而是 OOM 之后、异常展开时的释放结果。** 主机制是 QuickJS 的引用计数：`JS_CallInternal` 在异常路径上先 `ret_val = JS_EXCEPTION`，再走 `done:` 释放该帧的**所有局部变量与栈值**（`quickjs.c:20690`、`:20699-20709`）；`__JS_FreeValueRT` 对引用计数归零的对象挂入 zero-ref 链，并在 `gc_phase == NONE` 时立即 `free_zero_refcount`（`:6474-6485`、`:6414-6429`），`free_object` 逐个 `free_property`（`:6341-6353`），属性里的 `next` 引用被释放后**级联**清空整条链表（`free_property` 调 `JS_FreeValueRT`，`:6097-6117`）。只挂在局部变量上的链表在 `JS_Eval` 返回前就已释放，引擎随后读到的自然是回落值。
3. **批次 22 与批次 23 两次记录（批次 23 仓内日志 + 批次 22 工单转录）读到同一个 177216**（`docs/research/2026-10-26-batch-22-record.md:117`、`:180`；`docs/research/evidence/2026-10-26-batch-23/step02-quickjs-oom.md:92`），与“引用计数级联释放后固定水位”一致。E-02 自己已给出同一假设的两个分支：“夹具没有推满堆”或“在错误处理/堆快照前已回收到 177 KiB”（同文件 `:101`）；本文用源码链把后一支落成主解释。
4. **次要机制（同向，不依赖它成立，也不能说成“频繁 GC”）：** 每次 `JS_NewObjectFromShape` 分配前会调 `js_trigger_gc`（`quickjs.c:5619`），但 GC 阈值每次触发后抬到当前水位的 1.5 倍（`:1795-1796`）；链仍可达、水位接近上限时 GC 腾不出空间，阈值会被抬到约 1.5 × 上限，**后续分配不再满足触发条件**。因此 job 排水阶段的 GC 不能作为主机制；主机制只能是 §2.2 的异常出口同步释放。
5. **P2 也不是超时**：logcat 显示 P1 11 ms、P2 64 ms（`build/device-track-batch23/step02-quickjs-oom-logcat.txt:110`、`:117-118`），远小于用例的 10 s 协作期限；且超时路径会抛 `InternalError: interrupted`（`quickjs.c:7858-7862`）并被 quickjs-kt 包装成 `QuickJsInterruptedException`（`QuickJs.jni.kt:425-431`），分类器类型分支归 `PHASE_TIMEOUT`，不会落到 `[UNATTRIBUTED]`。
6. **因此旧的 P2 形状（未锚定链表 + 比值证据）不可能成立**，不是阈值问题。换夹具是唯一出路，**不得下调 90%**。
7. **夹具修复**：每轮把最新链表头钉到 `globalThis.__visualHeapProbeHead`，给链增加一个**根引用**——异常展开释放局部 `head` 时引用计数不归零，链不会级联回收；全局对象同时是 GC 根（`JS_MarkContext` 标记 `ctx->global_obj`，`quickjs.c:2717-2728`）。其余约束（有界 50 万轮、单轮小对象、无大 realloc、无长字符串）不变。
8. **分类口径**：90% 阈值与 `MEMORY_LIMIT` 映射不变；以比值为**主证据**的归因统一带 `PENDING_CALIBRATION`（内部枚举 `VisualJsCalibration.PENDING_CALIBRATION` + 诊断字面量），直到设备用锚定夹具复跑并落盘。引擎原文 `InternalError: out of memory` 的直接证据不带该标记。90% of 16,777,216 = **15,099,495**（不是 14,400,000）。
9. **“极小 memoryLimit 的等价判据”不采用**：它不改变“局部变量在异常展开时被释放”这一机制（只是把回落值缩小），而且 R3 §5.7 明确记录了低上限接近基线时的初始化/清理崩溃史（bellard #468、quickjs-ng #1181）；为测试新增预算注入面也不指向一个新失败。真要用只能作为**校准夹具**，不能作为 KD-70 的产品证据。
10. **设备复跑未执行**（本任务纪律：不跑 Gradle；设备复跑归设备轨）。P2 新形状是**待设备验证的预期**，KD-70 保持 open。

---

## 1. 证据等级与边界

| 事实 | 等级 | 依据 |
|---|---|---|
| `JS_SetMemoryLimit` 只写 `malloc_limit`；malloc/realloc 用 `malloc_size` 检查与记账 | 源码级（R3 已逐行核，本轮复核） | `quickjs.c:2153-2175`、`:2182-2206`、`:2221-2224` |
| quickjs-kt 的 `memoryLimit` 传参链到 `JS_SetMemoryLimit` | 源码级 + AAR 符号级（本轮实读 `.so`） | `QuickJs.jni.kt:200-207`、`:953`；`quickjs_jni.c:403-417`（调用在 `:412`）；§2.0 |
| `mallocSize` 直接取 `malloc_state.malloc_size`；`memoryUsedSize` 等另行统计 | 源码级（本轮实读） | `quickjs.c:6928-6938`；`MemoryUsage.kt:7-19` |
| 分配失败且 Error 对象也构造失败 → `JS_NULL` | 源码级 + 设备（P2 `raw=null`） | `quickjs.c:7778-7787`、`:7638-7659`（`:7647-7649`） |
| `JS_CallInternal` 异常路径释放局部变量与栈值 | 源码级（本轮实读） | `quickjs.c:20690`、`:20699-20709` |
| 引用计数归零的对象进入 `free_zero_refcount`；对象释放会释放属性值 | 源码级（本轮实读） | `__JS_FreeValueRT` 对象分支 `:6474-6485`；`free_zero_refcount` `:6414-6429`；`free_object` `:6341-6353`；`free_property` `:6097-6117` |
| 全局对象是 GC 根 | 源码级（本轮实读） | `JS_MarkContext` `:2717`，`JS_MarkValue(rt, ctx->global_obj, …)` `:2728` |
| 每次 `JS_NewObjectFromShape` 先调用 `js_trigger_gc`；阈值每次 GC 后抬到 1.5× | 源码级（本轮实读） | `quickjs.c:5613-5620`（`:5619`）；`:1786-1796`；初值 `:2083` |
| `JS_NULL` → Java `null`（JNI 层） | 源码级（R3 + 本轮复核） | `js_value_to_jobject.c:406-410`；`exception_util.c:170-174`（`JS_TAG_NULL` 不进异常分支） |
| Kotlin 把 `null` reason 合成字符串 `"null"` | 源码级（R3 已核） | `QuickJs.jni.kt:837-839` |
| 引擎在 `close()` 前读 `memoryUsage` | 源码级（按本会话读取时行号） | `QuickJsVisualExecutionEngine.kt:455-474`（读取 `:461`，关闭 `:474`） |
| 177216 是“异常展开释放后的水位” | **推断（高置信）**：源码链 + 两次记录同值（其一为工单转录）；未做设备插桩 | §2.2；E-02 `:92`/`:101`；批次 22 记录 `:117`/`:180` |
| P2 不是 10 s 超时 | **实测读日志**：P2 64 ms、P1 11 ms | `step02-quickjs-oom-logcat.txt:110`、`:117-118` |
| AAR 与 tag 源码“同源” | **符号级一致（本轮已核）**；未反编译、未逐指令比对 | §2.7 的 `grep -a` 结果；`classes.jar` 类清单 |
| 177216 是否为纯基线（无 OOM 时同路径读数） | **未验证** | 需要一条“不 OOM、读同一位置 memoryUsage”的设备对照 |
| 锚定夹具在设备上读到 ≥90% | **未验证（源码推导）** | §4；本批未跑 Gradle/设备 |

**代码行号说明**：`core/data/.../visual` 三文件在本会话期间仍被 T2 热区修改（mtime 20:06）；本文行号按本会话读取时的内容，引用时以符号名（如 `VisualJsCalibration.PENDING_CALIBRATION`）为准。

---

## 2. 记账链（逐环）

### 2.0 memoryLimit 如何到达引擎

```kotlin
// QuickJs.jni.kt:200-207
actual var memoryLimit: Long = -1L
    set(value) {
        withJsLockSync {
            ensureNotClosed()
            field = value
            setMemoryLimit(runtime, globals, value)
        }
    }
```

- `setMemoryLimit` 是 JNI 外部函数（`QuickJs.jni.kt:953`）。
- JNI 侧（`quickjs_jni.c:403-417`）在 `globals->js_mutex` 内调用 `JS_SetMemoryLimit(runtime, byte_count)`（`:412`）。
- QuickJS 侧（`quickjs.c:2221-2224`）：`rt->malloc_ctx.malloc_state.malloc_limit = limit;`。
- 检查点：`js_def_malloc` 用 `malloc_size + size > malloc_limit` 拒绝（`quickjs.c:2153-2160`）；`js_def_realloc` 用 `malloc_size + size - old_size > malloc_limit` 拒绝（`:2182-2199`）。
- 记账：成功分配按 `js_def_malloc_usable_size(ptr) + MALLOC_OVERHEAD` 增加（`:2168`），释放按同值减少（`:2178`）；`MALLOC_OVERHEAD` 非 Apple 为 8（`:59-61`）。所以 **16 MiB 是近似硬上限**：检查用请求字节，记账用可用字节 + 8，成功分配后计数可能略微超过上限。
- 时序：引擎在 `QuickJs.create(jobDispatcher)` 之后才设 `js.memoryLimit = MAX_HEAP_BYTES`（`QuickJsVisualExecutionEngine.kt` 的阶段配置区）。`QuickJs` 的 `init` 已创建 runtime、context 和 globals（`QuickJs.jni.kt:248-266`），这些分配都已计入 `malloc_size`。**16 MiB 预算里包含约 173 KiB 的启动读数**（177216 的观测值；纯基线未单独采样，见 §6）。
- 读取：`memoryUsage` getter（`QuickJs.jni.kt:218-222`）→ JNI `getMemoryUsage`（`quickjs_jni.c:497-530`）→ `JS_ComputeMemoryUsage`（`:507`）→ 复制 `malloc_count/malloc_size/malloc_limit`（`quickjs.c:6928-6938`）。

### 2.1 OOM 抛出与消息丢失

1. `visual.prepare` 内持续分配小对象，`js_def_malloc` 在 `malloc_size + size > malloc_limit` 时返回 NULL（R3 §2.1.1）。
2. `js_malloc` 见 NULL → `JS_ThrowOutOfMemory`（`quickjs.c:7778-7787`）→ `JS_ThrowInternalError(ctx, "out of memory")` → `JS_ThrowError2`。
3. `JS_ThrowError2` 先 `JS_NewObjectProtoClass` 造 Error 对象；若这一步也失败（余量小于一个 Error 对象），把 pending exception 设成 `JS_NULL`（`quickjs.c:7638-7659`，`:7647-7649`，源码注释“out of memory: throw JS_NULL to avoid recursing”）。
4. JNI 的 `check_js_context_exception` 对 `JS_TAG_NULL` 不进异常分支（`exception_util.c:170-174`）；`js_value_to_jobject` 对 `JS_NULL` 返回 Java `null`（`js_value_to_jobject.c:406-410`）；Kotlin 的 `setUnhandledPromiseRejection` 用 `reason.toString()` 合成 `"null"`（`QuickJs.jni.kt:837-839`）。这与引擎诊断 `raw=null` 一致。

**message 丢失的三种形态**：Java `null`、Kotlin 合成的 `"null"`、JNI 防御路径的 `Invalid evaluation handle: -1`。引擎的 `isLostMessage` 三者都接（`VisualJsFailureClassification.kt:134-137`）。`Cannot format QuickJS exception message.` 有消息，不属丢失哨兵。

### 2.1b `mallocSize` 的计量口径（含/不含）

`JS_ComputeMemoryUsage` 的第一段（`quickjs.c:6928-6938`）：

```c
s->malloc_count = rt->malloc_ctx.malloc_state.malloc_count;
s->malloc_size = rt->malloc_ctx.malloc_state.malloc_size;
s->malloc_limit = rt->malloc_ctx.malloc_state.malloc_limit;
```

其余字段（`memory_used_size`、`obj_size`、`prop_size`、`shape_size`、`str_size` 等）是随后遍历 context / 模块 / GC 对象图**另行计算**的估算值（`:6939` 起）。quickjs-kt 原样暴露为 `MemoryUsage`（`MemoryUsage.kt:7-19`）。

| 计入 `mallocSize` | 不计入 `mallocSize` |
|---|---|
| 所有经 QuickJS 分配器的块：JSObject、属性数组（`JSProperty`）、shape、字符串/atom、函数 bytecode、ArrayBuffer 数据、runtime/context、module、job 等 | Java/Kotlin 堆、JNI 局部/全局引用、JNI 字符串副本 |
| GC 管理的对象**自身的分配**（GC 管的是存活判定，不是记账） | C 栈与 JS 调用帧（`alloca`/C 栈；`maxStackSize` 是独立预算） |
| 分配器开销：`usable_size + MALLOC_OVERHEAD`（8 B/块，非 Apple） | 宿主在 QuickJS 之外分配的内存、Android `meminfo`/RSS 口径 |

**关键语义**：它是“当前存活字节数”，不是“历史峰值”。`free`/`realloc` 立即扣减；引用计数归零的对象在异常展开时被同步释放，计数就回落到基线。这就是 P2 读到 177216 的原因——分类器读的是**失败之后**的存活量，不是失败瞬间的峰值。`memoryUsedSize` 也是存活对象图的估算值，同样会回落到基线，不能解决这个问题。

### 2.2 主机制：异常展开时局部变量被同步释放，引用计数级联清空链表

`JS_CallInternal` 的异常出口（节选，`quickjs.c:20690-20709`）：

```c
    ret_val = JS_EXCEPTION;
    /* the local variables are freed by the caller in the generator case. … */
    if (b->func_kind != JS_FUNC_NORMAL) {
    done_generator:
        sf->cur_pc = pc;
        sf->cur_sp = sp;
    } else {
    done:
        if (unlikely(b->var_ref_count != 0)) {
            /* variable references reference the stack: must close them */
            close_var_refs(rt, b, sf);
        }
        /* free the local variables and stack */
        for(pval = local_buf; pval < sp; pval++) {
            JS_FreeValue(ctx, *pval);
        }
    }
    rt->current_stack_frame = sf->prev_frame;
    return ret_val;
```

- 普通函数（`prepare` 就是普通函数）在异常返回前把 `local_buf` 里的每个局部变量 `JS_FreeValue`。`head` 是其中之一。
- QuickJS 的对象是引用计数 + 环回收：
  - `__JS_FreeValueRT` 的对象分支在引用计数归零、且当前不在 `REMOVE_CYCLES` 阶段时，把对象放入 zero-ref 链；若 `gc_phase == NONE` 则立即 `free_zero_refcount`，若在 `DECREF` 阶段则由外层循环释放（`quickjs.c:6474-6485`；`free_zero_refcount` `:6414-6429`）。
  - `free_object` 对每个属性调 `free_property`（`:6341-6353`）；`free_property` 对普通值调 `JS_FreeValueRT`（`:6097-6117`）。
  - `free_zero_refcount` 是**迭代**的：子对象被挂回 zero-ref 链，不会递归调用自身，深链不会爆 C 栈。
- 旧夹具的链表里，头节点只由局部变量 `head` 持有，其余每个节点只由“更新的那个节点”的 `next` 属性持有。局部 `head` 被释放 → 头节点引用计数归零 → 释放它的 `next` 属性 → 前一节点引用计数归零 → …… 整条链在 `JS_Eval` 返回给 JNI 之前就被同步释放。
- 这就是固定值 177216 的来源：读到的是释放完成后的水位，与失败时的 16 MiB 无关。
- **为什么失败点仍在堆上限附近**：失败的那次分配是“新节点”，发生在 `JS_NewObjectFromShape` → `js_malloc` 处；此时 `head` 仍持有旧链，堆水位已接近 `malloc_limit`。异常在节点创建失败后立即抛出，中间没有把链挂到别处。

### 2.3 次要机制（同向，不改变结论；纠正“频繁 GC”的错误）

- 每次 `JS_NewObjectFromShape` 在分配前调用 `js_trigger_gc`（`quickjs.c:5613-5620`），但触发条件是 `malloc_size + size > malloc_gc_threshold`（`:1786-1787`）；每次触发 GC 后阈值被抬到当前水位的 1.5 倍（`:1795-1796`），初值 256 KiB（`:2083`）。
- 当链仍可达、水位接近上限时，GC 腾不出空间，阈值会被抬到约 1.5 × 上限（> 16 MiB）；**后续分配不再满足触发条件**，不会“每次分配都触发 GC”。因此 job 排水阶段的 GC（`QuickJs.jni.kt:590`）不能作为主机制。
- 主机制是 §2.2 的异常出口同步释放；固定值 177216 也由它解释（同步、确定、与 GC 时机无关）。次要机制只在“万一引用计数路径没有清空链”时提供同向保障，不能反过来当作主因。

### 2.4 为什么这不是“夹具没推满堆”

- 如果链表从未增长到上限，根本不会有分配失败：`prepare` 会正常返回 `[]`，测试得到 `Rendered`（断言红但形态不同），不会出现“消息丢失的引擎错误”。
- `JS_NULL` 只在“分配失败且 Error 对象也造不出来”（或程序自己 `throw null`；本夹具没有）时出现（§2.1）；它证明失败确实发生在堆上限附近。
- 失败之后没有任何代码再向链表追加节点；释放只会在异常展开时发生一次。
- 两次记录（批次 23 仓内日志 + 批次 22 工单转录）读到同一个 177216，符合“同步释放后的固定水位”，不符合“每次失败点不同”。
- **时序旁证**：P2 从 started 到 failed 共 64 ms（`step02-quickjs-oom-logcat.txt:117-118`），P1 11 ms（`:110`）；10 s 协作期限远未到，排除超时。超时路径是 typed `QuickJsInterruptedException`（`QuickJs.jni.kt:425-431`），分类器会归 `PHASE_TIMEOUT`，与 `[UNATTRIBUTED]` 不符。

### 2.5 为什么 `globalThis` 锚定能保住读数

- 每轮执行 `globalThis.__visualHeapProbeHead = head;`，全局对象属性持有头节点的一个**拥有引用**。异常展开时局部 `head` 被释放，但全局属性仍持有引用，引用计数不归零 → 不会触发 `free_object` 级联。
- 同时，`JS_MarkContext` 会标记 `ctx->global_obj`（`quickjs.c:2717-2728`），全局对象及其属性在 GC 中也是根。
- 因此引擎在 `runPhase` 的 catch 里读 `js.memoryUsage` 时，链表仍在 → `mallocSize` 代表失败时的存活水位（预期 ≥90% 上限）。
- 这不会改变“失败时消息丢失”：失败分配仍是 `sizeof(JSObject)` 量级，余量不足以构造 Error 对象。
- `close()` 时链被 `free_zero_refcount` **迭代**释放（§2.2），不会递归爆栈。

### 2.6 旧 500k 夹具的尺寸推导（它本来能推满）

- 对象字面量 `{ next: head }` 每轮分配：`JSObject` + 属性数组。`JS_NewObjectFromShape` 先 `js_trigger_gc`，再 `js_malloc(sizeof(JSObject))`（`quickjs.c:5619-5620`），随后 `js_malloc(sizeof(JSProperty) * sh->prop_size)`（`:5636`）。
- `JS_PROP_INITIAL_SIZE = 2`（`:965`）；`add_property` 先用 `find_hashed_shape_prop` 复用已哈希的 shape（`:9179-9215`，`:9208`），1 属性对象的 `prop_size` 保持 2，不会每轮新建 shape 或扩容。
- 64 位下量级：`JSObject` 约 64 B（GC 头 + 位域 + class_id + shape/prop 指针 + union），`JSProperty` 约 16 B/槽，2 槽 32 B；加分配器开销，**每节点约 96 B 或更高**。500k × 96 B ≈ 48 MB；即使按明显低估的 32 B/节点，原始 16 MB 加上每节点至少两个分配块的 `MALLOC_OVERHEAD`（≥16 B/节点）与约 173 KiB 启动读数后也超过 16 MiB。
- 因此 500k 只是**有界上界**；x86_64 上约 `16.7 MB / 96 B ≈ 175k` 轮就会撞上限（arm64 的 per-node/allocator 不同，但同一数量级，仍远小于 500k）。循环不依赖超时兜底：memoryLimit 失效时它正常跑完、测试以 `Rendered` 红掉。

### 2.7 AAR 二进制符号级核对（本会话实际执行）

R3 的边界是“未反编译 AAR，按 tag 与 AAR 同源使用”。本会话对**本机 Gradle transform 缓存中解包的 pinned AAR**（`quickjs-release`，x86_64）做了只读符号检查：

```bash
SO=.gradle/caches/9.6.1/transforms/0050a95dca483a43bf79f982ff0b1da8/transformed/quickjs-release/jni/x86_64/libquickjs.so
for p in "Java_com_dokar_quickjs_QuickJs_setMemoryLimit" \
         "Java_com_dokar_quickjs_QuickJs_getMemoryUsage" \
         "Java_com_dokar_quickjs_QuickJs_installInterrupt" \
         "JS_SetMemoryLimit" "JS_ComputeMemoryUsage" \
         "out of memory" "interrupted" \
         "Cannot format QuickJS exception message."; do
  printf '%-55s %s\n' "$p" "$(grep -a -o -F "$p" "$SO" | wc -l)"
done
```

结果（计数）：`setMemoryLimit` JNI 2、`getMemoryUsage` JNI 2、`installInterrupt` JNI 2、`JS_SetMemoryLimit` 1、`JS_ComputeMemoryUsage` 1、`out of memory` 2、`interrupted` 1、`Cannot format QuickJS exception message.` 1。`classes.jar` 同时含 `com/dokar/quickjs/MemoryUsage.class`、`QuickJs.class`、`QuickJsException.class`。

**结论等级**：AAR 与 tag 在这些入口/文案上**符号级一致**；不等于逐指令/逐字节同源。AAR 的 SHA256 由项目钉版门校验（本会话未运行该门）。

---

## 3. 判据（分类器）

90% 阈值与既有映射**不变**（`VisualJsFailureClassification.kt:53-54`；R3 §6.3）：

```text
重试：InternalError: out of memory 严格前缀              -> MEMORY_LIMIT（直接证据）
丢失哨兵（null/"null"/Invalid evaluation handle: -1）
    + mallocLimit > 0 && mallocSize*100 >= mallocLimit*90 -> MEMORY_LIMIT（比值证据，PENDING_CALIBRATION）
    + 否则                                              -> UNATTRIBUTED
其他                                                    -> INVALID_PROGRAM / SANDBOX_VIOLATION
```

实现：`VisualJsFailureClassification.kt:147-207`；阈值 `:53-54`；校准枚举/标记 `:87-100`；比值分支诊断 `:171-191`。

本批新增的是**证据状态**，不是新判据：

| 分支 | `VisualJsCalibration` | 诊断后缀 |
|---|---|---|
| 引擎原文 `InternalError: out of memory` | `NONE` | 无 |
| 丢失哨兵 + 比值 ≥90% | `PENDING_CALIBRATION` | `; PENDING_CALIBRATION` |
| 丢失哨兵 + 比值 <90%（`UNATTRIBUTED`） | `NONE` | 无 |
| 其他程序错误/沙箱/超时 | `NONE` | 无 |

### 3.1 比值阈值建议（以 `mallocSize/mallocLimit` 为主证据）

令 `ratio = mallocSize / mallocLimit`（仅当 `mallocLimit > 0`；`mallocSize` 允许略超 `mallocLimit`）：

| `ratio` | 分类 | 证据状态 | 说明 |
|---|---|---|---|
| `>= 0.90` | `MEMORY_LIMIT` | `PENDING_CALIBRATION` | 设备上该分支从未成立（唯一数据 1.06%）；锚定夹具复跑前不得当作已校准 |
| `< 0.90` | `UNATTRIBUTED` | `NONE` | 低堆消息丢失（如 `throw null`）必须留在这里；P4 负控依赖它 |
| `mallocLimit <= 0` | `UNATTRIBUTED` | `NONE` | 未设上限，不能归属堆上限 |
| 锚定夹具的**目标** | `MEMORY_LIMIT` | `PENDING_CALIBRATION` → 可申请升为已校准 | 预期 `ratio >= 0.99`（失败分配只有几十字节；`usable_size` 记账甚至可能让 `mallocSize` 略超上限） |

**为什么必须标**：设备上唯一一次比值证据只有 1.06%，“比值≥90%”这条分支在设备上从未成立过。把未校准的推断与引擎原文直证混在一起，会让宿主、记录员或后续批次误把一次“比值恰好过线”当成 KD-70 已关闭。标记只影响诊断与内部类型，`reason` 仍是 `MEMORY_LIMIT`，不放宽也不收紧任何门值。

**何时可以去掉标记**（校准判据，需设备轨 + 主会话记录）：锚定夹具在设备上读到 `mallocSize >= 90% × mallocLimit`（= **15,099,495**），且诊断含 `attributed by memoryUsage`，证据按 R4 落盘；届时由主会话决定是否把该分支升为已校准。此前一律保留。

**为什么是 90% 而不是 99%：** 失败请求是 `sizeof(JSObject)`/属性数组量级（几十字节），理论上 99.99% 也能过；但 `usable_size + 8` 的记账可能让计数略超上限，ABI/分配器/基线也有差异。90% 是保守边界，留出约 1.6 MiB 的余量。**不要为避免红而降到 1%–10%**：那会把 P4 的 `throw null`（基线水位）误归为 OOM，直接破坏负控。

### 3.2 负控与不可下调的理由

- **P4 `throw null`**：消息 `"null"`、堆在基线 → 必须 `UNATTRIBUTED`（`VisualJsFailureClassificationTest` 与仪器化用例都钉住）。把阈值降到 1% 就会把它误判成 OOM。
- **P5 `RangeError('out of memory')`**：消息有 `out of memory` 但不是引擎前缀 → `INVALID_PROGRAM`。严格前缀不能放宽成 `contains`。
- **P1 64 MiB `ArrayBuffer`**：消息可读 → 直接证据，不需要比值。它证明 AAR 的消息通道正常，也证明“16 MiB 限制确实生效”。
- **P3 rope 翻倍**：`InternalError: string too long` → `INVALID_PROGRAM`。`string too long` 不是堆上限。

### 3.3 产品含义：栈根分配在真实 OOM 中仍会 `UNATTRIBUTED`

这是本判据的**固有限制**，必须如实登记：

- 分类器读的是失败**之后**的存活分配量。若程序把唯一引用放在局部变量/调用栈上（绝大多数临时分配都是这样），异常展开会先把它们释放掉，快照只能看到基线 → `[UNATTRIBUTED]`。
- 这不是分类器缺陷，也不是可以靠调阈值修复的：除非上游/JNI 在 OOM 瞬间抓取峰值（1.0.15 没有这个钩子），否则宿主无法从失败后的 live 计数反推峰值。
- 对外行为仍是**有界拒绝**：`INVALID_PROGRAM` + `[UNATTRIBUTED]`，不崩、不截断成“正确图”。诚实标注“原因不可归属”比伪造 OOM 更安全。
- 锚定夹具是**测试仪器**：它让“比值分支”可被设备证明，但不改变真实程序栈根分配的可观测性。

---

## 4. 确定性夹具（仪器化 P2）

### 4.1 主夹具（当前树已落）：锚定链表

`core/data/src/androidTest/.../QuickJsVisualExecutionEngineInstrumentedTest.kt:761-770`：

```js
const visual = {
  prepare: function () {
    var head = null;
    for (var i = 0; i < 500000; i++) {
      head = { next: head };
      globalThis.__visualHeapProbeHead = head;   // 锚：整条链始终是 GC 根
    }
    return [];
  },
  render: function () { return {}; },
};
```

`globalThis.__visualHeapProbeHead` 在首轮建立属性（一次小分配/形状迁移），之后每轮只是覆盖值，不引入新的大分配。它不改变失败点所在的分配大小，只改变失败后对象的可达性。

| 约束 | 为什么 |
|---|---|
| 每轮把 `head` 写到 `globalThis` | 给链增加根引用：异常展开释放局部 `head` 时引用计数不归零，链不会被同步级联回收；全局对象同时是 GC 根 |
| 每轮只分配一个 `{next: head}` | 失败分配是 `sizeof(JSObject)` 量级，余量不够造 Error → 稳定走 JS_NULL（消息丢失分支） |
| 不增长数组、不做字符串拼接 | 避免 fast-array 大 realloc 留下余量、避免先撞 `JS_STRING_LEN_MAX`（2^30-1，R3 §2.3） |
| 500000 轮上界 | 按 §2.6 每节点约 96 B，约 48 MB，远大于 16 MiB；memoryLimit 若失效也会正常结束（测试红），不靠超时兜底 |
| 不在顶层执行 | 顶层失败可能走 `Invalid evaluation handle: -1` 防御路径，不是要测的 promise rejection 形态（R3 §5.1） |

### 4.2 预期设备形态（批次 24 E-02 已在 x86_64 上执行）

> **执行回填（2026-10-26 批次 24 E-02）**：下表前六项在 emulator-5554 / API 34 / x86_64 / 4 KB 上由断言约束成立（`OK (20 tests)`）；**精确 `mallocSize` 未打印**，只能确证 `>= 15,099,495`，“目标 ≥99%”未被证据覆盖。arm64/16 KB 仍未执行（R2 §5 的两 ABI 要求未满足）；P2 用例总耗时 98 ms，用例内 `cooperativeTimeoutMillis=10_000`（非默认 100 ms）。证据 `docs/research/evidence/2026-10-26-batch-24/step02-quickjs-oom.md:10`/`:57-61`/`:141-149`。

| 字段 | 预期 |
|---|---|
| reason | `MEMORY_LIMIT` |
| message | 丢失（`raw` 为 `"null"` 或 Java null） |
| diagnostics | 含 `attributed by memoryUsage`、`mallocSize=<actual>`、`mallocLimit=16777216`、`PENDING_CALIBRATION` |
| `mallocSize` | `>= 15,099,495`（90% × 16,777,216）；**目标 `>= 16,610,000`（≥99%）** |
| 快照 | 必须取到（诊断里 `mallocSize` 不能是 `-1`） |
| 诊断长度 | `<= 512`（`FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS`） |
| 失败时余量 | 小于一个 Error 对象（消息丢失是这一点的事后证据） |

> **数字口径**：仪器化用例 KDoc 里的“≥90% × 16777216（= 14400000）”是早期宽松数；分类器的实际整数判据是 `mallocSize * 100 >= mallocLimit * 90`，即 `>= 15,099,495`。设备证据以分类器判据为准；该 KDoc 数字待 T2 在热区解冻后校正（仅注释，不改判据）。

若设备仍读到约 177216：说明锚定未生效或该 AAR 与 tag 源码行为不一致；把实际 `mallocSize` 落盘、保持 90% 阈值不变，回到 KD-70 继续查（可能需要一条“不 OOM 同路径读数”的对照探针）。

### 4.3 失败模式判读表（设备复跑用）

| 设备实际 | 解释 | 动作 |
|---|---|---|
| `mallocSize` 约 16.6M–16.8M，`MEMORY_LIMIT` | 锚定生效，比值分支成立 | 记录实际值；走 KD-70 关闭/升校准流程 |
| `mallocSize` 约 177216（<1%） | 锚定未生效，或该 AAR 另有释放路径 | 保持 90% 阈值；把实际值落盘；查 `globalThis` 写是否被沙箱拦截/是否在 OOM 前被释放；不要下调阈值 |
| `mallocSize = -1` 且 `[UNATTRIBUTED]` | `memoryUsage` 快照没取到（读失败/引擎已关） | 查快照时机与并发 close；这是取证失败，不是 OOM 判定 |
| `Rendered` | memoryLimit 未生效或夹具没跑 | 查 `js.memoryLimit` 赋值/`MAX_HEAP_BYTES`；用例应红，不得改成“有界拒绝即可” |
| `PHASE_TIMEOUT` | 循环太慢或 10 s 期限被压 | 查 per-node 成本/GC；不要用超时兜底 |
| `INVALID_PROGRAM` + `string too long` | 夹具被误改成字符串路径 | 恢复分配型夹具；`string too long` 不是堆上限 |
| `INVALID_PROGRAM` + 可读 `InternalError: out of memory` | 失败时余量够构造 Error（例如夹具意外大 realloc） | 查夹具是否引入了大分配；这不是 P2 要测的 message-less 分支 |

### 4.4 备选夹具：大 ballast + 预分配数组（若锚定链表在低端设备太慢/有 ABI 问题）

```js
const visual = {
  prepare: function () {
    // 12 MiB 单块、确定能成功；失败时它仍在 globalThis 上。
    globalThis.__oomBallast = new ArrayBuffer(12 * 1024 * 1024);
    // 预分配固定容量，避免 fast-array 扩容 realloc 在失败时留下大余量。
    var nodes = new Array(200000);
    globalThis.__oomNodes = nodes;
    for (var i = 0; i < 200000; i++) {
      nodes[i] = { n: i };   // 小对象；数组把每个节点根住
    }
    return [];
  },
  render: function () { return {}; },
};
```

- 尺寸：12 MiB + 200k 槽 × 8 B ≈ 1.6 MB + 启动读数约 0.18 MB ≈ 13.8 MB，剩余约 2.4 MB；每节点约 96 B → 约 25k 轮就失败，远小于 200k。
- 失败分配仍是小 `JSObject` → message-less；ballast 和数组都在全局根上 → 快照接近上限。
- 数组是**扁平**结构，`close()` 逐元素释放，不依赖链式递归；大 ballast 是单块 ArrayBuffer，释放成本低。
- 风险：`new ArrayBuffer(12 MiB)` / `new Array(200000)` 必须成功；若某 ABI 基线异常高导致它们先失败，失败会留下较大余量、得到可读 message（不是 P2 目标）。用 10–12 MiB + 更小数组可留出余量。
- **取舍**：比锚定链表多两个固定分配/两个全局属性，但更不依赖 per-node 成本与深链释放；如果主夹具在设备上稳定，不必引入。

### 4.5 为什么不选“极小 memoryLimit”（1 MiB）

- 回落问题与上限绝对值无关：未锚定对象在异常展开时就会被释放，`mallocSize/mallocLimit` 同样会掉到很低；1 MiB 仍必须把 ballast 锚定到全局根，否则读数同样回落到基线。它只把数字缩小，不改变机制。
- 引擎目前硬编码 `VisualExecutionBudget.MAX_HEAP_BYTES`（`VisualExecutionEngine.kt:117`，16 MiB）；要做 1 MiB 必须加“注入堆上限”的构造参数/测试 seam，扩大生产类 API 面。
- 1 MiB 距基线（约 173 KiB）只有约 5.9 倍，落在上游低上限崩溃史附近（bellard #468、quickjs-ng #1181；R3 §5.7），不适合作为产品证据。
- 它不消灭一个新的失败：真正的问题是“读快照前的释放”，不是“上限太大”。
- **结论**：不采用为 KD-70 判据。若未来只想要一个快速校准 AAR 记账的探针，可以用独立 `QuickJs` 实例 + 1 MiB + 锚定 ballast，但要在记录里标明“非产品预算、非 KD-70 证据”。

---

## 5. 设备轨命令与判据（本轮未执行）

```bash
# 构建（门禁由脚本串行执行；本文件未运行）
./gradlew :core:data:assembleDebugAndroidTest

# 安装并逐类真跑（不要加 -e log true）
adb -s emulator-5554 install -r -t \
  core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner

# P2 诊断
adb -s emulator-5554 logcat -d | grep -E \
  "PENDING_CALIBRATION|attributed by memoryUsage|mallocSize=|TestRunner: (started|finished|failed): heapExhaustion"
```

**P2 通过判据（缺一不可）**：

1. `reason == MEMORY_LIMIT`；
2. 诊断含 `attributed by memoryUsage`；
3. 诊断含 `mallocLimit=16777216`；
4. 诊断含实际 `mallocSize=<n>`，且 `n >= 15,099,495`（90% × 16,777,216）；目标 `n >= 16,610,000`（≥99%）；
5. 诊断含 `PENDING_CALIBRATION`（在升校准前）；
6. 诊断不含 `mallocSize=-1`；
7. 两种 ABI 各跑一次：x86_64/4 KB 与 arm64/16 KB（R3 §5.7）；设备信息、APK SHA256、原始日志按 R4 规范落盘（`docs/research/2026-10-26-device-evidence-convention.md`）。

**不通过时**：按 §4.3 判读；不得下调 90%、不得改成“有界拒绝即可”、不得用 P1/P3/P4/P5 的绿替代 P2。

**本轮实际未执行**：无 Gradle、无 `am instrument`、无设备安装；以上命令只作为设备轨输入。

---

## 6. 未验证项

1. 锚定链表在设备上是否真的让 `mallocSize` 停在 ≥90%——**批次 24 E-02 已在 x86_64/4 KB 上以断言约束成立（≥15,099,495），精确值仍未打印；arm64/16 KB 未测**（§4.2 回填）。
2. 设备侧未插桩验证“释放发生在 `JS_Eval` 返回前”的时序；该时序由源码（`JS_CallInternal` done 路径 + 引用计数）推得，数值恒定只是强旁证。
3. 177216 是否为纯基线（无 OOM 时同位置读数）未采样。
4. AAR 与 tag 的**逐指令/逐字节**一致性未验证：本轮只做了 `.so` 符号级核对（§2.7），未反编译。
5. 90% 阈值在 arm64/16KB 上的适用性未测（R3 §5.7 要求两 ABI 各一次）。
6. `memoryUsage` 在 OOM 后读取的耗时与稳定性在两种 ABI 上都未实测。
7. `globalThis` 属性写入是否在某些 QuickJS 构建上被沙箱/bootstrap 禁用——当前 bootstrap 只封闭 `Date`、冻结 `Math`，全局赋值在 `WRITER_SOURCE` 用例中已被设备证明可行（批次 23 全量 20 例中该用例绿），但未在 OOM 路径上单独复核。
8. 深链在 `close()` 时的实际耗时未测：源码证明 `free_zero_refcount` 迭代释放、不递归；175k 节点的释放耗时未测（预期毫秒级）。
9. 备选夹具（大 ballast + 数组）与 1 MiB 校准夹具均未实测。

---

## 7. 本轮实际执行的只读命令（可复跑）

```bash
# 1) 仓库内事实（只读）
grep -rn "mallocSize=177216" docs/research build/device-track-batch23
grep -n "TestRunner: .*heapExhaustion\|started:\|failed:" \
  build/device-track-batch23/step02-quickjs-oom-logcat.txt
grep -n "memoryUsage\|js.close()\|catch (jsError" \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt
grep -n "NEAR_LIMIT_PERCENT\|PENDING_CALIBRATION\|isLostMessage" \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualJsFailureClassification.kt
grep -n "HEAP_LIST_SOURCE\|visualHeapProbeHead" \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineInstrumentedTest.kt

# 2) 上游源码（GitHub API raw 经管道；未落盘）
S=$(gh api -H "Accept: application/vnd.github.raw" \
  "repos/bellard/quickjs/contents/quickjs.c?ref=04be246001599f5995fa2f2d8c91a0f198d3f34c")
printf '%s' "$S" | sed -n '2153,2224p'      # malloc/realloc/SetMemoryLimit
printf '%s' "$S" | sed -n '6928,6938p'      # JS_ComputeMemoryUsage 的 malloc_* 复制
printf '%s' "$S" | sed -n '20662,20710p'    # 异常出口释放局部变量/栈
printf '%s' "$S" | sed -n '6414,6486p'      # free_zero_refcount / __JS_FreeValueRT
printf '%s' "$S" | sed -n '6097,6113p'      # free_property
printf '%s' "$S" | sed -n '7638,7659p'      # JS_ThrowError2 -> JS_NULL
printf '%s' "$S" | sed -n '7778,7880p'      # OOM / interrupted
printf '%s' "$S" | sed -n '1780,1797p'      # js_trigger_gc 阈值增长
printf '%s' "$S" | sed -n '5613,5636p'      # JS_NewObjectFromShape 分配
printf '%s' "$S" | sed -n '9179,9215p'      # add_property / shape 复用
printf '%s' "$S" | sed -n '2717,2734p'      # JS_MarkContext 标记 global_obj

# 3) quickjs-kt 源码（tag a081755...）
gh api -H "Accept: application/vnd.github.raw" \
  "repos/dokar3/quickjs-kt/contents/quickjs/src/jniMain/kotlin/com/dokar/quickjs/QuickJs.jni.kt?ref=a081755bcdb18ee6afa7ffee25b8fa976f06d31f" |
  grep -n "memoryLimit\|memoryUsage\|evalAndAwait\|wasInterrupted\|setUnhandledPromiseRejection"
gh api -H "Accept: application/vnd.github.raw" \
  "repos/dokar3/quickjs-kt/contents/quickjs/native/jni/quickjs_jni.c?ref=a081755bcdb18ee6afa7ffee25b8fa976f06d31f" |
  grep -n "setMemoryLimit\|getMemoryUsage\|JS_SetMemoryLimit(runtime\|JS_ComputeMemoryUsage(runtime"
gh api -H "Accept: application/vnd.github.raw" \
  "repos/dokar3/quickjs-kt/contents/quickjs/native/jni/exception_util.c?ref=a081755bcdb18ee6afa7ffee25b8fa976f06d31f" |
  grep -n -A 40 "int check_js_context_exception"
gh api -H "Accept: application/vnd.github.raw" \
  "repos/dokar3/quickjs-kt/contents/quickjs/native/jni/mapping/js_value_to_jobject.c?ref=a081755bcdb18ee6afa7ffee25b8fa976f06d31f" |
  sed -n '406,410p'
gh api -H "Accept: application/vnd.github.raw" \
  "repos/dokar3/quickjs-kt/contents/quickjs/native/jni/promise_rejection_handler.c?ref=a081755bcdb18ee6afa7ffee25b8fa976f06d31f" |
  sed -n '8,25p'
gh api -H "Accept: application/vnd.github.raw" \
  "repos/dokar3/quickjs-kt/contents/quickjs/native/common/quickjs_interrupt.c?ref=a081755bcdb18ee6afa7ffee25b8fa976f06d31f" |
  sed -n '30,88p'

# 4) pinned AAR 的 .so 符号级核对（本机 Gradle transform 缓存；只读）
SO=.gradle/caches/9.6.1/transforms/0050a95dca483a43bf79f982ff0b1da8/transformed/quickjs-release/jni/x86_64/libquickjs.so
for p in Java_com_dokar_quickjs_QuickJs_setMemoryLimit \
         Java_com_dokar_quickjs_QuickJs_getMemoryUsage \
         Java_com_dokar_quickjs_QuickJs_installInterrupt \
         JS_SetMemoryLimit JS_ComputeMemoryUsage "out of memory" "interrupted"; do
  printf '%-55s %s\n' "$p" "$(grep -a -o -F "$p" "$SO" | wc -l)"
done
unzip -l .gradle/caches/9.6.1/transforms/0050a95dca483a43bf79f982ff0b1da8/transformed/quickjs-release/jars/classes.jar |
  grep -E "QuickJs.class|MemoryUsage.class|QuickJsException.class"
```

**未执行**：Gradle、编译、JVM 测试、仪器化测试、`adb`/设备命令、git 写操作。

---

## 8. 来源

**上游源码（行号对 commit 固定）**

- bellard/quickjs `quickjs.c` @ `04be246001599f5995fa2f2d8c91a0f198d3f34c`
  <https://github.com/bellard/quickjs/blob/04be246001599f5995fa2f2d8c91a0f198d3f34c/quickjs.c>
  - `MALLOC_OVERHEAD` `:59-61`；`js_def_malloc` `:2153-2175`；`js_def_free` `:2173-2180`；`js_def_realloc` `:2182-2206`；`JS_SetMemoryLimit` `:2221-2224`
  - `js_malloc/js_mallocz/js_realloc` 的 OOM 抛出 `:1832-1876`
  - `JS_ComputeMemoryUsage` 的 `malloc_*` 复制 `:6928-6938`
  - `JS_ThrowError2` → `JS_NULL` `:7638-7659`（`:7647-7649`）；`JS_ThrowOutOfMemory` `:7778-7787`
  - `JS_ThrowInterrupted`（`"interrupted"` + uncatchable）`:7858-7862`；`__js_poll_interrupts` `:7864-7874`
  - `js_trigger_gc` `:1780-1797`；GC 阈值初值 `:2083`
  - `JS_NewObjectFromShape` `:5613-5636`（`:5619` GC 触发、`:5620` JSObject、`:5636` 属性数组）；`JS_PROP_INITIAL_SIZE` `:965`
  - `add_property` 的 shape 复用 `:9179-9215`（`:9208`）
  - `struct JSProperty` `:947-965`；`struct JSObject` `:990-…`
  - `JS_MarkContext` 标记 `ctx->global_obj` `:2717-2734`（`:2728`）
  - `JS_CallInternal` 异常出口 `:20662-20710`（`:20690`、`:20699-20709`）
  - `free_zero_refcount` `:6414-6429`；`__JS_FreeValueRT` `:6432-6486`；`free_object` `:6340-6382`；`free_property` `:6097-6113`
  - `async_func_free_frame` `:20937-20950`
- quickjs-kt `v1.0.15`（tag `a081755bcdb18ee6afa7ffee25b8fa976f06d31f`）
  - `QuickJs.jni.kt:200-207`（memoryLimit）、`:218-222`（memoryUsage）、`:224-230`（interrupt）、`:406-437`（evalAndAwait，`:430` typed interrupt）、`:837-839`（`reason.toString()`）、`:953`/`:971`（JNI 声明）
  - `quickjs_jni.c:403-417`（setMemoryLimit，调用在 `:412`）、`:497-530`（getMemoryUsage，调用在 `:507`）、`:566-590`（store_evaluate_result）、`:789-833`（evaluate）
  - `exception_util.c:170-209`（`check_js_context_exception` 过滤 `JS_TAG_NULL`）
  - `mapping/js_value_to_jobject.c:406-410`（`JS_NULL` → Java `null`）
  - `promise_rejection_handler.c:8-25`
  - `quickjs_interrupt.c:30-88`（handler/install/reset/fired）
  - `MemoryUsage.kt:7-19`（`mallocLimit/mallocCount/mallocSize/memoryUsedSize/...`）
  <https://github.com/dokar3/quickjs-kt/tree/a081755bcdb18ee6afa7ffee25b8fa976f06d31f>
- 上游 issue（message-less 语义的旁证，R3 已引）
  - quickjs-ng #798 <https://github.com/quickjs-ng/quickjs/issues/798>
  - quickjs-ng #1773 <https://github.com/quickjs-ng/quickjs/issues/1773>
  - quickjs-ng #1181 <https://github.com/quickjs-ng/quickjs/issues/1181>
  - bellard #468 <https://github.com/bellard/quickjs/issues/468>

**仓库内**

- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt`
  - `:387-392`（快照可信度 KDoc：异常出口释放）、`:455-474`（catch → `js.memoryUsage` `:461` → `js.close()` `:474`）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualJsFailureClassification.kt`
  - `:42-59`（HeapSnapshot/90%）、`:87-100`（`VisualJsCalibration`/标记）、`:147-207`（分类与诊断）
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineInstrumentedTest.kt`
  - `:299-326`（P2 用例/断言）、`:748-770`（P2 KDoc + `HEAP_LIST_SOURCE`）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:117`（`MAX_HEAP_BYTES = 16 MiB`）
- `docs/research/2026-10-26-quickjs-oom-semantics.md`（R3：上游语义、P1–P5、阈值推导）
- `docs/research/2026-10-26-batch-24-baseline.md:340-380`（§6 现状核对）
- `docs/research/evidence/2026-10-26-batch-23/step02-quickjs-oom.md:90-95`（P2 实际输出）、`:101`（两种解释）、`:112`（FAIL）
- `build/device-track-batch23/step02-quickjs-oom-logcat.txt:110`（P1 11 ms）、`:117-118`（P2 64 ms）、`:120`（P2 诊断）
- `docs/research/2026-10-26-batch-22-record.md:117`、`:180`（同一 `mallocSize=177216` 的工单转录）
- `docs/known-defects.md` KD-70（`:2677-2695`；批次 24 T2 的机制/夹具记录见 `:2693-2695`）
- 本机 pinned AAR 解包（Gradle transform 缓存，非仓库文件）：
  `.gradle/caches/9.6.1/transforms/0050a95dca483a43bf79f982ff0b1da8/transformed/quickjs-release/jni/x86_64/libquickjs.so`、
  `.../jars/classes.jar`
