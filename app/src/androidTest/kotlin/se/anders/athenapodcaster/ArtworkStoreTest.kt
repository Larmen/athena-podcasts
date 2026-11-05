package se.anders.athenapodcaster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.art.ArtworkStore
import se.anders.athenapodcaster.domain.ArtStyle

/** Cover art on a real device: download once, cache, decode small, and hand a grayscale JPEG to the lock screen. */
@RunWith(AndroidJUnit4::class)
class ArtworkStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer
    private lateinit var store: ArtworkStore
    private val urls = mutableListOf<String>()

    private fun png(size: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        store = ArtworkStore(context)
    }

    @After
    fun tearDown() {
        server.close()
        store.delete(urls)
    }

    private fun url(path: String) = server.url(path).toString().also { urls += it }

    @Test
    fun downloadsOnceAndThenWorksOffline() = runBlocking {
        server.enqueue(MockResponse.Builder().body(Buffer().write(png(600, Color.RED))).addHeader("Content-Type", "image/png").build())
        val u = url("/art-${System.nanoTime()}.png")
        assertFalse(store.has(u))
        val before = store.version.value
        store.ensure(u)
        assertTrue(store.has(u))
        assertTrue(store.version.value > before)
        server.close() // offline from here on
        store.ensure(u) // already stored: no request, no error
        assertNotNull(store.firstBitmap(listOf(u), 300))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun decodesNoBiggerThanNeeded() = runBlocking {
        server.enqueue(MockResponse.Builder().body(Buffer().write(png(2000, Color.BLUE))).addHeader("Content-Type", "image/png").build())
        val u = url("/big-${System.nanoTime()}.png")
        store.ensure(u)
        val bmp = store.firstBitmap(listOf(u), 280)!!
        assertTrue("decoded ${bmp.width}px for a 280px slot", bmp.width in 280..560)
    }

    @Test
    fun firstStoredPictureWinsAndMissingOnesAreSkipped() = runBlocking {
        server.enqueue(MockResponse.Builder().body(Buffer().write(png(100, Color.GREEN))).addHeader("Content-Type", "image/png").build())
        val showArt = url("/show-${System.nanoTime()}.png")
        store.ensure(showArt)
        val episodeArt = url("/never-fetched-${System.nanoTime()}.png") // not stored
        assertNotNull(store.firstBitmap(listOf(episodeArt, showArt), 100))
        assertNull(store.firstBitmap(listOf(episodeArt, null), 100))
    }

    @Test
    fun rejectsHtmlAndBrokenImages() = runBlocking {
        server.enqueue(MockResponse.Builder().body("<html>sign in</html>").addHeader("Content-Type", "text/html").build())
        val html = url("/page-${System.nanoTime()}")
        store.ensure(html)
        assertFalse(store.has(html))
        server.enqueue(MockResponse.Builder().body("not really an image").addHeader("Content-Type", "image/png").build())
        val broken = url("/broken-${System.nanoTime()}.png")
        store.ensure(broken)
        assertFalse(store.has(broken))
        server.enqueue(MockResponse.Builder().code(404).build())
        val missing = url("/gone-${System.nanoTime()}.png")
        store.ensure(missing)
        assertFalse(store.has(missing))
    }

    @Test
    fun lockScreenBytesAreSmallAndGrayscaleWhenAsked() = runBlocking {
        server.enqueue(MockResponse.Builder().body(Buffer().write(png(1500, Color.rgb(220, 30, 30)))).addHeader("Content-Type", "image/png").build())
        val u = url("/colour-${System.nanoTime()}.png")
        store.ensure(u)

        val gray = BitmapFactory.decodeByteArray(store.mediaBytes(listOf(u), ArtStyle.Grayscale)!!.let { it }, 0, store.mediaBytes(listOf(u), ArtStyle.Grayscale)!!.size)
        assertTrue("longest side ${gray.width}", gray.width <= 1500 && gray.width in 375..750)
        val p = gray.getPixel(gray.width / 2, gray.height / 2)
        assertTrue("r=${Color.red(p)} g=${Color.green(p)} b=${Color.blue(p)}", kotlin.math.abs(Color.red(p) - Color.green(p)) < 8 && kotlin.math.abs(Color.green(p) - Color.blue(p)) < 8)

        val originalBytes = store.mediaBytes(listOf(u), ArtStyle.Original)!!
        val color = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
        val q = color.getPixel(color.width / 2, color.height / 2)
        assertTrue("original should stay red", Color.red(q) > 150 && Color.green(q) < 100)
        assertTrue(originalBytes.size < 200_000)
        assertNull(store.mediaBytes(listOf(null, "https://nothing.invalid/x"), ArtStyle.Original))
    }
}
