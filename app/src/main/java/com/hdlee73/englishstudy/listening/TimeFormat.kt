package com.hdlee73.englishstudy.listening

import com.hdlee73.englishstudy.R

import java.util.Locale

/** mm:ss, or h:mm:ss for an hour and more. */
fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

/** mm:ss.t, used where A-B points are adjusted in tenths of a second. */
fun formatTimeTenths(ms: Long): String {
    val clamped = ms.coerceAtLeast(0)
    return formatTime(clamped) + "." + (clamped % 1000) / 100
}
