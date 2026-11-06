#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模拟器低负载窗门（批次 41 / WS4 item 25/28）。

## 它消灭的具体失败

批次 40 的 isolation 证据只能写"单臂 15/15 绿"，因为**没有可判定的低负载窗**：
`step04-isolation.md` 的 I50-1 是"load<1.0 持续 5 min"，既没有采样口径，也没有
相对地板——4 vCPU 上 `la1=1.0` 是 25% 容量、2 vCPU 上是 50%，同一数字含义不同；
而套件自身就会把 `la1` 顶到 1 附近。于是"这一窗是干净的"只能靠印象，红集差异
无法归因（批次 39 三窗 6/7/5 红、kill 计数 10/10/6）。

本脚本把"窗"变成一个**可复跑、可审计、fail-closed** 的对象：
- `snapshot`：进程快照（无并发 runner / 无 `.test` / 无孤儿 `:visual_js`）；
- `calibrate`：装包 settle 后在同一 AVD 上标定 **地板**（busy% / la1/nproc /
  ctxt/s / 唤醒延迟 p95），地板是输入而不是常数；
- `gate`：设备内逐 **10 s** 采样、窗口 5 min（31 点 = 30 个差分）、缺 1 点即
  INVALID；每点过 `max` 界、分布过 `p95` 界，不取平均、不剔最大值；
- `evaluate`：离线重判（同一份 CSV/JSON 可复核，不依赖设备在场）。

`verdict=INVALID` 时退出码 1，且 `render_instrument_args()` 只产出
`-e smb.lowload.gate INVALID`——androidTest 侧（`LowLoadProbe` + 套件前置门）
据此把整套件标 `UNGATED` 并跳过，跳过**不算通过**（报告工具把 `-4` 单列）。

## 与 R5 的口径差异（如实）

R5（`docs/research/2026-11-03-low-load-gating.md` §0.5/§4.1）建议 5 s × 60 样本；
本批任务书要求**逐 10 s 采样**。窗口长度仍取 5 min（`EXP_1=1884` → 窗口前事件
残余权重 < 1%），因此窗口是 31 个采样点 = 30 个差分，`expected_samples=30`。
采样间隔同时作为 fail-closed 检查项：任一 `Δt` 落在 [8, 15] s 之外即 INVALID，
防止 adb 往返把窗口拉长。

## 权限边界（R5 §2.2 实测）

API 34 上 app/instrumentation 读不到 `/proc/stat`、`/proc/loadavg`（EACCES），
所以门只能在 **adb shell 侧**判定；instrumentation 内只能做自证探针
（`app/src/androidTest/.../LowLoadProbe.kt`）。本脚本的采样循环在**设备内**执行，
避免宿主逐样本 `adb shell` 拉长间隔。

## 退出码

- 0：窗口 PASS（或 snapshot 三条全 0）；
- 1：窗口 INVALID（含样本缺失、地板缺失、能力缺失、并发/孤儿进程）；
- 2：用法/设备不可达/输出无法解析（没有"通过"可言）。

## 用法

    export PATH="$PATH:/c/Android/Sdk/platform-tools"
    python -I tools/ci/lowload_gate.py snapshot --serial emulator-5584
    python -I tools/ci/lowload_gate.py calibrate --serial emulator-5584 --out build/lowload/floor.json
    python -I tools/ci/lowload_gate.py gate --serial emulator-5584 \
        --floor build/lowload/floor.json --out build/lowload/gate.json \
        --emit-args build/lowload/instrument.args
    python -I tools/ci/lowload_gate.py evaluate --floor floor.json \
        --window window.json --wakeup wakeup.json
"""

from __future__ import annotations

import argparse
import json
import math
import subprocess
import sys
import time
from pathlib import Path

# 判据常数（R5 §4.3 L-3）。相对地板，不提供放宽参数。
BUSY_P95_BAND_PP = 3.0
BUSY_MAX_BAND_PP = 5.0
LA_P95_BAND = 0.05
LA_MAX_ABS = 0.30
CTXT_P95_FACTOR = 1.5
WAKEUP_P95_FACTOR = 3.0
WAKEUP_P95_FLOOR_MS = 30.0
WAKEUP_MAX_FACTOR = 5.0
INTERVAL_TOLERANCE = (0.8, 1.5)
DEFAULT_SAMPLES = 30          # 30 个差分 = 31 个点 = 5 min @ 10 s
DEFAULT_INTERVAL_S = 10.0
DEFAULT_WAKEUP_SAMPLES = 40
WAKEUP_TARGET_MS = 50.0
USER_HZ = 100.0


def percentile(values, q: float) -> float | None:
    """nearest-rank 分位数（R5 §4.3：不插值，不做平滑）。"""
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, math.ceil(q * len(ordered)))
    return float(ordered[min(rank, len(ordered)) - 1])


def parse_sample_lines(lines) -> list[dict]:
    """解析设备侧循环的输出行。

    形状：``uptime|loadavg|cpu line|procs_running N|procs_blocked N|ctxt N``。
    空行忽略；任何非空行的形状不符都抛 [ValueError]——**不得静默丢行**，否则
    "样本缺失"会被写成"窗口通过"。
    """
    samples = []
    for raw in lines:
        line = raw.strip()
        if not line:
            continue
        parts = line.split("|")
        if len(parts) != 6:
            raise ValueError(f"unexpected sample row ({len(parts)} fields): {line!r}")
        uptime_text, loadavg_text, cpu_text, run_text, blk_text, ctxt_text = parts
        cpu_fields = cpu_text.split()
        if not cpu_fields or cpu_fields[0] != "cpu":
            raise ValueError(f"unexpected cpu row: {cpu_text!r}")
        ticks = []
        for token in cpu_fields[1:]:
            ticks.append(int(token))
        if len(ticks) < 8:
            ticks.extend([0] * (10 - len(ticks)))
        samples.append({
            "uptime": float(uptime_text),
            "la1": float(loadavg_text.split()[0]),
            "cpu": ticks[:10],
            "procs_running": int(run_text.split()[1]),
            "procs_blocked": int(blk_text.split()[1]),
            "ctxt": int(ctxt_text.split()[1]),
        })
    return samples


def compute_metrics(samples: list[dict], nproc: int) -> dict:
    """由相邻采样点差分出窗口指标（busy% / la1/nproc / ctxt/s）。"""
    busy_pct, la_ratio, ctxt_per_s, intervals = [], [], [], []
    for previous, current in zip(samples, samples[1:]):
        dt = current["uptime"] - previous["uptime"]
        if dt <= 0:
            raise ValueError("non-monotonic /proc/uptime in samples")
        intervals.append(dt)
        # 字段：user nice system idle iowait irq softirq steal guest guest_nice
        before, after = previous["cpu"], current["cpu"]
        busy_delta = sum(
            after[i] - before[i] for i in (0, 1, 2, 5, 6, 7)
        )
        total_delta = busy_delta + (after[3] - before[3]) + (after[4] - before[4])
        busy_pct.append(100.0 * busy_delta / total_delta if total_delta > 0 else 0.0)
        la_ratio.append(current["la1"] / float(nproc) if nproc > 0 else 0.0)
        ctxt_per_s.append((current["ctxt"] - previous["ctxt"]) / dt)
    return {
        "n": len(busy_pct),
        "nproc": nproc,
        "busy_pct": busy_pct,
        "la_ratio": la_ratio,
        "ctxt_per_s": ctxt_per_s,
        "procs_running": [sample["procs_running"] for sample in samples],
        "interval_s": intervals,
        "uptime_span_s": (samples[-1]["uptime"] - samples[0]["uptime"]) if len(samples) > 1 else 0.0,
    }


def parse_wakeup_lines(lines) -> list[float]:
    """解析唤醒探针行：每行 ``start_ns end_ns``；返回迟到量（ms）。"""
    lateness = []
    for raw in lines:
        line = raw.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) != 2:
            raise ValueError(f"unexpected wakeup row: {line!r}")
        start_ns, end_ns = int(parts[0]), int(parts[1])
        lateness.append((end_ns - start_ns) / 1_000_000.0 - WAKEUP_TARGET_MS)
    return lateness


def summarize_wakeup(lateness_ms: list[float]) -> dict:
    return {
        "p50_ms": percentile(lateness_ms, 0.50),
        "p95_ms": percentile(lateness_ms, 0.95),
        "max_ms": max(lateness_ms) if lateness_ms else None,
        "n": len(lateness_ms),
    }


def evaluate_window(floor: dict, window: dict, wakeup: dict | None,
                    expected_samples: int = DEFAULT_SAMPLES) -> dict:
    """判读一个窗口。任一检查不过 → INVALID（不返回"部分通过"）。"""
    reasons: list[str] = []
    checks: dict[str, dict] = {}

    if not floor or not all(
        key in floor for key in (
            "busy_p95", "busy_max", "la_ratio_p95", "la_ratio_max",
            "ctxt_p95", "wakeup_p95_ms", "wakeup_max_ms", "nproc",
        )
    ):
        reasons.append("floor_missing")
    if window.get("n") != expected_samples:
        reasons.append("sample_count")
    if window.get("nproc") != floor.get("nproc"):
        reasons.append("nproc")

    intervals = window.get("interval_s") or []
    expected_interval = float(floor.get("interval_s", DEFAULT_INTERVAL_S))
    low, high = INTERVAL_TOLERANCE
    if not intervals or any(
        dt < expected_interval * low or dt > expected_interval * high for dt in intervals
    ):
        reasons.append("interval")

    if wakeup is None or wakeup.get("p95_ms") is None or wakeup.get("max_ms") is None:
        reasons.append("wakeup_missing")
    elif "wakeup_p95_ms" in floor:
        wakeup_p95_gate = max(
            WAKEUP_P95_FACTOR * float(floor["wakeup_p95_ms"]), WAKEUP_P95_FLOOR_MS
        )
        wakeup_max_gate = WAKEUP_MAX_FACTOR * float(floor["wakeup_max_ms"])
        checks["wakeup_p95"] = {"value": wakeup["p95_ms"], "limit": wakeup_p95_gate}
        checks["wakeup_max"] = {"value": wakeup["max_ms"], "limit": wakeup_max_gate}
        if float(wakeup["p95_ms"]) > wakeup_p95_gate:
            reasons.append("wakeup_p95")
        if float(wakeup["max_ms"]) > wakeup_max_gate:
            reasons.append("wakeup_max")

    busy = window.get("busy_pct") or []
    la = window.get("la_ratio") or []
    ctxt = window.get("ctxt_per_s") or []
    busy_p95 = percentile(busy, 0.95)
    la_p95 = percentile(la, 0.95)
    ctxt_p95 = percentile(ctxt, 0.95)
    checks["busy_p95"] = {"value": busy_p95, "limit": float(floor.get("busy_p95", 0)) + BUSY_P95_BAND_PP}
    checks["busy_max"] = {"value": max(busy) if busy else None,
                          "limit": float(floor.get("busy_max", 0)) + BUSY_MAX_BAND_PP}
    checks["la_ratio_p95"] = {"value": la_p95,
                              "limit": float(floor.get("la_ratio_p95", 0)) + LA_P95_BAND}
    checks["la_ratio_max"] = {"value": max(la) if la else None, "limit": LA_MAX_ABS}
    checks["ctxt_p95"] = {"value": ctxt_p95,
                          "limit": CTXT_P95_FACTOR * float(floor.get("ctxt_p95", 0))}

    if busy_p95 is not None and busy_p95 > checks["busy_p95"]["limit"]:
        reasons.append("busy_p95")
    if busy and max(busy) > checks["busy_max"]["limit"]:
        reasons.append("busy_max")
    if la_p95 is not None and la_p95 > checks["la_ratio_p95"]["limit"]:
        reasons.append("la_ratio_p95")
    if la and max(la) > LA_MAX_ABS:
        reasons.append("la_ratio_max")
    if ctxt_p95 is not None and ctxt_p95 > checks["ctxt_p95"]["limit"]:
        reasons.append("ctxt_p95")

    # 去重保序
    seen = set()
    reasons = [reason for reason in reasons if not (reason in seen or seen.add(reason))]

    return {
        "verdict": "PASS" if not reasons else "INVALID",
        "reasons": reasons,
        "expected_samples": expected_samples,
        "checks": checks,
        "summary": {
            "n": window.get("n"),
            "busy_p95": busy_p95,
            "busy_max": max(busy) if busy else None,
            "la_ratio_p95": la_p95,
            "la_ratio_max": max(la) if la else None,
            "ctxt_p95": ctxt_p95,
            "procs_running_max": max(window.get("procs_running") or [0]),
            "uptime_span_s": window.get("uptime_span_s"),
            "wakeup": wakeup,
            # 仅报告，不作门（R5 §4.3 L-3e）。
        },
        "floor": floor,
    }


def render_instrument_args(verdict: str, reason: str) -> str:
    """把门判词渲染成 `am instrument` 参数行（套件只认 PASS 才开跑）。

    注意 `am instrument` 的参数形状是 `-e NAME VALUE`（两个 token），**不是**
    `-e NAME=VALUE`；后者会让 am 把 `-e` 当成 VALUE，随后报
    `Invalid userId -2`（本轮实测）。reason 里的空白折成 `_`：参数经 adb shell
    拼接，带空格的值会被拆成多个 argv。
    """
    safe_reason = "_".join(str(reason or "").split())[:200]
    return f"-e smb.lowload.gate {verdict}\n-e smb.lowload.reason {safe_reason}\n"


# ---------------------------------------------------------------- 设备侧


def _adb(adb: str, serial: str, *args: str, timeout: int = 120) -> subprocess.CompletedProcess:
    command = [adb]
    if serial:
        command += ["-s", serial]
    command += list(args)
    return subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                          errors="replace", timeout=timeout)


def _shell(adb: str, serial: str, script: str, timeout: int = 120) -> subprocess.CompletedProcess:
    return _adb(adb, serial, "shell", script, timeout=timeout)


def read_nproc(adb: str, serial: str) -> int | None:
    result = _shell(adb, serial, "nproc")
    if result.returncode != 0:
        return None
    text = (result.stdout or "").strip()
    try:
        value = int(text)
    except ValueError:
        return None
    return value if value > 0 else None


def sample_script(samples: int, interval_s: float) -> str:
    return (
        "i=0; while [ $i -lt {samples} ]; do "
        "up=$(cut -d' ' -f1 /proc/uptime); "
        "la=$(cat /proc/loadavg); "
        "cpu=$(grep -m1 '^cpu ' /proc/stat); "
        "run=$(grep -m1 '^procs_running ' /proc/stat); "
        "blk=$(grep -m1 '^procs_blocked ' /proc/stat); "
        "ctx=$(grep -m1 '^ctxt ' /proc/stat); "
        'echo "$up|$la|$cpu|$run|$blk|$ctx"; '
        "i=$((i+1)); sleep {interval}; done"
    ).format(samples=samples, interval=interval_s)


def wakeup_script(samples: int) -> str:
    return (
        "i=0; while [ $i -lt {samples} ]; do "
        "a=$(date +%s%N); sleep 0.05; b=$(date +%s%N); "
        'echo "$a $b"; i=$((i+1)); done'
    ).format(samples=samples)


def snapshot_script() -> str:
    # R5 L-4：三条都必须是 0；`grep -c` 无命中时退出码 1，用 `|| true` 保留 "0"。
    # runner 计数不能用 `grep`：grep 自身的 argv 就是 `grep AndroidJUnitRunner`，会
    # 恒自匹配（实测得到 2 个假并发）；`sh -c` 的 argv 也含脚本原文。这里用 awk 并按
    # NAME 排除 awk/sh 自身，只数真正的 app 进程。
    return (
        "echo -n 'test_processes='; ps -A -o NAME | grep -c '\\.test$' || true; "
        "echo -n 'runner_processes='; ps -A -o NAME,ARGS | "
        "awk '$1 != \"awk\" && $1 != \"sh\" && /AndroidJUnitRunner/ {c++} END {print c+0}'; "
        "echo -n 'orphan_workers='; ps -A -o NAME | grep -c ':visual_js$' || true; "
        "echo -n 'window_animation_scale='; settings get global window_animation_scale; "
        "echo -n 'transition_animation_scale='; settings get global transition_animation_scale; "
        "echo -n 'animator_duration_scale='; settings get global animator_duration_scale"
    )


def parse_snapshot(text: str) -> dict:
    values: dict[str, object] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if key in {"test_processes", "runner_processes", "orphan_workers"}:
            try:
                values[key] = int(value)
            except ValueError:
                values[key] = None
        else:
            values[key] = value
    return values


def take_snapshot(adb: str, serial: str) -> dict:
    result = _shell(adb, serial, snapshot_script())
    if result.returncode != 0:
        return {"ok": False, "error": (result.stderr or result.stdout or "").strip()}
    parsed = parse_snapshot(result.stdout or "")
    missing = [key for key in ("test_processes", "runner_processes", "orphan_workers")
               if parsed.get(key) is None]
    if missing:
        return {"ok": False, "error": f"snapshot fields missing: {missing}", **parsed}
    parsed["ok"] = all(parsed.get(key) == 0 for key in
                       ("test_processes", "runner_processes", "orphan_workers"))
    return parsed


def take_window(adb: str, serial: str, samples: int, interval_s: float) -> dict:
    nproc = read_nproc(adb, serial)
    if nproc is None:
        return {"ok": False, "error": "nproc_unavailable"}
    # 采样循环本身按 points 计：n 个差分需要 n+1 个点。
    points = samples + 1
    result = _shell(adb, serial, sample_script(points, interval_s),
                    timeout=int(points * interval_s) + 120)
    if result.returncode != 0:
        return {"ok": False, "error": (result.stderr or result.stdout or "").strip()}
    try:
        parsed = parse_sample_lines((result.stdout or "").splitlines())
        metrics = compute_metrics(parsed, nproc)
    except ValueError as failure:
        return {"ok": False, "error": f"parse: {failure}"}
    metrics["ok"] = True
    metrics["interval_expected_s"] = interval_s
    return metrics


def take_wakeup(adb: str, serial: str, samples: int) -> dict:
    result = _shell(adb, serial, wakeup_script(samples), timeout=max(120, samples))
    if result.returncode != 0:
        return {"ok": False, "error": (result.stderr or result.stdout or "").strip()}
    try:
        lateness = parse_wakeup_lines((result.stdout or "").splitlines())
    except ValueError as failure:
        return {"ok": False, "error": f"parse: {failure}"}
    summary = summarize_wakeup(lateness)
    summary["ok"] = summary["n"] > 0
    return summary


# ---------------------------------------------------------------- CLI


def _write(path: str | None, payload: dict) -> None:
    if not path:
        return
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def _load(path: str) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def command_snapshot(args) -> int:
    snapshot = take_snapshot(args.adb, args.serial)
    _write(args.out, snapshot)
    print(json.dumps(snapshot, ensure_ascii=False))
    if snapshot.get("ok"):
        return 0
    return 1


def command_calibrate(args) -> int:
    if args.settle > 0:
        print(f"settle {args.settle}s", file=sys.stderr)
        time.sleep(args.settle)
    snapshot = take_snapshot(args.adb, args.serial)
    if not snapshot.get("ok"):
        print(json.dumps({"ok": False, "error": "snapshot", "snapshot": snapshot},
                         ensure_ascii=False))
        _write(args.out, {"ok": False, "error": "snapshot", "snapshot": snapshot})
        return 1
    window = take_window(args.adb, args.serial, args.samples, args.interval)
    if not window.get("ok"):
        _write(args.out, {"ok": False, "error": window.get("error")})
        print(json.dumps(window, ensure_ascii=False))
        return 1
    wakeup = take_wakeup(args.adb, args.serial, args.wakeup_samples)
    if not wakeup.get("ok"):
        _write(args.out, {"ok": False, "error": wakeup.get("error")})
        print(json.dumps(wakeup, ensure_ascii=False))
        return 1
    floor = {
        "ok": True,
        "serial": args.serial,
        "captured_at_epoch": time.time(),
        "nproc": window["nproc"],
        "samples": window["n"],
        "interval_s": args.interval,
        "busy_p95": percentile(window["busy_pct"], 0.95),
        "busy_max": max(window["busy_pct"]),
        "la_ratio_p95": percentile(window["la_ratio"], 0.95),
        "la_ratio_max": max(window["la_ratio"]),
        "ctxt_p95": percentile(window["ctxt_per_s"], 0.95),
        "wakeup_p95_ms": wakeup["p95_ms"],
        "wakeup_max_ms": wakeup["max_ms"],
        "raw_window": {key: window[key] for key in
                       ("busy_pct", "la_ratio", "ctxt_per_s", "interval_s")},
        "raw_wakeup": wakeup,
        "snapshot": snapshot,
    }
    _write(args.out, floor)
    print(json.dumps(floor, ensure_ascii=False))
    return 0


def command_gate(args) -> int:
    try:
        floor = _load(args.floor)
    except (OSError, ValueError) as failure:
        print(f"lowload_gate: cannot read floor: {failure}", file=sys.stderr)
        return 2
    snapshot = take_snapshot(args.adb, args.serial)
    window = take_window(args.adb, args.serial, args.samples, args.interval)
    wakeup = take_wakeup(args.adb, args.serial, args.wakeup_samples)
    if not window.get("ok"):
        verdict = {"verdict": "INVALID", "reasons": ["device_or_parse"],
                   "error": window.get("error"), "snapshot": snapshot}
    else:
        verdict = evaluate_window(floor, window, wakeup, expected_samples=args.samples)
        verdict["snapshot"] = snapshot
        if not snapshot.get("ok"):
            verdict["reasons"].append("process_snapshot")
            verdict["verdict"] = "INVALID"
        # 动画置 0 是 R5 L-6 的预置；本工具只记录（共享设备禁改设置），
        # runner 侧统一加 `--no-window-animation` 中和窗口动画。
        verdict["animation_scales"] = {
            key: snapshot.get(key) for key in (
                "window_animation_scale", "transition_animation_scale",
                "animator_duration_scale",
            )
        }
    verdict["serial"] = args.serial
    verdict["captured_at_epoch"] = time.time()
    _write(args.out, verdict)
    if args.emit_args:
        target = Path(args.emit_args)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(
            render_instrument_args(verdict["verdict"], ",".join(verdict["reasons"])),
            encoding="utf-8",
        )
    print(json.dumps(verdict, ensure_ascii=False))
    return 0 if verdict["verdict"] == "PASS" else 1


def command_evaluate(args) -> int:
    try:
        floor = _load(args.floor)
        window = _load(args.window)
        wakeup = _load(args.wakeup)
    except (OSError, ValueError) as failure:
        print(f"lowload_gate: cannot read input: {failure}", file=sys.stderr)
        return 2
    verdict = evaluate_window(floor, window, wakeup, expected_samples=args.expected_samples)
    _write(args.out, verdict)
    if args.emit_args:
        target = Path(args.emit_args)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(
            render_instrument_args(verdict["verdict"], ",".join(verdict["reasons"])),
            encoding="utf-8",
        )
    print(json.dumps(verdict, ensure_ascii=False))
    return 0 if verdict["verdict"] == "PASS" else 1


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Low-load window gate (WS4 / R5)")
    sub = parser.add_subparsers(dest="command", required=True)

    def common(p):
        p.add_argument("--serial", default="", help="adb serial, e.g. emulator-5584")
        p.add_argument("--adb", default="adb", help="adb executable")

    snapshot = sub.add_parser("snapshot", help="process snapshot (L-4)")
    common(snapshot)
    snapshot.add_argument("--out")
    snapshot.set_defaults(func=command_snapshot)

    calibrate = sub.add_parser("calibrate", help="measure the per-AVD floor")
    common(calibrate)
    calibrate.add_argument("--samples", type=int, default=DEFAULT_SAMPLES)
    calibrate.add_argument("--interval", type=float, default=DEFAULT_INTERVAL_S)
    calibrate.add_argument("--wakeup-samples", type=int, default=DEFAULT_WAKEUP_SAMPLES)
    calibrate.add_argument("--settle", type=int, default=120)
    calibrate.add_argument("--out", required=True)
    calibrate.set_defaults(func=command_calibrate)

    gate = sub.add_parser("gate", help="judge one low-load window")
    common(gate)
    gate.add_argument("--floor", required=True)
    gate.add_argument("--samples", type=int, default=DEFAULT_SAMPLES)
    gate.add_argument("--interval", type=float, default=DEFAULT_INTERVAL_S)
    gate.add_argument("--wakeup-samples", type=int, default=DEFAULT_WAKEUP_SAMPLES)
    gate.add_argument("--out")
    gate.add_argument("--emit-args")
    gate.set_defaults(func=command_gate)

    evaluate = sub.add_parser("evaluate", help="re-judge an existing window offline")
    evaluate.add_argument("--floor", required=True)
    evaluate.add_argument("--window", required=True)
    evaluate.add_argument("--wakeup", required=True)
    evaluate.add_argument("--expected-samples", type=int, default=DEFAULT_SAMPLES)
    evaluate.add_argument("--out")
    evaluate.add_argument("--emit-args")
    evaluate.set_defaults(func=command_evaluate)

    return parser


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
