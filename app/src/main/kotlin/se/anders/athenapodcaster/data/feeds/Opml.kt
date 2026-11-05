package se.anders.athenapodcaster.data.feeds

import android.util.Xml
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

data class OpmlFeed(val title: String?, val url: String)

/** OPML subscription lists (FR-004). Reading is forgiving; writing escapes everything. */
object Opml {
    class OpmlException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Every `<outline xmlUrl="…">` in the file, nested or not, in order, without duplicates. */
    fun read(input: InputStream, newParser: () -> XmlPullParser = { Xml.newPullParser() }): List<OpmlFeed> {
        val result = LinkedHashMap<String, OpmlFeed>()
        try {
            val p = newParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(input, null)
            var sawOpml = false
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    if (p.name.equals("opml", ignoreCase = true)) sawOpml = true
                    if (p.name.equals("outline", ignoreCase = true)) {
                        val url = attr(p, "xmlUrl")?.trim()
                        if (!url.isNullOrEmpty() && url !in result) {
                            result[url] = OpmlFeed((attr(p, "title") ?: attr(p, "text"))?.trim()?.ifEmpty { null }, url)
                        }
                    }
                }
                event = p.next()
            }
            if (!sawOpml) throw OpmlException("Not an OPML file")
        } catch (e: XmlPullParserException) {
            throw OpmlException("Not an OPML file", e)
        }
        return result.values.toList()
    }

    fun write(feeds: List<OpmlFeed>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<opml version=\"2.0\">\n  <head><title>Athena Podcasts subscriptions</title></head>\n  <body>\n")
        for (feed in feeds) {
            val title = escape(feed.title ?: feed.url)
            append("    <outline type=\"rss\" text=\"$title\" title=\"$title\" xmlUrl=\"${escape(feed.url)}\"/>\n")
        }
        append("  </body>\n</opml>\n")
    }

    /** Attribute lookup that ignores case (`xmlUrl`, `xmlurl`, `XMLURL` all occur in the wild). */
    private fun attr(p: XmlPullParser, name: String): String? {
        for (i in 0 until p.attributeCount) if (p.getAttributeName(i).equals(name, ignoreCase = true)) return p.getAttributeValue(i)
        return null
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
