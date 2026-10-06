package com.hdlee73.englishstudy.docvoice.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.github.axet.lamejni.Lame
import java.io.File
import java.io.OutputStream

/** 소리 파일(m4a, webm 등)을 원래 음질 그대로 읽어 LAME 으로 mp3 에 쓴다. */
object Mp3Encoder {
    class EncodeException(message: String) : Exception(message)

    fun encode(input: File, out: OutputStream, active: () -> Boolean, progress: (Float) -> Unit) {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw EncodeException("소리를 읽지 못했어요.")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        var lame: Lame? = null
        var channels = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!active()) throw kotlinx.coroutines.CancellationException()
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
                        val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        lame = Lame().also { it.open(channels, rate, 128, 2) }
                    }
                    o >= 0 -> {
                        if (info.size > 0) {
                            if (lame == null) {
                                val f = codec.outputFormat
                                channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
                                lame = Lame().also { it.open(channels, f.getInteger(MediaFormat.KEY_SAMPLE_RATE), 128, 2) }
                            }
                            val bb = codec.getOutputBuffer(o)!!
                            bb.position(info.offset)
                            bb.limit(info.offset + info.size)
                            val shorts = ShortArray(info.size / 2)
                            bb.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer().get(shorts)
                            // 채널이 3개 이상이면 앞의 둘만 쓴다.
                            val srcCh = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val pcm = if (srcCh <= 2) shorts else ShortArray(shorts.size / srcCh * 2).also { d ->
                                for (k in 0 until shorts.size / srcCh) for (c in 0 until 2) d[k * 2 + c] = shorts[k * srcCh + c]
                            }
                            lame!!.encode(pcm, 0, pcm.size).takeIf { it.isNotEmpty() }?.let(out::write)
                            if (durationUs > 0) progress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        codec.releaseOutputBuffer(o, false)
                    }
                }
            }
            lame?.close()?.takeIf { it.isNotEmpty() }?.let(out::write)
            if (lame == null) throw EncodeException("소리가 비어 있어요.")
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            extractor.release()
        }
    }
}
