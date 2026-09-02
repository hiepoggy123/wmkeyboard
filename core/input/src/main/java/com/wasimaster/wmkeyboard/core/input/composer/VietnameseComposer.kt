package com.wasimaster.wmkeyboard.core.input.composer

import java.text.Normalizer

/**
 * Vietnamese input by the two dominant methods, Telex and VNI. Both are
 * transliterating composers: the roman keystrokes accumulate in the service
 * buffer and [composeBuffer] folds them into fully-toned Vietnamese, shown as
 * the composing region and committed as a unit — the same shape as Avro/Hangul,
 * so no dictionary and no candidate list are involved.
 *
 * Telex spells the diacritics with letters (`as`→á, `aa`→â, `aw`→ă, `ow`→ơ,
 * `w`→ư, `dd`→đ, tones s/f/r/x/j); VNI spells them with digits (1..5 tones,
 * 6 circumflex, 7 horn, 8 breve, 9 đ, 0 clears the tone). The engine is shared;
 * only the keystroke→intent mapping differs.
 *
 * The tone lands on the syllable's main vowel by the standard rule: a vowel that
 * already carries a mark (â ê ô ă ơ ư) wins; otherwise a single vowel takes it,
 * a closed cluster puts it on the last vowel, and an open cluster on the first
 * (except oa/oe/uy, which take the second). `qu`/`gi` onsets are not counted as
 * the nucleus.
 */
internal enum class VMark { NONE, CIRCUMFLEX, BREVE, HORN, STROKE }

internal enum class VTone(val combining: Char?) {
    NONE(null), ACUTE('́'), GRAVE('̀'), HOOK('̉'), TILDE('̃'), DOT('̣')
}

private class VLetter(var base: Char, var mark: VMark, val upper: Boolean)

internal object VietnameseEngine {

    private fun isVowel(c: Char) = c in "aeiouy"

    private fun precompose(base: Char, mark: VMark): Char = when (mark) {
        VMark.NONE -> base
        VMark.CIRCUMFLEX -> when (base) { 'a' -> 'â'; 'e' -> 'ê'; 'o' -> 'ô'; else -> base }
        VMark.BREVE -> if (base == 'a') 'ă' else base
        VMark.HORN -> when (base) { 'o' -> 'ơ'; 'u' -> 'ư'; else -> base }
        VMark.STROKE -> if (base == 'd') 'đ' else base
    }

    /** The index of the tone-bearing vowel, or -1 if the syllable has no vowel. */
    private fun nucleus(letters: List<VLetter>): Int {
        val vowels = letters.indices.filter { isVowel(letters[it].base) }.toMutableList()
        // qu- and gi- onsets: the u / i is a glide, not the nucleus, unless it is
        // the syllable's only vowel.
        if (letters.size >= 2 && letters[0].base == 'q' && letters[1].base == 'u' &&
            vowels.any { it > 1 }
        ) vowels.remove(1)
        if (letters.size >= 2 && letters[0].base == 'g' && letters[1].base == 'i' &&
            vowels.any { it > 1 }
        ) vowels.remove(1)
        if (vowels.isEmpty()) return -1
        vowels.lastOrNull { letters[it].mark != VMark.NONE }?.let { return it }
        if (vowels.size == 1) return vowels[0]
        val last = vowels.last()
        val hasCoda = (last + 1..letters.lastIndex).any { !isVowel(letters[it].base) }
        if (hasCoda) return last
        if (vowels.size >= 3) return vowels[vowels.size - 2]
        val a = letters[vowels[0]].base
        val b = letters[vowels[1]].base
        return if ((a == 'o' && b == 'a') || (a == 'o' && b == 'e') || (a == 'u' && b == 'y')) {
            vowels[1]
        } else {
            vowels[0]
        }
    }

    private fun render(letters: List<VLetter>, tone: VTone): String {
        if (letters.isEmpty()) return ""
        val nuc = if (tone == VTone.NONE) -1 else nucleus(letters)
        val sb = StringBuilder()
        letters.forEachIndexed { i, l ->
            var c = precompose(l.base, l.mark)
            if (l.upper) c = c.uppercaseChar()
            sb.append(c)
            if (i == nuc) tone.combining?.let { sb.append(it) }
        }
        return Normalizer.normalize(sb, Normalizer.Form.NFC)
    }

    /** Apply [mark] to the last letter whose base is in [targets]; returns success. */
    private fun applyMark(letters: List<VLetter>, targets: String, mark: VMark): Boolean {
        for (i in letters.indices.reversed()) {
            if (letters[i].base in targets) {
                // A second press of the same mark key cancels it (Telex `aa` then
                // `a`, VNI double 6): toggle back to plain.
                letters[i].mark = if (letters[i].mark == mark) VMark.NONE else mark
                return true
            }
        }
        return false
    }

    fun transduce(raw: String, vni: Boolean): String {
        val letters = ArrayList<VLetter>()
        var tone = VTone.NONE

        fun toggleTone(t: VTone) { tone = if (tone == t) VTone.NONE else t }
        fun hasVowel() = letters.any { isVowel(it.base) }

        for ((idx, ch) in raw.withIndex()) {
            val upper = ch.isUpperCase()
            val lc = ch.lowercaseChar()
            if (vni) {
                when (lc) {
                    '1' -> {
                        if (hasVowel()) toggleTone(VTone.ACUTE)
                        continue
                    }
                    '2' -> {
                        if (hasVowel()) toggleTone(VTone.GRAVE)
                        continue
                    }
                    '3' -> {
                        if (hasVowel()) toggleTone(VTone.HOOK)
                        continue
                    }
                    '4' -> {
                        if (hasVowel()) toggleTone(VTone.TILDE)
                        continue
                    }
                    '5' -> {
                        if (hasVowel()) toggleTone(VTone.DOT)
                        continue
                    }
                    '0' -> { tone = VTone.NONE; continue }
                    '6' -> { if (applyMark(letters, "aeo", VMark.CIRCUMFLEX)) continue }
                    '7' -> { if (applyMark(letters, "ou", VMark.HORN)) continue }
                    '8' -> { if (applyMark(letters, "a", VMark.BREVE)) continue }
                    '9' -> { if (applyMark(letters, "d", VMark.STROKE)) continue }
                }
                letters.add(VLetter(lc, VMark.NONE, upper))
                continue
            }
            // Telex
            when (lc) {
                's', 'f', 'r', 'x', 'j' -> {
                    val t = when (lc) {
                        's' -> VTone.ACUTE; 'f' -> VTone.GRAVE; 'r' -> VTone.HOOK
                        'x' -> VTone.TILDE; else -> VTone.DOT
                    }
                    // Telex tone keys (s, f, r, x, j) MUST be trailing at the end of the syllable
                    val isTrailingTone = (idx until raw.length).all { raw[it].lowercaseChar() in "sfrxjw" }
                    if (hasVowel() && isTrailingTone) {
                        // Repeating the tone key cancels it and types the letter.
                        if (tone == t) { tone = VTone.NONE; letters.add(VLetter(lc, VMark.NONE, upper)) }
                        else tone = t
                    } else {
                        letters.add(VLetter(lc, VMark.NONE, upper))
                    }
                }
                'w' -> {
                    // Check if 'w' is typed after uo cluster (ươ):
                    val uIdx = letters.indexOfLast { it.base == 'u' }
                    val oIdx = letters.indexOfLast { it.base == 'o' }
                    if (uIdx != -1 && oIdx != -1 && oIdx == uIdx + 1) {
                        if (letters[uIdx].mark == VMark.HORN && letters[oIdx].mark == VMark.HORN) {
                            // Repeating 'w' on ươ cancels the horn and appends 'w' -> uow!
                            letters[uIdx].mark = VMark.NONE
                            letters[oIdx].mark = VMark.NONE
                            letters.add(VLetter('w', VMark.NONE, upper))
                        } else {
                            letters[uIdx].mark = VMark.HORN
                            letters[oIdx].mark = VMark.HORN
                        }
                    } else {
                        // Check if previous a, o, or u has mark:
                        val markedVowelIdx = letters.indexOfLast {
                            (it.base == 'a' && it.mark == VMark.BREVE) ||
                            ((it.base == 'o' || it.base == 'u') && it.mark == VMark.HORN)
                        }
                        if (markedVowelIdx != -1) {
                            // Repeating 'w' cancels horn/breve AND appends 'w'!
                            // e.g. "ro" + 'w' -> "rơ"; "rơ" + 'w' -> "row"!
                            // "dră" + 'w' -> "draw", "tư" + 'w' -> "tuw"
                            letters[markedVowelIdx].mark = VMark.NONE
                            letters.add(VLetter('w', VMark.NONE, upper))
                        } else {
                            // Normal first press of 'w': apply horn to 'ou' or breve to 'a':
                            val applied = applyMark(letters, "a", VMark.BREVE) ||
                                applyMark(letters, "ou", VMark.HORN)
                            if (!applied) letters.add(VLetter('w', VMark.NONE, upper))
                        }
                    }
                }
                'a', 'e', 'o' -> {
                    val last = letters.lastOrNull()
                    if (last != null && last.base == lc) {
                        if (last.mark == VMark.CIRCUMFLEX) {
                            last.mark = VMark.NONE
                            letters.add(VLetter(lc, VMark.NONE, upper))
                        } else {
                            last.mark = VMark.CIRCUMFLEX
                        }
                    } else {
                        // Free-form delayed circumflex at end of syllable after coda consonants (e.g. "dong" + 'o' -> "đông", "viet" + 'e' -> "việt")
                        val targetIdx = letters.indexOfLast { it.base == lc }
                        val isTailOfCoda = targetIdx != -1 && (targetIdx + 1..letters.lastIndex).all { !isVowel(letters[it].base) }
                        if (isTailOfCoda && targetIdx != -1) {
                            if (letters[targetIdx].mark == VMark.CIRCUMFLEX) {
                                letters[targetIdx].mark = VMark.NONE
                                letters.add(VLetter(lc, VMark.NONE, upper))
                            } else {
                                letters[targetIdx].mark = VMark.CIRCUMFLEX
                            }
                        } else {
                            letters.add(VLetter(lc, VMark.NONE, upper))
                        }
                    }
                }
                'd' -> {
                    val last = letters.lastOrNull()
                    if (last != null && last.base == 'd') {
                        if (last.mark == VMark.STROKE) {
                            last.mark = VMark.NONE
                            letters.add(VLetter('d', VMark.NONE, upper))
                        } else {
                            last.mark = VMark.STROKE
                        }
                    } else if (letters.isNotEmpty() && letters[0].base == 'd') {
                        if (letters[0].mark == VMark.STROKE) {
                            letters.add(VLetter('d', VMark.NONE, upper))
                        } else {
                            // Free-form delayed 'd' on first onset 'd' (dodong -> đông, dongod -> đông, daud -> đâu)
                            letters[0].mark = VMark.STROKE
                        }
                    } else {
                        letters.add(VLetter('d', VMark.NONE, upper))
                    }
                }
                // Direct tone marks (from Tone Popup or unicode diacritics)
                '\u0301', '́' -> { if (hasVowel()) tone = VTone.ACUTE else letters.add(VLetter('́', VMark.NONE, false)); continue }
                '\u0300', '̀' -> { if (hasVowel()) tone = VTone.GRAVE else letters.add(VLetter('̀', VMark.NONE, false)); continue }
                '\u0309', '̉' -> { if (hasVowel()) tone = VTone.HOOK else letters.add(VLetter('̉', VMark.NONE, false)); continue }
                '\u0303', '̃' -> { if (hasVowel()) tone = VTone.TILDE else letters.add(VLetter('̃', VMark.NONE, false)); continue }
                '\u0323', '̣' -> { if (hasVowel()) tone = VTone.DOT else letters.add(VLetter('̣', VMark.NONE, false)); continue }
                else -> letters.add(VLetter(lc, VMark.NONE, upper))
            }
        }
        return render(letters, tone)
    }
}

/** Vietnamese Telex: letters spell the diacritics (`as`→á, `aw`→ă, `dd`→đ). */
object VietnameseTelexComposer : Composer {
    override val isTransliterating: Boolean get() = true
    override val isVietnameseTelex: Boolean get() = true
    override fun buffersChar(c: Char): Boolean = c in "\u0301\u0300\u0309\u0303\u0323"
    override fun composeBuffer(buffer: String): String = VietnameseEngine.transduce(buffer, vni = false)
}

/** Vietnamese VNI: digits spell the diacritics (`a8`→ă, `a1`→á, `d9`→đ). */
object VietnameseVniComposer : Composer {
    override val isTransliterating: Boolean get() = true
    override val bufferDigits: Boolean get() = true
    override fun buffersChar(c: Char): Boolean = c in "\u0301\u0300\u0309\u0303\u0323"
    override fun composeBuffer(buffer: String): String = VietnameseEngine.transduce(buffer, vni = true)
}
