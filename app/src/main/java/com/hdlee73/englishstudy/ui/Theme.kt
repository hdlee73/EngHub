package com.hdlee73.englishstudy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Blue = Color(0xFF168C82)
val Mint = Color(0xFF1E9A8C)
val Ink = Color(0xFF172B2A)
val Canvas = Color(0xFFEAF3F1)
val Miss = Color(0xFFE5675A)
val Muted = Color(0xFF6B7D7A)
val SoftBlue = Color(0xFFDDF1EC)

@Composable
fun EnglishStudyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue, primaryContainer = SoftBlue, onPrimaryContainer = Color(0xFF0C6F67),
            secondary = Mint, secondaryContainer = SoftBlue, onSecondaryContainer = Color(0xFF0C6F67),
            tertiary = Color(0xFF0C6F67), background = Canvas, surface = Color.White, onSurface = Ink,
            surfaceVariant = Color(0xFFF1F6F5), outline = Color(0xFFB9CFCA), outlineVariant = Color(0xFFDCE9E6)
        ),
        content = content
    )
}
