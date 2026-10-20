package com.hdlee73.englishstudy.translate

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * English ↔ Korean translation by ML Kit on the device. It is the second choice behind the online translation: it takes
 * over when the online service gives no answer (offline, blocked). Each language model is downloaded once (~30 MB) and then
 * works without a connection.
 */
internal object MlKitTranslator {
    const val CREDIT = "기기 내 번역(ML Kit)으로 번역했어요. 온라인 번역보다 단순할 수 있어요."

    private val enKo by lazy { client(TranslateLanguage.ENGLISH, TranslateLanguage.KOREAN) }
    private val koEn by lazy { client(TranslateLanguage.KOREAN, TranslateLanguage.ENGLISH) }

    private fun client(from: String, to: String) = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build()
    )

    /** The translation, or null when the model is not available (first use without internet) or the text cannot be translated. */
    suspend fun translate(text: String, direction: Direction): String? {
        val translator = if (direction == Direction.EN_KO) enKo else koEn
        return try {
            translator.downloadModelIfNeeded().await()
            val lines = ArrayList<String>()
            for (chunks in TextTranslation.pieces(text.take(TextTranslation.MAX_CHARS))) {
                if (chunks == null) { lines += ""; continue }
                val parts = ArrayList<String>()
                for (chunk in chunks) parts += translator.translate(chunk).await().trim()
                lines += parts.joinToString(" ")
            }
            lines.joinToString("\n").trim().takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
