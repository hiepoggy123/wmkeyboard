package com.wasimaster.wmkeyboard.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FdroidIndexTest {

    @Test
    fun readsFdroidOrgWhereVersionCodesAreNumbers() {
        val index = FdroidIndex.decode(
            """{"packageName":"org.fdroid.fdroid","suggestedVersionCode":1023052,""" +
                """"packages":[{"versionName":"1.23.2","versionCode":1023052},{"versionName":"1.23.1","versionCode":1023051}]}""",
        )
        assertNotNull(index)
        assertEquals(1023052, index!!.suggestedVersionCode)
        assertEquals("1.23.2", FdroidIndex.newerThan(index, 1023051)?.versionName)
    }

    @Test
    fun readsIzzyOnDroidWhereVersionCodesAreStrings() {
        // The same API shape, served by apt.izzysoft.de with quoted codes.
        val index = FdroidIndex.decode(
            """{"packageName":"com.aurora.store","suggestedVersionCode":"76",""" +
                """"packages":[{"versionCode":"76","versionName":"4.8.4"},{"versionCode":"75","versionName":"4.8.3"}]}""",
        )
        assertNotNull(index)
        assertEquals(76, index!!.suggestedVersionCode)
        assertEquals("4.8.4", FdroidIndex.newerThan(index, 75)?.versionName)
        assertNull(FdroidIndex.newerThan(index, 76))
    }

    @Test
    fun anUnreadableCodeIsNoUpdateRatherThanACrash() {
        val index = FdroidIndex.decode("""{"suggestedVersionCode":"soon","packages":[]}""")
        assertEquals(0, index?.suggestedVersionCode)
    }

    @Test
    fun theDefaultRepositoryIsFdroidOrg() {
        assertEquals("https://f-droid.org/api/v1/packages/a.b", FdroidIndex.apiUrl("a.b"))
        assertEquals("https://f-droid.org/packages/a.b/", FdroidIndex.packagePage("a.b"))
    }
}
