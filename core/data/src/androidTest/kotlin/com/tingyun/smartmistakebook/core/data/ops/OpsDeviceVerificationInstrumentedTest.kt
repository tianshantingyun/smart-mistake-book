package com.tingyun.smartmistakebook.core.data.ops

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.backup.AndroidBackupRepository
import com.tingyun.smartmistakebook.core.data.backup.RestoreIndexRebuildMarker
import com.tingyun.smartmistakebook.core.data.knowledge.BundledKnowledgeBaseInstaller
import com.tingyun.smartmistakebook.core.data.study.seedStudyFacts
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeSearchFeatureExtractor
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.domain.BackupOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

/**
 * Ops device verification (three items): 16-thread concurrent recall p95, backup/restore
 * with explicit -wal/-shm + index-rebuild + rowCounts checks. The cold-start first-query
 * p95 is measured separately by `GoldenRetrievalInstrumentedTest` after a cold boot.
 *
 * Measurement-only: no production code, judge, golden set or mirror is edited.
 */
@RunWith(AndroidJUnit4::class)
class OpsDeviceVerificationInstrumentedTest {

    @Test
    fun sixteenThreadConcurrentRecallRecordsP95() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "ops-concurrent-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        try {
            BundledKnowledgeBaseInstaller.install(store)
            val probes = loadFirstGoldenPerSubject(context)
            assertEquals("probe set must be the 4 first-per-subject golden queries", 4, probes.size)
            probes.forEach { probe ->
                store.readSubjectKnowledgeRecallCandidates(
                    subject = probe.subject,
                    searchFeatures = KnowledgeSearchFeatureExtractor.fromQuestion(probe.query),
                    limit = 5,
                    queryText = probe.query,
                )
            }

            val latencies = Collections.synchronizedList(mutableListOf<Long>())
            val errors = AtomicInteger()
            val pool = Executors.newFixedThreadPool(THREADS)
            val tasks = (0 until THREADS).map { thread ->
                Callable {
                    repeat(ROUNDS) { round ->
                        val probe = probes[(thread + round) % probes.size]
                        val started = SystemClock.elapsedRealtimeNanos()
                        try {
                            runBlocking {
                                store.readSubjectKnowledgeRecallCandidates(
                                    subject = probe.subject,
                                    searchFeatures = KnowledgeSearchFeatureExtractor.fromQuestion(probe.query),
                                    limit = 5,
                                    queryText = probe.query,
                                )
                            }
                        } catch (failure: Throwable) {
                            errors.incrementAndGet()
                        }
                        latencies += (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
                    }
                }
            }
            pool.invokeAll(tasks)
            pool.shutdown()

            val sorted = latencies.sorted()
            val n = sorted.size
            val p50 = sorted[(n * 50 + 99) / 100 - 1]
            val p95 = sorted[(n * 95 + 99) / 100 - 1]
            val max = sorted.last()
            println(
                "ops.concurrent threads=$THREADS samples=$n p50=$p50 p95=$p95 max=$max errors=${errors.get()}",
            )
            assertEquals("all concurrent calls must complete", THREADS * ROUNDS, n)
            assertEquals("no concurrent recall may throw", 0, errors.get())
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun backupRestoreRemovesStaleWalShmAndRebuildsIndex() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME
        context.deleteDatabase(databaseName)
        RestoreIndexRebuildMarker.clear(context)
        val archive = File(context.cacheDir, "ops-restore-${System.nanoTime()}.smbk")
        try {
            val source = StudyDatabaseFactory.open(context, databaseName)
            seedStudyFacts(context, source, databaseName, seedWithKnowledgeNode())
            val databaseFile = context.getDatabasePath(databaseName)
            val wal = File("${databaseFile.absolutePath}-wal")
            val shm = File("${databaseFile.absolutePath}-shm")
            val walBefore = wal.length()

            AndroidBackupRepository(context, source).create(FileOutputStream(archive), BackupOptions())
            source.close()

            val entries = ZipFile(archive).use { zip ->
                zip.entries().toList().map { it.name }
            }
            assertTrue("archive must not embed a -wal file: $entries", entries.none { it.endsWith("-wal") })
            assertTrue("archive must not embed a -shm file: $entries", entries.none { it.endsWith("-shm") })

            val restoreTarget = StudyDatabaseFactory.open(context, databaseName)
            AndroidBackupRepository(context, restoreTarget).restore(archive.inputStream())

            assertEquals("restore must leave no stale -wal frames", 0L, wal.length())
            assertEquals("restore must leave no stale -shm", 0L, shm.length())

            SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val integrity = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else "no-row"
                }
                assertEquals("restored database integrity", "ok", integrity)
                assertEquals("knowledge_node rowCount", 1L, DatabaseUtils.queryNumEntries(db, "knowledge_node"))
                assertEquals(
                    "derived search index must be empty after restore",
                    0L,
                    DatabaseUtils.queryNumEntries(db, "knowledge_search_feature"),
                )
            }
            assertTrue("restore must leave the index-rebuild marker", RestoreIndexRebuildMarker.exists(context))
            println("ops.backup walBefore=$walBefore walAfter=0 shmAfter=0 integrity=ok entries=$entries")

            val rebuilt = StudyDatabaseFactory.open(context, databaseName)
            rebuilt.rebuildKnowledgeSearchIndex("MATH")
            rebuilt.close()
            SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val features = DatabaseUtils.queryNumEntries(db, "knowledge_search_feature")
                assertTrue("index rebuild must recreate feature rows", features > 0)
                println("ops.backup rebuiltFeatureRows=$features")
            }
        } finally {
            RestoreIndexRebuildMarker.clear(context)
            archive.delete()
            context.deleteDatabase(databaseName)
        }
    }

    private fun loadFirstGoldenPerSubject(context: Context): List<Probe> {
        val json = context.assets.open(ASSET_JSON).use { it.bufferedReader().readText() }
        val cases = Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }
        val first = linkedMapOf<String, Probe>()
        cases.forEach { value ->
            val subject = value["subject"]!!.jsonPrimitive.content
            first.putIfAbsent(subject, Probe(subject, value["query"]!!.jsonPrimitive.content))
        }
        return listOf("MATH", "PHYSICS", "CHEMISTRY", "BIOLOGY").mapNotNull { first[it] }
    }

    private fun seedWithKnowledgeNode() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord(
                problemId = "problem-ops",
                canonicalFingerprint = "a".repeat(64),
                subject = "MATH",
                createdAtEpochMillis = 1_000,
            ),
        ),
        revisions = listOf(
            ProblemRevisionSeedRecord(
                revisionId = "revision-ops",
                problemId = "problem-ops",
                revisionNumber = 1,
                title = "运维演练题",
                problemMarkdown = "求函数最值。",
                questionDocumentSnapshot = null,
                answerSpecId = null,
                answerSpecSnapshot = null,
                answerVerificationStatus = "UNKNOWN",
                sourceType = "TEST",
                sourceReference = null,
                contentFingerprint = "b".repeat(64),
                createdAtEpochMillis = 1_000,
            ),
        ),
        practiceUnits = listOf(
            PracticeUnitSeedRecord(
                practiceUnitId = "practice-ops",
                problemId = "problem-ops",
                problemRevisionId = "revision-ops",
                unitKey = "unit:ops",
                unitKind = "PROBLEM",
                title = "运维演练题",
                promptMarkdown = "求函数最值。",
                estimatedSeconds = 180,
                createdAtEpochMillis = 1_000,
            ),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-ops",
                practiceUnitId = "practice-ops",
                problemId = "problem-ops",
                currentRevisionId = "revision-ops",
                sourceKey = null,
                acceptedAtEpochMillis = 1_000,
                updatedAtEpochMillis = 1_000,
            ),
        ),
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = "ops-knowledge-node",
                stableCode = "ops:MATH:ops-knowledge-node",
                subject = "MATH",
                displayName = "函数单调性",
                parentKnowledgeNodeId = null,
                taxonomyVersion = "moe-2025-four-subjects-v1",
                createdAtEpochMillis = 1_000,
                nodeKind = KnowledgeNodeKind.CONCEPT.name,
                granularity = KnowledgeNodeGranularity.ATOMIC.name,
                boundaryMarkdown = "只覆盖单调性判断，不涉及其他知识点。",
                verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name,
            ),
        ),
    )

    private data class Probe(val subject: String, val query: String)

    private companion object {
        const val ASSET_JSON = "golden/golden_queries_v2.json"
        const val THREADS = 16
        const val ROUNDS = 10
    }
}
