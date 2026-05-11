package com.example.jellyfinplayer.ui.screens

import androidx.compose.ui.graphics.Color

internal val subtitleColorOptions = listOf(
    "white" to "White",
    "yellow" to "Yellow",
    "cyan" to "Cyan",
    "green" to "Green"
)

internal fun subtitleColorLabel(value: String): String =
    subtitleColorOptions.firstOrNull { it.first == value }?.second ?: "White"

internal fun subtitleComposeColor(value: String): Color = when (value) {
    "yellow" -> Color(0xFFFFF176)
    "cyan" -> Color(0xFF80DEEA)
    "green" -> Color(0xFFA5D6A7)
    else -> Color.White
}

internal fun subtitleAndroidColor(value: String): Int = when (value) {
    "yellow" -> android.graphics.Color.rgb(255, 241, 118)
    "cyan" -> android.graphics.Color.rgb(128, 222, 234)
    "green" -> android.graphics.Color.rgb(165, 214, 167)
    else -> android.graphics.Color.WHITE
}
