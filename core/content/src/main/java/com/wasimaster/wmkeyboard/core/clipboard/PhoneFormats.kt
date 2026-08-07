package com.wasimaster.wmkeyboard.core.clipboard

/**
 * One phone-number shape, parsed out of a mask the user wrote.
 *
 * [countryCode] is the literal dial code a mask spelled with a leading `+`, and
 * null for a mask written the national way. [national] is the rest, one
 * character per digit: a literal digit that has to match, or [PhoneFormats.ANY]
 * for any digit at all. Separators are not kept — they are decoration in the
 * mask exactly as they are noise in a clip.
 */
data class PhoneMask(
    val countryCode: String?,
    val national: String,
)

/**
 * Phone-number masks: what a number a user actually copies looks like.
 *
 * The plain detector in [ClipEntities] has to guess from shape alone, so it
 * offers any 7-to-15 digit run that is not obviously a date or an order number.
 * That is right for someone who copies numbers from three countries and wrong
 * for everyone else, whose false positives all look the same: an invoice total,
 * a tracking id, a row of figures out of a spreadsheet.
 *
 * A mask is the fix. `+880 1XXX-XXXXXX` says "eleven digits, and the national
 * part starts with a 1"; anything else stops being a phone number. The leeway
 * is deliberate and goes in one direction only:
 *
 *  - Separators are ignored on both sides, so `01712-345678`, `01712 345678`
 *    and `(01712) 345678` are the same number to a mask, whatever the mask
 *    itself was written with.
 *  - A mask with a country code also matches the number without it, and with a
 *    leading trunk `0` in its place. One mask covers `+8801712345678`,
 *    `8801712345678`, `01712345678` and `1712345678`.
 *  - A clip that carries an explicit `+` or `00` has to match a country code,
 *    because it named one. That is what keeps a foreign number out.
 *
 * Masks are a filter, never a source: a number still has to be found by
 * [ClipEntities] first, and every other guard there still applies.
 */
object PhoneFormats {

    /** The mask character for "any digit". */
    const val ANY = 'X'

    /** Also accepted when a mask is written by hand, and folded to [ANY]. */
    private const val ANY_ALIASES = "xX#"

    /** Punctuation people write numbers with, ignored everywhere. */
    private const val SEPARATORS = " -().[]/"

    /** Below this a mask stops naming a number and starts matching digits. */
    private const val MIN_DIGITS = 4

    /** E.164 caps a number at 15 digits. */
    private const val MAX_DIGITS = 15

    /** The national prefix people write in front of a number instead of a dial code. */
    private const val TRUNK = "0"

    /**
     * The two-digit dial codes. E.164 gives zone 1 (North America) and zone 7
     * (Russia and Kazakhstan) one digit, hands two digits to the countries
     * below, and gives everything else three, so these three rules cover every
     * dial code without a 200-row table.
     */
    private val TWO_DIGIT_CODES = setOf(
        20, 27,
        30, 31, 32, 33, 34, 36, 39,
        40, 41, 43, 44, 45, 46, 47, 48, 49,
        51, 52, 53, 54, 55, 56, 57, 58,
        60, 61, 62, 63, 64, 65, 66,
        81, 82, 84, 86,
        90, 91, 92, 93, 94, 95, 98,
    )

    /**
     * The mask [raw] describes, or null when it is not a mask: no digits at
     * all, a character that is neither a digit nor a separator, a length no
     * number has, or a country code written as `X` (a dial code that matches
     * anything is not a dial code).
     */
    fun parse(raw: String): PhoneMask? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val international = trimmed.startsWith("+")
        val body = if (international) trimmed.substring(1) else trimmed
        val template = StringBuilder()
        // Where the country code ends, if the mask separated it off itself.
        var codeEnd = -1
        for (ch in body) {
            when {
                ch.isDigit() -> template.append(ch)
                ch in ANY_ALIASES -> template.append(ANY)
                ch in SEPARATORS ->
                    if (international && codeEnd < 0 && template.isNotEmpty()) {
                        codeEnd = template.length
                    }
                else -> return null
            }
        }
        if (template.length !in MIN_DIGITS..MAX_DIGITS) return null
        val digits = template.toString()
        if (!international) return PhoneMask(null, digits)
        val split = if (codeEnd > 0) codeEnd else dialCodeLength(digits)
        val code = digits.take(split)
        if (code.length >= digits.length) return null
        // A dial code spelled `+XX` names no country, so the mask keeps only
        // what follows it and reads as a national one.
        if (code.any { it == ANY }) return PhoneMask(null, digits.drop(split))
        return PhoneMask(code, digits.drop(split))
    }

    /** True when [raw] is a mask this can use. */
    fun isValid(raw: String): Boolean = parse(raw) != null

    /**
     * [raw] as it is stored and shown: trimmed, with `x` and `#` written as
     * [ANY] so two spellings of one mask cannot both sit in the list. The
     * separators are the user's and stay as they typed them. Null when [raw] is
     * not a mask.
     */
    fun canonical(raw: String): String? {
        val trimmed = raw.trim()
        if (!isValid(trimmed)) return null
        return buildString {
            for (ch in trimmed) append(if (ch in ANY_ALIASES) ANY else ch)
        }
    }

    /** The masks in [raw] that parse; the rest are settings from a newer build. */
    fun parseAll(raw: Collection<String>): List<PhoneMask> = raw.mapNotNull(::parse)

    /**
     * Turns a number the user recognises into a mask: the dial code stays
     * literal, every other digit becomes [ANY], and the separators are kept so
     * the mask still looks like the number it came from.
     *
     * Returns null when [example] is not a number. The result is a starting
     * point that the user can edit — typing a digit back into it pins that
     * position, which is how `+880 1XXX-XXXXXX` gets its 1.
     */
    fun fromExample(example: String): String? {
        val trimmed = example.trim()
        if (trimmed.isEmpty()) return null
        val international = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val codeLength = if (international) dialCodeLength(digits) else 0
        val mask = StringBuilder()
        var seen = 0
        for (ch in trimmed) {
            when {
                ch.isDigit() -> {
                    mask.append(if (seen < codeLength) ch else ANY)
                    seen++
                }
                ch == '+' && mask.isEmpty() -> mask.append('+')
                ch in SEPARATORS -> mask.append(ch)
                // A letter or a currency sign says this was never a number.
                else -> return null
            }
        }
        val result = mask.toString()
        return if (isValid(result)) result else null
    }

    /**
     * Whether [raw] — a digit run as it was found in a clip, separators and
     * all — is a number the user said they copy.
     *
     * An empty [masks] means the user set no formats, so every number passes.
     */
    fun matches(raw: String, masks: List<PhoneMask>): Boolean {
        if (masks.isEmpty()) return true
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return false
        // `00` is the same statement as `+`: what follows names a country.
        val hasZeroZero = trimmed.startsWith("00") && digits.length >= MIN_DIGITS + 2
        val international = trimmed.startsWith("+") || hasZeroZero
        val full = if (hasZeroZero) digits.substring(2) else digits
        return masks.any { mask -> matches(digits, full, international, mask) }
    }

    private fun matches(
        digits: String,
        full: String,
        international: Boolean,
        mask: PhoneMask,
    ): Boolean {
        if (international) {
            // The clip named a country, so the mask has to agree with it.
            return if (mask.countryCode != null) {
                fits(full, mask.countryCode + mask.national)
            } else {
                // A national mask has nothing to say about dial codes, so the
                // clip's own code is taken off and the rest is compared.
                fitsNational(full.drop(dialCodeLength(full)), mask.national)
            }
        }
        if (fitsNational(digits, mask.national)) return true
        // A number pasted with its dial code but no `+`.
        return mask.countryCode != null && fits(digits, mask.countryCode + mask.national)
    }

    /**
     * The national part, allowing for the trunk prefix that people write in
     * front of a number or leave off. Both spellings are the same number, and
     * a mask written either way accepts both.
     *
     * The zero is only ever added to the side that does not have it. Adding it
     * to both would make a mask of nine wildcards accept ten digits as well,
     * which is the vagueness this whole file exists to remove.
     */
    private fun fitsNational(value: String, template: String): Boolean {
        if (fits(value, template)) return true
        return if (template.startsWith(TRUNK)) {
            fits(TRUNK + value, template)
        } else {
            value.startsWith(TRUNK) && fits(value.drop(TRUNK.length), template)
        }
    }

    /** Whether a run of digits fills a template of literals and [ANY]. */
    private fun fits(value: String, template: String): Boolean {
        if (value.length != template.length) return false
        for (index in template.indices) {
            val expected = template[index]
            if (expected != ANY && expected != value[index]) return false
        }
        return true
    }

    /** How many of these digits are the dial code. See [TWO_DIGIT_CODES]. */
    private fun dialCodeLength(digits: String): Int {
        val first = digits.firstOrNull() ?: return 0
        if (first == '1' || first == '7') return 1
        val two = digits.take(2).toIntOrNull() ?: return minOf(3, digits.length)
        return if (two in TWO_DIGIT_CODES) 2 else 3
    }
}
