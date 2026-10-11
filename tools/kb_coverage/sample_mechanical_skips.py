# -*- coding: utf-8 -*-
"""机械 SKIP 分层抽检取件（纯读；件落 build/agent-batch9/skipsample/）。

本脚本是 `docs/research/2026-10-11-mechanical-skip-stratified-sampling.md` 的取件器：
分层、确定性等距抽样（无随机、无人工挑选），结果可复跑复现。

对象：tools/kb_coverage/tables/mechanical_skips.csv（107,160 行，旧机械判据的 SKIP 产物）。
分层（按 note 前缀）：
  A-选项派生：题目派生：正文含成套选项 A/B/C/D（68,705）
  B-答案解析派生：题目派生：正文含答案/故选等判定语（30,503）
  C-题号短题干派生：题目派生：标题是题号/来源 + 题目派生：正文是短题干且无结论（1,550）
  D-指纹重复：同内容指纹重复（只留首次）（1,131）
  E-过短（附加层，批 6 根因报告点名该层抽样不足）：过短（N 字 < 40）（2,735）
每层确定性等距抽样 N=40（按 (chunk_rel, chunk_id) 排序后等距取）。
另取 ≤20 个「跨科目指纹重复组」（同 fp、kept 与 dropped 科目不同）供漏覆盖核对——
2026-10-11 实测为 **0 组**（全池 739 个多副本 fp 组均单科，详见报告 §3）。

产出：
  build/agent-batch9/skipsample/kit-<层>.json      每层一个件（≤40 块：正文 + 旧判据 + 来源）
  build/agent-batch9/skipsample/cross-subject.json 跨科目 fp 组（≤20 组）
  build/agent-batch9/skipsample/manifest.json      分层总量 / 抽样量 / 缺池计数
判定件（人工/模型判定产物）落 build/agent-batch9/skipsample/verdicts/；
本报告采用的那一轮判定件另存入版本控制：
  tools/kb_coverage/tables/skip-sample-2026-10-11/kit-<层>.verdicts.csv
"""
from __future__ import annotations

import csv
import hashlib
import json
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SKIPS = REPO / "tools/kb_coverage/tables/mechanical_skips.csv"
POOL = REPO / "tools/kb_coverage/tables/extracted_chunks.rekeyed.jsonl"
OUT = REPO / "build" / "agent-batch9" / "skipsample"
N = 40
CROSS = 20


def stratum(note: str) -> str:
    n = note or ""
    if n.startswith("题目派生：正文含成套选项"):
        return "A-选项派生"
    if n.startswith("题目派生：正文含答案") or "解析" in n and n.startswith("题目派生"):
        return "B-答案解析派生"
    if n.startswith("题目派生：标题是题号") or n.startswith("题目派生：正文是短题干"):
        return "C-题号短题干派生"
    if n.startswith("同内容指纹重复"):
        return "D-指纹重复"
    if n.startswith("过短"):
        return "E-过短(附加)"
    return ""


def fp_of(d: dict) -> str:
    if d.get("fp"):
        return str(d["fp"])
    h = hashlib.sha256()
    h.update(" ".join((d.get("text") or "").split()).encode("utf-8"))
    return h.hexdigest()


def block_of(key: tuple[str, str], pool: dict) -> dict | None:
    d = pool.get(key)
    if d is None:
        return None
    return {
        "chunk_rel": key[0],
        "chunk_id": key[1],
        "subject": d.get("subject") or "",
        "heading": d.get("heading") or "",
        "text": d.get("text") or "",
    }


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "verdicts").mkdir(exist_ok=True)

    pool: dict[tuple[str, str], dict] = {}
    order: list[tuple[str, str]] = []
    for line in POOL.open(encoding="utf-8"):
        line = line.strip()
        if not line:
            continue
        d = json.loads(line)
        key = (d.get("rel_path"), d.get("chunk_id"))
        pool[key] = d
        order.append(key)
    print(f"池 {len(pool)} 行")

    rows = list(csv.DictReader(SKIPS.open(encoding="utf-8-sig", newline="")))
    print(f"机械 SKIP 表 {len(rows)} 行")

    groups: dict[str, list[dict]] = defaultdict(list)
    unclassified = 0
    for r in rows:
        s = stratum(r.get("note") or "")
        if s:
            groups[s].append(r)
        else:
            unclassified += 1
    print(f"可分层 {sum(len(v) for v in groups.values())} / 未归类 {unclassified}")

    manifest = {"pool_rows": len(pool), "skip_rows": len(rows), "strata": {}, "missing_in_pool": 0}
    for s, rs in sorted(groups.items()):
        rs.sort(key=lambda r: (r.get("chunk_rel") or "", r.get("chunk_id") or ""))
        take = min(N, len(rs))
        step = max(1, len(rs) // take)
        picked: list[dict] = []
        seen: set[tuple[str, str]] = set()
        for i in range(take):
            r = rs[min((i * step + step // 2), len(rs) - 1)]
            key = (r.get("chunk_rel") or "", r.get("chunk_id") or "")
            if key in seen:
                continue
            seen.add(key)
            picked.append(r)
        blocks = []
        for r in picked:
            key = (r.get("chunk_rel") or "", r.get("chunk_id") or "")
            b = block_of(key, pool)
            if b is None:
                manifest["missing_in_pool"] += 1
                continue
            b["old_note"] = r.get("note") or ""
            b["old_action"] = r.get("action") or ""
            blocks.append(b)
        kit = OUT / f"kit-{s}.json"
        kit.write_text(json.dumps(blocks, ensure_ascii=False, indent=1), encoding="utf-8")
        manifest["strata"][s] = {"total": len(rs), "sampled": len(blocks), "file": kit.name}
        print(f"{s}: 层内 {len(rs)} → 抽 {len(blocks)} → {kit.name}")

    # 跨科目指纹重复组
    fp_members: dict[str, list[tuple[str, str]]] = defaultdict(list)
    for key in order:
        fp_members[fp_of(pool[key])].append(key)
    dup_notes = {}
    for r in rows:
        if (r.get("note") or "").startswith("同内容指纹重复"):
            dup_notes[(r.get("chunk_rel") or "", r.get("chunk_id") or "")] = r.get("note") or ""
    cross = []
    for fp, members in fp_members.items():
        if len(members) < 2:
            continue
        kept = members[0]
        subs = {(pool[m].get("subject") or "") for m in members}
        subs.discard("")
        if len(subs) < 2:
            continue
        dropped = [m for m in members[1:]]
        if not any(m in dup_notes for m in dropped):
            continue
        cross.append({
            "fp": fp[:16],
            "kept": {"key": list(kept), "subject": pool[kept].get("subject") or "",
                     "heading": pool[kept].get("heading") or "", "text": (pool[kept].get("text") or "")[:1200]},
            "dropped": [{"key": list(m), "subject": pool[m].get("subject") or "",
                         "heading": pool[m].get("heading") or "", "text": (pool[m].get("text") or "")[:1200],
                         "old_note": dup_notes.get(m, "")} for m in dropped[:3]],
            "subjects": sorted(subs),
        })
    cross.sort(key=lambda g: g["fp"])
    cross = cross[:CROSS]
    (OUT / "cross-subject.json").write_text(json.dumps(cross, ensure_ascii=False, indent=1), encoding="utf-8")
    manifest["cross_subject_groups"] = len(cross)
    (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"跨科目 fp 组 {len(cross)} 组 → cross-subject.json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
