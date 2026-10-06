package com.hdlee73.englishstudy.docvoice

import android.content.Intent

/** How the DocVoice notifications point back at the DocVoice tab. */
object DocVoiceLink {
    const val EXTRA_OPEN = "open_docvoice"

    /** The YouTube address in a text another app shared to EngHub (the share sheet), if any. */
    fun sharedYoutubeUrl(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND)
            com.hdlee73.englishstudy.docvoice.core.YouTubeAudio.findUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
        else null

    fun isForDocVoice(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_OPEN, false) == true || sharedYoutubeUrl(intent) != null
}
