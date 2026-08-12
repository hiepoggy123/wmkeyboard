package com.wasimaster.wmkeyboard.core.keyman

/**
 * Reads a compiled `.kmx` into a [KeymanKeyboard].
 *
 * **Total.** Arbitrary bytes in, a [KeymanKeyboard] or a [KeymanFault] out —
 * never a throw. That is not politeness: a `.kmx` can arrive from a package the
 * user downloaded, so a malformed one is untrusted input on a path that must not
 * take the keyboard down with it.
 *
 * The parser also **validates every offset and index up front** — each store,
 * group and rule pointer is checked to land inside the file, and every group and
 * store index a rule can name is checked to exist. The interpreter's inner loop
 * then indexes without re-checking, which is the whole reason to do it here.
 */
object KmxParser {

    // Sixteen exits, and each one is a distinct way the bytes can be wrong.
    // Folding them into a nested expression would hide which check failed,
    // which is the one thing this function exists to report.
    @Suppress("ReturnCount")
    fun parse(bytes: ByteArray): KeymanResult<KeymanKeyboard> {
        if (bytes.size > KeymanLimits.MAX_KMX_BYTES) {
            return KeymanResult.Failure(KeymanFault.TOO_LARGE)
        }
        if (bytes.size < KmxFormat.HEADER_SIZE) {
            return KeymanResult.Failure(KeymanFault.TRUNCATED)
        }
        val r = Reader(bytes)

        if (r.u32(KmxFormat.HDR_IDENTIFIER) != KmxFormat.FILE_IDENTIFIER) {
            return KeymanResult.Failure(KeymanFault.BAD_MAGIC)
        }
        val version = r.u32(KmxFormat.HDR_FILE_VERSION)
        if (version < KmxFormat.VERSION_MIN || version > KmxFormat.VERSION_MAX) {
            return KeymanResult.Failure(KeymanFault.UNSUPPORTED_VERSION)
        }

        val storeCount = r.u32(KmxFormat.HDR_STORE_COUNT)
        val groupCount = r.u32(KmxFormat.HDR_GROUP_COUNT)
        if (storeCount !in 0..KeymanLimits.MAX_STORES || groupCount !in 0..KeymanLimits.MAX_GROUPS) {
            return KeymanResult.Failure(KeymanFault.TOO_LARGE)
        }
        val storeArray = r.u32(KmxFormat.HDR_STORE_ARRAY)
        val groupArray = r.u32(KmxFormat.HDR_GROUP_ARRAY)
        if (!r.fits(storeArray, storeCount * KmxFormat.STORE_SIZE) ||
            !r.fits(groupArray, groupCount * KmxFormat.GROUP_SIZE)
        ) {
            return KeymanResult.Failure(KeymanFault.TRUNCATED)
        }

        // --- Stores ---
        val stores = ArrayList<KmxStore>(storeCount)
        for (i in 0 until storeCount) {
            val at = storeArray + i * KmxFormat.STORE_SIZE
            val systemId = r.u32(at)
            val name = r.string(r.u32(at + 4)) ?: return KeymanResult.Failure(KeymanFault.TRUNCATED)
            val value = r.string(r.u32(at + 8)) ?: return KeymanResult.Failure(KeymanFault.TRUNCATED)
            stores += KmxStore(systemId, name, value)
        }

        // --- Groups ---
        var totalRules = 0
        val groups = ArrayList<KmxGroup>(groupCount)
        for (i in 0 until groupCount) {
            val at = groupArray + i * KmxFormat.GROUP_SIZE
            val name = r.string(r.u32(at + KmxFormat.GROUP_OFF_NAME))
                ?: return KeymanResult.Failure(KeymanFault.TRUNCATED)
            val keyArray = r.u32(at + KmxFormat.GROUP_OFF_KEY_ARRAY)
            val keyCount = r.u32(at + KmxFormat.GROUP_OFF_KEY_COUNT)
            val match = r.string(r.u32(at + KmxFormat.GROUP_OFF_MATCH)).orEmpty()
            val noMatch = r.string(r.u32(at + KmxFormat.GROUP_OFF_NOMATCH)).orEmpty()
            val usingKeys = r.u32(at + KmxFormat.GROUP_OFF_USING_KEYS) != 0

            if (keyCount < 0) return KeymanResult.Failure(KeymanFault.TRUNCATED)
            totalRules += keyCount
            if (totalRules > KeymanLimits.MAX_RULES) return KeymanResult.Failure(KeymanFault.TOO_LARGE)
            if (keyCount > 0 && !r.fits(keyArray, keyCount * KmxFormat.KEY_SIZE)) {
                return KeymanResult.Failure(KeymanFault.TRUNCATED)
            }

            val rules = ArrayList<KmxRule>(keyCount)
            for (k in 0 until keyCount) {
                val ka = keyArray + k * KmxFormat.KEY_SIZE
                val output = r.string(r.u32(ka + 12)) ?: return KeymanResult.Failure(KeymanFault.TRUNCATED)
                val context = r.string(r.u32(ka + 16)) ?: return KeymanResult.Failure(KeymanFault.TRUNCATED)
                rules += KmxRule(
                    key = r.u16(ka),
                    line = r.u32(ka + 4),
                    shiftFlags = r.u32(ka + 8),
                    output = output,
                    context = context,
                )
            }
            // Compiled order is longest-context-first within a key and the
            // runtime takes the first match, so the list is kept exactly as
            // read. Sorting it here would silently change which rule wins.
            groups += KmxGroup(name, usingKeys, rules, match, noMatch)
        }

        // --- Entry points ---
        val startGroup = r.u32(KmxFormat.HDR_START_GROUP_UNICODE)
            .takeIf { it != KmxFormat.NO_GROUP && it in 0 until groupCount }
            ?: -1
        if (startGroup < 0) {
            // Keyman Core bails on the same condition: an ANSI-only keyboard has
            // no Unicode entry point and cannot be run.
            return KeymanResult.Failure(KeymanFault.UNSUPPORTED_VERSION)
        }

        val newContext = entryPointStore(stores, KmxFormat.TSS_BEGIN_NEWCONTEXT, groupCount)
        val postKeystroke = entryPointStore(stores, KmxFormat.TSS_BEGIN_POSTKEYSTROKE, groupCount)

        // --- Every index a rule can name must resolve ---
        for (g in groups) {
            for (rule in g.rules) {
                if (!indicesResolve(rule.context, stores.size, groupCount) ||
                    !indicesResolve(rule.output, stores.size, groupCount)
                ) {
                    return KeymanResult.Failure(KeymanFault.INDEX_OUT_OF_RANGE)
                }
            }
            if (!indicesResolve(g.match, stores.size, groupCount) ||
                !indicesResolve(g.noMatch, stores.size, groupCount)
            ) {
                return KeymanResult.Failure(KeymanFault.INDEX_OUT_OF_RANGE)
            }
        }

        val mnemonic = stores.firstOrNull { it.systemId == KmxFormat.TSS_MNEMONIC }?.value == "1"
        return KeymanResult.Success(
            KeymanKeyboard(version, stores, groups, startGroup, newContext, postKeystroke, mnemonic),
        )
    }

    /**
     * `begin NewContext` / `begin PostKeystroke` are stored as the three-unit
     * string `UC_SENTINEL, CODE_USE, groupIndex + 1` rather than as a number,
     * because `COMP_KEYBOARD.StartGroup[]` only has two slots.
     */
    private fun entryPointStore(stores: List<KmxStore>, systemId: Int, groupCount: Int): Int {
        val s = stores.firstOrNull { it.systemId == systemId }?.value ?: return -1
        if (s.length != 3) return -1
        if (s[0].code != KmxFormat.UC_SENTINEL || s[1].code != KmxFormat.CODE_USE) return -1
        val index = s[2].code - 1
        return if (index in 0 until groupCount) index else -1
    }

    /**
     * Every index inside [s] that the interpreter will use to subscript an array
     * names something that exists.
     *
     * Only those. Not every operand is an index, and treating them alike refuses
     * perfectly good keyboards: `CODE_CONTEXTEX` takes a **position in the
     * matched context**, and `CODE_IFSYSTEMSTORE`/`CODE_SETSYSTEMSTORE` take a
     * `TSS_*` **system store id**, neither of which is a subscript into the store
     * array and both of which routinely exceed its length. What is checked here
     * is exactly what the promise of "the hot loop indexes without bounds
     * checks" rests on; anything else the interpreter reads defensively.
     *
     * Note [KmxString.forEachOpcode] already de-biases the operands, so these
     * compare against the raw value rather than subtracting one again — doing it
     * twice is how the first version of this refused Khmer Angkor.
     */
    private fun indicesResolve(s: String, storeCount: Int, groupCount: Int): Boolean {
        KmxString.forEachOpcode(s) { code, operands ->
            val ok = when (code) {
                KmxFormat.CODE_ANY,
                KmxFormat.CODE_NOTANY,
                KmxFormat.CODE_INDEX,
                -> operands[0] in 0 until storeCount

                KmxFormat.CODE_USE -> operands[0] in 0 until groupCount

                else -> true
            }
            if (!ok) return false
        }
        return true
    }

    /** Bounds-checked little-endian view over the file. */
    private class Reader(private val b: ByteArray) {
        fun fits(offset: Int, length: Int): Boolean =
            offset >= 0 && length >= 0 && offset.toLong() + length <= b.size

        fun u32(at: Int): Int {
            if (!fits(at, 4)) return 0
            return (b[at].toInt() and 0xFF) or
                ((b[at + 1].toInt() and 0xFF) shl 8) or
                ((b[at + 2].toInt() and 0xFF) shl 16) or
                ((b[at + 3].toInt() and 0xFF) shl 24)
        }

        fun u16(at: Int): Int {
            if (!fits(at, 2)) return 0
            return (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
        }

        /**
         * A UTF-16LE null-terminated string. Offset 0 is null, which the format
         * uses for "no such string" and which reads back as an empty string
         * rather than a failure. Returns null only when the string runs off the
         * end of the file without a terminator.
         */
        fun string(at: Int): String? {
            if (at == 0) return ""
            if (at < 0 || at >= b.size) return null
            val sb = StringBuilder()
            var i = at
            while (i + 1 < b.size) {
                val unit = u16(i)
                if (unit == 0) return sb.toString()
                sb.append(unit.toChar())
                i += 2
            }
            return null
        }
    }
}
