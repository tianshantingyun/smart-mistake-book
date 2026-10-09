# -*- coding: utf-8 -*-
"""候选区"量体"：把 16 万块分成【需模型判定】与【机械可判 SKIP】两类，报比例与按来源分布。

## 它消灭的失败

封包轮要处理的是**全部未判定块**（实测 171,461 键 / 块池 186,635 行）。若不分流就整批送模型：
- 大量块本来就**不该成为材料**（题目/答案/目录/封面/版权/纯页码），它们占了相当比例，
  让模型逐条判"这是不是知识点"是纯浪费，而且**题目一旦被模型改写成"知识点"就会违约入库**
  （用户底线：只要知识点不要题目）；
- 真需要模型做的是"把成体系的知识改写成材料 + 绑到知识点"，这必须是**少数块**。

所以本工具先按**机械判据**分流，产出可复核的比例与按来源分布——后面按这个数排工。

## 判据（四类"机械可判 SKIP" + 一类"需模型"）

1. **题目/答案/解析派生**：复用 `kb_build.audit_material_examples` 的四条客观判据
   （标题是题号/考试来源、成套 A/B/C/D、含答案/故选/解析语、短题干无结论）。
   **块内含"知识段"时不适用**（见 `knowledge_span_chars`：块级存在性判据会把
   "讲义正文+随附例题"的混排页整页判掉，批次 6 的 16 条确认误杀全走这一支）；
2. **非知识形态**：目录（点线+页码成行）、封面/版权/出版信息（ISBN、出版社、仅供…使用）、
   广告宣传语、纯页眉页脚/页码、空白与占位（如"本页无正文"）；
3. **过短**：去空白后 < 40 字（不足以承载一条材料）；**完整结论句除外**
   （见 `_complete_statement`：阈值量的是切块器切口，不是内容完整性）；
4. **重复内容**：同一内容指纹（fp）在池中出现多次 → 只留该 fp 组**最全**的那一条
   （旧实现"只留首次"会丢掉更完整的副本：738 个 fp 组里 73 组首次副本更短）；
5. 其余 = **需模型判定**。

注意：本工具**只报告**，不动块池——`--json <path>` 把机器可读汇总落一份 JSON
（工作流读它决定 fan-out 规模）。没有 `--write` 开关。

用法：
    PYTHONPATH=tools python -m kb_coverage.prescreen_chunks
    PYTHONPATH=tools python -m kb_coverage.prescreen_chunks --json build/prescreen.json
"""

from __future__ import annotations

import argparse
import collections
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools"))
from kb_build import audit_material_examples as ame  # noqa: E402
from kb_coverage.pool_path import POOL_PATH  # noqa: E402

CHUNKS = POOL_PATH
JUDGMENTS = REPO / "tools/kb_coverage/tables/material_judgments.csv"
REJUDGE = REPO / "tools/kb_coverage/tables/rejudge_queue.csv"

# 分流哨兵：已判定键的行不参与分流（下游只关心 SKIP/JUDGE）。
JUDGED = "JUDGED"

MIN_CHARS = 40
# 非知识形态（确定性形态，不是内容判断）
_TOC_LINE = re.compile(r"(?:…{2,}|\.{3,})\s*\d{1,4}\s*$", re.M)
# `CIP` 必须独立成词：旧写法命中 `DCIP`（04fac7cba4-037）、基因名 `CsCIPK11`（294376c3d7-236）
# 这类子串巧合，把纯题目块的理由写成"版权/出版信息"。
_COPYRIGHT = re.compile(r"ISBN|版权所有|出版发行|印张|字数|定价|书号"
                        r"|(?<![A-Za-z])CIP(?![A-Za-z])|社址|邮编|开本|责任编辑")
# `限时` 单字面会在题目判据之前短路：广告层 2,480 条里 2,212 条（89.2%）触发词只有它，
# 实为「【限时训练】」题组（370fdfaa09-019）——要求与真广告词搭配，
# 且题目形态的块交给题目判据（理由不再写成"广告宣传"）。
_AD = re.compile(r"扫码|关注公众号|优惠|赠送|免费领取|购买链接|天猫|京东|店铺")
_AD_TIME = re.compile(r"限时\s*(?:练|训|优惠|秒杀|抢购|特惠)")
_NAV = re.compile(r"^(?:第?\s*\d{1,4}\s*页|[•·\-—]?\s*\d{1,4}\s*[•·\-—]?)$")
# `略\s*$` 是子串匹配：空白层 24/24 全由它触发、0 条由"本页无正文/此页空白/空白页"触发，
# 命中的是任何以「策略/简略/忽略」结尾的正文块（77badf0b80-006、b5a7a82b7b-017）。
# 改为要求"略"独立成词或带括号。
_EMPTY = re.compile(r"本页无正文|此页空白|空白页|（略）|\(略\)|(?:^|\n)\s*略\s*[。.]?\s*$")

# —— 判定单位（块 → 段）用的形态信号：只为"块内是否存在知识段"服务 ——
# 题目/解析锚：命中即视为"题目段"的一部分
_Q_OPTION = re.compile(r"^\s*[A-D][.．、]")
_Q_ANSWER = re.compile(r"^\s*【?(?:答案|解析|详解|分析|点睛|点评|试题解析|小问\s*\d+\s*详解)】?"
                       r"|^\s*故选|^\s*答案[　\s:：]|^\s*故答案为|^\s*答[：:]|^\s*解[　\s]")
_Q_STEM = re.compile(r"^\s*例\s*\d|典例|^\s*【?变式|^\s*第\s*\d+\s*题|[（(]\s*\d{4}|"
                     r"（\s*[　\s]*）|\(\s*[　\s]*\)|_{4,}|^\s*【?例题")
# 知识标题锚：讲义/知识清单的行内小标题（"知识段的标题是内容自带的"那几条走下面的未归属分支）
_KNOW_MARK = re.compile(r"考点[一二三四五六七八九十\d]|知识点\d|思维建模|方法技巧"
                        r"|易错(?:提醒|规避|点|辨析|警示)|知识拓展|解题思路|解题关键|答题技巧"
                        r"|规律总结|方法归纳|特别提醒|解题时|核心考点|通法|二级结论|易错点"
                        r"|归纳总结|方法总结|思维导图|教材回扣|基础梳理|核心突破|规律|小结|归纳")
_KNOW_CN = re.compile(r"^\s*[一二三四五六七八九十]{1,3}\s*[、.．]\s*\S{2,}")
# 试卷结构行（"四、解答题：本题共5小题，共77分…"）不是知识段标题
_KNOW_CN_BAD = re.compile(r"本题共|解答题|分值|考试时间|注意事项|满分|答题卡")
_KNOW_ITEM = re.compile(r"^\s*\d{1,2}\s*[.．、]\s*\S")
_KNOW_ANSKEY = re.compile(r"^(?:答案|解析|详解|分析|点评|点睛|故选)")
# 题干/填空信号：未归属段里出现就不再当知识段（"下列/已知/如图/作答空白"两边同形，但只有题干侧必然出现）
_STEM_SIG = re.compile(r"已知|求|如图|下列|正确的是|错误的是|不正确的是|所示|（填|\(填|选填|填字母|叙述"
                       r"|[\u3000]{2,}| {4,}|≈|小题|题型\s*\d|实验内容")
# 解析签名：段里出现即视为上一题的解析续文，不是知识段（"所以"用于
# 329dd4c079-006 这种"上一题解析尾句"被当成知识段的漏网）
_PARSING_SIG = re.compile(r"∴|∵|联立|解得|代入|可得|故选|故答案为|由.{0,14}得|故.{0,6}(?:正确|错误)|综上|所以")
# 知识段必须至少有一行"成文行"：PPT/目录页被切成一行一词，拼起来也能凑够字数，
# 但没有一行正文（1591c3ea15-079 的"考点二"+token 流、05b93bd511-002 的目录页）
_MIN_PROSE_LINE = 16
# 完整结论式（过短豁免）：定义/规律句
_COMPLETE_SIG = re.compile(r"叫做|称为|是指|即为|规律|原则|我们就说")
_MIN_COMPLETE = 30


def _marker_line(line: str) -> bool:
    """知识标题锚行：标记词之外还要带标题文字。

    `考点二` 这类 PPT 幻灯片标题（标记词独占一行）不算锚：否则幻灯片被切成一行一词后，
    标题 + token 流会被拼成"知识段"（1591c3ea15-079 的 338 字"知识段"就是题面）。
    """
    m = _KNOW_MARK.search(line)
    return bool(m) and len(re.sub(r"\s+", "", line)) - len(m.group(0)) >= 2


def _numbered_knowledge(line: str) -> bool:
    """`2.基因突变对性状的影响` 这类编号知识条目：必须短、非小数（0.25）、非答案行、无题干信号。"""
    m = re.match(r"^\s*\d{1,2}\s*[.．、]\s*(\S.*)$", line)
    if not m:
        return False
    if re.match(r"^\d+\s*[.．]\s*\d", line.strip()):
        return False
    core = m.group(1)
    return len(core) <= 22 and not _KNOW_ANSKEY.match(core) and not _STEM_SIG.search(core)


def _complete_statement(stripped: str) -> bool:
    """过短豁免：完整结论句（≥30 字、以句号/叹号收尾、含定义或规律句式）。

    消灭的误杀模式：阈值量的是切块器切口而不是内容完整性——同一「1 电荷」条目被切成
    5 块后，`1)概念：…我们就说它带有电荷。`（38 字）与
    `3)相互作用规律：同种电荷相互排斥，异种电荷相互吸引。`（30 字）被判 SKIP，
    而同条目的另外 3 块（70/96/111 字）判 JUDGE。
    30 字门槛挡住「本题选B。」（4f82967fc2-027，15 字）、孤立选项行（35 字）、文档标题（30 字）。
    """
    return (len(stripped) >= _MIN_COMPLETE and stripped.endswith(("。", "！"))
            and bool(_COMPLETE_SIG.search(stripped)))


def knowledge_span_chars(heading: str, text: str) -> int:
    """块内"知识段"的净字数（0 = 没有知识段）。

    消灭的误杀模式（批次 6 根因报告 §2.1）：复用来的四条题目判据是为**教学材料**写的
    （一条材料 = 一个例题/知识点单元），原样搬到**原始块**上后，"这条材料是不是例题"
    漂移成"这个块里有没有出现过一道例题"——一轮复习讲义/知识清单的排版天然是
    「讲义正文 + 紧跟的例题/变式题」，一道随附例题就否决整页知识（16 条确认误杀全走这一支，
    块内知识段 114–386 字、位置从块首到块尾都有）。

    做法（判定单位从块降到段）：
      1. 逐行标 mode：题目/解析锚 → 题目段；知识标题锚 → 知识段；其余沿用上一行；
         块首（还没见到任何锚）记"未归属"；
      2. 知识段 = 知识标题锚行 + 其后连续正文（**遇到锚就切段**：锚之前的解析续文不算进来，
         否则 `329dd4c079-006` 这类"上一题解析 + 考点标题"会把解析字数算成知识）；
         块首未归属的整段也作为候选（"知识段的标题是内容自带的"那几条：146a58287c-002 的裸结论句、
         fe528a7719-026 的 3.–10. 实验要点、90a8086046-008 的表格正文）；
      3. 段 ≥ MIN_CHARS 字、至少有一行 ≥ `_MIN_PROSE_LINE` 字的成文行，且
         （有知识标题锚 或 段首未归属且无题干/解析信号）→ 记为知识段。

    措辞两边同形（求/已知/若/下列 在知识正文与题干里都出现），所以未归属段必须再排除
    题干信号（下列/已知/如图/作答空白）与解析签名（∴/解得/联立/综上）——这是"段首未归属"
    才会用到的条件，知识标题锚段不受它限制（否则 6c1d89cf2e-002 一类会被误排除）。
    """
    lines = [l.strip() for l in f"{heading}\n{text or ''}".split("\n") if l.strip()]
    dedup: list[str] = []            # 版面切块常把同一行复制两份，虚增字数
    for line in lines:
        if not dedup or dedup[-1] != line:
            dedup.append(line)
    best = 0
    chars = 0
    anchor = False
    buf = ""
    maxline = 0
    start_mode: str | None = None
    have_run = False

    def close_run() -> None:
        nonlocal best, chars, anchor, buf, maxline, start_mode, have_run
        if (have_run and chars >= MIN_CHARS and maxline >= _MIN_PROSE_LINE
                and (anchor or (start_mode is None
                                and not _PARSING_SIG.search(buf)
                                and not _STEM_SIG.search(buf)))):
            best = max(best, chars)
        chars, anchor, buf, maxline, start_mode, have_run = 0, False, "", 0, None, False

    mode: str | None = None
    for line in dedup:
        if _Q_OPTION.match(line) or _Q_ANSWER.match(line) or _Q_STEM.search(line):
            close_run()
            mode = "q"
            continue
        is_anchor = (_marker_line(line)
                     or (bool(_KNOW_CN.match(line)) and not _KNOW_CN_BAD.search(line))
                     or (_KNOW_ITEM.match(line) and _numbered_knowledge(line)))
        if is_anchor:
            close_run()          # 锚行开启新的知识段：锚之前的行不属于它
            mode = "k"
        if mode == "q":
            continue
        if not have_run:
            have_run, start_mode = True, mode
        n = len(re.sub(r"\s+", "", line))
        chars += n
        maxline = max(maxline, n)
        anchor = anchor or is_anchor
        buf += line
    close_run()
    return best



def classify_chunk(heading: str, text: str) -> tuple[str, str]:
    """返回 ("SKIP"|"JUDGE", 理由)。判据顺序：形态 → 题目 → 过短。"""
    body = f"{heading}\n{text or ''}".strip()
    stripped = re.sub(r"\s+", "", body)
    if not stripped:
        return "SKIP", "空块"
    # 题目派生：复用既有四条判据（字段名映射：heading→title，text→contentMarkdown）。
    # 先算是因为 `_AD_TIME`（"限时训练"这类题组标题）要按题目形态分流理由。
    fake = {"title": heading or "", "contentMarkdown": text or ""}
    is_example, why = ame.classify(fake)
    if _EMPTY.search(body):
        return "SKIP", "空白/占位页"
    if _COPYRIGHT.search(body) and len(stripped) < 400:
        return "SKIP", "版权/出版信息"
    if _AD.search(body) or (_AD_TIME.search(body) and not is_example):
        return "SKIP", "广告宣传"
    if _NAV.match(body.strip()):
        return "SKIP", "纯页码/导航行"
    # 目录：多数行都是"条目……页码"
    lines = [l for l in body.split("\n") if l.strip()]
    if lines and len(lines) >= 3:
        toc = len([l for l in lines if _TOC_LINE.search(l)])
        if toc >= max(3, int(len(lines) * 0.6)):
            return "SKIP", "目录（点线+页码）"
    if is_example:
        # 块内含知识段（≥ MIN_CHARS 字）→ 是"讲义正文 + 随附例题"的混排页，送模型判定；
        # 只修"成套选项"这一支无效（16 条里 11 条同时命中"正文含答案/故选"），
        # 所以豁免落在整个"题目派生"判定上，而不是某一条分支上。
        if knowledge_span_chars(heading or "", text or "") >= MIN_CHARS:
            return "JUDGE", ""
        return "SKIP", f"题目派生：{why.split('：')[0]}"
    if len(stripped) < MIN_CHARS:
        if _complete_statement(stripped):
            return "JUDGE", ""
        return "SKIP", f"过短（{len(stripped)} 字 < {MIN_CHARS}）"
    return "JUDGE", ""


def load_judged_keys() -> set[tuple[str, str]]:
    import csv

    keys: set[tuple[str, str]] = set()
    if not JUDGMENTS.exists():
        return keys
    with JUDGMENTS.open(encoding="utf-8-sig", newline="") as fh:
        for r in csv.DictReader(fh):
            keys.add(((r.get("chunk_rel") or "").strip(), (r.get("chunk_id") or "").strip()))
    return keys


def load_rejudge_keys() -> set[tuple[str, str]]:
    """重判队列键（坏 type 待重判）。供 count_unjudged / make_judgment_slices 共用：
    这些键虽在判定表里，但判定作废、按未判定处理。"""
    import csv

    keys: set[tuple[str, str]] = set()
    if not REJUDGE.exists():
        return keys
    with REJUDGE.open(encoding="utf-8-sig", newline="") as fh:
        for r in csv.DictReader(fh):
            keys.add(((r.get("chunk_rel") or "").strip(), (r.get("chunk_id") or "").strip()))
    return keys


def _chunk_chars(rec: dict) -> int:
    return len(re.sub(r"\s+", "", f"{rec.get('heading') or ''}\n{rec.get('text') or ''}"))


def fingerprint_keep_index(chunks_path: Path) -> dict[str, int]:
    """fp → 该 fp 组里"最全副本"的池行号（字数最大，并列取首次出现）。

    消灭的误杀模式（D4）：旧实现"只留首次"会丢掉更全的副本——738 个 fp 组里 73 组
    （9.9%）首次副本比被丢副本更短（多数差 1–3 字；fp=a243c3778020 差 33 字：150 vs 183），
    被判掉的恰是内容更完整的那一份。
    """
    keep: dict[str, int] = {}
    lens: dict[str, int] = {}
    with chunks_path.open(encoding="utf-8") as fh:
        for idx, line in enumerate(fh):
            line = line.strip()
            if not line:
                continue
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue
            fp = rec.get("fp") or ""
            if not fp:
                continue
            n = _chunk_chars(rec)
            if fp not in lens or n > lens[fp]:
                keep[fp] = idx
                lens[fp] = n
    return keep


def iter_classified(chunks_path: Path, judged: set[tuple[str, str]]):
    """逐行读块池并分流，产出 `(rec, verdict, why)`。

    已判定键 → verdict=JUDGED（不参与分流）；未判定键按序应用判据：
    同内容指纹重复 → SKIP-重复（同 fp 组只留最全副本，其余判重复）、
    同 (源,chunk_id) 重复行 → SKIP-重复键、
    其余 `classify_chunk` → SKIP / JUDGE。判据顺序与模块 docstring 一致。

    从 main 抽出来是给 make_judgment_slices / count_unjudged 共用同一份分流，
    消灭"两个工具各写一遍判据、口径漂移"的失败。
    """
    keep = fingerprint_keep_index(chunks_path)
    seen_fp: set[str] = set()
    seen_key: set[tuple[str, str]] = set()
    with chunks_path.open(encoding="utf-8") as fh:
        for idx, line in enumerate(fh):
            line = line.strip()
            if not line:
                continue
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue
            key = ((rec.get("rel_path") or "").strip(), (rec.get("chunk_id") or "").strip())
            if key in judged:
                yield rec, JUDGED, ""
                continue
            fp = rec.get("fp") or ""
            if fp and fp in seen_fp:
                yield rec, "SKIP-重复", "同内容指纹重复（只留最全）"
                continue
            if fp and keep.get(fp, idx) != idx:
                # 更全的副本在别处：本行判重复，且不占用 seen_fp（等最全副本自己来判形态）
                yield rec, "SKIP-重复", "同内容指纹重复（只留最全）"
                continue
            if fp:
                seen_fp.add(fp)
            if key in seen_key:
                yield rec, "SKIP-重复键", "同 (源,chunk_id) 重复行"
                continue
            seen_key.add(key)
            verdict, why = classify_chunk(rec.get("heading") or "", rec.get("text") or "")
            yield rec, verdict, why


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--json", type=Path, help="把机器可读汇总写到这个路径")
    args = ap.parse_args(argv)

    judged = load_judged_keys()
    verdicts = collections.Counter()
    reasons = collections.Counter()
    by_source: dict[str, collections.Counter] = {}
    samples: dict[str, list[str]] = collections.defaultdict(list)
    rows = unjudged = 0

    for rec, verdict, why in iter_classified(CHUNKS, judged):
        rows += 1
        if verdict == JUDGED:
            continue
        unjudged += 1
        verdicts[verdict] += 1
        if why:
            reasons[why] += 1
        src = (rec.get("rel_path") or "?").split("/")[0]
        by_source.setdefault(src, collections.Counter())[verdict] += 1
        if why and len(samples[why]) < 2:
            samples[why].append(f"{src} | {(rec.get('heading') or '')[:30]} | {(rec.get('text') or '')[:60]}")
    dup_rows = verdicts.get("SKIP-重复", 0)

    judge = verdicts.get("JUDGE", 0)
    print(f"块池 {rows} 行；已判定跳过；**未判定 {unjudged} 块**")
    print(f"  → 需模型判定 JUDGE：**{judge}**（{judge / max(unjudged, 1):.1%}）")
    print(f"  → 机械可判 SKIP：{unjudged - judge}（{1 - judge / max(unjudged, 1):.1%}）")
    print("\nSKIP 理由：")
    for why, n in reasons.most_common():
        print(f"   {n:>7}  {why}")
    print("\n按来源（前 12，JUDGE / SKIP）：")
    for src, c in sorted(by_source.items(), key=lambda kv: -sum(kv[1].values()))[:12]:
        tot = sum(c.values())
        print(f"   {src[:34]:<34s} {tot:>7}  JUDGE {c.get('JUDGE', 0):>7}  SKIP {tot - c.get('JUDGE', 0):>7}")
    print("\n样例：")
    for why, ss in list(samples.items())[:8]:
        for s in ss[:1]:
            print(f"   [{why[:26]}] {s[:96]}")

    if args.json:
        payload = {
            "rows": rows, "unjudged": unjudged, "judge": judge, "skip": unjudged - judge,
            "dup_rows": dup_rows,
            "reasons": dict(reasons.most_common()),
            "by_source": {k: dict(v) for k, v in by_source.items()},
        }
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"\n→ 已写 {args.json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
