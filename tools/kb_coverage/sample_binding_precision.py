# -*- coding: utf-8 -*-
"""三层绑定抽样器：从**成品包**的 bindings 里抽判定件，给绑定精确率做分层估计。

## 它消灭的失败

绑定质量此前只有"嫌疑清单 + 逐条裁定"这种**全量**手段（一次几千条、代理读不完，
于是只能挑一层看）。想报一个**能代表全体**的精确率，就必须分层抽样：少数几个
高风险层的精确率不能代表 59,008 条绑定——超配层一层就占 93%，普通层只有 6%，
不分层加权就会把某一层的结论当成整体结论。

1. **不分层 = 拿一层的结论冒充整体**：本工具按绑定落点分三层（超配/薄料/普通），
   层规模由成品包 sidecar 实测，权重随件写进 manifest，供统计器加权。
2. **随机抽样不可复跑**：判定件与被判对象必须能被第三方重放核对。本工具**不用随机数**
   ——每层内按 `material_slug` 排序后等距取（`floor(i*N/k)`），同一输入永远得到同一批件。
3. **判据靠"层名单"散落在各处**：`overfull_nodes.csv` 的 `node_id`、`gaps.csv` 的
   `(subject, slug)`，对不上就是静默漏判。本工具把两份名单解析成节点 id 集合并**断言**
   全部命中当前包、两层零重叠，对不上直接报错退出。

## 层定义（与 ask 逐字一致）

- **超配层 overfull**：绑到 `build/kb-staging/overfull_nodes.csv` 里节点的绑定。
- **薄料层 thin**：绑到 `knowledge-production/node-material-gaps-2026-10-10.csv` 的绑定。
- **普通层 normal**：其余绑定（= 全部 − 超配 − 薄料）。

抽样单位是**一条绑定**（material × 现绑节点），因为精确率的判据就是"这条绑定对不对"。

## 产物

    build/agent-batch9/d1/precision/kits/kit-<layer>-<NN>.json   判定件（每条：材料 + 现绑节点）
    build/agent-batch9/d1/precision/kits.list                    件清单（相对仓库路径，一行一件）
    build/agent-batch9/d1/precision/manifest.json                层规模/权重/样本量/输入指纹

判定员回填的表头（统计器 `binding_precision.py` 读同一口径）：

    material_slug,layer,correct,evidence,note      # correct ∈ YES/NO

## 用法

    python tools/kb_coverage/sample_binding_precision.py
    python tools/kb_coverage/sample_binding_precision.py --per-layer 200 --per-kit 40

幂等：同一输入重复跑，产物逐字节相同（无时间戳、无随机、无环境相关字段）。
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from kb_build import pack_io  # noqa: E402

REPO: Path = pack_io.REPO

LAYERS = ("overfull", "thin", "normal")
LAYER_ZH = {"overfull": "超配层", "thin": "薄料层", "normal": "普通层"}
# 绑定落点既在超配名单又在薄料名单时的归属（本仓实测两层零重叠，见 assert_no_overlap）
LAYER_PRECEDENCE = ("overfull", "thin")

# 材料正文五字段：与 make_binding_slices.py 的 FIELDS 同一口径，判定员看到的是同一套文本
MATERIAL_FIELDS = ("title", "summaryMarkdown", "applicabilityMarkdown",
                   "contentMarkdown", "boundaryMarkdown")
MATERIAL_META_FIELDS = ("type", "sourceId", "sourceLocator")


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def resolve_repo_path(p: Path) -> Path:
    """把路径解析到仓库内，越界直接报错（不静默取相对目录）。"""
    resolved = p.resolve()
    if not resolved.is_relative_to(REPO):
        raise SystemExit(f"路径逃出仓库根：{resolved}")
    return resolved


def load_pack_and_nodes(release_dir: Path) -> tuple[dict[str, Any], dict[str, dict[str, Any]], list[Path]]:
    """读成品包：返回 (pack, node_id -> 节点档案, sidecar 路径列表)。

    sidecar 清单以索引文件为准（与 Kotlin loader 同一份索引）；不在索引里、
    packId 不等于本包的 sidecar 会被**显式记账并排除**，不做静默吸收。
    """
    index_path = release_dir / pack_io.SIDECAR_INDEX_NAME
    if not index_path.exists():
        raise SystemExit(f"成品包缺 sidecar 索引：{index_path}")
    index = json.loads(index_path.read_text(encoding="utf-8"))
    pack_path = release_dir / pack_io.PACK_NAME
    pack = json.loads(pack_path.read_text(encoding="utf-8"))

    nodes: dict[str, dict[str, Any]] = {}
    for subject, topic, point in pack_io.iter_points(pack):
        node_id = f'kb:{pack["packId"]}:{subject.lower()}:atomic:{point["slug"]}'
        if node_id in nodes:
            raise SystemExit(f"包内节点 id 重复：{node_id}")
        nodes[node_id] = {
            "subject": subject,
            "slug": point["slug"],
            "name": point.get("name") or point["slug"],
            "kind": point.get("kind") or "",
            "boundary": point.get("boundary") or "",
            "aliases": list(point.get("aliases") or []),
            "topic": topic.get("slug") or "",
        }

    sidecars: list[Path] = []
    for name in index["sidecars"]:
        # 索引里写的是 "knowledge/xxx.json"（类路径写法），落到成品目录
        sidecars.append(release_dir / name.rsplit("/", 1)[-1])
    return pack, nodes, sidecars


def load_sidecar_bindings(
    release_dir: Path, sidecars: list[Path]
) -> tuple[list[dict[str, Any]], dict[str, Any], list[dict[str, Any]]]:
    """读成品包绑定的**权威口径**：索引里的 sidecar。

    返回 (bindings, scope 记账, 被排除的 sidecar 记账)。
    材料必须带且只带一条绑定（DB 契约要求至少一条；多绑材料本工具显式报错，
    避免"一条材料两票"污染精确率）。
    """
    bindings: list[dict[str, Any]] = []
    materials = 0
    excluded: list[dict[str, Any]] = []
    index = json.loads((release_dir / pack_io.SIDECAR_INDEX_NAME).read_text(encoding="utf-8"))
    indexed = {p.name for p in sidecars}
    pack_id = index["packId"]

    for path in sidecars:
        if not path.exists():
            raise SystemExit(f"索引列出的 sidecar 不存在：{path}")
        doc = json.loads(path.read_text(encoding="utf-8"))
        if doc.get("packId") != pack_id:
            raise SystemExit(f"sidecar packId 与索引不符：{path} -> {doc.get('packId')}")
        for m in doc["materials"]:
            materials += 1
            bs = m.get("bindings") or []
            if len(bs) != 1:
                raise SystemExit(
                    f"材料 {m['slug']} 有 {len(bs)} 条绑定，本工具按 1 材料 1 绑定建口径"
                    f"（{path.name}）")
            b = bs[0]
            bindings.append({
                "material_slug": m["slug"],
                "subject": m["subject"],
                "node_id": b["knowledgeNodeId"],
                "role": b.get("role") or "",
                "sidecar": path.name,
                "material": m,
            })

    # 其余 sidecar（本包家族之外的历史包）：显式记账、不并入层规模
    for path in sorted(release_dir.glob("moe-*teaching-support*.json")):
        if path.name in indexed or path.name == pack_io.SIDECAR_INDEX_NAME:
            continue
        doc = json.loads(path.read_text(encoding="utf-8"))
        n_mat = len(doc.get("materials") or [])
        n_bind = sum(len(m.get("bindings") or []) for m in doc.get("materials") or [])
        excluded.append({
            "path": str(path.relative_to(REPO)).replace("\\", "/"),
            "pack_id": doc.get("packId") or "",
            "materials": n_mat,
            "bindings": n_bind,
            "reason": "packId 不在成品包 sidecar 索引内；层名单（超配/薄料）不描述该包的节点空间",
        })

    scope = {
        "sidecar_index": pack_io.SIDECAR_INDEX_NAME,
        "pack_id": pack_id,
        "sidecars": len(sidecars),
        "materials": materials,
        "bindings": len(bindings),
        "excluded_sidecars": excluded,
    }
    return bindings, scope, excluded


def load_overfull_nodes(path: Path) -> tuple[set[str], dict[str, Any]]:
    """超配名单：`node_id` 列的去重集合（`material_count` 只用于记账，不参与分层）。"""
    ids: set[str] = set()
    rows = 0
    with path.open(encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            rows += 1
            nid = (row.get("node_id") or "").strip()
            if not nid:
                raise SystemExit(f"{path}: 第 {rows + 1} 行缺 node_id")
            ids.add(nid)
    return ids, {"rows": rows, "unique_node_ids": len(ids)}


def load_thin_nodes(
    path: Path, nodes: dict[str, dict[str, Any]], pack_id: str
) -> tuple[set[str], dict[str, Any]]:
    """薄料名单：`(subject, slug)` 逐行解析成节点 id，**全部必须命中当前包**。

    解析不出来（slug 已被并点/改名）就报错——照着过期账本分层会把整层的权重算错，
    这正是 make_judge_kit.py 记录过的坑。
    """
    by_subject: dict[str, set[str]] = {}
    for nid, meta in nodes.items():
        by_subject.setdefault(meta["subject"], set()).add(meta["slug"])

    ids: set[str] = set()
    rows = 0
    unresolved: list[dict[str, str]] = []
    with path.open(encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            rows += 1
            subject = (row.get("subject") or "").strip()
            slug = (row.get("slug") or "").strip()
            if slug not in by_subject.get(subject, set()):
                unresolved.append({"subject": subject, "slug": slug})
                continue
            ids.add(f"kb:{pack_id}:{subject.lower()}:atomic:{slug}")
    if unresolved:
        sample = ", ".join(f"{u['subject']}/{u['slug']}" for u in unresolved[:5])
        raise SystemExit(
            f"{path} 有 {len(unresolved)} 行解析不到当前包的节点（例：{sample}）——"
            f"名单已过期，分层权重不可信，先修名单来源")
    return ids, {"rows": rows, "resolved_node_ids": len(ids)}


def allocate_even(counts: dict[str, int], target: int) -> dict[str, int]:
    """把 target 份**尽量均摊**给各科（不是按体量比例），超出某科存量时顺延给余科。

    确定性：按 subject 名字母序轮转发牌，余数因此落在字母序靠前的科——同一输入必然同解。
    """
    keys = sorted(counts)
    if not keys:
        return {}
    available = sum(counts.values())
    remaining = min(target, available)
    alloc = {k: 0 for k in keys}
    while remaining > 0:
        progressed = 0
        for k in keys:
            if remaining == 0:
                break
            if alloc[k] < counts[k]:
                alloc[k] += 1
                remaining -= 1
                progressed += 1
        if progressed == 0:
            break
    return alloc


def equidistant_indices(pool_size: int, k: int) -> list[int]:
    """从 range(pool_size) 里等距取 k 个下标：`floor(i*N/k)`。

    严格递增、恒 <N、可复跑（无随机）；k>=N 时退化为全取。
    """
    if k <= 0:
        return []
    if k >= pool_size:
        return list(range(pool_size))
    return [(i * pool_size) // k for i in range(k)]


def assign_layers(
    bindings: list[dict[str, Any]], overfull: set[str], thin: set[str]
) -> dict[str, list[dict[str, Any]]]:
    """按落点分层：超配 → 薄料 → 普通（前两层已断言零重叠，故顺序不吞并）。

    每条绑定**必然落进且只落进一层**（普通层是"其余"），调用方据此核对总数守恒。
    """
    buckets: dict[str, list[dict[str, Any]]] = {L: [] for L in LAYERS}
    for b in bindings:
        nid = b["node_id"]
        if nid in overfull:
            layer = "overfull"
        elif nid in thin:
            layer = "thin"
        else:
            layer = "normal"
        b["layer"] = layer
        buckets[layer].append(b)
    return buckets


def sample_layer(
    rows: list[dict[str, Any]], per_layer: int
) -> tuple[list[dict[str, Any]], dict[str, dict[str, int]]]:
    """层内抽样：先按科均摊配额，再在每科内按 material_slug 排序等距取。

    返回 (抽中的绑定，逐科配额与池子记账)。
    """
    by_subject: dict[str, list[dict[str, Any]]] = {}
    for r in rows:
        by_subject.setdefault(r["subject"], []).append(r)
    counts = {s: len(v) for s, v in by_subject.items()}
    alloc = allocate_even(counts, per_layer)

    picked: list[dict[str, Any]] = []
    quota: dict[str, dict[str, int]] = {}
    for subject in sorted(by_subject):
        pool = sorted(by_subject[subject], key=lambda r: (r["material_slug"], r["node_id"]))
        take = alloc.get(subject, 0)
        idxs = equidistant_indices(len(pool), take)
        for pos in idxs:
            row = dict(pool[pos])
            row["pool_size"] = len(pool)
            row["pool_index"] = pos
            picked.append(row)
        quota[subject] = {"allocated": take, "pool": len(pool), "taken": len(idxs)}
    picked.sort(key=lambda r: (r["subject"], r["material_slug"], r["node_id"]))
    return picked, quota


def kit_entry(b: dict[str, Any], nodes: dict[str, dict[str, Any]]) -> dict[str, Any]:
    """一条绑定 = 一件。材料正文 + 现绑节点档案 + 层名，判定员据此判 YES/NO。"""
    m = b["material"]
    meta = nodes.get(b["node_id"], {})
    entry: dict[str, Any] = {
        "material_slug": m["slug"],
        "subject": m["subject"],
        "layer": b["layer"],
        "layer_zh": LAYER_ZH[b["layer"]],
        "material": {k: m.get(k, "") for k in MATERIAL_FIELDS},
        "material_meta": {k: m.get(k, "") for k in MATERIAL_META_FIELDS},
        "bound_node": {
            "node_id": b["node_id"],
            "slug": meta.get("slug", ""),
            "name": meta.get("name", ""),
            "boundary": meta.get("boundary", ""),
            "aliases": meta.get("aliases", []),
        },
        "bound_role": b["role"],
        "source": {"sidecar": b["sidecar"], "pool_size": b.get("pool_size"),
                   "pool_index": b.get("pool_index")},
    }
    return entry


def write_json(path: Path, obj: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(obj, ensure_ascii=False, indent=1, sort_keys=False)
    with path.open("w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--release-dir", type=Path, default=pack_io.release_dir(),
                    help="成品包目录（只读）")
    ap.add_argument("--overfull", type=Path,
                    default=REPO / "build/kb-staging/overfull_nodes.csv")
    ap.add_argument("--gaps", type=Path,
                    default=REPO / "knowledge-production/node-material-gaps-2026-10-10.csv")
    ap.add_argument("--out", type=Path,
                    default=REPO / "build/agent-batch9/d1/precision")
    ap.add_argument("--per-layer", type=int, default=200, help="每层抽多少条")
    ap.add_argument("--per-kit", type=int, default=40, help="每个判定件装多少条")
    args = ap.parse_args(argv)

    release_dir = resolve_repo_path(args.release_dir)
    overfull_path = resolve_repo_path(args.overfull)
    gaps_path = resolve_repo_path(args.gaps)
    out: Path = resolve_repo_path(args.out)
    if args.per_layer <= 0 or args.per_kit <= 0:
        raise SystemExit("--per-layer / --per-kit 必须为正")

    pack, nodes, sidecars = load_pack_and_nodes(release_dir)
    bindings, scope, excluded = load_sidecar_bindings(release_dir, sidecars)
    if excluded:
        print(f"注意：{len(excluded)} 个非本包 sidecar 已排除（见 manifest.binding_scope."
              f"excluded_sidecars）", file=sys.stderr)
    overfull, over_meta = load_overfull_nodes(overfull_path)
    thin, thin_meta = load_thin_nodes(gaps_path, nodes, pack["packId"])

    # 名单必须都落在当前包内，否则分层是"按一份对不上的名单"分的
    missing_over = sorted(overfull - set(nodes))
    if missing_over:
        raise SystemExit(f"超配名单有 {len(missing_over)} 个节点不在当前成品包（例："
                         f"{missing_over[:3]}）——名单与包不同代，先修名单来源")
    bind_missing = sorted({b["node_id"] for b in bindings} - set(nodes))
    if bind_missing:
        raise SystemExit(f"成品包有 {len(bind_missing)} 条绑定指向包内不存在的节点（例："
                         f"{bind_missing[:3]}）")

    overlap = overfull & thin
    if overlap:
        raise SystemExit(
            f"超配名单与薄料名单重叠 {len(overlap)} 个节点（例：{sorted(overlap)[:3]}）"
            f"——层定义会互相吞并，需先裁定归属再抽样")

    buckets = assign_layers(bindings, overfull, thin)
    total = sum(len(v) for v in buckets.values())
    if total != len(bindings):
        raise SystemExit(f"分层后条数 {total} != 绑定总数 {len(bindings)}（有绑定被判据吞掉）")

    # 产物目录：清掉上一轮的件，保证"目录里剩的就是本次产物"
    kits_dir = out / "kits"
    if kits_dir.exists():
        for old in sorted(kits_dir.glob("kit-*.json")):
            old.unlink()
    kits_dir.mkdir(parents=True, exist_ok=True)

    layer_stats: dict[str, Any] = {}
    kit_files: list[dict[str, Any]] = []
    sample_stats: dict[str, Any] = {}
    for layer in LAYERS:
        rows = buckets[layer]
        picked, quota = sample_layer(rows, args.per_layer)
        used_nodes = len({r["node_id"] for r in rows})
        layer_stats[layer] = {
            "layer_zh": LAYER_ZH[layer],
            "nodes": used_nodes,
            "size": len(rows),
            "weight": (len(rows) / total) if total else 0.0,
        }
        sample_stats[layer] = {
            "n": len(picked),
            "target": args.per_layer,
            "by_subject": quota,
        }

        for i in range(0, len(picked), args.per_kit):
            chunk = picked[i:i + args.per_kit]
            nn = i // args.per_kit + 1
            name = f"kit-{layer}-{nn:02d}.json"
            path = kits_dir / name
            write_json(path, [kit_entry(b, nodes) for b in chunk])
            kit_files.append({
                "path": str(path.relative_to(REPO)).replace("\\", "/"),
                "layer": layer,
                "count": len(chunk),
                "sha256": sha256_file(path),
            })

    kit_files.sort(key=lambda k: k["path"])
    kits_list = out / "kits.list"
    kits_list.parent.mkdir(parents=True, exist_ok=True)
    with kits_list.open("w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(k["path"] for k in kit_files) + "\n")

    manifest = {
        "tool": "tools/kb_coverage/sample_binding_precision.py",
        "schema": 1,
        "release_dir": str(release_dir.relative_to(REPO)).replace("\\", "/"),
        "pack_id": pack["packId"],
        "inputs": {
            "overfull_nodes_csv": {
                "path": str(overfull_path.relative_to(REPO)).replace("\\", "/"),
                "sha256": sha256_file(overfull_path),
                **over_meta,
            },
            "gaps_csv": {
                "path": str(gaps_path.relative_to(REPO)).replace("\\", "/"),
                "sha256": sha256_file(gaps_path),
                **thin_meta,
            },
        },
        "binding_scope": scope,
        "layers": layer_stats,
        "total_bindings": total,
        "sampling": {
            "unit": "binding（material_slug × 现绑节点）",
            "method": "层内先按科均摊配额，再在每科内按 material_slug 排序等距取 floor(i*N/k)",
            "randomness": "none",
            "sort_key": "subject, material_slug, node_id",
            "per_layer": args.per_layer,
            "per_kit": args.per_kit,
            "layer_precedence": list(LAYER_PRECEDENCE),
        },
        "sample": sample_stats,
        "kits": {
            "dir": str(kits_dir.relative_to(REPO)).replace("\\", "/"),
            "count": len(kit_files),
            "items": sum(k["count"] for k in kit_files),
            "files": kit_files,
        },
        "kits_list": str(kits_list.relative_to(REPO)).replace("\\", "/"),
        "verdict_header": "material_slug,layer,correct,evidence,note",
    }
    write_json(out / "manifest.json", manifest)

    print(f"成品包 {release_dir}")
    print(f"绑定总数 {total}（材料 {scope['materials']}，sidecar {scope['sidecars']}）")
    for layer in LAYERS:
        st = layer_stats[layer]
        print(f"  {layer:8s} {LAYER_ZH[layer]} 规模 {st['size']:6d}"
              f"  权重 {st['weight']:.6f}  节点 {st['nodes']:5d}"
              f"  抽样 {sample_stats[layer]['n']}")
    print(f"判定件 {len(kit_files)} 个 / {manifest['kits']['items']} 条 → {kits_dir}")
    print(f"清单 {kits_list}")
    print(f"manifest {out / 'manifest.json'}")
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
