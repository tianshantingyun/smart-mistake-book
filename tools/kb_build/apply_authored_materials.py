# -*- coding: utf-8 -*-
"""作者型材料受控落地器：把裁决产出的 13 键材料 JSONL 写进 staging 侧车。

## 它消灭的失败

`build.py` 停用后，"作者型材料"（候选区结构化结论、知识清单缺口补料）没有任何
**可重放**的入库通道：材料正文只存在于成品 sidecar 里，要加料只能手工改包，
而手工改的包不可重放——正是这套工具链当初要消灭的故障（`new_content.py` 为五三批
写过同一句立项理由）。本工具把"作者型材料 JSONL → staging 侧车"做成单通道：

- 字段级判据直接复用 `new_content` 的（13 键/3 值 type/恰好 1 条 PRIMARY/1–4 行/
  中文引号/标题不得是例题），不另写一份；
- **卷滚动与来源条目复用 `materialize` 的同一份实现**（选卷规则有"静默丢数据 /
  索引污染"两处踩坑史，抄第二份等于把坑也抄一份）；来源条目同样取最早时间戳，
  避免 KD-15「材料早于来源导入」的同类失败；
- 幂等按材料 slug：已存在且绑定一致 → 跳过；绑定不一致 → 拒整批；
- 只写 staging；成品目录只由 `promote.py` 写。

## 输入

`--materials <path.jsonl>`：每行一个 13 键材料对象（键序同 `MATERIAL_KEYS`）。
`sourceId` 必须是**已登记来源**（出现在任一现有 sidecar 的 sources 里）——
本工具不自造来源条目：来源登记是独立纪律，未登记的来源应当先走登记流程。

## 用法

    python tools/kb_build/apply_authored_materials.py --materials <path> --dry-run
    python tools/kb_build/apply_authored_materials.py --materials <path> --write
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import new_content as nc  # noqa: E402
from kb_build import pack_io  # noqa: E402
from kb_coverage import materialize as mz  # noqa: E402

MAX_TITLE, MAX_SUMMARY, MAX_APPLICABILITY = 240, 4000, 8000
MAX_CONTENT, MAX_BOUNDARY, MAX_LOCATOR = 32000, 8000, 2000


def load_materials(path: Path) -> list[dict]:
    if not path.exists():
        raise ValueError(f"材料文件不存在：{path}")
    out: list[dict] = []
    for index, line in enumerate(path.read_text(encoding="utf-8").splitlines()):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        try:
            out.append(json.loads(line))
        except json.JSONDecodeError as exc:
            raise ValueError(f"{path}[{index}]: JSON 解析失败 {exc}") from exc
    return out


def load_pack_nodes() -> set[tuple[str, str]]:
    pack = pack_io.load_json(pack_io.pack_path())
    return {(s, p["slug"]) for s, _t, p in pack_io.iter_points(pack)}


def validate(material: dict, index: int, known_nodes: set[tuple[str, str]],
             registered_sources: dict[str, dict]) -> list[str]:
    """逐条校验，返回错误列表（空 = 可写）。判据与 new_content 同源。"""
    tag = f"materials[{index}]"
    errors: list[str] = []
    if tuple(material) != nc.MATERIAL_KEYS:
        return [f"{tag}: 键集合或顺序不符，期望 {nc.MATERIAL_KEYS}，实际 {tuple(material)}"]
    slug = material["slug"]
    if not isinstance(slug, str) or not nc._SAFE_SLUG.fullmatch(slug):
        errors.append(f"{tag}: slug {slug!r} 不是 kebab 形式")
    subject = material["subject"]
    if subject not in nc.SUBJECT_CN:
        errors.append(f"{tag}: 未知科目 {subject!r}")
    if material["type"] not in nc.VALID_MATERIAL_TYPES:
        errors.append(f"{tag}: type {material['type']!r} 不在 {sorted(nc.VALID_MATERIAL_TYPES)}")
    if material["derivationKind"] != nc.REQUIRED_DERIVATION:
        errors.append(f"{tag}: derivationKind 必须是 {nc.REQUIRED_DERIVATION}")
    sid = material["sourceId"]
    if sid not in registered_sources:
        errors.append(f"{tag}: 来源未登记（{sid!r} 不在现有 sidecar 的 sources 里）——先走来源登记，不得自造 sourceId")
    elif registered_sources[sid].get("subject") not in (None, subject):
        errors.append(f"{tag}: 来源 {sid!r} 属 {registered_sources[sid].get('subject')}，与材料科目 {subject} 不符")
    for field in ("title", "summaryMarkdown", "applicabilityMarkdown",
                  "contentMarkdown", "boundaryMarkdown"):
        value = material.get(field)
        if not isinstance(value, str):
            errors.append(f"{tag}: {field} 不是字符串")
            continue
        try:
            nc._check_text(field, value, slug or tag, forbid_ascii_quote=True)
        except nc.NewContentError as exc:
            errors.append(f"{tag}: {exc}")
    content = material.get("contentMarkdown")
    if isinstance(content, str):
        lines = len(content.split("\n"))
        if not 1 <= lines <= 4:
            errors.append(f"{tag}: contentMarkdown 必须 1–4 行，实际 {lines} 行")
    caps = ((MAX_TITLE, "title"), (MAX_SUMMARY, "summaryMarkdown"),
            (MAX_APPLICABILITY, "applicabilityMarkdown"), (MAX_CONTENT, "contentMarkdown"),
            (MAX_BOUNDARY, "boundaryMarkdown"), (MAX_LOCATOR, "sourceLocator"))
    for cap, field in caps:
        value = material.get(field)
        if isinstance(value, str) and len(value) > cap:
            errors.append(f"{tag}: {field} 超过 {cap} 字")
    title = material.get("title") or ""
    if title.startswith("例") or "典例" in title:
        errors.append(f"{tag}: title 不得是例题（不以「例」开头、不含「典例」）")
    bindings = material.get("bindings")
    if not isinstance(bindings, list) or len(bindings) != 1 or bindings[0].get("role") != "PRIMARY":
        errors.append(f"{tag}: 必须恰好 1 条 PRIMARY 绑定，实际 {bindings!r}")
    else:
        node = bindings[0].get("knowledgeNodeId", "")
        parts = node.split(":")
        if len(parts) < 5 or (parts[-3].upper(), parts[-1]) not in known_nodes:
            errors.append(f"{tag}: 绑定悬空 {node[:70]}")
        elif parts[-3].upper() != subject:
            errors.append(f"{tag}: 绑定跨科（材料 {subject}，节点 {parts[-3].upper()}）")
    return errors


def plan(materials: list[dict]) -> dict:
    known_nodes = load_pack_nodes()
    registered = mz._existing_source_entries()
    errors: list[str] = []
    seen: set[str] = set()
    for index, material in enumerate(materials):
        slug = material.get("slug")
        if isinstance(slug, str):
            if slug in seen:
                errors.append(f"materials[{index}]: slug 重复 {slug!r}")
            seen.add(slug)
        errors.extend(validate(material, index, known_nodes, registered))
    return {"errors": errors, "count": len(materials), "known_nodes": known_nodes,
            "registered": registered}


def write(materials: list[dict]) -> dict:
    pl = plan(materials)
    if pl["errors"]:
        raise ValueError("材料校验未通过，拒绝写盘：\n  " + "\n  ".join(pl["errors"][:10]))
    state = mz._sidecar_state()
    existing: dict[str, str] = {}
    for doc in state["docs"].values():
        for mat in doc["materials"]:
            for binding in mat.get("bindings") or []:
                existing.setdefault(mat["slug"], binding["knowledgeNodeId"].split(":")[-1])
    changed: dict[Path, dict] = {}
    added = 0
    skipped = 0
    now = mz._now_ms()
    for material in materials:
        slug = material["slug"]
        target_node = material["bindings"][0]["knowledgeNodeId"].split(":")[-1]
        if slug in existing:
            if existing[slug] != target_node:
                raise ValueError(f"slug 撞车：{slug} 已绑 {existing[slug]}，本行目标 {target_node}")
            skipped += 1
            continue
        subject = material["subject"]
        target = mz._pick_sidecar(state, subject)
        doc = state["docs"][target]
        changed[target] = doc
        sid = material["sourceId"]
        if not any(s.get("sourceId") == sid for s in doc["sources"]):
            doc["sources"].append(dict(pl["registered"][sid]))
        doc["materials"].append({
            "slug": slug,
            "subject": subject,
            "type": material["type"],
            "title": material["title"].strip(),
            "summaryMarkdown": material["summaryMarkdown"].strip(),
            "applicabilityMarkdown": material["applicabilityMarkdown"].strip(),
            "contentMarkdown": material["contentMarkdown"].strip(),
            "boundaryMarkdown": material["boundaryMarkdown"].strip(),
            "derivationKind": material["derivationKind"],
            "sourceId": sid,
            "sourceLocator": material["sourceLocator"].strip(),
            "reviewedAtEpochMillis": now,
            "bindings": [{"knowledgeNodeId": material["bindings"][0]["knowledgeNodeId"],
                          "role": "PRIMARY"}],
        })
        mz._note_appended(state, target, doc["materials"][-1], subject)
        existing[slug] = target_node
        added += 1
    for path, doc in changed.items():
        pack_io.dump_json(doc, path)
    return {"added": added, "skipped": skipped,
            "sidecars": {p.name: len(d["materials"]) for p, d in changed.items()}}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--materials", required=True, type=Path)
    group = ap.add_mutually_exclusive_group()
    group.add_argument("--dry-run", action="store_true")
    group.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)
    materials = load_materials(args.materials)
    if args.write:
        stats = write(materials)
        print(stats)
        return 0
    pl = plan(materials)
    print(f"材料 {pl['count']} 条；错误 {len(pl['errors'])}")
    for err in pl["errors"][:10]:
        print("  -", err)
    if not pl["errors"]:
        print("（dry-run：校验通过，未写盘；加 --write 落盘）")
    return 0 if not pl["errors"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
