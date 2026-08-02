package com.wasimaster.wmkeyboard.core.aihistory

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiHistoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun entry(
        input: String = "before",
        output: String = "after",
        action: String = "builtin_rewrite",
    ) = AiHistoryEntry(
        id = 0,
        timestamp = 1_700_000_000_000,
        actionId = action,
        actionName = "Rewrite",
        provider = "GEMINI",
        model = "gemini-3.5-flash",
        input = input,
        output = output,
        durationMs = 1_200,
    )

    private fun file() = File(tmp.newFolder(), AiHistoryStore.FILE_PATH)

    @Test
    fun `records come back newest first`() {
        val store = AiHistoryStore(null)
        store.add(entry(input = "one"))
        store.add(entry(input = "two"))
        store.add(entry(input = "three"))
        assertEquals(listOf("three", "two", "one"), store.items().map { it.input })
    }

    @Test
    fun `the oldest records fall off at the limit`() {
        val store = AiHistoryStore(null, maxItems = 3)
        for (i in 1..5) store.add(entry(input = "run $i"))
        assertEquals(3, store.size())
        assertEquals(listOf("run 5", "run 4", "run 3"), store.items().map { it.input })
    }

    @Test
    fun `lowering the limit trims what is already stored`() {
        val store = AiHistoryStore(null, maxItems = 10)
        for (i in 1..6) store.add(entry(input = "run $i"))
        store.trimTo(2)
        assertEquals(listOf("run 6", "run 5"), store.items().map { it.input })
    }

    @Test
    fun `long text is cut and the record says so`() {
        val store = AiHistoryStore(null)
        val stored = store.add(entry(input = "x".repeat(AiHistoryStore.MAX_TEXT + 500)))
        assertEquals(AiHistoryStore.MAX_TEXT, stored.input.length)
        assertTrue(stored.textCut)
    }

    @Test
    fun `a cut never leaves half a character behind`() {
        // An emoji sitting exactly on the boundary would otherwise be split
        // into an unpaired code unit, which then travels wherever the record
        // is copied to.
        val store = AiHistoryStore(null)
        val text = "a".repeat(AiHistoryStore.MAX_TEXT - 1) + "🍎" + "b".repeat(10)
        val stored = store.add(entry(input = text))
        assertFalse(stored.input.last().isHighSurrogate())
        assertEquals(AiHistoryStore.MAX_TEXT - 1, stored.input.length)
    }

    @Test
    fun `short text is left alone`() {
        val stored = AiHistoryStore(null).add(entry())
        assertFalse(stored.textCut)
        assertEquals("before", stored.input)
        assertEquals("after", stored.output)
    }

    @Test
    fun `records survive a save and a reload`() {
        val file = file()
        val first = AiHistoryStore(file)
        first.add(entry(input = "kept"))
        first.save()

        val second = AiHistoryStore(file)
        assertEquals(1, second.size())
        assertEquals("kept", second.items().single().input)
    }

    @Test
    fun `a commit is recorded and survives a reload`() {
        val file = file()
        val store = AiHistoryStore(file)
        val stored = store.add(entry())
        assertEquals(AiHistoryEntry.COMMITTED_NONE, stored.committed)
        store.markCommitted(stored.id, AiHistoryEntry.COMMITTED_REPLACE)
        store.save()

        val reopened = AiHistoryStore(file)
        assertEquals(AiHistoryEntry.COMMITTED_REPLACE, reopened.items().single().committed)
    }

    @Test
    fun `reload after the file is deleted elsewhere gives nothing`() {
        // What the keyboard sees after the settings app deleted the history:
        // it must come back empty rather than re-saving what it still held.
        val file = file()
        val store = AiHistoryStore(file)
        store.add(entry())
        store.save()
        assertTrue(file.delete())

        store.reload()
        assertTrue(store.isEmpty())
    }

    @Test
    fun `deleting the storage empties the file and the memory`() {
        val file = file()
        val store = AiHistoryStore(file)
        store.add(entry())
        store.save()
        assertTrue(file.isFile)

        store.deleteStorage()
        assertTrue(store.isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun `deleting one record leaves the rest`() {
        val store = AiHistoryStore(null)
        val first = store.add(entry(input = "one"))
        store.add(entry(input = "two"))
        store.delete(first.id)
        assertEquals(listOf("two"), store.items().map { it.input })
    }

    @Test
    fun `with no file it still records but writes nothing`() {
        // The contract the keyboard relies on before the device is unlocked.
        val store = AiHistoryStore(null)
        store.add(entry())
        store.save()
        assertEquals(1, store.size())
    }

    @Test
    fun `a record holds these fields and no others`() {
        // A schema guard. The risk this catches is a future field quietly
        // carrying something that must never be written down, so the list is
        // spelled out rather than derived.
        val file = file()
        val store = AiHistoryStore(file)
        store.add(entry())
        store.save()

        val keys = Regex(""""([a-zA-Z]+)":""").findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(
            setOf(
                "entries", "id", "timestamp", "actionId", "actionName", "provider",
                "model", "input", "output", "durationMs",
            ),
            keys,
        )
    }
}
