# -*- coding: utf-8 -*-
"""B51-D1 分层抽样器：从批次 51 改后成品包抽「绑定」判定件（确定性、可重放）。

口径来源：
- `.zcode/plans/plan-2026-11-10-batch-51-final.md` G1-11/12（新抽样框快照 + 新 audit id）
- `docs/research/2026-11-09-binding-audit.md` §2.2/§2.3/§3.4（2 层框、哈希排序+随机起点、1,600）
- `docs/research/evidence/2026-11-10-batch-51/3d-d2d3/README.md`、`frame-divergence.json`
  （P2 冻结抽样框：v28 时代快照，D-2 冻结并登记 sha；v29 复算只作差异登记，不替换冻结框）

本脚本只读成品包、冻结框快照与 frame_manifest.json，只写本目录（3d-audit/）。
- 层：overfull = 当前绑定所落 node_id ∈ 冻结快照 node_id 集合；normal = 其余。
  快照 sha256 必须与 `frame/frame_manifest.json` 登记值逐字一致，否则中止。
  快照是 v28 时代产物（批次 51 P1 改绑前），层归属按指令沿用冻结框；当前包节点若不在
  快照中不参与 overfull（本批实测缺失 0 个，仍记账）。
- 抽样核（与批次 50 相同）：seed = sha256(pack_scope_sha|audit_id)；
  层内按 sha256(seed|layer|material_slug) 升序；随机起点 start = sha256(seed|layer|start)[:8]
  mod floor(N/n)；系统选样 idx_i = start + floor(i*N/n)，i=0..n-1。
- 样本量：overfull 1,300 + normal 300 = 1,600（固定样本、一次判读）。
- 冻结：sample.jsonl / sample.csv 落盘后不得重抽/补样/删样。默认拒绝重跑；`--force`
  只允许在工具故障后重算派生文件，且新样本键必须与已冻结 sample.csv 逐键一致，否则中止。

用法：
    python -I -X utf8 sample_d1.py            # 抽样 + 写 sample/manifest/判件分片
    python -I -X utf8 sample_d1.py --check    # 只复算样本键并与已落盘 sample.csv 对账
    python -I -X utf8 sample_d1.py --force    # 工具故障后重算（样本键必须不变）
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

AUDIT_ID = "B51-D1-2026-11-10"
N_OVERFULL = 1300
N_NORMAL = 300
SHARD_SIZE = 50
GAPS_CSV = Path("knowledge-production/node-material-gaps-2026-10-10.csv")
PACK_DIR = Path("core/data/src/main/resources/knowledge")
PACK_NAME = "moe-2025-four-subjects-v1.json"
INDEX_NAME = "moe-2025-teaching-support-v2-index.json"
TEXT_FIELDS = ("title", "summaryMarkdown", "applicabilityMarkdown",
               "contentMarkdown", "boundaryMarkdown")


def find_repo(start: Path) -> Path:
    for p in [start, *start.parents]:
        if (p / PACK_DIR / PACK_NAME).exists():
            return p
    raise SystemExit("找不到仓库根（core/data/.../knowledge/moe-2025-four-subjects-v1.json）")


HERE = Path(__file__).resolve().parent
REPO = find_repo(HERE)
FRAME_DIR = HERE / "frame"
OVERFULL_SNAPSHOT = FRAME_DIR / "overfull_nodes.snapshot.csv"
FRAME_MANIFEST = FRAME_DIR / "frame_manifest.json"
OUT_SAMPLE_JSONL = HERE / "sample.jsonl"
OUT_SAMPLE_CSV = HERE / "sample.csv"
OUT_SAMPLE_SHA = HERE / "sample.sha256"
OUT_MANIFEST = HERE / "manifest.json"
OUT_JUDGE_DIR = HERE / "judge_input"
OUT_SHARD_MANIFEST = OUT_JUDGE_DIR / "shard_manifest.json"


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(REPO)).replace("\\", "/")
    except ValueError:
        return str(path).replace("\\", "/")


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def load_nodes() -> dict[str, dict]:
    pack = load_json(REPO / PACK_DIR / PACK_NAME)
    pack_id = pack["packId"]
    nodes: dict[str, dict] = {}
    for subject in pack["subjects"]:
        subj = subject["subject"]
        for topic in subject["topics"]:
            for point in topic.get("knowledgePoints") or []:
                node_id = f"kb:{pack_id}:{subj.lower()}:atomic:{point['slug']}"
                if node_id in nodes:
                    raise SystemExit(f"节点 id 重复：{node_id}")
                nodes[node_id] = {
                    "node_id": node_id,
                    "subject": subj,
                    "topic": topic.get("slug") or "",
                    "topic_name": topic.get("name") or "",
                    "slug": point["slug"],
                    "name": point.get("name") or point["slug"],
                    "kind": point.get("kind") or "",
                    "boundary": point.get("boundary") or "",
                    "aliases": list(point.get("aliases") or []),
                }
    return nodes


def load_bindings() -> tuple[list[dict], dict, list[Path]]:
    index = load_json(REPO / PACK_DIR / INDEX_NAME)
    pack_id = index["packId"]
    rows: list[dict] = []
    sidecars: list[Path] = []
    seen_slugs: set[str] = set()
    for name in index["sidecars"]:
        path = REPO / PACK_DIR / name.rsplit("/", 1)[-1]
        if not path.exists():
            raise SystemExit(f"索引列出的 sidecar 不存在：{path}")
        sidecars.append(path)
        doc = load_json(path)
        if doc.get("packId") != pack_id:
            raise SystemExit(f"sidecar packId 不符：{path}")
        for m in doc["materials"]:
            if m["slug"] in seen_slugs:
                raise SystemExit(f"材料 slug 跨 sidecar 重复：{m['slug']}")
            seen_slugs.add(m["slug"])
            bs = m.get("bindings") or []
            if len(bs) != 1:
                raise SystemExit(f"材料 {m['slug']} 有 {len(bs)} 条绑定（契约要求 1 材料 1 绑定）")
            rows.append({
                "material_slug": m["slug"],
                "subject": m["subject"],
                "node_id": bs[0]["knowledgeNodeId"],
                "sidecar": path.name,
                "material": m,
            })
    return rows, index, sidecars


def load_overfull() -> tuple[set[str], dict]:
    """读 P2 冻结快照；sha/行数/节点数必须与 frame_manifest.json 登记值一致。"""
    frame = load_json(FRAME_MANIFEST)
    expected_sha = frame["sha256"]
    actual_sha = sha256_file(OVERFULL_SNAPSHOT)
    if actual_sha != expected_sha:
        raise SystemExit(f"冻结框快照 sha 不符：登记 {expected_sha} 实得 {actual_sha}")
    ids: set[str] = set()
    rows = 0
    with OVERFULL_SNAPSHOT.open(encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            rows += 1
            nid = (row.get("node_id") or "").strip()
            if not nid:
                raise SystemExit(f"{OVERFULL_SNAPSHOT}: 第 {rows + 1} 行缺 node_id")
            ids.add(nid)
    if rows != frame["rows_parsed_by_csv"]:
        raise SystemExit(f"冻结框行数不符：登记 {frame['rows_parsed_by_csv']} 实得 {rows}")
    if len(ids) != frame["distinct_nodes"]:
        raise SystemExit(f"冻结框节点数不符：登记 {frame['distinct_nodes']} 实得 {len(ids)}")
    meta = {
        "type": "frozen snapshot（P2，v28 时代；层归属按指令沿用，不随 v29 复算替换）",
        "path": rel(OVERFULL_SNAPSHOT),
        "sha256": actual_sha,
        "rows": rows,
        "unique_node_ids": len(ids),
        "frozen_at_utc": frame.get("frozen_at_utc"),
        "frozen_by": frame.get("frozen_by"),
        "source_path": frame.get("source_path"),
        "source_mtime_utc": frame.get("source_mtime_utc"),
        "stale_relative_to_pack": frame.get("stale_relative_to_pack"),
    }
    return ids, meta


def thin_status(nodes: dict[str, dict]) -> dict:
    """按抽样器的同一解析口径检查薄料名单，只记账、不用于分层。"""
    path = REPO / GAPS_CSV
    if not path.exists():
        return {"path": str(GAPS_CSV).replace("\\", "/"), "exists": False}
    by_subject: dict[str, set[str]] = {}
    for meta in nodes.values():
        by_subject.setdefault(meta["subject"], set()).add(meta["slug"])
    rows = unresolved = 0
    examples: list[str] = []
    with path.open(encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            rows += 1
            subject = (row.get("subject") or "").strip()
            slug = (row.get("slug") or "").strip()
            if slug not in by_subject.get(subject, set()):
                unresolved += 1
                if len(examples) < 5:
                    examples.append(f"{subject}/{slug}")
    return {"path": str(GAPS_CSV).replace("\\", "/"), "exists": True,
            "rows": rows, "unresolved": unresolved, "examples": examples,
            "sha256": sha256_file(path),
            "decision": "名单失效 -> 不用 thin 层；沿用批次 50 的 2 层框（overfull/normal）"}


def pack_scope_sha(index: dict, sidecars: list[Path]) -> tuple[str, list[dict]]:
    """包 scope sha：主包 + 索引 + 索引内 sidecar，按固定顺序逐文件 sha256 再汇总。"""
    files = [REPO / PACK_DIR / PACK_NAME, REPO / PACK_DIR / INDEX_NAME, *sidecars]
    parts: list[dict] = []
    h = hashlib.sha256()
    for path in files:
        digest = sha256_file(path)
        name = path.name
        h.update(f"{name}\0{digest}\n".encode("utf-8"))
        parts.append({"file": name, "sha256": digest})
    return h.hexdigest(), parts


def sample_layer(rows: list[dict], layer: str, n: int, seed: str) -> list[dict]:
    if n > len(rows):
        raise SystemExit(f"{layer}: 样本量 {n} 超过层规模 {len(rows)}")
    ordered = sorted(rows, key=lambda r: sha256_bytes(
        f"{seed}|{layer}|{r['material_slug']}".encode("utf-8")))
    N = len(ordered)
    interval = max(1, N // n)
    start = int(sha256_bytes(f"{seed}|{layer}|start".encode("utf-8"))[:8], 16) % interval
    picked = []
    idxs: list[int] = []
    for i in range(n):
        idx = start + (i * N) // n
        if idx >= N:
            raise SystemExit(f"{layer}: idx {idx} 越界 N={N}")
        idxs.append(idx)
        row = dict(ordered[idx])
        row["layer"] = layer
        row["pool_size"] = N
        row["pool_index"] = idx
        row["order_rank"] = i
        picked.append(row)
    if len(set(idxs)) != n:
        raise SystemExit(f"{layer}: 系统选样出现重复下标")
    return picked


def material_text(material: dict) -> str:
    return "\n".join(str(material.get(f) or "") for f in TEXT_FIELDS if material.get(f))


def shard_order(samples: list[dict]) -> list[dict]:
    """判定分片顺序：audit_id 域哈希升序，确定性混层（判定件不含 layer）。"""
    return sorted(samples, key=lambda s: sha256_bytes(
        f"{AUDIT_ID}|shard|{s['material_slug']}".encode("utf-8")))


def build_judge_batches(samples: list[dict], nodes: dict[str, dict],
                        batch_size: int = SHARD_SIZE) -> dict:
    """判定件：不含 layer / pool / 全局序号；REBIND 候选另查 node_index.tsv。"""
    OUT_JUDGE_DIR.mkdir(parents=True, exist_ok=True)
    for old in OUT_JUDGE_DIR.glob("batch-*.json"):
        old.unlink()
    ordered = shard_order(samples)
    if len(ordered) % batch_size:
        raise SystemExit(f"样本数 {len(ordered)} 不能被分片大小 {batch_size} 整除")
    shards: list[dict] = []
    for b in range(0, len(ordered), batch_size):
        chunk = ordered[b:b + batch_size]
        items = []
        for n, s in enumerate(chunk, start=1):
            m = s["material"]
            cur = nodes[s["node_id"]]
            items.append({
                "n": n,
                "material_slug": m["slug"],
                "subject": m["subject"],
                "type": m.get("type") or "",
                "title": m.get("title") or "",
                "text": material_text(m)[:500],
                "current_node": {
                    "node_slug": cur["slug"], "name": cur["name"], "kind": cur["kind"],
                    "topic_name": cur["topic_name"], "boundary": cur["boundary"][:120],
                },
            })
        name = f"batch-{b // batch_size + 1:02d}.json"
        path = OUT_JUDGE_DIR / name
        path.write_text(json.dumps(items, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        shards.append({
            "batch": name[:-5],
            "file": f"judge_input/{name}",
            "sha256": sha256_file(path),
            "count": len(items),
            "material_slugs": [it["material_slug"] for it in items],
        })
    payload = {
        "audit_id": AUDIT_ID,
        "count": len(shards),
        "size": batch_size,
        "order_rule": "sha256(audit_id + '|shard|' + material_slug) 升序后按 50 条切分（确定性混层）",
        "blind_fields_excluded": ["layer", "pool_size", "pool_index", "order_rank", "global_i"],
        "shards": shards,
    }
    OUT_SHARD_MANIFEST.write_text(json.dumps(payload, ensure_ascii=False, indent=1) + "\n",
                                  encoding="utf-8")
    # 节点清单：REBIND 目标检索用（判定件本身不给候选，避免诱导改绑）
    lines = ["# subject\ttopic\tnode_slug\tnode_name\tkind\tboundary"]
    for nid in sorted(nodes, key=lambda n: (nodes[n]["subject"], nodes[n]["topic"], nodes[n]["slug"])):
        meta = nodes[nid]
        lines.append("\t".join([meta["subject"], meta["topic"], meta["slug"], meta["name"],
                                meta["kind"], meta["boundary"].replace("\t", " ").replace("\n", " ")[:160]]))
    (HERE / "node_index.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return payload


def existing_sample_keys() -> list[tuple[str, str, str]] | None:
    if not OUT_SAMPLE_CSV.exists():
        return None
    with OUT_SAMPLE_CSV.open(encoding="utf-8", newline="") as fh:
        return sorted((r["layer"], r["material_slug"], r["node_id"])
                      for r in csv.DictReader(fh))


def write_sample(picked: list[dict], nodes: dict[str, dict]) -> tuple[str, str]:
    with OUT_SAMPLE_JSONL.open("w", encoding="utf-8", newline="\n") as fh:
        for r in picked:
            m = r["material"]
            nid = r["node_id"]
            meta = nodes[nid]
            rec = {
                "i": r["i"], "layer": r["layer"], "material_slug": m["slug"],
                "subject": m["subject"], "type": m.get("type") or "",
                "sidecar": r["sidecar"],
                "material": {k: m.get(k) for k in TEXT_FIELDS},
                "source": {"sourceId": m.get("sourceId"), "sourceLocator": m.get("sourceLocator"),
                           "contentAttribute": m.get("contentAttribute"),
                           "contentProvider": m.get("contentProvider")},
                "binding": {"knowledgeNodeId": nid, "role": (m.get("bindings") or [{}])[0].get("role")},
                "node": {"node_slug": meta["slug"], "name": meta["name"], "kind": meta["kind"],
                         "topic": meta["topic"], "topic_name": meta["topic_name"],
                         "boundary": meta["boundary"]},
                "pool_size": r["pool_size"], "pool_index": r["pool_index"],
                "order_rank": r["order_rank"],
            }
            fh.write(json.dumps(rec, ensure_ascii=False) + "\n")

    with OUT_SAMPLE_CSV.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["i", "layer", "material_slug", "subject", "node_id", "node_slug",
                    "pool_size", "pool_index", "order_rank"])
        for r in picked:
            w.writerow([r["i"], r["layer"], r["material_slug"], r["subject"], r["node_id"],
                        nodes[r["node_id"]]["slug"], r["pool_size"], r["pool_index"], r["order_rank"]])
    csv_sha = sha256_file(OUT_SAMPLE_CSV)
    jsonl_sha = sha256_file(OUT_SAMPLE_JSONL)
    OUT_SAMPLE_SHA.write_text(f"{csv_sha}  sample.csv\n{jsonl_sha}  sample.jsonl\n",
                              encoding="utf-8", newline="\n")
    return csv_sha, jsonl_sha


def run(force: bool = False) -> None:
    nodes = load_nodes()
    bindings, index, sidecars = load_bindings()
    overfull, overfull_meta = load_overfull()
    for b in bindings:
        if b["node_id"] not in nodes:
            raise SystemExit(f"悬空绑定：{b['material_slug']} -> {b['node_id']}")
    absent = sorted(overfull - set(nodes))
    overfull_meta["snapshot_nodes_absent_from_pack"] = len(absent)
    overfull_meta["absent_examples"] = absent[:5]

    for b in bindings:
        b["layer"] = "overfull" if b["node_id"] in overfull else "normal"
    buckets = {"overfull": [], "normal": []}
    for b in bindings:
        buckets[b["layer"]].append(b)
    if len(buckets["overfull"]) + len(buckets["normal"]) != len(bindings):
        raise SystemExit("分层不守恒")

    scope_sha, scope_files = pack_scope_sha(index, sidecars)
    seed = sha256_bytes(f"{scope_sha}|{AUDIT_ID}".encode("utf-8"))
    picked = (sample_layer(buckets["overfull"], "overfull", N_OVERFULL, seed)
              + sample_layer(buckets["normal"], "normal", N_NORMAL, seed))
    picked.sort(key=lambda r: (r["layer"], r["material_slug"]))
    for i, r in enumerate(picked):
        r["i"] = i

    new_keys = sorted((r["layer"], r["material_slug"], r["node_id"]) for r in picked)
    frozen_keys = existing_sample_keys()
    if frozen_keys is not None:
        if new_keys != frozen_keys:
            raise SystemExit("样本键与已冻结 sample.csv 不一致：禁止重抽/补样/删样；"
                             "换框或换包必须新 audit id")
        if not force:
            raise SystemExit("样本已冻结（sample.csv/sample.jsonl 已存在）；"
                             "重写派生文件用 --force（样本键须不变）")

    frozen_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    csv_sha, jsonl_sha = write_sample(picked, nodes)

    manifest = {
        "audit_id": AUDIT_ID,
        "generated_by": "docs/research/evidence/2026-11-10-batch-51/3d-audit/sample_d1.py",
        "sampler_sha256": sha256_file(Path(__file__)),
        "pack": {
            "dir": str(PACK_DIR).replace("\\", "/"),
            "pack_id": index["packId"],
            "pack_scope_sha256": scope_sha,
            "scope_files": scope_files,
            "nodes": len(nodes),
            "materials": len(bindings),
            "bindings": len(bindings),
        },
        "frame": {
            "layers": {
                "overfull": {"size": len(buckets["overfull"]),
                             "weight": len(buckets["overfull"]) / len(bindings),
                             "list": overfull_meta},
                "normal": {"size": len(buckets["normal"]),
                           "weight": len(buckets["normal"]) / len(bindings)},
            },
            "total": len(bindings),
            "layer_rule": "当前包绑定所落 node_id ∈ 冻结快照 node_id 集合 -> overfull；其余 -> normal",
            "thin": thin_status(nodes),
        },
        "sampling": {
            "unit": "一条绑定（1 材料 1 绑定）",
            "seed": seed,
            "seed_rule": "sha256(pack_scope_sha256 + '|' + audit_id)",
            "order": "层内按 sha256(seed|layer|material_slug) 升序",
            "start": "int(sha256(seed|layer|start)[:8],16) mod floor(N_layer/n_layer)",
            "index": "idx_i = start + floor(i*N_layer/n_layer), i=0..n_layer-1",
            "randomness": "none（确定性哈希顺序 + 哈希起点；无 RNG 状态）",
        },
        "sample": {
            "overfull": N_OVERFULL, "normal": N_NORMAL, "total": len(picked),
            "fixed": True, "early_stop": False, "frozen_at_utc": frozen_at,
            "sha256": {"sample.csv": csv_sha, "sample.jsonl": jsonl_sha},
        },
        "shards": {
            "count": len(picked) // SHARD_SIZE, "size": SHARD_SIZE,
            "manifest": "judge_input/shard_manifest.json",
            "manifest_sha256": sha256_file(OUT_SHARD_MANIFEST) if OUT_SHARD_MANIFEST.exists() else None,
            "blind_fields_excluded": ["layer", "pool_size", "pool_index", "order_rank", "global_i"],
        },
    }
    build_judge_batches(picked, nodes)
    manifest["shards"]["manifest_sha256"] = sha256_file(OUT_SHARD_MANIFEST)
    OUT_MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n",
                            encoding="utf-8")
    print(json.dumps({
        "audit_id": AUDIT_ID,
        "pack_scope_sha256": scope_sha,
        "layers": {"overfull": len(buckets["overfull"]), "normal": len(buckets["normal"])},
        "sample": {"overfull": N_OVERFULL, "normal": N_NORMAL, "total": len(picked)},
        "sample_sha256": {"sample.csv": csv_sha, "sample.jsonl": jsonl_sha},
        "frame_sha256": overfull_meta["sha256"],
        "shards": manifest["shards"]["count"],
        "seed": seed,
        "out": rel(HERE),
    }, ensure_ascii=False, indent=1))


def check() -> None:
    """复算样本键并与已落盘 sample.csv 对账（不写任何文件）。"""
    nodes = load_nodes()
    bindings, index, sidecars = load_bindings()
    overfull, _ = load_overfull()
    for b in bindings:
        b["layer"] = "overfull" if b["node_id"] in overfull else "normal"
    buckets = {"overfull": [], "normal": []}
    for b in bindings:
        buckets[b["layer"]].append(b)
    scope_sha, _ = pack_scope_sha(index, sidecars)
    seed = sha256_bytes(f"{scope_sha}|{AUDIT_ID}".encode("utf-8"))
    picked = (sample_layer(buckets["overfull"], "overfull", N_OVERFULL, seed)
              + sample_layer(buckets["normal"], "normal", N_NORMAL, seed))
    expected = sorted((r["layer"], r["material_slug"], r["node_id"]) for r in picked)
    with OUT_SAMPLE_CSV.open(encoding="utf-8", newline="") as fh:
        actual = sorted((r["layer"], r["material_slug"], r["node_id"]) for r in csv.DictReader(fh))
    ok = expected == actual
    print(json.dumps({"check": "sample_keys", "match": ok,
                      "expected": len(expected), "actual": len(actual)}, ensure_ascii=False))
    if not ok:
        raise SystemExit(2)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--force", action="store_true",
                    help="仅工具故障后重算派生文件；样本键必须与已冻结 sample.csv 一致")
    args = ap.parse_args(argv)
    if args.check:
        check()
    else:
        run(force=args.force)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
