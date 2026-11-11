# -*- coding: utf-8 -*-
"""B50-D1 分层抽样器：从现行成品包抽「绑定」判定件（确定性、可重放）。

口径来源：
- `.zcode/plans/plan-2026-11-09-batch-50-release.md` §G1-12（D-1 分层抽样与模型审计）
- `docs/research/2026-11-09-binding-audit.md` §2.2/§2.3/§3.4（2 层框、哈希排序+随机起点、1,600）
- `docs/research/2026-11-09-batch-50-baseline.md`（现行包 3,280 节点 / 64,853 材料 / 64,853 绑定）

本脚本只读成品包与名单，只写本目录（d1/）。
- 层：overfull（落 overfull_nodes.csv 节点）/ normal（其余）。
  薄料名单 `node-material-gaps-2026-10-10.csv` 在现行包上有 11 行解析不到节点
  （R2 §1.4 实测），本脚本**不**用它分层，只在 manifest 里记录失效行数。
- 抽样核（R2 §2.3）：seed = sha256(pack_scope_sha|audit_id)；
  层内按 sha256(seed|layer|material_slug) 升序；随机起点 start = sha256(seed|layer)[:8] mod floor(N/n)；
  系统选样 idx_i = start + floor(i*N/n)。
- 样本量：overfull 1,300 + normal 300 = 1,600（R2 §3.4 推荐值，固定样本、一次判读）。

用法：
    python -I -X utf8 sample_d1.py            # 写 sample.jsonl / sample.csv / manifest.json / judge_input/
    python -I -X utf8 sample_d1.py --check    # 只复算样本键并与已落盘 sample.csv 对账
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import sys
from pathlib import Path

AUDIT_ID = "B50-D1-2026-11-08"
N_OVERFULL = 1300
N_NORMAL = 300
OVERFULL_CSV = Path("build/kb-staging/overfull_nodes.csv")
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
D1 = HERE
OUT_SAMPLE_JSONL = D1 / "sample.jsonl"
OUT_SAMPLE_CSV = D1 / "sample.csv"
OUT_MANIFEST = D1 / "manifest.json"
OUT_JUDGE_DIR = D1 / "judge_input"


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


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
    for name in index["sidecars"]:
        path = REPO / PACK_DIR / name.rsplit("/", 1)[-1]
        if not path.exists():
            raise SystemExit(f"索引列出的 sidecar 不存在：{path}")
        sidecars.append(path)
        doc = load_json(path)
        if doc.get("packId") != pack_id:
            raise SystemExit(f"sidecar packId 不符：{path}")
        for m in doc["materials"]:
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
    path = REPO / OVERFULL_CSV
    ids: set[str] = set()
    rows = 0
    with path.open(encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            rows += 1
            nid = (row.get("node_id") or "").strip()
            if not nid:
                raise SystemExit(f"{path}: 第 {rows + 1} 行缺 node_id")
            ids.add(nid)
    return ids, {"path": str(OVERFULL_CSV).replace("\\", "/"),
                 "rows": rows, "unique_node_ids": len(ids),
                 "sha256": sha256_file(path)}


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
            "decision": "名单失效 -> 不用 thin 层；按 R2 §1.4(b) 改 2 层（overfull/normal）并登记"}


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
    for i in range(n):
        idx = start + (i * N) // n
        if idx >= N:
            raise SystemExit(f"{layer}: idx {idx} 越界 N={N}")
        row = dict(ordered[idx])
        row["layer"] = layer
        row["pool_size"] = N
        row["pool_index"] = idx
        row["order_rank"] = i
        picked.append(row)
    return picked


def bigrams(s: str) -> set[str]:
    s = "".join(ch for ch in s if not ch.isspace())
    return {s[i:i + 2] for i in range(len(s) - 1)} if len(s) > 1 else {s}


def similarity(a: str, b: str) -> float:
    A, B = bigrams(a), bigrams(b)
    if not A or not B:
        return 0.0
    return len(A & B) / len(A | B)


def node_bigram_index(nodes: dict[str, dict]) -> dict[str, list[set[str]]]:
    """节点名 + 别名 的 2-gram 集合，一次算好（1,600×3,280 次循环不能每次重建）。"""
    out: dict[str, list[set[str]]] = {}
    for nid, meta in nodes.items():
        out[nid] = [bigrams(x) for x in [meta["name"], *meta["aliases"]][:40]]
    return out


def candidates_for(material: dict, current: dict, nodes: dict[str, dict],
                   bigram_idx: dict[str, list[set[str]]], k: int = 6) -> list[dict]:
    """同科候选：标题/摘要 对 节点名+别名 的 2-gram Jaccard，取前 k；再补同 topic 兄弟。"""
    q = (material.get("title") or "") + " " + (material.get("summaryMarkdown") or "")[:100]
    Q = bigrams(q)
    scored: list[tuple[float, str]] = []
    for nid, meta in nodes.items():
        if nid == current["node_id"] or meta["subject"] != current["subject"]:
            continue
        best = 0.0
        for B in bigram_idx[nid]:
            if Q and B:
                j = len(Q & B) / len(Q | B)
                if j > best:
                    best = j
        if best > 0:
            scored.append((best, nid))
    scored.sort(key=lambda t: (-t[0], t[1]))
    out: list[dict] = []
    seen: set[str] = set()
    for score, nid in scored[:k]:
        meta = nodes[nid]
        seen.add(nid)
        out.append({"node_slug": meta["slug"], "name": meta["name"], "kind": meta["kind"],
                    "boundary": meta["boundary"][:120], "score": round(score, 3),
                    "reason": "2gram"})
    # 同 topic 兄弟节点：当词面相似度抓不到时，至少给判定员一个结构邻域
    for nid, meta in nodes.items():
        if len(out) >= k + 3:
            break
        if (nid not in seen and nid != current["node_id"]
                and meta["subject"] == current["subject"]
                and meta["topic"] == current["topic"]):
            seen.add(nid)
            out.append({"node_slug": meta["slug"], "name": meta["name"], "kind": meta["kind"],
                        "boundary": meta["boundary"][:120], "score": 0.0,
                        "reason": "same_topic"})
    return out


def material_text(material: dict) -> str:
    return "\n".join(str(material.get(f) or "") for f in TEXT_FIELDS if material.get(f))


def build_judge_batches(samples: list[dict], nodes: dict[str, dict], batch_size: int = 50) -> None:
    """判定件：**不含 layer / 候选节点**，避免层标签与候选表影响判定阈值。

    候选节点另放 `node_index.tsv`，只在初判为 REBIND/NONE 后按需查询目标。
    """
    OUT_JUDGE_DIR.mkdir(parents=True, exist_ok=True)
    for old in OUT_JUDGE_DIR.glob("batch-*.json"):
        old.unlink()
    for b in range(0, len(samples), batch_size):
        chunk = samples[b:b + batch_size]
        items = []
        for s in chunk:
            m = s["material"]
            cur = nodes[s["node_id"]]
            items.append({
                "i": s["i"],
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
        path = OUT_JUDGE_DIR / f"batch-{b // batch_size + 1:02d}.json"
        path.write_text(json.dumps(items, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    # 节点清单：REBIND 目标检索用（判定件本身不给候选，避免诱导改绑）
    lines = ["# subject\ttopic\tnode_slug\tnode_name\tkind\tboundary"]
    for nid in sorted(nodes, key=lambda n: (nodes[n]["subject"], nodes[n]["topic"], nodes[n]["slug"])):
        meta = nodes[nid]
        lines.append("\t".join([meta["subject"], meta["topic"], meta["slug"], meta["name"],
                                meta["kind"], meta["boundary"].replace("\t", " ").replace("\n", " ")[:160]]))
    (D1 / "node_index.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")


def run() -> None:
    nodes = load_nodes()
    bindings, index, sidecars = load_bindings()
    overfull, overfull_meta = load_overfull()
    for b in bindings:
        if b["node_id"] not in nodes:
            raise SystemExit(f"悬空绑定：{b['material_slug']} -> {b['node_id']}")
    for nid in overfull:
        if nid not in nodes:
            raise SystemExit(f"overfull_nodes.csv 有不属现行包的节点：{nid}")

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

    # sample.jsonl：完整可审计快照（含层，供复算；不含给判定员）
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

    manifest = {
        "audit_id": AUDIT_ID,
        "generated_by": "docs/research/evidence/2026-11-09-batch-50/d1/sample_d1.py",
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
        "sample": {"overfull": N_OVERFULL, "normal": N_NORMAL,
                   "total": len(picked), "fixed": True, "early_stop": False},
    }
    OUT_MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n",
                            encoding="utf-8")
    build_judge_batches(picked, nodes)
    print(json.dumps({
        "pack_scope_sha256": scope_sha,
        "layers": {"overfull": len(buckets["overfull"]), "normal": len(buckets["normal"])},
        "sample": {"overfull": N_OVERFULL, "normal": N_NORMAL},
        "seed": seed,
        "out": str(D1).replace("\\", "/"),
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
    args = ap.parse_args(argv)
    if args.check:
        check()
    else:
        run()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
