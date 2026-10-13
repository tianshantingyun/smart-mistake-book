# -*- coding: utf-8 -*-
"""把判定源里指向**已并入死节点**的 MATERIAL 行归一：沿 point_merge.csv 链解析到 staging 存活节点。

## 它消灭的失败

批次 9 的 382 条并点后，判定源（`knowledge-production/judgment-verdicts/*.jsonl.csv`）
仍有 MATERIAL 行的 `node_slug` 指向已并入的旧节点（实测 2,924 行 / 373 slug / 202 文件）。
`merge_text_judgments --resync` 的语义是**源优先**：这些行不归一，下一次合并就把判定表里的
绑定再打回（批次 9 C3 已实际发生）——轻则材料绑定被判回 `NEW:`/SKIP，重则 slug 撞车整批拒绝。

## 规则（机械、可复核）

- 只改 `node_slug` 一列（其余 11 列逐字不动）；SKIP 行不动；`NEW:` 开头不动。
- 行科目：切片记录（`tables/judgment_slices/<同名>.jsonl`）的 `subject`（与
  `check_slice_verdicts` 同口径）；切片缺失时回退 `extraction_state.subject_of_path`。
- 节点在该科 staging 包内 → 不动；不在 → 沿 `point_merge.csv` 的
  `(subject, merged_slug) → survivor_slug` 链解析，直到落在该科存活节点。
  断言：无环、无多目标、终态在 staging 包内；解析不到/有环/多目标/触及
  point_delete·point_rename → 记异常并 exit 1，**不写盘**（不猜）。
- 字节级写回：按记录边界（含引号字段内的原始换行）只拼接被改字段的原文跨度；
  EOL 逐文件原样保留（CRLF/LF/混合都不规范化，不做任何行尾转换）；
  有任何改动的文件才落盘，未改动文件一个字节都不写。

用法：
    python tools/kb_coverage/normalize_source_slugs.py             # dry-run（默认）
    python tools/kb_coverage/normalize_source_slugs.py --write     # 应用
    python tools/kb_coverage/normalize_source_slugs.py --report 路径 # 报告落盘位置
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import sys
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
if str(REPO / "tools") not in sys.path:      # 直跑引导
    sys.path.insert(0, str(REPO / "tools"))

from kb_build import pack_io  # noqa: E402
from kb_coverage.extraction_state import subject_of_path  # noqa: E402
from kb_coverage import check_slice_verdicts as _slice_gate  # noqa: E402

VERDICTS = REPO / "knowledge-production/judgment-verdicts"
POINT_MERGE = REPO / "tools/kb_build/tables/point_merge.csv"
POINT_DELETE = REPO / "tools/kb_build/tables/point_delete.csv"
POINT_RENAME = REPO / "tools/kb_build/tables/point_rename.csv"
SLICES = _slice_gate.SLICES
REPORT = REPO / "build" / "agent-batch10" / "a1" / "normalization-report.txt"
HDR = ["chunk_rel", "chunk_id", "action", "node_slug", "type", "title", "summary",
       "applicability", "content", "boundary", "note", "midx"]
NODE_COL = HDR.index("node_slug")


# --------------------------------------------------------------------------- 输入表

def load_pack_nodes() -> dict[str, set[str]]:
    """staging 包 → {科目: {slug}}（与 check_slice_verdicts._pack_nodes 同口径）。"""
    pack = pack_io.load_json(pack_io.pack_path())
    out: dict[str, set[str]] = {}
    for subject, _topic, point in pack_io.iter_points(pack):
        out.setdefault(subject, set()).add(point["slug"])
    return out


def _read_table(path: Path, cols: tuple[str, ...]) -> list[dict]:
    if not path.exists():
        return []
    with path.open(encoding="utf-8", newline="") as fh:
        return [{c: (r.get(c) or "") for c in cols} for r in csv.DictReader(fh)]


def load_merge_map() -> dict[tuple[str, str], str]:
    """point_merge.csv → {(科目, merged_slug): survivor_slug}。

    同键两个不同 survivor / 自环（merged == survivor）直接拒绝：链的每一步都必须无歧义。
    """
    out: dict[tuple[str, str], str] = {}
    for r in _read_table(POINT_MERGE, ("subject", "survivor_slug", "merged_slug", "reason")):
        key = (r["subject"], r["merged_slug"])
        if r["merged_slug"] == r["survivor_slug"]:
            raise SystemExit(f"point_merge 自环：{key}")
        prev = out.get(key)
        if prev is not None and prev != r["survivor_slug"]:
            raise SystemExit(f"point_merge 同键多目标：{key} → {prev!r} / {r['survivor_slug']!r}")
        out[key] = r["survivor_slug"]
    return out


def load_aux_slugs() -> tuple[set[tuple[str, str]], set[tuple[str, str]]]:
    """point_delete / point_rename 的 (科目, slug) 集合（安全网：这两类归并不走 merge 链）。"""
    dels = {(r["subject"], r["slug"]) for r in _read_table(POINT_DELETE, ("subject", "slug", "reason"))}
    rens = {(r["subject"], r["slug"]) for r in _read_table(POINT_RENAME, ("subject", "slug", "new_name", "reason"))}
    return dels, rens


def load_slice_subjects(directory: Path) -> dict[str, dict[tuple[str, str], str]]:
    """同名切片文件 → {(chunk_rel, chunk_id): subject}（键口径与 check_slice_verdicts 一致）。"""
    import json
    out: dict[str, dict[tuple[str, str], str]] = {}
    for path in sorted(directory.glob("*.jsonl.csv")):
        sp = SLICES / path.name[:-4]
        m: dict[tuple[str, str], str] = {}
        if sp.exists():
            for line in sp.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if not line:
                    continue
                rec = json.loads(line)
                m[(rec["chunk_rel"], rec["chunk_id"])] = rec.get("subject") or ""
        out[path.name] = m
    return out


# --------------------------------------------------------------------------- 记录切分

def tokenize(text: str) -> list[tuple[int, int, list[tuple[int, int]]]]:
    """CSV 文本 → [(记录起点, 记录终点[不含行尾], [(字段起点, 字段终点), ...]), ...]。

    - 引号字段内的 `\\r`/`\\n` 属字段内容，不切记录；字段跨度含引号本身。
    - 空行（零长记录）跳过，但仍保留在原文里（本工具只拼接字段跨度）。
    - 引号未闭合 / 引号收尾后既非逗号也非行尾 → ValueError（拒绝在坏结构上写盘）。
    """
    records: list[tuple[int, int, list[tuple[int, int]]]] = []
    i, n = 0, len(text)
    while i < n:
        start = i
        fields: list[tuple[int, int]] = []
        while True:
            if i < n and text[i] == '"':
                j = i + 1
                while True:
                    if j >= n:
                        raise ValueError(f"引号未闭合 @{i}")
                    if text[j] == '"':
                        if j + 1 < n and text[j + 1] == '"':
                            j += 2
                            continue
                        j += 1
                        break
                    j += 1
                end = j
                fields.append((i, end))
                if j < n and text[j] not in ",\r\n":
                    raise ValueError(f"引号字段收尾后有杂字符 @{j}: {text[j]!r}")
            else:
                j = i
                while j < n and text[j] not in ",\r\n":
                    j += 1
                fields.append((i, j))
            if j < n and text[j] == ",":
                i = j + 1
                continue
            break
        rec_end = j
        if len(fields) == 1 and fields[0][0] == fields[0][1] and rec_end == start:
            if text[j:j + 2] == "\r\n":
                i = j + 2
            elif j < n and text[j] in "\r\n":
                i = j + 1
            else:
                i = j
            continue
        if text[j:j + 2] == "\r\n":
            i = j + 2
        elif j < n and text[j] in "\r\n":
            i = j + 1
        else:
            i = j
        records.append((start, rec_end, fields))
    return records


def decode_field(text: str, span: tuple[int, int]) -> str:
    raw = text[span[0]:span[1]]
    if raw.startswith('"') and raw.endswith('"') and len(raw) >= 2:
        return raw[1:-1].replace('""', '"')
    return raw


def encode_field(value: str) -> str:
    """仅 node_slug 用：slug 必须是无逗号/引号/换行的裸值，否则拒绝（不猜引号写法）。"""
    if any(ch in value for ch in ',\r\n"'):
        raise ValueError(f"目标 slug 含 CSV 特殊字符，拒绝拼接：{value!r}")
    return value


def assert_tokenizer_matches_csv(text: str, records: list[tuple[int, int, list[tuple[int, int]]]],
                                 name: str) -> None:
    """分词结果必须与标准 csv 模块逐记录逐字段一致（防御分词器自身出错）。"""
    expected = [r for r in csv.reader(io.StringIO(text)) if r != []]
    if len(expected) != len(records):
        raise ValueError(f"{name}: 分词记录数 {len(records)} ≠ csv {len(expected)}")
    for k, (exp, (_s, _e, spans)) in enumerate(zip(expected, records)):
        got = [decode_field(text, sp) for sp in spans]
        if got != exp:
            raise ValueError(f"{name}: 记录 {k} 字段不符：{got[:3]} vs {exp[:3]}")


def classify_eol(records: list[tuple[int, int, list[tuple[int, int]]]], text: str) -> str:
    """按记录终止符分类（CRLF/LF/MIXED）；字段内嵌换行不参与分类。"""
    kinds = set()
    for _s, e, _f in records:
        if text[e:e + 2] == "\r\n":
            kinds.add("CRLF")
        elif e < len(text) and text[e] in "\r\n":
            kinds.add("LF")
        else:
            kinds.add("EOF")
    if kinds <= {"CRLF"}:
        return "CRLF"
    if kinds <= {"LF"}:
        return "LF"
    if kinds <= {"EOF"}:
        return "EOF"
    return "MIXED"


# --------------------------------------------------------------------------- 解析链

def resolve(subject: str, slug: str, merge_map: dict[tuple[str, str], str],
            nodes: dict[str, set[str]]) -> tuple[str | None, str]:
    """沿 point_merge 链解析到该科存活节点。

    返回 (存活 slug, "alive") 或 (None, 原因)：no_entry / cycle / terminal_not_alive。
    终态必须在 staging 包内——这是本工具唯一的"存活"判据。
    """
    seen: set[str] = set()
    cur = slug
    while True:
        if cur in nodes.get(subject, set()):
            return cur, "alive"
        nxt = merge_map.get((subject, cur))
        if nxt is None:
            return None, "no_entry"
        if cur in seen:
            return None, "cycle"
        seen.add(cur)
        cur = nxt


# --------------------------------------------------------------------------- 写回

def apply_changes(text: str, changes: list[tuple[int, int, int, str]]) -> str:
    """changes = [(记录序号, 字段跨度, 旧值, 新值)]，只拼接字段跨度，文本其余逐字保留。"""
    parts: list[str] = []
    last = 0
    for _rec, (s, e), _old, new in sorted(changes, key=lambda c: c[1][0]):
        parts.append(text[last:s])
        parts.append(encode_field(new))
        last = e
    parts.append(text[last:])
    return "".join(parts)


def verify_rewrite(old_text: str, new_text: str,
                   changes: list[tuple[int, int, int, str]], name: str) -> None:
    """改后复算：记录数不变；被改记录只有 node_slug 变了；未改记录逐字节相同。"""
    old_recs = tokenize(old_text)
    new_recs = tokenize(new_text)
    if len(old_recs) != len(new_recs):
        raise ValueError(f"{name}: 改写后记录数 {len(new_recs)} ≠ {len(old_recs)}")
    changed_idx = {rec for rec, _sp, _o, _n in changes}
    for idx, ((os_, oe, ospans), (ns_, ne, nspans)) in enumerate(zip(old_recs, new_recs)):
        if idx in changed_idx:
            of = [decode_field(old_text, sp) for sp in ospans]
            nf = [decode_field(new_text, sp) for sp in nspans]
            want = next(n for r, _sp, _o, n in changes if r == idx)
            if len(of) != len(nf):
                raise ValueError(f"{name}: 记录 {idx} 字段数变了 {len(of)}→{len(nf)}")
            for k in range(len(of)):
                if k == NODE_COL:
                    if nf[k] != want:
                        raise ValueError(f"{name}: 记录 {idx} node_slug={nf[k]!r} ≠ {want!r}")
                elif of[k] != nf[k]:
                    raise ValueError(f"{name}: 记录 {idx} 第 {k} 列被动了：{of[k]!r} → {nf[k]!r}")
        else:
            if old_text[os_:oe] != new_text[ns_:ne]:
                raise ValueError(f"{name}: 未改记录 {idx} 字节变了")
    if not changes:
        return
    for rec, sp, old, _new in changes:
        if decode_field(old_text, sp) != old:
            raise ValueError(f"{name}: 记录 {rec} 原文与记录值不符")


# --------------------------------------------------------------------------- 主流程

def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dir", type=Path, default=VERDICTS, help="判定源目录（*.jsonl.csv）")
    ap.add_argument("--write", action="store_true", help="应用改动（默认 dry-run）")
    ap.add_argument("--report", type=Path, default=REPORT, help="报告落盘路径")
    args = ap.parse_args(argv)

    nodes = load_pack_nodes()
    merge_map = load_merge_map()
    del_slugs, ren_slugs = load_aux_slugs()
    slices = load_slice_subjects(args.dir)

    files = sorted(p for p in args.dir.glob("*.jsonl.csv") if p.is_file())
    lines: list[str] = []
    def log(s: str = "") -> None:
        lines.append(s)

    total_rows = material_rows = skip_rows = new_rows = 0
    flagged_rows = 0
    pending_by_file: dict[Path, list[tuple[int, int, int, str]]] = {}
    mapping: Counter[tuple[str, str, str]] = Counter()
    mapping_keys: dict[tuple[str, str], str] = {}
    flagged_slugs: set[str] = set()
    subject_fallback = 0
    eol_stats: Counter[str] = Counter()
    anomalies: list[str] = []
    chains: set[tuple[str, str]] = set()
    per_file: list[tuple[int, str]] = []

    file_digest: dict[Path, str] = {}
    for path in files:
        try:
            raw = path.read_bytes()
            text = raw.decode("utf-8")
            records = tokenize(text)
            assert_tokenizer_matches_csv(text, records, path.name)
        except (UnicodeDecodeError, ValueError) as exc:
            anomalies.append(f"{path.name}: {exc}")
            continue
        file_digest[path] = hashlib.sha256(raw).hexdigest()
        eol_stats[classify_eol(records, text)] += 1
        if not records:
            anomalies.append(f"{path.name}: 文件无任何记录")
            continue
        header = [decode_field(text, sp).lstrip("\ufeff") for sp in records[0][2]]
        if header != HDR:
            anomalies.append(f"{path.name}: 表头不符 {header}")
            continue
        file_changes: list[tuple[int, int, int, str]] = []
        file_rows = 0
        sm = slices.get(path.name, {})
        for idx, (_rs, _re, spans) in enumerate(records[1:], start=1):
            fields = [decode_field(text, sp) for sp in spans]
            if len(fields) != 12:
                anomalies.append(f"{path.name}: 记录 {idx} 列数 {len(fields)}")
                continue
            row = dict(zip(HDR, fields))
            total_rows += 1
            action = (row["action"] or "").strip()
            if action == "SKIP":
                skip_rows += 1
                continue
            if action != "MATERIAL":
                anomalies.append(f"{path.name}: 记录 {idx} 非法 action {action!r}")
                continue
            material_rows += 1
            node = (row["node_slug"] or "").strip()
            if not node or node.startswith("NEW:"):
                new_rows += node.startswith("NEW:")
                continue
            key = (row["chunk_rel"], row["chunk_id"])
            subject = sm.get(key) or ""
            if not subject:
                subject = subject_of_path(row["chunk_rel"]) or ""
                subject_fallback += 1
            if not subject:
                anomalies.append(f"{path.name}: 记录 {idx} 科目不可判 {key}")
                continue
            if node in nodes.get(subject, set()):
                continue
            flagged_rows += 1
            file_rows += 1
            flagged_slugs.add(node)
            if (subject, node) in del_slugs:
                anomalies.append(f"{path.name}: 记录 {idx} {subject}/{node} 在 point_delete（不走并点链，需人工）")
                continue
            if (subject, node) in ren_slugs:
                anomalies.append(f"{path.name}: 记录 {idx} {subject}/{node} 在 point_rename（不走并点链，需人工）")
                continue
            target, why = resolve(subject, node, merge_map, nodes)
            chains.add((subject, node))
            if target is None:
                anomalies.append(f"{path.name}: 记录 {idx} {subject}/{node} 解析失败（{why}）")
                continue
            prev = mapping_keys.get((subject, node))
            if prev is not None and prev != target:
                anomalies.append(f"{path.name}: 记录 {idx} {subject}/{node} 二次解析出不同目标 {prev!r}/{target!r}")
                continue
            mapping_keys[(subject, node)] = target
            mapping[(subject, node, target)] += 1
            if target != node:
                file_changes.append((idx, spans[NODE_COL], node, target))
        if file_changes:
            pending_by_file[path] = file_changes
            per_file.append((len(file_changes), path.name))

    # 应用
    written_files = written_rows = 0
    for path, changes in pending_by_file.items():
        try:
            raw = path.read_bytes()
            if hashlib.sha256(raw).hexdigest() != file_digest.get(path):
                anomalies.append(f"{path.name}: 扫描后文件被外部改动，本次跳过（跨度已过期）")
                continue
            text = raw.decode("utf-8")
            new_text = apply_changes(text, changes)
            verify_rewrite(text, new_text, changes, path.name)
        except ValueError as exc:
            anomalies.append(f"{path.name}: 写前复算失败 {exc}")
            continue
        if args.write:
            path.write_bytes(new_text.encode("utf-8"))
            written_files += 1
            written_rows += len(changes)

    log(f"归一判定源 node_slug —— {'write（已写盘）' if args.write else 'dry-run（未写盘）'}")
    log(f"判定源：{args.dir.as_posix()}（{len(files)} 个 *.jsonl.csv）")
    log(f"总行 {total_rows}；MATERIAL {material_rows}、SKIP {skip_rows}、NEW: {new_rows}")
    log(f"待改 MATERIAL 行 {flagged_rows} / {len(flagged_slugs)} 个 slug / 涉及 {len(pending_by_file)} 个文件"
        f"（可改 {sum(len(v) for v in pending_by_file.values())} 行）")
    log(f"EOL（按记录终止符）：CRLF {eol_stats.get('CRLF', 0)} 文件、LF {eol_stats.get('LF', 0)} 文件、"
        f"混合 {eol_stats.get('MIXED', 0)} 文件、无行尾 {eol_stats.get('EOF', 0)} 文件")
    log(f"链断言：检查 {len(chains)} 条 (科目, slug) 链；环 {sum(1 for a in anomalies if 'cycle' in a)} 条；"
        f"终态全部要求落在 staging 包内（解析失败即异常）")
    if subject_fallback:
        log(f"切片缺失回退 subject_of_path 的行 {subject_fallback} 行")
    log("")
    log("映射表（科目 旧slug → 新slug ×行数）：")
    for (subject, old, new), n in sorted(mapping.items(), key=lambda kv: (-kv[1], kv[0])):
        log(f"  {subject} {old} → {new} ×{n}")
    log("")
    log(f"逐文件（待改行数 {len(per_file)} 个文件）：")
    for n, name in sorted(per_file, key=lambda x: (-x[0], x[1])):
        log(f"  {n:>5}  {name}")
    if args.write:
        log("")
        log(f"写盘：{written_files} 个文件 / {written_rows} 行；未改动文件未落盘；"
            f"每文件仅拼接 node_slug 字段跨度，其余字节逐字保留")
    log("")
    log(f"异常 {len(anomalies)} 条：")
    for a in anomalies[:50]:
        log(f"  ! {a}")

    out = "\n".join(lines) + "\n"
    print(out, end="")
    try:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(out, encoding="utf-8")
    except OSError as exc:
        print(f"报告落盘失败：{exc}", file=sys.stderr)
        return 1
    return 1 if anomalies else 0


if __name__ == "__main__":
    raise SystemExit(main())
