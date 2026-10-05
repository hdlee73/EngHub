package com.hdlee73.englishstudy.docvoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.englishstudy.ui.Hero

/** The banner at the top of the screen: the same one every other LexiFlow tab has, with the info button and any actions at its right end. */
@Composable
fun ScreenHero(emoji: String, title: String, subtitle: String, info: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Hero(emoji, title, subtitle) {
        if (info != null) InfoButton(title, info, Color.White)
        actions()
    }
}

/** A plain title for a bottom sheet, with an optional info button. */
@Composable
fun SheetTitle(title: String, info: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Dv.Label, modifier = Modifier.weight(1f))
        if (info != null) InfoButton(title, info, Dv.Secondary)
    }
}

@Composable
fun InfoButton(title: String, text: String, tint: Color = Dv.Secondary) {
    var open by remember { mutableStateOf(false) }
    RoundIcon(Icons.Rounded.Info, tint, 24.dp) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Dv.Card,
            title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text(text, fontSize = 15.sp, color = Dv.Label, lineHeight = 21.sp) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("확인", color = Dv.Blue, fontWeight = FontWeight.SemiBold) } },
        )
    }
}

/** 배경 없는 둥근 아이콘 버튼 */
@Composable
fun RoundIcon(icon: ImageVector, tint: Color = Dv.Blue, iconSize: androidx.compose.ui.unit.Dp = 24.dp, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 채워진 원형 아이콘 버튼 */
@Composable
fun CircleIcon(icon: ImageVector, bg: Color, tint: Color, size: androidx.compose.ui.unit.Dp = 48.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun GroupHeader(text: String) {
    Text(text, fontSize = 13.sp, color = Dv.Secondary, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
}

class GroupScope {
    val rows = ArrayList<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) { rows.add(content) }
}

/** iOS 의 inset grouped 목록 */
@Composable
fun Group(content: GroupScope.() -> Unit) {
    val scope = GroupScope().apply(content)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dv.Card)) {
        scope.rows.forEachIndexed { i, r ->
            r()
            if (i < scope.rows.lastIndex) Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(0.5.dp).background(Dv.Separator))
        }
    }
}

/** 행 앞의 단색(파랑) 아이콘 */
@Composable
fun IconTile(icon: ImageVector, color: Color) {
    Icon(icon, null, tint = Dv.Blue, modifier = Modifier.size(24.dp))
}

@Composable
fun RowShell(icon: ImageVector? = null, color: Color = Dv.Blue, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconTile(icon, color); Spacer(Modifier.width(14.dp)) }
        content()
    }
}

@Composable
fun ValueRow(icon: ImageVector, color: Color, title: String, value: String? = null, titleColor: Color = Dv.Label, onClick: (() -> Unit)? = null) {
    RowShell(icon, color, onClick) {
        Text(title, fontSize = 17.sp, color = titleColor, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (value != null) { Spacer(Modifier.width(8.dp)); Text(value, fontSize = 15.sp, color = Dv.Secondary, maxLines = 1) }
    }
}

/** 눌러서 고르는 메뉴 행 (iOS 풀다운) */
@Composable
fun MenuRow(icon: ImageVector, color: Color, title: String, current: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RowShell(icon, color, { open = true }) {
            Text(title, fontSize = 17.sp, color = Dv.Label, modifier = Modifier.weight(1f))
            Text(current, fontSize = 17.sp, color = Dv.Secondary, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.UnfoldMore, null, tint = Dv.Tertiary, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Dv.Card) {
            options.forEach { (label, id) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 16.sp) },
                    trailingIcon = { if (label == current) Icon(Icons.Rounded.Check, null, tint = Dv.Blue, modifier = Modifier.size(18.dp)) },
                    onClick = { open = false; onPick(id) },
                )
            }
        }
    }
}

@Composable
fun ToggleRow(icon: ImageVector, color: Color, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowShell(icon, color) {
        Text(title, fontSize = 17.sp, color = Dv.Label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Dv.Blue, checkedThumbColor = Color.White, checkedBorderColor = Dv.Blue,
                uncheckedTrackColor = Color(0xFFE9E9EA), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color(0xFFE9E9EA),
            ),
        )
    }
}

@Composable
fun SegmentedRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) { Segmented(options, selected, onSelect) }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val outer = RoundedCornerShape(9.dp)
    val inner = RoundedCornerShape(7.dp)
    Row(Modifier.fillMaxWidth().clip(outer).background(Dv.SegmentTrack).padding(2.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).height(32.dp)
                    .then(if (sel) Modifier.shadow(1.5.dp, inner).background(Color.White, inner) else Modifier)
                    .clip(inner)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = Dv.Label, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SliderRow(icon: ImageVector, color: Color, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    RowShell(icon, color) {
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            modifier = Modifier.weight(1f),
            thumb = { Box(Modifier.size(26.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape)) },
            colors = SliderDefaults.colors(
                activeTrackColor = Dv.Blue, inactiveTrackColor = Dv.Fill,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.width(10.dp))
        Text(valueText, fontSize = 15.sp, color = Dv.Secondary, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
    }
}

@Composable
fun FilledButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, color: Color = Dv.Blue, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else Dv.Fill)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (enabled) Color.White else Dv.Tertiary
        if (icon != null) { Icon(icon, null, tint = c, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c)
    }
}
