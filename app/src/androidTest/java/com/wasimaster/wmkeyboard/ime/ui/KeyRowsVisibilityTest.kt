package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasimaster.wmkeyboard.core.plugins.InstalledPlugin
import com.wasimaster.wmkeyboard.core.plugins.PluginWidget
import com.wasimaster.wmkeyboard.core.plugins.RenderedUi
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.PanelMode
import com.wasimaster.wmkeyboard.ime.PluginPanelUi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the real keyboard and asks whether the key grid is on screen.
 *
 * A panel that takes the keys away from the user's field has to draw a set of
 * its own underneath, or the user is typing into something with nothing to type
 * with. A plugin's text box shipped doing exactly half of that: the panel
 * collapsed to make room (`FullBleedTool(compact = ...)`) and the matching
 * `KeyRows` call was never added.
 *
 * `PluginKeyOwnershipTest` guards the same thing by reading the source, which
 * catches the omission but would keep passing if the call site were reworded.
 * This one composes the screen, so it only passes when the keys really render.
 */
@RunWith(AndroidJUnit4::class)
class KeyRowsVisibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val plugin = InstalledPlugin(id = "com.example.demo", name = "Demo", version = "1.0.0")

    private val pluginUi = RenderedUi(
        listOf(PluginWidget.Input(id = "msg", label = "A text box", placeholder = "Type here")),
    )

    /**
     * Every callback the screen has no default for. None of them fire here —
     * the test only asks what got composed.
     */
    private fun show(state: KeyboardUiState) {
        compose.setContent {
            KeyboardThemeProvider(state.settings) {
                KeyboardScreen(
                    stateFlow = MutableStateFlow(state),
                    onKey = {},
                    onSuggestion = {},
                    onEmoji = {},
                    onEmojiQueryTap = {},
                    onPanelChange = {},
                    onClipboardItem = {},
                    onClipboardPin = {},
                    onClipboardDelete = {},
                )
            }
        }
    }

    @Test
    fun theKeysAreThereWithNoPanelOpen() {
        show(KeyboardUiState())
        compose.onNodeWithTag(KeyRowsTestTag).assertIsDisplayed()
    }

    /**
     * The regression. Without the `pluginTypingActive` branch in `KeyboardBody`
     * the panel still collapses — it just collapses onto nothing.
     */
    @Test
    fun theKeysComeBackWhenAPluginTextBoxTakesThem() {
        show(
            KeyboardUiState(
                panel = PanelMode.PLUGINS,
                plugins = PluginPanelUi.Running(plugin, pluginUi),
                pluginInputs = mapOf("msg" to ""),
                pluginFocusedInput = "msg",
            ),
        )
        compose.onNodeWithTag(KeyRowsTestTag).assertIsDisplayed()
    }

    /**
     * And the other way, or the test above would pass on a keyboard that simply
     * always draws its keys. With no box focused the plugin owns the full
     * height and the keys are meant to be gone.
     */
    @Test
    fun thePluginPanelKeepsTheHeightWhileNothingIsFocused() {
        show(
            KeyboardUiState(
                panel = PanelMode.PLUGINS,
                plugins = PluginPanelUi.Running(plugin, pluginUi),
                pluginFocusedInput = null,
            ),
        )
        compose.onNodeWithTag(KeyRowsTestTag).assertDoesNotExist()
    }
}
