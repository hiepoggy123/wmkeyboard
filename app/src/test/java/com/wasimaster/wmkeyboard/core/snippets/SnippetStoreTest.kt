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
    fun `a folder that is switched off disarms its triggers and nothing else`() {
        val store = SnippetStore(null)
        val work = store.addFolder("Work")
        val filed = store.add(
            Snippet(id = 0, label = "Sig", text = "Regards", trigger = "sig", folderId = work.id),
        )
        store.add(Snippet(id = 0, label = "Way", text = "On my way!", trigger = "omw"))
        assertEquals(filed.id, store.matchTrigger("sig")?.id)

        store.setFolderEnabled(work.id, false)
        // Gone from the index...
        assertEquals(null, store.matchTrigger("sig"))
        // ...and from nowhere else: the panel still lists it and a tap still
        // inserts it, which is the whole point of the switch.
        assertEquals(2, store.items().size)
        assertTrue(store.items().any { it.id == filed.id })
        // A snippet outside the folder is untouched.
        assertEquals("On my way!", store.matchTrigger("omw")?.text)

        store.setFolderEnabled(work.id, true)
        assertEquals(filed.id, store.matchTrigger("sig")?.id)
    }

    @Test
    fun `a folder that is switched off disarms its patterns too`() {
        val store = SnippetStore(null)
        val folder = store.addFolder("Greetings")
        store.add(
            Snippet(
                id = 0,
                label = "Greet",
                text = "Hello, \$1!",
                triggerPattern = "^hi (.+)$",
                folderId = folder.id,
            ),
        )
        assertTrue(store.hasPatterns())
        store.setFolderEnabled(folder.id, false)
        // Every question the index answers has to agree, not just the match:
        // the keystroke gate asks these before it reads the field at all.
        assertTrue(!store.hasPatterns())
        assertTrue(!store.couldStartPattern('h'))
        assertEquals(null, store.matchPattern("hi John", atFieldStart = true))
    }

    @Test
    fun `deleting a folder keeps its snippets unless asked otherwise`() {
        val store = SnippetStore(null)
        val folder = store.addFolder("Work")
        store.add(Snippet(id = 0, label = "Sig", text = "Regards", folderId = folder.id))
        store.removeFolder(folder.id)
        assertTrue(store.folders().isEmpty())
        assertEquals(1, store.items().size)
        assertEquals(0L, store.items().single().folderId)

        val second = store.addFolder("Work")
        store.add(Snippet(id = 0, label = "Note", text = "…", folderId = second.id))
        store.removeFolder(second.id, withSnippets = true)
        assertEquals(listOf("Sig"), store.items().map { it.label })
    }

    @Test
    fun `an empty folder is pruned and a folder with anything left in it is not`() {
        val store = SnippetStore(null)
        val pack = store.addFolder("Pack")
        val own = store.add(Snippet(id = 0, label = "A", text = "a", folderId = pack.id))
        store.add(Snippet(id = 0, label = "Mine", text = "b", folderId = pack.id))
        // What uninstalling a pack does: drop the pack's own rows, then offer
        // the folder up. Something else is in it, so it stays.
        store.remove(own.id)
        store.removeFolderIfEmpty(pack.id)
        assertEquals(1, store.folders().size)

        store.remove(store.items().single().id)
        store.removeFolderIfEmpty(pack.id)
        assertTrue(store.folders().isEmpty())
    }

    @Test
    fun `addAll recreates a file's folders under fresh ids`() {
        val store = SnippetStore(null)
        // Something already here, so the ids in the file cannot accidentally
        // line up with the ids they are given.
        store.addFolder("Existing")
        val added = store.addAll(
            snippets = listOf(
                Snippet(id = 7, label = "A", text = "a", folderId = 1),
                Snippet(id = 8, label = "B", text = "b", folderId = 1),
                Snippet(id = 9, label = "C", text = "c", folderId = 0),
            ),
            folders = listOf(SnippetFolder(id = 1, name = "Replies")),
        )
        val replies = store.folders().single { it.name == "Replies" }
        assertTrue(replies.id != 1L)
        assertEquals(listOf(replies.id, replies.id, 0L), added.map { it.folderId })
    }

    @Test
    fun `addAll files everything under the fallback when the file declares no folders`() {
        // How a snippet pack installs: one folder named after the pack, and
        // whatever the file thought its own grouping was is flattened into it.
        val store = SnippetStore(null)
        val pack = store.addFolder("Dev shortcuts")
        val added = store.addAll(
            snippets = listOf(
                Snippet(id = 1, label = "A", text = "a", folderId = 4),
                Snippet(id = 2, label = "B", text = "b"),
            ),
            fallbackFolderId = pack.id,
        )
        assertEquals(listOf(pack.id, pack.id), added.map { it.folderId })
    }

    @Test
    fun `folders survive a save and reload`() {
        val file = File(tmp.newFolder(), "snippets.json")
        val store = SnippetStore(file)
        val work = store.addFolder("Work")
        store.setFolderEnabled(work.id, false)
        store.add(Snippet(id = 0, label = "Sig", text = "Regards", trigger = "sig", folderId = work.id))
        store.save()

        val reloaded = SnippetStore(file)
        val folder = reloaded.folders().single()
        assertEquals("Work", folder.name)
        assertTrue(!folder.enabled)
        assertEquals(folder.id, reloaded.items().single().folderId)
        // The switch has to survive the trip too, or a folder someone silenced
        // starts firing again on the next launch.
        assertEquals(null, reloaded.matchTrigger("sig"))
        // Fresh folder ids continue past the highest persisted one.
        assertTrue(reloaded.addFolder("Other").id > folder.id)
    }

    @Test
    fun `a snippet pointing at a folder nobody declared ends up in none`() {
        val file = File(tmp.newFolder(), "snippets.json")
        file.writeText(
            """{"snippets":[{"id":1,"label":"A","text":"a","folderId":42}],"folders":[]}""",
        )
        assertEquals(0L, SnippetStore(file).items().single().folderId)
    }

    @Test
    fun `an edit leaves the folder alone unless it is given one`() {
        val store = SnippetStore(null)
        val folder = store.addFolder("Work")
        val snippet = store.add(Snippet(id = 0, label = "Sig", text = "Regards", folderId = folder.id))
        store.update(snippet.id, "Signature", "Best")
        assertEquals(folder.id, store.items().single().folderId)
        store.update(snippet.id, "Signature", "Best", folderId = 0)
        assertEquals(0L, store.items().single().folderId)
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
