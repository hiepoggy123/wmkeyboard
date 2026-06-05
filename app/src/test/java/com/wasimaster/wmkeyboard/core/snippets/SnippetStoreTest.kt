package com.wasimaster.wmkeyboard.core.snippets

import java.io.File
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnippetStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixedTime(): Long = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 19, 16, 45, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `add update remove roundtrip`() {
        val store = SnippetStore(null)
        val snippet = store.add("Sig", "Cheers,\nWasi")
        assertEquals(1, store.items().size)
        store.update(snippet.id, "Signature", "Best,\nWasi")
        assertEquals("Signature", store.items().single().label)
        assertEquals("Best,\nWasi", store.items().single().text)
        store.remove(snippet.id)
        assertTrue(store.items().isEmpty())
    }

    @Test
    fun `persists and reloads`() {
        val file = File(tmp.root, "snippets.json")
        val store = SnippetStore(file)
        store.add("Addr", "12 Road, Dhaka")
        store.add("Mail", "me@example.com")
        store.save()

        val reloaded = SnippetStore(file)
        assertEquals(listOf("Addr", "Mail"), reloaded.items().map { it.label })
        // New ids continue after the highest persisted one.
        val next = reloaded.add("X", "y")
        assertTrue(next.id > reloaded.items().first().id)
    }

    @Test
    fun `expands date and time variables`() {
        val expanded = SnippetStore.expand("Meeting on {date} at {time}", now = fixedTime())
        if (Locale.getDefault().language == "en") {
            assertEquals("Meeting on 19 Jul 2026 at 16:45", expanded)
        } else {
            assertTrue(expanded.contains("2026") && expanded.contains("16:45"))
        }
    }

    @Test
    fun `expands datetime variable`() {
        val expanded = SnippetStore.expand("{datetime}", now = fixedTime())
        assertTrue(expanded.contains("2026") && expanded.contains("16:45"))
    }

    @Test
    fun `expands clipboard variable`() {
        assertEquals(
            "See: pasted-thing",
            SnippetStore.expand("See: {clip}", clipboard = "pasted-thing"),
        )
        assertEquals("See: ", SnippetStore.expand("See: {clip}", clipboard = null))
    }

    @Test
    fun `text without variables is untouched`() {
        assertEquals("plain text", SnippetStore.expand("plain text"))
    }
}
