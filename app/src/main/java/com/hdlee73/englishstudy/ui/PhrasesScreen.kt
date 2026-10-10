package com.hdlee73.englishstudy.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.englishstudy.dictionary.SavedWordsRepository
import com.hdlee73.englishstudy.dictionary.WordEntry
import com.hdlee73.englishstudy.phrases.Phrase
import com.hdlee73.englishstudy.phrases.PhraseBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Patterns and expressions used in the US and the UK: search them in English or Korean, narrow them by country and kind,
 * hear them, look them up in the dictionary or save them to the word list.
 */
@Composable
fun PhrasesScreen(onSpeak: (String) -> Unit, onLookup: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by produceState(emptyList<Phrase>()) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("phrases_us_uk.txt").bufferedReader(Charsets.UTF_8).use { PhraseBank.parse(it.readText()) } }.getOrDefault(emptyList())
        }
    }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    val results = remember(all, query, region, category) { PhraseBank.search(all, query, region, category) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Hero("💬", "Phrases", "미국·영국에서 흔히 쓰는 패턴과 표현", Modifier.padding(top = 4.dp, bottom = 10.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("영어 표현이나 한국어로 검색 (예: wondering, 소용없다, lift)") },
            shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = region == null, onClick = { region = null }, label = { Text("전체") })
            PhraseBank.REGIONS.forEach { r -> FilterChip(selected = region == r, onClick = { region = if (region == r) null else r }, label = { Text(r) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("모든 종류") })
            PhraseBank.CATEGORIES.forEach { c -> FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c) }) }
        }
        Text("${results.size}개", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(results, key = { it.phrase + "|" + it.region }) { p ->
                PhraseCard(
                    p, onSpeak, onLookup,
                    onSave = {
                        scope.launch(Dispatchers.IO) {
                            val ok = SavedWordsRepository.get(context).save(
                                WordEntry(
                                    word = p.phrase, ipa = "", korean = p.korean + if (p.note.isNotBlank()) "\n(${p.note})" else "", english = "",
                                    examples = p.example + "\t" + p.exampleKo, source = "패턴·표현 (${p.region})"
                                )
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, if (ok) "‘${p.phrase}’을(를) 단어장에 저장했습니다." else "저장하지 못했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp)) }
        }
    }
}

@Composable
private fun PhraseCard(p: Phrase, onSpeak: (String) -> Unit, onLookup: (String) -> Unit, onSave: () -> Unit) {
    val badge = when (p.region) {
        "미국" -> Color(0xFF2F6FDB)
        "영국" -> Color(0xFFC0392B)
        else -> Color(0xFF168C82)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.region, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(badge).padding(horizontal = 8.dp, vertical = 2.dp))
            Text(p.category, color = Muted, fontSize = 12.sp)
        }
        Text(p.phrase, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 25.sp)
        Text(p.korean, color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
        if (p.note.isNotBlank()) Text(p.note, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SoftBlue).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.example, color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
            Text(p.exampleKo, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSpeak(p.example) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("🔊 예문", fontSize = 13.sp) }
            OutlinedButton(onClick = { onSave() }, shape = RoundedCornerShape(12.dp)) { Text("⭐ 단어장", fontSize = 13.sp) }
            OutlinedButton(onClick = { onLookup(p.phrase) }, shape = RoundedCornerShape(12.dp)) { Text("📖 사전", fontSize = 13.sp) }
        }
    }
}
