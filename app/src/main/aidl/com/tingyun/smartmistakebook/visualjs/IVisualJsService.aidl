// 批次 22 T1（4E.2 前置）：宿主 → :visual_js 隔离进程的控制面。
//
// 契约 §4 与 R1（docs/research/2026-10-26-visualjs-isolated-process.md §4）：
// - initialize/execute 是两路调用，只做 UID/generation/单在飞校验与投递，立刻返回；
//   JS 永不在 Binder 线程执行。
// - request 是 in ParcelFileDescriptor：programJson + limits 包在一个有界信封 JSON 里
//   （单向上限 256 KiB），不把大 JSON 塞进 Binder 事务。
// - terminate 是 oneway：Binder 线程上核对当前 generation 后由服务进程自己
//   killProcess(myPid())；过期 terminate 必须 no-op，且不排在 JS 执行队列后面。
//
// 返回码与 generation 语义见 Kotlin VisualJsWire（STATUS_ACCEPTED / STATUS_BUSY /
// STATUS_STALE_GENERATION / STATUS_BAD_REQUEST / STATUS_SHUTTING_DOWN）。
package com.tingyun.smartmistakebook.visualjs;

import android.os.ParcelFileDescriptor;
import com.tingyun.smartmistakebook.visualjs.IVisualJsCallback;

interface IVisualJsService {
    // 不匹配即 fail-closed（宿主在 onServiceConnected 里比对后解绑）。
    int protocolVersion();

    // 服务进程 UID；宿主断言 != Process.myUid()（isolatedProcess 真的生效）。
    int workerUid();

    // 诊断用；宿主不以它作为控制依据。
    long currentGeneration();

    // 0 = 已初始化；重复 initialize 同 generation 幂等，不同 generation 回 STALE。
    int initialize(long generation);

    // 0 = ACCEPTED；1 = BUSY；2 = STALE_GENERATION；3 = BAD_REQUEST；4 = SHUTTING_DOWN。
    int execute(long generation, String requestId,
                in ParcelFileDescriptor request, IVisualJsCallback callback);

    // 控制面 oneway：generation 匹配才自杀；不排 JS 队列。
    oneway void terminate(long generation);
}
