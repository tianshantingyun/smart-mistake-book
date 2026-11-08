package com.tingyun.smartmistakebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用内隐私政策页的**文案与占位符口径**（批次 50 隐私备案线）。
 *
 * 这里钉住四类会真实伤害用户/合规的失败：
 * - 政策页缺章：用户找不到"哪些数据离开本机""怎么删数据""未成年人怎么办"；
 * - 备案号/运营主体被编造：填一个看起来像真的号码比留空更危险，所以留空必须有明确
 *   "尚未取得"表述，且任何字符串都不许出现伪造的备案号形态；
 * - 数据清单与真实存储面对不上：备份四类、临时文件、设置必须逐项在册；
 * - 未成年人专章缺失：AI 文案 §3 第 5 条要求家长"阅读《隐私政策》中未成年人个人信息保护
 *   章节"，该章节不存在时这句话就是空指针。
 */
class PrivacyPolicyCopyTest {

    private val expectedChapterTitles = listOf(
        "我们保存哪些数据",
        "什么情况下内容会离开本机",
        "我们不发送什么",
        "本机数据的删除与备份",
        "通知与系统权限",
        "未成年人个人信息保护",
        "AI 生成内容与标识",
        "备案与运营主体信息",
        "政策版本与更新",
    )

    @Test
    fun `policy states a version and an effective date`() {
        assertTrue(
            "版本号必须是可直接印在政策页上的日期形态",
            Regex("""\d{4}-\d{2}-\d{2}""").matches(PrivacyPolicyCopy.POLICY_VERSION),
        )
        assertTrue(
            "生效日期必须是日期形态",
            Regex("""\d{4}-\d{2}-\d{2}""").matches(PrivacyPolicyCopy.EFFECTIVE_DATE),
        )
        val header = PrivacyPolicyCopy.headerText()
        assertTrue(header.contains(PrivacyPolicyCopy.POLICY_VERSION))
        assertTrue(header.contains(PrivacyPolicyCopy.EFFECTIVE_DATE))
    }

    @Test
    fun `every required chapter is present once and in reading order`() {
        val titles = PrivacyPolicyCopy.sections(networkOffline = false).map { it.title }
        assertEquals(expectedChapterTitles, titles)
        PrivacyPolicyCopy.sections(networkOffline = false).forEach { section ->
            assertTrue("章节「${section.title}」不能为空", section.paragraphs.isNotEmpty())
            section.paragraphs.forEach { paragraph ->
                assertTrue(
                    "章节「${section.title}」有空段落",
                    paragraph.isNotBlank(),
                )
            }
        }
    }

    @Test
    fun `unfilled filing number and operator say so instead of inventing values`() {
        // 外部信息（备案号、运营主体、联系方式）在取得前必须留空；填了才允许显示。
        assertTrue(
            "未取得 App 备案号时必须留空，不得写占位数字",
            PrivacyPolicyCopy.APP_FILING_NUMBER.isBlank() ||
                Regex("""^[\u4e00-\u9fa5]?ICP备\d{6,}号(-?\d+)?$""")
                    .matches(PrivacyPolicyCopy.APP_FILING_NUMBER),
        )
        val filing = PrivacyPolicyCopy.filingNumberText()
        if (PrivacyPolicyCopy.APP_FILING_NUMBER.isBlank()) {
            assertTrue("留空时必须如实说明尚未取得", filing.contains("尚未取得"))
        }
        val operator = PrivacyPolicyCopy.operatorDisclosureText()
        if (PrivacyPolicyCopy.OPERATOR_NAME.isBlank()) {
            assertTrue("运营主体留空时必须如实说明", operator.contains("尚未取得"))
        }
        val contact = PrivacyPolicyCopy.contactText()
        if (PrivacyPolicyCopy.CONTACT.isBlank()) {
            assertTrue("联系方式留空时必须如实说明", contact.contains("尚未取得"))
        }
    }

    @Test
    fun `no policy string fabricates a filing number or a备案-like code`() {
        val all = buildString {
            append(PrivacyPolicyCopy.headerText())
            append(PrivacyPolicyCopy.filingNumberText())
            append(PrivacyPolicyCopy.operatorDisclosureText())
            append(PrivacyPolicyCopy.contactText())
            PrivacyPolicyCopy.sections(networkOffline = false).forEach { section ->
                append(section.title)
                section.paragraphs.forEach(::append)
            }
        }
        // 伪造形态：ICP 备 + 数字、或 8 位以上连续数字（备案号/统一社会信用代码片段）。
        assertFalse("政策文案里出现了疑似备案号", Regex("""ICP\s*备\s*\d{4,}""").containsMatchIn(all))
        assertFalse("政策文案里出现了长数字串（疑似备案号/信用代码）", Regex("""\d{8,}""").containsMatchIn(all))
    }

    @Test
    fun `local data inventory matches the real backup categories and temp files`() {
        val text = PrivacyPolicyCopy.sections(networkOffline = false)
            .first { it.title == "我们保存哪些数据" }
            .paragraphs
            .joinToString("\n")
        listOf("错题本", "掌握库", "讲题会话", "题图", "设置", "临时").forEach { token ->
            assertTrue("数据清单缺「$token」", text.contains(token))
        }
    }

    @Test
    fun `external send section names what leaves and what never leaves`() {
        val online = PrivacyPolicyCopy.sections(networkOffline = false)
            .first { it.title == "什么情况下内容会离开本机" }
            .paragraphs.joinToString("\n")
        assertTrue(online.contains("你选择的模型服务"))
        assertTrue(online.contains("当前题目"))
        assertTrue(online.contains("主动发起"))
        val never = PrivacyPolicyCopy.sections(networkOffline = false)
            .first { it.title == "我们不发送什么" }
            .paragraphs.joinToString("\n")
        listOf("API Key", "无关的错题", "完整学习历史").forEach { token ->
            assertTrue("不发送清单缺「$token」", never.contains(token))
        }
        val offline = PrivacyPolicyCopy.sections(networkOffline = true)
            .first { it.title == "什么情况下内容会离开本机" }
            .paragraphs.joinToString("\n")
        assertTrue("离线版必须说明不联网", offline.contains("不联网"))
        assertFalse("离线版不能描述正在发生的上传", offline.contains("你选择的模型服务"))
    }

    @Test
    fun `minor chapter carries the guardian rule that the AI copy points to`() {
        val minor = PrivacyPolicyCopy.sections(networkOffline = false)
            .first { it.title == "未成年人个人信息保护" }
            .paragraphs.joinToString("\n")
        assertTrue(minor.contains("未成年人个人信息"))
        assertTrue(minor.contains("不满 14 周岁"))
        assertTrue(minor.contains("监护人"))
        assertTrue("必须说明监护人可以拒绝/撤回", minor.contains("拒绝") || minor.contains("撤回"))
    }

    @Test
    fun `deletion and backup chapter matches the storage screen scope`() {
        val text = PrivacyPolicyCopy.sections(networkOffline = false)
            .first { it.title == "本机数据的删除与备份" }
            .paragraphs.joinToString("\n")
        listOf("数据库", "题图", "设置", "临时文件", "不会自动备份").forEach { token ->
            assertTrue("删除/备份章缺「$token」", text.contains(token))
        }
    }
}
