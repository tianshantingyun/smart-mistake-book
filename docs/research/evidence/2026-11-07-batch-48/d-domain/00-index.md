# 批次 48 D 域产品路径取证索引（E-48-D-DEV-01）

| 字段 | 值 |
|---|---|
| evidence-id | `E-48-D-DEV-01` |
| run-id | `2026-11-06T18:40:12Z-batch48-d-domain-device` |
| APK sha256 | `147ca5a4c61618fb6381c2dc8c1c0daa50286f42b714be838ed06e1627901be8`（`:app:assembleLocalFirstDebug`，GRADLE_EXIT=0，门禁阶段串行构建） |
| 设备 | `emulator-5554`（sdk_gphone64_x86_64，Android 14，`getconf PAGE_SIZE`=4096；D 域无 arm64/16KB 判据，runbook §9.4） |
| 密钥来源 | **人工在设备 `模型设置` 输入**（`adb shell input text` 注入至 `capability_api_key` 字段，字段名 testTag=`capability_api_key`；密钥只存 gitignored `local.properties` 字段 `SMB_REAL_PROVIDER_KEY`，sha256 前 8 位 **`cd10efbc`**） |
| 服务商/模型/协议 | DeepSeek / `deepseek-flash` / `OPENAI_CHAT_COMPLETIONS`；Base URL `https://api.deepseek.com`（非秘密，与 E-48-PROV-01 冒烟同源） |
| 密钥泄露核查 | raw logcat 全文检索：key 明文 0 命中、`sk-` 0 命中、`Bearer ` 0 命中；本目录所有文件均只记 sha8 |

## 时间线（设备本地时间 = UTC）

1. 18:40 安装 `app-localFirst-debug.apk`（`adb install -r`，Success）→ 启动 MainActivity。
2. 18:43 进 我的→模型设置（`capability_screen`；该屏设 `FLAG_SECURE`——`SecondaryScreens.kt:116`，**按 app 设计不可截屏**，故无设置屏截图，只有 uiautomator 文本证据）。
3. 18:45 填 服务商/模型 ID/Base URL/API Key → `capability_save` → 消息「配置已保存在本机，请继续测试模型。」（首次注入时 key 误入 base_url 字段，已 CTRL+A 清空重填，此后重灌至 api_key 字段 35 个掩码点）。
4. 18:46 与 18:47 两次 `capability_test`（能力测试）均回 **「测试通过：图片读取和讲解都可用。」**（capability-test-msg.txt）——app 网关 → 真实 DeepSeek 上游往返成功（图片读取 + 讲解两路）。
5. 18:55 录入屏实走 **拍照** 腿（系统相机 com.android.camera2 快门 → 完成回传）→ app 保存原图（「原图已保存 · 发送范围由你决定」）→ 虚拟场景草稿的 CAPTURE_ASSESS（`capture-assess:6b37df03…`）**SUCCEEDED**（attempt=1，21s）。
6. 18:57 相册腿：照片选择器选真实题图（源图 sha256 `fefd17c2…`，推入 `/sdcard/Pictures/d48-main-path.jpg`）→ 该页 CAPTURE_ASSESS（`capture-assess:cdef39e2…`）**SUCCEEDED**（attempt=1，29s）。
7. 18:55:52 起 `problem-organization:1003d5536a…`（PROBLEM_CLASSIFY，虚拟场景草稿整理）STREAMING/VALIDATING_OUTPUT；**至 19:14+（≥18 分钟）无终态事件**。runbook 口径：`MODEL_TASK_CEILING_MILLIS=600_000` 只兜「派发方死亡」（`RoomModelTaskRepository.kt:1157-1163`），健康流式不被打断；app 无 HTTP 日志，无法区分「长流式」与「静默停滞」。队列被其占位：题图整理任务与讲题 TUTOR 任务 18 分钟内未创建（0 条新任务）。
8. 19:14 观测窗收口：讲题会话停在「正在准备这道题」（无 TUTOR 任务行）。

## 文件清单（sha256，落盘字节）

| 文件 | 内容 | sha256 |
|---|---|---|
| `capability-test-msg.txt` | 两次能力测试通过消息（uiautomator dump 节点摘录） | `5f4ed5e36a1c322041efb30294755b9c2ba81d2192f9f4d9eb9334496032e863` |
| `device-task-lifecycle.log` | model_task / model_task_event SQL 摘录，含 `COMMAND:`/`EXIT=0` 头（3 任务全状态流转，无密钥、无学生内容） | `d025054c3079cf4ef0a2620067a787ff754846e71df28435724f6583b1b3303e` |
| `device-d-domain-run.json` | 设备证据 JSON（serial、apk_sha256、协议/模型、key 来源与 sha8） | `55cb0a2e69c5a359196e154adce572080915ee76be5ffd07cfb798ec45ddf6d0` |
| `logcat-key-lines.log` | logcat 关键行脱敏摘录（483 行；raw 1.7MB 已删，其 sha256 曾为 `b53cae0462e5498a549ed9fc7c2007feaa63a0bf2805d3bed4734f1e6c87a830`，脱敏前后 key 核查均 0 命中） | `a286d18af60c22fce630369e5c8236b4455d708df845fe919aefa88d99b8c26a` |
| `shot-picked-page.png` | 录入屏：真实题图第 1 页 + 「正在整理题目」状态 | `5926525982116e214fc8dc63270c219f895ccc95d173ffb28fe8fafed633f131` |
| `tutor-session-preparing.png` | 讲题会话「正在准备这道题」状态 | `325aedd56bb8c772077ff7f7378e802f29f60c30156c71dd6ddff468f47a31bb` |

> 模型设置屏（能力测试 PASS 消息所在屏）**无截图**：app 有意设置 `FLAG_SECURE`
> （`app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt:116`，API Key 输入屏防截屏），
> screencap 按设计产出 0 字节；该屏证据以 uiautomator 文本摘录（capability-test-msg.txt）代替。

## 对 D-082..D-090 的证据映射（诚实口径）

- **已证**：设置保存 + 两次能力测试 PASS（真实上游往返）；两条 CAPTURE_ASSESS 真实任务完整生命周期 SUCCEEDED（attempt=1）；证据头四项（run-id/APK sha/serial/密钥来源）齐全且全文无密钥片段。
- **未证**：D-082 的「上游请求 id」——app 不落库上游 id、网关无 HTTP 日志（grep `HttpLoggingInterceptor` 0 命中），无法与 APK sha/run-id 同份绑定；分题（PROBLEM_CLASSIFY）在观测窗内未达终态；讲题 TUTOR 任务未启动。
- **未触及**：401/429/5xx 注入（D-083）、上下文/图片尺寸边界（D-084）、取消（D-085）、真实限流（D-086）、上游计费账本对账（D-087、真实计费侧不可核）、失败报文诊断（D-088）。
- **可复算**：`device-task-lifecycle.log` 的命令可用 `adb -s emulator-5554 shell run-as com.tingyun.smartmistakebook.localfirst sqlite3 databases/smart-mistake-book.db "…"` 复跑比对（设备与库存在期间）；`logcat-key-lines.log` 的脱敏规则与 key 核查结果见上。
