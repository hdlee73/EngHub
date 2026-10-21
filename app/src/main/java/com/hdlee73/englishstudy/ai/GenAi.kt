package com.hdlee73.englishstudy.ai

import android.content.Context
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import com.google.mlkit.genai.rewriting.Rewriter
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gemini Nano through ML Kit GenAI (Android AICore): proofreading + rewriting + free-form
 * prompts (used by 프리토킹 for open conversation).
 *
 * Supported device (Pixel 8+/S24+ etc. with AICore) -> the feature is checked, downloaded once
 * if needed, then every call runs fully on-device. Otherwise every call returns empty results
 * and the screens fall back to the existing engines (rule engine, dictionary, online translation).
 * Nothing is checked or downloaded until a screen that uses it calls [initialize].
 */
object GenAi {

    enum class Status { CHECKING, AVAILABLE, DOWNLOADING, UNAVAILABLE }

    private val _status = MutableStateFlow(Status.CHECKING)
    val status: StateFlow<Status> = _status

    private var proofreader: Proofreader? = null
    private var rewriter: Rewriter? = null
    private var chatModel: GenerativeModel? = null
    private var proofreaderReady = false
    private var rewriterReady = false
    private var chatReady = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True when the Prompt API (open-ended generation) can be used for free talk. */
    fun canChat(): Boolean = chatReady

    fun isReady(): Boolean = _status.value == Status.AVAILABLE

    private val initLock = Mutex()

    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        initLock.withLock {
            // Ready, or a model download is already running: nothing to start again.
            if (_status.value == Status.AVAILABLE || _status.value == Status.DOWNLOADING) return@withLock isReady()
            try {
                val p = Proofreading.getClient(
                    ProofreaderOptions.builder(context)
                        .setInputType(ProofreaderOptions.InputType.KEYBOARD)
                        .setLanguage(ProofreaderOptions.Language.ENGLISH)
                        .build()
                )
                val r = Rewriting.getClient(
                    RewriterOptions.builder(context)
                        .setOutputType(RewriterOptions.OutputType.REPHRASE)
                        .setLanguage(RewriterOptions.Language.ENGLISH)
                        .build()
                )
                proofreader = p
                rewriter = r
                val pStatus = runCatching { p.checkFeatureStatus().await() }.getOrDefault(FeatureStatus.UNAVAILABLE)
                val rStatus = runCatching { r.checkFeatureStatus().await() }.getOrDefault(FeatureStatus.UNAVAILABLE)
                proofreaderReady = pStatus == FeatureStatus.AVAILABLE
                rewriterReady = rStatus == FeatureStatus.AVAILABLE
                if (pStatus == FeatureStatus.DOWNLOADABLE) p.downloadFeature(callback { proofreaderReady = true })
                if (rStatus == FeatureStatus.DOWNLOADABLE) r.downloadFeature(callback { rewriterReady = true })
                val cStatus = initChatModel()
                refreshStatus(downloading = pStatus.isPending() || rStatus.isPending() || cStatus.isPending())
            } catch (_: Throwable) {
                _status.value = Status.UNAVAILABLE
            }
            isReady()
        }
    }

    /** Prompt API client; isolated so an unsupported device only loses free-talk generation. */
    private suspend fun initChatModel(): Int = try {
        val g = Generation.getClient()
        chatModel = g
        val status = g.checkStatus()
        chatReady = status == FeatureStatus.AVAILABLE
        if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
            scope.launch {
                runCatching { g.download().collect { } }
                chatReady = runCatching { g.checkStatus() == FeatureStatus.AVAILABLE }.getOrDefault(false)
                refreshStatus()
            }
        }
        status
    } catch (_: Throwable) {
        FeatureStatus.UNAVAILABLE
    }

    private fun Int.isPending() = this == FeatureStatus.DOWNLOADABLE || this == FeatureStatus.DOWNLOADING

    private fun refreshStatus(downloading: Boolean = false) {
        _status.value = when {
            proofreaderReady || rewriterReady || chatReady -> Status.AVAILABLE
            downloading -> Status.DOWNLOADING
            else -> Status.UNAVAILABLE
        }
    }

    private fun callback(onDone: () -> Unit) = object : DownloadCallback {
        override fun onDownloadStarted(bytesToDownload: Long) {}
        override fun onDownloadProgress(totalBytesDownloaded: Long) {}
        override fun onDownloadCompleted() { onDone(); refreshStatus() }
        override fun onDownloadFailed(e: GenAiException) { refreshStatus() }
    }

    /** Proofreading (Gemini Nano). Returns corrected text or null when unavailable. */
    suspend fun proofread(text: String): String? = withContext(Dispatchers.IO) {
        val p = proofreader?.takeIf { proofreaderReady } ?: return@withContext null
        runCatching {
            withTimeoutOrNull(TIMEOUT_MS) {
                p.runInference(ProofreadingRequest.builder(text).build()).await()
            }?.results?.firstOrNull()?.text?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** Rewriting (Gemini Nano): up to 3 rephrase candidates. */
    suspend fun rephrase(text: String): List<String> = withContext(Dispatchers.IO) {
        val r = rewriter?.takeIf { rewriterReady } ?: return@withContext emptyList()
        val results = runCatching {
            withTimeoutOrNull(TIMEOUT_MS) {
                r.runInference(RewritingRequest.builder(text).build()).await()
            }?.results
        }.getOrNull().orEmpty()
        results.map { it.text.trim() }.distinct().filter { it.isNotBlank() && it != text }.take(3)
    }

    /** Free-form generation (Gemini Nano Prompt API). Returns null when unavailable or on failure. */
    suspend fun generate(prompt: String): String? = withContext(Dispatchers.IO) {
        val m = chatModel?.takeIf { chatReady } ?: return@withContext null
        runCatching {
            withTimeoutOrNull(TIMEOUT_MS) { m.generateContent(prompt) }
                ?.candidates?.firstOrNull()?.text?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * Like [generate], but [onText] gets each piece of text as soon as the model has written it, so a spoken partner can start
     * with the first sentence. Returns the whole text, or null when unavailable or nothing was written.
     */
    suspend fun generateStream(prompt: String, onText: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        val m = chatModel?.takeIf { chatReady } ?: return@withContext null
        val all = StringBuilder()
        try {
            withTimeoutOrNull(TIMEOUT_MS) {
                m.generateContentStream(prompt).collect { chunk ->
                    val text = chunk.candidates.firstOrNull()?.text.orEmpty()
                    if (text.isNotEmpty()) { all.append(text); onText(text) }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Whatever was written before the failure is still returned.
        }
        all.toString().trim().takeIf { it.isNotBlank() }
    }

    private const val TIMEOUT_MS = 15_000L
}
