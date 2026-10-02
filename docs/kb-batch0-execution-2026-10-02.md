# 批次 0 执行报告（2026-10-02）

> 范围：知识库批次 0「零裁定小修」九条（规格见 `docs/kb-outstanding-research-2026-10-02.md`「批次 0」节，:680-703）。
> 本报告由「执行报告撰写」线整理，素材 = 五条实施线的施工回报、独立复核（只读）的裁定与 findings、编排脚本的终局门记录与范围核对结果、基线资料 `build/agent-outstanding/group-*.md`。
> 本批纪律：共享工作树、不切分支、不 amend/rebase、不 push；改动全部留在工作树未提交（HEAD=`3d234d32`）。

**一句话状态**：九条全部施工完成，复核裁定 8 条 done + 1 条 partial；终局门 `run_kb_checks` / Python 套件 / `overfull_nodes` 全为 0，JVM 全量退出码 1（基线 0；撰写时复跑为 0、未复现失败，见第三节）；CI 真正回绿必须等推送后由 CI 验证——**本批不提交、不推送**。

**本报告撰写时的复跑（2026-10-02 20:2x–20:4x，只读命令；除注明外均在本工作树实跑）**：

| 复跑命令 | 结果 |
|---|---|
| `python tools/ci/run_kb_checks.py` | 退出码 0（gates / consistency / roundtrip / manifest / dense 5 节全 OK） |
| `PYTHONPATH=tools python -m unittest discover -s tools/tests -t tools` | `Ran 612 tests in 130.309s` / OK / 退出码 0 |
| `PYTHONPATH=tools python -m kb_build.overfull_nodes` | 退出码 0 |
| `PYTHONPATH=tools python tools/kb_build/gate.py` | 「全部通过：缺陷归零」/ 退出码 0 |
| `./gradlew.bat testDebugUnitTest --continue --console=plain --rerun` | BUILD SUCCESSFUL in 1m 26s / 退出码 0（详见第三节） |

---

## 一、逐条执行记录（1–9）

### 条目 1 · `overfull_nodes.py` 修好 + 自述重写（实施线：内容管线工具）

**落法（选择理由与代价）**
- `load()` 改走 `pack_io.pack_path()` + `pack_io.sidecar_paths()`（索引驱动枚举，与 Kotlin loader 读同一份索引）。理由：旧 glob 会把卷索引（只有 `packId`/`sidecars` 两个键）当材料卷读入并 `KeyError: 'materials'`，是该工具跑不通的唯一根因；用仓库既有惯例解决，不另造枚举。代价：枚举从此依赖包索引在场/格式。
- docstring 与输出按实测重写（3,866 点 / >4 2,518 / ≥4 2,754 / =4 236 / 挂 47,538 / 超出前 4 的绑定 37,466 / 最大 139）；机制描述改为运行时代码事实（无每节点条数门；1,024 是整批取数上界；展示由 20,000 字符预算按 `roleRank→typePriority→title→materialId` 逐条装填、装不下跳过）。理由：旧文案「第 5 条起永远进不了预算」与运行时相反、会诱导错裁决。

**改动文件**：`tools/kb_build/overfull_nodes.py`（改）、`tools/tests/test_kb_overfull_nodes.py`（新增）。

**验证命令与实测结果**
- `PYTHONPATH=tools python -m kb_build.overfull_nodes` → exit=0；首行「2,518 个超配节点（同一节点 > 4 条材料）；它们挂着 47,538 条材料，其中超出 37,466 条是节点内排序第 5 位及以后的绑定。」第 3 行「……落在 3,866 个节点上……节点内 ≥4 条共 2,754 个（其中 =4 条 236 个）」；样本首节点「139 条 [BIOLOGY] 生物技术与工程 / 基因工程」。
- 修复前同形态复现失败：exit=1、`KeyError: 'materials'`（旧代码 `overfull_nodes.py:75`；复核者用 `git show HEAD:… | python -` 独立复现）。
- `PYTHONPATH=tools python -m unittest discover -s tools/tests -t tools -p "test_kb_overfull_nodes.py"` → `Ran 3 tests` / OK / exit 0（合成夹具含「诱饵卷不读」断言）。
- 复核者：机制描述逐条核对 Kotlin 实码（`RoomTutorTeachingReferenceRepository.kt` 的 1,024 取数上界与 20,000 字符预算、排序键、无每节点条数门）。
- 本报告撰写时复跑：同上命令 exit 0，输出含上述全部数字（另有 2-gram 分诊行：零 2-gram 重合 5,449 条，其中超配段 4,242 条标为优先复核）。

**遗留**
- 施工清单未写共享 staging：staging 里现存的 `overfull_nodes.csv` 是 2026-09-14 的旧产物（7,102 行），按写入窗口纪律未覆盖；行数=47,538 是在隔离副本里验证的，实际落 staging 属批次 1。
- `build.py` 与 `promote.py` docstring 的「22 门」不在本条范围，已由条目 8 一并改为 23（见条目 8）。

### 条目 2 · `materialize.py` 白名单收紧 3 值 + type 门（实施线：入库白名单与协议）

**落法（选择理由与代价）**
- `TYPES` 直接硬收紧到 3 值（与 `tools/kb_coverage/merge_text_judgments.py:44-45` 逐字一致，机检 byte-equal）。选择理由：先实测收紧后的存量后果——内存 patch 后跑 plan 全表 86,414 行，错误 0，旧 type 仅剩 482 条 SKIP 行——存量没有任何行需要容忍；容忍清单不消灭现存失败，反而把刚关闭的 7 值旁路重开一条。代价：此后任何 MATERIAL 旧 type 行整批拒写（这正是门要的效果，且上游 merge 同白名单先拦）。
- `plan()` 非法 type 报错文案补上允许值列表（`sorted(TYPES)` 拼入，不改变判定逻辑），让新门首次触发即可诊断。
- 协议文档 `docs/kb-chunk-judgment-protocol.md` :95-101 段落整体重写为 2026-10-02 复测口径（MATERIAL 全 3 值、旧 type 剩 482 行全在 SKIP、698 行已由 P0.3 移入重判队列、包内 667 条在包不在表、处置以 §F7 为准）。
- 测试文件加 `_missing_inputs()` + 类级 skipIf：CI checkout 缺块池（未入库产物）时显式 skip 并写明缺席路径与理由，在场照常断言；新增回归用例（白名单=3 值且与 merge 逐字相等；4 种旧值 MATERIAL 行硬拒、SKIP 行不拒）。

**改动文件**：`tools/kb_coverage/materialize.py`、`docs/kb-chunk-judgment-protocol.md`、`tools/tests/test_kb_materialize.py`。

**验证命令与实测结果**
- `PYTHONPATH=tools python -m kb_coverage.materialize --dry-run` → 「判定 86414 行：材料 38929，跳过 47485，错误 0」，EXIT=0。
- 收紧前内存实测（存量后果）：应用 3 值后跑 `plan(load_judgments())` → judgments 86,414、errors 0；旧 type 行 482 全为 SKIP（REPRESENTATION_GUIDE 363 / DERIVATION 104 / WORKED_EXAMPLE 14 / COMPLETE_SOLUTION 1）。
- 历史存量重放实测：`git show 7315ac09^:tools/kb_coverage/tables/material_judgments.csv` 16,625 行过收紧后 plan() → 710 错误 = 698 非法 type（519/150/25/4）+ 12 节点不存在；698 行已先由 P0.3 移入重判队列，是硬收紧安全的前提（对当前表 = 0）。
- 667 归属实测：判定表 MATERIAL 旧 type=0；19 卷 sidecar 50,383 条材料中旧 type 667（491/150/22/4）——在包、不在表；materialize 不读包，不经此门重放。
- `PYTHONPATH=tools python -m unittest discover -s tools/tests -t tools -p "test_kb_materialize.py"` → `Ran 9 tests` / OK / EXIT=0；CI 同形（不设 PYTHONPATH）复跑同样 9 OK；缺席模拟（把 `pool_path.POOL_PATH` 指向不存在路径）→ ran 9 / skipped 7 / failures 0 / errors 0，skip 文案含缺席路径。
- 相关回归：`PYTHONPATH=tools python -m unittest tests.test_kb_apply_rejudged_materials tests.test_kb_extraction_state tests.test_kb_merge_text_judgments tests.test_kb_restore_rebind_protected` → `Ran 26 tests` / OK / EXIT=0。
- 逐字一致机检：`materialize.py` 与 `merge_text_judgments.py` 的 `TYPES = {...}` 行 byte-equal（脚本比较 True）；另确认 `check_slice_verdicts.py:31`、`new_content.py:43`、`reconcile_judgments.py:48` 同白名单。

**遗留**
- 与预期不符（如实报告）：「约 667 条旧 type 材料所在的 MATERIAL 行」在判定表上实为 0——667 是包内 sidecar 材料数；旧 type 的 MATERIAL 行曾有 698 条，但已由 P0.3（commit 7315ac09）先移入重判队列。历史的「会报错」版本重放数=698。
- 包内 667 条旧 type 材料未动：处置属 F7 裁定 + 重判闭环（`make_judgment_slices` → 判定 → merge → `apply_rejudged_materials`），不在本条目范围。
- 未改 `apply_rejudged_materials.py` / `apply_authored_materials.py`（不在允许文件范围，且今天不可达：表内 0 旧 type MATERIAL、上游 merge/authored 校验拦截）。
- 只模拟了「块池缺席」一种缺产物场景；guard 覆盖的另两个输入（包/状态表）缺席路径未逐一模拟。
- 协议文档其余带明确历史日期的段落未动（属历史记录）。
- 复核 finding（note，F4）：干净检出上该文件 7/9 例整类 skip（含本次新增两条白名单行为回归），CI 里「收紧 3 值」仅剩常量比较、无行为断言；建议用 `mock.patch(load_chunks)` 注入合成块池。**未修**（owner：入库白名单与协议）。

### 条目 3 · `scan_duplicate_nodes.py` 产品化（实施线：内容管线工具）

**落法（选择理由与代价）**
- 拆纯函数（`norm` / `group_nodes` / `same_groups` / `prefix_pairs`）+ I/O 层（`nodes_of` / `material_counts` / `scan` / `main`），加 `--out` / `--json` 与 9 列 CSV。理由：产品化要能被测试钉住判据，也要能被下游直接消费 CSV/JSON 而不是解析打印文本。
- 材料计数从 slug 单键改 `(subject, slug)` 键控（9 个跨科同名 slug 不再串数）；先跑两种口径对比确认 11/18 基线不变才改。
- 人类报告展示顺序由「包遍历序」改为确定性排序（组大小降序 → 科目 → 关键词）；仅显示次序、计数与内容不变，全仓无下游解析该顺序。

**改动文件**：`tools/kb_build/scan_duplicate_nodes.py`（改）、`tools/tests/test_kb_scan_duplicate_nodes.py`（新增）。

**验证命令与实测结果**
- `python tools/kb_build/scan_duplicate_nodes.py`（无 PYTHONPATH 直跑）→ exit=0、首行同为 11 组；runpy 形态 → 「规范化完全同名组：11 组」「前缀包含对：18 对（前 8）」，exit=0。
- `PYTHONPATH=tools python -m kb_build.scan_duplicate_nodes --json` 管道解析 → same=11 / prefix_pairs=18；隔离目录跑 `--out` → CSV 59 行（same 22 + prefix 37），报告尾「（59 行；same 11 组 / prefix 18 对）」。
- `PYTHONPATH=tools python -m unittest discover -s tools/tests -t tools -p "test_kb_scan_duplicate_nodes.py"` → `Ran 9 tests` / OK / exit 0（含真实包基线 11 组/18 对）。
- 复核者：直跑 exit 0；跨科同名 slug 确为 9 个（`(subject, slug)` 键控必要）；全仓无代码调用者（只有文档引用），显示排序改动无下游。

**遗留**
- 两个真实数据基线测试与包内容耦合：包内容一变数字就会变红，需按测试内注释先复算再更新（已核对 `build/kb-staging` 与成品目录同源，主包 sha256 均为 `647675cd98f52ca8`；干净检出从成品种子可复现同样的 11/18）。
- 显示顺序改动仅影响人类报告排版，无下游解析（复核确认）。

### 条目 4 · `build.py --write` 封口 + 三处误导提示（实施线：内容管线工具）

**落法（选择理由与代价）**
- `build.py --write` 默认拒绝落盘（exit 2 + 明说会整体重写 staging），必须加 `--i-know-this-rewrites-staging`。理由：该生成器已停用而代码里曾无任何守卫，一次误跑会用历史拓扑覆盖其他手术工具在 staging 的成果。
- 三处「先跑 build --write」提示改为走 `pack_io.work_dir()`（缺包时按仓库惯例自动从成品目录种子）并重写守卫文案；顺带修 `wusan_plan.py` 的同一类卷索引 glob（此前实跑 `KeyError: 'materials'` 崩，修后 exit 0），否则该文件提示修完工具仍不可用。

**改动文件**：`tools/kb_build/build.py`、`tools/kb_build/regroup_aggregates.py`、`tools/kb_build/resolve_points.py`、`tools/kb_build/wusan_plan.py`。

**验证命令与实测结果**
- `PYTHONPATH=tools python -m kb_build.build --write` → exit=2，打印拒绝原因与 `--i-know-this-rewrites-staging` 指路；隔离副本里 `--write --i-know-this-rewrites-staging` → 越过守卫进入 `Builder()`（随后撞既有 `NewContentError`，见遗留）。
- 三个提示位在空目录 fixture（`use_directory` 到 build/ 下 tmp）下实调 loader → 均 `SystemExit`，文案为「…work_dir()（默认 build/kb-staging，缺包时从成品目录种子）…不要跑 kb_build.build…」，不再出现「先跑 …build --write」。
- `wusan_plan` 修复前 exit=1（`KeyError: 'materials'`，`wusan_plan.py:70`），修复后 exit=0（「规范章 160 个…」）；`resolve_points` 前后均 exit=0；`regroup_aggregates` 前后均 exit=1（既有数据态核对拒绝，与本次改动无关）。
- `PYTHONPATH=tools python tools/kb_build/gate.py` → 「全部通过：缺陷归零」exit=0；`python tools/ci/run_kb_checks.py` → 5 节全 OK（本报告撰写时复跑同样 exit 0）。
- 复核者补跑：`test_kb_build_generator` 9 tests OK；三处提示位实读已改（行号随编辑位移：`regroup_aggregates.py:274` / `resolve_points.py:66` / `wusan_plan.py:62`）。

**遗留**
- `build.py` 的写盘路径只验证到「危险旗标放行 → 进入 `Builder()`」：隔离副本里随后撞上既有的 `NewContentError`（`new_points.csv`: BIOLOGY 已有同名 slug '生态金字塔'；另见 `tools/kb_build/apply_boundary_fixes.py:11` 记载「全量生成链当前跑不起来」），因此没有跑成一次完整落盘；且按纪律未对真实 staging 执行写路径。
- `wusan_plan` 的改动超出「修提示」字面范围：还把它读材料卷的旧 glob 换成 `pack_io.sidecar_paths()`（同条目 1 的失败类）；实施者已在回报中声明，若不希望可按此条回退该 3 行。
- 复核 finding（note，F7）：封口后全仓仍存在 `build --write` 的推荐/调用残留——`core/data/src/test/.../knowledge/StagingPackRetrievalBenchmarkTest.kt:46`（tracked，`assumeTrue` 文案教人跑该命令，现 exit 2）、`tools/kb_build/check_materials.py:2`（docstring 仍以它为流程锚点）、两个 gitignored 工作流脚本（`53-scan-bulk.ts:228`、`check.ts:600`）。⑥-5 的验收「全仓 grep 不到推荐用法」因此未完全达成（三处指名提示已修）。**未修**（owner：内容管线工具）。

### 条目 5 · 学生侧两小修（实施线：学生侧两小修）· 复核裁定 **partial**

**落法（选择理由与代价）**
- 5a：`masteryUpdate` 在 `resolve()` 命中后现算 `label = K{n}「名称」`（`requireNotNull(resolved.code)`，注册表只对已赋码条目建索引），接受文案改为「学习证据已记录：K1「函数单调性」 POSITIVE weight=…」、被拒文案改为「…未计入掌握度（REASON）——目标知识点 K1「函数单调性」。」。`label` 由条目的 code+displayName 组成，原始 node id 一个字节不进文本，且不新增模型可见字段（不动模型输入指纹），是满足「读出目标」的最小形态。
- 5a 的 `TutorToolTrace` 半边**不加字段**：唯一把 `TutorToolExecution` 变成 `TutorToolTraceEntry` 的填充点（`core/data/.../model/ModelTaskLiveChannels.kt:74-83`，append 只读 `outcome.tool/resultCount/ok/errorKind`）不在允许改的文件列表内；只加字段+行文案会得到「接了但没接上」的死机制，故按「如需要」判为当前范围内不需要，缺口与精确跟进点已写入遗留。
- 5b：大厅提示词（`OpenAiModelTaskAdapters.tutorLobbyPrompt` 尾部）追加私有块 `lobbySubjectScopeNote(declarations)`：仅当声明含 `KNOWLEDGE_READ`/`MASTERY_READ` 时渲染「本轮没有科目上下文：不要申请 KNOWLEDGE_READ / MASTERY_READ；它们需要科目范围，现在申请只会拿到空结果。」——与「提示词不得提到本轮未声明的工具」纪律一致（空声明/未声明轮零字节变化）。
- 用例：`RoomTutorToolRunnerTest` 新增两条（接受/被拒各一条，断言含 K1 与「函数单调性」且不含原始 id「kc-monotonicity」）；`TutorToolPromptInjectionTest` 新增两条（含声明时必须出现 / 未声明时不渲染）。

**改动文件**：`core/data/src/main/kotlin/.../study/RoomTutorToolRunner.kt`、`core/data/src/main/kotlin/.../model/OpenAiModelTaskAdapters.kt`、`core/data/src/test/kotlin/.../study/RoomTutorToolRunnerTest.kt`、`core/data/src/test/kotlin/.../model/TutorToolPromptInjectionTest.kt`。

**验证命令与实测结果**
- 复核者实跑 `./gradlew.bat :core:model:test --tests "*TutorToolTraceTest*" :core:data:testDebugUnitTest --tests "*RoomTutorToolRunnerTest*" --tests "*TutorToolPromptInjectionTest*"` → BUILD SUCCESSFUL，XML 实测 tests=44/11/12、failures/errors 全 0。
- 实施者实跑：`:core:model:test` TutorToolTraceTest → BUILD SUCCESSFUL、XML tests=12；`:core:data:testDebugUnitTest` RoomTutorToolRunnerTest → tests=44（基线 42 → +2，含 `an accepted write result names the target knowledge point by code and name` 与 `a rejected write result also names the target knowledge point`）；TutorToolPromptInjectionTest → tests=11（基线 9 → +2）；额外回归 TutorConversationPromptTest OK。
- 影响面闭合：四个 wire 协议编码器（OpenAiModelProtocol.kt:153、AnthropicMessagesProtocol.kt:82、GeminiGenerateContentProtocol.kt:90、OpenAiResponsesProtocol.kt:73）都只经 `OpenAiModelTaskAdapters.prompt` 一处取提示词，改一处覆盖全部路由；大厅声明集生产侧恒为全量五工具（TUTOR_TOOL_DECLARATIONS）。
- 复核者补：`grep -rn "TutorToolTraceEntry(" core feature app`（排除 build）main 源码唯一构造点在 `ModelTaskLiveChannels.kt:76`，其 append 只读四字段——证实「唯一填充点在授权外」的判断成立。

**遗留**
- **partial 裁定**：规格第 5 条里的 `TutorToolTraceEntry` 节点标签、行文案（`TutorToolTrace.rowText`）与学生面渲染未做（授权外文件）。精确跟进点：a) `RoomTutorToolRunner` 把 label 挂到该类型；b) `ModelTaskLiveChannels.append` 传 label；c) `TutorToolTrace.rowText`（TutorToolTrace.kt:173-196）在 label 非空时渲染；d) 学生面 `TutorConversationSurface` 跟进。
- 提示词断言用例落在 `TutorToolPromptInjectionTest`（不在 ask 括号点名的两个测试类）——两个点名类都不承载提示词用例，该文件是「提示词×工具声明」不变量之家；若编排方要求严格限定文件集，需要裁定这条用例该放哪。复核者认可「两个点名类不承载提示词用例」这一事实。
- prompt policy 版本：原始回报声明「未 bump、修复点在 `core/model/.../ModelEgress.kt`（授权外）」，复核把它列为 blocking（F1）。**撰写时工作树该文件已是 v11**：`ModelEgress.kt:53` = `tutor-lobby-v11-subject-scope-reads-forbidden`，注释写明「v11（批次 0 条目 5b）」（git diff 实读）——即复核要求的补丁已落在工作树；但该文件不在任何实施者回报的 files 清单内（机械范围核对列表见第四节）。
- 未跑任何 instrumented/androidTest（本机无设备）；`core:data` 全量与其它模块由编排脚本跑。

### 条目 6 · 发布链 7 个审计脚本的 PowerShell 编码修复（实施线：CI 与发布链）

**落法（选择理由与代价）**
- 清单：按仓库 grep 得 **7 个**审计脚本（报告 ⑤-8 正文「6 个」与同行列出的 7 个不符，按 grep 的 7 个改；第 8 个命中的 `tools/verify-android-env.ps1:96` 不是审计脚本、读 AVD `config.ini`，未改）。
- 9 处 `Get-Content` 全部加 `-Encoding UTF8`——本机 5.1 默认代码页 GBK，裸读 UTF-8 无 BOM JSON 会乱码并在 `ConvertFrom-Json` 直接抛；这是让 5.1 与 7 的解码口径一致的最小改动。
- 附带必要修复 A（两脚本加 UTF-8 BOM）：实测 5.1 把无 BOM 的 `.ps1` 按 GBK 解析，「语文」这类字面量尾字节吞掉闭引号 → 整个脚本语法错误（改前 :129/:114）；不加 BOM 就无法执行，而 BOM 对 pwsh 7 无行为影响。
- 附带必要修复 B（两脚本改 `ConvertFrom-Json` 取值）：5.1 与 7 对顶层 JSON 数组的取值口径不同（2 条记录实测 5.1 报 1、7 报 2；lesson 在 5.1 取属性直接报错），统一成「先取值再 `@()` 归一」才满足「同一输入同一结论」。

**改动文件**：`tools/audit-curriculum-coverage-draft.ps1`、`tools/audit-knowledge-coverage-ledger.ps1`、`tools/audit-knowledge-packs.ps1`、`tools/audit-knowledge-source-register.ps1`、`tools/audit-knowledge-source-rights.ps1`、`tools/audit-smartedu-lesson-activity-catalog.ps1`、`tools/audit-smartedu-textbook-catalog.ps1`。

**验证命令与实测结果**
- `grep -rn "Get-Content" tools/audit-*.ps1`（本报告撰写时复核）→ 7 文件 9 处，9/9 均已带 `-Encoding UTF8`：draft:70 / ledger:102,103 / packs:79 / register:134 / rights:16 / lesson:32 / textbook:32,69。
- 本机 shell：PowerShell **5.1.26100.9444** 与 pwsh **7.6.6** 两者均在（pwsh 未缺失）。
- 改前退出码（HEAD 副本，`&` 调用传 -ProjectRoot）：draft/ledger/packs/register/rights → ps5.1=1、pwsh=0；smartedu-lesson/textbook → ps5.1=1（:129/:114 解析错）、pwsh=1（缺 .artifacts 产物，:26）。
- 改后矩阵（当前文件，`& script` 无参数）：draft/ledger/packs/register/rights → ps5.1=0、pwsh=0；smartedu 两个 → ps5.1=1、pwsh=1，且同为 `SmartEdu … version file is missing: D:\…\.artifacts\research\smartedu-*.json`（第 26 行）——两 shell 结论一致。
- 编码复现：5.1 下裸 `Get-Content -Raw` 读真实 ledger → `ConvertFrom-Json : 传入的对象无效… (825)`；同文件加 `-Encoding UTF8` 后字段正确（`整本书阅读与研讨`）。
- 合成 smartedu 目录（UTF-8 无 BOM，1–2 条）跨 shell 相等（textbook 2 条 → 两 shell 都 `totalResourceRecords=2 uniqueResourceIds=2 CHINESE=1 MATH=1`；lesson 1 条 → 两 shell 都 `totalRecords=1 highSchool=1 CHINESE=1`；空分片 → 两 shell 都抛 `part is empty`、exit 1）；复核者独立复现（textbook 3 条、lesson 3 条均相等）。
- 7 个脚本在 5.1 与 7 下输出 JSON 逐字段比较 → 全部 `equal=True`（复核者独立复现）。

**遗留**
- 两个 smartedu 审计脚本的真实产物（`.artifacts/research/smartedu-*.json` 与分片）不在本机（缺口已登记 `docs/kb-outstanding-research-2026-10-02.md:317-319`），端到端只在合成目录（1–2 条记录）上验证；真实 4 分片/数千条记录的路径 UNVERIFIED。
- PS 5.1 的 `-File` 与 dot-source 调用下，param 默认值里的 `$PSScriptRoot` 为空 → 脚本在参数绑定即失败（既有怪癖，与编码无关）；治理文档给的调用形态是 `& .\tools\audit-….ps1`，不受影响；5.1 下用 `-File` 需显式传 `-ProjectRoot`。
- `tools/verify-android-env.ps1:96` 的 `Get-Content` 未加 `-Encoding`（不在「审计脚本」清单内，读 AVD `config.ini` 为 ASCII）。
- 报告 ⑤-8「怎么做」行写「6 个脚本」未改（他线在飞文档，本轮不碰）。
- 复核 finding（note，F8）：自称证据里的「8 处」是笔误，实际 9 处；实质无缺（本报告 grep 复核 9/9 到位）。

### 条目 7 · CI 回绿：artifact 依赖测试改「缺席 ⇒ 显式 skip」（实施线：CI 与发布链）

**落法（选择理由与代价）**
- 三个文件把产物依赖拆成「完全缺席 ⇒ `SkipTest` + 理由 / 在场（哪怕不全）⇒ 原断言一条不改」——干净检出没有 `build/`（`.gitignore:14` `**/build/`），但把断言改永真或删用例是红线，所以只加守卫、不碰断言。
- 每个 skip 分支配一个能构造「产物缺席」的用例（临时目录注入 + patch 默认路径后直接跑真实用例方法，断言它抛 `SkipTest`），避免出现没人可验证的 skip。
- 只在切片「一个都没有」时 skip；切片存在但不全仍走原断言变红——防的是把不完整产物也当缺席放过。
- skip 理由写清产物路径 + 重建命令，便于把「跳过」与「门红」区分开。

**改动文件**：`tools/tests/test_kb_content_audit_verdicts.py`、`tools/tests/test_kb_transcription_ledger.py`、`tools/tests/test_kb_check_transcripts.py`（用例数 8→11、24→28、10→13，共 +10；原断言零改动）。

**验证命令与实测结果**
- 干净检出（`git archive HEAD` 导出，无 build/）改前：`python -m unittest discover -s tools/tests -t tools` → `Ran 585 tests` / `FAILED (failures=13, errors=7, skipped=1)`；7 个 ERROR 全是 `缺 manifest：…\build\2027-53-pages\manifest_slim.json（先跑 scan_render_pages.py）`（transcription_ledger×6、check_transcripts×1），2 个 FAIL 是 content_audit_verdicts 的切片/复算用例。
- 干净检出改后（同导出 + 3 个测试文件覆盖）：三文件分别 `Ran 11/28/13 … OK (skipped=2/7/1)`；全量 `Ran 595 tests` / `FAILED (failures=11, skipped=11)`——**0 ERROR**；残余 11 红 = `test_kb_materialize`×4（他线）+ 本模拟 CRLF 产物×7（见遗留）。
- 工作区（产物在场，最终状态）：`python -m unittest discover -s tools/tests -t tools` → `Ran 612 tests … OK`、EXIT=0（0 skip）；本报告撰写时复跑 `Ran 612 tests in 130.309s` / OK / 0。
- skip 可达性：新增 10 个用例全绿；干净检出上 skip 用例仍 `ok`（自己构造缺席条件），`-v` 输出里 skip 理由原文可见（含产物路径与重建命令）。
- 复核者独立复现：内存注入「产物缺席」后四个文件分别 28/13/11/9 例 → skipped=7/1/2/7、failures=0、errors=0；并拉 CI 最新失败 run 的日志确认 7 ERROR（ledger×6 + transcripts×1）与 6 FAIL（4 materialize「块不存在」+ 2 content_audit 切片）正是本次守卫对象。

**遗留**
- 干净检出上「单文件直跑」仍会 ERROR 的 ~15 个测试（`mkdtemp(dir=pack_io.REPO / "build")` 类，如 `test_kb_apply_authored_materials:59`、`test_kb_content_audit:180`、`test_kb_pack_io:52`、`test_kb_promote:36,57`、`test_kb_staging_freeze:27`、`test_kb_table_consistency:44`、`test_kb_update_manifest:33` 等）本次未改：它们缺的是 build/ **目录**（不是产物），正确修法是建目录而非 skip；CI 走全量 discovery 时 build/ 早已建立，不影响 CI 绿。
- `knowledge-production/2027-53-transcripts` 未被 git 跟踪：manifest 在场而转写缺席的机器上，账本用例会在空转写集上运行（口径静默偏弱）；只守了会 ERROR 的 manifest。
- 干净检出验证用的是 `git archive` 导出，会把文本文件 CRLF 化，导致该导出里 `test_dense_asset_gate` / `test_kb_promote`×5 / `test_kb_table_consistency` 的残余红；已用 sha 与内嵌换行证据判定为模拟产物（Linux checkout 无此转换），但「Linux 上确实全绿」未在本机执行（无 Linux 环境）——属推断 + 字节证据。
- 复核 finding（note，F4，与条目 2 同一条）：`test_kb_materialize.py` 干净检出 7/9 例整类 skip（详见条目 2 遗留）。
- CI「回绿」本身未验证：修复未推送（见第五节）。

### 条目 8 · 文档清账（实施线：文档与只读产出）

**落法（逐点）**
- dense 两份 README：不篡改历史读数，改用「文首 2026-10-02 复核注记（现值 41,193=3,866 节点+37,327 别名、fixture 330；n=290/28,931/40,319 为当时口径）+ 关键行内标注」；验收表本身（≥0.999 硬门）一字未动。
- `check_asset.py`：docstring/注释里的 3,572/25,359 与 28,931 就地改为 3,866/37,327 与 41,193 并注日期（纯注释，不动任何判据）。
- 「22 门」→ 23：活跃代码/测试注释直接改（`promote.py:8,54`、`__init__.py:22`、`apply_boundary_fixes.py:9`、`build.py:11`、`test_dense_asset_gate.py:4`、`test_kb_promote.py:9`）；dated 历史文档保留原值但加「当时口径——2026-10-02 实测已为 23 门」标注。
- 118/308 → 130/308：两处源头（`problem-register:616`、`coverage-baseline-design:170`）就地订正并留「2026-10-02 复算订正；原写 118」痕。
- 25 模块表述：「永久 UNDECIDED」订正为「分母不完整/待原子化」（`baseline.md:68` 与 `coverage-baseline-design:154`），依据台账 v2 全表 UNDECIDED=0 的实测。
- W-2 数字：`outstanding-work:38,92` 与 `textbook-source-verification:98,108` 标注「2026-10-02 复算现值 571（薄料）/3,866（知识点）」；outstanding-work 顶部作废指针保留未删。
- gate 命令写法：`baseline.md:24/114` 订正为 `PYTHONPATH=tools python tools/kb_build/gate.py`（原样写法实测 `ModuleNotFoundError`；其余命令实测可原样跑故未动）。
- `docs/status.md`：按 `generate_status.py` 的用法（CI 同款环境变量 `COMMIT_SHA`/`BRANCH`/`TRIGGERED_BY`/`JOB_STATUS`）重生成，并解决「权威表 sha 滞后」——6 张表 sha 与工作树逐一实测相等。

**改动文件**：`docs/research/exam-leak-2026-10-02.md`（条目 9 新增，与 8 同线）、`docs/research/material-bindings-arrears-2026-10-02.md`（条目 9 新增）、`docs/status.md`、`docs/kb-problem-register-2026-09-15.md`、`docs/research/coverage-baseline-design-2026-09-15.md`、`docs/kb-textbook-source-verification-2026-09-21.md`、`docs/kb-outstanding-work-2026-09-20.md`、`docs/kb-architecture-refactor-2026-09-21-report.md`、`docs/kb-architecture-refactor-decisions-2026-09-21.md`、`core/data/src/main/assets/dense/README.md`、`tools/dense_build/README.md`、`tools/dense_build/check_asset.py`、`tools/kb_build/promote.py`、`tools/kb_build/__init__.py`、`tools/kb_build/apply_boundary_fixes.py`、`tools/kb_build/build.py`、`tools/tests/test_dense_asset_gate.py`、`tools/tests/test_kb_promote.py`、`build/agent-outstanding/baseline.md`（gitignored 事实卡，见 F9）。

**验证命令与实测结果**
- `python tools/dense_build/check_asset.py` → 10/10 OK，`vectorHeader：头：dim=512 count=41193`、`layout：ids 与包布局逐条一致（41193 条向量）`、`.vec sha256=8649afa62ce968c3`。
- 读旁车 `bge-small-zh-int8.vec.json` → `{'atomicNodes': 3866, 'aliasVectors': 37327, 'vectorCount': 41193}`；读 `encoder-parity.json` → count 330、byKind {'query': 130, 'surface': 200}。
- `PYTHONPATH=tools python tools/kb_build/gate.py` → 「全部通过：缺陷归零」；`len(gate.evaluate()) = 23`（实测 23 项）；本报告撰写时复跑 exit 0。
- 聚合 `knowledge-production/kb-coverage-alignment-2026-v2.json` → MATH 130/308、PHYSICS 71/138、CHEMISTRY 51/73、BIOLOGY 100/142；TOTAL UNDECIDED=0、COVERED 352/661。
- `PYTHONPATH=tools python -m kb_build.report_material_gaps` → 「缺口共 571 个：零材料 0、仅 1 条材料 571」。
- `COMMIT_SHA=3d234d32… BRANCH=main TRIGGERED_BY=local-manual JOB_STATUS=success python tools/ci/generate_status.py` → `wrote docs/status.md`；Overall **PASS**；6 张表 sha 与工作树逐一实测 match=True（含修复 `chapter_by_source=8efce39f8e61`、`alias_map=2210f01b2c72`）。
- grep 复查：`130/308` 命中两处订正文本（本报告撰写时复核：`problem-register:616`、`coverage-baseline-design:170` 均含「2026-10-02 复算订正；原写 118」）；`复算现值 571` 命中 3 处；两处「2026-10-02 复核注记」（core README:15 / tools dense README:11）；`baseline.md:24/114` 均为 PYTHONPATH 形式；`23 门/23 道` 命中 6 个代码/测试文件 + 2 个历史文档标注。

**遗留**
- `grep -rn "118/308" docs/` 不为 0：5 处命中全部落在 `docs/kb-outstanding-research-2026-10-02.md`（本轮结论记录，明确不碰；含验收判据本身的行），其余源头已订正并留痕。①-3/⑥-6 的验收「为 0」按字面不可达成（复核 finding F5）。
- 同类旧数未清（不在 ⑥-6 表清单内/未获授权）：`tools/dense_build/dense_asset.py:30,36,286`、`pack_dense_asset.py:11,22`、`gen_device_parity_fixture.py:22`、`quant_error_compensation.py:39` 的 docstring 仍含 28,931/40,319（本报告撰写时 grep 复核：7 处命中）；`DenseRecallRerankerTest.kt:17` 注释「真资产 40,319 条」；`docs/kb-dense-rebuild-2026-10-01.md` 保留原样（重打轮历史报告）。
- n=330 下的宿主对拍**未重测**（UNVERIFIED，两份 dense README 已如实标注）；真机侧 n=330 读数（min 0.99955 / p95 0.99985）系引 `build/agent-outstanding/group-5.md:140-144` 卡片，未复跑仪器化测试。
- `build/agent-outstanding/baseline.md` 严格说在授权（docs/ 路径 + tools/）之外被修改：它是 gitignored 的工作流事实卡，改动不进提交；实施者已声明、给出回退方式（复核 finding F9）。
- `build/agent-outstanding/group-2.md:164` 与 `draft.md:1041` 仍含无 PYTHONPATH 的 gate 命令写法（工作流事实卡/报告草稿，未授权改动）；旧 `.pyc` 缓存亦有「22 门」命中（gitignored）。
- 任务描述称 outstanding-work 的 W-2 段含「1,232/3,572」；实测该文件只有 1,232，3,572 位于 `docs/kb-textbook-source-verification-2026-09-21.md:108`——两个文件均已按「当时口径 + 2026-10-02 复算现值」订正。
- `docs/status.md` 的 tools tests 计数仍是 602/602 快照（生成于本批最后一批测试落地之前；现工作树为 612，见 F6）；6 张表 sha 与工作树相等、Overall PASS 无误。
- `problem-register:193` 的「永久空洞」句未改（2026-09-15 的分析记录，其条件句仍成立）。

### 条目 9 · 只读产出：exam-leak 与 material-bindings 欠账清单（实施线：文档与只读产出）

**落法（选择理由与代价）**
- `docs/research/exam-leak-2026-10-02.md` 由 `check_exam_leak` 同一代码路径（`audit_material_examples.classify`）脚本化复算生成 206 条明细 + 164 节点汇总，避免人工誊抄；写入生成时间与两条复算命令。
- `docs/research/material-bindings-arrears-2026-10-02.md` 用只读脚本按 release 包逐行对照 `material_bindings` 598 行，展开 407 条欠账 + 41 条目标消失 + 37 条空目标（解绑）行；写入复算命令与生成时间；**不执行任何写入**。

**改动文件**：`docs/research/exam-leak-2026-10-02.md`（新增）、`docs/research/material-bindings-arrears-2026-10-02.md`（新增）。

**验证命令与实测结果**
- `PYTHONPATH=tools python -m kb_coverage.check_exam_leak` → EXIT=1；`MATERIAL 行 38929；例题派生命中 206（涉及 node_slug 164 个）`；判据 119/85/1/1。复核者用 `check_rows` 复算命中 206 / 节点 164 / 判据 119-85-1-1 全等，并数行：文档 §2=164 行、§3=206 行。
- arrears 复算（新文档 §3 的同一脚本）→ `consistent=113 arrear=407 missing_point=41 empty_target=37`（407+78+113=598）；staging 与 release 主包 sha 前 16 位均为 `647675cd98f52ca8`；复核者独立跑同脚本得同数，§1=407、§2.1=41、§2.2=37 行。
- 两份均含「生成时间」（2026-10-02 18:07）与「复算命令」节。

**遗留**
- 两份新文档的完整复算命令以 linux/bash heredoc 形式给出（Windows cmd 下需 git bash）。
- 未跑：真机 instrumented 三门、gradle 侧单测（非本条范围）。

---

## 二、独立复核的裁定与处理

复核者由编排单独指派、**未参与施工**，只读、只跑只读命令，逐条核对 1–9 的落法与证据。

### 2.1 逐条裁定

| 条目 | 裁定 | 复核理由摘要（复核者自己读到/跑到的） |
|---|---|---|
| 1 | done | 实跑 `overfull_nodes` exit 0，输出 2,518/47,538/37,466/3,866/≥4 2,754/=4 236/最大 139 与自称一致；`git show HEAD:…` 复现旧 `KeyError: 'materials'`；合成夹具 3 tests OK；机制描述核对 Kotlin 实码（1,024 取数上界、20,000 预算、排序键、无每节点条数门）。 |
| 2 | done | TYPES 收紧到 3 值且与 merge 逐字相等（机检 True，含 check_slice_verdicts/new_content/reconcile 共 5 处同值）；`--dry-run` 86,414 行 → 材料 38,929/跳过 47,485/错误 0；自算表内旧 type MATERIAL=0、SKIP 旧 type 482（363/104/14/1）、rejudge_queue 698、包内 667；protocol 文档订正成立；测试 9 例 OK。注：干净检出上行为回归 7 例会 skip（见 F4）。 |
| 3 | done | 直跑 exit 0、11 组/18 对；`--json` same=11/prefix=18、CSV 行数自算 59；跨科同名 slug 确为 9 个；新测试 9 例 OK；全仓无代码调用者，显示排序改动无下游。 |
| 4 | done | `build --write` exit 2 + 拒绝文案（未落盘）；三处提示实读已改；`wusan_plan` 旧版经 stdin 复现 `KeyError` exit 1、新版 exit 0；`resolve_points` exit 0、`regroup_aggregates` exit 1（既有数据态拒绝，消息与改动无关）；`test_kb_build_generator` 9 tests OK。残留提示/调用者见 F7。 |
| 5 | **partial** | 实跑三条 JVM 命令 BUILD SUCCESSFUL，XML 44/11/12 全 0 failures；代码实读 targetLabel=`K{n}「name」` 两路文案、原始 id 不进文本、四个 wire 编码器都经 `OpenAiModelTaskAdapters.prompt` 取词。缺：规格里的 TutorToolTraceEntry/行文案/学生面半边未落（授权外）；提示词版本未 bump（见 F1）。 |
| 6 | done | 9 处 Get-Content 全带 `-Encoding UTF8`；复核者在 PS 5.1 与 pwsh 7.6.6 各跑一遍，5 个脚本双 shell rc=0 且 JSON 逐字段相等；两 smartedu 双 shell rc=1 同一缺产物结论；PS5.1 裸读真实 ledger 复现 `ConvertFrom-Json…(825)`、加 -Encoding 通过；BOM 已在；合成目录跨 shell 相等独立复现。 |
| 7 | done | skip 守卫可证明（内存注入缺席后四文件 skipped=7/1/2/7、failures=0/errors=0）；产物在场时原断言照跑（全量 612 OK 复跑）；diff 实读无删用例/无放宽；CI 侧 7 ERROR + 6 FAIL 与守卫对象一一对应。注：「gh run → success」门当前为 failure（未推送，见 F3）。 |
| 8 | done | `check_asset.py` 实跑 10/10 OK、count=41193；旁车 3866/37327/41193、fixture 330；23 门 6 个代码/测试文件改齐、`gate.evaluate()=23`、`python tools/kb_build/gate.py` 复现 ModuleNotFoundError；130/308 两处订正；W-2 571 实跑；baseline.md:24/114 为 PYTHONPATH 写法；status.md 6 张表 sha 逐一相等、Overall PASS。残留口径见 F5/F6/F10。 |
| 9 | done | `check_exam_leak.check_rows` 复算 206/164/119-85-1-1 全等，文档 §2/§3 行数实点；arrears 复算 consistent=113/arrear=407/missing_point=41/empty_target=37（598 行），§1/§2.1/§2.2 行数实点；staging 与 release 主包 sha 前 16 位相等；两文档均含生成时间与复算命令；未执行任何写入。 |

### 2.2 findings 与处理（1 条 blocking + 9 条 note）

| # | 位置 | 问题 | 等级 | 处理 / 现状（截至本报告撰写） |
|---|---|---|---|---|
| F1 | 条目 5 · `core/model/.../ModelEgress.kt:51,639-642` | 大厅提示词文本已改（新增 `lobbySubjectScopeNote`），但 `ModelPromptPolicyVersions.TUTOR_LOBBY` 未 bump——违反仓库明写纪律（措辞进 prompt 指纹，改动需 bump），且无测试能自动抓住 | **blocking** | **补丁已见工作树**：撰写时 `ModelEgress.kt:53` = `tutor-lobby-v11-subject-scope-reads-forbidden`，注释写明「v11（批次 0 条目 5b）」（git diff 实读）。但该文件不在任何实施者回报的 files 里，机械范围核对把它列入「无法归因」（见第四节）；**提交时需确认它随本批提交**。 |
| F2 | 条目 5 · 规格第 5 条 | 规格写的「runner + TutorToolTraceEntry + 行文案 + 用例」只落了 runner 半边（理由成立：唯一填充点在授权外），按规格判 partial | note | **未修**。跟进点 a–d 见条目 5 遗留；需编排方决定是否安排后续线。 |
| F3 | 批次 0 验收门 · gh run 36965281642（android-check，headSha 65dadc7b） | 验收门要求最新 main push 结论 success，实测仍是 failure：check job 在「Knowledge build toolchain tests」失败（Ran 585，failures=6/errors=7），instrumented job 在 `:core:data:connectedDebugAndroidTest` 失败 | note | **待推送后验证**：这 13 条红正是条目 7 的修复对象；修复未推送，只能等 push 后由 CI 复核；instrumented 失败不在批次 0 范围，但同属该验收门（见第五节）。 |
| F4 | 条目 2/7 · `tools/tests/test_kb_materialize.py:29-64` | 干净检出（CI）里 7/9 例整类 skip，CI 实际只剩常量比较——「收紧 3 值」在 CI 没有行为断言 | note | **未修**（owner：入库白名单与协议）。建议用 `mock.patch(load_chunks)` 给 PlanTest 注入合成块池，摆脱未入库产物依赖。 |
| F5 | 条目 8 · `docs/kb-outstanding-research-2026-10-02.md:285` 验收判据 | `grep -rn "118/308" docs/` 为 0 未达成：5 处命中全在本轮结论记录（明确不碰），其余源头已订正留痕 | note | **未清**（对象文档明令不碰）。验收口径与现状的差异如实记录在案。 |
| F6 | 条目 8 · `docs/status.md:97-101` | 「tools tests: 602/602」是快照，早于本批最后落下的测试（现工作树 612 例）；`generate_status.py` 自己会跑套件取数 | note | **未更新**；提交前重生成一次即可对齐（6 张表 sha 与工作树相等，Overall PASS 无误）。 |
| F7 | 条目 4 · `StagingPackRetrievalBenchmarkTest.kt:46`、`tools/kb_build/check_materials.py:2`、`.zcode` 两工作流脚本 | 封口后仍有「build --write」的推荐/调用残留（tracked 测试的 assumeTrue 文案会把人指去一条 exit 2 的命令；两 gitignored 脚本调用现会被守卫拒绝） | note | **未修**（owner：内容管线工具）。⑥-5 验收「全仓 grep 不到推荐用法」因此未完全达成。 |
| F8 | 条目 6 · 证据文本 | 自称证据「8 处 Get-Content」是笔误，实际 9 处 | note | **已核实**：本报告 grep 复核 9/9 均已加 -Encoding UTF8，实质无缺；数字已按实况记录。 |
| F9 | 条目 8 · `build/agent-outstanding/baseline.md` | 该文件在授权（docs/ 路径 + tools/）之外被修改（gitignored 工作流事实卡，⑥-6 表把它列为源头之一，可辩护） | note | **记录在案**：改动不进提交、无仓库产物影响；如需回退该文件即可（不影响其余订正）。 |
| F10 | 条目 8 · `tools/dense_build/dense_asset.py:30,36,286` 等 | ⑥-6 圈定范围外的同类旧数仍留在 docstring（40,319/28,931 等，部分以看似现值的口吻书写） | note | **未清**（7 处，本报告 grep 复核）；是否清属编排范围。 |

**汇总**：blocking 1 条（F1，补丁已见工作树、待提交确认）；note 9 条（F2–F10）。其中**未随本批修复的 note 项 7 条：F2、F4、F5、F6、F7、F9、F10**（各自 owner 与处置路径见上表）；F3 属「待推送验证」，F8 属证据数字更正。

---

## 三、终局门结果

| 门 | 命令（编排脚本实跑） | 终局结果 | 改动前基线 | 本报告撰写时复跑 |
|---|---|---|---|---|
| KB 门 | `python tools/ci/run_kb_checks.py` | 退出码 **0**（gates/consistency/roundtrip/manifest/dense 5 节全 OK） | 退出码 0 | 复跑退出码 **0** |
| Python 套件 | `python -m unittest discover -s tools/tests -t tools` | 退出码 **0** | 退出码 0 | 复跑（带 `PYTHONPATH=tools`）`Ran 612 tests in 130.309s` / OK / 退出码 **0** |
| overfull 自跑 | `python -c "import sys;sys.path.insert(0,'tools');import runpy;runpy.run_module('kb_build.overfull_nodes',run_name='__main__')"` | 退出码 **0** | （基线未跑该项） | 复跑 `PYTHONPATH=tools python -m kb_build.overfull_nodes` 退出码 **0** |
| JVM 全量 | `gradlew.bat testDebugUnitTest --continue --console=plain` | 退出码 **1** | 退出码 0 | 复跑（加 `--rerun`）BUILD SUCCESSFUL in 1m 26s / 退出码 **0**（见下） |

另：`PYTHONPATH=tools python tools/kb_build/gate.py` → 「全部通过：缺陷归零」/ 退出码 0（23 项；本报告撰写时复跑）。

**JVM 全量（四道门中唯一未过的一节）说明**：
- 终局门记录的退出码 1 来自编排脚本（基线 0 → 终局 1）；素材未附该次运行的失败明细（哪个模块/哪条用例/哪个任务）。
- 本报告撰写时实查磁盘：各模块 `build/test-results` 下最近一批 XML（2026-10-02 20:21–20:24，291 个文件，覆盖 core/* 与 feature/*）**没有** `failures="[1-9]"` 或 `errors="[1-9]"` 标记。
- 本报告撰写时复跑同门命令并加 `--rerun`（强制重执行测试任务）：BUILD SUCCESSFUL in 1m 26s、退出码 0；其中 9 个模块的 `testDebugUnitTest` 任务实际重执行（core:export / feature:profile / core:ui / feature:library / feature:review / feature:capture / feature:tutor / core:database / core:data），core:domain 与 core:model 的测试任务为 UP-TO-DATE（沿用其上一轮成功结果），146 个任务 9 executed / 137 up-to-date。
- 结论（如实）：该退出码 1 **在撰写时未复现**，其失败明细已不可从工作树证据恢复（被更晚的运行覆盖）；也无法判定它由本批改动还是同工作树他线在飞改动/任务级偶发引起。如需钉死，建议在提交前的冻结树上再跑一次同门并保留完整输出。

---

## 四、范围核对

### 4.1 本批新增/改动的文件清单（43 个，不含本报告；按实施线分组，「新增」为本次新建）

**条目 1/3/4（内容管线工具）8 个**
- `tools/kb_build/overfull_nodes.py`
- `tools/kb_build/scan_duplicate_nodes.py`
- `tools/kb_build/build.py`（与条目 8 同文件）
- `tools/kb_build/regroup_aggregates.py`
- `tools/kb_build/resolve_points.py`
- `tools/kb_build/wusan_plan.py`
- `tools/tests/test_kb_overfull_nodes.py`（新增）
- `tools/tests/test_kb_scan_duplicate_nodes.py`（新增）

**条目 2（入库白名单与协议）3 个**
- `tools/kb_coverage/materialize.py`
- `docs/kb-chunk-judgment-protocol.md`
- `tools/tests/test_kb_materialize.py`

**条目 5（学生侧两小修）4 个**
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelTaskAdapters.kt`
- `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunnerTest.kt`
- `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorToolPromptInjectionTest.kt`

**条目 6/7（CI 与发布链）10 个**
- `tools/audit-curriculum-coverage-draft.ps1`
- `tools/audit-knowledge-coverage-ledger.ps1`
- `tools/audit-knowledge-packs.ps1`
- `tools/audit-knowledge-source-register.ps1`
- `tools/audit-knowledge-source-rights.ps1`
- `tools/audit-smartedu-lesson-activity-catalog.ps1`
- `tools/audit-smartedu-textbook-catalog.ps1`
- `tools/tests/test_kb_content_audit_verdicts.py`
- `tools/tests/test_kb_transcription_ledger.py`
- `tools/tests/test_kb_check_transcripts.py`

**条目 8/9（文档与只读产出）19 个**
- `docs/research/exam-leak-2026-10-02.md`（新增）
- `docs/research/material-bindings-arrears-2026-10-02.md`（新增）
- `docs/status.md`
- `docs/kb-problem-register-2026-09-15.md`
- `docs/research/coverage-baseline-design-2026-09-15.md`
- `docs/kb-textbook-source-verification-2026-09-21.md`
- `docs/kb-outstanding-work-2026-09-20.md`
- `docs/kb-architecture-refactor-2026-09-21-report.md`
- `docs/kb-architecture-refactor-decisions-2026-09-21.md`
- `core/data/src/main/assets/dense/README.md`
- `tools/dense_build/README.md`
- `tools/dense_build/check_asset.py`
- `tools/kb_build/promote.py`
- `tools/kb_build/__init__.py`
- `tools/kb_build/apply_boundary_fixes.py`
- `tools/kb_build/build.py`（计一次）
- `tools/tests/test_dense_asset_gate.py`
- `tools/tests/test_kb_promote.py`
- `build/agent-outstanding/baseline.md`（gitignored 事实卡；授权外改动，见 F9）

### 4.2 无法归因到本批的文件（27 个；机械范围核对 = 批次窗口内被改动、但不在任何实施者 files 清单内）

**core/domain 主代码 9 个**：`AdaptiveQuestionSelector.kt`、`AlgorithmConstants.kt`、`KnowledgeReviewQueue.kt`、`LearningCoreVersions.kt`、`LearningProjector.kt`、`MasteryWriteGate.kt`、`ReviewPlanner.kt`、`StudyExperienceRepository.kt`、`KnowledgeNodeScorer.kt`（未跟踪新文件）

**core/domain 测试 8 个**：`AdaptiveQuestionSelectorTest.kt`、`BlockingLearningCoreReviewTest.kt`、`KnowledgeMasteryDropPropagationTest.kt`、`KnowledgeReviewPlannerTest.kt`、`KnowledgeReviewQueueTest.kt`、`LearningProjectorTest.kt`、`ReviewPlannerV2Test.kt`、`ReviewPlannerTest.kt`（已删除，git 状态为 D）

**core/data 主代码 4 个**：`KnowledgeQuizFeedbackWriter.kt`、`RoomBackedStudyExperienceRepository.kt`、`StudyReviewPlannerService.kt`、`KnowledgeEvidenceWriter.kt`（未跟踪新文件）

**core/data 测试 3 个**：`KnowledgeQuizFeedbackWriteTest.kt`、`MasteryUpdateEvidenceIdTest.kt`、`RoomBackedStudyExperienceRepositoryTest.kt`

**core/database 1 个**：`LearnerChatEvidenceEntity.kt`　**core/model 1 个**：`ModelEgress.kt`　**docs 1 个**：`docs/research/algorithm-version-ledger.md`

两点需要说明（本报告撰写时实读）：
- `ModelEgress.kt` 的 diff 内容为条目 5b 的版本 bump（v11 + 注释）——与复核 F1 要求的补丁一致；它不在实施者回报里，但内容指向本批条目 5b，提交时需连同确认归属。
- 其余条目（core/domain 学习核心 + core/data 学习体验的成套改动、含一个测试删除）与同工作树中另一在飞工作流的特征一致；撰写时观察到另一工作流的运行会话（`stage3b-resume` 脚本）仍在活动。具体归属以提交时逐文件确认为准。

### 4.3 撰写时补充观察（不属上述两表）

工作树里另有一批**基线时即已存在**的他线在飞改动与未跟踪临时产物（因此不落入「窗口内新增」的无法归因表），例如：`docs/agent-first-refactor-decisions-2026-09-23.md`、`docs/superpowers/specs/2026-09-25-*.md`（本批纪律明令不碰）、`tools/kb_build/merge_candidate_verdicts.py` 及其测试（他线既有改动，实施者声明未触碰）、`docs/kb-outstanding-research-2026-10-02.md`（上一轮研究报告，未跟踪），以及大量 `.agent_*`/`.tmp_*` 临时目录（他线产物）。本批实施者与复核均未触碰上述范围。

---

## 五、未验证项与原因

1. **CI 真正回绿（最重要）**：本批不提交、不推送，故必须等推送后由 CI（GitHub Actions `android-check`）验证。复核拉取的最近 main push 结论仍是 **failure**（run 36965281642，headSha `65dadc7b`，早于本批施工）：其中「Knowledge build toolchain tests」的 13 条红（7 ERROR + 6 FAIL）正是条目 7 的修复对象，修复未推送故无法在本批验证；同 run 的 instrumented job（`:core:data:connectedDebugAndroidTest`）失败不在批次 0 范围。另 `gh run` 门在批次 0 的验收要求是「最新 main push 结论 success」，当前未满足。
2. **JVM 全量退出码 1 的失败明细**：未能定位——素材未附该次运行的失败明细；磁盘上更晚的运行已覆盖其结果；撰写时复跑（`--rerun`）未复现失败（见第三节）。无法判定它由本批改动或他线在飞状态引起。
3. **instrumented / androidTest / 真机三门**：本机无设备，未跑（与 KD-25 同款限制）；条目 5 的学生面渲染等设备侧验收留有跟进点。
4. **smartedu 两个审计脚本的真实产物路径**：真实 `.artifacts/research/smartedu-*.json` 不在本机，端到端只在合成目录（1–2 条记录）验证；真实 4 分片/数千条记录的路径 UNVERIFIED。
5. **「干净检出在 Linux CI 全绿」**：本机无 Linux 环境，结论基于 `git archive` 模拟 + CRLF 字节证据（属推断 + 字节证据，非实测）；模拟中残余 11 红已逐条归因（4 条来自他线文件、7 条来自导出方法的 CRLF 转换，Linux checkout 无此转换）。
6. **dense n=330 宿主对拍与真机读数**：未重测；真机 n=330 读数系引 `build/agent-outstanding/group-5.md:140-144` 卡片，未复跑仪器化测试（两份 dense README 已如实标注）。
7. **`build.py` 完整落盘路径**：只验证到「危险旗标放行 → 进入 `Builder()`」，未跑成一次完整落盘（撞既有 `NewContentError`）；按纪律未对真实 staging 执行写路径。
8. **`regroup_aggregates` 在真实数据上仍 exit 1**：是其自身的一致性核对拒绝（MATH 平面向量章节目标不符），改动前后同一条消息，非本次引入；本次只验证了它的缺包守卫路径。
9. **全量套件读数的口径**：工作树 612 例包含他线在飞未提交测试与改动（非纯本批状态的干净检出读数）；干净检出读数（595 例、11 红）受 `git archive` CRLF 干扰。
10. **文档清账的 grep 验收**：`grep -rn "118/308" docs/` 为 0 不可达成（5 处命中在本轮结论记录、明令不碰）；「全仓 grep 旧数只在显式标注历史段落出现」未满足（dense_build 7 处等，见 F10）。
11. **批次外的未跑项**（按编排记录）：真机性能、仪器化三门、批次 1 起的结构清理（需 F 分叉裁定）。

> 附：本报告本身是本批新增的文件（`docs/kb-batch0-execution-2026-10-02.md`），不属于上述 43 个来源文件；除本文件外，本报告撰写未改动任何受版本控制或既有的文件，未切分支、未提交、未推送。
