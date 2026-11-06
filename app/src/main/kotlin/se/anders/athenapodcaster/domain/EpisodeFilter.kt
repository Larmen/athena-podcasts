package se.anders.athenapodcaster.domain

import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode

/** What the episode list shows (FR-014): everything, only what is on the phone, or only what has not been played. */
enum class EpisodeFilter {
    All, Downloaded, Unplayed;

    /** The filter button steps through them in this order and wraps around. */
    fun next(): EpisodeFilter = entries[(ordinal + 1) % entries.size]

    companion object {
        /** What a podcast's episode list shows when the app starts: the episodes still to hear (FR-014). */
        val Default = Unplayed
    }

    fun apply(episodes: List<Episode>): List<Episode> = when (this) {
        All -> episodes
        Downloaded -> episodes.filter { it.downloadState == DownloadState.Done }
        Unplayed -> episodes.filter { !it.played }
    }
}
