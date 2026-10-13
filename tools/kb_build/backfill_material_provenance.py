# -*- coding: utf-8 -*-
"""F8·③ 一次性注入器：把整个材料侧车族升到 schemaVersion 4 并补齐 AI/provenance 三键。

## 它消灭的失败

侧车族现存 23 卷（22 卷 `moe-2025-teaching-support-v2-*.json` + 1 卷历史样例
`moe-2020-teaching-support-v1.json`）是 v1/v2 时代的产物：材料没有 AI/provenance 三键。
Kotlin codec 的 v4 要求材料必含三键，而**没有任何既有工具能升级已落盘的材料**
（`materialize` 只追加新材料；重建整族已实测不可信，见下），于是"包里有 5 万条
AI 整理材料却一条标识都没有"这个失败只能靠一个可复跑、可复核的注入器消灭。

## 为什么是注入器而不是 materialize 重生成（2026-10-14 对拍，实测）

`materialize --write` 到 scratch（只给包 + 索引，让它从判定表 + 块池全量重建）与现盘
逐文件对拍：**相同 0 / 不同 22**，卷数 19 vs 22，各卷材料集互不相交（例：卷 01
实有 3235 / 建有 1919，交集 0；卷 20–22 根本没重建出来）。判定表不是现盘内容的完整
来源（改绑、重判替换、作者型材料都不在表里），重生成会整体回退。故落地通道 = 本注入器
（就地升级，不动材料正文/绑定/时间戳），`materialize` 侧只负责"新材料出生即 v4"。

## 幂等与 fail-closed

- 已合规（v4 + 三键齐）的卷**零改动**（重跑逐字节一致）；
- 半写的三键（缺一/缺二）、越界取值、contentId ≠ slug 一律**拒**，不猜不补；
- v1 来源缺的四枚政策键按契约唯一合法值补齐（REFERENCE_ONLY →
  REVIEWED_SYNTHESIS_ONLY，其余取 null）；推不出真值的 v1 来源整批拒；
- 写盘走 `pack_io.dump_json`（规范排版），只写 `work_dir()`（默认 staging）——
  成品目录仍只由 `promote.py` 写。

## 用法

    python tools/kb_build/backfill_material_provenance.py --dry-run
    python tools/kb_build/backfill_material_provenance.py --write

（`--root <dir>` 只给测试/复核用：把工作目录切到别处。）
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import material_provenance as prov  # noqa: E402
from kb_build import pack_io  # noqa: E402


def plan(work_dir: Path) -> dict:
    """读族 + 预演升级；不改磁盘。返回 {"names", "docs", "stats", "problems"}。"""
    problems: list[str] = []
    index_doc = None
    index_path = work_dir / pack_io.SIDECAR_INDEX_NAME
    if index_path.exists():
        index_doc = pack_io.load_json(index_path)
    names = prov.family_names(work_dir)
    docs: dict[str, dict] = {}
    stats: dict[str, dict] = {}
    current = {"volumes": 0, "volumes_v4": 0, "materials": 0, "materials_keyed": 0}
    for name in names:
        path = work_dir / name
        if not path.exists():
            problems.append(f"索引/族里的卷不在磁盘上：{name}")
            continue
        doc = pack_io.load_json(path)
        # 升级前的现状（dry-run 报的就是它；ensure_doc 是原地改，先数后改）
        current["volumes"] += 1
        if doc.get("schemaVersion") == prov.SCHEMA_VERSION:
            current["volumes_v4"] += 1
        materials = doc.get("materials") or []
        current["materials"] += len(materials)
        current["materials_keyed"] += sum(
            1 for m in materials if all(k in m for k in prov.PROVENANCE_KEYS))
        try:
            stats[name] = prov.ensure_doc(doc, source=name)
        except prov.MaterialProvenanceError as exc:
            problems.append(str(exc))
            continue
        docs[name] = doc
    if index_doc is None:
        problems.append("缺侧车索引（Kotlin loader 按索引加载，无索引 = 装不上）")
    else:
        problems.extend(prov.index_problems(index_doc, work_dir))
    return {"names": names, "docs": docs, "stats": stats, "problems": problems,
            "current": current}


def coverage(docs: dict[str, dict]) -> dict[str, int]:
    """交付态复算：卷数 / v4 卷数 / 材料数 / 带齐三键的材料数。"""
    volumes = len(docs)
    at_v4 = sum(1 for d in docs.values() if d.get("schemaVersion") == prov.SCHEMA_VERSION)
    materials = sum(len(d.get("materials") or []) for d in docs.values())
    keyed = sum(1 for d in docs.values() for m in d.get("materials") or []
                if all(k in m for k in prov.PROVENANCE_KEYS))
    return {"volumes": volumes, "volumes_v4": at_v4, "materials": materials, "materials_keyed": keyed}


def write(work_dir: Path) -> dict:
    """落盘升级。整批先全部读完 + 校验通过才写（一个坏卷不留下半升级的族）。"""
    pl = plan(work_dir)
    if pl["problems"]:
        raise prov.MaterialProvenanceError(
            "族不自洽，拒绝写盘：\n  " + "\n  ".join(pl["problems"][:20]))
    written: list[str] = []
    for name, doc in pl["docs"].items():
        if not pl["stats"][name]["changed"]:
            continue
        pack_io.dump_json(doc, work_dir / name)
        written.append(name)
    # 写后复算：族必须自洽（材料 100% 带三键、卷全 v4、索引一致）
    after = plan(work_dir)
    if after["problems"]:
        raise prov.MaterialProvenanceError(
            "写盘后复算不自洽：\n  " + "\n  ".join(after["problems"][:20]))
    cov = coverage(after["docs"])
    if cov["volumes_v4"] != cov["volumes"] or cov["materials_keyed"] != cov["materials"]:
        raise prov.MaterialProvenanceError(f"写盘后交付态不达标：{cov}")
    return {"written": written, "stats": pl["stats"], "coverage": cov}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--root", type=Path, default=None,
                    help="工作目录（默认 staging；只给测试/复核用）")
    args = ap.parse_args(argv)
    if args.root is not None:
        pack_io.use_directory(args.root)
    work_dir = pack_io.work_dir()
    pl = plan(work_dir)
    for name in pl["names"]:
        if name not in pl["stats"]:
            continue
        s = pl["stats"][name]
        if s["changed"]:
            print(f"  {name}: v→{prov.SCHEMA_VERSION}、材料补键 {s['materials_upgraded']}、"
                  f"来源升级 {s['sources_upgraded']}")
    cov = pl["current"]
    print(f"族 {len(pl['names'])} 卷；需补键材料 {sum(s['materials_upgraded'] for s in pl['stats'].values())}；"
          f"问题 {len(pl['problems'])}")
    print(f"现状：v4 卷 {cov['volumes_v4']}/{cov['volumes']}，带齐三键材料 "
          f"{cov['materials_keyed']}/{cov['materials']}")
    for p in pl["problems"][:10]:
        print("  -", p)
    if args.write:
        if pl["problems"]:
            return 1
        stats = write(work_dir)
        print(f"已写 {len(stats['written'])} 卷：{stats['written']}")
        print(f"交付态：{stats['coverage']}")
        return 0
    print("（dry-run：未写盘；加 --write 落盘）")
    return 1 if pl["problems"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
