package se.anders.athenapodcaster.domain

import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode

/**
 * FR-031: which episodes of one feed to download automatically. Only the newest [count] episodes are considered
 * (never the back catalogue), and of those only the ones that are unplayed and not already downloaded, queued
 * or running. A failed one is tried again. If the newest episode is already played, nothing is downloaded.
 */
fun selectForAutoDownload(episodes: List<Episode>, count: Int): List<Episode> =
    episodes
        .sortedWith(compareByDescending<Episode> { it.publishedAt }.thenByDescending { it.id })
        .take(count.coerceAtLeast(0))
        .filter { !it.played && (it.downloadState == DownloadState.None || it.downloadState == DownloadState.Failed) }

/** File extension for a downloaded enclosure: from its URL if it is a known audio type, else from the MIME type, else mp3. */
fun audioExtension(url: String, mimeType: String?): String {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    val fromPath = path.substringAfterLast('.', "").takeIf { it in KNOWN_EXTENSIONS }
    if (fromPath != null) return fromPath
    return when (mimeType?.lowercase()?.substringBefore(';')?.trim()) {
        "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/aac" -> "m4a"
        "audio/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/flac" -> "flac"
        else -> "mp3"
    }
}

private val KNOWN_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac")
