# 批次 34 / T7 文档：台账刷新与命令记录（2026-10-29）

> **性质**：只读核对 + 文档改动。**零 Gradle、零设备执行、零 git 写操作**。所有“已落码”只表示现树存在代码与用例，执行结果另行判定。
> **快照时点**：2026-10-29 18:14（+0800）；HEAD `db792fa8`；工作树 `git status --porcelain | wc -l` = **720**（开工基线 689，含本批新增文件与他线在飞改动）。
> **本文件是 T7 文档轨自己的命令日志**，不是设备/门禁脚本的转录。

---

## 1. 设备证据边界（KD-80 裁决前提）

```text
$ ls docs/research/evidence/2026-10-29-batch-34
ls: cannot access 'docs/research/evidence/2026-10-29-batch-34': No such file or directory
$ ls -d build/device-track-batch34
ls: cannot access 'build/device-track-batch34': No such file or directory
$ for f in build/device-track-batch33/step01-backup-final.log \
           build/device-track-batch33/step03-trimmer-final.log \
           build/device-track-batch33/step04-c4-visual.log; do
    echo "$f rowCounts=$(grep -c rowCounts $f) tutor_visual=$(grep -c tutor_visual $f) $(grep -E 'OK \(' $f | tail -1)"
  done
build/device-track-batch33/step01-backup-final.log rowCounts=0 tutor_visual=0 OK (34 tests)
build/device-track-batch33/step03-trimmer-final.log rowCounts=0 tutor_visual=0 OK (9 tests)
build/device-track-batch33/step04-c4-visual.log rowCounts=0 tutor_visual=0 OK (5 tests)
```

**结论**：截至本时点没有任何批次 34 设备执行产物；KD-80 的 80-C2 设备侧“90 键逐名”在**已落盘日志里仍是 0 命中**。批次 34 新增用例 `BackupRowCountsCoverageInstrumentedTest.kt` 只是**落码**，未上设备。

> 注：上面的 `ls` 在本文件写入**之前**执行；本目录现在只有本文件（T7 文档记录），**没有任何设备证据**。

---

## 2. 80-C2 静态半边（实跑，S=71）

命令（仓库根，只读；与 `docs/research/2026-10-29-batch-34-device-criteria.md` §1.4 相同）：

```bash
S=71 python - <<'PY'
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
echo "EXIT=$?"
```

本轮输出（逐行）：

```text
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

`71.json` 事实现读：

```text
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

**效力边界**：这只证明静态清单一致；**不**证明设备侧 rowCounts 逐名。KD-80 的 80-C2 仍为 PARTIAL。

---

## 3. KD-81 工具侧回归（实跑）

```bash
$ python -m pytest tools/tests/test_split_gate.py -q
....................................                                     [100%]
36 passed in 1.74s
```

与 KD-81 直接相关的用例（`tools/tests/test_split_gate.py`）：

| 行 | 用例 | 钉住什么 |
|---:|---|---|
| 669 | `test_same_main_db_different_wal_yields_different_identity` | 同主库 + 不同 WAL → 身份不同（受控实验） |
| 701 | `test_same_wal_snapshot_exported_twice_is_byte_identical` | 同一快照两次导出逐字节一致 |
| 721 | `test_absent_wal_is_recorded_as_absent` | 无 WAL 如实记录 |
| 735 | `test_empty_wal_sidecar_from_own_read_is_not_drift` | 只读打开自造的 0 B `-wal` 不判漂移 |
| 765 | `test_export_rejects_input_change_during_export` | 导出期间 db/-wal 变化 → 拒绝 |
| 789 | `test_reads_are_pinned_to_one_snapshot` | 读事务钉住一个 WAL 快照 |

工具侧实现锚点（`tools/split_golden/export_split_eval.py`）：`_sidecar_paths` `:101-104`（db + `-wal`，`-shm` 显式排除）、`_input_manifest` `:107-135`（身份 = `sha256(canon({"db": …, "wal": …}))`，`identityKind = "inputs-v1"`）、前后清单比对 `:284-294`、读事务 `:222`。

**效力边界**：工具侧原始缺陷已消灭；关闭条件中的“`-shm` 覆盖”字面项与“AUD-08 设备库复跑”未满足 → KD-81 保持 open（详见 KD-81 Progress）。

---

## 4. A-01 / A-02 / A-03 落码锚点（只读 grep）

```text
$ grep -n "private val visualEngine\|private val visualFrameInputs\|FigureGenerationMethod.JS -> generateLocalJs\|private suspend fun generateLocalJs\|state.beginLocalAttempt" \
    core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/DefaultFigureGenerationCoordinator.kt
153:    private val visualEngine: VisualExecutionEngine? = null,
158:    private val visualFrameInputs: VisualFrameInputs? = null,
211:            FigureGenerationMethod.JS -> generateLocalJs(request, proposal)
248:    private suspend fun generateLocalJs(
290:            state.beginLocalAttempt(FigureGenerationMethod.JS, sourceSha)

$ grep -n "FigureGenerationMethod.JS ->" core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolLoop.kt
355:                FigureGenerationMethod.JS -> {

$ grep -n "val jsAdvertised\|figureMethods: Set" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelProtocol.kt
130: figureMethods ... (:129-130 默认 setOf(IMAGE_MODEL))
162: figureMethods ...
243: figureMethods ...
378: figureMethods ...
388:        val jsAdvertised = figureGeneration &&
```

版本面（未变）：

```text
core/database/.../StudyDatabase.kt:139  internal const val STUDY_DATABASE_VERSION = 71
core/model/.../ModelTasks.kt:546        const val TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18
```

广告面（未变，JS 仍不广告）：

```text
core/model/.../FigureGeneration.kt:160-167   IMAGE_MODEL_ONLY（availableMethods 仅 IMAGE_MODEL）
core/data/.../FigurePromptAssembler.kt:40     require(JS !in capabilities.availableMethods)
core/data/.../FigurePromptAssembler.kt:108    同上
```

---

## 5. 本轨改动文件（只 docs）

- `docs/known-defects.md`：KD-80 末尾新增批次 34 T7 逐条裁决块；KD-81 末尾新增工具侧修复 + 回归实跑块。
- `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`：头注新增 v3 补记 4；A-01/A-02/A-03 三行现状/证据位置/验收用例刷新；新增附录 H（契约缺失项登记）。
- 本文件。

**未改**：`docs/current/*`（热区）、`core/*`、`app/*`、`feature/*`、`settings.gradle.kts`、`.gitignore`、`tools/*`。未运行 Gradle、未安装/执行设备用例、未做任何 git 写操作。

---

## 6. 未验证项

1. A-01/A-02/A-03 的 JVM 用例执行结果：**UNVERIFIED**（本轨零 Gradle）。
2. KD-80 的 `BackupRowCountsCoverageInstrumentedTest`（2 例）设备结果：**UNVERIFIED**（无设备产物）。
3. KD-80 的 80-C1/C3/C4 本批复跑：**UNVERIFIED**（只有批次 33 旧源绿轮）。
4. KD-81 的 AUD-08 设备库复跑与 `-shm` 口径：**UNVERIFIED / 待裁定**。
5. 工作树在文档写入期间仍有他线在飞改动；本文件的所有源码锚点以 **18:14 快照**为准。

---

## 7. 回填（2026-10-29 设备轨落盘后；本文件 §1/§6 的“本批无设备证据”已被取代）

> 本节由复核修复轮追加，**不改写 §1/§6 的历史快照**：它们只对 18:14 时点成立。

设备轨 18:42–19:02 已落盘 `docs/research/evidence/2026-10-29-batch-34/`（11 份）与
`build/device-track-batch34/`（15 套件）。对 §6 逐项回填：

1. **A-01/A-02/A-03 的 JVM**：本批仍**未执行**（设备轨只 assemble*）；现树存在
   `build/` XML 旁证（`FigureGenerationCoordinatorTest` 17/0、`TutorFigureMethodDualParseTest`
   14/0、`TutorToolRoundGateTest` 16/0 等，mtime 18:33–18:37，此后无 `.kt` 改动），
   但**无仓内命令日志** → 仍记 `UNVERIFIED`（不得由 build/ 旁证读成门通过）。
2. **`BackupRowCountsCoverageInstrumentedTest`**：设备实跑 **`OK (2 tests)`**
   （`build/device-track-batch34/step02-rowcounts.log:7/:14/:21/:28/:34`，两条方法名均在
   未过滤的 `-r` 流中）。→ §6 第 2 项作废。
3. **80-C1/C3/C4 本批复跑**：E-02 已取到 `OK (34)` / `OK (9)` / `OK (5)`。→ §6 第 3 项作废。
4. **KD-81 设备复跑**：仍未做（本批设备轨不含 AUD-08 快照）；`-shm` 口径仍待裁定。
5. **KD-80 裁决**：80-C2 设备侧逐名已取到，四条在同一批 APK 上全满足 →
   `docs/known-defects.md` 的 KD-80 已由“保持 open”回改为 **closed（2026-10-29 批次 34）**。
   唯一口径保留：80-C3 的 JVM 沿用批次 33 仓内命令日志，本批未重跑。
