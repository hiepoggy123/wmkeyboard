package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun shortAndSpelledOutMagnitudesScaleTheAmount() {
        for (typed in listOf("1.5k usd", "1.5K USD", "1.5k dollars", "1 thousand 500 dollars")) {
            val h = hit(typed)
            assertNotNull("no hit for \"$typed\"", h)
            assertEquals(typed, "180,000.00 Taka", h!!.result)
            assertEquals(typed, typed.length, h.replaceSpan)
        }
        assertEquals("240,000,000.00 Taka", hit("2m usd")?.result)
        assertEquals("240,000,000.00 Taka", hit("2 million dollars")?.result)
        assertEquals("180,000.00 Taka", hit("$1.5k")?.result)
    }

    @Test
    fun aScaledAmountReachesTheConverterAsAPlainNumber() {
        val prefill = hit("1.5k usd")?.prefill as? ToolPrefill.Currency
        assertEquals("USD", prefill?.from)
        assertEquals("1500", prefill?.amount)
        // The chip itself still shows the amount the way it was typed.
        assertEquals("1.5k USD", hit("1.5k usd")?.query)
    }

    @Test
    fun onlyTheLastNumberOfARunIsSpentWhenItStandsAlone() {
        // "1 thousand 500" is one amount; "in 2020 500" is a year and a price.
        val h = hit("in 2020 500 usd")
        assertEquals("60,000.00 Taka", h?.result)
        assertEquals("500 usd".length, h?.replaceSpan)
    }

    @Test
    fun aMagnitudeLetterNeverEatsIntoTheCurrencyCode() {
        // "b" is billion, but not when "bdt" follows it.
        assertEquals("120 BDT", hit("120 bdt")?.query)
        assertEquals("5 bucks", SmartSuggest.Kind.CURRENCY, hit("5 bucks")?.kind)
    }

    @Test
    fun poundsReadAsWeightNotSterling() {
        assertEquals(SmartSuggest.Kind.UNIT, hit("5 pounds")?.kind)
    }

    // ---- display tiers ----

    @Test
    fun theWidestTierEchoesTheWordTheUserTyped() {
        val tiers = hit("150 dollar")!!.tiers
        assertEquals("150 Dollar", tiers.first().query)
        assertEquals("18,000.00 Taka", tiers.first().result)
        // The insert never degrades with the display.
        assertEquals("18,000.00 Taka", hit("150 dollar")?.insert)
    }

    @Test
    fun aTypedSymbolStaysASymbolOnTheChip() {
        assertEquals("$150", hit("$150")!!.tiers.first().query)
        assertEquals("150€", hit("150€")!!.tiers.first().query)
    }

    @Test
    fun tiersShedZerosThenNamesThenCodes() {
        val tiers = hit("150 dollar")!!.tiers
        // ".00" goes first, then the name gives way to the symbol.
        assertTrue("18,000 Taka" in tiers.map { it.result })
        assertEquals("৳18,000", tiers.last().result)
        assertTrue("the digits tier is a last resort", tiers.last().lastResort)
        assertEquals("$150", tiers.last().query)
    }

    @Test
    fun aSpelledOutAmountOnlyFlattensToDigitsAsALastResort() {
        val tiers = hit("1 thousand 500 dollars")!!.tiers
        assertEquals("1 thousand 500 Dollars", tiers.first().query)
        assertFalse(tiers.first().lastResort)
        val resort = tiers.first { it.lastResort }
        assertEquals("1500 USD", resort.query)
    }

    @Test
    fun aRoundingThatMovesTheNumberWearsATilde() {
        // 5 BDT is about 0.0417 USD; rounding to a whole unit is a real lie.
        val tiers = hit("5 bdt")!!.tiers
        assertTrue(tiers.any { it.result.startsWith("~") })
        // 150 USD is exactly 18,000 BDT; no tilde anywhere.
        assertTrue(hit("150 usd")!!.tiers.none { it.result.startsWith("~") })
    }

    @Test
    fun calcOffersARoundedPreviewTier() {
        val tiers = hit("1/3")!!.tiers
        assertEquals("0.3333", tiers.first().result)
        assertEquals("~0.33", tiers.last().result)
        // Exact sums have nothing to round away.
        assertEquals(1, hit("12*4")!!.tiers.size)
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

    @Test
    fun unitsReadSpelledOutMagnitudesLikeCurrencyDoes() {
        val h = hit("1 thousand miles")
        assertEquals(SmartSuggest.Kind.UNIT, h?.kind)
        assertEquals("1609.344 km", h?.result)
        assertEquals("1 thousand miles".length, h?.replaceSpan)
        assertEquals("1000", (h?.prefill as? ToolPrefill.Units)?.value)
        // "2 lakh km" and friends scale the same way.
        assertEquals(SmartSuggest.Kind.UNIT, hit("2 lakh km")?.kind)
    }

    @Test
    fun theMagnitudeLettersStayOutOfUnits() {
        // "5m" is five metres, never five million of anything.
        assertEquals("16.4042 ft", hit("5m")?.result)
    }

    @Test
    fun aUnitAmountOnlySpendsTheNumbersThatBelongToIt() {
        val h = hit("in 2020 500 miles")
        assertEquals("500 miles".length, h?.replaceSpan)
        assertEquals("804.672 km", h?.result)
    }

    @Test
    fun unitTiersMirrorTheCurrencyLadder() {
        val tiers = hit("1 thousand miles")!!.tiers
        assertEquals("1 thousand miles", tiers.first().query)
        assertEquals("1609.344 km", tiers.first().result)
        val resort = tiers.first { it.lastResort }
        assertEquals("1000 mi", resort.query)
        assertEquals("1609 km", resort.result)
    }

    @Test
    fun unitResultsDegradeThroughTwoDecimalsToWholeUnits() {
        assertEquals(
            listOf("1.6093 km", "1.61 km", "~2 km"),
            hit("1 mi")!!.tiers.map { it.result },
        )
    }

    @Test
    fun aSpelledOutUnitShrinksToItsSymbolBeforeDigits() {
        val mid = hit("1 thousand miles")!!.tiers.first { it.query == "1 thousand mi" }
        assertFalse("symbol tier comes before the digits tier", mid.lastResort)
        // A symbol that is not shorter never replaces the typed unit:
        // "30c" must not widen into "30 °C".
        assertTrue(hit("30c")!!.tiers.none { it.query == "30 °C" })
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
        assertNull(hit("12/04/2025"))
        // Unless the user asks outright.
        assertEquals("3", hit("12/4=")?.result)
    }

    @Test
    fun aPlainDivisionIsASumOfItsOwn() {
        assertEquals("0.5", hit("1/2")?.result)
        assertEquals("3", hit("12/4")?.result)
        assertEquals("1/2".length, hit("1/2")?.replaceSpan)
    }

    @Test
    fun countingOperatorsAreOffered() {
        assertEquals("60", hit("5p3")?.result)
        assertEquals("6", hit("4c2")?.result)
        assertEquals("120", hit("10C3")?.result)
        assertEquals(SmartSuggest.Kind.CALC, hit("5P3")?.kind)
    }

    @Test
    fun aStrayLetterBesideANumberIsNotACount() {
        // The letters only count glued between two digits.
        assertNull(hit("page 5 c 2"))
        assertNull(hit("chapter 3 p"))
        // A lone "5c" is still a temperature.
        assertEquals(SmartSuggest.Kind.UNIT, hit("5c")?.kind)
        // r cannot be bigger than n.
        assertNull(hit("2c5"))
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
    fun textEditingKeywordPreservesText() {
        val h = hit("edit")
        assertEquals(SmartSuggest.Kind.TOOL, h?.kind)
        assertEquals(ToolbarTool.TEXT_EDIT, h?.tool)
        assertEquals(0, h?.replaceSpan)
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

    // ---- keyword case sensitivity ----

    @Test
    fun keywordsIgnoreCapitalsUntilAToolAsksForThem() {
        assertEquals(ToolbarTool.WIKIPEDIA, hit("Wiki")?.tool)
        assertEquals(ToolbarTool.WIKIPEDIA, hit("WIKI")?.tool)
    }

    @Test
    fun aCaseSensitiveToolOnlyAnswersToItsOwnSpelling() {
        val words = SmartSuggest.withKeywords("", ToolbarTool.WIKIPEDIA, listOf("Wiki"))
        val exact = SmartSuggest.encodeCaseSensitive(setOf(ToolbarTool.WIKIPEDIA))
        val context = ctx.copy(keywordOverrides = words, caseSensitiveKeywords = exact)
        assertEquals(ToolbarTool.WIKIPEDIA, hit("Wiki", context)?.tool)
        assertNull("lower case must not match a case-sensitive keyword", hit("wiki", context))
        assertNull("upper case must not match either", hit("WIKI", context))
    }

    @Test
    fun keywordsKeepTheCapitalsTheyWereSavedWith() {
        val encoded = SmartSuggest.withKeywords("", ToolbarTool.WIKIPEDIA, listOf("Wiki", "ENC"))
        assertEquals(listOf("Wiki", "ENC"), SmartSuggest.keywordsFor(ToolbarTool.WIKIPEDIA, encoded))
    }

    @Test
    fun caseSensitivityIsPerToolAndReversible() {
        val on = SmartSuggest.withCaseSensitive("", ToolbarTool.AI, sensitive = true)
        assertTrue(SmartSuggest.caseSensitiveKeyword(ToolbarTool.AI, on))
        assertFalse(SmartSuggest.caseSensitiveKeyword(ToolbarTool.WIKIPEDIA, on))

        val off = SmartSuggest.withCaseSensitive(on, ToolbarTool.AI, sensitive = false)
        assertEquals("", off)
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
