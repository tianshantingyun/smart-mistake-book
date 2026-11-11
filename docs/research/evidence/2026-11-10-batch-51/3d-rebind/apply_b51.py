# -*- coding: utf-8 -*-
"""批次51 G1 3D：把 B50-D1 的 43 条错误逐条处置并应用到 staging。

处置（R2 §4.1）：
- 39 条 REBIND：material_rebind.csv 的 from→target 改绑（rebind_materials.rebind）。
- 1 条 NONE 经主会话裁决覆盖为 REBIND（ext-phy-2cea5a0fdb-045）：
  判定前提「同科无对应节点」被节点 boundary/alias 的逐字证据否证，见 OVERRIDES。
- 3 条 NONE：材料不含可归知识 → DROP_MATERIAL（drop_materials）。

写盘范围：只写 build/kb-staging/（工具链拓扑），成品目录由 promote.py 单点写入。
--write 才落盘；默认只写本证据目录的 dispositions.csv / material_rebind_b51.csv。

用法：
    python -I -X utf8 apply_b51.py            # 只出清单/校验，不动包
    python -I -X utf8 apply_b51.py --write    # 写 staging + 同步两张台账
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[4]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import drop_materials, pack_io, rebind_materials  # noqa: E402

ERRORS = REPO / "docs/research/evidence/2026-11-09-batch-50/d1/errors.csv"
REBIND_TABLE = REPO / "tools/kb_build/tables/material_rebind.csv"
BINDINGS_TABLE = REPO / "tools/kb_build/tables/material_bindings.csv"

# 主会话 2026-11-10 裁决：该条 judge_verdict=NONE 的「同科无节点」前提被否证。
OVERRIDES = {
    "ext-phy-2cea5a0fdb-045": {
        "final_target": "平抛运动与斜面的结合分析",
        "override_reason": "判定前提被否证（同科存在逐字匹配节点）：节点 boundary 逐字含"
                           "「并含与斜面或圆弧面有关的抛体运动及易混概念辨析」，aliases 逐字含"
                           "「平抛无碰撞进入圆弧轨道的处理（分解速度）」「从圆弧轨道外平抛恰好无碰撞进入圆弧轨道」；"
                           "材料正文逐字含「小球恰好沿圆轨道上某点的切线方向进入时，进入点的速度沿该点切线方向，"
                           "半径垂直于速度方向」。按 R2 §4.1，DROP（材料不含可归知识）与 NEW_POINT（同科无既有落点）"
                           "两条均不成立，故按 REBIND 处置；只处置，不算已修正，待新一轮盲判复核。",
    },
}

# 3 条 NONE：材料无可归知识内容 → DROP_MATERIAL（R2 §4.1）。
DROPS = {
    "chem-fx-yan-dao-qi-zhong-de-shi-zhu-yao-de-da-f51x12":
        "材料正文仅有题干导语「为了监测其含量，选用如下采样和检测方法」，无采样/检测方法的教学内容；"
        "applicability/boundary 为通用模板句，不含可归知识 → DROP_MATERIAL。",
    "chem-fx-you-ji-hua-he-wu-de-ming-ming-jie-gou-f64x10":
        "材料正文仅章节标题罗列「有机化合物的命名、结构简式的书写、有机化学方程式的书写等」，"
        "无任何知识结论；同科无单一节点覆盖三者 → DROP_MATERIAL。",
    "chem-fx-zai-biao-zhun-zhuang-kuang-xia-f01x08":
        "材料正文仅题干数值情境「在标准状况下，将含a L NO2和b L NO的混合气体通入625 g…」，"
        "无可归知识结论 → DROP_MATERIAL。",
}

REBIND_NOTE = "；(B50-D1 errors.csv, audit B50-D1-2026-11-08, 批次51 3D 改绑 2026-11-10)"


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_items() -> list[dict]:
    rows = list(csv.DictReader(ERRORS.open(encoding="utf-8-sig", newline="")))
    out = []
    for r in rows:
        slug = r["material_slug"]
        if slug in DROPS:
            action, target, reason = "DROP_MATERIAL", "", DROPS[slug]
        elif slug in OVERRIDES:
            action, target = "REBIND", OVERRIDES[slug]["final_target"]
            reason = OVERRIDES[slug]["override_reason"]
        elif r["verdict"] == "REBIND":
            action, target, reason = "REBIND", r["target_node_slug"], ""
        else:
            raise SystemExit(f"未定义处置：{slug} verdict={r['verdict']}")
        out.append({**r, "final_action": action, "final_target": target,
                    "disposition_reason": reason,
                    "override_reason": OVERRIDES.get(slug, {}).get("override_reason", "")})
    return out


def write_dispositions(items: list[dict]) -> None:
    cols = ["material_slug", "layer", "subject", "judge_verdict", "verdict",
            "current_node_id", "current_node_slug", "target_node_id", "target_node_slug",
            "confidence", "final_action", "final_target_node_slug", "override_reason",
            "disposition_reason", "evidence_quote", "note"]
    with (HERE / "dispositions.csv").open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=cols)
        w.writeheader()
        for it in items:
            w.writerow({
                "material_slug": it["material_slug"], "layer": it["layer"],
                "subject": it["subject"], "judge_verdict": it["verdict"],
                "verdict": it["verdict"], "current_node_id": it["current_node_id"],
                "current_node_slug": it["current_node_slug"],
                "target_node_id": it["target_node_id"] if it["verdict"] == "REBIND" else "",
                "target_node_slug": it["target_node_slug"] if it["verdict"] == "REBIND" else "",
                "confidence": it["confidence"], "final_action": it["final_action"],
                "final_target_node_slug": it["final_target"],
                "override_reason": it["override_reason"],
                "disposition_reason": it["disposition_reason"],
                "evidence_quote": it["evidence_quote"], "note": it["note"],
            })


def rebuild_rows(items: list[dict]) -> list[dict]:
    rows = []
    for it in items:
        if it["final_action"] != "REBIND":
            continue
        rows.append({
            "material_slug": it["material_slug"],
            "from_node_slug": it["current_node_slug"],
            "to_node_slug": it["final_target"],
            "evidence": (it["evidence_quote"] + REBIND_NOTE),
        })
    with (HERE / "material_rebind_b51.csv").open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=rebind_materials.COLUMNS)
        w.writeheader()
        w.writerows(rows)
    return rows


def append_rebind_table(rows: list[dict]) -> int:
    """把 40 行追加到权威表（幂等：同 slug/from/to 已存在则跳过）。"""
    existing = set()
    if REBIND_TABLE.exists():
        with REBIND_TABLE.open(encoding="utf-8-sig", newline="") as fh:
            for r in csv.DictReader(fh):
                existing.add((r["material_slug"].strip(), r["from_node_slug"].strip(),
                              r["to_node_slug"].strip()))
    new = [r for r in rows if (r["material_slug"], r["from_node_slug"], r["to_node_slug"]) not in existing]
    if not new:
        return 0
    raw = REBIND_TABLE.read_bytes()
    needs_nl = bool(raw) and not raw.endswith(b"\n")
    with REBIND_TABLE.open("a", encoding="utf-8", newline="") as fh:
        if needs_nl:
            fh.write("\n")
        w = csv.DictWriter(fh, fieldnames=rebind_materials.COLUMNS)
        w.writerows(new)
    return len(new)


def update_bindings_table(items: list[dict]) -> int:
    """material_bindings.csv 的两行必须指向新目标，否则 F9 欠账+1 且 build.py 会改回。"""
    targets = {it["material_slug"]: it["final_target"] for it in items
               if it["final_action"] == "REBIND"}
    raw = BINDINGS_TABLE.read_text(encoding="utf-8").splitlines(keepends=True)
    header = next(csv.reader([raw[0]]))
    changed = 0
    out = [raw[0]]
    for line in raw[1:]:
        row = next(csv.reader([line]))
        rec = dict(zip(header, row))
        slug = rec.get("material_slug", "")
        if slug in targets and rec.get("point_slug") != targets[slug]:
            rec["point_slug"] = targets[slug]
            rec["reason"] = (rec.get("reason", "") + "；批次51 3D 改绑 2026-11-10"
                             "（B50-D1 errors.csv 改指）").lstrip("；")
            buf = io.StringIO()
            csv.writer(buf, lineterminator="\n").writerow([rec[h] for h in header])
            out.append(buf.getvalue())
            changed += 1
        else:
            out.append(line)
    if changed:
        BINDINGS_TABLE.write_text("".join(out), encoding="utf-8")
    return changed


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)

    items = load_items()
    write_dispositions(items)
    rows = rebuild_rows(items)
    rebinds = [it for it in items if it["final_action"] == "REBIND"]
    drops = [it for it in items if it["final_action"] == "DROP_MATERIAL"]
    print(f"43 条：REBIND {len(rebinds)}（含裁决覆盖 {len(OVERRIDES)}）、DROP_MATERIAL {len(drops)}")

    # 预检：材料存在、现绑=from、目标存在且同科。
    pack_io.use_directory(REPO / "build/kb-staging")
    try:
        pack = pack_io.load_json(pack_io.pack_path())
        sidecars = [pack_io.load_json(p) for p in pack_io.sidecar_paths()]
        valid = rebind_materials._valid_node_ids(pack)
        index = {m["slug"]: (sc, m) for sc in sidecars for m in sc["materials"]}
        problems = []
        for it in rebinds:
            slug = it["material_slug"]
            if slug not in index:
                problems.append(f"{slug}: 材料不存在")
                continue
            sc, m = index[slug]
            want = rebind_materials._node_id_of(pack, m["subject"], it["final_target"])
            if want not in valid:
                problems.append(f"{slug}: 目标不存在 {it['final_target']}")
            from_id = rebind_materials._node_id_of(pack, m["subject"], it["current_node_slug"])
            if from_id not in [b["knowledgeNodeId"] for b in m.get("bindings") or []]:
                problems.append(f"{slug}: 现绑不是 {it['current_node_slug']}")
        for it in drops:
            if it["material_slug"] not in index:
                problems.append(f"{it['material_slug']}: 待删材料不存在")
        print(f"预检问题 {len(problems)}")
        for p in problems:
            print("  !", p)
        if problems:
            return 1

        if not args.write:
            print("（未写盘，加 --write 应用）")
            return 0

        before = {slug: [b["knowledgeNodeId"] for b in m.get("bindings") or []]
                  for slug, (_sc, m) in index.items()}
        stats = rebind_materials.rebind(pack, sidecars, rows)
        print(f"改绑结果：rebound={stats['rebound']} skipped={stats['skipped']} "
              f"bad_to={len(stats['bad_to'])}")
        if stats["rebound"] != len(rebinds) or stats["skipped"] or stats["bad_to"]:
            print("!! 改绑统计不符，停止写盘")
            return 1
        # 只写有材料变化的卷（避免整族重写）。
        touched = set()
        for it in rebinds:
            sc, m = index[it["material_slug"]]
            now = [b["knowledgeNodeId"] for b in m.get("bindings") or []]
            if now != before[m["slug"]]:
                touched.add(next(sp.name for sp, s in zip(pack_io.sidecar_paths(), sidecars)
                                  if s is sc))
        for sp, sc in zip(pack_io.sidecar_paths(), sidecars):
            if sp.name in touched:
                pack_io.dump_json(sc, sp)
        print(f"写回卷：{sorted(touched)}")

        slug_list = ",".join(it["material_slug"] for it in drops)
        rc = drop_materials.main(["--slugs", slug_list, "--write"])
        if rc != 0:
            return rc

        appended = append_rebind_table(rows)
        updated = update_bindings_table(items)
        print(f"权威表：material_rebind.csv +{appended} 行；material_bindings.csv 改 {updated} 行")

        # 落盘后自检。
        sidecars2 = [pack_io.load_json(p) for p in pack_io.sidecar_paths()]
        index2 = {m["slug"]: m for sc in sidecars2 for m in sc["materials"]}
        mats_total = len(index2)
        dangling = [b["knowledgeNodeId"] for sc in sidecars2 for m in sc["materials"]
                    for b in m.get("bindings") or [] if b["knowledgeNodeId"] not in valid]
        unbound = [m["slug"] for m in index2.values() if not m.get("bindings")]
        after = {it["material_slug"]: [b["knowledgeNodeId"] for b in index2[it["material_slug"]]["bindings"]]
                 for it in rebinds}
        bad_after = [it["material_slug"] for it in rebinds
                     if after[it["material_slug"]] != [rebind_materials._node_id_of(
                         pack, index2[it["material_slug"]]["subject"], it["final_target"])]]
        result = {
            "rebind_rows": len(rebinds), "drop_rows": len(drops),
            "tool_stats": stats, "materials_before": len(index),
            "materials_after": mats_total, "bindings_after": sum(
                len(m.get("bindings") or []) for m in index2.values()),
            "dangling": dangling, "unbound": unbound, "bad_after": bad_after,
            "touched_sidecars": sorted(touched),
            "rebind_table_appended": appended, "bindings_table_updated": updated,
        }
        (HERE / "apply-result.json").write_text(
            json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")
        print(json.dumps({k: v for k, v in result.items() if k != "tool_stats"},
                         ensure_ascii=False, indent=1))
        ok = (not dangling and not unbound and not bad_after
              and mats_total == len(index) - len(drops))
        return 0 if ok else 1
    finally:
        pack_io.reset_directory()


if __name__ == "__main__":
    raise SystemExit(main())
