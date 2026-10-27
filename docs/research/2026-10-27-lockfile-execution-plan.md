# 锁文件重生成执行预案（KD-72 驱动，批次 26 R3/T3）

> 线：R3 锁执行预案｜日期：2026-10-27｜落点：本文件
>
> **本文是什么**：把批次 25 R3 §4 的前置清单（`docs/research/2026-10-27-lockfile-clean-tree-check.md`，下称 **R3**）转成
> 门禁轨可逐条照跑的分步预案。机制论证、官方口径、外部检索结论**一律引用 R3 不重复**；
> 本文只补三样：T0 窗口判定程序、两个分支的逐步命令＋判据＋失败归因＋回滚、负控的具体目标。
>
> **本轮实际执行过的**：只读 git（log/show/cat-file/status/diff --cached/ls-files）、
> `python tools/ci/check_lockfiles.py`（exit 0）、`python tools/ci/check_dependency_pins.py`（exit 0）、
> 仓库文件实读。**未运行 Gradle 的任何任务；未做任何 git 写操作；未改任何 build 文件。**
> 本文 §2/§3 里所有 Gradle 命令均由主会话门禁轨脚本串行执行，本线只产出预案（纪律：不运行 Gradle）。
> 所有"执行后会怎样"的结论凡未经实跑的，沿用 R3 的标注口径（待验证）并在 §5 汇总。

---

## 0. T0 实测快照（2026-10-27 本轮，窗口判定输入）

本轮实跑命令与结果（全部只读）：

| # | 命令 | 结果 |
|---|---|---|
| S1 | `git log --oneline -1` | HEAD = `405c8373`（批次 25 收口，与计划 §0 一致） |
| S2 | `git diff --cached --name-only \| wc -l` | **0**（R3 §0 的 01:01 暂存已清空，与批次 25 记录 §30 吻合） |
| S3 | `git status --porcelain=v1 -- '*.lockfile'` | 14 个 `??`：13 项目锁（含 `core/markdown/gradle.lockfile`）+ `settings-gradle.lockfile`；无一被跟踪 |
| S4 | `git ls-files '*gradle.lockfile'` | 空（HEAD 无跟踪锁） |
| S5 | `ls benchmark/gradle.lockfile` | 不存在 |
| S6 | 六文件入库判据（详 §1 J1，本轮实跑） | **不满足**：HEAD `settings.gradle.kts` 无 `:core:markdown` include（grep exit=1）；HEAD `core/ui/build.gradle.kts:30` 仍是 `implementation(libs.richtext.commonmark)`；HEAD `core/export/build.gradle.kts` 无 serialization（grep exit=1）；HEAD 无 `core/markdown/build.gradle.kts`（cat-file exit≠0）；工作树 5 个 ` M` + `?? core/markdown/` |
| S7 | `git status --porcelain=v1 \| wc -l` 及分类计数 | **674**（201 ` M` + 473 `??`）——严格"干净树"不满足 |
| S8 | `python tools/ci/check_lockfiles.py` | **EXIT=0**，提示"尚未落地……工作区存在 13 个未跟踪锁文件（例如 app/gradle.lockfile），不能原样提交；缺锁 project：:benchmark"（skip 模式，`check_lockfiles.py:175-193`） |
| S9 | `python tools/ci/check_dependency_pins.py` | **EXIT=0**（R3 §4.1.5 预检当前为绿） |
| S10 | `git status --porcelain=v1 -- gradle/libs.versions.toml core/model/build.gradle.kts` | 空（负控目标文件当前干净，§2-A5 的恢复安全性前提，执行时须复验） |

**T0 窗口判定（本轮时点）：分支 B——窗口不可行。** 六文件未入库（S6）且整树未收口（S7）。
R3 §0 的阻塞级发现（他线暂存锁不得原样提交）当前无暂存态（S2），但其结论继续有效：磁盘上这 14 个锁
是批 23 旧产物，与 HEAD 解析图不符，任何分支下都**不得**被 `git add`/提交（R3 §0；批次 26 计划 §0 判红条款）。

---

## 1. T0 窗口判定程序（每次执行前先跑，全部只读，<1 分钟）

门禁轨在动任何锁之前按序跑 J1–J4，逐条给判定；全绿 → 进 §2 分支 A，任一红 → 进 §3 分支 B。

```bash
cd <仓库根>

# ---- J1 六文件入库判据（工作树与 HEAD 的解析图一致）----
# J1a 六文件工作树干净（无 M / ??）
git status --porcelain=v1 -- settings.gradle.kts core/ui/build.gradle.kts \
  feature/tutor/build.gradle.kts core/markdown/build.gradle.kts \
  core/export/build.gradle.kts .gitignore
# 期望：空输出
# J1b core/markdown 模块与 include 已在 HEAD
git cat-file -e HEAD:core/markdown/build.gradle.kts && echo "J1b-md=OK"
git show HEAD:settings.gradle.kts | grep -q '":core:markdown"' && echo "J1b-include=OK"
# J1c HEAD 不再用 richtext-commonmark、HEAD 已含 serialization
git show HEAD:core/ui/build.gradle.kts | grep -q 'richtext.commonmark' \
  && echo "J1c-richtext=RED" || echo "J1c-richtext=OK"
git show HEAD:core/export/build.gradle.kts | grep -q 'kotlinx.serialization.json' \
  && echo "J1c-serialization=OK" || echo "J1c-serialization=RED"
# J1 判据：J1a 空 + 三个 OK + 无 RED。对应 R3 §4.1.2（6 文件入库）；为什么不只数"文件在 HEAD"：
# 五个文件 HEAD 里本来就有，必须验"新声明已在 HEAD"（R3 §1.1 表的 diff 内容）。

# ---- J2 暂存区无锁判据 ----
git diff --cached --name-only | grep -i 'lockfile'
# 期望：无输出（grep exit 1）。有输出 → 他线又在暂存锁（R3 §0 情形重演）：
# 先按 R3 §4.1.1 协调主会话裁定，不得直接 unstage 他线在途提交。

# ---- J3 干净树判据（R3 §4.1.3 最严口径；KD-72 台账 docs/known-defects.md:2740 "干净、待提交的树"）----
git status --porcelain=v1 | wc -l
# 期望：0。非 0 → 未收口（等；或按主会话书面放宽口径，但放宽须逐条记录在案）。

# ---- J4 环境与预检 ----
python tools/ci/check_dependency_pins.py    # 期望 EXIT=0（quickjs 1.0.15 / stdlib 2.3.21）
java -version 2>&1 | head -1                # 期望 21.x（R3 §4.1.4）
ls "$ANDROID_HOME/platforms" 2>/dev/null | grep -q android-37 && echo "SDK37=OK" \
  || cat local.properties 2>/dev/null | grep sdk.dir
# SDK 判据放宽说明：本机批 23 生成实跑成功过（R3 §2.1，2m55s），环境在；仅当机器更换时从严核 SDK 37/build-tools 37。
```

**分支选择**：J1–J4 全绿 → 分支 A；J1 红 → 等 W1；J2 红 → 先处置 W3；J3 红 → 等 W2；
J4 红 → 环境修复（不属于等待清单）。本轮实测：J1 红、J2 绿、J3 红（674 条）、J4 绿 → **分支 B**。

---

## 2. 分支 A：窗口可行——完整执行序列（门禁轨执行）

前置：§1 J1–J4 全绿；执行期间不再有他线并行改动（若 §1 快照与 §2 各步之间树发生变化，回 §1 重跑）。
每步含：命令、判据、失败归因（本批/他线/环境）、回滚。机制依据见 R3 §4.2（此处不重复论证）。

### A0 前置自查

```bash
git status --porcelain=v1 | wc -l        # 期望 0（=J3）
git log --oneline -1                     # 记录执行起点 HEAD，写入证据
python tools/ci/check_dependency_pins.py # 期望 EXIT=0（=J4）
```

判据：三条全过。失败：树又动了 → 回 §1 重判（他线归因）；pins 红 → 环境归因，停。

### A1 清掉 14 个陈旧锁（unstage + 删除）

```bash
# 若 J2 曾红且主会话已裁定可动：
git restore --staged -- '*.lockfile' 2>/dev/null   # 当前 staged=0，此步为幂等保险（R3 §4.2 步骤1）
rm -f app/gradle.lockfile core/*/gradle.lockfile \
      feature/*/gradle.lockfile benchmark/gradle.lockfile settings-gradle.lockfile
# 判据（两条都要）：
git status --porcelain=v1 -- '*.lockfile'          # 期望：空
find app core feature benchmark -maxdepth 2 -name 'gradle.lockfile'
test ! -e settings-gradle.lockfile && echo "OK-no-settings-lock"
# 期望：find 无输出 + 打印 OK-no-settings-lock
```

- 判据：磁盘零锁、暂存区零锁、`git ls-files '*gradle.lockfile'` 仍为空（从未跟踪过，不应有输出）。
- 失败归因：rm 有残留 → 本批命令漏路径（检查是否有新模块目录）；`git restore --staged` 报错且 J2 曾红 → 他线在途，停，回主会话。
- 回滚：无需回滚——删除的都是未跟踪的再生性产物（R3 §0 已裁定不可提交），重跑 A2 即可重建。
- 跨线触碰注记（分支 A 无此问题）：分支 A 下 core/markdown 已入库（J1b），不再是热区；
  分支 B 的子集预演若要删 `core/markdown/gradle.lockfile`，见 §3.3 的批准要求。

### A2 重生成（与 CI step 完全同参，`android-check.yml:81-82`）

```bash
./gradlew --no-configuration-cache --no-parallel --console=plain \
  lockAllDependencies --write-locks > tmp-lockfile-generation.log 2>&1
echo "EXIT=$?"
tail -5 tmp-lockfile-generation.log     # 期望 BUILD SUCCESSFUL
```

- 两个 CLI 覆盖是**必需**不是可选：`resolveLockableClasspaths` 声明了
  `notCompatibleWithConfigurationCache`（`build.gradle.kts:47`），而 `gradle.properties:2-3` 默认开
  configuration-cache 与 parallel——不带覆盖会在配置期/任务期失败。与 CI 同参保证本地产物可被 CI 步骤复现。
- 判据：EXIT=0 + BUILD SUCCESSFUL。时长参考批 23 实跑 2m55s（R3 §2.1）。
- 失败归因（R3 §4.2：构建失败时锁不落盘，官方口径；勿用 `--continue` 抢救）：
  - 解析期红（"Did not resolve / not part of the lock state / out of date"）→ **本批**归因：树不在 HEAD 解析图上，回 J1 复验；
  - 仓库下载超时/网络错 → **环境**归因，重试一次，再败停并留日志；
  - `notCompatibleWithConfigurationCache` 配置期错 → **本批**归因：CLI 覆盖漏写。
- 回滚：失败时锁可能部分落盘 → 重跑 A1 的 rm 后再重试（产物再生，无 git 状态变化）。

### A3 产物审计（15 锁 + 不变量 + 配置面）

```bash
# A3.1 数量：15 个 ??（14 项目锁 = 现有 13 模块——已含 core/markdown——再 + benchmark 新增；另 + settings 锁）
git status --porcelain=v1 -- '*.lockfile' | sort        # 期望 15 行 ??；settings-gradle.lockfile 必在列
# A3.2 行数非零
wc -l app/gradle.lockfile core/*/gradle.lockfile feature/*/gradle.lockfile benchmark/gradle.lockfile
# A3.3 四条内容不变量（R3 §4.2 步骤3 原样沿用；stdlib 2.3.21 的来源见 build.gradle.kts:73-83）
grep -c "org.jetbrains.kotlin:kotlin-stdlib:2.3.21" core/data/gradle.lockfile   # 期望 ≥1
grep -c "quickjs-kt-android"                        core/data/gradle.lockfile   # 期望 ≥1
grep -c "kotlinx-serialization"                     core/export/gradle.lockfile # 期望 ≥1（HEAD 已含该依赖=J1c）
grep -c "richtext-commonmark"                       core/ui/gradle.lockfile     # 期望 0（HEAD 已换 project 依赖=J1c）
# A3.4 benchmark 锁（R3 §2.2 判据）：存在且至少两条 classpath
test -f benchmark/gradle.lockfile && \
  sed -n 's/^.*=//p' benchmark/gradle.lockfile | tr ',' '\n' | sort -u
# A3.5 配置面审计 + 残余面登记（lint / release-KSP / kotlinCompilerClasspath，R3 §3.3）
for f in app/gradle.lockfile core/*/gradle.lockfile feature/*/gradle.lockfile benchmark/gradle.lockfile; do
  echo "== $f"; sed -n 's/^.*=//p' "$f" | tr ',' '\n' | sort -u
done
```

- 判据：A3.1 恰 15 行（若 `settings.gradle.kts` include 数与 14 不符，以 `grep -c '":' settings.gradle.kts`
  实数为准，期望 = include 数 + 1）；A3.3 四个数字全中；A3.4 benchmark 锁含
  `debugCompileClasspath`/`debugRuntimeClasspath` 两条（`com.android.test` 的门合格线，
  `check_lockfiles.py:100-104,121-129`）；A3.5 面尾见 R3 §3.2 预期矩阵，残余面照录进 KD-72 收口记录。
- 失败归因：锁数 14（缺 benchmark）→ R3 §2.1 缺锁成因复现，走 R3 §2.2 fallback ①单跑
  `:benchmark:dependencies --write-locks --no-configuration-cache` 定位，仍缺则**登记**（根构建脚本修复不在本批落点，
  需主会话裁定——R3 §2.2 fallback ②）；richtext/serialization 不变量反 → **本批**归因：跑的不是 HEAD 树（回 J1）；
  stdlib ≠ 2.3.21 → **本批**归因：`build.gradle.kts:73-83` 的 useVersion 未生效，查根构建是否被改。
- 回滚：审计不过 → 全部锁回 A1（rm），排除归因后重来；无 git 状态变化。

### A4 静态门

```bash
python tools/ci/check_lockfiles.py; echo "EXIT=$?"
# 期望：EXIT=0，输出 "OK: dependency locking 已落地：14 个 included project 都有覆盖测试/运行面的锁文件"（:223）
```

- 失败归因（按脚本错误行）：`存在但未入库` → 本批（A6 还没跑，正常，先做完 A5 回看）；`缺 <project>` →
  生成产物缺锁，回 A3.4 归因路径；`缺 *CompileClasspath/*RuntimeClasspath/unitTest/androidTest/test 配置` →
  resolveLockableClasspaths 未按预期覆盖（R3 §3.2 推导落空），照录缺口、登记、不放宽门断言（纪律：不许放宽断言）；
  `include 外的锁` → 多余锁文件，rm。
- 回滚：同 A3（rm 重建），门本身是只读脚本（`check_lockfiles.py:29`），无现场可坏。

### A5 负控 5a–5c + 恢复（KD-72 关闭判据的"负控"项；证明 DEFAULT 锁门真咬人）

**目标文件与备份纪律**：只动 `gradle/libs.versions.toml` 与 `core/model/build.gradle.kts`（两者本轮实测干净，
S10；执行时必须复验为空才继续）。恢复用**备份副本回拷**，不用 `git restore`——若执行中途他线使文件变脏，
`git restore` 会把他线改动一起抹掉（禁改他线内容）。

```bash
# 恢复安全性前置（红则停：负控改期）
git status --porcelain=v1 -- gradle/libs.versions.toml core/model/build.gradle.kts   # 期望空
mkdir -p .tmp   # .gitignore:9 已忽略
cp gradle/libs.versions.toml .tmp/bak-libs.versions.toml
cp core/model/build.gradle.kts .tmp/bak-core-model-build.gradle.kts
```

**5a 抬版**：改 `gradle/libs.versions.toml:11` `serialization = "1.9.0"` → `"1.8.0"`（改一个确定存在的旧版；
若上游无该版本会得到"仓库缺版本"型红而非锁型红——此时换一个确定存在的版本重试并记录）。

```bash
./gradlew --no-configuration-cache --no-parallel --console=plain :core:model:dependencies > .tmp/nc-5a.log 2>&1
echo "EXIT=$?"
# 判据：EXIT≠0 且 .tmp/nc-5a.log 含 lock state 字样的解析期错误（"Dependency lock state out of date" 或同族）。
# 无 --write-locks 是本步的全部意义：锁挡住漂移而不是被漂移更新。精确报错文本以实跑为准（R3 引官方口径）。
```

**5b 加依赖**：在 `core/model/build.gradle.kts` dependencies 块加一行 `implementation(libs.androidx.paging.common)`
（catalog 真实别名：`gradle/libs.versions.toml:82`，core/model 现图不含它；失败发生在解析期，不会走到编译）。

```bash
./gradlew --no-configuration-cache --no-parallel --console=plain :core:model:dependencies > .tmp/nc-5b.log 2>&1
echo "EXIT=$?"
# 判据：EXIT≠0 且日志含 lock-state 解析错误（多出的依赖不在锁状态，R3 §4.2 5b 句式）。
```

**5c 删依赖**：从 `core/model/build.gradle.kts:29` 删除 `implementation(libs.kotlinx.serialization.json)` 整行。

```bash
./gradlew --no-configuration-cache --no-parallel --console=plain :core:model:dependencies > .tmp/nc-5c.log 2>&1
echo "EXIT=$?"
# 判据：EXIT≠0 且日志含 "Did not resolve ... part of the dependency lock state"（锁里有的模块没被解析）。
```

**5d 恢复现场（必须做完才准进 A6）**：

```bash
cp .tmp/bak-libs.versions.toml gradle/libs.versions.toml
cp .tmp/bak-core-model-build.gradle.kts core/model/build.gradle.kts
git status --porcelain=v1 -- gradle/libs.versions.toml core/model/build.gradle.kts   # 期望空
# 负控全程无 --write-locks，锁不应被改；为消除一切疑问，仍按 R3 §4.2 5d 删单锁重跑全量生成：
rm -f core/model/gradle.lockfile
./gradlew --no-configuration-cache --no-parallel --console=plain \
  lockAllDependencies --write-locks > .tmp/regen-after-nc.log 2>&1; echo "EXIT=$?"    # 期望 0
git status --porcelain=v1 -- '*.lockfile' | wc -l   # 期望仍 15（数量与 A3.1 一致）
python tools/ci/check_lockfiles.py                  # 期望 EXIT=0
```

- 三条负控判据统一：**EXIT≠0 + 锁定状态相关报错**。误绿（EXIT=0）= 锁门不咬 = KD-72 不能关，
  如实登记（不许放宽断言或降级判据）；报错文本非预期句式但 EXIT≠0 → 记录实际文本回链，判定有效性由
  报错是否指向 lock state 决定。
- 失败归因：5a/5b/5c EXIT=0 → **本批**归因优先（改错文件/改错行/没触发 core:model 解析），排除后仍绿 →
  这是对锁门的**真实证伪**，停，登记，KD-72 保持 open；网络型红 → 环境归因，修网重试。
- 回滚：5d 即回滚；备份副本在 `.tmp/`，负控期间任何一步失败先回拷再讨论。

### A6 提交分块（≤6 路径/块；15 个锁分 3 块 ×5）

```bash
# 块1（5）
git add settings-gradle.lockfile app/gradle.lockfile core/model/gradle.lockfile \
        core/domain/gradle.lockfile core/database/gradle.lockfile
git commit -m "build(deps): 批次26——依赖锁入库(1/3): settings+app+core/{model,domain,database}"
# 块2（5）
git add core/data/gradle.lockfile core/export/gradle.lockfile core/ui/gradle.lockfile \
        core/markdown/gradle.lockfile benchmark/gradle.lockfile
git commit -m "build(deps): 批次26——依赖锁入库(2/3): core/{data,export,ui,markdown}+benchmark 首锁"
# 块3（5）
git add feature/tutor/gradle.lockfile feature/library/gradle.lockfile feature/review/gradle.lockfile \
        feature/capture/gradle.lockfile feature/profile/gradle.lockfile
git commit -m "build(deps): 批次26——依赖锁入库(3/3): feature×5"
# 终态判据：
git status --porcelain=v1 -- '*.lockfile' | wc -l    # 期望 0（全部入库）
git ls-files '*gradle.lockfile' | wc -l              # 期望 15（14 项目锁 + settings 锁）
python tools/ci/check_lockfiles.py                   # 期望 EXIT=0（已落地口径）
```

- 说明：≤6/块来自批次 26 计划 §2 纪律；15 锁无法单提交，分 3 块。**中间提交单独 checkout 时门会是红的**
  （首项目锁被跟踪后门即强制全覆盖，`check_lockfiles.py:21-27,195-207`）——但 CI 只对推送后的 HEAD 跑
  （`android-check.yml` 按 push/PR 触发），推送前双门在最终树上执行，中间态只影响 bisect 落点（红而不错）。
  若主会话要求原子单提交，属 ≤6/块纪律的例外，需主会话裁定，不在本预案内自定。
- 分块内不可混入任何非锁文件（尤其不得把 6 个 build 文件再动——分支 A 下它们已在 HEAD）。
- 失败归因：commit 被 hook 拒 → 按 hook 输出归因修，禁 --no-verify（纪律）；数量判据不中 → 本批漏 add，
  补齐后重跑终态三条。
- 回滚（推送前）：`git reset --soft HEAD~3`（保留文件改动，仅退提交），锁回到暂存区，按需
  `git restore --staged -- '*.lockfile'`。不做任何 `--hard`。

### A7 推送 + CI 双 step + P1 冒烟

```bash
git push        # 照旧纪律：非快进 merge；网络失败重试
```

- 判据（CI 两个 step 都绿，`android-check.yml:70-92`）：
  - "Dependency lockfile manifest and coverage gate"（:70-74，始终跑）→ exit 0，"已落地：14 个 included project"；
  - "Dependency lockfiles are up to date"（:75-92，`hashFiles('**/gradle.lockfile')` 命中后首次实跑，:78）→
    CI 内重跑同参生成后 `git status --porcelain -- '*.lockfile'` 为空（:86-92）。
    **该 step 此前从未在本仓实跑过（此前零跟踪锁，hashFiles 为空跳过）→ 首跑行为列入 §5 未验证。**
- P1 冒烟（Gradle #38057：锁定改变 runtime classpath 顺序，9.6.1 未修；R3 §4.2 步骤7）：锁入库后对
  release/benchmark/internal 变体各做一次装机冒烟（QuickJS/Room/ML Kit 路径）。设备轨承接执行；
  **KD-72 收口以其绿为条件**——若本批未跑，台账如实写"锁已入库、P1 冒烟待跑"，KD-72 保持 open（中间态登记，不许当已关）。
- 失败归因：CI 生成步骤红 → 比对 CI 日志与本机 `tmp-lockfile-generation.log`（本批：同参应可复现；
  不可复现 → 环境/ runner 归因）；P1 冒烟红（NoSuchMethodError/NoClassDefFoundError 且归因 AAR）→
  按 Gradle #38057 归因并回 R3 §6 来源链，锁门不放宽，处置走主会话裁定。

### A8 KD-72 收口

关闭判据（批次 26 计划 §2 + R3 §4.4 前序研究 §7，全部满足才关）：干净树重生成 15 锁入库 +
`check_lockfiles.py` exit 0 + 负控 5a–5c 三红 + CI 双 step 绿 + P1 冒烟绿 + 台账写明未覆盖面
（lint / release-KSP / kotlinCompilerClasspath，A3.5 实录）+ 记录回链。
执行：改 `docs/known-defects.md:2740` 起的 KD-72 条目（本批落点 ✓）+ 本预案执行证据落盘
`docs/research/`（命令、EXIT 码、负控报错原文、15 锁清单、配置面审计输出）。

### 2.A 分支 A 回滚矩阵（汇总）

| 阶段 | 已发生的事 | 回滚动作 | 不可逆点 |
|---|---|---|---|
| A0–A5 | 删了未跟踪锁；可能动过 2 个负控文件 | rm 重生成（A1+A2）；备份回拷（5d） | 无 |
| A6 已 commit 未 push | 3 个本地提交 | `git reset --soft HEAD~3`（保文件退提交） | 无 |
| A7 已 push | 远端出现锁提交 | 新增 revert 提交（**永不 force-push**，纪律） | 有（历史推进，只能向前修） |
| 任意时刻树被他人再变脏 | 解析图漂移 | 停 → 回 §1 重判 → 已生成未提交的锁 rm 重来 | 无 |

---

## 3. 分支 B：窗口不可行——最小等待清单 + 登记性预演

**本轮实测即此分支**（§0：J1 红、J3 红）。分支 B 下**不生成、不删除、不提交任何锁**是默认动作；
磁盘上现存 14 个陈旧锁维持原状（不得 add，见 §0）。

### 3.1 最小等待清单（判据即 §1 对应项，任一满足即消掉对应等待）

| 等待项 | 内容 | 判据（复跑即验） | 在等谁 |
|---|---|---|---|
| W1 | 6 个解析图 build 文件入库（R3 §4.1.2；core/markdown 模块+include+core/ui 换依赖同一提交面，R3 §6 P5） | §1 J1 全绿 | markdown 迁移线 / 6B-2 线各自的批次提交 |
| W2 | 整树收口（KD-72 口径"干净、待提交的树"，`docs/known-defects.md:2740`） | §1 J3 = 0 | 主会话按队列顺序逐批提交 674 条目 |
| W3 | 暂存区无锁（R3 §0 情形不再重演；若重演：先协调再 unstage，R3 §4.1.1） | §1 J2 无输出 | 他线提交行为 |

### 3.2 重评时机

每个批次 T0（开工）由门禁轨跑一遍 §1（<1 分钟，全只读）。J1–J4 全绿即切分支 A，
**不需要**等"所有批次结束"——J3 的 674 条里只有与解析图相关的 6 文件影响锁内容（R3 §1.1），
但整树口径（J3=0）是台账写明的关闭前提，未放宽前照等。

### 3.3 替代（可选，须主会话批准）：只生成可锁子集并登记

**目的与边界**：在等窗口期间把 R3 §5 的机制级 UNVERIFIED（resolveLockableClasspaths 实跑行为、
benchmark 缺锁成因、配置面覆盖矩阵、settings 锁内容是否受 include 影响）换成实测事实。
**它不满足任何 KD-72 关闭判据**（关窗判据要求全 15 锁 + 干净树 + CI），产出不得被记为锁进度，只能记为机制预演。

**硬约束**：① **一个锁都不提交**——首个项目锁被跟踪后 `check_lockfiles.py` 立即从 skip 切强制全覆盖
（`check_lockfiles.py:21-27`），缺 3 个模块的锁会直接判红（`:199-207`），把门推成永久红；
② 产出全部保持 `??`；③ 触碰 `core/markdown/gradle.lockfile`（删除）属他线热区目录内动作
（`core/markdown` 整目录在禁改清单），**必须主会话批准并在批次记录注明**，或保留该文件不动
（见命令块注）。

**前置**（全只读自查，任一红则放弃本预演，只做 §3.1）：

```bash
# 根构建面 + 目录 + 目录内全部非排除模块的 build 脚本干净（锁内容只由它们决定）
git status --porcelain=v1 -- build.gradle.kts settings.gradle.kts gradle/libs.versions.toml \
  gradle/wrapper/gradle-wrapper.properties gradle.properties \
  app/build.gradle.kts benchmark/build.gradle.kts \
  core/model/build.gradle.kts core/domain/build.gradle.kts core/database/build.gradle.kts core/data/build.gradle.kts \
  feature/tutor/build.gradle.kts feature/library/build.gradle.kts feature/review/build.gradle.kts \
  feature/capture/build.gradle.kts feature/profile/build.gradle.kts
# 期望：空。例外：feature/tutor/build.gradle.kts 当前为 M，但其 diff 仅 project 依赖（R3 §1.1 行3：
# project 依赖不进锁），可放行——判据：生成后 grep -c "project" feature/tutor/gradle.lockfile 仍为 0（锁格式本就不含 project）。
# 若主会话从严，把 feature/tutor 也加入 -x，子集降为 10 模块。
```

**命令**（R3 §4.3 分支 B 的 `-x` 形式，排除解析图未稳定的 3 个模块，每模块两个任务都排除）：

```bash
rm -f app/gradle.lockfile core/model/gradle.lockfile core/domain/gradle.lockfile \
      core/database/gradle.lockfile core/data/gradle.lockfile \
      core/ui/gradle.lockfile core/export/gradle.lockfile \
      feature/*/gradle.lockfile benchmark/gradle.lockfile settings-gradle.lockfile
# ↑ 先清全部旧锁防新旧混淆；core/markdown/gradle.lockfile 的删除需 §3.3 硬约束③的批准，
#   未批准则保留不动并在记录注明"预演不覆盖 core/markdown"。
./gradlew --no-configuration-cache --no-parallel --console=plain \
  lockAllDependencies --write-locks \
  -x :core:ui:dependencies -x :core:ui:resolveLockableClasspaths \
  -x :core:export:dependencies -x :core:export:resolveLockableClasspaths \
  -x :core:markdown:dependencies -x :core:markdown:resolveLockableClasspaths \
  > .tmp/lockfile-subset-preview.log 2>&1; echo "EXIT=$?"

# 产物判据：11 个新 ?? 项目锁（14 include − 3 排除）+ settings-gradle.lockfile，共 12 个 ??；
git status --porcelain=v1 -- '*.lockfile' | sort | wc -l      # 期望 12（markdown 旧锁保留时为 13，注明）
# 机制事实采集（登记材料，对应 R3 §5 的 UNVERIFIED）：
test -f benchmark/gradle.lockfile && sed -n 's/^.*=//p' benchmark/gradle.lockfile | tr ',' '\n' | sort -u
for f in app/gradle.lockfile core/*/gradle.lockfile feature/*/gradle.lockfile benchmark/gradle.lockfile; do
  echo "== $f"; sed -n 's/^.*=//p' "$f" | tr ',' '\n' | sort -u
done
grep -c "org.jetbrains.kotlin:kotlin-stdlib:2.3.21" core/data/gradle.lockfile   # 期望 ≥1
python tools/ci/check_lockfiles.py                      # 期望仍 EXIT=0（skip 口径：无任何已跟踪锁）
```

**登记**（写 `docs/known-defects.md` KD-72 Progress + `docs/research/` 证据文件）：EXIT 码、
benchmark 锁是否生成（R3 §2.1 假设 a/b 的实测裁决）、逐锁配置面 vs R3 §3.2 预期矩阵的出入、
残余面实录、settings 锁与批 23 版本逐字节 diff（验证 include() 不影响 settings 锁内容的推导）、
以及一句明示："子集预演不推进 KD-72 关闭"。

**回滚**：预演产物全部未跟踪 → `git status --porcelain=v1 -- '*.lockfile'` 里的 12（或 13）个 `??`
按需 rm 即回预演前；git 状态零变化；被删旧锁可再生（A2 同参）。登记文本保留（那是本轮的真实产出）。

---

## 4. 未验证清单（沿 R3 §5 口径，本文新增执行层各项）

1. **UNVERIFIED**：分支 A 全序列未执行（本线禁 Gradle）——A2 产物数、A3 四不变量、A4 消息文本、A5 三负控的实际报错文本，均以门禁轨实跑为准。
2. **UNVERIFIED**：5a 用 `serialization="1.8.0"` 的上游存在性（本线未联网核）；非锁型红（仓库缺版本）时换版本重试。
3. **UNVERIFIED**：CI "Dependency lockfiles are up to date" step 首次实跑行为（此前 hashFiles 恒空，:78 从未触发过）。
4. **UNVERIFIED**：R3 §5 已列各项（benchmark 缺锁成因、配置面矩阵推导、STRICT 探针安全性）继续有效，分支 A/预演产出即其裁决材料。
5. **待验证（单一来源→预演裁决）**：settings 锁内容不受 include() 影响（本预案推导；分支 B 预演或分支 A 的 A3.1 直接实测）。
6. **UNVERIFIED**：分支 A 假设执行期间无他线并行写树——各步判据内嵌复验（A0/J1），不成立即停。

## 5. 来源

**本轮实跑（可复跑）**：§0 表 S1–S10 的命令与输出；`git show HEAD:{settings.gradle.kts,core/ui/build.gradle.kts,core/export/build.gradle.kts}` 三处 grep/cat-file（S6）；`git status --porcelain=v1` 全树与六文件/target 文件窄查（S6/S7/S10）；`python tools/ci/check_lockfiles.py`、`python tools/ci/check_dependency_pins.py`（S8/S9）。

**仓库内（本轮实读）**：`build.gradle.kts:36-39,44-62,47,73-83,87-92`；`settings.gradle.kts:19-34`；`gradle.properties:2-3`；`gradle/wrapper/gradle-wrapper.properties`（9.6.1）；`gradle/libs.versions.toml:11,13,68-113`（:11 serialization 1.9.0、:82 androidx-paging-common）；`core/model/build.gradle.kts:28-31`；`tools/ci/check_lockfiles.py:21-27,100-104,115-130,169-172,175-193,195-219,223,226-241`；`.github/workflows/android-check.yml:70-92`（:78 hashFiles、:81-82 同参命令、:86-92 porcelain 判据）；`docs/known-defects.md:2740`（KD-72 条目）；`.gitignore:9`（.tmp/ 备份位）。

**引用不重复论证**：`docs/research/2026-10-27-lockfile-clean-tree-check.md`（R3）§0（暂存锁不得原样提交）、§1.1（六文件 diff 表）、§2.1-2.2（benchmark 成因与补法）、§3.1-3.4（配置面矩阵与审计）、§4.1-4.4（前置清单/命令块/分支/KD-72 判据）、§5-§6（未验证与来源）；`docs/research/2026-10-27-batch-25-record.md` §2.3/§5（R3 只登记未执行、#17）；批次 26 计划 `.zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md` §0-§2（窗口判定驱动 T3、≤6/块、KD-72 关闭判据）。

**外部（沿用 R3 §6 已核来源，本轮无新增外部缺口、未新增检索）**：Gradle 9.6.1 dependency locking <https://docs.gradle.org/9.6.1/userguide/dependency_locking.html>；#30480 <https://github.com/gradle/gradle/issues/30480>；#38057 <https://github.com/gradle/gradle/issues/38057>。
