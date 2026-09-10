package com.wasimaster.wmkeyboard.app

import com.wasimaster.wmkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginIdeSearchTest {

    @Test
    fun `the plugin editor row is found by the words people use for it`() {
        assertEquals(R.string.search_kw_plugin_ide, searchKeywordsFor(R.string.plugin_ide_entry_title))
    }
}
