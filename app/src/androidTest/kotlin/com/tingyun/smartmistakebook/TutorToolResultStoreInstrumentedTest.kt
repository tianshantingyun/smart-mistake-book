package com.tingyun.smartmistakebook

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real app-private artifact store: deterministic ids, bounded paging, absent/corrupt reads. */
@RunWith(AndroidJUnit4::class)
class TutorToolResultStoreInstrumentedTest {
    private val store = SharedPreferencesTutorToolResultStore(
        ApplicationProvider.getApplicationContext(),
    )

    @Test
    fun writesAreContentAddressedAndReadBackInBoundedChunks() = runBlocking {
        val payload = "{\"items\":[" + (1..500).joinToString(",") { "\"item-$it\"" } + "]}"
        val id = store.write("conversation-1", "NOTEBOOK_READ", payload)
        assertEquals(id, store.write("conversation-1", "NOTEBOOK_READ", payload))

        val first = store.read(id, 0, 3_500) ?: error("first chunk must exist")
        assertTrue(first.text.isNotEmpty())
        assertTrue(first.text.length <= 3_500)
        assertEquals(payload.length, first.totalChars)
        assertFalse(first.complete)

        val second = store.read(id, first.nextOffset, 3_500) ?: error("second chunk must exist")
        assertTrue(second.offset > 0)
        assertTrue(second.nextOffset > first.nextOffset)
        assertEquals(payload.substring(first.nextOffset, second.nextOffset), second.text)

        assertNull(store.read("r-" + "0".repeat(64), 0, 100))
        assertNull(store.read("not-an-artifact", 0, 100))
        assertNull(store.read(id, payload.length + 1, 100))
    }
}
