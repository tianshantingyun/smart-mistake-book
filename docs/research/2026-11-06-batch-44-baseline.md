<!-- 体量自查（落盘后 wc -lc 实测见 §11）：本文件是 docs/research 下的基线件，按批次 43 基线同构。批次 44 基线核对员落盘（只读）。 -->

# 批次 44 基线核对（只读，2026-11-04）

| 字段 | 值 |
|---|---|
| evidence-id | `B44-BASELINE-01` |
| executed-at | 2026-11-04（本机时间）；文档名按计划批次 44 的 `2026-11-06` |
| head | `26f2327b0f88d3cad7e076b967b62874e36ca07d`（`26f2327b`，本轮 `git rev-parse HEAD`） |
| origin/main | 本地 `refs/remotes/origin/main` = 同值；`git rev-list --left-right --count HEAD...origin/main` = `0	0`；**远端未 fetch 核实**（`.git/FETCH_HEAD` 停在 2026-11-01 16:02） |
| 执行者 | 基线核对员（子代理），共享工作树，**只读** |
| 工具（本轮实跑版本） | git 2.55.0.windows.5、Python 3.13.14、adb 1.0.41（37.0.1-15733141）、emulator 37.1.11.0 |
| 未执行 | 任何 Gradle 任务、任何 `am instrument`、任何 APK 构建/安装、任何 git 写、任何 Provider 调用、任何 emulator 新起 |
| verdict | 基线成立（8 项核对全部有本轮实跑/实读证据）；但**计划自身的验收机制在树上一个字节都不存在**，且 1200 项里 ≥1020 项没有枚举载体（§5 F1/F2/F3）。三项为**批次 44 开跑前必须先解决的高危前提** |

> **口径**：**实跑**=本轮在本机执行的命令与输出；**实读**=本轮读到的仓内源码/文档/日志；**转录**=引用既有文件原文（标明出处行）。
> 状态严格三值：**已核** / **未核（UNVERIFIED）** / **阻塞（BLOCKED）**。凡“绿”必指名绑哪份 APK/日志。
> **本轮未跑 Gradle**：纪律要求 Gradle 只在门禁阶段串行运行；下文引用的门禁日志一律标“转录”，不当本轮结果。

---

## 一、HEAD / origin / 工作树

### 1.1 HEAD 与远端

```text
$ git rev-parse HEAD                              → 26f2327b0f88d3cad7e076b967b62874e36ca07d
$ git rev-parse origin/main                       → 26f2327b0f88d3cad7e076b967b62874e36ca07d
$ git rev-list --left-right --count HEAD...origin/main → 0	0
$ git log --oneline -1                            → 26f2327b docs: 批次43 台账回填（KD-75 收窄关闭/KD-76 open/KD-85；6C-2 残留回滚）（4 文件）
$ git branch -a --contains HEAD                   → * main / remotes/origin/HEAD -> origin/main / remotes/origin/main
$ ls -l .git/FETCH_HEAD                           → 2026-11-01 16:02（2131 B）
```

- HEAD 是批次 43 的**收口后端**提交；批次 43 记录里的 HEAD `0fef9436` 已被这笔推进，**任何按 `0fef9436` 复现的命令现在都指向非 HEAD**。
- **边界**：`origin/main` 是本地 ref，本轮**未** fetch（fetch 属 git 写，本 ask 禁止）。故“远端此刻仍等于该提交”**UNVERIFIED**，只能写“本地 ref 相等”。

### 1.2 工作树计数（含时点差）

```text
# 第 1 次读（本节开头）
$ git status --porcelain -uall | wc -l            → 3747
$ git status --porcelain -uall | cut -c1-2 | sort | uniq -c
   3646 ??
    101  M
# 第 2 次读（同一 ask 内，收尾前复核）
$ git status --porcelain -uall | wc -l            → 3753
$ git status --porcelain -uall | cut -c1-2 | sort | uniq -c
   3651 ??
    102  M
$ git status --porcelain | wc -l                  → 535        （目录折叠口径）
$ git diff --cached --name-only | wc -l           → 0          （无 staged）
$ git status --porcelain -uall | grep -c "^ D|^D" → 0          （无删除）
$ git ls-files "*gradle.lockfile" | wc -l         → 0          （已跟踪锁 = 0）
$ git status --porcelain -uall | grep -c gradle.lockfile → 14  （未跟踪锁 = 14，禁提交）
```

| 量 | 值 | 备注 |
|---|---:|---|
| 逐文件条目（`-uall`） | **3747 → 3753**（两次读） | M 101 → 102，?? 3646 → 3651 |
| 目录折叠口径 | 535 行 | 同一状态，**不可与 3747/3753 混引** |
| 暂存 / 删除 | 0 / 0 | — |
| 跟踪锁 / 未跟踪锁 | 0 / **14** | 14 = app、core×7、feature×5、`settings-gradle.lockfile`；KD-72 仍 open（转录：`docs/research/2026-11-05-release-gates.md:48` G-REL-19） |

- **工作树是活的（本轮实跑的直接证据）**：同一 ask 内两次全量读取差 **+6 条 / +1 M**。共享工作树上有他线在并发改动，**任何总数都不是稳定基线**；本批凡用计数的地方必须带读取时刻，或改用逐路径判据。这也解释了为什么同一命令的 `101 M` 与 `102 M` 都能复现——不是脚本 bug，是树在变。

M 的顶层分布（本轮 `git status --porcelain -uall | grep "^ M"`）：`core` 43、`docs` 40、`tools` 12、`app` 4、`knowledge-production` 1、根 `dynamic_gui_architecture_plan.md` 1、`.gitignore` 1。
`core` 内分布：`core/data` 25、`core/export` 8、`core/database` 5、`core/model` 4、`core/domain` 1。
`docs` 内分布：`research` 16、**`current` 7**、`history` 3、`superpowers` 2、其余 scattered 单文件。

- **跨批与时点差，均不可推**：批次 43 记录自述 3835（`…batch-43-record.md:39`）→ 本轮 3747→3753。工作树**变少**了约 88 条后又在同一 ask 内回升 6 条，说明期间既有清理（`tmp/`、`.agent_*` 类）也有并发写入，**不得**据此推断“本批没变脏/变干净”。
- 未跟踪大块（**第 1 次读**，`?? ` 前缀按顶层计；树是活的，数字只对该时刻成立）：`tmp/` **1380**、`.agent_crops_fill2` 129、`"tmp` 84、`.agent_fill_crops` 71、`.agent_crops_p0002v` 64…… 全是工作产物，提交时一律排除。
- **异常项**：`feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/nul` 是未跟踪条目（`git status` 原文 `?? …/nul`）。Windows 保留设备名做文件名，`ls` 在该目录**列不出它**（本轮实跑）。属工具误产物，**不得提交**，也不得当作源码改动。

---

## 二、schema 73 与请求 schema 19

| 证据 | 值 |
|---|---|
| DB 常量 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:142` → `internal const val STUDY_DATABASE_VERSION = 73`；`:248` 使用该常量 |
| 导出 JSON | `core/database/schemas/…StudyDatabase/73.json`（本轮 `python -I` 解析） |
| JSON 实测 | `version 73` / **`entities 92`** / `views 2` / `setupQueries 2` / `identityHash 1d086050204a0da1ed6b6e3299dd9733` / 文件 `sha256 c7e7586a4eaab6d7309e91f2b715a74229369d95bc11966cfe96a94755f39f1e` |
| 抽样表名 | `problem` / `problem_revision` / `practice_unit`（前 3 个） |
| 请求 schema | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:566` → `const val PAPER_EDIT_SCHEMA_VERSION = 19`；`:567` → `CURRENT_SCHEMA_VERSION = PAPER_EDIT_SCHEMA_VERSION` |

- 与批次 43 基线 §1.3 逐字同值（92/2/`1d086050…`/`c7e7586a…`）→ **schema 面自批次 43 起未漂移**。这是本轮唯一能给出的“跨批次一致性”证据（两次独立解析同一文件）。
- `docs/current/data-schema.md` 是 7 M 之一（他线），**不是**权威值来源；权威是代码常量 + 73.json。
- 本轮**未跑** `tools/ci/check_backup_plan_sync.py`（四方门）。它是只读脚本、非 Gradle，但本 ask 只要基线事实，且批次 43 §9.1 已跑过 exit 0；本文件不把它记成本轮结果。

---

## 三、热区（计划 §6 / ask 纪律口径）

```text
$ git status --porcelain -uall -- core/domain core/ui 'feature/*' docs/current settings.gradle.kts \
    app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt
 M core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt
 M docs/current/README.md
 M docs/current/architecture-contract.md
 M docs/current/data-schema.md
 M docs/current/implementation-status.md
 M docs/current/model-task-contracts.md
 M docs/current/product-contract.md
 M docs/current/release-gates.md
?? core/domain/gradle.lockfile
?? core/ui/gradle.lockfile
?? feature/{capture,library,profile,review,tutor}/gradle.lockfile
?? feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/nul
```

| 热区 | M 数 | 文件 / 说明 |
|---|---:|---|
| `core/domain` | **1** | `TutorJudgedReview.kt`（他线，批次 43 §6.2 已登记为共享面） |
| `core/ui` | **0** | `Theme.kt` **已回滚干净** |
| `core/export` | **8** | 全部是 **test**（`androidTest/MistakePdfExporterAigcInstrumentedTest` + 7 个 `src/test`）；**主码 0 M** → “只做已批准 hunk”的口径当前成立 |
| `feature/*` | 0 M | 仅 5 个未跟踪锁 + 1 个 `nul` 误产物 |
| `docs/current` | **7** | README / architecture-contract / data-schema / implementation-status / model-task-contracts / product-contract / release-gates |
| `settings.gradle.kts` | 0 | — |
| `Application`/`Root` | 0 | `SmartMistakeBookApplication.kt` 无命中；全树 `git status` 也无 `Application.kt`/`Root*.kt` M |

**批次 43 §9.2 的回滚已生效（本轮实跑核对）**：`core/domain/…/BackupRepository.kt`、`app/…/SecondaryScreens.kt`、`core/ui/…/Theme.kt` 三条路径 `git status` **均无输出（clean）**。

**6C-2 行为面（本轮实读，非转录）**：

- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt:166` → `val ALLOWED_MASKS: Set<Int> = linkedSetOf(9, 13, 15)`；KDoc `:162-165` 明写“`0` 与 `8` 刻意不在其中”。
- 不变量测试在树：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/backup/SixC2RulingInvariantTest.kt`。它**独立写死**期望值 `rulingAllowedMasks = setOf(9, 13, 15)`（`:44`），并做跨层逐字相等断言（`:47-60`，域层 `BackupSelection.ALLOWED_MASKS` vs 数据层 `SmbkSelectionPlan.allowedMasks`）。
- 结论：**mask=8 不提供 / mask=0 禁止 / 派生索引恒裁 三项在行为层与防漂移测试层都成立**；本批只可加断言，不可改行为（与 ask 的不可变清单一致）。

---

## 四、设备与 AVD

### 4.1 当前在跑的设备（本轮 `adb devices` 实跑）

```text
$ adb devices
List of devices attached
emulator-5562	device
emulator-5566	device
emulator-5570	device
emulator-5584	device
```

| serial | API | ABI | product | PAGE_SIZE | boot_id（本轮读） | uptime(s) |
|---|---:|---|---|---:|---|---:|
| emulator-5562 | 34 | x86_64 | `sdk_gphone64_x86_64` | **4096** | `05b7b5dc-47f9-4959-9939-4bc95aae52e7` | **325.58**（刚起） |
| emulator-5566 | 34 | x86_64 | `sdk_gphone64_x86_64` | **4096** | `6def72cc-e33c-49ce-a724-b9804849b701` | 54330.15（≈15 h） |
| emulator-5570 | **23** | x86_64 | `sdk_phone_x86_64` | **未测**（API 23 无 `getconf`，原文 `/system/bin/sh: getconf: not found`） | `3d3900aa-7e01-4582-9416-be4d78f704a3` | 20653.73 |
| emulator-5584 | 34 | x86_64 | `sdk_gphone64_x86_64` | **4096** | `5a34f877-606b-4e74-b7b8-84f6398faf9d` | 10279.19 |

对比批次 43 基线 §6.1 的 boot_id（`a0ebed2d…` / `8f54d45c…` / `01798878…`）：

1. **三台全部重启过**（boot_id 全不同）→ 批次 43 关于“5566/5584 未重启、冷启动结论不可复用”的警示**已过期**，但反过来意味着 5566 的 15 h uptime 与批次 43 的 15 h 不是同一段。
2. **多出第 4 台 `emulator-5570`（API 23）**，正是 KD-77（API 23 冒烟）需要的机型；批次 43 基线的 3 台里没有它。
3. **四台同时在线**：批次 42 D1/D5 记录的共享设备竞争条件**比批次 43 更重**。批次 43 §9.5 已裁定低负载门地板 0.475 > 绝对上限 0.30、本机不能判 PASS；再加一台只会更差。**本批任何 isolation/低负载结论不得在这四台上取得**。

### 4.2 AVD 与 16KB / arm64

```text
$ emulator -list-avds
api23_smoke  api23_smoke32  api23_smoke64  audit_user_visible_20261016
kb_test_device  kb_test_device_iso  ps16k_arm64  ps16k_x86_64  test_device
```

- 与批次 43 基线 §6.2 的 8 个相比**新增 `kb_test_device_iso`**（隔离副本机型）。
- `ps16k_x86_64` 配置实读：`abi.type = x86_64`、`tag.id = page_size_16kb`、`image.sysdir.1 = system-images\android-36\google_apis_ps16k\x86_64\`、`hw.ramSize = 1536M` → **可起**（宿主同架构）。
- arm64 本轮**重做了决定性探针**（60 s 超时）：

```text
$ emulator -avd ps16k_arm64 -no-window -no-audio -no-snapshot -read-only -no-boot-anim
INFO  | Android emulator version 37.1.11.0 (build_id 15917651) (CL:N/A)
INFO  | Found systemPath C:\Android\Sdk\system-images\android-36\google_apis_ps16k\arm64-v8a\
FATAL | Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host.
        System image must match the host architecture.
```

- **arm64 = BLOCKED（宿主架构级，本轮亲跑复现，非转录）**。镜像与 AVD 都在盘，x86_64 宿主上 QEMU2 拒绝启动。
- **16KB 运行时本轮 UNVERIFIED**：当前在线的四台全是 `PAGE_SIZE=4096` 或 API 23，`ps16k_x86_64` **未起**。批次 43 的 G-REL-15 PASS 绑的是 `emulator-5620` + APK `a5fa0c4f…`（转录：`docs/research/2026-11-05-release-gates.md:44` G-REL-15、`:53` G-REL-24），**不可当作本轮或本批的证据**。

---

## 五、既有验收台账与验收基础设施（ask 指定核对项）

### 5.1 结论：**不存在**

```text
$ ls -d docs/acceptance                      → No such file or directory
$ for p in docs/acceptance docs/acceptance/ledger.md docs/acceptance/dod.md docs/acceptance/verdicts \
      tools/ci/acceptance_report.py tools/ci/acceptance_lint.py tools/ci/trace_requirements.py \
      tools/ci/metamorphic_checks.py tools/ci/screenshot_baseline.py tools/ci/a11y_audit.py \
      tools/ci/acceptance_ci.patch
  → 11/11 全部 ABSENT
$ ls tools/ci | grep -iE "accept|metamorph|screenshot|a11y|trace|verdict|ledger|dod"  → 0 命中
$ git check-ignore -v docs/acceptance/ledger.md   → exit 1（未被忽略，新建即可被 git 跟踪）
```

- **计划 §1 D0 的 10 个交付物路径，一个都不存在**。D0 不是“做了一半”，是**零字节**。
- 仓库里叫 “acceptance” 的两份既有文件与本次无关，不要混用：`docs/kb-acceptance-2026-10-17.md`（知识库验收）、`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md`（KB 判定准则）。
- `build/agent-batch1x/**/verdicts/` 下的 `verdicts` 目录是**旧批次的测试夹具**，**不是**计划要的 `docs/acceptance/verdicts/`。任何“已有 verdicts”的说法都不成立。
- 现存可复用的只有通用门禁：`tools/ci` 共 24 个 `.py`，其中 **17 个**文件名含 `check/gate/report/verify`；`tools/tests` 有 **107 个 `.py`**（含 `__init__.py` 与夹具辅助）。这些**没有一条**做“PASS 需 ≥2 类独立证据 / 禁止 summary 当证据 / 禁止 UNVERIFIED 标 PASS”的判定。

### 5.2 计划自身的“1200 项”没有可核对的枚举（高危）

| 计划段 | 声明 | 可枚举性（本轮按文本逐条判） |
|---|---|---|
| §1 D0 | “共 100 项：上述 15 类 × 逐条子项展开，**见验收台账**”（`:34`） | **不可枚举**：台账不存在 → 100 项无载体，`见验收台账` 是悬空引用 |
| §2 A 功能旅程 | “10 条用户旅程 × 9 检查”（`:39`） | **可枚举 = 90**（10 条旅程、9 类检查都在文本里点名） |
| §2 B 数据完整性 | 迁移 72（`:42`）+ 备份 18 = 90 | **可枚举 = 90**，但第三条“并发/CAS/事务/恢复（逐项）”**没有预算**——72+18 已把 90 用完，第三类只能溢出或无位 |
| §2 C 视觉与渲染 | 六原语 × 5 关系 = 30（`:47`），其余 5 类无计数 | 可枚举 ≈ 30，**60 项无清单** |
| §2 D–J | 各为一段能力清单，**无逐项计数**（`:50-68`） | **不可枚举**：7 个域 × 90 = **630 项无清单** |
| §3 修复与加固 | “100 项”（`:70`） | **不可枚举**：只有严重度分档的散文 |
| §4 门禁与终裁 | “100 项”（`:74`） | **不可枚举**：只有“每域独立复核 + 对抗复核 + 主会话终裁”的散文 |
| §5 计数 | 100+900+100+100 = **1200**（`:80`） | **算术成立**（本轮核算 1200），但它是对**未枚举总量**的求和 |

- **可枚举下限**：A 90 + B 90 = **180**；加上 C 的 30 也只有 210。即 **≥1020 项（85%）在今天没有可指认的条目**。
- 计划 §7 要求“1200 项逐项有 verdict，**无空项**”（`:93`），而台账不存在、绝大多数条目未枚举 → **该验收条件在当前状态下不可判定**。它不是“本轮没做”，是“还没有能数的对象”。
- 这是**连续第四批**计划计数缺陷（41: 55 vs 62、42: 74 vs 90、43: 74 vs 78；本批从“写错数字”升级为“总量无枚举”）。建议 D0 第一步不是写工具，而是先把 1200 行台账逐条枚举出来，再谈 lint。

---

## 六、40/41/42/43 已知假绿清单（lint 回放的输入集）

> 用途：计划 D0 item 15「用历史批次 41/42 数据跑 lint，必须能抓出已知假绿」。
> **本轮结论：回放现在跑不了**——`acceptance_lint.py` 不存在（§5.1），也没有机器可读的夹具格式；下列清单只能以“记录文本 + 证据目录”形式提供。条目全部来自本轮实读的既有记录，**未在本轮重新执行**。

### 6.1 清单

| 批 | 假绿形态 | 原始“绿”的说法 | 为什么是假绿（判定依据） | 出处（本轮实读） | lint 规则 |
|---|---|---|---|---|---|
| 40 | **旧 APK 当当前树** | “schema 73 设备全绿（16/16、backup 34/34…）” | 设备绿绑 10:13 旧 APK，其 73.json 是 **93 表 / `aa9f0313`**；当前树 **92 表 / `1d086050`**。产物与树不同源 | `2026-11-02-batch-40-record.md:67`、`:100`、`:133`、`:238` | R-ART：证据必须带 APK sha + 树 HEAD，且与 73.json hash 自洽 |
| 40 | **单臂绿计入总数** | “27 绿” | isolation 只有单臂、无配对/控制轮，被列进 27 绿头条 | 同上 `:251`；`§8 S7`（同文件 `:293`）、`S19`（`:305`） | R-SKIP：`-4`/assumeTrue/单臂一律不得计 PASS |
| 40 | **集成轨 summary 虚报** | “93 表 / 73 注册齐全” | 依赖已回滚的 L2；当前树实读为 92/无 L2 | 同上 `§8 S3`（`:289`） | R-SUMMARY：summary 不得作为证据来源 |
| 41 | **缺方法假绿** | `PAIRED_RED_SET_IDENTICAL` 判配对成立 | `isolation_pair_report` 对 OFF 臂缺失方法不判 SKIP/UNKNOWN，一臂少跑仍判 IDENTICAL | `2026-11-03-batch-41-record.md:318`（F7） | R-SKIP + R-METHOD：比较前必须校验方法全集 |
| 41 | **控制标记只比源码** | `t4_control_marker verify` exit 0 | verify 只比源码树，不比**已安装 APK**；两臂还共用同一 COMPONENT | 同上 `:319`（F8） | R-ART：身份必须取自安装包 |
| 41 | **空 sha 整轮剔除** | “n=20 轮” | `summarize_kd76` 把空 per-round sha 整轮静默剔除；bootCount 无门 → 同 boot 内 force-stop 可冒充 20 轮 | 同上 `:321`（F10） | R-COUNT：样本数必须用“有效轮”口径，缺失轮计删失 |
| 41 | **阈值可覆盖 / 旧入口** | “20 轮判词” | 单进程 20 轮旧入口仍在，`Kd76ReportVerdict` 接受 `requiredSamples/gateMillis` 覆盖 | 同上 `:322`（F11） | R-PARAM：判词函数的阈值只可来自常量 |
| 42 | **修复即绿（未演练）** | F7/F8/F10/F11/F12 已“修” | 修复件结构 fail-closed，但**运行时未演练**（两臂方法齐的场景没跑过） | `2026-11-04-batch-42-record.md:425-429` | R-EXERCISE：修复项必须有一次“原本会绿、现在会红”的实跑 |
| 42 | **无 exit code 的绿** | “6 组 exit 0” | 聚合行是 `TARGETED RESULT: FAIL`；日志无 exit code 行 → 只能读到任务级 | 同上 `:418`（S19） | R-EXIT：PASS 必须引用显式 exit code |
| 42 | **命令行不可复现** | WS4 §9 的配对命令 | 本轮复跑该命令行 → `TRUE_EXIT=2`，缺 `--expected-methods` | 同上 `:402`（S3） | R-REPRO：证据必须含可复跑命令与退出码 |
| 43 | **`OK (n)` 与 `-4 ×15` 并列** | “套件 OK” | `-4` 是 `assumeTrue` 跳过，15 例零执行 | `2026-11-05-batch-43-record.md:11`、`:364` | R-SKIP：跳过数必须从绿色分子里扣除 |
| 43 | **6 份 APK 跨轨合并** | “设备全绿” | 同一批 6 个 APK（`ec135844…`/`028eb07e…`/`119812ff…`/`1aee470a…`/`9b832f96…`/`a5fa0c4f…`），无一份绑最终树 | 同上 `:51`、`:272` | R-ART：跨 APK 数字禁止合并 |
| 43 | **p09 单次结论** | “已修 / 必红” | WS4 三次 712/933 红、2046 绿；设备轨 3 次 2 台同断言失败 → 两轨相反 | 同上 `:365`、`:54`（S10） | R-TRACK：冲突轨结论不得单边引用 |
| 43 | **两份单测日志结论相反** | `build/batch43-unit.log` = `BUILD SUCCESSFUL in 3m 2s`（08:23） | 权威门日志 `build/main-session-gate-batch43-unit.log`（13:37）= **`1642 tests completed, 8 failed` / `BUILD FAILED`**。把前者当“单测绿”就是假绿 | 本轮实跑 `grep` 两份日志（§10） | R-EXIT + R-GATE：同名族多份日志必须指名哪一份是门 |
| 43 | **“不存在”的陈述与盘上相反** | §6.3 写“修复环后的全量强门：**无** `batch43-strong-postfix.log`” | 该文件**在盘**（12:57:35，1012 行），内含 7/7 轮 `exit 0`（4 模块 `--rerun` + 2 flavor androidTest + `assembleLocalFirstDebug`） | `…batch-43-record.md:471`（同判亦见 `:237` PR11）vs 本轮实读 `build/batch43-strong-postfix.log` 尾部汇总 | R-DISK：任何“文件不存在/无产物”的断言必须对盘复核 |

### 6.2 回放可行性（本轮判定）

| 需要 | 现状 | 结论 |
|---|---|---|
| lint 执行器 | `tools/ci/acceptance_lint.py` **不存在** | 回放**不可执行** |
| 机器可读夹具格式 | 无；清单只存在于 4 份记录的散文表格 | 需先定义 `{case_id, claim, evidence_ref, expected_verdict, rule}` |
| 可复现的原始输入 | 4 份记录（共 ~220 KB）+ 4 个证据目（13/17/12/14 件）在树 | **可用**，足以构造 **15 条**回放用例（§6.1 表行数，本轮 `awk` 实数） |
| 预期输出 | 每条历史假绿必须被判 `FAIL`（或 `REJECTED_EVIDENCE`） | 需在 lint 里落成断言，否则“跑过 lint”无法自证 |

> 最硬的一条回放用例是 43 的“两日志相反”：同一批、同名族、一份绿一份红。任何 lint 若在这条上判 PASS，就证明它只是读了第一个 `BUILD SUCCESSFUL`。

---

## 七、真实 Provider / 签名 / arm64 / 人工金标

| 面 | 本轮证据（实跑/实读） | 状态 |
|---|---|---|
| **签名** | `app/build.gradle.kts:125-128` 四个 `System.getenv("RELEASE_*")`；`:147-150` 缺失即登记缺项；`:72`/`:82` debug 用 `debug`、`:91` release 用 `release` 配置。仓内 **无** `*.jks`/`*.keystore`（`ls` 无输出、`git ls-files` 0）；`local.properties` **只有 `sdk.dir` 一个键**；`env | grep ^RELEASE_` **无输出（4/4 未设）** | **BLOCKED（生产签名）**：本机无法产出生产签名包。与批次 43 记录 §6.1-2 一致 |
| **真实 Provider** | 模型密钥只在运行时经 `AndroidKeystoreModelSecretVault` + `DataStoreModelConfigurationStore` 存取（`core/data/.../settings/`）；源码内**无** `System.getenv("*API_KEY")` 命中；`sk-…`/`AIza…` 字面量扫描 **0 个文件命中**（启发式，非完整扫描器） | **BLOCKED（真实 Provider）**：需用户/发布者提供凭据；仓内零凭据符合约束。注意 `G-REL-29` 秘密扫描仍 **NOT_RUN**（转录：release-gates `:58`），不要把本轮的启发式扫描当完整结论 |
| **arm64 16KB** | 本轮重跑 AVD 探针 → `FATAL | … 'arm64' is not supported … on x86_64 host`（§4.2 原文） | **BLOCKED（宿主架构）**，非配置问题 |
| **16KB 运行时（x86_64）** | 在线四台无 16384 页；`ps16k_x86_64` 未起 | **UNVERIFIED（本轮）**；历史 PASS 绑 `emulator-5620` + `a5fa0c4f…`，不可外推 |
| **人工金标（AUD-08）** | 需 ≥2 标注员 + 1 裁定者；门值 **0.5714 < 0.9 → FAIL 维持**（转录：`…batch-43-record.md:578`） | **BLOCKED + FAIL** |
| **发版就绪** | 版本门/签名/arm64/质量门四项硬阻塞（转录：`…batch-43-record.md:577`）；本轮直接证实签名与 arm64 两项，质量门一面：当前权威单测日志红（§6.1 第 13 行） | **不成立**（维持批次 43 裁定） |

---

## 八、基线缺陷与风险清单

| # | 发现 | 证据 | 严重度 | 处置建议 |
|---|---|---|---|---|
| **F1** | 计划 1200 项里 **≥1020 项无可枚举载体**；`§1` 的“见验收台账”指向不存在的文件；`§7` “逐项有 verdict、无空项”因此**不可判定** | §5.2 逐段表；`plan:34,39,42,47,70,74,80,93` | **高（判据）** | D0 第一动作 = 把 1200 行台账**先枚举**（哪怕先列 ID/域/检查点/证据位），再做工具。否则本批会重演前三批“总数写得好、条目数不出” |
| **F2** | D0 的 **10 个交付物路径 0 存在**；`docs/acceptance` 整个目录不存在 | §5.1 的 11 条 `ABSENT` | **高（能力缺）** | 本批必须**新建**，不是“补齐”。新建时注意：`docs/acceptance` **未被 gitignore**（`git check-ignore` exit 1），会直接进工作树/提交面 |
| **F3** | 历史假绿回放**无执行器、无夹具格式**；15 条清单只在散文里 | §6.2 | **高** | 先定夹具 schema（§6.2 表），再把 §6.1 的 15 条落成 JSON；断言“全部判 FAIL” |
| **F4** | 热区 `core/domain` 有 **1 M**（`TutorJudgedReview.kt`），`docs/current` **7 M** | §3 表 | **高（纪律）** | 本批一律排除；提交时逐 hunk。批次 43 §9.2 的三文件回滚**已生效**（本轮核对），不要再“顺手回滚” |
| **F5** | 批次 43 记录有**两条与盘上相反**的陈述：① §6.3/PR11 “无 strong-postfix 日志”（文件在，7/7 exit 0）；② §6.1-1 “HEAD 不可编译”（§9.3 已自我更正为误判） | §6.1 末两行；`…batch-43-record.md:471`（`:237`）vs `:552` | **中（证据污染）** | 本批**继承前先复核**：凡“不存在/不可编译/未跑”类断言，按 §6.1 的 R-DISK 规则对盘复验 |
| **F6** | 单测门存在**同族两份相反日志**；测试 XML 已被覆盖，逐例结果不可回读 | §6.1 第 13 行；`…batch-43-record.md:468` | **中** | 本批门禁必须**一次写一份**、带显式 `exit` 行与 XML 归档路径；不得事后命名歧义 |
| **F7** | 设备面：**4 台在线**（新增 API 23 的 5570）、三台 boot_id 全变、5562 刚起 325 s | §4.1 | **中（可复现性）** | 设备结论必须绑 `serial + boot_id + APK sha + 次数`；低负载窗本机仍不可判（四台更差） |
| **F8** | 计划 §2 B 的 90 项被“迁移 72 + 备份 18”**刚好用满**，第三类“并发/CAS/事务/恢复”无预算 | `plan:42-44` | **低（口径）** | 枚举台账时显式给并发类分配编号，或把 B 改为 72+18+并发（>90）并登记总量变化 |
| **F9** | 工作树卫生：`feature/tutor/…/nul` 未跟踪误产物、`tmp/` 1380 条、14 个未跟踪锁、已跟踪锁 0 | §1.2；`release-gates.md:57`（KD-72 open） | **低** | 提交排除；锁文件问题按 KD-72 另行处理，**不得**为“让门绿”而提交锁 |

---

## 九、未验证 / 阻塞（不得写成通过）

| 项 | 状态 | 原因 |
|---|---|---|
| 远端 `origin/main` 此刻仍等于 `26f2327b` | **UNVERIFIED** | 未 fetch（git 写被禁）；`.git/FETCH_HEAD` = 2026-11-01 16:02 |
| 12 模块单测门 / 强门（本批） | **UNVERIFIED（本轮未跑）** | 纪律：Gradle 只在门禁阶段串行 |
| 任何设备用例执行 | **UNVERIFIED（本轮未跑）** | 未执行 `am instrument`；§4 只有设备属性，无测试结果 |
| 16KB 运行时（16384 页） | **UNVERIFIED** | 在线四台无 16KB；`ps16k_x86_64` 未起 |
| arm64 运行时 | **BLOCKED** | 本轮 `FATAL` 原文复现（宿主架构） |
| 真实 Provider 调用 | **BLOCKED** | 无凭据；源码/仓内零凭据（符合约束） |
| 生产签名 / Play App Signing | **BLOCKED** | `RELEASE_*` 4/4 未设、无 keystore |
| AUD-08 人工金标 | **BLOCKED** | 需独立标注员 + 裁定者 |
| G-REL-29 秘密扫描（完整结论） | **UNVERIFIED** | 无扫描器；本轮只做启发式字面量扫描（0 命中）与“无跟踪密钥文件” |
| 1200 项逐项 verdict | **不可判定** | 台账不存在、≥1020 项未枚举（F1） |
| 历史假绿 lint 回放 | **未执行** | 执行器不存在（F3） |
| 6C-2 不变量测试的红相 | **UNVERIFIED** | 需改行为才能变红，本批禁止 |
| p09 定性 | **UNVERIFIED（两轨相反，未重跑）** | 批次 43 已裁定“不单边裁定” |
| 工作树跨时点计数的可推性 | **不可推** | 3835（批次 43）→ 3747（本轮），期间有清理，无法归因 |

---

## 十、本轮实际执行的命令清单（可重跑）

```bash
git rev-parse HEAD; git rev-parse origin/main; git rev-list --left-right --count HEAD...origin/main
git log --oneline -5; git branch -a --contains HEAD; ls -l .git/FETCH_HEAD
git status --porcelain; git status --porcelain -uall | wc -l
git status --porcelain -uall | cut -c1-2 | sort | uniq -c
git diff --cached --name-only | wc -l
git ls-files "*gradle.lockfile" | wc -l
git status --porcelain -uall -- core/domain core/ui 'feature/*' docs/current settings.gradle.kts
git status --porcelain -uall -- core/export
git status --porcelain -uall -- app/.../SecondaryScreens.kt core/ui/.../Theme.kt core/domain/.../BackupRepository.kt
git status --porcelain -uall | grep "^ M" | cut -d/ -f2 | sort | uniq -c
grep -n "STUDY_DATABASE_VERSION" core/database/.../StudyDatabase.kt
python -I -c "json.load(.../StudyDatabase/73.json); print(version/entities/views/setupQueries/identityHash/sha256)"
grep -n "PAPER_EDIT_SCHEMA_VERSION|CURRENT_SCHEMA_VERSION" core/model/.../ModelTasks.kt
ls -d docs/acceptance; ls docs/acceptance/*; ls tools/ci | grep -iE "accept|metamorph|screenshot|a11y|trace|verdict|ledger|dod"
for p in <11 个 D0 路径>; do [ -e "$p" ] && echo EXISTS || echo ABSENT; done
git check-ignore -v docs/acceptance/ledger.md
adb devices; adb -s <S> shell "getprop ro.build.version.sdk; getprop ro.product.cpu.abi; getconf PAGE_SIZE;
    cat /proc/sys/kernel/random/boot_id; getprop ro.product.name; cat /proc/uptime"
emulator -list-avds
grep -E "abi.type|image.sysdir.1|tag.id|hw.ramSize" ~/.android/avd/ps16k_x86_64.avd/config.ini
emulator -avd ps16k_arm64 -no-window -no-audio -no-snapshot -read-only -no-boot-anim   # FATAL 原文
grep -nE "RELEASE_|storeFile|signingConfig|keyAlias" app/build.gradle.kts
cut -d= -f1 local.properties; env | grep -E "^RELEASE_" | sed 's/=.*/=<set>/'
grep -rIl --include=*.kt --include=*.kts -E "sk-[A-Za-z0-9]{20,}|AIza[0-9A-Za-z_-]{30,}" app core feature gradle
grep -nE "BUILD (SUCCESSFUL|FAILED)|tests completed|GATE RESULT" build/batch43-unit.log
grep -nE "BUILD (SUCCESSFUL|FAILED)|tests completed" build/main-session-gate-batch43-unit.log
tail -15 build/batch43-strong-postfix.log; grep -cE "^> Task" build/batch43-strong-postfix.log
```

> 本轮**未**执行：任何 Gradle、任何 `am instrument`、任何 APK 构建/安装、任何 git 写（含 fetch/commit/stash）、任何 emulator 启动、任何 Provider 调用。

---

## 十一、体量自查与回链

- 体量（本轮落盘后 `wc -lc` 实测）：**354 行 / 32,791 B**。字节远低于 64 KiB；行数低于批次 43 基线的 460 行，也不触 400 行上限。
- 回链：计划 `.zcode/plans/plan-2026-11-05-batch-44-acceptance.md`（§1 D0、§2 十域、§3 修复、§4 门禁、§5 计数、§6 硬约束、§7 验收）；上一批 `docs/research/2026-11-05-batch-43-record.md`（§9.7 收口裁定）；`docs/research/2026-11-05-batch-43-baseline.md`（§1.3/§4/§6 对照值）；`docs/research/2026-11-05-release-gates.md`（G-REL-00/09/15/16/19/29）。
- 不可变清单：`core/domain/.../BackupSelection.kt:166`（`{9,13,15}`）、`core/data/src/test/.../SixC2RulingInvariantTest.kt:44,47-60`、`docs/research/2026-11-04-6c2-rulings.md`。
- 本文件不替代主会话裁定。可写的基线事实只有三句：
  1. **树与 schema 未漂移**（HEAD `26f2327b` = 本地 origin/main；73/92/2/`1d086050…`；请求 schema 19；6C-2 行为面 `{9,13,15}`）。
  2. **验收机制为零**：`docs/acceptance` 与 D0 全部 10 个路径不存在，1200 项里 ≥1020 项无枚举，历史假绿回放无执行器（F1/F2/F3）。
  3. **外部面维持 BLOCKED**：arm64（本轮 FATAL 复现）、生产签名（`RELEASE_*` 4/4 未设）、真实 Provider（零凭据）、人工金标；发版就绪不成立。
