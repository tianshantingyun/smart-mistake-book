# -*- coding: utf-8 -*-
"""近重复材料去重：char 3-gram MinHash（128 perm，Jaccard ≥ 0.70），只在同一节点内聚类。

## 它消灭的失败

1. **v27 的判重口径太窄**：`dedupe_judged_materials` 只按
   `(chunk_id, node_slug, type, title, content)` 逐字相同判重，而同一条材料在
   学生版 / 教师版、两份教辅之间常只差标点、章节号或一句措辞——这些近重复
   留在包里，同一节点下材料互相稀释检索权重，也让"为什么两条几乎一样"反复回到
   人工审计。
2. **v27 去重把锚点材料退休**：`bio-kb-chromatin-chromosome-same-substance`
   在 `tools/kb_build/tables/content_audit_anchors.csv` 里是 I-04 锚点，被
   v27 去重退休后 `test_kb_content_audit.py::test_i04_anchors_are_reported_on_the_current_pack`
   变红（功能性回归）。本脚本把"被 tests / gates 引用的 slug"抽成硬保护集，
   **任何被引用材料永不 RETIRED**——宁可少退休，也不能让门失去锚点。

## 口径（写死，不在 CLI 暴露）

- 分片：char **3-gram**，文本 = `title + "\n" + summaryMarkdown + "\n" + contentMarkdown`，
  先做 `NFKC + casefold + 去全部 Unicode 空白`。
- MinHash：**128** 个置换（置换系数由固定种子的 SHA-256 派生，跨进程/跨版本稳定）。
- 判定阈值：精确 Jaccard **≥ 0.70**（MinHash 估计先以 0.60 粗筛，避免 128 置换的
  估计误差漏掉 0.70 附近的真重复；最终决定只看精确值，不让估计器单独决定退休）。
- 只在**同一节点**内比较（`bindings[].knowledgeNodeId`，当前包每条材料恰好 1 条 PRIMARY）。
- 保留规则（从高到低）：`PRIMARY` 绑定数 → `contentMarkdown` 字符数 → 绑定条数（引用数）
  → slug 后缀更短 → slug 字典序更小。当前包全部是 PRIMARY 且各 1 条绑定，因此实际
  由"正文更长"决定；引用数/PRIMARY 是写死的次级规则，不是摆设。
- **精度门**（本次实测加的，理由见下）：两个材料还必须"规范化标题"相同——
  `NFKC + casefold + 去学生版/教师版等版次词 + 去括号内容 + 去标点空白`。
  纯 0.70 会把 `y=Asin(ωx+φ)` 与 `y=Acos(ωx+φ)`、`a>0` 与 `a<0`、下凸与上凸这几类
  **不同知识点**聚成一簇（本包实测 122 对）；这些是互补结论，不是重复。
- **实体替换门**（人工抽检发现的第二类假阳性）：正文做 `difflib` 对比，若存在
  **≤6 字的实词替换**（非标点/数字/圈号），不退休。它挡住的是同一模板下换了实体的
  表格式材料：`核糖/脱氧核糖`、`果糖/半乳糖`、`蔗糖/乳糖`、`温度/pH`——这些如果退休，
  丢的是 RNA、半乳糖、乳糖等独立知识点。人工抽检 20 簇的记录见
  `build/agent-batch31c/manual-review-20.csv`。
- **不链式退休**：簇用连通分量报告，但每条被退休材料必须与**最终保留者**直接满足
  `标题相同 + Jaccard ≥ 0.70`；靠中间材料串起来、与保留者并不相似的成员记 `SKIP`。

## 锚点保护

`load_anchor_slugs()` 合并三处：

1. `tools/kb_build/tables/content_audit_anchors.csv` 的 `material_slug`（I-04 登记锚点）；
2. `tools/tests/**/*.py` 中出现的现包材料 slug 字面量；
3. gates（`tools/ci/**/*.py`、`tools/kb_build/gate.py`、`promote.py`、
   `check_pack_contract.py`、`audit_content_bindings.py`、`tools/kb_coverage/materialize.py`）
   中出现的现包材料 slug 字面量；
4. `tools/kb_build/tables/alias_map.csv` 里"该材料标题是本节点别名的**唯一**支撑"
   的材料（`load_alias_backed_slugs`）——别名是 `ghost_aliases` 门的输入，退掉唯一
   撑住别名的材料会让门变红，并逼出一次全量 dense 重导（实测布局 44,121→44,100）。

被保护材料**永不进入 retire 列表**；若它与别的材料同簇，保留者取被保护者，
其余只在"与它直接相似"时才退休。

## 用法

    PYTHONPATH=tools python -m kb_coverage.dedupe_near_dup_materials --dry-run
    PYTHONPATH=tools python -m kb_coverage.dedupe_near_dup_materials --apply build/agent-batch31c/plan.json

`--apply` 前做两次守卫：
- **porcelain 守卫**：plan 记录 dry 时的 `git status --porcelain` 摘要；写入前复核，
  不一致即拒写（防止把别人的改动裹进本批）。
- **sha 守卫 + 备份**：plan 记录每个将改文件的 sha256；写入前逐文件核对并把
  原文件复制到 `build/agent-batch31c/backup/`。

退休是可逆台账：`tools/kb_build/tables/material_retire.csv` 逐条记录完整材料 JSON 的
sha256，原 JSON 另存 `build/agent-batch31c/retired-json/<slug>.json`；ext- 材料同时把
判定行 `action` 改成 `SKIP`（判定表是入库唯一源，materialize 不会再把它写回）。
"""

from __future__ import annotations

import argparse
import csv
import difflib
import hashlib
import json
import re
import shutil
import subprocess
import sys
import time
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
if str(REPO / "tools") not in sys.path:      # 直跑引导
    sys.path.insert(0, str(REPO / "tools"))

from kb_build import pack_io                          # noqa: E402
from kb_coverage import judgments                     # noqa: E402
from kb_coverage.dedupe_judged_materials import slug_of as judged_slug_of  # noqa: E402

# ---------------------------------------------------------------- 写死口径

SHINGLE_K = 3
NUM_PERM = 128
JACCARD_THRESHOLD = 0.70
MINHASH_CANDIDATE_THRESHOLD = 0.60
PERM_SEED = 20261102
MERSENNE_P = (1 << 31) - 1

# 版次词：同一条材料的学生版/教师版是同一内容的两次落盘，标题只差这个尾巴。
EDITION_TOKENS = ("学生版", "教师版", "学生用书", "教师用书", "学生用", "教师用")
_PAREN = re.compile(r"[（(][^（）()]*[）)]")
_NON_WORD = re.compile(r"[^\w\u4e00-\u9fff]+")
_WS = re.compile(r"\s+")

ANCHOR_TABLE = REPO / "tools" / "kb_build" / "tables" / "content_audit_anchors.csv"
ALIAS_MAP = REPO / "tools" / "kb_build" / "tables" / "alias_map.csv"
RETIRE_LEDGER = REPO / "tools" / "kb_build" / "tables" / "material_retire.csv"
GOLDEN_JSON = REPO / "tools" / "kb_coverage" / "tables" / "golden_queries_v2.json"
OUT_DIR = REPO / "build" / "agent-batch31c"

GATE_FILES = (
    REPO / "tools" / "kb_build" / "gate.py",
    REPO / "tools" / "kb_build" / "promote.py",
    REPO / "tools" / "kb_build" / "check_pack_contract.py",
    REPO / "tools" / "kb_build" / "audit_content_bindings.py",
    REPO / "tools" / "kb_coverage" / "materialize.py",
)

LEDGER_COLUMNS = ("slug", "kept_slug", "subject", "node_slug", "title", "summary",
                  "source_file", "source_index", "material_json_sha256",
                  "pre_sidecar_sha256", "retired_at", "reverted_at", "reason")

# ---------------------------------------------------------------- 文本口径


def normalize_text(text: str | None) -> str:
    """NFKC + casefold + 去全部 Unicode 空白（不删标点、不做同义改写）。"""
    value = unicodedata.normalize("NFKC", text or "").casefold()
    return _WS.sub("", value)


def canonical_title(title: str | None) -> str:
    """标题精度门的规范化：去版次词、去括号内容、去标点空白。"""
    value = unicodedata.normalize("NFKC", title or "").casefold()
    for token in EDITION_TOKENS:
        value = value.replace(token, "")
    value = _PAREN.sub("", value)
    return _NON_WORD.sub("", value)


def shingle_text(material: dict) -> str:
    return "\n".join((
        material.get("title") or "",
        material.get("summaryMarkdown") or "",
        material.get("contentMarkdown") or "",
    ))


def _fnv1a64(text: str) -> int:
    digest = 0xCBF29CE484222325
    for byte in text.encode("utf-8"):
        digest ^= byte
        digest = (digest * 0x100000001B3) & 0xFFFFFFFFFFFFFFFF
    return digest


def shingle_hashes(material: dict, k: int = SHINGLE_K) -> frozenset[int]:
    """char k-gram → 64-bit 哈希集合（内存按单节点释放，不跨节点累积）。"""
    text = normalize_text(shingle_text(material))
    if not text:
        return frozenset()
    if len(text) < k:
        return frozenset({_fnv1a64(text)})
    return frozenset(_fnv1a64(text[i:i + k]) for i in range(len(text) - k + 1))


def exact_jaccard(left: frozenset[int], right: frozenset[int]) -> float:
    if not left or not right:
        return 0.0
    inter = len(left & right)
    union = len(left) + len(right) - inter
    return inter / union if union else 0.0


_CIRCLED = frozenset(chr(code) for code in range(0x2460, 0x2474))


def _is_cosmetic_token(token: str) -> bool:
    """格式化差异：标点/空白/数字/圈号/箭头/转义残片，不含实词字符。"""
    for ch in token:
        category = unicodedata.category(ch)
        if category[0] in ("P", "Z", "S") or ch.isdigit() or ch in _CIRCLED:
            continue
        if ch in "\\nrt":
            continue
        return False
    return True


def substantive_substitutions(kept_body: str | None, retired_body: str | None,
                              max_token: int = 6) -> list[tuple[str, str]]:
    """短替换里出现实词 → 这是"换了一个实体"，不是重复。

    消灭的具体失败：生物"细胞中的糖类"把 核糖/脱氧核糖、果糖/半乳糖、蔗糖/乳糖
    拆成多条材料时，正文模板相同、只差一个实体词，纯 3-gram Jaccard 会把它们
    判成近重复；若退休"核糖"那条，RNA 这一知识点就从包里消失（本次人工抽检
    实测 4 例）。同样保护"温度/pH"这类实验控制变量的替换。
    """
    left = normalize_text(kept_body)
    right = normalize_text(retired_body)
    matcher = difflib.SequenceMatcher(None, left, right, autojunk=False)
    hits: list[tuple[str, str]] = []
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag != "replace":
            continue
        old, new = left[i1:i2], right[j1:j2]
        if max(len(old), len(new)) > max_token:
            continue
        if _is_cosmetic_token(old) and _is_cosmetic_token(new):
            continue
        hits.append((old, new))
    return hits


def _perm_tables(permutations: int = NUM_PERM, seed: int = PERM_SEED) -> tuple[list[int], list[int]]:
    """固定种子的 SHA-256 派生置换系数；不用随机数，跨进程逐位可复算。"""
    a: list[int] = []
    b: list[int] = []
    for index in range(permutations):
        digest = hashlib.sha256(f"minhash-perm|{seed}|{index}".encode("utf-8")).digest()
        a.append(1 + int.from_bytes(digest[:8], "big") % (MERSENNE_P - 1))
        b.append(int.from_bytes(digest[8:16], "big") % MERSENNE_P)
    return a, b


_A_TABLE, _B_TABLE = _perm_tables()


def minhash_signature(hashes: frozenset[int], permutations: int = NUM_PERM) -> tuple[int, ...]:
    """128 置换 MinHash。同输入同签名（系数表固定），可复算。

    numpy 可用时按 (128, n) 广播；否则纯 Python 回退（小夹具/无 numpy 环境）。
    输入取 31 位（`h & 0x7FFFFFFF`），保证 `a*x+b` 不溢出 numpy uint64。
    """
    xs = [h & 0x7FFFFFFF for h in hashes]
    if not xs:
        return tuple([MERSENNE_P] * permutations)
    if numpy is not None:
        import numpy as np  # noqa: F401  局部导入：没有 numpy 的环境走纯 Python
        arr = np.fromiter(xs, dtype=np.uint64, count=len(xs))
        a = np.asarray(_A_TABLE[:permutations], dtype=np.uint64)
        b = np.asarray(_B_TABLE[:permutations], dtype=np.uint64)
        values = (a[:, None] * arr[None, :] + b[:, None]) % np.uint64(MERSENNE_P)
        return tuple(int(v) for v in values.min(axis=1))
    signature = [MERSENNE_P] * permutations
    for x in xs:
        for index in range(permutations):
            value = (_A_TABLE[index] * x + _B_TABLE[index]) % MERSENNE_P
            if value < signature[index]:
                signature[index] = value
    return tuple(signature)


def minhash_jaccard(left: tuple[int, ...], right: tuple[int, ...]) -> float:
    if not left or len(left) != len(right):
        return 0.0
    return sum(1 for a, b in zip(left, right) if a == b) / len(left)


try:  # numpy 只是加速；不是正确性依赖
    import numpy  # type: ignore  # noqa: F401
except Exception:  # pragma: no cover - 环境差异
    numpy = None


# ---------------------------------------------------------------- 材料分组


def material_groups() -> dict[str, list[dict]]:
    """knowledgeNodeId -> 材料列表。多绑定材料跳过（本脚本只做单节点去重）。"""
    groups: dict[str, list[dict]] = defaultdict(list)
    for _path, material in pack_io.load_materials():
        bindings = material.get("bindings") or []
        if len(bindings) != 1:
            continue
        node = bindings[0].get("knowledgeNodeId") or ""
        if node:
            groups[node].append(material)
    return dict(groups)


def node_slug_of(node_id: str) -> str:
    return node_id.split(":atomic:", 1)[-1] if ":atomic:" in node_id else node_id.rsplit(":", 1)[-1]


# ---------------------------------------------------------------- 锚点保护


_TOKEN = re.compile(r"(?<![A-Za-z0-9_-])([a-z0-9][a-z0-9._-]{4,})(?![A-Za-z0-9_-])")


def _anchor_table_slugs() -> dict[str, str]:
    if not ANCHOR_TABLE.exists():
        return {}
    out: dict[str, str] = {}
    with ANCHOR_TABLE.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            slug = (row.get("material_slug") or "").strip()
            if slug:
                out[slug] = f"{ANCHOR_TABLE.relative_to(REPO)}:registered-anchor"
    return out


def _scan_files() -> list[Path]:
    files = sorted((REPO / "tools" / "tests").glob("*.py"))
    files += sorted((REPO / "tools" / "ci").glob("*.py"))
    files += [path for path in GATE_FILES if path.exists()]
    return files


def load_anchor_slugs(known_slugs: set[str] | None = None) -> dict[str, list[str]]:
    """返回 {slug: [引用出处, …]}。只保留现包里真实存在的 slug（防过时登记）。"""
    known = known_slugs or set()
    anchors: dict[str, list[str]] = defaultdict(list)
    for slug, provenance in _anchor_table_slugs().items():
        if not known or slug in known:
            anchors[slug].append(provenance)
    for path in _scan_files():
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        rel = str(path.relative_to(REPO))
        for match in _TOKEN.finditer(text):
            slug = match.group(1).rstrip(".-")
            if slug in known and rel not in anchors[slug]:
                anchors[slug].append(rel)
    return dict(anchors)


def load_alias_backed_slugs(groups: dict[str, list[dict]]) -> dict[str, list[str]]:
    """标题被 `alias_map.csv` 采纳为节点别名的材料：退休它 = 让别名失去依据。

    `ghost_aliases` 门（`kb_build.gate`）要求每个别名都等于主名或该节点**当前绑定
    材料的标题**；alias_map 是由材料标题重建的。若把唯一一条撑着某别名的材料退休，
    别名立刻变成 ghost，`gate.evaluate()` 与 5 个 promote 用例会红（本次实测 24 条）。
    重建 alias_map 能消掉 ghost，但别名 id 是 dense 旁车布局的一部分（实测 44,121→
    44,100），会逼出一次全量 dense 重导——本批不做。因此把这类材料当"引用方"保护：
    别名是 gate 的输入，等价于 tests/gates 引用。代价是少退休一些近重复。
    """
    if not ALIAS_MAP.exists():
        return {}
    by_key: dict[tuple[str, str], set[str]] = defaultdict(set)
    with ALIAS_MAP.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            subject = (row.get("subject") or "").strip()
            node = (row.get("slug") or "").strip()
            alias = (row.get("alias") or "").strip()
            if subject and node and alias:
                by_key[(subject, node)].add(alias)
    out: dict[str, list[str]] = {}
    title_counts = Counter(
        (material.get("subject") or "", node_slug_of(node_id), (material.get("title") or "").strip())
        for node_id, items in groups.items() for material in items
    )
    for node_id, items in groups.items():
        node = node_slug_of(node_id)
        for material in items:
            key = (material.get("subject") or "", node)
            title = (material.get("title") or "").strip()
            # 只有"该标题在本节点仅这一条材料"时，退休它才会让别名失去唯一依据。
            if title in by_key.get(key, set()) and title_counts[(key[0], node, title)] == 1:
                out[material["slug"]] = [f"{ALIAS_MAP.relative_to(REPO)}:sole-alias-backing"]
    return out


def _golden_expected_slugs(golden_path: Path | str | None = None) -> set[str]:
    """金标 `expectedSlug` 去重集合（缺失/全空 ⇒ 抛错，见 load_golden_expected_slugs）。"""
    path = Path(golden_path) if golden_path is not None else GOLDEN_JSON
    if not path.is_file():
        raise FileNotFoundError(f"金标文件缺失，拒绝退化为「无保护」：{path}")
    queries = json.loads(path.read_text(encoding="utf-8"))
    expected = {str(q.get("expectedSlug") or "").strip() for q in queries}
    expected.discard("")
    if not expected:
        raise ValueError(f"金标 expectedSlug 全空，拒绝退化为「无保护」：{path}")
    return expected


def load_golden_expected_slugs(golden_path: Path | str | None = None,
                               groups: dict[str, list[dict]] | None = None
                               ) -> dict[str, list[str]]:
    """金标 `expectedSlug` 命中的**节点**下的全部材料：永不退休。

    金标是判读基准（`golden_queries_v2.json` 129 条查询 / 128 个唯一 expectedSlug）。
    把某题期望节点的材料当近重复退休，等于削薄该题的交付依据（F1/F2），而这种削减
    不会让任何门变红——所以在去重这一侧直接硬挡。

    金标文件缺失或 `expectedSlug` 全空 ⇒ **抛错**：静默退化成"无保护"会让本批悄悄
    少挡一批材料，而调用方从计数上看不出来。
    """
    expected = _golden_expected_slugs(golden_path)
    groups = groups if groups is not None else material_groups()
    out: dict[str, list[str]] = {}
    for node_id, items in groups.items():
        if node_slug_of(node_id) in expected:
            for material in items:
                out.setdefault(material["slug"], []).append("golden:expectedSlug")
    return out


def load_thin_node_slugs(groups: dict[str, list[dict]], max_materials: int = 2
                         ) -> dict[str, list[str]]:
    """去重前材料数 ≤ `max_materials` 的节点：其全部材料永不退休。

    否则「恰 2 条」会被降到 1 条、「恰 1 条」会被降到 0 条——节点从可教变不可教，
    而没有任何门把"节点材料数"当判据（R3 的 D5）。
    """
    out: dict[str, list[str]] = {}
    for _node_id, items in groups.items():
        if len(items) <= max_materials:
            for material in items:
                out.setdefault(material["slug"], []).append(
                    f"thin:<={max_materials}-materials")
    return out


def merge_protections(*sources: dict[str, list[str]] | None) -> dict[str, list[str]]:
    """合并多个 {slug: [来源…]}；同一 slug 来自多处时**保留全部来源**（审计要看得出为什么被保护）。"""
    merged: dict[str, list[str]] = defaultdict(list)
    for source in sources:
        for slug, reasons in (source or {}).items():
            for reason in reasons:
                if reason not in merged[slug]:
                    merged[slug].append(reason)
    return {slug: sorted(reasons) for slug, reasons in merged.items()}


def title_mentions(titles: dict[str, str], files: list[Path] | None = None) -> dict[str, list[str]]:
    """可选复核：材料**完整标题**（≥10 字）作为字面量出现在 tests/gates 里的情况。

    标题不是 slug，不在本批硬保护契约内；只作为退休前的人工告警（见 dry 输出）。
    """
    corpus = {}
    for path in files or _scan_files():
        try:
            corpus[str(path.relative_to(REPO))] = path.read_text(encoding="utf-8")
        except OSError:
            continue
    hits: dict[str, list[str]] = {}
    for slug, title in titles.items():
        cleaned = (title or "").strip()
        if len(cleaned) < 10:
            continue
        where = [rel for rel, text in corpus.items() if cleaned in text]
        if where:
            hits[slug] = where
    return hits


# ---------------------------------------------------------------- 保留规则


def _primary_count(material: dict) -> int:
    return sum(1 for binding in material.get("bindings") or []
               if binding.get("role") == "PRIMARY")


def _ref_count(material: dict) -> int:
    """引用数 = 该材料在包内被声明的知识节点引用条数（当前包恒为 1）。"""
    return len(material.get("bindings") or [])


def _slug_suffix_len(slug: str) -> int:
    tail = slug.rsplit("-", 1)[-1]
    return len(tail) - len(tail.rstrip("abcdefghijklmnopqrstuvwxyz"))


def keep_rank(material: dict) -> tuple:
    """排序键：越小越该保留。写死规则见模块头。"""
    slug = material.get("slug") or ""
    return (
        -_primary_count(material),
        -len(material.get("contentMarkdown") or ""),
        -_ref_count(material),
        _slug_suffix_len(slug),
        slug,
    )


# ---------------------------------------------------------------- 聚类


class _UnionFind:
    def __init__(self) -> None:
        self.parent: dict[str, str] = {}

    def find(self, item: str) -> str:
        self.parent.setdefault(item, item)
        root = item
        while self.parent[root] != root:
            root = self.parent[root]
        while self.parent[item] != root:
            self.parent[item], item = root, self.parent[item]
        return root

    def union(self, left: str, right: str) -> None:
        left_root, right_root = self.find(left), self.find(right)
        if left_root != right_root:
            self.parent[right_root] = left_root


def find_clusters(groups: dict[str, list[dict]], threshold: float = JACCARD_THRESHOLD
                  ) -> tuple[list[dict], list[dict]]:
    """返回 (clusters, pairs)。

    clusters: 连通分量（只含通过精度门的边）
    pairs:    每个同节点内 Jaccard ≥ threshold 的候选对（含 MinHash 估计与精确值）
    """
    pairs: list[dict] = []
    union = _UnionFind()
    members: dict[str, dict] = {}
    node_of: dict[str, str] = {}
    for node_id in sorted(groups):
        items = [m for m in groups[node_id] if m.get("slug")]
        if len(items) < 2:
            continue
        hashes = [shingle_hashes(m) for m in items]
        signatures = [minhash_signature(h) for h in hashes]
        titles = [canonical_title(m.get("title")) for m in items]
        for material in items:
            members[material["slug"]] = material
            node_of[material["slug"]] = node_id
        for i in range(len(items)):
            for j in range(i + 1, len(items)):
                estimate = minhash_jaccard(signatures[i], signatures[j])
                if estimate < MINHASH_CANDIDATE_THRESHOLD:
                    continue
                exact = exact_jaccard(hashes[i], hashes[j])
                if exact < threshold:
                    continue
                same_title = bool(titles[i]) and titles[i] == titles[j]
                left, right = items[i]["slug"], items[j]["slug"]
                pairs.append({
                    "node_id": node_id,
                    "node_slug": node_slug_of(node_id),
                    "left": left,
                    "right": right,
                    "minhash": round(estimate, 6),
                    "exact": round(exact, 6),
                    "canonical_title_equal": same_title,
                })
                if same_title:      # 只有精度门通过的对才进簇
                    union.union(left, right)
    components: dict[str, list[str]] = defaultdict(list)
    for slug in sorted(members):
        if slug in union.parent:
            components[union.find(slug)].append(slug)
    clusters = []
    for root, slugs in sorted(components.items()):
        if len(slugs) < 2:
            continue
        clusters.append({
            "cluster_id": _short_hash(root),
            "node_id": node_of[slugs[0]],
            "node_slug": node_slug_of(node_of[slugs[0]]),
            "subject": members[slugs[0]].get("subject") or "",
            "members": sorted(slugs),
        })
    return clusters, pairs


def _short_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:12]


# ---------------------------------------------------------------- 退休计划


def build_plan(groups: dict[str, list[dict]], anchors: dict[str, list[str]],
               title_hits: dict[str, list[str]] | None = None,
               extra_protected: dict[str, list[str]] | None = None) -> dict:
    clusters, pairs = find_clusters(groups)
    by_slug = {m["slug"]: m for items in groups.values() for m in items}
    protected = set(anchors) | set(extra_protected or {})
    title_hits = title_hits or {}

    pair_index = {(p["left"], p["right"]): p for p in pairs}
    plan_clusters: list[dict] = []
    retire: list[dict] = []
    for cluster in clusters:
        slugs = cluster["members"]
        materials = [by_slug[s] for s in slugs]
        ordered = sorted(materials, key=keep_rank)
        protected_members = [m["slug"] for m in ordered if m["slug"] in protected]

        def similar(left: str, right: str) -> bool:
            key = (left, right) if (left, right) in pair_index else (right, left)
            pair = pair_index.get(key)
            if not (pair and pair["canonical_title_equal"] and pair["exact"] >= JACCARD_THRESHOLD):
                return False
            # 精度门之二：正文里出现短实词替换 = 换了实体，不是重复。
            return not substantive_substitutions(
                by_slug[right].get("contentMarkdown"), by_slug[left].get("contentMarkdown"))

        def skip_reason(left: str, right: str) -> str:
            key = (left, right) if (left, right) in pair_index else (right, left)
            pair = pair_index.get(key)
            if not (pair and pair["canonical_title_equal"] and pair["exact"] >= JACCARD_THRESHOLD):
                return "SKIP: chained cluster, not similar to kept material"
            subs = substantive_substitutions(
                by_slug[right].get("contentMarkdown"), by_slug[left].get("contentMarkdown"))
            if subs:
                old, new = subs[0]
                return f"SKIP: substantive token differs ({old or '∅'}→{new or '∅'})"
            return "SKIP: not retired"

        kept_slugs: list[str] = []
        retired_here: list[tuple[str, str, str]] = []   # (slug, kept, reason)
        if protected_members:
            kept_slugs = protected_members
            for material in ordered:
                slug = material["slug"]
                if slug in protected:
                    continue
                match = next((p for p in protected_members if similar(slug, p)), None)
                if match:
                    retired_here.append((slug, match, "near-duplicate of protected anchor"))
                else:
                    retired_here.append((slug, "", skip_reason(slug, protected_members[0])))
        else:
            kept = ordered[0]["slug"]
            kept_slugs = [kept]
            for material in ordered[1:]:
                slug = material["slug"]
                if similar(slug, kept):
                    retired_here.append((slug, kept, "near-duplicate"))
                else:
                    retired_here.append((slug, "", skip_reason(slug, kept)))

        for slug, kept, reason in retired_here:
            material = by_slug[slug]
            row = {
                "cluster_id": cluster["cluster_id"],
                "node_id": cluster["node_id"],
                "node_slug": cluster["node_slug"],
                "subject": cluster["subject"],
                "slug": slug,
                "kept_slug": kept,
                "verdict": "SKIP" if reason.startswith("SKIP") else "RETIRE",
                "reason": reason,
                "title": material.get("title") or "",
                "content_len": len(material.get("contentMarkdown") or ""),
                "protected": slug in protected,
                "title_mentioned_in": ";".join(title_hits.get(slug, [])),
            }
            if row["protected"]:
                row["verdict"] = "SKIP"
                row["reason"] = "protected anchor — never retired"
            if row["verdict"] == "RETIRE":
                retire.append(row)
        plan_clusters.append({
            "cluster_id": cluster["cluster_id"],
            "node_id": cluster["node_id"],
            "node_slug": cluster["node_slug"],
            "subject": cluster["subject"],
            "size": len(slugs),
            "kept": ",".join(kept_slugs),
            "retired": ",".join(r[0] for r in retired_here if not r[2].startswith("SKIP")),
            "skipped": ",".join(r[0] for r in retired_here if r[2].startswith("SKIP")),
            "members": ",".join(slugs),
            "protected_members": ",".join(protected_members),
        })
    return {
        "clusters": plan_clusters,
        "pairs": pairs,
        "retire": retire,
        "anchors": {slug: sorted(where) for slug, where in sorted(anchors.items())},
        "counts": {
            "materials": sum(len(items) for items in groups.values()),
            "nodes": len(groups),
            "clusters": len(plan_clusters),
            "pairs": len(pairs),
            "retire": len(retire),
            "skip": sum(1 for c in plan_clusters for _ in c["skipped"].split(",") if _),
            "anchors": len(anchors),
            "alias_protected": len(extra_protected or {}),
        },
    }


# ---------------------------------------------------------------- 写盘守卫


def _porcelain_digest() -> str:
    done = subprocess.run(["git", "status", "--porcelain"], cwd=str(REPO),
                          capture_output=True, text=True, check=False)
    return hashlib.sha256(done.stdout.encode("utf-8")).hexdigest()


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _material_json_sha(material: dict) -> str:
    payload = json.dumps(material, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def _affected_files(retire: list[dict]) -> list[Path]:
    paths = []
    slug_to_path = {}
    for path in pack_io.sidecar_paths():
        doc = pack_io.load_json(path)
        for material in doc.get("materials") or []:
            slug_to_path[material.get("slug")] = path
    for row in retire:
        path = slug_to_path.get(row["slug"])
        if path and path not in paths:
            paths.append(path)
    return paths


def _write_outputs(plan: dict, now: str, protected: dict[str, list[str]] | None = None,
                   out_dir: Path | None = None) -> None:
    out = Path(out_dir) if out_dir is not None else OUT_DIR
    out.mkdir(parents=True, exist_ok=True)
    with (out / "dedupe-clusters.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=("cluster_id", "node_id", "node_slug", "subject",
                                                    "size", "kept", "retired", "skipped", "members",
                                                    "protected_members"))
        writer.writeheader()
        writer.writerows(plan["clusters"])
    with (out / "dedupe-pairs.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=("node_id", "node_slug", "left", "right",
                                                    "minhash", "exact", "canonical_title_equal"))
        writer.writeheader()
        writer.writerows(sorted(plan["pairs"], key=lambda p: (p["node_slug"], p["left"], p["right"])))
    with (out / "retire-list.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=("cluster_id", "node_id", "node_slug", "subject",
                                                    "slug", "kept_slug", "verdict", "reason", "title",
                                                    "content_len", "protected", "title_mentioned_in"))
        writer.writeheader()
        writer.writerows(plan["retire"])
    with (out / "anchors.txt").open("w", encoding="utf-8") as handle:
        for slug, where in sorted(plan["anchors"].items()):
            handle.write(f"{slug}\t{';'.join(where)}\n")

    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=str(REPO),
                          capture_output=True, text=True, check=False).stdout.strip()
    affected = _affected_files(plan["retire"])
    file_shas = {str(p.relative_to(REPO)): _sha256_file(p) for p in affected if p.is_file()}
    for part in sorted(judgments.parts_dir().glob("*.csv")):
        file_shas[str(part.relative_to(REPO))] = _sha256_file(part)
    payload = {
        "generated_at": now,
        "head": head,
        "porcelain_sha256": _porcelain_digest(),
        "threshold": JACCARD_THRESHOLD,
        "num_perm": NUM_PERM,
        "counts": plan["counts"],
        "anchors": plan["anchors"],
        # 保护清单（slug → 来源）+ sha256：写后重算要与它一致（R3「保护清单未漂移」）
        "protected": {slug: list(reasons) for slug, reasons in sorted((protected or {}).items())},
        "protected_sha256": _sha256_bytes(json.dumps(
            {slug: list(reasons) for slug, reasons in sorted((protected or {}).items())},
            ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")),
        "retire": plan["retire"],
        "file_sha256": file_shas,
    }
    (out / "plan.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")


def apply_plan(plan_path: Path, out_dir: Path | None = None) -> int:
    out = Path(out_dir) if out_dir is not None else OUT_DIR
    if not plan_path.exists():
        print(f"✗ 找不到 plan：{plan_path}（先跑 --dry-run）", file=sys.stderr)
        return 2
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    if _porcelain_digest() != plan["porcelain_sha256"]:
        print("✗ porcelain 守卫失败：dry 之后工作树有变化（别人/别的批次写盘了）。"
              "重新 dry 并复核 retire 清单，拒绝盲写。", file=sys.stderr)
        return 3
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=str(REPO),
                          capture_output=True, text=True, check=False).stdout.strip()
    if head != plan["head"]:
        print(f"✗ HEAD 守卫失败：plan={plan['head']} now={head}", file=sys.stderr)
        return 3

    retire = plan["retire"]
    if not retire:
        print("retire 清单为空，无需写盘。")
        return 0

    # sha 守卫
    for rel, expected in sorted(plan["file_sha256"].items()):
        path = REPO / rel
        if path.is_file() and _sha256_file(path) != expected:
            print(f"✗ sha 守卫失败：{rel} 与 dry 时不一致", file=sys.stderr)
            return 3

    # 备份
    backup = out / "backup"
    backup.mkdir(parents=True, exist_ok=True)
    for rel in sorted(plan["file_sha256"]):
        src = REPO / rel
        if src.is_file():
            dst = backup / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)

    # 索引：slug -> (sidecar path, index, material)
    sidecars = pack_io.sidecar_paths()
    docs = {path: pack_io.load_json(path) for path in sidecars}
    nodes_before = {b.get("knowledgeNodeId") for doc in docs.values()
                    for m in doc.get("materials") or [] for b in m.get("bindings") or []
                    if b.get("knowledgeNodeId")}
    location: dict[str, tuple[Path, int, dict]] = {}
    for path, doc in docs.items():
        for index, material in enumerate(doc.get("materials") or []):
            location[material.get("slug")] = (path, index, material)

    # 判定行索引（ext- 材料）
    rows = judgments.load_all()
    row_by_slug: dict[str, dict] = {}
    for row in rows:
        if row.get("action") == "MATERIAL":
            slug = judged_slug_of(row.get("chunk_rel", ""), row.get("chunk_id", ""), row.get("midx", ""))
            if slug:
                row_by_slug[slug] = row

    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    ledger_rows: list[dict] = []
    retired_json_dir = out / "retired-json"
    retired_json_dir.mkdir(parents=True, exist_ok=True)
    missing: list[str] = []
    skipped_protected: list[str] = []
    for row in sorted(retire, key=lambda r: r["slug"]):
        slug = row["slug"]
        if slug in plan["anchors"]:
            skipped_protected.append(slug)
            continue
        hit = location.get(slug)
        if not hit:
            missing.append(slug)
            continue
        path, index, material = hit
        material_sha = _material_json_sha(material)
        pre_sha = _sha256_file(path)
        (retired_json_dir / f"{slug}.json").write_text(
            json.dumps(material, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
        ledger_rows.append({
            "slug": slug,
            "kept_slug": row["kept_slug"],
            "subject": material.get("subject") or row["subject"],
            "node_slug": row["node_slug"],
            "title": material.get("title") or "",
            "summary": material.get("summaryMarkdown") or "",
            "source_file": path.name,
            "source_index": index,
            "material_json_sha256": material_sha,
            "pre_sidecar_sha256": pre_sha,
            "retired_at": now,
            "reverted_at": "",
            "reason": f"near-dup dedupe (minhash 3-gram, jaccard>={JACCARD_THRESHOLD:.2f}); kept={row['kept_slug']}",
        })
        judged = row_by_slug.get(slug)
        if judged is not None:
            judged["action"] = "SKIP"
            note = (judged.get("note") or "").strip()
            suffix = f"v27 near-dup retired; kept={row['kept_slug']}"
            judged["note"] = f"{note}; {suffix}" if note else suffix

    if skipped_protected:
        print(f"✗ 计划里混入受保护材料（不应发生）：{skipped_protected}", file=sys.stderr)
        return 4
    if missing:
        print(f"✗ 有 {len(missing)} 条在包里找不到：{missing[:10]}", file=sys.stderr)
        return 4

    # 先落判定表，再落侧车；任一步失败可回滚（备份在案）
    judgments.write_all(rows)

    retire_set = {row["slug"] for row in ledger_rows}
    removed = 0
    for path, doc in docs.items():
        before = len(doc.get("materials") or [])
        doc["materials"] = [m for m in doc.get("materials") or [] if m.get("slug") not in retire_set]
        removed += before - len(doc["materials"])
        if len(doc["materials"]) != before:
            pack_io.dump_json(doc, path)

    # 台账（append-only）
    # 只有**未回滚**的行才算"已经退休过"：回滚过的材料重新退休时必须再记一行，
    # 否则台账会显示 0 条新增，而侧车确实又少了一条（台账与实际不一致，无法审计）。
    existing: set[str] = set()
    if RETIRE_LEDGER.exists():
        with RETIRE_LEDGER.open(encoding="utf-8", newline="") as handle:
            existing = {(r.get("slug") or "").strip() for r in csv.DictReader(handle)
                        if not (r.get("reverted_at") or "").strip()}
    new_rows = [r for r in ledger_rows if r["slug"] not in existing]
    if new_rows:
        write_header = not RETIRE_LEDGER.exists()
        with RETIRE_LEDGER.open("a", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=LEDGER_COLUMNS)
            if write_header:
                writer.writeheader()
            writer.writerows(new_rows)

    # 写后自检
    groups_after = material_groups()
    present = {m["slug"] for items in groups_after.values() for m in items}
    leaked = sorted(retire_set & present)
    anchors_after = load_anchor_slugs(present)
    lost_anchors = sorted(set(plan["anchors"]) - set(anchors_after))
    # 扩保护三类：金标 / THIN 在写前的保护集必须一条都没被退休；且回滚后重算的保护清单
    # 要与 plan 记录的一致（保护清单未漂移）
    protected_plan = plan.get("protected") or {}
    protected_hit = sorted(retire_set & set(protected_plan))
    protected_now = merge_protections(load_alias_backed_slugs(groups_after),
                                      load_golden_expected_slugs(groups=groups_after),
                                      load_thin_node_slugs(groups_after))
    protected_sha_now = _sha256_bytes(json.dumps(
        protected_now, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8"))
    # 节点材料数：写前有材料的节点，写后必须仍有材料（降到 0 条 = 节点从可教变不可教）
    dropped = sorted(node_slug_of(n) for n in (nodes_before - set(groups_after)))
    thin_after = sorted(s for s, reasons in protected_plan.items()
                        if any(r.startswith("thin:") for r in reasons) and s not in present)
    print(f"写盘完成：侧车移除 {removed} 条，判定行 SKIP {len(ledger_rows)} 条，"
          f"台账新增 {len(new_rows)} 行")
    if leaked:
        print(f"✗ 写后自检失败：{len(leaked)} 条仍在包里 {leaked[:10]}", file=sys.stderr)
        return 5
    if lost_anchors:
        print(f"✗ 锚点自检失败：{lost_anchors}", file=sys.stderr)
        return 5
    if protected_hit:
        print(f"✗ 保护自检失败：{len(protected_hit)} 条受保护材料被退休 {protected_hit[:10]}",
              file=sys.stderr)
        return 5
    if thin_after:
        print(f"✗ THIN 自检失败：受 THIN 保护却被移除 {len(thin_after)} 条 {thin_after[:10]}",
              file=sys.stderr)
        return 5
    if dropped:
        print(f"✗ 结构自检失败：{len(dropped)} 个节点降到 0 条材料 {dropped[:10]}", file=sys.stderr)
        return 5
    print(f"✓ 写后自检：退休材料 0 条残留；锚点 {len(anchors_after)}/{len(plan['anchors'])} 在位；"
          f"受保护材料被退休 0 条；节点材料数降到 0 条 0 个；"
          f"保护清单 {len(protected_plan)} 条（写后口径 sha {protected_sha_now[:12]}…）")
    return 0


# ---------------------------------------------------------------- CLI


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="只出候选簇/退休清单（默认行为）")
    parser.add_argument("--apply", type=Path, default=None,
                        help="按 plan 写盘（需 dry 产出的 plan.json）")
    parser.add_argument("--plan", type=Path, default=OUT_DIR / "plan.json")
    parser.add_argument("--out-dir", type=Path, default=None,
                        help="本轮产物目录（默认 build/agent-batch31c；新一批请指到自己的目录，别覆盖旧基线）")
    parser.add_argument("--json", action="store_true", help="把 counts 打到 stdout")
    args = parser.parse_args(argv)
    if args.dry_run and args.apply is not None:
        parser.error("--dry-run 与 --apply 互斥")

    if args.apply is not None:
        return apply_plan(args.apply, out_dir=args.out_dir)

    started = time.time()
    groups = material_groups()
    known = {m["slug"] for items in groups.values() for m in items}
    anchors = load_anchor_slugs(known)
    alias_backed = load_alias_backed_slugs(groups)
    golden = load_golden_expected_slugs(groups=groups)
    thin = load_thin_node_slugs(groups)
    protected = merge_protections(alias_backed, golden, thin)
    golden_nodes = sum(1 for node_id, items in groups.items()
                       if node_slug_of(node_id) in _golden_expected_slugs())
    thin_nodes = sum(1 for items in groups.values() if len(items) <= 2)
    titles = {m["slug"]: (m.get("title") or "") for items in groups.values() for m in items}
    plan = build_plan(groups, anchors, extra_protected=protected)
    retire_slugs = {row["slug"] for row in plan["retire"]}
    title_hits = title_mentions({s: titles[s] for s in retire_slugs})
    plan = build_plan(groups, anchors, title_hits, extra_protected=protected)
    counts = plan["counts"]
    counts["alias_protected"] = len(alias_backed)
    counts["golden_protected"] = len(golden)
    counts["thin_protected"] = len(thin)
    counts["golden_nodes"] = golden_nodes
    counts["thin_nodes"] = thin_nodes
    counts["protected_total"] = len(set(anchors) | set(protected))
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")
    _write_outputs(plan, now, protected=protected, out_dir=args.out_dir)

    counts["title_mentioned_in_tests"] = len(title_hits)
    print(f"材料 {counts['materials']} / 节点 {counts['nodes']}；候选对 {counts['pairs']}；"
          f"簇 {counts['clusters']}；拟退休 {counts['retire']}；SKIP {counts['skip']}；"
          f"受保护锚点 {counts['anchors']}；标题别名唯一支撑保护 {counts['alias_protected']}；"
          f"金标节点 {counts['golden_nodes']}（{counts['golden_protected']} 条材料）；"
          f"THIN 节点 {counts['thin_nodes']}（{counts['thin_protected']} 条材料）；"
          f"保护合计 {counts['protected_total']}；"
          f"标题告警 {len(title_hits)}（{time.time() - started:.1f}s）")
    if title_hits:
        print("⚠ 以下退休候选的完整标题出现在 tests/gates，人工复核：")
        for slug, where in sorted(title_hits.items()):
            print(f"  {slug} <- {', '.join(where)}")
    out = Path(args.out_dir) if args.out_dir is not None else OUT_DIR
    print(f"→ {out / 'dedupe-clusters.csv'}")
    print(f"→ {out / 'retire-list.csv'}")
    print(f"→ {out / 'plan.json'}")
    if args.json:
        print(json.dumps(counts, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
