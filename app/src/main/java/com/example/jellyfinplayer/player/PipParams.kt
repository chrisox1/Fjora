package com.example.jellyfinplayer.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi

/**
 * Build PictureInPictureParams reflecting the current player state. The
 * params include three RemoteActions in the overlay: rewind 10s, play/pause
 * (toggles based on the player's current state), and forward 30s.
 *
 * Called from both the manual PiP button (PlayerScreen) and auto-PiP via
 * onUserLeaveHint (MainActivity), so the overlay controls are consistent
 * regardless of how the user entered PiP.
 *
 * Also called whenever the player's isPlaying state flips (so the overlay
 * icon updates between play and pause).
 */
@RequiresApi(Build.VERSION_CODES.O)
fun buildPipParamsForPlayer(
    activity: Activity,
    aspectWidth: Int,
    aspectHeight: Int,
    isPlaying: Boolean,
    hasNext: Boolean = false
): PictureInPictureParams {
    val aspect = clampedPipAspect(aspectWidth, aspectHeight)

    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(aspect)

    val actions = buildList {
        add(makePipRemoteAction(activity,
            kind = PipActionReceiver.KIND_REWIND,
            requestCode = 1,
            iconRes = android.R.drawable.ic_media_rew,
            label = "Rewind 10s"
        ))
        add(makePipRemoteAction(activity,
            kind = PipActionReceiver.KIND_TOGGLE_PLAY_PAUSE,
            requestCode = 2,
            iconRes = if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
            label = if (isPlaying) "Pause" else "Play"
        ))
        add(makePipRemoteAction(activity,
            kind = PipActionReceiver.KIND_FORWARD,
            requestCode = 3,
            iconRes = android.R.drawable.ic_media_ff,
            label = "Forward 30s"
        ))
        if (hasNext) {
            add(makePipRemoteAction(activity,
                kind = PipActionReceiver.KIND_NEXT,
                requestCode = 4,
                iconRes = android.R.drawable.ic_media_next,
                label = "Next episode"
            ))
        }
    }
    builder.setActions(actions)
    return builder.build()
}

private fun clampedPipAspect(width: Int, height: Int): Rational {
    val safeW = if (width <= 0) 16 else width
    val safeH = if (height <= 0) 9 else height
    val ratio = safeW.toDouble() / safeH.toDouble()

    // Android rejects PiP aspect ratios outside roughly 1:2.39 to 2.39:1.
    // Cinematic movies can report 2.40:1 or wider, so clamp to a legal ratio
    // instead of letting setAspectRatio / enterPictureInPictureMode crash.
    return when {
        ratio > 2.39 -> Rational(239, 100)
        ratio < 1.0 / 2.39 -> Rational(100, 239)
        else -> Rational(safeW, safeH)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun makePipRemoteAction(
    activity: Activity,
    kind: String,
    requestCode: Int,
    iconRes: Int,
    label: String
): RemoteAction {
    val intent = Intent(PipActionReceiver.ACTION)
        .setPackage(activity.packageName)
        .putExtra(PipActionReceiver.EXTRA_KIND, kind)
    // FLAG_IMMUTABLE is required on Android 12+; FLAG_UPDATE_CURRENT lets us
    // refresh extras when we rebuild after a play-state change.
    val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    val pi = PendingIntent.getBroadcast(activity, requestCode, intent, flags)
    return RemoteAction(
        Icon.createWithResource(activity, iconRes),
        label,
        label,
        pi
    )
}
