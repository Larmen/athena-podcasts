package se.anders.athenapodcaster.data.feeds

import android.util.Xml
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

data class ParsedFeed(
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    /** Epoch milliseconds; 0 when the feed gives no usable date. */
    val publishedAt: Long,
    val durationSec: Int?,
    val enclosureUrl: String,
    val enclosureType: String?,
    val enclosureLength: Long?,
    /** `<itunes:image href>` of the episode, when the feed gives one (FR-040). */
    val imageUrl: String? = null,
    /** Show notes as plain text (FR-017). */
    val description: String? = null,
)

class FeedParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Streaming RSS 2.0 parser (plan §3.2, FR-001, NFR-007). Reads iTunes tags where useful and ignores the rest.
 * Tolerates a missing `<guid>` (falls back to the enclosure URL), odd date and duration formats, BOMs and
 * CDATA. Items without an audio enclosure are skipped. Error messages never contain feed content or URLs.
 *
 * [newParser] exists so unit tests can supply a JVM parser; on a device it is Android's own.
 */
class RssParser(private val newParser: () -> XmlPullParser = { Xml.newPullParser() }) {

    fun parse(input: InputStream): ParsedFeed {
        val parser = newParser()
        try {
            // Android's Xml.newPullParser() turns namespace processing on, which would report "itunes:duration"
            // as "duration". Match on prefixed names everywhere by switching it off explicitly.
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input.skipBom(), null)
            return readFeed(parser)
        } catch (e: XmlPullParserException) {
            throw FeedParseException("Not a valid XML document", e)
        }
    }

    private fun readFeed(p: XmlPullParser): ParsedFeed {
        var sawRss = false
        var title: String? = null
        var description: String? = null
        var image: String? = null
        val episodes = ArrayList<ParsedEpisode>()
        // Depth-aware walk: channel-level tags are only those directly under <channel>.
        var depth = 0
        var channelDepth = -1
        var sawChannel = false
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val name = p.name
                    when {
                        depth == 1 -> if (name == "rss" || name == "RDF" || name == "rdf:RDF") sawRss = true else throw FeedParseException("Not an RSS feed")
                        // Items count wherever they are: RSS 1.0 and some generators put them next to <channel>, not in it.
                        name == "item" -> { readItem(p)?.let(episodes::add); depth-- }
                        name == "channel" && !sawChannel -> { sawChannel = true; channelDepth = depth }
                        // Show-level tags are only the direct children of the (still open) channel; the first one wins.
                        channelDepth != -1 && depth == channelDepth + 1 -> when (name) {
                            "title" -> { val t = readText(p); if (title == null) title = t; depth-- }
                            "description" -> { val d = readText(p); if (description == null) description = d; depth-- }
                            "itunes:image" -> { image = image ?: p.attr("href"); skip(p); depth-- }
                            "image" -> { image = image ?: readImageUrl(p); depth-- }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "channel" && depth == channelDepth) channelDepth = -1
                    depth--
                }
            }
            event = p.next()
        }
        if (!sawRss || !sawChannel) throw FeedParseException("Not an RSS feed")
        return ParsedFeed(
            title = cleanText(title).ifEmpty { "Untitled" },
            description = cleanText(description).ifEmpty { null },
            imageUrl = image?.trim()?.ifEmpty { null },
            episodes = episodes,
        )
    }

    /** Reads `<image><url>…</url></image>`; leaves the parser on the closing `</image>`. */
    private fun readImageUrl(p: XmlPullParser): String? {
        var url: String? = null
        while (p.next() != XmlPullParser.END_TAG || p.name != "image") {
            if (p.eventType == XmlPullParser.START_TAG && p.name == "url") url = readText(p) else if (p.eventType == XmlPullParser.START_TAG) skip(p)
            if (p.eventType == XmlPullParser.END_DOCUMENT) break
        }
        return url?.trim()?.ifEmpty { null }
    }

    /** Parser is on `<item>`; leaves it on `</item>`. Returns null for items without a usable audio enclosure. */
    private fun readItem(p: XmlPullParser): ParsedEpisode? {
        var title: String? = null
        var guid: String? = null
        var pubDate: String? = null
        var duration: String? = null
        var url: String? = null
        var type: String? = null
        var length: Long? = null
        var mediaUrl: String? = null // <media:content>, used only when there is no <enclosure>
        var mediaType: String? = null
        var image: String? = null
        val notes = ArrayList<String>() // description, content:encoded, itunes:summary: the longest wins (FR-017)
        while (true) {
            val ev = p.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && p.name == "item") break
            if (ev != XmlPullParser.START_TAG) continue
            when (p.name) {
                "title" -> title = readText(p)
                "guid" -> guid = readText(p)
                "pubDate" -> pubDate = readText(p)
                "itunes:duration" -> duration = readText(p)
                "description", "content:encoded", "itunes:summary" -> notes += readText(p)
                "enclosure" -> {
                    if (url == null) {
                        url = p.attr("url")?.trim()
                        type = p.attr("type")?.trim()?.lowercase()
                        length = p.attr("length")?.trim()?.toLongOrNull()
                    }
                    skip(p)
                }
                "itunes:image" -> { image = image ?: p.attr("href")?.trim(); skip(p) }
                "media:content" -> {
                    if (mediaUrl == null) {
                        mediaUrl = p.attr("url")?.trim()
                        mediaType = p.attr("type")?.trim()?.lowercase() ?: if (p.attr("medium") == "audio") "audio/mpeg" else null
                        if (length == null) length = p.attr("fileSize")?.trim()?.toLongOrNull()
                    }
                    skip(p)
                }
                else -> skip(p)
            }
        }
        if (url.isNullOrEmpty()) { url = mediaUrl; type = mediaType }
        val enclosureUrl = url?.takeIf { it.isNotEmpty() && isAudio(it, type) } ?: return null
        return ParsedEpisode(
            guid = guid?.trim().orEmpty().ifEmpty { enclosureUrl },
            title = cleanText(title).ifEmpty { "Untitled" },
            publishedAt = parseFeedDate(pubDate) ?: 0L,
            durationSec = parseDuration(duration),
            enclosureUrl = enclosureUrl,
            enclosureType = type?.ifEmpty { null },
            enclosureLength = length?.takeIf { it > 0 },
            imageUrl = image?.ifEmpty { null },
            description = notes.map(::showNotesToText).maxByOrNull { it.length }?.ifEmpty { null },
        )
    }

    /** Text of the current element, including CDATA; parser ends on its end tag. Nested tags are skipped. */
    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        var nested = 0
        while (true) {
            when (p.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> if (nested == 0) sb.append(p.text)
                XmlPullParser.START_TAG -> nested++
                XmlPullParser.END_TAG -> if (nested == 0) return sb.toString() else nested--
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
    }

    private fun skip(p: XmlPullParser) {
        var nested = 1
        while (nested > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> nested++
                XmlPullParser.END_TAG -> nested--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

    /**
     * Audio if the type says so. Without a type (some private feeds leave it out and use extension-less download
     * links) anything that is not obviously a video, image or document is accepted.
     */
    private fun isAudio(url: String, type: String?): Boolean {
        if (!type.isNullOrEmpty()) return type.startsWith("audio/") || (type == "application/octet-stream" || type == "binary/octet-stream") && !hasNonAudioExtension(url)
        return !hasNonAudioExtension(url)
    }

    private fun hasNonAudioExtension(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return NON_AUDIO_EXTENSIONS.any { path.endsWith(it) }
    }

    private fun InputStream.skipBom(): InputStream {
        val buffered = if (markSupported()) this else java.io.BufferedInputStream(this)
        buffered.mark(3)
        val head = ByteArray(3)
        val n = buffered.read(head)
        if (!(n == 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte())) buffered.reset()
        return buffered
    }

    private companion object {
        val NON_AUDIO_EXTENSIONS = listOf(".mp4", ".m4v", ".mov", ".webm", ".mkv", ".avi", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".pdf", ".epub", ".txt", ".html", ".zip")
    }
}

/** Titles may carry stray whitespace and simple HTML; keep readable text only. */
internal fun cleanText(raw: String?): String =
    raw.orEmpty().replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()

/** `itunes:duration`: seconds ("3600", "3600.5"), "mm:ss" or "hh:mm:ss". Null when unusable. */
fun parseDuration(raw: String?): Int? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val parts = text.split(':')
    if (parts.size > 3) return null
    var total = 0.0
    for (part in parts) {
        val value = part.trim().toDoubleOrNull() ?: return null
        if (value < 0) return null
        total = total * 60 + value
    }
    return total.toInt().takeIf { it > 0 }
}

private val DATE_FORMATTERS: List<DateTimeFormatter> = listOf(
    "EEE, d MMM yyyy HH:mm:ss Z",
    "EEE, d MMM yyyy HH:mm:ss zzz",
    "EEE, d MMM yyyy HH:mm Z",
    "EEE, d MMM yyyy HH:mm zzz",
    "EEE, d MMM yy HH:mm:ss Z",
    "EEE, d MMM yy HH:mm:ss zzz",
    "d MMM yyyy HH:mm:ss Z",
    "d MMM yyyy HH:mm:ss zzz",
    "d MMM yy HH:mm:ss Z",
    "d MMM yy HH:mm:ss zzz",
    "d MMM yyyy HH:mm Z",
    "d MMM yyyy HH:mm zzz",
    "EEE d MMM yyyy HH:mm:ss Z",
).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

/** RFC 822 / 1123 dates as feeds actually write them (zone names, no weekday, two-digit years), plus ISO 8601. */
fun parseFeedDate(raw: String?): Long? {
    val text = raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
    if (text.isEmpty()) return null
    // The weekday is redundant, and some feeds get it wrong ("Wed, 05 Nov 2026" is a Thursday); java.time would
    // reject the whole date, so it is dropped before parsing and the day of the month is trusted.
    val noWeekday = text.replace(Regex("^[A-Za-z]{3,9},?\\s+"), "")
    if (noWeekday != text) parseFeedDate(noWeekday)?.let { return it }
    try { return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() } catch (_: DateTimeParseException) {}
    for (formatter in DATE_FORMATTERS) {
        try { return ZonedDateTime.parse(text, formatter).toInstant().toEpochMilli() } catch (_: DateTimeParseException) {}
    }
    try { return OffsetDateTime.parse(text).toInstant().toEpochMilli() } catch (_: DateTimeParseException) {}
    return null
}

private val PARAGRAPH_END = Regex("(?i)</(p|div|h[1-6]|blockquote)\\s*>")
private val BLOCK_END = Regex("(?i)</(ul|ol)\\s*>|<br\\s*/?>")
private val LIST_ITEM = Regex("(?i)<li[^>]*>")
private val TAGS = Regex("<[^>]*>")
private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
private const val MAX_NOTES = 20_000

/** Show notes: HTML (or plain text) to readable plain text. Paragraphs and list items keep their line breaks (FR-017). */
fun showNotesToText(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    var t = raw.replace("\r", "")
    t = LIST_ITEM.replace(t, "\n• ")
    t = PARAGRAPH_END.replace(t, "\n\n")
    t = BLOCK_END.replace(t, "\n")
    t = TAGS.replace(t, "")
    t = ENTITY.replace(t) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> when (e.lowercase()) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                "ndash" -> "–"; "mdash" -> "—"; "hellip" -> "…"; "rsquo", "lsquo" -> "'"; "ldquo", "rdquo" -> "\""
                else -> null
            }
        } ?: m.value
    }
    t = t.lines().joinToString("\n") { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
    t = t.replace(Regex("\n{3,}"), "\n\n").trim()
    return if (t.length > MAX_NOTES) t.take(MAX_NOTES).trimEnd() + "…" else t
}
