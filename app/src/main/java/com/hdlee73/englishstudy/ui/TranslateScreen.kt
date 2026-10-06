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
import com.hdlee73.englishstudy.translate.Direction
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
    onSaveToSpeaking: (english: String, korean: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Hero("🌐", "번역", "영어 ⇄ 한국어 · 단어, 문장, 긴 글까지")
            // Language bar: source, swap, target.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageBox(state.direction.fromLabel, Modifier.weight(1f))
                OutlinedButton(onClick = onSwap, shape = RoundedCornerShape(14.dp)) { Text("⇄", fontSize = 18.sp) }
                LanguageBox(state.direction.toLabel, Modifier.weight(1f))
            }
            OutlinedTextField(
                value = state.input, onValueChange = onInput, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                placeholder = { Text("번역할 내용을 입력하세요 (영어·한국어는 자동으로 알아봐요)", color = Muted) },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let(onInput)
                }, shape = RoundedCornerShape(12.dp)) { IconText("📋 붙여넣기", fontSize = 14.sp) }
                if (state.input.isNotEmpty()) OutlinedButton(onClick = onClear, shape = RoundedCornerShape(12.dp)) { Text("✕ 지우기", fontSize = 14.sp) }
                if (state.direction == Direction.EN_KO && state.input.isNotBlank()) {
                    OutlinedButton(onClick = { onLookup(state.input.trim()) }, shape = RoundedCornerShape(12.dp)) { IconText("📖 사전", fontSize = 14.sp) }
                }
            }
            StartButton(
                if (state.translating) "번역 중…" else "번역하기",
                enabled = state.input.isNotBlank() && !state.translating,
                onClick = { focus.clearFocus(); onTranslate() }
            )
            if (state.translating) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }
            }
            val result = state.result
            if (state.resultVisible && result != null) {
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
                    Text("구글 번역 기반 자동 번역이에요. 중요한 내용은 한 번 더 확인하세요.", color = Muted, fontSize = 11.sp)
                }
            }
        }
        MessageBar(state.message, onMessageDismiss, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun LanguageBox(label: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(SoftBlue).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
