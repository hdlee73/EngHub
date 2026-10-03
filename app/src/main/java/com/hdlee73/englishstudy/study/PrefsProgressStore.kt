package com.hdlee73.englishstudy.study

import android.content.Context

/** [ProgressStore] kept in SharedPreferences as one small text value. */
internal class PrefsProgressStore(context: Context) : ProgressStore {
    private val prefs = context.applicationContext.getSharedPreferences("study_progress", Context.MODE_PRIVATE)
    private val cache: MutableMap<String, WordProgress> =
        LinkedHashMap(ProgressCodec.decode(prefs.getString(KEY, "").orEmpty()))

    @Synchronized override fun all(): Map<String, WordProgress> = LinkedHashMap(cache)

    @Synchronized override fun update(word: String, change: (WordProgress) -> WordProgress): WordProgress {
        val key = progressKey(word)
        val updated = change(cache[key] ?: WordProgress())
        cache[key] = updated
        persist()
        return updated
    }

    @Synchronized override fun remove(word: String) {
        if (cache.remove(progressKey(word)) != null) persist()
    }

    private fun persist() {
        prefs.edit().putString(KEY, ProgressCodec.encode(cache)).apply()
    }

    private companion object {
        const val KEY = "progress_v1"
    }
}
