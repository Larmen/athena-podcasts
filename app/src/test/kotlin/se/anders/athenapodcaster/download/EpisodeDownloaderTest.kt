package se.anders.athenapodcaster.download

import java.io.File
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import se.anders.athenapodcaster.data.download.DownloadResult
import se.anders.athenapodcaster.data.download.EpisodeDownloader

class EpisodeDownloaderTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var server: MockWebServer
    private val downloader = EpisodeDownloader()
    private val content = ByteArray(300_000) { (it % 251).toByte() }
    private lateinit var dest: File
    private val progress = mutableListOf<Pair<Long, Long>>()

    @Before fun setUp() { server = MockWebServer().also { it.start() }; dest = File(folder.root, "feed/1.mp3") }
    @After fun tearDown() = server.close()

    private fun download(path: String = "/a.mp3") =
        runBlocking { downloader.download(server.url(path).toString(), dest) { d, t -> progress += d to t } }

    private fun part() = File(dest.path + ".part")
    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    @Test
    fun downloadsAndRenamesAtomically() {
        server.enqueue(MockResponse.Builder().body(body(content)).build())
        assertEquals(DownloadResult.Done(content.size.toLong()), download())
        assertArrayEquals(content, dest.readBytes())
        assertFalse(part().exists())
        assertEquals(content.size.toLong(), progress.last().first)
        assertEquals(content.size.toLong(), progress.last().second)
    }

    @Test
    fun resumesAPartialFileWithARangeRequest() {
        part().apply { parentFile.mkdirs(); writeBytes(content.copyOfRange(0, 100_000)) }
        server.enqueue(
            MockResponse.Builder().code(206).addHeader("Content-Range", "bytes 100000-299999/300000")
                .body(body(content.copyOfRange(100_000, content.size))).build(),
        )
        assertTrue(download() is DownloadResult.Done)
        assertEquals("bytes=100000-", server.takeRequest().headers["Range"])
        assertArrayEquals(content, dest.readBytes())
    }

    @Test
    fun serverThatIgnoresRangeRestartsFromZero() {
        part().apply { parentFile.mkdirs(); writeBytes(ByteArray(50_000) { 9 }) }
        server.enqueue(MockResponse.Builder().body(body(content)).build()) // 200, not 206
        assertTrue(download() is DownloadResult.Done)
        assertArrayEquals(content, dest.readBytes())
    }

    @Test
    fun rangeNotSatisfiableRestartsOnce() {
        part().apply { parentFile.mkdirs(); writeBytes(ByteArray(10) { 1 }) }
        server.enqueue(MockResponse.Builder().code(416).build())
        server.enqueue(MockResponse.Builder().body(body(content)).build())
        assertTrue(download() is DownloadResult.Done)
        assertArrayEquals(content, dest.readBytes())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun resumeFromTheWrongOffsetStartsOver() {
        part().apply { parentFile.mkdirs(); writeBytes(ByteArray(100) { 1 }) }
        server.enqueue(MockResponse.Builder().code(206).addHeader("Content-Range", "bytes 0-299999/300000").body(body(content)).build())
        server.enqueue(MockResponse.Builder().body(body(content)).build())
        assertTrue(download() is DownloadResult.Done)
        assertArrayEquals(content, dest.readBytes())
    }

    @Test
    fun truncatedBodyKeepsThePartForTheNextAttempt() {
        // The server announces the full length, sends a third of it and hangs up.
        server.enqueue(
            MockResponse.Builder().body(body(content.copyOfRange(0, 100_000))).setHeader("Content-Length", content.size.toString())
                .onResponseBody(SocketEffect.CloseSocket()).build(),
        )
        val result = download()
        assertEquals(DownloadResult.Failed(retryable = true), result)
        assertFalse(dest.exists())
        assertTrue("part should survive for resume", part().exists() && part().length() in 1 until content.size.toLong())
    }

    @Test
    fun httpErrors() {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(DownloadResult.Failed(retryable = false), download())
        server.enqueue(MockResponse.Builder().code(403).build())
        assertEquals(DownloadResult.Failed(retryable = false), download())
        server.enqueue(MockResponse.Builder().code(503).build())
        assertEquals(DownloadResult.Failed(retryable = true), download())
        server.enqueue(MockResponse.Builder().code(429).build())
        assertEquals(DownloadResult.Failed(retryable = true), download())
        assertFalse(dest.exists())
    }

    @Test
    fun followsRedirectsToAnotherPath() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/real.mp3").toString()).build())
        server.enqueue(MockResponse.Builder().body(body(content)).build())
        assertTrue(download("/short") is DownloadResult.Done)
        assertArrayEquals(content, dest.readBytes())
    }

    @Test
    fun unreachableServerIsRetryableAndBadUrlIsNot() = runBlocking {
        val dead = server.url("/a.mp3").toString()
        server.close()
        assertEquals(DownloadResult.Failed(retryable = true), downloader.download(dead, dest) { _, _ -> })
        assertEquals(DownloadResult.Failed(retryable = false), downloader.download("not a url", dest) { _, _ -> })
    }

    @Test
    fun sendsAGenericUserAgentOnly() {
        server.enqueue(MockResponse.Builder().body(body(content)).build())
        download("/secret-token-xyz/a.mp3")
        assertEquals("AthenaPodcaster/0.1", server.takeRequest().headers["User-Agent"])
    }
}
