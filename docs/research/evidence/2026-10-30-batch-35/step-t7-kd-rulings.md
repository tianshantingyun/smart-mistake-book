# 批次 35 / T7 KD 裁决：KD-80 复核回链 + KD-81 关闭 + A-08/反向虚报清理（2026-10-30）

> **性质**：台账维护（只改 docs）。只读核对 + 两条非 Gradle 命令实跑（pytest、静态清单门）。**零 Gradle、零设备执行、零 git 写操作**。
> **快照时点**：2026-10-30；HEAD `902d7acce117c3e33905b1ec5ba9ea0d61a21f9c`（`git rev-parse HEAD` 实跑，与批次 35 基线一致）。
> **对照计划**：`.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md` 任务 6/7/8；**对照基线**：`docs/research/2026-10-30-batch-35-baseline.md` §四（其判定：KD-80 已在批次 34 内 closed 且含裁决文本 → 本批"只需复核裁决文本无需再落"；KD-81 待做收敛为"-shm 裁定落台账 + 关闭"；A-08 已由 v3 补记 5 回填 → 本批把行口径改为"JS 半边已落"）。
> **本文件是 T7 KD 裁决轨自己的命令日志**；设备/JVM 套件本轮一律未重跑（设备绿绑定源快照），只做证据回链复核。

---

## 1. KD-80：四条逐条回链复核（关闭维持）

批次 34 已写关闭裁决（`docs/known-defects.md` KD-80 设备轨回填块 + 记录员"确认关闭"块 + 记录 §4.7 四条裁决表）。本轨按任务 6 逐条对回证据文件复核，并落批次 35 复核块（`docs/known-defects.md` KD-80 节末"裁决复核与回链（2026-10-30 批次 35）"）：

| 条件 | 回链（本轨实读） | 本轨新增验证 |
|---|---|---|
| 80-C1 | E-02 `docs/research/evidence/2026-10-29-batch-34/step02-backup-rowcounts-trimmer-c4.md` §3：`BackupRestoreInstrumentedTest` `OK (34 tests)` | raw `build/device-track-batch34/step01-backup.log` 实读 `OK (34 tests)`；`BackupSnapshotTrimException`/`expected:<70>`/`expected:<69>` grep = **0** |
| 80-C2 | 设备侧逐名 E-02 §4/§4.1：`BackupRowCountsCoverageInstrumentedTest` `OK (2 tests)`，90 键逐名、`missing=[] extra=[]`；静态半边批次 34 T7 §2 `RESULT=PASS`/`EXIT=0` | raw `step02-rowcounts.log` 实读 `:7/:14` `everyRowCountMatchesThePackagedAndRestoredDatabaseByName`、`:21/:28` `manifestRowCountsEnumerateTheNinetySchemaTablesByName`、`:34` `OK (2 tests)`；静态门本轮按 T7 §2 原命令复跑 → `RESULT=PASS`/`EXIT=0`（§4.2）；现树 `71.json` sha256 = `4a13d9d0b5ee…`（§4.3）= E-02 §4.1 设备绿轮所用 asset sha → 静态/设备两半边对同一份 schema 字节 |
| 80-C3 | 设备裁剪器 E-02 §3 `OK (9 tests)`；JVM `docs/research/evidence/2026-10-29-batch-33/step-jvm-80c3.md`：`SmbkSelectionPlanTest` 12/0 + `VisualCheckpointRetentionTest` 17/0（批次 33 主会话补证） | raw `step04-trimmer.log` 实读 `OK (9 tests)`、异常签名 grep = 0。**口径保留不变**：批次 34 未重跑 JVM，本批亦未重跑 |
| 80-C4 | E-02 §3：`BackupVisualReferenceInstrumentedTest` `OK (5 tests)` | raw `step05-c4-visual.log` 实读 `OK (5 tests)`、异常签名 grep = 0 |

**结论：KD-80 关闭维持（closed 2026-10-29 批次 34；2026-10-30 批次 35 复核确认）。** 本轨只读证据回链，不构成新执行证据；已知边界沿用批次 34（设备源快照 find 集合当时不含 `core/model/src`，判据文档已由复核修复轮更正，见 E-01 §8）。

## 2. KD-81：按主会话裁定关闭（db+wal 进身份、-shm 排除）

- **裁定**（计划 §0，本轨落台账）：快照内容身份 = db + `-wal`（`identityKind="inputs-v1"`，`tools/split_golden/export_split_eval.py:78`；身份 = `sha256(canon({"db": …, "wal": …}))`，`:107-135`）；`-shm` 显式排除且按裁定接受为最终口径——`-shm` 是派生的共享内存索引、不承载已提交帧，只读导出会重建/改写它（工具 docstring `:101-102`），纳入即把读副作用当漂移（假漂移/自指）。关闭条件 ② 的"`-shm` 字面覆盖"自裁定起**改判为已满足（按排除口径）**。
- **T5b 回链（已落地）**：`test_export_rejects_wal_change_inside_transaction`（`tools/tests/test_split_gate.py:829`，docstring：读事务**内**发生的已提交写入，后比对仍必须 `exit 2` 且校验先于写盘）。
- **关闭条件逐条**：① 满足（`:669` 用例 + `:107-135` 身份公式）；② 按裁定满足；③ AUD-08 设备库复跑**按裁定不再作为关闭前置**（工具侧缺陷已消灭；未来复跑导出自动携带 db+wal 身份与导出前后比对 `:213`/`:290-298`/`:234` `BEGIN`/`:300/:309` `payload.sha256`），批次 31 快照度量局限保留在 Symptom/Impact，不因关闭改写。
- **残留登记（不阻塞关闭）**：身份 = 导出前后两次字节清单，无锁、不绑定 SQLite 读事务；ABA/撕裂 WAL 上限已在批次 34 块登记。`docs/current/visual-generation-contract.md:208` 的快照身份边界句停在批次 32 时点，与现树口径不符——**docs/current 热区，本轨不改**，留契约轨回改。
- **结论：KD-81 关闭（closed 2026-10-30 批次 35）**；标题与关闭块已落 `docs/known-defects.md`。

## 3. A-08 行与批次 34 反向虚报句清理

- **A-08 行**（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:196`）：现状由笼统"已落"改为"**JS 半边已落**"，并登记**入口侧约束半边（宿主显式传 basis 时以 program 摘要复核）未落、归批次 35 计划任务 12**。锚点本轨实读复核在树：`core/model/.../FigureGeneration.kt:646` `fun figureJsBasisFingerprint(`、`core/data/.../DefaultFigureGenerationCoordinator.kt:698` `FigureGenerationMethod.JS -> figureJsBasisFingerprint(`、`FigureGenerationCoordinatorTest.kt:463` `distinctJavascriptProgramsGetDistinctBasisFingerprints`。头注新增 **v3 补记 7**（不新增行、仍 69 行、0 项转"通过"、MVA 不成立维持、`availableMethods` 未动）。
- **反向虚报句清点结论**：
  - KD-80 节：批次 34 T7 块的"本批尚无任何设备执行结果可引用"**已被**设备轨回填块显式标注"只对 18:14 快照成立"取代（批次 34 内已清理，本轨复核确认，无需再改）。
  - KD-81 节：末块"保持 open / `-shm` 口径待裁"由本批关闭块显式取代（历史块不改写，沿批次 32 起的"新块钉现树"纪律）。
  - `docs/REFACTOR-MASTER-LINE.md:4` 关键限定句"KD-81 仍 open（`-shm` 字面项/设备复跑）"→ 改为"KD-81 已 closed（2026-10-30 批次 35 …）"（唯一因关闭而失真的现树句）。
  - 历史记录（批次 34 记录 §3.5/§5.2 #I、批次 32/33 记录、R2 研究）为时点快照，按纪律不改写；`docs/current/*` 热区不改。

## 4. 命令与输出（本轨实跑）

### 4.1 KD-81 回归（T5b 在内）

```bash
$ python -m pytest tools/tests/test_split_gate.py -q
.....................................                                    [100%]
37 passed in 2.44s
EXIT=0
```

（对照：批次 34 T5 轮 36 passed in 1.74s、记录员复核轮 37 passed in 2.87s——本批现树复跑同绿。）

### 4.2 80-C2 静态门复跑（S=71，与批次 34 T7 §2 同一命令）

```bash
$ S=71 python - <<'PY'
import json, os, re, sys
v = os.environ.get("S", "71")
plan_path = "core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt"
schema_path = f"core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/{v}.json"
if not os.path.exists(schema_path):
    print(f"SCHEMA={v} MISSING {schema_path}"); sys.exit(2)
src = open(plan_path, encoding="utf-8").read()
def block(a, b): return src[src.index(a):src.index(b)]
views = re.findall(r'"([a-z0-9_]+)"', block("val views", "val categoryTables"))
tables = []
for a, b in [("val categoryTables", "val alwaysTables"), ("val alwaysTables", "val derivedTables"),
             ("val derivedTables", "val splitTables"), ("val splitTables", "val rebuildTables")]:
    tables += re.findall(r'"([a-z0-9_]+)"', block(a, b))
db = json.load(open(schema_path, encoding="utf-8"))["database"]
json_tables = [e["tableName"] for e in db["entities"]]
json_views = [x["viewName"] for x in db["views"]]
plan_ver = int(re.search(r'const val SCHEMA_VERSION: Int = (\d+)', src).group(1))
plan_hash = re.search(r'const val IDENTITY_HASH: String = "([0-9a-f]+)"', src).group(1)
only_plan = sorted(set(tables) - set(json_tables)); only_json = sorted(set(json_tables) - set(tables))
dups = sorted({t for t in tables if tables.count(t) > 1})
print(f"SCHEMA={v} PLAN_TABLES={len(tables)} UNIQUE={len(set(tables))} JSON_TABLES={len(json_tables)}")
print(f"ONLY_PLAN={only_plan}"); print(f"ONLY_JSON={only_json}"); print(f"DUP={dups}")
print(f"VIEWS_MATCH={set(views) == set(json_views)}")
print(f"PLAN_VERSION={plan_ver} JSON_VERSION={db['version']}")
print(f"PLAN_HASH={plan_hash} JSON_HASH={db['identityHash']}")
ok = (len(tables) == len(set(tables)) == len(json_tables) and not only_plan and not only_json
      and set(views) == set(json_views) and plan_ver == db["version"] == int(v) and plan_hash == db["identityHash"])
print("RESULT=" + ("PASS" if ok else "FAIL")); sys.exit(0 if ok else 1)
PY
SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90
ONLY_PLAN=[]
ONLY_JSON=[]
DUP=[]
VIEWS_MATCH=True
PLAN_VERSION=71 JSON_VERSION=71
PLAN_HASH=714c0ecb85ee5edf74f0013740148138 JSON_HASH=714c0ecb85ee5edf74f0013740148138
RESULT=PASS
EXIT=0
```

### 4.3 71.json 内容身份（与设备绿轮 asset 同源核对）

```bash
$ python - <<'EOF'
import json,hashlib,pathlib
p=pathlib.Path('core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json')
b=p.read_bytes(); d=json.loads(b)['database']
print('bytes',len(b),'sha256',hashlib.sha256(b).hexdigest())
print('version',d['version'],'identityHash',d['identityHash'],'entities',len(d['entities']),'views',len(d['views']))
EOF
bytes 378961 sha256 4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94
version 71 identityHash 714c0ecb85ee5edf74f0013740148138 entities 90 views 2
```

（= E-02 §4.1 记录的设备测试 APK 内 asset sha `4a13d9d0…`，逐字节相同。）

### 4.4 只读核对命令（grep/读文件，全部命中即本文件所引）

```bash
$ git rev-parse HEAD
902d7acce117c3e33905b1ec5ba9ea0d61a21f9c
$ git status --porcelain -- docs/known-defects.md docs/research/2026-10-26-batch-24-4e2-admission-checklist.md docs/REFACTOR-MASTER-LINE.md
# 开工时输出为空（三文件干净；改动后仅含本轨改动）
$ grep -E "OK \(" build/device-track-batch34/step01-backup.log | tail -1   # OK (34 tests)
$ grep -E "OK \(" build/device-track-batch34/step02-rowcounts.log          # :34 OK (2 tests)
$ grep -E "OK \(" build/device-track-batch34/step04-trimmer.log | tail -1  # OK (9 tests)
$ grep -E "OK \(" build/device-track-batch34/step05-c4-visual.log | tail -1 # OK (5 tests)
$ grep -n "def test_" tools/tests/test_split_gate.py | grep -E ":(669|701|721|735|765|789|829):"
# 669/701/721/735/765/789 六件 KD-81 直接用例 + 829 T5b，行号逐一命中
$ grep -n "figureJsBasisFingerprint" core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/FigureGeneration.kt
# 646: fun figureJsBasisFingerprint(
$ grep -n "distinctJavascriptProgramsGetDistinctBasisFingerprints" core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationCoordinatorTest.kt
# 463
$ grep -n "FigureGenerationMethod.JS -> figureJsBasisFingerprint" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/DefaultFigureGenerationCoordinator.kt
# 698
$ git log --oneline -1 -- tools/split_golden/export_split_eval.py
# 93cb099f fix(aud08): 批次34 KD-81 快照身份…（36 例回归）
```

## 5. 本轨改动文件（只 docs）

1. `docs/known-defects.md`：KD-80 节末新增"裁决复核与回链（2026-10-30 批次 35）"块（四条逐条回链）；KD-81 标题 open → **closed 2026-10-30 批次 35**；节末新增"裁决：KD-81 关闭"块（裁定内容/T5b 回链/关闭条件逐条/残留登记）。历史 Progress 块一律未改写。
2. `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`：A-08 行现状改"JS 半边已落"+ 入口侧半边登记；头注新增 v3 补记 7。
3. `docs/REFACTOR-MASTER-LINE.md`：第 4 行关键限定句的 KD-81 状态片段随关闭更新。
4. 本文件。

**未改**：`docs/current/*`（热区，`visual-generation-contract.md:208` 过时句已登记待契约轨回改）、`core/*`、`app/*`、`feature/*`、`tools/*`、历史批次记录。三份目标文件开工时均干净（`git status --porcelain` 为空），改动仅本轨内容（`git diff --stat` = 3 files, +20/−4）。

## 6. 未验证项

1. 80-C1/C2 设备侧/C3 设备半边/C4 四套设备结果：**本轨未重跑**（设备绿绑定源快照；零 Gradle/零设备），只做证据回链复核；如需现树复跑须按批次 34 判据重建重装 APK。
2. 80-C3 的 JVM 半边（12/0 + 17/0）：沿用批次 33 仓内命令日志（`step-jvm-80c3.md`），本批未重跑——口径保留不变。
3. `docs/research/evidence/2026-10-30-batch-35/` 在本文件写入前不存在（`ls` 实测），本文件是该目录首份证据。
4. 其余批次 35 任务（1–5、9–16）不在本轨范围，其执行状态见各轨记录与本批基线。
