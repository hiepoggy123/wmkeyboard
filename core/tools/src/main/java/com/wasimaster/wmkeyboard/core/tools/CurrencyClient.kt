package com.wasimaster.wmkeyboard.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Exchange rates for the currency tool, from keyless free APIs. Fiat comes
 * from open.er-api.com by default (~160 currencies, daily refresh) and
 * cryptocurrency from Coinbase, with the rest of [Provider] available as
 * alternatives or fallbacks. Every source is normalised to units per USD
 * and cross-converted locally, so one fetch serves every pair.
 */
object CurrencyClient {

    /**
     * [rates] holds fiat and coins together — they are the same kind of
     * number, and [convert] cannot tell them apart. [crypto] names the codes
     * that came from a coin source, which is what the panel and the chip use
     * to pick the section and the decimals.
     */
    data class Rates(
        val base: String,
        val rates: Map<String, Double>,
        val crypto: Set<String> = emptySet(),
    ) {
        fun isCrypto(code: String): Boolean = code in crypto
    }

    /** Where rates can come from. Ids are stored in settings, so do not rename them. */
    enum class Provider(val fiat: Boolean, val crypto: Boolean) {
        ER_API(fiat = true, crypto = false),
        FRANKFURTER(fiat = true, crypto = false),
        COINBASE(fiat = true, crypto = true),
        CURRENCY_API(fiat = true, crypto = true),
        COINGECKO(fiat = false, crypto = true),
        ;

        companion object {
            fun of(id: String): Provider? = entries.firstOrNull { it.name == id }
        }
    }

    val defaultFiatProviders: List<String> = listOf(Provider.ER_API.name, Provider.FRANKFURTER.name)
    val defaultCryptoProviders: List<String> =
        listOf(Provider.COINBASE.name, Provider.CURRENCY_API.name)

    private val json = Json { ignoreUnknownKeys = true }

    // ---- fetching ----

    /**
     * Fiat rates from the first provider in [providers] that answers.
     * Providers that cannot serve fiat are skipped; an empty or unusable
     * list falls back to the defaults, so a bad setting cannot break the
     * tool outright.
     */
    fun fetchRates(providers: List<String> = defaultFiatProviders): Rates {
        val chain = chain(providers, defaultFiatProviders) { it.fiat }
        var last: Throwable? = null
        for (provider in chain) {
            runCatching { fetchFiat(provider) }
                .onSuccess { return it }
                .onFailure { last = it }
        }
        last?.let { throw it }
        error("No rate provider")
    }

    /**
     * Coin rates as units per USD, for the codes in [codes] only. Returned
     * raw rather than merged so the caller can keep the table and re-merge
     * when the user changes which coins are on, without another fetch.
     */
    fun fetchCryptoRates(
        providers: List<String> = defaultCryptoProviders,
        codes: Set<String> = CryptoCatalog.defaultCodes,
    ): Map<String, Double> {
        val chain = chain(providers, defaultCryptoProviders) { it.crypto }
        var last: Throwable? = null
        for (provider in chain) {
            runCatching { fetchCrypto(provider, codes) }
                .onSuccess { if (it.isNotEmpty()) return it }
                .onFailure { last = it }
        }
        last?.let { throw it }
        error("No coin provider")
    }

    private fun chain(
        ids: List<String>,
        fallback: List<String>,
        serves: (Provider) -> Boolean,
    ): List<Provider> {
        val picked = ids.mapNotNull(Provider::of).filter(serves)
        return picked.ifEmpty { fallback.mapNotNull(Provider::of).filter(serves) }
    }

    private fun fetchFiat(provider: Provider): Rates = when (provider) {
        Provider.ER_API -> parseErApi(ToolHttp.get("https://open.er-api.com/v6/latest/USD"))
        Provider.FRANKFURTER ->
            parseFrankfurter(ToolHttp.get("https://api.frankfurter.app/latest?from=USD"))
        Provider.COINBASE -> Rates("USD", parseCoinbase(ToolHttp.get(COINBASE_URL)) + ("USD" to 1.0))
        Provider.CURRENCY_API ->
            Rates("USD", parseCurrencyApi(ToolHttp.get(CURRENCY_API_URL)) + ("USD" to 1.0))
        Provider.COINGECKO -> error("CoinGecko has no fiat table")
    }

    private fun fetchCrypto(provider: Provider, codes: Set<String>): Map<String, Double> =
        when (provider) {
            Provider.COINBASE -> parseCoinbase(ToolHttp.get(COINBASE_URL)).filterKeys { it in codes }
            Provider.CURRENCY_API ->
                parseCurrencyApi(ToolHttp.get(CURRENCY_API_URL)).filterKeys { it in codes }
            Provider.COINGECKO -> {
                val ids = CryptoCatalog.geckoIds(codes)
                if (ids.isEmpty()) {
                    emptyMap()
                } else {
                    parseCoinGecko(
                        ToolHttp.get(geckoUrl(ids)),
                        CryptoCatalog.geckoIdToCode(codes),
                    )
                }
            }
            else -> error("${provider.name} has no coin table")
        }

    private const val COINBASE_URL = "https://api.coinbase.com/v2/exchange-rates?currency=USD"
    private const val CURRENCY_API_URL =
        "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json"

    private fun geckoUrl(ids: List<String>): String =
        "https://api.coingecko.com/api/v3/simple/price?ids=" +
            ids.joinToString("%2C") + "&vs_currencies=usd"

    // ---- parsing ----

    fun parseErApi(body: String): Rates {
        val root = json.parseToJsonElement(body).jsonObject
        check(root["result"]?.jsonPrimitive?.content == "success") { "Rate API error" }
        val rates = numbers((root["rates"] ?: error("No rates in response")).jsonObject)
        check(rates.isNotEmpty()) { "No rates in response" }
        return Rates(base = "USD", rates = rates + ("USD" to 1.0))
    }

    fun parseFrankfurter(body: String): Rates {
        val root = json.parseToJsonElement(body).jsonObject
        val rates = numbers((root["rates"] ?: error("No rates in response")).jsonObject)
        check(rates.isNotEmpty()) { "No rates in response" }
        return Rates(base = "USD", rates = rates + ("USD" to 1.0))
    }

    /**
     * Coinbase nests its table under `data.rates` and quotes every rate as a
     * string, so the values need reading as text before they are numbers.
     */
    fun parseCoinbase(body: String): Map<String, Double> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = (root["data"] ?: error("No rates in response")).jsonObject
        val rates = numbers((data["rates"] ?: error("No rates in response")).jsonObject)
        check(rates.isNotEmpty()) { "No rates in response" }
        return rates
    }

    /** The jsDelivr currency-api keeps everything under a lower-case `usd` object. */
    fun parseCurrencyApi(body: String): Map<String, Double> {
        val root = json.parseToJsonElement(body).jsonObject
        val table = (root["usd"] ?: error("No rates in response")).jsonObject
        val rates = numbers(table).mapKeys { (code, _) -> code.uppercase() }
        check(rates.isNotEmpty()) { "No rates in response" }
        return rates
    }

    /**
     * CoinGecko answers per coin id and quotes dollars per coin, the other
     * way round from every other source, so each price is inverted into the
     * coins-per-USD the rate table is built on.
     */
    fun parseCoinGecko(body: String, idToCode: Map<String, String>): Map<String, Double> {
        val root = json.parseToJsonElement(body).jsonObject
        val rates = buildMap {
            for ((id, element) in root) {
                val code = idToCode[id] ?: continue
                val entry = element as? JsonObject ?: continue
                val price = (entry["usd"] as? JsonPrimitive)?.content?.toDoubleOrNull()
                if (price == null || !price.isFinite() || price <= 0.0) continue
                put(code, 1.0 / price)
            }
        }
        check(rates.isNotEmpty()) { "No rates in response" }
        return rates
    }

    /** Reads a flat code → rate object, dropping anything that is not a usable number. */
    private fun numbers(table: JsonObject): Map<String, Double> =
        buildMap {
            for ((code, value) in table) {
                val rate = (value as? JsonPrimitive)?.content?.toDoubleOrNull() ?: continue
                if (!rate.isFinite() || rate <= 0.0) continue
                put(code, rate)
            }
        }

    // ---- merging ----

    /**
     * [fiat] plus the coins in [raw] the user has turned on. Fiat wins every
     * collision: Coinbase and the currency-api both quote EUR and BDT, and
     * those readings must not displace the fiat table the rest of the tool
     * is built on.
     */
    fun withCrypto(fiat: Rates, raw: Map<String, Double>, tickers: Set<String>): Rates {
        val wanted = CryptoCatalog.enabled(tickers)
        val add = raw.filterKeys { it in wanted && it !in fiat.rates }
        if (add.isEmpty()) return fiat.copy(crypto = emptySet())
        return fiat.copy(rates = fiat.rates + add, crypto = add.keys)
    }

    /** amount in [from] → [to]; both must exist in [rates]. */
    fun convert(amount: Double, from: String, to: String, rates: Rates): Double? {
        val fromRate = rates.rates[from] ?: return null
        val toRate = rates.rates[to] ?: return null
        if (fromRate == 0.0) return null
        return amount / fromRate * toRate
    }

    /** Human names for the common codes; anything else shows as its code. */
    val names: Map<String, String> = mapOf(
        "USD" to "US Dollar", "EUR" to "Euro", "GBP" to "British Pound",
        "BDT" to "Bangladeshi Taka", "INR" to "Indian Rupee", "JPY" to "Japanese Yen",
        "CNY" to "Chinese Yuan", "AUD" to "Australian Dollar", "CAD" to "Canadian Dollar",
        "CHF" to "Swiss Franc", "SGD" to "Singapore Dollar", "MYR" to "Malaysian Ringgit",
        "AED" to "UAE Dirham", "SAR" to "Saudi Riyal", "QAR" to "Qatari Riyal",
        "KWD" to "Kuwaiti Dinar", "PKR" to "Pakistani Rupee", "LKR" to "Sri Lankan Rupee",
        "NPR" to "Nepalese Rupee", "THB" to "Thai Baht", "IDR" to "Indonesian Rupiah",
        "PHP" to "Philippine Peso", "VND" to "Vietnamese Dong", "KRW" to "South Korean Won",
        "TRY" to "Turkish Lira", "RUB" to "Russian Ruble", "BRL" to "Brazilian Real",
        "MXN" to "Mexican Peso", "ZAR" to "South African Rand", "NGN" to "Nigerian Naira",
        "EGP" to "Egyptian Pound", "NZD" to "NZ Dollar", "SEK" to "Swedish Krona",
        "NOK" to "Norwegian Krone", "DKK" to "Danish Krone", "PLN" to "Polish Złoty",
        "CZK" to "Czech Koruna", "HUF" to "Hungarian Forint", "ILS" to "Israeli Shekel",
        "HKD" to "Hong Kong Dollar", "TWD" to "Taiwan Dollar",
    )

    /**
     * The bare unit name for compact display — "BDT" → "Taka", "USD" →
     * "Dollar" — taken as the last word of the full [names] entry. Falls
     * back to the code for anything without a known name, which is also what
     * coins get: "Binance Coin" would shorten to "Coin", and a ticker reads
     * better than that anyway.
     */
    fun unitName(code: String): String = names[code]?.substringAfterLast(' ') ?: code

    /** Codes the picker pins to the front, in this order. */
    val popular: List<String> = listOf(
        "USD", "EUR", "BDT", "GBP", "INR", "JPY", "CNY", "AUD", "CAD", "SGD",
        "AED", "SAR", "MYR", "TRY", "PKR",
    )
}
