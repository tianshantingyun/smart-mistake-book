# -*- coding: utf-8 -*-
"""把 judge_input/batch-*.json 渲染成判定分片：紧凑纯文本 + 自包含 shard 说明。

用法：python -I -X utf8 render_compact.py
产物：
- judge_input/compact-NN.txt（判定员速读用，与批次 50 同格式）
- shards/batch-NN.md（每片写清输入文件路径、输出要求与 50 条判定件；不含 layer）

判定输入本身不含 layer / pool / 全局序号；本脚本也不引入任何层标签。
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
JUDGE = HERE / "judge_input"
SHARDS = HERE / "shards"
PROMPT = HERE / "JUDGE_PROMPT.md"


def find_repo(start: Path) -> Path:
    for p in [start, *start.parents]:
        if (p / "core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json").exists():
            return p
    raise SystemExit("找不到仓库根（core/data/.../knowledge/moe-2025-four-subjects-v1.json）")


REPO = find_repo(HERE)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(REPO)).replace("\\", "/")
    except ValueError:
        return str(path).replace("\\", "/")


def one_line(s: str) -> str:
    return " / ".join(part.strip() for part in str(s).splitlines() if part.strip())


def render_items(items: list[dict]) -> list[str]:
    out: list[str] = []
    for it in items:
        cur = it["current_node"]
        out.append(f"[{it['n']}] {it['material_slug']} | {it['subject']} | {it['type']}")
        out.append(f"T: {one_line(it['title'])}")
        out.append(f"X: {one_line(it['text'])}")
        out.append(f"N: {cur['name']} | {cur['kind']} | topic={cur['topic_name']} | {one_line(cur['boundary'])}")
        out.append("")
    return out


def shard_md(batch: str, items: list[dict], input_path: Path, total: int) -> str:
    audit_id = json.loads((HERE / "manifest.json").read_text(encoding="utf-8"))["audit_id"]
    idx = int(batch.replace("batch-", ""))
    lines: list[str] = [
        f"# {audit_id} · 判定分片 {batch} / {total:02d}",
        "",
        "> **盲判**：本片不含 layer / pool / 全局序号；禁止推测或利用层归属。",
        "",
        "## 输入",
        "",
        f"- 机器输入件（本片 {len(items)} 条，JSON）：`{rel(input_path)}`",
        f"  - sha256：`{sha256_file(input_path)}`",
        f"  - 条数：{len(items)}",
        f"- 节点索引（REBIND 目标查证）：`{rel(HERE / 'node_index.tsv')}`",
        f"- 判定口径原文：`{rel(PROMPT)}`",
        f"  - sha256：`{sha256_file(PROMPT)}`",
        "",
        "## 输出要求",
        "",
        f"- 落盘：`{rel(HERE / 'verdicts_raw' / (batch + '.tsv'))}`",
        "- TSV 表头逐字：`material_slug\tverdict\ttarget_node_slug\tconfidence\tevidence_quote\tnote`",
        f"- 恰 {len(items)} 行（每条 material_slug 一行），顺序与本片输入一致；不得漏判、重判、加行。",
        "- `verdict ∈ {CORRECT, REBIND, NONE}`；REBIND 必须填同科现存 `target_node_slug`"
        "（从 `node_index.tsv` 逐字抄；同名 slug 按 subject 列区分），其余留空。",
        "- `evidence_quote`：从材料 title / summaryMarkdown / applicabilityMarkdown / "
        "contentMarkdown / boundaryMarkdown 逐字复制的连续子串（≤160 字，禁改写/拼接/省略号）；"
        "X 行里的 ` / ` 是字段/换行连接符、不是原文，引文不要跨 ` / `；"
        "脚本机械校验 `quote in text`，不通过按错误计。X 行是正文节选（≤500 字），引文请从可见文本复制。",
        "- `confidence ∈ {high, medium, low}`；REBIND/NONE 必须写 `note`（≤60 字）。",
        "- 不写 layer 列，不写其它列。",
        "- 硬禁令：不得因标题出现在节点 aliases 判 CORRECT；不得跨科改绑；拿不准标 `low`，"
        "不要默认 CORRECT。",
        "",
        f"## 判定条目（{len(items)}）",
        "",
        *render_items(items),
    ]
    return "\n".join(lines)


def main() -> int:
    SHARDS.mkdir(parents=True, exist_ok=True)
    paths = sorted(JUDGE.glob("batch-*.json"))
    if not paths:
        raise SystemExit("judge_input/ 下没有 batch-*.json；先跑 sample_d1.py")
    total = len(paths)
    for path in paths:
        items = json.loads(path.read_text(encoding="utf-8"))
        compact = path.with_name(path.stem.replace("batch-", "compact-") + ".txt")
        compact.write_text("\n".join(render_items(items)), encoding="utf-8")
        md = SHARDS / (path.stem + ".md")
        md.write_text(shard_md(path.stem, items, path, total), encoding="utf-8")
        print(f"{path.name} -> {compact.name} / {md.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
