package com.tingyun.smartmistakebook.feature.capture

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun ComposeContentTestRule.captureCaptureVisualEvidence(fileName: String) {
    if (InstrumentationRegistry.getArguments().getString("uiEvidence") != "true") return
    waitForIdle()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir("full-ui-forest"))
    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    try {
        File(directory, fileName).outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    } finally {
        bitmap.recycle()
    }
}
