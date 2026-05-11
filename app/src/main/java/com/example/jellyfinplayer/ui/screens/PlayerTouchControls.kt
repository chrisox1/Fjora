package com.example.jellyfinplayer.ui.screens

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
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
private const val PlayerGestureMaxStartHeightFraction = 0.80f
private const val PlayerGestureSensitivity = 0.35f

internal enum class PlayerGestureTarget {
    Brightness,
    Volume
}

internal data class PlayerGestureFeedbackState(
    val target: PlayerGestureTarget,
    val percent: Int
)

internal fun Modifier.playerBrightnessVolumeGestures(
    activity: Activity?,
    audioManager: AudioManager?,
    enabled: Boolean,
    onFeedback: (PlayerGestureFeedbackState) -> Unit
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
                    start.y > size.height * PlayerGestureMaxStartHeightFraction -> null
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
                    onFeedback(
                        PlayerGestureFeedbackState(
                            target = PlayerGestureTarget.Brightness,
                            percent = (next * 100).roundToInt()
                        )
                    )
                } else {
                    val am = audioManager ?: return@detectVerticalDragGestures
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    val next = (startVolume + dragFraction * max).roundToInt().coerceIn(0, max)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                    onFeedback(
                        PlayerGestureFeedbackState(
                            target = PlayerGestureTarget.Volume,
                            percent = ((next.toFloat() / max.toFloat()) * 100).roundToInt()
                        )
                    )
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
    feedback: PlayerGestureFeedbackState?,
    modifier: Modifier = Modifier
) {
    if (feedback == null) return
    val align = if (feedback.target == PlayerGestureTarget.Volume) {
        Alignment.CenterEnd
    } else {
        Alignment.CenterStart
    }
    Box(
        modifier = modifier,
        contentAlignment = align
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 54.dp)
                .width(34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${feedback.percent}%",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .height(122.dp)
                    .width(9.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .padding(vertical = 1.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .height((120f * feedback.percent.coerceIn(0, 100) / 100f).dp)
                        .width(9.dp)
                        .background(Color.White, RoundedCornerShape(50))
                )
            }
            Spacer(Modifier.height(14.dp))
            Icon(
                imageVector = if (feedback.target == PlayerGestureTarget.Volume) {
                    Icons.Default.VolumeUp
                } else {
                    Icons.Default.WbSunny
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
