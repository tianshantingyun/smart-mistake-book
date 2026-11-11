# -*- coding: utf-8 -*-
"""B50 R-4 探针：对**现行**成品包重算 D-1 分层框（只读，不写判定件）。

复现 sample_binding_precision.py 的层定义（overfull/thin/normal），并额外算
超配层内 risk 子层（weak_match × rank）的规模，供样本分配计算用。
"""
from __future__ import annotations

import collections
import csv
import json
import sys
from pathlib import Path

def find_repo(start):
    """向上找含 tools/kb_build/pack_io.py 的目录作为仓库根（脚本可放在任意子目录）。"""
    for p in [start, *start.parents]:
        if (p / "tools" / "kb_build" / "pack_io.py").exists():
            return p
    raise SystemExit("找不到仓库根（缺 tools/kb_build/pack_io.py）")


REPO = find_repo(Path(__file__).resolve().parent)
sys.path.insert(0, str(REPO / "tools"))
from kb_build import pack_io  # noqa: E402
import importlib.util  # noqa: E402

spec = importlib.util.spec_from_file_location(
    "sbp", REPO / "tools/kb_coverage/sample_binding_precision.py")
sbp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sbp)


def main() -> int:
    rd = pack_io.release_dir()
    pack, nodes, sidecars = sbp.load_pack_and_nodes(rd)
    bindings, scope, excluded = sbp.load_sidecar_bindings(rd, sidecars)
    over, over_meta = sbp.load_overfull_nodes(REPO / "build/kb-staging/overfull_nodes.csv")
    try:
        thin, thin_meta = sbp.load_thin_nodes(
            REPO / "knowledge-production/node-material-gaps-2026-10-10.csv", nodes, pack["packId"])
        thin_err = None
    except SystemExit as e:
        thin, thin_meta, thin_err = set(), None, str(e)[:400]

    missing_over = sorted(over - set(nodes))
    buckets = sbp.assign_layers(bindings, over, thin)
    total = sum(len(v) for v in buckets.values())

    # risk 子层：来自 overfull_nodes.csv 的 (weak_match, rank)
    risk: dict[str, str] = {}
    with (REPO / "build/kb-staging/overfull_nodes.csv").open(encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            w = "yes" if (row.get("weak_match") or "").strip() == "yes" else "no"
            r = "le4" if int(row.get("rank") or 0) <= 4 else "gt4"
            risk[(row.get("node_id") or "").strip(), (row.get("material_slug") or "").strip()] = f"{w}_{r}"

    sub = collections.Counter()
    unlabeled = 0
    for b in buckets["overfull"]:
        key = risk.get((b["node_id"], b["material_slug"]))
        if key is None:
            unlabeled += 1
        else:
            sub[key] += 1

    out = {
        "pack_id": pack["packId"],
        "scope": scope,
        "excluded_sidecars": excluded,
        "overfull_csv": over_meta,
        "overfull_ids_missing_from_pack": len(missing_over),
        "overfull_missing_examples": missing_over[:5],
        "thin_csv": thin_meta,
        "thin_error": thin_err,
        "layers": {k: {"size": len(v), "nodes": len({b["node_id"] for b in v})}
                   for k, v in buckets.items()},
        "total_bindings": total,
        "weights": {k: len(v) / total for k, v in buckets.items()},
        "overfull_risk_substrata": dict(sub),
        "overfull_bindings_without_risk_label": unlabeled,
    }
    print(json.dumps(out, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
