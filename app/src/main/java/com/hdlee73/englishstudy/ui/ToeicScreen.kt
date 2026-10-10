package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.englishstudy.toeic.ToeicQuestion
import com.hdlee73.englishstudy.toeic.ToeicUiState
import java.time.LocalDate

/**
 * 오늘의 토익: each day 5 reading and 5 listening questions of TOEIC type (original practice questions), answered one by one with
 * the explanation shown right after each answer, and a score at the end. Listening questions are read aloud by the phone's English voice.
 */
@Composable
fun ToeicScreen(
    state: ToeicUiState,
    onStart: () -> Unit,
    onChoose: (Int) -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onRestart: () -> Unit,
    onSpeakLines: (texts: List<String>, rate: Float, onStart: (Int) -> Unit, onEnd: () -> Unit) -> Unit,
    onStopSpeaking: () -> Unit
) {
    when {
        !state.loaded -> EmptyState("📝", "문제를 준비하고 있어요", "잠시만 기다려 주세요.")
        state.total == 0 -> EmptyState("📝", "오늘의 문제를 불러오지 못했어요", "앱을 다시 열어 주세요.")
        state.inSession && !state.finished || state.showingFeedback -> ToeicQuestionView(state, onChoose, onNext, onPause, onSpeakLines, onStopSpeaking)
        state.finished -> ToeicResult(state, onRestart)
        else -> ToeicHome(state, onStart)
    }
}

@Composable
private fun ToeicHome(state: ToeicUiState, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hero("📝", "오늘의 토익", "${state.dateLabel} · 읽기 5문제 + 듣기 5문제")
        val answered = state.answers.size
        StartButton(
            if (answered == 0) "▶  오늘의 문제 풀기 · ${state.total}문제" else "▶  이어 풀기 · $answered/${state.total}",
            enabled = true, onClick = onStart
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SoftBlue).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("이렇게 풀어요", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "한 문제씩 풀고 바로 정답과 해설을 봅니다. 읽기는 빈칸 채우기(Part 5)와 짧은 글 읽기(Part 7), 듣기는 질문-응답(Part 2)·짧은 대화(Part 3)·담화(Part 4) 유형입니다. " +
                    "듣기는 휴대폰의 영어 음성이 읽어 주고, 보기는 화면에 보입니다. 문제는 날마다 새로 바뀌고 보기 순서도 매일 달라집니다.",
                color = Ink, fontSize = 14.sp, lineHeight = 20.sp
            )
            Text("문제는 EngHub용으로 직접 만든 토익 형식의 연습 문제이며, 실제 시험 문제가 아닙니다.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        ToeicHistory(state)
    }
}

@Composable
private fun ToeicHistory(state: ToeicUiState) {
    if (state.history.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("나의 기록", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            if (state.streak > 0) Text("🔥 ${state.streak}일 연속", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        state.history.takeLast(7).reversed().forEach { d ->
            val date = LocalDate.ofEpochDay(d.day)
            Row(Modifier.fillMaxWidth()) {
                Text("${date.monthValue}월 ${date.dayOfMonth}일", color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${d.score} / ${d.total}", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ToeicQuestionView(
    state: ToeicUiState,
    onChoose: (Int) -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onSpeakLines: (List<String>, Float, (Int) -> Unit, () -> Unit) -> Unit,
    onStopSpeaking: () -> Unit
) {
    val q = state.current ?: return
    val number = state.questions.indexOf(q) + 1
    val feedback = state.showingFeedback
    val chosen = if (feedback) state.answers.lastOrNull() else null
    var playing by remember(q.id) { mutableStateOf(false) }
    DisposableEffect(q.id) { onDispose { onStopSpeaking() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPause) { Text("← 나가기") }
            Spacer(Modifier.weight(1f))
            Text("$number / ${state.total}", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = { (number - if (feedback) 0 else 1) / state.total.toFloat() }, modifier = Modifier.fillMaxWidth(), color = Blue, trackColor = SoftBlue)
        Text(
            (if (q.isListening) "🎧 듣기 · Part ${q.part}" else "📖 읽기 · Part ${q.part}"),
            color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
        if (q.isListening) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        playing = true
                        onSpeakLines(q.audioLines, 0.9f, {}, { playing = false })
                    },
                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) { Text(if (playing) "듣는 중…" else "▶ 듣기", fontSize = 15.sp) }
                OutlinedButton(
                    onClick = {
                        playing = true
                        onSpeakLines(q.audioLines, 0.7f, {}, { playing = false })
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("🐢 천천히", fontSize = 14.sp) }
                if (playing) OutlinedButton(onClick = { onStopSpeaking() }, shape = RoundedCornerShape(12.dp)) { Text("■", fontSize = 14.sp) }
            }
            if (q.part == "2") Text("질문을 듣고 가장 알맞은 응답을 고르세요.", color = Muted, fontSize = 13.sp)
        } else if (q.passage.isNotBlank()) {
            Text(
                q.passageText, color = Ink, fontSize = 16.sp, lineHeight = 24.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp)
            )
        }
        if (q.question.isNotBlank()) Text(q.question, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
        q.options.forEachIndexed { i, option ->
            val state0 = when {
                !feedback -> 0
                i == q.answer -> 1
                i == chosen -> 2
                else -> 3
            }
            val bg = when (state0) { 1 -> Color(0xFFD7F2E3); 2 -> Color(0xFFFBDCD8); else -> Color.White }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)
                    .clickable(enabled = !feedback) { onChoose(i) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${"ABCD"[i]}", color = if (state0 == 3) Muted else Blue, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 12.dp))
                Text(option, color = if (state0 == 3) Muted else Ink, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                if (state0 == 1) Text("✓", color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (state0 == 2) Text("✗", color = Miss, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (feedback) {
            val correct = chosen == q.answer
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SoftBlue).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (correct) "정답이에요! 👏" else "아쉬워요. 정답은 ${"ABCD"[q.answer]}", color = if (correct) Blue else Miss, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(q.explanation, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
                if (q.isListening) {
                    Text("대본", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(q.scriptLines.joinToString("\n"), color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
                }
            }
            Button(
                onClick = onNext, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue)
            ) { Text(if (state.finished) "결과 보기" else "다음 문제", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToeicResult(state: ToeicUiState, onRestart: () -> Unit) {
    val (rc, rt) = state.sectionScore("R")
    val (lc, lt) = state.sectionScore("L")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hero("🏁", "오늘의 결과", "${state.dateLabel} · ${state.score} / ${state.total} 정답")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreTile("📖 읽기", "$rc / $rt", Modifier.weight(1f))
            ScoreTile("🎧 듣기", "$lc / $lt", Modifier.weight(1f))
            ScoreTile("🔥 연속", "${state.streak}일", Modifier.weight(1f))
        }
        state.questions.forEachIndexed { i, q ->
            val picked = state.answers.getOrNull(i)
            val ok = picked == q.answer
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${if (q.isListening) "듣기" else "읽기"} Part ${q.part}", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(if (ok) "✓ 정답" else "✗ 오답", color = if (ok) Blue else Miss, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(q.question.ifBlank { q.scriptLines.firstOrNull().orEmpty() }, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
                Text("정답: ${"ABCD"[q.answer]}. ${q.options[q.answer]}", color = Blue, fontSize = 14.sp)
                if (!ok && picked != null && picked in q.options.indices) Text("내 답: ${"ABCD"[picked]}. ${q.options[picked]}", color = Miss, fontSize = 14.sp)
                Text(q.explanation, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("처음부터 다시 풀기") }
        ToeicHistory(state)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ScoreTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}
