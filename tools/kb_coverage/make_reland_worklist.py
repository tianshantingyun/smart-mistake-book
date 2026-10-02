# -*- coding: utf-8 -*-
"""生成「材料重落」工件单 v2：**以规划表的 `evidence_chunk` 为主源**。

## 它消灭的失败（v1 的两个错）

v1 只扫判定源 note 里的 `NEW:` 串来配块，实测漏了 9 个点（MHC与抗原呈递、胚胎移植…）——
它们的 note 写法不同，但规划表里**每个点都自带 `evidence_chunk`**（块定位），那才是权威主源。
v1 对着 note 清洗名字还会把 `铅及其化合物的性质…`、`平面的概念与表示：…）` 判成新点名，
而这两个**包内已存在**（该走改绑）。

v2 的规则：
- 主源 = 规划表每个点的 `evidence_chunk`（`<chunk_rel>#<chunk_id>`）→ 建点用 `NEW:<slug>` 行；
- 名字若**包内已存在**（按科查）→ 降级为 `REBIND:<既有 slug>`；
- 定位判定源行：在 `judgment-verdicts/*.jsonl.csv` 里按 `chunk_id` 找那一行（跨片键唯一）；
  该行若是 `SKIP` → **改写它**（`rewrite`）；若已是 `MATERIAL` → 需要**新加一行**（`append`，midx 由代理取下一个空闲后缀）；
- note 里额外出现的 `NEW:` 名字统一按同一套清洗映射（能对上既有节点/规划点的收进工件单，其余进 `unmatched`）。

输出：`build/agent-input/reland_worklist.csv`（+ `.unmatched.csv`）

用法：`PYTHONPATH=tools python tools/kb_coverage/make_reland_worklist.py [--write]`
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from kb_build import pack_io  # noqa: E402
from kb_coverage.extraction_state import subject_of_path  # noqa: E402
from kb_coverage.plan_block_proposals import clean_name  # noqa: E402

REPO = TOOLS.parent
VERDICTS = REPO / "knowledge-production" / "judgment-verdicts"
PLAN = REPO / "build" / "agent-input" / "block_proposals_plan.json"
OUT = REPO / "build" / "agent-input" / "reland_worklist.csv"
OUT_BAD = REPO / "build" / "agent-input" / "reland_worklist_unmatched.csv"
NEW_RE = re.compile(r"NEW:([A-Z]+)/(\S+)")
STRAY = re.compile(r"^[（()）\s]+|[（()）\s]+$")
COLS = ["mode", "kind", "subject", "slug", "chunk_rel", "chunk_id", "verdict_file", "midx",
        "row_action", "note_raw"]


def match_name(raw: str) -> str:
    return STRAY.sub("", clean_name(raw.strip()) or raw.strip())


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)
    if not args.write:
        print("（只读；加 --write 落盘）")

    pack = pack_io.load_json(pack_io.pack_path())
    have = {s["subject"]: {k["slug"] for t in s["topics"] for k in t.get("knowledgePoints") or []}
            for s in pack["subjects"]}
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    # 本轮新建的点（不能用"包内是否存在"判 NEW/REBIND——这些点刚被 create_points 建出来）
    plan_slugs = {p["slug"] for p in plan["plan"]}

    # 判定源索引：chunk_id → (file, row)
    rows: list[dict] = []
    index: dict[str, tuple[Path, dict]] = {}
    for path in sorted(VERDICTS.glob("*.jsonl.csv")):
        for r in csv.DictReader(path.open(encoding="utf-8-sig", newline="")):
            index.setdefault(r["chunk_id"], (path, r))

    def emit(point_subject: str, slug: str, chunk: str, note_raw: str) -> None:
        rel, _, cid = chunk.rpartition("#")
        hit = index.get(cid)
        if not hit:
            rows.append({"mode": "MISSING", "kind": "", "subject": point_subject, "slug": slug,
                         "chunk_rel": rel, "chunk_id": cid, "verdict_file": "", "midx": "",
                         "row_action": "", "note_raw": "证据块不在判定源里"})
            return
        path, r = hit
        action = r["action"]
        # 本轮新建的点一律 NEW；其余（既有节点）算 REBIND
        kind = "NEW" if slug in plan_slugs else "REBIND"
        rows.append({"mode": "append" if action == "MATERIAL" else "rewrite",
                     "kind": kind, "subject": point_subject, "slug": slug,
                     "chunk_rel": r["chunk_rel"], "chunk_id": cid, "verdict_file": path.name,
                     "midx": (r.get("midx") or "").strip(), "row_action": action,
                     "note_raw": note_raw[:80]})

    # ① 主源：规划表的每个点（这些点**本轮刚建**，所以不能再用"包内是否存在"判 NEW/REBIND）
    plan_slugs = {p["slug"] for p in plan["plan"]}
    for p in plan["plan"]:
        emit(p["subject"], p["slug"], p["evidence_chunk"], "plan.evidence_chunk")
    # ② 改绑提案：从 note 反查块（规划表的 rebind 只有名字→既有 slug，没有块定位）
    rebind_by_name: dict[str, str] = {}
    for name, target in plan["rebind"].items():
        rebind_by_name[match_name(name)] = target
    for path in sorted(VERDICTS.glob("*.jsonl.csv")):
        for r in csv.DictReader(path.open(encoding="utf-8-sig", newline="")):
            text = f"{r.get('node_slug') or ''} {r.get('note') or ''}"
            for _subj, raw in NEW_RE.findall(text):
                name = match_name(raw)
                if name in rebind_by_name:
                    rows.append({"mode": "append" if r["action"] == "MATERIAL" else "rewrite",
                                 "kind": "REBIND", "subject": subject_of_path(r["chunk_rel"]) or "",
                                 "slug": rebind_by_name[name], "chunk_rel": r["chunk_rel"],
                                 "chunk_id": r["chunk_id"], "verdict_file": path.name,
                                 "midx": (r.get("midx") or "").strip(), "row_action": r["action"],
                                 "note_raw": f"plan.rebind:{name}"[:80]})

    unmatched: list[dict] = []
    for path in sorted(VERDICTS.glob("*.jsonl.csv")):
        for r in csv.DictReader(path.open(encoding="utf-8-sig", newline="")):
            text = f"{r.get('node_slug') or ''} {r.get('note') or ''}"
            for subj, raw in NEW_RE.findall(text):
                name = match_name(raw)
                if name in have.get(subj, set()):
                    unmatched.append({"reason": "其名已是既有节点（改绑候选，但不在规划 rebind 里）",
                                      "subject": subj, "slug": name, "chunk_id": r["chunk_id"],
                                      "verdict_file": path.name, "note_raw": raw[:70]})
                else:
                    unmatched.append({"reason": "名不在规划表（多为整句话/超长，未建点）",
                                      "subject": subj, "slug": name, "chunk_id": r["chunk_id"],
                                      "verdict_file": path.name, "note_raw": raw[:70]})

    if args.write:
        with OUT.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=COLS)
            w.writeheader()
            w.writerows(rows)
        with OUT_BAD.open("w", encoding="utf-8", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=["reason", "subject", "slug", "chunk_id",
                                               "verdict_file", "note_raw"])
            w.writeheader()
            w.writerows(unmatched)

    def count(key: str) -> dict:
        out: dict[str, int] = {}
        for r in rows:
            out[r[key]] = out.get(r[key], 0) + 1
        return out

    print(f"工件单 {len(rows)} 件：mode={count('mode')} kind={count('kind')}")
    print(f"未匹配名单 {len(unmatched)} 条 → {OUT_BAD.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
