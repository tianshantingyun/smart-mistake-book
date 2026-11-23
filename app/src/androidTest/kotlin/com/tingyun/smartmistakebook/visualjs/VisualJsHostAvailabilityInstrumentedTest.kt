package com.tingyun.smartmistakebook.visualjs

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.SmartMistakeBookApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualJsHostAvailabilityInstrumentedTest {
    @Test fun aHostWithNoRemainingRebuildDoesNotAdvertiseJavaScript() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SmartMistakeBookApplication>()
        val host = requireNotNull(app.visualJsFigureHost)
        assertTrue(host.prepare())
        assertTrue(host.available())
        assertTrue(host.engine.abandonForTest())
        assertTrue(host.engine.connectForTest())
        assertTrue(host.engine.abandonForTest())
        assertFalse(host.engine.connectForTest())
        assertFalse(host.available())
        assertFalse(host.prepare())
    }
}
