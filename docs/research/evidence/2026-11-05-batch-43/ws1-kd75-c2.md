# E-43-WS1-01：KD-75 C1–C8 逐条对账（设备轨 + 本轮独立 pypdf 重读）

| 字段 | 值 |
|---|---|
| evidence-id | `E-43-WS1-01` |
| convention-version | 1 |
| batch | 2026-11-05-batch-43 |
| step | 计划 §2 WS1 item 6/7/9（item 5 JVM 用例、item 10 台账回写另见边界） |
| gate-or-kd | KD-75 C1–C8；C8 已另立 KD-84；C2 的新覆盖缺口另立 KD-85 |
| verdict | **PARTIAL**：C1–C7 有设备实测（C2/C3 本轮另有独立 pypdf 重读）；**C2 的“模型解析文本”只覆盖 `MODEL_DOCUMENT_PARSE` provenance**，自然语言改卷产生的模型文本未覆盖（KD-85）；item 5 的 JVM 三类内容用例仍无逐例执行证据 |
| executed-at | 设备：2026-11-04 08:44–11:30（本机）；独立 pypdf 重读：2026-11-05 修复环 |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86`（工作树脏；本轮未改源码） |
| device-serial | `emulator-5584`（API 34）、`emulator-5570`（API 23） |
| apk-sha256 | app `9b832f96…d7e29e16`；export.test `08ea26417f746daddf0344c8b12433702628e25670f7ca80283b2e2374e165c0`（逐位见 `ws1-ws4-device-track.md:57-61`） |
| exit-code | `am instrument` `OK (7 tests)` ×2（5584 / 5570），`EXIT=0`；pypdf 重读脚本 `EXIT=0` |
| raw-log | `build/wsdevice-batch43/ws1/`（`exportaigc.am.txt`、`exportaigc-5570.am.txt`、`meta.txt`、`pdfs/`） |
| record-ref | `ws1-ws4-device-track.md` §1；`docs/known-defects.md` KD-75 |
| sanitized | 是（仅模拟器 serial、包名、PDF 元数据；无凭据/学习者内容） |

## 0. 本文件补的是什么

批次 43 索引 §6 原记“WS1 无独立 E 文件”。本文件把 C1–C8 与可复现产物逐条绑定，并**收窄**两处过宽表述：

1. “独立读者 pypdf”只读回 C2/C3（不是 C1–C7）；
2. C2 只覆盖 `MODEL_DOCUMENT_PARSE`；自然语言改卷的模型文本是另一条来源（KD-85）。

## 1. C1–C8 对账

| 判据 | 结论 | 证据 |
|---|---|---|
| C1 含 AI 图导出 → `/AIGC` | **PASS** | 设备 `PdfAigcReader` 读回 `Label=1`；`ws1-ws4-device-track.md:40-56` |
| C2 仅模型解析文本、无图 → `/AIGC` | **PASS（限 `MODEL_DOCUMENT_PARSE`）** | 设备用例 `modelParsedTextOnlyExportIsLabeled`；`model-text-labeled-5570.pdf` 经宿主 pypdf 读回（§2） |
| C3 纯用户内容 → 无 `/AIGC` | **PASS** | `pure-user-unlabeled-5570.pdf` 经宿主 pypdf 读回（§2） |
| C4 旧无标记同键产物拒绝并重建 | **PASS** | `oldUnmarkedArtifact…` / `modelParsedTextArtifactUnderTheOldKey…` 两例绿；`GENERATION=2` 与三处前缀见 `ws1-ws4-device-track.md:68` |
| C5 注入后字节前缀与渲染器原始字节一致 | **PASS（项目 `PdfAigcReader`/断言）** | `MistakePdfExporterAigcInstrumentedTest.kt:100-106`（`assertArrayEquals`），非独立读者 |
| C6 注入后平台 `PdfRenderer` 打开且页数不变 | **PASS（项目 `PdfRenderer`）** | 同文件 `:155-161`；`PdfRenderer` 打开 ≠ 外部阅读器一致 |
| C7 设备日志含 `evidence=` 且产物拉回 | **PASS（可用件 2）** | `ws1/` 日志；5584 拉取的 `model-text-labeled.pdf` / `pure-user-unlabeled.pdf` 为 **0 B**，不可用（§3） |
| C8 部分页打印丢 `/AIGC` | **未处置（登记）** | 设备用例 `known gap` 断言；已另立 **KD-84** |

## 2. 本轮独立 pypdf 重读（2026-11-05，修复环实跑）

命令（仓库根）：

```bash
python -I - <<'PY'
import hashlib
from pathlib import Path
from pypdf import PdfReader
for name in ["model-text-labeled-5570.pdf", "pure-user-unlabeled-5570.pdf"]:
    p = Path("build/wsdevice-batch43/ws1/pdfs") / name
    raw = p.read_bytes(); r = PdfReader(str(p)); meta = dict(r.metadata or {})
    print(name, len(raw), len(r.pages), hashlib.sha256(raw).hexdigest(),
          sorted(meta), meta.get("/AIGC", "")[:80], b"/AIGC" in raw)
PY
```

实测输出（pypdf `6.16.2`）：

```text
model-text-labeled-5570.pdf  2514765  1  88f661a6bfe89e606962aa630f237019955e795acb71c5543da8ac2a091e87e2
  keys ['/AIGC']  /AIGC {"Label":"1","ContentProducer":"SmartMistakeBook","ProduceID":"c52d2907…",…}  contains_AIGC_bytes True
pure-user-unlabeled-5570.pdf 2492555  1  1d510294582bb0dcd985ca0a21fdc489f4c90220833b1b63581e6bb00be2b9cb
  keys []         /AIGC (none)                                                                    contains_AIGC_bytes False
```

- 正向：无任何 AI 图、只有模型解析文本的导出带 `/AIGC`，`Label=1`。
- 负向：纯用户内容 metadata 为空，全文无 `/AIGC` 字节。
- 与台账旧数字的关系：KD-75 Progress ⑥ 原引 `model-text-labeled.pdf` 42,900 B / `pure-user-unlabeled.pdf` 40,406 B；批次 43 磁盘上这两个名字是 **0 B**（§3），2.5 MB 文件是 `*-5570.pdf`。关闭仍由 5570 实测支撑，但旧数字不可复现，已在 KD-75 Progress ⑨ 订正。

## 3. 产物可用性（不要引用 0 B 文件）

```text
build/wsdevice-batch43/ws1/pdfs/
  model-text-labeled-5570.pdf   2,514,765 B  (可用，§2 实读)
  model-text-labeled.pdf                0 B  (不可用)
  pure-user-unlabeled-5570.pdf  2,492,555 B  (可用，§2 实读)
  pure-user-unlabeled.pdf               0 B  (不可用)
```

## 4. 未覆盖 / 未运行

1. **item 5（JVM 三类内容用例）**：单测门不逐例；本文件未运行 Gradle → **UNVERIFIED**。
2. **C2 的自然语言改卷路径**：`TutorPaperEditApplier` → `toDraft()` 透传来源 `hasModelParsedText`；纯用户来源被模型改写后不带 `/AIGC` → 已开 **KD-85**，不在本条 PASS 范围内。
3. **DOCX**：无 `/AIGC` 机制（`MistakeDocxExporter.kt:59,77`）→ C2 只声明 PDF 链。
4. **外部阅读器一致性**：`PdfRenderer` 能打开 ≠ 第三方阅读器一致；跨实现一致仍 UNVERIFIED。
