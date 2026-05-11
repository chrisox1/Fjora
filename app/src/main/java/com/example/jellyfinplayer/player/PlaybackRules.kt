package com.example.jellyfinplayer.player

import com.example.jellyfinplayer.api.MediaItem

internal fun nextEpisodeAfter(current: MediaItem, episodes: List<MediaItem>): MediaItem? {
    if (episodes.isEmpty()) return null
    val currentIndex = episodes.indexOfFirst { it.id == current.id }
    if (currentIndex >= 0) return episodes.getOrNull(currentIndex + 1)

    val currentSeason = current.seasonNumber ?: return null
    val currentEpisode = current.episodeNumber ?: return null
    return episodes
        .filter { episode ->
            val season = episode.seasonNumber ?: Int.MAX_VALUE
            val number = episode.episodeNumber ?: Int.MAX_VALUE
            season > currentSeason || (season == currentSeason && number > currentEpisode)
        }
        .minWithOrNull(
            compareBy<MediaItem> { it.seasonNumber ?: Int.MAX_VALUE }
                .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
        )
}

internal fun initialSeasonForEpisodeReturn(item: MediaItem): Int? = item.seasonNumber
