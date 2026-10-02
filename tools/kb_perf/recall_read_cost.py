# -*- coding: utf-8 -*-
"""召回固定读数开销探针（S19 遗留项，2026-10-02 入库）。

**测什么**：每次知识库召回都会先做三笔"固定读数"——
`readVersion`（索引版本锚点）+ `countReviewedKnowledgeNodesBySubject`（`knowledge_node`
按科 COUNT）+ `countIndexedKnowledgeNodesBySubject`（`knowledge_search_feature` 按科
COUNT(DISTINCT …)，实测最贵的一笔）。三条 SQL 与 Kotlin DAO 的 @Query 逐字一致
（`KnowledgeSearchIndexStateDao.readVersion`、`ProblemOrganizationDao` 的两个计数）。

**为什么**：审计记过 p95 143ms / max 16.6s，S19 诊断把长尾拆成"召回本体 + 固定读数 +
稠密腿/父节点注入"；固定读数桌面档实测 ≈12.6ms/次，设备档只会更高。本脚本把这一笔变成
可复跑的确定性读数：表结构按生产 schema 导出（`57.json`）**逐字抄写**，数据规模对齐实测
口径（4 科 × 9,000 节点 / 每节点 24 条特征 ≈ 86.4 万特征行）。

**口径声明**：桌面 SQLite（Python 标准库，内存库）与 Android 内置可能版本不同——本脚本
的用途是**机制与量级**判断（SQL 形态会不会随规模劣化、改动前后有没有回归），最终数字
以真机仪器化为准。**不参与任何红绿门**。

**安全与一致性口径**：
- 数据全部由本脚本确定性生成（同参数 ⇒ 同数据），无任何外部输入；
- 每条 SQL 都是**字面量**（不拼装、不经变量），值一律走 `?` 绑定；
- 建表后 `assert_matches_schema()` 用 `PRAGMA table_info` 把**实际建出来的列**与
  `57.json` 的字段逐名核对（+ 索引名核对）——schema 一升级，本探针立刻报错而不是悄悄测旧表形。

用法：
    python tools/kb_perf/recall_read_cost.py                # 全量（默认 4×9,000 节点）
    python tools/kb_perf/recall_read_cost.py --quick        # 冒烟（4×200 节点，用于测试）
    python tools/kb_perf/recall_read_cost.py --json         # 机器可读
"""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
import statistics
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCHEMA_REL = ("core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/"
              "57.json")

SUBJECTS = ("MATH", "PHYSICS", "CHEMISTRY", "BIOLOGY")
TABLES = ("knowledge_node", "knowledge_search_feature", "knowledge_search_index_state")


def create_schema(conn: sqlite3.Connection) -> None:
    """按 57.json 逐字建表（列定义与索引与导出完全一致；见 assert_matches_schema 的核对）。"""
    conn.execute(
        "CREATE TABLE IF NOT EXISTS knowledge_node (`knowledge_node_id` TEXT NOT NULL, "
        "`stable_code` TEXT NOT NULL, `subject` TEXT NOT NULL, `display_name` TEXT NOT NULL, "
        "`canonical_name` TEXT NOT NULL DEFAULT '', `node_kind` TEXT NOT NULL DEFAULT 'TOPIC', "
        "`granularity` TEXT NOT NULL DEFAULT 'TOPIC', `aliases_text` TEXT NOT NULL DEFAULT '', "
        "`boundary_markdown` TEXT, `verification_status` TEXT NOT NULL DEFAULT 'MODEL_CANDIDATE', "
        "`parent_knowledge_node_id` TEXT, `taxonomy_version` TEXT NOT NULL, "
        "`created_at_epoch_millis` INTEGER NOT NULL, `status` TEXT NOT NULL DEFAULT 'ACTIVE', "
        "`superseded_by` TEXT, PRIMARY KEY(`knowledge_node_id`), "
        "FOREIGN KEY(`parent_knowledge_node_id`) REFERENCES `knowledge_node`(`knowledge_node_id`) "
        "ON UPDATE NO ACTION ON DELETE RESTRICT )")
    conn.execute(
        "CREATE TABLE IF NOT EXISTS knowledge_search_feature (`subject` TEXT NOT NULL, "
        "`search_feature` TEXT NOT NULL, `knowledge_node_id` TEXT NOT NULL, "
        "PRIMARY KEY(`subject`, `search_feature`, `knowledge_node_id`), "
        "FOREIGN KEY(`knowledge_node_id`) REFERENCES `knowledge_node`(`knowledge_node_id`) "
        "ON UPDATE NO ACTION ON DELETE CASCADE )")
    conn.execute(
        "CREATE TABLE IF NOT EXISTS knowledge_search_index_state (`subject` TEXT NOT NULL, "
        "`index_version` INTEGER NOT NULL, PRIMARY KEY(`subject`))")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_knowledge_node_stable_code` ON knowledge_node (`stable_code`)")
    conn.execute("CREATE INDEX IF NOT EXISTS `index_knowledge_node_parent_knowledge_node_id` ON knowledge_node (`parent_knowledge_node_id`)")
    conn.execute("CREATE INDEX IF NOT EXISTS `index_knowledge_node_subject_display_name` ON knowledge_node (`subject`, `display_name`)")
    conn.execute("CREATE INDEX IF NOT EXISTS `index_knowledge_node_subject_canonical_name` ON knowledge_node (`subject`, `canonical_name`)")
    conn.execute("CREATE INDEX IF NOT EXISTS `index_knowledge_node_subject_granularity` ON knowledge_node (`subject`, `granularity`)")
    conn.execute("CREATE INDEX IF NOT EXISTS `index_knowledge_search_feature_knowledge_node_id` ON knowledge_search_feature (`knowledge_node_id`)")
    conn.execute("CREATE INDEX IF NOT EXISTS `index_knowledge_search_feature_subject_knowledge_node_id` ON knowledge_search_feature (`subject`, `knowledge_node_id`)")


def assert_matches_schema(conn: sqlite3.Connection, schema_path: Path) -> None:
    """实际建出来的列名与索引名，必须与 schema 导出逐名一致（schema 升级即报错）。"""
    database = json.loads(schema_path.read_text(encoding="utf-8"))["database"]
    entities = {e["tableName"]: e for e in database["entities"]}

    columns = {
        "knowledge_node": {row[1] for row in conn.execute("PRAGMA table_info(knowledge_node)")},
        "knowledge_search_feature": {row[1] for row in conn.execute("PRAGMA table_info(knowledge_search_feature)")},
        "knowledge_search_index_state": {row[1] for row in conn.execute("PRAGMA table_info(knowledge_search_index_state)")},
    }
    for table in TABLES:
        expected = {f["columnName"] for f in entities[table]["fields"]}
        actual = columns[table]
        if expected != actual:
            raise AssertionError(
                f"{table} 列与 schema 不一致：缺 {sorted(expected - actual)}、多 {sorted(actual - expected)}")

    expected_indexes = set()
    for table in TABLES:
        for index in entities[table].get("indices") or []:
            name = re.search(r"INDEX IF NOT EXISTS `([a-z_]+)`", index["createSql"])
            if name:
                expected_indexes.add(name.group(1))
    actual_indexes = {row[0] for row in conn.execute(
        "SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%'")}
    if expected_indexes != actual_indexes:
        raise AssertionError(f"索引与 schema 不一致：缺 {sorted(expected_indexes - actual_indexes)}、"
                             f"多 {sorted(actual_indexes - expected_indexes)}")


def build(nodes_per_subject: int, features_per_node: int) -> sqlite3.Connection:
    """建内存库并灌入**确定性生成的合成数据**（同参数 ⇒ 同数据，读数可复跑）。"""
    conn = sqlite3.connect(":memory:")
    create_schema(conn)
    node_rows = []
    feature_rows = []
    for subject in SUBJECTS:
        for i in range(nodes_per_subject):
            node_id = f"kb:{subject}:atomic:node-{i}"
            node_rows.append((node_id, f"{subject}-{i}", subject, f"节点{i}", f"节点{i}",
                              "CURATED", "ACTIVE", "test-taxonomy", 0))
            for f in range(features_per_node):
                # 特征词表共享性近似：大部分特征在不同节点间共享（对齐 S19 的 4,000 词表口径）
                feature_rows.append((subject, f"feat-{i % 7}-{f % 4000}", node_id))
        conn.execute(
            "INSERT INTO knowledge_search_index_state (subject, index_version) VALUES (?, ?)",
            (subject, 1))
    conn.executemany(
        "INSERT INTO knowledge_node (knowledge_node_id, stable_code, subject, display_name,"
        " canonical_name, verification_status, status, taxonomy_version,"
        " created_at_epoch_millis) VALUES (?,?,?,?,?,?,?,?,?)", node_rows)
    conn.executemany(
        "INSERT INTO knowledge_search_feature (subject, search_feature, knowledge_node_id)"
        " VALUES (?,?,?)", feature_rows)
    conn.commit()
    return conn


def measure(conn: sqlite3.Connection, subject: str, samples: int) -> dict:
    """逐次计时「三笔固定读数」的整体耗时（毫秒）。首次不计（预热页缓存）。

    三条 SQL 与生产一致：`SQL_READ_VERSION` / `SQL_COUNT_REVIEWED` / `SQL_COUNT_INDEXED`
    的原文逐字见下方各 execute 调用（与 Kotlin DAO 的 @Query 一致）。
    """
    timings: list[float] = []
    for i in range(samples + 1):
        start = time.perf_counter()
        conn.execute(
            "SELECT index_version FROM knowledge_search_index_state WHERE subject = ?",
            (subject,)).fetchone()
        conn.execute(
            "SELECT COUNT(*) FROM knowledge_node WHERE subject = ?"
            " AND verification_status IN ('CURATED','SOURCE_GROUNDED','USER_CONFIRMED')"
            " AND status != 'RETIRED'", (subject,)).fetchone()
        conn.execute(
            "SELECT COUNT(DISTINCT knowledge_node_id) FROM knowledge_search_feature"
            " WHERE subject = ?", (subject,)).fetchone()
        elapsed_ms = (time.perf_counter() - start) * 1000
        if i > 0:
            timings.append(elapsed_ms)
    timings.sort()
    return {
        "samples": len(timings),
        "p50_ms": round(statistics.median(timings), 3),
        "p95_ms": round(timings[min(len(timings) - 1, int(len(timings) * 0.95))], 3),
        "min_ms": round(timings[0], 3),
        "max_ms": round(timings[-1], 3),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--quick", action="store_true", help="小规模冒烟（4×200 节点 / 5 采样）")
    parser.add_argument("--nodes", type=int, default=9000, help="每科节点数（默认 9000）")
    parser.add_argument("--features", type=int, default=24, help="每节点特征数（默认 24）")
    parser.add_argument("--samples", type=int, default=24)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)

    nodes, features, samples = args.nodes, args.features, args.samples
    if args.quick:
        nodes, features, samples = 200, 24, 5

    schema_path = REPO / SCHEMA_REL
    if not schema_path.exists():
        print(f"schema 不在场：{schema_path}", file=sys.stderr)
        return 2
    conn = build(nodes, features)
    assert_matches_schema(conn, schema_path)
    result = measure(conn, "MATH", samples)
    rows = conn.execute("SELECT COUNT(*) FROM knowledge_node").fetchone()[0]
    feats = conn.execute("SELECT COUNT(*) FROM knowledge_search_feature").fetchone()[0]
    conn.close()

    payload = {
        "schema": SCHEMA_REL,
        "nodes_total": rows,
        "features_total": feats,
        "readings_per_recall": 3,
        "queries": ["readVersion", "countReviewedKnowledgeNodesBySubject",
                    "countIndexedKnowledgeNodesBySubject"],
        **result,
    }
    if args.json:
        print(json.dumps(payload, ensure_ascii=False))
    else:
        print(f"schema {payload['schema']}；节点 {rows}；特征 {feats}")
        print(f"三笔固定读数/次：p50 {result['p50_ms']}ms、p95 {result['p95_ms']}ms"
              f"（min {result['min_ms']} / max {result['max_ms']}，n={result['samples']}）")
        print("口径：桌面 SQLite，量级判断用；真机读数以仪器化为准。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
