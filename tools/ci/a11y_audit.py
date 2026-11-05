#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""I 域（可用性与无障碍）自动审计：语义树导出、触摸目标、标签、对比度。

## 它消灭的具体失败

批次 43 之前，I 域的“无障碍已检查”只等于一张手截的图或一次心情走查：没人能
回答“这个页面上有多少可点击节点小于 48dp”“哪个按钮没有朗读文本”“主题色的
对比度是多少”。本工具把 uiautomator 语义树（`uiautomator dump` 产物）离线解析
成带退出码的断言，一条违规一行，失败即 exit 1：

- 可点击节点的**两个方向**都必须 ≥ `--min-dp`（默认 48dp，按 `--density-dpi`
  把 px 折算成 dp）；
- 可点击节点必须有 `text` 或 `content-desc`（含后代合并语义），否则无法被
  TalkBack 朗读；
- 对比度：只有显式给出颜色输入时才计算 WCAG 对比度，低于 4.5 判 FAIL；
  **没有颜色输入时标 UNVERIFIED，绝不把“结构检查通过”冒充整体通过**。

边界：本工具只证明“语义树上的结构事实”。朗读语感、朗读顺序是否自然、图标
是否表意、动效可感知性无法自动判定，台账对应行保持 UNVERIFIED。语义树本身
由设备导出（adb + uiautomator），导出脚本记录 serial 与 APK sha，见
`tools/ci/a11y_probe.py`。

用法：

    # 单次审计（结构 + 可选对比度）
    python -I tools/ci/a11y_audit.py audit --dump build/window_dump.xml \
        --density-dpi 420 --out build/a11y.json

    # 只读主题色对（不依赖设备）
    python -I tools/ci/a11y_audit.py theme --colors build/colors.json --out build/theme.json

退出码：0 无 FAIL 违规（对比度可能为 UNVERIFIED，见输出）；1 有违规；
2 参数/文件不可读。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
HEX_RE = re.compile(r"0x([0-9A-Fa-f]{8})")
DEFAULT_MIN_DP = 48.0
DEFAULT_THRESHOLD = 4.5
# 大字号（>=18pt 普通 / >=14pt 粗体）在 WCAG 2.1 AA 下阈值 3.0；语义树没有可靠
# 的字号与字重，默认一律按 4.5，宁可偏严。
LARGE_TEXT_THRESHOLD = 3.0
# uiautomator 语义树是设备产物，仍按不可信输入处理：限制体积并拒绝 DTD/实体。
MAX_DUMP_BYTES = 16 * 1024 * 1024
ENTITY_RE = re.compile(rb"<!\s*(DOCTYPE|ENTITY)", re.IGNORECASE)


def parse_bounds(value: str) -> tuple[int, int, int, int] | None:
    match = BOUNDS_RE.search(value or "")
    if not match:
        return None
    return tuple(int(part) for part in match.groups())  # type: ignore[return-value]


def parse_dump(xml_text: str) -> list[dict]:
    """把 uiautomator XML 解析成扁平节点列表，保留父子关系（用于合并语义）。

    安全约束：语义树是不可信设备产物。标准库 ElementTree 不解析外部实体，
    但会展开内部 DTD 实体（billion laughs）。解析前先限制输入体积并拒绝
    `<!DOCTYPE` / `<!ENTITY`，因为 uiautomator dump 永远不需要 DTD。
    """
    raw = xml_text.encode("utf-8", errors="replace")
    if len(raw) > MAX_DUMP_BYTES:
        raise ValueError(f"dump 体积 {len(raw)} 超过上限 {MAX_DUMP_BYTES}")
    if ENTITY_RE.search(raw):
        raise ValueError("dump 含 DTD/实体声明，拒绝解析")
    root = ET.fromstring(xml_text)
    nodes: list[dict] = []

    def walk(element: ET.Element, parent: int | None) -> int:
        attrs = element.attrib
        index = len(nodes)
        nodes.append({
            "index": index,
            "parent": parent,
            "class": attrs.get("class", ""),
            "text": attrs.get("text", "") or "",
            "content_desc": attrs.get("content-desc", "") or "",
            "resource_id": attrs.get("resource-id", "") or "",
            "clickable": (attrs.get("clickable", "false") or "").lower() == "true",
            "enabled": (attrs.get("enabled", "true") or "").lower() != "false",
            "focused": (attrs.get("focused", "false") or "").lower() == "true",
            "selected": (attrs.get("selected", "false") or "").lower() == "true",
            "scrollable": (attrs.get("scrollable", "false") or "").lower() == "true",
            "bounds": attrs.get("bounds", ""),
            "rect": parse_bounds(attrs.get("bounds", "")),
        })
        for child in list(element):
            if child.tag == "node":
                walk(child, index)
        return index

    for element in list(root):
        if element.tag == "node":
            walk(element, None)
    return nodes


def _merged_label(nodes: list[dict], index: int) -> str:
    """可点击容器的朗读文本可能落在后代节点上（Compose 合并语义）。"""
    parts: list[str] = []
    stack = [index]
    while stack:
        current = stack.pop()
        node = nodes[current]
        for key in ("text", "content_desc"):
            value = (node.get(key) or "").strip()
            if value:
                parts.append(value)
        stack.extend(
            child["index"] for child in nodes
            if child.get("parent") == current
        )
    return " ".join(parts).strip()


def _dp(px: float, density_dpi: int) -> float:
    if density_dpi <= 0:
        raise ValueError("density_dpi 必须为正数")
    return px * 160.0 / float(density_dpi)


def _channel(value: float) -> float:
    value = value / 255.0
    if value <= 0.04045:
        return value / 12.92
    return ((value + 0.055) / 1.055) ** 2.4


def _luminance(rgb) -> float:
    r, g, b = (_channel(float(c)) for c in rgb[:3])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast_ratio(fg, bg) -> float:
    """WCAG 2.1 相对亮度对比度（1.0..21.0）。"""
    l1 = _luminance(fg)
    l2 = _luminance(bg)
    lighter, darker = max(l1, l2), min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)


def _normalise_pairs(colors: dict | None) -> tuple[list[dict], str | None]:
    """颜色输入支持两种形态：按节点 bounds 匹配，或主题色对（pairs）。"""
    if colors is None:
        return [], "no colors input"
    if not isinstance(colors, dict):
        return [], "colors input is not an object"
    pairs: list[dict] = []
    for entry in colors.get("pairs") or []:
        if not isinstance(entry, dict):
            continue
        fg = entry.get("fg")
        bg = entry.get("bg")
        if not (isinstance(fg, list) and isinstance(bg, list)):
            continue
        pairs.append({
            "label": str(entry.get("label") or "theme-pair"),
            "fg": fg,
            "bg": bg,
            "large_text": bool(entry.get("large_text")),
        })
    for entry in colors.get("nodes") or []:
        if not isinstance(entry, dict):
            continue
        fg = entry.get("fg")
        bg = entry.get("bg")
        if not (isinstance(fg, list) and isinstance(bg, list)):
            continue
        pairs.append({
            "label": f"node:{entry.get('bounds', '')}",
            "bounds": entry.get("bounds"),
            "fg": fg,
            "bg": bg,
            "large_text": bool(entry.get("large_text")),
        })
    if not pairs:
        return [], "colors input has no usable pairs"
    return pairs, None


def audit(nodes: list[dict], density_dpi: int, colors: dict | None = None,
          min_dp: float = DEFAULT_MIN_DP,
          threshold: float = DEFAULT_THRESHOLD) -> dict:
    """结构检查 + 对比度检查；对比度缺少颜色输入时明确 UNVERIFIED。"""
    violations: list[dict] = []
    clickable = [n for n in nodes if n.get("clickable") and n.get("enabled", True)]

    for node in clickable:
        rect = node.get("rect")
        label = _merged_label(nodes, node["index"])
        if rect is None:
            violations.append({
                "code": "BOUNDS_UNREADABLE",
                "detail": f"{node.get('class')} bounds={node.get('bounds')!r}",
            })
            continue
        width_px = rect[2] - rect[0]
        height_px = rect[3] - rect[1]
        width_dp = _dp(width_px, density_dpi)
        height_dp = _dp(height_px, density_dpi)
        if width_dp + 1e-6 < min_dp or height_dp + 1e-6 < min_dp:
            violations.append({
                "code": "TOUCH_TARGET_TOO_SMALL",
                "detail": (
                    f"{node.get('class')} {node.get('bounds')} "
                    f"= {width_dp:.1f}x{height_dp:.1f}dp < {min_dp}dp"
                    f" text={node.get('text')!r} desc={node.get('content_desc')!r}"
                ),
            })
        if not label:
            violations.append({
                "code": "MISSING_LABEL",
                "detail": f"{node.get('class')} {node.get('bounds')} 无可朗读文本",
            })

    pairs, pair_error = _normalise_pairs(colors)
    contrast = {
        "status": "UNVERIFIED",
        "threshold": threshold,
        "measured": 0,
        "min_ratio": None,
        "pairs": [],
        "reason": pair_error,
    }
    if pairs:
        measured = 0
        worst: float | None = None
        for pair in pairs:
            limit = LARGE_TEXT_THRESHOLD if pair["large_text"] else threshold
            ratio = contrast_ratio(pair["fg"], pair["bg"])
            passed = ratio + 1e-9 >= limit
            contrast["pairs"].append({
                "label": pair["label"],
                "ratio": round(ratio, 3),
                "threshold": limit,
                "pass": passed,
            })
            measured += 1
            worst = ratio if worst is None else min(worst, ratio)
            if not passed:
                violations.append({
                    "code": "CONTRAST_TOO_LOW",
                    "detail": f"{pair['label']} ratio={ratio:.2f} < {limit}",
                })
        contrast["status"] = "PASS" if measured and all(
            item["pass"] for item in contrast["pairs"]) else "FAIL"
        contrast["measured"] = measured
        contrast["min_ratio"] = round(worst, 3) if worst is not None else None

    structural_ok = not any(
        v["code"] in {"TOUCH_TARGET_TOO_SMALL", "MISSING_LABEL",
                      "BOUNDS_UNREADABLE", "CONTRAST_TOO_LOW"}
        for v in violations
    )
    return {
        "nodes": len(nodes),
        "clickable": len(clickable),
        "density_dpi": density_dpi,
        "min_dp": min_dp,
        "structural_ok": structural_ok,
        "contrast": contrast,
        "violations": violations,
        "violation_codes": sorted({v["code"] for v in violations}),
        # 结构通过 + 对比度未测 ≠ 整体通过；这是本工具的核心判定。
        "ok": structural_ok and contrast["status"] == "PASS",
    }


def load_colors(path: str | None) -> dict | None:
    if not path:
        return None
    return json.loads(Path(path).read_text(encoding="utf-8"))


def _extract_call_bodies(text: str, name: str) -> list[str]:
    """按括号配平取出 `name(...)` 的实参文本，避免被内层 `Color(...)` 截断。"""
    bodies: list[str] = []
    for match in re.finditer(rf"\b{name}\s*\(", text):
        depth = 1
        index = match.end()
        start = index
        while index < len(text) and depth:
            char = text[index]
            if char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
            index += 1
        if depth == 0:
            bodies.append(text[start:index - 1])
    return bodies


def _color_constants(text: str) -> dict[str, list[int]]:
    """文件内的 `val Name = Color(0xAARRGGBB)`（含 object 内的 val）。"""
    constants: dict[str, list[int]] = {}
    compose_builtin = {
        "White": [255, 255, 255],
        "Black": [0, 0, 0],
        "Transparent": [0, 0, 0],
    }
    for match in re.finditer(
            r"\bval\s+(\w+)\s*(?::\s*Color\s*)?=\s*Color\s*\(\s*0x([0-9A-Fa-f]{8})\s*\)",
            text):
        argb = int(match.group(2), 16)
        constants[match.group(1)] = [
            (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF,
        ]
    for match in re.finditer(
            r"\bval\s+(\w+)\s*(?::\s*Color\s*)?=\s*(?:[\w.]+\.)?(White|Black|Transparent)\b",
            text):
        constants[match.group(1)] = compose_builtin[match.group(2)]
    return constants


def theme_pairs_from_source(paths: list[Path]) -> list[dict]:
    """从 Kotlin 主题源码里提取颜色常量对。

    只处理明确写到同一 `lightColorScheme(...)` / `darkColorScheme(...)` 调用里的
    颜色常量（字面量或同文件 `val` 常量），配成 onSurface/surface、
    onBackground/background、onPrimary/primary、onError/error、
    onSecondaryContainer/secondaryContainer 等 Material 标准对。
    """
    pairs: list[dict] = []
    for path in paths:
        text = path.read_text(encoding="utf-8", errors="replace")
        constants = _color_constants(text)
        for scheme in ("lightColorScheme", "darkColorScheme"):
            for body in _extract_call_bodies(text, scheme):
                values: dict[str, list[int]] = {}
                literal = re.compile(
                    r"(\w+)\s*=\s*Color\s*\(\s*0x([0-9A-Fa-f]{8})\s*\)")
                named = re.compile(r"(\w+)\s*=\s*([A-Za-z_]\w*)\s*(?:,|$)")
                for colour in literal.finditer(body):
                    argb = int(colour.group(2), 16)
                    values[colour.group(1)] = [
                        (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF,
                    ]
                for colour in named.finditer(body):
                    if colour.group(2) in constants:
                        values[colour.group(1)] = constants[colour.group(2)]
                for base, foreground in (
                    ("surface", "onSurface"),
                    ("background", "onBackground"),
                    ("primary", "onPrimary"),
                    ("secondaryContainer", "onSecondaryContainer"),
                    ("surfaceVariant", "onSurfaceVariant"),
                    ("error", "onError"),
                    ("errorContainer", "onErrorContainer"),
                ):
                    if base in values and foreground in values:
                        pairs.append({
                            "label": f"{path.name}:{scheme}:{foreground}/{base}",
                            "fg": values[foreground],
                            "bg": values[base],
                        })
    return pairs


def _hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def _cmd_audit(args: argparse.Namespace) -> int:
    dump = Path(args.dump)
    try:
        xml_text = dump.read_text(encoding="utf-8", errors="replace")
        nodes = parse_dump(xml_text)
        colors = load_colors(args.colors)
    except (OSError, ET.ParseError, ValueError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}")
        print("EXIT=2")
        return 2

    result = audit(nodes, density_dpi=args.density_dpi, colors=colors,
                   min_dp=args.min_dp, threshold=args.threshold)
    result.update({
        "dump": str(dump),
        "dump_sha256": _hash_file(dump),
        "colors": args.colors,
        "colors_sha256": _hash_file(Path(args.colors)) if args.colors else None,
        "min_dp_source": args.min_dp,
    })
    if args.out:
        Path(args.out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.out).write_text(
            json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8")

    print(f"NODES={result['nodes']} CLICKABLE={result['clickable']}")
    print(f"STRUCTURAL={'PASS' if result['structural_ok'] else 'FAIL'}")
    print(f"CONTRAST={result['contrast']['status']}"
          + (f" ({result['contrast']['reason']})" if result['contrast']['reason'] else ""))
    for violation in result["violations"]:
        print(f"VIOLATION {violation['code']} {violation['detail']}")
    code = 1 if not result["structural_ok"] else 0
    print(f"OVERALL={'PASS' if result['ok'] else ('FAIL' if code else 'UNVERIFIED')}")
    print(f"EXIT={code}")
    return code


def _cmd_theme(args: argparse.Namespace) -> int:
    sources = [Path(p) for p in args.source]
    missing = [str(p) for p in sources if not p.exists()]
    if missing:
        print(f"ERROR: 缺少主题源码 {missing}")
        print("EXIT=2")
        return 2
    pairs = theme_pairs_from_source(sources)
    payload = {"pairs": pairs, "sources": [str(p) for p in sources]}
    if args.out:
        Path(args.out).parent.mkdir(parents=True, exist_ok=True)
        Path(args.out).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"PAIRS={len(pairs)}")
    failed = []
    for pair in pairs:
        limit = LARGE_TEXT_THRESHOLD if pair.get("large_text") else DEFAULT_THRESHOLD
        ratio = contrast_ratio(pair["fg"], pair["bg"])
        passed = ratio + 1e-9 >= limit
        if not passed:
            failed.append(pair["label"])
        print(f"{pair['label']} ratio={ratio:.2f} threshold={limit} "
              f"{'PASS' if passed else 'FAIL'}")
    code = 1 if (not pairs or failed) else 0
    print(f"EXIT={code}")
    return code


SERIAL_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$")


def _resolve_adb(explicit: str | None) -> str | None:
    if explicit:
        return explicit if Path(explicit).exists() else None
    found = shutil.which("adb")
    if found:
        return found
    for candidate in (
        r"C:\Android\Sdk\platform-tools\adb.exe",
        "/c/Android/Sdk/platform-tools/adb.exe",
    ):
        if Path(candidate).exists():
            return candidate
    return None


def _cmd_dump(args: argparse.Namespace) -> int:
    """从设备导出当前语义树（runbook §6 的命令形态）。

    安全约束：serial 只允许 `[A-Za-z0-9.:-]`，adb 走可执行文件解析，远端路径是
    常量，命令一律用参数列表（shell=False），不拼接 shell 字符串。
    """
    if not SERIAL_RE.match(args.serial):
        print("ERROR: serial 含非法字符")
        print("EXIT=2")
        return 2
    adb = _resolve_adb(args.adb)
    if not adb:
        print("ERROR: adb 不可用")
        print("EXIT=2")
        return 2
    remote = "/sdcard/a11y_dump.xml"
    try:
        subprocess.run([adb, "-s", args.serial, "shell", "uiautomator", "dump", remote],
                       capture_output=True, text=True, timeout=120, check=False)
        raw = subprocess.run([adb, "-s", args.serial, "exec-out", "cat", remote],
                             capture_output=True, timeout=120, check=False).stdout
    except (OSError, subprocess.SubprocessError) as exc:
        print(f"ERROR: {exc}")
        print("EXIT=2")
        return 2
    if b"<hierarchy" not in raw:
        print("ERROR: 设备未返回语义树")
        print("EXIT=2")
        return 2
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(raw)
    print(f"serial={args.serial}")
    print(f"out={out}")
    print(f"sha256={hashlib.sha256(raw).hexdigest()}")
    print("EXIT=0")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="I 域无障碍自动审计")
    sub = parser.add_subparsers(dest="command", required=True)

    dump_parser = sub.add_parser("dump", help="从设备导出当前语义树")
    dump_parser.add_argument("--serial", required=True)
    dump_parser.add_argument("--out", required=True)
    dump_parser.add_argument("--adb")
    dump_parser.set_defaults(func=_cmd_dump)

    audit_parser = sub.add_parser("audit", help="审计 uiautomator 语义树")
    audit_parser.add_argument("--dump", required=True)
    audit_parser.add_argument("--density-dpi", type=int, default=160)
    audit_parser.add_argument("--colors")
    audit_parser.add_argument("--min-dp", type=float, default=DEFAULT_MIN_DP)
    audit_parser.add_argument("--threshold", type=float, default=DEFAULT_THRESHOLD)
    audit_parser.add_argument("--out")
    audit_parser.set_defaults(func=_cmd_audit)

    theme_parser = sub.add_parser("theme", help="从主题源码生成对比度色对")
    theme_parser.add_argument("--source", action="append", required=True)
    theme_parser.add_argument("--out")
    theme_parser.set_defaults(func=_cmd_theme)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    sys.exit(main())
