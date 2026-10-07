package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.englishstudy.update.UpdateChecker

/** App name, version, developer and the GitHub releases page (update info), reachable from every tab. */
@Composable
fun AboutDialog(onOpenUrl: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { UpdateChecker.installedVersion(ctx) }
    var newer by remember { mutableStateOf(UpdateChecker.available(ctx)) }
    LaunchedEffect(Unit) { newer = UpdateChecker.check(ctx) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("앱 정보", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("EngHub", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("버전 $version")
                val release = newer
                if (release != null) {
                    Text("새 버전 ${release.version.removePrefix("v")}이(가) 나왔어요. 아래 버튼으로 받아 업데이트하세요.", color = Blue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else {
                    Text("최신 버전을 쓰고 있어요.", color = Color.Gray, fontSize = 13.sp)
                }
                Text("만든이: 이현덕 (hdlee73@gmail.com)")
                Text("업데이트 정보는 GitHub 릴리스 페이지에서 확인할 수 있습니다.", color = Color.Gray, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onOpenUrl(newer?.url ?: UpdateChecker.RELEASES_URL) }) { Text(if (newer != null) "업데이트 받기" else "릴리스 페이지 열기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
