#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KB B35 查询类型路由 v2 装置脚本（唯一测量路径）。

契约源：docs/research/2026-11-06-kb-b35-routing-prereg.md（下称 b35-routing §3；写死），
上游 docs/research/2026-11-03-kb-b34-routing-prereg-v2.md（§5 收编）。

CLI（b35-routing §3.2，冻结）：
  python -I tools/kb_coverage/routing_eval_v2.py --self-check
  python -I tools/kb_coverage/routing_eval_v2.py --static  --out build/agent-batch35/routing/static.json
  python -I tools/kb_coverage/routing_eval_v2.py --device  <device.tsv> --out build/agent-batch35/routing/device.json
  python -I tools/kb_coverage/routing_eval_v2.py --report  <static.json> <device.json> --out build/agent-batch35/routing/report.json

退出码：0 = 本模式的机械门全过；1 = 断言失败（ABORT/FAIL/NOT_JUDGEABLE）；
2 = 用法/路径安全越界（§3.1：全部 CLI 路径必须规范化 + 限仓库内）。

口径（全部冻结，脚本不出采纳判词，采纳由执行轮对照 b35-routing §4 落笔）：
- 词表/秩表/冻结下标/冻结哈希 = b35-routing §0.3/§1.2/§1.3/§2.2（本脚本 --self-check 逐值复现）。
- 分类器：lowercase → 每词子串包含、每查每词计 1 → 每类得分 = 命中 distinct 词条数 →
  严格最大；0 命中或并列 → None（UNCLASSIFIED）。
- 匹配率（§2.3）：分母 = 分类查询中有材料的槽位（静态=86 个期望节点）；
  preferred_available_type = 该节点现有材料类型中**该查询类秩表**秩最小者（identity 列与
  routed 列用同一个 preferred）；match = 被选材料类型 == preferred；Δ = routed − identity。
- 选材镜像（§3.3 --static/--device）：SQLite 内存库装侧车材料/绑定平面，逐字执行
  `KnowledgeTeachingMaterialDao` 的 identity SQL 与 ranked SQL（ranked 只把 7 个类型 CASE
  字面量换成 :r0..:r6 绑定参数；IN 列表按节点数展开，本脚本全部单节点调用）。
- material_id 的包侧代理键 = 侧车 slug（b35-routing §3.3 冻结；仅作同 title 时末位
  tiebreaker）。与设备对拍（Z1）时把选中 slug 映射为设备端确定性 id
  `kb-material:<sha256("<packId>:<subject小写>:teaching:<slug>")[:40]>`
  （来源 `ReviewedTeachingMaterialSidecarJsonCodec`：stableCode/materialId 推导）；
  侧车 slug 无重复（--self-check 断言），两种键的 15 条变化下标已核实一致（执行轮记录）。
- device.tsv 契约（b35-routing §3.3，b34-routing §3.3 原文）：每行一个 (query, slot)，列 =
  query_id  chapter  expectedSlug  slot  node_id  identity_material_id  identity_type
  routed_material_id  routed_type  rendered_identity_sha256  rendered_routed_sha256；
  query_id = 金标 0 基下标；空材料 = 空串。

装置失败不得改本文件的冻结值；按 b35-routing §3.0/§4.1 记录后按原配置重跑。
"""

import argparse
import hashlib
import json
import os
import re
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

# ---------------------------------------------------------------------------
# 冻结常量（b35-routing §0.3/§1.2/§1.3/§2.2/§2.3；不得在本文件内改动来"凑绿"）
# ---------------------------------------------------------------------------

GOLDEN_SHA256 = "eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102"
MAIN_PACK_SHA256 = "10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670"
MANIFEST_VERSION = 28
MANIFEST_CONTENT_VERSION = "ce8edb8f89d91fe7"
SIDECAR_COUNT = 26  # 25 × moe-2025-teaching-support-v2-<NN>.json + 1 × moe-2020-teaching-support-v1.json
SIDECAR_2025_PATTERN = re.compile(r"^moe-2025-teaching-support-v2-\d\d\.json$")
SIDECAR_2020_NAME = "moe-2020-teaching-support-v1.json"
TOTAL_BINDINGS = 64_857  # b35-routing §0.3：2025 64,853 + 2020 4

WORD_LIST = {
    "CONCEPT": [
        "为什么", "是什么", "什么区别", "区别", "概念", "含义", "定义", "原理", "原因",
        "作用", "属于", "称为", "叫作", "本质", "判断", "是否", "能不能", "对不对",
        "一回事", "关系",
    ],
    "METHOD": [
        "方法", "步骤", "怎么写", "怎么求", "怎么判断", "怎么算", "如何", "怎样", "怎么做",
        "几种", "哪几种", "画法", "写法", "证明", "构造", "换元", "配方", "待定系数",
        "归纳", "分类讨论", "数形结合", "解法",
    ],
    "COMPUTATION": [
        "计算", "求值", "多少", "质量比", "物质的量", "浓度", "体积", "面积", "长度",
        "角度", "数值", "结果", "等于", "算出", "取值", "最值", "概率", "比例",
    ],
}
WORD_LIST_SHA256 = "ed21d6d26831fb22b688b93465a75e606a4eed686312e6ccd77f8897522a4c15"
WORD_LIST_COUNTS = {"CONCEPT": 20, "METHOD": 22, "COMPUTATION": 18}

FROZEN_DISTRIBUTION = {"CONCEPT": 67, "METHOD": 7, "COMPUTATION": 12, "UNCLASSIFIED": 43}
FROZEN_TIE = [86, 106, 112, 113, 121, 123]
FROZEN_ZERO_HIT = [
    0, 2, 3, 6, 9, 15, 23, 26, 35, 37, 44, 45, 46, 47, 51, 52, 58, 59, 60, 61, 64,
    66, 70, 71, 76, 80, 82, 83, 93, 99, 105, 107, 109, 114, 116, 119, 124,
]
UNCLASSIFIED_INDEX_SHA256 = "099d21ab10b6899a06a0dbc22698229a63e59ec650fad54cdafe404957f73e37"

# 类型 CASE 顺序（KnowledgeTeachingMaterialDao.readBestTeachingMaterialPerNode 的 CASE 字面量序）
CASE_ORDER = [
    "CONCEPT_EXPLANATION",
    "METHOD_MODEL",
    "DERIVATION",
    "WORKED_EXAMPLE",
    "REPRESENTATION_GUIDE",
    "MISCONCEPTION_GUIDE",
    "COMPLETE_SOLUTION",
]
UNKNOWN_TYPE_RANK = 7

# 四张类型秩表（b35-routing §2.2；键 = 查询类；值 = CASE_ORDER 各类型的秩；数字越小越优先）
RANK_TABLES = {
    "UNCLASSIFIED": [0, 1, 2, 3, 4, 5, 6],
    "CONCEPT": [0, 2, 1, 3, 4, 5, 6],
    "METHOD": [3, 0, 2, 1, 4, 5, 6],
    "COMPUTATION": [4, 1, 2, 0, 5, 6, 3],
}
QUERY_TYPES = ["CONCEPT", "METHOD", "COMPUTATION"]

ROLE_RANK = {"PRIMARY": 0, "SUPPORTING": 1}  # 其余（含 PREREQUISITE）= 2（SQL CASE ELSE 2）

FROZEN_IDENTITY_MATCH = 71
FROZEN_ROUTED_MATCH = 86
FROZEN_WITH_MATERIAL = 86
FROZEN_CHANGED = [7, 16, 21, 33, 50, 69, 72, 78, 85, 88, 101, 102, 122, 127, 128]
CHANGED_SHA256 = "ca0acf43e3a2adafb4e60dbee7ab6af45ccd30cad127465549cc54d1a0fb49c4"
FROZEN_TRANSITIONS = {"CONCEPT_EXPLANATION->METHOD_MODEL": 15}

GOLDEN_REPO_DIR = "tools/kb_coverage/tables"
GOLDEN_FILE_NAME = "golden_queries_v2.json"
KNOWLEDGE_RES_DIR = "core/data/src/main/resources/knowledge"
MAIN_PACK_NAME = "moe-2025-four-subjects-v1.json"
MANIFEST_NAME = "moe-2025-update-manifest.json"
DEVICE_TSV_COLUMNS = [
    "query_id", "chapter", "expectedSlug", "slot", "node_id",
    "identity_material_id", "identity_type", "routed_material_id", "routed_type",
    "rendered_identity_sha256", "rendered_routed_sha256",
]
MISCONCEPTION_RATE_FLOOR = 0.20  # R4：routed 误区槽位率 ≤ 20%
REPORT_SMOKE_DIR = "smoke"  # 机械自检（合成 tsv）专用输出目录；冻结产物路径不放合成数据

# 逐字 SQL（来源 core/database/.../dao/KnowledgeTeachingMaterialDao.kt readBestTeachingMaterialPerNode；
# ranked 版只把 7 个类型 CASE 字面量换成 :r0..:r6，其余逐字相同。IN 列表由 _best_row_sql 展开。）

_SQL_FROM = """
        SELECT
          binding.knowledge_node_id AS knowledge_node_id,
          material.*
        FROM knowledge_teaching_material AS material
        INNER JOIN knowledge_teaching_material_node_binding AS binding
          ON binding.material_id = material.material_id
        WHERE material.subject = :subject
          AND material.status != 'RETIRED'
          AND binding.knowledge_node_id IN ({node_ids})
          AND material.material_id = (
            SELECT m2.material_id
            FROM knowledge_teaching_material AS m2
            INNER JOIN knowledge_teaching_material_node_binding AS b2
              ON b2.material_id = m2.material_id
            WHERE b2.knowledge_node_id = binding.knowledge_node_id
              AND m2.subject = :subject
              AND m2.status != 'RETIRED'
            ORDER BY
              CASE b2.role
                WHEN 'PRIMARY' THEN 0
                WHEN 'SUPPORTING' THEN 1
                ELSE 2
              END ASC,
              CASE m2.material_type
                WHEN 'CONCEPT_EXPLANATION' THEN {r0}
                WHEN 'METHOD_MODEL' THEN {r1}
                WHEN 'DERIVATION' THEN {r2}
                WHEN 'WORKED_EXAMPLE' THEN {r3}
                WHEN 'REPRESENTATION_GUIDE' THEN {r4}
                WHEN 'MISCONCEPTION_GUIDE' THEN {r5}
                WHEN 'COMPLETE_SOLUTION' THEN {r6}
                ELSE 7
              END ASC,
              m2.title ASC,
              m2.material_id ASC
            LIMIT 1
          )
        ORDER BY binding.knowledge_node_id ASC
"""


def identity_sql(node_ids: int) -> str:
    ranks = RANK_TABLES["UNCLASSIFIED"]
    return _best_row_sql(node_ids, ranks)


def ranked_sql(node_ids: int, query_type: str) -> str:
    return _best_row_sql(node_ids, RANK_TABLES[query_type])


def _best_row_sql(node_ids: int, ranks) -> str:
    placeholders = ",".join(f":k{i}" for i in range(node_ids))
    return _SQL_FROM.format(
        node_ids=placeholders,
        r0=ranks[0], r1=ranks[1], r2=ranks[2], r3=ranks[3], r4=ranks[4], r5=ranks[5], r6=ranks[6],
    )


class GateFailure(Exception):
    """机械断言失败（exit 1）。"""


class UsageError(Exception):
    """用法/路径安全越界（exit 2）。"""


# ---------------------------------------------------------------------------
# 仓库根 / 路径安全（b35-routing §3.1：全部 CLI 路径规范化 + commonpath 限仓库内）
# ---------------------------------------------------------------------------

def repo_root() -> Path:
    root = Path(__file__).resolve().parents[2]
    if not (root / "settings.gradle.kts").is_file():
        raise UsageError(f"脚本不在预期仓库布局内（未找到 settings.gradle.kts）：{root}")
    return root


def safe_path(raw: str, *, must_exist: bool, root: Path) -> Path:
    if not raw:
        raise UsageError("路径参数为空")
    p = Path(raw)
    if not p.is_absolute():
        p = Path.cwd() / p
    try:
        resolved = p.resolve()
        resolved.relative_to(root)
    except ValueError:
        raise UsageError(f"路径越出仓库根，拒绝（§3.1）：{raw}")
    if must_exist and not resolved.is_file():
        raise UsageError(f"输入文件不存在：{raw}")
    return resolved


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


# ---------------------------------------------------------------------------
# 分类器（b35-routing §1.1/§1.2 冻结语义）
# ---------------------------------------------------------------------------

def classify(text):
    """返回 'CONCEPT'/'METHOD'/'COMPUTATION'；None = UNCLASSIFIED（含空值/零命中/并列）。"""
    if text is None or not text.strip():
        return None
    lowered = text.lower()
    scores = {k: sum(1 for w in words if w in lowered) for k, words in WORD_LIST.items()}
    best = max(scores.values())
    if best == 0:
        return None
    winners = [k for k, v in scores.items() if v == best]
    return winners[0] if len(winners) == 1 else None


# ---------------------------------------------------------------------------
# 内容平面装载（26 侧车；材料代理键 = slug）与 SQLite 镜像
# ---------------------------------------------------------------------------

def load_material_plane(root: Path):
    """返回 (materials, bindings, problems)。

    materials: slug -> {pack_id, subject, type, title, device_id}
    bindings: [(slug, knowledge_node_id, role)]
    """
    res_dir = root / KNOWLEDGE_RES_DIR
    sidecars_2025 = sorted(
        f for f in res_dir.iterdir() if SIDECAR_2025_PATTERN.match(f.name)
    )
    sidecar_2020 = res_dir / SIDECAR_2020_NAME
    problems = []
    if len(sidecars_2025) != 25:
        problems.append(f"2025 sidecar 数量 = {len(sidecars_2025)}（期望 25）")
    if not sidecar_2020.is_file():
        problems.append(f"缺少 2020 sidecar：{SIDECAR_2020_NAME}")
    materials = {}
    bindings = []
    for path in sidecars_2025 + [sidecar_2020]:
        with path.open("r", encoding="utf-8") as fh:
            pack = json.load(fh)
        pack_id = pack["packId"]
        for material in pack.get("materials", []):
            slug = material["slug"]
            if slug in materials:
                problems.append(f"侧车 slug 重复：{slug}")
                continue
            stable_code = f"{pack_id}:{material['subject'].lower()}:teaching:{slug}"
            device_id = "kb-material:" + hashlib.sha256(stable_code.encode("utf-8")).hexdigest()[:40]
            materials[slug] = {
                "pack_id": pack_id,
                "subject": material["subject"],
                "type": material["type"],
                "title": material["title"],
                "device_id": device_id,
            }
            for binding in material.get("bindings", []):
                bindings.append((slug, binding["knowledgeNodeId"], binding["role"]))
    return materials, bindings, problems


def build_sqlite_mirror(materials, bindings):
    conn = sqlite3.connect(":memory:")
    conn.execute(
        "CREATE TABLE knowledge_teaching_material ("
        " material_id TEXT PRIMARY KEY, subject TEXT NOT NULL, status TEXT NOT NULL,"
        " material_type TEXT NOT NULL, title TEXT NOT NULL)"
    )
    conn.execute(
        "CREATE TABLE knowledge_teaching_material_node_binding ("
        " material_id TEXT NOT NULL, knowledge_node_id TEXT NOT NULL, role TEXT NOT NULL)"
    )
    conn.executemany(
        "INSERT INTO knowledge_teaching_material VALUES (?,?,?,?,?)",
        [
            (slug, m["subject"], "ACTIVE", m["type"], m["title"])
            for slug, m in materials.items()
        ],
    )
    conn.executemany(
        "INSERT INTO knowledge_teaching_material_node_binding VALUES (?,?,?)",
        bindings,
    )
    return conn


def best_material(conn, subject: str, node_id: str, query_type: str):
    """单节点镜像选材：返回 (slug_proxy, type, title) 或 None（无材料）。"""
    sql = ranked_sql(1, query_type) if query_type in QUERY_TYPES else identity_sql(1)
    row = conn.execute(
        sql,
        {"subject": subject, "k0": node_id},
    ).fetchone()
    if row is None:
        return None
    # 列序：knowledge_node_id, material_id, subject, status, material_type, title
    return {"materialId": row[1], "type": row[4], "title": row[5]}


def available_types(bindings_by_node, node_id: str, materials) -> set:
    return {
        materials[slug]["type"]
        for slug, _role in bindings_by_node.get(node_id, [])
    }


def preferred_available_type(types: set, query_type: str):
    if not types:
        return None
    return min(types, key=lambda t: type_rank(query_type, t))


def type_rank(query_type: str, material_type: str) -> int:
    if material_type not in CASE_ORDER:
        return UNKNOWN_TYPE_RANK
    return RANK_TABLES[query_type][CASE_ORDER.index(material_type)]


# ---------------------------------------------------------------------------
# 金标 / 静态镜像
# ---------------------------------------------------------------------------

def load_golden(root: Path):
    golden_path = root / GOLDEN_REPO_DIR / GOLDEN_FILE_NAME
    if not golden_path.is_file():
        raise GateFailure(f"金标文件不存在：{golden_path}")
    digest = sha256_file(golden_path)
    if digest != GOLDEN_SHA256:
        raise GateFailure(f"金标 sha 不符：{digest}（冻结 {GOLDEN_SHA256}）→ ABORT")
    with golden_path.open("r", encoding="utf-8") as fh:
        queries = json.load(fh)
    if not isinstance(queries, list) or len(queries) != 129:
        raise GateFailure(f"金标条数异常：{len(queries) if isinstance(queries, list) else type(queries)}")
    return queries


def classify_all(queries):
    distribution = {"CONCEPT": 0, "METHOD": 0, "COMPUTATION": 0, "UNCLASSIFIED": 0}
    tie, zero_hit = [], []
    qtypes = []
    for index, query in enumerate(queries):
        qtype = classify(query.get("query"))
        qtypes.append(qtype)
        if qtype is None:
            if _scores_tied(query.get("query")):
                tie.append(index)
            else:
                zero_hit.append(index)
            distribution["UNCLASSIFIED"] += 1
        else:
            distribution[qtype] += 1
    return distribution, tie, zero_hit, qtypes


def _scores_tied(text):
    if text is None or not text.strip():
        return False
    lowered = text.lower()
    scores = {k: sum(1 for w in words if w in lowered) for k, words in WORD_LIST.items()}
    best = max(scores.values())
    if best == 0:
        return False
    return sum(1 for v in scores.values() if v == best) >= 2


def resolve_expected_node(bindings_by_node, expected_slug: str, subject: str):
    """b35-routing §3.3：绑定 knowledgeNodeId 末段 == slug 且含 :<subject小写>: 段；0/多义 → None。"""
    if not expected_slug:
        return None
    marker = f":{subject.lower()}:"
    hits = [
        node_id
        for node_id in bindings_by_node
        if node_id.rsplit(":", 1)[-1] == expected_slug and marker in node_id
    ]
    return hits[0] if len(hits) == 1 else None


def static_mirror(root: Path):
    """跑 --self-check 断言并产出 static.json 载荷（b35-routing §3.4）。"""
    problems = []
    queries = load_golden(root)
    res_dir = root / KNOWLEDGE_RES_DIR
    main_pack = res_dir / MAIN_PACK_NAME
    manifest_path = res_dir / MANIFEST_NAME
    main_sha = sha256_file(main_pack)
    if main_sha != MAIN_PACK_SHA256:
        problems.append(f"主包 sha 不符：{main_sha}")
    with manifest_path.open("r", encoding="utf-8") as fh:
        manifest = json.load(fh)
    if manifest.get("version") != MANIFEST_VERSION or manifest.get("contentVersion") != MANIFEST_CONTENT_VERSION:
        problems.append(
            f"manifest 身份不符：{manifest.get('version')}/{manifest.get('contentVersion')}"
        )

    word_blob = json.dumps(WORD_LIST, ensure_ascii=False, separators=(",", ":"))
    word_sha = sha256_text(word_blob)
    if word_sha != WORD_LIST_SHA256:
        problems.append(f"词表哈希不符：{word_sha}")
    for key, expected in WORD_LIST_COUNTS.items():
        if len(WORD_LIST[key]) != expected:
            problems.append(f"词表 {key} 词数 = {len(WORD_LIST[key])}（期望 {expected}）")
    all_words = [w for words in WORD_LIST.values() for w in words]
    if len(all_words) != len(set(all_words)):
        problems.append("词表存在跨类重复词条")
    short_words = [w for w in all_words if len(w) < 2]
    if short_words:
        problems.append(f"词表存在 <2 字词条：{short_words}")

    distribution, tie, zero_hit, qtypes = classify_all(queries)
    if distribution != FROZEN_DISTRIBUTION:
        problems.append(f"分类分布不符：{distribution}")
    if tie != FROZEN_TIE:
        problems.append(f"tie 下标不符：{tie}")
    if zero_hit != FROZEN_ZERO_HIT:
        problems.append(f"零命中下标不符：{zero_hit}")
    u_sha = sha256_text(",".join(str(i) for i in tie + zero_hit))
    if u_sha != UNCLASSIFIED_INDEX_SHA256:
        problems.append(f"43 条 UNCLASSIFIED 下标哈希不符：{u_sha}")

    materials, bindings, plane_problems = load_material_plane(root)
    problems.extend(plane_problems)
    binding_total = len(bindings)
    if binding_total != TOTAL_BINDINGS:
        problems.append(f"绑定总数 = {binding_total}（冻结 {TOTAL_BINDINGS}）")

    bindings_by_node = defaultdict(list)
    for slug, node_id, role in bindings:
        bindings_by_node[node_id].append((slug, role))

    conn = build_sqlite_mirror(materials, bindings)
    per_query = []
    classified = 0
    with_material = 0
    identity_match = 0
    routed_match = 0
    changed = []
    transitions = defaultdict(int)
    unclassified_total = 0
    unclassified_equal = 0
    for index, query in enumerate(queries):
        subject = query["subject"]
        qtype = qtypes[index]
        node_id = resolve_expected_node(bindings_by_node, query.get("expectedSlug", ""), subject)
        entry = {
            "index": index,
            "subject": subject,
            "chapter": query.get("chapter"),
            "expectedSlug": query.get("expectedSlug"),
            "qtype": qtype if qtype is not None else "UNCLASSIFIED",
            "nodeId": node_id,
            "nCandidates": len(bindings_by_node.get(node_id, [])) if node_id else 0,
            "identity": None,
            "routed": None,
            "identityMatch": None,
            "routedMatch": None,
        }
        if node_id is None:
            per_query.append(entry)
            continue
        identity = best_material(conn, subject, node_id, "UNCLASSIFIED")
        routed = identity if qtype is None else best_material(conn, subject, node_id, qtype)
        entry["identity"] = identity
        entry["routed"] = routed
        if qtype is None:
            unclassified_total += 1
            same = (
                identity is not None
                and routed is not None
                and identity["materialId"] == routed["materialId"]
                and identity["type"] == routed["type"]
            )
            if same:
                unclassified_equal += 1
            per_query.append(entry)
            continue
        if identity is None or routed is None:
            per_query.append(entry)
            continue
        classified += 1
        types = available_types(bindings_by_node, node_id, materials)
        preferred = preferred_available_type(types, qtype)
        entry["identityMatch"] = identity["type"] == preferred
        entry["routedMatch"] = routed["type"] == preferred
        if entry["identityMatch"]:
            identity_match += 1
        if entry["routedMatch"]:
            routed_match += 1
        if identity["materialId"] != routed["materialId"] or identity["type"] != routed["type"]:
            changed.append(index)
            transitions[f"{identity['type']}->{routed['type']}"] += 1
        with_material += 1
        per_query.append(entry)
    conn.close()

    if classified != FROZEN_WITH_MATERIAL:
        problems.append(f"有材料的分类查询 = {classified}（冻结 {FROZEN_WITH_MATERIAL}）")
    if identity_match != FROZEN_IDENTITY_MATCH:
        problems.append(f"identity 匹配 = {identity_match}（冻结 {FROZEN_IDENTITY_MATCH}）")
    if routed_match != FROZEN_ROUTED_MATCH:
        problems.append(f"routed 匹配 = {routed_match}（冻结 {FROZEN_ROUTED_MATCH}）")
    if changed != FROZEN_CHANGED:
        problems.append(f"变化下标不符：{changed}")
    changed_sha = sha256_text(",".join(str(i) for i in changed))
    if changed_sha != CHANGED_SHA256:
        problems.append(f"变化下标哈希不符：{changed_sha}")
    if unclassified_total != FROZEN_DISTRIBUTION["UNCLASSIFIED"] or unclassified_equal != unclassified_total:
        problems.append(
            f"UNCLASSIFIED 静态零变化：{unclassified_equal}/{unclassified_total}（要求 {FROZEN_DISTRIBUTION['UNCLASSIFIED']}/{FROZEN_DISTRIBUTION['UNCLASSIFIED']}）"
        )

    payload = {
        "script": "tools/kb_coverage/routing_eval_v2.py",
        "sqliteVersion": sqlite3.sqlite_version,
        "goldenSha256": GOLDEN_SHA256,
        "mainPackSha256": main_sha,
        "manifestVersion": MANIFEST_VERSION,
        "contentVersion": MANIFEST_CONTENT_VERSION,
        "sidecars": SIDECAR_COUNT,
        "bindings": binding_total,
        "wordListSha256": word_sha,
        "distribution": distribution,
        "tie": tie,
        "zeroHit": zero_hit,
        "unclassifiedIndexSha256": u_sha,
        "classified": classified,
        "withMaterial": with_material,
        "identityMatch": identity_match,
        "routedMatch": routed_match,
        "identityMatchRate": round(identity_match / classified, 6) if classified else None,
        "routedMatchRate": round(routed_match / classified, 6) if classified else None,
        "changed": changed,
        "changedSha256": changed_sha,
        "changedTransitions": dict(transitions),
        "unclassifiedZeroChange": {"total": unclassified_total, "equal": unclassified_equal},
        "materialIdProxy": "sidecar slug（仅作同 title 末位 tiebreaker；b35-routing §3.3 登记）",
        "perQuery": per_query,
    }
    return payload, problems


# ---------------------------------------------------------------------------
# --device：device.tsv 结构校验 + 全槽镜像 + 匹配率 + F1/F2/F3 + 章表
# ---------------------------------------------------------------------------

def _cell(row, key):
    value = row.get(key, "")
    return value if value not in ("", "null", "NULL") else None


def parse_device_tsv(path: Path):
    with path.open("r", encoding="utf-8-sig", newline="") as fh:
        lines = [line.rstrip("\n").rstrip("\r") for line in fh if line.strip()]
    if not lines:
        raise GateFailure("device.tsv 为空")
    rows = []
    start = 0
    if lines[0].split("\t")[0].strip() == "query_id":
        start = 1
    for line_no, line in enumerate(lines[start:], start=start + 1):
        parts = line.split("\t")
        if len(parts) != len(DEVICE_TSV_COLUMNS):
            raise GateFailure(
                f"device.tsv 第 {line_no} 行列数 = {len(parts)}（期望 {len(DEVICE_TSV_COLUMNS)}）"
            )
        rows.append(dict(zip(DEVICE_TSV_COLUMNS, (p.strip() for p in parts))))
    return rows


def device_mirror(root: Path, tsv: Path):
    problems = []
    queries = load_golden(root)
    materials, bindings, plane_problems = load_material_plane(root)
    problems.extend(plane_problems)
    bindings_by_node = defaultdict(list)
    for slug, node_id, role in bindings:
        bindings_by_node[node_id].append((slug, role))
    conn = build_sqlite_mirror(materials, bindings)

    rows = parse_device_tsv(tsv)
    by_query = defaultdict(dict)
    seen_pairs = set()
    duplicate_pairs = 0
    for row in rows:
        try:
            qid = int(row["query_id"])
            slot = int(row["slot"])
        except ValueError:
            raise GateFailure(f"device.tsv query_id/slot 非整数：{row['query_id']}/{row['slot']}")
        if not (0 <= qid < len(queries)):
            raise GateFailure(f"device.tsv query_id 越界：{qid}")
        if (qid, slot) in seen_pairs:
            duplicate_pairs += 1
        seen_pairs.add((qid, slot))
        by_query[qid][slot] = row
    structure_problems = []
    if duplicate_pairs:
        structure_problems.append(f"重复 (query,slot) 对：{duplicate_pairs}")
    missing = [q for q in range(len(queries)) if len(by_query.get(q, {})) != 5]
    if missing:
        structure_problems.append(f"未凑满 5 槽的 query_id：{missing[:20]}{'…' if len(missing) > 20 else ''}")
    if len(rows) != 5 * len(queries):
        structure_problems.append(f"总行数 = {len(rows)}（期望 {5 * len(queries)}）")
    for qid, slots in sorted(by_query.items()):
        if len(set(slots)) != 5:
            structure_problems.append(f"query {qid} 槽位重复")
        for slot, row in slots.items():
            if row["chapter"] != queries[qid].get("chapter") or row["expectedSlug"] != queries[qid].get("expectedSlug"):
                structure_problems.append(f"query {qid} slot {slot} chapter/expectedSlug 与金标不符")
                break

    _, _, _, qtypes = classify_all(queries)

    mirror_slots = 0
    mismatch_identity_id = 0
    mismatch_identity_type = 0
    mismatch_routed_id = 0
    mismatch_routed_type = 0
    mismatch_details = []
    zero_violations = []
    denominator = 0
    identity_matches = 0
    routed_matches = 0
    per_class = {q: {"n": 0, "denominator": 0, "identityMatch": 0, "routedMatch": 0} for q in QUERY_TYPES}
    f1_identity = 0
    f1_routed = 0
    f2_identity = 0
    f2_routed = 0
    f3_identity = 0
    f3_routed = 0
    chapters = defaultdict(lambda: {"total": 0, "identityHits": 0, "routedHits": 0})

    for qid in range(len(queries)):
        query = queries[qid]
        subject = query["subject"]
        qtype = qtypes[qid]
        chapter = query.get("chapter")
        chapters[chapter]["total"] += 1
        expected_slug = query.get("expectedSlug")
        for slot, row in sorted(by_query.get(qid, {}).items()):
            node_id = _cell(row, "node_id")
            dev_identity_id = _cell(row, "identity_material_id")
            dev_identity_type = _cell(row, "identity_type")
            dev_routed_id = _cell(row, "routed_material_id")
            dev_routed_type = _cell(row, "routed_type")
            rendered_i = _cell(row, "rendered_identity_sha256")
            rendered_r = _cell(row, "rendered_routed_sha256")
            has_material = bool(dev_identity_id) or bool(dev_routed_id)
            if dev_identity_id:
                f1_identity += 1
            if dev_routed_id:
                f1_routed += 1
            if dev_identity_type == "MISCONCEPTION_GUIDE":
                f3_identity += 1
            if dev_routed_type == "MISCONCEPTION_GUIDE":
                f3_routed += 1
            expected_node_present = bool(node_id) and expected_slug and node_id.rsplit(":", 1)[-1] == expected_slug
            if expected_node_present and dev_identity_id:
                f2_identity += 1
            if expected_node_present and dev_routed_id:
                f2_routed += 1
            if expected_node_present:
                chapters[chapter]["identityHits"] += 1
                chapters[chapter]["routedHits"] += 1

            # 全槽镜像：包数据 + 导出 node_id 独立复算（b35-routing §3.3）
            mir_identity = None
            mir_routed = None
            preferred = None
            if node_id:
                mir_identity = best_material(conn, subject, node_id, "UNCLASSIFIED")
                mir_routed = (
                    mir_identity
                    if qtype is None
                    else best_material(conn, subject, node_id, qtype)
                )
                if qtype is not None:
                    preferred = preferred_available_type(
                        available_types(bindings_by_node, node_id, materials), qtype
                    )
            mirror_slots += 1

            def _cmp(dev_id, dev_type, mir, side):
                nonlocal mismatch_identity_id, mismatch_identity_type, mismatch_routed_id, mismatch_routed_type
                mir_dev_id = materials[mir["materialId"]]["device_id"] if mir else None
                mir_type = mir["type"] if mir else None
                id_bad = dev_id != mir_dev_id
                type_bad = dev_type != mir_type
                if side == "identity":
                    mismatch_identity_id += int(id_bad)
                    mismatch_identity_type += int(type_bad)
                else:
                    mismatch_routed_id += int(id_bad)
                    mismatch_routed_type += int(type_bad)
                if (id_bad or type_bad) and len(mismatch_details) < 40:
                    mismatch_details.append(
                        {
                            "query_id": qid, "slot": slot, "column": side,
                            "device": {"materialId": dev_id, "type": dev_type},
                            "mirror": {"materialId": mir_dev_id, "type": mir_type},
                            "nodeId": node_id,
                        }
                    )

            _cmp(dev_identity_id, dev_identity_type, mir_identity, "identity")
            _cmp(dev_routed_id, dev_routed_type, mir_routed, "routed")

            if qtype is None:
                same = (
                    dev_identity_id == dev_routed_id
                    and dev_identity_type == dev_routed_type
                    and rendered_i == rendered_r
                )
                if not same:
                    zero_violations.append(
                        {
                            "query_id": qid, "slot": slot,
                            "identity_material_id": dev_identity_id,
                            "routed_material_id": dev_routed_id,
                            "identity_type": dev_identity_type,
                            "routed_type": dev_routed_type,
                            "rendered_identity_sha256": rendered_i,
                            "rendered_routed_sha256": rendered_r,
                        }
                    )
                continue

            if not has_material:
                continue
            denominator += 1
            per_class[qtype]["n"] += 1
            per_class[qtype]["denominator"] += 1
            if dev_identity_type is not None and preferred is not None and dev_identity_type == preferred:
                identity_matches += 1
                per_class[qtype]["identityMatch"] += 1
            if dev_routed_type is not None and preferred is not None and dev_routed_type == preferred:
                routed_matches += 1
                per_class[qtype]["routedMatch"] += 1
    conn.close()
    problems.extend(structure_problems)

    total_slots = len(queries) * 5
    unclassified_total = FROZEN_DISTRIBUTION["UNCLASSIFIED"]
    payload = {
        "script": "tools/kb_coverage/routing_eval_v2.py",
        "sqliteVersion": sqlite3.sqlite_version,
        "deviceTsv": str(tsv),
        "rows": len(rows),
        "queries": len(queries),
        "structure": {
            "ok": not structure_problems,
            "problems": structure_problems,
        },
        "mirror": {
            "slots": mirror_slots,
            "mismatches": {
                "identityId": mismatch_identity_id,
                "identityType": mismatch_identity_type,
                "routedId": mismatch_routed_id,
                "routedType": mismatch_routed_type,
                "total": (
                    mismatch_identity_id + mismatch_identity_type
                    + mismatch_routed_id + mismatch_routed_type
                ),
            },
            "details": mismatch_details,
            "note": "mirror materialId = 侧车 slug 映射的设备端确定性 id（kb-material:sha256(stableCode)[:40]）",
        },
        "unclassifiedZeroChange": {
            "queries": unclassified_total,
            "violations": zero_violations,
            "equalQueries": unclassified_total - len({v["query_id"] for v in zero_violations}),
            "ok": not zero_violations,
        },
        "matchRate": {
            "denominator": denominator,
            "identityMatch": identity_matches,
            "routedMatch": routed_matches,
            "identityRate": round(identity_matches / denominator, 6) if denominator else None,
            "routedRate": round(routed_matches / denominator, 6) if denominator else None,
            "perClass": {
                q: {
                    **per_class[q],
                    "identityRate": (
                        round(per_class[q]["identityMatch"] / per_class[q]["denominator"], 6)
                        if per_class[q]["denominator"] else None
                    ),
                    "routedRate": (
                        round(per_class[q]["routedMatch"] / per_class[q]["denominator"], 6)
                        if per_class[q]["denominator"] else None
                    ),
                }
                for q in QUERY_TYPES
            },
        },
        "f1": {
            "identity": f1_identity,
            "routed": f1_routed,
            "totalSlots": total_slots,
        },
        "f2": {"identity": f2_identity, "routed": f2_routed, "totalQueries": len(queries)},
        "f3": {
            "identity": f3_identity,
            "routed": f3_routed,
            "totalSlots": total_slots,
            "identityRate": round(f3_identity / total_slots, 6) if total_slots else None,
            "routedRate": round(f3_routed / total_slots, 6) if total_slots else None,
        },
        "chapters": dict(chapters),
    }
    return payload, problems


# ---------------------------------------------------------------------------
# --report：合并判读（机械门判词；采纳判词由执行轮对照 b35-routing §4 落笔）
# ---------------------------------------------------------------------------

def _load_json(path: Path):
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def build_report(static: dict, device: dict, static_problems, device_problems):
    checks = {}
    notes = []

    s1 = {
        "goldenSha256": static.get("goldenSha256") == GOLDEN_SHA256,
        "mainPackSha256": static.get("mainPackSha256") == MAIN_PACK_SHA256,
        "manifest": (
            static.get("manifestVersion") == MANIFEST_VERSION
            and static.get("contentVersion") == MANIFEST_CONTENT_VERSION
        ),
        "sidecars": static.get("sidecars") == SIDECAR_COUNT,
        "ok": not static_problems and not device_problems,
        "problems": static_problems + device_problems,
    }
    checks["S1_identity"] = s1
    checks["S2_wordList"] = {
        "sha256": static.get("wordListSha256") == WORD_LIST_SHA256,
        "ok": static.get("wordListSha256") == WORD_LIST_SHA256,
    }
    s3_ok = (
        static.get("distribution") == FROZEN_DISTRIBUTION
        and static.get("tie") == FROZEN_TIE
        and static.get("zeroHit") == FROZEN_ZERO_HIT
        and static.get("unclassifiedIndexSha256") == UNCLASSIFIED_INDEX_SHA256
    )
    checks["S3_classification"] = {"ok": s3_ok, "distribution": static.get("distribution")}
    s4_ok = (
        static.get("identityMatch") == FROZEN_IDENTITY_MATCH
        and static.get("routedMatch") == FROZEN_ROUTED_MATCH
        and static.get("changed") == FROZEN_CHANGED
        and static.get("changedSha256") == CHANGED_SHA256
    )
    checks["S4_staticExpectation"] = {
        "ok": s4_ok,
        "identityMatch": static.get("identityMatch"),
        "routedMatch": static.get("routedMatch"),
        "changed": static.get("changed"),
    }
    s5 = static.get("unclassifiedZeroChange", {})
    checks["S5_staticZeroChange"] = {
        "ok": s5.get("total") == FROZEN_DISTRIBUTION["UNCLASSIFIED"]
        and s5.get("equal") == s5.get("total") == FROZEN_DISTRIBUTION["UNCLASSIFIED"],
        **s5,
    }

    structure = device.get("structure", {})
    checks["S6_deviceStructure"] = {"ok": bool(structure.get("ok")), "problems": structure.get("problems", [])}

    mirror_ok = device.get("mirror", {}).get("mismatches", {}).get("total") == 0
    checks["Z1_staticDeviceMirror"] = {
        "ok": mirror_ok,
        "mismatches": device.get("mirror", {}).get("mismatches", {}),
    }
    checks["Z2_fusedLexicalSymdiff"] = {
        "status": "NOT_RUN",
        "ok": None,
        "note": "fused/lexical MISS symdiff 由 v1/AC 的 JVM 镜像装置承担；本脚本 CLI 无该输入（b35-routing §3.2 冻结），执行轮另行落账",
    }
    checks["Z3_sameRunTwoColumns"] = {
        "status": "STRUCTURAL_PASS",
        "ok": bool(structure.get("ok")),
        "note": "device.tsv 单 node_id 列结构保证两列同一节点序列；设备用例断言由装置用例承担（b35-routing §4.4）",
    }

    zero = device.get("unclassifiedZeroChange", {})
    r5_ok = bool(zero.get("ok")) and zero.get("queries") == FROZEN_DISTRIBUTION["UNCLASSIFIED"]
    checks["R5_unclassifiedZeroChange"] = {"ok": r5_ok, **zero}

    f1 = device.get("f1", {})
    r2_ok = f1.get("identity") is not None and f1.get("identity") == f1.get("routed")
    checks["R2_f1Coverage"] = {"ok": r2_ok, **f1}

    f3 = device.get("f3", {})
    r4_ok = (
        f3.get("identity") is not None
        and f3.get("routed", 10**9) <= f3.get("identity", 0)
        and (f3.get("routedRate") or 0) <= MISCONCEPTION_RATE_FLOOR
    )
    checks["R4_f3MisconceptionSlots"] = {"ok": r4_ok, "floor": MISCONCEPTION_RATE_FLOOR, **f3}

    rates = device.get("matchRate", {})
    per_class = rates.get("perClass", {})
    delta_overall = None
    if rates.get("identityRate") is not None and rates.get("routedRate") is not None:
        delta_overall = round(rates["routedRate"] - rates["identityRate"], 6)
    class_deltas = {}
    classes_not_down = True
    for q in QUERY_TYPES:
        entry = per_class.get(q, {})
        if entry.get("identityRate") is not None and entry.get("routedRate") is not None:
            class_deltas[q] = round(entry["routedRate"] - entry["identityRate"], 6)
            if entry["routedRate"] < entry["identityRate"]:
                classes_not_down = False
        else:
            class_deltas[q] = None
    f2 = device.get("f2", {})
    # R3：同 run 两列的「期望节点在 top-5 且有材料」查询数一致（单 node_id 列结构 + Z1 镜像承载）。
    r3_ok = f2.get("identity") is not None and f2.get("identity") == f2.get("routed")
    checks["R3_f2ExpectedNodeMaterial"] = {
        "ok": r3_ok,
        **f2,
        "note": "同 run 两列共用同一 node 序列（Z3 结构）；期望节点有材料数由 Z1 镜像一致性与 R2 共同承载",
    }
    checks["S8_deliveryDelta"] = {
        "deltaOverall": delta_overall,
        "perClassDelta": class_deltas,
        "classesNotDown": classes_not_down,
        "matchRate": rates,
    }
    checks["S9_columnsComplete"] = {
        "ok": all(
            k in device and device.get(k) not in (None, {})
            for k in ("f1", "f2", "f3", "chapters", "matchRate")
        ),
        "keys": sorted(device.keys()),
    }

    chapter_rows = []
    chapters = device.get("chapters", {})
    for chapter, row in sorted(chapters.items()):
        chapter_rows.append(
            {
                "chapter": chapter,
                "total": row.get("total"),
                "identityHits": row.get("identityHits"),
                "routedHits": row.get("routedHits"),
                "netChange": (row.get("routedHits", 0) - row.get("identityHits", 0)),
            }
        )
    chapter_loss = [r for r in chapter_rows if r["netChange"] <= -2]
    checks["chapters24"] = {"rows": chapter_rows, "lossChapters": [r["chapter"] for r in chapter_loss]}

    hard_fail = []
    if not s1["ok"]:
        hard_fail.append("S1")
    if not checks["S2_wordList"]["ok"]:
        hard_fail.append("S2")
    if not s3_ok:
        hard_fail.append("S3")
    if not s4_ok:
        hard_fail.append("S4")
    if not checks["S5_staticZeroChange"]["ok"]:
        hard_fail.append("S5")
    if not checks["S6_deviceStructure"]["ok"]:
        hard_fail.append("S6")
    regression = []
    if not r2_ok:
        regression.append("R2")
    if not r3_ok:
        regression.append("R3")
    if not r4_ok:
        regression.append("R4")
    if not r5_ok:
        regression.append("R5")
    if chapter_loss:
        regression.append("chapters24-loss>=2")

    judgeable = True
    not_judgeable_reason = []
    if not mirror_ok:
        judgeable = False
        not_judgeable_reason.append("Z1 mismatch>0")
    if structure.get("problems"):
        judgeable = False
        not_judgeable_reason.append("device.tsv 结构问题")
    if rates.get("denominator") in (None, 0):
        judgeable = False
        not_judgeable_reason.append("匹配率分母为空")
    if delta_overall is None:
        judgeable = False
        not_judgeable_reason.append("Δ_overall 不可计算")

    if hard_fail:
        verdict = "FAIL"
        reason = f"静态/结构门失败：{','.join(hard_fail)}"
    elif not judgeable:
        verdict = "NOT_JUDGEABLE"
        reason = "；".join(not_judgeable_reason) + "（Z2 另行落账）"
    elif regression:
        verdict = "FAIL"
        reason = f"无回归门失败：{','.join(regression)}"
    elif delta_overall > 0 and classes_not_down:
        verdict = "PASS"
        reason = "Δ_overall>0 且逐类不降；R2/R4/R5/Z1 全绿（采纳判词由执行轮对照 §4 落笔）"
    elif delta_overall == 0 and classes_not_down:
        verdict = "KEEP_UNCHANGED"
        reason = "Δ_overall=0 且无回归 → b35-routing §4.3「交付层类型加权无证明收益」结案口径"
    else:
        verdict = "FAIL"
        reason = "逐类下降（局部回归）"
    if checks["Z2_fusedLexicalSymdiff"]["status"] == "NOT_RUN" and verdict == "PASS":
        notes.append("Z2 未跑：NOT_RUN 不阻断本判词输出，但按 §4.4「任一门非 0 → 不判读」，采纳前执行轮必须落账 Z2=0")

    report = {
        "script": "tools/kb_coverage/routing_eval_v2.py",
        "sqliteVersion": sqlite3.sqlite_version,
        "verdict": verdict,
        "reason": reason,
        "exitHint": 0 if verdict in ("PASS", "KEEP_UNCHANGED") else 1,
        "checks": checks,
        "delta": {"overall": delta_overall, "perClass": class_deltas},
        "f1": f1,
        "f2": device.get("f2", {}),
        "f3": f3,
        "chapters24": chapter_rows,
        "notes": notes,
    }
    return report


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=1)
        fh.write("\n")


def _print_checks(problems) -> None:
    if problems:
        for p in problems:
            print(f"[FAIL] {p}")
    else:
        print("[OK] 全部冻结值逐值复现")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        description="KB B35 查询类型路由 v2 装置脚本（b35-routing §3；只出机械断言与计数）",
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--self-check", action="store_true", help="输入/词表/分布/静态期望自检（不跑设备）")
    mode.add_argument("--static", action="store_true", help="静态镜像（SQLite 逐字 SQL）")
    mode.add_argument("--device", metavar="DEVICE_TSV", help="设备导出 device.tsv（一次 run）")
    mode.add_argument("--report", nargs=2, metavar=("STATIC_JSON", "DEVICE_JSON"), help="合并判读")
    parser.add_argument("--out", metavar="OUT_JSON", help="输出 json 路径（--static/--device/--report 必填）")
    args = parser.parse_args(argv)

    try:
        root = repo_root()
        if args.static and not args.out:
            raise UsageError("--static 需要 --out")
        if args.device and not args.out:
            raise UsageError("--device 需要 --out")
        if args.report and not args.out:
            raise UsageError("--report 需要 --out")

        if args.self_check:
            _, problems = static_mirror(root)
            _print_checks(problems)
            return 1 if problems else 0

        if args.static:
            out = safe_path(args.out, must_exist=False, root=root)
            payload, problems = static_mirror(root)
            _print_checks(problems)
            _write_json(out, payload)
            print(f"[OUT] {out}")
            return 1 if problems else 0

        if args.device:
            tsv = safe_path(args.device, must_exist=True, root=root)
            out = safe_path(args.out, must_exist=False, root=root)
            payload, problems = device_mirror(root, tsv)
            _print_checks(problems)
            _write_json(out, payload)
            print(f"[OUT] {out}")
            return 1 if problems else 0

        if args.report:
            static_path = safe_path(args.report[0], must_exist=True, root=root)
            device_path = safe_path(args.report[1], must_exist=True, root=root)
            out = safe_path(args.out, must_exist=False, root=root)
            static = _load_json(static_path)
            device = _load_json(device_path)
            static_problems = static.get("perQuery") is None and ["static.json 缺 perQuery（不是本脚本产物）"]
            device_problems = device.get("mirror") is None and ["device.json 缺 mirror（不是本脚本产物）"]
            report = build_report(static, device, static_problems or [], device_problems or [])
            _write_json(out, report)
            print(f"[VERDICT] {report['verdict']}：{report['reason']}")
            print(f"[OUT] {out}")
            return report["exitHint"]
    except (GateFailure, UsageError) as failure:
        print(f"[ABORT exit-{'1' if isinstance(failure, GateFailure) else '2'}] {failure}")
        return 1 if isinstance(failure, GateFailure) else 2
    return 0


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
        sys.stderr.reconfigure(encoding="utf-8", errors="backslashreplace")
    except Exception:  # noqa: BLE001 - 旧解释器无 reconfigure 时退回默认
        pass
    sys.exit(main())
