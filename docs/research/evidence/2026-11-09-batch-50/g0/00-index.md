# G0 数据安全（批次 50）证据索引

- 执行时间：2026-11-08（本会话）
- 计划：`.zcode/plans/plan-2026-11-09-batch-50-release.md` G0 items 8–11
- 设备：`emulator-5590`（API 34 / `PAGE_SIZE=4096` / x86_64）；本轮**没有**执行任何设备用例
- 结论：四方门与自测 **PASS**；设备复跑 **命令已备、NOT RUN**；B 域 75 证据 **UNVERIFIED（旧 PASS 为 v73 历史）**

## 1. 逐项结果

| ask 项 | 结果 | 证据 |
|---|---|---|
| 1) 设计件机器块单一 begin/end，内容 75/99/closure 6/hash；`check_backup_plan_sync.py` exit 0 | **完成**：严格解析的 v3 块恰 1 个；`EXIT=0` | `01-plan-sync-gate-after.txt`、`03-static-preconditions.txt` |
| 2) 若四方不一致，按 KSP 生成物同步（禁手写 hash） | **内容层原本已一致**：本会话按门算法独立复算空库全选摘要 `8567faae…` == 测试 golden == 机器块；仅结构（标记）与脚本钉值不一致，已修；`identityHash` 取自 `75.json`，未手写新值 | `01-plan-sync-gate-after.txt`（digest 三处同值）；`03-static-preconditions.txt` |
| 3) 设备复跑命令（迁移 1..75、备份 34、trimmer、rowCounts、drill） | **命令已准备，未执行** | `04-device-rerun-commands.md` |
| 4) 台账 B 域标注（v73 历史、75 需新证据、旧 PASS 不算 75） | **完成**：B 域标注文件 + 域索引注记；`ledger.md` 字节未动 | `05-b-domain-annotation.txt`、`docs/acceptance/b-domain-schema75-annotation.md` |

## 2. 本会话实跑命令与关键输出

```text
$ python -I tools/ci/check_backup_plan_sync.py
... 26 项 OK ...
PASS 四方一致（code / §9.14 机器块 / golden / 75.json）
EXIT=0

$ python -I -m pytest tools/tests/test_backup_plan_sync.py -q
.........                                                                [100%]
9 passed in 0.42s
EXIT=0
```

修复前同一命令（本会话首跑，未改任何文件时）：

```text
$ python -I tools/ci/check_backup_plan_sync.py
ERROR 无法建立可比事实：设计件必须恰有一个 6c2-plan-v3 机器块，实际 0
EXIT=2
```

> 这条「红→绿」是本会话亲测；修复前输出来自同一会话的首跑记录，修复后输出见
> `01-plan-sync-gate-after.txt`。

## 3. 改了什么

### 3.1 设计件机器块（`docs/research/2026-10-17-6c2-content-selection-design.md`）

- §9.11 的残块：`6c2-plan-v3:begin`（无 v3 end，末尾是 `v4:end`）→ 冻结为
  `6c2-plan-v5:begin/end`（其内容本来就是 closure 5 / schema 73），并改写节首说明：
  该块只作历史，不再驱动门禁。
- §9.14 的当前块：`6c2-plan-v6:begin/end` → `6c2-plan-v3:begin/end`。这样全文只有**一对**
  `6c2-plan-v3` 标记，内容仍是 `closure 6 / schema 75 / identity f9a3d1c9… / 99 表`。
- 给当前块补 `"views": ["library_catalog","knowledge_question_lattice"]`（取自 `75.json`
  的 `views[].viewName`），使门脚本的 views 三方比对（code/block/75.json）不至于因缺键报错；
  没有新增任何表/类别/摘要。
- 追加一行 2026-11-08 的版本说明（parser 契约名统一为 v3，内容随 KSP 75 同步）。

### 3.2 四方门脚本（`tools/ci/check_backup_plan_sync.py`）

- `SCHEMA_PATH`：`73.json` → `75.json`；表数钉值 `92` → `99`；`schemaVersion/identityHash/table list`
  的记录名与详情改为 75.json。
- 机器块 golden 键：`block["digest"]["goldenEmptyFull"]["sha256"]` →
  `block["digest"]["emptyFullSelectionGolden"]`（现行块的真实键名；仍是独立复算后比对，不是读块自证）。
- views：`code == block == 75.json`（此前只比 code==73.json）。
- v4 冻结键集合：新增解析 `ClosureRuleTables.interiorTables`，把公式修成
  `current − error_cause − interior + material_feature`（99−2−7+1=91）；此前只减 error_cause，
  在 99 表下会算出 98。`materialFeatureTable` 的解析切片改为止于 `interiorTables`，避免把
  7 张 interior 表名误算进 material。v4 的 91 键/83 裁剪/v4 摘要 golden/三份夹具仍全部校验。

### 3.3 测试与注释

- `SmbkSelectionPlanTest.kt`：机器块正则 `6c2-plan-v6` → `6c2-plan-v3`；其余断言逐字未动
  （`GOLDEN_EMPTY_FULL_DIGEST` 仍是 `8567faae…`）。kdoc 的 97/113 → 99/123（`75.json` 实测
  99 entities / 123 FK），`73.json`→`75.json`。
- `SmbkSelectionPlan.kt`：仅注释（schema 74/v5/97 表/identity 4d2c8e0f… → schema 75/closure 6/
  99 表/f9a3d1c9…，机器块标记 v5→v3）；常量与逻辑零改动。
- `tools/tests/test_backup_plan_sync.py`：漂移用例的 identity 改为现行 `f9a3d1c9…`；新增
  `test_machine_block_marker_drift_exits_two`（v3 end 缺失必须 exit 2，正是本轮 KD-80 复发形态）。

### 3.4 设备用例的静态前置（为了让 §3 的命令不是「已知必红」）

- `BackupRowCountsCoverageInstrumentedTest.kt`：`73.json`→`75.json`，表/键计数 `92`→`99`，
  文案同步。等值断言未放宽。
- `BackupSelectionRestoreInstrumentedTest.kt`：v4 包恢复后的版本断言 `73` →
  `SmbkSelectionPlan.SCHEMA_VERSION`（当前 75）。
- `BackupV4PackRestoreInstrumentedTest.kt`：`readDatabaseVersion()` / `user_version` 断言
  `73` → 当前 schema；回执 `databaseSchemaVersion=72` 不变。

### 3.5 台账 B 域

- 新增 `docs/acceptance/b-domain-schema75-annotation.md`：90 个 B 行、282 个证据 token 全部
  指向 batch-44（schema 73）；73 行描述明写 v73；旧 PASS 只对 73 目标有效。
- `docs/acceptance/domain-index.md` 的 B 行出口追加同一结论与两处指引。
- **没有改 `docs/acceptance/ledger.md`**：其 sha256 被 922 份行级复核 JSON 绑定
  （`acceptance_report.py:224-231`、`:346`），改一个字节就会全量失配。

## 4. 未跑 / 未验证（不得读成通过）

- **设备五组全部 NOT RUN**（`04-device-rerun-commands.md`）；迁移 1..75 / 备份 34 / trimmer /
  rowCounts / drill 在 75 上**没有**本轮证据。
- **Gradle/JVM 未跑**：`SmbkSelectionPlanTest`（含新正则）、改过的 3 个 androidTest 均未编译/
  未执行；`assembleDebugAndroidTest` 未跑。→ UNVERIFIED。
- **acceptance_report / acceptance_lint 未跑**；B 域 75 换绑未做。
- 门脚本只证明静态四方同源，不证明运行时 schema/迁移/设备备份行为（脚本自述边界不变）。

## 5. 协作面核对

- 开工前 `git status --porcelain -- core/data/.../backup docs/research docs/acceptance tools/ci tools/tests`
  显示：`SmbkSelectionPlan.kt`、`SmbkSelectionPlanTest.kt`、设计件、门脚本、pytest 均为干净；
  5 个 backup androidTest 中 `BackupRestoreInstrumentedTest`、`BackupRowCountsCoverage…`、
  `BackupSelectionRestore…`、`BackupVisualReference…`、`DeviceMigrationDrill…` 为既有 M
  （mtime 2026-11-07 05:18，非本会话在飞）。本会话只对其中 2 个加最小 hunk，未整文件重写；
  `BackupRestore…`/`BackupVisualReference…`/`DeviceMigrationDrill…` 的既有改动**原样保留、未触碰**。
- 本会话没有新增/修改任何 JS/UI 在飞文件。

## 6. 残余风险

1. 机器块标记从 v6 改回 v3 是**契约名统一**；若他处（未在本会话检索到的脚本/文档）硬编码
   `6c2-plan-v6`，会失配。本会话 `grep -rn "6c2-plan-v"` 只命中设计件、`SmbkSelectionPlan.kt`
   注释、`SmbkSelectionPlanTest.kt` 与门脚本，均已同步。
2. 门脚本的 v4 公式依赖 `ClosureRuleTables.interiorTables` 字面量；若有人把该集合改名/内联，
   脚本会 exit 2（fail-closed），不会静默通过。
3. 设备命令未执行，因此 75 的 rowCounts/备份/迁移结论仍为空；不得引用 batch-44 的 v73 PASS。

## 7. 本目录证据文件

| 文件 | 内容 |
|---|---|
| `00-index.md` | 本索引 |
| `01-plan-sync-gate-before.txt` | 修复前首跑（exit 2，已不可复现） |
| `01-plan-sync-gate-after.txt` | 修复后门输出（`GATE_EXIT=0`） |
| `02-plan-sync-pytest.txt` | 自测（`PYTEST_EXIT=0`，9 passed） |
| `03-static-preconditions.txt` | 标记/75.json/块解析/设备/测试计数/迁移范围 |
| `04-device-rerun-commands.md` | 五组设备复跑命令（NOT RUN） |
| `05-b-domain-annotation.txt` | B 域 90 行/282 token/ledger sha 核对 |
| `06-files-changed.md` | 本会话改动与未触碰文件清单 |

