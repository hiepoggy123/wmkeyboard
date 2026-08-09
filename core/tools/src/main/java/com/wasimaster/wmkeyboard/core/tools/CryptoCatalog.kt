package com.wasimaster.wmkeyboard.core.tools

import java.util.Locale

/**
 * The coins the currency tool knows about.
 *
 * Rate providers quote hundreds of tickers — Coinbase alone lists 636,
 * including `00`, `2Z`, `AI`, `ONE`, `NEAR`, `LINK`, `GAS`, `SAND` and
 * `TIME`. Reading all of them would turn "see you in 1 near future" into a
 * conversion, so only the codes in this catalogue (plus the tickers a user
 * adds by hand) are ever taken out of a provider's table.
 */
object CryptoCatalog {

    /**
     * [aliases] are the spellings that may be typed in lower case, so they
     * must never be ordinary words. They also cannot exceed 8 letters: the
     * currency scanner matches at most `[\p{L}]{2,8}` after an amount, which
     * is why "chainlink" and "avalanche" are absent.
     */
    data class Coin(
        val code: String,
        val name: String,
        val geckoId: String,
        val aliases: List<String> = emptyList(),
    )

    val coins: List<Coin> = listOf(
        Coin("BTC", "Bitcoin", "bitcoin", listOf("bitcoin")),
        Coin("ETH", "Ethereum", "ethereum", listOf("ethereum", "ether")),
        Coin("USDT", "Tether", "tether", listOf("tether")),
        Coin("USDC", "USD Coin", "usd-coin"),
        Coin("BNB", "BNB", "binancecoin"),
        Coin("XRP", "XRP", "ripple", listOf("ripple")),
        Coin("SOL", "Solana", "solana", listOf("solana")),
        Coin("ADA", "Cardano", "cardano", listOf("cardano")),
        Coin("DOGE", "Dogecoin", "dogecoin", listOf("dogecoin")),
        Coin("TRX", "TRON", "tron"),
        Coin("TON", "Toncoin", "the-open-network", listOf("toncoin")),
        Coin("AVAX", "Avalanche", "avalanche-2"),
        Coin("SHIB", "Shiba Inu", "shiba-inu"),
        Coin("DOT", "Polkadot", "polkadot", listOf("polkadot")),
        Coin("LINK", "Chainlink", "chainlink"),
        Coin("POL", "Polygon", "polygon-ecosystem-token", listOf("polygon")),
        Coin("MATIC", "Polygon (old)", "matic-network"),
        Coin("LTC", "Litecoin", "litecoin", listOf("litecoin")),
        Coin("BCH", "Bitcoin Cash", "bitcoin-cash"),
        Coin("XLM", "Stellar", "stellar", listOf("stellar")),
        Coin("XMR", "Monero", "monero", listOf("monero")),
        Coin("ETC", "Ethereum Classic", "ethereum-classic"),
        Coin("ATOM", "Cosmos", "cosmos"),
        Coin("UNI", "Uniswap", "uniswap", listOf("uniswap")),
        Coin("APT", "Aptos", "aptos", listOf("aptos")),
        Coin("NEAR", "NEAR Protocol", "near"),
        Coin("ARB", "Arbitrum", "arbitrum", listOf("arbitrum")),
        Coin("OP", "Optimism", "optimism", listOf("optimism")),
        Coin("FIL", "Filecoin", "filecoin", listOf("filecoin")),
        Coin("ICP", "Internet Computer", "internet-computer"),
        Coin("HBAR", "Hedera", "hedera-hashgraph", listOf("hedera")),
        Coin("VET", "VeChain", "vechain", listOf("vechain")),
        Coin("ALGO", "Algorand", "algorand", listOf("algorand")),
        Coin("AAVE", "Aave", "aave"),
        Coin("PEPE", "Pepe", "pepe"),
        Coin("CRO", "Cronos", "crypto-com-chain", listOf("cronos")),
        Coin("SUI", "Sui", "sui"),
        Coin("DAI", "Dai", "dai"),
        Coin("XTZ", "Tezos", "tezos", listOf("tezos")),
    )

    private val byCode: Map<String, Coin> = coins.associateBy { it.code }

    /** The coins that are on when the user has not picked their own set. */
    val defaultCodes: Set<String> = setOf(
        "BTC", "ETH", "USDT", "USDC", "BNB", "XRP", "SOL", "ADA", "DOGE",
        "TRX", "TON", "AVAX", "SHIB", "DOT", "LINK", "POL", "LTC", "BCH",
        "XLM", "XMR",
    )

    /**
     * Tickers that also read as themselves in lower case. Everything else
     * needs capitals, the same rule that keeps "150 all" English text while
     * "150 ALL" is Albanian lek — "1 dot", "3 link" and "2 near" are far more
     * often sentences than they are prices.
     */
    val safeLower: Set<String> = setOf("btc", "eth", "xrp", "ltc", "doge", "usdt", "usdc", "xmr")

    /** One satoshi is a hundred-millionth of a bitcoin. */
    private const val SATOSHI = 1e-8

    private val aliasIndex: Map<String, Pair<String, Double>> = buildMap {
        for (coin in coins) {
            for (alias in coin.aliases) put(alias, coin.code to 1.0)
        }
        put("sats", "BTC" to SATOSHI)
        put("satoshi", "BTC" to SATOSHI)
        put("satoshis", "BTC" to SATOSHI)
    }

    /** The codes in play: the user's own set, or the defaults while it is empty. */
    fun enabled(tickers: Set<String>): Set<String> =
        tickers.ifEmpty { defaultCodes }

    /** CoinGecko asks for coin ids rather than tickers. */
    fun geckoIds(codes: Set<String>): List<String> =
        coins.filter { it.code in codes }.map { it.geckoId }

    fun geckoIdToCode(codes: Set<String>): Map<String, String> =
        coins.filter { it.code in codes }.associate { it.geckoId to it.code }

    fun name(code: String): String? = byCode[code]?.name

    fun isKnown(code: String): Boolean = code in byCode

    /**
     * A typed token → the coin it means and how much of that coin one unit
     * is worth ("sats" is 1e-8 BTC). Names always match; a bare ticker needs
     * capitals unless it is in [safeLower]. Tickers the user added by hand
     * resolve too, in capitals only, since nothing here vouches for them.
     */
    fun resolve(token: String, tickers: Set<String>): Pair<String, Double>? {
        if (token.isEmpty()) return null
        val active = enabled(tickers)
        val lower = token.lowercase(Locale.ROOT)
        aliasIndex[lower]?.let { (code, scale) ->
            return if (code in active) code to scale else null
        }
        val upper = token.uppercase(Locale.ROOT)
        if (upper !in active) return null
        if (token != upper && lower !in safeLower) return null
        return upper to 1.0
    }
}
