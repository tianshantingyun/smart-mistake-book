#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""结果面向禁令自查：评分/门脚本是否"读取评测集标签来调参"（AUD-08 item 50）。

## 它消灭的具体失败

禁令原文（`docs/research/2026-10-11-new-stages-spec.md:165`）：训练/开发/验收必须切分，
"不能用同一批调门又验门"；`tools/split_golden/GATE.md:12-13` 与 §5.7 进一步规定
"不得为让门变绿改阈值/口径/分母/金标"。但这条禁令此前**没有任何机器检查**：

  ① 阈值由金标度量值算出（`GATE = observed_rate - 0.1`）时，diff 里只是一个普通常量，
     与"立项自定 + 出处"的常量无法区分；
  ② 金标数据被写进参数文件（`gate_thresholds.json`）再被另一个门读取，调参发生在
     两次运行之间，单看任何一个脚本都"干净"；
  ③ 门脚本读 protected 集，同时开放 `--*-threshold` / 环境变量覆盖，事后改阈值不留痕。

本脚本只做**静态只读**检查，把上述三种形态报出来；**不修改任何被扫文件、不自动修复**
（"只报不改"）。它不是正确性证明：动态拼路径、跨仓库生成、git 历史里的改值都超出
静态 AST 的可见范围——这些在报告的 `limitations` 里如实登记，不假装已检。

## 检查项

| id | 严重度 | 判据 |
|---|---|---|
| `LABEL_DERIVED_THRESHOLD` | error | 阈值/门限命名的变量（或阈值字典项）由 protected 标签数据派生 |
| `LABEL_DERIVED_PARAM_OUTPUT` | error | 把 protected 标签派生值写进阈值/参数/预算/校准类文件 |
| `THRESHOLD_OVERRIDE_SURFACE` | warning | 读 protected 标签的脚本同时开放阈值 CLI/环境变量覆盖（调参面，不等于已调参） |
| `SCAN_UNPARSABLE` | error | 候选脚本无法解析——无法检查不得当作干净 |

protected 标签集 = 路径含 `golden/blind/acceptance/held-out/label/annotation/eval-set/judge`；
`dev/train/tuning` 集是可调参集，不算 protected（两者同时命中时按 protected 处理）。

## 退出码

  0  无 error（warning 只报不拦）
  1  存在 error（禁令面需人工复核）
  2  用法/扫描根错误

## 用法（仓库根）

    python tools/ci/check_eval_label_leakage.py
    python tools/ci/check_eval_label_leakage.py --paths tools/split_golden tools/figure_eval
    python tools/ci/check_eval_label_leakage.py --json
"""

from __future__ import annotations

import argparse
import ast
import json
import re
import sys
from pathlib import Path

REPORT_SCHEMA_VERSION = 1
HERE: Path = Path(__file__).resolve().parent
REPO: Path = HERE.parents[1]

CANDIDATE_RE = re.compile(
    r"(gate|eval|score|scor|measure|accuracy|golden|judge|quality|calibrat)", re.I)
PROTECTED_LABEL_RE = re.compile(
    r"(golden|blind|acceptance|held[_-]?out|label|annotation|eval[_-]?set|judge)", re.I)
DEV_LABEL_RE = re.compile(r"(^|[^a-z])(dev|train|tuning)([^a-z]|$)", re.I)
THRESHOLD_NAME_RE = re.compile(
    r"(threshold|gate|tolerance|cutoff|quota|cap|budget|min_rate|max_rate|rate_min|rate_max)",
    re.I)
NON_VALUE_NAME_RE = re.compile(r"(_FILE|_PATH|_DIR|_NAME|_KEY|_DOC|_URL)$", re.I)
THRESHOLD_OPTION_TOKENS = {
    "threshold", "thresholds", "tolerance", "tolerances", "cutoff", "cutoffs",
    "quota", "quotas", "budget", "budgets", "cap", "caps",
}
PARAM_OUTPUT_RE = re.compile(
    r"(threshold|param|tuning|budget|tolerance|calib|config)[a-z0-9_-]*\.(json|ya?ml|toml)$",
    re.I)
READ_CALLS = {"open", "read_text", "read_bytes", "read", "load", "loads",
              "DictReader", "reader"}
LABEL_LOADER_NAME_RE = re.compile(r"^(load|read|parse)_", re.I)
PROHIBITION = ("不得用评测集标签调参：阈值/口径/分母/金标不得为让门变绿而改；"
               "训练/开发/验收必须切分，不能用同一批调门又验门"
               "（spec :165；GATE.md §0/§5.7）。本脚本只报不改。")
LIMITATIONS = [
    "静态 AST 看不到动态拼接的路径、跨仓库/跨进程生成的参数文件与 git 历史改值；"
    "clean 只表示这些形态在本脚本的可见范围内未出现，不是正确性证明。",
    "`THRESHOLD_OVERRIDE_SURFACE` 是调参**面**（可能被滥用），不等于已经调过参。",
    "不检查数据集划分本身是否存在——划分状态见 GATE.md §2（v1 未做 train/dev/acceptance）。",
]


# ---------------------------------------------------------------- 路径与字面量

def _resolve_string(node: ast.AST | None, consts: dict[str, str], depth: int = 0) -> str | None:
    """把表达式解析成字符串：常量、常量名、Path 拼接、f-string。解析不了返回 None。"""
    if node is None or depth > 8:
        return None
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    if isinstance(node, ast.Name):
        return consts.get(node.id)
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Div):
        left = _resolve_string(node.left, consts, depth + 1)
        right = _resolve_string(node.right, consts, depth + 1)
        if right:
            return (left + "/" if left else "") + right
        return left
    if isinstance(node, ast.Call):
        for arg in list(node.args) + [kw.value for kw in node.keywords]:
            resolved = _resolve_string(arg, consts, depth + 1)
            if resolved:
                return resolved
        return None
    if isinstance(node, ast.Attribute):
        return _resolve_string(node.value, consts, depth + 1)
    if isinstance(node, ast.JoinedStr):
        parts = [_resolve_string(value, consts, depth + 1) for value in node.values]
        if all(part is not None for part in parts):
            return "".join(part for part in parts if part is not None)
    return None


def _module_constants(tree: ast.Module) -> dict[str, str]:
    """模块级字符串常量（含 `Path(...) / "x.json"`），供后续解析引用。"""
    consts: dict[str, str] = {}
    for _ in range(4):
        changed = False
        for node in tree.body:
            if isinstance(node, ast.Assign):
                resolved = _resolve_string(node.value, consts)
                for target in node.targets:
                    if isinstance(target, ast.Name) and resolved and target.id not in consts:
                        consts[target.id] = resolved
                        changed = True
            elif isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name):
                resolved = _resolve_string(node.value, consts)
                if resolved and node.target.id not in consts:
                    consts[node.target.id] = resolved
                    changed = True
        if not changed:
            break
    return consts


_PATHISH_RE = re.compile(r"(\.(json|ya?ml|csv|tsv|txt|md)$)|[/\\]")


def _looks_like_path(text: str) -> bool:
    """只有"像文件路径"的字符串才算标签来源；正则/说明文本里的 `golden` 不算。

    它消灭的具体失败：扫描器把自身的 `PROTECTED_LABEL_RE = re.compile(r"(golden|…)")`
    误计为"读金标的脚本"——常量值命中关键词但根本不是路径，protected 读者计数与
    覆盖面告警都会被污染。
    """
    return bool(_PATHISH_RE.search(text))


def _classify_label(text: str | None) -> str | None:
    if not text or not _looks_like_path(text):
        return None
    if PROTECTED_LABEL_RE.search(text):
        return "protected"
    if DEV_LABEL_RE.search(text):
        return "tunable"
    return None


# ---------------------------------------------------------------- 作用域与污点

def _scope_assignments(scope: ast.AST) -> list[ast.AST]:
    """收集一个作用域内的赋值节点，不进入嵌套函数/类体（各自独立作用域）。"""
    assignments: list[ast.AST] = []

    class Visitor(ast.NodeVisitor):
        def visit_Assign(self, node: ast.Assign) -> None:
            assignments.append(node)

        def visit_AnnAssign(self, node: ast.AnnAssign) -> None:
            assignments.append(node)

        def visit_AugAssign(self, node: ast.AugAssign) -> None:
            assignments.append(node)

        def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
            return

        def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> None:
            return

        def visit_ClassDef(self, node: ast.ClassDef) -> None:
            return

    for statement in getattr(scope, "body", []):
        Visitor().visit(statement)
    return assignments


def _call_name(node: ast.Call) -> str | None:
    if isinstance(node.func, ast.Name):
        return node.func.id
    if isinstance(node.func, ast.Attribute):
        return node.func.attr
    return None


def _label_read(node: ast.Call, consts: dict[str, str]) -> str | None:
    """该调用是否在读取 protected/tunable 标签文件；返回分类。"""
    name = _call_name(node)
    if name not in READ_CALLS:
        return None
    for arg in list(node.args) + [kw.value for kw in node.keywords]:
        classification = _classify_label(_resolve_string(arg, consts))
        if classification:
            return classification
    return None


def _label_loader_functions(tree: ast.Module, consts: dict[str, str]) -> set[str]:
    """函数体内读标签的辅助函数名（调用它的结果按标签数据污点处理）。"""
    names: set[str] = set()
    for node in ast.walk(tree):
        if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        if not LABEL_LOADER_NAME_RE.match(node.name):
            continue
        for inner in ast.walk(node):
            if isinstance(inner, ast.Call) and _label_read(inner, consts):
                names.add(node.name)
                break
    return names


def _expr_tainted(node: ast.AST | None, taint: set[str], consts: dict[str, str],
                  label_loader_funcs: set[str]) -> bool:
    if node is None:
        return False
    if isinstance(node, ast.Name):
        return node.id in taint
    if isinstance(node, ast.Call):
        if _label_read(node, consts):
            return True
        name = _call_name(node)
        if name in label_loader_funcs:
            return True
        if name in READ_CALLS and any(
                _expr_tainted(arg, taint, consts, label_loader_funcs)
                for arg in list(node.args) + [kw.value for kw in node.keywords]):
            return True
        if isinstance(node.func, ast.Attribute) and _expr_tainted(
                node.func.value, taint, consts, label_loader_funcs):
            return True
        return any(_expr_tainted(arg, taint, consts, label_loader_funcs)
                   for arg in node.args)
    if isinstance(node, (ast.Attribute, ast.Subscript)):
        return _expr_tainted(node.value, taint, consts, label_loader_funcs)
    if isinstance(node, ast.Dict):
        return any(_expr_tainted(value, taint, consts, label_loader_funcs)
                   for value in node.values if value is not None)
    if isinstance(node, (ast.List, ast.Tuple, ast.Set)):
        return any(_expr_tainted(element, taint, consts, label_loader_funcs)
                   for element in node.elts)
    if isinstance(node, (ast.BinOp, ast.BoolOp, ast.Compare, ast.IfExp)):
        children = list(ast.iter_child_nodes(node))
        return any(_expr_tainted(child, taint, consts, label_loader_funcs)
                   for child in children if isinstance(child, ast.expr))
    if isinstance(node, ast.JoinedStr):
        return any(_expr_tainted(value, taint, consts, label_loader_funcs)
                   for value in node.values)
    return False


def _tainted_names(assignments: list[ast.AST], seeds: set[str],
                   consts: dict[str, str], label_loader_funcs: set[str]) -> set[str]:
    taint = set(seeds)
    for _ in range(8):
        changed = False
        for node in assignments:
            if isinstance(node, ast.Assign):
                targets, value = node.targets, node.value
            elif isinstance(node, ast.AnnAssign):
                targets, value = [node.target], node.value
            else:
                targets, value = [node.target], node.value
            if value is None or not _expr_tainted(value, taint, consts, label_loader_funcs):
                continue
            for target in targets:
                if isinstance(target, ast.Name) and target.id not in taint:
                    taint.add(target.id)
                    changed = True
        if not changed:
            break
    return taint


# ---------------------------------------------------------------- 目标命名

def _is_threshold_name(name: str) -> bool:
    return bool(THRESHOLD_NAME_RE.search(name)) and not NON_VALUE_NAME_RE.search(name)


def _is_threshold_option(text: str) -> bool:
    """阈值覆盖 option/env 名：按 `-`/`_` 分词后，整词属于阈值词表。

    不解 `gate`：`--enforce-quality-gates` 这类布尔开关只开/关门，不移动阈值；
    把它当阈值覆盖会产生假阳性。
    """
    tokens = [token for token in re.split(r"[^a-z0-9]+", text.lower()) if token]
    return any(token in THRESHOLD_OPTION_TOKENS for token in tokens)


def _subscript_base(node: ast.Subscript) -> str | None:
    value = node.value
    while isinstance(value, ast.Subscript):
        value = value.value
    return value.id if isinstance(value, ast.Name) else None


def _output_name(node: ast.AST | None, consts: dict[str, str],
                 handles: dict[str, str]) -> str | None:
    """写文件的输出名：Path 常量、`NAME.open(...)`、`open(NAME, ...)`、文件句柄变量。"""
    if isinstance(node, ast.Name):
        return consts.get(node.id) or handles.get(node.id)
    if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) \
            and node.func.attr == "open":
        return _resolve_string(node.func.value, consts)
    if isinstance(node, ast.Call) and _call_name(node) == "open" and node.args:
        return _resolve_string(node.args[0], consts)
    if isinstance(node, ast.Attribute):
        return _resolve_string(node.value, consts)
    return None


def _file_handles(assignments: list[ast.AST], consts: dict[str, str]) -> dict[str, str]:
    handles: dict[str, str] = {}
    for node in assignments:
        if isinstance(node, ast.Assign) and isinstance(node.value, ast.Call) \
                and _call_name(node.value) == "open" and node.value.args:
            resolved = _resolve_string(node.value.args[0], consts)
            if resolved:
                for target in node.targets:
                    if isinstance(target, ast.Name):
                        handles[target.id] = resolved
        if isinstance(node, ast.Assign) and isinstance(node.value, ast.Call) \
                and isinstance(node.value.func, ast.Attribute) \
                and node.value.func.attr == "open":
            resolved = _resolve_string(node.value.func.value, consts)
            if resolved:
                for target in node.targets:
                    if isinstance(target, ast.Name):
                        handles[target.id] = resolved
    return handles


# ---------------------------------------------------------------- 单文件扫描

def _finding(check: str, severity: str, path: Path, line: int, detail: str) -> dict:
    return {"check": check, "severity": severity, "file": str(path), "line": line,
            "detail": detail}


def _scan_file(path: Path, display: str) -> tuple[list[dict], bool]:
    try:
        source = path.read_text(encoding="utf-8")
        tree = ast.parse(source, filename=str(path))
    except (OSError, SyntaxError, UnicodeDecodeError) as failure:
        return [_finding("SCAN_UNPARSABLE", "error", Path(display), 0,
                         f"无法解析，未检查：{failure}")], False

    consts = _module_constants(tree)
    label_loader_funcs = _label_loader_functions(tree, consts)
    protected_constants = {name for name, value in consts.items()
                           if _classify_label(value) == "protected"}
    has_protected_read = bool(protected_constants) or any(
        _label_read(node, consts) == "protected" for node in ast.walk(tree)
        if isinstance(node, ast.Call))

    findings: list[dict] = []
    scopes: list[ast.AST] = [tree] + [
        node for node in ast.walk(tree)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))]

    for scope in scopes:
        assignments = _scope_assignments(scope)
        taint = _tainted_names(assignments, protected_constants, consts,
                               label_loader_funcs)
        handles = _file_handles(assignments, consts)

        for node in assignments:
            targets = node.targets if isinstance(node, ast.Assign) else [node.target]
            value = node.value
            if value is None:
                continue
            value_tainted = _expr_tainted(value, taint, consts, label_loader_funcs)
            if not value_tainted:
                continue
            for target in targets:
                if isinstance(target, ast.Name) and _is_threshold_name(target.id):
                    findings.append(_finding(
                        "LABEL_DERIVED_THRESHOLD", "error", Path(display),
                        getattr(node, "lineno", 0),
                        f"{target.id} 由 protected 标签数据派生（禁止用评测集调参）"))
                elif isinstance(target, ast.Subscript):
                    base = _subscript_base(target)
                    if base and _is_threshold_name(base):
                        findings.append(_finding(
                            "LABEL_DERIVED_THRESHOLD", "error", Path(display),
                            getattr(node, "lineno", 0),
                            f"{base}[...] 的阈值项由 protected 标签数据派生"))

        for node in ast.walk(scope):
            if not isinstance(node, ast.Call):
                continue
            name = _call_name(node)
            payload_tainted = bool(node.args) and _expr_tainted(
                node.args[0], taint, consts, label_loader_funcs)

            # 写参数文件：json.dump(payload, out) / Path.write_text(payload) / fh.write(payload)
            if payload_tainted and name in ("dump", "write_text", "write_bytes", "write"):
                if name == "dump":
                    output = _output_name(node.args[1] if len(node.args) > 1 else None,
                                          consts, handles)
                elif name in ("write_text", "write_bytes") and isinstance(
                        node.func, ast.Attribute):
                    output = _output_name(node.func.value, consts, handles)
                else:
                    output = _output_name(node.func.value if isinstance(
                        node.func, ast.Attribute) else None, consts, handles)
                if output and PARAM_OUTPUT_RE.search(output):
                    findings.append(_finding(
                        "LABEL_DERIVED_PARAM_OUTPUT", "error", Path(display),
                        getattr(node, "lineno", 0),
                        f"把 protected 标签派生值写进参数文件 {output!r}"))

            # 阈值覆盖面：add_argument("--*-threshold") / os.getenv("...threshold...")
            if name == "add_argument":
                for arg in node.args:
                    text = _resolve_string(arg, consts)
                    if text and text.startswith("-") and _is_threshold_option(text):
                        if has_protected_read:
                            findings.append(_finding(
                                "THRESHOLD_OVERRIDE_SURFACE", "warning", Path(display),
                                getattr(node, "lineno", 0),
                                f"读 protected 标签的脚本开放阈值覆盖 {text!r}（调参面）"))
            if name in ("getenv", "get") and isinstance(node.func, ast.Attribute) \
                    and isinstance(node.func.value, ast.Attribute) \
                    and node.func.value.attr == "environ" and node.args:
                text = _resolve_string(node.args[0], consts)
                if text and _is_threshold_option(text) and has_protected_read:
                    findings.append(_finding(
                        "THRESHOLD_OVERRIDE_SURFACE", "warning", Path(display),
                        getattr(node, "lineno", 0),
                        f"读 protected 标签的脚本允许环境变量覆盖阈值 {text!r}"))

    unique: dict[tuple, dict] = {}
    for finding in findings:
        unique[(finding["check"], finding["file"], finding["line"], finding["detail"])] = finding
    return list(unique.values()), has_protected_read


# ---------------------------------------------------------------- 扫描入口

def _candidate_files(roots: list[Path]) -> list[Path]:
    files: list[Path] = []
    for root in roots:
        if root.is_file():
            if root.suffix == ".py" and not root.name.startswith("test_"):
                files.append(root)
            continue
        for path in root.rglob("*.py"):
            parts = {part.lower() for part in path.parts}
            if "__pycache__" in parts or "tests" in parts:
                continue
            if path.name.startswith("test_"):
                continue
            if CANDIDATE_RE.search(path.name):
                files.append(path)
    return sorted({path.resolve() for path in files})


def _display_path(path: Path, roots: list[Path]) -> str:
    for root in sorted(roots, key=lambda item: len(str(item)), reverse=True):
        if root.is_file():
            if path == root:
                return path.name
            continue
        try:
            return path.relative_to(root).as_posix()
        except ValueError:
            continue
    return path.as_posix()


def scan(paths: list[str | Path]) -> dict:
    roots = [Path(path).resolve() for path in paths]
    files = _candidate_files(roots)
    findings: list[dict] = []
    protected_readers: list[str] = []
    for path in files:
        display = _display_path(path, roots)
        file_findings, protected = _scan_file(path, display)
        findings.extend(file_findings)
        if protected:
            protected_readers.append(display)
    findings.sort(key=lambda item: (item["file"], item["line"], item["check"], item["detail"]))
    errors = sum(1 for item in findings if item["severity"] == "error")
    warnings = sum(1 for item in findings if item["severity"] == "warning")
    return {
        "reportSchemaVersion": REPORT_SCHEMA_VERSION,
        "prohibition": PROHIBITION,
        "roots": [str(root) for root in roots],
        "scanned": [str(_display_path(path, roots)) for path in files],
        "protectedLabelReaders": protected_readers,
        "findings": findings,
        "summary": {"files": len(files), "protectedLabelReaders": len(protected_readers),
                    "errors": errors, "warnings": warnings},
        "limitations": list(LIMITATIONS),
        "exitCode": 1 if errors else 0,
    }


def _print_human(report: dict) -> None:
    print("结果面向禁令自查（只报不改）：读取评测集标签来调参的静态检查")
    print(f"  扫描 {report['summary']['files']} 个候选脚本；"
          f"protected 标签读者 {report['summary']['protectedLabelReaders']} 个")
    if not report["findings"]:
        print("  未发现 LABEL_DERIVED_THRESHOLD / LABEL_DERIVED_PARAM_OUTPUT；"
              "无阈值覆盖面告警")
    for finding in report["findings"]:
        print(f"  [{finding['severity']}] {finding['check']} "
              f"{finding['file']}:{finding['line']} — {finding['detail']}")
    print(f"  error {report['summary']['errors']} / warning "
          f"{report['summary']['warnings']} → exit {report['exitCode']}")
    print("  说明：静态检查不覆盖动态路径/跨仓库生成/git 历史改值；clean ≠ 正确性证明。")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="扫描评分/门脚本是否读取评测集标签来调参（只报不改）")
    parser.add_argument("--paths", nargs="+", default=None,
                        help="扫描根（默认仓库 tools/；可为目录或单个 .py）")
    parser.add_argument("--json", action="store_true", help="输出机器口径 JSON")
    args = parser.parse_args(argv)
    paths = args.paths or [str(REPO / "tools")]
    missing = [path for path in paths if not Path(path).exists()]
    if missing:
        print(f"扫描根不存在：{missing}", file=sys.stderr)
        return 2
    report = scan(paths)
    if args.json:
        print(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2))
    else:
        _print_human(report)
    return int(report["exitCode"])


if __name__ == "__main__":
    sys.exit(main())
