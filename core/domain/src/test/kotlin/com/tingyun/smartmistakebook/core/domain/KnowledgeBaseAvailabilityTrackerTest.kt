package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识能力就绪位（D-Q3：首装后台化）。
 *
 * 消灭的失败：安装状态此前只存在于 app 层的安装协程里（try/catch 的局部变量），
 * 消费点看不到它，于是各自对"没准备好"给出不同的假答案——零命中、无材料、没有记录。
 * 本类把状态做成可观察的写入面：消费者只读 [KnowledgeBaseAvailabilityTracker.state]，
 * 写入方（app 启动编排）按固定序列推进它。
 */
class KnowledgeBaseAvailabilityTrackerTest {

    @Test
    fun `initial state is preparing not ready`() {
        // 初值必须是 Preparing：进程刚起来时"内容已就位"还没有任何证据，
        // 默认 Ready 就等于把改前的静默零命中当默认行为。
        val tracker = KnowledgeBaseAvailabilityTracker()

        assertEquals(KnowledgeBaseAvailability.Preparing, tracker.state.value)
        assertFalse(tracker.state.value.isReady)
    }

    @Test
    fun `install success moves preparing to ready`() {
        val tracker = KnowledgeBaseAvailabilityTracker()

        tracker.markReady()

        assertEquals(KnowledgeBaseAvailability.Ready, tracker.state.value)
        assertTrue(tracker.state.value.isReady)
    }

    @Test
    fun `install failure carries the banner diagnostic id`() {
        val tracker = KnowledgeBaseAvailabilityTracker()

        tracker.markUnavailable("startup:knowledge:42")

        assertEquals(
            KnowledgeBaseAvailability.Unavailable("startup:knowledge:42"),
            tracker.state.value,
        )
        assertFalse("失败不是就绪：依赖知识库的能力仍要说'还没准备好'", tracker.state.value.isReady)
    }

    @Test
    fun `a retry returns to preparing and can reach ready`() {
        // 横幅重试的真实序列：失败 → 重试（准备中）→ 成功。
        // 重试期间必须回到 Preparing，否则消费点会继续用一个已经过期的成功态。
        val tracker = KnowledgeBaseAvailabilityTracker()
        tracker.markUnavailable("startup:knowledge:1")

        tracker.markPreparing()
        assertEquals(KnowledgeBaseAvailability.Preparing, tracker.state.value)

        tracker.markReady()
        assertEquals(KnowledgeBaseAvailability.Ready, tracker.state.value)
    }

    @Test
    fun `a failed retry keeps the same diagnostic id as the banner`() {
        val tracker = KnowledgeBaseAvailabilityTracker()
        tracker.markUnavailable("startup:knowledge:7")
        tracker.markPreparing()

        tracker.markUnavailable("startup:knowledge:7")

        assertEquals(
            "重试失败不产生第二个编号：学生看到的编号与日志必须能对上",
            KnowledgeBaseAvailability.Unavailable("startup:knowledge:7"),
            tracker.state.value,
        )
    }
}
