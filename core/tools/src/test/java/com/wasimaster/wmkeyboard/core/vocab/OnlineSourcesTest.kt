package com.wasimaster.wmkeyboard.core.vocab

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineSourcesTest {

    private val kaikkiLine = """
        {"word": "abhor", "pos": "verb", "lang_code": "en",
         "forms": [{"form": "abhors", "tags": ["present"]}, {"form": "en-verb", "tags": ["table-tags"]}],
         "sounds": [{"tags": ["Received-Pronunciation"], "ipa": "/əbˈhɔː/"}, {"tags": ["US"], "ipa": "/əbˈhɔɹ/", "mp3_url": "https://x/us.mp3"}, {"rhymes": "-ɔː(ɹ)"}],
         "senses": [{"glosses": ["To regard as horrifying."], "examples": [{"text": "I abhor traffic.", "type": "example"}, {"text": "abhorre that which is euill", "ref": "1611, King James Version:", "type": "quotation"}], "synonyms": [{"word": "detest"}], "tags": ["transitive", "not-kept"]}],
         "synonyms": [{"word": "hate"}], "antonyms": [{"word": "love"}], "derived": [{"word": "abhorrable"}],
         "etymology_text": "First attested in 1449, from Middle English abhorren.",
         "translations": [{"code": "bn", "word": "ঘৃণা করা"}, {"code": "hy", "word": "ատել", "roman": "atel"}, {"code": "es", "word": "odiar"}]}
    """.trimIndent().replace("\n", " ")

    @Test
    fun `kaikki folds into a pack-shaped record`() {
        val word = KaikkiClient.parse(kaikkiLine, "abhor", translationCodes = listOf("bn", "hy"))!!
        assertEquals(listOf("verb"), word.pos)
        assertEquals("/əbˈhɔɹ/", word.ipaFor(VocabAccent.US))
        assertEquals("/əbˈhɔː/", word.ipaFor(VocabAccent.UK))
        assertEquals("https://x/us.mp3", word.audioFor(VocabAccent.UK))
        assertEquals("To regard as horrifying.", word.definition)
        assertEquals("I abhor traffic.", word.senses[0].example)
        assertEquals("1611, King James Version", word.senses[0].quotations.single().ref)
        assertEquals(listOf("transitive"), word.senses[0].tags)
        assertEquals(listOf("detest", "hate"), word.synonyms)
        assertEquals(listOf("love"), word.antonyms)
        assertEquals(listOf("abhorrable"), word.family?.derived)
        assertEquals(listOf("abhors"), word.forms)
        assertEquals("-ɔː(ɹ)", word.rhymes)
        assertEquals("1449", word.attested)
        assertEquals(setOf("bn", "hy"), word.translations.keys)
        assertEquals(listOf("atel"), word.translations["hy"]?.r)
        assertTrue(word.translations["bn"]?.r.orEmpty().isEmpty())
        assertNull(KaikkiClient.parse("", "abhor"))
        assertEquals("https://kaikki.org/dictionary/English/meaning/a/ab/abhor.jsonl", KaikkiClient.url("Abhor"))
    }

    @Test
    fun `wiktionary rest strips its html`() {
        val body = """{"en": [{"partOfSpeech": "Verb", "language": "English", "definitions": [
            {"definition": "To <a href=\"/wiki/regard\">regard</a> as horrifying &amp; detestable", "examples": ["I <b>abhor</b> traffic."]},
            {"definition": ""}]}]}"""
        val word = WiktionaryRestClient.parse(body, "abhor")!!
        assertEquals(listOf("verb"), word.pos)
        assertEquals(1, word.senses.size)
        assertEquals("To regard as horrifying & detestable", word.definition)
        assertEquals("I abhor traffic.", word.senses[0].example)
        assertNull(WiktionaryRestClient.parse("{}", "abhor"))
    }

    @Test
    fun `sources are asked in turn and a dead one is skipped`() = runBlocking {
        val index = VocabIndex.EMPTY
        val found = VocabWord("abhor", senses = listOf(VocabSense(definition = "x")))
        val down = VocabAutofill.Source { _, _ -> throw java.io.IOException("down") }
        val nothing = VocabAutofill.Source { _, _ -> null }
        val hit = VocabAutofill.Source { _, _ -> found }
        val result = VocabAutofill.resolve(index, "abhor", allowOnline = true, sources = listOf(down, hit))
        assertTrue(result is VocabAutofill.Result.Found && result.fromOnline)
        assertEquals(
            VocabAutofill.Result.NotFound,
            VocabAutofill.resolve(index, "abhor", allowOnline = true, sources = listOf(down, nothing)),
        )
        assertEquals(VocabAutofill.Result.Failed, VocabAutofill.resolve(index, "abhor", allowOnline = true, sources = listOf(down, down)))
    }
}
