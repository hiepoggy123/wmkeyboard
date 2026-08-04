// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.prediction.eval

import com.wasimaster.wmkeyboard.core.prediction.CandidateReranker
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The honest gate on ever building a real reranker model: an oracle that
 * always moves the intended word to the front (when it is in the rerank pool
 * at all) bounds what ANY model could gain over the frequency ranking. If
 * this ceiling is small, a model is not worth its bytes; the number printed
 * here is the one that decision gets made on.
 */
class RerankOracleTest {

    @Test
    fun oracleCeilingIsMeasuredAndTheSeamWorks() {
        val entries = run {
            val candidates = listOf(
                File("dictionaries-src/en.txt"),
                File("app/dictionaries-src/en.txt"),
            )
            candidates.firstOrNull { it.exists() }?.inputStream()
                ?.use { DictionaryLoader.loadEntries(it) }
                ?: error("en.txt not found")
        }
        val engine = SuggestionEngine(
            PackedTrie.of(entries), BengaliPhoneticIndex(emptyList()), UserLexicon(null),
        )
        val cases = TypoCorpus(42L).generate(entries, 2000)

        var baseTop1 = 0
        var oracleTop1 = 0
        var intended = ""
        engine.reranker = CandidateReranker { _, candidates ->
            val hit = candidates.firstOrNull { it.equals(intended, ignoreCase = true) }
            if (hit == null) null else listOf(hit) + candidates.filterNot { it == hit }
        }
        for (case in cases) {
            intended = case.intended
            val base = engine.suggest(case.typed, previousWord = case.previous)
            if (base.firstOrNull()?.equals(case.intended, ignoreCase = true) == true) baseTop1++
            val reranked = engine.suggest(
                case.typed, previousWord = case.previous, allowRerank = true,
            )
            if (reranked.firstOrNull()?.equals(case.intended, ignoreCase = true) == true) {
                oracleTop1++
            }
        }

        val base = baseTop1 / cases.size.toDouble()
        val oracle = oracleTop1 / cases.size.toDouble()
        println(
            String.format(
                Locale.ROOT,
                "rerank oracle: baseTop1=%.4f oracleTop1=%.4f ceiling=+%.4f",
                base, oracle, oracle - base,
            )
        )
        // The seam itself must work: the oracle can only help, never hurt.
        assertTrue("oracle regressed top1", oracleTop1 >= baseTop1)
        // And the pool is deep enough that the oracle finds real headroom —
        // if this ever fails, the pool or the ranking changed materially.
        assertTrue("oracle found no headroom at all", oracleTop1 > baseTop1)
    }
}
