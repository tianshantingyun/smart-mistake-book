# -*- coding: utf-8 -*-
"""340 官方节 ↔ 661 课标条目 ↔ 节点子树 ↔ 材料计数：只读终验 join。

## 它消灭的失败

教材目录终验此前只有三份互不相连的中间物：W-7 四份分册报告（340 官方节 + 20 非节行）、
`curriculum_alignment.csv`（661 条课标条目 → 原子点）、现包主题树与 sidecar 材料。
没有任何一份可逐节回答“这一官方节在现包落到哪个节点/主题、带多少材料、对应哪些课标条目、
批 1–13 在 FINAL 台账里怎么处置”。本模块把四者按同一官方节键 join 成一张可复算的台账，
并自带行数/键/章表/材料守恒/课标 target 五类自检。

## 口径（写死，不随输出漂移）

- **官方节表**：四份 W-7 报告逐册表的 340 行（化学 60 / 生物 82 / 物理 125 / 数学 73）；
  另 20 行为非节行（化学 11 / 生物 4 / 物理 0 / 数学 5），单列。`chapter_structure_full.json`
  只作辅助基准（22 键 / 91 章 / 471 节，含「物理其余」127 节兜底；去兜底 344 节，
  与官方 340 的已知差异记录在自检说明，不参与 340 键构造）。
- **现包落点**：按 ①官方节同名主题（名或 slug 末段）→ ②同名/包含该节名的知识点所在主题
  → ③FINAL §3「现包现状」列指认的覆盖主题 → ④官方章容器，四级回退；同级多落点时全部列出。
- **材料数**：sidecar 中绑定到落点子树（含后代主题）知识点的**去重材料**数。
- **课标条目**：`curriculum_alignment.csv` 的 `target_node_slug` 是原子点；落在子树内的点
  所命中的行按 `(subject, candidate_slug)` 去重后，汇总 status 与 target。
- **处置注**：只取 `FINAL-COVERAGE-TABLE.md` §4 与 §11 原文（批11 F2 残差处置，含 item_id 与
  disposition）；FINAL 未记载的批12/13 不臆造，只在“现包落点”列体现 v16 实测。

## 用法

    PYTHONPATH=tools python -m kb_coverage.final_coverage_join --write
    PYTHONPATH=tools python -m kb_coverage.final_coverage_join          # 只读推演，不落盘
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DOCS = REPO / "docs" / "kb-textbook-toc-align"
REFERENCE = DOCS / "reference"
DEFAULT_PACK = REPO / "build" / "kb-staging" / "moe-2025-four-subjects-v1.json"
DEFAULT_FINAL = DOCS / "FINAL-COVERAGE-TABLE.md"
DEFAULT_CURRICULUM = REPO / "tools" / "kb_coverage" / "tables" / "curriculum_alignment.csv"
DEFAULT_OUT = DOCS / "SECTION-BY-SECTION-LEDGER.md"

VOLUME_NAMES: dict[str, list[str]] = {
    "CHEMISTRY": ["化学必修第一册", "化学必修第二册", "化学选择性必修1",
                  "化学选择性必修2", "化学选择性必修3"],
    "BIOLOGY": ["生物学必修1", "生物学必修2", "生物学选择性必修1",
                "生物学选择性必修2", "生物学选择性必修3"],
    "PHYSICS": ["物理必修第一册", "物理必修第二册", "物理必修第三册",
                "物理选择性必修第一册", "物理选择性必修第二册", "物理选择性必修第三册"],
    "MATH": ["数学必修第一册", "数学必修第二册", "数学选择性必修第一册",
             "数学选择性必修第二册", "数学选择性必修第三册"],
}
ALL_VOLUMES: dict[str, str] = {v: s for s, vs in VOLUME_NAMES.items() for v in vs}
SUBJECT_CN = {"CHEMISTRY": "化学", "BIOLOGY": "生物学", "PHYSICS": "物理", "MATH": "数学"}
OFFICIAL_SECTION_COUNTS = {"CHEMISTRY": 60, "BIOLOGY": 82, "PHYSICS": 125, "MATH": 73}
OFFICIAL_NONSECTION_COUNTS = {"CHEMISTRY": 11, "BIOLOGY": 4, "PHYSICS": 0, "MATH": 5}
TOTAL_OFFICIAL_SECTIONS = 340
TOTAL_NONSECTIONS = 20

# FINAL §4.1 伪章行里被点名的新落点主题（原文摘录，供行级匹配；不是新判断）。
PSEUDO_TOPIC_TOKENS: dict[str, list[str]] = {
    "化学必修第一册·第二章第三节": ["物质的量"],
    "化学必修第二册·第五章第三节": ["无机非金属材料", "反应热的计算"],
    "生物学必修1·第五章第三节": ["细胞呼吸", "细胞呼吸的原理和应用"],
    "生物学必修1·第五章第四节": ["光合作用", "光合作用与能量转化"],
    "生物学必修2·第一章第二节": ["孟德尔的豌豆杂交实验（一）", "基因在染色体上",
                                   "伴性遗传", "遗传的基本规律"],
}

# FINAL §4.4 游离主题 → 官方节/章落点（逐条取自 §4.4/§11.5 原文的箭头目标）。
FREE_TOPIC_TARGETS: dict[str, str | None] = {
    "有机物的结构与表示": "有机化合物的结构特点",
    "烷烃的结构与性质": "烷烃",
    "脂肪烃": "烯烃 炔烃",
    "醇": "醇 酚",
    "酚": "醇 酚",
    "醛和酮": "醛 酮",
    "酯和油脂": "羧酸 羧酸衍生物",
    "胺和酰胺": "羧酸 羧酸衍生物",
    "氨基酸": "蛋白质",
    "化石燃料的综合利用": "自然资源的开发利用",
    "分子间作用力": "分子结构与物质的性质",
    "晶胞与均摊法": "物质的聚集状态与晶体的常识",
    "晶体的性质": "晶体结构与性质",
    "铝及其化合物": "金属材料",
    "化学实验基础": None,
    "物质的分离与提纯": None,
    "用高倍显微镜观察细胞": "细胞的多样性和统一性",
}

CHAPTER_RE = re.compile(r"第([一二三四五六七八九十百零\d]+)(?:至([一二三四五六七八九十百零\d]+))?章")
ROW_RE = re.compile(r"^\|(.*)\|\s*$")
ITEM_RE = re.compile(r"kit-\d+#\d+")
CN_DIGITS = {**{str(i): c for i, c in enumerate("一二三四五六七八九", 1)}, "10": "十"}


# ---------------------------------------------------------------------------
# 文本归一 / 章号


def normalize_name(text: str) -> str:
    """去 markdown、空白、引号、节/章号与题号；用于名面匹配（不做语义判断）。"""
    s = (text or "")
    s = s.replace("**", "").replace("`", "")
    s = s.replace("\u3000", " ").replace(" ", "")
    for quote in ('"', "'", "\u201c", "\u201d", "\u2018", "\u2019"):
        s = s.replace(quote, "")
    s = re.sub(r"^第[一二三四五六七八九十百零\d]+[章节]\s*", "", s)
    s = re.sub(r"^\d+(\.\d+)*[\.\s、]*", "", s)
    s = s.lstrip("*＊").strip()
    s = re.sub(r"^科技探索之路\s*", "", s)
    return s.strip(" ·→；,，。.、：:【】[]（）()")


def name_variants(text: str) -> list[str]:
    """原名 + 去括号内容（全角/半角）的匹配变体。"""
    out: list[str] = []
    for candidate in (text, re.sub(r"（[^）]*）", "", text), re.sub(r"\([^)]*\)", "", text)):
        candidate = candidate.strip()
        if candidate and candidate not in out:
            out.append(candidate)
    return out


def _num_value(token: str) -> int | None:
    if token.isdigit():
        return int(token)
    token = CN_DIGITS.get(token, token)
    if token == "十":
        return 10
    if len(token) == 2 and token[0] == "十":
        return 10 + _num_value(token[1]) if token[1] in CN_DIGITS else None
    if len(token) == 2 and token[1] == "十":
        return (_num_value(token[0]) or 0) * 10
    return {"一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7,
            "八": 8, "九": 9}.get(token)


def chapter_numbers(text: str) -> set[str]:
    """从章名/slug 取涉及的章号（支持 第五六章 / 第九十章 / 第一至三章 / 第一章第二节）。"""
    s = (text or "").replace(" ", "")
    m = CHAPTER_RE.search(s)
    if not m:
        return set()
    a, b = m.group(1), m.group(2)
    a = CN_DIGITS.get(a, a)
    if b:
        b = CN_DIGITS.get(b, b)
        if a == "一" and b == "三":
            return {"一", "二", "三"}
        return {a, b}
    if a == "十一十二":
        return {"十一", "十二"}
    if a in ("十一", "十二"):
        return {a}
    if len(a) >= 2 and all(ch in "一二三四五六七八九十" for ch in a):
        return set(a)
    return {a}


def chapter_distance(left: set[str], right: set[str]) -> int | None:
    lvals = [v for v in (_num_value(x) for x in left) if v is not None]
    rvals = [v for v in (_num_value(x) for x in right) if v is not None]
    if not lvals or not rvals:
        return None
    return min(abs(a - b) for a in lvals for b in rvals)


def volume_from_title(title: str) -> str | None:
    """报告/台账标题 → 官方册名（最长匹配，避免「必修1」误配到「选择性必修1」）。"""
    text = (title or "").replace("（A版）", "").replace("(A版)", "")
    hits = [v for v in ALL_VOLUMES if v in text]
    return max(hits, key=len) if hits else None


# ---------------------------------------------------------------------------
# 输入解析


@dataclass(frozen=True)
class OfficialRow:
    subject: str
    volume: str
    chapter: str
    section: str
    report_file: str
    report_line: int
    verdict: str


def is_non_section_row(row: OfficialRow) -> bool:
    """非节行：官方节栏以括号开头、官方章栏以括号开头、栏目名或「—」。"""
    section = (row.section or "").strip()
    chapter = (row.chapter or "").strip()
    return (section.startswith("（") or chapter.startswith("（")
            or section in ("—", "") or section.startswith("科技探索之路"))


def parse_reports(docs_dir: Path) -> list[OfficialRow]:
    """解析四份 W-7 报告「## 逐册对照」下的 4 列行（340 节 + 20 非节 = 360）。"""
    rows: list[OfficialRow] = []
    for filename, subject in (("CHEMISTRY.md", "CHEMISTRY"), ("BIOLOGY.md", "BIOLOGY"),
                              ("PHYSICS.md", "PHYSICS"), ("MATH.md", "MATH")):
        path = Path(docs_dir) / filename
        if not path.exists():
            raise FileNotFoundError(path)
        volume: str | None = None
        in_section = False
        for line_no, text in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if text.startswith("## 逐册对照"):
                in_section = True
                continue
            if text.startswith("## ") and not text.startswith("## 逐册对照"):
                in_section = False
            heading = re.match(r"^### (.+)$", text)
            if heading and in_section:
                volume = volume_from_title(heading.group(1).strip())
                continue
            if not in_section or volume is None:
                continue
            match = ROW_RE.match(text)
            if not match:
                continue
            cells = [c.strip() for c in match.group(1).split("|")]
            if len(cells) != 4 or cells[0].startswith("---") or "官方章" in cells[0]:
                continue
            rows.append(OfficialRow(subject=subject, volume=volume, chapter=cells[0],
                                    section=cells[1], report_file=filename,
                                    report_line=line_no, verdict=cells[2]))
    return rows


@dataclass
class FinalCell:
    volume: str
    chapter: str
    section: str
    status: str
    note: str
    line: int


class FinalTable:
    def __init__(self, cells: list[FinalCell]) -> None:
        self.cells = cells
        self._by_key: dict[tuple[str, str, str], list[FinalCell]] = defaultdict(list)
        for cell in cells:
            self._by_key[(cell.volume, cell.chapter, cell.section)].append(cell)

    def get(self, volume: str, chapter: str, section: str, occurrence: int = 0) -> FinalCell | None:
        hits = self._by_key.get((volume, chapter, section), [])
        if not hits:
            return None
        return hits[min(occurrence, len(hits) - 1)]

    def has(self, volume: str, chapter: str, section: str) -> bool:
        return (volume, chapter, section) in self._by_key


def parse_final_table(path: Path) -> FinalTable:
    """解析 FINAL §3 的 5 列逐册表；重复非节键按出现顺序保留。"""
    cells: list[FinalCell] = []
    volume: str | None = None
    in_section = False
    for line_no, text in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if text.startswith("## 3."):
            in_section = True
            continue
        if text.startswith("## 4."):
            break
        if not in_section:
            continue
        heading = re.match(r"^#### (.+?)（官方", text)
        if heading:
            volume = volume_from_title(heading.group(1).strip())
            continue
        if text.startswith("#### "):
            continue
        match = ROW_RE.match(text)
        if not match:
            continue
        cells_raw = [c.strip() for c in match.group(1).split("|")]
        if len(cells_raw) != 5 or cells_raw[0].startswith("---") or "官方章" in cells_raw[0]:
            continue
        if volume is None:
            continue
        cells.append(FinalCell(volume=volume, chapter=cells_raw[0], section=cells_raw[1],
                               status=cells_raw[3], note=cells_raw[4], line=line_no))
    return FinalTable(cells)


@dataclass
class CurriculumRow:
    subject: str
    candidate_slug: str
    status: str
    target: str
    evidence: str
    note: str


class Curriculum:
    def __init__(self, rows: list[CurriculumRow]) -> None:
        self.rows = rows
        self.by_point: dict[tuple[str, str], list[CurriculumRow]] = defaultdict(list)
        for row in rows:
            if row.target:
                self.by_point[(row.subject, row.target)].append(row)

    def targets_for_points(self, subject: str, points: set[str]) -> list[CurriculumRow]:
        out: list[CurriculumRow] = []
        seen: set[tuple[str, str]] = set()
        for point in sorted(points):
            for row in self.by_point.get((subject, point), []):
                key = (row.subject, row.candidate_slug)
                if key not in seen:
                    seen.add(key)
                    out.append(row)
        return out


def load_curriculum(path: Path) -> Curriculum:
    with Path(path).open(encoding="utf-8", newline="") as fh:
        rows = [CurriculumRow(
            subject=(r.get("subject") or "").strip(),
            candidate_slug=(r.get("candidate_slug") or "").strip(),
            status=(r.get("status") or "").strip(),
            target=(r.get("target_node_slug") or "").strip(),
            evidence=(r.get("evidence_phrase") or "").strip(),
            note=(r.get("note") or "").strip(),
        ) for r in csv.DictReader(fh)]
    return Curriculum(rows)


@dataclass
class Disposition:
    source: str
    kind: str
    text: str
    item_ids: list[str] = field(default_factory=list)
    volume: str | None = None
    chapter: str | None = None
    section: str | None = None
    topic: str | None = None
    disposition: str | None = None


def _extract_item_ids(text: str) -> list[str]:
    return list(dict.fromkeys(ITEM_RE.findall(text or "")))


def _clean_text(text: str) -> str:
    return (text or "").strip().strip("｜").strip()


def _split_volume_from_section(cell: str) -> tuple[str | None, str]:
    """§4.3 官方节单元格 → (册, 节名)。"""
    text = (cell or "").strip()
    if text.startswith("（官方无此节）"):
        rest = text[len("（官方无此节）"):].strip()
        volume = None
        m = re.match(r"^（([^）]+)）", rest)
        if m:
            volume = volume_from_title(m.group(1))
        if volume is None:
            hits = [v for v in ALL_VOLUMES if v in text]
            volume = max(hits, key=len) if hits else None
        return volume, "（官方无此节）"
    trailing = re.search(r"（([^）]+)）\s*$", text)
    if trailing:
        volume = volume_from_title(trailing.group(1))
        if volume:
            return volume, text[:trailing.start()].strip()
    hits = [v for v in ALL_VOLUMES if v in text]
    if hits:
        volume = max(hits, key=len)
        return volume, text.replace(volume, "").strip()
    return None, text


def _target_from_text(text: str) -> str | None:
    """从处置原文箭头/「同节」/「落」提取目标节名（原文摘录，不做语义扩展）。"""
    for pattern in (r"→\s*([^；;|]+)", r"同节\s*([^；;|，,]+)",
                    r"落\s*([^；;|，,]+?)节位", r"建\s*([^；;|，,]+?)节"):
        m = re.search(pattern, text or "")
        if m:
            raw = re.sub(r"（[^）]*）", "", m.group(1))
            raw = re.sub(r"\([^)]*\)", "", raw)
            seg = raw.strip(" ·").split("·")[-1]
            seg = seg.strip(" ·；;，,。")
            if seg:
                return seg
    return None


def parse_final_dispositions(path: Path) -> list[Disposition]:
    """解析 FINAL §4（4.1–4.5）与 §11（11.1–11.6）的处置原文。"""
    lines = Path(path).read_text(encoding="utf-8").splitlines()
    out: list[Disposition] = []
    mode: str | None = None
    in_11 = False
    for line_no, text in enumerate(lines, 1):
        if text.startswith("## 批11") or text.startswith("# 批11"):
            in_11 = True
            mode = None
            continue
        m4 = re.match(r"^### 4\.(\d)", text)
        if m4:
            mode = f"4.{m4.group(1)}"
            continue
        m11 = re.match(r"^### 11\.(\d)", text)
        if m11:
            mode = f"11.{m11.group(1)}"
            continue
        if text.startswith("## ") and not text.startswith("## 批11"):
            if not text.startswith("### "):
                mode = None
            continue

        if mode == "4.1":
            m = re.match(r"^-\s*`([^`]+)`.*?｜\s*(.+)$", text)
            if m:
                pseudo, body = m.group(1).strip(), _clean_text(m.group(2))
                tokens = PSEUDO_TOPIC_TOKENS.get(pseudo, [])
                out.append(Disposition("§4.1", "pseudo", body, _extract_item_ids(text),
                                       section=pseudo, topic="、".join(tokens) or None))
            continue

        if mode == "4.2":
            m = ROW_RE.match(text)
            if not m:
                continue
            cells = [c.strip() for c in m.group(1).split("|")]
            if len(cells) != 4 or cells[0].startswith("---") or cells[0] == "册":
                continue
            volume = volume_from_title(cells[0])
            if not volume:
                continue
            out.append(Disposition("§4.2", "chapter", _clean_text(cells[3]),
                                   _extract_item_ids(text), volume=volume))
            continue

        if mode == "4.3":
            m = ROW_RE.match(text)
            if not m:
                continue
            cells = [c.strip() for c in m.group(1).split("|")]
            if len(cells) != 3 or cells[0].startswith("---") or cells[0].startswith("官方节"):
                continue
            volume, section = _split_volume_from_section(cells[0])
            body = _clean_text(cells[2].split("｜", 1)[-1] if "｜" in cells[2] else cells[2])
            out.append(Disposition("§4.3", "misplaced", body, _extract_item_ids(text),
                                   volume=volume, section=section,
                                   topic=cells[1]))
            continue

        if mode == "4.4":
            m = re.match(r"^-\s*([^（(]+)[（(][^）)]*[）)]\s*｜\s*(.+)$", text)
            if m:
                topic = m.group(1).strip()
                out.append(Disposition("§4.4", "free", _clean_text(m.group(2)),
                                       _extract_item_ids(text), topic=topic,
                                       section=FREE_TOPIC_TARGETS.get(topic)))
            continue

        if mode == "4.5":
            m = ROW_RE.match(text)
            if not m:
                continue
            cells = [c.strip() for c in m.group(1).split("|")]
            if len(cells) != 4 or cells[0].startswith("---") or cells[0] == "学科":
                continue
            volume = volume_from_title(cells[1])
            body = _clean_text(cells[3])
            out.append(Disposition("§4.5", "missing", body, _extract_item_ids(text),
                                   volume=volume, section=cells[2]))
            continue

        if mode and mode.startswith("11.") and mode != "11.6":
            m = ROW_RE.match(text)
            if not m:
                continue
            cells = [c.strip() for c in m.group(1).split("|")]
            if len(cells) != 3 or cells[0].startswith("---") or cells[0] == "item_id":
                continue
            item = cells[0]
            if not ITEM_RE.fullmatch(item):
                continue
            if mode == "11.1":
                body = f"{cells[1]} → {cells[2]}"
            elif mode == "11.3":
                body = f"{cells[1]}：{cells[2]}"
            else:
                body = f"{cells[1]}：{cells[2]}"
            volume = next((v for v in ALL_VOLUMES if v in body), None)
            out.append(Disposition(f"§{mode}", "item", f"{item} {body}", [item],
                                   volume=volume, disposition=cells[1]))
            continue

        if mode == "11.6":
            m = ROW_RE.match(text)
            if not m:
                continue
            cells = [c.strip() for c in m.group(1).split("|")]
            if len(cells) != 3 or cells[0].startswith("---") or cells[0] == "item_id":
                continue
            item = cells[0]
            if not ITEM_RE.fullmatch(item):
                continue
            out.append(Disposition("§11.6", "out_of_table", f"{item} {cells[1]}：{cells[2]}",
                                   [item], volume=next((v for v in ALL_VOLUMES if v in cells[2]), None),
                                   disposition=cells[1]))
            continue
    return out


def default_sidecar_paths(pack_path: Path) -> list[Path]:
    """与 Kotlin loader 同源：优先读 sidecar 索引，缺失时按文件名 glob。"""
    pack_path = Path(pack_path)
    index = pack_path.parent / "moe-2025-teaching-support-v2-index.json"
    if index.exists():
        doc = json.loads(index.read_text(encoding="utf-8"))
        return [pack_path.parent / name.rsplit("/", 1)[-1] for name in doc["sidecars"]]
    return sorted(pack_path.parent.glob("moe-2025-teaching-support-v2-*.json"))


# ---------------------------------------------------------------------------
# 现包索引


@dataclass(eq=False)
class TopicNode:
    subject: str
    name: str
    slug: str
    depth: int
    parent_slug: str | None
    points: list[str]
    volume: str | None
    chapter_name: str | None
    chapter_slug: str | None
    slug_last: str

    def __hash__(self) -> int:
        return hash((self.subject, self.slug))


class PackIndex:
    def __init__(self) -> None:
        self.topics: list[TopicNode] = []
        self.by_slug: dict[str, TopicNode] = {}
        self.name_index: dict[str, list[TopicNode]] = defaultdict(list)
        self.point_to_topic: dict[tuple[str, str], TopicNode] = {}
        self.point_name_index: dict[str, list[TopicNode]] = defaultdict(list)
        self.own_materials: dict[str, set[str]] = defaultdict(set)
        self.subtree_materials: dict[str, set[str]] = {}
        self.subtree_points: dict[str, set[str]] = {}
        self.total_materials = 0
        self.subject_material_counts: Counter[str] = Counter()
        self.subject_materials: dict[str, set[str]] = defaultdict(set)
        self.orphan_materials: list[tuple[str, str]] = []

    def lookup_topics(self, subject: str, name: str) -> list[TopicNode]:
        hits: list[TopicNode] = []
        for key in name_variants(normalize_name(name)):
            for topic in self.name_index.get(key, []):
                if topic.subject == subject:
                    hits.append(topic)
        return list({t.slug: t for t in hits}.values())

    def lookup_point_topics(self, subject: str, name: str) -> list[tuple[TopicNode, bool]]:
        """返回 (主题, 是否精确命中点名)。精确优先于包含；包含要求公共子串 >= 4 字。"""
        key = normalize_name(name)
        if not key:
            return []
        exact: list[TopicNode] = []
        contains: list[TopicNode] = []
        for point_key, topics in self.point_name_index.items():
            if not point_key:
                continue
            if point_key == key:
                exact.extend(topics)
            elif len(key) >= 4 and len(point_key) >= 4 and (key in point_key or point_key in key):
                contains.extend(topics)
        out: list[tuple[TopicNode, bool]] = []
        seen: set[str] = set()
        for topic in exact:
            if topic.subject == subject and topic.slug not in seen:
                seen.add(topic.slug)
                out.append((topic, True))
        for topic in contains:
            if topic.subject == subject and topic.slug not in seen:
                seen.add(topic.slug)
                out.append((topic, False))
        return out

    def material_count(self, topics: list[TopicNode]) -> int:
        mats: set[str] = set()
        for topic in topics:
            mats |= self.subtree_materials.get(topic.slug, set())
        return len(mats)

    def points_for(self, topics: list[TopicNode]) -> set[str]:
        points: set[str] = set()
        for topic in topics:
            points |= self.subtree_points.get(topic.slug, set())
        return points


def _material_subject(node_id: str) -> str | None:
    parts = (node_id or "").split(":")
    if len(parts) >= 2:
        return parts[-3].upper() if len(parts) >= 3 else None
    return None


def _material_point(node_id: str) -> str | None:
    parts = (node_id or "").split(":")
    return parts[-1] if parts else None


def load_pack_index(pack_path: Path, sidecar_paths: list[Path]) -> PackIndex:
    pack = json.loads(Path(pack_path).read_text(encoding="utf-8"))
    index = PackIndex()
    for subject_doc in pack["subjects"]:
        subject = subject_doc["subject"]
        by_slug = {t["slug"]: t for t in subject_doc["topics"]}
        for raw in subject_doc["topics"]:
            slug = raw["slug"]
            depth = slug.count("·")
            parts = slug.split("·")
            if depth >= 1:
                volume = parts[0]
                chapter_slug = "·".join(parts[:2]) if depth >= 2 else slug
                chapter = by_slug.get(chapter_slug)
                chapter_name = chapter["name"] if chapter else None
            else:
                volume = None
                chapter_slug = None
                chapter_name = None
            topic = TopicNode(
                subject=subject, name=raw["name"], slug=slug, depth=depth,
                parent_slug=raw.get("parentSlug"), points=[p["slug"] for p in (raw.get("knowledgePoints") or [])],
                volume=volume, chapter_name=chapter_name, chapter_slug=chapter_slug,
                slug_last=parts[-1],
            )
            index.topics.append(topic)
            index.by_slug[slug] = topic
            keys: set[str] = set()
            for source in (topic.name, topic.slug_last):
                for variant in name_variants(normalize_name(source)):
                    keys.add(variant)
            for key in keys:
                index.name_index[key].append(topic)
            for point in raw.get("knowledgePoints") or []:
                index.point_to_topic[(subject, point["slug"])] = topic
                point_keys = set()
                for source in (point["name"], point["slug"]):
                    for variant in name_variants(normalize_name(source)):
                        point_keys.add(variant)
                for key in point_keys:
                    index.point_name_index[key].append(topic)

    for path in sidecar_paths:
        doc = json.loads(Path(path).read_text(encoding="utf-8"))
        for material in doc.get("materials") or []:
            slug = material.get("slug") or material.get("contentId") or ""
            for binding in material.get("bindings") or []:
                node_id = binding.get("knowledgeNodeId") or ""
                subject = _material_subject(node_id)
                point = _material_point(node_id)
                if not subject or not point:
                    continue
                topic = index.point_to_topic.get((subject, point))
                if topic is None:
                    index.orphan_materials.append((slug, node_id))
                    continue
                index.own_materials[topic.slug].add(slug)
                index.subject_materials[subject].add(slug)

    index.total_materials = len({m for mats in index.own_materials.values() for m in mats})
    index.subject_material_counts = Counter({
        subject: len(materials) for subject, materials in index.subject_materials.items()})
    children: dict[str, list[str]] = defaultdict(list)
    for topic in index.topics:
        if topic.parent_slug:
            children[topic.parent_slug].append(topic.slug)

    memo_materials: dict[str, set[str]] = {}
    memo_points: dict[str, set[str]] = {}

    def walk(slug: str) -> tuple[set[str], set[str]]:
        if slug in memo_materials:
            return memo_materials[slug], memo_points[slug]
        mats = set(index.own_materials.get(slug, set()))
        pts: set[str] = set(index.by_slug[slug].points) if slug in index.by_slug else set()
        for child in children.get(slug, []):
            cm, cp = walk(child)
            mats |= cm
            pts |= cp
        memo_materials[slug] = mats
        memo_points[slug] = pts
        return mats, pts

    for topic in index.topics:
        walk(topic.slug)
    index.subtree_materials = memo_materials
    index.subtree_points = memo_points
    return index


# ---------------------------------------------------------------------------
# 匹配 / join


SOURCE_LABEL = {
    "exact": "同名节",
    "point_exact": "同名点所在主题",
    "point_contains": "含节名点所在主题",
    "final_arrow": "FINAL §3 覆盖主题",
    "final_same": "FINAL §3 同名主题",
    "final_path": "FINAL §3 路径主题",
    "final_point": "FINAL §3 点所在主题",
    "chapter": "官方章容器",
    "none": "无落点",
}
SOURCE_BONUS = {
    "exact": 1000,
    "point_exact": 900,
    "point_contains": 800,
    "final_arrow": 700,
    "final_same": 650,
    "final_path": 600,
    "final_point": 500,
    "chapter": 400,
}


def _topic_score(topic: TopicNode, row: OfficialRow) -> float:
    score = 0.0
    if topic.volume == row.volume:
        score += 100
    if topic.volume in ALL_VOLUMES:
        score += 50
    official_nums = chapter_numbers(row.chapter)
    topic_nums = chapter_numbers(topic.chapter_name or "") | chapter_numbers(topic.chapter_slug or "")
    if official_nums & topic_nums:
        score += 30
    else:
        distance = chapter_distance(official_nums, topic_nums)
        if distance is not None:
            score += max(0, 10 - distance)
    official_chapter = normalize_name(row.chapter)
    topic_chapter = normalize_name(topic.chapter_name or "")
    if official_chapter and topic_chapter:
        if official_chapter == topic_chapter:
            score += 25
        elif len(official_chapter) >= 3 and len(topic_chapter) >= 3:
            if official_chapter in topic_chapter or topic_chapter in official_chapter:
                score += 25
    score += min(topic.depth, 6)
    score += 25 if topic.points else 0
    score += min(len(topic.points), 100) * 0.001
    return score


def _last_path_segment(text: str) -> str:
    raw = re.sub(r"（[^）]*）", "", text or "")
    raw = re.sub(r"\([^)]*\)", "", raw)
    return raw.strip(" ·").split("·")[-1].strip(" ·；;，,。")


def final_cell_candidates(cell: str | None) -> list[tuple[str, str]]:
    """FINAL §3「现包现状」→ [(主题名, 来源)]，箭头目标优先。"""
    if not cell:
        return []
    out: list[tuple[str, str]] = []
    for raw in re.split(r"[；;]", cell):
        segment = raw.strip(" ·")
        if not segment or "已无" in segment or "无对应节点" in segment:
            continue
        if "→" in segment:
            name = _last_path_segment(segment.split("→")[-1])
            if name and not re.fullmatch(r"第[一二三四五六七八九十]+章", name):
                out.append((name, "final_arrow"))
            continue
        if "同名" in segment:
            after = segment.split("同名", 1)[1]
            after = re.sub(r"^（[^）]*）", "", after)
            name = _last_path_segment(after)
            if name:
                out.append((name, "final_same"))
            continue
        if "=点" in segment or "点（在" in segment:
            m = re.search(r"（在\s*([^）]*(?:（[^）]*）)?[^）]*)）", segment)
            if m:
                name = _last_path_segment(m.group(1))
                if name:
                    out.append((name, "final_point"))
            continue
        if "（" in segment:
            name = _last_path_segment(segment)
            if name:
                out.append((name, "final_path"))
    # 同优先级去重
    seen: set[tuple[str, str]] = set()
    unique: list[tuple[str, str]] = []
    for item in out:
        if item not in seen:
            seen.add(item)
            unique.append(item)
    return unique


def _chapter_fallback(row: OfficialRow, index: PackIndex) -> list[TopicNode]:
    official_nums = chapter_numbers(row.chapter)
    candidates = [t for t in index.topics
                  if t.subject == row.subject and t.volume == row.volume
                  and (chapter_numbers(t.chapter_name or "") | chapter_numbers(t.chapter_slug or "")) & official_nums]
    if not candidates:
        return []
    direct = [t for t in candidates if t.depth == 2]
    if len(direct) == 1:
        return direct
    chapters = [t for t in candidates if t.depth == 1]
    if chapters:
        return [max(chapters, key=lambda t: len(t.points))]
    return direct[:1]


def _chapter_compatible(row: OfficialRow, topic: TopicNode) -> bool:
    """点级落点只接受与官方章同章/同合并章的主题，避免把别章同名点当成本节落点。"""
    official_nums = chapter_numbers(row.chapter)
    topic_nums = chapter_numbers(topic.chapter_name or "") | chapter_numbers(topic.chapter_slug or "")
    if official_nums and topic_nums:
        if official_nums & topic_nums:
            return True
        return False
    official_chapter = normalize_name(row.chapter)
    topic_chapter = normalize_name(topic.chapter_name or "")
    return bool(official_chapter and topic_chapter
                and (official_chapter == topic_chapter
                     or official_chapter in topic_chapter
                     or topic_chapter in official_chapter))


def match_official_row(row: OfficialRow, index: PackIndex,
                       final_cell: str | None) -> tuple[list[TopicNode], str]:
    scored: list[tuple[float, TopicNode, str]] = []

    if not is_non_section_row(row):
        for topic in index.lookup_topics(row.subject, row.section):
            scored.append((SOURCE_BONUS["exact"] + _topic_score(topic, row), topic, "exact"))
        for topic, exact in index.lookup_point_topics(row.subject, row.section):
            compatible = _chapter_compatible(row, topic)
            if exact and not compatible:
                # 别章同名点只在 FINAL §3 明确以「X=点（在 …）」指认时才采信，
                # 否则优先落到官方章内（避免把别章同名点当本节落点）。
                section_key = normalize_name(row.section)
                if not (final_cell and "=点" in final_cell
                        and section_key and section_key in normalize_name(final_cell)):
                    continue
            if not exact and not compatible:
                continue
            source = "point_exact" if exact else "point_contains"
            scored.append((SOURCE_BONUS[source] + _topic_score(topic, row), topic, source))

    if not is_non_section_row(row):
        section_key = normalize_name(row.section)
        for name, source in final_cell_candidates(final_cell):
            for topic in index.lookup_topics(row.subject, name):
                bonus = 0.0
                topic_key = normalize_name(topic.name)
                if (source != "final_point" and len(section_key) >= 4
                        and _chapter_compatible(row, topic)
                        and section_key in topic_key):
                    bonus = 100.0
                scored.append((SOURCE_BONUS[source] + bonus + _topic_score(topic, row),
                               topic, source))
    else:
        for name, source in final_cell_candidates(final_cell):
            for topic in index.lookup_topics(row.subject, name):
                scored.append((SOURCE_BONUS[source] + _topic_score(topic, row), topic, source))

    if not scored:
        for topic in _chapter_fallback(row, index):
            scored.append((SOURCE_BONUS["chapter"] + _topic_score(topic, row), topic, "chapter"))

    if not scored:
        return [], "none"
    best = max(score for score, _t, _s in scored)
    top: list[TopicNode] = []
    source = "none"
    seen: set[str] = set()
    for score, topic, src in scored:
        if score == best and topic.slug not in seen:
            seen.add(topic.slug)
            top.append(topic)
            source = src
    return top, source


@dataclass
class LedgerRow:
    official: OfficialRow
    is_section: bool
    landing: list[TopicNode]
    landing_source: str
    material_count: int
    curriculum_status: Counter[str]
    curriculum_targets: list[tuple[str, str]]
    curriculum_total: int
    final_found: bool = True
    dispositions: list[Disposition] = field(default_factory=list)

    @property
    def disposition_text(self) -> str:
        if not self.dispositions:
            return "—（FINAL §4/§11 无对应条目）"
        parts: list[str] = []
        seen: set[tuple[str, str]] = set()
        seen_items: set[str] = set()
        for disposition in self.dispositions:
            key = (disposition.source, disposition.text)
            if key in seen:
                continue
            if disposition.item_ids and all(item in seen_items for item in disposition.item_ids):
                continue
            seen.add(key)
            seen_items.update(disposition.item_ids)
            prefix = ""
            if disposition.item_ids and not any(item in disposition.text for item in disposition.item_ids):
                prefix = ",".join(disposition.item_ids) + " "
            parts.append(f"[{disposition.source}] {prefix}{disposition.text}")
        return "<br>".join(parts)


def _curriculum_for(topics: list[TopicNode], index: PackIndex,
                    curriculum: Curriculum) -> tuple[Counter[str], list[tuple[str, str]], int]:
    if not topics:
        return Counter(), [], 0
    subject = topics[0].subject
    points = index.points_for(topics)
    rows = curriculum.targets_for_points(subject, points)
    status = Counter(row.status for row in rows)
    targets = sorted({(row.target, row.status) for row in rows if row.target})
    return status, targets, len(rows)


def join_rows(rows: list[OfficialRow], index: PackIndex, final_table: FinalTable,
              dispositions: list[Disposition], curriculum: Curriculum) -> list[LedgerRow]:
    occurrences: Counter[tuple[str, str, str]] = Counter()
    ledger: list[LedgerRow] = []
    for row in rows:
        key = (row.volume, row.chapter, row.section)
        occurrence = occurrences[key]
        occurrences[key] += 1
        final_cell = final_table.get(row.volume, row.chapter, row.section, occurrence)
        landing, source = match_official_row(row, index, final_cell.status if final_cell else None)
        is_section = not is_non_section_row(row)
        status, targets, total = _curriculum_for(landing, index, curriculum) if is_section else (Counter(), [], 0)
        ledger.append(LedgerRow(
            official=row, is_section=is_section, landing=landing, landing_source=source,
            material_count=index.material_count(landing), curriculum_status=status,
            curriculum_targets=targets, curriculum_total=total,
            final_found=final_cell is not None,
        ))
    _assign_dispositions(ledger, dispositions)
    return ledger


def _row_names(entry: LedgerRow) -> set[str]:
    names = {normalize_name(entry.official.section), normalize_name(entry.official.chapter)}
    for topic in entry.landing:
        names.add(normalize_name(topic.name))
        names.add(normalize_name(topic.slug_last))
        names.add(normalize_name(topic.slug))
        names.add(normalize_name(topic.chapter_name or ""))
    return {n for n in names if n}


def _topic_tokens(text: str) -> list[str]:
    """从 §4.3 的位置格抽主题名：W-7 指认「X」、反引号路径末段、最后一个 · 段。"""
    tokens: list[str] = []
    for m in re.finditer(r"W-7\s*指认「([^」]+)」", text or ""):
        tokens.append(m.group(1))
    for m in re.finditer(r"`([^`]+)`", text or ""):
        tokens.append(_last_path_segment(m.group(1)))
    if "·" in (text or ""):
        tokens.append(_last_path_segment(text))
    out: list[str] = []
    for token in tokens:
        token = normalize_name(token)
        if token and token not in out and token not in ("官方无此节", "未计缺失"):
            out.append(token)
    return out


def _disposition_matches(entry: LedgerRow, disposition: Disposition) -> bool:
    row = entry.official
    if disposition.kind in ("missing", "misplaced"):
        if disposition.volume and disposition.volume != row.volume:
            return False
        if disposition.section in (None, "", "（官方无此节）"):
            if disposition.topic:
                row_names = " ".join(_row_names(entry))
                return any(token in row_names for token in _topic_tokens(disposition.topic))
            return not entry.is_section
        return normalize_name(disposition.section) == normalize_name(row.section)
    if disposition.kind == "pseudo":
        row_names = " ".join(_row_names(entry))
        tokens = [normalize_name(x) for x in (disposition.topic or "").split("、") if x]
        return any(token and token in row_names for token in tokens)
    if disposition.kind == "free":
        target = disposition.section
        if not target:
            return False
        target_key = normalize_name(target)
        section_key = normalize_name(row.section)
        chapter_key = normalize_name(row.chapter)
        return bool(target_key and (
            target_key == section_key
            or (len(section_key) >= 3 and target_key in section_key)
            or target_key == chapter_key))
    if disposition.kind == "chapter":
        return disposition.volume == row.volume and entry.is_section
    return False


def _assign_dispositions(ledger: list[LedgerRow], dispositions: list[Disposition]) -> None:
    if not ledger:
        return
    first_section: dict[tuple[str, str], LedgerRow] = {}
    first_volume: dict[str, LedgerRow] = {}
    for entry in ledger:
        if not entry.is_section:
            continue
        key = (entry.official.subject, entry.official.volume)
        first_section.setdefault(key, entry)
        first_volume.setdefault(entry.official.volume, entry)

    referenced = {item for d in dispositions if d.kind != "item" and d.kind != "out_of_table"
                  for item in d.item_ids}
    for disposition in dispositions:
        if disposition.kind == "chapter":
            target = first_volume.get(disposition.volume or "")
            if target is not None:
                target.dispositions.append(disposition)
            continue
        if disposition.kind in ("item", "out_of_table"):
            # 表外项只在点名册时挂到该册首行；学科汇总/无册项进附录。
            if any(item in referenced for item in disposition.item_ids):
                continue
            if disposition.volume:
                target = first_volume.get(disposition.volume)
                if target is not None:
                    target.dispositions.append(disposition)
            continue
        for entry in ledger:
            if _disposition_matches(entry, disposition):
                entry.dispositions.append(disposition)

    # 行级匹配失败的 §4 条目：按册挂到该册首行（原文不改，来源仍标 §4.x），
    # 避免 340 行台账里出现“已解析但不可见”的处置。
    attached = {item for entry in ledger for d in entry.dispositions for item in d.item_ids}
    for disposition in dispositions:
        if disposition.kind in ("misplaced", "missing", "pseudo", "free") \
                and not any(item in attached for item in disposition.item_ids):
            target = None
            if disposition.kind == "misplaced" and disposition.section == "（官方无此节）":
                target = next((e for e in ledger if not e.is_section
                               and e.official.volume == disposition.volume), None)
            if target is None and disposition.volume:
                target = first_volume.get(disposition.volume)
            if target is None and disposition.kind == "free":
                for entry in ledger:
                    if not entry.is_section and entry.landing and normalize_name(
                            disposition.topic or "") in " ".join(_row_names(entry)):
                        target = entry
                        break
            if target is not None:
                target.dispositions.append(disposition)


# ---------------------------------------------------------------------------
# 自检 / 渲染


@dataclass
class Check:
    name: str
    expected: object
    actual: object
    hard: bool = True

    @property
    def ok(self) -> bool:
        return self.expected == self.actual


@dataclass
class CheckReport:
    checks: list[Check]
    errors: list[str]
    notes: list[str]

    @property
    def ok(self) -> bool:
        return not self.errors


def self_check(ledger: list[LedgerRow], index: PackIndex, curriculum: Curriculum,
               docs_dir: Path) -> CheckReport:
    sections = [e for e in ledger if e.is_section]
    nonsec = [e for e in ledger if not e.is_section]
    checks: list[Check] = []
    errors: list[str] = []
    notes: list[str] = []

    def check(name: str, expected: object, actual: object, hard: bool = True) -> None:
        item = Check(name, expected, actual, hard)
        checks.append(item)
        if hard and not item.ok:
            errors.append(f"{name}: 期望 {expected}，实得 {actual}")

    check("官方节行数", TOTAL_OFFICIAL_SECTIONS, len(sections))
    check("非节行数", TOTAL_NONSECTIONS, len(nonsec))
    check("总行数", TOTAL_OFFICIAL_SECTIONS + TOTAL_NONSECTIONS, len(ledger))
    for subject, expected in OFFICIAL_SECTION_COUNTS.items():
        check(f"官方节行数/{SUBJECT_CN[subject]}", expected,
              sum(1 for e in sections if e.official.subject == subject))
    for subject, expected in OFFICIAL_NONSECTION_COUNTS.items():
        check(f"非节行数/{SUBJECT_CN[subject]}", expected,
              sum(1 for e in nonsec if e.official.subject == subject))
    keys = [(e.official.volume, e.official.chapter, e.official.section) for e in sections]
    check("官方节键唯一", len(keys), len(set(keys)))
    check("FINAL §3 行覆盖缺失", 0, sum(1 for e in ledger if not e.final_found))
    check("课标条目行数", 661, len(curriculum.rows))
    check("落点缺失行数", 0, sum(1 for e in sections if not e.landing))
    check("官方章数", 90, len({(e.official.volume, e.official.chapter) for e in sections}))
    check("官方册数", 21, len({e.official.volume for e in sections}))
    check("材料总数为正", True, index.total_materials > 0)
    check("材料孤儿绑定", 0, len(index.orphan_materials))
    check("课标 target 悬空", 0, sum(1 for row in curriculum.rows
                                     if row.target and (row.subject, row.target) not in index.point_to_topic))

    standard_path = Path(docs_dir) / "reference" / "standard_chapter_names.json"
    if standard_path.exists():
        standard = json.loads(standard_path.read_text(encoding="utf-8"))
        check("标准章表册数", 21, len(standard))
        check("标准章表章数", 90, sum(len(v) for v in standard.values()))
        volume_order = [v for s in ("CHEMISTRY", "BIOLOGY", "PHYSICS", "MATH")
                        for v in VOLUME_NAMES[s]]
        present = {e.official.volume for e in sections}
        for volume in [v for v in volume_order if v in present]:
            key = volume if volume in standard else volume.replace("生物学", "生物")
            if key in standard:
                check(f"章数/{volume}", len(standard[key]),
                      len({e.official.chapter for e in sections if e.official.volume == volume}))
    else:
        notes.append(f"标准章表缺失：{standard_path}")

    structure_path = Path(docs_dir) / "reference" / "chapter_structure_full.json"
    if structure_path.exists():
        structure = json.loads(structure_path.read_text(encoding="utf-8"))
        total = sum(len(c.get("secs") or []) for chapters in structure.values() for c in chapters)
        fallback = sum(len(c.get("secs") or []) for c in structure.get("物理其余", []))
        chapters = sum(len(v) for v in structure.values())
        check("章结构键数", 22, len(structure))
        check("章结构章数", 91, chapters)
        check("章结构节数", 471, total)
        check("章结构去物理其余兜底节数", 344, total - fallback, hard=False)
        notes.append(f"章结构基准：{len(structure)} 键 / {chapters} 章 / {total} 节（含「物理其余」{fallback} 节兜底）；"
                     f"去兜底 {total - fallback} 节，与官方 {TOTAL_OFFICIAL_SECTIONS} 节的差异为已知基准偏差。")
    else:
        notes.append(f"章结构基准缺失：{structure_path}")

    return CheckReport(checks=checks, errors=errors, notes=notes)


def _md_cell(value: object) -> str:
    text = str(value).replace("|", "\\|").replace("\r", " ").replace("\n", "<br>")
    return text.strip()


def _render_landing(entry: LedgerRow, index: PackIndex) -> str:
    if not entry.landing:
        return "—（无落点）"
    parts: list[str] = []
    for topic in entry.landing:
        parts.append(f"`{topic.slug}`（材料 {len(index.subtree_materials.get(topic.slug, set()))}）")
    suffix = ""
    if len(entry.landing) > 1:
        suffix = f"；合计材料 {entry.material_count}"
    return "；".join(parts) + suffix + f"〔{SOURCE_LABEL.get(entry.landing_source, entry.landing_source)}〕"


def _render_curriculum(entry: LedgerRow) -> str:
    if not entry.is_section:
        return "—（非节行）"
    if not entry.landing:
        return "—（无落点）"
    if entry.curriculum_total == 0:
        return "0 条（落点子树无课标条目映射）"
    order = ("COVERED", "PARTIAL", "NOT_COVERED", "UNDECIDED")
    counts = " / ".join(f"{status} {entry.curriculum_status.get(status, 0)}" for status in order)
    shown = entry.curriculum_targets[:8]
    targets = "、".join(f"{target}（{status}）" for target, status in shown)
    more = len(entry.curriculum_targets) - len(shown)
    if more > 0:
        targets += f"…（+{more}）"
    return f"{entry.curriculum_total} 条：{counts}；target: {targets or '—'}"


def render_ledger(ledger: list[LedgerRow], index: PackIndex, curriculum: Curriculum,
                  docs_dir: Path, all_dispositions: list[Disposition] | None = None,
                  pack_path: Path | None = None) -> str:
    sections = [e for e in ledger if e.is_section]
    nonsec = [e for e in ledger if not e.is_section]
    report = self_check(ledger, index, curriculum, docs_dir)
    subject_counts = Counter(e.official.subject for e in sections)
    nonsec_counts = Counter(e.official.subject for e in nonsec)
    landing_sources = Counter(e.landing_source for e in sections)

    lines: list[str] = []
    lines.append("# 教材·课标·现包 逐节终验台账（340 官方节 + 20 非节）")
    lines.append("")
    lines.append(f"> 生成日期：{date.today().isoformat()}｜只读 join：不改包、不改表、不写 staging。")
    lines.append("")
    lines.append("## 1. 口径与自检")
    lines.append("")
    lines.append(f"- **官方节表**：四份 W-7 报告逐册表共 **{len(sections)} 官方节** + **{len(nonsec)} 非节行** "
                 f"= {len(ledger)} 行；分科 {dict(subject_counts)}；非节分科 {dict(nonsec_counts)}。")
    four_subject = "、".join(
        f"{SUBJECT_CN[s]} {subject_counts.get(s, 0)}/{nonsec_counts.get(s, 0)}"
        for s in ("CHEMISTRY", "BIOLOGY", "PHYSICS", "MATH"))
    lines.append(f"- **四科分布（官方节/非节）**：{four_subject}。")
    status_total = Counter(row.status for row in curriculum.rows)
    lines.append(f"- **课标条目**：`curriculum_alignment.csv` 共 {len(curriculum.rows)} 行"
                 f"（COVERED {status_total.get('COVERED', 0)} / PARTIAL {status_total.get('PARTIAL', 0)} / "
                 f"NOT_COVERED {status_total.get('NOT_COVERED', 0)} / UNDECIDED {status_total.get('UNDECIDED', 0)}），"
                 f"其中有 target 的 {sum(1 for r in curriculum.rows if r.target)} 行。"
                 "按 target 原子点 join 到落点子树，按 `(subject, candidate_slug)` 去重后逐节展示；"
                 "同一候选可出现在多个节行。")
    lines.append(f"- **现包材料分科**：" + "、".join(
        f"{SUBJECT_CN[s]} {index.subject_material_counts.get(s, 0):,}"
        for s in ("CHEMISTRY", "BIOLOGY", "PHYSICS", "MATH")) + "。")
    pack_display = Path(pack_path or DEFAULT_PACK)
    try:
        pack_display = pack_display.resolve().relative_to(REPO).as_posix()
    except ValueError:
        pass
    version, content_version = _pack_identity(pack_path or DEFAULT_PACK)
    lines.append(f"- **现包**：`{pack_display}`（v{version}，contentVersion {content_version}，sha256 "
                 f"{_sha256(pack_path or DEFAULT_PACK)[:16]}…，"
                 f"{len(index.topics)} 主题 / {len(index.point_to_topic):,} 知识点）；"
                 f"sidecar 材料 **{index.total_materials:,}** 条。")
    lines.append("- **落点四级**：官方节同名主题（名或 slug 末段）→ 同名/含节名点所在主题 → "
                 "FINAL §3「现包现状」指认覆盖主题 → 官方章容器；同级多落点全部列出。")
    lines.append("- **材料数**：落点子树（含后代主题）内知识点所绑定的去重材料数。")
    lines.append("- **处置注**：只取 `FINAL-COVERAGE-TABLE.md` §4 与 §11 原文（批11 F2 残差处置，逐条保留 "
                 "item_id 与 disposition）；批1–10 的处置已折入 §3 现包现状基线，批12/13 无逐节处置原文入 FINAL，"
                 f"故不补写，v{version} 实测体现在“现包落点”列。")
    lines.append("")
    lines.append(f"**自检：{len(sections)} 官方节 + {len(nonsec)} 非节 = {len(ledger)}**"
                 f"（期望 {TOTAL_OFFICIAL_SECTIONS} + {TOTAL_NONSECTIONS} = "
                 f"{TOTAL_OFFICIAL_SECTIONS + TOTAL_NONSECTIONS}）")
    lines.append("")
    lines.append("| 自检项 | 期望 | 实得 | 结果 |")
    lines.append("|---|---|---|---|")
    for item in report.checks:
        result = "✅" if (item.ok and item.hard) else ("❌" if item.hard else "记录")
        lines.append(f"| {_md_cell(item.name)} | {_md_cell(item.expected)} | {_md_cell(item.actual)} | {result} |")
    for note in report.notes:
        lines.append(f"- {note}")
    if report.errors:
        lines.append("")
        lines.append("**自检失败项：**")
        for error in report.errors:
            lines.append(f"- {error}")
    lines.append("")
    lines.append(f"落点来源分布：{dict(landing_sources)}。")
    lines.append("")

    lines.append("## 2. 340 官方节逐节台账")
    lines.append("")
    lines.append("| # | 学科 | 册 | 官方章 | 官方节 | 现包落点（材料数） | 课标条目（status → target） | 处置注（FINAL §4/§11） |")
    lines.append("|---|---|---|---|---|---|---|---|")
    for number, entry in enumerate(sections, 1):
        row = entry.official
        lines.append("| " + " | ".join(_md_cell(x) for x in (
            number, SUBJECT_CN.get(row.subject, row.subject), row.volume, row.chapter,
            row.section, _render_landing(entry, index), _render_curriculum(entry),
            entry.disposition_text,
        )) + " |")
    lines.append("")

    lines.append("## 3. 20 行非节单列")
    lines.append("")
    lines.append("| # | 学科 | 册 | 官方章 | 原行标签 | 现包落点（材料数） | 处置注（FINAL §4/§11） |")
    lines.append("|---|---|---|---|---|---|---|")
    for number, entry in enumerate(nonsec, 1):
        row = entry.official
        lines.append("| " + " | ".join(_md_cell(x) for x in (
            number, SUBJECT_CN.get(row.subject, row.subject), row.volume, row.chapter,
            row.section, _render_landing(entry, index), entry.disposition_text,
        )) + " |")
    lines.append("")

    lines.append("## 4. 附录")
    lines.append("")
    source_dispositions = all_dispositions if all_dispositions is not None else [
        d for entry in ledger for d in entry.dispositions]
    chapter_items = [d for d in source_dispositions if d.kind == "chapter"]
    if chapter_items:
        lines.append("### 4.1 册级章层处置（FINAL §4.2，挂在各册首行）")
        lines.append("")
        lines.append("| 来源 | 册 | 原文 |")
        lines.append("|---|---|---|")
        seen_ch: set[tuple[str, str]] = set()
        for item in chapter_items:
            key = (item.source, item.text)
            if key in seen_ch:
                continue
            seen_ch.add(key)
            lines.append(f"| {item.source} | {_md_cell(item.volume or '')} | {_md_cell(item.text)} |")
        lines.append("")

    free_items = [d for d in source_dispositions if d.kind == "free"]
    if free_items:
        lines.append("### 4.2 游离主题处置（FINAL §4.4 / §11.5）")
        lines.append("")
        lines.append("| 来源 | 主题 | 原文 |")
        lines.append("|---|---|---|")
        seen_free: set[tuple[str, str]] = set()
        for item in free_items:
            key = (item.source, item.text)
            if key in seen_free:
                continue
            seen_free.add(key)
            lines.append(f"| {item.source} | {_md_cell(item.topic or '')} | {_md_cell(item.text)} |")
        lines.append("")

    out_of_table = [d for d in source_dispositions if d.kind == "out_of_table"]
    if out_of_table:
        lines.append("### 4.3 FINAL §11.6 表外项（12 条）")
        lines.append("")
        lines.append("| item_id | 处置 | 原文 |")
        lines.append("|---|---|---|")
        seen_out: set[str] = set()
        for item in out_of_table:
            item_id = item.item_ids[0] if item.item_ids else ""
            if item_id in seen_out:
                continue
            seen_out.add(item_id)
            body = item.text
            if item_id and body.startswith(item_id + " "):
                body = body[len(item_id) + 1:]
            lines.append(f"| {item_id} | {_md_cell(item.disposition or '')} | {_md_cell(body)} |")
        lines.append("")

    lines.append("### 4.4 已知基准差异")
    lines.append("")
    lines.append("- `chapter_structure_full.json`：22 键 / 91 章 / 471 节；含「物理其余」127 节兜底，"
                 "去兜底 344 节。与官方 340 节的差异来自物理 5 节多出、数学 1 节缺失（FINAL §5 已登记），"
                 "本台账的 340 键以四份 W-7 报告为准。")
    lines.append("- `standard_chapter_names.json`：21 册 / 90 章；5 处章名已于 2026-10-20 按 W-7 官方名对齐"
                 "（生物 2：选必1 第一章「人体的内环境与稳态」、选必2 第四章「人与环境」；物理 3），"
                 "本基准件现为 0 处旧名。")
    lines.append("- FINAL 成文于批11；批12/13 无逐节处置原文入表，处置注列不补写。")
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# CLI


def _pack_identity(pack_path: Path) -> tuple[str, str]:
    """当前包版本与内容戳：优先读同目录 manifest，缺失时退回包内字段。"""
    manifest = Path(pack_path).parent / "moe-2025-update-manifest.json"
    if manifest.exists():
        doc = json.loads(manifest.read_text(encoding="utf-8"))
        version = str(doc.get("version") or "?")
        content_version = str(doc.get("contentVersion") or "?")
        return version, content_version
    pack = json.loads(Path(pack_path).read_text(encoding="utf-8"))
    return (str(pack.get("version") or "?"),
            str(pack.get("contentVersion") or "?"))


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--docs", type=Path, default=DOCS)
    parser.add_argument("--pack", type=Path, default=DEFAULT_PACK)
    parser.add_argument("--sidecar", type=Path, action="append", default=None,
                        help="可重复；缺省读同目录 sidecar 索引")
    parser.add_argument("--final", type=Path, default=DEFAULT_FINAL)
    parser.add_argument("--curriculum", type=Path, default=DEFAULT_CURRICULUM)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--write", action="store_true", help="写台账（默认只读推演）")
    args = parser.parse_args(argv)

    rows = parse_reports(args.docs)
    final_table = parse_final_table(args.final)
    dispositions = parse_final_dispositions(args.final)
    curriculum = load_curriculum(args.curriculum)
    sidecars = args.sidecar or default_sidecar_paths(args.pack)
    index = load_pack_index(args.pack, sidecars)
    ledger = join_rows(rows, index, final_table, dispositions, curriculum)
    report = self_check(ledger, index, curriculum, args.docs)
    markdown = render_ledger(ledger, index, curriculum, args.docs, dispositions, args.pack)

    sections = sum(1 for e in ledger if e.is_section)
    nonsec = len(ledger) - sections
    print(f"pack={args.pack} sha256={_sha256(args.pack)[:16]} topics={len(index.topics)} "
          f"points={len(index.point_to_topic)} materials={index.total_materials}")
    print(f"rows={len(ledger)} sections={sections} non_sections={nonsec}")
    print(f"subject_sections={dict(Counter(e.official.subject for e in ledger if e.is_section))}")
    print(f"landing_sources={dict(Counter(e.landing_source for e in ledger if e.is_section))}")
    print(f"curriculum_rows={len(curriculum.rows)} disposition_entries={len(dispositions)}")
    if not report.ok:
        print("SELF-CHECK FAILED")
        for error in report.errors:
            print(f"  - {error}")
        return 1
    print("SELF-CHECK OK")
    if args.write:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(markdown, encoding="utf-8", newline="\n")
        print(f"wrote {args.out} ({len(markdown)} chars)")
    else:
        print("dry-run: use --write to write the ledger")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
