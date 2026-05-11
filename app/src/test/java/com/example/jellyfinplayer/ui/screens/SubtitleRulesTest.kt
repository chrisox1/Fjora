package com.example.jellyfinplayer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleRulesTest {
    @Test
    fun normalSubtitleFractionUsesFourPercentWhenInsideClamp() {
        assertEquals(
            0.04f,
            clampedExoSubtitleFraction(screenHeightDp = 600, scale = 1f, inPip = false),
            0.0001f
        )
    }

    @Test
    fun normalSubtitleFractionHonorsUserScaleInsideClamp() {
        assertEquals(
            0.05f,
            clampedExoSubtitleFraction(screenHeightDp = 600, scale = 1.25f, inPip = false),
            0.0001f
        )
    }

    @Test
    fun pipSubtitleFractionIsClampedToReadableMinimum() {
        val fraction = clampedExoSubtitleFraction(screenHeightDp = 220, scale = 1f, inPip = true)

        assertTrue("PiP subtitles should not be tiny", fraction >= 16f / 220f)
    }

    @Test
    fun subtitleColorLabelsFallbackToWhite() {
        assertEquals("White", subtitleColorLabel("unknown"))
    }
}
