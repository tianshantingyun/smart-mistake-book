#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""设备 DB 快照 → `build/split-eval/app-split.json` 导出器（AUD-08 工具链，R4 §3.7）。

## 它消灭的具体失败

度量脚本的输入 `build/split-eval/app-split.json` 自 4C 起只存在于个别会话的手工产物里
（R4 §3.7 实测："仓内没有生产者脚本"，`tools/split_golden` 只引用该路径）。于是：

  ① 复跑度量无入口：换一台设备/换一个 DB 快照，没人能以同一口径再产出 app-split.json；
  ② 导出文件无溯源：页名怎么配的、框来自哪张表，文件里没有任何记录（4C 的页名
     靠 pages.txt + 感知匹配在会话外完成）；
  ③ 坏数据无门：重复页名会把指标无声加权、非法框会裁出空/反向图——度量脚本虽有
     校验，但导出端先挡一层，错误信息直接指向 DB 行。

## 页名解析（不猜：每页都标 nameSource）

1. `--page-map <json>`：显式映射 `{"<job_id>": {"<page_index>": "<文件名>"}}`
   （4C 会话的 pages.txt/感知匹配产物即此形态）→ `nameSource="page_map"`；
2. 批导入页：`split_import_job.job_id` 形如 `split:<batch_job_id>:<page_index>`
   （生产常量，BatchSplitRecognizer.kt）→ 查 `batch_import_page` 的 `source_uri`
   取文件名 → `nameSource="batch_page_source_uri"`；
3. 都没有 → 合成名 `job:<job_id>#p<page_index>` → `nameSource="job_id_synthetic"`
   （不会与金标配对，如实标记，绝不假装成页名）。

## 红线

- DB **只读**（sqlite `mode=ro` URI 打开，导出前后字节不变）；SQL 一律字面量/
  参数化占位符（?），无任何输入拼接；
- 不联网；只写 `--out`（与可选 `<out>.sha256` 旁车）；
- 同一 DB 复跑输出**逐字节一致**（无时间戳字段，溯源用 DB/映射文件自身的 sha256）；
- 非法框（越界 0..1、left>=right、top>=bottom）与重复页名一律 exit 2 拒绝；
- 不做任何筛选（selected/confirm_state 原样计数随页落盘），筛选属口径裁定、不归工具。

## 用法（仓库根）

    python tools/split_golden/export_split_eval.py
        --db <设备DB快照.sqlite>
        [--out build/split-eval/app-split.json]
        [--page-map <map.json>]
        [--sidecar]          # 同时写 <out>.sha256

退出码：0 成功；2 输入不可用（DB 缺失/无表/非法框/重复页名/映射坏/输出已存在）。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import sys
import urllib.request
from pathlib import Path

HERE: Path = Path(__file__).resolve().parent
REPO: Path = HERE.parents[1]
DEFAULT_OUT: Path = REPO / "build" / "split-eval" / "app-split.json"

BOX_KEYS = ("left", "top", "right", "bottom")
REPORT_SCHEMA_VERSION = 1
# 溯源记录用（与下方 execute 处的字面量语句一致；SQL 全部字面量/参数化，无输入拼接）。
QUERIES_DOCUMENTATION = (
    "SELECT q.job_id, q.page_index, q.question_ordinal, q.region_left, q.region_top, "
    "q.region_right, q.region_bottom, q.selected, q.confirm_state, j.source_uri, "
    "j.status, j.page_count FROM split_import_question q JOIN split_import_job j "
    "ON j.job_id = q.job_id ORDER BY q.job_id, q.page_index, q.question_ordinal ; "
    "SELECT source_uri FROM batch_import_page WHERE job_id = ? AND page_index = ?"
)


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _connect_read_only(db_path: Path) -> sqlite3.Connection:
    """只读 URI 打开（Windows 空格路径安全）；失败原样抛 DataError。"""
    uri = "file:" + urllib.request.pathname2url(str(db_path.resolve())) + "?mode=ro"
    return sqlite3.connect(uri, uri=True)


def _uri_file_name(source_uri: str) -> str | None:
    """从 source_uri 取文件名；取不出可信文件名就返回 None（绝不猜）。"""
    if not source_uri:
        return None
    tail = source_uri.split("?", 1)[0].split("#", 1)[0]
    tail = tail.replace("\\", "/").rstrip("/").rsplit("/", 1)[-1]
    if not tail or "." not in tail or tail.startswith("."):
        return None
    name, _, extension = tail.rpartition(".")
    if not name or not extension.isalnum():
        return None
    return tail


def _resolve_page_name(conn: sqlite3.Connection, job_id: str, page_index: int,
                       page_map: dict) -> tuple[str, str]:
    """页名解析（见模块 docstring 的三级顺序）。"""
    by_page = page_map.get(job_id)
    if isinstance(by_page, dict):
        mapped = by_page.get(str(page_index))
        if isinstance(mapped, str) and mapped.strip():
            return mapped.strip(), "page_map"
    if job_id.startswith("split:") and job_id.count(":") >= 2:
        batch_job_id, _, batch_page_text = job_id[len("split:"):].rpartition(":")
        try:
            batch_page_index = int(batch_page_text)
        except ValueError:
            batch_job_id, batch_page_index = "", -1
        if batch_job_id and batch_page_index >= 0:
            try:
                row = conn.execute(
                    "SELECT source_uri FROM batch_import_page"
                    " WHERE job_id = ? AND page_index = ?",
                    (batch_job_id, batch_page_index),
                ).fetchone()
            except sqlite3.OperationalError:
                row = None
            if row and row[0]:
                name = _uri_file_name(str(row[0]))
                if name:
                    return name, "batch_page_source_uri"
    return f"job:{job_id}#p{page_index}", "job_id_synthetic"


def _validate_box(values: dict, where: str) -> None:
    for key in BOX_KEYS:
        value = values[key]
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise DataError(f"{where}.{key} 不是数字：{value!r}")
        if not 0.0 <= float(value) <= 1.0:
            raise DataError(f"{where}.{key}={value} 越界（要求 0..1）")
    if not values["left"] < values["right"]:
        raise DataError(f"{where} region_left={values['left']} >= "
                        f"region_right={values['right']}（空/反向框，下游会裁出空图）")
    if not values["top"] < values["bottom"]:
        raise DataError(f"{where} region_top={values['top']} >= "
                        f"region_bottom={values['bottom']}（空/反向框，下游会裁出空图）")


class DataError(Exception):
    """输入不可用（缺文件/缺表/坏行/重名），exit 2。"""


def collect(db_path: Path, page_map_path: Path | None) -> dict:
    """查询 + 解析 + 校验，返回导出文档（未序列化）。所有失败走 DataError。"""
    if not db_path.is_file():
        raise DataError(f"DB 快照不存在：{db_path}")
    page_map: dict = {}
    page_map_meta: dict | None = None
    if page_map_path is not None:
        if not page_map_path.is_file():
            raise DataError(f"页名映射不存在：{page_map_path}")
        try:
            loaded = json.loads(page_map_path.read_text(encoding="utf-8"))
        except ValueError as failure:
            raise DataError(f"页名映射不是合法 JSON：{page_map_path}（{failure}）") from failure
        if not isinstance(loaded, dict):
            raise DataError(f"页名映射根不是 JSON 对象：{type(loaded).__name__}")
        page_map = loaded
        page_map_meta = {"file": page_map_path.name, "sha256": _sha256(page_map_path),
                         "bytes": page_map_path.stat().st_size}

    conn = _connect_read_only(db_path)
    try:
        try:
            rows = conn.execute(
                "SELECT q.job_id, q.page_index, q.question_ordinal, "
                "q.region_left, q.region_top, q.region_right, q.region_bottom, "
                "q.selected, q.confirm_state, j.source_uri, j.status, j.page_count "
                "FROM split_import_question q JOIN split_import_job j"
                " ON j.job_id = q.job_id "
                "ORDER BY q.job_id, q.page_index, q.question_ordinal"
            ).fetchall()
        except sqlite3.OperationalError as failure:
            raise DataError(f"DB 快照缺 split_import_job / split_import_question 表"
                            f"（不是应用 DB？）：{failure}") from failure

        grouped: dict[tuple[str, int], dict] = {}
        for (job_id, page_index, ordinal, left, top, right, bottom,
             selected, confirm_state, source_uri, job_status, page_count) in rows:
            key = (str(job_id), int(page_index))
            page_entry = grouped.setdefault(key, {
                "jobId": key[0], "pageIndex": key[1], "sourceUri": source_uri,
                "jobStatus": job_status, "pageCount": page_count,
                "questions": [], "selectedCount": 0, "confirmStates": {}})
            box = {"left": left, "top": top, "right": right, "bottom": bottom}
            _validate_box(box, f"split_import_question(job={key[0]},"
                               f"page={key[1]},ordinal={ordinal})")
            page_entry["questions"].append(box)
            if selected:
                page_entry["selectedCount"] += 1
            state_text = str(confirm_state)
            page_entry["confirmStates"][state_text] = \
                page_entry["confirmStates"].get(state_text, 0) + 1

        pages: list[dict] = []
        for (job_id, page_index), entry in sorted(grouped.items()):
            name, name_source = _resolve_page_name(conn, job_id, page_index, page_map)
            pages.append({"file": name, "nameSource": name_source,
                          "jobId": job_id, "pageIndex": page_index,
                          "questionCount": len(entry["questions"]),
                          "selectedCount": entry["selectedCount"],
                          "confirmStates": dict(sorted(entry["confirmStates"].items())),
                          "questions": entry["questions"]})

        pages.sort(key=lambda page: page["file"])
        seen: dict[str, str] = {}
        for page in pages:
            if page["file"] in seen:
                raise DataError(f"重复页名 {page['file']!r}：{seen[page['file']]} 与 "
                                f"{page['jobId']}#p{page['pageIndex']} 都解析到同一文件名"
                                "——同一页会被算两次，指标被无声加权；请用 --page-map 显式消歧")
            seen[page["file"]] = f"{page['jobId']}#p{page['pageIndex']}"
    finally:
        conn.close()

    return {
        "schemaVersion": REPORT_SCHEMA_VERSION,
        "source": {
            "db": {"file": db_path.name, "sha256": _sha256(db_path),
                   "bytes": db_path.stat().st_size},
            "pageMap": page_map_meta,
            "queries": QUERIES_DOCUMENTATION,
            "nameSourceCounts": _count_name_sources(pages),
        },
        "pages": pages,
    }


def _count_name_sources(pages: list[dict]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for page in pages:
        counts[page["nameSource"]] = counts.get(page["nameSource"], 0) + 1
    return dict(sorted(counts.items()))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="设备 DB 快照 → app-split.json 导出器（AUD-08；只读 DB，不联网）")
    parser.add_argument("--db", type=Path, required=True, help="设备 DB 快照（sqlite）")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--page-map", type=Path, default=None,
                        help='页名映射 {"<job_id>": {"<page_index>": "<文件名>"}}')
    parser.add_argument("--sidecar", action="store_true",
                        help="同时写 <out>.sha256 旁车")
    args = parser.parse_args(argv)

    try:
        if args.out.exists():
            raise DataError(f"输出已存在，不覆盖（重跑请换名或先删）：{args.out}")
        document = collect(args.db, args.page_map)
    except DataError as failure:
        print(f"split 导出 FAIL：{failure}")
        return 2

    text = json.dumps(document, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(text, encoding="utf-8", newline="")
    digest = _sha256(args.out)
    total_boxes = sum(page["questionCount"] for page in document["pages"])
    print(f"split 导出 OK：{args.out}")
    print(f"  页 {len(document['pages'])} / 框 {total_boxes}；"
          f"页名来源 {document['source']['nameSourceCounts']}")
    print(f"  DB sha256={document['source']['db']['sha256']}（只读打开，字节未动）")
    print(f"  输出 sha256={digest}")
    if args.sidecar:
        sidecar = args.out.with_name(args.out.name + ".sha256")
        sidecar.write_text(f"{digest}  {args.out.name}\n", encoding="utf-8", newline="")
        print(f"  旁车：{sidecar}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
