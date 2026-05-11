package com.example.jellyfinplayer.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLog {
    private const val FileName = "fjora-diagnostics.log"
    private const val MaxBytes = 256 * 1024
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun record(context: Context, message: String) {
        runCatching {
            val file = file(context)
            if (file.exists() && file.length() > MaxBytes) {
                file.writeText(file.readText().takeLast(MaxBytes / 2))
            }
            file.appendText("${dateFormat.format(Date())} $message\n")
        }
    }

    fun exportFile(context: Context): File {
        val source = file(context)
        val out = File(context.cacheDir, "fjora-diagnostics-export.txt")
        val header = buildString {
            appendLine("Fjora diagnostics")
            appendLine("Generated: ${dateFormat.format(Date())}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} API ${android.os.Build.VERSION.SDK_INT}")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine()
        }
        out.writeText(header + if (source.exists()) source.readText() else "No diagnostics recorded yet.\n")
        return out
    }

    private fun file(context: Context): File = File(context.filesDir, FileName)
}
