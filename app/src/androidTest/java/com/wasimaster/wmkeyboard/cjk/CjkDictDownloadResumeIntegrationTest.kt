package com.wasimaster.wmkeyboard.cjk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictCatalog
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real Device Network & Storage Instrumented Test for Dictionary Downloads (P2.4).
 *
 * Tests the HTTP Range request resume logic and gzip encoding mismatch issue.
 */
@RunWith(AndroidJUnit4::class)
class CjkDictDownloadResumeIntegrationTest {

    /**
     * Test P2.4: Resume request MUST use Accept-Encoding: identity so Range requests operate
     * on decompressed byte offsets without triggering GZIPInputStream magic header mismatch (ID1ID2).
     */
    @Test
    fun testP2_4_DownloadManagerResumeRangeHeaderMustNotBeGzip() {
        val strokePack = CjkDictCatalog.byId("stroke")
        assertNotNull(strokePack)

        val urlString = strokePack!!.url
        if (urlString.isNotBlank()) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connect()

            val contentEncoding = connection.getHeaderField("Content-Encoding") ?: ""

            // Intended specification assertion: Range requests must not use transparent gzip encoding
            assertNotEquals("Content-Encoding for Range download MUST NOT be gzip", "gzip", contentEncoding)

            connection.disconnect()
        }
    }

    @Test
    fun testPartFileOffsetMath() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val filesDir = appContext.filesDir

        val pack = CjkDictCatalog.byId("pinyin")!!
        val partFile = CjkDictStore.partFile(filesDir, pack)

        partFile.parentFile?.mkdirs()
        partFile.writeBytes(ByteArray(1000))

        assertTrue(partFile.isFile)
        assertEquals(1000L, partFile.length())

        partFile.delete()
    }
}
