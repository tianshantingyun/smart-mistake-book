# 批次 45 基线核对（2026-11-06）

> 执行者：基线核对员（只读，不跑 Gradle）。
> 输入：`.zcode/plans/plan-2026-11-06-batch-45-acceptance-close.md`、
> `docs/research/2026-11-06-batch-44-record.md` §0/§9。
> 本文只记录**本轮亲手实读/实跑**得到的基线值，并标出计划文本与基线不符之处。
> 凡未在本轮执行的检查（Gradle 双门、设备 instrument）一律标 **未跑**，不当作通过。

---

## 0. 本轮实跑的命令（可重跑）

| # | 命令 | 用途 |
|---|---|---|
| C1 | `git rev-parse HEAD` / `git rev-parse origin/main` | 源一致性 |
| C2 | `git status --porcelain \| wc -l`、`--porcelain -uall \| wc -l`、`git diff --cached --name-only \| wc -l` | 工作树漂移 |
| C3 | `git diff --numstat` / `git diff -w --numstat` | 判定漂移是真内容还是行尾噪声 |
| C4 | `sha256sum docs/acceptance/ledger.md`、`stat -c '%s %y'`、`wc -l` | 台账身份 |
| C5 | `python -I -c` 逐行解析 `ledger.md` | 1200 行 / ID 完整性 / 分域计数 |
| C6 | `grep -nE '^\s*CMD\s*:' docs/research/evidence/2026-11-05-batch-44/e-domain/*.cmd.log` | E 域占位真实位置 |
| C7 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md` | 验收报告默认门 |
| C8 | 同上 `--require-complete --out build/b45-baseline/report-complete.json` | 终裁门 |
| C9 | `python -I tools/ci/acceptance_lint.py` | 台账 lint |
| C10 | `python -I tools/ci/trace_requirements.py` | 需求追溯 gaps |
| C11 | `ls -la docs/acceptance/verdicts/`、`git ls-files docs/acceptance/verdicts` | 复核 JSON 是否真存在 |
| C12 | `"/c/Android/Sdk/platform-tools/adb.exe" devices` + `getprop` | 设备状态 |
| C13 | `grep -n` 读 `core/domain/.../BackupSelection.kt`、`git status --porcelain --` 同文件 | 6C-2 只读确认 |

工具输出落 `build/b45-baseline/{report-default.json,report-complete.json,lint.json,trace.json}`（gitignored，不作归档证据）。

---

## 1. 源一致性

| 项 | 实值 | 证据 |
|---|---|---|
| HEAD | `87604198feeef198d5dba7343b8c4692c5e8f937`（`87604198`） | C1 |
| `origin/main` | `87604198feeef198d5dba7343b8c4692c5e8f937` | C1（**与 HEAD 相同，批次 44 的“推送待网络恢复”已兑现**） |
| 最近提交 | `87604198 docs: 批次44 主会话收口…` → `ae1234dc` → `d6890611` | `git log --oneline -3` |
| 暂存 | **0** | C2 |
| 工作树 `porcelain` | **538** | C2 |
| 工作树 `porcelain -uall` | **3750**（= 报告内 `dirty_paths`） | C2 / C7 |
| 已跟踪文件改动 | **98 个文件，+2492 / −1141** | C3 |
| 其中忽略空白后仍不同 | **98 个文件，+2378 / −1027** | C3（**不是行尾噪声，是真内容改动**） |

**基线判读**：HEAD 干净可达，但工作树带着 98 个已跟踪文件的**未提交真实改动**继续推进。
本轮所有 PASS/FAIL 证据都绑在 `head=87604198` + `dirty_paths=3750` 这棵**脏树**上——
报告 JSON 已如实记录该字段（`build/b45-baseline/report-default.json` 的 `head`/`dirty_paths`）。
任何后续“发版就绪”结论必须说明它来自脏树，不能只引 HEAD。

**热区只读确认（未改，均与本批无关）**：

- `core/domain`：1 改（`TutorJudgedReview.kt`）+ 1 未跟踪锁 → 2 条；`core/ui`：1 未跟踪锁。
- `feature`：1 改测试 + 6 未跟踪锁 → 7 条；`docs/current`：**7 个 M**。
- `settings.gradle.kts`、根 `build.gradle.kts`、`gradle/libs.versions.toml`、App 清单：**0 改**。
- 计数与批次 44 记录 §0.2 完全一致（2 / 1 / 7 / 7）→ **本批未新增热区漂移**，属既有历史脏树。

---

## 2. 台账身份与 1200 行复算

| 项 | 实值 | 证据 |
|---|---|---|
| sha256 | `02ba530745ed928c139943a6c3a3ece48ae0f0b3d1c32031b8000e38de397e65` | C4 |
| 大小 / mtime | 460,671 B / `2026-11-05 07:31:26.848580800 +0800` | C4 |
| 物理行 | 1298 | `wc -l` |
| 数据行 | **1200**（6 列，列数分布 `{6: 1200}`） | C5 |
| ID 唯一性 | **1200 唯一，0 重号** | C5 |
| ID 连续性 | 每域 `1..N` **全部连续**，无缺号 | C5 |

**台账 sha 与批次 44 记录 §0.2 的 `02ba5307…` 一致，且自批次 44 起未被改写（mtime 仍是 07:31:26）。**

### 2.1 分域计数（C5 逐行复算，非读 summary）

| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 台账行号 |
|---|---:|---:|---:|---:|---:|---|
| D0 | 100 | 0 | 0 | 0 | 100 | 32–131 |
| A | 90 | 65 | 0 | 1 | 24 | 137–226 |
| B | 90 | 88 | 0 | 0 | 2 | 232–321 |
| C | 90 | 77 | 1 | 0 | 12 | 327–416 |
| D | 90 | 81 | 0 | 9 | 0 | 424–513 |
| E | 90 | 80 | 0 | 0 | 10 | 519–608 |
| F | 90 | 55 | 2 | 10 | 23 | 614–703 |
| G | 90 | 82 | 0 | 2 | 6 | 709–798 |
| H | 90 | 66 | 5 | 12 | 7 | 804–893 |
| I | 90 | 53 | 0 | 5 | 32 | 904–993 |
| J | 90 | 21 | 13 | 34 | 22 | 999–1088 |
| FIX | 100 | 0 | 0 | 0 | 100 | 1094–1193 |
| GATE | 100 | 0 | 0 | 0 | 100 | 1199–1298 |
| **合计** | **1200** | **668** | **21** | **73** | **438** | — |

与批次 44 记录 §1.1 **逐格一致**（四项合计 1200，无“other”值、无未登记域）。

---

## 3. 门禁三件套实值（本轮实跑）

### 3.1 `acceptance_report`（C7，默认门）

```
EXIT=1
total=1200  counts={PASS:668, FAIL:21, BLOCKED:73, UNVERIFIED:438}
state=FAIL   ok=False
ledger_sha256=02ba530745ed928c139943a6c3a3ece48ae0f0b3d1c32031b8000e38de397e65
head=87604198feeef198d5dba7343b8c4692c5e8f937    dirty_paths=3750
pass_with_review=0      pass_without_review=668
violations=101          唯一代码 PLACEHOLDER_CMD（101 条 / 80 个唯一 id，全部 E 域）
acceptance_complete=False
```

### 3.2 `acceptance_report --require-complete`（C8，终裁门）

```
EXIT_COMPLETE=1
violations=103 = PLACEHOLDER_CMD 101 + NOT_ACCEPTANCE_COMPLETE 1
               + PASS_WITHOUT_REVIEW 1
NOT_ACCEPTANCE_COMPLETE: UNVERIFIED=438 FAIL=21 BLOCKED=73
PASS_WITHOUT_REVIEW    : 668 个 PASS 行没有 review 证据
```

### 3.3 `acceptance_lint`（C9）

```
LINT_EXIT=0   violations=0   warnings=29   ok=True
```

**注意**：lint exit 0 **不等于** E 域干净。lint 不检查日志文件内的 `CMD:` 占位
（该项由 report 的 `PLACEHOLDER_RE` 负责），两者覆盖面不同，不得互相顶替。
29 条 warning 全部是 `EVIDENCE_REUSED`（一个证据文件被多行 PASS 共用），**批次 44 记录未登记此项**，
见 §6.3。

### 3.4 `trace_requirements`（C10）

```
TRACE_EXIT=1   gaps=60   ok=False
```

60 条 gaps 的**逐条构成**（本轮按 `detail` 聚类，非读 summary）：

| 缺口目标 | 条数 |
|---|---:|
| `docs/acceptance/verdicts/FIX-review.json`（不存在） | 20 |
| `docs/acceptance/verdicts/GATE-{A…J}-{independent,adversarial,main}.json`（30 个文件全不存在） | 30 |
| `方法声明设备执行，但无 device 证据`（无路径，10 行） | 10 |
| **合计** | **60** |

即“50 条缺证据路径 + 10 条缺设备证据”与批次 44 记录 §9.2 的说法一致；本轮把它落到**具体文件名**。

---

## 4. E 域占位命令：真实位置与行号

### 4.1 台账里**没有**占位符

C5 对 1200 行的 `方法`（第 4 列）与 `证据`（第 5 列）做全量扫描：

| 模式 | 命中域 |
|---|---|
| `<...>`（尖括号占位，report 工具判据） | **0** |
| `...`（三连点） | 仅 `C-050`（1 行） |
| `*xxx*`（globs 通配类名） | 仅 FIX **32 行** |
| `…`（全角省略号） | 0 |

**结论：计划 §1 WS1 第 1–10 条“把 `<E-domain classes>` 占位命令换成逐字真实命令”在台账层无对象可改。**
E 域 80 个 PASS 行的 `方法` 列已经是带全限定类名的真实命令（例：台账 `519` 行 E-001
`./gradlew --offline -q :core:domain:test --tests com.tingyun.smartmistakebook.core.domain.StoredRatingBasisTest`）。

### 4.2 占位符在**日志文件的 `CMD:` 行**里（4 处，均在文件第 1 行）

C6 实读（`grep -nE '^\s*CMD\s*:'`）：

| 文件 | 行 | 内容 |
|---|---:|---|
| `docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log` | **1** | `CMD: ./gradlew --offline -q :core:data:testDebugUnitTest --tests <E-domain study classes>` |
| `docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-domain-jvm.cmd.log` | **1** | `CMD: ./gradlew --offline -q :core:domain:test --tests <E-domain classes incl StudyDayMathTest>` |
| `docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe.cmd.log` | **1** | `CMD: java -cp <probe out + module classes + kotlin-stdlib> EDomainProbe` |
| `docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe-mutate.cmd.log` | **1** | `CMD: java -cp <probe out + module classes + kotlin-stdlib> EDomainProbe --mutate` |

### 4.3 80 个违规行与 3 个日志的对应（C5 + C7 交叉）

| 日志 | 被引用行数 | 涉及 verdict | 台账行号 |
|---|---:|---|---|
| `e-domain-data-jvm.cmd.log` | 56 | 全 PASS | 528–608 |
| `e-domain-domain-jvm.cmd.log` | 30 | 全 PASS | 519–608 |
| `e-domain-probe.cmd.log` | 15 | 全 PASS | 521–607 |
| `e-domain-probe-mutate.cmd.log` | **0**（无台账引用，仍含占位） | — | — |

并集 = **80 个唯一 id**（`E-001`@519 … `E-090`@608），与报告 `violations` 的 80 个 id 完全吻合；
101 条违规 = 同一行被 1–3 个占位日志重复计入。
**每一行违规都至少引用上述 3 个日志之一，没有任何一行例外。**

### 4.4 对 WS1 的影响（基线结论）

- 真正的修复面是 **3 个日志（+1 个无引用的 probe-mutate 日志）**，不是 80 行台账。
- 台账行本身**不需要**改命令；若改了，反而与日志不一致。
- 重新实跑并重写 3 个日志的 `CMD:` 行即可让 `PLACEHOLDER_CMD` 归零——**前提**是日志里
  的 exit 0 与 XML 结果确实来自该命令，否则就是把占位换成了另一种假绿。
- E 域 10 条 UNVERIFIED（E-007/016/025/034/043/052/061/070/079/086，台账 525–604）
  是**并发/DST 未测**的人类残余，证据只有 `file:`，与占位无关，不因重录日志而变绿。

---

## 5. D0 / FIX / GATE：300 行的真实结构

### 5.1 「空 verdict」在台账层并不存在

C5 全量统计：**verdict 为空或为 `-` 的行 = 0**。1200 行每行都有一个字面 verdict。

真正空的是**证据列**（第 5 列 = `-`）：

| 域 | 行数 | verdict 分布 | 证据为 `-` | 方法为 `-` |
|---|---:|---|---:|---:|
| D0 | 100 | 全 UNVERIFIED | **100** | 0 |
| FIX | 100 | 全 UNVERIFIED | **100** | 0 |
| GATE | 100 | 全 UNVERIFIED | **100** | 0 |
| 其他 | 8（G 域） | 6 UNVERIFIED + 2 BLOCKED | 8 | 0 |

**全库证据为 `-` 的行 = 308**（300 + G 域 8）。

> **计划文本纠正**：计划 §1 WS2 第 23 条「D0 100 行全部 verdict 非空」、§3「台账 1200 行无空 verdict」
> 描述的缺口**不是空 verdict**，而是 300 行**有 verdict（UNVERIFIED）但零证据**。
> 判定标准应写成“证据列非 `-` 且 ≥2 类独立证据”，否则照字面检查会全部假通过。

### 5.2 G 域 8 条空证据行（清单）

`G-016`@724、`G-036`@744、`G-072`@780、`G-080`@788、`G-081`@789、`G-087`@795（BLOCKED）、
`G-088`@796（BLOCKED）、`G-089`@797。

### 5.3 FIX / GATE 的方法列**已存在但不可直接复跑**

- FIX **32 行**用 `--tests *中文名*` 形式（例：台账 `1094` FIX-001
  `cmd: ./gradlew :core:data:testDebugUnitTest --tests *数据迁移*`；`1099` FIX-006 `*备份恢复*`）。
- GATE 行直接指向**不存在的**复核 JSON（例：`1199` GATE-001
  `review: docs/acceptance/verdicts/GATE-A-independent.json`）。

**门禁盲点（本批必须知道）**：`acceptance_report.py:60` 的占位判据只有 `<[^<>\n]{1,120}>`，
`*中文名*` 与 `...` **都不会被判为占位**。因此：
- 若 FIX 域按现状补日志，report 会给 PASS，而命令仍不可复现；
- `C-050`@376（`device: adb -s emulator-5584 shell am force-stop ...; am start; …`）当前就是 PASS，
  方法列含 `...`，工具不报。

这是**工具覆盖面缺口**，不是台账造假；批次 45 若声称 FIX/D0/GATE 已覆盖，必须显式说明这 33 行
（32 FIX + 1 C）按现有判据不可复跑。

---

## 6. `docs/acceptance` 结构清单与缺失产物

### 6.1 目录实况（C11 + `ls`）

```
docs/acceptance/
├── adjudication-draft.md        20,596 B  （草案，非终裁）
├── dod.md                        5,276 B
├── ledger.md                   460,671 B  （sha 02ba5307…）
├── ledger.schema.json            1,619 B
├── runbook.md                   11,478 B
├── verdicts.md                 353,119 B  （旧 run-id 投影）
├── evidence/G/ , evidence/H/
└── verdicts/                    ← 只有 README.md + _template.json
```

`git ls-files docs/acceptance` = **43 个文件**；`git status --porcelain -- docs/acceptance` = **空**
（该目录全部已入库、无未提交改动）。

### 6.2 三个「应存在但不存在」的产物

| 计划要求 | 实际 | 影响 |
|---|---|---|
| §1 WS6 第 78 条：终裁落 `docs/acceptance/adjudication.md` | **只有 `adjudication-draft.md`**，无 `adjudication.md` | 本批必须新建终裁文件，且内容只能是“签字”或“不通过”二选一 |
| §1 WS5 第 62–70 条：每域复核 JSON | `docs/acceptance/verdicts/` 仅 2 个骨架文件（README + `_template.json`），**0 个真实 verdict JSON** | `pass_with_review=0` 的直接原因；`trace_requirements` 50 条缺路径同源 |
| §1 WS6 第 73 条：重生成 `acceptance-report.json` | `docs/acceptance/` 下**无**该文件 | 见 §6.4 的陈旧报告风险 |

`GATE-001…GATE-100` 逐行引用的 30 个 `GATE-{A..J}-{independent,adversarial,main}.json`
与 `FIX-review.json` **一个都不存在**。

### 6.3 lint 的 29 条 `EVIDENCE_REUSED`（批次 44 未登记）

同一证据文件被多行 PASS 共用，独立性待确认。影响面最大的 6 条：

| 证据 | 被多少行 PASS 共用 |
|---|---:|
| `b-domain/b-migration-matrix.xml` / `.device.json` | 72 |
| `b-domain/b-backup-restore.xml` / `.device.json`、`b-backup-plan-gate.log` | 16 |
| `c-domain/c-node-compiler.log` | 31 |
| `c-domain/c-visualdrawopspainterinstrumentedtest.{xml,device.json,log}` | 23 |
| `e-domain/e-domain-data-jvm.cmd.log` | 56 |
| `e-domain/e-domain-domain-jvm.cmd.log` | 30 |

**含义**：这 29 个文件每个都同时是几行到 72 行 PASS 的“唯一测试证据”。
在被证明“一次运行确实覆盖了所有这些检查项”之前，这些 PASS 的**独立性未成立**，
而 DoD §1.5 要的正是独立证据。

### 6.4 陈旧报告：盘上那个 `violations=0` 的报告不得引用

`docs/research/evidence/2026-11-06-batch-44/acceptance-report.json`（本轮实读）：

```
ledger_sha256 = e139b03e8b9a005af73d658333f7721907b7e6976718106934c00e096a3eb675  ← 不是当前台账
counts        = {PASS:670, FAIL:21, BLOCKED:73, UNVERIFIED:436}
violations    = 0
state         = None    exit_code = None   （该 schema 版本没有这两个字段）
```

**这是本批最容易踩的坑**：它同时**绑旧台账**、**报 0 违规**、**没有 exit 码**。
任何段落若引用“报告 0 违规 / 670 PASS”，都是引用 `e139b03e…` 的旧字节；
当前台账 `02ba5307…` 的实跑值是 §3.1 的 `exit=1 / 101 违规 / 668 PASS`。

---

## 7. 设备状态（C12）

`adb devices`：

```
emulator-5562   device
emulator-5584   device
```

| serial | API | ABI | Android |
|---|---:|---|---:|
| emulator-5562 | 34 | x86_64 | 14 |
| emulator-5584 | 34 | x86_64 | 14 |

- 两机**在线可用**（非 offline/unauthorized），状态与批次 44 的设备轨一致（`C-050`@376 即引用 `emulator-5584`）。
- **本轮未跑任何 instrument / instrumented test，未装 APK，未截图。**
  这两个设备上是否装着当前 HEAD 的 APK **未验证**（不能在未验证时假设“设备证据可用”）。
- `adb` 不在 PATH，需用 `/c/Android/Sdk/platform-tools/adb.exe`（`local.properties` 的 `sdk.dir=C:\Android\Sdk`）。
- 纪律提醒：本批仍禁 `:app:connectedLocalFirstDebugAndroidTest`。

---

## 8. 不可变清单只读确认（本轮未改任何一行）

| 项 | 本轮实读 | 结论 |
|---|---|---|
| 6C-2 `ALLOWED_MASKS` | `BackupSelection.kt:166` = `linkedSetOf(9, 13, 15)` | 未变；`8` 不在集合（不提供）、`0` 不在集合（禁止） |
| 6C-2 `isCreatable` | `BackupSelection.kt:90` = `isClosed && mask in ALLOWED_MASKS` | 未变 |
| 6C-2 `requireCreatable` | `BackupSelection.kt:136,140` 拒绝 `mask ∉ ALLOWED_MASKS` | 未变 |
| `BackupSelection.kt` 工作树 | `git status --porcelain` **空**（干净） | **零改动** |
| 6C-2 不变量测试 | `core/data/src/test/kotlin/.../core/data/backup/SixC2RulingInvariantTest.kt` 存在且已入库 | 沿用，不新增 |
| KD-76 / isolation / p09 / AUD-08 | `adjudication-draft.md:141`（R9）、`:165` 明写“本批未重裁/未改变” | **维持原判**，不得据本批 PASS 数回填 |

> 路径注记：批次 44 记录 §8 把不变量测试写成 `core/data/src/test/.../SixC2RulingInvariantTest.kt`（省略了
> `core/data/backup` 一级）；实际路径为上表值。

---

## 9. 计划 vs 基线的落差（批次 45 开工前必须知道）

| # | 计划文本 | 基线实际 | 处置 |
|---|---|---|---|
| G1 | §0「台账 sha `02ba5307…`；1200 行」 | **成立** | 无需动作 |
| G2 | §0「HEAD `87604198`（推送待网络恢复）」 | **HEAD 与 `origin/main` 均已 `87604198`** | 推送已完成 |
| G3 | WS1「E 域 80 行占位命令」 | 台账 0 占位；**占位在 3+1 个日志的第 1 行 `CMD:`** | 改日志，不改台账 |
| G4 | WS2 第 23 条 / §3「空 verdict 行」 | **0 个空 verdict**；300 行**零证据** + G 域 8 行 | 判据改为“证据非 `-`” |
| G5 | WS6 第 74 条「exit 0 可签字」 | 当前 **default exit=1、require-complete exit=1** | 目标只能靠真绿或 BLOCKED 清单 |
| G6 | WS5「`pass_with_review>0`」 | **0**，且 0 个 verdict JSON | 需新建 30+1 个 JSON |
| G7 | §3「台账 1200 行无空 verdict」 | 已满足，但**不构成通过** | 见 G4 |
| G8 | 计划未提 | 新增：lint **29 条 `EVIDENCE_REUSED`** | WS5 独立性必须回应 |
| G9 | 计划未提 | 新增：FIX **32 行 + C-050** 方法用 `*glob*`/`...`，**工具判不出** | WS3/WS4 需显式声明或换 FQCN |
| G10 | 计划未提 | 新增：盘上旧报告 `violations=0` 绑 `e139b03e…` | 明确禁止引用 |

### 9.1 本批开工时的先验结论（不是终裁）

在当前 HEAD + 当前台账下，**验收通过的先验概率为 0**，理由是可机械复算的：

1. `pass_with_review=0`、`pass_without_review=668`（C7）——DoD §1.5 硬不满足；
2. `PLACEHOLDER_CMD=101`（C7）——E 域证据链可复现性不成立；
3. 300 行零证据（§5.1）——D0/FIX/GATE 三域结构性未发生；
4. `trace_requirements` 60 gaps（C10）——30+1 个复核 JSON 与 10 条设备证据缺失。

这四项**每一项单独**就足以让 `--require-complete` exit 1。批次 45 若要把 exit 变成 0，
必须四项同时清零；否则只能走“可签字的 BLOCKED 清单 + 明确验收不通过”。

---

## 10. 本轮未做的检查（必须标 UNVERIFIED，不得当通过）

| 检查 | 状态 | 说明 |
|---|---|---|
| 单测门（12 模块） | **未跑**（本批纪律：Gradle 只在门禁阶段） | 仅确认 `build/batch44-unit.log` 存在，含 `GRADLE_EXIT=0`(240 行)、`GRADLE_EXIT_PHASE2=0`(296 行)；这是**批次 44 的日志**，不是本批证据 |
| 强门（`--rerun` androidTest 编译 + assemble） | **未跑** | `build/batch44-strong.log`、`build/batch44-strong-postfix.log` 存在，属批次 44 |
| 设备 instrument | **未跑** | 两机仅确认在线，未装 APK、未跑用例 |
| 6C-2 不变量测试实跑 | **未跑** | 只读确认测试文件存在与 `BackupSelection.kt` 干净 |
| 台账 schema 校验（`acceptance_init.py --check`） | **未跑** | 本轮用逐行解析替代（C5），结论只覆盖行数/ID/计数，**不覆盖 schema 语义** |

> 特别声明：批次 44 §9.1 的 exit=0 是**批次 44 的**实跑结果。
> 本批不得把它当作“当前 HEAD 的门已通过”——当前树有 98 个未提交改动，双门是否仍绿**未验证**。

---

## 11. 复算命令（任何人都能重跑上面每个数字）

```bash
# 源
git rev-parse HEAD; git rev-parse origin/main
git status --porcelain | wc -l; git status --porcelain -uall | wc -l
git diff --cached --name-only | wc -l
git diff --numstat | awk '{a+=$1;d+=$2} END {print a,d}'
git diff -w --numstat | awk '{a+=$1;d+=$2} END {print a,d}'

# 台账
sha256sum docs/acceptance/ledger.md
python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md; echo "EXIT=$?"
python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --require-complete --out build/x.json; echo "EXIT=$?"
python -I tools/ci/acceptance_lint.py; echo "EXIT=$?"
python -I tools/ci/trace_requirements.py; echo "EXIT=$?"

# E 域占位真实位置
grep -nE '^\s*CMD\s*:' docs/research/evidence/2026-11-05-batch-44/e-domain/*.cmd.log

# 复核 JSON 是否真存在
ls docs/acceptance/verdicts/
git ls-files docs/acceptance/verdicts

# 设备
/c/Android/Sdk/platform-tools/adb.exe devices
```

> 编码坑：`python -I` 会忽略 `PYTHONIOENCODING`，直接把 stdout 重定向到文件会得到 GBK 字节。
> 需要机器可读的 JSON 时用 `--out <path>`（工具内部按 UTF-8 写），不要靠管道。

---

## 12. 基线一句话

**台账是真的（1200 行、sha `02ba5307…`、计数 668/21/73/438 可逐行复算），门也是真的红（exit=1，101 占位 + 668 条零复核 + 300 行零证据 + 60 条追溯缺口）。**
计划对缺口的**定位有两处偏差**（占位在日志不在台账；空的是证据不是 verdict），本批按基线位置施工，不按计划字面施工。

---

<!-- 体量自查：wc -lc = 434 行 / 22,120 B（≤65,536 B）。本轮 Gradle 0 次执行。 -->

