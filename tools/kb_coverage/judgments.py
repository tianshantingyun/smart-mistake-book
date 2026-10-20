# -*- coding: utf-8 -*-
"""判定表分片读写：`tables/material_judgments.csv` 单文件 → `tables/material_judgments/<科目>.csv` 五片。

## 它消灭的失败

判定表原是一张 `tools/kb_coverage/tables/material_judgments.csv`（物理 82,649,203 B ≈ 78.8 MiB）。
两个失败随之而来：

1. **逼近 GitHub 单文件 100 MB 硬限**：一旦越线，推送被拒，判定表就再也无法随仓库分发
   （而它是入库的唯一判定源）。拆成五片后单文件约 20 MB，留出 5 倍余量。
2. **整表重写级 diff**：追加/修正一行要重写整张 78 MB 文件，diff 里看不出一行改动；
   按科目分片后一次判定只改它所在的那一片，diff 与改动同量级（逐条复核判定表是本项目的核心纪律）。

## 口径（唯一权威源）

- **片归属** = `extraction_state.subject_of_path(chunk_rel)`（不可判的全科卷 → `UNASSIGNED`），
  与 materialize / merge_text_judgments / dedupe 判科同口径（extraction_state.py:71-80），
  不另立关键词规则。
- **片的顺序** = `PARTS` 常量（不是目录遍历顺序）：`load_all` 拼接、`write_all` 落盘都按它，
  保证同一份行集在任何机器上产出同一份字节。
- **片内保序**：片内行序 = 写盘行序（判定行序即复核顺序，不得按科目重排打乱）。
- **表头逐字 12 列、UTF-8 无 BOM、`\\r\\n` 行尾**：与拆分前的单文件一致。行尾必须钉死——
  否则任意一次写入都会把十万行行尾全部改掉，diff 噪音淹没真正的改动。

## 缺席语义（防"静默判绿"）

`load_all` 对**不存在的片目录**抛 `FileNotFoundError`，不返回空表：判定表缺席是坏状态
（`kb_build.gate` 的 exam_leak 门正是靠这个异常把"查不了"置红，而不是静默判绿）。
片文件缺失（拆分/迁移途中某片还没落盘）按空片跳过。

## 用法

    from kb_coverage import judgments
    rows = judgments.load_all()           # 按 PARTS 序拼接五片
    judgments.write_all(rows)             # 按科目分片写回（12 列表头、CRLF）
    judgments.key(row)                    # 行身份 (chunk_rel, chunk_id, midx)

    python tools/kb_coverage/judgments.py # 直跑自检：各片行数/合计/重复键（重复键 exit 1）
"""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
if str(REPO / "tools") not in sys.path:      # 直跑引导：python tools/kb_coverage/judgments.py
    sys.path.insert(0, str(REPO / "tools"))

from kb_coverage.extraction_state import subject_of_path  # noqa: E402

DIR_NAME = "material_judgments"
PARTS = ("MATH", "PHYSICS", "CHEMISTRY", "BIOLOGY", "UNASSIGNED")
# 与拆分前的单文件表头逐字一致（materialize/reconcile/merge 的同名列必须与它相等）。
COLUMNS = ("chunk_rel", "chunk_id", "action", "node_slug", "type", "title",
           "summary", "applicability", "content", "boundary", "note", "midx")

# materialize 写入侧的科目缩写（slug 前缀 ext-<subj3>-…）。放在这里是为了让
# "判定行 → 包内 material slug"只有一份实现：materialize 写、漂移检查读，同源。
SUBJ3 = {"MATH": "mat", "PHYSICS": "phy", "CHEMISTRY": "che", "BIOLOGY": "bio"}


def parts_dir() -> Path:
    """片目录：`tools/kb_coverage/tables/material_judgments/`。"""
    return Path(__file__).resolve().parent / "tables" / DIR_NAME


def part_of(rel_path: str | None) -> str:
    """chunk_rel → 片名（PARTS 之一）：`subject_of_path` 包一层，None（全科卷）→ UNASSIGNED。"""
    subj = subject_of_path(rel_path or "")
    return subj if subj in PARTS else "UNASSIGNED"


def key(row: dict) -> tuple[str, str, str]:
    """行身份 `(chunk_rel, chunk_id, midx)`。midx 去空白：`''/b/c` 是块后缀，
    带空白的 midx 与不带的是同一行——判重、补行、定位都必须按同一把钥匙对齐。"""
    return ((row.get("chunk_rel") or ""), (row.get("chunk_id") or ""),
            (row.get("midx") or "").strip())


def material_slug(row: dict, subject: str | None) -> str | None:
    """判定行 → 包内 material slug（与 `materialize.write` 的写入公式同源）。

    `ext-{subj3}-{chunk_id 第一段}-{chunk_id 末段}{midx}`；subject 不是四科
    （UNASSIGNED/None）或 chunk_id 不含 `-` 时返回 None（该行无法定位到包内材料）。
    """
    if subject not in SUBJ3:
        return None
    chunk_id = row.get("chunk_id") or ""
    if "-" not in chunk_id:
        return None
    digest = chunk_id.split("-", 1)[0]
    index = chunk_id.rsplit("-", 1)[-1]
    return f"ext-{SUBJ3[subject]}-{digest}-{index}{(row.get('midx') or '').strip()}"


def _read_part(path: Path) -> list[dict]:
    with path.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        cols = list(reader.fieldnames or [])
        missing = [c for c in COLUMNS if c not in cols]
        if missing:
            raise ValueError(f"{path.name} 表头缺列 {missing}")
        extra = [c for c in cols if c not in COLUMNS]
        if extra:
            raise ValueError(f"{path.name} 表头含未知列 {extra}（判定表只许 12 列）")
        return [{c: (r.get(c) or "") for c in COLUMNS} for r in reader]


def load_all(base: Path | None = None) -> list[dict]:
    """按 PARTS 序读五片并拼接（片内保行序）。

    base 默认 `parts_dir()`；必须是片目录（单个 CSV 不是合法输入）。目录不存在 →
    FileNotFoundError（见模块 docstring「缺席语义」）。
    """
    root = Path(base) if base is not None else parts_dir()
    if not root.exists():
        raise FileNotFoundError(f"判定表片目录不存在：{root}")
    if not root.is_dir():
        raise NotADirectoryError(f"判定表必须是片目录（不是单个 CSV）：{root}")
    rows: list[dict] = []
    for part in PARTS:
        path = root / f"{part}.csv"
        if path.exists():
            rows.extend(_read_part(path))
    return rows


def write_all(rows: list[dict], base: Path | None = None) -> dict[str, int]:
    """按 `part_of(chunk_rel)` 把行写进五片，返回各片行数（PARTS 序）。

    五片一律重写（无行的片写表头行）——保证"写一次之后五片齐全"，位点固定、可复核。
    字段值统一 `r.get(c) or ""` 取 12 列（多余键丢弃、缺键补空），行尾固定 `\\r\\n`。
    """
    root = Path(base) if base is not None else parts_dir()
    if root.exists() and not root.is_dir():
        raise NotADirectoryError(f"判定表必须是片目录（不是单个 CSV）：{root}")
    root.mkdir(parents=True, exist_ok=True)
    grouped: dict[str, list[dict]] = {p: [] for p in PARTS}
    for r in rows:
        grouped[part_of(r.get("chunk_rel"))].append(r)
    counts: dict[str, int] = {}
    for part in PARTS:
        path = root / f"{part}.csv"
        with path.open("w", encoding="utf-8", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=list(COLUMNS),
                                    lineterminator="\r\n", quoting=csv.QUOTE_MINIMAL)
            writer.writeheader()
            writer.writerows({c: (r.get(c) or "") for c in COLUMNS} for r in grouped[part])
        counts[part] = len(grouped[part])
    return counts


def report(base: Path | None = None) -> dict:
    """各片行数 + 合计 + 重复行身份数（自检口径，见 main）。"""
    rows = load_all(base)
    counts = {p: 0 for p in PARTS}
    for r in rows:
        counts[part_of(r.get("chunk_rel"))] += 1
    keys = [key(r) for r in rows]
    return {"parts": counts, "rows": len(rows), "dup_keys": len(keys) - len(set(keys))}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="判定表分片自检（行数/重复键）")
    ap.add_argument("--dir", type=Path, default=None, help="片目录（默认 tables/material_judgments）")
    args = ap.parse_args(argv)
    try:
        res = report(args.dir)
    except (OSError, ValueError) as exc:
        print(f"判定表读不出：{exc}")
        return 1
    for part in PARTS:
        print(f"{part:<10} {res['parts'][part]:>7}")
    print(f"{'合计':<9} {res['rows']:>7} 行；重复行身份 {res['dup_keys']}")
    return 1 if res["dup_keys"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
