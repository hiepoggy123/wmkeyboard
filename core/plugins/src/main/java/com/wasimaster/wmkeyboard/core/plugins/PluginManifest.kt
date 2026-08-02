package com.wasimaster.wmkeyboard.core.plugins

import android.content.Context
import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.plugins.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A line of text for the user that this module cannot put into words itself.
 *
 * Nothing here has a Context: the manifest reader, the importer and the runtime
 * are plain classes, and unit tests drive them with no Android around them. So a
 * message travels as a string resource with its arguments, and the screen that
 * draws it turns it into words with [resolve]. That also keeps the words in the
 * user's language at the moment they are read.
 */
sealed interface PluginText {

    /**
     * A line this build wrote. [arg1] and [arg2] fill `%1$s` and `%2$s`, in that
     * order. Two arguments is the whole budget; a message that needs more is a
     * message that is too long to read.
     */
    data class Resource(
        @get:StringRes val textRes: Int,
        val arg1: Any? = null,
        val arg2: Any? = null,
    ) : PluginText

    /**
     * Text from the Lua virtual machine, which the plugin author wrote. It is
     * shown as it arrives: the app has no translation for a stranger's error
     * message.
     */
    data class Script(val text: String) : PluginText

    companion object {
        /** A message with no arguments. */
        fun of(@StringRes textRes: Int): PluginText = Resource(textRes)

        /** A message with one argument. */
        fun of(@StringRes textRes: Int, arg1: Any): PluginText = Resource(textRes, arg1)

        /** A message with two arguments, in the order the string names them. */
        fun of(@StringRes textRes: Int, arg1: Any, arg2: Any): PluginText = Resource(textRes, arg1, arg2)
    }
}

/** Puts a [PluginText] into words. Call this where the text is drawn. */
fun PluginText.resolve(context: Context): String = when (this) {
    is PluginText.Script -> text
    is PluginText.Resource -> when {
        arg1 == null -> context.getString(textRes)
        arg2 == null -> context.getString(textRes, arg1)
        else -> context.getString(textRes, arg1, arg2)
    }
}

/**
 * `plugin.json`, the manifest inside a `.wmplugin` archive.
 *
 * Every field is defaulted so a manifest missing one decodes rather than
 * throws; what a manifest may actually get away with is decided by
 * [PluginManifestCodec.read], which is strict on purpose. Unlike the data
 * addon formats — where a bad entry is repaired and reported so one broken
 * sticker doesn't lose the pack — a plugin is code, and half-understood code
 * does not get to run. Every rejection here is total and carries a reason the
 * user can read.
 */
@Serializable
data class PluginManifest(
    /** Must be [PluginManifestCodec.FORMAT]; the one tag that identifies the file. */
    val format: String = "",
    /** Container format version. */
    val version: Int = PluginManifestCodec.VERSION,
    /** Reverse-DNS, lowercase. Doubles as the install key and the directory name. */
    val id: String = "",
    val name: String = "",
    /** The plugin's own version, shown to the user and compared for updates. */
    val pluginVersion: String = "",
    val author: String = "",
    val description: String = "",
    /** The `wm.*` API level this script needs. Higher than the host's is refused. */
    val apiVersion: Int = PluginManifestCodec.API_VERSION,
    /** The archive entry holding the Lua source. A lookup key, never a path. */
    val entry: String = PluginManifestCodec.DEFAULT_ENTRY,
    val permissions: List<String> = emptyList(),
)

/** What reading a manifest produced. */
sealed interface PluginManifestResult {

    data class Ok(
        val manifest: PluginManifest,
        val permissions: List<PluginPermission>,
    ) : PluginManifestResult

    /** No manifest, or a manifest for something else entirely. */
    data object NotAPlugin : PluginManifestResult

    /** A plugin manifest this build will not accept. [reasonText] is user-facing. */
    data class Rejected(val reasonText: PluginText) : PluginManifestResult
}

/**
 * Reads and validates `plugin.json`.
 *
 * The validation exists for two audiences. The user gets a manifest whose
 * displayed strings cannot lie about their length or smuggle direction-override
 * characters into a name that will later appear above a permission list. The
 * filesystem gets an id that is provably a single safe path segment, since the
 * id becomes a directory under `filesDir/plugins/`.
 */
object PluginManifestCodec {

    const val FORMAT = "wmkeyboard-plugin"

    /** Container format version this build writes and understands. */
    const val VERSION = 1

    /** The `wm.*` API level this build provides. */
    const val API_VERSION = 1

    const val DEFAULT_ENTRY = "main.lua"

    /**
     * A single safe path segment, lowercase so two ids cannot collide into one
     * directory on a case-insensitive filesystem. Leading character is
     * alphanumeric, which is also what stops `.` and `..` from ever matching.
     */
    private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{2,63}$")

    /** Shown above a permission list, so it is kept short and inert. */
    const val MAX_NAME = 40
    private const val MAX_VERSION = 32
    private const val MAX_AUTHOR = 64
    private const val MAX_DESCRIPTION = 280
    private const val MAX_ENTRY = 64
    private const val MAX_PERMISSIONS = 16

    /** Enough to echo an unknown permission back without pasting an essay into the UI. */
    private const val MAX_ECHO = 32

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(manifest: PluginManifest): String = json.encodeToString(manifest)

    @Suppress("ReturnCount")
    fun read(text: String): PluginManifestResult {
        val raw = runCatching { json.decodeFromString<PluginManifest>(text) }.getOrNull()
            ?: return PluginManifestResult.NotAPlugin
        if (raw.format != FORMAT) return PluginManifestResult.NotAPlugin

        // Treat a missing apiVersion as 1 — the field postdates nothing yet, and
        // a beginner who omits it meant the only level that exists.
        val api = if (raw.apiVersion <= 0) 1 else raw.apiVersion
        if (api > API_VERSION) {
            return PluginManifestResult.Rejected(
                PluginText.of(R.string.core_plugins_reject_newer_version),
            )
        }

        val id = raw.id.trim().lowercase()
        if (!ID_PATTERN.matches(id)) {
            return PluginManifestResult.Rejected(PluginText.of(R.string.core_plugins_reject_bad_id))
        }

        val name = displayText(raw.name, MAX_NAME)
        if (name.isEmpty()) {
            return PluginManifestResult.Rejected(PluginText.of(R.string.core_plugins_reject_no_name))
        }

        val version = displayText(raw.pluginVersion, MAX_VERSION)
        if (version.isEmpty()) {
            return PluginManifestResult.Rejected(
                PluginText.of(R.string.core_plugins_reject_no_version, name),
            )
        }

        val entry = displayText(raw.entry, MAX_ENTRY).ifEmpty { DEFAULT_ENTRY }

        val permissions = ArrayList<PluginPermission>()
        for (declared in raw.permissions.take(MAX_PERMISSIONS)) {
            val permission = PluginPermission.parse(declared)
                ?: return PluginManifestResult.Rejected(
                    PluginText.of(
                        R.string.core_plugins_reject_unknown_permission,
                        displayText(declared, MAX_ECHO),
                        name,
                    ),
                )
            if (permission !in permissions) permissions.add(permission)
        }

        val manifest = raw.copy(
            version = if (raw.version <= 0) VERSION else raw.version,
            id = id,
            name = name,
            pluginVersion = version,
            author = displayText(raw.author, MAX_AUTHOR),
            description = displayText(raw.description, MAX_DESCRIPTION),
            apiVersion = api,
            entry = entry,
            permissions = permissions.map { it.wire },
        )
        return PluginManifestResult.Ok(manifest, permissions)
    }

    /**
     * Trims [raw] to something safe to draw: no control characters, no
     * Unicode format characters, and no longer than [max].
     *
     * Format characters go because the bidirectional overrides are among them,
     * and a plugin name is rendered directly above the list of what that plugin
     * may do — a name that can reorder the text around it is a name that can lie
     * about it. The cost is that a zero-width joiner in an emoji sequence is
     * dropped too, which is a fair trade for a 40-character label.
     *
     * Walks code points rather than chars: a `Char` in the middle of a surrogate
     * pair reports itself as a surrogate, so filtering per-char would quietly
     * delete every emoji and every non-BMP script from a name. Whole pairs are
     * kept and only *lone* surrogates — which are malformed anyway — are dropped.
     */
    private fun displayText(raw: String, max: Int): String {
        val trimmed = raw.trim()
        val out = StringBuilder()
        var i = 0
        while (i < trimmed.length) {
            val cp = trimmed.codePointAt(i)
            val width = Character.charCount(cp)
            i += width
            if (!isDisplayable(cp)) continue
            if (out.length + width > max) break
            out.appendCodePoint(cp)
        }
        return out.toString().trim()
    }

    private fun isDisplayable(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.CONTROL.toInt(),
        Character.FORMAT.toInt(),
        Character.SURROGATE.toInt(),
        Character.PRIVATE_USE.toInt(),
        -> false

        else -> true
    }
}
