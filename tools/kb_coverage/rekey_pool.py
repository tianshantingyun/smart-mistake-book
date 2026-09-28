# -*- coding: utf-8 -*-
"""块池 `(rel_path, chunk_id)` 键唯一化：确定性重编号 + 判定表重映射 + fp 对拍。

## 它消灭的失败

store_53_transcripts 曾每轮从 001 重排 cid，把同一 rel 的块写成重复键（实测四本
2027 版 53 扫描件：2,309 个重复键 / 5,738 行与他人撞键）。materialize 以
`(rel_path, chunk_id)` 建 dict，撞键静默只留末行——先写的块内容被丢。本工具：

1. **重编号**：只动有重复键的 rel（改唯一键 rel 的 id 会连带打断 sidecar slug 与
   状态机 output_ref）。同 rel 内按块池行出现顺序排 001..N，前缀
   `sha256(rel_path)[:10]` 不变；
2. **备份 + 原子落盘**：先 `shutil.copy2` 把迁移源备份为 `extracted_chunks.jsonl.bak`，
   写临时文件后 `os.replace`；
3. **映射表**：`tables/key_remap.csv`（chunk_rel, old_chunk_id, new_chunk_id, fp），
   重排 rel 每行一条（含 old==new 的行——判定表撞键消歧需要全量旧键对应关系）；
4. **判定表重映射 + fp 对拍**：`material_judgments.csv` 的 chunk_id 按映射表改写。
   逐行校验：未重排键 → 判定行必须命中池行；唯一旧键 → 新键的池行 fp 必须与映射表
   一致（内容不变、只改 id）；撞键（旧键对应多条池行）→ 用判定行内容指纹匹配
   具体池行，匹配不上记入 `tables/fp_mismatch.csv` 并 exit 1（不静默改绑）。

## 池路径迁移（2026-09-29，见 kb_coverage/pool_path.py）

旧名 `extracted_chunks.jsonl` 的句柄被宿主进程持有（rename/replace 均 WinError
5/32），无法就地改名，所以**首次运行读旧名、写入生效名
`extracted_chunks.rekeyed.jsonl`**（同目录新目录项，不碰旧句柄）。后续所有工具
经 `pool_path.POOL_PATH` 读生效池。

**收尾清理步骤（P4 验收阶段执行）**：旧句柄放开后，把
`extracted_chunks.rekeyed.jsonl` rename 回 `extracted_chunks.jsonl`（先删旧文件，
再把 `pool_path.POOL_PATH` 改回规范名并删除 `LEGACY_POOL` 与迁移分支）；若届时
仍锁，保留双名、在验收报告"遗留"写明，旧文件删除等用户明令。

幂等：重跑时生效池已存在且无重复键 → 不写池、不写备份、不重映射判定表（0 变化）。

用法：
    python tools/kb_coverage/rekey_pool.py
"""

from __future__ import annotations

import csv
import hashlib
import json
import os
import re
import shutil
import sys
import time
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))

from kb_coverage.pool_path import POOL_PATH, LEGACY_POOL  # noqa: E402

CHUNKS = POOL_PATH            # 当前生效池（重排后的唯一键池）
LEGACY = LEGACY_POOL          # 旧名原始池（P0.2 迁移源；句柄被宿主锁住，只读不写）
BACKUP = LEGACY.with_name(LEGACY.name + ".bak")
REMAP = REPO / "tools/kb_coverage/tables/key_remap.csv"
JUDGMENTS = REPO / "tools/kb_coverage/tables/material_judgments.csv"
MISMATCH = REPO / "tools/kb_coverage/tables/fp_mismatch.csv"
REMAP_COLS = ("chunk_rel", "old_chunk_id", "new_chunk_id", "fp")
MISMATCH_COLS = ("chunk_rel", "chunk_id", "action", "node_slug", "reason")
CJK = re.compile(r"[\u4e00-\u9fff]")


def base_of(rel_path: str) -> str:
    return hashlib.sha256(rel_path.encode("utf-8")).hexdigest()[:10]


def _source_pool() -> Path:
    """迁移源：当前生效池（rekeyed 名）还不存在时读旧名原始池，否则读生效池。"""
    if not CHUNKS.exists() and LEGACY.exists():
        return LEGACY
    return CHUNKS


def load_pool() -> list[dict]:
    rows: list[dict] = []
    with _source_pool().open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def renumber_plan(rows: list[dict]) -> tuple[list[dict], list[dict]]:
    """按 rel 重排有重复键的 rel，返回 (行列表[就地改], 映射表行)。

    唯一键的 rel 一律不动：改它们的 id 会连带打断 sidecar slug 与状态机 output_ref。
    同 rel 内按行出现顺序 001..N，前缀 base=sha256(rel_path)[:10] 不变。
    """
    by_rel: dict[str, list[int]] = defaultdict(list)
    for i, r in enumerate(rows):
        by_rel[r.get("rel_path") or ""].append(i)
    remap: list[dict] = []
    for rel, idxs in by_rel.items():
        keys = [rows[i].get("chunk_id") or "" for i in idxs]
        if len(keys) == len(set(keys)):
            continue
        base = base_of(rel)
        for n, i in enumerate(idxs, 1):
            old = rows[i].get("chunk_id") or ""
            new = f"{base}-{n:03d}"
            rows[i]["chunk_id"] = new
            remap.append({"chunk_rel": rel, "old_chunk_id": old,
                          "new_chunk_id": new, "fp": rows[i].get("fp") or ""})
    return rows, remap


def _content_fps(row: dict) -> set[str]:
    """判定行内容指纹候选（全文 sha256 与仅 CJK sha256，两种池 fp 口径都试）。"""
    out: set[str] = set()
    for field in ("content", "title", "summary"):
        t = (row.get(field) or "").strip()
        if t:
            out.add(hashlib.sha256(t.encode("utf-8")).hexdigest())
            out.add(hashlib.sha256("".join(CJK.findall(t)).encode("utf-8")).hexdigest())
    return out


def remap_judgments(pool_rows: list[dict]) -> tuple[int, list[dict], int]:
    """判定表 chunk_id 重映射 + 逐行 fp 对拍。

    返回 (重映射行数, mismatch 列表, 判定表总行数)。判定表只在确有改写时写回。
    """
    pool: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for r in pool_rows:
        pool[(r["rel_path"], r["chunk_id"])].append(r)

    old_to_new: dict[tuple[str, str], list[tuple[str, str]]] = defaultdict(list)
    if REMAP.exists():
        with REMAP.open(encoding="utf-8", newline="") as fh:
            for r in csv.DictReader(fh):
                old_to_new[(r["chunk_rel"], r["old_chunk_id"])].append(
                    (r["new_chunk_id"], r["fp"]))

    if not JUDGMENTS.exists():
        return 0, [], 0
    rows = list(csv.DictReader(JUDGMENTS.open(encoding="utf-8")))
    cols = list(rows[0].keys())
    remapped = 0
    mismatches: list[dict] = []
    for r in rows:
        key = (r["chunk_rel"], r["chunk_id"])
        entries = old_to_new.get(key)
        if not entries:
            # 未重排键：必须直接命中池行（fp 非空）
            prows = pool.get(key)
            if not prows:
                mismatches.append(dict(chunk_rel=r["chunk_rel"], chunk_id=r["chunk_id"],
                                       action=r["action"], node_slug=r["node_slug"],
                                       reason="判定引用悬空：池中无此键"))
            elif len(prows) > 1:
                mismatches.append(dict(chunk_rel=r["chunk_rel"], chunk_id=r["chunk_id"],
                                       action=r["action"], node_slug=r["node_slug"],
                                       reason="判定引用撞键且未重排"))
            elif not (prows[0].get("fp") or "").strip():
                mismatches.append(dict(chunk_rel=r["chunk_rel"], chunk_id=r["chunk_id"],
                                       action=r["action"], node_slug=r["node_slug"],
                                       reason="池行无 fp"))
            continue
        if len(entries) == 1:
            new_id, efp = entries[0]
            prows = pool.get((r["chunk_rel"], new_id))
            if not prows:
                mismatches.append(dict(chunk_rel=r["chunk_rel"], chunk_id=r["chunk_id"],
                                       action=r["action"], node_slug=r["node_slug"],
                                       reason="重排目标缺失：新键不在池中"))
                continue
            if (prows[0].get("fp") or "") != efp:
                mismatches.append(dict(chunk_rel=r["chunk_rel"], chunk_id=r["chunk_id"],
                                       action=r["action"], node_slug=r["node_slug"],
                                       reason="fp 不一致：映射表 vs 池行"))
                continue
            r["chunk_id"] = new_id
            remapped += 1
            continue
        # 撞键（旧键对应多条池行）：内容指纹匹配具体池行，匹配不上不猜
        cand = _content_fps(r)
        hits = [e for e in entries if e[1] in cand]
        if len(hits) == 1:
            r["chunk_id"] = hits[0][0]
            remapped += 1
        else:
            mismatches.append(dict(chunk_rel=r["chunk_rel"], chunk_id=r["chunk_id"],
                                   action=r["action"], node_slug=r["node_slug"],
                                   reason=f"撞键且内容指纹匹配不上（旧键对应 {len(entries)} 条池行）"))
    if remapped:
        with JUDGMENTS.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=cols, lineterminator="\n")
            w.writeheader()
            w.writerows(rows)
    return remapped, mismatches, len(rows)


def _atomic_replace(tmp: Path, target: Path) -> None:
    """临时文件 → 目标原子替换。Windows 上目标若被外部进程持有句柄（共享树会话、
    扫描器等），os.replace 会报 WinError 5/32——限次重试，重试仍失败才抛。"""
    last: Exception | None = None
    for attempt in range(20):
        try:
            os.replace(tmp, target)
            return
        except PermissionError as exc:  # pragma: no cover - 平台锁，仅实机触发
            last = exc
            time.sleep(3.0)
    raise last  # type: ignore[misc]


def main(argv: list[str] | None = None) -> int:
    rows = load_pool()
    pool_rows = len(rows)
    rows, remap = renumber_plan(rows)

    keys: set[tuple[str, str]] = set()
    dup = 0
    for r in rows:
        k = (r["rel_path"], r["chunk_id"])
        if k in keys:
            dup += 1
        keys.add(k)

    pool_written = False
    if remap:
        if dup:
            print(f"重排后仍存在 {dup} 个重复键，拒绝写盘", file=sys.stderr)
            return 2
        shutil.copy2(_source_pool(), BACKUP)
        tmp = CHUNKS.with_name(CHUNKS.name + ".tmp")
        with tmp.open("w", encoding="utf-8") as fh:
            for r in rows:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")
        _atomic_replace(tmp, CHUNKS)
        with REMAP.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=list(REMAP_COLS), lineterminator="\n")
            w.writeheader()
            w.writerows(remap)
        pool_written = True

    # 判定表重映射只在**本轮真的重写了池**时执行：重排是稠密编号，旧序号数字会被
    # 新行复用，若重跑时无条件再映射，会把"新写的、恰好顶旧序号"的判定行错搬到
    # 别的键上。池没变的重跑 = 无操作（判定表上一轮已重映射完）。
    remapped = 0
    mismatches: list[dict] = []
    judgment_rows = 0
    if pool_written:
        remapped, mismatches, judgment_rows = remap_judgments(rows)
        if mismatches:
            MISMATCH.parent.mkdir(parents=True, exist_ok=True)
            with MISMATCH.open("w", encoding="utf-8", newline="") as fh:
                w = csv.DictWriter(fh, fieldnames=list(MISMATCH_COLS), lineterminator="\n")
                w.writeheader()
                w.writerows(mismatches)
        elif MISMATCH.exists():
            MISMATCH.unlink()
    elif JUDGMENTS.exists():
        with JUDGMENTS.open(encoding="utf-8") as fh:
            judgment_rows = sum(1 for _ in fh) - 1  # 减表头

    stats = {
        "poolRows": pool_rows,
        "uniqueKeys": len(keys),
        "dupKeyCount": dup,
        "poolWritten": pool_written,
        "remapRows": len(remap),
        "judgmentRows": judgment_rows,
        "judgmentRemapped": remapped,
        "mismatchCount": len(mismatches),
    }
    print(json.dumps(stats, ensure_ascii=False))
    return 1 if mismatches else 0


if __name__ == "__main__":
    raise SystemExit(main())
