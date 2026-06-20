package com.wasimaster.wmkeyboard.spell

import android.content.Context
import android.service.textservice.SpellCheckerService
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import com.wasimaster.wmkeyboard.core.grammar.GrammarChecker
import com.wasimaster.wmkeyboard.core.grammar.GrammarFix
import com.wasimaster.wmkeyboard.core.grammar.GrammarLint
import com.wasimaster.wmkeyboard.core.settings.GrammarDialect
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Exposes the bundled Harper engine as a system-wide spell checker, so the
 * red underlines and correction menus in *other* apps come from the same
 * checker as the keyboard's own grammar tool.
 *
 * Full builds only: lite ships no `libharper_jni.so` at all, so advertising
 * a spell checker there would put an entry in the system settings that
 * could never find anything. The service and its manifest entry live in
 * `src/full` for that reason.
 *
 * Harper is a sentence-level checker — its lints carry spans and can cover
 * several words ("their" → "they're") — so the real work happens in
 * [Session.onGetSentenceSuggestionsMultiple], which is the only spell
 * checker API that can report an offset and length per problem. The older
 * per-word [Session.onGetSuggestions] is implemented too, because the
 * framework still falls back to it on some paths, but it can only ever flag
 * the single word it is handed.
 */
/**
 * A Harper fix as the whole-span replacement the spell checker UI needs.
 *
 * The framework can only swap a flagged span for a string, so an insertion
 * has to become "the span plus the insertion". Deletions return null: the
 * correction menu would show an empty row, which cannot be read or chosen.
 */
internal fun spellReplacementFor(span: String, fix: GrammarFix): String? = when (fix.kind) {
    "replace" -> fix.text
    "insertAfter" -> fix.text?.let { span + it }
    else -> null
}

class HarperSpellCheckerService : SpellCheckerService() {

    override fun createSession(): Session = HarperSession(applicationContext)

    private class HarperSession(private val context: Context) : Session() {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Mirrors the user's grammar dialect. Read through a collected flow
         * rather than per-request: the framework calls into a session on a
         * binder thread and expects a prompt answer, and a DataStore read on
         * every keystroke-driven check would dominate the cost of the check
         * itself.
         */
        @Volatile
        private var dialectOrdinal: Int = GrammarDialect.AMERICAN.ordinal

        override fun onCreate() {
            val repository = SettingsRepository(context)
            scope.launch {
                repository.settings
                    .map { it.grammarDialect.ordinal }
                    .collect { ordinal ->
                        val changed = ordinal != dialectOrdinal
                        dialectOrdinal = ordinal
                        // Building Harper's rule set takes ~100 ms; do it now
                        // so the first underline in an app is not late.
                        if (changed) GrammarChecker.warmUp(ordinal)
                    }
            }
        }

        override fun onClose() {
            scope.cancel()
        }

        /**
         * Sentence-level checking: one [SentenceSuggestionsInfo] per input,
         * each carrying a problem's offset and length so the host app can
         * underline exactly the span Harper flagged.
         */
        override fun onGetSentenceSuggestionsMultiple(
            textInfos: Array<out TextInfo>?,
            suggestionsLimit: Int,
        ): Array<SentenceSuggestionsInfo> {
            val inputs = textInfos ?: return emptyArray()
            if (!GrammarChecker.available) return inputs.map { emptySentence() }.toTypedArray()
            return inputs
                .map { info -> sentenceSuggestions(info, suggestionsLimit) }
                .toTypedArray()
        }

        /**
         * Per-word checking. The framework hands over one word with no
         * surrounding sentence, so only lints spanning the whole word are
         * meaningful — a grammar rule about the words around it has nothing
         * to work with here.
         */
        override fun onGetSuggestions(
            textInfo: TextInfo?,
            suggestionsLimit: Int,
        ): SuggestionsInfo {
            val word = textInfo?.text.orEmpty()
            if (!GrammarChecker.available || word.isBlank()) return noProblem()
            val lint = lint(word).firstOrNull { it.start == 0 && it.end == word.length }
                ?: return noProblem()
            return suggestionsInfo(word, lint, suggestionsLimit)
        }

        private fun sentenceSuggestions(
            info: TextInfo,
            suggestionsLimit: Int,
        ): SentenceSuggestionsInfo {
            val text = info.text.orEmpty()
            if (text.isBlank()) return emptySentence()
            val lints = lint(text).filter { it.end > it.start }
            if (lints.isEmpty()) return emptySentence()

            val infos = ArrayList<SuggestionsInfo>(lints.size)
            val offsets = ArrayList<Int>(lints.size)
            val lengths = ArrayList<Int>(lints.size)
            for (lint in lints) {
                val span = text.substring(lint.start, lint.end)
                infos.add(suggestionsInfo(span, lint, suggestionsLimit))
                offsets.add(lint.start)
                lengths.add(lint.end - lint.start)
            }
            return SentenceSuggestionsInfo(
                infos.toTypedArray(),
                offsets.toIntArray(),
                lengths.toIntArray(),
            )
        }

        private fun lint(text: String): List<GrammarLint> =
            // The framework's call is synchronous and already off the main
            // thread; GrammarChecker hops to its own single native thread.
            runBlocking { GrammarChecker.check(text, dialectOrdinal) }

        private fun suggestionsInfo(
            span: String,
            lint: GrammarLint,
            suggestionsLimit: Int,
        ): SuggestionsInfo {
            val replacements = lint.suggestions
                .mapNotNull { spellReplacementFor(span, it) }
                .filter { it.isNotBlank() && it != span }
                .distinct()
                .take(suggestionsLimit.coerceAtLeast(1))
            var flags = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
            if (replacements.isNotEmpty()) {
                flags = flags or SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS
            }
            return SuggestionsInfo(flags, replacements.toTypedArray())
        }

        private fun noProblem() =
            SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())

        private fun emptySentence() =
            SentenceSuggestionsInfo(emptyArray(), IntArray(0), IntArray(0))
    }
}
