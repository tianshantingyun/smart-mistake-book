# E-14 批次 39 收尾轮·源快照门与阻塞登记

| 字段 | 值 |
|---|---|
| evidence-id | E-14 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39（收尾轮 2026-11-02） |
| step | 收尾轮·收口 |
| gate-or-kd | 源快照门（窗内稳定） |
| verdict | **FAIL（窗 W3 作废）**；窗 W2 通过 |
| executed-at | 2026-11-02T03:12:00+08:00 |
| operator | 设备验证轨（子代理） |
| head | `f10a1e4e` |
| worktree | 514（开窗）→ 510（收口）；本轨新增 1 个 M（X-03 测试）与 1 个 untracked 文件修改（KD-76 用例） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | 见 E-09 |
| apk-sha256 | 见 E-09 |
| exit-code | diff 命令退出 1（有差异） |
| raw-log | `build/device-track-batch39-tail/src-sha-{before,after-build,after-restore,after-all}.txt`、`src-snapshot-{build,restore,all}.diff`、`src-snapshot-all-paths.txt` |
| record-ref | `docs/research/evidence/2026-11-01-batch-39/00-index.md` §11 |
| sanitized | 是 |

## 1. 窗 W2（有效）

```text
src-sha-before.txt         1232 行  sha256 b8708cfd…
src-sha-after-build.txt    diff = 0 行（构建窗稳定）
src-sha-after-restore.txt  diff = 0 行（01:26，X-03 / isolation / T4 控制轮窗稳定）
```

E-10 与 E-11 的全部结论成立于窗 W2。

## 2. 窗 W3（作废）

```bash
find app/src core/model/src core/data/src core/database/src core/domain/src -type f -print0 \
  | sort -z | xargs -0 sha256sum > src-sha-after-all.txt
diff -u src-sha-before.txt src-sha-after-all.txt   # 172 行差异 / 29 个路径
```

29 个路径（`src-snapshot-all-paths.txt`）：

- **本轨 1 个**：`app/src/androidTest/.../visualjs/VisualJsConnectStageCalibrationInstrumentedTest.kt`（KD-76 loadavg A 类修正，见 E-13）。
- **他线 28 个**：`core/database` 的 schema-72/知识检索工作（`KnowledgeSearchFeatureStatMigration_71_72.kt`、`KnowledgeSearchMaterialFeatureMigration_71_72.kt`、`StudyDatabase.kt` 等 20 个）与 `core/data`/`core/database` 的测试/基准 8 个；其中 2 个 androidTest 文件 mtime 01:57:30，`StudyDatabase.kt` mtime 01:57:29，02:25→02:59 其 sha 再次变化。

判定：**W3 作废**。W3 内跑的 26 套件（01:27:44–02:57:24）与 KD-76 校准（03:05–03:10）不能作为"同一源快照"的证据；其中 24 个绿套件仍可作**运行观测**，但不得作为不回退的最终证明。三个 DB 版本相关红例的归因（E-12）与 KD-76 G1 红（E-13）均落在 W3。

## 3. 归因汇总（A/B/C/D）

| 项 | 类别 | 依据 |
|---|---|---|
| X-03 首跑 | A（夹具类型） | `Integer(300) != Long(300)`；javap 实证；E-10 §2 |
| X-03 绿 | — | 修正后同 APK 绿 |
| m7071 首跑 | A（runner FQCN） | 仓内类名带 `Identity`；E-12 §4 |
| KD-76 首跑 | A（fixture：/proc 限制） | `raw='null'`；E-13 §2 |
| backup/c4/rowcounts 红 | B+D | 包内 `StudyDatabase=72` vs `SmbkSelectionPlan=71`；E-12 §3 |
| isolation 8 红 + 控制 10 红 | **D 与 T4 均未隔离** | 两窗红集只有 7 例交集、控制构建身份不可证（E-11 §3/§5）；原"D（T4 排除）"撤回 |
| KD-76 G1 红 | D（负载） | 设备 loadavg 2.66→11.25；E-13 §5 |
| 窗 W3 不稳定 | B | 28 个他线路径在窗内变动；本节 |

## 4. 阻塞与待办（交主会话/主轨裁定）

1. **isolation 15/15 未达**：需在他线构建/测试静止、设备 loadavg ≤1.0 的长窗复跑。**T4 未被排除**（控制轮不是可证伪对照：`control-baseline-engine.sha256` 是基线 sha、`build-control.log` 无回退记录、两窗 red 集只有 7 例交集），D 也不能单独归因；复跑时须按逐方法配对重做 T4 判别。若复跑仍红，按 C 产品回归立案（候选面：`SERVICE_HARD_DEADLINE_MILLIS=1500` 与冷启动 attach 7.6 s 的叠加），不得先改常量或断言。
2. **backup/c4/rowcounts**：需等他线 schema-72 工作落定、树静止后重建 `data-debug-androidTest.apk` 并复跑三套件；当前红例不是本批产品回归。
3. **KD-76**：预注册 n=20 未达成（有效 9），且**主口径 M=S-bind+S-warmup 的 p95=9813 ms > 5000 ms 已 FAIL**（E-13 §4.1，本复核轮复算）。需按"reboot 后冷启动轮"重跑；KD-76 另半（R3 广告门/加载态文案）仍 open。
4. **源快照门**：W3 已作废；上述复跑必须在同一静止窗内完成，否则旧绿/新红均不可作数。
5. **29.5 s 链**：S4 无计时点；S3 **门未取得**（原写"门佐证"不成立——isolation 基线窗 `aHungWorker…` 失败在预热断言，hard+500 门未执行，E-13 §6）。不得声称实测。
6. **DB schema 71/72 未裁定（新增）**：现树 `StudyDatabase.kt:140` = 72、`72.json` 未跟踪，而 `S=71` 门只校验 71.json（E-09 §8）。任何按现树重建前必须先裁定 71/72；`S=71 PASS` 不得当作现树有效门。
7. **p10 签名判据被就地放宽（新增）**：p10 窗有 1 条 `death was not confirmed`，严格按 T1 §7.2"9 类签名 0"不满足（E-12 §5）；是否接受由主会话裁定。
8. **单元测试门当前红（他线 B，不是本批回归；2026-11-02 两次复跑，本会话实读测试报告与产物）**：
   - **第二次复跑（最新，契约拒收，24 红）**：`:core:data:testDebugUnitTest` 22 failed、`:core:database:testDebugUnitTest` 2 failed，统一 `DatabaseContractViolationException`。两类消息：
     - `Reference-only knowledge sources need a license expression or license URI; sources without verifiable provenance must use the unverified-placeholder class`（`core/database/.../KnowledgeBaseImportContract.kt:167`）；
     - `source.sourceUri must not use a documentation placeholder domain`（同文件 `:326`）。
   - 根因：工作树对 `KnowledgeBaseImportContract.kt` 的未提交改动新增了 `validatePlaceholderClass` / `validateLicenseEvidence`（`git diff` 可见：占位域名单 `example.com/.net/.org/.edu/example`、空 license 拒收），而 bundled knowledge pack 的现有来源仍带 `https://www.example.edu/...` 等占位域/无 license 证据 → 契约拒收。归 **B（他线在飞，KB 契约/包未同步）**。
   - 另有 `java.nio.file.NoSuchFileException: core/database/build/test-results/testDebugUnitTest/binary/in-progress-results-generic.bin`：测试报告中间产物的基础设施错误（Gradle 测试任务收尾），不是产品断言失败。**该错误使 core:database 的 XML 结果不完整**（本会话读到 `KnowledgeTeachingMaterialContractTest.xml` 显示 `failures="0"`，与日志的 FAILED 不一致），故 2 个 core:database 红例名与消息以本轮构建日志为准，不以被覆盖的 XML 为准。
   - **第一次复跑（已过时，值漂移 3 红）**：`ProductionLexicalLegExportTest` MRR `0.6178294573643413→0.6130490956072351`、`Stage1LexicalLabTest` v1 旧生产形 MRR `0.15930232558139526→0.16072351421188627`、`Stage2LexicalScoresExportTest` 臂 A 命中 `101→100`；原因是 `KnowledgeSearchFeatureExtractor` 的 L2 改动（`INDEX_VERSION` 1→2、192→128、新增 `fromMaterial`）。该轮已被第二次复跑的契约拒收覆盖，保留作树漂移证据。
   - **处置**：本轮**未**改 KB 契约、bundled pack 或这些测试的期望值（改包/改规则=接受他线新行为，需 KB 线/主会话裁定）。
   - 与本批的关系：上述任务**不编译/不执行** `app/src/androidTest`，不能用来判定本批 KD-76 用例；本批用例的编译与设备执行仍 UNVERIFIED。

## 5. 本轨未执行/未验证

- 未运行 `connectedAndroidTest`；未做任何 git 写操作；未改热区（Application/Coordinator/feature/*/core/ui/core/export/core/domain/settings.gradle.kts）。
- 本轨改动仅两处测试侧：X-03 类型归一（E-10）、KD-76 loadavg 降级（E-13）；均未提交。
- 设备 5566/5584/5596 未触碰；所有设备命令只作用于 `emulator-5562`。
