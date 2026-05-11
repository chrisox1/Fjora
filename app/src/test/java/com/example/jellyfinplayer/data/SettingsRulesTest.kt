package com.example.jellyfinplayer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRulesTest {
    @Test
    fun directPlayOnlyForcesMpvForLocalPlayback() {
        assertTrue(
            effectiveUseMpvForLocal(
                directPlayOnly = true,
                storedUseMpvForLocal = false
            )
        )
    }

    @Test
    fun directPlayOnlyForcesMpvForAllPlayback() {
        assertTrue(effectiveUseMpvForAll(directPlayOnly = true))
    }

    @Test
    fun mpvForLocalCanStayOffWhenDirectPlayOnlyIsOff() {
        assertFalse(
            effectiveUseMpvForLocal(
                directPlayOnly = false,
                storedUseMpvForLocal = false
            )
        )
        assertFalse(effectiveUseMpvForAll(directPlayOnly = false))
    }
}
