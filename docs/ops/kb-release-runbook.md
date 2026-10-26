# KB 生产发布 Runbook（内容更新 / 检索臂）

| 项 | 值 |
| --- | --- |
| 版本 | v1（2026-10-26） |
| 适用 | `core/data/src/main/resources/knowledge/` 包家族 + `knowledge/dense/` 向量资产的生产发布：别名/材料内容更新、检索机制臂、采纳与回滚 |
| 权威口径 | `docs/research/2026-10-22-d3-round3-a1-prereg.md`（机制臂三规则）、`docs/research/2026-10-26-kb-v21-content-update-prereg.md`（内容更新非劣四判据）、`docs/research/2026-10-23-title-vector-arm-prereg.md`、`docs/research/2026-10-22-dense-union-prereg.md` |
| 实跑链 | 批次19（A1 采纳 v20 / 标题臂回滚）、批次22（v21 非劣采纳）——`docs/kb-batch19-2026-10-23.md`、`docs/kb-batch22-2026-10-26.md` |
| 编写时基线（本文件实读，2026-10-26） | 包 **v21**、contentVersion `9f9e99ceec47cf8c`、主包 `a719248448f314d1…`；dense `.vec` `79a22270b54180f6…`（44,043 行）；检索基线 **117/129 = 0.9069767441860465**（非劣，非增益） |

> **本 runbook 的证据规则**：每条命令都来自本仓库已落盘的脚本、源码或批次实跑记录，逐条注明出处；编写时实读的数字在 §5 单列。**没有实跑过的门不得写成“已通过”**。批次22 的文书轮本身**未重跑** Gradle/设备/JVM（`docs/kb-batch22-2026-10-26.md:336`），本文件不继承任何“已跑”假设。
>
> **路径约定**：命令均在仓库根 `D:/smart mistake book` 下执行（Git Bash）。`build/` 被 `.gitignore:15` 忽略，`*.log` 被 `.gitignore:31` 忽略，`*.apk` 被 `.gitignore:23` 忽略：`build/` 下任何产物都**不是**可提交证据（详见 §3.6）。

---

## 0. 执行前必读：五条不可违反的纪律

1. **装置门未绿，不写任何包**：`:core:data:compileDebugUnitTestKotlin` 必须 **exit=0**；红时停在别名写入之前，不写 staging、不跑 dense、不拿旧账本判读（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:87-91`，`docs/research/2026-10-22-d3-round3-a1-prereg.md:23-25`）。
2. **判官、金标零改**：设备判官、JVM 判官、零漂移镜像、`golden_queries_v2.json` 全程只读；跑前跑后各记 sha（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:120-131,168-171`）。
3. **零漂移非 0 = 装置缺陷，该臂不判读**：不是“FAIL”，也不是采纳/回滚结论；先修装置再重出数（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:62,68`）。
4. **没有预注册不发布**：预注册 §1–§6 出数前冻结、只填 §7；预期数字只能写“算术预期”，不得事后改口径（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:1-3,44`）。
5. **不把“定向跑”写成“全量绿”**：`--tests` 定向、`--rerun` 复用、`up-to-date` 跳过、`scoped build` 都必须在报告里点名（`docs/kb-batch22-2026-10-26.md:195,336`；`build/agent-batch22/dense.log:247-249`）。

---

## 1. 内容更新发布链

总览（每一步的失败动作都是“停”，不得跳步）：

| # | 阶段 | 命令入口 | 关键产物 | 门 / 阈值 | 停的条件 |
| --- | --- | --- | --- | --- | --- |
| 0 | 冻结与前置 | `git rev-parse` / `sha256sum` / `compileDebugUnitTestKotlin` | `pre-sha.txt`、装置门日志 | 门 exit=0；工作树写面 CLEAN | 门红、写面脏、无预注册 |
| 1 | 别名写入 | `rebuild_aliases`（干跑→`--write`） | staging 包、`alias_map.csv`、`alias_dropped.csv` | 净增=预注册算术值、冲突 0、拒绝 0、变更节点集合精确 | 净增不符 / 别的节点被改 |
| 1b | 材料轨 | `rebind_materials`（报告模式） | 报告 stdout | 待执行改绑 0、悬空 0、材料总数不变 | 需 `--write` 才能收敛 → 另立预注册 |
| 2 | promote | `promote --dry-run` → `promote` | 成品包 28 文件、manifest、contentVersion | 23 道内容门 + 一致性 + round-trip + 台账全绿；压平 0 | 任一节红 → 不写盘 |
| 3 | dense 8 步 | 见 §1.3 | `.vec` + 旁车 + 5 类夹具 + 期望值 | 模型对拍 ≥0.999、资产还原 ≥0.999、对齐 ≥0.9999、`check_asset` 10/10 | 任一步非 0 |
| 4 | 重钉 | 编辑常量/测试锚 | `DenseRecallAssembly.kt` 常量、锚测试 | 常量==旁车==实文件三方一致；断言/容差不放宽 | 需放宽容差才能绿 |
| 5 | 单测 | `:core:data:testDebugUnitTest` | XML 结果 | failures=0 errors=0 skipped=0（全量）；定向类全绿 | 任一红（先分清登记红） |
| 6 | 双路 | 设备判官 + JVM 判官 | 4 份 MISS 账本、2 份 metrics | 设备 `OK (1 test)`+同代 MATCH；JVM exit=0 | 同代 MISMATCH / 设备非 OK |
| 7 | 零漂移 | `zero_drift.py` | `zero-drift.json` | 同路 lexical/fused symdiff 均 0、exit=0 | 非 0 → 不判读 |
| 8 | 非劣 / 三规则 | `retrieval_significance.py` + 判据表 | significance JSON | 见 §1.8 | 任一判据不成立 → 回滚 |
| 9 | 采纳 / 回滚 | 见 §1.9 / §2 | stateAfter 登记 | 回滚后全 sha 回基线 | — |

### 1.0 阶段 0：冻结与前置

```bash
cd "D:/smart mistake book"
D=build/kb-release-$(date +%Y%m%d)   # 本批目录（跨天回滚时改回写前那一批的目录，见 §2.2）

# 1) 当前提交与工作树（只作现场上下文，工作树通常含他线改动）
git rev-parse HEAD
git status --porcelain | wc -l

# 2) 写面必须 CLEAN（把 §2.1 的路径清单放进来；批次22 写前实测为空）
git status --porcelain -- \
  tools/kb_build/tables/alias_exceptions.csv \
  tools/kb_build/tables/alias_map.csv \
  tools/kb_build/tables/alias_dropped.csv \
  core/data/src/main/resources/knowledge \
  core/data/src/main/resources/knowledge/dense \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallAssembly.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge \
  core/data/src/test/resources/dense \
  core/data/src/androidTest/assets/dense

# 3) 写前 pre-sha：执行 §2.1 的生成块（它已重定向到本批 pre-sha.txt）
mkdir -p build/kb-release-$(date +%Y%m%d)
#    ……把 §2.1 的整段 { sha256sum ... } > $D/pre-sha.txt 贴到这里执行
wc -l build/kb-release-$(date +%Y%m%d)/pre-sha.txt

# 4) 装置门（写死）
./gradlew.bat :core:data:compileDebugUnitTestKotlin --console=plain
echo "gate_exit=$?"
```

- **产物**：`pre-sha.txt`（逐文件全值）、门日志。
- **门**：门 **exit=0**；写面 porcelain 为空。批次22 写前实测为空（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:177-181`）。
- **失败动作**：门红 → 泊车（§4.1）；写面非空 → 先登记脏态、单独裁定，**不得覆盖他线改动**（同上 `:180`）。

### 1.1 阶段 1：别名写入（`rebuild_aliases`）

输入是人工定稿层 `tools/kb_build/tables/alias_exceptions.csv`，表头 `subject,slug,alias,reason`（`tools/kb_build/rebuild_aliases.py:30-36`）。**先追加行，再干跑，再写**：

```bash
# 1) 追加预注册冻结的行（示例：批次22 的 2 行；reason 保留候选表原文）
#    只改这一个表，不在本步手改 alias_map.csv
#    行尾：MATH,等和线,<alias>,<reason>

# 2) 干跑（不写盘），核对与预注册 §1 的算术预期逐项相符
PYTHONPATH=tools python -m kb_build.rebuild_aliases

# 3) 写表 + 回写 staging 包
PYTHONPATH=tools python -m kb_build.rebuild_aliases --write
```

- **产物**：`build/kb-staging/` 下 staging 包；`tools/kb_build/tables/alias_map.csv`、`alias_dropped.csv` 重建。
- **门（全部满足才进 promote）**：
  - 例外应用数 = 既有 + 本批新增；**冲突改判 0、拒绝 0**；
  - **别名总数净增 = 预注册算术值**（批次19：40,765→40,796；批次22：40,796→40,798）；
  - `alias_map.csv` 数据行净增 = 新增条数，且新增行**全部**属于预注册点（批次22：37,551→37,553，两行均 `MATH,等和线`）；
  - **变更节点集合恰好等于预注册点集**（批次19：21 个节点；批次22：1 个节点）。出现其他节点变化 = 缺陷，停止并回滚（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:173`）。
- **失败动作**：净增与预期不符（例如标题派生面同时漂移）→ **按实测登记并停止判读**，先查原因（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:44`）。
- **复核命令**（只读）：

```bash
python - <<'PY'
import csv
for p in ['tools/kb_build/tables/alias_exceptions.csv','tools/kb_build/tables/alias_map.csv','tools/kb_build/tables/alias_dropped.csv']:
    rows=list(csv.reader(open(p,encoding='utf-8')))
    print(p, 'data', len(rows)-1)
PY
git diff --stat -- tools/kb_build/tables/alias_{exceptions,map,dropped}.csv
git diff -- tools/kb_build/tables/alias_exceptions.csv tools/kb_build/tables/alias_map.csv | grep '^+MATH,等和线'
```

### 1.2 阶段 1b：材料轨（默认零写）

材料/25 sidecar 正文**不属于**别名发布链。可执行的只有报告模式：

```bash
PYTHONPATH=tools python -m kb_build.rebind_materials     # 报告；不要加 --write

# 内容侧相邻只读门（课标覆盖台账完整性；不属于 §1.9 发布判据，结果登记）
PYTHONPATH=tools python -m kb_coverage.alignment --check
```

- **产物**：stdout 统计（改绑数、跳过数、材料总数、悬空绑定、可写回）；`alignment --check` 打印各科候选/已判定数与“台账完整/不完整”。
- **门**：`改绑 0；跳过（幂等/错行）；材料总数不变；悬空绑定 0`（批次22 实测 `0 / 9575 / 64,973 / 0`，`docs/research/2026-10-26-kb-v21-content-update-prereg.md:22-34`）。`alignment --check` 期望 rc=0 且“台账完整：全部候选恰有一行判定，target 均在包中”；它不是发布判据，红了只登记、不据此改包。
- **边界**：任何材料/sidecar **正文写入都会使已测 117/129 基线失效**，必须走“自产料池门 + 晋升”并另立预注册；不得在同一批里拿“补料”解释分数变化（`docs/kb-batch22-2026-10-26.md:188`）。
- **失败动作**：出现待执行改绑 → **停**，不得用 `--write` 顺手收敛；另立预注册。

### 1.3 阶段 2：promote（staging → 成品，唯一写入口）

```bash
# 1) 只跑门，不写盘
PYTHONPATH=tools python -m kb_build.promote --dry-run

# 2) 正式晋升（全绿才逐文件落盘；先写临时文件再原子替换，台账最后写）
PYTHONPATH=tools python -m kb_build.promote
echo "promote_exit=$?"
```

- **产物**：`core/data/src/main/resources/knowledge/` 下 **28 个文件**（主包、manifest、25 sidecar、index）；manifest 的 `version` 与 `contentVersion` 只在落盘这一刻刷新（`tools/kb_build/promote.py:1-24`）。
- **门（写死在 `promote.py`）**：
  1. **23 道内容门**（`gate.evaluate()`，`tools/kb_build/promote.py:54-58`）；
  2. 契约镜像 + 五张权威表↔包一致性（`check_pack_contract.evaluate`）；
  3. round-trip：包家族全部文件可规范重放；
  4. 台账 schema 2 压平 + 一跳到底 + **version 单调**（`max(staging, 成品)+1`）。
  任一环不绿 → **不写盘、exit 1**；不得产生半包/半升级（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:40,210`）。
- **落盘后立即核验**：

```bash
python -c "import json;d=json.load(open('core/data/src/main/resources/knowledge/moe-2025-update-manifest.json',encoding='utf-8'));print(d['packId'],d['version'],d['contentVersion'])"
sha256sum core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json
```

  - 期望形态（批次22 实测）：`moe-2025-four-subjects-v1 21 9f9e99ceec47cf8c`；主包 `a7192484…`（`docs/kb-batch22-2026-10-26.md:206-208`）。
  - 25 sidecar 聚合 sha 按“按 v2-01…v2-25 顺序以 `\n` 连接 `<basename> <sha256>`、末尾无换行”复算（构造见预注册 §5.2 `:181`；本文件 §5 已复算为 `ed1c6d4f…`）。
- **失败动作**：`promote` 红 → 保持上一版，不跑 dense、不跑设备、不判读。

### 1.4 阶段 3：dense 8 步链

**八步的权威定义**（`docs/kb-batch10-2026-10-13.md:114-119`、`docs/kb-batch11-2026-10-14.md:104-107`、`docs/kb-batch5-2026-10-04.md:136`）：

| 步 | 脚本 | 作用 | 产物 |
| --- | --- | --- | --- |
| ① | `tools/dense_build/export_bge_int8.py` | 模型导出 / 对拍 | int8 ONNX、`int8-*.npy`、`fp32-*.npy`、`model-manifest.json` 本档条目 |
| ② | `tools/dense_build/pack_dense_asset.py` | 打包向量资产 | `.vec` + `.vec.json` 旁车 |
| ③ | `tools/dense_build/stage3_expectation.py` | 离线期望值（D1 形态） | `build/stage3-device-expectation.json` |
| ④ | `tools/dense_build/gen_tokenizer_fixture.py` | tokenizer 夹具 | `tokenizer-parity-{cases,stages,summary}` |
| ⑤ | `tools/dense_build/gen_dense_device_fixture.py` | dense 夹具 | `dense-{scan,fusion}-reference.txt` |
| ⑥ | `tools/dense_build/gen_device_parity_fixture.py` | 设备对拍夹具 | `encoder-parity-cases.tsv` / `encoder-parity-vectors.f32` / `encoder-parity.json` |
| ⑦ | `tools/dense_build/stage3_device_sim.py` | 端侧算法模拟 | `dense-device-order-reference.txt` |
| ⑧ | `tools/dense_build/check_asset.py` | 资产陈旧性门 | 10 项 OK/FAIL |

批量执行（批次22 实际顺序；`build/agent-batch22/dense.log:207-246`）：

```bash
# ⓪ 词面腿导出（必须在 ① 之前：旁车要记录 leg 行数与词面哈希）
./gradlew.bat :core:data:testDebugUnitTest --tests "*ProductionLexicalLegExportTest*" --rerun --console=plain

# ① 模型导出/对拍（复用已有 ONNX，不重转模型）
python tools/dense_build/export_bge_int8.py --skip-export

# ② 打包（向量 = 全部 canonical + alias）
python tools/dense_build/pack_dense_asset.py

# ③ 离线期望
python tools/dense_build/stage3_expectation.py

# ④ tokenizer 夹具
python tools/dense_build/gen_tokenizer_fixture.py

# ⑤ dense 夹具
python tools/dense_build/gen_dense_device_fixture.py

# ⑤附 金标查询向量（本批若动别名/向量面则重出；批次22 实际执行）
python tools/dense_build/export_golden_query_vectors.py

# ⑥ 设备对拍夹具
python tools/dense_build/gen_device_parity_fixture.py

# ⑦ 端侧模拟
python tools/dense_build/stage3_device_sim.py

# ⑧ 资产陈旧性门
python tools/dense_build/check_asset.py

# ⑧附 陈旧性门的两条 CI 同款
python tools/ci/run_kb_checks.py
python -m unittest discover -s tools/tests -t tools -p "test_dense_asset_gate.py"
```

每步的门与阈值（写死；出处为 `tools/dense_build/README.md`、`tools/dense_build/pack_dense_asset.py:26-33,189-201`、`tools/dense_build/gen_device_parity_fixture.py:67`、`build/agent-batch22/dense.log:213-246`）：

| 步 | 门 / 阈值 | 批次22 实测（仅作形态参照） |
| --- | --- | --- |
| ⓪ | 测试 1/1 通过；钉值未漂则**零改** | 主集 102/129、MRR `…232`、MISS 27，钉值未漂 |
| ① | **int8 vs torch fp32 逐行 cosine 最小 ≥0.999**（docs 与 queries 两条） | docs 0.999009609 / queries 0.999194920；fp32 0.999999881 |
| ② | 向量数 = canonical + alias；**资产还原（vs int8 模型输出）逐行 cosine 最小 ≥0.999**；旁车 `packSha256` == 当前主包 sha | 44,043 行 = 3,245 + 40,798；还原 0.999746978；`packSha256=a7192484…` |
| ② 附 | 端到端（资产 vs fp32）**不作硬门**，但必须量出来并落盘/上报 | 0.998889863（两层 int8 叠加） |
| ③ | 只产出参照数，不单独判分；`selfCheck.stage2Reproduction` 语料口径已变时为 **N/A，不算通过** | refFused 117/129、refDense 104/129、词面 102/129；自证 N/A |
| ④ | 372 条（edge 18 / query 129 / stage 25 / surface 200）；批式自证 True | 同左 |
| ⑤ | scan 4 查询 / 3,245 节点行；fusion 10 用例；资产 sha == `.vec` | 同左 |
| ⑤附 | 129 题 / 393 行；量化还原 min ≥0.9995 | min 0.999829 |
| ⑥ | **行对齐自检 min ≥0.9999**（低于即 `SystemExit`，fixture 不产出） | min 0.999999881 |
| ⑦ | **命中丢失必须 0**；成员差/名次差逐批登记 | 成员差 1/129、名次差 0/129、丢失 0 |
| ⑧ | `check_asset` **10/10 OK**；`run_kb_checks` exit 0；`test_dense_asset_gate` 6 tests OK | 10/10 OK；exit 0；6 OK |

**失败动作**：任一步非 0 → **不重钉、不出数**；不得用旧 `.vec`/旧夹具继续下游。

### 1.5 阶段 4：重钉（只按实测，不放宽）

变更后必须重钉的钉值（按实际改动逐项判断，不要机械全改）：

| 钉值 | 位置 | 何时改 | 门 |
| --- | --- | --- | --- |
| `VECTOR_ASSET_SHA256` | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallAssembly.kt:108` | `.vec` sha 变 | **常量 == `.vec` 实文件 sha == 旁车 `vectorSha256`** 三方一致 |
| `.vec.json` `vectorCount` / `packSha256` | 旁车 | 向量数或包变 | 与头/当前包一致 |
| `DenseRecallRerankerTest` 候选映射/总数 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallRerankerTest.kt` | 候选映射变 | 量源 = 本轮 `dense-device-order-reference.txt` 逐 `[candidate]` 清点 |
| `DenseVectorAssetTest` 节点数 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseVectorAssetTest.kt` | 节点数变 | 与 scan 行合计一致 |
| `ProductionLexicalLegExportTest` 主集/MRR/hits | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/ProductionLexicalLegExportTest.kt` | 词面计数变 | 断言不放宽；ULP 两源按既有口径 |
| `Stage1LexicalLabTest` / `Stage2LexicalScoresExportTest` 锚 | 同名测试 | 词面计数变 | 按实测重钉，纯数值更新 |
| `model-manifest.json` | `tools/dense_build/model-manifest.json` | 链驱动更新 | 三方一致（vocab/onnx/bytes） |

- **产物**：重钉后的源文件/测试文件 + 每条钉值的新旧值与量源注记（日志路径/提交号）。
- **硬规则**：**容差、断言、required 集合一律不放宽**；重钉是“把期望值改成实测值”，不是“把门改松”（`docs/kb-batch19-2026-10-23.md:62`；`docs/kb-batch22-2026-10-26.md:98`）。
- **量源必须留痕**：每条重钉在注释里写新旧值与量源（日志路径/提交号），保留 §2.1 的 pre-sha 表作审计链（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:195-197`）。
- **零改项复核**：判官两件、零漂移镜像、金标、25 sidecar 正文、模型资产（ONNX 权重/词表）——跑前跑后 sha 必须相同（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:168-175`）。

### 1.6 阶段 5：单测

```bash
# 1) 定向：重钉涉及的全部类（批次22 实际只跑 dense 4 类；锚测试按本批重钉面加入）
./gradlew.bat :core:data:testDebugUnitTest \
  --tests "*ProductionLexicalLegExportTest*" \
  --tests "*Stage1LexicalLabTest*" \
  --tests "*Stage2LexicalScoresExportTest*" \
  --tests "*DenseRecallRerankerTest*" \
  --tests "*DenseVectorAssetTest*" \
  --tests "*DenseAssetProvenanceTest*" \
  --rerun --console=plain
echo "targeted_exit=$?"

# 2) 全量（采纳前必须有一次真实全量；定向不能替代）
./gradlew.bat :core:data:testDebugUnitTest --rerun --console=plain
echo "full_exit=$?"
```

- **产物**：`core/data/build/test-results/testDebugUnitTest/TEST-*.xml`。
- **门**：全量 **failures=0 / errors=0 / skipped=0**；定向类全绿。批次19 的全量读数为 **1033/1033**（`docs/kb-batch19-2026-10-23.md:24`）。
- **XML 计数复核**（不要只看 `BUILD SUCCESSFUL`）：

```bash
python - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=e=s=0
for p in glob.glob('core/data/build/test-results/testDebugUnitTest/TEST-*.xml'):
    r=ET.parse(p).getroot()
    t+=int(r.get('tests',0)); f+=int(r.get('failures',0))
    e+=int(r.get('errors',0)); s+=int(r.get('skipped',0))
print('tests=%d failures=%d errors=%d skipped=%d files=%d' % (t,f,e,s,len(glob.glob('core/data/build/test-results/testDebugUnitTest/TEST-*.xml'))))
PY
```

- **失败动作**：任一类红 → 先分清“登记红（已知缺陷/他线遗留）”与“本批引入红”。登记红必须有具名条目才能继续，且报告中不得写成“全绿”（批次19 的 `ProductionLexicalLegExportTest` ULP 登记即此类，`docs/kb-batch19-2026-10-23.md:65,133`）。
- **口径警告**：批次22 **未跑** `:core:data:testDebugUnitTest` 全量，只跑定向 4 类 13 tests（`docs/kb-batch22-2026-10-26.md:42,195`）。发布时若同样只跑定向，报告只能写“定向绿”，不能写“全套绿”。

### 1.7 阶段 6：双路（设备判官 + JVM 镜像判官）

“双路”= **设备轨**与 **JVM 镜像轨**两套判官跑同一份冻结金标；每路内部再分 lexical / fused 两条账本。主线判读只看**设备融合臂**，JVM 只作零漂移与装置保真，**不单独判分**（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:48-55`）。

> 批次22 把设备步骤写成 `build/agent-batch22/v21/step{0,1,3,4}_*.sh`；这些脚本在 `build/` 下、**不入仓**。下面把等价命令内联，便于在没有上批 `build/` 副本时照做。

#### 1.7.1 设备轨（唯一主判读）

```bash
ADB=/c/Android/Sdk/platform-tools/adb.exe
EMU=/c/Android/Sdk/emulator/emulator.exe
DEVICE=emulator-5584
APK="core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk"
PKG=com.tingyun.smartmistakebook.core.data.test
RUNNER=androidx.test.runner.AndroidJUnitRunner
CLS=com.tingyun.smartmistakebook.core.data.knowledge.GoldenRetrievalInstrumentedTest
D=build/kb-release-$(date +%Y%m%d)

# (1) 只动本线 AVD；先核对 AVD 名，非 kb_test_device 直接中止
$ADB -s $DEVICE emu avd name            # 必须输出 kb_test_device
$ADB -s $DEVICE emu kill

# (2) 冷启 + 清数据（wipe）
mkdir -p $D
nohup $EMU -avd kb_test_device -port 5584 -wipe-data -no-snapshot -no-boot-anim -no-audio -no-window -gpu swiftshader_indirect > $D/emulator-coldstart.log 2>&1 &
# 轮询到 1
$ADB -s $DEVICE shell getprop sys.boot_completed

# (3) 先跑门，再删旧 APK 重新 assemble（防 up-to-date 伪影，见 §4.4）
./gradlew.bat :core:data:compileDebugUnitTestKotlin --console=plain
rm -f "$APK"
./gradlew.bat :core:data:assembleDebugAndroidTest --console=plain
sha256sum "$APK"

# (4) 安装，然后做 post-install 同代核验（解包 APK 内嵌 .vec 的 sha；见 §3.2）
$ADB -s $DEVICE install -r -t "$APK"
INST_PATH=$($ADB -s $DEVICE shell pm path $PKG | tr -d '\r' | sed 's/^package://' | head -n1)
$ADB -s $DEVICE pull "$INST_PATH" $D/installed-after-install.apk
#    再按 §3.2 比 worktree / local APK / installed APK 三方 .vec sha，必须 MATCH

# (5) 清 logcat 后跑判官
$ADB -s $DEVICE logcat -c
$ADB -s $DEVICE shell am instrument -w -e class "$CLS" "$PKG/$RUNNER"
echo "instrument_exit=$?"
```

- **产物**：`OK (1 test)` 摘要、`/sdcard/Download/golden-{fused,lexical}-misses-<stamp>.txt` 两份账本、logcat。
- **门（全部满足才算设备轨有效）**：
  - `OK (1 test)` 且 `am instrument exit=0`（`build/agent-batch22/v21/step3_install_run_device.sh:83-88`）；
  - **同代 MATCH**：工作树 `.vec` sha == 本地 APK 内嵌 `.vec` sha == 已装 APK 内嵌 `.vec` sha；否则脚本 **exit 3，拒绝 instrument**（同文件 `:54-60`）；
  - 账本时间戳 ≥ 本轮起始（取回脚本会拒绝早于本轮起始的账本）（`build/agent-batch22/v21/step4_pull_ledgers.sh:60-70`）。
- **失败动作**：MISMATCH → 重 assemble/重装后重核，**不得在 MISMATCH 下跑判官**；设备非 OK → 先看 logcat（`FATAL EXCEPTION`/`shortMsg=Process crashed.` 等），不判读分数。

#### 1.7.2 JVM 镜像轨（只作保真）

```bash
sha256sum \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalJvmTest.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RetrievalBenchmark.kt
git status --porcelain -- \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalJvmTest.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RetrievalBenchmark.kt

./gradlew.bat :core:data:testDebugUnitTest --tests "*GoldenRetrievalJvmTest*" --rerun --console=plain
echo "jvm_exit=$?"
```

- **产物**：`build/golden-jvm-metrics.txt`、`build/agent-batch14/d3/jvm-fused-metrics.txt`（批次22 的快照路径，`build/agent-batch22/v21/step5_run_jvm.sh:23-29`）。
- **门**：exit=0；JVM 融合/词面读数与设备逐位一致（保真）；判官与镜像 sha 跑前跑后不变。
- **失败动作**：判官文件被改 → 整轮不可比，停止；JVM 红 → 修装置，不得用设备单边数据替代零漂移。

### 1.8 阶段 7：零漂移

```bash
D=build/kb-release-$(date +%Y%m%d)
python build/agent-batch19/a1/zero_drift.py \
  --device-lexical $D/device-lexical-misses.txt \
  --device-fused   $D/device-fused-misses.txt \
  --jvm-lexical    $D/jvm-lexical-misses.txt \
  --jvm-fused      $D/jvm-fused-misses.txt \
  --json-out       $D/zero-drift.json
echo "zero_drift_exit=$?"
```

- **产物**：`zero-drift.json`（`--json-out`）+ 终端 `verdict: lexical=… fused=…` 行。
- **判据**：同路 **device↔JVM** 的 MISS 题面集合 symmetric difference：lexical **必须 0**、fused **必须 0**；名次不进判据，只报情报（`build/agent-batch19/a1/zero_drift.py:1-27`）。
- **阈值**：两条同路边都为 0 ⇒ exit 0；否则 exit 2。
- **脚本位置警告**：`zero_drift.py` 位于 `build/agent-batch19/a1/`，而 `build/` 被 `.gitignore:15` 忽略——它**不在版本库内**。执行前先确认存在（`test -f build/agent-batch19/a1/zero_drift.py`）；不存在时从上一批 `build/` 复制，或按其 docstring 口径重写（题面为键、认不出的行报错、重复题面报错）。**不得把“脚本缺失”记成通过。**
- **失败动作**：非 0 = **装置缺陷**，本臂**不判读**；先修装置（同代/重装）再重出数。不得写成“FAIL 后回滚”，也不得用代理账本冒充同路门（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:62,68`）。

### 1.9 阶段 8：非劣（内容更新）/ 三规则（机制臂）

先判定本批属于哪一类，**不得中途换口径**：

| 批型 | 判据 | 依据 |
| --- | --- | --- |
| **内容更新**（别名/材料补齐，不主张新机制） | **非劣四判据** | `docs/research/2026-10-26-kb-v21-content-update-prereg.md:56-69` |
| **检索机制臂**（别名臂/向量臂/并集臂，主张机制增益） | **三采纳规则 + 零漂移一票否决** | `docs/research/2026-10-22-d3-round3-a1-prereg.md:14-21` |

#### 1.9.1 内容更新：非劣四判据

| # | 判据 | 阈值 | 求值来源 |
| --- | --- | --- | --- |
| 1 | 主集非劣 | 设备融合 Recall@5 **≥ 上一基线**（v21 对 A1：≥117/129）；低于即不采纳 | 设备 `device-metrics.txt` 主判读 |
| 2 | 24 章逐章不劣 | 任一章**严格更低**即不采纳；equal/improved 通过 | 逐章行 `diff` + significance JSON `per_chapter` |
| 3 | 零漂移 | 同路 fused 与 lexical 的 symdiff **均为 0** | `zero-drift.json` |
| 4 | 负向守卫 | 按章 cluster 配对 bootstrap `--reps 20000 --seed 0`，A=本批、B=基线；95% CI **上界 ≥ 0** | significance JSON |

补充：预算 `p50Budget=150ms` / `p95Budget=250ms`，`overBudgetSamples>0` 只作装置健康观测，**不单独作为采纳条件**（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:67`）。

```bash
# BASE = 上一已采纳基线的账本目录（**每次采纳后更新**）
#   当前基线 v21：BASE=build/agent-batch22/v21
#   批次22 当时的上一基线是 A1：BASE=build/agent-batch19/a1
BASE=build/agent-batch22/v21
D=build/kb-release-$(date +%Y%m%d)
CUR=$D

# 融合
python tools/kb_coverage/retrieval_significance.py \
  --golden tools/kb_coverage/tables/golden_queries_v2.json \
  --misses "$CUR/device-fused-misses.txt" \
  --misses-b "$BASE/device-fused-misses.txt" \
  --reps 20000 --seed 0 --json

# 词面（同式，换 lexical 两份账本）
python tools/kb_coverage/retrieval_significance.py \
  --golden tools/kb_coverage/tables/golden_queries_v2.json \
  --misses "$CUR/device-lexical-misses.txt" \
  --misses-b "$BASE/device-lexical-misses.txt" \
  --reps 20000 --seed 0 --json
```

- **不要写死 A1 路径**：A1 只是 v21 的上一基线；新基线采纳后，下一次的 `BASE` 必须换成新基线的账本目录（如 v21 → `build/agent-batch22/v21`）。

- **产物**：`significance-{fused,lexical}-vs-<baseline>.json` + 逐条判据求值（写进预注册 §7）。
- 方向说明：落盘字段是 `B−A`；**传参顺序决定符号**，报告里必须写清 A/B 各是谁（批次22 的 A=v21、B=A1 写法见 `docs/kb-batch22-2026-10-26.md:141-143`）。
- 未跑出 significance 产物 → 判据 4 记 **NOT_RUN**，不得写“无显著差异”（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:69`）。

#### 1.9.2 机制臂：三采纳规则

1. **主集严格更高**（不是“不低”）；
2. **24 章逐章不劣**（任一章严格更低即不采纳）；
3. **负向守卫 CI 上界 ≥ 0**；
4. **零漂移一票否决**：同路 symdiff 非 0 → 该臂不判读。

A1 实例（v20）：117/129 > 100/129；0 章更低；CI [0.0769, 0.1926] 上界 ≥0；零漂移 PASS/PASS → adopted（`docs/kb-batch19-2026-10-23.md:53-58`）。

- **禁止**：内容更新套用“严格更高”（会被单题翻转支配，等价于面向结果调参）；机制臂套用“非劣”（会放过机制无效）。两者转换必须另立预注册（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:71-76`，`docs/kb-batch22-2026-10-26.md:145-150`）。

### 1.10 阶段 9：采纳 / 回滚

#### 采纳

1. 保留新包、新 `.vec`、新别名账本、dense 夹具与重钉锚；
2. 按实测重钉（§1.5），容差/断言不放宽；
3. 在预注册 §7 登记 **stateAfter**（版本 + contentVersion + 主包 sha + `.vec` sha + 向量数）；
4. 把新基线写进三处：预注册 §7、验收台账、现行数字台账（见 §2.3）；
5. 报告里写清“本批不承诺任何分数”（内容更新）或“增益读数与判据”（机制臂）。

#### 回滚（逐字，不做“部分保留”）

按 §2.2 执行；**整批回滚**，不得“只留一条别名”（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:203`）。

---

## 2. 版本与回滚

### 2.1 pre-sha 清单（写前必存，全值 64 hex）

**固定路径集**（每次发布都取这一组；批次22 的 A 表 26 行为同一口径，见 `docs/research/2026-10-26-kb-v21-content-update-prereg.md:137-166`）：

```bash
# 生成（写前；输出到本批 build 目录）
D=build/kb-release-$(date +%Y%m%d)
mkdir -p "$D"
{
  sha256sum tools/kb_build/tables/alias_exceptions.csv \
            tools/kb_build/tables/alias_map.csv \
            tools/kb_build/tables/alias_dropped.csv
  sha256sum core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json \
            core/data/src/main/resources/knowledge/moe-2025-update-manifest.json \
            core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-index.json
  sha256sum core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-*.json
  sha256sum core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec \
            core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json \
            core/data/src/main/assets/dense/bge-small-zh-v1.5-int8.tflite
  sha256sum tools/dense_build/model-manifest.json \
            core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallAssembly.kt
  sha256sum core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/ProductionLexicalLegExportTest.kt \
            core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/Stage1LexicalLabTest.kt \
            core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/Stage2LexicalScoresExportTest.kt \
            core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallRerankerTest.kt \
            core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseVectorAssetTest.kt \
            core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseAssetProvenanceTest.kt
  sha256sum core/data/src/test/resources/dense/dense-{scan,fusion,device-order}-reference.txt \
            core/data/src/test/resources/dense/golden-query-vectors.i8 \
            core/data/src/test/resources/dense/tokenizer-parity-{cases.txt,stages.txt,summary.json}
  sha256sum core/data/src/androidTest/assets/dense/encoder-parity-cases.tsv \
            core/data/src/androidTest/assets/dense/encoder-parity-vectors.f32 \
            core/data/src/androidTest/assets/dense/encoder-parity.json
  sha256sum build/production-lexical-leg.tsv build/stage3-device-expectation.json
  # 零改项（判官/金标/镜像）必须存，回滚后要逐位比对
  sha256sum core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalInstrumentedTest.kt \
            core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalJvmTest.kt \
            core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RetrievalBenchmark.kt \
            tools/kb_coverage/tables/golden_queries_v2.json
} > $D/pre-sha.txt
```

- **前置**：`build/production-lexical-leg.tsv` 与 `build/stage3-device-expectation.json` 若在基线态不存在，先按 ⓪/③ 重建再取 pre-sha；否则 `sha256sum -c` 会在回滚时因缺文件失败。
- **必须检查 stderr 与行数**：`sha256sum` 遇到不存在的文件只在 stderr 报错，**整段 brace group 的最终 rc 仍可能是最后一条命令的 0**。生成后要确认 `stderr` 为空、`wc -l` 等于预期（本表为 **59** 行；`moe-2025-teaching-support-v2-*.json` 与显式 index 行有 1 行重复，属正常）。**不得只看 rc。**
- **校验命令可用性**：本文件编写轮实测 `sha256sum f.txt > list.txt && sha256sum -c list.txt` → `f.txt: OK`、exit 0（Windows 输出 `hash *file` 的格式可被 `-c` 正确解析）。

**当前基线（本文件编写时实读，2026-10-26；可作对照，不得当作下一次的 pre 值）**：

| 文件 | sha256（全值） |
| --- | --- |
| `moe-2025-four-subjects-v1.json` | `a719248448f314d1a6e6bfc74e3a101f07960d08d6985ade3d1c97f3efe6da36` |
| `moe-2025-update-manifest.json` | `c4d3875049dab731bae7dfdeb960a3b35473cf706e48d71a1c8b7ff8deced3e0`（version=21） |
| `dense/bge-small-zh-int8.vec` | `79a22270b54180f6ba5346a4f56d87eb16e5f335107ac9877cbb142fd4515446`（44,043 行 / 26,041,347 B） |
| `dense/bge-small-zh-int8.vec.json` | `68ed524adbf0c45339c06e98ef95b159651dfd62e1d3ce16f502a023a1c448ce` |
| `alias_exceptions.csv` | `1cba77feb7d61389e61674e6ec238ee2c7019c8fe53f051a8b14fe589410db59`（35 数据行） |
| `alias_map.csv` | `3b47ab2da0517a0ff9955ad2536d0d4cf46275bf88d5a750301dbfe350d02d8e`（37,553 数据行） |
| `alias_dropped.csv` | `84040e23256bfae0e31531d4e8f04cb3d022b31654bf6bedae0cce2b297eafdf`（22,559 数据行） |
| 25 sidecar 聚合 | `ed1c6d4f6d9d331a0982408c2f60a660a8490cbad5c2a74d438604d12057355e`（本文件按 §1.3 构造复算） |
| `DenseRecallAssembly.kt` | `a32fa8e7b702ec52cdb8e5e37dea5b257698a3534bb27c6f65141b6b976f4ba2`（`:108` = `79a22270…`） |
| `DenseRecallRerankerTest.kt` | `807d3a506c2fb3c77a8b49860c3a85e0e74c600d925af042c4b68f73de0f5b5e` |
| `model-manifest.json` | `8b3a7b2415275c82d2b0f92afe5561cf24acdaf7b9eba8536bac31cc3efea1c5` |
| 设备判官 | `c2280255a9b0db2bc77ac1213e977be755a431efe80cfdf5b3c0c6e7bb7a4786` |
| JVM 判官 | `62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95` |
| 零漂移镜像 | `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` |
| 金标 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |

### 2.2 逐字回滚命令

**原则**：CLEAN 文件用 git 恢复；脏文件/未跟踪文件用字节备份；`build/` 产物用备份覆盖；恢复来源逐字节对拍 `pre-sha.txt`。批次19 标题臂的 21 文件回滚是这套程序的实际样本（`build/agent-batch19/title-final.md:17-56`）。

```bash
# 回滚必须指向**写前那一批**的目录；跨天时手动改成原批次目录，不要用当天日期
D=build/kb-release-<批次日期>
# (1) tracked 且写前 CLEAN 的文件：按路径逐个 checkout（不要 git checkout . / git restore .）
git checkout -- \
  tools/kb_build/tables/alias_exceptions.csv \
  tools/kb_build/tables/alias_map.csv \
  tools/kb_build/tables/alias_dropped.csv \
  core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json \
  core/data/src/main/resources/knowledge/moe-2025-update-manifest.json \
  core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-index.json \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallAssembly.kt
# ……以及 pre-sha.txt 中其余 tracked 路径

# (2) 写前即为 M 态或未跟踪的文件：用字节备份恢复（不要假设 git 里有正确内容）
cp $D/backup/<file>.pre <原路径>

# (3) build/ 产物（gitignored）：用备份覆盖
cp $D/backup/stage3-device-expectation.json.pre build/stage3-device-expectation.json
cp $D/backup/production-lexical-leg.tsv.pre build/production-lexical-leg.tsv

# (4) 全段终扫：逐行比对 pre-sha（任何一行不中即未回滚完成）
sha256sum -c $D/pre-sha.txt
# (5) 归一化差异必须被证明，不能假设：CRLF/LF 的 EOL-only 差异用 git 对拍
git ls-files --eol -- <差异文件>

# (6) 恢复态资产门
python tools/dense_build/check_asset.py        # 期望 10/10 OK

# (7) 恢复态定向单测（至少覆盖被改过的类）
./gradlew.bat :core:data:testDebugUnitTest \
  --tests "*Stage1LexicalLabTest*" --tests "*Stage2LexicalScoresExportTest*" \
  --tests "*DenseRecallRerankerTest*" --tests "*DenseAssetProvenanceTest*" \
  --rerun --console=plain
```

**回滚后的核验清单**（逐条写进报告，不得只写“已回滚”）：

| 项 | 期望 |
| --- | --- |
| manifest | `version` = 上一版；`contentVersion` = 上一版戳 |
| 主包 | sha == pre 值 |
| 25 sidecar 聚合 | == pre 聚合值 |
| 别名三表 | 行数与 sha 均 == pre 值 |
| `.vec` / `.vec.json` | sha、向量数 == pre 值 |
| `VECTOR_ASSET_SHA256` | == 恢复后的 `.vec` sha |
| 判官/金标/镜像 | sha 全程不变 |
| `check_asset.py` | 10/10 OK |
| 材料报告 | `改绑 0；悬空 0`；材料总数不变 |
| 装置门 | `compileDebugUnitTestKotlin` exit=0；仍红则登记为他线/基线问题，不归因本批 |

- **不得**把“已回滚”写成“未采纳但保留改动”；**不得**在回滚轮顺手改判官或放宽断言（批次19 的 ULP 红就按“保留 A1 锚、不代改”处理，`build/agent-batch19/title-final.md:64-77`）。
- **他线保护**：回滚前先 `git status --porcelain -- <写面路径>`，确认命中的都是本批写入；若某路径出现非本批的改动，**停**并单独裁定，不得用 `git checkout` 覆盖他线内容（同预注册 §5.2 的脏态纪律，`docs/research/2026-10-26-kb-v21-content-update-prereg.md:180`）。

### 2.3 采纳后基线登记（三处，缺一不可）

1. **预注册 §7**：stateAfter（版本、contentVersion、主包 sha、`.vec` sha、向量数）、逐条判据求值、回滚/采纳结论。样例：`docs/research/2026-10-26-kb-v21-content-update-prereg.md:371-375`。
2. **验收台账**：追加批次补记，只追加不改旧读数。样例：`docs/kb-acceptance-2026-10-17.md:559-607`（批次19）、§11（v21）。
3. **现行数字台账**：文件头的“现行数字”标签与版本标签。样例：`docs/kb-outstanding-work-2026-09-20.md:3-5`（v20→v21 更新行）。

登记格式（可直接抄）：

```text
stateAfter：<包 packId> v<NN>（contentVersion <16 hex>，主包 sha <64 hex>）
            + dense <64 hex>（<N> 行；旁车 <64 hex>）
检索基线：融合 <hits>/129 = <float>、MRR <float>；词面 <hits>/129
裁定：adopted=<true|false>；回滚：<无 | N/N 文件 sha 核验>
```

> 新版号由 `promote` 的单调规则决定（`max(staging, 成品)+1`），**写前不可知**；不得手工预填（`tools/kb_build/promote.py:1-24`，`docs/research/2026-10-26-kb-v21-content-update-prereg.md:112-113`）。

---

## 3. 装置纪律

### 3.1 专用 AVD：`kb_test_device`

- 本线专用 AVD = **`kb_test_device`**，端口 **5584**（批次22 的 `emulator-5584`）；批次19 用的是 `emulator-5554`——**端口不是固定契约，AVD 名才是**。
- 冷启前先核对 AVD 名；不是 `kb_test_device` 就 **ABORT**，不碰其他线的 5554/5562（`build/agent-batch22/v21/step1_coldstart_emulator.sh:59-68`）。

```bash
$ADB -s emulator-5584 emu avd name     # 必须 kb_test_device
```

### 3.2 同代核验：解包 APK 内嵌 `.vec` 的 sha

**三方一致才算同代**：工作树 `.vec` == 本地构建 APK 内嵌 `.vec` == 设备已装 APK 内嵌 `.vec`（`build/agent-batch22/v21/step0_samegen_check.sh:1-8,95-107`；`build/agent-batch22/v21/step3_install_run_device.sh:39-64`）。

```bash
ADB=/c/Android/Sdk/platform-tools/adb.exe
D=build/kb-release-$(date +%Y%m%d)
vec_sha_of_apk() {
python - "$1" <<'PY'
import sys, zipfile, hashlib
with zipfile.ZipFile(sys.argv[1]) as z:
    print(hashlib.sha256(z.read("knowledge/dense/bge-small-zh-int8.vec")).hexdigest())
PY
}

WT=$(sha256sum core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec | awk '{print $1}')
APK=core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
LOCAL=$(vec_sha_of_apk "$APK")
INST_PATH=$($ADB -s emulator-5584 shell pm path com.tingyun.smartmistakebook.core.data.test | tr -d '\r' | sed 's/^package://' | head -n1)
$ADB -s emulator-5584 pull "$INST_PATH" $D/installed-after-install.apk
INST=$(vec_sha_of_apk $D/installed-after-install.apk)
echo "worktree=$WT local=$LOCAL installed=$INST"
[ "$WT" = "$LOCAL" ] && [ "$WT" = "$INST" ] && echo POST_INSTALL_SAMEGEN=MATCH || echo POST_INSTALL_SAMEGEN=MISMATCH
```

- **阈值**：三者逐字节相同 = MATCH。MISMATCH 时**拒绝 instrument**（批次22 脚本 exit 3）。
- **注意**：同代核验是“资产代际”，不是 APK 整包 sha；整包 sha 会因构建时间变化，不能拿它当同代判据。
- **首次装前的 MISMATCH 是预期前态**：批次22 的 `apk-samegen-check.log` 在重装前显示 `MISMATCH_LOCAL+MISMATCH_INSTALLED`，最终以 **post-install** 结论为准（`docs/research/2026-10-26-kb-v21-content-update-prereg.md:295`）。

### 3.3 冷启 wipe

```bash
ADB=/c/Android/Sdk/platform-tools/adb.exe
D=build/kb-release-$(date +%Y%m%d)
$ADB -s emulator-5584 emu avd name      # 核对
$ADB -s emulator-5584 emu kill
nohup /c/Android/Sdk/emulator/emulator.exe \
  -avd kb_test_device -port 5584 -wipe-data -no-snapshot -no-boot-anim -no-audio -no-window \
  -gpu swiftshader_indirect > $D/emulator-coldstart.log 2>&1 &
# 轮询 boot_completed=1；再验证 /sdcard/Download 可写（账本落点）
$ADB -s emulator-5584 shell getprop sys.boot_completed
```

- **为什么必须 wipe**：设备上残留的旧账本/旧安装会冒充本轮结果；账本取回脚本还会检查时间戳（§1.7.1）。
- **只对专用测试设备 wipe**；共享设备先按 `docs/testing/device-state-safety.md:7-10` 备份。

### 3.4 判官不改

| 文件 | 角色 | 当前 sha（编写时实读） |
| --- | --- | --- |
| `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalInstrumentedTest.kt` | 设备判官 | `c2280255…` |
| `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalJvmTest.kt` | JVM 判官 | `62c232fc…` |
| `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RetrievalBenchmark.kt` | 零漂移镜像（非判官） | `8c2a2ef0…` |
| `tools/kb_coverage/tables/golden_queries_v2.json` | 冻结金标（129/24） | `eabef102…` |

- 跑前跑后各记一次；`git status --porcelain -- <这四个文件>` 必须与跑前相同。
- 判官被动过 → 两轮不可比，零漂移门失去意义，**停止判读**。
- 只需重钉“测试锚点”（数值断言）时，改的是**锚测试**，不是判官；且只按实测改数值、不放宽容差（§1.5）。

### 3.5 设备证据落盘

设备结论必须落到 **仓内 `.md`**（`docs/research/evidence/<YYYY-MM-DD>-batch-<NN>/`），`build/` 只放原始全量；`.log` 被 `.gitignore:31` 忽略，放进 `docs/` 也入不了库（`docs/research/2026-10-26-device-evidence-convention.md:16-23,120-130`）。

最小记录字段：`am instrument` summary + `INSTRUMENTATION_CODE`、设备 serial/AVD/API/ABI/页大小、APK sha256/字节数/versionName、logcat 签名 grep（含 0 命中）、退出码（`docs/research/2026-10-26-device-evidence-convention.md:47-56,611-621`）。

### 3.6 `build/` 边界（发布相关）

- `build/` 下的一切（本批日志、`zero_drift.py`、`pre-sha.txt`、`stage3-device-expectation.json`）**都不入仓、可被 `clean` 删除**。
- 因此：**pre-sha 清单与回滚备份不能只存在 `build/`**——至少把关键 sha 与回滚结论写进批次记录/预注册 §7；脚本缺失时按 §1.8 重建，不得当作已执行。
- APK 本体不能入库，只记 sha256/大小/versionName（`.gitignore:23`）。

---

## 4. 常见故障与处置

### 4.1 门红 → 泊车（park）

**症状**：`:core:data:compileDebugUnitTestKotlin` exit≠0；错误落在**他线在飞文件**。

**处置**：
1. 记录失败文件与行号（批次19：`core/domain/.../PaperAnswerKeyChain.kt:139` 块注释未闭合）；
2. **不写包、不跑 dense、不跑设备、不判读**；
3. 不修改/不回滚他线文件；轮询待命（批次19 经 7 轮、01:01:38 转绿）；
4. 门绿后再从阶段 0 重新开始，不用旧账本。

依据：`docs/kb-batch19-2026-10-23.md:31`；`docs/research/2026-10-22-d3-round3-a1-prereg.md:23-25`。

### 4.2 他线在飞件把 `androidTest` 编译打红

**症状**：`compileDebugUnitTestKotlin` 绿，但 `:core:data:assembleDebugAndroidTest` 在 `compileDebugAndroidTestKotlin` 红，错误全来自未跟踪文件（批次22：`QuickJsVisualExecutionEngineInstrumentedTest.kt`）。

**处置**：
1. 用错误路径确认归属（`git status --porcelain -- <path>`，`??` = 未跟踪）；
2. **等待他线自愈**；批次22 在 05:20 的首个有效探测已绿，全程**未启用** source 排除预案（`exclude_quickjs_test.init.gradle` 未使用）；
3. 若确需排除，必须主会话授权、只排除单个测试类、并在报告登记“scoped build”；**默认禁止**用排除把红改绿。

依据：`build/agent-batch22/v21/deviation.md:9-32`；`docs/kb-batch22-2026-10-26.md:193`。

**附加陷阱：伪探测**。批次22 的 05:05/05:10/05:15 三轮“探测”其实没执行 Gradle——`'D:\smart' 不是内部或外部命令`（cmd.exe 引号缺陷），这三轮**不计为编译红**（`build/agent-batch22/v21/deviation.md:13`）。判定“跑过”的最低证据：日志里有 Gradle 的 `BUILD SUCCESSFUL/FAILED` 与 exit code，而不是只有包装器的一行。

### 4.3 `--tests` 通配改写导致的假绿

**症状**：全量/宽通配红，改成更窄的 `--tests`（或改一次通配字面量）后 `BUILD SUCCESSFUL`，于是被写成“通过”。

> 口径说明：本仓库的批次记录里**没有登记过**“改写通配换绿”的具体事故；本条是防御性纪律，依据是批次22 已把“定向/全量”明确分开（只跑定向 4 类、全量未跑，`docs/kb-batch22-2026-10-26.md:195`）以及批次10/11 的重钉轮次口径。不要把它写成“曾经发生过”。

**处置**：
1. **禁止在红之后收窄通配**来换绿。收窄只能用于定位，不能作为验收证据；
2. 每次定向跑必须记录实际选中的类与用例数，以 XML 为准：

```bash
python - <<'PY'
import glob, xml.etree.ElementTree as ET
for p in sorted(glob.glob('core/data/build/test-results/testDebugUnitTest/TEST-*.xml')):
    r=ET.parse(p).getroot()
    print(p.split('TEST-')[-1][:-4], 'tests', r.get('tests'), 'failures', r.get('failures'), 'errors', r.get('errors'), 'skipped', r.get('skipped'))
PY
```

3. 报告里区分三种口径：**全量**（`:core:data:testDebugUnitTest --rerun`）、**定向**（`--tests` 若干类）、**补充验证**（非门任务）。批次22 的 dense 4 类明确标为“补充验证，非 ask 指名门”（`build/agent-batch22/dense.log:247-249`），且全量未跑（`docs/kb-batch22-2026-10-26.md:195`）——这是正确写法，不是缺陷。
4. 若必须收窄才能跑（例如他线红），在报告写“scoped build + 原因 + 未覆盖类”，不得写“全套通过”。

### 4.4 `up-to-date` 伪影（旧 APK 没重打包）

**症状**：工作树 `.vec` 已换代，但 Gradle 判定 up-to-date，APK 仍内嵌上一代资产；设备跑的是**旧资产**，读数却看起来正常。

**实例**：批次19 标题臂——设备 APK 内嵌 `0536d0d1…`（23:16 构建）vs 工作树 `9d9bf3f5…`（02:46 重导），同路融合零漂移 symdiff=3，按预注册判“装置缺陷、不判读”（`docs/kb-batch19-2026-10-23.md:76`）。

**处置（顺序固定）**：
1. 跑同代核验（§3.2），不要先看分数；
2. MISMATCH → **删除旧 APK 再重 assemble**（批次22 的 step2 就是 `del`/`rm` 旧 APK 后 `assembleDebugAndroidTest`，`build/agent-batch22/v21/step2_gate_and_build.cmd:6-10`）；
3. 重装 → **post-install** 再核验一次，直到 MATCH；
4. 未 MATCH 不得 instrument。

### 4.5 零漂移非 0（装置缺陷）

**症状**：同路 lexical 或 fused symdiff ≠ 0，脚本 exit=2。

**处置**：
1. 先查**代际**（§4.4），再查账本时间戳（是否取到上一轮账本），最后查判官/镜像 sha；
2. 修复后**重出数**；不得写“FAIL 所以回滚”，也不得用 JVM 单边数据顶替；
3. 若预注册写“非 0 = 不判读”，就按不判读登记，三规则记“未求值”（批次19 标题臂的写法：`docs/kb-batch19-2026-10-23.md:88`）。

### 4.6 其它已见故障

| 症状 | 根因 | 处置 |
| --- | --- | --- |
| `promote` 后 `promote_dry` 显示 `next=<NN+1>` | 它是落盘后的闸门快照（单调规则 dry-run），**不是**本批又写了一次 | 以 `promote-run.log` 的实际写入为准（`docs/kb-batch22-2026-10-26.md:84,198`） |
| 离线 `stage3-device-expectation.json` 的 lexical MRR 与设备钉值差 ~3e-16 | ULP 两源（`…229` Python vs `…232` Kotlin/设备），既有口径 | 不合并、不改判据；报告写明两源（`docs/kb-batch22-2026-10-26.md:191`） |
| `stage3` 自证 N/A | 语料口径已变（离线臂 28,931 vs 当前布局 44,043+） | 记 N/A，**不算通过**（`docs/kb-batch22-2026-10-26.md:51`） |
| 端到端 cosine 0.99889 < 0.999 | 两层 int8 叠加，设计上不作硬门 | 只记录不判门；硬门是模型对拍与资产还原（`tools/dense_build/pack_dense_asset.py:189-201`） |
| 八门全绿但单测红 | 八门是 Python 门，不含 Gradle | 不得由“八门全绿”推出“D1/D3 全库无红”（`docs/kb-batch22-2026-10-26.md:197`） |
| `:app:connectedLocalFirstDebugAndroidTest` 卸载删数据 | AGP 行为 | 统一 `assembleDebugAndroidTest` + `adb install -r -t` + `am instrument`（`.zcode/plans/plan-2026-10-20-batch-15-prd-a-b-backup-wg05.md:199`） |
| `connectedDebugAndroidTest --tests` 被拒 | 当前 Gradle/AGP 组合不支持该选项 | 用 `am instrument -e class` 或 `-Pandroid.testInstrumentationRunnerArguments.class=`（`docs/kb-dense-rebuild-2026-10-01.md:207-208`） |
| 直接调 `gradlew.bat` 不返回 | **在 `.cmd` 批处理里**必须用 `call gradlew.bat`（批次19/20 教训；Git Bash 里直接 `./gradlew.bat` 正常） | `.cmd` 内写成 `call "D:\smart mistake book\gradlew.bat" …`（`build/agent-batch22/v21/step2_gate_and_build.cmd:10-14`） |

---

## 5. 编写时实读记录（2026-10-26，只读）

以下命令由本文件编写轮在**当前工作树**实跑，用于确认基线口径；它们**不是**发布门，也不改变任何已落盘文件。

| 检查 | 命令（摘要） | 结果 |
| --- | --- | --- |
| 包身份 | `python -c "json.load(...update-manifest.json); print(packId, version, contentVersion)"` | `moe-2025-four-subjects-v1 21 9f9e99ceec47cf8c` |
| 关键 sha | `sha256sum` 主包 / manifest / `.vec` / 旁车 / 别名三表 / 判官 / 镜像 / 金标 / `DenseRecallAssembly.kt` / `DenseRecallRerankerTest.kt` / `model-manifest.json` | 与 §2.1 表逐项一致 |
| 别名行数 | Python `csv.reader` 计数（总行−表头） | `35 / 37553 / 22559` |
| 钉值 | `grep -n VECTOR_ASSET_SHA256 DenseRecallAssembly.kt` | `:108` = `79a22270b54180f6ba5346a4f56d87eb16e5f335107ac9877cbb142fd4515446` |
| 25 sidecar 聚合 | 按 §1.3 构造 Python 复算 | `ed1c6d4f6d9d331a0982408c2f60a660a8490cbad5c2a74d438604d12057355e` |
| 资产陈旧性门 | `python tools/dense_build/check_asset.py` | **10/10 OK**（packSha256 `a7192484…`、`.vec` `79a22270…`、count=44,043、layout 44,043 逐条一致） |
| 零漂移脚本存在性 | `test -f build/agent-batch19/a1/zero_drift.py`；`git check-ignore -v …` | 存在；命中 `.gitignore:15`（**未入仓**，见 §1.8） |
| 回滚校验命令 | `sha256sum f.txt > list.txt && sha256sum -c list.txt`（临时目录） | `f.txt: OK`、exit 0（`*` 二进制标记格式可被 `-c` 解析） |

**未在编写轮运行**：Gradle 任何任务、设备 `am instrument`、JVM 判官、dense 链脚本、`promote`、零漂移与显著性脚本。因此本文件**不声称**任何门在编写时通过；上表只证明当前基线的身份与资产门。

> **后续校验轮补充（2026-10-26，见文末《校验记录》）**：校验轮实跑了 `promote --dry-run`、`rebuild_aliases` 干跑、`rebind_materials` 报告、`alignment --check`、`run_kb_checks`、`check_asset`、`test_dense_asset_gate`、`zero_drift`（既有账本）、`retrieval_significance`（既有账本）与设备只读查询；**仍未跑** Gradle 真执行、设备 `am instrument`/install/wipe、JVM 判官与 dense 写链。两节的“未跑”范围以各自正文为准。

---

## 附录 A：一页版执行顺序（复制到工位）

```bash
# 0) 冻结（pre-sha 用 §2.1 的整段生成块）
./gradlew.bat :core:data:compileDebugUnitTestKotlin --console=plain || exit 1
D=build/kb-release-$(date +%Y%m%d); BASE=build/agent-batch22/v21
mkdir -p "$D"
#    在 §2.1 执行 { sha256sum ... } > "$D/pre-sha.txt"

# 1) 别名
PYTHONPATH=tools python -m kb_build.rebuild_aliases          # 干跑对预期
PYTHONPATH=tools python -m kb_build.rebuild_aliases --write  # 净增/冲突/拒绝核对

# 2) promote
PYTHONPATH=tools python -m kb_build.promote --dry-run
PYTHONPATH=tools python -m kb_build.promote                  # 23 门 + 一致性 + roundtrip + 台账

# 3) dense 8 步
./gradlew.bat :core:data:testDebugUnitTest --tests "*ProductionLexicalLegExportTest*" --rerun
python tools/dense_build/export_bge_int8.py --skip-export
python tools/dense_build/pack_dense_asset.py
python tools/dense_build/stage3_expectation.py
python tools/dense_build/gen_tokenizer_fixture.py
python tools/dense_build/gen_dense_device_fixture.py
python tools/dense_build/export_golden_query_vectors.py
python tools/dense_build/gen_device_parity_fixture.py
python tools/dense_build/stage3_device_sim.py
python tools/dense_build/check_asset.py
python tools/ci/run_kb_checks.py
python -m unittest discover -s tools/tests -t tools -p "test_dense_asset_gate.py"

# 4) 重钉 + 单测
#    DenseRecallAssembly.VECTOR_ASSET_SHA256 / DenseRecallRerankerTest / 其余锚
./gradlew.bat :core:data:testDebugUnitTest --rerun --console=plain

# 5) 双路（设备 + JVM）
#    AVD kb_test_device → kill → -wipe-data 冷启 → 删旧 APK → assemble → 装 → 同代 MATCH → am instrument
./gradlew.bat :core:data:testDebugUnitTest --tests "*GoldenRetrievalJvmTest*" --rerun

# 6) 零漂移（D/BASE 见第 0 步；账本先按 §1.7.1 取回并命名）
python build/agent-batch19/a1/zero_drift.py \
  --device-lexical "$D/device-lexical-misses.txt" --device-fused "$D/device-fused-misses.txt" \
  --jvm-lexical    "$D/jvm-lexical-misses.txt"    --jvm-fused    "$D/jvm-fused-misses.txt" \
  --json-out       "$D/zero-drift.json"

# 7) 非劣 / 三规则
python tools/kb_coverage/retrieval_significance.py --golden tools/kb_coverage/tables/golden_queries_v2.json \
  --misses "$D/device-fused-misses.txt" --misses-b "$BASE/device-fused-misses.txt" \
  --reps 20000 --seed 0 --json
# 8) 采纳 → 重钉 + stateAfter 登记；回滚 → §2.2 逐字恢复 + 全段终扫
```

## 附录 B：阈值速查

| 门 | 阈值 | 出处 |
| --- | --- | --- |
| 装置门 | `compileDebugUnitTestKotlin` exit=0 | `docs/research/2026-10-26-kb-v21-content-update-prereg.md:87` |
| promote | 23 门全绿 + 一致性 + roundtrip + 台账；压平 0 | `tools/kb_build/promote.py:8,54-98` |
| 模型对拍 | int8 vs fp32 逐行 cosine min **≥0.999** | `tools/dense_build/pack_dense_asset.py:26-33` |
| 资产还原 | 逐行 cosine min **≥0.999** | `tools/dense_build/pack_dense_asset.py:26-33,201` |
| 端到端 | 只记录，**不作硬门** | `tools/dense_build/pack_dense_asset.py:189-199` |
| 夹具行对齐 | min **≥0.9999** | `tools/dense_build/gen_device_parity_fixture.py:67` |
| `check_asset` | **10/10 OK** | `tools/dense_build/check_asset.py`；本文件 §5 |
| 设备判官 | `OK (1 test)` + exit=0 + 同代 MATCH | `build/agent-batch22/v21/step3_install_run_device.sh:83-88` |
| 零漂移 | 同路 lexical/fused symdiff **均 0** | `build/agent-batch19/a1/zero_drift.py:1-27` |
| 预算 | p50 150ms / p95 250ms；overBudget 只观测 | `docs/research/2026-10-26-kb-v21-content-update-prereg.md:67` |
| 显著性 | `--reps 20000 --seed 0`；CI 上界 **≥0** | `docs/research/2026-10-26-kb-v21-content-update-prereg.md:63` |
| 内容更新 | 主集 ≥基线 + 逐章不劣 + 零漂移 + 负向守卫 | 同上 `:56-63` |
| 机制臂 | 严格更高 + 逐章不劣 + 负向守卫 + 零漂移否决 | `docs/research/2026-10-22-d3-round3-a1-prereg.md:16-21` |

## 附录 C：证据路径（本次发布应留的目录形状）

```text
build/kb-release-<YYYYMMDD>/
  pre-sha.txt                 # 写前全值 sha
  backup/                     # 脏文件/未跟踪文件/build 产物的字节备份
  gate.log                    # 装置门
  rebuild-dryrun.out.txt      # 别名干跑
  rebuild-write.out.txt       # 别名写入
  promote-dry.log / promote-run.log
  dense.log                   # 8 步链执行记录 + PRE/POST 快照
  step0_samegen_check.log
  step1_coldstart.log
  step2_build.log             # gate + assemble + apk_vec_sha
  step3_run_device.log        # install/samegen/am instrument
  step4_pull_ledgers.log      # 4 份 MISS 账本 + stamp
  step5_run_jvm.log
  zero-drift.json
  significance-{fused,lexical}-vs-<baseline>.json
  test-results/testDebugUnitTest/TEST-*.xml   # 聚合计数（tests/failures/errors/skipped）
docs/research/evidence/<YYYY-MM-DD>-batch-<NN>/   # 仓内 .md 证据（设备结论的唯一关闭依据）
```

> `build/` 下的文件不入仓；入仓的是 `docs/research/evidence/.../*.md` 与批次记录/预注册 §7。两者都要留，职责不同：`build/` 供本机复查，`docs/` 供跨批次复核。

---

## 校验记录（2026-10-26）

> **性质**：只读校验。本轮**未写生产文件、未跑设备用例、未重打 dense、未执行 `--write`/正式 `promote`/`git checkout`**（仅 Gradle `--dry-run` 的构建缓存/报告与 `/tmp` 临时文件）。目的：逐条确认 runbook 的命令与参数存在且可执行，并把无法实跑的命令显式标为“未实跑仅文档”。
>
> **环境**：Git Bash；Python `3.13.14`；Gradle `9.6.1`；设备 `emulator-5584`（AVD `kb_test_device`，`sys.boot_completed=1`）；工作树 HEAD `5bc8bdf8`。

### V.1 实跑通过（本轮真实执行并读取 rc/输出）

| # | 命令（与 runbook 同形） | 结果 |
| --- | --- | --- |
| V1 | `PYTHONPATH=tools python -m kb_build.promote --dry-run` | **rc=0**；`gates / consistency / roundtrip / manifest` 全 OK；`manifest：version→22`；末行“dry-run：门全绿，未写盘；将写入 version 22” |
| V2 | `PYTHONPATH=tools python -m kb_build.rebuild_aliases`（**干跑，无 `--write`**） | **rc=0**；`例外：应用 35，冲突改判 0，拒绝 0`；`别名：40798 → 40798；变更节点 0`；末行“未写盘；加 --write 生效”。当前树已是 v21，故净增 0 是幂等结果，不代表新批次 |
| V3 | `PYTHONPATH=tools python -m kb_build.rebind_materials`（**报告模式**） | **rc=0**；`改绑 0；跳过（幂等/错行）9575`；`材料总数 64973；悬空绑定 0；可写回: True` |
| V4 | `PYTHONPATH=tools python -m kb_coverage.alignment --check` | **rc=0**；`MATH 候选 308，已判定 308`、`PHYSICS 138/138`、`CHEMISTRY 73/73`、`BIOLOGY 142/142`；`台账完整：全部候选恰有一行判定，target 均在包中` |
| V5 | `python tools/ci/run_kb_checks.py` | **rc=0**；`gates / consistency / roundtrip / manifest / dense / judgment_drift` 全 OK；`report_only` 为 INFO |
| V6 | `python tools/dense_build/check_asset.py` | **rc=0**；**10/10 OK**（packSha256 `a7192484…`、`.vec` `79a22270…`、count=44,043、layout 44,043 逐条一致） |
| V7 | `python -m unittest discover -s tools/tests -t tools -p "test_dense_asset_gate.py"` | **rc=0**；`Ran 6 tests` / `OK` |
| V8 | `python build/agent-batch19/a1/zero_drift.py --device-lexical build/agent-batch22/v21/device-lexical-misses.txt --device-fused build/agent-batch22/v21/device-fused-misses.txt --jvm-lexical build/agent-batch22/v21/jvm-lexical-misses.txt --jvm-fused build/agent-batch22/v21/jvm-fused-misses.txt --json-out /tmp/zd-validation.json` | **rc=0**；`same-route lexical symdiff = 0`、`same-route fused symdiff = 0`；`verdict: lexical=PASS fused=PASS` |
| V9 | `python tools/kb_coverage/retrieval_significance.py --golden tools/kb_coverage/tables/golden_queries_v2.json --misses build/agent-batch22/v21/device-fused-misses.txt --misses-b build/agent-batch19/a1/device-fused-misses.txt --reps 20000 --seed 0 --json` | **rc=0**；JSON `a.recall = b.recall = 0.9069767441860465`；`paired = {delta_recall_b_minus_a: 0.0, ci95_chapter: [0.0, 0.0], p_two_sided: 1.0}` |
| V10 | `python -c "import json;d=json.load(open('core/data/src/main/resources/knowledge/moe-2025-update-manifest.json',encoding='utf-8'));print(d['packId'],d['version'],d['contentVersion'])"` | `moe-2025-four-subjects-v1 21 9f9e99ceec47cf8c` |
| V11 | `sha256sum core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | `a719248448f314d1…`（与 runbook §2.1 表一致） |
| V12 | Python `csv.reader` 计数（runbook §1.1 复核块） | `35 / 37553 / 22559` 数据行，与 §2.1 一致 |
| V13 | 25 sidecar 聚合复算（runbook §1.3 构造） | `ed1c6d4f6d9d331a0982408c2f60a660a8490cbad5c2a74d438604d12057355e` |
| V14 | `sha256sum f.txt > list.txt && sha256sum -c list.txt`（临时目录，验证回滚终扫格式） | `f.txt: OK`，rc=0（Windows 的 `hash *file` 格式可被 `-c` 解析） |
| V15 | XML 聚合脚本（runbook §1.6 / §4.3） | rc=0；读现有 148 个 XML → `tests=1164 failures=0 errors=0 skipped=0`。**这是既有产物，不是本轮跑的全量** |
| V16 | samegen 解包脚本（runbook §3.2） | `worktree = local = installed = 79a22270b54180f6…`（已装 APK 从 emulator-5584 pull 到临时目录；本轮 pull 目标是 Git Bash `/tmp`，用 `cygpath -w` 交给原生 Python 读取——runbook 用相对 `$D` 路径，无此问题） |
| V17 | `adb -s emulator-5584 emu avd name` / `shell getprop sys.boot_completed` / `shell pm path com.tingyun.smartmistakebook.core.data.test` / `pull` | `kb_test_device` / `1` / 返回已装 `base.apk` 路径 / `pull rc=0` |
| V18 | `git rev-parse HEAD`；`git status --porcelain \| wc -l`；`git diff --stat -- tools/kb_build/tables/alias_{exceptions,map,dropped}.csv`；`git ls-files --eol -- …alias_exceptions.csv`；`git check-ignore -v build/agent-batch19/a1/zero_drift.py` | HEAD `5bc8bdf8`；porcelain 698；别名表无 diff（干净）；EOL 正常输出；`zero_drift.py` 命中 `.gitignore:15`（确认未入仓） |
| V19 | Gradle 任务存在性：`./gradlew.bat :core:data:compileDebugUnitTestKotlin :core:data:testDebugUnitTest :core:data:assembleDebugAndroidTest --dry-run --console=plain` | **rc=0**，`BUILD SUCCESSFUL in 6s`，三任务均 `SKIPPED`（dry-run 不执行） |
| V20 | `./gradlew.bat :core:data:testDebugUnitTest --tests "*GoldenRetrievalJvmTest*" --dry-run --console=plain` | **rc=0**，`BUILD SUCCESSFUL in 2s`（`--tests` 参数可解析） |
| V21 | §2.1 的整段 pre-sha 生成块（**修复后**，输出到 `/tmp/pre-sha-validation2.txt`） | **rc=0**；`lines=59`；`stderr_bytes=0`。首次按原文运行得到 **58 行 + `encoder-parity-json: No such file`**，见 V.5 |

### V.2 仅 `--help` 验证（入口与参数存在，未执行）

以下命令均返回 **rc=0**，并显示 runbook 使用的选项；它们会写盘/重打/跑测试，本轮**不执行**：

| 命令 | 本轮验证方式 | 关键选项（`--help` 输出） |
| --- | --- | --- |
| `PYTHONPATH=tools python -m kb_build.rebuild_aliases --write` | `--help` | `[--write]` |
| `PYTHONPATH=tools python -m kb_build.promote`（正式写） | `--help` | `[--dry-run] [--json]`（无 `--write`；不加 `--dry-run` 即写盘） |
| `PYTHONPATH=tools python -m kb_build.rebind_materials --write` | `--help` | `[--write] [--root ROOT]`（runbook 明确不用于发布链） |
| `python tools/dense_build/export_bge_int8.py --skip-export` | `--help` | `--skip-export`、`--model`、`--publish` |
| `python tools/dense_build/pack_dense_asset.py` | `--help` | `--repo-root`、`--model` |
| `python tools/dense_build/stage3_expectation.py` | `--help` | `--repo-root`、`--model` |
| `python tools/dense_build/gen_tokenizer_fixture.py` | `--help` | `--repo-root`、`--snapshot` |
| `python tools/dense_build/gen_dense_device_fixture.py` | `--help` | `--repo-root`、`--model` |
| `python tools/dense_build/export_golden_query_vectors.py` | `--help` | `--repo-root` |
| `python tools/dense_build/gen_device_parity_fixture.py` | `--help` | `--repo-root`、`--model` |
| `python tools/dense_build/stage3_device_sim.py` | `--help` | `--repo-root`、`--model` |
| `python tools/dense_build/check_asset.py` | `--help` + 实跑（V6） | `--repo-root`、`--json` |
| `python build/agent-batch19/a1/zero_drift.py` | `--help` + 实跑（V8） | `--device-lexical/-fused`、`--jvm-lexical/-fused`、`--json-out` |
| `python tools/kb_coverage/retrieval_significance.py` | `--help` + 实跑（V9） | `--golden`、`--misses`、`--misses-b`、`--reps`、`--seed`、`--json` |

### V.3 未实跑仅文档（必须由发布轮真跑，不能引本节当通过证据）

| 命令 / 动作 | 为什么本轮不跑 |
| --- | --- |
| `./gradlew.bat :core:data:compileDebugUnitTestKotlin`（真执行） | 只验证了任务名（V19）。真执行会触发构建；本轮只做只读校验 |
| `./gradlew.bat :core:data:testDebugUnitTest [--tests …] --rerun` | 会跑真实单测；V15 的 XML 是既有产物，不是本轮结果。发布轮必须自己跑并读 XML |
| `./gradlew.bat :core:data:assembleDebugAndroidTest` | 会重打包 APK；本轮未执行 |
| `adb … install -r -t` / `logcat -c` / `shell am instrument` | 会改动设备状态并跑判官；5584 可能被他线使用，本轮只做只读查询（V17） |
| `adb … emu kill` / `emulator -avd kb_test_device … -wipe-data` | 冷启/清数据是状态变更；本轮未执行 |
| `export_bge_int8.py --skip-export` / `pack_dense_asset.py` / `stage3_expectation.py` / 5 类夹具脚本 / `export_golden_query_vectors.py` / `stage3_device_sim.py` | 会写 `.vec`、旁车、npy、夹具与 `build/stage3-device-expectation.json`；本轮只验证 `--help` |
| `rebuild_aliases --write` | 会写 staging 包与别名表；本轮跑的是干跑（V2） |
| `promote`（无 `--dry-run`） | 会写成品包 28 文件；本轮跑的是 dry-run（V1） |
| `git checkout -- …`（回滚） | 破坏性写操作；runbook 的 §2.2 未在本轮执行 |
| `cp $D/backup/...` / `rm -f "$APK"` / `sha256sum -c $D/pre-sha.txt`（真实批次） | 需要真实批次的 `$D` 与备份；本轮只用临时样本验证了 `sha256sum -c` 的格式（V14） |

### V.4 校验结论

1. **未发现不存在的命令或错误参数**：4 个 Python 模块入口、9 个 dense 脚本、2 个检索脚本的 `--help` 均 rc=0；3 个 Gradle 任务名与 `--tests` 参数经 `--dry-run` 确认存在；`adb`/`emulator` 二进制与 AVD `kb_test_device` 在位。
2. **关键门可在当前树复现**：`promote --dry-run` 全绿（V1）、`run_kb_checks` 全绿（V5）、`check_asset` 10/10（V6）、`test_dense_asset_gate` 6/6（V7）、`alignment --check` 台账完整（V4）、零漂移同路 0（V8）、显著性 0 差（V9）。
3. **边界与注意**：
   - `zero_drift.py` 确认位于 `build/agent-batch19/a1/` 且被 `.gitignore:15` 忽略；发布轮必须先确认脚本存在（runbook §1.8 已写）。
   - `rebuild_aliases` 干跑的“净增 0”是 v21 已落地的幂等结果；新批次应等于预注册算术净增（runbook §1.1 的门写法不变）。
   - V15 的 1,164 条 XML 是既有产物，**不能**当作本轮全量通过；发布轮必须跑 `:core:data:testDebugUnitTest --rerun` 并重新聚合。
   - `alignment --check` 是内容侧完整性门，已作为“相邻只读门”写入 §1.2；它**不进入** §1.9 的发布判据。
   - V16/V17 证明**当前**装置与工作树同代（`79a22270…`），但不替代发布轮重新构建/安装后的 **post-install** 同代核验。
4. **标注规则**：V.1 = 本轮实跑；V.2 = 仅 `--help`；V.3 = 未实跑仅文档。除 V.1 外，任何条目都不得写成“已通过”。

### V.5 本轮发现并修复的命令缺陷（1 处）

| 项 | 内容 |
| --- | --- |
| 位置 | §2.1 的 pre-sha 生成块，原行 `sha256sum …/encoder-parity-{cases.tsv,vectors.f32,json}` |
| 缺陷 | Bash 花括号展开把 `json` 拼成 `encoder-parity-json`（缺 `.`），实际文件是 `encoder-parity.json`。首次按原文运行：`sha256sum: …/encoder-parity-json: No such file or directory`，输出 **58 行** |
| 危险性 | **整段 brace group 的最终 rc 仍为 0**（最后一条 `sha256sum` 成功），只看 rc 会漏掉缺文件；这正是回滚终扫会踩的坑 |
| 修复 | 改为显式三行：`encoder-parity-cases.tsv`、`encoder-parity-vectors.f32`、`encoder-parity.json`；并在 §2.1 增加“必须检查 stderr 为空 + `wc -l` = 59，不得只看 rc”的硬要求 |
| 复验 | 修复后重跑：**rc=0、59 行、stderr 0 字节**（V21） |
| 其余花括号 | `alias_{exceptions,map,dropped}.csv`、`dense-{scan,fusion,device-order}-reference.txt`、`tokenizer-parity-{cases.txt,stages.txt,summary.json}` 均展开正确（59 行里逐项在上） |

> 本条是本轮校验唯一的命令级缺陷；其余未实跑项（V.3）不是缺陷，而是需要发布轮真跑的动作。
