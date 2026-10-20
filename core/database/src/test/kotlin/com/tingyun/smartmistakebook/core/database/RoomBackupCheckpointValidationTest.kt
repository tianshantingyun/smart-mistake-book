package com.tingyun.smartmistakebook.core.database

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * KD-62①：备份前 `wal_checkpoint(TRUNCATE)` 的结果行判据。
 *
 * 消灭的失败：旧实现消费结果行后直接忽略——长写者/其他连接让 TRUNCATE 返回 busy=1 时，
 * checkpoint 并没有完成，但回退路径照样复制主库，产出一份可能缺 WAL 已提交页的备份。
 */
class RoomBackupCheckpointValidationTest {

    @Test
    fun aBlockedCheckpointIsAProblem() {
        assertNotNull(
            "busy=1 = TRUNCATE 被挡下，checkpoint 未完成",
            checkpointCompletionProblem(busy = 1, log = 10, checkpointed = 0),
        )
    }

    @Test
    fun aCompletedCheckpointIsAccepted() {
        assertNull(checkpointCompletionProblem(busy = 0, log = 0, checkpointed = 10))
    }

    @Test
    fun aNonWalDatabaseIsALegalNoOp() {
        assertNull(
            "两列都是 -1 = 连接不在 WAL 模式，没有 WAL 可 checkpoint",
            checkpointCompletionProblem(busy = 0, log = -1, checkpointed = -1),
        )
    }

    @Test
    fun aPartiallyCheckpointedWalIsAProblem() {
        assertNotNull(
            "已回写页数少于 WAL 页数 = 未完成",
            checkpointCompletionProblem(busy = 0, log = 10, checkpointed = 9),
        )
    }

    @Test
    fun anIncompleteResultRowIsAProblem() {
        assertNotNull(
            "只报了一个 -1 的结果行不可信，按未完成处理",
            checkpointCompletionProblem(busy = 0, log = 3, checkpointed = -1),
        )
    }
}
