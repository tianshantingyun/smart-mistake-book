# Privacy, In-App Policy and Play Data Safety

> **口径（批次 50 隐私备案线）**：应用内《隐私政策》的**唯一文案源**是
> `app/src/main/kotlin/com/tingyun/smartmistakebook/PrivacyPolicyCopy.kt`；本文是 H 域验收门
> 读取的文档镜像与外部项登记台。修改政策实质内容时两处同时改，并用
> `PrivacyPolicyCopyTest`（8 条）与 `h_compliance_gate.py --topic privacy` 双向复跑。
> Play 表单逐项答案见 `docs/current/play-data-safety-answers.md`（本页只保留结论行）。

## In-app privacy disclosure

The app is local-first. Learning records, canonical question images, review
history, and tutor conversations remain in app-private storage by default.

应用内政策共 9 章（版本 `2026-11-08`，生效 `2026-11-08`）：

1. 我们保存哪些数据——错题本、掌握库、讲题会话、题图、设置、临时内容；
2. 什么情况下内容会离开本机——联网版只在用户主动发起时发送给用户自选的模型服务（BYOK）；
3. 我们不发送什么——API Key/授权头、Keystore 材料、无关的错题与完整学习历史、租约与预算；
4. 本机数据的删除与备份——删除全部数据删除数据库/题图/设置/临时文件，且不会自动备份；
5. 通知与系统权限——通知、开机完成、联网（仅联网版）；
6. 未成年人个人信息保护（见下节）；
7. AI 生成内容与标识——"AI 生成"角标与导出文件的可见/隐式标识；
8. 备案与运营主体信息——外部信息，未取得时如实写"尚未取得"；
9. 政策版本与更新。

When the user configures a BYOK model provider and explicitly sends a request,
the app discloses exactly what leaves the device:

- the current question text and current student message;
- explicitly granted source images for the current capture session;
- the selected provider and model identity;
- a bounded teaching context that excludes unrelated mistakes and full history.

The app never sends API keys, Keystore material, leases, dispatch budgets, or
unrelated local facts.

## Data deletion

The storage screen offers:

- complete backup creation through the system document picker;
- backup validation before restore;
- staged restore with rollback on failure;
- delete-all-data with an explicit confirmation dialog.

Deleting all data removes the database, canonical assets, preferences, and
temporary files. The app should be restarted afterwards. 系统云备份与换机迁移
已关闭（`allowBackup=false`，database/sharedpref/file 等域全部排除），备份只走
用户显式导出的文件。

## 未成年人个人信息保护

- 本应用面向中学生等未成年学习者设计，不含社交、私信、评论、广告与内购；
- 未成年人个人信息按《中华人民共和国个人信息保护法》第三十一条处理：处理不满 14 周岁
  未成年人个人信息，应当取得父母或者其他监护人的同意；未取得同意前不做需要监护人同意的处理；
- 监护人可拒绝或撤回同意，并可在存储页删除全部本机数据；
- 遥测侧另有独立的年龄门（`TelemetryGate`）：14 岁以下与 14–17 岁均要求监护人确认，
  未披露年龄 fail-closed，首装默认关闭且 v1 无上传端点。

> 该章节是 `docs/ai-content-disclosure-copy.md` §3 第 5 条（"请家长或监护人阅读《隐私政策》中
> 未成年人个人信息保护章节"）的实际落点。此前该引用指向不存在的章节，H-025 因此 FAIL。

## Play Data safety answers

The following is a draft to be reviewed before upload:

- Data collected: none by the app itself; user-chosen BYOK requests are sent
  only to the user-selected provider endpoint.
- Data shared: only the explicitly disclosed current question/context and
  granted images during an authorized model request.
- Data encryption: local data is protected by app sandbox; backups use the
  system storage framework and are not cloud-synced.
- Deletion: in-app delete-all-data removes local records.

逐题清单（数据类别 × 收集/共享 × 用途 × 传输加密 × 删除方式）与代码证据见
`docs/current/play-data-safety-answers.md`。**Play Console 表单本身未提交**：
需开发者账号与已发布政策 URL，属外部条件（H-036 BLOCKED）。

## 外部登记信息（未取得，BLOCKED）

| 字段 | 当前值 | 状态 | 依赖 / 回填落点 |
|---|---|---|---|
| 运营主体名称 | 空 | **BLOCKED** | 备案主体信息；回填 `PrivacyPolicyCopy.OPERATOR_NAME` |
| App 备案号（工信部 ICP，含 App） | 空 | **BLOCKED** | 经接入服务商/分发平台提交；回填 `PrivacyPolicyCopy.APP_FILING_NUMBER` |
| 联系方式 | 空 | **BLOCKED** | 备案完成后确定；回填 `PrivacyPolicyCopy.CONTACT` |
| 隐私政策公开 URL | 无 | **BLOCKED**（H-024） | 开发者域名与托管；回填应用内"查看隐私政策全文"跳转与商店详情页 |
| 所用模型名称/备案号/上线编号 | 占位符 | **BLOCKED**（H-053） | CAC 已备案信息公告名单 + 属地网信登记；回填 `docs/ai-content-disclosure-copy.md` §3 第 6 条 |
| Play Console data safety 表单 | 未提交 | **BLOCKED**（H-036） | Play 开发者账号 + 上架 URL |

**纪律**：以上六项在取得真实值前一律留空并显示"尚未取得"，禁止填写任何猜测值。
`PrivacyPolicyCopyTest` 有一条反伪造用例（扫描 ICP 备号形态与 8 位以上连续数字），
填错形态会直接红。
