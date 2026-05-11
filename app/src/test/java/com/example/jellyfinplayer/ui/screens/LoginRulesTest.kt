package com.example.jellyfinplayer.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginRulesTest {
    @Test
    fun parseServerInputSwitchesToHttpsWhenFullUrlIsPasted() {
        val parsed = parseServerInput("http", "https://jellyfin.example.com")

        assertEquals("https", parsed.scheme)
        assertEquals("jellyfin.example.com", parsed.server)
    }

    @Test
    fun parseServerInputKeepsCurrentSchemeForHostOnlyInput() {
        val parsed = parseServerInput("https", "192.168.1.10:8096")

        assertEquals("https", parsed.scheme)
        assertEquals("192.168.1.10:8096", parsed.server)
    }

    @Test
    fun fullServerUrlTrimsServerBeforeSubmit() {
        assertEquals(
            "http://server.local:8096",
            buildFullServerUrl("http", " server.local:8096 ")
        )
    }
}
