package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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

private val HeroStart = Color(0xFF4F7CFF)
private val HeroEnd = Color(0xFF8B5CF6)

/** The colourful banner at the top of the study setup screens. */
@Composable
fun Hero(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(HeroStart, HeroEnd)))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 24.sp) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp)
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
            disabledContainerColor = Color(0xFFD5DBE8), disabledContentColor = Color(0xFF8A94A8)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
}

/** Cards for choosing what to study: the saved words or a speaking dataset. Scrolls sideways when there are many. */
@Composable
fun SourcePicker(sources: List<com.hdlee73.englishstudy.study.StudySource>, selectedId: String, unit: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("학습 자료", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            sources.forEach { source ->
                val selected = source.id == selectedId
                Column(
                    Modifier.width(150.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (selected) SoftBlue else Color.White)
                        .border(if (selected) 2.dp else 1.dp, if (selected) Blue else Color(0xFFDDE3F0), RoundedCornerShape(16.dp))
                        .clickable { onSelect(source.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        (if (source.id == com.hdlee73.englishstudy.study.SAVED_SOURCE) "⭐ " else "🗂 ") + source.label,
                        color = if (selected) Blue else Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2
                    )
                    Text("${source.count}$unit", color = Muted, fontSize = 13.sp)
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
