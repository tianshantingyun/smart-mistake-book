# tools/split_golden —— 切分金标门工具链（AUD-08）

> 门值、五类指标的分母/阈值/样本量/统计区间/严重度与**未实现项的如实登记**见
> [`GATE.md`](GATE.md)（门的独立载体）。本文件只讲工具链怎么跑。

## 文件与职责

| 文件 | 职责 |
|---|---|
| `tables/split_golden_v1.json` + `.sha256` | **冻结金标 v1**：7 页 / 16 框；代理金标（模型标注、两标注员一致、无人工专家）；sha `f9e92c47…`；等级 **G2 但不可复算**（见 GATE.md §2.1） |
| `validate_split_golden.py` | 冻结字节与 schema 机械校验 C1–C7（sha 旁车、canonical 逐字节、CRLF=0、框合法、页不重复、页数≥7、evidenceGrade 冻结常量；C7 = G0–G5 等级与其登记字段） |
| `export_split_eval.py` | 设备 DB 快照 → `build/split-eval/app-split.json`（R4 §3.7 指名的生产者；只读 DB、**快照身份覆盖 `-wal`**、读事务钉住一个快照、导出期间 db/`-wal` 漂移即 exit 2、页名三级解析逐页标 `nameSource`、拒重复页名/非法框；**v2 生产者字段见 §"app-split v2"**） |
| `measure_split_accuracy.py` | 度量（口径 v2）：①题数完全一致率 ②IoU≥0.5 ③切残率 + 逐页表；`--json` 机器口径（逐页行透传 `appNameSource`/`appJobId`/`appPageId`/`appJobQuestionCountMatches` 等来源，并给出**自算**的 `goldBoxesSha256`/`appBoxesSha256`/`fragmentPiecesByGoldIndex`/`unmatchedAppBoxCount`；文件级 `app.source` 含 `dbUserVersion`/快照身份/载荷/生产者）；同输入重跑逐字节一致 |
| `check_split_gate.py` | **门判定器**：冻结校验 → 度量 → 0.9/0.05 闭边界判定 → 报告/证据/裁定落盘；报告含 `golden.evidenceGradeLevel`、`hardRedLines`、`scope`；exit 0/1/2/3；不切任何产品开关 |
| `rulings.py` | 逐页裁定文档构建/落盘：`--rulings-out <dir>` → `<dir>/<runId>.json`（一 run 一份、append-only、落盘前自检）；门不可判时**不生成** |
| `validate_rulings.py` | `rulings.json` 机械校验 R1–R9（六条不变量 + 内容摘要 + 裁定人语义）；负控：改数/改 kind/改摘要必红 |
| `GATE.md` | 门说明（独立载体；门值/红线/等级/范围/禁止清单） |
| `test_split_gate.py`（在 `tools/tests/`） | 机制测试：阈值边界、报告形状、冻结校验、导出器、快照身份、溯源透传、**rulings 负控、v2 生产者、G0–G5 等级、门红无 PASS 误印**（CI discover 覆盖） |

## 数据流

```
设备 DB 快照（sqlite，只读）
   │  export_split_eval.py --db <快照> [--page-map <map.json>]
   ▼
build/split-eval/app-split.json（schemaVersion=2：页名+框+v2 溯源；无溯源块的旧导出：设备来源 UNVERIFIED）
   │  check_split_gate.py（内部：validate C1–C7 → measure --json → 判门）
   ▼
门判定 exit 0/1/2/3
   ├── --report-json   确定性报告（含等级/硬红线/范围声明）
   ├── --evidence-out  R4 模板精神证据 md
   └── --rulings-out   <dir>/<runId>.json（逐页裁定，append-only；validate_rulings.py 校验）
```

门不过 → **门后自动化不启用**（裁定 37 / 4C③）；门过也不由工具启用任何东西。

## 常用命令（仓库根）

```bash
# 1) 金标冻结校验（任何度量/判门的前置）
python tools/split_golden/validate_split_golden.py                      # exit 0 = PASS

# 2) 产出应用导出（需设备 DB 快照；页名映射来自 4C 式会话内核验，见下）
python tools/split_golden/export_split_eval.py --db snapshot.sqlite --sidecar

# 3) 只度量（人读）
python tools/split_golden/measure_split_accuracy.py                     # 只报数，exit 0 ≠ 门过

# 4) 判门（红绿判定器 + 报告/证据/逐页裁定）
python tools/split_golden/check_split_gate.py \
    --report-json build/split-eval/gate-report.json \
    --evidence-out docs/research/evidence/<批目录>/step-aud08-<时点>.md \
    --rulings-out build/split-eval/rulings

# 5) 裁定文件自检（R1–R9；改一处数字/kind/摘要即红）
python tools/split_golden/validate_rulings.py \
    --file build/split-eval/rulings/<runId>.json

# 6) 自检（只读；当前历史导出的期望：FAIL + 自动化关闭）
python tools/split_golden/check_split_gate.py    # exit 1 = 门 FAIL（① 4/7）；automation.enabled=false
```

测试：`python -m unittest discover -s tools/tests -t tools -k split_gate`

## 逐页裁定的"来源"列

证据 §5 每页写 `file | 来源 | 金标题数 | 应用框数 | ①一致 | IoU≥0.5 | 切残`。"来源"取自
应用导出的页级 `nameSource`+`jobId`（由 `export_split_eval.py` 写入）：

- 有溯源块：如 `batch_page_source_uri（split:<job>#p<page>）` / `page_map（…）`；
- 应用缺该页：`应用缺页（0 框计入）`；
- 导出无溯源块（4C 历史导出的形态）：`UNVERIFIED（导出无溯源块）`——**不以文件名反推来源**。

来源只做记录，不进任何分子/分母/阈值；门值与口径不受影响。

同一批逐页数据还会写进 `--rulings-out` 的 `rulings.json`（append-only）：
每页含 `pageId/pageIdKind`、`appRef`、`gold/app.boxesSha256`、全部 `diff` 数值
（含 `iouByGoldIndex`/`unmatchedAppBoxCount`/`fragmentPiecesByGoldIndex`）、
`ruling.verdict/redLineHit/decidedBy/evidenceGradeLevel`。`validate_rulings.py` 校验
R1–R9；负控（改数/改 kind/改摘要）在 `tools/tests/test_split_gate.py` 有对应用例。
注意 `summary.missingInApp` 与指标 ① 的分子口径不同（单题页缺页时 ① 仍记一致），
引用时写明口径（GATE.md §3）。

## app-split v2 生产者字段（2026-11-02）

`schemaVersion=2`，**只做加法**（`pages[].file/questions` 不动，度量/门脚本照常消费）：

| 字段 | 消灭的具体失败 |
|---|---|
| `source.producer{file,sha256,contract}` | 同 `schemaVersion` 的两版脚本输出无法区分，旧导出被当新口径 |
| `source.db.userVersion` | 不知道导出产自 schema 70/71/72/73；切分表迁移后跨版本比较无意义 |
| `source.snapshot.mode` | 分不清"三件原样拷贝"与"逻辑快照"（身份语义不同） |
| `pages[].pageId` + `pageIdKind` | 合成页名（`job:<id>#p<n>`）与真实文件名混在一列，join 只能靠猜 |
| `pages[].job{jobId,sourceUri,sourceKind,sourceFingerprint,status,pageCount,questionCount,createdAt,updatedAt}` | 同一文件名可能来自不同 job，事后不可区分 |
| `pages[].integrity{jobQuestionCountMatches,jobPageCountMatches}` | job 记 6 题、导出行 3 条时**静默通过**（只按行分组） |
| `pages[].boxesSha256` | 裁定只绑文件名；同名页换了框仍复用旧裁定 |

- `db.userVersion` 在**同一读事务内**用 `PRAGMA user_version` 读取；只记录，不据此拒绝导出。
- `integrity=false` **不 exit**（生产数据可能合法缺页），由门报告的
  `appIntegrityMismatchPages` 与证据 §6 显式列出。
- 不加墙钟：任何时间字段都会让"同快照两次导出"不再逐字节一致。
- 真设备库实测（2026-11-02）：4 台在线设备中 **2 台装有本应用**——
  `emulator-5562`（schema **72**，导出 sha `fa0ba8ae…`）与 `emulator-5596`（schema **71**，
  导出 sha `02cec6f0…`，`tmp/aud08-db-5596-2026-11-02/app-split.json`）；两者的
  `split_import_job`/`split_import_question` 均 **0 行**
  → 导出 **0 页**；同一快照复跑**逐字节一致**（5562 实测）。另 2 台未安装
  （`run-as: unknown package`）。"job/integrity 字段在真机有分题数据时的取值"仍
  **UNVERIFIED**（夹具用例只证明字段生成正确）。

## 设备 DB 快照怎么取（2026-10-29 批次 31 在真设备库上实跑，命令可复用）

1. 真机/模拟器上走完"拍摄/导入 → 开始分题"后，debug 包用 `run-as` 逐文件取出。
   **三件必须取自同一时刻**；只取主库而不取 `-wal` 会漏掉未 checkpoint 的已提交行
   （2026-10-29 受控 WAL 快照实测：2 行只在 WAL 内，带 WAL 导出读到 2 框，仅主库导出 0 框）：

   ```bash
   adb -s <serial> exec-out run-as com.tingyun.smartmistakebook.offline \
       cat databases/smart-mistake-book.db     > snapshot.db
   adb -s <serial> exec-out run-as com.tingyun.smartmistakebook.offline \
       cat databases/smart-mistake-book.db-wal > snapshot.db-wal
   adb -s <serial> exec-out run-as com.tingyun.smartmistakebook.offline \
       cat databases/smart-mistake-book.db-shm > snapshot.db-shm
   ```

2. `export_split_eval.py --db <快照>` 导出（主库与 `-wal` 只读打开、字节不变；`-shm` 由
   SQLite 重建/改写、不进身份；同一快照复跑逐字节一致）。
3. 页名配对：DB 内 split 表**不含原始页文件名**（4C 实测，stage1 §4：页名靠
   `files/source-assets` 图片与原数据集的会话外感知匹配得出）。把匹配结果写成
   `--page-map` 映射文件（`{"<job_id>": {"<page_index>": "<IMG....jpg>"}}`）再导出；
   无映射时页名落到 `job:<id>#p<page>` 合成名，不会与金标配对（如实标记，不猜）。
4. **快照身份（KD-81 已修；2026-10-29 批次 34 T5）**。导出文档现在有两条身份，**不许互相顶替**
   （口径与实测见 `docs/research/2026-10-29-snapshot-identity-wal-and-r8.md` §2–§4）：

   | 字段 | 口径 | 覆盖 | 不覆盖 |
   |---|---|---|---|
   | `source.db.sha256` | **旧字段，保留原位原义**：主库字节摘要 | 主库文件字节 | `-wal`、物理布局 |
   | `source.snapshot.identitySha256` | 产物身份 `inputs-v1` = `sha256(canon({db, wal}))` | 主库与 `-wal` 的字节 + 存在性 | `-shm`、mtime、路径名 |
   | `source.payload.sha256` | 内容身份 = `sha256(canon(pages))` | 被度量的每一页每一框 | DB 物理布局/`page_size`/是否 checkpoint |

   - 同一主库 + 不同 `-wal` ⇒ **`identitySha256` 必不同**（受控实验：主库 sha 同为
     `01c6125b…`，一次 1 框、一次 2 框，身份分别 `10b99adc…` / `b095ae9c…`）。
     `source.db.sha256` 仍会相同——它**不是**快照身份，只能读作"主库字节摘要"。
   - `-shm` **不进身份也不进输出**：只读导出本身会创建/改写它（实测：导出前不存在，
     第一次导出后出现，sha `663f9609…`），把它写进输出会让"同一快照跑两遍"不再逐字节一致。
   - **0 字节 `-wal` 按"无 WAL"归一**：只读打开一个 WAL 模式的库会自建 `<db>-wal`（0 B）
     与 `<db>-shm`；不归一的话第一次导出会被自己的读副作用判成 `快照一致性`（exit 2）。
     0 字节 WAL 不含帧，`wal_checkpoint(TRUNCATE)` 之后就是该形态。
   - 导出内部是**一个读事务**（显式 `BEGIN`），主查询与逐页页名查询不会跨 WAL 快照；
     导出前后各取一次输入清单，`db`/`-wal` 变了就 `exit 2`（stdout `快照一致性`），
     且该校验**先于写盘**，坏导出不会落盘。
   - 工具**不能**证明"曾经有 `-wal` 被漏拷"：`walPresent=false` 只如实记录"此刻没有
     含帧的 `-wal`"（缺失或 0 字节）。所以步骤 1 的取库纪律（三件同一时刻）仍然必须遵守。
   - 复跑前的手工检测仍可跑（现在只作旁证，不再是唯一防线）：

   ```bash
   SNAP=snapshot.db
   for f in "$SNAP" "$SNAP-wal" "$SNAP-shm"; do
     [ -e "$f" ] && sha256sum "$f" && stat -c '%n %s %y' "$f"
   done
   ```


## 冻结与版本纪律

- v1 冻结字节不可改：任何编辑（含缩进/键序/结尾换行）都会被 C1 判红；
- 升级金标 = **新文件 v2 + 新旁车**（冻结 v1 不动），同时补 GATE.md §2 的
  标注来源/复核与 train/dev/acceptance 切分登记；
- **v2 起等级是结构化字段**：`evidenceGradeLevel` 必填，`validate_split_golden.py` 的 C7
  逐级检查该等级要求的登记字段（G1 单模型 / G2 两模型+agreement / G3 人工复核 /
  G4 双人+裁定 / G5 加 dev/acceptance 切分）。v1 只能按键值映射为 G2 且
  `recomputable=false`——不得通过给 v1 加字段"提升等级"（C2 会红）；
- 阈值 0.9/0.05 改值：先裁定并留旧值对照（`THRESHOLDS.source` 随报告落盘），
  不得为让门变绿改阈值/口径/分母/金标。
