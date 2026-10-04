package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.geometry.Offset
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
    onTranslateSnippet: (String) -> Unit,
    onClearSnippet: () -> Unit,
    onMessageDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        val article = state.open
        if (article == null) {
            ReadingList(state, onOpen)
        } else {
            ArticleView(article, state, onClose, onToggleTranslation, onLookup, onSpeak, onTranslateSnippet, onClearSnippet)
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
            "글은 영어 학습용으로 새로 작성한 설명·특집 기사이며 실제 뉴스 보도가 아닙니다. 단어를 누르거나 길게 눌러 끌면 단어·구·문장을 골라 사전 검색·번역·복사를 할 수 있고, 번역 버튼으로 전체를 한국어로 볼 수 있어요. 새 글 3편은 매일 바뀝니다.",
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
    onSpeak: (String) -> Unit,
    onTranslateSnippet: (String) -> Unit,
    onClearSnippet: () -> Unit
) {
    val context = LocalContext.current
    // The selection: which paragraph and which characters (a word, a phrase or a sentence).
    var selected by remember(article.id) { mutableStateOf<Pair<Int, IntRange>?>(null) }
    val selectedText = selected?.let { (p, range) -> article.paragraphs[p].substring(range.first, range.last + 1) }
    LaunchedEffect(selectedText) { onClearSnippet() }

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
            Text("단어를 누르거나, 길게 누른 채 끌어 구·문장을 고르세요 · 약 ${article.minutes}분", color = Muted, fontSize = 12.sp)
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
            Spacer(Modifier.height(if (selectedText != null) 260.dp else 24.dp))
        }
        val current = selected
        if (selectedText != null && current != null) {
            val (paragraphIndex, range) = current
            val paragraph = article.paragraphs[paragraphIndex]
            val query = ReadingWords.lookupText(selectedText)
            val snippet = state.snippet?.takeIf { it.text == selectedText }
            Surface(shadowElevation = 12.dp, color = Color.White, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selectedText, color = Ink, fontSize = if (selectedText.length > 40) 15.sp else 20.sp, fontWeight = FontWeight.Bold,
                            maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { selected = null }) { Text("✕", color = Muted) }
                    }
                    if (snippet != null) {
                        Box(Modifier.fillMaxWidth().heightIn(max = 140.dp).clip(RoundedCornerShape(12.dp)).background(SoftBlue).verticalScroll(rememberScrollState()).padding(12.dp)) {
                            Text(
                                when {
                                    snippet.loading -> "번역 중…"
                                    snippet.korean != null -> snippet.korean
                                    else -> "번역하지 못했어요."
                                },
                                color = if (snippet.korean == null) Muted else Ink, fontSize = 15.sp, lineHeight = 22.sp
                            )
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onLookup(query) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)
                        ) { Text("📖 사전", fontSize = 14.sp, maxLines = 1) }
                        OutlinedButton(onClick = { onTranslateSnippet(selectedText) }, shape = RoundedCornerShape(12.dp)) { Text("🌐 번역", fontSize = 14.sp, maxLines = 1) }
                        OutlinedButton(onClick = { copyToClipboard(context, selectedText) }, shape = RoundedCornerShape(12.dp)) { Text("📋 복사", fontSize = 14.sp, maxLines = 1) }
                        OutlinedButton(onClick = { onSpeak(selectedText) }, shape = RoundedCornerShape(12.dp)) { Text("🔊", fontSize = 14.sp) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { selected = paragraphIndex to ReadingWords.extendLeft(paragraph, range) }) { Text("← 앞 단어", fontSize = 13.sp) }
                        TextButton(onClick = { selected = paragraphIndex to ReadingWords.extendRight(paragraph, range) }) { Text("뒤 단어 →", fontSize = 13.sp) }
                        TextButton(onClick = { selected = paragraphIndex to ReadingWords.shrink(paragraph, range) }) { Text("− 줄이기", fontSize = 13.sp) }
                        TextButton(onClick = { ReadingWords.sentenceRange(paragraph, range.first)?.let { selected = paragraphIndex to it } }) { Text("문장 전체", fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

/** A paragraph whose words can be tapped, or selected as a range by long-pressing and dragging; the selection is highlighted. */
@Composable
private fun TappableParagraph(text: String, highlight: IntRange?, onSelect: (IntRange) -> Unit) {
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
    fun wordAt(position: Offset): IntRange? = layout?.let { ReadingWords.rangeAt(text, it.getOffsetForPosition(position)) }
    Text(
        styled, color = Ink, fontSize = 18.sp, lineHeight = 29.sp,
        onTextLayout = { layout = it },
        modifier = Modifier.fillMaxWidth()
            .pointerInput(text) {
                detectTapGestures { position -> wordAt(position)?.let(onSelect) }
            }
            .pointerInput(text) {
                var anchor: IntRange? = null
                detectDragGesturesAfterLongPress(
                    onDragStart = { position -> anchor = wordAt(position); anchor?.let(onSelect) },
                    onDrag = { change, _ ->
                        change.consume()
                        val start = anchor
                        val here = wordAt(change.position)
                        if (start != null && here != null) onSelect(ReadingWords.span(start, here))
                    }
                )
            }
    )
}
