# 标注指南（aud08-model-gold-b48-v1，冻结于开标前）

> 冻结声明：本文件在批次 48 v2 金标任何一页标注开始之前写定（2026-11-07，标注会话内）。
> 它不是协议 V1 §S0 意义上的独立"指南文件冻结"流程产物（该流程未执行），而是本批
> model-labeled 代理金标实际使用的操作指南；`promptSha256` 以本文件字节为提示词模板摘要。
> 事后不得更改边界规则；若更改必须升版并重标受影响页。

## 1. 标注单元

- 一页 = 一个 `pageId`（= 文件名去扩展名，如 `IMG20260810003928`）。
- 框 = **题干区域**：从题号印刷体（如 `1.`、`2.`、`一、`）起，到该题题干文字（含选择题的
  全部选项行）结束为止的矩形。
- 选择题：选项行（A/B/C/D）属于题干框内。
- 填空/解答题：学生手写的答案、演算若与题干同处一块，随题干框一并覆盖（框跟印刷题块外缘，
  必要时外扩含行内手写）。
- **不并入框**：页眉/页脚/页码/姓名班级栏、试卷大题标题行（如"一、填空题"）、游离于题块外的
  红笔批注、成块附图（图归属用 `strata.figureOwnership` 登记图属于第几题，不占框）。
- 题内的行间小图（如题干中的函数图随文排）可包含在框内，并在 `strata.hasFigure` 体现。

## 2. 题数与题序

- `questionCount` = 页面上**印刷题号**的不同题目数；题内小问 `(1)(2)(3)` 不另计。
- 从上一页延续、本页无题号的剩余内容：并入该题在本页的框（若有），页级记
  `crossPage=true` 并在 `notes` 说明接续哪一题；纯页尾留白不算题。
- `boxes[].order` = 1 起的整数，按版面阅读顺序（先上下、多栏先左栏后右栏）。
- 无题号且无法归属的独立内容块（如单独一道手写题）：计入 `questionCount`，`order` 按位置，
  `notes` 注明 "unnumbered"。

## 3. 坐标

- 归一化到**整张图像**（非页面裁剪后）：`left/top/right/bottom ∈ [0,1]`，
  `left<right`，`top<bottom`；照片倾斜时框仍取轴对齐外接矩形。
- 每框 `coordConfidence ∈ {high,medium,low}`：high=边界清晰可读；medium=受倾斜/阴影影响
  估读；low=边界模糊或被遮挡。
- 页级 `coordMethod` 固定为 `visual-estimate-from-rendered-image`（模型直读渲染图/裁片放大图）。

## 4. 页级属性

| 字段 | 取值 |
|---|---|
| `subject` | 数学/语文/英语/物理/化学/生物/其他（按内容判定） |
| `layout` | `single-column` / `two-column` / `grid`（表格试卷）/ `notebook`（作业本横线）/ `other` |
| `questionCount` | 见 §2 |
| `strata.questionType` | 每题题型填 `boxes[].questionType`；页级填主导题型（choice/fill/solve/mixed/other） |
| `strata.hasFormula` / `strata.hasHandwriting` / `strata.hasFigure` | 布尔，逐页 |
| `strata.crossPage` | 布尔，逐页 |
| `strata.figureOwnership` | 数组，元素 = 拥有附图的题 `order`（0 = 无） |
| `strata.optionsIncluded` | 选择题选项是否入框（本指南恒 true） |

## 5. 边界锚（正/反例）

- 正例 1：`1. 计算 3+5×2=____` → 框含题号行与算式行；右侧空白不含。
- 正例 2：选择题题干 + 四个选项行 → 一框含全部五行；选项后的作答痕迹随框。
- 正例 3：解答题题干两行半 + 下方大片手写演算 → 框含题干；演算区若与题干连续则并入，
  若明显分离成整页草稿则不含（notes 说明）。
- 反例 1：页眉"XX中学 2026-2027 学年"→ 不入任何框。
- 反例 2：大题标题"二、填空题（每题 3 分）"→ 不入任何框。
- 反例 3：页边红笔"注意书写！"→ 不入任何框。

## 6. 单题页与版本式

- `questionCount < 2` 的页：期望整页一框（"不拆分"口径），除非页面主体是页眉/装饰。
- 试卷多栏：每栏内按上下顺序编 `order`，左栏完成后接右栏。

## 7. 双轮与独立性

- A 轮（主标）：24 页全标，页序 = 抽样计划 §4 表序（00→23）。
- B 轮（复标）：≥8 页，页序乱序（固定种子），在**未读取任何 A 轮 JSON** 之前写入 B 轮 JSON。
- B 轮为同一模型新会话重标，协议 §3.3 意义上**不构成独立标注**，只作一致性证据，不升级等级。
- 裁定（本批为主会话代理角色，同模型自裁定）：A 轮为 v2 基线；B 轮分歧逐条复核图像后
  决定采纳/保留，全部进 `adjudication.decisions[]`；A 轮被改动的框进 `review.changes[]`。

## 8. 坐标空间补遗（v1 → v1.1，2026-11-07，标注第 1 页后、第 2 页前追加）

- 横拍竖页（raw 4096×3072，EXIF orientation=1，页面内容顺时针转 90° 才正读）的页：
  v2 的 `boxes[].left/top/right/bottom` 一律记 **upright 空间**（摆正后 3072×4096 归一化）；
  同时每框附 `rawBox{left,top,right,bottom}`（原始图像空间归一化），两种口径可互查。
- 页级 `orientation` 字段：`{rawSize, rawToUpright: "rotate90cw"|"none", uprightSize}`。
- 未旋转页（raw 即正）`rawBox` 与 `box` 数值相同。已标页 00 属此类，不受影响，无需重标。
- `promptSha256` 仍指 v1 基础规则字节摘要 `408d3337…a980`；本补遗字节摘要
  `guidelineAddendumSha256` 见批 48 记录文件。
