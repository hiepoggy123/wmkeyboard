package com.wasimaster.wmkeyboard.app.updates

import com.wasimaster.wmkeyboard.core.endpoints.ServiceEndpoint
import com.wasimaster.wmkeyboard.core.endpoints.ServiceEndpoints
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/** One build of a package, as F-Droid's index describes it. */
@Serializable
internal data class FdroidPackage(
    val versionName: String = "",
    @Serializable(with = LenientIntSerializer::class)
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
    @Serializable(with = LenientIntSerializer::class)
    val suggestedVersionCode: Int = 0,
    val packages: List<FdroidPackage> = emptyList(),
)

/**
 * An integer that may arrive quoted. f-droid.org sends version codes as JSON
 * numbers; IzzyOnDroid serves the same API with them as strings ("76"). A
 * repository the user points the update check at must not read as "no update"
 * over a pair of quote marks.
 */
internal object LenientIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        val element = decoder.decodeJsonElement() as? JsonPrimitive ?: return 0
        return element.content.trim().toIntOrNull() ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** Reading F-Droid's index, and where to read it from. */
internal object FdroidIndex {

    private val json = Json { ignoreUnknownKeys = true }

    /** The index entry for one package. 404 when F-Droid does not have it. */
    fun apiUrl(packageName: String): String =
        "${ServiceEndpoints.base(ServiceEndpoint.FDROID_REPOSITORY)}/api/v1/packages/$packageName"

    /** The page a user is sent to, when the F-Droid client is not installed. */
    fun packagePage(packageName: String): String =
        "${ServiceEndpoints.base(ServiceEndpoint.FDROID_REPOSITORY)}/packages/$packageName/"

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
