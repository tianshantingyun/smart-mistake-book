package com.tingyun.smartmistakebook.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 导出记录端口的组级半边必须在真实库上可用。
 *
 * 这五个 override 曾与 `MistakeEditJobPort` 一起在 UI 整合合并中丢失，端口落到
 * `UnsupportedOperationException("Export groups are unavailable")`，智能体的
 * `START_EXPORT`/详细答案交付会在建组第一步失败。建组本身要求先有一份冻结答案 manifest
 * （由 `freezeAnswerManifest` 的生产链建立），这里用"实现已安装 + 读口/观察口可用"
 * 做防回归门；真实建组由导出设备链覆盖。
 */
@RunWith(AndroidJUnit4::class)
class ExportRecordPortInstrumentedTest {
    private lateinit var store: StudyDatabasePort

    @Before
    fun setUp() {
        store = StudyDatabaseFactory.openInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun exportGroupPortOverridesAreInstalledAndReadable() = runBlocking {
        val methods = RoomStudyDatabase::class.java.declaredMethods.map { it.name }.toSet()
        listOf(
            "createMistakeExportGroup",
            "readMistakeExportGroup",
            "observeMistakeExportGroups",
            "confirmMistakeAnswerPreview",
            "startGroupedMistakeExport",
        ).forEach { name ->
            assertTrue("RoomStudyDatabase is missing the $name override", name in methods)
        }

        assertNull(store.readMistakeExportGroup("missing-group"))
        assertEquals(emptyList<MistakeExportGroupRecord>(), store.observeMistakeExportGroups(10).first())
    }
}
