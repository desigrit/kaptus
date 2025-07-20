// app/src/main/java/com/example/kaptus/data/SubtitleEntry.kt

package com.example.kaptus.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SubtitleEntry(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
) : Parcelable {
    fun isActiveAt(timeMs: Long): Boolean {
        return timeMs in startTimeMs..endTimeMs
    }
}