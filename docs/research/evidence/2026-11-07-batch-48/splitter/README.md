# 批次 48 WS2 切分导出通道与度量证据（2026-11-07）

## 通道形态（golden 页面图像 → 切分 → 同 schema JSON）

- **生产 wire 全复用**：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/GoldenSplitEvalChannelHarness.kt`
  （assumeTrue 门控，默认跳过）调用真实 `OpenAiModelProtocol.requestBody`（SYSTEM_PROMPT + 单页
  切分 prompt + image_url data URI, detail=high, temperature 0.1, json_object, 非流式）→ 真实
  `parseCompletion`（含 `CaptureAssessment` 契约校验）→ 真实 `isUsableSplitRegionSet`
  （`CaptureWorkflowRepository.kt:439`，判据不过=不切=0 框，与 `BatchSplitRecognizer` 消费语义一致）。
- **图像出网副本**：`build/batch48-ws2/prepare_egress.py` 按 `EgressImageBudget` 口径
  （长边 2048、JPEG q88、超 1.5MiB 回落 q70；PIL BILINEAR）。harness 用真实
  `EgressImageBudget.scaledSize`/`MAX_IMAGE_BYTES` 复算断言。
- **与生产链路的差异（如实）**：①传输层为直连 HTTPS POST（非 gateway 授权管线），请求体字节同源；
  ②像素重采样为 PIL 非 Android Skia（字节不同）；③无设备 DB 落库——导出由脚本组装成
  `export_split_eval.py` 同 schema（schemaVersion 2, pages[].file/questions）。
- **密钥**：`SMB_REAL_PROVIDER_KEY` 从 gitignored `local.properties` 读入进程环境，仅 sha8
  `cd10efbc` 入证据；任何文件/日志不含密钥（grep `sk-…` 零命中）。

## 前置实测：Provider 图像输入能力（E-48-SPLIT-PROV）

`probe-vision-result.json`：app 同形状 image probe（ModelProbeSpec 合成图 Q7M2）→
HTTP 200、`replyContainsToken=true`（deepseek-flash 正确读出 Q7M2，finish_reason=stop）。
**推翻 E-48-PROV-01 "③图像输入能力未测" 的保留项**：DeepSeek 图像输入实测可用。

## 运行索引（runs/<runId>/：逐页记录 + manifest；度量 = 真实 measure_split_accuracy.py）

| run | 配置 | 页 | ① countExact | ② IoU≥0.5 | ③ fragment | 备注 |
|---|---|---|---|---|---|---|
| dev-red-01 | A=原生产代码（prompt 原文, temp 0.1） | dev 16 | **5/16=0.3125** | 19/52=0.3654 | 0/52=0.0 | RED 基线；选定配置 |
| dev-diag-02 | 同 A | 8 失分页 | — | — | — | 诊断轮：留痕原始回包 |
| dev-green-01 | B=prompt 三条款 + temp 0.1 | dev 16 | 1/16=0.0625 | 13/52=0.25 | 2/52=0.0385 | 回退 |
| dev-green-02 | C=prompt 三条款 + temp 0 | dev 16 | 1/16=0.0625 | 12/52=0.2308 | 0/52=0.0 | 回退；塌向 PASS |
| dev-green-03 | D=原 prompt + temp 0 | dev 16 | 4/16=0.25 | 18/52=0.3462 | 1/52=0.0192 | 回退 |
| acceptance-once-01 | A（唯一一次） | acceptance 8 | 5/8=0.6250 | 13/26=0.50 | 3/26=0.1154 | 单次，不再调参 |

## dev 失分模式（对 dev-red-01/diag 的逐页证据）

1. **NEED_MORE_IMAGE 过发**（004511/004854/005605/010633）：页缘截断的页被判"未拍全"；金标语义数
   完整可见题。结构性语义差，prompt 微调未能消除。
2. **多题页判 PASS**（011017/011126）：上题尾部残段 + 下一题的版式被读成单题页（011017 的
   "上题尾部分两段"语义见 WS1 record §1.3）。
3. **契约拒收（非确定）**：顶层信封键 `type`（004511/005144，`requireOnlyKeys` 拒）、
   NEED_MORE_IMAGE 未配 ADD_IMAGE（005605）、acceptance 011358 同类。整页归 0 框。
4. **相邻题合并致差 1**（010306/010859/011248/011649；acceptance 10034 6→5）。
5. **密排双栏页少计**（005144：金标 7，模型 4–6）。
6. **模型随机性**：同配置同页跨轮 ±1–2 框、decision 翻转（temp 0.1 与 0 均观察到）。

## 门判定（--golden v2 接入）

- 最终导出：`app-split-golden-v2-final.json`（24 页 42 框，dev=dev-red-01 + acceptance=acceptance-once-01，
  逐页 origin 登记；sha256 `abb7744f…`）。
- `check_split_gate.py --golden tools/split_golden/tables/split_golden_v2.json --app <final>` →
  **FAIL（exit 1）**：① 10/24=0.4167 < 0.9 **未过**；③ 3/78=0.0385 ≤ 0.05 过；
  **切残硬红线 fragmentedQuestions=3 ≠ 0 未满足**（报告 `hardRedLines`）。
- 报告/证据/裁定：`gate-report-v2-01.json` / `gate-evidence-v2-01.md` /
  `rulings/20261106T181632Z-3191bcc0.json`；产物 sha 见 `MANIFEST-SHA256.txt`。
- 范围声明（不可外推）：门值只对 金标 sha `03b1eecc…` × 本导出 sha `abb7744f…` 成立；
  通道为代理 harness（非设备 DB 导出）；金标等级 G1（model-labeled）。

## 未验证 / 边界（如实）

1. harness 的"默认跳过"路径按 JUnit4 assumeTrue 语义实现；本会话 6 次激活运行均真跑，
   但无 env 的跳过运行未实测——会话末段共享工作树被并线改动（core/database 知识线在改）
   导致再次编译不能，未重试。
2. 单轮采样：各配置 ① 为一次 16 页采样的值，非均值；配置间差值受模型随机性影响。
3. 本门值不等于设备产品路径准确率（通道差异见上），也不等于该模型在 155 页上的表现。
4. 密钥零接触声明：除 sha8 外未记录任何密钥材料；`local.properties` git-ignored、未提交。
