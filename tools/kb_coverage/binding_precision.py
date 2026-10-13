# -*- coding: utf-8 -*-
"""绑定精确率统计器：读判定件 + manifest，出每层 Wilson 95% 区间与**加权 Σ 精确率**。

## 它消灭的失败

1. **单层精确率冒充整体**：三层绑定量极不均（超配层 93%、薄料层 0.6%、普通层 6%），
   任何一层的样本率都不能代表整体。本工具按 manifest 里的层规模加权（权重 = 层规模/总规模），
   并把权重来源（层规模、总规模）打进输出，让"加权"可被逐项复核。
2. **点估计没有不确定度**：200 条样本的比例，5 个百分点的差别可能全在噪声里。
   本工具对每层出 **Wilson 95% 区间**（小样本/极端比例下比 Wald 稳），
   加权值另给分层估计量的近似区间（含抽样占比大的有限总体校正对照）。
3. **判定件与抽样对象对不上**：判定员漏判、重判、抄错层名都会让样本率失真却看不出来。
   本工具把判定件与 `manifest.sampling`/`kits.list` 里的**应有清单**逐条核对：
   数量不符、层名未知、`correct` 取值非法、`material_slug` 重复或不在样本内——一律报错退出，
   不产出任何数字。

## 判定件表头（逐字）

    material_slug,layer,correct,evidence,note        # correct ∈ YES/NO

`layer` 用 manifest 的层名（`overfull`/`thin`/`normal`，也接受中文 `超配层`/`薄料层`/`普通层`）。

## 用法

    python tools/kb_coverage/binding_precision.py --verdicts build/agent-batch9/d1/precision/verdicts.csv
    python tools/kb_coverage/binding_precision.py --verdicts-dir build/agent-batch9/d1/precision/verdicts
    python tools/kb_coverage/binding_precision.py --verdicts ... --json-out build/.../precision_report.json
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from kb_build import pack_io  # noqa: E402

REPO: Path = pack_io.REPO

Z95 = 1.959963984540054  # 正态 95% 分位（Φ^{-1}(0.975)）
EXPECTED_HEADER = ("material_slug", "layer", "correct", "evidence", "note")
LAYER_ALIASES = {
    "overfull": "overfull", "超配层": "overfull",
    "thin": "thin", "薄料层": "thin",
    "normal": "normal", "普通层": "normal",
}
TRUE_TOKENS = {"YES", "Y", "TRUE", "T", "1", "对", "是"}
FALSE_TOKENS = {"NO", "N", "FALSE", "F", "0", "错", "否"}


def resolve_repo_path(p: Path) -> Path:
    resolved = p.resolve()
    if not resolved.is_relative_to(REPO):
        raise SystemExit(f"路径逃出仓库根：{resolved}")
    return resolved


def wilson_interval(k: int, n: int, z: float = Z95) -> tuple[float, float, float]:
    """Wilson 得分区间。返回 (点估计, 下界, 上界)；n == 0 时返回 (nan, 0.0, 1.0)。"""
    if n <= 0:
        return float("nan"), 0.0, 1.0
    p = k / n
    denom = 1.0 + z * z / n
    center = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return p, max(0.0, center - half), min(1.0, center + half)


def stratified_interval(
    parts: list[tuple[float, int, int, int]], z: float = Z95
) -> tuple[float, float, float]:
    """分层比例估计量的近似区间（Wald 型，附有限总体校正 FPC）。

    parts: [(weight, k, n, population)]，weight 之和应为 1。
    返回 (点估计, 无校正下界, 无校正上界, FPC 下界, FPC 上界)。
    样本是层内"每科等距"抽的，不是简单随机，故此区间是**近似**（已在报告里写明）。
    """
    point = 0.0
    var = 0.0
    var_fpc = 0.0
    for w, k, n, pop in parts:
        p = (k / n) if n else 0.0
        point += w * p
        if n > 1:
            v = p * (1 - p) / n
            var += (w * w) * v
            # 抽样占比大时 FPC 显著收窄（薄料层抽了 60% 的层规模）
            fpc = max(0.0, (pop - n) / (pop - 1)) if pop > 1 else 1.0
            var_fpc += (w * w) * v * fpc
    half = z * math.sqrt(var)
    half_fpc = z * math.sqrt(var_fpc)
    return (point, max(0.0, point - half), min(1.0, point + half),
            max(0.0, point - half_fpc), min(1.0, point + half_fpc))


def load_manifest(path: Path) -> dict[str, Any]:
    doc = json.loads(path.read_text(encoding="utf-8"))
    for key in ("layers", "total_bindings", "sampling", "sample", "kits"):
        if key not in doc:
            raise SystemExit(f"manifest 缺字段 {key}：{path}")
    total = doc["total_bindings"]
    if total <= 0:
        raise SystemExit(f"manifest.total_bindings = {total}，无法加权")
    for layer, st in doc["layers"].items():
        for key in ("size", "weight"):
            if key not in st:
                raise SystemExit(f"manifest.layers[{layer}] 缺 {key}")
    return doc


def expected_from_kits(manifest: dict[str, Any]) -> dict[str, dict[str, str]]:
    """从 kits.list + 件文件取"应有绑定"：层 -> {material_slug: 现绑 node_id}。

    判定件表头里没有 node_id（ask 定的口径），所以配对键只能是 `material_slug`；
    这条成立是因为抽样器**拒绝**了多绑定材料（1 材料 1 绑定，见 sample_binding_precision.py）。
    这里仍返回 node_id，用于在报告里显式核对"判的那条绑定"是哪一条。
    """
    list_path = manifest.get("kits_list")
    if not list_path:
        raise SystemExit("manifest 缺 kits_list")
    lp = resolve_repo_path(REPO / list_path)
    if not lp.exists():
        raise SystemExit(f"kits.list 不存在：{lp}")
    names = [ln.strip() for ln in lp.read_text(encoding="utf-8").splitlines() if ln.strip()]
    out: dict[str, dict[str, str]] = defaultdict(dict)
    for rel in names:
        kp = resolve_repo_path(REPO / rel)
        for entry in json.loads(kp.read_text(encoding="utf-8")):
            node_id = (entry.get("bound_node") or {}).get("node_id") or ""
            slug = entry["material_slug"]
            layer = entry["layer"]
            if slug in out[layer]:
                raise SystemExit(f"样本内 material_slug 重复（层 {layer}）：{slug}"
                                 f"——配对键不唯一，需先裁定")
            out[layer][slug] = node_id
    return out


SELFTEST_PREFIX = "SELFTEST-"


def read_verdicts(
    paths: list[Path], allow_selftest: bool = False
) -> tuple[dict[str, dict[str, str]], list[str]]:
    """读判定件 → 层 -> {material_slug: YES/NO}；返回 (数据, 报错清单)。"""
    data: dict[str, dict[str, str]] = defaultdict(dict)
    errors: list[str] = []
    for path in paths:
        # 自测夹具不能冒充判定件：SELFTEST- 前缀的文件必须显式放行才读
        # （本工具的自测夹具刻意名叫 SELFTEST-verdicts-GOOD.csv，正是为了能被这一条拦住）
        if path.name.startswith(SELFTEST_PREFIX) and not allow_selftest:
            errors.append(f"{path.name}: 名称带 {SELFTEST_PREFIX} 前缀，是自测夹具不是判定件；"
                          f"确实要拿它自测请显式加 --allow-selftest")
            continue
        with path.open(encoding="utf-8", newline="") as fh:
            reader = csv.DictReader(fh)
            header = tuple(reader.fieldnames or ())
            if header != EXPECTED_HEADER:
                errors.append(f"{path.name}: 表头应为 {','.join(EXPECTED_HEADER)}，实为 "
                              f"{','.join(header)}")
                continue
            for lineno, row in enumerate(reader, start=2):
                layer_raw = (row.get("layer") or "").strip()
                layer = LAYER_ALIASES.get(layer_raw)
                slug = (row.get("material_slug") or "").strip()
                token = (row.get("correct") or "").strip().upper()
                if layer is None:
                    errors.append(f"{path.name}:{lineno}: 未知层名 {layer_raw!r}")
                    continue
                if not slug:
                    errors.append(f"{path.name}:{lineno}: material_slug 为空")
                    continue
                if token in TRUE_TOKENS:
                    correct = "YES"
                elif token in FALSE_TOKENS:
                    correct = "NO"
                else:
                    errors.append(f"{path.name}:{lineno}: correct={token!r} 不是 YES/NO")
                    continue
                if slug in data[layer]:
                    errors.append(f"{path.name}:{lineno}: {layer}/{slug} 重复判定")
                    continue
                data[layer][slug] = correct
    return data, errors


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--verdicts", type=Path, nargs="*", default=[],
                    help="判定件 CSV（可多份；表头必须逐字一致）")
    ap.add_argument("--verdicts-dir", type=Path, default=None,
                    help="目录：读其中全部 *.csv（与 --verdicts 可并用）")
    ap.add_argument("--manifest", type=Path,
                    default=REPO / "build/agent-batch9/d1/precision/manifest.json")
    ap.add_argument("--json-out", type=Path, default=None, help="可选：同时落一份 JSON")
    ap.add_argument("--allow-selftest", action="store_true",
                    help="放行 SELFTEST- 前缀的自测夹具（只用于验证本工具自身）")
    args = ap.parse_args(argv)

    manifest_path = resolve_repo_path(args.manifest)
    if not manifest_path.exists():
        raise SystemExit(f"manifest 不存在：{manifest_path}（先跑 sample_binding_precision.py）")
    manifest = load_manifest(manifest_path)

    paths: list[Path] = []
    for p in args.verdicts:
        paths.append(resolve_repo_path(p))
    if args.verdicts_dir:
        d = resolve_repo_path(args.verdicts_dir)
        if not d.is_dir():
            raise SystemExit(f"--verdicts-dir 不是目录：{d}")
        paths.extend(sorted(d.glob("*.csv")))
    if not paths:
        raise SystemExit("未给判定件：--verdicts 或 --verdicts-dir")
    seen: set[Path] = set()
    uniq: list[Path] = []
    for p in paths:
        if p in seen:
            continue
        seen.add(p)
        if not p.exists():
            raise SystemExit(f"判定件不存在：{p}")
        uniq.append(p)

    verdicts, errors = read_verdicts(uniq, allow_selftest=args.allow_selftest)
    if errors:
        print("判定件不合法，未产出任何数字：", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        return 2

    # 与"应有清单"逐条核对：数量、层名、成员都要对得上
    expected = expected_from_kits(manifest)
    manifest_layers = list(manifest["layers"])
    for layer in sorted(set(verdicts) | set(expected) | set(manifest_layers)):
        need = set(expected.get(layer, {}))
        got = set(verdicts.get(layer, {}))
        missing = sorted(need - got)
        extra = sorted(got - need)
        if missing or extra:
            print(f"判定件与样本清单不符（层 {layer}）：缺 {len(missing)} 条、多 {len(extra)} 条",
                  file=sys.stderr)
            for slug in missing[:5]:
                print(f"  - 缺判定：{layer}/{slug}"
                      f"（应绑 {expected[layer][slug]}）", file=sys.stderr)
            for slug in extra[:5]:
                print(f"  - 样本外：{layer}/{slug}", file=sys.stderr)
            return 2
    # 层名核对：样本里的 slug 只能判在它所在的那一层
    layer_of = {slug: L for L, m in expected.items() for slug in m}
    misplaced = [(slug, L, layer_of[slug])
                 for L, m in verdicts.items() for slug in m
                 if layer_of.get(slug) and layer_of[slug] != L]
    if misplaced:
        slug, got_layer, want_layer = misplaced[0]
        print(f"层名与样本不符：{slug} 判在 {got_layer}，样本里是 {want_layer}",
              file=sys.stderr)
        return 2

    # 逐层统计
    layers_out: dict[str, Any] = {}
    parts: list[tuple[float, int, int, int]] = []
    lines: list[str] = []
    for layer in manifest_layers:
        st = manifest["layers"][layer]
        got = verdicts.get(layer, {})
        n = len(got)
        k = sum(1 for v in got.values() if v == "YES")
        size = int(st["size"])
        weight = float(st["weight"])
        p, lo, hi = wilson_interval(k, n)
        layers_out[layer] = {
            "layer_zh": st.get("layer_zh", ""),
            "size": size,
            "weight": weight,
            "sampled": n,
            "correct": k,
            "incorrect": n - k,
            "precision": p,
            "wilson95": [lo, hi],
        }
        parts.append((weight, k, n, size))
        zh = st.get("layer_zh", layer)
        lines.append(f"{layer}/{zh}：n={n}，判对 {k}，点估计 {p:.4f}"
                     f"（Wilson 95% {lo:.4f}–{hi:.4f}），权重 {weight:.6f}（= {size}/{manifest['total_bindings']}）")

    wpoint, wlo, whi, wlo_fpc, whi_fpc = stratified_interval(parts)
    weight_sum = sum(w for w, _k, _n, _pop in parts)

    report = {
        "tool": "tools/kb_coverage/binding_precision.py",
        "schema": 1,
        "manifest": str(manifest_path.relative_to(REPO)).replace("\\", "/"),
        "verdicts": [str(p.relative_to(REPO)).replace("\\", "/") for p in uniq],
        "layers": layers_out,
        "weighted": {
            "precision": wpoint,
            "approx_ci95": [wlo, whi],
            "approx_ci95_fpc": [wlo_fpc, whi_fpc],
            "z": Z95,
            "weight_sum": weight_sum,
            "total_bindings": manifest["total_bindings"],
        },
        "total_sampled": sum(len(v) for v in verdicts.values()),
        "caveats": [
            "层内样本按每科均摊配额 + material_slug 等距抽取，不是简单随机样本；"
            "Wilson 与分层区间都是近似（等距样本的方差与 SRS 同阶但不完全相等）。",
            "加权估计量的区间只含层内抽样误差，不含分层判据本身的不确定度。",
            "薄料层全层仅 333 条绑定、抽 200 条（占比 60%），FPC 对照值明显窄于无校正值。",
        ],
    }

    print("绑定精确率（成品包 三层加权）")
    print(f"manifest：{manifest_path}")
    print(f"判定件：{len(uniq)} 份，共 {report['total_sampled']} 条")
    print()
    for ln in lines:
        print(f"  {ln}")
    print()
    print(f"加权 Σ 精确率 = {wpoint:.4f}（权重合计 {weight_sum:.6f}）")
    print(f"  近似 95% CI（分层 Wald，无 FPC）= {wlo:.4f} – {whi:.4f}")
    print(f"  近似 95% CI（含 FPC 对照）     = {wlo_fpc:.4f} – {whi_fpc:.4f}")
    if abs(weight_sum - 1.0) > 1e-9:
        print(f"  警告：权重合计 {weight_sum!r} != 1（manifest 的层规模与总数不自洽）",
              file=sys.stderr)

    if args.json_out:
        out = resolve_repo_path(args.json_out)
        out.parent.mkdir(parents=True, exist_ok=True)
        with out.open("w", encoding="utf-8", newline="\n") as fh:
            fh.write(json.dumps(report, ensure_ascii=False, indent=1))
        print(f"\nJSON → {out}")
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
