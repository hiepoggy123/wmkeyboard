package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryClientTest {

    // Trimmed real response shape from api.dictionaryapi.dev for "quixotic".
    private val quixotic = """
        [
          {
            "word": "quixotic",
            "phonetic": "/kwɪkˈsɒtɪk/",
            "phonetics": [
              { "text": "/kwɪkˈsɒtɪk/", "audio": "" },
              { "text": "/kwɪkˈsɑtɪk/", "audio": "https://api.dictionaryapi.dev/media/pronunciations/en/quixotic-us.mp3" }
            ],
            "meanings": [
              {
                "partOfSpeech": "adjective",
                "definitions": [
                  {
                    "definition": "Possessing or acting with the desire to do noble and romantic deeds, without thought of realism and practicality; exceedingly idealistic.",
                    "synonyms": [],
                    "antonyms": [],
                    "example": "His quixotic quest to become a famous poet"
                  },
                  {
                    "definition": "Impulsive.",
                    "synonyms": ["impetuous"],
                    "antonyms": []
                  }
                ],
                "synonyms": ["idealistic", "impractical", "romantic"],
                "antonyms": ["realistic"]
              }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `parses word, phonetic and audio`() {
        val entries = DictionaryClient.parse(quixotic)
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("quixotic", entry.word)
        assertEquals("/kwɪkˈsɒtɪk/", entry.phonetic)
        assertEquals(
            "https://api.dictionaryapi.dev/media/pronunciations/en/quixotic-us.mp3",
            entry.audioUrl,
        )
    }

    @Test
    fun `parses meanings with examples and synonyms`() {
        val meaning = DictionaryClient.parse(quixotic)[0].meanings[0]
        assertEquals("adjective", meaning.partOfSpeech)
        assertEquals(2, meaning.definitions.size)
        assertEquals("His quixotic quest to become a famous poet", meaning.definitions[0].example)
        assertNull(meaning.definitions[1].example)
        assertEquals(listOf("impetuous"), meaning.definitions[1].synonyms)
        assertEquals(listOf("idealistic", "impractical", "romantic"), meaning.synonyms)
        assertEquals(listOf("realistic"), meaning.antonyms)
    }

    @Test
    fun `tolerates missing optional fields`() {
        val minimal = """[{"word":"cat","meanings":[{"partOfSpeech":"noun",
            "definitions":[{"definition":"A small felid."}]}]}]""".trimIndent()
        val entries = DictionaryClient.parse(minimal)
        assertEquals(1, entries.size)
        assertEquals("", entries[0].phonetic)
        assertNull(entries[0].audioUrl)
        assertEquals("A small felid.", entries[0].meanings[0].definitions[0].text)
    }

    @Test
    fun `drops entries without usable definitions`() {
        val empty = """[{"word":"x","meanings":[]},"notAnObject"]"""
        assertTrue(DictionaryClient.parse(empty).isEmpty())
    }

    @Test
    fun `non-array body parses to empty`() {
        assertTrue(DictionaryClient.parse("""{"title":"No Definitions Found"}""").isEmpty())
    }
}
