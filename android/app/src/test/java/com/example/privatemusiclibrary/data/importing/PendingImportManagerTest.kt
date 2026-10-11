package com.example.privatemusiclibrary.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PendingImportManagerTest {

    private lateinit var manager: PendingImportManager

    @Before
    fun setUp() {
        manager = PendingImportManager()
    }

    @Test
    fun setPendingUrl_updatesCurrentPendingUrl() {
        manager.setPendingUrl("https://youtu.be/12345")
        assertEquals("https://youtu.be/12345", manager.pendingUrl.value)
    }

    @Test
    fun setPendingUrl_withBlankOrNull_doesNotOverwriteValidPending() {
        manager.setPendingUrl("https://youtu.be/12345")
        manager.setPendingUrl(null)
        assertEquals("https://youtu.be/12345", manager.pendingUrl.value)

        manager.setPendingUrl("   ")
        assertEquals("https://youtu.be/12345", manager.pendingUrl.value)
    }

    @Test
    fun consumePendingUrl_returnsUrlAndClearsStateExactlyOnce() {
        manager.setPendingUrl("https://youtu.be/12345")

        val consumedFirst = manager.consumePendingUrl()
        assertEquals("https://youtu.be/12345", consumedFirst)
        assertNull(manager.pendingUrl.value)

        // Second consume attempt returns null
        val consumedSecond = manager.consumePendingUrl()
        assertNull(consumedSecond)
    }

    @Test
    fun dismissPendingUrl_clearsPendingUrl() {
        manager.setPendingUrl("https://youtu.be/12345")
        assertEquals("https://youtu.be/12345", manager.pendingUrl.value)

        manager.dismissPendingUrl()
        assertNull(manager.pendingUrl.value)
    }
}
