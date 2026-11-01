# E-08 KD-76 计时缝落码状态与校准出数条件核验

| 字段 | 值 |
|---|---|
| evidence-id | E-08 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39 |
| step | KD-76（T4 seam 落码状态核验 + T5 出数可行性核验） |
| gate-or-kd | KD-76（`docs/known-defects.md` open 维持） |
| verdict | **UNVERIFIED（出数未执行）**：T4 seam 已落码（主源+JVM 测试在树）；设备校准用例未落码（全仓 androidTest 0 引用）→ T5 出数（n≥10 冷启动轮）本会话不可执行 |
| executed-at | 2026-11-01T10:10–10:15+08:00（树状态核验，只读） |
| operator | 设备验证轨（批次 39 工作流子代理） |
| head | `a0641bb7392f380b5716afe95dd3c371dd0c7670` |
| sanitized | 是（纯只读核验，无设备数据） |

## 1. 核验命令与结果（本会话实跑）

```bash
grep -rn "connectStageTimestampsForTest\|ConnectStageTimestamps" \
  app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt
# → :97（Link KDoc）、:548/:1019（Link 成员与构造）、:1284-1288（访问器+回滚 KDoc）、:1397-1400（类）
#   ——T4 计时缝已落码（在飞 M，diff 特征化=6 打点行/5 语义写点 + 访问器 + 类，与 R2 §2 一致）

grep -rln "connectStageTimestampsForTest" app/src core/data/src
# → 仅两文件：引擎本体（main）+ app/src/test/.../ConnectStageTimestampsTest.kt（JVM 半边）
#   **app/data 两侧 androidTest 全部 0 引用** —— 设备校准用例未落码
```

## 2. 判定

- **T4（seam 落码）**：代码面已交付（本轨以 diff 特征化 + dex 无关性核实存在；其设备绿绑定前提=隔离套件复跑绿，当前三窗红未取得——见 E-06 §2 上报）。
- **T5（READY_TIMEOUT=10 s 校准出数 + 29.5 s 链分段实测）**：**UNVERIFIED**——判据（R2 §3.2/§4.2）要求"经 seam 的冷启动轮 n≥10"，其载体设备用例不在树上（grep 0 引用为证），本轨不代写落码批验收件；READY_TIMEOUT 维持 10 s 零改动，KD-76 维持 open。
- C1/C2/C4 条件矩阵与 29.5 s 链 S1/S2/S4 段同样未出数（同一前置缺口）。

## 3. 缺口归属

设备校准用例落码归 T4/T5 落码批（R2 §6 判定表"seam 用例 … T4 … 未落码未跑"现状未变）；落码后按"新增用例必须重跑"进本轨套件单。
