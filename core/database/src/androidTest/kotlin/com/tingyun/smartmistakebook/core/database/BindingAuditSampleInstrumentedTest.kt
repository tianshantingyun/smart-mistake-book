package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.model.BindingAuditSampling
import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshot
import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshotCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 绑定抽样读写口（KF-29）的真库行为：配额封顶、内容寻址幂等、判定只落一次。
 *
 * 与 `FullMigrationMatrixInstrumentedTest` 的分工：那边管"迁移后表建没建出来、旧行在不在"，
 * 这里管 `BindingAuditSampleDao` 的三条不变量（KDoc 里逐条写着）。
 */
@RunWith(AndroidJUnit4::class)
class BindingAuditSampleInstrumentedTest {

    @Test
    fun quotaCapsEachSubjectWeekAndIsIndependentAcrossSubjectsAndWeeks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "binding-audit-quota-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val store = StudyDatabaseFactory.open(context, databaseName)
            val week40 = BindingAuditSampling.quotaPrefix("MATH", utcMillis("2026-10-04"))
            val week41 = BindingAuditSampling.quotaPrefix("MATH", utcMillis("2026-10-05"))
            val physics = BindingAuditSampling.quotaPrefix("PHYSICS", utcMillis("2026-10-04"))

            // 配额 3：第 4 条必须被拒，且不落库。
            assertTrue(record(store, week40, "unit-1"))
            assertFalse(
                "同 id 重放不新增样本、也不重复占配额（内容寻址幂等）",
                record(store, week40, "unit-1"),
            )
            assertTrue(record(store, week40, "unit-2"))
            assertTrue(record(store, week40, "unit-3"))
            assertFalse("配额满后第 4 条不得入队", record(store, week40, "unit-4"))
            assertEquals(
                "被拒的样本不许落库",
                3,
                store.readBindingAuditSamples(BINDING_AUDIT_STATUS_PENDING).size,
            )

            assertTrue("别的科有自己的配额", record(store, physics, "unit-p1"))
            assertTrue("下一周重新计数", record(store, week41, "unit-5"))
            assertEquals(
                "总样本 = 3 + 1 + 1（重放与被拒的都没多写）",
                5,
                store.readBindingAuditSamples().size,
            )
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun reviewSettlesOnceAndPendingRowsCarryNoVerdict() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "binding-audit-review-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val store = StudyDatabaseFactory.open(context, databaseName)
            val quota = BindingAuditSampling.quotaPrefix("MATH", utcMillis("2026-10-04"))
            val command = sampleCommand(quota, "unit-1")
            assertTrue(store.recordBindingAuditSample(command))

            val pending = store.readBindingAuditSamples(BINDING_AUDIT_STATUS_PENDING).single()
            assertEquals(BINDING_AUDIT_STATUS_PENDING, pending.status)
            assertNull("没有判定不许先写 verdict", pending.verdict)
            assertNull("没有判定不许先写 reviewed_at", pending.reviewedAtEpochMillis)
            assertEquals(
                "观察口与读口看到同一行",
                pending.sampleId,
                store.observeBindingAuditSamples(BINDING_AUDIT_STATUS_PENDING).first().single().sampleId,
            )

            val reviewed = store.reviewBindingAuditSample(
                sampleId = command.sampleId,
                verdict = BINDING_AUDIT_VERDICT_WRONG,
                reviewedAtEpochMillis = 2_000,
            )
            assertNotNull(reviewed)
            assertEquals(BINDING_AUDIT_STATUS_REVIEWED, reviewed!!.status)
            assertEquals(BINDING_AUDIT_VERDICT_WRONG, reviewed.verdict)
            assertEquals(2_000L, reviewed.reviewedAtEpochMillis)
            assertEquals(
                "已判定的行不再出现在待复核列表",
                0,
                store.readBindingAuditSamples(BINDING_AUDIT_STATUS_PENDING).size,
            )

            // 判定只落一次：再次落判不覆盖第一条（复核屏重放不会产生互相矛盾的两次判定）。
            val replay = store.reviewBindingAuditSample(
                sampleId = command.sampleId,
                verdict = BINDING_AUDIT_VERDICT_CORRECT,
                reviewedAtEpochMillis = 3_000,
            )
            assertEquals("第一条判定是权威", BINDING_AUDIT_VERDICT_WRONG, replay?.verdict)
            assertEquals(2_000L, replay?.reviewedAtEpochMillis)

            assertNull(
                "不存在的样本落判返回 null（不冒领）",
                store.reviewBindingAuditSample("missing", BINDING_AUDIT_VERDICT_CORRECT, 4_000),
            )
            assertTrue(
                "非法 verdict 必须被拒",
                runCatching {
                    store.reviewBindingAuditSample(command.sampleId, "MAYBE", 4_000)
                }.isFailure,
            )
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * F1 修复轮：并发封顶**实测**（不再只有机制论证）。16 路并行对同一配额 record，
     * 配额 5——`@Transaction` 的计数+插入在写事务里串行化，必须恰好 5 路成功、落库 5 条。
     * 消灭的失败：并发确认下"先读后写"超发，抽样队列被挤爆、每科每周首 N 条形同虚设。
     */
    @Test
    fun concurrentRecordsNeverExceedTheQuota() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "binding-audit-concurrent-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val store = StudyDatabaseFactory.open(context, databaseName)
            val quota = BindingAuditSampling.quotaPrefix("MATH", utcMillis("2026-10-04"))

            val accepted = coroutineScope {
                (1..16).map { index ->
                    async(Dispatchers.IO) {
                        store.recordBindingAuditSample(
                            sampleCommand(quota, "unit-$index"),
                            maxPerSubjectWeek = 5,
                        )
                    }
                }.awaitAll()
            }

            assertEquals("并发下恰好 5 路成功（配额封顶，不超发）", 5, accepted.count { it })
            assertEquals(
                "落库恰好 5 条（被拒的 11 条一条都不许落）",
                5,
                store.readBindingAuditSamples().size,
            )
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun record(store: StudyDatabasePort, quota: String, unit: String): Boolean =
        store.recordBindingAuditSample(sampleCommand(quota, unit), maxPerSubjectWeek = 3)

    private fun sampleCommand(quota: String, unit: String) = RecordBindingAuditSampleCommand(
        sampleId = BindingAuditSampling.sampleId(
            quotaPrefix = quota,
            practiceUnitId = unit,
            problemRevisionId = "revision-$unit",
            bindingIds = listOf("binding-$unit"),
        ),
        quotaPrefix = quota,
        practiceUnitId = unit,
        bindingSnapshotJson = BindingAuditSnapshotCodec.encode(
            BindingAuditSnapshot(
                subject = quota.substringAfter("binding-audit:").substringBefore(":"),
                acceptanceSource = "LOCAL_POLICY_ACCEPTED",
                acceptedAtEpochMillis = 1_000,
                problemId = "problem-$unit",
                problemRevisionId = "revision-$unit",
                practiceUnitId = unit,
                questionMarkdown = "题面",
            ),
        ),
    )

    private fun utcMillis(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
