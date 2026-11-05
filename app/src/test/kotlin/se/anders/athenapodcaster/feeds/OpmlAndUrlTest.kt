package se.anders.athenapodcaster.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.kxml2.io.KXmlParser
import se.anders.athenapodcaster.data.feeds.Opml
import se.anders.athenapodcaster.data.feeds.OpmlFeed
import se.anders.athenapodcaster.data.feeds.normalizeFeedUrl

class OpmlAndUrlTest {
    private fun read(xml: String) = Opml.read(xml.byteInputStream()) { KXmlParser() }

    @Test
    fun readsNestedOutlinesAndIgnoresCase() {
        val feeds = read(
            """<?xml version="1.0"?><opml version="2.0"><body>
              <outline text="Group"><outline type="rss" text="One" xmlUrl="https://a.test/1.xml"/>
              <outline title="Two" XMLURL="https://a.test/2.xml"/></outline>
              <outline text="Folder only"/>
              <outline text="Dup" xmlUrl="https://a.test/1.xml"/>
            </body></opml>""",
        )
        assertEquals(listOf(OpmlFeed("One", "https://a.test/1.xml"), OpmlFeed("Two", "https://a.test/2.xml")), feeds)
    }

    @Test
    fun rejectsNonOpml() {
        assertThrows(Opml.OpmlException::class.java) { read("<rss/>") }
        assertThrows(Opml.OpmlException::class.java) { read("garbage") }
    }

    @Test
    fun writeThenReadRoundTripsAndEscapes() {
        val feeds = listOf(OpmlFeed("Q&A <show> \"x\"", "https://a.test/f?a=1&b=2"), OpmlFeed(null, "https://a.test/g"))
        val back = read(Opml.write(feeds))
        assertEquals(listOf(OpmlFeed("Q&A <show> \"x\"", "https://a.test/f?a=1&b=2"), OpmlFeed("https://a.test/g", "https://a.test/g")), back)
    }

    @Test
    fun normalizesUrls() {
        assertEquals("https://a.test/f.xml", normalizeFeedUrl("  https://a.test/f.xml "))
        assertEquals("https://a.test/f.xml", normalizeFeedUrl("a.test/f.xml"))
        assertEquals("https://a.test/f.xml", normalizeFeedUrl("feed://a.test/f.xml"))
        assertEquals("https://a.test/f.xml", normalizeFeedUrl("itpc://a.test/f.xml"))
        assertEquals("https://a.test/f.xml", normalizeFeedUrl("feed:https://a.test/f.xml"))
        assertEquals("http://a.test/f.xml", normalizeFeedUrl("http://a.test/f.xml"))
        assertNull(normalizeFeedUrl(""))
        assertNull(normalizeFeedUrl("   "))
        assertNull(normalizeFeedUrl("ftp://a.test/f.xml"))
    }
}
