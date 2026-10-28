package com.tingyun.smartmistakebook.feature.tutor

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Opt-in evidence only; normal test runs do not capture the display. */
internal fun captureTutorFullUiEvidence(name: String, waitForIdle: () -> Unit) {
    if (InstrumentationRegistry.getArguments().getString("uiEvidence") != "true") return
    waitForIdle()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir("full-ui-forest"))
    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    try {
        File(directory, name).outputStream().use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    } finally {
        screenshot.recycle()
    }
}
