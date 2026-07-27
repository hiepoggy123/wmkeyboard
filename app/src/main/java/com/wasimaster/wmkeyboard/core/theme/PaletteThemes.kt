package com.wasimaster.wmkeyboard.core.theme

/**
 * Built-in themes ported from well-known editor colour schemes, plus one
 * original neon theme.
 *
 * Every hex here is copied verbatim from the upstream palette definition —
 * Dracula's spec, Nord's `nord0..nord15`, Solarized's `base03..base3` +
 * accents, Catppuccin's `palette.json`, Tokyo Night's theme JSON — so a user
 * who knows the scheme from their editor sees the same colours on the
 * keyboard. Where a keyboard needs a background step the source palette does
 * not define (a third tone for modifier keys), the deviation is derived from
 * the palette's own tones and called out in a comment.
 *
 * Contrast is checked per theme against the WCAG ratio the element needs
 * (3:1 for key labels, which are large text, and for UI shapes). Two schemes
 * needed help: Solarized deliberately keeps its two background tones ~1.2:1
 * apart, which makes key shapes vanish, so both Solarized themes draw a 1 dp
 * `base01`/`base1` outline instead of being re-tinted away from spec.
 *
 * Licensing: the five upstream schemes are all MIT (Dracula © Dracula Theme,
 * Nord © Sven Greb, Solarized © Ethan Schoonover, Catppuccin © Catppuccin,
 * Tokyo Night © enkia). Their notices ship in
 * `assets/licenses/mit-color-themes.txt` and are listed in the About screen's
 * licences list. The names are used nominatively, to say which palette this
 * is; nothing here claims endorsement by those projects. "Cyberpunk" is our
 * own palette with no upstream, so it carries no attribution.
 */
val PaletteThemes: List<ThemeSpec> = listOf(

    // ---- Dracula ------------------------------------------------------
    // https://spec.draculatheme.com — background/selection/comment plus the
    // official UI variants (BGDark #21222C, BGLight #343746) from
    // dracula/visual-studio-code, which is where the extra background steps
    // a keyboard needs already exist.
    ThemeSpec(
        id = "builtin_dracula",
        name = "Dracula",
        dark = true,
        boardBackground = 0xFF282A36, // background
        keyBackground = 0xFF44475A, // selection / current line
        keyText = 0xFFF8F8F2, // foreground
        modifierKeyBackground = 0xFF21222C, // BGDark
        modifierKeyText = 0xFFF8F8F2,
        // Dark label on purple reads at 5.9:1; the foreground would be 2.4:1.
        enterKeyBackground = 0xFFBD93F9, // purple
        enterKeyText = 0xFF282A36,
        pressedKeyBackground = 0xFF6272A4, // comment
        accent = 0xFFBD93F9, // purple
        gestureTrailColor = 0xFFFF79C6, // pink
        popupBackground = 0xFF343746, // BGLight
        popupText = 0xFFF8F8F2,
        toolbarIcon = 0xFFF8F8F2,
        toolCircleBackground = 0xFF44475A,
        toolCircleActiveBackground = 0xFF6272A4,
        chipBackground = 0xFF21222C,
        suggestionText = 0xFFF8F8F2,
    ),

    // ---- Nord ---------------------------------------------------------
    // https://www.nordtheme.com/docs/colors-and-palettes — Polar Night for
    // the surfaces (nord0 board, nord1 modifiers, nord2 keys, nord3 pressed),
    // Snow Storm for text, Frost for the accent. Aurora is unused: it is the
    // semantic status range, and nothing on a keyboard means "error".
    ThemeSpec(
        id = "builtin_nord",
        name = "Nord",
        dark = true,
        boardBackground = 0xFF2E3440, // nord0
        keyBackground = 0xFF434C5E, // nord2
        keyText = 0xFFECEFF4, // nord6
        modifierKeyBackground = 0xFF3B4252, // nord1
        modifierKeyText = 0xFFE5E9F0, // nord5
        enterKeyBackground = 0xFF88C0D0, // nord8
        enterKeyText = 0xFF2E3440,
        pressedKeyBackground = 0xFF4C566A, // nord3
        accent = 0xFF88C0D0, // nord8
        gestureTrailColor = 0xFF8FBCBB, // nord7
        popupBackground = 0xFF4C566A,
        popupText = 0xFFECEFF4,
        toolbarIcon = 0xFFD8DEE9, // nord4
        toolCircleBackground = 0xFF3B4252,
        toolCircleActiveBackground = 0xFF4C566A,
        chipBackground = 0xFF3B4252,
        suggestionText = 0xFFECEFF4,
    ),

    // ---- Solarized dark -----------------------------------------------
    // https://ethanschoonover.com/solarized — base03 board, base02 keys, as
    // in the scheme's "background / background highlights" split. Those two
    // are only ~1.2:1 apart by design, so keys get a base01 outline rather
    // than a re-tint; modifiers use a darkened base03 for a third step.
    ThemeSpec(
        id = "builtin_solarized_dark",
        name = "Solarized Dark",
        dark = true,
        boardBackground = 0xFF002B36, // base03
        keyBackground = 0xFF073642, // base02
        // base1 (emphasized content) rather than base0 for labels: 4.9:1
        // against base02 instead of 4.1:1, still inside the palette.
        keyText = 0xFF93A1A1, // base1
        modifierKeyBackground = 0xFF00212B, // base03 darkened (not in spec)
        modifierKeyText = 0xFF839496, // base0
        enterKeyBackground = 0xFF268BD2, // blue
        enterKeyText = 0xFF002B36,
        // base02 lifted 45% toward base01, not base01 itself: a pressed key
        // still shows its label, and base1-on-base01 is only 2:1.
        pressedKeyBackground = 0xFF2B4F59,
        keyBorderColor = 0x80586E75, // base01 at 50% — the key outline
        keyBorderWidthDp = 1f,
        accent = 0xFF268BD2, // blue
        gestureTrailColor = 0xFF2AA198, // cyan
        popupBackground = 0xFF073642,
        popupText = 0xFF93A1A1,
        toolbarIcon = 0xFF839496, // base0
        toolCircleBackground = 0xFF073642,
        toolCircleActiveBackground = 0xFF586E75,
        chipBackground = 0xFF073642,
        suggestionText = 0xFF93A1A1,
    ),

    // ---- Solarized light ----------------------------------------------
    // Same palette inverted the way Solarized itself inverts: base3 board,
    // base2 key "highlights", base01 text. Same outline trick, same reason.
    ThemeSpec(
        id = "builtin_solarized_light",
        name = "Solarized Light",
        dark = false,
        boardBackground = 0xFFFDF6E3, // base3
        keyBackground = 0xFFEEE8D5, // base2
        keyText = 0xFF586E75, // base01
        modifierKeyBackground = 0xFFE4DCC4, // base2 darkened (not in spec)
        modifierKeyText = 0xFF657B83, // base00
        enterKeyBackground = 0xFF268BD2, // blue
        enterKeyText = 0xFFFDF6E3,
        // base2 pulled 40% toward base1; base1 itself drops the base01 label
        // to 2:1, the mirror of the same problem in Solarized Dark.
        pressedKeyBackground = 0xFFC9CBC0,
        keyBorderColor = 0x8093A1A1, // base1 at 50%
        keyBorderWidthDp = 1f,
        accent = 0xFF268BD2,
        gestureTrailColor = 0xFF2AA198,
        popupBackground = 0xFFFDF6E3,
        popupText = 0xFF586E75,
        toolbarIcon = 0xFF657B83, // base00
        toolCircleBackground = 0xFFEEE8D5,
        // Blue rather than base1: an icon on base1 lands at 2.7:1 whichever
        // colour it takes, while white on blue reads at 3.7:1.
        toolCircleActiveBackground = 0xFF268BD2,
        chipBackground = 0xFFEEE8D5,
        suggestionText = 0xFF586E75,
    ),

    // ---- Catppuccin Mocha ---------------------------------------------
    // catppuccin/palette `palette.json`. Surface ramp exactly as the style
    // guide prescribes: base for the board, surface0/1/2 for raised things,
    // mantle/crust for recessed ones, mauve as the brand accent.
    ThemeSpec(
        id = "builtin_catppuccin_mocha",
        name = "Catppuccin Mocha",
        dark = true,
        boardBackground = 0xFF1E1E2E, // base
        keyBackground = 0xFF313244, // surface0
        keyText = 0xFFCDD6F4, // text
        modifierKeyBackground = 0xFF181825, // mantle
        modifierKeyText = 0xFFBAC2DE, // subtext1
        enterKeyBackground = 0xFFCBA6F7, // mauve
        enterKeyText = 0xFF11111B, // crust
        pressedKeyBackground = 0xFF45475A, // surface1
        accent = 0xFFCBA6F7, // mauve
        gestureTrailColor = 0xFFF5C2E7, // pink
        popupBackground = 0xFF45475A, // surface1
        popupText = 0xFFCDD6F4,
        toolbarIcon = 0xFFA6ADC8, // subtext0
        toolCircleBackground = 0xFF313244,
        toolCircleActiveBackground = 0xFF585B70, // surface2
        chipBackground = 0xFF181825,
        suggestionText = 0xFFCDD6F4,
    ),

    // ---- Catppuccin Latte ---------------------------------------------
    // The light flavour, same ramp read the other way: base is the lightest
    // surface so it takes the keys, then mantle and crust step down. Board is
    // crust rather than mantle so the key faces actually separate from it —
    // base-on-mantle is 1.08:1, which is a flat grey slab at arm's length.
    ThemeSpec(
        id = "builtin_catppuccin_latte",
        name = "Catppuccin Latte",
        dark = false,
        boardBackground = 0xFFDCE0E8, // crust
        keyBackground = 0xFFEFF1F5, // base
        keyText = 0xFF4C4F69, // text
        modifierKeyBackground = 0xFFE6E9EF, // mantle
        modifierKeyText = 0xFF5C5F77, // subtext1
        enterKeyBackground = 0xFF8839EF, // mauve
        enterKeyText = 0xFFEFF1F5,
        pressedKeyBackground = 0xFFCCD0DA, // surface0
        accent = 0xFF8839EF, // mauve
        gestureTrailColor = 0xFFEA76CB, // pink
        popupBackground = 0xFFEFF1F5,
        popupText = 0xFF4C4F69,
        toolbarIcon = 0xFF6C6F85, // subtext0
        toolCircleBackground = 0xFFEFF1F5,
        toolCircleActiveBackground = 0xFFCCD0DA, // surface0
        chipBackground = 0xFFE6E9EF, // mantle
        suggestionText = 0xFF4C4F69,
    ),

    // ---- Tokyo Night --------------------------------------------------
    // enkia/tokyo-night-vscode-theme (the "Night" variant): bg #1a1b26,
    // bg_dark #16161e, bg_highlight #292e42, fg_gutter #3b4261,
    // terminal_black #414868, fg #c0caf5, fg_dark #a9b1d6.
    ThemeSpec(
        id = "builtin_tokyo_night",
        name = "Tokyo Night",
        dark = true,
        boardBackground = 0xFF1A1B26, // bg
        keyBackground = 0xFF292E42, // bg_highlight
        keyText = 0xFFC0CAF5, // fg
        modifierKeyBackground = 0xFF16161E, // bg_dark
        modifierKeyText = 0xFFA9B1D6, // fg_dark
        enterKeyBackground = 0xFF7AA2F7, // blue
        enterKeyText = 0xFF1A1B26,
        pressedKeyBackground = 0xFF3B4261, // fg_gutter
        accent = 0xFF7AA2F7, // blue
        gestureTrailColor = 0xFFBB9AF7, // magenta
        popupBackground = 0xFF3B4261,
        popupText = 0xFFC0CAF5,
        toolbarIcon = 0xFFA9B1D6, // fg_dark
        toolCircleBackground = 0xFF292E42,
        toolCircleActiveBackground = 0xFF414868, // terminal_black
        chipBackground = 0xFF16161E,
        suggestionText = 0xFFC0CAF5,
    ),

    // ---- Cyberpunk ----------------------------------------------------
    // Ours, not a port — so it is the one place that uses the whole feature
    // set: an animated board gradient, cut-corner keys, translucent key
    // faces with a neon outline and a sheen over them. Key faces are
    // translucent so the gradient reads through them; flattened opaque
    // (which is what the panel Material scheme does) they stay dark enough
    // for the pale label text.
    ThemeSpec(
        id = "builtin_cyberpunk",
        name = "Cyberpunk",
        dark = true,
        boardBackground = 0xFF0B0A2E,
        boardGradient = GradientSpec(
            listOf(0xFF1B0533, 0xFF0B0A2E, 0xFF03141F), GradientType.LINEAR, 135f,
        ),
        keyShape = KeyShapeKind.CUT,
        keyGradient = GradientSpec(
            listOf(0x2605D9E8, 0x0005D9E8), GradientType.LINEAR, 90f,
        ),
        keyBackground = 0x59202447,
        keyText = 0xFFD1F7FF,
        modifierKeyBackground = 0x40120A38,
        modifierKeyText = 0xFF9BE8F2,
        enterKeyBackground = 0xFFFF2A6D,
        enterKeyText = 0xFF11021F,
        // Magenta-tinted press rather than a cyan flash: cyan bright enough
        // to read as neon leaves the pale key label at 1.5:1.
        pressedKeyBackground = 0xFF4A0F35,
        keyBorderColor = 0x6605D9E8,
        keyBorderWidthDp = 1f,
        accent = 0xFF05D9E8,
        gestureTrailColor = 0xFFFF2A6D,
        popupBackground = 0xFF190A3A,
        popupText = 0xFFD1F7FF,
        toolbarIcon = 0xFF9BE8F2,
        toolCircleBackground = 0x59202447,
        toolCircleActiveBackground = 0xFF2B0E4D,
        chipBackground = 0x40190A3A,
        suggestionText = 0xFFD1F7FF,
        animation = ThemeAnimation.FLOW,
        animationSpeed = 0.7f,
    ),
)
