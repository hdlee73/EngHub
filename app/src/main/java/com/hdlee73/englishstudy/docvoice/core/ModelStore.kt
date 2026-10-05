package com.hdlee73.englishstudy.docvoice.core

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 받아쓰기 정확도 선택 (Whisper int8). */
enum class WhisperSize(val key: String, val label: String, val hint: String) {
    BASE("base", "빠름", "base · 약 150MB"),
    SMALL("small", "정확 (권장)", "small · 약 370MB");
}

/** 실시간(스트리밍) 인식 언어 */
enum class LiveLang(val key: String, val label: String, val archive: String, val mb: Int) {
    EN("en", "English", "sherpa-onnx-streaming-zipformer-en-2023-06-26", 300),
    KO("ko", "한국어", "sherpa-onnx-streaming-zipformer-korean-2024-06-16", 400),
    MIX("mix", "한·영", "", 1100);

    /** 실제로 필요한 단일 언어 모델들 */
    val members: List<LiveLang> get() = if (this == MIX) listOf(EN, KO) else listOf(this)

    companion object {
        fun of(key: String) = values().firstOrNull { it.key == key } ?: EN
    }
}

class ModelException(message: String) : Exception(message)

/** 음성 인식 · VAD · 화자 구분 모델을 앱 저장공간에 내려받아 보관한다 (최초 1회, 이후 오프라인 사용). */
class ModelStore(context: Context) {
    private val root = File(context.filesDir, "models").apply { mkdirs() }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val HF = "https://huggingface.co/csukuangfj"
        private const val GH = "https://github.com/k2-fsa/sherpa-onnx/releases/download"
        const val VAD_URL = "$GH/asr-models/silero_vad.onnx"
        const val SEG_URL = "$GH/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2"
        const val EMB_URL = "$GH/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
    }

    class WhisperFiles(val encoder: File, val decoder: File, val tokens: File)

    fun whisper(size: WhisperSize): WhisperFiles {
        val d = File(root, "whisper-${size.key}")
        return WhisperFiles(File(d, "encoder.onnx"), File(d, "decoder.onnx"), File(d, "tokens.txt"))
    }

    class LiveFiles(val encoder: File, val decoder: File, val joiner: File, val tokens: File)

    fun live(l: LiveLang): LiveFiles {
        val d = File(root, "live-${l.key}")
        return LiveFiles(File(d, "encoder.onnx"), File(d, "decoder.onnx"), File(d, "joiner.onnx"), File(d, "tokens.txt"))
    }

    fun liveReady(l: LiveLang): Boolean = if (l == LiveLang.MIX) (l.members.all { liveReady(it) } && whisperReady(WhisperSize.SMALL)) else live(l).let { it.encoder.exists() && it.decoder.exists() && it.joiner.exists() && it.tokens.exists() }

    /** 스트리밍 모델 압축 파일에서 인코더/디코더/조이너/토큰만 골라 꺼낸다 (int8 우선). */
    fun ensureLive(l: LiveLang, isActive: () -> Boolean, onProgress: (String, Float) -> Unit) {
        if (l == LiveLang.MIX) {
            val ms = l.members
            ms.forEachIndexed { i, m -> ensureLive(m, isActive) { s, f -> onProgress("${m.label} · $s", (i + f) / ms.size * 0.65f) } }
            ensureWhisper(WhisperSize.SMALL, isActive) { s, f -> onProgress(s, 0.65f + f * 0.35f) }
            return
        }
        if (liveReady(l)) return
        val f = live(l)
        val dir = f.encoder.parentFile!!
        dir.mkdirs()
        val tmp = File(root, "live-${l.key}.tar.bz2")
        download("$GH/asr-models/${l.archive}.tar.bz2", tmp, isActive) { onProgress("실시간 인식 모델 내려받는 중", it * 0.9f) }
        onProgress("실시간 인식 모델 준비 중", 0.92f)
        val best = HashMap<String, Pair<Int, File>>()
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(tmp.inputStream()))).use { tar ->
            var e = tar.nextTarEntry
            while (e != null) {
                if (!e.isDirectory && !e.name.contains("test_wavs")) {
                    val base = e.name.substringAfterLast('/')
                    val int8 = base.contains("int8")
                    val (role, score) = when {
                        base == "tokens.txt" -> "tokens" to 1
                        !base.endsWith(".onnx") -> (null to 0)
                        base.startsWith("encoder") -> "encoder" to (if (int8) 2 else 1)
                        base.startsWith("joiner") -> "joiner" to (if (int8) 2 else 1)
                        base.startsWith("decoder") -> "decoder" to (if (int8) 1 else 2)
                        else -> (null to 0)
                    }
                    if (role != null && (best[role]?.first ?: 0) < score) {
                        val out = File(dir, "$role.cand")
                        out.outputStream().use { tar.copyTo(it) }
                        val keep = File(dir, "$role.best")
                        best[role]?.second?.delete()
                        out.renameTo(keep)
                        best[role] = score to keep
                    }
                }
                e = tar.nextTarEntry
            }
        }
        tmp.delete()
        val map = mapOf("encoder" to f.encoder, "decoder" to f.decoder, "joiner" to f.joiner, "tokens" to f.tokens)
        for ((role, dest) in map) {
            val src = best[role]?.second ?: throw ModelException("실시간 인식 모델에서 $role 파일을 찾지 못했습니다.")
            if (!src.renameTo(dest)) throw ModelException("모델 파일을 저장하지 못했습니다.")
        }
        onProgress("실시간 인식 모델 준비 중", 1f)
    }

    val vadFile get() = File(root, "silero_vad.onnx")
    val segFile get() = File(root, "diar/segmentation.onnx")
    val embFile get() = File(root, "diar/embedding.onnx")

    fun whisperReady(size: WhisperSize) = whisper(size).let { it.encoder.exists() && it.decoder.exists() && it.tokens.exists() }
    fun vadReady() = vadFile.exists()
    fun diarReady() = segFile.exists() && embFile.exists()

    /** 이미 있으면 즉시 반환, 없으면 내려받는다. onProgress(설명, 0..1) */
    fun ensureWhisper(size: WhisperSize, isActive: () -> Boolean, onProgress: (String, Float) -> Unit) {
        if (whisperReady(size)) return
        val f = whisper(size)
        val repo = "$HF/sherpa-onnx-whisper-${size.key}/resolve/main/${size.key}"
        download("$repo-encoder.int8.onnx", f.encoder, isActive) { onProgress("음성 인식 모델 내려받는 중 (1/3)", it * 0.45f) }
        download("$repo-decoder.int8.onnx", f.decoder, isActive) { onProgress("음성 인식 모델 내려받는 중 (2/3)", 0.45f + it * 0.5f) }
        download("$repo-tokens.txt", f.tokens, isActive) { onProgress("음성 인식 모델 내려받는 중 (3/3)", 0.95f + it * 0.05f) }
    }

    fun ensureVad(isActive: () -> Boolean, onProgress: (String, Float) -> Unit) {
        if (vadReady()) return
        download(VAD_URL, vadFile, isActive) { onProgress("음성 구간 검출 모델 내려받는 중", it) }
    }

    fun ensureDiar(isActive: () -> Boolean, onProgress: (String, Float) -> Unit) {
        if (diarReady()) return
        if (!segFile.exists()) {
            val tmp = File(root, "seg.tar.bz2")
            download(SEG_URL, tmp, isActive) { onProgress("화자 구분 모델 내려받는 중 (1/2)", it * 0.3f) }
            extractModel(tmp, segFile)
            tmp.delete()
        }
        if (!embFile.exists()) {
            download(EMB_URL, embFile, isActive) { onProgress("화자 구분 모델 내려받는 중 (2/2)", 0.3f + it * 0.7f) }
        }
    }

    private fun extractModel(archive: File, dest: File) {
        dest.parentFile?.mkdirs()
        var found = false
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(archive.inputStream()))).use { tar ->
            var e = tar.nextTarEntry
            while (e != null) {
                val n = e.name
                if (!e.isDirectory && (n.endsWith("/model.int8.onnx") || n == "model.int8.onnx")) {
                    val part = File(dest.path + ".part")
                    part.outputStream().use { tar.copyTo(it) }
                    if (!part.renameTo(dest)) throw ModelException("모델 파일을 저장하지 못했습니다.")
                    found = true
                    break
                }
                e = tar.nextTarEntry
            }
        }
        if (!found) throw ModelException("화자 구분 모델 압축 파일에서 모델을 찾지 못했습니다.")
    }

    private fun download(url: String, dest: File, isActive: () -> Boolean, onFraction: (Float) -> Unit) {
        if (dest.exists() && dest.length() > 0) { onFraction(1f); return }
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        try {
            http.newCall(Request.Builder().url(url).header("User-Agent", "DocVoice-Android").build()).execute().use { resp ->
                if (!resp.isSuccessful) throw ModelException("모델 내려받기 실패 (HTTP ${resp.code}). 인터넷 연결을 확인하세요.")
                val body = resp.body ?: throw ModelException("빈 응답입니다.")
                val total = body.contentLength()
                var done = 0L
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (!isActive()) throw kotlinx.coroutines.CancellationException("취소됨")
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onFraction((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (total > 0 && done != total) throw ModelException("내려받기가 중간에 끊겼습니다.")
            }
            if (!part.renameTo(dest)) throw ModelException("모델 파일을 저장하지 못했습니다.")
            onFraction(1f)
        } catch (e: IOException) {
            part.delete()
            throw ModelException("모델 내려받기 실패: ${e.message}")
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }
}
