package com.wasimaster.wmkeyboard.core.keyman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves [KmxParser] against real compiler output, and proves it is total.
 *
 * The fixtures are official `.kmx` binaries — see `PROVENANCE.md` beside them.
 * Parsing bytes this repository generated itself would only show the parser
 * agrees with its own assumptions, and the assumptions are the risky part: the
 * `COMP_GROUP` field order and the operand bias are both things a plausible
 * misreading gets silently wrong rather than loudly.
 */
class KmxParserTest {

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("kmx/$name")) {
            "missing test fixture kmx/$name"
        }.use { it.readBytes() }

    private fun parsed(name: String): KeymanKeyboard {
        val result = KmxParser.parse(fixture(name))
        assertTrue(
            "$name did not parse: ${(result as? KeymanResult.Failure)?.fault}",
            result is KeymanResult.Success,
        )
        return (result as KeymanResult.Success).value
    }

    @Test
    fun `every fixture parses`() {
        for (name in FIXTURES) {
            val kb = parsed(name)
            assertTrue("$name has no groups", kb.groups.isNotEmpty())
            assertTrue("$name has no rules", kb.ruleCount > 0)
        }
    }

    /**
     * The check that catches a wrong `COMP_GROUP` field order. Reading
     * `cxKeyArray` from offset 8 instead of 16 picks up `dpMatch`, a file
     * offset, which is a large positive number — so the rule count comes out in
     * the tens of thousands and the parse either fails the bounds check or
     * produces nonsense rules. A plausible count is the signal.
     */
    @Test
    fun `rule counts are plausible`() {
        for (name in FIXTURES) {
            val kb = parsed(name)
            for (g in kb.groups) {
                assertTrue(
                    "$name group '${g.name}' claims ${g.rules.size} rules",
                    g.rules.size in 0..5_000,
                )
            }
            assertTrue("$name claims ${kb.ruleCount} rules", kb.ruleCount in 1..20_000)
        }
    }

    /** Every keyboard names itself, which proves the store table was read. */
    @Test
    fun `every fixture carries a name store`() {
        for (name in FIXTURES) {
            val kb = parsed(name)
            assertNotNull("$name has no &NAME store", kb.name)
            assertTrue("$name has an empty &NAME store", kb.name!!.isNotEmpty())
        }
    }

    @Test
    fun `the start group is a real group`() {
        for (name in FIXTURES) {
            val kb = parsed(name)
            assertTrue(
                "$name start group ${kb.startGroup} is not in 0..${kb.groups.size - 1}",
                kb.startGroup in kb.groups.indices,
            )
        }
    }

    /**
     * Rule order is the compiler's, longest context first within a key, and the
     * interpreter takes the first match — so if the parser ever sorted, the
     * wrong rule would win. Checked by confirming context lengths are
     * non-increasing within each run of equal keys.
     */
    @Test
    fun `rules keep their compiled order`() {
        for (name in FIXTURES) {
            val kb = parsed(name)
            for (g in kb.groups) {
                var previousKey = Int.MIN_VALUE
                var previousLength = Int.MAX_VALUE
                for (rule in g.rules) {
                    val length = KmxString.length(rule.context)
                    if (rule.key != previousKey) {
                        previousKey = rule.key
                        previousLength = length
                        continue
                    }
                    assertTrue(
                        "$name group '${g.name}' key ${rule.key}: context length rose " +
                            "$previousLength -> $length, so the array was re-sorted",
                        length <= previousLength,
                    )
                    previousLength = length
                }
            }
        }
    }

    /**
     * Walking with [KmxString.next] must consume every string exactly, landing
     * on the end rather than past it. An off-by-one in the operand widths shows
     * up here and nowhere else until the interpreter misreads a rule.
     */
    @Test
    fun `every context and output walks cleanly`() {
        for (name in FIXTURES) {
            val kb = parsed(name)
            for (g in kb.groups) {
                for (rule in g.rules) {
                    assertWalks("$name ${g.name} context", rule.context)
                    assertWalks("$name ${g.name} output", rule.output)
                }
            }
        }
    }

    private fun assertWalks(where: String, s: String) {
        var i = 0
        var steps = 0
        while (i < s.length && s[i].code != 0) {
            val next = KmxString.next(s, i)
            assertTrue("$where: next() did not advance at $i", next > i)
            i = next
            assertTrue("$where: walk ran away", ++steps <= s.length + 1)
        }
        assertTrue("$where: walk overshot ${s.length} to $i", i <= s.length)
    }

    /** [KmxString.prev] must undo [KmxString.next] for every element. */
    @Test
    fun `prev is the inverse of next`() {
        for (name in FIXTURES) {
            val kb = parsed(name)
            for (g in kb.groups) {
                for (rule in g.rules) {
                    var i = 0
                    while (i < rule.context.length && rule.context[i].code != 0) {
                        val next = KmxString.next(rule.context, i)
                        if (next >= rule.context.length) break
                        assertEquals(
                            "$name ${g.name}: prev(next($i)) != $i",
                            i,
                            KmxString.prev(rule.context, next),
                        )
                        i = next
                    }
                }
            }
        }
    }

    // --- Totality ---

    @Test
    fun `an empty file is refused, not thrown`() {
        assertEquals(
            KeymanFault.TRUNCATED,
            (KmxParser.parse(ByteArray(0)) as KeymanResult.Failure).fault,
        )
    }

    @Test
    fun `a file with the wrong magic is refused`() {
        val bytes = fixture(FIXTURES[0]).copyOf()
        bytes[0] = 0
        assertEquals(
            KeymanFault.BAD_MAGIC,
            (KmxParser.parse(bytes) as KeymanResult.Failure).fault,
        )
    }

    /**
     * Every truncation of a real keyboard either parses into something
     * self-consistent or is refused — never throws. That is the property that
     * matters, because a `.kmx` can arrive inside a package the user downloaded
     * and a half-written one must not take the keyboard down with it.
     *
     * Note it is *not* "every truncation is refused". A `.kmx` ends with its
     * icon bitmap, which nothing here reads: khmer_angkor is 27,226 bytes with
     * the bitmap occupying the last 1,150, so any cut inside that tail leaves a
     * complete, working keyboard. Asserting refusal there would be asserting a
     * bug. Cuts *before* the bitmap must be refused, and that is checked.
     */
    @Test
    fun `every truncation either parses cleanly or is refused, never throws`() {
        val full = fixture("khmer_angkor.kmx")
        val bitmapOffset = readU32(full, 0x38)
        assertTrue("fixture has no bitmap; pick another for this test", bitmapOffset in 1 until full.size)

        var length = 0
        while (length < full.size) {
            when (val result = KmxParser.parse(full.copyOf(length))) {
                is KeymanResult.Failure -> Unit
                is KeymanResult.Success -> {
                    assertTrue(
                        "truncation to $length bytes cut real data at $bitmapOffset yet parsed",
                        length >= bitmapOffset,
                    )
                    val kb = result.value
                    assertTrue("start group dangles at $length", kb.startGroup in kb.groups.indices)
                    for (g in kb.groups) {
                        for (rule in g.rules) {
                            assertTrue(
                                "a use() dangles at $length",
                                usesResolve(rule.output, kb.groups.size),
                            )
                        }
                    }
                }
            }
            length += if (length < 512) 1 else 97
        }
    }

    private fun readU32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or
            ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or
            ((b[at + 3].toInt() and 0xFF) shl 24)

    /**
     * Flipping single bytes in the header must never produce a keyboard whose
     * indices are out of range — either it parses and every index resolves, or
     * it is refused.
     */
    @Test
    fun `header mutations never produce an inconsistent keyboard`() {
        val full = fixture("lao_2008_basic.kmx")
        for (offset in 0 until KmxFormat.HEADER_SIZE) {
            for (value in listOf(0x00, 0xFF, 0x7F)) {
                val bytes = full.copyOf()
                bytes[offset] = value.toByte()
                when (val result = KmxParser.parse(bytes)) {
                    is KeymanResult.Failure -> Unit
                    is KeymanResult.Success -> {
                        val kb = result.value
                        assertTrue(
                            "mutation at $offset=$value left start group ${kb.startGroup} dangling",
                            kb.startGroup in kb.groups.indices,
                        )
                        for (g in kb.groups) {
                            for (rule in g.rules) {
                                assertTrue(
                                    "mutation at $offset=$value left a use() dangling",
                                    usesResolve(rule.output, kb.groups.size),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun usesResolve(s: String, groupCount: Int): Boolean {
        var i = 0
        while (i < s.length && s[i].code != 0) {
            if (KmxString.opcodeAt(s, i) == KmxFormat.CODE_USE) {
                if (KmxString.operandAt(s, i, 0) !in 0 until groupCount) return false
            }
            i = KmxString.next(s, i)
        }
        return true
    }

    private companion object {
        val FIXTURES = listOf(
            "basic_kbdus.kmx",
            "khmer_angkor.kmx",
            "lao_2008_basic.kmx",
            "sil_euro_latin.kmx",
            "sil_ipa.kmx",
        )
    }
}
