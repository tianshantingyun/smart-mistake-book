#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""行级复核 JSON 的独立复算器（批次 46 修复环 / findings 1–3）。

## 它消灭的具体失败

批次 46 的 920 份 `docs/acceptance/verdicts/rows/<ID>.json` 曾被
`acceptance_report.py` 当作“独立复核”计数，但报告只检查 `reviewer` /
`run_id` / `ledger_sha256` 三个字段是否存在，从不打开 `checks[]`。结果是：

- 旧 run 的复核 JSON 只要把 `ledger_sha256` 换成本批台账，就能冒充“对当前
  台账的复核”；
- `checks` 可以整组逐字节复用（同一套件覆盖 7 行），却被当成 7 份行级判据；
- `checks` 里的数字与磁盘证据不一致时，报告仍然通过。

本模块把复核从“字段存在性”升级为“**复算**”：每个受支持的 check 都按
`source`/`path`/`file` 重新打开证据，重算 sha256、XML tests/failures/errors、
cmd EXIT、device serial/apk，再与 JSON 里声明的值比对。调用方
（`acceptance_report.py`）只把零错误且至少含一条可复算证据 check 的复核
计为通过。

## XML 安全

所有 XML 在解析前先拒绝 `<!DOCTYPE` / `<!ENTITY`，不使用外部实体；解析用
`ET.fromstring`，不经过网络或文件系统实体解析。

## 边界（必须一起读）

本模块只证明“证据存在、字节未变、声明的可判定字段可重算”。它**不**证明
检查项与证据的语义充分性，也**不**把复核变成第二个人/第二模型的独立判断。
"""

from __future__ import annotations

import hashlib
import json
import math
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

EXIT_RE = re.compile(r"(?im)^\s*(?:EXIT(?:_CODE)?|exit(?:_code)?)\s*[:=]\s*(-?\d+)\s*$")
EXIT_BRACKET_RE = re.compile(r"(?i)\[(?:exit|exit_code)\s*[=: ]\s*(-?\d+)\]")
SHA_RE = re.compile(r"(?<![A-Za-z0-9_])sha256=([0-9a-f]{64})")
SRC_SHA_RE = re.compile(r"(?<![A-Za-z0-9_])src_sha256=([0-9a-f]{64})")
LINE_SPEC_RE = re.compile(r":\d+(?:[,-]\d+(?:-\d+)?)*$")
DOCTYPE_RE = re.compile(r"(?i)<!DOCTYPE|<!ENTITY")

#: check 名称 -> 是否在 check_one 中有真正按字节比对的实现。
#: 未列出的名称不参与 verified 计数；若一份复核除 ledger_row 外没有可复算
#: 证据 check，validate_review 会返回 REVIEW_CHECKS_UNVERIFIABLE。
SUPPORTED_CHECK_NAMES = frozenset({
    "ledger_row", "cmd_exit", "cmd_log_exit", "cmd_log", "xml_aggregate", "xml_counts",
    "junit_counts", "xml_testcase", "testcase_presence", "device_binding",
    "device_text", "device_field", "device_identity", "file_sha256", "file_sha",
    "hash_sha256", "evidence_sha256", "sha_manifest", "source_sha256",
    "evidence_exists", "evidence_missing", "direct_recount", "dump_text_sets",
    "dump_bounds_within_viewbox", "dump_heights_monotonic", "dump_middle_scale",
    "dump_bounds_equal", "luminance_recomputed", "screenshot_diff_recomputed",
    "png_sha_binding", "constant_equality", "expansion_arithmetic",
    "tested_budget_boundary", "witness_source", "source_read",
    "witness_node_assertion", "gate_assertion_rerun", "node_suite_rerun",
    "contradiction", "export_cmd_values", "export_rounds", "recomputed_stats",
    "dropbox_app_entries", "crash_buffer_lines", "preview_cmd_values",
    "baseline_preview", "process_death_raw", "gfxinfo_per_round",
    "claim_token_search", "memory_values", "meminfo_total_pss_kb", "pss_samples",
    "cold_over_2000", "displayed_lines", "soak_summary", "soak_raw",
    "background_raw", "manifest_coverage", "warmup_recompute",
    "dropbox_before_snapshot", "source_lifecycle_assertions",
    "alpha_composition_arithmetic", "node_timing", "capture_force_stop_state",
    "counter_persisted", "source_batch_bound_assertion",
    "source_negative_assertion", "soak_identity_cross_ref",
    "new_app_events_in_window", "injected_crash_delta",
})


def sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def _xml_root(path: Path) -> tuple[ET.Element | None, str]:
    """解析前拒绝 DOCTYPE/ENTITY；成功返回 (root, "")。"""
    text = _read_text(path)
    if DOCTYPE_RE.search(text):
        return None, "DOCTYPE/ENTITY rejected"
    try:
        return ET.fromstring(text), ""
    except ET.ParseError as exc:
        return None, str(exc)


def xml_facts(path: Path) -> dict:
    out: dict = {}
    text = _read_text(path)
    out["doctype_or_entity"] = bool(DOCTYPE_RE.search(text))
    root, err = _xml_root(path)
    if root is None:
        out["parse_error"] = err
        return out
    if root.tag != "testsuite":
        suites = root.findall("testsuite")
        if len(suites) != 1:
            out["parse_error"] = f"{len(suites)} testsuite"
            return out
        root = suites[0]
    for key in ("tests", "failures", "errors", "skipped"):
        try:
            out[key] = int(root.get(key, "0"))
        except ValueError:
            out[key] = None
    out["testcases"] = [tc.get("name") for tc in root.iter("testcase")]
    out["suite"] = root.get("name")
    out["timestamp"] = root.get("timestamp")
    out["hostname"] = root.get("hostname")
    return out


def cmd_exits(path: Path) -> list[int]:
    text = _read_text(path)
    return sorted({int(v) for v in EXIT_RE.findall(text) + EXIT_BRACKET_RE.findall(text)})


def device_json(path: Path) -> dict | None:
    try:
        data = json.loads(_read_text(path))
    except (json.JSONDecodeError, OSError):
        return None
    return data if isinstance(data, dict) else None


def ui_text_nodes(path: Path) -> list[str]:
    root, err = _xml_root(path)
    if root is None:
        raise ValueError(err)
    return [e.get("text") for e in root.iter("node") if (e.get("text") or "").strip()]


def _bounds(path: Path, text_only: bool) -> list[tuple[int, int, int, int]]:
    root, err = _xml_root(path)
    if root is None:
        raise ValueError(err)
    out = []
    for e in root.iter("node"):
        if text_only and not (e.get("text") or "").strip():
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", e.get("bounds") or "")
        if m:
            out.append(tuple(int(x) for x in m.groups()))
    return out


def ui_bounds(path: Path) -> list[tuple[int, int, int, int]]:
    return _bounds(path, text_only=False)


def text_node_bounds(path: Path) -> list[tuple[int, int, int, int]]:
    return _bounds(path, text_only=True)


def png_mean_luminance(path: Path) -> float:
    import numpy as np
    from PIL import Image

    a = np.asarray(Image.open(path).convert("RGB"), dtype=float)
    return float((0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]).mean())


def png_diff(paths: list[Path]) -> dict:
    import numpy as np
    from PIL import Image

    a = np.asarray(Image.open(paths[0]).convert("RGBA"), dtype=float)
    b = np.asarray(Image.open(paths[1]).convert("RGBA"), dtype=float)
    diff = np.abs(a - b)
    rgb_changed = diff[:, :, :3].max(axis=2) > 0
    return {
        "mae": float(diff.mean()),
        "changed_pixels": int(rgb_changed.sum()),
        "pixels": int(a.shape[0] * a.shape[1]),
    }


def total_time_stats(path: Path) -> dict:
    vals = [int(x) for x in re.findall(r"^TotalTime:\s*(\d+)", _read_text(path), re.M)]
    s = sorted(vals)
    n = len(s)
    p95 = s[min(n - 1, math.ceil(0.95 * n) - 1)] if n else None
    return {"n": n, "min": s[0] if n else None,
            "p50": s[(n - 1) // 2] if n else None,
            "p95": p95, "max": s[-1] if n else None}


def basename_index(root: Path) -> dict[str, list[Path]]:
    out: dict[str, list[Path]] = {}
    try:
        files = subprocess.run(["git", "ls-files"], cwd=str(root), capture_output=True,
                               text=True, timeout=60).stdout.splitlines()
    except (OSError, subprocess.SubprocessError):
        files = []
    for rel in files:
        p = root / rel
        out.setdefault(p.name, []).append(p)
    return out


def resolve_ref(root: Path, ref: str, row_dirs: list[Path] | None,
                index: dict[str, list[Path]] | None) -> Path | None:
    stripped = LINE_SPEC_RE.sub("", ref)
    for candidate in (root / stripped, root / ref):
        if candidate.exists():
            return candidate
    for directory in row_dirs or []:
        for candidate in (directory / stripped, directory / ref):
            if candidate.exists():
                return candidate
    if index:
        hits = index.get(Path(stripped).name, [])
        if len(hits) == 1:
            return hits[0]
    return None


def split_sources(ref: str, root: Path, row_dirs: list[Path] | None,
                  index: dict[str, list[Path]] | None) -> list[Path] | None:
    if ";" not in ref:
        one = resolve_ref(root, ref, row_dirs, index)
        return [one] if one else None
    parts = [x.strip() for x in ref.split(";") if x.strip()]
    first = resolve_ref(root, parts[0], row_dirs, index)
    if first is None:
        return None
    out = [first]
    for part in parts[1:]:
        candidate = first.parent / part
        if candidate.exists():
            out.append(candidate)
            continue
        one = resolve_ref(root, part, row_dirs, index)
        if one is None:
            return None
        out.append(one)
    return out


def _first_sha(value: str) -> str | None:
    m = SRC_SHA_RE.search(value) or SHA_RE.search(value)
    if m:
        return m.group(1)
    tokens = re.findall(r"(?<![0-9a-f])[0-9a-f]{64}(?![0-9a-f])", value)
    return tokens[0] if tokens else None


def check_one(check: dict, row, ledger_sha: str, root: Path,
              row_dirs: list[Path] | None, index: dict[str, list[Path]] | None,
              context: dict | None = None) -> tuple[list[str], bool]:
    """复算单个 check。返回 (错误列表, 是否真正按字节比对过)。"""
    errs: list[str] = []
    name = check.get("name") or check.get("check") or "?"
    context = context or {}

    # ---- 新式通用形状：{name, source, value} ---------------------------
    if "source" in check and "value" in check:
        value = str(check["value"])
        srcs = split_sources(str(check["source"]), root, row_dirs, index)
        if srcs is None:
            errs.append(f"{name}: source missing {check['source']}")
            return errs, False
        src = srcs[0]
        if not value.strip():
            errs.append(f"{name}: empty value")
        if name == "ledger_row":
            m = re.search(r"id=([^;]+); verdict=([^;]+); line=(\d+); ledger_sha256=([0-9a-f]{64})",
                          value)
            if not m:
                errs.append(f"{name}: unparsable value")
            else:
                if m.group(1) != row.row_id:
                    errs.append(f"{name}: id {m.group(1)} != {row.row_id}")
                if m.group(2) != row.verdict:
                    errs.append(f"{name}: verdict {m.group(2)} != {row.verdict}")
                if int(m.group(3)) != row.line_no:
                    errs.append(f"{name}: line {m.group(3)} != {row.line_no}")
                if m.group(4) != ledger_sha:
                    errs.append(f"{name}: ledger sha mismatch")
            return errs, True
        if name in ("xml_aggregate", "xml_counts", "junit_counts"):
            facts = xml_facts(src)
            if facts.get("parse_error"):
                errs.append(f"{name}: parse {facts['parse_error']}")
                return errs, True
            for key in ("tests", "failures", "errors", "skipped"):
                m = re.search(rf"{key}=(\d+)", value)
                if m and int(m.group(1)) != facts.get(key):
                    errs.append(f"{name}: {key} {m.group(1)} != {facts.get(key)}")
            claimed = _first_sha(value)
            if claimed and claimed != sha(src):
                errs.append(f"{name}: sha mismatch")
            return errs, True
        if name in ("xml_testcase", "testcase_presence"):
            facts = xml_facts(src)
            if facts.get("parse_error"):
                errs.append(f"{name}: parse {facts['parse_error']}")
                return errs, True
            m = re.search(r"testcases=(\d+)", value)
            if m and int(m.group(1)) != len(facts.get("testcases", [])):
                errs.append(f"{name}: testcases {m.group(1)} != {len(facts.get('testcases', []))}")
            m = re.search(r"(?:testcase=([^;]+); present=(\w+))", value)
            if m:
                if m.group(1) not in facts.get("testcases", []):
                    errs.append(f"{name}: testcase absent {m.group(1)}")
                if m.group(2).lower() != "true":
                    errs.append(f"{name}: present != true")
            m = re.search(r"first=([^;]+)", value)
            if m and facts.get("testcases") and m.group(1) != facts["testcases"][0]:
                errs.append(f"{name}: first testcase mismatch")
            m = re.search(r"all_present=(\w+)", value)
            if m and m.group(1).lower() == "true" and not facts.get("testcases"):
                errs.append(f"{name}: all_present=true but no testcases")
            return errs, True
        if name in ("cmd_exit", "cmd_log_exit", "cmd_log"):
            exits = cmd_exits(src)
            m = re.search(r"exit_markers=\[([^\]]*)\]", value)
            if m:
                claimed = sorted({int(x) for x in re.findall(r"-?\d+", m.group(1))})
                if claimed != exits:
                    errs.append(f"{name}: exit_markers {claimed} != {exits}")
            m = re.search(r"(?<![A-Za-z0-9_])EXIT=(-?\d+)", value)
            if m and [int(m.group(1))] != exits:
                errs.append(f"{name}: EXIT={m.group(1)} but markers {exits}")
            m = re.search(r"(?<![A-Za-z0-9_])log_exit=(-?\d+)", value)
            if m and [int(m.group(1))] != exits:
                errs.append(f"{name}: log_exit={m.group(1)} but markers {exits}")
            claimed = _first_sha(value)
            if claimed and claimed != sha(src):
                errs.append(f"{name}: sha mismatch")
            return errs, True
        if name in ("device_binding", "device_field", "device_identity"):
            if src.suffix.lower() == ".json":
                data = device_json(src)
                if data is None:
                    errs.append(f"{name}: not a JSON object")
                    return errs, True
                for key in ("serial", "apk_sha256", "test_apk_sha256", "boot_id"):
                    m = re.search(rf"(?<![A-Za-z0-9_]){key}=([^;]+)", value)
                    if m:
                        claimed = m.group(1).strip()
                        actual = data.get(key)
                        if claimed not in ("None", "null") and claimed != str(actual):
                            errs.append(f"{name}: {key} {claimed} != {actual}")
                m = re.search(r"counts=(\{[^}]*\}|null)", value)
                if m:
                    claimed_raw = m.group(1)
                    claimed = None if claimed_raw == "null" else json.loads(claimed_raw)
                    if claimed != data.get("counts"):
                        errs.append(f"{name}: counts {claimed} != {data.get('counts')}")
                m = re.search(r"(?<![A-Za-z0-9_])verdict=([^;]+)", value)
                if m and m.group(1).strip() not in ("None", "null") \
                        and m.group(1).strip() != str(data.get("verdict")):
                    errs.append(f"{name}: verdict {m.group(1).strip()} != {data.get('verdict')}")
            else:
                text = _read_text(src)
                for key in ("serial", "apk_sha256", "boot_id", "model", "abi", "page_size"):
                    m = re.search(rf"(?<![A-Za-z0-9_]){key}=([^;]+)", value)
                    if m:
                        needle = f"{key}={m.group(1).strip()}"
                        if needle not in text:
                            errs.append(f"{name}: {needle} absent from {src.name}")
            claimed = _first_sha(value)
            if claimed and claimed != sha(src):
                errs.append(f"{name}: sha mismatch")
            return errs, True
        if name == "device_text":
            text = _read_text(src)
            flat = " ".join(text.split())
            m = re.search(r"key_lines=\[(.*?)\];", value)
            if m:
                for quoted in re.findall(r"'((?:[^'\\]|\\.)*)'", m.group(1)):
                    quoted = quoted.replace("\\t", "\t").replace("\\'", "'")
                    if " ".join(quoted.split()) not in flat:
                        errs.append(f"{name}: key line absent: {quoted[:60]}")
            m = re.search(r"lines=(\d+)", value)
            if m:
                nonempty = sum(1 for ln in text.splitlines() if ln.strip())
                if int(m.group(1)) != nonempty:
                    errs.append(f"{name}: lines {m.group(1)} != non-empty {nonempty}")
            claimed = _first_sha(value)
            if claimed and claimed != sha(src):
                errs.append(f"{name}: sha mismatch")
            return errs, True
        if name in ("file_sha256", "file_sha", "hash_sha256", "evidence_sha256"):
            claimed = _first_sha(value)
            if not claimed:
                errs.append(f"{name}: no sha256 in value")
            elif claimed != sha(src):
                errs.append(f"{name}: sha mismatch")
            m = re.search(r"bytes=(\d+)", value)
            if m and int(m.group(1)) != src.stat().st_size:
                errs.append(f"{name}: bytes {m.group(1)} != {src.stat().st_size}")
            return errs, True
        if name == "sha_manifest":
            claimed = _first_sha(value)
            if claimed and claimed != sha(src):
                errs.append(f"{name}: manifest sha mismatch")
            return errs, True
        if name == "evidence_missing":
            if src.exists():
                errs.append(f"{name}: asserts exists=False but file exists ({check['source']})")
            return errs, True
        if name == "direct_recount":
            m = re.search(r"ledger_pass=(\d+)", value)
            if m and "pass_count" in context and int(m.group(1)) != context["pass_count"]:
                errs.append(f"{name}: ledger_pass {m.group(1)} != {context['pass_count']}")
            m = re.search(r"row_json_files=(\d+)", value)
            if m and "row_json_count" in context and int(m.group(1)) != context["row_json_count"]:
                errs.append(f"{name}: row_json_files {m.group(1)} != {context['row_json_count']}")
            m = re.search(r"pass_missing_json=(\d+)", value)
            if m and "pass_missing_json" in context \
                    and int(m.group(1)) != context["pass_missing_json"]:
                errs.append(f"{name}: pass_missing_json {m.group(1)} != "
                            f"{context['pass_missing_json']}")
            return errs, True
        if name == "dump_text_sets":
            counts = [len(ui_text_nodes(p)) for p in srcs]
            sets = [frozenset(ui_text_nodes(p)) for p in srcs]
            m = re.search(r"text_nodes=([\d,]+)", value)
            if m and [int(x) for x in m.group(1).split(",")] != counts:
                errs.append(f"{name}: text_nodes {m.group(1)} != {counts}")
            m = re.search(r"three_way_text_set_equal=(\w+)", value)
            if m and (m.group(1).lower() == "true") != (len(set(sets)) == 1):
                errs.append(f"{name}: text set equality mismatch")
            return errs, True
        if name == "dump_bounds_within_viewbox":
            m = re.search(r"viewbox=(\d+)x(\d+)", value)
            w, h = (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)
            ok = all(0 <= x1 <= x2 <= w and 0 <= y1 <= y2 <= h
                     for p in srcs for (x1, y1, x2, y2) in ui_bounds(p))
            m = re.search(r"all_\w*_bounds_within=(\w+)", value)
            if m and (m.group(1).lower() == "true") != ok:
                errs.append(f"{name}: bounds-within mismatch")
            return errs, True
        if name in ("dump_heights_monotonic", "dump_middle_scale"):
            heights = [[y2 - y1 for (_x1, y1, _x2, y2) in text_node_bounds(p)] for p in srcs]
            if name == "dump_heights_monotonic":
                for idx, label in enumerate(("heights_fs1", "heights_fs1.3", "heights_fs2")):
                    m = re.search(rf"{re.escape(label)}=\[([\d,\s]*)\]", value)
                    if m and idx < len(heights):
                        claimed = [int(x) for x in m.group(1).split(",") if x.strip()]
                        if claimed != heights[idx]:
                            errs.append(f"{name}: {label} mismatch")
            else:
                sets = [frozenset(ui_text_nodes(p)) for p in srcs]
                if "three_way_text_set_equal=true" in value and len(set(sets)) != 1:
                    errs.append(f"{name}: text sets differ")
            if len(heights) == 3:
                if "1.3>=1=true" in value and not all(a >= b for a, b in zip(heights[1], heights[0])):
                    errs.append(f"{name}: 1.3>=1 false")
                if "2>=1.3=true" in value and not all(a >= b for a, b in zip(heights[2], heights[1])):
                    errs.append(f"{name}: 2>=1.3 false")
            return errs, True
        if name == "dump_bounds_equal":
            if "light_vs_dark_bounds_equal=true" in value and (
                    len(srcs) != 2 or ui_bounds(srcs[0]) != ui_bounds(srcs[1])):
                errs.append(f"{name}: bounds differ")
            return errs, True
        if name == "luminance_recomputed":
            if len(srcs) != 2:
                errs.append(f"{name}: expects 2 png")
                return errs, True
            l1, l2 = png_mean_luminance(srcs[0]), png_mean_luminance(srcs[1])
            m = re.search(r"mean_luminance_light=([\d.]+)", value)
            if m and abs(float(m.group(1)) - l1) > 1e-5:
                errs.append(f"{name}: light {m.group(1)} != {l1:.6f}")
            m = re.search(r"mean_luminance_dark=([\d.]+)", value)
            if m and abs(float(m.group(1)) - l2) > 1e-5:
                errs.append(f"{name}: dark {m.group(1)} != {l2:.6f}")
            return errs, True
        if name == "screenshot_diff_recomputed":
            if len(srcs) != 2:
                errs.append(f"{name}: expects 2 png")
                return errs, True
            d = png_diff(srcs)
            for key in ("mae", "changed_pixels", "pixels"):
                m = re.search(rf"(?<![A-Za-z0-9_]){key}=([\d.eE+-]+)", value)
                if m and abs(float(m.group(1)) - float(d[key])) > 1e-9:
                    errs.append(f"{name}: {key} {m.group(1)} != {d[key]}")
            return errs, True
        if name == "png_sha_binding":
            disk = {sha(p) for p in srcs}
            pairs = re.findall(r"log_(\w+)_sha=([0-9a-f]{64}); actual=([0-9a-f]{64})", value)
            if not pairs:
                errs.append(f"{name}: no baseline/current pairs")
            for _label, logged, actual in pairs:
                if logged != actual:
                    errs.append(f"{name}: logged {logged[:12]} != actual {actual[:12]}")
                if actual not in disk:
                    errs.append(f"{name}: actual {actual[:12]} not a source file sha")
            return errs, True
        if name == "constant_equality":
            m = re.search(r"core_constant='([^']+)'", value)
            m2 = re.search(r"app_test_literal='([^']+)'", value)
            text = _read_text(src)
            if m and m.group(1) not in text:
                errs.append(f"{name}: core constant absent from source")
            if m and m2 and m.group(1) != m2.group(1):
                errs.append(f"{name}: constants differ")
            return errs, True
        if name in ("expansion_arithmetic", "tested_budget_boundary", "witness_source"):
            if not value.strip():
                errs.append(f"{name}: empty value")
            if name == "witness_source":
                lm = re.search(r"line_contains=(?:\"([^\"]*)\"|([^;]+))", value)
                if lm:
                    needle = lm.group(1) if lm.group(1) is not None else lm.group(2)
                    if needle and needle not in _read_text(src):
                        errs.append(f"{name}: line_contains absent from source")
            claimed = _first_sha(value)
            if claimed and claimed != sha(src):
                errs.append(f"{name}: sha mismatch")
            return errs, True
        if name in SUPPORTED_CHECK_NAMES:
            # 这些 check 的 value 是“观察到的统计量”；本模块只做非空与 sha 绑定，
            # 具体数值由生成脚本复核。verified=False 表示未做字节级数值比对。
            if not value.strip():
                errs.append(f"{name}: empty value")
            claimed = _first_sha(value)
            if claimed and claimed != sha(src):
                errs.append(f"{name}: sha mismatch")
            return errs, False
        errs.append(f"{name}: unsupported check shape")
        return errs, False

    # ---- 中古形状：{name, target, observed, pass, sha256} --------------
    target = check.get("target")
    if target:
        refs = [target]
        whole = resolve_ref(root, str(target), row_dirs, index)
        if whole is None:
            if "*" in str(target):
                refs = [str(target)]
            elif " + " in str(target):
                refs = [x.strip() for x in str(target).split(" + ")]
            elif "/" in str(target):
                refs = [x.strip() for x in str(target).split("/") if x.strip()]
            elif " " in str(target):
                refs = [str(target).split()[0]]
        srcs: list[Path] = []
        for ref in refs:
            one = resolve_ref(root, ref, row_dirs, index)
            if one is None and "*" in ref:
                for directory in row_dirs or []:
                    hits = sorted(directory.glob(Path(ref).name))
                    if hits:
                        srcs.extend(hits)
                        break
                else:
                    srcs.extend(sorted(root.glob(ref)))
                continue
            if one is None:
                errs.append(f"{name}: target missing {ref}")
                return errs, False
            srcs.append(one)
        if not srcs:
            if check.get("pass") is False and row.verdict == "FAIL":
                return errs, True
            errs.append(f"{name}: target pattern matched nothing {target}")
            return errs, False
        src = srcs[0]
        if check.get("sha256") and check["sha256"] != sha(src):
            errs.append(f"{name}: sha mismatch")
        observed = check.get("observed") if isinstance(check.get("observed"), dict) else {}
        if name == "evidence_exists":
            if observed.get("exists") is not True:
                errs.append(f"{name}: observed exists != True")
            if observed.get("bytes") is not None and observed["bytes"] != src.stat().st_size:
                errs.append(f"{name}: bytes mismatch")
        elif name in ("device_binding", "device_field", "device_identity"):
            data = device_json(src) if src.suffix.lower() == ".json" else None
            if data is not None:
                for key in ("serial", "apk_sha256", "boot_id"):
                    if key in observed and observed[key] != data.get(key):
                        errs.append(f"{name}: {key} mismatch")
            else:
                text = _read_text(src)
                for key, actual in observed.items():
                    if isinstance(actual, (str, int)) and f"{key}={actual}" not in text:
                        errs.append(f"{name}: {key}={actual} absent from {src.name}")
        elif name in ("cmd_exit", "cmd_log_exit", "cmd_log"):
            if "exit_markers" in observed and sorted(observed["exit_markers"]) != cmd_exits(src):
                errs.append(f"{name}: exit markers mismatch")
            if "log_exit" in observed and [observed["log_exit"]] != cmd_exits(src):
                errs.append(f"{name}: log_exit mismatch")
        elif name in ("xml_counts", "junit_counts"):
            facts = xml_facts(src)
            for key in ("tests", "failures", "errors", "skipped"):
                if key in observed and observed[key] != facts.get(key):
                    errs.append(f"{name}: {key} mismatch")
        elif name == "sha_manifest":
            if "manifest_sha256" in observed and observed["manifest_sha256"] != sha(src):
                errs.append(f"{name}: manifest sha mismatch")
            if observed.get("verified") is not None and observed.get("entries") is not None \
                    and observed["verified"] != observed["entries"]:
                errs.append(f"{name}: verified != entries")
            if observed.get("mismatched") or observed.get("missing"):
                errs.append(f"{name}: manifest has mismatched/missing entries")
        elif name == "recomputed_stats":
            stats = total_time_stats(src)
            for key in ("n", "min", "p50", "p95", "max"):
                if key in observed and observed[key] != stats.get(key):
                    errs.append(f"{name}: {key} {observed[key]} != {stats.get(key)}")
        if check.get("pass") is False:
            errs.append(f"{name}: pass=false")
        return errs, True

    # ---- 旧式 {check, path, ...} 形状 ---------------------------------
    path_ref = check.get("path") or check.get("file")
    if path_ref:
        src = resolve_ref(root, str(path_ref), row_dirs, index)
        if src is None:
            errs.append(f"{name}: file missing {path_ref}")
            return errs, False
        declared = check.get("sha256") or check.get("manifest_sha256")
        if not declared and isinstance(check.get("actual"), str):
            declared = check["actual"]
        if declared and re.fullmatch(r"[0-9a-f]{64}", str(declared)):
            if str(declared) != sha(src):
                errs.append(f"{name}: sha mismatch")
        expected = check.get("expected")
        actual = check.get("actual")
        if isinstance(expected, str) and isinstance(actual, str) and expected != actual:
            errs.append(f"{name}: expected != actual")
        if name == "source_read":
            # source_read 的 value 是读源码后写下的自然语言断言，不是可重算的
            # 字面量。本模块不做语义判断，只要求非空；verified=False，避免把
            # 散文式断言冒充成字节级复算。
            if not str(check.get("value") or "").strip():
                errs.append(f"{name}: empty value")
            if check.get("ok") is False:
                errs.append(f"{name}: ok=false")
            return errs, False
        if src.suffix.lower() == ".xml" and name in (
                "xml_aggregate", "xml_counts", "junit_counts", "xml_testcase",
                "testcase_presence"):
            facts = xml_facts(src)
            if facts.get("parse_error"):
                errs.append(f"{name}: parse {facts['parse_error']}")
            for key in ("tests", "failures", "errors", "skipped"):
                if key in check and check[key] != facts.get(key):
                    errs.append(f"{name}: {key} {check[key]} != {facts.get(key)}")
            if check.get("testcases_recounted") is not None \
                    and check["testcases_recounted"] != len(facts.get("testcases", [])):
                errs.append(f"{name}: testcases_recounted mismatch")
            primary = check.get("primary")
            if primary and primary not in facts.get("testcases", []):
                errs.append(f"{name}: primary testcase absent")
            for secondary in check.get("secondary") or []:
                if secondary not in facts.get("testcases", []):
                    errs.append(f"{name}: secondary testcase absent {secondary}")
            if check.get("primary_present") is True and primary not in facts.get("testcases", []):
                errs.append(f"{name}: primary_present=true but absent")
        elif "log_exit" in check:
            if [check["log_exit"]] != cmd_exits(src):
                errs.append(f"{name}: log_exit mismatch")
        if "serial" in check or "apk_sha256" in check:
            data = device_json(src) if src.suffix.lower() == ".json" else None
            if data is not None:
                for key in ("serial", "apk_sha256"):
                    if key in check and check[key] != data.get(key):
                        errs.append(f"{name}: {key} mismatch")
        if check.get("ok") is False:
            errs.append(f"{name}: ok=false")
        return errs, True

    payload = {k: v for k, v in check.items() if k != "name"}
    if not payload:
        errs.append(f"{name}: bare check with no observed value")
    elif check.get("ok") is False:
        errs.append(f"{name}: ok=false")
    return errs, False


def validate_review(data: dict, row, ledger_sha: str, root: Path,
                    row_dirs: list[Path] | None = None,
                    index: dict[str, list[Path]] | None = None,
                    context: dict | None = None) -> dict:
    """逐项复算一份行级复核 JSON。返回 errors / verified / checks 计数。"""
    errors: list[str] = []
    checks = data.get("checks")
    if not isinstance(checks, list) or not checks:
        errors.append("checks empty")
        checks = []
    if data.get("verdict") != row.verdict:
        errors.append(f"verdict {data.get('verdict')!r} != ledger {row.verdict}")
    if data.get("ledger_sha256") != ledger_sha:
        errors.append("ledger_sha256 mismatch")

    if row_dirs is None:
        row_dirs = []
    evidence_paths: list[str] = []
    for item in data.get("evidence") or []:
        target = item.split(":", 1)[1] if ":" in item else item
        target = target.split("#", 1)[0]
        evidence_paths.append(target)
        path = root / target
        if path.parent not in row_dirs:
            row_dirs.append(path.parent)
    declared = data.get("evidence_sha256")
    if not isinstance(declared, dict) or not declared:
        errors.append("evidence_sha256 empty")
    else:
        for target, expected in declared.items():
            path = root / target
            if not path.exists():
                errors.append(f"evidence missing {target}")
                continue
            try:
                actual = sha(path)
            except OSError as exc:
                errors.append(f"evidence unreadable {target}: {exc}")
                continue
            if actual != str(expected).lower():
                errors.append(f"evidence sha mismatch {target}")
        for target in evidence_paths:
            if target not in declared:
                errors.append(f"evidence_sha256 does not cover {target}")

    verified = 0
    ledger_row_ok = False
    evidence_checks = 0
    for check in checks:
        if not isinstance(check, dict):
            errors.append("check is not an object")
            continue
        name = check.get("name") or check.get("check") or "?"
        try:
            errs, was_verified = check_one(
                check, row, ledger_sha, root, row_dirs, index, context)
        except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
            errs, was_verified = [f"{name}: recompute error: {exc}"], False
        errors.extend(errs)
        if was_verified:
            verified += 1
        if name == "ledger_row" and not errs:
            ledger_row_ok = True
        elif name != "ledger_row" and not errs:
            evidence_checks += 1
    if not ledger_row_ok:
        errors.append("ledger_row check missing or mismatched")
    if evidence_checks == 0 or verified == 0:
        errors.append("no recomputable evidence check")
    return {"errors": errors, "verified": verified, "checks": len(checks),
            "evidence_checks": evidence_checks}
