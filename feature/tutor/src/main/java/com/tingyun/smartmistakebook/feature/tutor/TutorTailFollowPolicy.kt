package com.tingyun.smartmistakebook.feature.tutor

internal fun isTutorTailVisible(
    lastIndex: Int?,
    totalItems: Int,
    lastBottom: Int?,
    viewportEnd: Int,
): Boolean = totalItems == 0 ||
    (lastIndex == totalItems - 1 && lastBottom != null && lastBottom <= viewportEnd)
