package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

enum class AppTab(val label: String, val emoji: String) {
    DICTIONARY("사전", "📖"),
    FLASHCARDS("암기", "🃏"),
    QUIZ("퀴즈", "✏️"),
    READING("리딩", "📰"),
    TRANSLATE("번역", "🌐"),
    SPEAKING("스피킹", "🎤")
}

/** The tabs of the app; the screen of the selected tab is drawn by [content]. */
@Composable
fun AppRoot(tab: AppTab, onTab: (AppTab) -> Unit, content: @Composable (AppTab) -> Unit) {
    // The tab bar can be folded away to give the screen more room; a slim handle stays to bring it back.
    var barHidden by rememberSaveable { mutableStateOf(false) }
    EnglishStudyTheme {
        Scaffold(
            containerColor = Canvas,
            bottomBar = {
                if (barHidden) {
                    Box(
                        Modifier.fillMaxWidth().background(Color.White).clickable { barHidden = false }.navigationBarsPadding().height(26.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("︿  메뉴 보이기", color = Muted, fontSize = 12.sp) }
                } else Column {
                    Box(
                        Modifier.fillMaxWidth().background(Color.White).clickable { barHidden = true }.height(24.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("﹀  메뉴 숨기기", color = Muted, fontSize = 12.sp) }
                NavigationBar(containerColor = Color.White) {
                    AppTab.values().forEach { item ->
                        NavigationBarItem(
                            selected = item == tab,
                            onClick = { onTab(item) },
                            icon = { Text(item.emoji, fontSize = 20.sp) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = SoftBlue)
                        )
                    }
                }
                }
            }
        ) { inner: PaddingValues ->
            Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).imePadding()) { content(tab) }
        }
    }
}
