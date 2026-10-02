# 绑定语义裁定 第二轮（D-1）· 切片内 101 条已裁定（2026-10-02）

> 依据：`docs/kb-outstanding-research-2026-10-02.md` 批次 2（D-1 语义裁定流水线）+
> 裁决 27 的 D-1。本轮对象 = `audit_content_bindings` 的新一轮候选（**496 条**，靶区节点 161），
> 先做**切片内 101 条**，切片外 395 条待下一批。

## 1. 结果（切片内 101 条）

| 裁定 | 条数 | 说明 |
|---|---|---|
| KEEP | **87** | 材料确实讲的是现绑节点那个知识点 |
| REBIND | **14** | 明显绑错，建议改绑到已存在的节点 |
| NONE | **0** | （原判 1 条，被独立复核推翻，见 §3） |

- 产物：`tools/kb_build/tables/content_audit_2026-10-02.csv`（八列，101 行；
  切片件在 `build/agent-batch2/slices/slice-*.verdicts.csv`，build/ 不纳管）。
- 管线自检：`merge_content_audit_verdicts --slice-dir build/agent-batch2/slices`
  → 「REBIND 引文对不上材料正文的行：0 / 14」。
- 上一轮的 409 行裁定表**未被触碰**（本轮写在新文件；两张表各自代表一轮）。

## 2. 14 条 REBIND 的去向

- 物理 2 条：`矢量图解法` / `正弦定理与余弦定理的应用` 上挂的"辅助圆法"材料 → `动态平衡问题`
  （**但见 §4 的节点级风险：目标族三节点重叠，改绑只是止血**）
- 物理 1 条：交变电流节点上的饱和汽压材料 → `饱和汽`（现绑完全无关，纯错绑）
- 化学 3 条：`铁三角` / `铁的化学性质` 上挂的铁盐、Fe²⁺/Fe³⁺ 材料 → `fe2-与fe3-的相互转化`、
  `铁盐和亚铁盐的性质和检验易错点`
- 生物 1 条：`激素` 上的血糖调节材料 → `胰岛素`
- 数学 7 条：`双曲线的渐近线`/`三角函数的图象与性质` 上的对勾函数材料 → `对勾函数`（3 条）；
  线面角/空间向量/数列节点上的平面几何材料 → `两条直线的位置关系`（2 条）、
  `圆锥曲线离心率的求法`（1 条）、`等比数列前项和的常用性质`（1 条）

## 3. 独立复核（换人、不看第一判的理由）

- 15 条重结论（14 REBIND + 1 NONE）逐条复判：**14 AGREE / 1 DISAGREE / 0 UNCLEAR**。
- REBIND 一面 **14/14 成立**；唯一失败是那条 NONE（建议解绑）——复核指出该材料
  （`chem-fx-you-yu-neng-gou-zai-zhong-ran-shao-f17x09`）正文"镁在 CO₂ 中燃烧"正合现绑
  `镁的化学性质` 的边界，**解绑会摘掉一条正绑材料**。已按复核证据订正为 KEEP（全批现为 87/14/0）。
- 口径声明：判定与复核都是**模型单一来源、无人类双复核**；批次 2 的 ≥0.95 语义精确率门
  只在"重结论"一面达成（14/14），不代表 87 条 KEEP 都被人眼验证过。

## 4. 本轮暴露的**节点级**问题（不是逐条改绑能解决的，转 ①-2 近重复节点）

1. **「共点力的动态平衡」vs「动态平衡问题」是同概念双节点**（27 / 22 条材料，边界同义），
   slice-02 里 32+ 条 foreign-title-match 行都属这一类；物理侧辅助圆法类材料已**散落 4 个节点**
   （矢量图解法 / 动态平衡问题 / 共点力的动态平衡 / 三力平衡的三角形法与拉密定理）。
   三个独立判定员都独立报了这条。→ 应先做节点合并（`point_merge.csv` 通道），再谈逐条改绑。
2. 若干"现绑明显无关"的案例（饱和汽压挂交变电流、对勾函数挂双曲线渐近线）根因是
   **别名污染**：别名表由绑定材料标题重建，一条错绑会把它的标题灌进节点别名，
   从而在下一次审计里"自证"成命中——本轮多处 evidence 已记录这一机制。

## 5. 复算与续跑

```bash
PYTHONPATH=tools python -m kb_build.audit_content_bindings --write --out build/agent-batch2/content-audit-round2-candidates.csv
PYTHONPATH=tools python -m kb_build.make_content_audit_kit --candidates build/agent-batch2/content-audit-round2-candidates.csv --out-dir build/agent-batch2/kit --per-file 25
# 判定件落 build/agent-batch2/verdicts/kit-NN.verdicts.csv（八列）→ 汇总：
PYTHONPATH=tools python -m kb_build.merge_content_audit_verdicts --slice-dir build/agent-batch2/slices
```

**下一步**：① 判切片外 395 条（kit-01…16 的 395 条，约 16 件）；② 节点合并（§4.1）；
③ 全部裁定后走 `apply_rebind_verdicts.py` → `rebind_materials` 落包 + 23 门复验（属批次 3）。
