package com.hdlee73.englishstudy

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Receives text selected in another app (the selection bar's "EngHub 사전·번역") and hands it to [MainActivity], which
 * asks what to do with it (dictionary / word list, or translation). It has no screen of its own.
 */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_PROCESS_TEXT)
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
        finish()
    }
}
