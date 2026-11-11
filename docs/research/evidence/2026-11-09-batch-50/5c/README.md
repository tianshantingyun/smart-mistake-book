# 5c 错因 UI（详情页教学候选 + 用户确认/撤销）证据

- 批次：50（2026-11-08/09）
- 计划：`.zcode/plans/plan-2026-11-09-batch-50-release.md:61`（产品面 5c）
- 数据面事实：D-1 审计 `docs/research/evidence/2026-11-09-batch-50/d1/stats.json`
  `pass_main=true / pass_guardrail=false` → **3D 门整体未通过**（normal 层 Wilson 下界 0.8979 < 0.90）。
  因此本页按"未过门"分支实现：**模型自动落账关闭**；候选只读展示；只有学生点「确认」才写一条
  `USER_CONFIRMED`（门答询见记录 §5c-0）。
- 设备：`emulator-5590`（x86_64，API 由镜像决定），非真机。

## 1. 交互形态（设备截图，同一台机同一次会话）

| 文件 | 状态 | 看点 |
|---|---|---|
| `panel-empty-current-revision.png` | 当前修订、无候选 | 标题「教学候选（未成为标签）」；四类图例 E01–E04；空态不明示原因；落款说明不进错题本分类/筛选/卡片标签 + 门未认证 |
| `candidate-shown.png` | 注入一条 `PROPOSED` 候选 | 候选行显示 E01 / 讲题记录 / 置信 86% / 证据 1 条 / 理由；「确认这条」 |
| `after-confirm.png` | 点「确认这条」后 | 现行标签「你确认 · 置信 86%」+「撤销」；候选清空；回执"已记下…不影响掌握度" |
| `after-undo.png` | 点「撤销」后 | 标签消失、回到空态；回执"已撤销这条候选。历史记录仍然保留，这次作答的对错不变。" |

> 说明：`after-undo.png` 是**修复后**的复拍。修复前同一步显示的是"已记下"（撤销成功套用了确认文案），
> 该缺陷由本轨设备走查发现，已修并加 JVM 用例 `ErrorCauseWriteMessageTest` 钉住。

## 2. 数据面读数（真 app 库，逐条命令可复跑）

注入行（内容寻址 id 必须与 `ErrorCauseIdentity` 一致，否则确认会在端口内找不到 observation）：

```bash
python -I -X utf8 -c '...'   # 见记录 §5c-2 的字段序；得 ecobs-1d6379db…e25e40
adb -s emulator-5590 shell am force-stop com.tingyun.smartmistakebook.localfirst
adb -s emulator-5590 shell "run-as com.tingyun.smartmistakebook.localfirst sqlite3 databases/smart-mistake-book.db" < build/agent-5c-inject.sql
```

确认后（`sqlite3` 实读）：

```
observation: ecobs-1d6379db…e25e40 | ACCEPTED | USER
binding    : ecuser-ecobs-1d6379db…e25e40 | USER_CONFIRMED | gate_version IS NULL=1 | ACTIVE
```

撤销后（`sqlite3` 实读）：

```
ecobs-1d6379db…e25e40 | ACCEPTED  | USER | supersedes!=null=0   ← 原证据保留
ecobs-2255b3b5…46296a | RETRACTED | USER | supersedes!=null=1   ← 撤回行（指向原证据）
ecuser-ecobs-1d63…    | VOIDED    | active_key IS NULL=1        ← 活键腾空
```

清理（本轨注入的三行已全删，避免污染他线设备状态）：

```
select count(*) from error_cause_observation;  → 0
select count(*) from error_cause_binding;      → 0
```

## 3. 门禁实跑（本轮当前树）

| 门 | 命令 | 读数 |
|---|---|---|
| JVM 错因全链 | `./gradlew :core:data:testDebugUnitTest --tests "*ErrorCause*"` | **41 tests / 0 failures**（Chain 6、Extractor 11、Gate 9、Retractor 9、ReviewService 6） |
| JVM 面板契约 | `./gradlew :feature:library:testDebugUnitTest --tests "…ErrorCausePanelStateTest"` | 9 / 0 |
| JVM 回执文案 | `./gradlew :app:testLocalFirstDebugUnitTest --tests "…ErrorCauseWriteMessageTest"` | 3 / 0 |
| JVM schema 契约 | `./gradlew :core:database:testDebugUnitTest --tests "*ErrorCauseSchemaContractTest"` | 3 / 0 |
| 设备·真库接缝 | `:core:data:assembleDebugAndroidTest` → `adb install -r -t` → `am instrument -e class …ErrorCauseReviewServiceInstrumentedTest` | **OK (1 test)** |
| 设备·Compose 面板 | `:feature:library:assembleDebugAndroidTest` → `adb install -r -t` → `am instrument -e class …MistakeErrorCausePanelInstrumentedTest` | **OK (7 tests)** |
| 编译 | `:app:compileLocalFirstDebugKotlin`、`:core:data:assembleDebugAndroidTest` | BUILD SUCCESSFUL |

绑定二进制：

- app debug APK `6f2f4fde8a121a265c273326d6176a0fdbc490de3ff56c29ccc9d45be4d2a685`
- core:data test APK `a41206926eb1c5f30a095909e6a23839a950241b8f780ded252a48cbed28e254`
- feature:library test APK `e1173cb544c7f4cef732e644ff6c056f0f955b35f4125e765cb6060314e62588`

## 4. 未做 / 未验证（如实）

- **未上真机**（仅 emulator-5590）；未跑 2h soak。
- **未跑 `connectedLocalFirstDebugAndroidTest`**（计划禁令）；设备用例全部走裸 `am instrument`。
- **生产里目前没有候选生产者**：`ErrorCauseBookingService.proposeAndBook` / `ErrorCauseExtractor` 在
  生产装配中仍无调用方（批次 41 只落了接缝）。因此真实用户当前只会看到空态；本次写入证据是
  显式注入一条 `PROPOSED` 行后走真实 UI 得到，不是"模型自己产出的候选"。
- 应用内**没有**用生产用户库做"讲题→产候选"的全链（无生产者，无法产生）。
- 历史版本页面不挂面板由 `MistakeErrorCausePanelInstrumentedTest#panelOnlyRendersForTheCurrentRevision`
  覆盖（Compose 设备用例），未做手工点按复核。
