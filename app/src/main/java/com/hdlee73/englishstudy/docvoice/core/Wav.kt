package com.hdlee73.englishstudy.docvoice.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16kHz 모노 16bit PCM → WAV 바이트. */
object Wav {
    fun encode(pcm: ShortArray, size: Int, sampleRate: Int = 16000): ByteArray {
        val dataLen = size * 2
        val b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        b.putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(dataLen)
        for (i in 0 until size) b.putShort(pcm[i])
        return b.array()
    }
}
