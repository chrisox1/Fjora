package com.example.jellyfinplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.jellyfinplayer.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Application class — exists primarily to register a custom Coil ImageLoader
 * that applies crossfade to every AsyncImage in the app. Without this, image
 * swaps were instant pops which made the UI feel cheap when scrolling and
 * navigating.
 *
 * Also bumps the disk + memory caches so artwork that's been seen once
 * doesn't re-download on subsequent visits — Jellyfin's image endpoints
 * include the api_key in the query string, which Coil treats as part of the
 * cache key, so the cache hit rate is high as long as the token stays
 * stable.
 */
class JellyfinApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        // Read the user's cache-size choice synchronously at startup. This
        // blocks Application init for ~10–20 ms on first read which is fine —
        // there's no UI yet to lag, and we need the value before constructing
        // the ImageLoader. Changes only take effect on next app launch (the
        // ImageLoader is built once).
        val cacheLimitBytes = runCatching {
            runBlocking { SettingsStore(this@JellyfinApp).flow.first().imageCacheLimitBytes }
        }.getOrNull() ?: (50L * 1024 * 1024)
        return ImageLoader.Builder(this)
            // 200ms crossfade is the sweet spot — fast enough not to feel
            // sluggish on grid scrolls, slow enough to register as a fade
            // rather than a pop.
            .crossfade(true)
            .crossfade(200)
            .memoryCache {
                MemoryCache.Builder(this)
                    // 25% of the app's available heap. Coil's default is 15%
                    // — for a media-heavy app the bigger cache pays off
                    // because grid scrolling re-recycles items that were
                    // visible seconds ago.
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(cacheLimitBytes)
                    .build()
            }
            // Aggressive caching — the URLs include api_key, but the actual
            // image content under that URL is stable, so respecting normal
            // HTTP caching is fine. Jellyfin servers return short max-age
            // by default, but the image bytes don't actually change — the
            // ITEM cover endpoint is content-addressable internally.
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
