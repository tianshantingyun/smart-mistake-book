package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.domain.OrganizationOption
import com.tingyun.smartmistakebook.core.domain.ProblemOrganizationSelection
import com.tingyun.smartmistakebook.core.domain.UserProblemClassification
import com.tingyun.smartmistakebook.core.model.ClassificationDimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakeOrganizationTreePickerTest {
    @Test
    fun pickedKnowledgeOptionJoinsTheSelectionAsAUserClassification() {
        val pick = pickOrganizationTreeOption(
            existing = listOf(
                UserProblemClassification(ClassificationDimension.CHAPTER, "函数"),
            ),
            dimension = ClassificationDimension.KNOWLEDGE,
            displayName = " 三角函数的图象与变换 ",
        )

        val added = (pick as OrganizationTreePick.Added).classifications
        assertEquals(
            listOf(
                ClassificationDimension.CHAPTER to "函数",
                ClassificationDimension.KNOWLEDGE to "三角函数的图象与变换",
            ),
            added.map { it.dimension to it.displayName },
        )
    }

    @Test
    fun alreadySelectedOptionIsNotAddedTwiceRegardlessOfCase() {
        val existing = listOf(
            UserProblemClassification(ClassificationDimension.KNOWLEDGE, "二次函数最值"),
        )

        assertEquals(
            OrganizationTreePick.Duplicate,
            pickOrganizationTreeOption(
                existing = existing,
                dimension = ClassificationDimension.KNOWLEDGE,
                displayName = "二次函数最值",
            ),
        )
        assertEquals(
            OrganizationTreePick.Duplicate,
            pickOrganizationTreeOption(
                existing = existing,
                dimension = ClassificationDimension.KNOWLEDGE,
                displayName = "  二次函数最值  ",
            ),
        )
    }

    @Test
    fun blankOptionIsRejectedAndFullQuotaIsReported() {
        assertEquals(
            OrganizationTreePick.Invalid,
            pickOrganizationTreeOption(emptyList(), ClassificationDimension.KNOWLEDGE, "   "),
        )

        val full = (1..ProblemOrganizationSelection.MAX_USER_CLASSIFICATIONS).map { index ->
            UserProblemClassification(ClassificationDimension.KNOWLEDGE, "知识点 $index")
        }
        assertEquals(
            OrganizationTreePick.LimitReached,
            pickOrganizationTreeOption(full, ClassificationDimension.KNOWLEDGE, "再来一个"),
        )
    }

    @Test
    fun filterMatchesOptionNamesCaseInsensitively() {
        val options = listOf(
            OrganizationOption(labelId = "topic-functions", displayName = "函数的概念与性质"),
            OrganizationOption(labelId = "atomic-triangle", displayName = "三角函数的图象与变换"),
            OrganizationOption(labelId = "atomic-vector", displayName = "Vector Basics"),
        )

        assertEquals(options, filterOrganizationOptions(options, "  "))
        assertEquals(
            listOf("atomic-triangle"),
            filterOrganizationOptions(options, "三角").map(OrganizationOption::labelId),
        )
        assertEquals(
            listOf("atomic-vector"),
            filterOrganizationOptions(options, "vector basics")
                .map(OrganizationOption::labelId),
        )
        assertTrue(filterOrganizationOptions(options, "不存在").isEmpty())
    }

    @Test
    fun selectionKeyIsDimensionScopedAndCaseInsensitive() {
        assertTrue(
            organizationOptionSelectionKey(ClassificationDimension.KNOWLEDGE, "二次函数最值") ==
                organizationOptionSelectionKey(ClassificationDimension.KNOWLEDGE, " 二次函数最值 "),
        )
        assertTrue(
            organizationOptionSelectionKey(ClassificationDimension.CHAPTER, "函数") !=
                organizationOptionSelectionKey(ClassificationDimension.KNOWLEDGE, "函数"),
        )
    }
}
