#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""备份内容选择清单的**四方一致性**门（批次 41 / WS1 item 7）。

## 它消灭的具体失败

KD-80 类漂移已经出现过三次：`SmbkSelectionPlan.kt`、设计件 §9.14 机器块、
`SmbkSelectionPlanTest` 的 golden、`75.json`（Room/KSP 导出物）任一处单独改动，
其余三处仍能各自"自洽"——单测只比其中两处，于是"清单说 98 表、导出物有 99 表"
可以静默存在到成包/恢复阶段。本脚本把四处钉在一起，任一处不等即非零退出：

    SmbkSelectionPlan.kt 常量/表清单
        == 设计件 §9.14 `6c2-plan-v3` 机器块（parser 契约名沿用 v3，内容随 KSP 同步）
        == SmbkSelectionPlanTest 的空库全选 golden（并按同算法独立复算）
        == core/database/schemas/.../75.json

另外校验 closure 4 的冻结规则表（WS1 item 5）：v4 键集合 = v5 − 错因两表 +
`knowledge_search_material_feature`（91 键）、裁剪上限 83、v4 摘要 golden 固定。
这条消灭"有人顺手把 v4 快照同步成新 schema"的漂移。

并消费 `tools/backup_fixtures/v4/` 的**冻结夹具字节**（WS7 S11）：三份包的
`sha256` 必须同时等于 sidecar 与 golden，golden 的键集合/摘要/行数/载荷字节由本脚本
独立复算。这条消灭"重打包夹具或重算 golden 不会被任何门抓住"的漂移（批次 42 记录
§5 S11 的残余风险）。夹具字节被篡改导致 zip 载荷不可读时按"读到但不一致"记 exit 1。

## 退出码

- 0：全部一致；
- 1：读到但有一项不一致；
- 2：缺文件/无法解析（没有"一致"可言，不当通过）。

## 用法

    python -I tools/ci/check_backup_plan_sync.py
    python -I tools/ci/check_backup_plan_sync.py --json
    python -I tools/ci/check_backup_plan_sync.py --repo /path/to/repo

只读：不写任何文件。CI 挂载草案见 `tools/ci/check_backup_plan_sync.ci.patch`。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

PLAN_PATH = "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt"
RULES_PATH = "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/ClosureRuleTables.kt"
TEST_PATH = "core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlanTest.kt"
SCHEMA_PATH = "core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/75.json"
DESIGN_PATH = "docs/research/2026-10-17-6c2-content-selection-design.md"

MACHINE_BLOCK = re.compile(
    r"<!-- 6c2-plan-v3:begin -->\s*```json\s*(\{.*?})\s*```\s*<!-- 6c2-plan-v3:end -->",
    re.DOTALL,
)

# closure 4（schema 72）、空库、全选（mask=15）、assetPolicy=INCLUDED 的 v4 golden。
# 由本脚本同算法独立复算：91 键全 0 + `smbk-selection-v4` 前缀 + closureVersion=4。
GOLDEN_V4_EMPTY_FULL = "ee9a1b4d3f0ea7ed44eeec773f459df9d55c5df3abda32a69c62f7fdfac6b006"

# v4 冻结夹具（WS5 item 42–47）。门消费其字节/sidecar/golden：重打包、重算摘要或
# 只改 golden 都会被抓住（批次 42 记录 §5 S11 的残余风险）。
FIXTURE_DIR = "tools/backup_fixtures/v4"
GOLDEN_V4_PATH = FIXTURE_DIR + "/v4-pack.golden.json"
FIXTURE_PACKS = (
    "closure4-schema72-mask15.smbk",
    "forged-count.smbk",
    "dropped-nonempty.smbk",
)
# closure 4 冻结夹具的 schema 版本（`smbk-selection-v4` 时点的真实 schema）。
SCHEMA_V4 = 72


class ParseError(Exception):
    """源码/文档形状变了，脚本无法建立可比事实——exit 2，不静默通过。"""


def _balanced(text: str, open_paren: int) -> str:
    depth = 0
    for index in range(open_paren, len(text)):
        char = text[index]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[open_paren:index + 1]
    raise ParseError("括号不配对，无法截取 listOf(…)/setOf(…)")


def _call_slice(text: str, marker: str, call: str = "listOf(") -> str:
    start = text.find(marker)
    if start < 0:
        raise ParseError(f"找不到声明：{marker}")
    open_paren = text.find(call, start)
    if open_paren < 0:
        raise ParseError(f"{marker} 不是 {call}… 形状")
    return _balanced(text, open_paren + len(call) - 1)


def _strings(chunk: str) -> list[str]:
    return re.findall(r'"([^"]*)"', chunk)


def _int_const(text: str, name: str) -> int:
    match = re.search(rf"const val {name}\s*:\s*Int\s*=\s*(-?\d+)", text)
    if not match:
        raise ParseError(f"找不到整型常量 {name}")
    return int(match.group(1))


def _string_const(text: str, name: str) -> str:
    match = re.search(rf'const val {name}\s*:\s*String\s*=\s*"([^"]*)"', text)
    if not match:
        raise ParseError(f"找不到字符串常量 {name}")
    return match.group(1)


def parse_plan(path: Path) -> dict:
    text = path.read_text(encoding="utf-8")
    category_section = text[text.index("val categoryTables"):text.index("val alwaysTables")]
    categories: dict[str, list[str]] = {}
    for match in re.finditer(r"SmbkSelectionCategory\.(\w+) to listOf\(", category_section):
        paren = match.end() - 1
        categories[match.group(1)] = _strings(_balanced(category_section, paren))

    enum_section = text[text.index("enum class SmbkSelectionCategory"):]
    enum: dict[str, tuple[int, str]] = {}
    for match in re.finditer(r"(\w+)\((\d+),\s*\"([^\"]*)\"\)", enum_section):
        enum[match.group(1)] = (int(match.group(2)), match.group(3))

    allowed = [int(value) for value in re.findall(r"\d+", _call_slice(text, "val allowedMasks", "setOf("))]

    always = _strings(_call_slice(text, "val alwaysTables"))
    derived = _strings(_call_slice(text, "val derivedTables"))
    split = _strings(_call_slice(text, "val splitTables"))
    rebuild = _strings(_call_slice(text, "val rebuildTables"))
    trim_order = _strings(_call_slice(text, "val trimOrder"))
    views = _strings(_call_slice(text, "val views"))
    all_tables = [table for tables in categories.values() for table in tables] + always + derived + split

    if len(enum) != 4:
        raise ParseError(f"SmbkSelectionCategory 应恰有 4 项，实际 {sorted(enum)}")
    if set(categories) != set(enum):
        raise ParseError(f"categoryTables 类别 {sorted(categories)} 与枚举 {sorted(enum)} 不一致")

    return {
        "closureVersion": _int_const(text, "CLOSURE_VERSION"),
        "schemaVersion": _int_const(text, "SCHEMA_VERSION"),
        "identityHash": _string_const(text, "IDENTITY_HASH"),
        "digestPrefix": _string_const(text, "CLOSURE_DIGEST_PREFIX"),
        "assetPolicy": _string_const(text, "ASSET_POLICY_INCLUDED"),
        "fullMask": _int_const(text, "FULL_MASK"),
        "allowedMasks": sorted(allowed),
        "categories": categories,
        "enum": enum,
        "alwaysTables": always,
        "derivedTables": derived,
        "splitTables": split,
        "rebuildTables": rebuild,
        "trimOrder": trim_order,
        "views": views,
        "allTables": all_tables,
    }


def parse_rules(path: Path) -> dict:
    text = path.read_text(encoding="utf-8")
    v4_start = text.index("val v4")
    v4_section = text[v4_start:text.index("init {", v4_start)]
    closure = re.search(r"closureVersion\s*=\s*(\d+)", v4_section)
    prefix = re.search(r'digestPrefix\s*=\s*"([^"]+)"', v4_section)
    trim_size = re.search(r"trimOrderSize\s*=\s*(\d+)", v4_section)
    if not (closure and prefix and trim_size):
        raise ParseError("ClosureRuleTables.v4 缺少 closureVersion/digestPrefix/trimOrderSize")
    error_cause_section = text[text.index("errorCauseTables"):text.index("materialFeatureTable")]
    # materialFeatureTable 的声明止于 interiorTables 之前；否则会把 interior 表名一并算进来。
    material_section = text[text.index("materialFeatureTable"):text.index("interiorTables")]
    interior_section = text[text.index("interiorTables"):text.index("val v5")]
    return {
        "closureVersion": int(closure.group(1)),
        "digestPrefix": prefix.group(1),
        "trimOrderSize": int(trim_size.group(1)),
        "errorCauseTables": _strings(error_cause_section),
        "materialFeatureTables": _strings(material_section),
        # closure 5 新增、closure 6 仍保留的内部表：v4 冻结键集合必须同时剔除它们。
        "interiorTables": _strings(interior_section),
    }


def digest(prefix: str, closure: int, category_names: list[str],
           asset_policy: str, row_counts: dict[str, int]) -> str:
    payload = (
        f"{prefix}\nclosureVersion={closure}\n"
        f"categories={','.join(sorted(category_names))}\n"
        f"assetPolicy={asset_policy}\n"
    )
    for table in sorted(row_counts):
        payload += f"{table}={row_counts[table]}\n"
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def evaluate(repo: Path = REPO) -> dict:
    checks: list[dict] = []

    def record(name: str, ok: bool, detail: str) -> None:
        checks.append({"name": name, "ok": bool(ok), "detail": detail})

    paths = {
        "plan": repo / PLAN_PATH,
        "rules": repo / RULES_PATH,
        "test": repo / TEST_PATH,
        "schema": repo / SCHEMA_PATH,
        "design": repo / DESIGN_PATH,
        "v4_golden": repo / GOLDEN_V4_PATH,
    }
    for name in FIXTURE_PACKS:
        paths[f"fixture:{name}"] = repo / FIXTURE_DIR / name
        paths[f"fixture:{name}.sha256"] = repo / FIXTURE_DIR / f"{name}.sha256"
    missing = [str(path) for path in paths.values() if not path.is_file()]
    if missing:
        record("files", False, "缺文件：" + "、".join(missing))
        return {"ok": False, "checks": checks, "failed": checks}

    plan = parse_plan(paths["plan"])
    rules = parse_rules(paths["rules"])
    design_text = paths["design"].read_text(encoding="utf-8")
    blocks = MACHINE_BLOCK.findall(design_text)
    if len(blocks) != 1:
        raise ParseError(f"设计件必须恰有一个 6c2-plan-v3 机器块，实际 {len(blocks)}")
    block = json.loads(blocks[0])
    schema = json.loads(paths["schema"].read_text(encoding="utf-8"))["database"]
    test_text = paths["test"].read_text(encoding="utf-8")
    golden_match = re.search(
        r'GOLDEN_EMPTY_FULL_DIGEST\s*=\s*\n?\s*"([0-9a-f]{64})"', test_text
    )
    if not golden_match:
        raise ParseError("SmbkSelectionPlanTest 找不到 GOLDEN_EMPTY_FULL_DIGEST")
    test_golden = golden_match.group(1)

    # ---- 1. 版本三元组 ----
    record("closureVersion: code==block", plan["closureVersion"] == block["closureVersion"],
           f"code={plan['closureVersion']} block={block['closureVersion']}")
    record("schemaVersion: code==block==75.json",
           plan["schemaVersion"] == block["schemaVersion"] == schema["version"],
           f"code={plan['schemaVersion']} block={block['schemaVersion']} schema={schema['version']}")
    record("identityHash: code==block==75.json",
           plan["identityHash"] == block["identityHash"] == schema["identityHash"],
           f"code={plan['identityHash']} block={block['identityHash']} "
           f"schema={schema['identityHash']}")

    # ---- 2. 表清单 ----
    block_tables = (
        [table for spec in block["categories"].values() for table in spec["tables"]]
        + block["alwaysTables"] + block["derivedTables"] + block["splitTables"]
    )
    schema_tables = [entity["tableName"] for entity in schema["entities"]]
    record("table list: code==block==75.json",
           plan["allTables"] == block_tables and sorted(plan["allTables"]) == sorted(schema_tables),
           f"code={len(plan['allTables'])} block={len(block_tables)} "
           f"schema={len(schema_tables)}; "
           f"code-only={sorted(set(plan['allTables']) - set(schema_tables))} "
           f"schema-only={sorted(set(schema_tables) - set(plan['allTables']))}")
    record("table count == 99", len(plan["allTables"]) == 99 == len(schema_tables),
           f"code={len(plan['allTables'])} schema={len(schema_tables)}")

    for key, label in (
        ("alwaysTables", "alwaysTables"),
        ("derivedTables", "derivedTables"),
        ("splitTables", "splitTables"),
        ("rebuildTables", "rebuildTables"),
        ("trimOrder", "trimOrder"),
        ("views", "views"),
    ):
        if key == "views":
            schema_views = [view["viewName"] for view in schema.get("views", [])]
            record(f"{label}: code==block==75.json", plan[key] == schema_views == block[key],
                   f"code={plan[key]} schema={schema_views} block={block[key]}")
        else:
            record(f"{label}: code==block", plan[key] == block[key],
                   f"code={len(plan[key])} block={len(block[key])}")

    # ---- 3. 类别 ----
    for name, (bit, label) in plan["enum"].items():
        spec = block["categories"][name]
        record(f"category {name}", spec["bit"] == bit and spec["label"] == label
               and spec["tables"] == plan["categories"][name],
               f"code=({bit},{label},{len(plan['categories'][name])}) "
               f"block=({spec['bit']},{spec['label']},{len(spec['tables'])})")

    record("allowedMasks == block maskPolicy.closedMasks",
           plan["allowedMasks"] == sorted(block["maskPolicy"]["closedMasks"]),
           f"code={plan['allowedMasks']} block={block['maskPolicy']['closedMasks']}")

    # ---- 4. digest golden：独立复算 ----
    zero_counts = {table: 0 for table in plan["allTables"]}
    recomputed = digest(plan["digestPrefix"], plan["closureVersion"],
                        list(plan["enum"]), plan["assetPolicy"], zero_counts)
    published = block["digest"]["emptyFullSelectionGolden"]
    record("digest: recomputed==test golden==block golden",
           recomputed == test_golden == published,
           f"recomputed={recomputed} test={test_golden} block={published}")

    # ---- 5. closure 4 冻结规则表 ----
    code_tables = set(plan["allTables"])
    material = set(rules["materialFeatureTables"])
    error_cause = set(rules["errorCauseTables"])
    interior = set(rules["interiorTables"])
    v4_added = error_cause | interior
    v4_tables = (code_tables - v4_added) | material
    record("v4: 91 keys and delta == {+error_cause+interior, -material_feature}",
           len(v4_tables) == 91
           and v4_added <= code_tables
           and material.isdisjoint(code_tables)
           and (code_tables - v4_tables) == v4_added
           and (v4_tables - code_tables) == material,
           f"keys={len(v4_tables)} added={sorted(v4_added)} "
           f"dropped={sorted(material)}")
    record("v4: frozen closure/prefix/trim limit",
           rules["closureVersion"] == 4 and rules["digestPrefix"] == "smbk-selection-v4"
           and rules["trimOrderSize"] == 83,
           f"closure={rules['closureVersion']} prefix={rules['digestPrefix']} "
           f"trimLimit={rules['trimOrderSize']}")
    v4_recomputed = digest(rules["digestPrefix"], rules["closureVersion"],
                           list(plan["enum"]), plan["assetPolicy"],
                           {table: 0 for table in v4_tables})
    record("v4: empty-full digest golden", v4_recomputed == GOLDEN_V4_EMPTY_FULL,
           f"recomputed={v4_recomputed} pinned={GOLDEN_V4_EMPTY_FULL}")

    # ---- 6. v4 冻结夹具（字节 / sidecar / golden 同源） ----
    golden_v4 = json.loads(paths["v4_golden"].read_text(encoding="utf-8"))
    negative = golden_v4.get("negative", {})
    fixture_mismatches: list[str] = []
    for name in FIXTURE_PACKS:
        data = (repo / FIXTURE_DIR / name).read_bytes()
        actual = hashlib.sha256(data).hexdigest()
        pinned = (repo / FIXTURE_DIR / f"{name}.sha256").read_text(encoding="utf-8").strip()
        declared = golden_v4 if name == golden_v4.get("fixture") else negative.get(name, {})
        expected_sha = declared.get("packSha256")
        expected_bytes = declared.get("packBytes")
        if actual != pinned or expected_sha != actual or expected_bytes != len(data):
            fixture_mismatches.append(
                f"{name}: bytes={len(data)}/{expected_bytes} "
                f"sha={actual[:12]} sidecar={pinned[:12]} golden={(expected_sha or '-')[:12]}"
            )
    record("v4 fixture: bytes == golden == sidecar", not fixture_mismatches,
           "；".join(fixture_mismatches) or "3 夹具字节与 sidecar/golden 逐位一致")

    golden_categories = sorted(
        name for name, (bit, _) in plan["enum"].items() if plan["fullMask"] & bit
    )
    record("v4 fixture golden: declared shape matches frozen rule",
           golden_v4["closureVersion"] == rules["closureVersion"]
           and golden_v4["digestPrefix"] == rules["digestPrefix"]
           and golden_v4["schemaVersion"] == SCHEMA_V4
           and golden_v4["categoryMask"] == plan["fullMask"]
           and sorted(golden_v4["categories"]) == golden_categories
           and golden_v4["assetPolicy"] == plan["assetPolicy"]
           and set(golden_v4["rowCountKeys"]) == set(golden_v4["rowCounts"]) == v4_tables,
           f"closure={golden_v4['closureVersion']} prefix={golden_v4['digestPrefix']} "
           f"schema={golden_v4['schemaVersion']} mask={golden_v4['categoryMask']} "
           f"keys={len(golden_v4['rowCountKeys'])}")

    golden_digest = digest(golden_v4["digestPrefix"], golden_v4["closureVersion"],
                           list(plan["enum"]), golden_v4["assetPolicy"],
                           golden_v4["rowCounts"])
    record("v4 fixture golden: closureDigest recomputed from rowCounts",
           golden_digest == golden_v4["closureDigest"],
           f"recomputed={golden_digest} golden={golden_v4['closureDigest']}")

    compact_counts = json.dumps(
        {table: golden_v4["rowCounts"][table] for table in sorted(golden_v4["rowCounts"])},
        ensure_ascii=False, separators=(",", ":"),
    ).encode("utf-8")
    record("v4 fixture golden: rowCountsSha256 recomputed",
           hashlib.sha256(compact_counts).hexdigest() == golden_v4["rowCountsSha256"],
           f"recomputed={hashlib.sha256(compact_counts).hexdigest()[:12]} "
           f"golden={golden_v4['rowCountsSha256'][:12]}")

    try:
        with zipfile.ZipFile(repo / FIXTURE_DIR / golden_v4["fixture"]) as archive:
            payload = archive.read("database.sqlite")
    except (zipfile.BadZipFile, KeyError, OSError) as error:
        # 夹具字节已被篡改/重打包到不可解析：这是"读到但不一致"（exit 1），不得抛栈。
        record("v4 fixture: payload database bytes == golden", False,
               f"payload unreadable: {error}")
    else:
        record("v4 fixture: payload database bytes == golden",
               hashlib.sha256(payload).hexdigest() == golden_v4["databaseSha256"]
               and len(payload) == golden_v4["databaseBytes"],
               f"payload={len(payload)}B/{golden_v4['databaseBytes']}B "
               f"sha={hashlib.sha256(payload).hexdigest()[:12]}")

    failed = [check for check in checks if not check["ok"]]
    return {"ok": not failed, "checks": checks, "failed": failed}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="备份选择清单四方一致性门")
    parser.add_argument("--repo", type=Path, default=REPO, help="仓库根（默认脚本上两级）")
    parser.add_argument("--json", action="store_true", help="JSON 输出")
    args = parser.parse_args(argv)

    try:
        result = evaluate(args.repo)
    except ParseError as exc:
        print(f"ERROR 无法建立可比事实：{exc}", file=sys.stderr)
        return 2
    except (OSError, ValueError, KeyError) as exc:
        print(f"ERROR 读取/解析输入失败：{exc}", file=sys.stderr)
        return 2

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=1))
    else:
        for check in result["checks"]:
            mark = "OK  " if check["ok"] else "FAIL"
            print(f"  {mark} {check['name']}：{check['detail']}")
        if result["ok"]:
            print("PASS 四方一致（code / §9.14 机器块 / golden / 75.json）")
        else:
            print(f"FAIL {len(result['failed'])} 项不一致")
    if any(check["name"] == "files" for check in result["failed"]):
        return 2  # 缺文件：没有"一致"可言，不当通过
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
