package com.wasimaster.wmkeyboard.core.keyman

/**
 * Walking a Keyman context or output string.
 *
 * These strings are UTF-16 units in which `0xFFFF` ([KmxFormat.UC_SENTINEL])
 * introduces an opcode and its operands, so a plain index-by-one walk lands in
 * the middle of an escape and a plain `length` counts operands as characters.
 * [next] and [prev] are ports of Keyman Core's `incxstr`/`decxstr`, and the step
 * order in [next] is theirs — including the deliberately tolerant fall-through
 * for a malformed opcode, which is what keeps a corrupt string from walking off
 * the end rather than throwing.
 */
internal object KmxString {

    /**
     * Index of the element after the one starting at [i].
     *
     * Order matters, and is Keyman's:
     *  1. a NUL ends the string;
     *  2. a non-sentinel advances one unit, or two for a surrogate pair;
     *  3. `CODE_EXTENDED` runs to its `UC_SENTINEL_EXTENDEDEND` terminator;
     *  4. an opcode that is out of range or variable-width advances one unit,
     *     rather than trusting a width that would overshoot;
     *  5. otherwise skip the sentinel, the opcode and its operands — bailing
     *     early if a NUL appears inside the run.
     */
    fun next(s: String, i: Int): Int {
        if (i >= s.length) return s.length
        val c = s[i].code
        if (c == 0) return s.length
        if (c != KmxFormat.UC_SENTINEL) {
            return if (Character.isHighSurrogate(s[i]) && i + 1 < s.length) i + 2 else i + 1
        }
        if (i + 1 >= s.length) return s.length
        val code = s[i + 1].code
        if (code == KmxFormat.CODE_EXTENDED) {
            var j = i + 2
            while (j < s.length && s[j].code != KmxFormat.UC_SENTINEL_EXTENDEDEND) {
                if (s[j].code == 0) return s.length
                j++
            }
            return (j + 1).coerceAtMost(s.length)
        }
        val operands = if (code in 0..KmxFormat.CODE_LAST) KmxFormat.CODE_OPERANDS[code] else -1
        if (operands < 0) return i + 1
        var j = i + 2
        var left = operands
        while (left > 0) {
            if (j >= s.length || s[j].code == 0) return s.length
            j++
            left--
        }
        return j
    }

    /**
     * Index of the element before the one at [i], or 0.
     *
     * Looks back at most [KmxFormat.CODE_LAST]-sized runs for a sentinel whose
     * declared operand count lands exactly here — the only way to tell an
     * operand from a character, since operands are ordinary code units.
     */
    fun prev(s: String, i: Int): Int {
        if (i <= 0) return 0
        var back = 1
        val maxBack = MAX_ESCAPE_UNITS.coerceAtMost(i)
        while (back <= maxBack) {
            val at = i - back
            if (s[at].code == KmxFormat.UC_SENTINEL && at + 1 < s.length) {
                val code = s[at + 1].code
                val operands = if (code in 0..KmxFormat.CODE_LAST) KmxFormat.CODE_OPERANDS[code] else -1
                if (operands >= 0 && operands + 2 == back) return at
            }
            back++
        }
        // Not an escape: one character back, allowing for a surrogate pair.
        if (i >= 2 && Character.isLowSurrogate(s[i - 1]) && Character.isHighSurrogate(s[i - 2])) {
            return i - 2
        }
        return i - 1
    }

    /** Number of logical elements, counting each escape as one. */
    fun length(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length && s[i].code != 0) {
            i = next(s, i)
            n++
        }
        return n
    }

    /**
     * [length] but skipping the conditional opcodes, which consume no context.
     * This is `xstrlen_ignoreifopt`, and it is what decides how much context a
     * matched rule covers — so counting the conditionals here would make the
     * engine delete more of the user's text than the rule matched.
     */
    fun lengthIgnoringConditionals(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length && s[i].code != 0) {
            val isConditional = s[i].code == KmxFormat.UC_SENTINEL && i + 1 < s.length &&
                (s[i + 1].code == KmxFormat.CODE_IFOPT || s[i + 1].code == KmxFormat.CODE_IFSYSTEMSTORE)
            i = next(s, i)
            if (!isConditional) n++
        }
        return n
    }

    /** The opcode at [i], or -1 when [i] is an ordinary character. */
    fun opcodeAt(s: String, i: Int): Int {
        if (i + 1 >= s.length) return -1
        if (s[i].code != KmxFormat.UC_SENTINEL) return -1
        return s[i + 1].code
    }

    /**
     * The [n]th operand of the escape at [i], **already de-biased** — the format
     * stores operands as `value + 1` so that none can be a NUL, and every
     * consumer subtracts one, so this returns the value the caller wants.
     */
    fun operandAt(s: String, i: Int, n: Int): Int {
        val at = i + 2 + n
        return if (at < s.length) s[at].code - 1 else -1
    }

    /**
     * Calls [action] for each escape in [s], with de-biased operands.
     *
     * Inline so a caller can return out of it, and used at load time only — the
     * interpreter reads opcodes through [opcodeAt] and [operandAt] instead, so
     * that its inner loop allocates nothing.
     */
    inline fun forEachOpcode(s: String, action: (code: Int, operands: IntArray) -> Unit) {
        var i = 0
        while (i < s.length && s[i].code != 0) {
            if (s[i].code == KmxFormat.UC_SENTINEL && i + 1 < s.length) {
                val code = s[i + 1].code
                val count = if (code in 0..KmxFormat.CODE_LAST) KmxFormat.CODE_OPERANDS[code] else -1
                if (count > 0) {
                    val operands = IntArray(count) { n ->
                        val at = i + 2 + n
                        if (at < s.length) s[at].code - 1 else -1
                    }
                    action(code, operands)
                } else if (count == 0) {
                    action(code, EMPTY_OPERANDS)
                }
            }
            i = next(s, i)
        }
    }

    /** Shared empty array so a zero-operand opcode allocates nothing. */
    val EMPTY_OPERANDS: IntArray = IntArray(0)

    /**
     * Longest an escape can be: sentinel, opcode and the widest operand count.
     * [prev] never looks back further than this.
     */
    private const val MAX_ESCAPE_UNITS: Int = 5
}
