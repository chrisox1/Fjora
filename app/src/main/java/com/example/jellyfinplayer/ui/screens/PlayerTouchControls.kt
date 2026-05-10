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

internal fun Modifier.playerBrightnessVolumeGestures(
    activity: Activity?,
    audioManager: AudioManager?,
    enabled: Boolean,
    onFeedback: (String) -> Unit
): Modifier {
    if (!enabled) return this
    return pointerInput(activity, audioManager) {
        var leftSide = true
        detectVerticalDragGestures(
            onDragStart = { start -> leftSide = start.x < size.width / 2f },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                val delta = (-dragAmount / size.height).coerceIn(-0.12f, 0.12f)
                if (delta == 0f) return@detectVerticalDragGestures
                if (leftSide) {
                    val window = activity?.window ?: return@detectVerticalDragGestures
                    val attrs = window.attributes
                    val current = attrs.screenBrightness.takeIf { it >= 0f } ?: 0.5f
                    val next = (current + delta).coerceIn(0.02f, 1f)
                    attrs.screenBrightness = next
                    window.attributes = attrs
                    onFeedback("Brightness ${(next * 100).roundToInt()}%")
                } else {
                    val am = audioManager ?: return@detectVerticalDragGestures
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val step = (delta * max).roundToInt().takeIf { it != 0 }
                        ?: if (delta > 0) 1 else -1
                    val next = (current + step).coerceIn(0, max)
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
