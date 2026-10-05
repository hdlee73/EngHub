package com.hdlee73.englishstudy.listening

import android.content.Intent

/** How the rest of the app (notification, home-screen widget, "Open with") points at the Listening tab. */
object ListeningLink {
    const val ACTION_PICK_DRIVE = "com.hdlee73.englishstudy.listening.action.PICK_DRIVE"
    const val ACTION_RESUME_LAST = "com.hdlee73.englishstudy.listening.action.RESUME_LAST"
    const val EXTRA_OPEN = "open_listening"

    /** True when [intent] was meant for the Listening tab: an audio file opened from elsewhere, or a widget / notification tap. */
    fun isForListening(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.getBooleanExtra(EXTRA_OPEN, false)) return true
        return intent.action == Intent.ACTION_VIEW && intent.data != null
    }
}
