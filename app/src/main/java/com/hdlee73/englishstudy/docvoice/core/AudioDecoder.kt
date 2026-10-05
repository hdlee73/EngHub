package com.hdlee73.englishstudy.docvoice.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

/** 16kHz 모노 16bit PCM 을 담는 늘어나는 버퍼. */
class PcmBuffer(initial: Int = 1 shl 20) {
    var data = ShortArray(initial)
        private set
    var size = 0
        private set

    fun add(v: Short) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    val seconds: Double get() = size / AudioDecoder.SAMPLE_RATE.toDouble()

    fun toFloats(from: Int = 0, to: Int = size): FloatArray {
        val out = FloatArray(to - from)
        for (i in out.indices) out[i] = data[from + i] / 32768f
        return out
    }
}

/** 선형 보간 스트리밍 리샘플러 (입력 → 16kHz). */
internal class Resampler(private val inRate: Int, private val outRate: Int = AudioDecoder.SAMPLE_RATE) {
    private val step = inRate.toDouble() / outRate
    private var nextPos = 0.0
    private var consumed = 0L
    private var carry: Short = 0
    private var hasCarry = false

    fun push(m: ShortArray, n: Int, out: (Short) -> Unit) {
        if (n <= 0) return
        if (inRate == outRate) {
            for (i in 0 until n) out(m[i])
            return
        }
        val extra = if (hasCarry) 1 else 0
        val startAbs = consumed - extra
        fun at(idx: Int): Short = if (hasCarry) (if (idx == 0) carry else m[idx - 1]) else m[idx]
        val len = n + extra
        while (true) {
            val p = nextPos
            val i = Math.floor(p).toLong() - startAbs
            if (i + 1 >= len) break
            val frac = p - Math.floor(p)
            val a = at(i.toInt()).toInt()
            val b = at(i.toInt() + 1).toInt()
            out((a + (b - a) * frac).toInt().toShort())
            nextPos += step
        }
        carry = m[n - 1]
        hasCarry = true
        consumed += n
    }
}

object AudioDecoder {
    const val SAMPLE_RATE = 16000

    class DecodeException(message: String) : Exception(message)

    /**
     * 오디오/동영상 파일을 16kHz 모노 PCM 으로 디코딩한다.
     * @param onProgress 0f..1f (길이를 알 수 있을 때)
     * @param isActive 취소 여부 확인
     */
    fun decode(
        context: Context,
        uri: Uri,
        isActive: () -> Boolean = { true },
        onProgress: (Float) -> Unit = {},
    ): PcmBuffer {
        val extractor = MediaExtractor()
        try {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: Exception) {
                throw DecodeException("파일을 열 수 없습니다. (${e.message})")
            }
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i; format = f; break
                }
            }
            if (track < 0 || format == null) throw DecodeException("오디오 트랙을 찾을 수 없습니다.")
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            val codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                throw DecodeException("지원하지 않는 오디오 형식입니다. ($mime)")
            }
            val pcm = PcmBuffer()
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
                var rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else SAMPLE_RATE
                var resampler = Resampler(rate)
                var mono = ShortArray(8192)
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!isActive()) throw kotlinx.coroutines.CancellationException("취소됨")
                    if (!inputDone) {
                        val idx = codec.dequeueInputBuffer(10_000)
                        if (idx >= 0) {
                            val buf = codec.getInputBuffer(idx)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0)
                                if (durationUs > 0) onProgress((extractor.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f))
                                extractor.advance()
                            }
                        }
                    }
                    val out = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        out >= 0 -> {
                            if (info.size > 0) {
                                val ob = codec.getOutputBuffer(out)!!
                                ob.position(info.offset)
                                ob.limit(info.offset + info.size)
                                val shorts = ob.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                                val frames = shorts.remaining() / channels.coerceAtLeast(1)
                                if (mono.size < frames) mono = ShortArray(frames)
                                val ch = channels.coerceAtLeast(1)
                                for (f in 0 until frames) {
                                    var sum = 0
                                    for (c in 0 until ch) sum += shorts.get(f * ch + c).toInt()
                                    mono[f] = (sum / ch).toShort()
                                }
                                resampler.push(mono, frames) { pcm.add(it) }
                            }
                            codec.releaseOutputBuffer(out, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                        out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val nf = codec.outputFormat
                            val nr = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (nr != rate) { rate = nr; resampler = Resampler(rate) }
                        }
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                codec.release()
            }
            if (pcm.size == 0) throw DecodeException("오디오에서 소리를 읽지 못했습니다.")
            onProgress(1f)
            return pcm
        } finally {
            extractor.release()
        }
    }
}
