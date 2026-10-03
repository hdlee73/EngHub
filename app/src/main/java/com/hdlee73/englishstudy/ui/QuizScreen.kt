package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.englishstudy.study.Blanker
import com.hdlee73.englishstudy.study.QuizQuestion
import com.hdlee73.englishstudy.study.QuizStage
import com.hdlee73.englishstudy.study.QuizUiState

@Composable
fun QuizScreen(
    state: QuizUiState,
    onCount: (Int) -> Unit,
    onStart: () -> Unit,
    onChoose: (Int) -> Unit,
    onNext: () -> Unit,
    onRetryWrong: () -> Unit,
    onEnd: () -> Unit,
    onSpeak: (String) -> Unit
) {
    when (state.stage) {
        QuizStage.SETUP -> QuizSetup(state, onCount, onStart)
        QuizStage.QUESTION -> QuizQuestionView(state, onChoose, onNext, onEnd, onSpeak)
        QuizStage.RESULT -> QuizResult(state, onRetryWrong, onEnd, onSpeak)
    }
}

@Composable
private fun QuizSetup(state: QuizUiState, onCount: (Int) -> Unit, onStart: () -> Unit) {
    if (state.savedCount == 0) {
        EmptyState("✏️", "저장한 단어가 없어요", "사전 탭에서 단어를 저장하면 예문 빈칸 퀴즈를 풀 수 있어요.")
        return
    }
    if (state.eligibleCount == 0) {
        EmptyState("✏️", "퀴즈로 낼 예문이 없어요", "저장한 단어 ${state.savedCount}개 중 예문이 있는 단어가 없습니다. 예문이 있는 단어를 저장해 보세요.")
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("예문 퀴즈")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("예문의 빈칸에 알맞은 단어를 4개 중에서 고르세요.", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("한국어 뜻이 힌트로 나와요. 예문이 있는 저장 단어 ${state.eligibleCount}개 중에서 출제됩니다.", color = Muted, fontSize = 14.sp)
            }
        }
        Text("문제 수", color = Ink, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.countOptions.forEach { option ->
                FilterChip(
                    selected = option == state.effectiveCount, onClick = { onCount(option) },
                    label = { Text(if (option == state.eligibleCount && option !in listOf(5, 10, 20)) "전체 $option" else "${option}문제") }
                )
            }
        }
        Button(
            onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue)
        ) { Text("퀴즈 시작 · ${state.effectiveCount}문제", fontSize = 16.sp) }
    }
}

/** The sentence with the quiz word set in bold, for the moment the answer has been given. */
private fun highlighted(sentence: String, word: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (span in Blanker.find(sentence, word)) {
        append(sentence.substring(cursor, span.first))
        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Blue)) { append(sentence.substring(span.first, span.last + 1)) }
        cursor = span.last + 1
    }
    append(sentence.substring(cursor))
}

@Composable
private fun QuizQuestionView(
    state: QuizUiState,
    onChoose: (Int) -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val question = state.question ?: return
    val selected = state.selected
    val answered = selected != null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onEnd) { Text("← 그만하기") }
            Text(
                "${state.index + 1} / ${state.total} · 맞힌 개수 ${state.correct}",
                color = Muted, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f)
            )
        }
        LinearProgressIndicator(
            progress = { if (state.total == 0) 0f else (state.index + if (answered) 1 else 0).toFloat() / state.total },
            modifier = Modifier.fillMaxWidth(), color = Blue
        )
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("빈칸에 들어갈 단어는?", color = Muted, fontSize = 13.sp)
                if (answered) {
                    Text(highlighted(question.sentence, question.word), color = Ink, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(question.blankedSentence, color = Ink, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                }
                if (question.translation.isNotBlank()) {
                    Text(question.translation, color = Muted, fontSize = 16.sp, lineHeight = 22.sp)
                } else if (question.meaningHint.isNotBlank()) {
                    Text("뜻: ${question.meaningHint}", color = Muted, fontSize = 16.sp)
                }
            }
        }
        question.choices.forEachIndexed { index, choice ->
            val isAnswer = index == question.answerIndex
            val container = when {
                !answered -> Color.White
                isAnswer -> Mint
                index == selected -> Miss
                else -> Color.White
            }
            val content = if (answered && (isAnswer || index == selected)) Color.White else Ink
            Button(
                onClick = { onChoose(index) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = container, contentColor = content,
                    disabledContainerColor = container, disabledContentColor = if (answered && (isAnswer || index == selected)) Color.White else Muted
                ),
                enabled = !answered
            ) { Text("${index + 1}.  $choice", fontSize = 17.sp) }
        }
        if (answered) {
            val right = selected == question.answerIndex
            Text(
                if (right) "정답이에요! 👏" else "아쉬워요. 정답은 ‘${question.word}’",
                color = if (right) Mint else Miss, fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onSpeak(question.sentence) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("🔊 예문 듣기") }
                Button(
                    onClick = onNext, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) { Text(if (state.index + 1 >= state.total) "결과 보기" else "다음 문제") }
            }
        }
    }
}

@Composable
private fun QuizResult(state: QuizUiState, onRetryWrong: () -> Unit, onEnd: () -> Unit, onSpeak: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("퀴즈 결과")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
                Text("${state.total}문제 중 ${state.correct}문제 정답", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    when {
                        state.wrong.isEmpty() -> "모두 맞혔어요! 🎉"
                        state.correct * 2 >= state.total -> "잘했어요. 틀린 단어는 암기 탭 ‘틀린 단어’에도 모여 있어요."
                        else -> "틀린 단어를 한 번 더 풀어 볼까요?"
                    },
                    color = Muted, fontSize = 14.sp
                )
            }
        }
        if (state.wrong.isNotEmpty()) {
            Text("틀린 문제", color = Ink, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(state.wrong, key = { index, q -> "$index-${q.wordId}" }) { _, q -> WrongItem(q, onSpeak) }
            }
            Button(onClick = onRetryWrong, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                Text("틀린 단어만 다시 풀기", fontSize = 16.sp)
            }
        } else {
            Box(Modifier.weight(1f))
        }
        OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("처음으로", fontSize = 16.sp) }
    }
}

@Composable
private fun WrongItem(q: QuizQuestion, onSpeak: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(q.word, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(highlighted(q.sentence, q.word), color = Ink, fontSize = 14.sp)
                if (q.translation.isNotBlank()) Text(q.translation, color = Muted, fontSize = 13.sp)
            }
            TextButton(onClick = { onSpeak(q.sentence) }) { Text("🔊", fontSize = 20.sp) }
        }
    }
}
