package com.tingyun.smartmistakebook.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCompatibilityHeadersTest {
    @Test
    fun `opencode zen endpoints carry the required session header`() {
        val headers = providerCompatibilityHeaders(OPENCODE_ZEN_HOST)
        assertEquals(1, headers.size)
        assertEquals(OPENCODE_SESSION_HEADER, headers.single().first)
        assertTrue(headers.single().second.isNotBlank())
    }

    @Test
    fun `the session id stays stable within the process`() {
        val first = providerCompatibilityHeaders("opencode.ai").single().second
        val second = providerCompatibilityHeaders("opencode.ai").single().second
        assertEquals(first, second)
    }

    @Test
    fun `the host match is case insensitive`() {
        assertEquals(1, providerCompatibilityHeaders("OpenCode.AI").size)
    }

    @Test
    fun `other providers receive no compatibility headers`() {
        assertEquals(emptyList<Pair<String, String>>(), providerCompatibilityHeaders("api.openai.com"))
        assertEquals(emptyList<Pair<String, String>>(), providerCompatibilityHeaders("api.commandcode.ai"))
    }

    @Test
    fun `a lookalike host is not treated as opencode`() {
        assertEquals(
            emptyList<Pair<String, String>>(),
            providerCompatibilityHeaders("opencode.ai.evil.example"),
        )
    }
}
