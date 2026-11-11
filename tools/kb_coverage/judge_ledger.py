# -*- coding: utf-8 -*-
"""块级判定账本：稳定逻辑身份 + 全内容 sha + pending→judged/failed 状态机。

## 它消灭的失败

1. **重复计费/重复写判定**：同一块被两次重跑当成新工作。账本用
   `work_key = sha256(stage|block_key|content_sha|processor_version)` 做唯一裁决者，
   重复 ingest/claim 不新增行、不重置 attempts。
2. **公式/数字改了但判定不重跑**：旧 `office_extract._fp` 只哈希 CJK，
   `$x=1$`→`$x=2$` 指纹不变。本模块的 `content_sha` 取**全内容**（见
   `kb_coverage.content_identity`），内容变则新 `work_key`，旧行 `superseded_by` 指向新行。
3. **重排 chunk_id 后判定绑错块**：`block_key` 只由 `source_rel + 规范化 locator`
   决定，`chunk_id` 只作兼容列存储，不参与身份哈希。
4. **失败原因/尝试次数丢失**：`failed` 行必须带 `last_error`；`attempts` 只由
   `claim` 增加。`reopen_failed` 只重开 failed 且按失败类别/尝试上限筛选，judged 永不重开。

## 状态机

    pending --claim(attempts+1, lease)--> pending/processing
    pending --judge(result_path)--------> judged（终态）
    pending --fail(class,error)---------> failed（终态）
    failed  --reopen_failed(...)--------> pending（attempts 保留，reopen_epoch+1）

租约（`lease_owner/lease_expires_at`）表达“正在处理”，不新增 `running` 状态。

## 写前守卫 + 备份

- `ingest(..., payload_path=...)`：写账本前重算 payload 的 `content_sha`，不一致
  直接 `LedgerIntegrityError`，不落行。
- `ingest_jsonl(..., db_path=...)`：读源文件前后比对 `(size, mtime_ns)`，变化即拒写；
  写库前把已有 SQLite 复制为 `<db>.bak`。
- `office_extract.run` 对块池做同样的写前签名守卫与 `.bak` 备份。

## 用法

    python -m kb_coverage.judge_ledger init
    python -m kb_coverage.judge_ledger ingest --jsonl slices/a.jsonl --stage judge \
        --processor-version "prompt:abc|model:m@1|rules:r1|canon:text-nfc-lf-v1"
    python -m kb_coverage.judge_ledger claim --stage judge --owner host --limit 10
    python -m kb_coverage.judge_ledger judge --work-key <wk> --result-path out.json
    python -m kb_coverage.judge_ledger fail --work-key <wk> --class transient --error "..."
    python -m kb_coverage.judge_ledger reopen --stage judge --max-attempts 3 --reason "..."
    python -m kb_coverage.judge_ledger status
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterator, Mapping

REPO = Path(__file__).resolve().parents[2]
if str(REPO / "tools") not in sys.path:  # 直跑引导
    sys.path.insert(0, str(REPO / "tools"))

from kb_coverage.content_identity import (  # noqa: E402
    CANON_VERSION,
    content_sha as content_sha_of,
    file_text_content_sha,
    sha256_text,
)

DEFAULT_DB = REPO / "build" / "kb_ledger" / "ledger.sqlite3"
SEP = "\x1f"
STATUSES = ("pending", "judged", "failed")
FAILURE_CLASSES = ("transient", "permanent", "integrity", "policy")

SCHEMA = """
CREATE TABLE IF NOT EXISTS block_ledger (
  work_key           TEXT PRIMARY KEY,
  block_key          TEXT NOT NULL,
  source_rel         TEXT NOT NULL,
  source_locator     TEXT NOT NULL,
  chunk_id           TEXT,
  stage              TEXT NOT NULL,
  content_sha        TEXT NOT NULL,
  canon_version      TEXT NOT NULL,
  processor_version  TEXT NOT NULL,
  status             TEXT NOT NULL CHECK (status IN ('pending','judged','failed')),
  attempts           INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  last_error         TEXT,
  failure_class      TEXT CHECK (failure_class IS NULL
                                 OR failure_class IN ('transient','permanent','integrity','policy')),
  payload_path       TEXT,
  result_path        TEXT,
  lease_owner        TEXT,
  lease_expires_at   TEXT,
  reopen_epoch       INTEGER NOT NULL DEFAULT 0,
  supersedes         TEXT,
  superseded_by      TEXT,
  created_at         TEXT NOT NULL,
  updated_at         TEXT NOT NULL,
  completed_at       TEXT,
  CHECK (status <> 'failed' OR last_error IS NOT NULL),
  CHECK (status <> 'judged' OR result_path IS NOT NULL)
);

-- 同一逻辑块 + 同一 stage 只能有一条“当前版本”
CREATE UNIQUE INDEX IF NOT EXISTS ux_block_current
  ON block_ledger(stage, block_key)
  WHERE superseded_by IS NULL;

-- 防哈希实现变化导致唯一性失效：显式四元组也唯一
CREATE UNIQUE INDEX IF NOT EXISTS ux_block_work
  ON block_ledger(stage, block_key, content_sha, processor_version);

CREATE INDEX IF NOT EXISTS ix_block_status
  ON block_ledger(stage, status, created_at);
"""


class LedgerIntegrityError(RuntimeError):
    """payload/内容哈希与账本不一致：不调用模型、不落行。"""


def _nonempty(name: str, value: Any) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{name} 不能为空")
    return value


def _now(value: datetime | str | None = None) -> str:
    if value is None:
        value = datetime.now(timezone.utc)
    if isinstance(value, datetime):
        if value.tzinfo is None:
            value = value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc).isoformat(timespec="seconds")
    return str(value)


def _lease_expiry(now: datetime | str | None, lease_seconds: int) -> str:
    if lease_seconds <= 0:
        raise ValueError("lease_seconds 必须 > 0")
    if now is None:
        base = datetime.now(timezone.utc)
    elif isinstance(now, datetime):
        base = now if now.tzinfo else now.replace(tzinfo=timezone.utc)
    else:
        base = datetime.fromisoformat(str(now))
    return (base + timedelta(seconds=lease_seconds)).astimezone(timezone.utc).isoformat(
        timespec="seconds")


def canonical_locator(locator: Mapping[str, Any] | str) -> str:
    """规范化 locator；**剔除 chunk_id**，因为可重排的编号不是逻辑位置。

    locator 至少要有 page/ordinal/span/paragraph/bbox/heading 之一。只给 chunk_id
    会得到空 locator 并显式报错——不允许把“可重排编号”偷偷当块身份。
    """
    if isinstance(locator, Mapping):
        obj = {str(k): v for k, v in locator.items()
               if k != "chunk_id" and v is not None and v != ""}
        if not obj:
            raise ValueError(
                "locator 不能为空，也不能只含 chunk_id；请提供 page/ordinal/span/paragraph/heading")
        return json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    if isinstance(locator, str):
        raw = locator.strip()
        if not raw:
            raise ValueError("locator 不能为空")
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            return raw
        if not isinstance(parsed, Mapping):
            raise ValueError("locator JSON 必须是对象")
        return canonical_locator(parsed)
    raise TypeError("locator 必须是对象、JSON 对象字符串或非空字符串")


def block_key(source_rel: str, locator: Mapping[str, Any] | str) -> str:
    """逻辑块身份：`sha256(source_rel \\x1f canonical_locator)`。"""
    source_rel = _nonempty("source_rel", source_rel)
    return sha256_text(f"{source_rel}{SEP}{canonical_locator(locator)}")


def work_key(stage: str, block_key: str, content_sha: str, processor_version: str) -> str:
    """幂等键：`sha256(stage|block_key|content_sha|processor_version)`。"""
    for name, value in (("stage", stage), ("block_key", block_key),
                        ("content_sha", content_sha),
                        ("processor_version", processor_version)):
        _nonempty(name, value)
    return sha256_text(SEP.join((stage, block_key, content_sha, processor_version)))


def _row_to_dict(row: sqlite3.Row | None) -> dict[str, Any] | None:
    return dict(row) if row is not None else None


def connect(db_path: Path | str | None = None) -> sqlite3.Connection:
    path = Path(db_path) if db_path is not None else DEFAULT_DB
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(path), isolation_level=None, timeout=30)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


def db_path_of(conn: sqlite3.Connection) -> Path | None:
    row = conn.execute("PRAGMA database_list").fetchone()
    return Path(row[2]) if row and row[2] else None


def backup_db(path: Path | str | None = None, *, suffix: str = ".bak") -> Path | None:
    """写前备份：已有非空数据库复制为 `<db>.bak`。返回备份路径（无需备份时 None）。"""
    db = Path(path) if path is not None else None
    if db is None or not db.exists() or db.stat().st_size == 0:
        return None
    dest = db.with_name(db.name + suffix)
    dest.parent.mkdir(parents=True, exist_ok=True)
    # 用 sqlite backup API 而不是 copy2：WAL 模式下只复制主文件会漏掉未 checkpoint 的页。
    src = sqlite3.connect(str(db))
    try:
        dst = sqlite3.connect(str(dest))
        try:
            src.backup(dst)
        finally:
            dst.close()
    finally:
        src.close()
    return dest


def init_db(path: Path | str | None = None, *, backup: bool = True) -> sqlite3.Connection:
    db = Path(path) if path is not None else DEFAULT_DB
    if backup:
        backup_db(db)
    conn = connect(db)
    conn.executescript(SCHEMA)
    return conn


@contextmanager
def _immediate(conn: sqlite3.Connection) -> Iterator[None]:
    conn.execute("BEGIN IMMEDIATE")
    try:
        yield
    except Exception:
        conn.execute("ROLLBACK")
        raise
    else:
        conn.execute("COMMIT")


def ingest(
    conn: sqlite3.Connection,
    *,
    stage: str,
    source_rel: str,
    locator: Mapping[str, Any] | str,
    processor_version: str,
    text: str | None = None,
    content_sha: str | None = None,
    chunk_id: str | None = None,
    payload_path: Path | str | None = None,
    canon_version: str = CANON_VERSION,
    now: datetime | str | None = None,
) -> tuple[dict[str, Any], bool]:
    """登记一次逻辑工作。返回 `(行, 是否新建)`；重复 ingest 不新增行、不动 attempts。

    写前守卫：`payload_path` 存在时先重算其内容哈希，与 `content_sha` 不一致即拒写。
    内容变更（同 block_key 不同 content_sha）会新建行，并把旧当前行的
    `superseded_by` 指向新 work_key；旧行保留用于审计/回滚。
    """
    stage = _nonempty("stage", stage)
    processor_version = _nonempty("processor_version", processor_version)
    source_rel = _nonempty("source_rel", source_rel)
    bkey = block_key(source_rel, locator)

    if text is not None:
        computed = content_sha_of(text)
        if content_sha is not None and content_sha != computed:
            raise LedgerIntegrityError(
                f"text 的 content_sha={computed} 与显式传入的 {content_sha} 不一致")
        csha = computed
    elif content_sha is not None:
        csha = _nonempty("content_sha", content_sha)
    else:
        raise ValueError("text 与 content_sha 至少给一个")

    if payload_path is not None:
        payload = Path(payload_path)
        if not payload.is_file():
            raise FileNotFoundError(f"payload 不存在：{payload}")
        actual = file_text_content_sha(payload)
        if actual != csha:
            raise LedgerIntegrityError(
                f"payload 内容哈希 {actual} 与账本 content_sha {csha} 不一致：{payload}")

    wkey = work_key(stage, bkey, csha, processor_version)
    stamp = _now(now)
    with _immediate(conn):
        existing = conn.execute(
            "SELECT * FROM block_ledger WHERE work_key = ?", (wkey,)).fetchone()
        if existing is not None:
            return dict(existing), False

        current = conn.execute(
            "SELECT * FROM block_ledger WHERE stage = ? AND block_key = ? "
            "AND superseded_by IS NULL", (stage, bkey)).fetchone()

        # 先让旧当前行退出 ux_block_current，再插新行；插入失败事务回滚。
        if current is not None:
            conn.execute(
                "UPDATE block_ledger SET superseded_by = ?, updated_at = ? WHERE work_key = ?",
                (wkey, stamp, current["work_key"]),
            )
        conn.execute(
            """INSERT INTO block_ledger
               (work_key, block_key, source_rel, source_locator, chunk_id, stage,
                content_sha, canon_version, processor_version, status, attempts,
                payload_path, supersedes, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', 0, ?, ?, ?, ?)""",
            (wkey, bkey, source_rel, canonical_locator(locator), chunk_id, stage,
             csha, canon_version, processor_version, str(payload_path) if payload_path else None,
             current["work_key"] if current is not None else None, stamp, stamp),
        )
        row = conn.execute("SELECT * FROM block_ledger WHERE work_key = ?", (wkey,)).fetchone()
        return dict(row), True


def claim(
    conn: sqlite3.Connection,
    *,
    stage: str,
    owner: str,
    limit: int = 1,
    lease_seconds: int = 300,
    now: datetime | str | None = None,
) -> list[dict[str, Any]]:
    """原子领取 pending 行：attempts+1、写租约。返回领取行。"""
    stage = _nonempty("stage", stage)
    owner = _nonempty("owner", owner)
    if limit < 1:
        raise ValueError("limit 必须 >= 1")
    stamp = _now(now)
    expires = _lease_expiry(now, lease_seconds)
    with _immediate(conn):
        rows = conn.execute(
            """SELECT * FROM block_ledger
               WHERE stage = ? AND status = 'pending' AND superseded_by IS NULL
                 AND (lease_expires_at IS NULL OR lease_expires_at <= ?)
               ORDER BY created_at, work_key
               LIMIT ?""",
            (stage, stamp, limit),
        ).fetchall()
        out: list[dict[str, Any]] = []
        for row in rows:
            conn.execute(
                """UPDATE block_ledger
                   SET lease_owner = ?, lease_expires_at = ?, attempts = attempts + 1,
                       updated_at = ?
                   WHERE work_key = ?""",
                (owner, expires, stamp, row["work_key"]),
            )
            fresh = conn.execute(
                "SELECT * FROM block_ledger WHERE work_key = ?", (row["work_key"],)).fetchone()
            out.append(dict(fresh))
        return out


def claim_one(
    conn: sqlite3.Connection,
    work_key: str,
    *,
    owner: str,
    lease_seconds: int = 300,
    now: datetime | str | None = None,
) -> dict[str, Any] | None:
    """原子领取**指定** work_key（单块 worker / 判定结账用）。

    与 `claim` 同一套状态机：只领取 pending、未被取代、租约已过期的行；成功时
    attempts+1 并写租约。不可领取时返回 None（不改变行），不存在时抛 KeyError。
    """
    work_key = _nonempty("work_key", work_key)
    owner = _nonempty("owner", owner)
    stamp = _now(now)
    expires = _lease_expiry(now, lease_seconds)
    with _immediate(conn):
        row = conn.execute(
            "SELECT * FROM block_ledger WHERE work_key = ?", (work_key,)).fetchone()
        if row is None:
            raise KeyError(f"账本无此 work_key：{work_key}")
        if row["superseded_by"] is not None:
            return None
        if row["status"] != "pending":
            return None
        if row["lease_expires_at"] is not None and row["lease_expires_at"] > stamp:
            return None
        conn.execute(
            """UPDATE block_ledger
               SET lease_owner = ?, lease_expires_at = ?, attempts = attempts + 1,
                   updated_at = ?
               WHERE work_key = ?""",
            (owner, expires, stamp, work_key),
        )
        fresh = conn.execute(
            "SELECT * FROM block_ledger WHERE work_key = ?", (work_key,)).fetchone()
        return dict(fresh)


def judge(
    conn: sqlite3.Connection,
    work_key: str,
    *,
    result_path: Path | str,
    content_sha: str | None = None,
    now: datetime | str | None = None,
) -> tuple[dict[str, Any], bool]:
    """pending → judged。已 judged 且 result_path 相同 → 幂等返回 False。"""
    work_key = _nonempty("work_key", work_key)
    result_path = _nonempty("result_path", str(result_path))
    stamp = _now(now)
    with _immediate(conn):
        row = conn.execute(
            "SELECT * FROM block_ledger WHERE work_key = ?", (work_key,)).fetchone()
        if row is None:
            raise KeyError(f"账本无此 work_key：{work_key}")
        if row["superseded_by"] is not None:
            raise ValueError(f"该 work_key 已被新内容取代：{work_key}")
        if content_sha is not None and content_sha != row["content_sha"]:
            raise LedgerIntegrityError(
                f"提交时 content_sha={content_sha} 与账本 {row['content_sha']} 不一致")
        if row["status"] == "judged":
            if row["result_path"] == result_path:
                return dict(row), False
            raise ValueError("已 judged 的 work_key 不允许换 result_path 重写")
        if row["status"] == "failed":
            raise ValueError("failed 行必须先 reopen 才能 judge")
        conn.execute(
            """UPDATE block_ledger
               SET status = 'judged', result_path = ?, completed_at = ?, updated_at = ?,
                   lease_owner = NULL, lease_expires_at = NULL,
                   last_error = NULL, failure_class = NULL
               WHERE work_key = ?""",
            (result_path, stamp, stamp, work_key),
        )
        fresh = conn.execute(
            "SELECT * FROM block_ledger WHERE work_key = ?", (work_key,)).fetchone()
        return dict(fresh), True


def fail(
    conn: sqlite3.Connection,
    work_key: str,
    *,
    failure_class: str,
    error: str,
    now: datetime | str | None = None,
) -> tuple[dict[str, Any], bool]:
    """pending → failed。attempts 不在这里增加（由 claim 增加）。"""
    work_key = _nonempty("work_key", work_key)
    if failure_class not in FAILURE_CLASSES:
        raise ValueError(f"failure_class 必须是 {FAILURE_CLASSES} 之一")
    error = _nonempty("error", error)
    stamp = _now(now)
    with _immediate(conn):
        row = conn.execute(
            "SELECT * FROM block_ledger WHERE work_key = ?", (work_key,)).fetchone()
        if row is None:
            raise KeyError(f"账本无此 work_key：{work_key}")
        if row["superseded_by"] is not None:
            raise ValueError(f"该 work_key 已被新内容取代：{work_key}")
        if row["status"] == "judged":
            raise ValueError("judged 是终态，不能改成 failed")
        if (row["status"] == "failed" and row["failure_class"] == failure_class
                and row["last_error"] == error):
            return dict(row), False
        conn.execute(
            """UPDATE block_ledger
               SET status = 'failed', failure_class = ?, last_error = ?,
                   completed_at = ?, updated_at = ?,
                   lease_owner = NULL, lease_expires_at = NULL
               WHERE work_key = ?""",
            (failure_class, error, stamp, stamp, work_key),
        )
        fresh = conn.execute(
            "SELECT * FROM block_ledger WHERE work_key = ?", (work_key,)).fetchone()
        return dict(fresh), True


def reopen_failed(
    conn: sqlite3.Connection,
    *,
    stage: str,
    max_attempts: int,
    include_permanent: bool = False,
    reason: str | None = None,
    now: datetime | str | None = None,
) -> list[str]:
    """failed-only 重开：只把 failed 且 attempts < max_attempts 的行放回 pending。

    - `judged` 永不重开；内容变更走新 work_key，不是重开。
    - `failure_class='integrity'` 永不自动重开。
    - `permanent` 默认不重开；必须 `include_permanent=True` 且给出 `reason`。
    - attempts 保留；reopen_epoch+1。重复执行同一筛选第二次返回空列表（幂等）。
    """
    stage = _nonempty("stage", stage)
    if max_attempts < 1:
        raise ValueError("max_attempts 必须 >= 1")
    if include_permanent and not (reason or "").strip():
        raise ValueError("重开 permanent 失败必须提供非空 reason")
    stamp = _now(now)
    classes = ["transient", "policy"]
    if include_permanent:
        classes.append("permanent")
    placeholders = ",".join("?" for _ in classes)
    with _immediate(conn):
        rows = conn.execute(
            f"""SELECT work_key FROM block_ledger
                WHERE stage = ? AND status = 'failed' AND superseded_by IS NULL
                  AND attempts < ?
                  AND failure_class IN ({placeholders})
                ORDER BY created_at, work_key""",
            (stage, max_attempts, *classes),
        ).fetchall()
        keys = [r["work_key"] for r in rows]
        for key in keys:
            conn.execute(
                """UPDATE block_ledger
                   SET status = 'pending', lease_owner = NULL, lease_expires_at = NULL,
                       reopen_epoch = reopen_epoch + 1, completed_at = NULL, updated_at = ?
                   WHERE work_key = ?""",
                (stamp, key),
            )
        return keys


def status_counts(conn: sqlite3.Connection, *, stage: str | None = None,
                  current_only: bool = True) -> dict[str, int]:
    """按 status 计数；缺省状态补 0。默认只数当前版本（排除已被取代的旧行）。"""
    where = ["1 = 1"]
    params: list[Any] = []
    if stage is not None:
        where.append("stage = ?")
        params.append(stage)
    if current_only:
        where.append("superseded_by IS NULL")
    sql = ("SELECT status, COUNT(*) AS n FROM block_ledger WHERE "
           + " AND ".join(where) + " GROUP BY status")
    counts = {s: 0 for s in STATUSES}
    for row in conn.execute(sql, tuple(params)):
        counts[row["status"]] = int(row["n"])
    counts["total"] = sum(counts[s] for s in STATUSES)
    return counts


def locator_from_row(row: Mapping[str, Any], ordinal: int) -> Mapping[str, Any] | str:
    """从切片/池行推导 locator（不含 chunk_id）。

    优先显式 `source_locator`/`locator`；否则用 page/span/paragraph/bbox，
    再补 heading；都没有时用**行序** ordinal。行序在“只重排 chunk_id、不重排行”
    的迁移窗口内稳定（`key_remap.csv` 正是这种迁移）。
    """
    explicit = row.get("source_locator") or row.get("locator")
    if explicit:
        return explicit
    loc: dict[str, Any] = {}
    for key in ("page", "span", "paragraph", "bbox"):
        value = row.get(key)
        if value is not None and value != "":
            loc[key] = value
    heading = row.get("heading")
    if heading is not None and heading != "":
        loc["heading"] = heading
    if not loc:
        loc["ordinal"] = ordinal
    elif not any(k in loc for k in ("page", "span", "paragraph", "bbox")):
        # 只有 heading 时用同文件内行序消歧，保证同一 heading 的多块可区分。
        loc["ordinal"] = ordinal
    return loc


def _file_signature(path: Path) -> tuple[int, int]:
    stat = path.stat()
    return stat.st_size, stat.st_mtime_ns


def ingest_jsonl(
    conn: sqlite3.Connection,
    path: Path | str,
    *,
    stage: str,
    processor_version: str,
    backup: bool = True,
    now: datetime | str | None = None,
) -> dict[str, int]:
    """把切片/池 JSONL 登记进账本；重复行按 work_key 幂等。

    写前守卫：整份文件读入后、写库前比对 `(size, mtime_ns)`；期间被改写则拒写。
    备份：写库前对已有数据库做 `backup_db`（`.bak`）。
    """
    src = Path(path)
    before = _file_signature(src)
    text = src.read_text(encoding="utf-8")
    if _file_signature(src) != before:
        raise RuntimeError(f"源文件在读取期间被改写，拒绝 ingest：{src}")

    rows: list[dict[str, Any]] = []
    for lineno, line in enumerate(text.splitlines(), 1):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"{src}:{lineno} JSON 解析失败：{exc.msg}") from exc
        if not isinstance(row, dict):
            raise ValueError(f"{src}:{lineno} 不是 JSON 对象")
        rows.append(row)

    db = db_path_of(conn)
    if backup and db is not None:
        backup_db(db)

    stats = {"rows": 0, "inserted": 0, "existing": 0}
    for ordinal, row in enumerate(rows, 1):
        source_rel = row.get("chunk_rel") or row.get("rel_path")
        if not source_rel:
            raise ValueError(f"{src} 第 {ordinal} 行缺 chunk_rel/rel_path")
        if row.get("text") is None and not row.get("content_sha"):
            raise ValueError(f"{src} 第 {ordinal} 行缺 text/content_sha")
        _, created = ingest(
            conn,
            stage=stage,
            source_rel=str(source_rel),
            locator=locator_from_row(row, ordinal),
            processor_version=processor_version,
            text=row.get("text"),
            content_sha=row.get("content_sha"),
            chunk_id=row.get("chunk_id"),
            payload_path=row.get("payload_path"),
            now=now,
        )
        stats["rows"] += 1
        stats["inserted" if created else "existing"] += 1
    return stats


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="块级判定账本（r6）")
    ap.add_argument("--db", default=str(DEFAULT_DB), help="SQLite 路径（默认 build/kb_ledger）")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("init")
    p_ing = sub.add_parser("ingest")
    p_ing.add_argument("--jsonl", required=True)
    p_ing.add_argument("--stage", required=True)
    p_ing.add_argument("--processor-version", required=True)
    p_ing.add_argument("--no-backup", action="store_true")

    p_claim = sub.add_parser("claim")
    p_claim.add_argument("--stage", required=True)
    p_claim.add_argument("--owner", required=True)
    p_claim.add_argument("--limit", type=int, default=1)
    p_claim.add_argument("--lease-seconds", type=int, default=300)

    p_judge = sub.add_parser("judge")
    p_judge.add_argument("--work-key", required=True)
    p_judge.add_argument("--result-path", required=True)

    p_fail = sub.add_parser("fail")
    p_fail.add_argument("--work-key", required=True)
    p_fail.add_argument("--class", dest="failure_class", required=True,
                        choices=FAILURE_CLASSES)
    p_fail.add_argument("--error", required=True)

    p_reopen = sub.add_parser("reopen")
    p_reopen.add_argument("--stage", required=True)
    p_reopen.add_argument("--max-attempts", type=int, required=True)
    p_reopen.add_argument("--include-permanent", action="store_true")
    p_reopen.add_argument("--reason", default="")

    p_status = sub.add_parser("status")
    p_status.add_argument("--stage", default=None)

    args = ap.parse_args(argv)
    conn = init_db(args.db, backup=args.cmd not in ("init", "status"))
    try:
        if args.cmd == "init":
            print(f"账本已初始化：{args.db}")
        elif args.cmd == "ingest":
            stats = ingest_jsonl(conn, args.jsonl, stage=args.stage,
                                 processor_version=args.processor_version,
                                 backup=not args.no_backup)
            print(json.dumps(stats, ensure_ascii=False))
        elif args.cmd == "claim":
            rows = claim(conn, stage=args.stage, owner=args.owner, limit=args.limit,
                         lease_seconds=args.lease_seconds)
            print(json.dumps(rows, ensure_ascii=False, indent=1))
        elif args.cmd == "judge":
            row, changed = judge(conn, args.work_key, result_path=args.result_path)
            print(json.dumps({"changed": changed, "row": row}, ensure_ascii=False, indent=1))
        elif args.cmd == "fail":
            row, changed = fail(conn, args.work_key, failure_class=args.failure_class,
                                error=args.error)
            print(json.dumps({"changed": changed, "row": row}, ensure_ascii=False, indent=1))
        elif args.cmd == "reopen":
            keys = reopen_failed(conn, stage=args.stage, max_attempts=args.max_attempts,
                                 include_permanent=args.include_permanent,
                                 reason=args.reason)
            print(json.dumps({"reopened": len(keys), "work_keys": keys},
                             ensure_ascii=False, indent=1))
        elif args.cmd == "status":
            print(json.dumps(status_counts(conn, stage=args.stage), ensure_ascii=False))
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
