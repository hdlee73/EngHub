package com.hdlee73.englishstudy.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import com.hdlee73.englishstudy.update.UpdateChecker
import kotlinx.coroutines.launch

enum class AppTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DICTIONARY("Words", androidx.compose.material.icons.Icons.Outlined.MenuBook),
    FLASHCARDS("Cards", androidx.compose.material.icons.Icons.Outlined.Style),
    QUIZ("Quiz", androidx.compose.material.icons.Icons.Outlined.Quiz),
    READING("Reading", androidx.compose.material.icons.Icons.Outlined.Newspaper),
    TRANSLATE("Translate", androidx.compose.material.icons.Icons.Outlined.Translate),
    SPEAKING("Speaking", androidx.compose.material.icons.Icons.Outlined.Mic),
    LISTENING("Listening", androidx.compose.material.icons.Icons.Outlined.Headphones),
    DOCVOICE("DocVoice", androidx.compose.material.icons.Icons.Outlined.GraphicEq)
}

/** The menu groups, in the order the left menu lists them. */
private val STUDY_TABS = listOf(AppTab.DICTIONARY, AppTab.FLASHCARDS, AppTab.QUIZ, AppTab.READING, AppTab.TRANSLATE)
private val PRACTICE_TABS = listOf(AppTab.SPEAKING, AppTab.LISTENING, AppTab.DOCVOICE)

/** The left menu (like the DailyHabit app): every tab, in two groups, with app info at the bottom. */
@Composable
private fun AppDrawer(tab: AppTab, updateAvailable: Boolean, onTab: (AppTab) -> Unit, onInfo: () -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(296.dp)
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp)
    ) {
        Text("EngHub", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(start = 10.dp, top = 22.dp, bottom = 14.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            STUDY_TABS.forEach { DrawerItem(it.label, it.icon, it == tab, null) { onTab(it) } }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), color = Color(0xFFE4E7EC))
            Text("연습", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
            PRACTICE_TABS.forEach { DrawerItem(it.label, it.icon, it == tab, null) { onTab(it) } }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), color = Color(0xFFE4E7EC))
            DrawerItem("앱 정보", androidx.compose.material.icons.Icons.Outlined.Info, false, if (updateAvailable) "NEW" else null, onInfo)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, badge: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) SoftBlue else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(22.dp), tint = if (selected) Blue else Ink)
        Text(label, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) Blue else Ink, modifier = Modifier.weight(1f).padding(start = 16.dp))
        if (badge != null) Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Blue)
    }
}

/** The slim bar above every screen: the menu button, the current tab's icon and name, and the app info button. */
@Composable
private fun TopBar(tab: AppTab, onMenu: () -> Unit, onInfo: () -> Unit) {
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(48.dp).padding(horizontal = 4.dp)) {
        BarButton(androidx.compose.material.icons.Icons.Outlined.Menu, "메뉴 열기", onMenu, Modifier.align(Alignment.CenterStart))
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(tab.icon, null, Modifier.size(20.dp), tint = Blue)
            Text(tab.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(start = 8.dp))
        }
        BarButton(androidx.compose.material.icons.Icons.Outlined.Info, "앱 정보", onInfo, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(24.dp), tint = Ink)
    }
}

/**
 * The tabs of the app, reached from the left menu (the ☰ button, or a swipe from the left edge); the screen of the selected tab is drawn by [content].
 * The menu is open when the app starts (unless another app sent something to a particular tab), and a popup at start asks to update while a newer release is not installed.
 */
@Composable
fun AppRoot(tab: AppTab, onTab: (AppTab) -> Unit, onOpenUrl: (String) -> Unit, content: @Composable (AppTab) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var aboutOpen by remember { mutableStateOf(false) }
    var newer by remember { mutableStateOf(UpdateChecker.available(context)) }
    var updatePrompt by remember { mutableStateOf(false) }
    // Every time the app is opened: look for a newer release, and keep asking until it is installed.
    LaunchedEffect(Unit) {
        newer = UpdateChecker.check(context)
        if (newer != null) updatePrompt = true
    }
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    EnglishStudyTheme {
        ModalNavigationDrawer(
            drawerState = drawer,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            drawerContent = {
                AppDrawer(
                    tab, newer != null,
                    onTab = { next -> scope.launch { drawer.close() }; onTab(next) },
                    onInfo = { scope.launch { drawer.close() }; aboutOpen = true }
                )
            }
        ) {
            Scaffold(containerColor = Canvas, topBar = {
                TopBar(tab, onMenu = { scope.launch { drawer.open() } }, onInfo = { aboutOpen = true })
            }) { inner: PaddingValues ->
                Column(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).imePadding()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content(tab) }
                }
            }
        }
        if (aboutOpen) AboutDialog(onOpenUrl = onOpenUrl, onDismiss = { aboutOpen = false })
        val release = newer
        if (updatePrompt && release != null && !aboutOpen) {
            AlertDialog(
                onDismissRequest = { updatePrompt = false },
                title = { Text("새 버전이 있어요", fontWeight = FontWeight.Bold) },
                text = { Text("EngHub ${release.version.removePrefix("v")}이(가) 나왔어요 (지금 ${UpdateChecker.installedVersion(context)}).\n업데이트하기 전까지 앱을 열 때마다 알려 드려요.") },
                confirmButton = { TextButton(onClick = { updatePrompt = false; onOpenUrl(release.url) }) { Text("업데이트 받기") } },
                dismissButton = { TextButton(onClick = { updatePrompt = false }) { Text("나중에") } }
            )
        }
    }
}
