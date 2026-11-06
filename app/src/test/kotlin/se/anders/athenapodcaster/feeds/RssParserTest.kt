package se.anders.athenapodcaster.feeds

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import se.anders.athenapodcaster.data.feeds.FeedParseException
import se.anders.athenapodcaster.data.feeds.RssParser
import se.anders.athenapodcaster.data.feeds.parseDuration
import se.anders.athenapodcaster.data.feeds.parseFeedDate

class RssParserTest {
    private val parser = RssParser { KXmlParser() }
    private fun parse(xml: String) = parser.parse(xml.byteInputStream())
    private fun feed(items: String, channelExtra: String = "") =
        """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"><channel><title>Show</title>$channelExtra$items</channel></rss>"""

    private fun item(title: String, guid: String? = "g-$title", enclosure: String = """<enclosure url="https://x.test/$title.mp3" type="audio/mpeg" length="123"/>""", extra: String = "") =
        "<item><title>$title</title>${guid?.let { "<guid>$it</guid>" }.orEmpty()}$enclosure$extra</item>"

    @Test
    fun parsesChannelAndItems() {
        val f = parse(
            feed(
                item("One", extra = "<pubDate>Tue, 03 Nov 2026 06:00:00 +0000</pubDate><itunes:duration>01:02:03</itunes:duration>") + item("Two"),
                channelExtra = """<description>About</description><itunes:image href="https://x.test/art.jpg"/>""",
            ),
        )
        assertEquals("Show", f.title)
        assertEquals("About", f.description)
        assertEquals("https://x.test/art.jpg", f.imageUrl)
        assertEquals(2, f.episodes.size)
        val one = f.episodes[0]
        assertEquals("g-One", one.guid)
        assertEquals("https://x.test/One.mp3", one.enclosureUrl)
        assertEquals("audio/mpeg", one.enclosureType)
        assertEquals(123L, one.enclosureLength)
        assertEquals(3723, one.durationSec)
        assertEquals(Instant.parse("2026-11-03T06:00:00Z").toEpochMilli(), one.publishedAt)
    }

    @Test
    fun worksWhenThePlatformParserHasNamespacesOn() {
        // Android's Xml.newPullParser() enables namespace processing; regression for missing durations on device.
        val nsParser = RssParser { KXmlParser().apply { setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true) } }
        val f = nsParser.parse(
            feed(item("One", extra = "<itunes:duration>01:02:03</itunes:duration>"), channelExtra = "<itunes:image href=\"https://x.test/art.jpg\"/>").byteInputStream(),
        )
        assertEquals(3723, f.episodes.single().durationSec)
        assertEquals("https://x.test/art.jpg", f.imageUrl)
    }

    @Test
    fun itemElementsDoNotLeakIntoChannelTitle() {
        // <title> inside <item> and <image> must not replace the channel title.
        val f = parse(feed("<image><url>https://x.test/i.png</url><title>Logo</title></image>" + item("Ep")))
        assertEquals("Show", f.title)
        assertEquals("https://x.test/i.png", f.imageUrl)
    }

    @Test
    fun missingGuidFallsBackToEnclosureUrl() {
        val f = parse(feed(item("NoGuid", guid = null)))
        assertEquals("https://x.test/NoGuid.mp3", f.episodes.single().guid)
    }

    @Test
    fun itemsNextToTheChannelAreStillEpisodesAndDoNotReplaceTheShowTitle() {
        // Some generators close <channel> before the items. Regression: the show was named after an episode, with 0 episodes.
        val xml = """<?xml version="1.0"?><rss version="2.0"><channel><title>The Show</title><description>About</description></channel>""" +
            item("Episode 2") + item("Episode 1") + "</rss>"
        val f = parse(xml)
        assertEquals("The Show", f.title)
        assertEquals(listOf("Episode 2", "Episode 1"), f.episodes.map { it.title })
    }

    @Test
    fun firstChannelTitleWinsAndLaterDuplicatesAreIgnored() {
        val f = parse(feed(item("Ep"), channelExtra = "<title>Second title</title>"))
        assertEquals("Show", f.title)
    }

    @Test
    fun mediaContentIsUsedWhenThereIsNoEnclosure() {
        val f = parse(feed("<item><title>M</title><guid>m</guid><media:content xmlns:media=\"http://search.yahoo.com/mrss/\" url=\"https://x.test/m.mp3\" type=\"audio/mpeg\" fileSize=\"500\"/></item>"))
        val e = f.episodes.single()
        assertEquals("https://x.test/m.mp3", e.enclosureUrl)
        assertEquals(500L, e.enclosureLength)
    }

    @Test
    fun episodeArtIsReadAndChannelArtDoesNotLeakIntoEpisodes() {
        val f = parse(
            feed(
                item("WithArt", extra = "<itunes:image href=\"https://x.test/ep.jpg\"/>") + item("NoArt"),
                channelExtra = "<itunes:image href=\"https://x.test/show.jpg\"/>",
            ),
        )
        assertEquals("https://x.test/show.jpg", f.imageUrl)
        assertEquals("https://x.test/ep.jpg", f.episodes[0].imageUrl)
        assertNull(f.episodes[1].imageUrl)
    }

    @Test
    fun enclosureWithoutTypeOrExtensionIsAccepted() {
        val f = parse(feed(item("NoType", enclosure = "<enclosure url=\"https://x.test/download?id=7&amp;key=abc\"/>")))
        assertEquals("https://x.test/download?id=7&key=abc", f.episodes.single().enclosureUrl)
    }

    @Test
    fun nonAudioAndEnclosureLessItemsAreSkipped() {
        val f = parse(
            feed(
                item("Video", enclosure = """<enclosure url="https://x.test/v.mp4" type="video/mp4"/>""") +
                    item("None", enclosure = "") +
                    item("Untyped", enclosure = """<enclosure url="https://x.test/u.m4a?token=abc"/>""") +
                    item("Good"),
            ),
        )
        assertEquals(listOf("Untyped", "Good"), f.episodes.map { it.title })
    }

    @Test
    fun cdataHtmlAndWhitespaceAreCleaned() {
        val f = parse(feed("<item><title><![CDATA[  <b>Hello</b>\n   world  ]]></title><enclosure url=\"https://x.test/a.mp3\" type=\"audio/mpeg\"/></item>"))
        assertEquals("Hello world", f.episodes.single().title)
    }

    @Test
    fun byteOrderMarkIsAccepted() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + feed(item("Ep")).toByteArray()
        assertEquals(1, parser.parse(bytes.inputStream()).episodes.size)
    }

    @Test
    fun notRssIsRejectedWithoutLeakingContent() {
        val e = assertThrows(FeedParseException::class.java) { parse("<html><body>secret-token-123</body></html>") }
        assertTrue(!e.message.orEmpty().contains("secret"))
        assertThrows(FeedParseException::class.java) { parse("not xml at all") }
        assertThrows(FeedParseException::class.java) { parse("<rss><notchannel/></rss>") }
    }

    @Test
    fun tenThousandItemsParseQuickly() {
        val items = (1..10_000).joinToString("") { item("E$it") }
        val start = System.nanoTime()
        val f = parse(feed(items))
        assertEquals(10_000, f.episodes.size)
        assertTrue("took ${(System.nanoTime() - start) / 1_000_000} ms", (System.nanoTime() - start) < 5_000_000_000L)
    }

    @Test
    fun durations() {
        assertEquals(3600, parseDuration("3600"))
        assertEquals(3600, parseDuration("3600.7"))
        assertEquals(125, parseDuration("2:05"))
        assertEquals(3723, parseDuration("1:02:03"))
        assertNull(parseDuration(""))
        assertNull(parseDuration("abc"))
        assertNull(parseDuration("1:2:3:4"))
        assertNull(parseDuration("0"))
        assertNull(parseDuration(null))
    }

    @Test
    fun dates() {
        val expected = Instant.parse("2026-11-03T06:00:00Z").toEpochMilli()
        assertEquals(expected, parseFeedDate("Tue, 03 Nov 2026 06:00:00 +0000"))
        assertEquals(expected, parseFeedDate("Tue, 3 Nov 2026 06:00:00 GMT"))
        assertEquals(expected, parseFeedDate("3 Nov 2026 06:00:00 +0000"))
        assertEquals(expected, parseFeedDate("Tue, 03 Nov 2026 07:00:00 +0100"))
        // A wrong weekday (3 Nov 2026 is a Tuesday) must not lose the date.
        assertEquals(expected, parseFeedDate("Thu, 03 Nov 2026 06:00:00 +0000"))
        assertEquals(expected, parseFeedDate("Wednesday, 3 Nov 2026 06:00:00 GMT"))
        assertEquals(expected, parseFeedDate("Tue, 03 Nov 2026 01:00:00 EST"))
        assertEquals(expected, parseFeedDate("2026-11-03T06:00:00Z"))
        assertEquals(expected, parseFeedDate("  Tue,  03 Nov 2026   06:00:00 +0000 "))
        assertNull(parseFeedDate("yesterday"))
        assertNull(parseFeedDate(""))
    }
}
