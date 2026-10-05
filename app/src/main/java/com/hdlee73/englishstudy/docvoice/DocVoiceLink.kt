package com.hdlee73.englishstudy.docvoice

import android.content.Intent

/** How the DocVoice notifications point back at the DocVoice tab. */
object DocVoiceLink {
    const val EXTRA_OPEN = "open_docvoice"

    fun isForDocVoice(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_OPEN, false) == true
}
