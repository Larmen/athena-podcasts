package se.anders.athenapodcaster.feeds

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kxml2.io.KXmlParser
import se.anders.athenapodcaster.data.feeds.FeedFetcher
import se.anders.athenapodcaster.data.feeds.FetchErrorKind
import se.anders.athenapodcaster.data.feeds.FetchResult
import se.anders.athenapodcaster.data.feeds.RssParser

class FeedFetcherTest {
    private lateinit var server: MockWebServer
    private val xml = """<rss version="2.0"><channel><title>Show</title><item><title>Ep</title><guid>g</guid><enclosure url="https://x.test/a.mp3" type="audio/mpeg"/></item></channel></rss>"""

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() = server.close()

    private fun fetcher(maxBytes: Long = FeedFetcher.MAX_FEED_BYTES) = FeedFetcher(parser = RssParser { KXmlParser() }, maxBytes = maxBytes)
    private fun fetch(path: String = "/feed", etag: String? = null, modified: String? = null, f: FeedFetcher = fetcher()) =
        runBlocking { f.fetch(server.url(path).toString(), etag, modified) }

    @Test
    fun fetchesAndParses() {
        server.enqueue(MockResponse.Builder().body(xml).addHeader("ETag", "\"v1\"").addHeader("Last-Modified", "Tue, 03 Nov 2026 06:00:00 GMT").build())
        val r = fetch() as FetchResult.Fetched
        assertEquals("Show", r.feed.title)
        assertEquals("\"v1\"", r.etag)
        assertEquals("Tue, 03 Nov 2026 06:00:00 GMT", r.lastModified)
        assertNull(r.movedTo)
        val req = server.takeRequest()
        assertEquals("AthenaPodcaster/0.1", req.headers["User-Agent"])
    }

    @Test
    fun sendsConditionalHeadersAndHandles304() {
        server.enqueue(MockResponse.Builder().code(304).build())
        assertEquals(FetchResult.NotModified, fetch(etag = "\"v1\"", modified = "Tue, 03 Nov 2026 06:00:00 GMT"))
        val req = server.takeRequest()
        assertEquals("\"v1\"", req.headers["If-None-Match"])
        assertEquals("Tue, 03 Nov 2026 06:00:00 GMT", req.headers["If-Modified-Since"])
    }

    @Test
    fun permanentRedirectReportsNewUrl() {
        server.enqueue(MockResponse.Builder().code(301).addHeader("Location", server.url("/new").toString()).build())
        server.enqueue(MockResponse.Builder().body(xml).build())
        val r = fetch("/old") as FetchResult.Fetched
        assertEquals(server.url("/new").toString(), r.movedTo)
    }

    @Test
    fun temporaryRedirectIsFollowedButNotRemembered() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/tmp").toString()).build())
        server.enqueue(MockResponse.Builder().body(xml).build())
        assertNull((fetch("/old") as FetchResult.Fetched).movedTo)
    }

    @Test
    fun gzipBodiesAreDecoded() {
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(xml.toByteArray()) } }.toByteArray()
        server.enqueue(MockResponse.Builder().body(Buffer().write(gz)).addHeader("Content-Encoding", "gzip").build())
        assertEquals("Show", (fetch() as FetchResult.Fetched).feed.title)
    }

    @Test
    fun authAndHttpErrors() {
        for (code in listOf(401, 403, 410)) {
            server.enqueue(MockResponse.Builder().code(code).build())
            assertEquals(FetchResult.Failed(FetchErrorKind.AuthFailed, code), fetch())
        }
        server.enqueue(MockResponse.Builder().code(500).build())
        assertEquals(FetchResult.Failed(FetchErrorKind.Http, 500), fetch())
    }

    @Test
    fun htmlIsNotAFeed() {
        server.enqueue(MockResponse.Builder().body("<html><body>Sign in</body></html>").build())
        assertEquals(FetchResult.Failed(FetchErrorKind.NotAFeed), fetch())
    }

    @Test
    fun oversizedFeedIsRejected() {
        server.enqueue(MockResponse.Builder().body(xml + " ".repeat(5000)).build())
        assertEquals(FetchResult.Failed(FetchErrorKind.TooLarge), fetch(f = fetcher(maxBytes = 1000)))
    }

    @Test
    fun invalidUrlAndUnreachableHost() = runBlocking {
        assertEquals(FetchResult.Failed(FetchErrorKind.InvalidUrl), fetcher().fetch("not a url", null, null))
        val dead = server.url("/feed").toString()
        server.close()
        assertEquals(FetchResult.Failed(FetchErrorKind.Network), fetcher().fetch(dead, null, null))
    }

    @Test
    fun theKindOfNetworkProblemIsReported() = runBlocking {
        // A name that cannot resolve.
        assertEquals(FetchResult.Failed(FetchErrorKind.Dns), fetcher().fetch("https://no-such-host.invalid/feed", null, null))
        // A server that accepts the connection but never answers.
        server.enqueue(MockResponse.Builder().bodyDelay(5, java.util.concurrent.TimeUnit.SECONDS).body(xml).build())
        val slow = FeedFetcher(parser = RssParser { KXmlParser() }, timeoutSeconds = 1)
        assertEquals(FetchResult.Failed(FetchErrorKind.Timeout), slow.fetch(server.url("/slow").toString(), null, null))
    }

    @Test
    fun errorsNeverContainTheUrl() {
        server.enqueue(MockResponse.Builder().code(403).build())
        val r = fetch("/secret-token-abc") as FetchResult.Failed
        assertTrue(!r.toString().contains("secret-token-abc"))
        assertNotNull(r)
    }
}
