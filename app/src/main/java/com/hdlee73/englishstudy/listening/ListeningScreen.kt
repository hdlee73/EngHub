package com.hdlee73.englishstudy.listening

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hdlee73.englishstudy.R
import com.hdlee73.englishstudy.ui.Hero

private fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

/** Keeps the controller alive for as long as the tab is on screen. */
private class ControllerHolder {
    var controller: ListeningController? = null
}

/**
 * The Listening tab: the MP3 player of the former MP3 Player app. The banner at the top is the same as on the other tabs;
 * everything under it is the player's own screen, drawn with its original views and theme.
 * [pendingIntent] is an audio file opened from elsewhere, or a tap on the widget / notification; [onIntentConsumed] is called once it is handed over.
 */
@Composable
fun ListeningScreen(
    pendingIntent: Intent?,
    onIntentConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    onLookup: (String) -> Unit = {},
    onTranslate: (String) -> Unit = {}
) {
    val latestLookup = androidx.compose.runtime.rememberUpdatedState(onLookup)
    val latestTranslate = androidx.compose.runtime.rememberUpdatedState(onTranslate)
    val hostContext = LocalContext.current
    val activity = remember(hostContext) { hostContext.findActivity() } ?: return
    val themed = remember(activity) { ContextThemeWrapper(activity, R.style.ls_Theme_Listening) }
    val holder = remember { ControllerHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current

    Column(modifier.fillMaxSize().background(Color(0xFFEAF3F1))) {
        Hero("🎧", "Listening", "MP3 · A-B 반복 · 자막으로 귀를 열어요", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = {
                val view = LayoutInflater.from(themed).inflate(R.layout.ls_activity_main, null)
                val controller = ListeningController(activity, themed, view, { latestLookup.value(it) }, { latestTranslate.value(it) })
                holder.controller = controller
                controller.start()
                view
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.controller?.onStart()
                Lifecycle.Event.ON_STOP -> holder.controller?.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.controller?.dispose()
            holder.controller = null
        }
    }

    LaunchedEffect(pendingIntent) {
        val intent = pendingIntent ?: return@LaunchedEffect
        holder.controller?.deliver(intent)
        onIntentConsumed()
    }
}
