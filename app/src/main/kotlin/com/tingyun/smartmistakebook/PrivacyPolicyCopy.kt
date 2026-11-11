package com.tingyun.smartmistakebook

/**
 * 应用内《隐私政策》的**唯一文案源**（批次 50 隐私备案线）。
 *
 * 为什么单独成文件而不是直接写在 Compose 里：
 * - 政策文案是合规承诺，必须能被 JVM 用例逐字钉住（缺章、编造备案号、清单漏项都要能红）；
 * - 备案号/运营主体/联系方式是**外部信息**，取得前只能留空并如实说明"尚未取得"，
 *   留空的判定必须与渲染共用同一份常量，否则改一处就会撒谎。
 *
 * 事实来源（本文件不引入任何未核对的口径）：
 * - 本机数据面：`core/domain/BackupSelection.kt` 的四类（错题本/掌握库/会话/图片）+ 设置 + 临时文件；
 * - 外部发送面：`PRIVACY-DATA-FLOW.md` 与 `docs/current/privacy-and-data-safety.md`；
 * - 权限面：`app/src/main/AndroidManifest.xml` 与 `localFirst`/`strictOffline` 两个 flavor 清单；
 * - 删除面：`StorageScreen` 的"删除全部数据"确认文案（数据库/题图/设置/临时文件/不会自动备份）。
 */
internal object PrivacyPolicyCopy {

    /** 政策版本与生效日期。实质修改时两个一起改，并同步 `docs/current/privacy-and-data-safety.md`。 */
    const val POLICY_VERSION: String = "2026-11-08"
    const val EFFECTIVE_DATE: String = "2026-11-08"

    /**
     * 外部信息占位符：取得前保持空串。
     *
     * 空白时渲染"尚未取得"，**不得**填任何看起来像真的号码/主体/邮箱——伪造备案号
     * 比留空更危险（用户会据此核对监管名单）。回填条件见
     * `docs/current/play-data-safety-answers.md` §4 与
     * `docs/research/2026-10-25-store-launch-materials-checklist.md` 第 2/6/7 项。
     */
    const val OPERATOR_NAME: String = ""
    const val APP_FILING_NUMBER: String = ""
    const val CONTACT: String = ""

    data class Section(val title: String, val paragraphs: List<String>)

    private const val EMPTY_PLACEHOLDER = "尚未取得"

    fun headerText(): String =
        "隐私政策（版本 $POLICY_VERSION，生效日期 $EFFECTIVE_DATE）"

    fun operatorDisclosureText(): String =
        if (OPERATOR_NAME.isBlank()) {
            "运营主体：$EMPTY_PLACEHOLDER（备案主体信息取得后在本页公示）。"
        } else {
            "运营主体：$OPERATOR_NAME。"
        }

    fun filingNumberText(): String =
        if (APP_FILING_NUMBER.isBlank()) {
            "App 备案号：$EMPTY_PLACEHOLDER（完成 App 备案后在本页与商店详情页公示）。"
        } else {
            "App 备案号：$APP_FILING_NUMBER。"
        }

    fun contactText(): String =
        if (CONTACT.isBlank()) {
            "联系方式：$EMPTY_PLACEHOLDER（备案完成后公示可用的联系渠道）。"
        } else {
            "联系方式：$CONTACT。"
        }

    /**
     * 政策正文。联网版与离线版只在"内容会不会离开本机"一章分叉，其余章节一致——
     * 因为存储、删除、权限、未成年人条款与是否联网无关。
     */
    fun sections(networkOffline: Boolean): List<Section> = listOf(
        Section(
            title = "我们保存哪些数据",
            paragraphs = listOf(
                "本应用是本地优先的学习工具：下面这些数据默认只保存在这台设备的应用私有目录里，不上传服务器。",
                "错题本：题目与题面修订、练习单元、草稿、标签与知识点关系、题目侧的治理记录。",
                "掌握库：学习证据、掌握度投影、复习计划与队列、复习会话与教学建议。",
                "讲题会话：你与讲题功能的对话消息，以及消息引用的题图。",
                "题图：你拍摄或导入的原始图片保存在应用私有的图片目录，供题目与讲题使用。",
                "设置：模型服务地址、模型名称等本机设置；API Key 由系统安全存储保护，应用不会把它写进数据库、日志或备份。",
                "临时内容：未完成的临时照片按过期规则清理，不会自动进入错题本。",
            ),
        ),
        Section(
            title = "什么情况下内容会离开本机",
            paragraphs = if (networkOffline) {
                listOf(
                    "当前版本是离线版：应用不联网，也没有任何上传通道，你的内容只留在本机。",
                    "离线版仍然可以录入错题、复习与导出；需要模型参与的功能在你切换到联网版并自行配置模型服务之前不可用。",
                )
            } else {
                listOf(
                    "只有你在讲题、拍照识别或整理等操作中主动发起请求时，当前内容才会发送给你选择的模型服务（BYOK：由你自行配置服务地址、模型与密钥）。",
                    "一次请求会发送：当前题目与本次消息、你为本次请求明确授予的题图、所选服务商与模型标识，以及一段受限的教学上下文。",
                    "发送受本机预算与次数上限约束；应用进程被重建后不会自动续发，必须由你再次发起。",
                    "你随时可以在模型设置里清空配置；清空后应用不再向任何服务发起请求。",
                )
            },
        ),
        Section(
            title = "我们不发送什么",
            paragraphs = listOf(
                "不发送 API Key 与授权头、系统安全存储里的私钥材料。",
                "不发送无关的错题或完整学习历史；教学上下文只包含与本次请求相关的受限内容。",
                "不发送进程租约、调度预算与本地调度状态。",
                "不执行也不转发模型返回的任意链接、代码、SQL 或文件路径。",
                "应用不集成广告或第三方行为分析 SDK。",
            ),
        ),
        Section(
            title = "本机数据的删除与备份",
            paragraphs = listOf(
                "设置里的「删除全部数据」会删除本机数据库、题图、设置和临时文件，且不会自动备份；删除后需要重启应用。",
                "备份由你通过系统文件选择器主动创建，可以只选部分内容类别；恢复前会校验备份包，恢复失败会回滚。",
                "系统云备份与换机迁移已关闭（allowBackup=false，数据库/文件/偏好等域全部排除），备份只走你显式导出的文件。",
                "删除或恢复都会在存储页给出结果回执；失败时保留原有数据并说明原因。",
            ),
        ),
        Section(
            title = "通知与系统权限",
            paragraphs = listOf(
                "通知权限只用于复习提醒，可以拒绝，拒绝后应用的其他功能不受影响。",
                "开机完成与时间变更广播用于重排复习提醒闹钟；应用更新后重新登记，避免提醒静默失效。",
                "联网权限只存在于联网版；离线版在清单里显式移除联网与网络状态权限。",
                "应用不申请通讯录、位置、短信、通话记录、麦克风权限。",
            ),
        ),
        Section(
            title = "未成年人个人信息保护",
            paragraphs = listOf(
                "本应用面向中学生等未成年学习者设计。未成年人个人信息按《中华人民共和国个人信息保护法》第三十一条处理：处理不满 14 周岁未成年人个人信息，应当取得父母或者其他监护人的同意。",
                "不满 14 周岁的儿童使用前，请由父母或其他监护人阅读本政策并作出同意；未取得同意前，不进行需要监护人同意的处理。",
                "监护人可以拒绝或撤回同意，也可以在存储页删除全部本机数据；撤回后不影响撤回前基于同意已进行的处理。",
                "14–17 岁用户同样建议在监护人指导下使用；应用不含社交、私信、评论、广告与内购。",
                "本应用不建立账号体系，不收集与学习无关的身份信息。",
            ),
        ),
        Section(
            title = "AI 生成内容与标识",
            paragraphs = listOf(
                "讲题解析与配图由人工智能生成，相关内容旁显示「AI 生成」角标；导出的 PDF/Word 含「本文件含 AI 生成内容」的可见说明，并在文件属性中写入隐式标识。",
                "AI 生成内容可能存在错误或偏差，请以教材、课堂讲授和老师批改为准。",
                "所用模型服务的名称与备案信息见《AI 生成内容说明》；本应用自身的备案信息见下一章。",
            ),
        ),
        Section(
            title = "备案与运营主体信息",
            paragraphs = listOf(
                operatorDisclosureText(),
                filingNumberText(),
                contactText(),
                "以上三项属于外部登记信息，取得前本页如实写「$EMPTY_PLACEHOLDER」，不填写任何猜测值。",
            ),
        ),
        Section(
            title = "政策版本与更新",
            paragraphs = listOf(
                "当前政策版本 $POLICY_VERSION，自 $EFFECTIVE_DATE 起生效。",
                "政策发生实质变化时更新版本号，并在应用内提示；继续使用前你可以重新阅读本页。",
                "本政策只描述本应用当前实际的数据处理行为；新增数据面或新增外部服务时，先更新本页与随附的数据安全清单，再发版。",
            ),
        ),
    )
}
