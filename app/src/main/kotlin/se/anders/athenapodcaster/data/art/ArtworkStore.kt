package se.anders.athenapodcaster.data.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import se.anders.athenapodcaster.data.feeds.urlHash
import se.anders.athenapodcaster.domain.ArtStyle
import se.anders.athenapodcaster.domain.GRAYSCALE_MATRIX

/** Largest side, in pixels, of the cover art put on the notification and lock screen. */
private const val MEDIA_ART_PX = 512

/**
 * Cover art (FR-040..043). Pictures are downloaded once with the feed refresh into the app's private storage, so
 * they show offline afterwards, and decoded at the size they are drawn (never at full size). No placeholder is
 * ever shown for missing art: callers simply get null. URLs are never logged.
 */
class ArtworkStore(context: Context, client: OkHttpClient = OkHttpClient()) {
    private val dir = File(context.applicationContext.filesDir, "art").apply { mkdirs() }
    private val client = client.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private val _version = MutableStateFlow(0)

    /** Bumped whenever a new picture lands, so screens showing "no art yet" can look again. */
    val version: StateFlow<Int> = _version

    private fun fileFor(url: String) = File(dir, urlHash(url).take(40))

    fun has(url: String?): Boolean = url != null && fileFor(url).isFile

    /** Downloads the picture unless it is already stored. Best effort: any problem just leaves it missing. */
    suspend fun ensure(url: String?) {
        if (url.isNullOrBlank() || has(url)) return
        val httpUrl = url.toHttpUrlOrNull() ?: return
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(httpUrl).header("User-Agent", "AthenaPodcaster/0.1").build()
                client.newCall(request).execute().use { response ->
                    val body = response.body
                    val type = body.contentType()
                    if (!response.isSuccessful || type?.type != "image" || body.contentLength() > MAX_BYTES) return@use
                    val temp = File(dir, "${fileFor(url).name}.tmp")
                    var total = 0L
                    temp.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                total += n
                                if (total > MAX_BYTES) { temp.delete(); return@use }
                                out.write(buffer, 0, n)
                            }
                        }
                    }
                    // Keep it only if it really decodes as a picture.
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temp.path, bounds)
                    if (bounds.outWidth > 0 && temp.renameTo(fileFor(url))) _version.value++ else temp.delete()
                }
            } catch (_: Exception) {
                // offline or a bad server: art is optional
            }
        }
    }

    /** The first of [urls] that is stored, decoded to at most [maxPx] on its longest side; null if none is. */
    suspend fun firstBitmap(urls: List<String?>, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        for (url in urls) {
            if (url == null) continue
            bitmap(url, maxPx)?.let { return@withContext it }
        }
        null
    }

    private fun bitmap(url: String, maxPx: Int): ImageBitmap? {
        val key = "$url@$maxPx"
        cache.get(key)?.let { return it }
        val file = fileFor(url).takeIf { it.isFile } ?: return null
        val decoded = decode(file, maxPx) ?: return null
        return decoded.asImageBitmap().also { cache.put(key, it) }
    }

    private fun decode(file: File, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPx) }
        return BitmapFactory.decodeFile(file.path, options)
    }

    /**
     * Cover art for the notification and lock screen (FR-041): the first stored of [urls], scaled to at most 512 px,
     * in the chosen [style], as JPEG bytes. Null if there is none.
     */
    suspend fun mediaBytes(urls: List<String?>, style: ArtStyle, maxPx: Int = MEDIA_ART_PX): ByteArray? = withContext(Dispatchers.IO) {
        val file = urls.firstNotNullOfOrNull { url -> url?.let(::fileFor)?.takeIf { it.isFile } } ?: return@withContext null
        var bitmap = decode(file, maxPx) ?: return@withContext null
        if (style == ArtStyle.Grayscale) bitmap = grayscale(bitmap)
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    private fun grayscale(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(GRAYSCALE_MATRIX)) }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /** Removes the pictures of a feed that was unsubscribed. */
    fun delete(urls: List<String?>) {
        urls.filterNotNull().forEach { fileFor(it).delete() }
        cache.evictAll()
        _version.value++
    }

    private companion object {
        const val MAX_BYTES = 5L * 1024 * 1024
        const val CACHE_BYTES = 8 * 1024 * 1024
    }
}

/**
 * Power-of-two decode factor so the decoded picture is not much bigger than needed:
 * the largest factor that keeps the longest side at or above [maxPx].
 */
fun sampleSizeFor(width: Int, height: Int, maxPx: Int): Int {
    if (maxPx <= 0) return 1
    val longest = maxOf(width, height)
    var sample = 1
    while (longest / (sample * 2) >= maxPx) sample *= 2
    return sample
}
