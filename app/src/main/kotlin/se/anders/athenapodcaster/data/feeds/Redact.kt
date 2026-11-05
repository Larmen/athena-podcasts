package se.anders.athenapodcaster.data.feeds

import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The only form of a feed URL that may be shown or logged (constitution §4): scheme and host, never the path or
 * query, where tokens live. "https://example.com/private/abc?key=1" becomes "https://example.com/…".
 */
fun redactUrl(url: String): String {
    val parsed = url.toHttpUrlOrNull() ?: return "…"
    return "${parsed.scheme}://${parsed.host}/…"
}

/** Stable fingerprint of a URL, used to spot duplicate subscriptions without keeping the URL in the clear. */
fun urlHash(url: String): String =
    MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
