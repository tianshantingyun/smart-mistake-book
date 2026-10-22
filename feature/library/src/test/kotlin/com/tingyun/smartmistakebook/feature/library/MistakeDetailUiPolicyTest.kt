package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.ui.LocalImageLoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakeDetailUiPolicyTest {
    @Test
    fun sourceToggleHasNoAssetsWhenSourceIsMissing() {
        assertTrue(MistakeSourceSet.Missing.displayAssets().isEmpty())
    }

    @Test
    fun unavailableSourceRemainsVisibleWithTruthfulStatus() {
        val asset = sourceAsset(MistakeSourceLocation.Unavailable)

        assertEquals(
            listOf(asset),
            MistakeSourceSet.Present(listOf(asset)).displayAssets(),
        )
        assertEquals(
            "原图记录存在，但本机文件当前不可用。",
            sourceStatusText(
                hasLocalLocation = false,
                loadState = LocalImageLoadState.UNAVAILABLE,
                expanded = false,
            ),
        )
    }

    @Test
    fun failedLocalDecodeDoesNotClaimThatTheImageWasShown() {
        assertEquals(
            "原图文件暂时打不开。",
            sourceStatusText(
                hasLocalLocation = true,
                loadState = LocalImageLoadState.UNAVAILABLE,
                expanded = false,
            ),
        )
    }

    @Test
    fun cleanRedrawAssetComesFirstWhenPresent() {
        val clean = sourceAsset(
            MistakeSourceLocation.Available("file:///clean.png"),
            role = CLEAN_IMAGE_SOURCE_ROLE,
        )
        val original = sourceAsset(MistakeSourceLocation.Available("file:///original.png"))

        val displayed = MistakeSourceSet.Present(listOf(original, clean)).displayAssets()

        assertEquals(2, displayed.size)
        assertEquals(CLEAN_IMAGE_SOURCE_ROLE, displayed.first().role)
        assertEquals("QUESTION_SOURCE", displayed.last().role)
    }

    @Test
    fun noCleanRedrawKeepsOriginalOrder() {
        val a = sourceAsset(MistakeSourceLocation.Available("file:///a.png"))
        val b = sourceAsset(MistakeSourceLocation.Available("file:///b.png"))

        assertEquals(listOf(a, b), MistakeSourceSet.Present(listOf(a, b)).displayAssets())
    }

    /**
     * 4E WG-06 T2b：讲解图是解析区资产，绝不进题目区（题目区出现讲解图 = 学生把讲解当题面看）。
     */
    @Test
    fun tutorExplanationFiguresAreSplitOutOfTheQuestionAssets() {
        val original = sourceAsset(
            MistakeSourceLocation.Available("file:///original.png"),
            role = "QUESTION_SOURCE",
        )
        val clean = sourceAsset(
            MistakeSourceLocation.Available("file:///clean.png"),
            role = VisualRole.CLEAN_IMAGE.name,
        )
        val diagram = sourceAsset(
            MistakeSourceLocation.Available("file:///diagram.png"),
            role = VisualRole.QUESTION_DIAGRAM.name,
        )
        val explanationA = sourceAsset(
            MistakeSourceLocation.Available("file:///explain-a.png"),
            role = VisualRole.TUTOR_EXPLANATION.name,
            sourceAssetId = "asset-explain-a",
        )
        val explanationB = sourceAsset(
            MistakeSourceLocation.Available("file:///explain-b.png"),
            role = VisualRole.TUTOR_EXPLANATION.name,
            sourceAssetId = "asset-explain-b",
        )

        val sections = MistakeSourceSet
            .Present(listOf(original, explanationA, diagram, clean, explanationB))
            .detailAssets()

        assertEquals(
            listOf(clean.sourceAssetId, original.sourceAssetId, diagram.sourceAssetId),
            sections.question.map(MistakeSourceAsset::sourceAssetId),
        )
        assertEquals(
            listOf(explanationA.sourceAssetId, explanationB.sourceAssetId),
            sections.explanation.map(MistakeSourceAsset::sourceAssetId),
        )
    }

    /** 旧数据（只有 CLEAN_IMAGE/QUESTION_SOURCE）在题目区的顺序必须逐位不变。 */
    @Test
    fun legacyCleanImageQuestionOrderIsUnchangedByTheRoleSplit() {
        val original = sourceAsset(
            MistakeSourceLocation.Available("file:///original.png"),
            role = "QUESTION_SOURCE",
        )
        val clean = sourceAsset(
            MistakeSourceLocation.Available("file:///clean.png"),
            role = VisualRole.CLEAN_IMAGE.name,
        )
        val present = MistakeSourceSet.Present(listOf(original, clean))

        assertEquals(present.displayAssets(), present.detailAssets().question)
        assertTrue(present.detailAssets().explanation.isEmpty())
    }

    @Test
    fun missingSourceHasNoQuestionOrExplanationAssets() {
        assertTrue(MistakeSourceSet.Missing.detailAssets().question.isEmpty())
        assertTrue(MistakeSourceSet.Missing.detailAssets().explanation.isEmpty())
    }

    /**
     * WG-06 读侧闭合：书库卡片用 displayAssets() 全量标"题图"；T2a 起 revision 会持久化
     * TUTOR_EXPLANATION 链接，这里必须把它排除，否则讲解图会以"题图 · n/m"出现。
     */
    @Test
    fun tutorExplanationFiguresNeverAppearInTheLibraryPreview() {
        val original = sourceAsset(MistakeSourceLocation.Available("file:///original.png"))
        val explanation = sourceAsset(
            MistakeSourceLocation.Available("file:///explain.png"),
            role = VisualRole.TUTOR_EXPLANATION.name,
        )

        assertEquals(
            listOf(original),
            MistakeSourceSet.Present(listOf(original, explanation)).displayAssets(),
        )
    }

    /** 旧库同一 revision 多张 CLEAN_IMAGE：当前题面必须是创建时间最新的那张。 */
    @Test
    fun theNewestCleanImageIsTheCurrentSheet() {
        val older = sourceAsset(
            MistakeSourceLocation.Available("file:///clean-old.png"),
            role = VisualRole.CLEAN_IMAGE.name,
            sourceAssetId = "asset-clean-old",
            createdAtEpochMillis = 1_000L,
        )
        val newer = sourceAsset(
            MistakeSourceLocation.Available("file:///clean-new.png"),
            role = VisualRole.CLEAN_IMAGE.name,
            sourceAssetId = "asset-clean-new",
            createdAtEpochMillis = 2_000L,
        )
        val source = MistakeSourceSet.Present(listOf(older, newer))

        assertEquals(
            listOf(newer, older),
            source.displayAssets(),
        )
        assertEquals(
            listOf(newer, older),
            source.detailAssets().question,
        )
    }

    private fun sourceAsset(
        location: MistakeSourceLocation,
        role: String = "QUESTION_SOURCE",
        sourceAssetId: String = "asset-$role",
        createdAtEpochMillis: Long = 1L,
    ) = MistakeSourceAsset(
        role = role,
        sourceAssetId = sourceAssetId,
        contentSha256 = "a".repeat(64),
        mimeType = "image/jpeg",
        byteSize = 1_024,
        width = 800,
        height = 600,
        sourceType = "CAMERA",
        createdAtEpochMillis = createdAtEpochMillis,
        location = location,
    )
}
