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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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

private const val LISTEN_LIMIT = 100

/**
 * Patterns and expressions used in the US and the UK: search them in English or Korean, narrow them by country and kind,
 * hear them, look them up in the dictionary or save them to the word list.
 */
@Composable
fun PhrasesScreen(
    onSpeak: (String) -> Unit,
    onSpeakAll: (texts: List<String>, gapMs: Long, onStart: (Int) -> Unit, onEnd: () -> Unit) -> Unit,
    onStopSpeaking: () -> Unit,
    onLookup: (String) -> Unit
) {
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
    var topic by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val results = remember(all, query, region, category, topic) { PhraseBank.search(all, query, region, category, topic) }
    val topics = remember(all) { PhraseBank.TOPICS.filter { t -> all.any { it.topic == t } } }
    // A changed filter or search ends a running continuous listening, since its list no longer matches.
    DisposableEffect(results) { onDispose { if (playing != null) onStopSpeaking() } }
    LaunchedEffect(playing) { playing?.let { if (it in results.indices) listState.animateScrollToItem(it) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Hero("💬", "Phrases", "미국·영국에서 흔히 쓰는 패턴과 표현", Modifier.padding(top = 4.dp, bottom = 10.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showFilters = !showFilters }, shape = RoundedCornerShape(12.dp)) {
                Text(if (showFilters) "검색·선택 숨기기 ▲" else "검색·선택 보이기 ▼", fontSize = 13.sp)
            }
            val active = listOfNotNull(region, category, topic).size + if (query.isNotBlank()) 1 else 0
            if (!showFilters && active > 0) Text("조건 ${active}개 적용 중", color = Muted, fontSize = 12.sp)
        }
        if (showFilters) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                placeholder = { Text("영어 표현이나 한국어로 검색 (예: wondering, 소용없다, lift)") },
                shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            Text("나라", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = region == null, onClick = { region = null }, label = { Text("전체") })
                PhraseBank.REGIONS.forEach { r -> FilterChip(selected = region == r, onClick = { region = if (region == r) null else r }, label = { Text(r) }) }
            }
            Text("종류", color = Muted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("모든 종류") })
                PhraseBank.CATEGORIES.forEach { c -> FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c) }) }
            }
            Text("주제", color = Muted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = topic == null, onClick = { topic = null }, label = { Text("모든 주제") })
                topics.forEach { t -> FilterChip(selected = topic == t, onClick = { topic = if (topic == t) null else t }, label = { Text(t) }) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (playing != null) "듣는 중 ${playing!! + 1}/${minOf(results.size, LISTEN_LIMIT)}" else "${results.size}개", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (playing == null) {
                OutlinedButton(
                    enabled = results.isNotEmpty(), shape = RoundedCornerShape(12.dp),
                    onClick = {
                        val list = results.take(LISTEN_LIMIT)
                        playing = 0
                        onSpeakAll(list.map { it.example }, 1200L, { playing = it }, { playing = null })
                    }
                ) { Text("🎧 예문 연속 듣기", fontSize = 13.sp) }
            } else {
                Button(onClick = { onStopSpeaking() }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("■ 멈춤", fontSize = 13.sp) }
            }
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(results, key = { _, it -> it.phrase + "|" + it.region }) { index, p ->
                PhraseCard(
                    p, index == playing, onSpeak, onLookup,
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
private fun PhraseCard(p: Phrase, highlighted: Boolean, onSpeak: (String) -> Unit, onLookup: (String) -> Unit, onSave: () -> Unit) {
    val badge = when (p.region) {
        "미국" -> Color(0xFF2F6FDB)
        "영국" -> Color(0xFFC0392B)
        else -> Color(0xFF168C82)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (highlighted) SoftBlue else Color.White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.region, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(badge).padding(horizontal = 8.dp, vertical = 2.dp))
            Text(p.category + if (p.topic.isNotBlank()) " · ${p.topic}" else "", color = Muted, fontSize = 12.sp)
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
