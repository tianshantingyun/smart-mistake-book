# 发布外部阻塞（BLOCKED）清单与解锁动作（批次 51 · 发布线）

- 批次：51（2026-11-10/11）｜计划：`.zcode/plans/plan-2026-11-10-batch-51-final.md:90-91`（item 37/38）
- 口径：**本文件只登记 BLOCKED 与出口，不产生任何 PASS**。下列各项在本机/本会话内均不可闭合。
- 当前可核事实：HEAD `8dd29e963975ff44703f638064322be0e7494f98`；工作树 683 条脏（`-uall` 5443+）；
  本地 release 包已出（versionCode 101177，见 `release-51.md`），签名身份是
  `CN=SmartMistakeBook Local Release, OU=NOT-PLAY-PRODUCTION`（`keytool-keystore.txt:6`），**不是 Play 生产身份**。

## 0. 一览

| # | 项 | 状态 | 本机事实（证据） | 解锁动作（谁） | 解锁判据 |
|---|---|---|---|---|---|
| X-1 | Play 开发者账号 / Play Console | **BLOCKED（外部）** | 仓内无 Play 凭据文件（`git ls-files` 无 service-account/Play key）；`docs/acceptance/blocked-runbook.md:506` 列为 J-5 外部组 | 账号持有人注册/提供 Play Console 访问；建内部/封闭轨道（`blocked-runbook.md:114-120`） | 轨道建立回执 + 测试账号登记在盘；J-017/J-073…J-090 逐行复跑 |
| X-2 | 生产签名 keystore / 上传密钥 | **BLOCKED（外部）** | 四变量齐但钥匙是本地测试钥：DN 含 `OU=NOT-PLAY-PRODUCTION`；仓内 0 个被跟踪 keystore（`baseline-commands.txt`）；`RELEASE.md:20-24` 只定义 env 契约 | 生产密钥持有人生成/托管上传密钥，按 `RELEASE_*` 注入（`2026-11-09-release-signing.md:482` B-4） | `signing` 门指纹 == 已登记上传证书指纹；证书覆盖发布窗口 |
| X-3 | App 备案号（工信部 ICP / App 备案） | **BLOCKED（外部）** | 应用内政策页已留占位并渲染「尚未取得」，代码拒绝伪造（`app/src/main/kotlin/com/tingyun/smartmistakebook/PrivacyPolicyCopy.kt:26-33,49-54`）；清单列「适用（前置阻断项）」（`docs/research/2026-10-25-store-launch-materials-checklist.md:12`） | 发布者经网络接入服务商/分发平台提交备案，取得备案号；回填 `APP_FILING_NUMBER` 与商店详情页（B-1，`2026-11-09-release-signing.md:478`） | 备案号在盘 + 应用内/商店详情页同值；H-024/H-025/H-036/H-043/H-053/H-081 复跑 |
| X-4 | 公网可访问的隐私政策 URL | **BLOCKED（外部）** | 应用内已有政策全文与入口（`SecondaryScreens.kt:658-695`、`PrivacyPolicyCopy.kt:4-16`），但无公网托管 URL；Play 要求商店页与应用内都要有可访问链接（`docs/research/2026-11-02-kb-r6-ledger-and-16kb.md:783`） | 发布者提供开发者域名 + HTTPS 托管（非 PDF、无需登录），把同一文案发布到公网并在 Play 商店页填写（B-7，`2026-11-09-release-signing.md:484`） | URL 公网可访问（有效 SSL）+ 应用内/商店页同链接；H 域对应断言 exit 0 |
| X-5 | 图像 provider（MVA IMG 臂） | **BLOCKED（外部）** | 无图像 provider；且栅格上没有 1e-9 结构化事实，IMG 臂上限 63/120（`docs/research/evidence/2026-11-10-batch-51/mva/README.md:224-225`） | 用户/Provider 持有人提供图像能力 provider（base URL + model ID；密钥只经设备输入，不进源码） | IMG 臂有真实可评样本并跑 `tools/figure_eval/mva_gate.py`；在此之前 MVA 不判 PASS、`visualEngine` 不注入/不广告（用户裁定） |

> 机器可核的“仍缺”：`env | grep -c '^RELEASE_'` 在未 source 本地 env 文件时为 **0**；
> 仓内被跟踪 keystore 数 **0**；`adb` 不在本 shell PATH，用全路径
> `C:/Android/Sdk/platform-tools/adb.exe devices` 实跑只有 `emulator-5592`
> （x86_64 / `PAGE_SIZE=4096` / API 34，`device-probe.txt`）；`bundletool-all` 仍不存在（只有库 jar）。

## 1. X-1 Play 账号 / 商店轨道

- 缺失条件与行清单：`docs/acceptance/blocked-runbook.md:506`（J-5 组：J-017、J-073…J-090），
  环境类汇总见 `blocked-runbook.md:111-120`。
- 需要的外部动作（按 `docs/research/2026-10-25-store-launch-materials-checklist.md` 备料）：
  1) 账号持有人开通 Play Console 并授权本发布负责人；
  2) 建内部/封闭测试轨道，登记测试账号；
  3) 商店素材（图标/截图/描述/AI 生成说明）按清单备齐；
  4) 上传 AAB（上传前先完成 X-2 的生产/上传密钥注入）。
- 本机已有可交付物：两个 flavor 的 AAB（`app-strictOffline-release.aab` / `app-localFirst-release.aab`，
  sha256 见 `artifacts.sha256`），但**只能作为候选上传件**，其签名身份是本地测试钥（X-2）。
- 不做的部分：不伪造轨道回执、不把本地包说成已上传、不填任何 Play 后台字段。

## 2. X-2 生产签名 keystore / 上传密钥

- 现状：`validateReleaseSigning` 四变量在本次构建中齐备且门 exit 0；证书 SHA-256
  `2c8415b2…5eba`、有效期至 2054-03-26（`keytool-keystore.txt:6-13`、`signing-*.json`）。
  但 DN 明写 `OU=NOT-PLAY-PRODUCTION`，它是**本地测试钥匙**，不是生产/上传钥匙。
- 启用 Play App Signing 后，本地钥匙只是上传密钥；用户设备上的证书由 Google 托管
  （`2026-11-09-release-signing.md:312-322` §4.5）。因此本文件的 `signing` 门只证明
  “APK/AAB 同身份 + 证书在有效期内”，**不证明**商店发布证书正确。
- 解锁动作：生产密钥持有人按 `2026-11-09-release-signing.md:257-280` 生成/交付上传密钥 ->
  主会话注入 `RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`/`RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`
  -> 重跑 `assemble/bundle*Release` + `j_release_verify.py signing` -> Play Console 登记上传证书指纹。
- 红线：密钥只从环境变量/密钥服务读取；源码、示例、测试、证据文件都不得出现可用口令。
  本次证据只保留证书 DN/指纹（公开信息）与变量**名**。

## 3. X-3 备案号（工信部 App 备案；关联教育部备案）

- 应用内已实现：政策页“备案与运营主体信息”章节，`APP_FILING_NUMBER`/`OPERATOR_NAME`/`CONTACT`
  为空时渲染「尚未取得」，**禁止**填看起来像真的号码（`PrivacyPolicyCopy.kt:23-33,42-61`）。
- 备案适用性：`docs/research/2026-10-25-store-launch-materials-checklist.md:12`（前置阻断项）。
  关联项：教育移动应用备案（同文件 `:14`，经 `app.eduyun.cn` 提供者备案）——本产品为工具类学习 App，
  清单判定适用；两项都需主体资质，本机不可代办。
- 解锁动作：发布者完成备案 -> 提供备案号/运营主体/联系方式 -> 回填 `PrivacyPolicyCopy.kt`
  三个常量并更新 `docs/current/privacy-and-data-safety.md` -> 重跑 H 域断言
  （`python -I tools/ci/h_compliance_gate.py --only H-024,H-025,H-036,H-043,H-053,H-081`）
  与政策文案 JVM 用例。
- 禁止：为了让页面“看起来完整”而编造备案号/主体/邮箱（代码注释与用例已明确禁止）。

## 4. X-4 公网隐私政策 URL

- 现状：应用内入口与全文已在（`DataPrivacyScreen` 的 `privacy_policy_open`、
  `SecondaryScreens.kt:658-695`；文案唯一源 `PrivacyPolicyCopy.kt`）。缺的是**公网可访问 URL**，
  供 Play 商店页与应用内链接使用（`2026-11-02-kb-r6-ledger-and-16kb.md:783` 引官方要求：
  商店页 + 应用内都要有，公开可访问、有效 SSL、非 PDF）。
- 解锁动作：发布者提供开发者域名与 HTTPS 托管 -> 发布同一版本文案（版本 `2026-11-08`，
  `PrivacyPolicyCopy.kt:19-21`）-> 商店页与应用内入口都指向该 URL -> 复跑 H 域断言。
- 文档同步：`docs/current/privacy-and-data-safety.md` 与 `docs/current/play-data-safety-answers.md`
  的 data safety 口径需与本 URL 一致（BYOK 内容会离开设备，不能写“完全不收集”）。

## 5. X-5 图像 provider（MVA IMG 臂）

- 现状：MVA JS 臂在本批有真实样本尝试，但作者程序不可编译，仍 0 可评样本；
  IMG 臂因**无图像 provider** 且栅格无结构化事实保持 BLOCKED
  （`docs/research/evidence/2026-11-10-batch-51/mva/README.md:200-225`）。
- 解锁动作：用户/Provider 持有人提供图像理解 provider（base URL + model ID；API key 只经设备
  `模型设置` 输入，不写入源码/证据）-> 在 IMG 臂采集 ≥120 题真实样本 -> 事实核验 + VLM judge
  -> `python -I tools/figure_eval/mva_gate.py`。
- 与发布的关系：用户裁定“MVA 未过则 `visualEngine` 不注入、能力不广告”。本清单不改变该状态；
  X-5 未解锁前，MVA 门只能记 `NOT_EVALUABLE/BLOCKED`，不得写 PASS，也不得据此投放 JS 广告。

## 6. 关联但不属于本次“外部五项”的未闭合项（如实登记）

| 项 | 状态 | 说明 |
|---|---|---|
| arm64 / 16KB 真机运行时 | **NOT_RUN** | 仅有 x86_64 模拟器 `emulator-5592`（`PAGE_SIZE=4096` / API 34，`device-probe.txt`）；x86_64 宿主不能起 arm64 AVD。16KB 静态门已过（`apk16k`/`aab16k`），运行时 `getconf PAGE_SIZE=16384` 未取证（B-5，`2026-11-09-release-signing.md:482`） |
| `bundletool-all` 官方 AAB 交叉验证 | **NOT_RUN** | 本机只有 `bundletool-1.18.3.jar` 库 jar（无 Main-Class）；官方 `dump config` 未跑（B-6，`:483`） |
| Play data safety 表单 | **BLOCKED（外部）** | 需 Play 后台与 X-2/X-3/X-4 输入；草稿口径与 BYOK 出端事实需一致（`2026-11-06-compliance-acceptance.md:16,20`） |
| 生产包上传/审核 | **BLOCKED（外部）** | 依赖 X-1/X-2；本次产物不得称“已发布” |
