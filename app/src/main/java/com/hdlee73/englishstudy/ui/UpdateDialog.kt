package com.hdlee73.englishstudy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.englishstudy.update.UpdateChecker
import com.hdlee73.englishstudy.update.UpdateInstaller
import kotlinx.coroutines.launch
import java.io.File

/**
 * Update popup: downloads the new APK in the app, then opens the system installer (the user taps "Install" once).
 * The first time, Android asks to allow EngHub to install apps; after allowing, the button continues from the downloaded file.
 */
@Composable
fun UpdateDialog(release: UpdateChecker.Release, onOpenUrl: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy = progress != null && file == null

    fun start() {
        error = null
        file?.let { apk ->
            if (UpdateInstaller.canInstall(ctx)) UpdateInstaller.install(ctx, apk) else UpdateInstaller.openInstallPermissionSettings(ctx)
            return
        }
        progress = -1f
        scope.launch {
            runCatching { UpdateInstaller.download(ctx, release) { progress = it } }
                .onSuccess { apk ->
                    file = apk
                    if (UpdateInstaller.canInstall(ctx)) UpdateInstaller.install(ctx, apk) else UpdateInstaller.openInstallPermissionSettings(ctx)
                }
                .onFailure { progress = null; error = it.message ?: "다운로드에 실패했어요" }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("새 버전이 있어요", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("EngHub ${release.version.removePrefix("v")}이(가) 나왔어요 (지금 ${UpdateChecker.installedVersion(ctx)}).")
                when {
                    error != null -> Text(error!!, color = Blue, fontSize = 13.sp)
                    file != null -> Text(
                        if (UpdateInstaller.canInstall(ctx)) "받기가 끝났어요. 설치 화면에서 ‘설치’(또는 ‘업데이트’)를 눌러 주세요."
                        else "‘이 출처 허용’을 한 번 켜 주세요. 켠 뒤 돌아와서 ‘설치하기’를 누르면 됩니다.",
                        fontSize = 13.sp
                    )
                    busy -> {
                        val p = progress!!
                        if (p in 0f..1f) LinearProgressIndicator(progress = { p }) else LinearProgressIndicator()
                        Text(if (p in 0f..1f) "받는 중… ${(p * 100).toInt()}%" else "받는 중…", fontSize = 13.sp)
                    }
                    else -> Text("앱 안에서 바로 받고 설치 화면까지 열어 드려요. 안드로이드 규칙상 마지막 ‘설치’ 버튼은 직접 한 번 눌러야 합니다.", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (release.apkUrl == null) { onDismiss(); onOpenUrl(release.url) } else start()
            }) { Text(if (file != null) "설치하기" else if (error != null) "다시 받기" else "업데이트") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(if (file != null) "닫기" else "나중에") } },
    )
}
