package com.hdlee73.englishstudy.study

import android.content.Context
import android.net.Uri
import com.hdlee73.englishstudy.speaking.data.DatasetParser
import com.hdlee73.englishstudy.speaking.model.SentencePair
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A word list the learner added for the flashcards. */
data class FlashDataset(val id: String, val name: String, val fileName: String, val count: Int)

/**
 * The learner's own word lists for the flashcards (Excel or CSV with an English column and a Korean
 * column). They are kept apart from the speaking datasets because the two tabs serve different purposes.
 */
class FlashDatasetStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("flash_datasets", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "flash_datasets").apply { mkdirs() }

    fun list(): List<FlashDataset> = runCatching {
        val array = JSONArray(prefs.getString("index", "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val dataset = FlashDataset(item.getString("id"), item.getString("name"), item.getString("file"), item.getInt("count"))
                if (File(directory, dataset.fileName).exists()) add(dataset)
            }
        }
    }.getOrDefault(emptyList())

    /** Reads the file at [uri], keeps a copy, and returns the new list. Throws with a message fit for the learner. */
    fun import(uri: Uri, displayName: String): FlashDataset {
        val extension = displayName.substringAfterLast('.', "xlsx").lowercase().takeIf { it in setOf("xlsx", "csv") } ?: "xlsx"
        val id = UUID.randomUUID().toString()
        val file = File(directory, "$id.$extension")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("파일을 열 수 없습니다.")
            input.use { source -> file.outputStream().use(source::copyTo) }
            val usable = file.inputStream().use { DatasetParser.parse(it, file.name) }.filter { it.english.isNotBlank() && it.korean.isNotBlank() }
            require(usable.isNotEmpty()) { "‘$displayName’에 영어와 한글 뜻이 함께 있는 줄이 없습니다. 영어·한글 두 열로 만들어 주세요." }
            val dataset = FlashDataset(id, displayName, file.name, usable.size)
            saveIndex(list() + dataset)
            return dataset
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    /** English / Korean pairs of a list; rows without both are skipped. */
    fun load(dataset: FlashDataset): List<SentencePair> =
        File(directory, dataset.fileName).inputStream().use { DatasetParser.parse(it, dataset.fileName) }
            .filter { it.english.isNotBlank() && it.korean.isNotBlank() }

    fun delete(id: String) {
        val all = list()
        all.firstOrNull { it.id == id }?.let { File(directory, it.fileName).delete() }
        saveIndex(all.filterNot { it.id == id })
    }

    private fun saveIndex(items: List<FlashDataset>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("file", it.fileName).put("count", it.count)) }
        prefs.edit().putString("index", array.toString()).apply()
    }
}
