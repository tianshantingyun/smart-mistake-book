# 批次 50 · AUD-08 dev 三臂 / acceptance 单次 逐页对照

金标 v2（G1，model-labeled；24 页 78 题）。① 口径 v2：单题金标页“不拆分=一致”。格内数字 = 应用框数（生产消费语义：`isUsableSplitRegionSet` 不过 = 0 框）。

| 页 | 子集 | 金标题数 | A 无提示 | B hint | B2 hint+prompt | B 的 decision | B 的 status |
|---|---|---:|---:|---:|---:|---|---|
| IMG20260810004304.jpg | dev | 1 | 0 | 0 | 0 | PASS | OK |
| IMG20260810004511.jpg | dev | 3 | 2 | 2 | 2 | SPLIT | OK |
| IMG20260810004854.jpg | dev | 2 | 0 | 0 | 0 | NEED_MORE_IMAGE | OK |
| IMG20260810005144.jpg | dev | 7 | 5 | 0 | 6 | SPLIT | OK |
| IMG20260810005301.jpg | dev | 3 | 2 | 2 | 2 | SPLIT | OK |
| IMG20260810005605.jpg | dev | 2 | 0 | 0 | 0 | NEED_MORE_IMAGE | OK |
| IMG20260810005855.jpg | dev | 3 | 3 | 3 | 2 | SPLIT | OK |
| IMG20260810010306.jpg | dev | 4 | 4 | 0 | 3 | None | ERROR |
| IMG20260810010349.jpg | dev | 4 | 4 | 4 | 4 | SPLIT | OK |
| IMG20260810010633.jpg | dev | 3 | 5 | 0 | 0 | None | ERROR |
| IMG20260810010859.jpg | dev | 4 | 3 | 3 | 2 | SPLIT | OK |
| IMG20260810011017.jpg | dev | 3 | 0 | 0 | 0 | None | ERROR |
| IMG20260810011126.jpg | dev | 2 | 0 | 0 | 0 | PASS | OK |
| IMG20260810011248.jpg | dev | 5 | 4 | 4 | 4 | SPLIT | OK |
| IMG20260810011649.jpg | dev | 3 | 2 | 2 | 2 | SPLIT | OK |
| IMG20260810012103.jpg | dev | 3 | 0 | 3 | 3 | SPLIT | OK |
| IMG20260810003928.jpg | acc | 4 | — | — | — | None | None |
| IMG20260810004353.jpg | acc | 2 | — | — | — | None | None |
| IMG20260810004952.jpg | acc | 2 | — | — | — | None | None |
| IMG20260810005711.jpg | acc | 3 | — | — | — | None | None |
| IMG20260810010034.jpg | acc | 6 | — | — | — | None | None |
| IMG20260810010202.jpg | acc | 3 | — | — | — | None | None |
| IMG20260810011358.jpg | acc | 5 | — | — | — | None | None |
| IMG20260810011800.jpg | acc | 1 | — | — | — | None | None |

## 指标（度量脚本机器口径；dev 切片 16 页 / acceptance 切片 8 页）

| 臂 | ① countExact | ② IoU≥0.5（只度量） | ③ fragment | gatePassed |
|---|---:|---:|---:|---|
| A. LLM-only（无提示） | 0.2500 (4/16) | 0.4231 (22/52) | 0.0000 (0/52) | False |
| B. LLM+hint（产品路径） | 0.2500 (4/16) | 0.3077 (16/52) | 0.0000 (0/52) | False |
| B2. LLM+hint+提示词实验（已回退） | 0.1875 (3/16) | 0.4038 (21/52) | 0.0000 (0/52) | False |
| ACC. acceptance 单次（B 臂） | 0.3750 (3/8) | 0.4231 (11/26) | 0.0385 (1/26) | False |
