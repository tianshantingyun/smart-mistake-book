# AUD-08 人工金标抽样计划（n ≥ 20）——协议 V1 的 S1 执行件

| 字段 | 值 |
|---|---|
| evidence-id | `B42-WS9-AUD08-SAMPLING-PLAN-01` |
| batch | 2026-11-04-batch-42 / 计划项 73（§2 WS9） |
| head | `faab17f58b01eab78d5c08a9f843b3af3df2fecf` |
| executed-at | 2026-11-03（本机时间；文档名按计划批次 42 的 2026-11-04） |
| 输入 | `docs/research/2026-11-03-aud08-human-gold-protocol.md`（协议 V1）、`docs/research/2026-11-02-aud08-annotation.md`（R5）、`tools/split_golden/GATE.md` §2/§5、`tools/split_golden/tables/split_golden_v1.json` |
| 本会话实跑 | 逐文件 `os.listdir` + `hashlib.sha256` 统计 155 张图；按 v1 的 `sampleRule`（步长 6）复算 24 页；核对 7 张 v1 金标页全部落在该抽样内；`python -I tools/split_golden/check_split_gate.py`（见 §8） |
| 本会话未跑 | 任何人工标注、任何模型标注、任何 v2 金标生成、任何 Gradle/设备 |
| verdict | 抽样计划**可执行且可复算**；**人工标注未执行**（无标注员参与）→ G3/G4/G5 产物仍不存在，人工金标注定 **UNVERIFIED** |

> 本文只落 **S1（抽样与切分）** 的执行件：冻结抽样框、抽样规则、24 页清单与 sha256、dev/acceptance 切分、替换规则与门红线前置。
> 它不生成 `split_golden_v2.json`，不改 v1 一个字节，不代表"人工金标已完成"。
> v1 仍是 **G2 代理金标**；`evidenceGradeLevel` 以产物为准，不以本文或自由文本改写。

---

## 1. 为什么必须重出抽样计划（消掉的具体失败）

v1 的 `sampleRule` 与产物有三个可指认的缺陷（R5 §1.1，本会话复核一致）：

1. **155→24 只记了抽样，24→7 的入集准则不在产物里** → 7 页不是 24 页的概率样本，更不是 155 页的概率样本；选择偏差不可评估。
2. **自承 24 张里只有 2 张被人眼打开过**，其余只做存在性/魔数核验 → "两名标注员独立一致"无法从产物复算（`evidenceLevelRecomputable=false`）。
3. **把不一致页排除出金标**（24→7 实为"题数一致且逐题 IoU≥0.6 才入集"）→ 幸存者偏差：难题/分歧页被系统性丢弃，门值只对"容易页"成立。

本计划对应地做三件事：

| v1 缺陷 | 本计划的对策 | 可核判据 |
|---|---|---|
| 24→7 入集准则丢失 | **不一致页不排除**，全部 24 页进 v2，分歧逐条进 `adjudication`/`changes[]`（协议 S4/S5） | 样本量固定为 24；v2 `pages` 数 = 24（或被记录替换后仍 = 24） |
| 只打开 2/24 | 100% 页由两名人工独立标注；每页 `reviewedBy` | v2 每页有 `reviewedBy`；`review.changes[]` 覆盖每次改动 |
| 抽样不可复算 | 冻结抽样框 + 规则 + 24 页 sha256 + 切分（本文 §2–§5） | 任何人按 §8 命令可重建同一清单、逐字节同 sha |

---

## 2. 抽样框（frame）

| 项 | 值（本会话实测） |
|---|---|
| 路径 | `C:\Users\听云\Pictures\错题数据集`（**仓外**，不入库） |
| 文件数 | **155** |
| 扩展名分布 | `.jpg` = 155 |
| 命名 | `IMG<日期><时间>.jpg`（同一相机批次） |
| 排序键 | **文件名字典序**（`sorted(os.listdir(...))`，确定性） |
| 冻结方式 | 24 页逐个 `sha256(file bytes)` 记录在 §4；框外文件不参与本计划 |

> 抽样框是"这一个目录里的 155 张"。它不是全体用户错题数据的概率样本，也不是 155 页之外的推断总体——该限制写进 v2 的 `scope`，不得外推。

---

## 3. 抽样规则与复算证明（v1 规则重建）

协议 V1 §4-S1 要求"记录入集准则"；本计划直接复用 v1 `sampleRule` 已记载的**系统抽样**，并给出可执行定义：

> **规则**：对 155 个文件名做字典序排序，取下标 `0, 6, 12, …, 138`（步长 6，起点 0）→ **24 页**。

**复算证明（本会话实跑）**：

```text
sorted frame = 155
draw = [names[i] for i in range(0, 139, 6)]  → 24
```

- 7 张 v1 金标页在 155 张框里的下标是 `0, 6, 12, 48, 84, 108, 132` —— **全部是 6 的倍数**，因此全部落在上述 24 页内（本会话逐项核对通过）。
- 这同时证明：v1 的 7 页确实是这 24 页的子集，v1 的"24→7"是**入集过滤**而不是"另一次抽样"。24→7 的过滤规则（题数一致 + IoU≥0.6）在 `docs/research/2026-10-14-4c-stage1-record.md:86` 有文字记录，但未进 v1 产物；本计划不再使用该过滤（见 §1）。

**替换规则**（唯一允许偏离上表的情形）：

- 仅当某页无法打开/不是有效图片（解码失败、0 字节、重复字节）时替换；
- 替换件 = 抽样框内下标 `144`、`150`（步长 6 的后续两名）按序补位；
- 每次替换必须记录 `{replaced, reason, replacement, replacementSha256}`，并写进 v2 的 `sampleRule`；
- **不得**因"标注困难/题数少/分歧大"替换——那是 v1 的幸存者偏差复发。

---

## 4. 冻结样本清单（24 页，sha256 与切分）

> `split` 列由 §5 的确定性规则产生：对 24 个**文件名**取 `sha256(name)` 升序，前 16 = `dev`，后 8 = `acceptance`。切分只依赖文件名，不依赖图片内容，也不依赖标注结果。

| # | file | sha256(file bytes) | split |
|---:|---|---|---|
| 00 | `IMG20260810003928.jpg` | `a8283fd2550086c1bc8e99f1973594b69fe00e741d34d44195597a642ea4f34d` | acceptance |
| 01 | `IMG20260810004304.jpg` | `b99119948a0d1f8eff0639dcb491688cef468be9a268325563e8e75bf4672a0b` | dev |
| 02 | `IMG20260810004353.jpg` | `eb92fe5b24ff41ec2ad8396e150faa500336719e13e8baeb973a28433a9fd726` | acceptance |
| 03 | `IMG20260810004511.jpg` | `9190e13efa39df228032754acee0ccd76a5cb38173487820e663d7ff0acde750` | dev |
| 04 | `IMG20260810004854.jpg` | `254833c635f00c34689b332d475d8503faa0921041d192995f1336f25f7dae08` | dev |
| 05 | `IMG20260810004952.jpg` | `d00ee03587664cd52ac88fcbbcfb0b0390e703aeb1b7967fa436c23e64fa7290` | acceptance |
| 06 | `IMG20260810005144.jpg` | `dd91f2d68aa5479814f858b7928dcfd525ed564a655bb3dffb93e7b04c3697a7` | dev |
| 07 | `IMG20260810005301.jpg` | `7d682009ceebd089bdfdc141b4264d589415f79763c4a75369770eb401d2ca7b` | dev |
| 08 | `IMG20260810005605.jpg` | `3dad1553820d3d263ff884d51caeb565246cf5d79af648f1c754894093c754f7` | dev |
| 09 | `IMG20260810005711.jpg` | `8e940693d7fc95c740a7f59a9d4787cb4ee217e04a24ae330619013b5bdceaa1` | acceptance |
| 10 | `IMG20260810005855.jpg` | `3cfca2983f228b10bed09baf89782daf267f039ad0582e59529a9a1fce833ea8` | dev |
| 11 | `IMG20260810010034.jpg` | `ca110cc4ae39968947cdda26d2f69d9db32ef49c08c347b7b3f306c262cb002b` | acceptance |
| 12 | `IMG20260810010202.jpg` | `09f27749dc9bfac95211dedc938830b9e31e2405fedf6f593978d17ff4811da0` | acceptance |
| 13 | `IMG20260810010306.jpg` | `c0a6174dc3584a1f457eed3d0db0daef1644a8c49c8dda84e6a61148df28e5cd` | dev |
| 14 | `IMG20260810010349.jpg` | `9e57e84e0dc6c4ce29e22657a46ba7cf91069a1ee890ef003a93f3c94513af13` | dev |
| 15 | `IMG20260810010633.jpg` | `c8da1e90dd25cfb618b95ff23362dbde6b835e3121b8188c54b59fdeb39a6125` | dev |
| 16 | `IMG20260810010859.jpg` | `758b03abb3db320191a89ada2758c2d4b8f846ca8973aedd8e2c1882cca562b8` | dev |
| 17 | `IMG20260810011017.jpg` | `dcfe9f6714d221e9a928716b996c1f0ebbb13e61ae6dce6b83508f6fe0ff38f2` | dev |
| 18 | `IMG20260810011126.jpg` | `d67c3bed50dbbd96c69baecc1e43857b40c4919f654428189d2500180b25ea3e` | dev |
| 19 | `IMG20260810011248.jpg` | `a4b8d938af9b6717884dca1c4961bcabd9176ae022f1f59824657ad2926bf65f` | dev |
| 20 | `IMG20260810011358.jpg` | `35d5df23ebab77798143735883f2ea0a3d026d2fdb7c639f33bb19fb20b2a78a` | acceptance |
| 21 | `IMG20260810011649.jpg` | `75b90dfbf369e90bd3af15279908e56af267b2d7a540d5871621a308bb3b8389` | dev |
| 22 | `IMG20260810011800.jpg` | `0609ba473e93bfcbf86dd0ada34d7d4cac9849aac4c8f82a08f92321abfd25d4` | acceptance |
| 23 | `IMG20260810012103.jpg` | `689fe4f3ecd2ba4b638406bee90ffc16a88a62e1e78f01ae2a49847656f03003` | dev |

- `dev` = 16 页，`acceptance` = 8 页；两集合不相交、并集 = 24（v2 `split` 字段直接抄本表）。
- 8 张 acceptance 中有 **5 张不在 v1 的 7 页内**（`…4952`、`…5711`、`…0010034`、`…010202`、`…011358`）——这保证验收集不是"已看过的 7 页"。
- v1 的 7 页在本样本里分布为 **dev 4 / acceptance 3**（dev：`…4304`、`…5605`、`…0349`、`…1126`；acceptance：`…3928`、`…4353`、`…1800`）。v1 页在新样本里**不享有任何豁免**，按同一指南重标。

---

## 5. 切分规则（可复算）

```text
order = sorted(draw, key=lambda name: sha256(name).hexdigest())
dev        = order[:16]
acceptance = order[16:]
```

- 只用文件名做键，避免"按内容/难度切分"引入标注者预期。
- 切分在**任何标注开始前**写入 v2 的 `split{dev,acceptance}`；`acceptance` 封存到 v2 冻结（协议 S7）。
- `heldOutUnusedForTuning=true` 是自证字段：本计划要求标注员与调参者在流程上不接触 acceptance，但**该真伪不可机检**，证据文件一律写 UNVERIFIED。

---

## 6. 标注执行（协议 V1 的 S0–S7 映射）

| 步 | 本计划的值 | 状态 |
|---|---|---|
| S0 指南冻结 | `guidelineVersion = aud08-human-gold-v1`；八项属性 + 3 正例/3 反例 | **未执行**（指南文件未冻结） |
| S1 抽样与切分 | 本文 §2–§5（24 页 / dev 16 / acceptance 8） | **完成（本文件）** |
| S2 独立标注 | 标注员 A、B 各标 24 页；页序随机；互不可见；不看模型中间结果 | **未执行**（无标注员） |
| S3 一致性报告 | 原始一致率 + 机会校正统计量 + 逐页题数一致率 + 逐题 IoU≥0.5 配对率 | **未执行** |
| S4 分歧清单 | 逐页/逐框列 A≠B，不合并 | **未执行** |
| S5 裁定 | 裁定员 C（≠A/B）按指南裁决并记 `adjudication`/`changes[]` | **未执行** |
| S6 冻结 v2 | 新文件 `split_golden_v2.json` + 新旁车；C1–C7；负控三改必红 | **未执行** |
| S7 验收集单次运行 | v2 冻结后才跑 acceptance，结果原样记录 | **未执行** |

角色与独立性要求见协议 §4。**把模型标注写成 `kind="human"` 属等级造假**；模型预标注可保留在 `labelers[]`，但等级只能按实际人工覆盖记 G3/G4。

---

## 7. n ≥ 20 的门值算术与自动决策红线（前置，不可跳过）

- **页级 n = 24 ≥ 20**。题级分母只有 S2 标注后才知道；按 v1 的 7/7 页含题与 16 题 / 7 页 ≈ 2.29 题/页，预计题级 n ≈ 55（**推断，非实测**）。**只要题级 n ≥ 20（24 页中至少 20 页含题即成立），`GATE.md` §5 的冲突即触发**；本计划按"会触发"预置 AND 红线。
- 冲突内容：题级 n ≥ 20 时，`1/n ≤ 0.05`，**1 道切残题会通过 ③ 的闭边界**，与"切残不得自动入库"红线直接冲突。若 S2 实测题级 n < 20，该条不适用，但 ③ 仍不得放宽。
- 所以本计划的启用前提（在 v2 冻结并跑门**之前**）已固定为：

  1. **门后自动化 = `gate.passed && hardRedLines.fragmentedQuestions.satisfied`**，不是只看 ③；
  2. 该 AND 条件必须由产品裁定（4C③）显式确认；本计划**不自裁**；
  3. 即使门变绿，以下四项仍各自独立阻断自动化：held-out 未做、等级仅 G2（升级到 G4 也只能"作输入"）、样本不可外推、无 4C③ 产品裁定（GATE.md §5 四项）；
  4. **阈值 0.9 / 0.05、分母、金标 v1 均不动**；改值须先裁定并留旧值对照。

- 本批门值复算（FAIL 维持）与裁定证据：`docs/research/evidence/2026-11-04-batch-42/step-aud08-gate-recompute-b42.md`、`…/rulings/20261103T044703Z-10c61be3.json`（本会话实跑，exit 1；详见 §8）。

---

## 8. 本会话实跑（可复跑）

```bash
# 框与抽样复算（只读；不打印图片内容）
python -I -c "
import os,hashlib
d=r'C:/Users/听云/Pictures/错题数据集'
names=sorted(os.listdir(d)); assert len(names)==155
draw=[names[i] for i in range(0,139,6)]; assert len(draw)==24
for i,n in enumerate(draw):
    h=hashlib.sha256(open(os.path.join(d,n),'rb').read()).hexdigest()
    print(i,n,h)
"

# 门值复算（FAIL 维持；阈值零改动）
python -I tools/split_golden/check_split_gate.py \
  --report-json build/ws9-batch42/gate-report-batch42.json \
  --evidence-out docs/research/evidence/2026-11-04-batch-42/step-aud08-gate-recompute-b42.md \
  --rulings-out docs/research/evidence/2026-11-04-batch-42/rulings
# → ① 4/7 = 0.5714 < 0.9；③ 0/16 = 0.0000 ≤ 0.05；FAIL / exit 1
python -I tools/split_golden/validate_rulings.py \
  --file docs/research/evidence/2026-11-04-batch-42/rulings/20261103T044703Z-10c61be3.json
# → PASS / exit 0（页数 7；FAIL；rulingsSha256 自洽）
```

另外本会话核对：`git status --porcelain -- docs/current/` = **7 条 M**（契约正文仍为热区，**本计划不改 `docs/current`**）。

---

## 9. 未验证 / 阻塞（如实）

### 9.1 解除阻塞的 runbook（B1/B2）

1. **定人**：指定标注员 A、B 与裁定员 C（C ≠ A/B），记录真实 `agentId`/`role`；三人在标注阶段互不可见。
2. **冻结指南**：写 `guidelineVersion = aud08-human-gold-v1` 的八项属性与 3 正例/3 反例边界锚；未冻结不得开标。
3. **备样本**：按 §4 的 24 个文件名从抽样框复制到只读工作目录，逐字节核对 sha256；按 §5 打 `dev`/`acceptance` 标，页序对 A/B 随机化。
4. **独立标注**：A、B 各自按页标题干框与题数；不得看对方结果或模型中间结果；产出按 v2 页级 schema（`file/subject/layout/questionCount/boxes/labelers/strata`）。
5. **一致性与分歧**：S3 报原始一致率 + 机会校正统计量 + 逐页题数一致率 + 逐题 IoU≥0.5；S4 逐页列 A≠B，不合并；C 按指南裁定并写 `adjudication` + `changes[]`。
6. **冻结 v2**：canonical 序列化 → `split_golden_v2.json` + `.sha256` 旁车 → C1–C7 → 负控（改数字/等级/摘要各一次必红）。
7. **验收集单次**：v2 冻结后才跑 `acceptance`；结果原样记录，不得回头改标注/阈值。
8. **门后自动化**：先取 4C③ 产品裁定，再把启用条件固定为 `gate.passed && hardRedLines.fragmentedQuestions.satisfied`；四项独立阻断（held-out、G2/G4 等级、样本可外推、产品裁定）逐条登记。

### 9.2 状态表

| # | 项 | 状态 |
|---|---|---|
| U1 | 两名人工标注员（A/B）与裁定员 C 的身份与独立性 | **不存在**；本会话无任何人工标注 |
| U2 | 指南冻结与 3 正例/3 反例边界锚 | **未执行** |
| U3 | v2 金标文件、旁车、C1–C7、负控 | **未生成** |
| U4 | 人工一致性、裁定质量、人工金标正确率 | **UNVERIFIED**（无产物） |
| U5 | `heldOutUnusedForTuning=true` 的真伪 | **不可机检**（协议自证字段） |
| U6 | 24 页里是否每页都能解码、是否有重复字节 | 本会话只做了存在性/字节 sha；解码与内容检查属 S2 |
| B1 | 执行本计划需要的人 | **BLOCKED**：需 ≥2 名独立人工标注员 + 1 名非标注员裁定者（协议 §4）；解除见 §9.1 |
| B2 | 门后自动化开关 | **BLOCKED**：需 4C③ 产品裁定与 §7 的 AND 条件；解除见 §9.1 第 8 步 |

**本文件不构成"人工金标已完成"或"门已通过"**：门仍是 FAIL，v1 仍是 G2，JS 仍不广告，`availableMethods` 未动。
