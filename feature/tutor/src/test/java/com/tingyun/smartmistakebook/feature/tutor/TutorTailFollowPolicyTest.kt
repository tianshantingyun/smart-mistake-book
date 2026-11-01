package com.tingyun.smartmistakebook.feature.tutor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorTailFollowPolicyTest {
    @Test fun anEmptyListIsAtItsTail() = assertTrue(isTutorTailVisible(null, 0, null, 700))
    @Test fun aPartiallyVisibleFinalItemIsNotAtItsTail() = assertFalse(isTutorTailVisible(4, 5, 900, 700))
    @Test fun theLastBottomAtTheViewportEdgeIsAtItsTail() = assertTrue(isTutorTailVisible(4, 5, 700, 700))
    @Test fun anEarlierVisibleItemCannotPinTheTail() = assertFalse(isTutorTailVisible(3, 5, 600, 700))
    @Test fun unknownMeasurementsCannotPinANonemptyList() = assertFalse(isTutorTailVisible(null, 5, null, 700))
}
