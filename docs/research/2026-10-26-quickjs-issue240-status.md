# R2：QuickJS issue #240 上游状态与 quickjs-ng 换栈判据（批次 23，2026-10-26）

- 研究线：R2（服务本批"维持 AAR 观察 vs 换 fork"的裁决；不写业务代码、不改钉版）
- 范围：#240 是否修复/有无 PR/维护者回应；quickjs-ng 对挂死与 job 队列的已知差异；换 ng 底座可行性（绑定/维护度/license）；维持 vs 换栈的可判定触发条件；若已修复的版本与升级路径
- 不在范围：改 `gradle/libs.versions.toml`、改 AAR 钉版、跑 Gradle/设备（共享纪律：门禁与锁生成由脚本串行执行）、真实 Provider 质量（无凭据，`UNVERIFIED`）
- 关联：`docs/known-defects.md` KD-66；`docs/research/2026-10-25-quickjs-aar-consumption.md` §2（#240 在 1.0.15 上的结构性核验）；`docs/current/visual-generation-contract.md:72`（L1/L2/L3 终止层）；`docs/research/2026-10-25-js-engine-selection.md` §6（后备触发条件）

---

## 0. 结论速览

1. **#240 未修复，且上游没有任何回应。** issue 状态 `open`、`comments=0`、`updated_at == created_at`（2026-09-26T08:42:29Z）；唯一事件是 2026-10-25T15:01:20Z 由 `pa2x2` 产生的 `referenced`（指向 fork 上的修复提交）。上游 `main` HEAD 仍是 `a081755b`（2026-09-03），最新 release `v1.0.15`（2026-09-03），Maven Central 最新版本 `1.0.15`（`lastUpdated=20260903123516`）。**没有任何"修复版本"可升级。**
2. **修复代码存在，但只活在一个 1 天大的个人 fork 里。** `pa2x2/quickjs-kt` 的 `fbe42274`（"Stop draining pending jobs once an evaluation is interrupted or closed (#240)"）加 8 个配套提交（含 1 个 merge），共领先上游 9 个提交，发布为 GitHub release `quickjs-kt-1.0.15-fork.1`（group `io.github.pa2x2`，非 Maven Central，包名不变）。**无上游 PR**（`commits/fbe42274/pulls` 为空）；fork 仓库 0★/0 fork、创建于 2026-10-25。我本地下载该 AAR 复算了 sha256，与它发布的 `SHA256SUMS` 一致（§2.6），但这个维护信号不足以作为生产依赖。
3. **quickjs-ng 在 job 语义上确有差异，但不足以成为换栈理由。** ng v0.17.0 的 `promise_reaction_job()` 对 uncatchable 异常（中断用的 `InternalError: interrupted`）直接 `return JS_EXCEPTION`，而 bellard/quickjs（1.0.15 内嵌，钉 `04be246`）一律 `JS_GetException` 后转成 promise rejection 并返回成功——这正是 #240 能无限排水的原因。所以 **换 ng 引擎会让同一个自续 job 形状在 `JS_ExecutePendingJob` 返回 -1 处停下（前提是绑定层把 -1 当停止）**；但 (a) 这不是 quickjs-kt 的绑定层，仍要自己维护绑定；(b) **ng 没有维护中的 Kotlin/Android 绑定**（§3.5）；(c) ng 仍有 open 的中断覆盖缺口 #1753；(d) ng master 已把 `JSInterruptHandler` 签名从 `JSRuntime*` 改成 `JSContext*`（v0.17.0 还是旧签名），钉哪个版本都要另做兼容。**判定：本批不换 ng。**
4. **判据：维持 `quickjs-kt-android:1.0.15` + L2 `:visual_js` 隔离进程/`killProcess`，直到 §4.3 的任一触发条件成立。** 换 fork/自建补丁构建的触发条件是"上游沉默 + L2 设备门无法转绿 + 该候选自身过全部门"，不是"#240 又出现了"；换 ng 的触发条件是"出现稳定发布版 Kotlin/Android 绑定 + #1753 关闭 + 设备实证"。在此之前，JS 维持不广告（沿用裁定）。

---

## 1. 证据通道与 pin

本次全部 GitHub 事实用 `gh` CLI（v2.95.0，已认证）实时取得，Maven 用 `curl`，AAR 用 `gh release download` + 本地 Python/sha256。**未运行 Gradle、未跑设备**。

| 对象 | 读取的 pin | 取法 |
|---|---|---|
| `dokar3/quickjs-kt` | `main` = `a081755bcdb18ee6afa7ffee25b8fa976f06d31f`（2026-09-03） | `gh api repos/dokar3/quickjs-kt/commits/main` |
| 同上，v1.0.15 源码 | tag `v1.0.15`（内容与 `a081755b` 同源，`main` 无后续提交） | `?ref=v1.0.15` |
| `bellard/quickjs`（子模块） | `04be246001599f5995fa2f2d8c91a0f198d3f34c`（2026-06-16；`VERSION=2026-06-04`） | `gh api repos/dokar3/quickjs-kt/contents/quickjs/native/quickjs` → `submodule_git_url` + `gh api repos/bellard/quickjs/commits/04be246...` |
| `pa2x2/quickjs-kt`（fork） | `main` = `0af18df5c534dd81a591e5e5e596640c6731d378`（2026-10-25） | `gh api repos/pa2x2/quickjs-kt/commits/main` |
| `quickjs-ng/quickjs` | release `v0.17.0` = `6d46d07d04041b40f4f49eaa7fdebe44c314c699`（2026-09-18）；`master` = `7a23d8e3fedb010070ef1a03873c55500c7aa1a9`（2026-10-23） | `gh api repos/quickjs-ng/quickjs/commits/{v0.17.0,master}` |

行号说明：quickjs-kt 行号按 `?ref=v1.0.15` 的原文；bellard 行号按 `04be246`；ng 行号按 `v0.17.0` 或 `master`，正文逐处标注。

---

## 2. 任务一：#240 上游状态

### 2.1 issue 元数据（`gh api repos/dokar3/quickjs-kt/issues/240`，2026-10-26 实取）

```json
{"state":"open","comments":0,"created_at":"2026-09-26T08:42:29Z",
 "updated_at":"2026-09-26T08:42:29Z","closed_at":null,"pull_request":null}
```

- 标题：`evaluate() never returns when a job keeps queuing the next one, even with interruptEvaluation()`
- 无 label、无 milestone；**零评论** → 维护者从未在 issue 内回应。
- timeline 只有一条：`{"event":"referenced","actor":"pa2x2","commit_id":"fbe42274...","created_at":"2026-10-25T15:01:20Z"}`；events 同样只有这一条。
- 引用：https://github.com/dokar3/quickjs-kt/issues/240

### 2.2 修复存在，但只在 fork

`gh api repos/dokar3/quickjs-kt/commits/fbe42274f2a8a0a7dc19a7ec44a488a7da1ddce6` 能取到该提交（同一 GitHub network），但：

- `.../commits/fbe42274/branches-where-head` → **空**（上游仓库没有任何分支以它或其后代为 HEAD）；
- `.../commits/fbe42274/pulls` → **空**（没有关联 PR）；
- 提交作者/committer 都是 `pa2x2`（2026-10-25T14:47:12Z），parent 为 `52a398b1`（"Point the fork's Maven group, POM metadata and README at pa2x2/quickjs-kt"）；
- `gh api repos/pa2x2/quickjs-kt/compare/fbe42274...0af18df5` → `ahead_by=7`，即修复是 fork `main` 的祖先；
- 上游开放 PR 列表（`gh pr list --state all`）没有 #240 相关条目；issue 作者 `kangzj` 的 fork `kangzj/quickjs-kt` HEAD = `a081755b`（与上游相同，未带修复）。

fork 领先上游的 9 个提交（`gh api "repos/pa2x2/quickjs-kt/compare/a081755b...0af18df5"` → `ahead_by=9`）：

| sha | 提交 |
|---|---|
| `52a398b1` | Point the fork's Maven group, POM metadata and README at pa2x2/quickjs-kt |
| `fbe42274` | Stop draining pending jobs once an evaluation is interrupted or closed (#240) |
| `faaeab83` | Don't settle async binding promises after their evaluation has finished |
| `c015ed65` | Add an opt-in handler that reports unhandled promise rejections instead of failing the evaluation |
| `b7683338` | update license |
| `14dd274f` | Release the Android AAR and JVM jar as GitHub release assets |
| `6692abd7` | Merge branch 'evaluation-fixes' |
| `a537a5a1` | Rebuild the JNI libraries when switching between debug and publishing builds |
| `0af18df5` | Run the release tests in the publishing build, on the libraries that ship |

→ **不是"上游修好了"，而是"下游有人做了一份补丁并自建发布"。**

### 2.3 上游发布与主线（"是否落版"的直接判据）

| 检查 | 命令 | 结果 |
|---|---|---|
| 最新 release | `gh api repos/dokar3/quickjs-kt/releases/latest` | `tag_name=v1.0.15`，`published_at=2026-09-03T12:39:32Z` |
| main HEAD | `gh api repos/dokar3/quickjs-kt/commits/main` | `a081755b`，2026-09-03T11:39:23Z |
| 2026-09-20 之后 main 提交 | `gh api "repos/dokar3/quickjs-kt/commits?since=2026-09-20T00:00:00Z"` | **空** |
| Maven 版本清单 | `curl -s https://repo1.maven.org/maven2/io/github/dokar3/quickjs-kt-android/maven-metadata.xml` | 最高 `<version>1.0.15</version>`，`<lastUpdated>20260903123516</lastUpdated>` |
| 仓库 `pushed_at` | `gh api repos/dokar3/quickjs-kt` | `2026-10-20T18:48:53Z` —— 来自 renovate 分支推送；**main 仍停在 09-03** |

→ 截至 2026-10-26，**没有 >1.0.15 的版本，没有修复落版**。KD-66 的"上游修复版升级"分支不成立。

### 2.4 维护者回应与维护度（一手原话）

请求把引擎迁到 quickjs-ng 的 issue #137（2025-08-14 开）下，维护者 `dokar3` 于 **2026-01-21T10:39:39Z** 回复（`gh issue view 137 --repo dokar3/quickjs-kt` 实取）：

> "Thanks for the suggestion. This looks like a big change, and I stopped Kotlin development over a year ago. So I might not have time to invest in such a change."

这是判断"上游短期不会修 #240"的最硬证据：不是优先级问题，而是维护者已停止 Kotlin 开发。配套事实：155★、32 fork、29 open issues；#240/#241/#242 三个 9 月下旬 issue 全部零评论；最新一次 release 是 2026-09-03。

### 2.5 根因：为什么这是绑定层问题，不是引擎"修一修就好"

quickjs-kt 1.0.15 的执行链（源码实读，`?ref=v1.0.15`）：

1. `QuickJs.jni.kt:583-595`：`awaitEvaluateResult` 外层 `while (true)`，内层在 `jsMutex.withLock`（:585）里 `do { executePendingJob(context, globals) } while (executed)`（:589-592）。**内层不检查中断/isClosed**；`isClosed` 检查在 :586，只有内层退出才到得了。
2. `quickjs_jni.c:984-1022`：`executePendingJob` 调 `JS_ExecutePendingJob`；`ret == 0` → `JNI_FALSE`；`ret < 0` → 抛 Java 异常并 `JNI_FALSE`；否则 `JNI_TRUE`。
3. `quickjs_interrupt.c`：`requested` 置位后 handler 每次返回 1（`if (state->requested) { state->fired = 1; return 1; }`），**只有 `qjs_interrupt_reset` 清零**；因此一次 `interruptEvaluation()` 会在整个根 evaluate 期间打断后续每一个 job。
4. 引擎侧（bellard `04be246`，`quickjs.c:53386-53423`）：promise reaction job 里被中断抛出的异常会被 `JS_GetException` 取出并交给 reject 函数，**job 本身返回正常值**；`JS_ExecutePendingJob` 于是返回 1（`quickjs.c` 中 `ret = 1` 分支）。这就是"每个 job 都的确被打断，但它已经排队了下一个，排水循环照样继续"的机制——**中断不能让队列变空，队列由运行中的 job 自我续命**。
5. 结论：**只要绑定层不在 job 之间检查中断，换任何兼容 QuickJS 的引擎都会保持这个循环形状。** #240 的修复必须落在排水策略上（issue 作者建议的方向与 fork 的实际补丁一致）。

> 上面第 4 点是源码链推断，与 issue 作者的现象描述及 KD-66 的设备线程栈（`executePendingJob←awaitEvaluateResult←evalAndAwait`）一致；本线未在设备上重跑，标 `UNVERIFIED`（纪律：本线不跑 Gradle/设备）。

### 2.6 fork 修复内容与构件核查

**改动面**（`gh api repos/dokar3/quickjs-kt/commits/fbe42274` 的 `files[].patch` 实读；+157/-2）：

- `quickjs/native/common/quickjs_interrupt.c/.h`：抽出 `qjs_interrupt_check()`，新增 `qjs_interrupt_poll()`——**在 JS 执行之外**检查 `requested`/deadline 并置 `fired`；
- `quickjs/native/jni/quickjs_jni.c`：新增 `pollInterrupt(state_ptr)` JNI；
- `quickjs/src/jniMain/.../QuickJs.jni.kt`：排水循环每个 job 后 `if (executed && shouldStopDrainingJobs()) { drainStopped = true; break }`，随后 `cancelAsyncJobs(session)` 并抛 `QuickJsInterruptedException("InternalError: interrupted")`；`shouldStopDrainingJobs()` = `isClosed || pollInterrupt(interruptState)`；
- `quickjs/src/nativeMain/.../QuickJs.native.kt`：同构改动（K/N 侧）；
- `quickjs/src/commonTest/.../EvaluationInterruptTest.kt`：新增 3 例（超时/手动中断/协程取消 × 自续 job），用 5 s watchdog 断言 evaluate 返回且 `close()` 5 s 内返回。

**构件核查（本线本地执行）**：

```console
$ gh release download quickjs-kt-1.0.15-fork.1 -R pa2x2/quickjs-kt \
    -p "quickjs-kt-android-*.aar" -p "*SHA256SUMS.txt" -D <tmp>
$ sha256sum quickjs-kt-android-1.0.15-fork.1.aar
57d7239f7889961f4a5b7969e77d38d9df517b23850a923357176b7f62528212  quickjs-kt-1.0.15-fork.1.aar
# 发布的 SHA256SUMS 内对应行：
57d7239f7889961f4a5b7969e77d38d9df517b23850a923357176b7f62528212  quickjs-kt-android-1.0.15-fork.1.aar
```

- AAR 大小 1,833,905 B（上游 1.0.15 为 1,828,904 B，本批未重下上游 AAR，此对照值引自 `2026-10-25-quickjs-aar-consumption.md` §3.1）；
- 内含 4 ABI：arm64-v8a 925,200 / armeabi-v7a 657,116 / x86 880,092 / x86_64 911,392 B；`classes.jar` 196,392 B；`aar-metadata.properties` 仍是 `minCompileSdk=36`；
- ELF 程序头本地解析（Python 直读，方法同 `2026-10-25-16kb-arm64-api23-build.md`）：
  - arm64：`PT_LOAD align=0x4000`；`GNU_RELRO end=0xc5000`，`%16384=4096`，RELRO 完整落在第二个 RW LOAD 内（`vaddr=0xbe6a0 memsz=0x6960`）；
  - x86_64：`GNU_RELRO end=0xcf000`，`%16384=12288`，同样完整落在其 LOAD 内；
  - 与上游 AAR 同形状，按契约 §2（`visual-generation-contract.md:22`）援引的官方"RELRO in its own LOAD segment"豁免，**结构上不构成新的 16 KB 阻断**（真机加载仍归设备轨）。
- **风险登记**：补丁注释自述 "Jobs the script queued stay queued, the runtime can't drop them"——中断后队列里仍有残余 job，同实例复用时要验证不会在下次 evaluate 时继续跑；fork 的测试只覆盖"evaluate 返回 + close() 返回"，未覆盖复用。

**判定**：该 fork 是"#240 可修"的存在性证明，也是自建补丁的参考实现；**但它本身不是可采纳依赖**（0★、1 天、单人、GitHub release 而非 Maven、无上游 PR、无复用测试）。

---

## 3. 任务二：quickjs-ng 的差异与换栈可行性

### 3.1 ng 仓库事实（`gh api repos/quickjs-ng/quickjs`，2026-10-26）

| 项 | 值 |
|---|---|
| license | **MIT**（LICENSE 首行 "The MIT License (MIT)"，版权行含 Bellard / Charlie Gordon / Ben Noordhuis / Saúl Ibarra Corretgé） |
| 活跃度 | 4018★、417 fork、185 open issues；`pushed_at=2026-10-24T07:37:10Z` |
| 最新 release | `v0.17.0`（2026-09-18T21:39:55Z）；此前 `v0.16.2`（2026-08-20）、`v0.16.1`（2026-08-04） |
| 发布节奏（自述） | `docs/docs/diff.md`："average of a new release every 2 months"；40+ contributors / 400+ PRs；每个 PR 跑 50+ 配置 + test262 |
| Android 平台 | `docs/docs/supported_platforms.md`：Android 需 NDK ≥ 26.0.10792818，官方标注 "Limited testing" |
| 与 bellard 的关系 | 自述 "aims to be a drop-in replacement … Minimal API changes might be necessary"；构建改为 CMake，Windows 一等公民 |

### 3.2 job 队列/挂死：逐层对比

| 层 | bellard `04be246`（quickjs-kt 1.0.15 内嵌） | ng `v0.17.0` | ng `master`（2026-10-23） |
|---|---|---|---|
| 中断异常 | `JS_ThrowInternalError(ctx,"interrupted")` + `JS_SetUncatchableException(ctx, TRUE)`（`quickjs.c:7860-7861`） | 同（`quickjs.c:8653-8654`，`JS_SetUncatchableError`） | 同 |
| `promise_reaction_job` 对异常 | **不区分 uncatchable**，一律 `res = JS_GetException(ctx)` 后调 reject（`quickjs.c:53410-53423`）→ job 正常返回 | **uncatchable 直接 `return JS_EXCEPTION`**（`quickjs.c:55620-55623`）→ job 失败 | 同 v0.17.0（`quickjs.c:55626-55628`） |
| `JS_ExecutePendingJob` 返回 | `1`（job "成功"）→ 绑定层排水继续 | `-1`（异常） | `-1` |
| 参考事件循环 `js_std_loop` | 不适用（嵌入方自写循环） | 内层 `for(;;) { err = JS_ExecutePendingJob(...); if (err <= 0) { if (err < 0) goto done; break; } }`（`quickjs-libc.c:4852-4877`）；每轮 job 都返回 1 时**同样不设上界** | 同 |
| 对 #240 形状的含义 | 可无限排水（与实测一致） | 中断后 job 返回 -1；**若绑定层把 -1 当停止，循环可退出** | 同 |

三点必须同时说清楚：

1. **ng 并没有"修好 job 队列"**：它的 `JS_ExecutePendingJob` 仍是 1/0/-1 三态、不做"最多排空 N 个 job"的上界（`master quickjs.c:2525-2552`）；自续 job 只要每次成功返回 1，任何 `do/while(executed)` 形状仍会无限跑。ng 的差异是"中断在 promise job 内变成 -1"，不是"队列有界"。
2. **这个差异是引擎语义变化，不是无痛修补**：中断从"被 promise 机制吞掉、变成 rejected derived promise"变成"job 直接失败"。它会让未处理 rejection 的处理路径、评估结果状态机、错误文案都发生变化——fork 自己也在同一批提交里加了 "Add an opt-in handler that reports unhandled promise rejections instead of failing the evaluation"（`c015ed65`），说明这块语义很敏感。
3. **ng 官方立场是"job API 是低层原语，循环由嵌入方负责"**：issue #1095（"Is it possible to change the js_std_loop from the internal while loop..."）中，维护者 `saghul` 原话："QuickJS provides the low level primitives already (ie the jobs API) and it's up to the embedder to wire it up. quickjs-libc is a reference implementation, but doesn't aim to be a comprehensive one."（2025-06；issue 已 closed）。→ **不能把 #240 的修复责任推给 ng。**

### 3.3 ng 的中断覆盖缺口（换栈不能解决"硬终止"）

- **#1753（open，2026-09-25）**："More Array builtins never consult the interrupt handler (follow-up to #1672)"。正文列出 `join/toLocaleString`、`reverse`、`copyWithin/shift/unshift/splice`、`slice/splice`、`concat`、`sort`、`flat/flatMap`、`fill`、`Array.from` 等 C 侧长循环在 v0.17.0 仍不 poll；`Array.prototype.join.call({length: 2**32-1}, "")` 实测 4–5 分钟不返回，Ctrl-C 无效，内存上限也拦不住（稀疏数组每轮不分配）。
- #1672/#1674（已修）只覆盖了 `indexOf/lastIndexOf/includes/every/some/forEach/map/filter/reduce` 与 `find` 家族。
- 含义：**ng 也不提供"任意脚本硬上界"**；契约 §4 的 L2 进程级 `killProcess` 仍是唯一可靠层。换 ng 不会减少对 L2 的依赖。

### 3.4 API 漂移（钉版本时必须处理）

| 版本 | `JSInterruptHandler` 签名 | 证据 |
|---|---|---|
| bellard `04be246`（1.0.15 内嵌） | `typedef int JSInterruptHandler(JSRuntime *rt, void *opaque);` | `quickjs.h:925-926` |
| ng `v0.17.0`（已发布） | **同旧签名** `JSRuntime *rt` | `quickjs.h:1187-1188` |
| ng `master`（2026-10-05 起） | `typedef int JSInterruptHandler(JSContext *ctx, void *opaque);` | `quickjs.h:1192-1193`；commit `876442bd` "Pass JSContext to JSInterruptHandler (#1802)"，对应 issue #1789（closed 2026-10-05） |

→ 若换 ng：钉 v0.17.0 则中断 shim 源码级兼容；钉 master 则 `quickjs_interrupt.c`/JNI 需要改签名。无论如何，quickjs-kt 的 `qjs_interrupt_*` 封装、16 KB 链接参数、AAR/so 打包、consumer proguard 规则都要重做并重验。

### 3.5 Kotlin/Android 绑定现状（负面证据，逐条给命令）

| 检查 | 命令 | 结果 |
|---|---|---|
| ng 官方"使用/支持项目"清单 | `gh api repos/quickjs-ng/quickjs/contents/docs/docs/projects.md` | 28 个项目，**无任何 Kotlin/Java/Android 绑定**（Rust/Go/Zig/Python/C++/C#/OCaml/Erlang 等） |
| GitHub 仓库搜索 | `gh search repos "quickjs-ng kotlin"` / `"quickjs-ng android"` | 两次均 **0 命中** |
| Kotlin 代码搜索 | `gh search code "quickjs-ng" --language Kotlin` | 命中均为**应用内自建 JNI**，例如 `chrisbirster/stingjs`（0★，`QuickJsNgCandidateRuntime.kt` + 自建 `libsting_quickjs_ng_android.so`，"candidate runtime"）；无一个发布为可复用库 |
| 最接近的 Android 产物 | `react-native-quickjs/quickjs`（MIT，41★，2026-09-01 创建，2026-10-15 push） | React Native **JSI runtime**，README 自述 **Alpha**、"Breaking changes are expected"；要求 RN 0.85+/新架构、Android 7.0+（API 24）；面向 Kotlin/JS 模块而非任意字符串求值，**不是通用 Kotlin 绑定** |
| quickjs-kt 官方是否迁 ng | issue #137（§2.4） | 维护者明确拒绝："I stopped Kotlin development over a year ago" |

现有其他 Kotlin 候选均**未提供"可复用的 ng 绑定"证据**：`peacefulprogram/kuickjs`（0★，最后 push 2026-07-15，未声明 ng）、`yuroyami/KiteJS`（1★，Rhino 纯 Kotlin 移植，非 QuickJS）、`block/trailblaze` 的 `quickjs-tools`（测试框架内部模块，不是通用绑定）。

### 3.6 换 ng 底座可行性判定

**结论：技术上"可做"，工程上"本批不做"。**

- 引擎本身：MIT、活跃、CMake、官方 Android NDK ≥26（limited testing）→ 编译不是障碍；
- 绑定层：**没有可复用的 Kotlin/Android 产物** → 等价于"自建并长期维护一份 quickjs-kt 的 ng 分支"，与计划 T4 红线"直接采用选型库，不自己写 JNI"冲突，需先经用户裁定（这正是 `2026-10-25-js-engine-selection.md` §6 后备条款的适用场景）；
- 收益：对 #240 形状，ng v0.17.0 的 uncatchable 语义**可能**让现有排水循环退出——但 #1753 的 C 循环缺口仍在，L2 仍是唯一硬终止；而绑定层自身的 `do/while(executed)` 上界问题，ng 并没有替你解决；
- 成本：重做 JNI/AAR/16 KB/R8/sha 链 + 绑定层排水策略 + 设备门 + 长期维护（上游 quickjs-kt 已停更，等于把 bus factor 从 1 人换成自己）。
- **裁定：维持 AAR + L2；ng 只保留为"若 quickjs-kt 停更且出现阻断级缺陷"的后备，触发条件见 §4.3-C。**

---

## 4. 任务三：判据——维持 AAR + 进程级终止 vs 换 fork

### 4.1 现状基线（本批复核）

- 消费坐标：`io.github.dokar3:quickjs-kt-android:1.0.15`（`gradle/libs.versions.toml:51`），AAR 已接、sha256 已记录（`2026-10-25-quickjs-aar-consumption.md` §3.1）；16 KB 按契约 §2 判定合规（`visual-generation-contract.md:22`）。
- 硬终止：L2 `:visual_js` `isolatedProcess` + AIDL + watchdog `Process.killProcess(myPid())` **代码已落层**（`app/src/main/AndroidManifest.xml`、`app/src/main/aidl/.../visualjs/`、`VisualJsService.kt`、`VisualJsClientEngine.kt`），但**设备复跑 UNVERIFIED**（批次 22 修 `dup()` 后未重装验证，`known-defects.md` KD-66 Progress）。
- 软终止：L1 `evaluationTimeoutMillis` + 协程取消，对自续 job 无效（#240）。
- JS 状态：UNAVAILABLE、不广告；4E.2/交互不做（沿用裁定）。
- **KD-66 仍 open**：关闭判据是"#240 形状在 hard+500 ms 内返回拒绝、且后续请求可用"的设备证据。

### 4.2 路线对比

| 维度 | A. 维持 AAR + L2（现状） | B. 换用修复版 fork AAR（pa2x2） | C. 自建 quickjs-kt + 携带补丁 | D. 换 ng 底座 |
|---|---|---|---|---|
| #240 结果 | 不修；进程级 SIGKILL 兜底 | 修（evaluate 抛 `QuickJsInterruptedException`；close 返回） | 同 B（补丁可参考 fork） | 引擎把中断变 -1；绑定仍需自证退出 |
| 维护方 | 上游（已停更） | 陌生个人 fork（0★/1 天） | 自己 | 自己 + ng 社区（仅引擎） |
| 额外供应链风险 | 低（钉版 + sha） | 高（新发布者、无 Maven、无独立消费者） | 中（自建链要自证） | 高（全链自建） |
| 16 KB / R8 / 设备门 | 已核（设备门待绿） | 本地 ELF 形状同上游；设备未验 | 需全量重验 | 需全量重验 |
| 与现有裁定 | 一致 | 需改"钉版不动" | 需改"钉版不动" | 需改"钉版不动"+T4 红线 |
| 本批结论 | **采纳** | 不采纳 | 不采纳（留作后备） | 不采纳（留作后备） |

### 4.3 触发条件（可判定，不靠"感觉"）

**A. 维持现状（默认）**，只要同时满足：
1. AAR 仍可消费（sha256 可复算、16 KB 判定不被推翻、R8/JNI keep 规则无回归）；
2. L2 设备门在后续批次转绿（KD-66 关闭判据达成）；
3. 没有出现新的"进程级也无法兜底"的缺陷（如 `killProcess` 被系统策略阻断、隔离进程无法装载 so）。

**B. 升级到上游修复版（首选升级路径）**，当且仅当：
1. 上游发布 **>1.0.15**，且 release notes/提交信息明确包含 #240 修复（或排水循环变更）；
2. Maven Central 出现该版本（`maven-metadata.xml` 版本表新增）；
3. 该 AAR 通过：sha256 记录 + 16 KB 逐 ABI 复核 + aar-metadata/minSdk 核对 + R8 internal 含 Worker/OCR 门 + 设备 KD-66 反转用例（自续 job 必须在 hard+500 ms 内以 `QuickJsInterruptedException` 返回，且同实例/新实例后续请求可用）。

**C. 换 fork / 自建补丁构建**，当且仅当同时满足：
1. 上游自 #240 登记起 **≥90 天无修复、无回应**（即 2026-12-25 之后仍无 release），**且**
2. L2 设备门无法转绿或代价不可接受（例：`killProcess` 在此 ROM/策略下不可用、隔离进程重建成本超过预算），**且**
3. 候选（fork 或我们自建）满足：有持续维护信号（≥3 个月活动，或 ≥2 个独立下游）、补丁有回归测试（含**同实例复用**用例，覆盖残余 job）、我们独立复算 sha256、16 KB 逐 ABI 复核、设备门全绿；
4. 走用户裁定（改"钉版不动"），并在契约/KD-66 回写。
   - 现状评估：pa2x2 fork 只满足 3 的一部分（有 3 个回归测试，但无复用测试、维护信号为 0），**现在不触发**。

**D. 换 ng 底座**，当且仅当同时满足：
1. 出现**发布版、可复用**的 Kotlin/Android ng 绑定（Maven 坐标、MIT/Apache、minSdk ≤23、含 `memoryLimit`/`interrupt`/`JS_ExecutePendingJob` 语义、有 release 记录），**或**用户明确批准自建并长期维护；
2. ng 的 #1753（C 循环不 poll 中断）等 open 中断缺口关闭，或我们接受"L2 仍是唯一硬终止"并写进契约；
3. 钉定版本的中断 handler 签名与 shim 一致（v0.17.0 旧签名 / master `JSContext*`），且绑定层排水循环有显式上界（不能只依赖引擎返回 -1）；
4. 设备实证：自续 job 在硬期限内返回拒绝、后续请求可用；16 KB/R8/sha 全链复核。
   - **禁止**：仅因 #240 就换 ng（证据：修复在绑定层；ng 的 `JS_ExecutePendingJob` 同样无上界；ng 官方明说循环由嵌入方负责）。

### 4.4 明确不做（本批）

- 不改 `libs.versions.toml` / AAR 钉版；不引入 fork AAR；不建 ng 分支；不因为 #240 打开 JS 广告或 4E.2。

---

## 5. 任务四：若 #240 已修复——版本与升级路径

**当前答案：未修复。** 判定依据（§2.1-2.3）：issue open/0 评论/无 PR；main 停在 2026-09-03；最新 release 与 Maven 最高版本都是 1.0.15。**不存在"修复版本"可指。** 唯一包含修复的构件是 `pa2x2/quickjs-kt` 的 `quickjs-kt 1.0.15-fork.1`（GitHub release，非 Maven，§2.6），按 §4.3-C 未触发。

未来若修复落版，升级路径（预写，便于触发时直接执行）：

1. **确认版本**：`gh api repos/dokar3/quickjs-kt/releases/latest` 出现 `>v1.0.15`；release notes/commit 引用 #240；`curl` Maven `maven-metadata.xml` 出现同版本。
2. **改钉版**：`gradle/libs.versions.toml` 的 `quickjs-kt-android` 版本号 + 记录的 sha256（下载新 AAR 本地复算，并与官方 `.sha1` 双向核对）。
3. **复核供应链**：16 KB 逐 ABI ELF 程序头（PT_LOAD/RELRO 判定沿用 `2026-10-25-16kb-arm64-api23-build.md` 方法）、`aar-metadata minCompileSdk`、manifest minSdk、consumer proguard、POM 传递依赖（kotlin-stdlib 版本约束 KD-73 未证前不动）。
4. **反转 KD-66 用例**：把观察型 `issue240_selfQueuingJobHangs_KnownOpen` 改为"必须 `QuickJsInterruptedException`、hard+500 ms 内返回、后续请求可用"的断言（关闭判据见 `known-defects.md` KD-66）。
5. **保留 L2**：即使引擎修复，L2 仍是任意脚本的最终硬终止层，不因升级而拆除。
6. **回写**：契约 §4/§5、KD-66、本文件补一条"已于 vX 修复"的记录。

---

## 6. 未验证项（如实登记）

- **未运行 Gradle / 未跑设备**：fork AAR 的 #240 实际行为、close 行为、同实例复用行为均 `UNVERIFIED`；ng 的任何构建/加载也未执行。
- **bellard `promise_reaction_job` → 排水继续**的机制是源码链推断（与 issue 描述、KD-66 设备线程栈一致），未在设备上逐步验证。
- **ng v0.17.0 的 uncatchable → -1 能否让 quickjs-kt 的 JNI 排水退出**：需要把 ng 源码换进 binding 并实跑，未做；`quickjs_jni.c:1011-1018` 的 `ret<0 → 抛异常 + JNI_FALSE` 只是静态读码。
- fork AAR 的 16 KB 判定只做了本地 ELF 解析（未用 NDK/llvm-readelf 交叉复核），真机加载未验。
- fork 发布者的身份/动机/后续维护意愿未知；`SHA256SUMS` 只证明"下载字节与发布者声明一致"，不证明构建可复现。
- `react-native-quickjs/quickjs` 只读了 README 摘要，未评估其 JSI API 是否可用于非 RN 的 Kotlin 宿主（结论"不是通用绑定"基于其自述与平台约束）。
- ng 的 release cadence 数字来自其 `diff.md` 自述，不是独立统计。

---

## 7. 来源

**上游 issue / 仓库**

1. issue #240 — https://github.com/dokar3/quickjs-kt/issues/240
2. 修复提交（fork） — https://github.com/dokar3/quickjs-kt/commit/fbe42274f2a8a0a7dc19a7ec44a488a7da1ddce6
3. fork 仓库 — https://github.com/pa2x2/quickjs-kt
4. fork release `quickjs-kt-1.0.15-fork.1` — https://github.com/pa2x2/quickjs-kt/releases/tag/quickjs-kt-1.0.15-fork.1
5. 上游 release `v1.0.15` — https://github.com/dokar3/quickjs-kt/releases/tag/v1.0.15
6. issue #137（迁 ng 请求 + 维护者回复） — https://github.com/dokar3/quickjs-kt/issues/137
7. 上游 `main` HEAD `a081755b` — https://github.com/dokar3/quickjs-kt/tree/a081755bcdb18ee6afa7ffee25b8fa976f06d31f

**quickjs-kt 1.0.15 源码（`?ref=v1.0.15`）**

8. `QuickJs.jni.kt:583-595`（排水循环） — https://github.com/dokar3/quickjs-kt/blob/v1.0.15/quickjs/src/jniMain/kotlin/com/dokar/quickjs/QuickJs.jni.kt#L583-L595
9. `quickjs_jni.c:984-1022`（executePendingJob） — https://github.com/dokar3/quickjs-kt/blob/v1.0.15/quickjs/native/jni/quickjs_jni.c#L984-L1022
10. `quickjs_interrupt.c`（中断状态机） — https://github.com/dokar3/quickjs-kt/blob/v1.0.15/quickjs/native/common/quickjs_interrupt.c

**bellard/quickjs（`04be246`）**

11. `quickjs.c:53386-53423`（promise_reaction_job） — https://github.com/bellard/quickjs/blob/04be246001599f5995fa2f2d8c91a0f198d3f34c/quickjs.c#L53386-L53423
12. `quickjs.c:7860-7875`（interrupted + js_poll_interrupts） — https://github.com/bellard/quickjs/blob/04be246001599f5995fa2f2d8c91a0f198d3f34c/quickjs.c#L7860-L7875
13. `quickjs.h:925-926`（interrupt handler 签名） — https://github.com/bellard/quickjs/blob/04be246001599f5995fa2f2d8c91a0f198d3f34c/quickjs.h#L925-L926

**quickjs-ng**

14. 仓库 — https://github.com/quickjs-ng/quickjs
15. v0.17.0 `quickjs.c`（`promise_reaction_job` 55620-55623；`js_poll_interrupts` 8653-8677；`JS_ExecutePendingJob`） — https://github.com/quickjs-ng/quickjs/blob/v0.17.0/quickjs.c
16. master `quickjs.c:2525-2552`（`JS_ExecutePendingJob`） — https://github.com/quickjs-ng/quickjs/blob/master/quickjs.c#L2525-L2552
17. master `quickjs-libc.c:4852-4877`（`js_std_loop`） — https://github.com/quickjs-ng/quickjs/blob/master/quickjs-libc.c#L4852-L4877
18. issue #1095（js_std_loop 由嵌入方控制；saghul 回复） — https://github.com/quickjs-ng/quickjs/issues/1095
19. issue #1753（Array builtins 不 poll 中断，open） — https://github.com/quickjs-ng/quickjs/issues/1753
20. issue #1789（interrupt handler 改 `JSContext*`）+ commit `876442bd` — https://github.com/quickjs-ng/quickjs/issues/1789
21. `docs/docs/diff.md`（与 bellard 的差异、发布节奏） — https://github.com/quickjs-ng/quickjs/blob/master/docs/docs/diff.md
22. `docs/docs/supported_platforms.md`（Android NDK ≥26，limited testing） — https://github.com/quickjs-ng/quickjs/blob/master/docs/docs/supported_platforms.md
23. `docs/docs/projects.md`（使用项目清单，无 Kotlin/Android 绑定） — https://github.com/quickjs-ng/quickjs/blob/master/docs/docs/projects.md
24. `docs/docs/developer-guide/intro.md`（"Execution timeout and interrupts"） — https://github.com/quickjs-ng/quickjs/blob/master/docs/docs/developer-guide/intro.md

**其他**

25. react-native-quickjs（ng 的 RN JSI runtime，Alpha） — https://github.com/react-native-quickjs/quickjs
26. Maven Central `quickjs-kt-android` 版本清单 — https://repo1.maven.org/maven2/io/github/dokar3/quickjs-kt-android/maven-metadata.xml
27. KD-66 — `docs/known-defects.md`（"QuickJS issue #240 使引擎硬期限不能返回"）
28. 契约终止层 — `docs/current/visual-generation-contract.md:72`
29. 批次 21 AAR 研究 — `docs/research/2026-10-25-quickjs-aar-consumption.md` §2
30. 引擎选型与后备触发条件 — `docs/research/2026-10-25-js-engine-selection.md` §5/§6
31. `:visual_js` 隔离进程研究 — `docs/research/2026-10-26-visualjs-isolated-process.md`
