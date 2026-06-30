package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSuggestTest {

    private val rates = CurrencyClient.Rates(
        base = "USD",
        rates = mapOf("USD" to 1.0, "BDT" to 120.0, "EUR" to 0.9, "GBP" to 0.8, "INR" to 83.0),
    )

    private val ctx = SmartSuggest.Context(
        precision = 4,
        rates = rates,
        currencyFrom = "USD",
        currencyTo = "BDT",
        currencyDecimals = 2,
        enabledTools = ToolbarTool.entries,
    )

    private fun hit(text: String, context: SmartSuggest.Context = ctx) =
        SmartSuggest.detect(text, context)

    // ---- currency ----

    @Test
    fun currencyReadsEveryWayAnAmountIsWritten() {
        for (typed in listOf("150 usd", "150usd", "150 USD", "150$", "$150", "150 dollar", "150 dollars")) {
            val h = hit(typed)
            assertNotNull("no hit for \"$typed\"", h)
            assertEquals(typed, SmartSuggest.Kind.CURRENCY, h!!.kind)
            assertEquals(typed, "18,000.00 Taka", h.result)
            assertEquals(typed, typed.length, h.replaceSpan)
        }
    }

    @Test
    fun currencyKeepsWhatCameBefore() {
        val h = hit("that will be 150 usd")
        assertEquals("18,000.00 Taka", h?.result)
        // Only "150 usd" is replaced, not the sentence around it.
        assertEquals("150 usd".length, h?.replaceSpan)
    }

    @Test
    fun currencySymbolsMapToTheirCodes() {
        assertEquals("SmartSuggest should read € as EUR", "150 EUR", hit("150€")?.query)
        assertEquals("150 GBP", hit("£150")?.query)
        assertEquals("150 INR", hit("₹150")?.query)
        assertEquals("150 BDT", hit("150 tk")?.query)
    }

    @Test
    fun amountAlreadyInTargetConvertsBackToTheOtherSide() {
        val h = hit("120 bdt")
        assertEquals(SmartSuggest.Kind.CURRENCY, h?.kind)
        assertEquals("1.00 Dollar", h?.result)
    }

    @Test
    fun currencyWithoutRatesIsPendingRatherThanMissing() {
        val h = hit("150 usd", ctx.copy(rates = null))
        assertEquals(SmartSuggest.Kind.CURRENCY, h?.kind)
        assertTrue("should be waiting on rates", h!!.pending)
        assertNull(h.result)
        assertNull(h.insert)
    }

    @Test
    fun lowercaseIsoCodesThatAreAlsoEnglishWordsAreIgnored() {
        // "150 try" is a sentence far more often than Turkish lira; the
        // capitalised form still reads as the currency.
        val onlyCurrency = ctx.copy(
            rates = rates.copy(rates = rates.rates + ("TRY" to 32.0)),
            calcEnabled = false, unitsEnabled = false, keywordsEnabled = false,
        )
        assertNull(hit("150 try", onlyCurrency))
        assertEquals("150 TRY", hit("150 TRY", onlyCurrency)?.query)
    }

    @Test
    fun poundsReadAsWeightNotSterling() {
        assertEquals(SmartSuggest.Kind.UNIT, hit("5 pounds")?.kind)
    }

    // ---- units ----

    @Test
    fun unitsReadSpacedAndGlued() {
        for (typed in listOf("1ft", "1 ft", "1 foot", "1 feet")) {
            val h = hit(typed)
            assertNotNull("no hit for \"$typed\"", h)
            assertEquals(typed, SmartSuggest.Kind.UNIT, h!!.kind)
            assertEquals(typed, "0.3048 m", h.result)
        }
    }

    @Test
    fun unitChipCarriesEverythingTheConverterNeeds() {
        val prefill = hit("72kg")?.prefill as? ToolPrefill.Units
        assertEquals("Mass", prefill?.category)
        assertEquals("kg", prefill?.from)
        assertEquals("lb", prefill?.to)
        assertEquals("72", prefill?.value)
    }

    @Test
    fun theLastUsedPairWinsOverTheBuiltInPartner() {
        val h = hit("1 km", ctx.copy(unitLast = "Length|km|ft"))
        assertEquals("Length|km|ft", "3280.8399 ft", h?.result)
    }

    @Test
    fun oneLetterUnitsNeedToBeGluedToTheNumber() {
        // "30c" is a temperature; "30 c" is prose that happens to end in a letter.
        assertEquals(SmartSuggest.Kind.UNIT, hit("30c")?.kind)
        assertNull(hit("about 30 c"))
        assertNull(hit("meet me in 5 in"))
    }

    @Test
    fun temperatureCrossesTheOffsetCorrectly() {
        assertEquals("86 °F", hit("30c")?.result)
    }

    // ---- arithmetic ----

    @Test
    fun sumsAreOfferedWithTheirResult() {
        assertEquals("48", hit("12*4")?.result)
        assertEquals("4", hit("2+2")?.result)
        assertEquals("3.5", hit("(3+4)/2")?.result)
        assertEquals(SmartSuggest.Kind.CALC, hit("12 × 4")?.kind)
    }

    @Test
    fun trailingEqualsAppendsInsteadOfReplacing() {
        val h = hit("12*4=")
        assertEquals("48", h?.result)
        assertEquals("nothing should be deleted after an explicit =", 0, h?.replaceSpan)
    }

    @Test
    fun datesAndPhoneNumbersAreNotSums() {
        assertNull(hit("12/04"))
        assertNull(hit("2024-07"))
        assertNull(hit("555-1234"))
        // Unless the user asks outright.
        assertEquals("3", hit("12/4=")?.result)
    }

    @Test
    fun bareNumbersAreNotSums() {
        assertNull(hit("150"))
        assertNull(hit("3.14"))
        assertNull(hit("-42"))
    }

    @Test
    fun aTrailingPercentIsAPercentageNotADivision() {
        assertNull(hit("100%"))
        assertNull(hit("up 50%"))
        // Mixed with another operator it is arithmetic again — and the
        // engine reads a trailing "%" as "of one", so this is 50 + 0.1.
        assertEquals("50.1", hit("50+10%")?.result)
    }

    @Test
    fun rootsCountAsArithmeticEvenAtTheFront() {
        assertEquals("3", hit("√9")?.result)
    }

    @Test
    fun aTrailingSpaceStaysInsideTheSpan() {
        // The span has to cover the space too — deleting "expression.length"
        // characters would eat the space and leave the first digit behind.
        val h = hit("12*4 ")
        assertEquals("48", h?.result)
        assertEquals("12*4 ".length, h?.replaceSpan)
    }

    @Test
    fun aSumInsideASentenceStillOnlyReplacesTheSum() {
        val h = hit("the total is 12*4")
        assertEquals("48", h?.result)
        assertEquals("12*4".length, h?.replaceSpan)
    }

    // ---- tool keywords ----

    @Test
    fun keywordsOfferTheirTool() {
        val h = hit("wiki")
        assertEquals(SmartSuggest.Kind.TOOL, h?.kind)
        assertEquals(ToolbarTool.WIKIPEDIA, h?.tool)
        assertEquals("wiki".length, h?.replaceSpan)
        assertNull("a keyword chip only opens the tool", h?.insert)
    }

    @Test
    fun keywordsOnlyFireOnTheWholeWord() {
        assertNull(hit("wikipedian"))
        assertEquals(ToolbarTool.WIKIPEDIA, hit("see wiki")?.tool)
    }

    @Test
    fun disabledToolsNeverOfferThemselves() {
        assertNull(hit("wiki", ctx.copy(enabledTools = listOf(ToolbarTool.EMOJI))))
    }

    @Test
    fun overridesReplaceTheDefaultsAndCanBeCleared() {
        val custom = SmartSuggest.withKeywords("", ToolbarTool.WIKIPEDIA, listOf("enc"))
        assertEquals(ToolbarTool.WIKIPEDIA, hit("enc", ctx.copy(keywordOverrides = custom))?.tool)
        assertNull(hit("wiki", ctx.copy(keywordOverrides = custom)))

        val silenced = SmartSuggest.withKeywords("", ToolbarTool.WIKIPEDIA, emptyList())
        assertNull(hit("wiki", ctx.copy(keywordOverrides = silenced)))
    }

    @Test
    fun writingBackTheDefaultsDropsTheOverride() {
        val defaults = SmartSuggest.defaultKeywords.getValue(ToolbarTool.WIKIPEDIA)
        val encoded = SmartSuggest.withKeywords("", ToolbarTool.WIKIPEDIA, defaults)
        assertEquals("", encoded)
    }

    // ---- gating and ordinary prose ----

    @Test
    fun eachKindCanBeTurnedOffOnItsOwn() {
        assertNull(hit("12*4", ctx.copy(calcEnabled = false)))
        assertNull(hit("150 usd", ctx.copy(currencyEnabled = false)))
        assertNull(hit("1 ft", ctx.copy(unitsEnabled = false)))
        assertNull(hit("wiki", ctx.copy(keywordsEnabled = false)))
    }

    @Test
    fun ordinaryTypingIsLeftAlone() {
        for (typed in listOf(
            "hello there", "see you at 5", "call me", "", "i have 2 cats",
            "version 1.2.3", "room 101", "he said so",
        )) {
            assertNull("\"$typed\" should not raise a chip", hit(typed))
        }
    }
}
