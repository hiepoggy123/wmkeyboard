package com.wasimaster.wmkeyboard.core.prediction

/**
 * In-memory index of the user's contact email addresses, so typing the start
 * of one completes the whole address ("john" → john.doe@gmail.com). Built from
 * a one-off contacts query; nothing is persisted.
 *
 * Unlike [NameIndex], which splits names into their component words, an email
 * is kept as a single token: its "@", "." and "-" would otherwise fragment it
 * into useless pieces. Addresses are stored lowercase — email local parts are
 * treated case-insensitively in practice — and matched by whole-string prefix.
 */
class ContactEmails private constructor(private val index: WordSource, val size: Int) {

    val isEmpty: Boolean get() = size == 0

    /** True when [address] (lowercase) is a known contact email. */
    fun contains(address: String): Boolean = index.contains(address)

    /** Contact emails starting with [prefix] (lowercase), capped at [limit]. */
    fun complete(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        return index.complete(prefix, limit).map { it.word }
    }

    companion object {
        val EMPTY = ContactEmails(PackedTrie.EMPTY, 0)

        /**
         * Builds an index from raw address strings, keeping only plausible
         * addresses (one "@" with something either side, no whitespace) and
         * de-duplicating case-insensitively.
         */
        fun fromAddresses(addresses: Iterable<String>): ContactEmails {
            val seen = HashSet<String>()
            for (raw in addresses) {
                val email = raw.trim().lowercase()
                val at = email.indexOf('@')
                if (at <= 0 || at >= email.length - 1) continue
                if (email.any { it.isWhitespace() }) continue
                seen.add(email)
            }
            return ContactEmails(PackedTrie.of(seen.map { it to 1 }), seen.size)
        }
    }
}
