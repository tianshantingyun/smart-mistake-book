# MATH · 等和线（等和线）

## 现在包里的 boundary（断在末尾）

```
定位：数学必修第二册 第六章。三点共线的充要条件是 $\lambda+\mu=1$，前提是 $O$、$A$、$B$ 三点不共线（否则 $\overrightarrow{OA}$ 与 $\overrightarrow{OB}$ 共线，表示不唯一）；系数和不为 1 时点 $
```

## 必须逐字保留的前缀（133 字符，修好的正文要以它开头）

```
定位：数学必修第二册 第六章。三点共线的充要条件是 $\lambda+\mu=1$，前提是 $O$、$A$、$B$ 三点不共线（否则 $\overrightarrow{OA}$ 与 $\overrightarrow{OB}$ 共线，表示不唯一）；系数和不为 1 时点
```

## 该知识点已绑定的材料（13 条）

### 材料 1：[ext-mat-c56b9d7d92-001b] 等和线的定义与四条性质

**摘要**：

平面向量基本定理中，系数之和为定值的点在同一条与基准线平行的直线上，这条直线叫等和线。

**正文**：

## 共线条件\n已知平面内一组基向量 $\\vec{OA}$、$\\vec{OB}$ 及任一向量 $\\vec{OC}$，且 $\\vec{OC}=x\\vec{OA}+y\\vec{OB}$，若 $x+y=1$，则 $A$、$B$、$C$ 三点共线，反之亦成立。\n\n## 等和线的定义\n平面内一组基向量 $\\vec{OA}$、$\\vec{OB}$ 及任一向量 $\\vec{OP}$，且 $\\vec{OP}=x\\vec{OA}+y\\vec{OB}$。若点 $P$ 在直线 $AB$ 上（或平行于 $AB$ 的直线上），且 $x+y=k$（$k$ 为定值），则把直线 $AB$ 以及与直线 $AB$ 平行的直线称为平面向量基本定理系数的等和线。\n\n## 四条性质\n- 当等和线为直线 $AB$ 时，$k=1$。\n- 当等和线在点 $O$ 和直线 $AB$ 之间时，$0<k<1$。\n- 当直线 $AB$ 在点 $O$ 和等和线之间时，$k>1$。\n- 当等和线过点 $O$ 时，$k=0$。

**边界**：

等和线是针对同一组基向量而言的，换基底后等和线随之改变；系数之和相同的点在同一条直线上，但系数本身的值随点的位置变化；判断 $k$ 的范围时要看等和线与点 $O$、直线 $AB$ 的相对位置，不能只看图形的远近。

**适用**：

“求基底系数 x+y 的值或取值范围、判断动点位置与系数和的关系时使用。”

### 材料 2：[ext-mat-c56b9d7d92-004] 利用等和线求系数范围

**摘要**：

把动点轨迹与等和线的平移结合，取临界位置的等和线得到基底系数和的最大值与最小值。

**正文**：

## 方法步骤\n1. 确定基准：以两个不共线向量为基底，直线 $AB$ 为基准等和线（对应系数和为 1）。\n2. 分析动点约束：明确动点的轨迹（线段、圆、封闭区域等），梳理轨迹的边界特征。\n3. 找临界等和线：平移基准等和线，使其与动点轨迹的临界位置（线段端点、区域边界、圆的切线切点）相交或相切，得到系数和的临界值。\n4. 确定范围：由临界值写出基底系数和的取值范围。\n\n## 易错规避\n- 临界位置遗漏：动点轨迹的所有边界点都要逐一分析。\n- 平移方向与系数大小的关系容易混淆：等和线向远离基底公共起点的方向平移时系数和增大，向靠近的方向平移时系数和减小。\n- 忽略轨迹的封闭性：若动点轨迹是封闭区域（三角形、圆等），需要覆盖所有可能的等和线，避免范围缺失。

**边界**：

等和线平移方向与系数和增减的对应关系要记准，方向反了范围就会写反；临界位置通常在轨迹的端点、边界或切点处；动点轨迹为封闭区域时不要把取值范围写成端点之内的开区间而漏掉边界。

**适用**：

“动点在圆、线段或封闭区域内运动，求基底系数和的取值范围时使用。”

### 材料 3：[ext-mat-1110ca3dd4-006] 三点共线与等和线（系数和为 1）

**摘要**：

若 $\vec{AP}=x\vec{AB}+y\vec{AC}$ 且 $x+y=1$，则 P、B、C 三点共线；反之由三点共线可设出系数和为 1 的表示式；一般地 $x+y=k$ 表示与 BC 平行的一组直线。

**正文**：

**基本结论**：\n\n① 三点共线：对 $\vec{AP}=x\vec{AB}+y\vec{AC}$ 来说，P、B、C 三点共线等价于 $x+y=1$；\n\n② 分点公式：点 M 在 BC 上且 CM 与 MB 的比为 $m:n$ 时，$\vec{AM}=\frac{m}{m+n}\vec{AB}+\frac{n}{m+n}\vec{AC}$，两个系数之和恒为 1；\n\n③ 等和线：$x+y=k$（$k$ 为常数）表示与直线 BC 平行的一组直线，$k$ 随直线远离点 A 而增大，因此求 $x+y$ 的取值范围就是求过动点的等和线的 $k$ 的范围。\n\n**应用示例**：动点 M 在线段 BC 上运动，求 $\vec{AM}=x\vec{AB}+y\vec{AD}$ 中 $x+y$ 的取值范围；由等和线知当 M 与 B 重合时 $x+y=1$、与 C 重合时 $x+y=3$，故 $x+y\in[1，3]$。\n\n**重心**：若 G 是 $\triangle ABC$ 的重心，则 $\vec{AG}=\frac{\vec{AB}+\vec{AC}}{3}$，常与三点共线条件联立求参数。

**边界**：

三点共线的结论要求两个向量有同一个起点；用等和线求范围时要把动点的轨迹与等和线的位置一一对应，只取端点值容易漏解；系数可以为负（动点在直线外侧时），求范围不能只考虑正值；分点公式中 $m$、$n$ 与两条线段的对应顺序容易写反，代端点检验最稳妥。

**适用**：

图形中出现三点共线、需要用基底表示向量、或求向量系数和（差）的取值范围。

### 材料 4：[ext-mat-e99f917373-005] 等和线定理及应用

**摘要**：

动点在平行于基底终点连线的直线上时，向量的两组基底系数之和为定值。

**正文**：

### 一、定理\n平面内一个基底 $\{\overrightarrow{OA}、\overrightarrow{OB}\}$ 及任意向量 $\overrightarrow{OP}$ 满足 $\overrightarrow{OP}=\lambda\overrightarrow{OA}+\mu\overrightarrow{OB}$（$\lambda、\mu\in R$）。若点 $P$ 在直线 $AB$ 上或在与 $AB$ 平行的直线 $CD$ 上运动，则 $\lambda+\mu$ 为定值：$|k|=\dfrac{|\overrightarrow{OP_{1}}|}{|\overrightarrow{OQ}|}$，其中 $Q$ 为 $OP$ 所在直线与直线 $AB$ 的交点，反之也成立。直线 $AB$ 以及与 $AB$ 平行的直线 $CD$ 称为等和线。\n### 二、$k$ 的取值与位置关系\n1. 点 $O$ 在直线 $AB$ 与点 $P$ 所在直线 $CD$ 之间时，$k<0$；\n2. $CD$ 过点 $O$ 时，$k=0$；\n3. $CD$ 在点 $O$ 与直线 $AB$ 之间时，$k\in(0、1)$；\n4. 点 $P$ 在直线 $AB$ 上时，$k=1$；\n5. 直线 $AB$ 在点 $O$ 与 $CD$ 之间时，$k\in(1、+\infty)$。\n### 三、应用\n把 $\overrightarrow{OP}$ 表示为 $\overrightarrow{OA}、\overrightarrow{OB}$ 的组合后，系数和问题可借助等和线定理转化为线段比例（或坐标）问题求解，核心是找到过点 $P$ 且平行于 $AB$ 的直线。

**边界**：

必须先写成同一组不共线基向量的线性组合才能谈系数和；过点 $P$ 的等和线要与 $AB$ 平行；判断 $k$ 的范围时点 $O$ 与两条直线的相对位置容易看反，最好配合图形验证。

**适用**：

求 $\lambda+\mu$ 的取值范围、判断系数和的最值、用基底表示向量的最值问题。

### 材料 5：[ext-mat-91e0c2ace8-058] 和为 1 的等和线

**摘要**：

若 $\overrightarrow{OC}=\lambda\overrightarrow{OA}+\mu\overrightarrow{OB}$，则 $A$、$B$、$C$ 三点共线等价于 $\lambda+\mu=1$，这条直线称为和为 1 的等和线。

**正文**：

**适用场景**：对于求解相关向量式 $\overrightarrow{OA}=\lambda\overrightarrow{OB}+\mu\overrightarrow{OC}$（$\lambda$、$\mu\in R$）中参数的值或最值（取值范围）问题，我们常使用等和线处理。\n\n**其和为 1 的等和线（三点共线的充要条件）**：点 $O$、$A$、$B$ 为平面上不共线的三个点，则 $\overrightarrow{OC}=t\overrightarrow{OA}+(1-t)\overrightarrow{OB}\Leftrightarrow$ 点 $C$ 在直线 $AB$ 上；也即若 $\overrightarrow{OC}=\lambda\overrightarrow{OA}+\mu\overrightarrow{OB}$，则 $A$、$B$、$C$ 三点共线 $\Leftrightarrow\lambda+\mu=1$。\n\n**证明（充分性）**：因为 $\overrightarrow{OC}=t\overrightarrow{OA}+(1-t)\overrightarrow{OB}$，所以 $\overrightarrow{OC}-\overrightarrow{OB}=t(\overrightarrow{OA}-\overrightarrow{OB})$，所以 $\overrightarrow{BC}=t\overrightarrow{BA}$，所以点 $C$ 在直线 $AB$ 上。\n\n**证明（必要性）**：若点 $C$ 在直线 $AB$ 上，则 $\overrightarrow{BC}\parallel\overrightarrow{BA}$，即存在唯一的实数 $t$，使得 $\overrightarrow{BC}=t\overrightarrow{BA}$。在直线 $AC$ 外任取一点 $O$，则 $\overrightarrow{BC}=\overrightarrow{OC}-\overrightarrow{OB}$、$\overrightarrow{BA}=\overrightarrow{OA}-\overrightarrow{OB}$，所以 $\overrightarrow{OC}-\overrightarrow{OB}=t\overrightarrow{OA}-t\overrightarrow{OB}$，整理得 $\overrightarrow{OC}=t\overrightarrow{OA}+(1-t)\overrightarrow{OB}$。\n\n**推论（由 $t$ 的取值判断位置）**：当 $t<0$ 时，点 $C$ 在线段 $AB$ 的延长线上；当 $0\le t\le1$ 时，点 $C$ 在线段 $AB$ 上；当 $t>1$ 时，点 $C$ 在线段 $AB$ 的反向延长线上。如果 $\overrightarrow{BC}=t\overrightarrow{BA}$（$t\in R$），则 $\overrightarrow{OC}=t\overrightarrow{OA}+(1-t)\overrightarrow{OB}$，这个式子可以帮助我们在已知 $|\overrightarrow{BC}|$ 与 $|\overrightarrow{BA}|$ 的比例关系的情况下，快速写出 $\overrightarrow{OC}$ 的表达式。

**边界**：

三点共线的充要条件是 $\lambda+\mu=1$，前提是 $O$、$A$、$B$ 三点不共线（否则 $\overrightarrow{OA}$ 与 $\overrightarrow{OB}$ 共线，表示不唯一）；系数和不为 1 时点 $C$ 不在直线 $AB$ 上，而在与 $AB$ 平行的直线上；用 $t$ 判断点的位置时区间端点的含义（$t=0$ 对应点 $B$、$t=1$ 对应点 $A$）要写清；由长度比写表达式时不要把 $t$ 与 $(1-t)$ 的对应边弄反；基底向量必须不共线。

**适用**：

求解向量式 $\overrightarrow{OA}=\lambda\overrightarrow{OB}+\mu\overrightarrow{OC}$（$\lambda$、$\mu\in R$）中参数的值或最值（取值范围）问题；判断三点共线并确定分点的位置。

### 材料 6：[ext-mat-91e0c2ace8-058b] 和为 $k$ 的等和线

**摘要**：

若 $\overrightarrow{OC}=t\overrightarrow{OA}+(k-t)\overrightarrow{OB}$（$k\ne1$），则点 $C$ 的轨迹是平行于直线 $AB$ 的一条直线，$|k|$ 等于等和线到点 $O$ 的距离与直线 $AB$ 到点 $O$ 的距离之比。

**正文**：

**其和为 $k$ 的等和线**：点 $O$、$A$、$B$ 为平面上不共线的三个点，则 $\overrightarrow{OC}=t\overrightarrow{OA}+(k-t)\overrightarrow{OB}$（$k\ne1$，$k\in R$）$\Leftrightarrow$ 点 $C$ 的轨迹是平行于直线 $AB$ 的一条直线。\n\n**证明**：① 当 $k=0$ 时，$\overrightarrow{OC}=t(\overrightarrow{OA}-\overrightarrow{OB})=t\overrightarrow{BA}$，所以点 $C$ 的轨迹是过点 $O$ 且平行于直线 $AB$ 的一条直线。② 当 $k\ne0$ 且 $k\ne1$ 时，取两点 $A_1$、$B_1$，使得 $\overrightarrow{OA_1}=k\overrightarrow{OA}$、$\overrightarrow{OB_1}=k\overrightarrow{OB}$，因为 $\overrightarrow{OC}=t\overrightarrow{OA}+(k-t)\overrightarrow{OB}$，所以 $\overrightarrow{OC}=\frac tk\overrightarrow{OA_1}+\left(1-\frac tk\right)\overrightarrow{OB_1}$，所以点 $C$ 在直线 $A_1B_1$ 上。因为 $\overrightarrow{OA_1}=k\overrightarrow{OA}$、$\overrightarrow{OB_1}=k\overrightarrow{OB}$，所以 $A_1B_1\parallel AB$，所以点 $C$ 的轨迹是平行于直线 $AB$ 的一条直线。此时直线 $A_1B_1$ 是和为 $k$ 的等和线“。\n\n**$|k|$ 的几何意义**：$|k|$ 的几何意义为 $|\overrightarrow{OA_1}|$ 与 $|\overrightarrow{OA}|$ 的比值，即 $|k|=\frac{|\overrightarrow{OA_1}|}{|\overrightarrow{OA}|}=\frac{|\overrightarrow{OB_1}|}{|\overrightarrow{OB}|}=\frac{d_1}{d}$，其中 $d_1$ 为点 $O$ 到直线 $A_1B_1$ 的距离，$d$ 为点 $O$ 到直线 $AB$ 的距离；实际解题时也是通过长度比确定 $k$ 的值。\n\n**$k$ 的取值影响等和线的位置**：若 $k=1$，则等和线即为直线 $AB$；若 $k=0$，则等和线过点 $O$；若 $k\in(0$，$1)$，则等和线在点 $O$ 和直线 $AB$ 之间；若 $k\in(-\infty$，$0)$，则点 $O$ 在等和线和直线 $AB$ 之间；若 $k\in(1$，$+\infty)$，则直线 $AB$ 在点 $O$ 和等和线之间。特别地，若两条等和线关于点 $O$ 对称，则对应的 $k$ 互为相反数。”

**边界**：

$k=1$ 时等和线就是直线 $AB$ 本身（三点共线情形），$k=0$ 时过点 $O$，这两种特殊情形要单独讨论；$|k|$ 由长度比确定，符号由点 $O$ 与直线所在的位置关系确定（同侧为正、异侧为负），符号判断错会导致参数错误；等和线只与 $A$、$B$ 的连线方向有关，求最值时要先确定动点所在直线与 $AB$ 平行的位置关系；基底向量必须不共线；系数和与距离比的对应关系只适用于 $O$ 到两条平行线的距离之比。

**适用**：

系数和 $\lambda+\mu=k$ 为定值的动点轨迹问题；由等和线的位置判断参数和的范围或求最值。

### 材料 7：[ext-mat-91e0c2ace8-059] 等和线的应用

**摘要**：

由 $\overrightarrow{OC}=t\overrightarrow{OA}+(k-t)\overrightarrow{OB}$ 建立动点轨迹与系数和 $k$ 的关系，通过平移和为 1 的等和线，由长度比与点的位置关系求 $k$ 的范围或判断点 $C$ 的位置。

**正文**：

**两种应用**：从前面的知识我们已经知道，$\overrightarrow{OC}=t\overrightarrow{OA}+(k-t)\overrightarrow{OB}$（$k\in R$）实际上建立了点 $C$ 的轨迹与 $k$ 值的关系，因此关于等和线就有以下两种应用：\n\n**第一种**：在已知点 $C$ 的轨迹的情况下，求 $k$ 的取值或取值范围（这种用得比较多）。做法是通过平移和为 1 的等和线，作出符合条件的等和线，从长度比和点的位置两个角度分析。\n\n**第二种**：在已知 $k$ 的取值的情况下，去判断点 $C$ 的位置。\n\n**操作流程（以求 $\lambda+\mu$ 的范围为例）**\n\n① 把待求式写成 $\overrightarrow{AP}=\lambda\overrightarrow{AB}+\mu\overrightarrow{AD}$ 的形式，选取一组不共线的基向量；\n② 作系数和为 1 的等和线（过基向量终点的直线），再平移该直线，使它经过动点 $P$ 的轨迹上符合条件的点；\n③ 由平移后的直线与基向量所在直线的长度比确定 $\lambda+\mu$ 的值，结合点的位置（同侧或异侧）确定范围与最值。

**边界**：

基向量必须不共线；等和线的位置由长度比确定，平移时要注意等和线在点 $O$ 的同侧才对应正的系数和（异侧为负）；求最值要把动点轨迹与等和线的位置关系分析完整（包括相切等临界情形）；系数和的范围要与动点轨迹的实际范围（线段、圆弧等）对应，不能只看端点；用几何位置求解时要写出系数相等的依据（平面向量基本定理）。

**适用**：

已知动点 $C$ 的轨迹求系数和 $\lambda+\mu$ 的最大值或取值范围；已知系数和 $k$ 判断点 $C$ 的位置。

### 材料 8：[ext-mat-30f4986c08-002] 用等和线求系数和最值的步骤

**摘要**：

确定单位线后平移等和线并由长度比求最值

**正文**：

由等和线的结论可知，用基底表示向量时系数和只与相似比有关，而相似比可以用对应高线、中线、角平分线或半径之比来刻画，用高线最便于操作。\n求系数和（或系数差）最值的一般步骤是：第一步确定单位线，即系数和为 $1$ 的那条等和线，通常就是两个参考点连线所在的直线；第二步平移等和线，观察它在何处取得最大或最小值，最值位置通常与区域的边界相切或经过某个端点；第三步由长度比计算最值，把定值写成等和线到基点距离与参考线到基点距离之比。\n这样就把系数的代数问题转化为平行线的平移与距离比问题，避免繁琐的坐标运算。

**边界**：

平移等和线时要保持与参考直线平行，方向不能改变；最值位置要说明落在哪条边界或哪个端点上，否则结论不可靠。

**适用**：

动点在区域内运动时求系数和或系数差的最值

### 材料 9：[ext-mat-d5471920f6-003] 等和线定理与系数和的取值规律

**摘要**：

同一条平行线上的点用基底表示时系数和为定值

**正文**：

设平面内一组基底 $\{e_1，e_2\}$ 与任一向量 $\overrightarrow{OP}=\lambda e_1+\mu e_2$，若点 $P$ 在直线 $AB$ 上（这里的直线端点对应基底的两个向量），或在平行于 $AB$ 的直线上，且 $k$ 为点 $P$ 到基点连线的距离与直线 $AB$ 到基点连线距离的比值，则 $\lambda+\mu=k$ 为定值；反过来也成立。直线 $AB$ 以及与它平行的直线都叫做平面向量基本定理系数的等和线。\n系数和的取值规律是：当等和线恰为直线 $AB$ 时 $k=1$；当等和线在基点与直线 $AB$ 之间时 $k$ 在 0 与 1 之间；当直线 $AB$ 在基点与等和线之间时 $k$ 大于 1；当等和线过基点时 $k=0$；若两条等和线关于基点对称，则对应的定值 $k$ 互为相反数；定值 $k$ 的绝对值与等和线到基点的距离成正比。\n解题时把待求的系数和看作 $k$，再由动点所在平行线的位置确定 $k$ 的范围，就可把代数问题化为几何位置问题。

**边界**：

使用前必须把向量整理成相对同一组基底的两个系数之和的形式；等和线对应的是共起点（基点）的直线族，基点选错会导致定值判断错误。

**适用**：

由基底表示向量的系数和求取值范围或定值

### 材料 10：[ext-mat-c56b9d7d92-003] 利用等和线求基底系数和的步骤

**摘要**：

确定基底后作出等和线，从长度比或点的位置算出满足条件的等和线所对应的系数和。

**正文**：

## 三大步骤\n1. 确定基底（选定两个不共线的向量）。\n2. 平移直线，作出满足条件的等和线。\n3. 从长度比或点的位置两个角度，计算满足条件的等和线的长度比，从而得到系数和的值。\n\n## 常用关系\n基准线 $AB$ 对应 $x+y=1$；若等和线与基准线平行，则 $x+y$ 由基向量公共起点到等和线的距离与到基准线的距离之比确定；当等和线过公共起点 $O$ 时 $x+y=0$。

**边界**：

等和线必须与基准线平行；计算长度比时要选同一个位似中心（基向量的公共起点），比值方向不能颠倒；系数和的值只由长度比决定，与所取的动点无关。

**适用**：

“已知点在某条直线或区域内、求基底系数 x+y 的值时使用。”

### 材料 11：[ext-mat-6a2e9cacfa-012] 平面向量等和线的判定与应用

**摘要**：

若点 C 满足向量 OC 用 OA、OB 表示时两系数之和为定值，则点 C 的轨迹是平行于 AB 的直线。

**正文**：

## 基本结论\n点 $O$、$A$、$B$ 为平面上不共线的三个点：\n1. 系数和为 1 的等和线：$\overrightarrow{OC}=t\overrightarrow{OA}+(1-t)\overrightarrow{OB}$ ⇔ 点 $C$ 在直线 $AB$ 上。\n2. 系数和为 $k$ 的等和线：$\overrightarrow{OC}=t\overrightarrow{OA}+(k-t)\overrightarrow{OB}$（$k\neq1$，$k\in\mathbf{R}$）⇔ 点 $C$ 的轨迹是平行于直线 $AB$ 的一条直线。\n\n## 理解方式\n由 $\overrightarrow{OC}=(1-t)\overrightarrow{OA}+t\overrightarrow{OB}$ 可知，当系数和为 1 时点 $C$ 的轨迹恰是 $AB$ 所在的直线；系数和变为 $k$ 相当于把这条直线按比例平移，因此得到一族平行线。\n\n## 应用\n凡题目给出 $\overrightarrow{OP}=\alpha\overrightarrow{OA}+\beta\overrightarrow{OB}$ 的形式，要求 $\alpha+\beta$ 的取值范围时，都可转化为“动点所在的平行线与 $AB$ 的位置关系”问题，结合图形中动点的可行区域判断即可。

**边界**：

易错点：把系数和与点所在位置的关系记反（和为 1 对应点在 AB 上）；忽略 $k\neq1$ 的限制；判断范围时未结合动点所在的实际区域（如圆内、梯形内）确定平行线的极端位置。

**适用**：

判断系数之和的取值范围、求动点表示的线性组合系数和的问题。

### 材料 12：[ext-mat-30f4986c08-001c] 等和线的定义与主要结论

**摘要**：

以系数和为定值的平行线族判断系数和的范围

**正文**：

设平面内一组基底与任一向量，把向量用这组基底线性表示。若点在同一条直线上移动，表示的系数之和保持定值，这条直线以及与它平行的直线都称为等和线。\n主要结论：当等和线恰为两点连线所在的直线时，系数和等于 $1$；当等和线位于基点与这条直线之间时，系数和介于 $0$ 与 $1$ 之间；当这条直线位于基点与等和线之间时，系数和大于 $1$；两条关于基点对称的等和线，其定值互为相反数；定值的变化与等和线到基点的距离成正比。\n因此求系数和的取值范围，只需判断动点所在区域被哪些等和线夹住。\n由于相似比可以用对应高线之比刻画，系数和的大小也可以通过点到基线的距离比来计算。

**边界**：

等和线必须与基底对应的那条参考直线平行；判断系数和大于 $1$ 还是小于 $1$ ，要看等和线与基点、参考直线的相对位置顺序。

**适用**：

求用基底表示向量时系数和或系数差的最值问题

### 材料 13：[ext-mat-1fee698076-004] 等和线的定义与性质

**摘要**：

若向量 OP 用基底表示为 λOA+μOB，当 P 落在与 AB 平行的直线上时 λ+μ 为定值。

**正文**：

**推导思路**：由三点共线结论，若 $\overrightarrow{OP}=\lambda\overrightarrow{OA}+\mu\overrightarrow{OB}$（$\lambda$、$\mu\in\mathbf{R}$）且 $P$ 在直线 $AB$ 上，则 $\lambda+\mu=1$。当 $P$ 落在与直线 $AB$ 平行的直线上时，由三角形相似可知存在常数 $k$，使 $\overrightarrow{OP}=k\overrightarrow{OP'}$，于是 $\overrightarrow{OP}=k\lambda\overrightarrow{OA}+k\mu\overrightarrow{OB}$；设 $\overrightarrow{OP}=x\overrightarrow{OA}+y\overrightarrow{OB}$，则 $x+y=k(\lambda+\mu)=k$；反之也成立。\n**定义**：平面内取一个基底（即两个不共线的向量 $\overrightarrow{OA}$ 与 $\overrightarrow{OB}$），任一向量 $\overrightarrow{OP}=\lambda\overrightarrow{OA}+\mu\overrightarrow{OB}$，若点 $P$ 在直线 $AB$ 上或在与 $AB$ 平行的直线上，则 $\lambda+\mu=k$（定值），直线 $AB$ 以及与 $AB$ 平行的直线称为等和（高）线。\n**性质**：① 等和线恰为直线 $AB$ 时 $k=1$；② 等和线在点 $O$ 与直线 $AB$ 之间时 $0<k<1$；③ 直线 $AB$ 在点 $O$ 与等和线之间时 $k>1$；④ 等和线过点 $O$ 时 $k=0$；⑤ 两条等和线关于点 $O$ 对称时，定值 $k_1$、$k_2$ 互为相反数；⑥ 定值 $k$ 的绝对值与点 $O$ 到等和线的距离成正比。

**边界**：

使用等和线要求 $\overrightarrow{OA}$ 与 $\overrightarrow{OB}$ 不共线，能构成基底；$k$ 的符号由等和线相对点 $O$ 的位置决定，不能只比较到点 O 的距离大小来确定正负。

**适用**：

求线性组合式（如 2x+y、λ+μ）的最值或取值范围。

