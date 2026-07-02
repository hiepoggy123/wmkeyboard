package com.wasimaster.wmkeyboard.core.input.composer

import com.wasimaster.wmkeyboard.core.transliteration.AvroPhonetic
import com.wasimaster.wmkeyboard.core.transliteration.BengaliGraphemes

/**
 * Avro: roman letters transliterated to Bengali as the buffer grows, committed
 * as a unit. Wraps the existing [AvroPhonetic] so the transliteration rules are
 * unchanged — the composer only puts it behind the shared interface.
 *
 * Committed output is Bengali, so backspace over already-committed text still
 * removes a whole conjunct cluster (the roman *buffer* is edited char by char by
 * the service, ahead of commit).
 */
object BengaliTransliterateComposer : Composer {

    override val isTransliterating: Boolean get() = true

    override val isBengaliPhonetic: Boolean get() = true

    override fun composeBuffer(buffer: String): String = AvroPhonetic.transliterate(buffer)

    override fun deleteLength(before: CharSequence): Int =
        BengaliGraphemes.clusterDeleteLength(before)
}
