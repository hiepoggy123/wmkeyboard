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

    // ---- cryptocurrency ----

    private val coinRates = rates.copy(
        rates = rates.rates + mapOf(
            "BTC" to 0.000015, "ETH" to 0.0005, "DOGE" to 14.0, "USDT" to 1.0,
            "AVAX" to 0.05, "LINK" to 0.08, "DOT" to 0.25,
        ),
        crypto = setOf("BTC", "ETH", "DOGE", "USDT", "AVAX", "LINK", "DOT"),
    )

    private val coinCtx = ctx.copy(
        rates = coinRates,
        calcEnabled = false,
        unitsEnabled = false,
        keywordsEnabled = false,
    )

    @Test
    fun coinAmountsConvertIntoTheTargetCurrency() {
        assertEquals("8,000,000.00 Taka", hit("1 btc", coinCtx)?.result)
        assertEquals("4,000,000.00 Taka", hit("0.5 BTC", coinCtx)?.result)
        assertEquals("16,000,000.00 Taka", hit("2 bitcoin", coinCtx)?.result)
        assertEquals("857.14 Taka", hit("100 doge", coinCtx)?.result)
    }

    @Test
    fun tickersLongerThanThreeLettersResolve() {
        // The three-letter gate that fiat codes go through would have thrown
        // both of these away.
        assertEquals("1,200.00 Taka", hit("10 USDT", coinCtx)?.result)
        assertEquals("2,400.00 Taka", hit("1 AVAX", coinCtx)?.result)
    }

    @Test
    fun tickersThatAreAlsoWordsNeedCapitals() {
        for (typed in listOf("3 link", "1 dot")) {
            assertNull("\"$typed\" is a sentence, not a price", hit(typed, coinCtx))
        }
        assertEquals("4,500.00 Taka", hit("3 LINK", coinCtx)?.result)
        assertEquals("480.00 Taka", hit("1 DOT", coinCtx)?.result)
    }

    @Test
    fun tickersThatAreNoWordAtAllReadInLowerCase() {
        for (typed in listOf("1 btc", "1 eth", "1 doge", "1 usdt")) {
            assertNotNull("no hit for \"$typed\"", hit(typed, coinCtx))
        }
    }

    @Test
    fun satsAreAHundredMillionthOfABitcoin() {
        val h = hit("100000 sats", coinCtx)
        assertEquals("8,000.00 Taka", h?.result)
        // The chip echoes what was typed, not 100,000 whole bitcoin.
        assertEquals("100000 sats", h?.query)
    }

    @Test
    fun aCoinPendsWhileOnlyTheCurrencyTableIsLoaded() {
        // The regression this guards: fiat rates arriving used to make the
        // coin chip disappear instead of waiting for the coin table.
        val h = hit("1 btc")
        assertEquals(SmartSuggest.Kind.CURRENCY, h?.kind)
        assertTrue("should be waiting on the coin table", h!!.pending)
        assertTrue("the caller has to know to fetch coins", h.pendingCrypto)
        assertNull(h.result)
    }

    @Test
    fun aCoinIsSilentWhenCoinsAreOffOrTheSourceIsDown() {
        assertNull(hit("1 btc", coinCtx.copy(cryptoEnabled = false)))
        // Rates are loaded but carry no coins and the source failed: give up
        // rather than spin on every keystroke.
        assertNull(hit("1 btc", ctx.copy(cryptoUnavailable = true)))
    }

    @Test
    fun theUsersOwnTickerSetReplacesTheDefaults() {
        val onlyBitcoin = coinCtx.copy(cryptoTickers = setOf("BTC"))
        assertNotNull(hit("1 btc", onlyBitcoin))
        assertNull(hit("100 doge", onlyBitcoin))
    }

    @Test
    fun aCoinTargetKeepsItsTickerAndItsDigits() {
        val toBitcoin = coinCtx.copy(currencyTo = "BTC")
        val h = hit("1000000 bdt", toBitcoin)
        // 1,000,000 BDT is 0.125 BTC — two decimals would have said "0.13",
        // and a smaller amount would have said "0.00".
        assertEquals("0.125 BTC", h?.result)
        assertEquals("0.000015 BTC", hit("120 bdt", toBitcoin)?.result)
    }

    @Test
    fun aCoinDecimalCountOverridesTheAutomaticDigits() {
        val fixed = coinCtx.copy(currencyTo = "BTC", cryptoDecimals = 4)
        assertEquals("0.1250 BTC", hit("1000000 bdt", fixed)?.result)
    }

    @Test
    fun noCoinTierEverRoundsAwayToZero() {
        val tiers = hit("120 bdt", coinCtx.copy(currencyTo = "BTC"))!!.tiers
        assertTrue(tiers.isNotEmpty())
        for (tier in tiers) {
            assertFalse("\"${tier.result}\" says nothing", tier.result.startsWith("0 "))
            assertFalse(tier.result.startsWith("~0 "))
        }
    }

    @Test
    fun aPairCodeThatLeftTheTableFallsBack() {
        // Coins were switched off while the saved pair still names one; the
        // chip converts into something else rather than dying.
        val h = hit("150 usd", ctx.copy(currencyTo = "BTC"))
        assertEquals("135.00 Euro", h?.result)
    }

    @Test
    fun noCoinTickerCollidesWithAKnownCurrency() {
        val fiat = CurrencyClient.names.keys + CurrencyClient.popular
        for (code in CryptoCatalog.defaultCodes) {
            assertFalse("$code is both a coin and a currency", code in fiat)
        }
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
        assertEquals("Length|km|ft", "3280 ft 10.08 in", h?.result)
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
        assertEquals("16 ft 4.85 in", hit("5m")?.result)
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
    fun feetComeBackWithTheirInches() {
        // A metre is 3 ft 3.37 in to anybody who measures in feet; the plain
        // decimal is a number, not a length.
        assertEquals("3 ft 3.37 in", hit("1m")?.result)
        assertEquals("3 ft 3.37 in", hit("1 m")?.result)
        // The chip inserts what it shows, units and all.
        assertEquals("3 ft 3.37 in", hit("1m")?.insert)
        // Off, it is the decimal it always was.
        assertEquals("3.2808 ft", hit("1m", ctx.copy(compoundUnits = false))?.result)
    }

    @Test
    fun aSpelledOutUnitIsAnsweredInWords() {
        assertEquals("3 feet 3.37 inches", hit("1 meter")?.result)
        assertEquals("3 feet 3.37 inches", hit("1 metre")?.result)
        // A symbol asked the question, so a symbol answers it.
        assertEquals("3 ft 3.37 in", hit("1m")?.result)
    }

    @Test
    fun compoundResultsDegradeThroughSymbolsToPrimes() {
        assertEquals(
            listOf("3 ft 3.37 in", "~3 ft 3 in", "~3'3\""),
            hit("1m")!!.tiers.map { it.result },
        )
        // Spelled out, the names go first and are the first thing given up.
        assertEquals(
            listOf("3 feet 3.37 inches", "3 ft 3.37 in", "~3 ft 3 in", "~3 ft 3 in", "~3'3\""),
            hit("1 meter")!!.tiers.map { it.result },
        )
        assertEquals(
            listOf("1 meter", "1 meter", "1 meter", "1 m", "1 m"),
            hit("1 meter")!!.tiers.map { it.query },
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
