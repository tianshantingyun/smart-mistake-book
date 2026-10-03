package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RootNavigationPolicyTest {
    @Test
    fun `root destinations select themselves`() {
        listOf(Routes.Review, Routes.Tutor, Routes.Library, Routes.Profile).forEach { route ->
            assertEquals(route, bottomBarRouteFor(route))
        }
    }

    /**
     * 讲题只有"一个页面"：底部「智能体」、拍照讲解落下的会话、错题详情「讲解这道题」（复习
     * 预判与判题复核也走它）、历史重开都停在同一格上。三个入口各自带什么题由导航参数决定
     * （会话 id 或 entryId/problemId/problemRevisionId），底栏始终是「智能体」。
     */
    @Test
    fun `every tutor entry keeps the tutor destination selected`() {
        assertEquals(Routes.Tutor, bottomBarRouteFor(Routes.Tutor))
        assertEquals(Routes.Tutor, bottomBarRouteFor(Routes.CapturedTutorSession))
        assertEquals(Routes.Tutor, bottomBarRouteFor(Routes.MistakeTutor))
        assertEquals(Routes.Tutor, bottomBarRouteFor(Routes.TutorTextConversation))
        assertEquals(Routes.Tutor, bottomBarRouteFor(Routes.TutorHistory))
    }

    /**
     * 录入只有一个入口（L6）：错题本与智能体（大厅、历史文字会话）都指到同一条路由模式，
     * 来源只是它的一个参数。改前 `capture/tutor` 与 `capture/library` 是两条并列路由——
     * 同一屏两个意图，看起来像两个功能（用户裁定："它不管是批量目录还是单个体目录都一样，
     * 都是录入"）。这条断言钉住：任何来源都在这一个模式上，且来源可往返解码。
     */
    @Test
    fun `every capture entry resolves to the same single capture route pattern`() {
        val pattern = Routes.Capture
        val prefix = pattern.substringBefore("{")
        assertTrue(
            "唯一录入路由必须是带来源参数的单一模式（实际：$pattern）",
            pattern.contains("{${Routes.CaptureOriginArgument}}"),
        )
        CaptureEntryOrigin.entries.forEach { origin ->
            val target = Routes.capture(origin)
            assertTrue(
                "来源 $origin 的落点 $target 必须落在唯一录入入口 $pattern 上",
                target.startsWith(prefix),
            )
            assertEquals(
                "来源必须能从这条落点里解回来",
                origin,
                Routes.captureEntryOrigin(target.removePrefix(prefix)),
            )
        }
    }

    @Test
    fun `capture origin argument decoding fails fast on an unknown value`() {
        assertEquals(CaptureEntryOrigin.TUTOR, Routes.captureEntryOrigin("tutor"))
        assertEquals(CaptureEntryOrigin.LIBRARY, Routes.captureEntryOrigin("library"))
        // 没有来源（框架恢复等场景）= 按"存入错题本"处理。
        assertEquals(CaptureEntryOrigin.LIBRARY, Routes.captureEntryOrigin(null))
        assertEquals(CaptureEntryOrigin.LIBRARY, Routes.captureEntryOrigin("  "))
        // 接线错误不静默降级：静默会把讲题拍的照片存进错题本。
        assertThrows(IllegalArgumentException::class.java) {
            Routes.captureEntryOrigin("lobby")
        }
    }

    /** 录入流程是模态工作流，不进底栏；整卷与拆分复核是它内部的步骤，同样不进。 */
    @Test
    fun `the capture workflow stays out of the bottom bar while it runs`() {
        assertNull(bottomBarRouteFor(Routes.capture(CaptureEntryOrigin.TUTOR)))
        assertNull(bottomBarRouteFor(Routes.capture(CaptureEntryOrigin.LIBRARY)))
        assertNull(bottomBarRouteFor(Routes.BatchImport))
        assertNull(bottomBarRouteFor(Routes.SplitReview))
    }

    @Test
    fun `secondary workflows do not acquire the bottom bar`() {
        listOf(
            Routes.ReviewSession,
            Routes.capture(CaptureEntryOrigin.TUTOR),
            Routes.capture(CaptureEntryOrigin.LIBRARY),
            Routes.SplitReview,
            Routes.BatchImport,
            Routes.LibraryBatchExport,
            Routes.CaptureResume,
            Routes.MistakeDetail,
            Routes.MistakeExport,
            Routes.Capability,
            Routes.LearningMastery,
            Routes.Privacy,
            Routes.Reminder,
            Routes.Storage,
            null,
        ).forEach { route -> assertNull(bottomBarRouteFor(route)) }
    }
}
