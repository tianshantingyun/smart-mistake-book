# KB 线验收终章 · F1–F24 逐条终态（2026-11-06）

> **文件名按 ask 指定**；本验收轮实际执行于 **2026-11-08**（HEAD `ba7b5624ea0c2bc3f09dcee247f5852e4f428122`，worktree `git status --porcelain` = **643** 项，staged 0）。主要“本轮实跑”命令的原始输出保存在 `build/kb-acceptance-final-*.log` 与 `build/agent-f24-final/*.recheck.*`；inline 复算命令在正文逐字给出。历史读数只引用其落盘证据，并标注“本轮未重跑”。
>
> **判定三态（不放宽）**：
> - **达标** = 判据要求的分母/阈值在证据上成立，且复现命令可重跑；
> - **边界** = 判据本身不触发（N/A），或受外部/宿主/他线条件限制，本仓不能自证；边界不等于通过；
> - **未达标** = 判据未成立，或缺少判据要求的全量证据（UNVERIFIED 按未达标计）。
>
> **总判**：**达标 6 / 边界 6 / 未达标 12**。KB 线与“F1–F24 全部达标”仍有距离；其中 F6/F8/F9/F13/F14/F17–F23 共 12 项未达标，F7/F10/F11/F15/F16/F24 共 6 项只能登记边界。**不得把本文件读作“全绿”**。

---

## 0. 口径与基线

| 项 | 值 | 证据 |
|---|---|---|
| HEAD | `ba7b5624ea0c2bc3f09dcee247f5852e4f428122` | `git rev-parse HEAD`（本会话实跑） |
| worktree | 643 项未提交（`M`/`??` 混合，含他线在飞） | `git status --porcelain \| wc -l` = 643 |
| 内容包 | manifest `version=28` / `contentVersion=ce8edb8f89d91fe7` | `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json`（本会话读取） |
| 材料/节点 | 64,853 条材料 / 3,280 节点（25 个 release sidecar） | 本轮 `dedupe_near_dup_materials --dry-run` 输出；本轮 summary/title 复算 |
| 金标 | `golden_queries_v2.json` 129 条 / 24 章 / top5 | `tools/kb_coverage/tables/golden_queries_v2.json`（本会话读取，129 条） |
| 检索基线 | 融合 Recall@5 117/129 = 0.907 | 本轮 `kb_coverage.retest_usage_r1` 输出第 2 行 |
| F24 来源登记册 | 32 源：pendingAcquisition 1 / metadataVerified 9 / acquiredUnreviewed 19 / acquiredReviewed 3 / revisionInterpretations 1 | 本轮 F24 口径 A 报告 |

**口径纪律**：F1–F5 沿用 `docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md` §F.1；F6–F7 沿用同文 §F.5；F8–F12 沿用 `docs/kb-batch30-2026-10-31.md:264-277`；F13–F16 沿用 `docs/kb-batch31a-2026-11-01.md:170-196`；F17–F19 沿用 `docs/kb-batch32a-2026-11-01.md:180-186`；F20–F24 沿用 `docs/kb-batch32b-2026-11-02.md:290-300` 与 `docs/kb-batch33-2026-11-02.md:220-234`。**判据文字与阈值不改**；F20 采用 v1.6 的“未解释近重复=0 + 锚点零误伤”口径，F23 采用 v1.6 的“点估计 ≥0.61 且 bootstrap 95% CI 下界 ≥0.61”。

---

## 1. F1–F24 总表

| # | 判据（冻结口径） | 本轮读数 / 状态 | 终态 |
|---|---|---|---|
| F1 | 每节点材料覆盖 ≥90% | 645/645 = **100.0%**（py 与 sql 双口径一致） | **达标** |
| F2 | 期望节点有材料 ≥90% | **117/129 = 90.7%**（= 召回命中上限；余量 0.70pp） | **达标** |
| F3 | 误区槽位占比 ≤20% | **8/645 = 1.24%**（CONCEPT 562 / METHOD 75 / MISCONCEPTION 8） | **达标** |
| F4 | 无材料节点显式标注 | 金标 **0/0（空真）**；JVM 夹具 5/0 + 6/0 证明标记真实渲染 | **达标** |
| F5 | 预算与形态不变 | **5 条/查询（129/129）**；chars max **838** ≤2,000；UTF-8 max **2,267 B**（R5 契约侧，不等于 F5 通过） | **达标** |
| F6 | 首启导入 ≤60 s（冷装全量） | batch29 v23 三次 47,504/51,775/51,646 ms；但 **batch35 W1 当前 v28 基线三次 66,530 / 60,905 / 59,012 ms（median 60,905 >60,000，2/3 超限）** | **未达标** |
| F7 | 截断安全（R3/R4） | JVM `SafeTruncationTest` **13/13** 本轮通过；设备端真实 `KNOWLEDGE_READ` 截断抽验未做 | **边界** |
| F8 | title 必现 + 节选 >60 | JVM 夹具 **13/13** 含 `longTitleIsKeptVerbatim…`；129 条镜像 F8a/F8b 全量读数不存在 | **未达标** |
| F9 | 真实 2k 门 | JVM `KnowledgeReadRecallBudgetTest` **6/6** 本轮通过；129 条 F9a max / F9b–d 计数未落盘 | **未达标** |
| F10 | 配额臂读数（若采纳） | **N/A — 臂未采纳**；历史读数 114/129、4 章 lower、零漂移 5 仍登记 | **边界** |
| F11 | 16KB 页兼容：DAO 仪器化 + 冒烟 0 崩溃 | x86_64 `ps16k` 镜像 **OK (4 tests)**、logcat 0 FATAL；未读 `PAGE_SIZE`，arm64 16KB 未跑 | **边界** |
| F12 | 遥测发射：`material_coverage` 真实事件 ≥1 | logcat **14 条** `v=1 metric=material_coverage` | **达标** |
| F13 | 去重后近重复 = 0 | 本轮 dry-run：**50 簇 / 173 对 / 拟退休 1 / SKIP 20**（≠0） | **未达标** |
| F14 | 退化摘要 = 0 | 本轮复算 **127**（`len(strip)≤8` 或 `norm(summary)==norm(title)`） | **未达标** |
| F15 | title 合规率 | 本轮复算 **34,209/64,853 = 52.7485%**；判据无阈值，只读数、不判绿 | **边界** |
| F16 | 别名仅材料派生、无题干反推 | 37,561 行 = 37,526 材料标题 + 35 登记例外 + **0 未解释**；`alias==golden query` 0；但 35 条例外非材料派生，其中 2 条为 golden query 严格子串 | **边界** |
| F17 | BM25 词面腿 | 设备融合 **115/129**（B 117/129）；24 章 2 lower/22 equal/0 higher；零漂移 **9/2 ≠0**；bootstrap NOT_RUN；臂已回滚 | **未达标** |
| F18 | 材料特征索引 L2 | 无 L2 预注册、无代码、无读数；L1 的 47,470 ms 不能挪作 L2 | **未达标** |
| F19 | 查询类型路由 L3 | 装置已由批35 落盘；设备报告 **`NOT_JUDGEABLE`（Z1 mismatch 14）**，按预注册不判读/回滚 | **未达标** |
| F20 | 去重：未解释近重复=0 + 锚点零误伤 | 锚点 **10/10 在位、0 误伤**；但同规则 dry-run 仍有 **1 条拟退休 + 20 SKIP**（`ext-che-2728b2ed9a-016` → `ext-che-4f82967fc2-009`，节点「纯净物」） | **未达标** |
| F21 | 退化摘要 = 0 | **127 ≠ 0**（B1 摘要臂回滚后复原） | **未达标** |
| F22 | title 合规率（205 集 100%） | 205 集 **202/205 = 98.54%**；1 条 APPLIED 仍含 `^`（`ext-mat-1a1cc76f2d-032`）；全包 68.06%（F22 规则）/ 68.31%（batch32b 原读数） | **未达标** |
| F23 | κ 点估计 ≥0.61 且 CI 下界 ≥0.61 | 点估计 **0.6247272727**；bootstrap 95% CI **[0.5510018, 0.6968699]**；`POSITIVE_REPRODUCTION_LOW`、`ciCrossesThreshold=true` | **未达标** |
| F24 | 来源门（v1.6 原口径：`-RequireProductionReady` 绿） | 口径 A「来源可核性」**EXIT=0**；口径 B「九科四类来源齐全」**EXIT=1**（九科 × 四类全缺） | **边界** |

---

## 2. 逐条证据与复现命令

### 2.1 F1–F5（使用面材料分配）

**统一复现命令（本轮实跑，exit 0）**：

```bash
cd "D:/smart mistake book"
PYTHONPATH=tools python -X utf8 -m kb_coverage.retest_usage_r1 > build/kb-acceptance-final-retest-r1-inrepo.log 2>&1; echo "EXIT=$?"
```

**逐字输出（节选，完整件见 `build/kb-acceptance-final-retest-r1-inrepo.log`）**：

```text
FUSED mirror Recall@5 117 / 129 = 0.907
rows 645 rows_with_material 645 py_summary_rows 645 F1 100.0 | sql_summary_rows 645 F1_sql 100.0
py 117 / 129 90.7 | sql 117 / 129 90.7 | recall hits 117
py slots 645 {"CONCEPT_EXPLANATION": 562, "METHOD_MODEL": 75, "MISCONCEPTION_GUIDE": 8} misconception 8 F3 1.24
no_material_rows 0 marked 0 fake_material_segments 0
rows/query py {"5": 129}
render chars(UTF-16) min/median/max 660 743 838 | max pct of 2000 chars 41.9
render utf8 bytes min/median/max 1707 1992 2267 | max pct of 2000 bytes 113.3
checked_sql_rows 645 mismatch_queries 0 mismatch_nodes 0
```

- **F1 达标**：分母 645 = 129×5 中有绑定材料的节点行；分子 645，`F1 100.0`，SQL 镜像同值。
- **F2 达标**：117/129 = 90.70% ≥90%；12 条 MISS 的期望节点不在 top-5，结构上不可能有材料；命中查询 117/117 全覆盖。
- **F3 达标**：`MISCONCEPTION_GUIDE` 8/645 = 1.24% ≤20%。
- **F4 达标**：金标 645 行全有材料，`no_material_rows 0` 是空真，不作为通过依据；通过依据是 JVM 夹具——`UsageMaterialAllocationTest.kt:183` `aNodeWithoutMaterialsRendersTheExplicitMarkerWithoutDisturbingMaterialNodes` 与 `KnowledgeReadRecallBudgetTest.kt:196` `everyRenderedNodeGetsItsOwnMaterialAndAMaterialslessNodeIsMarkedExplicitly`。本轮定向测试 `UsageMaterialAllocationTest` **5/0**、`KnowledgeReadRecallBudgetTest` **6/0**（XML 见 §2.2）。
- **F5 达标（限字符口径）**：129/129 查询各 5 条；UTF-16 max 838 ≤2,000。**UTF-8 max 2,267 B 已越 2,000 B**，这是 R5 契约侧问题，不能写成“2k 字节通过”。

### 2.2 F6–F7（性能门 / 截断安全）

**F6 证据（本轮未重跑，只读两组日志）**：

```bash
cd "D:/smart mistake book"
cat build/f6-retest-20261031/run1/install-finished.txt \
    build/f6-retest-20261031/run2/install-finished.txt \
    build/f6-retest-20261031/run3/install-finished.txt
cat build/agent-batch35/w1/runs/run1/finished.txt \
    build/agent-batch35/w1/runs/run2/finished.txt \
    build/agent-batch35/w1/runs/run3/finished.txt
python -I -X utf8 -c "import json;d=json.load(open('build/agent-batch35/w1/attribution.json',encoding='utf-8'));print(d['apk']['sha256'],d['repo']['headSha'])"
```

**逐字输出（batch29，v23 内容 + emulator）**：

```text
10-31 05:13:43.029 ... KnowledgeInstall: install finished in 47504 ms (full path: parse + reconcile)
10-31 05:14:47.769 ... KnowledgeInstall: install finished in 51775 ms (full path: parse + reconcile)
10-31 05:15:56.422 ... KnowledgeInstall: install finished in 51646 ms (full path: parse + reconcile)
```

**逐字输出（batch35 W1，当前 v28 基线 + emulator-5566/API34）**：

```text
D/KnowledgeInstall(16037): install finished in 66530 ms (full path: parse + reconcile)
D/KnowledgeInstall(18815): install finished in 60905 ms (full path: parse + reconcile)
D/KnowledgeInstall(20332): install finished in 59012 ms (full path: parse + reconcile)
```

- **F6 终态：未达标**。batch29 的 v23 读数三次全 ≤60 s，但**当前 v28 基线在 batch35 W1 上三次为 66,530 / 60,905 / 59,012 ms，median 60,905 ms >60,000 ms，2/3 超限**；该轮 HEAD `3007a2bc`，APK 为该工作树构建（`bb459273…`），内容锚 `10ede1bf…`（主包）/ manifest `d69ebb8b…` 与当前树逐字一致（本会话复算 manifest/pack sha 相同）。按判据“≤60 s”不放宽，当前基线未过 ⇒ 未达标。若以 batch29 的 51,646 ms 为“曾达标”，也只能作为历史读数，不能替代当前树读数。
- 复测命令：同 APK 同口径冷装三次（uninstall → install → 冷启），逐轮读 `KnowledgeInstall: install finished in … ms (full path: parse + reconcile)`；N=3 全 ≤60,000 ms 才可转达标。

**F7 证据（本轮实跑，exit 0）**：

```bash
cd "D:/smart mistake book"
./gradlew :core:data:testDebugUnitTest \
  --tests '*SafeTruncationTest*' --tests '*UsageMaterialAllocationTest*' \
  --tests '*KnowledgeReadRecallBudgetTest*' --rerun-tasks --console=plain \
  > build/kb-acceptance-final-gradle-rerun.log 2>&1; echo "EXIT=$?"
```

**结果（XML 实读）**：

```text
SafeTruncationTest            tests 13 failures 0 errors 0 skipped 0
UsageMaterialAllocationTest   tests  5 failures 0 errors 0 skipped 0
KnowledgeReadRecallBudgetTest tests  6 failures 0 errors 0 skipped 0
```

- **F7 终态：边界**。`safeTruncateMarkdown` 的 JVM 夹具（公式内切口回退、句界回退、保底 `take`、2k 字符预算）全绿，`SafeTruncationTest.kt:324` 覆盖长标题；但**设备端真实 `KNOWLEDGE_READ` 截断抽验未做**（批次27/29/30 均登记），且未覆盖 R2（标题/正文缺失）与 R5（2k 字节越界）。设备端未验，不能记达标。

### 2.3 F8–F12（渲染 v2 / 16KB / 遥测）

**F8 未达标**：`SafeTruncationTest` 13/13 含 `longTitleIsKeptVerbatimWithExcerptsOverSixtyCharsAndOverBudgetTrimCarriesTheFooter`（`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/SafeTruncationTest.kt:324`），本轮实跑通过；但判据要求“title 必现 + 节选 >60”在 129 条金标镜像上的 F8a/F8b 读数**不存在**。预注册文末无 §7，129 条镜像无落盘。缺全量证据 ⇒ 未达标。

**F9 未达标**：`KnowledgeReadRecallBudgetTest` 6/6（`MAX_TOOL_RESULT_CHARS = 2_000`，`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolLoop.kt:489`；断言 `KnowledgeReadRecallBudgetTest.kt:174,252,392,414`），本轮实跑通过；但 129 条镜像的 F9a max、F9b–d displayed/dropped/trimmed/footer 计数**未落盘**。JVM 夹具不能替代“129 条逐条 ≤2,000”。⇒ 未达标。

**F8/F9 的“全量读数缺失”复核命令（只读）**：

```bash
cd "D:/smart mistake book"
sed -n '264,277p' docs/kb-batch30-2026-10-31.md   # v1.3 F8/F9 行：JVM 夹具绿，129 条镜像 UNVERIFIED
```

**F10 边界（N/A）**：配额臂未采纳，判据条件“若采纳”不触发。历史读数保留：设备 114/129、24 章 4 lower、同路 fused 零漂移 5。复核命令：

```bash
cd "D:/smart mistake book"
cat build/agent-batch30/quota/SUMMARY.md
```

N/A 不等于通过。

**F11 边界（16KB 只到 x86_64 冒烟）**：

```bash
cd "D:/smart mistake book"
grep -c "FATAL EXCEPTION\|AndroidRuntime.*FATAL" build/ps16k-smoke-2026-11-01/logcat-coldstart.txt \
  build/ps16k-smoke-2026-11-01/logcat-dao-test.txt build/ps16k-smoke-2026-11-01/emulator-5602.log
grep -E "OK \(|tests|PASSED|FAILED" build/ps16k-smoke-2026-11-01/am-instrument-teaching-allocation.txt
```

**输出**：三份 logcat 的 FATAL 计数均为 **0**；`am-instrument-teaching-allocation.txt` = `OK (4 tests)`。但未直接读 `PAGE_SIZE`，冷启日志在 CHEMISTRY 重建处截断，装置为 `android-36/google_apis_ps16k/x86_64`，**不是 arm64**。证据：`docs/kb-batch30-2026-10-31.md:186-196`。⇒ 边界。

**F12 达标**：

```bash
cd "D:/smart mistake book"
grep -c "v=1 metric=material_coverage" build/telemetry-verify/logcat-kbobs.txt
```

**输出**：`14`。事件字段含 `subject / queriedNodes / returnedMaterials / nodesWithMaterial / nodesWithoutMaterial / emptyNodeIds`。边界注记：该目录只落 logcat，未落产生它的命令/测试类；生产 M1–M4 指标仍未落地（`KB23-R1-16`，见结案章）。判据“≥1 条真实事件”成立 ⇒ 达标。

### 2.4 F13–F16（材料卫生：去重 / 摘要 / 标题 / 别名）

**F13 未达标（本轮实跑）**：

```bash
cd "D:/smart mistake book"
PYTHONPATH=tools python -X utf8 -m kb_coverage.dedupe_near_dup_materials --dry-run
```

**逐字输出**：

```text
材料 64853 / 节点 3280；候选对 173；簇 50；拟退休 1；SKIP 20；受保护锚点 10；标题别名唯一支撑保护 35538；金标节点 128（3847 条材料）；THIN 节点 433（628 条材料）；保护合计 37536；标题告警 0（44.1s）
```

- 判据“去重后近重复=0”不成立：仍有 **1 条拟退休**（`ext-che-2728b2ed9a-016` → `ext-che-4f82967fc2-009`，节点「纯净物」）与 20 条 SKIP。相对批31A 的 96 组/103 额外对已大幅下降，但不等于 0。
- 本轮 dry-run 会写 `build/agent-batch31c/{plan.json,dedupe-clusters.csv,retire-list.csv}`（gitignored 证据目录），不写数据面。

**F14 未达标（本轮复算）**：

```bash
cd "D:/smart mistake book"
python -I -X utf8 - <<'PY'
import glob,json,re,unicodedata
from pathlib import Path
REL=Path('core/data/src/main/resources/knowledge')
def norm(s): return re.sub(r'\s+','',unicodedata.normalize('NFKC',s or '').casefold())
mats=[]
for f in sorted(glob.glob(str(REL/'moe-2025-teaching-support-v2-[0-9]*.json'))):
    d=json.loads(Path(f).read_text(encoding='utf-8'))
    mats += d.get('materials') or []
deg=[m for m in mats if len((m.get('summaryMarkdown') or '').strip())<=8 or norm(m.get('summaryMarkdown'))==norm(m.get('title'))]
print('materials',len(mats),'degenerate_summary',len(deg))
PY
```

**输出**：`materials 64853 degenerate_summary 127`。判据“退化摘要=0”不成立 ⇒ 未达标。

**F15 边界（只读数、无阈值）**：

```bash
cd "D:/smart mistake book"
python -I -X utf8 - <<'PY'
import glob,json
from pathlib import Path
REL=Path('core/data/src/main/resources/knowledge')
PUNCT="。！？；;，,：:、（）()【】[]《》“”‘’…—·"
mats=[]
for f in sorted(glob.glob(str(REL/'moe-2025-teaching-support-v2-[0-9]*.json'))):
    d=json.loads(Path(f).read_text(encoding='utf-8'))
    mats += d.get('materials') or []
ok=lambda t: len(t or '')<=24 and not any(c in (t or '') for c in PUNCT)
n=sum(1 for m in mats if ok(m.get('title')))
print(n,'/',len(mats),'=',round(100*n/len(mats),4))
PY
```

**输出**：`34209 / 64853 = 52.7485`。判据只要求报读数（批31A §7 明写“ask 未给阈值，不判绿”），因此终态为**边界**，不得写成达标。

**F16 边界（可追溯但不满足“仅材料派生”的严格口径）**：

```bash
cd "D:/smart mistake book"
python -I -X utf8 - <<'PY'
import csv,glob,json
from pathlib import Path
REL=Path('core/data/src/main/resources/knowledge')
titles={(m.get('title') or '').strip() for f in glob.glob(str(REL/'moe-2025-teaching-support-v2-[0-9]*.json'))
        for m in (json.loads(Path(f).read_text(encoding='utf-8')).get('materials') or [])}
rows=list(csv.DictReader(open('tools/kb_build/tables/alias_map.csv',encoding='utf-8-sig')))
exc=list(csv.DictReader(open('tools/kb_build/tables/alias_exceptions.csv',encoding='utf-8-sig')))
excset={(e['subject'],e['alias']) for e in exc}
not_title=[r for r in rows if (r['alias'] or '').strip() not in titles]
print('rows',len(rows),'exceptions',len(exc),'not_title',len(not_title),
      'registered',sum(1 for r in not_title if (r['subject'],r['alias']) in excset),
      'unexplained',sum(1 for r in not_title if (r['subject'],r['alias']) not in excset))
g=json.load(open('tools/kb_coverage/tables/golden_queries_v2.json',encoding='utf-8'))
qs={(q.get('query') if isinstance(q,dict) else q).strip() for q in g}
print('alias==golden query',sum(1 for r in rows if (r['alias'] or '').strip() in qs),
      'len>24',sum(1 for r in rows if len(r['alias'] or '')>24),
      'contains?',sum(1 for r in rows if '?' in (r['alias'] or '') or '？' in (r['alias'] or '')))
PY
```

**输出**：`rows 37561 exceptions 35 not_title 35 registered 35 unexplained 0`；`alias==golden query 0 len>24 0 contains? 0`。

- 37,561 行全部可追溯：37,526 行是材料标题，35 行是登记例外，**0 行未解释**；`alias == golden query` 0。
- 但 35 条例外**不是材料标题派生物**，其中 2 条是 golden query 的严格子串（`实验室制取氢氧化铝`、`二项式系数最大的项`，批16 口径）。严格“别名仅材料派生”不成立，且这 2 条需人工复核 ⇒ **边界**。

### 2.5 F17–F19（三条检索臂）

**F17 未达标**：BM25 词面腿已冻结公式/常数（`k1=1.2`、`b=0.75`、`N=3,280`、`1e-9`、`StrictMath`），单测 7/7；但设备融合 **115/129** 低于 B 的 117/129，24 章 **2 lower / 22 equal / 0 higher**，同路零漂移 **9/2 ≠ 0**，bootstrap **NOT_RUN**；按各臂 final 已回滚。判据“公式/常数/单测/读数”中的设备读数与零漂移未过 ⇒ 未达标。

**F18 未达标**：L2 材料特征索引**无预注册、无代码、无读数**；只有代价外推（+31% 行、约 +44 s，未实测）。L1 的 47,470 ms 导入读数**不属于 L2**，不能挪用。⇒ 未达标（NOT_RUN）。

**F19 未达标**：批34 W6 因 `tools/kb_coverage/routing_eval_v2.py` 缺失而 ABORT（`build/agent-batch34/w6/device/run.status` 原文）；该装置后由批35 落盘（`tools/kb_coverage/routing_eval_v2.py`，2026-11-07 22:38，untracked）。批35 W3 复跑冻结判官：`self-check` / `--static` / `--device` 均 exit 0，`--report` exit 1，**verdict `NOT_JUDGEABLE`**，Z1 mismatches `{identityId:7, routedId:7, identityType:0, routedType:0, total:14}`；按预注册 §4.4“Z1≠0 不得判读”与 §5.4-4 → 不采纳并回滚。Δ overall +0.155814 虽为正，但镜像对不上，不能用于出结论。⇒ 未达标（NOT_JUDGEABLE）。

**F17–F19 复核命令（只读）**：

```bash
cd "D:/smart mistake book"
sed -n '88,159p' docs/kb-batch32a-2026-11-01.md      # L1/L2/L3 三节与 F17–F19 判定
cat build/agent-batch34/w6/device/run.status          # 批34 W6 ABORT 原文（当时装置缺失）
python -I -X utf8 -c "import json;d=json.load(open('build/agent-batch35/routing/report.json',encoding='utf-8'));print(d['verdict'],d['reason'],d['checks']['Z1_staticDeviceMirror']['mismatches'])"   # NOT_JUDGEABLE；Z1 total=14
head -30 build/agent-batch35/w3/final.md              # 批35 W3 判读/回滚记录
```

### 2.6 F20–F24（去重 / 摘要 / 标题 / 校准 / 来源）

**F20 未达标（本轮实跑）**：

```bash
cd "D:/smart mistake book"
PYTHONPATH=tools python -X utf8 -m kb_coverage.dedupe_near_dup_materials --dry-run
cat build/agent-batch31c/retire-list.csv
```

**关键输出**：

```text
材料 64853 / 节点 3280；候选对 173；簇 50；拟退休 1；SKIP 20；受保护锚点 10；...；保护合计 37536；标题告警 0
cluster_id,node_id,node_slug,subject,slug,kept_slug,verdict,reason,title,content_len,protected,title_mentioned_in
50af104ae054,kb:moe-2025-four-subjects-v1:chemistry:atomic:纯净物,纯净物,CHEMISTRY,ext-che-2728b2ed9a-016,ext-che-4f82967fc2-009,RETIRE,near-duplicate,从组成、熔沸点、性质区分纯净物与混合物（第03讲 盐类的水解·思维建模）,72,False,
```

- **锚点零误伤成立**：10/10 登记锚点均在位，`protected_intersect_retire` 为空（工具输出“受保护锚点 10”）。
- **“未解释近重复=0”不成立**：同规则 dry-run 仍给出 1 条 `RETIRE`，且 `protected=False`；这不是“双方受保护”或“链式跳过”的已登记豁免。批33 B3 的 369 对快照记 `未解释=0`；该快照之后同一「纯净物」簇有 4 条材料被退休，簇结构已改变。本轮在现包上对该对重新判定为直接相似，读数与批32B 的“1 条残留”一致。⇒ 未达标。
- 复开条件：人工裁定这 1 条（补退休或纳入保护/实体门），然后复跑 dry-run 至 `拟退休 0`。

**F21 未达标**：

```bash
cd "D:/smart mistake book"
python -I -X utf8 - <<'PY'
import glob,json,re,unicodedata
from pathlib import Path
REL=Path('core/data/src/main/resources/knowledge')
def norm(s): return re.sub(r'\s+','',unicodedata.normalize('NFKC',s or '').casefold())
mats=[m for f in glob.glob(str(REL/'moe-2025-teaching-support-v2-[0-9]*.json'))
      for m in (json.loads(Path(f).read_text(encoding='utf-8')).get('materials') or [])]
print(sum(1 for m in mats if len((m.get('summaryMarkdown') or '').strip())<=8 or norm(m.get('summaryMarkdown'))==norm(m.get('title'))))
PY
```

**输出**：`127`。批33 B1（摘要臂）已回滚，`targets_landed=0`、`degenerate=127`；批32B 的“127/127、退化 0”只在被回滚的 B1 状态成立，不能作为当前树读数。⇒ 未达标。

**F22 未达标**：

```bash
cd "D:/smart mistake book"
python -I -X utf8 - <<'PY'
import glob,json
from pathlib import Path
REL=Path('core/data/src/main/resources/knowledge')
mats=[m for f in glob.glob(str(REL/'moe-2025-teaching-support-v2-[0-9]*.json'))
      for m in (json.loads(Path(f).read_text(encoding='utf-8')).get('materials') or [])]
ok=lambda t: len(t or '')<=24 and not any(c in (t or '') for c in '$\\^_') and not any(c in (t or '') for c in '。！？；')
print('full_pack',sum(1 for m in mats if ok(m.get('title'))),'/',len(mats))
for m in mats:
    if m.get('slug')=='ext-mat-1a1cc76f2d-032': print('residual',repr(m.get('title')))
PY
```

**输出**：`full_pack 44140 / 64853`（F22 规则 = 68.06%；batch32B 在 64,839 包上原读 44,289/64,839 = 68.31%）；`residual '含 (-1)^n 的恒成立参数范围按 n 的奇偶分离求最值（§6.4 数列中的构造问题·slide e43 答案页）'`。
- 205 定向集：**202/205 = 98.54%**，未达 100%；1 条 APPLIED 仍含 `^`（`ext-mat-1a1cc76f2d-032`），2 条按设计回退（40/27 字），1 条 slug 不存在。
- 批33 B2 标题臂 **STOPPED**（0/153 应用），未产生新的全量合规读数。⇒ 未达标。

**F23 未达标**：

```bash
cd "D:/smart mistake book"
python -I -X utf8 -c "import json;d=json.load(open('build/agent-batch32b/calibration/kappa.json',encoding='utf-8'));print(d['n'],d['hardNegatives'],d['primary']['kappa'],d['primary']['ci95_bootstrap']['ci95'],d['decision'])"
```

**输出**：`480 120 0.6247272727272727 [0.5510017999309649, 0.6968698517298187] {'status': 'RUBRIC_NOT_REJECTED_AT_G2', 'threshold': 0.61, 'warnings': ['POSITIVE_REPRODUCTION_LOW'], 'packageChanged': False, 'judgmentSourceChanged': False, 'ciCrossesThreshold': True}`。
- 点估计 0.6247 ≥0.61 成立；但 bootstrap 95% CI 下界 0.5510 <0.61，`ciCrossesThreshold=true`，且 `POSITIVE_REPRODUCTION_LOW`。按 v1.6 口径“点估计 + 下界都要过”，判据未成立 ⇒ 未达标。
- 口径是**模型双标 G2**，不是人工 IAA；人工金标是外部条件（见 `docs/acceptance/adjudication.md:289` 的裁定先例）。即便如此，统计下界未过，不能记边界代替未达标。

**F24 边界（A 绿 / B 红，外部条件）**：

```bash
cd "D:/smart mistake book"
pwsh -NoProfile -File tools/audit-knowledge-source-register.ps1 > build/agent-f24-final/register-audit-default.recheck.json 2> build/agent-f24-final/register-audit-default.recheck.err; ec=$?; echo "DEFAULT_TRUE_EXIT=$ec"
pwsh -NoProfile -File tools/audit-knowledge-source-register.ps1 -RequireProductionReady > build/agent-f24-final/register-audit-requireproductionready.recheck.json 2> build/agent-f24-final/register-audit-requireproductionready.recheck.err; ec=$?; echo "REQUIREPRODUCTIONREADY_TRUE_EXIT=$ec"
```

**本轮实跑输出**：

```text
DEFAULT_TRUE_EXIT=0          # stderr 0 字节
REQUIREPRODUCTIONREADY_TRUE_EXIT=1
```

**口径 A（来源可核性）关键字段**：

```text
registerId = high-school-knowledge-source-register-2025-v1
targetBaselineId = moe-high-school-2017-2025
sourceProductionReady = False
currentCoverageEvidenceReady = False
teachingReferenceEvidenceReady = False
methodReferenceEvidenceReady = False
workedExampleReferenceEvidenceReady = False
sourceCounts = {'total': 32, 'pendingAcquisition': 1, 'metadataVerified': 9, 'acquiredUnreviewed': 19, 'acquiredReviewed': 3, 'revisionInterpretations': 1}
missingCurrentCoverageSubjects = ['CHINESE','MATH','ENGLISH','POLITICS','HISTORY','GEOGRAPHY','PHYSICS','CHEMISTRY','BIOLOGY']
（teaching / method / worked-example 三个 missing 列表同为九科全列）
```

**口径 B（九科四类来源齐全）stderr 原文（去 ANSI 后）**：

```text
Exception: D:\smart mistake book\tools\audit-knowledge-source-register.ps1:578
Knowledge source production gate is not ready. Current-text evidence missing: CHINESE, MATH, ENGLISH, POLITICS, HISTORY, GEOGRAPHY, PHYSICS, CHEMISTRY, BIOLOGY; reviewed multi-source teaching references missing: CHINESE, MATH, ENGLISH, POLITICS, HISTORY, GEOGRAPHY, PHYSICS, CHEMISTRY, BIOLOGY; method references missing: CHINESE, MATH, ENGLISH, POLITICS, HISTORY, GEOGRAPHY, PHYSICS, CHEMISTRY, BIOLOGY; worked-example references missing: CHINESE, MATH, ENGLISH, POLITICS, HISTORY, GEOGRAPHY, PHYSICS, CHEMISTRY, BIOLOGY
```

- 口径 A 的规则由工具实际执行：schema/键集/枚举、`discoveryUri` 必须绝对 HTTPS（`tools/audit-knowledge-source-register.ps1:255`）、`ACQUIRED_*` 必须带 `documentUri`+`contentLengthBytes`+大写 SHA-256（`:365-377`）、三政策交叉一致（`:307-342`）、`LICENSED` 必带 `licenseExpression`/`licenseUri`/`attributionText`（`:347-357`）。
- 口径 B 的门在 `:577` 判定、`:578` throw；四类 missing 均为九科全列。
- **终态：边界**。按 `docs/kb-outstanding-work-2026-09-20.md` §27 的预登记裁定：A 是 F24 的门（rc=0 即绿），B 是**外部条件边界（与真机同级）**，不阻塞 A 口径下的 F24 门，但继续阻塞一切“生产级知识来源就绪”声明。引用时必须 A/B 并列，不得只写 A 的 rc=0。

---

## 3. 边界类专章（ask 指定六项）

> 本节六项全部是**边界**，不是通过。每一项都给出“为什么本仓不能自证”和“复开条件”。

### 3.1 真机

| 项 | 证据 | 结论 |
|---|---|---|
| 物理真机 | 本会话 `C:/Android/Sdk/platform-tools/adb.exe devices` 输出仅 `emulator-5566`、`emulator-5590` 两台模拟器；无物理设备序列 | **未跑真机** |
| 用户裁定 | `docs/acceptance/adjudication.md:289`：“真机/arm64 验证：用户裁定豁免……不伪造 PASS” | **边界（用户豁免）** |
| 影响 | F6/F7/F11/F12 的设备侧读数均为 emulator（API23/34/36-16KB x86_64）；真机差异未测 | 不得把 emulator 读数写成真机通过 |

**复开条件**：接入物理设备（非 emulator），按各 F 项原命令重跑并记录 serial/APK sha；或在发布口径中保留“真机豁免”注记，不伪造 PASS。

### 3.2 AAB / bundletool

| 项 | 证据 | 结论 |
|---|---|---|
| bundletool CLI | 本会话 `which bundletool` → `no bundletool in (...)`，exit 1 | **不可用** |
| 仓内 AAB | `app/build/outputs/bundle/localFirstRelease/app-localFirst-release.aab`（113,439,352 B，mtime **2026-09-30 02:34:47**）；`strictOfflineRelease` 同（113,438,749 B，02:34:20） | **旧物，非当前树** |
| 登记 | `docs/kb-batch34-2026-11-03.md:199`：16KB AAB / bundletool **NOT_RUN**；`:298` 同 | **NOT_RUN** |

**复开条件**：安装 bundletool，基于当前 HEAD 重新构建 AAB，跑 `bundletool build-apks` / `install-apks` / 16KB 对齐核验，并落盘 APK set 与设备安装日志。旧 AAB 不能当 PASS。

### 3.3 arm64 16KB

| 项 | 证据 | 结论 |
|---|---|---|
| AVD | `ps16k_arm64` 在盘，`abi.type=arm64-v8a`，`image.sysdir.1=system-images\android-36\google_apis_ps16k\arm64-v8a\`，`tag.id=page_size_16kb` | 配置在盘 |
| 启动探针 | `docs/research/2026-11-05-batch-43-baseline.md:369-377` 逐字：`FATAL \| Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host. System image must match the host architecture.` | **宿主架构硬阻塞** |
| x86_64 16KB | `build/ps16k-smoke-2026-11-01/`：`OK (4 tests)`、0 FATAL；未读 `PAGE_SIZE` | 只证 x86_64 冒烟 |

**复开条件**：在 arm64 宿主（KVM/HVF）启动 `ps16k_arm64`，读 `getconf PAGE_SIZE` = 16384，跑 DAO 仪器化 + 冷启冒烟 + 全量导入完成，并落盘 logcat 零崩溃签名。x86_64 结果不得外推 arm64。

### 3.4 九科四类来源（F24 外部条件）

| 项 | 证据 | 结论 |
|---|---|---|
| 口径 A | 本轮 `DEFAULT_TRUE_EXIT=0`、stderr 0 B；32 源，`acquiredReviewed=3` | **来源可核（A 绿）** |
| 口径 B | 本轮 `REQUIREPRODUCTIONREADY_TRUE_EXIT=1`；四类 missing 均为九科全列 | **九科四类证据全缺（B 红）** |
| 性质 | `docs/kb-outstanding-work-2026-09-20.md` §27.3：B 需真实外部来源采集 + 人工复核，非本仓文书可自证；与真机/arm64 同级外部条件 | **边界** |

**复开条件**：逐科取得官方 current-text（或 base+delta）证据，并取得每科 ≥2 独立组 reviewed teaching + ≥1 method + ≥1 worked-example 证据，登记为 `ACQUIRED_REVIEWED` 后重跑 `-RequireProductionReady` 至 EXIT=0。在此之前发布口径只能写“来源可核（A 绿）+ 九科四类生产证据未取得（B 红，外部条件）”。

### 3.5 他线 CI

| 项 | 证据 | 结论 |
|---|---|---|
| 最新 main run | 本会话 `gh run list --workflow=android-check.yml --limit 5`：run **42303448215**（2026-11-06T22:16:42Z，`failure`）、42255715807（`failure`）、42255651459（`failure`） | **CI 红** |
| check job | 本会话 `gh run view 42303448215 --log-failed`：`Ran 1167 tests in 334.904s` / `FAILED (failures=11, errors=34, skipped=21)`；失败含 `tests.test_acceptance_lint` / `tests.test_acceptance_report` 的 `TypeError: 'NoneType' object is not subscriptable` 与缺少 `build/acceptance/lint-fake-green*.json` 夹具 | **check 红** |
| instrumented job | 同命令：`Warning: An error occurred while preparing SDK package Android Emulator: Error on ZipFile unknown archive.`、`error: could not connect to TCP port 5554: Connection refused`、`adb failed with exit code 1` | **instrumented 红（环境）** |
| KB 侧对照 | 本会话 `python -I tools/tests/test_dense_asset_gate.py` → `Ran 9 tests ... OK`，exit 0（批34 的 B2b 遗留 3 红已不复现） | KB 子门当前绿，但整条 CI 仍红 |

**结论：边界（他线 CI 红，不归 KB 线单独关闭）**。不能写“CI 全绿”；KB 侧只能声明本会话实际跑过的子门结果。复开条件：他线修复 `core:export`/`core:database` 提交态不一致、acceptance lint/report 夹具缺失、instrumented emulator 启动问题后，整条 `android-check` 在 main 上转绿。

### 3.6 安全审计（Mimosa 覆盖不完整）

| 项 | 证据 | 结论 |
|---|---|---|
| KD-14 | `docs/known-defects.md:538-622`：Mimosa deep 扫描 `runStatus=inconclusive`、`completeness=partial`；语义阶段（threatModel/findingDiscovery）不覆盖 entry points/principals/authorization，`pathAnalysis` N/A；托管 Semgrep/PyCG 在宿主不可用；KD-14 **以宿主环境边界关闭** | **无完整结论** |
| 批43 重试 | `docs/research/2026-11-05-batch-43-record.md:196`：jobId `scan-job-mw18lxy6…`、scanId `scan-2026-11-03T22-07-50…`、seal `d06d4e4b…`；`runStatus=inconclusive`、`completeness=partial`、**13 findings（6 HIGH：1 跟踪 + 5 未跟踪）** | **仍不完整** |
| 可辩护陈述 | 静态阶段覆盖全文件、0 high/medium（KD-14 轮）、依赖面 38 包 0 advisory；但语义阶段未覆盖 | 只能写“静态 + 依赖面”，不得写“安全审计通过” |

**结论：边界（Mimosa 覆盖不完整）**。复开条件：Mimosa 在任一宿主 sealed `runStatus=complete` 且覆盖 entry points/principals/authorization/dataflow；或任何宿主出现 high/medium finding（后者另立 KD）。在此之前所有发布声明必须引用 KD-14，不得声称“安全/无漏洞”。

---

## 4. 未达标清单与复开条件

| # | 未达标事实 | 复开条件 |
|---|---|---|
| F8 | 129 条镜像 F8a/F8b 读数不存在；只有 JVM 13/13 | 在 129 条金标镜像上落盘 title 必现 / 节选 >60 的逐条计数，且全过 |
| F9 | 129 条镜像 F9a max / F9b–d 计数未落盘；只有 JVM 6/6 | 在 129 条镜像上落盘 displayed/dropped/trimmed/footer 计数，且 max ≤2,000 |
| F13 | 近重复 1 条拟退休 + 20 SKIP ≠0 | 人工裁定并执行/登记豁免，dry-run 至 `拟退休 0`（且 SKIP 逐对登记） |
| F14 | 退化摘要 127 ≠0 | 摘要重写臂重新落地并通过不劣判据，复算至 0 |
| F17 | 设备 115/129 < B 117/129；零漂移 9/2；bootstrap NOT_RUN | 修数值确定性/镜像后重跑双路/零漂移/bootstrap，且不劣于 B |
| F18 | L2 无对象 | 先立预注册，冻结特征配额与导入预算，再实施并出读数 |
| F19 | L3 无对象 | 先立预注册，冻结线索表与“非命中零变化”装置，再实施 |
| F20 | 1 条未保护近重复残留；锚点虽 10/10 但不能抵消“未解释≠0” | 人工裁定该 1 条后复跑 dry-run 至 0；或把它纳入保护/实体门并逐对登记 |
| F21 | 退化摘要 127 ≠0 | 同 F14 |
| F22 | 205 集 202/205；1 条 `^` 残留；全包 68.06% | 清 `ext-mat-1a1cc76f2d-032` 的 `^`，205 集至 100%，并另立全量 backlog |
| F23 | CI 下界 0.5510 <0.61；`POSITIVE_REPRODUCTION_LOW` | 提高正例复现率/扩大样本，使 bootstrap 下界 ≥0.61；人工 IAA 可另行登记但不得替代 |

---

## 5. 本轮实跑 / 未跑清单（证据纪律）

**本轮实跑（命令与退出码）**：

| 命令 | 退出码 / 结果 | 证据 |
|---|---|---|
| `PYTHONPATH=tools python -X utf8 -m kb_coverage.retest_usage_r1` | 0；F1 100.0 / F2 117/129 / F3 1.24 / F4 0-0 / F5 838 chars | `build/kb-acceptance-final-retest-r1-inrepo.log` |
| `./gradlew :core:data:testDebugUnitTest --tests '*SafeTruncationTest*' --tests '*UsageMaterialAllocationTest*' --tests '*KnowledgeReadRecallBudgetTest*' --rerun-tasks` | 0；13/0、5/0、6/0 | `build/kb-acceptance-final-gradle-rerun.log` + 三份 XML |
| `PYTHONPATH=tools python -X utf8 -m kb_coverage.dedupe_near_dup_materials --dry-run` | 0；1 retire / 20 SKIP / 50 clusters / 10 anchors | `build/agent-batch31c/*`（本轮重写） |
| 本轮 summary/title/alias 三项 inline Python | 127 / 52.7485% / 37,561+35+0 | 见 §2.4 |
| `pwsh -NoProfile -File tools/audit-knowledge-source-register.ps1` | **0**；stderr 0 B | `build/agent-f24-final/register-audit-default.recheck.{json,err}` |
| 同上 + `-RequireProductionReady` | **1**；四类 missing 九科全列 | `build/agent-f24-final/register-audit-requireproductionready.recheck.{json,err}` |
| `python -I tools/tests/test_dense_asset_gate.py` | 0；`Ran 9 tests ... OK` | 本会话 stdout |
| `gh run list --workflow=android-check.yml --limit 5` / `gh run view 42303448215 --log-failed` | 最新 main run `failure`；check 11 failures/34 errors | 本会话 gh 输出 |
| `adb devices`（SDK 全路径） | 仅 emulator-5566 / emulator-5590 | 本会话 stdout |
| `which bundletool` | 1；未找到 | 本会话 stdout |

**本轮未跑（不得写成通过）**：

- F6 三次冷装（本轮未重装；只读 batch29 `build/f6-retest-20261031/` 与 batch35 W1 `build/agent-batch35/w1/runs/`）；F7 设备端真实截断；F8/F9 的 129 条镜像读数；F10 配额臂；F11 arm64 16KB；F17–F19 三条检索臂；F23 的 κ 重算（只读 `kappa.json`）；所有物理真机 / AAB / bundletool / Mimosa deep scan。
- 未跑原因分别是：设备/宿主/外部来源/他线条件不具备，或读数本就不存在（F8/F9）。**UNVERIFIED 已按未达标计，未以边界代替。**

---

## 6. 终局结论

1. **F1–F5 的使用面材料分配仍成立**：本轮同口径复跑 F1 100.0%、F2 90.7%、F3 1.24%、F4 空真+夹具、F5 838/2,000 chars；这五项可记达标。
2. **F7/F10/F11/F15/F16/F24 只能记边界**：不是失败，但也不是通过。F24 的 A/B 两口径必须并列：A rc=0（来源可核），B rc=1（九科四类全缺，外部条件）。
3. **F6/F8/F9/F13/F14/F17–F23 共 12 项未达标**：F6 当前 v28 基线冷装 median 60,905 ms >60 s；F8/F9 缺全量证据；F13/F14/F20/F21 近重复/退化摘要未清零；F22 标题未 100%；F17–F19 三条臂 gate red / NOT_JUDGEABLE；F23 κ 下界未过。
4. **六项边界（真机、AAB/bundletool、arm64 16KB、九科四类来源、他线 CI、Mimosa）全部未取得**：它们与 F 项的边界互为印证，任何“KB 线全绿/生产就绪/安全通过”的声明都不成立。
5. **本文件不改变任何历史判定**：§27 之前的历史“F24 红”原样有效；本文件只做终态归并与证据刷新，不做洗绿。

**一句话终态**：**KB 线 F1–F5/F12 达标（6 项），F7/F10/F11/F15/F16/F24 边界（6 项），其余 12 项未达标（含 F6 当前 v28 冷装 60,905 ms median）；六项外部边界全部未取得。验收不通过，不得称全绿。**
