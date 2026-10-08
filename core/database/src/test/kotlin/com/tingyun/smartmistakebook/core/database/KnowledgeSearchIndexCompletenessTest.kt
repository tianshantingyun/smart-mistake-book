package com.tingyun.smartmistakebook.core.database

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ④-6（K1 批 1）判定缓存的 JVM 语义钉（纯 Kotlin，无 Android 依赖）。
 *
 * 它消灭的具体失败：缓存语义（命中/失效/在途验证作废）只有仪器化用例才能覆盖时，
 * core:database 的 JVM 门会漏掉这块纯逻辑——而它正是"召回漏新装节点"的正确性核心。
 *
 * 与 `KnowledgeSearchIndexCompletenessInstrumentedTest` 的分工：这里钉缓存状态机本身；
 * 那里钉"生产写路径的失效接线真的接上了"。
 */
class KnowledgeSearchIndexCompletenessTest {

    @Test
    fun freshCacheIsNotVerifiedCompleteAndInvalidateIsIdempotent() {
        val cache = KnowledgeSearchIndexCompleteness()
        assertFalse(cache.isVerifiedComplete("MATH"))

        cache.invalidate("MATH")
        assertFalse("无判定的失效是空操作（不得因此产生判定）", cache.isVerifiedComplete("MATH"))
    }

    @Test
    fun markedSubjectIsCompleteAndInvalidateClearsIt() {
        val cache = KnowledgeSearchIndexCompleteness()
        val generation = cache.readInvalidationGeneration("MATH")

        cache.markVerifiedCompleteIfUnchanged("MATH", generation)
        assertTrue(cache.isVerifiedComplete("MATH"))

        cache.invalidate("MATH")
        assertFalse("写路径失效后不得再命中", cache.isVerifiedComplete("MATH"))
    }

    @Test
    fun markIsRejectedWhenInvalidationHappenedDuringVerification() {
        val cache = KnowledgeSearchIndexCompleteness()
        val observedGeneration = cache.readInvalidationGeneration("MATH")

        // 验证期间写路径失效（安装/重建/确认）——在途结论必须作废。
        cache.invalidate("MATH")
        cache.markVerifiedCompleteIfUnchanged("MATH", observedGeneration)

        assertFalse(
            "在途验证不得把失效前的旧读落成完整判定（否则重建失败回滚会留下永久漏召回）",
            cache.isVerifiedComplete("MATH"),
        )
    }

    @Test
    fun markAfterObservingThePostInvalidationGenerationIsAccepted() {
        val cache = KnowledgeSearchIndexCompleteness()
        cache.invalidate("MATH")

        val generation = cache.readInvalidationGeneration("MATH")
        cache.markVerifiedCompleteIfUnchanged("MATH", generation)

        assertTrue(cache.isVerifiedComplete("MATH"))
    }

    @Test
    fun markIsRejectedForAnotherSubjectOnly() {
        val cache = KnowledgeSearchIndexCompleteness()
        val mathGeneration = cache.readInvalidationGeneration("MATH")
        cache.invalidate("PHYSICS")

        cache.markVerifiedCompleteIfUnchanged("MATH", mathGeneration)

        assertTrue("失效必须按科隔离——PHYSICS 的写不得作废 MATH 的判定", cache.isVerifiedComplete("MATH"))
        assertFalse(cache.isVerifiedComplete("PHYSICS"))
    }

    @Test
    fun disabledInvalidationCanaryLeavesTheStaleClaimInPlace() {
        // 负向用例的"摘线"形态：失效接线断开时，旧判定原地留存——这就是
        // KnowledgeSearchIndexCompletenessInstrumentedTest 的 canary 断言依赖的语义。
        val cache = KnowledgeSearchIndexCompleteness(invalidationEnabled = false)
        val generation = cache.readInvalidationGeneration("MATH")
        cache.markVerifiedCompleteIfUnchanged("MATH", generation)

        cache.invalidate("MATH")

        assertTrue("invalidationEnabled=false 时失效是空操作（测试缝语义）", cache.isVerifiedComplete("MATH"))
    }

    /**
     * 并发下 mark 与 invalidate 的竞争：每轮先把失效代号读定为"失效前"，再让 main 线程失效，
     * 最后才落 mark——所有 mark 携带的都是失效前代号，必须全部被拒（check-then-write 不原子
     * 的实现会在失效清空之后又把判定写回去，本用例逐轮判红）。
     */
    @Test
    fun marksObservedBeforeAnInvalidationNeverSurviveTheInvalidation() {
        val cache = KnowledgeSearchIndexCompleteness()
        val markerCount = 2
        val rounds = 100
        repeat(rounds) { round ->
            val observed = CountDownLatch(markerCount)
            val invalidated = CountDownLatch(1)
            val marked = CountDownLatch(markerCount)
            val markers = List(markerCount) {
                Thread {
                    val generation = cache.readInvalidationGeneration("MATH")
                    observed.countDown()
                    invalidated.await()
                    cache.markVerifiedCompleteIfUnchanged("MATH", generation)
                    marked.countDown()
                }.also(Thread::start)
            }
            assertTrue(observed.await(30, TimeUnit.SECONDS))
            cache.invalidate("MATH")
            invalidated.countDown()
            assertTrue(marked.await(30, TimeUnit.SECONDS))
            markers.forEach(Thread::join)
            assertFalse(
                "第 $round 轮：失效前观察到的验证结论在失效后仍然命中（CAS 未生效）",
                cache.isVerifiedComplete("MATH"),
            )
        }
    }

    @Test
    fun verificationQuerySeamRecordsDaoMethodLabels() {
        val cache = KnowledgeSearchIndexCompleteness()
        cache.recordVerificationQuery(SearchIndexVerificationQueries.READ_INDEX_VERSION)
        cache.recordVerificationQuery(SearchIndexVerificationQueries.COUNT_REVIEWED)
        cache.recordVerificationQuery(SearchIndexVerificationQueries.COUNT_INDEXED)

        assertEquals(
            listOf(
                SearchIndexVerificationQueries.READ_INDEX_VERSION,
                SearchIndexVerificationQueries.COUNT_REVIEWED,
                SearchIndexVerificationQueries.COUNT_INDEXED,
            ),
            cache.issuedVerificationQueries.toList(),
        )
    }
}
