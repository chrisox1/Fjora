package com.example.jellyfinplayer.ui.screens

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun Modifier.tabletContentWidth(): Modifier =
    fillTabletWidth(maxWidthDp = 840)

internal fun Modifier.formContentWidth(): Modifier =
    fillTabletWidth(maxWidthDp = 560)

private fun Modifier.fillTabletWidth(maxWidthDp: Int): Modifier =
    this.widthIn(max = maxWidthDp.dp)
