# 材料时效台账 · 超龄警告（2026-10）

- 生成日期：2026-10-13（文件名沿用裁定给出的 `2026-10-12`）
- 台账文件：`knowledge-production/source-age-register-2026-10.csv`（81 行：注册表 32 + 材料侧 49）
- 裁定口径（F15）：**来源版次为准，超龄阈值 10 年（起步值），超龄进「需复核」清单；先警告后进门**。
- 本文件**只警告**：本轮未新增、未修改任何 gate / CI / 测试断言（「进门」留待后续裁定）。

## 一、结论摘要

| 桶 | 条数 | 说明 |
|---|---|---|
| 需复核（2026 − edition_or_year ≥ 10） | **1** | `candidate:bccampus:physical-geology-cc-by`（2015，11 年） |
| 待确认（UNKNOWN 或非年份版次） | **15** | UNKNOWN 11 + 版次号非年份（`2e`）4 |
| 未超龄（可算年份且 <10） | 65 | 注册表 16 + 材料侧 49 |
| 合计 | 81 | — |

- 材料→来源回溯：材料池共 **59010 条材料**，全部挂在**材料侧 49 个 sourceId** 上；注册表 32 个 sourceId 在材料池 **0 命中**（`material_count=0` 为真值，不是缺省；两套 sourceId 命名空间无交集）。

## 二、口径（怎么算的、证据来自哪）

### ① 版次/年份判定
- 逐条只取仓内可核证据，来源按优先级：登记册字段（`title` / `sourceId` / `sourceLocator` / `attributionText`）、`build/kb-staging/*.json` 的 `sources[].edition`、`knowledge-production/scan-registry-2026-09.json` 的 `rel_path` 目录名、sourceId 内嵌年份。
- 推不出年份的填 `UNKNOWN`，notes 写「待人工确认」，**未编造年份**。
- 两种特殊情形（不按阈值计算，单列待确认）：
  - `smartedu` 两个目录的 `2025` / `2026` 是**登记册/快照口径**（活体目录元数据，无出版年），已在 evidence 与 notes 标注；
  - OpenStax 四本记版次号 `2e`（有版次、无出版年；`sourceLocator` 只给 Web PDF 更新日期 2026-04-21/22、2026-06-11，**更新日期≠出版年**）。

### ② 材料→来源回溯口径
- 材料池 = `build/kb-staging/*.json`（`moe-2025-four-subjects-v1` 的 22 个 v2 sidecar + `moe-2020-foundation-v1` pack；sidecar 清单见 `build/kb-staging/moe-2025-teaching-support-v2-index.json`）。聚合键 = `materials[].sourceId`（与 `sources[].sourceId` 同域）。
- `knowledge-production/wusan/materials.jsonl`（319 条）与材料池中 4 个 `registry-wusan-2027-a-version-jingjiang-*` 的 `(sourceId, slug)` 集合**逐条相等**（实测差异 0），故不重复计数。
- 注册表行 `material_count=0`：注册表 32 个 sourceId 在材料池中无匹配——注册表（课标/目录/OER 参考）与材料侧（教辅/扫描件）**命名空间不同**；是否需要把材料侧来源回登注册表，待人工确认。
- `first_used` 口径：注册表行 = 该来源在仓内**最早可核引用文件的首次入库日期**（`git log --diff-filter=A`）；材料侧行 = `sources[].importedAtEpochMillis`（UTC 日期）。无引用证据的记「未使用（仅登记）」。

### ③ 只警告、不加门
- 本台账与本文**不挂钩任何门禁**；`tools/kb_build/gate.py`、CI、测试断言均未改动（本轮只新增两个只读聚合/生成脚本 `build/agent-batch9/source_age_gen.py`、`build/agent-batch9/source_age_doc.py`，不接入任何门禁）。
- 「先警告后进门」：待警告清单稳定、阈值与来源确认后，再另行裁定门禁化。

## 三、需复核（2026 − edition_or_year ≥ 10）

| # | source_id | 名称 | 版次/年份 | 年龄 | 证据 | 建议 |
|---|---|---|---|---|---|---|
| 1 | `candidate:bccampus:physical-geology-cc-by` | Physical Geology（CC BY EPUB） | 2015 | 11 | attributionText「Steven Earle, Physical Geology, BCcampus, 2015」 | 复核概念/术语时效；补登再版年份或换代（建议由人工决定，本轮不代改） |

## 四、待确认（UNKNOWN / 非年份版次）；不按阈值计算

| # | source_id | 名称 | 现值 | 原因 | notes |
|---|---|---|---|---|---|
| 1 | `candidate:zxxk:high-school-teaching-materials` | 学科网高中教学资源 | UNKNOWN | 登记册/材料登记无年份字段 | 待人工确认年份 |
| 2 | `candidate:pep:high-school-review-design` | 人教版高中总复习优化设计 | UNKNOWN | 登记册/材料登记无年份字段 | 待人工确认年份 |
| 3 | `candidate:jyeoo:high-school-preparation-and-analysis` | 菁优网高中备课与试题解析资源 | UNKNOWN | 登记册/材料登记无年份字段 | 待人工确认年份（含试题解析；按政策不作题库使用） |
| 4 | `candidate:siyavula:grade-10-mathematics-cc-by` | Everything Maths Grade 10（无品牌 CC BY EPUB） | UNKNOWN | 登记册/材料登记无年份字段 | 待人工确认年份；首次使用证据=open-teaching-*-2026-v1.json（首入库 b5fe5ddb） |
| 5 | `candidate:siyavula:grade-11-mathematics-cc-by` | Everything Maths Grade 11（无品牌 CC BY EPUB） | UNKNOWN | 登记册/材料登记无年份字段 | 待人工确认年份；首次使用证据=open-teaching-*-2026-v1.json（首入库 b5fe5ddb） |
| 6 | `candidate:siyavula:grade-12-mathematics-cc-by` | Everything Maths Grade 12（无品牌 CC BY EPUB） | UNKNOWN | 登记册/材料登记无年份字段 | 待人工确认年份；首次使用证据=open-teaching-*-2026-v1.json（首入库 b5fe5ddb） |
| 7 | `candidate:siyavula:grade-10-physical-sciences-cc-by` | Everything Science Grade 10（无品牌 CC BY EPUB） | UNKNOWN | 登记册/材料登记无年份字段 | 待人工确认年份；首次使用证据=open-teaching-*-2026-v1.json（首入库 b5fe5ddb） |
| 8 | `candidate:siyavula:grade-11-physical-sciences-cc-by` | Everything Science Grade 11（无品牌 CC BY EPUB） | UNKNOWN | 登记册/材料登记无年份字段 | 待人工确认年份；首次使用证据=open-teaching-*-2026-v1.json（首入库 b5fe5ddb） |
| 9 | `candidate:siyavula:grade-12-physical-sciences-cc-by` | Everything Science Grade 12（无品牌 CC BY EPUB） | UNKNOWN | 登记册/材料登记无年份字段 | 待人工确认年份；首次使用证据=open-teaching-*-2026-v1.json（首入库 b5fe5ddb） |
| 10 | `candidate:siyavula:grade-10-life-sciences-cc-by` | Life Sciences Grade 10（无品牌 CC BY EPUB） | UNKNOWN | 登记册/材料登记无年份字段 | 待人工确认年份；PENDING_ACQUISITION（下载端点仅 134 字节 LFS 指针，正文未取得） |
| 11 | `candidate:open-oregon:principles-of-biology-cc-by` | Principles of Biology: Biology 211, 212, and 213（CC BY EPUB） | UNKNOWN | 登记册/材料登记无年份字段 | 待人工确认年份；首次使用证据=open-teaching-*-2026-v1.json（首入库 b5fe5ddb） |
| 12 | `candidate:openstax:algebra-and-trigonometry-2e` | Algebra and Trigonometry 2e | 2e | 有版次号（2e）无出版年 | 非年份版次→待确认；sourceLocator 仅记 Web PDF 更新 2026-04-21 |
| 13 | `candidate:openstax:college-physics-2e` | College Physics 2e | 2e | 有版次号（2e）无出版年 | 非年份版次→待确认；sourceLocator 仅记 Web PDF 更新 2026-04-22 |
| 14 | `candidate:openstax:chemistry-2e` | Chemistry 2e | 2e | 有版次号（2e）无出版年 | 非年份版次→待确认；sourceLocator 仅记 Web PDF 更新 2026-04-22 |
| 15 | `candidate:openstax:biology-2e` | Biology 2e | 2e | 有版次号（2e）无出版年 | 非年份版次→待确认；sourceLocator 仅记 Web PDF 更新 2026-06-11 |

## 五、材料→来源回溯表（49 行，按材料数降序）

| # | source_id | edition_or_year | material_count | first_used |
|---|---|---|---|---|
| 1 | `desktop-src-48689b61de:physics` | 2026版 | 10967 | 2026-09-18 |
| 2 | `desktop-src-c099d2d6fc:chemistry` | 2026版 | 9845 | 2026-09-18 |
| 3 | `desktop-src-e35c96096a:math` | 2026版 | 7483 | 2026-09-17 |
| 4 | `desktop-src-d8b718d85a:biology` | 2026版 | 3281 | 2026-09-18 |
| 5 | `desktop-src-2452b2cbe4:biology` | 2026/2027版 | 2872 | 2026-09-18 |
| 6 | `desktop-src-7a6607b645:chemistry` | 2027版 | 2125 | 2026-09-30 |
| 7 | `desktop-src-7b2647b433:biology` | 2027版 | 2046 | 2026-09-18 |
| 8 | `desktop-src-7b2647b433:chemistry` | 2027版 | 1956 | 2026-09-18 |
| 9 | `desktop-src-7b2647b433:physics` | 2027版 | 1727 | 2026-09-18 |
| 10 | `desktop-src-7b2647b433:math` | 2027版 | 1616 | 2026-09-18 |
| 11 | `registry-kb-biology-knowledge-list:biology` | 2027版 | 1405 | 2026-07-24 |
| 12 | `desktop-src-e7f072992e:biology` | 2027版 | 1204 | 2026-09-30 |
| 13 | `registry-kb-physics-handout:physics` | 2027版 | 1093 | 2026-07-24 |
| 14 | `desktop-src-d719f687b2:physics` | 2027版 | 1092 | 2026-09-30 |
| 15 | `desktop-src-76de8fd161:math` | 2027版 | 1056 | 2026-09-30 |
| 16 | `registry-kb-chemistry-handout:chemistry` | 2027版 | 938 | 2026-07-24 |
| 17 | `registry-kb-chemistry-methods:chemistry` | 2027版 | 877 | 2026-07-24 |
| 18 | `registry-kb-biology-misc:biology` | 2027版 | 840 | 2026-07-24 |
| 19 | `registry-kb-biology-handout:biology` | 2027版 | 804 | 2026-07-24 |
| 20 | `registry-kb-math-handout:math` | 2027版 | 655 | 2026-07-24 |
| 21 | `registry-lixiangshu-2027-bikuti-kuangk-math:math` | 2027版 | 431 | 2026-07-24 |
| 22 | `registry-lixiangshu-2027-bikuti-kuangk-physics:physics` | 2027版 | 429 | 2026-07-24 |
| 23 | `registry-kb-chemistry-knowledge-list:chemistry` | 2027版 | 355 | 2026-07-24 |
| 24 | `registry-lixiangshu-2027-bikuti-kuangk-biology:biology` | 2027版 | 345 | 2026-07-24 |
| 25 | `registry-kb-math-misc:math` | 2027版 | 340 | 2026-07-24 |
| 26 | `registry-kb-math-methods:math` | 2027版 | 328 | 2026-07-24 |
| 27 | `registry-kb-physics-methods:physics` | 2027版 | 322 | 2026-07-24 |
| 28 | `registry-kb-biology-methods:biology` | 2027版 | 297 | 2026-07-24 |
| 29 | `desktop-src-9c54443f53:chemistry` | 2027版 | 279 | 2026-09-18 |
| 30 | `registry-kb-physics-knowledge-list:physics` | 2027版 | 274 | 2026-07-24 |
| 31 | `desktop-src-3692ca65ed:math` | 2027版 | 245 | 2026-09-18 |
| 32 | `registry-kb-physics-misc:physics` | 2027版 | 224 | 2026-07-24 |
| 33 | `desktop-src-3dd32e4bb8:biology` | 2027版 | 223 | 2026-09-18 |
| 34 | `registry-lixiangshu-2027-bikuti-kuangk-chemistry:chemistry` | 2027版 | 220 | 2026-07-24 |
| 35 | `registry-wusan-2027-a-version-jingjiang-chemistry:chemistry` | 2027版 | 220 | 2026-07-24 |
| 36 | `desktop-src-5b2dfb6dc5:physics` | 2027版 | 218 | 2026-09-18 |
| 37 | `registry-kb-math-knowledge-list:math` | 2027版 | 182 | 2026-07-24 |
| 38 | `registry-wusan-2027-a-version-jingjiang-physics:physics` | 2027版 | 49 | 2026-07-24 |
| 39 | `registry-kb-chemistry-misc:chemistry` | 2027版 | 45 | 2026-07-24 |
| 40 | `registry-wusan-2027-a-version-jingjiang-biology:biology` | 2027版 | 25 | 2026-07-24 |
| 41 | `registry-wusan-2027-a-version-jingjiang-math:math` | 2027版 | 25 | 2026-07-24 |
| 42 | `registry-kb-physics-textbook-preserve:physics` | 2027版 | 20 | 2026-07-24 |
| 43 | `registry-gap-gaokao-2025:chemistry` | 2027版 | 7 | 2026-07-24 |
| 44 | `registry-kb-chemistry-textbook-preserve:chemistry` | 2027版 | 7 | 2026-07-24 |
| 45 | `registry-gap-gaokao-2025:biology` | 2027版 | 5 | 2026-07-24 |
| 46 | `registry-gap-gaokao-2025:physics` | 2027版 | 5 | 2026-07-24 |
| 47 | `registry-kb-biology-textbook-preserve:biology` | 2027版 | 4 | 2026-07-24 |
| 48 | `registry-gap-gaokao-2025:math` | 2027版 | 2 | 2026-07-24 |
| 49 | `source:moe-2020:math:teaching` | 2020版 | 2 | 2026-07-22 |
| — | **合计** | — | **59010** | — |

材料侧版次分布：2027版 ×43；2026版 ×4；2026/2027版 ×1；2020版 ×1。

## 六、注册表行未超龄（可算年份且 <10）——保留供核对

| source_id | edition_or_year | 年龄 |
|---|---|---|
| `registry:moe:2020:high-school-standards-archive` | 2020 | 6 |
| `registry:ictr:2025:high-school-revision-interpretation-toc` | 2025 | 1 |
| `registry:ictr:2026:revision-implementation-notice` | 2026 | 0 |
| `registry:smartedu:2025:high-school-textbook-catalog` | 2025 | 1 |
| `registry:smartedu:2026:high-school-lesson-activity-catalog` | 2026 | 0 |
| `candidate:moe:2025:chinese:curriculum-text` | 2025 | 1 |
| `candidate:moe:2025:math:curriculum-text` | 2025 | 1 |
| `candidate:moe:2025:english:curriculum-text` | 2025 | 1 |
| `candidate:moe:2025:politics:curriculum-text` | 2025 | 1 |
| `candidate:moe:2025:history:curriculum-text` | 2025 | 1 |
| `candidate:moe:2025:geography:curriculum-text` | 2025 | 1 |
| `candidate:moe:2025:physics:curriculum-text` | 2025 | 1 |
| `candidate:moe:2025:chemistry:curriculum-text` | 2025 | 1 |
| `candidate:moe:2025:biology:curriculum-text` | 2025 | 1 |
| `candidate:uh-pressbooks:english-composition-cc-by` | 2019 | 7 |
| `candidate:uh-pressbooks:journeys-world-history-i-cc-by-sa` | 2023 | 3 |

## 七、未做 / 待人工确认

- 材料池 `build/kb-staging/` 可能被并行会话继续写入；本表为 2026-10-13 只读取数，重跑复算命令可核对（数字可能随其更新而变化）。
- 任务书写「材料侧有 42 个不同 `reviewedAtEpochMillis`」：实测材料池 46 个不同值、`build/.rebind5-snapshot/staging` 快照 43 个，**未能复现 42**，口径待确认。
- `docs/kb-textbook-toc-align/*`（2019 人教版 A版 5 册等）与 2027 五三 / 必刷题扫描件的目录对应关系未登记在注册表；本轮只在 evidence 层引用，未为其立行。
- 扫描件登记（`scan-registry-2026-09.json`）未覆盖 `2026年新高考资料(3)/(4)`、`2027版高中《53知识清单》彩色版` 等材料侧目录（表中已标注「登记快照未覆盖」），对应关系待人工确认。
- `registry-gap-gaokao-2025` 命名含 2025、材料登记版次为 2027版，不一致，待人工确认。
- `first_used` 取「首次入库提交日期」，是仓内可核证据的代理量，不是精确业务首用时间。

## 八、复算命令

```text
# 1) 注册表 32 源 + 材料池聚合（只读）
python -c "import json,io; r=json.load(io.open('knowledge-production/source-register-2025-v1.json',encoding='utf-8')); print(len(r['sources']))"   # -> 32
# 2) 材料池逐条聚合 sourceId × 材料数（build/kb-staging/*.json，materials[].sourceId）
#    见 build/agent-batch9/source_age_gen.py（本轮生成脚本，dry 模式可复现 81 行）
python build/agent-batch9/source_age_gen.py dry
# 3) 首次入库日期
git log --format=%as --diff-filter=A -- knowledge-production/curriculum-coverage-extraction-manifest-2025-v1.json   # -> 2026-07-25
# 4) 旧快照对照（43 个不同 reviewedAtEpochMillis）
#    build/.rebind5-snapshot/staging/*.json
```
