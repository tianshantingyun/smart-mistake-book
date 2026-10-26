# -*- coding: utf-8 -*-
# ---------------------------------------------------------------------------
# 用途：复算 KB 使用面 R1 验收口径 F1-F5（批次23 新教学语义：每节点材料分配、
#       教学类型优先、无材料明示），与批次22 审计镜像同口径；只读，只打印读数。
# 输入：冻结的批次22 审计镜像 audit_knowledge_read.py（提供 golden / full_recall /
#       bindings_by_node / materials），路径写在本文件 AUDIT 常量
#       （C:/Users/听云/AppData/Local/Temp/audit_knowledge_read.py，
#       sha256 80b053853b997a56c3eb6db3c892ccadc0d5e383db727c2fc52b0f796744337e）。
#       该镜像不在仓库，需自行放置；脚本不写仓库源码。
# 复现口径（对照 docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md）：
#       F1 每节点材料覆盖 >=90%（分母=有绑定材料的节点行，分子=其中获得材料摘要的行）
#       F2 期望节点拿到材料 >=90%（expectedSlug 在 top-5 内且获得材料摘要）
#       F3 误区槽位占比 <=20%（MISCONCEPTION_GUIDE 槽 / 全部被选槽）
#       F4 无材料明示（无材料行数 / 已标注 / 伪造材料段数，期望 0/0/0）
#       F5 预算与形态不变（材料条数/查询=5；渲染 UTF-16 chars <=2000，UTF-8 字节另报）
#       并附 Python 镜像与 SQLite 逐字 DAO SQL 的交叉核对（mismatch 应为 0）。
# 用法：python tools/kb_coverage/retest_usage_r1.py
# 原 sha256：aaf716ab656aa09653d52bf58a92e1004312de40e66e4f2e901dacaf700b8a2c
#       （原文件 11349 B，现存于 C:/Users/听云/AppData/Local/Temp/retest_r1_batch23.py；
#        build/agent-batch23/scripts/retest_r1_batch23.py 经 ls -l 确认不存在）
# 入库说明：仅新增本头注释，其余逻辑零改动。
# ---------------------------------------------------------------------------
"""批次23 的 R1 复测：与批次22 审计 R1 同口径（金标 top-5 + 检索/渲染镜像
+ SQLite 逐字复刻 DAO SQL），只把材料取数换成批次23 的新教学语义
（每节点 1 条：role -> 教学类型 -> title -> material_id）。

只读：不写仓库源码；只打印，供报告引用。
"""
import contextlib
import importlib.util
import io
import json
import sqlite3
import statistics

AUDIT = r"C:/Users/听云/AppData/Local/Temp/audit_knowledge_read.py"

# Load the frozen batch-22 mirror and reuse its globals (retrieval + packs).
spec = importlib.util.spec_from_file_location("audit_mirror", AUDIT)
g = importlib.util.module_from_spec(spec)
buf = io.StringIO()
with contextlib.redirect_stdout(buf):
    spec.loader.exec_module(g)

golden = g.golden
full_recall = g.full_recall
bindings_by_node = g.bindings_by_node

ROLE_RANK = {"PRIMARY": 0, "SUPPORTING": 1}
TEACH_RANK = {
    "CONCEPT_EXPLANATION": 0,
    "METHOD_MODEL": 1,
    "DERIVATION": 2,
    "WORKED_EXAMPLE": 3,
    "REPRESENTATION_GUIDE": 4,
    "MISCONCEPTION_GUIDE": 5,
    "COMPLETE_SOLUTION": 6,
}
NO_MATERIAL_MARKER = "（该节点无材料）"


# ---------------------------------------------------------------- mirror side
def best_for_node(nid):
    """Python mirror of readBestTeachingMaterialPerNode for one node."""
    cands = bindings_by_node.get(nid, [])
    if not cands:
        return None
    return min(
        cands,
        key=lambda x: (
            ROLE_RANK.get(x[1], 2),
            TEACH_RANK.get(x[0]["type"], 7),
            x[0]["title"],
            x[0]["material_id"],
        ),
    )[0]


def new_render(nodes5):
    """Mirror of current RoomTutorToolRunner.knowledgeRead (batch 23):
    boundary take(80); material '|material:' + summary take(60); else explicit mark."""
    lines, picked = [], {}
    for i, n in enumerate(nodes5):
        marker = "K%d" % (i + 1)
        boundary = n["boundary"][:80] if n["boundary"] else None
        m = best_for_node(n["id"])
        if m is not None:
            picked[n["id"]] = m
        material = ("｜材料：" + m["summary"][:60]) if m else "｜" + NO_MATERIAL_MARKER
        lines.append(marker + ". " + n["name"] + (("：" + boundary) if boundary else "") + material)
    header = "知识点候选 %d 个（代号 K1..Kn 本会话有效，写掌握度时 terms 填代号）：" % len(nodes5)
    return header + "\n" + "\n".join(lines), lines, picked


# ---------------------------------------------------------------- SQLite side
def build_sqlite():
    con = sqlite3.connect(":memory:")
    con.execute(
        "CREATE TABLE knowledge_teaching_material ("
        "material_id TEXT PRIMARY KEY, subject TEXT, material_type TEXT, title TEXT, "
        "status TEXT, summaryMarkdown TEXT)"
    )
    con.execute(
        "CREATE TABLE knowledge_teaching_material_node_binding ("
        "material_id TEXT, knowledge_node_id TEXT, role TEXT)"
    )
    rows, binds = [], []
    for m in g.materials:
        rows.append((m["material_id"], m["subject"], m["type"], m["title"], "ACTIVE", m["summary"]))
        for b in m["bindings"]:
            binds.append((m["material_id"], b["knowledgeNodeId"], b["role"]))
    con.executemany("INSERT INTO knowledge_teaching_material VALUES (?,?,?,?,?,?)", rows)
    con.executemany("INSERT INTO knowledge_teaching_material_node_binding VALUES (?,?,?)", binds)
    con.commit()
    return con


con = build_sqlite()


def sql_pick(subject, node_ids):
    """Run the verbatim new DAO SQL; every value is bound, never interpolated.
    Room expands the :knowledgeNodeIds set into five '?' placeholders."""
    assert len(node_ids) == 5
    params = (subject, node_ids[0], node_ids[1], node_ids[2], node_ids[3], node_ids[4], subject)
    rows = con.execute(
        """
        SELECT
          binding.knowledge_node_id AS knowledge_node_id,
          material.material_id AS material_id,
          material.material_type AS material_type,
          material.title AS title,
          material.summaryMarkdown AS summaryMarkdown
        FROM knowledge_teaching_material AS material
        INNER JOIN knowledge_teaching_material_node_binding AS binding
          ON binding.material_id = material.material_id
        WHERE material.subject = ?
          AND material.status != 'RETIRED'
          AND binding.knowledge_node_id IN (?,?,?,?,?)
          AND material.material_id = (
            SELECT m2.material_id
            FROM knowledge_teaching_material AS m2
            INNER JOIN knowledge_teaching_material_node_binding AS b2
              ON b2.material_id = m2.material_id
            WHERE b2.knowledge_node_id = binding.knowledge_node_id
              AND m2.subject = ?
              AND m2.status != 'RETIRED'
            ORDER BY
              CASE b2.role
                WHEN 'PRIMARY' THEN 0
                WHEN 'SUPPORTING' THEN 1
                ELSE 2
              END ASC,
              CASE m2.material_type
                WHEN 'CONCEPT_EXPLANATION' THEN 0
                WHEN 'METHOD_MODEL' THEN 1
                WHEN 'DERIVATION' THEN 2
                WHEN 'WORKED_EXAMPLE' THEN 3
                WHEN 'REPRESENTATION_GUIDE' THEN 4
                WHEN 'MISCONCEPTION_GUIDE' THEN 5
                WHEN 'COMPLETE_SOLUTION' THEN 6
                ELSE 7
              END ASC,
              m2.title ASC,
              m2.material_id ASC
            LIMIT 1
          )
        ORDER BY binding.knowledge_node_id ASC
        """,
        params,
    ).fetchall()
    return {r[0]: dict(material_id=r[1], type=r[2], title=r[3], summary=r[4]) for r in rows}


def pct(a, b):
    return round(100.0 * a / b, 2) if b else None


# ---------------------------------------------------------------- run
rows_total = 0
rows_with_material = 0
py_summary_rows = 0
sql_summary_rows = 0
py_expected = 0
sql_expected = 0
hits = 0
py_type_hist, sql_type_hist = {}, {}
py_chars, py_bytes = [], []
py_counts, sql_counts = [], []
py_missing_rows = py_missing_marked = py_missing_fake_material = 0
mismatch_queries, mismatch_nodes = [], 0
checked_sql_rows = 0

# old (batch-22) semantics, recomputed on the same instrument as a calibration.
base_rows = base_with_mat = base_summary = base_expected = 0
base_types = {}

for case in golden:
    nodes5 = full_recall(case["query"], case["subject"], use_dense=True, limit=512)[:5]
    ids = [n["id"] for n in nodes5]
    subject = case["subject"]
    slug = case["expectedSlug"]
    exp_id = next((nid for nid in ids if nid.endswith(":atomic:" + slug)), None)
    hits += 1 if exp_id else 0

    # old render (batch-22 baseline calibration)
    _, _, old_selected, old_summaries = g.render(nodes5, code=True)
    base_rows += len(nodes5)
    base_with_mat += sum(1 for nid in ids if bindings_by_node.get(nid))
    base_summary += sum(1 for nid in ids if nid in old_summaries)
    if exp_id and exp_id in old_summaries:
        base_expected += 1
    for m in old_selected:
        base_types[m["type"]] = base_types.get(m["type"], 0) + 1

    # new semantics: Python mirror
    text, lines, py_picked = new_render(nodes5)
    py_chars.append(len(text))
    py_bytes.append(len(text.encode("utf-8")))
    py_counts.append(len(py_picked))
    rows_total += len(nodes5)
    rows_with_material += sum(1 for nid in ids if bindings_by_node.get(nid))
    py_summary_rows += len(py_picked)
    if exp_id and exp_id in py_picked:
        py_expected += 1
    for idx, nid in enumerate(ids):
        m = py_picked.get(nid)
        if m is None:
            py_missing_rows += 1
            if NO_MATERIAL_MARKER in lines[idx]:
                py_missing_marked += 1
            if "｜材料：" in lines[idx]:
                py_missing_fake_material += 1
        else:
            py_type_hist[m["type"]] = py_type_hist.get(m["type"], 0) + 1

    # new semantics: SQLite verbatim DAO SQL
    sql_map = sql_pick(subject, ids)
    sql_counts.append(len(sql_map))
    sql_summary_rows += len(sql_map)
    if exp_id and exp_id in sql_map:
        sql_expected += 1
    for nid in ids:
        m = sql_map.get(nid)
        if m:
            sql_type_hist[m["type"]] = sql_type_hist.get(m["type"], 0) + 1
    checked_sql_rows += len(sql_map)

    py_ids = {nid: m["material_id"] for nid, m in py_picked.items()}
    sql_ids = {nid: m["material_id"] for nid, m in sql_map.items()}
    if py_ids != sql_ids:
        diff = sorted(set(py_ids.items()) ^ set(sql_ids.items()))
        mismatch_nodes += len(diff)
        mismatch_queries.append((case["query"][:30], diff))

print("=== RETRIEVAL (unchanged gate) ===")
print("FUSED mirror Recall@5", hits, "/", len(golden), "=", round(hits / len(golden), 4))

print("=== BASELINE recalibration (batch-22 old semantics, same instrument) ===")
b_slots = sum(base_types.values())
print("base rows", base_rows, "with_material", base_with_mat, "summary", base_summary,
      "F1", pct(base_summary, base_with_mat),
      "| F2", base_expected, "/", len(golden), pct(base_expected, len(golden)),
      "| F3", base_types.get("MISCONCEPTION_GUIDE", 0), "/", b_slots,
      pct(base_types.get("MISCONCEPTION_GUIDE", 0), b_slots),
      json.dumps(base_types, ensure_ascii=False))

print("=== F1 per-node material coverage (new teaching semantics) ===")
print("rows", rows_total, "rows_with_material", rows_with_material,
      "py_summary_rows", py_summary_rows, "F1", pct(py_summary_rows, rows_with_material),
      "| sql_summary_rows", sql_summary_rows, "F1_sql", pct(sql_summary_rows, rows_with_material))

print("=== F2 expected node got material ===")
print("py", py_expected, "/", len(golden), pct(py_expected, len(golden)),
      "| sql", sql_expected, "/", len(golden), pct(sql_expected, len(golden)),
      "| recall hits", hits)

print("=== F3 misconception slot share ===")
tot_py = sum(py_type_hist.values())
tot_sql = sum(sql_type_hist.values())
print("py slots", tot_py, json.dumps(py_type_hist, ensure_ascii=False),
      "misconception", py_type_hist.get("MISCONCEPTION_GUIDE", 0),
      "F3", pct(py_type_hist.get("MISCONCEPTION_GUIDE", 0), tot_py))
print("sql slots", tot_sql, json.dumps(sql_type_hist, ensure_ascii=False),
      "misconception", sql_type_hist.get("MISCONCEPTION_GUIDE", 0),
      "F3_sql", pct(sql_type_hist.get("MISCONCEPTION_GUIDE", 0), tot_sql))

print("=== F4 no-material explicit marker (gold) ===")
print("no_material_rows", py_missing_rows, "marked", py_missing_marked,
      "fake_material_segments", py_missing_fake_material)

print("=== F5 material rows per query / render chars ===")
print("rows/query py", json.dumps({k: py_counts.count(k) for k in sorted(set(py_counts))}))
print("rows/query sql", json.dumps({k: sql_counts.count(k) for k in sorted(set(sql_counts))}))
print("render chars(UTF-16) min/median/max", min(py_chars), statistics.median(py_chars), max(py_chars),
      "| max pct of 2000 chars", round(100.0 * max(py_chars) / 2000, 1))
print("render utf8 bytes min/median/max", min(py_bytes), statistics.median(py_bytes), max(py_bytes),
      "| max pct of 2000 bytes", round(100.0 * max(py_bytes) / 2000, 1))

print("=== SQL vs Python mirror cross-check ===")
print("checked_sql_rows", checked_sql_rows, "mismatch_queries", len(mismatch_queries),
      "mismatch_nodes", mismatch_nodes)
for q, diff in mismatch_queries[:5]:
    print("  MISMATCH", q, diff)
