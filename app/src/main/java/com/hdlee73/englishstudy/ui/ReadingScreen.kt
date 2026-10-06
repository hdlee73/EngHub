package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.material3.FilterChip
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
import com.hdlee73.englishstudy.reading.ExpressionItem
import com.hdlee73.englishstudy.reading.ReadingArticle
import com.hdlee73.englishstudy.reading.ReadingMode
import com.hdlee73.englishstudy.reading.ReadingUiState
import com.hdlee73.englishstudy.reading.ReadingWords

private val Highlight = Color(0xFFFFE08A)

@Composable
fun ReadingScreen(
    state: ReadingUiState,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onMode: (ReadingMode) -> Unit,
    onSaveExpression: (Int) -> Unit,
    onLookup: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onTranslateSnippet: (String) -> Unit,
    onClearSnippet: () -> Unit,
    onMessageDismiss: () -> Unit,
    savedScroll: Int = 0,
    onScroll: (Int) -> Unit = {},
    savedSelection: Triple<Int, Int, Int>? = null,
    onSelection: (Triple<Int, Int, Int>?) -> Unit = {}
) {
    Box(Modifier.fillMaxSize()) {
        val article = state.open
        if (article == null) {
            ReadingList(state, onOpen)
        } else {
            ArticleView(article, state, onClose, onMode, onSaveExpression, onLookup, onSpeak, onTranslateSnippet, onClearSnippet, savedScroll, onScroll, savedSelection, onSelection)
        }
        MessageBar(state.message, onMessageDismiss, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ReadingList(state: ReadingUiState, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hero("📰", "오늘의 리딩", if (state.loaded) "${state.dateLabel} · 오늘 ${state.readIds.size}/${state.today.size} 읽음" else "불러오는 중…")
        if (state.fetching) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("오늘의 기사를 가져오는 중…", color = Muted, fontSize = 13.sp)
            }
        }
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
                        if (article.level.isBlank()) article.topic else "${article.level}  ·  ${article.topic}", color = if (article.level == "고급") Miss else Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (read) Text("✓ 읽음", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(article.title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp)
                Text(
                    article.paragraphs.first(), color = Muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text("약 ${article.wordCount}단어 · ${article.minutes}분" + if (article.credit.isNotBlank()) " · ${article.credit}" else "", color = Muted, fontSize = 12.sp)
            }
        }
        Text(
            if (state.today.any { it.credit == "The Daily Upside" }) "The Daily Upside의 기사를 하루 한 편, 원문 그대로 가져옵니다. 글을 열면 위쪽 탭에서 번역과 주요 표현을 볼 수 있고, 원문 링크는 글 아래에 있어요. 저작권은 원문 사이트에 있으니 개인 학습용으로만 쓰세요."
            else if (state.fromWeb) "실제 영어 뉴스(Wikinews, CC BY 2.5)에서 매일 중급·고급 한 편씩 가져옵니다. (The Daily Upside에 연결하지 못해 대신 보여 드려요.)"
            else "인터넷에 연결되면 실제 영어 뉴스로 바뀝니다. 지금은 앱에 들어 있는 학습용 글을 보여 드려요.",
            color = Muted, fontSize = 12.sp, lineHeight = 17.sp
        )
    }
}

@Composable
private fun ArticleView(
    article: ReadingArticle,
    state: ReadingUiState,
    onClose: () -> Unit,
    onMode: (ReadingMode) -> Unit,
    onSaveExpression: (Int) -> Unit,
    onLookup: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onTranslateSnippet: (String) -> Unit,
    onClearSnippet: () -> Unit,
    savedScroll: Int,
    onScroll: (Int) -> Unit,
    savedSelection: Triple<Int, Int, Int>?,
    onSelection: (Triple<Int, Int, Int>?) -> Unit
) {
    val context = LocalContext.current
    // The selection: which paragraph and which characters (a word, a phrase or a sentence).
    // After a dictionary lookup the tab is drawn again: the selection and the scroll position come back from where they were left.
    var selected by remember(article.id) {
        mutableStateOf<Pair<Int, IntRange>?>(
            savedSelection?.takeIf { (p, first, last) -> article.paragraphs.getOrNull(p)?.let { first >= 0 && last >= first && last < it.length } == true }
                ?.let { (p, first, last) -> p to (first..last) }
        )
    }
    val selectedText = selected?.let { (p, range) -> article.paragraphs[p].substring(range.first, range.last + 1) }
    LaunchedEffect(selected) { onSelection(selected?.let { (p, range) -> Triple(p, range.first, range.last) }) }
    val scrollState = rememberScrollState(savedScroll)
    LaunchedEffect(scrollState) { androidx.compose.runtime.snapshotFlow { scrollState.value }.collect { onScroll(it) } }
    LaunchedEffect(selectedText) { onClearSnippet() }
    // While a range is being dragged the panel stays away, so the text does not move under the finger.
    var dragging by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("← 목록") }
            Spacer(Modifier.weight(1f))
            if (state.translating || state.loadingExpressions) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.mode == ReadingMode.TEXT, onClick = { onMode(ReadingMode.TEXT) }, label = { Text("원문") })
            FilterChip(selected = state.mode == ReadingMode.TRANSLATION, onClick = { onMode(ReadingMode.TRANSLATION) }, label = { Text("🇰🇷 번역") })
            FilterChip(selected = state.mode == ReadingMode.EXPRESSIONS, onClick = { onMode(ReadingMode.EXPRESSIONS) }, label = { IconText("💡 주요 표현") })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(article.topic, color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(article.title, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
            Text(
                (if (article.level.isNotBlank()) "${article.level} · " else "") + "약 ${article.wordCount}단어 · ${article.minutes}분",
                color = if (article.level == "고급") Miss else Muted, fontSize = 12.sp
            )
            if (state.mode == ReadingMode.EXPRESSIONS) {
                ExpressionList(state.expressions, state.loadingExpressions, onSaveExpression, onSpeak)
            } else article.paragraphs.forEachIndexed { index, paragraph ->
                TappableParagraph(paragraph, selected?.takeIf { it.first == index }?.second, { dragging = it }) { range -> selected = index to range }
                if (state.showTranslation) {
                    val korean = state.translations[index]
                    Text(
                        korean ?: "번역 중…", color = if (korean == null) Muted else Ink, fontSize = 15.sp, lineHeight = 23.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SoftBlue).padding(12.dp)
                    )
                }
            }
            if (article.credit.isNotBlank() && state.mode != ReadingMode.EXPRESSIONS) {
                Text(
                    "출처: ${article.credit}" + if (article.url.isNotBlank()) "\n${article.url}" else "",
                    color = Muted, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
            // Constant room at the end, so showing or hiding the panel never moves the text.
            Spacer(Modifier.height(280.dp))
        }
        val current = selected
        if (selectedText != null && current != null && !dragging && state.mode != ReadingMode.EXPRESSIONS) {
            val query = ReadingWords.lookupText(selectedText)
            val snippet = state.snippet?.takeIf { it.text == selectedText }
            Surface(Modifier.align(Alignment.BottomCenter), shadowElevation = 12.dp, color = Color.White, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
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
                        ) { IconText("📖 사전", fontSize = 14.sp, maxLines = 1) }
                        OutlinedButton(onClick = { onTranslateSnippet(selectedText) }, shape = RoundedCornerShape(12.dp)) { IconText("🌐 번역", fontSize = 14.sp, maxLines = 1) }
                        OutlinedButton(onClick = { copyToClipboard(context, selectedText) }, shape = RoundedCornerShape(12.dp)) { IconText("📋 복사", fontSize = 14.sp, maxLines = 1) }
                        OutlinedButton(onClick = { onSpeak(selectedText) }, shape = RoundedCornerShape(12.dp)) { IconText("🔊", fontSize = 14.sp) }
                    }
                }
            }
        }
        }
    }
}

/**
 * A paragraph that is selected like text on an iPhone: tap a word, double-tap a sentence, or long-press and drag; the selection
 * then has a handle at each end that can be dragged to widen or narrow it freely (words, phrases, whole sentences).
 */
@Composable
private fun TappableParagraph(text: String, highlight: IntRange?, onDragging: (Boolean) -> Unit, onSelect: (IntRange) -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val styled = remember(text, highlight) {
        buildAnnotatedString {
            if (highlight == null) {
                append(text)
            } else {
                append(text.substring(0, highlight.first))
                // Only the background changes, so the lines never re-wrap while the selection is being resized.
                withStyle(SpanStyle(background = Highlight)) { append(text.substring(highlight.first, highlight.last + 1)) }
                append(text.substring(highlight.last + 1))
            }
        }
    }
    val latestHighlight by rememberUpdatedState(highlight)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestDragging by rememberUpdatedState(onDragging)
    fun offsetAt(position: Offset): Int? = layout?.getOffsetForPosition(position)
    fun wordAt(position: Offset): IntRange? = offsetAt(position)?.let { ReadingWords.rangeAt(text, it) }

    Box(Modifier.fillMaxWidth()) {
        Text(
            styled, color = Ink, fontSize = 18.sp, lineHeight = 29.sp,
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth()
                .pointerInput(text) {
                    detectTapGestures(
                        onDoubleTap = { position -> offsetAt(position)?.let { ReadingWords.sentenceRange(text, it) }?.let { latestSelect(it) } },
                        onTap = { position -> wordAt(position)?.let { latestSelect(it) } }
                    )
                }
                .pointerInput(text) {
                    var anchor: IntRange? = null
                    detectDragGesturesAfterLongPress(
                        onDragStart = { position -> latestDragging(true); anchor = wordAt(position); anchor?.let { latestSelect(it) } },
                        onDragEnd = { latestDragging(false) },
                        onDragCancel = { latestDragging(false) },
                        onDrag = { change, _ ->
                            change.consume()
                            val start = anchor
                            val here = wordAt(change.position)
                            if (start != null && here != null) latestSelect(ReadingWords.span(start, here))
                        }
                    )
                }
        )
        val l = layout
        val h = highlight
        if (l != null && h != null && h.last < text.length) {
            SelectionHandle(
                rect = l.getCursorRect(h.first), isStart = true,
                onBegin = { latestDragging(true) }, onEnd = { latestDragging(false) },
                onMove = { position ->
                    val current = latestHighlight
                    val offset = layout?.getOffsetForPosition(position)
                    if (current != null && offset != null) {
                        val start = ReadingWords.wordStartFrom(text, offset)
                        val lastWordStart = ReadingWords.rangeAt(text, current.last)?.first ?: current.first
                        if (start != null) latestSelect(minOf(start, lastWordStart)..current.last)
                    }
                }
            )
            SelectionHandle(
                rect = l.getCursorRect(h.last + 1), isStart = false,
                onBegin = { latestDragging(true) }, onEnd = { latestDragging(false) },
                onMove = { position ->
                    val current = latestHighlight
                    val offset = layout?.getOffsetForPosition(position)
                    if (current != null && offset != null) {
                        val end = ReadingWords.wordEndBefore(text, offset)
                        val firstWordEnd = ReadingWords.rangeAt(text, current.first)?.last ?: current.last
                        if (end != null) latestSelect(current.first..maxOf(end, firstWordEnd))
                    }
                }
            )
        }
    }
}

/** A selection handle: a thin line along the selection edge with a round knob (top for the start, bottom for the end). */
@Composable
private fun SelectionHandle(rect: Rect, isStart: Boolean, onBegin: () -> Unit, onEnd: () -> Unit, onMove: (Offset) -> Unit) {
    val density = LocalDensity.current
    val knob = 8.dp
    val touch = 40.dp
    val latestRect by rememberUpdatedState(rect)
    val latestMove by rememberUpdatedState(onMove)
    val knobPx = with(density) { knob.toPx() }
    val touchPx = with(density) { touch.toPx() }
    val heightDp = with(density) { rect.height.toDp() } + knob * 2
    DrawCanvas(
        Modifier
            .offset { IntOffset((rect.left - touchPx / 2).roundToInt(), (if (isStart) rect.top - knobPx * 2 else rect.top).roundToInt()) }
            .size(touch, heightDp)
            .pointerInput(Unit) {
                // The finger moves the line's middle; the text under that point decides the new edge.
                var position = Offset.Zero
                detectDragGestures(
                    onDragStart = { onBegin(); position = Offset(latestRect.left, (latestRect.top + latestRect.bottom) / 2) },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onEnd() },
                    onDrag = { change, amount -> change.consume(); position += amount; latestMove(position) }
                )
            }
    ) {
        val cx = size.width / 2
        val stroke = 2.dp.toPx()
        if (isStart) {
            drawLine(Blue, Offset(cx, knobPx * 2), Offset(cx, size.height), stroke)
            drawCircle(Blue, knobPx, Offset(cx, knobPx))
        } else {
            drawLine(Blue, Offset(cx, 0f), Offset(cx, size.height - knobPx * 2), stroke)
            drawCircle(Blue, knobPx, Offset(cx, size.height - knobPx))
        }
    }
}

/** The key expressions of the open text: meaning, the sentence they come from with its translation, and a save button. */
@Composable
private fun ExpressionList(items: List<ExpressionItem>, loading: Boolean, onSave: (Int) -> Unit, onSpeak: (String) -> Unit) {
    if (items.isEmpty()) {
        Text(if (loading) "주요 표현을 고르는 중…" else "이 글에서 고른 주요 표현이 없어요.", color = Muted, fontSize = 14.sp)
        return
    }
    Text("글 속 핵심 표현이에요. 저장하면 단어장(암기·퀴즈)에서 바로 쓸 수 있어요.", color = Muted, fontSize = 12.sp)
    items.forEachIndexed { index, item ->
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.expression, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onSpeak(item.expression) }) { IconText("🔊") }
            }
            Text(item.meaning ?: "뜻을 불러오는 중…", color = if (item.meaning == null) Muted else Ink, fontSize = 15.sp, lineHeight = 22.sp)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SoftBlue).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(highlightExpression(item.sentence, item.expression), color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
                Text(item.sentenceKo ?: "해석을 불러오는 중…", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
            Button(
                onClick = { onSave(index) }, enabled = item.meaning != null && !item.saved, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue)
            ) { IconText(if (item.saved) "✓ 단어장에 저장됨" else "⭐ 단어장에 저장", fontSize = 14.sp) }
        }
    }
}

private fun highlightExpression(sentence: String, expression: String) = buildAnnotatedString {
    val at = sentence.indexOf(expression, ignoreCase = true)
    if (at < 0) { append(sentence); return@buildAnnotatedString }
    append(sentence.substring(0, at))
    withStyle(SpanStyle(background = Highlight, fontWeight = FontWeight.Bold)) { append(sentence.substring(at, at + expression.length)) }
    append(sentence.substring(at + expression.length))
}
