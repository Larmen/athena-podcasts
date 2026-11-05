package se.anders.athenapodcaster.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import se.anders.athenapodcaster.data.feeds.redactUrl
import se.anders.athenapodcaster.data.feeds.urlHash

class RedactTest {
    @Test
    fun redactionKeepsOnlySchemeAndHost() {
        val shown = redactUrl("https://feeds.example.com/private/abc123secret?key=TOKEN&u=1")
        assertEquals("https://feeds.example.com/…", shown)
        assertFalse(shown.contains("abc123") || shown.contains("TOKEN"))
        assertEquals("…", redactUrl("not a url"))
    }

    @Test
    fun hashIsStableAndDistinguishesUrls() {
        assertEquals(urlHash("https://a.test/x?k=1"), urlHash("https://a.test/x?k=1"))
        assertNotEquals(urlHash("https://a.test/x?k=1"), urlHash("https://a.test/x?k=2"))
        assertEquals(64, urlHash("x").length)
        assertFalse(urlHash("https://a.test/x?k=SECRET").contains("SECRET"))
    }
}
