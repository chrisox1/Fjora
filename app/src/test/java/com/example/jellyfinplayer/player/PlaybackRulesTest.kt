package com.example.jellyfinplayer.player

import com.example.jellyfinplayer.api.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRulesTest {
    @Test
    fun nextEpisodeAfterUsesListOrderWhenCurrentEpisodeIsPresent() {
        val current = episode("s3e2", season = 3, number = 2)
        val next = episode("s3e3", season = 3, number = 3)

        assertEquals(
            next,
            nextEpisodeAfter(
                current = current,
                episodes = listOf(
                    episode("s3e1", season = 3, number = 1),
                    current,
                    next
                )
            )
        )
    }

    @Test
    fun nextEpisodeAfterFallsBackToSeasonAndEpisodeNumbers() {
        val current = episode("missing-id", season = 2, number = 10)
        val next = episode("s3e1", season = 3, number = 1)

        assertEquals(
            next,
            nextEpisodeAfter(
                current = current,
                episodes = listOf(
                    episode("s1e1", season = 1, number = 1),
                    next,
                    episode("s3e2", season = 3, number = 2)
                )
            )
        )
    }

    @Test
    fun nextEpisodeAfterReturnsNullAtSeriesEnd() {
        assertNull(
            nextEpisodeAfter(
                current = episode("s3e10", season = 3, number = 10),
                episodes = listOf(episode("s3e10", season = 3, number = 10))
            )
        )
    }

    @Test
    fun initialSeasonForEpisodeReturnUsesEpisodeSeason() {
        assertEquals(3, initialSeasonForEpisodeReturn(episode("s3e4", season = 3, number = 4)))
    }

    private fun episode(id: String, season: Int, number: Int): MediaItem =
        MediaItem(
            id = id,
            name = id,
            type = "Episode",
            seasonNumber = season,
            episodeNumber = number
        )
}
