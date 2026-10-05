package com.hdlee73.englishstudy.docvoice.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hdlee73.englishstudy.docvoice.DocVoiceViewModel
import com.hdlee73.englishstudy.docvoice.JobState
import com.hdlee73.englishstudy.docvoice.OutFile
import com.hdlee73.englishstudy.docvoice.RecPhase
import com.hdlee73.englishstudy.docvoice.core.Extractors
import com.hdlee73.englishstudy.docvoice.core.LiveLang
import com.hdlee73.englishstudy.docvoice.core.TtsVoices
import com.hdlee73.englishstudy.docvoice.core.WhisperSize
import java.util.Locale

/** The DocVoice tab: a switch between its three tools under the shared banner. */
@Composable
fun DocVoiceScreen(vm: DocVoiceViewModel) {
    Box(Modifier.fillMaxSize().background(Dv.Bg)) {
        if (vm.tab == 2) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { RecordScreen(vm) }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { if (vm.tab == 0) TtsScreen(vm) else SttScreen(vm) }
        }
    }
}

/** Document → MP3, MP3 → document, or recording: sits right under the banner. */
@Composable
private fun ModeSwitch(vm: DocVoiceViewModel) {
    Segmented(listOf("문서 → MP3", "MP3 → 문서", "녹음"), vm.tab) { vm.tab = it }
}

// ---- 설정 시트 --------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Dv.Bg,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

// ---- 녹음 ---------------------------------------------------------------------------

private fun clock(sec: Double): String {
    val s = sec.toInt()
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.RecordScreen(vm: DocVoiceViewModel) {
    val ctx = LocalContext.current
    val rec by vm.rec.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) vm.startRecording() else Toast.makeText(ctx, "마이크 권한이 필요해요.", Toast.LENGTH_SHORT).show()
    }
    fun start() {
        val need = ArrayList<String>()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need += Manifest.permission.RECORD_AUDIO
        if (android.os.Build.VERSION.SDK_INT >= 31 && vm.recBluetooth &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) need += Manifest.permission.BLUETOOTH_CONNECT
        if (need.isEmpty()) vm.startRecording() else permission.launch(need.toTypedArray())
    }
    val live = rec.phase == RecPhase.Recording || rec.phase == RecPhase.Paused
    val busy = rec.phase == RecPhase.Preparing || rec.phase == RecPhase.Finishing
    val stopped = rec.phase == RecPhase.Stopped
    val idle = rec.phase == RecPhase.Idle || rec.phase == RecPhase.Failed

    ScreenHero("🎙️", "녹음 & 문서화", "말하는 대로 글자가 나타나요") {
        if (stopped) RoundIcon(Icons.Rounded.Tune, Color.White) { sheet = true }
    }
    ModeSwitch(vm)
    val jobNow = vm.job.collectAsState().value
    val modelReady = remember(vm.recLang, jobNow) { vm.liveReady(vm.recLang) }
    if (idle) {
        Segmented(LiveLang.values().map { it.label }, LiveLang.values().indexOfFirst { it.key == vm.recLang }.coerceAtLeast(0)) {
            vm.recLang = LiveLang.values()[it].key; vm.save()
        }
        Segmented(listOf("자동 (블루투스 우선)", "내장 마이크"), if (vm.recBluetooth) 0 else 1) { vm.recBluetooth = it == 0; vm.save() }
    } else if (live || busy) {
        Text(LiveLang.of(vm.recLang).label + if (rec.source.isNotEmpty()) " · ${rec.source}" else "", fontSize = 13.sp, color = Dv.Secondary, modifier = Modifier.padding(start = 4.dp))
    }
    JobPanel(vm)

    // 받아쓰기 영역 (화면의 대부분)
    val scroll = rememberScrollState()
    LaunchedEffect(rec.segments.size, rec.partial) { scroll.animateScrollTo(scroll.maxValue) }
    Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Dv.Card)) {
        if (rec.segments.isEmpty() && rec.partial.isEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Icon(
                    when {
                        stopped -> Icons.Rounded.ErrorOutline
                        busy -> Icons.Rounded.GraphicEq
                        else -> Icons.Rounded.Mic
                    },
                    null, tint = Dv.Fill, modifier = Modifier.size(88.dp),
                )
                if (stopped) {
                    Text(
                        rec.message ?: if (rec.peak < 0.01f) "마이크 소리가 들어오지 않았어요" else "인식된 말이 없어요",
                        fontSize = 15.sp, color = Dv.Secondary,
                    )
                }
                if (idle && !modelReady) {
                    FilledButton(
                        "${LiveLang.of(vm.recLang).label} 모델 내려받기 (약 ${LiveLang.of(vm.recLang).mb}MB)", Icons.Rounded.CloudDownload,
                        enabled = jobNow !is JobState.Running, modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                    ) { vm.downloadModel() }
                }
            }
        }
        if (rec.segments.isNotEmpty() || rec.partial.isNotEmpty()) Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rec.segments.forEach { seg ->
                Text(seg.text, fontSize = 24.sp, color = Dv.Label, lineHeight = 33.sp, fontWeight = FontWeight.Medium)
            }
            if (rec.partial.isNotEmpty()) Text(rec.partial, fontSize = 24.sp, color = Dv.Blue, lineHeight = 33.sp, fontWeight = FontWeight.Medium)
        }
    }

    // 하단 컨트롤
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (busy) {
            val fr = rec.fraction
            if (fr != null) LinearProgressIndicator(progress = { fr }, color = Dv.Blue, trackColor = Dv.Fill, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
            else LinearProgressIndicator(color = Dv.Blue, trackColor = Dv.Fill, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
            rec.message?.let { Text(it, fontSize = 13.sp, color = Dv.Secondary) }
        }
        if (rec.phase == RecPhase.Failed) Text(rec.message ?: "", fontSize = 13.sp, color = Dv.Red)
        if (!stopped) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(clock(rec.seconds), fontSize = 28.sp, fontWeight = FontWeight.Light, color = Dv.Label, style = TextStyle(fontFeatureSettings = "tnum"))
                if (live) LevelBars(rec.level, rec.phase == RecPhase.Recording)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    CircleIcon(if (rec.phase == RecPhase.Paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, Dv.Fill, Dv.Label, 56.dp) { vm.togglePause() }
                    RecordButton(true, true) { vm.stopRecording() }
                    Spacer(Modifier.size(56.dp))
                } else RecordButton(busy, rec.phase == RecPhase.Preparing || !busy) { if (busy) vm.stopRecording() else start() }
            }
        } else {
            val formats = listOf("docx", "pdf", "xlsx", "txt", "srt")
            Segmented(formats.map { it.uppercase(Locale.ROOT) }, formats.indexOf(vm.recFormat).coerceAtLeast(0)) { vm.recFormat = formats[it] }
            FilledButton("문서로 내보내기", Icons.Rounded.Description, enabled = rec.segments.isNotEmpty()) { vm.exportRecording() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LabeledAction(Icons.Rounded.Close, "닫기", Dv.Label) { vm.newRecording() }
                LabeledAction(Icons.Rounded.Mic, "다시 녹음", Dv.Red) { vm.newRecording(); start() }
                if (rec.wav == null) LabeledAction(Icons.Rounded.Save, "파일 저장", Dv.Label) { vm.saveRecordingFile() }
                else {
                    LabeledAction(Icons.Rounded.PlayArrow, "재생", Dv.Label) { openFile(ctx, rec.wav!!) }
                    LabeledAction(Icons.Rounded.IosShare, "공유", Dv.Label) { shareFile(ctx, rec.wav!!) }
                }
            }
        }
    }

    if (sheet) SettingsSheet({ sheet = false }) {
        SheetTitle("내보내기 설정", "• 정밀 재인식: 녹음이 끝난 뒤 더 정확한 모델로 처음부터 다시 받아써서 문서에 담아요. (시간이 더 걸려요)\n• 화자 구분: 누가 말했는지 나눠 줄을 바꿔요.\n• 줄바꿈 간격: 발언 사이가 이 시간보다 길면 줄을 바꿔요.")
        Group {
            row { ToggleRow(Icons.Rounded.AutoAwesome, Dv.Blue, "정밀 재인식", vm.recRefine) { vm.recRefine = it } }
            if (vm.recRefine) row { SegmentedRow(WhisperSize.values().map { it.label }, vm.size.ordinal) { vm.size = WhisperSize.values()[it]; vm.save() } }
            row { ToggleRow(Icons.Rounded.People, Dv.Blue, "화자 구분", vm.recDiarize) { vm.recDiarize = it } }
            if (vm.recDiarize) row { ToggleRow(Icons.Rounded.RecordVoiceOver, Dv.Blue, "화자 표시", vm.showSpeaker) { vm.showSpeaker = it; vm.save() } }
            row { ToggleRow(Icons.Rounded.Schedule, Dv.Blue, "시간 표시", vm.includeTime) { vm.includeTime = it; vm.save() } }
            row { SliderRow(Icons.Rounded.Subject, Dv.Blue, String.format(Locale.US, "%.1f초", vm.gap), vm.gap, 0.5f..4f, 6) { vm.gap = Math.round(it * 10) / 10f; vm.save() } }
        }
    }
}

@Composable
private fun LabeledAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 11.sp, color = Dv.Secondary)
    }
}

@Composable
private fun LevelBars(level: Float, active: Boolean) {
    val history = remember { mutableStateListOf<Float>().apply { repeat(22) { add(0f) } } }
    LaunchedEffect(level, active) {
        history.removeAt(0)
        history.add(if (active) level else 0f)
    }
    Row(Modifier.height(32.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        history.forEach { v ->
            Box(Modifier.width(3.dp).height((4 + 26 * v).dp).clip(CircleShape).background(if (active) Dv.Blue else Dv.Tertiary))
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).border(3.dp, Dv.Tertiary, CircleShape).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Dv.Red))
        else Box(Modifier.size(58.dp).clip(CircleShape).background(Dv.Red))
    }
}

// ---- 읽어주기 (문서 → 음성) ---------------------------------------------------------------

@Composable
private fun TtsScreen(vm: DocVoiceViewModel) {
    val busy = vm.job.collectAsState().value is JobState.Running
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pickDoc) }

    ScreenHero(
        "🔊", "문서 → MP3", "문서를 남성 목소리 MP3로 만들어요",
        "문서를 자연스러운 남성 목소리 MP3로 만들어요.\n\n지원 형식: ${Extractors.SUPPORTED.joinToString(", ")}\n\nMicrosoft 신경망 음성을 쓰므로 인터넷 연결이 필요해요. 결과는 다운로드/DocVoice 폴더에 저장돼요.",
    )
    ModeSwitch(vm)
    JobPanel(vm)

    Group {
        row {
            ValueRow(
                Icons.Rounded.UploadFile, Dv.Blue, vm.ttsFile?.name ?: "문서 선택", vm.ttsFile?.size?.takeIf { it >= 0 }?.let(::humanSize),
                titleColor = if (vm.ttsFile == null) Dv.Blue else Dv.Label,
            ) { picker.launch(arrayOf("*/*")) }
        }
    }
    Group {
        row {
            MenuRow(Icons.Rounded.RecordVoiceOver, Dv.Blue, "한국어", TtsVoices.KO.entries.firstOrNull { it.value == vm.koVoice }?.key ?: "", TtsVoices.KO.map { it.key to it.value }) {
                vm.koVoice = it; vm.save()
            }
        }
        row { SegmentedRow(listOf("미국식", "영국식"), if (vm.accent == "uk") 1 else 0) { vm.accent = if (it == 1) "uk" else "us"; vm.save() } }
        row {
            val list = if (vm.accent == "uk") TtsVoices.EN_UK else TtsVoices.EN_US
            MenuRow(Icons.Rounded.Translate, Dv.Blue, "English", list.entries.firstOrNull { it.value == vm.enVoice }?.key ?: "", list.map { it.key to it.value }) {
                if (vm.accent == "uk") vm.enVoiceUk = it else vm.enVoiceUs = it
                vm.save()
            }
        }
        row { SliderRow(Icons.Rounded.Speed, Dv.Blue, String.format(Locale.US, "%.1f×", vm.speed), vm.speed, 0.7f..1.5f, 7) { vm.speed = Math.round(it * 10) / 10f; vm.save() } }
    }
    FilledButton("MP3 만들기", Icons.Rounded.VolumeUp, enabled = vm.ttsFile != null && !busy) { vm.startTts() }
}

// ---- 받아쓰기 (음성 → 문서) ---------------------------------------------------------------

@Composable
private fun SttScreen(vm: DocVoiceViewModel) {
    val busy = vm.job.collectAsState().value is JobState.Running
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pickAudio) }
    var sheet by remember { mutableStateOf(false) }

    ScreenHero(
        "📝", "MP3 → 문서", "음성 파일을 문서로 받아써요",
        "음성 파일(mp3, m4a, wav, mp4 등)을 문서로 바꿔요.\n\n• XLSX: 한 문장이 한 행에 들어가요. 영어는 단어 3개 이상일 때만 한 문장으로 쳐요.\n• DOCX · PDF · TXT: 화자가 바뀌거나 발언 간격이 길면 줄을 바꿔요.\n\n인식은 휴대폰 안에서 이뤄져요. 처음 한 번만 모델을 내려받고, 화면을 꺼도 계속돼요.",
    ) { RoundIcon(Icons.Rounded.Tune, Color.White) { sheet = true } }
    ModeSwitch(vm)
    JobPanel(vm)

    Group {
        row {
            ValueRow(
                Icons.Rounded.GraphicEq, Dv.Blue, vm.sttFile?.name ?: "음성 선택", vm.sttFile?.size?.takeIf { it >= 0 }?.let(::humanSize),
                titleColor = if (vm.sttFile == null) Dv.Blue else Dv.Label,
            ) { picker.launch(arrayOf("audio/*", "video/*")) }
        }
    }
    Group {
        row { SegmentedRow(vm.formats.map { it.uppercase(Locale.ROOT) }, vm.formats.indexOf(vm.format).coerceAtLeast(0)) { vm.format = vm.formats[it]; vm.save() } }
        row { ToggleRow(Icons.Rounded.People, Dv.Blue, "화자 구분", vm.diarize) { vm.diarize = it; vm.save() } }
        row { ToggleRow(Icons.Rounded.Schedule, Dv.Blue, "시간 표시", vm.includeTime) { vm.includeTime = it; vm.save() } }
    }
    FilledButton("문서로 변환", Icons.Rounded.Description, enabled = vm.sttFile != null && !busy) { vm.startStt() }

    if (sheet) SettingsSheet({ sheet = false }) {
        SheetTitle("인식 설정", "정확도 '정확'은 모델이 커서 처음 내려받는 데 시간이 걸리고 인식도 더 오래 걸려요. 줄바꿈 간격은 발언 사이가 이 시간보다 길면 줄을 바꾸는 기준이에요.")
        Group {
            row { SegmentedRow(listOf("자동", "한국어", "영어"), listOf("", "ko", "en").indexOf(vm.language).coerceAtLeast(0)) { vm.language = listOf("", "ko", "en")[it]; vm.save() } }
            row { SegmentedRow(WhisperSize.values().map { it.label }, vm.size.ordinal) { vm.size = WhisperSize.values()[it]; vm.save() } }
            if (vm.diarize) row { ToggleRow(Icons.Rounded.RecordVoiceOver, Dv.Blue, "화자 표시", vm.showSpeaker) { vm.showSpeaker = it; vm.save() } }
            row { SliderRow(Icons.Rounded.Subject, Dv.Blue, String.format(Locale.US, "%.1f초", vm.gap), vm.gap, 0.5f..4f, 6) { vm.gap = Math.round(it * 10) / 10f; vm.save() } }
        }
    }
}

// ---- 진행 / 결과 ----------------------------------------------------------------------------

@Composable
private fun JobPanel(vm: DocVoiceViewModel) {
    val ctx = LocalContext.current
    when (val s = vm.job.collectAsState().value) {
        JobState.Idle -> {}
        is JobState.Running -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dv.Card).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.stage, fontSize = 15.sp, color = Dv.Label, modifier = Modifier.weight(1f))
                RoundIcon(Icons.Rounded.Close, Dv.Secondary, 20.dp) { vm.cancel() }
            }
            if (s.fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Dv.Blue, trackColor = Dv.Fill)
            else LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Dv.Blue, trackColor = Dv.Fill)
        }
        is JobState.Done -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dv.Card).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        ) {
            if (s.files.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Dv.Blue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(s.message, fontSize = 15.sp, color = Dv.Label, modifier = Modifier.weight(1f))
                RoundIcon(Icons.Rounded.Close, Dv.Secondary, 20.dp) { vm.dismissResult() }
            }
            s.files.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Dv.Blue, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(f.name, fontSize = 15.sp, color = Dv.Label, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    RoundIcon(if (f.mime.startsWith("audio")) Icons.Rounded.PlayArrow else Icons.Rounded.OpenInNew, Dv.Blue) { openFile(ctx, f) }
                    RoundIcon(Icons.Rounded.IosShare, Dv.Blue) { shareFile(ctx, f) }
                    RoundIcon(Icons.Rounded.Close, Dv.Secondary, 20.dp) { vm.dismissResult() }
                }
            }
            s.note?.let { Text(it, fontSize = 12.sp, color = Dv.Secondary, modifier = Modifier.padding(bottom = 6.dp, end = 8.dp)) }
        }
        is JobState.Failed -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dv.Card).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = Dv.Red, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(s.message, fontSize = 14.sp, color = Dv.Label, modifier = Modifier.weight(1f))
            RoundIcon(Icons.Rounded.Close, Dv.Secondary, 20.dp) { vm.dismissResult() }
        }
    }
}

private fun openFile(ctx: Context, f: OutFile) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(f.uri, f.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "열 수 있는 앱이 없어요. 다운로드/DocVoice 폴더를 확인하세요.", Toast.LENGTH_LONG).show()
    }
}

private fun shareFile(ctx: Context, f: OutFile) {
    val send = Intent(Intent.ACTION_SEND).setType(f.mime).putExtra(Intent.EXTRA_STREAM, f.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun humanSize(b: Long): String = when {
    b >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    b >= 1 shl 10 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}
