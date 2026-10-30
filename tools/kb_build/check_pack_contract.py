# -*- coding: utf-8 -*-
"""成品包的"契约镜像"检查 + 表↔包一致性（五张权威表）。

## 为什么要单独写

`:core:data` 的 Kotlin 用例是最权威的契约门，但它依赖 :core:domain / :core:database 能编译；
工作树由两条会话共享，另一条会话改那两个模块时这扇门会临时关上（2026-09-19 实测：
先卡在 `LearningProjector.knowledgeNodeSuccessors`，再卡在 `RoomStudyDatabase` 抽象成员，
最后卡在 Room/KSP 处理 `ProblemOrganizationDao`）。本脚本覆盖
`BundledKnowledgePackResources` 对**主题/节点**字段的全部已知约束 + 表↔包一致性，
让"包本身是否合法"在 Python 侧随时可查——它不是替代 Kotlin 门，而是补上它不可用时的下限。

## 检查项

- slug 唯一、parentSlug 悬空、字段 trim、控制字符（ISO Cc 排除 \\n\\r\\t + 双向控制符）
- 节点 kind 必须落在 App 的 KnowledgeNodeKind 枚举内
- 别名：非空、首项是主名、无重复、长度 ≤256
- 同主题内节点重名
- 五张权威表↔包一致性（见 `check_table_consistency` 的逐表语义）
- F9：`material_bindings` 的欠账三桶（节点缺/空/欠账）≤ 具名基线（见
  `material_binding_gate`；报告型计数 report_only_counts 的进门通道）

检查对象是 `pack_io.work_dir()`（默认 staging）里的包：门校验的是**晋升候选**，
成品目录在晋升前不被任何检查改写。

用法： PYTHONPATH=tools python -m kb_build.check_pack_contract
"""

from __future__ import annotations

import copy
import sys
import unicodedata
from collections import Counter
from pathlib import Path

from kb_build import gen_chapter_table, material_provenance as _prov, new_content as _nc
from kb_build import pack_io, tables, update_manifest
from kb_build import gate as _gate

VALID_KINDS = {"TOPIC", "CONCEPT", "PROCEDURE", "REASONING", "REPRESENTATION",
               "EXPERIMENT", "EXPRESSION"}


def bad_chars(text: str) -> list[str]:
    out = []
    for ch in text:
        if unicodedata.category(ch) == "Cc" and ch not in "\n\r\t":
            out.append(hex(ord(ch)))
        if ch in "\u061c\u200e\u200f" or 0x202A <= ord(ch) <= 0x202E or 0x2066 <= ord(ch) <= 0x2069:
            out.append(hex(ord(ch)))
    return out


# ---------------------------------------------------------------------------
# 契约镜像：codec 会拒的字段形状
# ---------------------------------------------------------------------------


def check_contract(pack: dict) -> tuple[list[str], Counter]:
    """字段形状检查（codec 镜像）。返回 (问题, 统计)。"""
    problems: list[str] = []
    stats = Counter()
    for subject in pack["subjects"]:
        all_slugs = {t["slug"] for t in subject["topics"]}
        seen_topics: set[str] = set()
        for topic in subject["topics"]:
            stats["topics"] += 1
            slug = topic["slug"]
            if slug in seen_topics:
                problems.append(f"主题 slug 重复：{slug}")
            seen_topics.add(slug)
            for field in ("slug", "name", "sourceLocator"):
                value = topic.get(field) or ""
                if bad_chars(value):
                    problems.append(f"[{subject['subject']}] 主题 {slug} 的 {field} 含控制字符")
                if value != value.strip():
                    problems.append(f"[{subject['subject']}] 主题 {slug} 的 {field} 未 trim")
            parent = topic.get("parentSlug")
            # 顺序无关：父只要在同科内存在即可（既有包的"综合"链子先父后也存在）
            if parent and parent not in all_slugs:
                problems.append(f"[{subject['subject']}] 主题 {slug} 的父悬空：{parent}")
            stats["points"] += len(topic.get("knowledgePoints") or [])
            seen_names: dict[str, str] = {}
            for point in topic.get("knowledgePoints") or []:
                if point.get("kind") not in VALID_KINDS:
                    problems.append(f"[{subject['subject']}] {point['slug']} kind 越界：{point.get('kind')}")
                aliases = point.get("aliases") or []
                if len(set(aliases)) != len(aliases):
                    problems.append(f"[{subject['subject']}] {point['slug']} 别名重复")
                if not aliases or aliases[0] != point["name"]:
                    problems.append(f"[{subject['subject']}] {point['slug']} 别名首项不是主名")
                for field in ("slug", "name", "boundary", "sourceLocator"):
                    value = point.get(field) or ""
                    if bad_chars(value):
                        problems.append(f"[{subject['subject']}] {point['slug']} 的 {field} 含控制字符")
                    if value != value.strip():
                        problems.append(f"[{subject['subject']}] {point['slug']} 的 {field} 未 trim")
                if len(point.get("name") or "") > 256:
                    problems.append(f"[{subject['subject']}] {point['slug']} 名称超长")
                for alias in aliases:
                    if len(alias) > 256:
                        problems.append(f"[{subject['subject']}] {point['slug']} 别名超长：{alias[:20]}")
                if point["name"] in seen_names:
                    problems.append(f"[{subject['subject']}] 同主题内重名：{point['name']}")
                seen_names[point["name"]] = point["slug"]
    return problems, stats


# ---------------------------------------------------------------------------
# 表↔包一致性：五张权威表
# ---------------------------------------------------------------------------

def _read_rows(tables_dir: Path, name: str) -> list[dict[str, str]]:
    """读表行。**行尾归一化只有一处口径**：`tables.read_rows`。

    KB25-01：Windows 检出（`* text=auto` + core.autocrlf）把引号内多行别名的
    `\n` 一并转成 `\r\n`，与包侧 `\n` 逐字比较就出现 alias_map×4 伪不一致。
    修的是读入口，不是表、也不是比对口径——表（git 对象）与包本就 0 差异。
    """
    return tables.read_rows(tables_dir, name)


def _alias_map_check(pack: dict, tables_dir: Path) -> list[str]:
    """alias_map 必须与包**逐节点双向**一致：

    表里多出的行 = 别名没写进包（丢了召回）；包里多出的别名 = 表没记账
    （下一次重建别名表时会把现行别名当噪声清掉）。
    历史上就是因为一个读 staging、一个读成品，差点用过期快照覆盖成品别名。
    """
    from_table: dict[tuple[str, str], set[str]] = {}
    for row in _read_rows(tables_dir, "alias_map.csv"):
        from_table.setdefault((row["subject"].strip(), row["slug"].strip()), set()).add(row["alias"].strip())
    problems: list[str] = []
    if not from_table:
        problems.append("别名表为空")
        return problems
    for subject in pack["subjects"]:
        for topic in subject["topics"]:
            for point in topic.get("knowledgePoints") or []:
                extra = set(point.get("aliases") or []) - {point["name"]}
                key = (subject["subject"], point["slug"])
                if from_table.get(key, set()) != extra:
                    problems.append(
                        f"别名表与包不一致：{subject['subject']}/{point['slug']} "
                        f"表 {sorted(from_table.get(key, set()))[:3]} vs 包 {sorted(extra)[:3]}")
    return problems[:10]


def _unit_baseline(point: dict, chapter_table: dict) -> tuple[str, str] | None:
    """节点在"没有 chapter_map 覆盖行"时应有的（册, 章）；None = 无基线要求。

    与 gate 的 chapter_locator_mismatch 同一口径：
    单元不在章表 / 决定是 keep_per_node → 无要求；split → 必须有覆盖行（基线 None）；
    其余（fix/keep）→ 章表声明的（册, 章）。
    """
    unit = gen_chapter_table.source_unit(point.get("sourceLocator", ""))
    entry = chapter_table.get(unit)
    if entry is None or entry["decision"] == "keep_per_node":
        return None
    if entry["decision"] == "split":
        return None  # split 单元一律要求覆盖行，见 _chapter_map_check
    return (entry["book"].strip(), entry["chapter"].strip())


def _chapter_map_check(pack: dict, tables_dir: Path) -> list[str]:
    """chapter_map（节点级章节覆盖）三条语义：

    - 行可在包中定位：(subject, slug) 必须是包里的点；
    - 动作已生效：点的定位串（册, 章）== 行的 (volume, chapter)；
    - 偏离有账：定位串偏离单元基线的点（含 split 单元的全部点）必须有行——
      行被删而定位串还在偏离，就是"表领先/落后于包"的分叉。
    """
    rows = _read_rows(tables_dir, "chapter_map.csv")
    points = {(s, p["slug"]): p for s, _t, p in pack_io.iter_points(pack)}
    chapter_table = tables.load_chapter_by_source()
    problems: list[str] = []
    covered: set[tuple[str, str]] = set()
    for row in rows:
        key = (row["subject"].strip(), row["slug"].strip())
        point = points.get(key)
        if point is None:
            problems.append(f"chapter_map 行在包中不存在：{key[0]}/{key[1]}")
            continue
        covered.add(key)
        loc = _gate._place_of(point.get("boundary") or "")
        want = (row["volume"].strip(), row["chapter"].strip())
        if loc != want:
            problems.append(
                f"chapter_map 未生效：{key[0]}/{key[1]} 定位于 {loc}，行声明 {want}")
    for key, point in points.items():
        baseline = _unit_baseline(point, chapter_table)
        unit = gen_chapter_table.source_unit(point.get("sourceLocator", ""))
        is_split = chapter_table.get(unit, {}).get("decision") == "split"
        loc = _gate._place_of(point.get("boundary") or "")
        needs_row = is_split or (baseline is not None and loc != baseline)
        if needs_row and key not in covered:
            problems.append(
                f"章节偏离无账：{key[0]}/{key[1]} 定位于 {loc}"
                + ("（split 单元必须有覆盖行）" if is_split else f"（基线 {baseline}）"))
    return problems[:20]


def _chapter_by_node_check(pack: dict, tables_dir: Path) -> list[str]:
    """chapter_by_node（split 单元逐节点定章的提案账）两条语义：

    - 行可在包中定位：(subject, slug) 必须是包里的点（节点删/并后行要清，
      否则这张表就变成过期快照——它曾因 stale 行差点反向污染别名表）；
    - 该列的都在：split 单元的每个点都必须有行（gen_chapter_table 的约定：
      "split 单元逐节点细分，列在 chapter_by_node.csv 里"）。
    """
    rows = _read_rows(tables_dir, "chapter_by_node.csv")
    points = {(s, p["slug"]): p for s, _t, p in pack_io.iter_points(pack)}
    chapter_table = tables.load_chapter_by_source()
    problems: list[str] = []
    covered: set[tuple[str, str]] = set()
    for row in rows:
        key = (row["subject"].strip(), row["slug"].strip())
        if key not in points:
            problems.append(f"chapter_by_node 行在包中不存在：{key[0]}/{key[1]}")
            continue
        covered.add(key)
    for key, point in points.items():
        unit = gen_chapter_table.source_unit(point.get("sourceLocator", ""))
        if chapter_table.get(unit, {}).get("decision") == "split" and key not in covered:
            problems.append(f"split 单元缺逐节点行：{key[0]}/{key[1]}（{unit[:40]}）")
    return problems[:20]


def _prereq_map_check(pack: dict, tables_dir: Path) -> list[str]:
    """prereq_map（前置权威表）三条语义，双向等价：

    - 行可在包中定位：point 与 prerequisite 都必须是**同科**包内的点；
    - 动作已生效：边的两端都在，且点的 prerequisiteSlugs 含该前置；
    - 包里有账：包中每条前置边都必须在表里（表里没有 = 未声明，
      与 gate 的 undeclared_prereq 同口径）。
    """
    rows = _read_rows(tables_dir, "prereq_map.csv")
    points = {(s, p["slug"]): p for s, _t, p in pack_io.iter_points(pack)}
    problems: list[str] = []
    table_edges: set[tuple[str, str, str]] = set()
    for row in rows:
        subj, slug, preq = row["subject"].strip(), row["slug"].strip(), row["prerequisite"].strip()
        table_edges.add((subj, slug, preq))
        point = points.get((subj, slug))
        if point is None:
            problems.append(f"prereq_map 点在包中不存在：{subj}/{slug}")
            continue
        if (subj, preq) not in points:
            problems.append(f"prereq_map 前置在包中不存在：{subj}/{slug} ← {preq}")
            continue
        if preq not in (point.get("prerequisiteSlugs") or []):
            problems.append(f"prereq_map 未生效：{subj}/{slug} 的包内前置不含 {preq}")
    for (subj, slug), point in points.items():
        for preq in point.get("prerequisiteSlugs") or []:
            if (subj, slug, preq) not in table_edges:
                problems.append(f"包内前置未声明：{subj}/{slug} ← {preq}")
    return problems[:20]


def _rename_rows(tables_dir: Path) -> dict[tuple[str, str], str]:
    """point_rename：同一 slug 多行时**最后一行**生效（执行器按文件序覆盖，
    与 rename_points.load_renames 的 dict 语义一致）。"""
    out: dict[tuple[str, str], str] = {}
    for row in _read_rows(tables_dir, "point_rename.csv"):
        out[(row["subject"].strip(), row["slug"].strip())] = row["new_name"].strip()
    return out


def _delete_rows(tables_dir: Path) -> dict[tuple[str, str], str]:
    out: dict[tuple[str, str], str] = {}
    for row in _read_rows(tables_dir, "point_delete.csv"):
        out[(row["subject"].strip(), row["slug"].strip())] = row.get("reason", "")
    return out


def _merge_rows(tables_dir: Path) -> set[tuple[str, str, str]]:
    out: set[tuple[str, str, str]] = set()
    for row in _read_rows(tables_dir, "point_merge.csv"):
        out.add((row["subject"].strip(), row["survivor_slug"].strip(), row["merged_slug"].strip()))
    return out


def _point_table_checks(pack: dict, manifest: dict | None, tables_dir: Path) -> dict[str, list[str]]:
    """point_* 四张动作表（rename/delete/merge/relocation）。

    语义（表 = 动作账，包 = 事实源）：
    - point_rename：行可在包中定位（slug 还在）；生效 = 点名 == 该 slug 最后一行的 new_name。
    - point_delete：生效 = slug 已不在包内；台账有账 = 台账里每条 atomic DELETE
      都能找到对应的表行（表行被删而台账还在 = 动作失去溯源）。
    - point_merge：survivor 在包内、merged 已不在（生效）；台账里每条 atomic MERGE
      的 (survivor, merged) 都能找到对应的表行。
    - point_relocation：行可在包中定位；生效 = 点当前所在 topic 的 slug == to_topic_slug。

    台账方向按**晋升形态**对账（`update_manifest.canonicalize`，与 promote 同一变换）：
    schema 1 的链式条目晋升时会被压平到终局，压平后指向 DELETE 底的条目降级为
    DELETE——对账必须用压平后的指向，否则历史形态会被误判成"表无行"。
    """
    points = {(s, p["slug"]): p for s, _t, p in pack_io.iter_points(pack)}
    point_topic = {
        (s, p["slug"]): t["slug"] for s, t, p in pack_io.iter_points(pack)
    }
    out: dict[str, list[str]] = {
        "point_rename": [], "point_delete": [], "point_merge": [], "point_relocation": [],
    }

    for key, want in _rename_rows(tables_dir).items():
        point = points.get(key)
        if point is None:
            out["point_rename"].append(f"point_rename 点在包中不存在：{key[0]}/{key[1]}")
        elif point["name"] != want:
            out["point_rename"].append(
                f"point_rename 未生效：{key[0]}/{key[1]} 现为「{point['name'][:30]}」，表定稿「{want[:30]}」")

    for key in _delete_rows(tables_dir):
        if key in points:
            out["point_delete"].append(f"point_delete 未生效：{key[0]}/{key[1]} 仍在包内")

    for key in _merge_rows(tables_dir):
        subj, survivor, merged = key
        if (subj, survivor) not in points:
            out["point_merge"].append(f"point_merge 幸存者不在包内：{subj}/{survivor} ← {merged}")
        elif (subj, merged) in points:
            out["point_merge"].append(f"point_merge 未生效：{subj}/{merged} 仍在包内")

    for row in _read_rows(tables_dir, "point_relocation.csv"):
        key = (row["subject"].strip(), row["slug"].strip())
        target = row["to_topic_slug"].strip()
        point = points.get(key)
        if point is None:
            out["point_relocation"].append(f"point_relocation 点在包中不存在：{key[0]}/{key[1]}")
        elif point_topic[key] != target:
            out["point_relocation"].append(
                f"point_relocation 未生效：{key[0]}/{key[1]} 在 {point_topic[key][:40]}，表目标 {target[:40]}")

    if manifest is not None:
        # 台账按**晋升形态**对账（与 promote 同一 canonicalize）：schema 1 的链式
        # 条目在晋升时会被压平，对账若用未压平形态会把历史形态误判成"表无行"。
        try:
            doc = update_manifest.canonicalize(copy.deepcopy(manifest))
        except ValueError as exc:
            out["point_merge"].append(f"台账规范化失败：{exc}")
            out["point_delete"].append(f"台账规范化失败：{exc}")
            return _truncate(out)
        for entry in doc.get("retired", []):
            parts = entry["nodeId"].split(":")
            if len(parts) < 5 or parts[-2] != "atomic":
                continue  # topic 退役走 topic 家族账（topic_actions），不在本组
            subj, slug = parts[-3].upper(), parts[-1]
            if entry["kind"] == "DELETE" and (subj, slug) not in _delete_rows(tables_dir):
                out["point_delete"].append(f"台账有账、表无行：DELETE {subj}/{slug}")
            if entry["kind"] == "MERGE":
                tparts = (entry.get("supersededBy") or "").split(":")
                if len(tparts) < 5 or (subj, tparts[-1], slug) not in _merge_rows(tables_dir):
                    out["point_merge"].append(
                        f"台账有账、表无行：MERGE {subj}/{slug} ← {tparts[-1] if len(tparts) >= 5 else '?'}")
    return _truncate(out)


def _three_value_types() -> set[str]:
    """三值白名单的**单一来源** = 写入侧 `kb_coverage.materialize.TYPES`
    （`merge_text_judgments.TYPES` 与它逐字一致，由 test_kb_materialize 钉住）。
    延迟导入：kb_build 不在导入期拖起 kb_coverage 的依赖链。"""
    from kb_coverage import materialize as mz
    return mz.TYPES


def legacy_type_counts() -> dict[str, int]:
    """材料 `type` 的三值外计数（协议 v1.1 只许 CONCEPT_EXPLANATION / METHOD_MODEL /
    MISCONCEPTION_GUIDE）。

    2026-10-11 批8 迁移：存量 4 种旧值 667 条（REPRESENTATION_GUIDE 491 / DERIVATION 150 /
    WORKED_EXAMPLE 22 / COMPLETE_SOLUTION 4）已按裁定 F7 的映射迁到三值，**只改 type 字段**
    （映射、前后计数与只改 type 的证明见 `build/agent-batch8/migration_report.json`）。
    **基线归零 = 硬门**：`LEGACY_TYPE_BASELINE` 只许**递减**，回归钉在
    `tools/tests/test_kb_contract_report_counts.py`（超过基线即红，否则只报数）。
    回流面已核清：`materialize --write` 按 slug 幂等跳过存量材料，且判定表的 47,583 条
    MATERIAL 行（唯一的写入源）已全为三值；作者源 `knowledge-production/wusan/materials.jsonl`
    的 28 条旧 type 同批迁移。
    """
    allowed = _three_value_types()
    counts: dict[str, int] = {}
    for path in pack_io.sidecar_paths():
        for material in pack_io.load_json(path).get("materials") or []:
            t = material.get("type") or ""
            if t not in allowed:
                counts[t] = counts.get(t, 0) + 1
    counts["legacy_total"] = sum(counts.values())
    return counts


LEGACY_TYPE_BASELINE = 0  # 2026-10-11 批8 迁移后归零；只许递减（>0 即红，见 legacy_type_counts 的说明）


def _truncate(out: dict[str, list[str]]) -> dict[str, list[str]]:
    for key in out:
        out[key] = out[key][:20]
    return out


# ---------------------------------------------------------------------------
# 报告型计数：boundary_map 只报数（**不参与红/绿**）；material_bindings 的
# 节点缺/空/欠账三桶经 `material_binding_gate` 进门（F9，见文件下半部）
# ---------------------------------------------------------------------------

def _pack_node_boundaries(pack: dict) -> dict[tuple[str, str], str]:
    out: dict[tuple[str, str], str] = {}
    for subject in pack["subjects"]:
        name = subject["subject"]

        def walk(objs) -> None:
            for topic in objs:
                for point in topic.get("knowledgePoints") or []:
                    out[(name, point["slug"])] = point.get("boundary") or ""
                walk(topic.get("topics") or [])

        walk(subject["topics"])
    return out


def _pack_material_bindings() -> dict[str, set[str]]:
    """材料 slug → 已绑定节点的短名集合（材料卷枚举走 `pack_io.sidecar_paths()`）。"""
    out: dict[str, set[str]] = {}
    for path in pack_io.sidecar_paths():
        for material in pack_io.load_json(path).get("materials") or []:
            shorts = {b["knowledgeNodeId"].rsplit(":", 1)[-1]
                      for b in material.get("bindings") or []}
            out.setdefault(material["slug"], set()).update(shorts)
    return out


def report_only_counts(pack: dict, tables_dir: Path | None = None,
                       bindings: dict[str, set[str]] | None = None) -> dict[str, int]:
    """两张「对外展示为权威、但不在任何对账里」的表的现状计数（只报数，不红不绿）。

    背景：`docs/status.md` 把 6 张表并列展示为权威，其中 boundary_map（20 行 vs 包内
    3,866 条 boundary）与 material_bindings（598 行 vs 50,383 条材料）此前不被任何检查
    读取（详见 docs/kb-outstanding-research-2026-10-02.md ⑥）。本函数把现状量出来，
    由 run_kb_checks 以 INFO 节呈现。**F9 执行口径（2026-10-12 批9 d2）：**
    material_bindings 的节点缺/空/欠账三桶经 `material_binding_gate` 进门（≤ 具名基线，
    清账明细见 build/agent-batch9/d2/bindings-fix.csv）；boundary_map 仍只报数。

    - boundary_map：`boundary_effective` 与包内节点 boundary 逐字相等 /
      `boundary_stale` 不等 / `boundary_missing` 节点不在包内；
    - material_bindings：`mb_effective` 材料在包内且已绑到声明节点 /
      `mb_arrear` 材料在包内但没绑到该节点 / `mb_material_missing` 材料不在包内 /
      `mb_point_missing` 节点 slug 不在包内 / `mb_empty` 两列有空。
    """
    tables_dir = tables_dir or tables.TABLES_DIR
    boundaries = _pack_node_boundaries(pack)
    point_slugs = {slug for _s, slug in boundaries}

    b_rows = _read_rows(tables_dir, "boundary_map.csv")
    b_effective = b_stale = b_missing = 0
    for row in b_rows:
        key = (row.get("subject", ""), row.get("slug", ""))
        if key not in boundaries:
            b_missing += 1
        elif boundaries[key] == (row.get("boundary") or ""):
            b_effective += 1
        else:
            b_stale += 1

    binds = bindings if bindings is not None else _pack_material_bindings()
    m_rows = _read_rows(tables_dir, "material_bindings.csv")
    m_effective = m_arrear = m_material_missing = m_point_missing = m_empty = 0
    for row in m_rows:
        material, point = row.get("material_slug", ""), row.get("point_slug", "")
        if not material or not point:
            m_empty += 1
        elif material not in binds:
            m_material_missing += 1
        elif point not in point_slugs:
            m_point_missing += 1
        elif point in binds[material]:
            m_effective += 1
        else:
            m_arrear += 1

    return {
        "boundary_rows": len(b_rows),
        "boundary_effective": b_effective,
        "boundary_stale": b_stale,
        "boundary_missing": b_missing,
        "mb_rows": len(m_rows),
        "mb_effective": m_effective,
        "mb_arrear": m_arrear,
        "mb_material_missing": m_material_missing,
        "mb_point_missing": m_point_missing,
        "mb_empty": m_empty,
    }


# ---------------------------------------------------------------------------
# F9 对账门：material_bindings 报告型计数 → 门
# ---------------------------------------------------------------------------

# 2026-10-12 批9 d2 清账后的**具名基线**（只许递减，超过即红）。
#   - 清账前（598 行）：节点缺 41 / 空 37 / 欠账 3；
#   - 清账后（598 行）：生效 598、节点缺 0 / 空 0 / 欠账 0。
# 逐条处置（旧值/处置/新值/理由）见 build/agent-batch9/d2/bindings-fix.csv；
# 6 条「应改绑」走 material_rebind.csv + `rebind_materials --write`（staging 已生效，
# 成品包待批10 写入窗口 promote 后对齐——见下方口径说明）。
#
# 口径：对账对象是**工作目录（晋升候选）**——`evaluate()` 拿到的包就是 promote 的
# 候选；两段拓扑下成品包在 promote 前落后于 staging 属预期（D-5），不据此调基线。
# 回归含义：再出现改绑不更新台账、节点删/并不清台账，本门即红。
MATERIAL_BINDING_BASELINE: dict[str, int] = {"point_missing": 0, "empty": 0, "arrear": 0}

_BINDING_GATE_BUCKETS = (
    ("mb_point_missing", "point_missing", "节点缺"),
    ("mb_empty", "empty", "空"),
    ("mb_arrear", "arrear", "欠账"),
)


def material_binding_gate(counts: dict[str, int]) -> list[str]:
    """把 `report_only_counts` 的三桶欠账计数升级为门（F9）。

    返回问题列表；空 = 过。基线在 `MATERIAL_BINDING_BASELINE` 具名登记，只许递减：
    清账后任何一桶回到基线之上（新欠账）都会让 evaluate() / run_kb_checks / promote 判红。
    """
    problems: list[str] = []
    for key, base_key, label in _BINDING_GATE_BUCKETS:
        value, base = counts.get(key, 0), MATERIAL_BINDING_BASELINE[base_key]
        if value > base:
            problems.append(
                f"material_bindings {label} {value} 条 > 基线 {base}"
                f"（清账明细见 build/agent-batch9/d2/bindings-fix.csv）")
    return problems


def check_table_consistency(pack: dict, manifest: dict | None,
                            tables_dir: Path | None = None) -> dict[str, list[str]]:
    """五张权威表↔包一致性。返回 {表名: 问题列表}；全空 = 一致。

    表名：alias_map / chapter_map / chapter_by_node / prereq_map /
    point_rename / point_delete / point_merge / point_relocation
    （point_* 四表记为一组，语义见 `_point_table_checks`）。
    """
    tables_dir = tables_dir or tables.TABLES_DIR
    result: dict[str, list[str]] = {
        "alias_map": _alias_map_check(pack, tables_dir),
        "chapter_map": _chapter_map_check(pack, tables_dir),
        "chapter_by_node": _chapter_by_node_check(pack, tables_dir),
        "prereq_map": _prereq_map_check(pack, tables_dir),
    }
    result.update(_point_table_checks(pack, manifest, tables_dir))
    return result


# ---------------------------------------------------------------------------
# F8 契约镜像：材料侧车的 AI/provenance 键集（codec 的 Python 下限）
# ---------------------------------------------------------------------------


def material_provenance_gate() -> list[str]:
    """材料侧车族 ↔ codec 的材料键集/版本下限（F8）。

    消灭的失败：staging 里某卷写坏 F8 三键（漏补 / 多写 / 取值越界 / contentId 与 slug
    不符）或索引与磁盘对不上 → 本门不红 → promote 全绿 → **装机时 codec 整包拒** →
    主页横幅常驻。codec 对越界是"整包拒、没有单条跳过"，所以下限必须与它同宽。

    口径只与 codec 同宽，不比它更严：v2/v3 卷不因"没升 v4"变红（codec 照收、装机不受
    影响）；v4 卷则必须键齐值正。判据本体在 `kb_build/material_provenance.py`（与写入侧
    共用一份，不在这里抄第二份）。
    """
    return _prov.family_problems(pack_io.work_dir(), base_keys=_nc.MATERIAL_KEYS)


# ---------------------------------------------------------------------------
# 入口
# ---------------------------------------------------------------------------


def evaluate(root: Path | None = None) -> tuple[dict[str, list[str]], Counter]:
    """跑全部检查（契约镜像 + 五表一致性）。root 缺省 = 当前工作目录（staging）。"""
    if root is not None:
        pack_io.use_directory(root)
    pack = pack_io.load_json(pack_io.pack_path())
    manifest = None
    mp = pack_io.work_dir() / update_manifest.MANIFEST_NAME
    if mp.exists():
        manifest = pack_io.load_json(mp)
    problems, stats = check_contract(pack)
    consistency = check_table_consistency(pack, manifest)
    consistency["contract"] = problems
    # F9：material_bindings 的报告型计数进门（与五表一致性同判红/绿、同被 promote 读取）
    consistency["material_bindings"] = material_binding_gate(report_only_counts(pack))
    # F8：材料侧车的 AI/provenance 键集 / 版本门（同上，同被 promote 读取）
    consistency["material_provenance"] = material_provenance_gate()
    return consistency, stats


def main() -> int:
    consistency, stats = evaluate()
    total = sum(len(v) for v in consistency.values())
    print(f"主题 {stats['topics']}、知识点 {stats['points']}；问题 {total}")
    for name in ("contract", "alias_map", "chapter_map", "chapter_by_node", "prereq_map",
                 "point_rename", "point_delete", "point_merge", "point_relocation",
                 "material_bindings", "material_provenance"):
        for p in consistency[name]:
            print(f"  ! [{name}]", p)
    pack = pack_io.load_json(pack_io.pack_path())
    counts = report_only_counts(pack)
    legacy = legacy_type_counts()
    print("报告型计数（boundary_map 只报数；material_bindings 三桶已进门 F9）：")
    print(f"  boundary_map 生效 {counts['boundary_effective']}/{counts['boundary_rows']}"
          f"（stale {counts['boundary_stale']}、missing {counts['boundary_missing']}）")
    _base = MATERIAL_BINDING_BASELINE
    print(f"  material_bindings 生效 {counts['mb_effective']}、欠账 {counts['mb_arrear']}、"
          f"材料缺 {counts['mb_material_missing']}、节点缺 {counts['mb_point_missing']}、"
          f"空 {counts['mb_empty']}（共 {counts['mb_rows']}；"
          f"F9 门 节点缺/空/欠账 ≤ {_base['point_missing']}/{_base['empty']}/{_base['arrear']}）")
    print(f"  三值外材料 type {legacy['legacy_total']}（基线豁免 {LEGACY_TYPE_BASELINE}，只许递减）")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
