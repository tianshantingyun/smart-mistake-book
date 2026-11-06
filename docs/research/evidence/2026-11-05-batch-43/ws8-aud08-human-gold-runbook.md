# WS8 item 57：AUD-08 人工金标抽样执行 runbook（BLOCKED）

| 字段 | 值 |
|---|---|
| evidence-id | `B43-WS8-57-HUMAN-GOLD-RUNBOOK-01` |
| batch | 2026-11-05-batch-43 / 计划 §2 WS8 item 57 |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86` |
| executed-at | 2026-11-03T21:39Z（本会话 UTC；本机 2026-11-04） |
| verdict | **BLOCKED（外部：无人工标注员）**；抽样可执行性**已复算通过**；标注/裁定/一致性产物 **UNVERIFIED** |
| 上游 | `docs/research/2026-11-04-aud08-human-gold-sampling-plan.md`（S1 执行件）、`docs/research/2026-11-03-aud08-human-gold-protocol.md`（协议 V1）、`tools/split_golden/GATE.md` §2/§5.1 |
| 本会话实跑 | 抽样框复算 + 24 页 sha256 与计划逐项比对（24/24 一致） |
| 本会话未跑 | 任何人工/模型标注、v2 生成、Gradle、设备 |

## 1. 这一项要消灭的具体失败

批次 42 已落 S1（抽样与切分）执行件，但状态表 U1–U6 / B1 停在同一处：**计划存在、执行没有边界**——
谁提供标注员、从哪一步开始、每一步产出什么、什么算完成，全部只在自由文本里。具体失败是：

1. 下一次会话要么重新推导抽样（可能换一套 24 页/切分），要么把"计划已就绪"误写成"人工金标已完成"；
2. 阻断解除后没有可照做的步骤，v2 的 C1–C7、负控、held-out 与门后自动化前置容易漏项；
3. 外部要求（≥2 名独立标注员 + 1 名非标注员裁定者）没有被固定为**输入条件**，容易被当成"再跑一次模型"绕过。

本 runbook 只补执行边界，不生成任何金标产物，不改 v1 一个字节。

## 2. 本轮实跑：抽样可执行性复算（只读）

### 2.1 抽样框与系统抽样

```bash
python -I -c "
import os
d=r'C:/Users/听云/Pictures/错题数据集'
print('exists', os.path.isdir(d))
names=sorted(os.listdir(d))
print('count', len(names))
print('ext', sorted({os.path.splitext(n)[1].lower() for n in names}))
draw=[names[i] for i in range(0,139,6)]
print('draw', len(draw), draw[0], draw[-1])
"
```

实测输出（本会话）：

```text
exists True
count 155
ext ['.jpg']
draw 24 IMG20260810003928.jpg IMG20260810012103.jpg
```

与批次 42 计划 §2/§3 一致：155 张、字典序、下标 `0,6,…,138`、24 页。

### 2.2 24 页 sha256 与切分逐项比对

```bash
python -I -c "
import os,hashlib,re
d=r'C:/Users/听云/Pictures/错题数据集'
text=open(r'docs/research/2026-11-04-aud08-human-gold-sampling-plan.md',encoding='utf-8').read()
rows=re.findall(r'\| (\d{2}) \| \`(IMG[0-9]+\.jpg)\` \| \`([0-9a-f]{64})\` \| (\w+) \|', text)
names=sorted(os.listdir(d)); draw=[names[i] for i in range(0,139,6)]
table={f:s for _,f,s,_ in rows}; ok=0; bad=[]
for n in draw:
    h=hashlib.sha256(open(os.path.join(d,n),'rb').read()).hexdigest()
    if table.get(n)==h: ok+=1
    else: bad.append(n)
splits=[s for _,_,_,s in rows]
print('parsed', len(rows), 'match', ok, '/', len(draw), 'bad', bad)
print('dev', splits.count('dev'), 'acceptance', splits.count('acceptance'))
"
```

实测输出（本会话）：

```text
parsed 24 match 24 / 24 bad []
dev 16 acceptance 8
```

结论：**抽样可复算、24 页字节未漂移、dev/acceptance 切分与计划一致**。这只证明"可以开始标注"，
不证明任何标注已发生。

## 3. 执行 runbook（阻断解除后逐步照做）

### 3.1 角色与独立性（硬前置）

| 角色 | 要求 | 不得 |
|---|---|---|
| 标注员 A | 真实人工，按冻结指南独立标注 24 页 | 看 B 的结果、看模型中间结果、看 acceptance 用途说明 |
| 标注员 B | 真实人工，与 A 独立 | 同上；不得由 A 兼任 |
| 裁定员 C | 非 A/B，按冻结指南裁决分歧 | 不得参与 A/B 的原始标注 |

模型预标注只能记为 `labelers[].kind="model"`；**把模型标注写成 `kind="human"` 属等级造假**
（协议 V1 `:74`、`:155`）。

### 3.2 分步执行

| 步 | 动作 | 输入 | 产物 | 通过判据 |
|---|---|---|---|---|
| S0 | 冻结指南 `guidelineVersion = aud08-human-gold-v1`：八项属性 + 3 正例/3 反例边界锚 | 协议 V1 | 指南文件（版本号+sha） | 版本号写入 v2 根级；未冻结不得开标 |
| S1 | 抽样与切分 | 本文 §2；抽样框只读副本 | 24 页清单 + sha256 + `split{dev,acceptance}` | 重建清单逐字节同 sha；dev 16 / acceptance 8 |
| S2 | A、B 各标 24 页，页序随机且各自独立 | S1 副本、S0 指南 | 两人页级标注（`file/subject/layout/questionCount/boxes/labelers/strata`） | 每页有 `reviewedBy`；A/B 互不可见 |
| S3 | 一致性报告 | S2 两份 | 原始一致率 + 机会校正统计量 + 逐页题数一致率 + 逐题 IoU≥0.5 配对率 | 报告含分母与口径；不得只报一个比率 |
| S4 | 分歧清单 | S2 两份 | 逐页/逐框 A≠B 列表 | 不合并、不排除难题页 |
| S5 | C 裁定 | S3/S4 + 指南 | `adjudication` + `changes[]` | C ≠ A/B；每次改动可追到页/框 |
| S6 | 冻结 v2 | S1–S5 | `split_golden_v2.json` + `.sha256` 旁车 | C1–C7 全过；负控三改（数字/等级/摘要）各一次**必红** |
| S7 | 验收集单次运行 | 冻结 v2、新应用导出 | acceptance 门报告 | v2 冻结后才跑；结果原样记录，不回改标注/阈值 |

### 3.3 门重跑命令（v2 冻结后；新路径，不覆盖既有证据）

```bash
python -I tools/split_golden/check_split_gate.py \
  --golden <v2 路径>/split_golden_v2.json \
  --app <新应用导出>/app-split.json \
  --report-json build/ws8-batch43/<新名>.json \
  --evidence-out docs/research/evidence/<新批>/<新名>.md \
  --rulings-out docs/research/evidence/<新批>/rulings
python -I tools/split_golden/validate_rulings.py --file <新 rulings.json>
```

- 阈值 `COUNT_GATE=0.9` / `FRAGMENT_GATE=0.05` **不动**；改值须先裁定并留旧值对照
  （`tools/split_golden/check_split_gate.py:91-92`、`GATE.md:241`）。
- 门后自动化仍须 `gate.passed && hardRedLines.fragmentedQuestions.satisfied` **且** 4C③ 产品裁定
  （`GATE.md:225-232`；抽样计划 §7）。四项独立阻断（held-out、等级、可外推性、产品裁定）逐条登记。

## 4. 阻断条件与所需外部输入（BLOCKED）

| # | 需要谁提供 | 具体内容 | 没有它就不能做什么 |
|---|---|---|---|
| B1 | 用户/项目方 | ≥2 名独立人工标注员 + 1 名非标注员裁定者（真实身份与可用时间） | S2–S5 无法开始 |
| B2 | 用户/产品裁定 | 4C③ 门后自动化裁定（AND 硬红线写法） | 即使门变绿也不得开自动化 |
| B3 | 标注组织者 | S0 指南冻结（版本号 + 3 正例/3 反例） | 不得开标 |
| B4 | 数据持有人 | 155 张抽样框读取授权与只读副本 | 无法按 §2 重建样本 |

依据：批次 43 基线 `docs/research/2026-11-05-batch-43-baseline.md:426` 与计划收尾盘点
`docs/research/2026-11-05-plan-closure.md:301` 均把 WS8-57 标为 BLOCKED，原因“无标注员”。

## 5. 未验证 / UNVERIFIED（不得写成通过）

- 人工一致性、裁定质量、人工金标正确率：**UNVERIFIED**（无任何人工标注产物）。
- v2 金标文件、旁车、C1–C7、负控：**未生成**。
- 24 页是否每页可解码、是否有重复字节：本会话只做存在性/字节 sha；解码与内容检查属 S2。
- `heldOutUnusedForTuning=true`：自证字段，**不可机检**。
- 当前门值仍是 G2 代理金标上的 FAIL（见 `step-aud08-gate-recompute-b43.md`），**不是**人工金标结果。

## 6. 回链

- 抽样与切分规范：`docs/research/2026-11-04-aud08-human-gold-sampling-plan.md` §2–§5、§7、§9.1
- 协议：`docs/research/2026-11-03-aud08-human-gold-protocol.md`
- 等级与 C7：`tools/split_golden/GATE.md` §2.1
- 本轮门值：`docs/research/evidence/2026-11-05-batch-43/step-aud08-gate-recompute-b43.md`
- 禁令复扫：`docs/research/evidence/2026-11-05-batch-43/ws8-aud08-prohibition-rescan.md`
- 审计报告：`docs/research/evidence/2026-11-05-batch-43/ws8-aud08-audit-report.md`
