package com.tingyun.smartmistakebook.core.ui

import com.tingyun.smartmistakebook.core.model.SubjectKind
import java.util.Locale

fun SubjectKind.studentLabel(): String = when (this) {
    SubjectKind.CHINESE -> "语文"
    SubjectKind.MATH -> "数学"
    SubjectKind.ENGLISH -> "英语"
    SubjectKind.PHYSICS -> "物理"
    SubjectKind.CHEMISTRY -> "化学"
    SubjectKind.BIOLOGY -> "生物"
    SubjectKind.POLITICS -> "思想政治"
    SubjectKind.HISTORY -> "历史"
    SubjectKind.GEOGRAPHY -> "地理"
    SubjectKind.GENERAL -> "综合"
}

fun String.studentSubjectLabel(): String = runCatching {
    SubjectKind.valueOf(trim().uppercase(Locale.ROOT))
}.getOrNull()?.studentLabel() ?: this

/** Subject accents aid recognition while status and primary actions keep their own colors. */
fun SubjectKind.studentAccentColor(): androidx.compose.ui.graphics.Color = when (this) {
    SubjectKind.MATH, SubjectKind.PHYSICS -> SmartColors.LearningAccent
    SubjectKind.CHEMISTRY -> SmartColors.AgentAccent
    SubjectKind.CHINESE, SubjectKind.ENGLISH -> SmartColors.NotesAccent
    SubjectKind.HISTORY, SubjectKind.POLITICS, SubjectKind.GEOGRAPHY -> SmartColors.ReadingAccent
    SubjectKind.BIOLOGY -> SmartColors.JadeDark
    SubjectKind.GENERAL -> SmartColors.InkSecondary
}

fun String.studentSubjectAccentColor(): androidx.compose.ui.graphics.Color = runCatching {
    SubjectKind.valueOf(trim().uppercase(Locale.ROOT))
}.getOrNull()?.studentAccentColor() ?: SmartColors.InkSecondary
