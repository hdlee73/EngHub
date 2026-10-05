package com.hdlee73.englishstudy.docvoice.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TtsException(message: String) : Exception(message)

object TtsVoices {
    val KO = linkedMapOf(
        "인준 (남성)" to "ko-KR-InJoonNeural",
        "현수 (남성, 다국어)" to "ko-KR-HyunsuMultilingualNeural",
    )
    val EN_US = linkedMapOf(
        "Guy (남성)" to "en-US-GuyNeural",
        "Andrew (남성)" to "en-US-AndrewNeural",
        "Brian (남성)" to "en-US-BrianNeural",
        "Christopher (남성)" to "en-US-ChristopherNeural",
        "Eric (남성)" to "en-US-EricNeural",
    )
    val EN_UK = linkedMapOf(
        "Ryan (남성)" to "en-GB-RyanNeural",
        "Thomas (남성)" to "en-GB-ThomasNeural",
    )
    const val DEFAULT_KO = "ko-KR-InJoonNeural"
}

data class TtsChunk(val text: String, val lang: String) // lang: "ko" | "en"

/** Microsoft Edge 신경망 음성(온라인) WebSocket 클라이언트. 비공식 경로이므로 서비스 정책에 따라 막힐 수 있음. */
class EdgeTts(private val client: OkHttpClient = defaultClient()) {

    companion object {
        private const val BASE = "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
        private const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val CHROMIUM_FULL = "143.0.3650.75"
        private const val WIN_EPOCH = 11644473600L

        @Volatile
        private var clockSkewSeconds = 0L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        internal fun secMsGec(nowMillis: Long = System.currentTimeMillis()): String {
            var t = nowMillis / 1000 + clockSkewSeconds + WIN_EPOCH
            t -= t % 300
            val ticks = t * 10_000_000L
            val digest = MessageDigest.getInstance("SHA-256").digest("$ticks$TOKEN".toByteArray(Charsets.US_ASCII))
            return digest.joinToString("") { String.format(Locale.ROOT, "%02X", it) }
        }

        private fun jsDate(): String {
            val f = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", Locale.ENGLISH)
            f.timeZone = TimeZone.getTimeZone("UTC")
            return f.format(Date()) + " GMT+0000 (Coordinated Universal Time)"
        }

        internal fun removeIncompatible(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s) {
                val c = ch.code
                sb.append(if (c <= 8 || c == 11 || c == 12 || c in 14..31) ' ' else ch)
            }
            return sb.toString()
        }

        internal fun escapeXml(s: String): String =
            s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        internal fun ssml(text: String, voice: String, rate: String): String =
            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                "<voice name='$voice'><prosody pitch='+0Hz' rate='$rate' volume='+0%'>" +
                escapeXml(removeIncompatible(text)) +
                "</prosody></voice></speak>"

        internal fun rateString(speed: Double): String {
            val pct = Math.round((speed - 1.0) * 100).toInt()
            return String.format(Locale.ROOT, "%+d%%", pct)
        }
    }

    private class SkewException(val serverDate: String?) : Exception("서버 시간 불일치")

    /** 텍스트 한 덩어리를 mp3 바이트로 합성한다. */
    suspend fun synthesize(text: String, voice: String, rate: String = "+0%"): ByteArray =
        suspendCancellableCoroutine { cont ->
            val audio = ByteArrayOutputStream()
            var finished = false
            val url = "wss://$BASE/edge/v1?TrustedClientToken=$TOKEN" +
                "&ConnectionId=${UUID.randomUUID().toString().replace("-", "")}" +
                "&Sec-MS-GEC=${secMsGec()}&Sec-MS-GEC-Version=1-$CHROMIUM_FULL"
            val major = CHROMIUM_FULL.substringBefore('.')
            val request = Request.Builder().url(url)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36 Edg/$major.0.0.0",
                )
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "muid=${UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT)};")
                .build()

            fun fail(e: Throwable) {
                if (!finished) {
                    finished = true
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }

            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(
                        "X-Timestamp:${jsDate()}\r\n" +
                            "Content-Type:application/json; charset=utf-8\r\n" +
                            "Path:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
                            "\"sentenceBoundaryEnabled\":\"true\",\"wordBoundaryEnabled\":\"false\"}," +
                            "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
                    )
                    webSocket.send(
                        "X-RequestId:${UUID.randomUUID().toString().replace("-", "")}\r\n" +
                            "Content-Type:application/ssml+xml\r\n" +
                            "X-Timestamp:${jsDate()}Z\r\n" + // Edge 의 알려진 동작: 끝에 Z 를 붙인다
                            "Path:ssml\r\n\r\n" + ssml(text, voice, rate)
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val path = Regex("(?m)^Path:(.*?)\\r?$").find(text)?.groupValues?.get(1)?.trim()
                    if (path == "turn.end" && !finished) {
                        finished = true
                        webSocket.close(1000, null)
                        if (cont.isActive) {
                            val bytes = audio.toByteArray()
                            if (bytes.isEmpty()) cont.resumeWithException(TtsException("오디오가 수신되지 않았습니다"))
                            else cont.resume(bytes)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (bytes.size < 2) return
                    val headerLen = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                    if (headerLen + 2 > bytes.size) return
                    val headers = bytes.substring(2, 2 + headerLen).utf8()
                    if (!headers.contains("Path:audio")) return
                    val data = bytes.substring(2 + headerLen)
                    if (data.size > 0) audio.write(data.toByteArray())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (response?.code == 403) fail(SkewException(response.header("Date")))
                    else fail(TtsException("음성 서버 연결 실패: ${t.message ?: t.javaClass.simpleName}"))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    fail(TtsException("음성 서버 연결이 예기치 않게 닫혔습니다"))
                }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }

    /** 재시도 + 시계 보정 + (실패 시) 대체 음성. */
    suspend fun synthesizeWithRetry(text: String, voice: String, rate: String, fallbackVoice: String? = null): ByteArray {
        var last: Throwable? = null
        for (v in listOfNotNull(voice, fallbackVoice).distinct()) {
            repeat(3) { attempt ->
                try {
                    return synthesize(text, v, rate)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SkewException) {
                    last = e
                    e.serverDate?.let { d -> adjustSkew(d) }
                    delay(500L * (attempt + 1))
                } catch (e: Exception) {
                    last = e
                    delay(1500L * (attempt + 1))
                }
            }
        }
        throw TtsException("음성 합성에 실패했습니다. 인터넷 연결을 확인하세요. (${last?.message})")
    }

    private fun adjustSkew(serverDate: String) {
        try {
            val f = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
            val server = f.parse(serverDate)?.time ?: return
            clockSkewSeconds += (server - System.currentTimeMillis()) / 1000
        } catch (_: Exception) {
        }
    }
}

object TtsPlanner {
    const val MAX_BYTES = 3000

    private fun escapedBytes(s: String) = EdgeTts.escapeXml(s).toByteArray(Charsets.UTF_8).size

    /** 너무 긴 문장은 공백/쉼표 기준으로 잘라 MAX_BYTES 이하 조각으로 만든다. */
    internal fun splitLong(sent: String, maxBytes: Int): List<String> {
        if (escapedBytes(sent) <= maxBytes) return listOf(sent)
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (word in sent.split(Regex("(?<=[\\s,;，、])"))) {
            if (escapedBytes(cur.toString() + word) > maxBytes && cur.isNotEmpty()) {
                out.add(cur.toString().trim())
                cur = StringBuilder()
            }
            var w = word
            while (escapedBytes(w) > maxBytes) { // 공백이 전혀 없는 아주 긴 덩어리
                var cut = w.length / 2
                while (cut > 1 && escapedBytes(w.substring(0, cut)) > maxBytes) cut /= 2
                out.add(w.substring(0, cut))
                w = w.substring(cut)
            }
            cur.append(w)
        }
        if (cur.isNotBlank()) out.add(cur.toString().trim())
        return out
    }

    /** 문장 단위로 언어를 판별해 같은 언어끼리 묶는다. */
    fun buildChunks(text: String, maxBytes: Int = MAX_BYTES): List<TtsChunk> {
        val chunks = ArrayList<TtsChunk>()
        var curLang: String? = null
        val buf = StringBuilder()
        var size = 0

        fun flush() {
            if (buf.isNotBlank()) chunks.add(TtsChunk(buf.toString().trim(), curLang ?: "ko"))
            buf.setLength(0)
            size = 0
        }

        for (line in text.split("\n")) {
            val l = line.trim()
            if (l.isEmpty()) continue
            val spans = Segmenter.splitSpans(l)
            for ((k, r) in spans.withIndex()) {
                val whole = l.substring(r.first, r.last + 1)
                var lang = if (Segmenter.isKorean(whole)) "ko" else "en"
                if (whole.none { it.isLetter() } && curLang != null) lang = curLang!!
                val last = k == spans.size - 1
                for (sent in splitLong(whole, maxBytes)) {
                    val len = escapedBytes(sent) + 1
                    if (lang != curLang || (size + len > maxBytes && buf.isNotEmpty())) {
                        flush()
                        curLang = lang
                    }
                    buf.append(sent).append(if (last) "\n" else " ")
                    size += len
                }
            }
        }
        flush()
        return chunks.filter { it.text.isNotBlank() }
    }
}

object TtsEngine {
    /** 모든 조각을 (동시 3개) 합성해 하나의 mp3 바이트로 이어 붙인다. */
    suspend fun synthesizeAll(
        chunks: List<TtsChunk>,
        koVoice: String,
        enVoice: String,
        speed: Double,
        edge: EdgeTts = EdgeTts(),
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ByteArray = coroutineScope {
        val rate = EdgeTts.rateString(speed)
        val sem = Semaphore(3)
        val done = AtomicInteger(0)
        val parts = chunks.map { ch ->
            async {
                sem.withPermit {
                    val voice = if (ch.lang == "ko") koVoice else enVoice
                    val fb = if (ch.lang == "ko") TtsVoices.DEFAULT_KO else null
                    val bytes = edge.synthesizeWithRetry(ch.text, voice, rate, fb)
                    onProgress(done.incrementAndGet(), chunks.size)
                    bytes
                }
            }
        }.awaitAll()
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        out.toByteArray()
    }
}
