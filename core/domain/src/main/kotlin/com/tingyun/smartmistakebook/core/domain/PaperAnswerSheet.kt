package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.StudyDayMath
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 一张纸质答卷的**回收身份**（T5/5B-G8，AUD-01/02 身份同型）。
 *
 * 身份分量与它们各自消灭的失败：
 * - [paperFingerprint]（= 导出记录 `MistakeExportRecord.inputSha256`，内容单源）：定"哪张卷"。
 *   同内容重导出（不同 [exportId]、同指纹）不开第二条计分道——同一张物理答卷同日只算一次。
 * - [exportId]：溯源绑定（这张答卷来自哪次导出记录），**不参与键派生**；键链核对时用它
 *   做第一道匹配（同指纹不同导出也可回收，见 [PaperAnswerKeyChain] 判定）。
 * - 学习日（[recycleStudyDay]，由 [recycledAtEpochMillis]+[utcOffsetMinutes] 经
 *   [StudyDayMath.localEpochDayOf] 单源派生，04:00 日界）："不同日不撞键"的日分量。
 *
 * 消灭的失败（spec 2026-10-11-new-stages §5B）：重传重复计分；不同卷/不同日撞键。
 * 等价语义先例：AUD-01 修复（`09f0c85c`）——"同次重试 1 笔；隔日同选项 2 笔"。
 */
data class PaperAnswerSheetIdentity(
    val exportId: String,
    val paperFingerprint: String,
    val recycledAtEpochMillis: Long,
    val utcOffsetMinutes: Int,
) {
    init {
        require(exportId.isNotBlank()) { "A paper answer sheet needs its export id" }
        require(paperFingerprint.isNotBlank()) { "A paper answer sheet needs its paper fingerprint" }
        require(recycledAtEpochMillis >= 0) { "A recycle time must not be negative" }
    }

    /** 回收发生的学习日（单源派生，调用方不得自算再传入）。 */
    val recycleStudyDay: Long = StudyDayMath.localEpochDayOf(recycledAtEpochMillis, utcOffsetMinutes)

    /** 整张答卷的幂等键：同 (指纹, 学习日) 的重传命中同一条记录。 */
    val sheetKey: String = deriveKey(
        namespace = "paper-answer-sheet",
        fingerprint = paperFingerprint,
        paperQuestionId = null,
        studyDay = recycleStudyDay,
    )

    /**
     * 卷面题作答尝试键：同卷（指纹）同题同学习日 → 同键（重传幂等、不重复计分）；
     * 不同卷（指纹）或不同学习日 → 不同键（不撞键）。
     */
    fun attemptKeyFor(paperQuestionId: String): String {
        require(paperQuestionId.isNotBlank()) { "A paper question id must not be blank" }
        return deriveKey(
            namespace = "paper-answer-attempt",
            fingerprint = paperFingerprint,
            paperQuestionId = paperQuestionId,
            studyDay = recycleStudyDay,
        )
    }
}

/** 一份回收答卷：学生作答证据（拍照/导入 → 4C 切题后的结构化形态）。 */
data class PaperAnswerSheet(
    val identity: PaperAnswerSheetIdentity,
    val recycledAtEpochMillis: Long,
    val answers: List<PaperQuestionAnswer>,
) {
    init {
        require(recycledAtEpochMillis >= 0) { "A recycle time must not be negative" }
        require(answers.isNotEmpty()) { "A recycled answer sheet needs at least one answer" }
        require(
            answers.map(PaperQuestionAnswer::paperQuestionId).distinct().size == answers.size,
        ) { "One answer sheet carries at most one answer per paper question" }
    }
}

/**
 * 卷面上一题的学生作答。
 *
 * [selectedChoiceId] 必须是**卷面该题选项集里的选项 ID**（与键链同一卷面版本同源）；
 * 开放题/未识别出选项时为 null（走判词通道形态，见 [PaperOpenAnswerJudgingChannel]）。
 * 生成图/清图永远不得顶替这里的学生作答（spec 5B/WG-09 红线）。
 */
data class PaperQuestionAnswer(
    val paperQuestionId: String,
    val selectedChoiceId: String?,
) {
    init {
        require(paperQuestionId.isNotBlank()) { "A paper question id must not be blank" }
        require(selectedChoiceId == null || selectedChoiceId.isNotBlank()) {
            "A selected choice id must not be blank when provided"
        }
    }
}

/** 答卷记录回执：[created]=false 表示同 [PaperAnswerSheetIdentity.sheetKey] 的重传。 */
data class PaperAnswerSheetRecordReceipt(
    val sheetKey: String,
    val created: Boolean,
)

/**
 * 回收答卷的持久化读写口（T5 零 schema 先行；真实持久化随导出侧接线落地，见实现侧 KDoc）。
 */
interface PaperAnswerSheetStore {
    /**
     * 幂等记录一张答卷：同 [PaperAnswerSheetIdentity.sheetKey] 重传返回 created=false，
     * 首次记录是权威（重传不覆盖）。
     */
    suspend fun recordAnswerSheet(sheet: PaperAnswerSheet): PaperAnswerSheetRecordReceipt

    /** 读回一张答卷；null = 该键没有记录。 */
    suspend fun readAnswerSheet(sheetKey: String): PaperAnswerSheet?
}

/**
 * 派生键：`<namespace>-<sha256(域前缀 + 指纹 + [题] + 学习日)>`。
 *
 * 形态对齐 core:data `StudyWriteContext.stableId`（域前缀 + SHA-256 hex），但分量是 G8 自己的
 * （指纹/题/学习日），不掺 learnerId——答卷身份先于投影归属，计分幂等由证据端口的 attemptKey
 * 独立保证。
 */
private fun deriveKey(
    namespace: String,
    fingerprint: String,
    paperQuestionId: String?,
    studyDay: Long,
): String {
    val payload = buildString {
        append("smb-paper-recycle-v1")
        append('\n')
        append(fingerprint)
        append('\n')
        append(paperQuestionId.orEmpty())
        append('\n')
        append(studyDay)
    }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(payload.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
    return "$namespace-$digest"
}
