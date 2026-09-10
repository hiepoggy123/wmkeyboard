package com.wasimaster.wmkeyboard.core.plugins

import com.wasimaster.wmkeyboard.plugins.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RenderedUi.repairCodes] must stay index-aligned with [RenderedUi.repairs]. */
class PluginRepairCodesTest {

    private fun render(source: String): RenderedUi {
        val budget = PluginBudget()
        val globals = PluginSandbox.create(budget) {}
        budget.begin(PluginLimit.LOAD)
        try {
            PluginSandbox.compile(globals, PluginPrelude.SOURCE, PluginPrelude.CHUNK_NAME).call()
            return PluginUiCodec.fromLua(PluginSandbox.compile(globals, source).call())
        } finally {
            budget.end()
        }
    }

    @Test
    fun `each repair message carries its code at the same index`() {
        val ui = render(
            """
            return {
              42,
              { type = "sparkle" },
              { },
              ui.tabs{ id = "t" },
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf(PluginRepair.NOT_A_WIDGET, PluginRepair.UNKNOWN_TYPE, PluginRepair.NO_TYPE, PluginRepair.TABS_NO_PAGES),
            ui.repairCodes,
        )
        assertEquals(ui.repairs.size, ui.repairCodes.size)
        assertEquals(PluginText.of(R.string.core_plugins_ui_repair_unknown_type, "sparkle"), ui.repairs[1])
    }

    @Test
    fun `a repeated message is dropped together with its code`() {
        val ui = render("""return { { type = "a" }, { type = "a" }, { type = "b" } }""")
        assertEquals(listOf(PluginRepair.UNKNOWN_TYPE, PluginRepair.UNKNOWN_TYPE), ui.repairCodes)
        assertEquals(2, ui.repairs.size)
    }

    @Test
    fun `a clean tree has no codes`() {
        assertTrue(render("""return ui.label{ text = "hi" }""").repairCodes.isEmpty())
    }
}
