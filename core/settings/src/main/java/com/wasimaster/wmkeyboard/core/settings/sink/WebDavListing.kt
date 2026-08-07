package com.wasimaster.wmkeyboard.core.settings.sink

import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/**
 * Reads the XML a WebDAV server answers `PROPFIND` with.
 *
 * Pure, and apart from the sink, because the sink is untestable — it needs a
 * network — and this is where the fiddly parts live. Servers disagree about
 * almost everything in this document except its shape: the `DAV:` namespace
 * prefix is `d:` on Nextcloud, `D:` on Apache, `lp1:` on some, absent on
 * others; hrefs come back percent-encoded, absolute or path-only; and
 * `getcontentlength` is missing on collections.
 */
object WebDavListing {

    /** One `<response>` worth of what we care about. */
    data class Entry(
        /** The href exactly as the server gave it, needed to address the file. */
        val href: String,
        /** The last path segment, percent-decoded. */
        val name: String,
        val sizeBytes: Long,
        val modifiedAtMs: Long,
        val isCollection: Boolean,
    )

    /**
     * RFC 1123, which is what `getlastmodified` is defined to be.
     *
     * [Locale.US] is not optional: the format has English month and day names
     * in it, and a device set to another language would fail to parse every
     * date. Built per call because [SimpleDateFormat] is not thread-safe.
     */
    private fun httpDate() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("GMT") }

    private val DOCTYPE = Regex("""<!DOCTYPE""", RegexOption.IGNORE_CASE)

    private const val MAX_SOURCE_BYTES = 4 * 1024 * 1024

    /**
     * Every `<response>` in [xml], or an empty list when it is not a multistatus
     * document at all.
     *
     * The DOCTYPE check is the XXE guard, done by inspection rather than by
     * asking the parser. Android's SAX recognises only the two `namespaces`
     * features and throws for anything else, so setting Apache's
     * `disallow-doctype-decl` fails every parse on device while passing on the
     * JVM. `SvgParser` learned that the hard way; this follows it.
     */
    fun parse(xml: String): List<Entry> {
        if (xml.length > MAX_SOURCE_BYTES) return emptyList()
        if (DOCTYPE.containsMatchIn(xml)) return emptyList()

        val handler = Handler()
        runCatching {
            val factory = SAXParserFactory.newInstance().apply {
                // Prefixes vary by server, so local names are matched by hand
                // rather than by asking for namespace awareness we cannot rely
                // on every response to declare correctly.
                isNamespaceAware = false
                disableQuietly("http://apache.org/xml/features/disallow-doctype-decl", true)
                disableQuietly("http://xml.org/sax/features/external-general-entities", false)
                disableQuietly("http://xml.org/sax/features/external-parameter-entities", false)
                runCatching { setXIncludeAware(false) }
            }
            factory.newSAXParser().parse(InputSource(StringReader(xml)), handler)
        }
        return handler.entries
    }

    /** The last path segment of [href], percent-decoded. */
    fun nameOf(href: String): String {
        val path = href.substringBefore('?').trimEnd('/')
        val last = path.substringAfterLast('/')
        return runCatching { java.net.URLDecoder.decode(last, "UTF-8") }.getOrDefault(last)
    }

    private fun SAXParserFactory.disableQuietly(feature: String, value: Boolean) {
        runCatching { setFeature(feature, value) }
    }

    /** `d:getcontentlength` and `getcontentlength` are the same element to us. */
    private fun local(qName: String): String = qName.substringAfterLast(':').lowercase(Locale.US)

    private class Handler : DefaultHandler() {

        val entries = ArrayList<Entry>()

        private var href: String? = null
        private var size = -1L
        private var modified = 0L
        private var collection = false
        private var text = StringBuilder()
        private var depth = 0

        override fun startElement(uri: String?, localName: String?, qName: String, at: Attributes?) {
            text = StringBuilder()
            when (local(qName)) {
                "response" -> {
                    href = null
                    size = -1L
                    modified = 0L
                    collection = false
                    depth = 1
                }
                // The marker for a directory. It is an empty element inside
                // <resourcetype>, so its mere presence is the whole signal.
                "collection" -> if (depth > 0) collection = true
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            val value = text.toString().trim()
            when (local(qName)) {
                "href" -> if (href == null) href = value
                "getcontentlength" -> size = value.toLongOrNull() ?: -1L
                "getlastmodified" ->
                    modified = runCatching { httpDate().parse(value)?.time }.getOrNull() ?: 0L
                "response" -> {
                    val target = href
                    if (depth > 0 && !target.isNullOrEmpty()) {
                        entries += Entry(
                            href = target,
                            name = nameOf(target),
                            sizeBytes = size,
                            modifiedAtMs = modified,
                            isCollection = collection,
                        )
                    }
                    depth = 0
                }
            }
            text = StringBuilder()
        }
    }
}
