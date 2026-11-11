# -*- coding: utf-8 -*-
"""批次51 3D：改绑后的影响面闭合（别名 + chapter_by_node 悬空行）。

为什么需要：别名的判据是"该节点自己绑定材料的标题"（rebuild_aliases 文档）。材料改绑后，
旧节点的别名失去依据（ghost_aliases 跳闸），新节点缺少对应检索名；改绑还使一个纯题干
节点失去唯一材料（unbound_points），该点已由 delete_points 删除，chapter_by_node 的
逐节点行随之悬空。

本脚本按 `rebuild_aliases.rebuild()` 的同一判据算出别名，但**只应用受本次改绑影响的
(subject, slug)**：用改前成品（shipped，仍为改前基线）重算一次，把"改前就存在的漂移
节点"排除。键一律用 (subject, slug)——只用 slug 会在跨科同名节点（如 CHEMISTRY/BIOLOGY
都有「基因工程」）上串味。

包的重建以 shipped 为基线：删点 + 只改受影响节点的 aliases，保证 43 条之外的节点
逐字段不动。

用法：
    python -I -X utf8 fix_aliases_closure.py            # 报告
    python -I -X utf8 fix_aliases_closure.py --write    # 写 staging 包 + alias_map + chapter_by_node
"""
from __future__ import annotations

import argparse
import copy
import csv
import io
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[4]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import pack_io, rebuild_aliases  # noqa: E402

STAGING = REPO / "build/kb-staging"
SHIPPED = REPO / "core/data/src/main/resources/knowledge"
ALIAS_MAP = REPO / "tools/kb_build/tables/alias_map.csv"
CHAPTER_BY_NODE = REPO / "tools/kb_build/tables/chapter_by_node.csv"


def titles_from(root: Path) -> dict[tuple[str, str], set[str]]:
    out: dict[tuple[str, str], set[str]] = {}
    for i in range(1, 26):
        p = root / f"moe-2025-teaching-support-v2-{i:02d}.json"
        if not p.exists():
            continue
        for m in pack_io.load_json(p)["materials"]:
            for b in m.get("bindings") or []:
                parts = b["knowledgeNodeId"].split(":")
                out.setdefault((parts[-3].upper(), parts[-1]), set()).add(m.get("title") or "")
    return out


def keyed_aliases(pack: dict, titles: dict) -> dict[tuple[str, str], list[str]]:
    """(subject, slug) -> 按 rebuild 判据应得的完整 aliases（主名在首位）。"""
    plan = rebuild_aliases.rebuild(pack, titles=titles)
    out: dict[tuple[str, str], list[str]] = {}
    for key, point in plan["node_by_key"].items():
        out[key] = [point["name"]] + sorted(
            a for a in plan["candidates"].get(key, set()) if a != point["name"])
    return out


def pack_aliases(pack: dict) -> dict[tuple[str, str], list[str]]:
    return {(s["subject"], p["slug"]): list(p.get("aliases") or [])
            for s in pack["subjects"] for t in s["topics"]
            for p in t.get("knowledgePoints") or []}


def pack_keys(pack: dict) -> set[tuple[str, str]]:
    return set(pack_aliases(pack))


def git_blob(path: Path) -> bytes:
    rel = path.relative_to(REPO).as_posix()
    return subprocess.check_output(["git", "show", f"HEAD:{rel}"], cwd=REPO)


def replace_alias_rows(rows: list[dict], changed: dict[tuple[str, str], list[str]],
                       deleted: set[tuple[str, str]]) -> tuple[list[list[str]], int]:
    out: list[list[str]] = []
    emitted: set[tuple[str, str]] = set()
    dropped = 0
    for r in rows:
        key = (r["subject"], r["slug"])
        if key in deleted:
            dropped += 1
            continue
        if key in changed:
            if key in emitted:
                dropped += 1
                continue
            emitted.add(key)
            for alias in changed[key][1:]:
                out.append([key[0], key[1], alias])
            continue
        out.append([key[0], key[1], r["alias"]])
    for key in sorted(set(changed) - emitted):
        for alias in changed[key][1:]:
            out.append([key[0], key[1], alias])
    return out, dropped


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)

    pack_io.use_directory(STAGING)
    try:
        staging_pack = pack_io.load_json(STAGING / pack_io.PACK_NAME)
        shipped_pack = pack_io.load_json(SHIPPED / pack_io.PACK_NAME)
        staging_titles = titles_from(STAGING)
        shipped_titles = titles_from(SHIPPED)

        deleted = pack_keys(shipped_pack) - pack_keys(staging_pack)
        desired = keyed_aliases(shipped_pack, staging_titles)       # 以改前节点树 + 改后材料标题
        baseline_desired = keyed_aliases(shipped_pack, shipped_titles)
        baseline_actual = pack_aliases(shipped_pack)
        drift = {k for k in baseline_desired if baseline_desired[k] != baseline_actual[k]}
        changed_keys = {k for k in desired
                        if k not in deleted and k not in drift
                        and desired[k] != baseline_actual[k]}

        detail = {}
        for key in sorted(changed_keys):
            detail[f"{key[0]}/{key[1]}"] = {
                "added": sorted(set(desired[key]) - set(baseline_actual[key])),
                "removed": sorted(set(baseline_actual[key]) - set(desired[key])),
            }
        report = {
            "deleted_points": [f"{k[0]}/{k[1]}" for k in sorted(deleted)],
            "pre_existing_drift_excluded": [f"{k[0]}/{k[1]}" for k in sorted(drift)],
            "applied_nodes": len(changed_keys),
            "added_total": sum(len(v["added"]) for v in detail.values()),
            "removed_total": sum(sum(1 for _ in v["removed"]) for v in detail.values()),
            "detail": detail,
        }
        (HERE / "alias-closure.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"删除点 {len(deleted)}；改前漂移（排除）{len(drift)}；应用 {len(changed_keys)} 个节点；"
              f"+{report['added_total']} / -{report['removed_total']} 条别名")

        if not args.write:
            print("（未写盘，加 --write 生效）")
            return 0

        # 1) 包：以 shipped 为基线，删点 + 只改受影响节点的 aliases。
        fixed = copy.deepcopy(shipped_pack)
        for subj in fixed["subjects"]:
            for t in subj["topics"]:
                t["knowledgePoints"] = [p for p in t.get("knowledgePoints") or []
                                        if (subj["subject"], p["slug"]) not in deleted]
                for p in t["knowledgePoints"]:
                    key = (subj["subject"], p["slug"])
                    if key in changed_keys:
                        p["aliases"] = desired[key]
        # 防御：重建后的节点集合必须与当前 staging 完全一致（没有别的节点被删/加）。
        if pack_keys(fixed) != pack_keys(staging_pack):
            print("!! 重建节点集合与 staging 不一致，停止写盘")
            return 1
        pack_io.dump_json(fixed, STAGING / pack_io.PACK_NAME)

        # 2) alias_map：以 HEAD 基线重放，删点行 + 受影响节点块替换。
        base_rows = list(csv.DictReader(io.StringIO(git_blob(ALIAS_MAP).decode("utf-8-sig"),
                                                    newline="")))
        out_rows, dropped = replace_alias_rows(
            base_rows, {k: desired[k] for k in changed_keys}, deleted)
        with ALIAS_MAP.open("w", encoding="utf-8", newline="") as fh:
            w = csv.writer(fh, lineterminator="\n")
            w.writerow(["subject", "slug", "alias"])
            w.writerows(out_rows)

        # 3) chapter_by_node：删掉被删点的逐节点行。
        cn_lines = git_blob(CHAPTER_BY_NODE).decode("utf-8-sig").splitlines(keepends=True)
        header = next(csv.reader([cn_lines[0]]))
        kept = [cn_lines[0]]
        removed_cn = 0
        for line in cn_lines[1:]:
            rec = dict(zip(header, next(csv.reader([line]))))
            if (rec.get("subject", ""), rec.get("slug", "")) in deleted:
                removed_cn += 1
                continue
            kept.append(line if line.endswith("\n") else line + "\n")
        CHAPTER_BY_NODE.write_text("".join(kept), encoding="utf-8")

        print(f"→ 已写 staging 包；alias_map 替换 {len(changed_keys)} 块（删/去重 {dropped} 行）；"
              f"chapter_by_node 删 {removed_cn} 行")
        return 0
    finally:
        pack_io.reset_directory()


if __name__ == "__main__":
    raise SystemExit(main())
