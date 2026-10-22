package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-08 T1b：视觉角色是**一份**常量，不是五处各写一遍的字符串。
 *
 * 这里钉住线上值（数据库 `role` 列仍存 TEXT）：改枚举名等于改持久值，必须与迁移/读侧
 * 一起改；只改一处会在本用例立即报红，而不是让旧数据静默失配。
 */
class VisualRoleTest {

    @Test
    fun exposesExactlyTheFourPersistedRoleNames() {
        assertEquals(
            listOf("CLEAN_IMAGE", "TUTOR_EXPLANATION", "QUESTION_DIAGRAM", "SOURCE_EVIDENCE"),
            VisualRole.entries.map { it.name },
        )
    }

    /**
     * T2a：讲题工具 trace 里的 kind 只有两种，各映射到唯一持久角色。这条映射是保存链与
     * 消息链的单源——REDRAW 生成的是干净题面，GENERATE_PROCESS 生成的是讲解过程图；
     * 两者互换的后果是导出把讲解图放进题目区。
     */
    @Test
    fun mapsTheTwoTutorFigureKindsToTheirPersistedRoles() {
        assertEquals(VisualRole.CLEAN_IMAGE, TutorFigureKind.REDRAW_PROBLEM.visualRole())
        assertEquals(VisualRole.TUTOR_EXPLANATION, TutorFigureKind.GENERATE_PROCESS.visualRole())
    }

    /**
     * T2a：文本修订（用户改题面）只允许把源照片与源证据带到新 revision。干净题面、讲解图、
     * 题图都基于**旧题面**，带过去就是让新题面显示旧内容。
     */
    @Test
    fun onlySourceEvidenceRolesCrossIntoATextRevision() {
        assertTrue(carriesAcrossTextRevision(QUESTION_SOURCE_ROLE))
        assertTrue(carriesAcrossTextRevision(VisualRole.SOURCE_EVIDENCE.name))
        assertFalse(carriesAcrossTextRevision(VisualRole.CLEAN_IMAGE.name))
        assertFalse(carriesAcrossTextRevision(VisualRole.TUTOR_EXPLANATION.name))
        assertFalse(carriesAcrossTextRevision(VisualRole.QUESTION_DIAGRAM.name))
        assertFalse("旧 NULL 角色不得被猜成可携带", carriesAcrossTextRevision(null))
    }
}
