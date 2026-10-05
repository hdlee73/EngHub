package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.englishstudy.dictionary.DictionaryUiState
import com.hdlee73.englishstudy.dictionary.SavedSort
import com.hdlee73.englishstudy.dictionary.WordEntry
import com.hdlee73.englishstudy.dictionary.displayExamples
import com.hdlee73.englishstudy.dictionary.examplePairs
import kotlin.math.abs

// The look of the original dictionary app: soft blue-grey cards, lavender 듣기 and green 저장 buttons.
private val DictBlue = Color(0xFF168C82)
private val DictDark = Color(0xFF172B2A)
private val DictGrey = Color(0xFF5E716E)
private val ListenFill = Color(0xFFDDF1EC)
private val ListenInk = Color(0xFF0C6F67)
private val SaveFill = Color(0xFFDFF0E7)
private val SaveInk = Color(0xFF386752)
private val TabOn = Color(0xFFDDF1EC)
private val TabOff = Color(0xFFF1F6F5)

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
    val focus = LocalFocusManager.current
    var sortOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<WordEntry?>(null) }
    val sorted = remember(saved, state.sort) { state.sort.apply(saved, { it.word }, { it.id }) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // The same banner as the other tabs.
            Hero("📖", "영어단어장", "단어·숙어·구동사를 찾고 저장해요", Modifier.padding(top = 10.dp, bottom = 12.dp))

            // Search field: results appear while typing, so there is no search button.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color.White).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("단어, 숙어 또는 구동사") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // Enter searches at once and puts the keyboard away so the result is fully visible.
                    keyboardActions = KeyboardActions(onSearch = { onSearch(); focus.clearFocus() })
                )
                if (state.query.isNotEmpty()) TextButton(onClick = { onQueryChange("") }) { Text("✕", color = DictGrey) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabButton("검색 결과", !state.showSaved, Modifier.weight(1f)) { onShowSaved(false) }
                TabButton("저장 단어", state.showSaved, Modifier.weight(1f)) { onShowSaved(true) }
            }

            if (state.showSaved) {
                SavedList(
                    sorted, state.sort, onSortClick = { sortOpen = true }, onExportClick = { exportOpen = true },
                    onOpen = { detail = it }, onResearch = onPickSuggestion, onDelete = onDelete
                )
            } else {
                SearchResult(state, saved, { word -> focus.clearFocus(); onPickSuggestion(word) }, onSave, onSpeak, onOpenUrl)
            }
        }
        MessageBar(state.message, onMessageDismiss, Modifier.align(Alignment.BottomCenter))
    }

    if (sortOpen) {
        AlertDialog(
            onDismissRequest = { sortOpen = false },
            title = { Text("저장 단어 정렬") },
            text = {
                Column {
                    SavedSort.values().forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSort(option); sortOpen = false }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (option == state.sort) "●  " else "○  ", color = DictBlue)
                            Text(option.title, color = DictDark)
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
            title = { Text("내보내기 형식") },
            text = {
                Column {
                    ExportFormats.forEachIndexed { index, label ->
                        Text(
                            label, color = DictDark,
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
            title = { Text(current.word + if (current.ipa.isBlank()) "" else "  " + current.ipa) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Section("한글 의미", current.korean)
                    if (current.english.isNotBlank()) Section("English definition", current.english)
                    val examples = displayExamples(current.examples)
                    if (examples.isNotBlank()) Section("예문", examples)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("닫기") } },
            dismissButton = { TextButton(onClick = { onSpeak(current.word) }) { Text("🔊 듣기") } }
        )
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(40.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) TabOn else TabOff, contentColor = if (selected) DictBlue else Color(0xFF6B7D7A)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        elevation = null
    ) { Text(text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

/** A small rounded action button like the original app's 듣기 / 저장 buttons. */
@Composable
private fun PillButton(text: String, fill: Color, ink: Color, width: androidx.compose.ui.unit.Dp, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.width(width).height(40.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = ink, disabledContainerColor = fill, disabledContentColor = ink),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), elevation = null
    ) { Text(text, fontSize = 12.sp, maxLines = 1) }
}

@Composable
private fun Section(title: String, value: String) {
    Text(title, color = DictBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 3.dp))
    Text(value.trim(), color = DictDark, fontSize = 16.sp, lineHeight = 22.sp)
}

/** Calls [onSwipe] when the finger is dragged sideways far enough (the original app's swipe gesture). */
private fun Modifier.horizontalSwipe(key: Any?, onSwipe: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onSwipe)
    pointerInput(key) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (abs(total) > 160f) current(); total = 0f },
            onDragCancel = { total = 0f },
            onHorizontalDrag = { _, amount -> total += amount }
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
            Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.searching) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(state.status, color = Color(0xFF6B7D7A), fontSize = 14.sp)
            }
        }
        if (state.suggestions.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp)) {
                    if (state.suggestionTitle.isNotBlank()) Text(state.suggestionTitle, color = DictGrey, fontSize = 13.sp)
                    state.suggestions.forEach { word ->
                        Text(
                            "$word   ›", color = DictBlue, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().clickable { onPickSuggestion(word) }.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }
        state.entry?.let { entry ->
            item {
                val alreadySaved = saved.any { it.word.equals(entry.word, ignoreCase = true) }
                EntryCard(entry, state.canSave, alreadySaved, onSave, onSpeak, onOpenUrl)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Word and pronunciation with 듣기 and 저장 right next to it, then meanings, examples and source. */
@Composable
private fun EntryCard(
    entry: WordEntry,
    canSave: Boolean,
    alreadySaved: Boolean,
    onSave: () -> Unit,
    onSpeak: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
            .horizontalSwipe(entry.word) { if (canSave) onSave() }.padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.word, color = DictDark, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                if (entry.ipa.isNotBlank()) Text(entry.ipa, color = DictGrey, fontSize = 15.sp)
            }
            PillButton("🔊 듣기", ListenFill, ListenInk, 78.dp) { onSpeak(entry.word) }
            Spacer(Modifier.width(6.dp))
            PillButton(
                if (alreadySaved) "✓ 저장" else "🔖 저장", SaveFill, SaveInk, 72.dp,
                enabled = canSave && !alreadySaved, onClick = onSave
            )
        }
        Section("한글 의미", entry.korean)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val encoded = java.net.URLEncoder.encode(entry.word, "UTF-8").replace("+", "%20")
            Text("네이버 ", color = Color(0xFF6B7D7A), fontSize = 12.sp)
            Text(
                "영한", color = DictBlue, fontSize = 12.sp, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onOpenUrl("https://en.dict.naver.com/#/search?query=$encoded") }.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            Text(" · ", color = Color(0xFF6B7D7A), fontSize = 12.sp)
            Text(
                "영영", color = DictBlue, fontSize = 12.sp, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onOpenUrl("https://dict.naver.com/enendict/#/search?query=$encoded") }.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
        if (entry.english.isNotBlank()) Section("English definition", entry.english)
        val pairs = examplePairs(entry.examples)
        if (pairs.isNotEmpty()) {
            Section(if (pairs.any { it.second.isNotBlank() }) "예문 · 한국어 해석" else "영어 예문", displayExamples(entry.examples))
        }
        Text(
            entry.source.ifBlank { "의미별 자체 정리 · 직접 작성한 한영 예문" }, color = Color(0xFF6B7D7A), fontSize = 10.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun SavedList(
    words: List<WordEntry>,
    sort: SavedSort,
    onSortClick: () -> Unit,
    onExportClick: () -> Unit,
    onOpen: (WordEntry) -> Unit,
    onResearch: (String) -> Unit,
    onDelete: (WordEntry) -> Unit
) {
    if (words.isEmpty()) {
        Text(
            "아직 저장한 단어가 없습니다. 검색 결과에서 원하는 단어만 저장할 수 있어요.",
            color = Color(0xFF6B7D7A), fontSize = 15.sp, modifier = Modifier.padding(4.dp, 14.dp)
        )
        return
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onSortClick, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TabOn, contentColor = DictBlue), elevation = null
        ) { Text("정렬: " + sort.title.substringBefore(" ("), maxLines = 1, fontSize = 14.sp) }
        Button(
            onClick = onExportClick, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SaveFill, contentColor = SaveInk), elevation = null
        ) { Text("↗ 엑셀 내보내기", maxLines = 1, fontSize = 13.sp) }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(words, key = { it.id }) { entry ->
            var menuOpen by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .horizontalSwipe(entry.id) { onDelete(entry) }
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).clickable { onOpen(entry) }) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(entry.word) }
                            if (entry.ipa.isNotBlank()) append("  " + entry.ipa)
                        },
                        color = DictDark, fontSize = 18.sp
                    )
                    Text(entry.korean.trim(), color = DictGrey, fontSize = 14.sp, maxLines = 2)
                }
                PillButton("보기", TabOff, DictBlue, 56.dp) { onOpen(entry) }
                Box {
                    Button(
                        onClick = { menuOpen = true }, modifier = Modifier.padding(start = 4.dp).size(40.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TabOff, contentColor = DictBlue),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), elevation = null
                    ) { Text("⋯", fontSize = 22.sp) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("다시 검색") }, onClick = { menuOpen = false; onResearch(entry.word) })
                        DropdownMenuItem(text = { Text("삭제") }, onClick = { menuOpen = false; onDelete(entry) })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
