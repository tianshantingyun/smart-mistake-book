# KD-72 锁窗口重判与最小等待清单（批次 40 / WS6 item 34，2026-11-02）

> 只读复核 + 门脚本复跑；**零 Gradle、零 git 写、零锁文件动作**。结论：**窗口仍不可行（分支 B）**，但 W1 已从历次记录的 6/6 脏收敛到 **1/6 脏**（仅 `.gitignore`）。下方给出可执行的最小等待清单，以及"哪些条件已不再阻塞"。
>
> 依据：`docs/known-defects.md:2758-2796`（KD-72 全文与历次 Progress）、`docs/research/2026-11-02-batch-40-baseline.md:167-176,220`、计划 `.zcode/plans/plan-2026-11-02-batch-40-5x.md:97-100`、R6 `docs/research/2026-11-02-keeper-l8.md:310-320,544`（锁与 Keeper 同窗）。

## 0. 本轮实测（2026-11-02，HEAD `a3f367df`）

```text
git status --porcelain | wc -l                    -> 564
git status --porcelain | awk '{print $1}' | ...   -> 564 = 131 " M" + 433 "??"
git diff --cached --name-only | wc -l             -> 0
git ls-files '*lockfile' | wc -l                  -> 0
git status --porcelain | grep -i lockfile         -> 14 条（全部 ??）
find . -maxdepth 3 -name '*.lockfile' ... | wc -l -> 14
ls benchmark/gradle.lockfile                      -> No such file or directory
```

14 条 git 可见锁 = `app`、`core/{data,database,domain,export,markdown,model,ui}`、`feature/{capture,library,profile,review,tutor}` 的 `gradle.lockfile` + `settings-gradle.lockfile`。`core/markdown` 整目录已入库（`git ls-files core/markdown` 非空），故"git 可见"与"盘上"首次同为 **14**；`check_lockfiles.py` 的提示仍按"13 个未跟踪 `gradle.lockfile`"计数（不含 `settings-gradle.lockfile`），这是脚本口径差异，不是新遗漏。

门脚本复跑：

```text
python -I tools/ci/check_lockfiles.py
-> OK: dependency locking 尚未落地：没有任何已跟踪的 gradle.lockfile，KD-72 保持 open，本门跳过；
   工作区存在 13 个未跟踪锁文件（例如 app/gradle.lockfile），不能原样提交；
   缺锁 project：:benchmark（DEFAULT 模式下不会报错）
   EXIT=0

python -I tools/ci/check_dependency_pins.py
-> resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']
   OK: quickjs checksums, upstream POM requirement and resolved runtime pins match
   EXIT=0
```

## 1. W1–W7 逐项重判（对照批次 29 R3 分支 A/B）

| # | 条件 | 本轮实测 | 判定 |
|---|---|---|---|
| W1 | 六个触面 build 文件干净 | `settings.gradle.kts`、`core/ui/build.gradle.kts`、`feature/tutor/build.gradle.kts`、`core/export/build.gradle.kts`、`core/markdown/build.gradle.kts` **均无输出**；仅 `.gitignore` 为 ` M` | **1/6 脏**（历次为 6/6） |
| W2 | 树收口、可整批提交 | `git status --porcelain \| wc -l` = **564**（131 M + 433 ??），他线在飞 | **不满足** |
| W3 | 暂存区无锁 | `git diff --cached --name-only \| wc -l` = 0 | 满足 |
| W4 | 跟踪锁 / 未跟踪锁 / 盘上锁 | 0 / 14 / 14；全部 `??`，不得 `git add` | 未落地（预期） |
| W5 | `:benchmark` 有锁 | `ls benchmark/gradle.lockfile` = No such file | **不满足** |
| W6 | 负控（人为抬版/加依赖在解析期红） | 从未执行；需 Gradle 解析 | **不满足**（本轨禁跑） |
| W7 | Gradle 环境 | 本轨未跑 Gradle | 未验证 |

**裁定：分支 B（不可行）维持，零动作。** W1 的收敛是真实进展，但 W2/W5/W6 任一单独成立都不够：锁必须在"干净、待提交"的树上重生成，否则重生成的锁仍绑不定提交面；`:benchmark` 缺锁会让 `check_lockfiles.py` 在首个跟踪锁落地后判红；负控只能由门禁轨运行 Gradle。

## 2. 最小等待清单（按可并行/串行分组）

### 2.1 必须先由他线/主会话完成（本轨不可代做）

1. **树收口（W2）**：工作树 564 条降到"本批全部改动已分组、可提交"的状态。至少要满足：`git status --porcelain -- gradle/ build.gradle.kts settings.gradle.kts app/build.gradle.kts core/*/build.gradle.kts feature/*/build.gradle.kts` 为空（或只剩已裁定的不触锁文件）。当前 `core/database`、`core/data`、`app` 等源码仍在飞，KSP/Room schema 73 落码会再改构建输出。
2. **`.gitignore`（W1 唯一残项）**：属热区，本轨不改。需要其所有者确认当前 ` M` 是最终形态（基线记录显示它在批次 40 开工即 M）。
3. **Keeper 裁定（R6 D1/D4）**：若采纳 `usefulness/keeper 0.19.0`，`settings-gradle.lockfile` 会新增插件 classpath 坐标，`app/gradle.lockfile` 可能新增 R8 坐标；**锁重生成必须排在 Keeper 落码之后**，否则要生成两遍。若裁定维持 R-04 阻塞，则本项消失。

### 2.2 锁轨在窗口满足后的一次性动作（串行）

1. 确认无未跟踪锁被误 `git add`；必要时先清掉盘上 14 个旧锁（它们是脏树产物，不能作为基线）。
2. 运行（由门禁轨执行，本轨不跑）：
   ```bash
   ./gradlew --no-configuration-cache --no-parallel --console=plain \
     lockAllDependencies --write-locks
   ```
3. 审 diff：14 个 included project 都应有 `gradle.lockfile`（含 `:benchmark`）；核对 `core/markdown` 已在 `settings.gradle.kts:19-35` 的 include 列表内，锁可随模块入库。
4. 跑 `python3 tools/ci/check_lockfiles.py`（首个跟踪锁落地后进入强制模式）与 CI 的写锁-diff step。
5. 负控（W6）：人为抬高一个传递依赖版本/临时加依赖，确认解析期因锁状态不匹配而红；还原后复跑绿。命令与输出落 `docs/research/evidence/<batch>/`。

## 3. 不成立的"提前动作"

- **不能**现在生成锁并提交：W2 未满足，锁会再次绑定他线未提交的构建图；且 Keeper 若落地会立刻使锁过期。
- **不能**只提交 `settings-gradle.lockfile` 或部分模块锁：`check_lockfiles.py` 在首个跟踪锁出现后要求每个 included project 都有锁，`core/markdown` 已入库、`:benchmark` 仍缺锁，必然判红。
- **不能**把 `.gitignore` 的 `build/` 规则改动当作锁窗口的前置修复：它不改变解析图，只影响可提交面。

## 4. 结论

KD-72 **保持 open**；W1 已不再是主要阻碍，剩余最小等待面 = **W2 树收口 + W5 `:benchmark` 锁 + W6 负控 + W7 Gradle 轨**，外加 **Keeper D4 的排序**。在这些条件满足前，盘上 14 个未跟踪锁一律不得 `git add`。

## 5. 来源

| 类型 | 来源 | 用途 |
|---|---|---|
| 台账 | `docs/known-defects.md:2758-2796` | KD-72 定义、历次 W1–W7 记录、Fix direction |
| 基线 | `docs/research/2026-11-02-batch-40-baseline.md:167-176,220` | 锁状态与"1/6 脏"的开工读数 |
| 脚本 | `tools/ci/check_lockfiles.py`、`tools/ci/check_dependency_pins.py` | 本轮实跑 exit 0 与提示文本 |
| 构建 | `settings.gradle.kts:1-35`（`FAIL_ON_PROJECT_REPOS`、14 个 include） | W5/锁覆盖范围 |
| 研究 | `docs/research/2026-11-02-keeper-l8.md:310-320,544` | Keeper 对锁的牵动与 D4 裁定 |
| 命令 | 本轮 `git status --porcelain`、`git ls-files '*lockfile'`、`find … -name '*.lockfile'`、`ls benchmark/gradle.lockfile` | §0 全部读数 |
