# -*- coding: utf-8 -*-
"""把 1a 裁决表（candidate-merge/verdicts/*.csv）确定性合并成写入窗口要吃的四类产物。

## 它消灭的失败

45 个裁决批各写各的 CSV；若不收拢，写入窗口只能手工抄表——抄错一行就是一条悬空绑定
或一个假节点（旧通道 build.py 停用前正是"手工改包不可重放"的标本）。本工具把
"裁决表 → 建点行 / 主题行 / 作者型材料 / 证据账"这一步做成确定性单通道：

- 材料 slug 由来源条目 idx 派生（`cmp-<科3>-<idx:05d>`；材料层沿用候选 slug），
  同批重跑逐字节一致；
- 节点 slug 按包内既有风格派生（非字母数字/汉字 → `-`，折叠），科内查重后加序号；
- `sourceId` 一律走登记表 → 侧车形态的既有变换（`:`→`-`、追加 `:<科小写>`），
  **并且必须在包内已登记**，否则整条进隔离队列（不得自造来源）；
- 材料先过 `apply_authored_materials` 的同一份校验；不过的进 `quarantine.csv` 附原因；
- 新点的父主题解析：能精确落到既有主题链的才出 `new_points_rows.csv`，
  其余进 `placement_review.csv` 交一次裁决（自由文本的归属不做机械猜测）。

## 产物（`knowledge-production/candidate-merge/`）

`authored_materials.jsonl`（13 键）、`new_points_rows.csv`、`topic_create_rows.csv`、
`placement_review.csv`、`coverage_register.csv`（COVERED 证据）、`rejects.csv`、
`quarantine.csv`、`summary.json`。

## 用法

    python tools/kb_build/merge_candidate_verdicts.py            # dry-run 报数
    python tools/kb_build/merge_candidate_verdicts.py --write
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import apply_authored_materials as A  # noqa: E402
from kb_build import new_content as nc  # noqa: E402
from kb_build import pack_io  # noqa: E402

VERDICTS = Path("knowledge-production") / "candidate-merge" / "verdicts"
OUT_DIR = Path("knowledge-production") / "candidate-merge"
DIR_COLS = ["idx", "subject", "name", "verdict", "node_slug", "point_name", "kind",
            "parent_hint", "material_title", "material_type", "material_summary",
            "material_content", "material_boundary", "evidence", "reason"]
MAT_COLS = ["idx", "slug", "subject", "verdict", "node_slug", "covering_material_slug",
            "adjudicated_type", "material_title", "material_content", "material_boundary",
            "evidence", "reason"]
SUBJ3 = {"MATH": "mat", "PHYSICS": "phy", "CHEMISTRY": "che", "BIOLOGY": "bio"}
_STRIP = re.compile(r"[^0-9A-Za-z\u4e00-\u9fff]+")
VALID_KINDS = {"CONCEPT", "PROCEDURE", "REASONING", "REPRESENTATION", "EXPERIMENT"}


def sidecar_source_id(registry_id: str, subject: str) -> str:
    """登记表 id → 侧车形态（既有唯一变换：`:`→`-`，追加 `:<科小写>`）。"""
    return registry_id.replace(":", "-") + f":{subject.lower()}"


def point_slug(name: str) -> str:
    """节点 slug 派生：非字母数字/汉字 → `-`，折叠并去首尾（与包内既有风格一致）。"""
    return _STRIP.sub("-", (name or "").strip()).strip("-")


def load_verdicts(verdicts_dir: Path) -> tuple[list[dict], list[dict], list[str]]:
    dir_rows: list[dict] = []
    mat_rows: list[dict] = []
    errors: list[str] = []
    for path in sorted(verdicts_dir.glob("*.csv")):
        with path.open(encoding="utf-8-sig", newline="") as fh:
            reader = csv.DictReader(fh)
            rows = list(reader)
            cols = reader.fieldnames or []
        is_dir = path.name.startswith("dir_")
        expected = DIR_COLS if is_dir else MAT_COLS
        if cols != expected:
            errors.append(f"{path.name}: 表头不符（{cols}）")
            continue
        for row in rows:
            row["_file"] = path.name
            if is_dir:
                dir_rows.append(row)
            else:
                mat_rows.append(row)
    return dir_rows, mat_rows, errors


def material_from_dir_row(row: dict, node_slug: str) -> dict:
    subject = row["subject"]
    sid = f"registry-kb-{subject.lower()}-knowledge-list:{subject.lower()}"
    return {
        "slug": f"cmp-{SUBJ3[subject]}-{int(row['idx']):05d}",
        "subject": subject,
        "type": (row.get("material_type") or "").strip(),
        "title": (row.get("material_title") or "").strip(),
        "summaryMarkdown": (row.get("material_summary") or "").strip(),
        "applicabilityMarkdown": (row.get("material_summary") or "").strip(),
        "contentMarkdown": (row.get("material_content") or "").replace("\\n", "\n").strip(),
        "boundaryMarkdown": (row.get("material_boundary") or "").strip(),
        "derivationKind": nc.REQUIRED_DERIVATION,
        "sourceId": sid,
        "sourceLocator": f"知识清单（学生版） {row.get('_source_file') or ''} · {row['name']}".strip(),
        "reviewedAtEpochMillis": 0,
        "bindings": [{"knowledgeNodeId":
                      f"kb:moe-2025-four-subjects-v1:{subject.lower()}:atomic:{node_slug}",
                      "role": "PRIMARY"}],
    }


def material_from_mat_row(row: dict, candidate: dict, node_slug: str) -> dict:
    subject = row["subject"]
    sid = sidecar_source_id(candidate.get("sourceId") or "", subject)
    return {
        "slug": candidate["slug"],
        "subject": subject,
        "type": (row.get("adjudicated_type") or candidate.get("type") or "").strip(),
        "title": (row.get("material_title") or candidate.get("title") or "").strip(),
        "summaryMarkdown": (candidate.get("summaryMarkdown") or "").strip(),
        "applicabilityMarkdown": (candidate.get("applicabilityMarkdown") or "").strip(),
        "contentMarkdown": (row.get("material_content") or candidate.get("contentMarkdown") or "")
        .replace("\\n", "\n").strip(),
        "boundaryMarkdown": (row.get("material_boundary") or candidate.get("boundaryMarkdown") or "")
        .strip(),
        "derivationKind": nc.REQUIRED_DERIVATION,
        "sourceId": sid,
        "sourceLocator": (candidate.get("sourceLocator") or "").strip(),
        "reviewedAtEpochMillis": 0,
        "bindings": [{"knowledgeNodeId":
                      f"kb:moe-2025-four-subjects-v1:{subject.lower()}:atomic:{node_slug}",
                      "role": "PRIMARY"}],
    }


def build(root: Path, verdicts_dir: Path | None = None,
          entries_path: Path | None = None) -> dict:
    verdicts_dir = verdicts_dir or (root / VERDICTS)
    entries_path = entries_path or (root / OUT_DIR / "mat_entries.jsonl")
    dir_rows, mat_rows, errors = load_verdicts(verdicts_dir)
    pack = pack_io.load_json(pack_io.pack_path())
    nodes: dict[tuple[str, str], dict] = {}
    topic_slugs: dict[str, dict[str, str]] = {}
    for subject, topic, point in pack_io.iter_points(pack):
        nodes[(subject, point["slug"])] = point
    for subject_doc in pack.get("subjects") or []:
        subject = subject_doc.get("subject")
        mapping: dict[str, str] = {}
        for topic in subject_doc.get("topics") or []:
            mapping[topic.get("name") or ""] = topic.get("slug") or ""
            mapping[topic.get("slug") or ""] = topic.get("slug") or ""
        topic_slugs[subject] = mapping
    registered = A.mz._existing_source_entries()

    materials: list[dict] = []
    new_points: list[dict] = []
    coverage: list[dict] = []
    rejects: list[dict] = []
    quarantine: list[dict] = []
    placement: list[dict] = []
    used_point_slugs: set[tuple[str, str]] = set()

    def add_material(material: dict, tag: str, extra: dict) -> None:
        errs = A.validate(material, len(materials), set(nodes), registered)
        # 新点材料此刻尚未建点：悬空绑定单独放行（写入窗口先建点再落料），其余照拒
        if errs and all("绑定悬空" in e for e in errs) and extra.get("new_point"):
            errs = []
        if errs:
            quarantine.append({"source": tag, "slug": material["slug"],
                               "reasons": "; ".join(errs[:3])})
            return
        materials.append(material)

    for row in dir_rows:
        verdict = (row.get("verdict") or "").strip()
        subject = row["subject"]
        tag = f"{row['_file']}#{row['idx']}"
        if verdict in ("COVERED", "REJECT", "NEEDS_REVIEW"):
            bucket = coverage if verdict == "COVERED" else rejects
            bucket.append({"source": tag, "subject": subject, "name": row["name"],
                           "verdict": verdict, "evidence": row.get("evidence") or "",
                           "reason": row.get("reason") or ""})
            continue
        if verdict == "GAP" or verdict == "MATERIAL_ONLY":
            node_slug = (row.get("node_slug") or "").strip()
            if (subject, node_slug) not in nodes:
                quarantine.append({"source": tag, "slug": "",
                                   "reasons": f"节点不存在：{subject}/{node_slug}"})
                continue
            add_material(material_from_dir_row(row, node_slug), tag, {})
            continue
        if verdict == "NEW_POINT":
            name = (row.get("point_name") or "").strip()
            kind = (row.get("kind") or "").strip()
            hint = (row.get("parent_hint") or "").strip()
            slug = point_slug(name)
            if not slug or kind not in VALID_KINDS or not name:
                quarantine.append({"source": tag, "slug": slug,
                                   "reasons": f"新点字段不合规 name={name!r} kind={kind!r}"})
                continue
            base = slug
            seq = 2
            while (subject, slug) in nodes or (subject, slug) in used_point_slugs:
                slug = f"{base}-{seq}"
                seq += 1
            used_point_slugs.add((subject, slug))
            parent = topic_slugs.get(subject, {}).get(hint, "")
            material_slug = f"cmp-{SUBJ3[subject]}-{int(row['idx']):05d}"
            if not parent:
                # 归属未定：不出材料（绑定会悬空），把材料草稿一并带上交复核裁决
                placement.append({
                    "source": tag, "subject": subject, "point_name": name, "point_slug": slug,
                    "kind": kind, "parent_hint": hint, "material_slug": material_slug,
                    "material_title": (row.get("material_title") or "").strip(),
                    "material_type": (row.get("material_type") or "").strip(),
                    "material_summary": (row.get("material_summary") or "").strip(),
                    "material_content": (row.get("material_content") or "").replace("\\n", "\n").strip(),
                    "material_boundary": (row.get("material_boundary") or "").strip(),
                })
                continue
            new_points.append({"subject": subject, "slug": slug, "name": name,
                               "kind": kind, "parent_topic_slug": parent,
                               "boundary": (row.get("material_boundary") or "").strip(),
                               "source_locator": f"知识清单（学生版） {row.get('_source_file') or ''}".strip()})
            add_material(material_from_dir_row(row, slug), tag, {"new_point": True})
            continue
        quarantine.append({"source": tag, "slug": "", "reasons": f"未知 verdict {verdict!r}"})

    candidates: dict[str, dict] = {}
    if entries_path.exists():
        for line in entries_path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                doc = json.loads(line)
                candidates[doc["slug"]] = doc
    for row in mat_rows:
        verdict = (row.get("verdict") or "").strip()
        tag = f"{row['_file']}#{row['idx']}"
        slug = (row.get("slug") or "").strip()
        candidate = candidates.get(slug)
        if verdict == "PROMOTE":
            node_slug = (row.get("node_slug") or "").strip()
            if candidate is None:
                quarantine.append({"source": tag, "slug": slug,
                                   "reasons": "mat_entries.jsonl 里没有这条候选"})
                continue
            if (row["subject"], node_slug) not in nodes:
                quarantine.append({"source": tag, "slug": slug,
                                   "reasons": f"节点不存在：{row['subject']}/{node_slug}"})
                continue
            add_material(material_from_mat_row(row, candidate, node_slug), tag, {})
        elif verdict in ("MERGE_INTO", "REJECT", "NEEDS_REVIEW"):
            rejects.append({"source": tag, "subject": row["subject"], "name": slug,
                            "verdict": verdict, "evidence": row.get("evidence") or "",
                            "reason": row.get("reason") or "",
                            "covering_material_slug": row.get("covering_material_slug") or ""})
        else:
            quarantine.append({"source": tag, "slug": slug, "reasons": f"未知 verdict {verdict!r}"})

    return {"errors": errors, "materials": materials, "new_points": new_points,
            "coverage": coverage, "rejects": rejects, "quarantine": quarantine,
            "placement": placement,
            "counts": {"dir_rows": len(dir_rows), "mat_rows": len(mat_rows)}}


def write_all(root: Path, out_dir: Path, built: dict) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    with (out_dir / "authored_materials.jsonl").open("w", encoding="utf-8") as fh:
        for material in built["materials"]:
            fh.write(json.dumps(material, ensure_ascii=False) + "\n")

    def dump_csv(name: str, rows: list[dict], cols: list[str]) -> None:
        with (out_dir / name).open("w", encoding="utf-8", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=cols, extrasaction="ignore")
            writer.writeheader()
            for row in rows:
                writer.writerow(row)

    dump_csv("new_points_rows.csv", built["new_points"],
             ["subject", "slug", "name", "kind", "parent_topic_slug", "boundary", "source_locator"])
    dump_csv("placement_review.csv", built["placement"],
             ["source", "subject", "point_name", "point_slug", "kind", "parent_hint",
              "material_slug", "material_title", "material_type", "material_summary",
              "material_content", "material_boundary"])
    dump_csv("coverage_register.csv", built["coverage"],
             ["source", "subject", "name", "verdict", "evidence", "reason"])
    dump_csv("rejects.csv", built["rejects"],
             ["source", "subject", "name", "verdict", "evidence", "reason", "covering_material_slug"])
    dump_csv("quarantine.csv", built["quarantine"], ["source", "slug", "reasons"])
    summary = {
        "dir_rows": built["counts"]["dir_rows"], "mat_rows": built["counts"]["mat_rows"],
        "authored_materials": len(built["materials"]), "new_points": len(built["new_points"]),
        "coverage": len(built["coverage"]), "rejects": len(built["rejects"]),
        "quarantine": len(built["quarantine"]), "placement_review": len(built["placement"]),
        "header_errors": built["errors"],
    }
    (out_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--verdicts", type=Path, default=None)
    ap.add_argument("--out", type=Path, default=None)
    args = ap.parse_args(argv)
    root = pack_io.REPO
    built = build(root, verdicts_dir=args.verdicts,
                  entries_path=(root / OUT_DIR / "mat_entries.jsonl"))
    counts = built["counts"]
    print(f"裁决行：目录层 {counts['dir_rows']}，材料层 {counts['mat_rows']}；"
          f"材料 {len(built['materials'])}，新点 {len(built['new_points'])}，"
          f"覆盖证据 {len(built['coverage'])}，驳回 {len(built['rejects'])}，"
          f"隔离 {len(built['quarantine'])}，归属待判 {len(built['placement'])}")
    for err in built["errors"][:5]:
        print("  - 表头错：", err)
    if not args.write:
        print("（dry-run：未落盘；加 --write 落盘）")
        return 0
    write_all(root, args.out or (root / OUT_DIR), built)
    print("已写入", args.out or (root / OUT_DIR))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
