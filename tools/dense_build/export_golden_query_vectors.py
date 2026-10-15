#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""导出金标 v2 129 条查询的**离线查询向量**为端侧 JVM fixture（`golden-query-vectors.i8`）。

## 为什么是"原文"（无前缀）

设备生产路径（`GoldenRetrievalInstrumentedTest` → `RoomKnowledgeBaseStore` →
`LiteRtDenseQueryEncoder.encode(queryText)`）不经过任何前缀（core/data 全库 Kotlin 无前缀
常量；前缀只存在于离线链与对拍 fixture）。批 11 的**设备编码探针**实测（129 条）：
  - 设备原文向量 ↔ 离线原文向量：min cosine 0.999776 / median 0.999834；
  - 设备原文向量 ↔ 离线前缀向量（`int8-queries.npy`）：min 0.9324 / median 0.9646。
⇒ 设备跑的确实是**原文**；本脚本按原文编码（与 `int8-queries.npy` 的"前缀"约定不同——
后者是离线判分器的历史口径，两者的差异登记在批 11 记录里）。

## 产物（入库，JVM 测试读）

`core/data/src/test/resources/dense/golden-query-vectors.i8`，文本、`#` 注释、转义与
`tokenizer-parity-cases.txt` 同口径，逐题三行：
```
[case] <index> \t <subject> \t <query（转义）>
[i8]   <scale repr> \t <512 个 int8 码（空格分隔；每向量对称量化，scale=max|v|/127，与 dense_asset.quantize_int8 同口径）>
[f32]  <512 个归一化浮点（repr）>
```
`[f32]` 是判分实际喂给假编码器的值（设备查询侧是 fp32；`[i8]` 是同一向量的规范量化表示，
两者由测试侧现场复核一致性——实测量化还原 cosine min≈0.99983）。

## 确定性

本地 HF 快照（钉 revision `profile["revision"]`）+ `build/dense-model/bge-small-zh-v1.5-int8.onnx`
+ 固定分词参数（快照 `sentence_bert_config.json` 的 do_lower_case + strip_accents=False）
+ `maxLen` 按档 ⇒ 逐字节可重生成。

用法（仓库根下）：
```
python tools/dense_build/export_golden_query_vectors.py
```
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dense_asset as D  # noqa: E402

OUT_RELATIVE = "core/data/src/test/resources/dense/golden-query-vectors.i8"
BACKSLASH = chr(92)


def escape(text: str) -> str:
    return (text.replace(BACKSLASH, BACKSLASH + BACKSLASH)
            .replace("\t", BACKSLASH + "t")
            .replace("\n", BACKSLASH + "n")
            .replace("\r", BACKSLASH + "r"))


def load_golden_sealed(root: Path) -> list:
    """按仓库 .sha256 封存复核读金标。

    **不改** `dense_asset.GOLDEN_SHA256`：该常量仍是 2026-10-14 金标迁移前的值
    （473dbeac…），本脚本只做只读旁路，并在输出里点名（属主需同步该常量与下游重生成）。
    """
    path = root.joinpath(*D.GOLDEN_RELATIVE.split("/"))
    seal = (path.parent / (path.name + ".sha256")).read_text(encoding="utf-8").strip()
    actual = D.sha256_file(path)
    if actual != seal:
        raise SystemExit("金标集与仓库 .sha256 封存不符：%s != %s" % (actual, seal))
    cases = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(cases, list) or not cases:
        raise SystemExit("金标集不是非空数组：%s" % path)
    return cases, actual


def encode_queries(root: Path, queries: list[str]) -> np.ndarray:
    """离线编码（与 `export_bge_int8.py` 同一条路径，唯文本不加前缀）。"""
    import onnxruntime as ort
    from transformers import AutoTokenizer

    profile = D.model_profile(None)
    snapshot = (Path.home() / ".cache" / "huggingface" / "hub"
                / ("models--" + "BAAI/bge-small-zh-v1.5".replace("/", "--"))
                / "snapshots" / profile["revision"])
    if not (snapshot / "config.json").is_file():
        raise SystemExit("本地快照缺失（钉 revision=%s）：%s" % (profile["revision"], snapshot))
    sbert = snapshot / "sentence_bert_config.json"
    kwargs = {}
    if sbert.is_file():
        do_lower_case = bool(json.loads(sbert.read_text(encoding="utf-8")).get("do_lower_case", False))
        kwargs = dict(do_lower_case=do_lower_case, strip_accents=False)
    tokenizer = AutoTokenizer.from_pretrained(str(snapshot), **kwargs)

    encoded = tokenizer(queries, padding=True, truncation=True,
                        max_length=profile["maxLen"], return_tensors="np")
    session = ort.InferenceSession(str(root / "build" / "dense-model" / "bge-small-zh-v1.5-int8.onnx"),
                                   providers=["CPUExecutionProvider"])
    vectors = session.run(None, {
        "input_ids": encoded["input_ids"].astype(np.int64),
        "attention_mask": encoded["attention_mask"].astype(np.int64),
        "token_type_ids": encoded["token_type_ids"].astype(np.int64),
    })[0]
    if vectors.shape != (len(queries), profile["dim"]):
        raise SystemExit("编码形状 %s 应为 (%d,%d)" % (vectors.shape, len(queries), profile["dim"]))
    return vectors / np.maximum(np.linalg.norm(vectors, axis=1, keepdims=True), 1e-12)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", default=None)
    args = parser.parse_args()
    root = D.repo_root(args.repo_root)
    profile = D.model_profile(None)

    cases, golden_sha = load_golden_sealed(root)
    queries = [case["query"] for case in cases]
    if len(set(queries)) != len(queries):
        raise SystemExit("金标题面有重复——fixture 的文本键会撞车")
    print("金标：cases=%d sha=%s（dense_asset.GOLDEN_SHA256=%s 为迁移前常量，属主需同步）"
          % (len(cases), golden_sha[:16], D.GOLDEN_SHA256[:16]))

    vectors = encode_queries(root, queries)
    quantized, scales = D.quantize_int8(vectors.astype(np.float32))
    dequantized = quantized.astype(np.float32) * scales[:, None]
    unit = lambda arr: arr / np.maximum(np.linalg.norm(arr, axis=1, keepdims=True), 1e-12)
    recheck = np.sum(unit(dequantized) * unit(vectors.astype(np.float32)), axis=1)
    if float(recheck.min()) < 0.9995:
        raise SystemExit("量化还原 cosine 过低（min=%.6f）——量化口径异常" % float(recheck.min()))
    print("量化还原 cosine：min=%.6f median=%.6f（阈值 0.9995）"
          % (float(recheck.min()), float(np.median(recheck))))

    lines = [
        "# golden-query-vectors — 金标 v2 查询的离线向量（设备同变换：原文，无前缀）",
        "# 生成：python tools/dense_build/export_golden_query_vectors.py（int8 ONNX + 冻结 tokenizer；见脚本头）",
        "# golden sha256=%s cases=%d dim=%d goldenConstantStale=%s"
        % (golden_sha, len(cases), profile["dim"], D.GOLDEN_SHA256[:16]),
        "# 用途：GoldenRetrievalJvmTest 的 B-fused-mirror 路——假编码器按题面返回 [f32] 向量；",
        "#       [i8] 是同一向量的规范 int8+scale 表示，测试侧复核两者一致（量化还原 cosine≥0.9995）。",
        "# 实测锚（批11 设备探针）：设备原文向量 ↔ 本文件离线原文向量 min cosine 0.999776 / median 0.999834。",
    ]
    fmt = lambda values: " ".join(repr(float(v)) for v in values)
    for index, case in enumerate(cases):
        lines.append("[case]\t%d\t%s\t%s" % (index, case["subject"], escape(case["query"])))
        lines.append("[i8]\t%s\t%s" % (repr(float(scales[index])),
                                       " ".join(str(int(v)) for v in quantized[index])))
        lines.append("[f32]\t%s" % fmt(vectors[index]))

    out = root.joinpath(*OUT_RELATIVE.split("/"))
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print("落盘：%s（%d 题，%d 行）sha256=%s"
          % (OUT_RELATIVE, len(cases), len(lines), D.sha256_file(out)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
