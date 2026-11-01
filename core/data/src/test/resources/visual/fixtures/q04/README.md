# Q-04 六类 fixture 静态子集（批次 38 T-Q）

判据（准入清单 Q-04 行，`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:212`）：
"六类 fixture 静态子集 + 新组合，独立预期；学科正确性与渲染 fixture 分开"。
oracle 载体（R2 §三）：**fixture + expected 结构 JSON 断言（非人工目测截图）**。

## 语料范围（R3 §4.3 的静态 MVA 必测子集）

| 文件 | 类别 | 计划锚点 |
|---|---|---|
| `screenshot1-ratio-bar` | 截图 1 比例条 | 计划 `:159`（3:1/1:1 精确比例） |
| `screenshot2-fx-area` | 截图 2 F-x 面积 | 计划 `:160`（x=h/2 面积=3F0h/8） |
| `screenshot5-force-diagram` | 截图 5 受力图 | 计划 `:163`（方向遵循物理条件） |
| `screenshot6-plug-stages` | 截图 6 拔塞阶段 | 计划 `:164`（x=0/h/2/h、不显示原始 TeX） |
| `combo-wave-trajectory` | 新组合：波叠加/轨迹采样 | 计划 `:165`（只新增模型程序与数据） |

截图 3（解题流程=原生卡片/公式）与截图 4（问答=4E.2 原生表单）按 R3 §4.3 **不属于 JS 静态
子集**（"截图 3 主要验原生卡片/公式；截图 4 属 4E.2 表单，后置"），不进本语料。

## 每类三件套

- `*.fixture.json` —— 冻结输入：SceneV1 场景 + metrics（contentId 冻结度量）+ labels
  （contentId → 声明文本）+ `subject` 块（学科正确性金标的**声明参数**：函数定义、比例、
  受力方向、阶段映射、采样参数）。
- `*.drawops.json` —— 生产编译器（`core/data/src/main/resources/visual/visual-compiler.js`）
  对上述场景的**逐字节真实输出**（无手写 DrawOps）。重生成/漂移门：
  `node tools/visual_compiler/compile_fixtures.mjs`（`--write` 重生成）。
- `*.oracle.json` —— **独立预期**（渲染结构 oracle）：op 序列、stats、viewBox、颜色、
  stroke 规格、label 终位置（按 `x=anchor−w·hFrac; y=anchor−h·vFrac+baseline` 独立推导）、
  可精确手导的路径坐标。与 fixture/drawops 分文件，断言由 JVM 门逐字段执行。

## 学科正确性与渲染 fixture 分开（计划 `:169`）

- **渲染 oracle**（`*.oracle.json` + `VisualFigureFixtureOracleTest` 的结构比对）回答
  "这份 DrawOps 长什么样"；
- **学科 oracle**（fixture 的 `subject` 块 + 测试内独立重算）回答"画的东西物理上对不对"：
  比例 3:1/1:1 从路径几何实测、面积按 shoelace 对 3F0h/8 闭式、受力方向按杆轴单位向量、
  阶段位置按声明映射换算、波形顶点按叠加函数重算。**截图缺完整题面时不把图线端点当
  物理公理**——学科断言只使用 fixture 自己声明的参数与函数，不引入真实题面假设。

两层的分离由两条反证用例钉住：只破坏 label 位置 → 仅渲染 oracle 红、学科断言仍绿；
只破坏波形顶点坐标 → 仅学科 oracle 红、渲染结构仍绿。

## 执行面

- JVM（`core/data/src/test/.../VisualFigureFixtureOracleTest`）：解析提交的 drawops →
  类型化文档 vs 渲染 oracle 逐字段比对 + subject 独立重算 + 正/负例（几何篡改、
  物理篡改、malformed 变体按码拒绝）。
- 设备半边（截图 1/2/5/6 + 波叠加/轨迹采样的真实渲染与金样）不在本语料，归设备轨
  （Q-04 行交付列）。
