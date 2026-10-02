# -*- coding: utf-8 -*-
"""块池判定的 NEW 提案 → 可建点规划（复用 `plan_new_nodes` 的归属规则与近重复归并）。

## 它消灭的失败

判定员按协议规则 7 在 SKIP 行的 `note` 里写 `NEW:<subject>/<slug>` 建点提案。这些提案此前
**没有入口**：`plan_new_nodes` 只读 `merged_text_batch.csv` 里 `node_slug` 以 `NEW:` 开头的
**MATERIAL** 行（rejudge 通道），而块池轮把提案写在 **SKIP 行的 note** 里。结果 232 条提案
只活在一个手工建的 CSV（`knowledge-production/block-judgment-proposals-2026-09-30.csv`）里，
没有任何工具消费它，`create_points` 永远收不到这些点——"正式区确缺的考点"就停在提案上。

本工具把两处来源统一收拢（逐片 verdict CSV 的 note / node_slug，加判定表），复用
`plan_new_nodes` 的：① 归属正则规则 ② canonical 近重复归并 ③ 坏名门 ④ 既有节点包含匹配兜底，
产出与它**同形**的规划表供 `create_points` 取用。

## 用法

    PYTHONPATH=tools python -m kb_coverage.plan_block_proposals            # 报告
    PYTHONPATH=tools python -m kb_coverage.plan_block_proposals --write    # 落 plan json + csv
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from kb_build import gate, pack_io  # noqa: E402
from kb_coverage.extraction_state import subject_of_path  # noqa: E402
from kb_coverage.plan_new_nodes import canonical, fallback, norm, rules  # noqa: E402

REPO = TOOLS.parent
VERDICTS = REPO / "knowledge-production" / "judgment-verdicts"
JUDGMENTS = REPO / "tools" / "kb_coverage" / "tables" / "material_judgments.csv"
OUT_JSON = REPO / "build" / "agent-input" / "block_proposals_plan.json"
OUT_CSV = REPO / "build" / "agent-input" / "block_proposals_plan.csv"
MANUAL_COLS = ("subject", "slug", "name", "kind", "parent_topic_slug", "boundary", "source_locator")
NEW_RE = re.compile(r"NEW:([A-Z]+)/(\S+)")
# 判定员常把解释写进提案名（实测 212 条里 119 条），如
# `DNA片段的电泳鉴定（正式区无对口节点，本条为电泳原理与迁移速率）`。
# 壳是括号（中英）里的补充说明，去掉后才是**知识点名**；去掉后仍不过坏名门才判人工处置。
SHELL_RE = re.compile(r"[（(][^（()）]*[)）]")
# 另一种写法是破折号接解释：`准晶体——准晶体介于晶体与非晶体之间、具有原子排列有序性但无周期性`。
# 知识点名在破折号之前。
DASH_RE = re.compile(r"[—–-]{2,}")


def clean_name(name: str) -> str:
    """剥掉提案名里的解释尾巴（括号补充 / 破折号后的说明），得到知识点名。

    最后一步只处理**未闭合**的括号：剥掉成对的之后还剩下的 `（`/`(` 必然是判定员写解释时
    被截断的（如 `内能（正式区缺内能节点；本块给出内能的定义…`），知识点名在它之前。
    """
    head = DASH_RE.split(name, 1)[0]
    head = SHELL_RE.sub("", head)
    for op in ("（", "("):
        if op in head:
            head = head.split(op, 1)[0]
    return re.sub(r"\s+", " ", head).strip(" ·，,。;；：:")


def collect() -> tuple[dict[str, str], dict[str, dict], list[str]]:
    """返回 (提案名 → 证据行, 提案名 → 归并别名, 问题)。

    证据行取**第一个**出现的块：chunk_rel 决定科目与来源，正文首段进 boundary。
    """
    evidence: dict[str, dict] = {}
    subject_by: dict[str, str] = {}
    problems: list[str] = []
    sources: list[tuple[Path, str]] = []
    if VERDICTS.exists():
        sources += [(p, "verdict") for p in sorted(VERDICTS.glob("*.jsonl.csv"))]
    for path, kind in sources:
        with path.open(encoding="utf-8-sig", newline="") as fh:
            for row in csv.DictReader(fh):
                text = f"{row.get('node_slug') or ''} {row.get('note') or ''}"
                for subj, name in NEW_RE.findall(text):
                    key = name.strip()
                    if not key:
                        continue
                    if subj not in ("MATH", "PHYSICS", "CHEMISTRY", "BIOLOGY"):
                        problems.append(f"{path.name}: 提案科目非法 {subj}（{key[:30]}）")
                        continue
                    subject_by.setdefault(key, subj)
                    evidence.setdefault(key, {
                        "subject": subj,
                        "name": key,
                        "chunk_rel": row.get("chunk_rel") or "",
                        "chunk_id": row.get("chunk_id") or "",
                        "text": (row.get("content") or row.get("summary") or "").strip(),
                    })
    # 判定表里可能还有早前合并进来的提案行
    if JUDGMENTS.exists():
        with JUDGMENTS.open(encoding="utf-8-sig", newline="") as fh:
            for row in csv.DictReader(fh):
                note = row.get("note") or ""
                for subj, name in NEW_RE.findall(note):
                    key = name.strip()
                    if key and key not in evidence:
                        subject_by.setdefault(key, subj)
                        evidence.setdefault(key, {
                            "subject": subj, "name": key,
                            "chunk_rel": row.get("chunk_rel") or "",
                            "chunk_id": row.get("chunk_id") or "",
                            "text": (row.get("content") or "").strip(),
                        })
    return subject_by, evidence, problems


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)

    pack = pack_io.load_json(pack_io.pack_path())
    topics = {s["subject"]: {t["slug"] for t in s["topics"]} for s in pack["subjects"]}
    name_by: dict[str, dict[str, str]] = {}
    for s in pack["subjects"]:
        for t in s["topics"]:
            for kp in t.get("knowledgePoints") or []:
                name_by.setdefault(s["subject"], {}).setdefault(norm(kp["name"]), kp["slug"])
                name_by[s["subject"]].setdefault(norm(kp["slug"]), kp["slug"])

    subject_by, evidence, problems = collect()
    # 先把"解释写进名字"的提案按**清洗后的知识点名**归并，再走后续判定
    cleaned_from: dict[str, list[str]] = defaultdict(list)
    by_clean: dict[str, str] = {}
    for name, subj in subject_by.items():
        clean = clean_name(name) or name
        by_clean[name] = clean
        cleaned_from[f"{subj}|{clean}"].append(name)
    canonical_of = {name: clean_name(name) or name for name in subject_by}
    subject_of_clean: dict[str, str] = {}
    for name, subj in subject_by.items():
        subject_of_clean.setdefault(f"{subj}|{canonical_of[name]}", subj)

    groups: dict[tuple[str, str], list[str]] = defaultdict(list)
    for name, subj in subject_by.items():
        groups[(subj, canonical(canonical_of[name]))].append(name)
    alias: dict[str, str] = {}
    for names in groups.values():
        # 代表优先取"干净"的原名（未改写的那个），否则取最短
        rep = min(names, key=lambda n: (canonical_of[n] != n, len(canonical_of[n]), n))
        for n in names:
            if n != rep:
                alias[n] = rep

    plan, rebind, bad = [], {}, []
    for name in sorted(subject_by, key=lambda n: (subject_by[n], n)):
        if name in alias:
            continue
        ev = evidence.get(name)
        if ev is None:
            bad.append((name, "无证据行"))
            continue
        subj = ev["subject"]
        members = [name] + [k for k, v in alias.items() if v == name]
        point = canonical_of[name]                      # 清洗后的知识点名
        notes: list[str] = []
        for m in members:
            parts = DASH_RE.split(m, 1)
            if len(parts) > 1 and parts[1].strip():
                notes.append(parts[1].strip())
            notes += [s.strip() for s in SHELL_RE.findall(m) if s.strip()]
        existing = name_by.get(subj, {}).get(norm(point))
        if existing:
            rebind.update({m: existing for m in members})
            continue
        cand = norm(point)
        near = [slug for key, slug in name_by.get(subj, {}).items()
                if abs(len(key) - len(cand)) <= 4 and (key in cand or cand in key)]
        if len(set(near)) == 1:
            rebind.update({m: near[0] for m in members})
            continue
        verdict = gate._is_bad_name(point)
        if verdict:
            bad.append((point, f"坏名：{verdict}"))
            continue
        if len(point) > 24:
            bad.append((point, f"超长 {len(point)}"))
            continue
        target = None
        for pat, slug in rules().get(subj, []):
            if len(slug.split("·")) < 2:
                continue
            if re.search(pat, point) and slug in topics[subj]:
                target = slug
                break
        if target is None:
            target = fallback(subj, topics[subj])
        seg = target.split("·")
        place = f"{subj}·综合·综合" if target.endswith("综合·综合·综合") else f"{seg[0]} {seg[1]}"
        excerpt = re.sub(r"\s+", " ", ev["text"]).replace(",", "，").replace('"', "”")
        plan.append({
            "subject": subj, "slug": point, "name": point,
            "kind": "REASONING" if re.search(r"辨析|易错|陷阱|注意", point) else "CONCEPT",
            "parent_topic_slug": target,
            "boundary": f"定位：{place}。{excerpt[:100]}",
            "source_locator": f"块池判定·{ev['chunk_rel'].split('/')[0] or '未知来源'}",
            "place": place,
            "evidence_chunk": f"{ev['chunk_rel']}#{ev['chunk_id']}",
            "alias_written_as": [m for m in members if m != name],
            "name_cleaned_from": name if name != point else "",
            "agent_notes": "；".join(dict.fromkeys(notes))[:160],
        })
    print(f"提案名 {len(subject_by)}：可建点 {len(plan)}、可改绑既有节点 {len(rebind)}、需人工处置 {len(bad)}")
    print("按科：", Counter(p["subject"] for p in plan).most_common())
    print("归属分布（前 10）：")
    for t, c in Counter(p["parent_topic_slug"] for p in plan).most_common(10):
        print(f"   {c:>4}  {t[:60]}")
    if bad:
        print("需人工处置：")
        for n, why in bad[:10]:
            print(f"   ! {n[:34]} — {why}")
    if problems:
        print(f"问题 {len(problems)}：")
        for p in problems[:6]:
            print("   !", p)

    if args.write:
        OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
        OUT_JSON.write_text(json.dumps({"plan": plan, "rebind": rebind, "bad": bad},
                                       ensure_ascii=False, indent=1), encoding="utf-8")
        with OUT_CSV.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=list(MANUAL_COLS) + ["place", "evidence_chunk",
                                                                  "alias_written_as"])
            w.writeheader()
            for p in plan:
                row = {k: p[k] for k in MANUAL_COLS}
                row["alias_written_as"] = "|".join(p["alias_written_as"])
                row["place"], row["evidence_chunk"] = p["place"], p["evidence_chunk"]
                w.writerow(row)
        print(f"→ {OUT_JSON}\n→ {OUT_CSV}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
