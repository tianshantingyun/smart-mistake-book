# MATH · 角平分线定理（角平分线定理）

## 现在包里的 boundary（断在末尾）

```
定位：数学必修第一册 第五章。定理适用于内角平分线与对边的交点，外角平分线的情形公式形式不同（需另记或用向量处理）；比例式中线段的对应关系不能颠倒（左邻边比右邻边等于左段比右段）；求出比例后往往还需结合余弦定理或面积公式才能解出具体边长；三种证明都要用到 $\angle
```

## 必须逐字保留的前缀（127 字符，修好的正文要以它开头）

```
定位：数学必修第一册 第五章。定理适用于内角平分线与对边的交点，外角平分线的情形公式形式不同（需另记或用向量处理）；比例式中线段的对应关系不能颠倒（左邻边比右邻边等于左段比右段）；求出比例后往往还需结合余弦定理或面积公式才能解出具体边长；三种证明都要用到
```

## 该知识点已绑定的材料（13 条）

### 材料 1：[math-fx-pingfenxian-dengmianji-fa] 角平分线长的等面积法

**摘要**：

用 $ ria\angle ABC$ 的面积等于两个小三角形面积之和，解出角平分线长 $t$。

**正文**：

1. 等面积：$S_{\triangle ABC}=S_{\triangle ABD}+S_{\triangle ACD}$，即 $\dfrac{1}{2}bc\sin A=\dfrac{1}{2}c\cdot t\sin\dfrac{A}{2}+\dfrac{1}{2}b\cdot t\sin\dfrac{A}{2}$。
2. 整理得 $t=\dfrac{2bc\cos\frac{A}{2}}{b+c}$（再用 $\cos\dfrac{A}{2}=\sqrt{\dfrac{1+\cos A}{2}}$ 可化为边式）。
3. 等价结论：$t^2=bc\left(1-\dfrac{a^2}{(b+c)^2}\right)$。

**边界**：

面积法中两小三角形的高都是 $t$、底角都是 $\dfrac{A}{2}$，方向不能写错；结果式依赖 $a^2=b^2+c^2-2bc\cos A$ 化简。

**适用**：

求角平分线长或由角平分线长建立方程时。

### 材料 2：[math-fx-pingfenxian-jiao-xingshi] 角平分线的角形式

**摘要**：

在 $ ria\angle ABD$ 与 $ ria\angle ACD$ 中分别用余弦定理，利用 $\cos\angle BAD=\cos\angle DAC$ 建立边的关系。

**正文**：

1. 设 $AD=t$，在 $\triangle ABD$ 中 $\cos\dfrac{A}{2}=\dfrac{c^2+t^2-BD^2}{2ct}$；在 $\triangle ACD$ 中同理。
2. 两式相等（同为 $\cos\dfrac{A}{2}$），配合 $BD+DC=a$ 可解出 $t$ 与分段。
3. 该法与等面积法、比例定理等效，按已知量选择最简便的一条。

**边界**：

三个方法（比例、面积、余弦）结果一致，选取时以未知量最少为准；注意 $t$ 为正且满足三角形不等式。

**适用**：

由角平分线条件（含角）建立边角等式时。

### 材料 3：[ext-mat-103c65c5e1-001d] 解三角形中的角平分线问题

**摘要**：

角平分线把对边分成与两邻边成正比的两段，证明可用等面积法或正弦定理，求长度可用等面积法。

**正文**：

在 $\triangle ABC$ 中，$AD$ 平分 $\angle BAC$ 并与 $BC$ 交于点 $D$，常用结论与方法：\n（1）角平分线定理：$\dfrac{BD}{DC}=\dfrac{AB}{AC}$，即角平分线把对边分成与两邻边成正比的两段。证法一（等面积法）：点 $D$ 到 $AB$、$AC$ 两边的距离相等，故 $\triangle ABD$ 与 $\triangle ACD$ 的面积之比等于 $AB:AC$，而这两个三角形分别以 $BD$、$DC$ 为底时高相同，面积比又等于 $BD:DC$，从而得出结论；证法二用正弦定理，在两个小三角形中分别用正弦定理并利用相等的角化简。\n（2）等面积法：由 $S_{\triangle ABC}=S_{\triangle ABD}+S_{\triangle ACD}$，把三个三角形的面积分别用两边与夹角的正弦表示，可解出角平分线长或相关边长。\n（3）含角度条件时常用二倍角关系：角平分线把 $\angle BAC$ 分成两个相等的小角，可据此列出三角关系式并结合和差角公式化简。

**边界**：

易错点：比例式写反（应是 BD:DC=AB:AC）；等面积法中两个小三角形的高取错（应是同一个高）；已知角平分线长求边长时方程列错导致多解，需结合三角形存在的条件取舍。

**适用**：

已知角平分线求边、面积或长度，以及判定角平分线相关比例关系的题目。

### 材料 4：[ext-mat-e4c87437a2-005b] 用面积法求角平分线段长

**摘要**：

把 $S_{\triangle ABC}$ 拆成两个小三角形面积之和，即可解出角平分线段长度 $AD=\frac{2bc\cos\frac A2}{b+c}$。

**正文**：

**推导**：设 $AD$ 平分 $\angle BAC$，$D$ 在 $BC$ 上。由 $S_{\triangle ABC}=S_{\triangle ABD}+S_{\triangle ACD}$ 得\n$$\frac12 AB\cdot AC\sin A=\frac12 AB\cdot AD\sin\frac A2+\frac12 AC\cdot AD\sin\frac A2$$\n记 $AB=c$、$AC=b$，两边同除以 $\frac12\sin\frac A2$，并用 $\sin A=2\sin\frac A2\cos\frac A2$，得\n$$AD=\frac{2bc\cos\frac A2}{b+c}$$\n\n**用法**：求角平分线长只需 $b$、$c$ 与 $A$ 三个量，比先用余弦定理解出 $BC$ 再算更省步；也可反用该式，由 $AD$ 与两边求角 $A$。\n\n**同源结论**：$S_{\triangle ABD}:S_{\triangle ACD}=AB:AC$，而两三角形以 $BD$、$DC$ 为底时高相同，故 $\frac{BD}{DC}=\frac{AB}{AC}$，即角平分线分对边成两边之比。

**边界**：

公式中 $b$、$c$ 是夹角 $A$ 的两条邻边，分子为 $2bc\cos\frac A2$、分母为 $b+c$，角必须取对；面积拆分时三个系数 $\frac12$ 都不能丢；该式只对内角平分线成立，求外角平分线或中线长需换用其他结论。

**适用**：

已知三角形两边及夹角，求内角平分线长；或已知角平分线长反求边。

### 材料 5：[ext-mat-e4c87437a2-009c] 角平分线分对边成比例

**摘要**：

三角形内角平分线把对边分成与两条邻边对应成比例的两段，即 $\frac{BD}{DC}=\frac{AB}{AC}$。

**正文**：

**定理**：在 $\triangle ABC$ 中，$AD$ 平分 $\angle BAC$ 且 $D$ 在 $BC$ 上，则\n$$\frac{BD}{DC}=\frac{AB}{AC}$$\n**证明思路**：两个三角形 $ABD$ 与 $ACD$ 在 $D$ 处的高相同（都以 $BC$ 为底所在直线），故面积比等于底之比 $\frac{BD}{DC}$；又由 $AD$ 平分角，$A$ 到两边的距离概念与 $\sin$ 面积式给出 $S_{\triangle ABD}:S_{\triangle ACD}=AB:AC$，两者合并即得。\n\n**用法**：\n1. 求比例：直接写 $\frac{BD}{DC}=\frac{AB}{AC}$，把已知比例关系转成边长关系；\n2. 设元运算：把 $BD$、$DC$ 按比例设为含同一未知量的式子（如设 $DC=2x$、$AC=3x$），再用余弦定理在 $\triangle ABD$ 或 $\triangle ACD$ 中列方程求解；\n3. 与面积法配合：由 $\frac{BD}{DC}=\frac{AB}{AC}$ 与 $S_{\triangle ABC}=S_{\triangle ABD}+S_{\triangle ACD}$ 可求角平分线长。

**边界**：

定理中的两段是对边被分成的两段，比的方向要对应（$BD$ 挨着 $AB$）；该结论只对内角平分线成立，外角平分线得到的是 $\frac{BD}{DC}=\frac{AB}{AC}$ 的另一种形式，不要混用；设元时未知量要设成同一个 $x$ 的倍数，否则无法用余弦定理闭合。

**适用**：

条件中出现角平分线交对边于一点，需要求线段比例、边长或配合余弦定理建立方程的题目。

### 材料 6：[ext-mat-98e813b184-009b] 三角形角平分线常用结论

**摘要**：

在 $\triangle ABC$ 中 $AD$ 平分 $\angle BAC$ 时：$\angle BAC=2\angle BAD=2\angle CAD$；内角平分线定理 $\frac{AB}{AC}=\frac{BD}{DC}$；等面积法导出角平分线长公式 $AD=\frac{2bc\cos\frac{\angle BAC}{2}}{b+c}$。

**正文**：

在 $\triangle ABC$ 中，$AD$ 平分 $\angle BAC$，$\angle BAC$、$B$、$C$ 所对的边分别为 $a$、$b$、$c$：\n### 1. 角度的倍数关系\n$$\angle BAC=2\angle BAD=2\angle CAD.$$\n即角平分线把顶角平分，求角平分线长时常用 $\frac{\angle BAC}{2}$ 的正余弦。\n### 2. 内角平分线定理\n$$\frac{AB}{AC}=\frac{BD}{DC}.$$\n即角平分线分对边所得两段与两邻边成比例（可用来“设一段为 $x$、另一段按比例表示”）。\n### 3. 等面积法（角平分线长公式）\n因为 $S_{\triangle ABC}=S_{\triangle ABD}+S_{\triangle ACD}$，所以\n$$\frac12bc\sin\angle BAC=\frac12c\cdot AD\sin\frac{\angle BAC}{2}+\frac12b\cdot AD\sin\frac{\angle BAC}{2}.$$\n整理得 $(b+c)AD=2bc\cos\frac{\angle BAC}{2}$，即\n$$AD=\frac{2bc\cos\frac{\angle BAC}{2}}{b+c}.$$\n（推导中用到 $\sin\angle BAC=2\sin\frac{\angle BAC}{2}\cos\frac{\angle BAC}{2}$。）\n### 用法\n求角平分线长优先用第 3 条（也可用“面积相等”现场推导）；求分点位置用第 2 条；并结合余弦定理把边、角联系起来。

**边界**：

内角平分线定理只对内角平分线成立（外角平分线的比例式不同）；等面积法的三部分面积要不重不漏（$\triangle ABD$ 与 $\triangle ACD$ 的面积都用 $\frac12$ 乘夹边再乘 $\sin\frac{\angle BAC}{2}$）；用 $\sin\angle BAC=2\sin\frac{\angle BAC}{2}\cos\frac{\angle BAC}{2}$ 化简时要保证 $\frac{\angle BAC}{2}$ 在 $0$ 到 $\frac{\pi}{2}$ 之间（余弦为正）；求出的角平分线长要检验是否落在三角形内部。

**适用**：

已知角平分线求线段比、求角平分线长；由面积关系建立关于角平分线长的方程。

### 材料 7：[ext-mat-98e813b184-009c] 用方程组求角平分线的长

**摘要**：

在只知道中线长与角的条件下求角平分线长：先用余弦定理建立第一个方程，再用中线向量（中线定理）建立第二个方程，解出 $a^2+b^2$ 与 $ab$，最后用面积相等建立关于 $CD$ 的方程求解。

**正文**：

例：在 $\triangle ABC$ 中，$C=\frac{\pi}{3}$，$c=2\sqrt3$，$AB$ 边上的中线长为 $2$，$CD$ 为 $\angle ACB$ 的平分线，求 $CD$ 的长。\n### 第一步：用余弦定理建立第一个方程\n在 $\triangle ABC$ 中，由余弦定理得 $c^2=a^2+b^2-2ab\cos\angle ACB$，即\n$$a^2+b^2-ab=12\quad\text{①}.$$\n### 第二步：用中线建立第二个方程\n引入 $AB$ 的中点 $M$，借助向量的线性运算，结合中线长 $CM=2$ 建立边 $a$、$b$ 的第二个方程：设 $M$ 为 $AB$ 的中点，则 $CM=2$，$\overrightarrow{CM}=\frac12\left(\overrightarrow{CA}+\overrightarrow{CB}\right)$，所以\n$$CM^2=\frac14\left(CA^2+CB^2+2\overrightarrow{CA}\cdot\overrightarrow{CB}\right);\quad\text{即}a^2+b^2+ab=16\quad\text{②}.$$\n### 第三步：解方程组并求 $a+b$\n由①②得 $a^2+b^2=14$，$ab=2$；再用完全平方公式得 $(a+b)^2=a^2+b^2+2ab=18$，所以 $a+b=3\sqrt2$。\n### 第四步：用面积相等求角平分线长\n因为 $S_{\triangle ABC}=S_{\triangle ACD}+S_{\triangle BCD}$，所以\n$$\frac12ab\sin60^\circ=\frac12b\cdot CD\sin30^\circ+\frac12a\cdot CD\sin30^\circ.$$\n即 $CD=\frac{\sqrt3ab}{a+b}=\frac{2\sqrt3}{3\sqrt2}=\frac{\sqrt6}{3}$。\n### 方法要点\n“已知角 + 中线”是双条件，两个条件分别用余弦定理与中线（向量）定理转成关于 $a$、$b$ 的方程；“求角平分线长”则用面积相等（等面积法）建立关于 $CD$ 的方程——这是爪型三角形中“多线共存”问题的通用套路。

**边界**：

中线用向量表示时，$\overrightarrow{CM}=\frac12\left(\overrightarrow{CA}+\overrightarrow{CB}\right)$ 的起点必须是被平分边的两个端点，夹角是 $\angle ACB$；面积相等时两小三角形的面积用 $\sin\frac{\angle ACB}{2}$（角平分线把角分成两半），不要用 $\sin\angle ACB$；解方程组得到的 $a$、$b$ 有正负取舍（长度为正）；最后结果要检验是否合理（$CD$ 应小于两边）。

**适用**：

已知三角形的一角、对边与另一边上的中线长，求该角的平分线长。

### 材料 8：[ext-mat-91e0c2ace8-052b] 角平分线定理

**摘要**：

在 $\triangle ABC$ 中，$\angle BAC$ 的平分线交 $BC$ 于点 $D$，则 $\frac{AB}{AC}=\frac{DB}{DC}$，它把角的两边之比与对边被分成的两段之比联系起来。

**正文**：

**定理**：在 $\triangle ABC$ 中，$\angle BAC$ 的平分线交 $BC$ 于点 $D$，则有 $\frac{AB}{AC}=\frac{DB}{DC}$（等价地写成 $\frac{AB}{DB}=\frac{AC}{DC}$，即两边之比等于对边被分成的两段之比）。\n\n**证明方法一（正弦定理）**：因为 $\angle ADB+\angle ADC=\pi$，所以 $\sin\angle ADB=\sin\angle ADC$（两角互补，正弦值相等）。在 $\triangle ABD$ 中，由正弦定理有 $\frac{AB}{\sin\angle ADB}=\frac{DB}{\sin\angle BAD}$；在 $\triangle CAD$ 中，由正弦定理有 $\frac{AC}{\sin\angle ADC}=\frac{DC}{\sin\angle CAD}$。又 $\angle BAD=\angle CAD$，所以 $\frac{AB}{AC}=\frac{DB}{DC}$。\n\n**证明方法二（面积法）**：因为 $\angle BAD=\angle CAD$，所以 $\frac{S_{\triangle ABD}}{S_{\triangle ACD}}=\frac{AB}{AC}$；又 $\frac{S_{\triangle ABD}}{S_{\triangle ACD}}=\frac{DB}{DC}$（同高），故 $\frac{AB}{AC}=\frac{DB}{DC}$。\n\n**证明方法三（作平行线）**：过点 $B$ 作 $BM\parallel AC$，交 $AD$ 的延长线于点 $M$。因为 $AC\parallel BM$，所以 $\angle BMD=\angle DAC=\angle BAD$，所以 $AB=BM$，则由 $\frac{AB}{AC}=\frac{BM}{AC}=\frac{DB}{DC}$ 得 $\frac{AB}{AC}=\frac{DB}{DC}$。\n\n**应用要点**：角平分线定理实际上构建了角平分线分对边形成的两条线段长度与该角的两条邻边长度之间的关系，题目中出现角平分线条件时，可以考虑使用这组关系列式子解决问题。

**边界**：

定理适用于内角平分线与对边的交点，外角平分线的情形公式形式不同（需另记或用向量处理）；比例式中线段的对应关系不能颠倒（左邻边比右邻边等于左段比右段）；求出比例后往往还需结合余弦定理或面积公式才能解出具体边长；三种证明都要用到 $\angle BAD=\angle CAD$ 这一前提，作图或书写时要先说明；定理不涉及角平分线的长度，求角平分线长需用张角定理或面积法等方法。

**适用**：

题目中出现角平分线条件（已知两边求分点比例，或已知比例求边长）时，用这组关系列式求解。

### 材料 9：[ext-mat-7d4877601e-008b] 角平分线的向量表示

**摘要**：

角平分线方向等于两条边单位向量之和的方向，配合角平分线定理可确定分点比例。

**正文**：

角平分线的向量表示与角平分线定理。\n角平分线定理：在三角形 ABC 中，若 AD 是角 A 的平分线并交 BC 于 D，则 $\frac{BD}{DC}＝\frac{AB}{AC}$，据此可以确定分点的比例。\n向量表示：角平分线的方向等于两条边方向单位向量之和的方向，即 $\lambda(\frac{\vec{AB}}{|\vec{AB}|}＋\frac{\vec{AC}}{|\vec{AC}|})$，其中 λ 为参数。用它可以把角相等的条件转化为向量关系。\n进阶结论：三角形三条角平分线的交点是内心，内心向量公式为 $\vec{OI}＝\frac{a\vec{OA}＋b\vec{OB}＋c\vec{OC}}{a＋b＋c}$，其中 a、b、c 分别为角 A、B、C 所对的边长，O 为平面内任意一点。

**边界**：

单位向量相加只给出角平分线的方向，不代表角平分线的长度；若两条边向量不是单位向量，必须先化为单位向量再相加，否则方向会被边长比例扭曲。

**适用**：

涉及角平分线的向量表示、求内心或线段比的问题。

### 材料 10：[ext-mat-1cd2392dbb-007] 角平分线相关结论

**摘要**：

角平分线把对边分成的两段与夹这个角的两边成比例，并给出长度公式。

**正文**：

**分对边成比例**：在 $\triangle ABC$ 中角 $A$ 的平分线交 $BC$ 于点 $D$，则 $\dfrac{BD}{DC}=\dfrac{AB}{AC}=\dfrac{c}{b}$，即角平分线把对边分成的两段与夹这个角的两边成比例。\n\n**长度公式（已知两边及夹角）**：$l_a=\dfrac{2bc\cos\frac{A}{2}}{b+c}$，可由两个小三角形的面积之和等于大三角形的面积推出。\n\n**长度公式（已知三边）**：$l_a^{2}=bc\left[1-\dfrac{a^{2}}{(b+c)^{2}}\right]$，也可写成 $l_a^{2}=bc-BD\cdot DC$（斯库顿定理），常用于已知三边求角平分线的长。\n\n**应用**：由角平分线条件与面积、周长条件联立求边；由角平分线长反求边或角；验证角平分线的性质。

**边界**：

公式中的点 $D$ 必须是角平分线与对边的交点，比例式中的两段与两边必须一一对应；用面积法推导时三个三角形共顶点，注意各部分的底与高不要混淆。

**适用**：

已知两边及角平分线分对边的比例求边长，或由角平分线长反求边与角的题目。

### 材料 11：[ext-mat-1f355a086e-014] 角平分线定理及其证明思路

**摘要**：

三角形内角平分线分对边所得两线段与相邻两边成比例

**正文**：

角平分线定理：在 $\triangle ABC$ 中，若 $AD$ 是 $\angle A$ 的平分线并与 $BC$ 交于点 $D$，则 $\frac{BD}{DC}=\frac{AB}{AC}$，即角平分线把对边分成的两条线段与夹这个角的两边对应成比例。\n该定理可用面积法证明：$\triangle ABD$ 与 $\triangle ACD$ 的高相同，面积比等于 $BD:DC$；而由 $\frac{1}{2}AB\cdot AD\sin\angle BAD$ 与 $\frac{1}{2}AC\cdot AD\sin\angle CAD$ 可知面积比又等于 $AB:AC$，两者结合即得结论。\n解三角形问题中，该定理把角平分线条件转化为线段的比例关系，再与正弦定理、余弦定理联立求值。

**边界**：

定理中的角平分线指内角的平分线；比例式中分子分母的对应关系不能颠倒，$BD$ 对应 $AB$、$DC$ 对应 $AC$；定理给出的是比例关系而不是具体长度。

**适用**：

已知角平分线求线段比、结合面积或正弦定理处理解三角形问题时使用

### 材料 12：[ext-mat-2a2a083eab-006b] 角平分线定理

**摘要**：

三角形内角平分线把对边分成的两段与两邻边成比例的定理

**正文**：

角平分线定理：在△ABC 中，若∠BAC 的平分线 AD 与边 BC 相交于点 D，则 $\frac{AB}{AC}=\frac{BD}{DC}$，即三角形内角平分线把对边分成的两段与相邻两边对应成比例。\n\n应用要点：处理涉及角平分线的长度、比值或面积问题时可直接使用该比例关系；常与面积公式结合使用（被角平分线分开的两个小三角形同高，面积比等于底边之比），也可与正弦定理、余弦定理联立求解。

**边界**：

该比例关系由内角平分线得到，外角平分线对应的关系形式不同，不要混用；使用前要确认 AD 确实是角平分线，且点 D 必须落在对边 BC 上。三角形不存在或角平分线与对边无交点时要另作讨论。

**适用**：

涉及角平分线的长度、比值或面积问题，与正弦定理余弦定理联立求解

### 材料 13：[ext-mat-bfdbca381c-001b] 含角平分线问题的破解方法

**摘要**：

角平分线定理与等面积法在解三角形中的应用

**正文**：

**角平分线定理**：$\triangle ABC$ 中 $AD$ 平分 $\angle BAC$ 交 $BC$ 于 $D$，则 $\frac{BD}{DC}=\frac{AB}{AC}$，即角平分线分对边所得两段与两邻边成比例。\n\n**两种证明思路**：①等面积法——由 $\triangle ABD$ 与 $\triangle ACD$ 面积之比既等于 $\frac{BD}{DC}$（同高），又等于 $\frac{AB\cdot AD\sin\frac{A}{2}}{AC\cdot AD\sin\frac{A}{2}}$（用两边夹角的面积公式），比较即得；②正弦定理法——在 $\triangle ABD$ 与 $\triangle ACD$ 中分别用正弦定理表示 $BD$、$DC$，结合 $\angle BAD=\angle DAC$ 与互补角正弦相等化简。\n\n**等面积法求角平分线长**：由 $S_{\triangle ABC}=S_{\triangle ABD}+S_{\triangle ACD}$，即 $\frac{1}{2}AB\cdot AC\sin A=\frac{1}{2}AB\cdot AD\sin\frac{A}{2}+\frac{1}{2}AC\cdot AD\sin\frac{A}{2}$，可求出角平分线长 $AD$。

**边界**：

角平分线定理给出的是比例关系，不能直接给出长度，求长还需结合面积或余弦定理；用等面积法时两小三角形的高与公共顶点有关，写面积时注意两角都是 $\frac{A}{2}$；求最值问题要注意角的范围与边长为正。

**适用**：

解三角形中含角平分线的求值、证明与范围问题

