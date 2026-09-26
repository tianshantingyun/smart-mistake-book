# -*- coding: utf-8 -*-
r"""修 JSONL 里"反斜杠没双写"的非法转义（LaTeX 进 JSON 的必犯错误）。

## 它消灭的失败

代理把 LaTeX 写进 JSON 字符串时，容易把 `\\lambda` 写成 `\lambda`——JSON 里 `\l` 不是合法转义，
整行**解析不能**。实测（2026-09-27）：470 页并入库时，就这一行把"并入"这一步整批带倒
（并入器对坏文件是硬报错）。意图无歧义：文本里要的是字面反斜杠（LaTeX 命令），所以修法是把
"非法的那个反斜杠"双写。

## 判据（只动非法的那一个，不碰合法转义）

逐个反斜杠看后一个字符：属于 `" \\ / b f n r t u` 的是**合法 JSON 转义**，原样保留；
其余的（`\l`、`\c`、`\m`…）双写成 `\\`。修完必须能 `json.loads` 通过，否则该行原样不动并报出来。

用法：
    PYTHONPATH=tools python -m kb_coverage.fix_jsonl_escapes --dir <目录或文件>       # 报告
    PYTHONPATH=tools python -m kb_coverage.fix_jsonl_escapes --dir <目录或文件> --write
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

VALID_AFTER = set('"\\/bfnrtu')


def repair_line(raw: str) -> tuple[str, int]:
    """返回 (修好的行, 改了几个反斜杠)。只在确实无法解析时调用。"""
    out: list[str] = []
    fixed = 0
    i = 0
    while i < len(raw):
        ch = raw[i]
        if ch == "\\":
            nxt = raw[i + 1] if i + 1 < len(raw) else ""
            if nxt in VALID_AFTER:
                out.append(ch)
            else:
                out.append("\\\\")          # 双写：文本里仍是字面反斜杠
                fixed += 1
            i += 1
            continue
        out.append(ch)
        i += 1
    return "".join(out), fixed


def fix_file(path: Path, write: bool) -> tuple[int, int, list[str]]:
    """返回 (修好的行数, 改动数, 仍失败的行说明)。"""
    lines = path.read_text(encoding="utf-8").splitlines()
    out: list[str] = []
    fixed_lines = changed = 0
    still: list[str] = []
    for ln, raw in enumerate(lines, 1):
        s = raw.strip()
        if not s:
            out.append(raw)
            continue
        cand = s[:-1].rstrip() if s.endswith(",") else s
        try:
            json.loads(cand)
            out.append(raw)
            continue
        except json.JSONDecodeError:
            pass
        fixed_text, n = repair_line(cand)
        try:
            json.loads(fixed_text)
        except json.JSONDecodeError as e:
            still.append(f"{path.name}:L{ln} 修后仍不可解析：{e.msg}")
            out.append(raw)
            continue
        out.append(fixed_text + ("," if s.endswith(",") else ""))
        fixed_lines += 1
        changed += n
    if write and fixed_lines:
        path.write_text("\n".join(out) + "\n", encoding="utf-8")
    return fixed_lines, changed, still


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dir", type=Path, required=True, help="目录（递归找 *.jsonl）或单个文件")
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)
    files = [args.dir] if args.dir.is_file() else sorted(args.dir.rglob("*.jsonl"))
    if not files:
        print(f"没有 jsonl：{args.dir}")
        return 2
    total_lines = total_changed = 0
    all_still: list[str] = []
    for f in files:
        n_lines, n_changed, still = fix_file(f, args.write)
        if n_lines or still:
            print(f"  {f}: 修好 {n_lines} 行（双写反斜杠 {n_changed} 处）" +
                  (f"；仍失败 {len(still)} 行" if still else ""))
        total_lines += n_lines
        total_changed += n_changed
        all_still += still
    print(f"\n{'已修' if args.write else '待修'} {total_lines} 行、共 {total_changed} 处反斜杠"
          f"（{len(files)} 个文件）" + ("（未写盘；加 --write 生效）" if not args.write else ""))
    for s in all_still[:10]:
        print("   !", s)
    return 1 if all_still else 0


if __name__ == "__main__":
    raise SystemExit(main())
