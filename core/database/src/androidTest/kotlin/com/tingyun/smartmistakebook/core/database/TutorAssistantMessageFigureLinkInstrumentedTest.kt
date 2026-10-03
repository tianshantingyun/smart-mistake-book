package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A2（4B 批 2）：生成图**持久引用**的 DB 半边。
 *
 * 消灭的失败：`persistCleanImageBytes` 只写文件不写 DB 时，canonical 行不存在 → 孤儿回收
 * 看不见（永不回收），消息也没有引用 → 界面只能靠内存 URI。这里钉住三件事：
 * 1. 助手消息与配图引用**同一次写入**（消息行在、引用就在）；
 * 2. 引用建立后该资产**不再出现在未引用集合**（GC 按引用判定保留）；
 * 3. 同一消息重放（同一 id）不会重复建引用（幂等）。
 */
@RunWith(AndroidJUnit4::class)
class TutorAssistantMessageFigureLinkInstrumentedTest {

    @Test
    fun assistantMessageAndFigureReferenceAreWrittenTogether() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "assistant-figure-link-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database = StudyDatabaseFactory.open(context, databaseName)
        try {
            val conversation = database.createTutorConversation(
                CreateTutorConversationDatabaseCommand(
                    conversationId = "conversation-figures",
                    anchorKind = "TEXT_ONLY",
                    anchorId = null,
                    anchorRevisionId = null,
                    title = null,
                    createdAtEpochMillis = 1_000,
                ),
            )
            database.registerCanonicalSourceAsset(
                CanonicalSourceAssetRecord(
                    sourceAssetId = "figure-asset-1",
                    contentSha256 = "b".repeat(64),
                    relativePath = "source-assets/figure-asset-1.png",
                    mimeType = "image/png",
                    byteSize = 2_048,
                    width = 1_024,
                    height = 1_024,
                    sourceType = StudyDbValue.SourceAssetType.GENERATED_FIGURE,
                    createdAtEpochMillis = 1_000,
                ),
            )
            // 引用建立之前：它是"未引用"的（孤儿回收按引用判定，宽限期保护在途）。
            assertTrue(
                database.readUnreferencedCanonicalAssets()
                    .any { it.sourceAssetId == "figure-asset-1" },
            )

            val command = AppendTutorAssistantMessageDatabaseCommand(
                conversationId = conversation.conversationId,
                messageId = "assistant-with-figure",
                ordinal = null,
                replyToMessageId = null,
                bodyMarkdown = "我把图放在下面了。",
                toolTraceJson = null,
                sourceImageAssetIds = listOf("figure-asset-1"),
                logicalOperationId = "operation-figures",
                status = "SUCCEEDED",
                createdAtEpochMillis = 2_000,
                completedAtEpochMillis = 3_000,
                errorCode = null,
            )
            database.appendTutorAssistantMessage(command)

            val links = database.readTutorMessageSourceAssets(listOf("assistant-with-figure"))
            assertEquals(1, links.size)
            assertEquals("figure-asset-1", links.single().sourceAssetId)
            assertEquals(0, links.single().ordinal)
            assertFalse(
                "被引用的生成图不得再出现在未引用集合（否则宽限期后会被回收）",
                database.readUnreferencedCanonicalAssets()
                    .any { it.sourceAssetId == "figure-asset-1" },
            )

            // 同一逻辑操作重放：消息行与引用都幂等（引用不重复、不消失）。
            database.appendTutorAssistantMessage(command)
            assertEquals(
                1,
                database.readTutorMessageSourceAssets(listOf("assistant-with-figure")).size,
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    /**
     * 门失败 G2 的回归：`ordinal = null` 的消息重放必须幂等——重推的号（计数器 +1）与存行
     * 当时分配到的号不同，不能据此判冲突。学生消息与助手消息同一条判据（对称修改）。
     */
    @Test
    fun studentMessageReplayWithAnAllocatedOrdinalIsIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "student-message-replay-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database = StudyDatabaseFactory.open(context, databaseName)
        try {
            val conversation = database.createTutorConversation(
                CreateTutorConversationDatabaseCommand(
                    conversationId = "conversation-replay",
                    anchorKind = "TEXT_ONLY",
                    anchorId = null,
                    anchorRevisionId = null,
                    title = null,
                    createdAtEpochMillis = 1_000,
                ),
            )
            val command = AppendTutorStudentMessageDatabaseCommand(
                conversationId = conversation.conversationId,
                messageId = "student-replay",
                ordinal = null,
                bodyMarkdown = "这道题怎么做？",
                logicalOperationId = "operation-replay",
                createdAtEpochMillis = 2_000,
            )
            val first = database.appendTutorStudentMessage(command)
            // 重放：计数器已被第一次写入推进（lastTurnOrdinal=1 → 重推值=2），存行号仍是 1。
            val second = database.appendTutorStudentMessage(command)

            assertEquals(first.messageId, second.messageId)
            assertEquals(first.ordinal, second.ordinal)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
