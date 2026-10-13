# -*- coding: utf-8 -*-
"""材料侧 AI/provenance 三键（F8·③）：侧车 schema 4 的唯一口径 + 单点注入口。

## 它消灭的失败

侧车里的材料正文由模型判定/改写通道产出（derivationKind=REVIEWED_SYNTHESIS），但包内
没有任何"这段正文是 AI 生成合成内容"的机器可读痕迹——文件被原样带走（复制/备份/导出）
后无法分辨来源，也点不出服务提供者与内容编号。F8 裁定把《人工智能生成合成内容标识办法》
第 5 条要求的元数据三要素落成材料的三个键，随侧车（= 材料的文件载体）走。

第二个失败是**漂移**：v4 起 Kotlin codec 把材料键集钉死（多一键/少一键/取值越界都是
整包拒 → 装机横幅常驻），而写入侧有多处（判定通道 materialize、作者型材料
apply_authored_materials、一次性升级注入器、Python 契约镜像）。若各自手抄键名与取值，
任何一处迟早与 codec 对不上，且对不上时 promote 未必红。本模块是那三键的唯一权威：
键名、词表、派生口径、整卷升级、以及"v4 卷必须自洽"的自检，四个调用点共用一份。

## 键与派生口径（机械可派生真值，不从自由文本猜）

| 键 | 类型 | 取值 | 依据 |
|---|---|---|---|
| `contentAttribute` | string（闭词表） | `AI_SYNTHESIZED` | 第 5 条「生成合成内容属性信息」；包内材料全部是模型判定/改写正文（`REVIEWED_SYNTHESIS`），人工撰写的材料需要新词表值 + 再 bump schemaVersion，而不是猜 |
| `contentProvider` | string（trim 后非空） | 未上架前 = 该卷 `packId` | 第 5 条「服务提供者名称或者编码」；上架前没有登记好的提供者编码，用**包标识**是此刻唯一的真值，上架前核定后整族替换（键集不变，不 bump 版本，见契约「边界登记」） |
| `contentId` | string | **= 材料 `slug`** | 第 5 条「内容编号」；slug 是材料在包内唯一的稳定编号（codec 由它派生 `materialId`/`stableCode`），取它才可机械复核 |

`contentId` 取 slug 使 codec 能**交叉核对**（键全在但取值说谎的包照样被拒），这是"机械
可派生真值"的落点：三键的取值全部由既有字段推出，不引入任何新的作者输入。

## 与 schemaVersion 的关系（3 → 4）

- v≤3：材料键集恰好 13 枚，三键**一个都不许出现**（出现即未知键 → 整包拒，与 D-0
  三元组的版本门同法）。旧读者读 v4 会整包拒（App 未发布故无现实风险）。
- v4：材料**必含**三键（不是可选三元组；D-0 的 v3 裁定三元组是「可选 all-or-none」，
  本组是「必填组」——因为法规要求的是"文件必须带"，不是"允许带"）。
- 版本只在**落盘那一刻**由写入侧统一升位（见 `ensure_doc`）。

契约：`docs/research/2026-10-13-f8-material-provenance-contract.md`。
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Sequence

from kb_build import pack_io

# 侧车内容格式版本：v4 = 材料必含 AI/provenance 三键（与 Kotlin codec 的
# CURRENT_SCHEMA_VERSION 同值；两边不一致由 Python 契约镜像用例抓）。
SCHEMA_VERSION = 4

# v4 材料**必填**组（键序即落盘键序：插在 trailing `bindings` 之前）。
PROVENANCE_KEYS: tuple[str, ...] = ("contentAttribute", "contentProvider", "contentId")

# 属性词表（第 5 条「生成合成内容属性信息」）。当前只有一个机械为真的取值：
# 包内材料正文全部经模型判定/改写。要加 HUMAN_* 之类的值 = 扩词表 → 必须再 bump
# schemaVersion 并同步 Kotlin 枚举（`KnowledgeMaterialContentAttribute`）。
ATTRIBUTE_AI_SYNTHESIZED = "AI_SYNTHESIZED"
ATTRIBUTES: frozenset[str] = frozenset({ATTRIBUTE_AI_SYNTHESIZED})

# 2020 历史样例族的侧车（不在 `moe-2025-teaching-support-v2-index.json` 里，
# 由 `BundledKnowledgePackResources.teachingSidecarsByPack` 直挂）。镜像这一条映射：
# 索引一致性检查与注入器都要覆盖它，否则该族材料永远不会被升级。
LEGACY_SIDECAR_NAMES: tuple[str, ...] = ("moe-2020-teaching-support-v1.json",)

# v1 来源条目缺的四枚政策键（v2+ 才有）。v1→v4 升级时按下面的机械规则补：
#   - contentUsePolicy：REFERENCE_ONLY 的许可形态下，Kotlin 的
#     `KnowledgeBaseImportContract.validateSourceReuseRights` **只允许**
#     REVIEWED_SYNTHESIS_ONLY —— 不是选择，是契约里的唯一合法值；
#   - licenseExpression / licenseUri / attributionText：v1 条目没有授权表达、授权
#     URI 与归属文案，取 null（不编造文案），且 null 是契约允许的形态
#     （非直接复用分支只校验"给了就合法"，不要求必给）。
# 许可状态不是 REFERENCE_ONLY 的 v1 来源一律拒（推不出真值就不写）。
_LEGACY_SOURCE_POLICY_KEYS: tuple[str, ...] = (
    "contentUsePolicy", "licenseExpression", "licenseUri", "attributionText")
_LEGACY_MATERIAL_SOURCE_POLICY = "REVIEWED_SYNTHESIS_ONLY"


class MaterialProvenanceError(Exception):
    """注入/升级路径上的硬失败（半写的三键、推不出真值的 v1 来源、卷不自洽）。"""


def provenance_fields(pack_id: str, slug: str) -> dict[str, str]:
    """三键的机械派生值（键序 = `PROVENANCE_KEYS`）。"""
    if not pack_id or not pack_id.strip():
        raise MaterialProvenanceError("packId 为空，推不出 contentProvider")
    if not slug or not slug.strip():
        raise MaterialProvenanceError("slug 为空，推不出 contentId")
    return {
        "contentAttribute": ATTRIBUTE_AI_SYNTHESIZED,
        "contentProvider": pack_id,
        "contentId": slug,
    }


def sidecar_material_keys(base_keys: Sequence[str]) -> tuple[str, ...]:
    """作者键序 → 侧车落盘键序：三键插在 trailing `bindings` 之前。

    写入侧照此顺序构造材料对象（`materialize` / `apply_authored_materials`），
    `new_content.MATERIAL_SIDECAR_KEYS` 由它派生——"作者 13 键"与"侧车 16 键"
    两处键集不许各写一份。
    """
    keys = list(base_keys)
    if not keys or keys[-1] != "bindings":
        raise MaterialProvenanceError("材料键序必须以 bindings 结尾（F8 三键插在它之前）")
    keys[-1:-1] = list(PROVENANCE_KEYS)
    return tuple(keys)


def material_problems(material: dict, *, require: bool) -> list[str]:
    """单条材料的三键检查。`require=True` 时缺失也是问题（v4 口径）。"""
    problems: list[str] = []
    slug = material.get("slug") or ""
    present = [k for k in PROVENANCE_KEYS if k in material]
    if not present:
        return [f"材料 {slug} 缺 F8 三键（v4 必含）"] if require else []
    if len(present) != len(PROVENANCE_KEYS):
        missing = [k for k in PROVENANCE_KEYS if k not in material]
        return [f"材料 {slug} 的 F8 三键残缺（缺 {missing}）——v4 是三键必填组，半写即拒"]
    attribute = material["contentAttribute"]
    if not isinstance(attribute, str) or attribute not in ATTRIBUTES:
        problems.append(f"材料 {slug} 的 contentAttribute 越界：{attribute!r}")
    provider = material["contentProvider"]
    if not isinstance(provider, str) or not provider.strip() or provider != provider.strip():
        problems.append(f"材料 {slug} 的 contentProvider 不是 trim 后非空字符串：{provider!r}")
    content_id = material["contentId"]
    if not isinstance(content_id, str) or content_id != slug:
        problems.append(f"材料 {slug} 的 contentId 必须等于材料 slug，实际 {content_id!r}")
    return problems


def doc_problems(doc: dict, *, name: str = "?", base_keys: Sequence[str] = ()) -> list[str]:
    """单卷自洽检查（= codec 在材料层面的下限）。

    v≤3：材料键集恰好 `base_keys`（三键一个都不许出现——出现即未知键，整包拒）；
    v4：键集恰好 `base_keys + 三键`，且三键取值合法（含 `contentId == slug`）。
    """
    problems: list[str] = []
    version = doc.get("schemaVersion")
    materials = doc.get("materials") or []
    if version not in (1, 2, 3, SCHEMA_VERSION):
        problems.append(f"{name}: schemaVersion {version!r} 不在 {{1,2,3,{SCHEMA_VERSION}}}")
        return problems
    expected = set(base_keys) | (set(PROVENANCE_KEYS) if version == SCHEMA_VERSION else set())
    for material in materials:
        slug = material.get("slug") or "?"
        if base_keys:
            extra = sorted(set(material) - expected)
            missing = sorted(expected - set(material))
            if extra or missing:
                problems.append(
                    f"{name}: 材料 {slug} 键集与 v{version} 口径不符"
                    f"（多 {extra}、缺 {missing}）——codec 整包拒")
                continue
        if version == SCHEMA_VERSION:
            for problem in material_problems(material, require=True):
                problems.append(f"{name}: {problem}")
    return problems


def index_problems(index_doc: dict, work_dir: Path) -> list[str]:
    """索引 ↔ 磁盘一致性（Kotlin loader 按索引加载，索引说一套、磁盘放另一套就是装不上）。"""
    problems: list[str] = []
    listed = [rel.rsplit("/", 1)[-1] for rel in index_doc.get("sidecars") or []]
    if not listed:
        problems.append("侧车索引没有任何 sidecar")
    for name in listed:
        if not (work_dir / name).exists():
            problems.append(f"索引列出的 sidecar 不在磁盘上：{name}")
    dirs = {p.name for p in work_dir.glob("moe-2025-teaching-support-v2-*.json")}
    for orphan in sorted(dirs - set(listed) - {"moe-2025-teaching-support-v2-index.json"}):
        problems.append(f"磁盘上的 sidecar 未被索引收录：{orphan}")
    return problems


def family_names(work_dir: Path) -> list[str]:
    """材料侧车族的卷名：索引列出的（2025 族）+ 直挂的历史样例卷。

    历史样例卷不在索引里（Kotlin `teachingSidecarsByPack` 直挂，Python 侧没有第二处
    镜像），但它同样进 App、同样过 codec——漏了它就漏了一族材料。索引缺失时按文件名
    glob 兜底（与 `pack_io.sidecar_paths` 同一口径），并由调用方报"缺索引"。
    """
    import re
    names: list[str] = []
    index_path = work_dir / pack_io.SIDECAR_INDEX_NAME
    if index_path.exists():
        for rel in pack_io.load_json(index_path).get("sidecars") or []:
            names.append(rel.rsplit("/", 1)[-1])
    else:
        pat = re.compile(r"moe-2025-teaching-support-v2-\d{2}\.json$")
        names.extend(sorted(p.name for p in work_dir.glob("moe-2025-teaching-support-v2-*.json")
                            if pat.search(p.name)))
    for name in LEGACY_SIDECAR_NAMES:
        if (work_dir / name).exists():
            names.append(name)
    seen: set[str] = set()
    return [n for n in names if not (n in seen or seen.add(n))]


def family_problems(work_dir: Path, base_keys: Sequence[str]) -> list[str]:
    """整族下限检查（promote 镜像用）：索引一致 + 每卷自洽。

    与 codec 同宽，不比它更严：v2/v3 卷**不**因"没升 v4"变红（codec 照收，装机不受影响），
    v4 卷则必须键齐值正。"全族升 v4"是 F8 的交付态（由注入器写后复算证明），不是常设门。
    """
    problems: list[str] = []
    index_path = work_dir / pack_io.SIDECAR_INDEX_NAME
    if not index_path.exists():
        problems.append("缺侧车索引（Kotlin loader 按索引加载，无索引 = 装不上）")
    else:
        problems.extend(index_problems(pack_io.load_json(index_path), work_dir))
    for name in family_names(work_dir):
        path = work_dir / name
        if not path.exists():
            continue
        problems.extend(doc_problems(pack_io.load_json(path), name=name, base_keys=base_keys))
    return problems


def ensure_doc(doc: dict, *, source: str = "?") -> dict[str, Any]:
    """把一卷升到 v4 并保证自洽（单点注入；幂等：已合规的卷零改动）。

    只**补缺**、不**改写**：材料已有三键就保留原值（上架前核定后 provider 会被整族
    替换，这里不能把它按 packId 覆写回去），只在取值不合法时拒。半写的三键（缺一/缺二）
    一律拒——那是"写坏了的卷"，不是"待升级的卷"。

    返回 {"version_bumped", "materials_upgraded", "sources_upgraded", "changed"}。
    """
    stats = {"version_bumped": False, "materials_upgraded": 0, "sources_upgraded": 0,
             "changed": False}
    version = doc.get("schemaVersion")
    pack_id = doc.get("packId")
    if not isinstance(pack_id, str) or not pack_id.strip():
        raise MaterialProvenanceError(f"{source}: 卷缺 packId，contentProvider 推不出来")
    if version == 1:
        _upgrade_legacy_sources(doc, source)
        stats["sources_upgraded"] = len(doc.get("sources") or [])
    elif version not in (2, 3, SCHEMA_VERSION):
        raise MaterialProvenanceError(
            f"{source}: schemaVersion {version!r} 不在 {{1,2,3,{SCHEMA_VERSION}}}，不猜")
    for material in doc.get("materials") or []:
        slug = material.get("slug")
        if not isinstance(slug, str) or not slug.strip():
            raise MaterialProvenanceError(f"{source}: 材料缺 slug，contentId 推不出来")
        present = [k for k in PROVENANCE_KEYS if k in material]
        if present and len(present) != len(PROVENANCE_KEYS):
            raise MaterialProvenanceError(
                f"{source}: 材料 {slug} 的 F8 三键残缺（有 {present}）——半写即拒，不替它补")
        if present:
            problems = material_problems(material, require=True)
            if problems:
                raise MaterialProvenanceError(f"{source}: " + "；".join(problems))
            continue
        injected = provenance_fields(pack_id, slug)
        for key in PROVENANCE_KEYS:  # 键序 = 落盘键序：三键插在 bindings 之前
            material[key] = injected[key]
        # 重新排键：把三键放在 trailing bindings 之前（dict 保序；插值留在尾部会与
        # 写入侧新建材料的键序不一致 → 同一卷里两种键序，diff 噪音且难以复核）
        if "bindings" in material:
            material["bindings"] = material.pop("bindings")
        stats["materials_upgraded"] += 1
        stats["changed"] = True
    if version != SCHEMA_VERSION:
        doc["schemaVersion"] = SCHEMA_VERSION
        stats["version_bumped"] = True
        stats["changed"] = True
    return stats


def _upgrade_legacy_sources(doc: dict, source: str) -> None:
    """v1 → v4 的来源条目升级（v4 的 codec 走 v2+ 键集：必须带四枚政策键）。"""
    for entry in doc.get("sources") or []:
        for key in _LEGACY_SOURCE_POLICY_KEYS:
            if key in entry:
                raise MaterialProvenanceError(
                    f"{source}: v1 来源 {entry.get('sourceId')!r} 已含 {key!r}，形态混叠，不猜")
        if entry.get("licenseStatus") != "REFERENCE_ONLY":
            raise MaterialProvenanceError(
                f"{source}: v1 来源 {entry.get('sourceId')!r} 的 licenseStatus="
                f"{entry.get('licenseStatus')!r} 推不出 contentUsePolicy，需人工裁定")
        entry["contentUsePolicy"] = _LEGACY_MATERIAL_SOURCE_POLICY
        entry["licenseExpression"] = None
        entry["licenseUri"] = None
        entry["attributionText"] = None
