package se.anders.athenapodcaster.domain

/** What is playing right now, as far as saving progress cares. */
data class NowPlaying(val episodeId: Long, val positionMs: Long, val durationMs: Long)

/**
 * Decides when playback progress is written (FR-022): on every tick while playing, whenever playback stops
 * (**including a pause during buffering**, where "is playing" does not change because it was already false) and at the end.
 * Pure, so the rules are tested without a player.
 */
class ProgressTracker(
    private val current: () -> NowPlaying?,
    private val persist: (episodeId: Long, Progress) -> Unit,
) {
    fun onTick() = save(ended = false)
    fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) save(ended = false) }
    fun onPlayWhenReadyChanged(playWhenReady: Boolean) { if (!playWhenReady) save(ended = false) }
    fun onEnded() = save(ended = true)

    private fun save(ended: Boolean) {
        val now = current() ?: return
        persist(now.episodeId, progressFor(now.positionMs, now.durationMs, ended))
    }
}
