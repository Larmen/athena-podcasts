package se.anders.athenapodcaster

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.secrets.KeystoreSecretStore

/** The real Android Keystore on the device (constitution §4). */
@RunWith(AndroidJUnit4::class)
class SecretStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = KeystoreSecretStore(context)

    @Test
    fun roundTripReplaceAndDelete() {
        val ref = store.put("https://x.test/private/TOKEN/f.xml")
        assertEquals("https://x.test/private/TOKEN/f.xml", store.get(ref))
        store.replace(ref, "https://y.test/f.xml")
        assertEquals("https://y.test/f.xml", store.get(ref))
        store.delete(ref)
        assertNull(store.get(ref))
    }

    @Test
    fun fileOnDiskDoesNotContainThePlaintext() {
        val ref = store.put("https://x.test/private/SUPERSECRET/f.xml")
        val bytes = File(context.filesDir, "secrets/$ref").readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("SUPERSECRET"))
        assertTrue(bytes.size > 12) // IV + ciphertext
        store.delete(ref)
    }

    @Test
    fun aDamagedFileReadsAsMissingNotACrash() {
        val ref = store.put("value")
        File(context.filesDir, "secrets/$ref").writeBytes(ByteArray(40) { 7 })
        assertNull(store.get(ref))
        store.delete(ref)
    }

    @Test
    fun unknownRefIsMissing() {
        assertNull(store.get("does-not-exist"))
    }
}
