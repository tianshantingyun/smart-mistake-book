# 一致性门度量纠正：`centroidDeltaPx` 的定义、方向反解与判据形态

日期：2026-10-29 ｜ 批次 32 T2（驱动）｜ 计划 `.zcode/plans/plan-2026-10-29-batch-32-parity-schema71.md:14,36`
产出：**研究文档（只读）**。本轮**未改任何 Kotlin/测试/产品源码**、**未运行 Gradle**、**未上设备**、
**未做 git 写操作**。文中所有设备读数都是**复读仓内既有日志**，不是本轮重跑；本轮唯一执行的计算是
§2 的 Python 反解（命令与输出见 §2.2 脚注）。

输入（本会话实读）：计划全文；`VisualDrawOpsParityMetrics.kt`（978 行全文）；
`VisualDrawOpsParityInstrumentedTest.kt`（头部 KDoc 与 N09/N11/N12 全文，`:25-80,296-336,354-410,781-850`）；
`docs/research/2026-10-29-parity-calibration.md`（全文，含顶部设备更正块）；
`docs/research/evidence/2026-10-29-batch-31/step08-parity.md`、`step08b-parity-rerun.md`（全文）；
原始日志 `build/device-track-batch31/step08-parity-logcat.txt:105-106,124-129,182-186,217-220,235-242`、
`build/device-track-batch30/step05-parity-logcat.txt:127`；`docs/research/2026-10-29-batch-32-baseline.md`。

---

## 0. 结论摘要

1. **`centroidDeltaPx` 的定义是跨栅格欧氏距离**：`‖c_P − c_S‖₂`，`c_*` 是两侧**各自**的墨量加权质心
   （`VisualDrawOpsParityMetrics.kt:808-812`，质量来自 `massAt`，`:855-858`）。它不是"同一侧平移前后的质心差"，
   两者在数学上是不同的量（§1.2–§1.3）。
2. **精确关系**：设 `v = c_P(0) − c_S`（基线偏移矢量），PDF 侧整块平移 `t` 后读数
   `d(t) = ‖v + t‖`；同一侧的平移量则是 `‖t‖`。二者只在 `v ∥ t` 且同向时相等。
3. **反解成立且读数自洽**：由 P07 基线 `0.5181329158118464` 与 N12 的 +4 px 读数 `3.56014654994809`
   解出 `v_P07 = (−0.449227283, ±0.258179331)`（模长回代 `0.518132915811846`，15 位一致）；
   预测 P07 的 +1 px 读数 = **0.608282**（< 0.75）。P06 由 `|v|=0.32` 与 +1 px 读数 `1.2624767812258337`
   解出 `v_P06 = (0.245724, ±0.204987)`。加性模型 `‖v‖+‖t‖` 被两例同时否证（误差 −0.0575 / −0.9580）。
4. **`1.000` 那条期望是错的，而且不是"数值精度"问题**：它把跨栅格读数当成同侧位移；误差量级
   +0.2625（P06）、−0.3917（P07 预测），远超任何容差。但本轮给出更强的结论——**任何阈值都不可能在
   P07 上同时"接受正例"并"保证识别全部 1 px 位移"**（§1.5 命题 4：可行性条件 `2‖v‖ < s`，而 `2‖v‖=1.036 > 1`）。
   所以正确说法不是"改成能抓 1 px"，而是"质心的保证分辨力是 `> 2·max‖v‖ ≈ 1.04 px`，1 px 交给别的出口"。
5. **正确判据形态**：位移先由 `bestShift`（±3 整数 argmax，带符号）判，`bboxDev` 做整数辅助，
   跨栅格质心**只作形状/权重分布护栏**。`bestShift` 自身有两个边界：搜索半径 3 导致 `+4 px` 饱和到
   `(3,0)`（N04 `:105`、N12 `:182`，实测）；`gain ≤ 0.02` 不触发（判据定义，非实测）。
6. **阈值与盲区要写"语义 + 保证 + 集合"三件套**，不能写标量窗口：
   - 语义：`0.75` = 噪声上界 + 绝对余量，抓形状粗差（N11 实测 1.1665），**不是位移计时器**；
   - 保证：整像素 ≥ 2 px 必红，1 px 不保证；
   - 盲区：`{t : ‖v + t‖ ≤ τ}` = **闭圆盘 `B(−v, τ)`**（P07：中心 `(0.4492, −0.2582)`、半径 0.75，
     沿 `−v̂` 延伸至 `‖t‖ ≤ 1.268`；沿 `+v̂` 只有 `0.232`）。
7. **版本号必须随"断言"变，不只随"数字"变**：`THRESHOLD_VERSION` 现在是 `calibrated-r1`
   （`VisualDrawOpsParityMetrics.kt:242`），但 N09 的断言与 `blindWindow` 文案都已改。设备日志里
   同一个 `thresholds=calibrated-r1` 同时出现在 `blindWindow=text-centroid(0,0.76)px`（旧 APK）
   与现树的 `direction-dependent`（`VisualDrawOpsParityInstrumentedTest.kt:95`）之下——版本号已经不标识判据。
   建议 `calibrated-r2` 或独立 `CRITERION_VERSION`。
8. **外部先例支持"整数峰值先行、质心只做局部精修"**：OpenCV `phaseCorrelate` 的实现说明是
   "先取峰值位置，再在峰值周围 **5×5 加权质心**求亚像素"（`imgproc.hpp` 4.x，行 3052-3057）。这与
   本门应取的形态同构：**整数位移出口在前，质心只在已定位的邻域内作修正**，而不是拿全局质心差当位移计。
9. **本轮的设备证据仍不足以称"已验证"**：现树 N09 已改成 `verdict == F4_GEOMETRY`（`…Test.kt:322-326`，
   提交 `f7c5d4b7`），但批次 31 设备跑的是旧包（`head=59d4a03f`），`N09-P07` **从未执行**
   （`step08-parity.md:51,139-141`；`step08b-parity-rerun.md:72`）。`calibrated-r1` 的 15 例绿轮仍 **UNVERIFIED**。

---

## 1. 一、定义：跨栅格欧氏距离 vs 同侧平移量

### 1.1 代码事实（逐行）

`measureText` 在文本带 `band` 内累加两侧质量与一次矩（`VisualDrawOpsParityMetrics.kt:784-800`）：

```
M_X = Σ_{(x,y)∈band} m_X(x,y)
S_X = Σ m_X(x,y)·(x,y)            // 一阶矩，二维
```

于是质心 `c_X = S_X / M_X`，读数是（`:808-812`）：

```
d = hypot( c_P.x − c_S.x , c_P.y − c_S.y ) = ‖c_P − c_S‖₂
```

质量函数是"相对背景的暗度"（`:855-858`）：

```
m(x,y) = max(0, 1 − L(color(x,y)) / L(backdrop)),
L(c) = (299·R + 587·G + 114·B) / 1000     // 整数除法
```

任一侧 `M = 0` 时 `d = NaN`（`:808-812`），这是与 OpenCV `moments` 文档一致的处置（`m00 = 0` 时质心
未定义，"must be handled explicitly by the caller"，§4）。它与 scipy `ndimage.center_of_mass` 的定义
逐项同构：`Σ(input·grid)/Σ input`（§4）。

### 1.2 同侧平移量：平移等变性（引理 1）

设把某一侧的栅格按整块平移 `t`（本门的 `shift()`，`VisualDrawOpsParityMetrics.kt:510-522`：搬移像素、
空出的像素置为透明 → 合成后等于背景 → 质量为 0）。若**没有墨量离开统计带**，则每个像素的质量连同坐标
一起平移，`M` 不变、`S → S + M·t`，于是

```
c(t) = S(t)/M = c(0) + t        ⇒   ‖c(t) − c(0)‖ = ‖t‖      （精确，无近似）
```

这就是"同侧平移量"：它**等于**平移矢量本身，与基线无关、与另一侧无关。它是唯一能直接测 `t` 的质心型量。

### 1.3 跨栅格读数（引理 2）

本门的 `d` 是**两个不同渲染器**的质心之差。把 PDF 侧平移 `t`：

```
d(t) = ‖(c_P(0) + t) − c_S‖ = ‖v + t‖,     v := c_P(0) − c_S
```

`v` 是基线跨栅格偏移（P07 实测模长 0.518、P06 0.32），其来源是 AA/字体结构差，不是几何错
（`2026-10-29-parity-calibration.md` §1.1 S1–S3）。**`v` 与 `t` 在同一个矢量方程里，单次读数无法分离。**

### 1.4 三个直接推论

**推论 1（偏差有界）**：由反向三角不等式 `| ‖v+t‖ − ‖t‖ | ≤ ‖v‖`，任何一次读数的"看似平移量"
误差都被 `‖v‖` 夹住。P06：`|1.2625 − 1| = 0.2625 ≤ 0.32` ✓；P07：`|3.5601 − 4| = 0.4399 ≤ 0.5181` ✓。

**推论 2（加性模型必错）**：

```
d(t)² − (‖v‖ + ‖t‖)² = 2‖v‖‖t‖(cos θ − 1) ≤ 0,     θ = ∠(v, t)
```

等号只在 `v` 与 `t` 同向时成立。故"基线 + 平移量"的加法模型是对本度量的**系统性高估**：
P06 高估 0.0575、P07 高估 0.9580（§2.5 表）。这也解释了为什么旧文档里"1 px 信号 1.000"看起来"自洽"——
它恰好假定 `v = 0`，而 `v = 0` 只在两侧渲染器完全一致时成立，与本门的用例前提（AA 关 vs AA 开）矛盾。

**推论 3（盲区是圆盘）**：判据 `d ≤ τ` 等价于

```
‖v + t‖ ≤ τ   ⟺   t ∈ B(−v, τ)        （以 −v 为心、半径 τ 的闭圆盘）
```

所以"质心看不出哪些位移"是一个**二维集合**，不是一维区间。P07（`τ=0.75`）：
盘心 `(0.4492, −0.2582)`、半径 0.75；沿 `−v̂`（大致 +x 方向）可覆盖到 `‖t‖ ≤ ‖v‖+τ = 1.268`，
沿 `+v̂` 只覆盖 `τ − ‖v‖ = 0.232`。这就精确复现了设备观测：P07 的 `(+1, 0)` 读数 0.608 落在盘内，
`(−1, 0)` 读数 1.472 在盘外。

### 1.5 命题 4：为什么"用质心保证 1 px"在数学上不可能

设 `N := ‖v‖` 是该夹具的正例噪声上界（本门取 P07 的 0.5181，因为阈值要同时容纳所有正例）。

- **有效性**要求 `τ > N`，否则正例自己就红（这正是旧 0.5 阈值的死因）。
- **保证性**（对幅值 ≥ `s` 的一切位移都判红）要求圆盘 `B(−v, τ)` 与圆 `‖t‖ = s` 不相交。
  盘内最大模长是 `N + τ`，故等价于 `τ + N < s`。
- 两者联立：`N < τ < s − N`，**可行当且仅当 `2N < s`**。

代入 P07：`2N = 1.0363 > 1 = s` ⇒ **不可行**。即：不存在任何 `τ` 能既让 P07 正例通过、又对所有
1 px 位移判红。这不是标定没做好，是量纲/信息量的硬限制。

它的正面表述就是应写进文档的那句：

> **质心护栏的保证分辨力是 `> 2·max‖v‖ ≈ 1.04 px` ⇒ 一切整像素位移 ≥ 2 px 必红；1 px 是否红取决于方向，
> 不可由单一阈值保证。**

P06 单独看是"可行"的（`2×0.32 = 0.64 < 1`，取 `0.32 < τ < 0.68` 即可），但 TEXT 类共用阈值时必须按
最坏夹具（P07）取，于是类级保证只能是 2 px。这解释了为什么 N09 在 P06 上"红得漂亮"（1.2625）而在 P07 上
会读 0.608——同一阈值、同一操作，结论随白例方向翻转。

---

## 2. 二、用批次 31 读数反解方向向量并复核一致性

### 2.1 读数（全部来自既有日志，本轮未重跑）

| 用例 | 侧/操作 | 读数 | 出处 |
|---|---|---|---|
| P07 正例基线 | screen vs pdf，`shift=0` | `centroid=0.5181329158118464`（精确值见失败消息）、`bboxDev=1`、`mass=0.957`、`bestShift=(0,0)` | `build/device-track-batch30/step05-parity-logcat.txt:127`（精确值）、批次 31 `step08-parity-logcat.txt:219-220`（0.52，2 位） |
| N12：P07 +4 px（PDF 侧 `shift(dx=4)`） | 同上 | `centroid=3.56014654994809`、`bboxDev=5`、`mass=0.9574246771879573`、`bestShift=(3,0)`、`bestIou=0.7867`、`iou=0.3650` | `step08-parity-logcat.txt:182-183,186` |
| P06 正例基线 | `shift=0` | `centroid=0.32`（仅 2 位）、`bboxDev=1`、`mass=0.991`、`bestShift=(0,0)` | `step08-parity-logcat.txt:217-218` |
| N09：P06 +1 px | 同上 | `centroid=1.2624767812258337`、`bboxDev=2`、`mass=0.991`、`bestShift=(1,0)`、`bestIou=0.8846`、`iou=0.6897` | `step08-parity-logcat.txt:124-125,129` |
| N09：P07 +1 px | 同上 | **未执行**（循环在 P06 中止） | `step08-parity.md:51,139-141` |

> 注：`PARITY` 汇总行只印 2 位小数（`centroid=1.26` / `0.52`）；精确值分别来自失败消息 `:129` 与批次 30
> 的失败消息 `:127`。引用时必须指明来源行，否则会把 2 位印刷值当精确值。

### 2.2 反解公式

由 `‖v‖² = a²`、`‖v + (t,0)‖² = b²`：

```
(vx + t)² + vy² = b²
       vx² + vy² = a²
⇒ 2·t·vx + t² = b² − a²
⇒ vx = (b² − a² − t²) / (2t)
⇒ vy = ±√(a² − vx²)                     （符号不可由 x 向读数确定，见 2.4）
```

复算命令（本轮实际执行）：

```bash
python - <<'PY'
import math
def solve(a,b,t):
    vx=(b*b-a*a-t*t)/(2*t); return vx, math.sqrt(a*a-vx*vx)
a7,b7,t7=0.5181329158118464,3.56014654994809,4.0
vx7,vy7=solve(a7,b7,t7)
print(vx7,vy7,math.hypot(vx7,vy7),math.hypot(vx7+1,vy7))
a6,b6=0.32,1.2624767812258337
vx6,vy6=solve(a6,b6,1.0)
print(vx6,vy6,math.hypot(vx6-1,vy6),math.hypot(vx6+4,vy6))
PY
```

输出（节选，均为双精度原值的前若干位，`…` = 截断）：
`vx7=-0.449227282668… vy7=0.258179330998… ‖v‖=0.518132915811846 d(+1)=0.608282132823731`；
`vx6=0.245723811567… vy6=0.204987337240… d(-1)=0.781634426612… d(+4)=4.2506685…`。

### 2.3 P07：结果与预测

| 量 | 值 |
|---|---|
| `vx` | **−0.449227282668** |
| `vy²` | 0.066656566955 |
| `|vy|` | **0.258179330998** |
| `‖v‖` 回代 | 0.518132915811846（= 基线读数，15 位一致） |
| 预测 `d(+1)` | **0.608282132823731**（< 0.75，质心不判红） |
| 预测 `d(−1)` | 1.472044932664348（> 0.75，判红） |
| 预测 `d(+0.9)` | 0.519473396475793（≈ 基线；近退化） |
| 预测 `d(+2)` / `d(+3)` | 1.572117 / 2.563805 |

`d(+0.9) ≈ 0.5195` 说明 **0.9 px 的右移与"不平移"在质心标量上几乎不可区分**——这不是噪声，是几何。

### 2.4 P06：结果与不确定度

| 量 | 值 | 说明 |
|---|---|---|
| `vx` | 0.245724 | 用 `a=0.32`（2 位）解出 |
| `|vy|` | 0.204987 | 同上 |
| `vx` 区间 | **[0.244111, 0.247311]** | `a ∈ [0.315, 0.325)` 扫过 |
| `|vy|` 区间 | **[0.195095, 0.214557]** | 同上 |
| 预测 `d(−1)` | 0.781634 | 刚过 0.75，余量仅 0.032 |
| 预测 `d(−0.9)` | 0.6856 | **落在盲盘内**（小数位移） |
| 预测 `d(+4)` | 4.250669 | 未测（N12 用的是 P07） |

**`vy` 的符号不可识别**：单次 x 向平移只给出 `vy²`。要定符号需要一条 y 向读数
（`‖v + (0,t)‖² = a² + 2t·vy + t²`），本批没有。两个符号对 §1.5 的结论无影响（只 `vy²` 进入 `‖v‖` 与
`d(+1)`），但会让盲盘镜像到 x 轴另一侧——写盲区时必须注明"`vy` 符号未定"。

### 2.5 一致性复核：什么算证据、什么不算

| 复核项 | 结果 | 是否独立证据 |
|---|---|---|
| 三角不等式 `|b − ‖t‖| ≤ ‖v‖` | P06 0.2625 ≤ 0.32；P07 0.4399 ≤ 0.5181 ✓ | **弱**（必要条件，非充分） |
| 单个矢量模型精确复现观测 | 2 未知量、2 方程 ⇒ 恒有解 | **不算**（按构造必然成立，是拟合不是检验） |
| 加性模型 `‖v‖+‖t‖` | P06 预测 1.3200 vs 实测 1.2625（−0.0575）；P07 预测 4.5181 vs 实测 3.5601（−0.9580） | **算**：两例同时否证加法模型，矢量模型是唯一幸存者 |
| 旧断言 `centroid = 1.000` | P06 **实测** 1.2625（+0.2625，唯一被执行的否证）；P07 按模型**预测** 0.6083（−0.3917，未执行） | **算**（P06）；P07 为预测 |
| 跨轮确定性 | 批次 30 两轮逐位相同 `0.5181329158118464`；批次 31 两轮逐位相同 `1.2624767812258337` | **算**：排除抖动/偶发（`step08b-parity-rerun.md:48`） |
| **真正独立的预测**：P07 `d(+1) = 0.6083`（由 +4 读数解出，未参与拟合） | **未执行** | **UNVERIFIED** |

**结论**：一致性成立到"模型未被任何现有读数否证、且加法模型被否证"的程度；P06 的方向反解受 2 位印刷值限制，
P07 的方向反解精度受 `vy` 符号限制。**不能**说"模型已被独立读数确认"。

### 2.6 平移前提的核查（"整块平移"是否真的成立）

反解假定 PDF 侧质量被**刚性搬运且未离开统计带**。可核的间接证据：

- `bboxDev` 从基线 1 增到 5（`= 1 + 4`），`+1` 情形从 1 增到 2（`= 1 + 1`）——四边最大偏离恰好增加平移量；
- `mass` 在日志精度上不变（P07 基线 3 位 `0.957` vs 平移后全精度 `0.9574246771879573`，前 3 位相同；
  P06 `0.991` → `0.991`）。

**但**：日志只印四边最大偏离与 3–4 位质量的 `massRatio`，**不印逐边墨迹框**。若右边缘恰好被统计带裁掉
若干列，`bboxDev` 仍可能由未被裁的左边给出 5。因此"无裁剪"是**推断而非直测**，本项标 **UNVERIFIED（弱）**。
建议复跑时把 `screenBox/pdfBox` 逐边打进 `PARITY_CONTROL_DETAIL`——这是零成本且能一次性关掉这个不确定性的改动。

---

## 3. 三、正确判据形态：位移靠 shift/bbox，质心只作辅助

### 3.1 四个量的分工（按"是否真的估计 t"排序）

| 量 | 定义（代码位置） | 对平移 `t` 的响应 | 作用 |
|---|---|---|---|
| **同侧质心差** `Δc_P` | `‖c_P(t) − c_P(0)‖`（需暴露逐轴质心，当前未暴露） | **`= ‖t‖` 精确**（无裁剪时） | **仅用于负控/仪器自证**：证明"平移被实现了 t"，不是生产判据 |
| **`bestShift`** | `[-3,3]²` 内掩膜 IoU 的 argmax（`:356-369`；语义见 `:915-918`：`+dx` = PDF 内容右移 `dx`） | `= t`（`‖t‖ ≤ 3`）；`|t| ≥ 4` 饱和到边界；带符号 | **位移主判据** |
| **`bboxDev`** | 四边最大绝对偏离（`:801-807`、`:838-853`） | 每边 `|d₀ − t_axis|`；不一定增加 | **整数辅助**（见 3.2 的保证边界） |
| **`centroidDeltaPx`** | `‖c_P − c_S‖`（`:808-812`） | `‖v + t‖`，方向相关 | **形状/权重分布护栏，不作位移判据** |

### 3.2 每个出口的保证与盲区（必须随判据一起写）

| 出口 | 保证 | 盲区（已实测或有推导） |
|---|---|---|
| `bestShift ≠ (0,0)` 且 `gain > 0.02` | 对 `‖t‖ ≤ 3` 的整像素位移，只要位移让掩膜重叠显著改善即可给出**带符号**的 `t`；P06 +1 px 实测 `(1,0)`、`gain=0.8846−0.6897≈0.195` | ① 搜索半径 3：`+4 px` 读 `(3,0)`（N04 `:105`、N12 `:182`）——**幅度饱和，只能当"非零"用**；② `gain ≤ 0.02` 时不触发（AA 主导掩膜差时 IoU 对 1 px 不敏感）；③ 只反映掩膜，不反映内部灰度分布 |
| `bboxDev > 1` | 整数指标，无浮点余量问题；基线 `d₀ ∈ {−1,0,1}` 时，`|t| ≥ 3` 必使某边 `|d₀ − t| ≥ 2` ⇒ **保证 ≥ 3 px/轴** | 1 px 的命中**依赖基线偏移的符号与方向**：基线 `dev=1` 且平移与之同向时读 2（P06 实测），反向时读 0 而漏判。故 `bboxDev` 不能单独承担 1 px |
| `centroidDeltaPx > τ` | 一切整像素位移 ≥ 2 px 必红（§1.5：`‖v‖+τ = 1.268 < 2`） | 1 px 不保证：P07 `(+1,0)` 读 0.608；小数位移的盲盘 `B(−v, 0.75)` 沿 `−v̂` 可达 1.268 px |
| 同侧质心差 | `= ‖t‖` 精确 | 需要"同一侧、同一渲染器的两次渲染"，生产判据里不存在这个基线，只能用于负控 |

**因此**："由 bbox/shift 兜底"要写成两句话，而不是一句：

> `bestShift` 是**主**位移判据（带符号、不看基线）；`bboxDev` 是**辅**整数判据（其 1 px 命中依赖基线偏移方向，
> 最坏只保证 ≥ 3 px/轴）；质心**不是**位移判据，它的保证分辨力是 ≥ 2 px。

计划把 bbox 与 shift 并列（`plan-…-schema71.md:14`），这个并列会让人以为 bbox 独立承担 1 px——实际上
P06 的 1 px 是"基线 dev 恰为 1 且同向"的运气，不是一般保证。必须写明。

### 3.3 判据形态（建议，与现树 `rawTextVerdict` 对齐）

现树（`VisualDrawOpsParityMetrics.kt:666-721`）已经是"硬几何护栏优先"的正确形态：

```
0. 硬几何护栏（任一成立 → F4_GEOMETRY）
   a. 任一侧带内无墨                       :670-673
   b. bboxDev > 1                          :675-680
   c. !(centroidDelta ≤ τ_c)               :684-689   ← 角色：形状护栏
   d. bestShift ≠ (0,0) 且 gain > 0.02      :692-700   ← 角色：位移主判据
1. F1 字体/覆盖率（massRatio / TV）        :703-711
2. F2 AA（edgeMean / IoU 只报不判）        :714-719
3. PASS
```

两条判据**顺序**建议（不改判定结果，只改证据质量）：

1. **把 d 放到 c 之前**：设备日志里 N09-P06 的理由只写了 `text bboxDev=2px > 1px`（`:126`），
   实际上 `bestShift=(1,0)`、`centroid=1.2625` 也都成立。主判据的证据不应被辅助判据遮蔽。
2. **`reasons` 不要 first-match-only**：现在每个护栏都 `return`，日志只留第一条。验收账要的是
   "哪几条出口同时命中"，应把全部命中项写进 `reasons` 再返回（判定不变）。

### 3.4 阈值写法模板（可直接搬进 KDoc / 日志）

```
maxCentroidDeltaPx = 0.75
  语义：跨栅格质心偏移的噪声上界 + 绝对余量（P07 AA 结构噪声 0.5181，余量 0.232）。
  用途：抓形状/权重分布粗差（N11 旋转实测 1.1665，余量 0.4165）。
  保证：整像素位移 ≥ 2 px 必红（在 max‖v‖ = 0.5181 的夹具上：‖v‖+τ = 1.268 < 2）；1 px 不保证。
  非用途：位移计时器；不得用于"这个位移一定被抓到"的结论。
```

配套必须出现的两句（缺一句就会误导）：

```
d(t) = ‖v + t‖，v = 基线跨栅格质心偏移（P07 = (−0.4492, ±0.2582)，‖v‖ = 0.5181）。
判据 d ≤ τ 的通过集 = 闭圆盘 B(−v, τ)；P07 该盘沿 −v̂ 覆盖到 ‖t‖ ≤ 1.268，含 (+1,0)。
```

### 3.5 盲区声明：写集合，不写标量窗口

| ❌ 会误导的写法 | ✅ 应改成 |
|---|---|
| `blindWindow=text-centroid(0,0.76)px` | `blindSet=B(−v,τ_c)（per-fixture；P07 心 (0.4492,−0.2582) r=0.75）` |
| "文本亚像素几何在 (0,0.76) px 不可分辨" | "质心对 1 px 位移的可见性由方向决定：P07 `(+1,0)` 读 0.608、`(−1,0)` 读 1.472" |
| "1 px 平移 ⇒ centroid = 1.000（平移不变性）" | "同侧质心差 ⇒ 1.000；跨栅格读数 ⇒ `‖v+t‖`。前者只用于负控自证" |
| "阈值 0.75 能抓到 1 px 位移" | "0.75 抓形状粗差；1 px 由 `bestShift`（主）与 `bboxDev`（辅）承担" |
| "质心 + bbox 双保险抓 1 px" | "`bestShift` 主判、`bboxDev` 辅、质心不作位移判据；三者保证不同，见 3.2" |
| "1 px 位移仍会红（N09 证明）" | "N09 证明 P06 的 1 px 在 bbox 上红；P07 的 +1 px **未执行**（按模型质心读 0.608，`bestShift` 是否给出 `(1,0)` 未验证）" |

### 3.6 版本与证据绑定

- `THRESHOLD_VERSION = "calibrated-r1"`（`VisualDrawOpsParityMetrics.kt:242`）在**数值未变、断言变了**之后
  没有升级。后果已经发生：设备日志（旧 APK）把 `thresholds=calibrated-r1` 与
  `blindWindow=text-centroid(0,0.76)px` 印在一起（`step08-parity-logcat.txt:110` 等 15 行），
  现树同一版本号印 `direction-dependent`（`VisualDrawOpsParityInstrumentedTest.kt:95`）。
  **同一个版本标识承载了两套互相矛盾的声明**，任何"按版本引用"的复核都会读到错的那套。
- 建议：改判据语义 ⇒ 升 `calibrated-r2`（或新增 `CRITERION_VERSION`/把 `blindWindow` 文本纳入版本），
  并把"数字变了才升版本"这条隐含规则改成"**声明变了就升版本**"。
- 建议：`PARITY_GATE` 行同时打印 `‖v‖` 的实测值或引用键（如 `baselineV=P07:0.5181`），
  否则读者无法自己算出盲盘。

### 3.7 现在仍可能误导的具体位置（本轮实读）

| 位置 | 状态 |
|---|---|
| `VisualDrawOpsParityMetrics.kt:26-38`（文件头 KDoc） | **已改正**：写明跨栅格、方向相关、1 px 由 bbox/bestShift |
| `VisualDrawOpsParityMetrics.kt:186-196`（`TextThresholds` KDoc） | **已改正**：0.75 的依据改为噪声上侧 + N11 |
| `VisualDrawOpsParityInstrumentedTest.kt:25-80`（类头 KDoc） | **已改正**：含"撤回 (0,0.76) 标量窗口" |
| `VisualDrawOpsParityInstrumentedTest.kt:95`（日志串） | **已改正**为 `direction-dependent` |
| `VisualDrawOpsParityInstrumentedTest.kt:299-313`（N09 注释） | **已改正**：解释跨栅格 + 0.608 反解 |
| `THRESHOLD_VERSION`（`:242`） | **未改正**：语义变了、版本没升（§3.6） |
| 批次 31 设备日志 `build/device-track-batch31/step08-parity-logcat.txt:110,114,…` | **旧串仍在**（旧 APK 产物）。批次 32 复跑必须显示新串，否则说明 APK/源漂移（批次 31 记录 `2026-10-29-batch-31-record.md:233` 已登记该漂移） |
| `docs/research/2026-10-29-parity-calibration.md` 正文 | 顶部有更正块，正文的 `1.000 / 下余量 0.250` 按补记惯例保留——**引用只能引更正块** |
| 计划 `plan-…-schema71.md:14` "bbox/shift 兜底" | 需按 §3.2 拆成"主/辅"两句，否则会被读成 bbox 独立保证 1 px |

---

## 4. 四、GitHub 参考（图像配准 / 质心偏移度量）评分

取数方式：`gh api repos/<owner>/<repo>`、`gh api … /contents/… -H "Accept: application/vnd.github.raw"`
与官方 docs（2026-10-29 本会话实取）。**通道记录**：`curl` 直连 raw.githubusercontent 两次失败
（exit 56 / exit 28），改走 `gh api`；scikit-image 的 contents API 对 `skimage/registration/...` 返回 404，
改用官方文档站 + `LICENSE.txt`（API 取回，首行 `License: BSD-3-Clause`）。
**评分口径**：5 = 宽松 license（MIT/Apache/BSD/ISC）+ 近 90 天有推送 + 语义可直接借用；4 = 宽松且可用但
需重集成或 >90 天未动；3 = 宽松但归档/停更；2 = license 无碍但语义不适用；1 = 强 copyleft（可读不可纳入）。
**"适配度"单列**，避免把 license 分当成技术适配分。

| 项目 | license | ★ / pushed（实取） | 与本度量的关系（原文要点） | license 分 | 适配度 | 采纳 |
|---|---|---|---|---|---|---|
| [scipy/scipy](https://github.com/scipy/scipy) `ndimage.center_of_mass` | **BSD-3-Clause** | 15,165★ / 2026-10-29 | 质心定义 `Σ(input·grid)/Σ input`；`m00=0` 时返回 `inf/NaN` 并告警（源码 `scipy/ndimage/_measurements.py:1485-1556`，本会话 `gh api` 实读） | **5** | **5** | **采纳为定义基准**：本门 `measureText` 的加权质心与其逐项同构，`NaN` 处置同向 |
| [opencv/opencv](https://github.com/opencv/opencv) `moments` | **Apache-2.0** | 91,361★ / 2026-10-28 | `centroid = (m10/m00, m01/m00)`；**`m00 == 0` 时质心未定义，"must be handled explicitly by the caller"**（`modules/imgproc/include/opencv2/imgproc.hpp` 4.x，:3902-3906） | **5** | **5** | **采纳**：质心公式与退化处置的第二个独立先例 |
| [opencv/opencv](https://github.com/opencv/opencv) `phaseCorrelate` | Apache-2.0 | 同上 | "computes the peak location and computes a **5x5 weighted centroid around the peak** to achieve sub-pixel accuracy"；`(Δx,Δy)=weightedCentroid{argmax r}`；Hanning 窗去边缘效应；`response∈[0,1]` 表示单峰程度（同文件 :3037-3063） | **5** | **5** | **采纳其结构**：**整数峰值先行、质心只在已定位峰附近做精修**——与本门"bestShift 主、质心辅"同构；**不引入依赖** |
| [scikit-image/scikit-image](https://github.com/scikit-image/scikit-image) `phase_cross_correlation` | **BSD-3-Clause**（`LICENSE.txt` 实读） | 6,628★ / 2026-10-27 | "Images will be registered to **within 1 / upsample_factor of a pixel**"；`normalization="phase"`；文档同时警告 phase correlation "works well under different illumination, but is **not very robust to noise**"（官方 API 页） | **5** | **4** | **列为升级路径，本轮不引入**：亚像素需求出现时才用（`upsample_factor` 给出可复算精度）；当前 AA 噪声正是其弱项 |
| [SimpleITK/SimpleITK](https://github.com/SimpleITK/SimpleITK) / [ITK](https://github.com/InsightSoftwareConsortium/ITK) | **Apache-2.0** | 1,093★ / 1,666★，均 2026-10-28/29 | 配准 = "metric + optimizer + interpolator + transform"的迭代估计；**质心对齐只作可选初始化**，平移由强度相似度优化得到（`registrationOverview` 页，WebFetch 摘要） | **5/5** | **3** | **不采纳（依赖重量）**；采纳其**框定**：平移是估计问题，不是质心差问题 |
| [kornia/kornia](https://github.com/kornia/kornia) | **Apache-2.0** | 11,452★ / 2026-10-28 | `geometry/subpix` = `dsnt` / `nms` / `spatial_soft_argmax`；无相位相关/平移配准模块（目录 `gh api` 实取） | **5** | **2** | **不采纳**：语义不对口（描述子采样与软 argmax，不是两图平移估计） |
| [mapbox/pixelmatch](https://github.com/mapbox/pixelmatch) | **ISC** | 7,005★ / 2026-10-06 | 纯逐像素差 + AA 跳过；**不做任何位移估计**（`threshold` 0.1、`includeAA=false`） | **5** | **1** | **不采纳（对照项）**：说明"只做差分的门"必须另配位移出口——本门的 `bestShift` 就是那个出口 |

**从外部可迁移的三条做法（各指名它消灭的失败）**

1. **整数峰值 → 局部质心**（OpenCV `phaseCorrelate`）→ 消灭"用全局质心差冒充位移量"这一类错误。
   本门的落法：`bestShift` 先定整数位移；质心只在"形状是否被改"这一问题上用，且用跨栅格差的模长。
2. **退化输入显式未定义**（scipy `center_of_mass` 的 `inf/NaN`、OpenCV `moments` 的 `m00==0`）→
   消灭"墨量为 0 时质心读出一个看似正常的数"。本门已是 `NaN`（`:808-812`），且 N10 证明它会红。
3. **亚像素精度必须由可复算的因子给出**（scikit-image 的 `1/upsample_factor`）→ 消灭"亚像素分辨率
   只能靠调阈值猜"。若将来要 < 1 px 的位移读数，走 FFT 相位相关并显式声明 `upsample_factor`，
   **不要**再动质心阈值。

---

## 5. 未验证项（UNVERIFIED，不得作为事实引用）

1. **现树 N09 从未上设备**：`n09OnePixelTextShiftIsCaught` 已改为 `verdict == F4_GEOMETRY`
   （`…Test.kt:322-326`，提交 `f7c5d4b7`），批次 31 设备跑的是旧包（`head=59d4a03f`）；
   `N09-P07` 仍未执行；`calibrated-r1` 的 15 例绿轮 **未取得**。
2. **P07 的 +1 px 读数 0.6083 是预测，不是测量**（由 +4 读数反解，未执行）。
3. **P07 基线精确值来自批次 30 的失败消息**（`step05-parity-logcat.txt:127`），不是批次 31 的读数；
   批次 31 只印 2 位（`0.52`）。跨构建真正逐位相等**未验证**（只验证了各自批内两轮逐位相同）。
4. **P06 基线只有 2 位**，故其方向向量只有区间（`vx∈[0.2441,0.2473]`、`|vy|∈[0.1951,0.2146]`）；
   精确值需复跑打印全精度。
5. **`vy` 符号未定**（两个夹具）：无 y 向读数，盲盘在 x 轴上下两侧镜像等价。
6. **"无墨量离开统计带"是推断**：依据 `bboxDev = 1 + 4` 与日志精度内质量不变；逐边墨迹框未入日志。
7. **无任何 −1 px / 纵向平移 / 小数位移的设备读数**；§2 的 `d(−1)`、`d(+0.9)`、`d(+4)` 均为推导。
8. **`bestShift` 的 `gain ≤ 0.02` 盲区**只有 P06/P07 两个正例的 `(0,0)` 与 P06 的 `(1,0)` 支持，
   未在其它字形/AA 状态下测。
9. **外部语义只读了文档/头文件**，未在本仓运行 OpenCV / scikit-image / scipy / SimpleITK（未新增依赖）。
10. SimpleITK 的语义取自 `registrationOverview` 页的 WebFetch 摘要（非逐字引用，页面未在本会话逐句核对）；
    ITK 源码头文件取数失败（路径猜错），**ITK 源码未读**。

---

## 6. 来源

**仓内（本会话实读）**

- `.zcode/plans/plan-2026-10-29-batch-32-parity-schema71.md:14,18-25,36`
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityMetrics.kt`
  全 978 行；关键 `:26-38,121-135,186-196,242,248-256,265,356-369,510-522,666-721,758-825,838-853,855-858,916-943`
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt`
  `:25-80,92-96,296-336,354-410,781,812-850`
- `docs/research/2026-10-29-parity-calibration.md`（全文 + 顶部设备更正块）
- `docs/research/evidence/2026-10-29-batch-31/step08-parity.md:51,93-116,139-148`；
  `step08b-parity-rerun.md:44-53,71-72`
- `docs/research/2026-10-29-batch-31-record.md:233`（源/APK 漂移登记）
- `docs/research/2026-10-29-batch-32-baseline.md`（D1 项：N09 源侧已改）
- `build/device-track-batch31/step08-parity-logcat.txt:105-106,110,124-129,182-186,217-220,235-242`
- `build/device-track-batch30/step05-parity-logcat.txt:127`（`0.5181329158118464` 的唯一精确出处）
- `build/device-track-batch31/step08b-parity-rerun-logcat.txt:147`（N12 复跑同值）

**外部（2026-10-29 本会话 `gh api` / 官方文档实取）**

- scipy `center_of_mass`：https://github.com/scipy/scipy —— `scipy/ndimage/_measurements.py:1485-1556`；
  BSD-3-Clause、15,165★、pushed 2026-10-29。
- OpenCV `moments` / `phaseCorrelate` / `matchTemplate`：
  https://github.com/opencv/opencv —— `modules/imgproc/include/opencv2/imgproc.hpp`（4.x）`:3037-3063`（峰值 +
  5×5 加权质心）、`:3902-3906`（`m10/m00` 与 `m00==0`）、`:3976-3981`（`TM_CCOEFF_NORMED`）；
  Apache-2.0、91,361★、pushed 2026-10-28。⚠️ `docs.opencv.org` 在本会话返回 403 / 超时，改用仓库头文件。
- scikit-image `phase_cross_correlation`：https://scikit-image.org/docs/stable/api/skimage.registration.html
  （"within 1 / upsample_factor of a pixel"、"not very robust to noise"）；
  https://scikit-image.org/docs/stable/api/skimage.measure.html （`centroid` / `centroid_weighted` 定义）；
  `LICENSE.txt` 首行 `License: BSD-3-Clause`；6,628★、pushed 2026-10-27。
- SimpleITK 配准概览：https://simpleitk.readthedocs.io/en/master/registrationOverview.html
  （metric + optimizer + interpolator + transform；质心对齐只作初始化）
  ——本会话以 WebFetch 摘要形式取得，未见逐字原文。Apache-2.0、1,093★、pushed 2026-10-28。
- ITK：https://github.com/InsightSoftwareConsortium/ITK —— Apache-2.0、1,666★、pushed 2026-10-29（仅元数据）。
- kornia：https://github.com/kornia/kornia —— `kornia/geometry/subpix/` 目录实取
  （`dsnt.py` / `nms.py` / `spatial_soft_argmax.py`）；Apache-2.0、11,452★、pushed 2026-10-28。
- pixelmatch：https://github.com/mapbox/pixelmatch —— ISC、7,005★、pushed 2026-10-06（仅元数据 + 上一轮实读的语义）。
