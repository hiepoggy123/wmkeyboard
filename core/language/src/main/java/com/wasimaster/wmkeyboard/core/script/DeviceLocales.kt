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
     * the SIM's country first, then the network's, then the phone's time zone,
     * and only last the regions carried by the locales themselves. A locale's
     * region is the weakest of the lot — plenty of phones ship `en-GB` or
     * `en-US` and never have it changed — and only the first region that has
     * anything to offer is read, so on a phone with a SIM or a set time zone it
     * never comes up at all.
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
            regionCodes = (simRegions(context) + timeZoneRegion() + localeRegions).distinct(),
        )
    }

    /**
     * The country of the phone's own time zone: `Asia/Dhaka` → `BD`.
     *
     * Weaker than a SIM but far stronger than a locale, and it is the only
     * signal a Wi-Fi-only tablet has. ICU keeps the zone-to-country table the
     * platform already ships, so this costs no data of ours.
     *
     * Zones that span countries, or that are not places at all (`UTC`,
     * `GMT+6`), answer `001` — the whole world — which is filtered out here
     * rather than looked up and missed.
     */
    private fun timeZoneRegion(): List<String> = try {
        val id = java.util.TimeZone.getDefault()?.id
        val region = id?.let { android.icu.util.TimeZone.getRegion(it) }
        listOfNotNull(region?.takeIf { it.length == 2 && it.all(Char::isLetter) }?.uppercase(Locale.ROOT))
    } catch (_: RuntimeException) {
        emptyList()
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
