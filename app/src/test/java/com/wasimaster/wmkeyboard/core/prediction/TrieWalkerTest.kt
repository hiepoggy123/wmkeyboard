package com.wasimaster.wmkeyboard.core.prediction

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Locks every [TrieWalker] implementation to its owning trie's semantics: a
 * full walk must reproduce the trie's word set exactly, and maxSubtree must
 * hold its bound invariant at every node.
 */
class TrieWalkerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val words = listOf(
        "the" to 100,
        "they" to 90,
        "them" to 80,
        "hello" to 70,
        "help" to 60,
        "world" to 50,
        "a" to 40,
        "ab" to 30,
        "abc" to 20,
    )

    private fun walkAll(walker: TrieWalker): Map<String, Int> {
        val out = HashMap<String, Int>()
        val buffer = ChildBuffer()
        fun descend(node: Int, prefix: String) {
            if (walker.isWord(node)) out[prefix] = walker.frequency(node)
            val count = walker.childrenInto(node, buffer)
            // Copy before recursing: the shared buffer is overwritten below us.
            val labels = CharArray(count) { i -> buffer.labels[i] }
            val nodes = IntArray(count) { i -> buffer.nodes[i] }
            for (i in 0 until count) descend(nodes[i], prefix + labels[i])
        }
        descend(walker.root, "")
        return out
    }

    private fun checkInvariants(walker: TrieWalker) {
        val buffer = ChildBuffer()
        fun descend(node: Int): Int {
            var best = if (walker.isWord(node)) walker.frequency(node) else 0
            val count = walker.childrenInto(node, buffer)
            val labels = CharArray(count) { i -> buffer.labels[i] }
            val nodes = IntArray(count) { i -> buffer.nodes[i] }
            for (i in 0 until count) {
                // child() must agree with childrenInto().
                assertEquals(nodes[i], walker.child(node, labels[i]))
                best = maxOf(best, descend(nodes[i]))
            }
            assertEquals("maxSubtree invariant at node $node", best, walker.maxSubtree(node))
            return best
        }
        descend(walker.root)
    }

    private fun verify(source: WordSource) {
        val walkers = source.walkers()
        assertEquals(1, walkers.size)
        val walker = walkers.single()
        assertEquals(words.toMap(), walkAll(walker))
        checkInvariants(walker)
        assertEquals(-1, walker.child(walker.root, 'z'))
    }

    @Test fun nodeTrieWalker() {
        val trie = Trie().apply { for ((w, f) in words) insert(w, f) }
        verify(trie)
    }

    @Test fun packedTrieWalker() {
        verify(PackedTrie.of(words))
    }

    @Test fun mappedTrieWalker() {
        val file = File(temp.root, "walker.wmdict")
        ByteArrayOutputStream().use { bytes ->
            PackedTrieCodec.write(PackedTrie.of(words), bytes)
            file.writeBytes(bytes.toByteArray())
        }
        val mapped = MappedTrie.open(file) ?: error("failed to open $file")
        verify(mapped)
    }

    @Test fun compositeWalkersAreTheUnionOfParts() {
        val a = PackedTrie.of(listOf("one" to 1))
        val b = PackedTrie.of(listOf("two" to 2))
        val composite = CompositeWordSource.of(listOf(a, b))
        assertEquals(2, composite.walkers().size)
    }

    @Test fun reinforceKeepsHandlesValid() {
        val trie = Trie().apply { insert("the", 10) }
        val before = trie.walkers().single()
        val rootChildren = ChildBuffer()
        before.childrenInto(before.root, rootChildren)
        trie.reinforce("they")
        // A fresh walker sees the new word; the old handle space still resolves.
        val after = trie.walkers().single()
        assertEquals(mapOf("the" to 10, "they" to 1), walkAll(after))
        assertTrue(before.child(before.root, 't') >= 0)
    }

    @Test fun flatSourcesExposeNoWalkers() {
        // The default method is the contract: a WordSource that doesn't
        // override walkers() reports none.
        val flat = object : WordSource {
            override fun complete(prefix: String, limit: Int) = emptyList<Suggestion>()
            override fun frequencyOf(word: String) = 0
            override fun contains(word: String) = false
        }
        assertTrue(flat.walkers().isEmpty())
    }
}
