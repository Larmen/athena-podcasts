package se.anders.athenapodcaster.domain

/** FR-014: an episode counts as played once it is within this long of its end. */
const val PLAYED_TAIL_MS = 15_000L

/** Episodes this short only count as played when they actually end, so a 20 s clip is not "played" at 5 s. */
private const val MIN_DURATION_FOR_TAIL_MS = 2 * PLAYED_TAIL_MS

/** What to store for the episode right now: [played] means mark it played and forget the position (FR-014, FR-022). */
data class Progress(val positionMs: Long, val played: Boolean)

fun progressFor(positionMs: Long, durationMs: Long, ended: Boolean): Progress {
    val inTail = durationMs >= MIN_DURATION_FOR_TAIL_MS && durationMs - positionMs <= PLAYED_TAIL_MS
    return if (ended || inTail) Progress(0, played = true) else Progress(positionMs.coerceAtLeast(0), played = false)
}
