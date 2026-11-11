# Play Data Safety 逐题清单（草案，未提交）

> **落盘**：2026-11-08，批次 50 隐私备案线。
> **状态**：**未提交**（Play Console 需开发者账号与已发布隐私政策 URL，外部条件；H-036 BLOCKED）。
> **口径边界（重要）**：本清单把两件事分开写——**代码可判定的事实**（§1，逐条带证据）与
> **给表单的建议答案**（§2，标注"待后台核对"）。本会话无法登录 Play 后台，`developer.android.com`
> 与 `support.google.com` 的官方页面抓取超时（`WebFetch` 连接超时），只从搜索结果摘要确认了表单结构
> （先问"是否收集或共享下列用户数据类型"，再问用途与安全实践）。因此**表单字段名与
> "collected/shared" 的判定口径未逐字核对**，不得把 §2 当成已核实结论；本文件不构成法律意见。

## 1. 事实层（代码/清单可判定，逐条可复跑）

| # | 事实 | 读数 | 证据 |
|---|---|---|---|
| F1 | 开发者自己的服务器上传通道：**无** | 遥测构建开关默认 `false`；v1 `endpoint` 默认空串，上传门要求端点非空 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/telemetry/TelemetryConsent.kt:47`、`:48`、`:75` |
| F2 | 遥测首装默认关闭、年龄门 fail-closed | 首装 `UNKNOWN` 等价 OFF；14 岁以下与 14–17 岁均要求监护人确认 | `TelemetryConsent.kt:32`；门 H-028/H-031/H-032 PASS |
| F3 | 分析/广告 SDK：**0** | 全 Gradle 清单按 `firebase/admob/appsflyer/umeng/gms.ads/analytics` 检索命中 0 | `h_compliance_gate.py --topic privacy` 的 H-022 PASS |
| F4 | 本机数据面 = 备份四类 + 设置 + 临时文件 | 错题本 / 掌握库（学习证据与复习）/ 会话（讲题消息与附图引用）/ 图片（`canonical_source_asset` 与字节） | `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt:10-23` |
| F5 | 数据离机的**唯一**路径：用户主动发起的 BYOK 请求 | 只发当前题目与本次消息、本次明确授予的题图、所选服务商/模型标识、受限教学上下文 | `PRIVACY-DATA-FLOW.md:6-18` |
| F6 | 永不发送：API Key/授权头、Keystore 材料、无关错题与完整历史、租约与调度预算 | 同 F5 | `PRIVACY-DATA-FLOW.md:10-14` |
| F7 | 系统备份/换机迁移 **关闭** | `allowBackup=false`；root/file/database 等域全部排除；`disableIfNoEncryptionCapabilities` | `app/src/main/AndroidManifest.xml:12`；`app/src/main/res/xml/backup_rules.xml:3-5`；门 H-035 PASS |
| F8 | 权限最小集 | 通知、开机完成（主清单）；联网与网络状态仅联网版声明，离线版 `tools:node="remove"` | `app/src/main/AndroidManifest.xml:4-5`；`app/src/localFirst/AndroidManifest.xml:3-4`；`app/src/strictOffline/AndroidManifest.xml:4-11` |
| F9 | 传输加密 | 仅 HTTPS：`usesCleartextTraffic=false` + 联网版 `cleartextTrafficPermitted="false"` | `app/src/main/AndroidManifest.xml:20`；`app/src/localFirst/res/xml/network_security_config.xml:3` |
| F10 | 应用内删除 | "删除全部数据"删除数据库、题图、设置、临时文件，且不会自动备份 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt:958-960` |
| F11 | 不采集设备标识/IP/logcat | 诊断事件只记本地已知版本三元组，零新增隐私面 | `app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics/DiagnosticEvent.kt:7` |
| F12 | 诊断包只在用户导出时生成本地文件，无自动上传 | 导出器无 HTTP/上传/端点调用 | `app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics/DiagnosticsBundleExporter.kt`（全文检索 `http|upload|POST|endpoint` 零命中） |

## 2. 表单建议答案（**待 Play 后台核对**）

> Play 的表单把"收集"定义为数据离开设备，把"共享"定义为提供给第三方。BYOK 形态（数据直接发往
> **用户自己选择并配置**的模型服务，开发者不运营该端点）在两种口径下的归类**未核实**：可能要求按
> "收集"申报并说明用途，也可能按用户发起的一次性传输处理。**提交前必须在 Play 后台逐题核对，
> 不得照抄本节当结论。**

| 数据对象 | 对应 Play 数据类型（字段名待核对） | 是否离开设备 | 触发条件 | 建议答案 | 待核对点 |
|---|---|---|---|---|---|
| 题图（拍摄/导入的题目图片） | 照片和视频 | 仅 BYOK 请求时 | 用户主动发起且明确授予本次图片 | 建议申报：用户发起的外部传输；用途=应用功能（讲题/识别） | collected vs shared；是否需勾选"可选" |
| 当前题目/本次消息文本 | 消息（其他应用内消息）或 用户生成内容 | 仅 BYOK 请求时 | 同上 | 同上 | 同上 |
| 错题本、掌握库、复习记录 | 应用活动 / 用户生成内容 | **否**（仅本机） | — | 不收集、不共享 | 无 |
| 设置与 API Key | 应用内偏好 / 凭据 | **否**（Key 存系统安全存储，不进备份/日志） | — | 不收集、不共享 | 无 |
| 诊断信息 | 应用信息与性能（诊断） | **否**（用户显式导出文件） | 用户导出 | 不收集 | 导出文件是否被视为离机 |
| 设备或其他 ID、位置、联系人、日历、短信、健康、财务、账号 | — | **否** | — | 不收集、不共享（无权限、无账号体系） | 无 |

**安全实践四问（建议）**

| 问题 | 建议答案 | 依据 |
|---|---|---|
| 传输中加密？ | 是（仅 HTTPS，明文流量禁用） | F9 |
| 提供删除数据方式？ | 是（应用内"删除全部数据"，见存储页） | F10 |
| 是否承诺遵守 Families 政策？ | **待定**：产品面向中学生（12–16 岁），是否进入 Play Families 项目需产品/法务决定 | 不在本线裁定范围 |
| 独立安全审查？ | **否**（不得勾选未发生的审查） | 无第三方审查记录 |

## 3. 与国内备案的字段映射（外部，BLOCKED）

Play data safety 之外，国内上架还需以下登记信息；**六项全部未取得**，一一对应到回填点：

| 字段 | 依赖 | 回填落点 | 状态 |
|---|---|---|---|
| 运营主体名称 | 备案主体 | `PrivacyPolicyCopy.OPERATOR_NAME` | BLOCKED |
| App 备案号（工信部） | 接入服务商/分发平台提交 | `PrivacyPolicyCopy.APP_FILING_NUMBER` | BLOCKED |
| 联系方式 | 备案完成后确定 | `PrivacyPolicyCopy.CONTACT` | BLOCKED |
| 隐私政策公开 URL | 开发者域名与托管 | 应用内跳转 + 商店详情页（H-024） | BLOCKED |
| 模型名称/备案号/上线编号 | CAC 已备案名单 + 属地网信登记 | `docs/ai-content-disclosure-copy.md` §3 第 6 条（H-053） | BLOCKED |
| Play 表单提交回执 | Play 开发者账号 | 本文 §2 的答案提交后回填回执 | BLOCKED（H-036） |

## 4. 复跑与校验

```bash
python -I tools/ci/h_compliance_gate.py --topic privacy
python -I tools/ci/h_compliance_gate.py --topic data-safety
./gradlew :app:testLocalFirstDebugUnitTest --tests "com.tingyun.smartmistakebook.PrivacyPolicyCopyTest"
```

基线（2026-11-08，改动前）：privacy 主题 7 PASS / H-024 FAIL（外部）/ H-025 FAIL（未成年人专章缺失）；
data-safety 主题 8 PASS / H-036 FAIL（外部）。本线补上未成年人专章后复跑结果见
`docs/research/evidence/2026-11-09-batch-50/privacy/`。
