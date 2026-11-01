package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class TutorComposerTextPolicyTest {
    @Test fun anEmojiAtTheLengthBoundaryIsNotSplit() {
        val result = boundedTutorInput(TextFieldValue("甲😀乙", TextRange(4)), 2)
        assertEquals("甲", result.text)
        assertEquals(TextRange(1), result.selection)
    }

    @Test fun aComposingCandidateCanFinishBeforeTheLimitIsApplied() {
        val composing = TextFieldValue("拼音候选字", TextRange(5), TextRange(2, 5))
        assertEquals(composing, boundedTutorInput(composing, 3))
        assertEquals("拼音候", boundedTutorInput(composing.copy(composition = null), 3).text)
    }

    @Test fun theLimitPreservesACompleteSurrogatePair() {
        assertEquals("甲😀", boundedTutorInput(TextFieldValue("甲😀乙"), 3).text)
    }
}
