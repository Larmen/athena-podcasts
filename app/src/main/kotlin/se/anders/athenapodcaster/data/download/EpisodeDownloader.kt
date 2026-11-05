package se.anders.athenapodcaster.data.download

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

sealed interface DownloadResult {
    data class Done(val bytes: Long) : DownloadResult

    /** [retryable] failures (network, 5xx, truncated file) are worth another attempt; the rest (404, 403…) are not. */
    data class Failed(val retryable: Boolean) : DownloadResult
}

/**
 * Downloads one file with resume (FR-032). Data goes to `<dest>.part`; a later attempt continues it with an HTTP
 * Range request when the server answers 206, and starts over when it answers 200. The finished file is renamed
 * into place atomically, so a half-written file is never mistaken for an episode. URLs are never logged.
 */
class EpisodeDownloader(client: OkHttpClient = OkHttpClient()) {
    private val client: OkHttpClient = client.newBuilder()
        .followRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS) // large files: only the per-read timeout applies
        .build()

    /** [onProgress] gets (bytes so far, total bytes or -1 if unknown). */
    suspend fun download(url: String, dest: File, onProgress: suspend (Long, Long) -> Unit): DownloadResult = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull() ?: return@withContext DownloadResult.Failed(retryable = false)
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        attempt(httpUrl, dest, part, onProgress, mayRestart = true)
    }

    private suspend fun attempt(url: okhttp3.HttpUrl, dest: File, part: File, onProgress: suspend (Long, Long) -> Unit, mayRestart: Boolean): DownloadResult {
        val existing = if (part.isFile) part.length() else 0L
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()
        val call = client.newCall(request)
        coroutineContext.job.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            call.execute().use { response ->
                return when (response.code) {
                    200 -> write(response, dest, part, append = false, offset = 0, onProgress)
                    206 -> {
                        // Only continue if the server really resumes where we asked.
                        val start = response.header("Content-Range")?.substringAfter("bytes ", "")?.substringBefore('-')?.trim()?.toLongOrNull()
                        if (start == existing) write(response, dest, part, append = true, offset = existing, onProgress)
                        else if (mayRestart) { part.delete(); attempt(url, dest, part, onProgress, mayRestart = false) }
                        else DownloadResult.Failed(retryable = true)
                    }
                    416 -> if (mayRestart) { part.delete(); attempt(url, dest, part, onProgress, mayRestart = false) } else DownloadResult.Failed(retryable = true)
                    408, 425, 429, in 500..599 -> DownloadResult.Failed(retryable = true)
                    else -> DownloadResult.Failed(retryable = false)
                }
            }
        } catch (_: IOException) {
            coroutineContext.ensureActive() // a cancelled download is not a failure
            return DownloadResult.Failed(retryable = true)
        }
    }

    private suspend fun write(response: Response, dest: File, part: File, append: Boolean, offset: Long, onProgress: suspend (Long, Long) -> Unit): DownloadResult {
        val body = response.body
        val length = body.contentLength()
        val total = if (length >= 0) offset + length else -1L
        var written = offset
        if (!append) part.delete()
        java.io.FileOutputStream(part, append).use { out ->
            val buffer = ByteArray(64 * 1024)
            body.byteStream().use { input ->
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    written += n
                    onProgress(written, total)
                }
            }
        }
        // A server that closed early leaves a short file: keep the .part so the next attempt can resume it.
        if (total >= 0 && written != total) return DownloadResult.Failed(retryable = true)
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) return DownloadResult.Failed(retryable = true)
        return DownloadResult.Done(written)
    }

    companion object {
        const val USER_AGENT = "AthenaPodcaster/0.1"
    }
}
