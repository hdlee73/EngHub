package com.hdlee73.englishstudy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Blue = Color(0xFF285BE6)
val Mint = Color(0xFF2FAE8C)
val Ink = Color(0xFF17243D)
val Canvas = Color(0xFFF4F7FF)
val Miss = Color(0xFFE5484D)
val Muted = Color(0xFF5B6B88)
val SoftBlue = Color(0xFFE3EBFF)

@Composable
fun EnglishStudyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Blue, background = Canvas, surface = Color.White, onSurface = Ink),
        content = content
    )
}
