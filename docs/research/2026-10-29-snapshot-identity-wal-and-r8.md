# 快照身份（WAL 面）与 R-04 `testProguardFiles` 核实（批次 34 R2，驱动 T5/T6）

> **性质**：研究/设计文档（本文件是本轮**唯一**写盘产物）。
> 本轮**零 Gradle、零 adb / am instrument、零 git 写操作、零仓内源码改动**。
> 文中 SQLite 行为结论全部来自**本会话实跑的探针**（Windows 10.0.26200 / Python 3.13.14 /
> `sqlite3` 模块的引擎 **3.50.4**，文件库，WAL 模式）与**本会话实跑的工具**（仓内
> `tools/split_golden/export_split_eval.py` 原样执行）；AGP 结论来自**本地 9.3.0 构件的
> `javap` 字节码**与**官方 DSL 参考 9.3 页面原文**。
>
> **口径**：`已落` = 当前树有代码（本文实读，引 `path:line`）；`未落` = grep 零命中；
> `建议` = 本文判断，T5/T6 落码时确认或否决；`实测` = 本会话跑过，命令与输出在 §8。
> **0 项在本轮转"通过"**；所有构建/设备结论一律 UNVERIFIED。
>
> **纪律沿用**：热区禁改；`docs/current` 其余 M 不动；不做 git 写；不跑 Gradle；
> 测试先红后绿、不放宽断言；新增必须指认消灭的失败；GitHub 只借形态不引依赖。

---

## 0. 结论速览

| # | 结论 | 它消灭的具体失败 | 证据等级 |
|---|---|---|---|
| **C1** | KD-81 用**仓内工具原样复现**：同一主库（sha `16c9db64…`）+ 不同 WAL → 两次导出 `exit=0`，一次 1 页 1 框、一次 1 页 2 框，而 `source.db.sha256` **逐字相同** | 报告自称"同一快照"，实际度量了两个不同状态；AUD-08 的"设备库无分题数据/门值 0.5714"这类结论因此不可复现 | 实测 audit3（§1.2） |
| **C2** | 现状不止"sha 不含 WAL"：`collect()` 的**两条语句各自 autocommit**，中间 WAL 变化会给出**混合状态**的导出（实测：同连接两次读 1 → 2） | 比"身份错"更坏的失败：一次导出内部不一致，任何 sha 都无法描述它 | 实测 P3 + 代码 `export_split_eval.py:167-174`、`:111-116` |
| **C3** | **`-shm` 绝不能进身份**：只读导出本身会**改写 `-shm`**（实测 sha `4b42c88d…` → `0a7d520d…`，主库与 `-wal` 不变），且干净关闭会删除它 | 把 `-shm` 纳入 sha 会让"同一次导出跑两遍"得到两个身份（自称不复现），并把"应用是否在跑"编码进度量身份 | 实测 P2b + 官方文档（§1.3） |
| **C4** | "导出前 `wal_checkpoint(TRUNCATE)` + 锁"作为**源库**方案被三项否决：① 需要写连接（违反现行只读红线与 `test_db_is_read_only` 的字节不变断言）；② TRUNCATE **不保证完成**——有读者持有读事务时返回 `(busy=1, log=1, checkpointed=0)` 且 WAL 仍 4152 B（实测 P5）；③ 即使在副本上成功，它也是**产物身份**不是**内容身份**（同内容、不同 `page_size`/`user_version` 的主库字节不同，实测 P9） | 采用它会同时引入"导出改动了被测物"和"忙等截断失败被当成成功"两种静默失败 | 实测 P5/P8/P9 + Litestream README 反证（§2.1） |
| **C5** | **推荐组合 = 读事务（一致性）+ 输入清单（db+wal 的 sha 进身份）+ 导出后比对（变化即拒）+ 载荷 sha（内容身份）**。四者各消灭一类失败，缺任一项都留有已知缺口 | 见 §2.4 的矩阵：缺读事务 → 混合导出；缺 WAL 进身份 → 同 sha 不同内容；缺后比对 → 导出期间漂移无感知；缺载荷 sha → 无法回答"两次度量是否同一输入" | 实测 P1–P9 + 代码 |
| **C6** | AGP 9.3 的 `testProguardFiles` 语义**核实成立**，官方原文两处："Adds proguard rule files to be used when processing test code. **Test code needs to be processed to apply the same obfuscation as was done to main code.**"；本地构件 `VariantDimension` 有 `getTestProguardFiles/testProguardFile/testProguardFiles`，且有 `ProguardFileType.TEST` 在 `OptimizationCreationConfigImpl$proguardFiles$2$1` 与 `CommonOptimizationDslInfoImpl` 的常量池里被引用 ⇒ **"可测 release 变体"不必以 `-dontobfuscate` 为代价** | 若信旧结论"必须加 `-dontobfuscate`"，会为了跑两例而把主包混淆面整个削掉 | 官方 9.3 页面原文 + `javap`（§5.1） |
| **C7** | R-04 在本仓的**具体阻塞点**已定位到两条（不是"R8 会改名"这一句笼统结论）：① `validateReleaseSigning` 由 `tasks.configureEach` 挂在**任何名字含 `Release` 的 assemble/package 任务**上（`app/build.gradle.kts:147-153`），`assembleStrictOfflineReleaseAndroidTest` 会连带要求四个 `RELEASE_*` 环境变量；② `testBuildType` 是**模块级全局**开关，直接改成 `release` 会把现有 debug 设备轨一起改向 | 不做①，实验第一步就红在"缺签名环境变量"，会被误归因为 R8；不做②，日常 14 套件设备轨被顺手改道 | `app/build.gradle.kts:147-153`、`:43-55`、baseline §六 |
| **C8** | R-04 的最小验证**不需要新 build type**：`testBuildType` 用 Gradle 属性门（默认 `debug`）保留双轨 + `release` 上加 `testProguardFiles`，产物级判据（同证书、测试类名前缀保留、`libquickjs.so` 在包、两例 `am instrument` 绿）四条全可机检 | 若改成"新增一个 copy 出来的 release 变体"，会动 `matchingFallbacks`/签名/备份清单等一串牵动面，收益不增 | 建议 + 命令（§5.4/§5.5），端到端 UNVERIFIED |

---

## 1. KD-81 事实闭包

### 1.1 工具现状（本会话实读）

| 事实 | 证据 |
|---|---|
| 身份只对**一个文件**的字节求 sha | `tools/split_golden/export_split_eval.py:72-73`（`_sha256(path)`） |
| 只读打开用 `mode=ro` URI，**无** `immutable`、**无** `-wal`/`-shm` 概念 | 同上 `:76-79` |
| `source` 块只有一个 `db`：`{"file","sha256","bytes"}` | 同上 `:218-228`（db 在 `:221`） |
| 主查询一次取全表（`fetchall`） | 同上 `:167-174` |
| 之后**逐页**再查 `batch_import_page`（第二条语句，独立读事务） | 同上 `:111-116`、调用点 `:199` |
| 输出在 `collect()` **之后**才写盘（写盘前还有一道 `out.exists()` 拒绝） | 同上 `:249-259` |
| stdout 断言"（只读打开，字节未动）" | 同上 `:265` —— 该断言**只对主库成立**，§1.2/§1.3 实测 `-shm` 会被改写 |
| 现有"只读"用例只哈希主库 | `tools/tests/test_split_gate.py:506-514`（`test_db_is_read_only`） |
| 已知限制已写进 README（检测命令，非代码） | `tools/split_golden/README.md:86-96` |
| KD-81 关闭条件写明"工具侧 manifest 覆盖 `-wal`/`-shm`（或 checkpoint 完成证据）+ 受控实验" | `docs/known-defects.md:3047-3078`（引用块 `:3078`） |
| 消费者只读 `source.db.sha256` | `tools/split_golden/measure_split_accuracy.py:404-414`（`_describe_app_source`）；`tools/split_golden/check_split_gate.py:315`、`:382` 打印 `dbSha256` |

### 1.2 受控实验：仓内工具原样复现（实测 audit3）

夹具：最小分题表四张；先 `PRAGMA wal_checkpoint(TRUNCATE)` 把**建表与 job/page 行**落进主库，
之后两题分两次插入（都留在 `-wal`），每次插入后拷贝三件。于是两份快照**主库字节完全相同**、
`-wal` 不同。

```
snap1.db 36864 B  snap1.db-wal  8272 B   (1 题)
snap2.db 36864 B  snap2.db-wal 16512 B   (2 题)
main 字节相同? True  16c9db64a60c62e8…
wal 字节相同? False
```

用仓内工具原样执行（无任何改动）：

```
python tools/split_golden/export_split_eval.py --db .../snap1.db --out .../out1.json
  → split 导出 OK：页 1 / 框 1；DB sha256=16c9db64a60c62e8fb1df2f43f778c9a6249294707a54f6c3ad0ab6ebef8a9a7
    EXIT_1=0
python tools/split_golden/export_split_eval.py --db .../snap2.db --out .../out2.json
  → split 导出 OK：页 1 / 框 2；DB sha256=16c9db64a60c62e8fb1df2f43f778c9a6249294707a54f6c3ad0ab6ebef8a9a7
    EXIT_2=0
```

结论：**KD-81 不是"理论风险"，是仓内工具当前行为**——两次导出报告同一 `source.db.sha256`，
输出 sha 不同（`a6208bec…` vs `4695a0d4…`），框数 1 vs 2。

补充实测（更极端的形态）：只拷主库、丢掉 WAL 时，工具以 `exit 2` 报"缺 split_import_job /
split_import_question 表"——因为连**建表语句**都还在 WAL 里（`no such table`）。
这说明"漏 WAL"在当前工具上不总是**静默**，但当 schema 已 checkpoint 过时就是静默丢行（audit3
的两份快照正是这种形态）。**一律按静默处理**才是安全假设。

### 1.3 SQLite 语义（官方文档 + 本会话探针，逐条对应）

| 编号 | 场景 | 实测结果 | 官方原文（`https://www.sqlite.org/wal.html`，本会话 WebFetch 实读） |
|---|---|---|---|
| **P1** | 连接仍开、两行已 COMMIT | 主库 4096 B（sha `5c9dec18…`）、`-wal` 12392 B、`-shm` 32768 B；`mode=ro` 读 `-wal` 得 2 行；只拷主库 → `no such table: t` | §4："The WAL file is part of the persistent state of the database and should be kept with the database if the database is copied or moved… transactions … might be lost, or the database file might become corrupted." |
| **P1b** | 干净关闭连接 | 主库 8192 B，`-wal`/`-shm` **消失** | §3.1/§6："when the last database connection … closes" 触发末次 checkpoint 并删 WAL 与 shm |
| **P2** | 快照只有 db+wal（**无** shm），`mode=ro` 打开 | **成功**，count=2；打开过程中**新建了 32768 B 的 `-shm`**；db/wal sha 不变 | §1/§5：只读打开 WAL 库需要 `-shm` 已存在**或目录可写**（22.0 起） |
| **P2b** | 快照三件齐全，`mode=ro` 导出一次 | 主库 sha 不变、`-wal` sha 不变、**`-shm` sha 变了**（`4b42c88d…` → `0a7d520d…`） | 同上（shm 是共享内存索引，不是持久状态） |
| **P3** | 同一 `mode=ro` 连接，**两条** SELECT（autocommit） | 第一次 1，追加一行后第二次 **2** ⇒ 两条语句跨了两个快照 | §2.2：读者的"end mark"决定它看到哪一段 WAL |
| **P4** | `BEGIN` 后两条 SELECT | 1 → **1**（快照被钉住）；默认 `isolation_level` 下同样成立 | 同上（读事务期间的 end mark 固定） |
| **P5** | `PRAGMA wal_checkpoint(TRUNCATE)` | 无读者：`(0,0,0)`，WAL **0 B**，主库 8192 B；有持读事务的读者：**`(1,1,0)`**，WAL 仍 **4152 B**；读者释放后：`(0,0,0)`，WAL 0 B | §3.2/§6：TRUNCATE 会"ensure the checkpoint runs to completion"，代价是可能阻塞读者；PASSIVE 则"might not run to completion" |
| **P6** | `immutable=1`（含 `mode=ro&immutable=1`） | **`no such table: t`** ⇒ 忽略 WAL，静默丢已提交行 | §5 把 `immutable` 列为"只读打开"的第三条路径 —— 但它是"忽略 WAL"的路径，不能用于快照导出 |
| **P7** | 同逻辑内容、不同历史（100 行 vs 200 行后删 100） | 行集相同；主库 **16384 B / freelist 0** vs **20480 B / freelist 1**，sha 不同 | —（文件布局是实现细节） |
| **P8** | 同一份三件快照拷两份，各自 rw 打开 + `wal_checkpoint(TRUNCATE)` | 两份都 `(0,0,0)`、2 行、主库 sha **相同**（`97418956…`）；再加一行后 sha 变 | —（同产物 → 同结果，可复现） |
| **P9** | 同 100 行内容，`page_size=4096` vs `8192` vs `user_version=7` | 三者行集相同，主库 sha **全不同**（`7c75ceca…` / `0ed49843…` / `39a4d6d3…`） | — |
| **P7b** | P7 的同内容两库**重新构造并各自 `VACUUM`**（关闭连接触发 checkpoint） | 两份**归一到同一 sha**（`7c75ceca…`、16384 B、freelist 0）。注意：连 P7 的 `fresh` 库 VACUUM 后也换了 sha（`a142e93e…` → `7c75ceca…`）⇒ VACUUM 不是字节无操作 | — |

**由 P7/P8/P9 得到的分层结论（本文最重要的口径）**：

- **产物身份（artifact identity）**：给同一份文件集，得同一摘要；换 WAL 就换摘要。
  只有"输入清单"（db+wal 的 sha）能表达它。
- **内容身份（content identity）**：给同一**被度量的内容**，得同一摘要；与物理布局、
  `page_size`、`user_version`、是否 checkpoint、`VACUUM` 无关（P9/P7 vs P7b 证明文件字节做不到）。
  只有"载荷摘要"（导出文档本身）能表达它。
- KD-81 的原文要求（"同主库 + 不同 WAL 必得不同身份/可区分导出"）落在**产物身份**上；
  但复跑度量真正需要的是**内容身份**。两者都要记，不能互相顶替。

---

## 2. 三种方案与各自失败面

### 2.1 方案 A：导出前 `wal_checkpoint(TRUNCATE)` + 锁

**形态**：打开（必须可写）→ 拿锁 → `PRAGMA wal_checkpoint(TRUNCATE)` → 校验返回值 → 求 sha → 导出。

**失败面（逐条有证据）**：

| # | 失败 | 证据 |
|---|---|---|
| A1 | **违反只读红线**：checkpoint 要写主库与 `-wal`。现行工具的 `mode=ro` 与用例 `test_db_is_read_only`（`tools/tests/test_split_gate.py:506-514`）都建立在"输入字节不变"上；checkpoint 会改写主库 | 代码 `:76-79`、用例 `:506-514` |
| A2 | **在真设备库上根本做不到**：设备库由 app 进程持有，宿主只有一份**拷贝**；对拷贝 checkpoint 只说明"拷贝被整理过"，对"拷贝是否完整"无任何证明 | 逻辑 + 实测 P1（WAL 是独立文件，宿主只能拷文件） |
| A3 | **TRUNCATE 不保证完成**：有读者时返回 `(1,1,0)` 且 WAL 未截断（P5）。若实现只看"没抛异常"，失败被静默吞掉 | 实测 P5；官方 §3.2/§6 |
| A4 | **锁挡不住"拷贝本身"**：真正危险的窗口是"设备还在写时拷三件"，锁在宿主端对这个窗口无意义 | 逻辑 + P1 |
| A5 | **它改变被测物**：Litestream 的 README 明确记了同型代价——它为了协调 checkpoint 会在**源库建表**，并写明"its creation changes the source database schema and can change its page count or bytes, so a database under Litestream should not be expected to remain byte-identical" | `benbjohnson/litestream` README（`gh api …/readme` 实读） |
| A6 | **即使成功也不是内容身份**：P9（同内容不同 `page_size`/`user_version` → 不同字节）；P7 vs P7b（不 VACUUM 不同字节、VACUUM 才归一） | 实测 P7/P7b/P9 |

**唯一可取处**：在**副本**上 checkpoint 能把三件归一成"单文件 + 空 WAL"，便于下游只吃一个文件（P8）。
所以它只能作为**可选的下游整形步骤**，不能充当身份与一致性机制。

### 2.2 方案 B：把 `-wal`/`-shm` 字节纳入 sha

**形态**：`sha256(db) + sha256(db-wal) + sha256(db-shm)` 进一个综合摘要。

**失败面**：

| # | 失败 | 证据 |
|---|---|---|
| B1 | **`-shm` 参与即不可复现**：只读导出会改写 `-shm`（P2b），于是"导出"这个动作本身改变了身份；同一输入跑两遍得两个身份 | 实测 P2b |
| B2 | **`-shm` 存在性随连接生命周期翻转**：干净关闭后 `-shm`/`-wal` 消失（P1b），下次打开又出现（P2） | 实测 P1b/P2 |
| B3 | **`-wal` 字节是历史函数，不是内容函数**：同内容可对应不同 WAL（audit3 的两份快照主库相同、WAL 不同；P5 同一逻辑库在"刚写入"与"刚 checkpoint"下 WAL 分别 214272 B 与 0 B） | 实测 audit3/P5 |
| B4 | **仍不解决混合导出**：文件摘要与"连接读到的快照"没有绑定关系；autocommit 下两条语句可以跨快照（P3），而摘要只描述其中某一份 | 实测 P3 + 代码 `:167-174`、`:111-116` |
| B5 | **`-wal` 可能在导出期间被外部进程改写**：摘要必须前后各取一次并比对，否则"摘要描述的是哪一时刻"无定义 | 见 §2.3 |

**结论**：B 若只做"`db`+`wal` 进摘要、`shm` 只记存在性"，可以正确消灭 KD-81 的原始症状；
但**单独使用仍是错的**——它给出的是产物身份，配上 autocommit 就是"用错身份描述一份混合数据"。
必须与"读事务 + 前后比对 + 载荷摘要"同批落地。

### 2.3 方案 C：导出后二次校验一致性

**形态**：导出前后各取一次输入清单，相等才写盘；或导出后再读一遍做差分。

**失败面**：

| # | 失败 | 证据 |
|---|---|---|
| C1 | **只检测不识别**：它能说"变了"，不能给"这是哪一份"；若不带 B，同一 sha 仍对应不同内容 | 逻辑 |
| C2 | **比对字段选错就恒真/恒假**：比对 `-shm` → 每次都不等（P2b）；比对 mtime → 拷贝工具的 mtime 语义各不相同 → 抖动；只比对主库 → 原始缺口不变（WAL 变了主库不动） | 实测 P2b + P1 |
| C3 | **"变回原样"不可检测**：前后 sha 相同但中间态不同（理论上可构造），C 单独使用不保证"导出期间只有一份状态" | 逻辑（P3 说明混合可发生） |
| C4 | **写盘顺序**：必须在**写 `--out` 之前**完成校验，否则已落盘的坏导出无法撤回 | 代码 `:249-259`（当前顺序天然满足，改法不得打乱） |

**结论**：C 是**守卫**不是**身份**。它的价值在于把"导出期间漂移"从静默变成 `exit 2`。

### 2.4 方案矩阵与推荐组合

| 失败 | A 源库 checkpoint | B wal/shm 进 sha | C 后比对 | **推荐组合** |
|---|---|---|---|---|
| 同主库不同 WAL 同 sha（KD-81 症状） | 依赖 A3 成功，不可靠 | ✅（db+wal） | ❌ | ✅ 输入清单（db+wal） |
| 导出内部混合状态 | ❌ | ❌ | ❌ | ✅ 读事务（`BEGIN`） |
| 导出期间输入漂移 | ❌（锁不在拷贝上） | ❌ | ✅ | ✅ 前后清单比对 → `exit 2` |
| 同内容不同物理布局的"身份不可比" | ❌ | ❌ | ❌ | ✅ 载荷 sha |
| 自指破坏（导出改变身份） | — | ❌（shm） | — | ✅ shm 不进摘要 |
| 下游只吃单文件 | ✅（副本上） | — | — | 可选，不进主路径 |

**推荐落地顺序**：C5 的四件**同一批**（T5/T6 一次改完）。理由：它们互相提供定义（读事务定义
"哪一份"、清单定义"哪一输入"、后比对定义"是否漂移"、载荷摘要定义"内容是什么"），
拆开落地会出现"用身份描述混合数据"的中间态。

---

## 3. `export_split_eval.py` 最小改法（给 T5/T6 的草案）

> 原则：**不改 CLI 必填参数、不改退出码语义（仍是 0/2）**、不动 `--out` 存在性拒绝的既有行为、
> 不动 SQL 语义；只加输入清单、读事务、后比对、载荷摘要四件。目标文件当前**干净**
> （`git status --porcelain -- tools/split_golden/export_split_eval.py` 无输出，本会话实测）。

### 3.1 改动点（逐处，附行号）

**① 新增常量与 canon 工具（放在 `:60-69` 附近）**

```python
IDENTITY_KIND = "inputs-v1"          # 显式版本化，未来改口径必须换值


def _canonical_json(value) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":")).encode("utf-8")


def _sidecar_paths(db_path: Path) -> dict[str, Path]:
    return {"db": db_path,
            "wal": db_path.with_name(db_path.name + "-wal"),
            "shm": db_path.with_name(db_path.name + "-shm")}


def _input_manifest(db_path: Path) -> dict:
    """三件输入的存在性/字节/摘要；mtime 只作溯源，**不进**身份摘要。"""
    entries: dict[str, dict | None] = {}
    digest_input: dict[str, str | None] = {}
    for role, path in _sidecar_paths(db_path).items():
        if path.is_file():
            stat = path.stat()
            sha = _sha256(path)
            entries[role] = {"file": path.name, "bytes": stat.st_size,
                             "sha256": sha, "mtimeNs": stat.st_mtime_ns}
            digest_input[role] = sha if role in ("db", "wal") else None
        else:
            entries[role] = None
            digest_input[role] = None
    identity = hashlib.sha256(_canonical_json(digest_input)).hexdigest()
    return {"identityKind": IDENTITY_KIND,
            "identitySha256": identity,
            "inputs": entries,
            "walPresent": entries["wal"] is not None}
```

**为什么 `shm` 的 sha 不入摘要**：§2.2 B1/B2（导出会改写它、关闭会删它）。`shm` 的
`sha256/bytes/mtimeNs` 仍落进 `inputs.shm` 作溯源，但它是"观察值"不是"身份分量"。

**② `_connect_read_only`（`:76-79`）不变**，但 `collect()` 内改为读事务：

```python
    conn = _connect_read_only(db_path)
    try:
        conn.isolation_level = None          # 关掉隐式事务，显式 BEGIN
        conn.execute("BEGIN")                # 钉住一个 WAL 快照（P4）
        ...                                   # 主查询与逐页查询全部在同一事务内
        conn.execute("COMMIT")
    finally:
        conn.close()
```

- 必须是**显式** `BEGIN`：Python 的默认 `isolation_level` 只在 DML 前隐式开事务，SELECT 不会
  （实测 P3/P4 与默认隔离级别下 `BEGIN` 均通过）。
- 这条同时消灭 C2 的"混合导出"。**它是本次改动里唯一会改变读出行为的一项**，必须在文档
  与 stdout 里如实写明。

**③ `collect()` 前后取清单，先改后比（`collect()` 入口 `:145-147` 附近与返回处 `:218` 前）**

```python
def collect(db_path, page_map_path):
    if not db_path.is_file():
        raise DataError(f"DB 快照不存在：{db_path}")
    before = _input_manifest(db_path)        # 打开连接之前
    ...                                      # 原有查询与页名解析
    after = _input_manifest(db_path)         # 关闭连接之后
    if (before["inputs"]["db"]["sha256"] if before["inputs"]["db"] else None,
        before["inputs"]["wal"]["sha256"] if before["inputs"]["wal"] else None) != \
       (after["inputs"]["db"]["sha256"] if after["inputs"]["db"] else None,
        after["inputs"]["wal"]["sha256"] if after["inputs"]["wal"] else None):
        raise DataError(
            "快照一致性：导出期间 db/-wal 发生变化（同一份导出不可能对应两个状态）；"
            "请在应用停止写入后重新取三件快照")
    ...
    return {... "source": {"db": ..., "snapshot": before, "payload": ...}}
```

- 比对只取 `db`/`wal` 的 `sha256`（**不取 mtime、不取 shm**），否则会因 §2.3 C2 恒假。
- 位置必须在 `collect()` 内、`main()` 写盘之前（`main()` 写盘在 `:257-259`），保持 C4 的顺序不变式。
- 报错文案带固定前缀 `快照一致性`，便于 grep 与 `exit 2` 的唯一归因。

**④ `source` 块扩两节（`:218-228`）**

```python
    payload_digest = hashlib.sha256(_canonical_json(pages)).hexdigest()
    return {
        "schemaVersion": REPORT_SCHEMA_VERSION,
        "source": {
            "db": {"file": db_path.name, "sha256": _sha256(db_path),
                   "bytes": db_path.stat().st_size},          # 保留：主库字节摘要（旧口径）
            "snapshot": before,                                # 新增：产物身份
            "payload": {"sha256": payload_digest,              # 新增：内容身份
                        "pages": len(pages),
                        "boxes": sum(p["questionCount"] for p in pages)},
            "pageMap": page_map_meta,
            "queries": QUERIES_DOCUMENTATION,
            "nameSourceCounts": _count_name_sources(pages),
        },
        "pages": pages,
    }
```

**⑤ stdout（`:262-266`）如实改写**

```
  DB sha256=<db>（主库字节摘要；不等于快照身份）
  快照身份 sha256=<snapshot.identitySha256>（inputs-v1：db+wal；shm 只记存在性）
  载荷 sha256=<payload.sha256>（页 N / 框 M；内容身份）
  WAL 存在；导出期间 db/-wal 字节未变（-shm 由 SQLite 重建，不计入身份）
```

并在 WAL 不存在时打印 `WAL 不存在（该快照可能已 checkpoint 或取自关闭的应用）`——
**这是如实告知，不是错误**：工具无法证明"曾经有 WAL 被漏拷"（这一点必须留在 README 里）。

**⑥ `QUERIES_DOCUMENTATION`（`:63-69`）**

追加一句读事务说明即可（SQL 文本不变）：

```python
    " ; BEGIN IMMEDIATE-less read transaction: BEGIN … COMMIT around all SELECTs"
```

实际上应写成注释式说明而非 SQL，避免被下游当 SQL 执行；建议新增独立键
`"readTransaction": "BEGIN … COMMIT (single WAL snapshot)"` 而不是塞进 `queries`。

### 3.2 身份定义（写进 docstring 与 README）

```
产物身份 identityKind = "inputs-v1"
  identitySha256 = sha256( canon({ "db": sha256(db) | null,
                                   "wal": sha256(wal) | null }) )
  —— 覆盖：文件集存在性 + 主库/ WAL 字节；不覆盖：shm、mtime、路径名
内容身份
  payload.sha256 = sha256( canon(pages 列表) )
  —— 覆盖：被度量的每一页每一框；不覆盖：DB 物理布局、来源块、页名解析来源
```

两条身份的关系（写进 README，防止下一位读者再用一条顶替另一条）：

- 产物身份不同而内容身份相同 → **合法**（同一内容的不同物理快照），度量可复跑；
- 产物身份相同而内容身份不同 → **不允许**（本次改动要消灭的就是它；`exit 2` 也不允许出现，
  因为后比对已经把"同一身份两份内容"变成"检测到漂移"）；
- 内容身份相同 → 度量数值必然相同（与门槛判定无关）。

### 3.3 退出码与兼容性

| 项 | 处理 | 理由 |
|---|---|---|
| 退出码 | 仍只有 0/2；一致性拒绝也走 2 | `check_split_gate.py`/测试只认 0/2（`tools/tests/test_split_gate.py:474-514`、`:587-600`），新增码会扩大牵动面且无收益 |
| `source.db.sha256` | **保留原值与原位置** | `measure_split_accuracy.py:404-414` 读它落 `dbSha256`；`check_split_gate.py:315`/`:382` 打印它。改成快照身份会**静默改变报告语义** |
| 新增字段 | 只加不改 | 旧导出文件仍可被 measure 消费（`source.get("db")` 容错） |
| `--sidecar` | 不变（仍是输出文件 sha） | 旁车语义是"这份导出文件"的 sha，本来就正确 |

**牵动面清单（改一处先枚举）**

| 落点 | 要改 | 漏掉会怎样 |
|---|---|---|
| `export_split_eval.py` `:72-79`、`:145-228`、`:238-271` | 四件 | 见 §2.4 矩阵 |
| `tools/split_golden/README.md:69-96` | 把"已知限制"改为"已由 manifest 覆盖"，保留"三件同一时刻"的操作要求 | 文档与工具漂移，下一位读者以为仍需手工检测 |
| `tools/split_golden/GATE.md` | 不强制改；若需引用身份，加"身份口径"一节 | 门说明与报告身份脱节（可选） |
| `tools/tests/test_split_gate.py:474-514` | 新增用例（§4）；**不改** `test_db_is_read_only` 的断言 | 放宽旧断言 = 掩盖红 |
| `measure_split_accuracy.py:404-414` | 可选：把 `snapshotSha256`/`payloadSha256` 一并落报告 | 报告仍只有 db sha；不阻断（建议同批 2 行） |
| `check_split_gate.py:315`/`:382` | 可选：打印身份两行 | 同上 |
| `docs/current/visual-generation-contract.md:208`（AUD-08 段） | 状态句回改（"未覆盖"→"已覆盖，见 …"） | 契约仍写 KD-81 未修（归属文档轨，非本轨） |

> **注意**：`tools/split_golden/GATE.md`、`check_split_gate.py`、`measure_split_accuracy.py`
> 当前均为 `M`（他线在飞，本会话 `git status --porcelain` 实测）。T5 若必须改它们，
> 按纪律**整文件纳入并注明**或**停项登记**；`export_split_eval.py` 与 `test_split_gate.py`
> 的 `M` 状态需在开工时复核（本会话时 `test_split_gate.py` 为 `M`）。

---

## 4. 回归测试形态（先红后绿）

### 4.1 用例表

| # | 用例 | 做法 | 红灯（改前）期望 | 绿灯（改后）期望 |
|---|---|---|---|---|
| **T1** | `test_same_main_db_different_wal_yields_different_identity` | 夹具：建表 + job/page **checkpoint 进主库**；插入第 1 题后拷三件 → `snapA`；插入第 2 题后拷三件 → `snapB`（两份主库字节相同） | 改前无 `source.snapshot` 键 → **KeyError（红）**。**不要**把旧行为（`db.sha256` 相同）写成断言——那会把缺陷锁进用例；旧行为只用于证明"这条用例本来是红的" | `snapshot.identitySha256` 不同；`payload.sha256` 不同；`db.sha256` 相同（**保留这条断言**，用来证明旧字段确实不是身份） |
| **T2** | `test_wal_sidecar_is_recorded_even_when_shm_absent` | 只拷 `db`+`wal`（无 shm） | `source["snapshot"]` KeyError → 红 | `snapshot.inputs.wal.file/sha256/bytes` 有值、`inputs.shm is None`、`walPresent is True`；导出成功 |
| **T3** | `test_absent_wal_is_recorded_as_absent` | 已 checkpoint 的库（无 wal 文件） | 同上 → 红 | `inputs.wal is None`、`walPresent is False`；导出成功 |
| **T4** | `test_export_rejects_input_change_during_export` | 猴子补丁：把模块级 `_input_manifest` 包一层，**第 2 次调用（关闭连接后）前**用另一个连接向 WAL 插一行（脚本化计数，不依赖线程时序）；**保持**真实前后比对 | 无拒绝路径 → `exit 0` 并写出文件（红） | `exit 2`、stdout 含 `快照一致性`；`--out` **未被创建**（顺序断言，C4） |
| **T5** | `test_reads_are_pinned_to_one_snapshot` | **另测钉住**（不与 T4 混）：把 `_input_manifest` 打成常量（关掉后比对），再在 `_resolve_page_name` 的**首次调用**（主查询之后、仍在事务内）用另一个连接插一行 | 导出**含**新行（红：P3 的 autocommit 跨快照行为） | 导出**不含**新行且 `exit 0`（读事务钉住）；这条价值在于把"一致性"与"检测"分开证明 |
| **T5b** | `test_export_rejects_wal_change_inside_transaction` | 与 T4 同，但插入点改在**事务内**（`_resolve_page_name` 首次调用）：后比对仍必须拒绝 | 红（同上） | `exit 2`；说明"钉住"与"漂移检测"同时成立也不冲突 |
| **T6** | `test_payload_identity_is_physical_layout_independent` | 用 P9 的构造：同 100 行、`page_size` 4096 vs 8192（保留 WAL 形态相同） | 无 `payload` 段 → 红 | 两份 `snapshot.identitySha256` **不同**、`payload.sha256` **相同** |
| **T7** | `test_export_does_not_rewrite_main_db_or_wal` | 导出前后对 `db`/`wal` 取 sha | 已通过（保留） | 仍通过（**守住**：只允许 `-shm` 变） |
| **T8** | `test_shm_is_not_part_of_identity` | 同一快照连导两次，第二次前把 `-shm` 删掉（模拟干净关闭） | 无身份段 → 红 | 两次 `snapshot.identitySha256` 相同（shm 不进摘要）；第二次仍成功（P2：缺 shm 会自动重建） |
| **T9** | `test_determinism_unchanged` | 现有 `test_export_is_deterministic_and_consumable_by_measure` 原样保留 | 通过 | 通过（**不放宽**：仍要求逐字节相同） |

### 4.2 夹具构造（最小，沿用现有 `make_split_db`/`seed_split_db` 形态）

关键结构：**一个写连接**，在两个插入点各拷一次三件 ⇒ 两份快照**主库字节相同、WAL 不同**。
不要写成"每次调用重建一个库"——那样主库字节不保证相同，T1 的前提就没了。

```python
def make_wal_snapshot_pair(tmp: Path) -> tuple[Path, Path]:
    """主库含 schema+job/page（已 checkpoint）；两份快照各捕获 1 题 / 2 题。"""
    live = tmp / "live.db"
    conn = sqlite3.connect(live, isolation_level=None)      # 全程不关，WAL 才留得住
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA wal_autocheckpoint=0")
    # … 建四表 + insert batch_import_job/page + split_import_job（同 :76-102 的列形状）
    conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")        # 结构落进主库

    def snapshot(tag: str) -> Path:
        dst = tmp / f"snap{tag}.db"
        shutil.copyfile(live, dst)
        shutil.copyfile(str(live) + "-wal", str(dst) + "-wal")
        shutil.copyfile(str(live) + "-shm", str(dst) + "-shm")
        return dst

    conn.execute("INSERT INTO split_import_question VALUES (?,?,?,?,?,?,?,?,?,?)",
                 ("split:B1:0", 0, 0, 0.05, 0.05, 0.9, 0.4, 1, "PENDING", None))
    snap_one = snapshot("one")
    conn.execute("INSERT INTO split_import_question VALUES (?,?,?,?,?,?,?,?,?,?)",
                 ("split:B1:0", 1, 0, 0.05, 0.5, 0.9, 0.95, 1, "CONFIRMED", None))
    snap_two = snapshot("two")
    conn.close()
    return snap_one, snap_two
```

**关键点**：

- 拷贝必须在**写连接仍存活**时做（P1b：关连接会 checkpoint 并删除 WAL）。若在 `conn.close()`
  之后拷贝，会得到"没有 WAL"的库，T1/T2 会**假绿**。
- `PRAGMA wal_checkpoint(TRUNCATE)` 必须在**第一次插入之前**执行：这样两份快照的主库都含
  完整 schema（否则"只拷主库"会以缺表 `exit 2` 失败，见 §1.2 补充实测）。
- 返回前**不要**对 `live.db` 再做 checkpoint（会把 WAL 清空，第二份快照就与第一份同形）。

### 4.3 不许动的东西

- `test_export_is_deterministic_and_consumable_by_measure`（`tools/tests/test_split_gate.py:474`）
  的逐字节相等断言：**不得**因为新增字段而放宽——新字段本身是确定性的（无时间戳）。
- `test_db_is_read_only`（`:506`）：断言主体是主库，**保留**；新增 T7 覆盖 `-wal`，
  但**不要**把该用例改成"三件都不变"——`-shm` 会被创建/改写（P2/P2b），改成三件会得到
  一个**必然红且不该绿**的断言。
- `--out` 已存在即拒绝（`:250-251`）：不动。

---

## 5. R-04：AGP 9.3 `testProguardFiles` 语义与最小验证

### 5.1 语义核实（官方原文 + 本地构件）

| 事实 | 证据（本会话实读） |
|---|---|
| AGP **9.3** 官方 DSL 参考原文（方法详情，非摘要表）："Adds proguard rule files to be used when processing test code. **Test code needs to be processed to apply the same obfuscation as was done to main code.** This method has a return value for legacy reasons." | `https://developer.android.google.cn/reference/tools/gradle-api/9.3/com/android/build/api/dsl/VariantDimension?hl=en`（`curl -sSL` 实取 2,795,889 B；文本由本地正则提取） |
| 属性形态原文："The collection of proguard rule files to be used when processing test code. **Test code needs to be processed to apply the same obfuscation as was done to main code.**" | 同上（属性 `testProguardFiles`） |
| 摘要表里只有短句"…used when processing test code." | 同上（首次 grep 只命中摘要行；**长句在方法详情里**，易误判为"文档没写"。批次 29/30 引用的长句**成立**，本轮复核确认，非新增） |
| 本地 AGP 9.3.0 构件确有该 API 与消费点 | `javap -p` 于 `gradle-api-9.3.0.jar`：`getTestProguardFiles()`、`testProguardFile(Object)`、`testProguardFiles(Object...)`；`gradle-9.3.0.jar` 有 `ProguardFileType.TEST`，且 `OptimizationCreationConfigImpl$proguardFiles$2$1` / `CommonOptimizationDslInfoImpl` 引用 `gatherProguardFiles` |
| `testBuildType` 语义原文："Specifies the build type that the plugin should use to test the module." | `…/9.3/com/android/build/api/dsl/TestedExtension?hl=en`（本会话 `curl` 实取） |
| `ApplicationExtension extends … TestedExtension`（即 app 模块可用 `testBuildType`） | `javap ApplicationExtension`（本会话） |
| 该长句在 7.4/8.1/8.7 的同一页面**也存在** | 本会话对四个版本页面 `grep -o "same obfuscation" \| wc -l` 均为 **3**（9.3 / 8.7 / 8.1 / 7.4）⇒ 非 9.x 新增语义 |

> **一处必须更正的旧说法**：批次 29 R3 把 `testProguardFiles` 的语义标为"官方（文档站点镜像）"。
> 本轮确认该原文在 `developer.android.google.cn` 的 **9.3** 页面里逐字存在，故可升为
> **官方一手（同站点）**；但 `developer.android.com` 与 `issuetracker.google.com` 本轮
> 仍然**连接超时**（`WebFetch` 443 timeout），实时主站与 AGP issue #126429384 状态**未复核**。

### 5.2 本仓现状（开工事实）

| 事实 | 证据 |
|---|---|
| `testProguardFiles` 零命中、`testBuildType` 零命中 | baseline §六；本会话复核 `app/build.gradle.kts` 全文无这两个词 |
| release/benchmark/internal 三者均 `isMinifyEnabled = true` | `app/build.gradle.kts:57-86`（`proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`） |
| `validateReleaseSigning` 被挂在**任何**含 `Release` 的 `assemble*/package*/bundle*` 上 | `app/build.gradle.kts:147-153`：`if (name.contains("Release", true) && (name.startsWith("bundle") || name.startsWith("assemble") || name.startsWith("package"))) dependsOn("validateReleaseSigning")` |
| 四个 `RELEASE_*` 环境变量缺失时 `validateReleaseSigning` **抛** `GradleException` | 同上 `:130-145` |
| flavor 维度 `networkMode`：`strictOffline` (`.offline`) / `localFirst` (`.localfirst`) | `app/build.gradle.kts:43-55` |
| AIDL 开关显式打开（AIDL 全在 app 模块） | 同上 `:88-96` |
| 现有 release APK 产物路径 | `app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk`（本会话 `find` 实读） |
| androidTest APK 产物路径（debug） | `app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` ⇒ release 形态预期 `…/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk` |
| R-04 判据原文 | `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:232`："release（R8）变体 APK 上跑 `VisualJsIsolationInstrumentedTest` 至少'往返 + watchdog'两例，且 `unzip -l <release.apk> \| grep libquickjs.so` 非空" |
| 两例方法名 | `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt:116`（`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`）、`:191`（`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`） |

**由此得到的两条"实验前必须先解决"的阻塞（不是 R8 本身）**：

1. `assembleStrictOfflineReleaseAndroidTest` 名字含 `Release` ⇒ 会 `dependsOn("validateReleaseSigning")`
   ⇒ **必须有四个 `RELEASE_*` 环境变量**（一次性本地 keystore 即可，批次 24/26 已有先例：
   "一次性本地 key"）。否则第一步就失败，且失败信息与 R8 无关，极易被误归因。
2. `testBuildType` 是**模块级**；直接写死 `"release"` 会让现有 `assembleStrictOfflineDebugAndroidTest`
   （设备 14 套件基线）一起改向。必须用属性门并存。

### 5.3 最小配置草案（不引 `-dontobfuscate`）

```kotlin
// app/build.gradle.kts（当前干净；若开工后变 M 则按纪律整文件纳入或停项）
val r04TestBuildType = providers.gradleProperty("smbk.testBuildType").orNull ?: "debug"

android {
    testBuildType = r04TestBuildType          // 默认 debug：日常设备轨零变化
    buildTypes {
        getByName("release") {
            // 仅在 testBuildType=release 的 androidTest 构建里生效；
            // 规则只作用于 test code（官方语义），不削弱主包混淆面。
            testProguardFiles("r04-test-keep.pro")
        }
    }
}
```

`app/r04-test-keep.pro` 起步形态（按实验收敛，不是最终值）：

```proguard
# JUnit/Instrumentation 按类名反射加载测试类 ⇒ 测试类名不可改。
-keep class com.tingyun.smartmistakebook.** {
    <init>(...);
    @org.junit.Test <methods>;
}
```

- **为什么不是 `-dontobfuscate`**：官方语义明确是"test code 被处理以套用主代码同一混淆"；
  `-dontobfuscate` 会让 test 包不再套用主包 mapping，等于把实验目标（R8 改名下能否往返）取消。
- **为什么规则只 keep 测试入口**：被测的是**主包**的混淆结果；主包混淆面由 `proguard-rules.pro`
  决定，本文件的 keep 只作用于 test dex。
- **必须补的负向断言（防"实验变成自欺"）**：`app/proguard-rules.pro` 与 `r04-test-keep.pro`
  都不得出现 `-dontobfuscate`；且 release 的 `mapping.txt` 必须非空并含真实改名（见 §5.5 K4）。

### 5.4 可执行命令（由门禁脚本串行执行；本轮未跑）

```bash
# 0) 一次性本地 keystore（仅本机；口令从环境变量取，不写进仓/文档/测试）
#    build/ 已被 .gitignore:15 的 **/build/ 忽略（本会话 `git check-ignore -v` 实测）
: "${R04_STOREPASS:?先 export R04_STOREPASS=<本机一次性口令，随机生成，勿提交>}"
keytool -genkeypair -v -keystore build/r04-local.jks -alias r04local \
  -keyalg RSA -keysize 2048 -validity 3650 \
  -storepass "$R04_STOREPASS" -keypass "$R04_STOREPASS" \
  -dname "CN=R04 Local, OU=Local, O=Local, L=Local, S=Local, C=CN"
export RELEASE_STORE_FILE=build/r04-local.jks
export RELEASE_STORE_PASSWORD="$R04_STOREPASS"
export RELEASE_KEY_ALIAS=r04local
export RELEASE_KEY_PASSWORD="$R04_STOREPASS"

# 1) 红基线（现状：testBuildType=debug，装 release 主包 + debug test 包）
./gradlew :app:assembleStrictOfflineRelease :app:assembleStrictOfflineDebugAndroidTest --console=plain
adb -s emulator-5562 install -r -t app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
adb -s emulator-5562 install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk
adb -s emulator-5562 shell am instrument -w \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# 期望：失败（批次 27 记录的 `NoClassDefFoundError: Lkotlin/jvm/internal/Intrinsics;` 形态）
# 若第 2 步 install 就报 "does not have a signature matching the target"，同样是红基线的一种形态。

# 2) 改后构建（属性门打开）
./gradlew :app:assembleStrictOfflineReleaseAndroidTest \
  -Psmbk.testBuildType=release --console=plain
# 期望：BUILD SUCCESSFUL，且同时产出
#   app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
#   app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk

# 3) 产物级判据（不需要设备）
unzip -l app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk | grep libquickjs.so
apksigner verify --print-certs app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
apksigner verify --print-certs app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk
# 两份证书 SHA-256 必须相同
"$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" dex packages \
  app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk \
  | grep -c "VisualJsIsolationInstrumentedTest"
grep -c "dontobfuscate" app/proguard-rules.pro app/r04-test-keep.pro     # 期望 0 0
find app/build/outputs/mapping -name "mapping.txt" -path "*release*" -exec grep -c " -> " {} \;

# 4) 设备两例
adb -s emulator-5562 install -r -t app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk
adb -s emulator-5562 install -r -t app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk
adb -s emulator-5562 shell am instrument -w \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater,com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest#theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5562 logcat -d | grep -cE "NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError"   # 期望 0

# 5) 回归（证明没把日常轨改道）
./gradlew :app:assembleStrictOfflineDebugAndroidTest --console=plain   # 不带属性 → 仍 debug
```

### 5.5 判据（K1–K6）

| # | 判据 | 期望 | 失败归因 |
|---|---|---|---|
| K1 | 步骤 1 红基线**确实红**，且红因是 R8 改名/签名门（不是缺环境变量） | 红 | 若因 `validateReleaseSigning` 缺变量 → 环境问题，先修 §5.2 阻塞 1 |
| K2 | 步骤 2 `BUILD SUCCESSFUL` 且两个 release 产物都在 | 绿 | 先看 `testProguardFiles` 是否被属性门包住 |
| K3 | 两份 APK 证书 SHA-256 **相同** | 绿 | 不同 ⇒ 需显式给 androidTest 指定 `signingConfig`（见 §7 U4） |
| K4 | `grep -c dontobfuscate` 两个文件都 = 0，且 release `mapping.txt` 有真实改名行 | 绿 | 有 `-dontobfuscate` ⇒ 实验无效，不得判 R-04 |
| K5 | `unzip -l` 含 `libquickjs.so`；测试类名在 androidTest dex 中**未被改名** | 绿 | dex 里查不到 ⇒ keep 规则不足，扩到 `<init>` 与 `@Test` 方法之外 |
| K6 | 两例 `am instrument` `OK (2 tests)`，且 logcat 三类签名计数 0 | 绿 | 仍是 `NoClassDefFoundError` ⇒ 在本仓组合下该路线**不成立**，按"阻塞"回报，不得改写判据 |

> **判据不得放宽**：R-04 的原文要求是"release（R8）变体 APK 上跑两例"。若为了变绿把判据改成
> "internal 变体"（§3.2 b2 的形态），属于**判据改口径**，必须回到主会话裁定（批次 29 R3 §3.3(c)）。

### 5.6 与"不引 `-dontobfuscate`"的关系（一句话）

`testProguardFiles` 的官方语义就是"test code 被处理以套用主代码同一混淆"，**前提是主代码被混淆**；
`-dontobfuscate` 会取消这个前提。因此本路线与 `-dontobfuscate` **互斥**，不需要也不允许引入。

### 5.7 未验证项（R-04 半边）

- 端到端（构建 + 上设备）**UNVERIFIED**：本轮禁 Gradle/adb。
- `testProguardFiles` 在**本仓组合**（app 模块 + AIDL + 两个 flavor + release minify +
  `testBuildType=release`）下是否真能让 androidTest dex 套用主包 mapping：**UNVERIFIED**。
  静态证据只到"API 存在 + `ProguardFileType.TEST` 在优化配置的常量池里被引用"。
- androidTest APK 是否**默认继承** tested variant 的签名：字节码里
  `AndroidTestComponentDslInfoImpl$signingConfigResolver$2` 会用 tested variant 的 dslInfo +
  test buildType 解析（本会话 `javap -c` 实读），但**"继承"是推断**；K3 实测决定。
- `r04-test-keep.pro` 的最小规则集：未验证；K5 的红/绿决定扩不扩。

---

## 6. GitHub 参考评分

> 通道：`gh api repos/<owner>/<repo>`（license/stars/pushed_at 为本会话实测）；
> 文本证据为本会话 `gh api …/contents` / `readme` / `WebFetch` 实读。
> **只借形态，不引任何依赖**（本仓已有等价实现：内容寻址、命中行数 CAS、两段式裁剪）。

| 仓 | license（实测） | stars | 最近 push | 借哪一点（原文） | 在本仓落地 | 评分（license/维护/可复用） |
|---|---|---|---|---|---|---|
| [sqlite/sqlite](https://github.com/sqlite/sqlite) | GitHub 返回 `NOASSERTION`；官方声明 public domain（`https://www.sqlite.org/copyright.html`，前批已实读） | 10,854 | 2026-10-29 | WAL §4："The WAL file is part of the persistent state … should be kept with the database if the database is copied or moved"；§5 只读打开三条路径（shm 存在/目录可写/`immutable`）；§3.2/§6 TRUNCATE 语义 | §2 三种方案判定的**第一性依据**；P2/P5/P6 的官方对应 | 5 / 5 / 5 |
| [benbjohnson/litestream](https://github.com/benbjohnson/litestream) | Apache-2.0 | 14,549 | 2026-10-28 | README："Litestream only communicates with SQLite through the SQLite API so it will not corrupt your database"；为协调 checkpoint 在源库建 `_litestream_lock` 表，"Its creation changes the source database schema and can change its page count or bytes, so a database under Litestream should not be expected to remain byte-identical" | §2.1 A5 的**反证**：源库内 checkpoint+锁 ≠ 字节不变身份；同时支持"经 SQLite API 读，不裸拷主库"的方向 | 5 / 5 / 4 |
| [restic/restic](https://github.com/restic/restic) | BSD-2-Clause | 36,871 | 2026-10-18 | `doc/design.rst`：快照是"a JSON document … The filename is the storage ID. This string is unique and used within restic to uniquely identify a snapshot"；"A snapshot references a tree by the SHA-256 hash of the JSON string representation of its contents" | §3.2：身份建在**规范化 JSON**（= 载荷摘要）上，而不是物理文件字节；两条身份分离 | 5 / 5 / 4 |
| [jrodbx/agp-sources](https://github.com/jrodbx/agp-sources) | **`null`（GitHub 未识别到许可证）** | 138 | 2026-03-11 | AGP 源码按版本镜像：本会话读到 `9.1.0` 目录（`…/gradle/internal/dsl` 下 152 个文件）；递归树 `truncated=true`，**未定位到** `PostProcessingBlock`，且镜像最新 9.1.0 **落后本仓 9.3.0** | §5 的旁读材料；**license 不明 + 版本落后 ⇒ 不得复制代码，也不得据它断定 9.3 行为** | 1 / 2 / 1 |
| [android/gradle-recipes](https://github.com/android/gradle-recipes) | **`null`（根目录无 LICENSE 文件）** | 2,372 | 2026-10-22 | 官方 AGP DSL 配方集（默认分支 `agp-9.0`，tree 1106 条；`proguard` 命中 **6** 条，均为普通 `proguard-rules.pro` 与 `CheckMultiDexKeepProguardTask`，**无** `testProguardFiles`/`testBuildType` 配方） | 仅作"官方配方无此项"的负证据（**路径级**，未做全库正文检索） | 1 / 5 / 1 |

**取舍**：本轮四条结论（WAL 是持久状态、只读打开需要 shm、TRUNCATE 会 busy、身份应建在规范化
载荷上）**全部可由官方文档 + 本地探针自证**，不需要引入任何库。两个 AGP 镜像的 license 为
`null`，按纪律**只读不抄**。

---

## 7. 未验证项与边界（如实）

| # | 项 | 状态 |
|---|---|---|
| U1 | 本轮**零 Gradle、零仪器化、零设备**：§5 的所有命令**未执行**，R-04 的构建/设备结论全部 UNVERIFIED | 未跑 |
| U2 | SQLite 探针是**桌面 Python 3.13 / 3.50.4 / Windows 文件系统**，不是 Android 设备上的 SQLite（版本不同）。WAL/shm/checkpoint 语义是标准行为，但**设备侧未实测** | 待设备轮（若 T5/T6 落 WAL 用例，建议同时跑一次设备侧同型探针） |
| U3 | `mode=ro` 在**只读目录/只读介质**（无法创建 `-shm`）上的行为未实测（Windows ACL 无法干净模拟）；官方 §5 说满足三条之一即可读，P2 只覆盖了"目录可写"一条 | 未验证 |
| U4 | androidTest APK 是否默认继承 release 签名：字节码推断 + K3 待实测 | 未验证 |
| U5 | 导出期间**设备仍在写**时的"撕裂快照"（拷到半个 WAL 帧）无法由工具检出；SQLite 会因帧校验失败回退到更早的提交 → **静默回退到较旧状态**。这是操作纪律问题（README 的"三件同一时刻/停止写入再取"），不是本次代码能消灭的失败 | 明确不修，保留登记 |
| U6 | `developer.android.com` 与 `issuetracker.google.com` 本轮仍 443 超时；AGP issue #126429384 的当前状态未复核 | 未验证 |
| U7 | 「同内容 → `VACUUM` 后字节归一」只在**单一样本**成立（P7b），不能推广成"VACUUM 可得内容哈希"；P9 已给出反例（`page_size`/`user_version` 不同） | 样本有限 |
| U8 | 本会话未审 `tools/split_golden/GATE.md`、`check_split_gate.py`、`measure_split_accuracy.py` 的他线在飞改动内容（三者 `M`）；§3.3 的牵动面只按 `HEAD` 版语义判断 | 需 T5 开工复核 |

---

## 8. 来源与可重跑命令

### 8.1 仓内（本会话实读，`path:line`）

- 导出器：`tools/split_golden/export_split_eval.py:60-69,72-79,96-123,145-228,238-271`
- 导出器测试：`tools/tests/test_split_gate.py:76-124`（夹具）、`:474-514`（确定性/只读）、`:587-600`（诚实失败）
- 消费者：`tools/split_golden/measure_split_accuracy.py:404-414`；`tools/split_golden/check_split_gate.py:298-320,375-390`
- 操作记录：`tools/split_golden/README.md:69-96`；`docs/known-defects.md:3047-3078`
- 构建：`app/build.gradle.kts:43-55,57-86,88-96,130-145,147-153`；`gradle/libs.versions.toml:2`（`agp = "9.3.0"`）
- R-04 判据：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:232`；`docs/research/2026-10-28-lock-window-and-batch30-prep.md:140-260`
- 两例方法：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt:116,191`
- 基线：`docs/research/2026-10-29-batch-34-baseline.md`（§六 R-04；§八 与计划不符处）
- 计划：`.zcode/plans/plan-2026-10-29-batch-34-dual-backend-kd80.md` §1 任务 3/6、§2 硬约束

### 8.2 外部（本会话实跑）

- `https://www.sqlite.org/wal.html`（WebFetch 实读；§1 只读/§2 WAL/§2.1 checkpoint/§3.1 自动/§3.2 TRUNCATE/§4 持久状态/§5 只读/§6 截断）
- `https://developer.android.google.cn/reference/tools/gradle-api/9.3/com/android/build/api/dsl/VariantDimension?hl=en`（`curl -sSL`，含 `testProguardFiles` 长句原文）
- `https://developer.android.google.cn/reference/tools/gradle-api/9.3/com/android/build/api/dsl/TestedExtension?hl=en`（`testBuildType`）
- 同一页面的 8.7 / 8.1 / 7.4 版本（对照"长句非 9.x 新增"）
- `gh api repos/{sqlite/sqlite,benbjohnson/litestream,restic/restic,jrodbx/agp-sources,android/gradle-recipes} --jq '{license,stars,pushed_at,archived}'`
- `gh api repos/benbjohnson/litestream/readme`（`_litestream_lock` 与"not expected to remain byte-identical"）
- `gh api repos/restic/restic/contents/doc/design.rst`（快照身份 / 树的 SHA-256）
- `gh search code "testProguardFiles" --limit 8`（返回的 8 条**全部**是 `jrodbx/agp-sources` 镜像文件；未做全量判定，不能据此断言"社区无此用法"）
- 本地构件：`javap -p` / `javap -c` 于 `gradle-api-9.3.0.jar`、`gradle-9.3.0.jar`（`VariantDimension`、`TestedExtension`、`ApplicationExtension`、`ProguardFileType`、`OptimizationCreationConfigImpl`、`AndroidTestComponentDslInfoImpl$signingConfigResolver$2`）

### 8.3 本轮实跑命令（可重跑）

```bash
# 探针（Python 3.13.14 / sqlite3 3.50.4 / Windows）
python - <<'PY'   # P1  WAL 可见性 + 快照三件 + 只拷主库
PY
python - <<'PY'   # P2 缺 shm 只读打开；P2b 导出改写 shm；P3 autocommit 跨快照；
                  # P4 BEGIN 钉快照；P5 checkpoint 返回码；P6 immutable 忽略 WAL
PY
python - <<'PY'   # P7 同内容不同布局；P8 副本 checkpoint 可复现；P8b 加行必变
PY
python - <<'PY'   # P7b VACUUM 归一；P9 page_size/user_version 反例
PY

# 仓内工具原样复现（audit3）
python tools/split_golden/export_split_eval.py --db <snap1.db> --out <out1.json>
python tools/split_golden/export_split_eval.py --db <snap2.db> --out <out2.json>

# 文件状态
git status --porcelain -- tools/split_golden/export_split_eval.py app/build.gradle.kts \
  docs/research/2026-10-29-snapshot-identity-wal-and-r8.md
find app/build/outputs/apk -name "*.apk"

# AGP
JAR=$(cygpath -m ~/.gradle/caches/modules-2/files-2.1/com.android.tools.build/gradle-api/9.3.0/*/gradle-api-9.3.0.jar)
javap -p -classpath "$JAR" com.android.build.api.dsl.VariantDimension | grep -i proguard
javap -classpath "$JAR" com.android.build.api.dsl.TestedExtension

# 官方文档
curl -sSL --max-time 30 "https://developer.android.google.cn/reference/tools/gradle-api/9.3/com/android/build/api/dsl/VariantDimension?hl=en" -o vd.html
```

> 探针脚本未落仓（`/tmp` 临时目录；其中 P1–P9 的语义与输出已逐条抄在 §1.3 / §2，结论不依赖
> 脚本文件存在）。T5/T6 重跑时按上表场景逐条构造即可。

---

## 9. 给 T5/T6 的落地清单（一页）

1. **T5（工具）**：`export_split_eval.py` 按 §3.1 ①②③④⑤ 改；新增 `tools/tests/test_split_gate.py`
   的 T1–T9（含 T5b，§4.1）；`README.md:86-96` 回改；`measure/gate` 的两处身份打印**可选**同批。
2. **T5（先红）**：先落 T1/T2/T4/T5/T5b 的断言并跑红（现状必红：无 `source.snapshot` 键 /
   同 sha 同内容不成立），再落实现转绿。**不许**改 `test_export_is_deterministic…` 与
   `test_db_is_read_only` 的既有断言。
3. **T6（R-04）**：先核 `app/build.gradle.kts` 是否 `M`（开工时）——`M` 则停项登记不改；
   否则按 §5.3 落属性门 + `r04-test-keep.pro`，按 §5.4 跑步骤 1–5，按 §5.5 K1–K6 判。
4. **共同红线**：本轮四件（读事务/清单/后比对/载荷摘要）**同批**；R-04 不许引 `-dontobfuscate`；
   不许把判据从 release 改成 internal；`source.db.sha256` 语义**不得**被悄悄改成快照身份。
5. **回报口径**：KD-81 关闭需要"工具改 + 受控实验"两件（`docs/known-defects.md:3078`）；
   受控实验即 T1（同主库不同 WAL → 身份不同）+ T4/T5b（漂移必拒）。
   本轮只提供设计与判据，**不关闭 KD-81**。
