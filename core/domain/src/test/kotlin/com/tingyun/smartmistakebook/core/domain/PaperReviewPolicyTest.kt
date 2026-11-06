package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.*
import org.junit.Test

class PaperReviewPolicyTest {
    @Test fun agentOnlyReceivesReadablePhotosAssociatedWithTheCurrentQuestion() {
        val photos = listOf(photo("whole", null), photo("this", 1), photo("other", 2), photo("missing", 1).copy(localUri = ""))
        assertEquals(listOf("whole", "this"), paperReviewPhotosForQuestion(photos, 1).map { it.photoId })
        assertEquals(listOf("this"), paperReviewAgentPhotos(photos, 1, setOf("this")).map { it.photoId })
        assertThrows(IllegalArgumentException::class.java) { paperReviewAgentPhotos(photos, 1, setOf("other")) }
    }

    @Test fun agentPhotoLimitRequiresAnExplicitPageSelectionRatherThanSilentTruncation() {
        val photos = (0..9).map { photo("page-$it", null) }
        assertEquals(10, paperReviewPhotosForQuestion(photos, 1).size)
        assertThrows(IllegalArgumentException::class.java) { paperReviewAgentPhotos(photos, 1, photos.map { it.photoId }.toSet()) }
        assertThrows(IllegalArgumentException::class.java) { paperReviewAgentPhotos(photos, 1, emptySet()) }
        assertEquals(listOf("page-8", "page-9"), paperReviewAgentPhotos(photos, 1, setOf("page-9", "page-8")).map { it.photoId })
    }

    @Test fun correctedWorkDoesNotImplyIndependentRecall() {
        assertNull(paperReviewEvidenceKey(true, ReviewSelfReportKey.GOOD, false))
        assertEquals(ReviewSelfReportKey.AGAIN, paperReviewEvidenceKey(true, ReviewSelfReportKey.AGAIN, false))
        assertEquals(ReviewSelfReportKey.GOOD, paperReviewEvidenceKey(false, ReviewSelfReportKey.GOOD, true))
    }
    @Test fun unknownOriginalResultDoesNotCompleteTheQuestion() {
        assertNull(paperReviewEvidenceKey(false, null, true))
        assertNull(paperReviewEvidenceKey(true, null, false))
    }
    @Test fun aPaperAnswerMustOccurAfterExportAndBeforeUpload() {
        assertTrue(validPaperAnsweredAt(100, 150, 200))
        assertFalse(validPaperAnsweredAt(100, 99, 200))
        assertFalse(validPaperAnsweredAt(100, 201, 200))
    }

    private fun photo(id: String, ordinal: Int?) = PaperReviewPhoto(id, "file:///$id.png", false, ordinal, "asset:$id")
}
