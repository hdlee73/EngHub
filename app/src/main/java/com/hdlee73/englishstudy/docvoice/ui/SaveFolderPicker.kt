package com.hdlee73.englishstudy.docvoice.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

/** A launcher that opens the system folder picker; [onPicked] gets the chosen folder. Call `launch(null)` to pick. */
@Composable
fun rememberSaveFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree -> tree?.let(callback.value) }
    return { launcher.launch(null) }
}
