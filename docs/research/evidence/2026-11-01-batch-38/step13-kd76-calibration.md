# E-13 KD-76 校准测量（步骤四：判据未就绪，不执行——登记）

| 字段 | 值 |
|---|---|
| evidence-id | E-13 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤四·KD-76 校准测量（READY_TIMEOUT / 29.5 s 链）——"**若判据就绪**"条件判定 |
| gate-or-kd | KD-76（`docs/known-defects.md:2863`，open）；判据预研 `docs/research/2026-11-01-kd76-ready-timeout-calibration.md` |
| verdict | **UNVERIFIED（未执行）——判据未就绪，条件不成立** |
| executed-at | 判定于 2026-11-01T05:4x+08:00（只读核对，零设备写） |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5` |
| device-serial | emulator-5562（本步未使用） |
| exit-code | —（未执行设备命令） |
| raw-log | 无（本步零设备命令） |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（无输出即无敏感面） |

## 1. 判定依据（本会话实跑核对）

任务项条件"若判据就绪"不成立，三条独立证据：

1. **计时 seam 未落码**：判据预研 §2.1-b（推荐路径）的观测面 `connectStageTimestampsForTest()` 在引擎与测试两侧均 0 命中——`grep -rn "connectStageTimestampsForTest" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt app/src/androidTest/` → 引擎既有 seam 仅 `abandonForTest`（:1161）/`connectionCountForTest`（:1167）/`readyTimeoutOverrideForTest`（:1183）/`workerUidForTest`（:1205），无 ready 分段锚。没有它，ready 段（校准对象）无法与 attach 段分离（预研 §2.1 自认 E-04 §6-4 缺口），§2.3 判定规则（以 ready 段 max 为输入）不可执行。
2. **零代码近似被预研自判不足**：§2.1-(a) 明文"出数精度不足以支撑调常量裁定"——按预研口径执行它不构成"校准出数"。
3. **条件矩阵不完整**：C3（低 RAM AVD）不在本机设备清单（在线 4 台均常规档，E-00 §2）；C2 的 busy-loop k 值/loadavg 门可复现性为预研 U2 UNVERIFIED。且新增校准用例属"新增用例必须重跑"面——落 1–2 例进隔离套件（预研 §3.3）需要先落 seam（引擎文件改动 → 绑定设备套件全量复跑 + R8 keep 牵动，预研 §2.5），超出设备轨授权（本轨零产品码改动）。

## 2. 未执行项与影响

- `READY_TIMEOUT=10 s` 设备校准：**UNVERIFIED**（常量维持"待设备校准起点值"口径，`VisualJsClientEngine.kt:1251` KDoc 不变）。
- 29.5 s worst-case 链（口径 A 分段注入 / 口径 B 端到端）：**UNVERIFIED**。
- 间接证据（非校准，仅登记）：本批 26 窗 `warm-up failed`=0（E-12）——每个被 spawn 的 worker 都在 ready 门内就绪，与 E-04 口径（控制组 p50=686 ms）方向一致；但该口径是服务端合并观测，不构成 ready 段校准出数。

## 3. 处置

KD-76 维持 open（本步不关闭、不落 Progress 台账——台账归记录员/主会话）；校准判据的就绪前置（seam 落码 + 低 RAM 档位）移交实现批。本批设备轨对此登记为"条件不成立未执行"，不虚报出数。

## 4. 脱敏与去噪

无设备输出；无敏感面。

## 5. 判定

**UNVERIFIED（未执行，条件不成立）**：三条依据均为本会话实读/实跑（grep 0 命中、设备清单、预研原文）；未以间接证据冒充校准出数。
