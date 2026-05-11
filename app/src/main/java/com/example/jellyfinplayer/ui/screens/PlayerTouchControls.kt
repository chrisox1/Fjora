package com.example.jellyfinplayer.ui.screens

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val PlayerGestureEdgeWidthFraction = 0.22f
private const val PlayerGestureSensitivity = 0.35f

private enum class PlayerGestureTarget {
    Brightness,
    Volume
}

internal fun Modifier.playerBrightnessVolumeGestures(
    activity: Activity?,
    audioManager: AudioManager?,
    enabled: Boolean,
    onFeedback: (String) -> Unit
): Modifier {
    if (!enabled) return this
    return pointerInput(activity, audioManager) {
        var target: PlayerGestureTarget? = null
        var startBrightness = 0.5f
        var startVolume = 0
        var dragFraction = 0f
        detectVerticalDragGestures(
            onDragStart = { start ->
                val edgeWidth = size.width * PlayerGestureEdgeWidthFraction
                target = when {
                    start.x <= edgeWidth -> PlayerGestureTarget.Brightness
                    start.x >= size.width - edgeWidth -> PlayerGestureTarget.Volume
                    else -> null
                }
                dragFraction = 0f
                startBrightness = activity?.window?.attributes?.screenBrightness
                    ?.takeIf { it >= 0f }
                    ?: 0.5f
                startVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
            },
            onDragEnd = {
                target = null
                dragFraction = 0f
            },
            onDragCancel = {
                target = null
                dragFraction = 0f
            },
            onVerticalDrag = { change, dragAmount ->
                val activeTarget = target ?: return@detectVerticalDragGestures
                change.consume()
                val height = size.height.takeIf { it > 0 } ?: return@detectVerticalDragGestures
                dragFraction += (-dragAmount / height) * PlayerGestureSensitivity
                if (dragFraction == 0f) return@detectVerticalDragGestures
                if (activeTarget == PlayerGestureTarget.Brightness) {
                    val window = activity?.window ?: return@detectVerticalDragGestures
                    val attrs = window.attributes
                    val next = (startBrightness + dragFraction).coerceIn(0.02f, 1f)
                    attrs.screenBrightness = next
                    window.attributes = attrs
                    onFeedback("Brightness ${(next * 100).roundToInt()}%")
                } else {
                    val am = audioManager ?: return@detectVerticalDragGestures
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    val next = (startVolume + dragFraction * max).roundToInt().coerceIn(0, max)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                    onFeedback("Volume ${((next.toFloat() / max.toFloat()) * 100).roundToInt()}%")
                }
            }
        )
    }
}

@Composable
internal fun PlayerLockButton(
    locked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = if (locked) "Unlock controls" else "Lock controls",
            tint = Color.White
        )
    }
}

@Composable
internal fun PlayerGestureFeedback(
    text: String?,
    modifier: Modifier = Modifier
) {
    if (text == null) return
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
