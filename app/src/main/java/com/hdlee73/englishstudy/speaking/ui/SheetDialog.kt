package com.hdlee73.englishstudy.speaking.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A panel that rises from the bottom like a bottom sheet but cannot be dragged. The Material bottom
 * sheet competes with the scrolling content inside it for every vertical drag, which made the settings
 * panel shake while it was scrolled; this panel is fixed and only its content scrolls.
 * Tapping the dimmed area outside it, or the back button, closes it.
 */
@Composable
internal fun SheetDialog(
    onDismiss: () -> Unit,
    heightFraction: Float = 0.92f,
    containerColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val none = remember { MutableInteractionSource() }
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = none, indication = null) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                Modifier.fillMaxWidth().fillMaxHeight(heightFraction).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = containerColor
            ) {
                Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) { content() }
            }
        }
    }
}
