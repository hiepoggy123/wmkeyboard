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
    fun `trigger match is case-insensitive and blank triggers are dropped`() {
        val store = SnippetStore(null)
        val snippet = store.add("Sig", "On my way!", trigger = "omw")
        assertEquals(snippet.id, store.matchTrigger("OMW")?.id)
        assertEquals(null, store.matchTrigger("omww"))

        val blank = store.add("Blank", "text", trigger = "   ")
        assertEquals(null, blank.trigger)

        store.update(snippet.id, "Sig", "On my way!", trigger = "")
        assertEquals(null, store.items().first { it.id == snippet.id }.trigger)
        assertEquals(null, store.matchTrigger("omw"))
    }

    @Test
    fun `the first snippet wins a duplicate trigger`() {
        // The linear scan this replaced kept the first match, and a map built
        // the obvious way keeps the last.
        val store = SnippetStore(null)
        val first = store.add("A", "first", trigger = "omw")
        store.add("B", "second", trigger = "OMW")
        assertEquals(first.id, store.matchTrigger("omw")?.id)
    }

    @Test
    fun `a pattern snippet is matched without reloading`() {
        val store = SnippetStore(null)
        assertTrue(!store.hasPatterns())
        store.add(Snippet(id = 0, label = "Greet", text = "Hello, \$1!", triggerPattern = "^hi (.+)$"))
        assertTrue(store.hasPatterns())
        assertEquals(
            "Hello, John!",
            store.matchPattern("hi John", atFieldStart = true)?.text,
        )
        assertTrue(store.couldStartPattern('h'))
        assertTrue(!store.couldStartPattern('z'))
    }

    @Test
    fun `update can clear a pattern`() {
        val store = SnippetStore(null)
        val snippet = store.add(
            Snippet(id = 0, label = "Greet", text = "Hello, \$1!", triggerPattern = "^hi (.+)$"),
        )
        store.update(snippet.id, "Greet", "Hello, \$1!", trigger = "hey")
        assertEquals(null, store.items().single().triggerPattern)
        assertTrue(!store.hasPatterns())
    }

    @Test
    fun `add keeps every field of the snippet it is given`() {
        // Import and add-on installation both come through here, so a field
        // dropped on this path is a pack that installs with no trigger and no
        // complaint.
        val store = SnippetStore(null)
        val added = store.add(
            Snippet(
                id = 99,
                label = "  Greet  ",
                text = "Hello, \$1!",
                triggerPattern = "^hi (.+)$",
                triggerWords = 4,
            ),
        )
        assertEquals("Greet", added.label)
        assertEquals("^hi (.+)$", added.triggerPattern)
        assertEquals(4, added.triggerWords)
        // The id in the file is never trusted.
        assertTrue(added.id != 99L)
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
    fun `a pattern survives a save and reload`() {
        val file = File(tmp.root, "snippets.json")
        val store = SnippetStore(file)
        store.add(
            Snippet(
                id = 0,
                label = "Greet",
                text = "Hello, \$1!",
                triggerPattern = "^hi (.+)$",
                triggerWords = 2,
            ),
        )
        store.save()

        val reloaded = SnippetStore(file).items().single()
        assertEquals("^hi (.+)$", reloaded.triggerPattern)
        assertEquals(2, reloaded.triggerWords)
    }

    @Test
    fun `the ask-first flag survives an edit and a reload`() {
        val file = File(tmp.newFolder(), "snippets.json")
        val store = SnippetStore(file)
        val snippet = store.add(
            Snippet(id = 0, label = "Reply", text = "Thanks!", trigger = "ty", confirm = true),
        )
        assertTrue(store.matchTrigger("ty")!!.confirm)
        store.save()
        assertTrue(SnippetStore(file).matchTrigger("TY")!!.confirm)

        // The dialog hands every field back on save, so the flag has to be
        // clearable through the same call that sets it.
        store.update(snippet.id, "Reply", "Thanks!", trigger = "ty", confirm = false)
        store.save()
        assertTrue(!SnippetStore(file).matchTrigger("ty")!!.confirm)
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
