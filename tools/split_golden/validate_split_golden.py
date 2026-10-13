#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""拆题金标 v1：**冻结的** 7 页题框标注 + sha256 旁车，机械校验（不调模型）。

## 它消灭的具体失败

"自动拆题"（页面 → 题干框）若没有正确性基准，换一版提示词 / 换一个模型 / 调一次阈值
是否更好，只能靠观感。所以把两名标注员一致的 7 页框标注冻结成可比对的数。而"冻结"若
只靠"文件还在那"，下面五件事会各自出错且**没有门会红**：

  ① 字节漂了：缩进 / 键序 / 结尾换行变一位，sha 就变（消费侧的"旁车 == 文件字节"
     断言直接红，或者更糟——**静默比错对象**；旁车若在内存字符串上算，记录的就不是
     磁盘上那份字节，而文件"看起来是对的"）。所以：落盘禁 CRLF，sha 取**磁盘字节**，
     并在此做重序列化的逐字节硬断言。
  ② questionCount 与 boxes 个数脱钩（人写摘要时数错，指标分母就错，而没有任何门会红）。
  ③ 框不合法（越界 0..1、left>=right、top>=bottom）——下游裁图会裁出空图或反向图，
     且这类错误在"平均 IoU"里往往被平均掉。
  ④ 同一页出现两次（同一页算两次 ⇒ 指标被无声加权）。
  ⑤ 页数掉到 7 以下（两员一致子集被谁悄悄删页，而均值照样漂亮）。

## 校验项（全机械）

  C1 冻结字节：旁车形状（sha256 + 文件名）→ 磁盘字节 sha 与旁车一致 → 无 CR/CRLF、
     `canonical_dump(json.loads(磁盘字节))` 与磁盘字节**逐字节相等**（键序/缩进/结尾换行）
  C2 schema：根 / 页 / 框的键集合恰好、类型与取值域；generatedAt 为 ISO-8601；
     evidenceGrade 等于冻结常量（防止"模型标注、无人工专家"被无声改写成"人工专家"）
  C3 每页 questionCount == len(boxes)
  C4 每个框合法：left/top/right/bottom ∈ [0,1] 且 left<right、top<bottom
  C5 file 全局不重复
  C6 pages >= 7（MIN_PAGES）

结构或类型没过的页**不进入** C3–C5（避免拿残缺数据再报一串噪声），C2 已逐条说明原因。

全过 → 打印摘要，exit 0；否则逐条列出**全部**失败，exit 1。

用法（仓库根）：

    python tools/split_golden/validate_split_golden.py
        [--file tools/split_golden/tables/split_golden_v1.json]   # 默认即此
        [--sidecar <file> + '.sha256']                            # 默认即此

序列化约定（**冻结**，改它等于换一代冻结代号，必须同时更新旁车并说明原因）：
`json.dumps(..., ensure_ascii=False, sort_keys=True, indent=2)` + 恰好一个 `\\n`，
UTF-8 无 BOM，LF 换行。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

REPO: Path = Path(__file__).resolve().parents[2]
DEFAULT_FILE: Path = REPO / "tools" / "split_golden" / "tables" / "split_golden_v1.json"

SCHEMA_VERSION = 1
MIN_PAGES = 7
# 冻结的证据等级：两名标注员独立一致，但**无人工专家**。任何"提升"都必须改这一行 + 旁车。
EVIDENCE_GRADE = "model-labeled, no human expert; two independent labelers agreed"

# 本节冻结的序列化约定（消费方按同一份约定复算 sha）。
CANONICAL_JSON_KW = dict(ensure_ascii=False, sort_keys=True, indent=2)

ROOT_KEYS = frozenset({"schemaVersion", "generatedAt", "sampleRule", "evidenceGrade", "pages"})
PAGE_KEYS = frozenset({"file", "subject", "layout", "questionCount", "boxes"})
BOX_KEYS = frozenset({"left", "top", "right", "bottom"})

_ISO_8601_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$")
# 旁车一行：<64 位小写 sha256>  <文件名>
_SIDECAR_RE = re.compile(r"^([0-9a-f]{64})(?: {1,2}(\S+))?$")


def canonical_dump(payload: Any) -> str:
    """冻结形态的字符串（唯一合法序列化；落盘与校验都用它）。"""
    return json.dumps(payload, **CANONICAL_JSON_KW) + "\n"


def first_diff(disk: str, canonical: str) -> str:
    for index, (left, right) in enumerate(zip(disk, canonical)):
        if left != right:
            return f"偏移 {index}（磁盘 {left!r} vs canonical {right!r}）"
    if len(disk) != len(canonical):
        return f"长度 {len(disk)} vs {len(canonical)}"
    return "无差异"


def check_sidecar(raw: bytes, file_path: Path, sidecar_path: Path) -> list[str]:
    """C1 前半：旁车形状 + 磁盘字节 sha。返回失败清单。"""
    failures: list[str] = []
    if not sidecar_path.is_file():
        return [f"C1: 旁车不存在：{sidecar_path}"]
    text = sidecar_path.read_text(encoding="utf-8")
    if "\r" in text:
        failures.append(f"C1: 旁车含 CR —— 约定是 LF：{sidecar_path}")
    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines = lines[:-1]
    if len(lines) != 1:
        failures.append(f"C1: 旁车应为恰好一行 '<sha256>  <文件名>'，实测 {len(lines)} 行")
        return failures
    matched = _SIDECAR_RE.match(lines[0])
    if not matched:
        failures.append(f"C1: 旁车首行不是 '<64位 sha256>  <文件名>'：{lines[0]!r}")
        return failures
    sealed, named = matched.group(1), matched.group(2)
    if named is None:
        failures.append("C1: 旁车缺文件名（本目录约定为 sha256 + 文件名，便于 sha256sum -c）")
    elif named != file_path.name:
        failures.append(f"C1: 旁车记录的文件名 {named!r} != 金标文件名 {file_path.name!r}")
    actual = hashlib.sha256(raw).hexdigest()
    if sealed != actual:
        failures.append(f"C1: 冻结字节 sha256 不符：旁车 {sealed} / 磁盘 {actual}")
    return failures


def check_schema(doc: Any) -> tuple[list[dict], int, list[str]]:
    """C2：根 / 页 / 框的形状与类型。返回 (结构完好的页, 原始页数, 失败清单)。"""
    failures: list[str] = []
    if not isinstance(doc, dict):
        return [], 0, [f"C2: 根不是 JSON 对象（{type(doc).__name__}）"]
    missing = ROOT_KEYS - set(doc)
    extra = set(doc) - ROOT_KEYS
    if missing or extra:
        failures.append(f"C2: 根字段不是恰好 {sorted(ROOT_KEYS)}"
                        f"（缺 {sorted(missing)}，多 {sorted(extra)}）")
    if doc.get("schemaVersion") != SCHEMA_VERSION:
        failures.append(f"C2: schemaVersion={doc.get('schemaVersion')!r} != {SCHEMA_VERSION}")
    generated_at = doc.get("generatedAt")
    if not isinstance(generated_at, str) or not _ISO_8601_RE.match(generated_at):
        failures.append(f"C2: generatedAt 不是 ISO-8601 时间戳：{generated_at!r}")
    sample_rule = doc.get("sampleRule")
    if not isinstance(sample_rule, str) or not sample_rule.strip():
        failures.append(f"C2: sampleRule 不是非空字符串：{sample_rule!r}")
    if doc.get("evidenceGrade") != EVIDENCE_GRADE:
        failures.append("C2: evidenceGrade 与冻结常量不符"
                        f"（磁盘 {doc.get('evidenceGrade')!r} vs 冻结 {EVIDENCE_GRADE!r}）")
    raw_pages = doc.get("pages")
    if not isinstance(raw_pages, list):
        failures.append(f"C2: pages 不是数组：{type(raw_pages).__name__}")
        return [], 0, failures

    pages: list[dict] = []
    for index, page in enumerate(raw_pages):
        where = f"pages[{index}]"
        if not isinstance(page, dict):
            failures.append(f"C2: {where} 不是对象（{type(page).__name__}）")
            continue
        page_missing = PAGE_KEYS - set(page)
        page_extra = set(page) - PAGE_KEYS
        if page_missing or page_extra:
            failures.append(f"C2: {where} 字段不是恰好 {sorted(PAGE_KEYS)}"
                            f"（缺 {sorted(page_missing)}，多 {sorted(page_extra)}）")
            continue
        printable = page.get("file", where)
        page_failures: list[str] = []
        for key in ("file", "subject", "layout"):
            value = page[key]
            if not isinstance(value, str) or not value.strip():
                page_failures.append(f"C2: {where}.{key} 不是非空字符串：{value!r}")
        if isinstance(page["questionCount"], bool) or not isinstance(page["questionCount"], int):
            page_failures.append(f"C2: {where}.questionCount 不是整数："
                                 f"{page['questionCount']!r}")
        if not isinstance(page["boxes"], list):
            page_failures.append(f"C2: {where}.boxes 不是数组：{type(page['boxes']).__name__}")
            failures.extend(page_failures)
            continue
        for box_index, box in enumerate(page["boxes"]):
            box_where = f"{where}({printable!r}).boxes[{box_index}]"
            if not isinstance(box, dict):
                page_failures.append(f"C2: {box_where} 不是对象（{type(box).__name__}）")
                continue
            box_missing = BOX_KEYS - set(box)
            box_extra = set(box) - BOX_KEYS
            if box_missing or box_extra:
                page_failures.append(f"C2: {box_where} 字段不是恰好 {sorted(BOX_KEYS)}"
                                     f"（缺 {sorted(box_missing)}，多 {sorted(box_extra)}）")
        failures.extend(page_failures)
        if not page_failures:            # 残页不进入 C3–C5，免得再报一串噪声
            pages.append(page)
    return pages, len(raw_pages), failures


def check_counts(pages: list[dict]) -> list[str]:
    """C3：questionCount == len(boxes)。"""
    failures: list[str] = []
    for index, page in enumerate(pages):
        name = page["file"]
        boxes = len(page["boxes"])
        if page["questionCount"] != boxes:
            failures.append(f"C3: pages[{index}]({name!r}) questionCount="
                            f"{page['questionCount']} != len(boxes)={boxes}")
    return failures


def check_boxes(pages: list[dict]) -> list[str]:
    """C4：框合法（0..1、left<right、top<bottom）。"""
    failures: list[str] = []
    for index, page in enumerate(pages):
        name = page["file"]
        for box_index, box in enumerate(page["boxes"]):
            where = f"pages[{index}]({name!r}).boxes[{box_index}]"
            values: dict[str, float] = {}
            broken = False
            for key in ("left", "top", "right", "bottom"):
                value = box[key]
                if isinstance(value, bool) or not isinstance(value, (int, float)):
                    failures.append(f"C4: {where}.{key} 不是数字：{value!r}")
                    broken = True
                    continue
                number = float(value)
                values[key] = number
                if not 0.0 <= number <= 1.0:
                    failures.append(f"C4: {where}.{key}={value} 越界，要求 0..1")
                    broken = True
            if broken:
                continue
            if not values["left"] < values["right"]:
                failures.append(f"C4: {where} left={values['left']:g} "
                                f">= right={values['right']:g}（空/反向框，下游会裁出空图）")
            if not values["top"] < values["bottom"]:
                failures.append(f"C4: {where} top={values['top']:g} "
                                f">= bottom={values['bottom']:g}（空/反向框，下游会裁出空图）")
    return failures


def check_unique(pages: list[dict]) -> list[str]:
    """C5：file 全局不重复（同一页算两次 = 指标被无声加权）。"""
    failures: list[str] = []
    seen: dict[str, int] = {}
    for index, page in enumerate(pages):
        name = page["file"]
        if name in seen:
            failures.append(f"C5: pages[{index}] 与 pages[{seen[name]}] 是同一文件 {name!r}——"
                            "同一页会被算两次")
        else:
            seen[name] = index
    return failures


def validate(file_path: Path, sidecar_path: Path) -> tuple[dict | None, list[str]]:
    """跑完 C1–C6，返回 (文档或 None, 全部失败)。"""
    if not file_path.is_file():
        return None, [f"C1: 金标文件不存在：{file_path}"]
    raw = file_path.read_bytes()
    failures = check_sidecar(raw, file_path, sidecar_path)
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as failure:
        return None, failures + [f"C1: 不是合法 UTF-8：{failure}"]
    if b"\r" in raw:
        failures.append("C1: 含 CR/CRLF —— 字节约定是 LF")
    try:
        doc = json.loads(text)
    except ValueError as failure:
        return None, failures + [f"C1: 非法 JSON：{failure}"]
    canonical = canonical_dump(doc)
    if text != canonical:
        failures.append("C1: 磁盘字节 != canonical 重序列化（键序/缩进/结尾换行/转义有一处不同）："
                        f"{first_diff(text, canonical)}")

    pages, raw_page_count, schema_failures = check_schema(doc)
    failures.extend(schema_failures)
    failures.extend(check_counts(pages))
    failures.extend(check_boxes(pages))
    failures.extend(check_unique(pages))
    if raw_page_count < MIN_PAGES:
        failures.append(f"C6: 页数 {raw_page_count} < {MIN_PAGES}")
    return doc, failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="拆题金标 v1 冻结字节校验（不调模型）")
    parser.add_argument("--file", type=Path, default=DEFAULT_FILE)
    parser.add_argument("--sidecar", type=Path, default=None,
                        help="默认 <file> + '.sha256'")
    args = parser.parse_args(argv)
    sidecar_path = args.sidecar or args.file.with_name(args.file.name + ".sha256")

    doc, failures = validate(args.file, sidecar_path)
    if failures:
        print(f"拆题金标校验 FAIL：{len(failures)} 处失败（{args.file.name}）")
        for failure in failures:
            print(f"  - {failure}")
        return 1

    assert doc is not None                     # 无失败 ⇒ 文档已解析
    pages = doc["pages"]
    raw = args.file.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    total_boxes = sum(len(page["boxes"]) for page in pages)
    by_subject: dict[str, int] = {}
    by_layout: dict[str, int] = {}
    for page in pages:
        by_subject[page["subject"]] = by_subject.get(page["subject"], 0) + 1
        by_layout[page["layout"]] = by_layout.get(page["layout"], 0) + 1
    print("拆题金标校验 PASS")
    print(f"  · C1 冻结字节：sha256={digest}（旁车一致、canonical 逐字节相等、CRLF=0，"
          f"{len(raw)} B）")
    print(f"  · C2 schema：根/页/框键集合与类型全过；generatedAt={doc['generatedAt']}")
    print(f"  · C3/C4：{len(pages)} 页 {total_boxes} 框，questionCount 与框数一致、框全部合法")
    print(f"  · C5 无重复文件（{len(pages)} 个唯一 file）")
    print(f"  · C6 页数 {len(pages)} >= {MIN_PAGES}")
    print(f"  · 科目分布：" + " / ".join(f"{k} {v}" for k, v in sorted(by_subject.items())))
    print(f"  · 版面分布：" + " / ".join(f"{k} {v}" for k, v in sorted(by_layout.items())))
    print(f"  · evidenceGrade={doc['evidenceGrade']!r}")
    print("  · 页清单：" + "；".join(
        f"{page['file']}({page['subject']}/{page['layout']}/{page['questionCount']}框)"
        for page in pages))
    return 0


if __name__ == "__main__":
    sys.exit(main())
