package com.hdlee73.englishstudy.speaking.data

import android.content.Context
import android.net.Uri
import com.hdlee73.englishstudy.speaking.model.SavedDataset
import com.hdlee73.englishstudy.speaking.model.SentencePair
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class DatasetStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("learning", 0)
    private val directory = File(context.filesDir, "datasets").apply { mkdirs() }

    fun ensureDefault() {
        ensurePhrasalDefault()
        if (prefs.getBoolean("daily_500_installed", false)) return
        val file = File(directory, "daily_500.csv")
        context.assets.open("daily_500.csv").use { input -> file.outputStream().use(input::copyTo) }
        val count = file.inputStream().use { DatasetParser.parse(it, file.name) }.size
        saveIndex(list() + SavedDataset("daily_500", "생활 영어 패턴 500.csv", file.name, count))
        prefs.edit().putBoolean("daily_500_installed", true).apply()
    }

    private fun ensurePhrasalDefault() {
        if (prefs.getBoolean("phrasal_200_installed", false)) return
        val file = File(directory, "phrasal_200.csv")
        context.assets.open("phrasal_200.csv").use { input -> file.outputStream().use(input::copyTo) }
        val count = file.inputStream().use { DatasetParser.parse(it, file.name) }.size
        saveIndex(list() + SavedDataset("phrasal_200", "실생활 구동사 200.csv", file.name, count))
        prefs.edit().putBoolean("phrasal_200_installed", true).apply()
    }

    /**
     * Rewrites the built-in "saved words" dataset from the example sentences of the saved dictionary
     * words, so it always matches the word list. Returns null (and removes the dataset) when there are
     * no sentences. Sentence edits made in the dataset editor are discarded: the saved words are the
     * source of truth.
     */
    fun syncSavedWords(pairs: List<SentencePair>): SavedDataset? = syncManaged(SAVED_WORDS_ID, SAVED_WORDS_NAME, pairs)

    /**
     * The example sentences of the bundled Phrases collection, as a dataset for Speaking, Cards and Quiz. Rewritten only when the
     * collection changed (a different number of sentences), so a later app version with more phrases refreshes it.
     */
    fun syncPhraseExamples(pairs: List<SentencePair>): SavedDataset? {
        if (pairs.isEmpty()) return null
        val existing = list().firstOrNull { it.id == PHRASES_ID }
        if (existing != null && existing.sentenceCount == pairs.size) return existing
        return syncManaged(PHRASES_ID, PHRASES_NAME, pairs)
    }

    /** Same for the starred sentences: they live in their own dataset next to the saved-words one. */
    fun syncFavorites(pairs: List<SentencePair>): SavedDataset? = syncManaged(FAVORITES_ID, FAVORITES_NAME, pairs)

    /**
     * Appends a sentence saved from the Translate tab to the "번역 저장 문장" dataset (created on first use).
     * Returns false when that English sentence is already in it.
     */
    fun addTranslated(pair: SentencePair): Boolean {
        val existing = list().firstOrNull { it.id == TRANSLATED_ID }
        val items = existing?.let { runCatching { load(it) }.getOrNull() }.orEmpty()
        val key = pair.english.trim().lowercase()
        if (items.any { it.english.trim().lowercase() == key }) return false
        val all = items + pair
        File(directory, "$TRANSLATED_ID.csv").writeText(DatasetCsv.write(all), Charsets.UTF_8)
        File(directory, "$TRANSLATED_ID.edited.json").delete()
        val dataset = SavedDataset(TRANSLATED_ID, TRANSLATED_NAME, "$TRANSLATED_ID.csv", all.size)
        saveIndex(managedFirst(list().filterNot { it.id == TRANSLATED_ID } + dataset))
        return true
    }

    private fun managedFirst(items: List<SavedDataset>): List<SavedDataset> {
        val managedIds = listOf(SAVED_WORDS_ID, FAVORITES_ID, TRANSLATED_ID)
        val (managed, rest) = items.partition { it.id in managedIds }
        return managed.sortedBy { managedIds.indexOf(it.id) } + rest
    }

    private fun syncManaged(id: String, name: String, pairs: List<SentencePair>): SavedDataset? {
        val others = list().filterNot { it.id == id }
        val file = File(directory, "$id.csv")
        File(directory, "$id.edited.json").delete()
        if (pairs.isEmpty()) {
            file.delete()
            saveIndex(others)
            return null
        }
        file.writeText(DatasetCsv.write(pairs), Charsets.UTF_8)
        val dataset = SavedDataset(id, name, file.name, pairs.size)
        // The managed datasets stay at the top of the list: saved words first, then favorites, then translated sentences.
        saveIndex(managedFirst(others + dataset))
        return dataset
    }

    fun saveEdits(dataset: SavedDataset, items: List<SentencePair>) {
        require(items.size == dataset.sentenceCount && items.all { it.english.isNotBlank() })
        val json = JSONArray()
        items.forEach { json.put(JSONObject().put("korean", it.korean).put("english", it.english)) }
        val target = File(directory, "${dataset.id}.edited.json")
        val temporary = File(directory, "${dataset.id}.edited.tmp")
        temporary.writeText(json.toString(), Charsets.UTF_8)
        check(temporary.renameTo(target)) { "수정 내용을 저장하지 못했습니다." }
    }

    fun list(): List<SavedDataset> = runCatching {
        val array = JSONArray(prefs.getString("datasets_index", "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val saved = SavedDataset(item.getString("id"), item.getString("name"), item.getString("file"), item.getInt("count"))
                if (File(directory, saved.fileName).exists()) add(saved)
            }
        }
    }.getOrDefault(emptyList())

    fun import(uri: Uri, displayName: String): Pair<SavedDataset, List<SentencePair>> {
        val extension = displayName.substringAfterLast('.', "xlsx").lowercase().takeIf { it in setOf("xlsx", "csv") } ?: "xlsx"
        val id = UUID.randomUUID().toString()
        val file = File(directory, "$id.$extension")
        context.contentResolver.openInputStream(uri)!!.use { source -> file.outputStream().use(source::copyTo) }
        val items = file.inputStream().use { DatasetParser.parse(it, displayName) }
        val saved = SavedDataset(id, displayName, file.name, items.size)
        saveIndex(list() + saved)
        return saved to items
    }

    fun load(dataset: SavedDataset): List<SentencePair> {
        val edited = File(directory, "${dataset.id}.edited.json")
        if (edited.exists()) {
            val json = JSONArray(edited.readText(Charsets.UTF_8))
            return (0 until json.length()).map { i -> json.getJSONObject(i).let { SentencePair(it.getString("korean"), it.getString("english")) } }
        }
        return File(directory, dataset.fileName).inputStream().use { DatasetParser.parse(it, dataset.name) }
    }

    fun delete(dataset: SavedDataset) {
        File(directory, dataset.fileName).delete()
        File(directory, "${dataset.id}.edited.json").delete()
        saveIndex(list().filterNot { it.id == dataset.id })
    }

    fun migrateLegacy(): SavedDataset? {
        if (list().isNotEmpty()) return null
        val legacy = File(context.filesDir, "dataset")
        if (!legacy.exists()) return null
        val name = prefs.getString("dataset_name", "dataset.xlsx") ?: "dataset.xlsx"
        val extension = name.substringAfterLast('.', "xlsx").lowercase()
        val id = UUID.randomUUID().toString()
        val target = File(directory, "$id.$extension")
        legacy.copyTo(target, overwrite = true)
        val count = target.inputStream().use { DatasetParser.parse(it, name) }.size
        return SavedDataset(id, name, target.name, count).also { saveIndex(listOf(it)) }
    }

    private fun saveIndex(items: List<SavedDataset>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("name", item.name).put("file", item.fileName).put("count", item.sentenceCount))
        }
        prefs.edit().putString("datasets_index", array.toString()).apply()
    }

    companion object {
        const val SAVED_WORDS_ID = "saved_words"
        /** The name must end in ".csv": the parser is chosen from it when the dataset is loaded. */
        const val SAVED_WORDS_NAME = "저장 단어 예문.csv"
        const val FAVORITES_ID = "favorites"
        const val PHRASES_ID = "phrase_examples"
        const val PHRASES_NAME = "패턴·표현 예문.csv"
        const val FAVORITES_NAME = "즐겨찾기 문장.csv"
        const val TRANSLATED_ID = "translated"
        const val TRANSLATED_NAME = "번역 저장 문장.csv"
    }
}
