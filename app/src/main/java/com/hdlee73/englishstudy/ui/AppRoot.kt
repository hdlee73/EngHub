package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.layout.PaddingValues
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
    SPEAKING("스피킹", "🎤")
}

/** The four tabs of the app; the screen of the selected tab is drawn by [content]. */
@Composable
fun AppRoot(tab: AppTab, onTab: (AppTab) -> Unit, content: @Composable (AppTab) -> Unit) {
    EnglishStudyTheme {
        Scaffold(
            containerColor = Canvas,
            bottomBar = {
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
        ) { inner: PaddingValues ->
            Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).imePadding()) { content(tab) }
        }
    }
}
