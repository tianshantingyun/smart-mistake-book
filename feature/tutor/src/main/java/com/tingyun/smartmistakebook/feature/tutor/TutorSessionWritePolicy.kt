package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.TutorMemoryPreference
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput

/**
 * 这次会话里学生说过"这次别记"吗（`memoryPreference = BLOCK_LONG_TERM_WRITES_FOR_SESSION`）。
 *
 * 判据两条，都在模型的**语义输出**上：学生这次明确提了这一要求（`explicitActionRequest`），
 * 且模型对这条判断的置信度不低于 [MIN_RESTRICT_WRITES_CONFIDENCE]。两条与
 * `TutorIntentAuthorityPolicy` 当年给的准入**同量级、同置信门**；当年还有一半是"用学生原话的
 * 逐字标记"核对这一次声明（`actionIsBoundTo`），那一半随该策略一起删掉了——标记法本地并不
 * 真懂语义，而这条开关的失败方向是**安全的一侧**（多拦一次写入，不是多写一条记录）。
 */
internal fun List<ModelTaskSnapshot>.blocksTutorLongTermWrites(): Boolean = any { task ->
    val input = task.request.input as? TutorRespondInput
    val output = task.output as? TutorRespondOutput
    task.status == ModelTaskStatus.SUCCEEDED &&
        input != null &&
        output != null &&
        output.intentDecision.memoryPreference ==
        TutorMemoryPreference.BLOCK_LONG_TERM_WRITES_FOR_SESSION &&
        output.intentDecision.explicitActionRequest &&
        output.intentDecision.confidence >= MIN_RESTRICT_WRITES_CONFIDENCE
}

/** 与当年那道授权策略同一道门（它记录的 0.70）。 */
private const val MIN_RESTRICT_WRITES_CONFIDENCE = 0.70
