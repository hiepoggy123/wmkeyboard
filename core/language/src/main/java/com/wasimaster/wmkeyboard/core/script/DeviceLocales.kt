package com.wasimaster.wmkeyboard.core.script

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/**
 * Reads the two device signals [LanguageSuggestions] ranks on.
 *
 * Both are permission-free and stay on the phone. There is deliberately no
 * third signal: nothing here looks at installed apps, accounts, contacts or
 * anything the user has typed.
 */
object DeviceLocales {

    /**
     * The phone's language list and region, ready to rank.
     *
     * Locales come from the app's own configuration, which is the system list
     * (Settings → System → Languages) in the user's preference order.
     *
     * Regions are ordered by how much they say about where someone actually is:
     * the SIM's country first, then the network's, then the regions carried by
     * the locales themselves. A locale's region is the weakest of the three —
     * plenty of phones ship `en-US` and never have it changed — so it is only
     * consulted when there is no SIM to ask.
     */
    fun read(context: Context): DeviceLanguageSignals {
        val locales = ConfigurationCompat.getLocales(context.resources.configuration)
        val tags = ArrayList<String>(locales.size())
        val localeRegions = ArrayList<String>(locales.size())
        for (i in 0 until locales.size()) {
            val locale = locales.get(i) ?: continue
            tags += locale.toLanguageTag()
            locale.country.takeIf { it.length == 2 }?.let { localeRegions += it.uppercase(Locale.ROOT) }
        }
        return DeviceLanguageSignals(
            systemLocales = tags.distinct(),
            regionCodes = (simRegions(context) + localeRegions).distinct(),
        )
    }

    /**
     * The SIM's and the network's country, when there is a radio to ask.
     *
     * Wrapped because [TelephonyManager] is missing entirely on Wi-Fi-only
     * tablets, and has been seen to throw on a few OEM builds rather than
     * return empty. Neither call needs a permission.
     */
    private fun simRegions(context: Context): List<String> = try {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        listOfNotNull(
            telephony?.simCountryIso,
            telephony?.networkCountryIso,
        ).filter { it.length == 2 }.map { it.uppercase(Locale.ROOT) }
    } catch (_: RuntimeException) {
        emptyList()
    }
}
