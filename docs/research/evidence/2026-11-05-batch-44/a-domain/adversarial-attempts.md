# A 域对抗复核尝试（批次 44）

> 作者：A 域验收工程师（同一执行者做的**反例尝试**，不是独立复核人）。
> 独立复核与终裁在 GATE 域；本文件只记录“怎样试图把假绿做出来、结果是否被挡住”。

## 1. 尝试清单与结果

| # | 目标行 | 反例做法 | 结果 |
|---|---|---|---|
| 1 | A-004 空态 | 先在设备上保留种子数据，再只断言 `root_library` 存在，不检查空态标记 | 被挡：探针先 `pm clear`，且同时断言 `library_empty_state`；种子数据无法留下 |
| 2 | A-042 权限拒绝 | 在测试进程内 `revokeRuntimePermission` 后继续跑同一 instrumentation | **抓到真缺陷**：Android 撤销运行期权限会杀目标进程，run 以 `Process crashed.` 收尾；改为外部先 `pm revoke` + `-e smb_notifications_denied=true`，重跑 1/1 通过 |
| 3 | A-016 旋转 | 只在横屏检查根节点、或在返回竖屏后重新导航再断言 | 被挡：旋转探针在横屏只验根节点、返回竖屏**不重新导航**；`tutor_capture_shortcut` 必须在原页面重现 |
| 4 | A-041 大数据 | 用 `ExportPdfWorkerTest` 的“100 个 id 能编码进 WorkManager”冒充大页数渲染 | **主动撤回**：该用例只证输入编码上限，改为 `UNVERIFIED` |
| 5 | A-001 主路径 | 只跑 UI 打开录入页，不检查是否真正落库 | 被挡：`LocalTaskLineDeviceInstrumentedTest` 断言 `problem` 行数 1、`model_task` 仍为 0、离开再进入仍可见 |
| 6 | A-080 后台 | HOME 返回后重新导航到提醒页再断言，掩盖状态丢失 | 被挡：`home` 动作返回后**不重新导航**，只能原页面重现才判 PASS |
| 7 | A-009 杀进程 | `am kill` 无效时直接判通过（进程没死） | 被挡：探针在 `am kill` 后 pid 不变则补 `am force-stop`，并断言 pid 必须变化 |
| 8 | A-050 大数据 | 用选择面 8 个 UI 用例冒充“大库备份/恢复” | 被挡：另跑 `BackupSelectionScaleInstrumentedTest` 的 365 MiB 全量/子集备份恢复 |
| 9 | A-070 旋转 | 只在竖屏 dump 一次就断言 | 被挡：旋转动作强制横屏→竖屏两次断言，且横屏/竖屏分别判根节点 |
| 10 | A-090 杀进程 | 杀进程后用 `pm clear` 后的空库启动（数据本来就没有）来“证明持久化” | 被挡：杀进程探针不 `pm clear`，用已落库的调度设置页作为断言面 |

## 2. 仍然存在的残余（不得写成 PASS）

- **审美/语感**：空态与错误文案是否“像人话”，自动化只断言存在与关键短语。
- **真实 Provider**：讲题主路径需外部凭据；A-010 维持 `BLOCKED`。
- **无 oracle 的维度**：各旅程“大数据”在 E/F 域另有覆盖，A 域只保留可执行的设备/批量证据。
- **状态恢复语义**：`home`/`kill` 只证明入口可用与页面重现，不证明滚动位置、草稿字节级一致。

## 3. 门怎么抓复发

| 复发形态 | 抓住它的门 |
|---|---|
| `am instrument` 失败仍 exit 0 | `am_instrument_recorder.py` 解析状态码，失败/错误/跳过 → `EXIT=1` |
| 权限拒绝用例自我 revoke 杀进程 | 用例要求外部预置 `smb_notifications_denied`，缺失直接失败（不是 skip） |
| 旋转后只验根节点 | 探针横屏验根、竖屏验根+次要节点；次要节点缺失判 FAIL |
| HOME 后重新导航掩盖状态丢失 | `home` 分支不做二次导航 |
| `am kill` 没杀掉仍判过 | pid 必须变化，否则补 force-stop 并记录方法 |
| 跳过用例计绿 | `acceptance_lint.py` 的 `SKIPPED_GATE_AS_PASS`；recorder 不写 `skipped=` 到 cmd 日志 |
