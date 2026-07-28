package com.wasimaster.wmkeyboard.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Exchange rates for the currency tool, from keyless free APIs:
 * open.er-api.com first (~160 currencies, daily refresh), falling back to
 * frankfurter.app (ECB, ~30 currencies). Rates are quoted against USD and
 * cross-converted locally, so one fetch serves every pair.
 */
object CurrencyClient {

    data class Rates(val base: String, val rates: Map<String, Double>)

    private val json = Json { ignoreUnknownKeys = true }

    fun fetchRates(): Rates {
        return runCatching {
            parseErApi(ToolHttp.get("https://open.er-api.com/v6/latest/USD"))
        }.getOrElse {
            parseFrankfurter(ToolHttp.get("https://api.frankfurter.app/latest?from=USD"))
        }
    }

    fun parseErApi(body: String): Rates {
        val root = json.parseToJsonElement(body).jsonObject
        check(root["result"]?.jsonPrimitive?.content == "success") { "Rate API error" }
        val rates = (root["rates"] ?: error("No rates in response")).jsonObject.mapNotNull { (code, value) ->
            value.jsonPrimitive.doubleOrNull?.let { code to it }
        }.toMap()
        check(rates.isNotEmpty()) { "No rates in response" }
        return Rates(base = "USD", rates = rates + ("USD" to 1.0))
    }

    fun parseFrankfurter(body: String): Rates {
        val root = json.parseToJsonElement(body).jsonObject
        val rates = (root["rates"] ?: error("No rates in response")).jsonObject.mapNotNull { (code, value) ->
            value.jsonPrimitive.doubleOrNull?.let { code to it }
        }.toMap()
        check(rates.isNotEmpty()) { "No rates in response" }
        return Rates(base = "USD", rates = rates + ("USD" to 1.0))
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
     * back to the code for anything without a known name.
     */
    fun unitName(code: String): String = names[code]?.substringAfterLast(' ') ?: code

    /** Codes the picker pins to the front, in this order. */
    val popular: List<String> = listOf(
        "USD", "EUR", "BDT", "GBP", "INR", "JPY", "CNY", "AUD", "CAD", "SGD",
        "AED", "SAR", "MYR", "TRY", "PKR",
    )
}
