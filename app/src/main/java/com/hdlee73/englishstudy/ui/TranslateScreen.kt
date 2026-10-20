package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hdlee73.englishstudy.ai.GenAi
import com.hdlee73.englishstudy.ai.Tutor
import com.hdlee73.englishstudy.translate.Direction
import com.hdlee73.englishstudy.translate.TranslateMode
import com.hdlee73.englishstudy.translate.TranslateUiState

@Composable
fun TranslateScreen(
    state: TranslateUiState,
    onInput: (String) -> Unit,
    onTranslate: () -> Unit,
    onSwap: () -> Unit,
    onClear: () -> Unit,
    onLookup: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onMessageDismiss: () -> Unit,
    /** Saves the translated sentence (English, Korean) to the Speaking tab's "번역 저장 문장" dataset. */
    onSaveToSpeaking: (english: String, korean: String) -> Unit = { _, _ -> },
    onMode: (TranslateMode) -> Unit = {},
    onCorrect: () -> Unit = {},
    /** Corrects the English result of a Korean → English translation. */
    onCorrectResult: () -> Unit = {},
    /** Called when the correction mode is shown, so Gemini Nano can be checked (and downloaded once) then. */
    onPrepareAi: () -> Unit = {}
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val correcting = state.mode == TranslateMode.CORRECT
    val aiStatus by GenAi.status.collectAsState()
    LaunchedEffect(correcting) { if (correcting) onPrepareAi() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Hero("🌐", "번역", if (correcting) "영어 문장을 더 자연스럽게 고쳐 드려요" else "영어 ⇄ 한국어 · 단어, 문장, 긴 글까지")
            // Mode: translate or correct an English sentence.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in TranslateMode.values()) {
                    val selected = state.mode == mode
                    val label = if (mode == TranslateMode.TRANSLATE) "번역" else "교정"
                    if (selected) Button(onClick = { onMode(mode) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(label, fontWeight = FontWeight.Bold) }
                    else OutlinedButton(onClick = { onMode(mode) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(label) }
                }
            }
            if (correcting) {
                Text(Tutor.engineLabel(aiStatus), color = Muted, fontSize = 12.sp)
            } else {
                // Language bar: source, swap, target.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageBox(state.direction.fromLabel, Modifier.weight(1f))
                    OutlinedButton(onClick = onSwap, shape = RoundedCornerShape(14.dp)) { Text("⇄", fontSize = 18.sp) }
                    LanguageBox(state.direction.toLabel, Modifier.weight(1f))
                }
            }
            OutlinedTextField(
                value = state.input, onValueChange = onInput, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                placeholder = {
                    Text(if (correcting) "고치고 싶은 영어 문장을 입력하세요" else "번역할 내용을 입력하세요 (영어·한국어는 자동으로 알아봐요)", color = Muted)
                },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let(onInput)
                }, shape = RoundedCornerShape(12.dp)) { IconText("📋 붙여넣기", fontSize = 14.sp) }
                if (state.input.isNotEmpty()) OutlinedButton(onClick = onClear, shape = RoundedCornerShape(12.dp)) { Text("✕ 지우기", fontSize = 14.sp) }
                if (!correcting && state.direction == Direction.EN_KO && state.input.isNotBlank()) {
                    OutlinedButton(onClick = { onLookup(state.input.trim()) }, shape = RoundedCornerShape(12.dp)) { IconText("📖 사전", fontSize = 14.sp) }
                }
            }
            if (correcting) {
                StartButton(
                    if (state.correcting) "교정 중…" else "교정하기",
                    enabled = state.input.isNotBlank() && !state.correcting,
                    onClick = { focus.clearFocus(); onCorrect() }
                )
            } else {
                StartButton(
                    if (state.translating) "번역 중…" else "번역하기",
                    enabled = state.input.isNotBlank() && !state.translating,
                    onClick = { focus.clearFocus(); onTranslate() }
                )
            }
            if (state.translating || state.correcting) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }
            }
            val fix = state.correction
            if (correcting && state.correctionVisible && fix != null) {
                CorrectionCard(fix, onSpeak = onSpeak, onCopy = { copyToClipboard(context, it) })
            }
            val result = state.result
            if (!correcting && state.resultVisible && result != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(state.resultDirection.toLabel, color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    SelectionContainer { Text(result, color = Ink, fontSize = 19.sp, lineHeight = 28.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { copyToClipboard(context, result) }, shape = RoundedCornerShape(12.dp)) { IconText("📋 복사", fontSize = 14.sp) }
                        if (state.resultDirection == Direction.KO_EN) {
                            OutlinedButton(onClick = { onSpeak(result) }, shape = RoundedCornerShape(12.dp)) { IconText("🔊 듣기", fontSize = 14.sp) }
                            OutlinedButton(onClick = onCorrectResult, shape = RoundedCornerShape(12.dp)) { IconText("✏️ 교정", fontSize = 14.sp) }
                            OutlinedButton(onClick = { onLookup(result) }, shape = RoundedCornerShape(12.dp)) { IconText("📖 사전", fontSize = 14.sp) }
                        }
                    }
                    Button(
                        onClick = {
                            val (english, korean) = if (state.resultDirection == Direction.EN_KO) state.resultFor to result else result to state.resultFor
                            onSaveToSpeaking(english, korean)
                        },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SoftBlue, contentColor = Blue)
                    ) { IconText("🎤 문장말하기 데이터셋에 추가", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Text(
                        state.resultNote.ifBlank { "구글 번역 기반 자동 번역이에요. 중요한 내용은 한 번 더 확인하세요." },
                        color = Muted, fontSize = 11.sp
                    )
                }
            }
        }
        MessageBar(state.message, onMessageDismiss, Modifier.align(Alignment.BottomCenter))
    }
}

/** The corrected sentence, what changed, and more natural ways to say it. */
@Composable
private fun CorrectionCard(fix: Tutor.CorrectionResult, onSpeak: (String) -> Unit, onCopy: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("교정한 문장", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        SelectionContainer { Text(fix.corrected, color = Ink, fontSize = 19.sp, lineHeight = 28.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onCopy(fix.corrected) }, shape = RoundedCornerShape(12.dp)) { IconText("📋 복사", fontSize = 14.sp) }
            OutlinedButton(onClick = { onSpeak(fix.corrected) }, shape = RoundedCornerShape(12.dp)) { IconText("🔊 듣기", fontSize = 14.sp) }
        }
        if (fix.diffs.isEmpty()) {
            Text("고칠 곳이 없어요. 자연스러운 문장이에요!", color = Ink, fontSize = 14.sp)
        } else {
            Text("바뀐 곳", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            for (d in fix.diffs) {
                val line = when {
                    d.from.isNotEmpty() && d.to.isNotEmpty() -> "${d.from} → ${d.to}"
                    d.from.isNotEmpty() -> "빼기: ${d.from}"
                    else -> "넣기: ${d.to}"
                }
                Text(line, color = Ink, fontSize = 14.sp)
            }
        }
        if (fix.paraphrases.isNotEmpty()) {
            Text("이렇게 말해도 좋아요", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            for (p in fix.paraphrases) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(Modifier.weight(1f)) { Text(p, color = Ink, fontSize = 15.sp) }
                    OutlinedButton(onClick = { onSpeak(p) }, shape = RoundedCornerShape(12.dp)) { IconText("🔊", fontSize = 14.sp) }
                }
            }
        }
        Text(
            if (fix.usedModel) "Gemini Nano(기기 내 AI)로 교정했어요."
            else "기본 규칙으로 교정했어요. (대문자·관사·3인칭 등 기본만 확인해요. Gemini Nano를 쓸 수 있는 기기에서는 더 자세히 고쳐요.)",
            color = Muted, fontSize = 11.sp
        )
    }
}

@Composable
private fun LanguageBox(label: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(SoftBlue).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
