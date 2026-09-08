@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import org.junit.Test

class OctopusRepro {
    @Test
    fun repro() {
        val trie = Trie().apply {
            insert("just", 100); insert("job", 90); insert("je", 40)
            insert("jhamela", 20); insert("jodi", 15); insert("in", 200); insert("is", 190)
            insert("download", 80); insert("downloaded", 70); insert("downloading", 60)
        }
        val engine = SuggestionEngine(trie, BengaliPhoneticIndex(emptyList()), UserLexicon(null))
        for (typed in listOf("i", "J", "Download")) {
            val pool = engine.suggest(typed, previousWord = null, limit = 24)
            val floated = engine.octopusWords(
                composing = typed, previousWord = null, limit = 26, pool = pool, keyOf = { it },
            )
            println("typed='$typed' pool=$pool")
            for (w in floated) {
                println("   key=${w.keyCodePoint.toChar()} word='${w.word}' typedChars=${w.typedChars} kind=${w.kind}")
            }
        }
    }
}
