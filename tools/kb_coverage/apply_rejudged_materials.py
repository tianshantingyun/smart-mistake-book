# -*- coding: utf-8 -*-
"""重判替换：把重判队列块的新判决**原地替换**掉侧车里的旧材料（同 slug 不同绑定）。

## 它消灭的失败

重判队列（698 行旧 type 不合规）的块在更早轮次已经落过材料——同块同 slug（`ext-<科3>-<块哈希>-<序号><midx>`），
但重判后的目标节点不同。`materialize.write()` 对"slug 已存在且绑定不同"是**整批硬拒**
（它的保护是给"同块两行复用"用的），于是这批新判决会把 2 万条待落材料一起挡在门外（实测：一条都不写）。
本工具按 `(rel, chunk_id, midx)` 的 slug 逐条**原地替换**：字段、来源、时间戳与绑定全换成新判决，
使 materialize 的幂等面重新干净。替换过的行有账（`--write` 时打印并落 CSV），可复核。

## 用法

    python tools/kb_coverage/apply_rejudged_materials.py --dry-run
    python tools/kb_coverage/apply_rejudged_materials.py --write
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import pack_io  # noqa: E402
from kb_coverage import materialize as mz  # noqa: E402
from kb_coverage.pool_path import POOL_PATH  # noqa: E402

OUT_LOG = REPO / "build" / "agent-input" / "rejudged_replacements.csv"


def material_slug(subject: str, chunk_id: str, midx: str) -> str:
    """与 materialize.write() 同一口径的 slug 派生（两处必须一致）。"""
    h = chunk_id.split("-", 1)[0]
    idx = chunk_id.rsplit("-", 1)[-1]
    return f"ext-{mz.SUBJ3[subject]}-{h}-{idx}{(midx or '').strip()}"


def subject_by_key() -> dict[tuple[str, str], str]:
    out: dict[tuple[str, str], str] = {}
    with POOL_PATH.open(encoding="utf-8") as fh:
        for line in fh:
            if not line.strip():
                continue
            d = json.loads(line)
            out[(d["rel_path"], d["chunk_id"])] = d.get("subject") or ""
    return out


def load_rows(path: Path | None = None) -> list[dict]:
    target = path or mz.JUDGMENTS
    with target.open(encoding="utf-8-sig", newline="") as fh:
        return [r for r in csv.DictReader(fh) if (r.get("action") or "") == "MATERIAL"]


def plan(rows: list[dict] | None = None) -> dict:
    """找出「slug 已存在且绑定不同」的行，并定位它在哪一卷、原来绑谁。"""
    rows = rows if rows is not None else load_rows()
    subjects = subject_by_key()
    state = mz._sidecar_state()
    index: dict[str, tuple[Path, dict]] = {}
    for path, doc in state["docs"].items():
        for material in doc["materials"]:
            for binding in material.get("bindings") or []:
                index.setdefault(material["slug"], (path, {"node": binding["knowledgeNodeId"].split(":")[-1]}))
    todo: list[dict] = []
    for row in rows:
        subject = subjects.get((row["chunk_rel"], row["chunk_id"]), "")
        if subject not in mz.SUBJ3:
            continue
        slug = material_slug(subject, row["chunk_id"], row.get("midx") or "")
        hit = index.get(slug)
        if hit is None:
            continue
        old_node = hit[1]["node"]
        if old_node == row["node_slug"]:
            continue
        todo.append({"row": row, "subject": subject, "slug": slug, "sidecar": hit[0],
                     "old_node": old_node, "new_node": row["node_slug"]})
    return {"todo": todo, "docs": {p: d for p, d in state["docs"].items()}}


def apply(todo: list[dict], docs: dict) -> dict:
    now = mz._now_ms()
    changed: dict[Path, dict] = {}
    for item in todo:
        row, subject, slug = item["row"], item["subject"], item["slug"]
        path = item["sidecar"]
        doc = changed.setdefault(path, docs[path])
        for material in doc["materials"]:
            if material["slug"] != slug:
                continue
            material.update({
                "type": row["type"],
                "title": row["title"].strip(),
                "summaryMarkdown": row["summary"].strip(),
                "applicabilityMarkdown": (row.get("applicability") or "").strip() or row["summary"].strip(),
                "contentMarkdown": row["content"].strip(),
                "boundaryMarkdown": (row.get("boundary") or "").strip() or row["summary"].strip(),
                "derivationKind": "REVIEWED_SYNTHESIS",
                "reviewedAtEpochMillis": now,
                "bindings": [{"knowledgeNodeId": f"kb:moe-2025-four-subjects-v1:"
                                                 f"{subject.lower()}:atomic:{item['new_node']}",
                              "role": "PRIMARY"}],
            })
            break
    for path, doc in changed.items():
        pack_io.dump_json(doc, path)
    OUT_LOG.parent.mkdir(parents=True, exist_ok=True)
    with OUT_LOG.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["slug", "old_node", "new_node", "sidecar"])
        for item in todo:
            writer.writerow([item["slug"], item["old_node"], item["new_node"], item["sidecar"].name])
    return {"replaced": len(todo), "sidecars": {p.name: len(d["materials"]) for p, d in changed.items()}}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    group = ap.add_mutually_exclusive_group()
    group.add_argument("--dry-run", action="store_true")
    group.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)
    pl = plan()
    todo = pl["todo"]
    print(f"需替换 {len(todo)} 条（同 slug 已存在、目标不同）")
    for item in todo[:5]:
        print(f"  - {item['slug']}: {item['old_node']} → {item['new_node']}")
    if not args.write:
        print("（dry-run：未写盘；加 --write 落盘）")
        return 0
    stats = apply(todo, pl["docs"])
    print(f"已替换 {stats['replaced']} 条 → 账在 {OUT_LOG.relative_to(REPO).as_posix()}")
    print(f"涉及卷：{stats['sidecars']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
