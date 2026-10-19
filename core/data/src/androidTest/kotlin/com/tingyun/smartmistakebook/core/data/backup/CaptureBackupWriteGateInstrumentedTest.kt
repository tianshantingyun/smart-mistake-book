package com.tingyun.smartmistakebook.core.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.data.capture.CaptureWorkflowTestBase
import com.tingyun.smartmistakebook.core.data.capture.RoomCaptureWorkflowRepository
import com.tingyun.smartmistakebook.core.data.splitimport.RoomSplitImportRepository
import com.tingyun.smartmistakebook.core.domain.BackupActivity
import com.tingyun.smartmistakebook.core.domain.BackupWriteGate
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureInputSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-54 的接线面（仪器化）：采集写入口与备份仓库看到**同一道** [BackupWriteGate] 时，
 * 备份快照期间的采集写会显式排队，备份结束后继续——而不是像设备实测那样在 room3
 * 连接池里等到 `unable to grant a connection`。
 *
 * 测试用真 Room 库 + 真采集仓库；备份侧用手工持有的 `runBackup` 段模拟快照阶段
 * （真实 `VACUUM INTO` 的时长取决于库体积，不能用来做确定性用例；设备对照留给设备轨）。
 */
@RunWith(AndroidJUnit4::class)
class CaptureBackupWriteGateInstrumentedTest : CaptureWorkflowTestBase() {
    @Test
    fun captureWriteQueuesWhileBackupSnapshotRunsAndSucceedsAfterwards() = runBlocking {
        // 超时防止断言失败后未被放行的备份协程把 runBlocking 挂住。
        withTimeout(20_000L) {
            val imported = repository.importDraft(
                CaptureDraftImportRequest(
                    requestId = "backup-gate-import",
                    localUri = privateUri(createPng(48, 48)).toString(),
                    source = CaptureInputSource.CAMERA,
                    origin = CaptureEntryOrigin.LIBRARY,
                    occurredAtEpochMillis = 1_000L,
                ),
            )
            val gate = BackupWriteGate(
                maxWriteQueueWaitMillis = 10_000L,
                drainTimeoutMillis = 10_000L,
            )
            val gatedRepository = RoomCaptureWorkflowRepository(
                database = database,
                assetVault = AndroidCanonicalAssetVault(context),
                localTextRecognizer = noTextRecognizer(),
                splitImports = RoomSplitImportRepository(database),
                writeGate = gate,
            )

            val snapshotStarted = CompletableDeferred<Unit>()
            val releaseSnapshot = CompletableDeferred<Unit>()
            val backup = launch {
                gate.runBackup {
                    snapshotStarted.complete(Unit)
                    releaseSnapshot.await()
                }
            }
            snapshotStarted.await()
            assertTrue(
                "快照阶段必须显式可见（写入口据此提示/排队）",
                gate.activity.value is BackupActivity.Snapshotting,
            )

            val writeStarted = CompletableDeferred<Unit>()
            val write = async {
                writeStarted.complete(Unit)
                gatedRepository.abandonPendingCapture(
                    draftId = imported.draftId,
                    expectedRevisionNumber = imported.revisionNumber,
                    abandonedAtEpochMillis = 4_000L,
                )
            }
            writeStarted.await()
            delay(250L)
            assertFalse("备份快照期间采集写必须排队，不能先执行", write.isCompleted)
            assertTrue(
                "备份期间写没有落库：草稿仍应在待处理列表里",
                gatedRepository.observePendingCaptures().first()
                    .any { item -> item.draftId == imported.draftId },
            )

            releaseSnapshot.complete(Unit)
            backup.join()
            assertTrue("备份结束后排队的写必须真正执行", write.await())
            assertTrue(
                "备份结束后草稿必须已被移出待处理列表",
                gatedRepository.observePendingCaptures().first()
                    .none { item -> item.draftId == imported.draftId },
            )
        }
    }
}
