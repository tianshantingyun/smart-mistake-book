#!/usr/bin/env python3
"""closure 4（schema 72）备份包夹具的生成器与独立校验器（KD-83 / WS5 item 42-46）。

**它消灭的具体失败**：仓库里唯一的 v4 用例（`SmbkArchiveCodecTest`）用被测代码
（`ClosureRuleTables.v4` + `ClosureRuleTables.digest`）给被测代码出题——规则表漂了、
键集少一张、closureVersion 写错时，夹具与断言一起错，用例照绿（批次 41 记录 F14）。
本脚本把"生成夹具所需的每一项输入"都换成**不执行 Kotlin 被测代码**也能重算的来源：

* 91 键：`V4_ROW_COUNT_KEYS` 冻结字面量，逐字取自 `git show 70509247`（closure 4 时点），
  并与已入库的 `72.json` 实体集合双向核对（缺一即失败）；
* 行数：`sqlite3` CLI（Android SDK 的独立实现）与 CPython 内置 SQLite 两次 `COUNT(*)`，
  两份实现必须一致；
* 摘要：本文件的 `v4_digest()`（Python `hashlib`），payload 形状逐行对照历史源码
  `SmbkSelectionPlan.closureDigest`（payload 的 4 行头 + 按表名排序的 `table=count`）。

载荷是**真的 schema 72 库**（`PRAGMA user_version=72`，含 2 个视图、91 张声明表、
`room_master_table` 身份哈希，`error_cause_*` 不存在，`knowledge_search_material_feature`
存在且为 0 行），不是"从 v5 包删两张表"——后者 `user_version` 仍是 73，恢复时
72→73 迁移根本不会跑，KD-83 要证明的东西被整条跳过。

用法（Python 3.13，`-I` 隔离运行）：

    python -I tools/ci/make_v4_pack_fixture.py build \
        --schema core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/72.json \
        --out-dir tools/backup_fixtures/v4 \
        --sqlite3 C:/Android/Sdk/platform-tools/sqlite3.exe

    python -I tools/ci/make_v4_pack_fixture.py verify \
        --pack tools/backup_fixtures/v4/closure4-schema72-mask15.smbk \
        --golden tools/backup_fixtures/v4/v4-pack.golden.json \
        --sqlite3 C:/Android/Sdk/platform-tools/sqlite3.exe

夹具有意**逐字节冻结**：`ZipInfo` 时间戳固定为 1980-01-01、条目顺序固定、压缩级别固定。
重新冻结必须是一次显式提交（新 golden + 旧 sha256 存档），CI 的职责是 `verify` 而不是
重新生成。
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import pathlib
import re
import sqlite3
import subprocess
import sys
import tempfile
import zipfile

V4_PREFIX = "smbk-selection-v4"
V4_CLOSURE = 4
SCHEMA_VERSION = 72
FORMAT_VERSION = 3
ASSET_POLICY = "INCLUDED"
CATEGORY_BITS = (
    ("ISSUE_BOOK", 1),
    ("MASTERY", 2),
    ("SESSIONS", 4),
    ("IMAGES", 8),
)
FULL_MASK = 15

GOLDEN_NAME = "closure4-schema72-mask15.smbk"
FORGED_NAME = "forged-count.smbk"
DROPPED_NONEMPTY_NAME = "dropped-nonempty.smbk"
GOLDEN_JSON = "v4-pack.golden.json"

# 固定时间戳 = 包内 createdAt；L8：合成件不伪装成"某用户真实备份"。
FIXED_CREATED_AT = 1_700_000_000_000

_IDENTIFIER = re.compile(r"^[a-z][a-z0-9_]*$")
_TABLE_PLACEHOLDER = "$" + "{TABLE_NAME}"
_VIEW_PLACEHOLDER = "$" + "{VIEW_NAME}"

# 91 项，逐字取自 `git show 70509247:.../SmbkSelectionPlan.kt` 的
# categoryTables + alwaysTables + derivedTables + splitTables（字母序）。
# 提交后的运行不依赖 git 历史；本字面量就是"历史键集"的冻结副本。
V4_ROW_COUNT_KEYS = (
    "active_review_plan_slot",
    "agent_pending_request",
    "answer_reveal_outcome",
    "applied_answer_reveal_record",
    "applied_attempt_record",
    "applied_correction_record",
    "applied_tutor_answer_exposure_record",
    "assessment_answer_reveal_event",
    "assessment_event",
    "assessment_evidence_attribution",
    "assessment_evidence_snapshot",
    "assessment_item_snapshot",
    "assessment_presentation",
    "attempt_correction",
    "attempt_event",
    "attempt_submission",
    "batch_import_job",
    "batch_import_page",
    "binding_audit_sample",
    "binding_change_event",
    "canonical_source_asset",
    "clean_redraw_task",
    "content_install_state",
    "error_book_entry",
    "figure_generation_attempt",
    "figure_generation_operation",
    "independent_correct_observation",
    "knowledge_grounding_request",
    "knowledge_grounding_resolution",
    "knowledge_node",
    "knowledge_node_relation",
    "knowledge_node_source_binding",
    "knowledge_research_review_bundle",
    "knowledge_research_review_source",
    "knowledge_search_feature",
    "knowledge_search_index_state",
    "knowledge_search_material_feature",
    "knowledge_source",
    "knowledge_teaching_material",
    "knowledge_teaching_material_node_binding",
    "learner_chat_evidence",
    "learner_knowledge_mastery_state",
    "learner_problem_memory_state",
    "learner_projection_snapshot",
    "learning_sequence",
    "library_search_content",
    "library_search_fts",
    "library_search_outbox",
    "llm_teaching_advisory",
    "mistake_export_record",
    "model_task",
    "model_task_event",
    "model_task_operation",
    "practice_unit",
    "practice_unit_knowledge_binding",
    "prediction_outcome",
    "presentation_projection_state",
    "problem",
    "problem_classification_binding",
    "problem_classification_candidate",
    "problem_draft",
    "problem_draft_commit_receipt",
    "problem_draft_edit_snapshot",
    "problem_draft_revision",
    "problem_draft_source_asset",
    "problem_organization_receipt",
    "problem_relation",
    "problem_revision",
    "problem_revision_source_asset",
    "problem_revision_visual",
    "projection_archive",
    "projection_outbox",
    "review_log",
    "review_plan",
    "review_queue_item",
    "review_queue_knowledge_node",
    "review_queue_reason",
    "review_session",
    "review_session_advance_receipt",
    "review_session_revision",
    "split_import_job",
    "split_import_question",
    "student_model_prediction",
    "tutor_answer_exposure",
    "tutor_answer_exposure_outcome",
    "tutor_conversation",
    "tutor_message",
    "tutor_message_source_asset",
    "tutor_message_visual",
    "tutor_visual_program",
    "tutor_visual_snapshot",
)
V4_KEY_SET = frozenset(V4_ROW_COUNT_KEYS)

# 表名是 SQL 标识符，不能作为绑定参数；本脚本只从**冻结的 91 键字面量**派生查询文本，
# 任何不在该集合里的名字在 bare_identifier 处直接失败。下面两条常量在导入期一次性构造，
# 调用点（execute / sqlite3 CLI 脚本）不再拼接任何字符串。
_COUNT_SQL_BY_TABLE = {
    table: 'SELECT COUNT(*) FROM "' + table + '"' for table in V4_ROW_COUNT_KEYS
}
_COUNT_SCRIPT = "\n".join(
    "SELECT '" + table + "' || '|' || COUNT(*) FROM \"" + table + "\";"
    for table in V4_ROW_COUNT_KEYS
)

# 真实行：ISSUE_BOOK(problem) / MASTERY(attempt_submission) / ALWAYS(knowledge_node,
# content_install_state)。全 0 的 91 键包能被"声明全 0"恒过，所以黄金件必须带行。
SEED_SQL = (
    "INSERT INTO `problem` (problem_id, canonical_fingerprint, subject, created_at_epoch_millis)"
    " VALUES ('v4fixture-problem-1','fp-v4-1','math',1700000000000);",
    "INSERT INTO `attempt_submission` (submission_id, learner_id, payload_fingerprint)"
    " VALUES ('v4fixture-submission-1','v4fixture-learner','fp-sub-1');",
    "INSERT INTO `attempt_submission` (submission_id, learner_id, payload_fingerprint)"
    " VALUES ('v4fixture-submission-2','v4fixture-learner','fp-sub-2');",
    "INSERT INTO `knowledge_node` (knowledge_node_id, stable_code, subject, display_name,"
    " taxonomy_version, created_at_epoch_millis)"
    " VALUES ('v4fixture-node-1','V4-FIXTURE-NODE-1','math','夹具知识点','v4-fixture',"
    "1700000000000);",
    "INSERT INTO `content_install_state` (pack_id, content_version, applied_at_epoch_millis)"
    " VALUES ('v4-fixture-pack','v4-fixture-content',1700000000000);",
)

# N2：被删表非空。FK 干净（source → material → material_feature 逐级补父行），
# 因此它被拒的理由只会是目标判据（D3/声明非 0），不会被引用完整性侧因掩盖。
DROPPED_NONEMPTY_SQL = SEED_SQL + (
    "INSERT INTO `knowledge_source` (source_id, subject, source_type, title, license_status,"
    " content_fingerprint, imported_at_epoch_millis)"
    " VALUES ('v4fixture-source-1','math','TEST','夹具来源','TEST_ONLY','fp-v4-source-1',"
    "1700000000000);",
    "INSERT INTO `knowledge_teaching_material` (material_id, stable_code, subject,"
    " material_type, title, summary_markdown, applicability_markdown, content_markdown,"
    " boundary_markdown, derivation_kind, source_id, source_locator, content_fingerprint,"
    " reviewed_at_epoch_millis)"
    " VALUES ('v4fixture-material-1','V4-FIXTURE-MATERIAL-1','math','SUMMARY','夹具材料',"
    "'摘要','适用','正文','边界','FIXTURE','v4fixture-source-1','locator-1','fp-v4-material-1',"
    "1700000000000);",
    "INSERT INTO `knowledge_search_material_feature`"
    " (subject, material_id, knowledge_node_id, search_feature)"
    " VALUES ('math','v4fixture-material-1','v4fixture-node-1','夹具');",
)


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def bare_identifier(name: str) -> str:
    if not isinstance(name, str) or not _IDENTIFIER.match(name) or name not in V4_KEY_SET:
        raise SystemExit(f"非法或未登记的标识符：{name!r}")
    return name


def run_sqlite(sqlite3_exe: str, db: pathlib.Path, script: str) -> str:
    """SQL 经 stdin 交给 sqlite3 CLI（不经 shell、不拼命令串）。

    Windows 上 sqlite3.exe 的诊断行可能落在宿主代码页（非 UTF-8），统一 `errors=replace`
    只为把失败信息带回来；SQL 本身按 UTF-8 字节送入。
    """
    done = subprocess.run(
        [sqlite3_exe, str(db)],
        input=script.encode("utf-8"),
        capture_output=True,
        check=False,
    )
    stdout = done.stdout.decode("utf-8", errors="replace")
    stderr = done.stderr.decode("utf-8", errors="replace")
    if done.returncode != 0:
        raise SystemExit(f"sqlite3 执行失败：{stderr.strip()}")
    return stdout


def load_schema(schema_json: pathlib.Path) -> dict:
    return json.loads(schema_json.read_text(encoding="utf-8"))["database"]


def schema_table_names(schema: dict) -> set[str]:
    return {str(entity["tableName"]) for entity in schema["entities"]}


def build_schema_db(
    schema: dict,
    out_db: pathlib.Path,
    sqlite3_exe: str,
    seed_sql: tuple[str, ...],
) -> None:
    """按导出 schema 物化一份真库（entities → indices → views → setupQueries → user_version）。"""
    if int(schema["version"]) != SCHEMA_VERSION:
        raise SystemExit(f"schema JSON 版本必须是 {SCHEMA_VERSION}（v4 时点）")
    names = schema_table_names(schema)
    if names != V4_KEY_SET:
        raise SystemExit(f"冻结键集与 {SCHEMA_VERSION}.json 不一致：{sorted(names ^ V4_KEY_SET)}")

    parts = ["PRAGMA journal_mode=DELETE;", "PRAGMA foreign_keys=OFF;"]
    for entity in schema["entities"]:
        table = bare_identifier(str(entity["tableName"]))
        parts.append(str(entity["createSql"]).replace(_TABLE_PLACEHOLDER, table) + ";")
        for index in entity.get("indices", []):
            parts.append(str(index["createSql"]).replace(_TABLE_PLACEHOLDER, table) + ";")
    for view in schema.get("views", []):
        view_name = str(view["viewName"])
        if not _IDENTIFIER.match(view_name):
            raise SystemExit(f"非法视图名：{view_name!r}")
        parts.append(str(view["createSql"]).replace(_VIEW_PLACEHOLDER, view_name) + ";")
    parts += [str(query) + ";" for query in schema.get("setupQueries", [])]
    parts.append(f"PRAGMA user_version = {SCHEMA_VERSION};")
    parts += list(seed_sql)
    parts.append("VACUUM;")

    if out_db.exists():
        out_db.unlink()
    out_db.parent.mkdir(parents=True, exist_ok=True)
    run_sqlite(sqlite3_exe, out_db, "\n".join(parts) + "\n")


def row_counts_cli(db: pathlib.Path, sqlite3_exe: str) -> dict[str, int]:
    """一次调用数完 91 张表：输出 `表名|行数`；缺表由 sqlite3 报错（非零退出）。"""
    counts: dict[str, int] = {}
    for line in run_sqlite(sqlite3_exe, db, _COUNT_SCRIPT + "\n").splitlines():
        line = line.strip()
        if not line:
            continue
        table, _, count = line.partition("|")
        if table not in V4_KEY_SET:
            raise SystemExit(f"未登记的表名：{table!r}")
        counts[table] = int(count)
    if set(counts) != V4_KEY_SET:
        raise SystemExit(f"计数缺表：{sorted(V4_KEY_SET - set(counts))}")
    return counts


def row_counts_cpython(db: pathlib.Path) -> dict[str, int]:
    """第二实现：CPython 内置 SQLite（与 `sqlite3` CLI 是两份独立实现）。"""
    connection = sqlite3.connect(f"file:{db.as_posix()}?mode=ro", uri=True)
    try:
        counts = {}
        for table in V4_ROW_COUNT_KEYS:
            cursor = connection.execute(_COUNT_SQL_BY_TABLE[table])
            counts[table] = int(cursor.fetchone()[0])
        return counts
    finally:
        connection.close()


def v4_digest(categories: list[str], asset_policy: str, counts: dict[str, int]) -> str:
    """v4 时点 payload 形状（逐行对照 `git show 70509247` 的 closureDigest）。"""
    if set(counts) != V4_KEY_SET:
        raise SystemExit(f"摘要输入键集不是 91 键：{sorted(set(counts) ^ V4_KEY_SET)}")
    lines = [
        V4_PREFIX,
        "closureVersion=" + str(V4_CLOSURE),
        "categories=" + ",".join(sorted(set(categories))),
        "assetPolicy=" + asset_policy,
    ]
    lines += [table + "=" + str(counts[table]) for table in sorted(counts)]
    return sha256_bytes(("\n".join(lines) + "\n").encode("utf-8"))


def categories_for_mask(mask: int) -> list[str]:
    return [name for name, bit in CATEGORY_BITS if mask & bit]


def build_pack(
    database_bytes: bytes,
    counts: dict[str, int],
    mask: int,
    problem_count: int,
) -> tuple[bytes, dict]:
    categories = categories_for_mask(mask)
    digest = v4_digest(categories, ASSET_POLICY, counts)
    manifest = {
        "formatVersion": FORMAT_VERSION,
        "databaseSchemaVersion": SCHEMA_VERSION,
        "createdAt": FIXED_CREATED_AT,
        "problemCount": problem_count,
        "assetCount": 0,
        "hashAlgorithm": "SHA-256",
        "appVersionName": "v4-fixture",
        "appVersionCode": 0,
        "contentVersion": None,
        "hasSettings": False,
        "settingsDigest": None,
        "files": [
            {
                "path": "database.sqlite",
                "sha256": sha256_bytes(database_bytes),
                "byteSize": len(database_bytes),
            }
        ],
        "referencedAssets": [],
        "selection": {
            "schemaVersion": 2,
            "categoryMask": mask,
            "categories": categories,
            "assetPolicy": ASSET_POLICY,
            "closureVersion": V4_CLOSURE,
            "closureDigest": digest,
            "rowCounts": {table: counts[table] for table in sorted(counts)},
            "trimmedTableCount": 0,
        },
    }
    manifest_bytes = json.dumps(
        manifest, ensure_ascii=False, separators=(",", ":")
    ).encode("utf-8")
    checksums = (sha256_bytes(database_bytes) + " *database.sqlite").encode("utf-8")

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for name, payload in (
            ("manifest.json", manifest_bytes),
            ("database.sqlite", database_bytes),
            ("checksums.sha256", checksums),
        ):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o600 << 16
            info.create_system = 0
            archive.writestr(info, payload, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    pack_bytes = buffer.getvalue()
    summary = {
        "packSha256": sha256_bytes(pack_bytes),
        "packBytes": len(pack_bytes),
        "databaseSha256": sha256_bytes(database_bytes),
        "databaseBytes": len(database_bytes),
        "closureDigest": digest,
        "nonZeroTables": {table: count for table, count in sorted(counts.items()) if count},
    }
    return pack_bytes, summary


def sqlite3_version(sqlite3_exe: str) -> str:
    done = subprocess.run(
        [sqlite3_exe, "--version"],
        capture_output=True,
        check=False,
    )
    if done.returncode != 0:
        raise SystemExit(
            "无法读取 sqlite3 版本：" + done.stderr.decode("utf-8", errors="replace").strip()
        )
    return done.stdout.decode("utf-8", errors="replace").strip()


def generator_sha256() -> str:
    return sha256_bytes(pathlib.Path(__file__).read_bytes())


def golden_body(
    pack_summary: dict, counts: dict[str, int], schema: dict, sqlite3_exe: str
) -> dict:
    compact_counts = json.dumps(
        {table: counts[table] for table in sorted(counts)},
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    return {
        "fixture": GOLDEN_NAME,
        "synthetic": True,
        "note": "合成夹具，不是用户真实备份；只证明读侧与 72→73 迁移，不证明写侧能产出 v4 包",
        "schemaJson": (
            "core/database/schemas/com.tingyun.smartmistakebook.core.database."
            "StudyDatabase/72.json"
        ),
        "schemaIdentityHash": schema["identityHash"],
        "schemaVersion": SCHEMA_VERSION,
        "formatVersion": FORMAT_VERSION,
        "closureVersion": V4_CLOSURE,
        "digestPrefix": V4_PREFIX,
        "categoryMask": FULL_MASK,
        "categories": categories_for_mask(FULL_MASK),
        "assetPolicy": ASSET_POLICY,
        "trimmedTableCount": 0,
        "rowCountKeys": list(V4_ROW_COUNT_KEYS),
        "rowCounts": {table: counts[table] for table in sorted(counts)},
        "rowCountsSha256": sha256_bytes(compact_counts),
        "packSha256": pack_summary["packSha256"],
        "packBytes": pack_summary["packBytes"],
        "databaseSha256": pack_summary["databaseSha256"],
        "databaseBytes": pack_summary["databaseBytes"],
        "closureDigest": pack_summary["closureDigest"],
        "nonZeroTables": pack_summary["nonZeroTables"],
        "negative": {},
        "toolchain": {
            "python": sys.version.split()[0],
            "sqlite3": sqlite3_version(sqlite3_exe),
            "generatorSha256": generator_sha256(),
        },
    }


def write_authority_files(
    out_dir: pathlib.Path,
    pack_bytes: bytes,
    golden: dict,
    negative: dict[str, tuple[bytes, dict]],
) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / GOLDEN_NAME).write_bytes(pack_bytes)
    (out_dir / (GOLDEN_NAME + ".sha256")).write_text(
        sha256_bytes(pack_bytes) + "\n", encoding="utf-8"
    )
    for name, (bytes_out, summary) in negative.items():
        (out_dir / name).write_bytes(bytes_out)
        (out_dir / (name + ".sha256")).write_text(
            sha256_bytes(bytes_out) + "\n", encoding="utf-8"
        )
        golden["negative"][name] = summary
    (out_dir / GOLDEN_JSON).write_text(
        json.dumps(golden, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def command_build(args: argparse.Namespace) -> int:
    schema = load_schema(pathlib.Path(args.schema))
    out_dir = pathlib.Path(args.out_dir)
    sqlite3_exe = args.sqlite3
    # sqlite3 CLI（Windows 32-bit）不能打开含中文的用户目录路径；工作目录固定在仓库内
    # ASCII 的 build/ 下（gitignored），权威源输出目录仍按参数。
    work_root = pathlib.Path(args.work_dir)
    work_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="v4fixture-", dir=work_root) as temp:
        temp_dir = pathlib.Path(temp)
        golden_db = temp_dir / "database.v72.sqlite"
        build_schema_db(schema, golden_db, sqlite3_exe, SEED_SQL)
        counts = row_counts_cli(golden_db, sqlite3_exe)
        counts_cpython = row_counts_cpython(golden_db)
        if counts != counts_cpython:
            raise SystemExit(
                "两份实现的逐表行数不一致："
                + str({k: (counts[k], counts_cpython[k]) for k in counts if counts[k] != counts_cpython[k]})
            )
        golden_db_bytes = golden_db.read_bytes()
        pack_bytes, summary = build_pack(golden_db_bytes, counts, FULL_MASK, counts["problem"])

        # N1：自洽伪造行数（声明 problem=5，实测 1，摘要按声明重算）→ codec 放行，只有恢复侧 R1 能抓。
        forged_counts = dict(counts)
        forged_counts["problem"] = 5
        forged_bytes, forged_summary = build_pack(
            golden_db_bytes, forged_counts, FULL_MASK, problem_count=counts["problem"]
        )
        forged_summary["forgedTable"] = "problem"
        forged_summary["declared"] = 5
        forged_summary["actual"] = counts["problem"]

        # N2：被删表非空（声明 1 并自洽重算摘要）→ codec D3 必须在行数对账前拒绝。
        dropped_db = temp_dir / "database.v72.dropped-nonempty.sqlite"
        build_schema_db(schema, dropped_db, sqlite3_exe, DROPPED_NONEMPTY_SQL)
        dropped_counts = row_counts_cli(dropped_db, sqlite3_exe)
        if dropped_counts != row_counts_cpython(dropped_db):
            raise SystemExit("N2 夹具的两份实现行数不一致")
        if dropped_counts["knowledge_search_material_feature"] != 1:
            raise SystemExit("N2 夹具必须真的在 knowledge_search_material_feature 写 1 行")
        dropped_bytes, dropped_summary = build_pack(
            dropped_db.read_bytes(), dropped_counts, FULL_MASK, dropped_counts["problem"]
        )

        golden = golden_body(summary, counts, schema, sqlite3_exe)
        write_authority_files(
            out_dir,
            pack_bytes,
            golden,
            {FORGED_NAME: (forged_bytes, forged_summary), DROPPED_NONEMPTY_NAME: (dropped_bytes, dropped_summary)},
        )

        if args.asset_dir:
            asset_dir = pathlib.Path(args.asset_dir)
            asset_dir.mkdir(parents=True, exist_ok=True)
            for name in (GOLDEN_NAME, FORGED_NAME, DROPPED_NONEMPTY_NAME):
                (asset_dir / name).write_bytes((out_dir / name).read_bytes())
                (asset_dir / (name + ".sha256")).write_bytes(
                    (out_dir / (name + ".sha256")).read_bytes()
                )
            (asset_dir / GOLDEN_JSON).write_bytes((out_dir / GOLDEN_JSON).read_bytes())

    generated = json.loads((out_dir / GOLDEN_JSON).read_text(encoding="utf-8"))
    print(json.dumps(
        {
            "pack": GOLDEN_NAME,
            "packSha256": generated["packSha256"],
            "packBytes": generated["packBytes"],
            "closureDigest": generated["closureDigest"],
            "nonZeroTables": generated["nonZeroTables"],
        },
        ensure_ascii=False,
    ))
    return 0


def read_zip_entries(pack: pathlib.Path) -> dict[str, bytes]:
    with zipfile.ZipFile(pack) as archive:
        names = archive.namelist()
        if sorted(names) != sorted(["manifest.json", "database.sqlite", "checksums.sha256"]):
            raise SystemExit(f"条目集合不是三件套：{names}")
        return {name: archive.read(name) for name in names}


def inspect_database(db: pathlib.Path, sqlite3_exe: str) -> dict:
    user_version = run_sqlite(sqlite3_exe, db, "PRAGMA user_version;\n").strip()
    tables = {
        line.strip()
        for line in run_sqlite(
            sqlite3_exe, db, "SELECT name FROM sqlite_master WHERE type='table';\n"
        ).splitlines()
        if line.strip()
    }
    identity = run_sqlite(
        sqlite3_exe, db, "SELECT identity_hash FROM room_master_table WHERE id=42;\n"
    ).strip()
    quick_check = run_sqlite(sqlite3_exe, db, "PRAGMA quick_check;\n").strip()
    foreign_keys = run_sqlite(sqlite3_exe, db, "PRAGMA foreign_key_check;\n").strip()
    return {
        "user_version": user_version,
        "tables": tables,
        "identity_hash": identity,
        "quick_check": quick_check,
        "foreign_key_check": foreign_keys,
    }


def verify_pack(
    pack: pathlib.Path,
    sqlite3_exe: str,
    golden: dict | None,
    work_dir: pathlib.Path,
) -> list[str]:
    problems: list[str] = []
    entries = read_zip_entries(pack)
    manifest = json.loads(entries["manifest.json"].decode("utf-8"))
    database_bytes = entries["database.sqlite"]

    if manifest.get("formatVersion") != FORMAT_VERSION:
        problems.append(f"formatVersion != {FORMAT_VERSION}")
    if manifest.get("databaseSchemaVersion") != SCHEMA_VERSION:
        problems.append(f"databaseSchemaVersion != {SCHEMA_VERSION}")
    files = manifest.get("files") or []
    if len(files) != 1 or files[0].get("path") != "database.sqlite":
        problems.append("files 必须恰好只有 database.sqlite")
    else:
        if files[0].get("sha256") != sha256_bytes(database_bytes):
            problems.append("database.sqlite 摘要与 manifest 不符")
        if files[0].get("byteSize") != len(database_bytes):
            problems.append("database.sqlite 大小与 manifest 不符")
    declared_checksums = entries["checksums.sha256"].decode("utf-8").strip()
    expected_checksums = f"{sha256_bytes(database_bytes)} *database.sqlite"
    if declared_checksums != expected_checksums:
        problems.append("checksums.sha256 文本与载荷不符")

    selection = manifest.get("selection") or {}
    if selection.get("closureVersion") != V4_CLOSURE:
        problems.append("selection.closureVersion != 4")
    if selection.get("schemaVersion") != 2:
        problems.append("selection.schemaVersion != 2")
    if selection.get("assetPolicy") != ASSET_POLICY:
        problems.append("selection.assetPolicy != INCLUDED")
    row_counts = {str(k): int(v) for k, v in (selection.get("rowCounts") or {}).items()}
    if set(row_counts) != V4_KEY_SET:
        problems.append(f"rowCounts 键集不是 91 键（差 {sorted(set(row_counts) ^ V4_KEY_SET)[:5]}）")
    else:
        digest = v4_digest(
            list(selection.get("categories") or []),
            str(selection.get("assetPolicy") or ""),
            row_counts,
        )
        if digest != selection.get("closureDigest"):
            problems.append(
                f"closureDigest 与独立重算不符：declared={selection.get('closureDigest')} recomputed={digest}"
            )

    with tempfile.TemporaryDirectory(prefix="v4verify-", dir=work_dir) as temp:
        db_path = pathlib.Path(temp) / "database.sqlite"
        db_path.write_bytes(database_bytes)
        try:
            info = inspect_database(db_path, sqlite3_exe)
        except SystemExit as failure:
            problems.append(f"载荷库不可读：{failure}")
            return problems
        actual = row_counts_cli(db_path, sqlite3_exe)
        if actual != row_counts_cpython(db_path):
            problems.append("两份实现的逐表行数不一致")
        if set(actual) != V4_KEY_SET:
            problems.append("实测计数缺表")
        if info["user_version"] != str(SCHEMA_VERSION):
            problems.append(f"载荷 user_version={info['user_version']} != {SCHEMA_VERSION}")
        if info["quick_check"] != "ok":
            problems.append(f"quick_check={info['quick_check']}")
        if info["foreign_key_check"]:
            problems.append(f"foreign_key_check 非空：{info['foreign_key_check'][:200]}")
        missing = sorted(V4_KEY_SET - info["tables"])
        if missing:
            problems.append(f"载荷缺表：{missing[:5]}")
        if any(name.startswith("error_cause") for name in info["tables"]):
            problems.append("v4 载荷不得含 error_cause_* 表")
        if "knowledge_search_material_feature" not in info["tables"]:
            problems.append("v4 载荷必须含 knowledge_search_material_feature")
        for table, declared in row_counts.items():
            if actual.get(table) != declared:
                problems.append(f"声明行数与实测不符：{table} declared={declared} actual={actual.get(table)}")
        if golden is not None:
            if golden.get("schemaIdentityHash") != info["identity_hash"]:
                problems.append("room_master_table 身份哈希与 72.json 不符")
            if golden.get("packSha256") != sha256_bytes(pack.read_bytes()):
                problems.append("pack sha256 与 golden 不符")
            if golden.get("closureDigest") != selection.get("closureDigest"):
                problems.append("closureDigest 与 golden 不符")
            if golden.get("rowCounts") != {k: v for k, v in row_counts.items()}:
                problems.append("rowCounts 与 golden 不符")
    return problems


def command_verify(args: argparse.Namespace) -> int:
    pack = pathlib.Path(args.pack)
    golden = None
    if args.golden:
        golden = json.loads(pathlib.Path(args.golden).read_text(encoding="utf-8"))
    work_root = pathlib.Path(args.work_dir)
    work_root.mkdir(parents=True, exist_ok=True)
    problems = verify_pack(pack, args.sqlite3, golden, work_root)
    if problems:
        for problem in problems:
            print(f"FAIL {problem}")
        return 1
    entries = read_zip_entries(pack)
    manifest = json.loads(entries["manifest.json"].decode("utf-8"))
    print(
        "PASS "
        + json.dumps(
            {
                "pack": pack.name,
                "packSha256": sha256_bytes(pack.read_bytes()),
                "packBytes": pack.stat().st_size,
                "closureVersion": manifest["selection"]["closureVersion"],
                "closureDigest": manifest["selection"]["closureDigest"],
                "rowCountKeys": len(manifest["selection"]["rowCounts"]),
            },
            ensure_ascii=False,
        )
    )
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    build = subparsers.add_parser("build", help="按 72.json 造真库并冻结 v4 夹具")
    build.add_argument("--schema", required=True, help="schema 72 JSON 路径")
    build.add_argument("--out-dir", required=True, help="权威源输出目录")
    build.add_argument("--sqlite3", required=True, help="sqlite3 CLI 路径")
    build.add_argument("--asset-dir", default=None, help="可选的 androidTest assets 副本目录")
    build.add_argument(
        "--work-dir", default="build/v4-fixture-work", help="ASCII 临时工作目录（gitignored）"
    )
    build.set_defaults(handler=command_build)

    verify = subparsers.add_parser("verify", help="只读复核一份 v4 夹具")
    verify.add_argument("--pack", required=True)
    verify.add_argument("--sqlite3", required=True)
    verify.add_argument("--golden", default=None)
    verify.add_argument(
        "--work-dir", default="build/v4-fixture-work", help="ASCII 临时工作目录（gitignored）"
    )
    verify.set_defaults(handler=command_verify)

    args = parser.parse_args(argv)
    return int(args.handler(args))


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
