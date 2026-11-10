# 批次51 G1 · 3D 改绑证据（shipped pack）

- 输入：`docs/research/evidence/2026-11-09-batch-50/d1/errors.csv`（audit `B50-D1-2026-11-08`，43 条）
- 处置：**39 条 judge-REBIND → 目标节点**；**1 条 NONE 经主会话裁决覆盖为 REBIND**
  （`ext-phy-2cea5a0fdb-045` → `平抛运动与斜面的结合分析`，判定前提“同科无节点”被
  boundary/alias 逐字证据否证）；**3 条 NONE → DROP_MATERIAL**（仅题干/章节标题罗列、无可归知识）。
- 落表：`dispositions.csv`（43 行，含 `judge_verdict` / `final_action` / `override_reason`）、
  `per-item-before-after.csv`（43 行 before→after）、`material_rebind_b51.csv`（40 行）。
- 工具：`tools/kb_build/rebind_materials.py` 的 `rebind()`（40 行，`rebound=40 skipped=0 bad_to=0`）、
  `tools/kb_build/drop_materials.py --write`（3 条命中）、`tools/kb_build/delete_points.py --write`
  （改绑后孤点 1 个：`一种药物中间体-h-的合成路线如下`，纯题干残渣、无材料）。

## 包变化（shipped == staging，逐字节）

| 量 | 改前 | 改后 |
|---|---|---|
| 主包 sha256 | `10ede1bf449de38f…` | `aaf81be884933b91…` |
| 节点 | 3,280 | 3,279（删 1 题干孤点） |
| 材料 / 绑定 | 64,853 / 64,853 | 64,850 / 64,850（撤 3 条，无悬空/无零绑定） |
| update-manifest version | 28 | 29 |
| contentVersion | `ce8edb8f89d91fe7` | `5f164dab812c5e03` |

`diff_pack_staging.py`：92 处 = 40 绑定 + 3 材料删除 + 1 知识点删除 + 48 节点别名；
主题树 0 增删/0 换序；无其它字段变化。

## 影响面闭合（43 条之外的必要动作）

- 别名闭合：改绑使旧节点别名失去“材料标题”依据（ghost_aliases 26 → 0），
  按 `rebuild_aliases` 同一判据只改受影响 **48 个节点**（+24 / -25 条），排除 6 个改前
  已存在的漂移节点（`alias-closure.json`）。
- 孤点：`ext-che-d3a055d224-007` 改绑后旧节点零材料（unbound_points=1），按
  `delete_points.py` 删除并写 DELETE 退役台账（`chapter_by_node` 1 行同步清除）。
- 台账同步：`material_rebind.csv +40`、`material_bindings.csv` 2 行改指（防 build.py 回改与 F9 欠账）、
  `point_delete.csv +1`、`alias_map.csv +24/-26`、`chapter_by_node.csv -1`；全部 LF。

## 身份/清单/完整性门（本会话实跑）

| 检查 | 命令 | 结果 |
|---|---|---|
| 三方主包哈希 | `python -I -X utf8 tools/ci/check_pack_sync.py` | **exit 0**（release == staging == dense 旁车） |
| dense 资产陈旧性 | `python -I -X utf8 tools/dense_build/check_asset.py` | **11/11 OK，exit 0** |
| KB 全量 CI | `python -I -X utf8 tools/ci/run_kb_checks.py` | **gates/consistency/roundtrip/manifest/dense/judgment_drift 全 OK，exit 0** |

dense 链由仓内工具重生成（未手写 hash）：
`export_bge_int8.py --skip-export` → 44,117 行（3,279 节点 + 40,838 别名），门全 true；
`pack_dense_asset.py` → `.vec` sha `ed866a6251371c3d…`（44,117 行），还原 cosine min 0.999747；
`gen_dense_device_fixture.py` → scan 3,279 节点行；`stage3_device_sim.py` → device-order fixture。
Kotlin 侧 `VECTOR_ASSET_SHA256`、布局/节点数 golden 均取自上述实测值。

## 测试

- `./gradlew.bat :core:data:compileDebugKotlin` → **EXIT=0**（BUILD SUCCESSFUL）。
- 18 个定向类（8 KB 装载 + 5 dense + 5 检索/词面）：
  `:core:data:testDebugUnitTest --rerun --tests …` → **77 tests，0 failures / 0 errors，EXIT=0**
  （逐类见 `test-results.json`）。

## 回归读数（交 D-3）

`stage3_device_sim.py`：端侧融合 **116/129**（批次 50 基线 117/129，**-1**）；离线参考
`refFusedD1` 118/129。丢失命中 2 条，其中 case#97 期望 `超重与失重的判断方法`
（词面 A 路 rank 2，融合 B 路 rank 0）。本阶段只记录，不以此改口径/阈值；D-3 需决定
别名/融合调参或如实验收。

## 未做/未验证

- 未跑设备/仪表化测试（本阶段任务书只要求 compile + KB 装载定向单测）。
- `material_rebind.csv` 全表 `--write` 被 10 条**改前已存在**的失效行挡住（如
  `bio-kb3-f5-s5-2-n11`、`ext-che-024618b63e-024` 等），故本次用同一 `rebind()` 函数
  以 40 行 scoped 输入应用；未修这 10 行（非本阶段范围）。
- `material_bindings.csv` 的 `bio-kb3-f5-s5-2-n11` 改前即不在包内（report-only，F9 三桶为 0）。
- 3D 新一轮盲判/复核未在本阶段执行（按计划另起 audit id）。
