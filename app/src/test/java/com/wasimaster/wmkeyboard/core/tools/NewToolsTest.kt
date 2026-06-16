package com.wasimaster.wmkeyboard.core.tools

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcEngineTest {

    private fun eval(expr: String, degrees: Boolean = true) = CalcEngine.evaluate(expr, degrees)

    @Test
    fun basicArithmetic() {
        assertEquals(7.0, eval("1+2*3"), 1e-9)
        assertEquals(9.0, eval("(1+2)*3"), 1e-9)
        assertEquals(2.5, eval("5/2"), 1e-9)
        assertEquals(-4.0, eval("-4"), 1e-9)
        assertEquals(1.0, eval("5 mod 2"), 1e-9)
        assertEquals(8.0, eval("2^3"), 1e-9)
        // Right-associative power.
        assertEquals(512.0, eval("2^3^2"), 1e-9)
    }

    @Test
    fun unicodeOperators() {
        assertEquals(6.0, eval("2×3"), 1e-9)
        assertEquals(2.0, eval("6÷3"), 1e-9)
        assertEquals(1.0, eval("3−2"), 1e-9)
    }

    @Test
    fun functionsAndConstants() {
        assertEquals(1.0, eval("sin(90)"), 1e-9)
        assertEquals(0.0, eval("sin(pi)", degrees = false), 1e-9)
        assertEquals(2.0, eval("sqrt(4)"), 1e-9)
        assertEquals(2.0, eval("√4"), 1e-9)
        assertEquals(3.0, eval("log(1000)"), 1e-9)
        assertEquals(1.0, eval("ln(e)", degrees = false), 1e-9)
        assertEquals(120.0, eval("abs(-120)"), 1e-9)
    }

    @Test
    fun implicitMultiplication() {
        assertEquals(2 * Math.PI, eval("2π"), 1e-9)
        assertEquals(14.0, eval("2(3+4)"), 1e-9)
        assertEquals(6.0, eval("(1+2)(2)"), 1e-9)
    }

    @Test
    fun percent() {
        assertEquals(0.5, eval("50%"), 1e-9)
        assertEquals(1.0, eval("5%2"), 1e-9) // % followed by value = modulo
    }

    @Test
    fun errorsAreReported() {
        assertFails("1/0")
        assertFails("2+")
        assertFails("foo(2)")
        assertFails("(1+2")
        assertFails("sqrt(-1)")
    }

    private fun assertFails(expr: String) {
        try {
            eval(expr)
            throw AssertionError("Expected failure for “$expr”")
        } catch (expected: CalcEngine.CalcException) {
            // expected
        }
    }

    @Test
    fun formatting() {
        assertEquals("3.14159265", CalcEngine.format(Math.PI, 8))
        assertEquals("2", CalcEngine.format(2.0, 8))
        assertEquals("0.5", CalcEngine.format(0.5, 8))
        assertTrue(CalcEngine.format(1e20, 6).contains("e"))
    }
}

class UnitConvertTest {

    private fun unit(category: String, symbol: String): UnitConvert.ConvUnit =
        UnitConvert.categories.first { it.name == category }.units.first { it.symbol == symbol }

    @Test
    fun length() {
        assertEquals(
            1609.344,
            UnitConvert.convert(1.0, unit("Length", "mi"), unit("Length", "m")),
            1e-6,
        )
        assertEquals(
            2.54,
            UnitConvert.convert(1.0, unit("Length", "in"), unit("Length", "cm")),
            1e-9,
        )
    }

    @Test
    fun temperature() {
        assertEquals(
            212.0,
            UnitConvert.convert(100.0, unit("Temperature", "°C"), unit("Temperature", "°F")),
            1e-9,
        )
        assertEquals(
            273.15,
            UnitConvert.convert(0.0, unit("Temperature", "°C"), unit("Temperature", "K")),
            1e-9,
        )
        assertEquals(
            -40.0,
            UnitConvert.convert(-40.0, unit("Temperature", "°F"), unit("Temperature", "°C")),
            1e-9,
        )
    }

    @Test
    fun fuelEconomyReciprocal() {
        // 5 L/100km ≈ 47.04 mpg US
        assertEquals(
            47.04,
            UnitConvert.convert(5.0, unit("Fuel economy", "L/100km"), unit("Fuel economy", "mpg")),
            0.01,
        )
        // Round-trips through a reciprocal unit.
        val mpg = unit("Fuel economy", "mpg")
        val l100 = unit("Fuel economy", "L/100km")
        assertEquals(30.0, UnitConvert.convert(UnitConvert.convert(30.0, mpg, l100), l100, mpg), 1e-9)
    }

    @Test
    fun data() {
        assertEquals(
            1024.0,
            UnitConvert.convert(1.0, unit("Data", "KiB"), unit("Data", "B")),
            1e-9,
        )
        assertEquals(
            8.0,
            UnitConvert.convert(1.0, unit("Data", "B"), unit("Data", "bit")),
            1e-9,
        )
    }
}

class PasswordGenTest {

    private val random = SecureRandom()

    @Test
    fun passwordRespectsSpec() {
        val spec = PasswordGen.PasswordSpec(
            length = 20, upper = true, digits = true, symbols = true,
        )
        repeat(50) {
            val password = PasswordGen.password(spec, random)
            assertEquals(20, password.length)
            assertTrue(password.any { it.isUpperCase() })
            assertTrue(password.any { it.isDigit() })
            assertTrue(password.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun passwordDisabledClassesAbsent() {
        val spec = PasswordGen.PasswordSpec(
            length = 32, upper = false, digits = false, symbols = false,
        )
        repeat(20) {
            val password = PasswordGen.password(spec, random)
            assertTrue(password.all { it in 'a'..'z' })
        }
    }

    @Test
    fun excludeAmbiguousFilters() {
        val spec = PasswordGen.PasswordSpec(
            length = 64, upper = true, digits = true, symbols = false, excludeAmbiguous = true,
        )
        repeat(20) {
            val password = PasswordGen.password(spec, random)
            assertTrue(password.none { it in "Il1O0o5S8B" })
        }
    }

    @Test
    fun passphraseShape() {
        val words = listOf("alpha", "bravo", "charlie", "delta", "echo")
        val phrase = PasswordGen.passphrase(
            words,
            PasswordGen.PassphraseSpec(words = 4, separator = "-", capitalize = true),
            random,
        )
        val parts = phrase.split("-")
        assertEquals(4, parts.size)
        assertTrue(parts.all { it.first().isUpperCase() })
    }

    @Test
    fun wordlistFilter() {
        val words = PasswordGen.buildWordlist(
            sequenceOf("ok", "good", "Capital", "hyphen-ed", "sunshine", "waterfalls", "good"),
        )
        assertEquals(listOf("good", "sunshine"), words)
    }
}

class CurrencyClientTest {

    @Test
    fun parsesErApi() {
        val rates = CurrencyClient.parseErApi(
            """{"result":"success","rates":{"USD":1,"EUR":0.9,"BDT":120.5}}""",
        )
        assertEquals(0.9, rates.rates["EUR"]!!, 1e-9)
        assertEquals(1.0, rates.rates["USD"]!!, 1e-9)
        // Cross conversion EUR -> BDT.
        assertEquals(
            120.5 / 0.9,
            CurrencyClient.convert(1.0, "EUR", "BDT", rates)!!,
            1e-9,
        )
    }

    @Test
    fun parsesFrankfurter() {
        val rates = CurrencyClient.parseFrankfurter(
            """{"amount":1.0,"base":"USD","rates":{"EUR":0.92,"GBP":0.79}}""",
        )
        assertEquals(0.79, rates.rates["GBP"]!!, 1e-9)
        assertNotNull(rates.rates["USD"])
    }

    @Test
    fun unknownCodeReturnsNull() {
        val rates = CurrencyClient.Rates("USD", mapOf("USD" to 1.0))
        assertNull(CurrencyClient.convert(1.0, "USD", "XXX", rates))
    }
}

class WikipediaClientTest {

    @Test
    fun parsesSearch() {
        val results = WikipediaClient.parseSearch(
            """{"query":{"search":[
                {"title":"Kotlin (programming language)","snippet":"<span class=\"searchmatch\">Kotlin</span> is a language"},
                {"title":"Kotlin River","snippet":"river in Russia"}
            ]}}""",
        )
        assertEquals(2, results.size)
        assertEquals("Kotlin (programming language)", results[0].title)
        assertEquals("Kotlin is a language", results[0].description)
    }

    @Test
    fun parsesSummary() {
        val summary = WikipediaClient.parseSummary(
            """{"title":"Dhaka","description":"Capital of Bangladesh",
                "extract":"Dhaka is the capital city.",
                "content_urls":{"desktop":{"page":"https://en.wikipedia.org/wiki/Dhaka"}}}""",
            fallbackUrl = "fallback",
        )
        assertEquals("Dhaka", summary.title)
        assertEquals("https://en.wikipedia.org/wiki/Dhaka", summary.url)
    }

    @Test
    fun parsesLinksAndFullText() {
        val links = WikipediaClient.parseLinks(
            """{"query":{"pages":{"123":{"links":[{"ns":0,"title":"Bangladesh"},{"ns":0,"title":"Bengal"}]}}}}""",
        )
        assertEquals(listOf("Bangladesh", "Bengal"), links)
        val text = WikipediaClient.parseFullText(
            """{"query":{"pages":{"123":{"extract":"Full text here."}}}}""",
        )
        assertEquals("Full text here.", text)
    }

    @Test
    fun articleUrlEncodes() {
        assertEquals(
            "https://en.wikipedia.org/wiki/Ada_Lovelace",
            WikipediaClient.articleUrl("Ada Lovelace", "en"),
        )
    }
}

class AiClientParseTest {

    @Test
    fun parsesAnthropic() {
        assertEquals(
            "Hello!",
            AiClient.parseAnthropic(
                """{"content":[{"type":"text","text":"Hello!"}],"model":"m"}""",
            ),
        )
    }

    @Test
    fun parsesOpenAi() {
        assertEquals(
            "Result",
            AiClient.parseOpenAi(
                """{"choices":[{"message":{"role":"assistant","content":"Result"}}]}""",
            ),
        )
    }

    @Test
    fun parsesGemini() {
        assertEquals(
            "AB",
            AiClient.parseGemini(
                """{"candidates":[{"content":{"parts":[{"text":"A"},{"text":"B"}]}}]}""",
            ),
        )
    }

    @Test
    fun parsesOllama() {
        assertEquals(
            "Hi",
            AiClient.parseOllama("""{"message":{"role":"assistant","content":"Hi"}}"""),
        )
    }
}

class QrToolTest {

    @Test
    fun symbolCatalogHasNoDuplicatesWithinCategory() {
        for (category in SymbolCatalog.categories) {
            assertEquals(
                "Duplicates in ${category.name}",
                category.symbols.size,
                category.symbols.distinct().size,
            )
        }
    }
}
