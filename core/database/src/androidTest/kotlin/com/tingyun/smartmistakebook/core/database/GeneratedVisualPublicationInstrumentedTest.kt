package com.tingyun.smartmistakebook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeneratedVisualPublicationInstrumentedTest {
    @Test
    fun publicationIsLiveWithoutCreatingAMessage() = runBlocking {
        withDatabase { store, command, name ->
            val published = store.commitGeneratedVisualArtifact(CommitGeneratedVisualArtifactCommand(command))
            assertEquals(TutorVisualStatus.LIVE, published.status)
            assertNull(published.ownerRequestId)
            assertNotNull(published.promotedAtEpochMillis)
            assertEquals(published, store.readTutorVisualSnapshot(published.snapshotId))
            assertEquals(0L, countRows(name, "tutor_conversation"))
            assertEquals(0L, countRows(name, "tutor_message"))
            assertEquals(0L, countRows(name, "tutor_message_visual"))
            assertTrue(store.readUnreferencedCanonicalAssets().none {
                it.sourceAssetId == command.snapshot.previewAssetId
            })
        }
    }

    @Test
    fun replayReusesTheLiveSnapshotEvenWhenTheBudgetIsLowered() = runBlocking {
        withDatabase { store, command, name ->
            val first = store.commitGeneratedVisualArtifact(CommitGeneratedVisualArtifactCommand(command))
            val replay = store.commitGeneratedVisualArtifact(CommitGeneratedVisualArtifactCommand(
                command.copy(ownerRequestId = "retry", perProgramBudgetBytes = 1, globalBudgetBytes = 1),
            ))
            assertEquals(first, replay)
            assertEquals(1L, countRows(name, "tutor_visual_program"))
            assertEquals(1L, countRows(name, "tutor_visual_snapshot"))
        }
    }

    @Test
    fun aBudgetFailureRollsBackTheProgramSnapshotAndPromotion() = runBlocking {
        withDatabase { store, command, name ->
            val failure = runCatching {
                store.commitGeneratedVisualArtifact(CommitGeneratedVisualArtifactCommand(
                    command.copy(perProgramBudgetBytes = 1),
                ))
            }.exceptionOrNull()
            assertTrue(failure.toString(), failure is TutorVisualCheckpointBudgetExceededException)
            assertEquals(0L, countRows(name, "tutor_visual_program"))
            assertEquals(0L, countRows(name, "tutor_visual_snapshot"))
            assertNotNull(store.readCanonicalSourceAsset(requireNotNull(command.snapshot.previewAssetId)))
        }
    }

    @Test
    fun invalidSnapshotIdentityIsRejectedWithoutLeavingAProgram() = runBlocking {
        withDatabase { store, command, name ->
            val failure = runCatching {
                store.commitGeneratedVisualArtifact(CommitGeneratedVisualArtifactCommand(
                    command.copy(snapshot = command.snapshot.copy(snapshotId = "forged")),
                ))
            }.exceptionOrNull()
            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertEquals(0L, countRows(name, "tutor_visual_program"))
            assertEquals(0L, countRows(name, "tutor_visual_snapshot"))
        }
    }

    private suspend fun withDatabase(
        block: suspend (RoomStudyDatabase, StageTutorVisualSnapshotCommand, String) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "generated-publication-${System.nanoTime()}.db"
        val store = StudyDatabaseFactory.open(context, name) as RoomStudyDatabase
        try {
            store.registerCanonicalSourceAsset(CanonicalSourceAssetRecord(
                sourceAssetId = "preview", contentSha256 = "a".repeat(64),
                relativePath = "source-assets/preview.png", mimeType = "image/png",
                byteSize = 128, width = 1, height = 1,
                sourceType = StudyDbValue.SourceAssetType.GENERATED_FIGURE,
                createdAtEpochMillis = 1_000,
            ))
            block(store, stagingCommand(), name)
        } finally {
            store.close()
            context.deleteDatabase(name)
        }
    }

    private fun countRows(name: String, table: String): Long {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            }
        }
    }

    private fun stagingCommand(): StageTutorVisualSnapshotCommand {
        val source = "draw();"
        val state = "{}"
        val scene = "{\"scene\":true}"
        val draw = "{\"ops\":[]}"
        val programId = TutorVisualIdentity.deriveProgramId(source, state, "basis", 1, "sdk", "TUTOR_EXPLANATION")
        val stateDigest = TutorVisualIdentity.sha256Hex(state)
        val drawDigest = TutorVisualIdentity.sha256Hex(draw)
        val program = TutorVisualProgramWrite(
            programId, "learner:local", "TUTOR_EXPLANATION", TutorVisualIdentity.sha256Hex(source),
            source, state, stateDigest, 1, "sdk", null, "basis", null, 1_000,
        )
        val snapshot = TutorVisualSnapshotWrite(
            snapshotId = TutorVisualIdentity.deriveSnapshotId(programId, stateDigest, drawDigest, "renderer", "fonts"),
            programId = programId, stateJson = state, stateSha256 = stateDigest,
            sceneSchemaVersion = 1, sceneJson = scene, sceneSha256 = TutorVisualIdentity.sha256Hex(scene),
            renderSchemaVersion = 1, drawCommandsJson = draw, drawCommandsSha256 = drawDigest,
            contentIdsJson = "[]", viewportWidthDu = 360, viewportHeightDu = 640,
            fontScaleMilli = 1_000, theme = "LIGHT", timeMs = 0, seed = 0,
            runtimeVersion = "test", compilerVersion = "compiler", rendererVersion = "renderer",
            fontManifestVersion = "fonts", questionVerificationStatus = "NOT_CHECKED",
            previewAssetId = "preview", checkpointKind = TutorVisualCheckpointKind.INITIAL,
            createdAtEpochMillis = 1_000,
        )
        return StageTutorVisualSnapshotCommand(program, snapshot, "operation")
    }
}
