package com.wasimaster.wmkeyboard.app

import android.content.res.Resources
import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.settings.isSupportedTool

/**
 * How much an entry is worth next to the others, as a percentage applied to
 * its raw match score.
 *
 * Two entries can match a word equally well and still deserve very different
 * places in the list. "Themes" names both the themes screen and the toolbar
 * shortcut that opens the theme picker, and someone searching *settings*
 * wants the screen. The backup screen, meanwhile, has a toggle named after
 * nearly every feature ("Sticker packs", "Include API keys") and none of
 * them is the feature itself.
 */
internal enum class EntryWeight(val percent: Int) {
    /** A destination screen: the whole feature lives behind it. */
    SECTION(200),

    /** An ordinary setting row, or a tool's own page. */
    NORMAL(100),

    /**
     * A row that only points at a setting whose real home is another screen:
     * the backup screen's per-feature toggles, and the "All … settings"
     * shortcuts on the tool pages.
     */
    MIRROR(40),
}

/**
 * One searchable setting: what it is called, where it lives, and the route
 * that opens the screen holding it.
 *
 * [title], [subtitle] and [screen] are plain text so that the ranking below
 * stays ordinary Kotlin. They are resolved once, by [settingsSearchIndex],
 * from the very resources the settings screens draw, so the index reads in
 * the same language the user sees.
 *
 * [titleRes] is the id behind [title]. It travels to [SettingsHighlight], which
 * matches a row by its resource rather than by its words: an id survives
 * translation, and the drawn text does not.
 */
internal data class SettingsSearchEntry(
    val title: String,
    val subtitle: String,
    /** Breadcrumb shown under the result, e.g. "Tools › Camera". */
    val screen: String,
    val route: String,
    val weight: EntryWeight = EntryWeight.NORMAL,
    /** Set on the tool entries, so a result can draw the tool's own icon. */
    val tool: ToolbarTool? = null,
    @StringRes val titleRes: Int = 0,
)

/** What separates the parts of a breadcrumb. Punctuation, not words. */
private const val CRUMB_SEPARATOR = " › "

/** Joins the breadcrumb parts that are set, outermost first. */
private fun Resources.crumb(vararg parts: Int): String =
    parts.filter { it != 0 }.joinToString(CRUMB_SEPARATOR) { getString(it) }

/**
 * Builds one entry from the resources the owning screen draws.
 *
 * [screen] names the screen the row sits on, [screenParent] the screen above
 * it and [screenRoot] the one above that. Leave the two outer ones at 0 for a
 * row on a top-level screen.
 */
private fun Resources.entry(
    @StringRes title: Int,
    @StringRes subtitle: Int = 0,
    @StringRes screen: Int = 0,
    route: String = "",
    @StringRes screenParent: Int = 0,
    @StringRes screenRoot: Int = 0,
    weight: EntryWeight = EntryWeight.NORMAL,
): SettingsSearchEntry = SettingsSearchEntry(
    title = getString(title),
    subtitle = if (subtitle == 0) "" else getString(subtitle),
    screen = crumb(screenRoot, screenParent, screen),
    route = route,
    weight = weight,
    titleRes = title,
)

/**
 * Builds an entry on a tool's own page. The breadcrumb and the route both come
 * from the tool, so a renamed tool never desynchronises from its entries, and
 * the tool's name is read from [toolTitle] rather than copied here.
 */
private fun Resources.toolEntry(
    tool: ToolbarTool,
    @StringRes title: Int,
    @StringRes subtitle: Int = 0,
    weight: EntryWeight = EntryWeight.NORMAL,
): SettingsSearchEntry = SettingsSearchEntry(
    title = getString(title),
    subtitle = if (subtitle == 0) "" else getString(subtitle),
    screen = crumb(R.string.home_tools_title, toolTitle(tool)),
    route = "tool/${tool.name}",
    weight = weight,
    tool = tool,
    titleRes = title,
)

/** Rows on the Typing screen, in screen order. */
private fun Resources.typingRows(): List<SettingsSearchEntry> {
    fun row(@StringRes title: Int, @StringRes subtitle: Int = 0) =
        entry(title, subtitle, R.string.home_typing_title, "typing")
    return listOfNotNull(
        row(R.string.typing_autocorrect_title, R.string.typing_autocorrect_subtitle),
        row(R.string.typing_autocorrect_confidence_title, R.string.typing_autocorrect_confidence_subtitle),
        row(R.string.typing_undo_autocorrect_title, R.string.typing_undo_autocorrect_subtitle),
        row(R.string.typing_skip_all_caps_title, R.string.typing_skip_all_caps_subtitle),
        row(R.string.typing_block_offensive_title, R.string.typing_block_offensive_subtitle),
        row(R.string.typing_auto_apostrophe_title, R.string.typing_auto_apostrophe_subtitle),
        row(R.string.typing_auto_capitalize_title, R.string.typing_auto_capitalize_subtitle),
        row(R.string.typing_double_space_period_title, R.string.typing_double_space_period_subtitle),
        row(R.string.typing_double_space_tab_title, R.string.typing_double_space_tab_subtitle),
        row(R.string.typing_auto_space_punctuation_title, R.string.typing_auto_space_punctuation_subtitle),
        row(R.string.typing_space_after_suggestion_title, R.string.typing_space_after_suggestion_subtitle),
        row(R.string.typing_wrap_selection_title, R.string.typing_wrap_selection_subtitle),
        row(R.string.typing_shift_recase_title, R.string.typing_shift_recase_subtitle),
        row(R.string.typing_suggestions_title, R.string.typing_suggestions_subtitle),
        row(R.string.typing_punctuation_suggestions_title, R.string.typing_punctuation_suggestions_subtitle),
        row(R.string.typing_suggestions_first_title, R.string.typing_suggestions_first_subtitle),
        row(R.string.typing_primary_center_title, R.string.typing_primary_center_subtitle),
        row(R.string.typing_contact_names_title, R.string.typing_contact_names_subtitle),
        row(R.string.typing_contact_emails_title, R.string.typing_contact_emails_subtitle),
        row(R.string.typing_contact_emails_in_email_fields_title, R.string.typing_contact_emails_in_email_fields_subtitle),
        row(R.string.typing_app_names_title, R.string.typing_app_names_subtitle),
        row(R.string.typing_inline_emoji_search_title, R.string.typing_inline_emoji_search_subtitle),
        row(R.string.typing_inline_autofill_title, R.string.typing_inline_autofill_subtitle),
        row(R.string.typing_smart_replies_title, R.string.typing_smart_replies_subtitle),
        // Personal dictionary, Custom dictionaries and Suggestion blacklist are
        // rows on this screen too, but each only opens a screen of its own. They
        // are indexed once, in sectionRows, pointing straight at that screen: a
        // second entry that lands on Typing and flashes the row would be a
        // near-identical result one line below the useful one.
        row(R.string.typing_smart_chips_title, R.string.typing_smart_chips_subtitle),
        row(R.string.typing_smart_calc_title, R.string.typing_smart_calc_subtitle),
        row(R.string.typing_smart_currency_title),
        row(R.string.typing_smart_units_title, R.string.typing_smart_units_subtitle),
        row(R.string.typing_smart_tool_keywords_title, R.string.typing_smart_tool_keywords_subtitle),
        row(R.string.typing_glide_typing_title, R.string.typing_glide_typing_subtitle),
        // Full builds only: the handwriting recognizer is an ML Kit feature.
        if (BuildConfig.ENABLE_ML_KIT_HANDWRITING) {
            row(R.string.typing_letter_swipe_action_title, R.string.typing_letter_swipe_action_subtitle)
        } else {
            null
        },
        row(R.string.typing_space_glide_multiword_title, R.string.typing_space_glide_multiword_subtitle),
        row(R.string.typing_swipe_start_distance_title, R.string.typing_swipe_start_distance_subtitle),
        row(R.string.typing_trail_width_title, R.string.typing_trail_width_subtitle),
        row(R.string.typing_trail_length_title, R.string.typing_trail_length_subtitle),
        row(R.string.typing_trail_opacity_title),
        row(R.string.typing_spacebar_language_arrows_title, R.string.typing_spacebar_language_arrows_subtitle),
        row(R.string.typing_spacebar_display_title, R.string.typing_spacebar_display_subtitle),
        row(R.string.typing_spacebar_text_label),
        row(R.string.typing_backspace_swipe_title, R.string.typing_backspace_swipe_subtitle),
        row(R.string.typing_shift_enter_title, R.string.typing_shift_enter_subtitle),
        row(R.string.typing_volume_cursor_title, R.string.typing_volume_cursor_subtitle),
        row(R.string.typing_volume_cursor_media_title, R.string.typing_volume_cursor_media_subtitle),
        row(R.string.typing_hardware_input_title, R.string.typing_hardware_input_subtitle),
        row(R.string.typing_hw_shortcuts_title, R.string.typing_hw_shortcuts_subtitle),
        row(R.string.typing_hw_panel_nav_title, R.string.typing_hw_panel_nav_subtitle),
        row(R.string.typing_hw_esc_title, R.string.typing_hw_esc_subtitle),
        row(R.string.typing_hw_suggestion_hotkeys_title, R.string.typing_hw_suggestion_hotkeys_subtitle),
        row(R.string.typing_hw_auto_show_title, R.string.typing_hw_auto_show_subtitle),
    )
}

/** Rows on the Key press screen. */
private fun Resources.keyPressRows(): List<SettingsSearchEntry> {
    fun row(@StringRes title: Int, @StringRes subtitle: Int = 0) =
        entry(title, subtitle, R.string.home_keypress_title, "keypress")
    return listOf(
        row(R.string.keypress_haptics_title, R.string.keypress_haptics_subtitle),
        row(R.string.keypress_haptic_strength_title, R.string.keypress_haptic_strength_subtitle),
        row(R.string.keypress_haptic_intensity_title, R.string.keypress_haptic_intensity_subtitle),
        row(R.string.keypress_long_press_haptics_title, R.string.keypress_long_press_haptics_subtitle),
        row(R.string.keypress_long_press_release_title, R.string.keypress_long_press_release_subtitle),
        row(R.string.keypress_vibrate_space_title, R.string.keypress_vibrate_space_subtitle),
        row(R.string.keypress_vibrate_delete_swipe_title, R.string.keypress_vibrate_delete_swipe_subtitle),
        row(R.string.keypress_vibrate_repeat_title, R.string.keypress_vibrate_repeat_subtitle),
        row(R.string.keypress_dnd_mute_title, R.string.keypress_dnd_mute_subtitle),
        row(R.string.keypress_popup_title, R.string.keypress_popup_subtitle),
        row(R.string.keypress_popup_numeric_title, R.string.keypress_popup_numeric_subtitle),
        row(R.string.keypress_popup_min_duration_title, R.string.keypress_popup_min_duration_subtitle),
        row(R.string.keypress_popup_on_key_title, R.string.keypress_popup_on_key_subtitle),
        row(R.string.keypress_popup_font_size_title, R.string.keypress_popup_font_size_subtitle),
        row(R.string.keypress_popup_height_title, R.string.keypress_popup_height_subtitle),
        row(R.string.keypress_long_press_delay_title, R.string.keypress_long_press_delay_subtitle),
        row(R.string.keypress_key_repeat_title, R.string.keypress_key_repeat_subtitle),
        row(R.string.keypress_caps_lock_title, R.string.keypress_caps_lock_subtitle),
        row(R.string.keypress_long_press_hints_title, R.string.keypress_long_press_hints_subtitle),
        row(R.string.keypress_all_accents_title, R.string.keypress_all_accents_subtitle),
        row(R.string.keypress_currency_keys_title),
        row(R.string.keypress_ctrl_raw_title, R.string.keypress_ctrl_raw_subtitle),
        row(R.string.keypress_hold_a_title, R.string.keypress_hold_a_subtitle),
        row(R.string.keypress_hold_c_title, R.string.keypress_hold_c_subtitle),
        row(R.string.keypress_hold_x_title, R.string.keypress_hold_x_subtitle),
        row(R.string.keypress_hold_v_title, R.string.keypress_hold_v_subtitle),
        row(R.string.keypress_hold_z_title, R.string.keypress_hold_z_subtitle),
        row(R.string.keypress_hold_y_title, R.string.keypress_hold_y_subtitle),
    )
}

/** Rows on Appearance and the two screens that hang off it. */
private fun Resources.photoRows(): List<SettingsSearchEntry> {
    fun services(@StringRes title: Int, @StringRes subtitle: Int = 0) = entry(
        title, subtitle, R.string.photo_services_title, "photos",
        screenParent = R.string.home_screen_theme_edit_title,
        screenRoot = R.string.home_appearance_title,
    )
    fun rotation(@StringRes title: Int, @StringRes subtitle: Int = 0) = entry(
        title, subtitle, R.string.photo_rotation_title, "photo_rotation",
        screenParent = R.string.home_screen_theme_edit_title,
        screenRoot = R.string.home_appearance_title,
    )
    fun library(@StringRes title: Int, @StringRes subtitle: Int = 0) = entry(
        title, subtitle, R.string.photo_library_title, "photo_library",
        screenParent = R.string.photo_rotation_title,
        screenRoot = R.string.home_appearance_title,
    )
    return listOf(
        services(R.string.photo_unsplash_key_label),
        services(R.string.photo_pexels_key_label),
        rotation(R.string.photo_rotation_on_title, R.string.photo_rotation_on_subtitle),
        rotation(R.string.photo_rotation_interval_title),
        rotation(R.string.photo_rotation_shuffle_title, R.string.photo_rotation_shuffle_subtitle),
        rotation(R.string.photo_rotation_source_saved_title, R.string.photo_rotation_source_saved_subtitle),
        rotation(R.string.photo_rotation_source_online_title, R.string.photo_rotation_source_online_subtitle),
        rotation(R.string.photo_rotation_topics_title, R.string.photo_rotation_topics_subtitle),
        rotation(R.string.photo_rotation_terms_label),
        rotation(R.string.photo_rotation_safe_title, R.string.photo_rotation_safe_subtitle),
        rotation(R.string.photo_rotation_wide_title, R.string.photo_rotation_wide_subtitle),
        rotation(R.string.photo_rotation_metered_title, R.string.photo_rotation_metered_subtitle),
        rotation(R.string.photo_rotation_pool_title),
        rotation(R.string.photo_rotation_scope_title),
        rotation(
            R.string.photo_rotation_delete_downloads_title,
            R.string.photo_rotation_delete_downloads_subtitle,
        ),
        library(R.string.photo_add_device_title, R.string.photo_add_device_subtitle),
        library(R.string.photo_storage_title),
    )
}

private fun Resources.appearanceRows(): List<SettingsSearchEntry> {
    fun theme(@StringRes title: Int, @StringRes subtitle: Int = 0) = entry(
        title, subtitle, R.string.home_screen_themes_title, "themes",
        screenParent = R.string.home_appearance_title,
    )
    fun icon(@StringRes title: Int, @StringRes subtitle: Int = 0) = entry(
        title, subtitle, R.string.home_screen_icons_title, "icons",
        screenParent = R.string.home_appearance_title,
    )
    fun row(@StringRes title: Int, @StringRes subtitle: Int = 0) =
        entry(title, subtitle, R.string.home_appearance_title, "appearance")
    return listOf(
        // Keyboard themes, Keyboard font and Icons are rows here, but each
        // opens its own screen. They are indexed once in sectionRows,
        // pointing at that screen.
        theme(R.string.theme_auto_title, R.string.theme_auto_subtitle),
        theme(R.string.theme_auto_light_from_title),
        theme(R.string.theme_auto_dark_from_title),
        theme(R.string.theme_background_image_landscape_title),
        icon(R.string.plugins_icons_pack_title),
        icon(R.string.plugins_icons_import_title, R.string.plugins_icons_import_subtitle),
        icon(R.string.plugins_icons_reset_title, R.string.plugins_icons_reset_subtitle),
        icon(R.string.plugins_icons_picker_use_svg_action),
        row(R.string.appearance_key_corner_radius_title, R.string.appearance_key_corner_radius_subtitle),
        row(R.string.appearance_key_label_size_title, R.string.appearance_key_label_size_subtitle),
        row(R.string.appearance_toolbar_show_title, R.string.appearance_toolbar_show_subtitle),
        row(R.string.appearance_toolbar_swipe_down_title, R.string.appearance_toolbar_swipe_down_subtitle),
        row(R.string.appearance_toolbar_hardware_only_title, R.string.appearance_toolbar_hardware_only_subtitle),
        row(R.string.appearance_toolbar_rtl_title, R.string.appearance_toolbar_rtl_subtitle),
        row(R.string.appearance_toolbar_spread_title, R.string.appearance_toolbar_spread_subtitle),
        row(R.string.appearance_toolbar_height_title, R.string.appearance_toolbar_height_subtitle),
        row(R.string.appearance_toolbar_scroll_title, R.string.appearance_toolbar_scroll_subtitle),
        row(R.string.appearance_toolbar_labels_title, R.string.appearance_toolbar_labels_subtitle),
        row(R.string.appearance_toolbar_label_size_title, R.string.appearance_toolbar_label_size_subtitle),
        row(R.string.home_reset_pinned_tools_title, R.string.home_reset_pinned_tools_subtitle),
        row(R.string.appearance_tool_circle_title, R.string.appearance_tool_circle_subtitle),
        row(R.string.appearance_toolbox_layout_title, R.string.appearance_toolbox_layout_subtitle),
        row(R.string.appearance_toolbox_columns_title, R.string.appearance_toolbox_columns_subtitle),
        row(R.string.appearance_toolbox_pill_columns_title, R.string.appearance_toolbox_pill_columns_subtitle),
        row(R.string.appearance_toolbox_pill_filled_title, R.string.appearance_toolbox_pill_filled_subtitle),
        row(R.string.appearance_toolbox_paginate_title, R.string.appearance_toolbox_paginate_subtitle),
        row(R.string.appearance_toolbox_page_size_title, R.string.appearance_toolbox_page_size_subtitle),
    )
}

/** Rows on Layout & size, plus the two rows that moved off it. */
private fun Resources.layoutRows(): List<SettingsSearchEntry> {
    fun row(@StringRes title: Int, @StringRes subtitle: Int = 0) =
        entry(title, subtitle, R.string.home_layout_title, "layout")
    return listOf(
        row(R.string.layout_number_row_title, R.string.layout_number_row_subtitle),
        row(R.string.layout_number_row_height_title, R.string.layout_number_row_height_subtitle),
        row(R.string.layout_number_row_in_symbols_title, R.string.layout_number_row_in_symbols_subtitle),
        row(R.string.layout_numeral_scope_title, R.string.layout_numeral_scope_subtitle),
        row(R.string.layout_key_height_title, R.string.layout_key_height_subtitle),
        row(R.string.layout_bottom_row_height_title, R.string.layout_bottom_row_height_subtitle),
        row(R.string.layout_side_padding_title, R.string.layout_side_padding_subtitle),
        row(R.string.layout_key_spacing_title, R.string.layout_key_spacing_subtitle),
        row(R.string.layout_keyboard_scale_title, R.string.layout_keyboard_scale_subtitle),
        row(R.string.layout_bottom_padding_title, R.string.layout_bottom_padding_subtitle),
        row(R.string.layout_keyboard_width_title, R.string.layout_keyboard_width_subtitle),
        row(R.string.layout_keyboard_position_title, R.string.layout_keyboard_position_info),
        row(R.string.layout_variant_follows_portrait_label),
        row(R.string.layout_font_size_title),
        row(R.string.layout_follow_portrait_title),
        row(R.string.layout_one_handed_title, R.string.layout_one_handed_subtitle),
        row(R.string.layout_split_title, R.string.layout_split_subtitle),
        row(R.string.layout_split_gap_title, R.string.layout_split_gap_subtitle),
        row(R.string.layout_floating_title, R.string.layout_floating_subtitle),
        row(R.string.layout_floating_width_title, R.string.layout_floating_width_subtitle),
        row(R.string.layout_comma_emoji_title, R.string.layout_comma_emoji_subtitle),
        row(R.string.layout_globe_emoji_title, R.string.layout_globe_emoji_subtitle),
        row(R.string.layout_swap_comma_globe_title, R.string.layout_swap_comma_globe_subtitle),
        entry(R.string.layout_editor_import_title, R.string.layout_editor_import_subtitle, R.string.home_keymaps_title, "keymaps"),
        // Per language now, so search lands on the language list rather than
        // on a switch that no longer exists at the top level.
        entry(R.string.languages_conjunct_backspace_title, 0, R.string.home_languages_title, "languages"),
    )
}

/** Rows on the Emoji screen. */
private fun Resources.emojiRows(): List<SettingsSearchEntry> {
    fun row(@StringRes title: Int, @StringRes subtitle: Int = 0) =
        entry(title, subtitle, R.string.home_emoji_title, "emoji")
    return listOf(
        row(R.string.langemoji_emoji_toolbar_title, R.string.langemoji_emoji_toolbar_subtitle),
        row(R.string.langemoji_emoji_full_bleed_title, R.string.langemoji_emoji_full_bleed_subtitle),
        row(R.string.langemoji_emoji_prediction_title, R.string.langemoji_emoji_prediction_subtitle),
        row(R.string.langemoji_emoji_insert_mode_title, R.string.langemoji_emoji_insert_mode_subtitle),
        row(R.string.langemoji_emoji_tab_mode_title, R.string.langemoji_emoji_tab_mode_subtitle),
        row(R.string.langemoji_emoji_clear_recents_title, R.string.langemoji_emoji_clear_recents_subtitle),
        row(R.string.langemoji_emoji_kaomoji_title, R.string.langemoji_emoji_kaomoji_subtitle),
        row(R.string.langemoji_emoji_long_press_name_title, R.string.langemoji_emoji_long_press_name_subtitle),
        row(R.string.langemoji_emoji_animated_title, R.string.langemoji_emoji_animated_subtitle),
        row(R.string.langemoji_emoji_sticker_title, R.string.langemoji_emoji_sticker_subtitle),
        row(R.string.langemoji_emoji_row_title, R.string.langemoji_emoji_bar_mode_subtitle),
        row(R.string.langemoji_emoji_bar_content_title, R.string.langemoji_emoji_bar_content_subtitle),
        row(R.string.langemoji_emoji_bar_count_title, R.string.langemoji_emoji_bar_count_subtitle),
        row(R.string.langemoji_emoji_bar_scroll_title, R.string.langemoji_emoji_bar_scroll_subtitle),
        row(R.string.langemoji_emoji_font_title, R.string.langemoji_emoji_font_subtitle),
        row(R.string.langemoji_emoji_skin_tone_title, R.string.langemoji_emoji_skin_tone_subtitle),
        row(R.string.langemoji_emoji_tone_override_title, R.string.langemoji_emoji_tone_override_subtitle),
        row(R.string.langemoji_emoji_close_after_insert_title, R.string.langemoji_emoji_close_after_insert_subtitle),
        // One title in every configuration now: an emoji the chosen font lacks
        // is drawn in the phone's own font rather than hidden, so the toggle is
        // always about the phone.
        row(R.string.langemoji_emoji_hide_unrenderable_title, R.string.langemoji_emoji_hide_unrenderable_subtitle),
    )
}

/** Rows on the tool pages, from Emoji through One-handed mode. */
private fun Resources.toolPageRowsA(): List<SettingsSearchEntry> = listOf(
    toolEntry(ToolbarTool.EMOJI, R.string.tooldetail_emoji_toolbar_title, R.string.tooldetail_emoji_toolbar_subtitle),
    toolEntry(ToolbarTool.EMOJI, R.string.tooldetail_emoji_all_title, R.string.tooldetail_emoji_all_subtitle, weight = EntryWeight.MIRROR),
    toolEntry(ToolbarTool.CLIPBOARD, R.string.tooldetail_clipboard_history_title, R.string.tooldetail_clipboard_history_subtitle),
    toolEntry(
        ToolbarTool.CLIPBOARD,
        R.string.tooldetail_clipboard_suggest_recent_title,
        R.string.tooldetail_clipboard_suggest_recent_subtitle,
    ),
    toolEntry(ToolbarTool.CLIPBOARD, R.string.tooldetail_clipboard_toast_title, R.string.tooldetail_clipboard_toast_subtitle),
    toolEntry(ToolbarTool.CLIPBOARD, R.string.tooldetail_clipboard_expiry_title, R.string.tooldetail_clipboard_expiry_subtitle),
    toolEntry(ToolbarTool.CLIPBOARD, R.string.tooldetail_clipboard_max_title, R.string.tooldetail_clipboard_max_subtitle),
    toolEntry(ToolbarTool.CLIPBOARD, R.string.tooldetail_clipboard_sensitive_title, R.string.tooldetail_clipboard_sensitive_subtitle),
    toolEntry(
        ToolbarTool.CLIPBOARD,
        R.string.tooldetail_clipboard_detect_sensitive_title,
        R.string.tooldetail_clipboard_detect_sensitive_subtitle,
    ),
    toolEntry(
        ToolbarTool.CLIPBOARD,
        R.string.tooldetail_clipboard_sensitive_expiry_title,
        R.string.tooldetail_clipboard_sensitive_expiry_subtitle,
    ),
    toolEntry(ToolbarTool.CLIPBOARD, R.string.tooldetail_clipboard_bottom_row_title, R.string.tooldetail_clipboard_bottom_row_subtitle),
    toolEntry(ToolbarTool.CLIPBOARD, R.string.tooldetail_clipboard_pinned_last_title, R.string.tooldetail_clipboard_pinned_last_subtitle),
    toolEntry(ToolbarTool.CLIPBOARD, R.string.tooldetail_clipboard_search_title, R.string.tooldetail_clipboard_search_subtitle),
    toolEntry(
        ToolbarTool.CLIPBOARD,
        R.string.tooldetail_clipboard_password_paste_title,
        R.string.tooldetail_clipboard_password_paste_subtitle,
    ),
    toolEntry(
        ToolbarTool.CLIPBOARD,
        R.string.tooldetail_clipboard_link_previews_title,
        R.string.tooldetail_clipboard_link_previews_subtitle,
    ),
    toolEntry(ToolbarTool.SPLIT, R.string.tooldetail_split_gap_title, R.string.tooldetail_split_gap_subtitle),
    toolEntry(
        ToolbarTool.SPLIT,
        R.string.tooldetail_layout_nav_title,
        R.string.tooldetail_layout_nav_split_subtitle,
        weight = EntryWeight.MIRROR,
    ),
    toolEntry(ToolbarTool.FLOATING, R.string.tooldetail_floating_width_title, R.string.tooldetail_floating_width_subtitle),
    toolEntry(
        ToolbarTool.FLOATING,
        R.string.tooldetail_layout_nav_title,
        R.string.tooldetail_layout_nav_floating_subtitle,
        weight = EntryWeight.MIRROR,
    ),
    toolEntry(ToolbarTool.FLASHLIGHT, R.string.tooldetail_flashlight_auto_off_title, R.string.tooldetail_flashlight_auto_off_subtitle),
    toolEntry(ToolbarTool.COMPASS, R.string.tooldetail_compass_degrees_title, R.string.tooldetail_compass_degrees_subtitle),
    toolEntry(ToolbarTool.COMPASS, R.string.tooldetail_compass_qibla_title, R.string.tooldetail_compass_qibla_subtitle),
    toolEntry(ToolbarTool.LEVEL, R.string.tooldetail_level_angles_title, R.string.tooldetail_level_angles_subtitle),
    toolEntry(ToolbarTool.UNDO, R.string.tooldetail_redo_ctrl_y_title, R.string.tooldetail_redo_ctrl_y_subtitle),
    toolEntry(ToolbarTool.REDO, R.string.tooldetail_redo_ctrl_y_title, R.string.tooldetail_redo_ctrl_y_subtitle),
    toolEntry(ToolbarTool.MOON_PHASE, R.string.tooldetail_moon_southern_title, R.string.tooldetail_moon_southern_subtitle),
    toolEntry(ToolbarTool.WEATHER, R.string.tooldetail_weather_fahrenheit_title, R.string.tooldetail_weather_fahrenheit_subtitle),
    toolEntry(ToolbarTool.CALENDAR, R.string.tooldetail_calendar_first_title, R.string.tooldetail_calendar_first_subtitle),
    toolEntry(ToolbarTool.CALENDAR, R.string.tooldetail_calendar_second_title, R.string.tooldetail_calendar_second_subtitle),
    toolEntry(ToolbarTool.CALENDAR, R.string.toolai_weekend_title, R.string.toolai_weekend_subtitle),
    toolEntry(ToolbarTool.CALENDAR, R.string.tooldetail_calendar_hijri_title, R.string.tooldetail_calendar_hijri_subtitle),
    toolEntry(ToolbarTool.CAMERA, R.string.tooldetail_camera_front_title, R.string.tooldetail_camera_front_subtitle),
    toolEntry(ToolbarTool.CAMERA, R.string.tooldetail_camera_mirror_title, R.string.tooldetail_camera_mirror_subtitle),
    toolEntry(ToolbarTool.CAMERA, R.string.tooldetail_camera_fullframe_title, R.string.tooldetail_camera_fullframe_subtitle),
    toolEntry(ToolbarTool.CAMERA, R.string.tooldetail_camera_gallery_title, R.string.tooldetail_camera_gallery_subtitle),
    toolEntry(ToolbarTool.CAMERA, R.string.tooldetail_camera_shutter_title, R.string.tooldetail_camera_shutter_subtitle),
    toolEntry(ToolbarTool.CAMERA, R.string.tooldetail_camera_haptics_title, R.string.tooldetail_camera_haptics_subtitle),
    toolEntry(ToolbarTool.DICTIONARY, R.string.tooldetail_dictionary_auto_title, R.string.tooldetail_dictionary_auto_subtitle),
    toolEntry(ToolbarTool.TEXT_EDIT, R.string.tooldetail_text_edit_repeat_title, R.string.tooldetail_text_edit_repeat_subtitle),
    toolEntry(ToolbarTool.NUMPAD, R.string.tooldetail_numpad_calc_title, R.string.tooldetail_numpad_calc_subtitle),
    toolEntry(ToolbarTool.INCOGNITO, R.string.tooldetail_incognito_learning_title, R.string.tooldetail_incognito_learning_subtitle),
    toolEntry(ToolbarTool.INCOGNITO, R.string.tooldetail_incognito_clipboard_title, R.string.tooldetail_incognito_clipboard_subtitle),
    toolEntry(ToolbarTool.INCOGNITO, R.string.tooldetail_incognito_auto_title, R.string.tooldetail_incognito_auto_subtitle),
    toolEntry(ToolbarTool.POWER_SAVING, R.string.tooldetail_power_now_title, R.string.tooldetail_power_now_subtitle),
    toolEntry(ToolbarTool.POWER_SAVING, R.string.tooldetail_power_trigger_title, R.string.tooldetail_power_trigger_subtitle),
    toolEntry(ToolbarTool.POWER_SAVING, R.string.tooldetail_power_battery_title, R.string.tooldetail_power_battery_subtitle),
    toolEntry(ToolbarTool.POWER_SAVING, R.string.tooldetail_power_charging_title, R.string.tooldetail_power_charging_subtitle),
    toolEntry(ToolbarTool.POWER_SAVING, R.string.tooldetail_power_drop_haptics_title, R.string.tooldetail_power_drop_haptics_subtitle),
    toolEntry(ToolbarTool.POWER_SAVING, R.string.tooldetail_power_drop_sound_title, R.string.tooldetail_power_drop_sound_subtitle),
    toolEntry(ToolbarTool.POWER_SAVING, R.string.tooldetail_power_drop_anim_title, R.string.tooldetail_power_drop_anim_subtitle),
    toolEntry(ToolbarTool.POWER_SAVING, R.string.tooldetail_power_drop_trail_title, R.string.tooldetail_power_drop_trail_subtitle),
    toolEntry(ToolbarTool.POWER_SAVING, R.string.tooldetail_power_drop_chips_title, R.string.tooldetail_power_drop_chips_subtitle),
    toolEntry(ToolbarTool.POWER_SAVING, R.string.tooldetail_power_drop_network_title, R.string.tooldetail_power_drop_network_subtitle),
    toolEntry(
        ToolbarTool.POWER_SAVING,
        R.string.tooldetail_power_drop_screenshot_title,
        R.string.tooldetail_power_drop_screenshot_subtitle,
    ),
    toolEntry(ToolbarTool.POWER_SAVING, R.string.tooldetail_power_drop_models_title, R.string.tooldetail_power_drop_models_subtitle),
    // The tool screen's own "Autocorrect" toggle is not listed: toolRows
    // already indexes the tool under that title and route. A row named after
    // the tool holding it is always a duplicate result, never a second one.
    toolEntry(
        ToolbarTool.AUTOCORRECT,
        R.string.tooldetail_typing_nav_title,
        R.string.tooldetail_typing_nav_subtitle,
        weight = EntryWeight.MIRROR,
    ),
    toolEntry(
        ToolbarTool.SOUND_HAPTICS,
        R.string.tooldetail_keypress_nav_title,
        R.string.tooldetail_keypress_nav_subtitle,
        weight = EntryWeight.MIRROR,
    ),
    toolEntry(ToolbarTool.HANDWRITING, R.string.tooldetail_handwriting_stylus_title, R.string.tooldetail_handwriting_stylus_subtitle),
    toolEntry(
        ToolbarTool.HANDWRITING,
        R.string.tooldetail_handwriting_auto_space_title,
        R.string.tooldetail_handwriting_auto_space_subtitle,
    ),
    toolEntry(ToolbarTool.HANDWRITING, R.string.tooldetail_handwriting_pause_title, R.string.tooldetail_handwriting_pause_subtitle),
    toolEntry(
        ToolbarTool.THEMES,
        R.string.tooldetail_themes_nav_title,
        R.string.tooldetail_themes_nav_subtitle,
        weight = EntryWeight.MIRROR,
    ),
    toolEntry(
        ToolbarTool.ONE_HANDED,
        R.string.tooldetail_layout_nav_title,
        R.string.tooldetail_layout_nav_one_handed_subtitle,
        weight = EntryWeight.MIRROR,
    ),
    toolEntry(ToolbarTool.TRANSLATE, R.string.tooldetail_translate_key_label, R.string.tooldetail_translate_key_hint),
)

/** Rows on the tool pages, from Translate through the AI tool. */
private fun Resources.toolPageRowsB(): List<SettingsSearchEntry> = listOf(
    toolEntry(ToolbarTool.STICKER, R.string.tooldetail_sticker_packs_title, R.string.tooldetail_sticker_packs_subtitle),
    toolEntry(ToolbarTool.GIF, R.string.tooldetail_media_full_bleed_title, R.string.tooldetail_media_full_bleed_subtitle),
    toolEntry(ToolbarTool.STICKER, R.string.tooldetail_media_full_bleed_title, R.string.tooldetail_media_full_bleed_subtitle),
    toolEntry(ToolbarTool.GIF, R.string.tooldetail_media_klipy_label, R.string.tooldetail_media_klipy_hint),
    toolEntry(ToolbarTool.STICKER, R.string.tooldetail_media_klipy_label, R.string.tooldetail_media_klipy_hint),
    toolEntry(ToolbarTool.GIF, R.string.tooldetail_media_giphy_label, R.string.tooldetail_media_giphy_hint),
    toolEntry(ToolbarTool.STICKER, R.string.tooldetail_media_giphy_label, R.string.tooldetail_media_giphy_hint),
    // Each send mode is indexed under the one tool whose panel it governs; the
    // other page no longer draws it.
    toolEntry(ToolbarTool.STICKER, R.string.tooldetail_media_sticker_send_title, R.string.tooldetail_media_sticker_send_subtitle),
    toolEntry(ToolbarTool.GIF, R.string.tooldetail_media_gif_send_title, R.string.tooldetail_media_gif_send_subtitle),
    toolEntry(ToolbarTool.WEB_SEARCH, R.string.tooldetail_search_key_label, R.string.tooldetail_search_key_hint),
    toolEntry(ToolbarTool.IMAGE_SEARCH, R.string.tooldetail_search_key_label, R.string.tooldetail_search_key_hint),
    toolEntry(ToolbarTool.WEB_SEARCH, R.string.tooldetail_search_safe_title, R.string.tooldetail_search_safe_subtitle),
    toolEntry(ToolbarTool.IMAGE_SEARCH, R.string.tooldetail_search_safe_title, R.string.tooldetail_search_safe_subtitle),
    toolEntry(ToolbarTool.WEB_SEARCH, R.string.tooldetail_search_count_title, R.string.tooldetail_search_count_subtitle),
    toolEntry(ToolbarTool.IMAGE_SEARCH, R.string.tooldetail_search_count_title, R.string.tooldetail_search_count_subtitle),
    toolEntry(ToolbarTool.OCR, R.string.tooldetail_ocr_select_all_title, R.string.tooldetail_ocr_select_all_subtitle),
    toolEntry(ToolbarTool.QR_SCAN, R.string.tooldetail_qr_scan_auto_title, R.string.tooldetail_qr_scan_auto_subtitle),
    toolEntry(ToolbarTool.QR_SCAN, R.string.tooldetail_qr_scan_haptics_title, R.string.tooldetail_qr_scan_haptics_subtitle),
    toolEntry(ToolbarTool.DOC_SCAN, R.string.tooldetail_doc_scan_gallery_title, R.string.tooldetail_doc_scan_gallery_subtitle),
    toolEntry(ToolbarTool.VOICE, R.string.tooldetail_voice_strip_title, R.string.tooldetail_voice_strip_subtitle),
    toolEntry(ToolbarTool.VOICE, R.string.tooldetail_voice_continuous_title, R.string.tooldetail_voice_continuous_subtitle),
    toolEntry(ToolbarTool.VOICE, R.string.tooldetail_voice_punctuation_title, R.string.tooldetail_voice_punctuation_subtitle),
    toolEntry(ToolbarTool.GRAMMAR, R.string.tooldetail_grammar_dialect_title, R.string.tooldetail_grammar_dialect_subtitle),
    toolEntry(ToolbarTool.GRAMMAR, R.string.tooldetail_grammar_debounce_title, R.string.tooldetail_grammar_debounce_subtitle),
    toolEntry(ToolbarTool.GRAMMAR, R.string.tooldetail_grammar_system_title, R.string.tooldetail_grammar_system_subtitle),
    toolEntry(ToolbarTool.WIKIPEDIA, R.string.tooldetail_wiki_language_label, R.string.tooldetail_wiki_language_hint),
    toolEntry(ToolbarTool.WIKIPEDIA, R.string.tooldetail_wiki_markdown_title, R.string.tooldetail_wiki_markdown_subtitle),
    toolEntry(ToolbarTool.CALCULATOR, R.string.tooldetail_calc_smart_title, R.string.tooldetail_calc_smart_subtitle),
    toolEntry(ToolbarTool.CALCULATOR, R.string.tooldetail_calc_degrees_title, R.string.tooldetail_calc_degrees_subtitle),
    toolEntry(ToolbarTool.CALCULATOR, R.string.tooldetail_calc_precision_title, R.string.tooldetail_calc_precision_subtitle),
    toolEntry(ToolbarTool.UNIT_CONVERT, R.string.tooldetail_units_smart_title, R.string.tooldetail_units_smart_subtitle),
    toolEntry(ToolbarTool.CURRENCY, R.string.tooldetail_currency_smart_title),
    toolEntry(ToolbarTool.CURRENCY, R.string.tooldetail_currency_decimals_title, R.string.tooldetail_currency_decimals_subtitle),
    toolEntry(ToolbarTool.CURRENCY, R.string.tooldetail_currency_refresh_title, R.string.tooldetail_currency_refresh_subtitle),
    toolEntry(ToolbarTool.QR_GEN, R.string.tooldetail_qr_gen_size_title, R.string.tooldetail_qr_gen_size_subtitle),
    toolEntry(ToolbarTool.QR_GEN, R.string.tooldetail_qr_gen_send_title, R.string.tooldetail_qr_gen_send_subtitle),
    toolEntry(ToolbarTool.QR_GEN, R.string.tooldetail_qr_gen_gallery_title, R.string.tooldetail_qr_gen_gallery_subtitle),
    toolEntry(ToolbarTool.PASSWORD_GEN, R.string.tooldetail_password_length_title),
    toolEntry(ToolbarTool.PASSWORD_GEN, R.string.tooldetail_password_uppercase_title, R.string.tooldetail_password_uppercase_subtitle),
    toolEntry(ToolbarTool.PASSWORD_GEN, R.string.tooldetail_password_digits_title, R.string.tooldetail_password_digits_subtitle),
    toolEntry(ToolbarTool.PASSWORD_GEN, R.string.tooldetail_password_symbols_title),
    toolEntry(ToolbarTool.PASSWORD_GEN, R.string.tooldetail_password_ambiguous_title, R.string.tooldetail_password_ambiguous_subtitle),
    toolEntry(ToolbarTool.PASSWORD_GEN, R.string.tooldetail_passphrase_words_title),
    toolEntry(ToolbarTool.PASSWORD_GEN, R.string.tooldetail_passphrase_separator_label, R.string.tooldetail_passphrase_separator_hint),
    toolEntry(
        ToolbarTool.PASSWORD_GEN,
        R.string.tooldetail_passphrase_capitalize_title,
        R.string.tooldetail_passphrase_capitalize_subtitle,
    ),
    toolEntry(ToolbarTool.PASSWORD_GEN, R.string.tooldetail_passphrase_digit_title, R.string.tooldetail_passphrase_digit_subtitle),
    toolEntry(ToolbarTool.MODES, R.string.tooldetail_modes_edit_title, R.string.tooldetail_modes_edit_subtitle),
    toolEntry(ToolbarTool.TYPING_TEST, R.string.toolai_typing_seconds_label),
    toolEntry(ToolbarTool.TYPING_TEST, R.string.toolai_typing_words_label),
    toolEntry(ToolbarTool.TYPING_TEST, R.string.toolai_typing_punctuation_title, R.string.toolai_typing_punctuation_subtitle),
    toolEntry(ToolbarTool.TYPING_TEST, R.string.toolai_typing_numbers_title, R.string.toolai_typing_numbers_subtitle),
    toolEntry(ToolbarTool.TYPING_TEST, R.string.toolai_typing_clear_records_title, R.string.toolai_typing_clear_records_subtitle),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_anthropic_key_label, R.string.toolai_ai_anthropic_key_hint),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_model_label),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_openai_key_label, R.string.toolai_ai_openai_key_hint),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_gemini_key_label, R.string.toolai_ai_gemini_key_hint),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_xai_key_label, R.string.toolai_ai_xai_key_hint),
    toolEntry(
        ToolbarTool.AI,
        R.string.toolai_ai_deepseek_key_label,
        R.string.toolai_ai_deepseek_key_hint,
    ),
    toolEntry(
        ToolbarTool.AI,
        R.string.toolai_ai_compatible_url_label,
        R.string.toolai_ai_compatible_url_hint,
    ),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_server_address_label, R.string.toolai_ai_ollama_url_hint),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_max_tokens_title, R.string.toolai_ai_max_tokens_subtitle),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_translate_to_label, R.string.toolai_ai_translate_to_hint),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_show_thinking_title, R.string.toolai_ai_show_thinking_subtitle),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_model_picker_title, R.string.toolai_ai_model_picker_subtitle),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_actions_title, R.string.toolai_ai_actions_subtitle),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_diff_title, R.string.toolai_ai_diff_subtitle),
    toolEntry(ToolbarTool.AI, R.string.toolai_ai_history_title, R.string.toolai_ai_history_subtitle),
    toolEntry(ToolbarTool.TRANSLATE, R.string.toolai_translate_into_title, R.string.toolai_translate_into_subtitle),
)

/**
 * Rows on Backup, on the sticker and plugin screens, and on Privacy, Rows &
 * bars, Keyboard modes, Accessibility, About and Tools.
 */
private fun Resources.otherRows(): List<SettingsSearchEntry> {
    fun backup(@StringRes title: Int, @StringRes subtitle: Int) =
        entry(title, subtitle, R.string.home_backup_title, "backup", weight = EntryWeight.MIRROR)
    fun stickerPack(@StringRes title: Int, @StringRes subtitle: Int) = entry(
        title, subtitle, R.string.home_screen_sticker_packs_title, "sticker_packs",
        screenParent = toolTitle(ToolbarTool.STICKER),
        screenRoot = R.string.home_tools_title,
    )
    fun plugin(@StringRes title: Int, @StringRes subtitle: Int) = entry(
        title, subtitle, R.string.home_screen_plugins_title, "plugins",
        screenParent = R.string.home_tools_title,
    )
    fun privacy(@StringRes title: Int, @StringRes subtitle: Int) =
        entry(title, subtitle, R.string.home_privacy_title, "privacy")
    fun mode(@StringRes title: Int, @StringRes subtitle: Int = 0) =
        entry(title, subtitle, R.string.home_screen_mode_edit_title, "modes")
    fun access(@StringRes title: Int, @StringRes subtitle: Int = 0) =
        entry(title, subtitle, R.string.home_accessibility_title, "accessibility")
    fun about(@StringRes title: Int, @StringRes subtitle: Int = 0) =
        entry(title, subtitle, R.string.home_about_title, "about")
    return listOfNotNull(
        backup(R.string.backup_include_secrets_title, R.string.backup_include_secrets_subtitle),
        backup(R.string.backup_section_stickers_label, R.string.backup_include_stickers_subtitle),
        stickerPack(R.string.import_sticker_pack_new_title, R.string.import_sticker_pack_new_subtitle),
        stickerPack(R.string.import_sticker_pack_import_title, R.string.import_sticker_pack_import_subtitle),
        // Lands on the pack list: the editor itself cannot open without an
        // image to edit, so there is nothing to deep-link to.
        stickerPack(R.string.import_sticker_editor_title, R.string.import_sticker_editor_subtitle),
        plugin(R.string.plugins_allow_title, R.string.plugins_allow_subtitle),
        plugin(R.string.tooldetail_plugins_manage_title, R.string.tooldetail_plugins_manage_subtitle),
        plugin(R.string.plugins_install_file_title, R.string.plugins_install_file_subtitle),
        privacy(R.string.privacy_learn_typing_title, R.string.privacy_learn_typing_subtitle),
        privacy(R.string.privacy_system_dictionary_title, R.string.privacy_system_dictionary_subtitle),
        privacy(R.string.privacy_dict_shortcuts_title, R.string.privacy_dict_shortcuts_subtitle),
        privacy(R.string.privacy_incognito_title, R.string.privacy_incognito_subtitle),
        privacy(R.string.privacy_auto_incognito_title, R.string.privacy_auto_incognito_subtitle),
        privacy(R.string.privacy_backup_title, R.string.privacy_backup_subtitle),
        entry(R.string.rows_symbol_row_title, R.string.rows_symbol_row_subtitle, R.string.home_rows_title, "rows"),
        entry(R.string.modes_drag_edits_title, R.string.modes_drag_edits_subtitle, R.string.home_modes_title, "modes"),
        mode(R.string.modes_name_label, R.string.modes_name_hint),
        mode(R.string.modes_emoji_row_title, R.string.modes_active_subtitle),
        mode(R.string.modes_symbol_row_title),
        mode(R.string.modes_pinned_tools_title, R.string.modes_pinned_tools_subtitle),
        mode(R.string.modes_pinned_behaviour_title, R.string.modes_pinned_behaviour_append_subtitle),
        mode(R.string.modes_toolbox_order_title, R.string.modes_toolbox_order_subtitle),
        mode(R.string.modes_symbol_sets_title, R.string.modes_symbol_sets_subtitle),
        access(R.string.accessibility_color_vision_title, R.string.accessibility_color_vision_subtitle),
        access(R.string.accessibility_high_contrast_title, R.string.accessibility_high_contrast_subtitle),
        access(R.string.accessibility_key_outlines_title, R.string.accessibility_key_outlines_subtitle),
        access(R.string.accessibility_bold_labels_title, R.string.accessibility_bold_labels_subtitle),
        access(R.string.accessibility_readable_font_title),
        access(R.string.accessibility_text_size_title, R.string.accessibility_text_size_subtitle),
        access(R.string.accessibility_keyboard_font_title, R.string.accessibility_keyboard_font_subtitle),
        access(R.string.accessibility_reduce_motion_title, R.string.accessibility_reduce_motion_subtitle),
        access(R.string.accessibility_talkback_title, R.string.accessibility_talkback_subtitle),
        access(R.string.accessibility_passthrough_service_title),
        access(R.string.accessibility_debounce_title, R.string.accessibility_debounce_subtitle_off),
        access(R.string.accessibility_long_press_title, R.string.accessibility_long_press_subtitle),
        access(R.string.accessibility_key_size_title, R.string.accessibility_key_size_subtitle),
        access(R.string.accessibility_haptics_title, R.string.accessibility_haptics_subtitle),
        about(R.string.about_version_title),
        about(R.string.about_licence_title),
        about(R.string.about_source_title),
        // Open-source licences has its own screen, indexed once in sectionRows.
        about(R.string.about_user_guide_title),
        about(R.string.about_privacy_policy_title, R.string.about_privacy_policy_subtitle),
        entry(R.string.about_diagnostics_title, R.string.about_diagnostics_subtitle, R.string.home_about_title, "debug_log"),
        about(R.string.about_dictionaries_title, R.string.about_dictionaries_subtitle),
        // Play builds only: nowhere else has an Updates group to land on. The
        // check row is indexed under its resting name — the resource is what a
        // search result matches on, and the row wears three other names while
        // a download is running.
        if (BuildConfig.ENABLE_PLAY_STORE) {
            about(R.string.update_row_check_title, R.string.update_row_check_subtitle)
        } else {
            null
        },
        if (BuildConfig.ENABLE_PLAY_STORE) {
            about(R.string.update_row_prompts_title, R.string.update_row_prompts_subtitle)
        } else {
            null
        },
        entry(R.string.tools_colored_icons_title, R.string.tools_colored_icons_subtitle, R.string.home_tools_title, "tools"),
        entry(R.string.tools_gradient_icons_title, R.string.tools_gradient_icons_subtitle, R.string.home_tools_title, "tools"),
    )
}

/** The destinations: the settings home's own list, plus the screens that hang off it. */
private fun Resources.sectionRows(): List<SettingsSearchEntry> {
    fun home(@StringRes title: Int, @StringRes subtitle: Int = 0, route: String) =
        entry(title, subtitle, CommonR.string.common_settings, route, weight = EntryWeight.SECTION)
    fun under(@StringRes title: Int, @StringRes subtitle: Int, @StringRes screen: Int, route: String) =
        entry(title, subtitle, screen, route, weight = EntryWeight.SECTION)
    return listOf(
        home(R.string.home_typing_title, R.string.home_typing_subtitle, route = "typing"),
        home(R.string.home_keypress_title, R.string.home_keypress_subtitle, route = "keypress"),
        home(R.string.home_languages_title, R.string.search_languages_subtitle, route = "languages"),
        home(R.string.home_appearance_title, R.string.home_appearance_subtitle, route = "appearance"),
        home(R.string.home_layout_title, R.string.home_layout_subtitle, route = "layout"),
        under(
            R.string.photo_rotation_title, R.string.photo_rotation_subtitle,
            R.string.home_screen_theme_edit_title, "photo_rotation",
        ),
        under(
            R.string.photo_find_title, R.string.photo_find_subtitle,
            R.string.home_screen_theme_edit_title, "photo_browse",
        ),
        under(
            R.string.photo_library_title, R.string.photo_library_subtitle,
            R.string.photo_rotation_title, "photo_library",
        ),
        under(
            R.string.photo_services_title, R.string.photo_services_subtitle,
            R.string.home_screen_theme_edit_title, "photos",
        ),
        home(R.string.home_keymaps_title, R.string.home_keymaps_subtitle, route = "keymaps"),
        home(R.string.home_rows_title, R.string.home_rows_subtitle, route = "rows"),
        home(R.string.home_modes_title, R.string.home_modes_subtitle, route = "modes"),
        home(R.string.home_emoji_title, R.string.home_emoji_subtitle, route = "emoji"),
        home(R.string.home_tools_title, R.string.home_tools_subtitle, route = "tools"),
        home(R.string.home_addons_title, R.string.home_addons_subtitle, route = "addons"),
        home(R.string.home_accessibility_title, R.string.home_accessibility_subtitle, route = "accessibility"),
        home(R.string.home_privacy_title, R.string.home_privacy_subtitle, route = "privacy"),
        home(R.string.home_backup_title, R.string.home_backup_subtitle, route = "backup"),
        home(R.string.home_about_title, R.string.home_about_subtitle, route = "about"),
        under(R.string.appearance_themes_title, R.string.appearance_themes_subtitle, R.string.home_appearance_title, "themes"),
        under(R.string.appearance_font_title, R.string.appearance_font_subtitle, R.string.home_appearance_title, "fonts"),
        under(R.string.appearance_icons_title, R.string.appearance_icons_subtitle, R.string.home_appearance_title, "icons"),
        under(
            R.string.typing_personal_dictionary_title,
            R.string.typing_personal_dictionary_subtitle,
            R.string.home_typing_title,
            "dictionary",
        ),
        under(
            R.string.typing_custom_dictionaries_title,
            R.string.typing_custom_dictionaries_subtitle,
            R.string.home_typing_title,
            "customdictionaries",
        ),
        under(
            R.string.langemoji_emoji_keywords_title,
            R.string.langemoji_emoji_keywords_subtitle,
            R.string.home_emoji_title,
            "emojikeywords",
        ),
        under(R.string.typing_blacklist_title, R.string.typing_blacklist_subtitle, R.string.home_typing_title, "blacklist"),
        under(
            R.string.typing_hw_shortcuts_list_title,
            R.string.typing_hw_shortcuts_list_subtitle,
            R.string.home_typing_title,
            "hwshortcuts",
        ),
        under(R.string.about_licences_title, R.string.about_licences_subtitle, R.string.home_about_title, "licenses"),
        under(R.string.about_storage_title, R.string.about_storage_subtitle, R.string.home_about_title, "storage"),
    )
}

/**
 * The Storage screen's categories.
 *
 * All of them point at the screen itself rather than at each category's own
 * page: someone searching for stickers wants the sticker packs, and a second
 * result that lands on a size readout for them would only be in the way. What
 * this buys is the other direction — a search for cached files, or for models,
 * finds the one screen that can delete them.
 */
private fun Resources.storageRows(): List<SettingsSearchEntry> {
    fun row(@StringRes title: Int, @StringRes subtitle: Int) =
        entry(title, subtitle, R.string.about_storage_title, "storage", screenParent = R.string.home_about_title)
    return listOf(
        row(R.string.storage_wordlists_title, R.string.storage_wordlists_subtitle),
        row(R.string.storage_voice_models_title, R.string.storage_voice_models_subtitle),
        row(R.string.storage_ai_models_title, R.string.storage_ai_models_subtitle),
        row(R.string.storage_themes_title, R.string.storage_themes_subtitle),
        row(R.string.storage_learned_title, R.string.storage_learned_subtitle),
        row(R.string.storage_cache_images_title, R.string.storage_cache_images_subtitle),
        row(R.string.storage_settings_data_title, R.string.storage_settings_data_subtitle),
    )
}

/** Rows that live on one of those screens rather than naming it. */
private fun Resources.sectionChildRows(): List<SettingsSearchEntry> = listOf(
    entry(R.string.fonts_installed_header, 0, R.string.home_screen_fonts_title, "fonts", screenParent = R.string.home_appearance_title),
    entry(
        R.string.customdict_emoji_auto_download_title,
        R.string.customdict_emoji_auto_download_subtitle,
        R.string.home_screen_emoji_keywords_title,
        "emojikeywords",
        screenParent = R.string.home_emoji_title,
    ),
)

/**
 * The tools themselves, derived from the enum rather than listed, so a new
 * tool is searchable the moment it has a title. No index edit needed.
 */
private fun Resources.toolRows(): List<SettingsSearchEntry> =
    ToolbarTool.entries.filter(::isSupportedTool).map { tool ->
        toolEntry(tool, toolTitle(tool), toolDescription(tool))
    }

/**
 * The whole searchable surface, in the language [res] is configured for.
 *
 * Build it once per screen, keyed on the context, and hand the result to
 * [searchSettings]. Tool-detail rows for tools this build cannot provide (the
 * lite flavor drops the ML Kit ones) are filtered out, because their screens
 * are unreachable.
 */
internal fun settingsSearchIndex(res: Resources): List<SettingsSearchEntry> = with(res) {
    val unsupported = ToolbarTool.entries.filterNot(::isSupportedTool)
        .map { "tool/${it.name}" }.toSet()
    val all = sectionRows() + toolRows() + sectionChildRows() + typingRows() + keyPressRows() +
        appearanceRows() + photoRows() + layoutRows() + emojiRows() + toolPageRowsA() + toolPageRowsB() +
        storageRows() + otherRows()
    all.filterNot { it.route in unsupported }
}

/**
 * Scores [entry] against an already-lowercased [token]. Higher is better;
 * zero means the token is absent, which disqualifies the whole entry: every
 * token must land somewhere, so "emoji row" does not match every emoji row.
 */
private fun score(entry: SettingsSearchEntry, token: String): Int {
    val title = entry.title.lowercase()
    return when {
        title == token -> 100
        title.startsWith(token) -> 80
        title.split(' ', '-', '&', '(').any { it.startsWith(token) } -> 60
        title.contains(token) -> 40
        entry.screen.lowercase().contains(token) -> 25
        entry.subtitle.lowercase().split(' ', '-', ',', '.').any { it.startsWith(token) } -> 15
        entry.subtitle.lowercase().contains(token) -> 8
        else -> 0
    }
}

/**
 * Ranked matches for [query] over [index]. The raw match score is scaled by the
 * entry's [EntryWeight], so a destination screen beats a row that names it just
 * as well and a backup toggle sinks below the feature it backs up. Ties break
 * on title length so the plainest setting with a matching name floats above the
 * wordier ones.
 */
internal fun searchSettings(
    query: String,
    index: List<SettingsSearchEntry>,
): List<SettingsSearchEntry> {
    val tokens = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()
    return index
        .map { candidate -> candidate to tokens.map { score(candidate, it) } }
        // Every token has to land somewhere: "emoji row" must not match a row
        // that only knows about emoji, nor every row on the emoji screen.
        .filter { (_, scores) -> scores.all { it > 0 } }
        .map { (candidate, scores) -> candidate to scores.sum() * candidate.weight.percent }
        .sortedWith(
            compareByDescending<Pair<SettingsSearchEntry, Int>> { it.second }
                .thenBy { it.first.title.length },
        )
        .map { it.first }
}
