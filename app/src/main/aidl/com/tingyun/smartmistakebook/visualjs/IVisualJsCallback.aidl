// Batch 22 T1 (4E.2 prerequisite): one-way result callback from the :visual_js isolated process to the host.
//
// Contract §4 / R1 §4:
// - Both methods are oneway: the worker only posts; the host enqueues on the Binder thread
//   and reads the PFD on IO, never blocking the Binder thread.
// - onResult carries the result envelope in an in ParcelFileDescriptor, so large JSON never
//   travels in the Binder transaction (symmetric with the request direction).
// - onFatal is a best-effort pre-death cause; the host must also rely on binder death /
//   onServiceDisconnected because delivery is not guaranteed.
//
// The host validates generation/requestId: stale generation, stale request, or a caller that
// is not the worker UID is dropped and the fd closed (never treat another frame as this one).

package com.tingyun.smartmistakebook.visualjs;

import android.os.ParcelFileDescriptor;

/**
 * 批次 22 T1（4E.2 前置）：隔离进程 → 宿主的单向结果面。
 *
 * 两个方法都必须是 oneway：worker 只投递不等待；宿主在 Binder 线程入队、在 IO 线程读
 * PFD，绝不在 Binder 线程做重活。onResult 的结果信封走 in PFD（与请求方向对称，大 JSON
 * 不进 Binder 事务）；onFatal 只是尽力而为的死前因，投递后立即 SIGKILL，宿主必须同时
 * 依赖 Binder death / onServiceDisconnected 兜底（投递不保证）。
 *
 * 宿主核对 generation/requestId：stale generation、stale request、或调用方不是 worker
 * UID 的帧一律丢弃并关闭 fd（绝不把另一帧当成本次结果）。
 */
interface IVisualJsCallback {
    oneway void onResult(long generation, String requestId, int status, in ParcelFileDescriptor payload, String diagnostics);
    oneway void onFatal(long generation, String requestId, int code, String diagnostics);
}
