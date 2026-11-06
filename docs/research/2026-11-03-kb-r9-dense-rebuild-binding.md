# 稠密向量资产的重打与绑定语义（KB R9，2026-11-03）

> **范围**：`core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` 与旁车 `.vec.json`、`DenseRecallAssembly.VECTOR_ASSET_SHA256`、`tools/dense_build/` 生成链，以及别名集变化后的重钉顺序。
> **方法**：先读当前源码/旁车/门（本会话实读），再用本机真实模型与真实资产做 5 组探针（本会话实跑），最后用官方文档交叉核对。文中每个数字都标了来源；推断与未验证分开写。
> **写作时点**：2026-11-03。当前工作树有他线改动，本文件不触碰内容包、不重打资产。

---

## 0. 当前身份（本会话实测）

| 项 | 值 | 来源 |
|---|---|---|
| `.vec` | 44,121 行 × 512 维 int8；26,087,046 B；sha256 `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` | `sha256sum` + 文件头 |
| `.vec.json` | sha256 `6c1c448f2428091c5b91ed8888aa4c1ef90539201f47ef82db4682c2f02a07fc`；`packSha256=10ede1bf449de38f…`；`vectorCount=44,121`；`atomicNodes=3,280`；`aliasVectors=40,841` | 实读 |
| 主包 | `moe-2025-four-subjects-v1.json` sha256 `10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670` | 实算 |
| Kotlin 钉值 | `DenseRecallAssembly.kt:113` = `70329115…`（与旁车/文件三方一致） | 实读 |
| 模型件 | int8 ONNX sha256 `4d3b31355a3df4ff923db83bc76181f8581592de10f67502be825f519d2fa213`；fp32 `519a2f46…`；ORT 1.28.0 | 旁车 + `model-manifest.json` |
| 金标 | `tools/kb_coverage/tables/golden_queries_v2.json`，129 条，sha256 `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` | `dense_asset.py:80` + 实算 |
| 陈旧性门 | `python tools/dense_build/check_asset.py` → **10/10 OK**、exit 0 | 本会话实跑 |

---

## 1. 事实：`.vec` 到底绑定到什么

**一句话结论**：是否重打取决于**被嵌入的有序 surface 序列**（`node_id + kind + surface`）是否变化，而不是主包文件是否变化。材料 sidecar 变化通常连主包都不变；主包“元数据-only”变化只需同步 `packSha256`；知识点 `name` 或别名变化必须重打。

### 1.1 生成链的确定性输入

`tools/dense_build/dense_asset.py:323-359` 的 `atomic_layout()` 是布局的权威定义。它只读主包的这些字段：

| 读取的字段 | 作用 |
|---|---|
| `taxonomyVersion` | 拼进每个 `knowledgeNodeId` |
| `subjects[].subject`（小写） | 拼进 id，并作为端侧 `subjectKey` |
| `subjects[]` / `topics[]` / `knowledgePoints[]` 的**列表顺序** | 决定行顺序 |
| `knowledgePoints[].slug` | 拼进 id |
| `knowledgePoints[].name` | **canonical surface**（一行向量） |
| `knowledgePoints[].aliases[]`（`dict.fromkeys` 保序去重） | **alias surfaces**（每别名一行） |

不读、因此**不进入向量输入**的字段：

- 顶层 `schemaVersion`、`packId`、`sourceNamespace`、`reviewedAtEpochMillis`、`sourceUri`、`coverage`
- `subjects[].sourceFingerprint`
- `topics[].name`、`topics[].sourceLocator`、`topics[].slug`（只进 `parent_id`，不进行）
- `knowledgePoints[].boundary`、`kind`、`sourceLocator`、`prerequisiteSlugs`

**材料/教学 sidecar（`moe-2025-teaching-support-v2-*.json`）根本不在主包里**：材料正文、材料标题、来源三元组的变化不会改变主包 sha，也不会改变 `atomic_layout()`。只有当标题短式等流程**把新别名发布进主包 `aliases[]`** 时，才会改变向量集。

术语必须先拆开，否则“标题变化”会被误判：

| “标题”指什么 | 是否进入 `.vec` | 正确动作 |
|---|---|---|
| 材料标题（teaching-support sidecar） | **否**，除非重建别名并发布进主包 | 不动 dense；若发布新别名 → 按别名变化处理 |
| 知识点 `name`（主包 canonical surface） | **是** | 必须重算该行（或全量重打） |
| 主包 `boundary/sourceLocator/kind/...` | 否 | 只可能影响 `packSha256`，不重算向量 |

### 1.2 `.vec` 字节的构成

`dense_asset.py` 的格式（v1）：

```
header(24B) | ids block（每行 u32 长度 + UTF-8 完整 knowledgeNodeId） | int8 matrix（行主序） | f32 scales
```

每行向量 = `quantize_int8(encoder(surface))`，量化是**逐行独立**的：

```python
scale[r] = max(abs(v_r)) / 127
q[r]     = rint(v_r / scale[r]).clip(-127, 127)
```

`pack_dense_asset.py:214-273` 旁车的 `packSha256` 是**打包时主包文件的整文件哈希**，不是“向量输入摘要”。因此它覆盖了大量与向量无关的字段（见 1.1），是一个**过粗的触发器**。

### 1.3 当前门的语义与一个真实的盲区

`tools/dense_build/check_asset.py` 检查：

1. `sidecar.packSha256 == 当前主包 sha256`（第 71-72 行）
2. 词表/ tokenizer sha 与旁车一致
3. `.vec` 自身 sha 与旁车一致
4. 头字段 `dim/count/version/dtype` 与字节数自洽
5. **`header.ids == atomic_layout()` 的 node_id 序列**（第 117-140 行）
6. 模型清单三方一致

`DenseAssetProvenanceTest.kt:44-81` 只钉“常量 == 旁车 sha == 文件 sha”以及计数自洽；**它不读主包，也不比较 surface 文本**。

关键盲区：第 5 条只比较 **node_id**。如果只改 `knowledgePoints[].name`（slug/id 不变），或者在同一节点内**等量替换**某个别名文本，`ids` 完全不变；此时若有人手工把 `packSha256` 更新成新主包 sha，`check_asset` 会绿，但矩阵里那一行仍是旧文本的向量。`DenseAssetProvenanceTest` 也不会红（它只比对 sha 三方）。

本会话用真实主包做了内存级验证（临时序列化到 `build/`，随后删除；未改仓库文件）：

| 变体 | `packSha256` | `ids` | 有序 `(node_id, kind, surface)` 摘要 | 行数 |
|---|---|---|---|---|
| 仅改 `reviewedAtEpochMillis/sourceUri/coverage/sourceFingerprint/sourceLocator/boundary/prerequisiteSlugs` | **变** | **不变** | **不变** | 不变 |
| 只改一个 `knowledgePoints[].name`（slug 不变） | **变** | **不变** | **变**（1 行 surface 不同） | 不变 |
| 追加 1 个别名 | **变** | **变**（长度 +1） | **变** | 44,121 → 44,122 |

结论：

- **`packSha256` 只能证明“主包文件换过”，不能证明“向量输入没换”。**
- **`ids` 能证明“节点集合/顺序没换”，不能证明“surface 文本没换”。**
- 现有门缺一个“向量输入摘要”这一层。这不是理论风险：1.4 的仓库先例正是“只改 `packSha256`、不重算向量”，该操作在 surface 不变时正确，在 surface 等量变化时会把陈旧向量放绿。

### 1.4 仓库已有先例

**(a) 元数据-only：B4 `sourceUri` 清零（已执行）。**
`docs/research/2026-11-02-kb-b33-b4-sourceuri-prereg.md §3.2/§6.6` 明确：主包顶层 `sourceUri` 改 null → 主包 sha `96246c78… → 10ede1bf…`；`.vec` 字节不变；`.vec.json` **只改 `packSha256`**，文件 sha `6612a28d… → 6c1c448f…`；`check_asset` exit 0。`build/agent-batch33/b4/final.md:92,101` 还登记了“未重跑 `pack_dense_asset.py`，只手工 re-pin `packSha256`；`generatedBy` 等生成链元数据未更新”。

这是“只有材料/来源元数据变化、节点与别名集合不变 → 不重打向量”的**已执行先例**；但它同时暴露了流程依赖人工保证“surface 未变”，而不是门在保证。

**(b) 别名变化：B2b 预注册（未执行）。**
`docs/research/2026-11-02-kb-b33-b2-title-only-prereg.md §4.1` 写死：**20 个既有节点、净 +123 别名**，delta 合并口径下 `vectorCount 44,121 → 44,244`（`3,280 canonical + 40,964 alias`），`.vec` / `.vec.json` sha 全变，`packSha256` 跟随新主包。§2.4 还警告：`rebuild(pre)` 的候选集与已发布别名集**不是同一集合**，B2b 只能做 delta 合并或整表重写二选一；整表重写的条数不是 `44,121+123`。因此“+123 / 20 节点”应理解为**20 个受影响节点**，不是 20 个新节点；canonical 仍 3,280。

**(c) 历史批次。**
批次30（v24）别名 40,839 → 40,841，行数 44,119 → 44,121，重打并重钉；批次22（v21）别名 +2，行数 44,041 → 44,043，重打并重钉。见 `DenseRecallAssembly.kt:90-107` 的版本注记。

### 1.5 与常规做法的对照

| 方案 | 存了什么 | 一致性靠什么 | 与本仓的差别 |
|---|---|---|---|
| **本仓 `.vec` + `.vec.json`** | 向量 + 逐行 node_id；旁车存 `.vec` sha、主包 sha、计数、模型/词表哈希 | 三道 sha + ids 对账 | 比 FAISS 强在“逐行 id + 旁车溯源”；弱在 `packSha` 是整包哈希，且没有 surface 摘要 |
| **FAISS** | 向量 + 可选 64-bit id（`IndexIDMap` 维护 id 表）；**不存任意 metadata/payload** | 调用方自己维护 id→payload、模型版本、语料版本 | FAISS 的索引文件不是自描述清单；一致性完全外置。官方 wiki：Flat 索引 “do not store vector ids… can be wrapped in an `IndexIDMap`”；`IndexIDMap` “maintains a table with the mapping” |
| **HuggingFace datasets 指纹** | 初始指纹 = Arrow table（或磁盘 Arrow 文件）的哈希；后续指纹 = 前一状态指纹 + 最新 transform 的哈希 | 内容/变换链派生；不可哈希 transform → 随机指纹并全量重算 | HF 的指纹是**内容派生**的；本仓的 `packSha` 是**容器文件**派生。HF 更接近“该重算才重算”，本仓更保守（任何主包字节变化都触发） |

对本仓的直接含义：**正确的绑定摘要应落在“被嵌入的有序 surface 序列”上**——它比主包 sha 细（能区分改名/换别名），比 `.vec` sha 粗（不因重排/压缩实现变化而失效）。1.3 的盲区正是缺这一层。

**来源等级**：FAISS 与 HF 的条目来自官方 wiki/官方文档源码（见 §5）；“FAISS 不存 metadata”是**从官方 API 只暴露 ids 推断**，未见一句显式“metadata 不存”的原文，标“待验证”。

---

## 2. 三种策略对比

### 2.1 全量重打（当前仓库的默认做法）

**做法**：按 ⓪–⑧ 链重跑：词面腿 → `export_bge_int8.py`（可用 `--skip-export`，模型未变）→ `pack_dense_asset.py` → `stage3_expectation.py` → 四个 fixture → `check_asset.py`。

**保证**：
- 行顺序、id 布局、surface 与当前主包**按构造一致**（`atomic_layout` 是唯一来源）。
- 所有向量来自同一次导出、同一模型/词表/量化实现。
- 不需要证明“哪些行可复用”。

**代价（本机实测，见 2.5）**：int8 ORT 编码 44,121 条约 **58–167 秒**（16/1 线程），整条链另加 torch fp32 参考、两次量化路线、夹具与设备判官，通常是**分钟到小时**级。

**风险**：低。唯一要求是 pin 住模型/词表/ORT 版本/`maxLen`；这些旁车与 `model-manifest.json` 已有记录。

### 2.2 增量/局部重算（只编码变更行）

**(a) 同一模型对文本独立编码是否确定？——本机答案是“逐位确定”。**

本会话用生产 int8 ONNX（`4d3b3135…`）+ 生产 tokenizer（`48cea5d4…`）+ ORT 1.28.0，在 Ryzen 9 9950X / Windows 上跑了以下对照，结果**全部 bitwise 相等**（`np.array_equal` 为 True，不只是 cosine≈1）：

| 对照 | 结果 |
|---|---|
| 同一 session 重复编码 | bitwise 相等 |
| 两个独立 session（同线程数） | bitwise 相等 |
| 1 线程 vs 16 线程 | bitwise 相等 |
| 逐条编码 vs 6 条一批（padding 到批内最长） | bitwise 相等 |
| 逐条编码 vs 64 条一批 | bitwise 相等 |
| 动态 padding vs 固定 pad 到 128 | bitwise 相等 |
| 独立进程重新编码 vs 随包 `int8-docs.npy` 对应行 | **bitwise 相等**（抽查 7 行） |
| 独立编码 + `quantize_int8` vs 随包 `.vec` 的 int8 行与 scale | **bitwise 相等**（抽查 4 行：0 / 12,345 / 30,000 / 44,120） |

工程上的原因（本会话实证，不是猜）：
- 导出的 ONNX 图**不含** `Dropout` / `Random*` / 训练态算子。实际算子集只有 `MatMul/Add/LayerNormalization/Softmax/Erf/Gather/ReduceL2/DequantizeLinear/...`（`onnx.load` 枚举）。
- 池化是 **CLS**（取第 0 个 token），L2 归一化已烘进图（`ReduceL2` 节点）；padding token 被 `attention_mask` 屏蔽，CLS 位置不受 padding 长度影响。
- int8 采用**权重-only、per-channel、激活 fp32**（仓库路线 B），不是激活动态量化；量化误差不引入运行时随机性。
- 量化是**逐行**的，`scale[r]` 只依赖该行自身，不依赖批次或其他行。

**(b) 批次大小是否影响数值？——在本机/本模型上不影响（bitwise）。**
我们测了 batch 1 / 6 / 64，以及 pad 到 128；输出字节完全相同。需要注意这是**经验结论**，不是数学保证：不同 ORT 版本、不同 CPU ISA（AVX2/AVX512）、不同 graph optimization level 可能选择不同 kernel 与累加顺序。跨机器/跨版本必须重新验证。

**(c) 是否需要全量重跑以保持顺序与 id 布局？——需要“全量重组装”，但不需要“全量重编码”。**
`.vec` 是位置型格式，且 `DenseVectorAsset.read` 强制“同一节点的行必须连续”（`DenseVectorAsset.kt` 的 `closedNodes` 检查）。因此：
- 不能在文件末尾直接 append 新别名；必须按 `atomic_layout` 的新顺序重新拼 ids/matrix/scales。
- 但未变化的行可以按 `(node_id, kind, surface ordinal)` 复用旧的 int8 + scale，逐位不变。
- 新增/删除节点会移动后续所有行，必须整表重写（I/O 仍要写 26MB，但不重编码）。
- 同一节点内别名顺序变化、或等量替换别名文本，`ids` 不变但行与 surface 的对应关系变了；复用旧行会错位。这正是 1.3 的盲区。

**复杂度的真实成本**：映射表、重复 surface（本包 canonical 常与某个 alias 同名，例如 `函数的概念`）、节点段拼接、复用行断言、失败回滚。约 150–250 行脚本 + 一套证明；而 2.5 显示它只省约 1 分钟编码时间。

### 2.3 不重打，只改侧车

**成立条件（必须同时满足）**：
1. `atomic_layout()` 的有序 `(node_id, kind, surface)` 与上一次打包**逐行相同**（不只是 ids 相同）。
2. `.vec` 字节未变（sha 相同）。
3. 只允许改 `packSha256`（以及因主包变化而确实需要同步的溯源字段）；`sha256/bytes/counts/format/quantization` 保持不变。

**做法（两种，按可复现性排序）**：
- **A. 重跑 `pack_dense_asset.py`**：`int8-docs.npy` 仍在、行数与布局一致时，脚本会用同一份 npy 重新量化并落盘。若 surface 未变，`.vec` 应**逐字节相同**；旁车只应 `packSha256` 变。执行前先记旧 `.vec` sha，执行后断言相等；不等则停并回滚。优点：不手工改 JSON；缺点：仍不能自动证明 npy 的行对应新 surface（它只查形状），所以仍需外部 surface 断言。
- **B. 只改旁车 `packSha256`**（B4 先例）：用脚本读取旁车、只替换该字段、重算文件 sha；不要手改。执行前必须用 1.3 的有序 surface 摘要证明“输入未变”。B4 的 `final.md:92,101` 已登记这种方式会让 `generatedBy` 等字段停留在旧轮次，属于已知的溯源漂移。

**不成立的情形**：任何 surface 文本变化、别名增删、节点增删、顺序变化、`taxonomyVersion` 变化、模型/词表/量化实现变化。此时改侧车 = 把陈旧向量放绿。

### 2.4 对比表

| 维度 | 全量重打 | 增量重算 | 只改侧车 |
|---|---|---|---|
| 适用触发 | 任何 surface/id/顺序/模型变化 | 少量新增/改名，且想省编码 | 仅主包元数据变化，surface 逐行不变 |
| 必须证明 | 模型/词表/ORT 固定；布局来自当前包 | 旧行可复用且逐位不变；新行独立编码 | 有序 surface 摘要不变；`.vec` sha 不变 |
| 编码量 | 44,121 行 | 仅变更行（B2b 为 123 行） | 0 |
| `.vec` 写入量 | 全量 26MB | 全量 26MB（重组装） | 0 |
| 本机耗时量级 | 1–3 分钟（仅编码） | <1 秒编码 + 重组装/校验 | 秒级 |
| 主要风险 | 低 | 行错位/重复 surface 键/顺序漂移 | 把“ids 未变”误当“surface 未变” |
| 建议 | **默认** | 暂不做（见 3.5） | 仅元数据-only，且加 surface 断言 |

### 2.5 成本估算（本机实测）

测试条件：AMD Ryzen 9 9950X（16C/32T）、Windows、ORT 1.28.0、CPUExecutionProvider、`bge-small-zh-v1.5-int8.onnx`、`maxLen=128`、`padding=True`、batch=64。

语料统计（全量 44,121 条 surface 实测）：

| 指标 | 值 |
|---|---|
| token 数均值 / 中位 / p95 / p99 / 最大 | 15.27 / 15 / 23 / 25 / 26 |
| 总 token 数 | 673,632 |
| 超过 64 / 128 token 的条数 | 0 / 0 |

因此**没有截断**，`maxLen=128` 远大于实际需要（模型卡上限 512，仓库选 128 是保守值）。

吞吐（分层抽样 4,096 条，按整表外推）：

| intra-op 线程 | rows/s | 44,121 行预计 |
|---:|---:|---:|
| 1 | 265 | **167 s** |
| 4 | 652 | **68 s** |
| 16 | 764 | **58 s** |

结论：**全量编码在这台机器上是“一分钟级”**，不是小时级。增量只省约 1 分钟，却引入一套行复用/错位风险；**在 44k 规模不值得**。真正昂贵的是设备双路判官、夹具重出与回归（小时级），不是编码。

> 公开基准：本次检索**未找到**该模型在 CPU 上的权威 rows/s 公开基准；上表是本机实测，不外推到其他 CPU。标“单一环境实测”。

### 2.6 端侧/生成侧的确定性工程约束

本仓生成链（离线）与端侧（LiteRT）是两套运行时，必须分开说：

| 约束 | 本仓现状 | 证据 |
|---|---|---|
| CPU 执行 | 生成侧 `onnxruntime` CPUExecutionProvider；端侧 LiteRT `.tflite` | `export_bge_int8.py`、`DenseRecallAssembly.MODEL_ASSET_PATH` |
| int8 口径 | 权重-only int8 per-channel + fp32 激活；句向量 L2 已归一化 | `model-manifest.json`、旁车 `model.onnxRoute` |
| eval/无 dropout | 导出前 `wrapper.eval()`；ONNX 图无 Dropout/Random 算子 | `export_bge_int8.py` + 本会话算子枚举 |
| 池化 | CLS + L2（烘进图） | `ReduceL2` 节点；BGE 模型卡“select … [CLS]” |
| 文本预处理 | `do_lower_case=True`、`strip_accents=False`、`maxLen=128`、padding/truncation | `sentence_bert_config.json` + `export_bge_int8.py` |
| 线程 | 生成侧默认 ORT intra-op = 物理核；`--threads` 只控 torch | `export_bge_int8.py:440` |
| 版本固定 | `model-manifest.json` 记录 `onnxruntime: 1.28.0`、模型/词表 sha | 实读 |

**官方是否保证“同输入同输出”？**
- **没有找到 CPU 端的显式官方保证。** ORT 官方文档树（`docs/reference`、`docs/performance`）没有 reproducibility/determinism 专页；唯一显式开关是 `OrtApi::SetDeterministicCompute` / `session.use_deterministic_compute`，其 C API 注释明确限定 **“for GPU kernels where possible”**、默认 false、可能有性能代价（`onnxruntime_c_api.h:5282-5292`；`onnxruntime_session_options_config_keys.h:1008-1011`）。
- 社区 issue #4611 报告“GPU 不确定、CPU 数值相同”，属**用户报告/单一来源**，不是官方承诺。
- 本会话的 bitwise 证据是**当前机器 + 当前版本 + 当前模型**的实测；它足以支撑“在这条构建链上增量复用旧行是安全的”，不足以支撑“任何环境都逐位一致”。

**实践结论**：不要依赖“CPU 一定确定”的口头保证；把 **模型 sha、词表 sha、ORT 版本、maxLen、tokenizer 配置**钉住，并在每次重打后做一次“旧行重编码 vs 旧 `.vec` 行”的抽样断言（见 3.3）。不建议为此打开 `use_deterministic_compute`（官方说它针对 GPU，CPU 上收益/语义未证实，且可能付费）。

---

## 3. 对本仓的建议

### 3.1 决策矩阵

| 变化类型 | 例子 | `.vec` 是否变 | 策略 |
|---|---|---|---|
| 材料正文/材料标题/来源三元组（teaching-support sidecar） | 材料 `sourceUri`、正文改写 | **否** | 不动 dense；跑词面/契约门即可 |
| 主包元数据（不进入 `atomic_layout`） | `reviewedAtEpochMillis`、`sourceUri`、`boundary`、`prerequisiteSlugs`、`sourceLocator`、`kind` | **否** | 只 re-pin `packSha256`（B4 先例），并附 surface 摘要断言 |
| 主包 `name` 改名（slug 不变） | 知识点 canonical 改名 | **是**（该行） | 重打；至少重算该行并重组装 |
| 别名追加/删除 | B2b +123 | **是**（行数变） | 全量重打（默认） |
| 别名等量替换/节点内重排 | 同数换词 | **是**（行内容/顺序变） | 重打；`ids` 门抓不到，必须 surface 摘要 |
| 节点增删/改 slug/改 taxonomyVersion | 建点、并点 | **是**（id/行数变） | 全量重打 + 重钉 |
| 模型/词表/量化实现变化 | 换 ONNX、换 tokenizer | **是** | 全量重打 + 全套对拍门 |

### 3.2 B2b（+123 别名 / 20 个既有节点）的正确重钉顺序

**顺序原则：每个产物只从它的上游派生；不要在向量还没生成时先改钉值。**

```
0. 冻结与快照
   - 记录六元组 pre 值：pack sha、.vec sha + count、alias_map sha、golden sha、代码树 sha、判官/设备版本
   - 记录当前 .vec / .vec.json / 主包 sha；确认工作树只含本批预期改动

1. 重建主包（先让布局稳定）
   - 把 +123 别名按预注册口径（delta 合并 / 整表重写二选一）写入 alias_map / 主包
   - promote 落盘 → 新主包 sha（B2b 预期不再是 10ede1bf…）
   - 断言：atomicNodes 仍 3,280；aliasVectors 40,841 → 40,964（delta 口径）；vectorCount 预期 44,244
   - 断言：有序 (node_id, kind, surface) 的 delta 恰好 = +123 行、0 删除、0 重排、20 个既有节点
   - 门：run_kb_checks.py、check_pack_contract 绿

2. 词面腿（dense 的旁车会记录它的 sha/rows）
   - ./gradlew.bat :core:data:testDebugUnitTest --tests "*ProductionLexicalLegExportTest*" --rerun
   - 若期望值漂移：先记录实测，再重钉该测试的锚值（不得猜）

3. 重打向量（模型未变时用 --skip-export）
   - python tools/dense_build/export_bge_int8.py --skip-export
     断言：int8-docs.npy = (44,244, 512)；token max ≤ 128；fp32/int8 对拍 ≥ 0.999；manifest 行数/哈希更新
   - python tools/dense_build/pack_dense_asset.py
     断言：header.count=44,244；ids == 当前布局；逐行量化还原 cosine min ≥ 0.999；
           .vec sha 变、bytes 变、旁车 packSha == 新主包、计数自洽

4. 期望值与夹具（全部从新资产派生）
   - python tools/dense_build/stage3_expectation.py
   - python tools/dense_build/gen_tokenizer_fixture.py
   - python tools/dense_build/gen_dense_device_fixture.py
   - python tools/dense_build/gen_device_parity_fixture.py
   - python tools/dense_build/stage3_device_sim.py
   断言：golden-query-vectors.i8 不变（查询集未变）；golden sha 仍 eabef102…；
        tokenizer universe = 44,244；scan/fusion/order 参考与新资产一致；
        stage3_device_sim 与 refFusedD1 的 top-5 差异在预注册容差内

5. 重钉 Kotlin 常量与测试锚（只钉实测值）
   - DenseRecallAssembly.VECTOR_ASSET_SHA256 = 新 .vec 实测 sha；更新版本注记（行数/别名数/主包 sha）
   - DenseVectorAssetTest：nodeCount 仍 3,280（若整表重写或建点则按实测）
   - DenseRecallRerankerTest：候选总数/逐 case 数按新 fixture 实测重钉
   - ProductionLexicalLegExportTest：V1_D1_MAIN / V1_D1_MRR 按本轮实测重钉
   - 其它引用 .vec/词面腿/夹具计数的常量按实测重钉
   - 门：DenseAssetProvenanceTest（常量 == 旁车 == 文件三方一致）

6. 资产陈旧性门 + Python 测试
   - python tools/dense_build/check_asset.py          → 期望 10/10 OK
   - python -m unittest discover -s tools/tests -t tools -p "test_dense_asset_gate.py" → OK

7. 判官（最后跑；它消费的是第 5/6 步之后的资产）
   - JVM：GoldenRetrievalJvmTest（测量台，不断言质量阈值）
   - 设备：GoldenRetrievalInstrumentedTest（回归地板 + 时延门）
   - 双路对比基线：主集不劣 / 24 章不劣 / 零漂移 0（按 B2b 预注册）
   - 记录 MISS 集与逐章差异；不达预期就停，不改判据

8. 六元组与提交
   - 复算 pack / .vec + count / alias_map / golden / 代码树 / 判官版本
   - 只有六元组自洽且所有门绿才提交；中间态（新包 + 旧 .vec）不得提交
```

**为什么不能颠倒**：
- 先重钉常量再重打 → 常量指向不存在的 sha，设备会静默回退纯词面（`DenseRecallAssembly` 设计如此），而门可能还没跑到。
- 先跑判官再重钉 → 判官读旧资产/旧钉值，得到的通过没有解释力。
- 先改 `.vec.json.sha256` 而不改 `.vec` → `DenseAssetProvenanceTest` 三方不一致，且这是“伪造身份”。

### 3.3 断言清单（可直接照做）

**A. 布局/包**
1. `atomic_layout()` 行数 == `3,280 + 40,964 = 44,244`（B2b delta 口径）。
2. 有序 `(node_id, kind, surface)` 的 diff：新增恰好 123 行，全部是 `kind=alias`，落在 20 个既有 node_id 上；删除 0；重排 0。
3. 3,280 条 canonical surface 与旧布局逐条相同。
4. 主包 `atomicNodes` 不变；`aliasVectors` 精确 +123。
5. `run_kb_checks.py`、`check_pack_contract` exit 0。

**B. 编码/模型**
6. `model-manifest.json` 的 `onnxInt8Sha256 == 4d3b3135…`、词表 sha `45bbac6b…`、tokenizer sha `48cea5d4…`（模型未换）。
7. `int8-docs.npy` 形状 `(44,244, 512)`；`int8-queries.npy` 形状 `(129, 512)`。
8. 全量 token max ≤ 128（本批预期仍为 26 左右）。
9. 对拍：int8 docs/queries vs torch fp32 逐行 cosine min ≥ 0.999；fp32 ONNX vs torch ≥ 0.999。
10. **抽样确定性断言**：随机取 32 条旧 surface，单条独立编码 + 量化，与旧 `.vec` 对应行的 int8/scale **逐字节相等**；任一条不等 → 停（模型/工具链已变，不能复用旧向量）。

**C. 资产**
11. header：`magic=SMBV`、`version=1`、`dim=512`、`count=44,244`、`dtype=1`。
12. 文件字节数 == `24 + idsBytes + count*512 + count*4`。
13. `header.ids == atomic_layout()` 逐条；每个 node 的行连续；无 `closedNodes` 违反。
14. 旁车自洽：`canonicalVectors + aliasVectors == vectorCount == format.count == header.count`。
15. 旁车 `sha256 == .vec 实测 sha == DenseRecallAssembly.VECTOR_ASSET_SHA256`（重钉后）。
16. 旁车 `packSha256 == 当前主包实测 sha`。
17. 旁车 `vocab.sha256` / `tokenizerJsonSha256` / `model.onnxInt8Sha256` 与清单一致。
18. 量化：`roundtripCosineMinVsInt8Model ≥ 0.999`；`endToEndCosineMin…` 如实记录（不作硬门）。
19. `productionLexicalLeg.rows/sha` == 本轮 TSV 实测。

**D. 夹具/期望**
20. `goldens` sha == `eabef102…`，129 条（**金标集本身零改**）。
21. `golden-query-vectors.i8` 不变（查询未变）。
22. tokenizer fixture 的 `universe == 44,244`；cases/stages 的差异逐项解释。
23. `dense-scan-reference` / `dense-fusion-reference` / `dense-device-order-reference` 由新资产重出，旧值不得静默保留。
24. `stage3_device_sim` 与 `refFusedD1` 的 top-5 成员差、名次差、命中丢失在预注册容差内（批次22 当时的 130 条口径先例是 2/130、0/130、0）。

**E. 判官/发布**
25. JVM 判官 exit 0；设备判官 exit 0（时延门按 CI 系数）。
26. 与基线对比：主集 Recall@5 不劣、MRR 不劣、24/24 章不劣、零漂移对称差 0。
27. 六元组复算通过；`.vec` 实测 sha/count 与旁车/常量/批报逐位一致。
28. APK 内解包 `knowledge/dense/bge-small-zh-int8.vec` 的 sha 与仓库一致（发布前最后一道）。

### 3.4 建议加固：给旁车加“向量输入摘要”

**它消灭的具体失败**（不是“更完备”）：1.3 演示的“只改 canonical `name` 或等量替换别名 → `ids` 不变 → 手工 re-pin `packSha256` → `check_asset` 绿，但矩阵里那一行是旧文本的向量”。现有门对此**没有任何反应**；设备上只会表现为检索质量静默掉档。

**最小实现**（一个字段 + 一个检查，约 10 行）：

- `pack_dense_asset.py` 写旁车时增加：
  ```json
  "corpus": { "layoutSha256": "<sha256 over rows>" }
  ```
  计算口径（与本会话演示一致）：
  `for row in atomic_layout(): h.update(f"{row.node_id}\t{row.kind}\t{row.surface}\n".encode())`
- `check_asset.py`：
  - 计算当前包的 `layoutSha256`；
  - 若旁车有该字段：`layoutSha256` 不等 → **FAIL**（即使 `packSha256` 相等）；
  - 若旁车无该字段（迁移期）：退回现有 ids 检查 + 打 warning；
  - `packSha256` 的策略二选一：
    - **保守**：仍要求相等（元数据-only 时照 B4 手工/脚本 re-pin）；
    - **推荐**：`layoutSha256` 相等即视为“向量输入未变”，`packSha256` 不等降为 warning（避免每次元数据变化都要改旁车）。
- 当前包的 baseline 值（本会话实算，**不要直接写死进受控文件**，应由脚本生成）：`1aa8142e381d1b2b40b2ba3d5eec2783e5781830321613fcb78fff7f61e9d75c`。

**为什么不是 gold-plating**：它替代的是“人工口头保证 surface 没变”；B4 的先例已经证明人工 re-pin 是实际流程。没有它，1.3 的失败无法被任何门发现。

**不建议同时做的**：
- 不要引入 second sidecar / 双哈希体系；
- 不要在 Kotlin 端重复解析主包（端侧只需要 `.vec` 的 sha 与 ids 连续性，已有）；
- 不要打开 `use_deterministic_compute`（官方限定 GPU，CPU 未证实收益）。

### 3.5 是否值得做“只重算变更项”

**当前不建议。** 理由（按证据）：
1. 全量编码仅 **58–167 秒**（本机），增量只省其中约 1 分钟；而整条发布链的设备判官/夹具是小时级。
2. `.vec` 仍需全量重组装（26MB 写入），I/O 不省。
3. 增量必须处理：重复 surface（canonical 与 alias 同名）、节点段插入、等量替换、顺序变化；任何一个错位都会让 `DenseAssetVector.read` 判资产不可用（失败关闭，但会浪费一轮）。
4. 旧行复用需要“独立编码逐位等于旧行”的断言；虽然本会话已证明在该环境成立，但跨 ORT/CPU 版本会失效，增量路径会成为长期维护负担。

**何时重估**：语料 >50 万行、单次编码 >10 分钟、或模型换成更大的 encoder（base/large）且 CPU 编码成为瓶颈时。届时实现“按 `(node_id, kind, ordinal)` 复用 + 新行独立编码 + 全量重组装 + 旧行逐位断言”，并保留全量重打作为回退。

### 3.6 金标预期怎么变

- **金标集本身不变**：`golden_queries_v2.json` sha `eabef102…`、129 条。禁止为了让结果好看改金标或改 `expectedSlug`。
- **金标“预期读数”会变**：`build/stage3-device-expectation.json` 的 `refFusedD1/refDenseD1/refLexicalOnlyD1`、逐题 rank、逐章分布都要重算；`GoldenRetrievalJvmTest` 的测量输出、设备判官的 MISS 集与逐章也会变。
- **Kotlin 里以“实测值”形式钉住的锚**（`V1_D1_MAIN`、`V1_D1_MRR`、`DenseRecallRerankerTest` 的候选数等）必须**先跑后钉**，不能预填、不能按“应该会变好”猜。
- **验收规则在跑之前冻结**：B2b 预注册已写“主集不劣 / 24 章不劣 / 零漂移 0”；跑完按规则判，不因结果改规则。

---

## 4. 未验证项

| # | 未验证项 | 现状 | 影响 |
|---:|---|---|---|
| 1 | ORT CPU “同输入同输出”的**官方保证** | 未找到显式文档；`SetDeterministicCompute` 官方注释限定 GPU；issue #4611 是用户报告（单一来源） | 本会话的 bitwise 结论只覆盖 Ryzen 9950X / Windows / ORT 1.28.0 / 本模型；跨版本/跨 ISA 未验证 |
| 2 | 公开 CPU 吞吐基准 | 未找到该模型/该量化的权威 rows/s 基准 | 2.5 的数字是单一环境实测，不能外推到手机/其他 CPU |
| 3 | FAISS “不存 metadata”的原文 | 官方 wiki 只显式说 Flat 不存 ids、`IndexIDMap` 维护 id 表；metadata 不存是从 API 推断 | 不影响本仓结论，但引用时标“待验证” |
| 4 | HF datasets 指纹的底层实现细节（是否含 mtime/路径） | 只核对了官方文档源码的表述：“hash from the Arrow table / Arrow files + transform hash” | 不影响“内容派生 vs 容器派生”的对比结论 |
| 5 | 元数据-only 场景的**端到端**执行 | 本会话只做了 `atomic_layout` 的内存级模拟；B4 是真实执行过的 `sourceUri` only 变更 | “只改侧车”在完整链上的耗时/顺序未实测 |
| 6 | 增量路径 | 只证明了“单行独立编码 + 量化 == 旧行”；未实现拼接/复用脚本 | 不能声称增量已可用；本报告建议暂不做 |
| 7 | 端侧 LiteRT/tflite 的逐位确定性 | 未测；本会话只测 ONNX 生成侧 | 端侧只消费 `.vec`，不参与重打；但“端侧编码器与离线 npy 是否逐位一致”未验证（现有 `gen_device_parity_fixture` 的行对齐是 cosine ≥0.9999，不是 bitwise） |
| 8 | B2b 的实际金标读数变化 | 未执行 | 无法预测 `refFusedD1/refDenseD1` 与逐章变化；只能跑后实测 |
| 9 | 新 `.vec` 的确切 sha/bytes | 未构建 | 只能给估算（约 +72.7KB → 约 26.16MB）；不得预填 |
| 10 | `layoutSha256` 加固 | 未实现 | 1.3 的盲区目前仍存在；建议按 3.4 落地 |
| 11 | batch >64 的数值 | 只测了 1/6/64；更大批次未测 | 若将来改 batch，需重跑 bitwise 对照 |
| 12 | 不同 graph optimization level | 未测 | 默认 `ORT_ENABLE_ALL`；若改级别需重新验证 |

**特别说明（端侧与生成侧不要混）**：`DenseRecallAssembly` 在设备上用 LiteRT `.tflite`；`.vec` 是离线用 ONNX int8 生成的。重打链的确定性结论只适用于**离线 ONNX 生成侧**，不能直接搬到端侧编码器；端侧的一致性由 `gen_device_parity_fixture` 的 cosine 门（≥0.999）与设备判官保证，不是 bitwise。

---

## 5. 参考来源

| 来源 | URL | 等级 / 用途 |
|---|---|---|
| FAISS wiki, *Faiss indexes* | https://github.com/facebookresearch/faiss/wiki/Faiss-indexes | 官方 wiki；Flat 不存 ids、`IndexIDMap` 维护 id 表（原文已核） |
| FAISS wiki, *Pre and post processing* | https://github.com/facebookresearch/faiss/wiki/Pre--and-post-processing | 官方 wiki；`add_with_ids` / `IndexIDMap` 语义 |
| HF datasets, *Caching and fingerprinting*（文档源码） | https://raw.githubusercontent.com/huggingface/datasets/main/docs/source/about_cache.mdx | 官方文档源码；初始指纹 = Arrow table/files 哈希，后续 = 前态 + transform 哈希；不可哈希 transform → 随机指纹并全量重算 |
| HF 文档页面（同一内容） | https://huggingface.co/docs/datasets/about_cache | 官方文档页（本会话网络超时，未直接抓取；内容以上面的源码为准） |
| BAAI/bge-small-zh-v1.5 模型卡 | https://huggingface.co/BAAI/bge-small-zh-v1.5 | 官方模型卡；CLS pooling、`normalize_embeddings=True`、查询指令、512 维 |
| ONNX Runtime C API `SetDeterministicCompute` | https://github.com/microsoft/onnxruntime/blob/main/include/onnxruntime/core/session/onnxruntime_c_api.h | 官方源码；注释限定 “GPU kernels where possible”，默认 false |
| ONNX Runtime session config keys | https://github.com/microsoft/onnxruntime/blob/main/include/onnxruntime/core/session/onnxruntime_session_options_config_keys.h | 官方源码；`session.use_deterministic_compute` |
| ONNX Runtime thread management | https://onnxruntime.ai/docs/performance/tune-performance/threading.html | 官方文档；线程旋钮，无 CPU 确定性保证 |
| ONNX Runtime issue #4611 | https://github.com/microsoft/onnxruntime/issues/4611 | **单一来源/用户报告**；“CPU results are numerically identical”仅作旁证 |
| 本仓旁车 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json` | 实测值 |
| 本仓布局/量化权威 | `tools/dense_build/dense_asset.py:323-385` | 实读 |
| 本仓打包/旁车 | `tools/dense_build/pack_dense_asset.py:214-273` | 实读 |
| 本仓陈旧性门 | `tools/dense_build/check_asset.py:71-140` | 实读 + 本会话实跑 |
| 本仓三方一致测试 | `core/data/src/test/kotlin/.../dense/DenseAssetProvenanceTest.kt:44-81` | 实读 |
| B4 先例（只改 packSha） | `docs/research/2026-11-02-kb-b33-b4-sourceuri-prereg.md §3.2/§6.6`；`build/agent-batch33/b4/final.md:92,101` | 实读 |
| B2b 预注册（+123 / 20 节点 / 44,244） | `docs/research/2026-11-02-kb-b33-b2-title-only-prereg.md §2.2/§2.4/§4.1` | 实读 |
| dense 八步链 | `docs/kb-batch10-2026-10-13.md §5.1`；`build/agent-input/dense_chain_v9.log` | 实读 |
