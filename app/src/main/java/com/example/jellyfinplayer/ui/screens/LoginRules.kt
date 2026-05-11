package com.example.jellyfinplayer.ui.screens

internal data class ParsedServerInput(
    val scheme: String,
    val server: String
)

internal fun parseServerInput(currentScheme: String, input: String): ParsedServerInput {
    return when {
        input.startsWith("https://", ignoreCase = true) -> ParsedServerInput(
            scheme = "https",
            server = input.drop(8)
        )
        input.startsWith("http://", ignoreCase = true) -> ParsedServerInput(
            scheme = "http",
            server = input.drop(7)
        )
        else -> ParsedServerInput(
            scheme = currentScheme,
            server = input
        )
    }
}

internal fun buildFullServerUrl(scheme: String, server: String): String =
    "$scheme://${server.trim()}"
