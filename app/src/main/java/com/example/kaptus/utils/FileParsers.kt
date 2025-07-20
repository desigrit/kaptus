// app/src/main/java/com/example/kaptus/utils/FileParsers.kt

package com.example.kaptus.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.kaptus.data.SrtParser
import com.example.kaptus.data.SubtitleEntry

fun parseSrtFile(context: Context, uri: Uri): List<SubtitleEntry> {
    val inputStream = context.contentResolver.openInputStream(uri)
    return inputStream?.use { SrtParser().parse(it) } ?: emptyList()
}

fun getFileName(context: Context, uri: Uri): String {
    var result = "Unknown"
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex >= 0) {
                    result = it.getString(columnIndex) ?: "Unknown"
                }
            }
        }
    }
    if (result == "Unknown") {
        result = uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        } ?: "Unknown"
    }
    return result
}

fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}