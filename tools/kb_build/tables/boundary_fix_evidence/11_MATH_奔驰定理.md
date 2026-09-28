# MATH · 奔驰定理（奔驰定理）

## 现在包里的 boundary（断在末尾）

```
定位：数学必修第二册 第六章。定理中各向量必须共起点（都是以 $O$ 为起点指向三角形顶点的向量），且 $O$ 与三角形的位置关系要明确（$O$ 为内点时三个面积都取正值，为外点时按位置取负号）；面积的对应关系是“缺少哪个顶点的三角形面积对应哪个顶点的向量”（$S_{\
```

## 必须逐字保留的前缀（130 字符，修好的正文要以它开头）

```
定位：数学必修第二册 第六章。定理中各向量必须共起点（都是以 $O$ 为起点指向三角形顶点的向量），且 $O$ 与三角形的位置关系要明确（$O$ 为内点时三个面积都取正值，为外点时按位置取负号）；面积的对应关系是“缺少哪个顶点的三角形面积对应哪个顶点的向量”（
```

## 该知识点已绑定的材料（14 条）

### 材料 1：[ext-mat-68307cc9a5-001] 三角形“四心”的向量特征与奔驰定理的面积比关联

**摘要**：

归纳重心、内心、外心、垂心的向量本质以及奔驰定理与面积比的等价关系

**正文**：

三角形“四心”的向量本质：\n1. 重心是三条中线的交点，以重心 $G$ 为起点时有 $\vec{GA}+\vec{GB}+\vec{GC}=\mathbf{0}$；\n2. 内心是三条角平分线的交点，对应边长加权的向量和为零，即 $a\vec{IA}+b\vec{IB}+c\vec{IC}=\mathbf{0}$，其中 $a$、$b$、$c$ 为对应边的长；\n3. 外心是三条中垂线的交点，关键特征是到三个顶点的距离相等；\n4. 垂心是三条高线的交点，满足与顶点连线垂直对边的数量积关系。\n\n奔驰定理的核心关联：向量系数比与对应三角形的面积比完全等价，且可直接关联“四心”，是解决面积比例问题的常用工具。\n\n识别时优先匹配向量核心特征：看到 $\vec{GA}+\vec{GB}+\vec{GC}=\mathbf{0}$ 直接判定为重心；垂心可通过点乘为零验证垂直；内心关注单位向量和或边长加权。

**边界**：

四个心的向量特征不能混：外心的核心是“到顶点距离相等”，垂心的核心是数量积为零；内心的表达式系数是边长而不是角度；奔驰定理的面积比关系以点在三角形内部为前提，点在外部时面积比要带符号。

**适用**：

由向量关系式判断点是什么心，或需要把向量系数比转化为三角形面积比时

### 材料 2：[ext-mat-1110ca3dd4-008] 面积比与向量系数（奔驰定理）

**摘要**：

若 $\vec{AP}=x\vec{AB}+y\vec{AC}$，则 $S_{\triangle PAB}:S_{\triangle PCA}:S_{\triangle PBC}=|y|:|x|:|1-x-y|$，即三角形的面积比等于对应向量的系数比。

**正文**：

**结论**：设点 P 与 $\triangle ABC$，若 $\vec{AP}=x\vec{AB}+y\vec{AC}$，则 $S_{\triangle PAB}:S_{\triangle PCA}:S_{\triangle PBC}=|y|:|x|:|1-x-y|$；等价形式为 $S_{\triangle PBC}\vec{PA}+S_{\triangle PCA}\vec{PB}+S_{\triangle PAB}\vec{PC}=\vec 0$，常称为奔驰定理。\n\n**用法**：\n\n① 由向量式求面积比：把向量式化为以同一个顶点为起点的形式，读出系数，按系数之比得面积比。例如由 $\vec{AP}=\frac12\vec{AB}+\frac34\vec{AC}$ 得 $S_{\triangle ABP}:S_{\triangle ACP}=\frac34:\frac12=3:2$；解题时也可取 $\vec{AE}=\frac12\vec{AB}$、$\vec{AF}=\frac34\vec{AC}$，以 $\vec{AE}$、$\vec{AF}$ 为邻边作平行四边形，由底与高的比例关系得到面积比。\n\n② 由面积比求参数：把面积比写成系数比，得到方程从而求出向量式中的参数，常与三点共线条件联立。

**边界**：

三个面积与三个系数的对应顺序容易记错，可用特殊位置检验（P 为重心时三个面积相等，对应系数各为三分之一）；系数为负表示点在该边所对直线的另一侧，求面积比要取绝对值；使用前必须把三个向量统一到同一个起点，否则系数比没有意义；结合图形作平行四边形时注意底边与高的对应。

**适用**：

由向量的线性表示式求三角形面积比，或由面积比反求参数的值与比值。

### 材料 3：[ext-mat-e99f917373-004] 奔驰定理及与四心的面积比

**摘要**：

三角形内一点与三顶点连线把三角形分成三块，三块面积作为系数与三个向量配成零向量。

**正文**：

### 一、奔驰定理\n若点 $O$ 为 $\triangle ABC$ 内一点，则 $S_{\triangle BOC}\overrightarrow{OA}+S_{\triangle COA}\overrightarrow{OB}+S_{\triangle AOB}\overrightarrow{OC}=\boldsymbol{0}$。\n证明要点：在射线 $OB$ 与 $OC$ 上分别取 $B_{1}$、$C_{1}$，使 $O$ 为 $\triangle AB_{1}C_{1}$ 的重心，由重心性质得 $\overrightarrow{OA}+\overrightarrow{OB_{1}}+\overrightarrow{OC_{1}}=\boldsymbol{0}$；设 $\overrightarrow{OB}=p\overrightarrow{OB_{1}}$、$\overrightarrow{OC}=q\overrightarrow{OC_{1}}$（$p、q>0$），利用面积比 $\dfrac{S_{\triangle BOC}}{S_{\triangle B_{1}OC_{1}}}=pq$、$\dfrac{S_{\triangle AOB}}{S_{\triangle AOB_{1}}}=p$、$\dfrac{S_{\triangle COA}}{S_{\triangle C_{1}OA}}=q$ 把面积比代入即得。\n### 二、与四心的面积比\n1. $O$ 是重心：$S_{\triangle BOC}:S_{\triangle COA}:S_{\triangle AOB}=1:1:1$；\n2. $O$ 是内心：$S_{\triangle BOC}:S_{\triangle COA}:S_{\triangle AOB}=a:b:c$（$a、b、c$ 为三边长）；\n3. $O$ 是外心：$S_{\triangle BOC}:S_{\triangle COA}:S_{\triangle AOB}=\sin 2\angle BAC:\sin 2\angle ABC:\sin 2\angle ACB$；\n4. $O$ 是锐角三角形的垂心：$S_{\triangle BOC}:S_{\triangle COA}:S_{\triangle AOB}=\tan\angle BAC:\tan\angle ABC:\tan\angle ACB$。

**边界**：

奔驰定理中点 $O$ 应在三角形内部（否则要按有向面积理解）；由向量等式反推面积比时，每一项的系数对应的是“与顶点相对的那块三角形”的面积，位置不能错；四心的面积比结论要记准。

**适用**：

由 $m\overrightarrow{MA}+n\overrightarrow{MB}+k\overrightarrow{MC}=\boldsymbol{0}$ 判断点的位置、求面积比或求角。

### 材料 4：[ext-mat-91e0c2ace8-060b] 奔驰定理

**摘要**：

若 $O$ 为 $\triangle ABC$ 内一点，则 $S_{\triangle BOC}\overrightarrow{OA}+S_{\triangle COA}\overrightarrow{OB}+S_{\triangle AOB}\overrightarrow{OC}=\vec{0}$。

**正文**：

**奔驰定理**：若点 $O$ 为 $\triangle ABC$ 内一点，则 $S_{\triangle BOC}\overrightarrow{OA}+S_{\triangle COA}\overrightarrow{OB}+S_{\triangle AOB}\overrightarrow{OC}=\vec{0}$。\n\n**拓展（点 $O$ 在三角形外）**：若点 $O$ 为 $\triangle ABC$ 外一点，以 $OA$ 在 $OB$、$OC$ 之间为例，有 $-S_{\triangle BOC}\overrightarrow{OA}+S_{\triangle COA}\overrightarrow{OB}+S_{\triangle AOB}\overrightarrow{OC}=\vec{0}$（即取在另外两个向量之间“的那个向量对应的面积为负）。因为其图形与奔驰的标志特别相似，故称之为奔驰定理。\n\n**应用方向**：奔驰定理实际上建立了 $S_{\triangle AOB}$、$S_{\triangle BOC}$、$S_{\triangle COA}$ 的比例与 $\overrightarrow{OA}$、$\overrightarrow{OB}$、$\overrightarrow{OC}$ 的向量式之间的关系，在一些三角形面积的比例或三角形四心向量式相关问题中有妙用。”

**边界**：

定理中各向量必须共起点（都是以 $O$ 为起点指向三角形顶点的向量），且 $O$ 与三角形的位置关系要明确（$O$ 为内点时三个面积都取正值，为外点时按位置取负号）；面积的对应关系是“缺少哪个顶点的三角形面积对应哪个顶点的向量”（$S_{\triangle BOC}$ 配 $\overrightarrow{OA}$），不能张冠李戴；系数只是与面积的比例关系挂钩，并非对应系数相等；点 $O$ 在三角形边上时某个面积为 0，公式退化；四心问题中要先把离心条件化成向量式再套用。

**适用**：

已知三角形内一点与顶点的向量关系求面积比；已知面积比求向量式中的系数；判断三角形四心的向量式特征。

### 材料 5：[ext-mat-91e0c2ace8-061] 奔驰定理的证明与推论

**摘要**：

用重心的性质证明奔驰定理，并得到推论：若 $x\overrightarrow{OA}+y\overrightarrow{OB}+z\overrightarrow{OC}=\vec{0}$，则三个小三角形的面积之比为 $|x|:|y|:|z|$。

**正文**：

**证明（根据重心的性质推导）**：在射线 $OB$ 与 $OC$ 上分别取点 $B_1$ 与点 $C_1$，使得点 $O$ 为 $\triangle AB_1C_1$ 的重心，并令点 $D$ 为 $B_1C_1$ 的中点，点 $E$ 为 $AB_1$ 的中点。因为点 $D$ 为 $B_1C_1$ 的中点，所以 $\overrightarrow{OB_1}+\overrightarrow{OC_1}=2\overrightarrow{OD}$；又点 $O$ 为 $\triangle AB_1C_1$ 的重心，所以 $\overrightarrow{OA}=-2\overrightarrow{OD}$，所以 $\overrightarrow{OA}+\overrightarrow{OB_1}+\overrightarrow{OC_1}=\vec{0}$。\n\n设 $\overrightarrow{OB}=p\overrightarrow{OB_1}$（$p>0$）、$\overrightarrow{OC}=q\overrightarrow{OC_1}$（$q>0$），则 $\overrightarrow{OA}+\frac1p\overrightarrow{OB}+\frac1q\overrightarrow{OC}=\vec{0}$，且 $|\overrightarrow{OB}|=p|\overrightarrow{OB_1}|$、$|\overrightarrow{OC}|=q|\overrightarrow{OC_1}|$。因为 $S_{\triangle BOC}=\frac12|\overrightarrow{OB}||\overrightarrow{OC}|\sin\angle BOC$、$S_{\triangle B_1OC_1}=\frac12|\overrightarrow{OB_1}||\overrightarrow{OC_1}|\sin\angle B_1OC_1$，所以 $\frac{S_{\triangle BOC}}{S_{\triangle B_1OC_1}}=pq$。同理 $\frac{S_{\triangle AOB}}{S_{\triangle AOB_1}}=p$，$\frac{S_{\triangle COA}}{S_{\triangle C_1OA}}=q$。因为点 $O$ 为 $\triangle AB_1C_1$ 的重心，所以 $S_{\triangle AOB_1}=S_{\triangle B_1OC_1}=S_{\triangle C_1OA}$，所以 $S_{\triangle BOC}\overrightarrow{OA}+S_{\triangle COA}\overrightarrow{OB}+S_{\triangle AOB}\overrightarrow{OC}=\vec{0}$。\n\n**推论**：已知 $O$ 为 $\triangle ABC$ 内一点，且 $x\overrightarrow{OA}+y\overrightarrow{OB}+z\overrightarrow{OC}=\vec{0}$（$x$、$y$、$z\in R$，$x\ne0$，$y\ne0$，$z\ne0$），则有：\n\n（1）$S_{\triangle OBC}:S_{\triangle OAC}:S_{\triangle OAB}:S_{\triangle ABC}=|x|:|y|:|z|:|x+y+z|$；\n\n（2）$\frac{S_{\triangle OBC}}{S_{\triangle ABC}}=\frac{|x|}{|x+y+z|}$，$\frac{S_{\triangle OAC}}{S_{\triangle ABC}}=\frac{|y|}{|x+y+z|}$，$\frac{S_{\triangle OAB}}{S_{\triangle ABC}}=\frac{|z|}{|x+y+z|}$。

**边界**：

推论中的向量式必须以 $O$ 为起点指向三角形顶点，且 $x$、$y$、$z$ 均不为 0；面积比取系数的绝对值（系数为负说明 $O$ 在三角形外）；比例与面积的对应顺序（$S_{\triangle OBC}$ 对应 $\overrightarrow{OA}$ 的系数）不能颠倒；用重心性质证明时辅助点 $B_1$、$C_1$ 在射线上，比例 $p$、$q$ 为正；当 $x+y+z=0$ 时 $S_{\triangle ABC}$ 的表达式失效；结论只是面积比，求具体面积还要用到 $S_{\triangle ABC}$ 的值。

**适用**：

已知 $x\overrightarrow{OA}+y\overrightarrow{OB}+z\overrightarrow{OC}=\vec{0}$（$x$、$y$、$z$ 均不为 0）求面积比；已知面积比求向量式中的系数。

### 材料 6：[ext-mat-91e0c2ace8-061b] 奔驰定理的系数只是比例关系

**摘要**：

由 $\alpha\overrightarrow{OA}+\beta\overrightarrow{OB}+\gamma\overrightarrow{OC}=\vec{0}$（$\alpha$、$\beta$、$\gamma>0$）不能得出 $S_{\triangle BOC}=\alpha$，只能得出 $S_{\triangle BOC}:S_{\triangle COA}:S_{\triangle AOB}=\alpha:\beta:\gamma$。

**正文**：

**典型错误**：有些同学可能会由 $\alpha\overrightarrow{OA}+\beta\overrightarrow{OB}+\gamma\overrightarrow{OC}=\vec{0}$（$\alpha$、$\beta$、$\gamma>0$）直接得 $S_{\triangle BOC}=\alpha$、$S_{\triangle COA}=\beta$、$S_{\triangle AOB}=\gamma$。\n\n**错因**：这里忽略了奔驰定理这个 $kS_{\triangle BOC}\overrightarrow{OA}+kS_{\triangle COA}\overrightarrow{OB}+kS_{\triangle AOB}\overrightarrow{OC}=\vec{0}$（$k\ne0$）的一般形式——系数只是与面积的比例关系挂钩，并不是对应系数相等。\n\n**正确结论**：为了避免错误，我们在使用奔驰定理的过程中，要记住 $S_{\triangle BOC}:S_{\triangle COA}:S_{\triangle AOB}=\alpha:\beta:\gamma$。若要求具体面积，还需结合面积和的关系 $S_{\triangle BOC}+S_{\triangle COA}+S_{\triangle AOB}=S_{\triangle ABC}$ 求出比例系数。

**边界**：

只有当向量式写成“系数恰为面积的 $k$ 倍”形式时系数才与面积成正比；由比例求具体面积必须再利用定量条件（已知 $S_{\triangle ABC}$ 或某个小三角形的面积）；向量式中的系数有公因数时可先约去再比较比例；点 $O$ 在三角形外部时系数会出现负值，此时面积仍取正值、比例取绝对值；使用前先确认向量式确实是“以 $O$ 为起点”的形式。

**适用**：

用奔驰定理求三角形面积，或由向量式 $x\overrightarrow{OA}+y\overrightarrow{OB}+z\overrightarrow{OC}=\vec{0}$ 求面积时，容易把系数直接当成面积。

### 材料 7：[ext-mat-2c4c1b3df0-005] 奔驰定理在内心与垂心问题中的应用

**摘要**：

内心的向量系数是对边边长、面积比等于边长比；垂心的系数是内角的正切。

**正文**：

## 内心\n内心是角平分线的交点，到三边的距离相等，因此三个小三角形的面积比等于对应边长之比，即 $S_{\triangle PBC}:S_{\triangle PCA}:S_{\triangle PAB}=a:b:c$（也等于 $\sin A:\sin B:\sin C$）。由奔驰定理可得内心的向量特征\n$a\overrightarrow{OA}+b\overrightarrow{OB}+c\overrightarrow{OC}=\vec 0$，\n其中 $a$、$b$、$c$ 分别是角 $A$、$B$、$C$ 的对边。遇角平分线或内切圆相关的问题，可优先利用这一向量式把边长与向量系数关联起来。\n\n## 垂心\n垂心是三边高的交点，其向量特征是邻边向量的数量积相等，即 $\overrightarrow{OA}\cdot\overrightarrow{OB}=\overrightarrow{OB}\cdot\overrightarrow{OC}=\overrightarrow{OC}\cdot\overrightarrow{OA}$。垂心对应的三个小三角形面积比为 $\tan A:\tan B:\tan C$，结合奔驰定理可写出垂心的另一种向量表示。

**边界**：

内心式的系数是边长而不是面积，使用时要把边长与正、余弦定理配合；垂心的面积比在钝角三角形中会出现负系数，需注意三角形形状对结论的影响；四心的向量式各有前提，不能互相套用。

**适用**：

内心与角平分线、内切圆相关的问题；垂心相关的向量等式与面积比。

### 材料 8：[ext-mat-2c4c1b3df0-003b] 奔驰定理与面积比的关系

**摘要**：

若向量系数和为向量零，则三个小三角形的面积比等于对应系数绝对值之比。

**正文**：

## 核心公式\n若点 $P$ 为 $\triangle ABC$ 内一点，满足 $\lambda_1\overrightarrow{PA}+\lambda_2\overrightarrow{PB}+\lambda_3\overrightarrow{PC}=\vec 0$（且三个系数都不为零），则\n$S_{\triangle PBC}:S_{\triangle PCA}:S_{\triangle PAB}=|\lambda_1|:|\lambda_2|:|\lambda_3|$，\n即三个小三角形的面积比等于对应顶点向量系数绝对值之比。\n\n## 应用要点\n1. 对号入座：$\lambda_1$ 对应的是不含点 $A$ 的那个小三角形的面积，写的时候不要错位。\n2. 若点 $P$ 在三角形外部，需结合向量系数的正负判断面积比的对应关系，系数符号反映了点与三角形的位置关系。\n3. 与整体的比例：$S_{\triangle PBC}$ 与 $S_{\triangle ABC}$ 的比为 $|\lambda_1|:(|\lambda_1|+|\lambda_2|+|\lambda_3|)$，另外两个同理。

**边界**：

系数为负时不能简单取绝对值后直接当面积比，要回到点与三角形的位置关系上判断；公式的前提是向量等式成立且系数与顶点一一对应；面积比的顺序必须与顶点的对应关系保持一致。

**适用**：

由向量等式直接写面积比；由面积比反求向量系数或判断点的位置。

### 材料 9：[ext-mat-e88490161b-002] 奔驰定理及其推论

**摘要**：

三角形内一点与三个小三角形面积的向量关系，以及面积比与系数比的对应。

**正文**：

**引理（奔驰定理）**：设 $O$ 是三角形 $ABC$ 内一点，三个小三角形 $BOC$、$AOC$、$AOB$ 的面积分别为 $S_A$、$S_B$、$S_C$，则 $S_A\vec{OA}+S_B\vec{OB}+S_C\vec{OC}=\vec 0$。因为三个向量前的系数恰是面积，图形结构与奔驰汽车的标志类似，故称奔驰定理。\n\n**证明思路**：延长 $AO$ 与 $BC$ 相交于点 $D$。利用同高的两个三角形面积之比等于底边之比，把 $S_B$ 与 $S_C$ 用 $BD$ 与 $DC$ 的比表示；再由向量共线关系，把 $\vec{OD}$ 用 $\vec{OB}$ 与 $\vec{OC}$ 线性表示，代回整理即可得到三个面积与三个向量的线性关系。\n\n**推论**：若 $O$ 是三角形 $ABC$ 内的一点，且 $x\vec{OA}+y\vec{OB}+z\vec{OC}=\vec 0$，则三个小三角形的面积之比为 $S_A:S_B:S_C=x:y:z$。\n\n**用途**：把“面积比”与“向量的线性表示中的系数比”互相翻译。已知系数比可以立即写出面积比并判断点的位置；已知点在特殊位置（重心、内心等）可以反推出系数比，用于解决填空与选择题。

**边界**：

定理中的点必须在三角形内部，若点在三角形外部或边上，系数的符号会出现负数，面积比也要按有向面积理解。系数与面积的对应顺序不能错：$\vec{OA}$ 对应的面积是与 $A$ 相对的三角形 $BOC$。用推论判断点的位置时，要先确认三个系数同号，才能断定点在三角形内部。

**适用**：

已知点在三角形内部并给出向量的线性关系时，判断面积比或点的位置。

### 材料 10：[ext-mat-fd4ef39358-003] 奔驰定理及其与三角形四心的联系

**摘要**：

三角形内一点与三个顶点构成的三个小三角形面积，与对应向量构成系数和为零的向量等式。

**正文**：

**奔驰定理的内容**：已知 $M$ 是 $△ABC$ 内一点，把 $△MBC$、$△MCA$、$△MAB$ 的面积分别记作 $S_A$、$S_B$、$S_C$，则成立向量等式 $S_A\overrightarrow{MA}+S_B\overrightarrow{MB}+S_C\overrightarrow{MC}=\vec{0}$。由于相应的几何图形与奔驰汽车的标志十分相似，这个结论常被称为奔驰定理。\n**定理的使用方向**：一是由向量关系反推三个小三角形的面积之比，二是由面积之比判断点 $M$ 的位置特征；当点 $M$ 恰好是三角形的某种心时，面积比会呈现特殊的比例。\n**与三角形四心的联系**：当 $M$ 为重心时，三个小三角形面积相等，即 $S_A：S_B：S_C=1：1：1$；当 $M$ 为内心时，三个小三角形的高相等，面积之比等于三边之比，即 $S_A：S_B：S_C=a：b：c$；当 $M$ 为外心时面积之比与对应角的正弦值有关；当 $M$ 为垂心时面积之比与对应角的正切值有关。因此可以用面积比的结构反过来识别四心。

**边界**：

奔驰定理要求点 $M$ 在三角形内部，点在三角形外部时结论形式会改变符号；面积比与向量系数是一一对应的，三个系数分别对应三个顶点，不能错位；由面积比判断四心时要先确认比例的具体形式。

**适用**：

已知三角形内一点满足的向量关系求面积比、判断该点是三角形的哪一种心的题目。

### 材料 11：[ext-mat-4b071c22c7-002] 奔驰定理及其与四心的关系

**摘要**：

三角形内一点与顶点连成的三个小三角形面积可作为系数，把三个顶点向量加权求和为零。

**正文**：

奔驰定理：O 是三角形 ABC 内一点，若三角形 OBC、OCA、OAB 的面积分别为 $S_A$、$S_B$、$S_C$，则 $S_A\vec{OA}＋S_B\vec{OB}＋S_C\vec{OC}＝0$。由于对应的图形与奔驰汽车的标志相似，所以称为奔驰定理。\n证明思路有两种：一是延长某条分角线与对边相交，把面积比转化为线段比再代入向量关系；二是延长 OA、OB、OC 得到三点，使 O 成为新三角形的重心，利用重心的向量关系推出结论。\n奔驰定理与三角形四心的关系：O 是重心时三个小三角形面积相等，三个向量系数相等；O 是内心时三个小三角形面积分别与对应的边长成正比，系数可用边长表示；O 是外心时面积与该角的正弦有关；O 是垂心时可由数量积关系验证。四心的向量特征式都可以由奔驰定理统一改写。

**边界**：

定理中的系数是三个小三角形的面积，顺序必须与各顶点一一对应，写反会得到错误的关系式；O 在三角形外时定理的形式需要重新推导，不能直接套用；面积比与向量系数成正比的前提是三个向量共起点。

**适用**：

由向量关系判断点与三角形的位置关系、统一处理四心问题。

### 材料 12：[ext-mat-2c4c1b3df0-001] 奔驰定理的内容与用法

**摘要**：

三角形内一点与三个顶点连成的三个小三角形面积与对应向量的线性组合为零向量。

**正文**：

## 定理内容\n已知 $P$ 为 $\triangle ABC$ 内一点，记 $\triangle PBC$、$\triangle PCA$、$\triangle PAB$ 的面积分别为 $S_A$、$S_B$、$S_C$，则\n$S_A\cdot\overrightarrow{PA}+S_B\cdot\overrightarrow{PB}+S_C\cdot\overrightarrow{PC}=\vec 0$，\n即三个顶点向量按三个小三角形的面积“分配”系数后，线性组合为零向量。\n\n## 名称的由来\n该结论对应的图形形似奔驰汽车的标志，因此称为奔驰定理。对推导三角形四心的向量结论有直接作用，是把面积关系与向量关系相互转化的桥梁。\n\n## 常见用法\n当题目给出形如 $\lambda_1\overrightarrow{PA}+\lambda_2\overrightarrow{PB}+\lambda_3\overrightarrow{PC}=\vec 0$ 的条件时，可直接读出三个小三角形的面积之比。

**边界**：

各系数的顺序必须与三个小三角形的面积一一对应（PA 的系数对应不含 A 点的那个三角形的面积），顺序写错会得到相反的结论；点 P 在三角形内部时三个面积都为正；点不在内部时不能直接套用。

**适用**：

已知三角形内一点满足的向量等式求面积比；由面积比反推点的位置或向量系数。

### 材料 13：[ext-mat-2c4c1b3df0-004] 奔驰定理在重心与外心问题中的应用

**摘要**：

重心的向量系数相等、三个小三角形面积相等；外心的面积比按二倍角的正弦分配。

**正文**：

## 重心\n在奔驰定理中取三个系数相等，即得重心的向量特征 $\overrightarrow{OA}+\overrightarrow{OB}+\overrightarrow{OC}=\vec 0$，对应奔驰定理中系数之比为 $1:1:1$。相应地重心把三角形分成面积相等的三个小三角形，即三个小三角形面积相等。遇三角形重心的向量问题，可直接利用系数相等的特征，快速转化为面积或向量运算。\n\n## 外心\n外心是三边垂直平分线的交点，到三个顶点的距离相等，即 $|\overrightarrow{OA}|=|\overrightarrow{OB}|=|\overrightarrow{OC}|$。外心对应的三个小三角形面积比为 $\sin2A:\sin2B:\sin2C$（$A$、$B$、$C$ 为三角形的内角），结合奔驰定理可写出外心对应的向量式，再利用模长相等的条件简化运算。

**边界**：

重心式的三个系数相等，内心式的三个系数是对边 a、b、c，两式形式相似但含义不同，不能混用；外心的面积比用二倍角的正弦表示，钝角三角形中会出现负值，要结合位置关系判断。

**适用**：

重心相关向量问题；外心与面积比、模长相等条件结合的问题。

### 材料 14：[ext-mat-f53b1f065b-020] 奔驰定理与三角形四心的向量式

**摘要**：

以三角形内一点与三个顶点为端点，按三个小三角形面积的比给出向量等式，并由此得到四心的向量表示。

**正文**：

奔驰定理：设 $P$ 是 $\triangle ABC$ 内一点，$\triangle PBC$、$\triangle PCA$、$\triangle PAB$ 的面积分别为 $S_A$、$S_B$、$S_C$，则 $S_A\overrightarrow{PA}+S_B\overrightarrow{PB}+S_C\overrightarrow{PC}=\mathbf{0}$。\n\n由此可得三角形四心的向量式：若 $P$ 为重心，则 $\overrightarrow{PA}+\overrightarrow{PB}+\overrightarrow{PC}=\mathbf{0}$；若 $P$ 为内心（三边长为 $a$、$b$、$c$），则 $a\overrightarrow{PA}+b\overrightarrow{PB}+c\overrightarrow{PC}=\mathbf{0}$；若 $P$ 为外心，则 $\sin 2A\cdot\overrightarrow{PA}+\sin 2B\cdot\overrightarrow{PB}+\sin 2C\cdot\overrightarrow{PC}=\mathbf{0}$；若 $P$ 为垂心，则 $\tan A\cdot\overrightarrow{PA}+\tan B\cdot\overrightarrow{PB}+\tan C\cdot\overrightarrow{PC}=\mathbf{0}$。\n\n判定时先回到四心的定义：重心是三条中线的交点，垂心是三边上高的交点，内心是三条内角平分线的交点（到三边距离相等），外心是三条边垂直平分线的交点（到三个顶点距离相等），再用对应的向量式作转化。

**边界**：

定理要求点在三角形内部，点在外面时小三角形的面积符号要另行讨论；四个向量式各自只对应一种心，系数中的 $a$、$b$、$c$ 表示角的对边，代错边会得到错误的点；垂心式中的正切只在锐角三角形内取正值。

**适用**：

涉及三角形内一点与三个小三角形面积关系的问题，以及用向量判断点是否为三角形的重心、内心、外心或垂心的题目。

