# -*- coding: utf-8 -*-
"""B50 R-4 探针：对当前成品包做**全量确定性检查**（不判定语义，只看机器可核事实）。

输出：材料/绑定计数、悬空、多绑、跨科绑、节点数、每节点绑定量分布、材料 type 分布、
sidecar sha256（版本绑定）。
"""
from __future__ import annotations

import collections
import hashlib
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


def sha(p: Path) -> str:
    h = hashlib.sha256()
    with p.open("rb") as fh:
        for c in iter(lambda: fh.read(1 << 20), b""):
            h.update(c)
    return h.hexdigest()


def main() -> int:
    rd = pack_io.release_dir()
    pack = json.loads((rd / pack_io.PACK_NAME).read_text(encoding="utf-8"))
    index = json.loads((rd / pack_io.SIDECAR_INDEX_NAME).read_text(encoding="utf-8"))
    pack_id = index["packId"]

    nodes: dict[str, dict] = {}
    for subject, topic, point in pack_io.iter_points(pack):
        nid = f'kb:{pack_id}:{subject.lower()}:atomic:{point["slug"]}'
        nodes[nid] = {"subject": subject, "slug": point["slug"], "kind": point.get("kind") or ""}

    materials = 0
    bindings: list[tuple[str, str, str, str]] = []  # (mat_slug, mat_subject, node_id, type)
    multi = 0
    zero = 0
    sidecars = []
    for name in index["sidecars"]:
        p = rd / name.rsplit("/", 1)[-1]
        sidecars.append({"name": p.name, "bytes": p.stat().st_size, "sha256": sha(p)})
        doc = json.loads(p.read_text(encoding="utf-8"))
        for m in doc["materials"]:
            materials += 1
            bs = m.get("bindings") or []
            if len(bs) == 0:
                zero += 1
            elif len(bs) > 1:
                multi += 1
            for b in bs:
                bindings.append((m["slug"], m.get("subject") or "", b.get("knowledgeNodeId") or "",
                                 m.get("type") or ""))

    dangling = [b for b in bindings if b[2] not in nodes]
    cross_subject = [b for b in bindings
                     if b[2] in nodes and b[1].upper() != nodes[b[2]]["subject"].upper()]
    per_node = collections.Counter(b[2] for b in bindings)
    sizes = sorted(per_node.values())
    types = collections.Counter(b[3] for b in bindings)

    out = {
        "release_dir": str(rd),
        "pack_id": pack_id,
        "sidecars": sidecars,
        "nodes": len(nodes),
        "materials": materials,
        "bindings": len(bindings),
        "materials_zero_binding": zero,
        "materials_multi_binding": multi,
        "dangling_bindings": len(dangling),
        "cross_subject_bindings": len(cross_subject),
        "cross_subject_examples": cross_subject[:8],
        "nodes_with_bindings": len(per_node),
        "binding_per_node": {
            "min": sizes[0] if sizes else 0,
            "p50": sizes[len(sizes) // 2] if sizes else 0,
            "mean": round(len(bindings) / len(per_node), 4) if per_node else 0,
            "max": sizes[-1] if sizes else 0,
            "gt4": sum(1 for s in sizes if s > 4),
        },
        "material_type_top": types.most_common(15),
    }
    print(json.dumps(out, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
