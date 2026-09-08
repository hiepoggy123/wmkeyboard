package com.wasimaster.wmkeyboard.app.updates

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One build of a package, as F-Droid's index describes it. */
@Serializable
internal data class FdroidPackage(
    val versionName: String = "",
    val versionCode: Int = 0,
)

/**
 * What F-Droid has for one package.
 *
 * `suggestedVersionCode` is the build F-Droid would install, which is the only
 * number worth comparing against: F-Droid's index lags a GitHub tag by however
 * long its build server takes, so asking GitHub and then sending the user to
 * F-Droid would point them at a page that still shows the version they have.
 */
@Serializable
internal data class FdroidPackages(
    val packageName: String = "",
    val suggestedVersionCode: Int = 0,
    val packages: List<FdroidPackage> = emptyList(),
)

/** Reading F-Droid's index, and where to read it from. */
internal object FdroidIndex {

    private val json = Json { ignoreUnknownKeys = true }

    /** The index entry for one package. 404 when F-Droid does not have it. */
    fun apiUrl(packageName: String): String =
        "https://f-droid.org/api/v1/packages/$packageName"

    /** The page a user is sent to, when the F-Droid client is not installed. */
    fun packagePage(packageName: String): String =
        "https://f-droid.org/packages/$packageName/"

    /** The index in [text], or null when it is not one. */
    fun decode(text: String): FdroidPackages? =
        runCatching { json.decodeFromString<FdroidPackages>(text) }.getOrNull()

    /**
     * The version F-Droid would install, if it is newer than [installedVersionCode].
     *
     * Returns the name alongside the code because F-Droid's suggested build is
     * an entry in the same response, and a card that named only a number would
     * be worse than one that names a version.
     */
    fun newerThan(index: FdroidPackages, installedVersionCode: Int): FdroidPackage? {
        val suggested = index.suggestedVersionCode
        if (suggested <= installedVersionCode) return null
        return index.packages.firstOrNull { it.versionCode == suggested }
            ?: FdroidPackage(versionName = "", versionCode = suggested)
    }
}
