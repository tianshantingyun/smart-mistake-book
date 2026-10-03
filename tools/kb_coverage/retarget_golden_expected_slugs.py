# -*- coding: utf-8 -*-
"""按映射迁移冻结金标的 `expectedSlug`（改绑→重放），或退役一条（内容已不在库）。

它消灭的失败（2026-10-03 实测）：近重复节点合并把 105 个节点并掉后，冻结金标 v2 有 5 条
`expectedSlug` 指向已不存在的节点——**不改就永远算 MISS**（指标被一条坏标签污染），
手改又会破坏冻结件的字节规范（sha 旁车、排序、缩进）而让校验与判分读不到同一份文件。

规矩（本工具强制）：
- 映射只允许「旧 slug → 新 slug（必须存在于当前包）」或「→ null（退役，须给理由）」；
- 退役会减少题数：**必须写进 reasons**，并在协议文档留档（禁止无声改评测集）；
- 写入前做**字节复现自检**：未改动的字段按原样 dump 必须与原文件逐字节相同（缩进/排序/
  ensure_ascii 一旦不符就报错拒绝写），保证只改该改的；
- 两份副本（androidTest assets + kb_coverage/tables）同步改，两份 `.sha256` 旁车同步重算。

用法：
    PYTHONPATH=tools python -m kb_coverage.retarget_golden_expected_slugs \\
        --map build/agent-batch3/golden-migration.json            # 报告（不写）
    PYTHONPATH=tools python -m kb_coverage.retarget_golden_expected_slugs \\
        --map build/agent-batch3/golden-migration.json --write    # 写两份副本 + sha
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
COPIES = (
    REPO / "core/data/src/androidTest/assets/golden/golden_queries_v2.json",
    REPO / "tools/kb_coverage/tables/golden_queries_v2.json",
)
DUMP_KW = {"ensure_ascii": False, "indent": 1, "sort_keys": True}


def load_pack_slugs() -> set[tuple[str, str]]:
    sys.path.insert(0, str(REPO / "tools"))
    from kb_build import pack_io  # noqa: E402

    pack = pack_io.load_json(pack_io.pack_path())
    out: set[tuple[str, str]] = set()

    def walk(subject: str, objs) -> None:
        for topic in objs:
            for point in topic.get("knowledgePoints") or []:
                out.add((subject, point["slug"]))
            walk(subject, topic.get("topics") or [])

    for s in pack["subjects"]:
        walk(s["subject"], s["topics"])
    return out


def dump(entries: list[dict]) -> bytes:
    return (json.dumps(entries, **DUMP_KW) + "\n").encode("utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--map", type=Path, required=True,
                        help="迁移映射 JSON：数组，元素含 oldSlug / newSlug（null=退役）/ rationale")
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args(argv)

    mapping = json.loads(args.map.read_text(encoding="utf-8"))
    by_old: dict[tuple[str, str], dict] = {}
    for item in mapping:
        old = (item.get("subject", "").strip(), item.get("oldSlug", "").strip())
        if not old[1]:
            print(f"映射缺 oldSlug：{item}", file=sys.stderr)
            return 2
        by_old[old] = item

    original = COPIES[0].read_bytes()
    entries = json.loads(original.decode("utf-8"))
    if dump(entries) != original:
        print("字节复现自检失败：原文件的 dump 参数与本工具不一致（缩进/排序/编码）——"
              "拒绝改写，先对齐参数", file=sys.stderr)
        return 2

    pack_slugs = load_pack_slugs()
    changed = retired = 0
    kept: list[dict] = []
    report: list[str] = []
    for e in entries:
        key = (e["subject"], e["expectedSlug"])
        item = by_old.get(key)
        if item is None:
            kept.append(e)
            continue
        new = item.get("newSlug")
        if new is None:
            retired += 1
            report.append(f"退役  {key[0]} / {key[1]}：{item.get('rationale', '')[:80]}")
            continue
        if (key[0], new) not in pack_slugs:
            print(f"目标 slug 不在当前包：{key[0]} / {new}——拒绝改写", file=sys.stderr)
            return 2
        if new == e["expectedSlug"]:
            kept.append(e)
            continue
        changed += 1
        report.append(f"改指  {key[0]} / {key[1]} → {new}")
        kept.append({**e, "expectedSlug": new})

    print(f"条目 {len(entries)} → {len(kept)}（改指 {changed}、退役 {retired}）")
    for line in report:
        print("  " + line)
    if not args.write:
        print("（未写；加 --write 落盘）")
        return 0

    new_bytes = dump([dict(sorted(e.items())) for e in kept])
    digest = hashlib.sha256(new_bytes).hexdigest()
    old_sha_files = [p.with_name(p.name + ".sha256") for p in COPIES]
    for path in COPIES:
        path.write_bytes(new_bytes)
    for sha_path in old_sha_files:
        trailing = "\n" if sha_path.read_bytes().endswith(b"\n") else ""
        sha_path.write_text(digest + trailing, encoding="utf-8")
    print(f"已写 {len(COPIES)} 份副本 + {len(old_sha_files)} 份 sha 旁车 → sha256 {digest[:16]}…")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
