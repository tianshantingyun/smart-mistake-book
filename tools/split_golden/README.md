# tools/split_golden —— 切分金标门工具链（AUD-08）

> 门值、五类指标的分母/阈值/样本量/统计区间/严重度与**未实现项的如实登记**见
> [`GATE.md`](GATE.md)（门的独立载体）。本文件只讲工具链怎么跑。

## 文件与职责

| 文件 | 职责 |
|---|---|
| `tables/split_golden_v1.json` + `.sha256` | **冻结金标 v1**：7 页 / 16 框；代理金标（模型标注、两标注员一致、无人工专家）；sha `f9e92c47…` |
| `validate_split_golden.py` | 冻结字节与 schema 机械校验 C1–C6（sha 旁车、canonical 逐字节、CRLF=0、框合法、页不重复、页数≥7、evidenceGrade 冻结常量） |
| `export_split_eval.py` | 设备 DB 快照 → `build/split-eval/app-split.json`（R4 §3.7 指名的生产者；只读 DB、同快照同 sha、页名三级解析逐页标 `nameSource`、拒重复页名/非法框） |
| `measure_split_accuracy.py` | 度量（口径 v2）：①题数完全一致率 ②IoU≥0.5 ③切残率 + 逐页表；`--json` 机器口径（逐页行透传 `appNameSource`/`appJobId`/`appPageIndex`，文件级 `app.source`）；同输入重跑逐字节一致 |
| `check_split_gate.py` | **门判定器**：冻结校验 → 度量 → 0.9/0.05 闭边界判定 → 报告/证据落盘；证据 §5 逐页表含"来源"列；exit 0/1/2/3；不切任何产品开关 |
| `GATE.md` | 门说明（独立载体） |
| `test_split_gate.py`（在 `tools/tests/`） | 机制测试：阈值边界、报告形状、冻结校验、导出器（CI discover 覆盖） |

## 数据流

```
设备 DB 快照（sqlite，只读）
   │  export_split_eval.py --db <快照> [--page-map <map.json>]
   ▼
build/split-eval/app-split.json（页名+框+溯源；无溯源块的导出，设备来源 UNVERIFIED）
   │  check_split_gate.py（内部：validate C1–C6 → measure --json → 判门）
   ▼
门判定 exit 0/1/2/3 + --report-json + --evidence-out（R4 模板精神）
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

# 4) 判门（红绿判定器 + 证据落盘）
python tools/split_golden/check_split_gate.py \
    --report-json build/split-eval/gate-report.json \
    --evidence-out docs/research/evidence/<批目录>/step-aud08-<时点>.md

# 5) 自检（只读；当前历史导出的期望：FAIL + 自动化关闭）
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

2. `export_split_eval.py --db <快照>` 导出（主库只读打开、sha 不变；同快照复跑逐字节一致）。
3. 页名配对：DB 内 split 表**不含原始页文件名**（4C 实测，stage1 §4：页名靠
   `files/source-assets` 图片与原数据集的会话外感知匹配得出）。把匹配结果写成
   `--page-map` 映射文件（`{"<job_id>": {"<page_index>": "<IMG....jpg>"}}`）再导出；
   无映射时页名落到 `job:<id>#p<page>` 合成名，不会与金标配对（如实标记，不猜）。
4. **已知限制（2026-10-29 实测，未修；批次 32 T6 登记 KD-81）**：`source.db.sha256` 只覆盖主库字节，
   **不覆盖 WAL**。同一主库 + 不同 WAL 内容会得到不同的导出，却报告同一个
   `source.db.sha256`（受控实验：两快照主库 sha 同为 `946b896c…`，带 WAL 导出 1 页 2 框、
   无 WAL 导出 0 页）。设备库默认 WAL 模式，故该 sha 不能单独充当"快照身份"；
   复现时应把 `-wal`/`-shm` 与主库一起保存/比对，或先显式 checkpoint 再取库。
   **检测（复跑前必跑）**：对主库与存在的边车一起记账；`-wal` 非空却未进 manifest（或三件
   mtime 不同）即判"快照身份不完整"，此时报告里的 `source.db.sha256` 只能读作主库字节摘要：

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
- 阈值 0.9/0.05 改值：先裁定并留旧值对照（`THRESHOLDS.source` 随报告落盘），
  不得为让门变绿改阈值/口径/分母/金标。
