package com.example.jellyfinplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Bridge between PiP-overlay action button presses and whichever player is
 * currently active. Player-agnostic: each player screen registers its own
 * lambdas on mount and clears them on dispose, so the same overlay buttons
 * work for ExoPlayer AND the bundled mpv player.
 *
 * The companion-object callbacks are @Volatile static refs because there's
 * only ever one player active at any moment — the player lives inside its
 * Composable as a remembered object whose lifetime never overlaps another's.
 */
class PipActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.example.jellyfinplayer.PIP_ACTION"
        /** Intent extra: which action the user tapped. */
        const val EXTRA_KIND = "kind"

        const val KIND_PLAY = "play"
        const val KIND_PAUSE = "pause"
        const val KIND_TOGGLE_PLAY_PAUSE = "toggle_play_pause"
        const val KIND_REWIND = "rewind"
        const val KIND_FORWARD = "forward"
        const val KIND_NEXT = "next"

        // Player-agnostic action callbacks. Both ExoPlayer and MPV register
        // their own implementations of these on mount.
        @Volatile var activeTogglePlayPause: (() -> Unit)? = null
        @Volatile var activeRewind: (() -> Unit)? = null
        @Volatile var activeForward: (() -> Unit)? = null
        @Volatile var activePlayNext: (() -> Unit)? = null
        @Volatile var activeIsPlaying: (() -> Boolean)? = null
        @Volatile var activeStop: (() -> Unit)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION) return
        // Hop to the main thread — both ExoPlayer's API and MPVLib's JNI
        // calls expect to run there.
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post {
            runCatching {
                when (intent.getStringExtra(EXTRA_KIND)) {
                    KIND_PLAY, KIND_PAUSE, KIND_TOGGLE_PLAY_PAUSE ->
                        activeTogglePlayPause?.invoke()
                    KIND_REWIND -> activeRewind?.invoke()
                    KIND_FORWARD -> activeForward?.invoke()
                    KIND_NEXT -> activePlayNext?.invoke()
                    else -> Unit
                }
            }
        }
    }
}
