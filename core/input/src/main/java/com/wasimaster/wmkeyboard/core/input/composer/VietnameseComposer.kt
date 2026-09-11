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

    /** The index of the tone-bearing vowel, or -1 if the syllable has no vowel. Zero heap allocations. */
    private fun nucleus(letters: List<VLetter>): Int {
        if (letters.isEmpty()) return -1
        var vCount = 0
        var v0 = -1
        var v1 = -1
        var v2 = -1
        var v3 = -1
        var v4 = -1
        var markedIdx = -1

        for (i in letters.indices) {
            if (isVowel(letters[i].base)) {
                when (vCount) {
                    0 -> v0 = i
                    1 -> v1 = i
                    2 -> v2 = i
                    3 -> v3 = i
                    4 -> v4 = i
                }
                vCount++
                if (letters[i].mark != VMark.NONE) {
                    markedIdx = i
                }
            }
        }
        if (vCount == 0) return -1

        // qu- and gi- onsets: the u / i is a glide, not the nucleus, unless it is
        // the syllable's only vowel.
        if (letters.size >= 2 && vCount > 1) {
            val b0 = letters[0].base
            val b1 = letters[1].base
            if ((b0 == 'q' && b1 == 'u' && v0 == 1) || (b0 == 'g' && b1 == 'i' && v0 == 1)) {
                v0 = v1
                v1 = v2
                v2 = v3
                v3 = v4
                vCount--
                if (markedIdx == 1) markedIdx = -1
            }
        }

        if (vCount == 0) return -1
        if (markedIdx >= 0) return markedIdx
        if (vCount == 1) return v0

        val last = when (vCount) {
            2 -> v1
            3 -> v2
            4 -> v3
            else -> v4
        }

        var hasCoda = false
        for (i in (last + 1)..letters.lastIndex) {
            if (!isVowel(letters[i].base)) {
                hasCoda = true
                break
            }
        }
        if (hasCoda) return last

        if (vCount >= 3) {
            return when (vCount) {
                3 -> v1
                4 -> v2
                else -> v3
            }
        }

        val a = letters[v0].base
        val b = letters[v1].base
        return if ((a == 'o' && b == 'a') || (a == 'o' && b == 'e') || (a == 'u' && b == 'y')) {
            v1
        } else {
            v0
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

    private fun handleFlickMark(letters: ArrayList<VLetter>, base: Char, mark: VMark, upper: Boolean) {
        val last = letters.lastOrNull()
        if (last != null && last.base == base) {
            if (last.mark == mark) {
                last.mark = VMark.NONE
            } else {
                last.mark = mark
            }
        } else {
            letters.add(VLetter(base, mark, upper))
        }
    }

    fun transduce(raw: String, vni: Boolean, pureFlick: Boolean = false): String {
        val letters = ArrayList<VLetter>()
        var tone = VTone.NONE

        fun toggleTone(t: VTone) { tone = if (tone == t) VTone.NONE else t }
        fun hasVowel() = letters.any { isVowel(it.base) }
        fun hasValidVowelCluster(): Boolean {
            var first = -1
            var last = -1
            var count = 0
            for (i in letters.indices) {
                if (isVowel(letters[i].base)) {
                    if (first < 0) first = i
                    last = i
                    count++
                }
            }
            return count > 0 && last - first + 1 == count
        }

        for ((idx, ch) in raw.withIndex()) {
            val upper = ch.isUpperCase()
            val lc = ch.lowercaseChar()

            // Direct tone marks (from Flick gesture, Tone Popup or unicode diacritics)
            if (lc in "\u0301́\u0300̀\u0309̉\u0303̃\u0323̣") {
                when (lc) {
                    '\u0301', '́' -> if (hasVowel()) toggleTone(VTone.ACUTE) else letters.add(VLetter('́', VMark.NONE, false))
                    '\u0300', '̀' -> if (hasVowel()) toggleTone(VTone.GRAVE) else letters.add(VLetter('̀', VMark.NONE, false))
                    '\u0309', '̉' -> if (hasVowel()) toggleTone(VTone.HOOK) else letters.add(VLetter('̉', VMark.NONE, false))
                    '\u0303', '̃' -> if (hasVowel()) toggleTone(VTone.TILDE) else letters.add(VLetter('̃', VMark.NONE, false))
                    '\u0323', '̣' -> if (hasVowel()) toggleTone(VTone.DOT) else letters.add(VLetter('̣', VMark.NONE, false))
                }
                continue
            }

            // Precomposed vowel marks and đ from flick gestures
            when (lc) {
                'ă' -> { handleFlickMark(letters, 'a', VMark.BREVE, upper); continue }
                'â' -> { handleFlickMark(letters, 'a', VMark.CIRCUMFLEX, upper); continue }
                'ê' -> { handleFlickMark(letters, 'e', VMark.CIRCUMFLEX, upper); continue }
                'ô' -> { handleFlickMark(letters, 'o', VMark.CIRCUMFLEX, upper); continue }
                'ơ' -> { handleFlickMark(letters, 'o', VMark.HORN, upper); continue }
                'ư' -> { handleFlickMark(letters, 'u', VMark.HORN, upper); continue }
                'đ' -> { handleFlickMark(letters, 'd', VMark.STROKE, upper); continue }
            }
            if (vni) {
                when (lc) {
                    '1' -> {
                        if (hasValidVowelCluster()) toggleTone(VTone.ACUTE)
                        continue
                    }
                    '2' -> {
                        if (hasValidVowelCluster()) toggleTone(VTone.GRAVE)
                        continue
                    }
                    '3' -> {
                        if (hasValidVowelCluster()) toggleTone(VTone.HOOK)
                        continue
                    }
                    '4' -> {
                        if (hasValidVowelCluster()) toggleTone(VTone.TILDE)
                        continue
                    }
                    '5' -> {
                        if (hasValidVowelCluster()) toggleTone(VTone.DOT)
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
            if (pureFlick) {
                // Pure flick mode: tap NEVER mutates or transliterates. 100% plain Latin text.
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
                    if (hasValidVowelCluster() && isTrailingTone) {
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
                        // Repeating 'w' on existing ươ toggles it back to uo and appends 'w'
                        if (letters[uIdx].mark == VMark.HORN && letters[oIdx].mark == VMark.HORN) {
                            letters[uIdx].mark = VMark.NONE
                            letters[oIdx].mark = VMark.NONE
                            letters.add(VLetter('w', VMark.NONE, upper))
                        } else {
                            letters[uIdx].mark = VMark.HORN
                            letters[oIdx].mark = VMark.HORN
                        }
                    } else {
                        // Check if repeating 'w' on an already marked horn/breve vowel:
                        val markedVowelIdx = letters.indexOfLast {
                            (it.base == 'a' && it.mark == VMark.BREVE) ||
                            ((it.base == 'o' || it.base == 'u') && it.mark == VMark.HORN)
                        }
                        if (markedVowelIdx != -1) {
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
                        letters.add(VLetter(lc, VMark.NONE, upper))
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
                    } else {
                        letters.add(VLetter('d', VMark.NONE, upper))
                    }
                }
                else -> letters.add(VLetter(lc, VMark.NONE, upper))
            }
        }
        return render(letters, tone)
    }
}

/** Vietnamese Telex: letters spell the diacritics (`as`→á, `aw`→ă, `dd`→đ). */
object VietnameseTelexComposer : Composer {
    var pureFlickMode: Boolean = false
    override val isTransliterating: Boolean get() = true
    override val isVietnameseTelex: Boolean get() = true
    override fun buffersChar(c: Char): Boolean = c in "\u0301\u0300\u0309\u0303\u0323"
    override fun isPlausibleWord(word: String): Boolean = VietnameseOrthography.isSyllable(word)
    override fun composeBuffer(buffer: String): String = VietnameseEngine.transduce(buffer, vni = false, pureFlick = pureFlickMode)
}

/** Vietnamese VNI: digits spell the diacritics (`a8`→ă, `a1`→á, `d9`→đ). */
object VietnameseVniComposer : Composer {
    override val isTransliterating: Boolean get() = true
    override val bufferDigits: Boolean get() = true
    override fun buffersChar(c: Char): Boolean = c in "\u0301\u0300\u0309\u0303\u0323"
    override fun isPlausibleWord(word: String): Boolean = VietnameseOrthography.isSyllable(word)
    override fun composeBuffer(buffer: String): String = VietnameseEngine.transduce(buffer, vni = true)
}
