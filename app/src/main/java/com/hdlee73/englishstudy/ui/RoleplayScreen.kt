package com.hdlee73.englishstudy.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hdlee73.englishstudy.ai.GenAi
import com.hdlee73.englishstudy.ai.Tutor
import com.hdlee73.englishstudy.roleplay.RoleplayTheme
import com.hdlee73.englishstudy.roleplay.RoleplayThemes
import com.hdlee73.englishstudy.roleplay.RoleplayUiState
import com.hdlee73.englishstudy.roleplay.RoleplayVoice
import com.hdlee73.englishstudy.roleplay.Turn

/** The switch at the top of the Speaking tab: 따라읽기 (the sentence lessons) or 롤플레잉 (talking with a partner). */
@Composable
fun SpeakingModeBar(roleplay: Boolean, onMode: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Canvas).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (isRoleplay in listOf(false, true)) {
            val label = if (isRoleplay) "롤플레잉" else "따라읽기"
            if (roleplay == isRoleplay) Button(onClick = { onMode(isRoleplay) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(label, fontWeight = FontWeight.Bold) }
            else OutlinedButton(onClick = { onMode(isRoleplay) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(label) }
        }
    }
}

@Composable
fun RoleplayScreen(
    state: RoleplayUiState,
    onStart: (RoleplayTheme) -> Unit,
    onMic: () -> Unit,
    onEnd: () -> Unit,
    onRestart: () -> Unit,
    onRetry: () -> Unit,
    onToggleKorean: (Int) -> Unit,
    onSummary: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onMessageDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pendingTheme by remember { mutableStateOf<RoleplayTheme?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val theme = pendingTheme
        pendingTheme = null
        if (granted && theme != null) onStart(theme) else if (!granted) onMessage("마이크 권한이 있어야 말하면서 연습할 수 있어요. 설정에서 마이크를 허용해 주세요.")
    }
    val start: (RoleplayTheme) -> Unit = { theme ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onStart(theme)
        else { pendingTheme = theme; permission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    Box(Modifier.fillMaxSize().background(Canvas)) {
        val theme = state.theme
        when {
            theme == null -> ThemeList(state.busy, start)
            state.summary -> SummaryView(state, onContinue = { onSummary(false) }, onEnd = onEnd)
            else -> Conversation(state, theme, onMic, onEnd, onRestart, onRetry, onToggleKorean, onSummary)
        }
        MessageBar(state.message, onMessageDismiss, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ThemeList(busy: Boolean, onStart: (RoleplayTheme) -> Unit) {
    val aiStatus by GenAi.status.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("말로 하는 롤플레잉", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "상대가 말하면 마이크가 저절로 켜져요. 하고 싶은 말이 끝나면 한 번 터치하세요.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
            Text(Tutor.engineLabel(aiStatus), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(22.dp), strokeWidth = 2.dp)
        }
        // 프리토킹 first, then the scenes by group.
        item { ThemeCard(RoleplayThemes.FREE, highlighted = true, needsAi = false, onStart) }
        var lastGroup = ""
        for (scene in RoleplayThemes.SCENES) {
            if (scene.group != lastGroup) {
                lastGroup = scene.group
                val title = scene.group
                item(key = "group-$title") { Text(title, color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            }
            item(key = scene.id) { ThemeCard(scene, highlighted = false, needsAi = scene.script == null && aiStatus != GenAi.Status.AVAILABLE, onStart) }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp)) }
    }
}

@Composable
private fun ThemeCard(theme: RoleplayTheme, highlighted: Boolean, needsAi: Boolean, onStart: (RoleplayTheme) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (highlighted) SoftBlue else Color.White)
            .clickable { onStart(theme) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(if (highlighted) Color.White else SoftBlue), contentAlignment = Alignment.Center) {
            Text(theme.emoji, fontSize = 24.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(theme.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(theme.goal, color = Muted, fontSize = 13.sp)
            if (needsAi) Text("Gemini Nano가 있는 기기에서 가능", color = Miss, fontSize = 11.sp)
        }
        Text("›", color = Muted, fontSize = 22.sp)
    }
}

@Composable
private fun Conversation(
    state: RoleplayUiState,
    theme: RoleplayTheme,
    onMic: () -> Unit,
    onEnd: () -> Unit,
    onRestart: () -> Unit,
    onRetry: () -> Unit,
    onToggleKorean: (Int) -> Unit,
    onSummary: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.turns.size, state.finished) {
        val count = state.turns.size + if (state.finished) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onEnd) { Text("‹ 목록") }
            Text("${theme.emoji} ${theme.title}", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onRestart) { Text("처음부터") }
            TextButton(onClick = { onSummary(true) }) { Text("마치기") }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.turns, key = { it.id }) { Bubble(it, onToggleKorean) }
            if (state.finished) item(key = "finished") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("대화 완료!", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("‘${theme.goal}’ 상황을 끝까지 해냈어요. 한 번 더 하면 더 자연스러워져요.", color = Ink, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRestart, shape = RoundedCornerShape(12.dp)) { Text("다시 하기") }
                        OutlinedButton(onClick = { onSummary(true) }, shape = RoundedCornerShape(12.dp)) { Text("정리 보기") }
                    }
                }
            }
        }
        if (state.turns.any { it.fromUser }) {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetry, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("↺ 방금 말 다시", fontSize = 13.sp) }
                OutlinedButton(onClick = onRestart, Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("⟲ 처음부터 다시", fontSize = 13.sp) }
            }
        }
        MicBar(state, onMic)
    }
}

@Composable
private fun Bubble(turn: Turn, onToggleKorean: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (turn.fromUser) Alignment.End else Alignment.Start) {
        Box(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp))
                .background(if (turn.fromUser) Blue else Color.White).padding(horizontal = 14.dp, vertical = 10.dp)
        ) { Text(turn.text, color = if (turn.fromUser) Color.White else Ink, fontSize = 17.sp, lineHeight = 24.sp) }
        turn.heard?.let { Text("들린 대로: $it", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, end = 4.dp)) }
        if (!turn.fromUser) {
            TextButton(onClick = { onToggleKorean(turn.id) }) { Text(if (turn.showKorean) "한국어 숨기기" else "한국어 번역", fontSize = 12.sp) }
            if (turn.showKorean && turn.korean != null) Text(turn.korean, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
        }
        turn.tip?.let { tip ->
            Column(Modifier.padding(top = 6.dp).widthIn(max = 320.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF1DC)).padding(10.dp)) {
                Text("이렇게 말하면 더 자연스러워요", color = Color(0xFF8A5A00), fontSize = 11.sp)
                Text(tip, color = Ink, fontSize = 15.sp)
            }
        }
    }
}

/** The one big button: its look says what happens next (the partner is talking, the microphone is open, or it is the learner's move). */
@Composable
private fun MicBar(state: RoleplayUiState, onMic: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White).padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val listening = state.voice == RoleplayVoice.State.LISTENING
        val speaking = state.voice == RoleplayVoice.State.SPEAKING
        if (state.partial.isNotBlank()) Text(state.partial, color = Ink, fontSize = 15.sp, maxLines = 3)
        when {
            state.busy -> {
                CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                Text("답변을 만드는 중…", color = Muted, fontSize = 13.sp)
            }
            state.finished -> Text("대화가 끝났어요.", color = Muted, fontSize = 13.sp)
            else -> {
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(if (listening) Miss else Mint).clickable(onClick = onMic),
                    contentAlignment = Alignment.Center
                ) { Icon(if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp)) }
                Text(
                    when {
                        listening -> "듣고 있어요. 다 말했으면 눌러 주세요"
                        speaking -> "상대가 말하는 중이에요. 끝나면 마이크가 켜져요 (누르면 바로 말하기)"
                        else -> "마이크를 눌러 말해 보세요"
                    },
                    color = Muted, fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SummaryView(state: RoleplayUiState, onContinue: () -> Unit, onEnd: () -> Unit) {
    val tips = state.turns.filter { it.fromUser && it.tip != null }
    val spoken = state.turns.count { it.fromUser }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("오늘의 대화 정리", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("내가 말한 횟수 ${spoken}번 · 더 자연스럽게 말할 수 있는 문장 ${tips.size}개", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        items(tips, key = { it.id }) { turn ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("내가 한 말", color = Muted, fontSize = 11.sp)
                Text(turn.text, color = Ink, fontSize = 15.sp)
                Text("이렇게 말하면 더 자연스러워요", color = Blue, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                Text(turn.tip.orEmpty(), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (tips.isEmpty()) item { Text("고칠 문장이 없었어요. 잘하셨어요!", color = Ink, fontSize = 15.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onContinue, shape = RoundedCornerShape(12.dp)) { Text("대화 계속하기") }
                OutlinedButton(onClick = onEnd, shape = RoundedCornerShape(12.dp)) { Text("목록으로") }
            }
        }
    }
}
