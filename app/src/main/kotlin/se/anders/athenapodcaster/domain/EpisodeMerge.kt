package se.anders.athenapodcaster.domain

import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.feeds.ParsedEpisode

/** What a refresh changes in the database (FR-011). */
data class EpisodeMerge(val inserts: List<Episode>, val updates: List<Episode>)

/**
 * Compares the episodes already stored for a feed with a freshly parsed feed. Episodes are matched by guid.
 * New ones are inserted; existing ones only get their feed-supplied metadata refreshed, so position, played and
 * download state are never touched. Episodes that vanished from the feed are kept (their file may still exist).
 * A guid repeated inside one feed counts once (first occurrence wins).
 */
fun mergeEpisodes(feedId: Long, existing: List<Episode>, parsed: List<ParsedEpisode>): EpisodeMerge {
    val byGuid = existing.associateBy { it.guid }
    val seen = HashSet<String>()
    val inserts = ArrayList<Episode>()
    val updates = ArrayList<Episode>()
    for (p in parsed) {
        if (!seen.add(p.guid)) continue
        val old = byGuid[p.guid]
        if (old == null) {
            inserts += Episode(
                feedId = feedId,
                guid = p.guid,
                title = p.title,
                publishedAt = p.publishedAt,
                durationSec = p.durationSec,
                enclosureUrl = p.enclosureUrl,
                enclosureType = p.enclosureType,
                enclosureLength = p.enclosureLength,
                imageUrl = p.imageUrl,
                description = p.description,
            )
        } else {
            val refreshed = old.copy(
                title = p.title,
                publishedAt = if (p.publishedAt != 0L) p.publishedAt else old.publishedAt,
                durationSec = p.durationSec ?: old.durationSec,
                enclosureUrl = p.enclosureUrl,
                enclosureType = p.enclosureType ?: old.enclosureType,
                enclosureLength = p.enclosureLength ?: old.enclosureLength,
                imageUrl = p.imageUrl ?: old.imageUrl,
                description = p.description ?: old.description,
            )
            if (refreshed != old) updates += refreshed
        }
    }
    return EpisodeMerge(inserts, updates)
}
