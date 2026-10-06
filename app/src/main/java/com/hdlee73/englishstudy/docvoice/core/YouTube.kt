package com.hdlee73.englishstudy.docvoice.core

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.util.concurrent.TimeUnit

class YouTubeException(message: String) : Exception(message)

/** NewPipeExtractor 가 쓰는 HTTP 클라이언트 (OkHttp). */
private object YtHttp : Downloader() {
    const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody()
            method == "POST" || method == "PUT" -> ByteArray(0).toRequestBody()
            else -> null
        }
        val b = okhttp3.Request.Builder().method(method, body).url(request.url()).header("User-Agent", UA)
        request.headers().forEach { (name, values) ->
            b.removeHeader(name)
            values.forEach { b.addHeader(name, it) }
        }
        client.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("YouTube 가 잠시 요청을 막았어요.", request.url())
            return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }
}

/** 유튜브 영상의 소리를 내려받는다 (변환은 [Mp3Encoder]). 개인적으로 보관하는 용도로만 쓰세요. */
object YouTubeAudio {
    class Result(val title: String, val file: File)

    private val URL_RE = Regex("""https?://(?:www\.|m\.|music\.)?(?:youtube\.com|youtu\.be)/[^\s]+""", RegexOption.IGNORE_CASE)

    /** 공유된 글이나 붙여넣은 글에서 유튜브 주소만 뽑는다. */
    fun findUrl(text: String?): String? = text?.let { URL_RE.find(it)?.value?.trimEnd('.', ',', ')') }

    private var ready = false

    private fun init() {
        if (!ready) {
            NewPipe.init(YtHttp, Localization("en", "US"))
            ready = true
        }
    }

    /** 가장 좋은 소리 줄기를 골라 [dir] 의 임시 파일로 내려받는다. */
    fun download(url: String, dir: File, active: () -> Boolean, progress: (String, Float?) -> Unit): Result {
        val link = findUrl(url) ?: throw YouTubeException("유튜브 주소가 아니에요.")
        progress("영상 정보 읽는 중", null)
        val info = try {
            init()
            StreamInfo.getInfo(ServiceList.YouTube, link)
        } catch (e: ReCaptchaException) {
            throw YouTubeException(e.message ?: "YouTube 가 잠시 요청을 막았어요. 잠시 뒤 다시 해보세요.")
        } catch (e: org.schabi.newpipe.extractor.exceptions.ExtractionException) {
            throw YouTubeException("영상을 찾지 못했어요. 비공개·연령 제한·지역 제한 영상은 받을 수 없어요. (${e.message})")
        } catch (e: java.io.IOException) {
            throw YouTubeException("인터넷 연결을 확인해 주세요.")
        }
        val stream = pick(info.audioStreams) ?: throw YouTubeException("내려받을 수 있는 소리를 찾지 못했어요.")
        val suffix = stream.format?.suffix ?: "m4a"
        val out = File.createTempFile("yt", ".$suffix", dir)
        try {
            fetch(stream, out, active) { progress("소리 내려받는 중", it) }
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
        return Result(info.name ?: "YouTube", out)
    }

    /** 바로 내려받을 수 있는(HTTP) 줄기 중 m4a 를 먼저, 그 안에서 음질이 높은 것. */
    private fun pick(list: List<AudioStream>): AudioStream? {
        val direct = list.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
        val m4a = direct.filter { it.format?.suffix == "m4a" }
        return (m4a.ifEmpty { direct }).maxByOrNull { it.averageBitrate }
    }

    /** 유튜브 서버는 한 번에 크게 받으면 느려지므로 조각(range)으로 나눠 받는다. */
    private fun fetch(stream: AudioStream, out: File, active: () -> Boolean, progress: (Float?) -> Unit) {
        val total = stream.itagItem?.contentLength ?: -1L
        val base = stream.content
        var pos = 0L
        out.outputStream().buffered().use { sink ->
            while (true) {
                if (!active()) throw kotlinx.coroutines.CancellationException()
                val end = pos + CHUNK - 1
                val req = okhttp3.Request.Builder()
                    .url("$base&range=$pos-$end").header("User-Agent", YtHttp.UA).build()
                val got = YtHttp.client.newCall(req).execute().use { r ->
                    if (r.code == 416) return@use 0L
                    if (!r.isSuccessful) throw YouTubeException("내려받지 못했어요. (HTTP ${r.code})")
                    var n = 0L
                    r.body!!.byteStream().use { input ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val k = input.read(buf)
                            if (k < 0) break
                            sink.write(buf, 0, k)
                            n += k
                        }
                    }
                    n
                }
                pos += got
                progress(if (total > 0) (pos.toFloat() / total).coerceAtMost(1f) else null)
                if (got < CHUNK || (total in 1..pos)) break
            }
        }
        if (pos == 0L) throw YouTubeException("소리를 내려받지 못했어요.")
    }

    private const val CHUNK = 10L * 1024 * 1024
}
