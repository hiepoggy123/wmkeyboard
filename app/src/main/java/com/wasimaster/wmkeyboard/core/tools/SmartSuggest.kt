package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import java.text.DecimalFormat
import java.util.Locale

/**
 * Pre-loaded input handed to a tool panel when it is opened from a smart
 * chip, so "1 ft" lands on the converter with Length/ft→m and 1 already
 * filled in instead of on whatever the panel was last used for.
 */
sealed interface ToolPrefill {
    data class Calc(val expression: String) : ToolPrefill
    data class Units(
        val category: String,
        val from: String,
        val to: String,
        val value: String,
    ) : ToolPrefill

    data class Currency(val from: String, val to: String, val amount: String) : ToolPrefill
}

/**
 * Recognises tool-shaped text as it is typed and offers the answer on the
 * suggestion strip: "12*4" → 48, "150 usd" → the amount in your currency,
 * "1 ft" → the same length in metres, "wiki" → open Wikipedia.
 *
 * Everything here is pure and synchronous — [detect] takes the text before
 * the cursor plus a [Context] snapshot and returns at most one [SmartHit].
 * The only thing it cannot compute on its own is a currency conversion with
 * no rates loaded, which comes back as [SmartHit.pending] so the caller can
 * kick off a fetch and show a placeholder chip meanwhile.
 */
object SmartSuggest {

    enum class Kind { CALC, CURRENCY, UNIT, TOOL }

    /**
     * One recognised trigger. [replaceSpan] is how many characters before
     * the cursor the trigger occupies — tapping the chip deletes exactly
     * that many and types [insert] in their place. A trigger that ends in
     * "=" reports a span of 0 so the result appends after it.
     */
    data class SmartHit(
        val kind: Kind,
        /** What was recognised, shown on the left of the chip. */
        val query: String,
        /** The answer, or null while rates are still in flight. */
        val result: String?,
        /** Text a tap types, or null when the chip only opens a tool. */
        val insert: String?,
        val replaceSpan: Int,
        val tool: ToolbarTool,
        val prefill: ToolPrefill?,
        /** True while a currency conversion is waiting on exchange rates. */
        val pending: Boolean = false,
    )

    /** Everything [detect] needs from settings and live tool state. */
    data class Context(
        val calcEnabled: Boolean = true,
        val currencyEnabled: Boolean = true,
        val unitsEnabled: Boolean = true,
        val keywordsEnabled: Boolean = true,
        val degrees: Boolean = true,
        val precision: Int = 8,
        val rates: CurrencyClient.Rates? = null,
        val currencyFrom: String = "USD",
        val currencyTo: String = "BDT",
        val currencyDecimals: Int = 2,
        val unitLast: String = "",
        val enabledTools: Collection<ToolbarTool> = emptyList(),
        val keywordOverrides: String = "",
    )

    /** Characters of context the scanners look back over. */
    const val LOOKBEHIND = 48

    /**
     * The first trigger that matches the tail of [text], in priority order:
     * currency, units, arithmetic, then tool keywords. Currency and units
     * both start with a number so they can never both match; arithmetic is
     * tried last of the three because "12/04" style text should lose to a
     * real unit or currency reading.
     */
    fun detect(text: String, ctx: Context): SmartHit? {
        if (text.isEmpty()) return null
        val tail = text.takeLast(LOOKBEHIND)
        if (ctx.currencyEnabled) detectCurrency(tail, ctx)?.let { return it }
        if (ctx.unitsEnabled) detectUnit(tail, ctx)?.let { return it }
        if (ctx.calcEnabled) detectCalc(tail, ctx)?.let { return it }
        if (ctx.keywordsEnabled) detectKeyword(tail, ctx)?.let { return it }
        return null
    }

    // ---- numbers ----

    /** "1500", "1,500", "1500.25" — grouping commas allowed, not required. */
    private const val NUM = """\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?"""

    private fun parseNumber(raw: String): Double? = raw.replace(",", "").toDoubleOrNull()

    /** Trims a plain number back to how the user typed it, for the chip. */
    private fun tidyNumber(raw: String): String = raw.replace(",", "")

    private fun money(value: Double, decimals: Int): String {
        val pattern = if (decimals > 0) "#,##0." + "0".repeat(decimals) else "#,##0"
        return DecimalFormat(pattern).format(value)
    }

    // ---- currency ----

    private val CURRENCY_SUFFIX = Regex("""(?<![\w.,])($NUM)\s?([\p{L}]{2,8}|[^\s\w])$""")
    private val CURRENCY_PREFIX = Regex("""(?<![\w])([\p{L}]{0,2}[^\s\w])\s?($NUM)$""")

    private fun detectCurrency(tail: String, ctx: Context): SmartHit? {
        val (amountRaw, tokenRaw, span) = run {
            CURRENCY_SUFFIX.find(tail)?.let {
                return@run Triple(it.groupValues[1], it.groupValues[2], it.value.length)
            }
            CURRENCY_PREFIX.find(tail)?.let {
                return@run Triple(it.groupValues[2], it.groupValues[1], it.value.length)
            }
            return null
        }
        val amount = parseNumber(amountRaw) ?: return null
        val from = resolveCurrency(tokenRaw, ctx.rates) ?: return null
        // Converting a currency into itself says nothing; when the amount is
        // already in the target, fall back to the pair's other side.
        val to = if (from == ctx.currencyTo) ctx.currencyFrom else ctx.currencyTo
        if (from == to) return null

        val query = "${tidyNumber(amountRaw)} $from"
        val prefill = ToolPrefill.Currency(from, to, tidyNumber(amountRaw))
        val rates = ctx.rates
            ?: return SmartHit(
                kind = Kind.CURRENCY, query = query, result = null, insert = null,
                replaceSpan = span, tool = ToolbarTool.CURRENCY, prefill = prefill,
                pending = true,
            )
        val converted = CurrencyClient.convert(amount, from, to, rates) ?: return null
        // The result side is the user's local currency: show and insert its
        // name ("Taka") rather than the ISO code ("BDT").
        val result = "${money(converted, ctx.currencyDecimals)} ${CurrencyClient.unitName(to)}"
        return SmartHit(
            kind = Kind.CURRENCY, query = query, result = result, insert = result,
            replaceSpan = span, tool = ToolbarTool.CURRENCY, prefill = prefill,
        )
    }

    /**
     * A currency token → ISO code. Symbols and spelled-out names always
     * match; bare three-letter codes match the well-known list in any case,
     * and any other live code only when typed in capitals, so "150 all"
     * stays English text while "150 ALL" is Albanian lek.
     */
    private fun resolveCurrency(token: String, rates: CurrencyClient.Rates?): String? {
        if (token.isEmpty()) return null
        currencySymbols[token]?.let { return it }
        val lower = token.lowercase(Locale.ROOT)
        currencyWords[lower]?.let { return it }
        val upper = token.uppercase(Locale.ROOT)
        if (upper.length != 3) return null
        if (upper in CurrencyClient.names || upper in CurrencyClient.popular) {
            // "try" reads as the verb far more often than Turkish lira.
            if (lower == "try" && token != "TRY") return null
            return upper
        }
        if (token == upper && rates?.rates?.containsKey(upper) == true) return upper
        return null
    }

    private val currencySymbols: Map<String, String> = mapOf(
        "$" to "USD", "US$" to "USD", "C$" to "CAD", "A$" to "AUD", "S$" to "SGD",
        "NZ$" to "NZD", "R$" to "BRL", "HK$" to "HKD", "NT$" to "TWD",
        "€" to "EUR", "£" to "GBP", "¥" to "JPY", "₹" to "INR", "₨" to "PKR",
        "৳" to "BDT", "₽" to "RUB", "₩" to "KRW", "₺" to "TRY", "₦" to "NGN",
        "₱" to "PHP", "฿" to "THB", "₫" to "VND", "₪" to "ILS", "₴" to "UAH",
        "₸" to "KZT", "₭" to "LAK", "₮" to "MNT", "₡" to "CRC", "﷼" to "SAR",
        "₾" to "GEL", "₼" to "AZN", "៛" to "KHR", "₲" to "PYG", "₵" to "GHS",
        "zł" to "PLN", "Kč" to "CZK",
    )

    // "pound(s)" is deliberately absent: it reads as mass far more often
    // than as sterling, and £/GBP/quid cover the currency.
    private val currencyWords: Map<String, String> = mapOf(
        "dollar" to "USD", "dollars" to "USD", "buck" to "USD", "bucks" to "USD",
        "euro" to "EUR", "euros" to "EUR",
        "quid" to "GBP", "sterling" to "GBP",
        "tk" to "BDT", "taka" to "BDT", "takas" to "BDT",
        "rupee" to "INR", "rupees" to "INR",
        "yen" to "JPY", "yuan" to "CNY", "rmb" to "CNY",
        "ringgit" to "MYR", "riyal" to "SAR", "riyals" to "SAR",
        "dirham" to "AED", "dirhams" to "AED", "dinar" to "KWD",
        "peso" to "MXN", "pesos" to "MXN", "rand" to "ZAR",
        "lira" to "TRY", "ruble" to "RUB", "rubles" to "RUB", "rouble" to "RUB",
        "franc" to "CHF", "francs" to "CHF",
        "krona" to "SEK", "kronor" to "SEK", "krone" to "NOK", "kroner" to "NOK",
        "baht" to "THB", "rupiah" to "IDR", "naira" to "NGN",
        "shekel" to "ILS", "shekels" to "ILS", "zloty" to "PLN",
        "koruna" to "CZK", "forint" to "HUF", "dong" to "VND", "won" to "KRW",
    )

    // ---- units ----

    private val UNIT_TAIL = Regex("""(?<![\w.,])($NUM)(\s?)([\p{L}°²³/]{1,9})$""")

    private fun detectUnit(tail: String, ctx: Context): SmartHit? {
        val match = UNIT_TAIL.find(tail) ?: return null
        val amountRaw = match.groupValues[1]
        val spaced = match.groupValues[2].isNotEmpty()
        val token = match.groupValues[3]
        val amount = parseNumber(amountRaw) ?: return null
        val from = resolveUnit(token, spaced) ?: return null
        val category = UnitConvert.categories.first { cat -> cat.units.any { it.symbol == from.symbol } }
        val to = targetUnit(category, from, ctx.unitLast) ?: return null

        val converted = UnitConvert.convert(amount, from, to)
        if (converted.isNaN() || converted.isInfinite()) return null
        val resultValue = CalcEngine.format(converted, ctx.precision)
        val result = "$resultValue ${to.symbol}"
        return SmartHit(
            kind = Kind.UNIT,
            query = "${tidyNumber(amountRaw)} ${from.symbol}",
            result = result,
            insert = result,
            replaceSpan = match.value.length,
            tool = ToolbarTool.UNIT_CONVERT,
            prefill = ToolPrefill.Units(
                category = category.name,
                from = from.symbol,
                to = to.symbol,
                value = tidyNumber(amountRaw),
            ),
        )
    }

    /**
     * Where a recognised unit converts to by default: whatever the user last
     * paired it with in the converter, else the counterpart in [unitPartner],
     * else the category's first other unit.
     */
    private fun targetUnit(
        category: UnitConvert.Category,
        from: UnitConvert.ConvUnit,
        unitLast: String,
    ): UnitConvert.ConvUnit? {
        val saved = unitLast.split(';').firstOrNull { entry ->
            val parts = entry.split('|')
            parts.size == 3 && parts[0] == category.name && parts[1] == from.symbol
        }?.split('|')?.getOrNull(2)
        saved?.let { symbol ->
            category.units.firstOrNull { it.symbol == symbol && it.symbol != from.symbol }
                ?.let { return it }
        }
        unitPartner[from.symbol]?.let { symbol ->
            category.units.firstOrNull { it.symbol == symbol }?.let { return it }
        }
        return category.units.firstOrNull { it.symbol != from.symbol }
    }

    /**
     * A typed unit token → catalog unit. [spaced] is false for the glued
     * "30c" form, which is the only place the one-letter temperature and
     * mass abbreviations are safe — "30 c" is far more likely to be prose.
     */
    private fun resolveUnit(token: String, spaced: Boolean): UnitConvert.ConvUnit? {
        val exact = unitAliases[token]
        val lower = token.lowercase(Locale.ROOT)
        val symbol = exact ?: unitAliases[lower] ?: return null
        if (spaced && lower in spacedUnitBlocklist) return null
        return UnitConvert.categories.firstNotNullOfOrNull { cat ->
            cat.units.firstOrNull { it.symbol == symbol }
        }
    }

    /**
     * Tokens that only count as units when glued to the number. Each is a
     * common English word or an abbreviation short enough to appear mid
     * sentence, and a chip firing on "5 in the box" is worse than missing
     * "5 in" as inches.
     */
    private val spacedUnitBlocklist = setOf(
        "in", "s", "d", "h", "t", "b", "c", "f", "a", "w", "j", "k", "l",
        "pt", "st", "at", "ct", "gon", "bar", "cup", "ton", "turn", "mo", "bit",
    )

    /**
     * Every spelling that maps onto a [UnitConvert] symbol. Keys are matched
     * case-sensitively first (so "MB" beats "mb" → millibar were that ever
     * added) and then lowercased, which is why the odd-cased entries below
     * carry both forms.
     */
    private val unitAliases: Map<String, String> = buildMap {
        fun alias(symbol: String, vararg names: String) {
            put(symbol, symbol)
            names.forEach { put(it, symbol) }
        }
        // Length
        alias("mm", "millimetre", "millimetres", "millimeter", "millimeters")
        alias("cm", "centimetre", "centimetres", "centimeter", "centimeters")
        alias("m", "metre", "metres", "meter", "meters")
        alias("km", "kms", "kilometre", "kilometres", "kilometer", "kilometers")
        alias("in", "inch", "inches")
        alias("ft", "foot", "feet")
        alias("yd", "yds", "yard", "yards")
        alias("mi", "mile", "miles")
        alias("nmi", "nauticalmile", "nauticalmiles")
        alias("µm", "um", "micrometre", "micrometres", "micron", "microns")
        alias("nm", "nanometre", "nanometres", "nanometer", "nanometers")
        alias("ly", "lightyear", "lightyears")
        // Mass
        alias("mg", "milligram", "milligrams")
        alias("g", "gm", "gram", "grams", "gramme", "grammes")
        alias("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms")
        alias("t", "tonne", "tonnes")
        alias("oz", "ounce", "ounces")
        alias("lb", "lbs", "pound", "pounds")
        alias("st", "stone", "stones")
        alias("ton", "tons")
        alias("ct", "carat", "carats")
        // Temperature
        alias("°C", "c", "celsius", "centigrade", "°c")
        alias("°F", "f", "fahrenheit", "°f")
        alias("K", "kelvin")
        // Area
        alias("mm²", "mm2", "sqmm")
        alias("cm²", "cm2", "sqcm")
        alias("m²", "m2", "sqm")
        alias("ha", "hectare", "hectares")
        alias("km²", "km2", "sqkm")
        alias("in²", "in2", "sqin")
        alias("ft²", "ft2", "sqft")
        alias("yd²", "yd2", "sqyd")
        alias("ac", "acre", "acres")
        alias("mi²", "mi2", "sqmi")
        alias("katha", "kathas")
        alias("bigha", "bighas")
        // Volume
        alias("mL", "ml", "millilitre", "millilitres", "milliliter", "milliliters")
        alias("L", "l", "litre", "litres", "liter", "liters")
        alias("m³", "m3")
        alias("tsp", "teaspoon", "teaspoons")
        alias("tbsp", "tablespoon", "tablespoons")
        alias("fl oz", "floz", "fluidounce", "fluidounces")
        alias("cup", "cups")
        alias("pt", "pint", "pints")
        alias("qt", "quart", "quarts")
        alias("gal", "gallon", "gallons")
        alias("in³", "in3")
        alias("ft³", "ft3")
        // Speed
        alias("m/s", "mps")
        alias("km/h", "kmh", "kph", "kmph")
        alias("mph")
        alias("kn", "knot", "knots")
        alias("ft/s", "fps")
        alias("Mach", "mach")
        // Time
        alias("ms", "millisecond", "milliseconds")
        alias("s", "sec", "secs", "second", "seconds")
        alias("min", "mins", "minute", "minutes")
        alias("h", "hr", "hrs", "hour", "hours")
        alias("d", "day", "days")
        alias("wk", "wks", "week", "weeks")
        alias("mo", "month", "months")
        alias("yr", "yrs", "year", "years")
        // Data
        alias("bit", "bits")
        alias("B", "byte", "bytes")
        alias("kB", "kb", "kilobyte", "kilobytes")
        alias("MB", "mb", "megabyte", "megabytes")
        alias("GB", "gb", "gigabyte", "gigabytes")
        alias("TB", "tb", "terabyte", "terabytes")
        alias("KiB", "kib")
        alias("MiB", "mib")
        alias("GiB", "gib")
        alias("TiB", "tib")
        // Energy
        alias("J", "j", "joule", "joules")
        alias("kJ", "kj", "kilojoule", "kilojoules")
        alias("cal", "calorie", "calories")
        alias("kcal", "kilocalorie", "kilocalories")
        alias("Wh", "wh")
        alias("kWh", "kwh")
        alias("BTU", "btu")
        alias("eV", "ev")
        // Power
        alias("W", "w", "watt", "watts")
        alias("kW", "kw", "kilowatt", "kilowatts")
        alias("MW", "mw", "megawatt", "megawatts")
        alias("hp", "horsepower")
        alias("PS")
        // Pressure
        alias("Pa", "pa", "pascal", "pascals")
        alias("kPa", "kpa")
        alias("bar", "bars")
        alias("atm", "atmosphere", "atmospheres")
        alias("psi")
        alias("mmHg", "mmhg", "torr")
        // Angle
        alias("°", "deg", "degree", "degrees")
        alias("rad", "radian", "radians")
        alias("gon", "gradian", "gradians")
        alias("turn", "turns")
        // Frequency
        alias("Hz", "hz", "hertz")
        alias("kHz", "khz")
        alias("MHz", "mhz")
        alias("GHz", "ghz")
        alias("rpm")
        // Fuel economy
        alias("L/100km", "l/100km")
        alias("km/L", "kmpl", "km/l")
        alias("mpg")
    }

    /** The unit each recognised unit converts into unless told otherwise. */
    private val unitPartner: Map<String, String> = mapOf(
        "mm" to "in", "cm" to "in", "m" to "ft", "km" to "mi",
        "in" to "cm", "ft" to "m", "yd" to "m", "mi" to "km", "nmi" to "km",
        "mg" to "g", "g" to "oz", "kg" to "lb", "t" to "kg",
        "oz" to "g", "lb" to "kg", "st" to "kg", "ton" to "t", "ct" to "g",
        "°C" to "°F", "°F" to "°C", "K" to "°C",
        "m²" to "ft²", "ft²" to "m²", "ha" to "ac", "ac" to "ha",
        "km²" to "mi²", "mi²" to "km²", "katha" to "m²", "bigha" to "ac",
        "mL" to "fl oz", "L" to "gal", "gal" to "L", "fl oz" to "mL",
        "cup" to "mL", "tsp" to "mL", "tbsp" to "mL", "pt" to "L", "qt" to "L",
        "m/s" to "km/h", "km/h" to "mph", "mph" to "km/h", "kn" to "km/h",
        "ft/s" to "m/s", "Mach" to "km/h",
        "ms" to "s", "s" to "min", "min" to "h", "h" to "min",
        "d" to "h", "wk" to "d", "mo" to "d", "yr" to "d",
        "bit" to "B", "B" to "KiB", "kB" to "MB", "MB" to "GB", "GB" to "MB",
        "TB" to "GB", "KiB" to "kB", "MiB" to "MB", "GiB" to "GB", "TiB" to "TB",
        "J" to "cal", "kJ" to "kcal", "cal" to "J", "kcal" to "kJ",
        "Wh" to "kWh", "kWh" to "Wh", "BTU" to "kJ", "eV" to "J",
        "W" to "hp", "kW" to "hp", "MW" to "kW", "hp" to "kW", "PS" to "kW",
        "Pa" to "psi", "kPa" to "psi", "bar" to "psi", "atm" to "bar",
        "psi" to "bar", "mmHg" to "kPa",
        "°" to "rad", "rad" to "°", "gon" to "°", "turn" to "°",
        "Hz" to "kHz", "kHz" to "MHz", "MHz" to "GHz", "GHz" to "MHz", "rpm" to "Hz",
        "L/100km" to "mpg", "km/L" to "mpg", "mpg" to "L/100km", "mpg UK" to "L/100km",
    )

    // ---- arithmetic ----

    private val CALC_TAIL = Regex("""(?<![\w.])([\d.,()+\-*/^%×÷−√πe ]{2,40}?)(=?)$""")
    private val CALC_OPERATORS = "+-*/^%×÷−√"
    private val DATE_LIKE = Regex("""^\d{1,4}([/.\-])\d{1,2}(\1\d{1,4})?$""")

    private fun detectCalc(tail: String, ctx: Context): SmartHit? {
        val match = CALC_TAIL.find(tail) ?: return null
        val explicit = match.groupValues[2] == "="
        val raw = match.groupValues[1]
        val expression = raw.trim()
        // Three characters is the shortest real sum ("1+1"); a root is the
        // one shape that says what it is in two ("√9").
        if (expression.length < 3 && '√' !in expression) return null
        if (!looksLikeArithmetic(expression, explicit)) return null

        val value = runCatching { CalcEngine.evaluate(expression, ctx.degrees) }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        val result = CalcEngine.format(value, ctx.precision)
        // "5" evaluating to "5" is not an answer worth a chip.
        if (result == expression.replace(" ", "")) return null

        return SmartHit(
            kind = Kind.CALC,
            query = expression,
            result = result,
            insert = result,
            // A typed "=" is the user asking for the answer to follow it,
            // so the chip appends instead of swallowing what they wrote.
            // Otherwise the span covers the expression *as typed* — leading
            // whitespace is not part of it, trailing whitespace is, or
            // "12*4 " would delete four characters ending in the space and
            // leave the 1 behind.
            replaceSpan = if (explicit) 0 else raw.trimStart().length,
            tool = ToolbarTool.CALCULATOR,
            prefill = ToolPrefill.Calc(expression),
        )
    }

    /**
     * Filters the many number-and-punctuation runs that are not sums.
     * A trailing "=" is taken as the user explicitly asking, and skips the
     * ambiguity checks below.
     */
    private fun looksLikeArithmetic(expression: String, explicit: Boolean): Boolean {
        val compact = expression.replace(" ", "")
        if (compact.isEmpty()) return false
        if (!compact.last().let { it.isDigit() || it == ')' || it == '%' || it == 'π' || it == 'e' }) {
            return false
        }
        if (!compact.first().let { it.isDigit() || it == '(' || it == '-' || it == '√' || it == 'π' }) {
            return false
        }
        // A sign at the very front is part of the number, not a sum — but a
        // leading root is the whole point of "√9".
        val operators = compact.filterIndexed { index, c ->
            c in CALC_OPERATORS && (index > 0 || c == '√')
        }
        if (operators.isEmpty()) return false
        if (compact.count { it.isDigit() } < 2 && '√' !in compact && 'π' !in compact) return false
        if (explicit) return true

        // "100%" is how people write a percentage, not a request to divide
        // it by a hundred. Infix "%" still counts, as does "50%+10".
        if (operators == "%" && compact.endsWith("%")) return false

        // "12/04" is a date and "555-1234" a phone number far more often
        // than either is a sum, so a single slash or dash between plain
        // integers needs a second operator before it earns a chip.
        if (DATE_LIKE.matches(compact)) return false
        if (operators.length == 1 && (operators == "-" || operators == "/")) {
            val parts = compact.split('-', '/')
            if (parts.size == 2 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) return false
        }
        return true
    }

    // ---- tool keywords ----

    private val KEYWORD_TAIL = Regex("""(?<![\w])([\p{L}]{2,16})$""")

    private fun detectKeyword(tail: String, ctx: Context): SmartHit? {
        val word = KEYWORD_TAIL.find(tail)?.groupValues?.get(1) ?: return null
        val lower = word.lowercase(Locale.ROOT)
        val overrides = decodeKeywords(ctx.keywordOverrides)
        val tool = ToolbarTool.entries.firstOrNull { candidate ->
            candidate in ctx.enabledTools &&
                lower in (overrides[candidate] ?: defaultKeywords[candidate].orEmpty())
        } ?: return null
        return SmartHit(
            kind = Kind.TOOL,
            query = word,
            result = null,
            insert = null,
            replaceSpan = word.length,
            tool = tool,
            prefill = null,
        )
    }

    /** The keywords each tool answers to until the user edits them. */
    val defaultKeywords: Map<ToolbarTool, List<String>> = mapOf(
        ToolbarTool.WIKIPEDIA to listOf("wiki", "wikipedia"),
        ToolbarTool.TRANSLATE to listOf("translate"),
        ToolbarTool.CALCULATOR to listOf("calc", "calculator"),
        ToolbarTool.UNIT_CONVERT to listOf("convert", "unit"),
        ToolbarTool.CURRENCY to listOf("currency", "forex"),
        ToolbarTool.GIF to listOf("gif"),
        ToolbarTool.STICKER to listOf("sticker"),
        ToolbarTool.EMOJI to listOf("emoji"),
        ToolbarTool.WEATHER to listOf("weather"),
        ToolbarTool.CLIPBOARD to listOf("clipboard"),
        ToolbarTool.SNIPPETS to listOf("snippet", "snippets"),
        ToolbarTool.QR_GEN to listOf("qr"),
        ToolbarTool.PASSWORD_GEN to listOf("password", "passgen"),
        ToolbarTool.AI to listOf("ai"),
        ToolbarTool.WEB_SEARCH to listOf("search"),
        ToolbarTool.IMAGE_SEARCH to listOf("images"),
        ToolbarTool.SYMBOLS to listOf("symbol", "symbols"),
        ToolbarTool.VOICE to listOf("dictate"),
        ToolbarTool.OCR to listOf("ocr"),
        ToolbarTool.QR_SCAN to listOf("scan"),
        ToolbarTool.DOC_SCAN to listOf("docscan"),
        ToolbarTool.HANDWRITING to listOf("handwrite"),
        ToolbarTool.TYPING_TEST to listOf("wpm"),
        ToolbarTool.DICTIONARY to listOf("define", "dict"),
        ToolbarTool.GRAMMAR to listOf("grammar"),
        ToolbarTool.CALENDAR to listOf("calendar"),
        ToolbarTool.MOON_PHASE to listOf("moon"),
        ToolbarTool.COMPASS to listOf("compass"),
        ToolbarTool.LEVEL to listOf("level"),
        ToolbarTool.THEMES to listOf("theme", "themes"),
        ToolbarTool.MODES to listOf("modes"),
        ToolbarTool.NUMPAD to listOf("numpad"),
        ToolbarTool.CAMERA to listOf("camera"),
        ToolbarTool.TEXT_EDIT to listOf("edit"),
    )

    fun keywordsFor(tool: ToolbarTool, overrides: String): List<String> =
        decodeKeywords(overrides)[tool] ?: defaultKeywords[tool].orEmpty()

    /**
     * Overrides encode as "TOOL=a,b;TOOL=c". A tool with an empty list is
     * kept in the map (that is how "no keywords for this one" is stored, as
     * distinct from "never edited, use the defaults").
     */
    fun decodeKeywords(encoded: String): Map<ToolbarTool, List<String>> =
        encoded.split(';').mapNotNull { entry ->
            val name = entry.substringBefore('=', "").ifEmpty { return@mapNotNull null }
            val tool = ToolbarTool.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            val words = entry.substringAfter('=', "")
                .split(',')
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.length >= 2 && it.all(Char::isLetter) }
            tool to words
        }.toMap()

    fun encodeKeywords(map: Map<ToolbarTool, List<String>>): String =
        map.entries.joinToString(";") { (tool, words) -> "${tool.name}=${words.joinToString(",")}" }

    /** Replaces one tool's keywords inside an encoded override string. */
    fun withKeywords(encoded: String, tool: ToolbarTool, words: List<String>): String {
        val map = decodeKeywords(encoded).toMutableMap()
        val cleaned = words.map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.length >= 2 && it.all(Char::isLetter) }
            .distinct()
        if (cleaned == defaultKeywords[tool].orEmpty()) map.remove(tool) else map[tool] = cleaned
        return encodeKeywords(map)
    }
}
