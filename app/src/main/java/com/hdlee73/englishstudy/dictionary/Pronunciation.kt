package com.hdlee73.englishstudy.dictionary

/** IPA text from the online dictionary, normalized to "/.../" and rejected when it is not a transcription. */
internal object Pronunciation {
    fun clean(raw: String?): String {
        val text = raw.orEmpty().trim().trim('/', '[', ']').trim()
        if (text.isEmpty() || text.length > 40) return ""
        // Real transcriptions contain phonetic letters or stress marks; plain words are not IPA.
        val phonetic = text.any { it.code > 0x7F } || text.any { it in "ˈˌ:" }
        return if (phonetic || text.all { it.isLetter() || it in "'.-" }) "/$text/" else ""
    }

    /** First usable transcription of several candidates, in the order given. */
    fun first(candidates: List<String?>): String = candidates.asSequence().map { clean(it) }.firstOrNull { it.isNotEmpty() }.orEmpty()
}

/** Orders for the saved-word list; the chosen one is remembered between launches. */
enum class SavedSort(val title: String) {
    ALPHABETICAL("알파벳순 (A→Z)"),
    REVERSE("알파벳 역순 (Z→A)"),
    NEWEST("최근 저장한 순"),
    OLDEST("오래 저장한 순");

    fun <T> apply(items: List<T>, word: (T) -> String, id: (T) -> Long): List<T> = when (this) {
        ALPHABETICAL -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, word))
        REVERSE -> items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER, word))
        NEWEST -> items.sortedByDescending(id)
        OLDEST -> items.sortedBy(id)
    }

    companion object {
        fun of(name: String?): SavedSort = values().firstOrNull { it.name == name } ?: ALPHABETICAL
    }
}
