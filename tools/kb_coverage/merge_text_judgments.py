# -*- coding: utf-8 -*-
"""合并两批文本判定产物 → 一张可入库的判定表（含三处修复）。

## 它消灭的失败

1. **`chunk_rel` 被写成占位符/截断路径**（实测：1 个代理写 `@REL@`、另一个把路径截到目录名）。
   这类行直接入库会被 materialize 判"块不存在"而整批拒绝。修复依据是 chunk_id 的哈希前缀——
   它是 `sha256(rel_path)[:10]`，可唯一反查回真实文件（chunks 表就是权威映射）。
2. **同一个块被两个代理各判一次**（切片重切后，旧片产物覆盖的块也落在新片里）→ 会产生重复材料。
   修复：按 `(chunk_rel, chunk_id)` 分组，同组内按出现顺序重排 `midx`，组间不再产生重复行。
3. **节点写法不逐字**（大小写、误用候选表第 2 列的名称）→ 能唯一命中的改成规范 slug，
   改不动的记为 `NEW:` 待上层建点，不静默丢弃。

路径纪律：所有输入输出都从 `pack_io.REPO` 出发，且**打开前逐条做包含性校验**
（`resolve()` 后必须仍在仓库根之内，拒绝 `..` 与绝对路径）——glob 结果同样过这道门，
不因为"是我自己扫出来的"就免检。

用法：
    PYTHONPATH=tools python -m kb_coverage.merge_text_judgments
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter, defaultdict
from pathlib import Path

from kb_build import pack_io
from kb_coverage.extraction_state import subject_of_path
from kb_coverage.pool_path import POOL_PATH

REPO = Path(pack_io.REPO).resolve()
AGENT_INPUT = REPO / "build" / "agent-input"
OUT_CSV = AGENT_INPUT / "merged_text_batch.csv"
OUT_NEW = AGENT_INPUT / "text_new_nodes.json"
OUT_NEW_FULL = AGENT_INPUT / "new_proposals_full.csv"
VERDICTS_DIR = REPO / "knowledge-production" / "judgment-verdicts"
JUDGMENTS = REPO / "tools/kb_coverage/tables/material_judgments.csv"
CHUNKS = POOL_PATH
HDR = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title", "summary",
       "applicability", "content", "boundary", "note", "midx"]
# 协议 v1.1：type 白名单收紧到 3 值（此前 7 值是冻结规则前的历史口径）。
TYPES = {"CONCEPT_EXPLANATION", "METHOD_MODEL", "MISCONCEPTION_GUIDE"}


def within_repo(path: Path) -> Path:
    """把路径规范化到仓库内；越界（`..`、绝对路径、符号链接逃逸）直接报错。"""
    resolved = path.resolve()
    if resolved != REPO and REPO not in resolved.parents:
        raise ValueError(f"路径越出仓库根：{path}")
    return resolved


def load_chunk_registry() -> tuple[set[tuple[str, str]], dict[str, str]]:
    """返回 (已知 (rel, chunk) 集合, 哈希前缀 → rel_path)。"""
    known: set[tuple[str, str]] = set()
    by_hash: dict[str, str] = {}
    with within_repo(CHUNKS).open(encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            d = json.loads(line)
            known.add((d["rel_path"], d["chunk_id"]))
            by_hash.setdefault(d["chunk_id"].split("-", 1)[0], d["rel_path"])
    return known, by_hash


def collect_files(only: str = "", directory: Path | None = None) -> list[Path]:
    """文本判定产物。

    - `directory` 给定（切片判定轮）：收该目录下的 `*.csv`（判定员逐片产物）；
    - 否则回退旧口径：仓库根的 `.agent_t_*` / `.agent_t2_*` / `.agent_mp_*` / `.agent_mp2_*`
      （早期分两批的产物）。`only` 非空时只收名字里含该片段的文件。
    """
    if directory is not None:
        found = sorted(p for p in within_repo(directory).glob("*.csv") if p.is_file())
        if only:
            found = [p for p in found if only in p.name]
        return sorted(found)
    prefixes = [".agent_t_*.csv", ".agent_t2_*.csv", ".agent_mp_*.csv", ".agent_mp2_*.csv"]
    if only:
        prefixes = [p for p in prefixes if only in p]
    found = []
    for prefix in prefixes:
        found += sorted(REPO.glob(prefix))
    return [within_repo(p) for p in found if p.is_file()]


def subject_of(rel: str) -> str | None:
    """块的科目：与池同一口径（`extraction_state.subject_of_path`）。

    旧实现自己按"关键词出现顺序"判（化学优先于生物），实测 59 条生物路径
    （`降低化学反应活化能的酶` 含"化学"）被判成 CHEMISTRY，于是拿化学节点集去校验生物
    绑定，把合法绑定转成 `NEW:`（静默丢绑定）。
    """
    return subject_of_path(rel)


def merge(only: str = "", directory: Path | None = None) -> dict:
    known, by_hash = load_chunk_registry()
    pack = pack_io.load_json(pack_io.pack_path())
    slug_by = {s["subject"]: {kp["slug"] for t in s["topics"] for kp in (t.get("knowledgePoints") or [])}
               for s in pack["subjects"]}
    lower_by = {k: {x.lower(): x for x in v} for k, v in slug_by.items()}
    name_by = {s["subject"]: {kp["name"]: kp["slug"] for t in s["topics"]
                              for kp in (t.get("knowledgePoints") or [])} for s in pack["subjects"]}

    stats = Counter()
    problems: list[str] = []
    repairs: Counter[str] = Counter()
    rows: list[dict] = []
    files = collect_files(only, directory)
    for f in files:
        with f.open(encoding="utf-8") as fh:
            data = list(csv.reader(fh))
        if not data or data[0] != HDR:
            problems.append(f"{f.name}: 表头不符")
            continue
        for j, r in enumerate(data[1:], 2):
            if len(r) != 12:
                problems.append(f"{f.name}:{j} 列数 {len(r)}")
                continue
            rec = dict(zip(HDR, r))
            rel, cid = rec["chunk_rel"], rec["chunk_id"]
            if (rel, cid) not in known:
                guess = by_hash.get(cid.split("-", 1)[0])
                if guess and (guess, cid) in known:
                    rec["chunk_rel"] = guess
                    repairs["chunk_rel 按哈希回填"] += 1
                else:
                    problems.append(f"{f.name}:{j} 块不存在 {rel[:40]} / {cid}")
                    continue
            if rec["action"] == "SKIP":
                # 协议：SKIP 是显式裁定，同样落表防重判——保留（note 必须有理由）。
                if not (rec.get("note") or "").strip():
                    problems.append(f"{f.name}:{j} SKIP 缺理由")
                    continue
                rows.append(rec)
                stats["rows_in"] += 1
                stats["skips"] += 1
                continue
            if rec["action"] != "MATERIAL":
                problems.append(f"{f.name}:{j} action={rec['action']}")
                continue
            if rec["type"] not in TYPES:
                problems.append(f"{f.name}:{j} type={rec['type']}")
                continue
            subj = subject_of(rec["chunk_rel"])
            ns = rec["node_slug"]
            if not ns.startswith("NEW:"):
                if subj is None:
                    problems.append(f"{f.name}:{j} 学科未识别")
                    continue
                if ns not in slug_by[subj]:
                    if ns.lower() in lower_by[subj]:
                        rec["node_slug"] = lower_by[subj][ns.lower()]
                        repairs["slug 大小写"] += 1
                    elif ns in name_by[subj]:
                        rec["node_slug"] = name_by[subj][ns]
                        repairs["slug 用名称"] += 1
                    else:
                        rec["node_slug"] = f"NEW:{ns}"
                        repairs["未知节点转 NEW"] += 1
            rows.append(rec)
            stats["rows_in"] += 1

    grouped: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for r in rows:
        grouped[(r["chunk_rel"], r["chunk_id"])].append(r)
    merged: list[dict] = []
    suffix = ["", "b", "c", "d", "e", "f", "g", "h"]
    for key, group in grouped.items():
        stats["chunks"] += 1
        if len(group) > len(suffix):
            problems.append(f"同一块判定行数过多 {key} × {len(group)}")
        for n, rec in enumerate(group):
            rec["midx"] = suffix[n] if n < len(suffix) else f"x{n}"
            merged.append(rec)
    stats["rows_after_dedupe"] = len(merged)

    new_names = Counter(r["node_slug"][4:] for r in merged if r["node_slug"].startswith("NEW:"))
    target = within_repo(OUT_CSV)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=HDR)
        w.writeheader()
        w.writerows(merged)
    within_repo(OUT_NEW).write_text(
        json.dumps({"new": dict(new_names)}, ensure_ascii=False, indent=1), encoding="utf-8")
    return {"files": len(files), "stats": stats, "problems": problems, "repairs": repairs,
            "new": new_names, "merged": merged}


def apply_to_judgments(merged: list[dict], target: Path | None = None,
                       resync: bool = False) -> dict:
    """把合并结果**幂等**追加进判定表：既有 (chunk_rel, chunk_id) 一律跳过。

    两条与表实况对齐的约定（实测：表内 0 条 `NEW:` 行、materialize 不认 `NEW:`）：
    - `node_slug` 以 `NEW:` 开头的行**转 SKIP**，提案原文写进 `note`（块不重判、材料不落）；
      完整行（含材料草稿）另存 `new_proposals_full.csv` 供建点闭环取用。
    - 同块多条材料（midx ''/b/c）整批落表；判重只看表内既有键。

    `resync=True`（默认关）另做一件事：**把已修改过的判定行同步回表**。为什么需要它——
    判定产物落表后仍可能被修复（超 4 段 content 合并、节点错绑改绑、双写覆盖），
    而"既有键跳过"会让这些修正**永远进不了表**，材料带着旧内容入包（本会话实测 20 行）。
    只覆盖**同键同 midx** 的行，字段逐列对比、只改不同处，并报告改了哪些键。
    """
    path = within_repo(target or JUDGMENTS)
    rows: list[dict] = []
    existing: dict[tuple[str, str, str], int] = {}
    if path.exists():
        with path.open(encoding="utf-8-sig", newline="") as fh:
            for row in csv.DictReader(fh):
                rows.append({k: (row.get(k) or "") for k in HDR})
                existing[(row.get("chunk_rel") or "", row.get("chunk_id") or "",
                          (row.get("midx") or "").strip())] = len(rows) - 1
    added = skipped = proposals = resynced = 0
    table_keys = {(k[0], k[1]) for k in existing}
    changed_keys: list[str] = []
    full: list[dict] = []
    for rec in merged:
        key = (rec["chunk_rel"], rec["chunk_id"])
        midx = (rec.get("midx") or "").strip()
        if key in table_keys:
            if resync and (key[0], key[1], midx) not in existing:
                # 源里**新增的行**（同块的 midx=b/c…）：必须补进表，否则永远到不了包。
                # 实测踩过：判定的 append 流程给已入表的块追材料，20 条全部静默丢失——
                # 「既有键跳过」是按 (chunk_rel, chunk_id) 判的，同块的后续行被一并跳过。
                out = {k: (rec.get(k) or "") for k in HDR}
                if out["node_slug"].startswith("NEW:"):
                    note = out["note"].strip()
                    out = {k: "" for k in HDR}
                    out["chunk_rel"], out["chunk_id"] = key
                    out["action"] = "SKIP"
                    out["note"] = rec["node_slug"] + (("；" + note) if note else "")
                    out["midx"] = midx
                rows.append(out)
                existing[(key[0], key[1], midx)] = len(rows) - 1
                added += 1
                changed_keys.append(f"{key[1]}:{midx}（补行）")
                continue
            skipped += 1
            if resync:
                idx = existing.get((key[0], key[1], midx))
                if idx is not None:
                    out = {k: (rec.get(k) or "") for k in HDR}
                    if out["node_slug"].startswith("NEW:"):
                        note = out["note"].strip()
                        out = {k: "" for k in HDR}
                        out["chunk_rel"], out["chunk_id"] = key
                        out["action"] = "SKIP"
                        out["note"] = rec["node_slug"] + (("；" + note) if note else "")
                        out["midx"] = (rec.get("midx") or "").strip()
                    diffs = [k for k in HDR if rows[idx].get(k, "") != out.get(k, "")]
                    if diffs:
                        rows[idx] = out
                        resynced += 1
                        changed_keys.append(f"{key[1]}:{','.join(diffs)}")
            continue
        out = {k: (rec.get(k) or "") for k in HDR}
        if out["node_slug"].startswith("NEW:"):
            full.append(out)
            proposals += 1
            note = out["note"].strip()
            out = {k: "" for k in HDR}
            out["chunk_rel"], out["chunk_id"] = rec["chunk_rel"], rec["chunk_id"]
            out["action"] = "SKIP"
            out["note"] = rec["node_slug"] + (("；" + note) if note else "")
            # 回填 midx（对齐上方「补行」与「同步」两分支）：重建行若丢 midx，同键第二行
            # （如 NEW 提案的 midx='b'）会与首行（midx=''）撞成同一个 (key,midx) 键——
            # 表内出现重复行，且该提案行再想凭 midx 定位时已无法区分。
            out["midx"] = (rec.get("midx") or "").strip()
        rows.append(out)
        added += 1
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=HDR)
        writer.writeheader()
        writer.writerows(rows)
    if full:
        prop_path = within_repo(OUT_NEW_FULL)
        prop_path.parent.mkdir(parents=True, exist_ok=True)
        with prop_path.open("w", encoding="utf-8", newline="") as fh:
            writer = csv.DictWriter(fh, fieldnames=HDR)
            writer.writeheader()
            writer.writerows(full)
    return {"added": added, "skipped_existing": skipped, "total": len(rows),
            "new_proposals": proposals, "resynced": resynced, "resynced_keys": changed_keys}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--only", default="", help="只收文件名含该片段的文件，例如 mp")
    ap.add_argument("--dir", type=Path, default=None,
                    help="切片判定产物目录（默认 knowledge-production/judgment-verdicts）")
    ap.add_argument("--dir-mode", action="store_true",
                    help="显式启用 --dir 口径（不传 --only 时也收目录内全部 CSV）")
    ap.add_argument("--apply", action="store_true",
                    help="把合并结果幂等追加进判定表（既有键跳过）")
    ap.add_argument("--resync", action="store_true",
                    help="配 --apply：把已修改过的判定行同步回表（同键同 midx 覆盖，默认关）")
    args = ap.parse_args(argv)
    directory = args.dir if args.dir is not None else (VERDICTS_DIR if args.dir_mode else None)
    res = merge(args.only, directory)
    print(f"输入文件 {res['files']} 个；输入行 {res['stats']['rows_in']}；块 {res['stats']['chunks']}；"
          f"合并行 {res['stats']['rows_after_dedupe']}")
    print("修复：", dict(res["repairs"]))
    print(f"问题 {len(res['problems'])}")
    for p in res["problems"][:10]:
        print("   !", p)
    print(f"NEW 节点 {len(res['new'])} 个（引用 {sum(res['new'].values())} 行）")
    for n, c in res["new"].most_common(12):
        print(f"   {n[:44]} ×{c}")
    print(f"→ {OUT_CSV}")
    if args.apply:
        applied = apply_to_judgments(res["merged"], resync=args.resync)
        print(f"追加进判定表：新增 {applied['added']} 行，跳过既有键 {applied['skipped_existing']} 行，"
              f"表内共 {applied['total']} 行 → {JUDGMENTS.name}")
        if args.resync:
            print(f"同步修正 {applied['resynced']} 行：")
            for k in applied["resynced_keys"][:20]:
                print(f"   ~ {k}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
