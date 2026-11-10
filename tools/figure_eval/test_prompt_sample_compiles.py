# -*- coding: utf-8 -*-
"""产品提示词最小样例会否被真实 SceneV1 编译器接受（批次 51 / MVA 产品侧阻断回归）。

**它消灭的具体失败**：`FigurePromptAssembler.JS_STATIC_SAMPLE` 曾把 `stroke` 写成字符串、
且 path 不带 `fill`——`visual-compiler.js` 的 `requireStroke`（requireObject）与 KD-78 ①
（fill 必填）会拒绝，作者模型照样例写出的程序系统性不可编译（120 题 RENDERED 0/104）。
本测试从 Kotlin 源码逐字提取样例，交给真实 `visual-sdk.js` + `visual-compiler.js`
（Node vm，与生产同源资产）执行；样例不可编译即红。

Node 缺失时 skip 并在 skip 原因里写明"未执行"——不伪造通过。本机（Windows，Node 26）
实际执行路径见证据 README。
"""

from __future__ import annotations

import json
import shutil
import subprocess
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent.parent
PROBE = HERE / "prompt_sample_compile_probe.mjs"


def _node() -> str:
    node = shutil.which("node")
    if not node:
        pytest.skip("node 不在 PATH：样例编译回归未执行（不是通过）")
    return node


def test_js_static_sample_compiles_with_the_production_compiler() -> None:
    node = _node()
    completed = subprocess.run(
        [node, str(PROBE)],
        cwd=str(REPO_ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=120,
    )
    payload = json.loads(completed.stdout.strip().splitlines()[-1])
    assert completed.returncode == 0, (
        "JS_STATIC_SAMPLE 不能被生产编译器接受："
        f"{payload.get('error')}（stdout={completed.stdout[-800:]} stderr={completed.stderr[-800:]}）"
    )
    assert payload["compiles"] is True, payload
    # 样例必须真的演示出两类节点：带 fill+stroke 的 path 与带 contentId 的 label——
    # 否则模型仍学不到文字/描边的合法形状（本测试钉的是"样例可编译且有教学面"）。
    assert payload["pathOps"] >= 2, payload
    assert payload["labelOps"] >= 1, payload
