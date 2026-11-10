# 批次 51 · AUD-08 acceptance 一次性运行：跑前冻结（写入于运行之前）

> 写入时点：**acceptance 8 页尚未运行**（本文件写入命令在运行脚本之前执行）。
> 范围：只读冻结；不改任何门阈值（① 0.9 / ③ 0.05）、分母、金标、提示词或产品代码。

## 1. 冻结物

| 项 | 值 | 来源 |
|---|---|---|
| 金标 | `tools/split_golden/tables/split_golden_v2.json`，sha256 `03b1eeccbbde19e5ab2e67d5e7de9bf1c1f2b1d8124c3e4b1e6cfe2091e18e54`（与旁车逐字节一致，本阶段实读） | 本阶段 `python -I` 实算 |
| 金标等级 | **G1**（model-labeled；无人工专家） | v2 根 `evidenceGradeLevel` |
| dev / acceptance | 16 页 / 8 页，交集 0 | v2 `split` |
| acceptance 切片 | 8 页 / 26 题，sha256 `c9138ba284aacee8199596e64a44a4ca8c767be339cd2aeb0da8e468b9aa4bc7`（只按 `split.acceptance` 过滤页，未改任何框/计数；build 与证据目录两份逐字节一致） | `build/batch51-split/slices/golden-v2-acceptance-slice.json` + `…/acceptance/golden-v2-acceptance-slice.json` |
| acceptance 页清单 | IMG20260810003928(4) / 004353(2) / 004952(2) / 005711(3) / 010034(6) / 010202(3) / 011358(5) / 011800(1) | 同上（括号为金标题数） |
| 提示词 | 生产 `singlePageAssessmentPrompt`（v3.3），2204 字符，sha256 `7f92b456dea4bbdd5d47e4d2f1bff62375b6482298dc6444fe675c54e0a76061`；本阶段从 Kotlin 源重抽，与 `prompt-final.txt` 逐字节一致 | `build/batch51-split/extract_prompt.py` 实跑 |
| 臂 / 提示 | **单臂：模型识别，无 CV/几何 hint**（批次 50 已删除 layout 包/`PageLayoutHint`；用户裁定「切题只走模型识别」） | 计划 §Context |
| 模型 / 端点 | `deepseek-flash` / `https://api.deepseek.com`，temperature 0.1，`response_format=json_object` | `GoldenSplitEvalChannelHarness.kt` + `OpenAiModelProtocol.kt` |
| 凭据 | 只从 gitignored `local.properties` 读入进程环境；脚本断言 key sha8 `cd10efbc`，不打印值 | `build/batch51-split/run_split_acceptance.sh` |
| 图像 | 出网副本按 `EgressImageBudget`（长边 2048 / q88 / q70 回落），harness 用真实 `scaledSize` 复算断言 | `build/batch48-ws2/prepare_egress.py` |
| Gradle | `:core:data:cleanTestDebugUnitTest :core:data:testDebugUnitTest --tests …GoldenSplitEvalChannelHarness`（`--no-daemon`；本阶段串行，运行前确认无活动构建） | `run_split_acceptance.sh` |

## 2. 一次性承诺（跑前写死）

1. acceptance 8 页**只运行一次**（`acceptance-b51-01`）；无论结果如何不重跑、不换绿、不回调用参。
2. 结果只有 `PASS` / `FAIL` / `NOT_EVALUABLE`；未达 ①≥0.9 / ③≤0.05 / `fragmentedQuestions=0` 即如实 FAIL。
3. 门命令：`python -I tools/split_golden/check_split_gate.py --golden …v2.json --app <24 页合并导出>`
   （entry 已用 `--help` 确认）。合并导出 = dev 16 页 + acceptance 8 页（页集各与 `split` 块硬断言，无重复）。

## 3. 必须先说清的口径限制（不得省略）

1. **没有「从未看过的新 acceptance 切片」可用**：本库唯一冻结金标 v2 只有 24 页（dev 16 + acceptance 8），
   `tools/split_golden/tables/` 无 v3 或未用页池。该 8 页已在批次 48（`acceptance-once-01`，
   ① 5/8、③ 3/26）与批次 50（`acceptance-b50-01`，合并 ① 7/24、③ 1/78）各消费一次。
   因此本轮是**同一 8 页的第三次观测**，held-out 属性**不可恢复**；按本阶段判据，
   **不得据此给 citable PASS**——即使读数达标也只能记「新配置下的第三次观测达标，不可引用为未参与调参页面的准确率」。
2. 与调参的隔离是可核的：批次 51 五次 dev run 的 manifest 页集均恒等于 `split.dev` 16 页，
   与 acceptance 交集 0（本阶段实读 5 份 `manifest.json`）；切片脚本另有硬断言。
3. acceptance 8 页有 **0 张双栏**（批次 50 协议 §3.3）；双栏逻辑在该切片上是盲区。
4. 通道差异沿用 dev 登记（直连 HTTPS、PIL 重采样、无设备 DB 落库）；门值不外推为设备产品路径准确率。

## 4. 合并与判门规则（跑前写死，避免事后挑数）

- dev 部分有同提示词两场观测：`dev-v33-01`（① 14/16）、`dev-v33-02`（① 12/16）。
  **主门用 `dev-v33-02`**（冻结提示词的最后一场 dev 观测；不按分数挑选）；
  `dev-v33-01` 作**同数据敏感性读数**另出一份门报告，两者都留档。
- 判定：主门 `gate.passed`（①≥0.9 且 ③≤0.05）**且** `hardRedLines.fragmentedQuestions.value==0` 才算 PASS；
  任一不满足 → FAIL；输入坏/金标冻结失败 → NOT_EVALUABLE；导出缺失 → 如实报缺。
- ③ 分母 = 金标总题数（24 页 = 78 题）；① 分母 = 可比金标页数（24）。

## 5. 跑前预期（先登记，防止事后叙事）

dev 两场已分别有 `fragmented` 2 题（v33-01：004511/011017）与 3 题（v33-02：004511/004854/005855），
其中 004511 是金标坐标帧转置造成的固定伪切残（dev-tuning.md §5.1）。**即使 acceptance 8/8 全对，
合并门的 `fragmented=0` 也已不可能满足** ⇒ 按本阶段判据，本轮预期为 **FAIL**。
acceptance 仍按承诺运行一次并如实记录 ①②③/逐页。

## 6. 命令（跑前登记；证据落本目录 `run/` 与 `gate/`）

```
python -I build/batch51-split/prepare_acceptance_slice.py          # 切片 + sha（已执行，本文件 §1）
python -I build/batch48-ws2/prepare_egress.py build/batch51-split/egress-acceptance <pages.txt>
bash build/batch51-split/run_split_acceptance.sh acceptance-b51-01   # 唯一一次
python -I build/batch51-split/merge_exports.py <dev-export> <acc-export> <out>
python -I tools/split_golden/check_split_gate.py --golden tools/split_golden/tables/split_golden_v2.json \
  --app <merged> --report-json … --evidence-out … --rulings-out …
```
