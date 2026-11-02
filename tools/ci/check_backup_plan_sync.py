#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""备份内容选择清单的**四方一致性**门（批次 41 / WS1 item 7）。

## 它消灭的具体失败

KD-80 类漂移已经出现过三次：`SmbkSelectionPlan.kt`、设计件 §9.11 机器块、
`SmbkSelectionPlanTest` 的 golden、`73.json`（Room/KSP 导出物）任一处单独改动，
其余三处仍能各自"自洽"——单测只比其中两处，于是"清单说 92 表、导出物有 93 表"
可以静默存在到成包/恢复阶段。本脚本把四处钉在一起，任一处不等即非零退出：

    SmbkSelectionPlan.kt 常量/表清单
        == 设计件 §9.11 `6c2-plan-v3` 机器块
        == SmbkSelectionPlanTest 的空库全选 golden（并按同算法独立复算）
        == core/database/schemas/.../73.json

另外校验 closure 4 的冻结规则表（WS1 item 5）：v4 键集合 = v5 − 错因两表 +
`knowledge_search_material_feature`（91 键）、裁剪上限 83、v4 摘要 golden 固定。
这条消灭"有人顺手把 v4 快照同步成新 schema"的漂移。

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
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

PLAN_PATH = "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt"
RULES_PATH = "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/ClosureRuleTables.kt"
TEST_PATH = "core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlanTest.kt"
SCHEMA_PATH = "core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/73.json"
DESIGN_PATH = "docs/research/2026-10-17-6c2-content-selection-design.md"

MACHINE_BLOCK = re.compile(
    r"<!-- 6c2-plan-v3:begin -->\s*```json\s*(\{.*?})\s*```\s*<!-- 6c2-plan-v3:end -->",
    re.DOTALL,
)

# closure 4（schema 72）、空库、全选（mask=15）、assetPolicy=INCLUDED 的 v4 golden。
# 由本脚本同算法独立复算：91 键全 0 + `smbk-selection-v4` 前缀 + closureVersion=4。
GOLDEN_V4_EMPTY_FULL = "ee9a1b4d3f0ea7ed44eeec773f459df9d55c5df3abda32a69c62f7fdfac6b006"


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
    material_section = text[text.index("materialFeatureTable"):text.index("val v5")]
    return {
        "closureVersion": int(closure.group(1)),
        "digestPrefix": prefix.group(1),
        "trimOrderSize": int(trim_size.group(1)),
        "errorCauseTables": _strings(error_cause_section),
        "materialFeatureTables": _strings(material_section),
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
    }
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
    record("schemaVersion: code==block==73.json",
           plan["schemaVersion"] == block["schemaVersion"] == schema["version"],
           f"code={plan['schemaVersion']} block={block['schemaVersion']} schema={schema['version']}")
    record("identityHash: code==block==73.json",
           plan["identityHash"] == block["identityHash"] == schema["identityHash"],
           f"code={plan['identityHash']} block={block['identityHash']} "
           f"schema={schema['identityHash']}")

    # ---- 2. 表清单 ----
    block_tables = (
        [table for spec in block["categories"].values() for table in spec["tables"]]
        + block["alwaysTables"] + block["derivedTables"] + block["splitTables"]
    )
    schema_tables = [entity["tableName"] for entity in schema["entities"]]
    record("table list: code==block==73.json",
           plan["allTables"] == block_tables and sorted(plan["allTables"]) == sorted(schema_tables),
           f"code={len(plan['allTables'])} block={len(block_tables)} "
           f"schema={len(schema_tables)}; "
           f"code-only={sorted(set(plan['allTables']) - set(schema_tables))} "
           f"schema-only={sorted(set(schema_tables) - set(plan['allTables']))}")
    record("table count == 92", len(plan["allTables"]) == 92 == len(schema_tables),
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
            record(f"{label}: code==73.json", plan[key] == schema_views,
                   f"code={plan[key]} schema={schema_views}")
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
    published = block["digest"]["goldenEmptyFull"]["sha256"]
    record("digest: recomputed==test golden==block golden",
           recomputed == test_golden == published,
           f"recomputed={recomputed} test={test_golden} block={published}")

    # ---- 5. closure 4 冻结规则表 ----
    code_tables = set(plan["allTables"])
    material = set(rules["materialFeatureTables"])
    error_cause = set(rules["errorCauseTables"])
    v4_tables = (code_tables - error_cause) | material
    record("v4: 91 keys and delta == {+error_cause, -material_feature}",
           len(v4_tables) == 91
           and error_cause <= code_tables
           and material.isdisjoint(code_tables)
           and (code_tables - v4_tables) == error_cause
           and (v4_tables - code_tables) == material,
           f"keys={len(v4_tables)} added={sorted(error_cause)} "
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
            print("PASS 四方一致（code / §9.11 / golden / 73.json）")
        else:
            print(f"FAIL {len(result['failed'])} 项不一致")
    if any(check["name"] == "files" for check in result["failed"]):
        return 2  # 缺文件：没有"一致"可言，不当通过
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
