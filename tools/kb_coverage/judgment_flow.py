# -*- coding: utf-8 -*-
"""判定流程 ↔ 块级账本（r6）的接线：切片段登记、判决结账、failed-only 重开。

## 它消灭的失败

1. **同一块被两次重跑当新工作**：每行先用
   `work_key = sha256(stage|block_key|content_sha|processor_version)` 登记；重复
   ingest / settle 命中同一行，不新增行、不重置 attempts。
2. **公式/数字改了但旧判决仍被复用**：`content_sha` 取**全内容**
   （`kb_coverage.content_identity`），内容变则新 `work_key`，旧行 `superseded_by`
   指向新行；`verify_judgment_fp.py` 的 CJK-only `fp` 不再参与账本身份。
3. **重排 chunk_id 后判决绑错块**：`block_key` 只由 `source_rel + 规范化 locator`
   决定（`judge_ledger.locator_from_row` 会剔除 chunk_id），重排不改逻辑身份。
4. **判决文件缺行/失败原因丢失**：`settle` 对“判决 CSV 已存在、但该块没有行”记
   `failed/transient`（带 `last_error`）；已 judged 的行永不改写，failed 行必须先
   `reopen` 才能再次 `judge`。
5. **重跑无法解释进度**：`status` 直接读账本三态计数；`reopen` 只放回
   `failed` 且 `attempts < max_attempts` 的 transient/policy 行。

## 与既有工具的分工

- `check_slice_verdicts.py` 仍是**判决内容门**（表头/枚举/节点存在/字段/段数）；
- `verify_judgment_fp.py` 仍是**引用门**（判定悬空/撞键/fp）；
- 本模块只做**工作账**：这块有没有判决、重跑几次、失败原因是什么。
  因此 `settle` 不重复内容校验——先跑内容门，再结账。

## 用法

    python -m kb_coverage.judgment_flow init
    python -m kb_coverage.judgment_flow ingest --slices tools/kb_coverage/tables/judgment_slices \
        --processor-version "prompt:<sha12>|model:<p/m@v>|rules:<r>|canon:text-nfc-lf-v1"
    python -m kb_coverage.judgment_flow claim --owner "$HOSTNAME" --limit 20
    python -m kb_coverage.judgment_flow settle --slices tools/kb_coverage/tables/judgment_slices \
        --verdicts knowledge-production/judgment-verdicts \
        --processor-version "<same as ingest>"
    python -m kb_coverage.judgment_flow reopen --max-attempts 3 --reason "provider outage"
    python -m kb_coverage.judgment_flow status
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path
from typing import Any

REPO = Path(__file__).resolve().parents[2]
if str(REPO / "tools") not in sys.path:
    sys.path.insert(0, str(REPO / "tools"))

from kb_coverage import judge_ledger as jl  # noqa: E402
from kb_coverage.content_identity import content_sha as content_sha_of  # noqa: E402

STAGE = "judge"


def _slice_paths(slices_dir: Path | str) -> list[Path]:
    root = Path(slices_dir)
    if not root.is_dir():
        raise FileNotFoundError(f"切片目录不存在：{root}")
    return sorted(p for p in root.glob("*.jsonl") if p.is_file())


def _identity(row: dict[str, Any], processor_version: str, ordinal: int) -> tuple[str, str, str]:
    """一行切片 → `(block_key, content_sha, work_key)`，即 r6 的三层键。

    `block_key` = sha256(source_rel | canonical_locator)；`content_sha` = 全内容 sha；
    `work_key` = sha256(stage | block_key | content_sha | processor_version)。
    """
    source_rel = row.get("chunk_rel") or row.get("rel_path")
    if not source_rel:
        raise ValueError(f"切片行缺 chunk_rel/rel_path（序号 {ordinal}）")
    block_key = jl.block_key(source_rel, jl.locator_from_row(row, ordinal))
    text = row.get("text")
    if text is not None:
        content_sha = content_sha_of(text)
    else:
        content_sha = row.get("content_sha")
    if not content_sha:
        raise ValueError(f"切片行缺 text/content_sha：{source_rel}（序号 {ordinal}）")
    return (
        block_key,
        content_sha,
        jl.work_key(STAGE, block_key, content_sha, processor_version),
    )


def ingest_slices(
    conn,
    slices_dir: Path | str,
    *,
    processor_version: str,
    backup: bool = True,
    now: Any = None,
) -> dict[str, int]:
    """把切片段登记进账本（按 work_key 幂等）。返回 `rows/inserted/existing/files`。"""
    paths = _slice_paths(slices_dir)
    if backup:
        db = jl.db_path_of(conn)
        if db is not None:
            jl.backup_db(db)
    stats = {"files": 0, "rows": 0, "inserted": 0, "existing": 0}
    for path in paths:
        part = jl.ingest_jsonl(
            conn,
            path,
            stage=STAGE,
            processor_version=processor_version,
            backup=False,
            now=now,
        )
        stats["files"] += 1
        for key in ("rows", "inserted", "existing"):
            stats[key] += int(part[key])
    return stats


def _verdict_rows(path: Path) -> dict[tuple[str, str], list[dict[str, str]]]:
    rows: dict[tuple[str, str], list[dict[str, str]]] = {}
    with path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            key = ((row.get("chunk_rel") or ""), (row.get("chunk_id") or ""))
            rows.setdefault(key, []).append({k: (v or "") for k, v in row.items()})
    return rows


def _slice_rows(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        row = json.loads(line)
        if not isinstance(row, dict):
            raise ValueError(f"{path}:{lineno} 不是 JSON 对象")
        rows.append(row)
    return rows


def settle_verdicts(
    conn,
    slices_dir: Path | str,
    verdicts_dir: Path | str,
    *,
    processor_version: str,
    owner: str = "judgment-flow",
    lease_seconds: int = 300,
    backup: bool = True,
    now: Any = None,
) -> dict[str, int]:
    """把已有判决 CSV 结账到账本：有行 → judged；CSV 在但该块无行 → failed/transient。

    - 判决 CSV 不存在：整片保持 pending（还没开判，不是失败）；
    - 已 judged：幂等跳过；已 failed：保持原状，等显式 `reopen`；
    - 结账前先用 `claim_one` 领取该块：一次结账 = 一次 attempt（attempts+1），
      这样 failed-only 重开的 `attempts < max_attempts` 才有真实含义；
    - 不做内容校验（由 `check_slice_verdicts.py` 负责），只回答“这块有没有判决产物”。
    """
    slices = _slice_paths(slices_dir)
    verdict_root = Path(verdicts_dir)
    if backup:
        db = jl.db_path_of(conn)
        if db is not None:
            jl.backup_db(db)

    stats = {"slices": 0, "judged": 0, "failed": 0, "unchanged": 0, "awaiting_reopen": 0}
    for slice_path in slices:
        verdict_path = verdict_root / f"{slice_path.stem}.csv"
        if not verdict_path.is_file():
            continue
        stats["slices"] += 1
        verdicts = _verdict_rows(verdict_path)
        for ordinal, row in enumerate(_slice_rows(slice_path), 1):
            _, _, wkey = _identity(row, processor_version, ordinal)
            current, _ = jl.ingest(
                conn,
                stage=STAGE,
                source_rel=row.get("chunk_rel") or row.get("rel_path"),
                locator=jl.locator_from_row(row, ordinal),
                processor_version=processor_version,
                text=row.get("text"),
                content_sha=row.get("content_sha"),
                chunk_id=row.get("chunk_id"),
                now=now,
            )
            status = current["status"]
            if status == "judged":
                stats["unchanged"] += 1
                continue
            if status == "failed":
                stats["awaiting_reopen"] += 1
                continue
            claimed = jl.claim_one(conn, wkey, owner=owner,
                                   lease_seconds=lease_seconds, now=now)
            if claimed is None:
                # 另一个 worker 已持有活跃租约；本轮不抢。
                stats["unchanged"] += 1
                continue
            key = ((row.get("chunk_rel") or ""), (row.get("chunk_id") or ""))
            if key in verdicts:
                jl.judge(conn, wkey, result_path=str(verdict_path), now=now)
                stats["judged"] += 1
            else:
                jl.fail(
                    conn,
                    wkey,
                    failure_class="transient",
                    error=f"verdict row missing in {verdict_path.name} "
                          f"for {key[0]}#{key[1]}",
                    now=now,
                )
                stats["failed"] += 1
    return stats


def claim_pending(
    conn,
    *,
    owner: str,
    limit: int = 1,
    lease_seconds: int = 300,
    now: Any = None,
) -> list[dict[str, Any]]:
    return jl.claim(conn, stage=STAGE, owner=owner, limit=limit,
                    lease_seconds=lease_seconds, now=now)


def reopen_failed(
    conn,
    *,
    max_attempts: int,
    include_permanent: bool = False,
    reason: str | None = None,
    backup: bool = True,
    now: Any = None,
) -> list[str]:
    """failed-only 重开：judged 永不重开，integrity 永不重开，attempts 保留。"""
    if backup:
        db = jl.db_path_of(conn)
        if db is not None:
            jl.backup_db(db)
    return jl.reopen_failed(conn, stage=STAGE, max_attempts=max_attempts,
                            include_permanent=include_permanent, reason=reason, now=now)


def status(conn) -> dict[str, int]:
    return jl.status_counts(conn, stage=STAGE)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="判定流程 ↔ 块级账本（r6）")
    ap.add_argument("--db", default=str(jl.DEFAULT_DB), help="SQLite 路径（默认 build/kb_ledger）")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("init")

    p_ingest = sub.add_parser("ingest")
    p_ingest.add_argument("--slices", required=True)
    p_ingest.add_argument("--processor-version", required=True)
    p_ingest.add_argument("--no-backup", action="store_true")

    p_claim = sub.add_parser("claim")
    p_claim.add_argument("--owner", required=True)
    p_claim.add_argument("--limit", type=int, default=1)
    p_claim.add_argument("--lease-seconds", type=int, default=300)

    p_settle = sub.add_parser("settle")
    p_settle.add_argument("--slices", required=True)
    p_settle.add_argument("--verdicts", required=True)
    p_settle.add_argument("--processor-version", required=True)
    p_settle.add_argument("--owner", default="judgment-flow")
    p_settle.add_argument("--lease-seconds", type=int, default=300)
    p_settle.add_argument("--no-backup", action="store_true")

    p_judge = sub.add_parser("judge")
    p_judge.add_argument("--work-key", required=True)
    p_judge.add_argument("--result-path", required=True)

    p_fail = sub.add_parser("fail")
    p_fail.add_argument("--work-key", required=True)
    p_fail.add_argument("--class", dest="failure_class", required=True,
                        choices=jl.FAILURE_CLASSES)
    p_fail.add_argument("--error", required=True)

    p_reopen = sub.add_parser("reopen")
    p_reopen.add_argument("--max-attempts", type=int, required=True)
    p_reopen.add_argument("--include-permanent", action="store_true")
    p_reopen.add_argument("--reason", default="")
    p_reopen.add_argument("--no-backup", action="store_true")

    p_status = sub.add_parser("status")

    args = ap.parse_args(argv)
    conn = jl.init_db(args.db, backup=False)
    try:
        if args.cmd == "init":
            print(f"账本已初始化：{args.db}")
        elif args.cmd == "ingest":
            stats = ingest_slices(conn, args.slices, processor_version=args.processor_version,
                                  backup=not args.no_backup)
            print(json.dumps(stats, ensure_ascii=False))
        elif args.cmd == "claim":
            rows = claim_pending(conn, owner=args.owner, limit=args.limit,
                                 lease_seconds=args.lease_seconds)
            print(json.dumps(rows, ensure_ascii=False, indent=1))
        elif args.cmd == "settle":
            stats = settle_verdicts(conn, args.slices, args.verdicts,
                                    processor_version=args.processor_version,
                                    owner=args.owner,
                                    lease_seconds=args.lease_seconds,
                                    backup=not args.no_backup)
            print(json.dumps(stats, ensure_ascii=False))
        elif args.cmd == "judge":
            row, changed = jl.judge(conn, args.work_key, result_path=args.result_path)
            print(json.dumps({"changed": changed, "row": row}, ensure_ascii=False, indent=1))
        elif args.cmd == "fail":
            row, changed = jl.fail(conn, args.work_key, failure_class=args.failure_class,
                                   error=args.error)
            print(json.dumps({"changed": changed, "row": row}, ensure_ascii=False, indent=1))
        elif args.cmd == "reopen":
            keys = reopen_failed(conn, max_attempts=args.max_attempts,
                                 include_permanent=args.include_permanent,
                                 reason=args.reason, backup=not args.no_backup)
            print(json.dumps({"reopened": len(keys), "work_keys": keys},
                             ensure_ascii=False, indent=1))
        elif args.cmd == "status":
            print(json.dumps(status(conn), ensure_ascii=False))
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
