package com.wasimaster.wmkeyboard.core.addons

import android.content.Context
import com.wasimaster.wmkeyboard.core.feedback.KeySoundPlayer
import com.wasimaster.wmkeyboard.core.feedback.SoundFile
import com.wasimaster.wmkeyboard.core.feedback.SoundImportResult
import com.wasimaster.wmkeyboard.core.feedback.SoundStore
import com.wasimaster.wmkeyboard.core.fonts.FontFile
import com.wasimaster.wmkeyboard.core.fonts.FontImportResult
import com.wasimaster.wmkeyboard.core.fonts.FontStore
import com.wasimaster.wmkeyboard.core.icons.IconImportResult
import com.wasimaster.wmkeyboard.core.icons.IconPackFile
import com.wasimaster.wmkeyboard.core.icons.IconPackStore
import com.wasimaster.wmkeyboard.core.layout.LayoutFile
import com.wasimaster.wmkeyboard.core.plugins.PluginFile
import com.wasimaster.wmkeyboard.core.plugins.PluginImportResult
import com.wasimaster.wmkeyboard.core.plugins.PluginStore
import com.wasimaster.wmkeyboard.core.prediction.CustomDictionaries
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.snippets.SnippetFile
import com.wasimaster.wmkeyboard.core.snippets.SnippetStore
import com.wasimaster.wmkeyboard.core.stickers.StickerImportResult
import com.wasimaster.wmkeyboard.core.stickers.StickerPackFile
import com.wasimaster.wmkeyboard.core.stickers.StickerPackStore
import com.wasimaster.wmkeyboard.core.theme.ThemeCodec
import com.wasimaster.wmkeyboard.core.theme.withExtractedImages
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Turning a downloaded payload into an installed addon.
 *
 * This is the whole point of the repository format being an *index* rather
 * than a package format: every branch below hands the file to an importer the
 * app already had for local file import, so installing from a URL and opening
 * a `.wmtheme.json` from the Downloads folder end up in exactly the same code.
 * Nothing here parses a payload itself.
 *
 * Every branch records a `localRef` — whatever handle the importer produced —
 * so [uninstall] can reverse precisely that one install and nothing else.
 */
object AddonInstaller {

    sealed interface Outcome {
        /**
         * [localRef] is the handle to undo this install: a custom theme or
         * layout id, a pack id, a font or sound id, or a dictionary file path.
         * [repairs] carries whatever the importer had to fix on the way in.
         */
        data class Installed(val localRef: String, val repairs: List<String> = emptyList()) : Outcome

        /** The importer refused it, with a line worth showing the user. */
        data class Rejected(val message: String) : Outcome
    }

    suspend fun install(context: Context, entry: AddonEntry, payload: File): Outcome =
        when (entry.type) {
            AddonType.Theme -> installTheme(context, payload)
            AddonType.Layout -> installLayout(context, payload)
            AddonType.Dictionary -> installDictionary(context, entry, payload)
            AddonType.Snippets -> installSnippets(context, payload)
            AddonType.Stickers -> installStickers(context, payload)
            AddonType.IconPack -> installIconPack(context, payload)
            AddonType.Font -> installFont(context, entry, payload, emoji = false)
            AddonType.EmojiFont -> installFont(context, entry, payload, emoji = true)
            AddonType.Sound -> installSound(context, entry, payload)
            AddonType.Plugin -> installPlugin(context, payload)
            AddonType.Unknown -> Outcome.Rejected("This app version doesn't support that addon type")
        }

    /**
     * Undoes what [install] recorded. Best-effort by design: the user may well
     * have deleted the theme by hand already, and the desired end state — it's
     * gone, and the store no longer claims it's installed — is the same either
     * way.
     */
    suspend fun uninstall(context: Context, record: InstalledAddon) {
        when (record.type) {
            // deleteCustomTheme already falls the active theme back to a
            // built-in when the deleted one was in use.
            AddonType.Theme -> SettingsRepository(context).deleteCustomTheme(record.localRef)
            AddonType.Layout -> SettingsRepository(context).deleteCustomLayout(record.localRef)
            AddonType.Dictionary -> File(record.localRef).delete()
            AddonType.Snippets -> removeSnippets(context, record.localRef)
            AddonType.Stickers -> StickerPackStore.get(context).deletePack(record.localRef)
            AddonType.IconPack -> uninstallIconPack(context, record.localRef)
            AddonType.Font -> FontStore.get(context).delete(record.localRef)
            AddonType.EmojiFont -> uninstallEmojiFont(context, record.localRef)
            AddonType.Sound -> uninstallSound(context, record.localRef)
            // Deletes the plugin's whole directory: script, stored data and log.
            AddonType.Plugin -> PluginStore.get(context).delete(record.localRef)
            AddonType.Unknown -> Unit
        }
    }

    // ---- plugins --------------------------------------------------------

    /**
     * Installs a `.wmplugin`.
     *
     * Nothing is applied and nothing runs: the script lands on disk and waits
     * for the user to open it from the Plugins panel. Every other single-slot
     * type switches to the thing just installed, because a sound that installs
     * without the keyboard making it reads as an install that did nothing —
     * but "apply" for code would mean "execute", which is not a thing an
     * install should ever do on its own.
     */
    private fun installPlugin(context: Context, payload: File): Outcome {
        val store = PluginStore.get(context)
        return when (val result = payload.inputStream().use { PluginFile.import(it, store) }) {
            is PluginImportResult.Imported -> Outcome.Installed(result.plugin.id)
            is PluginImportResult.Rejected -> Outcome.Rejected(result.reason)
            PluginImportResult.NotAPlugin -> Outcome.Rejected("That file isn't a WM Keyboard plugin")
            PluginImportResult.TooManyPlugins ->
                Outcome.Rejected("You already have the maximum of ${PluginStore.MAX_PLUGINS} plugins")

            PluginImportResult.Failed -> Outcome.Rejected("That plugin couldn't be installed")
        }
    }

    // ---- themes & layouts ----------------------------------------------

    private suspend fun installTheme(context: Context, payload: File): Outcome {
        val theme = ThemeCodec.decode(payload.readText())
            ?: return Outcome.Rejected("That file isn't a WM Keyboard theme")
        val id = "custom_${System.currentTimeMillis()}"
        // withExtractedImages writes any base64-embedded background out to
        // app-private storage and rewrites the path, which is also what drops
        // any absolute path the file arrived with — a theme must never point
        // at a file outside our own storage.
        val stored = theme.copy(id = id).withExtractedImages(themeImagesDir(context))
        val repository = SettingsRepository(context)
        repository.upsertCustomTheme(stored)
        // Switch to it, the same as opening a .wmtheme.json file does. A theme
        // that installs without changing anything visible reads as an install
        // that didn't work.
        repository.setKeyboardThemeId(id)
        return Outcome.Installed(id)
    }

    private suspend fun installLayout(context: Context, payload: File): Outcome {
        val imported = LayoutFile.decode(payload.readText())
            ?: return Outcome.Rejected("That file isn't a WM Keyboard layout")
        val id = "custom_${System.currentTimeMillis()}"
        SettingsRepository(context).upsertCustomLayout(imported.layout.copy(id = id))
        return Outcome.Installed(id, imported.repairs)
    }

    // ---- dictionaries ---------------------------------------------------

    private fun installDictionary(context: Context, entry: AddonEntry, payload: File): Outcome {
        val langId = entry.langId?.takeIf { it.isNotBlank() }
            ?: return Outcome.Rejected("This dictionary doesn't say which language it's for")
        // byId falls back to a generic definition rather than failing, so the
        // registry has to be asked directly. A repository can ship words for a
        // language the app knows; it cannot add a new language.
        if (LanguageRegistry.all.none { it.id == langId }) {
            return Outcome.Rejected("This app version doesn't support the language “$langId”")
        }

        val name = entry.name.ifBlank { entry.id }
        val words = openPayload(entry, payload).use { stream ->
            CustomDictionaries.import(context.filesDir, langId, name, stream)
        }
        if (words == 0) {
            return Outcome.Rejected("No words could be read out of that file")
        }
        // import() names the file itself to avoid collisions, so the newest
        // file in the language's folder is the one it just wrote.
        val created = CustomDictionaries.lists(context.filesDir, langId)
            .maxByOrNull { it.lastModified() }
            ?: return Outcome.Rejected("The dictionary couldn't be saved")
        return Outcome.Installed(created.absolutePath)
    }

    /** Unwraps a `.gz` payload; the format allows dictionaries to be gzipped. */
    private fun openPayload(entry: AddonEntry, payload: File): InputStream {
        val stream = payload.inputStream().buffered()
        return if (entry.path.endsWith(".gz", ignoreCase = true)) GZIPInputStream(stream) else stream
    }

    // ---- snippets --------------------------------------------------------

    private fun installSnippets(context: Context, payload: File): Outcome {
        val imported = SnippetFile.decode(payload.readText())
            ?: return Outcome.Rejected("That file isn't a WM Keyboard snippet pack")
        if (imported.snippets.isEmpty()) {
            return Outcome.Rejected("That snippet pack is empty")
        }
        val store = SnippetStore(snippetsFile(context))
        // Ids in the file are ignored — add() assigns fresh ones — so the ids
        // it hands back are what uninstall has to remember.
        val added = imported.snippets.map { store.add(it.label, it.text, it.trigger).id }
        // add() only mutates the in-memory list; nothing reaches disk until save().
        store.save()
        return Outcome.Installed(added.joinToString(","), imported.repairs)
    }

    private fun removeSnippets(context: Context, localRef: String) {
        val store = SnippetStore(snippetsFile(context))
        localRef.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .forEach { store.remove(it) }
        store.save()
    }

    // ---- packs -----------------------------------------------------------

    private fun installStickers(context: Context, payload: File): Outcome {
        val store = StickerPackStore.get(context)
        return when (val result = payload.inputStream().use { StickerPackFile.import(it, store) }) {
            is StickerImportResult.Imported -> Outcome.Installed(result.pack.id, result.repairs)
            StickerImportResult.NotAStickerPack -> Outcome.Rejected("That file isn't a sticker pack")
            is StickerImportResult.NoStickers -> Outcome.Rejected(
                "No stickers could be read out of that pack. " + result.repairs.first(),
            )
            StickerImportResult.TooManyPacks ->
                Outcome.Rejected("You've reached the maximum number of sticker packs")
            StickerImportResult.Failed -> Outcome.Rejected("The sticker pack couldn't be read")
        }
    }

    private suspend fun installIconPack(context: Context, payload: File): Outcome {
        val store = IconPackStore.get(context)
        return when (val result = payload.inputStream().use { IconPackFile.import(it, store) }) {
            is IconImportResult.Imported -> {
                // Installing an icon pack and not switching to it would look
                // like nothing happened; the keyboard's glyphs are the whole
                // visible result.
                SettingsRepository(context).setIconPack(result.pack.id)
                Outcome.Installed(result.pack.id, result.repairs)
            }
            IconImportResult.NotAnIconPack -> Outcome.Rejected("That file isn't an icon pack")
            IconImportResult.TooManyPacks ->
                Outcome.Rejected("You've reached the maximum number of icon packs")
            IconImportResult.Failed -> Outcome.Rejected("The icon pack couldn't be read")
        }
    }

    private suspend fun uninstallIconPack(context: Context, packId: String) {
        IconPackStore.get(context).deletePack(packId)
        // Drops the active-pack setting and any per-slot override pinned to it,
        // so the keyboard falls back to its built-in icons instead of resolving
        // half its glyphs to a pack that no longer exists.
        SettingsRepository(context).forgetIconPack(packId)
    }

    // ---- fonts & sounds --------------------------------------------------

    private suspend fun installFont(
        context: Context,
        entry: AddonEntry,
        payload: File,
        emoji: Boolean,
    ): Outcome {
        val store = FontStore.get(context)
        val result = payload.inputStream().use {
            FontFile.import(
                input = it,
                store = store,
                name = entry.name.ifBlank { entry.id },
                author = entry.author,
                version = entry.version,
                langIds = entry.languages,
                emoji = emoji,
            )
        }
        return when (result) {
            is FontImportResult.Imported -> {
                // An emoji font applies on install, like a theme or an icon
                // pack: it is a single global choice with one obvious slot, so
                // installing without switching would read as nothing happening.
                // Text faces don't — there are several pickers they could go in
                // (English, Bengali, per-script) and guessing wrong is worse
                // than letting the user pick.
                if (emoji) {
                    SettingsRepository(context).setInstalledEmojiFont(result.font.id)
                }
                Outcome.Installed(result.font.id)
            }
            is FontImportResult.NotAFont -> Outcome.Rejected(result.message)
            FontImportResult.TooManyFonts ->
                Outcome.Rejected("You've reached the maximum number of installed fonts")
            is FontImportResult.Failed -> Outcome.Rejected(result.message)
        }
    }

    private suspend fun uninstallSound(context: Context, soundId: String) {
        SoundStore.get(context).delete(soundId)
        SettingsRepository(context).forgetKeySound(soundId)
        // The pool holds a decoded copy that outlives the file.
        KeySoundPlayer.forgetCustom(soundId)
    }

    private suspend fun uninstallEmojiFont(context: Context, fontId: String) {
        FontStore.get(context).delete(fontId)
        // Leaving the choice pointing at a deleted file would render every
        // emoji with the fallback face while the setting still claims otherwise.
        SettingsRepository(context).forgetInstalledEmojiFont(fontId)
    }

    private suspend fun installSound(context: Context, entry: AddonEntry, payload: File): Outcome {
        val store = SoundStore.get(context)
        val result = payload.inputStream().use {
            SoundFile.import(
                input = it,
                store = store,
                name = entry.name.ifBlank { entry.id },
                author = entry.author,
                version = entry.version,
            )
        }
        return when (result) {
            is SoundImportResult.Imported -> {
                // Applies on install, like a theme, an icon pack or an emoji
                // font: the key sound is a single global slot, and a sound that
                // installs without the keyboard ever making it reads as an
                // install that did nothing. setKeySoundCustomId also flips the
                // style to CUSTOM, which is what actually makes it audible.
                SettingsRepository(context).setKeySoundCustomId(result.sound.id)
                Outcome.Installed(result.sound.id)
            }
            is SoundImportResult.NotASound -> Outcome.Rejected(result.message)
            SoundImportResult.TooManySounds ->
                Outcome.Rejected("You've reached the maximum number of installed key sounds")
            is SoundImportResult.Failed -> Outcome.Rejected(result.message)
        }
    }

    // ---- shared paths ----------------------------------------------------

    private fun themeImagesDir(context: Context): File =
        File(context.filesDir, "theme_images").apply { mkdirs() }

    private fun snippetsFile(context: Context): File =
        File(context.filesDir, "snippets/snippets.json")
}
