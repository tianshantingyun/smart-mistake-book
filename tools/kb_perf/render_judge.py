#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""渲染机械判读（b35-render §2.1–2.2/§2.3/§2.5；不出采纳判词，p95 判读另由执行轮按 §2.6 做）。
用法: python -I tools/kb_perf/render_judge.py <render-capture.txt> <out-json>
退出码: 0=全部机械判据过；1=任一判据红；2=输入畸形（缺 RENDER 块/字段）。
长度口径：优先采信 Kotlin 侧上报的 RENDER_TOTAL_LEN（String.length，UTF-16 码元）；
非 BMP 字符出现时以结构化上报为准并在 JSON 标注。"""
import json, re, sys
from pathlib import Path

MARKER = "（该节点无材料）"

# 输出位置固定：不接受命令行写入路径，避免参数把写操作引到仓库外。
OUTPUT_PATH = Path(__file__).resolve().parents[2] / "build" / "agent-batch35" / "render" / "render-judge.json"


def main() -> int:
    raw = open(sys.argv[1], encoding="utf-8").read()
    blocks = re.findall(r"RENDER_BEGIN (round=\d+ q=\d+)(.*?)RENDER_END", raw, re.S)
    if not blocks:
        print("MALFORMED: no RENDER blocks", file=sys.stderr); return 2
    findings, ok = [], True
    for head, body in blocks:
        # b35-render §0.5-3 登记（执行轮唯一脚本改动）：render_capture.sh 落盘的是原始
        # logcat（threadtime 前缀），冻结正文的行首锚定 `^RENDER_TOTAL_LEN=` 永不命中；
        # 放开锚定（语义不变：仍取该块第一处数值字段；输入/输出/退出码契约不变）。
        total = re.search(r"RENDER_TOTAL_LEN=(\d+)", body)
        if not total:
            findings.append({"block": head, "check": "total", "ok": False, "why": "missing RENDER_TOTAL_LEN"}); ok = False; continue
        total_len = int(total.group(1))
        total_ok = total_len <= 2000
        ok &= total_ok
        findings.append({"block": head, "check": "total<=2000", "ok": total_ok, "value": total_len})
        nodes = re.findall(r"RENDER_NODE ([^\r\n]+)", body)
        if not nodes:
            findings.append({"block": head, "check": "nodes", "ok": False, "why": "no RENDER_NODE lines"}); ok = False; continue
        text_lines = [l.split("RENDER_TEXT|", 1)[1] for l in body.splitlines() if "RENDER_TEXT|" in l]
        text = "\n".join(text_lines)
        for n in nodes:
            f = dict(kv.split("=", 1) for kv in n.split(" ") if "=" in kv)
            idx, has_material, rows = f["idx"], f["hasMaterial"] == "1", int(f["rows"])
            title_len = int(f.get("primaryTitleLen", "-1"))
            excerpt_len = int(f.get("primaryExcerptLen", "-1"))
            ptype, stype = f.get("primaryType", "-"), f.get("secondaryType", "-")
            node_ok = True
            if not has_material:
                node_ok &= rows == 0 and title_len == 0 and excerpt_len == 0
                node_ok &= MARKER in text
            else:
                node_ok &= 1 <= rows <= 2 and title_len > 0 and excerpt_len >= 61
                node_ok &= MARKER not in _node_segment(text, f)
                if rows == 2:
                    node_ok &= stype != "-" and stype != ptype
            ok &= node_ok
            findings.append({"block": head, "check": "node", "idx": idx, "ok": bool(node_ok),
                             "rows": rows, "titleLen": title_len, "primaryExcerptLen": excerpt_len,
                             "primaryType": ptype, "secondaryType": stype})
    out_path = Path(sys.argv[2]).resolve()
    cwd = Path.cwd().resolve()
    if not out_path.is_relative_to(cwd):
        raise SystemExit(f"output path must stay inside the working directory: {out_path}")
    out_path.write_text(
        json.dumps({"ok": ok, "findings": findings}, ensure_ascii=False, indent=1) + "\n",
        encoding="utf-8",
    )
    return 0 if ok else 1

def _node_segment(text: str, f: dict) -> str:
    """该节点代号行起、下一节点代号行（或文末）止的片段——标记归属检查用。"""
    # b35-render §0.5-3 登记（执行轮第二处脚本改动）：原实现只按目标代号行分割，段尾
    # 延伸到文末——后续任意节点的标记会漏进本节点段，混合采集（有材料 + 无材料）必假红。
    # 改为按全部节点代号行分割后取目标段，与 docstring 声明的语义一致（判据含义不变）。
    code = f.get("code")
    segs = re.split(r"(?=^(?:K\d+|\d+)\. )", text, flags=re.M)
    if code is not None:
        segs = [seg for seg in segs if seg.startswith(code + ". ")]
    return segs[-1] if segs else text

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr); sys.exit(2)
    sys.exit(main())
