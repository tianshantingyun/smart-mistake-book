package com.tingyun.smartmistakebook.core.database.dao

import com.tingyun.smartmistakebook.core.database.entity.AssessmentEvidenceAttributionEntity
import com.tingyun.smartmistakebook.core.database.entity.AssessmentEvidenceSnapshotEntity

/**
 * KD-37：**时间戳不是快照身份的一部分**。
 *
 * 快照身份 = `snapshotId` 加上除采集时刻外的**全部列**，外加固属行（`attributions`）。
 * `capturedAtEpochMillis` 单列被排除，因为它记录的是"这一次把快照算出来是什么时候"，而不是
 * "这是哪一张快照"：`TutorJudgedReviewSettler` 每次结算都拿本次发生的时刻重建同一个
 * `snapshotId` 的快照（snapshotId 由稳定的会话身份派生），于是一次干净的重试必然带着一个
 * **新的时间戳**。
 *
 * 消灭的失败（KD-37「重新判定」死路）：整实体比较（含时间戳）会把这种重试判成"同一
 * snapshotId 被改写成另一份载荷"，在 `saveEvidenceSnapshot` 抛 `ImmutablePayloadConflictException`。
 * 而快照写入发生在 `recordAttempt` 的幂等回放**之前**，于是**第一次因 advance 失败而回滚的行**，
 * 之后每次重试（含学生点「重新判定」）都死在这一步，永远走不到回放、也永远写不进去。
 *
 * 守卫没有被放松：真正不同的内容（身份、题面修订、归属、校准、验证状态…）**仍然不等**，
 * 照旧抛冲突；分摊到不同 `snapshotId` 的快照是另一行，本来就不走这条比较。
 */
internal fun sameEvidenceSnapshotIdentity(
    existing: AssessmentEvidenceSnapshotEntity,
    existingAttributions: List<AssessmentEvidenceAttributionEntity>,
    candidate: AssessmentEvidenceSnapshotEntity,
    candidateAttributions: List<AssessmentEvidenceAttributionEntity>,
): Boolean =
    existing.copy(capturedAtEpochMillis = 0) == candidate.copy(capturedAtEpochMillis = 0) &&
        existingAttributions == candidateAttributions
