#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 v1(90) 与候选批次(40) 合成为**冻结的 v2 判官**（130 条）+ sha256 旁车。

## 它消灭的具体失败

扩集如果靠人手合并，三件事会各自出错且**没有门会红**：① 序列化约定漂了（缩进/排序/结尾换行
变一位，sha 就变，消费侧的"旁车 == 文件字节"断言直接红或**静默比错对象**）；② 候选与 v1 之间
出现逐字重复题（判官里同一道题算两次 ⇒ 指标被无声加权）；③ 合并后没人再核一遍章归属。
本脚本把这三条做成硬断言，并把结果写成与 v1 **逐字节同构**的形态。

## 顺序纪律（`docs/kb-golden-v2-protocol.md` §3）

本脚本是第 2 步：**跑它 = 判官迁移**。之后 v2 生效、继续冻结；v1 文件与旁车**全程不动**，
所以回退是一次"把消费点指回 v1"（15 处，见协议）。

用法（仓库根）：

    python tools/kb_coverage/freeze_golden_v2.py \
        [--v1 tools/kb_coverage/tables/golden_queries_v1.json] \
        [--candidate tools/kb_coverage/candidates/golden_queries_v2_candidate_2026-09-28.json] \
        [--out tools/kb_coverage/tables/golden_queries_v2.json] \
        [--dry-run]        # 只打印将要写的内容与 sha，不落盘
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import os
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from kb_coverage import check_golden_candidate as checker  # noqa: E402

# v1 的序列化约定**逐字节照抄**（实测：indent=1、sort_keys、不转义非 ASCII、末尾一个 \n）。
JSON_KW = dict(ensure_ascii=False, sort_keys=True, indent=1)
SORT_KEYS = ("subject", "chapter", "query", "expectedSlug")


def load(path: Path) -> list[dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list) or not data:
        raise SystemExit(f"不是非空数组：{path}")
    return data


def dump(rows: list[dict]) -> str:
    return json.dumps(rows, **JSON_KW) + "\n"


def sha256_hex(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def write_with_lf(path: Path, text: str) -> None:
    """按 **LF** 落盘（判官的字节约定）。

    这里踩过一次：Windows 上 `Path.write_text` 会把 `\\n` 翻成 `\\r\\n`，而 sha 若在内存字符串上算，
    旁车记录的就不是磁盘上那份字节——**每个消费方的 sha 校验都会红，而文件"看起来是对的"**。
    所以：显式 `newline="\\n"` 禁掉转换，并且 sha 一律**从落盘后的字节回读**（见 main）。
    """
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="合成并冻结金标 v2")
    parser.add_argument("--v1", type=Path, default=REPO / "tools/kb_coverage/tables/golden_queries_v1.json")
    parser.add_argument("--candidate", type=Path,
                        default=REPO / "tools/kb_coverage/candidates/golden_queries_v2_candidate_2026-09-28.json")
    parser.add_argument("--out", type=Path, default=REPO / "tools/kb_coverage/tables/golden_queries_v2.json")
    parser.add_argument("--pack", type=Path,
                        default=REPO / "core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)

    v1 = load(args.v1)
    candidate = load(args.candidate)

    # ① v1 必须仍是那版冻结集（防止"在已被改动的基线上合并"）
    v1_sha = sha256_hex(args.v1.read_text(encoding="utf-8"))
    v1_sidecar = args.v1.with_name(args.v1.name + ".sha256")
    if v1_sidecar.is_file():
        sealed = v1_sidecar.read_text(encoding="utf-8").strip()
        if sealed != v1_sha:
            raise SystemExit(f"v1 与它的旁车 sha 不符（文件 {v1_sha[:16]} vs 旁车 {sealed[:16]}）——"
                             "先查清 v1 是否被改动过，再谈迁移")
        print(f"  · v1 封存核对通过：sha256={v1_sha[:16]}…（{len(v1)} 条）")

    # ② 合并后逐项机械重核（章归属/存在性/唯一性/泄漏由 checker 复跑）
    merged_raw = list(v1) + list(candidate)
    duplicates = [q for q, n in collections.Counter(r["query"] for r in merged_raw).items() if n > 1]
    if duplicates:
        raise SystemExit("v1 与候选之间有逐字重复题面：%s" % duplicates[:3])
    merged = sorted(merged_raw, key=lambda r: tuple(r[k] for k in SORT_KEYS))

    with_path = args.out
    # 合并集要过一遍机械校验，但那次校验读的是"待写出去的内容"——写进系统临时目录，
    # 不在权威表目录里留中间物（dry-run 也照做，这样 dry-run 与真跑的校验是同一份）。
    tmp_candidate = Path(tempfile.gettempdir()) / f"golden-v2-merge-check-{os.getpid()}.json"
    tmp_candidate.write_text(json.dumps(merged, ensure_ascii=False), encoding="utf-8")
    try:
        code = checker.main(["--candidate", str(tmp_candidate), "--golden", str(args.v1),
                             "--pack", str(args.pack), "--skip-batch-rules"])
    finally:
        tmp_candidate.unlink(missing_ok=True)
    if code != 0:
        raise SystemExit("合并集未通过机械校验（上面的 ✗ 逐条）——未冻结任何东西")

    text = dump(merged)
    per_subject = collections.Counter(r["subject"] for r in merged)
    per_chapter = collections.Counter(r["chapter"] for r in merged)
    print(f"  · 合并：{len(v1)} + {len(candidate)} = {len(merged)} 条；"
          f"{len(per_chapter)} 章；科分布 " + " / ".join(f"{k} {v}" for k, v in sorted(per_subject.items())))
    print("  · 逐章条数：" + "；".join(f"{c}×{n}" for c, n in sorted(per_chapter.items())))

    if args.dry_run:
        print(f"  · --dry-run：未落盘（将写入的文本 sha256={sha256_hex(text)}）")
        return 0

    write_with_lf(args.out, text)
    raw = args.out.read_bytes()
    if b"\r\n" in raw:
        raise SystemExit("落盘后仍含 CRLF——字节约定是 LF，先查 newline 参数")
    sha = hashlib.sha256(raw).hexdigest()          # 封印取**磁盘字节**，不是内存字符串
    if raw.decode("utf-8") != text:
        raise SystemExit("落盘内容与预期文本不符（换行/编码被改动过）")
    args.out.with_name(args.out.name + ".sha256").write_text(sha + "\n", encoding="utf-8", newline="\n")
    print(f"  · 已写 {args.out}（{len(raw)} B，CRLF=0）")
    print(f"  · v2 sha256={sha}")
    print(f"  · 已写 {args.out.with_name(args.out.name + '.sha256')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
