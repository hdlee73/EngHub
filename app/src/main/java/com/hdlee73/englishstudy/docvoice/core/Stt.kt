package com.hdlee73.englishstudy.docvoice.core

import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

data class SttOptions(
    val language: String = "",          // "" = 자동 감지, "ko", "en"
    val size: WhisperSize = WhisperSize.SMALL,
    val diarize: Boolean = true,
)

class SttResult(
    val segments: List<Segment>,
    val turns: List<Triple<Double, Double, Int>>,
    val diarizationNote: String? = null,
)

/** 온디바이스 받아쓰기 (Silero VAD → Whisper) + 화자 구분 (pyannote 분할 + 3D-Speaker 임베딩). */
object Stt {
    /**
     * 화자 구분은 전체 음성을 한 번에 메모리에 올려야 한다(sherpa 의 `OfflineSpeakerDiarization.process` 는
     * 조각 처리를 지원하지 않는다). 16 kHz 모노 FloatArray 는 초당 약 64 KB 이므로 이 상한이 복사본의 크기를
     * 묶어 준다(50분 ≈ 192 MB). 이보다 긴 녹음은 메모리를 지키기 위해 화자 정보 없이 저장한다.
     */
    const val MAX_DIARIZE_SECONDS = 50 * 60

    /** Audio added on each side of a detected speech piece before it is recognized (0.2 s at 16 kHz). */
    private const val PAD_SAMPLES = 3200

    /**
     * 화자 구분은 전체 음성을 FloatArray 하나로 복사한다(샘플당 4바이트). 앱의 largeHeap 플래그를 뺐으므로
     * 긴 녹음이 기본 힙을 넘길 수 있다. 복사본이 최대 힙의 절반 안에 들어갈 때만 화자 구분을 실행한다.
     */
    private fun fitsInHeap(samples: Int): Boolean =
        samples.toLong() * 4L <= Runtime.getRuntime().maxMemory() / 2

    fun transcribe(
        store: ModelStore,
        pcm: PcmSource,
        opts: SttOptions,
        isActive: () -> Boolean,
        onStage: (String, Float) -> Unit,
    ): SttResult {
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val sr = AudioDecoder.SAMPLE_RATE

        // 1) 모델 준비
        store.ensureVad(isActive) { m, f -> onStage(m, f) }
        var opts = opts
        try {
            store.ensureWhisper(opts.size, isActive) { m, f -> onStage(m, f) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The English-only model could not be downloaded: fall back to the multilingual one.
            if (!opts.size.englishOnly) throw e
            opts = opts.copy(size = WhisperSize.SMALL)
            store.ensureWhisper(opts.size, isActive) { m, f -> onStage(m, f) }
        }
        if (opts.diarize) store.ensureDiar(isActive) { m, f -> onStage(m, f) }

        // 2) 받아쓰기
        val recognizer = newRecognizer(store, opts, threads)
        val vad = newVad(store)
        val segments = ArrayList<Segment>()
        try {
            val total = pcm.size
            fun drain() {
                while (!vad.empty()) {
                    val seg = vad.front()
                    vad.pop()
                    // The detector tends to clip the first and last word: give the recognizer a little audio on both sides,
                    // but report the detected times.
                    val from = maxOf(0, seg.start - PAD_SAMPLES)
                    val to = minOf(total, seg.start + seg.samples.size + PAD_SAMPLES)
                    val padded = runCatching { pcm.toFloats(from, to) }.getOrDefault(seg.samples)
                    decodeSegment(recognizer, padded, from, reportStart = seg.start, reportSamples = seg.samples.size)?.let { segments.add(it) }
                }
            }
            val window = 1600
            var pos = 0
            while (pos < total) {
                if (!isActive()) throw kotlinx.coroutines.CancellationException("취소됨")
                val end = minOf(total, pos + window)
                vad.acceptWaveform(pcm.toFloats(pos, end))
                pos = end
                if (!vad.empty()) {
                    drain()
                    onStage("받아쓰는 중", pos.toFloat() / total)
                }
            }
            vad.flush()
            drain()
            onStage("받아쓰는 중", 1f)
        } finally {
            vad.release()
            recognizer.release()
        }

        // 3) 화자 구분 (선택)
        var turns: List<Triple<Double, Double, Int>> = emptyList()
        var note: String? = null
        if (opts.diarize && segments.isNotEmpty()) {
            if (pcm.seconds > MAX_DIARIZE_SECONDS) {
                note = "녹음이 ${MAX_DIARIZE_SECONDS / 60}분보다 길어 화자 구분은 건너뛰었습니다."
            } else if (!fitsInHeap(pcm.size)) {
                note = "메모리가 부족해 화자 구분은 건너뛰었습니다."
            } else {
                try {
                    onStage("화자 구분 중", 0f)
                    turns = diarize(store, pcm, threads)
                    onStage("화자 구분 중", 1f)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    note = "화자 구분에 실패해 화자 정보 없이 저장합니다. (${e.message})"
                }
            }
        }
        return SttResult(segments, turns, note)
    }

    fun newRecognizer(store: ModelStore, opts: SttOptions, threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)): OfflineRecognizer {
        val wf = store.whisper(opts.size)
        return OfflineRecognizer(
            config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = wf.encoder.path,
                        decoder = wf.decoder.path,
                        // English-only models take no language.
                        language = if (opts.size.englishOnly) "" else opts.language,
                        task = "transcribe",
                        tailPaddings = 1000,
                    ),
                    tokens = wf.tokens.path,
                    numThreads = threads,
                    provider = "cpu",
                ),
            )
        )
    }

    /** 실시간(스트리밍) 인식기. 0.8초 이상 조용하면 한 문장으로 끊는다. */
    fun newLive(store: ModelStore, lang: LiveLang): OnlineRecognizer {
        val f = store.live(lang)
        return OnlineRecognizer(
            config = OnlineRecognizerConfig(
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(encoder = f.encoder.path, decoder = f.decoder.path, joiner = f.joiner.path),
                    tokens = f.tokens.path,
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                    provider = "cpu",
                ),
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 2.0f, 0.0f),
                    rule2 = EndpointRule(true, 0.8f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 15.0f),
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search",
            )
        )
    }

    fun newVad(store: ModelStore): Vad = Vad(
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = store.vadFile.path,
                threshold = 0.45f,
                // Longer pauses only: shorter pieces gave the recognizer too little context and cut sentences in two.
                minSilenceDuration = 0.65f,
                minSpeechDuration = 0.25f,
                windowSize = 512,
                maxSpeechDuration = 28f,
            ),
            sampleRate = AudioDecoder.SAMPLE_RATE,
            numThreads = 1,
            provider = "cpu",
        )
    )

    /**
     * VAD 가 잘라낸 한 구간을 Whisper 로 인식. startSample 은 16kHz 기준 샘플 위치.
     * [reportStart] / [reportSamples] 가 주어지면 samples 에 여유 구간이 붙어 있어도 그 시간으로 기록한다.
     */
    fun decodeSegment(
        recognizer: OfflineRecognizer, samples: FloatArray, startSample: Int,
        reportStart: Int = startSample, reportSamples: Int = samples.size,
    ): Segment? {
        val sr = AudioDecoder.SAMPLE_RATE
        val start = reportStart / sr.toDouble()
        val dur = reportSamples / sr.toDouble()
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, sr)
            recognizer.decode(stream)
            val text = Segmenter.cleanAsr(recognizer.getResult(stream).text, dur) ?: return null
            if (Segmenter.englishWordCount(text) + (if (Segmenter.hasHangul(text)) 1 else 0) == 0) return null
            return Segment(start, start + dur, text)
        } finally {
            stream.release()
        }
    }

    fun diarize(store: ModelStore, pcm: PcmSource, threads: Int): List<Triple<Double, Double, Int>> {
        val sd = OfflineSpeakerDiarization(
            config = OfflineSpeakerDiarizationConfig(
                segmentation = OfflineSpeakerSegmentationModelConfig(
                    pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = store.segFile.path),
                    numThreads = threads,
                    provider = "cpu",
                ),
                embedding = SpeakerEmbeddingExtractorConfig(model = store.embFile.path, numThreads = threads, provider = "cpu"),
                clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f),
                minDurationOn = 0.3f,
                minDurationOff = 0.5f,
            )
        )
        try {
            // The sherpa API needs the whole waveform as a single FloatArray, so it cannot be streamed: the copy is
            // bounded by MAX_DIARIZE_SECONDS (callers skip diarization for longer recordings).
            val result = sd.process(pcm.toFloats())
            return result.map { Triple(it.start.toDouble(), it.end.toDouble(), it.speaker) }
        } finally {
            sd.release()
        }
    }

    /** 받아쓰기 결과 → 문장 → (화자 매핑) → 문단 */
    fun toDocument(r: SttResult, gap: Double): Pair<List<Sentence>, List<Paragraph>> {
        val sentences = Segmenter.buildSentences(r.segments)
        if (r.turns.isNotEmpty()) Segmenter.assignSpeakers(sentences, r.turns)
        val paragraphs = Segmenter.groupParagraphs(sentences, gap = gap, useSpeaker = r.turns.isNotEmpty())
        return sentences to paragraphs
    }
}
