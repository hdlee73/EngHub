package com.hdlee73.englishstudy.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.englishstudy.dictionary.WordEntry
import com.hdlee73.englishstudy.dictionary.displayExamples
import com.hdlee73.englishstudy.study.DeckFilter
import com.hdlee73.englishstudy.study.FlashcardUiState
import com.hdlee73.englishstudy.study.StudyStage

@Composable
fun FlashcardScreen(
    state: FlashcardUiState,
    onSource: (String) -> Unit,
    onAddList: () -> Unit,
    onDeleteList: (String) -> Unit,
    onMessageDismiss: () -> Unit,
    onFilter: (DeckFilter) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onFrontIsWord: (Boolean) -> Unit,
    onStart: () -> Unit,
    onFlip: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onRetryMissed: () -> Unit,
    onEnd: () -> Unit,
    onSpeak: (String) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        when (state.stage) {
            StudyStage.SETUP -> FlashcardSetup(state, onSource, onAddList, onDeleteList, onFilter, onShuffle, onFrontIsWord, onStart)
            StudyStage.STUDY -> FlashcardStudy(state, onFlip, onAnswer, onEnd, onSpeak)
            StudyStage.DONE -> FlashcardDone(state, onRetryMissed, onEnd)
        }
        MessageBar(state.message, onMessageDismiss, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FlashcardSetup(
    state: FlashcardUiState,
    onSource: (String) -> Unit,
    onAddList: () -> Unit,
    onDeleteList: (String) -> Unit,
    onFilter: (DeckFilter) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onFrontIsWord: (Boolean) -> Unit,
    onStart: () -> Unit
) {
    val deckSize = state.deckCounts[state.filter] ?: 0
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    confirmDelete?.let { id ->
        val label = state.sources.firstOrNull { it.id == id }?.label.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("‘$label’ 삭제") },
            text = { Text("이 단어장을 삭제할까요? 원본 파일은 그대로 남습니다.") },
            confirmButton = { TextButton(onClick = { onDeleteList(id); confirmDelete = null }) { Text("삭제", color = Miss) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("취소") } }
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hero("🃏", "카드 암기", "카드를 넘기며 하나씩 외워요")
        // The start button comes first so it is always in reach.
        StartButton(
            when {
                deckSize > 0 -> "▶  학습 시작 · ${deckSize}장"
                state.cardCount == 0 -> "카드가 없어요 · 아래에서 단어장을 추가하세요"
                else -> "이 묶음에는 카드가 없어요"
            },
            enabled = deckSize > 0, onClick = onStart
        )
        SourcePicker(state.sources, state.sourceId, "장", onSource, onAdd = onAddList, onDelete = { confirmDelete = it })
        Text(
            "＋ 단어장 추가: 엑셀(.xlsx)·CSV 파일에서 영어 단어(또는 문장)와 한글 뜻을 두 열로 적어 불러오세요. 열 순서는 자동으로 인식합니다.",
            color = Muted, fontSize = 12.sp, lineHeight = 17.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${state.cardCount}", "전체", Blue, Modifier.weight(1f))
            StatTile("${state.masteredCount}", "외운 카드", Mint, Modifier.weight(1f))
            StatTile("${state.cardCount - state.masteredCount}", "외우는 중", Color(0xFFF59E0B), Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("학습 범위", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckFilter.values().forEach { filter ->
                    FilterChip(
                        selected = filter == state.filter, onClick = { onFilter(filter) },
                        label = { Text("${filter.label} ${state.deckCounts[filter] ?: 0}") }
                    )
                }
            }
            Text(state.filter.description, color = Muted, fontSize = 13.sp)
            Text("카드 앞면", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.frontIsWord, onClick = { onFrontIsWord(true) }, label = { Text("영어 → 뜻") })
                FilterChip(selected = !state.frontIsWord, onClick = { onFrontIsWord(false) }, label = { Text("뜻 → 영어") })
            }
            Text("순서", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.shuffle, onClick = { onShuffle(true) }, label = { Text("섞어서") })
                FilterChip(selected = !state.shuffle, onClick = { onShuffle(false) }, label = { Text("알파벳순") })
            }
        }
    }
}

@Composable
private fun FlashcardStudy(
    state: FlashcardUiState,
    onFlip: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onEnd: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val card = state.card ?: return
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onEnd) { Text("← 그만하기") }
            Text(
                "${state.known} / ${state.total} · 남은 카드 ${state.remaining}",
                color = Muted, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f)
            )
        }
        LinearProgressIndicator(
            progress = { if (state.total == 0) 0f else state.known.toFloat() / state.total },
            modifier = Modifier.fillMaxWidth(), color = Mint
        )
        FlipCard(card, state.flipped, state.frontIsWord, card.word.length > 28, onFlip, onAnswer, onSpeak, Modifier.weight(1f).fillMaxWidth())
        if (state.flipped) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onAnswer(false) }, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Miss)
                ) { Text("모르겠어요", fontSize = 16.sp) }
                Button(
                    onClick = { onAnswer(true) }, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint)
                ) { Text("알아요", fontSize = 16.sp) }
            }
            Text("카드를 옆으로 밀어도 돼요 (← 모르겠어요 · 알아요 →)", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            OutlinedButton(onClick = onFlip, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("뒤집기", fontSize = 16.sp) }
        }
    }
}

@Composable
private fun FlipCard(
    card: WordEntry,
    flipped: Boolean,
    frontIsWord: Boolean,
    sentence: Boolean,
    onFlip: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier
) {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, animationSpec = tween(350), label = "flip")
    val density = LocalDensity.current.density
    val showBack = rotation > 90f
    // The card is a new composition for each word, so a swipe only ever applies to the card it started on.
    val drag = remember(card.id, card.word) { floatArrayOf(0f) }
    Card(
        modifier = modifier
            .graphicsLayer { rotationY = rotation; cameraDistance = 14f * density }
            .pointerInput(card.id, flipped) {
                detectHorizontalDragGestures(
                    onDragStart = { drag[0] = 0f },
                    onDragEnd = {
                        if (flipped && drag[0] < -SWIPE_DISTANCE) onAnswer(false)
                        else if (flipped && drag[0] > SWIPE_DISTANCE) onAnswer(true)
                        drag[0] = 0f
                    },
                    onDragCancel = { drag[0] = 0f },
                    onHorizontalDrag = { _, amount -> drag[0] += amount }
                )
            }
            .clickable { onFlip() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (!showBack) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                if (frontIsWord) WordFace(card, onSpeak, sentence = sentence) else MeaningFace(card, showExamples = false)
            }
        } else {
            // Mirror the back so its text is not drawn reversed.
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.padding(20.dp), contentAlignment = Alignment.Center) {
                if (frontIsWord) MeaningFace(card, showExamples = true) else WordFace(card, onSpeak, withExamples = true, sentence = sentence)
            }
        }
    }
}

private const val SWIPE_DISTANCE = 120f

@Composable
private fun WordFace(card: WordEntry, onSpeak: (String) -> Unit, withExamples: Boolean = false, sentence: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            card.word, color = Ink, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            fontSize = if (sentence) (if (card.word.length > 80) 20.sp else 24.sp) else 34.sp,
            lineHeight = if (sentence) 32.sp else 40.sp
        )
        if (card.ipa.isNotBlank()) Text(card.ipa, color = Muted, fontSize = 17.sp)
        TextButton(onClick = { onSpeak(card.word) }) { Text("🔊 발음 듣기", fontSize = 16.sp) }
        if (withExamples) {
            val examples = displayExamples(card.examples).trim()
            if (examples.isNotEmpty()) Text(examples.lines().take(4).joinToString("\n"), color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MeaningFace(card: WordEntry, showExamples: Boolean) {
    val lines = meaningLines(card.korean)
    val examples = if (showExamples) displayExamples(card.examples).trim() else ""
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (lines.isEmpty()) "(뜻 정보 없음)" else lines.take(6).joinToString("\n"),
            color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 28.sp
        )
        if (examples.isNotEmpty()) {
            Text(examples.lines().take(4).joinToString("\n"), color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun FlashcardDone(state: FlashcardUiState, onRetryMissed: () -> Unit, onEnd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("라운드 완료 🎉")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
                Text("${state.total}장을 모두 확인했어요", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state.missedAnswers == 0) "한 번도 막히지 않았어요!" else "‘모르겠어요’ ${state.missedAnswers}번 · 헷갈린 단어 ${state.missedWords.size}개",
                    color = Muted, fontSize = 14.sp
                )
            }
        }
        if (state.missedWords.isNotEmpty()) {
            Text("헷갈린 단어", color = Ink, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.missedWords, key = { it.id }) { word ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(word.word, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(meaningLines(word.korean).firstOrNull().orEmpty(), color = Muted, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
            }
            Button(onClick = onRetryMissed, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                Text("헷갈린 단어만 다시", fontSize = 16.sp)
            }
        } else {
            Box(Modifier.weight(1f))
        }
        OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("처음으로", fontSize = 16.sp) }
    }
}
