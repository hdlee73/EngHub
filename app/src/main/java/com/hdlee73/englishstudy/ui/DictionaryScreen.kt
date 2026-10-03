package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.englishstudy.dictionary.DictionaryUiState
import com.hdlee73.englishstudy.dictionary.SavedSort
import com.hdlee73.englishstudy.dictionary.WordEntry
import com.hdlee73.englishstudy.dictionary.displayExamples

private val ExportFormats = listOf(
    "단어·뜻·영어 예문(한글 해석 병기)",
    "한글 예문 해석 + 영어 예문",
    "영어 예문만"
)

@Composable
fun DictionaryScreen(
    state: DictionaryUiState,
    saved: List<WordEntry>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPickSuggestion: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (WordEntry) -> Unit,
    onShowSaved: (Boolean) -> Unit,
    onSort: (SavedSort) -> Unit,
    onExport: (Int) -> Unit,
    onSpeak: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onMessageDismiss: () -> Unit
) {
    var sortOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<WordEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<WordEntry?>(null) }
    val sorted = remember(saved, state.sort) { state.sort.apply(saved, { it.word }, { it.id }) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            ScreenTitle("영어 사전", Modifier.padding(top = 12.dp, bottom = 8.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("영어 단어나 숙어 (예: look forward to)") },
                trailingIcon = {
                    if (state.query.isNotEmpty()) TextButton(onClick = { onQueryChange("") }) { Text("✕") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.showSaved, onClick = { onShowSaved(false) }, label = { Text("검색 결과") })
                FilterChip(selected = state.showSaved, onClick = { onShowSaved(true) }, label = { Text("저장 단어 (${saved.size})") })
            }
            if (state.showSaved) {
                SavedList(
                    sorted, state.sort, onSortClick = { sortOpen = true }, onExportClick = { exportOpen = true },
                    onOpen = { detail = it }, onSpeak = onSpeak
                )
            } else {
                SearchResult(state, saved, onPickSuggestion, onSave, onSpeak, onOpenUrl)
            }
        }
        MessageBar(state.message, onMessageDismiss, Modifier.align(Alignment.BottomCenter))
    }

    if (sortOpen) {
        AlertDialog(
            onDismissRequest = { sortOpen = false },
            title = { Text("정렬") },
            text = {
                Column {
                    SavedSort.values().forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSort(option); sortOpen = false }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (option == state.sort) "●  " else "○  ", color = Blue)
                            Text(option.title, color = Ink)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sortOpen = false }) { Text("닫기") } }
        )
    }
    if (exportOpen) {
        AlertDialog(
            onDismissRequest = { exportOpen = false },
            title = { Text("엑셀 내보내기 형식") },
            text = {
                Column {
                    ExportFormats.forEachIndexed { index, label ->
                        Text(
                            label, color = Ink,
                            modifier = Modifier.fillMaxWidth().clickable { exportOpen = false; onExport(index + 1) }.padding(vertical = 14.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { exportOpen = false }) { Text("취소") } }
        )
    }
    detail?.let { entry ->
        // Show the list's current copy so a deleted word closes the dialog.
        val current = saved.firstOrNull { it.id == entry.id }
        if (current == null) detail = null else AlertDialog(
            onDismissRequest = { detail = null },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { EntryBody(current, onSpeak, onOpenUrl) } },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("닫기") } },
            dismissButton = { TextButton(onClick = { confirmDelete = current }) { Text("삭제", color = Miss) } }
        )
    }
    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("‘${entry.word}’ 삭제") },
            text = { Text("저장한 단어를 삭제할까요? 암기·퀴즈 기록도 더는 쓰이지 않습니다.") },
            confirmButton = { TextButton(onClick = { onDelete(entry); confirmDelete = null; detail = null }) { Text("삭제", color = Miss) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("취소") } }
        )
    }
}

@Composable
private fun SearchResult(
    state: DictionaryUiState,
    saved: List<WordEntry>,
    onPickSuggestion: (String) -> Unit,
    onSave: () -> Unit,
    onSpeak: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.searching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                if (state.searching) Spacer(Modifier.size(8.dp))
                Text(state.status, color = Muted, fontSize = 14.sp)
            }
        }
        if (state.suggestions.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        if (state.suggestionTitle.isNotBlank()) Text(state.suggestionTitle, color = Muted, fontSize = 13.sp)
                        state.suggestions.forEach { word ->
                            Text(
                                word, color = Blue, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth().clickable { onPickSuggestion(word) }.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
        state.entry?.let { entry ->
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        EntryBody(entry, onSpeak, onOpenUrl)
                        val alreadySaved = saved.any { it.word.equals(entry.word, ignoreCase = true) }
                        Button(
                            onClick = onSave,
                            enabled = state.canSave,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue)
                        ) {
                            Text(
                                when {
                                    !state.canSave -> "불러오는 중…"
                                    alreadySaved -> "저장됨 · 다시 저장해 갱신"
                                    else -> "★ 단어장에 저장"
                                }
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SavedList(
    words: List<WordEntry>,
    sort: SavedSort,
    onSortClick: () -> Unit,
    onExportClick: () -> Unit,
    onOpen: (WordEntry) -> Unit,
    onSpeak: (String) -> Unit
) {
    if (words.isEmpty()) {
        EmptyState("⭐", "저장한 단어가 없어요", "단어를 검색한 뒤 ‘단어장에 저장’을 누르면 암기·퀴즈·스피킹에서 쓸 수 있어요.")
        return
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onSortClick, modifier = Modifier.weight(1f)) { Text("정렬: ${sort.title.substringBefore(" (")}", maxLines = 1) }
        OutlinedButton(onClick = onExportClick, modifier = Modifier.weight(1f)) { Text("↗ 엑셀 내보내기", maxLines = 1) }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(words, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(entry) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.word, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        val first = meaningLines(entry.korean).firstOrNull().orEmpty()
                        if (first.isNotEmpty()) Text(first, color = Muted, fontSize = 14.sp, maxLines = 1)
                    }
                    TextButton(onClick = { onSpeak(entry.word) }) { Text("🔊", fontSize = 20.sp) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Word, pronunciation, Korean and English meanings, examples and links to the Naver dictionaries. */
@Composable
fun EntryBody(entry: WordEntry, onSpeak: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.word, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                if (entry.ipa.isNotBlank()) Text(entry.ipa, color = Muted, fontSize = 15.sp)
            }
            TextButton(onClick = { onSpeak(entry.word) }) { Text("🔊", fontSize = 24.sp) }
        }
        if (entry.korean.isNotBlank()) Text(entry.korean.trim(), color = Ink, fontSize = 16.sp, lineHeight = 22.sp)
        if (entry.english.isNotBlank()) {
            Text("영어 풀이", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(entry.english.trim(), color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
        }
        val examples = displayExamples(entry.examples)
        if (examples.isNotBlank()) {
            Text("예문", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(examples.trim(), color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val encoded = java.net.URLEncoder.encode(entry.word, "UTF-8").replace("+", "%20")
            OutlinedButton(onClick = { onOpenUrl("https://en.dict.naver.com/#/search?query=$encoded") }) { Text("영한 ↗", fontSize = 13.sp) }
            OutlinedButton(onClick = { onOpenUrl("https://dict.naver.com/enendict/#/search?query=$encoded") }) { Text("영영 ↗", fontSize = 13.sp) }
        }
    }
}
