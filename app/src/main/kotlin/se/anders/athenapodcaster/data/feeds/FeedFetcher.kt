package se.anders.athenapodcaster.data.feeds

import java.io.FilterInputStream
import android.util.Log
import java.io.InterruptedIOException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Why a fetch failed. Names are stored in `Feed.lastError`; no URL or feed content is ever part of an error. */
enum class FetchErrorKind { InvalidUrl, Network, Dns, Timeout, Tls, AuthFailed, Http, NotAFeed, TooLarge }

/** Problems of the connection itself (offline, name lookup, timeout), as opposed to something wrong with the feed. */
fun FetchErrorKind.isConnectivity(): Boolean = this == FetchErrorKind.Network || this == FetchErrorKind.Dns || this == FetchErrorKind.Timeout

sealed interface FetchResult {
    data class Fetched(
        val feed: ParsedFeed,
        val etag: String?,
        val lastModified: String?,
        /** Set when the server permanently moved the feed (301/308); the stored URL should be replaced. */
        val movedTo: String?,
    ) : FetchResult

    data object NotModified : FetchResult

    data class Failed(val kind: FetchErrorKind, val httpCode: Int? = null) : FetchResult
}

interface FeedSource {
    suspend fun fetch(url: String, etag: String?, lastModified: String?): FetchResult
}

/**
 * Downloads and parses a feed (plan §3.2, FR-035). Conditional GET with ETag / Last-Modified, up to five
 * redirects, transparent gzip, a size cap and timeouts. Nothing here logs URLs (constitution §4).
 */
class FeedFetcher(
    client: OkHttpClient = OkHttpClient(),
    private val parser: RssParser = RssParser(),
    private val maxBytes: Long = MAX_FEED_BYTES,
    timeoutSeconds: Long = 30,
) : FeedSource {
    private val client: OkHttpClient = client.newBuilder()
        .followRedirects(true)
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(url: String, etag: String?, lastModified: String?): FetchResult = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull() ?: return@withContext FetchResult.Failed(FetchErrorKind.InvalidUrl)
        val request = Request.Builder().url(httpUrl)
            .header("User-Agent", USER_AGENT)
            .apply {
                etag?.let { header("If-None-Match", it) }
                lastModified?.let { header("If-Modified-Since", it) }
            }
            .build()
        try {
            client.newCall(request).execute().use { response -> handle(response) }
        } catch (_: TooLargeException) {
            FetchResult.Failed(FetchErrorKind.TooLarge)
        } catch (e: IOException) {
            // Say what kind of problem it was, so the app can show it. Only the class name is logged: never the
            // message, which can contain the address (constitution §4).
            runCatching { Log.w("FeedFetcher", "fetch failed: ${e.javaClass.simpleName}") } // Log is not available in plain JVM tests
            FetchResult.Failed(kindOf(e))
        }
    }

    private fun handle(response: Response): FetchResult {
        when (val code = response.code) {
            304 -> return FetchResult.NotModified
            401, 403, 410 -> return FetchResult.Failed(FetchErrorKind.AuthFailed, code)
            !in 200..299 -> return FetchResult.Failed(FetchErrorKind.Http, code)
        }
        val body = response.body
        val parsed = try {
            parser.parse(LimitedInputStream(body.byteStream(), maxBytes))
        } catch (_: FeedParseException) {
            return FetchResult.Failed(FetchErrorKind.NotAFeed)
        }
        return FetchResult.Fetched(
            feed = parsed,
            etag = response.header("ETag"),
            lastModified = response.header("Last-Modified"),
            movedTo = permanentRedirectTarget(response),
        )
    }

    /** The final URL, but only if every redirect hop on the way was permanent (301/308). */
    private fun permanentRedirectTarget(response: Response): String? {
        var hop = response.priorResponse ?: return null
        while (true) {
            if (hop.code != 301 && hop.code != 308) return null
            hop = hop.priorResponse ?: break
        }
        return response.request.url.toString()
    }

    private fun kindOf(e: IOException): FetchErrorKind = when (e) {
        is UnknownHostException -> FetchErrorKind.Dns
        is SocketTimeoutException, is InterruptedIOException -> FetchErrorKind.Timeout
        is SSLException -> FetchErrorKind.Tls
        else -> FetchErrorKind.Network
    }

    private class TooLargeException : IOException("Feed too large")

    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) add(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) add(it.toLong()) }
        private fun add(n: Long) { count += n; if (count > limit) throw TooLargeException() }
    }

    companion object {
        const val MAX_FEED_BYTES = 20L * 1024 * 1024
        const val USER_AGENT = "AthenaPodcaster/0.1"
    }
}
