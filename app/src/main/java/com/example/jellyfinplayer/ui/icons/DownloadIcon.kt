package com.example.jellyfinplayer.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom "download" icon, defined inline as an ImageVector. We have
 * material-icons-extended for most icons, but Download specifically isn't
 * present in some artifact versions of Compose 1.6.x — providing it as a
 * custom vector here means we don't depend on the icon's availability in
 * any particular release of the icon set.
 *
 * Visually: a filled-arrow pointing down into a horizontal "tray" line —
 * the universal Material download glyph.
 *
 * Sized 24x24 viewport so it slots into Icon() composables at any tinted
 * size without distortion.
 */
val DownloadIconVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "DownloadIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Arrow body — vertical bar from top, with arrow head pointing
            // down. Filled, integer coords for crisp rendering at small
            // sizes.
            moveTo(11f, 4f)
            lineTo(13f, 4f)
            lineTo(13f, 12f)
            lineTo(16.5f, 12f)
            lineTo(12f, 16.5f)
            lineTo(7.5f, 12f)
            lineTo(11f, 12f)
            close()
            // Tray line at the bottom — thin filled rectangle that suggests
            // "save here" / "into the device."
            moveTo(5f, 18f)
            lineTo(19f, 18f)
            lineTo(19f, 20f)
            lineTo(5f, 20f)
            close()
        }
    }.build()
}
