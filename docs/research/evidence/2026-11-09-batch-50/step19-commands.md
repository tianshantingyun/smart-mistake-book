# 批次 50 · item 19 证据：命令与产物

日期：2026-11-09（本会话实跑）
范围：只涉 dev 16 页；**acceptance 8 页未跑任何几何/模型测量**（只读了金标元数据）。

## 1. 几何诊断探针（Python，numpy+Pillow）

```bash
cd "D:/smart mistake book"
python tools/layout_probe/probe_layout.py \
  --pages-dir "C:/Users/听云/Pictures/错题数据集" \
  --golden tools/split_golden/tables/split_golden_v2.json \
  --subset dev --no-auto-orient \
  --out docs/research/evidence/2026-11-09-batch-50/step19-layout-probe-dev.json
```

- 产物：`step19-layout-probe-dev.json`（150,703 B，sha256 前缀 `29bfbcc8…`）
- 确定性：同命令复跑 → 与上文件**逐字节一致**（本会话 `dev-final-rerun.json` 对比 `identical True`）
- 报告自带的身份绑定：`tool.sha256 = 694ec6bb2247e340…`（= 当前 `probe_layout.py`），
  `golden.sha256` = 当前 `split_golden_v2.json`（两者本会话复核一致）

## 2. Kotlin 参考实现对拍

```bash
# 先用探针导出 .gray 夹具（与 Python 同一次解码/缩放）
python tools/layout_probe/probe_layout.py --pages-dir "C:/Users/听云/Pictures/错题数据集" \
  --subset dev --no-auto-orient --dump-gray build/layout-probe/gray \
  --out build/layout-probe/dev-dump.json

# 编译 + 跑（kotlin-compiler-embeddable 2.3.21 取自 Gradle 缓存；不跑 Gradle）
bash tools/layout_probe/compile_and_run.sh
```

- 产物：`step19-kotlin-parity.txt`（逐页 python vs kotlin 对照）
- 结果：columns 一致 **16/16**；blocks 差值 ≤2 的页 **16/16**；hint 字符 **113–286**
- 差异来源：Python `np.convolve(...,'same')` 与 Kotlin 边缘钳位平滑在页首尾差 1–2 个样本

## 2.1 真实提示块样例（UTF-8 落盘）

```bash
PROBE_HINT_PAGE=IMG20260810005144 bash tools/layout_probe/compile_and_run.sh
```

- 产物：`step19-hint-05144.txt`（286 字符；Kotlin 侧用 `File.writeText(Charsets.UTF_8)` 落盘，
  绕开 Windows 控制台 CP936——直接 `>` 重定向会得到非 UTF-8 字节，本会话实测踩过）
- 研究文档 §3.3 引用的样例与该文件**逐字相同**（本会话用 Python `in` 比较确认）

## 3. 坐标系证据（overlay）

| 文件 | 说明 |
|---|---|
| `step19-overlay-IMG20260810004854.png` | 金标框画回像素：框与题干**不对齐**（该页金标在旋转 90° 框内） |
| `step19-overlay-IMG20260810011649.png` | 对照：框与题干对齐（同一批里正的例子） |
| `step19-gutter-05144.png` | 栏谷放大：可见透印/竖线与红笔横穿（结构墨阈值与谷区间隙合并的动机） |

overlay 由 `PIL` 直接在同一图像上绘制金标 `boxes`（0..1 归一 → 像素），无插值、无二次编码。

## 4. 未跑项（如实登记）

| 项 | 状态 | 原因 |
|---|---|---|
| LLM-only / LLM+hint 两臂 | **未跑** | 无 provider key；且属 item 21–22 的调参与一次性验收，不在本研究内消耗 |
| `check_split_gate.py` | **未跑** | 无新应用导出；跑它只会复算旧 4C 导出（与本文无关） |
| Gradle / 设备 | **未跑** | 计划要求 Gradle 只在串行阶段跑；本轮为纯 JVM |
