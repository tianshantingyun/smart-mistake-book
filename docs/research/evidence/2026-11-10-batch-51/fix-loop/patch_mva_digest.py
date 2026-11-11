#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""只读派生：把 MVA gate 产物重算后的 p95 与独立性说明写回 readings_digest.json。

背景（批次51 修复环）：
- 原 digest 的 gate.p95_ms 来自适配器渲染延迟（32 样本 35.15 ms，met=true），
  与设计 §6「首张合格图 p95（请求→首图）」口径不符；机器可读门因此可能被误读。
- `mva_gate_run.py` 已改为用作者 `elapsed_ms` + 渲染延迟重算端到端 p95；
  本脚本把同一 `gate/mva_preflight.json` 的 JS 读数写回 digest，并把旧渲染读数
  保存在 gate.p95_ms_pre_fixloop（不删除历史）。
- 同时登记：判官与作者链同为 deepseek-flash（同源自证）；critical_errors 的 0
  是结构性结果，citable 只表示有可评样本。

不手写任何门值：值全部从 mva_preflight.json 读取。运行：
python -I -X utf8 patch_mva_digest.py
"""

from __future__ import annotations

import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
GATE = ROOT / "docs/research/evidence/2026-11-10-batch-51/mva-gate/gate"
DIGEST = GATE / "readings_digest.json"
PREFLIGHT = GATE / "mva_preflight.json"


def main() -> int:
    digest = json.loads(DIGEST.read_text(encoding="utf-8"))
    preflight = json.loads(PREFLIGHT.read_text(encoding="utf-8"))
    js = preflight["readings"]["JS"]
    new_p95 = js["first_image_p95_ms"]
    old_p95 = digest["gate"]["p95_ms"]

    if not isinstance(new_p95.get("value"), (int, float)):
        raise SystemExit("new p95 value missing; refusing to write")
    if new_p95.get("scope") != "request_to_first_image":
        raise SystemExit(f"unexpected p95 scope: {new_p95.get('scope')!r}")

    digest["gate"]["p95_ms_pre_fixloop"] = {
        "met": old_p95.get("met"),
        "samples": old_p95.get("samples"),
        "threshold_ms": old_p95.get("threshold_ms"),
        "value": old_p95.get("value"),
        "scope": "render_only",
        "note": "修复环前误把适配器渲染延迟当请求→首图；保留原值供对照。",
    }
    digest["gate"]["p95_ms"] = {
        "met": new_p95["met"],
        "samples": new_p95["samples"],
        "threshold_ms": new_p95["threshold_ms"],
        "value": new_p95["value"],
        "scope": new_p95["scope"],
        "render_only": new_p95["render_only"],
        "note": ("端到端口径 = 作者模型 elapsed_ms + 设备渲染延迟；"
                 "render_only 为并列读数，不参与门判。"),
    }
    digest["gate"]["critical_errors"]["interpretation"] = (
        "critical_errors=0 是结构性结果：本轮无『自我接受后交付』阶段，"
        "citable=true 只表示有可评样本，不表示模型未犯关键错误；不得作为独立能力证据。"
    )
    digest["independence"] = {
        "author_model": "deepseek-flash",
        "judge_model": "deepseek-flash",
        "same_model": True,
        "note": ("判官与作者链同模型（作者 temperature 0.1 / 判官 0.0）；"
                 "32/120 与 6 组读数为同源自证，不作独立能力证据；"
                 "门 FAIL 不依赖判官：RENDERED 95/120 < 108 已使首次门必然 FAIL。"),
    }
    digest["fixloop"] = {
        "batch": 51,
        "p95_scope_fix": "adapter_render_only -> request_to_first_image",
        "p95_source": "gate/mva_preflight.json readings.JS.first_image_p95_ms",
        "gate_verdict_unchanged": "FAIL",
    }

    DIGEST.write_text(json.dumps(digest, ensure_ascii=False, indent=1, sort_keys=True) + "\n",
                      encoding="utf-8", newline="\n")
    print(json.dumps({
        "p95_value_ms": digest["gate"]["p95_ms"]["value"],
        "p95_met": digest["gate"]["p95_ms"]["met"],
        "render_only_ms": digest["gate"]["p95_ms"]["render_only"]["value"],
        "critical_errors_citable": digest["gate"]["critical_errors"]["citable"],
        "same_model": digest["independence"]["same_model"],
    }, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
