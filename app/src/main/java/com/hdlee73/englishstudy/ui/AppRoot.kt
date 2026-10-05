package com.hdlee73.englishstudy.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppTab(val label: String, val emoji: String) {
    DICTIONARY("Words", "📖"),
    FLASHCARDS("Cards", "🃏"),
    QUIZ("Quiz", "✏️"),
    READING("Reading", "📰"),
    TRANSLATE("Translate", "🌐"),
    SPEAKING("Speaking", "🎤"),
    LISTENING("Listening", "🎧"),
    DOCVOICE("DocVoice", "🎙️")
}

/**
 * The tab bar: one slim row above the phone's own navigation bar that always shows every tab.
 * The selected tab grows and shows its name; the others shrink to just their icon, so even eight tabs fit on a narrow screen.
 */
@Composable
private fun TabBar(tab: AppTab, onTab: (AppTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).windowInsetsPadding(WindowInsets.navigationBars).height(52.dp).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AppTab.values().forEach { item ->
            val selected = item == tab
            val weight by androidx.compose.animation.core.animateFloatAsState(if (selected) 2.6f else 1f, label = "tabWeight")
            Column(
                Modifier.weight(weight).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                    .background(if (selected) SoftBlue else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onTab(item) })
                    .semantics { contentDescription = item.label },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(item.emoji, fontSize = if (selected) 18.sp else 20.sp, lineHeight = 22.sp)
                if (selected) {
                    Text(item.label, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, softWrap = false, color = Blue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private const val HINT_PREFS = "ui"
private const val HINT_KEY = "menu_hint_seen"

/**
 * The tabs of the app; the screen of the selected tab is drawn by [content].
 * The tab bar folds itself away a few seconds after it was last used; swipe up on the strip at the bottom (or tap the "^") to bring it back.
 * It never covers the phone's own navigation bar.
 */
@Composable
fun AppRoot(tab: AppTab, onTab: (AppTab) -> Unit, content: @Composable (AppTab) -> Unit) {
    val context = LocalContext.current
    var barVisible by remember { mutableStateOf(true) }
    var showHint by remember { mutableStateOf(false) }
    // The bar folds itself away a few seconds after it was last used; any tap on it starts the countdown again.
    var touches by remember { mutableStateOf(0) }
    LaunchedEffect(barVisible, touches) {
        if (barVisible) {
            kotlinx.coroutines.delay(4000)
            barVisible = false
            // The first time, a picture shows how to bring the bar back.
            if (!context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE).getBoolean(HINT_KEY, false)) showHint = true
        }
    }
    EnglishStudyTheme {
        Scaffold(
            containerColor = Canvas,
            bottomBar = {
                if (barVisible) {
                    TabBar(tab) { next -> touches++; onTab(next) }
                } else {
                    // Keeps the screen above the phone's navigation bar while the tab bar is away.
                    Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        ) { inner: PaddingValues ->
            Column(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content(tab) }
                if (!barVisible) {
                    // A strip across the whole width, above the phone's own navigation bar: swipe up on it, or tap the "^", to show the tab bar.
                    Box(
                        Modifier.fillMaxWidth().height(30.dp)
                            .pointerInput(Unit) {
                                var travelled = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { travelled = 0f },
                                    onDragEnd = { travelled = 0f },
                                    onDragCancel = { travelled = 0f },
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        travelled += amount
                                        if (travelled < -24f) { barVisible = true; travelled = 0f }
                                    }
                                )
                            }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { barVisible = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(width = 52.dp, height = 22.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xE6FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            DrawCanvas(Modifier.size(width = 20.dp, height = 10.dp)) {
                                val stroke = 2.6.dp.toPx()
                                drawLine(Blue, Offset(stroke / 2, size.height - stroke / 2), Offset(size.width / 2, stroke / 2), stroke, StrokeCap.Round)
                                drawLine(Blue, Offset(size.width / 2, stroke / 2), Offset(size.width - stroke / 2, size.height - stroke / 2), stroke, StrokeCap.Round)
                            }
                        }
                    }
                }
            }
        }
        if (showHint) {
            SwipeHint(onDismiss = {
                showHint = false
                context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE).edit().putBoolean(HINT_KEY, true).apply()
            })
        }
    }
}

/** A picture-only guide, shown once: a finger swiping up from the bottom of a phone raises the tab bar. Tap anywhere to close. */
@Composable
private fun SwipeHint(onDismiss: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "hint")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f, label = "swipe",
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart)
    )
    Box(
        Modifier.fillMaxSize().background(Color(0xB3101828))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        DrawCanvas(Modifier.size(width = 190.dp, height = 320.dp)) {
            val w = size.width
            val h = size.height
            val corner = CornerRadius(w * 0.14f)
            // The phone and its screen.
            drawRoundRect(Color.White, Offset.Zero, Size(w, h), corner, Stroke(width = 5.dp.toPx()))
            // The tab bar rising from the bottom edge as the finger moves up.
            val barHeight = h * 0.13f * progress
            drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(w * 0.08f, h - h * 0.05f - barHeight), Size(w * 0.84f, barHeight), CornerRadius(8.dp.toPx()))
            // The finger and the arrow of its path.
            val startY = h * 0.9f
            val endY = h * 0.45f
            val y = startY + (endY - startY) * progress
            val cx = w / 2
            val arrow = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.55f), Offset(cx, startY), Offset(cx, endY + 14.dp.toPx()), arrow.width, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.55f), Offset(cx, endY), Offset(cx - 12.dp.toPx(), endY + 14.dp.toPx()), arrow.width, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.55f), Offset(cx, endY), Offset(cx + 12.dp.toPx(), endY + 14.dp.toPx()), arrow.width, StrokeCap.Round)
            drawCircle(Color(0xFF22A394), 17.dp.toPx(), Offset(cx, y))
            drawCircle(Color.White, 17.dp.toPx(), Offset(cx, y), style = Stroke(width = 3.dp.toPx()))
        }
    }
}
