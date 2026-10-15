package com.tingyun.smartmistakebook.feature.library

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportTimeTest {
    @Test fun exportTimesFollowTheCurrentDeviceTimeZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"))
            assertEquals("01-01 08:00", formatExportTime(0))
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("01-01 00:00", formatExportTime(0))
        } finally { TimeZone.setDefault(original) }
    }
}
