# -*- coding: utf-8 -*-
"""重建节点别名：只保留**有据可依**的别名，并顺手把污染清掉。

## 它消灭的失败

别名曾经是"字符串机械匹配"的产物：`函数的概念` 挂着 `导数的概念与导函数`、
`排列与组合的概念及公式`、`绝对值型函数的处理方式`。这不是脏数据那么简单——别名是
**检索打分**与**模型选点**的输入：
- `KnowledgeContextRetriever`：别名重合给权重 7、精确短语给 70 分加成；
- `RoomMistakeOrganizationRepository`：用 `aliases + canonicalName` 匹配学生文字；
- `OpenAiProblemOrganizationProtocol`：把别名清单发给模型让它挑节点。
别名指向别的概念 = 直接把检索和模型往错误节点上引。

## 判据（三条，全部可复核）

1. **主名**永远保留；
2. 别名必须是**该节点自己绑定材料的标题**（题目标题就是这份材料在讲的东西；
   绑定是对的⇒别名跟着对），且形状合格（≤24 字、无句末标点、不是残片、过坏名检测）；
3. **全局互斥**：同一学科内，一个名字只能归一个节点——被两个以上节点同时claim的候选
   一律丢弃（否则"按别名匹配"本身就歧义）。

## 人工例外表（`tables/alias_exceptions.csv`）

判据 2 会周期性把人工补的检索名冲掉：批11 撤回点名（原名检索需求）每次重建都会消失。
例外表是**人工定稿层**，表头 `subject,slug,alias,reason`，登记者优先：

- 例外条目仍要过判据 2 的形状门（长度/字符/坏名）与判据 3 的学科互斥门
  （不能等于同学科另一节点主名，同一名字不能登记给两个节点）；
- 例外与重建结果冲突（同一名字被别的节点从标题派生出来）时，**以例外表为准**：
  归登记者，其它节点让位，冲突条数计入 `exception_conflicts`；
- 表不存在 = 无例外，重建行为与旧版完全一致。

门禁 `ghost_aliases` 的口径（"既不等于主名、也不来自当前绑定材料的标题"）与本判据同源，
例外表登记的名字同样算有据可依（gate 读同一张表），所以重建后该指标必然为 0；
`alias_collision`（新增）则守住第 3 条的长期不变量。

## 用法

    PYTHONPATH=tools python -m kb_build.rebuild_aliases            # 试算（不写）
    PYTHONPATH=tools python -m kb_build.rebuild_aliases --write    # 写表并回写 staging 包
"""

from __future__ import annotations

import argparse
import csv
from collections import Counter, defaultdict
from pathlib import Path

from kb_build import gate, pack_io

TABLES = Path(__file__).resolve().parent / "tables"
ALIAS_MAP = "alias_map.csv"
DROPPED = "alias_dropped.csv"
EXCEPTIONS = "alias_exceptions.csv"
EXCEPTION_FIELDS = ("subject", "slug", "alias", "reason")
NAME_MAX = 24
# 句末标点与**逗号**：名字里不会出现逗号——出现就说明是"从句/句子片段"
# （实测拦下 `奇偶性性质法求参：拆出含参部分，由不含参` 这类截断）。
# 冒号保留：教材节名就是 `实验：探究平抛运动的特点` 这种写法，拒掉会丢好别名。
SENTENCE_PUNCT = "。！？；;，,"
BAD_PREFIX = ("例", "典例", "如", "解", "答案", "解析", "已知", "下列", "判断", "计算", "求")
BAD_SUFFIX = ("的", "和", "与", "或", "及", "供", "为", "是")
BRACKETS = {"（": "）", "(": ")", "【": "】", "[": "]", "《": "》", "“": "”"}


def _balanced(text: str) -> bool:
    stack = []
    for ch in text:
        if ch in BRACKETS:
            stack.append(BRACKETS[ch])
        elif ch in BRACKETS.values():
            if not stack or stack.pop() != ch:
                return False
    return not stack


def title_usable(title: str) -> str | None:
    """材料标题能不能当别名；不能则返回原因。"""
    t = (title or "").strip()
    if not t:
        return "空标题"
    if len(t) > NAME_MAX:
        return "超长（>24 字）"
    if any(ch in t for ch in SENTENCE_PUNCT):
        return "含逗号/句末标点（是句子不是名字）"
    if t.startswith(BAD_PREFIX):
        return "以题面/指令开头（是题干不是名字）"
    if t.endswith(BAD_SUFFIX):
        return "以虚词结尾（截断残片）"
    if not _balanced(t):
        return "括号不配对（截断残片）"
    if "$" in t or "\\" in t:
        return "含 LaTeX 定界符（是公式片段不是名字）"
    verdict = gate._is_bad_name(t)
    if verdict:
        return f"坏名检测：{verdict}"
    return None


def bound_titles() -> dict[tuple[str, str], set[str]]:
    titles: dict[tuple[str, str], set[str]] = defaultdict(set)
    for sp in pack_io.sidecar_paths():
        for m in pack_io.load_json(sp)["materials"]:
            for b in m.get("bindings") or []:
                parts = b["knowledgeNodeId"].split(":")
                titles[(parts[-3].upper(), parts[-1])].add(m.get("title") or "")
    return titles


def load_exceptions(path: Path | None = None) -> dict[tuple[str, str], set[str]]:
    """读人工例外表：(subject, slug) -> {alias}。表不存在 = 无例外。

    `reason` 是登记依据（供人复核），不参与合并；缺列直接报错，避免表头写错后
    静默把所有例外当成不存在。
    """
    path = TABLES / EXCEPTIONS if path is None else Path(path)
    if not path.exists():
        return {}
    out: dict[tuple[str, str], set[str]] = defaultdict(set)
    with path.open(encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        missing = [f for f in EXCEPTION_FIELDS if f not in (reader.fieldnames or [])]
        if missing:
            raise ValueError(f"{path.name} 缺少列：{missing}")
        for row in reader:
            subject = (row.get("subject") or "").strip().upper()
            slug = (row.get("slug") or "").strip()
            alias = (row.get("alias") or "").strip()
            if subject and slug and alias:
                out[(subject, slug)].add(alias)
    return dict(out)


def _validate_exceptions(
        node_by_key: dict[tuple[str, str], dict],
        names_by_subject: dict[str, set[str]],
        exceptions: dict[tuple[str, str], set[str]],
) -> tuple[dict[tuple[str, str], set[str]], list[dict], dict[tuple[str, str], tuple[str, str]]]:
    """例外条目的三道门。返回 (有效例外, 拒绝记录, 唯一登记归属)。

    - 形状：沿用 `title_usable`（长度/标点/坏名/残片）；
    - 主名：不能等于同学科另一节点的主名（判据 3 的核心歧义）；
    - 例外表自身互斥：同一名字登记给同学科两个节点 → 两条都拒绝。
    """
    rejected: list[dict] = []
    valid: dict[tuple[str, str], set[str]] = defaultdict(set)
    registered: dict[tuple[str, str], set[tuple[str, str]]] = defaultdict(set)
    for key, aliases in exceptions.items():
        subject, _slug = key
        point = node_by_key.get(key)
        for alias in sorted({(a or "").strip() for a in aliases if (a or "").strip()}):
            if point is None:
                rejected.append({"subject": subject, "slug": key[1], "alias": alias,
                                 "reason": "节点不存在（subject/slug 无对应知识点）"})
                continue
            reason = title_usable(alias)
            if reason:
                rejected.append({"subject": subject, "slug": point["slug"], "alias": alias,
                                 "reason": f"例外条目形状不合格：{reason}"})
                continue
            if alias == point["name"]:
                rejected.append({"subject": subject, "slug": point["slug"], "alias": alias,
                                 "reason": "与主名相同（冗余）"})
                continue
            if alias in (names_by_subject[subject] - {point["name"]}):
                rejected.append({"subject": subject, "slug": point["slug"], "alias": alias,
                                 "reason": "与同学科另一节点主名相同（歧义）"})
                continue
            valid[key].add(alias)
            registered[(subject, alias)].add(key)
    for (subject, alias), keys in sorted(registered.items()):
        if len(keys) > 1:
            for key in sorted(keys):
                valid[key].discard(alias)
                rejected.append({"subject": subject, "slug": key[1], "alias": alias,
                                 "reason": f"同学科 {len(keys)} 个节点同时登记（歧义）"})
    owner = {key: next(iter(keys)) for key, keys in registered.items() if len(keys) == 1}
    return valid, rejected, owner


def rebuild(pack: dict, titles: dict[tuple[str, str], set[str]] | None = None,
            exceptions: dict[tuple[str, str], set[str]] | None = None) -> dict:
    titles = bound_titles() if titles is None else titles
    exceptions = load_exceptions() if exceptions is None else exceptions
    # 候选取集：每个节点的候选（主名另算，不参与互斥）
    candidates: dict[tuple[str, str], set[str]] = {}
    dropped: list[dict] = []
    names_by_subject: dict[str, set[str]] = defaultdict(set)
    node_by_key: dict[tuple[str, str], dict] = {}
    for subject in pack["subjects"]:
        for t in subject["topics"]:
            for p in t.get("knowledgePoints") or []:
                key = (subject["subject"], p["slug"])
                node_by_key[key] = p
                names_by_subject[subject["subject"]].add(p["name"])
    for key, point in node_by_key.items():
        subject, _slug = key
        keep: set[str] = set()
        for title in titles.get(key, set()):
            reason = title_usable(title)
            if reason:
                dropped.append({"subject": subject, "slug": point["slug"], "candidate": title,
                                "reason": reason})
                continue
            keep.add(title.strip())
        # 与其它节点主名撞车 → 歧义，丢弃
        for alias in sorted(keep & (names_by_subject[subject] - {point["name"]})):
            keep.discard(alias)
            dropped.append({"subject": subject, "slug": point["slug"], "candidate": alias,
                            "reason": "与同学科另一节点主名相同（歧义）"})
        # 与自己的主名相同 → 冗余（App 侧 aliases 是集合，重复值直接被契约拒）
        keep.discard(point["name"])
        candidates[key] = keep
    # 例外表：人工登记的点名别名是定稿层，仍要过形状/主名互斥门
    valid_exceptions, exceptions_rejected, exception_owner = _validate_exceptions(
        node_by_key, names_by_subject, exceptions)
    # 全局互斥：被多个节点 claim 的候选全部丢弃；例外表登记的名字归登记者，
    # 其它节点的同名派生候选让位（登记者优先），让位条数计入 exception_conflicts。
    claim = Counter()
    for keep in candidates.values():
        for alias in keep:
            claim[alias] += 1
    exception_conflicts = 0
    for key, keep in candidates.items():
        subject = key[0]
        for alias in [a for a in keep if claim[a] > 1 or (subject, a) in exception_owner]:
            owner = exception_owner.get((subject, alias))
            if owner == key:
                continue
            keep.discard(alias)
            if owner is not None:
                exception_conflicts += 1
                dropped.append({"subject": subject, "slug": node_by_key[key]["slug"],
                                "candidate": alias,
                                "reason": f"例外表登记给 {owner[1]}（登记者优先）"})
            else:
                dropped.append({"subject": subject, "slug": node_by_key[key]["slug"],
                                "candidate": alias,
                                "reason": f"被 {claim[alias]} 个节点同时 claim（全局互斥）"})
    for key, aliases in valid_exceptions.items():
        candidates[key] |= aliases
    for key, point in node_by_key.items():
        candidates[key].discard(point["name"])
    return {"candidates": candidates, "dropped": dropped, "node_by_key": node_by_key,
            "exceptions_applied": sum(len(v) for v in valid_exceptions.values()),
            "exception_conflicts": exception_conflicts,
            "exceptions_rejected": exceptions_rejected}


def apply_to_pack(pack: dict, plan: dict) -> dict:
    stats = Counter()
    for key, point in plan["node_by_key"].items():
        extra = sorted(a for a in plan["candidates"].get(key, set()) if a != point["name"])
        new_aliases = [point["name"]] + extra
        assert len(set(new_aliases)) == len(new_aliases), new_aliases
        old = point.get("aliases") or []
        if old != new_aliases:
            stats["nodes_changed"] += 1
        stats["old_total"] += len(old)
        stats["new_total"] += len(new_aliases)
        point["aliases"] = new_aliases
    return stats


def write_tables(plan: dict) -> None:
    TABLES.mkdir(parents=True, exist_ok=True)
    with (TABLES / ALIAS_MAP).open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["subject", "slug", "alias"])
        for (subject, slug), keep in sorted(plan["candidates"].items()):
            for alias in sorted(keep):
                w.writerow([subject, slug, alias])
    with (TABLES / DROPPED).open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["subject", "slug", "candidate", "reason"])
        w.writeheader()
        w.writerows(sorted(plan["dropped"], key=lambda r: (r["subject"], r["slug"], r["candidate"])))


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args(argv)
    path = pack_io.pack_path()
    pack = pack_io.load_json(path)
    plan = rebuild(pack)
    candidates = plan["candidates"]
    with_alias = sum(1 for v in candidates.values() if v)
    print(f"节点 {len(candidates)}：有别名 {with_alias}，别名总数 {sum(len(v) for v in candidates.values())}")
    print(f"例外：应用 {plan['exceptions_applied']}，冲突改判 {plan['exception_conflicts']}，"
          f"拒绝 {len(plan['exceptions_rejected'])}")
    for row in plan["exceptions_rejected"][:8]:
        print(f"   [{row['subject']}/{row['slug']}] {row['alias']}：{row['reason']}")
    print(f"丢弃候选 {len(plan['dropped'])}（按原因）:")
    for reason, count in Counter(d["reason"].split("：")[0] for d in plan["dropped"]).most_common(8):
        print(f"   {reason:<26} {count}")
    stats = apply_to_pack(pack, plan)
    print(f"别名：{stats['old_total']} → {stats['new_total']}；变更节点 {stats['nodes_changed']}")
    if not args.write:
        print("（未写盘；加 --write 生效）")
        return 0
    write_tables(plan)
    pack_io.dump_json(pack, path)
    print(f"→ 已写回 {path}；表：{ALIAS_MAP} / {DROPPED}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
