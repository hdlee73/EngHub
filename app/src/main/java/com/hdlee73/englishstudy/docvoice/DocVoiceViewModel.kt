package com.hdlee73.englishstudy.docvoice

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hdlee73.englishstudy.docvoice.core.Wav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hdlee73.englishstudy.docvoice.core.Exporter
import com.hdlee73.englishstudy.docvoice.core.SaveFolder
import com.hdlee73.englishstudy.docvoice.core.Storage
import com.hdlee73.englishstudy.docvoice.core.SttOptions
import com.hdlee73.englishstudy.docvoice.core.TtsVoices
import com.hdlee73.englishstudy.docvoice.core.WhisperSize

class DocVoiceViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("docvoice", 0)

    val job = JobHub.state

    var tab by mutableIntStateOf(0) // 0 문서→MP3, 1 MP3→문서, 2 녹음 & 문서화, 3 유튜브→MP3

    // 문서 → 음성
    var ttsFile by mutableStateOf<Storage.Picked?>(null)
    var koVoice by mutableStateOf(prefs.getString("koVoice", TtsVoices.DEFAULT_KO)!!)
    var accent by mutableStateOf(prefs.getString("accent", "us")!!)
    var enVoiceUs by mutableStateOf(prefs.getString("enVoiceUs", TtsVoices.EN_US.values.first())!!)
    var enVoiceUk by mutableStateOf(prefs.getString("enVoiceUk", TtsVoices.EN_UK.values.first())!!)
    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1.0f))

    /** 저장할 파일 이름(확장자 제외). 비우면 문서/영상 이름으로 자동. */
    var ttsName by mutableStateOf("")
    var ytName by mutableStateOf("")
    var sttName by mutableStateOf("")

    // 유튜브 → MP3
    var ytUrl by mutableStateOf("")

    /** 다른 앱에서 공유된 유튜브 주소를 받아 유튜브 탭에 채운다. */
    fun openYoutube(url: String) { ytUrl = url; tab = 3 }

    // 음성 → 문서
    var sttFile by mutableStateOf<Storage.Picked?>(null)
    var format by mutableStateOf(prefs.getString("format", "xlsx")!!)
    var language by mutableStateOf(prefs.getString("language", "")!!)
    var size by mutableStateOf(runCatching { WhisperSize.valueOf(prefs.getString("size", "SMALL")!!) }.getOrDefault(WhisperSize.SMALL))
    var diarize by mutableStateOf(prefs.getBoolean("diarize", true))
    var includeTime by mutableStateOf(prefs.getBoolean("includeTime", true))
    var showSpeaker by mutableStateOf(prefs.getBoolean("showSpeaker", true))
    var gap by mutableFloatStateOf(prefs.getFloat("gap", 1.5f))

    val enVoice get() = if (accent == "uk") enVoiceUk else enVoiceUs

    fun pickDoc(uri: Uri) { ttsFile = Storage.describe(app, uri); ttsName = "" }
    fun pickAudio(uri: Uri) { sttFile = Storage.describe(app, uri); sttName = "" }

    fun save() {
        prefs.edit()
            .putString("koVoice", koVoice).putString("accent", accent).putString("recLang", recLang).putBoolean("recBt", recBluetooth)
            .putString("enVoiceUs", enVoiceUs).putString("enVoiceUk", enVoiceUk)
            .putFloat("speed", speed)
            .putString("format", format).putString("language", language).putString("size", size.name)
            .putBoolean("diarize", diarize).putBoolean("includeTime", includeTime)
            .putBoolean("showSpeaker", showSpeaker).putFloat("gap", gap)
            .apply()
    }

    private fun launch(req: JobRequest) {
        if (JobHub.isRunning()) return
        save()
        JobHub.pending = req
        JobHub.state.value = JobState.Running("준비 중", "시작하는 중", null)
        ContextCompat.startForegroundService(app, Intent(app, JobService::class.java))
    }

    fun liveReady(lang: String) = com.hdlee73.englishstudy.docvoice.core.ModelStore(app).liveReady(com.hdlee73.englishstudy.docvoice.core.LiveLang.of(lang))

    fun downloadModel() {
        launch(JobRequest.ModelDownload(com.hdlee73.englishstudy.docvoice.core.LiveLang.of(recLang)))
    }

    fun startTts() {
        val f = ttsFile ?: return
        launch(JobRequest.Tts(f, koVoice, enVoice, speed.toDouble(), Storage.cleanName(ttsName, "mp3")))
    }

    fun startYt() {
        val url = com.hdlee73.englishstudy.docvoice.core.YouTubeAudio.findUrl(ytUrl) ?: run {
            android.widget.Toast.makeText(app, "유튜브 주소를 넣어 주세요.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        launch(JobRequest.Yt(url, Storage.cleanName(ytName, "mp3")))
    }

    fun startStt() {
        val f = sttFile ?: return
        launch(
            JobRequest.Stt(
                f, format, SttOptions(language, size, diarize), gap.toDouble(),
                includeTime, showSpeaker && diarize, Storage.cleanName(sttName, format),
            )
        )
    }

    fun cancel() {
        app.startService(Intent(app, JobService::class.java).setAction(JobService.ACTION_CANCEL))
    }

    fun dismissResult() {
        if (!JobHub.isRunning()) JobHub.state.value = JobState.Idle
    }

    // ---- 녹음 ----
    val rec = RecorderHub.state
    var recFormat by mutableStateOf(prefs.getString("recFormat", "docx")!!)
    var recDiarize by mutableStateOf(false)
    var recRefine by mutableStateOf(false)
    var recLang by mutableStateOf(prefs.getString("recLang", "en")!!)
    var recBluetooth by mutableStateOf(prefs.getBoolean("recBt", true))

    fun startRecording() {
        if (RecorderHub.isActive()) return
        RecorderHub.reset()
        RecorderHub.language = recLang
        RecorderHub.bluetooth = recBluetooth
        save()
        ContextCompat.startForegroundService(app, Intent(app, RecorderService::class.java).setAction(RecorderService.ACTION_START))
    }

    fun togglePause() {
        app.startService(Intent(app, RecorderService::class.java).setAction(RecorderService.ACTION_PAUSE))
    }

    fun stopRecording() {
        app.startService(Intent(app, RecorderService::class.java).setAction(RecorderService.ACTION_STOP))
    }

    /** 저장하지 않고 닫기 */
    fun newRecording() {
        if (!RecorderHub.isActive()) RecorderHub.reset()
    }

    /** 지금 녹음을 버리고 바로 다시 녹음 */
    fun restartRecording() {
        if (RecorderHub.isActive()) return
        startRecording()
    }

    /** 녹음 원본을 WAV 로 저장 (원할 때만) */
    fun saveRecordingFile() {
        val pcm = RecorderHub.pcm ?: return
        val name = (RecorderHub.state.value.title.ifBlank { "녹음" }) + ".wav"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Storage.saveToDownloads(app, name, Wav.encode(pcm.data, pcm.size))
                RecorderHub.upd { copy(wav = OutFile(name, uri, "audio/wav")) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { android.widget.Toast.makeText(app, "저장하지 못했어요.", android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun exportRecording() {
        val title = RecorderHub.state.value.title.ifBlank { "녹음" }
        prefs.edit().putString("recFormat", recFormat).putString("recLang", recLang).apply()
        launch(JobRequest.RecExport(title, recFormat, recRefine, if (recLang == "mix") "" else recLang, size, recDiarize, gap.toDouble(), includeTime, showSpeaker && recDiarize))
    }

    /** The folder files are saved to (kept in step with the folder picker). */
    val saveFolder = kotlinx.coroutines.flow.MutableStateFlow(SaveFolder.label(app))

    fun setSaveFolder(tree: Uri?) {
        SaveFolder.set(app, tree)
        saveFolder.value = SaveFolder.label(app)
    }

    val formats get() = Exporter.FORMATS
}
