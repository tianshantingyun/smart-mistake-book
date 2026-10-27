# tools/split_golden —— 切分金标门工具链（AUD-08）

> 门值、五类指标的分母/阈值/样本量/统计区间/严重度与**未实现项的如实登记**见
> [`GATE.md`](GATE.md)（门的独立载体）。本文件只讲工具链怎么跑。

## 文件与职责

| 文件 | 职责 |
|---|---|
| `tables/split_golden_v1.json` + `.sha256` | **冻结金标 v1**：7 页 / 16 框；代理金标（模型标注、两标注员一致、无人工专家）；sha `f9e92c47…` |
| `validate_split_golden.py` | 冻结字节与 schema 机械校验 C1–C6（sha 旁车、canonical 逐字节、CRLF=0、框合法、页不重复、页数≥7、evidenceGrade 冻结常量） |
| `export_split_eval.py` | 设备 DB 快照 → `build/split-eval/app-split.json`（R4 §3.7 指名的生产者；只读 DB、同快照同 sha、页名三级解析逐页标 `nameSource`、拒重复页名/非法框） |
| `measure_split_accuracy.py` | 度量（口径 v2）：①题数完全一致率 ②IoU≥0.5 ③切残率 + 逐页表；`--json` 机器口径；同输入重跑逐字节一致 |
| `check_split_gate.py` | **门判定器**：冻结校验 → 度量 → 0.9/0.05 闭边界判定 → 报告/证据落盘；exit 0/1/2/3；不切任何产品开关 |
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
```

测试：`python -m unittest discover -s tools/tests -t tools -k split_gate`

## 设备 DB 快照怎么取（设备轨执行；本工具轨未实跑，步骤为文档性指引）

1. 真机/模拟器上走完"拍摄/导入 → 开始分题"后，用 Android Studio Device File Explorer
   或 `adb shell su 0 cat`（debug 包可 `adb exec-out run-as <pkg> ...`）取出
   `databases/<应用DB>.sqlite`（连同 `-wal`/`-shm` 一并取出或先做 checkpoint）；
2. `export_split_eval.py --db <快照>` 导出（DB 只读打开，字节不变）；
3. 页名配对：DB 内 split 表**不含原始页文件名**（4C 实测，stage1 §4：页名靠
   `files/source-assets` 图片与原数据集的会话外感知匹配得出）。把匹配结果写成
   `--page-map` 映射文件（`{"<job_id>": {"<page_index>": "<IMG....jpg>"}}`）再导出；
   无映射时页名落到 `job:<id>#p<page>` 合成名，不会与金标配对（如实标记，不猜）。

## 冻结与版本纪律

- v1 冻结字节不可改：任何编辑（含缩进/键序/结尾换行）都会被 C1 判红；
- 升级金标 = **新文件 v2 + 新旁车**（冻结 v1 不动），同时补 GATE.md §2 的
  标注来源/复核与 train/dev/acceptance 切分登记；
- 阈值 0.9/0.05 改值：先裁定并留旧值对照（`THRESHOLDS.source` 随报告落盘），
  不得为让门变绿改阈值/口径/分母/金标。
