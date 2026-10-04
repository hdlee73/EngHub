package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.englishstudy.reading.ReadingArticle
import com.hdlee73.englishstudy.reading.ReadingUiState
import com.hdlee73.englishstudy.reading.ReadingWords

private val Highlight = Color(0xFFFFE08A)

@Composable
fun ReadingScreen(
    state: ReadingUiState,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onToggleTranslation: () -> Unit,
    onLookup: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onMessageDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        val article = state.open
        if (article == null) {
            ReadingList(state, onOpen)
        } else {
            ArticleView(article, state, onClose, onToggleTranslation, onLookup, onSpeak)
        }
        MessageBar(state.message, onMessageDismiss, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ReadingList(state: ReadingUiState, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hero("📰", "오늘의 리딩", if (state.loaded) "${state.dateLabel} · 오늘 ${state.readIds.size}/${state.today.size} 읽음" else "불러오는 중…")
        if (state.loaded && state.today.isEmpty()) {
            Text("읽을 글을 불러오지 못했습니다.", color = Muted)
        }
        state.today.forEachIndexed { index, article ->
            val read = article.id in state.readIds
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
                    .clickable { onOpen(article.id) }.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}  ·  ${article.topic}", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (read) Text("✓ 읽음", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(article.title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp)
                Text(
                    article.paragraphs.first(), color = Muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text("약 ${article.wordCount}단어 · ${article.minutes}분 · 중급", color = Muted, fontSize = 12.sp)
            }
        }
        Text(
            "글은 영어 학습용으로 새로 작성한 설명·특집 기사이며 실제 뉴스 보도가 아닙니다. 모르는 단어를 누르면 사전에서 찾을 수 있고, 번역 버튼으로 전체를 한국어로 볼 수 있어요. 새 글 3편은 매일 바뀝니다.",
            color = Muted, fontSize = 12.sp, lineHeight = 17.sp
        )
    }
}

@Composable
private fun ArticleView(
    article: ReadingArticle,
    state: ReadingUiState,
    onClose: () -> Unit,
    onToggleTranslation: () -> Unit,
    onLookup: (String) -> Unit,
    onSpeak: (String) -> Unit
) {
    // The word being looked at: which paragraph and which characters.
    var selected by remember(article.id) { mutableStateOf<Pair<Int, IntRange>?>(null) }
    val selectedWord = selected?.let { (p, range) -> article.paragraphs[p].substring(range.first, range.last + 1) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("← 목록") }
            Spacer(Modifier.weight(1f))
            if (state.translating) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Button(
                onClick = onToggleTranslation, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (state.showTranslation) SoftBlue else Blue, contentColor = if (state.showTranslation) Blue else Color.White)
            ) { Text(if (state.showTranslation) "번역 숨기기" else "🇰🇷 전체 번역", fontSize = 14.sp) }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(article.topic, color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(article.title, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
            Text("모르는 단어를 누르면 사전에서 찾을 수 있어요 · 약 ${article.minutes}분", color = Muted, fontSize = 12.sp)
            article.paragraphs.forEachIndexed { index, paragraph ->
                TappableParagraph(paragraph, selected?.takeIf { it.first == index }?.second) { range -> selected = index to range }
                if (state.showTranslation) {
                    val korean = state.translations[index]
                    Text(
                        korean ?: "번역 중…", color = if (korean == null) Muted else Ink, fontSize = 15.sp, lineHeight = 23.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SoftBlue).padding(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(if (selectedWord != null) 96.dp else 24.dp))
        }
        if (selectedWord != null) {
            Surface(shadowElevation = 12.dp, color = Color.White, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        ReadingWords.lookupForm(selectedWord), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { onSpeak(ReadingWords.lookupForm(selectedWord)) }, shape = RoundedCornerShape(12.dp)) { Text("🔊") }
                    Button(
                        onClick = { onLookup(ReadingWords.lookupForm(selectedWord)) }, shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) { Text("📖 사전에서 검색", fontSize = 14.sp, maxLines = 1) }
                    TextButton(onClick = { selected = null }) { Text("✕", color = Muted) }
                }
            }
        }
    }
}

/** A paragraph whose words can be tapped; the tapped word is highlighted. */
@Composable
private fun TappableParagraph(text: String, highlight: IntRange?, onWord: (IntRange) -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val styled = remember(text, highlight) {
        buildAnnotatedString {
            if (highlight == null) {
                append(text)
            } else {
                append(text.substring(0, highlight.first))
                withStyle(SpanStyle(background = Highlight, fontWeight = FontWeight.Bold)) { append(text.substring(highlight.first, highlight.last + 1)) }
                append(text.substring(highlight.last + 1))
            }
        }
    }
    Text(
        styled, color = Ink, fontSize = 18.sp, lineHeight = 29.sp,
        onTextLayout = { layout = it },
        modifier = Modifier.fillMaxWidth().pointerInput(text) {
            detectTapGestures { position ->
                val result = layout ?: return@detectTapGestures
                val range = ReadingWords.rangeAt(text, result.getOffsetForPosition(position))
                if (range != null) onWord(range)
            }
        }
    )
}
