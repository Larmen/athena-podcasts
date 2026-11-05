package se.anders.athenapodcaster.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRulesTest {
    private val hour = 3_600_000L

    @Test
    fun midEpisodeStoresPosition() {
        assertEquals(Progress(120_000, played = false), progressFor(120_000, hour, ended = false))
    }

    @Test
    fun endingMarksPlayedAndForgetsPosition() {
        assertEquals(Progress(0, played = true), progressFor(hour, hour, ended = true))
    }

    @Test
    fun lastFifteenSecondsCountAsPlayed() {
        assertEquals(Progress(0, played = true), progressFor(hour - 15_000, hour, ended = false))
        assertEquals(Progress(hour - 15_001, played = false), progressFor(hour - 15_001, hour, ended = false))
    }

    @Test
    fun shortClipsOnlyCountWhenTheyEnd() {
        assertEquals(Progress(10_000, played = false), progressFor(10_000, 20_000, ended = false))
        assertEquals(Progress(0, played = true), progressFor(20_000, 20_000, ended = true))
    }

    @Test
    fun unknownDurationNeverMarksPlayedEarly() {
        assertEquals(Progress(50_000, played = false), progressFor(50_000, 0, ended = false))
    }

    @Test
    fun negativePositionIsClamped() {
        assertEquals(Progress(0, played = false), progressFor(-5, hour, ended = false))
    }
}
