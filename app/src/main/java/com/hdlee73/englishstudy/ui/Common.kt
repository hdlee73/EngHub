package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
}

/** A short notice at the bottom of a screen that disappears by itself. */
@Composable
fun MessageBar(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        if (message != null) { delay(3500); onDismiss() }
    }
    if (message != null) {
        Snackbar(modifier = modifier.padding(16.dp), action = { TextButton(onClick = onDismiss) { Text("확인") } }) { Text(message) }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 48.sp)
        Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Text(body, color = Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

/** The part of a Korean meaning text that is worth showing on a card: numbered senses, no blank lines. */
fun meaningLines(korean: String): List<String> = korean.lines().map { it.trim() }.filter { it.isNotEmpty() }

private val HeroStart = Color(0xFF22A394)
private val HeroEnd = Color(0xFF0C6F67)

/** The colourful banner at the top of every screen. [trailing] puts buttons at its right end; [compact] makes it slimmer. */
@Composable
fun Hero(
    emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier,
    compact: Boolean = false, trailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(if (compact) 20.dp else 24.dp))
            .background(Brush.linearGradient(listOf(HeroStart, HeroEnd)))
            .padding(horizontal = 16.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(if (compact) 36.dp else 40.dp).clip(RoundedCornerShape(if (compact) 12.dp else 13.dp)).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = if (compact) 19.sp else 21.sp) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, color = Color.White, fontSize = if (compact) 18.sp else 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = if (compact) 22.sp else 24.sp)
            if (!compact) Text(subtitle, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 15.sp)
        }
        trailing?.invoke(this)
    }
}

/**
 * The top of a running study screen: a round close button, the title, two small stat pills and a rounded progress bar.
 * [progress] runs from 0 to 1.
 */
@Composable
fun StudyTopBar(title: String, primary: String, secondary: String, progress: Float, accent: Color, onClose: () -> Unit, compact: Boolean = false) {
    val animated by androidx.compose.animation.core.animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(if (compact) 16.dp else 22.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(if (compact) 30.dp else 38.dp).clip(RoundedCornerShape(19.dp)).background(SoftBlue).clickable { onClose() },
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Blue, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text(
                primary, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent).padding(horizontal = 12.dp, vertical = if (compact) 3.dp else 5.dp)
            )
            Text(
                secondary, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = if (compact) 3.dp else 5.dp)
            )
        }
        Box(Modifier.fillMaxWidth().height(if (compact) 6.dp else 8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFDCE9E6))) {
            Box(Modifier.fillMaxWidth(animated).height(if (compact) 6.dp else 8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
        }
    }
}

/** The big start button that sits at the top of a setup screen. */
@Composable
fun StartButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Mint, contentColor = Color.White,
            disabledContainerColor = Color(0xFFD3E2DF), disabledContentColor = Color(0xFF86A09B)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
}

/** Cards for choosing what to study: the saved words or a speaking dataset. Scrolls sideways when there are many. */
@Composable
fun SourcePicker(
    sources: List<com.hdlee73.englishstudy.study.StudySource>,
    selectedId: String,
    unit: String,
    onSelect: (String) -> Unit,
    onAdd: (() -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("학습 자료", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            sources.forEach { source ->
                val selected = source.id == selectedId
                Column(
                    Modifier.width(150.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (selected) SoftBlue else Color.White)
                        .border(if (selected) 2.dp else 1.dp, if (selected) Blue else Color(0xFFDCE9E6), RoundedCornerShape(16.dp))
                        .clickable { onSelect(source.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        (if (source.id == com.hdlee73.englishstudy.study.SAVED_SOURCE) "⭐ " else "🗂 ") + source.label,
                        color = if (selected) Blue else Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${source.count}$unit", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (onDelete != null && source.id != com.hdlee73.englishstudy.study.SAVED_SOURCE) {
                            Text(
                                "삭제", color = Miss, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onDelete(source.id) }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }
            if (onAdd != null) {
                Column(
                    Modifier.width(120.dp).height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Blue, RoundedCornerShape(16.dp)).clickable { onAdd() }.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Text("＋", color = Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("단어장 추가", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** A number with a caption, used for the little statistics on the setup screens. */
@Composable
fun StatTile(value: String, caption: String, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = tint, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(caption, color = Muted, fontSize = 12.sp)
    }
}

/** Puts [text] on the clipboard and says so. */
fun copyToClipboard(context: android.content.Context, text: String) {
    val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    manager?.setPrimaryClip(android.content.ClipData.newPlainText("English Study", text))
    android.widget.Toast.makeText(context, "복사했습니다", android.widget.Toast.LENGTH_SHORT).show()
}

/** A bar at the top of a screen that takes the learner back to where they came from. */
@Composable
fun ReturnBar(label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().background(SoftBlue).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("←  $label", color = Blue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
