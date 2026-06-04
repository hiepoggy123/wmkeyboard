package com.wasimaster.wmkeyboard.core.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardStoreTest {

    @Test fun addAndOrder() {
        val store = ClipboardStore(null)
        store.add("first", now = 1000)
        store.add("second", now = 2000)
        assertEquals(listOf("second", "first"), store.items(now = 3000).map { it.text })
    }

    @Test fun duplicateMovesToTop() {
        val store = ClipboardStore(null)
        store.add("a", now = 1000)
        store.add("b", now = 2000)
        store.add("a", now = 3000)
        val texts = store.items(now = 4000).map { it.text }
        assertEquals(listOf("a", "b"), texts)
        assertEquals(2, texts.size)
    }

    @Test fun pinnedFirstAndSurvivesExpiry() {
        val store = ClipboardStore(null, expiryMillis = 100)
        val old = store.add("keep me", now = 0)!!
        store.setPinned(old.id, true)
        store.add("fresh", now = 500)
        val texts = store.items(now = 550).map { it.text }
        assertEquals(listOf("keep me", "fresh"), texts)
    }

    @Test fun unpinnedExpires() {
        val store = ClipboardStore(null, expiryMillis = 100)
        store.add("ephemeral", now = 0)
        assertTrue(store.items(now = 200).isEmpty())
    }

    @Test fun searchFiltersCaseInsensitive() {
        val store = ClipboardStore(null, expiryMillis = 0)
        store.add("Hello World", now = 1000)
        store.add("other", now = 2000)
        assertEquals(1, store.search("hello").size)
    }

    @Test fun clearUnpinnedKeepsPins() {
        val store = ClipboardStore(null)
        val pin = store.add("pinned", now = 1000)!!
        store.setPinned(pin.id, true)
        store.add("gone", now = 2000)
        store.clearUnpinned()
        assertEquals(listOf("pinned"), store.items(now = 3000).map { it.text })
    }
}
