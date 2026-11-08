# 批次 50 · 设备全量证据（2026-11-08）

> 角色：设备工程师（设备全量轨）。范围 = 计划 `.zcode/plans/plan-2026-11-09-batch-50-release.md`
> 中「设备」相关项：迁移 1..75、备份 34、trimmer/rowCounts、drill、错因 UI（5c）、容量 UI（5b）、
> 改卷 UI（6B-3）、KD-67、JS（是否广告）、AIGC 导出、release APK 安装冒烟、2h soak。
>
> 口径：本目录每条结论都对应本会话实跑命令与原始日志；未跑/未验证在 §8 单列。
> 设备：`emulator-5590`（x86_64 / API 34 / Android 14 / PAGE_SIZE 4096 / boot_id
> `31ecd364-6b2a-4e52-9643-23e681696172`）——**模拟器，非真机**。
> 全部设备用例走裸 `adb install` + `am instrument`；**未跑** `connectedLocalFirstDebugAndroidTest` 及任何
> `connected*AndroidTest`（计划禁令）。

## 0. 一屏结论

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 快照绑定（HEAD / 脏树 / APK sha / 设备） | 已绑定 | `00-snapshot.txt`、`01-device-meta.txt`、`03-apk-shas.txt` |
| 2 | 迁移 1..75（`FullMigrationMatrixInstrumentedTest` 20 例） | 红 1（旧列清单）→ 同步实体 → 单例绿 → **整类 OK (20 tests)** | `05-…log`、`07-…log`、`47-instrument-migration-matrix-final.log` |
| 3 | 备份 34 | **OK (34 tests)** | `14-…BackupRestore…log` |
| 4 | trimmer（10） | **OK (10 tests)** | `14-…Trimmer…log` |
| 5 | rowCounts（2） | **OK (2 tests)** | `14-…RowCounts…log` |
| 6 | drill（6 例） | 3 mask 红（旧白名单）→ 同步 → **OK (3)**；corrupted ✓、kill-stage 按设计进程死亡、restore ✓ | `11`、`13`、`15`、`16` |
| 7 | KD-67 第二道校准题（真 Room e2e 3 例） | **OK (3 tests)** | `18-instrument-kd67-room-e2e.log` |
| 8 | 6B-3 改卷 UI（17 例）+ 5c 错因面板（7 例） | **OK (24 tests)** | `19-instrument-library-6b3-5c.log` |
| 9 | 5c 真库写入接缝（1）+ JS 回放 painter（24） | **OK (25 tests)** | `24-instrument-5c-realrepo-js-painter.log` |
| 10 | 5b 容量 / 新题预览 / AIGC 标签 / 能力屏（17 例） | 红 2（旧整串匹配）→ 修 → **OK (17 tests)** | `21-…log`、`23-…log` |
| 11 | AIGC 导出（`core:export` 全模块 32 例） | **OK (32 tests)** | `17-instrument-export-all.log` |
| 12 | AIGC 导出 UI：delivery 2/2 + notifications 2/2（拒绝态按 harness 重跑）+ flags 2/2 | 绿；**background 2 例 FAIL（见 §8）** | `25`、`26` |
| 13 | JS 是否广告 | **不广告**（6 处能力集合全 `IMAGE_MODEL_ONLY`；生产无 JS 能力串） | `20-js-not-advertised-grep.txt` |
| 14 | release APK 安装冒烟（versionCode 101160） | **装成功、四 tab 渲染、0 FATAL** | `32-…release-install-meta.txt`、`33/34-*` |
| 15 | 2h soak（release APK 101160） | **PASS**：7200s / 115 轮 / TOTAL PSS 64.7 MB→峰值 100.6 MB（末次 95.7）/ 0 新 crash / 0 新 dropbox / monkey 全 0 | `soak/soak-summary.txt`、`37-soak-console.log` |

## 1. 快照与绑定

`00-snapshot.txt`（2026-11-08T13:21:33Z 实录）：

```
HEAD=a87fc70d15c8ad726cee17422dbeef0b4977747d
dirty_porcelain_lines=688
dirty_uall_paths=4336
tracked_diff_sha256=8992552bacaae8386835ea269ca459d702ae424d036d6246ba55f1667c886b66
untracked_list_sha256=0495fa0484da033ce3ec2f80497b584215f0e1657f61da1667d92201b2403a0d
```

**如实**：本轮设备读数构建自**脏工作树**（HEAD + 他线在飞改动），不是干净树产物；绑定靠上表的
HEAD + 下列 APK sha。干净树 release 复跑属主会话项（release 证据 §8.2）。

**指纹可复现性（补记，收工时实测）**：开工记下的 `tracked_diff_sha256=8992552b…` 在收工**不能复现**——
排除本轨 3 处测试改动后的全树 diff 为 `1467713e…`，`git status --porcelain` 由 688 → 690
（= 688 + 本轨 3 − 1 条他线已回写干净的路径）。差异已按 mtime 核查：`app|core|feature` 源树内
`.kt/.kts` 与资源在快照后**零改动**（只有本轨 4 个测试文件，其中 1 个已 revert），故设备二进制不受影响；
但**未能定位**那条被回写的具体路径。⇒ 源码绑定以 **HEAD + APK sha** 为权威，diff 指纹不作为可复现证据。

APK sha256（`03-apk-shas.txt`，本会话实际安装的包）：

| 用途 | 文件 | sha256 |
|---|---|---|
| app debug（UI 走查） | `app-localFirst-debug.apk` | `43f1fe46…c34a3e8e` |
| app androidTest（跑前） | `app-localFirst-debug-androidTest.apk` | `f11374a4…3d9e46d2` |
| app androidTest（修测后最终） | 同上 | `2e32e085…fdd2afcd9` |
| core:data androidTest（跑前） | `data-debug-androidTest.apk` | `b6745283…c275f612c` |
| core:data androidTest（修测后最终） | 同上 | `0013cf37…3a883ee0` |
| core:database androidTest（跑前） | `database-debug-androidTest.apk` | `9ce5855e…d7263633` |
| core:database androidTest（修测后最终） | 同上 | `db14c022…92cbf92` |
| feature:library androidTest | `library-debug-androidTest.apk` | `14cdea1d…8a4fced4` |
| core:export androidTest | `export-debug-androidTest.apk` | `07c71394…db181cb` |
| release（冒烟+soak） | `app-localFirst-release.apk` | `1bf98b00…66d34dec`（与 release 证据 `release-50.md:167` 同值） |

## 2. 迁移 1..75（矩阵 + 带数据迁移）

- 版本权威：`StudyDatabase.kt:142` `STUDY_DATABASE_VERSION = 75`；
  `FullMigrationMatrixInstrumentedTest.kt:143` 的 `for (version in 1..75)` 逐版本迁移。
- 整类 20 例首跑（`05-instrument-migration-matrix.log`）：`Tests run: 20, Failures: 1`。
  - `everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback` **PASS**（1→75 全链）；
  - 4 例带数据迁移（`legacyAndCompleteV73…`、`reviewPlanFingerprintMerge…`、`projectionArchive…`、
    `partialV73…`）**PASS**；
  - **唯一红** `mistakeExportRecordTableIsCreatedEmptyWithoutTouchingStoredRows`：
    `expected [10 列] but was [12 列]`，多出 `group_id`、`artifact_role`。
- 根因（权威源核对）：`core/database/schemas/…/75.json` 的 `mistake_export_record` 实为 12 列且顺序一致；
  实体 `MistakeExportRecordEntity.kt:44-45` 已含 `group_id`/`artifact_role`（T10 组导出）。测试里的
  **硬编码 10 列期望停留在 v61 形状**，与"列与实体逐位同形"的断言语义矛盾。
- 修法（最小 hook，未放宽等值关系）：期望列表补两列（`FullMigrationMatrixInstrumentedTest.kt:1691-1692`）。
- 修后单例复跑（`07-…green.log`）：`OK (1 test)`，`Time: 4.904`。
- 修后**整类复跑**（`47-instrument-migration-matrix-final.log`，最终 database test APK `db14c022…`）：
  **`OK (20 tests)`，`Time: 553.379`** —— 1→75 全链仍逐版本通过，带数据迁移 4 例全绿。
- 同类旧断言同步（G0 本批已做，未动）：`73.json`→`75.json`、`92`→`99` 表数。

## 3. 备份 / trimmer / rowCounts / drill

全部在**最终 core:data test APK `0013cf37…`** 上重跑（`14-*` 三份原始日志）：

| 套件 | 结果 | 原始行 |
|---|---|---|
| `BackupRestoreInstrumentedTest` | OK (34 tests) | `14-instrument-…BackupRestore…on-synced-apk.log` |
| `BackupSnapshotTrimmerInstrumentedTest` | OK (10 tests) | `14-instrument-…Trimmer…on-synced-apk.log` |
| `BackupRowCountsCoverageInstrumentedTest` | OK (2 tests) | `14-instrument-…RowCounts…on-synced-apk.log` |

drill（`DeviceMigrationDrillInstrumentedTest`，6 例）：

- 首跑 `11-instrument-drill-optin.log`：`Tests run: 6, Failures: 3` —— 三条 mask 演练均因
  `settings.json` 多出 `dailyReviewMinutes` 而红（期望"偏好六键"）。
- 根因：生产 `SmbkSettingsDocument.kt:41-42`（`SETTINGS_SCHEMA_VERSION=2`）已把
  `dailyReviewMinutes` 写进 settings.json（`AndroidBackupRepository.kt:297`），恢复侧也读它
  （`:840`）——这是**计划内偏好**，旧白名单漏了它。
- 修法（最小 hook，白名单仍是精确集合）：白名单补 `dailyReviewMinutes` 并改文案"七键"
  （`DeviceMigrationDrillInstrumentedTest.kt:527,851-861`）。该文件开工时已被他线 `M`（`.candidates`
  召回 API 与表数 90 的改动），本轨只加这一处，没有整文件抢占。
- 修后：三条 mask `OK (3 tests)`（`13-…green.log`）；`corruptedSelectionStopsBeforeSwapLeavingNoHalfDatabase`
  首跑即绿（`11`）；kill-stage 单跑 `shortMsg=Process crashed`（按设计随进程死亡，`15`）；
  `restoreFromHandoffAfterProcessDeath` `OK (1 test)`（`16`）。

## 4. 产品面设备验证

### 4.1 KD-67（第二道校准题）

- 真 Room e2e 3 例：`KnowledgeCalibrationQuestionRoomE2eInstrumentedTest` → **OK (3 tests)**
  （`18-…log`；只走生产写路径：会话题面链 / 第二槽持久化+逐槽 CAS+校准走廊选中 / 反例按题面 id 判分）。
- **UI 未验**：kd67 README §5 明说第二道题的"生成+上屏+作答"没有生产入口，学生仍只看到决策文本；
  本轨没有可点的设备路径，如实登记（不是"跑过绿"）。

### 4.2 6B-3 改卷 UI

- 设备用例 17 例（`NativePaperEditModelInstrumentedTest` 7 + `NativePaperEditRouteInstrumentedTest` 10）
  → 与 5c 面板合并跑 **OK (24 tests)**（`19-…log`）。
- 真实 app 走查（`31-ui-paperedit-ai.xml/.png`）：错题详情 → `AI 改卷面` chip → 卷面页展开
  "收起 AI 改卷 / 用一句话改卷 / 生成修改预览"与"只改导出卷面，错题本保持不变"边界文案。
  **未点"生成修改预览"**（不调用真实 Provider），故真实模型回执质量仍 UNVERIFIED（与 6b-3 证据一致）。

### 4.3 5c 错因 UI

- Compose 面板 7 例 → 合并 **OK (24 tests)**；真库写入接缝
  `ErrorCauseReviewServiceInstrumentedTest` 1 例 → 与 painter 合并 **OK (25 tests)**（`24-…log`）。
- 真实 app 走查（`29/30-ui-detail*.xml/.png`）：详情页"错因 / 备注"、"教学候选（未成为标签）"、
  E01–E04 图例、空态"还没有候选…"、以及"模型自动落账门未认证，保持关闭：模型不会自动写，只有你点
  「确认」才会留下一条。"——与 5c 的"门未认证分支"一致。
- **未复跑确认/撤销的注入走查**（5c 线已用内容寻址 id 注入 `PROPOSED` 行走过并清理）；本轨没有重新注入，
  该两条交互不记在本轨名下。

### 4.4 5b 容量 UI

- 设备用例（`ReviewCapacitySectionInstrumentedTest` + `ReviewIntakeBacklogPreviewInstrumentedTest`
  + `AiContentLabelsInstrumentedTest` + `CapabilityScreenInstrumentedTest`）→ **OK (17 tests)**（`23-…log`）。
- 真实 app 复习页（`27-ui-home.xml/.png`）："复习容量"区块 +
  "今天没有临近的考试，暂无容量读数；到期的复习照常安排。"（诚实空态）+ 今日题目预览
  "闭区间上的函数最值 / 用浓度商判断平衡移动 / 椭圆离心率 / 另有 1 道题"——5b 的容量与
  KD-68 预览文案在真实快照上可见。

### 4.5 AIGC 导出

- `core:export` 全模块设备：**OK (32 tests)**（`17-…log`，含 `MistakePdfPaperEditAigcInstrumentedTest`）。
- app 侧：`MistakeExportDeliveryInstrumentedTest` 2/2（打印/分享走既有 adapter 与 intent）、
  `MistakeExportNotificationsInstrumentedTest` 2/2（拒绝态按 harness 先 `pm revoke` + `-e smb_notifications_denied=true`
  重跑，`26-…log`）、`VisualMeasureReplayPaintFlagsInstrumentedTest` 2/2（`25-…log`）。
- **未做**：真实文件导出后在设备侧用第三方阅读器打开核对（AIGC 线已有 host 侧 pypdf/docx 独立读回，
  本轨未复跑）。

### 4.6 JS（是否广告）

- 生产能力集合 6 处全部 `FigureGenerationCapabilities.IMAGE_MODEL_ONLY`
  （`20-js-not-advertised-grep.txt`），生产源内无 JS/VISUAL_JS 能力串；
  与 `SmartMistakeBookApplication.kt:386-392` 三参装配、`DefaultFigureGenerationCoordinator` 无引擎回
  UNAVAILABLE 一致 → **JS 未广告，故本轨没有"广告后的 JS 产品路径"可验**（按 ask 的"若广告"分支不适用）。
- 仍跑了 JS 相关设备面：painter 回放 24 例（含 math 显式降级、stroke-only/dash 兜底）+ flags 2 例，全绿。

## 5. release APK 安装冒烟

| 步 | 读数 |
|---|---|
| 备份 debug 库 | `.tmp/device-backup-2150/smart-mistake-book.db`（400,662,528 B，sha256 `68422935…c40d56`；`.tmp/` 已 gitignore） |
| 安装 | `uninstall com.tingyun.smartmistakebook.localfirst` → `install app-localFirst-release.apk` → `Success` |
| 包信息 | `versionCode=101160`、`versionName=0.2.0-localfirst`、`minSdk=23`/`targetSdk=36`（`32-…txt`）；安装包 sha `1bf98b00…` 与 release 证据里已验签的包完全一致，即同一签名产物 |
| 启动 | `am start` MainActivity 后 8s 截图；`logcat -b crash` 空；全缓冲 `FATAL EXCEPTION` 计数 **0** |
| 四 tab | 复习（空态"还没有可复习的题"）/ 智能体（"请先配置并测试模型"）/ 错题本（"暂无错题"）/ 我的（"学习掌握"）全部渲染（`33/34-*`） |

**如实**：debug→release 签名不同，必须先 uninstall，设备上原有 7 条测试数据被清（已备份到 `.tmp`，
release 不可调试所以 `run-as` 无法回灌）。冒烟后不把 release 当"用户数据可用"的证明。

## 6. 2h soak（结果：PASS）

`soak/soak-summary.txt`（脚本自算，`EXIT=0`）：

```
duration_target_s=7200
cycles=115
pss_samples=0 pss_min_kb=None pss_max_kb=None
total_pss_min_kb=64720 total_pss_max_kb=100634
dropbox_first=2 dropbox_last=2
crash_lines_first=0 crash_lines_last=0
crash_delta=0 dropbox_delta=0 monkey_rc_nonzero=0
completed=1
OUTCOME=PASS
FAIL_REASONS=none
```

- 起跑绑定：`36-soak-start.txt`（release APK `1bf98b00…`、versionCode 101160、boot_id
  `31ecd364…6172`）；harness = `35-soak-script.sh`（沿用批次 49 `f-soak.sh` 判据）。
- 115 轮 monkey（`soak/soak-monkey-*.log`，全部 rc=0）；pid 全程 `14496` 未变（**无进程重启**）；
  `logcat -b crash` 首尾均 0 行；dropbox 计数首尾均 2（无新增）；总 PSS 64,720 → 100,634 KB
  （+35.9 MB / 2h；批次 49 同口径 176,669→242,118 KB）。
- **口径如实**：`pss_kb`（smaps_rollup `Pss:`）在本设备读不到（`pss_samples=0`，与批次 49 相同），
  上表用 `dumpsys meminfo` 的 TOTAL PSS；soak 跑在**空库 release**（签名切换清库，见 §5）。
- **PASS 判据边界（修复环补注）**：脚本的 PASS 只看 `duration/cycles/crash_lines/dropbox/monkey`，
  **没有** ANR 采样，也**没有**内存/电池阈值（PSS 只记录、不比较）。因此本 PASS 只等价于
  "7200s 内无新增 crash/dropbox，monkey rc 全 0"，**不等于**"内存/电池稳定"；
  TOTAL PSS +35.9 MB/2h 只能作描述性观察，未设门。

## 7. 本轨改动文件（3 处测试侧最小 hook）

| 文件 | 改动 | 原因 |
|---|---|---|
| `core/database/src/androidTest/.../FullMigrationMatrixInstrumentedTest.kt` | +5 行：期望列清单补 `group_id`/`artifact_role` | 旧期望停在 v61；75.json 与实体已是 12 列 |
| `core/data/src/androidTest/.../DeviceMigrationDrillInstrumentedTest.kt` | +8/−4：白名单补 `dailyReviewMinutes` + 文案 | 旧白名单停在 settings v1 六键；生产已是 v2 七键 |
| `app/src/androidTest/.../CapabilityScreenInstrumentedTest.kt` | +14/−3：整串匹配改 `substring = true` + 常量 | 生产文案在同节点追加 `editCapabilityDescription()`，整串匹配永远找不到 |

三处都保持"断言要钉的事实"不变（列同形 / 偏好键精确集合 / 探针通过上屏），没有把失败当通过。
`DeviceMigrationDrillInstrumentedTest.kt` 开工时已是他线 `M` 文件，本轨只做这一处，未整文件抢占。

> 另：为定位导出背景 2 例，本轨曾给 `MistakeExportBackgroundInstrumentedTest.kt` 加过
> `performScrollToNode` helper（两次尝试均未转绿），**已 revert**，该文件当前 `git status` 0 行；
> 实验日志留在 `43/46-…log`，改动本身不计入本轮。

## 8. 未跑 / 未验证（如实）

1. **`MistakeExportBackgroundInstrumentedTest` 2 例 FAIL**（`25-…log`，最终 APK 上仍未转绿）：
   `exportCompletesAfterTheDetailingPageIsLeftAndStaysRetrievable`、
   `exportIsStillRunningWhenThePageIsLeftAndCompletesAfterTheGateOpens`——`waitForTag` 20s 超时，
   卡在等 `library_item_entry:m1:closed-interval-extrema`。
   - 事实一：该条目**在库里**（`49-db-entry-query.txt`：
     `entry:m1:closed-interval-extrema|ACTIVE|1767225600000`，全库 `count(*)=7`；
     `updated_at` 比设备上两条 2026-11-08 新拍条目旧，排序靠后）。
   - 事实二：真实 app 手工上滑到底后**能看到**"闭区间上的函数最值"卡片（`44-ui-library-bottom.xml/.png`），
     即产品列表没丢条目。
   - 事实三：把等待改成 `performScrollToNode(hasTestTag(tag)) + 立刻点击` 后，仍 20s 等不到该 tag
     （`43`/`46-…log`）；这条实验改动已 **revert**（该文件恢复干净，`git status` 0 行）。
     ⇒ 失败在 **Compose 测试与分页懒列表的交互**（滚动/刷新时序），不是"导出随页面丢"被证伪。
   - 该 claim 在本轨仍 **UNVERIFIED**；要转绿需要该测试 owner 用可控列表状态（或直接 LazyListState
     scrollToIndex）重修，本轨不代改。
2. 未上真机；无 arm64 设备；16KB 运行时未验（设备 4096 页/x86_64）。
3. 5c 确认/撤销的注入走查未复跑（见 §4.3）；KD-67 第二道题的生成/上屏/作答 UI 不存在（见 §4.1）。
4. AIGC 真实文件在设备第三方阅读器打开未验；真实 Provider 改卷回执未验（未调用真实 Provider）。
5. 2h soak 只在 release APK + 空库上跑（签名切换清库），没有"非空用户库 + release"这一组合。
6. 设备库状态：恢复后的 7 条 = 5 条夹具/既有 `entry:m1:*` + 2 条既有新拍条目（`49-db-entry-query.txt`
   `count(*)=7`）；与备份一致，未再清理。
