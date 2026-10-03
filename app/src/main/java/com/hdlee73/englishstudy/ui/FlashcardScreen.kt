package com.hdlee73.englishstudy.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
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
    when (state.stage) {
        StudyStage.SETUP -> FlashcardSetup(state, onFilter, onShuffle, onFrontIsWord, onStart)
        StudyStage.STUDY -> FlashcardStudy(state, onFlip, onAnswer, onEnd, onSpeak)
        StudyStage.DONE -> FlashcardDone(state, onRetryMissed, onEnd)
    }
}

@Composable
private fun FlashcardSetup(
    state: FlashcardUiState,
    onFilter: (DeckFilter) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onFrontIsWord: (Boolean) -> Unit,
    onStart: () -> Unit
) {
    if (state.savedCount == 0) {
        EmptyState("🃏", "저장한 단어가 없어요", "사전 탭에서 단어를 저장하면 플래시카드로 암기할 수 있어요.")
        return
    }
    val deckSize = state.deckCounts[state.filter] ?: 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("단어 암기")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
                Text("저장 단어 ${state.savedCount}개", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("외운 단어 ${state.masteredCount}개 · 외우는 중 ${state.savedCount - state.masteredCount}개", color = Muted, fontSize = 14.sp)
            }
        }
        Text("학습할 단어", color = Ink, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckFilter.values().forEach { filter ->
                FilterChip(
                    selected = filter == state.filter, onClick = { onFilter(filter) },
                    label = { Text("${filter.label} ${state.deckCounts[filter] ?: 0}") }
                )
            }
        }
        Text(state.filter.description, color = Muted, fontSize = 14.sp)
        Text("카드 앞면", color = Ink, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.frontIsWord, onClick = { onFrontIsWord(true) }, label = { Text("영어 단어 → 뜻") })
            FilterChip(selected = !state.frontIsWord, onClick = { onFrontIsWord(false) }, label = { Text("뜻 → 영어 단어") })
        }
        Text("순서", color = Ink, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.shuffle, onClick = { onShuffle(true) }, label = { Text("섞어서") })
            FilterChip(selected = !state.shuffle, onClick = { onShuffle(false) }, label = { Text("알파벳순") })
        }
        Button(
            onClick = onStart, enabled = deckSize > 0, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue)
        ) { Text(if (deckSize > 0) "시작하기 · ${deckSize}장" else "이 묶음에는 단어가 없어요", fontSize = 16.sp) }
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
        FlipCard(card, state.flipped, state.frontIsWord, onFlip, onAnswer, onSpeak, Modifier.weight(1f).fillMaxWidth())
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
                if (frontIsWord) WordFace(card, onSpeak) else MeaningFace(card, showExamples = false)
            }
        } else {
            // Mirror the back so its text is not drawn reversed.
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.padding(20.dp), contentAlignment = Alignment.Center) {
                if (frontIsWord) MeaningFace(card, showExamples = true) else WordFace(card, onSpeak, withExamples = true)
            }
        }
    }
}

private const val SWIPE_DISTANCE = 120f

@Composable
private fun WordFace(card: WordEntry, onSpeak: (String) -> Unit, withExamples: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(card.word, color = Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
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
